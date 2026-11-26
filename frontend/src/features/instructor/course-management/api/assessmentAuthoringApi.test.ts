// @vitest-environment jsdom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { bindAuthSession } from '../../../../shared/api/httpClient';
import {
    createAssignment, createQuestion, createQuiz, deleteUnusedHiddenQuiz, deleteUnusedQuestion, listOwnedAssignments, listOwnedQuizzes,
    listQuestions, questionShapeValid, readOwnedAssignment, readOwnedQuiz, readQuestion, replaceAssignment, replaceUnusedHiddenQuiz,
    replaceUnusedQuestion, setAssignmentPublication, setQuizPublication,
} from './assessmentAuthoringApi';
import {
    createCourseChapter, createCourseLesson, createOwnedCourse, getOwnedCourse, listOwnedCourses,
    orderChapterLessons, setCourseLessonPublication, setOwnedCoursePublication, updateOwnedCourse,
} from './instructorCourseApi';

const courseId = '00000000-0000-4000-8000-0000000000c1';
const skillId = '00000000-0000-4000-8000-0000000000a1';
const questionId = '00000000-0000-4000-8000-000000000001';
const quizId = '00000000-0000-4000-8000-0000000000f1';
const question = { id: questionId, skillId, prompt: 'What does\nprint(1) show?', kind: 'single_choice', difficulty: 'easy', options: ['1', '2'], correctOptions: [true, false] };
const write = { skillId, prompt: question.prompt, kind: 'single_choice' as const, difficulty: 'easy' as const, options: ['1', '2'], correctOptions: [true, false] };
const quiz = { id: quizId, courseId, title: 'Loops check', kind: 'practice', timeLimitSeconds: 95, published: false, questionCount: 1, questions: [{ position: 1, question }] };
const page = <T,>(content: T[], extra: Record<string, unknown> = {}) => ({ content, page: 0, size: 20, totalElements: content.length, totalPages: content.length ? 1 : 0, ...extra });
const json = (value: unknown, status = 200) => new Response(JSON.stringify(value), { status, headers: { 'Content-Type': 'application/json' } });
const problem = (status: number) => new Response(JSON.stringify({ type: 'about:blank', status, title: 'Conflict', detail: 'Server text', instance: '/x' }), { status, headers: { 'Content-Type': 'application/problem+json' } });
let fetcher: ReturnType<typeof vi.fn>, unbind: () => void;
beforeEach(() => {
    vi.stubEnv('VITE_API_BASE_URL', 'http://localhost:8080'); fetcher = vi.fn(); vi.stubGlobal('fetch', fetcher);
    unbind = bindAuthSession({ capture: () => ({ generation: 1, accessToken: 'synthetic-test-credential', expiresAt: Date.now() + 60000 }), isCurrent: (generation) => generation === 1, invalidate: vi.fn() });
});
afterEach(() => { unbind(); vi.unstubAllGlobals(); vi.unstubAllEnvs(); });
const call = (index = 0) => { const [url, init] = fetcher.mock.calls[index]; return { url: url as string, init: init as RequestInit & { headers: Headers } }; };

