// Regression tests for the r2 cleanup and recovery engine (ADR008 CT34-FIXTURES-2026-11-10-r2,
// Sections 10.3 and 10.4). The Docker CLI is injected as an in-process fake, so every call is
// recorded and every failure mode — a daemon that drifted, a removal that times out, a resource
// whose ownership cannot be read, a volume that was never inventoried — can be injected exactly.
//
// These are stub scenarios: they prove the protocol's decisions, not Docker integration itself.
// The real-Docker lifecycle is a separate, recorded verification.
import { afterEach, beforeEach, describe, test } from 'node:test';
import { spawn } from 'node:child_process';
import assert from 'node:assert/strict';
import { existsSync, mkdirSync, mkdtempSync, rmSync, writeFileSync } from 'node:fs';
import { lstatSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, join } from 'node:path';
import { markerFile, readInventory, writeInventory } from './run-inventory.mjs';
import { STACK_LABEL, dockerIdentity, reconcile, runLabel } from './stack-recovery.mjs';
import { hostIdentity, processIdentity } from './host-identity.mjs';

const HOST = hostIdentity();
const RUN = 'cm-e2e-stub';
const DAEMON = { context: 'stub-context', endpoint: 'unix:///var/run/docker.sock', daemonId: 'STUB-DAEMON-1' };
const ANON = 'a'.repeat(64);

let scratch = '';
let inventory = '';
const sleepers = [];
/** A separate live process standing in for a writer this reconciliation does not own the handle of. */
async function liveWriter() {
    const child = spawn(process.execPath, ['-e', 'setInterval(() => {}, 1000)'], { stdio: 'ignore' });
    sleepers.push(child);
    for (let at = 0; at < 50 && !processIdentity(child.pid).exists; at++) await new Promise((next) => setTimeout(next, 20));
    return { pid: child.pid, identity: processIdentity(child.pid), child };
}
afterEach(() => { for (const child of sleepers.splice(0)) child.kill('SIGKILL'); });
beforeEach(() => {
    scratch = mkdtempSync(join(tmpdir(), 'codemastery-recovery-test-'));
    inventory = join(scratch, 'run.json');
});

/**
 * A fake Docker CLI. `state` maps resource ID to `{kind, labels, mounts, present, removeFails,
 * inspectFails, timesOut}`; every call is appended to `calls`.
 */
function fakeDocker(state = {}, options = {}) {
    const calls = [];
    const fake = (args) => {
        calls.push(args.join(' '));
        const reply = (stdout) => ({ ok: true, stdout, detail: '', timedOut: false });
        const error = (detail, timedOut = false) => ({ ok: false, stdout: '', detail, timedOut });
        if (args[0] === 'context' && args[1] === 'show') return options.contextFails ? error('no context') : reply(`${options.context ?? DAEMON.context}\n`);
        if (args[0] === 'context' && args[1] === 'inspect') return reply(`${options.endpoint ?? DAEMON.endpoint}\n`);
        if (args[0] === 'info') return options.infoFails ? error('Cannot connect to the Docker daemon') : reply(`${options.daemonId ?? DAEMON.daemonId}\n`);
        const id = args.at(-1);
        const kindOf = (name) => (name === 'network' ? 'network' : name === 'volume' ? 'volume' : 'container');
        if (args[0] === 'inspect' || (args[1] === 'inspect' && ['network', 'volume'].includes(args[0]))) {
            const target = state[id];
            if (!target) return error(`Error: No such object: ${id}`);
            if (target.inspectFails) return error('Error response from daemon: i/o timeout', true);
            if (!target.present) return error(`Error: No such ${target.kind}: ${id}`);
            if (args[0] === 'inspect') {
                return reply(args.includes('{{json .Mounts}}')
                    ? JSON.stringify(target.mounts ?? [])
                    : JSON.stringify({ Id: id, Config: { Labels: target.labels ?? {} }, Mounts: target.mounts ?? [], State: { Status: 'exited' } }));
            }
            return reply(JSON.stringify(target.labels ?? {}));
        }
        if (args[0] === 'rm' || (args[1] === 'rm' && ['network', 'volume'].includes(args[0]))) {
            const target = state[id];
            if (!target) return error(`Error: No such object: ${id}`);
            if (target.timesOut) return error('docker timed out', true);
            if (target.removeFails) return error('Error response from daemon: removal refused');
            target.present = false;
            return reply(id);
        }
        if (args[0] === 'ps' || (args[0] === 'network' && args[1] === 'ls') || (args[0] === 'volume' && args[1] === 'ls')) {
            const want = kindOf(args[0] === 'ps' ? 'container' : args[0]);
            const named = args.includes('--filter') ? args.filter((arg, at) => args[at - 1] === '--filter') : [];
            const nameFilter = named.find((filter) => filter.startsWith('name=^'))?.slice(6, -1);
            const runFilter = named.find((filter) => filter.startsWith(`label=codemastery.e2e.run=`))?.split('=').at(-1);
            const ids = Object.entries(state).filter(([, target]) => target.present && target.kind === want
                && (!nameFilter || target.name === nameFilter)
                && (!runFilter || target.labels?.['codemastery.e2e.run'] === runFilter)
                && (!named.some((filter) => filter === `label=${STACK_LABEL}`) || target.labels?.['codemastery.e2e'] === 'disposable'))
                .map(([key]) => key);
            return reply(`${ids.join('\n')}\n`);
        }
        return error(`unexpected docker ${args.join(' ')}`);
    };
    fake.calls = calls;
    return fake;
}

