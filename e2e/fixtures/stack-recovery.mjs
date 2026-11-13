// The r2 cleanup and recovery engine (ADR008 CT34-FIXTURES-2026-11-10-r2, Section 10.3).
//
// Normal teardown and interrupted-run recovery use the *same* protocol and the same persistent
// inventory; the exclusive advisory lock is held by fixtures/lifecycle.py for the whole call. Only
// resources proven to belong exclusively to the recorded run are removed:
//
//   * processes are resolved by OS birth identity, never by PID, port or name pattern; an orphaned
//     preview is asked to close itself over its private control channel and there is no PID-based
//     fallback; anything live, unreadable, ambiguous or foreign is preserved. Writers are stopped
//     first: while any recorded writer is not verified as exited, neither the temporary build nor
//     any Docker resource is touched;
//   * every Docker call is bound to the recorded endpoint (DOCKER_HOST), and the daemon ID is checked
//     before work starts, behind every "no such resource" verdict and again before completion, so a
//     context switch, an unreachable daemon or another daemon can never be mistaken for absence.
//     Only Docker's own not-found responses for the exact queried ID count as absence; a connection
//     error, a timeout or an unparsable answer is inconclusive and preserves the resource;
//   * a previously recorded `removed`/`absent` state is never trusted on a later pass: every
//     recorded ID is inspected again, so a retry cannot complete over a resource that still exists;
//   * targets are the inventory's immutable IDs corroborated by the exact run labels; interrupted
//     creation intents are reconciled against the pinned daemon, never by deleting everything
//     carrying the generic disposable label, and never by a global prune;
//   * each container's volume association is an intent written before the container is created and
//     resolved from that container's own mounts; a container whose association is still unresolved is
//     never removed (a failed inspection preserves it for the next pass); a container that vanished
//     before its association was recorded leaves an unaccounted volume, never a completion. A volume
//     is removed only when it was recorded from this run's container and Docker itself marks it
//     anonymous;
//   * the runner leads a process group of its own; after it exits, the build and the stack stay
//     untouched while any process of that group (Playwright workers finishing) is still alive;
//   * only a direct child of the coordinator that holds the lock, acting for that coordinator's run,
//     may change the inventory: an orphan whose coordinator died stops before it writes anything;
//   * the temporary build directory is removed only after its name, parent, owner, device/inode and
//     run marker are verified without following a replacement symlink.
//
// Completion requires verified absence of every owned process, build directory, container, volume
// and network, with all creation intents and volume associations resolved, on the recorded daemon.
// Anything else is persisted as `incomplete`, returns a failure to the caller and keeps that checkout
// blocked until recovery succeeds. Resources this run does not own (other runs, pre-r2 leftovers) are
// reported for operator reconciliation and never removed; they do not block this run.
import { spawnSync } from 'node:child_process';
import { connect } from 'node:net';
import { lstatSync, readdirSync, rmSync } from 'node:fs';
import { basename, dirname, join } from 'node:path';
import { classifyProcess, hostIdentity, processGroup, sameHost, verifyDirectory } from './host-identity.mjs';
import { NONTERMINAL, OwnershipLost, observe, ownershipProblem, readInventory, setIntent, writeInventory } from './run-inventory.mjs';

export const STACK_LABEL = 'codemastery.e2e=disposable';
export const runLabel = (runId) => `codemastery.e2e.run=${runId}`;
export const ownerLabel = (uid = process.getuid()) => `codemastery.e2e.owner=${uid}`;
/** The label Docker itself puts on a volume it created anonymously for a container. */
const ANONYMOUS_VOLUME_LABEL = 'com.docker.volume.anonymous';
const DOCKER_TIMEOUT_MS = 30_000;
const REMOVE_ATTEMPTS = 3;
const RETRY_PAUSE_MS = 1000;
const pause = (ms) => new Promise((done) => setTimeout(done, ms));
const escapeRegExp = (text) => text.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
const isAnonymousName = (name) => /^[0-9a-f]{64}$/.test(name ?? '');

