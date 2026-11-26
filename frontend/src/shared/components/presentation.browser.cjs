#!/usr/bin/env node
// Optional real-browser presentation harness for the "Lit Path" UI (DESIGN-2026-11-06-r1; FE34 colour
// schemes and the Theme control per DESIGN-2026-11-19-r12).
//
// What it covers: every /preview screen (S5–S26 sample data), the live Group A pages with
// CONTROLLED API responses (page.route test doubles, never a backend), layout at ten widths
// and 200% zoom, the self-hosted font, contrast, focus visibility and order, skip link,
// drawer, dialogs, reduced motion, skeleton motion, the Mastery Map graph and production/
// preview chunk isolation. It writes screenshots, accessibility snapshots and a JSON report.
// It does not prove backend integration (see e2e/), screen-reader output or WCAG conformance.
//
// Usage (from frontend/):
//   npm run test:browser -- --fe32 --fixture-url <isolated-fixture-url> --url <owned-production-url> --engine firefox
//   npm run test:browser -- --fe28-remediation [--reproduce] [--case <substring>]
//   npm run test:browser                          # builds, serves on a free 127.0.0.1 port, Chromium
//   npm run test:browser -- --engine all          # chromium, webkit and firefox in turn
//   npm run test:browser -- --url http://127.0.0.1:4173/ --out /tmp/evidence --engine webkit
//   npm run test:browser -- --playwright /path/to/@playwright/test
// Playwright comes from --playwright, else ../e2e/node_modules/@playwright/test (npm ci in e2e/),
// else a resolvable "playwright". No browser is downloaded; install the pinned ones from e2e/.
'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const net = require('node:net');
const { spawn, execFileSync } = require('node:child_process');
const { randomBytes } = require('node:crypto');

const frontendRoot = path.resolve(__dirname, '../../..');
const args = process.argv.slice(2);
const flag = (name, fallback) => { const index = args.indexOf(`--${name}`); return index >= 0 && args[index + 1] ? args[index + 1] : fallback; };
// Positional compatibility with the FE01 interface: <playwright-module> <evidence-dir> <url> [engine].
const positional = args.filter((value, index) => !value.startsWith('--') && !(index > 0 && args[index - 1].startsWith('--')));
const modulePath = flag('playwright', positional[0]);
const out = path.resolve(flag('out', positional[1] || path.join(os.tmpdir(), `codemastery-browser-evidence-${Date.now()}`)));
const externalUrl = flag('url', positional[2]);
const engineArg = flag('engine', positional[3] || 'chromium');
const engines = engineArg === 'all' ? ['chromium', 'webkit', 'firefox'] : engineArg.split(',');

function loadPlaywright() {
    const candidates = [modulePath, path.resolve(frontendRoot, '../e2e/node_modules/@playwright/test'), 'playwright'].filter(Boolean);
    for (const candidate of candidates) { try { return require(candidate); } catch { /* try next */ } }
    throw new Error('Playwright not found. Run `npm ci` in e2e/ or pass --playwright <module path>.');
}
const freePort = () => new Promise((resolve, reject) => { const server = net.createServer(); server.unref(); server.on('error', reject); server.listen(0, '127.0.0.1', () => { const { port } = server.address(); server.close(() => resolve(port)); }); });
/** The marker written into the harness's temporary build; no other server on the port can answer with it. */
const buildMarker = (token) => `<meta name="codemastery-browser-build" content="${token}">`;
/** Marks the temporary build's index.html (outside the source tree) with `token`. */
function markBuild(dist, token) {
    const file = path.join(dist, 'index.html'), html = fs.readFileSync(file, 'utf8');
    if (!html.includes('</head>')) throw new Error(`${file} has no </head> to carry the build marker`);
    fs.writeFileSync(file, html.replace('</head>', `${buildMarker(token)}</head>`));
}
/**
 * Polls `url` until it serves the build marked with `token` (HTTP 200 from another server on the port is not enough).
 * Fails at once, with its stderr tail, if `child` exits first (for example on a taken port), and does not report
 * ready if `child` exited while the probe was answered.
 */
async function waitForUrl(url, ms, child, token, { probeMs = 3000, intervalMs = 500 } = {}) {
    let stderr = '';
    child.stderr?.on('data', (chunk) => { stderr = (stderr + chunk).slice(-2000); });
    const exited = () => {
        if (child.exitCode !== null || child.signalCode !== null) throw new Error(`The preview server exited (${child.exitCode ?? child.signalCode}) before ${url} was ready.${stderr ? `\n${stderr.trim()}` : ''}`);
    };
    const end = Date.now() + ms;
    for (;;) {
        exited();
        let ready = false;
        // Each probe, body included, is bounded, so a socket that accepts but never answers cannot stall the loop.
        try { const response = await fetch(url, { signal: AbortSignal.timeout(probeMs) }); ready = response.ok && (await response.text()).includes(buildMarker(token)); } catch { /* starting */ }
        if (ready) { exited(); return; }
        if (Date.now() > end) throw new Error(`Timed out waiting for ${url} to serve this harness's build${stderr ? `\n${stderr.trim()}` : ''}`);
        await new Promise((r) => setTimeout(r, intervalMs));
    }
}

// ── Controlled API doubles (presentation only) ───────────────────────────────
const uuid = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
const instructorId = '00000000-0000-4000-8000-000000000001';
const primaryId = '00000000-0000-4000-8000-000000000002';
const courses = [
    { id: primaryId, title: 'Introduction to Programming', description: 'Variables, decisions and loops for first-year students.', instructorId, instructorDisplayName: 'Instructor Demo', prerequisiteDescription: 'Comfortable with basic arithmetic.' },
    ...Array.from({ length: 20 }, (_, i) => ({ id: `00000000-0000-4000-8001-${String(i + 1).padStart(12, '0')}`, title: i === 0 ? 'W'.repeat(100) : `Course ${String(i + 1).padStart(2, '0')}`,
        description: i === 1 ? 'D'.repeat(500) : `Practice course ${i + 1}.`, instructorId, instructorDisplayName: i === 2 ? null : 'Instructor Demo', prerequisiteDescription: null })),
].sort((a, b) => (a.title < b.title ? -1 : a.title > b.title ? 1 : 0));
const summary = ({ prerequisiteDescription, ...rest }) => rest;
const encode = (value) => Buffer.from(JSON.stringify(value)).toString('base64url');
function loginBody(role = 'Learner', displayName = 'Harness Learner', sessionId = instructorId) {
    const iat = Math.floor(Date.now() / 1000);
    return { accessToken: `${encode({ alg: 'HS256', typ: 'JWT' })}.${encode({ sub: instructorId, sid: sessionId, iss: 'codemastery', aud: 'codemastery-api', iat, exp: iat + 900 })}.${'A'.repeat(43)}`,
        tokenType: 'Bearer', expiresIn: 900, user: { id: instructorId, displayName, role } };
}
const problem = (status, detail, instance) => ({ status, contentType: 'application/problem+json', body: JSON.stringify({ type: 'about:blank', title: { 400: 'Bad Request', 401: 'Unauthorized', 404: 'Not Found', 500: 'Internal Server Error' }[status], status, detail, instance }) });
const ok = (value) => ({ status: 200, contentType: 'application/json', body: JSON.stringify(value) });

function createApi() {
    const state = { notifications: [], newAfterMarkAll: null, role: 'Learner', displayName: 'Harness Learner', sessionId: instructorId, restore: false, requests: [], hold: null, fail: new Map(), memberships: new Map([[primaryId, 'active'], [courses[3].id, 'left']]) };
    async function handle(route) {
        const request = route.request(), url = new URL(request.url()), p = url.pathname.replace(/^\/api/, '');
        state.requests.push(`${request.method()} ${url.pathname}${url.search}`);
        if (state.hold && state.hold.test(p)) { const release = await new Promise((resolve) => { state.release = resolve; }); if (release === 'abort') return route.abort(); }
        if (state.fail.has(p)) return route.fulfill(problem(state.fail.get(p), 'The request could not be completed.', url.pathname));
        if (p === '/me/notifications/unread-count') return route.fulfill(ok({ unreadCount: state.notifications.filter((item) => !item.read).length }));
        if (p === '/me/notifications' && request.method() === 'GET') {
            const page = Number(url.searchParams.get('page')), size = Number(url.searchParams.get('size'));
            const rows = state.notifications.filter((item) => url.searchParams.get('unreadOnly') !== 'true' || !item.read);
            return route.fulfill(ok({ content: rows.slice(page * size, (page + 1) * size), page, size, totalElements: rows.length, totalPages: Math.ceil(rows.length / size) }));
        }
        if (p === '/me/notifications/read-all' && request.method() === 'PUT') {
            const markedCount = state.notifications.filter((item) => !item.read).length;
            state.notifications.forEach((item) => { item.read = true; });
            if (state.newAfterMarkAll) { state.notifications.unshift(state.newAfterMarkAll); state.newAfterMarkAll = null; }
            return route.fulfill(ok({ markedCount }));
        }
        const notice = /^\/me\/notifications\/([^/]+)\/read$/.exec(p);
        if (notice && request.method() === 'PUT') {
            const item = state.notifications.find((item) => item.id === notice[1]);
            if (!item) return route.fulfill(problem(404, 'Not found.', url.pathname));
            item.read = true; return route.fulfill(ok(item));
        }
        if (p === '/auth/login' && request.method() === 'POST') {
            const body = request.postDataJSON();
            return body.password === 'wrong-password-123' ? route.fulfill(problem(401, 'Invalid email or password.', url.pathname)) : route.fulfill(ok(loginBody(state.role, state.displayName, state.sessionId)));
        }
        // ADR014: the harness browser holds no refresh cookie, so the load-time session check is refused.
        if (p === '/auth/refresh' && request.method() === 'POST') return state.restore ? route.fulfill(ok(loginBody(state.role, state.displayName, state.sessionId))) : route.fulfill(problem(401, 'Authentication failed.', url.pathname));
        if (p === '/courses' && request.method() === 'GET') {
            const q = (url.searchParams.get('q') || '').toLowerCase(), pre = url.searchParams.get('prerequisites') || 'any';
            const page = Number(url.searchParams.get('page') || 0), size = Number(url.searchParams.get('size') || 20);
            const rows = courses.filter((c) => (!q || c.title.toLowerCase().includes(q) || c.description.toLowerCase().includes(q))
                && (pre === 'any' || (pre === 'none' ? c.prerequisiteDescription === null : c.prerequisiteDescription !== null)));
            return route.fulfill(ok({ content: rows.slice(page * size, page * size + size).map(summary), page, size, totalElements: rows.length, totalPages: Math.ceil(rows.length / size) }));
        }
        const detail = /^\/courses\/([^/]+)$/.exec(p);
        if (detail && request.method() === 'GET') {
            if (!uuid.test(detail[1])) return route.fulfill(problem(400, 'The request is invalid.', url.pathname));
            const course = courses.find((c) => c.id === detail[1]);
            if (!course) return route.fulfill(problem(404, 'Course not found.', url.pathname));
            return route.fulfill(ok({ ...course, syllabus: [
                { id: '00000000-0000-4000-8002-000000000001', title: 'Getting Started', position: 1, lessons: [{ id: '00000000-0000-4000-8003-000000000001', title: 'What a Program Is', position: 1 }, { id: '00000000-0000-4000-8003-000000000002', title: 'Variables and Values', position: 2 }] },
                { id: '00000000-0000-4000-8002-000000000002', title: 'Making Decisions', position: 2, lessons: [{ id: '00000000-0000-4000-8003-000000000003', title: 'If Statements', position: 1 }] }] }));
        }
        const membership = /^\/courses\/([^/]+)\/membership$/.exec(p);
        if (membership) {
            const id = membership[1], method = request.method();
            if (method === 'PUT') state.memberships.set(id, 'active');
            if (method === 'DELETE') state.memberships.set(id, 'left');
            return route.fulfill(ok({ courseId: id, status: state.memberships.get(id) || 'none' }));
        }
        if (p === '/me/courses') {
            const filter = url.searchParams.get('status') || 'joined';
            const rows = courses.filter((c) => state.memberships.has(c.id)).map((c) => ({ ...summary(c), status: state.memberships.get(c.id) }))
                .filter((c) => filter === 'all' || (filter === 'joined' ? c.status !== 'left' : c.status === filter));
            return route.fulfill(ok({ content: rows, page: 0, size: 20, totalElements: rows.length, totalPages: rows.length ? 1 : 0 }));
        }
        return route.fulfill(problem(404, 'Not found.', url.pathname));
    }
    return { state, handle };
}

// ── Colour helpers ───────────────────────────────────────────────────────────
function contrast(a, b) {
    const lum = (rgb) => rgb.map((x) => { x /= 255; return x <= 0.04045 ? x / 12.92 : ((x + 0.055) / 1.055) ** 2.4; }).reduce((sum, v, i) => sum + v * [0.2126, 0.7152, 0.0722][i], 0);
    const [lo, hi] = [lum(a), lum(b)].sort((x, y) => x - y);
    return (hi + 0.05) / (lo + 0.05);
}
/** Text nodes in the page with their colour, effective background and size (visible text only). */
const sampleText = () => {
    const parse = (value) => { const m = value.match(/[\d.]+/g); return m ? m.map(Number) : [0, 0, 0, 0]; };
    const background = (element) => {
        for (let node = element; node; node = node.parentElement) {
            const style = getComputedStyle(node), [r, g, b, a = 1] = parse(style.backgroundColor);
            if (style.backgroundImage !== 'none' && !style.backgroundImage.startsWith('linear-gradient')) return null;
            if (a >= 0.99) return [r, g, b];
        }
        return [10, 11, 15];
    };
    const rows = [], seen = new Set();
    const walker = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT);
    for (let text = walker.nextNode(); text && rows.length < 400; text = walker.nextNode()) {
        const element = text.parentElement, value = text.textContent.trim();
        if (!element || !value || seen.has(element)) continue;
        seen.add(element);
        if (element.closest('.cm-visually-hidden, [aria-hidden="true"], svg, .Mui-disabled, [disabled], .react-flow__attribution')) continue;
        const rect = element.getBoundingClientRect(), style = getComputedStyle(element);
        if (rect.width === 0 || rect.height === 0 || style.visibility === 'hidden' || Number(style.opacity) < 0.5) continue;
        const bg = background(element); if (!bg) continue;
        const [r, g, b, a = 1] = parse(style.color);
        const size = parseFloat(style.fontSize), weight = Number(style.fontWeight) || 400;
        rows.push({ text: value.slice(0, 40), color: [r, g, b].map((c, i) => c * a + bg[i] * (1 - a)), bg, large: size >= 24 || (size >= 18.66 && weight >= 700) });
    }
    return rows;
};

async function main() {
    const playwright = loadPlaywright();
    fs.mkdirSync(out, { recursive: true });
    let preview, dist, baseUrl = externalUrl, report = null, failed = 0;
    // Startup is inside the try as well: a build or preview failure still stops the preview process
    // and removes the temporary build directory.
    try {
        if (!baseUrl) {
            dist = fs.mkdtempSync(path.join(os.tmpdir(), 'codemastery-browser-dist-'));
            execFileSync('npx', ['vite', 'build', '--outDir', dist, '--emptyOutDir', '--logLevel', 'error'], { cwd: frontendRoot, env: { ...process.env, VITE_API_BASE_URL: '/api' }, stdio: 'inherit' });
            const token = randomBytes(16).toString('hex');
            markBuild(dist, token);
            // The port is chosen right before vite preview binds it; probing does not reserve it, so readiness also
            // requires the build marker and a collision fails instead of testing another server.
            const port = await freePort();
            preview = spawn('npx', ['vite', 'preview', '--outDir', dist, '--host', '127.0.0.1', '--port', String(port), '--strictPort'], { cwd: frontendRoot, stdio: ['ignore', 'ignore', 'pipe'] });
            baseUrl = `http://127.0.0.1:${port}/`;
            await waitForUrl(baseUrl, 60000, preview, token);
        }
        report = { revision: (() => { try { return execFileSync('git', ['rev-parse', 'HEAD'], { cwd: frontendRoot, encoding: 'utf8' }).trim(); } catch { return null; } })(),
            dirty: (() => { try { return execFileSync('git', ['status', '--porcelain'], { cwd: frontendRoot, encoding: 'utf8' }).trim().length > 0; } catch { return null; } })(),
            baseUrl, api: 'Controlled page.route responses only; no backend', engines: [] };
        for (const engine of engines) {
            const fixtureUrl = flag('fixture-url');
            if (args.includes('--fe32') && !fixtureUrl) throw new Error('--fe32 requires --fixture-url serving createFE32Fixture output; never activate production routes.');
            const result = args.includes('--fe28-remediation') ? await runFE28RemediationEngine(playwright, engine, baseUrl) : args.includes('--fe28') ? await runFE28Engine(playwright, engine, baseUrl) : args.includes('--fe32') ? await runFE32Engine(playwright, engine, baseUrl, fixtureUrl) : await runEngine(playwright, engine, baseUrl);
            report.engines.push(result); failed += result.failed.length;
        }
    } finally {
        if (preview && preview.exitCode === null && preview.signalCode === null) {
            preview.kill('SIGTERM');
            await new Promise((gone) => { preview.once('exit', gone); setTimeout(gone, 5000); });
        }
        if (dist) fs.rmSync(dist, { recursive: true, force: true });
        if (report) fs.writeFileSync(path.join(out, 'browser-report.json'), JSON.stringify(report, null, 2));
    }
    for (const result of report.engines) console.log(`${result.engine} ${result.version ?? ''}: ${result.passed.length} passed, ${result.failed.length} failed, ${result.notExercised?.length ?? 0} scenario(s) not exercised${result.launchError ? ` (launch: ${result.launchError})` : ''}`);
    console.log(`Evidence: ${out}`);
    if (failed > 0 || report.engines.some((r) => r.launchError)) process.exitCode = 1;
}

// ── FE34 colour schemes (DESIGN-2026-11-19-r12 › Themes) ─────────────────────
const CANVAS = { light: 'rgb(246, 246, 247)', dark: 'rgb(21, 23, 28)' };
const FRAME = 'rgb(10, 11, 15)';
/**
 * Rendered Light/Dark/System checks: first paint before the bundle, live operating-system changes,
 * other tabs, one Theme control per shell at the r12 widths, keyboard use, preview isolation and the
 * special modes (print, increased contrast, forced colours). Screenshots of each shell in both schemes
 * are written next to the report.
 */
