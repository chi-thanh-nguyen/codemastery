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
import { closeSync, fstatSync, fsyncSync, openSync, readFileSync, readSync, renameSync, writeSync } from 'node:fs';
import { dirname, join } from 'node:path';

export const SCHEMA = 'codemastery-e2e-inventory/1';
export const CONTRACT = 'CT34-FIXTURES-2026-11-10-r2';
export const MAX_RECORD_BYTES = 256 * 1024;
export const MAX_INTENTS = 128;
export const MAX_OBSERVATIONS = 96;
/** States that still carry an unresolved ownership obligation; only `complete` releases a checkout. */
export const NONTERMINAL = ['active', 'cleaning', 'incomplete'];
/** Resource states an intent can resolve to (Section 10.3: pending/removed/absent/preserved/failed). */
export const RESOURCE_STATES = ['pending', 'created', 'removed', 'absent', 'preserved', 'failed'];

export class UntrustedInventory extends Error {}

/** The record path handed down by the coordinator, or null when this process was not admitted. */
export const inventoryPath = () => process.env.CODEMASTERY_E2E_INVENTORY || null;

/** Reads and validates the record. Returns null when absent; throws when present but untrusted. */
export function readInventory(path) {
    let handle;
    try {
        handle = openSync(path, 'r');
    } catch (error) {
        if (error.code === 'ENOENT') return null;
        throw new UntrustedInventory(`inventory ${path} cannot be opened: ${error.message}`);
    }
    let raw;
    try {
        const info = fstatSync(handle);
        if (!info.isFile() || info.nlink !== 1) throw new UntrustedInventory(`inventory ${path} is not a single-link ordinary file`);
        if (info.uid !== process.getuid()) throw new UntrustedInventory(`inventory ${path} is owned by uid ${info.uid}`);
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
    if ((record.intents?.length ?? 0) > MAX_INTENTS) throw new UntrustedInventory(`inventory ${path} exceeds the ${MAX_INTENTS} intent bound`);
    return record;
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
export function updateInventory(path, mutate) {
    const record = readInventory(path);
    if (!record) throw new UntrustedInventory(`inventory ${path} disappeared; this run's ownership cannot be recorded`);
    const replacement = mutate(record);
    const next = replacement && typeof replacement === 'object' && replacement.schema === SCHEMA ? replacement : record;
    return writeInventory(path, next);
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
