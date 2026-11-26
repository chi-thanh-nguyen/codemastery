/**
 * Group B adaptive learning journey through the real frontend, REST API and PostgreSQL (F21 / CT35).
 * Exercises contrasting learner profiles (A-1), non-blocking prerequisite warnings (A-2),
 * skipped placement (A-3), and mastery-based practice difficulty selection & fallback (A-4).
 */
import { randomUUID } from 'node:crypto';
import type { APIRequestContext, Browser, BrowserContext, Page } from '@playwright/test';
import {
    expect,
    expectNoPageOverflow,
    newAccount,
    registerThroughApi,
    signIn,
    target,
    test,
    type Account,
} from '../fixtures/auth-fixture';

interface AdaptiveCourseFixture {
    courseId: string;
    courseTitle: string;
    lessons: { id: string; title: string }[];
    skills: { id: string; name: string }[];
    placementQuizId: string;
    questions: { id: string; skillIndex: number; difficulty: 'easy' | 'hard'; prompt: string }[];
}

async function apiHeaders(request: APIRequestContext, account: Account) {
    const res = await request.post(new URL('/api/auth/login', target.api).href, {
        data: { email: account.email, password: account.password },
    });
    expect(res.status(), 'instructor login for adaptive fixture').toBe(200);
    const { accessToken } = (await res.json()) as { accessToken: string };
    return { Authorization: `Bearer ${accessToken}` };
}

/**
 * Creates an isolated published Course mirroring the SD03 loops sub-graph (ADR013 §8.2):
 * K1 Conditionals; K2 While Loops -> K1; K3 For Loops -> K1; K4 Loop Control -> K2, K3; K5 Lists -> K3.
 */