async function themeChecks({ engine, check, notExercised, newPage, ready, overflow, url, dir }) {
    const scheme = (page) => page.evaluate(() => document.documentElement.dataset.colorScheme);
    const mainBg = (page) => page.evaluate(() => getComputedStyle(document.querySelector('main')).backgroundColor);
    const themeButton = (page) => page.locator('button[aria-label^="Theme:"]:visible');
    const waitScheme = (page, value) => page.waitForFunction((v) => document.documentElement.dataset.colorScheme === v, value, { timeout: 5000 });
    async function choose(page, label) {
        await themeButton(page).first().click();
        await page.getByRole('menuitemradio', { name: new RegExp(`^${label}`) }).click();
        await page.keyboard.press('Escape');
    }

    // First paint: the stored preference and the OS setting resolve before the application bundle runs.
    {
        const failures = [], rows = [['light', 'dark', 'light'], ['dark', 'light', 'dark'], ['system', 'dark', 'dark'], ['system', 'light', 'light'], [null, 'dark', 'dark'], ['sepia', 'dark', 'dark'], [null, 'no-preference', 'light']];
        let exercised = 0;
        for (const [stored, os, expected] of rows) {
            const { context, page } = await newPage({ colorScheme: os });
            await context.addInitScript(() => {
                // The root element may not exist yet when init scripts run, so observe the document.
                window.__fe34Schemes = [];
                new MutationObserver((records) => { for (const record of records) if (record.target === document.documentElement) window.__fe34Schemes.push(document.documentElement.getAttribute('data-color-scheme')); })
                    .observe(document, { attributes: true, subtree: true, attributeFilter: ['data-color-scheme'] });
            });
            await page.goto(url('/courses')); await ready(page);
            // Playwright's WebKit does not emulate "no preference": it reports the host appearance instead (verified on
            // WebKit 26.6 with a macOS host in Dark), so on such a host this row cannot be exercised there. That known
            // limitation is recorded as not exercised, never as a pass. Any other engine reporting dark here is an
            // unexpected emulation failure (of the browser, not of the application) and fails the check.
            if (os === 'no-preference' && await page.evaluate(() => matchMedia('(prefers-color-scheme: dark)').matches)) {
                const row = `${stored ?? 'missing'}+os:${os}`;
                if (engine === 'webkit') notExercised(`first paint row ${row}`, 'browser emulation limitation: WebKit reports the host appearance (prefers-color-scheme: dark) instead of "no-preference"');
                else failures.push(`${row}: emulation failure, not an application result: ${engine} reported prefers-color-scheme: dark under "no-preference"`);
                await context.close(); continue;
            }
            exercised++;
            await page.evaluate((value) => { if (value === null) localStorage.removeItem('codemastery.theme'); else localStorage.setItem('codemastery.theme', value); }, stored);
            let release; const gate = new Promise((done) => { release = done; });
            await page.route(/\/assets\/index-[^/]+\.js$/, async (route) => { await gate; await route.continue(); });
            await page.evaluate(() => { window.__fe34Previous = true; });
            const navigation = page.goto(url('/courses'));
            // Wait for the new document (not the one being replaced) to reach the first-paint state.
            await page.waitForFunction(() => !window.__fe34Previous && document.documentElement.hasAttribute('data-color-scheme'));
            const before = await page.evaluate(() => ({ scheme: document.documentElement.dataset.colorScheme, colorScheme: getComputedStyle(document.documentElement).colorScheme,
                root: getComputedStyle(document.documentElement).backgroundColor, app: document.getElementById('root')?.childElementCount ?? 0 }));
            release(); await navigation; await ready(page);
            const after = await page.evaluate(() => ({ scheme: document.documentElement.dataset.colorScheme, flips: window.__fe34Schemes }));
            const canvas = await mainBg(page);
            const label = `${stored ?? 'missing'}+os:${os}`;
            if (before.app !== 0) failures.push(`${label}: bundle ran before the check`);
            if (before.scheme !== expected || before.colorScheme !== expected) failures.push(`${label}: before bundle ${JSON.stringify(before)}`);
            if (before.root !== FRAME) failures.push(`${label}: root ${before.root}`);
            if (after.scheme !== expected || after.flips.join() !== expected) failures.push(`${label}: after ${JSON.stringify(after)}`);
            if (canvas !== CANVAS[expected]) failures.push(`${label}: canvas ${canvas}`);
            if (stored === 'sepia' && await page.evaluate(() => localStorage.getItem('codemastery.theme')) !== 'sepia') failures.push('invalid value was rewritten without a choice');
            await context.close();
        }
        // The name states how many rows ran, so a row recorded as not exercised is never read as covered by this pass.
        await check(`first paint (${exercised} of ${rows.length} rows exercised): the stored preference (System by default, invalid → System) and the OS setting set data-color-scheme and color-scheme before the bundle, on the frame colour, and the scheme never flips`, async () => assert.deepEqual(failures, []));
    }

    // Live operating-system changes, Light/Dark ignoring them, announcements and keyboard use.
    {
        const { context, page } = await newPage({ colorScheme: 'light' });
        await page.goto(url('/courses')); await ready(page);
        await check('System follows a live operating-system change without reload; Light and Dark ignore it', async () => {
            assert.equal(await scheme(page), 'light');
            assert.equal(await themeButton(page).getAttribute('aria-label'), 'Theme: System (currently Light)');
            const marker = await page.evaluate(() => { window.__fe34Document = Math.random(); return window.__fe34Document; });
            await page.emulateMedia({ colorScheme: 'dark' }); await waitScheme(page, 'dark');
            assert.equal(await themeButton(page).getAttribute('aria-label'), 'Theme: System (currently Dark)');
            assert.equal(await mainBg(page), CANVAS.dark);
            await choose(page, 'Light'); assert.equal(await scheme(page), 'light');
            await page.emulateMedia({ colorScheme: 'light' }); await page.emulateMedia({ colorScheme: 'dark' }); await page.waitForTimeout(100);
            assert.equal(await scheme(page), 'light');
            assert.equal(await page.evaluate(() => localStorage.getItem('codemastery.theme')), 'light');
            assert.equal(await page.evaluate(() => window.__fe34Document), marker, 'the document was reloaded');
        });
        await check('the Theme button and menu work by keyboard: Enter opens on the chosen item, arrows move, Enter selects and stays open, Escape returns focus; one polite status message', async () => {
            const button = themeButton(page).first();
            await button.focus(); await page.keyboard.press('Enter');
            const menu = page.getByRole('menu', { name: 'Theme' }); await menu.waitFor();
            assert.equal(await button.getAttribute('aria-expanded'), 'true');
            assert.deepEqual(await page.evaluate(() => [document.activeElement?.getAttribute('role'), document.activeElement?.getAttribute('aria-checked'), document.activeElement?.textContent]), ['menuitemradio', 'true', 'Light']);
            await page.keyboard.press('ArrowDown'); await page.keyboard.press('Enter');
            assert.equal(await scheme(page), 'dark'); assert(await menu.isVisible(), 'menu stays open');
            assert.deepEqual(await page.evaluate(() => [document.activeElement?.getAttribute('aria-checked'), document.activeElement?.textContent]), ['true', 'Dark']);
            const raised = await menu.evaluate((node) => { const paper = node.closest('.MuiPaper-root'); const s = getComputedStyle(paper); return [s.backgroundColor, s.boxShadow.includes('inset')]; });
            assert.deepEqual(raised, ['rgb(44, 48, 56)', true]); // bg.raised (graphite-700) with the dark hairline outline
            assert.equal(await page.locator('[role="status"]', { hasText: 'Theme set to' }).allTextContents().then((texts) => texts.join('|')), 'Theme set to Dark.');
            await page.keyboard.press('Escape'); await menu.waitFor({ state: 'hidden' });
            assert.equal(await button.evaluate((node) => node === document.activeElement), true);
            const ring = await button.evaluate((node) => { const s = getComputedStyle(node); return [s.outlineStyle, parseFloat(s.outlineWidth)]; });
            assert(ring[0] !== 'none' && ring[1] >= 2, `focus ring ${ring}`);
        });
        await check('Tab and Shift+Tab from the open Theme menu close it and continue the tab sequence from the Theme button, not from the document start', async () => {
            const button = themeButton(page).first();
            // The neighbours of the Theme button in the real tab sequence (it is the first top-bar utility).
            const neighbours = await button.evaluate((node) => {
                const order = [...document.querySelectorAll('a[href], button:not([disabled]), input:not([disabled]), [tabindex]:not([tabindex="-1"])')]
                    .filter((element) => element.getClientRects().length && getComputedStyle(element).visibility !== 'hidden');
                const at = order.indexOf(node), name = (element) => element?.getAttribute('aria-label') ?? element?.textContent?.trim() ?? null;
                return [name(order[at - 1]), name(order[at + 1])];
            });
            const focusedName = () => page.evaluate(() => document.activeElement?.getAttribute('aria-label') ?? document.activeElement?.textContent?.trim() ?? null);
            for (const [keys, expected] of [['Tab', neighbours[1]], ['Shift+Tab', neighbours[0]]]) {
                await button.focus(); await page.keyboard.press('Enter');
                const menu = page.getByRole('menu', { name: 'Theme' }); await menu.waitFor();
                await page.keyboard.press(keys); await menu.waitFor({ state: 'hidden' });
                assert.equal(await button.getAttribute('aria-expanded'), 'false');
                assert.equal(await focusedName(), expected, `${keys} from the menu`);
            }
        });
        await check('the "Theme" tooltip never covers the open Theme menu (hover or keyboard focus first), and the menu is not inside a tooltip role', async () => {
            const button = themeButton(page).first();
            const visibleTips = () => page.evaluate(() => [...document.querySelectorAll('.MuiTooltip-popper')]
                .filter((node) => node.textContent === 'Theme' && node.getClientRects().length && Number(getComputedStyle(node.firstElementChild ?? node).opacity) > 0).length);
            for (const open of [async () => { await button.hover(); await page.waitForTimeout(600); await button.click(); },
                async () => { await page.mouse.move(1, 1); await button.focus(); await page.waitForTimeout(600); await page.keyboard.press('Enter'); }]) {
                await open();
                const menu = page.getByRole('menu', { name: 'Theme' }); await menu.waitFor(); await page.waitForTimeout(400);
                assert.equal(await visibleTips(), 0, 'tooltip shown over the open menu');
                assert.equal(await menu.evaluate((node) => !!node.closest('[role="tooltip"]')), false);
                await page.keyboard.press('Escape'); await menu.waitFor({ state: 'hidden' });
            }
        });
        await check('a choice in one tab applies in another open tab through the storage event', async () => {
            const other = await context.newPage(); await other.goto(url('/login')); await ready(other);
            assert.equal(await scheme(other), 'dark');
            await choose(page, 'Light'); await waitScheme(other, 'light');
            assert.equal(await other.locator('button[aria-label^="Theme:"]').getAttribute('aria-label'), 'Theme: Light');
            // System resolves per tab: this tab's emulated OS is dark, the other tab's is light.
            await choose(page, 'System'); assert.equal(await scheme(page), 'dark');
            await other.waitForFunction(() => document.querySelector('button[aria-label^="Theme:"]')?.getAttribute('aria-label') === 'Theme: System (currently Light)', null, { timeout: 5000 });
            assert.equal(await scheme(other), 'light');
            await other.close();
        });
        await context.close();
    }

    // r13 (D13-1 – D13-5): icon colours, menu item states, header order and the Theme tooltip lifecycle.
    {
        const SELECTED = { light: 'rgb(227, 238, 255)', dark: 'rgb(27, 46, 77)' }, ITEM_HOVER = { light: 'rgb(238, 238, 240)', dark: 'rgb(54, 58, 67)' };
        await check('Theme menu: the selected item stays distinct from a hovered item, selected + hover and keyboard focus are distinguishable, in both schemes (D13-4)', async () => {
            for (const [preference, os, rendered] of [['system', 'light', 'light'], ['system', 'dark', 'dark'], ['light', 'dark', 'light'], ['dark', 'light', 'dark']]) {
                const { context, page } = await newPage({ colorScheme: os });
                await context.addInitScript((value) => { try { localStorage.setItem('codemastery.theme', value); } catch { /* blocked */ } }, preference);
                await page.goto(url('/courses')); await ready(page);
                const where = `${preference}/${rendered}`;
                await themeButton(page).first().click();
                const items = page.getByRole('menuitemradio'); await items.first().waitFor();
                const selected = ['system', 'light', 'dark'].indexOf(preference);
                const state = (index) => items.nth(index).evaluate((node) => { const s = getComputedStyle(node); return { bg: s.backgroundColor, ring: s.boxShadow.includes('inset'), outline: s.outlineStyle !== 'none' && parseFloat(s.outlineWidth) >= 2, weight: s.fontWeight, checked: node.getAttribute('aria-checked') }; });
                await page.mouse.move(1, 1); await page.waitForTimeout(250);
                const rest = await state(selected);
                assert.deepEqual([rest.bg, rest.ring, rest.weight, rest.checked], [SELECTED[rendered], false, '600', 'true'], `${where} selected at rest`);
                for (let index = 0; index < 3; index++) {
                    await items.nth(index).hover(); await page.waitForTimeout(250);
                    const hovered = await state(index), still = await state(selected);
                    if (index === selected) {
                        assert.equal(hovered.bg, SELECTED[rendered], `${where} selected + hover keeps the selected fill`);
                        assert.equal(hovered.ring, true, `${where} selected + hover adds a ring`);
                    } else {
                        assert.equal(hovered.bg, ITEM_HOVER[rendered], `${where} hover of item ${index}`);
                        assert.notEqual(hovered.bg, still.bg, `${where} hovered item ${index} looks like the selected one`);
                        assert.equal(still.bg, SELECTED[rendered], `${where} selected item while ${index} is hovered`);
                        assert.equal(hovered.checked, 'false');
                    }
                }
                await page.mouse.move(1, 1); await page.waitForTimeout(150);
                // Keyboard focus: a 2 px ring on the focused item, whether it is selected or not.
                await page.keyboard.press('ArrowDown'); await page.waitForTimeout(150);
                const focusedIndex = (selected + 1) % 3, focused = await state(focusedIndex);
                assert.equal(focused.outline, true, `${where} keyboard focus ring`);
                assert.equal(focused.bg, ITEM_HOVER[rendered], `${where} keyboard focus fill`);
                await page.keyboard.press('ArrowUp'); await page.waitForTimeout(150);
                assert.equal((await state(selected)).outline, true, `${where} keyboard focus ring on the selected item`);
                await page.keyboard.press('Escape');
                await context.close();
            }
        });
        await check('the drawer Theme control keeps the selected segment distinct from a hovered one (D13-4)', async () => {
            for (const stored of ['light', 'dark']) {
                const { context, page } = await newPage({ viewport: { width: 390, height: 844 } });
                await context.addInitScript((value) => { try { localStorage.setItem('codemastery.theme', value); } catch { /* blocked */ } }, stored);
                await page.goto(url('/courses')); await ready(page);
                await page.getByRole('button', { name: 'Toggle navigation' }).click();
                const group = page.getByRole('radiogroup', { name: 'Theme' }); await group.waitFor();
                const pill = (name) => group.getByRole('radio', { name }).evaluate((node) => [getComputedStyle(node, '::before').backgroundColor, getComputedStyle(node).color]);
                const selectedName = stored === 'light' ? 'Light' : 'Dark', otherName = 'System';
                await group.getByRole('radio', { name: otherName }).hover(); await page.waitForTimeout(250);
                const [selectedPill, hoveredPill] = [await pill(selectedName), await pill(otherName)];
                assert.notEqual(selectedPill[0], hoveredPill[0], `${stored}: hovered segment looks selected`);
                assert.deepEqual(selectedPill, ['rgb(255, 255, 255)', 'rgb(10, 11, 15)']);
                await context.close();
            }
        });
        await check('G-02: drawer Theme labels and glyphs stay inside their segments at 100%, 120% and 200% text size, including 320 px, with keyboard selection intact', async () => {
            for (const os of ['light', 'dark']) for (const width of [320, 640]) for (const textSize of [100, 120, 200]) {
                for (const screen of ['learner-home', 'teaching', 'admin-overview']) {
                    const { context, page } = await newPage({ colorScheme: os, viewport: { width, height: 844 } });
                    const where = `${screen}/${os}/${width}/${textSize}%`;
                    try {
                        await page.goto(url(`/preview?screen=${screen}`)); await ready(page);
                        await page.addStyleTag({ content: `html { font-size: ${textSize}% !important; }` });
                        await page.evaluate(() => document.fonts.ready);
                        await page.getByRole('button', { name: 'Toggle navigation' }).click();
                        const group = page.getByRole('radiogroup', { name: 'Theme', exact: true }); await group.waitFor();
                        await page.waitForTimeout(400); // Measure the settled drawer, never its Slide transition.
                        const inspect = async (selected) => {
                            const rows = await group.getByRole('radio').evaluateAll((nodes) => nodes.map((node) => {
                                const rect = node.getBoundingClientRect(), pill = getComputedStyle(node, '::before');
                                const walker = document.createTreeWalker(node, NodeFilter.SHOW_TEXT), content = [node.querySelector('svg').getBoundingClientRect()];
                                for (let text = walker.nextNode(); text; text = walker.nextNode()) if (text.textContent.trim() && !text.parentElement.closest('svg, style')) {
                                    const range = document.createRange(); range.selectNodeContents(text); content.push(range.getBoundingClientRect());
                                }
                                return { label: node.textContent, checked: node.getAttribute('aria-checked'), width: rect.width, height: rect.height,
                                    contained: content.every((part) => part.width > 0 && part.height > 0 && part.left >= rect.left + parseFloat(pill.left) - 1
                                        && part.right <= rect.right - parseFloat(pill.right) + 1 && part.top >= rect.top + parseFloat(pill.top) - 1
                                        && part.bottom <= rect.bottom - parseFloat(pill.bottom) + 1),
                                    fill: pill.backgroundColor, color: getComputedStyle(node).color, glyphColor: getComputedStyle(node.querySelector('svg')).color,
                                    fontRatio: parseFloat(getComputedStyle(node).fontSize) / parseFloat(getComputedStyle(document.documentElement).fontSize) };
                            }));
                            assert.equal(rows.length, 3, where);
                            assert.deepEqual(rows.filter((row) => row.checked === 'true').map((row) => row.label), [selected], where);
                            for (const row of rows) {
                                assert(row.width >= 44 && row.height >= 44, `${where}/${row.label}: target ${row.width} × ${row.height}`);
                                assert(row.contained, `${where}/${row.label}: label or glyph escapes its pill`);
                                assert(row.fontRatio >= .8125 - .001, `${where}/${row.label}: text enlargement was suppressed`);
                                assert.equal(row.glyphColor, row.color, `${where}/${row.label}: glyph follows its label`);
                            }
                            const active = rows.find((row) => row.checked === 'true');
                            assert.deepEqual([active.fill, active.color], ['rgb(255, 255, 255)', 'rgb(10, 11, 15)'], where);
                            if (textSize === 100) assert.equal(Math.round((await group.boundingBox()).height), 44, `${where}: normal track height`);
                        };
                        await inspect('System');
                        await group.getByRole('radio', { name: 'System', exact: true }).focus();
                        for (const [key, label] of [['ArrowRight', 'Light'], ['ArrowRight', 'Dark'], ['Home', 'System']]) {
                            await page.keyboard.press(key); await page.waitForTimeout(150);
                            await inspect(label);
                            const selected = group.getByRole('radio', { name: label, exact: true });
                            assert(await selected.evaluate((node) => node === document.activeElement), `${where}: keyboard focus on ${label}`);
                            assert(await selected.evaluate((node) => getComputedStyle(node).outlineStyle !== 'none' && parseFloat(getComputedStyle(node).outlineWidth) >= 2), `${where}: visible focus`);
                            assert.equal(await group.isVisible(), true, `${where}: drawer stays open`);
                            assert.equal(await scheme(page), label === 'System' ? os : label.toLowerCase(), where);
                        }
                        if (width === 320 && screen === 'learner-home') await page.screenshot({ path: path.join(dir, `g02-drawer-${os}-${textSize}.png`) });
                        await page.keyboard.press('Escape');
                        assert(await page.getByRole('button', { name: 'Toggle navigation' }).evaluate((node) => node === document.activeElement), `${where}: drawer restores focus`);
                    } finally { await context.close(); }
                }
            }
        });
        await check('StatusPill glyphs take their label colour; Needs-attention glyphs keep their semantic accents (D13-1)', async () => {
            for (const stored of ['light', 'dark']) {
                const { context, page } = await newPage();
                await context.addInitScript((value) => { try { localStorage.setItem('codemastery.theme', value); } catch { /* blocked */ } }, stored);
                for (const screen of ['teaching', 'admin-courses', 'course', 'overview']) {
                    await page.goto(url(`/preview?screen=${screen}`)); await ready(page);
                    const pills = await page.evaluate(() => [...document.querySelectorAll('span[data-tone]')].filter((node) => node.querySelector('svg'))
                        .map((node) => [node.textContent, getComputedStyle(node).color, getComputedStyle(node.querySelector('svg')).color, getComputedStyle(node.querySelector(':scope > span')).color]));
                    assert(pills.length > 0, `${screen}: no pills`);
                    for (const [text, pillColor, glyph, label] of pills) assert(glyph === label && label === pillColor, `${stored}/${screen} ${text}: glyph ${glyph}, label ${label}`);
                    if (screen === 'teaching') {
                        const published = pills.find(([text]) => text === 'Published');
                        assert(published, 'Published pill');
                        const attention = await page.evaluate(() => [...document.querySelectorAll('aside[aria-label="Needs attention"] li')].filter((node) => node.querySelector('svg'))
                            .map((node) => [getComputedStyle(node.querySelector('svg')).color, getComputedStyle(node).color]));
                        assert.equal(attention.length, 3);
                        for (const [glyph, text] of attention) assert.notEqual(glyph, text, `${stored}: an attention glyph lost its accent`);
                    }
                }
                await context.close();
            }
        });
        await check('header order: landing Catalogue → Theme → Sign in → Create account; workspace utilities → Theme → account menu (D13-2, D13-3)', async () => {
            const { context, page } = await newPage();
            await page.goto(url('/')); await ready(page);
            const landing = await page.locator('header nav :is(a, button):visible').evaluateAll((nodes) => nodes.map((node) => node.getAttribute('aria-label')?.startsWith('Theme:') ? 'Theme' : node.textContent.trim()));
            assert.deepEqual(landing, ['Catalogue', 'Theme', 'Sign in', 'Create account']);
            await page.goto(url('/preview?screen=learner-home')); await ready(page);
            const right = await page.locator('header :is(button, a[aria-label]):visible').evaluateAll((nodes) => nodes.map((node) => (node.getAttribute('aria-label') ?? node.textContent).replace(/^(Theme|Account menu|Notifications).*/, '$1')));
            assert.deepEqual(right.slice(-3), ['Notifications', 'Theme', 'Account menu']);
            await context.close();
        });
        for (const reducedMotion of ['no-preference', 'reduce']) await check(`the Theme tooltip closes with the menu and never stays behind after dismissal, by pointer and keyboard (${reducedMotion} motion) (D13-5)`, async () => {
            const { context, page } = await newPage({ reducedMotion });
            await page.goto(url('/preview?screen=learner-home')); await ready(page);
            const button = themeButton(page).first(), menu = page.getByRole('menu', { name: 'Theme' });
            const tips = () => page.evaluate(() => [...document.querySelectorAll('.MuiTooltip-popper')].filter((node) => node.textContent === 'Theme' && node.getClientRects().length && Number(getComputedStyle(node.firstElementChild ?? node).opacity) > 0.5).length);
            // Any trace of the tooltip (a fading one too) while the menu is open would overlap the menu.
            const traces = () => page.evaluate(() => [...document.querySelectorAll('.MuiTooltip-popper')].filter((node) => node.textContent === 'Theme' && node.getClientRects().length && Number(getComputedStyle(node.firstElementChild ?? node).opacity) > 0.05).length);
            const settle = () => page.waitForTimeout(700);
            const box = await button.boundingBox(), away = async () => { await page.mouse.move(box.x - 300, box.y + 400); await settle(); };
            for (const label of ['System', 'Light', 'Dark']) {
                await away(); await button.hover(); await settle(); assert.equal(await tips(), 1, `${label}: hover shows the tooltip`);
                await button.click(); await menu.waitFor(); await page.waitForTimeout(50); assert.equal(await traces(), 0, `${label}: the tooltip fades over the open menu`);
                await settle(); assert.equal(await tips(), 0, `${label}: hidden while the menu is open`);
                await page.getByRole('menuitemradio', { name: new RegExp(`^${label}`) }).click();
                await page.mouse.click(box.x - 300, box.y + 400); await menu.waitFor({ state: 'hidden' }); await settle();
                assert.equal(await tips(), 0, `${label}: stranded after an outside click`);
                await away(); assert.equal(await tips(), 0);
                await button.hover(); await settle(); assert.equal(await tips(), 1, `${label}: a new hover shows it again`);
            }
            // Opened and closed without a selection, the pointer staying on the button: no stale reopening.
            await button.click(); await menu.waitFor(); await button.click(); await menu.waitFor({ state: 'hidden' }); await settle();
            assert.equal(await tips(), 0, 'stale hover after closing with the button');
            await away(); await button.hover(); await settle(); assert.equal(await tips(), 1);
            // The pointer leaves while the menu is open; Escape restores focus without a tooltip.
            await button.click(); await menu.waitFor(); await away(); await page.keyboard.press('Escape'); await menu.waitFor({ state: 'hidden' }); await settle();
            assert.equal(await button.evaluate((node) => node === document.activeElement), true);
            assert.equal(await tips(), 0, 'synthetic focus restoration reopened the tooltip');
            // Keyboard: Tab onto the button shows it; Enter opens; arrows and Enter select; Escape returns focus without it.
            await page.keyboard.press('Shift+Tab'); await page.keyboard.press('Tab'); await settle();
            assert.equal(await button.evaluate((node) => node === document.activeElement), true); assert.equal(await tips(), 1, 'keyboard focus shows the tooltip');
            await page.keyboard.press('Enter'); await menu.waitFor(); await settle(); assert.equal(await tips(), 0);
            await page.keyboard.press('ArrowDown'); await page.keyboard.press('Enter'); await page.keyboard.press('Escape'); await menu.waitFor({ state: 'hidden' }); await settle();
            assert.equal(await button.evaluate((node) => node === document.activeElement), true); assert.equal(await tips(), 0, 'Escape left a tooltip');
            await page.keyboard.press('Tab'); await settle(); assert.equal(await tips(), 0);
            await page.keyboard.press('Shift+Tab'); await settle(); assert.equal(await tips(), 1, 'returning by keyboard shows it again');
            // AUD-02/F-01 and F-03: the pointer rests on the button through a close by Escape and then moves over the
            // glyph (stale hover: nothing); Tab away and Shift+Tab back without moving it: keyboard focus shows the
            // tooltip at once (no 400 ms hover delay), even though the pointer still rests on the button.
            const centre = { x: box.x + box.width / 2, y: box.y + box.height / 2 };
            await away(); await page.mouse.move(centre.x, centre.y); await settle();
            await button.click(); await menu.waitFor(); await page.keyboard.press('Escape'); await menu.waitFor({ state: 'hidden' });
            for (const [dx, dy] of [[-6, 5], [6, -5], [0, 0]]) await page.mouse.move(centre.x + dx, centre.y + dy, { steps: 3 });
            await settle(); assert.equal(await tips(), 0, 'stale hover after Escape reopened the tooltip');
            await page.keyboard.press('Tab'); await settle(); await page.keyboard.press('Shift+Tab');
            await page.waitForFunction(() => [...document.querySelectorAll('.MuiTooltip-popper')].some((node) => node.textContent === 'Theme' && node.getClientRects().length), null, { timeout: 250 })
                .catch(() => { throw new Error('keyboard refocus under a resting pointer did not show the tooltip within 250 ms (AUD-02/F-01, F-03)'); });
            assert.equal(await button.evaluate((node) => node === document.activeElement), true);
            // Cold keyboard focus (no tooltip shown in the last second) also shows it without the hover delay.
            await page.keyboard.press('Tab'); await away(); await page.waitForTimeout(1000); await page.keyboard.press('Shift+Tab');
            await page.waitForFunction(() => [...document.querySelectorAll('.MuiTooltip-popper')].some((node) => node.textContent === 'Theme' && node.getClientRects().length), null, { timeout: 250 })
                .catch(() => { throw new Error('cold keyboard focus did not show the tooltip within 250 ms (F-03)'); });
            await page.keyboard.press('Tab'); await settle();
            // N-04: Escape within 400 ms of keyboard focus leaves MUI's pending focus timer running. With focus unchanged, a later
            // pointer hover must still take the full 400 ms: MUI's 200 ms hysteresis delay (left set by that timer) would open it at ~200 ms.
            const present = () => page.evaluate(() => [...document.querySelectorAll('.MuiTooltip-popper')].some((node) => node.textContent === 'Theme' && node.getClientRects().length));
            await away(); await page.waitForTimeout(1000); await page.keyboard.press('Shift+Tab');
            await page.waitForTimeout(100); await page.keyboard.press('Escape'); await page.waitForTimeout(2000);
            assert.equal(await tips(), 0, 'N-04: Escape did not dismiss the keyboard-focus tooltip for good');
            assert.equal(await button.evaluate((node) => node === document.activeElement), true);
            await page.mouse.move(centre.x, centre.y, { steps: 2 }); await page.waitForTimeout(300);
            assert.equal(await present(), false, 'N-04: the hover tooltip opened before its 400 ms delay (hysteresis left set by the focus timer)');
            await page.waitForFunction(() => [...document.querySelectorAll('.MuiTooltip-popper')].some((node) => node.textContent === 'Theme' && node.getClientRects().length), null, { timeout: 800 })
                .catch(() => { throw new Error('N-04: the hover tooltip did not open after its 400 ms delay'); });
            await page.keyboard.press('Tab'); await away(); await settle();
            // Repeated cycles, then a route change: nothing stays behind.
            for (let round = 0; round < 4; round++) { await button.click(); await menu.waitFor(); await page.keyboard.press('Escape'); await menu.waitFor({ state: 'hidden' }); }
            await away(); await page.getByRole('link', { name: 'My courses' }).first().click(); await ready(page); await settle();
            assert.equal(await tips(), 0, 'a tooltip survived the route change');
            await context.close();
        });
    }

    // One control per rendered shell at the r12 widths, both schemes, with screenshots.
    {
        const failures = [];
        const shells = [['workspace', '/courses', true], ['landing', '/', false], ['auth', '/login', false], ['focus-bar', '/preview?screen=quiz-attempt', false], ['preview-learner', '/preview?screen=learner-home', true], ['diagnostic', '/preview?screen=quiz-attempt&item=diagnostic', false]];
        for (const stored of ['light', 'dark']) {
            const { context, page } = await newPage();
            await context.addInitScript((value) => { try { localStorage.setItem('codemastery.theme', value); } catch { /* blocked */ } }, stored);
            for (const width of [320, 360, 768, 1024, 1280, 1440]) {
                await page.setViewportSize({ width, height: 900 });
                for (const [name, route, workspace] of shells) {
                    await page.goto(url(route)); await ready(page); await page.waitForTimeout(100);
                    const where = `${name}@${width}/${stored}`;
                    if (await scheme(page) !== stored) failures.push(`${where}: scheme ${await scheme(page)}`);
                    const visible = await themeButton(page).count();
                    const expected = workspace && width < 900 ? 0 : 1;
                    if (visible !== expected) failures.push(`${where}: ${visible} visible Theme buttons, expected ${expected}`);
                    if (workspace && width < 900) {
                        await page.getByRole('button', { name: 'Toggle navigation' }).click();
                        const groups = page.getByRole('radiogroup', { name: 'Theme' }); await groups.first().waitFor();
                        if (await groups.count() !== 1) failures.push(`${where}: drawer radiogroups ${await groups.count()}`);
                        if (await themeButton(page).count() !== 0) failures.push(`${where}: a Theme button shows with the drawer`);
                        const height = await groups.first().evaluate((node) => node.getBoundingClientRect().height);
                        if (Math.round(height) !== 44) failures.push(`${where}: drawer track ${height}px`);
                        if ([360, 768].includes(width)) { await page.waitForTimeout(400); await page.screenshot({ path: path.join(dir, `fe34-${name}-drawer-${stored}-${width}.png`) }); }
                        await page.keyboard.press('Escape'); await page.getByRole('dialog').waitFor({ state: 'hidden' });
                    } else {
                        const box = await themeButton(page).first().boundingBox();
                        if (!box || Math.round(box.width) < 44 || Math.round(box.height) < 44) failures.push(`${where}: hit area ${JSON.stringify(box)}`);
                    }
                    const { scroll, client } = await overflow(page);
                    if (scroll > client) failures.push(`${where}: page overflow ${scroll} > ${client}`);
                    if ([360, 1280].includes(width)) await page.screenshot({ path: path.join(dir, `fe34-${name}-${stored}-${width}.png`) });
                }
            }
            await context.close();
        }
        await check('exactly one 44 px Theme control per rendered shell (top bar ≥ 900 px, 44 px drawer track below, landing, auth, quiz and diagnostic Focus bar) at 320–1440 px in both schemes, without page overflow', async () => assert.deepEqual(failures, []));
    }

    // Focus bar below 600 px: exit, Theme, timer and Submit stay; title and counter move to the navigator strip.
    {
        const { context, page } = await newPage({ viewport: { width: 390, height: 844 } });
        await page.goto(url('/preview?screen=quiz-attempt')); await ready(page);
        await check('below 600 px the Focus bar keeps exit, Theme, timer and Submit, and the title and counter move into the navigator strip (D10-1)', async () => {
            const bar = page.locator('header').first();
            assert(await bar.getByRole('button', { name: 'Leave quiz' }).isVisible());
            assert(await bar.getByRole('button', { name: /^Theme: / }).isVisible());
            assert(await bar.getByRole('button', { name: 'Submit quiz' }).isVisible());
            const strip = await bar.evaluate((node) => { const visible = (el) => el && el.getBoundingClientRect().height > 0 && getComputedStyle(el).display !== 'none';
                const texts = [...node.querySelectorAll('p')].filter(visible).map((p) => p.textContent); return texts; });
            assert(strip.some((text) => /^Question \d+ of \d+$/.test(text)), JSON.stringify(strip));
            const timerBefore = await bar.evaluate((node) => node.textContent.match(/\d+:\d{2}/)?.[0]);
            const answeredBefore = await page.locator('main input:checked').count();
            await choose(page, 'Dark');
            assert.equal(await scheme(page), 'dark');
            assert.equal(await page.getByRole('dialog').count(), 0); // no leave or submit confirmation
            assert(timerBefore, 'timer visible');
            assert.equal(await page.locator('main input:checked').count(), answeredBefore);
            assert.equal(await bar.getByRole('button', { name: /^Theme: / }).evaluate((node) => node === document.activeElement), true);
        });
        await context.close();
    }

    // Preview isolation: the preview-local choice is memory-only and does not reach other tabs.
    {
        const { context, page } = await newPage();
        await page.goto(url('/courses')); await ready(page);
        await page.evaluate(() => localStorage.setItem('codemastery.theme', 'light'));
        await page.goto(url('/preview?screen=learner-home')); await ready(page);
        await check('/preview loads the stored scheme; its choice is memory-only, reaches no other tab, survives Reset sample data and is gone on reload', async () => {
            const other = await context.newPage(); await other.goto(url('/courses')); await ready(other);
            assert.equal(await scheme(page), 'light');
            await choose(page, 'Dark'); assert.equal(await scheme(page), 'dark');
            assert.equal(await page.evaluate(() => localStorage.getItem('codemastery.theme')), 'light');
            await other.waitForTimeout(200); assert.equal(await scheme(other), 'light');
            await page.getByRole('button', { name: /^Account menu/ }).click();
            await page.getByRole('menuitem', { name: 'Reset sample data' }).click();
            assert.equal(await scheme(page), 'dark');
            await page.reload(); await ready(page);
            assert.equal(await scheme(page), 'light');
            assert.equal(await page.evaluate(() => sessionStorage.length), 0);
            await other.close();
        });
        await context.close();
    }

    // FE34 + FE03: the landing restore state keeps one Theme control (controlled refresh timing).
    for (const width of [390, 768, 1280]) {
        const { context, page, api } = await newPage({ viewport: { width, height: 844 } });
        await context.addInitScript(() => { try { localStorage.setItem('codemastery.theme', 'dark'); } catch { /* blocked */ } });
        api.state.hold = /^\/auth\/refresh$/;
        await check(`the landing page shows one Theme control while the session restore runs and one after it (${width} px)`, async () => {
            await page.goto(url('/'));
            await page.getByText('Restoring your session…').waitFor();
            assert.equal(await themeButton(page).count(), 1);
            assert.equal(await page.getByRole('link', { name: 'Sign in' }).count(), 0);
            assert.equal(await scheme(page), 'dark');
            const restoringHeader = await page.locator('header').first().boundingBox();
            await page.screenshot({ path: path.join(dir, `fe34-landing-restoring-${width}.png`) });
            await choose(page, 'Light'); assert.equal(await scheme(page), 'light');
            api.state.hold = null; api.state.release?.();
            await page.getByRole('heading', { level: 1, name: 'Learn programming at the pace of what you actually know.' }).waitFor();
            assert.equal(await themeButton(page).count(), 1);
            const landingHeader = await page.locator('header').first().boundingBox();
            assert.equal(Math.round(restoringHeader.height), Math.round(landingHeader.height), 'the header keeps its height');
            assert.deepEqual(api.state.requests.filter((entry) => entry.includes('/api/auth/')), ['POST /api/auth/refresh']);
            const { scroll, client } = await overflow(page); assert(scroll <= client, `overflow ${scroll} > ${client}`);
        });
        await context.close();
    }

    // A13 (DESIGN S26, ADR014 §7): two real tabs; the preview tab sends nothing when the other signs in or out.
    {
        const { context, page: preview } = await newPage();
        const previewRequests = [];
        preview.on('request', (request) => { if (new URL(request.url()).pathname.startsWith('/api/')) previewRequests.push(`${request.method()} ${new URL(request.url()).pathname}`); });
        await preview.goto(url('/preview?screen=learner-home')); await ready(preview);
        await check('A13: with /preview open, a sign-in and a sign-out in another real tab send no request from the preview and install no session there', async () => {
            const live = await context.newPage(); await live.goto(url('/login')); await ready(live);
            await live.locator('#login-email').fill('learner@example.test'); await live.locator('#login-password').fill('correct-password-123');
            await live.getByRole('button', { name: 'Sign in', exact: true }).click();
            await live.waitForURL(/\/home$/);
            await preview.waitForTimeout(500);
            await live.getByRole('button', { name: /^Account menu/ }).click();
            await live.getByRole('menuitem', { name: 'Sign out' }).click();
            await live.waitForURL(/\/login$/);
            await live.goto(url('/login')); await ready(live);
            await live.locator('#login-email').fill('learner@example.test'); await live.locator('#login-password').fill('correct-password-123');
            await live.getByRole('button', { name: 'Sign in', exact: true }).click();
            await live.waitForURL(/\/home$/);
            await preview.waitForTimeout(500);
            assert.deepEqual(previewRequests, []);
            assert.equal(new URL(preview.url()).pathname, '/preview');
            assert.equal(await preview.getByText('Restoring your session…').count(), 0);
            await live.close();
        });
        await context.close();
    }

    // Special modes.
    {
        const { context, page } = await newPage();
        await context.addInitScript(() => { try { localStorage.setItem('codemastery.theme', 'dark'); } catch { /* blocked */ } });
        await page.goto(url('/preview?screen=learner-home')); await ready(page);
        await check('dark surfaces: frame ink, lifted canvas, brighter tiles, mist text; printing always uses the light scheme', async () => {
            const surfaces = await page.evaluate(() => ({ body: getComputedStyle(document.body).backgroundColor, main: getComputedStyle(document.querySelector('main')).backgroundColor,
                text: getComputedStyle(document.querySelector('main h1')).color, header: getComputedStyle(document.querySelector('header')).backgroundColor }));
            assert.deepEqual(surfaces, { body: FRAME, main: CANVAS.dark, text: 'rgb(236, 237, 239)', header: FRAME });
            await page.emulateMedia({ media: 'print' });
            assert.equal(await mainBg(page), CANVAS.light);
            assert.equal(await page.evaluate(() => getComputedStyle(document.querySelector('main h1')).color), 'rgb(21, 23, 28)');
            // Native controls and system colours print light too: the print rule beats the inline color-scheme.
            assert.equal(await page.evaluate(() => getComputedStyle(document.documentElement).colorScheme), 'light');
            await page.emulateMedia({ media: 'screen' });
            assert.equal(await mainBg(page), CANVAS.dark);
            assert.equal(await page.evaluate(() => getComputedStyle(document.documentElement).colorScheme), 'dark');
        });
        await check('prefers-contrast: more strengthens hairlines and tertiary text in both schemes', async () => {
            // A context created with the preference: Playwright's Firefox updates matchMedia after a runtime
            // emulateMedia({ contrast }) but does not re-evaluate the stylesheets, so that path is engine-dependent.
            const { context: contrastContext, page: contrastPage } = await newPage({ contrast: 'more' });
            await contrastContext.addInitScript(() => { try { localStorage.setItem('codemastery.theme', 'dark'); } catch { /* blocked */ } });
            await contrastPage.goto(url('/preview?screen=learner-home')); await ready(contrastPage);
            const roleValues = () => contrastPage.evaluate(() => ['--mui-palette-roles-line-hairline', '--mui-palette-roles-text-tertiary']
                .map((name) => getComputedStyle(document.documentElement).getPropertyValue(name).trim().toUpperCase()));
            assert.deepEqual(await roleValues(), ['#7B818B', '#B4B8C0']);
            await choose(contrastPage, 'Light');
            assert.deepEqual(await roleValues(), ['#80858E', '#50545C']);
            await contrastContext.close();
        });
        if (engine === 'chromium') await check('forced colours: tinted states get a real border and keyboard focus keeps a visible outline (Chromium emulation)', async () => {
            await page.emulateMedia({ forcedColors: 'active' });
            const borders = await page.evaluate(() => [...document.querySelectorAll('[data-tone]')].slice(0, 5).map((node) => getComputedStyle(node).borderStyle));
            assert(borders.length > 0 && borders.every((style) => style !== 'none'), JSON.stringify(borders));
            const button = themeButton(page).first(); await button.focus(); await page.keyboard.press('Shift+Tab'); await page.keyboard.press('Tab');
            const ring = await page.evaluate(() => { const s = getComputedStyle(document.activeElement); return [s.outlineStyle, parseFloat(s.outlineWidth)]; });
            assert(ring[0] !== 'none' && ring[1] >= 2, JSON.stringify(ring));
            // Floating surfaces draw their edge with a shadow, which forced colours removes: they need a real line.
            const edge = (locator) => locator.evaluate((node) => { const s = getComputedStyle(node.closest('.MuiPaper-root')); return [s.outlineStyle, parseFloat(s.outlineWidth), s.borderStyle]; });
            await button.focus(); await page.keyboard.press('Enter');
            const themeMenu = page.getByRole('menu', { name: 'Theme' }); await themeMenu.waitFor();
            const themeEdge = await edge(themeMenu);
            assert(themeEdge[0] !== 'none' && themeEdge[1] >= 1 || themeEdge[2] !== 'none', `Theme menu edge ${themeEdge}`);
            await page.keyboard.press('Escape'); await themeMenu.waitFor({ state: 'hidden' });
            await page.getByRole('button', { name: /^Account menu/ }).click();
            const accountMenu = page.getByRole('menu').first(); await accountMenu.waitFor();
            const accountEdge = await edge(accountMenu);
            assert(accountEdge[0] !== 'none' && accountEdge[1] >= 1 || accountEdge[2] !== 'none', `account menu edge ${accountEdge}`);
            await page.keyboard.press('Escape'); await accountMenu.waitFor({ state: 'hidden' });
            await page.screenshot({ path: path.join(dir, 'fe34-forced-colors.png') });
            await page.emulateMedia({ forcedColors: 'none' });
        });
        await context.close();
    }
}

