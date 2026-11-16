// Regression tests for the pre-entry lifecycle coordinator of ADR008 CT34-FIXTURES-2026-11-10-r2
// (Section 10.2 and the Section 10.4 acceptance cases): exclusive ownership of the canonical
// checkout and of the three output roots is taken, and the run record persisted, *before* the
// runner is started; path aliases contend for the same lock; independently isolated checkouts run
// concurrently; a loser exits nonzero without touching outputs, resources or the active record;
// and an unresolved obligation blocks reuse until recovery verifies completion.
//
// Each test drives the real coordinator against a disposable fake checkout (its own directory, with
// its own copy of the coordinator in `fixtures/`, every other fixture symlinked to this directory,
// and its own `test-results` roots; the coordinator derives its checkout from its own resolved
// location, exactly as in the real checkout), so the real lock keys and
// records are never involved. The runner command is a plain Node process, so no Playwright, Docker,
// Vite or browser is touched. The registry is deliberately the real per-account one: the coordinator
// resolves `~` from the effective UID and must ignore a HOME override, which is asserted below.
import { after, before, describe, test } from 'node:test';
import assert from 'node:assert/strict';
import { spawn, spawnSync } from 'node:child_process';
import { chmodSync, copyFileSync, existsSync, lstatSync, mkdirSync, mkdtempSync, readdirSync, readFileSync, realpathSync, rmSync, symlinkSync, writeFileSync } from 'node:fs';
import { createServer as createHttpServer } from 'node:http';
import { homedir, tmpdir } from 'node:os';
import { dirname, join } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const launcher = join(here, 'lifecycle.py');
const python = process.env.E2E_PYTHON || 'python3';
const registry = join(realpathSync(homedir()), '.codemastery-e2e');
const EXIT = { ok: 0, fail: 1, conflict: 2, blocked: 3, unqualified: 4 };
const made = [];
const extraKeys = [];

/** A disposable fake checkout: its own output roots, and `fixtures` shared with this directory. */
function fakeCheckout(name = 'co') {
    const root = realpathSync(mkdtempSync(join(tmpdir(), `codemastery-${name}-`)));
    mkdirSync(join(root, 'test-results', 'artifacts'), { recursive: true });
    mkdirSync(join(root, 'test-results', 'html'), { recursive: true });
    writeFileSync(join(root, 'test-results', 'results.json'), '{"sentinel":true}');
    writeFileSync(join(root, 'test-results', 'artifacts', 'sentinel.txt'), 'SENTINEL');
    mkdirSync(join(root, 'fixtures'));
    copyFileSync(launcher, join(root, 'fixtures', 'lifecycle.py'));
    for (const name of readdirSync(here)) if (name !== 'lifecycle.py') symlinkSync(join(here, name), join(root, 'fixtures', name));
    const info = lstatSync(root);
    const entry = { root, key: `${info.dev}-${info.ino}` };
    made.push(entry);
    return entry;
}
const recordFile = (key) => join(registry, 'runs', `${key}.json`);
const readRecord = (key) => (existsSync(recordFile(key)) ? JSON.parse(readFileSync(recordFile(key), 'utf8')) : null);
const sentinels = (checkout) => [
    readFileSync(join(checkout.root, 'test-results', 'artifacts', 'sentinel.txt'), 'utf8'),
    readFileSync(join(checkout.root, 'test-results', 'results.json'), 'utf8'),
];

const env = (extra = {}) => ({ ...process.env, ...extra });
const launcherOf = (root) => join(root, 'fixtures', 'lifecycle.py');
/** Runs the checkout's own coordinator to completion. */
const coordinate = (checkout, args, extra = {}) =>
    spawnSync(python, [launcherOf(checkout.root), ...args], { encoding: 'utf8', timeout: 120_000, env: env(extra), cwd: checkout.root });
/** Starts a coordinator whose runner blocks until `gate` exists, so exclusivity can be observed. */
function startGated(checkout, gate) {
    const waiter = 'const fs=require("node:fs");const g=process.argv[1];const t=Date.now();'
        + '(function w(){if(fs.existsSync(g)||Date.now()-t>60000)process.exit(0);setTimeout(w,40);})();';
    return spawn(python, [launcherOf(checkout.root), 'run', '--', process.execPath, '-e', waiter, gate],
        { encoding: 'utf8', env: env(), cwd: checkout.root, stdio: ['ignore', 'pipe', 'pipe'] });
}
/** Waits until the coordinator has persisted an active record, i.e. ownership precedes runner entry. */
async function awaitActive(key, timeoutMs = 30_000) {
    const deadline = Date.now() + timeoutMs;
    for (;;) {
        const record = readRecord(key);
        if (record?.state === 'active') return record;
        if (Date.now() > deadline) throw new Error(`no active record for ${key} within ${timeoutMs} ms`);
        await new Promise((next) => setTimeout(next, 50));
    }
}
const open = (gate) => writeFileSync(gate, '');
const settle = (child) => new Promise((done) => child.once('close', (code) => done(code)));

before(() => {
    const ready = spawnSync(python, [launcher, 'host-identity'], { encoding: 'utf8' });
    assert.equal(ready.status, 0, `the coordinator must run on this host: ${ready.stderr}`);
});
after(() => {
    // Remove only the keys these tests created, and only from the registry's own files.
    for (const entry of made) {
        rmSync(recordFile(entry.key), { force: true });
        rmSync(join(registry, 'locks', `${entry.key}.lock`), { force: true });
        rmSync(join(registry, 'archive', `${entry.key}.last.json`), { force: true });
        rmSync(join(registry, 'runs', `.${entry.key}.json.lock`), { force: true });
        rmSync(entry.root, { recursive: true, force: true });
    }
    for (const key of extraKeys) {
        rmSync(recordFile(key), { force: true });
        rmSync(join(registry, 'locks', `${key}.lock`), { force: true });
        rmSync(join(registry, 'runs', `.${key}.json.lock`), { force: true });
    }
});

