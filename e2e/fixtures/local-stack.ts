/**
 * Playwright global setup: a disposable, task-owned local stack for the E2E suite.
 *
 * - PostgreSQL and the backend run from images built from this checkout
 *   (`infrastructure/postgres`, `backend/`) on a private Docker network.
 * - The frontend is a production Vite build served by `vite preview` on 127.0.0.1.
 * - Credentials (database, JWT secret, test accounts) are random per run, are passed
 *   to Docker through the environment rather than the command line, and are never
 *   printed or written inside the repository.
 * - Everything is removed by the returned teardown; `npm run stack:down` removes
 *   leftovers from an interrupted run. Containers and networks carry the shared label
 *   plus the run ID and the owning process ID, so stack:down never touches a run whose
 *   process is still alive (for example a concurrent run from another checkout).
 *
 * Set E2E_BASE_URL (and E2E_API_URL when the API has another origin) to run against an
 * already running environment instead. That mode never writes fixtures and skips the
 * fixture-dependent tests; the Instructor check then needs E2E_USERNAME/E2E_PASSWORD
 * (the reserved names in .env.example) for an existing Instructor account.
 */
import { execFile, spawn } from 'node:child_process';
import type { ChildProcess } from 'node:child_process';
import { randomBytes, randomUUID } from 'node:crypto';
import { lstatSync, readFileSync, writeFileSync } from 'node:fs';
import { mkdtemp, readFile, rm, writeFile } from 'node:fs/promises';
import { createRequire } from 'node:module';
import { createServer } from 'node:net';
import { tmpdir } from 'node:os';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { promisify } from 'node:util';
import { processIdentity } from './host-identity.mjs';
import { addIntent, inventoryPath, markerFile, observe, setIntent, updateInventory } from './run-inventory.mjs';
import { STACK_LABEL, dockerIdentity, ownerLabel, reconcile, runLabel } from './stack-recovery.mjs';

const run = promisify(execFile);
const here = dirname(fileURLToPath(import.meta.url));
const repo = resolve(here, '..', '..');
export { STACK_LABEL };
/**
 * Labels that tie a resource to this run and to the OS account that owns it. The owning PID is
 * deliberately *not* a label: under CT34-FIXTURES-2026-11-10-r2 a PID (or a label alone) is not
 * ownership evidence, so ownership lives in the persistent inventory and these labels only
 * corroborate the immutable IDs recorded there.
 */
const runLabels = (runId: string) => ['--label', STACK_LABEL, '--label', runLabel(runId), '--label', ownerLabel()];

async function docker(args: string[], env: Record<string, string> = {}, input?: string) {
    if (input === undefined) {
        const { stdout } = await run('docker', args, { env: { ...process.env, ...env }, maxBuffer: 32 * 1024 * 1024 });
        return stdout.trim();
    }
    return new Promise<string>((done, fail) => {
        const child = spawn('docker', args, { env: { ...process.env, ...env }, stdio: ['pipe', 'pipe', 'pipe'] });
        let out = '', err = '';
        child.stdout.on('data', (chunk) => { out += chunk; });
        child.stderr.on('data', (chunk) => { err += chunk; });
        child.on('close', (code) => (code === 0 ? done(out.trim()) : fail(new Error(`docker ${args[0]} failed (${code}): ${err.slice(0, 2000)}`))));
        child.stdin.end(input);
    });
}

function freePort() {
    return new Promise<number>((done, fail) => {
        const server = createServer();
        server.unref();
        server.on('error', fail);
        server.listen(0, '127.0.0.1', () => {
            const address = server.address();
            server.close(() => (typeof address === 'object' && address ? done(address.port) : fail(new Error('No free port'))));
        });
    });
}

/** A free loopback port other than `taken` (a port can be handed out twice in a row once its probe socket closes). */
async function freePortExcept(taken: number) {
    for (let attempt = 0; attempt < 10; attempt++) {
        const port = await freePort();
        if (port !== taken) return port;
    }
    throw new Error('Could not find two distinct free ports');
}

/**
 * Polls `probe` until it succeeds or `timeoutMs` passes. `stopIf` names a reason to give up at once (for example the
 * awaited process exited); it is checked before every probe and again after a successful one, so a success reported
 * while the awaited process was already gone does not count.
 */