async function runEngine(playwright, engine, baseUrl) {
    // notExercised: scenarios that did not run (for example a verified browser-emulation limitation). They are neither
    // passes nor failures and are listed separately in the summary and the JSON report.
    const result = { engine, version: null, passed: [], failed: [], notExercised: [], screens: [] };
    const dir = path.join(out, engine); fs.mkdirSync(dir, { recursive: true });
    let browser;
    try { browser = await playwright[engine].launch({ headless: true }); }
    catch (error) { result.launchError = String(error.message || error).split('\n').slice(0, 6).join(' | '); return result; }
    result.version = browser.version();
    const url = (p) => new URL(p, baseUrl).href;
    const check = async (name, fn) => {
        try { await fn(); result.passed.push(name); console.log(`PASS [${engine}] ${name}`); }
        catch (error) { result.failed.push({ name, error: String(error.message || error).slice(0, 1500) }); console.log(`FAIL [${engine}] ${name}\n  ${String(error.message || error).split('\n')[0]}`); }
    };
    const notExercised = (scenario, reason) => { result.notExercised.push({ scenario, reason }); console.log(`NOT EXERCISED [${engine}] ${scenario}: ${reason}`); };
    const errors = [], unexpectedConsole = [];
    async function newPage(options = {}) {
        const context = await browser.newContext({ viewport: { width: 1280, height: 900 }, locale: 'en-US', timezoneId: 'UTC', ...options });
        const api = createApi();
        await context.route((u) => new URL(u).pathname.startsWith('/api/'), api.handle);
        const page = await context.newPage();
        page.on('pageerror', (error) => errors.push(`${engine}: ${error.message}`));
        page.on('console', (message) => {
            if (message.type() !== 'error') return;
            const location = message.location().url || '';
            if (/Failed to load resource|status of (400|401|404|500)/.test(message.text()) && location.includes('/api/')) return; // controlled API failures
            unexpectedConsole.push(`${engine}: ${message.text().slice(0, 300)}`);
        });
        page.on('dialog', (dialog) => dialog.accept());
        return { context, page, api };
    }
    const overflow = (page) => page.evaluate(() => ({ scroll: document.documentElement.scrollWidth, client: document.documentElement.clientWidth }));
    const ready = async (page) => { await page.locator('main h1, h1').first().waitFor({ timeout: 15000 }); await page.evaluate(() => document.fonts.ready); };

    try {
        // ── Assets, font and licence ────────────────────────────────────────
        {
            const { context, page } = await newPage();
            await page.goto(url('/courses')); await ready(page);
            await check('favicon, theme colour and canonical logo are served', async () => {
                const icon = await page.locator('link[rel="icon"]').getAttribute('href');
                assert.match(icon, /favicon\.ico\?v=[0-9a-f]{12}$/);
                assert((await context.request.get(url(icon))).ok()); assert((await context.request.get(url('/favicon.svg'))).ok());
                assert.equal(await page.locator('meta[name="theme-color"]').getAttribute('content'), '#0A0B0F');
                const logo = page.locator('header img').first(); await logo.waitFor();
                assert(await logo.evaluate((img) => img.complete && img.naturalWidth > 0));
            });
            await check('one UI face (Google Sans Flex), JetBrains Mono for code and the wordmark face load from this origin with their OFL texts; no retired face remains', async () => {
                const faces = await page.evaluate(async () => {
                    for (const weight of [400, 500, 600, 700]) { const loaded = await document.fonts.load(`${weight} 16px "Google Sans Flex"`, 'Learning 0123 Tiếng Việt Đ'); if (!loaded.length) throw new Error(`UI weight ${weight}`); }
                    if (!(await document.fonts.load('400 16px "JetBrains Mono"', 'for i in range(3):')).length) throw new Error('code face');
                    if (!(await document.fonts.load('800 20px "CodeMastery Wordmark"', 'CodeMastery')).length) throw new Error('wordmark face');
                    return [...new Set([...document.fonts].map((f) => f.family.replace(/["']/g, '')))];
                });
                assert.deepEqual(faces.sort(), ['CodeMastery Wordmark', 'Google Sans Flex', 'JetBrains Mono'], JSON.stringify(faces));
                const woff = await page.evaluate(() => performance.getEntriesByType('resource').filter((e) => e.name.includes('.woff2')).map((e) => new URL(e.name)));
                assert(woff.length > 0 && woff.every((u) => u.origin === new URL(baseUrl).origin));
                assert(woff.some((u) => /google-sans-flex-vietnamese/.test(u.pathname)), 'the Vietnamese subset is fetched for Vietnamese text');
                for (const [file, owner] of [['/google-sans-flex-OFL.txt', 'Copyright 2015 Google LLC'], ['/jetbrains-mono-OFL.txt', 'The JetBrains Mono Project Authors'], ['/poppins-OFL.txt', 'The Poppins Project Authors']]) {
                    const licence = await context.request.get(url(file)); assert(licence.ok(), file); const text = await licence.text();
                    assert(text.includes(owner) && text.includes('SIL OPEN FONT LICENSE Version 1.1'), file);
                }
                for (const gone of ['/figtree-OFL.txt', '/plus-jakarta-sans-OFL.txt']) assert(!((await context.request.get(url(gone))).headers()['content-type'] ?? '').includes('text/plain'), `${gone} is no longer served`);
                assert.equal((await page.locator('main h1').evaluate((h) => getComputedStyle(h).fontFamily)).split(',')[0].replace(/["']/g, '').trim(), 'Google Sans Flex');
            });
            await check('Vietnamese text, code and the wordmark render with the shipped faces, without fallback glyphs', async () => {
                const vietnamese = 'Đường đi học lập trình: ăn, ân, ên, ôn, ơn, ưng — Ấ Ầ Ẩ Ẫ Ậ ặ ệ ộ ợ ự ỹ ỵ ĐĂÂÊÔƠƯ 0123456789';
                await page.evaluate((text) => {
                    const p = document.createElement('p'); p.id = 'vi-probe'; p.textContent = text; p.style.fontSize = '18px'; document.querySelector('main').append(p);
                    const c = document.createElement('code'); c.id = 'code-probe'; c.textContent = 'for i in range(3): print(i) # chào'; document.querySelector('main').append(c);
                }, vietnamese);
                await page.evaluate(() => document.fonts.ready);
                assert(await page.evaluate((text) => document.fonts.check('16px "Google Sans Flex"', text), vietnamese));
                if (engine === 'chromium') {
                    const cdp = await context.newCDPSession(page); await cdp.send('DOM.enable'); await cdp.send('CSS.enable');
                    const { root } = await cdp.send('DOM.getDocument');
                    const rendered = async (selector) => {
                        const { nodeId } = await cdp.send('DOM.querySelector', { nodeId: root.nodeId, selector });
                        return (await cdp.send('CSS.getPlatformFontsForNode', { nodeId })).fonts;
                    };
                    const vi = await rendered('#vi-probe'), code = await rendered('#code-probe'), mark = await rendered('header [data-wordmark]');
                    const only = (fonts, family) => fonts.length > 0 && fonts.every((f) => f.familyName.startsWith(family) && f.isCustomFont);
                    assert(only(vi, 'Google Sans Flex'), `Vietnamese: ${JSON.stringify(vi)}`);
                    assert(only(code, 'JetBrains Mono'), `code: ${JSON.stringify(code)}`);
                    assert(only(mark, 'Poppins'), `wordmark: ${JSON.stringify(mark)}`);
                    await cdp.detach();
                }
                const mark = await page.locator('header [data-wordmark]').evaluate((e) => { const s = getComputedStyle(e); return { family: s.fontFamily, weight: s.fontWeight }; });
                const first = (family) => family.split(',')[0].replace(/["']/g, '').trim();
                assert(first(mark.family) === 'CodeMastery Wordmark' && mark.weight === '800', JSON.stringify(mark));
                const nav = await page.locator('header nav a').first().evaluate((e) => getComputedStyle(e).fontFamily);
                assert.equal(first(nav), 'Google Sans Flex', `navigation keeps the UI face: ${nav}`);
                await page.evaluate(() => { document.getElementById('vi-probe')?.remove(); document.getElementById('code-probe')?.remove(); });
            });
            await check('the logo mark is sharp and its colour regions stay distinct on the ink frame at 16–40 px', async () => {
                const regions = await page.evaluate(async () => {
                    const svg = await (await fetch('/favicon.svg')).text();
                    const doc = new DOMParser().parseFromString(svg, 'image/svg+xml');
                    return [...doc.querySelectorAll('linearGradient')].map((g) => ({ id: g.id, stops: [...g.querySelectorAll('stop')].map((s) => s.getAttribute('stop-color')) }));
                });
                const lum = (hex) => { const c = [1, 3, 5].map((i) => parseInt(hex.slice(i, i + 2), 16) / 255).map((x) => x <= 0.04045 ? x / 12.92 : ((x + 0.055) / 1.055) ** 2.4); return 0.2126 * c[0] + 0.7152 * c[1] + 0.0722 * c[2]; };
                const ink = lum('#0A0B0F');
                for (const region of regions) for (const stop of region.stops) assert((lum(stop) + 0.05) / (ink + 0.05) >= 3, `${region.id} ${stop} stands out from the ink frame`);
                const mean = (id) => { const stops = regions.find((r) => r.id === id).stops; return stops.reduce((sum, s) => sum + lum(s), 0) / stops.length; };
                assert(Math.abs(mean('nodeDark') - mean('cBlue')) > 0.1 && Math.abs(mean('nodeLight') - mean('cBlue')) > 0.1, 'nodes separate from the C');
                for (const size of [16, 24, 32, 40]) {
                    const shot = await page.evaluate(async (s) => {
                        const img = new Image(); img.src = '/favicon.svg'; await img.decode();
                        const c = document.createElement('canvas'); c.width = c.height = s; const x = c.getContext('2d'); x.fillStyle = '#0A0B0F'; x.fillRect(0, 0, s, s); x.drawImage(img, 0, 0, s, s);
                        const d = x.getImageData(0, 0, s, s).data; let lit = 0, violet = 0, sky = 0;
                        for (let i = 0; i < d.length; i += 4) { const [r, g, b] = [d[i], d[i + 1], d[i + 2]]; if (r + g + b > 150) lit++; if (r > 110 && b > 200 && g < 170) violet++; if (g > 150 && b > 200 && r < 120) sky++; }
                        return { lit, violet, sky };
                    }, size);
                    assert(shot.lit > size * 2 && shot.sky > 0 && (size < 24 || shot.violet > 0), `${size}px: ${JSON.stringify(shot)}`);
                }
            });
            await check('the landing page at / loads only its example-map chunk, never the preview, and sends only the anonymous session check', async () => {
                const { context: landingContext, page: landing, api: landingApi } = await newPage();
                await landing.goto(url('/')); await ready(landing);
                await landing.getByText('Illustration · not learner data').waitFor();
                const scripts = await landing.evaluate(() => performance.getEntriesByType('resource').map((e) => new URL(e.name).pathname));
                assert(scripts.some((s) => /ExampleMasteryMap/.test(s)), scripts.join(','));
                assert(!scripts.some((s) => /PreviewApp/.test(s)));
                assert.deepEqual(landingApi.state.requests, ['POST /api/auth/refresh']);
                const links = await landing.getByRole('navigation', { name: 'Main navigation' }).getByRole('link').evaluateAll((list) => list.map((a) => [a.textContent, a.getAttribute('href')]));
                assert.deepEqual(links, [['Catalogue', '/courses'], ['Sign in', '/login'], ['Create account', '/register']]);
                await landing.screenshot({ path: path.join(dir, 'landing-1280.png'), fullPage: true });
                await landingContext.close();
            });
            await check('production routes never load the preview chunk', async () => {
                const scripts = await page.evaluate(() => performance.getEntriesByType('resource').filter((e) => e.initiatorType === 'script' || e.name.endsWith('.js')).map((e) => new URL(e.name).pathname));
                assert(!scripts.some((s) => /PreviewApp/.test(s)), scripts.join(','));
                const html = await (await context.request.get(url('/courses'))).text();
                assert(!/Linh Pham|Sample data|loopsAnswerKey/.test(html));
            });
            await context.close();
        }

        // ── Preview: every screen, desktop and mobile ───────────────────────
        const screens = [];
        {
            const { context, page, api } = await newPage();
            await page.goto(url('/preview')); await ready(page);
            await check('preview index links every screen inside /preview and shows no preview chrome', async () => {
                await page.locator('[data-preview-screen="overview"]').waitFor();
                assert.equal(await page.locator('[aria-label="Design preview notice"], [aria-label="Preview workspace"]').count(), 0);
                const hrefs = await page.locator('a[href^="/preview?screen="]').evaluateAll((links) => [...new Set(links.map((a) => new URL(a.href).searchParams.get('screen')))]);
                screens.push(...hrefs.filter(Boolean));
                assert(screens.length >= 30, `found ${screens.length} screens`);
            });
            await check('the preview chunk loads only at /preview and sends no API request or storage write', async () => {
                const scripts = await page.evaluate(() => performance.getEntriesByType('resource').map((e) => new URL(e.name).pathname));
                assert(scripts.some((s) => /PreviewApp/.test(s)));
                assert.deepEqual(api.state.requests, []);
                assert.deepEqual(await page.evaluate(() => [localStorage.length, sessionStorage.length]), [0, 0]);
            });
            await page.locator('body').ariaSnapshot().then((snap) => fs.writeFileSync(path.join(dir, 'preview-index.aria.yml'), snap));
            await context.close();
        }
        for (const [width, height, label] of [[1280, 900, 'desktop'], [390, 844, 'mobile']]) {
            const { context, page, api } = await newPage({ viewport: { width, height } });
            const bad = [];
            for (const screen of screens) {
                await page.goto(url(`/preview?screen=${screen}`)); await ready(page);
                await page.waitForTimeout(150);
                const size = await overflow(page), h1 = await page.locator('h1').first().textContent();
                if (size.scroll > size.client) bad.push(`${screen}: ${size.scroll}>${size.client}`);
                result.screens.push({ screen, width, h1, overflow: size.scroll > size.client });
                await page.screenshot({ path: path.join(dir, `preview-${screen}-${label}.png`), fullPage: true });
            }
            await check(`all ${screens.length} preview screens render a heading without page overflow at ${width} px`, async () => { assert.deepEqual(bad, []); assert.deepEqual(api.state.requests, []); });
            await context.close();
        }

        // ── Live pages with controlled responses: widths and zoom ───────────
        const widths = [320, 360, 600, 768, 900, 1024, 1200, 1280, 1440, 1536];
        {
            const { context, page } = await newPage();
            const bad = [], nav = [];
            for (const width of widths) {
                await page.setViewportSize({ width, height: 900 });
                for (const route of ['/', '/courses', `/courses/${primaryId}`, '/login', '/register', '/no-such-page', '/preview?screen=mastery', '/preview?screen=admin-users', '/preview?screen=learners']) {
                    await page.goto(url(route)); await ready(page);
                    const size = await overflow(page);
                    if (size.scroll > size.client) bad.push(`${route}@${width}: ${size.scroll}>${size.client}`);
                }
                await page.goto(url('/courses')); await ready(page);
                const inline = await page.getByRole('navigation', { name: 'Main navigation' }).isVisible();
                const toggle = await page.getByRole('button', { name: 'Toggle navigation' }).isVisible();
                nav.push({ width, inline, toggle });
                if (inline !== width >= 900 || toggle !== width < 900) bad.push(`navigation mode at ${width}: inline=${inline} toggle=${toggle}`);
            }
            await check(`live and preview pages have no page overflow and the right navigation mode at ${widths.join(', ')} px`, async () => assert.deepEqual(bad, []));
            await context.close();
        }
        {
            // 200% browser zoom of a 1280 px window lays out at 640 CSS px with 2x pixels.
            const { context, page } = await newPage({ viewport: { width: 640, height: 450 }, deviceScaleFactor: 2 });
            const bad = [];
            for (const route of ['/', '/courses', `/courses/${primaryId}`, '/login', '/register', '/preview?screen=mastery', '/preview?screen=course', '/preview?screen=admin-users']) {
                await page.goto(url(route)); await ready(page);
                const size = await overflow(page); if (size.scroll > size.client) bad.push(`${route}: ${size.scroll}>${size.client}`);
            }
            await page.goto(url('/courses')); await ready(page);
            await page.screenshot({ path: path.join(dir, 'zoom-200-catalogue.png'), fullPage: true });
            await check('200% zoom (640 CSS px at 2x) keeps every checked page free of horizontal page scroll', async () => assert.deepEqual(bad, []));
            // Text-only zoom: root font size doubled at 1280 px.
            await page.setViewportSize({ width: 1280, height: 900 });
            const textBad = [];
            for (const route of ['/courses', `/courses/${primaryId}`, '/preview?screen=course']) {
                await page.goto(url(route)); await ready(page);
                await page.addStyleTag({ content: 'html { font-size: 200% !important; }' }); await page.waitForTimeout(100);
                const size = await overflow(page); if (size.scroll > size.client) textBad.push(`${route}: ${size.scroll}>${size.client}`);
            }
            await check('200% text size at 1280 px keeps the checked pages free of horizontal page scroll', async () => assert.deepEqual(textBad, []));
            await context.close();
        }

        // ── Contrast ─────────────────────────────────────────────────────────
        for (const scheme of ['light', 'dark']) {
            const { context, page } = await newPage();
            await context.addInitScript((value) => { try { localStorage.setItem('codemastery.theme', value); } catch { /* storage blocked */ } }, scheme);
            const failures = [];
            for (const [route, width] of [['/', 1280], ['/', 390], ['/courses', 1280], [`/courses/${primaryId}`, 1280], ['/login', 1280], ['/register', 390], ['/preview?screen=learner-home', 1280], ['/preview?screen=mastery', 1280], ['/preview?screen=assessments', 1280], ['/preview?screen=quiz-result', 1280], ['/preview?screen=admin-overview', 1280], ['/preview?screen=admin-users', 390], ['/preview?screen=notifications', 1280], ['/preview?screen=course', 390]]) {
                await page.setViewportSize({ width, height: 900 });
                await page.goto(url(route)); await ready(page); await page.waitForTimeout(200);
                for (const row of await page.evaluate(sampleText)) {
                    const ratio = contrast(row.color, row.bg), need = row.large ? 3 : 4.5;
                    if (ratio < need) failures.push(`${route}@${width} "${row.text}" ${ratio.toFixed(2)} < ${need}`);
                }
            }
            await check(`visible text meets 4.5:1 (3:1 large) against its effective background on fourteen sampled screens (${scheme} scheme)`, async () => assert.deepEqual([...new Set(failures)].slice(0, 25), []));
            await context.close();
        }

        // ── FE34 colour schemes and the Theme control (DESIGN r12 › Themes) ──
        await themeChecks({ engine, check, notExercised, newPage, ready, overflow, url, dir });

        // ── Keyboard: skip link, focus visibility, focus order ──────────────
        {
            const { context, page } = await newPage();
            await page.goto(url('/courses')); await ready(page);
            await check('skip link is the first tab stop, becomes visible on focus and moves focus to main', async () => {
                const first = await page.evaluate(() => document.querySelector('a[href], button:not([disabled]), input, [tabindex]:not([tabindex="-1"])')?.textContent);
                assert.equal(first, 'Skip to main content');
                const skip = page.getByRole('link', { name: 'Skip to main content' });
                await skip.focus(); const box = await skip.boundingBox(); assert(box && box.y >= 0 && box.height > 0);
                await page.keyboard.press('Enter');
                assert.equal(await page.evaluate(() => document.activeElement?.id), 'main-content');
            });
            await check('route change moves focus to the new page heading', async () => {
                await page.getByRole('link', { name: 'Introduction to Programming' }).click();
                await page.getByRole('heading', { level: 1, name: 'Introduction to Programming' }).waitFor();
                assert.equal(await page.evaluate(() => document.activeElement?.tagName), 'H1');
            });
            await check('every keyboard focus stop on the catalogue shows a visible indicator', async () => {
                await page.goto(url('/courses')); await ready(page);
                await page.locator('main h1').focus();
                const missing = [];
                for (let i = 0; i < 14; i++) {
                    await page.keyboard.press('Tab');
                    const info = await page.evaluate(() => { const el = document.activeElement; if (!el || el === document.body) return null; const s = getComputedStyle(el); const tile = el.closest('article'); const ts = tile ? getComputedStyle(tile) : null;
                        return { name: (el.getAttribute('aria-label') || el.textContent || el.tagName).trim().slice(0, 40), outline: s.outlineStyle !== 'none' && parseFloat(s.outlineWidth) >= 2, shadow: s.boxShadow !== 'none', tileShadow: !!ts && ts.boxShadow !== 'none', input: el.tagName === 'INPUT' }; });
                    if (info && !(info.outline || info.shadow || info.tileShadow || info.input)) missing.push(info.name);
                }
                assert.deepEqual(missing, []);
            });
            await context.close();
        }

        // ── Mobile drawer ───────────────────────────────────────────────────
        {
            const { context, page } = await newPage({ viewport: { width: 390, height: 844 } });
            await page.goto(url('/courses')); await ready(page);
            await check('the landing header keeps the lockup, Sign in and the Theme button at 390 px; Browse courses reaches the catalogue (D9-7)', async () => {
                try {
                    await page.goto(url('/')); await ready(page);
                    const nav = page.getByRole('navigation', { name: 'Main navigation' });
                    assert(await nav.getByRole('link', { name: 'Sign in' }).isVisible());
                    assert(await nav.getByRole('button', { name: /^Theme: / }).isVisible());
                    assert(!(await nav.getByRole('link', { name: 'Catalogue' }).isVisible()), 'Catalogue is hidden below 600 px');
                    assert(await page.getByRole('main').getByRole('link', { name: 'Browse courses' }).isVisible());
                    assert(await page.getByRole('main').getByRole('link', { name: 'Create account' }).isVisible());
                } finally { await page.goto(url('/courses')); await ready(page); }
            });
            await check('mobile drawer opens, marks the current item, closes on Escape and returns focus', async () => {
                const toggle = page.getByRole('button', { name: 'Toggle navigation' });
                await toggle.click(); const drawer = page.getByRole('dialog'); await drawer.waitFor();
                assert.equal(await drawer.getByRole('link', { name: 'Catalogue' }).getAttribute('aria-current'), 'page');
                assert(await drawer.getByRole('link', { name: 'Create account' }).isVisible());
                await page.keyboard.press('Escape'); await drawer.waitFor({ state: 'hidden' });
                assert.equal(await toggle.evaluate((el) => el === document.activeElement), true);
            });
            await check('an open drawer closes when the window crosses the 900 px breakpoint', async () => {
                await page.getByRole('button', { name: 'Toggle navigation' }).click(); await page.getByRole('dialog').waitFor();
                await page.setViewportSize({ width: 1024, height: 844 }); await page.getByRole('dialog').waitFor({ state: 'hidden' });
                assert(await page.getByRole('navigation', { name: 'Main navigation' }).isVisible());
            });
            await context.close();
        }

        // ── Dialogs: focus, trap, restore, full-screen sheet ────────────────
        for (const [width, sheet] of [[1280, false], [390, true]]) {
            const { context, page } = await newPage({ viewport: { width, height: 844 } });
            await page.goto(url('/preview?screen=admin-users')); await ready(page);
            await check(`destructive dialog focuses Cancel, traps Tab, restores focus on Escape${sheet ? ' and fills the screen below 600 px' : ''} (${width} px)`, async () => {
                const trigger = page.getByRole('button', { name: 'Actions for Linh Pham' });
                await trigger.click(); await page.getByRole('menuitem', { name: 'Lock account' }).click();
                const dialog = page.getByRole('dialog', { name: /Lock Linh Pham/ }); await dialog.waitFor();
                await page.waitForTimeout(350);
                assert.equal(await page.evaluate(() => document.activeElement?.textContent), 'Cancel');
                for (let i = 0; i < 4; i++) { await page.keyboard.press('Tab'); assert(await dialog.evaluate((d) => d.contains(document.activeElement)), 'focus left the dialog'); }
                const box = await dialog.boundingBox();
                if (sheet) assert(box.width >= width - 1 && box.height >= 843, JSON.stringify(box)); else assert(box.width <= 560 + 1, JSON.stringify(box));
                await page.keyboard.press('Escape'); await dialog.waitFor({ state: 'hidden' });
                await page.waitForTimeout(350);
                assert.equal(await trigger.evaluate((el) => el === document.activeElement), true, 'focus returns to the row menu trigger');
            });
            await context.close();
        }

        // ── Forms: labels, errors, announcements, pagehide blur ─────────────
        {
            const { context, page } = await newPage();
            await page.goto(url('/register')); await ready(page);
            await check('registration labels every field, marks required ones in text and announces client validation', async () => {
                for (const id of ['register-displayName', 'register-email', 'register-password', 'register-confirm']) {
                    const marker = await page.locator(`label[for="${id}"] .MuiFormLabel-asterisk`).evaluate((a) => getComputedStyle(a, '::after').content);
                    assert.equal(marker, '" (required)"', id);
                    assert.equal(await page.locator(`#${id}`).evaluate((input) => input.required), true, `${id} keeps the required attribute`);
                }
                await page.getByRole('button', { name: 'Create account', exact: true }).click();
                const alert = page.getByRole('alert').first(); await alert.waitFor();
                assert((await alert.textContent()).trim().length > 0, 'an error summary is announced');
                const focused = await page.evaluate(() => ({ id: document.activeElement?.id, first: document.querySelector('form [aria-invalid="true"]')?.id }));
                assert(focused.id && focused.id === focused.first, `focus moves to the first invalid field: ${JSON.stringify(focused)}`);
                assert.equal(await page.locator('#register-email').getAttribute('aria-invalid'), 'true');
                const described = await page.locator('#register-email').getAttribute('aria-describedby');
                assert(described && (await page.locator(`[id="${described.split(' ')[0]}"]`).textContent()).trim().length > 0);
            });
            await page.goto(url('/login')); await ready(page);
            await check('sign-in announces a server rejection in an alert and keeps the email', async () => {
                await page.locator('#login-email').fill('learner@example.test'); await page.locator('#login-password').fill('wrong-password-123');
                await page.getByRole('button', { name: 'Sign in', exact: true }).click();
                const alert = page.getByRole('alert').first(); await alert.waitFor();
                assert.equal((await alert.textContent()).trim(), 'Authentication failed.'); // the client's safe 401 message; server detail is not echoed
                assert.equal(await page.locator('#login-email').inputValue(), 'learner@example.test');
            });
            await check('a focused password input is blurred on pagehide without clearing the form', async () => {
                await page.locator('#login-password').focus();
                await page.evaluate(() => dispatchEvent(new PageTransitionEvent('pagehide', { persisted: true })));
                assert.notEqual(await page.evaluate(() => document.activeElement?.id), 'login-password');
                assert.equal(await page.locator('#login-email').inputValue(), 'learner@example.test');
            });
            await context.close();
        }

        // ── Catalogue semantics and error states with controlled responses ──
        {
            const { context, page, api } = await newPage();
            await page.goto(url('/courses')); await ready(page);
            await check('catalogue search waits for submit; the prerequisite filter applies on selection', async () => {
                await page.getByRole('list', { name: 'Published courses' }).waitFor();
                const before = api.state.requests.length;
                await page.getByRole('searchbox', { name: 'Search courses' }).fill('introduction'); await page.waitForTimeout(500);
                assert.equal(api.state.requests.length, before, 'typing alone sends nothing');
                await page.getByRole('button', { name: 'Search', exact: true }).click();
                await page.getByText('1 course', { exact: true }).waitFor();
                assert.match(api.state.requests.at(-1), /q=introduction/);
                await page.getByRole('button', { name: 'No prerequisites' }).click();
                await page.getByRole('heading', { name: 'No courses match these filters' }).waitFor();
                assert.match(api.state.requests.at(-1), /prerequisites=none/);
            });
            await check('catalogue and detail failures show recoverable, specific states', async () => {
                api.state.fail.set('/courses', 500);
                await page.goto(url('/courses')); await page.getByRole('heading', { name: "Couldn't load the catalogue" }).waitFor();
                api.state.fail.delete('/courses');
                await page.getByRole('button', { name: 'Try again' }).click(); await page.getByRole('list', { name: 'Published courses' }).waitFor();
                await page.goto(url('/courses/not-a-uuid')); await page.getByRole('heading', { level: 1, name: 'Course details' }).waitFor();
                await page.getByText("Couldn't load this course").waitFor();
                await page.goto(url('/courses/abcdefab-cdef-4abc-8def-abcdefabcdef')); await page.getByRole('heading', { level: 1, name: 'Course not available' }).waitFor();
            });
            await check('loading skeletons shimmer for 1.2 s and name what is loading, without a spinner', async () => {
                api.state.hold = /^\/courses$/;
                await page.goto(url('/courses'));
                await page.locator('.MuiSkeleton-root').first().waitFor();
                const style = await page.locator('.MuiSkeleton-root').first().evaluate((el) => { const s = getComputedStyle(el); return { name: s.animationName, duration: s.animationDuration }; });
                assert.equal(style.name, 'cm-shimmer'); assert.equal(style.duration, '1.2s');
                assert(await page.getByRole('status').filter({ hasText: /Loading available courses/ }).count() > 0);
                assert.equal(await page.locator('main .MuiCircularProgress-root, main [role="progressbar"]').count(), 0, 'no spinner while content loads');
                api.state.hold = null; api.state.release?.('continue');
                await page.getByRole('list', { name: 'Published courses' }).waitFor();
            });
            await context.close();
        }
        {
            const { context, page, api } = await newPage({ reducedMotion: 'reduce' });
            await check('reduced motion makes skeletons static and transitions instant', async () => {
                api.state.hold = /^\/courses$/;
                await page.goto(url('/courses')); await page.locator('.MuiSkeleton-root').first().waitFor();
                const skeleton = await page.locator('.MuiSkeleton-root').first().evaluate((el) => getComputedStyle(el).animationName);
                assert.equal(skeleton, 'none');
                api.state.hold = null; api.state.release?.('continue');
                await page.getByRole('list', { name: 'Published courses' }).waitFor();
                const transition = await page.getByRole('button', { name: 'Search', exact: true }).evaluate((el) => getComputedStyle(el).transitionDuration);
                assert(transition.split(',').every((d) => parseFloat(d) <= 0.00001 * 1000), transition);
            });
            await context.close();
        }

        // ── Signed-in Learner pages with controlled responses ───────────────
        for (const width of [1280, 390]) {
            const { context, page, api } = await newPage({ viewport: { width, height: 844 } });
            await page.goto(url('/login')); await ready(page);
            await page.locator('#login-email').fill('learner@example.test'); await page.locator('#login-password').fill('correct-password-123');
            // A Learner signs in to Learner home (Leader instruction, 7 November 2026; other roles keep /courses).
            await page.getByRole('button', { name: 'Sign in', exact: true }).click(); await page.waitForURL(/\/home$/);
            await check(`sign-in lands a Learner on Learner home, which lists active memberships only, without pending sections (${width} px)`, async () => {
                await page.getByRole('heading', { level: 1, name: 'Welcome back, Harness Learner' }).waitFor();
                if (width >= 900) assert.equal(await page.getByRole('navigation', { name: 'Main navigation' }).getByRole('link', { name: 'Home' }).getAttribute('aria-current'), 'page');
                const list = page.getByRole('list', { name: 'Your active courses' }); await list.waitFor();
                assert.equal(await list.getByRole('listitem').count(), 1);
                assert(api.state.requests.some((r) => r.startsWith('GET /api/me/courses') && r.includes('status=active')));
                assert.equal(await page.getByText(/Continue learning|Up next/).count(), 0);
                const size = await overflow(page); assert(size.scroll <= size.client);
                await page.screenshot({ path: path.join(dir, `home-${width}.png`), fullPage: true });
            });
            await check(`My courses tile menu leaves with confirmation and re-enrolls (${width} px)`, async () => {
                if (width < 900) { await page.getByRole('button', { name: 'Toggle navigation' }).click(); await page.getByRole('dialog').getByRole('link', { name: 'My courses' }).click(); }
                else await page.getByRole('navigation', { name: 'Main navigation' }).getByRole('link', { name: 'My courses' }).click();
                await page.getByRole('heading', { level: 1, name: 'My courses' }).waitFor();
                const menu = page.getByRole('button', { name: 'More actions for Introduction to Programming' });
                await menu.click(); await page.getByRole('menuitem', { name: 'Leave course' }).click();
                const dialog = page.getByRole('dialog', { name: 'Leave course?' }); await dialog.waitFor(); await page.waitForTimeout(350);
                assert.equal(await page.evaluate(() => document.activeElement?.textContent), 'Cancel');
                if (width < 600) { const box = await dialog.boundingBox(); assert(box.width >= width - 1, 'full-screen sheet'); }
                await dialog.getByRole('button', { name: 'Leave course' }).click();
                await page.getByRole('status').filter({ hasText: 'You left Introduction to Programming.' }).waitFor();
                assert(api.state.requests.some((r) => r.startsWith(`DELETE /api/courses/${primaryId}/membership`)));
                await page.getByRole('button', { name: 'Left', exact: true }).click();
                await page.getByRole('button', { name: 'More actions for Introduction to Programming' }).click();
                await page.getByRole('menuitem', { name: 'Re-enroll' }).click();
                await page.getByRole('status').filter({ hasText: 'You re-enrolled in Introduction to Programming.' }).waitFor();
                const size = await overflow(page); assert(size.scroll <= size.client);
                await page.screenshot({ path: path.join(dir, `my-courses-${width}.png`), fullPage: true });
            });
            await context.close();
        }

        // ── Mastery Map graph ───────────────────────────────────────────────
        {
            const { context, page } = await newPage({ viewport: { width: 1440, height: 900 } });
            await page.goto(url('/preview?screen=mastery')); await ready(page);
            await check('Mastery Map nodes are 220 × 72, the first skills start in view, and Tab/Enter/Escape operate the graph', async () => {
                const canvas = page.locator('[role="group"][aria-label^="Skill map"]'); await canvas.waitFor();
                await page.waitForFunction(() => document.querySelector('[role="group"][aria-label^="Skill map"]')?.getAttribute('data-ready') === 'true');
                const nodes = canvas.locator('button[aria-pressed]');
                const size = await nodes.first().evaluate((el) => ({ w: el.offsetWidth, h: el.offsetHeight }));
                assert.deepEqual(size, { w: 220, h: 72 });
                const frame = await canvas.boundingBox(), first = await nodes.first().boundingBox();
                assert(first.x >= frame.x && first.x + first.width <= frame.x + frame.width && first.y >= frame.y, 'first node visible');
                const names = await nodes.evaluateAll((list) => list.map((n) => n.getAttribute('aria-label')));
                assert(names.every((n) => /, (Unknown|Learning|Mastered|Weak), \d+ prerequisites?/.test(n)), names.join(' | '));
                await nodes.first().focus(); await page.keyboard.press('Tab');
                assert.equal(await nodes.nth(1).evaluate((el) => el === document.activeElement), true, 'Tab moves to the next node in layer order');
                await page.keyboard.press('Enter'); assert.equal(await nodes.nth(1).getAttribute('aria-pressed'), 'true');
                await page.keyboard.press('Escape'); assert.equal(await nodes.nth(1).getAttribute('aria-pressed'), 'false');
                await page.screenshot({ path: path.join(dir, 'mastery-map-1440.png'), fullPage: true });
            });
            await context.close();
        }

        // ── Active navigation: concave curved bottom-edge indicator ──────────
        {
            // The reach is an opening cut into the header by its CSS mask (no painted element): read its geometry
            // from the mask itself and verify the opening with screenshot pixels.
            const geometry = (page) => page.evaluate(() => {
                const header = document.querySelector('header[data-canvas-reach]'), active = document.querySelector('header nav [aria-current="page"]');
                if (!header || !active) return null;
                const st = getComputedStyle(header), h = header.getBoundingClientRect(), a = active.getBoundingClientRect();
                const image = st.maskImage || st.webkitMaskImage, size = (st.maskSize || st.webkitMaskSize).split(',')[1].trim().split(' ').map(parseFloat);
                const composite = st.maskComposite || st.webkitMaskComposite;
                const svg = decodeURIComponent(image.match(/url\("data:image\/svg\+xml,([^"]+)"\)/)[1]);
                const d = svg.match(/ d="([^"]+)"/)[1];
                const path = document.createElementNS('http://www.w3.org/2000/svg', 'path'); path.setAttribute('d', d);
                const holder = document.createElementNS('http://www.w3.org/2000/svg', 'svg'); holder.append(path); document.body.append(holder);
                const total = path.getTotalLength(), pts = [];
                for (let i = 0; i <= 200; i++) { const q = path.getPointAtLength((total * i) / 200); pts.push([q.x, q.y]); }
                holder.remove();
                const w = size[0], curve = pts.filter(([, y]) => y < 14 - 1e-3);
                const asym = Math.max(...curve.map(([x, y]) => Math.min(...curve.filter(([, yy]) => Math.abs(yy - y) < 0.2).map(([xx]) => Math.abs(xx - (w - x))))));
                const x = Number(header.dataset.reachX);
                return { reachCentre: h.left + x + w / 2, activeCentre: a.left + a.width / 2, width: size[0], height: size[1], headerBottom: h.bottom, headerLeft: h.left, x,
                    crest: Math.min(...pts.map(([, y]) => y)), asym, composite, for: header.dataset.reachFor, label: active.textContent,
                    painted: document.querySelectorAll('header [data-canvas-reach], header svg[data-reach]').length };
            });
            /** RGB of screenshot pixels at viewport points (decoded in the page through a canvas). */
            const pixels = async (page, points) => {
                const png = (await page.screenshot()).toString('base64');
                return page.evaluate(async ({ png, points }) => {
                    const img = new Image(); img.src = `data:image/png;base64,${png}`; await img.decode();
                    const c = document.createElement('canvas'); c.width = img.naturalWidth; c.height = img.naturalHeight; const x = c.getContext('2d'); x.drawImage(img, 0, 0);
                    const scale = img.naturalWidth / innerWidth;
                    return points.map(([px, py]) => [...x.getImageData(Math.round(px * scale), Math.round(py * scale), 1, 1).data].slice(0, 3).join(','));
                }, { png, points });
            };
            const settled = async (page) => { await page.waitForTimeout(400); return geometry(page); };
            const assertAligned = (g, label) => {
                assert(g, `${label}: indicator present`);
                assert(Math.abs(g.reachCentre - g.activeCentre) <= 1, `${label}: centred under the active item ${JSON.stringify(g)}`);
                assert.equal(g.width, 88); assert.equal(g.height, 15); assert(g.crest < 0.01, `${label}: crest reaches the top (${g.crest})`); assert(g.asym < 0.6, `${label}: symmetric (${g.asym})`);
                assert.match(g.composite, /exclude|xor/, `${label}: the bell is subtracted from the header`); assert.equal(g.painted, 0, `${label}: nothing painted to fake the opening`);
            };
            const { context, page } = await newPage({ viewport: { width: 1280, height: 900 } });
            await check('the active tab carries one symmetric concave reach that follows clicks, child routes and history', async () => {
                await page.goto(url('/preview?screen=admin-overview')); await ready(page);
                let g = await settled(page); assertAligned(g, 'Overview'); assert.equal(g.label, 'Overview');
                await page.screenshot({ path: path.join(dir, 'nav-reach-admin-1280.png'), clip: { x: 0, y: 0, width: 1280, height: 120 } });
                await page.getByRole('navigation', { name: 'Main navigation' }).getByRole('link', { name: 'Reports' }).click();
                g = await settled(page); assertAligned(g, 'Reports'); assert.equal(g.label, 'Reports');
                await page.goBack(); g = await settled(page); assert.equal(g.label, 'Overview'); assertAligned(g, 'back');
                await page.goForward(); g = await settled(page); assert.equal(g.label, 'Reports'); assertAligned(g, 'forward');
                await page.goto(url('/preview?screen=teaching')); await ready(page); g = await settled(page); assertAligned(g, 'Teaching');
                await page.goto(url('/preview?screen=course-editor&item=c-intro')); await ready(page); g = await settled(page); assert.equal(g.label, 'Teaching', 'a child screen keeps its parent current'); assertAligned(g, 'child');
                await page.goto(url('/preview?screen=learner-home')); await ready(page); g = await settled(page); assertAligned(g, 'Learner Home');
                await page.goto(url('/courses')); await ready(page); g = await settled(page); assertAligned(g, 'Catalogue');
                await page.getByRole('link', { name: 'Introduction to Programming' }).click(); await page.getByRole('heading', { level: 1, name: 'Introduction to Programming' }).waitFor();
                g = await settled(page); assert.equal(g.label, 'Catalogue'); assertAligned(g, 'course detail');
                await page.screenshot({ path: path.join(dir, 'nav-reach-catalogue-1280.png'), clip: { x: 0, y: 0, width: 1280, height: 120 } });
            });
            await check('icon-only navigation at 1024 px keeps the reach under the active item; below 900 px it is not drawn', async () => {
                await page.setViewportSize({ width: 1024, height: 800 }); await page.goto(url('/preview?screen=admin-users')); await ready(page);
                assertAligned(await settled(page), '1024');
                await page.screenshot({ path: path.join(dir, 'nav-reach-admin-1024.png'), clip: { x: 0, y: 0, width: 1024, height: 110 } });
                await page.setViewportSize({ width: 390, height: 844 }); await page.waitForTimeout(300);
                assert.equal(await page.locator('header[data-canvas-reach]').count(), 0, 'no reach below 900 px');
                const size = await overflow(page); assert(size.scroll <= size.client);
            });
            await check('the reach is a real opening: the canvas shows through it seamlessly, and content scrolling under the bar shows through too', async () => {
                await page.setViewportSize({ width: 1280, height: 900 }); await page.goto(url('/preview?screen=my-courses')); await ready(page);
                const g = await settled(page); assertAligned(g, 'My courses');
                const cx = g.reachCentre, bottom = g.headerBottom;
                const [crest, above, shoulder, edge, below, seamLeft, seamRight] = await pixels(page, [[cx, bottom - 3], [cx, bottom - 18], [cx - 60, bottom - 3], [cx, bottom - 1], [cx, bottom + 1], [cx - 30, bottom - 0.5], [cx + 30, bottom - 0.5]]);
                const canvas = '246,246,247', ink = '10,11,15';
                assert.equal(crest, canvas, 'inside the bell: the canvas'); assert.equal(above, ink, 'above the crest: the bar');
                assert.equal(shoulder, ink, 'beside the bell: the bar'); assert.equal(edge, canvas, 'no seam where the opening meets the bar edge');
                assert.equal(below, canvas, 'below the bar: the same canvas'); assert.equal(seamLeft, seamRight, 'symmetric at the base');
                // Scroll so a white tile passes under the sticky bar: the opening now shows the tile, not a fixed colour.
                // (The catalogue is long enough to scroll a tile under the bar; the reach sits under "Catalogue".)
                await page.goto(url('/courses')); await ready(page); await page.locator('main article').first().waitFor();
                const cat = await settled(page); assertAligned(cat, 'Catalogue');
                const tileTop = await page.evaluate((x) => { const t = [...document.querySelectorAll('main article')].find((el) => { const r = el.getBoundingClientRect(); return r.left < x && r.right > x; }); return t ? t.getBoundingClientRect().top + scrollY : null; }, cat.reachCentre);
                assert(tileTop, 'a tile lies under the reach');
                await page.evaluate((y) => scrollTo(0, y), tileTop - cat.headerBottom + 44); await page.waitForTimeout(300);
                const g2 = await geometry(page);
                const [through] = await pixels(page, [[g2.reachCentre, g2.headerBottom - 3]]);
                assert.equal(through, '255,255,255', `the scrolled tile shows through the opening (${through})`);
                await page.screenshot({ path: path.join(dir, 'nav-reach-scrolled-1280.png'), clip: { x: Math.max(0, g2.reachCentre - 160), y: 0, width: 320, height: 120 } });
                await page.evaluate(() => scrollTo(0, 0));
            });
            await context.close();
        }

        // ── Brand lockup, preview notice and course marks (7 November 2026) ─
        {
            const lockup = (page) => page.evaluate(() => {
                const header = document.querySelector('header'), img = header.querySelector('img'), mark = header.querySelector('[data-wordmark]');
                const brand = img.closest('a') || img.parentElement, b = brand.getBoundingClientRect();
                // Every other control to the right of the lockup (the mobile menu button sits to its left).
                const after = [...header.querySelectorAll('a, button')].filter((el) => !brand.contains(el) && !el.contains(brand))
                    .map((el) => el.getBoundingClientRect()).filter((r) => r.width > 0 && r.left >= b.left);
                return { bar: header.getBoundingClientRect().height, img: img.getBoundingClientRect().height, natural: img.naturalWidth,
                    text: parseFloat(getComputedStyle(mark).fontSize), brandRight: b.right, nextLeft: Math.min(...after.map((r) => r.left)) };
            });
            const { context, page } = await newPage({ viewport: { width: 1280, height: 900 } });
            await check('the header lockup is larger and proportional (40 px mark, 25 px wordmark; fixed 36 px and 32 px steps on phones, no viewport units) and never crowds the navigation', async () => {
                for (const [width, mark, bar] of [[1280, 40, 80], [1024, 40, 80], [600, 40, 80], [599, 36, 64], [390, 36, 64], [389, 32, 64], [375, 32, 64], [360, 32, 64]]) {
                    await page.setViewportSize({ width, height: 844 });
                    for (const route of ['/courses', '/preview?screen=learner-home', '/']) {
                        await page.goto(url(route)); await ready(page);
                        const l = await lockup(page);
                        assert.equal(l.img, mark, `${width} ${route}: mark ${JSON.stringify(l)}`); assert(l.natural > 0, `${route}: mark decoded`);
                        assert.equal(l.text, mark * 0.625, `${width} ${route}: wordmark ${JSON.stringify(l)}`);
                        assert.equal(l.bar, bar, `${width} ${route}: bar height unchanged`);
                        assert(l.brandRight + 8 <= l.nextLeft, `${width} ${route}: brand clear of the next control ${JSON.stringify(l)}`);
                        const size = await overflow(page); assert(size.scroll <= size.client, `${width} ${route}: no page overflow`);
                    }
                    await page.goto(url('/courses')); await ready(page);
                    await page.screenshot({ path: path.join(dir, `brand-lockup-${width}.png`), clip: { x: 0, y: 0, width, height: bar + 24 } });
                }
                await page.setViewportSize({ width: 1280, height: 900 });
            });
            await check('preview screens carry no preview chrome (no notice, switcher, index or reset button)', async () => {
                for (const screen of ['overview', 'my-courses', 'admin-overview', 'sign-in', 'forgot-password', 'quiz-attempt']) {
                    await page.goto(url(`/preview?screen=${screen}`)); await ready(page);
                    assert.equal(await page.locator('[aria-label="Design preview notice"], [aria-label="Preview workspace"]').count(), 0, `${screen}: no notice or switcher`);
                    for (const name of ['Reset sample data', 'Preview index', 'Simulate time running out']) assert.equal(await page.getByRole('button', { name, exact: true }).count(), 0, `${screen}: ${name}`);
                    assert.equal(await page.getByText('Design preview · Sample data.').count(), 0, `${screen}: no sample-data line`);
                    assert.equal(await page.locator('header svg[data-reach], header [data-canvas-reach] > svg').count(), 0, `${screen}: no painted reach shape`);
                }
                await page.goto(url('/preview?screen=my-courses')); await ready(page);
                await page.screenshot({ path: path.join(dir, 'preview-top-1280.png'), clip: { x: 0, y: 0, width: 1280, height: 260 } });
            });
            await check('the signed-in account avatar is solid Deep Logo Blue with a white initial (no gradient)', async () => {
                await page.goto(url('/preview?screen=learner-home')); await ready(page);
                const avatar = await page.locator('header [data-monogram]').evaluate((el) => { const st = getComputedStyle(el); return { tone: el.dataset.monogram, bg: st.backgroundColor, image: st.backgroundImage, color: st.color, text: el.textContent }; });
                assert.equal(avatar.tone, 'brand'); assert.equal(avatar.bg, 'rgb(6, 82, 196)'); assert.equal(avatar.image, 'none'); assert.equal(avatar.color, 'rgb(255, 255, 255)'); assert.equal(avatar.text, 'L');
            });
            await check('course marks: one neutral container; official full-colour logos optically sized, otherwise a Deep Logo Blue code glyph; never letters, ink or gradients', async () => {
                const marks = () => page.evaluate(() => [...document.querySelectorAll('[data-course-mark]')].map((el) => {
                    const c = el.getBoundingClientRect(), img = el.querySelector('img'), s = getComputedStyle(el);
                    return { kind: el.dataset.courseMark, bg: s.backgroundColor, image: s.backgroundImage, color: s.color, text: el.textContent, radius: s.borderRadius, w: c.width, h: c.height,
                        img: img ? { loaded: img.complete && img.naturalWidth > 0, w: img.getBoundingClientRect().width, h: img.getBoundingClientRect().height,
                            inside: img.getBoundingClientRect().bottom <= c.bottom + 0.5 && img.getBoundingClientRect().top >= c.top - 0.5 } : null };
                }));
                await page.goto(url('/preview?screen=overview')); await ready(page);
                const sample = await marks();
                assert.deepEqual([...new Set(sample.map((m) => m.kind))].sort(), ['generic', 'python', 'typescript']);
                const neutral = (m) => m.bg === 'rgb(246, 246, 247)' && m.image === 'none';
                const generic = (m) => m.kind === 'generic' && neutral(m) && m.color === 'rgb(6, 82, 196)' && m.text === '';
                for (const m of sample) {
                    assert(m.kind === 'generic' ? generic(m) : neutral(m), JSON.stringify(m)); assert.equal(m.w, m.h);
                    if (m.img) { assert(m.img.loaded, `${m.kind} decoded`); assert(m.img.inside, `${m.kind} stays inside its container`); }
                }
                // Colour is kept: render the 48 px marks and find both official Python hues and TypeScript blue.
                const hues = await page.evaluate(async () => {
                    const out = {};
                    for (const el of document.querySelectorAll('[data-course-mark="python"] img, [data-course-mark="typescript"] img')) {
                        const img = new Image(); img.src = el.src; await img.decode();
                        const c = document.createElement('canvas'); c.width = 96; c.height = 96; const x = c.getContext('2d'); x.drawImage(img, 0, 0, 96, 96 * img.naturalHeight / img.naturalWidth);
                        const d = x.getImageData(0, 0, 96, 96).data; let blue = 0, yellow = 0;
                        for (let i = 0; i < d.length; i += 4) { const [r, g, b, a] = [d[i], d[i + 1], d[i + 2], d[i + 3]]; if (a < 200) continue; if (b > 150 && r < 90) blue++; if (r > 220 && g > 190 && b < 120) yellow++; }
                        out[el.closest('[data-course-mark]').dataset.courseMark] = { blue, yellow };
                    }
                    return out;
                });
                assert(hues.python.blue > 50 && hues.python.yellow > 50, `python colours ${JSON.stringify(hues)}`); assert(hues.typescript.blue > 1000, `typescript colour ${JSON.stringify(hues)}`);
                await page.locator('[aria-label="Course mark samples"]').screenshot({ path: path.join(dir, 'course-marks-1280.png') });
                // Catalogue doubles name no language, so no logo is guessed. The sample "Introduction to Programming"
                // (Python) shows the same mark in the Learner and Instructor workspaces; Learner home rows reuse it at 32 px.
                for (const [route, label, kinds, size] of [['/courses', 'catalogue', null, 48], ['/preview?screen=my-courses', 'my courses (sample)', ['python', 'generic', 'generic'], 48],
                    ['/preview?screen=teaching', 'teaching (sample)', ['python', 'generic'], 48], ['/preview?screen=learner-home', 'learner home rows (sample)', ['python', 'generic'], 32]]) {
                    await page.goto(url(route)); await ready(page);
                    const live = await marks(); assert(live.length > 0, `${label}: course marks shown`);
                    for (const m of live) { assert(neutral(m), `${label}: neutral container ${JSON.stringify(m)}`); assert.equal(m.w, size, label); }
                    if (kinds) assert.deepEqual(live.map((m) => m.kind), kinds, label);
                    else assert(live.every(generic), `${label}: ${JSON.stringify(live.map((m) => m.kind))}`);
                }
                await page.goto(url('/preview?screen=teaching')); await ready(page);
                await page.screenshot({ path: path.join(dir, 'course-marks-teaching-1280.png'), clip: { x: 0, y: 0, width: 1280, height: 600 } });
                await page.setViewportSize({ width: 390, height: 844 }); await page.goto(url('/courses')); await ready(page);
                const mobile = await marks(); assert(mobile.length > 0 && mobile.every((m) => m.w === 48 && generic(m)));
                await page.screenshot({ path: path.join(dir, 'course-marks-catalogue-390.png') });
            });
            await check('button hierarchy: graphite primary, outlined secondary, blue tertiary, red destructive, with hover, focus and disabled states', async () => {
                await page.setViewportSize({ width: 1280, height: 900 });
                await page.goto(url('/preview?screen=overview')); await ready(page);
                const style = (name) => page.getByRole('button', { name, exact: true }).first().evaluate((el) => { const s = getComputedStyle(el); return { bg: s.backgroundColor, color: s.color, border: `${s.borderTopWidth} ${s.borderTopStyle} ${s.borderTopColor}`, outline: `${s.outlineWidth} ${s.outlineStyle} ${s.outlineColor}` }; });
                const rest = { primary: await style('Primary action'), secondary: await style('Secondary'), tertiary: await style('Text link action'), danger: await style('Delete'), disabled: await style('Disabled') };
                assert.equal(rest.primary.bg, 'rgb(28, 30, 36)'); assert.equal(rest.primary.color, 'rgb(255, 255, 255)');
                assert.equal(rest.secondary.bg, 'rgb(255, 255, 255)'); assert.equal(rest.secondary.border, '1px solid rgb(228, 228, 231)'); assert.equal(rest.secondary.color, 'rgb(21, 23, 28)');
                assert.equal(rest.tertiary.bg, 'rgba(0, 0, 0, 0)'); assert.equal(rest.tertiary.color, 'rgb(8, 102, 242)');
                assert.equal(rest.danger.bg, 'rgb(180, 35, 24)'); assert.equal(rest.disabled.bg, 'rgb(238, 238, 240)');
                await page.getByRole('button', { name: 'Primary action', exact: true }).hover(); await page.waitForTimeout(200);
                assert.equal((await style('Primary action')).bg, 'rgb(42, 45, 53)', 'primary hover lightens to chrome-hover');
                await page.getByRole('button', { name: 'Secondary', exact: true }).hover(); await page.waitForTimeout(200);
                assert.equal((await style('Secondary')).bg, 'rgb(238, 238, 240)', 'secondary hover fills fill-150');
                await page.mouse.move(0, 0);
                await page.getByRole('button', { name: 'Secondary', exact: true }).focus(); await page.keyboard.press('Shift+Tab'); await page.keyboard.press('Tab');
                assert.match((await style('Secondary')).outline, /^2px solid rgb\(8, 102, 242\)$/, 'keyboard focus keeps the 2 px Logo Blue ring');
                await page.locator('main').getByText('Primary action').scrollIntoViewIfNeeded();
                await page.locator('main').getByText('Primary action').locator('xpath=ancestor::*[contains(@class,"MuiPaper-root") or self::div][1]').screenshot({ path: path.join(dir, 'buttons-1280.png') }).catch(() => undefined);
            });
            await check('consistency fixes: flat map nodes, 4/6 dashes, one instructor mention, current bell, 280 px outline', async () => {
                await page.setViewportSize({ width: 1280, height: 900 });
                await page.goto(url('/preview?screen=mastery')); await ready(page); await page.locator('.react-flow__node').first().waitFor();
                const graph = await page.evaluate(() => ({
                    nodes: [...document.querySelectorAll('.react-flow__node button')].map((node) => getComputedStyle(node).boxShadow),
                    dashes: [...new Set([...document.querySelectorAll('.react-flow__edge-path')].map((edge) => getComputedStyle(edge).strokeDasharray))],
                }));
                assert(graph.nodes.some((shadow) => /inset/.test(shadow)) && !graph.nodes.some((shadow) => /0px 1px 2px/.test(shadow)), `resting nodes are flat with a hairline: ${JSON.stringify(graph.nodes)}`);
                assert(graph.dashes.includes('4px, 6px'), `edge dashes ${JSON.stringify(graph.dashes)}`);
                await page.goto(url('/preview?screen=course')); await ready(page);
                const spine = await page.locator('[data-path-line="dashed"]').first().evaluate((el) => getComputedStyle(el).backgroundImage);
                assert.match(spine, /4px.*10px/, `path line dash ${spine}`);
                await page.goto(url(`/courses/${primaryId}`)); await ready(page);
                assert.equal(await page.getByText('Instructor Demo', { exact: false }).count(), 1, 'instructor named once');
                await page.goto(url('/preview?screen=notifications')); await ready(page);
                assert.equal(await page.locator('header [aria-label^="Notifications"]').getAttribute('aria-current'), 'page');
                await page.setViewportSize({ width: 1536, height: 900 }); await page.goto(url('/preview?screen=lesson')); await ready(page);
                // The reading grid's first column is the outline: presentationTokens.outlineWidth (280 px).
                const grid = await page.evaluate(() => [...document.querySelectorAll('main div')].map((el) => getComputedStyle(el)).filter((st) => st.display === 'grid').map((st) => st.gridTemplateColumns).find((cols) => /^\d+px \d/.test(cols) && parseFloat(cols) < 500) ?? null);
                assert.match(String(grid), /^280px /, `lesson outline column ${grid}`);
                await page.setViewportSize({ width: 1280, height: 900 });
            });
            await context.close();
        }

        // ── Accessibility snapshots for review ──────────────────────────────
        {
            const { context, page } = await newPage();
            for (const [name, route] of [['catalogue', '/courses'], ['detail', `/courses/${primaryId}`], ['login', '/login'], ['register', '/register'], ['mastery', '/preview?screen=mastery'], ['quiz-attempt', '/preview?screen=quiz-attempt'], ['admin-users', '/preview?screen=admin-users']]) {
                await page.goto(url(route)); await ready(page);
                fs.writeFileSync(path.join(dir, `${name}.aria.yml`), await page.locator('body').ariaSnapshot());
            }
            await context.close();
        }
        await check('no uncaught page errors', async () => assert.deepEqual(errors, []));
        await check('no unexpected console errors', async () => assert.deepEqual(unexpectedConsole.slice(0, 20), []));
    } finally {
        await browser.close();
    }
    return result;
}


/** Build the actual shell with synthetic route descriptors outside tracked source. */
function createFE32Fixture(dist) {
    const temp = fs.mkdtempSync(path.join(os.tmpdir(), 'codemastery-fe32-fixture-'));
    try {
        fs.symlinkSync(path.join(frontendRoot, 'node_modules'), path.join(temp, 'node_modules'), 'dir');
        const source = String.raw`import React, { useState, useEffect, useLayoutEffect } from 'react';
import { createRoot } from 'react-dom/client';
import { ThemeProvider, CssBaseline, Button, MenuItem } from '@mui/material';
import { BrowserRouter, useLocation, useNavigate } from 'react-router';
import { DashboardLayout, Page } from '__FRONTEND__/src/layouts/DashboardLayout';
import { AccountMenu, HeaderIconButton } from '__FRONTEND__/src/shared/components/AppHeader';
import { PresentationIcon } from '__FRONTEND__/src/shared/components/PresentationIcon';
import { roleNavigation, currentSection, notificationsAvailable } from '__FRONTEND__/src/routes/routePaths';
import { appTheme, ThemePreferenceContext, readStoredThemePreference, readSystemScheme, applyThemeScheme, resolveThemeScheme, writeStoredThemePreference } from '__FRONTEND__/src/app/theme/theme';
const paths:any = {
 Learner:['/home','/my-courses','/courses/:courseId/learn'],
 Instructor:['/teach','/teach/grading','/teach/grading/:submissionId','/teach/learners','/teach/courses/:courseId/learners'],
 Administrator:['/admin','/admin/users','/admin/courses','/admin/reports','/admin/audit']
};
const routes:any = [{path:'/courses'},{path:'/courses/:courseId'},...Object.entries(paths).map(([role,ps]:any)=>({handle:{roles:[role]},children:ps.map((path:string)=>({path}))})),{path:'/notifications',handle:{roles:['Learner','Instructor']}}];
function Shell(){
 const location=useLocation(),navigate=useNavigate();const query=new URLSearchParams(location.search);const role:any=query.get('role')||'Administrator';
 const long=query.get('name')!=='short';const name=long?'Alexandria Nguyễn Montgomery':'Alex Nguyen';
 const selected=currentSection(location.pathname,role,routes);
 const navItems=roleNavigation(role,routes).map(item=>({...item,icon:<PresentationIcon name={item.icon}/>}));
 const go=(path:string)=>navigate(path+location.search);
 return <DashboardLayout navItems={navItems} selectedNavId={selected} onNavSelect={go} brandHref={'/courses'+location.search} onBrandSelect={()=>go('/courses')}
  headerUtilities={notificationsAvailable(role,routes)?<HeaderIconButton label="Notifications" icon="bell" href={'/notifications'+location.search} current={selected==='/notifications'} onClick={(e:any)=>{e?.preventDefault();go('/notifications')}}/>:undefined}
  headerActions={<AccountMenu displayName={name} role={role} renderItems={close=><><MenuItem onClick={close}>Profile</MenuItem><MenuItem onClick={close}>Sign out</MenuItem></>}/>}
 ><Page><h1>Future-capacity fixture: {role}</h1><p>Actual reviewed shell; synthetic delivered-route descriptors and content. No production activation.</p><Button onClick={()=>go('/unmatched')}>Unmatched fixture route</Button></Page></DashboardLayout>
}
function Fixture(){
 const [preference,setPref]=useState(readStoredThemePreference),[systemScheme,setSystem]=useState(readSystemScheme);
 const scheme=resolveThemeScheme(preference,systemScheme);
 useLayoutEffect(()=>applyThemeScheme(scheme),[scheme]);
 useEffect(()=>{let m=matchMedia('(prefers-color-scheme: dark)');let h=()=>setSystem(readSystemScheme());m.addEventListener('change',h);return()=>m.removeEventListener('change',h)},[]);
 return <ThemePreferenceContext.Provider value={{preference,systemScheme,setPreference:(v:any)=>{setPref(v);writeStoredThemePreference(v)},previewPreference:null,setPreviewPreference:()=>{}}}><ThemeProvider theme={appTheme} defaultMode="light" storageManager={null} colorSchemeNode={null}><CssBaseline/><BrowserRouter><Shell/></BrowserRouter></ThemeProvider></ThemePreferenceContext.Provider>
}
createRoot(document.getElementById('root')!).render(<Fixture/>);
`.replaceAll('__FRONTEND__', frontendRoot);
        fs.writeFileSync(path.join(temp, 'fixture.tsx'), source);
        fs.writeFileSync(path.join(temp, 'index.html'), fs.readFileSync(path.join(frontendRoot, 'index.html'), 'utf8').replace('/src/main.tsx', '/fixture.tsx'));
        const config = `import { defineConfig } from ${JSON.stringify(path.join(frontendRoot, 'node_modules/vite/dist/node/index.js'))};
import react from ${JSON.stringify(path.join(frontendRoot, 'node_modules/@vitejs/plugin-react/dist/index.js'))};
export default defineConfig({root:${JSON.stringify(temp)},plugins:[react()],publicDir:${JSON.stringify(path.join(frontendRoot, 'public'))},build:{outDir:${JSON.stringify(dist)},emptyOutDir:true}});`;
        fs.writeFileSync(path.join(temp, 'vite.config.mjs'), config);
        execFileSync(process.execPath, [path.join(frontendRoot, 'node_modules/vite/bin/vite.js'), 'build', '--config', path.join(temp, 'vite.config.mjs')], { cwd: frontendRoot, stdio: 'inherit' });
    } finally { fs.rmSync(temp, { recursive: true, force: true }); }
}

/** B1-F01/F03: assert real rectangles and hit regions, not only document overflow. */
async function workspaceGeometry(page) {
    return page.evaluate(() => {
        const visible = (e) => { const r = e.getBoundingClientRect(); return r.width > 0 && r.height > 0 && getComputedStyle(e).visibility !== 'hidden'; };
        const box = (e) => { const r = e.getBoundingClientRect(); return { x: r.x, y: r.y, width: r.width, height: r.height, right: r.right, bottom: r.bottom }; };
        const header = document.querySelector('header');
        const items = [...header.querySelectorAll('[data-nav-item]')].filter(visible);
        const brand = header.querySelector('[aria-label="CodeMastery home"]');
        const utilities = [...header.querySelectorAll('[aria-label^="Notifications"], [data-theme-control="button"], [data-account-menu]')].filter(visible);
        const controls = [brand, ...items, ...utilities].filter((e) => e && visible(e));
        const overlaps = [];
        for (let i = 0; i < controls.length; i++) for (let j = i + 1; j < controls.length; j++) {
            const a = box(controls[i]), b = box(controls[j]);
            const width = Math.min(a.right, b.right) - Math.max(a.x, b.x), height = Math.min(a.bottom, b.bottom) - Math.max(a.y, b.y);
            if (width > .5 && height > .5) overlaps.push({ first: controls[i].getAttribute('aria-label') || controls[i].textContent, second: controls[j].getAttribute('aria-label') || controls[j].textContent, width, height });
        }
        const active = items.find((e) => e.getAttribute('aria-current') === 'page');
        const reach = header.dataset.reachX === undefined ? null : Number(header.dataset.reachX);
        const account = header.querySelector('[data-account-menu]');
        return {
            scrollWidth: document.documentElement.scrollWidth, clientWidth: document.documentElement.clientWidth,
            effectiveScheme: document.documentElement.dataset.colorScheme,
            frame: getComputedStyle(header).backgroundColor, fontsLoaded: document.fonts.check('16px "Google Sans Flex"'),
            overlaps, reachError: active && reach !== null ? reach + 44 - (box(active).x - box(header).x + box(active).width / 2) : null,
            reach, brand: brand && box(brand), utilities: utilities.map((e) => ({ name: e.getAttribute('aria-label'), box: box(e) })),
            account: account && { box: box(account), role: account.children[1]?.lastElementChild?.textContent,
                roleFits: account.children[1]?.lastElementChild?.scrollWidth <= account.children[1]?.lastElementChild?.clientWidth + 1 },
            items: items.map((e) => {
                const r = box(e);
                const points = [[r.x + 1, r.y + r.height / 2], [r.x + r.width / 2, r.y + r.height / 2], [r.right - 1, r.y + r.height / 2], [r.x + r.width / 2, r.y + 1], [r.x + r.width / 2, r.bottom - 1]];
                return { label: e.textContent.trim(), box: r, current: e.getAttribute('aria-current'), iconOnly: !!e.querySelector('.cm-visually-hidden'),
                    clipped: e.scrollWidth > e.clientWidth + 1, hits: points.map(([x, y]) => { const hit = document.elementFromPoint(x, y); return hit === e || e.contains(hit); }) };
            }),
        };
    });
}
function assertWorkspaceGeometry(measured, width) {
    assert(measured.scrollWidth <= measured.clientWidth + 1, 'horizontal document overflow');
    assert.equal(measured.frame, 'rgb(10, 11, 15)');
    assert(measured.fontsLoaded, 'font not loaded');
    assert.deepEqual(measured.overlaps, [], 'overlapping shell controls');
    if (width < 900) { assert.equal(measured.items.length, 0); assert.equal(measured.reach, null); return; }
    for (const item of measured.items) {
        assert(item.box.width >= 44 && item.box.height >= 44, `${item.label}: target below44x44`);
        assert(Math.abs(item.box.height - 44) <= .5, `${item.label}: pill height`);
        assert(item.hits.every(Boolean), `${item.label}: obstructed center/edge hit`);
        assert(!item.clipped, `${item.label}: clipped`);
        assert.equal(item.iconOnly, width < 1200 && item.current !== 'page', `${item.label}: responsive label`);
    }
    assert.equal(measured.items.filter((item) => item.current === 'page').length, 1);
    assert(measured.reachError !== null && Math.abs(measured.reachError) <= 1, 'reach alignment');
    if (measured.account) assert(measured.account.roleFits, 'account role clipped');
}

/** Focused FE32 regression mode; controlled APIs, actual production shell and a temporary full-capacity fixture. */
async function runFE32Engine(playwright, engine, baseUrl, fixtureUrl, selection = 'all') {
    const result = { engine, selection, version: null, passed: [], failed: [], notExercised: [], measurements: [] };
    const dir = path.join(out, engine); fs.mkdirSync(dir, { recursive: true });
    let browser;
    try { browser = await playwright[engine].launch({ headless: true }); }
    catch (error) { result.launchError = String(error); return result; }
    result.version = browser.version();
    const check = async (name, run) => {
        try { await run(); result.passed.push(name); console.log(`PASS [${engine}] ${name}`); }
        catch (error) { result.failed.push({ name, error: String(error) }); console.log(`FAIL [${engine}] ${name}: ${String(error)}`); }
    };
    const ready = async (page) => { await page.locator('header').first().waitFor(); await page.evaluate(() => document.fonts.ready); await page.waitForTimeout(300); };
    const longName = 'Alexandria Nguyễn Montgomery';
    try {
        for (const track of selection === 'focus' ? [] : ['production', 'future-fixture']) for (const role of track === 'production' ? ['Anonymous', 'Learner', 'Instructor', 'Administrator'] : ['Learner', 'Instructor', 'Administrator']) {
            for (const width of [360, 899, 900, 1199, 1200, 1440]) for (const preference of ['light', 'dark', 'system']) {
                const context = await browser.newContext({ viewport: { width, height: 900 }, colorScheme: preference === 'system' ? 'dark' : 'light' });
                await context.addInitScript((value) => localStorage.setItem('codemastery.theme', value), preference);
                const api = createApi(); Object.assign(api.state, { role, displayName: longName, restore: role !== 'Anonymous' });
                await context.route('**/api/**', api.handle);
                const page = await context.newPage();
                const route = track === 'production' ? '/courses' : (role === 'Learner' ? '/home' : role === 'Instructor' ? '/teach' : '/admin');
                const name = `FE32 ${track} ${role} ${width} ${preference}`;
                await check(name, async () => {
                    await page.goto(new URL(route + (track === 'future-fixture' ? `?role=${role}` : ''), track === 'production' ? baseUrl : fixtureUrl).href);
                    await ready(page);
                    const measured = await workspaceGeometry(page);
                    result.measurements.push({ name, track, role, width, height: 900, preference, route, measured });
                    fs.writeFileSync(path.join(dir, `${track}-${role}-${width}-${preference}.json`), JSON.stringify(measured, null, 2));
                    if ([360, 900, 1200, 1440].includes(width) && preference === 'light') await page.screenshot({ path: path.join(dir, `${track}-${role}-${width}.png`) });
                    assertWorkspaceGeometry(measured, width);
                    assert.equal(measured.effectiveScheme, preference === 'light' ? 'light' : 'dark');
                    if (width < 900) {
                        await page.getByRole('button', { name: 'Toggle navigation' }).click();
                        await page.getByRole('radio', { name: 'Dark', exact: true }).scrollIntoViewIfNeeded();
                        assert(await page.getByRole('radio', { name: 'Dark', exact: true }).isVisible());
                        assert.equal(await page.locator('.MuiDrawer-paper [aria-current="page"]').count(), 1);
                        await page.keyboard.press('Escape');
                    }
                });
                await context.close();
            }
        }
        // Every possible active full-menu item, with both short and long names, at the capacity boundaries.
        for (const role of selection === 'focus' ? [] : ['Instructor', 'Administrator']) for (const width of [900, 1200]) for (const displayName of ['short', 'long']) {
            const context = await browser.newContext({ viewport: { width, height: 900 } }), page = await context.newPage();
            try {
                await page.goto(new URL(`${role === 'Instructor' ? '/teach' : '/admin'}?role=${role}&name=${displayName}`, fixtureUrl).href); await ready(page);
                const labels = await page.locator('header nav a').allTextContents();
                for (const label of labels) await check(`FE32 active capacity ${role} ${width} ${displayName} ${label}`, async () => {
                    const link = page.locator('header nav').getByRole('link', { name: label, exact: true });
                    await link.focus(); await page.keyboard.press('Enter'); await page.waitForTimeout(300);
                    const measured = await workspaceGeometry(page); assertWorkspaceGeometry(measured, width);
                    result.measurements.push({ name: `active ${role} ${width} ${displayName} ${label}`, track: 'future-fixture', role, width, displayName, measured });
                });
            } finally { await context.close(); }
        }
        // The actual owned-Course route, with a held GET; a fixture heading cannot satisfy these checks.
        for (const scenario of ['delayed', 'user-focus', 'user-input', 'navigation-away', 'session-replacement', 'background-refresh']) {
            await check(`FE32 route focus ${scenario}`, async () => {
                const context = await browser.newContext({ viewport: { width: 1200, height: 900 } });
                const api = createApi(); Object.assign(api.state, { role: 'Instructor', displayName: longName, restore: true });
                await context.route('**/api/**', api.handle);
                let hold = true, acknowledgeRead;
                let readStarted = new Promise((done) => { acknowledgeRead = done; });
                const heldReads = new Set();
                const release = () => { hold = false; for (const done of heldReads) done(); heldReads.clear(); };
                const armRead = () => { hold = true; readStarted = new Promise((done) => { acknowledgeRead = done; }); };
                const waitForRead = async () => {
                    let timeout;
                    try {
                        await Promise.race([readStarted, new Promise((_, reject) => {
                            timeout = setTimeout(() => reject(new Error('owned Course GET did not start')), 7000);
                        })]);
                    } finally { clearTimeout(timeout); }
                };
                let course = { id: primaryId, title: 'Delayed owned Course', description: 'Synthetic Course for focus regression.', publicationStatus: 'hidden', prerequisiteDescription: null, chapters: [] };
                await context.route('**/api/instructor/courses**', async (route) => {
                    const p = new URL(route.request().url()).pathname;
                    if (p === '/api/instructor/courses') return route.fulfill(ok({ content: [{ id: primaryId, title: course.title, description: course.description, publicationStatus: 'hidden' }], page: 0, size: 20, totalElements: 1, totalPages: 1 }));
                    if (p.endsWith('/skill-graph')) return route.fulfill(ok({ courseId: primaryId, skills: [], prerequisites: [], mappings: [] }));
                    if (p === `/api/instructor/courses/${primaryId}`) {
                        if (route.request().method() === 'PUT') { course = { ...course, ...route.request().postDataJSON() }; return route.fulfill(ok(course)); }
                        if (hold) await new Promise((done) => { heldReads.add(done); acknowledgeRead(); });
                        return route.fulfill(ok(course)).catch(() => undefined);
                    }
                    return route.fallback();
                });
                const page = await context.newPage(); page.setDefaultTimeout(7000);
                try {
                    await page.goto(new URL('/teach', baseUrl).href); await page.getByRole('link', { name: new RegExp(course.title) }).click();
                    // The route handler acknowledges its held request; no polling or timing guess.
                    await waitForRead();
                    const account = page.getByRole('button', { name: /^Account menu/ });
                    if (scenario === 'user-focus' || scenario === 'user-input') {
                        await account.focus();
                        if (scenario === 'user-input') await page.keyboard.press('ArrowDown');
                    }
                    if (scenario === 'navigation-away') { await page.locator('header nav').getByRole('link', { name: 'Catalogue', exact: true }).click(); await ready(page); }
                    if (scenario === 'session-replacement') {
                        api.state.role = 'Learner'; api.state.sessionId = '00000000-0000-4000-8000-000000000099';
                        // BroadcastChannel does not deliver to its sender, so send from a second document.
                        const peer = await context.newPage(); await peer.goto(new URL('/preview', baseUrl).href);
                        await peer.evaluate(() => { const channel = new BroadcastChannel('codemastery-auth'); channel.postMessage({ type: 'signed-in' }); channel.close(); }); await peer.close();
                        await page.getByRole('heading', { name: "You don't have access to this page" }).waitFor();
                    }
                    release();
                    if (scenario === 'navigation-away' || scenario === 'session-replacement') {
                        await page.waitForTimeout(100); assert.equal(await page.getByRole('heading', { name: course.title, exact: true }).count(), 0);
                    } else {
                        const heading = page.getByRole('heading', { name: course.title, exact: true }); await heading.waitFor();
                        const focused = await heading.evaluate((e) => e === document.activeElement);
                        assert.equal(focused, scenario === 'delayed' || scenario === 'background-refresh');
                        if (scenario === 'background-refresh') {
                            await page.getByRole('button', { name: 'Settings', exact: true }).click();
                            const description = page.getByLabel('Description');
                            await description.fill('Acknowledged description');
                            armRead();
                            await page.getByRole('button', { name: 'Save changes', exact: true }).click();
                            await page.getByText('Settings saved. Refreshing course…', { exact: true }).waitFor();
                            await waitForRead();
                            await description.fill('Ongoing unsaved input');
                            release();
                            await page.waitForTimeout(150);
                            assert(await description.evaluate((e) => e === document.activeElement), 'background GET stole input focus');
                            assert.equal(await description.inputValue(), 'Ongoing unsaved input');
                        }
                    }
                    await page.screenshot({ path: path.join(dir, `focus-${scenario}.png`) });
                } finally { release(); await context.close(); }
            });
        }
    } finally { await browser.close(); }
    return result;
}

/** FE28: production notifications only, controlled REST doubles; backend evidence is separate. */
async function runFE28Engine(playwright, engine, baseUrl) {
    const result = { engine, version: null, passed: [], failed: [], measurements: [], notExercised: ['Real browser-to-backend delivery', 'VoiceOver/NVDA and physical devices'] };
    const dir = path.join(out, engine); fs.mkdirSync(dir, { recursive: true });
    let browser;
    try { browser = await playwright[engine].launch({ headless: true }); }
    catch (error) { result.launchError = String(error); return result; }
    result.version = browser.version();
    const check = async (name, run) => {
        try { await run(); result.passed.push(name); console.log(`PASS [${engine}] ${name}`); }
        catch (error) { result.failed.push({ name, error: String(error) }); console.log(`FAIL [${engine}] ${name}: ${String(error)}`); }
    };
    const fixtures = () => Array.from({ length: 23 }, (_, index) => {
        const date = new Date(); date.setDate(date.getDate() - (index < 2 ? 0 : index < 4 ? 1 : 7));
        return { id: `00000000-0000-3000-8000-${String(index + 1).padStart(12, '0')}`, read: false, createdAt: date.toISOString(),
            message: index === 0 ? `<b>Plain text</b>\n${'W'.repeat(180)} in "Course".` : `Notification ${index + 1} in "Programming".` };
    });
    const open = async (preference = 'light', width = 1280, role = 'Learner') => {
        const context = await browser.newContext({ viewport: { width, height: 900 }, colorScheme: 'dark' });
        await context.addInitScript((value) => localStorage.setItem('codemastery.theme', value), preference);
        const page = await context.newPage(), api = createApi(); api.state.role = role; api.state.notifications = fixtures();
        await page.route('**/api/**', api.handle);
        await page.goto(new URL('/login', baseUrl).href);
        await page.locator('input[name="email"]').fill('synthetic@example.com'); await page.locator('input[name="password"]').fill('synthetic-password');
        await page.getByRole('button', { name: 'Sign in', exact: true }).click();
        await page.waitForURL((url) => url.pathname !== '/login');
        return { context, page, api };
    };
    const ready = async (page) => { await page.locator('#notification-results').waitFor(); await page.waitForFunction(() => document.querySelector('#notification-results')?.getAttribute('aria-busy') === 'false'); await page.evaluate(() => document.fonts.ready); };
    try {
        await check('production journey: count-only shell, explicit reads, pagination, Unread and concurrent mark-all', async () => {
            const { context, page, api } = await open();
            try {
                await page.getByRole('link', { name: 'Notifications, 23 unread', exact: true }).waitFor();
                assert(!api.state.requests.some((value) => /GET \/api\/me\/notifications\?/.test(value)), 'Bell mounted without fetching a list');
                await page.getByRole('link', { name: 'Notifications, 23 unread', exact: true }).click(); await ready(page);
                assert.equal(await page.locator('h1').textContent(), 'Notifications');
                assert.equal(await page.locator('header [aria-current="page"]').getAttribute('href'), '/notifications');
                assert.equal(api.state.requests.filter((value) => value.startsWith('PUT ')).length, 0);
                assert.equal(await page.locator('#notification-results li').count(), 20);
                assert.equal(await page.locator('#notification-results a, #notification-results b').count(), 0);
                assert.equal(await page.locator('#notification-results li p').first().evaluate((node) => getComputedStyle(node).fontSize), '18px');
                await page.getByRole('button', { name: 'Go to page 2', exact: true }).click(); await ready(page);
                assert.equal(await page.locator('#notification-results li').count(), 3);
                await page.getByRole('button', { name: 'Unread', exact: true }).click(); await ready(page);
                assert(api.state.requests.some((value) => value.includes('unreadOnly=true&page=0&size=20')));
                await page.getByRole('button', { name: 'Mark as read', exact: true }).first().click();
                await page.getByRole('link', { name: 'Notifications, 22 unread', exact: true }).waitFor(); await ready(page);
                api.state.newAfterMarkAll = { id: '00000000-0000-3000-8000-999999999999', read: false, message: 'Concurrent new notification', createdAt: new Date().toISOString() };
                await page.getByRole('button', { name: 'Mark all as read', exact: true }).click();
                await page.getByText('Marked 22 notifications as read.', { exact: true }).waitFor();
                await page.getByRole('link', { name: 'Notifications, 1 unread', exact: true }).waitFor(); await ready(page);
                assert.equal(await page.locator('#notification-results li').count(), 1);
                await page.getByRole('button', { name: 'All', exact: true }).click(); await ready(page);
                assert.equal(await page.locator('#notification-results li p').nth(1).evaluate((node) => getComputedStyle(node).fontSize), '16px');
                api.state.fail.set('/me/notifications/unread-count', 500);
                await page.locator('header a[href="/courses"]').click();
                await page.getByRole('link', { name: 'Notifications', exact: true }).waitFor();
                assert.equal(await page.locator('header [aria-label*="unread"]').count(), 0);
                assert.equal(await page.locator('[role="alert"]').count(), 0);
            } finally { await context.close(); }
        });
        for (const preference of ['light', 'dark', 'system']) for (const width of [360, 768, 1024, 1280, 1440]) {
            await check(`${preference} ${width}px: reflow, grouping, roles, keyboard and visible focus`, async () => {
                const { context, page } = await open(preference, width);
                try {
                    await page.locator('header a[href="/notifications"]').click(); await ready(page);
                    const overflow = await page.evaluate(() => ({ scroll: document.documentElement.scrollWidth, width: innerWidth, scheme: document.documentElement.dataset.colorScheme }));
                    assert(overflow.scroll <= width + 1, JSON.stringify(overflow));
                    assert.equal(overflow.scheme, preference === 'system' ? 'dark' : preference);
                    assert.deepEqual(await page.locator('#notification-results h2').allTextContents(), ['Today', 'Yesterday', new Date(Date.now() - 7 * 86400000).toLocaleDateString('en-US', { dateStyle: 'medium' })]);
                    const button = page.getByRole('button', { name: 'Unread', exact: true });
                    await page.getByRole('button', { name: 'All', exact: true }).focus(); await page.keyboard.press('ArrowRight');
                    await ready(page); assert.equal(await button.getAttribute('aria-pressed'), 'true');
                    assert.equal(await page.evaluate(() => document.activeElement.textContent), 'Unread');
                    const focus = await button.evaluate((node) => ({ outline: getComputedStyle(node).outlineStyle, width: getComputedStyle(node).outlineWidth, focus: node.matches(':focus-visible') }));
                    assert(focus.focus && focus.outline !== 'none' && parseFloat(focus.width) >= 2, JSON.stringify(focus));
                    const targets = await page.locator('#notification-results button').first().boundingBox(); assert(targets && targets.width >= 44 && targets.height >= 44);
                    const colors = await page.evaluate(sampleText); const low = colors.filter((item) => contrast(item.color, item.bg) < (item.large ? 3 : 4.5)); assert.equal(low.length, 0, JSON.stringify(low));
                    if (width >= 1024) {
                        const order = await page.locator('header :is(a,button)[aria-label]').evaluateAll((nodes) => nodes.map((node) => node.getAttribute('aria-label')).filter((value) => /^(Notifications|Theme|Account menu)/.test(value)));
                        assert(/^Notifications/.test(order[0]) && /^Theme/.test(order[1]) && /^Account menu/.test(order[2]), JSON.stringify(order));
                    }
                    await page.screenshot({ path: path.join(dir, `notifications-${preference}-${width}.png`), fullPage: true });
                    result.measurements.push({ preference, width, overflow, focus });
                } finally { await context.close(); }
            });
        }
        await check('320px, System OS change, error/retry, zero-count disabling and preview isolation', async () => {
            const { context, page, api } = await open('system', 320);
            try {
                await page.locator('header a[href="/notifications"]').click(); await ready(page);
                await page.emulateMedia({ colorScheme: 'light' }); await page.waitForFunction(() => document.documentElement.dataset.colorScheme === 'light');
                assert(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1));
                api.state.fail.set('/me/notifications', 500); await page.getByRole('button', { name: 'Unread', exact: true }).click();
                await page.getByRole('heading', { name: "Couldn't load notifications" }).waitFor(); api.state.fail.delete('/me/notifications');
                await page.getByRole('button', { name: 'Try again', exact: true }).click(); await ready(page);
                await page.getByRole('button', { name: 'Mark all as read', exact: true }).click(); await page.getByText('Marked 23 notifications as read.', { exact: true }).waitFor();
                assert(await page.getByRole('button', { name: 'Mark all as read', exact: true }).isDisabled());
                api.state.requests.length = 0;
                await page.goto(new URL('/preview?screen=notifications', baseUrl).href); await page.locator('h1').filter({ hasText: 'Notifications' }).waitFor();
                assert.equal(api.state.requests.length, 0); assert.equal(await page.locator('#notification-results a').count(), 0);
            } finally { await context.close(); }
        });
        for (const role of ['Instructor', 'Administrator']) await check(`${role} production role boundary`, async () => {
            const { context, page, api } = await open('light', 1280, role);
            try {
                if (role === 'Instructor') { await page.locator('header a[href="/notifications"]').click(); await ready(page); assert.equal(await page.locator('h1').textContent(), 'Notifications'); }
                else { assert.equal(await page.locator('header a[href="/notifications"]').count(), 0); api.state.restore = true; api.state.requests.length = 0; await page.goto(new URL('/notifications', baseUrl).href); await page.getByRole('heading', { name: "You don't have access to this page" }).waitFor(); assert(!api.state.requests.some((value) => value.includes('/me/notifications'))); }
            } finally { await context.close(); }
        });
    } finally { await browser.close(); }
    return result;
}

/** Targeted FE28 independent-finding regressions, production build and controlled REST doubles. */
async function runFE28RemediationEngine(playwright, engine, baseUrl) {
    const result = { engine, version: null, passed: [], failed: [], measurements: [], focus: [], console: [], pageErrors: [], notExercised: ['Real backend (unchanged transport)', 'Screen readers, native zoom and physical devices'] };
    const dir = path.join(out, engine); fs.mkdirSync(dir, { recursive: true });
    let browser;
    try { browser = await playwright[engine].launch({ headless: true }); }
    catch (error) { result.launchError = String(error); return result; }
    result.version = browser.version();
    const baseline = args.includes('--reproduce');
    const notice = (n) => ({ id: `00000000-0000-3000-8000-${String(n).padStart(12, '0')}`, message: `Remediation notification ${n}`, read: false, createdAt: new Date().toISOString() });
    let serial = 0;
    const check = async (name, run) => {
        if (flag('case', '') && !name.includes(flag('case', ''))) { (result.skipped ??= []).push(name); return; }
        try { await run(); result.passed.push(name); console.log(`PASS [${engine}] ${name}`); }
        catch (error) { result.failed.push({ name, error: String(error), stack: error.stack }); console.log(`FAIL [${engine}] ${name}: ${error}`); }
    };
    const fresh = async ({ width = 1280, preference = 'light', role = 'Learner', count = undefined, size = 23 } = {}) => {
        const context = await browser.newContext({ viewport: { width, height: 900 }, colorScheme: 'dark' });
        // A native modified click first opens an opaque about:blank document.
        await context.addInitScript((value) => { if (location.protocol === 'http:' || location.protocol === 'https:') localStorage.setItem('codemastery.theme', value); }, preference);
        await context.tracing.start({ screenshots: true, snapshots: true });
        const api = createApi(); api.state.role = role; api.state.notifications = Array.from({ length: size }, (_, i) => notice(i + 1));
        const page = await context.newPage(); page.setDefaultTimeout(7000);
        const diagnostics = (tab) => {
            tab.on('pageerror', (error) => result.pageErrors.push(String(error)));
            tab.on('console', (message) => { if (message.type() === 'error') result.console.push(message.text()); });
            tab.on('response', (response) => { if (response.status() >= 400) (result.httpErrors ??= []).push({ status: response.status(), path: new URL(response.url()).pathname }); });
        };
        diagnostics(page); context.on('page', diagnostics);
        await context.route('**/api/**', api.handle);
        const countState = { value: count };
        await page.route('**/api/me/notifications/unread-count', (route) => countState.value === undefined ? route.fallback()
            : countState.value === null ? route.fulfill(problem(500, 'Synthetic count failure', '/api/me/notifications/unread-count')) : route.fulfill(ok({ unreadCount: countState.value })));
        await page.route(/\/api\/instructor\/courses(?:\?.*)?$/, (route) => route.fulfill(ok({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 })));
        await page.goto(new URL('/login', baseUrl).href);
        await page.locator('input[name="email"]').fill('synthetic@example.com'); await page.locator('input[name="password"]').fill('synthetic-password');
        await page.getByRole('button', { name: 'Sign in', exact: true }).click(); await page.waitForURL((url) => url.pathname !== '/login');
        // Successful ADR014 sign-in also installs a refresh-cookie session in a real browser.
        api.state.restore = true;
        await page.evaluate(() => document.fonts.ready);
        return { context, page, api, countState, close: async (label) => { await context.tracing.stop({ path: path.join(dir, `${++serial}-${label}.zip`) }); await context.close(); } };
    };
    const ready = async (page) => { await page.locator('#notification-results').waitFor(); await page.waitForFunction(() => document.querySelector('#notification-results')?.getAttribute('aria-busy') === 'false'); };
    const open = async (page) => { await page.locator('header a[href="/notifications"]').click(); await ready(page); };
    const shot = (page, label) => page.screenshot({ path: path.join(dir, `${label}.png`), fullPage: true });
    const focused = (page) => page.evaluate(() => { const el = document.activeElement, style = getComputedStyle(el); return { tag: el.tagName, id: el.id, label: el.getAttribute('aria-label'), text: el.textContent.slice(0, 100), visible: el.matches(':focus-visible'), outline: style.outlineStyle, outlineWidth: style.outlineWidth }; });
    // Holds the next authoritative list read; release before closing the context even on assertion failure.
    const holdList = async (page) => {
        let release, started; const wait = new Promise((resolve) => { release = resolve; }), hit = new Promise((resolve) => { started = resolve; });
        const handler = async (route) => { started(); await wait; await route.fallback().catch(() => undefined); };
        await page.route('**/api/me/notifications?*', handler, { times: 1 });
        return { release, hit };
    };
    const holdCount = async (page) => {
        let release;
        const wait = new Promise((resolve) => { release = resolve; });
        const hit = page.waitForRequest('**/api/me/notifications/unread-count');
        await page.route('**/api/me/notifications/unread-count', async (route) => { await wait; await route.fallback().catch(() => undefined); }, { times: 1 });
        return { release, hit };
    };
    const keyboardFocus = async (page, target) => {
        await target.focus(); await page.keyboard.press('Shift+Tab'); await page.keyboard.press('Tab');
        assert(await target.evaluate((node) => node === document.activeElement), 'keyboard traversal reaches the intended control');
    };
    try {
        if (!baseline) await check('IR-FE28-D01 focused bell survives delayed count transitions and reconciliation', async () => {
            const x = await fresh({ width: 360, size: 999 }); const { page, api } = x; let hold;
            const verify = async (original, count, flow) => {
                await page.getByRole('link', { name: count ? `Notifications, ${count} unread` : 'Notifications', exact: true }).waitFor();
                const bell = page.locator('header a[href="/notifications"]');
                const same = await bell.evaluate((node, old) => node === old && old.isConnected, original), focus = await focused(page);
                result.focus.push({ scenario: 'IR-FE28-D01', flow, count, same, ...focus });
                assert(same && focus.tag === 'A' && focus.label === (count ? `Notifications, ${count} unread` : 'Notifications'), JSON.stringify(focus));
                assert(focus.visible && focus.outline !== 'none', 'asynchronous count updates preserve the visible bell focus');
                assert.equal(await bell.getAttribute('aria-current'), new URL(page.url()).pathname === '/notifications' ? 'page' : null);
                await page.keyboard.press('Tab'); assert.match((await focused(page)).label, /^Account menu,/, 'Tab continues to the next utility, without repeating the bell');
                await original.dispose();
            };
            try {
                // Settle bootstrap before installing a one-shot hold for the next supported refresh.
                await page.getByRole('link', { name: 'Notifications, 999 unread', exact: true }).waitFor();
                for (const count of [1000, 999, 1000, 0, 1000, null, 1000, 1001, 1100]) {
                    if (count === null) api.state.fail.set('/me/notifications/unread-count', 500);
                    else api.state.notifications = Array.from({ length: count }, (_, i) => notice(i + 1));
                    hold = await holdCount(page);
                    await page.locator(new URL(page.url()).pathname === '/notifications' ? 'header a[aria-label="CodeMastery home"]' : 'header a[href="/notifications"]').click();
                    await hold.hit; await page.waitForFunction(() => document.activeElement?.tagName === 'H1');
                    const bell = page.locator('header a[href="/notifications"]'); await keyboardFocus(page, bell);
                    const original = await bell.elementHandle(); hold.release(); await verify(original, count, 'route refresh');
                    api.state.fail.delete('/me/notifications/unread-count');
                }
                await ready(page); hold = await holdCount(page);
                await keyboardFocus(page, page.getByRole('button', { name: 'Mark all as read', exact: true })); await page.keyboard.press('Space');
                await hold.hit; await ready(page);
                const bell = page.locator('header a[href="/notifications"]'); await keyboardFocus(page, bell);
                const original = await bell.elementHandle(); hold.release(); await verify(original, 0, 'mark-all reconciliation');
                assert.equal(api.state.requests.filter((request) => request.startsWith('PUT ')).length, 1, 'one explicit mutation, no replay');
                await shot(page, 'D01-after-count-reconciliation');
            } finally { hold?.release(); await x.close('D01-count-focus'); }
        });
        if (!baseline) await check('IR-FE28-D02 compact Home has a 44px target and operable edges', async () => {
            for (const role of ['Learner', 'Instructor']) for (const preference of ['light', 'dark']) {
                const x = await fresh({ width: 360, role, preference, count: 1000 });
                try {
                    await x.page.getByRole('link', { name: 'Notifications, 1000 unread', exact: true }).waitFor();
                    for (const width of [320, 360, 389, 390, 400]) {
                        await x.page.setViewportSize({ width, height: 900 }); await open(x.page);
                        const geometry = await x.page.getByRole('link', { name: 'CodeMastery home', exact: true }).evaluate((home) => {
                            const r = home.getBoundingClientRect(), point = { x: r.x + r.width / 2, y: r.y + 1 };
                            return { x: r.x, y: r.y, width: r.width, height: r.height, point, hit: document.elementFromPoint(point.x, point.y)?.closest('a') === home };
                        });
                        result.measurements.push({ track: 'home-target', role, preference, width, count: 1000, home: geometry });
                        assert(geometry.width >= 44 && geometry.height >= 44 && geometry.hit, JSON.stringify(geometry));
                        await x.page.mouse.click(geometry.point.x, geometry.point.y);
                        await x.page.waitForURL((url) => url.pathname === (role === 'Learner' ? '/home' : '/teach'));
                        if (width === 360) await x.page.locator('header').first().screenshot({ path: path.join(dir, `D02-${role}-${preference}-360.png`) });
                    }
                } finally { await x.close(`D02-home-${role}-${preference}`); }
            }
        });
        const themes = baseline ? ['light'] : ['light', 'dark', 'system'];
        for (const role of baseline ? ['Learner'] : ['Learner', 'Instructor']) for (const preference of themes) {
            await check(`IR-FE28-01 ${role} ${preference}: count geometry, hit testing and native navigation`, async () => {
                const x = await fresh({ width: 360, preference, role, count: 100000 }); const { page, countState } = x;
                const problems = [];
                try {
                    const counts = baseline ? [100000] : [0, 1, 9, 99, 999, 99999, 100000, 123456789, 2147483647, Number.MAX_SAFE_INTEGER, null];
                    for (const width of baseline ? [360] : [320, 360, 390, 400, 599, 600, 768, 899, 900, 901, 1024, 1199, 1200, 1201, 1280, 1440]) {
                        await page.setViewportSize({ width, height: 900 });
                        // Full count matrix on narrow screens; breakpoint and desktop stress/zero/unknown fixtures.
                        for (const count of counts.filter((n) => width <= 400 || [0, 100000, Number.MAX_SAFE_INTEGER, null].includes(n))) {
                            countState.value = count;
                            await page.getByRole('link', { name: 'CodeMastery home', exact: true }).click();
                            const bell = page.locator('header a[href="/notifications"]');
                            await page.getByRole('link', { name: count ? `Notifications, ${count} unread` : 'Notifications', exact: true }).waitFor();
                            await page.evaluate(() => new Promise((resolve) => requestAnimationFrame(() => requestAnimationFrame(resolve))));
                            const measurement = await bell.evaluate((el) => {
                                const rect = (node) => { const r = node.getBoundingClientRect(); return { x: r.x, y: r.y, width: r.width, height: r.height, right: r.right, bottom: r.bottom }; };
                                const b = el.querySelector('span[aria-hidden]') || el.closest('[data-wide-header-badge]')?.querySelector(':scope > span[aria-hidden]'), badge = b && rect(b), target = rect(el), home = document.querySelector('header a[aria-label="CodeMastery home"]');
                                const neighbors = [...el.closest('header').querySelectorAll('a,button')].filter((n) => n !== el && n.getBoundingClientRect().width > 0).map((n) => ({ name: n.getAttribute('aria-label') || n.textContent, rect: rect(n) }));
                                const collisions = badge ? neighbors.filter(({ rect: r }) => Math.min(r.right, badge.right) - Math.max(r.x, badge.x) > .5 && Math.min(r.bottom, badge.bottom) - Math.max(r.y, badge.y) > .5) : [];
                                const collision = collisions[0], point = collision && { x: (Math.max(collision.rect.x, badge.x) + Math.min(collision.rect.right, badge.right)) / 2, y: (Math.max(collision.rect.y, badge.y) + Math.min(collision.rect.bottom, badge.bottom)) / 2 };
                                const text = b && document.createRange(); if (text) text.selectNodeContents(b);
                                return { target, badge, badgeText: b?.textContent, textWidth: text?.getBoundingClientRect().width, home: rect(home), collisions, point, hit: point && document.elementFromPoint(point.x, point.y)?.closest('a')?.getAttribute('href'), scroll: document.documentElement.scrollWidth, width: innerWidth, scheme: document.documentElement.dataset.colorScheme,
                                    order: [...el.closest('header').querySelectorAll(':is(a,button)[aria-label]')].map((n) => n.getAttribute('aria-label')).filter((name) => /^(Notifications|Theme|Account menu)/.test(name)) };
                            });
                            const receipt = { role, preference, count, width, ...measurement }; result.measurements.push(receipt);
                            assert(measurement.target.width >= 44 && measurement.target.height >= 44, '44px bell target');
                            assert(/^Notifications/.test(measurement.order[0]) && /^Account menu/.test(measurement.order.at(-1)), 'utility order');
                            if (width >= 900) assert(/^Theme/.test(measurement.order[1]), 'Theme stays between bell and account');
                            assert.equal(measurement.scheme, preference === 'system' ? 'dark' : preference);
                            assert.equal(measurement.badgeText ?? null, count ? String(count) : null, 'exact visible count; zero/unknown hidden');
                            if (measurement.badge) {
                                assert(measurement.badge.x >= 0 && measurement.badge.right <= width + .5, 'badge inside viewport');
                                assert(measurement.textWidth <= measurement.badge.width, 'digits not clipped');
                            }
                            assert(measurement.scroll <= width + 1, 'no horizontal overflow');
                            if (measurement.collisions.length) {
                                await shot(page, `before-collision-${width}-${count}`);
                                await page.mouse.click(measurement.point.x, measurement.point.y);
                                await page.waitForURL('**/notifications'); receipt.collisionClickPath = new URL(page.url()).pathname;
                                problems.push(receipt); continue;
                            }
                            // Click the historical collision side of Home, not just its centre.
                            const home = measurement.home;
                            await page.mouse.click(home.right - 3, home.y + Math.min(11, home.height / 2));
                            await page.waitForURL((url) => url.pathname === (role === 'Learner' ? '/home' : '/teach'));
                            receipt.homeClickPath = new URL(page.url()).pathname;
                            await bell.click(); await ready(page); receipt.bellClickPath = new URL(page.url()).pathname;
                            assert.equal(await bell.getAttribute('aria-current'), 'page');
                            if ([320, 360, 1280].includes(width) && count === 100000) await shot(page, `header-${role}-${preference}-${width}`);
                        }
                    }
                    assert.equal(problems.length, 0, JSON.stringify(problems));
                    if (preference === 'system' && !baseline) { await page.emulateMedia({ colorScheme: 'light' }); await page.waitForFunction(() => document.documentElement.dataset.colorScheme === 'light'); }
                } finally { await x.close(`header-${role}-${preference}`); }
            });
        }
        if (!baseline) await check('supplemental header containment and glyph at breakpoint boundaries', async () => {
            for (const role of ['Learner', 'Instructor']) for (const preference of ['light', 'dark']) {
                const x = await fresh({ role, preference, width: 320, count: Number.MAX_SAFE_INTEGER });
                try {
                    await x.page.getByRole('link', { name: `Notifications, ${Number.MAX_SAFE_INTEGER} unread`, exact: true }).waitFor();
                    for (const width of [320, 360, 390, 400, 599, 600, 899, 900, 901, 1199, 1200, 1201, 1280, 1440]) {
                        await x.page.setViewportSize({ width, height: 900 });
                        await x.page.evaluate(() => new Promise((resolve) => requestAnimationFrame(() => requestAnimationFrame(resolve))));
                        const geometry = await x.page.locator('header a[href="/notifications"]').evaluate((button) => {
                            const rect = (el) => { const r = el.getBoundingClientRect(); return { x: r.x, y: r.y, right: r.right, bottom: r.bottom, width: r.width, height: r.height }; };
                            const intersects = (a,b) => Math.min(a.right,b.right)-Math.max(a.x,b.x) > .5 && Math.min(a.bottom,b.bottom)-Math.max(a.y,b.y) > .5;
                            const wrapper = button.closest('[data-wide-header-badge]'), badge = wrapper.querySelector(':scope > span[aria-hidden]'), b = rect(badge), target = rect(button), glyph = rect(button.querySelector('svg'));
                            const neighbors = [...button.closest('header').querySelectorAll('a,button')].filter((n) => n !== button && n.getBoundingClientRect().width > 0).map((n) => ({ name: n.getAttribute('aria-label') || n.textContent, rect: rect(n) }));
                            const points = [b.x+1,b.right-1].map((x) => ({ x, y: (b.y+b.bottom)/2 }));
                            return { badge: b, target, glyph, wrapper: rect(wrapper), neighbors, collisions: neighbors.filter((n) => intersects(n.rect,b) || intersects(n.rect,target)), glyphCollision: intersects(b,glyph), hitEdges: points.map((p) => document.elementFromPoint(p.x,p.y)?.closest('a,button')?.getAttribute('aria-label') ?? null), scroll: document.documentElement.scrollWidth, width: innerWidth, glyphVisibility: getComputedStyle(button.querySelector('svg')).visibility };
                        });
                        result.measurements.push({ track: 'containment', role, preference, width, count: Number.MAX_SAFE_INTEGER, ...geometry });
                        assert.equal(geometry.collisions.length, 0, JSON.stringify(geometry));
                        assert(!geometry.glyphCollision && geometry.glyphVisibility === 'visible', 'full count cannot cover the bell glyph');
                        assert(geometry.badge.x >= geometry.wrapper.x && geometry.badge.right <= geometry.wrapper.right+.5 && geometry.badge.y >= 0, 'badge contained in reserved space and header');
                        assert(geometry.wrapper.right <= width+.5 && geometry.scroll <= width+1, 'reserved utility fits the viewport');
                        assert(geometry.hitEdges.every((name) => name === null || name.startsWith('Notifications')), 'badge edges do not hit a neighbour');
                        for (const neighbor of geometry.neighbors) assert(neighbor.rect.x >= -.5 && neighbor.rect.right <= width+.5, JSON.stringify(neighbor));
                        if ([320,360,1280].includes(width)) await x.page.locator('header').first().screenshot({ path: path.join(dir, `header-stress-${role}-${preference}-${width}.png`) });
                    }
                } finally { await x.close(`containment-${role}-${preference}`); }
            }
        });
        if (!baseline) await check('supplemental Instructor native modified click and dirty-editor interception', async () => {
            const x = await fresh({ role: 'Instructor', width: 360, count: 100000 });
            try {
                await x.page.locator('main header').getByRole('link', { name: 'Create course', exact: true }).click();
                await x.page.getByRole('textbox', { name: /Course title/ }).fill('Synthetic unsaved notification regression');
                x.api.state.restore = true;
                const bell = x.page.locator('header a[href="/notifications"]'), popup = x.context.waitForEvent('page');
                await bell.click({ modifiers: [process.platform === 'darwin' ? 'Meta' : 'Control'] });
                const other = await popup; await other.waitForURL('**/notifications'); await ready(other);
                assert.equal(new URL(x.page.url()).pathname, '/teach/courses/new');
                assert.equal(await x.page.getByRole('textbox', { name: /Course title/ }).inputValue(), 'Synthetic unsaved notification regression');
                await other.close();
                await bell.click(); await x.page.getByRole('dialog', { name: 'Leave without saving?', exact: true }).waitFor();
                await x.page.getByRole('button', { name: 'Stay', exact: true }).click();
                assert.equal(new URL(x.page.url()).pathname, '/teach/courses/new');
                await bell.click(); await x.page.getByRole('button', { name: 'Leave', exact: true }).click(); await ready(x.page);
                assert.equal(new URL(x.page.url()).pathname, '/notifications');
            } finally { await x.close('native-and-guarded-navigation'); }
        });
        const scenarios = baseline ? ['single-all', 'mark-all-zero', 'pagination'] : ['single-all', 'single-unread', 'last-unread', 'mark-all-zero', 'mark-all-concurrent', 'pagination', 'shrink-page', 'mutation-failure', 'list-failure', 'move-focus', 'pointer-focus', 'navigate-away', 'logout', 'session-replacement', 'account-replacement', 'role-loss'];
        for (const scenario of scenarios) await check(`FE28-R01 keyboard ${scenario}`, async () => {
            const x = await fresh({ size: scenario === 'last-unread' ? 1 : ['pagination', 'shrink-page', 'mark-all-concurrent'].includes(scenario) ? 23 : 3, preference: scenario === 'pagination' ? 'dark' : 'light' });
            const { page, api } = x; let hold, mutationRelease;
            try {
                await open(page);
                if (['single-unread', 'last-unread', 'shrink-page'].includes(scenario)) { await page.getByRole('button', { name: 'All', exact: true }).focus(); await page.keyboard.press('ArrowRight'); await ready(page); }
                if (scenario === 'shrink-page') { await page.getByRole('button', { name: 'Go to page 2', exact: true }).click(); await ready(page); api.state.notifications.slice(0, 3).forEach((n) => { n.read = true; }); }
                if (scenario === 'mark-all-concurrent') api.state.newAfterMarkAll = notice(999);
                if (scenario === 'mutation-failure') api.state.fail.set(`/me/notifications/${notice(1).id}/read`, 500);
                const pagination = scenario === 'pagination', all = scenario.startsWith('mark-all');
                const button = pagination ? page.getByRole('button', { name: 'Go to page 2', exact: true }) : all ? page.getByRole('button', { name: 'Mark all as read', exact: true }) : page.getByRole('button', { name: 'Mark as read', exact: true }).first();
                await button.focus(); await page.keyboard.press('Shift+Tab'); await page.keyboard.press('Tab'); assert(await button.evaluate((n) => n === document.activeElement), 'keyboard order returns to action');
                hold = await holdList(page);
                if (scenario === 'list-failure') api.state.fail.set('/me/notifications', 500);
                if (['single-all', 'mark-all-zero'].includes(scenario) && !baseline) {
                    let begin; const hit = new Promise((resolve) => { begin = resolve; }), wait = new Promise((resolve) => { mutationRelease = resolve; });
                    await page.route(all ? '**/api/me/notifications/read-all' : '**/api/me/notifications/*/read', async (route) => { begin(); await wait; await route.fallback().catch(() => undefined); }, { times: 1 });
                    await page.keyboard.press(all ? 'Space' : 'Enter'); await Promise.race([hit, new Promise((_, reject) => { const timer = setTimeout(() => reject(new Error('mutation hold did not start')), 7000); timer.unref(); })]);
                    assert.equal((await focused(page)).id, 'notification-updates', 'focus survives mutation pending before reconciliation');
                    assert.equal(await page.locator('#notification-results').getAttribute('aria-busy'), 'true');
                    await shot(page, `after-focus-${scenario}-pending`); mutationRelease();
                } else await page.keyboard.press(all ? 'Space' : 'Enter'); await Promise.race([hold.hit, new Promise((_, reject) => { const timer = setTimeout(() => reject(new Error('list hold did not start')), 7000); timer.unref(); })]);
                result.focus.push({ scenario, phase: 'loading', ...(await focused(page)) });
                await shot(page, `${baseline ? 'before' : 'after'}-focus-${scenario}-loading`);
                if (scenario === 'move-focus' || scenario === 'pointer-focus') {
                    const other = page.getByRole('button', { name: 'Unread', exact: true });
                    // Deliberate input elsewhere while the response is still pending.
                    if (scenario === 'pointer-focus') await other.click();
                    else { await page.keyboard.press('Shift+Tab'); assert.equal((await focused(page)).text, 'All'); }
                } else if (scenario === 'navigate-away') { await page.getByRole('link', { name: 'CodeMastery home', exact: true }).click(); await page.waitForURL('**/home'); }
                else if (['logout', 'session-replacement', 'account-replacement', 'role-loss'].includes(scenario)) {
                    api.state.sessionId = notice(700).id;
                    api.state.restore = scenario !== 'logout'; api.state.role = scenario === 'role-loss' ? 'Administrator' : 'Learner';
                    if (scenario === 'account-replacement') await page.route('**/api/auth/refresh', (route) => {
                        const response = loginBody('Learner', 'Replacement learner', api.state.sessionId), id = notice(901).id;
                        const parts = response.accessToken.split('.'), payload = JSON.parse(Buffer.from(parts[1], 'base64url').toString());
                        response.accessToken = `${parts[0]}.${encode({ ...payload, sub: id })}.${parts[2]}`; response.user.id = id;
                        return route.fulfill(ok(response));
                    });
                    api.state.notifications = [notice(800)];
                    await page.evaluate((type) => { const c = new BroadcastChannel('codemastery-auth'); c.postMessage({ type }); c.close(); }, scenario === 'logout' ? 'signed-out' : 'signed-in');
                    if (scenario === 'logout') await page.waitForURL((url) => url.pathname !== '/notifications');
                    else if (scenario === 'role-loss') { await page.getByRole('heading', { name: "You don't have access to this page" }).waitFor(); await page.getByRole('link', { name: 'Go to catalogue', exact: true }).focus(); }
                    else { await page.getByText('Remediation notification 800', { exact: true }).waitFor(); await page.getByRole('button', { name: 'All', exact: true }).focus(); }
                } else if (!baseline) {
                    const focus = await focused(page); assert.equal(focus.id, 'notification-updates', 'stable named results region owns keyboard focus while loading');
                    assert(focus.visible && focus.outline !== 'none' && parseFloat(focus.outlineWidth) >= 2, 'visible focus ring');
                }
                const beforeRelease = await focused(page); hold.release(); if (!['navigate-away', 'logout', 'role-loss'].includes(scenario)) await ready(page);
                if (['navigate-away', 'logout', 'role-loss'].includes(scenario)) {
                    if (scenario !== 'role-loss') assert.notEqual(new URL(page.url()).pathname, '/notifications'); assert.notEqual((await focused(page)).id, 'notification-updates');
                    assert.equal(await page.locator('#notification-results').count(), 0, 'old page owner unmounted');
                } else if (['session-replacement', 'account-replacement'].includes(scenario)) {
                    assert.equal((await focused(page)).text, 'All', 'old operation cannot move focus in replacement session');
                    assert.equal(await page.locator('#notification-results li').count(), 1);
                    assert.equal(await page.getByText('Marked notification as read.', { exact: true }).count(), 0);
                } else if (scenario === 'move-focus') { assert.equal((await focused(page)).text, 'All', 'late response must not steal focus'); }
                else if (scenario === 'pointer-focus') { assert.equal((await focused(page)).text, 'Unread', 'late response respects pointer intent'); }
                else {
                    const focus = await focused(page); result.focus.push({ scenario, phase: 'settled', ...focus }); await shot(page, `${baseline ? 'before' : 'after'}-focus-${scenario}-settled`);
                    assert.equal(focus.id, 'notification-updates', JSON.stringify(focus));
                    assert.equal(await page.getByRole('region', { name: 'Notifications', exact: true }).count(), 1);
                    if (scenario === 'last-unread' || scenario === 'mark-all-zero') { assert(await page.getByRole('button', { name: 'Mark all as read', exact: true }).isDisabled()); }
                    if (scenario === 'mark-all-concurrent') { assert.equal(await page.locator('#notification-results li').count(), 20); await page.getByRole('link', { name: 'Notifications, 1 unread', exact: true }).waitFor(); }
                    if (scenario === 'shrink-page') assert(api.state.requests.at(-1).includes('unreadOnly=true&page=0&size=20'), 'shrunk last page recovers to current page zero');
                    if (scenario === 'pagination') assert.equal(await page.locator('#notification-results li').count(), 3);
                    if (scenario === 'list-failure') { api.state.fail.delete('/me/notifications'); await page.keyboard.press('Tab'); assert.equal((await focused(page)).text, 'Try again'); await page.keyboard.press('Enter'); await ready(page); assert.equal((await focused(page)).id, 'notification-updates'); }
                    await page.keyboard.press('Tab');
                    const next = await focused(page); assert(['BUTTON', 'A'].includes(next.tag), JSON.stringify(next));
                    if (scenario === 'single-all' || scenario === 'single-unread') assert.equal(next.text, 'Mark as read');
                    if (scenario === 'mutation-failure') { api.state.fail.clear(); assert.equal(next.text, 'Mark as read'); await page.keyboard.press('Space'); await ready(page); await page.getByText('Marked notification as read.', { exact: true }).waitFor(); assert.equal((await focused(page)).id, 'notification-updates'); }
                    assert.equal(api.state.requests.filter((r) => r.startsWith('PUT ')).length, pagination ? 0 : scenario === 'mutation-failure' ? 2 : 1, 'no mutation replay');
                }
                result.focus.push({ scenario, phase: 'release-owner', ...beforeRelease });
            } finally { mutationRelease?.(); hold?.release(); await x.close(`focus-${scenario}`); }
        });
        await check('supplemental no uncaught page errors', async () => { assert.equal(result.pageErrors.length, 0, JSON.stringify(result.pageErrors)); assert(!result.console.some((message) => /Uncaught|TypeError|ReferenceError|SyntaxError/.test(message)), JSON.stringify(result.console)); });
    } finally { await browser.close(); }
    return result;
}

// Lifecycle helpers are exported for presentation.browser.test.ts; the harness runs only when executed directly.
module.exports = { buildMarker, markBuild, waitForUrl, createFE32Fixture, workspaceGeometry, assertWorkspaceGeometry, runFE32Engine };
if (require.main === module) main().catch((error) => { console.error(error); process.exitCode = 1; });
