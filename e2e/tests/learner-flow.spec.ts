/**
 * Group A learner journey through the real frontend, REST API and PostgreSQL.
 * Runs against the disposable stack from fixtures/local-stack.ts (or E2E_BASE_URL).
 * No request is intercepted or mocked.
 */
import { readFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import { accountMenuName, expect, expectNoPageOverflow, fixture, newAccount, registerThroughApi, signIn, signOut, target, test, watchAuthorization } from '../fixtures/auth-fixture';

const desktop = (name: string) => name.endsWith('-desktop');

test.describe('catalogue and course detail (anonymous)', () => {
    test.skip(!target.fixture, 'Needs the deterministic catalogue fixture of the disposable stack.');

    test('lists only published courses, searches on submit, filters prerequisites and pages', async ({ page }) => {
        const auth = watchAuthorization(page);
        await page.goto('/courses');
        await expect(page.getByRole('heading', { level: 1, name: 'Course catalogue' })).toBeVisible();
        const list = page.getByRole('list', { name: 'Published courses' });
        await expect(list.getByRole('listitem')).toHaveCount(20);
        await expect(page.getByText(`${fixture.publishedCount} courses`, { exact: true })).toBeVisible();
        await expect(page.getByText(`Showing 1–20 of ${fixture.publishedCount} published courses`)).toBeVisible();
        await expect(page.getByText('E2E Hidden Course')).toHaveCount(0);

        await page.getByRole('button', { name: 'Go to page 2' }).click();
        await expect(list.getByRole('listitem')).toHaveCount(1);
        await expect(list.getByRole('heading', { name: fixture.longTitle })).toBeVisible();
        await expect(page.getByText(`Showing 21 of ${fixture.publishedCount} published courses`)).toBeVisible();
        await expectNoPageOverflow(page);

        // Search applies only when submitted.
        const search = page.getByRole('searchbox', { name: 'Search courses' });
        await search.fill('foundations');
        await expect(list.getByRole('listitem')).toHaveCount(1);
        await page.getByRole('button', { name: 'Search', exact: true }).click();
        await expect(list.getByRole('heading', { name: fixture.primaryTitle })).toBeVisible();
        await expect(page.getByText('1 course', { exact: true })).toBeVisible();

        // The prerequisite filter applies on selection, combined with the submitted search.
        await page.getByRole('button', { name: 'No prerequisites' }).click();
        await expect(page.getByRole('heading', { name: 'No courses match these filters' })).toBeVisible();
        await page.getByRole('button', { name: 'Clear filters' }).click();
        await expect(search).toHaveValue('');
        await expect(list.getByRole('listitem')).toHaveCount(20);
        await page.getByRole('button', { name: 'Prerequisites described' }).click();
        await expect(list.getByRole('listitem')).toHaveCount(1);
        await expect(page.getByRole('button', { name: 'Prerequisites described' })).toHaveAttribute('aria-pressed', 'true');

        expect(auth.length).toBeGreaterThan(0);
        expect(auth.every((entry) => !entry.bearer), 'public catalogue requests carry no bearer credential').toBe(true);
    });

    test('shows published syllabus only, facts and a sign-in action; hidden and unknown courses are unavailable', async ({ page }) => {
        await page.goto('/courses');
        await page.getByRole('link', { name: fixture.primaryTitle }).click();
        await expect(page).toHaveURL(new RegExp(`/courses/${fixture.primary}$`));
        await expect(page.getByRole('heading', { level: 1, name: fixture.primaryTitle })).toBeFocused();
        await expect(page.getByText('Instructor · E2E Course Instructor')).toBeVisible();
        const facts = page.getByRole('region', { name: 'Course facts' });
        await expect(facts.getByText('Comfortable with basic arithmetic.')).toBeVisible();
        await expect(facts.getByText('2 chapters · 2 lessons')).toBeVisible();

        const first = page.getByRole('button', { name: /Chapter 1: Getting Started/ });
        const second = page.getByRole('button', { name: /Chapter 2: Making Decisions/ });
        await expect(first).toHaveAttribute('aria-expanded', 'true');
        await expect(second).toHaveAttribute('aria-expanded', 'false');
        await expect(page.getByText('1. What a Program Is')).toBeVisible();
        await second.focus();
        await page.keyboard.press('Enter');
        await expect(second).toHaveAttribute('aria-expanded', 'true');
        await expect(page.getByText('1. If Statements')).toBeVisible();
        await page.keyboard.press('Space');
        await expect(second).toHaveAttribute('aria-expanded', 'false');
        await expect(page.getByText(/Draft Lesson Hidden From Learners|Hidden Chapter Lesson|Unreleased Chapter/)).toHaveCount(0);
        await expect(page.getByRole('link', { name: 'Sign in to enroll' })).toBeVisible();
        await expectNoPageOverflow(page);

        await page.getByRole('link', { name: 'Catalogue' }).first().click();
        await expect(page).toHaveURL(/\/courses$/);

        for (const id of [fixture.hidden, 'abcdefab-cdef-4abc-8def-abcdefabcdef']) {
            await page.goto(`/courses/${id}`);
            await expect(page.getByRole('heading', { level: 1, name: 'Course not available' })).toBeVisible();
            await expect(page.getByText("This course doesn't exist or isn't published.")).toBeVisible();
        }
    });

    test('keeps 100-character titles and 500-character descriptions inside their tiles', async ({ page }) => {
        await page.goto('/courses');
        const longDescription = page.getByText(fixture.longDescription.slice(0, 40), { exact: false });
        await expect(longDescription).toBeVisible();
        await page.getByRole('button', { name: 'Go to page 2' }).click();
        const title = page.getByRole('heading', { name: fixture.longTitle });
        await expect(title).toBeVisible();
        const geometry = await title.evaluate((element) => {
            const rect = element.getBoundingClientRect(), tile = element.closest('article')!.getBoundingClientRect();
            return { left: rect.left, right: rect.right, tileLeft: tile.left, tileRight: tile.right, scroll: element.scrollWidth, client: element.clientWidth };
        });
        expect(geometry.left).toBeGreaterThanOrEqual(geometry.tileLeft);
        expect(geometry.right).toBeLessThanOrEqual(geometry.tileRight);
        expect(geometry.scroll).toBeLessThanOrEqual(geometry.client + 1);
        await expectNoPageOverflow(page);
    });
});

test.describe('public landing and Learner start', () => {
    test('serves the landing page at / with public navigation and no API request', async ({ page }) => {
        const api = watchAuthorization(page);
        await page.goto('/');
        await expect(page.getByRole('heading', { level: 1, name: 'Learn programming at the pace of what you actually know.' })).toBeVisible();
        const nav = page.getByRole('navigation', { name: 'Main navigation' });
        await expect(nav.getByRole('link', { name: 'Catalogue' })).toHaveAttribute('href', '/courses');
        await expect(nav.getByRole('link', { name: 'Sign in' })).toHaveAttribute('href', '/login');
        await expect(page.getByText('Illustration · not learner data')).toBeVisible();
        await expectNoPageOverflow(page);
        expect(api).toEqual([]);
        await page.getByRole('link', { name: 'Browse courses' }).click();
        await expect(page).toHaveURL(/\/courses$/);
        await expect(page.getByRole('link', { name: 'CodeMastery home' })).toHaveAttribute('href', '/');
    });

    test('a Learner gets Home with real active memberships; the logo leads there', async ({ page, learner }) => {
        test.skip(!target.fixture, 'Needs the deterministic catalogue fixture of the disposable stack.');
        await signIn(page, learner);
        await expect(page.getByRole('link', { name: 'CodeMastery home' })).toHaveAttribute('href', '/home');
        await page.getByRole('link', { name: 'CodeMastery home' }).click();
        await expect(page).toHaveURL(/\/home$/);
        await expect(page.getByRole('heading', { level: 1, name: `Welcome back, ${learner.displayName}` })).toBeFocused();
        await expect(page.getByRole('heading', { name: "You haven't joined a course yet" })).toBeVisible();
        await expect(page.getByText(/Continue learning|Up next/)).toHaveCount(0);
        await page.getByRole('link', { name: 'Browse the catalogue' }).click();
        await page.getByRole('link', { name: fixture.primaryTitle }).click();
        await page.getByRole('button', { name: 'Enroll in course' }).click();
        await expect(page.getByText('Membership: Active')).toBeVisible();
        await page.getByRole('link', { name: 'CodeMastery home' }).click();
        const list = page.getByRole('list', { name: 'Your active courses' });
        await expect(list.getByRole('link', { name: fixture.primaryTitle })).toHaveAttribute('href', `/courses/${fixture.primary}`);
        await expect(list.getByText('Instructor · E2E Course Instructor')).toBeVisible();
        await expectNoPageOverflow(page);
    });

    test('says the session ended after the 15-minute expiry, once', async ({ page, learner }) => {
        await page.clock.install();
        await signIn(page, learner);
        await page.getByRole('button', { name: accountMenuName(learner) }).click();
        await page.getByRole('menuitem', { name: 'Profile' }).click();
        await expect(page).toHaveURL(/\/profile$/);
        await page.clock.fastForward('15:05');
        await expect(page).toHaveURL(/\/login$/);
        await expect(page.getByRole('status').filter({ hasText: 'Your session ended. Sign in again to continue.' })).toBeVisible();
        // Shown once: leaving and returning to Sign in does not repeat it. (Signing in again here would
        // compare the server's real-time token with the fast-forwarded browser clock, so it is not attempted.)
        await page.getByRole('link', { name: 'Browse courses' }).click();
        await expect(page).toHaveURL(/\/courses$/);
        await page.getByRole('link', { name: 'Sign in', exact: true }).first().click();
        await expect(page.getByRole('heading', { level: 1, name: 'Sign in' })).toBeVisible();
        await expect(page.getByText('Your session ended')).toHaveCount(0);
    });
});

test.describe('accounts and sessions', () => {
    test('registers through the form, then signs in; the session lives in memory only', async ({ page, context }) => {
        const account = newAccount('Registrant');
        await page.goto('/register');
        await expect(page.getByRole('heading', { level: 1, name: 'Create account' })).toBeVisible();
        await page.locator('#register-displayName').fill(account.displayName);
        await page.locator('#register-email').fill(account.email);
        await page.locator('#register-password').fill('short');
        await expect(page.getByRole('list', { name: 'Password rules' })).toBeVisible();
        await page.locator('#register-confirm').fill('short');
        await page.getByRole('button', { name: 'Create account', exact: true }).click();
        await expect(page).toHaveURL(/\/register$/);
        await expect(page.locator('#register-password')).toHaveAttribute('aria-invalid', 'true');

        await page.locator('#register-password').fill(account.password);
        await page.locator('#register-confirm').fill(account.password);
        await page.getByRole('button', { name: 'Create account', exact: true }).click();
        await expect(page.getByText('Account created. Sign in to continue.')).toBeVisible();

        // The same email cannot be registered twice; the API answers without creating a second account.
        const duplicate = await page.request.post(new URL('/api/auth/register', target.api).href, { data: account });
        expect(duplicate.status()).toBe(409);

        await signIn(page, account);
        const stored = await page.evaluate(() => ({ local: localStorage.length, session: sessionStorage.length }));
        expect(stored).toEqual({ local: 0, session: 0 });
        expect((await context.cookies()).length).toBe(0);
        await page.reload();
        await expect(page.getByRole('link', { name: 'Sign in', exact: true })).toBeVisible();
    });

    test('rejects wrong credentials with a safe message and keeps the form', async ({ page, learner }) => {
        await page.goto('/login');
        await page.locator('#login-email').fill(learner.email);
        await page.locator('#login-password').fill(`${learner.password}x`);
        await page.getByRole('button', { name: 'Sign in', exact: true }).click();
        await expect(page.getByRole('alert')).toBeVisible();
        await expect(page).toHaveURL(/\/login$/);
        await expect(page.locator('#login-email')).toHaveValue(learner.email);
    });

    test('shows the profile snapshot, signs out and guards private routes again', async ({ page, learner }) => {
        await signIn(page, learner);
        await page.getByRole('button', { name: accountMenuName(learner) }).click();
        await page.getByRole('menuitem', { name: 'Profile' }).click();
        await expect(page).toHaveURL(/\/profile$/);
        await expect(page.getByRole('heading', { level: 1, name: 'Profile' })).toBeFocused();
        const terms = await page.locator('main dl dt').allTextContents();
        expect(terms).toEqual(['Display name', 'Role', 'User ID']);
        await expect(page.locator('main dl')).toContainText(learner.displayName);
        await expect(page.locator('main dl')).toContainText('Learner');

        await signOut(page, learner);
        await expect(page.getByText('Your session ended')).toHaveCount(0);
        for (const path of ['/my-courses', '/profile']) {
            await page.goto(path);
            await expect(page).toHaveURL(/\/login$/);
            await expect(page.getByRole('heading', { level: 1, name: 'Sign in' })).toBeVisible();
        }
    });

    test('an Instructor sees access denied for My courses and the API refuses membership changes', async ({ page }) => {
        const instructor = target.instructor;
        test.skip(!instructor, 'Needs an Instructor account (created by the disposable stack).');
        await signIn(page, instructor!);
        await expect(page.getByRole('link', { name: 'My courses' })).toHaveCount(0);
        // In-app history navigation keeps the memory-only session (a typed URL would reload and sign out).
        await page.evaluate(() => { history.pushState(null, '', '/my-courses'); dispatchEvent(new PopStateEvent('popstate')); });
        await expect(page.getByRole('heading', { level: 1, name: "You don't have access to this page" })).toBeVisible();
        await expect(page.getByText("You're signed in as an Instructor. This page is for learners.")).toBeVisible();

        // Backend authorization is independent of the hidden link and the route guard.
        const login = await page.request.post(new URL('/api/auth/login', target.api).href, { data: { email: instructor!.email, password: instructor!.password } });
        const token = (await login.json()).accessToken as string;
        const denied = await page.request.put(new URL(`/api/courses/${fixture.primary}/membership`, target.api).href, { headers: { Authorization: `Bearer ${token}` } });
        expect(denied.status()).toBe(403);
    });

    test('unknown routes show the not-found state with a way back', async ({ page }) => {
        await page.goto('/no-such-page');
        await expect(page.getByRole('heading', { level: 1, name: 'Page not found' })).toBeVisible();
        await page.getByRole('link', { name: 'Browse courses' }).click();
        await expect(page).toHaveURL(/\/courses$/);
    });
});

test.describe('membership', () => {
    test.skip(!target.fixture, 'Needs the deterministic catalogue fixture of the disposable stack.');

    test('enrolls from course detail, leaves from the My courses tile menu and re-enrolls', async ({ page, learner }, testInfo) => {
        const auth = watchAuthorization(page);
        // The session is memory-only, so stay inside the app (no full reload) after signing in.
        await signIn(page, learner);
        await page.getByRole('link', { name: fixture.primaryTitle }).click();
        await expect(page).toHaveURL(new RegExp(`/courses/${fixture.primary}$`));
        await expect(page.getByText('Membership: Not enrolled')).toBeVisible();
        await page.getByRole('button', { name: 'Enroll in course' }).click();
        await expect(page.getByText('Membership: Active')).toBeVisible();

        await page.getByRole('link', { name: 'My courses' }).last().click();
        await expect(page).toHaveURL(/\/my-courses$/);
        await expect(page.getByRole('heading', { level: 1, name: 'My courses' })).toBeFocused();
        const courses = page.getByRole('list', { name: 'Your courses' });
        await expect(courses.getByRole('heading', { name: fixture.primaryTitle })).toBeVisible();
        await expect(courses.getByText('Membership: Active')).toBeVisible();

        const menu = page.getByRole('button', { name: `More actions for ${fixture.primaryTitle}` });
        await menu.click();
        await expect(page.getByRole('menuitem', { name: 'View course details' })).toBeVisible();
        await page.getByRole('menuitem', { name: 'Leave course' }).click();
        const dialog = page.getByRole('dialog', { name: 'Leave course?' });
        await expect(dialog).toContainText('Your learning history will be preserved. You can re-enroll while the course is published.');
        await expect(dialog.getByRole('button', { name: 'Cancel' })).toBeFocused();
        if (testInfo.project.name.endsWith('-mobile')) {
            const box = await dialog.boundingBox(), viewport = page.viewportSize()!;
            expect(box!.width).toBeGreaterThanOrEqual(viewport.width - 1);
        }
        await dialog.getByRole('button', { name: 'Cancel' }).click();
        await expect(menu).toBeFocused();
        await menu.click();
        await page.getByRole('menuitem', { name: 'Leave course' }).click();
        await page.getByRole('dialog', { name: 'Leave course?' }).getByRole('button', { name: 'Leave course' }).click();
        await expect(page.getByRole('status').filter({ hasText: `You left ${fixture.primaryTitle}.` })).toBeVisible();
        await expect(page.getByRole('heading', { name: "You haven't joined a course yet" })).toBeVisible();

        await page.getByRole('button', { name: 'Left', exact: true }).click();
        await expect(courses.getByText('Membership: Left')).toBeVisible();
        await page.getByRole('button', { name: `More actions for ${fixture.primaryTitle}` }).click();
        await page.getByRole('menuitem', { name: 'Re-enroll' }).click();
        await expect(page.getByRole('status').filter({ hasText: `You re-enrolled in ${fixture.primaryTitle}.` })).toBeVisible();
        await page.getByRole('button', { name: 'Joined', exact: true }).click();
        await expect(courses.getByText('Membership: Active')).toBeVisible();
        await expectNoPageOverflow(page);

        await courses.getByRole('link', { name: fixture.primaryTitle }).click();
        await expect(page.getByText('Membership: Active')).toBeVisible();
        await page.getByRole('button', { name: 'Leave course' }).click();
        await page.getByRole('dialog', { name: 'Leave course?' }).getByRole('button', { name: 'Leave course' }).click();
        await expect(page.getByText('Membership: Left')).toBeVisible();
        await page.getByRole('button', { name: 'Re-enroll' }).click();
        await expect(page.getByText('Membership: Active')).toBeVisible();

        const privateCalls = auth.filter((entry) => entry.path.endsWith('/membership') || entry.path === '/api/me/courses');
        const publicCalls = auth.filter((entry) => entry.path.startsWith('/api/courses') && !entry.path.endsWith('/membership'));
        expect(privateCalls.length).toBeGreaterThan(0);
        expect(privateCalls.every((entry) => entry.bearer), 'membership and My courses requests are authenticated').toBe(true);
        expect(publicCalls.every((entry) => !entry.bearer), 'catalogue requests stay anonymous').toBe(true);
    });

    test('My courses navigation, mobile drawer and keyboard skip link', async ({ page, learner }, testInfo) => {
        await signIn(page, learner);
        if (desktop(testInfo.project.name)) {
            const nav = page.getByRole('navigation', { name: 'Main navigation' });
            await expect(nav.getByRole('link', { name: 'Catalogue' })).toHaveAttribute('aria-current', 'page');
            await nav.getByRole('link', { name: 'My courses' }).click();
            await expect(nav.getByRole('link', { name: 'My courses' })).toHaveAttribute('aria-current', 'page');
        } else {
            const toggle = page.getByRole('button', { name: 'Toggle navigation' });
            await toggle.click();
            const drawer = page.getByRole('dialog');
            await expect(drawer.getByRole('link', { name: 'Catalogue' })).toHaveAttribute('aria-current', 'page');
            await page.keyboard.press('Escape');
            await expect(toggle).toBeFocused();
            await toggle.click();
            await page.getByRole('dialog').getByRole('link', { name: 'My courses' }).click();
        }
        await expect(page).toHaveURL(/\/my-courses$/);
        await expect(page.getByRole('heading', { level: 1, name: 'My courses' })).toBeVisible();
        await expectNoPageOverflow(page);
        // WebKit does not move Tab focus to links by default (a macOS setting), so the skip link is checked in Chromium and Firefox.
        if (desktop(testInfo.project.name) && testInfo.project.name !== 'webkit-desktop') {
            const skip = page.getByRole('link', { name: 'Skip to main content' });
            const first = await page.evaluate(() => document.querySelector('a[href], button:not([disabled]), input, [tabindex]:not([tabindex="-1"])')?.textContent);
            expect(first, 'the skip link is the first tab stop').toBe('Skip to main content');
            await skip.focus();
            const box = await skip.boundingBox();
            expect(box && box.y >= 0 && box.width > 0, 'the focused skip link is visible').toBe(true);
            await page.keyboard.press('Enter');
            await expect(page.locator('#main-content')).toBeFocused();
        }
    });
});

test.describe('REST contract smoke (API only)', () => {
    test('validates search input, page bounds and malformed identifiers without changing data', async ({ request }, testInfo) => {
        test.skip(testInfo.project.name !== 'chromium-desktop', 'API-only checks run once.');
        const api = (path: string) => new URL(path, target.api).href;
        const bad = await request.get(api('/api/courses?q=a%00b'));
        expect(bad.status()).toBe(400);
        expect(bad.headers()['content-type']).toMatch(/^application\/problem\+json/);
        for (const value of ['a%00b', '東京']) expect((await request.get(api(`/api/courses?q=${encodeURIComponent(value)}`))).status()).toBe(200);

        const spec = await readFile(fileURLToPath(new URL('../../docs/api/openapi.yaml', import.meta.url)), 'utf8');
        expect(spec).toContain('x-max-utf16-code-units');
        for (const [count, status] of [[50, 200], [51, 400]] as const) expect((await request.get(api(`/api/courses?q=${encodeURIComponent('😀'.repeat(count))}`))).status()).toBe(status);

        const account = newAccount('Probe');
        await registerThroughApi(request, account);
        const login = await request.post(api('/api/auth/login'), { data: { email: account.email, password: account.password } });
        expect(login.status()).toBe(200);
        const headers = { Authorization: `Bearer ${(await login.json()).accessToken}` };
        const malformed = api('/api/courses/0-0-4000-8000-2/membership');
        expect((await request.get(malformed)).status()).toBe(401);
        for (const method of ['GET', 'PUT', 'DELETE']) expect((await request.fetch(malformed, { method, headers })).status()).toBe(400);
        if (target.fixture) {
            const canonical = await request.get(api(`/api/courses/${fixture.primary}/membership`), { headers });
            expect(canonical.status()).toBe(200);
            expect((await canonical.json()).status).toBe('none');
            expect((await request.get(api(`/api/courses/${fixture.hidden}`))).status()).toBe(404);
            const far = await request.get(api('/api/courses?page=107374183&size=20'));
            expect(await far.json()).toEqual({ content: [], page: 107374183, size: 20, totalElements: fixture.publishedCount, totalPages: 2 });
        }
    });
});
