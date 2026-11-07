// Removes containers and networks left by an interrupted disposable E2E run.
// It only touches resources labelled codemastery.e2e=disposable by fixtures/local-stack.ts, and skips any
// whose owning process (label codemastery.e2e.pid) is still running, so a concurrent run is never removed.
// Resources from older runs that carry no owner label are treated as leftovers.
// Each resource is inspected and removed on its own, so one that a concurrent teardown removes first counts as
// already gone instead of aborting the cleanup; one whose owner cannot be read is kept and reported.
import { spawnSync } from 'node:child_process';

const label = 'label=codemastery.e2e=disposable';
const TIMEOUT_MS = 30_000;
const docker = (args) => {
    const result = spawnSync('docker', args, { encoding: 'utf8', timeout: TIMEOUT_MS });
    const detail = (result.stderr || result.error?.message || `exit ${result.status}`).trim();
    return { ok: result.status === 0, stdout: result.stdout ?? '', detail };
};
/** Docker's answers for a resource that no longer exists (already cleaned up). */
const absent = (detail) => /no such (object|container|network)|network \S+ not found/i.test(detail);
const alive = (pid) => {
    if (!/^\d+$/.test(pid ?? '')) return false;
    try { process.kill(Number(pid), 0); return true; } catch (error) { return error.code === 'EPERM'; }
};
const counts = { removed: 0, kept: 0, absent: 0, unknown: 0, failed: 0 };
const warn = (message) => { console.warn(`warning: ${message}`); };

/** Lists, inspects and removes one kind of labelled resource. */
function clean(kind, list, inspect, labels, remove) {
    const listed = docker(list);
    if (!listed.ok) throw new Error(`docker ${list.join(' ')} failed: ${listed.detail}`);
    for (const id of listed.stdout.split('\n').map((line) => line.trim()).filter(Boolean)) {
        const owner = docker([...inspect, '--format', `{{index ${labels} "codemastery.e2e.pid"}}`, id]);
        if (!owner.ok) {
            if (absent(owner.detail)) { counts.absent++; continue; }
            // Ownership is unknown, so the resource may belong to a live run: keep it.
            counts.unknown++; warn(`kept ${kind} ${id}: its owner could not be read (${owner.detail})`); continue;
        }
        if (alive(owner.stdout.trim())) { counts.kept++; continue; }
        const removed = docker([...remove, id]);
        if (removed.ok) counts.removed++;
        else if (absent(removed.detail)) counts.absent++;
        else { counts.failed++; warn(`could not remove ${kind} ${id}: ${removed.detail}`); }
    }
}

// Containers first: a network cannot be removed while a container is still attached to it.
clean('container', ['ps', '-aq', '--filter', label], ['inspect', '--type', 'container'], '.Config.Labels', ['rm', '-f', '-v']);
clean('network', ['network', 'ls', '-q', '--filter', label], ['network', 'inspect'], '.Labels', ['network', 'rm']);
console.log(`Removed ${counts.removed}; kept ${counts.kept} owned by a running E2E process; ${counts.absent} already gone; `
    + `${counts.unknown} kept with an unreadable owner; ${counts.failed} failed.`);
if (counts.unknown || counts.failed) process.exitCode = 1;