async function createAdaptiveCourseFixture(
    request: APIRequestContext,
    instructor: Account
): Promise<AdaptiveCourseFixture> {
    const headers = await apiHeaders(request, instructor);
    const api = (path: string) => new URL(path, target.api).href;
    const suffix = randomUUID().slice(0, 8);
    const courseTitle = `~E2E Journey Adaptive ${suffix}`;

    // 1. Create Course (hidden)
    const courseRes = await request.post(api('/api/instructor/courses'), {
        headers,
        data: {
            title: courseTitle,
            description: 'Automated adaptive learning E2E verification course.',
            prerequisiteDescription: null,
        },
    });
    expect(courseRes.status()).toBe(201);
    const { id: courseId } = (await courseRes.json()) as { id: string };

    // 2. Create Chapter and 5 Lessons E1..E5 (published)
    const chapRes = await request.post(api(`/api/instructor/courses/${courseId}/chapters`), {
        headers,
        data: { title: 'Loops Sub-graph' },
    });
    expect(chapRes.status()).toBe(201);
    const { id: chapterId } = (await chapRes.json()) as { id: string };

    const lessonNames = [
        'E1 Conditionals',
        'E2 While Loops',
        'E3 For Loops',
        'E4 Loop Control',
        'E5 Lists',
    ];
    const lessons: { id: string; title: string }[] = [];
    for (const title of lessonNames) {
        const lRes = await request.post(
            api(`/api/instructor/courses/${courseId}/chapters/${chapterId}/lessons`),
            { headers, data: { title, contentMarkdown: `Content for ${title}.` } }
        );
        expect(lRes.status()).toBe(201);
        const created = (await lRes.json()) as { id: string; title: string };
        const pubRes = await request.put(
            api(`/api/instructor/courses/${courseId}/lessons/${created.id}/publication`),
            { headers, data: { publicationStatus: 'published' } }
        );
        expect(pubRes.status()).toBe(200);
        lessons.push({ id: created.id, title: created.title });
    }

    // 3. Create 5 Skills K1..K5 and prerequisite edges
    const skillNames = ['Conditionals', 'While Loops', 'For Loops', 'Loop Control', 'Lists'];
    const skills: { id: string; name: string }[] = [];
    for (const name of skillNames) {
        const sRes = await request.post(api(`/api/courses/${courseId}/skills`), {
            headers,
            data: { name, description: `Mastery skill for ${name}` },
        });
        expect(sRes.status()).toBe(201);
        const created = (await sRes.json()) as { id: string; name: string };
        skills.push({ id: created.id, name: created.name });
    }

    const edges: [number, number][] = [
        [1, 0], // K2 -> K1
        [2, 0], // K3 -> K1
        [3, 1], // K4 -> K2
        [3, 2], // K4 -> K3
        [4, 2], // K5 -> K3
    ];
    for (const [skillIdx, prereqIdx] of edges) {
        const eRes = await request.put(
            api(
                `/api/courses/${courseId}/skills/${skills[skillIdx].id}/prerequisites/${skills[prereqIdx].id}`
            ),
            { headers }
        );
        expect(eRes.status()).toBe(204);
    }

    // 4. Map Lessons E1..E5 to K1..K5 (1-to-1)
    for (let i = 0; i < 5; i += 1) {
        const mRes = await request.put(
            api(`/api/courses/${courseId}/lessons/${lessons[i].id}/skills/${skills[i].id}`),
            { headers }
        );
        expect(mRes.status()).toBe(204);
    }

    // 5. Create 2 Questions per Skill (1 easy, 1 hard)
    const questions: { id: string; skillIndex: number; difficulty: 'easy' | 'hard'; prompt: string }[] = [];
    for (let i = 0; i < 5; i += 1) {
        for (const difficulty of ['easy', 'hard'] as const) {
            const prompt = `K${i + 1} ${skills[i].name} (${difficulty}) question`;
            const qRes = await request.post(api(`/api/courses/${courseId}/questions`), {
                headers,
                data: {
                    skillId: skills[i].id,
                    prompt,
                    kind: 'single_choice',
                    difficulty,
                    options: ['Correct answer', 'Wrong answer'],
                    correctOptions: [true, false],
                },
            });
            expect(qRes.status()).toBe(201);
            const qCreated = (await qRes.json()) as { id: string };
            questions.push({ id: qCreated.id, skillIndex: i, difficulty, prompt });
        }
    }

    // 6. Create & publish Placement Quiz (K1..K4, 8 questions)
    const placementQuestionIds = questions.filter((q) => q.skillIndex < 4).map((q) => q.id);
    const pQuizRes = await request.post(api(`/api/courses/${courseId}/quizzes`), {
        headers,
        data: {
            title: `E2E Placement ${suffix}`,
            kind: 'diagnostic',
            timeLimitSeconds: 600,
            questionIds: placementQuestionIds,
        },
    });
    expect(pQuizRes.status()).toBe(201);
    const { id: placementQuizId } = (await pQuizRes.json()) as { id: string };
    const pPubRes = await request.put(
        api(`/api/courses/${courseId}/quizzes/${placementQuizId}/publication`),
        { headers, data: { publicationStatus: 'published' } }
    );
    expect(pPubRes.status()).toBe(200);

    // 7. Create & publish easy and hard Practice Quizzes per Skill
    for (let i = 0; i < 5; i += 1) {
        for (const difficulty of ['easy', 'hard'] as const) {
            const qIds = questions
                .filter((q) => q.skillIndex === i && q.difficulty === difficulty)
                .map((q) => q.id);
            const prRes = await request.post(api(`/api/courses/${courseId}/quizzes`), {
                headers,
                data: {
                    title: `E2E practice K${i + 1} ${difficulty}`,
                    kind: 'practice',
                    timeLimitSeconds: 300,
                    questionIds: qIds,
                },
            });
            expect(prRes.status()).toBe(201);
            const { id: prId } = (await prRes.json()) as { id: string };
            const prPub = await request.put(
                api(`/api/courses/${courseId}/quizzes/${prId}/publication`),
                { headers, data: { publicationStatus: 'published' } }
            );
            expect(prPub.status()).toBe(200);
        }
    }

    // 8. Enable Adaptive Mode with explicit CT35 test policy
    const getSettings = await request.get(api(`/api/courses/${courseId}/adaptive-settings`), {
        headers,
    });
    expect(getSettings.status()).toBe(200);
    const etag = getSettings.headers()['etag'];
    expect(etag, 'adaptive-settings GET returns ETag').toBeTruthy();

    const putSettings = await request.put(api(`/api/courses/${courseId}/adaptive-settings`), {
        headers: { ...headers, 'If-Match': etag },
        data: {
            adaptiveEnabled: true,
            policy: {
                weak: { numerator: 1, denominator: 2 },
                mastered: { numerator: 4, denominator: 5 },
                minimumQuestionsPerSkill: 2,
            },
        },
    });
    expect(putSettings.status()).toBe(200);

    // 9. Publish Course
    const pubCourse = await request.put(
        api(`/api/instructor/courses/${courseId}/publication`),
        { headers, data: { publicationStatus: 'published' } }
    );
    expect(pubCourse.status()).toBe(200);

    return { courseId, courseTitle, lessons, skills, placementQuizId, questions };
}

