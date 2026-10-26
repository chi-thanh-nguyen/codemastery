// Local FE07 browser verification with an explicitly supplied Playwright installation.
// Run via stdin as documented in docs/testing.md; no repository dependency or mocks.
import assert from 'node:assert/strict';
import { createRequire } from 'node:module';
import { randomUUID } from 'node:crypto';
import { mkdir, writeFile } from 'node:fs/promises';
const [modulePath, frontend, backend, output] = process.argv.slice(2);
assert(modulePath && frontend && backend && output, 'Supply Playwright module, frontend URL, backend URL and evidence directory.');
const { chromium } = createRequire(`${process.cwd()}/package.json`)(modulePath);
await mkdir(output, { recursive: true });
const browser = await chromium.launch({ headless: true });
const context = await browser.newContext({ viewport: { width: 1280, height: 900 } });
const page = await context.newPage();
const checks = [], errors = [], privateHeaders = [], publicHeaders = [];
page.on('pageerror', (error) => errors.push(String(error)));
page.on('request', (request) => {
    const url = new URL(request.url());
    if (url.origin !== backend || !url.pathname.startsWith('/api/')) return;
    if (url.pathname.endsWith('/membership') || url.pathname === '/api/me/courses') privateHeaders.push(!!request.headers().authorization);
    else publicHeaders.push(!!request.headers().authorization);
});
async function check(name, action) { await action(); checks.push(name); console.log('PASS', name); }
async function visibleText(text) { await page.getByText(text, { exact: true }).waitFor({ state: 'visible' }); }
try {
    await page.goto(frontend + '/courses');
    await check('real published catalogue, search and prerequisite filtering', async () => {
        await page.getByRole('heading', { name: 'Course Catalogue' }).waitFor();
        await page.getByLabel('Search courses').fill('FE07');
        await page.getByRole('button', { name: 'Apply filters' }).click();
        await page.getByRole('heading', { name: 'FE07 Programming', exact: true }).waitFor();
        assert.equal(await page.getByRole('heading', { name: 'FE07 Hidden', exact: true }).count(), 0);
        await page.getByLabel('Prerequisites', { exact: true }).click();
        await page.getByRole('option', { name: 'Prerequisites described' }).click();
        await page.getByRole('button', { name: 'Apply filters' }).click();
        await visibleText('Showing 1 of 1 published courses');
    });
    await check('real detail and published-only syllabus with instructor presentation', async () => {
        await page.getByRole('button', { name: 'View Syllabus' }).click();
        await page.getByRole('heading', { name: 'FE07 Programming', exact: true }).waitFor();
        await visibleText('Instructor: FE07 Runtime Instructor');
        await visibleText('1. Visible lesson');
        assert.equal(await page.getByText('Hidden lesson').count(), 0);
        await page.getByRole('link', { name: 'Sign in to enroll' }).waitFor();
    });
    const coursePath = new URL(page.url()).pathname;
    const password = 'FE07-' + randomUUID() + '!';
    const email = `fe07-${randomUUID()}@example.test`;
    await check('real registration, login and guarded My Courses', async () => {
        await page.goto(frontend + '/my-courses');
        await page.waitForURL(frontend + '/login');
        await page.getByRole('link', { name: 'Create an account' }).click();
        await page.getByLabel('Display name').fill('FE07 Browser Learner');
        await page.getByLabel('Email address').fill(email);
        await page.getByLabel(/^Password/).fill(password);
        await page.getByLabel('Confirm password').fill(password);
        await page.getByRole('button', { name: 'Register', exact: true }).click();
        await page.getByRole('link', { name: 'Proceed to sign in' }).click();
        await page.getByLabel('Email address').fill(email);
        await page.getByLabel(/^Password/).fill(password);
        await page.getByRole('button', { name: 'Sign in', exact: true }).click();
        await page.waitForURL(frontend + '/courses');
        await page.goto(frontend + coursePath);
        // A full reload intentionally drops the memory-only session.
        await page.getByRole('link', { name: 'Sign in to enroll' }).waitFor();
        await page.getByRole('link', { name: 'Sign in to enroll' }).click();
        await page.getByLabel('Email address').fill(email);
        await page.getByLabel(/^Password/).fill(password);
        await page.getByRole('button', { name: 'Sign in', exact: true }).click();
        await page.waitForURL(frontend + '/courses');
        await page.getByLabel('Search courses').fill('FE07');
        await page.getByRole('button', { name: 'Apply filters' }).click();
        await visibleText('Showing 1 of 1 published courses');
        await page.getByRole('button', { name: 'View Syllabus' }).click();
        await visibleText('Membership: none');
    });
    await check('real enrolment, My Courses, leave confirmation, removal and re-enrolment', async () => {
        await page.getByRole('button', { name: 'Enroll in course', exact: true }).click();
        await visibleText('Membership: active');
        await page.getByRole('main').getByRole('link', { name: 'My Courses', exact: true }).click();
        await page.getByRole('heading', { name: 'My Courses', exact: true }).waitFor();
        await page.getByRole('heading', { name: 'FE07 Programming', exact: true }).waitFor();
        await page.getByRole('link', { name: 'View course and membership' }).click();
        await page.getByRole('button', { name: 'Leave course', exact: true }).click();
        const dialog = page.getByRole('dialog', { name: 'Leave course?' });
        await dialog.waitFor(); await page.getByRole('button', { name: 'Cancel', exact: true }).click();
        await visibleText('Membership: active');
        await page.getByRole('button', { name: 'Leave course', exact: true }).click();
        await dialog.getByRole('button', { name: 'Leave course', exact: true }).click();
        await visibleText('Membership: left');
        await page.getByRole('main').getByRole('link', { name: 'My Courses', exact: true }).click();
        await page.getByRole('heading', { name: 'No courses found' }).waitFor();
        await page.getByLabel('Membership status').click(); await page.getByRole('option', { name: 'Left', exact: true }).click();
        await page.getByRole('link', { name: 'View course and membership' }).click();
        await page.getByRole('button', { name: 'Re-enroll', exact: true }).click();
        await visibleText('Membership: active');
    });
    await check('responsive layout and accessible membership controls', async () => {
        for (const width of [1280, 768, 390, 320]) {
            await page.setViewportSize({ width, height: 900 });
            await page.waitForTimeout(100);
            assert(await page.evaluate(() => document.documentElement.scrollWidth <= document.documentElement.clientWidth), `Overflow at ${width}`);
            await page.getByRole('button', { name: 'Leave course', exact: true }).waitFor();
            await page.screenshot({ path: `${output}/membership-${width}.png`, fullPage: true });
        }
        await page.setViewportSize({ width: 1280, height: 900 });
    });
    await check('memory-only sessions, sign-out and protected navigation', async () => {
        const storage = await page.evaluate(() => ({ local: Object.keys(localStorage), session: Object.keys(sessionStorage), cookies: document.cookie }));
        assert.deepEqual(storage, { local: [], session: [], cookies: '' });
        await page.getByRole('button', { name: 'Sign out', exact: true }).click();
        await page.waitForURL(frontend + '/login');
        await page.goto(frontend + '/my-courses'); await page.waitForURL(frontend + '/login');
        assert(!await page.getByText('FE07 Browser Learner', { exact: true }).count());
    });
    await check('catalogue pagination, hidden 404 and preserved independent FE01 preview', async () => {
        await page.goto(frontend + '/courses'); await visibleText('Showing 20 of 21 published courses');
        await page.getByRole('button', { name: 'Go to page 2' }).click(); await visibleText('Showing 1 of 21 published courses');
        const response = await page.request.get(backend + '/api/courses/00000000-0000-4000-8000-000000000099'); assert.equal(response.status(), 404);
        await page.goto(frontend + '/courses/00000000-0000-4000-8000-000000000099'); await page.getByText('The requested resource is unavailable.', { exact: true }).waitFor();
        await page.goto(frontend + '/preview'); await page.getByRole('heading', { name: 'Your learning, in focus.' }).waitFor();
        await page.getByText('All learning data below is illustrative. Nothing is saved.', { exact: true }).waitFor();
    });
    assert(privateHeaders.length > 0 && privateHeaders.every(Boolean), 'Every membership request uses the private transport');
    assert(publicHeaders.every((value) => !value), 'Public operations never receive a bearer credential');
    assert.deepEqual(errors, []);
    await writeFile(`${output}/result.json`, JSON.stringify({ checks, browserErrors: errors, authenticatedRequests: privateHeaders.length, publicRequests: publicHeaders.length }, null, 2));
} finally { await browser.close(); }
