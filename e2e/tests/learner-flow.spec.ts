// Local FE07 browser verification with an explicitly supplied Playwright installation.
// Run via stdin as documented in docs/testing.md; no repository dependency or mocks.
import assert from 'node:assert/strict';
import { createRequire } from 'node:module';
import { randomUUID } from 'node:crypto';
import { mkdir, readFile, writeFile } from 'node:fs/promises';
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
    await check('NUL search returns safe 400 while literal percent text and Unicode remain valid', async () => {
        const bad = await page.request.get(backend + '/api/courses?q=a%00b');
        assert.equal(bad.status(), 400); assert(bad.headers()['content-type'].startsWith('application/problem+json'));
        const problem = await bad.json(); assert.equal(problem.detail, 'The request is invalid.'); assert.equal(problem.instance, '/api/courses');
        for (const value of ['a%00b', '東京']) assert.equal((await page.request.get(backend + '/api/courses?q=' + encodeURIComponent(value))).status(), 200);
    });
    await check('OpenAPI and real search requests use 100 UTF-16 units: 50 emoji accepted, 51 rejected', async () => {
        const spec = JSON.parse(await readFile('docs/api/openapi.yaml', 'utf8'));
        const query = spec.paths['/api/courses'].get.parameters.find((parameter) => parameter.name === 'q');
        assert(!Object.hasOwn(query.schema, 'maxLength')); assert.equal(query.schema['x-max-utf16-code-units'], 100);
        assert.equal('😀'.repeat(50).length, 100); assert.equal('😀'.repeat(51).length, 102);
        for (const [count, status] of [[50, 200], [51, 400]]) assert.equal((await page.request.get(backend + '/api/courses?q=' + encodeURIComponent('😀'.repeat(count)))).status(), status);
    });
    await check('malformed UUID membership requests are rejected without changing canonical membership', async () => {
        const credentials = { email: `r3-${randomUUID()}@example.test`, password: 'Probe-' + randomUUID() + '!' };
        assert.equal((await page.request.post(backend + '/api/auth/register', { data: { ...credentials, displayName: 'Round Three Probe' } })).status(), 201);
        const login = await page.request.post(backend + '/api/auth/login', { data: credentials }); assert.equal(login.status(), 200);
        const headers = { Authorization: 'Bearer ' + (await login.json()).accessToken };
        const bad = backend + '/api/courses/0-0-4000-8000-2/membership';
        assert.equal((await page.request.get(bad)).status(), 401);
        for (const method of ['GET', 'PUT', 'DELETE']) assert.equal((await page.request.fetch(bad, { method, headers })).status(), 400);
        assert.equal((await page.request.get(backend + '/api/courses/0-0-4000-8000-2')).status(), 400);
        const canonical = await page.request.get(backend + '/api/courses/00000000-0000-4000-8000-000000000002/membership', { headers });
        assert.equal(canonical.status(), 200); assert.equal((await canonical.json()).status, 'none');
        const missing = '/api/courses/aBcDeFaB-cDeF-4aBc-8DeF-aBcDeFaBcDeF';
        assert.equal((await page.request.get(backend + missing)).status(), 404);
        assert.equal((await page.request.get(backend + missing + '/membership', { headers })).status(), 404);
    });
    await check('approved large-page REST request returns 200 and accurate empty-page metadata', async () => {
        const response = await page.request.get(backend + '/api/courses?page=107374183&size=20');
        assert.equal(response.status(), 200);
        assert.deepEqual(await response.json(), { content: [], page: 107374183, size: 20, totalElements: 21, totalPages: 2 });
    });
    await page.goto(frontend + '/courses');
    await check('course-card title/instructor text and navigation remain accessible in all nine responsive cases', async () => {
        const long = 'W'.repeat(100), measurements = [], failures = [];
        const variants = [
            { name: 'title-only', title: long, instructor: 'Instructor: FE07 Runtime Instructor', id: '00000000-0000-4000-8001-000000000001', query: long, count: 2 },
            { name: 'instructor-only', title: 'Long instructor fixture', instructor: 'Instructor: ' + long, id: '00000000-0000-4000-8001-000000000002', query: 'Long instructor fixture', count: 1 },
            { name: 'both-long', title: long, instructor: 'Instructor: ' + long, id: '00000000-0000-4000-8001-000000000003', query: long, count: 2 },
        ];
        async function geometry(locator) {
            return locator.evaluate((element) => {
                const rect = element.getBoundingClientRect(), card = element.closest('.MuiCard-root').getBoundingClientRect();
                const range = document.createRange(); range.selectNodeContents(element);
                const fragments = [...range.getClientRects()].filter((part) => part.width > 0 && part.height > 0);
                const style = getComputedStyle(element);
                return { text: element.textContent, clientWidth: element.clientWidth, scrollWidth: element.scrollWidth,
                    clientHeight: element.clientHeight, scrollHeight: element.scrollHeight,
                    rect: { left: rect.left, right: rect.right, top: rect.top, bottom: rect.bottom },
                    card: { left: card.left, right: card.right, top: card.top, bottom: card.bottom },
                    fragments: fragments.map((part) => ({ left: part.left, right: part.right, top: part.top, bottom: part.bottom })),
                    lines: new Set(fragments.map((part) => part.top.toFixed(2))).size,
                    overflowWrap: style.overflowWrap, overflowX: style.overflowX, overflowY: style.overflowY,
                    textOverflow: style.textOverflow, lineClamp: style.webkitLineClamp };
            });
        }
        for (const variant of variants) for (const width of [320, 768, 1280]) {
            await page.setViewportSize({ width, height: 900 });
            await page.getByRole('heading', { name: 'Course Catalogue', exact: true }).waitFor();
            await page.getByLabel('Search courses').fill(variant.query);
            await page.getByRole('button', { name: 'Apply filters', exact: true }).click();
            await visibleText(`Showing ${variant.count} of ${variant.count} published courses`);
            const card = page.locator('.MuiCard-root').filter({ has: page.getByRole('heading', { name: variant.title, exact: true }) })
                .filter({ has: page.getByText(variant.instructor, { exact: true }) });
            assert.equal(await card.count(), 1);
            await page.evaluate(() => document.fonts.ready);
            await page.evaluate(() => new Promise((done) => requestAnimationFrame(() => requestAnimationFrame(done))));
            const title = await geometry(card.getByRole('heading', { name: variant.title, exact: true }));
            const instructor = await geometry(card.getByText(variant.instructor, { exact: true }));
            measurements.push({ variant: variant.name, width, title, instructor });
            for (const [field, measured, expected] of [['title', title, variant.title], ['instructor', instructor, variant.instructor]]) {
                const label = `${variant.name}/${width}/${field}`;
                if (measured.text !== expected) failures.push(`${label}: incomplete text`);
                if (!(measured.clientWidth > 0 && measured.scrollWidth <= measured.clientWidth + 1 && measured.scrollHeight <= measured.clientHeight + 1)) failures.push(`${label}: element overflow`);
                if (!measured.fragments.every((part) => part.left >= measured.rect.left - 1 && part.right <= measured.rect.right + 1 && part.top >= measured.rect.top - 1 && part.bottom <= measured.rect.bottom + 1)) failures.push(`${label}: text outside element`);
                if (!(measured.rect.left >= measured.card.left - 1 && measured.rect.right <= measured.card.right + 1 && measured.rect.top >= measured.card.top - 1 && measured.rect.bottom <= measured.card.bottom + 1 && measured.card.left >= 0 && measured.card.right <= width + 1)) failures.push(`${label}: clipped by card or viewport`);
                if (['hidden', 'clip', 'scroll', 'auto'].includes(measured.overflowX) || ['hidden', 'clip', 'scroll', 'auto'].includes(measured.overflowY) || measured.textOverflow === 'ellipsis' || Number.parseInt(measured.lineClamp) > 0) failures.push(`${label}: truncation or scrolling workaround`);
                if (expected.includes(long) && measured.lines <= 1) failures.push(`${label}: unbroken text did not wrap`);
            }
            const description = await card.getByText('Pagination fixture', { exact: true }).evaluate((element) => ({ clamp: getComputedStyle(element).webkitLineClamp, overflow: getComputedStyle(element).overflow }));
            assert.equal(description.clamp, '3'); assert.equal(description.overflow, 'hidden');
            if (variant.name === 'both-long') await page.screenshot({ path: `${output}/course-card-${width}.png`, fullPage: true });
            await card.getByRole('button', { name: 'View Syllabus', exact: true }).click();
            await page.waitForURL(frontend + '/courses/' + variant.id);
            await page.getByRole('heading', { level: 1, name: variant.title, exact: true }).waitFor();
            await page.getByRole('button', { name: 'Back to Catalogue', exact: true }).click();
        }
        await writeFile(`${output}/course-card-text.json`, JSON.stringify({ measurements, failures }, null, 2));
        assert.deepEqual(failures, []);
        await page.setViewportSize({ width: 1280, height: 900 });
    });
    await check('real published catalogue, search and prerequisite filtering', async () => {
        await page.getByRole('heading', { name: 'Course Catalogue' }).waitFor();
        const search = page.getByLabel('Search courses');
        await search.fill('😀'.repeat(50)); await search.press('End'); await search.pressSequentially('😀');
        assert.equal(await search.inputValue(), '😀'.repeat(50));
        await search.fill('FE07');
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
    await check('500-character unbroken description remains accessible at 320px, tablet and desktop', async () => {
        const description = page.getByText('D'.repeat(500), { exact: true });
        await description.waitFor();
        for (const width of [320, 768, 1280]) {
            await page.setViewportSize({ width, height: 900 });
            const measured = await description.evaluate((element) => {
                const rect = element.getBoundingClientRect(), range = document.createRange();
                range.selectNodeContents(element);
                const lines = [...range.getClientRects()];
                return { clientWidth: element.clientWidth, scrollWidth: element.scrollWidth,
                    text: element.textContent, lineCount: lines.length,
                    contained: lines.every((line) => line.left >= rect.left - 1 && line.right <= rect.right + 1 && line.bottom <= rect.bottom + 1),
                    hidden: ['hidden', 'clip', 'scroll', 'auto'].includes(getComputedStyle(element).overflowX) };
            });
            assert.equal(measured.text, 'D'.repeat(500));
            assert(measured.clientWidth > 0 && measured.scrollWidth <= measured.clientWidth + 1, `Description overflow at ${width}`);
            assert(measured.contained && measured.lineCount > 1 && !measured.hidden, `Description must wrap without clipping at ${width}`);
            await page.screenshot({ path: `${output}/long-description-${width}.png`, fullPage: true });
        }
        await page.setViewportSize({ width: 1280, height: 900 });
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
