// @vitest-environment jsdom
import { act } from 'react';
import { createRoot } from 'react-dom/client';
import type { Root } from 'react-dom/client';
import { MemoryRouter, Route, Routes, useLocation, useNavigate, useParams } from 'react-router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { AuthContext } from '../../../../app/providers/AuthProvider';
import type { AuthContextValue } from '../../../../app/providers/AuthProvider';
import { ApiError } from '../../../../shared/types/apiTypes';
import {
    createAssignment, createQuestion, createQuiz, deleteUnusedHiddenQuiz, deleteUnusedQuestion, listOwnedAssignments, listOwnedQuizzes,
    listQuestions, readOwnedAssignment, readOwnedQuiz, readQuestion, replaceAssignment, replaceUnusedHiddenQuiz, replaceUnusedQuestion,
    setAssignmentPublication, setQuizPublication,
} from '../api/assessmentAuthoringApi';
import type { B3Question, B3QuestionPage, B3QuizOwned, B3QuizOwnedPage, B5OwnedAssignment, B5OwnedAssignmentPage } from '../api/assessmentAuthoringApi';
import { createOwnedCourse, getOwnedCourse, listOwnedCourses, setOwnedCoursePublication, updateOwnedCourse } from '../api/instructorCourseApi';
import type { OwnedCourse } from '../api/instructorCourseApi';
import { AssessmentsPage, formatTimeLimit } from './QuestionBankPage';
import { QuizEditor, difficultySummary, timeLimitSeconds } from './QuizEditorPage';
import { AssignmentEditor } from './AssignmentEditorPage';
import type { SkillSource } from '../components/QuestionForm';
import { AppRouter } from '../../../../routes';
import { addCoursePrerequisite, createCourseSkill, getOwnedSkillGraph, mapCourseLessonSkill, removeCoursePrerequisite, unmapCourseLessonSkill, updateCourseSkill } from '../../skill-graph-editor/api/skillGraphApi';
import type { OwnedSkillGraph } from '../../skill-graph-editor/api/skillGraphApi';
import { instantMicros, sameAssignment, sameInstant } from './AssignmentEditorPage';

vi.mock('../api/assessmentAuthoringApi', async (original) => ({
    ...await original<object>(), listQuestions: vi.fn(), createQuestion: vi.fn(), readQuestion: vi.fn(), replaceUnusedQuestion: vi.fn(), deleteUnusedQuestion: vi.fn(),
    listOwnedQuizzes: vi.fn(), createQuiz: vi.fn(), readOwnedQuiz: vi.fn(), replaceUnusedHiddenQuiz: vi.fn(), deleteUnusedHiddenQuiz: vi.fn(), setQuizPublication: vi.fn(),
    listOwnedAssignments: vi.fn(), createAssignment: vi.fn(), readOwnedAssignment: vi.fn(), replaceAssignment: vi.fn(), setAssignmentPublication: vi.fn(),
}));
vi.mock('../api/instructorCourseApi', async (original) => ({
    ...await original<object>(), getOwnedCourse: vi.fn(), listOwnedCourses: vi.fn(), createOwnedCourse: vi.fn(), updateOwnedCourse: vi.fn(),
    setOwnedCoursePublication: vi.fn(),
}));
vi.mock('../../skill-graph-editor/api/skillGraphApi', async (original) => ({
    ...await original<object>(), getOwnedSkillGraph: vi.fn(), createCourseSkill: vi.fn(), updateCourseSkill: vi.fn(), addCoursePrerequisite: vi.fn(),
    removeCoursePrerequisite: vi.fn(), mapCourseLessonSkill: vi.fn(), unmapCourseLessonSkill: vi.fn(),
}));
Object.assign(globalThis, { IS_REACT_ACT_ENVIRONMENT: true });

const courseId = '00000000-0000-4000-8000-0000000000c1';
const loops = { id: '00000000-0000-4000-8000-0000000000a1', name: 'Loops', description: 'for and while' };
const arrays = { id: '00000000-0000-4000-8000-0000000000a2', name: 'Arrays', description: null };
const q = (n: number, patch: Partial<B3Question> = {}): B3Question => ({ id: `00000000-0000-4000-8000-${String(n).padStart(12, '0')}`, skillId: loops.id, prompt: `Question ${n}`,
    kind: 'single_choice', difficulty: 'easy', options: ['A', 'B'], correctOptions: [true, false], ...patch });
const quizId = '00000000-0000-4000-8000-0000000000f1';
const quiz = (patch: Partial<B3QuizOwned> = {}): B3QuizOwned => ({ id: quizId, courseId, title: 'Loops check', kind: 'practice', timeLimitSeconds: 95, published: false, questionCount: 2,
    questions: [{ position: 1, question: q(1) }, { position: 2, question: q(2) }], ...patch });
const questionPage = (content: B3Question[], patch: Partial<B3QuestionPage> = {}): B3QuestionPage => ({ content, page: 0, size: 20, totalElements: content.length, totalPages: content.length ? 1 : 0, ...patch });
const quizPage = (content: B3QuizOwned[], patch: Partial<B3QuizOwnedPage> = {}): B3QuizOwnedPage => ({ content, page: 0, size: 20, totalElements: content.length, totalPages: content.length ? 1 : 0, ...patch });
const lessonOneId = '00000000-0000-4000-8000-0000000000b1';
const lessonHiddenId = '00000000-0000-4000-8000-0000000000b2';
const assignmentId = '00000000-0000-4000-8000-0000000000d1';
const assignment = (patch: Partial<B5OwnedAssignment> = {}): B5OwnedAssignment => ({
    id: assignmentId,
    courseId,
    lessonId: lessonHiddenId,
    title: 'Loops Lab',
    instructions: '  # Lab instructions\nSubmit a ZIP.\n',
    deadlineAt: '2026-12-01T12:00:00.123456Z',
    publicationStatus: 'hidden',
    submissionCount: 4,
    gradedCount: 2,
    ...patch,
});
const assignmentPage = (content: B5OwnedAssignment[], patch: Partial<B5OwnedAssignmentPage> = {}): B5OwnedAssignmentPage => ({
    content, page: 0, size: 20, totalElements: content.length, totalPages: content.length ? 1 : 0, ...patch,
});
const ownedCourseFixture = (patch: Partial<OwnedCourse> = {}): OwnedCourse => ({
    id: courseId,
    title: 'Python Foundations',
    description: 'Variables, loops and functions.',
    publicationStatus: 'hidden',
    prerequisiteDescription: null,
    chapters: [{
        id: '00000000-0000-4000-8000-0000000000e1',
        title: 'Control Flow',
        position: 1,
        lessons: [
            { id: lessonOneId, chapterId: '00000000-0000-4000-8000-0000000000e1', title: 'For Loops', contentMarkdown: null, position: 1, publicationStatus: 'published', materials: [] },
            { id: lessonHiddenId, chapterId: '00000000-0000-4000-8000-0000000000e1', title: 'While Loops', contentMarkdown: null, position: 2, publicationStatus: 'hidden', materials: [] },
        ],
    }],
    ...patch,
});
const http = (status: number, message = 'The request conflicts with existing information.', fields?: Record<string, string>) =>
    new ApiError('http', message, status, undefined, fields);

let host: HTMLDivElement, root: Root, auth: AuthContextValue, skills: SkillSource, location = '';
beforeEach(() => {
    vi.stubGlobal('ResizeObserver', class { observe() { /* jsdom */ } unobserve() { /* jsdom */ } disconnect() { /* jsdom */ } });
    vi.stubGlobal('DOMMatrixReadOnly', class { m22 = 1; });
    host = document.createElement('div'); document.body.append(host); root = createRoot(host);
    auth = { user: { id: loops.id, displayName: 'Instructor', role: 'Instructor' }, generation: 1, isAuthenticated: true, restoring: false,
        isCurrent: (generation) => generation === 1, login: vi.fn(), register: vi.fn(), logout: vi.fn(), rememberProfile: vi.fn(), consumeReturn: () => '/courses' };
    skills = { status: 'ready', skills: [loops, arrays], reload: vi.fn() };
    vi.mocked(listQuestions).mockResolvedValue(questionPage([q(2), q(1, { skillId: arrays.id, difficulty: 'hard', kind: 'true_false', options: ['True', 'False'] })]));
    vi.mocked(listOwnedQuizzes).mockResolvedValue(quizPage([quiz(), quiz({ id: '00000000-0000-4000-8000-0000000000f2', title: 'Final test', kind: 'test', published: true, timeLimitSeconds: 600 })]));
    vi.mocked(listOwnedAssignments).mockResolvedValue(assignmentPage([assignment(), assignment({ id: '00000000-0000-4000-8000-0000000000d2', title: 'Final Project', lessonId: null, publicationStatus: 'published', submissionCount: 0, gradedCount: 0 })]));
    vi.mocked(getOwnedCourse).mockResolvedValue(ownedCourseFixture());
});
afterEach(async () => { await act(async () => root.unmount()); host.remove(); document.body.innerHTML = ''; vi.unstubAllGlobals(); vi.resetAllMocks(); });

