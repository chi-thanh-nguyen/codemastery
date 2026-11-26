// Regression tests for the startup lifecycle in local-stack.ts: frontend readiness must identify this run's build, a
// backend that fails to start must fail promptly with its diagnostics captured before teardown, and that teardown must
// go through the r2 inventory protocol (ADR008 CT34-FIXTURES-2026-11-10-r2): every resource is recorded before it is
// created, and cleanup removes exactly the recorded ones, stops the preview over its private control channel and
// verifies the temporary build before deleting it. Docker is a stateful stub first on PATH, Vite's build is a stub
// entry point (E2E_VITE_BIN) and its preview API a stub module (E2E_VITE_MODULE), so no real container, network,
// image, Vite build or browser is touched.
// Run from e2e/ (Node 22.18+ or 24, which load the TypeScript fixture natively): node --test fixtures/*.test.mjs
import { after, before, beforeEach, describe, test } from 'node:test';
import assert from 'node:assert/strict';
import { spawn } from 'node:child_process';
import { once } from 'node:events';
import { chmodSync, existsSync, mkdtempSync, readdirSync, readFileSync, realpathSync, rmSync, writeFileSync } from 'node:fs';
import { createServer } from 'node:http';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import globalSetup, { BUILD_FAILURE_LINES, STACK_LABEL, buildImage, buildMarker, cacheTag, confirmRecordedRunner, containerExit, markBuild, waitForBackend, waitForPreview, waitForStorageInitializer } from './local-stack.ts';
import { hostIdentity, processIdentity } from './host-identity.mjs';
import { readInventory, writeInventory } from './run-inventory.mjs';

