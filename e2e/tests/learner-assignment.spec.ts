import { randomUUID } from 'node:crypto';
import { request as playwrightRequest } from '@playwright/test';
import { expect, fixture, newAccount, registerThroughApi, signIn, target, test } from '../fixtures/auth-fixture';

/** FE11: actual REST/PostgreSQL/MinIO and production UI, no intercepted responses. */
test('FE11 first upload, confirmed replacement, protected download, grading and closed access', async ({ page, request, browser }) => {
    test.skip(!target.fixture, 'Mutation requires this run’s disposable fixture.');
    test.setTimeout(120000);
    const url = (path: string) => new URL('/api' + path, target.api).href;
    const login = async (account: {email:string;password:string}) => {
        const client = await playwrightRequest.newContext();
        try { const response = await client.post(url('/auth/login'), {data:{email:account.email,password:account.password}}); expect(response.status()).toBe(200); return {Authorization:`Bearer ${(await response.json()).accessToken}`}; }
        finally {await client.dispose();}
    };
    const teacher = await login(target.instructor!), learner = newAccount('FE11');
    await registerThroughApi(request,learner);const own = await login(learner);
    const course = `/courses/${fixture.primary}`;
    expect((await request.put(url(`${course}/membership`),{headers:own})).status()).toBe(200);
    const created = await request.post(url(`${course}/assignments`),{headers:teacher,data:{title:`FE11 ${randomUUID()}`,instructions:'## Instructions\n\nSubmit **your work** as PDF or ZIP.',deadlineAt:new Date(Date.now()+86400000).toISOString(),lessonId:null}});
    expect(created.status()).toBe(201);const assignment = await created.json(),owned = `${course}/owned-assignments/${assignment.id}`;
    expect((await request.put(url(`${owned}/publication`),{headers:teacher,data:{publicationStatus:'published'}})).status()).toBe(200);
    await signIn(page,learner,'home');await page.goto(new URL(course,target.base).href);
    await page.getByRole('link',{name:'Assessments',exact:true}).click();await page.getByRole('link',{name:`Open assignment ${assignment.title}`}).click();
    await expect(page.locator('strong')).toHaveText('your work');
    const assertPrimary = async (name:string) => {
        const originalViewport = page.viewportSize();
        for (const [width,height] of [[1280,720],[390,844]]) {
            await page.setViewportSize({width,height});await page.evaluate(()=>window.scrollTo(0,0));
            await page.evaluate(()=>new Promise(resolve=>requestAnimationFrame(()=>requestAnimationFrame(resolve))));
            const box = await page.getByRole('button',{name,exact:true}).boundingBox();
            expect(box,`${name} at ${width}x${height}`).not.toBeNull();expect(box!.y).toBeGreaterThanOrEqual(0);expect(box!.y+box!.height).toBeLessThanOrEqual(height);
            expect(await page.evaluate(()=>document.documentElement.scrollWidth)).toBeLessThanOrEqual(width);
        }
        if(originalViewport)await page.setViewportSize(originalViewport);
    };
    await assertPrimary('Choose a file');
    const bytesA = Buffer.from('%PDF-1.7\nFE11 original actual bytes'), bytesB = Buffer.from('PK\x03\x04FE11 replacement actual bytes');
    const submissionPath = `/api${course}/assignments/${assignment.id}/submission`;
    const uploadResponse = page.waitForResponse(r=>new URL(r.url()).pathname===submissionPath&&r.request().method()==='PUT');
    await page.locator('input[type=file]').setInputFiles({name:'original.pdf',mimeType:'application/pdf',buffer:bytesA});expect((await uploadResponse).status()).toBe(201);
    await expect(page.getByText('original.pdf',{exact:true})).toBeVisible();
    const download = async (name:string,bytes:Buffer) => {
        const event = page.waitForEvent('download');await page.getByRole('button',{name:'Download',exact:true}).click();const file=await event;expect(file.suggestedFilename()).toBe(name);
        const stream=await file.createReadStream(),chunks:Buffer[]=[];for await(const chunk of stream!)chunks.push(chunk);expect(Buffer.concat(chunks)).toEqual(bytes);
    };
    await download('original.pdf',bytesA);
    await page.locator('input[type=file]').setInputFiles({name:'replacement.zip',mimeType:'application/zip',buffer:bytesB});
    const dialog=page.getByRole('dialog',{name:'Replace file?'});await expect(dialog).toContainText('original.pdf');await expect(dialog.getByRole('button',{name:'Cancel'})).toBeFocused();await dialog.getByRole('button',{name:'Cancel'}).click();
    expect((await (await request.get(url(`${course}/assignments/${assignment.id}`),{headers:own})).json()).submission.originalFilename).toBe('original.pdf');
    await page.locator('input[type=file]').setInputFiles({name:'replacement.zip',mimeType:'application/zip',buffer:bytesB});const replaced=page.waitForResponse(r=>new URL(r.url()).pathname===submissionPath&&r.request().method()==='PUT');await dialog.getByRole('button',{name:'Replace file',exact:true}).click();expect((await replaced).status()).toBe(200);
    await expect(page.getByText('replacement.zip',{exact:true})).toBeVisible();await download('replacement.zip',bytesB);
    const list=await request.get(url(`${owned}/submissions`),{headers:teacher});const row=(await list.json()).content[0];
    const teacherContext = await browser.newContext();
    try {
        const teacherPage = await teacherContext.newPage();
        await signIn(teacherPage,target.instructor!,'teach');
        await teacherPage.goto(new URL(`/teach/grading/${row.id}?courseId=${fixture.primary}&assignmentId=${assignment.id}`,target.base).href);
        await expect(teacherPage.getByRole('heading',{level:1,name:assignment.title})).toBeVisible();
        const reviewed = teacherPage.waitForEvent('download');
        await teacherPage.getByRole('button',{name:'Download',exact:true}).click();
        expect((await reviewed).suggestedFilename()).toBe('replacement.zip');
        await teacherPage.getByLabel('Grade (0–100)').fill('85.25');
        await teacherPage.getByRole('textbox',{name:/^Feedback/}).fill('  **Plain text**\nNext steps. ');
        await teacherPage.getByRole('button',{name:'Save grade',exact:true}).click();
        await teacherPage.getByRole('dialog',{name:'Save grade?'}).getByRole('button',{name:'Save grade',exact:true}).click();
        await expect(teacherPage.getByText('Grade saved.',{exact:true})).toBeVisible();
    } finally { await teacherContext.close(); }
    const graded = (await (await request.get(url(`${owned}/submissions`),{headers:teacher})).json()).content[0];
    expect(graded.submittedAt).toBe(row.submittedAt); expect(graded.gradePercentage).toBe(85.25);
    await page.getByRole('button',{name:'Refresh assignment'}).click();await expect(page.getByText('85.25 / 100',{exact:true})).toBeVisible();await expect(page.getByText('**Plain text**',{exact:false})).toBeVisible();await expect(page.locator('input[type=file]')).toHaveCount(0);
    await assertPrimary('Download');
    expect((await request.put(url(owned),{headers:teacher,data:{title:assignment.title,instructions:assignment.instructions,deadlineAt:'2020-01-01T00:00:00Z',lessonId:null}})).status()).toBe(200);
    await page.getByRole('button',{name:'Refresh assignment'}).click();await expect(page.getByText('The deadline has passed. Submissions are closed.')).toBeVisible();await download('replacement.zip',bytesB);
    await page.reload();await expect(page.getByText('85.25 / 100',{exact:true})).toBeVisible();
    const other=newAccount('FE11Other');await registerThroughApi(request,other);const otherHeaders=await login(other);expect((await request.put(url(`${course}/membership`),{headers:otherHeaders})).status()).toBe(200);
    expect((await request.get(url(`${course}/assignments/${assignment.id}/submission/file`),{headers:otherHeaders})).status()).toBe(404);
    expect((await request.delete(url(`${course}/membership`),{headers:own})).status()).toBe(200);await page.getByRole('button',{name:'Refresh assignment'}).click();await expect(page.getByRole('heading',{name:'Rejoin the course to see this assignment.'})).toBeVisible();await expect(page.getByRole('button',{name:'Download',exact:true})).toHaveCount(0);
});
