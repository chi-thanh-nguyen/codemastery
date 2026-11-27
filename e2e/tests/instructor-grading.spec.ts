import { randomUUID } from 'node:crypto';
import { request as playwrightRequest } from '@playwright/test';
import { expect, fixture, newAccount, registerThroughApi, signIn, target, test } from '../fixtures/auth-fixture';

/** Real disposable PostgreSQL + MinIO + REST + production UI. No intercepted responses. */
test('FE17 protected download, replacement conflict, grade and regrade against the real backend', async ({ page, request }) => {
    test.skip(!target.fixture, 'Writes only this run’s disposable fixture.');
    test.setTimeout(120_000); page.setDefaultTimeout(15_000);
    const url = (path: string) => new URL('/api' + path, target.api).href;
    const login = async (account: { email: string; password: string }) => {
        const isolated = await playwrightRequest.newContext();
        try {
            const response = await isolated.post(url('/auth/login'), { data: { email: account.email, password: account.password } });
            expect(response.status()).toBe(200); return (await response.json()).accessToken as string;
        } finally { await isolated.dispose(); }
    };
    const teacher = await login(target.instructor!), learner = newAccount('FE17');
    await registerThroughApi(request, learner);
    const learnerToken = await login(learner);
    const headers = { Authorization: `Bearer ${teacher}` }, learnerHeaders = { Authorization: `Bearer ${learnerToken}` };
    expect((await request.put(url(`/courses/${fixture.primary}/membership`), { headers: learnerHeaders })).status()).toBe(200);
    const created = await request.post(url(`/courses/${fixture.primary}/assignments`), { headers, data: {
        title: `FE17 ${randomUUID()}`, instructions: 'Submit a PDF.', deadlineAt: new Date(Date.now() + 86_400_000).toISOString(), lessonId: null,
    } });
    expect(created.status()).toBe(201); const assignment = await created.json();
    expect((await request.put(url(`/courses/${fixture.primary}/owned-assignments/${assignment.id}/publication`), { headers, data: { publicationStatus: 'published' } })).status()).toBe(200);
    const bytes = Buffer.from('%PDF-1.7\nDisposable original bytes');
    const submissionPath = `/courses/${fixture.primary}/assignments/${assignment.id}/submission`;
    const submit = (name: string, buffer: Buffer) => request.put(url(submissionPath), { headers: learnerHeaders, multipart: { file: { name, mimeType: 'application/pdf', buffer } } });
    const submitted = await submit('original.pdf', bytes); expect(submitted.status()).toBe(201);
    const list = `/courses/${fixture.primary}/owned-assignments/${assignment.id}/submissions`;
    const initial = await request.get(url(list), { headers }); expect(initial.status()).toBe(200); const original = (await initial.json()).content[0];
    await signIn(page, target.instructor!, 'teach');
    const gradeUrl = new URL(`/teach/grading/${original.id}?courseId=${fixture.primary}&assignmentId=${assignment.id}`, target.base).href;
    await page.goto(gradeUrl);
    await expect(page.getByRole('heading', { level: 1, name: assignment.title })).toBeVisible();
    const downloadEvent = page.waitForEvent('download'); await page.getByRole('button', { name: 'Download', exact: true }).click();
    const download = await downloadEvent; expect(download.suggestedFilename()).toBe('original.pdf');
    const stream = await download.createReadStream(), chunks: Buffer[] = []; for await (const chunk of stream!) chunks.push(chunk); expect(Buffer.concat(chunks)).toEqual(bytes);
    await page.getByLabel('Grade (0–100)').fill('83.25'); await page.getByRole('textbox', { name: /^Feedback/ }).fill('  Helpful feedback.\nNext steps. ');
    const replacementBytes = Buffer.from('%PDF-1.7\nDisposable replacement bytes');
    expect((await submit('replacement.pdf', replacementBytes)).status()).toBe(200);
    const save = async (label: string) => { await page.getByRole('button', { name: label, exact: true }).click(); await page.getByRole('dialog', { name: 'Save grade?' }).getByRole('button', { name: label, exact: true }).click(); };
    await save('Save grade');
    await expect(page.getByText('The learner replaced the file after you opened it. Review the new file before grading.', { exact: true })).toBeVisible();
    await expect(page.getByLabel('Grade (0–100)')).toHaveValue('83.25');
    const replacedDownload = page.waitForEvent('download'); await page.getByRole('button', { name: 'Download', exact: true }).click(); expect((await replacedDownload).suggestedFilename()).toBe('replacement.pdf');
    await page.getByRole('button', { name: 'I have reviewed the new file', exact: true }).click();
    await save('Save grade'); await expect(page.getByRole('status').filter({ hasText: 'Grade saved.' })).toBeVisible();
    const saved = await request.get(url(list), { headers }); const savedRow = (await saved.json()).content[0];
    expect(savedRow.gradePercentage).toBe(83.25); expect(savedRow.feedback).toBe('  Helpful feedback.\nNext steps. '); expect(savedRow.submittedAt).not.toBe(original.submittedAt);
    const learnerView = await request.get(url(`/courses/${fixture.primary}/assignments/${assignment.id}`), { headers: learnerHeaders });
    expect(learnerView.status()).toBe(200); expect((await learnerView.json()).submission.gradePercentage).toBe(83.25);
    await page.getByLabel('Grade (0–100)').fill('100'); await save('Update grade');
    await expect(page.getByText(/Current grade: 100 \/ 100/)).toBeVisible();
    await page.getByRole('link', { name: 'Back to submissions', exact: true }).click(); await expect(page.getByText('100 / 100', { exact: true })).toBeVisible();
    // Another learner account has no access to the protected Instructor download.
    const other = newAccount('FE17Other'); await registerThroughApi(request, other); const otherToken = await login(other);
    expect((await request.get(url(`${list}/${original.id}/file`), { headers: { Authorization: `Bearer ${otherToken}` } })).status()).toBe(403);
    expect((await request.put(url(submissionPath), { headers: learnerHeaders, multipart: { file: { name: 'after-grade.pdf', mimeType: 'application/pdf', buffer: bytes } } })).status()).toBe(409);
});

