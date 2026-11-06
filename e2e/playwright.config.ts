import { defineConfig, devices } from '@playwright/test';

/**
 * CodeMastery end-to-end suite (Group A flows against the real REST API).
 *
 * By default fixtures/local-stack.ts starts a disposable stack built from this
 * checkout and removes it afterwards. Set E2E_BASE_URL to target a running
 * environment instead. Browsers are not downloaded by `npm ci`; install the pinned
 * revisions with `npx playwright install chromium firefox webkit` when missing.
 */
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
