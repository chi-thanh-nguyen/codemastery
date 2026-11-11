// @vitest-environment node
// Lifecycle checks for the browser harness (presentation.browser.cjs): readiness must identify the harness's own
// temporary build, and a failed start must still remove that build. No browser, screenshot or design check runs here.
import { spawn, spawnSync } from 'node:child_process';
import type { ChildProcess } from 'node:child_process';
import { once } from 'node:events';
import { chmodSync, mkdtempSync, readdirSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { createServer } from 'node:http';
import type { IncomingMessage, ServerResponse } from 'node:http';
import type { AddressInfo } from 'node:net';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { createRequire } from 'node:module';
import { fileURLToPath } from 'node:url';
import { afterEach, describe, expect, it } from 'vitest';

const harnessPath = fileURLToPath(new URL('./presentation.browser.cjs', import.meta.url));
const harness = createRequire(import.meta.url)(harnessPath) as {
    buildMarker: (token: string) => string;
    markBuild: (dist: string, token: string) => void;
    waitForUrl: (url: string, ms: number, child: ChildProcess, token: string, options?: { probeMs?: number; intervalMs?: number }) => Promise<void>;
};
const token = 'c'.repeat(32), fast = { probeMs: 300, intervalMs: 100 };
const cleanups: (() => void | Promise<void>)[] = [];
afterEach(async () => { for (const cleanup of cleanups.splice(0).reverse()) await cleanup(); });

async function serve(handler: (req: IncomingMessage, res: ServerResponse) => void) {
    const server = createServer(handler).listen(0, '127.0.0.1');
    await once(server, 'listening');
    cleanups.push(() => { server.closeAllConnections(); server.close(); });
    return `http://127.0.0.1:${(server.address() as AddressInfo).port}/`;
}
function child(code = 'setInterval(() => {}, 1000)') {
    const process_ = spawn(process.execPath, ['-e', code], { stdio: ['ignore', 'ignore', 'pipe'] });
    cleanups.push(async () => { if (process_.exitCode === null && process_.signalCode === null) { process_.kill(); await once(process_, 'exit'); } });
    return process_;
}

describe('browser harness readiness', () => {
    it('marks the temporary build and is ready only for the server that serves it', async () => {
        const dist = mkdtempSync(join(tmpdir(), 'codemastery-harness-dist-')); cleanups.push(() => rmSync(dist, { recursive: true, force: true }));
        writeFileSync(join(dist, 'index.html'), '<!doctype html><html><head><title>CodeMastery</title></head><body></body></html>');
        harness.markBuild(dist, token);
        const html = readFileSync(join(dist, 'index.html'), 'utf8');
        expect(html).toContain(`${harness.buildMarker(token)}</head>`);
        await harness.waitForUrl(await serve((_, res) => res.end(html)), 2000, child(), token, fast);
    });
    it.each([
        ['200 without the marker', '<!doctype html><html><head><title>CodeMastery</title></head><body></body></html>'],
        ['unrelated HTML', '<html><body>It works!</body></html>'],
        ['another build marker', `<html><head>${'<meta name="codemastery-browser-build" content="' + 'd'.repeat(32) + '">'}</head></html>`],
    ])('is not ready for a foreign server answering %s', async (_, body) => {
        const url = await serve((__, res) => res.end(body));
        await expect(harness.waitForUrl(url, 800, child(), token, fast)).rejects.toThrow(/Timed out waiting for .* to serve this harness's build/);
    });
    it('fails promptly with the preview stderr when the preview exits first', async () => {
        const url = await serve((_, res) => res.end('FOREIGN')), started = Date.now();
        const exiting = child('console.error("error when starting preview server: Port is already in use"); process.exit(1)');
        await expect(harness.waitForUrl(url, 30_000, exiting, token, fast)).rejects.toThrow(/preview server exited \(1\)[\s\S]*Port is already in use/);
        expect(Date.now() - started).toBeLessThan(10_000);
    });
    it('does not report ready when the preview exits while the probe is answered', async () => {
        const preview = child();
        const url = await serve(async (_, res) => { preview.kill(); await once(preview, 'exit'); res.end(`<html><head>${harness.buildMarker(token)}</head></html>`); });
        await expect(harness.waitForUrl(url, 2000, preview, token, fast)).rejects.toThrow(/preview server exited/);
    });
    it('bounds each probe when a server accepts but never answers', async () => {
        const url = await serve(() => { /* never answers */ }), started = Date.now();
        await expect(harness.waitForUrl(url, 500, child(), token, { probeMs: 200, intervalMs: 100 })).rejects.toThrow(/Timed out/);
        expect(Date.now() - started).toBeLessThan(3000);
    });
    it('removes the temporary build when the preview cannot start', () => {
        // Stub npx: `vite build` writes an index.html, `vite preview` fails as on a taken port. A stub Playwright module
        // stands in for the real one, which is never reached.
        const stub = mkdtempSync(join(tmpdir(), 'codemastery-harness-stub-')), temp = mkdtempSync(join(tmpdir(), 'codemastery-harness-tmp-'));
        cleanups.push(() => { rmSync(stub, { recursive: true, force: true }); rmSync(temp, { recursive: true, force: true }); });
        writeFileSync(join(stub, 'npx'), `#!/usr/bin/env node
const fs = require('node:fs'), path = require('node:path'), args = process.argv.slice(2), out = args[args.indexOf('--outDir') + 1];
if (args[1] === 'build') fs.writeFileSync(path.join(out, 'index.html'), '<html><head></head><body></body></html>');
else { console.error('error when starting preview server: Port is already in use'); process.exit(1); }
`);
        chmodSync(join(stub, 'npx'), 0o755);
        writeFileSync(join(stub, 'playwright.cjs'), 'module.exports = {};');
        const result = spawnSync(process.execPath, [harnessPath, '--playwright', join(stub, 'playwright.cjs'), '--out', join(temp, 'evidence')],
            { encoding: 'utf8', timeout: 30_000, env: { ...process.env, PATH: `${stub}:${process.env.PATH}`, TMPDIR: temp } });
        expect(result.status).toBe(1);
        expect(result.stderr).toMatch(/preview server exited \(1\)[\s\S]*Port is already in use/);
        expect(readdirSync(temp).filter((name) => name.startsWith('codemastery-browser-dist-'))).toEqual([]);
    });
});