// Stateful stub docker: resources live in STUB_DIR/resources.json and every call is appended to STUB_DIR/calls.log.
// Container state for the readiness helpers still comes from STUB_MODE. `run` remembers the JWT secret it receives
// through the environment, and `logs` prints it back, so redaction can be checked.
const ANON = 'a'.repeat(64);
const DOCKER = `#!/usr/bin/env node
const fs = require('node:fs'), path = require('node:path');
const dir = process.env.STUB_DIR, mode = process.env.STUB_MODE, args = process.argv.slice(2), id = args.at(-1);
fs.appendFileSync(path.join(dir, 'calls.log'), args.join(' ') + '\\n');
fs.appendFileSync(path.join(dir, 'hosts.log'), (process.env.DOCKER_HOST || '<unpinned>') + ' ' + (process.env.DOCKER_CONTEXT || '') + '|' + args.join(' ') + '\\n');
const fail = (message) => { process.stderr.write(message + '\\n'); process.exit(1); };
const dbFile = path.join(dir, 'resources.json'), db = fs.existsSync(dbFile) ? JSON.parse(fs.readFileSync(dbFile, 'utf8')) : {};
const save = () => fs.writeFileSync(dbFile, JSON.stringify(db));
const labelsOf = () => Object.fromEntries(args.filter((_, at) => args[at - 1] === '--label').map((label) => [label.split('=')[0], label.split('=').slice(1).join('=')]));
const list = (kind) => console.log(Object.entries(db).filter(([, r]) => r.kind === kind && r.present).map(([key]) => key).join('\\n'));
const live = (key) => { if (!db[key] || !db[key].present) fail('Error: No such object: ' + key); return db[key]; };
const counter = path.join(dir, 'inspects');
if (args[0] === 'context' && args[1] === 'show') console.log('stub-context');
else if (args[0] === 'context') console.log('unix:///var/run/docker.sock');
else if (args[0] === 'compose') {
    for (const key of ['APP_QUIZ_AUTO_SUBMIT_ENABLED', 'APP_QUIZ_AUTO_SUBMIT_DELAY_MS', 'APP_QUIZ_AUTO_SUBMIT_BATCH_SIZE'])
        if (!process.env[key]) fail('missing CT38 compose input ' + key);
    console.log(JSON.stringify({ services: {
    minio: { image: 'fixture/minio@sha256:' + '1'.repeat(64), command: ['server', '/data'] },
    'minio-init': { image: 'fixture/mc@sha256:' + '2'.repeat(64), entrypoint: ['/bin/sh', '-ec'], command: ['echo "$$STORAGE_BUCKET"'] },
} }));
}
else if (args[0] === 'info') console.log('stub-daemon');
else if (args[0] === 'network' && args[1] === 'create') { db['net-' + id] = { kind: 'network', labels: labelsOf(), present: true }; save(); }
else if (args[0] === 'network' && args[1] === 'inspect') { const key = db[id] ? id : 'net-' + id; const r = live(key); console.log(args.includes('{{.Id}}') ? key : JSON.stringify(r.labels)); }
else if (args[0] === 'network' && args[1] === 'ls') list('network');
else if (args[0] === 'volume' && args[1] === 'inspect') console.log(JSON.stringify(live(id).labels));
else if ((args[0] === 'network' || args[0] === 'volume') && args[1] === 'rm') { live(id).present = false; save(); console.log(id); }
else if (args[0] === 'ps') list('container');
else if (args[0] === 'rm') { live(id).present = false; save(); console.log(id); }
else if (args[0] === 'exec') { /* pg_isready and psql succeed */ }
else if (args[0] === 'build') {
    // Plain progress goes to stderr, as with BuildKit. 'build-fails' imitates the wrapper's download failing after a long log.
    // The log is one write and the exit status is set without process.exit(), which could drop queued pipe output.
    if (mode === 'build-fails') {
        const log = Array.from({ length: 200 }, (_, at) => '#11 ' + (at + 1) + '.000 Downloaded from central: artifact-' + (at + 1) + '.pom\\n');
        log.push('#11 0.285 Exception in thread "main" java.net.UnknownHostException: repo.maven.apache.org\\n');
        if (process.env.STUB_LEAK) log.push('#11 0.300 echoed ' + process.env.STUB_LEAK + '\\n');
        log.push('ERROR: failed to build: failed to solve: process "/bin/sh -c ./mvnw package -DskipTests" did not complete successfully: exit code: 1\\n');
        process.stderr.write(log.join(''));
        process.exitCode = 1;
    } else {
        fs.writeFileSync(path.join(dir, 'build-progress'), process.env.BUILDKIT_PROGRESS || '<unset>');
        fs.writeFileSync(args[args.indexOf('--iidfile') + 1], mode === 'build-no-id' ? 'not-an-image-id\\n' : 'sha256:' + 'b'.repeat(64));
        process.stderr.write('#1 [internal] load build definition from Dockerfile\\n');
    }
}
else if (args[0] === 'run') {
    if (process.env.JWT_SECRET) fs.writeFileSync(path.join(dir, 'secret'), process.env.JWT_SECRET);
    const name = args[args.indexOf('--name') + 1], postgres = name.endsWith('-postgres');
    if (name.endsWith('-minio-init')) fs.writeFileSync(path.join(dir, 'storage-secret'), process.env.MINIO_ROOT_PASSWORD);
    if (name.endsWith('-backend') && args.some((arg) => arg.startsWith('MINIO_ROOT_'))) fail('administrative credentials reached backend');
    db['ctr-' + name] = { kind: 'container', name, labels: labelsOf(), present: true,
        mounts: postgres ? [{ Type: 'volume', Name: '${ANON}', Destination: '/var/lib/postgresql/data' }] : [] };
    if (postgres) db['${ANON}'] = { kind: 'volume', labels: { 'com.docker.volume.anonymous': '' }, present: true };
    save(); console.log('ctr-' + name);
} else if (args[0] === 'inspect' && args.includes('{{json .Mounts}}')) {
    if (mode === 'mounts-fail') fail('Error response from daemon: i/o timeout');
    console.log(JSON.stringify(live(id).mounts));
}
else if (args[0] === 'inspect' && args.includes('{{json .State}}')) {
    if (mode === 'init-inspect-fails') fail('Error response from daemon: i/o timeout');
    console.log(JSON.stringify({ Status: mode === 'init-running' ? 'running' : 'exited', ExitCode: mode === 'init-fails' ? 7 : 0 }));
}
else if (args[0] === 'inspect' && args.includes('{{json .}}')) { const r = live(id); console.log(JSON.stringify({ Id: id, Config: { Labels: r.labels }, Mounts: r.mounts, State: { Status: 'exited' } })); }
else if (args[0] === 'inspect') {
    const inspects = (fs.existsSync(counter) ? Number(fs.readFileSync(counter, 'utf8')) : 0) + 1;
    fs.writeFileSync(counter, String(inspects));
    if (mode === 'missing') fail('Error: No such object: ' + id);
    if (mode === 'inspect-fails' || (mode === 'diagnostics-fail' && inspects > 1)) fail('Error response from daemon: i/o timeout');
    console.log(mode === 'running'
        ? 'state=running exit=0 oom=false error="" started=2026-11-07T00:00:00Z finished=0001-01-01T00:00:00Z'
        : 'state=exited exit=3 oom=false error="" started=2026-11-07T00:00:00Z finished=2026-11-07T00:00:04Z');
} else if (args[0] === 'logs') {
    if (mode === 'diagnostics-fail') fail('Error response from daemon: connection refused');
    const secret = fs.existsSync(path.join(dir, 'secret')) ? fs.readFileSync(path.join(dir, 'secret'), 'utf8') : 'none';
    if (fs.existsSync(path.join(dir, 'storage-secret'))) console.log('storage diagnostic: ' + fs.readFileSync(path.join(dir, 'storage-secret'), 'utf8'));
    console.log('Flyway migration failed: relation already exists');
    console.log('jwt secret in a log line: ' + secret);
    process.stderr.write('APPLICATION FAILED TO START\\n');
} else fail('unexpected docker ' + args.join(' '));
`;
// Stub Vite build entry point (run as \`node vite.cjs build --outDir <dir> ...\`): writes a minimal index.html.
const VITE_BIN = `const fs = require('node:fs'), path = require('node:path');
const args = process.argv.slice(2), outDir = args[args.indexOf('--outDir') + 1];
fs.mkdirSync(outDir, { recursive: true });
fs.writeFileSync(path.join(outDir, 'index.html'), '<!doctype html><html><head><title>CodeMastery</title></head><body></body></html>');
`;
// Stub of the pinned Vite preview API: serves the build and records that it closed its own server.
const VITE_MODULE = `import { createServer } from 'node:http';
import { readFileSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';
export async function preview(options) {
    const http = createServer((_, response) => response.end(readFileSync(join(options.build.outDir, 'index.html'))));
    await new Promise((ready, failed) => { http.once('error', failed); http.listen(options.preview.port, options.preview.host, ready); });
    return { close: () => new Promise((closed) => { writeFileSync(join(process.env.STUB_DIR, 'preview-stopped'), ''); http.closeAllConnections(); http.close(closed); }) };
}
`;

