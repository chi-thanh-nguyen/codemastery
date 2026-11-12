// The r2 cleanup and recovery engine (ADR008 CT34-FIXTURES-2026-11-10-r2, Section 10.3).
//
// Normal teardown and interrupted-run recovery use the *same* protocol and the same persistent
// inventory; the exclusive advisory lock is held by fixtures/lifecycle.py for the whole call. Only
// resources proven to belong exclusively to the recorded run are removed:
//
//   * processes are resolved by OS birth identity, never by PID, port or name pattern; an orphaned
//     preview is asked to close itself over its private control channel and there is no PID-based
//     fallback; anything live, unreadable, ambiguous or foreign is preserved;
//   * Docker work is pinned to the recorded endpoint, context *and* daemon ID, and targets the
//     inventory's immutable IDs corroborated by the exact run and owner labels; interrupted
//     creation intents are reconciled against that daemon, never by deleting everything carrying
//     the generic disposable label, and never by a global prune;
//   * anonymous PostgreSQL volumes are discovered from the owned container's mounts and recorded
//     before that container is removed; bind mounts and pre-existing named volumes are refused;
//   * the temporary build directory is removed only after its parent, owner, device/inode and run
//     marker are verified without following a replacement symlink;
//   * every target is inspected again after each action, so confirmed absence is distinguished from
//     permission errors, daemon errors and timeouts.
//
// Completion requires verified absence of every owned process, build directory, container, volume
// and network, with all creation intents resolved. Anything else is persisted as `incomplete`,
// returns a failure to the caller and keeps that checkout blocked until recovery succeeds.
import { spawnSync } from 'node:child_process';
import { connect } from 'node:net';
import { readdirSync, rmSync } from 'node:fs';
import { join } from 'node:path';
import { classifyProcess, hostIdentity, processIdentity, verifyDirectory } from './host-identity.mjs';
import { NONTERMINAL, observe, readInventory, setIntent, writeInventory } from './run-inventory.mjs';

export const STACK_LABEL = 'codemastery.e2e=disposable';
export const runLabel = (runId) => `codemastery.e2e.run=${runId}`;
export const ownerLabel = (uid = process.getuid()) => `codemastery.e2e.owner=${uid}`;
const DOCKER_TIMEOUT_MS = 30_000;
const REMOVE_ATTEMPTS = 3;
const RETRY_PAUSE_MS = 1000;
const absent = (text) => /no such (object|container|network|volume)|not found|is not a valid|no such/i.test(text);
const pause = (ms) => new Promise((done) => setTimeout(done, ms));

/** A bounded Docker CLI call that never throws and never hangs. */
export function makeDocker(timeoutMs = DOCKER_TIMEOUT_MS) {
    return (args, options = {}) => {
        const result = spawnSync('docker', args, { encoding: 'utf8', timeout: options.timeout ?? timeoutMs, maxBuffer: 8 * 1024 * 1024 });
        const detail = (result.stderr || result.error?.message || (result.status === null ? 'docker timed out' : `exit ${result.status}`)).trim();
        return { ok: result.status === 0, stdout: result.stdout ?? '', detail, timedOut: result.status === null };
    };
}

/**
 * The Docker client endpoint, context and daemon ID to pin for this run. Only a daemon local to this
 * client host is supported (a Unix socket or Windows pipe, which includes a local Docker Desktop
 * VM); a remote or shared client context is refused for automatic lifecycle management, and a
 * context name on its own is never treated as daemon identity.
 */