export async function waitFor(what: string, probe: () => Promise<boolean>, timeoutMs: number,
    stopIf?: () => string | null | Promise<string | null>, intervalMs = 1000) {
    const deadline = Date.now() + timeoutMs;
    const check = async () => { const stop = await stopIf?.(); if (stop) throw new Error(`${what}: ${stop}`); };
    for (;;) {
        await check();
        let ready = false;
        try { ready = await probe(); } catch { /* not ready yet */ }
        if (ready) { await check(); return; }
        if (Date.now() > deadline) throw new Error(`Timed out waiting for ${what}`);
        await new Promise((next) => setTimeout(next, intervalMs));
    }
}

/** The marker written into this run's temporary build; no other server on the port can answer with it. */
export const buildMarker = (token: string) => `<meta name="codemastery-e2e-build" content="${token}">`;
/** Marks the temporary build's index.html (outside the source tree) with this run's token. */
export async function markBuild(dist: string, token: string) {
    const file = join(dist, 'index.html'), html = await readFile(file, 'utf8');
    if (!html.includes('</head>')) throw new Error(`${file} has no </head> to carry the build marker`);
    await writeFile(file, html.replace('</head>', `${buildMarker(token)}</head>`));
}
/** True only when `url` answers with this run's marked build; HTTP 200 from any other server is not enough. */
export async function servesBuild(url: string, token: string, probeTimeoutMs = 5000) {
    const response = await fetch(url, { signal: AbortSignal.timeout(probeTimeoutMs) });
    return response.ok && (await response.text()).includes(buildMarker(token));
}
/** Waits until `child` (vite preview) serves the marked build; fails at once, with its stderr tail, if it exits. */
export async function waitForPreview(child: ChildProcess, url: string, token: string, stderrTail: () => string,
    { timeoutMs = 60_000, probeTimeoutMs = 5000, intervalMs = 1000 } = {}) {
    const tail = () => (stderrTail().trim() ? `: ${stderrTail().trim()}` : '');
    try {
        await waitFor('frontend preview', () => servesBuild(url, token, probeTimeoutMs), timeoutMs,
            () => (child.exitCode !== null || child.signalCode !== null ? `vite preview exited (${child.exitCode ?? child.signalCode})${tail()}` : null), intervalMs);
    } catch (error) {
        if ((error as Error).message.startsWith('Timed out')) throw new Error(`${(error as Error).message} (no response carried this run's build marker)${tail()}`, { cause: error });
        throw error;
    }
}

/** A bounded Docker CLI call for state and diagnostics: it never hangs and never throws. */
async function dockerQuery(args: string[], timeoutMs = 15_000) {
    try {
        const { stdout, stderr } = await run('docker', args, { timeout: timeoutMs, maxBuffer: 4 * 1024 * 1024 });
        return { ok: true, stdout: String(stdout), stderr: String(stderr) };
    } catch (error) {
        const failure = error as { stdout?: unknown; stderr?: unknown; message?: string };
        return { ok: false, stdout: String(failure.stdout ?? ''), stderr: String(failure.stderr || failure.message || 'docker failed') };
    }
}
const STATE_FORMAT = 'state={{.State.Status}} exit={{.State.ExitCode}} oom={{.State.OOMKilled}} error={{json .State.Error}} started={{.State.StartedAt}} finished={{.State.FinishedAt}}';
/**
 * Why `container` is confirmed to have stopped, or null while it runs. A failed inspection is a confirmed stop only when
 * Docker reports the container missing; any other inspection error is treated as transient and polling continues.
 */
export async function containerExit(container: string): Promise<string | null> {
    const state = await dockerQuery(['inspect', '--type', 'container', '--format', STATE_FORMAT, container]);
    if (!state.ok) return /no such (object|container)/i.test(state.stderr) ? `container ${container} no longer exists` : null;
    const summary = state.stdout.trim();
    return summary.startsWith('state=running ') ? null : `the container stopped (${summary})`;
}
/** Container state and the last `lines` log lines, with `secrets` redacted. Never throws; collection failures are reported inline. */
export async function containerDiagnostics(container: string, secrets: readonly string[], lines = 100) {
    const redact = (text: string) => secrets.filter((secret) => secret.length >= 6).reduce((out, secret) => out.split(secret).join('[redacted]'), text);
    const state = await dockerQuery(['inspect', '--type', 'container', '--format', STATE_FORMAT, container]);
    const logs = await dockerQuery(['logs', '--tail', String(lines), container]);
    const report = [
        `--- ${container} state: ${state.ok ? state.stdout.trim() : `unavailable (${state.stderr.trim().slice(0, 500)})`}`,
        logs.ok ? `--- last ${lines} log lines of ${container}:\n${`${logs.stdout}${logs.stderr}`.trimEnd().slice(-20_000)}` : `--- logs unavailable: ${logs.stderr.trim().slice(0, 500)}`,
    ].join('\n');
    return redact(report);
}
/**
 * Waits for the backend health endpoint, failing at once if the container stops. On any failure the container's state and
 * log tail are captured here, before the caller's teardown removes the container, and added to the original error.
 */
