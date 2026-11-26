import { randomUUID } from 'node:crypto';
import { request as playwrightRequest } from '@playwright/test';
import type { Page } from '@playwright/test';
import { expect, expectNoPageOverflow, signIn, target, test } from '../fixtures/auth-fixture';

/** Real REST, PostgreSQL and production UI; no intercepted responses or persisted credentials. */
test('FE16 complete authoring, safe Preview, exact deadlines and grading handoffs', async ({ page, request }, testInfo) => {
    test.skip(!target.fixture, 'Mutates only this run’s disposable environment.');
    test.setTimeout(180_000); page.setDefaultTimeout(15_000);
    const api = (path: string) => new URL('/api' + path, target.api).href;
    const isolated = await playwrightRequest.newContext();
    let token: string;
    try {
        const login = await isolated.post(api('/auth/login'), { data: { email: target.instructor!.email, password: target.instructor!.password } });
        expect(login.status()).toBe(200); token = (await login.json()).accessToken;
    } finally { await isolated.dispose(); }
    const headers = { Authorization: `Bearer ${token}` };
    const courseResponse = await request.post(api('/instructor/courses'), { headers, data: { title: `FE16 ${randomUUID()}`, description: 'Disposable Assignment authoring.', prerequisiteDescription: null } });
    expect(courseResponse.status()).toBe(201); const course = await courseResponse.json();
    const assignments = `/courses/${course.id}/owned-assignments`;
    const invalid = await request.post(api(`/courses/${course.id}/assignments`), { headers, data: { title: 'Invalid', instructions: '', deadlineAt: 'invalid', lessonId: null } });
    expect(invalid.status()).toBe(400);
    const problem = await invalid.json();
    expect(problem.detail).toBe('The request is invalid.'); expect(problem.errors ?? []).toEqual([]);
    await testInfo.attach('actual-assignment-400', { body: JSON.stringify(problem), contentType: 'application/json' });

    await signIn(page, target.instructor!, 'teach');
    const listUrl = `/teach/courses/${course.id}/assessments?view=assignments`;
    await page.goto(listUrl);
    await expect(page.getByText('No assignments yet', { exact: true })).toBeVisible();
    await page.getByRole('link', { name: 'New assignment', exact: true }).click();
    await expect(page).toHaveURL(/\/assignments\/new$/);
    const title = `Assignment ${randomUUID()}`;
    const markdown = '# Assignment preview\n\n**Read carefully**\n\n| A | B |\n| - | - |\n| 1 | 2 |\n\n```js\nconst answer = 42;\n```\n\n<script>window.fe16Executed = true</script>\n\n![Remote](https://example.test/fe16.png)\n\n[Unsafe](javascript:evil)';
    await page.getByRole('textbox', { name: 'Assignment title' }).fill(title);
    await page.getByRole('textbox', { name: 'Instructions', exact: true }).fill(markdown);
    await page.locator('input[type="datetime-local"]').fill('2020-01-02T12:30');
    const external: string[] = [];
    page.on('request', r => { if (new URL(r.url()).hostname === 'example.test') external.push(r.url()); });
    await page.getByRole('button', { name: 'Preview', exact: true }).focus(); await page.keyboard.press('Space');
    await page.keyboard.press('ArrowLeft');
    await expect(page.getByRole('button', { name: 'Instructions', exact: true })).toBeFocused();
    await page.keyboard.press('Space');
    await expect(page.getByRole('textbox', { name: 'Instructions', exact: true })).toHaveValue(markdown);
    await page.keyboard.press('ArrowRight');
    await expect(page.getByRole('button', { name: 'Preview', exact: true })).toBeFocused();
    await page.keyboard.press('Space');
    const preview = page.getByRole('region', { name: 'Instructions preview' });
    await expect(preview.getByRole('heading', { name: 'Assignment preview' })).toBeVisible();
    await expect(preview.getByRole('table')).toBeVisible();
    await expect(preview.locator('pre')).toContainText('const answer = 42;');
    await expect(preview.locator('script,img,a[href^="javascript:"]')).toHaveCount(0);
    expect(await page.evaluate(() => (window as Window & { fe16Executed?: boolean }).fe16Executed)).toBeUndefined(); expect(external).toEqual([]);
    const createResponse = page.waitForResponse(r => r.request().method() === 'POST' && new URL(r.url()).pathname === `/api/courses/${course.id}/assignments`);
    await page.getByRole('button', { name: 'Create assignment', exact: true }).click();
    const created = await createResponse; expect(created.status()).toBe(201); const assignment = await created.json();
    await expect(page).toHaveURL(new RegExp(`/assignments/${assignment.id}$`));
    await expect(page.getByText('Assignment created. It stays hidden until you publish it.', { exact: true })).toBeVisible();
    const editorUrl = new URL(page.url()).pathname;
    const checkControls = async () => {
        const clipped = await page.locator('main input:not([type="hidden"]), main textarea, main button, main [role="combobox"]').evaluateAll(elements => elements.filter(el => {
            let box = el.getBoundingClientRect(); const style = getComputedStyle(el);
            if (box.width <= 2 || box.height <= 2 || style.visibility === 'hidden') return false;
            // DESIGN intentionally scrolls segmented navigation on narrow screens. Prove each
            // control can be revealed inside that scroll surface; hidden clipping gets no exemption.
            if (box.left < -1 || box.right > innerWidth + 1) {
                let parent = el.parentElement;
                while (parent && !['auto', 'scroll'].includes(getComputedStyle(parent).overflowX)) parent = parent.parentElement;
                if (parent && parent.scrollWidth > parent.clientWidth) {
                    el.scrollIntoView({ block: 'nearest', inline: 'nearest' }); box = el.getBoundingClientRect();
                }
            }
            return box.left < -1 || box.right > innerWidth + 1;
        }).map(el => ({ label: el.getAttribute('aria-label') ?? el.textContent, left: el.getBoundingClientRect().left, right: el.getBoundingClientRect().right })));
        expect(clipped, 'Controls must fit the viewport even when a parent clips overflow').toEqual([]);
    };
    await page.reload();
    await expect(page.getByRole('textbox', { name: 'Instructions', exact: true })).toHaveValue(markdown);
    const precise = '2020-01-02T12:30:00.123456Z';
    expect((await request.put(api(`${assignments}/${assignment.id}`), { headers, data: { title, instructions: markdown, deadlineAt: precise, lessonId: null } })).status()).toBe(200);
    await page.reload();
    await expect(page.getByRole('textbox', { name: 'Assignment title' })).toHaveValue(title);
    await page.getByRole('textbox', { name: 'Assignment title' }).fill(title + ' edited');
    await page.getByRole('button', { name: 'Save changes', exact: true }).click();
    await expect(page.getByText('Saved', { exact: true })).toBeVisible();
    const current = await (await request.get(api(`${assignments}/${assignment.id}`), { headers })).json();
    expect(current.deadlineAt).toBe(precise); expect(current.instructions).toBe(markdown);
    await page.getByRole('button', { name: 'Publish assignment', exact: true }).click();
    await expect(page.getByRole('dialog')).toContainText('Learners in this course');
    await page.getByRole('dialog').getByRole('button', { name: 'Publish assignment', exact: true }).click();
    await expect(page.getByText('Assignment published.', { exact: true })).toBeVisible();
    await page.getByRole('textbox', { name: 'Instructions', exact: true }).fill(markdown + '\nUnsaved.');
    await page.getByRole('link', { name: 'Open grading', exact: true }).click();
    await expect(page.getByRole('dialog')).toContainText('Leave without saving?');
    await expect(page.getByRole('button', { name: 'Stay', exact: true })).toBeFocused();
    await page.getByRole('button', { name: 'Stay', exact: true }).click();
    await expect(page.getByRole('textbox', { name: 'Instructions', exact: true })).toHaveValue(markdown + '\nUnsaved.');
    await page.getByRole('button', { name: 'Save changes', exact: true }).click();
    await expect(page.getByText('Saved', { exact: true })).toBeVisible();

    // Every prescribed width in all three preferences, actual production editor and list.
    for (const preference of ['light', 'dark', 'system']) {
        await page.setViewportSize({ width: 1440, height: 960 });
        await page.getByRole('button', { name: /^Theme:/ }).click();
        await page.getByRole('menuitemradio', { name: new RegExp(`^${preference[0].toUpperCase()}${preference.slice(1)}`) }).click();
        await page.keyboard.press('Escape');
        await page.emulateMedia({ colorScheme: 'dark' });
        await expect(page.locator('html')).toHaveAttribute('data-color-scheme', preference === 'light' ? 'light' : 'dark');
        for (const width of [360, 768, 1024, 1280, 1440]) {
            await page.setViewportSize({ width, height: 960 }); await expectResponsiveHeaderMode(page, width);
            await page.getByRole('button', { name: 'Preview', exact: true }).click();
            await expectNoPageOverflow(page); await checkControls(); await page.evaluate(() => scrollTo(0, 0));
            await page.screenshot({ path: testInfo.outputPath(`fe16-${preference}-${width}.png`), fullPage: true });
            await page.getByRole('button', { name: 'Instructions', exact: true }).click();
            await expectNoPageOverflow(page); await checkControls();
        }
        if (preference === 'system') {
            await page.emulateMedia({ colorScheme: 'light' });
            await expect(page.locator('html')).toHaveAttribute('data-color-scheme', 'light');
        }
    }
    await page.getByRole('button', { name: 'Hide assignment', exact: true }).click();
    await page.getByRole('dialog').getByRole('button', { name: 'Hide assignment', exact: true }).click();
    await expect(page.getByText('Assignment hidden.', { exact: true })).toBeVisible();
    await page.getByRole('link', { name: 'Back to assignments', exact: true }).click();
    await expect(page).toHaveURL(new RegExp(`/assessments\\?view=assignments$`));
    await expect(page.getByRole('link', { name: `Open assignment: ${title} edited`, exact: true })).toBeVisible();
    await expect(page.getByRole('link', { name: `Grade: ${title} edited`, exact: true })).toHaveCount(0);
    for (const preference of ['Light', 'Dark', 'System']) {
        await page.setViewportSize({ width: 1440, height: 960 });
        await page.getByRole('button', { name: /^Theme:/ }).click();
        await page.getByRole('menuitemradio', { name: new RegExp(`^${preference}`) }).click(); await page.keyboard.press('Escape');
        for (const width of [360, 768, 1024, 1280, 1440]) {
            await page.setViewportSize({ width, height: 960 }); await expectResponsiveHeaderMode(page, width); await expectNoPageOverflow(page); await checkControls();
        }
    }
    await page.setViewportSize({ width: 320, height: 844 }); await expectResponsiveHeaderMode(page, 320); await expectNoPageOverflow(page); await checkControls();
    await page.goto(editorUrl);
    await expect(page.getByRole('link', { name: 'Open grading', exact: true })).toHaveAttribute('href', `/teach/grading?courseId=${course.id}&assignmentId=${assignment.id}`);
    await page.getByRole('link', { name: 'Open grading', exact: true }).click();
    await expect(page).toHaveURL(new RegExp(`/teach/grading\\?courseId=${course.id}&assignmentId=${assignment.id}$`));
    await expect(page.getByText('No submissions yet', { exact: true })).toBeVisible();
});

