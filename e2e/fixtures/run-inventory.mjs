// The persistent, versioned run inventory of ADR008 CT34-FIXTURES-2026-11-10-r2 (Section 10.3), as
// read and written by the Node side of the lifecycle (global setup, teardown and recovery).
//
// The record lives in the private per-account registry prepared by fixtures/lifecycle.py, which
// holds the exclusive advisory lock for this checkout for as long as this process tree runs. Every
// update is an atomic same-directory replacement with a durable file and directory flush, so a
// crash leaves either the previous or the new valid record, never a truncated one. Creation intents
// are written *before* the action they describe and carry the observed identity afterwards, so an
// interrupted creation stays pending instead of being assumed absent.
//
// Only non-secret fields are allowed: run/contract identity, owner UID, host/boot/namespace
// identity, process PIDs with their OS birth identity, canonical checkout/output/temporary-path
// identities and run marker, the private control-channel path, the Docker endpoint/context/daemon
// identity, immutable container, network and volume IDs with their creating-container association,
// creation intents and per-resource observations. Credentials, tokens, environment dumps, request
// bodies and executable cleanup commands are never stored.
import { spawnSync } from 'node:child_process';
import { closeSync, constants, fstatSync, fsyncSync, lstatSync, openSync, readFileSync, readSync, realpathSync, renameSync, writeSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

export const SCHEMA = 'codemastery-e2e-inventory/1';
export const CONTRACT = 'CT34-FIXTURES-2026-11-10-r2';
export const MAX_RECORD_BYTES = 256 * 1024;
export const MAX_INTENTS = 128;
export const MAX_OBSERVATIONS = 96;
export const MAX_PROCESSES = 64;
/** States that still carry an unresolved ownership obligation; only `complete` releases a checkout. */
export const NONTERMINAL = ['active', 'cleaning', 'incomplete'];
/** Resource states an intent can resolve to (Section 10.3: pending/removed/absent/preserved/failed). */
export const RESOURCE_STATES = ['pending', 'created', 'removed', 'absent', 'preserved', 'failed'];
/** Resource kinds an inventory may describe; nothing else is ever handed to Docker. */
export const RESOURCE_KINDS = ['container', 'network', 'volume'];
const RUN_ID = /^cm-e2e-[a-z0-9-]{1,64}$/;
/** Docker IDs and names that can only ever be an operand, never an option or a path. */
const SAFE_NAME = /^[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}$/;

export class UntrustedInventory extends Error {}
/** This process no longer acts for the coordinator that holds the checkout's lock (or for this run). */
export class OwnershipLost extends Error {}
/** The record changed since this process read it: its view is stale and was not written. */
export class StaleInventory extends Error {}

const coordinator = join(dirname(fileURLToPath(import.meta.url)), 'lifecycle.py');

/** The record path handed down by the coordinator, or null when this process was not admitted. */
export const inventoryPath = () => process.env.CODEMASTERY_E2E_INVENTORY || null;

/**
 * Why this process may *not* change `record`, or null when it may. Every inventory mutation made
 * inside a coordinated run (the runner's global setup and teardown, and `stack-down.mjs`) must come
 * from a direct child of the coordinator that currently holds this checkout's lock, and must name
 * that coordinator's run. A process whose coordinator died is reparented, so its parent no longer
 * matches: it must stop writing, because the lock is released and recovery (or the next admitted
 * run) may own the record now. The parent check cannot be fooled by PID reuse: an orphan is always
 * reparented to a reaper, never back to a new process with the old PID.
 *
 * Outside a coordinated run (none of the hand-down variables present) there is no relationship to
 * check; the unit tests drive the engine that way. A partial hand-down is refused.
 */
export function handDown(env = process.env) {
    const runId = env.CODEMASTERY_E2E_RUN_ID, key = env.CODEMASTERY_E2E_LOCK_KEY, supervisor = env.CODEMASTERY_E2E_SUPERVISOR;
    if (!runId && !key && !supervisor) return null;
    const fence = env.CODEMASTERY_E2E_FENCE === undefined ? null : Number(env.CODEMASTERY_E2E_FENCE);
    return { runId, key, supervisor: Number(supervisor), fence: Number.isInteger(fence) && fence > 0 ? fence : null, role: env.CODEMASTERY_E2E_ROLE ?? null };
}

/**
 * A quick, advisory early refusal; the authoritative check is the fenced compare-and-swap that
 * `commitInventory` performs under the record lock. `ppid` is read at every call (never captured
 * once), so a writer whose coordinator died notices it at its next mutation.
 */
export function ownershipProblem(record, { env = process.env, ppid } = {}) {
    const given = handDown(env);
    if (!given) return null;
    if (!given.runId || !given.key || !given.supervisor) return 'the lifecycle hand-down is incomplete (run ID, lock key and coordinator are all required)';
    const parent = ppid ?? process.ppid;
    if (given.supervisor !== parent) {
        return `the coordinator ${given.supervisor} that holds this checkout's lock is no longer this process' parent (parent ${parent}); its run may already be recovered or replaced`;
    }
    if (!record || record.runId !== given.runId) return `the record belongs to run ${record?.runId ?? '(none)'}, not to the handed-down run ${given.runId}`;
    if (record.lockKey !== given.key) return `the record belongs to lock key ${record.lockKey}, not ${given.key}`;
    if (given.fence === null) return 'no ownership generation (fence) was handed down to this process';
    if (record.fence !== given.fence) return `the record is at fence ${record.fence}, not this process' fence ${given.fence}; ownership was taken over`;
    return null;
}

/**
 * Structural validation before any field is used: the run ID, state, processes and intents must have
 * the shapes this lifecycle writes, so a malformed record can never turn into an unexpected Docker
 * operand, signal target or path. Optional fields may be absent; present ones must be well formed.
 */
function validate(record, path) {
    const bad = (what) => { throw new UntrustedInventory(`inventory ${path} is malformed (${what}); its run stays preserved`); };
    if (typeof record.runId !== 'string' || !RUN_ID.test(record.runId)) bad('run ID');
    if (![...NONTERMINAL, 'complete'].includes(record.state)) bad('state');
    const processes = record.processes ?? {};
    if (typeof processes !== 'object' || processes === null || Array.isArray(processes)) bad('processes');
    if (Object.keys(processes).length > MAX_PROCESSES) bad('process bound');
    for (const [role, entry] of Object.entries(processes)) {
        if (!entry || typeof entry !== 'object') bad(`process ${role}`);
        if (entry.pid != null && !(Number.isInteger(entry.pid) && entry.pid > 0)) bad(`process ${role} PID`);
        if (entry.group != null && !(Number.isInteger(entry.group) && entry.group > 0)) bad(`process ${role} group`);
        if (entry.control != null && typeof entry.control !== 'string') bad(`process ${role} control channel`);
    }
    const intents = record.intents ?? [];
    if (!Array.isArray(intents)) bad('intents');
    if (intents.length > MAX_INTENTS) throw new UntrustedInventory(`inventory ${path} exceeds the ${MAX_INTENTS} intent bound`);
    for (const intent of intents) {
        if (!intent || typeof intent !== 'object' || !RESOURCE_KINDS.includes(intent.kind)) bad('intent kind');
        if (intent.state !== undefined && !RESOURCE_STATES.includes(intent.state)) bad('intent state');
        for (const field of ['dockerId', 'name', 'container']) {
            if (intent[field] != null && !(typeof intent[field] === 'string' && SAFE_NAME.test(intent[field]))) bad(`intent ${field}`);
        }
    }
    for (const field of ['fence', 'revision']) {
        if (record[field] != null && !(Number.isInteger(record[field]) && record[field] > 0)) bad(field);
    }
    if (record.temp != null && (typeof record.temp !== 'object' || typeof record.temp.path !== 'string')) bad('temporary build');
    if (record.docker != null && (typeof record.docker !== 'object' || typeof record.docker.endpoint !== 'string'
        || typeof record.docker.daemonId !== 'string')) bad('docker identity');
}

/**
 * Reads and validates the record. Returns null when absent; throws when present but untrusted. The
 * record must be a single-link 0600 file of this account, opened without following a symlink.
 */
export function readInventory(path) {
    let handle;
    try {
        handle = openSync(path, constants.O_RDONLY | constants.O_NOFOLLOW);
    } catch (error) {
        if (error.code === 'ENOENT') return null;
        throw new UntrustedInventory(`inventory ${path} cannot be opened (${error.code ?? error.message}); a symlink is never followed`);
    }
    let raw;
    try {
        const info = fstatSync(handle);
        if (!info.isFile() || info.nlink !== 1) throw new UntrustedInventory(`inventory ${path} is not a single-link ordinary file`);
        if (info.uid !== process.getuid()) throw new UntrustedInventory(`inventory ${path} is owned by uid ${info.uid}`);
        if ((info.mode & 0o777) !== 0o600) throw new UntrustedInventory(`inventory ${path} has mode ${(info.mode & 0o777).toString(8)}, not 600`);
        if (info.size > MAX_RECORD_BYTES) throw new UntrustedInventory(`inventory ${path} is ${info.size} bytes, over the ${MAX_RECORD_BYTES} byte bound`);
        raw = Buffer.alloc(info.size);
        readSync(handle, raw, 0, info.size, 0);
    } finally {
        closeSync(handle);
    }
    let record;
    try {
        record = JSON.parse(raw.toString('utf8'));
    } catch (error) {
        throw new UntrustedInventory(`inventory ${path} is corrupt (${error.message}); its run may be incomplete and its resources stay preserved`);
    }
    if (!record || typeof record !== 'object' || record.schema !== SCHEMA) throw new UntrustedInventory(`inventory ${path} has an unknown schema`);
    if (record.ownerUid !== process.getuid()) throw new UntrustedInventory(`inventory ${path} belongs to uid ${record.ownerUid}`);
    validate(record, path);
    return record;
}

/**
 * Why this process may *not* act as the admitted Playwright invocation, or null when it may. The
 * coordinator hands its run down through the environment; this proves the hand-down is current and
 * belongs to this process: the parent must be the recorded coordinator, the record must be that run's
 * active record, and (for the runner) the configuration must belong to the admitted checkout. An
 * exported or stale variable, another checkout's configuration or a finished run is refused.
 */
export function admissionProblem({ env = process.env, ppid = process.ppid, pid = process.pid, checkoutDir = null } = {}) {
    const token = env.CODEMASTERY_E2E_OWNERSHIP, runId = env.CODEMASTERY_E2E_RUN_ID, key = env.CODEMASTERY_E2E_LOCK_KEY;
    const file = env.CODEMASTERY_E2E_INVENTORY, supervisor = Number(env.CODEMASTERY_E2E_SUPERVISOR);
    if (!token || !runId || !key || !file || !supervisor) return 'no lifecycle ownership was handed down to this process';
    if (token !== `${key}:${runId}`) return 'the ownership token does not name the handed-down run';
    if (supervisor !== ppid) return `this process was not started by the coordinator that owns the run (parent ${ppid}, coordinator ${supervisor})`;
    let record;
    try {
        record = readInventory(file);
    } catch (error) {
        return error.message;
    }
    if (!record) return 'the handed-down run record does not exist';
    if (record.runId !== runId || record.lockKey !== key) return 'the run record belongs to another run';
    if (record.state !== 'active') return `the run is ${record.state}, not active`;
    if (record.processes?.supervisor?.pid !== supervisor) return 'the run record names another coordinator';
    // The coordinator records the runner it started (intent before the spawn, PID and birth right
    // after). Once a PID is recorded, only that process is the admitted runner.
    const runner = record.processes?.runner;
    if (runner && runner.pid != null && runner.pid !== pid) return `the run record names runner ${runner.pid}, not this process`;
    if (checkoutDir) {
        let info;
        try {
            info = lstatSync(realpathSync(checkoutDir));
        } catch (error) {
            return `the checkout cannot be inspected: ${error.message}`;
        }
        if (`${info.dev}-${info.ino}` !== key) return 'this configuration belongs to another checkout than the admitted one';
    }
    return null;
}

/** Atomic replacement with a durable file and directory flush. */
export function writeInventory(path, record) {
    record.updatedAt = new Date().toISOString().replace(/\.\d+Z$/, 'Z');
    const payload = Buffer.from(JSON.stringify(record), 'utf8');
    if (payload.length > MAX_RECORD_BYTES) throw new UntrustedInventory(`refusing to write a ${payload.length} byte inventory record`);
    const directory = dirname(path);
    const temporary = join(directory, `.${path.split('/').pop()}.${process.pid}.tmp`);
    const handle = openSync(temporary, 'wx', 0o600);
    try {
        writeSync(handle, payload, 0, payload.length, 0);
        fsyncSync(handle);
    } finally {
        closeSync(handle);
    }
    renameSync(temporary, path);
    const folder = openSync(directory, 'r');
    try {
        fsyncSync(folder);
    } finally {
        closeSync(folder);
    }
    return record;
}

/**
 * Reads, mutates and durably rewrites the record in one step. `mutate` edits the record in place; a
 * return value is only taken as a replacement when it is a whole record of this schema, so helpers
 * that return the intent or the record they touched cannot accidentally become the record.
 */
export function updateInventory(path, mutate, options = {}) {
    for (let attempt = 1; ; attempt++) {
        const record = readInventory(path);
        if (!record) throw new UntrustedInventory(`inventory ${path} disappeared; this run's ownership cannot be recorded`);
        // An orphan whose coordinator died must never write into a record that recovery or a later
        // admitted run now owns: refused early here, and refused atomically by the fenced commit.
        const problem = ownershipProblem(record, options);
        if (problem) throw new OwnershipLost(`refusing to change inventory ${path}: ${problem}`);
        const replacement = mutate(record);
        const next = replacement && typeof replacement === 'object' && replacement.schema === SCHEMA ? replacement : record;
        try {
            return commitInventory(path, next, options);
        } catch (error) {
            // Another writer of the same generation committed in between: re-read and re-apply.
            if (!(error instanceof StaleInventory) || attempt >= 5) throw error;
        }
    }
}

/**
 * Durably replaces the record with `record`, which must be a mutation of the revision it carries.
 *
 * In a coordinated run (a lifecycle hand-down is present) the write is a fenced compare-and-swap
 * performed by the coordinator's own `commit-record` helper under the record lock: the record on
 * disk must still belong to this process' run ID, lock key and fence, and still be at the revision
 * this process read; otherwise nothing is written (`OwnershipLost` or `StaleInventory`). So a writer
 * that lost ownership, however long it was paused, can never replace a newer state. Without a
 * hand-down (the fixture unit tests driving the engine directly) the record is written as is.
 */
export function commitInventory(path, record, { env = process.env } = {}) {
    const given = handDown(env);
    if (!given) {
        record.revision = (record.revision ?? 0) + 1;
        return writeInventory(path, record);
    }
    if (!given.runId || !given.key || !given.supervisor || given.fence === null) {
        throw new OwnershipLost(`refusing to change inventory ${path}: the lifecycle hand-down (run ID, lock key, coordinator, fence) is incomplete`);
    }
    if (!Number.isInteger(record.revision)) throw new UntrustedInventory(`inventory ${path} carries no revision to compare`);
    record.updatedAt = new Date().toISOString().replace(/\.\d+Z$/, 'Z');
    // The hand-down only supplies the expected owner (as arguments); the helper runs in this
    // process' own environment.
    const reply = spawnSync(env.E2E_PYTHON || process.env.E2E_PYTHON || 'python3',
        [coordinator, 'commit-record', path, given.runId, given.key, String(given.fence), String(record.revision)],
        { input: JSON.stringify(record), encoding: 'utf8', timeout: 60_000 });
    const detail = (reply.stderr || reply.error?.message || '').trim().slice(0, 500);
    if (reply.status === 0) {
        record.revision = JSON.parse(reply.stdout).revision;
        return record;
    }
    if (reply.status === 5) throw new OwnershipLost(`refusing to change inventory ${path}: ${detail}`);
    if (reply.status === 6) throw new StaleInventory(`inventory ${path} changed since it was read: ${detail}`);
    throw new UntrustedInventory(`inventory ${path} could not be committed (exit ${reply.status ?? 'timeout'}): ${detail}`);
}

const bound = (list, max) => (list.length > max ? list.slice(list.length - max) : list);

/**
 * Appends a creation intent and returns its ID. Called *before* the resource is created, so an
 * interrupted creation leaves a `pending` intent that recovery must reconcile rather than ignore.
 */
export function addIntent(record, intent) {
    if (!RESOURCE_STATES.includes(intent.state ?? 'pending')) throw new UntrustedInventory(`unknown resource state ${intent.state}`);
    record.intents = record.intents ?? [];
    if (record.intents.length >= MAX_INTENTS) throw new UntrustedInventory(`this run already recorded ${MAX_INTENTS} intents`);
    const id = `i${record.intents.length + 1}`;
    record.intents.push({ id, state: 'pending', ...intent });
    return id;
}

/** Merges observed identity or an outcome into one intent. Unknown IDs are a programming error. */
export function setIntent(record, id, patch) {
    const intent = (record.intents ?? []).find((entry) => entry.id === id);
    if (!intent) throw new UntrustedInventory(`intent ${id} is not recorded`);
    if (patch.state && !RESOURCE_STATES.includes(patch.state)) throw new UntrustedInventory(`unknown resource state ${patch.state}`);
    Object.assign(intent, patch);
    return intent;
}

/** Records one bounded, non-secret observation (what was verified, preserved or failed). */
export function observe(record, text) {
    record.observations = bound([...(record.observations ?? []), `${new Date().toISOString().replace(/\.\d+Z$/, 'Z')} ${String(text).slice(0, 300)}`], MAX_OBSERVATIONS);
    return record;
}

/** Reads the run marker a disposable build directory must carry to be removable. */
export const markerFile = (directory) => join(directory, '.codemastery-e2e-run');
export const readMarker = (directory) => {
    try {
        return readFileSync(markerFile(directory), 'utf8').trim();
    } catch {
        return null;
    }
};