export async function waitForBackend(apiUrl: string, container: string, secrets: readonly string[], { timeoutMs = 180_000, intervalMs = 1000 } = {}) {
    try {
        await waitFor('backend health', async () => (await fetch(`${apiUrl}/actuator/health`, { signal: AbortSignal.timeout(5000) })).ok,
            timeoutMs, () => containerExit(container), intervalMs);
    } catch (error) {
        const diagnostics = await containerDiagnostics(container, secrets).catch((reason: unknown) => `--- diagnostics unavailable: ${String(reason)}`);
        throw new Error(`${(error as Error).message}\n${diagnostics}`, { cause: error });
    }
}

async function api(base: string, path: string, body: unknown) {
    const response = await fetch(new URL(path, base), { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) });
    if (!response.ok) throw new Error(`${path} returned HTTP ${response.status}`);
    return response.json() as Promise<Record<string, unknown>>;
}

/** The per-checkout layer-cache tag of one image, e.g. `codemastery-e2e/backend:co-16777230-4902041`. */
export function cacheTag(component: 'backend' | 'postgres', lockKey = process.env.CODEMASTERY_E2E_LOCK_KEY ?? '') {
    if (!/^[0-9]+-[0-9]+$/.test(lockKey)) throw new Error('No lifecycle lock key: the per-checkout image cache tag cannot be named');
    return `codemastery-e2e/${component}:co-${lockKey}`;
}

/** The pinned Vite CLI of `frontend/`, from its manifest's `bin` field (its exports map does not expose the file). */
function resolveViteBin(frontend: string) {
    const manifest = createRequire(join(frontend, 'package.json')).resolve('vite/package.json');
    const bin = JSON.parse(readFileSync(manifest, 'utf8')).bin;
    return join(dirname(manifest), typeof bin === 'string' ? bin : bin.vite);
}

/** Spawns one process, records it in the inventory as a writer while it runs, and resolves with its stderr tail. */
async function runRecorded(inventoryFile: string, role: string, command: string, args: string[],
    options: { cwd: string; env?: Record<string, string | undefined> }) {
    const child = spawn(command, args, { cwd: options.cwd, env: { ...process.env, ...options.env }, stdio: ['ignore', 'pipe', 'pipe'] });
    let tail = '';
    child.stdout?.on('data', (chunk) => { tail = (tail + chunk).slice(-4000); });
    child.stderr?.on('data', (chunk) => { tail = (tail + chunk).slice(-4000); });
    const identity = child.pid ? processIdentity(child.pid) : { birth: null, source: 'no-pid', reliable: false };
    updateInventory(inventoryFile, (record) => {
        record.processes[role] = { pid: child.pid ?? null, birth: identity.birth, birthSource: identity.source, role, state: 'live' };
    });
    const code = await new Promise<number | null>((done) => child.once('close', (status) => done(status)));
    updateInventory(inventoryFile, (record) => {
        record.processes[role] = { ...record.processes[role], state: 'exited', why: `exited with ${code}` };
    });
    if (code !== 0) throw new Error(`${role} (${command}) exited with ${code}: ${tail.trim().slice(-2000)}`);
    return tail;
}

