// Removes containers and networks left by an interrupted disposable E2E run.
// It only touches resources labelled codemastery.e2e=disposable by fixtures/local-stack.ts, and skips any
// whose owning process (label codemastery.e2e.pid) is still running, so a concurrent run is never removed.
// Resources from older runs that carry no owner label are treated as leftovers.
import { execFileSync } from 'node:child_process';

const label = 'label=codemastery.e2e=disposable';
const lines = (args) => execFileSync('docker', args, { encoding: 'utf8' }).split('\n').map((line) => line.trim()).filter(Boolean);
const alive = (pid) => {
    if (!/^\d+$/.test(pid ?? '')) return false;
    try { process.kill(Number(pid), 0); return true; } catch (error) { return error.code === 'EPERM'; }
};
/** IDs of labelled resources whose owner is gone, plus the count of live ones kept. Each inspect line is "<id> <pid>". */
const orphans = (list, inspect, labels) => {
    const ids = lines(list);
    if (!ids.length) return { remove: [], kept: 0 };
    const owners = new Map(lines([...inspect, '--format', `{{.Id}} {{index ${labels} "codemastery.e2e.pid"}}`, ...ids]).map((line) => line.split(' ')));
    const ownerOf = (id) => [...owners].find(([full]) => full.startsWith(id))?.[1];
    const remove = ids.filter((id) => !alive(ownerOf(id)));
    return { remove, kept: ids.length - remove.length };
};

const containers = orphans(['ps', '-aq', '--filter', label], ['inspect'], '.Config.Labels');
if (containers.remove.length) execFileSync('docker', ['rm', '-f', '-v', ...containers.remove], { stdio: 'inherit' });
const networks = orphans(['network', 'ls', '-q', '--filter', label], ['network', 'inspect'], '.Labels');
if (networks.remove.length) execFileSync('docker', ['network', 'rm', ...networks.remove], { stdio: 'inherit' });
console.log(`Removed ${containers.remove.length} container(s) and ${networks.remove.length} network(s); kept ${containers.kept + networks.kept} owned by a running E2E process.`);