function Where() { const value = useLocation(); location = `${value.pathname}${value.search}`; return null; }
async function mount(path: string) {
    const courseContext = { status: 'ready' as const, data: ownedCourseFixture(), reload: vi.fn() };
    await act(async () => root.render(<MemoryRouter initialEntries={[path]}><AuthContext.Provider value={auth}><Where /><Routes>
        <Route path="/teach/courses/:courseId/assessments" element={<AssessmentsPage courseId={courseId} skills={skills} courseContext={courseContext} />} />
        <Route path="/teach/courses/:courseId/quizzes/:quizId" element={<QuizRoute />} />
        <Route path="/teach/courses/:courseId/assignments/:assignmentId" element={<AssignmentRoute courseContext={courseContext} />} />
    </Routes></AuthContext.Provider></MemoryRouter>));
}
function QuizRoute() { const id = useParams().quizId!; return <QuizEditor key={id} courseId={courseId} quizId={id} skills={skills} />; }
function AssignmentRoute({ courseContext }: { courseContext: { status: 'loading' | 'ready' | 'error'; data: OwnedCourse | null; reload: () => void } }) {
    const id = useParams().assignmentId!;
    return <AssignmentEditor key={id} courseId={courseId} assignmentId={id} courseContext={courseContext} />;
}
const flush = () => act(async () => { await new Promise((resolve) => setTimeout(resolve, 0)); });
const buttons = (scope: ParentNode = document) => [...scope.querySelectorAll<HTMLElement>('button,a,[role="button"]')];
function find(text: string, scope: ParentNode = document) {
    const node = buttons(scope).find((item) => item.textContent?.trim() === text || item.getAttribute('aria-label') === text);
    if (!node) throw new Error(`No control "${text}" in: ${buttons(scope).map((item) => item.textContent?.trim() || item.getAttribute('aria-label')).join(' | ')}`);
    return node;
}
const click = async (text: string, scope: ParentNode = document) => { await act(async () => find(text, scope).click()); await flush(); };
const dialog = () => document.querySelector<HTMLElement>('[role="dialog"]:not([aria-hidden="true"])')!;
const dialogs = () => [...document.querySelectorAll<HTMLElement>('[role="dialog"]')];
const confirmDialog = (title: string) => dialogs().find((item) => item.textContent?.includes(title))!;
function field(label: string, scope: ParentNode = document) {
    const labelNode = [...scope.querySelectorAll('label')].find((item) => item.textContent?.replaceAll('*', '').trim() === label);
    const input = labelNode ? document.getElementById(labelNode.getAttribute('for') ?? '') as HTMLInputElement | HTMLTextAreaElement | null : scope.querySelector<HTMLInputElement>(`input[aria-label="${label}"]`);
    if (!input) throw new Error(`No field "${label}"`);
    return input;
}
async function type(label: string, value: string, scope: ParentNode = document) {
    const input = field(label, scope);
    const prototype = input instanceof HTMLTextAreaElement ? HTMLTextAreaElement.prototype : HTMLInputElement.prototype;
    await act(async () => { Object.getOwnPropertyDescriptor(prototype, 'value')!.set!.call(input, value); input.dispatchEvent(new Event('input', { bubbles: true })); });
}
async function choose(label: string, option: string, scope: ParentNode = document) {
    // MUI renders a select's label as a div with the InputLabel class.
    const labelNode = [...scope.querySelectorAll('.MuiInputLabel-root')].find((item) => item.textContent?.replaceAll('*', '').trim() === label)!;
    const combo = scope.querySelector<HTMLElement>(`[role="combobox"][aria-labelledby~="${labelNode.id}"]`)!;
    await act(async () => { combo.dispatchEvent(new MouseEvent('mousedown', { bubbles: true, button: 0 })); });
    const item = [...document.querySelectorAll<HTMLElement>('[role="option"]')].find((node) => node.textContent?.startsWith(option))!;
    await act(async () => item.click()); await flush();
}
const text = () => document.body.textContent ?? '';

describe('Assessments page (S19, D12-2)', () => {
    it('opens Questions for a missing or unknown view and shows rows in API order with graph skill names', async () => {
        await mount(`/teach/courses/${courseId}/assessments?view=unknown`);
        expect(document.querySelector('h1')?.textContent).toBe('Assessments');
        expect(listQuestions).toHaveBeenCalledWith(courseId, 0, 20, expect.objectContaining({ signal: expect.any(AbortSignal) }));
        const rows = [...document.querySelectorAll('tbody tr')].map((row) => row.textContent);
        expect(rows[0]).toContain('Question 2'); expect(rows[0]).toContain('Loops'); expect(rows[0]).toContain('Single choice');
        expect(rows[1]).toContain('Question 1'); expect(rows[1]).toContain('Arrays'); expect(rows[1]).toContain('True/false'); expect(rows[1]).toContain('Hard');
        expect(text()).toContain('Assignments'); expect(text()).not.toContain('Search');
        expect(listOwnedQuizzes).not.toHaveBeenCalled();
        expect(listOwnedAssignments).not.toHaveBeenCalled();
    });
    it('opens Assignments for ?view=assignments and renders lesson labels, submission counts and actions', async () => {
        await mount(`/teach/courses/${courseId}/assessments?view=assignments`);
        expect(listOwnedAssignments).toHaveBeenCalledWith(courseId, 0, 20, expect.anything());
        expect(find('New assignment').getAttribute('href')).toBe(`/teach/courses/${courseId}/assignments/new`);
        const rows = [...document.querySelectorAll('tbody tr')].map((row) => row.textContent);
        expect(rows[0]).toContain('Loops Lab');
        expect(rows[0]).toContain('While Loops (hidden)');
        expect(rows[0]).toContain('Hidden');
        expect(rows[1]).toContain('Final Project');
        expect(rows[1]).toContain('—');
        expect(rows[1]).toContain('Published');
        expect(find('Open assignment: Loops Lab').getAttribute('href')).toBe(`/teach/courses/${courseId}/assignments/${assignmentId}`);
    });
    it('switches views by replacing the URL and reads each view from its first page', async () => {
        vi.mocked(listQuestions).mockResolvedValue(questionPage([q(1)], { totalElements: 21, totalPages: 2 }));
        await mount(`/teach/courses/${courseId}/assessments`);
        await click('Go to page 2'); expect(vi.mocked(listQuestions).mock.lastCall?.slice(0, 2)).toEqual([courseId, 1]);
        await click('Quizzes'); expect(location).toBe(`/teach/courses/${courseId}/assessments?view=quizzes`);
        expect(listOwnedQuizzes).toHaveBeenCalledWith(courseId, 0, 20, expect.anything());
        const rows = [...document.querySelectorAll('tbody tr')].map((row) => row.textContent);
        expect(rows[0]).toContain('Loops check'); expect(rows[0]).toContain('Practice'); expect(rows[0]).toContain('1 min 35 s'); expect(rows[0]).toContain('Hidden');
        expect(rows[1]).toContain('Final test'); expect(rows[1]).toContain('10 min'); expect(rows[1]).toContain('Published');
        expect(find('Open quiz: Loops check').getAttribute('href')).toBe(`/teach/courses/${courseId}/quizzes/${quizId}`);
        expect(find('New quiz').getAttribute('href')).toBe(`/teach/courses/${courseId}/quizzes/new`);
        await click('Questions'); expect(location).toBe(`/teach/courses/${courseId}/assessments`); expect(vi.mocked(listQuestions).mock.lastCall?.[1]).toBe(0);
    });
    it('shows empty and failure states, retries the read and maps a concealed course to the F14 state', async () => {
        vi.mocked(listQuestions).mockResolvedValueOnce(questionPage([]));
        await mount(`/teach/courses/${courseId}/assessments`);
        expect(text()).toContain('No questions yet'); expect(text()).toContain('Each question is tagged with a skill and a difficulty.');
        vi.mocked(listOwnedQuizzes).mockRejectedValueOnce(new ApiError('http', 'x', 500));
        await click('Quizzes'); expect(text()).toContain("Quizzes couldn't be loaded."); expect(text()).toContain('Something went wrong on our side.');
        await click('Try again'); expect(text()).toContain('Loops check');
        vi.mocked(listQuestions).mockRejectedValueOnce(http(404, 'The requested resource is unavailable.'));
        await click('Questions'); expect(text()).toContain('Course not available'); expect(find('Back to catalogue').getAttribute('href')).toBe('/courses');
    });
    it('shows "Skill unavailable" when the graph read failed and re-reads a stale graph once per missing skill', async () => {
        skills = { status: 'error', skills: [], reload: vi.fn() };
        await mount(`/teach/courses/${courseId}/assessments`);
        expect(text()).toContain("Skills couldn't be loaded."); expect([...document.querySelectorAll('tbody tr')][0].textContent).toContain('Skill unavailable');
        await click('Try again'); expect(skills.reload).toHaveBeenCalledTimes(1);
        await act(async () => root.unmount()); root = createRoot(host);
        skills = { status: 'ready', skills: [loops], reload: vi.fn() };
        await mount(`/teach/courses/${courseId}/assessments`);
        expect(skills.reload).toHaveBeenCalledTimes(1);
        // The re-read returned a graph still without that skill: no further re-read for it.
        skills = { ...skills, skills: [loops] };
        await mount(`/teach/courses/${courseId}/assessments`);
        expect(skills.reload).toHaveBeenCalledTimes(1); expect([...document.querySelectorAll('tbody tr')][1].textContent).toContain('Skill unavailable');
    });
});