describe('CT23.questions bindings', () => {
    it('lists one owner page with the bearer token and the approved paging parameters', async () => {
        fetcher.mockResolvedValue(json(page([question], { page: 2, size: 1, totalElements: 3, totalPages: 3 })));
        expect((await listQuestions(courseId, 2, 1)).content).toEqual([question]);
        const { url, init } = call();
        expect(url).toBe(`http://localhost:8080/api/courses/${courseId}/questions?page=2&size=1`);
        expect(init.method).toBe('GET'); expect(init.headers.get('Authorization')).toBe('Bearer synthetic-test-credential'); expect(init.credentials).toBe('omit');
    });
    it.each([[-1, 20], [0, 0], [0, 101], [0.5, 20]])('rejects page %s / size %s without a request', async (index, size) => {
        await expect(listQuestions(courseId, index, size)).rejects.toMatchObject({ kind: 'validation' }); expect(fetcher).not.toHaveBeenCalled();
    });
    it('keeps the server order and an empty page past the end', async () => {
        const second = { ...question, id: '00000000-0000-4000-8000-000000000002' };
        fetcher.mockResolvedValueOnce(json(page([second, question], { totalElements: 2 })));
        expect((await listQuestions(courseId)).content.map((row) => row.id)).toEqual([second.id, question.id]);
        fetcher.mockResolvedValueOnce(json(page([], { page: 4, totalElements: 2, totalPages: 1 })));
        expect((await listQuestions(courseId, 4)).content).toEqual([]);
    });
    it.each([
        ['an echoed page that differs', 0, page([question], { page: 1 })],
        ['an echoed size that differs', 0, page([question], { size: 10 })],
        ['an incoherent page count', 0, page([question], { totalPages: 2 })],
        ['rows past the last page', 3, page([question], { page: 3, totalElements: 1, totalPages: 1 })],
        ['an unknown member', 0, { ...page([question]), sort: 'id' }],
    ])('rejects %s', async (_, index, value) => {
        fetcher.mockResolvedValue(json(value));
        await expect(listQuestions(courseId, index)).rejects.toMatchObject({ kind: 'protocol' });
    });
    it.each([
        ['a blank prompt', { ...question, prompt: '  ' }],
        ['a non-UUID skill', { ...question, skillId: 'loops' }],
        ['an unknown kind', { ...question, kind: 'code' }],
        ['an unknown difficulty', { ...question, difficulty: 'expert' }],
        ['a mask of another length', { ...question, correctOptions: [true] }],
        ['two keys for single choice', { ...question, correctOptions: [true, true] }],
        ['no key for multiple choice', { ...question, kind: 'multiple_choice', correctOptions: [false, false] }],
        ['true/false with other labels', { ...question, kind: 'true_false', options: ['Yes', 'No'] }],
        ['an extra usage member', { ...question, usedInQuizzes: 2 }],
    ])('rejects a question with %s', async (_, value) => {
        fetcher.mockResolvedValue(json(value)); await expect(readQuestion(courseId, questionId)).rejects.toMatchObject({ kind: 'protocol' });
    });
    it('binds read, replace and delete results to the requested question', async () => {
        fetcher.mockResolvedValue(json({ ...question, id: '00000000-0000-4000-8000-000000000009' }));
        await expect(readQuestion(courseId, questionId)).rejects.toMatchObject({ kind: 'protocol' });
        await expect(replaceUnusedQuestion(courseId, questionId, write)).rejects.toMatchObject({ kind: 'protocol' });
        await expect(deleteUnusedQuestion(courseId, questionId)).rejects.toMatchObject({ kind: 'protocol' });
    });
    it('sends exact write bodies and methods', async () => {
        fetcher.mockResolvedValueOnce(json(question, 201)); expect(await createQuestion(courseId, write)).toEqual(question);
        expect(call(0).init.method).toBe('POST'); expect(JSON.parse(call(0).init.body as string)).toEqual(write);
        fetcher.mockResolvedValueOnce(json(question)); await replaceUnusedQuestion(courseId, questionId, write);
        expect(call(1).url).toBe(`http://localhost:8080/api/courses/${courseId}/questions/${questionId}`); expect(call(1).init.method).toBe('PUT');
        fetcher.mockResolvedValueOnce(json(question)); await deleteUnusedQuestion(courseId, questionId);
        expect(call(2).init.method).toBe('DELETE'); expect(call(2).init.body).toBeUndefined();
    });
    it('requires 201 for creation and surfaces 409 without server text', async () => {
        fetcher.mockResolvedValueOnce(json(question, 200)); await expect(createQuestion(courseId, write)).rejects.toMatchObject({ kind: 'protocol' });
        fetcher.mockResolvedValueOnce(problem(409));
        const error = await replaceUnusedQuestion(courseId, questionId, write).catch((value: unknown) => value);
        expect(error).toMatchObject({ kind: 'http', status: 409, message: 'The request conflicts with existing information.' });
        expect(JSON.stringify(error)).not.toContain('Server text');
    });
    it('applies the V004 kind and mask rules', () => {
        expect(questionShapeValid('true_false', ['True', 'False'], [false, true])).toBe(true);
        expect(questionShapeValid('true_false', ['False', 'True'], [false, true])).toBe(false);
        expect(questionShapeValid('multiple_choice', ['a', 'b', 'c'], [true, true, false])).toBe(true);
        expect(questionShapeValid('single_choice', ['a', ' '], [true, false])).toBe(false);
        expect(questionShapeValid('single_choice', ['a'], [true])).toBe(false);
    });
});

