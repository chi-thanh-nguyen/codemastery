// Regression tests for stack-down.mjs. They run the real script against a stub `docker` placed first on PATH, so no
// Docker resource is listed, inspected or removed. Run from e2e/: node --test fixtures/*.test.mjs
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { chmodSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const script = join(dirname(fileURLToPath(import.meta.url)), 'stack-down.mjs');
// Stub docker: answers from a JSON scenario and appends every call to a log.
const STUB = `#!/usr/bin/env node
const fs = require('node:fs');
const args = process.argv.slice(2), scenario = JSON.parse(fs.readFileSync(process.env.STUB_SCENARIO, 'utf8'));
fs.appendFileSync(process.env.STUB_CALLS, args.join(' ') + '\\n');
const fail = (message) => { process.stderr.write(message + '\\n'); process.exit(1); };
const find = (kind, id) => scenario[kind].find((resource) => resource.id === id);
const respond = (kind, id, step, ok) => {
    const resource = find(kind, id), behavior = resource ? (resource[step] ?? 'ok') : 'missing';
    if (behavior === 'missing') fail(kind === 'containers' ? 'Error: No such container: ' + id : 'Error response from daemon: network ' + id + ' not found');
    if (behavior === 'error') fail('Error response from daemon: permission denied while trying to connect');
    ok(resource);
};
const command = args[0] === 'network' ? 'network ' + args[1] : args[0];
if (command === 'ps') console.log(scenario.containers.map((c) => c.id).join('\\n'));
else if (command === 'network ls') console.log(scenario.networks.map((n) => n.id).join('\\n'));
else if (command === 'inspect') respond('containers', args.at(-1), 'inspect', (c) => console.log(c.pid ?? '<no value>'));
else if (command === 'network inspect') respond('networks', args.at(-1), 'inspect', (n) => console.log(n.pid ?? '<no value>'));
else if (command === 'rm') respond('containers', args.at(-1), 'rm', (c) => console.log(c.id));
else if (command === 'network rm') respond('networks', args.at(-1), 'rm', (n) => console.log(n.id));
else fail('unexpected docker ' + args.join(' '));
`;

const livePid = String(process.pid);
const deadPid = String(spawnSync(process.execPath, ['-e', '']).pid);

function stackDown(scenario) {
    const dir = mkdtempSync(join(tmpdir(), 'codemastery-stack-down-test-'));
    try {
        writeFileSync(join(dir, 'docker'), STUB); chmodSync(join(dir, 'docker'), 0o755);
        writeFileSync(join(dir, 'scenario.json'), JSON.stringify(scenario)); writeFileSync(join(dir, 'calls.log'), '');
        const result = spawnSync(process.execPath, [script], { encoding: 'utf8',
            env: { ...process.env, PATH: `${dir}:${process.env.PATH}`, STUB_SCENARIO: join(dir, 'scenario.json'), STUB_CALLS: join(dir, 'calls.log') } });
        const calls = readFileSync(join(dir, 'calls.log'), 'utf8').trim().split('\n');
        return { status: result.status, stdout: result.stdout, stderr: result.stderr,
            removed: calls.filter((call) => /^(rm -f -v|network rm) /.test(call)).map((call) => call.split(' ').at(-1)) };
    } finally { rmSync(dir, { recursive: true, force: true }); }
}

test('keeps live-owner resources, removes orphans and survives resources that vanish concurrently', () => {
    const result = stackDown({
        containers: [
            { id: 'c-live', pid: livePid },
            { id: 'c-dead', pid: deadPid },
            { id: 'c-unlabelled' },
            // Removed by another run's teardown between `docker ps` and `docker inspect`.
            { id: 'c-vanished', inspect: 'missing' },
            // Removed by another run between `docker inspect` and `docker rm`.
            { id: 'c-gone-before-rm', pid: deadPid, rm: 'missing' },
            { id: 'c-after', pid: deadPid },
        ],
        networks: [{ id: 'n-live', pid: livePid }, { id: 'n-dead', pid: deadPid }, { id: 'n-vanished', inspect: 'missing' }],
    });
    assert.equal(result.status, 0, result.stderr);
    assert.deepEqual(result.removed, ['c-dead', 'c-unlabelled', 'c-gone-before-rm', 'c-after', 'n-dead']);
    assert.match(result.stdout, /Removed 4; kept 2 owned by a running E2E process; 3 already gone; 0 kept with an unreadable owner; 0 failed\./);
});

test('never removes a resource whose owner cannot be read, and reports a genuine removal failure without stopping', () => {
    const result = stackDown({
        containers: [
            { id: 'c-unknown-owner', inspect: 'error' },
            { id: 'c-rm-fails', pid: deadPid, rm: 'error' },
            { id: 'c-next', pid: deadPid },
            { id: 'c-live', pid: livePid },
        ],
        networks: [{ id: 'n-dead', pid: deadPid }],
    });
    assert.equal(result.status, 1, 'unresolved resources make the run fail');
    // No removal is attempted for the unreadable one; the failing removal does not stop the others.
    assert.deepEqual(result.removed, ['c-rm-fails', 'c-next', 'n-dead']);
    assert.match(result.stderr, /kept container c-unknown-owner: its owner could not be read \(Error response from daemon: permission denied/);
    assert.match(result.stderr, /could not remove container c-rm-fails: Error response from daemon: permission denied/);
    assert.match(result.stdout, /Removed 2; kept 1 owned by a running E2E process; 0 already gone; 1 kept with an unreadable owner; 1 failed\./);
});

test('does nothing when no labelled resource exists', () => {
    const result = stackDown({ containers: [], networks: [] });
    assert.equal(result.status, 0, result.stderr);
    assert.deepEqual(result.removed, []);
    assert.match(result.stdout, /Removed 0; kept 0/);
});
