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
import { mkdtemp, readFile, rm, writeFile } from 'node:fs/promises';
import { createServer } from 'node:net';
import { tmpdir } from 'node:os';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { promisify } from 'node:util';

const run = promisify(execFile);
const here = dirname(fileURLToPath(import.meta.url));
const repo = resolve(here, '..', '..');
export const STACK_LABEL = 'codemastery.e2e=disposable';
/** Labels that tie a resource to this run and to the process that owns it (read by stack-down.mjs). */
const runLabels = (runId: string) => ['--label', STACK_LABEL, '--label', `codemastery.e2e.run=${runId}`, '--label', `codemastery.e2e.pid=${process.pid}`];

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

export default async function globalSetup() {
    if (process.env.E2E_BASE_URL) {
        process.env.E2E_API_URL ??= process.env.E2E_BASE_URL;
        process.env.E2E_STACK = 'external';
        return async () => undefined;
    }
    const runId = `cm-e2e-${Date.now().toString(36)}-${randomBytes(3).toString('hex')}`;
    const work = await mkdtemp(join(tmpdir(), 'codemastery-e2e-'));
    const containers: string[] = [];
    let network = '', preview: ChildProcess | undefined;
    const teardown = async () => {
        if (preview && preview.exitCode === null && preview.signalCode === null) {
            preview.kill('SIGTERM');
            await new Promise((gone) => { preview!.once('exit', gone); setTimeout(gone, 5000); });
        }
        for (const name of containers.reverse()) await docker(['rm', '-f', '-v', name]).catch(() => undefined);
        if (network) await docker(['network', 'rm', network]).catch(() => undefined);
        await rm(work, { recursive: true, force: true });
    };
    try {
        const secrets = {
            POSTGRES_DB: 'codemastery_e2e', POSTGRES_USER: 'codemastery_e2e', POSTGRES_PASSWORD: randomBytes(24).toString('hex'),
            JWT_SECRET: randomBytes(32).toString('hex'),
            STORAGE_ACCESS_KEY: `e2e-${randomBytes(6).toString('hex')}`, STORAGE_SECRET_KEY: randomBytes(24).toString('hex'),
        };
        // Containers run the exact image ID this run built (printed by --quiet), not the shared tag, so a
        // concurrent run from another checkout that rebuilds the tag cannot swap the image underneath.
        const backendImage = process.env.E2E_BACKEND_IMAGE
            ?? await docker(['build', '--quiet', '--label', STACK_LABEL, '-t', 'codemastery-e2e/backend:local', join(repo, 'backend')]);
        const postgresImage = process.env.E2E_POSTGRES_IMAGE
            ?? await docker(['build', '--quiet', '--label', STACK_LABEL, '-t', 'codemastery-e2e/postgres:local', join(repo, 'infrastructure', 'postgres')]);

        network = runId;
        await docker(['network', 'create', ...runLabels(runId), network]);
        const postgres = `${runId}-postgres`;
        containers.push(postgres);
        await docker(['run', '-d', '--name', postgres, ...runLabels(runId), '--network', network, '--network-alias', 'postgres',
            '-e', 'POSTGRES_DB', '-e', 'POSTGRES_USER', '-e', 'POSTGRES_PASSWORD', postgresImage], secrets);
        await waitFor('PostgreSQL', async () => {
            await docker(['exec', postgres, 'pg_isready', '-q', '-U', secrets.POSTGRES_USER, '-d', secrets.POSTGRES_DB]);
            return true;
        }, 90_000);

        const backendPort = await freePort(), apiUrl = `http://127.0.0.1:${backendPort}`;
        // Production build pointed at this backend, marked with a per-run token in the temporary copy only.
        const frontend = join(repo, 'frontend'), dist = join(work, 'dist'), buildToken = randomBytes(16).toString('hex');
        await run('npx', ['vite', 'build', '--outDir', dist, '--emptyOutDir', '--logLevel', 'error'], { cwd: frontend, env: { ...process.env, VITE_API_BASE_URL: apiUrl }, maxBuffer: 32 * 1024 * 1024 });
        await markBuild(dist, buildToken);

        // The frontend port is chosen immediately before vite preview binds it. The backend needs that origin for CORS,
        // so it starts afterwards. Probing a port does not reserve it: readiness also requires this run's build marker,
        // so a collision fails instead of testing whatever server took the port.
        const frontendPort = await freePortExcept(backendPort), baseUrl = `http://127.0.0.1:${frontendPort}`;
        preview = spawn('npx', ['vite', 'preview', '--outDir', dist, '--host', '127.0.0.1', '--port', String(frontendPort), '--strictPort'], { cwd: frontend, stdio: ['ignore', 'ignore', 'pipe'] });
        let previewError = '';
        preview.stderr?.on('data', (chunk) => { previewError = (previewError + chunk).slice(-2000); });
        await waitForPreview(preview, baseUrl, buildToken, () => previewError);

        const backend = `${runId}-backend`;
        containers.push(backend);
        await docker(['run', '-d', '--name', backend, ...runLabels(runId), '--network', network, '-p', `127.0.0.1:${backendPort}:8080`,
            '-e', 'DB_HOST=postgres', '-e', 'DB_PORT=5432', '-e', 'DB_NAME', '-e', 'DB_USER', '-e', 'DB_PASSWORD', '-e', 'JWT_SECRET',
            '-e', 'STORAGE_ACCESS_KEY', '-e', 'STORAGE_SECRET_KEY', '-e', `CORS_ALLOWED_ORIGINS=${baseUrl}`, '-e', 'SERVER_PORT=8080', backendImage],
        { DB_NAME: secrets.POSTGRES_DB, DB_USER: secrets.POSTGRES_USER, DB_PASSWORD: secrets.POSTGRES_PASSWORD, JWT_SECRET: secrets.JWT_SECRET,
            STORAGE_ACCESS_KEY: secrets.STORAGE_ACCESS_KEY, STORAGE_SECRET_KEY: secrets.STORAGE_SECRET_KEY });
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
        return teardown;
    } catch (error) {
        await teardown();
        throw error;
    }
}