describe('CT23.composition and CT23.publication bindings', () => {
    it('lists owned quizzes of the requested course only', async () => {
        fetcher.mockResolvedValueOnce(json(page([quiz]))); expect((await listOwnedQuizzes(courseId)).content[0]).toEqual(quiz);
        expect(call(0).url).toBe(`http://localhost:8080/api/courses/${courseId}/owned-quizzes?page=0&size=20`);
        fetcher.mockResolvedValueOnce(json(page([{ ...quiz, courseId: skillId }]))); await expect(listOwnedQuizzes(courseId)).rejects.toMatchObject({ kind: 'protocol' });
    });
    it('keeps the exact time limit in seconds and the returned membership order', async () => {
        const second = { ...question, id: '00000000-0000-4000-8000-000000000002', difficulty: 'hard' };
        const ordered = { ...quiz, timeLimitSeconds: 2147483647, questionCount: 2, questions: [{ position: 1, question: second }, { position: 2, question }] };
        fetcher.mockResolvedValue(json(ordered));
        const result = await readOwnedQuiz(courseId, quizId);
        expect(result.timeLimitSeconds).toBe(2147483647); expect(result.questions.map((item) => item.question.id)).toEqual([second.id, question.id]);
    });
    it.each([
        ['a question count that differs from the membership', { ...quiz, questionCount: 2 }],
        ['positions out of order', { ...quiz, questionCount: 2, questions: [{ position: 2, question }, { position: 1, question: { ...question, id: '00000000-0000-4000-8000-000000000002' } }] }],
        ['a repeated question', { ...quiz, questionCount: 2, questions: [{ position: 1, question }, { position: 2, question }] }],
        ['a zero time limit', { ...quiz, timeLimitSeconds: 0 }],
        ['a fractional time limit', { ...quiz, timeLimitSeconds: 1.5 }],
        ['another quiz', { ...quiz, id: '00000000-0000-4000-8000-0000000000f2' }],
        ['another course', { ...quiz, courseId: skillId }],
        ['a string publication flag', { ...quiz, published: 'false' }],
    ])('rejects %s', async (_, value) => {
        fetcher.mockResolvedValue(json(value)); await expect(readOwnedQuiz(courseId, quizId)).rejects.toMatchObject({ kind: 'protocol' });
    });
    it('creates, replaces and deletes with exact bodies', async () => {
        const body = { title: 'Loops check', kind: 'practice' as const, timeLimitSeconds: 95, questionIds: [questionId] };
        fetcher.mockResolvedValueOnce(json(quiz, 201)); expect(await createQuiz(courseId, body)).toEqual(quiz);
        expect(call(0).url).toBe(`http://localhost:8080/api/courses/${courseId}/quizzes`); expect(JSON.parse(call(0).init.body as string)).toEqual(body);
        fetcher.mockResolvedValueOnce(json(quiz)); await replaceUnusedHiddenQuiz(courseId, quizId, body); expect(call(1).init.method).toBe('PUT');
        fetcher.mockResolvedValueOnce(json(quiz)); await deleteUnusedHiddenQuiz(courseId, quizId); expect(call(2).init.method).toBe('DELETE');
    });
    it('sends the explicit publication command and requires the returned state to match it', async () => {
        fetcher.mockResolvedValueOnce(json({ ...quiz, published: true })); expect((await setQuizPublication(courseId, quizId, true)).published).toBe(true);
        expect(call(0).url).toBe(`http://localhost:8080/api/courses/${courseId}/quizzes/${quizId}/publication`);
        expect(JSON.parse(call(0).init.body as string)).toEqual({ publicationStatus: 'published' });
        fetcher.mockResolvedValueOnce(json(quiz)); await expect(setQuizPublication(courseId, quizId, true)).rejects.toMatchObject({ kind: 'protocol' });
        fetcher.mockResolvedValueOnce(json(quiz)); await setQuizPublication(courseId, quizId, false);
        expect(JSON.parse(call(2).init.body as string)).toEqual({ publicationStatus: 'hidden' });
    });
});