describe('Question form (S19, D10-4)', () => {
    it('creates a tagged question without a code-snippet field and reports the saved status', async () => {
        vi.mocked(createQuestion).mockResolvedValue(q(9));
        await mount(`/teach/courses/${courseId}/assessments`);
        await click('New question');
        const sheet = dialog();
        expect(sheet.textContent).toContain('New question'); expect(sheet.textContent).not.toMatch(/code snippet/i);
        await click('Save question', sheet);
        expect(sheet.textContent).toContain('Enter the question.'); expect(sheet.textContent).toContain('Choose the skill this question checks.'); expect(sheet.textContent).toContain('Choose a difficulty.');
        expect(createQuestion).not.toHaveBeenCalled();
        await type('Question', 'x = 2\nprint(x * 2)', sheet); await type('Option 1', '4', sheet); await type('Option 2', '22', sheet);
        await act(async () => sheet.querySelector<HTMLInputElement>('input[type="radio"]')!.click());
        await choose('Skill', 'Arrays', sheet); await choose('Difficulty', 'Medium', sheet);
        await click('Save question', sheet);
        expect(createQuestion).toHaveBeenCalledWith(courseId, { skillId: arrays.id, prompt: 'x = 2\nprint(x * 2)', kind: 'single_choice', difficulty: 'medium', options: ['4', '22'], correctOptions: [true, false] }, expect.anything());
        expect(text()).toContain('Question saved.'); expect(listQuestions).toHaveBeenCalledTimes(2);
    });
    it('lists skills alphabetically, and blocks saving without skills', async () => {
        await mount(`/teach/courses/${courseId}/assessments`);
        await click('New question');
        const labelNode = [...dialog().querySelectorAll('.MuiInputLabel-root')].find((item) => item.textContent?.startsWith('Skill'))!;
        await act(async () => { dialog().querySelector<HTMLElement>(`[aria-labelledby~="${labelNode.id}"]`)!.dispatchEvent(new MouseEvent('mousedown', { bubbles: true, button: 0 })); });
        expect([...document.querySelectorAll('[role="option"]')].map((item) => item.textContent)).toEqual(['Arrays', 'Loopsfor and while']);
        await act(async () => root.unmount()); root = createRoot(host); document.body.innerHTML = ''; host = document.createElement('div'); document.body.append(host); root = createRoot(host);
        skills = { status: 'ready', skills: [], reload: vi.fn() };
        vi.mocked(listQuestions).mockResolvedValue(questionPage([]));
        await mount(`/teach/courses/${courseId}/assessments`);
        await click('New question');
        expect(dialog().textContent).toContain('This course has no skills yet. Add a skill in the Skills tab first.');
        expect(find('Save question', dialog()).hasAttribute('disabled')).toBe(true);
    });
    it('reads an existing question on opening, keeps the draft on 409 and saves it explicitly as a new question', async () => {
        vi.mocked(readQuestion).mockResolvedValue(q(2, { prompt: 'Question 2 (latest)' }));
        vi.mocked(replaceUnusedQuestion).mockRejectedValue(http(409));
        vi.mocked(createQuestion).mockResolvedValue(q(10));
        await mount(`/teach/courses/${courseId}/assessments`);
        await click('Edit question: Question 2');
        expect(readQuestion).toHaveBeenCalledWith(courseId, q(2).id, expect.anything());
        expect((field('Question', dialog()) as HTMLTextAreaElement).value).toBe('Question 2 (latest)');
        await type('Question', 'Edited prompt', dialog()); await click('Save question', dialog());
        expect(dialog().textContent).toContain("This question can't be changed, for example because learners have answered it. Your edits are still here.");
        expect((field('Question', dialog()) as HTMLTextAreaElement).value).toBe('Edited prompt');
        expect(createQuestion).not.toHaveBeenCalled();
        await click('Save as a new question', dialog());
        expect(createQuestion).toHaveBeenCalledWith(courseId, expect.objectContaining({ prompt: 'Edited prompt', skillId: loops.id }), expect.anything());
        expect(replaceUnusedQuestion).toHaveBeenCalledTimes(1); expect(text()).toContain('Question saved.');
    });
    it('re-reads after an unknown save outcome and offers another save only after the read', async () => {
        vi.mocked(readQuestion).mockResolvedValueOnce(q(2)).mockResolvedValueOnce(q(2, { prompt: 'Changed elsewhere' }));
        vi.mocked(replaceUnusedQuestion).mockRejectedValue(new ApiError('network', 'Unable to connect. Please try again.'));
        await mount(`/teach/courses/${courseId}/assessments`);
        await click('Edit question: Question 2'); await type('Question', 'Mine', dialog()); await click('Save question', dialog());
        expect(readQuestion).toHaveBeenCalledTimes(2); expect(replaceUnusedQuestion).toHaveBeenCalledTimes(1);
        expect(dialog().textContent).toContain("We couldn't confirm whether your changes were saved."); expect(dialog().textContent).toContain('Changed elsewhere');
        expect((field('Question', dialog()) as HTMLTextAreaElement).value).toBe('Mine');
        expect(find('Save question', dialog()).hasAttribute('disabled')).toBe(false);
    });
    it('treats a re-read equal to the draft as a committed save', async () => {
        vi.mocked(readQuestion).mockResolvedValueOnce(q(2)).mockResolvedValueOnce(q(2, { prompt: 'Mine' }));
        vi.mocked(replaceUnusedQuestion).mockRejectedValue(new ApiError('http', 'x', 500));
        await mount(`/teach/courses/${courseId}/assessments`);
        await click('Edit question: Question 2'); await type('Question', 'Mine', dialog()); await click('Save question', dialog());
        expect(text()).toContain('Question saved.');
    });
    it('deletes after confirmation and shows a 409 refusal without changing anything', async () => {
        vi.mocked(readQuestion).mockResolvedValue(q(2));
        vi.mocked(deleteUnusedQuestion).mockRejectedValueOnce(http(409)).mockResolvedValueOnce(q(2));
        await mount(`/teach/courses/${courseId}/assessments`);
        await click('Edit question: Question 2'); await click('Delete question', dialog());
        expect(deleteUnusedQuestion).not.toHaveBeenCalled();
        await click('Delete question', confirmDialog('Delete this question?'));
        expect(text()).toContain("This question can't be deleted, for example because it is in a quiz or has been answered.");
        await click('Delete question', dialogs().find((item) => item.textContent?.includes('Edit question'))!);
        await click('Delete question', confirmDialog('Delete this question?'));
        expect(text()).toContain('Question deleted.');
    });
    it('asks before discarding edits and offers a new question when the original is gone', async () => {
        vi.mocked(readQuestion).mockResolvedValue(q(2));
        vi.mocked(replaceUnusedQuestion).mockRejectedValue(http(404, 'The requested resource is unavailable.'));
        await mount(`/teach/courses/${courseId}/assessments`);
        await click('Edit question: Question 2'); await type('Question', 'Draft', dialog());
        await click('Cancel', dialog()); expect(confirmDialog('Discard your changes?')).toBeTruthy();
        await click('Keep editing', confirmDialog('Discard your changes?'));
        await click('Save question', dialog());
        expect(dialog().textContent).toContain('This question is no longer available.');
        expect(find('Save as a new question', dialog())).toBeTruthy(); expect(text()).not.toContain('Delete question');
    });
});

