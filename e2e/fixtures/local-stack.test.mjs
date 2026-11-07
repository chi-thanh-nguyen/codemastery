// Regression tests for the startup lifecycle in local-stack.ts: frontend readiness must identify this run's build, and a
// backend that fails to start must fail promptly with its diagnostics captured before teardown. Docker and npx are
// stubs placed first on PATH, so no real container, network, image or Vite process is touched.
// Run from e2e/ (Node 22.18+ or 24, which load the TypeScript fixture natively): node --test fixtures/*.test.mjs
import { after, before, beforeEach, describe, test } from 'node:test';
import assert from 'node:assert/strict';
import { spawn } from 'node:child_process';
import { once } from 'node:events';
import { chmodSync, existsSync, mkdtempSync, readdirSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { createServer } from 'node:http';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import globalSetup, { buildMarker, containerExit, markBuild, waitForBackend, waitForPreview } from './local-stack.ts';

// Stub docker: state comes from STUB_MODE; every call is appended to STUB_DIR/calls.log. `run` remembers the JWT
// secret it receives through the environment, and `logs` prints it back, so redaction can be checked.
const DOCKER = `#!/usr/bin/env node
const fs = require('node:fs'), path = require('node:path');
const dir = process.env.STUB_DIR, mode = process.env.STUB_MODE, args = process.argv.slice(2);
fs.appendFileSync(path.join(dir, 'calls.log'), args.join(' ') + '\\n');
const fail = (message) => { process.stderr.write(message + '\\n'); process.exit(1); };
const counter = path.join(dir, 'inspects'), inspects = (fs.existsSync(counter) ? Number(fs.readFileSync(counter, 'utf8')) : 0) + 1;
switch (args[0]) {
    case 'network': case 'rm': console.log('ok'); break;
    case 'exec': break;
    case 'run': if (process.env.JWT_SECRET) fs.writeFileSync(path.join(dir, 'secret'), process.env.JWT_SECRET); console.log('stub-container-id'); break;
    case 'inspect':
        fs.writeFileSync(counter, String(inspects));
        if (mode === 'missing') fail('Error: No such object: ' + args.at(-1));
        if (mode === 'inspect-fails' || (mode === 'diagnostics-fail' && inspects > 1)) fail('Error response from daemon: i/o timeout');
        console.log(mode === 'running'
            ? 'state=running exit=0 oom=false error="" started=2026-11-07T00:00:00Z finished=0001-01-01T00:00:00Z'
            : 'state=exited exit=3 oom=false error="" started=2026-11-07T00:00:00Z finished=2026-11-07T00:00:04Z');
        break;
    case 'logs': {
        if (mode === 'diagnostics-fail') fail('Error response from daemon: connection refused');
        const secret = fs.existsSync(path.join(dir, 'secret')) ? fs.readFileSync(path.join(dir, 'secret'), 'utf8') : 'none';
        console.log('Flyway migration failed: relation already exists');
        console.log('jwt secret in a log line: ' + secret);
        process.stderr.write('APPLICATION FAILED TO START\\n');
        break;
    }
    default: fail('unexpected docker ' + args.join(' '));
}
`;
// Stub npx: \`vite build\` writes a minimal index.html; \`vite preview\` serves it until SIGTERM.
const NPX = `#!/usr/bin/env node
const fs = require('node:fs'), http = require('node:http'), path = require('node:path');
const args = process.argv.slice(2), value = (name) => args[args.indexOf(name) + 1];
if (args[1] === 'build') {
    fs.mkdirSync(value('--outDir'), { recursive: true });
    fs.writeFileSync(path.join(value('--outDir'), 'index.html'), '<!doctype html><html><head><title>CodeMastery</title></head><body></body></html>');
} else if (args[1] === 'preview') {
    const server = http.createServer((_, res) => res.end(fs.readFileSync(path.join(value('--outDir'), 'index.html')))).listen(Number(value('--port')), value('--host'));
    process.on('SIGTERM', () => { fs.writeFileSync(path.join(process.env.STUB_DIR, 'preview-stopped'), ''); server.close(); process.exit(0); });
}
`;

let stub = '';
const saved = { ...process.env };
const calls = () => readFileSync(join(stub, 'calls.log'), 'utf8').trim().split('\n').filter(Boolean);
before(() => {
    stub = mkdtempSync(join(tmpdir(), 'codemastery-local-stack-test-'));
    for (const [name, body] of [['docker', DOCKER], ['npx', NPX]]) { writeFileSync(join(stub, name), body); chmodSync(join(stub, name), 0o755); }
    process.env.PATH = `${stub}:${saved.PATH}`; process.env.STUB_DIR = stub;
});
beforeEach(() => { writeFileSync(join(stub, 'calls.log'), ''); rmSync(join(stub, 'inspects'), { force: true }); rmSync(join(stub, 'preview-stopped'), { force: true }); });
after(() => { process.env = saved; rmSync(stub, { recursive: true, force: true }); });

/** An HTTP server on a free loopback port; `handler` answers every request. */
async function serve(handler) {
    const server = createServer(handler).listen(0, '127.0.0.1');
    await once(server, 'listening');
    return { url: `http://127.0.0.1:${server.address().port}/`, close: () => { server.closeAllConnections(); server.close(); } };
}
const sleeper = () => spawn(process.execPath, ['-e', 'setInterval(() => {}, 1000)'], { stdio: ['ignore', 'ignore', 'pipe'] });
const stopped = async (child) => { if (child.exitCode === null && child.signalCode === null) { child.kill(); await once(child, 'exit'); } };
const fast = { timeoutMs: 1500, probeTimeoutMs: 300, intervalMs: 100 };

describe('frontend readiness identifies this run\'s build', () => {
    const token = 'a'.repeat(32);
    test('marks the temporary build and accepts the server that serves it', async () => {
        const dist = mkdtempSync(join(tmpdir(), 'codemastery-local-stack-dist-'));
        try {
            writeFileSync(join(dist, 'index.html'), '<!doctype html><html><head><title>CodeMastery</title></head><body></body></html>');
            await markBuild(dist, token);
            const html = readFileSync(join(dist, 'index.html'), 'utf8');
            assert.ok(html.includes(`${buildMarker(token)}</head>`), html);
            const server = await serve((_, res) => res.end(html)), child = sleeper();
            try { await waitForPreview(child, server.url, token, () => '', fast); } finally { server.close(); await stopped(child); }
        } finally { rmSync(dist, { recursive: true, force: true }); }
    });
    for (const [name, body] of [
        ['a foreign server answering 200 without the marker', '<!doctype html><html><head><title>CodeMastery</title></head><body></body></html>'],
        ['a foreign server answering unrelated HTML', '<html><body>It works!</body></html>'],
        ['another run\'s build (a different token)', `<html><head>${buildMarker('b'.repeat(32))}</head></html>`],
    ]) {
        test(`is NOT ready for ${name}`, async () => {
            const server = await serve((_, res) => res.end(body)), child = sleeper();
            try { await assert.rejects(waitForPreview(child, server.url, token, () => '', fast), /Timed out waiting for frontend preview \(no response carried this run's build marker\)/); }
            finally { server.close(); await stopped(child); }
        });
    }
    test('fails promptly, with Vite\'s message, when the preview process exits before it is ready', async () => {
        const server = await serve((_, res) => res.end('FOREIGN'));
        const child = spawn(process.execPath, ['-e', 'console.error("error when starting preview server: Port is already in use"); process.exit(7)'], { stdio: ['ignore', 'ignore', 'pipe'] });
        let stderr = ''; child.stderr.on('data', (chunk) => { stderr += chunk; });
        const started = Date.now();
        try {
            await assert.rejects(waitForPreview(child, server.url, token, () => stderr, { ...fast, timeoutMs: 30_000 }), /vite preview exited \(7\): error when starting preview server: Port is already in use/);
            assert.ok(Date.now() - started < 10_000, 'well before the readiness timeout');
        } finally { server.close(); }
    });
    test('does not report ready when the preview process exits while the probe is answered', async () => {
        const child = sleeper();
        const server = await serve(async (_, res) => { child.kill(); await once(child, 'exit'); res.end(`<html><head>${buildMarker(token)}</head></html>`); });
        try { await assert.rejects(waitForPreview(child, server.url, token, () => '', fast), /vite preview exited/); } finally { server.close(); }
    });
    test('bounds every probe: a server that accepts but never answers cannot stall readiness', async () => {
        const server = await serve(() => { /* never answers */ }), child = sleeper(), started = Date.now();
        try {
            await assert.rejects(waitForPreview(child, server.url, token, () => '', { timeoutMs: 500, probeTimeoutMs: 200, intervalMs: 100 }), /Timed out/);
            assert.ok(Date.now() - started < 3000, `took ${Date.now() - started} ms`);
        } finally { server.close(); await stopped(child); }
    });
});

describe('backend readiness detects a failed start and keeps its diagnostics', () => {
    const unused = async () => { const server = await serve(() => undefined); server.close(); return server.url.replace(/\/$/, ''); };
    test('a transient inspection error is not an exit; a missing container is', async () => {
        process.env.STUB_MODE = 'inspect-fails'; assert.equal(await containerExit('cm-x-backend'), null);
        process.env.STUB_MODE = 'running'; assert.equal(await containerExit('cm-x-backend'), null);
        process.env.STUB_MODE = 'missing'; assert.equal(await containerExit('cm-x-backend'), 'container cm-x-backend no longer exists');
        process.env.STUB_MODE = 'exited'; assert.match(await containerExit('cm-x-backend'), /^the container stopped \(state=exited exit=3 /);
    });
    test('a backend that keeps running but never becomes healthy times out with its state and logs', async () => {
        process.env.STUB_MODE = 'running';
        await assert.rejects(waitForBackend(await unused(), 'cm-x-backend', ['not-in-the-logs'], { timeoutMs: 800, intervalMs: 100 }), (error) => {
            assert.match(error.message, /^Timed out waiting for backend health\n--- cm-x-backend state: state=running exit=0/);
            assert.match(error.message, /last 100 log lines of cm-x-backend:\nFlyway migration failed/);
            assert.match(error.message, /APPLICATION FAILED TO START/);
            return true;
        });
        assert.ok(calls().includes('logs --tail 100 cm-x-backend'));
    });
    test('a successful start collects no failure diagnostics', async () => {
        process.env.STUB_MODE = 'running';
        const health = await serve((_, res) => res.end('{"status":"UP"}'));
        try { await waitForBackend(health.url.replace(/\/$/, ''), 'cm-x-backend', [], { timeoutMs: 2000, intervalMs: 100 }); } finally { health.close(); }
        assert.ok(!calls().some((call) => call.startsWith('logs')), calls().join('\n'));
    });

    async function setupFailure(mode) {
        process.env.STUB_MODE = mode;
        const temp = mkdtempSync(join(tmpdir(), 'codemastery-local-stack-tmp-'));
        Object.assign(process.env, { TMPDIR: temp, E2E_BACKEND_IMAGE: 'stub-backend', E2E_POSTGRES_IMAGE: 'stub-postgres' });
        delete process.env.E2E_BASE_URL;
        const started = Date.now();
        try {
            const error = await globalSetup().then(() => null, (reason) => reason);
            return { error, elapsed: Date.now() - started, leftovers: readdirSync(temp) };
        } finally {
            if (saved.TMPDIR === undefined) delete process.env.TMPDIR; else process.env.TMPDIR = saved.TMPDIR;
            rmSync(temp, { recursive: true, force: true });
        }
    }
    const backendName = () => calls().find((call) => call.startsWith('run -d --name ') && call.includes('-backend '))?.split(' ')[3];

    test('global setup fails promptly when the backend exits, keeps its logs (secrets redacted) and then cleans up', async () => {
        const { error, elapsed, leftovers } = await setupFailure('exited');
        assert.ok(error instanceof Error, 'setup must fail');
        assert.ok(elapsed < 20_000, `failed after ${elapsed} ms, not after the 180 s health timeout`);
        assert.match(error.message, /^backend health: the container stopped \(state=exited exit=3 oom=false/);
        assert.match(error.message, /Flyway migration failed: relation already exists/);
        const secret = readFileSync(join(stub, 'secret'), 'utf8');
        assert.ok(secret.length === 64 && !error.message.includes(secret), 'the JWT secret is redacted');
        assert.match(error.message, /jwt secret in a log line: \[redacted\]/);
        const log = calls(), backend = backendName();
        assert.ok(log.indexOf(`logs --tail 100 ${backend}`) >= 0, log.join('\n'));
        assert.ok(log.indexOf(`logs --tail 100 ${backend}`) < log.indexOf(`rm -f -v ${backend}`), 'logs are read before the container is removed');
        assert.ok(log.some((call) => /^rm -f -v cm-e2e-.*-postgres$/.test(call)) && log.some((call) => /^network rm cm-e2e-/.test(call)));
        assert.ok(existsSync(join(stub, 'preview-stopped')), 'the preview process was stopped');
        assert.deepEqual(leftovers, [], 'the temporary build directory was removed');
    });
    test('a diagnostics failure keeps the original error and does not prevent cleanup', async () => {
        const { error, leftovers } = await setupFailure('diagnostics-fail');
        assert.match(error.message, /^backend health: the container stopped \(state=exited exit=3/);
        assert.match(error.message, /state: unavailable \(Error response from daemon: i\/o timeout\)/);
        assert.match(error.message, /logs unavailable: Error response from daemon: connection refused/);
        const log = calls(), backend = backendName();
        assert.ok(log.includes(`rm -f -v ${backend}`) && log.some((call) => call.startsWith('network rm ')), log.join('\n'));
        assert.ok(existsSync(join(stub, 'preview-stopped')));
        assert.deepEqual(leftovers, []);
    });
});