/**
 * True only when `detail` is Docker's own "not found" answer for exactly `id`. A transport error such
 * as `failed to connect to the docker API ... connect: no such file or directory`, a timeout or any
 * other daemon error is not absence.
 */
export function reportsNotFound(kind, id, detail) {
    const name = escapeRegExp(String(id));
    const forms = {
        container: `No such (?:container|object): ${name}`,
        network: `(?:No such (?:network|object): ${name}|network ${name} not found)`,
        volume: `(?:No such (?:volume|object): ${name}|get ${name}: no such volume)`,
    };
    return new RegExp(`^Error(?: response from daemon)?: ${forms[kind]}\\s*$`, 'm').test(detail ?? '');
}

/**
 * A bounded Docker CLI call that never throws and never hangs. With `endpoint`, every call is bound
 * to that daemon through DOCKER_HOST (with DOCKER_CONTEXT cleared), so neither a `docker context use`
 * elsewhere nor an inherited context can redirect it.
 */
export function makeDocker(endpoint = null, timeoutMs = DOCKER_TIMEOUT_MS) {
    const env = { ...process.env };
    if (endpoint) {
        env.DOCKER_HOST = endpoint;
        delete env.DOCKER_CONTEXT;
    }
    const call = (args, options = {}) => {
        const result = spawnSync('docker', args, { encoding: 'utf8', env, timeout: options.timeout ?? timeoutMs, maxBuffer: 8 * 1024 * 1024 });
        const detail = (result.stderr || result.error?.message || (result.status === null ? 'docker timed out' : `exit ${result.status}`)).trim();
        return { ok: result.status === 0, stdout: result.stdout ?? '', detail, timedOut: result.status === null };
    };
    call.endpoint = endpoint;
    call.at = (host) => makeDocker(host, timeoutMs);
    return call;
}
/** `docker` bound to `endpoint` when it supports binding (an injected test double is used as is). */
export const pinnedTo = (docker, endpoint) => (typeof docker.at === 'function' ? docker.at(endpoint) : docker);

/**
 * The Docker client endpoint, context and daemon ID to pin for this run. Only a daemon local to this
 * client host is supported (a Unix socket or Windows pipe, which includes a local Docker Desktop
 * VM); a remote or shared client context is refused for automatic lifecycle management, and a
 * context name on its own is never treated as daemon identity. The daemon ID is read through the
 * endpoint itself, so it names the daemon that endpoint reaches.
 */
export function dockerIdentity(docker = makeDocker()) {
    const context = docker(['context', 'show']);
    if (!context.ok) return { ok: false, why: `docker context show failed: ${context.detail}` };
    const name = context.stdout.trim();
    const endpoint = docker(['context', 'inspect', name, '--format', '{{.Endpoints.docker.Host}}']);
    if (!endpoint.ok) return { ok: false, why: `docker context inspect failed: ${endpoint.detail}` };
    const host = endpoint.stdout.trim();
    if (!/^(unix|npipe):\/\//.test(host)) {
        return { ok: false, why: `context ${name} uses ${host}; only a daemon local to this client host is supported for automatic lifecycle management` };
    }
    const info = pinnedTo(docker, host)(['info', '--format', '{{.ID}}']);
    if (!info.ok) return { ok: false, why: `docker info failed: ${info.detail}` };
    const daemonId = info.stdout.trim();
    if (!daemonId) return { ok: false, why: 'the daemon reported no ID' };
    return { ok: true, context: name, endpoint: host, daemonId, local: true };
}

/**
 * Confirms that the client still resolves to the recorded endpoint and that the recorded endpoint
 * still reaches the recorded daemon. Either kind of drift preserves the resources.
 */