export function dockerIdentity(docker = makeDocker()) {
    const context = docker(['context', 'show']);
    if (!context.ok) return { ok: false, why: `docker context show failed: ${context.detail}` };
    const name = context.stdout.trim();
    const endpoint = docker(['context', 'inspect', name, '--format', '{{.Endpoints.docker.Host}}']);
    if (!endpoint.ok) return { ok: false, why: `docker context inspect failed: ${endpoint.detail}` };
    const host = endpoint.stdout.trim();
    const info = docker(['info', '--format', '{{.ID}}']);
    if (!info.ok) return { ok: false, why: `docker info failed: ${info.detail}` };
    const daemonId = info.stdout.trim();
    if (!daemonId) return { ok: false, why: 'the daemon reported no ID' };
    if (!/^(unix|npipe):\/\//.test(host)) {
        return { ok: false, why: `context ${name} uses ${host}; only a daemon local to this client host is supported for automatic lifecycle management` };
    }
    return { ok: true, context: name, endpoint: host, daemonId, local: true };
}

/** Confirms the daemon we are about to clean up on is the exact one that created the resources. */
function verifyDaemon(recorded, docker) {
    if (!recorded) return { ok: false, why: 'no Docker endpoint was recorded for this run' };
    const current = dockerIdentity(docker);
    if (!current.ok) return { ok: false, why: current.why };
    if (current.daemonId !== recorded.daemonId) {
        return { ok: false, why: `the daemon ID is now ${current.daemonId}, not the recorded ${recorded.daemonId}` };
    }
    if (current.endpoint !== recorded.endpoint) {
        return { ok: false, why: `the Docker endpoint is now ${current.endpoint}, not the recorded ${recorded.endpoint}` };
    }
    return { ok: true, current };
}

/** `{absent}` or `{labels, mounts, status}` for one container, without ever throwing. */
function inspectContainer(docker, id) {
    const reply = docker(['inspect', '--type', 'container', '--format', '{{json .}}', id]);
    if (!reply.ok) return absent(reply.detail) ? { absent: true } : { error: reply.detail, timedOut: reply.timedOut };
    try {
        const parsed = JSON.parse(reply.stdout);
        return { labels: parsed.Config?.Labels ?? {}, mounts: parsed.Mounts ?? [], status: parsed.State?.Status ?? 'unknown', id: parsed.Id };
    } catch (error) {
        return { error: `unparsable inspect output: ${error.message}` };
    }
}

function inspectSimple(docker, kind, id) {
    const reply = kind === 'network'
        ? docker(['network', 'inspect', '--format', '{{json .Labels}}', id])
        : docker(['volume', 'inspect', '--format', '{{json .Labels}}', id]);
    if (!reply.ok) return absent(reply.detail) ? { absent: true } : { error: reply.detail, timedOut: reply.timedOut };
    try {
        return { labels: JSON.parse(reply.stdout || 'null') ?? {} };
    } catch {
        return { labels: {} };
    }
}

/** True when the resource's own labels corroborate the recorded run and owner. */
const ours = (labels, runId) => labels?.['codemastery.e2e.run'] === runId && labels?.['codemastery.e2e'] === 'disposable';

/**
 * Removes one resource and re-inspects it, up to a bounded number of attempts. A removal that
 * reports success is still verified, so a timeout or a daemon error is never reported as absence.
 */
async function removeVerified(docker, kind, id, runId, report) {
    const remove = kind === 'container' ? ['rm', '-f', id]
        : kind === 'network' ? ['network', 'rm', id] : ['volume', 'rm', id];
    for (let attempt = 1; attempt <= REMOVE_ATTEMPTS; attempt++) {
        const result = docker(remove);
        const seen = kind === 'container' ? inspectContainer(docker, id) : inspectSimple(docker, kind, id);
        if (seen.absent) return { state: result.ok ? 'removed' : 'absent', note: `verified absent after attempt ${attempt}` };
        if (seen.error) {
            report(`${kind} ${id}: removal could not be verified (${seen.error})`);
            if (attempt === REMOVE_ATTEMPTS) return { state: 'failed', note: `removal unverifiable: ${seen.error}` };
        } else {
            report(`${kind} ${id}: still present after attempt ${attempt} (${result.ok ? 'removal reported success' : result.detail})`);
            if (attempt === REMOVE_ATTEMPTS) return { state: 'failed', note: `still present after ${REMOVE_ATTEMPTS} attempts: ${result.ok ? 'removal reported success but the resource remains' : result.detail}` };
        }
        await pause(RETRY_PAUSE_MS);
    }
    return { state: 'failed', note: 'exhausted bounded retries' };
}