export default async function globalSetup() {
    // Ownership was taken by fixtures/lifecycle.py before this runner was started; a run that is
    // not admitted has no inventory to write its resources into and must not create any.
    const inventoryFile = inventoryPath();
    if (!inventoryFile) {
        throw new Error('The E2E suite must be started through the lifecycle coordinator, which takes exclusive ownership '
            + 'of this checkout and its outputs before Playwright runs. Use `npm test` or `npm run test:chromium` from e2e/.');
    }
    const runId = process.env.CODEMASTERY_E2E_RUN_ID as string;
    const runnerIdentity = processIdentity(process.pid);
    updateInventory(inventoryFile, (record) => {
        record.processes.runner = { pid: process.pid, birth: runnerIdentity.birth, birthSource: runnerIdentity.source, role: 'playwright-runner', state: 'live' };
    });
    /** Reconciles this run through the shared r2 engine; incomplete recovery is never silent. */
    const teardown = async (reason: string, previewHandle?: ChildProcess) => {
        // The runner's own exit is not claimed here: the coordinator verifies it by birth identity
        // after this process ends, and only then can the run become complete.
        const outcome = await reconcile(inventoryFile, { reason, previewHandle });
        if (!outcome.complete) {
            throw new Error(`The disposable E2E stack was not fully recovered (${reason}). Its inventory is retained and this `
                + 'checkout stays blocked until `npm run stack:down` completes; see the stack-down report above.');
        }
    };

    if (process.env.E2E_BASE_URL) {
        process.env.E2E_API_URL ??= process.env.E2E_BASE_URL;
        process.env.E2E_STACK = 'external';
        updateInventory(inventoryFile, (record) => {
            record.mode = 'external';
            observe(record, 'external target mode: this run owns no stack resource');
        });
        return () => teardown('external-mode teardown');
    }

    // The daemon this run will own resources on is pinned here — endpoint, context *and* daemon ID —
    // and re-verified before anything is removed, so context or daemon drift preserves resources.
    const daemon = dockerIdentity();
    if (!daemon.ok) throw new Error(`Docker cannot own this run's disposable stack: ${daemon.why}`);
    const work = await mkdtemp(join(tmpdir(), 'codemastery-e2e-'));
    writeFileSync(markerFile(work), `${runId}\n`, { mode: 0o600 });
    const workInfo = lstatSync(work), parentInfo = lstatSync(dirname(work));
    updateInventory(inventoryFile, (record) => {
        record.mode = 'disposable';
        record.docker = { context: daemon.context, endpoint: daemon.endpoint, daemonId: daemon.daemonId };
        record.temp = { path: work, dev: workInfo.dev, ino: workInfo.ino, parent: { dev: parentInfo.dev, ino: parentInfo.ino }, marker: runId, state: 'created' };
        observe(record, `pinned docker daemon ${daemon.daemonId} on ${daemon.context}; temporary build recorded`);
    });

    /** Records a creation intent before the resource exists and returns its inventory ID. */
    const intend = (fields: Record<string, unknown>) => {
        let id = '';
        updateInventory(inventoryFile, (record) => { id = addIntent(record, fields); });
        return id;
    };
    /** Persists the observed identity of a created resource before the run continues. */
    const created = (id: string, dockerId: string, why: string) => {
        updateInventory(inventoryFile, (record) => { setIntent(record, id, { dockerId, state: 'created', why }); });
    };

    let preview: ChildProcess | undefined;
    try {
        const secrets = {
            POSTGRES_DB: 'codemastery_e2e', POSTGRES_USER: 'codemastery_e2e', POSTGRES_PASSWORD: randomBytes(24).toString('hex'),
            JWT_SECRET: randomBytes(32).toString('hex'),
            STORAGE_ACCESS_KEY: `e2e-${randomBytes(6).toString('hex')}`, STORAGE_SECRET_KEY: randomBytes(24).toString('hex'),
        };
        // Containers run the exact image ID this run built (printed by --quiet); the tag is only a layer
        // cache. It is per checkout: runs of one checkout are serialized by the lifecycle lock, so the tag
        // never moves under a live run, while a concurrent run from another checkout uses its own tag.
        // (With a containerd image store, retagging a shared tag deletes the previously tagged image, so
        // a tag shared across checkouts could remove another run's exact image before it is started.)
        const backendImage = process.env.E2E_BACKEND_IMAGE
            ?? await docker(['build', '--quiet', '--label', STACK_LABEL, '-t', cacheTag('backend'), join(repo, 'backend')]);
        const postgresImage = process.env.E2E_POSTGRES_IMAGE
            ?? await docker(['build', '--quiet', '--label', STACK_LABEL, '-t', cacheTag('postgres'), join(repo, 'infrastructure', 'postgres')]);

        const network = runId;
        const networkIntent = intend({ kind: 'network', name: network });
        await docker(['network', 'create', ...runLabels(runId), network]);
        created(networkIntent, await docker(['network', 'inspect', '--format', '{{.Id}}', network]), 'created by this run');

        const postgres = `${runId}-postgres`;
        const postgresIntent = intend({ kind: 'container', name: postgres, image: postgresImage, role: 'postgres' });
        const postgresId = await docker(['run', '-d', '--name', postgres, ...runLabels(runId), '--network', network, '--network-alias', 'postgres',
            '-e', 'POSTGRES_DB', '-e', 'POSTGRES_USER', '-e', 'POSTGRES_PASSWORD', postgresImage], secrets);
        created(postgresIntent, postgresId, 'started by this run');
        // The anonymous data volume this image declares is discovered from the owned container's
        // mounts and recorded now, long before anything removes that container.
        await recordAnonymousVolumes(inventoryFile, postgresId, postgres);
        await waitFor('PostgreSQL', async () => {
            await docker(['exec', postgres, 'pg_isready', '-q', '-U', secrets.POSTGRES_USER, '-d', secrets.POSTGRES_DB]);
            return true;
        }, 90_000);

        const backendPort = await freePort(), apiUrl = `http://127.0.0.1:${backendPort}`;
        // Production build pointed at this backend, marked with a per-run token in the temporary copy only.
        const frontend = join(repo, 'frontend'), dist = join(work, 'dist'), buildToken = randomBytes(16).toString('hex');
        // Vite is started from its resolved entry point rather than through `npx`, so the recorded PID
        // is the real build/preview process and not a wrapper that exits before its child.
        const viteBin = process.env.E2E_VITE_BIN ?? resolveViteBin(frontend);
        await runRecorded(inventoryFile, 'builder', process.execPath,
            [viteBin, 'build', '--outDir', dist, '--emptyOutDir', '--logLevel', 'error'],
            { cwd: frontend, env: { VITE_API_BASE_URL: apiUrl } });
        await markBuild(dist, buildToken);

        // The frontend port is chosen immediately before the preview binds it. The backend needs that origin for CORS,
        // so it starts afterwards. Probing a port does not reserve it: readiness also requires this run's build marker,
        // so a collision fails instead of testing whatever server took the port.
        const frontendPort = await freePortExcept(backendPort), baseUrl = `http://127.0.0.1:${frontendPort}`;
        const control = join(process.env.CODEMASTERY_E2E_SOCKETS as string, `${runId}.sock`);
        preview = spawn(process.execPath, [join(here, 'preview-server.mjs'), '--root', frontend, '--outDir', dist,
            '--host', '127.0.0.1', '--port', String(frontendPort), '--strictPort', '--control', control, '--run', runId],
        { cwd: frontend, stdio: ['ignore', 'ignore', 'pipe'] });
        let previewError = '';
        preview.stderr?.on('data', (chunk) => { previewError = (previewError + chunk).slice(-2000); });
        const previewIdentity = preview.pid ? processIdentity(preview.pid) : { birth: null, source: 'no-pid', reliable: false };
        updateInventory(inventoryFile, (record) => {
            record.processes.preview = { pid: preview?.pid ?? null, birth: previewIdentity.birth, birthSource: previewIdentity.source,
                role: 'vite-preview', control, state: 'live' };
            observe(record, previewIdentity.reliable
                ? 'preview recorded with its OS birth identity and private control channel'
                : `preview birth identity is unavailable (${previewIdentity.source}): automatic recovery of this process is unsupported`);
        });
        await waitForPreview(preview, baseUrl, buildToken, () => previewError);

        const backend = `${runId}-backend`;
        const backendIntent = intend({ kind: 'container', name: backend, image: backendImage, role: 'backend' });
        const backendId = await docker(['run', '-d', '--name', backend, ...runLabels(runId), '--network', network, '-p', `127.0.0.1:${backendPort}:8080`,
            '-e', 'DB_HOST=postgres', '-e', 'DB_PORT=5432', '-e', 'DB_NAME', '-e', 'DB_USER', '-e', 'DB_PASSWORD', '-e', 'JWT_SECRET',
            '-e', 'STORAGE_ACCESS_KEY', '-e', 'STORAGE_SECRET_KEY', '-e', `CORS_ALLOWED_ORIGINS=${baseUrl}`, '-e', 'SERVER_PORT=8080', backendImage],
        { DB_NAME: secrets.POSTGRES_DB, DB_USER: secrets.POSTGRES_USER, DB_PASSWORD: secrets.POSTGRES_PASSWORD, JWT_SECRET: secrets.JWT_SECRET,
            STORAGE_ACCESS_KEY: secrets.STORAGE_ACCESS_KEY, STORAGE_SECRET_KEY: secrets.STORAGE_SECRET_KEY });
        created(backendIntent, backendId, 'started by this run');
        await recordAnonymousVolumes(inventoryFile, backendId, backend);
        await waitForBackend(apiUrl, backend, [secrets.POSTGRES_PASSWORD, secrets.JWT_SECRET, secrets.STORAGE_ACCESS_KEY, secrets.STORAGE_SECRET_KEY]);

        // Instructor account through the real registration API, then promoted in the disposable database only.
        const instructor = { email: `e2e-instructor-${randomUUID()}@example.test`, password: `E2e-${randomBytes(12).toString('hex')}!`, displayName: 'E2E Course Instructor' };
        await api(apiUrl, '/api/auth/register', instructor);
        const psql = ['exec', '-i', '-e', 'PGPASSWORD', postgres, 'psql', '-v', 'ON_ERROR_STOP=1', '-qtA', '-U', secrets.POSTGRES_USER, '-d', secrets.POSTGRES_DB];
        const pg = { PGPASSWORD: secrets.POSTGRES_PASSWORD };
        const instructorId = await docker([...psql, '-v', `email=${instructor.email}`], pg,
            "UPDATE users SET role = 'instructor' WHERE email = :'email' RETURNING id;\n");
        if (!/^[0-9a-f-]{36}$/.test(instructorId)) throw new Error('Instructor promotion failed');
        await docker([...psql, '-v', `instructor_id=${instructorId}`], pg, await readFile(join(here, 'catalogue-fixture.sql'), 'utf8'));
        // The preview started before the backend wait: confirm it is still alive and still serving this run's build.
        await waitForPreview(preview, baseUrl, buildToken, () => previewError, { timeoutMs: 10_000 });

        Object.assign(process.env, {
            E2E_STACK: 'disposable', E2E_BASE_URL: baseUrl, E2E_API_URL: apiUrl,
            E2E_USERNAME: instructor.email, E2E_PASSWORD: instructor.password, E2E_FIXTURE: 'catalogue-fixture.sql',
        });
        updateInventory(inventoryFile, (record) => observe(record, 'disposable stack ready; fixtures applied'));
        return () => teardown('playwright teardown', preview);
    } catch (error) {
        updateInventory(inventoryFile, (record) => observe(record, `setup failed: ${String((error as Error).message).slice(0, 200)}`));
        await teardown('setup failure', preview).catch((cleanupError: unknown) => {
            console.error(`E2E setup failed and cleanup did not complete: ${String(cleanupError)}`);
        });
        throw error;
    }
}

