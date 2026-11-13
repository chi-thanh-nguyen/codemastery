// Regression tests for the persistent run inventory of ADR008 CT34-FIXTURES-2026-11-10-r2
// (Section 10.3): durable atomic replacement, the non-secret field bound, rejection of corrupted,
// oversized, foreign or unknown-schema records, and creation intents that stay pending — never
// "assumed absent" — when a crash lands between creating a resource and persisting its identity.
// Nothing here touches Docker, Vite, a browser or the real registry.
import { describe, test } from 'node:test';
import assert from 'node:assert/strict';
import { chmodSync, linkSync, lstatSync, mkdtempSync, readFileSync, readdirSync, rmSync, symlinkSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import {
    MAX_INTENTS, MAX_OBSERVATIONS, MAX_RECORD_BYTES, SCHEMA, UntrustedInventory,
    addIntent, admissionProblem, markerFile, observe, readInventory, readMarker, setIntent, updateInventory, writeInventory,
} from './run-inventory.mjs';

const scratch = (body) => {
    const dir = mkdtempSync(join(tmpdir(), 'codemastery-inventory-test-'));
    try {
        return body(dir, join(dir, 'run.json'));
    } finally {
        rmSync(dir, { recursive: true, force: true });
    }
};
const base = () => ({ schema: SCHEMA, contract: 'CT34-FIXTURES-2026-11-10-r2', runId: 'cm-e2e-test', state: 'active', ownerUid: process.getuid(), intents: [], processes: {}, observations: [] });

describe('the run inventory is durable, bounded and non-secret', () => {
    test('writes a 0600 single-link record and reads it back', () => scratch((dir, file) => {
        writeInventory(file, base());
        const info = lstatSync(file);
        assert.equal(info.mode & 0o777, 0o600);
        assert.equal(info.nlink, 1);
        assert.equal(readInventory(file).runId, 'cm-e2e-test');
        assert.ok(readInventory(file).updatedAt, 'every write stamps updatedAt');
    }));

    test('replaces atomically in the same directory and leaves no temporary file behind', () => scratch((dir, file) => {
        writeInventory(file, base());
        for (let round = 0; round < 5; round++) updateInventory(file, (record) => observe(record, `round ${round}`));
        assert.deepEqual(readdirSync(dir), ['run.json'], 'no partial or temporary record remains');
        assert.equal(readInventory(file).observations.length, 5);
    }));

    test('an absent record is null; a corrupt one is untrusted and never silently replaced', () => scratch((dir, file) => {
        assert.equal(readInventory(file), null);
        writeFileSync(file, '{ this is not json', { mode: 0o600 });
        assert.throws(() => readInventory(file), (error) => error instanceof UntrustedInventory && /corrupt/.test(error.message));
        assert.throws(() => updateInventory(file, (record) => record), UntrustedInventory);
        assert.equal(readFileSync(file, 'utf8'), '{ this is not json', 'the evidence is preserved as written');
    }));

    test('rejects an unknown schema, a foreign owner, an oversized record and an over-bounded intent list', () => scratch((dir, file) => {
        writeFileSync(file, JSON.stringify({ schema: 'something-else' }), { mode: 0o600 });
        assert.throws(() => readInventory(file), /unknown schema/);
        writeFileSync(file, JSON.stringify({ ...base(), ownerUid: process.getuid() + 1000 }), { mode: 0o600 });
        assert.throws(() => readInventory(file), /belongs to uid/);
        writeFileSync(file, JSON.stringify({ ...base(), intents: Array.from({ length: MAX_INTENTS + 1 }, (_, at) => ({ id: `i${at}` })) }), { mode: 0o600 });
        assert.throws(() => readInventory(file), /intent bound/);
        const huge = { ...base(), observations: [('x').repeat(MAX_RECORD_BYTES)] };
        assert.throws(() => writeInventory(file, huge), /refusing to write/);
    }));

    test('rejects a record whose mode was widened, that gained a second link or that is reached through a symlink', () => scratch((dir, file) => {
        writeInventory(file, base());
        chmodSync(file, 0o644);
        assert.throws(() => readInventory(file), /has mode 644, not 600/);
        chmodSync(file, 0o600);
        assert.ok(readInventory(file));
        linkSync(file, join(dir, 'second-link.json'));
        assert.throws(() => readInventory(file), /not a single-link ordinary file/);
        rmSync(join(dir, 'second-link.json'));
        symlinkSync(file, join(dir, 'alias.json'));
        assert.throws(() => readInventory(join(dir, 'alias.json')), /a symlink is never followed/);
    }));

    test('rejects malformed fields before any of them can reach Docker, a signal or a path', () => scratch((dir, file) => {
        const write = (patch) => writeFileSync(file, JSON.stringify({ ...base(), ...patch }), { mode: 0o600 });
        for (const [patch, what] of [
            [{ runId: '../../etc' }, /run ID/],
            [{ state: 'done' }, /state/],
            [{ processes: { preview: { pid: -1 } } }, /process preview PID/],
            [{ processes: { preview: { pid: 5, control: 7 } } }, /control channel/],
            [{ intents: [{ id: 'i1', kind: 'image', dockerId: 'x' }] }, /intent kind/],
            [{ intents: [{ id: 'i1', kind: 'container', dockerId: '--all' }] }, /intent dockerId/],
            [{ intents: [{ id: 'i1', kind: 'volume', name: 'a/b' }] }, /intent name/],
            [{ docker: { endpoint: 5 } }, /docker identity/],
            [{ temp: { path: null } }, /temporary build/],
        ]) {
            write(patch);
            assert.throws(() => readInventory(file), what, JSON.stringify(patch));
        }
    }));

    test('bounds observations instead of growing without limit', () => scratch((dir, file) => {
        const record = base();
        for (let at = 0; at < MAX_OBSERVATIONS + 20; at++) observe(record, `note ${at}`);
        assert.equal(record.observations.length, MAX_OBSERVATIONS);
        assert.match(record.observations.at(-1), /note \d+$/);
        observe(record, 'y'.repeat(1000));
        assert.ok(record.observations.at(-1).length < 340, 'each observation is bounded');
    }));
});

describe('creation intents survive a crash between creation and identity persistence', () => {
    test('an intent is pending until its observed identity is persisted', () => scratch((dir, file) => {
        writeInventory(file, base());
        let id = '';
        // The intent is written before the resource is created...
        updateInventory(file, (record) => { id = addIntent(record, { kind: 'container', name: 'cm-e2e-x-postgres' }); });
        const crashed = readInventory(file);
        assert.equal(crashed.intents[0].state, 'pending');
        assert.equal(crashed.intents[0].dockerId, undefined, 'no identity is assumed before it is observed');
        // ...and only afterwards carries the identity Docker actually reported.
        updateInventory(file, (record) => setIntent(record, id, { dockerId: 'deadbeef', state: 'created' }));
        assert.equal(readInventory(file).intents[0].dockerId, 'deadbeef');
    }));

    test('refuses an unknown resource state and an unrecorded intent id', () => scratch((dir, file) => {
        const record = base();
        assert.throws(() => addIntent(record, { kind: 'network', name: 'n', state: 'vanished' }), /unknown resource state/);
        const id = addIntent(record, { kind: 'network', name: 'n' });
        assert.throws(() => setIntent(record, 'i99', { state: 'removed' }), /not recorded/);
        assert.throws(() => setIntent(record, id, { state: 'deleted-maybe' }), /unknown resource state/);
    }));

    test('refuses to record more than the intent bound', () => {
        const record = base();
        for (let at = 0; at < MAX_INTENTS; at++) addIntent(record, { kind: 'container', name: `c${at}` });
        assert.throws(() => addIntent(record, { kind: 'container', name: 'one-too-many' }), /already recorded/);
    });

    test('the run marker of a temporary build is read exactly, and a missing one is null', () => scratch((dir) => {
        assert.equal(readMarker(dir), null);
        writeFileSync(markerFile(dir), 'cm-e2e-abc\n', { mode: 0o600 });
        assert.equal(readMarker(dir), 'cm-e2e-abc');
    }));
});

describe('only the coordinator\'s current hand-down admits a process', () => {
    const handDown = (dir, file, patch = {}) => {
        writeInventory(file, { ...base(), runId: 'cm-e2e-adm', lockKey: '1-2', processes: { supervisor: { pid: 4242 } }, ...patch });
        return {
            CODEMASTERY_E2E_OWNERSHIP: '1-2:cm-e2e-adm', CODEMASTERY_E2E_RUN_ID: 'cm-e2e-adm', CODEMASTERY_E2E_LOCK_KEY: '1-2',
            CODEMASTERY_E2E_INVENTORY: file, CODEMASTERY_E2E_SUPERVISOR: '4242',
        };
    };
    test('the coordinator\'s child with the active record is admitted', () => scratch((dir, file) => {
        assert.equal(admissionProblem({ env: handDown(dir, file), ppid: 4242 }), null);
    }));
    test('exported variables in another process, a finished run or a mismatched token are refused', () => scratch((dir, file) => {
        const env = handDown(dir, file);
        assert.match(admissionProblem({ env, ppid: 1 }), /not started by the coordinator/);
        assert.match(admissionProblem({ env: { ...env, CODEMASTERY_E2E_OWNERSHIP: '1-2:cm-e2e-other' }, ppid: 4242 }), /token does not name/);
        assert.match(admissionProblem({ env: {}, ppid: 4242 }), /no lifecycle ownership/);
        handDown(dir, file, { state: 'complete' });
        assert.match(admissionProblem({ env, ppid: 4242 }), /the run is complete, not active/);
    }));
    test('a configuration of another checkout is refused even with a valid hand-down', () => scratch((dir, file) => {
        const env = handDown(dir, file);
        assert.match(admissionProblem({ env, ppid: 4242, checkoutDir: dir }), /belongs to another checkout/);
        const info = lstatSync(dir);
        const own = handDown(dir, file, { lockKey: `${info.dev}-${info.ino}` });
        own.CODEMASTERY_E2E_LOCK_KEY = `${info.dev}-${info.ino}`;
        own.CODEMASTERY_E2E_OWNERSHIP = `${info.dev}-${info.ino}:cm-e2e-adm`;
        assert.equal(admissionProblem({ env: own, ppid: 4242, checkoutDir: dir }), null);
    }));
});