/**
 * Resolves a creation intent whose identity was never persisted, by looking for exactly one resource
 * on the recorded daemon carrying this run's own labels and recorded name. No match means the
 * creation did not take effect; several inconsistent matches stay unresolved.
 */
function reconcileIntent(docker, intent, runId) {
    const filters = ['--filter', `label=${STACK_LABEL}`, '--filter', `label=${runLabel(runId)}`];
    const list = intent.kind === 'container'
        ? docker(['ps', '-aq', ...filters, '--filter', `name=^${intent.name}$`])
        : docker(['network', 'ls', '-q', ...filters, '--filter', `name=^${intent.name}$`]);
    if (!list.ok) return { resolved: false, why: `could not list ${intent.kind}s to reconcile the intent: ${list.detail}` };
    const ids = list.stdout.split('\n').map((line) => line.trim()).filter(Boolean);
    if (ids.length === 0) return { resolved: true, state: 'absent', why: 'the recorded creation intent left nothing on this daemon' };
    if (ids.length > 1) return { resolved: false, why: `${ids.length} resources match the intent ${intent.name}; ownership is ambiguous` };
    return { resolved: true, state: 'created', id: ids[0], why: 'one exact run-labelled match adopted from the creation intent' };
}

/**
 * Asks an orphaned preview to close its own server over its private control channel. The request
 * carries the recorded run ID and the recorded birth identity, and the preview refuses it unless
 * both are its own. A missing or unresponsive channel preserves the process; no signal is sent.
 */
export function askPreviewToClose(socketPath, runId, birth, timeoutMs = 10_000) {
    return new Promise((done) => {
        let settled = false;
        const finish = (value) => { if (!settled) { settled = true; done(value); } };
        let socket;
        try {
            socket = connect(socketPath);
        } catch (error) {
            finish({ ok: false, why: `the control channel could not be opened: ${error.message}` });
            return;
        }
        const timer = setTimeout(() => { socket.destroy(); finish({ ok: false, why: 'the control channel did not answer in time' }); }, timeoutMs);
        let reply = '';
        socket.setEncoding('utf8');
        socket.on('connect', () => socket.write(`${JSON.stringify({ command: 'shutdown', runId, birth })}\n`));
        socket.on('data', (chunk) => { reply += chunk; });
        socket.on('error', (error) => { clearTimeout(timer); finish({ ok: false, why: `the control channel is unavailable: ${error.code ?? error.message}` }); });
        socket.on('close', () => {
            clearTimeout(timer);
            try {
                const parsed = JSON.parse(reply);
                finish(parsed.ok ? { ok: true, acknowledged: parsed } : { ok: false, why: `the preview refused the request (${parsed.reason})`, reply: parsed });
            } catch {
                finish({ ok: false, why: `the control channel gave no usable answer (${reply.slice(0, 120) || 'empty'})` });
            }
        });
    });
}

/** Waits until the recorded process is verifiably gone; an unreadable process never counts as gone. */
async function waitForExit(recorded, host, timeoutMs = 15_000) {
    const deadline = Date.now() + timeoutMs;
    for (;;) {
        const verdict = classifyProcess(recorded, { host, recordedHost: recorded.recordedHost });
        if (verdict.state === 'exited' || verdict.state === 'pid-reused') return verdict;
        if (Date.now() > deadline) return verdict;
        await pause(500);
    }
}

/** Run IDs that another checkout's nonterminal record currently owns, so their resources are live. */
function otherActiveRunIds(registryRoot, ownKey) {
    const ids = new Set();
    if (!registryRoot) return ids;
    try {
        for (const name of readdirSync(join(registryRoot, 'runs'))) {
            if (!name.endsWith('.json') || name.slice(0, -5) === ownKey) continue;
            try {
                const other = readInventory(join(registryRoot, 'runs', name));
                if (other && NONTERMINAL.includes(other.state) && other.runId) ids.add(other.runId);
            } catch { /* another checkout's unreadable record is not ours to interpret */ }
        }
    } catch { /* no registry runs directory yet */ }
    return ids;
}

