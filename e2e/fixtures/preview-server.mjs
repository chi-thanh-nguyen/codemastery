// The disposable run's frontend preview, hosted in a process of its own so that it can be recovered
// cooperatively (ADR008 CT34-FIXTURES-2026-11-10-r2, Section 10.3).
//
// It serves the temporary production build through the pinned Vite preview API — the same thing the
// `vite preview` CLI does — and additionally listens on a private, run-scoped Unix-domain socket
// inside `~/.codemastery-e2e/sockets/`, protected by that registry's same-UID 0700 boundary. An
// orphaned preview is stopped by asking it, over that channel, to close its own server: the process
// acknowledges its own run and OS birth identity first and refuses a stale or mismatching request.
// A recovered numeric PID is never a signal target, and there is no PID-based fallback.
//
// Run as: node preview-server.mjs --root <frontend> --outDir <dir> --host <h> --port <n>
//                                --control <socket> --run <runId> [--strictPort]
import { chmodSync, unlinkSync } from 'node:fs';
import { createServer } from 'node:net';
import { createRequire } from 'node:module';
import { join } from 'node:path';
import { processIdentity } from './host-identity.mjs';

const argv = process.argv.slice(2);
const value = (name, fallback = null) => {
    const at = argv.indexOf(name);
    return at === -1 || at === argv.length - 1 ? fallback : argv[at + 1];
};
const root = value('--root');
const outDir = value('--outDir');
const host = value('--host', '127.0.0.1');
const port = Number(value('--port', '0'));
const control = value('--control');
const runId = value('--run');
const strictPort = argv.includes('--strictPort');
for (const [name, given] of [['--root', root], ['--outDir', outDir], ['--control', control], ['--run', runId]]) {
    if (!given) {
        process.stderr.write(`preview-server: ${name} is required\n`);
        process.exit(2);
    }
}
// A Unix-domain path is bounded by the platform (104 bytes on macOS, 108 on Linux). Fail closed
// rather than silently losing the only authorized recovery channel.
if (Buffer.byteLength(control) > 103) {
    process.stderr.write(`preview-server: the control socket path is ${Buffer.byteLength(control)} bytes, too long for this platform\n`);
    process.exit(2);
}

/** The pinned Vite of `frontend/` (resolved from that manifest), or a stub module in lifecycle tests. */
async function loadVite() {
    if (process.env.E2E_VITE_MODULE) return import(process.env.E2E_VITE_MODULE);
    return import(createRequire(join(root, 'package.json')).resolve('vite'));
}

const birth = processIdentity(process.pid).birth;
let server;

/** Closes the preview server and exits. The process stops itself; nobody signals it. */
async function selfClose(reason) {
    process.stdout.write(`preview-server: closing (${reason})\n`);
    try {
        await server?.close();
    } catch (error) {
        process.stderr.write(`preview-server: close failed: ${error.message}\n`);
        process.exit(1);
    }
    process.exit(0);
}

function startControlChannel() {
    const channel = createServer((socket) => {
        socket.setEncoding('utf8');
        let buffer = '';
        socket.on('data', (chunk) => {
            buffer += chunk;
            if (buffer.length > 4096) { socket.end(JSON.stringify({ ok: false, reason: 'oversized-request' })); return; }
            const line = buffer.indexOf('\n');
            if (line === -1) return;
            let request = {};
            try {
                request = JSON.parse(buffer.slice(0, line));
            } catch {
                socket.end(JSON.stringify({ ok: false, reason: 'unparsable-request' }));
                return;
            }
            if (request.command === 'ping') {
                socket.end(JSON.stringify({ ok: true, runId, pid: process.pid, birth }));
                return;
            }
            if (request.command !== 'shutdown') {
                socket.end(JSON.stringify({ ok: false, reason: 'unknown-command' }));
                return;
            }
            // The process acknowledges its own identity: a request naming another run, or the birth
            // identity of a process this PID no longer is, is refused.
            if (request.runId !== runId) {
                socket.end(JSON.stringify({ ok: false, reason: 'run-mismatch', runId }));
                return;
            }
            if (request.birth && birth && request.birth !== birth) {
                socket.end(JSON.stringify({ ok: false, reason: 'birth-mismatch', runId, pid: process.pid, birth }));
                return;
            }
            socket.end(JSON.stringify({ ok: true, closing: true, runId, pid: process.pid, birth }), () => {
                void selfClose('asked over the private control channel');
            });
        });
        socket.on('error', () => socket.destroy());
    });
    channel.on('error', (error) => {
        process.stderr.write(`preview-server: control channel failed: ${error.message}\n`);
        process.exit(1);
    });
    // A socket file left by a dead process of this same run ID would block binding; it is inside the
    // private registry and names this run, so replacing it touches nothing another run owns.
    try {
        unlinkSync(control);
    } catch { /* nothing to replace */ }
    channel.listen(control, () => {
        chmodSync(control, 0o600);
        process.stdout.write(`preview-server: control channel ready for ${runId}\n`);
    });
    channel.unref();
    return channel;
}

try {
    const { preview } = await loadVite();
    server = await preview({
        root, configFile: undefined, logLevel: 'error',
        build: { outDir }, preview: { host, port, strictPort },
    });
    startControlChannel();
    // SIGTERM remains honoured for the supervised path, where the parent still holds this child's
    // own process handle; it is not a recovery mechanism for an orphan.
    process.on('SIGTERM', () => void selfClose('SIGTERM from the owning supervisor'));
    process.on('SIGINT', () => void selfClose('SIGINT'));
} catch (error) {
    process.stderr.write(`preview-server: could not start the preview: ${error?.message ?? error}\n`);
    process.exit(1);
}