describe('Quiz editor (S19)', () => {
    it('creates a hidden quiz with an exact time limit and replaces the URL', async () => {
        vi.mocked(createQuiz).mockResolvedValue(quiz({ questionCount: 0, questions: [] }));
        vi.mocked(readOwnedQuiz).mockResolvedValue(quiz({ questionCount: 0, questions: [] }));
        await mount(`/teach/courses/${courseId}/quizzes/new`);
        expect(document.querySelector('h1')?.textContent).toBe('New quiz');
        await click('Create quiz');
        expect(text()).toContain('Enter a quiz title.'); expect(text()).toContain('Choose a quiz kind.'); expect(text()).toContain('Enter a time limit of at least 1 second');
        await type('Quiz title', 'Loops check'); await choose('Quiz kind', 'Practice'); await type('Minutes', '1'); await type('Seconds', '35');
        await click('Create quiz');
        expect(createQuiz).toHaveBeenCalledWith(courseId, { title: 'Loops check', kind: 'practice', timeLimitSeconds: 95, questionIds: [] }, expect.anything());
        expect(location).toBe(`/teach/courses/${courseId}/quizzes/${quizId}`);
        expect(readOwnedQuiz).toHaveBeenCalledWith(courseId, quizId, expect.anything());
        expect(text()).toContain('Quiz created. It stays hidden until you publish it.');
    });
    it('reads a hidden quiz on entry, reorders, removes and appends picked questions, and saves the exact order', async () => {
        vi.mocked(readOwnedQuiz).mockResolvedValue(quiz());
        vi.mocked(listQuestions).mockResolvedValue(questionPage([q(1), q(3, { difficulty: 'medium' }), q(4)]));
        vi.mocked(replaceUnusedHiddenQuiz).mockImplementation(async (_course, _id, write) => quiz({ questionCount: write.questionIds.length,
            questions: write.questionIds.map((id, index) => ({ position: index + 1, question: [q(1), q(2), q(3, { difficulty: 'medium' }), q(4)].find((item) => item.id === id)! })) }));
        await mount(`/teach/courses/${courseId}/quizzes/${quizId}`);
        expect((field('Minutes') as HTMLInputElement).value).toBe('1'); expect((field('Seconds') as HTMLInputElement).value).toBe('35');
        expect(text()).toContain('All questions are Easy. Learners can be recommended this quiz as Easy practice.');
        await click('Move question 2 up');
        await click('Add questions');
        const picker = confirmDialog('Add questions');
        const rows = [...picker.querySelectorAll('li')];
        expect(rows[0].textContent).toContain('In this quiz'); expect(rows[0].querySelector('input')!.disabled).toBe(true);
        await act(async () => rows[2].querySelector('input')!.click()); await act(async () => rows[1].querySelector('input')!.click());
        await click('Add questions', picker.querySelector('.MuiDialogActions-root')!);
        expect(text()).toContain('Mixed difficulty. This quiz is never recommended as practice, but learners can still take it.');
        await click('Remove question 1 from the quiz');
        expect(text()).toContain('Unsaved changes');
        await click('Save changes');
        expect(replaceUnusedHiddenQuiz).toHaveBeenCalledWith(courseId, quizId, { title: 'Loops check', kind: 'practice', timeLimitSeconds: 95, questionIds: [q(1).id, q(4).id, q(3).id] }, expect.anything());
        expect(text()).toContain('Saved');
    });
    it('opens a published quiz read-only and hides it after confirmation', async () => {
        vi.mocked(readOwnedQuiz).mockResolvedValue(quiz({ published: true }));
        vi.mocked(setQuizPublication).mockResolvedValue(quiz({ published: false }));
        await mount(`/teach/courses/${courseId}/quizzes/${quizId}`);
        expect(text()).toContain("Hide this quiz to edit it. Quizzes that learners have already started can't be edited, even when hidden.");
        expect((field('Quiz title') as HTMLInputElement).disabled).toBe(true); expect(text()).not.toContain('Add questions'); expect(text()).not.toContain('Delete quiz');
        await click('Hide quiz'); expect(setQuizPublication).not.toHaveBeenCalled();
        await click('Hide quiz', confirmDialog('Learners can no longer start this quiz.'));
        expect(setQuizPublication).toHaveBeenCalledWith(courseId, quizId, false, expect.anything());
        expect((field('Quiz title') as HTMLInputElement).disabled).toBe(false); expect(text()).toContain('Quiz hidden.'); expect(text()).toContain('Publish quiz');
    });
    it('shows the server refusal of publishing and offers a new quiz after a refused save', async () => {
        vi.mocked(readOwnedQuiz).mockResolvedValue(quiz({ questionCount: 0, questions: [] }));
        vi.mocked(setQuizPublication).mockRejectedValue(http(409));
        vi.mocked(replaceUnusedHiddenQuiz).mockRejectedValue(http(409));
        vi.mocked(createQuiz).mockResolvedValue(quiz({ id: '00000000-0000-4000-8000-0000000000f9', questionCount: 0, questions: [] }));
        await mount(`/teach/courses/${courseId}/quizzes/${quizId}`);
        await click('Publish quiz'); await click('Publish quiz', confirmDialog('Learners in this course will see this quiz'));
        expect(text()).toContain('The request conflicts with existing information.'); expect(readOwnedQuiz).toHaveBeenCalledTimes(2);
        await type('Quiz title', 'Renamed'); await click('Save changes');
        expect(text()).toContain("This quiz can't be changed, for example because learners have started it. Your edits are still here.");
        expect((field('Quiz title') as HTMLInputElement).value).toBe('Renamed');
        await click('Create a new quiz from these settings');
        expect(createQuiz).toHaveBeenCalledWith(courseId, expect.objectContaining({ title: 'Renamed' }), expect.anything());
        expect(location).toBe(`/teach/courses/${courseId}/quizzes/00000000-0000-4000-8000-0000000000f9`);
    });
    it('deletes a hidden quiz after confirmation and returns to the Quizzes view with the notice', async () => {
        vi.mocked(readOwnedQuiz).mockResolvedValue(quiz());
        vi.mocked(deleteUnusedHiddenQuiz).mockResolvedValue(quiz());
        await mount(`/teach/courses/${courseId}/quizzes/${quizId}`);
        await click('Delete quiz'); await click('Delete quiz', confirmDialog('Delete this quiz?'));
        expect(location).toBe(`/teach/courses/${courseId}/assessments?view=quizzes`); expect(text()).toContain('Quiz deleted.');
    });
    it('keeps an unknown creation as a deliberate retry and asks before leaving with edits', async () => {
        vi.mocked(createQuiz).mockRejectedValue(new ApiError('network', 'Unable to connect. Please try again.'));
        await mount(`/teach/courses/${courseId}/quizzes/new`);
        await type('Quiz title', 'T'); await choose('Quiz kind', 'Test'); await type('Seconds', '30');
        await click('Create quiz');
        expect(text()).toContain("We couldn't confirm whether the quiz was created. Check the quiz list before creating it again."); expect(createQuiz).toHaveBeenCalledTimes(1);
        await click('Back to quizzes'); expect(location).toBe(`/teach/courses/${courseId}/quizzes/new`);
        await click('Stay', confirmDialog('Leave without saving?')); expect(location).toBe(`/teach/courses/${courseId}/quizzes/new`);
        await click('Back to quizzes'); await click('Leave', confirmDialog('Leave without saving?'));
        expect(location).toBe(`/teach/courses/${courseId}/assessments?view=quizzes`);
    });
    it.each([[404, "This quiz isn't available. It may have been deleted."], [400, "We can't open this quiz from this link."]])('shows the %s entry state', async (status, message) => {
        vi.mocked(readOwnedQuiz).mockRejectedValue(http(status, 'x'));
        await mount(`/teach/courses/${courseId}/quizzes/${quizId}`);
        expect(text()).toContain(message); expect(find('Back to quizzes').getAttribute('href')).toBe(`/teach/courses/${courseId}/assessments?view=quizzes`);
    });
});

describe('time limits and difficulty mix', () => {
    it('stores minutes and seconds as exact seconds within the contract bounds', () => {
        expect(timeLimitSeconds('1', '35')).toBe(95); expect(timeLimitSeconds('', '1')).toBe(1); expect(timeLimitSeconds('35791394', '7')).toBe(2147483647);
        expect(timeLimitSeconds('0', '0')).toBeNull(); expect(timeLimitSeconds('1', '60')).toBeNull(); expect(timeLimitSeconds('1.5', '')).toBeNull();
        expect(timeLimitSeconds('35791394', '8')).toBeNull(); expect(timeLimitSeconds('', '')).toBeNull();
        expect(formatTimeLimit(95)).toBe('1 min 35 s'); expect(formatTimeLimit(45)).toBe('45 s'); expect(formatTimeLimit(600)).toBe('10 min');
    });
    it('summarizes practice difficulty only', () => {
        expect(difficultySummary('practice', [q(1, { difficulty: 'hard' })])).toBe('All questions are Hard. Learners can be recommended this quiz as Hard practice.');
        expect(difficultySummary('test', [q(1)])).toBeNull(); expect(difficultySummary('practice', [])).toBeNull();
    });
});