async function expectResponsiveHeaderMode(page: Page, width: number) {
    const header = page.getByRole('banner');
    await expect(header).toBeVisible();
    const theme = header.getByRole('button', { name: /^Theme:/ });
    // DESIGN/CT16 md=900: AppHeader mounts this control only in desktop React mode.
    // Wait for that commit in both resize directions before sampling strict geometry.
    if (width >= 900) await expect(theme).toBeVisible();
    else await expect(theme).toHaveCount(0);
}

test('FE14 real Course, Lesson, mappings and three material types', async ({ page }, testInfo) => {
    test.skip(!target.fixture, 'Mutates only this run’s disposable environment.');
    test.setTimeout(120_000);
    page.setDefaultTimeout(15_000);
    await signIn(page, target.instructor!, 'teach');
    await page.getByRole('link', { name: 'Create course', exact: true }).first().click();
    const title = `FE14 ${randomUUID()}`;
    await page.getByRole('textbox', { name: 'Course title' }).fill(title);
    await page.getByRole('textbox', { name: 'Description' }).fill('Disposable FE14 authoring qualification.');
    await page.getByRole('button', { name: 'Create course', exact: true }).click();
    await expect(page.getByRole('heading', { level: 1, name: title })).toBeVisible();
    const coursePath = new URL(page.url()).pathname;
    await page.getByRole('button', { name: 'Add chapter', exact: true }).first().click();
    await page.getByRole('textbox', { name: 'Chapter title' }).fill('Chapter A');
    await page.getByRole('dialog').getByRole('button', { name: 'Add chapter', exact: true }).click();
    await expect(page.getByRole('heading', { name: 'Chapter 1: Chapter A' })).toBeVisible();
    await page.getByRole('button', { name: 'Edit chapter', exact: true }).click();
    await page.getByRole('textbox', { name: 'Chapter title' }).fill('Chapter edited');
    await page.getByRole('button', { name: 'Save chapter', exact: true }).click();
    await expect(page.getByRole('heading', { name: 'Chapter 1: Chapter edited' })).toBeVisible();
    await page.getByRole('button', { name: 'Add chapter', exact: true }).first().click();
    await page.getByRole('textbox', { name: 'Chapter title' }).fill('Chapter B');
    await page.getByRole('dialog').getByRole('button', { name: 'Add chapter', exact: true }).click();
    await page.getByRole('button', { name: 'Move Chapter B up', exact: true }).click();
    await expect(page.getByRole('heading', { name: 'Chapter 1: Chapter B', exact: true })).toBeVisible();
    await page.getByRole('button', { name: 'Move Chapter B down', exact: true }).click();
    await expect(page.getByRole('heading', { name: 'Chapter 1: Chapter edited', exact: true })).toBeVisible();
    await page.getByRole('button', { name: 'Add lesson', exact: true }).first().click();
    await page.getByRole('textbox', { name: 'Lesson title' }).fill('Lesson A');
    await page.getByRole('dialog').getByRole('button', { name: 'Add lesson', exact: true }).click();
    await expect(page.getByRole('dialog')).toHaveCount(0);
    await page.getByRole('button', { name: 'Add lesson', exact: true }).first().click();
    await page.getByRole('textbox', { name: 'Lesson title' }).fill('Lesson B');
    await page.getByRole('dialog').getByRole('button', { name: 'Add lesson', exact: true }).click();
    await expect(page.getByRole('dialog')).toHaveCount(0);
    await page.getByRole('button', { name: 'Move Lesson B up', exact: true }).click();
    await expect(page.getByRole('status').filter({ hasText: 'Moved Lesson B to position 1.' })).toHaveCount(1);
    await page.getByRole('button', { name: 'Move Lesson B down', exact: true }).click();
    await page.getByRole('button', { name: 'Edit', exact: true }).first().click();
    await expect(page).toHaveURL(/\/lessons\/[a-f0-9-]+$/);
    const lessonPath = new URL(page.url()).pathname;
    await page.getByRole('textbox', { name: 'Content (Markdown)' }).fill('# Lesson heading\n\n**Safe Markdown**\n\n| A | B |\n| - | - |\n| 1 | 2 |\n\n<script>bad()</script>');
    await page.getByRole('button', { name: 'Save changes', exact: true }).click();
    await expect(page.getByText('Saved', { exact: true })).toBeVisible();
    await page.getByRole('button', { name: 'Preview', exact: true }).click();
    await expect(page.getByRole('heading', { name: 'Lesson heading' })).toBeVisible();
    await expect(page.locator('main script')).toHaveCount(0);
    await page.getByRole('button', { name: 'Add text', exact: true }).click();
    await page.getByRole('dialog').getByRole('textbox', { name: 'Title' }).fill('Reading');
    await page.getByRole('textbox', { name: 'Text (Markdown)' }).fill('**Material body**');
    await page.getByRole('dialog').getByRole('button', { name: 'Add material', exact: true }).click();
    await expect(page.getByRole('heading', { name: 'Reading', exact: true })).toBeVisible();
    const reading = page.getByRole('listitem').filter({ has: page.getByRole('heading', { name: 'Reading', exact: true }) });
    await reading.getByRole('button', { name: 'Edit material', exact: true }).click();
    await page.getByRole('textbox', { name: 'Text (Markdown)' }).fill('**Updated material body**');
    await page.getByRole('button', { name: 'Save material', exact: true }).click();
    await expect(reading.getByText('Updated material body', { exact: true })).toBeVisible();
    await reading.getByRole('button', { name: 'Publish material', exact: true }).click();
    await page.getByRole('dialog').getByRole('button', { name: 'Publish material', exact: true }).click();
    await expect(reading.getByText('Published', { exact: true })).toBeVisible();
    await reading.getByRole('button', { name: 'Hide material', exact: true }).click();
    await page.getByRole('dialog').getByRole('button', { name: 'Hide material', exact: true }).click();
    await expect(reading.getByText('Hidden', { exact: true })).toBeVisible();
    await page.getByRole('button', { name: 'Add video link', exact: true }).click();
    await page.getByRole('dialog').getByRole('textbox', { name: 'Title' }).fill('Video');
    const videoInput = page.getByRole('textbox', { name: 'Video link' });
    const malformedVideo = 'https://www.youtube-nocookie.com/embed/x' + ' '.repeat(120_000) + '!';
    await videoInput.fill(malformedVideo);
    const materialWrites: string[] = [];
    const recordWrite = (request: import('@playwright/test').Request) => {
        if (['POST', 'PUT'].includes(request.method()) && new URL(request.url()).pathname.endsWith('/materials')) materialWrites.push(request.method());
    };
    page.on('request', recordWrite);
    const validation = await page.getByRole('dialog').getByRole('button', { name: 'Add material', exact: true }).evaluate((button: HTMLButtonElement) => {
        const start = performance.now(); button.click(); return { durationMs: performance.now() - start };
    });
    await expect(videoInput).toHaveAttribute('aria-invalid', 'true');
    await expect(page.getByText('Enter a YouTube privacy-enhanced embed address.', { exact: true })).toBeVisible();
    expect(materialWrites).toEqual([]);
    page.off('request', recordWrite);
    await testInfo.attach('video-validation', { body: JSON.stringify({ ...validation, inputLength: malformedVideo.length, materialWrites }), contentType: 'application/json' });
    await videoInput.fill('  https://www.youtube-nocookie.com/embed/test_video  ');
    const createdVideo = page.waitForResponse(response => response.request().method() === 'POST' && new URL(response.url()).pathname.endsWith('/materials'));
    await page.getByRole('dialog').getByRole('button', { name: 'Add material', exact: true }).click();
    const videoResponse = await createdVideo;
    expect(videoResponse.status()).toBe(201);
    expect(videoResponse.request().postDataJSON().externalVideoUrl).toBe('https://www.youtube-nocookie.com/embed/test_video');
    await expect(page.getByRole('heading', { name: 'Video', exact: true })).toBeVisible();
    const video = page.getByRole('listitem').filter({ has: page.getByRole('heading', { name: 'Video', exact: true }) });
    await video.getByRole('button', { name: 'Edit material', exact: true }).click();
    await page.getByRole('textbox', { name: 'Video link' }).fill('https://www.youtube-nocookie.com/embed/replaced_video');
    await page.getByRole('button', { name: 'Save material', exact: true }).click();
    await expect(video.getByText('https://www.youtube-nocookie.com/embed/replaced_video', { exact: true })).toBeVisible();
    if (process.env.E2E_MATERIAL_FILES === '1') {
        await page.getByRole('button', { name: 'Add PDF file', exact: true }).click();
        await page.getByRole('dialog').getByRole('textbox', { name: 'Title' }).fill('PDF');
        await page.locator('input[type=file]').setInputFiles({ name: 'lesson.pdf', mimeType: 'application/pdf', buffer: Buffer.from('%PDF-1.7\nFE14 original bytes') });
        await page.getByRole('dialog').getByRole('button', { name: 'Add material', exact: true }).click();
        const row = page.getByRole('listitem').filter({ has: page.getByRole('heading', { name: 'PDF', exact: true }) });
        await expect(row).toBeVisible();
        await row.getByRole('button', { name: 'Edit title', exact: true }).click();
        await page.getByRole('dialog').getByRole('textbox', { name: 'Title' }).fill('PDF renamed');
        await page.getByRole('button', { name: 'Save material', exact: true }).click();
        const renamed = page.getByRole('listitem').filter({ has: page.getByRole('heading', { name: 'PDF renamed', exact: true }) });
        await renamed.getByRole('button', { name: 'Replace file', exact: true }).click();
        await page.locator('input[type=file]').setInputFiles({ name: 'replacement.pdf', mimeType: 'application/pdf', buffer: Buffer.from('%PDF-1.7\nFE14 replaced bytes') });
        await page.getByRole('button', { name: 'Save material', exact: true }).click();
        await page.getByRole('dialog').filter({ has: page.getByRole('heading', { name: 'Replace file?' }) }).getByRole('button', { name: 'Replace file', exact: true }).click();
        await expect(page.getByRole('dialog')).toHaveCount(0);
        const downloaded = page.waitForEvent('download');
        await renamed.getByRole('button', { name: 'Download PDF', exact: true }).click();
        const download = await downloaded; expect(download.suggestedFilename()).toBe('replacement.pdf');
        const stream = await download.createReadStream(); const chunks: Buffer[] = []; for await (const chunk of stream!) chunks.push(chunk);
        expect(Buffer.concat(chunks).toString()).toBe('%PDF-1.7\nFE14 replaced bytes');
        await renamed.getByRole('button', { name: 'Publish material', exact: true }).click();
        await page.getByRole('dialog').getByRole('button', { name: 'Publish material', exact: true }).click();
        await expect(renamed.getByText('Published', { exact: true })).toBeVisible();
        await renamed.getByRole('button', { name: 'Hide material', exact: true }).click();
        await page.getByRole('dialog').getByRole('button', { name: 'Hide material', exact: true }).click();
        await expect(renamed.getByText('Hidden', { exact: true })).toBeVisible();
    }
    // Map a real course Skill through the existing Skills screen, then toggle from Lesson editor.
    await page.getByRole('link', { name: 'Content', exact: true }).click();
    await page.getByRole('button', { name: 'Skills', exact: true }).click();
    await page.getByRole('button', { name: 'Add skill', exact: true }).first().click();
    await page.getByRole('textbox', { name: 'Skill name' }).fill('Authoring skill');
    await page.getByRole('button', { name: 'Add skill', exact: true }).last().click();
    await expect(page.getByRole('list', { name: 'Course skills' }).getByText('Authoring skill', { exact: true })).toBeVisible();
    await page.goto(lessonPath);
    const chip = page.getByRole('button', { name: 'Authoring skill', exact: true });
    await chip.click(); await expect(chip).toHaveAttribute('aria-pressed', 'true');
    await chip.focus(); await page.keyboard.press('Space'); await expect(chip).toHaveAttribute('aria-pressed', 'false');
    await page.getByRole('button', { name: 'Publish lesson', exact: true }).click();
    await page.getByRole('dialog').getByRole('button', { name: 'Publish lesson', exact: true }).click();
    await expect(page.getByRole('button', { name: 'Hide lesson', exact: true })).toBeEnabled();
    const responsive: { width: number; scroll: number; client: number; desktopThemeCount: number }[] = [];
    for (const width of [360, 768, 1024, 1280, 1440]) {
        await page.setViewportSize({ width, height: 960 });
        await expectResponsiveHeaderMode(page, width);
        await expectNoPageOverflow(page);
        responsive.push({ width, ...await page.evaluate(() => ({ scroll: document.documentElement.scrollWidth, client: document.documentElement.clientWidth })), desktopThemeCount: await page.getByRole('banner').getByRole('button', { name: /^Theme:/ }).count() });
        await page.screenshot({ path: testInfo.outputPath(`fe14-${width}.png`), fullPage: true });
    }
    await testInfo.attach('responsive-geometry', { body: JSON.stringify(responsive), contentType: 'application/json' });
    await page.getByRole('button', { name: /^Theme:/ }).click();
    await page.getByRole('menuitemradio', { name: 'Dark', exact: true }).click();
    await page.keyboard.press('Escape');
    await expect(page.locator('html')).toHaveAttribute('data-color-scheme', 'dark');
    await expectNoPageOverflow(page); await page.screenshot({ path: testInfo.outputPath('fe14-dark.png'), fullPage: true });
    await page.emulateMedia({ colorScheme: 'dark' });
    await page.getByRole('button', { name: /^Theme:/ }).click();
    await page.getByRole('menuitemradio', { name: /^System/ }).click();
    await page.keyboard.press('Escape');
    await expect(page.locator('html')).toHaveAttribute('data-color-scheme', 'dark');
    await page.emulateMedia({ colorScheme: 'light' });
    await expect(page.locator('html')).toHaveAttribute('data-color-scheme', 'light');
    await page.screenshot({ path: testInfo.outputPath('fe14-system-light.png'), fullPage: true });
    await page.goto(coursePath);
    await page.getByRole('button', { name: 'Publish course', exact: true }).click();
    await page.getByRole('dialog').getByRole('button', { name: 'Publish course', exact: true }).click();
    await expect(page.getByRole('button', { name: 'Hide course', exact: true })).toBeEnabled();
    await page.getByRole('button', { name: 'Hide course', exact: true }).click();
    await page.getByRole('dialog').getByRole('button', { name: 'Hide course', exact: true }).click();
    await expect(page.getByRole('button', { name: 'Publish course', exact: true })).toBeEnabled();
});