const labels = (run = RUN) => ({ 'codemastery.e2e': 'disposable', [`codemastery.e2e.run`]: run, 'codemastery.e2e.owner': String(process.getuid()) });
/** Writes a record describing a run that owns a network, two containers and an anonymous volume. */
function seed(patch = {}) {
    const record = {
        schema: 'codemastery-e2e-inventory/1', contract: 'CT34-FIXTURES-2026-11-10-r2', runId: RUN,
        state: 'active', ownerUid: process.getuid(), lockKey: 'test',
        host: { hostname: HOST.hostname, boot: HOST.boot, pidNamespace: HOST.pidNamespace, system: HOST.system },
        checkout: { path: scratch, dev: 1, ino: 1 }, outputs: [], processes: {}, docker: { ...DAEMON },
        temp: null, intents: [], observations: [], ...patch,
    };
    writeInventory(inventory, record);
    return record;
}
const run = (docker, options = {}) => reconcile(inventory, { docker, host: HOST, log: false, reason: 'test', registryRoot: null, ...options });
const stateOf = (record, name) => record.intents.find((intent) => intent.name === name)?.state;

describe('Docker ownership is pinned to the recorded daemon', () => {
    test('a local unix endpoint with a daemon ID is accepted; a tcp context is refused', () => {
        assert.deepEqual(dockerIdentity(fakeDocker()), { ok: true, ...DAEMON, local: true });
        const remote = dockerIdentity(fakeDocker({}, { endpoint: 'tcp://10.0.0.5:2376' }));
        assert.equal(remote.ok, false);
        assert.match(remote.why, /only a daemon local to this client host is supported/);
        assert.equal(dockerIdentity(fakeDocker({}, { infoFails: true })).ok, false);
    });

    test('a changed daemon ID preserves every resource and fails closed, with no removal attempted', async () => {
        seed({ intents: [{ id: 'i1', kind: 'container', name: 'c', dockerId: 'C1', state: 'created' }] });
        const docker = fakeDocker({ C1: { kind: 'container', name: 'c', present: true, labels: labels() } }, { daemonId: 'ANOTHER-DAEMON' });
        const outcome = await run(docker);
        assert.equal(outcome.complete, false);
        assert.equal(stateOf(outcome.record, 'c'), 'preserved');
        assert.ok(!docker.calls.some((call) => call.startsWith('rm ')), docker.calls.join('\n'));
        assert.match(outcome.report.join('\n'), /the daemon ID is now ANOTHER-DAEMON/);
        assert.equal(readInventory(inventory).state, 'incomplete');
    });

    test('a changed endpoint is refused even when the daemon ID matches', async () => {
        seed({ intents: [{ id: 'i1', kind: 'network', name: 'n', dockerId: 'N1', state: 'created' }] });
        const outcome = await run(fakeDocker({ N1: { kind: 'network', name: 'n', present: true, labels: labels() } }, { endpoint: 'unix:///other/docker.sock' }));
        assert.equal(outcome.complete, false);
        assert.match(outcome.report.join('\n'), /the Docker endpoint is now unix:\/\/\/other\/docker.sock/);
    });
});

