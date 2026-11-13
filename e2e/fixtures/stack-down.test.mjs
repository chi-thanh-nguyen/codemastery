// Regression tests for the `stack:down` command line (fixtures/stack-down.mjs) under ADR008
// CT34-FIXTURES-2026-11-10-r2. They run the real script against a stateful stub `docker` placed
// first on PATH, so no Docker resource is listed, inspected or removed. The removal protocol itself
// is covered by stack-recovery.test.mjs; these tests pin the command's contract: it refuses to run
// without the coordinator's exclusivity, reports a complete recovery with exit 0, an incomplete one
// with exit 1, and no longer removes resources on the strength of a dead-PID label.
// Run from e2e/: node --test fixtures/*.test.mjs
import { after, before, test } from 'node:test';
import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { chmodSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { hostIdentity } from './host-identity.mjs';
import { readInventory, writeInventory } from './run-inventory.mjs';

const script = join(dirname(fileURLToPath(import.meta.url)), 'stack-down.mjs');
// Stateful stub docker: resources live in STUB_STATE (JSON); every call is appended to STUB_CALLS.
const STUB = `#!/usr/bin/env node
const fs = require('node:fs');
const args = process.argv.slice(2), file = process.env.STUB_STATE, state = JSON.parse(fs.readFileSync(file, 'utf8'));
fs.appendFileSync(process.env.STUB_CALLS, args.join(' ') + '\\n');
const save = () => fs.writeFileSync(file, JSON.stringify(state));
const fail = (message) => { process.stderr.write(message + '\\n'); process.exit(1); };
const id = args.at(-1), item = state.resources[id];
if (args[0] === 'context' && args[1] === 'show') console.log('stub-context');
else if (args[0] === 'context' && args[1] === 'inspect') console.log('unix:///var/run/docker.sock');
else if (args[0] === 'info') console.log(state.daemonId);
else if (args[0] === 'ps' || (args[1] === 'ls')) {
    const kind = args[0] === 'ps' ? 'container' : args[0];
    const wanted = args.filter((arg, at) => args[at - 1] === '--filter' && arg.startsWith('label=')).map((arg) => arg.slice(6).split('='));
    console.log(Object.entries(state.resources).filter(([, r]) => r.kind === kind && r.present
        && wanted.every(([key, value]) => r.labels && r.labels[key] === value)).map(([k]) => k).join('\\n'));
} else if (args[0] === 'inspect' || args[1] === 'inspect') {
    if (!item || !item.present) fail(args[0] === 'network' ? 'Error response from daemon: network ' + id + ' not found' : 'Error response from daemon: No such container: ' + id);
    console.log(args[0] === 'inspect' ? JSON.stringify({ Id: id, Config: { Labels: item.labels }, Mounts: [], State: { Status: 'exited' } }) : JSON.stringify(item.labels));
} else if (args[0] === 'rm' || args[1] === 'rm') {
    if (!item || !item.present) fail('Error response from daemon: No such container: ' + id);
    if (item.refuse) fail('Error response from daemon: permission denied');
    item.present = false; save(); console.log(id);
} else fail('unexpected docker ' + args.join(' '));
`;

let dir = '';
before(() => {
    dir = mkdtempSync(join(tmpdir(), 'codemastery-stack-down-test-'));
    writeFileSync(join(dir, 'docker'), STUB);
    chmodSync(join(dir, 'docker'), 0o755);
});
after(() => rmSync(dir, { recursive: true, force: true }));

const host = hostIdentity();
const labels = (run) => ({ 'codemastery.e2e': 'disposable', 'codemastery.e2e.run': run, 'codemastery.e2e.owner': String(process.getuid()) });
function stackDown({ resources = {}, intents = [], daemonId = 'D1', recorded = true, inventory = true, supervisor = String(process.pid) }) {
    const stateFile = join(dir, 'state.json'), calls = join(dir, 'calls.log'), record = join(dir, 'run.json');
    writeFileSync(stateFile, JSON.stringify({ daemonId, resources }));
    writeFileSync(calls, '');
    rmSync(record, { force: true });
    if (recorded) {
        writeInventory(record, {
            schema: 'codemastery-e2e-inventory/1', contract: 'CT34-FIXTURES-2026-11-10-r2', runId: 'cm-e2e-cli', state: 'incomplete',
            ownerUid: process.getuid(), lockKey: 'k-cli', host: { hostname: host.hostname, boot: host.boot, pidNamespace: host.pidNamespace, system: host.system },
            processes: {}, temp: null, outputs: [], observations: [], intents,
            docker: { context: 'stub-context', endpoint: 'unix:///var/run/docker.sock', daemonId: 'D1' },
        });
    }
    const env = { ...process.env, PATH: `${dir}:${process.env.PATH}`, STUB_STATE: stateFile, STUB_CALLS: calls };
    delete env.CODEMASTERY_E2E_REGISTRY;
    if (inventory) env.CODEMASTERY_E2E_INVENTORY = record; else delete env.CODEMASTERY_E2E_INVENTORY;
    // This test process plays the coordinator that starts stack-down.mjs directly.
    if (supervisor) env.CODEMASTERY_E2E_SUPERVISOR = supervisor; else delete env.CODEMASTERY_E2E_SUPERVISOR;
    // The rest of the hand-down the coordinator gives stack-down.mjs.
    Object.assign(env, { CODEMASTERY_E2E_RUN_ID: 'cm-e2e-cli', CODEMASTERY_E2E_LOCK_KEY: 'k-cli' });
    const result = spawnSync(process.execPath, [script, '--reason', 'test'], { encoding: 'utf8', env, timeout: 60_000 });
    return { ...result, calls: readFileSync(calls, 'utf8').trim().split('\n').filter(Boolean), record: recorded ? readInventory(record) : null };
}

test('refuses to run outside the lifecycle coordinator and touches no Docker resource', () => {
    const result = stackDown({ inventory: false, resources: { C1: { kind: 'container', present: true, labels: labels('cm-e2e-cli') } } });
    assert.equal(result.status, 2);
    assert.match(result.stderr, /must run through the lifecycle coordinator/);
    assert.deepEqual(result.calls, []);
});

test('refuses exported lifecycle variables that do not name this process\' coordinator', () => {
    const result = stackDown({ supervisor: '1', resources: { C1: { kind: 'container', present: true, labels: labels('cm-e2e-cli') } } });
    assert.equal(result.status, 2);
    assert.match(result.stderr, /must run through the lifecycle coordinator/);
    assert.deepEqual(result.calls, []);
});

test('removes the recorded run\'s resources and exits 0 only when every one is verified absent', () => {
    const result = stackDown({
        resources: { C1: { kind: 'container', present: true, labels: labels('cm-e2e-cli') }, N1: { kind: 'network', present: true, labels: labels('cm-e2e-cli') } },
        intents: [{ id: 'i1', kind: 'container', name: 'c', dockerId: 'C1', state: 'created', volumes: 'recorded' }, { id: 'i2', kind: 'network', name: 'n', dockerId: 'N1', state: 'created' }],
    });
    assert.equal(result.status, 0, result.stdout + result.stderr);
    assert.match(result.stdout, /cm-e2e-cli — 2 removed; recovery complete/);
    assert.equal(result.record.state, 'complete');
});

test('a refused removal is reported and exits 1 with the inventory retained for retry', () => {
    const result = stackDown({
        resources: { C1: { kind: 'container', present: true, refuse: true, labels: labels('cm-e2e-cli') } },
        intents: [{ id: 'i1', kind: 'container', name: 'c', dockerId: 'C1', state: 'created' }],
    });
    assert.equal(result.status, 1);
    assert.match(result.stdout, /RECOVERY INCOMPLETE \(inventory retained for retry\)/);
    assert.equal(result.record.state, 'incomplete');
});

test('a legacy resource labelled only with a dead owner PID is reported, never removed, and not this run\'s failure', () => {
    const deadPid = String(spawnSync(process.execPath, ['-e', '']).pid);
    const result = stackDown({
        resources: { OLD: { kind: 'container', present: true, labels: { 'codemastery.e2e': 'disposable', 'codemastery.e2e.pid': deadPid } } },
    });
    assert.equal(result.status, 0, 'a resource of no inventory does not block this checkout');
    assert.ok(!result.calls.some((call) => call.startsWith('rm ')), result.calls.join('\n'));
    assert.match(result.stdout, /left container OLD untouched: it carries no run label \(a pre-r2 leftover\)/);
});

test('a daemon that changed since the run started preserves everything', () => {
    const result = stackDown({
        daemonId: 'D2',
        resources: { C1: { kind: 'container', present: true, labels: labels('cm-e2e-cli') } },
        intents: [{ id: 'i1', kind: 'container', name: 'c', dockerId: 'C1', state: 'created' }],
    });
    assert.equal(result.status, 1);
    assert.ok(!result.calls.some((call) => call.startsWith('rm ')));
    assert.match(result.stdout, /the daemon ID is now D2, not the recorded D1/);
});