test('FE14-IR-01: committed PUT survives app Leave and remount',async({page},info)=>{
 test.skip(!target.fixture); test.setTimeout(90000); await signIn(page,target.instructor!,'teach');
 await page.getByRole('link',{name:'Create course',exact:true}).first().click();
 await page.getByRole('textbox',{name:'Course title'}).fill(`Review ${randomUUID()}`);
 await page.getByRole('textbox',{name:'Description'}).fill('Disposable remediation regression');
 await page.getByRole('button',{name:'Create course',exact:true}).click();
 await page.getByRole('button',{name:'Add chapter',exact:true}).first().click();
 await page.getByRole('textbox',{name:'Chapter title'}).fill('Probe chapter');
 await page.getByRole('dialog').getByRole('button',{name:'Add chapter',exact:true}).click();
 await page.getByRole('button',{name:'Add lesson',exact:true}).first().click();
 await page.getByRole('textbox',{name:'Lesson title'}).fill('Original title');
 await page.getByRole('dialog').getByRole('button',{name:'Add lesson',exact:true}).click();
 await expect(page.getByRole('dialog')).toHaveCount(0); await page.getByRole('button',{name:'Edit',exact:true}).click();
 const lessonPath=new URL(page.url()).pathname, courseId=lessonPath.split('/')[3];
 let reads=0, first=true, release!:()=>void, committed!:()=>void;
 const commit=new Promise<void>(r=>committed=r), gate=new Promise<void>(r=>release=r);
 page.on('request',r=>{if(r.method()==='GET'&&new URL(r.url()).pathname===`/api/instructor/courses/${courseId}`)reads++});
 let serverTitle='';
 await page.route(`**/api/instructor/courses/${courseId}/lessons/*`,async route=>{
  if(route.request().method()!=='PUT'||!first){await route.continue();return} first=false;
  const response=await route.fetch(); const data=await response.json();serverTitle=data.title;committed();await gate;
  await route.fulfill({response}).catch(()=>{});
 });
 await page.getByRole('textbox',{name:'Lesson title'}).fill('Server committed title');
 await page.getByRole('button',{name:'Save changes',exact:true}).click(); await commit;
 await page.getByRole('link',{name:'Content',exact:true}).click();
 await page.getByRole('dialog').getByRole('button',{name:'Leave',exact:true}).click();
 await expect(page.getByRole('button',{name:'Edit',exact:true})).toBeDisabled();
 await expect(page.getByRole('button',{name:'Add chapter',exact:true}).first()).toBeDisabled();
 expect(reads).toBe(0); release();
 await expect(page.getByRole('button',{name:'Edit',exact:true})).toBeEnabled();
 await page.getByRole('button',{name:'Edit',exact:true}).click();
 const displayed=await page.getByRole('textbox',{name:'Lesson title'}).inputValue();
 expect(serverTitle).toBe('Server committed title'); expect(displayed).toBe('Server committed title'); expect(reads).toBeGreaterThanOrEqual(1);
 const evidence={serverTitle,displayedTitleOnRemount:displayed,authoritativeReadsSinceSend:reads};
 console.log('AUTHOR_REMEDIATION_PUT',JSON.stringify(evidence));
 await info.attach('pending-put-evidence',{body:JSON.stringify(evidence,null,2),contentType:'application/json'});
 await page.getByRole('textbox',{name:'Content (Markdown)'}).fill('A later content edit');
 const saved=page.waitForResponse(r=>r.request().method()==='PUT'&&new URL(r.url()).pathname.endsWith(lessonPath.replace('/teach/','/api/instructor/')));
 await page.getByRole('button',{name:'Save changes',exact:true}).click(); const second=await saved; const overwritten=await second.json();
 expect(overwritten.title).toBe('Server committed title');
 console.log('AUTHOR_REMEDIATION_PRESERVED_TITLE',JSON.stringify({title:overwritten.title,contentMarkdown:overwritten.contentMarkdown}));
 await expect(page.getByText('Saved',{exact:true})).toBeVisible();

});

