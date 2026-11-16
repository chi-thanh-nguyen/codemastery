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
import { basename, dirname, isAbsolute, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { handDown, ownershipProblem, readInventory, readMarker } from './run-inventory.mjs';

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

/**
 * `{reliable, members:[{pid, birth}]}`: the live (non-zombie) members of one process group, read from
 * the OS without signalling anything. `reliable:false` means the group could not be inspected, which
 * is never proof that it is empty.
 */
export function processGroup(pgid) {
    if (!Number.isInteger(pgid) || pgid <= 1) return { pgid, reliable: false, members: [], detail: 'invalid process-group ID' };
    const reply = ask(['proc-group', String(pgid)]);
    if (reply.error) return { pgid, reliable: false, members: [], detail: reply.error };
    return { pgid, reliable: reply.reliable === true, members: Array.isArray(reply.members) ? reply.members : [], detail: reply.detail };
}

/**
 * Waits until the coordinator has recorded *this* process as the writer `role` (it records each
 * writer's creation intent before the spawn and its PID and birth identity right after), and returns
 * that entry. A writer never records itself and writes nothing before this: a process that is not
 * the recorded one, whose coordinator is gone or whose generation was fenced out is refused.
 */
export async function confirmRecordedWriter(inventoryFile, role, { timeoutMs = 15_000, intervalMs = 100, env = process.env } = {}) {
    const given = handDown(env);
    if (!given || !given.runId || !given.key || !given.supervisor || given.fence === null) {
        throw new Error(`No complete lifecycle hand-down (run ID, lock key, coordinator and fence); the ${role} writes nothing`);
    }
    const mine = processIdentity(process.pid);
    const deadline = Date.now() + timeoutMs;
    for (;;) {
        const record = readInventory(inventoryFile);
        if (!record) throw new Error(`The run inventory ${inventoryFile} does not exist; nothing was changed`);
        const problem = ownershipProblem(record, { env });
        if (problem) throw new Error(`This ${role} no longer acts for its lifecycle coordinator (${problem}); nothing was changed`);
        const entry = record.processes?.[role];
        if (!entry) throw new Error(`The run record has no ${role} intent, so this process was not started by the coordinator; nothing was changed`);
        if (entry.pid === process.pid) {
            if (entry.birth && mine.birth && entry.birth !== mine.birth) throw new Error(`The recorded ${role} has another birth identity than this process; nothing was changed`);
            return entry;
        }
        if (entry.pid != null) throw new Error(`The run record names ${role} ${entry.pid}, not this process (${process.pid}); nothing was changed`);
        if (Date.now() > deadline) throw new Error(`The coordinator did not record this ${role}'s identity in time; nothing was changed`);
        await new Promise((next) => setTimeout(next, intervalMs));
    }
}

export function hostIdentity() {
    const reply = ask(['host-identity']);
    if (reply.error) return { system: process.platform, hostname: null, machine: null, boot: null, pidNamespace: null, uid: process.getuid(), detail: reply.error };
    return reply;
}

/**
 * True when `recorded` describes the same host, boot and PID namespace we are running in now. The
 * host is the OS machine identity (macOS host UUID, Linux machine-id) when both sides have one; a
 * network-assigned hostname changes with the network and is only the fallback for records without it.
 * A missing boot identity never matches.
 */
export function sameHost(recorded, current = hostIdentity()) {
    if (!recorded || !current || !recorded.boot || recorded.boot !== current.boot) return false;
    const host = recorded.machine && current.machine ? recorded.machine === current.machine : recorded.hostname === current.hostname;
    return host && (recorded.pidNamespace ?? null) === (current.pidNamespace ?? null);
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
    // Path ancestry: only a directory this lifecycle creates (`mkdtemp(<tmpdir>/codemastery-e2e-…)`)
    // can ever be a removal target, whatever else a record might name.
    if (!/^codemastery-e2e-[A-Za-z0-9]{6,}$/.test(basename(recorded.path))) return { ok: false, why: 'the recorded path is not a temporary build directory of this lifecycle' };
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