async function enrollLearnerInCourse(page: Page, courseId: string) {
    await page.goto(`/courses/${encodeURIComponent(courseId)}`);
    await page.getByRole('button', { name: 'Enroll in course' }).click();
    await expect(page.getByText('Membership: Active')).toBeVisible();
    await page.goto(`/courses/${encodeURIComponent(courseId)}/learn`);
}

async function completePlacementThroughUi(
    page: Page,
    courseId: string,
    chooseCorrectForPrompt: (promptText: string) => boolean
) {
    await page.getByRole('button', { name: 'Start diagnostic' }).click();
    await expect(page).toHaveURL(new RegExp(`/courses/${courseId}/(diagnostic|quizzes/.+)`));
    await page.getByRole('button', { name: 'Start quiz' }).click();
    await expect(page).toHaveURL(new RegExp(`/courses/${courseId}/quizzes/.+/attempt`));
    for (let i = 0; i < 8; i += 1) {
        const legend = await page.locator('fieldset legend').innerText();
        const label = chooseCorrectForPrompt(legend) ? 'Correct answer' : 'Wrong answer';
        await page.getByRole('radio', { name: label }).click({ force: true });
        if (i < 7) {
            await page.getByRole('button', { name: 'Next', exact: true }).click();
        }
    }
    await page.getByRole('button', { name: 'Review and submit' }).click();
    await page.getByRole('dialog', { name: 'Submit quiz?' }).getByRole('button', { name: 'Submit quiz' }).click();
    await expect(page).toHaveURL(new RegExp(`/courses/${courseId}/attempts/`));
}

// Reusable helper to eliminate SonarCloud code duplication
async function setupLearnerSessionWithPlacement(
    browser: Browser,
    request: APIRequestContext,
    courseId: string,
    profileLabel: string,
    evaluator: (prompt: string) => boolean
): Promise<{ page: Page; context: BrowserContext }> {
    const account = newAccount(profileLabel);
    await registerThroughApi(request, account);
    const context = await browser.newContext({ baseURL: target.base }); const page = await context.newPage();
    await signIn(page, account);
    await enrollLearnerInCourse(page, courseId);
    await completePlacementThroughUi(page, courseId, evaluator);
    return { page, context };
}

