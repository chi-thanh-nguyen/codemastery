// Regression tests for the private cooperative preview control channel of ADR008
// CT34-FIXTURES-2026-11-10-r2 (Section 10.3). A real preview process is started with a stub Vite
// module (no frontend build, no browser), on a real run-scoped Unix-domain socket, and is asked to
// close its own server. It must acknowledge its own run and OS birth identity, refuse a stale or
// mismatching request, and stop itself — no PID is ever signalled.
import { after, before, describe, test } from 'node:test';
import assert from 'node:assert/strict';
import { spawn } from 'node:child_process';
import { chmodSync, existsSync, lstatSync, mkdtempSync, rmSync, writeFileSync } from 'node:fs';
import { createServer } from 'node:http';
import { connect, createServer as createSocketServer } from 'node:net';
import { tmpdir } from 'node:os';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { hostIdentity, processIdentity } from './host-identity.mjs';
import { writeInventory } from './run-inventory.mjs';
import { askPreviewToClose, reconcile } from './stack-recovery.mjs';

const here = dirname(fileURLToPath(import.meta.url));
const server = join(here, 'preview-server.mjs');
/** A stub of the pinned Vite preview API: it binds the port and closes on request, like `preview()`. */
const STUB_VITE = `import { createServer } from 'node:http';
export async function preview(options) {
    const http = createServer((_, response) => response.end('<!doctype html><html><head></head><body>stub</body></html>'));
    await new Promise((ready, failed) => { http.once('error', failed); http.listen(options.preview.port, options.preview.host, ready); });
    return { close: () => new Promise((closed) => { http.closeAllConnections(); http.close(closed); }), httpServer: http };
}
`;

let scratch = '';
const started = [];
before(() => {
    scratch = mkdtempSync(join(tmpdir(), 'codemastery-pv-'));
    chmodSync(scratch, 0o700);
    writeFileSync(join(scratch, 'stub-vite.mjs'), STUB_VITE);
    writeFileSync(join(scratch, 'package.json'), '{"name":"stub-frontend","type":"module"}');
});
after(() => {
    // A failed assertion must not leave a preview process keeping the test runner alive.
    for (const child of started) if (child.exitCode === null && child.signalCode === null) child.kill('SIGKILL');
    rmSync(scratch, { recursive: true, force: true });
});

const freePort = () => new Promise((done) => {
    const probe = createServer().listen(0, '127.0.0.1', () => {
        const { port } = probe.address();
        probe.close(() => done(port));
    });
});

/** Starts a real preview process on its own control socket and waits until the channel is ready. */
async function startPreview(runId, socketName = 'p.sock') {
    const control = join(scratch, socketName);
    const port = await freePort();
    const child = spawn(process.execPath, [server, '--root', scratch, '--outDir', join(scratch, 'dist'),
        '--host', '127.0.0.1', '--port', String(port), '--strictPort', '--control', control, '--run', runId],
    { cwd: scratch, env: { ...process.env, E2E_VITE_MODULE: join(scratch, 'stub-vite.mjs') }, stdio: ['ignore', 'pipe', 'pipe'] });
    started.push(child);
    let out = '';
    child.stdout.on('data', (chunk) => { out += chunk; });
    child.stderr.on('data', (chunk) => { out += chunk; });
    const deadline = Date.now() + 20_000;
    while (!out.includes('control channel ready') && child.exitCode === null) {
        if (Date.now() > deadline) throw new Error(`preview did not become ready: ${out}`);
        await new Promise((next) => setTimeout(next, 50));
    }
    if (child.exitCode !== null) throw new Error(`preview exited early (${child.exitCode}): ${out}`);
    return { child, control, port, output: () => out };
}
const exited = (child, timeoutMs = 10_000) => new Promise((done) => {
    if (child.exitCode !== null) { done(child.exitCode); return; }
    const timer = setTimeout(() => done(null), timeoutMs);
    child.once('exit', (code) => { clearTimeout(timer); done(code); });
});
/** One raw request/response exchange over the control socket. */
const ask = (control, request) => new Promise((done) => {
    const socket = connect(control);
    let reply = '';
    socket.setEncoding('utf8');
    socket.on('connect', () => socket.write(`${JSON.stringify(request)}\n`));
    socket.on('data', (chunk) => { reply += chunk; });
    socket.on('error', (error) => done({ error: error.code }));
    socket.on('close', () => { try { done(JSON.parse(reply)); } catch { done({ raw: reply }); } });
});