let stub = '';
const saved = { ...process.env };
const calls = () => readFileSync(join(stub, 'calls.log'), 'utf8').trim().split('\n').filter(Boolean);
before(() => {
    stub = realpathSync(mkdtempSync(join(tmpdir(), 'cmls-')));
    chmodSync(stub, 0o700);
    writeFileSync(join(stub, 'docker'), DOCKER); chmodSync(join(stub, 'docker'), 0o755);
    writeFileSync(join(stub, 'vite.cjs'), VITE_BIN);
    writeFileSync(join(stub, 'vite-preview.mjs'), VITE_MODULE);
    process.env.PATH = `${stub}:${saved.PATH}`; process.env.STUB_DIR = stub;
});
beforeEach(() => {
    writeFileSync(join(stub, 'calls.log'), '');
    for (const name of ['inspects', 'preview-stopped', 'resources.json', 'secret', 'hosts.log', 'build-progress', 'storage-secret']) rmSync(join(stub, name), { force: true });
});
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

describe('image cache tags never cross checkouts', () => {
    test('each checkout key names its own layer-cache tag; a missing key is refused', () => {
        assert.equal(cacheTag('backend', '16777230-4902041'), 'codemastery-e2e/backend:co-16777230-4902041');
        assert.notEqual(cacheTag('postgres', '1-2'), cacheTag('postgres', '1-3'), 'two checkouts never share a tag that could move under a live run');
        assert.throws(() => cacheTag('backend', ''), /No lifecycle lock key/);
        assert.throws(() => cacheTag('backend', 'local'), /No lifecycle lock key/);
    });
});

