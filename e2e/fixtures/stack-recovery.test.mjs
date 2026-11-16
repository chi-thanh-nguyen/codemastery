// Regression tests for the r2 cleanup and recovery engine (ADR008 CT34-FIXTURES-2026-11-10-r2,
// Sections 10.3 and 10.4). The Docker CLI is injected as an in-process fake, so every call is
// recorded and every failure mode — a daemon that drifted, a removal that times out, a resource
// whose ownership cannot be read, a volume that was never inventoried — can be injected exactly.
//
// These are stub scenarios: they prove the protocol's decisions, not Docker integration itself.
// The real-Docker lifecycle is a separate, recorded verification.
import { afterEach, beforeEach, describe, test } from 'node:test';
import { spawn, spawnSync } from 'node:child_process';
import { once } from 'node:events';
import { createServer as netCreateServer } from 'node:net';
import assert from 'node:assert/strict';
import { chmodSync, existsSync, mkdirSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { lstatSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, join } from 'node:path';
import { markerFile, readInventory, writeInventory } from './run-inventory.mjs';
import { STACK_LABEL, dockerIdentity, makeDocker, reconcile, reportsNotFound, runLabel } from './stack-recovery.mjs';
import { hostIdentity, processGroup, processIdentity } from './host-identity.mjs';

const HOST = hostIdentity();
const RUN = 'cm-e2e-stub';
const DAEMON = { context: 'stub-context', endpoint: 'unix:///var/run/docker.sock', daemonId: 'STUB-DAEMON-1' };
const ANON = 'a'.repeat(64);
/** The labels Docker itself gives a volume it created anonymously for a container. */
const ANON_LABELS = { 'com.docker.volume.anonymous': '' };
/** Docker CLI 29.8.1's actual answer when the daemon socket has gone away (captured on macOS). */
const DAEMON_GONE = 'failed to connect to the docker API at unix:///var/run/docker.sock; check if the path is correct and if the daemon is running: dial unix /var/run/docker.sock: connect: no such file or directory';

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
        if (args[0] === 'info') {
            if (options.infoFails || options.down?.()) return error(options.down?.() ? DAEMON_GONE : 'Cannot connect to the Docker daemon');
            return reply(`${options.daemonIds ? options.daemonIds.shift() ?? DAEMON.daemonId : options.daemonId ?? DAEMON.daemonId}\n`);
        }
        if (options.down?.()) return error(DAEMON_GONE);
        const id = args.at(-1);
        if (args[0] === 'image' && args[1] === 'inspect') {
            const image = options.images?.[id];
            return image ? reply(`${JSON.stringify(image.volumes ?? null)}\n`) : error(`Error response from daemon: No such image: ${id}`);
        }
        const kindOf = (name) => (name === 'network' ? 'network' : name === 'volume' ? 'volume' : 'container');
        if (args[0] === 'inspect' || (args[1] === 'inspect' && ['network', 'volume'].includes(args[0]))) {
            const target = state[id];
            // Docker 29.8.1's own wording for each kind of missing resource.
            const missing = () => error(args[0] === 'network' ? `Error response from daemon: network ${id} not found`
                : args[0] === 'volume' ? `Error response from daemon: get ${id}: no such volume` : `Error response from daemon: No such container: ${id}`);
            if (!target) return missing();
            if (target.inspectFails) return error('Error response from daemon: i/o timeout', true);
            if (target.inspectFailsTimes > 0) { target.inspectFailsTimes -= 1; return error('Error response from daemon: i/o timeout', true); }
            if (!target.present) return missing();
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
            const runFilter = named.find((filter) => filter.startsWith('label=codemastery.e2e.run='))?.split('=').at(-1);
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
/**
 * The runner exactly as the coordinator records it once it has exited: its own process group
 * (a new session), the PID long gone. Every record the coordinator writes has a runner entry.
 */
const exitedRunnerPid = spawnSync(process.execPath, ['-e', '']).pid;
const exitedRunner = () => ({ pid: exitedRunnerPid, birth: 'x-starttime:runner', birthSource: 'test', role: 'playwright-runner', state: 'live', spawned: true, group: exitedRunnerPid });
/** Writes a record describing a run that owns a network, two containers and an anonymous volume. */
function seed(patch = {}) {
    const { processes, ...rest } = patch;
    const record = {
        schema: 'codemastery-e2e-inventory/1', contract: 'CT34-FIXTURES-2026-11-10-r2', runId: RUN,
        state: 'active', ownerUid: process.getuid(), lockKey: 'test', fence: 1, revision: 1,
        host: { hostname: HOST.hostname, boot: HOST.boot, pidNamespace: HOST.pidNamespace, system: HOST.system },
        checkout: { path: scratch, dev: 1, ino: 1 }, outputs: [], docker: { ...DAEMON },
        processes: { runner: exitedRunner(), ...(processes ?? {}) },
        temp: null, intents: [], observations: [], ...rest,
    };
    writeInventory(inventory, record);
    return record;
}
/** A verified, empty runner process group unless a test injects another answer. */
const emptyGroup = () => ({ reliable: true, members: [] });
const run = (docker, options = {}) => reconcile(inventory, { docker, host: HOST, log: false, reason: 'test', registryRoot: null, processGroup: emptyGroup, ...options });
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
                { id: 'i2', kind: 'container', name: `${RUN}-postgres`, dockerId: 'C1', state: 'created', volumes: 'recorded' },
                { id: 'i3', kind: 'volume', name: ANON, dockerId: ANON, container: 'C1', exclusive: true, state: 'created' },
            ],
        });
        const docker = fakeDocker({
            N1: { kind: 'network', name: RUN, present: true, labels: labels() },
            C1: { kind: 'container', name: `${RUN}-postgres`, present: true, labels: labels(), mounts: [{ Type: 'volume', Name: ANON, Destination: '/var/lib/postgresql/data' }] },
            [ANON]: { kind: 'volume', name: ANON, present: true, labels: ANON_LABELS },
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
        // A container whose mounts were already recorded at setup, exactly as setup leaves it.
        seed({ intents: [{ id: 'i1', kind: 'container', name: 'c', dockerId: 'C1', state: 'created', volumes: 'recorded' }] });
        const outcome = await run(fakeDocker({ C1: { kind: 'container', name: 'c', present: true, inspectFails: true, labels: labels() } }));
        assert.equal(outcome.complete, false);
        assert.equal(stateOf(outcome.record, 'c'), 'preserved');
        assert.match(outcome.report.join('\n'), /ownership could not be read/);
    });

    test('an already absent resource counts as absent, not as a failure', async () => {
        seed({ intents: [{ id: 'i1', kind: 'container', name: 'c', dockerId: 'C1', state: 'created', volumes: 'recorded' }] });
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
    test('a pending intent with nothing on the daemon resolves to absent when its image declares no volume', async () => {
        seed({ intents: [{ id: 'i1', kind: 'container', name: `${RUN}-backend`, image: 'sha256:backend', state: 'pending', volumes: 'pending' }] });
        const outcome = await run(fakeDocker({}, { images: { 'sha256:backend': { volumes: null } } }));
        assert.equal(outcome.complete, true, outcome.report.join('\n'));
        assert.equal(stateOf(outcome.record, `${RUN}-backend`), 'absent');
        assert.equal(outcome.record.intents[0].volumes, 'none');
    });

    test('a pending intent whose exact image is not on the daemon (a failed `docker run`) has no volume to account for', async () => {
        seed({ intents: [{ id: 'i1', kind: 'container', name: `${RUN}-backend`, image: 'sha256:missing', state: 'pending', volumes: 'pending' }] });
        const outcome = await run(fakeDocker({}, { images: {} }));
        assert.equal(outcome.complete, true, outcome.report.join('\n'));
        assert.equal(outcome.record.intents[0].volumes, 'none');
    });

    test('a pending PostgreSQL intent with nothing on the daemon leaves its possible volume unaccounted', async () => {
        // A crash after `docker run` created the container (and the image's anonymous volume) and
        // before its ID was recorded, followed by the container's removal, looks exactly like this.
        seed({ intents: [{ id: 'i1', kind: 'container', name: `${RUN}-postgres`, image: 'sha256:pg', state: 'pending', volumes: 'pending' }] });
        const docker = fakeDocker({}, { images: { 'sha256:pg': { volumes: { '/var/lib/postgresql/data': {} } } } });
        const outcome = await run(docker);
        assert.equal(outcome.complete, false, 'an unrecorded volume association is not success');
        assert.equal(outcome.record.intents[0].volumes, 'unaccounted');
        assert.match(outcome.report.join('\n'), /unaccounted volume/);
        assert.ok(!docker.calls.some((call) => /^volume (ls|rm|prune)/.test(call)), 'nothing is searched or pruned');
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
        // What setup leaves when it dies after `docker run` returned the ID but before the mounts were read.
        seed({ intents: [{ id: 'i1', kind: 'container', name: `${RUN}-postgres`, image: 'sha256:pg', role: 'postgres', dockerId: 'C1', state: 'created', volumes: 'pending' }] });
        const docker = fakeDocker({
            C1: { kind: 'container', name: `${RUN}-postgres`, present: true, labels: labels(), mounts: [{ Type: 'volume', Name: ANON, Destination: '/var/lib/postgresql/data' }] },
            [ANON]: { kind: 'volume', name: ANON, present: true, labels: ANON_LABELS },
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

    test('a container that vanished before its volume was inventoried leaves the volume unaccounted', async () => {
        // Exactly what setup writes: the container intent with its observed ID and a still-pending
        // volume association, and no volume intent yet. The container is gone and its anonymous
        // volume is still on the daemon.
        seed({ intents: [
            { id: 'i1', kind: 'network', name: RUN, dockerId: 'N1', state: 'created' },
            { id: 'i2', kind: 'container', name: `${RUN}-postgres`, image: 'sha256:pg', dockerId: 'C1', state: 'created', volumes: 'pending' },
        ] });
        const docker = fakeDocker({
            N1: { kind: 'network', name: RUN, present: true, labels: labels() },
            C1: { kind: 'container', name: `${RUN}-postgres`, present: false, labels: labels() },
            [ANON]: { kind: 'volume', name: ANON, present: true, labels: ANON_LABELS },
        });
        const outcome = await run(docker);
        assert.equal(outcome.complete, false, 'an unaccounted volume is not success');
        assert.equal(outcome.record.intents[1].volumes, 'unaccounted');
        assert.match(outcome.report.join('\n'), /unaccounted volume: container C1 .* vanished before its volumes were recorded/);
        assert.ok(!docker.calls.some((call) => call.startsWith('volume rm') || call.startsWith('volume ls')), 'no volume is searched for or removed');
        assert.equal(readInventory(inventory).state, 'incomplete');
        const again = await run(docker);
        assert.equal(again.complete, false, 'a retry cannot make an unaccounted volume disappear');
    });

    test('a mount inspection that fails keeps the association pending instead of reading it as no volume', async () => {
        seed({ intents: [{ id: 'i1', kind: 'container', name: `${RUN}-postgres`, dockerId: 'C1', state: 'created', volumes: 'pending' }] });
        const outcome = await run(fakeDocker({ C1: { kind: 'container', name: `${RUN}-postgres`, present: true, inspectFails: true, labels: labels() } }));
        assert.equal(outcome.complete, false);
        assert.equal(outcome.record.intents[0].volumes, 'pending');
        assert.match(outcome.report.join('\n'), /volume association of container C1 is unresolved/);
    });

    test('a container whose mount inspection fails once is kept, and the retry records its volume before removing both', async () => {
        // Exactly what setup leaves when `docker run` succeeded and reading the mounts failed.
        seed({ intents: [
            { id: 'i1', kind: 'network', name: RUN, dockerId: 'N1', state: 'created' },
            { id: 'i2', kind: 'container', name: `${RUN}-postgres`, image: 'sha256:pg', role: 'postgres', dockerId: 'C1', state: 'created', volumes: 'pending' },
        ] });
        const state = {
            N1: { kind: 'network', name: RUN, present: true, labels: labels() },
            C1: { kind: 'container', name: `${RUN}-postgres`, present: true, inspectFailsTimes: 1, labels: labels(), mounts: [{ Type: 'volume', Name: ANON, Destination: '/var/lib/postgresql/data' }] },
            [ANON]: { kind: 'volume', name: ANON, present: true, labels: ANON_LABELS },
        };
        const docker = fakeDocker(state);
        const first = await run(docker);
        assert.equal(first.complete, false, 'a transient error is never a completion');
        assert.ok(!docker.calls.includes('rm -f C1'), `the container is not removed while its association is unresolved:\n${docker.calls.join('\n')}`);
        assert.equal(state.C1.present, true);
        assert.equal(first.record.intents[1].volumes, 'pending', 'the association stays pending, not unaccounted');
        assert.equal(first.record.intents[1].state, 'preserved');
        assert.equal(first.record.intents.filter((intent) => intent.kind === 'volume').length, 0);
        assert.equal(readInventory(inventory).state, 'incomplete');
        assert.match(first.report.join('\n'), /preserved container C1: its volume association is unresolved/);

        const firstPass = docker.calls.length;
        const second = await run(docker);
        assert.equal(second.complete, true, second.report.join('\n'));
        const volume = second.record.intents.find((intent) => intent.kind === 'volume');
        assert.equal(volume?.dockerId, ANON, 'the retry recorded the anonymous volume from the container\'s own mounts');
        assert.equal(volume.container, 'C1');
        assert.equal(volume.state, 'removed');
        assert.equal(state[ANON].present, false, 'no anonymous volume is orphaned');
        assert.ok(docker.calls.indexOf(`inspect --type container --format {{json .}} C1`, firstPass) < docker.calls.indexOf('rm -f C1'),
            'the retry reads the mounts before it removes the container');
        assert.ok(docker.calls.indexOf('rm -f C1') < docker.calls.indexOf(`volume rm ${ANON}`));
        assert.ok(!docker.calls.some((call) => /^volume (ls|prune)/.test(call)), 'nothing is searched or pruned');
        assert.equal(readInventory(inventory).state, 'complete');
    });

    test('a volume Docker does not mark anonymous is preserved even when recorded as exclusive', async () => {
        seed({ intents: [
            { id: 'i1', kind: 'container', name: 'c', dockerId: 'C1', state: 'removed', volumes: 'recorded' },
            { id: 'v2', kind: 'volume', name: ANON, dockerId: ANON, container: 'C1', exclusive: true, state: 'created' },
        ] });
        const docker = fakeDocker({ [ANON]: { kind: 'volume', name: ANON, present: true, labels: {} } });
        const outcome = await run(docker);
        assert.equal(outcome.complete, false);
        assert.ok(!docker.calls.includes(`volume rm ${ANON}`), docker.calls.join('\n'));
        assert.match(outcome.report.join('\n'), /not proven to be an anonymous volume/);
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
        seed({ processes: { preview: recorded({ pid: writer.pid, birth: writer.identity.birth, control: join(scratch, `${RUN}.sock`) }) } });
        const outcome = await run(fakeDocker({}), { controlTimeoutMs: 500, exitTimeoutMs: 500 });
        assert.equal(outcome.complete, false);
        assert.match(outcome.report.join('\n'), /the private preview control channel could not stop/);
        assert.match(outcome.report.join('\n'), /no PID-based fallback is authorized/);
        assert.equal(writer.child.exitCode, null, 'the preserved process was never signalled');
        assert.equal(writer.child.signalCode, null);
    });

    test('the runner reconciling its own teardown does not block cleanup but keeps the run nonterminal', async () => {
        const self = processIdentity(process.pid);
        seed({ processes: { runner: { pid: process.pid, birth: self.birth, birthSource: self.source, role: 'playwright-runner', state: 'live', spawned: true, group: process.pid } } });
        const outcome = await run(fakeDocker({}), { processGroup: () => ({ reliable: true, members: [{ pid: process.pid, birth: self.birth }] }) });
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

describe('the runner\'s process group must be empty before the build or the stack is touched', () => {
    const deadPid = () => spawnSync(process.execPath, ['-e', '']).pid;
    /** A runner that exited, as the coordinator records it: it led its own process group. */
    const exitedRunner = (patch = {}) => { const pid = deadPid(); return { pid, birth: 'x-starttime:1', birthSource: 'test', role: 'playwright-runner', state: 'live', group: pid, ...patch }; };
    const stack = () => ({
        intents: [
            { id: 'i1', kind: 'network', name: RUN, dockerId: 'N1', state: 'created' },
            { id: 'i2', kind: 'container', name: `${RUN}-postgres`, dockerId: 'C1', state: 'created', volumes: 'recorded' },
        ],
    });
    const stackState = () => ({
        N1: { kind: 'network', name: RUN, present: true, labels: labels() },
        C1: { kind: 'container', name: `${RUN}-postgres`, present: true, labels: labels() },
    });

    test('a live member of the exited runner\'s group (a Playwright worker finishing) keeps the whole stack', async () => {
        const runner = exitedRunner();
        seed({ ...stack(), processes: { runner } });
        const docker = fakeDocker(stackState());
        const asked = [];
        const outcome = await run(docker, { processGroup: (pgid) => { asked.push(pgid); return { reliable: true, members: [{ pid: 4242, birth: 'x-starttime:2' }] }; } });
        assert.deepEqual(asked, [runner.pid], 'the recorded group is inspected, nothing is signalled');
        assert.equal(outcome.complete, false);
        assert.match(outcome.report.join('\n'), new RegExp(`process group ${runner.pid} still has 1 live process`));
        assert.ok(!docker.calls.some((call) => /^(rm |network rm|volume rm)/.test(call)), docker.calls.join('\n'));
        assert.equal(outcome.record.processes.runner.groupState, 'live');

        const later = await run(docker, { processGroup: () => ({ reliable: true, members: [] }) });
        assert.equal(later.complete, true, later.report.join('\n'));
        assert.equal(later.record.processes.runner.groupState, 'empty');
    });

    test('a group that cannot be inspected is never taken as empty', async () => {
        seed({ ...stack(), processes: { runner: exitedRunner() } });
        const docker = fakeDocker(stackState());
        const outcome = await run(docker, { processGroup: () => ({ reliable: false, members: [], detail: 'no qualified enumeration' }) });
        assert.equal(outcome.complete, false);
        assert.match(outcome.report.join('\n'), /could not be fully inspected \(no qualified enumeration\)/);
        assert.ok(!docker.calls.some((call) => /^(rm |network rm|volume rm)/.test(call)));
    });

    test('a reused runner PID is no shortcut: the group is still enumerated, and any member keeps the stack', async () => {
        const live = processIdentity(process.pid);
        seed({ ...stack(), processes: { runner: { pid: process.pid, birth: `${live.birth}-earlier`, role: 'playwright-runner', state: 'live', spawned: true, group: process.pid } } });
        const asked = [];
        const busy = await run(fakeDocker(stackState()), { processGroup: (pgid) => { asked.push(pgid); return { reliable: true, members: [{ pid: 5150, birth: 'x-starttime:9' }] }; } });
        assert.deepEqual(asked, [process.pid], 'a PID now carrying another birth identity does not prove the old group empty');
        assert.equal(busy.complete, false);
        const idle = await run(fakeDocker(stackState()), { processGroup: () => ({ reliable: true, members: [] }) });
        assert.equal(idle.complete, true, idle.report.join('\n'));
    });

    test('the real group enumeration sees a live member and its exit', async () => {
        const member = spawn(process.execPath, ['-e', 'setTimeout(() => {}, 30000)'], { detached: true, stdio: 'ignore' });
        sleepers.push(member);
        for (let at = 0; at < 50 && !processIdentity(member.pid).exists; at++) await new Promise((next) => setTimeout(next, 20));
        const seen = processGroup(member.pid);
        assert.equal(seen.reliable, true, seen.detail);
        assert.deepEqual(seen.members.map((entry) => entry.pid), [member.pid]);
        assert.equal(seen.members[0].birth, processIdentity(member.pid).birth, 'members carry their OS birth identity');
        member.kill('SIGKILL');
        await once(member, 'exit');
        assert.deepEqual(processGroup(member.pid).members, [], 'an exited (or reaped) member is gone');
    });
});

describe('only a process acting for the coordinator that holds the lock changes anything', () => {
    test('a reconciliation whose coordinator is gone inspects, removes and writes nothing', async () => {
        seed({ lockKey: 'k1', intents: [{ id: 'i1', kind: 'container', name: 'c', dockerId: 'C1', state: 'created', volumes: 'recorded' }] });
        const before = readFileSync(inventory, 'utf8');
        const docker = fakeDocker({ C1: { kind: 'container', name: 'c', present: true, labels: labels() } });
        const env = { CODEMASTERY_E2E_RUN_ID: RUN, CODEMASTERY_E2E_LOCK_KEY: 'k1', CODEMASTERY_E2E_SUPERVISOR: '4242' };
        const outcome = await run(docker, { env, ppid: 1 });
        assert.equal(outcome.complete, false);
        assert.equal(outcome.ownershipLost, true);
        assert.match(outcome.report.join('\n'), /coordinator 4242 that holds this checkout's lock is no longer this process' parent/);
        assert.deepEqual(docker.calls, [], 'no Docker call at all');
        assert.equal(readFileSync(inventory, 'utf8'), before, 'the record is byte-identical');
    });

    test('a reconciliation handed another run\'s record writes nothing', async () => {
        seed({ lockKey: 'k1', runId: 'cm-e2e-later-run' });
        const before = readFileSync(inventory, 'utf8');
        const env = { CODEMASTERY_E2E_RUN_ID: RUN, CODEMASTERY_E2E_LOCK_KEY: 'k1', CODEMASTERY_E2E_SUPERVISOR: String(process.ppid) };
        const outcome = await run(fakeDocker({}), { env, ppid: process.ppid });
        assert.equal(outcome.ownershipLost, true);
        assert.match(outcome.report.join('\n'), /belongs to run cm-e2e-later-run, not to the handed-down run cm-e2e-stub/);
        assert.equal(readFileSync(inventory, 'utf8'), before);
    });
});

describe('the temporary build is removed only after its identity is verified', () => {
    const build = () => {
        const dir = mkdtempSync(join(scratch, 'codemastery-e2e-'));
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
    test('a pre-r2 leftover or another account\'s resource is reported and untouched, and does not block this run', async () => {
        seed({ intents: [{ id: 'i1', kind: 'container', name: 'c', dockerId: 'C1', state: 'created' }] });
        const docker = fakeDocker({
            C1: { kind: 'container', name: 'c', present: true, labels: labels() },
            LEGACY: { kind: 'container', name: 'old', present: true, labels: { 'codemastery.e2e': 'disposable', 'codemastery.e2e.pid': '4242' } },
            ORPHAN: { kind: 'network', name: 'gone', present: true, labels: { ...labels('cm-e2e-nobody'), 'codemastery.e2e.owner': '9999' } },
        });
        const outcome = await run(docker);
        assert.equal(outcome.complete, true, outcome.report.join('\n'));
        assert.ok(!docker.calls.includes('rm -f LEGACY') && !docker.calls.includes('network rm ORPHAN'), 'resources of no current inventory are never removed');
        assert.match(outcome.report.join('\n'), /left container LEGACY untouched: it carries no run label \(a pre-r2 leftover\); it is not this run's, is never removed automatically and needs operator reconciliation/);
        assert.match(outcome.report.join('\n'), /left network ORPHAN untouched: labelled for run cm-e2e-nobody, which no current inventory owns/);
    });

    test('a resource carrying this run\'s label that the inventory does not name blocks completion', async () => {
        seed({ intents: [] , docker: { ...DAEMON } });
        const docker = fakeDocker({ STRAY: { kind: 'container', name: 'stray', present: true, labels: labels() } });
        const outcome = await run(docker);
        assert.equal(outcome.complete, false);
        assert.ok(!docker.calls.includes('rm -f STRAY'));
        assert.match(outcome.report.join('\n'), /preserved container STRAY: it carries this run's label but is not in its inventory/);
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

describe('Docker absence is only ever concluded on the recorded daemon', () => {
    test('only Docker\'s own not-found answer for the exact ID is absence; a lost daemon socket is not', () => {
        assert.equal(reportsNotFound('container', 'C1', 'Error response from daemon: No such container: C1'), true);
        assert.equal(reportsNotFound('network', 'N1', 'Error response from daemon: network N1 not found'), true);
        assert.equal(reportsNotFound('volume', ANON, `Error response from daemon: get ${ANON}: no such volume`), true);
        assert.equal(reportsNotFound('container', 'C1', 'Error: No such object: C1'), true, 'older CLI wording');
        assert.equal(reportsNotFound('container', 'C1', DAEMON_GONE), false, 'a transport error is never absence');
        assert.equal(reportsNotFound('container', 'C1', 'Error response from daemon: No such container: C12'), false, 'another ID is not this one');
        assert.equal(reportsNotFound('network', 'N1', 'context "x": context not found'), false);
        assert.equal(reportsNotFound('volume', 'V', 'docker timed out'), false);
    });

    test('a daemon that goes away mid-reconciliation and comes back cannot turn present resources into a completion', async () => {
        // The original two-pass failure: pass 1 starts on the recorded daemon, which then becomes
        // unreachable; pass 2 runs once it is back while the container and network still exist.
        seed({ intents: [
            { id: 'i1', kind: 'network', name: RUN, dockerId: 'N1', state: 'created' },
            { id: 'i2', kind: 'container', name: `${RUN}-postgres`, dockerId: 'C1', state: 'created', volumes: 'recorded' },
        ] });
        const state = {
            N1: { kind: 'network', name: RUN, present: true, labels: labels() },
            C1: { kind: 'container', name: `${RUN}-postgres`, present: true, labels: labels() },
        };
        // Down after the identity checks at the start of the pass (context show/inspect, info x2).
        let first = null;
        first = fakeDocker(state, { down: () => first.calls.length > 4 });
        const pass1 = await run(first);
        assert.equal(pass1.complete, false);
        assert.deepEqual(pass1.record.intents.map((intent) => intent.state), ['preserved', 'preserved'], 'nothing is recorded absent');
        const second = fakeDocker(state);
        const pass2 = await run(second);
        assert.equal(pass2.complete, true, pass2.report.join('\n'));
        assert.ok(second.calls.includes('rm -f C1') && second.calls.includes('network rm N1'), 'the resources that still existed were removed');
        assert.equal(state.C1.present, false);
        assert.equal(state.N1.present, false);
    });

    test('a removed/absent state recorded by an earlier pass is re-verified, never trusted', async () => {
        seed({ intents: [
            { id: 'i1', kind: 'network', name: RUN, dockerId: 'N1', state: 'absent' },
            { id: 'i2', kind: 'container', name: `${RUN}-postgres`, dockerId: 'C1', state: 'removed', volumes: 'recorded' },
        ] });
        const docker = fakeDocker({
            N1: { kind: 'network', name: RUN, present: true, labels: labels() },
            C1: { kind: 'container', name: `${RUN}-postgres`, present: true, labels: labels() },
        });
        const outcome = await run(docker);
        assert.equal(outcome.complete, true, outcome.report.join('\n'));
        assert.ok(docker.calls.includes('rm -f C1') && docker.calls.includes('network rm N1'));
    });

    test('a not-found answer is not absence unless the endpoint still reaches the recorded daemon', async () => {
        seed({ intents: [{ id: 'i1', kind: 'container', name: 'c', dockerId: 'C1', state: 'created', volumes: 'recorded' }] });
        // The identity checks pass (two `info` calls), then the endpoint reaches another daemon, which
        // of course knows nothing about C1.
        const docker = fakeDocker({}, { daemonIds: [DAEMON.daemonId, DAEMON.daemonId, 'ANOTHER-DAEMON', 'ANOTHER-DAEMON', 'ANOTHER-DAEMON'] });
        const outcome = await run(docker);
        assert.equal(outcome.complete, false);
        assert.equal(stateOf(outcome.record, 'c'), 'preserved');
        assert.match(outcome.report.join('\n'), /absence not confirmed: the endpoint now reaches daemon ANOTHER-DAEMON/);
    });

    test('every Docker call is bound to the recorded endpoint, whatever the current context is', async () => {
        // A real makeDocker against a stub `docker` that reports where each call was sent.
        const bin = mkdtempSync(join(scratch, 'bin-'));
        const log = join(bin, 'calls.log');
        writeFileSync(join(bin, 'docker'), `#!/bin/sh
echo "\${DOCKER_HOST:-<context>} \${DOCKER_CONTEXT:-} $*" >> "${log}"
case "$1 $2" in "context show") echo other-context; exit 0;; "context inspect") echo unix:///var/run/docker.sock; exit 0;; esac
[ "$1" = info ] && { echo ${DAEMON.daemonId}; exit 0; }
case "$1 $2" in ps*|"network ls") exit 0;; esac
echo "Error response from daemon: No such container: C1" >&2; exit 1
`);
        chmodSync(join(bin, 'docker'), 0o755);
        const saved = { PATH: process.env.PATH, DOCKER_CONTEXT: process.env.DOCKER_CONTEXT };
        process.env.PATH = `${bin}:${process.env.PATH}`;
        process.env.DOCKER_CONTEXT = 'other-context';
        try {
            seed({ intents: [{ id: 'i1', kind: 'container', name: 'c', dockerId: 'C1', state: 'created', volumes: 'recorded' }] });
            const outcome = await reconcile(inventory, { docker: makeDocker(), host: HOST, log: false, reason: 'test', registryRoot: null });
            assert.equal(outcome.complete, true, outcome.report.join('\n'));
        } finally {
            process.env.PATH = saved.PATH;
            if (saved.DOCKER_CONTEXT === undefined) delete process.env.DOCKER_CONTEXT; else process.env.DOCKER_CONTEXT = saved.DOCKER_CONTEXT;
        }
        const lines = readFileSync(log, 'utf8').trim().split('\n');
        const resourceCalls = lines.filter((line) => !/ context (show|inspect)/.test(line));
        assert.ok(resourceCalls.length > 0);
        for (const line of resourceCalls) assert.ok(line.startsWith(`${DAEMON.endpoint}  `), `bound to the recorded endpoint with no context: ${line}`);
    });

    test('a daemon swap after the work is done still withholds completion', async () => {
        seed({ intents: [{ id: 'i1', kind: 'container', name: 'c', dockerId: 'C1', state: 'created', volumes: 'recorded' }] });
        const state = { C1: { kind: 'container', name: 'c', present: true, labels: labels() } };
        // The identity checks and the confirmation behind the removal agree (three `info` calls);
        // the final re-identification then reaches another daemon.
        const ids = [DAEMON.daemonId, DAEMON.daemonId, DAEMON.daemonId, 'ANOTHER-DAEMON'];
        const outcome = await run(fakeDocker(state, { daemonIds: ids }));
        assert.equal(outcome.complete, false, outcome.report.join('\n'));
        assert.match(outcome.report.join('\n'), /Docker verdicts are not trusted: the endpoint now reaches daemon ANOTHER-DAEMON/);
    });
});

describe('writers are stopped first: a live writer keeps the whole stack', () => {
    test('a live runner from a lost coordinator keeps its containers, volume, network and build', async () => {
        const writer = await liveWriter();
        const temp = mkdtempSync(join(scratch, 'codemastery-e2e-'));
        writeFileSync(markerFile(temp), `${RUN}\n`, { mode: 0o600 });
        const self = lstatSync(temp), parent = lstatSync(dirname(temp));
        seed({
            processes: { runner: { pid: writer.pid, birth: writer.identity.birth, birthSource: writer.identity.source, role: 'playwright-runner', state: 'live' } },
            temp: { path: temp, dev: self.dev, ino: self.ino, parent: { dev: parent.dev, ino: parent.ino }, marker: RUN, state: 'created' },
            intents: [
                { id: 'i1', kind: 'network', name: RUN, dockerId: 'N1', state: 'created' },
                { id: 'i2', kind: 'container', name: `${RUN}-postgres`, dockerId: 'C1', state: 'created', volumes: 'recorded' },
                { id: 'v3', kind: 'volume', name: ANON, dockerId: ANON, container: 'C1', exclusive: true, state: 'created' },
            ],
        });
        const docker = fakeDocker({
            N1: { kind: 'network', name: RUN, present: true, labels: labels() },
            C1: { kind: 'container', name: `${RUN}-postgres`, present: true, labels: labels() },
            [ANON]: { kind: 'volume', name: ANON, present: true, labels: ANON_LABELS },
        });
        const outcome = await run(docker);
        assert.equal(outcome.complete, false);
        assert.ok(!docker.calls.some((call) => /^(rm |network rm|volume rm)/.test(call)), docker.calls.join('\n'));
        assert.equal(existsSync(temp), true);
        assert.match(outcome.report.join('\n'), /Docker resources are preserved: a recorded writer has not been verified as exited/);
        assert.equal(writer.child.exitCode, null, 'the live writer was never signalled');
    });

    test('a writer recorded before it was spawned (no PID yet) is unresolved, never assumed absent', async () => {
        seed({ processes: { builder: { pid: null, birth: null, birthSource: null, role: 'builder', state: 'starting' } } });
        const outcome = await run(fakeDocker({}));
        assert.equal(outcome.complete, false);
        assert.match(outcome.report.join('\n'), /preserved builder \(no PID recorded\): no PID recorded/);
    });

    test('a recorded control path that is not this run\'s private channel is never contacted', async () => {
        const writer = await liveWriter();
        const foreign = join(scratch, 'someone-else.sock');
        let contacted = false;
        const server = createSocketServerForTest(foreign, () => { contacted = true; });
        try {
            seed({ processes: { preview: { pid: writer.pid, birth: writer.identity.birth, birthSource: 'test', role: 'vite-preview', state: 'live', control: foreign } } });
            const outcome = await run(fakeDocker({}));
            assert.equal(outcome.complete, false);
            assert.match(outcome.report.join('\n'), /recorded control path is not this run's private channel/);
            assert.equal(contacted, false);
        } finally {
            server.close();
        }
    });
});

describe('a surviving runner keeps the preview it is testing against', () => {
    test('the preview of a live runner is never asked to close, even over its own private channel', async () => {
        const runner = await liveWriter(), preview = await liveWriter();
        const channel = join(scratch, `${RUN}.sock`);
        let contacted = false;
        const server = createSocketServerForTest(channel, () => { contacted = true; });
        try {
            seed({ processes: {
                // Object order puts the preview first, as a record written by setup can.
                preview: { pid: preview.pid, birth: preview.identity.birth, birthSource: 'test', role: 'vite-preview', state: 'live', control: channel },
                runner: { pid: runner.pid, birth: runner.identity.birth, birthSource: 'test', role: 'playwright-runner', state: 'live', group: runner.pid },
            } });
            const outcome = await run(fakeDocker({}), { controlTimeoutMs: 500, exitTimeoutMs: 500 });
            assert.equal(outcome.complete, false);
            assert.equal(contacted, false, 'the live run\'s preview was not contacted');
            assert.match(outcome.report.join('\n'), new RegExp(`preview ${preview.pid} is left running: the runner, a worker of its process group or another writer that may use it has not been verified as exited`));
            assert.equal(outcome.record.processes.preview.state, 'preserved');
            assert.equal(preview.child.exitCode, null);
        } finally {
            server.close();
        }
    });
});

describe('round 3: the runner group decides preview shutdown, and uncertainty fails closed', () => {
    const stackIntents = () => [{ id: 'i1', kind: 'network', name: RUN, dockerId: 'N1', state: 'created' }];
    const stackState = () => ({ N1: { kind: 'network', name: RUN, present: true, labels: labels() } });

    test('no recorded group identity: the group is never guessed empty and nothing is removed', async () => {
        const runner = exitedRunner(); delete runner.group;
        seed({ intents: stackIntents(), processes: { runner } });
        const docker = fakeDocker(stackState());
        const outcome = await run(docker, { processGroup: () => { throw new Error('an unusable identity must not be enumerated'); } });
        assert.equal(outcome.complete, false);
        assert.match(outcome.report.join('\n'), /no verifiable process-group identity was recorded for the runner/);
        assert.ok(!docker.calls.some((call) => /^(rm |network rm|volume rm)/.test(call)), docker.calls.join('\n'));
    });

    test('a group identity that is not the runner\'s own PID is unusable as well', async () => {
        seed({ intents: stackIntents(), processes: { runner: { ...exitedRunner(), group: 1 } } });
        const outcome = await run(fakeDocker(stackState()));
        assert.equal(outcome.complete, false);
        assert.match(outcome.report.join('\n'), /no verifiable process-group identity/);
    });

    test('a record without any runner (legacy or malformed) is preserved', async () => {
        seed({ intents: stackIntents(), processes: { runner: undefined } });
        const record = readInventory(inventory); delete record.processes.runner; writeInventory(inventory, record);
        const outcome = await run(fakeDocker(stackState()));
        assert.equal(outcome.complete, false);
        assert.match(outcome.report.join('\n'), /no runner is recorded \(a legacy or malformed record\)/);
    });

    test('a runner that was never started needs no group', async () => {
        seed({ intents: stackIntents(), processes: { runner: { pid: null, birth: null, role: 'playwright-runner', state: 'exited', spawned: false } } });
        const outcome = await run(fakeDocker(stackState()), { processGroup: () => { throw new Error('no group to inspect'); } });
        assert.equal(outcome.complete, true, outcome.report.join('\n'));
    });

    test('a live worker in the runner\'s group keeps the verified preview running and is told apart from it by birth identity', async () => {
        const preview = await liveWriter();
        const channel = join(scratch, `${RUN}.sock`);
        let contacted = false;
        const server = createSocketServerForTest(channel, () => { contacted = true; });
        try {
            seed({ intents: stackIntents(), processes: { preview: { pid: preview.pid, birth: preview.identity.birth, role: 'vite-preview', state: 'live', control: channel } } });
            const docker = fakeDocker(stackState());
            // The same PID with another birth identity is *not* the preview: it counts as another member.
            const impostor = await run(docker, { processGroup: () => ({ reliable: true, members: [{ pid: preview.pid, birth: `${preview.identity.birth}-other` }] }) });
            assert.equal(impostor.complete, false);
            assert.equal(contacted, false);
            const busy = await run(docker, { processGroup: () => ({ reliable: true, members: [{ pid: preview.pid, birth: preview.identity.birth }, { pid: 4242, birth: 'x-starttime:worker' }] }) });
            assert.equal(busy.complete, false);
            assert.equal(contacted, false, 'the preview was not contacted while a worker of the group is live');
            assert.match(busy.report.join('\n'), /still has 1 live process\(es\) besides the verified preview/);
            assert.ok(!docker.calls.some((call) => /^(rm |network rm|volume rm)/.test(call)));
        } finally {
            server.close();
        }
    });

    test('the group is checked again after the preview closed: a member that is still there keeps the stack', async () => {
        const preview = await liveWriter();
        const channel = join(scratch, `${RUN}.sock`);
        // A control channel that acknowledges and lets the preview stop itself (as the real one does).
        const server = netCreateServer((socket) => {
            socket.once('data', () => socket.end(JSON.stringify({ ok: true, closing: true }), () => preview.child.kill('SIGKILL')));
        }).listen(channel);
        try {
            seed({ intents: stackIntents(), processes: { preview: { pid: preview.pid, birth: preview.identity.birth, role: 'vite-preview', state: 'live', control: channel } } });
            const answers = [
                { reliable: true, members: [{ pid: preview.pid, birth: preview.identity.birth }] },
                { reliable: true, members: [{ pid: 4242, birth: 'x-starttime:late' }] },
            ];
            const docker = fakeDocker(stackState());
            const outcome = await run(docker, { processGroup: () => answers.shift() ?? { reliable: true, members: [] }, controlTimeoutMs: 2000, exitTimeoutMs: 3000 });
            assert.match(outcome.report.join('\n'), /acknowledged the private control channel/);
            assert.equal(outcome.complete, false, outcome.report.join('\n'));
            assert.match(outcome.report.join('\n'), /still has 1 live process\(es\) \(for example Playwright workers\); the build and the stack are preserved/);
            assert.ok(!docker.calls.some((call) => /^(rm |network rm|volume rm)/.test(call)));
        } finally {
            server.close();
        }
    });
});

describe('round 3: a stale reconciliation can never persist its view, even while its parent check passes', () => {
    // The hand-down of a coordinated finalizer whose coordinator (4242) is, as far as the advisory
    // parent check can tell, still alive: only the fenced compare-and-swap stands in the way.
    const handDown = { CODEMASTERY_E2E_RUN_ID: RUN, CODEMASTERY_E2E_LOCK_KEY: 'test', CODEMASTERY_E2E_SUPERVISOR: '4242', CODEMASTERY_E2E_FENCE: '1' };
    const coordinated = { env: handDown, ppid: 4242 };

    test('a takeover in the middle of a reconciliation (a new fence) stops it before its next write', async () => {
        seed({ intents: [{ id: 'i1', kind: 'container', name: 'c', dockerId: 'C1', state: 'created', volumes: 'recorded' }] });
        const state = { C1: { kind: 'container', name: 'c', present: true, labels: labels() } };
        const inner = fakeDocker(state);
        let takenOver = '';
        const docker = (args) => {
            if (args[0] === 'rm' && !takenOver) {
                // Recovery takes the record over while this reconciliation is inside a Docker removal.
                const record = readInventory(inventory);
                writeInventory(inventory, { ...record, fence: 2, revision: record.revision + 1, observations: [...record.observations, 'taken over'] });
                takenOver = readFileSync(inventory, 'utf8');
            }
            return inner(args);
        };
        const outcome = await run(docker, coordinated);
        assert.equal(outcome.ownershipLost, true, outcome.report.join('\n'));
        assert.match(outcome.report.join('\n'), /fenced: the record is at fence 2, not the writer's fence 1/);
        assert.equal(readFileSync(inventory, 'utf8'), takenOver, 'the new owner\'s record is byte-identical');
    });

    test('another writer of the same generation committing first makes this view stale: it is abandoned, not written', async () => {
        seed({ intents: [{ id: 'i1', kind: 'container', name: 'c', dockerId: 'C1', state: 'created', volumes: 'recorded' }] });
        const inner = fakeDocker({ C1: { kind: 'container', name: 'c', present: true, labels: labels() } });
        let theirs = '';
        const docker = (args) => {
            if (args[0] === 'rm' && !theirs) {
                const record = readInventory(inventory);
                writeInventory(inventory, { ...record, revision: record.revision + 1, observations: [...record.observations, 'a concurrent commit'] });
                theirs = readFileSync(inventory, 'utf8');
            }
            return inner(args);
        };
        const outcome = await run(docker, coordinated);
        assert.equal(outcome.ownershipLost, true, outcome.report.join('\n'));
        assert.match(outcome.report.join('\n'), /changed since it was read: .*stale: the record is at revision/);
        assert.equal(readFileSync(inventory, 'utf8'), theirs, 'the stale in-memory view was never written');
    });

    test('a run that replaced the record (another run ID) is never overwritten', async () => {
        const docker = (args) => {
            if (args[0] === 'context' && args[1] === 'show') {
                const record = readInventory(inventory);
                writeInventory(inventory, { ...record, runId: 'cm-e2e-run-b', fence: 1, revision: 1, state: 'active' });
            }
            return fakeDocker({})(args);
        };
        seed({ intents: [{ id: 'i1', kind: 'network', name: RUN, dockerId: 'N1', state: 'created' }] });
        const before = readFileSync(inventory, 'utf8');
        const outcome = await run(docker, coordinated);
        assert.equal(outcome.ownershipLost, true, outcome.report.join('\n'));
        assert.match(readFileSync(inventory, 'utf8'), /cm-e2e-run-b/);
        assert.notEqual(readFileSync(inventory, 'utf8'), before);
        assert.equal(readInventory(inventory).runId, 'cm-e2e-run-b', "run B's record survives");
    });
});

/** A Unix-domain listener that only records whether anybody connected. */
function createSocketServerForTest(path, onConnect) {
    return netCreateServer((socket) => { onConnect(); socket.destroy(); }).listen(path);
}
