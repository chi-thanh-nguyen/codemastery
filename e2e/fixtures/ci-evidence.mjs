// Sanitized, informational evidence for the CI E2E job (ADR008 CT34-CI-2026-11-09-r1, Section 7).
//
// Writes Markdown to stdout from an allowlist of facts only: the Playwright and browser revisions
// pinned by the lockfile, per-project test counts and the titles of failed or skipped tests from
// test-results/results.json, and non-secret fields of the run inventory (run ID, final state, image
// IDs and per-resource outcomes). It never copies error messages, attachments, traces, request
// bodies, storage state, environment values or log output: those can carry the run's synthetic
// credentials and session material, and redacting known variables would not sanitize them.
// It always exits 0; the test and cleanup steps are the gate.
import { spawnSync } from 'node:child_process';
import { existsSync, readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { createRequire } from 'node:module';
import { fileURLToPath } from 'node:url';

const e2e = join(dirname(fileURLToPath(import.meta.url)), '..');
const require = createRequire(join(e2e, 'package.json'));
const PROJECTS = ['chromium-desktop', 'firefox-desktop', 'webkit-desktop', 'chromium-mobile', 'webkit-mobile'];
/** A literal code span that no title can break out of: backticks, pipes and newlines are neutralized. */
const code = (text) => `\`${String(text ?? '').replace(/[`|\r\n]/g, ' ').slice(0, 200)}\``;
const out = [];
const line = (text = '') => out.push(text);
const read = (path) => { try { return JSON.parse(readFileSync(path, 'utf8')); } catch { return null; } };

line('### E2E test evidence');
line();
line('| Item | Value |');
line('| --- | --- |');
let playwright = 'unavailable';
try { playwright = require('@playwright/test/package.json').version; } catch { /* reported as unavailable */ }
line(`| \`@playwright/test\` (lockfile) | ${code(playwright)} |`);
try {
    // browsers.json is not in playwright-core's exports map, so it is read next to the resolved manifest.
    const browsers = JSON.parse(readFileSync(join(dirname(require.resolve('playwright-core/package.json')), 'browsers.json'), 'utf8')).browsers
        .filter((entry) => ['chromium', 'chromium-headless-shell', 'firefox', 'webkit'].includes(entry.name));
    for (const entry of browsers) line(`| Pinned browser ${code(entry.name)} | revision ${code(entry.revision)}, version ${code(entry.browserVersion ?? 'n/a')} |`);
} catch {
    line('| Pinned browsers | unavailable |');
}

const inventory = spawnSync('python3', [join(e2e, 'fixtures', 'lifecycle.py'), 'inspect'], { encoding: 'utf8', cwd: e2e });
const record = inventory.status === 0 ? (() => { try { return JSON.parse(inventory.stdout).record; } catch { return null; } })() : null;
if (record) {
    line(`| Run ID, mode | ${code(record.runId)}, ${code(record.mode ?? 'unknown')} |`);
    line(`| Final inventory state | ${code(record.state)} |`);
    for (const intent of (record.intents ?? []).filter((entry) => entry.kind === 'container')) {
        line(`| ${code(intent.role ?? intent.name)} image | ${code(intent.image ?? 'not recorded')} |`);
    }
    const outcomes = {};
    for (const intent of record.intents ?? []) outcomes[`${intent.kind} ${intent.state}`] = (outcomes[`${intent.kind} ${intent.state}`] ?? 0) + 1;
    line(`| Resource outcomes | ${Object.entries(outcomes).map(([key, total]) => code(`${total} ${key}`)).join(', ') || 'none recorded'} |`);
    for (const [role, entry] of Object.entries(record.processes ?? {})) line(`| Process ${code(role)} | ${code(entry.state)} |`);
} else {
    line('| Run inventory | unavailable: no record could be read, so no cleanup outcome is claimed |');
}
line();

const results = read(join(e2e, 'test-results', 'results.json'));
if (!results?.suites) {
    line('No `test-results/results.json` was written, so no test counts are claimed (setup may have failed before any test ran).');
} else {
    const tally = Object.fromEntries(PROJECTS.map((name) => [name, { passed: 0, failed: 0, flaky: 0, skipped: 0 }]));
    const notable = [];
    const walk = (suite, trail) => {
        for (const spec of suite.specs ?? []) {
            for (const entry of spec.tests ?? []) {
                const bucket = tally[entry.projectName] ??= { passed: 0, failed: 0, flaky: 0, skipped: 0 };
                const kind = entry.status === 'expected' ? 'passed' : entry.status === 'unexpected' ? 'failed' : entry.status === 'flaky' ? 'flaky' : 'skipped';
                bucket[kind]++;
                // A skip's reason is the test's own authored annotation, not runtime output.
                const reason = kind === 'skipped' ? (entry.annotations ?? []).find((note) => note.type === 'skip')?.description : null;
                if (kind !== 'passed') notable.push(`| ${code(entry.projectName)} | ${kind}${reason ? ` (${code(reason)})` : ''} | ${code([...trail, spec.title].filter(Boolean).join(' › '))} |`);
            }
        }
        for (const child of suite.suites ?? []) walk(child, [...trail, child.title]);
    };
    for (const suite of results.suites) walk(suite, [suite.title]);
    const discovered = (results.config?.projects ?? []).map((project) => project.name);
    line(`Projects configured: ${discovered.map(code).join(', ') || 'unknown'}${PROJECTS.every((name) => discovered.includes(name)) ? '' : ' — **not all five required projects are configured**'}.`);
    line();
    line('| Project | Passed | Failed | Flaky | Skipped |');
    line('| --- | --- | --- | --- | --- |');
    for (const [name, counts] of Object.entries(tally)) line(`| ${code(name)} | ${counts.passed} | ${counts.failed} | ${counts.flaky} | ${counts.skipped} |`);
    const total = Object.values(tally).reduce((sum, counts) => sum + counts.passed + counts.failed + counts.flaky + counts.skipped, 0);
    line();
    line(`Total test results: ${total}. Run-level errors reported by Playwright: ${(results.errors ?? []).length} (messages are in the test step log, not copied here).`);
    if (total === 0) line('**Zero tests ran: this is not coverage.**');
    if (notable.length) {
        line();
        line('| Project | Outcome | Test |');
        line('| --- | --- | --- |');
        out.push(...notable.slice(0, 200));
    }
}
line();
line('Raw reports, traces and screenshots are not published: they can contain the run\'s synthetic credentials and session material. '
    + 'This E2E infrastructure check is not CT19/CT35/CT20.F Final main-flow acceptance. This summary is informational; the job result is the gate.');
process.stdout.write(`${out.join('\n')}\n`);