test.describe('adaptive learning end-to-end journey (CT35)', () => {
    test('A-1 & A-2: contrasting learner profiles produce divergent Mastery Maps, test-outs, remedial reasons and non-blocking warnings', async ({
        browser,
        request,
    }) => {
        test.setTimeout(150_000);
        const instructor = target.instructor;
        test.skip(!instructor, 'Needs an Instructor account to author the adaptive E2E course.');
        const fx = await createAdaptiveCourseFixture(request, instructor!);
        // Profile A: Strong foundation
        const { page: pageA, context: ctxA } = await setupLearnerSessionWithPlacement(
            browser,
            request,
            fx.courseId,
            'StrongA',
            () => true
        );
        await pageA.getByRole('button', { name: 'Open Mastery Map' }).click();
        await expect(pageA).toHaveURL(new RegExp(`/courses/${fx.courseId}/mastery`));
        await expect(pageA.getByText('Mastery states reflect the latest submitted quiz evidence for each skill.')).toBeVisible();
        await pageA.getByRole('button', { name: 'List view' }).click();
        const tableA = pageA.getByRole('table', { name: `Skills in ${fx.courseTitle}` });
        for (const masteredSkill of ['Conditionals', 'While Loops', 'For Loops', 'Loop Control']) {
            await expect(tableA.getByRole('row', { name: new RegExp(masteredSkill) })).toContainText('Mastered');
        }
        await expect(tableA.getByRole('row', { name: /Lists/ })).toContainText('Unknown');
        await pageA.goto(`/courses/${encodeURIComponent(fx.courseId)}/learn`);
        await expect(pageA.getByText('Mastered · Counted as complete')).toHaveCount(4);
        await expect(pageA.getByText('Continue with the next lesson in the course order.')).toBeVisible();
        await expect(pageA.getByText('E5 Lists').first()).toBeVisible();
        await expectNoPageOverflow(pageA);
        await ctxA.close();
        // Profile B: Loops prerequisite gap
        const { page: pageB, context: ctxB } = await setupLearnerSessionWithPlacement(
            browser,
            request,
            fx.courseId,
            'GapB',
            (prompt) => prompt.includes('K1 ')
        );
        await pageB.getByRole('button', { name: 'Open Mastery Map' }).click();
        await expect(pageB).toHaveURL(new RegExp(`/courses/${fx.courseId}/mastery`));
        await pageB.getByRole('button', { name: 'List view' }).click();
        const tableB = pageB.getByRole('table', { name: `Skills in ${fx.courseTitle}` });
        await expect(tableB.getByRole('row', { name: /Conditionals/ })).toContainText('Mastered');
        for (const weakSkill of ['While Loops', 'For Loops', 'Loop Control']) {
            await expect(tableB.getByRole('row', { name: new RegExp(weakSkill) })).toContainText('Weak');
        }
        await expect(tableB.getByRole('row', { name: /Lists/ })).toContainText('Unknown');
        await pageB.goto(`/courses/${encodeURIComponent(fx.courseId)}/learn`);
        await expect(
            pageB.getByText(
                'Review this lesson first: your skill "Loop Control" is Weak and its prerequisite "While Loops" is not yet Mastered.'
            )
        ).toBeVisible();
        // A-2: Non-blocking prerequisite warning when opening E5 directly
        await pageB.getByText('E5 Lists').first().click();
        await expect(pageB).toHaveURL(new RegExp(`/courses/${fx.courseId}/lessons/${fx.lessons[4].id}`));
        await expect(pageB.getByText(/You haven't mastered For Loops yet\./)).toBeVisible();
        await expect(pageB.getByText('Content for E5 Lists.')).toBeVisible();
        await expect(pageB.getByRole('button', { name: 'Mark as complete' })).toBeEnabled();
        await expectNoPageOverflow(pageB);
        await ctxB.close();
    });

    test('A-3: skipping the placement diagnostic leaves all skills Unknown and starts at E1', async ({
        page,
        request,
    }) => {
        test.setTimeout(120_000);
        const instructor = target.instructor;
        test.skip(!instructor, 'Needs an Instructor account to author the adaptive E2E course.');
        const fx = await createAdaptiveCourseFixture(request, instructor!);
        const learnerC = newAccount('SkipC');
        await registerThroughApi(request, learnerC);
        await signIn(page, learnerC);
        await enrollLearnerInCourse(page, fx.courseId);
        await page.getByRole('button', { name: 'Skip and start from the beginning' }).click();
        await expect(page.getByText('E1 Conditionals').first()).toBeVisible();
        await page.getByRole('button', { name: 'Open Mastery Map' }).click();
        await expect(page).toHaveURL(new RegExp(`/courses/${fx.courseId}/mastery`));
        await page.getByRole('button', { name: 'List view' }).click();
        const table = page.getByRole('table', { name: `Skills in ${fx.courseTitle}` });
        for (const skill of fx.skills) {
            await expect(table.getByRole('row', { name: new RegExp(skill.name) })).toContainText('Unknown');
        }
    });

    test('A-4: recommended practice difficulty and fallback adapt to Mastered, Weak, Unknown and Learning states', async ({
        browser,
        request,
    }) => {
        const instructor = target.instructor;
        test.skip(!instructor, 'Needs an Instructor account to author the adaptive E2E course.');
        const fx = await createAdaptiveCourseFixture(request, instructor!);

        // 1. Learner A (K2 Mastered -> Hard practice on E2)
        const { page: pageA, context: ctxA } = await setupLearnerSessionWithPlacement(
            browser,
            request,
            fx.courseId,
            'PracticeA',
            () => true
        );
        await pageA.goto(`/courses/${encodeURIComponent(fx.courseId)}/lessons/${encodeURIComponent(fx.lessons[1].id)}`);
        await expect(pageA.getByText('E2E practice K2 hard')).toBeVisible();
        await expect(
            pageA.getByText('Recommended practice: Hard, because your skill "While Loops" is Mastered.')
        ).toBeVisible();
        await ctxA.close();

        // 2. Learner B (K2 Weak -> Easy practice on E2; K5 Unknown -> Easy practice on E5)
        const { page: pageB, context: ctxB } = await setupLearnerSessionWithPlacement(
            browser,
            request,
            fx.courseId,
            'PracticeB',
            (prompt) => prompt.includes('K1 ')
        );
        await pageB.goto(`/courses/${encodeURIComponent(fx.courseId)}/lessons/${encodeURIComponent(fx.lessons[1].id)}`);
        await expect(pageB.getByText('E2E practice K2 easy')).toBeVisible();
        await expect(
            pageB.getByText('Recommended practice: Easy, because your skill "While Loops" is Weak.')
        ).toBeVisible();

        await pageB.goto(`/courses/${encodeURIComponent(fx.courseId)}/lessons/${encodeURIComponent(fx.lessons[4].id)}`);
        await expect(pageB.getByText('E2E practice K5 easy')).toBeVisible();
        await expect(
            pageB.getByText('Recommended practice: Easy, because your skill "Lists" is not yet assessed.')
        ).toBeVisible();
        await ctxB.close();

        // 3. Learner D (1/2 on K1 -> Learning -> Medium target, falls back to Easy)
        const { page: pageD, context: ctxD } = await setupLearnerSessionWithPlacement(
            browser,
            request,
            fx.courseId,
            'FallbackD',
            (prompt) => prompt.includes('K1 ') && prompt.includes('(easy)')
        );
        await pageD.goto(`/courses/${encodeURIComponent(fx.courseId)}/lessons/${encodeURIComponent(fx.lessons[0].id)}`);
        await expect(pageD.getByText('E2E practice K1 easy')).toBeVisible();
        await expect(
            pageD.getByText(
                'Recommended practice: Easy, because your skill "Conditionals" is Learning and no Medium practice quiz is available for this lesson.'
            )
        ).toBeVisible();
        await expect(
            pageD.getByText('Mastery states reflect the latest submitted quiz evidence for each skill.')
        ).toBeVisible();
        const practiceLink = pageD.getByRole('link', { name: /E2E practice K1 easy|Practice|Start/i }).first();
        await expect(practiceLink).toHaveAttribute('href', new RegExp(`/courses/${fx.courseId}/quizzes/`));
        await expectNoPageOverflow(pageD);
        await ctxD.close();
    });
});