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