describe('the preview acknowledges its own identity over its private channel', () => {
    test('the socket is private to this account and reports this run, PID and birth identity', async () => {
        const preview = await startPreview('cm-e2e-ping');
        try {
            assert.equal(lstatSync(preview.control).mode & 0o777, 0o600, 'the socket is not readable by other accounts');
            const pong = await ask(preview.control, { command: 'ping' });
            assert.equal(pong.ok, true);
            assert.equal(pong.runId, 'cm-e2e-ping');
            assert.equal(pong.pid, preview.child.pid);
            assert.equal(pong.birth, processIdentity(preview.child.pid).birth, 'the process reports its own OS birth identity');
        } finally {
            preview.child.kill('SIGKILL');
        }
    });

    test('a request naming another run, another birth identity or an unknown command is refused', async () => {
        const preview = await startPreview('cm-e2e-refuse');
        try {
            const birth = processIdentity(preview.child.pid).birth;
            assert.deepEqual(await ask(preview.control, { command: 'shutdown', runId: 'cm-e2e-elsewhere', birth }),
                { ok: false, reason: 'run-mismatch', runId: 'cm-e2e-refuse' });
            const stale = await ask(preview.control, { command: 'shutdown', runId: 'cm-e2e-refuse', birth: 'x-starttime:1' });
            assert.equal(stale.ok, false);
            assert.equal(stale.reason, 'birth-mismatch');
            assert.equal((await ask(preview.control, { command: 'erase-everything' })).reason, 'unknown-command');
            assert.equal((await ask(preview.control, { bad: 'request' })).reason, 'unknown-command');
            assert.equal(preview.child.exitCode, null, 'a refused request leaves the preview running');
        } finally {
            preview.child.kill('SIGKILL');
        }
    });

    test('an exact request makes the preview close its own server and exit 0', async () => {
        const preview = await startPreview('cm-e2e-close');
        const birth = processIdentity(preview.child.pid).birth;
        const asked = await askPreviewToClose(preview.control, 'cm-e2e-close', birth);
        assert.equal(asked.ok, true, JSON.stringify(asked));
        assert.equal(asked.acknowledged.pid, preview.child.pid);
        assert.equal(await exited(preview.child), 0, preview.output());
        assert.match(preview.output(), /closing \(asked over the private control channel\)/);
        assert.equal(existsSync(preview.control), false, 'the run-scoped socket file is removed with the channel');
        assert.equal(processIdentity(preview.child.pid).exists !== true || processIdentity(preview.child.pid).birth !== birth, true,
            'the recorded birth identity is gone');
    });

    test('an absent or unresponsive channel is reported, not worked around', async () => {
        const missing = await askPreviewToClose(join(scratch, 'not-there.sock'), 'cm-e2e-x', 'x', 1000);
        assert.equal(missing.ok, false);
        assert.match(missing.why, /control channel is unavailable|could not be opened/);
        const mute = createSocketServer(() => { /* accepts and never answers */ });
        const mutePath = join(scratch, 'mute.sock');
        await new Promise((ready) => mute.listen(mutePath, ready));
        try {
            const timedOut = await askPreviewToClose(mutePath, 'cm-e2e-x', 'x', 800);
            assert.equal(timedOut.ok, false);
            assert.match(timedOut.why, /did not answer in time/);
        } finally {
            mute.close();
            rmSync(mutePath, { force: true });
        }
    });

    test('a control path too long for the platform fails closed instead of running unrecoverably', async () => {
        const long = join(scratch, `${'d'.repeat(120)}.sock`);
        const child = spawn(process.execPath, [server, '--root', scratch, '--outDir', join(scratch, 'dist'),
            '--host', '127.0.0.1', '--port', '0', '--control', long, '--run', 'cm-e2e-long'],
        { cwd: scratch, env: { ...process.env, E2E_VITE_MODULE: join(scratch, 'stub-vite.mjs') }, stdio: ['ignore', 'pipe', 'pipe'] });
        let out = '';
        child.stderr.on('data', (chunk) => { out += chunk; });
        assert.equal(await exited(child), 2, out);
        assert.match(out, /control socket path is \d+ bytes, too long/);
    });
});

describe('recovery stops an orphaned preview through that channel alone', () => {
    test('a live orphan is closed cooperatively and the run is then complete', async () => {
        const runId = 'cm-e2e-orphan-live';
        const preview = await startPreview(runId, 'orphan.sock');
        const inventory = join(scratch, 'orphan.json');
        const identity = processIdentity(preview.child.pid);
        writeInventory(inventory, {
            schema: 'codemastery-e2e-inventory/1', contract: 'CT34-FIXTURES-2026-11-10-r2', runId,
            host: (({ hostname, boot, pidNamespace, system }) => ({ hostname, boot, pidNamespace, system }))(hostIdentity()),
            state: 'active', ownerUid: process.getuid(), intents: [], temp: null, docker: null, outputs: [],
            processes: { preview: { pid: preview.child.pid, birth: identity.birth, birthSource: identity.source, role: 'vite-preview', control: preview.control, state: 'live' } },
        });
        const outcome = await reconcile(inventory, { log: false, reason: 'orphan test', registryRoot: null, docker: () => ({ ok: false, stdout: '', detail: 'no docker in this test' }) });
        assert.equal(outcome.complete, true, outcome.report.join('\n'));
        assert.match(outcome.report.join('\n'), /acknowledged the private control channel and is closing its own server/);
        assert.match(outcome.report.join('\n'), /is verifiably gone/);
        assert.equal(await exited(preview.child), 0);
        assert.equal(outcome.record.state, 'complete');
    });
});