describe('ownership is taken and persisted before the runner is entered', () => {
    test('the admitted runner already sees an active record naming its own run and output roots', () => {
        const checkout = fakeCheckout('admit');
        const probe = 'const fs=require("node:fs");const r=JSON.parse(fs.readFileSync(process.env.CODEMASTERY_E2E_INVENTORY,"utf8"));'
            + 'console.log(JSON.stringify({state:r.state,runId:r.runId,outputs:r.outputs.length,token:process.env.CODEMASTERY_E2E_OWNERSHIP,same:r.runId===process.env.CODEMASTERY_E2E_RUN_ID,'
            + 'runner:r.processes.runner||null,pid:process.pid,hooks:[process.env.E2E_VITE_BIN,process.env.E2E_VITE_MODULE].filter(Boolean).length}));';
        const result = coordinate(checkout, ['run', '--', process.execPath, '-e', probe], { E2E_VITE_BIN: '/nowhere/vite.js', E2E_VITE_MODULE: '/nowhere/vite.mjs' });
        assert.equal(result.status, EXIT.ok, result.stderr);
        const seen = JSON.parse(result.stdout.split('\n').find((line) => line.startsWith('{')));
        assert.equal(seen.state, 'active', 'the record is already persisted when the runner starts');
        assert.equal(seen.runner?.role, 'playwright-runner', 'the runner\'s creation intent was durable before it was spawned');
        assert.equal(seen.hooks, 0, 'fixture-test hooks are never handed to a coordinated run');
        const runner = readRecord(checkout.key).processes.runner;
        assert.equal(runner.pid, seen.pid, 'the coordinator recorded the PID of the process it spawned');
        assert.match(runner.birth, /^(linux|darwin)-starttime:/, 'with its OS birth identity');
        assert.equal(runner.group, seen.pid, 'the runner leads its own process group');
        assert.equal(runner.state, 'exited');
        assert.equal(runner.groupState, 'empty');
        assert.equal(seen.outputs, 3, 'all three CP-029 output roots are owned');
        assert.equal(seen.same, true);
        assert.equal(seen.token, `${checkout.key}:${seen.runId}`);
        assert.equal(readRecord(checkout.key).state, 'complete', 'a verified run ends terminal');
    });

    test('the lock file keeps one stable inode across runs and is never replaced', () => {
        const checkout = fakeCheckout('stable');
        assert.equal(coordinate(checkout, ['run', '--', '/bin/echo', 'one']).status, EXIT.ok);
        const first = lstatSync(join(registry, 'locks', `${checkout.key}.lock`));
        assert.equal(coordinate(checkout, ['run', '--', '/bin/echo', 'two']).status, EXIT.ok);
        const second = lstatSync(join(registry, 'locks', `${checkout.key}.lock`));
        assert.equal(second.ino, first.ino, 'the lock inode is stable');
        assert.equal(second.mode & 0o777, 0o600);
        assert.equal(second.nlink, 1);
    });

    test('a HOME override does not move the registry: `~` comes from the effective UID', () => {
        const checkout = fakeCheckout('home');
        const elsewhere = mkdtempSync(join(tmpdir(), 'codemastery-fake-home-'));
        try {
            const result = coordinate(checkout, ['run', '--', '/bin/echo', 'hi'], { HOME: elsewhere });
            assert.equal(result.status, EXIT.ok, result.stderr);
            assert.ok(existsSync(recordFile(checkout.key)), 'the record is in the account registry');
            assert.ok(!existsSync(join(elsewhere, '.codemastery-e2e')), 'the HOME override was ignored');
        } finally {
            rmSync(elsewhere, { recursive: true, force: true });
        }
    });

    test('the completed record is archived, not deleted, when the next run is admitted', () => {
        const checkout = fakeCheckout('archive');
        assert.equal(coordinate(checkout, ['run', '--', '/bin/echo', 'first']).status, EXIT.ok);
        const first = readRecord(checkout.key).runId;
        assert.equal(coordinate(checkout, ['run', '--', '/bin/echo', 'second']).status, EXIT.ok);
        const archived = JSON.parse(readFileSync(join(registry, 'archive', `${checkout.key}.last.json`), 'utf8'));
        assert.equal(archived.runId, first);
        assert.equal(archived.state, 'complete');
        assert.notEqual(readRecord(checkout.key).runId, first);
        assert.deepEqual(sentinels(checkout), ['SENTINEL', '{"sentinel":true}'], 'the coordinator never cleans outputs itself');
    });
});

