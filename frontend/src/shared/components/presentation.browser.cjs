#!/usr/bin/env node
// Optional real-browser presentation harness for the "Lit Path" UI (DESIGN-2026-11-06-r1).
//
// What it covers: every /preview screen (S5–S26 sample data), the live Group A pages with
// CONTROLLED API responses (page.route test doubles, never a backend), layout at ten widths
// and 200% zoom, the self-hosted font, contrast, focus visibility and order, skip link,
// drawer, dialogs, reduced motion, skeleton motion, the Mastery Map graph and production/
// preview chunk isolation. It writes screenshots, accessibility snapshots and a JSON report.
// It does not prove backend integration (see e2e/), screen-reader output or WCAG conformance.
//
// Usage (from frontend/):
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
/** Polls `url`; with `child`, fails at once (with its stderr tail) if that process exits first, for example on a taken port. */
async function waitForUrl(url, ms, child) {
    let stderr = '';
    child?.stderr?.on('data', (chunk) => { stderr = (stderr + chunk).slice(-2000); });
    const end = Date.now() + ms;
    for (;;) {
        if (child && child.exitCode !== null) throw new Error(`The preview server exited (${child.exitCode}) before ${url} was ready.${stderr ? `\n${stderr.trim()}` : ''}`);
        // Each probe is bounded, so a socket that accepts but never answers cannot stall the loop.
        try { if ((await fetch(url, { signal: AbortSignal.timeout(3000) })).ok) return; } catch { /* starting */ }
        if (Date.now() > end) throw new Error(`Timed out waiting for ${url}`);
        await new Promise((r) => setTimeout(r, 500));
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
function loginBody(role = 'Learner') {
    const iat = Math.floor(Date.now() / 1000);
    return { accessToken: `${encode({ alg: 'HS256', typ: 'JWT' })}.${encode({ sub: instructorId, iss: 'codemastery', aud: 'codemastery-api', iat, exp: iat + 900 })}.${'A'.repeat(43)}`,
        tokenType: 'Bearer', expiresIn: 900, user: { id: instructorId, displayName: 'Harness Learner', role } };
}
const problem = (status, detail, instance) => ({ status, contentType: 'application/problem+json', body: JSON.stringify({ type: 'about:blank', title: { 400: 'Bad Request', 401: 'Unauthorized', 404: 'Not Found', 500: 'Internal Server Error' }[status], status, detail, instance }) });
const ok = (value) => ({ status: 200, contentType: 'application/json', body: JSON.stringify(value) });

function createApi() {
    const state = { requests: [], hold: null, fail: new Map(), memberships: new Map([[primaryId, 'active'], [courses[3].id, 'left']]) };
    async function handle(route) {
        const request = route.request(), url = new URL(request.url()), p = url.pathname.replace(/^\/api/, '');
        state.requests.push(`${request.method()} ${url.pathname}${url.search}`);
        if (state.hold && state.hold.test(p)) { const release = await new Promise((resolve) => { state.release = resolve; }); if (release === 'abort') return route.abort(); }
        if (state.fail.has(p)) return route.fulfill(problem(state.fail.get(p), 'The request could not be completed.', url.pathname));
        if (p === '/auth/login' && request.method() === 'POST') {
            const body = request.postDataJSON();
            return body.password === 'wrong-password-123' ? route.fulfill(problem(401, 'Invalid email or password.', url.pathname)) : route.fulfill(ok(loginBody()));
        }
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
            const port = await freePort();
            execFileSync('npx', ['vite', 'build', '--outDir', dist, '--emptyOutDir', '--logLevel', 'error'], { cwd: frontendRoot, env: { ...process.env, VITE_API_BASE_URL: '/api' }, stdio: 'inherit' });
            preview = spawn('npx', ['vite', 'preview', '--outDir', dist, '--host', '127.0.0.1', '--port', String(port), '--strictPort'], { cwd: frontendRoot, stdio: ['ignore', 'ignore', 'pipe'] });
            baseUrl = `http://127.0.0.1:${port}/`;
            await waitForUrl(baseUrl, 60000, preview);
        }
        report = { revision: (() => { try { return execFileSync('git', ['rev-parse', 'HEAD'], { cwd: frontendRoot, encoding: 'utf8' }).trim(); } catch { return null; } })(),
            dirty: (() => { try { return execFileSync('git', ['status', '--porcelain'], { cwd: frontendRoot, encoding: 'utf8' }).trim().length > 0; } catch { return null; } })(),
            baseUrl, api: 'Controlled page.route responses only; no backend', engines: [] };
        for (const engine of engines) {
            const result = await runEngine(playwright, engine, baseUrl);
            report.engines.push(result); failed += result.failed.length;
        }
    } finally {
        if (preview && preview.exitCode === null) {
            preview.kill('SIGTERM');
            await new Promise((gone) => { preview.once('exit', gone); setTimeout(gone, 5000); });
        }
        if (dist) fs.rmSync(dist, { recursive: true, force: true });
        if (report) fs.writeFileSync(path.join(out, 'browser-report.json'), JSON.stringify(report, null, 2));
    }
    for (const result of report.engines) console.log(`${result.engine} ${result.version ?? ''}: ${result.passed.length} passed, ${result.failed.length} failed${result.launchError ? ` (launch: ${result.launchError})` : ''}`);
    console.log(`Evidence: ${out}`);
    if (failed > 0 || report.engines.some((r) => r.launchError)) process.exitCode = 1;
}

async function runEngine(playwright, engine, baseUrl) {
    const result = { engine, version: null, passed: [], failed: [], screens: [] };
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
            await check('the landing page at / loads only its example-map chunk, never the preview, and sends no API request', async () => {
                const { context: landingContext, page: landing, api: landingApi } = await newPage();
                await landing.goto(url('/')); await ready(landing);
                await landing.getByText('Illustration · not learner data').waitFor();
                const scripts = await landing.evaluate(() => performance.getEntriesByType('resource').map((e) => new URL(e.name).pathname));
                assert(scripts.some((s) => /ExampleMasteryMap/.test(s)), scripts.join(','));
                assert(!scripts.some((s) => /PreviewApp/.test(s)));
                assert.deepEqual(landingApi.state.requests, []);
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
        {
            const { context, page } = await newPage();
            const failures = [];
            for (const [route, width] of [['/', 1280], ['/', 390], ['/courses', 1280], [`/courses/${primaryId}`, 1280], ['/login', 1280], ['/register', 390], ['/preview?screen=learner-home', 1280], ['/preview?screen=mastery', 1280], ['/preview?screen=assessments', 1280], ['/preview?screen=quiz-result', 1280], ['/preview?screen=admin-overview', 1280], ['/preview?screen=admin-users', 390], ['/preview?screen=notifications', 1280], ['/preview?screen=course', 390]]) {
                await page.setViewportSize({ width, height: 900 });
                await page.goto(url(route)); await ready(page); await page.waitForTimeout(200);
                for (const row of await page.evaluate(sampleText)) {
                    const ratio = contrast(row.color, row.bg), need = row.large ? 3 : 4.5;
                    if (ratio < need) failures.push(`${route}@${width} "${row.text}" ${ratio.toFixed(2)} < ${need}`);
                }
            }
            await check('visible text meets 4.5:1 (3:1 large) against its effective background on twelve sampled screens', async () => assert.deepEqual([...new Set(failures)].slice(0, 25), []));
            await context.close();
        }

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
            await check('the landing header keeps Catalogue and Sign in reachable at 390 px', async () => {
                await page.goto(url('/')); await ready(page);
                const nav = page.getByRole('navigation', { name: 'Main navigation' });
                assert(await nav.getByRole('link', { name: 'Catalogue' }).isVisible()); assert(await nav.getByRole('link', { name: 'Sign in' }).isVisible());
                assert(await page.getByRole('main').getByRole('link', { name: 'Create account' }).isVisible());
                await page.goto(url('/courses')); await ready(page);
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

main().catch((error) => { console.error(error); process.exitCode = 1; });
