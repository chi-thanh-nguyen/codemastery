import { dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { defineConfig, devices } from '@playwright/test';
import { admissionProblem } from './fixtures/run-inventory.mjs';

/**
 * CodeMastery end-to-end suite (Group A flows against the real REST API).
 *
 * By default fixtures/local-stack.ts starts a disposable stack built from this
 * checkout and removes it afterwards. Set E2E_BASE_URL to target a running
 * environment instead. Browsers are not downloaded by `npm ci`; install the pinned
 * revisions with `npx playwright install chromium firefox webkit` when missing.
 *
 * Run it with `npm test` / `npm run test:chromium` from e2e/. Those enter through
 * fixtures/lifecycle.py, which takes exclusive ownership of this checkout and of the
 * three output roots below, and persists the run inventory, *before* the Playwright
 * CLI starts (ADR008 CT34-FIXTURES-2026-11-10-r2 Section 10.2; a check inside
 * globalSetup would run after output cleanup may already have begun). The guard below
 * closes the direct-invocation bypass: this file is evaluated before Playwright touches
 * any output, so an unadmitted `playwright test` stops here with the outputs intact. In the
 * Playwright CLI process the hand-down is verified, not merely present: its parent must be the
 * recorded coordinator of this checkout's active run (see admissionProblem). Worker and loader
 * processes started by that verified CLI inherit its environment.
 */
const COMMAND = process.argv[2] ?? '';
/** Playwright invocations that write no output of their own and therefore need no ownership. */
const READ_ONLY = ['show-report', '--version', '-V', '--help', '-h', 'install', 'install-deps', 'uninstall'];
if (!READ_ONLY.includes(COMMAND)) {
    const problem = COMMAND
        ? admissionProblem({ checkoutDir: dirname(fileURLToPath(import.meta.url)) })
        : (process.env.CODEMASTERY_E2E_OWNERSHIP ? null : 'no lifecycle ownership was handed down to this process');
    if (problem) {
        throw new Error(`Refusing to run \`playwright ${COMMAND}\` without E2E lifecycle ownership (${problem}): it would touch `
            + `test-results/artifacts, test-results/results.json and test-results/html before this checkout is owned. `
            + 'Use `npm test` (or `npm run test:chromium`) from e2e/, which acquires exclusive ownership first; '
            + 'extra Playwright arguments can be appended after `--`. Nothing was started and no output was touched.');
    }
}

export default defineConfig({
    testDir: './tests',
    globalSetup: './fixtures/local-stack.ts',
    timeout: 60_000,
    expect: { timeout: 10_000 },
    fullyParallel: true,
    forbidOnly: !!process.env.CI,
    retries: 0,
    workers: process.env.E2E_WORKERS ? Number(process.env.E2E_WORKERS) : 3,
    outputDir: 'test-results/artifacts',
    reporter: [['list'], ['json', { outputFile: 'test-results/results.json' }], ['html', { outputFolder: 'test-results/html', open: 'never' }]],
    use: {
        trace: 'retain-on-failure',
        screenshot: 'only-on-failure',
        video: 'off',
        locale: 'en-US',
        timezoneId: 'UTC',
    },
    projects: [
        { name: 'chromium-desktop', use: { ...devices['Desktop Chrome'], viewport: { width: 1280, height: 900 } } },
        { name: 'firefox-desktop', use: { ...devices['Desktop Firefox'], viewport: { width: 1280, height: 900 } } },
        { name: 'webkit-desktop', use: { ...devices['Desktop Safari'], viewport: { width: 1280, height: 900 } } },
        { name: 'chromium-mobile', use: { ...devices['Pixel 7'] } },
        { name: 'webkit-mobile', use: { ...devices['iPhone 14'] } },
    ],
});