/**
 * Reports resources carrying the generic disposable label that this inventory does not account for.
 * Resources belonging to another checkout's live run are listed as untouched and are not a failure;
 * anything else (a legacy PID-only or unknown-owner resource) is preserved and reported for explicit
 * operator reconciliation. Nothing here is ever removed automatically.
 */
function scanUnaccounted(docker, knownIds, runId, liveElsewhere, report, tolerateListFailure = false) {
    const findings = { other: 0, unaccounted: [] };
    for (const [kind, list] of [['container', ['ps', '-aq', '--filter', `label=${STACK_LABEL}`]],
        ['network', ['network', 'ls', '-q', '--filter', `label=${STACK_LABEL}`]]]) {
        const listed = docker(list);
        if (!listed.ok) {
            // When this inventory records no Docker work at all there is nothing to reconcile, so an
            // unavailable daemon is reported as a scan limit rather than as an unresolved resource.
            if (tolerateListFailure) { report(`could not scan for unaccounted ${kind}s: ${listed.detail}`); continue; }
            findings.unaccounted.push({ kind, id: '(listing failed)', why: listed.detail });
            continue;
        }
        for (const id of listed.stdout.split('\n').map((line) => line.trim()).filter(Boolean)) {
            if (knownIds.has(id)) continue;
            const seen = kind === 'container' ? inspectContainer(docker, id) : inspectSimple(docker, kind, id);
            if (seen.absent) continue;
            if (seen.error) { findings.unaccounted.push({ kind, id, why: `ownership unreadable: ${seen.error}` }); continue; }
            const owner = seen.labels?.['codemastery.e2e.run'];
            if (owner && owner !== runId && liveElsewhere.has(owner)) { findings.other++; continue; }
            findings.unaccounted.push({ kind, id, why: owner ? `labelled for run ${owner}, which no current inventory owns` : 'carries no run label (a pre-r2 leftover)' });
        }
    }
    if (findings.other) report(`${findings.other} resource(s) belong to another checkout's live run and were left untouched`);
    for (const entry of findings.unaccounted) {
        report(`preserved ${entry.kind} ${entry.id}: ${entry.why}; it is not removed automatically and needs operator reconciliation`);
    }
    return findings;
}

/**
 * Reconciles everything the inventory records and returns `{complete, record, report}`.
 *
 * `options.previewHandle` is the live child-process handle of a supervised run: while the parent
 * still holds an unreaped handle the PID cannot be reused, so it is a qualified owned handle and may
 * be used if the cooperative channel fails. A recovered numeric PID is never used this way.
 */