function verifyDaemon(recorded, base, pinned) {
    if (!recorded) return { ok: false, why: 'no Docker endpoint was recorded for this run' };
    const current = dockerIdentity(base);
    if (!current.ok) return { ok: false, why: current.why };
    if (current.endpoint !== recorded.endpoint) {
        return { ok: false, why: `the Docker endpoint is now ${current.endpoint}, not the recorded ${recorded.endpoint}` };
    }
    const info = pinned(['info', '--format', '{{.ID}}']);
    const daemonId = info.ok ? info.stdout.trim() : null;
    if (daemonId !== recorded.daemonId) {
        return { ok: false, why: daemonId ? `the daemon ID is now ${daemonId}, not the recorded ${recorded.daemonId}` : `the recorded daemon cannot be reached: ${info.detail}` };
    }
    return { ok: true };
}

/**
 * Inspection bound to one daemon. Absence is reported only for Docker's not-found answer for the
 * exact ID *and* only after the pinned endpoint is confirmed to still reach the recorded daemon;
 * everything else is `{error}` and never counts as absence.
 */
function inspector(docker, daemonId) {
    const daemonProblem = () => {
        const info = docker(['info', '--format', '{{.ID}}']);
        if (!info.ok) return `the recorded daemon could not be re-identified (${info.detail})`;
        return info.stdout.trim() === daemonId ? null : `the endpoint now reaches daemon ${info.stdout.trim() || '(unidentified)'}`;
    };
    const absentOr = (kind, id, reply) => {
        if (!reportsNotFound(kind, id, reply.detail)) return { error: reply.detail || 'inspection failed', timedOut: reply.timedOut };
        const problem = daemonProblem();
        return problem ? { error: `absence not confirmed: ${problem}` } : { absent: true };
    };
    return {
        daemonProblem,
        absentOr,
        container(id) {
            const reply = docker(['inspect', '--type', 'container', '--format', '{{json .}}', id]);
            if (!reply.ok) return absentOr('container', id, reply);
            try {
                const parsed = JSON.parse(reply.stdout);
                return { labels: parsed.Config?.Labels ?? {}, mounts: parsed.Mounts ?? [], status: parsed.State?.Status ?? 'unknown', id: parsed.Id };
            } catch (error) {
                return { error: `unparsable inspect output: ${error.message}` };
            }
        },
        simple(kind, id) {
            const reply = docker([kind, 'inspect', '--format', '{{json .Labels}}', id]);
            if (!reply.ok) return absentOr(kind, id, reply);
            try {
                return { labels: JSON.parse(reply.stdout || 'null') ?? {} };
            } catch (error) {
                return { error: `unparsable inspect output: ${error.message}` };
            }
        },
    };
}

/** True when the resource's own labels corroborate the recorded run. */
const ours = (labels, runId) => labels?.['codemastery.e2e.run'] === runId && labels?.['codemastery.e2e'] === 'disposable';

/**
 * Records the volumes a container mounts as intents tied to that container. An anonymous volume (a
 * 64-hex name) is this run's exclusive volume; a named volume is pre-existing and preserved; bind
 * mounts are not volumes. Marks the container's volume association as recorded. Shared by setup
 * (right after creation) and recovery (for a container whose association is still pending).
 */
export function recordVolumeAssociation(record, containerIntent, mounts) {
    const added = [];
    for (const mount of mounts ?? []) {
        if (mount?.Type !== 'volume' || !mount.Name) continue;
        if ((record.intents ?? []).some((entry) => entry.kind === 'volume' && entry.dockerId === mount.Name)) continue;
        const anonymous = isAnonymousName(mount.Name);
        const intent = {
            id: `v${record.intents.length + 1}`, kind: 'volume', name: mount.Name, dockerId: mount.Name,
            state: anonymous ? 'created' : 'preserved', container: containerIntent.dockerId, destination: mount.Destination,
            exclusive: anonymous,
            why: anonymous ? `anonymous volume of ${containerIntent.name}` : 'a named or pre-existing volume is not owned by this run',
        };
        record.intents.push(intent);
        added.push(intent);
    }
    containerIntent.volumes = 'recorded';
    return added;
}

