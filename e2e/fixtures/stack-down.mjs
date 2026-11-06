// Removes containers and networks left by an interrupted disposable E2E run.
// It only touches resources labelled codemastery.e2e=disposable by fixtures/local-stack.ts.
import { execFileSync } from 'node:child_process';

const label = 'label=codemastery.e2e=disposable';
const lines = (args) => execFileSync('docker', args, { encoding: 'utf8' }).split('\n').map((line) => line.trim()).filter(Boolean);

const containers = lines(['ps', '-aq', '--filter', label]);
if (containers.length) execFileSync('docker', ['rm', '-f', '-v', ...containers], { stdio: 'inherit' });
const networks = lines(['network', 'ls', '-q', '--filter', label]);
if (networks.length) execFileSync('docker', ['network', 'rm', ...networks], { stdio: 'inherit' });
console.log(`Removed ${containers.length} container(s) and ${networks.length} network(s).`);