describe('production routes and shared course shell (CT16.routes, D12-3)', () => {
    it('wraps Assessments, Skills, Quiz editor and Assignment editor in one CourseEditorShell sharing getOwnedCourse and getOwnedSkillGraph', async () => {
        vi.mocked(getOwnedCourse).mockResolvedValue(ownedCourseFixture());
        vi.mocked(getOwnedSkillGraph).mockResolvedValue({ courseId, skills: [loops], prerequisites: [], mappings: [] });
        vi.mocked(listQuestions).mockResolvedValue(questionPage([q(1)]));
        vi.mocked(readOwnedQuiz).mockResolvedValue(quiz());
        vi.mocked(readOwnedAssignment).mockResolvedValue(assignment());

        await act(async () => root.render(<MemoryRouter initialEntries={[`/teach/courses/${courseId}/assessments`]}><AuthContext.Provider value={auth}><Where /><AppRouter /></AuthContext.Provider></MemoryRouter>));
        await flush();
        expect(document.querySelector('main h1')?.textContent).toBe('Python Foundations');
        expect(document.querySelector('#assessments-title')?.textContent).toBe('Assessments');
        expect(getOwnedCourse).toHaveBeenCalledTimes(1);
        expect(getOwnedSkillGraph).toHaveBeenCalledTimes(1);
        expect(listQuestions).toHaveBeenCalledTimes(1);

        // Switching to Skills and back to Assessments reuses the shell's shared reads without duplicating requests.
        await click('Skills');
        expect(location).toBe(`/teach/courses/${courseId}/skills`);
        expect(getOwnedCourse).toHaveBeenCalledTimes(1);
        expect(getOwnedSkillGraph).toHaveBeenCalledTimes(1);

        await click('Assessments');
        await click('Quizzes');
        await click('Open quiz: Loops check');
        expect(location).toBe(`/teach/courses/${courseId}/quizzes/${quizId}`);
        expect(document.querySelector('main h1')?.textContent).toBe('Loops check');
        expect(getOwnedCourse).toHaveBeenCalledTimes(1);
        expect(getOwnedSkillGraph).toHaveBeenCalledTimes(1);

        await click('Back to quizzes');
        await click('Assignments');
        await click('Open assignment: Loops Lab');
        expect(location).toBe(`/teach/courses/${courseId}/assignments/${assignmentId}`);
        expect(document.querySelector('main h1')?.textContent).toBe('Loops Lab');
        expect(text()).toContain('Submitted 4 · Graded 2');
        expect(getOwnedCourse).toHaveBeenCalledTimes(1);
    });

    it('creates, edits and publishes an assignment while preserving untouched microsecond deadlines', async () => {
        vi.mocked(readOwnedAssignment).mockResolvedValue(assignment());
        vi.mocked(replaceAssignment).mockImplementation(async (_course, _id, write) => assignment({
            title: write.title, instructions: write.instructions, deadlineAt: write.deadlineAt, lessonId: write.lessonId,
        }));
        vi.mocked(setAssignmentPublication).mockResolvedValue(assignment({ publicationStatus: 'published' }));

        await mount(`/teach/courses/${courseId}/assignments/${assignmentId}`);
        await type('Assignment title', 'Updated Loops Lab');
        await click('Save changes');
        // Untouched deadline preserves exact microsecond ISO string from server.
        expect(replaceAssignment).toHaveBeenCalledWith(courseId, assignmentId, expect.objectContaining({
            title: 'Updated Loops Lab',
            deadlineAt: '2026-12-01T12:00:00.123456Z',
            lessonId: lessonHiddenId,
        }), expect.anything());
        expect(text()).toContain('Saved');

        await click('Publish assignment');
        await click('Publish assignment', confirmDialog('Publish assignment?'));
        expect(setAssignmentPublication).toHaveBeenCalledWith(courseId, assignmentId, 'published', expect.anything());
        expect(text()).toContain('Assignment published.');

        vi.mocked(createAssignment).mockResolvedValue(assignment({ id: '00000000-0000-4000-8000-0000000000d9' }));
        await act(async () => root.unmount()); root = createRoot(host);
        await mount(`/teach/courses/${courseId}/assignments/new`);
        await type('Assignment title', 'New Lab');
        await type('Instructions', 'Write tests.');
        await type('Deadline', '2026-12-20T14:30');
        await click('Create assignment');
        expect(createAssignment).toHaveBeenCalledTimes(1);
        expect(location).toBe('/teach/courses/00000000-0000-4000-8000-0000000000c1/assignments/00000000-0000-4000-8000-0000000000d9');
    });

    it('shows a production 400 (no field errors) at form level, and 404 skill rejection and 403 read-only lock in QuestionEditorSheet', async () => {
        // The CT23 backend answers 400 with a generic detail and no `errors[]`, and the shared client keeps no
        // field errors for these operations: the form-level alert is the production behavior.
        vi.mocked(createQuestion)
            .mockRejectedValueOnce(http(400, 'Please check the supplied information.'))
            .mockRejectedValueOnce(http(400, 'Please check the supplied information.', { prompt: 'Prompt invalid.' }))
            .mockRejectedValueOnce(http(404, 'The requested resource is unavailable.'))
            .mockRejectedValueOnce(http(403, 'You do not have permission to perform this action.'));
        await mount(`/teach/courses/${courseId}/assessments`);
        await click('New question');
        const sheet = dialog();
        await type('Question', 'Q?', sheet); await type('Option 1', 'A', sheet); await type('Option 2', 'B', sheet);
        await act(async () => sheet.querySelector<HTMLInputElement>('input[type="radio"]')!.click());
        await choose('Skill', 'Arrays', sheet); await choose('Difficulty', 'Easy', sheet);

        await click('Save question', sheet);
        expect(sheet.textContent).toContain('Please check the supplied information.');
        expect(sheet.textContent).not.toContain('Prompt invalid.');

        // HYPOTHETICAL, not production evidence: field mapping is kept for a future contract that returns
        // decoded field errors; no current CT23 response produces this ApiError.
        await click('Save question', sheet);
        expect(sheet.textContent).toContain('Prompt invalid.');

        await type('Question', 'Q2?', sheet);
        await click('Save question', sheet);
        expect(skills.reload).toHaveBeenCalledTimes(1);
        expect(sheet.textContent).toContain('This skill is no longer available. Choose another skill.');

        await choose('Skill', 'Loops', sheet);
        await click('Save question', sheet);
        expect(sheet.textContent).toContain("You don't have access to this course.");
        expect(find('Save question', sheet).hasAttribute('disabled')).toBe(true);
    });

    it('denies other roles before any assessment or course request', async () => {
        auth = { ...auth, user: { id: loops.id, displayName: 'Learner', role: 'Learner' } };
        await act(async () => root.render(<MemoryRouter initialEntries={[`/teach/courses/${courseId}/quizzes/new`]}><AuthContext.Provider value={auth}><AppRouter /></AuthContext.Provider></MemoryRouter>));
        expect(text()).toContain("You don't have access to this page");
        expect(getOwnedCourse).not.toHaveBeenCalled();
        expect(getOwnedSkillGraph).not.toHaveBeenCalled();
        expect(listQuestions).not.toHaveBeenCalled();
    });
});

// --- PR #273 audit remediation regressions (N1–N6, N10) ---

const variables = { id: '00000000-0000-4000-8000-0000000000a3', name: 'Variables', description: null };
const graphOf = (patch: Partial<OwnedSkillGraph> = {}): OwnedSkillGraph => ({ courseId, skills: [loops, variables], prerequisites: [{ skillId: loops.id, prerequisiteSkillId: variables.id }], mappings: [], ...patch });
async function mountApp(path: string) {
    // Destinations outside this feature are stubbed: the catalogue through AppRouter's own slot, Teaching's list by its client.
    vi.mocked(listOwnedCourses).mockResolvedValue({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 });
    await act(async () => root.render(<MemoryRouter initialEntries={[path]}><AuthContext.Provider value={auth}><Where />
        <AppRouter catalogue={<p>Catalogue stub</p>} /></AuthContext.Provider></MemoryRouter>));
    await flush();
}
/** Several macrotask turns: a render/effect/reload loop would keep issuing requests across them. */
const settle = async (turns = 25) => { for (let index = 0; index < turns; index += 1) await flush(); };
const navLink = (name: string) => [...document.querySelectorAll<HTMLElement>('header a, nav a')].find((item) => item.textContent?.trim() === name || item.getAttribute('aria-label') === name)!;

describe('N1: the skill graph read is never retried automatically inside the course editor shell', () => {
    it('makes one read under a persistent 500, and "Try again" makes exactly one more', async () => {
        vi.mocked(getOwnedSkillGraph).mockRejectedValue(new ApiError('http', 'x', 500));
        vi.mocked(listQuestions).mockResolvedValue(questionPage([q(1)]));
        await mountApp(`/teach/courses/${courseId}/assessments`);
        await settle();
        expect(text()).toContain("Skills couldn't be loaded.");
        expect(getOwnedSkillGraph).toHaveBeenCalledTimes(1);

        // Switching views re-renders the shell and the page; neither reads again.
        await click('Quizzes'); await click('Questions'); await settle();
        expect(getOwnedSkillGraph).toHaveBeenCalledTimes(1);

        await click('Try again');
        await settle();
        expect(getOwnedSkillGraph).toHaveBeenCalledTimes(2);
        expect(text()).toContain("Skills couldn't be loaded.");
    });

    it('re-reads a graph that an S20 change left stale once when the Questions view opens, not continuously', async () => {
        vi.mocked(getOwnedSkillGraph).mockResolvedValueOnce(graphOf()).mockRejectedValue(new ApiError('network', 'down'));
        vi.mocked(createCourseSkill).mockRejectedValue(new ApiError('network', 'down'));
        vi.mocked(listQuestions).mockResolvedValue(questionPage([q(1)]));
        await mountApp(`/teach/courses/${courseId}/skills`);
        await click('Add skill');
        await type('Skill name', 'Arrays');
        await act(async () => find('Add skill', document.querySelector('form')!).click()); await settle();
        // The unknown creation re-reads the graph once (S20); that read fails.
        expect(getOwnedSkillGraph).toHaveBeenCalledTimes(2);

        await click('Assessments'); await settle();
        expect(getOwnedSkillGraph).toHaveBeenCalledTimes(3);
        await click('Quizzes'); await settle();
        expect(getOwnedSkillGraph).toHaveBeenCalledTimes(3);
    });
});

describe('N3/N4: skill saves report exactly what CT05 committed, and no invented question count', () => {
    async function openSkill(name: string) {
        const chip = [...document.querySelectorAll<HTMLElement>('[aria-label="Course skills"] [role="button"]')].find((item) => item.textContent === name)!;
        await act(async () => chip.click()); await flush();
    }
    async function addPrerequisite(name: string) {
        await type('Prerequisites', name);
        const option = [...document.querySelectorAll<HTMLElement>('[role="option"]')].find((item) => item.textContent === name)!;
        await act(async () => option.click()); await flush();
    }

    it('shows no question count in the graph although CT05 returns none', async () => {
        vi.mocked(getOwnedSkillGraph).mockResolvedValue(graphOf());
        await mountApp(`/teach/courses/${courseId}/skills`);
        const node = document.querySelector<HTMLElement>('[aria-label^="Variables,"]')!;
        expect(node.getAttribute('aria-label')).toBe('Variables, 0 prerequisites');
        expect(document.querySelector('[aria-label^="Loops,"]')!.getAttribute('aria-label')).toBe('Loops, 1 prerequisite');
        expect(text()).not.toMatch(/\d+ questions?/);
    });

    it('runs edge additions first, so a cycle refusal saves nothing, keeps the draft and re-reads the graph', async () => {
        vi.mocked(getOwnedSkillGraph).mockResolvedValue(graphOf());
        vi.mocked(addCoursePrerequisite).mockRejectedValue(http(409, 'The request conflicts with existing information.'));
        await mountApp(`/teach/courses/${courseId}/skills`);
        await openSkill('Variables');
        await type('Skill name', 'Variables renamed');
        await addPrerequisite('Loops');
        await click('Save skill');
        expect(addCoursePrerequisite).toHaveBeenCalledWith(courseId, variables.id, loops.id, expect.anything());
        expect(updateCourseSkill).not.toHaveBeenCalled();
        expect(text()).toContain('Nothing was saved. Loops can\'t be a prerequisite of Variables renamed, because it would create a cycle of prerequisites. 1 other change was not attempted. Your edits are still here.');
        expect(text()).not.toContain('Skill saved.');
        expect(field('Skill name').value).toBe('Variables renamed');
        expect(getOwnedSkillGraph).toHaveBeenCalledTimes(2);
    });

    it('names a committed step when a later step fails', async () => {
        vi.mocked(getOwnedSkillGraph).mockResolvedValue(graphOf({ prerequisites: [] }));
        vi.mocked(addCoursePrerequisite).mockResolvedValue(undefined);
        vi.mocked(updateCourseSkill).mockRejectedValue(http(400, 'Please check the supplied information.'));
        await mountApp(`/teach/courses/${courseId}/skills`);
        await openSkill('Variables');
        await type('Skill name', 'Variables renamed');
        await addPrerequisite('Loops');
        await click('Save skill');
        expect(text()).toContain("Saved: Loops as a prerequisite. The name and description couldn't be saved. Your edits are still here.");
        expect(text()).not.toContain('Skill saved.');
    });

    it('keeps a created skill bound to the form when its prerequisite is refused, so saving again edits instead of duplicating', async () => {
        const arraysSkill = { id: '00000000-0000-4000-8000-0000000000a4', name: 'Arrays', description: null };
        vi.mocked(getOwnedSkillGraph).mockResolvedValueOnce(graphOf()).mockResolvedValue(graphOf({ skills: [loops, variables, arraysSkill] }));
        vi.mocked(createCourseSkill).mockResolvedValue(arraysSkill);
        vi.mocked(addCoursePrerequisite).mockRejectedValueOnce(http(404, 'The requested resource is unavailable.')).mockResolvedValue(undefined);
        await mountApp(`/teach/courses/${courseId}/skills`);
        await click('Add skill');
        await type('Skill name', 'Arrays');
        await addPrerequisite('Variables');
        await act(async () => find('Add skill', document.querySelector('form')!).click()); await settle();
        expect(createCourseSkill).toHaveBeenCalledTimes(1);
        expect(text()).toContain("Saved: the skill Arrays. Variables couldn't be added as a prerequisite. A skill it needs is no longer available. Your edits are still here.");
        expect(field('Skill name').value).toBe('Arrays');

        await click('Save skill');
        expect(createCourseSkill).toHaveBeenCalledTimes(1);
        expect(addCoursePrerequisite).toHaveBeenLastCalledWith(courseId, arraysSkill.id, variables.id, expect.anything());
        expect(text()).toContain('Skill saved.');
    });
});