describe('image builds record the exact image ID and report a failing step\'s own output', () => {
    const work = () => mkdtempSync(join(tmpdir(), 'codemastery-local-stack-build-'));

    test('the image ID comes from an ID file in the run\'s temporary directory, with plain progress instead of --quiet', async () => {
        process.env.STUB_MODE = 'running';
        process.env.BUILDKIT_PROGRESS = 'quiet';
        const dir = work();
        try {
            assert.equal(await buildImage('/checkout/backend', 'codemastery-e2e/backend:co-1-2', dir), `sha256:${'b'.repeat(64)}`);
            const build = calls().find((call) => call.startsWith('build ')).split(' ');
            assert.ok(!build.includes('--quiet') && !build.some((arg) => arg.startsWith('--progress')), `no output-suppressing or BuildKit-only flag: ${build.join(' ')}`);
            assert.equal(readFileSync(join(stub, 'build-progress'), 'utf8'), 'plain', 'plain progress is set for this build, over an inherited value');
            assert.ok(build[build.indexOf('--iidfile') + 1].startsWith(`${dir}/`), 'the ID file lives in the recorded temporary build directory');
            assert.equal(build[build.indexOf('--label') + 1], STACK_LABEL);
            assert.equal(build[build.indexOf('-t') + 1], 'codemastery-e2e/backend:co-1-2');
            assert.equal(build.at(-1), '/checkout/backend');
        } finally { delete process.env.BUILDKIT_PROGRESS; rmSync(dir, { recursive: true, force: true }); }
    });

    test('a failed build reports the failing step\'s own message from a bounded, redacted output tail', async () => {
        process.env.STUB_MODE = 'build-fails';
        process.env.STUB_LEAK = 'f'.repeat(48);
        const dir = work();
        try {
            const error = await buildImage('/checkout/backend', 'codemastery-e2e/backend:co-1-2', dir, ['f'.repeat(48)]).then(() => null, (reason) => reason);
            assert.ok(error instanceof Error, 'the build must fail');
            assert.match(error.message, /^docker build of \/checkout\/backend failed \(1\); last 80 of 203 output lines:\n/);
            assert.match(error.message, /java\.net\.UnknownHostException: repo\.maven\.apache\.org/, 'the cause --quiet used to discard is reported');
            assert.match(error.message, /did not complete successfully: exit code: 1$/);
            assert.equal(error.message.split('\n').length - 1, BUILD_FAILURE_LINES, 'only the output tail is kept');
            assert.ok(!error.message.includes('artifact-123.pom') && error.message.includes('artifact-124.pom'), 'older lines are dropped');
            assert.ok(!error.message.includes('f'.repeat(48)) && error.message.includes('echoed [redacted]'), 'secrets are redacted');
        } finally { delete process.env.STUB_LEAK; rmSync(dir, { recursive: true, force: true }); }
    });

    test('a build that records no valid image ID is refused instead of running an unknown image', async () => {
        process.env.STUB_MODE = 'build-no-id';
        const dir = work();
        try {
            await assert.rejects(buildImage('/checkout/backend', 'codemastery-e2e/backend:co-1-2', dir), /recorded no valid image ID: "not-an-image-id"/);
        } finally { rmSync(dir, { recursive: true, force: true }); }
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

    /** The record the lifecycle coordinator would have written before starting Playwright. */
    function admit(runnerPatch = {}) {
        const host = hostIdentity(), inventory = join(stub, 'run.json');
        writeInventory(inventory, {
            schema: 'codemastery-e2e-inventory/1', contract: 'CT34-FIXTURES-2026-11-10-r2', runId: 'cm-e2e-ls', state: 'active',
            ownerUid: process.getuid(), lockKey: 'stub', fence: 1, revision: 1, host: { hostname: host.hostname, boot: host.boot, pidNamespace: host.pidNamespace, system: host.system },
            // The coordinator is this runner's parent, exactly as when lifecycle.py starts Playwright, and it
            // has already recorded this process as the runner (intent before the spawn, identity after it).
            processes: {
                supervisor: { pid: process.ppid, birth: null, role: 'coordinator', state: 'live' },
                runner: { pid: process.pid, birth: processIdentity(process.pid).birth, birthSource: 'test', role: 'playwright-runner', state: 'live', spawned: true, group: process.pid, ...runnerPatch },
            },
            checkout: { path: stub, dev: 0, ino: 0 }, outputs: [], docker: null, temp: null, intents: [], observations: [],
        });
        Object.assign(process.env, {
            CODEMASTERY_E2E_INVENTORY: inventory, CODEMASTERY_E2E_RUN_ID: 'cm-e2e-ls', CODEMASTERY_E2E_SOCKETS: stub,
            CODEMASTERY_E2E_LOCK_KEY: 'stub', CODEMASTERY_E2E_SUPERVISOR: String(process.ppid), CODEMASTERY_E2E_FENCE: '1', E2E_VITE_BIN: join(stub, 'vite.cjs'), E2E_VITE_MODULE: join(stub, 'vite-preview.mjs'),
        });
        delete process.env.CODEMASTERY_E2E_REGISTRY;
        return inventory;
    }
    async function setupFailure(mode) {
        process.env.STUB_MODE = mode;
        const inventory = admit();
        const temp = mkdtempSync(join(tmpdir(), 'codemastery-local-stack-tmp-'));
        Object.assign(process.env, { TMPDIR: temp, E2E_BACKEND_IMAGE: 'stub-backend', E2E_POSTGRES_IMAGE: 'stub-postgres' });
        delete process.env.E2E_BASE_URL;
        const started = Date.now();
        try {
            const error = await globalSetup().then(() => null, (reason) => reason);
            return { error, elapsed: Date.now() - started, leftovers: readdirSync(temp), record: readInventory(inventory) };
        } finally {
            if (saved.TMPDIR === undefined) delete process.env.TMPDIR; else process.env.TMPDIR = saved.TMPDIR;
            rmSync(temp, { recursive: true, force: true });
        }
    }
    const backendId = () => `ctr-${calls().find((call) => call.startsWith('run -d --name ') && call.includes('-backend '))?.split(' ')[3]}`;

    test('global setup fails promptly when the backend exits, keeps its logs (secrets redacted) and then cleans up', async () => {
        const { error, elapsed, leftovers, record } = await setupFailure('exited');
        assert.ok(error instanceof Error, 'setup must fail');
        assert.ok(elapsed < 30_000, `failed after ${elapsed} ms, not after the 180 s health timeout`);
        assert.match(error.message, /^backend health: the container stopped \(state=exited exit=3 oom=false/);
        assert.match(error.message, /Flyway migration failed: relation already exists/);
        const secret = readFileSync(join(stub, 'secret'), 'utf8');
        assert.ok(secret.length === 64 && !error.message.includes(secret), 'the JWT secret is redacted');
        assert.match(error.message, /jwt secret in a log line: \[redacted\]/);
        const log = calls(), backend = backendId(), backendName = backend.slice(4);
        assert.ok(log.indexOf(`logs --tail 100 ${backendName}`) >= 0, log.join('\n'));
        assert.ok(log.indexOf(`logs --tail 100 ${backendName}`) < log.indexOf(`rm -f ${backend}`), 'logs are read before the container is removed');
        assert.ok(log.includes('rm -f ctr-cm-e2e-ls-postgres') && log.includes(`volume rm ${ANON}`) && log.includes('network rm net-cm-e2e-ls'), log.join('\n'));
        assert.ok(log.indexOf('rm -f ctr-cm-e2e-ls-postgres') < log.indexOf(`volume rm ${ANON}`), 'the volume goes after its container');
        assert.ok(!log.some((call) => /prune|--filter label=codemastery.e2e=disposable.*rm/.test(call)), 'no global or label-only deletion');
        assert.ok(existsSync(join(stub, 'preview-stopped')), 'the preview closed its own server');
        assert.deepEqual(leftovers, [], 'the temporary build directory was removed');
        // Every resource is verified absent; the run stays nonterminal until the coordinator verifies the runner's exit.
        assert.equal(record.state, 'cleaning', 'resources are gone but the run is not complete while its runner lives');
        assert.equal(record.processes.runner.state, 'reconciling-self');
        assert.deepEqual(record.intents.map((intent) => `${intent.kind}:${intent.state}`),
            ['network:removed', 'container:removed', 'volume:removed', 'container:removed', 'container:removed', 'container:removed']);
        assert.equal(record.processes.preview.state, 'exited');
        assert.equal(record.processes.builder.state, 'exited');
        assert.ok(record.processes.preview.birth, 'the preview was recorded with its OS birth identity');
        assert.ok(!JSON.stringify(record).includes(secret), 'no secret reaches the inventory');
    });
    test('the default stack initializes storage once, isolates root credentials and recovers it on failure', async () => {
        const { error, record } = await setupFailure('init-fails');
        assert.match(error.message, /Storage initializer failed \(exit 7\)/);
        assert.ok(!error.message.includes(readFileSync(join(stub, 'storage-secret'), 'utf8')), 'administrative secret is redacted');
        assert.match(error.message, /storage diagnostic: \[redacted\]/);
        const log = calls(), starts = log.filter((line) => line.startsWith('run -d --name '));
        assert.equal(starts.filter((line) => line.includes('-minio-init ')).length, 1, 'never replay initialization');
        assert.ok(!starts.some((line) => line.includes('-backend ')), 'backend cannot start after failed initialization');
        for (const role of ['minio', 'minio-init']) {
            const intent = record.intents.find((entry) => entry.role === role);
            assert.equal(intent.state, 'removed');
            assert.equal(intent.volumes, 'recorded');
        }
        assert.ok(starts.filter((line) => line.includes('-minio')).every((line) => !line.includes(' -p ')), 'storage stays private');
        assert.ok(!JSON.stringify(record).includes('MINIO_ROOT_PASSWORD'));
    });
    test('every resource is recorded as an intent before it is created', async () => {
        const { record } = await setupFailure('exited');
        const log = calls();
        assert.equal(record.docker.daemonId, 'stub-daemon', 'the daemon was pinned before anything was created');
        assert.equal(record.docker.endpoint, 'unix:///var/run/docker.sock');
        for (const intent of record.intents.filter((entry) => entry.kind !== 'volume')) assert.ok(intent.dockerId, `${intent.name} has its observed ID`);
        assert.equal(record.intents.find((entry) => entry.kind === 'volume').container, 'ctr-cm-e2e-ls-postgres', 'the anonymous volume is tied to its container');
        assert.ok(record.temp.marker === 'cm-e2e-ls' && record.temp.ino > 0, 'the temporary build is recorded with its identity and marker');
        assert.ok(log.some((call) => call.includes('--label codemastery.e2e.run=cm-e2e-ls --label codemastery.e2e.owner=')), 'run and owner labels corroborate the inventory');
        assert.ok(!log.some((call) => call.includes('codemastery.e2e.pid=')), 'a PID label is no longer written');
    });
    test('a diagnostics failure keeps the original error and does not prevent cleanup', async () => {
        const { error, leftovers, record } = await setupFailure('diagnostics-fail');
        assert.match(error.message, /^backend health: the container stopped \(state=exited exit=3/);
        assert.match(error.message, /state: unavailable \(Error response from daemon: i\/o timeout\)/);
        assert.match(error.message, /logs unavailable: Error response from daemon: connection refused/);
        const log = calls();
        assert.ok(log.includes(`rm -f ${backendId()}`) && log.some((call) => call.startsWith('network rm ')), log.join('\n'));
        assert.ok(existsSync(join(stub, 'preview-stopped')));
        assert.deepEqual(leftovers, []);
        assert.equal(record.state, 'cleaning');
    });
    test('every Docker call after the daemon is pinned is bound to the recorded endpoint', async () => {
        process.env.DOCKER_CONTEXT = 'some-other-context';
        try {
            const { record } = await setupFailure('exited');
            const lines = readFileSync(join(stub, 'hosts.log'), 'utf8').trim().split('\n');
            const bound = lines.filter((line) => !/\|context (show|inspect)/.test(line));
            assert.ok(bound.some((line) => line.includes('|run -d ')) && bound.some((line) => line.includes('|rm -f ')), lines.join('\n'));
            for (const line of bound) assert.ok(line.startsWith(`${record.docker.endpoint} |`), `bound to the pinned endpoint with no context: ${line}`);
        } finally {
            delete process.env.DOCKER_CONTEXT;
        }
    });
    test('container volume associations are intents written before creation and resolved from the container\'s mounts', async () => {
        const { record } = await setupFailure('exited');
        const containers = record.intents.filter((intent) => intent.kind === 'container');
        assert.deepEqual(containers.map((intent) => intent.volumes), ['recorded', 'recorded', 'recorded', 'recorded']);
        assert.equal(record.processes.builder.state, 'exited');
        assert.ok(record.processes.preview.pid > 0 && record.processes.preview.control.endsWith('cm-e2e-ls.sock'));
    });
    test('a mount inspection that fails is a setup error, never "no volume", and recovery still accounts for the volume', async () => {
        const { error, record } = await setupFailure('mounts-fail');
        assert.match(error.message, /The mounts of cm-e2e-ls-postgres could not be read; its volume association stays pending/);
        const postgres = record.intents.find((intent) => intent.name === 'cm-e2e-ls-postgres');
        assert.equal(postgres.volumes, 'recorded', 'recovery read the association from the container itself');
        assert.equal(record.intents.find((intent) => intent.kind === 'volume')?.state, 'removed');
        assert.ok(calls().indexOf('rm -f ctr-cm-e2e-ls-postgres') < calls().indexOf(`volume rm ${ANON}`));
    });
    test('a process the coordinator did not record as the runner creates nothing', async () => {
        admit({ pid: 999_999, birth: 'x-starttime:1' });
        await assert.rejects(globalSetup(), /The run record names runner 999999, not this process/);
        assert.deepEqual(calls(), [], 'no Docker call was made');
        assert.equal(readInventory(join(stub, 'run.json')).intents.length, 0, 'nothing was recorded');
    });
    test('a runner whose coordinator is gone (it was reparented) creates and writes nothing', async () => {
        const inventory = admit();
        process.env.CODEMASTERY_E2E_SUPERVISOR = '1';
        try {
            await assert.rejects(globalSetup(), /no longer acts for its lifecycle coordinator .* is no longer this process' parent/);
        } finally {
            process.env.CODEMASTERY_E2E_SUPERVISOR = String(process.ppid);
        }
        assert.deepEqual(calls(), [], 'no Docker call was made');
        const record = readInventory(inventory);
        assert.equal(record.docker, null, 'the record was not changed');
        assert.equal(record.temp, null);
    });
    test('global setup waits for the coordinator to record the runner\'s identity after the spawn', async () => {
        const inventory = admit({ pid: null, birth: null, state: 'starting' });
        // The coordinator persists the PID and birth identity right after the spawn.
        setTimeout(() => {
            const record = readInventory(inventory);
            record.processes.runner = { ...record.processes.runner, pid: process.pid, birth: processIdentity(process.pid).birth, state: 'live' };
            writeInventory(inventory, record);
        }, 300);
        const runner = await confirmRecordedRunner(inventory, { timeoutMs: 5000, intervalMs: 50 });
        assert.equal(runner.pid, process.pid);
        await assert.rejects(confirmRecordedRunner(admit({ pid: null, birth: null, state: 'starting' }), { timeoutMs: 300, intervalMs: 50 }),
            /did not record this runner's identity in time/);
    });
    test('a runner that was not admitted by the coordinator creates nothing', async () => {
        delete process.env.CODEMASTERY_E2E_INVENTORY;
        await assert.rejects(globalSetup(), /must be started through the lifecycle coordinator/);
        assert.deepEqual(calls(), [], 'no Docker call was made');
    });
});

describe('storage initializer completion is mandatory', () => {
    test('only confirmed zero exit succeeds; failure, timeout and unreadable state propagate', async () => {
        process.env.STUB_MODE = 'exited';
        await waitForStorageInitializer('owned-init', [], { timeoutMs: 50, intervalMs: 5 });
        for (const [mode, reason] of [['init-fails', /failed \(exit 7\)/], ['init-running', /Timed out/],
            ['init-inspect-fails', /could not be inspected/]]) {
            process.env.STUB_MODE = mode;
            await assert.rejects(waitForStorageInitializer('owned-init', [], { timeoutMs: 20, intervalMs: 5 }), reason);
        }
    });
});