describe('exactly one invocation of a checkout is admitted', () => {
    test('a second invocation from the same checkout is refused without touching anything', async () => {
        const checkout = fakeCheckout('same');
        const gate = join(checkout.root, 'gate');
        const first = startGated(checkout, gate);
        try {
            const active = await awaitActive(checkout.key);
            const loser = coordinate(checkout, ['run', '--', '/bin/echo', 'should not run']);
            assert.equal(loser.status, EXIT.conflict, loser.stdout + loser.stderr);
            assert.match(loser.stderr, /another E2E invocation owns this checkout \(lock key /);
            assert.match(loser.stderr, new RegExp(checkout.key.replace('-', '\\-')));
            assert.ok(!/should not run/.test(loser.stdout), 'the runner was not started');
            assert.equal(readRecord(checkout.key).runId, active.runId, "the winner's record is untouched");
            assert.deepEqual(sentinels(checkout), ['SENTINEL', '{"sentinel":true}'], 'outputs are intact');
        } finally {
            open(gate);
            await settle(first);
        }
        assert.equal(readRecord(checkout.key).state, 'complete');
    });

    test('a path alias to the same directory contends for the same lock', async () => {
        const checkout = fakeCheckout('alias');
        const aliasRoot = join(mkdtempSync(join(tmpdir(), 'codemastery-alias-')), 'link');
        symlinkSync(checkout.root, aliasRoot);
        const gate = join(checkout.root, 'gate');
        const first = startGated(checkout, gate);
        try {
            await awaitActive(checkout.key);
            // The coordinator is entered through the alias; it resolves its own location to the same directory.
            const loser = spawnSync(python, [launcherOf(aliasRoot), 'run', '--', '/bin/echo', 'aliased'],
                { encoding: 'utf8', env: env(), cwd: aliasRoot });
            assert.equal(loser.status, EXIT.conflict, loser.stdout + loser.stderr);
            assert.match(loser.stderr, new RegExp(`lock key ${checkout.key.replace('-', '\\-')}`));
        } finally {
            open(gate);
            await settle(first);
            rmSync(dirname(aliasRoot), { recursive: true, force: true });
        }
    });

    test('two independently isolated checkouts run at the same time with their own records', async () => {
        const one = fakeCheckout('iso1'), two = fakeCheckout('iso2');
        const gates = [join(one.root, 'gate'), join(two.root, 'gate')];
        const children = [startGated(one, gates[0]), startGated(two, gates[1])];
        try {
            const records = [await awaitActive(one.key), await awaitActive(two.key)];
            assert.notEqual(records[0].runId, records[1].runId);
            assert.notEqual(records[0].checkout.ino, records[1].checkout.ino);
            assert.equal(records[0].state, 'active');
            assert.equal(records[1].state, 'active', 'neither isolated checkout blocks the other');
        } finally {
            gates.forEach(open);
            await Promise.all(children.map(settle));
        }
        assert.equal(readRecord(one.key).state, 'complete');
        assert.equal(readRecord(two.key).state, 'complete');
    });

    test('`recover` on a checkout owned by a live coordinator preserves it instead of cleaning up', async () => {
        const checkout = fakeCheckout('livecoord');
        const gate = join(checkout.root, 'gate');
        const first = startGated(checkout, gate);
        try {
            await awaitActive(checkout.key);
            const recover = coordinate(checkout, ['recover']);
            assert.equal(recover.status, EXIT.conflict, recover.stdout + recover.stderr);
            assert.equal(readRecord(checkout.key).state, 'active', 'the live run is untouched');
        } finally {
            open(gate);
            await settle(first);
        }
    });
});

const goneRunner = spawnSync(process.execPath, ['-e', '']).pid;

describe('an unresolved ownership obligation blocks reuse until recovery completes', () => {
    /** Writes a record by hand, as a run killed before its teardown would leave behind. */
    const leaveRecord = (checkout, patch) => {
        const record = {
            schema: 'codemastery-e2e-inventory/1', contract: 'CT34-FIXTURES-2026-11-10-r2',
            runId: 'cm-e2e-orphan', state: 'active', ownerUid: process.getuid(), lockKey: checkout.key, fence: 1, revision: 1,
            host: { hostname: 'somewhere-else', boot: 'boot:0', pidNamespace: null, system: 'Linux' },
            checkout: { path: checkout.root, dev: 1, ino: 2 }, outputs: [], docker: null,
            temp: null, intents: [], observations: [], ...patch,
            // What the coordinator always records: the runner it started (long gone) and its own group.
            processes: { runner: { pid: goneRunner, birth: 'x-starttime:0', role: 'playwright-runner', state: 'live', spawned: true, group: goneRunner }, ...(patch.processes ?? {}) },
        };
        writeFileSync(recordFile(checkout.key), JSON.stringify(record), { mode: 0o600 });
        return record;
    };

    test('a released lock does not authorize reuse while the record is nonterminal', () => {
        const checkout = fakeCheckout('orphan');
        leaveRecord(checkout, { state: 'active' });
        const blocked = coordinate(checkout, ['run', '--', '/bin/echo', 'must not run']);
        assert.equal(blocked.status, EXIT.blocked, blocked.stdout + blocked.stderr);
        assert.match(blocked.stderr, /has an unresolved run \(cm-e2e-orphan, state active\)/);
        assert.match(blocked.stderr, /npm run stack:down/);
        assert.ok(!/must not run/.test(blocked.stdout), 'the runner was not started');
        assert.equal(readRecord(checkout.key).state, 'active', 'the evidence is retained, not deleted');
    });

    test('the same record blocks an `incomplete` and a `cleaning` state too', () => {
        const checkout = fakeCheckout('states');
        for (const state of ['incomplete', 'cleaning']) {
            leaveRecord(checkout, { state });
            const blocked = coordinate(checkout, ['run', '--', '/bin/echo', 'no']);
            assert.equal(blocked.status, EXIT.blocked, `state ${state} must block`);
        }
    });

    test('a corrupt record blocks automatic reuse and is preserved for operator reconciliation', () => {
        const checkout = fakeCheckout('corrupt');
        writeFileSync(recordFile(checkout.key), '{"schema":"codemastery-e2e-inven', { mode: 0o600 });
        const blocked = coordinate(checkout, ['run', '--', '/bin/echo', 'no']);
        assert.equal(blocked.status, EXIT.blocked, blocked.stdout + blocked.stderr);
        assert.match(blocked.stderr, /is corrupt/);
        assert.match(blocked.stderr, /remove it only after confirming there are no leftovers/);
        assert.ok(existsSync(recordFile(checkout.key)), 'the corrupt record is not deleted automatically');
    });

    test('recovery resolves a foreign-host orphan by preserving it, and reuse stays blocked', () => {
        const checkout = fakeCheckout('foreign');
        leaveRecord(checkout, {
            state: 'active',
            processes: { preview: { pid: 999_999, birth: 'linux-starttime:1', birthSource: 'linux-proc-starttime', role: 'vite-preview', state: 'live' } },
        });
        const recover = coordinate(checkout, ['recover']);
        assert.equal(recover.status, EXIT.fail, 'an unresolvable orphan is reported as incomplete');
        assert.match(recover.stdout, /preserved preview 999999: recorded on another host, boot or PID namespace/);
        assert.equal(readRecord(checkout.key).state, 'incomplete');
        assert.equal(coordinate(checkout, ['run', '--', '/bin/echo', 'no']).status, EXIT.blocked);
    });

    test('recovery of a resolvable orphan completes, and only then is the checkout reusable again', () => {
        const checkout = fakeCheckout('resolvable');
        const host = JSON.parse(spawnSync(python, [launcher, 'host-identity'], { encoding: 'utf8' }).stdout);
        leaveRecord(checkout, {
            state: 'incomplete',
            host: { hostname: host.hostname, boot: host.boot, pidNamespace: host.pidNamespace, system: host.system },
            processes: { preview: { pid: 999_999, birth: 'x-starttime:1', birthSource: 'test', role: 'vite-preview', state: 'live' } },
        });
        const recover = coordinate(checkout, ['recover']);
        assert.equal(recover.status, EXIT.ok, recover.stdout + recover.stderr);
        assert.match(recover.stdout, /preview 999999 is verifiably gone/);
        assert.equal(readRecord(checkout.key).state, 'complete');
        assert.equal(coordinate(checkout, ['run', '--', '/bin/echo', 'reused']).status, EXIT.ok);
    });

    test('a record whose mode was widened blocks reuse instead of being trusted', () => {
        const checkout = fakeCheckout('widened');
        leaveRecord(checkout, { state: 'complete' });
        spawnSync('chmod', ['644', recordFile(checkout.key)]);
        const blocked = coordinate(checkout, ['run', '--', '/bin/echo', 'no']);
        assert.equal(blocked.status, EXIT.blocked, blocked.stdout + blocked.stderr);
        assert.match(blocked.stderr, /has mode 644, not 600/);
    });

    test('`recover` with no record at all succeeds and reports that nothing is owned', () => {
        const checkout = fakeCheckout('norecord');
        const recover = coordinate(checkout, ['recover']);
        assert.equal(recover.status, EXIT.ok, recover.stdout + recover.stderr);
        assert.match(recover.stdout, /no inventory record for this checkout/);
    });
});

describe('unsafe checkouts and output roots are refused before anything starts', () => {
    test('an output root reached through a symlink is refused', () => {
        const checkout = fakeCheckout('symlink');
        rmSync(join(checkout.root, 'test-results', 'html'), { recursive: true, force: true });
        const elsewhere = mkdtempSync(join(tmpdir(), 'codemastery-elsewhere-'));
        symlinkSync(elsewhere, join(checkout.root, 'test-results', 'html'));
        try {
            const result = coordinate(checkout, ['run', '--', '/bin/echo', 'no']);
            assert.equal(result.status, EXIT.unqualified, result.stdout + result.stderr);
            assert.match(result.stderr, /is a symlink; outputs must be ordinary files or directories inside this checkout/);
            assert.ok(!existsSync(recordFile(checkout.key)), 'no ownership is recorded for a refused checkout');
        } finally {
            rmSync(elsewhere, { recursive: true, force: true });
        }
    });

    test('an output file shared with another checkout by a hard link is refused', () => {
        const one = fakeCheckout('hl1'), two = fakeCheckout('hl2');
        rmSync(join(two.root, 'test-results', 'results.json'), { force: true });
        spawnSync('ln', [join(one.root, 'test-results', 'results.json'), join(two.root, 'test-results', 'results.json')]);
        const result = coordinate(two, ['run', '--', '/bin/echo', 'no']);
        assert.equal(result.status, EXIT.unqualified, result.stdout + result.stderr);
        assert.match(result.stderr, /has 2 links; it may be shared with another checkout/);
    });

    test('an output root already claimed by another checkout\'s active run is refused', () => {
        const victim = fakeCheckout('claimed'), other = fakeCheckout('claimant');
        const artifacts = lstatSync(join(victim.root, 'test-results', 'artifacts'));
        const otherInfo = lstatSync(other.root);
        writeFileSync(recordFile(other.key), JSON.stringify({
            schema: 'codemastery-e2e-inventory/1', contract: 'CT34-FIXTURES-2026-11-10-r2', runId: 'cm-e2e-claimant', state: 'active',
            ownerUid: process.getuid(), lockKey: other.key, fence: 1, revision: 1, checkout: { path: other.root, dev: otherInfo.dev, ino: otherInfo.ino },
            processes: {}, intents: [], docker: null, temp: null, observations: [],
            outputs: [{ path: join(other.root, 'test-results', 'artifacts'), dev: artifacts.dev, ino: artifacts.ino, kind: 'directory' }],
        }), { mode: 0o600 });
        const result = coordinate(victim, ['run', '--', '/bin/echo', 'no']);
        assert.equal(result.status, EXIT.unqualified, result.stdout + result.stderr);
        assert.match(result.stderr, /is the same object as .*owned by the active run of checkout/s);
    });
});

describe('process birth identity, not a PID, decides what a recorded process is', () => {
    test('a live process is reliable, an absent PID is reliably absent, and the two differ', () => {
        const mine = JSON.parse(spawnSync(python, [launcher, 'proc-identity', String(process.pid)], { encoding: 'utf8' }).stdout);
        assert.equal(mine.exists, true);
        assert.equal(mine.reliable, true);
        assert.ok(/^(linux|darwin)-starttime:/.test(mine.birth), mine.birth);
        const gone = JSON.parse(spawnSync(python, [launcher, 'proc-identity', '999999'], { encoding: 'utf8' }).stdout);
        assert.equal(gone.exists, false);
        assert.equal(gone.reliable, true);
        assert.equal(gone.birth, null);
    });

    test('the same PID reports the same birth identity twice, so the comparison is stable', () => {
        const read = () => JSON.parse(spawnSync(python, [launcher, 'proc-identity', String(process.pid)], { encoding: 'utf8' }).stdout).birth;
        assert.equal(read(), read());
    });
});

describe('an obligation recorded under an earlier device identity of the same checkout', () => {
    // macOS assigns APFS device numbers at mount time, so after a reboot an unchanged checkout can
    // have a new st_dev and therefore a new lock key. The earlier record is reproduced exactly: same
    // canonical path and inode, the previous device number, and its own lock key.
    const earlier = (checkout, patch = {}) => {
        const info = lstatSync(checkout.root);
        // What the coordinator always writes: its own entry and the runner it started, both long gone.
        const gone = () => spawnSync(process.execPath, ['-e', '']).pid;
        const runnerPid = gone();
        const key = `${info.dev - 1}-${info.ino}`;
        extraKeys.push(key);
        const host = JSON.parse(spawnSync(python, [launcher, 'host-identity'], { encoding: 'utf8' }).stdout);
        writeFileSync(recordFile(key), JSON.stringify({
            schema: 'codemastery-e2e-inventory/1', contract: 'CT34-FIXTURES-2026-11-10-r2', runId: 'cm-e2e-before-reboot',
            state: 'incomplete', ownerUid: process.getuid(), lockKey: key, fence: 1, revision: 1,
            host: { hostname: host.hostname, machine: host.machine, boot: host.boot, pidNamespace: host.pidNamespace, system: host.system },
            checkout: { path: checkout.root, dev: info.dev - 1, ino: info.ino }, outputs: [], docker: null,
            processes: {
                supervisor: { pid: gone(), birth: 'x-starttime:1', birthSource: 'test', role: 'coordinator', state: 'live' },
                runner: { pid: runnerPid, birth: 'x-starttime:2', birthSource: 'test', role: 'playwright-runner', state: 'live', spawned: true, group: runnerPid },
            },
            temp: null, intents: [], observations: [], ...patch,
        }), { mode: 0o600 });
        return key;
    };

    test('blocks a new run, and `recover` reconciles that record under its own lock before reuse', () => {
        const checkout = fakeCheckout('devchange');
        const key = earlier(checkout);
        const blocked = coordinate(checkout, ['run', '--', '/bin/echo', 'must not run']);
        assert.equal(blocked.status, EXIT.blocked, blocked.stdout + blocked.stderr);
        assert.match(blocked.stderr, new RegExp(`unresolved run \\(cm-e2e-before-reboot, state incomplete\\) recorded under its earlier device identity ${key}`));
        assert.ok(!/must not run/.test(blocked.stdout));
        const recover = coordinate(checkout, ['recover']);
        assert.equal(recover.status, EXIT.ok, recover.stdout + recover.stderr);
        assert.match(recover.stdout, new RegExp(`reconciling run cm-e2e-before-reboot, recorded under this checkout's earlier device identity ${key}`));
        assert.equal(readRecord(key).state, 'complete', 'the earlier record is reconciled, not deleted');
        assert.ok(existsSync(join(registry, 'locks', `${key}.lock`)), 'its lock file is used, never removed');
        assert.equal(coordinate(checkout, ['run', '--', '/bin/echo', 'reused']).status, EXIT.ok);
    });

    test('an earlier record from a previous boot stays preserved and keeps blocking reuse', () => {
        const checkout = fakeCheckout('reboot');
        const key = earlier(checkout, {
            host: { hostname: 'x', boot: 'darwin-boot:1', pidNamespace: null, system: 'Darwin' },
            processes: { preview: { pid: 999_999, birth: 'darwin-starttime:1.000001', birthSource: 'darwin-kinfo-starttime', role: 'vite-preview', state: 'live' } },
        });
        const recover = coordinate(checkout, ['recover']);
        assert.equal(recover.status, EXIT.fail, recover.stdout + recover.stderr);
        assert.match(recover.stdout, /preserved preview 999999: recorded on another host, boot or PID namespace/);
        assert.equal(readRecord(key).state, 'incomplete');
        assert.equal(coordinate(checkout, ['run', '--', '/bin/echo', 'no']).status, EXIT.blocked);
    });

    test('a record of another checkout (other path or other inode) is never associated', () => {
        const checkout = fakeCheckout('notmine');
        const elsewhere = fakeCheckout('elsewhere');
        earlier(checkout, { checkout: { path: elsewhere.root, dev: 1, ino: lstatSync(checkout.root).ino } });
        const other = earlier(elsewhere);
        writeFileSync(recordFile(other), JSON.stringify({ ...readRecord(other), checkout: { path: checkout.root, dev: 1, ino: lstatSync(checkout.root).ino + 1 } }), { mode: 0o600 });
        const result = coordinate(checkout, ['run', '--', '/bin/echo', 'admitted']);
        assert.equal(result.status, EXIT.ok, result.stdout + result.stderr);
        assert.match(result.stdout, /admitted/);
    });
});

describe('the runner is owned from before its spawn until its whole process group has exited', () => {
    const inventoryModule = pathToFileURL(join(here, 'run-inventory.mjs')).href;
    const waitFor = async (what, check, timeoutMs = 30_000) => {
        const deadline = Date.now() + timeoutMs;
        for (;;) {
            const value = check();
            if (value) return value;
            if (Date.now() > deadline) throw new Error(`timed out waiting for ${what}`);
            await new Promise((next) => setTimeout(next, 50));
        }
    };
    const alive = (pid) => { try { process.kill(pid, 0); return true; } catch (error) { return error.code === 'EPERM'; } };
    const gatedWait = 'await new Promise((go) => { const t = setInterval(() => { if (existsSync(GATE)) { clearInterval(t); go(); } }, 40); });';

    test('coordinator SIGKILL before Playwright global setup: the live runner keeps the checkout blocked and can write nothing', async () => {
        const checkout = fakeCheckout('sigkill');
        const gate = join(checkout.root, 'gate'), started = join(checkout.root, 'started.json'), result = join(checkout.root, 'result.json');
        // Stands in for the Playwright CLI between its start and global setup's first inventory write.
        const runner = `import { existsSync, writeFileSync } from 'node:fs';
            const { readInventory, updateInventory } = await import(${JSON.stringify(inventoryModule)});
            const file = process.env.CODEMASTERY_E2E_INVENTORY, GATE = ${JSON.stringify(gate)};
            writeFileSync(${JSON.stringify(started)}, JSON.stringify({ intent: readInventory(file).processes.runner ?? null }));
            ${gatedWait}
            let outcome = 'written';
            try {
                updateInventory(file, (record) => { record.observations.push('the orphan wrote'); });
                writeFileSync('test-results/results.json', '{"overwritten":true}');
            } catch (error) { outcome = error.constructor.name + ': ' + error.message; }
            writeFileSync(${JSON.stringify(result)}, JSON.stringify({ outcome }));`;
        const coordinator = spawn(python, [launcherOf(checkout.root), 'run', '--', process.execPath, '--input-type=module', '-e', runner],
            { env: env(), cwd: checkout.root, stdio: 'ignore' });
        try {
            const recorded = await waitFor('the runner identity', () => existsSync(started) && readRecord(checkout.key)?.processes?.runner?.pid && readRecord(checkout.key));
            const runId = recorded.runId, runnerPid = recorded.processes.runner.pid;
            assert.ok(JSON.parse(readFileSync(started, 'utf8')).intent, 'the runner intent existed before the runner ran');
            coordinator.kill('SIGKILL');
            await settle(coordinator);
            assert.ok(alive(runnerPid), 'the runner outlives its coordinator');

            const recover = coordinate(checkout, ['recover']);
            assert.equal(recover.status, EXIT.fail, recover.stdout + recover.stderr);
            assert.match(recover.stdout, new RegExp(`preserved runner ${runnerPid}: the PID carries the recorded birth identity`));
            assert.equal(readRecord(checkout.key).state, 'incomplete', 'never complete while the runner lives');
            const blocked = coordinate(checkout, ['run', '--', '/bin/echo', 'must not run']);
            assert.equal(blocked.status, EXIT.blocked, blocked.stdout + blocked.stderr);
            assert.ok(!/must not run/.test(blocked.stdout));

            open(gate);
            const { outcome } = JSON.parse(await waitFor('the runner result', () => existsSync(result) && readFileSync(result, 'utf8')));
            assert.match(outcome, /^OwnershipLost: refusing to change inventory .* is no longer this process' parent/);
            assert.deepEqual(sentinels(checkout), ['SENTINEL', '{"sentinel":true}'], 'the orphan wrote no output');
            await waitFor('the runner exit', () => !alive(runnerPid));

            const after = coordinate(checkout, ['recover']);
            assert.equal(after.status, EXIT.ok, after.stdout + after.stderr);
            const final = readRecord(checkout.key);
            assert.equal(final.runId, runId);
            assert.equal(final.state, 'complete');
            assert.ok(!final.observations.some((line) => line.includes('the orphan wrote')));
            assert.equal(coordinate(checkout, ['run', '--', '/bin/echo', 'reused']).status, EXIT.ok);
        } finally {
            // Never leave a gated process behind when an assertion fails.
            open(gate);
            if (coordinator.exitCode === null && coordinator.signalCode === null) coordinator.kill('SIGKILL');
        }
    });

    test('a crash between the runner intent and its recorded identity leaves an unresolved writer, never an exited one', () => {
        const checkout = fakeCheckout('intent');
        const host = JSON.parse(spawnSync(python, [launcher, 'host-identity'], { encoding: 'utf8' }).stdout);
        const info = lstatSync(checkout.root);
        writeFileSync(recordFile(checkout.key), JSON.stringify({
            schema: 'codemastery-e2e-inventory/1', contract: 'CT34-FIXTURES-2026-11-10-r2', runId: 'cm-e2e-crashed', state: 'active',
            ownerUid: process.getuid(), lockKey: checkout.key, fence: 1, revision: 1, checkout: { path: checkout.root, dev: info.dev, ino: info.ino },
            host: { hostname: host.hostname, machine: host.machine, boot: host.boot, pidNamespace: host.pidNamespace, system: host.system },
            outputs: [], docker: null, temp: null, intents: [], observations: [],
            processes: {
                supervisor: { pid: spawnSync(process.execPath, ['-e', '']).pid, birth: 'x-starttime:1', role: 'coordinator', state: 'live' },
                runner: { pid: null, birth: null, birthSource: null, role: 'playwright-runner', state: 'starting' },
            },
        }), { mode: 0o600 });
        const recover = coordinate(checkout, ['recover']);
        assert.equal(recover.status, EXIT.fail, recover.stdout + recover.stderr);
        assert.match(recover.stdout, /preserved runner \(no PID recorded\): no PID recorded/);
        assert.equal(readRecord(checkout.key).state, 'incomplete');
        assert.equal(coordinate(checkout, ['run', '--', '/bin/echo', 'no']).status, EXIT.blocked);
    });

    test('the coordinator finalizes only after the runner\'s process group (Playwright workers) has exited', () => {
        const checkout = fakeCheckout('workers');
        const late = join(checkout.root, 'test-results', 'artifacts', 'late.txt');
        // The runner exits at once; a process it started keeps running and writes an output later,
        // exactly like a Playwright worker finishing its teardown after the runner is gone.
        const runner = `const { spawn } = require('node:child_process');
            spawn(process.execPath, ['-e', 'setTimeout(() => require("node:fs").writeFileSync(${JSON.stringify(late)}, "late"), 1500)'], { stdio: 'ignore' }).unref();`;
        const result = coordinate(checkout, ['run', '--', process.execPath, '-e', runner]);
        assert.equal(result.status, EXIT.ok, result.stdout + result.stderr);
        assert.ok(existsSync(late), 'the late writer finished before the coordinator finalized and released the checkout');
        const record = readRecord(checkout.key);
        assert.equal(record.state, 'complete');
        assert.equal(record.processes.runner.groupState, 'empty');
    });

    test('after a coordinator SIGKILL, recovery waits for the runner\'s surviving group members as well', async () => {
        const checkout = fakeCheckout('orphanworker');
        const gate = join(checkout.root, 'gate'), workerGate = join(checkout.root, 'worker-gate'), workerPidFile = join(checkout.root, 'worker.pid');
        const runner = `import { existsSync, writeFileSync } from 'node:fs';
            import { spawn } from 'node:child_process';
            const GATE = ${JSON.stringify(gate)};
            const worker = spawn(process.execPath, ['-e', 'const fs=require("node:fs");const t=setInterval(()=>{if(fs.existsSync(${JSON.stringify(workerGate)})){clearInterval(t);}},40);'], { stdio: 'ignore' });
            worker.unref();
            writeFileSync(${JSON.stringify(workerPidFile)}, String(worker.pid));
            ${gatedWait}`;
        const coordinator = spawn(python, [launcherOf(checkout.root), 'run', '--', process.execPath, '--input-type=module', '-e', runner],
            { env: env(), cwd: checkout.root, stdio: 'ignore' });
        try {
            const runnerPid = await waitFor('the runner identity', () => existsSync(workerPidFile) && readRecord(checkout.key)?.processes?.runner?.pid);
            const workerPid = Number(readFileSync(workerPidFile, 'utf8'));
            coordinator.kill('SIGKILL');
            await settle(coordinator);
            open(gate);
            await waitFor('the runner exit', () => !alive(runnerPid));
            assert.ok(alive(workerPid), 'the worker outlives the runner');

            const recover = coordinate(checkout, ['recover']);
            assert.equal(recover.status, EXIT.fail, recover.stdout + recover.stderr);
            assert.match(recover.stdout, new RegExp(`runner ${runnerPid} is verifiably gone`));
            assert.match(recover.stdout, new RegExp(`process group ${runnerPid} still has 1 live process`));
            assert.equal(readRecord(checkout.key).state, 'incomplete');

            open(workerGate);
            await waitFor('the worker exit', () => !alive(workerPid));
            const after = coordinate(checkout, ['recover']);
            assert.equal(after.status, EXIT.ok, after.stdout + after.stderr);
            assert.equal(readRecord(checkout.key).state, 'complete');
        } finally {
            open(gate);
            open(workerGate);
            if (coordinator.exitCode === null && coordinator.signalCode === null) coordinator.kill('SIGKILL');
        }
    });
});

describe('malformed records are refused cleanly, never half-interpreted', () => {
    const malformed = (key) => {
        extraKeys.push(key);
        writeFileSync(recordFile(key), JSON.stringify({
            schema: 'codemastery-e2e-inventory/1', ownerUid: process.getuid(), runId: 'cm-e2e-bad', state: 'active',
            lockKey: key, checkout: 'not-an-object', outputs: ['not-an-entry'], processes: {}, intents: [],
        }), { mode: 0o600 });
    };
    test('another checkout\'s malformed record neither crashes nor blocks this checkout', () => {
        const checkout = fakeCheckout('foreignbad');
        malformed('999999-1');
        const result = coordinate(checkout, ['run', '--', '/bin/echo', 'admitted']);
        assert.equal(result.status, EXIT.ok, result.stdout + result.stderr);
        assert.ok(!/Traceback/.test(result.stderr), result.stderr);
    });
    test('a wrongly typed field (intents: 5) is refused before any use, never a crash, for this or another checkout', () => {
        const checkout = fakeCheckout('typedbad');
        extraKeys.push('999999-2');
        const typed = (key, runId) => JSON.stringify({ schema: 'codemastery-e2e-inventory/1', ownerUid: process.getuid(), runId, state: 'active',
            lockKey: key, fence: 1, revision: 1, checkout: { path: '/x', dev: 1, ino: 2 }, outputs: [], processes: {}, intents: 5 });
        writeFileSync(recordFile('999999-2'), typed('999999-2', 'cm-e2e-typed'), { mode: 0o600 });
        const admitted = coordinate(checkout, ['run', '--', '/bin/echo', 'admitted']);
        assert.equal(admitted.status, EXIT.ok, admitted.stdout + admitted.stderr);
        assert.ok(!/Traceback|TypeError/.test(admitted.stderr), admitted.stderr);
        writeFileSync(recordFile(checkout.key), typed(checkout.key, 'cm-e2e-typed-own'), { mode: 0o600 });
        const blocked = coordinate(checkout, ['run', '--', '/bin/echo', 'no']);
        assert.equal(blocked.status, EXIT.blocked, blocked.stdout + blocked.stderr);
        assert.match(blocked.stderr, /is malformed \(intents\)/);
        assert.ok(!/Traceback|TypeError/.test(blocked.stderr), blocked.stderr);
    });

    test('this checkout\'s own malformed record blocks reuse with a clear error', () => {
        const checkout = fakeCheckout('ownbad');
        writeFileSync(recordFile(checkout.key), JSON.stringify({
            schema: 'codemastery-e2e-inventory/1', ownerUid: process.getuid(), runId: 'cm-e2e-bad', state: 'active',
            lockKey: checkout.key, checkout: 'not-an-object', outputs: [], processes: {}, intents: [],
        }), { mode: 0o600 });
        const blocked = coordinate(checkout, ['run', '--', '/bin/echo', 'no']);
        assert.equal(blocked.status, EXIT.blocked, blocked.stdout + blocked.stderr);
        assert.match(blocked.stderr, /is malformed \(checkout identity\)/);
        assert.ok(!/Traceback/.test(blocked.stderr));
    });
});

// ------------------------------------------------------------------------------------------------
// Round 3: inventory fencing, preview safety and fail-closed process-group verification.
// ------------------------------------------------------------------------------------------------

/** A stateful stub Docker CLI whose `rm` blocks once (a finalizer paused inside a Docker removal). */
const STUB_DOCKER = `#!/usr/bin/env node
const fs = require('node:fs'), path = require('node:path');
const dir = process.env.STUB_DIR, file = path.join(dir, 'state.json');
const read = () => (fs.existsSync(file) ? JSON.parse(fs.readFileSync(file, 'utf8')) : { resources: {} });
const args = process.argv.slice(2), id = args.at(-1), state = read(), item = state.resources[id];
const fail = (m) => { process.stderr.write(m + '\\n'); process.exit(1); };
if (args[0] === 'context' && args[1] === 'show') console.log('stub-context');
else if (args[0] === 'context' && args[1] === 'inspect') console.log('unix:///var/run/docker.sock');
else if (args[0] === 'info') console.log('D1');
else if (args[0] === 'ps' || args[1] === 'ls') {
    const kind = args[0] === 'ps' ? 'container' : args[0];
    const wanted = args.filter((a, i) => args[i - 1] === '--filter' && a.startsWith('label=')).map((a) => a.slice(6).split('='));
    console.log(Object.entries(state.resources).filter(([, r]) => r.kind === kind && r.present && wanted.every(([k, v]) => r.labels[k] === v)).map(([k]) => k).join('\\n'));
} else if (args[0] === 'inspect' || args[1] === 'inspect') {
    if (!item || !item.present) fail(args[0] === 'network' ? 'Error response from daemon: network ' + id + ' not found' : 'Error response from daemon: No such container: ' + id);
    console.log(args[0] === 'inspect' ? JSON.stringify({ Id: id, Config: { Labels: item.labels }, Mounts: [], State: { Status: 'running' } }) : JSON.stringify(item.labels));
} else if (args[0] === 'rm' || args[1] === 'rm') {
    if (fs.existsSync(path.join(dir, 'block-once'))) {
        fs.rmSync(path.join(dir, 'block-once'));
        fs.writeFileSync(path.join(dir, 'rm-blocked'), String(process.pid));
        while (!fs.existsSync(path.join(dir, 'gate'))) Atomics.wait(new Int32Array(new SharedArrayBuffer(4)), 0, 0, 40);
    }
    const now = read(), it = now.resources[id];
    if (!it || !it.present) fail('Error response from daemon: No such container: ' + id);
    it.present = false; fs.writeFileSync(file, JSON.stringify(now)); console.log(id);
} else fail('unexpected docker ' + args.join(' '));
`;
const STUB_VITE = `import { createServer } from 'node:http';
export async function preview(options) {
    const http = createServer((_, response) => response.end('<!doctype html><html><head></head><body>stub preview</body></html>'));
    await new Promise((ready, failed) => { http.once('error', failed); http.listen(options.preview.port, options.preview.host, ready); });
    return { close: () => new Promise((closed) => { http.closeAllConnections(); http.close(closed); }), httpServer: http };
}
`;
const inventoryUrl = pathToFileURL(join(here, 'run-inventory.mjs')).href;
const hostIdentityUrl = pathToFileURL(join(here, 'host-identity.mjs')).href;
const alive3 = (pid) => { try { process.kill(pid, 0); return true; } catch (error) { return error.code === 'EPERM'; } };
async function until(what, check, timeoutMs = 30_000) {
    const deadline = Date.now() + timeoutMs;
    for (;;) {
        const value = check();
        if (value) return value;
        if (Date.now() > deadline) throw new Error(`timed out waiting for ${what}`);
        await new Promise((next) => setTimeout(next, 50));
    }
}
const gateLoop = (gate) => `await new Promise((go) => { const t = setInterval(() => { if (existsSync(${JSON.stringify(gate)})) { clearInterval(t); go(); } }, 40); });`;
const commit = (checkout, owner, payload) => spawnSync(python, [launcherOf(checkout.root), 'commit-record', recordFile(checkout.key), owner.runId, checkout.key, String(owner.fence), String(owner.revision)],
    { input: JSON.stringify(payload), encoding: 'utf8', timeout: 60_000 });

describe('P1-1: a writer that lost ownership is fenced out of the record permanently', () => {
    let stub = '';
    before(() => {
        stub = mkdtempSync(join(tmpdir(), 'codemastery-stub-docker-'));
        writeFileSync(join(stub, 'docker'), STUB_DOCKER);
        chmodSync(join(stub, 'docker'), 0o755);
    });
    after(() => rmSync(stub, { recursive: true, force: true }));

    test('finalizer A paused in a Docker removal, coordinator A killed, A recovered, run B admitted, A resumed: B is never touched', async () => {
        const checkout = fakeCheckout('fence');
        const state = mkdtempSync(join(tmpdir(), 'codemastery-stub-state-'));
        writeFileSync(join(state, 'block-once'), '');
        const stubEnv = { PATH: `${stub}:${process.env.PATH}`, STUB_DIR: state };
        // Runner A owns one (stub) container, recorded exactly as global setup records it.
        const runnerA = `import { writeFileSync } from 'node:fs';
            const { addIntent, setIntent, updateInventory } = await import(${JSON.stringify(inventoryUrl)});
            const run = process.env.CODEMASTERY_E2E_RUN_ID, file = process.env.CODEMASTERY_E2E_INVENTORY;
            writeFileSync(process.env.STUB_DIR + '/state.json', JSON.stringify({ resources: { C1: { kind: 'container', present: true, labels: { 'codemastery.e2e': 'disposable', 'codemastery.e2e.run': run } } } }));
            updateInventory(file, (r) => { r.mode = 'disposable'; r.docker = { context: 'stub-context', endpoint: 'unix:///var/run/docker.sock', daemonId: 'D1' }; });
            let id; updateInventory(file, (r) => { id = addIntent(r, { kind: 'container', name: run + '-c', volumes: 'recorded' }); });
            updateInventory(file, (r) => { setIntent(r, id, { dockerId: 'C1', state: 'created' }); });`;
        const coordinatorA = spawn(python, [launcherOf(checkout.root), 'run', '--', process.execPath, '--input-type=module', '-e', runnerA],
            { env: env(stubEnv), cwd: checkout.root, stdio: 'ignore' });
        let gateB = '', childB = null;
        try {
            await until('finalizer A to block inside docker rm', () => existsSync(join(state, 'rm-blocked')));
            const atPause = readRecord(checkout.key);
            const runA = atPause.runId, finalizerA = atPause.processes['finalizer-1'];
            assert.ok(finalizerA?.pid && alive3(finalizerA.pid), 'finalizer A is a recorded, live writer');
            assert.match(finalizerA.birth, /^(linux|darwin)-starttime:/);
            coordinatorA.kill('SIGKILL');
            await settle(coordinatorA);

            // Recovery takes the record over (new fence) but cannot complete while finalizer A lives.
            const recover = coordinate(checkout, ['recover'], stubEnv);
            assert.equal(recover.status, EXIT.fail, recover.stdout + recover.stderr);
            assert.match(recover.stdout, new RegExp(`preserved finalizer-1 ${finalizerA.pid}: the PID carries the recorded birth identity`));
            const afterTakeover = readRecord(checkout.key);
            assert.equal(afterTakeover.runId, runA);
            assert.equal(afterTakeover.fence, 2, 'recovery took the record over with a new generation');
            assert.equal(afterTakeover.state, 'incomplete');
            assert.equal(coordinate(checkout, ['run', '--', '/bin/echo', 'B must wait'], stubEnv).status, EXIT.blocked,
                'run B is not admitted while an earlier finalizer may still write');

            // Finalizer A resumes: it may finish its own Docker call, but it changes nothing in the record.
            const frozen = readFileSync(recordFile(checkout.key), 'utf8');
            writeFileSync(join(state, 'gate'), '');
            await until('finalizer A to exit', () => !alive3(finalizerA.pid));
            assert.equal(readFileSync(recordFile(checkout.key), 'utf8'), frozen, 'finalizer A wrote nothing after losing ownership');

            const done = coordinate(checkout, ['recover'], stubEnv);
            assert.equal(done.status, EXIT.ok, done.stdout + done.stderr);
            assert.match(done.stdout, new RegExp(`finalizer-1 ${finalizerA.pid} is verifiably gone`));
            const completeA = readRecord(checkout.key);
            assert.equal(completeA.state, 'complete');

            // Run B is admitted; a writer still holding A's ownership (run, fence, revision) cannot touch B.
            gateB = join(checkout.root, 'gate-b');
            childB = startGated(checkout, gateB);
            const recordB = await awaitActive(checkout.key);
            assert.notEqual(recordB.runId, runA);
            const before = readFileSync(recordFile(checkout.key), 'utf8');
            for (const fence of [1, 2, completeA.fence]) {
                const stale = commit(checkout, { runId: runA, fence, revision: completeA.revision }, { ...completeA, state: 'complete', fence });
                assert.equal(stale.status, 5, `a stale writer (fence ${fence}) is fenced: ${stale.stderr}`);
                assert.match(stale.stderr, /fenced: the record belongs to run cm-e2e-/);
            }
            assert.equal(readFileSync(recordFile(checkout.key), 'utf8'), before, "B's record is byte-identical");
            open(gateB);
            assert.equal(await settle(childB), EXIT.ok);
            childB = null;
            assert.equal(readRecord(checkout.key).runId, recordB.runId);
            assert.equal(readRecord(checkout.key).state, 'complete');
            assert.deepEqual(sentinels(checkout), ['SENTINEL', '{"sentinel":true}'], 'no output was touched by A');
        } finally {
            writeFileSync(join(state, 'gate'), '');
            if (gateB) open(gateB);
            if (coordinatorA.exitCode === null && coordinatorA.signalCode === null) coordinatorA.kill('SIGKILL');
            if (childB) await settle(childB);
            rmSync(state, { recursive: true, force: true });
        }
    });

    test('a recovery takeover fences every earlier generation, even one whose run ID and revision still match', () => {
        const checkout = fakeCheckout('takeover');
        assert.equal(coordinate(checkout, ['run', '--', '/bin/echo', 'a']).status, EXIT.ok);
        const generation1 = readRecord(checkout.key);
        assert.equal(coordinate(checkout, ['recover']).status, EXIT.ok);
        const generation2 = readRecord(checkout.key);
        assert.equal(generation2.fence, generation1.fence + 1);
        const before = readFileSync(recordFile(checkout.key), 'utf8');
        const stale = commit(checkout, { runId: generation2.runId, fence: generation1.fence, revision: generation2.revision }, { ...generation2, fence: generation1.fence });
        assert.equal(stale.status, 5, stale.stderr);
        assert.match(stale.stderr, /ownership was taken over/);
        assert.equal(readFileSync(recordFile(checkout.key), 'utf8'), before);
    });

    test('concurrent writers of one generation are serialized: exactly one compare-and-swap wins, none is lost silently', async () => {
        const checkout = fakeCheckout('cas');
        assert.equal(coordinate(checkout, ['run', '--', '/bin/echo', 'a']).status, EXIT.ok);
        const current = readRecord(checkout.key);
        const owner = { runId: current.runId, fence: current.fence, revision: current.revision };
        const racers = Array.from({ length: 8 }, (_, index) => new Promise((done) => {
            const child = spawn(python, [launcherOf(checkout.root), 'commit-record', recordFile(checkout.key), owner.runId, checkout.key, String(owner.fence), String(owner.revision)], { stdio: ['pipe', 'ignore', 'ignore'] });
            child.stdin.end(JSON.stringify({ ...current, observations: [...current.observations, `racer ${index}`] }));
            child.once('close', (code) => done({ index, code }));
        }));
        const outcomes = await Promise.all(racers);
        const winners = outcomes.filter((outcome) => outcome.code === 0);
        assert.equal(winners.length, 1, JSON.stringify(outcomes));
        assert.ok(outcomes.filter((outcome) => outcome.code !== 0).every((outcome) => outcome.code === 6), 'every loser saw a stale revision');
        const final = readRecord(checkout.key);
        assert.equal(final.revision, owner.revision + 1);
        assert.deepEqual(final.observations.filter((line) => line.startsWith('racer')), [`racer ${winners[0].index}`]);
    });

    test('the record lock serializes validation and replacement: a commit waits for the holder, then lands', async () => {
        const checkout = fakeCheckout('serial');
        assert.equal(coordinate(checkout, ['run', '--', '/bin/echo', 'a']).status, EXIT.ok);
        const current = readRecord(checkout.key);
        const lockFile = join(registry, 'runs', `.${checkout.key}.json.lock`);
        const holder = spawn(python, ['-I', '-c', 'import fcntl,os,sys,time\nfd=os.open(sys.argv[1],os.O_RDWR)\nfcntl.flock(fd,fcntl.LOCK_EX)\nprint("held",flush=True)\ntime.sleep(1.5)', lockFile], { stdio: ['ignore', 'pipe', 'inherit'] });
        await new Promise((ready) => holder.stdout.once('data', ready));
        const started = Date.now();
        const result = commit(checkout, { runId: current.runId, fence: current.fence, revision: current.revision }, { ...current, observations: [...current.observations, 'after the holder'] });
        const waited = Date.now() - started;
        await settle(holder);
        assert.equal(result.status, 0, result.stderr);
        assert.ok(waited >= 1000, `the commit waited for the lock holder (${waited} ms)`);
        assert.ok(readRecord(checkout.key).observations.includes('after the holder'));
    });
});

describe('P1-2: the preview stays available while any worker of the runner\'s group lives', () => {
    test('runner gone, preview and an active worker alive: recovery keeps the preview; after the worker exits it closes the preview and completes', async () => {
        const checkout = fakeCheckout('pvworker');
        const site = mkdtempSync(join(tmpdir(), 'codemastery-stub-site-'));
        writeFileSync(join(site, 'stub-vite.mjs'), STUB_VITE);
        writeFileSync(join(site, 'package.json'), '{"name":"stub-frontend","type":"module"}');
        const port = await new Promise((done) => { const probe = createHttpServer().listen(0, '127.0.0.1', () => { const { port: chosen } = probe.address(); probe.close(() => done(chosen)); }); });
        const url = `http://127.0.0.1:${port}/`;
        const gate = join(checkout.root, 'gate'), workerGate = join(checkout.root, 'worker-gate'), ready = join(checkout.root, 'ready.json'), hits = join(checkout.root, 'hits');
        // The runner records its preview exactly as global setup does, starts a worker that keeps
        // requesting preview content (both in the runner's own process group), then dies without teardown.
        const runner = `import { spawn } from 'node:child_process';
            import { existsSync, writeFileSync } from 'node:fs';
            import { join } from 'node:path';
            const { updateInventory } = await import(${JSON.stringify(inventoryUrl)});
            const { processIdentity } = await import(${JSON.stringify(hostIdentityUrl)});
            const file = process.env.CODEMASTERY_E2E_INVENTORY, run = process.env.CODEMASTERY_E2E_RUN_ID;
            const control = join(process.env.CODEMASTERY_E2E_SOCKETS, run + '.sock');
            updateInventory(file, (r) => { r.processes.preview = { pid: null, birth: null, role: 'vite-preview', state: 'starting', control }; });
            const preview = spawn(process.execPath, [${JSON.stringify(join(here, 'preview-server.mjs'))}, '--root', ${JSON.stringify(site)}, '--outDir', ${JSON.stringify(join(site, 'dist'))},
                '--host', '127.0.0.1', '--port', '${port}', '--strictPort', '--control', control, '--run', run],
                { cwd: ${JSON.stringify(site)}, env: { ...process.env, E2E_VITE_MODULE: ${JSON.stringify(join(site, 'stub-vite.mjs'))} }, stdio: 'ignore' });
            const identity = processIdentity(preview.pid);
            updateInventory(file, (r) => { Object.assign(r.processes.preview, { pid: preview.pid, birth: identity.birth, state: 'live' }); });
            for (let i = 0; i < 200; i++) { try { if ((await fetch(${JSON.stringify(url)})).ok) break; } catch {} await new Promise((r) => setTimeout(r, 50)); }
            const worker = spawn(process.execPath, ['-e', 'const fs=require("node:fs");(async()=>{for(;;){if(fs.existsSync(${JSON.stringify(workerGate)}))process.exit(0);try{const r=await fetch(${JSON.stringify(url)});if(r.ok&&(await r.text()).includes("stub preview"))fs.appendFileSync(${JSON.stringify(hits)},".")}catch{}await new Promise(r=>setTimeout(r,100))}})()'], { stdio: 'ignore' });
            preview.unref(); worker.unref();
            writeFileSync(${JSON.stringify(ready)}, JSON.stringify({ preview: preview.pid, worker: worker.pid }));
            ${gateLoop(gate)}`;
        const coordinator = spawn(python, [launcherOf(checkout.root), 'run', '--', process.execPath, '--input-type=module', '-e', runner],
            { env: env(), cwd: checkout.root, stdio: 'ignore' });
        let pids = null;
        try {
            pids = JSON.parse(await until('the preview and worker', () => existsSync(ready) && readFileSync(ready, 'utf8')));
            const runnerPid = readRecord(checkout.key).processes.runner.pid;
            coordinator.kill('SIGKILL');
            await settle(coordinator);
            open(gate);
            await until('the runner exit', () => !alive3(runnerPid));
            await until('the worker to be served', () => existsSync(hits) && readFileSync(hits, 'utf8').length >= 2);

            const recover = coordinate(checkout, ['recover']);
            assert.equal(recover.status, EXIT.fail, recover.stdout + recover.stderr);
            assert.match(recover.stdout, new RegExp(`process group ${runnerPid} still has 1 live process\\(es\\) besides the verified preview`));
            assert.match(recover.stdout, new RegExp(`preview ${pids.preview} is left running`));
            assert.ok(!/acknowledged the private control channel/.test(recover.stdout), 'the preview was not asked to close');
            assert.ok(alive3(pids.preview), 'the preview still runs');
            const served = readFileSync(hits, 'utf8').length;
            await until('the worker to keep being served after recovery', () => readFileSync(hits, 'utf8').length > served + 2, 10_000);
            assert.equal(readRecord(checkout.key).state, 'incomplete');

            open(workerGate);
            await until('the worker exit', () => !alive3(pids.worker));
            const done = coordinate(checkout, ['recover']);
            assert.equal(done.status, EXIT.ok, done.stdout + done.stderr);
            assert.match(done.stdout, new RegExp(`preview ${pids.preview} acknowledged the private control channel`));
            assert.match(done.stdout, new RegExp(`the runner's process group ${runnerPid} has no other live member`));
            await until('the preview exit', () => !alive3(pids.preview), 10_000);
            assert.equal(readRecord(checkout.key).state, 'complete');
        } finally {
            open(gate);
            open(workerGate);
            if (coordinator.exitCode === null && coordinator.signalCode === null) coordinator.kill('SIGKILL');
            for (const pid of [pids?.preview, pids?.worker]) if (pid && alive3(pid)) process.kill(pid, 'SIGKILL');
            rmSync(site, { recursive: true, force: true });
        }
    });
});

describe('P1-3: uncertain process-group state never authorizes cleanup or completion', () => {
    test('runner exited with no recorded group identity while a worker lives: recovery stays incomplete', async () => {
        const checkout = fakeCheckout('nogroup');
        const host = JSON.parse(spawnSync(python, [launcher, 'host-identity'], { encoding: 'utf8' }).stdout);
        const info = lstatSync(checkout.root);
        const worker = spawn(process.execPath, ['-e', 'setInterval(() => {}, 1000)'], { stdio: 'ignore' });
        try {
            writeFileSync(recordFile(checkout.key), JSON.stringify({
                schema: 'codemastery-e2e-inventory/1', contract: 'CT34-FIXTURES-2026-11-10-r2', runId: 'cm-e2e-nogroup', state: 'active',
                ownerUid: process.getuid(), lockKey: checkout.key, fence: 1, revision: 1, checkout: { path: checkout.root, dev: info.dev, ino: info.ino },
                host: { hostname: host.hostname, machine: host.machine, boot: host.boot, pidNamespace: host.pidNamespace, system: host.system },
                outputs: [], docker: null, temp: null, intents: [], observations: [],
                processes: { runner: { pid: goneRunner, birth: 'x-starttime:1', role: 'playwright-runner', state: 'live', spawned: true } },
            }), { mode: 0o600 });
            const recover = coordinate(checkout, ['recover']);
            assert.equal(recover.status, EXIT.fail, recover.stdout + recover.stderr);
            assert.match(recover.stdout, /no verifiable process-group identity was recorded for the runner/);
            assert.equal(readRecord(checkout.key).state, 'incomplete');
            assert.equal(coordinate(checkout, ['run', '--', '/bin/echo', 'no']).status, EXIT.blocked);
        } finally {
            worker.kill('SIGKILL');
        }
    });

    const enumerate = (body) => {
        // Drives the coordinator's Linux /proc enumeration with injected per-process outcomes, on any host.
        const script = `import builtins, io, json, sys
sys.path.insert(0, sys.argv[1]); import lifecycle as l
l.platform.system = lambda: "Linux"
outcomes = json.loads(sys.argv[2])
l.os.listdir = lambda p: list(outcomes)
real_open = builtins.open
def fake_open(path, *a, **k):
    for pid, outcome in outcomes.items():
        if path == "/proc/%s/stat" % pid:
            if outcome == "member": return io.BytesIO(("%s (node) S 1 4242 4242 " % pid + "0 " * 15 + "777 0 0").encode())
            if outcome == "other": return io.BytesIO(("%s (sh) S 1 9 9 " % pid + "0 " * 15 + "777 0 0").encode())
            if outcome == "truncated": return io.BytesIO(("%s (x) S 1 4242" % pid).encode())
            raise {"EACCES": PermissionError(13, "Permission denied"), "EPERM": PermissionError(1, "Operation not permitted"),
                   "ENOENT": FileNotFoundError(2, "No such file"), "ESRCH": ProcessLookupError(3, "No such process"),
                   "EIO": OSError(5, "Input/output error")}[outcome]
    return real_open(path, *a, **k)
builtins.open = fake_open
print(json.dumps(l.group_members(4242)))`;
        const result = spawnSync(python, ['-I', '-c', script, here, JSON.stringify(body)], { encoding: 'utf8' });
        assert.equal(result.status, 0, result.stderr);
        return JSON.parse(result.stdout);
    };
    test('Linux enumeration: EACCES, EPERM, EIO or a truncated entry make the answer unreliable, never an empty group', () => {
        for (const failure of ['EACCES', 'EPERM', 'EIO', 'truncated']) {
            const answer = enumerate({ 101: 'member', 102: failure, 103: 'other' });
            assert.equal(answer.reliable, false, `${failure}: ${JSON.stringify(answer)}`);
            assert.deepEqual(answer.members, []);
        }
    });
    test('Linux enumeration: only a process verified gone (ENOENT, ESRCH) is skipped; members are reported with their birth', () => {
        const answer = enumerate({ 101: 'member', 102: 'ENOENT', 103: 'ESRCH', 104: 'other' });
        assert.equal(answer.reliable, true, JSON.stringify(answer));
        assert.deepEqual(answer.members, [{ pid: 101, birth: 'linux-starttime:777' }]);
    });
    test('a verified empty group still lets a normal run complete', () => {
        const checkout = fakeCheckout('emptygroup');
        assert.equal(coordinate(checkout, ['run', '--', '/bin/echo', 'done']).status, EXIT.ok);
        const record = readRecord(checkout.key);
        assert.equal(record.state, 'complete');
        assert.equal(record.processes.runner.groupState, 'empty');
        assert.equal(coordinate(checkout, ['recover']).status, EXIT.ok, 'repeated recovery of a complete run stays complete');
        assert.equal(readRecord(checkout.key).state, 'complete');
    });
});