/** Negative IR-01 regression: offline browser PUT cannot commit; API fixture remains online.
 * Real production frontend/backend/PostgreSQL/MinIO, same filename, no intercepted HTTP.
 */
test('FE17 unknown uncommitted grade then same-filename replacement requires download and deliberate review', async ({ page, request, context }) => {
    test.skip(!target.fixture, 'Writes only this run’s disposable fixture.');
    test.setTimeout(120_000); page.setDefaultTimeout(15_000);
    const url = (path: string) => new URL('/api' + path, target.api).href;
    const login = async (account: { email: string; password: string }) => {
        const isolated = await playwrightRequest.newContext();
        try {
            const response = await isolated.post(url('/auth/login'), { data: { email: account.email, password: account.password } });
            expect(response.status()).toBe(200); return { Authorization: `Bearer ${(await response.json()).accessToken}` };
        } finally { await isolated.dispose(); }
    };
    const owner = await login(target.instructor!), learner = newAccount('FE17Recovery');
    await registerThroughApi(request, learner); const learnerHeaders = await login(learner);
    const course = `/courses/${fixture.primary}`;
    expect((await request.put(url(`${course}/membership`), { headers: learnerHeaders })).status()).toBe(200);
    const created = await request.post(url(`${course}/assignments`), { headers: owner, data: {
        title: `FE17 recovery ${randomUUID()}`, instructions: 'Synthetic PDF.', deadlineAt: new Date(Date.now() + 86_400_000).toISOString(), lessonId: null,
    } });
    expect(created.status()).toBe(201); const assignment = await created.json();
    const owned = `${course}/owned-assignments/${assignment.id}`;
    expect((await request.put(url(`${owned}/publication`), { headers: owner, data: { publicationStatus: 'published' } })).status()).toBe(200);
    const bytesA = Buffer.from('%PDF-1.7\nReviewed A'), bytesB = Buffer.from('%PDF-1.7\nReplacement B');
    const upload = (buffer: Buffer) => request.put(url(`${course}/assignments/${assignment.id}/submission`), { headers: learnerHeaders,
        multipart: { file: { name: 'same.pdf', mimeType: 'application/pdf', buffer } } });
    expect((await upload(bytesA)).status()).toBe(201);
    const persisted = async () => {
        const response = await request.get(url(`${owned}/submissions`), { headers: owner }); expect(response.status()).toBe(200);
        return (await response.json()).content[0];
    };
    const original = await persisted();
    await signIn(page, target.instructor!, 'teach');
    await page.goto(new URL(`/teach/grading/${original.id}?courseId=${fixture.primary}&assignmentId=${assignment.id}`, target.base).href);
    await expect(page.getByLabel('Grade (0–100)')).toBeVisible();
    let downloads = 0; page.on('download', () => downloads++);
    const downloadBytes = async () => {
        const event = page.waitForEvent('download'); await page.getByRole('button', { name: 'Download', exact: true }).click();
        const download = await event; expect(download.suggestedFilename()).toBe('same.pdf');
        const stream = await download.createReadStream(), chunks: Buffer[] = [];
        for await (const chunk of stream!) chunks.push(chunk); return Buffer.concat(chunks);
    };
    expect(await downloadBytes()).toEqual(bytesA);
    const feedback = '  Draft for deliberate review.\nKept verbatim. ';
    await page.getByLabel('Grade (0–100)').fill('83.25'); await page.getByRole('textbox', { name: /^Feedback/ }).fill(feedback);
    const tokens: string[] = [];
    page.on('request', req => { if (new URL(req.url()).pathname === `/api${owned}/submissions/${original.id}/grade`) tokens.push(req.postDataJSON().submittedAt); });
    const save = async () => {
        await page.getByRole('button', { name: 'Save grade', exact: true }).click();
        await page.getByRole('dialog', { name: 'Save grade?' }).getByRole('button', { name: 'Save grade', exact: true }).click();
    };
    await context.setOffline(true);
    await save();
    await expect(page.getByText(/We couldn't confirm whether your grade was saved/)).toBeVisible();
    await expect(page.getByText("Submission couldn't be loaded.", { exact: true })).toBeVisible();
    expect((await persisted()).graded).toBe(false); // The first PUT did not commit.
    expect((await upload(bytesB)).status()).toBe(200);
    const replacement = await persisted(); expect(replacement.submittedAt).not.toBe(original.submittedAt);
    expect(replacement.originalFilename).toBe(original.originalFilename); expect(replacement.graded).toBe(false);
    await context.setOffline(false);
    await page.getByRole('button', { name: 'Try again', exact: true }).click();
    const review = page.getByRole('button', { name: 'I have reviewed the new file', exact: true });
    await expect(review).toBeDisabled(); await expect(page.getByRole('button', { name: 'Save grade', exact: true })).toBeDisabled();
    await expect(page.getByText(/We couldn't confirm whether your grade was saved/)).toBeVisible();
    await expect(page.getByRole('textbox', { name: /^Feedback/ })).toHaveValue(feedback);
    await page.locator('form').evaluate(form => form.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true })));
    expect(tokens).toEqual([original.submittedAt]); expect((await persisted()).graded).toBe(false); expect(downloads).toBe(1);
    expect(await downloadBytes()).toEqual(bytesB);
    await expect(review).toBeEnabled(); await expect(page.getByRole('button', { name: 'Save grade', exact: true })).toBeDisabled();
    expect((await persisted()).graded).toBe(false);
    await review.click(); await save();
    await expect(page.getByRole('status').filter({ hasText: 'Grade saved.' })).toBeVisible();
    const saved = await persisted();
    expect(saved.submittedAt).toBe(replacement.submittedAt); expect(saved.gradePercentage).toBe(83.25); expect(saved.feedback).toBe(feedback);
    expect(tokens).toEqual([original.submittedAt, replacement.submittedAt]); expect(downloads).toBe(2);
    console.log('IR01 negative regression passed: uncommitted A; distinct same-name B; no B PUT or persisted grade before explicit download/review; deliberate exact-token save persisted.');
});
