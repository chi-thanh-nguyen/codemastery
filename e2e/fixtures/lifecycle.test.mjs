// Regression tests for the pre-entry lifecycle coordinator of ADR008 CT34-FIXTURES-2026-11-10-r2
// (Section 10.2 and the Section 10.4 acceptance cases): exclusive ownership of the canonical
// checkout and of the three output roots is taken, and the run record persisted, *before* the
// runner is started; path aliases contend for the same lock; independently isolated checkouts run
// concurrently; a loser exits nonzero without touching outputs, resources or the active record;
// and an unresolved obligation blocks reuse until recovery verifies completion.
//
// Each test drives the real coordinator against a disposable fake checkout (its own directory, with
// `fixtures` symlinked to this one and its own `test-results` roots), so the real lock keys and
// records are never involved. The runner command is a plain Node process, so no Playwright, Docker,
// Vite or browser is touched. The registry is deliberately the real per-account one: the coordinator
// resolves `~` from the effective UID and must ignore a HOME override, which is asserted below.
import { after, before, describe, test } from 'node:test';
import assert from 'node:assert/strict';
import { spawn, spawnSync } from 'node:child_process';
import { existsSync, lstatSync, mkdirSync, mkdtempSync, readFileSync, realpathSync, rmSync, symlinkSync, writeFileSync } from 'node:fs';
import { homedir, tmpdir } from 'node:os';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const launcher = join(here, 'lifecycle.py');
const python = process.env.E2E_PYTHON || 'python3';
const registry = join(realpathSync(homedir()), '.codemastery-e2e');
const EXIT = { ok: 0, fail: 1, conflict: 2, blocked: 3, unqualified: 4 };
const made = [];

/** A disposable fake checkout: its own output roots, and `fixtures` shared with this directory. */
function fakeCheckout(name = 'co') {
    const root = realpathSync(mkdtempSync(join(tmpdir(), `codemastery-${name}-`)));
    mkdirSync(join(root, 'test-results', 'artifacts'), { recursive: true });
    mkdirSync(join(root, 'test-results', 'html'), { recursive: true });
    writeFileSync(join(root, 'test-results', 'results.json'), '{"sentinel":true}');
    writeFileSync(join(root, 'test-results', 'artifacts', 'sentinel.txt'), 'SENTINEL');
    symlinkSync(here, join(root, 'fixtures'));
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

const env = (checkout, extra = {}) => ({ ...process.env, CODEMASTERY_E2E_CHECKOUT: checkout.root, ...extra });
/** Runs the coordinator to completion. */
const coordinate = (checkout, args, extra = {}) =>
    spawnSync(python, [launcher, ...args], { encoding: 'utf8', timeout: 120_000, env: env(checkout, extra), cwd: checkout.root });
/** Starts a coordinator whose runner blocks until `gate` exists, so exclusivity can be observed. */
function startGated(checkout, gate) {
    const waiter = 'const fs=require("node:fs");const g=process.argv[1];const t=Date.now();'
        + '(function w(){if(fs.existsSync(g)||Date.now()-t>60000)process.exit(0);setTimeout(w,40);})();';
    return spawn(python, [launcher, 'run', '--', process.execPath, '-e', waiter, gate],
        { encoding: 'utf8', env: env(checkout), cwd: checkout.root, stdio: ['ignore', 'pipe', 'pipe'] });
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
        rmSync(entry.root, { recursive: true, force: true });
    }
});

describe('ownership is taken and persisted before the runner is entered', () => {
    test('the admitted runner already sees an active record naming its own run and output roots', () => {
        const checkout = fakeCheckout('admit');
        const probe = 'const fs=require("node:fs");const r=JSON.parse(fs.readFileSync(process.env.CODEMASTERY_E2E_INVENTORY,"utf8"));'
            + 'console.log(JSON.stringify({state:r.state,runId:r.runId,outputs:r.outputs.length,token:process.env.CODEMASTERY_E2E_OWNERSHIP,same:r.runId===process.env.CODEMASTERY_E2E_RUN_ID}));';
        const result = coordinate(checkout, ['run', '--', process.execPath, '-e', probe]);
        assert.equal(result.status, EXIT.ok, result.stderr);
        const seen = JSON.parse(result.stdout.split('\n').find((line) => line.startsWith('{')));
        assert.equal(seen.state, 'active', 'the record is already persisted when the runner starts');
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
            const loser = spawnSync(python, [launcher, 'run', '--', '/bin/echo', 'aliased'],
                { encoding: 'utf8', env: { ...process.env, CODEMASTERY_E2E_CHECKOUT: aliasRoot }, cwd: aliasRoot });
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

describe('an unresolved ownership obligation blocks reuse until recovery completes', () => {
    /** Writes a record by hand, as a run killed before its teardown would leave behind. */
    const leaveRecord = (checkout, patch) => {
        const record = {
            schema: 'codemastery-e2e-inventory/1', contract: 'CT34-FIXTURES-2026-11-10-r2',
            runId: 'cm-e2e-orphan', state: 'active', ownerUid: process.getuid(), lockKey: checkout.key,
            host: { hostname: 'somewhere-else', boot: 'boot:0', pidNamespace: null, system: 'Linux' },
            checkout: { path: checkout.root, dev: 1, ino: 2 }, outputs: [], processes: {}, docker: null,
            temp: null, intents: [], observations: [], ...patch,
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
        writeFileSync(recordFile(other.key), JSON.stringify({
            schema: 'codemastery-e2e-inventory/1', runId: 'cm-e2e-claimant', state: 'active',
            ownerUid: process.getuid(), intents: [],
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
