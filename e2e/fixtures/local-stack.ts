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
import { mkdtemp, readFile, rm } from 'node:fs/promises';
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

/** Two distinct free loopback ports (a port can be handed out twice in a row once its probe socket closes). */
async function freePorts() {
    for (let attempt = 0; attempt < 10; attempt++) {
        const first = await freePort(), second = await freePort();
        if (first !== second) return [first, second] as const;
    }
    throw new Error('Could not find two distinct free ports');
}

async function waitFor(what: string, probe: () => Promise<boolean>, timeoutMs: number, stopIf?: () => string | null) {
    const deadline = Date.now() + timeoutMs;
    for (;;) {
        const stop = stopIf?.();
        if (stop) throw new Error(`${what}: ${stop}`);
        try { if (await probe()) return; } catch { /* not ready yet */ }
        if (Date.now() > deadline) throw new Error(`Timed out waiting for ${what}`);
        await new Promise((ready) => setTimeout(ready, 1000));
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
        if (preview && preview.exitCode === null) {
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

        const [backendPort, frontendPort] = await freePorts();
        const apiUrl = `http://127.0.0.1:${backendPort}`, baseUrl = `http://127.0.0.1:${frontendPort}`;
        const backend = `${runId}-backend`;
        containers.push(backend);
        await docker(['run', '-d', '--name', backend, ...runLabels(runId), '--network', network, '-p', `127.0.0.1:${backendPort}:8080`,
            '-e', 'DB_HOST=postgres', '-e', 'DB_PORT=5432', '-e', 'DB_NAME', '-e', 'DB_USER', '-e', 'DB_PASSWORD', '-e', 'JWT_SECRET',
            '-e', 'STORAGE_ACCESS_KEY', '-e', 'STORAGE_SECRET_KEY', '-e', `CORS_ALLOWED_ORIGINS=${baseUrl}`, '-e', 'SERVER_PORT=8080', backendImage],
        { DB_NAME: secrets.POSTGRES_DB, DB_USER: secrets.POSTGRES_USER, DB_PASSWORD: secrets.POSTGRES_PASSWORD, JWT_SECRET: secrets.JWT_SECRET,
            STORAGE_ACCESS_KEY: secrets.STORAGE_ACCESS_KEY, STORAGE_SECRET_KEY: secrets.STORAGE_SECRET_KEY });
        await waitFor('backend health', async () => (await fetch(`${apiUrl}/actuator/health`, { signal: AbortSignal.timeout(5000) })).ok, 180_000);

        // Instructor account through the real registration API, then promoted in the disposable database only.
        const instructor = { email: `e2e-instructor-${randomUUID()}@example.test`, password: `E2e-${randomBytes(12).toString('hex')}!`, displayName: 'E2E Course Instructor' };
        await api(apiUrl, '/api/auth/register', instructor);
        const psql = ['exec', '-i', '-e', 'PGPASSWORD', postgres, 'psql', '-v', 'ON_ERROR_STOP=1', '-qtA', '-U', secrets.POSTGRES_USER, '-d', secrets.POSTGRES_DB];
        const pg = { PGPASSWORD: secrets.POSTGRES_PASSWORD };
        const instructorId = await docker([...psql, '-v', `email=${instructor.email}`], pg,
            "UPDATE users SET role = 'instructor' WHERE email = :'email' RETURNING id;\n");
        if (!/^[0-9a-f-]{36}$/.test(instructorId)) throw new Error('Instructor promotion failed');
        await docker([...psql, '-v', `instructor_id=${instructorId}`], pg, await readFile(join(here, 'catalogue-fixture.sql'), 'utf8'));

        // Production build pointed at this backend, served by vite preview.
        const frontend = join(repo, 'frontend'), dist = join(work, 'dist');
        await run('npx', ['vite', 'build', '--outDir', dist, '--emptyOutDir', '--logLevel', 'error'], { cwd: frontend, env: { ...process.env, VITE_API_BASE_URL: apiUrl }, maxBuffer: 32 * 1024 * 1024 });
        preview = spawn('npx', ['vite', 'preview', '--outDir', dist, '--host', '127.0.0.1', '--port', String(frontendPort), '--strictPort'], { cwd: frontend, stdio: ['ignore', 'ignore', 'pipe'] });
        let previewError = '';
        preview.stderr?.on('data', (chunk) => { previewError = (previewError + chunk).slice(-2000); });
        // Fail at once, with Vite's own message, if the preview exits early (for example the port was taken
        // between probing and binding), instead of polling until the timeout.
        const child = preview;
        await waitFor('frontend preview', async () => (await fetch(baseUrl, { signal: AbortSignal.timeout(5000) })).ok, 60_000,
            () => (child.exitCode !== null ? `vite preview exited (${child.exitCode})${previewError ? `: ${previewError.trim()}` : ''}` : null));

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