describe('only resources proven to belong to the recorded run are removed', () => {
    test('a full owned stack is removed in order and verified absent', async () => {
        seed({
            intents: [
                { id: 'i1', kind: 'network', name: RUN, dockerId: 'N1', state: 'created' },
                { id: 'i2', kind: 'container', name: `${RUN}-postgres`, dockerId: 'C1', state: 'created' },
                { id: 'i3', kind: 'volume', name: ANON, dockerId: ANON, container: 'C1', exclusive: true, state: 'created' },
            ],
        });
        const docker = fakeDocker({
            N1: { kind: 'network', name: RUN, present: true, labels: labels() },
            C1: { kind: 'container', name: `${RUN}-postgres`, present: true, labels: labels(), mounts: [{ Type: 'volume', Name: ANON, Destination: '/var/lib/postgresql/data' }] },
            [ANON]: { kind: 'volume', name: ANON, present: true, labels: {} },
        });
        const outcome = await run(docker);
        assert.equal(outcome.complete, true, outcome.report.join('\n'));
        assert.deepEqual(outcome.record.intents.map((intent) => intent.state), ['removed', 'removed', 'removed']);
        const order = docker.calls.filter((call) => /^(rm |network rm|volume rm)/.test(call));
        assert.deepEqual(order, ['rm -f C1', `volume rm ${ANON}`, 'network rm N1'], 'containers, then volumes, then the network');
        assert.equal(readInventory(inventory).state, 'complete');
    });

    test('a resource that does not carry this run\'s labels is preserved, not removed', async () => {
        seed({ intents: [{ id: 'i1', kind: 'container', name: 'c', dockerId: 'C1', state: 'created' }] });
        const docker = fakeDocker({ C1: { kind: 'container', name: 'c', present: true, labels: labels('cm-e2e-someone-else') } });
        const outcome = await run(docker);
        assert.equal(outcome.complete, false);
        assert.equal(stateOf(outcome.record, 'c'), 'preserved');
        assert.ok(!docker.calls.includes('rm -f C1'));
        assert.match(outcome.report.join('\n'), /does not carry this run's labels/);
    });

    test('a resource whose ownership cannot be read is preserved and reported', async () => {
        seed({ intents: [{ id: 'i1', kind: 'container', name: 'c', dockerId: 'C1', state: 'created' }] });
        const outcome = await run(fakeDocker({ C1: { kind: 'container', name: 'c', present: true, inspectFails: true, labels: labels() } }));
        assert.equal(outcome.complete, false);
        assert.equal(stateOf(outcome.record, 'c'), 'preserved');
        assert.match(outcome.report.join('\n'), /ownership could not be read/);
    });

    test('an already absent resource counts as absent, not as a failure', async () => {
        seed({ intents: [{ id: 'i1', kind: 'container', name: 'c', dockerId: 'C1', state: 'created' }] });
        const outcome = await run(fakeDocker({ C1: { kind: 'container', name: 'c', present: false, labels: labels() } }));
        assert.equal(outcome.complete, true, outcome.report.join('\n'));
        assert.equal(stateOf(outcome.record, 'c'), 'absent');
    });

    test('no global prune or label-only deletion is ever issued', async () => {
        seed({
            intents: [{ id: 'i1', kind: 'container', name: 'c', dockerId: 'C1', state: 'created' }],
        });
        const docker = fakeDocker({ C1: { kind: 'container', name: 'c', present: true, labels: labels() } });
        await run(docker);
        assert.ok(!docker.calls.some((call) => /prune/.test(call)), docker.calls.join('\n'));
        assert.ok(!docker.calls.some((call) => /^rm -f --filter|^rm .*label=/.test(call)));
    });
});

describe('removal failures are bounded, visible and retryable', () => {
    test('a refused removal is retried a bounded number of times and then recorded as failed', async () => {
        seed({ intents: [{ id: 'i1', kind: 'container', name: 'c', dockerId: 'C1', state: 'created' }] });
        const docker = fakeDocker({ C1: { kind: 'container', name: 'c', present: true, removeFails: true, labels: labels() } });
        const outcome = await run(docker);
        assert.equal(outcome.complete, false);
        assert.equal(stateOf(outcome.record, 'c'), 'failed');
        assert.equal(docker.calls.filter((call) => call === 'rm -f C1').length, 3, 'bounded retries');
        assert.match(outcome.report.join('\n'), /still present after attempt 1/);
        assert.equal(readInventory(inventory).state, 'incomplete');
    });

    test('a removal that times out is never reported as absence', async () => {
        seed({ intents: [{ id: 'i1', kind: 'network', name: 'n', dockerId: 'N1', state: 'created' }] });
        const outcome = await run(fakeDocker({ N1: { kind: 'network', name: 'n', present: true, timesOut: true, labels: labels() } }));
        assert.equal(outcome.complete, false);
        assert.equal(stateOf(outcome.record, 'n'), 'failed');
        assert.match(outcome.report.join('\n'), /still present after attempt/);
    });

    test('retrying the same run after a failure is idempotent and completes once the resource is gone', async () => {
        seed({ intents: [{ id: 'i1', kind: 'container', name: 'c', dockerId: 'C1', state: 'created' }] });
        const state = { C1: { kind: 'container', name: 'c', present: true, removeFails: true, labels: labels() } };
        assert.equal((await run(fakeDocker(state))).complete, false);
        state.C1.removeFails = false;
        const second = await run(fakeDocker(state));
        assert.equal(second.complete, true, second.report.join('\n'));
        assert.equal(stateOf(second.record, 'c'), 'removed');
        const third = await run(fakeDocker(state));
        assert.equal(third.complete, true, 'a third pass over a finished run stays complete');
    });
});

describe('interrupted creation intents are reconciled, never assumed', () => {
    test('a pending intent with nothing on the daemon resolves to absent', async () => {
        seed({ intents: [{ id: 'i1', kind: 'container', name: `${RUN}-postgres`, state: 'pending' }] });
        const outcome = await run(fakeDocker({}));
        assert.equal(outcome.complete, true, outcome.report.join('\n'));
        assert.equal(stateOf(outcome.record, `${RUN}-postgres`), 'absent');
    });

    test('a pending intent with exactly one run-labelled match is adopted and removed', async () => {
        seed({ intents: [{ id: 'i1', kind: 'container', name: `${RUN}-postgres`, state: 'pending' }] });
        const docker = fakeDocker({ C9: { kind: 'container', name: `${RUN}-postgres`, present: true, labels: labels() } });
        const outcome = await run(docker);
        assert.equal(outcome.complete, true, outcome.report.join('\n'));
        assert.equal(outcome.record.intents[0].dockerId, 'C9');
        assert.ok(docker.calls.includes('rm -f C9'));
    });

    test('a pending intent with several matches stays unresolved and blocks completion', async () => {
        seed({ intents: [{ id: 'i1', kind: 'network', name: RUN, state: 'pending' }] });
        const outcome = await run(fakeDocker({
            N1: { kind: 'network', name: RUN, present: true, labels: labels() },
            N2: { kind: 'network', name: RUN, present: true, labels: labels() },
        }));
        assert.equal(outcome.complete, false);
        assert.match(outcome.report.join('\n'), /2 resources match the intent .*ownership is ambiguous/);
        assert.equal(stateOf(outcome.record, RUN), 'preserved');
    });
});

describe('anonymous volumes are inventoried from the owned container before it is removed', () => {
    test('an uninventoried anonymous volume is recorded and removed after its container', async () => {
        seed({ intents: [{ id: 'i1', kind: 'container', name: `${RUN}-postgres`, dockerId: 'C1', state: 'created' }] });
        const docker = fakeDocker({
            C1: { kind: 'container', name: `${RUN}-postgres`, present: true, labels: labels(), mounts: [{ Type: 'volume', Name: ANON, Destination: '/var/lib/postgresql/data' }] },
            [ANON]: { kind: 'volume', name: ANON, present: true, labels: {} },
        });
        const outcome = await run(docker);
        assert.equal(outcome.complete, true, outcome.report.join('\n'));
        const volume = outcome.record.intents.find((intent) => intent.kind === 'volume');
        assert.equal(volume.dockerId, ANON);
        assert.equal(volume.container, 'C1');
        assert.equal(volume.exclusive, true);
        assert.ok(docker.calls.indexOf(`inspect --type container --format {{json .}} C1`) < docker.calls.indexOf('rm -f C1'),
            'the volume association is read before the container is removed');
        assert.ok(docker.calls.indexOf('rm -f C1') < docker.calls.indexOf(`volume rm ${ANON}`));
    });

    test('a named volume and a bind mount are preserved, never removed', async () => {
        seed({ intents: [{ id: 'i1', kind: 'container', name: 'c', dockerId: 'C1', state: 'created' }] });
        const docker = fakeDocker({
            C1: { kind: 'container', name: 'c', present: true, labels: labels(), mounts: [
                { Type: 'volume', Name: 'developer-postgres-data', Destination: '/data' },
                { Type: 'bind', Source: '/Users/someone/code', Destination: '/src' },
            ] },
            'developer-postgres-data': { kind: 'volume', name: 'developer-postgres-data', present: true, labels: {} },
        });
        const outcome = await run(docker);
        assert.equal(outcome.complete, false, 'a volume that cannot be proven exclusive blocks completion');
        assert.ok(!docker.calls.some((call) => call.startsWith('volume rm')), docker.calls.join('\n'));
        assert.match(outcome.report.join('\n'), /preserved named volume developer-postgres-data/);
        assert.equal(outcome.record.intents.filter((intent) => intent.kind === 'volume').length, 1, 'the bind mount is not inventoried as a volume');
    });

    test('a container that vanished before its volume was inventoried leaves the intent unresolved', async () => {
        seed({ intents: [
            { id: 'i1', kind: 'container', name: 'c', dockerId: 'C1', state: 'created' },
            { id: 'i2', kind: 'volume', name: 'unknown', state: 'pending', container: 'C1' },
        ] });
        const outcome = await run(fakeDocker({ C1: { kind: 'container', name: 'c', present: false, labels: labels() } }));
        assert.equal(outcome.complete, false, 'an unaccounted volume intent is not success');
        assert.equal(stateOf(outcome.record, 'unknown'), 'pending');
        assert.match(outcome.report.join('\n'), /INCOMPLETE/);
    });
});

describe('processes are resolved by birth identity and never signalled by PID', () => {
    const recorded = (patch) => ({ pid: 999_999, birth: 'x-starttime:1', birthSource: 'test', role: 'vite-preview', state: 'live', ...patch });

    test('a PID that now belongs to another process is treated as exited and left alone', async () => {
        const live = processIdentity(process.pid);
        seed({ processes: { preview: recorded({ pid: process.pid, birth: `${live.birth}-not-this-one` }) } });
        const outcome = await run(fakeDocker({}));
        assert.equal(outcome.complete, true, outcome.report.join('\n'));
        assert.match(outcome.report.join('\n'), /is verifiably gone \(the PID now carries another birth identity/);
        assert.equal(process.kill(process.pid, 0), true, 'this process was never signalled');
    });

    test('a live process with the recorded identity and no control channel is preserved', async () => {
        const writer = await liveWriter();
        seed({ processes: { preview: recorded({ pid: writer.pid, birth: writer.identity.birth, control: undefined }) } });
        const outcome = await run(fakeDocker({}));
        assert.equal(outcome.complete, false);
        assert.match(outcome.report.join('\n'), /is live but no control channel was recorded; it is preserved/);
        assert.equal(outcome.record.processes.preview.state, 'preserved');
    });

    test('an unavailable control channel preserves the process, reports it and sends no signal', async () => {
        const writer = await liveWriter();
        seed({ processes: { preview: recorded({ pid: writer.pid, birth: writer.identity.birth, control: join(scratch, 'missing.sock') }) } });
        const outcome = await run(fakeDocker({}), { controlTimeoutMs: 500, exitTimeoutMs: 500 });
        assert.equal(outcome.complete, false);
        assert.match(outcome.report.join('\n'), /the private preview control channel could not stop/);
        assert.match(outcome.report.join('\n'), /no PID-based fallback is authorized/);
        assert.equal(writer.child.exitCode, null, 'the preserved process was never signalled');
        assert.equal(writer.child.signalCode, null);
    });

    test('the runner reconciling its own teardown does not block cleanup but keeps the run nonterminal', async () => {
        const self = processIdentity(process.pid);
        seed({ processes: { runner: { pid: process.pid, birth: self.birth, birthSource: self.source, role: 'playwright-runner', state: 'live' } } });
        const outcome = await run(fakeDocker({}));
        assert.equal(outcome.complete, true, outcome.report.join('\n'));
        assert.equal(outcome.record.state, 'cleaning', 'completion waits for the coordinator to verify this process exited');
        assert.equal(outcome.record.processes.runner.state, 'reconciling-self');
    });

    test('a process with no recorded birth identity is ambiguous and preserved', async () => {
        seed({ processes: { runner: { pid: process.pid, birth: null, birthSource: 'unreadable', role: 'playwright-runner', state: 'live' } } });
        const outcome = await run(fakeDocker({}));
        assert.equal(outcome.complete, false);
        assert.match(outcome.report.join('\n'), /no OS birth identity was recorded/);
    });

    test('a record from another host or boot is preserved without any local verdict', async () => {
        seed({
            host: { hostname: 'another-machine', boot: 'boot:elsewhere', pidNamespace: null, system: 'Linux' },
            processes: { preview: recorded({ pid: process.pid }) },
        });
        const outcome = await run(fakeDocker({}));
        assert.equal(outcome.complete, false);
        assert.match(outcome.report.join('\n'), /recorded on another host, boot or PID namespace/);
    });
});

describe('the temporary build is removed only after its identity is verified', () => {
    const build = () => {
        const dir = mkdtempSync(join(scratch, 'build-'));
        writeFileSync(markerFile(dir), `${RUN}\n`, { mode: 0o600 });
        const self = lstatSync(dir), parent = lstatSync(dirname(dir));
        return { path: dir, dev: self.dev, ino: self.ino, parent: { dev: parent.dev, ino: parent.ino }, marker: RUN, state: 'created' };
    };

    test('a verified directory is removed and its absence confirmed', async () => {
        const temp = build();
        seed({ temp });
        const outcome = await run(fakeDocker({}));
        assert.equal(outcome.complete, true, outcome.report.join('\n'));
        assert.equal(existsSync(temp.path), false);
        assert.equal(outcome.record.temp.state, 'removed');
    });

    test('a replaced directory, a foreign marker and a replaced parent are all preserved', async () => {
        for (const [name, mutate] of [
            ['another run\'s marker', (temp) => writeFileSync(markerFile(temp.path), 'cm-e2e-other\n')],
            ['a missing marker', (temp) => rmSync(markerFile(temp.path))],
            ['a replaced identity', (temp) => { temp.ino += 1; }],
            ['a replaced parent', (temp) => { temp.parent.ino += 1; }],
        ]) {
            const temp = build();
            mutate(temp);
            seed({ temp });
            const outcome = await run(fakeDocker({}));
            assert.equal(outcome.complete, false, `${name} must block completion`);
            assert.equal(existsSync(temp.path), true, `${name}: the directory is preserved`);
            assert.equal(outcome.record.temp.state, 'preserved');
        }
    });

    test('the build is preserved while any writer has not been verified as exited', async () => {
        const temp = build();
        const writer = await liveWriter();
        seed({ temp, processes: { builder: { pid: writer.pid, birth: writer.identity.birth, birthSource: writer.identity.source, role: 'builder', state: 'live' } } });
        const outcome = await run(fakeDocker({}));
        assert.equal(outcome.complete, false);
        assert.equal(existsSync(temp.path), true);
        assert.match(outcome.report.join('\n'), /preserved: a writer has not been verified as exited/);
    });

    test('an already absent build directory is simply absent', async () => {
        const temp = build();
        rmSync(temp.path, { recursive: true, force: true });
        seed({ temp });
        const outcome = await run(fakeDocker({}));
        assert.equal(outcome.complete, true, outcome.report.join('\n'));
        assert.equal(outcome.record.temp.state, 'absent');
    });
});

describe('resources this inventory does not account for are reported, never removed', () => {
    test('a pre-r2 leftover without a run label is preserved and blocks completion', async () => {
        seed({ intents: [{ id: 'i1', kind: 'container', name: 'c', dockerId: 'C1', state: 'created' }] });
        const docker = fakeDocker({
            C1: { kind: 'container', name: 'c', present: true, labels: labels() },
            LEGACY: { kind: 'container', name: 'old', present: true, labels: { 'codemastery.e2e': 'disposable', 'codemastery.e2e.pid': '4242' } },
        });
        const outcome = await run(docker);
        assert.equal(outcome.complete, false);
        assert.ok(!docker.calls.includes('rm -f LEGACY'), 'a legacy resource is never removed automatically');
        assert.match(outcome.report.join('\n'), /preserved container LEGACY: carries no run label \(a pre-r2 leftover\); it is not removed automatically/);
    });

    test('another checkout\'s live run is left untouched and does not fail this recovery', async () => {
        const registry = mkdtempSync(join(scratch, 'registry-'));
        mkdirSync(join(registry, 'runs'), { recursive: true });
        writeInventory(join(registry, 'runs', 'other-key.json'), {
            schema: 'codemastery-e2e-inventory/1', runId: 'cm-e2e-other', state: 'active',
            ownerUid: process.getuid(), intents: [],
        });
        seed({ intents: [{ id: 'i1', kind: 'container', name: 'c', dockerId: 'C1', state: 'created' }] });
        const docker = fakeDocker({
            C1: { kind: 'container', name: 'c', present: true, labels: labels() },
            OTHER: { kind: 'container', name: 'other', present: true, labels: labels('cm-e2e-other') },
        });
        const outcome = await run(docker, { registryRoot: registry, lockKey: 'test' });
        assert.equal(outcome.complete, true, outcome.report.join('\n'));
        assert.ok(!docker.calls.includes('rm -f OTHER'));
        assert.match(outcome.report.join('\n'), /1 resource\(s\) belong to another checkout's live run and were left untouched/);
    });
});

describe('an untrusted or absent record is reported honestly', () => {
    test('a corrupt record is reported as untrusted and nothing is cleaned', async () => {
        writeFileSync(inventory, '{not json', { mode: 0o600 });
        const outcome = await run(fakeDocker({}));
        assert.equal(outcome.complete, false);
        assert.equal(outcome.untrusted, true);
        assert.match(outcome.report.join('\n'), /is corrupt/);
        assert.match(outcome.report.join('\n'), /remove it by hand only after confirming there are no leftovers/);
    });
});