/**
 * Records the anonymous volumes a just-created container owns, before anything can remove it.
 * Bind mounts and pre-existing named volumes are recorded as preserved: they are not this run's.
 */
async function recordAnonymousVolumes(inventoryFile: string, containerId: string, name: string) {
    const mounts = await docker(['inspect', '--type', 'container', '--format', '{{json .Mounts}}', containerId]).catch(() => '[]');
    let parsed: Array<{ Type?: string; Name?: string; Destination?: string }> = [];
    try { parsed = JSON.parse(mounts || '[]') ?? []; } catch { parsed = []; }
    for (const mount of parsed) {
        if (mount.Type !== 'volume' || !mount.Name) continue;
        const anonymous = /^[0-9a-f]{64}$/.test(mount.Name);
        updateInventory(inventoryFile, (record) => {
            const id = addIntent(record, {
                kind: 'volume', name: mount.Name as string, dockerId: mount.Name, container: containerId,
                destination: mount.Destination, exclusive: anonymous, state: anonymous ? 'created' : 'preserved',
                why: anonymous ? `anonymous volume of ${name}` : 'a named or pre-existing volume is not owned by this run',
            });
            observe(record, `${anonymous ? 'recorded owned anonymous' : 'preserved foreign'} volume ${mount.Name} of ${name} (${id})`);
        });
    }
}