/** True when a container intent's volume association no longer needs work. */
const associationResolved = (record, intent) => intent.volumes === 'recorded' || intent.volumes === 'none'
    // Records written before associations were tracked: volumes recorded against this container.
    || (intent.volumes === undefined && !!intent.dockerId
        && (record.intents ?? []).some((entry) => entry.kind === 'volume' && entry.container === intent.dockerId));

/**
 * Removes one resource and re-inspects it, up to a bounded number of attempts. A removal that
 * reports success is still verified, so a timeout or a daemon error is never reported as absence.
 */
async function removeVerified(docker, look, kind, id, report) {
    const remove = kind === 'container' ? ['rm', '-f', id] : [kind, 'rm', id];
    for (let attempt = 1; attempt <= REMOVE_ATTEMPTS; attempt++) {
        const result = docker(remove);
        const seen = kind === 'container' ? look.container(id) : look.simple(kind, id);
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
 * on the pinned daemon carrying this run's own labels and recorded name. No match (confirmed on the
 * recorded daemon) means the creation did not take effect or the resource is gone; several matches
 * stay unresolved.
 */
function reconcileIntent(docker, look, intent, runId) {
    const filters = ['--filter', `label=${STACK_LABEL}`, '--filter', `label=${runLabel(runId)}`];
    const list = intent.kind === 'container'
        ? docker(['ps', '-aq', ...filters, '--filter', `name=^${intent.name}$`])
        : docker(['network', 'ls', '-q', ...filters, '--filter', `name=^${intent.name}$`]);
    if (!list.ok) return { resolved: false, why: `could not list ${intent.kind}s to reconcile the intent: ${list.detail}` };
    const ids = list.stdout.split('\n').map((line) => line.trim()).filter(Boolean);
    if (ids.length > 1) return { resolved: false, why: `${ids.length} resources match the intent ${intent.name}; ownership is ambiguous` };
    if (ids.length === 1) return { resolved: true, state: 'created', id: ids[0], why: 'one exact run-labelled match adopted from the creation intent' };
    const problem = look.daemonProblem();
    if (problem) return { resolved: false, why: `no match, but absence is not confirmed: ${problem}` };
    return { resolved: true, state: 'absent', why: 'the recorded creation intent left nothing on this daemon' };
}

/**
 * Settles the volume association of a container intent whose association is still pending. A live
 * container's mounts are recorded. A container that is confirmed gone leaves an unaccounted volume,
 * unless the container never existed and its exact image declares no volume, in which case no
 * anonymous volume can have been created for it.
 */
function settleAssociation(docker, look, record, intent, report) {
    if (associationResolved(record, intent)) return true;
    if (intent.dockerId) {
        const seen = look.container(intent.dockerId);
        if (seen.error) { report(`the volume association of container ${intent.dockerId} is unresolved: ${seen.error}`); return false; }
        if (!seen.absent) {
            for (const added of recordVolumeAssociation(record, intent, seen.mounts)) {
                report(added.exclusive ? `recorded anonymous volume ${added.dockerId} of container ${intent.dockerId} before removing it`
                    : `preserved named volume ${added.dockerId}: it is not owned exclusively by this run`);
            }
            return true;
        }
        intent.volumes = 'unaccounted';
        intent.volumesWhy = 'the container vanished before its volume association was recorded; an anonymous volume may remain';
        report(`unaccounted volume: container ${intent.dockerId} (${intent.name}) vanished before its volumes were recorded; nothing is searched or pruned and recovery stays incomplete`);
        return false;
    }
    if (intent.state === 'absent' && intent.image) {
        const reply = docker(['image', 'inspect', '--format', '{{json .Config.Volumes}}', intent.image]);
        let declared;
        try { declared = reply.ok ? JSON.parse(reply.stdout || 'null') : undefined; } catch { declared = undefined; }
        if (declared === null || (declared && typeof declared === 'object' && Object.keys(declared).length === 0)) {
            intent.volumes = 'none';
            intent.volumesWhy = 'no container exists for the intent and its exact image declares no volume';
            return true;
        }
        // An exact image ID that does not exist on the recorded daemon cannot have produced a
        // container (a failed `docker run` of a missing image), so it cannot have produced a volume.
        const missing = new RegExp(`^Error(?: response from daemon)?: No such image: ${escapeRegExp(intent.image)}\\s*$`, 'm');
        if (!reply.ok && missing.test(reply.detail ?? '') && !look.daemonProblem()) {
            intent.volumes = 'none';
            intent.volumesWhy = 'no container exists for the intent and its exact image is not on the recorded daemon';
            return true;
        }
    }
    intent.volumes = 'unaccounted';
    intent.volumesWhy = 'the container is not on the daemon and its volume association was never recorded';
    report(`unaccounted volume: container intent ${intent.name} has no recorded volume association and no container to read it from; recovery stays incomplete`);
    return false;
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
        const verdict = classifyProcess(recorded, { host });
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
 * Looks for Docker resources this inventory does not account for. A container or network carrying
 * *this* run's label that the inventory does not name is this run's unaccounted resource and blocks
 * completion. Anything else carrying the generic disposable label (another checkout's live run, a
 * pre-r2 PID-only leftover, a run no current inventory owns) is not this run's: it is reported for
 * explicit operator reconciliation, never removed, and does not block this run.
 */
function scanUnaccounted(docker, knownIds, runId, liveElsewhere, report) {
    const findings = { other: 0, foreign: [], unaccounted: [] };
    for (const [kind, list] of [['container', ['ps', '-aq']], ['network', ['network', 'ls', '-q']]]) {
        if (runId) {
            const own = docker([...list, '--filter', `label=${runLabel(runId)}`]);
            if (!own.ok) findings.unaccounted.push({ kind, id: '(listing failed)', why: `this run's ${kind}s could not be listed: ${own.detail}` });
            else {
                for (const id of own.stdout.split('\n').map((line) => line.trim()).filter(Boolean)) {
                    if (!knownIds.has(id)) findings.unaccounted.push({ kind, id, why: 'it carries this run\'s label but is not in its inventory' });
                }
            }
        }
        const all = docker([...list, '--filter', `label=${STACK_LABEL}`]);
        if (!all.ok) { report(`could not scan for other disposable ${kind}s: ${all.detail}`); continue; }
        for (const id of all.stdout.split('\n').map((line) => line.trim()).filter(Boolean)) {
            if (knownIds.has(id) || findings.unaccounted.some((entry) => entry.id === id)) continue;
            const reply = kind === 'container'
                ? docker(['inspect', '--type', 'container', '--format', '{{json .}}', id])
                : docker(['network', 'inspect', '--format', '{{json .Labels}}', id]);
            let labels = null;
            try {
                const parsed = reply.ok ? JSON.parse(reply.stdout || 'null') : null;
                labels = parsed === null ? null : (kind === 'container' ? parsed.Config?.Labels ?? {} : parsed ?? {});
            } catch { labels = null; }
            const owner = labels?.['codemastery.e2e.run'];
            if (owner && owner === runId) continue; // listed above when unaccounted
            if (owner && liveElsewhere.has(owner)) { findings.other++; continue; }
            findings.foreign.push({ kind, id, why: labels === null ? 'its labels could not be read'
                : owner ? `labelled for run ${owner}, which no current inventory owns` : 'it carries no run label (a pre-r2 leftover)' });
        }
    }
    if (findings.other) report(`${findings.other} resource(s) belong to another checkout's live run and were left untouched`);
    for (const entry of findings.foreign) {
        report(`left ${entry.kind} ${entry.id} untouched: ${entry.why}; it is not this run's, is never removed automatically and needs operator reconciliation`);
    }
    for (const entry of findings.unaccounted) report(`preserved ${entry.kind} ${entry.id}: ${entry.why}; recovery stays incomplete`);
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
    const lines = [];
    const report = (text) => { lines.push(text); if (options.log !== false) console.log(`stack-down: ${text}`); };
    try {
        return await reconcileOwned(inventoryFile, options, lines, report);
    } catch (error) {
        if (!(error instanceof OwnershipLost)) throw error;
        report(error.message);
        report('nothing further was changed: the lock holder (recovery or the next admitted run) owns this record now');
        return { complete: false, record: null, report: lines, ownershipLost: true };
    }
}

async function reconcileOwned(inventoryFile, options, lines, report) {
    const base = options.docker ?? makeDocker();
    const host = options.host ?? hostIdentity();
    const groupOf = options.processGroup ?? processGroup;
    const relation = { env: options.env ?? process.env, ppid: options.ppid ?? process.ppid };
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
        scanUnaccounted(base, new Set(), null, otherActiveRunIds(registryRoot, ownKey), report);
        return { complete: true, record: null, report: lines };
    }

    /** Every write re-checks that this process still acts for the coordinator holding the lock. */
    const persist = () => {
        const problem = ownershipProblem(record, relation);
        if (problem) throw new OwnershipLost(`refusing to change inventory ${inventoryFile}: ${problem}`);
        writeInventory(inventoryFile, record);
    };
    const runId = record.runId;
    record.state = 'cleaning';
    observe(record, `reconciliation started (${options.reason ?? 'unspecified'})`);
    persist();

    // ---------------------------------------------------------------- 1. writers (processes first)
    const sameMachine = sameHost(record.host, host);
    let writersGone = true;
    let selfAlive = false;
    // The preview is the runner's own child and serves the runner's tests, so it is handled last and
    // only asked to close once every other writer is verified gone: a coordinator's death alone never
    // authorizes stopping what a surviving runner is still using.
    const entries = Object.entries(record.processes ?? {}).sort(([a], [b]) => (a === 'preview') - (b === 'preview'));
    for (const [role, recorded] of entries) {
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
        if (verdict.state === 'exact-live' && role === 'preview' && !writersGone) {
            report(`preview ${recorded.pid} is left running: the runner or another writer that uses it has not been verified as exited`);
        } else if (verdict.state === 'exact-live' && role === 'preview') {
            const socketPath = recorded.control;
            const expected = registryRoot ? join(registryRoot, 'sockets', `${runId}.sock`) : null;
            if (!socketPath) {
                report(`preview ${recorded.pid} is live but no control channel was recorded; it is preserved`);
            } else if ((expected && socketPath !== expected) || basename(socketPath) !== `${runId}.sock`) {
                report(`preview ${recorded.pid} is live but its recorded control path is not this run's private channel; it is preserved`);
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
            recorded.exitVerdict = verdict.state;
            report(`${role} ${recorded.pid} is verifiably gone (${verdict.why})`);
            // A preview that died without closing leaves its socket file. It is removed only when it is
            // this run's own name inside the private registry and is a socket, never another path.
            if (role === 'preview' && recorded.control && basename(recorded.control) === `${runId}.sock`
                && (!registryRoot || dirname(recorded.control) === join(registryRoot, 'sockets'))) {
                try {
                    if (lstatSync(recorded.control).isSocket()) rmSync(recorded.control, { force: true });
                } catch { /* already gone */ }
            }
        } else {
            recorded.state = 'preserved';
            recorded.why = verdict.why;
            report(`preserved ${role} ${recorded.pid ?? '(no PID recorded)'}: ${verdict.why}`);
            writersGone = false;
        }
    }
    // The runner leads its own process group (the coordinator starts it in a new session), which
    // holds every process it started: Playwright workers, the build and the preview. Once the runner
    // is gone, a live member can still write outputs (a disconnected Playwright worker finishes its
    // teardown before exiting), so nothing it may be using is removed until that group is empty.
    const runner = record.processes?.runner;
    if (runner && runner.state === 'exited' && Number.isInteger(runner.group) && runner.group === runner.pid && runner.groupState !== 'empty') {
        if (!sameMachine) {
            report(`the runner's process group ${runner.group} was recorded on another host, boot or PID namespace; it is preserved`);
            writersGone = false;
        } else if (runner.exitVerdict === 'pid-reused') {
            // While any member of a group is alive the kernel never hands out its ID as a new PID, so a
            // reused runner PID proves the runner's group has no member left.
            runner.groupState = 'empty';
            report(`the runner's process group ${runner.group} has no member left (its leader PID now belongs to another process)`);
        } else {
            const group = groupOf(runner.group);
            if (!group.reliable) {
                report(`the runner's process group ${runner.group} could not be inspected (${group.detail ?? 'unknown'}); the build and the stack are preserved`);
                writersGone = false;
            } else if (group.members.length) {
                runner.groupState = 'live';
                report(`the runner's process group ${runner.group} still has ${group.members.length} live process(es) (for example Playwright workers finishing); the build and the stack are preserved until they exit`);
                writersGone = false;
            } else {
                runner.groupState = 'empty';
                report(`the runner's process group ${runner.group} has no live member`);
            }
        }
    }
    persist();

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
    persist();

    // --------------------------------------------------------------------------- 3. Docker state
    const dockerIntents = () => (record.intents ?? []).filter((intent) => ['container', 'network', 'volume'].includes(intent.kind));
    const hasDockerWork = dockerIntents().length > 0 || !!record.docker;
    let dockerResolved = true;
    let unaccounted = 0;
    if (hasDockerWork && !writersGone) {
        // Stop verified writers first (Section 10.3): a live or unverifiable runner may still be using
        // and recording this stack, so nothing of it is removed and the inventory is left to its owner.
        report('Docker resources are preserved: a recorded writer has not been verified as exited');
        dockerResolved = false;
    } else if (hasDockerWork) {
        const docker = record.docker?.endpoint ? pinnedTo(base, record.docker.endpoint) : base;
        const daemon = verifyDaemon(record.docker, base, docker);
        if (!daemon.ok) {
            report(`Docker resources are preserved: ${daemon.why}`);
            for (const intent of dockerIntents()) {
                if (!['removed', 'absent'].includes(intent.state)) setIntent(record, intent.id, { state: 'preserved', why: daemon.why });
            }
            dockerResolved = false;
        } else {
            const look = inspector(docker, record.docker.daemonId);
            // a. Creation intents whose ID was never persisted are reconciled on every pass, so an
            //    interrupted creation is proven absent or adopted by exact identity before anything
            //    is removed; an earlier verdict is never reused.
            for (const intent of dockerIntents()) {
                if (intent.dockerId || intent.kind === 'volume') continue;
                const outcome = reconcileIntent(docker, look, intent, runId);
                if (!outcome.resolved) {
                    setIntent(record, intent.id, { state: 'preserved', why: outcome.why });
                    report(`unresolved ${intent.kind} creation intent ${intent.name}: ${outcome.why}`);
                    dockerResolved = false;
                } else if (outcome.state === 'absent') {
                    setIntent(record, intent.id, { state: 'absent', why: outcome.why });
                    report(`${intent.kind} intent ${intent.name}: ${outcome.why}`);
                } else {
                    setIntent(record, intent.id, { dockerId: outcome.id, state: 'created', why: outcome.why });
                    report(`${intent.kind} intent ${intent.name}: ${outcome.why} (${outcome.id})`);
                }
            }
            persist();

            // b. Volume associations are settled from each container's own mounts and recorded
            //    durably before that container is removed.
            for (const intent of dockerIntents().filter((entry) => entry.kind === 'container')) {
                if (!settleAssociation(docker, look, record, intent, report)) dockerResolved = false;
            }
            persist();

            // c. Every recorded ID is inspected again, whatever an earlier pass recorded.
            const removeGroup = async (kind) => {
                for (const intent of dockerIntents().filter((entry) => entry.kind === kind)) {
                    if (!intent.dockerId) {
                        if (intent.state !== 'absent') dockerResolved = false;
                        continue;
                    }
                    if (kind === 'container' && !associationResolved(record, intent) && intent.volumes !== 'unaccounted') {
                        // Removing it now would lose the only source of its anonymous volume's identity.
                        setIntent(record, intent.id, { state: 'preserved', why: 'its volume association is not recorded yet; it is kept so a later pass can read its mounts first' });
                        report(`preserved container ${intent.dockerId}: its volume association is unresolved, so it is not removed before its mounts are recorded`);
                        dockerResolved = false;
                        continue;
                    }
                    const seen = kind === 'container' ? look.container(intent.dockerId) : look.simple(kind, intent.dockerId);
                    if (seen.absent) {
                        if (!['removed', 'absent'].includes(intent.state)) setIntent(record, intent.id, { state: 'absent', why: 'verified absent on the recorded daemon' });
                        continue;
                    }
                    if (seen.error) {
                        setIntent(record, intent.id, { state: 'preserved', why: `ownership could not be read: ${seen.error}` });
                        report(`preserved ${kind} ${intent.dockerId}: ownership could not be read (${seen.error})`);
                        dockerResolved = false;
                        continue;
                    }
                    const proven = kind === 'volume'
                        ? intent.exclusive === true && isAnonymousName(intent.dockerId) && Object.hasOwn(seen.labels ?? {}, ANONYMOUS_VOLUME_LABEL)
                        : ours(seen.labels, runId);
                    if (!proven) {
                        const why = kind === 'volume' ? 'it is not proven to be an anonymous volume created for this run\'s container' : 'it does not carry this run\'s labels';
                        setIntent(record, intent.id, { state: 'preserved', why });
                        report(`preserved ${kind} ${intent.dockerId}: ${why}`);
                        dockerResolved = false;
                        continue;
                    }
                    const outcome = await removeVerified(docker, look, kind, intent.dockerId, report);
                    setIntent(record, intent.id, { state: outcome.state, why: outcome.note });
                    if (outcome.state === 'removed' || outcome.state === 'absent') report(`${kind} ${intent.dockerId}: ${outcome.state} (${outcome.note})`);
                    else dockerResolved = false;
                    persist();
                }
            };
            // Containers hold the network and the anonymous volumes, so they go first; volumes next,
            // because `rm` without `-v` leaves them; the network last.
            await removeGroup('container');
            await removeGroup('volume');
            await removeGroup('network');

            const known = new Set(dockerIntents().map((entry) => entry.dockerId).filter(Boolean));
            unaccounted = scanUnaccounted(docker, known, runId, otherActiveRunIds(registryRoot, ownKey), report).unaccounted.length;

            // d. Every verdict above was reached on the recorded daemon only if it is still the one
            //    the endpoint reaches now.
            const drift = look.daemonProblem();
            if (drift) {
                report(`Docker verdicts are not trusted: ${drift}`);
                dockerResolved = false;
            }
        }
    }

    const pending = dockerIntents().filter((intent) => !['removed', 'absent'].includes(intent.state));
    const associations = dockerIntents().filter((intent) => intent.kind === 'container' && !associationResolved(record, intent));
    const complete = writersGone && buildResolved && dockerResolved && pending.length === 0 && associations.length === 0 && unaccounted === 0;
    // With its own process still alive the run stays nonterminal (`cleaning`), so a runner that dies
    // after teardown still leaves a record that blocks reuse until the coordinator verifies it.
    record.state = !complete ? 'incomplete' : (selfAlive ? 'cleaning' : 'complete');
    observe(record, complete
        ? 'verified absence of every owned process, build, container, volume and network'
        : `incomplete: ${pending.length} unresolved resource(s), ${associations.length} unresolved volume association(s), ${unaccounted} unaccounted, writers ${writersGone ? 'gone' : 'preserved'}`);
    record.finishedAt = new Date().toISOString().replace(/\.\d+Z$/, 'Z');
    persist();
    report(complete && selfAlive
        ? `run ${runId}: every owned resource was verified absent; completion awaits verification of this process' exit`
        : complete
        ? `run ${runId} is complete: every owned resource was verified absent`
        : `run ${runId} is INCOMPLETE; its inventory is retained for retry and this checkout stays blocked until recovery succeeds`);
    return { complete, record, report: lines };
}