describe('N5: shell navigation asks before an editor with unsaved edits is left', () => {
    it('asks on Teaching from a dirty assignment editor; Stay keeps the route and draft, Leave navigates once', async () => {
        vi.mocked(getOwnedSkillGraph).mockResolvedValue(graphOf());
        vi.mocked(readOwnedAssignment).mockResolvedValue(assignment());
        await mountApp(`/teach/courses/${courseId}/assignments/${assignmentId}`);
        await type('Assignment title', 'Unsaved title');
        await act(async () => navLink('Teaching').click()); await flush();
        expect(confirmDialog('Leave without saving?')).toBeTruthy();
        expect(location).toBe(`/teach/courses/${courseId}/assignments/${assignmentId}`);
        await click('Stay', confirmDialog('Leave without saving?'));
        expect(location).toBe(`/teach/courses/${courseId}/assignments/${assignmentId}`);
        expect(field('Assignment title').value).toBe('Unsaved title');

        await act(async () => navLink('Teaching').click()); await flush();
        expect(dialogs().filter((item) => item.textContent?.includes('Leave without saving?'))).toHaveLength(1);
        await click('Leave', confirmDialog('Leave without saving?'));
        expect(location).toBe('/teach');
    });

    it('asks on the home link from a dirty quiz editor, and not at all once nothing is unsaved', async () => {
        vi.mocked(getOwnedSkillGraph).mockResolvedValue(graphOf());
        vi.mocked(readOwnedQuiz).mockResolvedValue(quiz());
        vi.mocked(replaceUnusedHiddenQuiz).mockResolvedValue(quiz({ title: 'Renamed quiz' }));
        await mountApp(`/teach/courses/${courseId}/quizzes/${quizId}`);
        await type('Quiz title', 'Renamed quiz');
        await act(async () => navLink('CodeMastery home').click()); await flush();
        expect(confirmDialog('Leave without saving?')).toBeTruthy();
        await click('Stay', confirmDialog('Leave without saving?'));
        await click('Save changes');
        await act(async () => navLink('Catalogue').click()); await flush();
        expect(dialogs().some((item) => item.textContent?.includes('Leave without saving?'))).toBe(false);
        expect(location).toBe('/courses');
    });
});

describe('N6: keyboard focus stays in the lesson row after mapping and unmapping', () => {
    it('returns focus to the row instead of <body>', async () => {
        vi.mocked(getOwnedSkillGraph).mockResolvedValueOnce(graphOf())
            .mockResolvedValueOnce(graphOf({ mappings: [{ lessonId: lessonOneId, skillId: loops.id }] }))
            .mockResolvedValue(graphOf());
        vi.mocked(mapCourseLessonSkill).mockResolvedValue(undefined);
        vi.mocked(unmapCourseLessonSkill).mockResolvedValue(undefined);
        await mountApp(`/teach/courses/${courseId}/skills`);
        await click('Lesson mapping');
        const row = () => [...document.querySelectorAll<HTMLElement>('li')].find((item) => item.querySelector('[data-mapping-title]')?.textContent === 'For Loops')!;
        await act(async () => find('Add skill', row()).click()); await flush();
        const option = [...document.querySelectorAll<HTMLElement>('[role="menuitem"]')].find((item) => item.textContent === 'Loops')!;
        await act(async () => option.click()); await settle();
        expect(mapCourseLessonSkill).toHaveBeenCalledWith(courseId, lessonOneId, loops.id, expect.anything());
        expect(document.activeElement).not.toBe(document.body);
        expect(row().contains(document.activeElement)).toBe(true);

        await act(async () => find('Remove Loops from For Loops', row()).click()); await settle();
        expect(unmapCourseLessonSkill).toHaveBeenCalledTimes(1);
        expect(row().contains(document.activeElement)).toBe(true);
        expect(document.activeElement?.textContent).toContain('Add skill');
    });
});

describe('N10: assignment instants are compared to the microsecond', () => {
    it('distinguishes sub-millisecond differences and accepts equivalent representations', () => {
        expect(sameInstant('2026-12-01T10:00:00.123456Z', '2026-12-01T10:00:00.123999Z')).toBe(false);
        expect(sameInstant('2026-12-01T10:00:00.000400Z', '2026-12-01T10:00:00Z')).toBe(false);
        expect(sameInstant('2026-12-01T10:00:00.123456Z', '2026-12-01T17:00:00.1234560+07:00')).toBe(true);
        expect(sameInstant('2026-12-01T10:00:00Z', '2026-12-01t10:00:00.000z')).toBe(true);
        expect(sameInstant('2026-12-01T00:30:00-02:30', '2026-12-01T03:00:00Z')).toBe(true);
        expect(instantMicros('2026-12-01T10:00:00.1234567Z')).toBeNull();
        expect(new Date(instantMicros('0050-01-01T00:00:00Z')!.seconds * 1000).getUTCFullYear()).toBe(50);
    });

    it('confirms an unknown-outcome save only when the deadline matches to the microsecond', () => {
        const draft = { title: 'Lab', instructions: 'Do it.', deadlineInput: '2026-12-01T17:00', lessonId: null };
        const baseline = { input: '2026-12-01T17:00', iso: '2026-12-01T10:00:00.123456Z' };
        const current = assignment({ title: 'Lab', instructions: 'Do it.', lessonId: null, deadlineAt: '2026-12-01T10:00:00.123456Z' });
        expect(sameAssignment(draft, baseline, current)).toBe(true);
        expect(sameAssignment(draft, baseline, { ...current, deadlineAt: '2026-12-01T10:00:00.123999Z' })).toBe(false);
        expect(sameAssignment(draft, baseline, { ...current, deadlineAt: '2026-12-01T17:00:00.123456+07:00' })).toBe(true);
    });
});

// --- PR #273 ultrareview remediation regressions (U2–U5) ---