test('FE14-IR-01: accepted PDF survives native Back and failed recovery',async({page,browserName},info)=>{
 test.skip(process.env.E2E_MATERIAL_FILES !== '1', 'Requires the disposable MinIO-enabled fixture.');
 test.skip(browserName === 'webkit', 'The WebKit route.fetch multipart probe returned 400 before acceptance on both baseline and remediation; qualify the delayed-upload race in Chromium.');
 test.skip(!target.fixture);test.setTimeout(90000);await signIn(page,target.instructor!,'teach');
 await page.getByRole('link',{name:'Create course',exact:true}).first().click();
 await page.getByRole('textbox',{name:'Course title'}).fill(`Review ${randomUUID()}`);
 await page.getByRole('textbox',{name:'Description'}).fill('Disposable remediation regression');
 await page.getByRole('button',{name:'Create course',exact:true}).click();
 await page.getByRole('button',{name:'Add chapter',exact:true}).first().click();
 await page.getByRole('textbox',{name:'Chapter title'}).fill('Probe chapter');
 await page.getByRole('dialog').getByRole('button',{name:'Add chapter',exact:true}).click();
 await page.getByRole('button',{name:'Add lesson',exact:true}).first().click();
 await page.getByRole('textbox',{name:'Lesson title'}).fill('Original title');
 await page.getByRole('dialog').getByRole('button',{name:'Add lesson',exact:true}).click();
 await expect(page.getByRole('dialog')).toHaveCount(0); await page.getByRole('button',{name:'Edit',exact:true}).click();

 const lessonPath=new URL(page.url()).pathname, c=lessonPath.split('/')[3];let reads=0,first=true,release!:()=>void,accepted!:()=>void;
 const gate=new Promise<void>(r=>release=r),commit=new Promise<void>(r=>accepted=r);
 page.on('request',r=>{if(r.method()==='GET'&&new URL(r.url()).pathname===`/api/instructor/courses/${c}`)reads++});
 await page.route(`**/api/instructor/courses/${c}/lessons/*/file-materials`,async route=>{
  if(!first){await route.continue();return}first=false;const response=await route.fetch();expect(response.status(), await response.text()).toBe(201);accepted();await gate;await route.fulfill({response}).catch(()=>{});
 });
 const add=async()=>{await page.getByRole('button',{name:'Add PDF file',exact:true}).click();await page.getByRole('dialog').getByRole('textbox',{name:'Title'}).fill('PDF accepted');await page.locator('input[type=file]').setInputFiles({name:'pending.pdf',mimeType:'application/pdf',buffer:Buffer.from('%PDF-1.7\nRemediation pending upload')});await page.getByRole('dialog').getByRole('button',{name:'Add material',exact:true}).click()};
 let failRecovery = true;
 await page.route(`**/api/instructor/courses/${c}`, async route => {
  if(route.request().method()==='GET' && failRecovery) {
   failRecovery=false; await route.fulfill({status:503,contentType:'application/json',body:'{}'});
  } else await route.continue();
 });
 await add(); await commit; await page.goBack();
 await expect(page.getByRole('button',{name:'Edit',exact:true})).toBeDisabled();
 await page.goForward();
 await expect(page.getByRole('button',{name:'Add PDF file',exact:true})).toBeDisabled();
 expect(reads).toBe(0); release();
 await expect(page.getByText("We couldn't load this course. The last loaded version is shown.")).toBeVisible();
 await expect(page.getByRole('button',{name:'Add PDF file',exact:true})).toBeDisabled();
 await page.getByRole('button',{name:'Try again',exact:true}).click();
 await expect(page.getByRole('heading',{name:'PDF accepted',exact:true})).toHaveCount(1);
 await expect(page.getByRole('button',{name:'Add PDF file',exact:true})).toBeEnabled();
 expect(reads).toBe(2);
 const evidence={materialRows:await page.getByRole('heading',{name:'PDF accepted',exact:true}).count(),authoritativeReads:reads};
 console.log('AUTHOR_REMEDIATION_PDF',JSON.stringify(evidence));
 await info.attach('pending-pdf-recovery',{body:JSON.stringify(evidence),contentType:'application/json'});
});