describe('CT10.rest owner assignment bindings', () => {
    const assignmentId = '00000000-0000-4000-8000-0000000000d1';
    const lessonId = '00000000-0000-4000-8000-0000000000b1';
    const assignment = {
        id: assignmentId,
        courseId,
        lessonId,
        title: 'Project 1',
        instructions: '  # Submit PDF or ZIP\n',
        deadlineAt: '2026-12-01T12:00:00.123456Z',
        publicationStatus: 'hidden' as const,
        submissionCount: 3,
        gradedCount: 1,
    };
    const write = { title: 'Project 1', instructions: assignment.instructions, deadlineAt: assignment.deadlineAt, lessonId };

    it('lists, creates, reads, replaces and updates publication with strict decoding', async () => {
        fetcher.mockResolvedValueOnce(json(page([assignment])));
        expect((await listOwnedAssignments(courseId)).content).toEqual([assignment]);
        expect(call(0).url).toBe(`http://localhost:8080/api/courses/${courseId}/owned-assignments?page=0&size=20`);

        fetcher.mockResolvedValueOnce(json(assignment, 201));
        expect(await createAssignment(courseId, write)).toEqual(assignment);
        expect(call(1).url).toBe(`http://localhost:8080/api/courses/${courseId}/assignments`);

        fetcher.mockResolvedValueOnce(json(assignment));
        expect(await readOwnedAssignment(courseId, assignmentId)).toEqual(assignment);

        fetcher.mockResolvedValueOnce(json(assignment));
        expect(await replaceAssignment(courseId, assignmentId, write)).toEqual(assignment);
        expect(call(3).init.method).toBe('PUT');

        fetcher.mockResolvedValueOnce(json({ ...assignment, publicationStatus: 'published' }));
        expect((await setAssignmentPublication(courseId, assignmentId, 'published')).publicationStatus).toBe('published');
    });

    it.each([
        ['gradedCount exceeding submissionCount', { ...assignment, submissionCount: 1, gradedCount: 2 }],
        ['sub-microsecond deadlineAt', { ...assignment, deadlineAt: '2026-12-01T12:00:00.1234567Z' }],
        ['another course', { ...assignment, courseId: skillId }],
        ['extra property', { ...assignment, open: true }],
    ])('rejects %s', async (_, bad) => {
        fetcher.mockResolvedValue(json(bad));
        await expect(readOwnedAssignment(courseId, assignmentId)).rejects.toMatchObject({ kind: 'protocol' });
    });
});

describe('CT21.authoring owned course and structure bindings', () => {
    const chapterId = '00000000-0000-4000-8000-0000000000e1';
    const lessonId = '00000000-0000-4000-8000-0000000000b1';
    const ownedCourse = {
        id: courseId,
        title: 'Python Foundations',
        description: 'Variables, loops and functions.',
        publicationStatus: 'hidden' as const,
        prerequisiteDescription: null,
        chapters: [{
            id: chapterId,
            title: 'Getting Started',
            position: 1,
            lessons: [{
                id: lessonId,
                chapterId,
                title: 'Hello Python',
                contentMarkdown: '# Welcome',
                position: 1,
                publicationStatus: 'hidden' as const,
                materials: [],
            }],
        }],
    };

    it('decodes owned course summaries, full tree, chapters and lessons strictly', async () => {
        fetcher.mockResolvedValueOnce(json(page([{ id: courseId, title: ownedCourse.title, description: ownedCourse.description, publicationStatus: 'hidden' }])));
        expect((await listOwnedCourses()).content).toHaveLength(1);
        expect(call(0).url).toBe('http://localhost:8080/api/instructor/courses?page=0&size=20');

        fetcher.mockResolvedValueOnce(json(ownedCourse, 201));
        expect((await createOwnedCourse({ title: ownedCourse.title, description: ownedCourse.description, prerequisiteDescription: null })).id).toBe(courseId);

        fetcher.mockResolvedValueOnce(json(ownedCourse));
        expect((await getOwnedCourse(courseId)).chapters[0].lessons[0].title).toBe('Hello Python');

        fetcher.mockResolvedValueOnce(json({ ...ownedCourse, title: 'Updated' }));
        expect((await updateOwnedCourse(courseId, { title: 'Updated', description: ownedCourse.description, prerequisiteDescription: null })).title).toBe('Updated');

        fetcher.mockResolvedValueOnce(json({ ...ownedCourse, publicationStatus: 'published' }));
        expect((await setOwnedCoursePublication(courseId, 'published')).publicationStatus).toBe('published');

        fetcher.mockResolvedValueOnce(json(ownedCourse.chapters[0], 201));
        expect((await createCourseChapter(courseId, 'Getting Started')).id).toBe(chapterId);

        fetcher.mockResolvedValueOnce(json(ownedCourse.chapters[0].lessons[0], 201));
        expect((await createCourseLesson(courseId, chapterId, { title: 'Hello Python', contentMarkdown: '# Welcome' })).id).toBe(lessonId);

        fetcher.mockResolvedValueOnce(json({ orderedIds: [lessonId] }));
        expect(await orderChapterLessons(courseId, chapterId, [lessonId])).toEqual([lessonId]);

        fetcher.mockResolvedValueOnce(json({ ...ownedCourse.chapters[0].lessons[0], publicationStatus: 'published' }));
        expect((await setCourseLessonPublication(courseId, lessonId, 'published')).publicationStatus).toBe('published');
    });
});