describe('U2: a failed background re-read keeps the course editor, its drafts and an explicit refresh failure', () => {
    it('keeps the Settings draft through a 500 re-read, says the copy is not current, and recovers with "Try again"', async () => {
        vi.mocked(getOwnedSkillGraph).mockResolvedValue(graphOf());
        vi.mocked(getOwnedCourse).mockResolvedValueOnce(ownedCourseFixture())
            .mockRejectedValueOnce(new ApiError('http', 'x', 500))
            .mockResolvedValueOnce(ownedCourseFixture({ publicationStatus: 'published' }));
        vi.mocked(setOwnedCoursePublication).mockResolvedValue(ownedCourseFixture({ publicationStatus: 'published' }));
        await mountApp(`/teach/courses/${courseId}/settings`);
        await type('Description', 'An unsaved description');
        const input = field('Description');

        // The publication change succeeds; the shell's re-read of the course fails with an unknown outcome.
        await click('Publish course');
        await click('Publish course', confirmDialog('Publish course?'));
        await settle();
        expect(getOwnedCourse).toHaveBeenCalledTimes(2);
        expect(text()).toContain("We couldn't load this course. The last loaded version is shown.");
        expect(document.querySelector('main h1')?.textContent).toBe('Python Foundations');
        expect(field('Description')).toBe(input);
        expect(field('Description').value).toBe('An unsaved description');
        expect(text()).toContain('Unsaved changes');
        // Nothing is retried on its own.
        await settle();
        expect(getOwnedCourse).toHaveBeenCalledTimes(2);

        await click('Try again');
        await settle();
        expect(getOwnedCourse).toHaveBeenCalledTimes(3);
        expect(text()).not.toContain('The last loaded version is shown.');
        expect(text()).toContain('Published');
        expect(field('Description').value).toBe('An unsaved description');
    });

    it('keeps the copy through a network failure but discards it on a 404 re-read (F14 state, no stale editor)', async () => {
        vi.mocked(getOwnedSkillGraph).mockResolvedValue(graphOf());
        vi.mocked(getOwnedCourse).mockResolvedValueOnce(ownedCourseFixture())
            .mockRejectedValueOnce(new ApiError('network', 'down'))
            .mockRejectedValueOnce(http(404, 'The requested resource is unavailable.'));
        vi.mocked(setOwnedCoursePublication).mockResolvedValue(ownedCourseFixture({ publicationStatus: 'published' }));
        await mountApp(`/teach/courses/${courseId}`);
        await click('Publish course');
        await click('Publish course', confirmDialog('Publish course?'));
        await settle();
        expect(text()).toContain('The last loaded version is shown.');
        expect(text()).toContain('Control Flow');

        await click('Try again');
        await settle();
        expect(text()).toContain('Course not available');
        expect(text()).not.toContain('Control Flow');
        expect(text()).not.toContain('Python Foundations');
    });

    it('keeps the open skill draft when the graph re-read after an unknown skill save fails', async () => {
        vi.mocked(getOwnedSkillGraph).mockResolvedValueOnce(graphOf()).mockRejectedValue(new ApiError('network', 'down'));
        vi.mocked(createCourseSkill).mockRejectedValue(new ApiError('network', 'down'));
        await mountApp(`/teach/courses/${courseId}/skills`);
        await click('Add skill');
        await type('Skill name', 'Arrays');
        await act(async () => find('Add skill', document.querySelector('form')!).click()); await settle();
        expect(getOwnedSkillGraph).toHaveBeenCalledTimes(2);
        expect(text()).toContain("Skills couldn't be loaded.");
        expect(field('Skill name').value).toBe('Arrays');
        expect(text()).toContain('Variables');
    });
});

describe('U3: Create course takes part in shell navigation', () => {
    it('asks once on Teaching and the home link; Stay keeps the draft, Leave navigates exactly once', async () => {
        await mountApp('/teach/courses/new');
        await type('Course title', 'Draft course');
        await act(async () => navLink('Teaching').click()); await flush();
        expect(dialogs().filter((item) => item.textContent?.includes('Leave without saving?'))).toHaveLength(1);
        await click('Stay', confirmDialog('Leave without saving?'));
        expect(location).toBe('/teach/courses/new');
        expect(field('Course title').value).toBe('Draft course');

        await act(async () => navLink('CodeMastery home').click()); await flush();
        expect(dialogs().filter((item) => item.textContent?.includes('Leave without saving?'))).toHaveLength(1);
        await click('Leave', confirmDialog('Leave without saving?'));
        expect(location).not.toBe('/teach/courses/new');
        expect(dialogs().some((item) => item.textContent?.includes('Leave without saving?'))).toBe(false);
    });

    it('asks on Catalogue, never once nothing is unsaved, and never blocks a successful creation', async () => {
        vi.mocked(createOwnedCourse).mockResolvedValue(ownedCourseFixture());
        vi.mocked(getOwnedSkillGraph).mockResolvedValue(graphOf());
        await mountApp('/teach/courses/new');
        await act(async () => navLink('Catalogue').click()); await flush();
        expect(location).toBe('/courses');

        await act(async () => root.unmount()); root = createRoot(host);
        await mountApp('/teach/courses/new');
        await type('Course title', 'New course');
        await act(async () => navLink('Catalogue').click()); await flush();
        expect(confirmDialog('Leave without saving?')).toBeTruthy();
        await click('Stay', confirmDialog('Leave without saving?'));
        await type('Description', 'Basics');
        await click('Create course');
        await settle();
        expect(location).toBe(`/teach/courses/${courseId}`);
        expect(dialogs().some((item) => item.textContent?.includes('Leave without saving?'))).toBe(false);
    });

    it('keeps the draft after an unknown creation outcome and still asks before leaving', async () => {
        vi.mocked(createOwnedCourse).mockRejectedValue(new ApiError('network', 'down'));
        await mountApp('/teach/courses/new');
        await type('Course title', 'Maybe created'); await type('Description', 'Basics');
        await click('Create course');
        expect(text()).toContain("We couldn't confirm whether the course was created.");
        expect(field('Course title').value).toBe('Maybe created');
        await act(async () => navLink('Teaching').click()); await flush();
        expect(confirmDialog('Leave without saving?')).toBeTruthy();
        await click('Leave', confirmDialog('Leave without saving?'));
        expect(location).toBe('/teach');
    });
});

describe('U4: deleting the only question on the last page moves to the last page the server reports', () => {
    it('reads the last remaining page once, keeps the notice, and shows the real empty state only when the bank is empty', async () => {
        const second = questionPage([q(21)], { page: 1, totalElements: 21, totalPages: 2 });
        const first = questionPage(Array.from({ length: 20 }, (_, index) => q(index + 1)), { totalElements: 21, totalPages: 2 });
        vi.mocked(listQuestions).mockImplementation(async (_course, page) => (page === 0 ? first : second));
        vi.mocked(readQuestion).mockResolvedValue(q(21));
        vi.mocked(deleteUnusedQuestion).mockResolvedValue(q(21));
        await mount(`/teach/courses/${courseId}/assessments`);
        await click('Go to page 2');
        expect(text()).toContain('Question 21');

        // After the deletion the server answers page 1 empty, with 20 questions on one page.
        const afterDelete = questionPage(Array.from({ length: 20 }, (_, index) => q(index + 1)), { totalElements: 20, totalPages: 1 });
        vi.mocked(listQuestions).mockImplementation(async (_course, page) => (page === 0 ? afterDelete
            : questionPage([], { page, totalElements: 20, totalPages: 1 })));
        await click('Edit question: Question 21'); await click('Delete question', dialog());
        await click('Delete question', confirmDialog('Delete this question?'));
        await settle();
        expect(text()).toContain('Question deleted.');
        expect(text()).not.toContain('No questions yet');
        expect(text()).toContain('Question 20');
        const pages = vi.mocked(listQuestions).mock.calls.map((call) => call[1]);
        expect(pages.slice(-2)).toEqual([1, 0]);
        await settle();
        expect(vi.mocked(listQuestions).mock.calls.map((call) => call[1]).slice(-2)).toEqual([1, 0]);
    });

    it('shows "No questions yet" when the last question of the whole bank is deleted on page 0', async () => {
        vi.mocked(listQuestions).mockResolvedValueOnce(questionPage([q(1)])).mockResolvedValue(questionPage([]));
        vi.mocked(readQuestion).mockResolvedValue(q(1));
        vi.mocked(deleteUnusedQuestion).mockResolvedValue(q(1));
        await mount(`/teach/courses/${courseId}/assessments`);
        await click('Edit question: Question 1'); await click('Delete question', dialog());
        await click('Delete question', confirmDialog('Delete this question?'));
        await settle();
        expect(text()).toContain('No questions yet');
        expect(vi.mocked(listQuestions).mock.calls.map((call) => call[1])).toEqual([0, 0]);
    });
});