export async function reconcile(inventoryFile, options = {}) {
    const docker = options.docker ?? makeDocker();
    const lines = [];
    const report = (text) => { lines.push(text); if (options.log !== false) console.log(`stack-down: ${text}`); };
    const host = options.host ?? hostIdentity();
    const registryRoot = options.registryRoot ?? process.env.CODEMASTERY_E2E_REGISTRY ?? null;
    const ownKey = options.lockKey ?? process.env.CODEMASTERY_E2E_LOCK_KEY ?? null;

    let record = null;
    try {
        record = readInventory(inventoryFile);
    } catch (error) {
        report(`${error.message}`);
        report('the record is preserved as evidence of an incomplete run; inspect it and remove it by hand only after confirming there are no leftovers');
        return { complete: false, record: null, report: lines, untrusted: true };
    }
    if (!record) {
        report('no inventory record for this checkout; nothing is owned here');
        const findings = scanUnaccounted(docker, new Set(), null, otherActiveRunIds(registryRoot, ownKey), report, true);
        return { complete: findings.unaccounted.length === 0, record: null, report: lines };
    }

    const runId = record.runId;
    record.state = 'cleaning';
    observe(record, `reconciliation started (${options.reason ?? 'unspecified'})`);
    writeInventory(inventoryFile, record);

    // ---------------------------------------------------------------- 1. writers (processes first)
    const recordedHost = record.host;
    const sameMachine = (recordedHost?.hostname ?? null) === (host.hostname ?? null)
        && (recordedHost?.boot ?? null) === (host.boot ?? null)
        && (recordedHost?.pidNamespace ?? null) === (host.pidNamespace ?? null);
    let writersGone = true;
    let selfAlive = false;
    for (const [role, recorded] of Object.entries(record.processes ?? {})) {
        if (role === 'supervisor' && recorded.pid === process.ppid) {
            // The coordinator that is waiting for this call is not a writer to stop.
            observe(record, `supervisor ${recorded.pid} is this reconciliation's own coordinator`);
            recorded.state = 'live-coordinator';
            continue;
        }
        if (recorded.state === 'exited') continue;
        if (recorded.pid === process.pid && sameMachine) {
            // The runner performing its own teardown: it does not write the temporary build, so it
            // cannot block that cleanup, but the run is not complete until the coordinator has
            // verified this process' exit by birth identity after it ends.
            const self = classifyProcess(recorded, { host });
            if (self.state === 'exact-live') {
                recorded.state = 'reconciling-self';
                selfAlive = true;
                report(`${role} ${recorded.pid} is this reconciliation's own process; its exit is verified afterwards by the coordinator`);
                continue;
            }
        }
        if (!sameMachine) {
            recorded.state = 'preserved';
            recorded.why = 'recorded on another host, boot or PID namespace; a local verdict is not valid';
            report(`preserved ${role} ${recorded.pid}: ${recorded.why}`);
            writersGone = false;
            continue;
        }
        let verdict = classifyProcess(recorded, { host });
        if (verdict.state === 'exact-live' && role === 'preview') {
            const socketPath = recorded.control;
            if (!socketPath) {
                report(`preview ${recorded.pid} is live but no control channel was recorded; it is preserved`);
            } else {
                const asked = await askPreviewToClose(socketPath, runId, recorded.birth, options.controlTimeoutMs);
                if (asked.ok) {
                    report(`preview ${recorded.pid} acknowledged the private control channel and is closing its own server`);
                    verdict = await waitForExit(recorded, host, options.exitTimeoutMs);
                } else {
                    report(`the private preview control channel could not stop ${recorded.pid}: ${asked.why}`);
                    if (options.previewHandle && options.previewHandle.exitCode === null && options.previewHandle.signalCode === null && options.previewHandle.pid === recorded.pid) {
                        // Supervised path only: an unreaped owned handle guarantees this PID is still
                        // the same process, so stopping it cannot reach an unrelated one.
                        report(`stopping the preview through this run's own unreaped process handle (no recovered PID is signalled)`);
                        options.previewHandle.kill('SIGTERM');
                        verdict = await waitForExit(recorded, host, options.exitTimeoutMs);
                    } else {
                        report('no PID-based fallback is authorized; the preview and its build are preserved and recovery is incomplete');
                    }
                }
            }
        }
        if (verdict.state === 'exited' || verdict.state === 'pid-reused') {
            recorded.state = 'exited';
            recorded.why = verdict.why;
            report(`${role} ${recorded.pid} is verifiably gone (${verdict.why})`);
        } else {
            recorded.state = 'preserved';
            recorded.why = verdict.why;
            report(`preserved ${role} ${recorded.pid}: ${verdict.why}`);
            writersGone = false;
        }
    }
    writeInventory(inventoryFile, record);

    // ------------------------------------------------------------------- 2. the temporary build
    let buildResolved = true;
    if (record.temp) {
        if (!writersGone) {
            report(`the temporary build ${record.temp.path} is preserved: a writer has not been verified as exited`);
            record.temp.state = 'preserved';
            buildResolved = false;
        } else {
            const verified = verifyDirectory(record.temp, runId);
            if (verified.absent) {
                record.temp.state = 'absent';
                report(`the temporary build ${record.temp.path} is already gone`);
            } else if (!verified.ok) {
                record.temp.state = 'preserved';
                record.temp.why = verified.why;
                report(`preserved the recorded temporary path ${record.temp.path}: ${verified.why}`);
                buildResolved = false;
            } else {
                try {
                    rmSync(record.temp.path, { recursive: true, force: true });
                    const after = verifyDirectory(record.temp, runId);
                    record.temp.state = after.absent ? 'removed' : 'failed';
                    if (after.absent) report(`removed the verified temporary build ${record.temp.path}`);
                    else { report(`the temporary build ${record.temp.path} is still present after removal`); buildResolved = false; }
                } catch (error) {
                    record.temp.state = 'failed';
                    record.temp.why = error.message;
                    report(`could not remove ${record.temp.path}: ${error.message}`);
                    buildResolved = false;
                }
            }
        }
    }
    writeInventory(inventoryFile, record);

    // --------------------------------------------------------------------------- 3. Docker state
    const intents = (record.intents ?? []).filter((intent) => ['container', 'network', 'volume'].includes(intent.kind));
    let dockerResolved = true;
    const knownIds = new Set(intents.map((intent) => intent.dockerId).filter(Boolean));
    let daemon = { ok: false, why: 'no Docker work was recorded' };
    if (intents.length || record.docker) {
        daemon = verifyDaemon(record.docker, docker);
        if (!daemon.ok) {
            report(`Docker resources are preserved: ${daemon.why}`);
            for (const intent of intents) {
                if (!['removed', 'absent'].includes(intent.state)) {
                    setIntent(record, intent.id, { state: 'preserved', why: daemon.why });
                    dockerResolved = false;
                }
            }
        }
    }
    if (daemon.ok) {
        // Reconcile unresolved creation intents first, so an interrupted creation is proven absent
        // or adopted by exact identity before anything is removed.
        for (const intent of intents) {
            if (intent.dockerId || ['removed', 'absent'].includes(intent.state)) continue;
            if (intent.kind === 'volume') continue;
            const outcome = reconcileIntent(docker, intent, runId);
            if (!outcome.resolved) {
                setIntent(record, intent.id, { state: 'preserved', why: outcome.why });
                report(`unresolved ${intent.kind} creation intent ${intent.name}: ${outcome.why}`);
                dockerResolved = false;
            } else if (outcome.state === 'absent') {
                setIntent(record, intent.id, { state: 'absent', why: outcome.why });
                report(`${intent.kind} intent ${intent.name}: ${outcome.why}`);
            } else {
                setIntent(record, intent.id, { dockerId: outcome.id, why: outcome.why });
                knownIds.add(outcome.id);
                report(`${intent.kind} intent ${intent.name}: ${outcome.why} (${outcome.id})`);
            }
        }
        writeInventory(inventoryFile, record);

        // Anonymous volumes are discovered from the owned container's mounts and recorded durably
        // before the container is removed, so a crash cannot leave a volume nobody can account for.
        for (const intent of intents.filter((entry) => entry.kind === 'container' && entry.dockerId)) {
            const seen = inspectContainer(docker, intent.dockerId);
            if (seen.absent || seen.error) continue;
            for (const mount of seen.mounts) {
                if (mount.Type === 'bind') continue;
                if (mount.Type !== 'volume' || !mount.Name) continue;
                const already = (record.intents ?? []).some((entry) => entry.kind === 'volume' && entry.dockerId === mount.Name);
                if (already) continue;
                const anonymous = /^[0-9a-f]{64}$/.test(mount.Name);
                record.intents.push({
                    id: `v${record.intents.length + 1}`, kind: 'volume', name: mount.Name, dockerId: mount.Name,
                    state: anonymous ? 'created' : 'preserved', container: intent.dockerId, destination: mount.Destination,
                    exclusive: anonymous,
                    why: anonymous ? 'anonymous volume created by this run\'s container' : 'a pre-existing or named volume is not owned by this run',
                });
                report(anonymous
                    ? `recorded anonymous volume ${mount.Name} of container ${intent.dockerId} before removing it`
                    : `preserved named volume ${mount.Name}: it is not owned exclusively by this run`);
            }
        }
        writeInventory(inventoryFile, record);

        const removeGroup = async (kind) => {
            for (const intent of (record.intents ?? []).filter((entry) => entry.kind === kind)) {
                if (['removed', 'absent'].includes(intent.state)) continue;
                if (!intent.dockerId) { dockerResolved = false; continue; }
                if (kind === 'volume' && intent.exclusive !== true) { dockerResolved = false; continue; }
                if (kind !== 'volume') {
                    const seen = kind === 'container' ? inspectContainer(docker, intent.dockerId) : inspectSimple(docker, kind, intent.dockerId);
                    if (seen.absent) { setIntent(record, intent.id, { state: 'absent', why: 'already gone' }); continue; }
                    if (seen.error) {
                        setIntent(record, intent.id, { state: 'preserved', why: `ownership could not be read: ${seen.error}` });
                        report(`preserved ${kind} ${intent.dockerId}: ownership could not be read (${seen.error})`);
                        dockerResolved = false;
                        continue;
                    }
                    if (!ours(seen.labels, runId)) {
                        setIntent(record, intent.id, { state: 'preserved', why: 'the resource does not carry this run\'s labels' });
                        report(`preserved ${kind} ${intent.dockerId}: it does not carry this run's labels`);
                        dockerResolved = false;
                        continue;
                    }
                }
                const outcome = await removeVerified(docker, kind, intent.dockerId, runId, report);
                setIntent(record, intent.id, { state: outcome.state, why: outcome.note });
                if (outcome.state === 'removed' || outcome.state === 'absent') report(`${kind} ${intent.dockerId}: ${outcome.state} (${outcome.note})`);
                else dockerResolved = false;
                writeInventory(inventoryFile, record);
            }
        };
        // Containers hold the network and the anonymous volumes, so they go first; volumes next,
        // because `rm` without `-v` leaves them; the network last.
        await removeGroup('container');
        await removeGroup('volume');
        await removeGroup('network');
    }

    const findings = daemon.ok
        ? scanUnaccounted(docker, new Set([...knownIds, ...(record.intents ?? []).map((entry) => entry.dockerId).filter(Boolean)]), runId, otherActiveRunIds(registryRoot, ownKey), report)
        : { other: 0, unaccounted: [] };

    const pending = (record.intents ?? []).filter((intent) => !['removed', 'absent'].includes(intent.state));
    const complete = writersGone && buildResolved && dockerResolved && pending.length === 0 && findings.unaccounted.length === 0;
    // With its own process still alive the run stays nonterminal (`cleaning`), so a runner that dies
    // after teardown still leaves a record that blocks reuse until the coordinator verifies it.
    record.state = !complete ? 'incomplete' : (selfAlive ? 'cleaning' : 'complete');
    observe(record, complete
        ? 'verified absence of every owned process, build, container, volume and network'
        : `incomplete: ${pending.length} unresolved resource(s), ${findings.unaccounted.length} unaccounted, writers ${writersGone ? 'gone' : 'preserved'}`);
    record.finishedAt = new Date().toISOString().replace(/\.\d+Z$/, 'Z');
    writeInventory(inventoryFile, record);
    report(complete && selfAlive
        ? `run ${runId}: every owned resource was verified absent; completion awaits verification of this process' exit`
        : complete
        ? `run ${runId} is complete: every owned resource was verified absent`
        : `run ${runId} is INCOMPLETE; its inventory is retained for retry and this checkout stays blocked until recovery succeeds`);
    return { complete, record, report: lines };
}
