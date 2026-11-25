// @vitest-environment jsdom
import { act, StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import type { Root } from 'react-dom/client';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { AuthContext } from '../../../../app/providers/AuthProvider';
import type { AuthContextValue } from '../../../../app/providers/AuthProvider';
import { ApiError } from '../../../../shared/types/apiTypes';
import { createQuiz, listOwnedQuizzes, listQuestions, replaceUnusedQuestion } from '../api/assessmentAuthoringApi';
import type { B3QuestionPage, B3QuizOwned } from '../api/assessmentAuthoringApi';
import { failureStatus, isCancelled, outcomeUnknown, useOwnedQuizzes, useQuizEditor } from './useAssessmentEditor';
import type { OwnerRead, QuizEditorOperations } from './useAssessmentEditor';
import { useQuestionBank, useQuestionEditor } from './useQuestionBank';
import { useSharedCourseState } from './useCourseEditor';
import { getOwnedCourse } from '../api/instructorCourseApi';
import type { OwnedCourse } from '../api/instructorCourseApi';
import type { QuestionEditorOperations } from './useQuestionBank';
import { getOwnedSkillGraph } from '../../skill-graph-editor/api/skillGraphApi';
import { useOwnedSkillGraph } from '../../skill-graph-editor/hooks/useSkillGraph';
import type { OwnedSkillGraphRead } from '../../skill-graph-editor/hooks/useSkillGraph';

vi.mock('../api/assessmentAuthoringApi', async (original) => ({ ...await original<object>(), listQuestions: vi.fn(), listOwnedQuizzes: vi.fn(), replaceUnusedQuestion: vi.fn(), createQuiz: vi.fn() }));
vi.mock('../../skill-graph-editor/api/skillGraphApi', () => ({ getOwnedSkillGraph: vi.fn() }));
vi.mock('../api/instructorCourseApi', async (original) => ({ ...await original<object>(), getOwnedCourse: vi.fn() }));
Object.assign(globalThis, { IS_REACT_ACT_ENVIRONMENT: true });

const courseId = '00000000-0000-4000-8000-0000000000c1';
const userId = '00000000-0000-4000-8000-0000000000e1';
const questionPage = (marker: string): B3QuestionPage => ({ content: [{ id: '00000000-0000-4000-8000-000000000001', skillId: userId, prompt: marker, kind: 'single_choice', difficulty: 'easy', options: ['a', 'b'], correctOptions: [true, false] }], page: 0, size: 20, totalElements: 1, totalPages: 1 });
let host: HTMLDivElement, root: Root, auth: AuthContextValue, generation = 1;
beforeEach(() => {
    generation = 1;
    host = document.createElement('div'); document.body.append(host); root = createRoot(host);
    auth = { user: { id: userId, displayName: 'Instructor', role: 'Instructor' }, get generation() { return generation; }, isAuthenticated: true, restoring: false,
        isCurrent: (value) => value === generation, login: vi.fn(), register: vi.fn(), logout: vi.fn(), rememberProfile: vi.fn(), startPath: '/courses', consumeReturn: () => '/courses' };
});
afterEach(async () => { await act(async () => root.unmount()); host.remove(); vi.resetAllMocks(); });
const deferred = <T,>() => { let resolve!: (value: T) => void, reject!: (error: unknown) => void; const promise = new Promise<T>((done, fail) => { resolve = done; reject = fail; }); return { promise, resolve, reject }; };

function Probe<T>({ use, out }: { use: () => T; out: { current?: T } }) { out.current = use(); return null; }
async function render<T>(use: () => T, strict = false) {
    const out: { current?: T } = {};
    const tree = <AuthContext.Provider value={auth}><Probe use={use} out={out} /></AuthContext.Provider>;
    await act(async () => root.render(strict ? <StrictMode>{tree}</StrictMode> : tree));
    return { out, rerender: async (next: () => T) => act(async () => root.render(<AuthContext.Provider value={auth}><Probe use={next} out={out} /></AuthContext.Provider>)) };
}

describe('owner list reads', () => {
    it('reads one page with cancellation and session checks, and discards a superseded page', async () => {
        const first = deferred<B3QuestionPage>();
        vi.mocked(listQuestions).mockReturnValueOnce(first.promise).mockResolvedValueOnce(questionPage('page two'));
        const { out, rerender } = await render<OwnerRead<B3QuestionPage>>(() => useQuestionBank(courseId, 0));
        expect(out.current!.loading).toBe(true);
        const options = vi.mocked(listQuestions).mock.calls[0][3]!;
        await rerender(() => useQuestionBank(courseId, 1));
        expect(options.signal?.aborted).toBe(true); expect(options.isCurrent?.()).toBe(false);
        await act(async () => first.resolve(questionPage('stale page one')));
        expect(out.current!.data?.content[0].prompt).toBe('page two');
        expect(vi.mocked(listQuestions).mock.calls[1].slice(0, 3)).toEqual([courseId, 1, 20]);
    });
    it('never shows a body after the session generation changes', async () => {
        const pending = deferred<B3QuestionPage>();
        vi.mocked(listQuestions).mockReturnValueOnce(pending.promise);
        const { out } = await render<OwnerRead<B3QuestionPage>>(() => useQuestionBank(courseId, 0));
        generation = 2;
        await act(async () => pending.resolve(questionPage('other session')));
        expect(out.current!.data).toBeNull();
    });
    it('reports a failure, then reloads on request without a mutation', async () => {
        vi.mocked(listOwnedQuizzes).mockRejectedValueOnce(new ApiError('network', 'Unable to connect. Please try again.'))
            .mockResolvedValueOnce({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 });
        const { out } = await render(() => useOwnedQuizzes(courseId, 0));
        expect(outcomeUnknown(out.current!.error)).toBe(true); expect(out.current!.loading).toBe(false);
        await act(async () => out.current!.reload());
        expect(out.current!.error).toBeNull(); expect(out.current!.data?.totalElements).toBe(0);
        expect(listOwnedQuizzes).toHaveBeenCalledTimes(2);
    });
    it('does not read without a session', async () => {
        auth = { ...auth, isAuthenticated: false, user: null };
        const { out } = await render(() => useQuestionBank(courseId, 0));
        expect(listQuestions).not.toHaveBeenCalled(); expect(out.current!.loading).toBe(false);
    });
    it('settles under Strict Mode double effects with one visible result', async () => {
        vi.mocked(listQuestions).mockResolvedValue(questionPage('strict'));
        const { out } = await render(() => useQuestionBank(courseId, 0), true);
        expect(out.current!.data?.content[0].prompt).toBe('strict');
        expect(vi.mocked(listQuestions).mock.calls.filter(([, , , options]) => !options?.signal?.aborted)).toHaveLength(1);
    });
});

describe('editor operations', () => {
    it('runs one operation at a time and resolves only current results', async () => {
        const pending = deferred<never>();
        vi.mocked(replaceUnusedQuestion).mockReturnValueOnce(pending.promise);
        const { out } = await render<QuestionEditorOperations>(() => useQuestionEditor(courseId));
        let first!: Promise<unknown>, second!: Promise<unknown>;
        await act(async () => { first = out.current!.replace('q', { skillId: userId, prompt: 'p', kind: 'single_choice', difficulty: 'easy', options: ['a', 'b'], correctOptions: [true, false] }).catch((value: unknown) => value); });
        expect(out.current!.busy).toBe(true);
        await act(async () => { second = out.current!.remove('q').catch((value: unknown) => value); });
        expect(isCancelled(await second)).toBe(true);
        await act(async () => pending.reject(new ApiError('http', 'The request conflicts with existing information.', 409)));
        const error = await first;
        expect(failureStatus(error)).toBe(409); expect(out.current!.busy).toBe(false);
    });
    it('discards a late result after unmount and aborts the request', async () => {
        const pending = deferred<B3QuizOwned>();
        vi.mocked(createQuiz).mockReturnValueOnce(pending.promise);
        const { out } = await render<QuizEditorOperations>(() => useQuizEditor(courseId));
        let result!: Promise<unknown>;
        await act(async () => { result = out.current!.create({ title: 'T', kind: 'test', timeLimitSeconds: 60, questionIds: [] }).catch((value: unknown) => value); });
        const options = vi.mocked(createQuiz).mock.calls[0][2]!;
        await act(async () => root.unmount()); root = createRoot(host);
        expect(options.signal?.aborted).toBe(true); expect(options.isCurrent?.()).toBe(false);
        await act(async () => pending.resolve({ id: courseId, courseId, title: 'T', kind: 'test', timeLimitSeconds: 60, published: false, questionCount: 0, questions: [] }));
        expect(isCancelled(await result)).toBe(true);
    });
    it('classifies unknown outcomes', () => {
        expect(outcomeUnknown(new ApiError('http', 'x', 500))).toBe(true);
        expect(outcomeUnknown(new ApiError('protocol', 'x'))).toBe(true);
        expect(outcomeUnknown(new ApiError('http', 'x', 409))).toBe(false);
        expect(outcomeUnknown(new ApiError('cancelled', 'x'))).toBe(false);
    });
});

describe('owned skill graph read', () => {
    it('reads once per course and session and discards the graph of another course', async () => {
        const graph = { courseId, skills: [{ id: userId, name: 'Loops', description: null }], prerequisites: [], mappings: [] };
        const other = deferred<typeof graph>();
        vi.mocked(getOwnedSkillGraph).mockResolvedValueOnce(graph).mockReturnValueOnce(other.promise);
        const { out, rerender } = await render<OwnedSkillGraphRead>(() => useOwnedSkillGraph(courseId));
        expect(out.current!.graph?.skills[0].name).toBe('Loops');
        await rerender(() => useOwnedSkillGraph(userId));
        expect(out.current!.graph).toBeNull(); expect(out.current!.loading).toBe(true);
        await act(async () => other.reject(new ApiError('http', 'The requested resource is unavailable.', 404)));
        expect(failureStatus(out.current!.error)).toBe(404);
        expect(getOwnedSkillGraph).toHaveBeenCalledTimes(2);
    });
});

describe('U2: a failed re-read keeps the last body only for an unknown outcome of the same key and session', () => {
    it('keeps the body with the error after a 500 or network re-read, retries nothing, and recovers on reload', async () => {
        vi.mocked(listQuestions).mockResolvedValueOnce(questionPage('first'))
            .mockRejectedValueOnce(new ApiError('http', 'x', 500))
            .mockRejectedValueOnce(new ApiError('network', 'down'))
            .mockResolvedValueOnce(questionPage('fresh'));
        const { out } = await render<OwnerRead<B3QuestionPage>>(() => useQuestionBank(courseId, 0));
        expect(out.current!.data?.content[0].prompt).toBe('first');
        await act(async () => out.current!.reload());
        expect(failureStatus(out.current!.error)).toBe(500);
        expect(out.current!.data?.content[0].prompt).toBe('first');
        expect(out.current!.loading).toBe(false);
        await act(async () => out.current!.reload());
        expect(outcomeUnknown(out.current!.error)).toBe(true);
        expect(out.current!.data?.content[0].prompt).toBe('first');
        expect(listQuestions).toHaveBeenCalledTimes(3);
        await act(async () => out.current!.reload());
        expect(out.current!.error).toBeNull();
        expect(out.current!.data?.content[0].prompt).toBe('fresh');
    });

    it.each([401, 403, 404, 409])('discards the body when the re-read is refused with %i', async (status) => {
        vi.mocked(listQuestions).mockResolvedValueOnce(questionPage('first')).mockRejectedValueOnce(new ApiError('http', 'refused', status));
        const { out } = await render<OwnerRead<B3QuestionPage>>(() => useQuestionBank(courseId, 0));
        await act(async () => out.current!.reload());
        expect(failureStatus(out.current!.error)).toBe(status);
        expect(out.current!.data).toBeNull();
    });

    it('never carries a kept body into another key or session', async () => {
        vi.mocked(listQuestions).mockResolvedValueOnce(questionPage('page zero'))
            .mockRejectedValueOnce(new ApiError('http', 'x', 503))
            .mockRejectedValueOnce(new ApiError('http', 'x', 503))
            .mockReturnValueOnce(deferred<B3QuestionPage>().promise);
        const { out, rerender } = await render<OwnerRead<B3QuestionPage>>(() => useQuestionBank(courseId, 0));
        await act(async () => out.current!.reload());
        expect(out.current!.data?.content[0].prompt).toBe('page zero');
        await rerender(() => useQuestionBank(courseId, 1));
        expect(out.current!.data).toBeNull();
        expect(failureStatus(out.current!.error)).toBe(503);
        expect(out.current!.data).toBeNull();
        generation = 2;
        await rerender(() => useQuestionBank(courseId, 0));
        expect(out.current!.data).toBeNull();
        expect(out.current!.loading).toBe(true);
    });

    it('applies the same rule to the owned skill graph', async () => {
        const graph = { courseId, skills: [{ id: userId, name: 'Loops', description: null }], prerequisites: [], mappings: [] };
        vi.mocked(getOwnedSkillGraph).mockResolvedValueOnce(graph)
            .mockRejectedValueOnce(new ApiError('protocol', 'bad body'))
            .mockRejectedValueOnce(new ApiError('http', 'gone', 404));
        const { out } = await render<OwnedSkillGraphRead>(() => useOwnedSkillGraph(courseId));
        await act(async () => out.current!.reload());
        expect(outcomeUnknown(out.current!.error)).toBe(true);
        expect(out.current!.graph?.skills[0].name).toBe('Loops');
        await act(async () => out.current!.reload());
        expect(failureStatus(out.current!.error)).toBe(404);
        expect(out.current!.graph).toBeNull();
        expect(getOwnedSkillGraph).toHaveBeenCalledTimes(3);
    });
});


describe('D1-R1: shared course read evidence', () => {
    const body = (): OwnedCourse => ({ id: courseId, title: 'Course', description: 'Description', prerequisiteDescription: null, publicationStatus: 'hidden', chapters: [] });
    beforeEach(() => vi.mocked(getOwnedSkillGraph).mockResolvedValue({ courseId, skills: [], prerequisites: [], mappings: [] }));

    it('advances evidence only on accepted success, including a same-object response', async () => {
        const initial = body(), read = deferred<OwnedCourse>(), retry = deferred<OwnedCourse>();
        vi.mocked(getOwnedCourse).mockResolvedValueOnce(initial).mockReturnValueOnce(read.promise).mockReturnValueOnce(retry.promise);
        const { out } = await render(() => useSharedCourseState(courseId));
        const first = out.current!.course.successfulRead;
        let required = 0;
        await act(async () => { required = out.current!.course.reload(); });
        expect(required).toBeGreaterThan(first);
        expect(out.current!.course.data).toBe(initial);
        expect(out.current!.course.successfulRead).toBe(first);
        await act(async () => read.reject(new ApiError('protocol', 'Invalid response')));
        expect(out.current!.course.data).toBe(initial);
        expect(out.current!.course.successfulRead).toBe(first);
        expect(outcomeUnknown(out.current!.course.error)).toBe(true);
        await act(async () => { required = out.current!.course.reload(); });
        expect(out.current!.course.error).toBeNull();
        expect(out.current!.course.successfulRead).toBe(first);
        await act(async () => retry.resolve(initial));
        expect(out.current!.course.data).toBe(initial);
        expect(out.current!.course.successfulRead).toBeGreaterThanOrEqual(required);
        expect(getOwnedCourse).toHaveBeenCalledTimes(3);
    });

    it('does not count a superseded success or duplicate GETs for coalesced reloads', async () => {
        const old = deferred<OwnedCourse>(), current = deferred<OwnedCourse>();
        vi.mocked(getOwnedCourse).mockReturnValueOnce(old.promise).mockReturnValueOnce(current.promise);
        const { out } = await render(() => useSharedCourseState(courseId));
        let required = 0;
        await act(async () => { out.current!.course.reload(); required = out.current!.course.reload(); });
        expect(getOwnedCourse).toHaveBeenCalledTimes(2);
        expect(vi.mocked(getOwnedCourse).mock.calls[0][1]?.signal?.aborted).toBe(true);
        await act(async () => old.resolve(body()));
        expect(out.current!.course.data).toBeNull();
        expect(out.current!.course.successfulRead).toBe(0);
        await act(async () => current.resolve(body()));
        expect(out.current!.course.successfulRead).toBeGreaterThanOrEqual(required);
    });

    it('isolates success evidence and retained data across course and session changes', async () => {
        const pending = deferred<OwnedCourse>();
        vi.mocked(getOwnedCourse).mockResolvedValueOnce(body()).mockReturnValueOnce(pending.promise)
            .mockResolvedValueOnce({ ...body(), id: userId }).mockRejectedValueOnce(new ApiError('network', 'Offline'));
        const { out, rerender } = await render(() => useSharedCourseState(courseId));
        await act(async () => { out.current!.course.reload(); });
        await rerender(() => useSharedCourseState(userId));
        expect(out.current!.course.data?.id).toBe(userId);
        const current = out.current!.course.successfulRead;
        await act(async () => pending.resolve(body()));
        expect(out.current!.course.successfulRead).toBe(current);
        generation = 2;
        await rerender(() => useSharedCourseState(userId));
        expect(out.current!.course.data).toBeNull();
        expect(out.current!.course.successfulRead).toBe(0);
    });

    it('retains initiation evidence under Strict Mode and aborts a read on unmount', async () => {
        const pending = deferred<OwnedCourse>();
        vi.mocked(getOwnedCourse).mockResolvedValue(body());
        const { out } = await render(() => useSharedCourseState(courseId), true);
        const previous = out.current!.course.successfulRead;
        vi.mocked(getOwnedCourse).mockReturnValueOnce(pending.promise);
        let required = 0;
        await act(async () => { required = out.current!.course.reload(); });
        expect(required).toBeGreaterThan(previous);
        const options = vi.mocked(getOwnedCourse).mock.calls.at(-1)![1]!;
        await act(async () => root.unmount());
        expect(options.signal?.aborted).toBe(true);
        expect(options.isCurrent?.()).toBe(false);
        await act(async () => pending.resolve(body()));
        expect(out.current!.course.successfulRead).toBe(previous);
        root = createRoot(host);
    });
});

describe('D1-R2: provider-owned Settings acknowledgment', () => {
    const body = (title = 'Read X'): OwnedCourse => ({ id: courseId, title, description: `Description ${title}`, prerequisiteDescription: `Prerequisites ${title}`, publicationStatus: 'hidden', chapters: [] });
    beforeEach(() => vi.mocked(getOwnedSkillGraph).mockResolvedValue({ courseId, skills: [], prerequisites: [], mappings: [] }));

    it('keeps PUT provenance separate and completes reconciliation without a mounted Settings form', async () => {
        const refresh = deferred<OwnedCourse>();
        const initial = body(), saved = body('Acknowledged A');
        vi.mocked(getOwnedCourse).mockResolvedValueOnce(initial).mockReturnValueOnce(refresh.promise);
        const { out } = await render(() => useSharedCourseState(courseId));
        const read = out.current!.course.successfulRead;
        await act(async () => out.current!.acknowledgeSettings(saved));
        expect(out.current!.course.data).toBe(initial);
        expect(out.current!.course.successfulRead).toBe(read);
        expect(out.current!.settingsAcknowledgment?.metadata).toEqual({ title: saved.title, description: saved.description, prerequisiteDescription: saved.prerequisiteDescription });
        const bound = out.current!.settingsAcknowledgment!.reconcileAfter;
        expect(bound).toBeGreaterThan(read);
        await act(async () => refresh.resolve(initial));
        expect(out.current!.course.successfulRead).toBeGreaterThanOrEqual(bound);
        expect(out.current!.settingsAcknowledgment).toBeNull();
        expect(out.current!.course.data).toBe(initial);
        expect(getOwnedCourse).toHaveBeenCalledTimes(2);
    });

    it.each([403, 404].flatMap(status => ['pending', 'failed'].map(state => ({ status, state }))))('$status after $state reconciliation erases acknowledgment through recovery in the same provider', async ({ status, state }) => {
        const refresh = deferred<OwnedCourse>();
        vi.mocked(getOwnedCourse).mockResolvedValueOnce(body()).mockReturnValueOnce(refresh.promise);
        const { out } = await render(() => useSharedCourseState(courseId));
        await act(async () => out.current!.acknowledgeSettings(body('Acknowledged A')));
        if (state === 'failed') await act(async () => refresh.reject(new ApiError('http', 'Unavailable', 503)));
        expect(out.current!.settingsAcknowledgment).not.toBeNull();
        vi.mocked(getOwnedCourse).mockRejectedValueOnce(new ApiError('http', 'Refused', status));
        await act(async () => { out.current!.course.reload(); });
        expect(out.current!.course.data).toBeNull(); expect(out.current!.settingsAcknowledgment).toBeNull();
        // Even a failed recovery, then a successful same-resource recovery, must not resurrect A.
        vi.mocked(getOwnedCourse).mockRejectedValueOnce(new ApiError('network', 'Offline'));
        await act(async () => { out.current!.course.reload(); });
        expect(out.current!.settingsAcknowledgment).toBeNull();
        vi.mocked(getOwnedCourse).mockResolvedValueOnce(body('Recovered B'));
        await act(async () => { out.current!.course.reload(); });
        if (state === 'pending') await act(async () => refresh.resolve(body('Late A')));
        expect(out.current!.course.data?.title).toBe('Recovered B');
        expect(out.current!.settingsAcknowledgment).toBeNull();
    });

    it.each(['course', 'session', 'logout'].flatMap(boundary => ['pending', 'failed'].map(state => ({ boundary, state }))))('$boundary replacement discards $state acknowledgment even when the hook owner survives', async ({ boundary, state }) => {
        const refresh = deferred<OwnedCourse>();
        vi.mocked(getOwnedCourse).mockResolvedValueOnce(body()).mockReturnValueOnce(refresh.promise);
        const { out, rerender } = await render(() => useSharedCourseState(courseId));
        await act(async () => out.current!.acknowledgeSettings(body('Acknowledged A')));
        if (state === 'failed') await act(async () => refresh.reject(new ApiError('network', 'Offline')));
        expect(out.current!.settingsAcknowledgment).not.toBeNull();
        const nextId = boundary === 'course' ? userId : courseId;
        if (boundary === 'session') generation++;
        if (boundary === 'logout') { generation++; auth = { ...auth, generation, user: null, isAuthenticated: false }; }
        vi.mocked(getOwnedCourse).mockResolvedValueOnce({ ...body('New identity'), id: nextId });
        await rerender(() => useSharedCourseState(nextId));
        expect(out.current!.settingsAcknowledgment).toBeNull();
        if (state === 'pending') await act(async () => refresh.resolve(body('Late A')));
        expect(out.current!.course.data?.title).toBe(boundary === 'logout' ? undefined : 'New identity');
        expect(out.current!.settingsAcknowledgment).toBeNull();
    });

    it.each(['pending', 'failed'])('Strict Mode provider unmount discards %s state and cancels late work', async state => {
        vi.mocked(getOwnedCourse).mockResolvedValue(body());
        const refresh = deferred<OwnedCourse>();
        const { out } = await render(() => useSharedCourseState(courseId), true);
        vi.mocked(getOwnedCourse).mockReturnValueOnce(refresh.promise);
        await act(async () => out.current!.acknowledgeSettings(body('Acknowledged A')));
        const options = vi.mocked(getOwnedCourse).mock.calls.at(-1)![1]!;
        if (state === 'failed') await act(async () => refresh.reject(new ApiError('protocol', 'Invalid response')));
        expect(out.current!.settingsAcknowledgment).not.toBeNull();
        await act(async () => root.unmount()); root = createRoot(host);
        expect(options.signal!.aborted).toBe(true); expect(options.isCurrent!()).toBe(false);
        vi.mocked(getOwnedCourse).mockResolvedValue(body('New mount'));
        const next = await render(() => useSharedCourseState(courseId), true);
        if (state === 'pending') await act(async () => refresh.resolve(body('Late A')));
        expect(next.out.current!.course.data?.title).toBe('New mount');
        expect(next.out.current!.settingsAcknowledgment).toBeNull();
    });
});