describe('U5: Course Settings keeps "Saved" and drafts across re-reads', () => {
    it.each(['course', 'session'] as const)('D1: discards a delayed save after a %s change', async (boundary) => {
        vi.mocked(getOwnedSkillGraph).mockResolvedValue(graphOf());
        let generation = 1;
        auth = { ...auth, isCurrent: (value) => value === generation };
        const nextId = boundary === 'course' ? '00000000-0000-4000-8000-0000000000c2' : courseId;
        const next = ownedCourseFixture({ id: nextId, title: 'New context' });
        vi.mocked(getOwnedCourse).mockResolvedValueOnce(ownedCourseFixture()).mockResolvedValue(next);
        let resolveSave!: (value: OwnedCourse) => void;
        vi.mocked(updateOwnedCourse).mockReturnValue(new Promise<OwnedCourse>((resolve) => { resolveSave = resolve; }));
        function SwitchCourse() {
            const navigate = useNavigate();
            return <button onClick={() => navigate(`/teach/courses/${nextId}/settings`)}>Switch course probe</button>;
        }
        const tree = () => <MemoryRouter initialEntries={[`/teach/courses/${courseId}/settings`]}>
            <AuthContext.Provider value={auth}><SwitchCourse /><Where /><AppRouter catalogue={<p>Catalogue stub</p>} /></AuthContext.Provider>
        </MemoryRouter>;
        await act(async () => root.render(tree()));
        await type('Course title', 'Old draft'); await click('Save changes');
        if (boundary === 'course') await click('Switch course probe');
        else {
            generation = 2;
            auth = { ...auth, generation, user: { ...auth.user!, id: arrays.id } };
            await act(async () => root.render(tree()));
        }
        await settle();
        expect(field('Course title').value).toBe('New context');
        expect(vi.mocked(updateOwnedCourse).mock.calls[0][2]?.signal?.aborted).toBe(true);
        await act(async () => resolveSave(ownedCourseFixture({ title: 'Old reply' })));
        expect(field('Course title').value).toBe('New context');
        expect(text()).not.toContain('Saved');
        expect(getOwnedCourse).toHaveBeenCalledTimes(2);
    });

    it('D1: reconciles a newer shared read that precedes a delayed save reply, without another request', async () => {
        vi.mocked(getOwnedSkillGraph).mockResolvedValue(graphOf());
        let resolveSave!: (value: OwnedCourse) => void;
        const pendingSave = new Promise<OwnedCourse>((resolve) => { resolveSave = resolve; });
        const saved = ownedCourseFixture({ title: 'My saved version' });
        const newer = ownedCourseFixture({ title: 'Newer external commit', publicationStatus: 'published' });
        vi.mocked(updateOwnedCourse).mockReturnValue(pendingSave);
        vi.mocked(getOwnedCourse).mockResolvedValueOnce(ownedCourseFixture()).mockResolvedValue(newer);
        vi.mocked(setOwnedCoursePublication).mockResolvedValue(newer);
        await mountApp(`/teach/courses/${courseId}/settings`);
        const input = field('Course title');
        await type('Course title', 'My saved version');
        await click('Save changes');
        expect(input.disabled).toBe(true);
        // A committed first; its reply is delayed. A later writer commits B, which this shared read sees.
        await click('Publish course');
        await click('Publish course', confirmDialog('Publish course?'));
        await settle();
        expect(document.querySelector('main h1')?.textContent).toBe('Newer external commit');
        await act(async () => resolveSave(saved));
        await settle();
        expect(field('Course title')).toBe(input);
        expect(input.value).toBe('Newer external commit');
        expect(text()).toContain('Saved');
        expect(getOwnedCourse).toHaveBeenCalledTimes(3);
        expect(updateOwnedCourse).toHaveBeenCalledTimes(1);
        await act(async () => navLink('Teaching').click()); await flush();
        expect(location).toBe('/teach');
        expect(confirmDialog('Leave without saving?')).toBeUndefined();
    });

    it('D1: keeps the acknowledged fields while the ordinary post-save read is pending or fails', async () => {
        vi.mocked(getOwnedSkillGraph).mockResolvedValue(graphOf());
        let rejectRead!: (error: unknown) => void;
        const pendingRead = new Promise<OwnedCourse>((_resolve, reject) => { rejectRead = reject; });
        const saved = ownedCourseFixture({ title: 'Acknowledged title' });
        vi.mocked(getOwnedCourse).mockResolvedValueOnce(ownedCourseFixture()).mockReturnValueOnce(pendingRead)
            .mockResolvedValueOnce({ ...saved });
        vi.mocked(updateOwnedCourse).mockResolvedValue(saved);
        await mountApp(`/teach/courses/${courseId}/settings`);
        const input = field('Course title');
        await type('Course title', 'Acknowledged title');
        await click('Save changes');
        expect(input.value).toBe('Acknowledged title');
        expect(text()).toContain('Saved');
        expect(document.querySelector('main h1')?.textContent).toBe('Python Foundations');
        await act(async () => rejectRead(new ApiError('http', 'unavailable', 503)));
        expect(input.value).toBe('Acknowledged title');
        expect(text()).toContain('The last loaded version is shown.');
        await click('Try again'); await settle();
        expect(field('Course title')).toBe(input);
        expect(input.value).toBe('Acknowledged title');
        expect(text()).toContain('Saved');
        expect(getOwnedCourse).toHaveBeenCalledTimes(3);
    });

    it('D1: does not revert to a newly read pre-save snapshot while the post-save read waits', async () => {
        vi.mocked(getOwnedSkillGraph).mockResolvedValue(graphOf());
        let resolveSave!: (value: OwnedCourse) => void, resolveRead!: (value: OwnedCourse) => void;
        vi.mocked(updateOwnedCourse).mockReturnValue(new Promise<OwnedCourse>((resolve) => { resolveSave = resolve; }));
        const published = ownedCourseFixture({ publicationStatus: 'published' });
        vi.mocked(setOwnedCoursePublication).mockResolvedValue(published);
        vi.mocked(getOwnedCourse).mockResolvedValueOnce(ownedCourseFixture()).mockResolvedValueOnce(published)
            .mockReturnValueOnce(new Promise<OwnedCourse>((resolve) => { resolveRead = resolve; }));
        await mountApp(`/teach/courses/${courseId}/settings`);
        await type('Course title', 'Acknowledged title'); await click('Save changes');
        await click('Publish course'); await click('Publish course', confirmDialog('Publish course?'));
        await act(async () => resolveSave(ownedCourseFixture({ title: 'Acknowledged title' })));
        expect(field('Course title').value).toBe('Acknowledged title');
        expect(text()).toContain('Saved');
        await act(async () => resolveRead(ownedCourseFixture({ title: 'Acknowledged title', publicationStatus: 'published' })));
        expect(field('Course title').value).toBe('Acknowledged title');
        expect(getOwnedCourse).toHaveBeenCalledTimes(3);
    });

    it('D1: adopts a fresh post-save read even when another writer restores the pre-save values', async () => {
        vi.mocked(getOwnedSkillGraph).mockResolvedValue(graphOf());
        const original = ownedCourseFixture();
        let resolveRead!: (value: OwnedCourse) => void;
        const pendingRead = new Promise<OwnedCourse>((resolve) => { resolveRead = resolve; });
        vi.mocked(getOwnedCourse).mockResolvedValueOnce(original).mockReturnValueOnce(pendingRead);
        vi.mocked(updateOwnedCourse).mockResolvedValue(ownedCourseFixture({ title: 'Acknowledged title' }));
        await mountApp(`/teach/courses/${courseId}/settings`);
        await type('Course title', 'Acknowledged title');
        await click('Save changes');
        expect(field('Course title').value).toBe('Acknowledged title');
        // A decoded GET is a new snapshot, even when its metadata equals an older read's metadata.
        await act(async () => resolveRead({ ...original }));
        expect(field('Course title').value).toBe(original.title);
        expect(text()).toContain('Saved');
    });

    it('D1: preserves a new dirty draft when the post-save read finishes', async () => {
        vi.mocked(getOwnedSkillGraph).mockResolvedValue(graphOf());
        let resolveRead!: (value: OwnedCourse) => void;
        const pendingRead = new Promise<OwnedCourse>((resolve) => { resolveRead = resolve; });
        vi.mocked(getOwnedCourse).mockResolvedValueOnce(ownedCourseFixture()).mockReturnValueOnce(pendingRead);
        vi.mocked(updateOwnedCourse).mockResolvedValue(ownedCourseFixture({ title: 'Saved title' }));
        await mountApp(`/teach/courses/${courseId}/settings`);
        await type('Course title', 'Saved title'); await click('Save changes');
        await type('Description', 'My next unsaved draft');
        await act(async () => resolveRead(ownedCourseFixture({ title: 'Other writer', description: 'Other description' })));
        expect(field('Course title').value).toBe('Saved title');
        expect(field('Description').value).toBe('My next unsaved draft');
        expect(text()).toContain('Unsaved changes');
        await act(async () => navLink('Teaching').click()); await flush();
        expect(confirmDialog('Leave without saving?')).toBeTruthy();
        await click('Stay', confirmDialog('Leave without saving?'));
        expect(field('Description').value).toBe('My next unsaved draft');
    });

    it('keeps "Saved" and the mounted form after the post-save re-read returns the new metadata', async () => {
        vi.mocked(getOwnedSkillGraph).mockResolvedValue(graphOf());
        const saved = ownedCourseFixture({ title: 'Python Basics', description: 'Variables, loops and functions.' });
        vi.mocked(getOwnedCourse).mockResolvedValueOnce(ownedCourseFixture()).mockResolvedValue(saved);
        vi.mocked(updateOwnedCourse).mockResolvedValue(saved);
        await mountApp(`/teach/courses/${courseId}/settings`);
        const input = field('Course title');
        await type('Course title', '  Python Basics  ');
        await click('Save changes');
        await settle();
        expect(getOwnedCourse).toHaveBeenCalledTimes(2);
        expect(document.querySelector('main h1')?.textContent).toBe('Python Basics');
        expect(field('Course title')).toBe(input);
        expect(field('Course title').value).toBe('Python Basics');
        expect(text()).toContain('Saved');
        expect(text()).not.toContain('Unsaved changes');
    });

    it('never reports "Saved" for an unknown outcome and keeps the draft over a newer committed copy', async () => {
        vi.mocked(getOwnedSkillGraph).mockResolvedValue(graphOf());
        vi.mocked(getOwnedCourse).mockResolvedValueOnce(ownedCourseFixture())
            .mockResolvedValue(ownedCourseFixture({ title: 'Renamed elsewhere' }));
        vi.mocked(updateOwnedCourse).mockRejectedValue(new ApiError('network', 'down'));
        await mountApp(`/teach/courses/${courseId}/settings`);
        await type('Course title', 'My draft title');
        await click('Save changes');
        await settle();
        expect(text()).toContain("We couldn't confirm whether your changes were saved.");
        expect(text()).not.toMatch(/(^|[^d ])Saved/);
        expect(document.querySelector('main h1')?.textContent).toBe('Renamed elsewhere');
        expect(field('Course title').value).toBe('My draft title');
        expect(text()).toContain('Unsaved changes');
    });

    it('takes over another writer\'s committed metadata only while the form is clean', async () => {
        vi.mocked(getOwnedSkillGraph).mockResolvedValue(graphOf());
        vi.mocked(getOwnedCourse).mockResolvedValueOnce(ownedCourseFixture())
            .mockResolvedValue(ownedCourseFixture({ publicationStatus: 'published', description: 'Changed by another tab.' }));
        vi.mocked(setOwnedCoursePublication).mockResolvedValue(ownedCourseFixture({ publicationStatus: 'published' }));
        await mountApp(`/teach/courses/${courseId}/settings`);
        await click('Publish course');
        await click('Publish course', confirmDialog('Publish course?'));
        await settle();
        expect(field('Description').value).toBe('Changed by another tab.');
        expect(text()).not.toContain('Unsaved changes');
    });
});