// Isolate the response-interception limitation from actual WebKit file transport.
test('FE14-IR-01: WebKit PDF transport without response interception', async ({page, browserName}) => {
    test.skip(process.env.E2E_MATERIAL_FILES !== '1', 'Requires the disposable MinIO-enabled fixture.');
    test.skip(browserName !== 'webkit' || !target.fixture);
    await signIn(page, target.instructor!, 'teach');
    await page.getByRole('link', {name:'Create course',exact:true}).first().click();
    await page.getByRole('textbox', {name:'Course title'}).fill(`PDF diagnostic ${randomUUID()}`);
    await page.getByRole('textbox', {name:'Description'}).fill('Disposable transport diagnostic.');
    await page.getByRole('button', {name:'Create course',exact:true}).click();
    await page.getByRole('button', {name:'Add chapter',exact:true}).first().click();
    await page.getByRole('textbox', {name:'Chapter title'}).fill('Chapter');
    await page.getByRole('dialog').getByRole('button', {name:'Add chapter',exact:true}).click();
    await page.getByRole('button', {name:'Add lesson',exact:true}).first().click();
    await page.getByRole('textbox', {name:'Lesson title'}).fill('Lesson');
    await page.getByRole('dialog').getByRole('button', {name:'Add lesson',exact:true}).click();
    await expect(page.getByRole('dialog')).toHaveCount(0);
    await page.getByRole('button', {name:'Edit',exact:true}).click();
    await page.getByRole('button', {name:'Add PDF file',exact:true}).click();
    await page.getByRole('dialog').getByRole('textbox', {name:'Title'}).fill('WebKit PDF');
    await page.locator('input[type=file]').setInputFiles({name:'pending.pdf',mimeType:'application/pdf',buffer:Buffer.from('%PDF-1.7\nRemediation pending upload')});
    const response = page.waitForResponse(r => r.request().method() === 'POST' && new URL(r.url()).pathname.endsWith('/file-materials'));
    await page.getByRole('dialog').getByRole('button', {name:'Add material',exact:true}).click();
    expect((await response).status()).toBe(201);
    await expect(page.getByRole('heading', {name:'WebKit PDF',exact:true})).toHaveCount(1);
    console.log('AUTHOR_WEBKIT_DIRECT_PDF: HTTP 201, one authoritative material row, no response interception');
});
