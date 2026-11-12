// Host, process-birth and path identity for the r2 lifecycle (ADR008 Section 10.3).
//
// A PID alone is never ownership evidence: a recorded process is resolved as exited only when the
// same host/boot/PID namespace and reliable OS birth data prove it, and a PID that now carries a
// different birth identity is an unrelated process that must never be signalled. The platform
// birth data itself comes from fixtures/lifecycle.py, which reads Linux process start ticks with
// the boot and PID-namespace identity, and macOS' native kinfo_proc start time (microseconds), so
// there is one qualified implementation rather than two.
import { spawnSync } from 'node:child_process';
import { lstatSync } from 'node:fs';
import { dirname, isAbsolute, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { readMarker } from './run-inventory.mjs';

const here = dirname(fileURLToPath(import.meta.url));
const launcher = join(here, 'lifecycle.py');
/** Documented tooling override, used by the stub lifecycle tests; the default is the system Python 3. */
export const pythonBin = () => process.env.E2E_PYTHON || 'python3';

function ask(args, timeoutMs = 20_000) {
    const result = spawnSync(pythonBin(), [launcher, ...args], { encoding: 'utf8', timeout: timeoutMs });
    if (result.status !== 0 || !result.stdout) {
        return { error: (result.stderr || result.error?.message || `exit ${result.status}`).trim().slice(0, 500) };
    }
    try {
        return JSON.parse(result.stdout);
    } catch (error) {
        return { error: `unparsable identity reply: ${error.message}` };
    }
}

/** `{pid, exists, birth, source, reliable}`; `reliable:false` means recovery must preserve the process. */
export function processIdentity(pid) {
    if (!Number.isInteger(pid) || pid <= 0) return { pid, exists: null, birth: null, source: 'invalid-pid', reliable: false };
    const reply = ask(['proc-identity', String(pid)]);
    if (reply.error) return { pid, exists: null, birth: null, source: 'unreadable', reliable: false, detail: reply.error };
    return reply;
}

export function hostIdentity() {
    const reply = ask(['host-identity']);
    if (reply.error) return { system: process.platform, hostname: null, boot: null, pidNamespace: null, uid: process.getuid(), detail: reply.error };
    return reply;
}

/** True when `recorded` describes the same host, boot and PID namespace we are running in now. */
export function sameHost(recorded, current = hostIdentity()) {
    if (!recorded) return false;
    return recorded.hostname === current.hostname && recorded.boot === current.boot
        && (recorded.pidNamespace ?? null) === (current.pidNamespace ?? null);
}

/**
 * Classifies one recorded process without ever signalling anything:
 *
 * - `foreign`      another host/boot/PID namespace; a local `kill(pid, 0)` verdict is meaningless
 * - `exact-live`   the PID exists and carries the recorded birth identity
 * - `pid-reused`   the PID exists with another birth identity: ours is gone, this one is unrelated
 * - `exited`       the OS reliably reports no such process
 * - `unreadable`   the process could not be inspected; failure to inspect is not proof of exit
 * - `ambiguous`    no reliable birth identity was ever recorded, so recovery is unsupported
 */
export function classifyProcess(recorded, options = {}) {
    if (!recorded || !Number.isInteger(recorded.pid)) return { state: 'ambiguous', why: 'no PID recorded' };
    const current = options.host ?? hostIdentity();
    if (options.recordedHost && !sameHost(options.recordedHost, current)) {
        return { state: 'foreign', why: 'recorded on another host, boot or PID namespace; explicit reconciliation is required' };
    }
    if (!recorded.birth) return { state: 'ambiguous', why: 'no OS birth identity was recorded for this process' };
    const seen = options.identity ?? processIdentity(recorded.pid);
    if (seen.exists === false && seen.reliable) return { state: 'exited', why: 'the OS reports no such process', seen };
    if (seen.exists === true && seen.reliable) {
        return seen.birth === recorded.birth
            ? { state: 'exact-live', why: 'the PID carries the recorded birth identity', seen }
            : { state: 'pid-reused', why: 'the PID now carries another birth identity; the recorded process is gone and this one is unrelated', seen };
    }
    return { state: 'unreadable', why: seen.detail || 'the process could not be inspected', seen };
}

/**
 * Verifies a recorded directory before anything is deleted: its parent, owner, device/inode and run
 * marker must all match, and neither it nor its parent may be a symlink, so a replaced path is
 * refused instead of followed.
 */
export function verifyDirectory(recorded, runId) {
    if (!recorded?.path || !isAbsolute(recorded.path)) return { ok: false, why: 'no absolute path recorded' };
    let parent, self;
    try {
        parent = lstatSync(dirname(recorded.path));
        self = lstatSync(recorded.path);
    } catch (error) {
        if (error.code === 'ENOENT') return { ok: false, absent: true, why: 'the recorded directory is already gone' };
        return { ok: false, why: `cannot inspect ${recorded.path}: ${error.message}` };
    }
    if (parent.isSymbolicLink() || !parent.isDirectory()) return { ok: false, why: 'the recorded parent is not an ordinary directory' };
    if (recorded.parent && (parent.dev !== recorded.parent.dev || parent.ino !== recorded.parent.ino)) {
        return { ok: false, why: 'the parent directory is not the one recorded (it was replaced)' };
    }
    if (self.isSymbolicLink() || !self.isDirectory()) return { ok: false, why: 'the recorded path is not an ordinary directory' };
    if (self.uid !== process.getuid()) return { ok: false, why: `the recorded directory is owned by uid ${self.uid}` };
    if (recorded.dev !== self.dev || recorded.ino !== self.ino) return { ok: false, why: 'the recorded directory identity changed (it was replaced)' };
    const marker = readMarker(recorded.path);
    if (marker !== runId) return { ok: false, why: `the run marker is ${marker === null ? 'missing' : 'another run'}` };
    return { ok: true };
}
