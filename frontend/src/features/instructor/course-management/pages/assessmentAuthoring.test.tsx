// @vitest-environment jsdom
import { act } from 'react';
import { createRoot } from 'react-dom/client';
import type { Root } from 'react-dom/client';
import { MemoryRouter, Route, Routes, useLocation, useParams } from 'react-router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { AuthContext } from '../../../../app/providers/AuthProvider';
import type { AuthContextValue } from '../../../../app/providers/AuthProvider';
import { ApiError } from '../../../../shared/types/apiTypes';
import {
    createQuestion, createQuiz, deleteUnusedHiddenQuiz, deleteUnusedQuestion, listOwnedQuizzes, listQuestions, readOwnedQuiz, readQuestion,
    replaceUnusedHiddenQuiz, replaceUnusedQuestion, setQuizPublication,
} from '../api/assessmentAuthoringApi';
import type { B3Question, B3QuestionPage, B3QuizOwned, B3QuizOwnedPage } from '../api/assessmentAuthoringApi';
import { AssessmentsPage, formatTimeLimit } from './QuestionBankPage';
import { QuizEditor, difficultySummary, timeLimitSeconds } from './QuizEditorPage';
import type { SkillSource } from '../components/QuestionForm';
import { AppRouter } from '../../../../routes';
import { getOwnedSkillGraph } from '../../skill-graph-editor/api/skillGraphApi';

vi.mock('../api/assessmentAuthoringApi', async (original) => ({
    ...await original<object>(), listQuestions: vi.fn(), createQuestion: vi.fn(), readQuestion: vi.fn(), replaceUnusedQuestion: vi.fn(), deleteUnusedQuestion: vi.fn(),
    listOwnedQuizzes: vi.fn(), createQuiz: vi.fn(), readOwnedQuiz: vi.fn(), replaceUnusedHiddenQuiz: vi.fn(), deleteUnusedHiddenQuiz: vi.fn(), setQuizPublication: vi.fn(),
}));
vi.mock('../../skill-graph-editor/api/skillGraphApi', () => ({ getOwnedSkillGraph: vi.fn() }));
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
const http = (status: number, message = 'The request conflicts with existing information.') => new ApiError('http', message, status);

let host: HTMLDivElement, root: Root, auth: AuthContextValue, skills: SkillSource, location = '';
beforeEach(() => {
    host = document.createElement('div'); document.body.append(host); root = createRoot(host);
    auth = { user: { id: loops.id, displayName: 'Instructor', role: 'Instructor' }, generation: 1, isAuthenticated: true, restoring: false,
        isCurrent: (generation) => generation === 1, login: vi.fn(), register: vi.fn(), logout: vi.fn(), rememberProfile: vi.fn(), consumeReturn: () => '/courses' };
    skills = { status: 'ready', skills: [loops, arrays], reload: vi.fn() };
    vi.mocked(listQuestions).mockResolvedValue(questionPage([q(2), q(1, { skillId: arrays.id, difficulty: 'hard', kind: 'true_false', options: ['True', 'False'] })]));
    vi.mocked(listOwnedQuizzes).mockResolvedValue(quizPage([quiz(), quiz({ id: '00000000-0000-4000-8000-0000000000f2', title: 'Final test', kind: 'test', published: true, timeLimitSeconds: 600 })]));
});
afterEach(async () => { await act(async () => root.unmount()); host.remove(); document.body.innerHTML = ''; vi.resetAllMocks(); });

function Where() { const value = useLocation(); location = `${value.pathname}${value.search}`; return null; }
async function mount(path: string) {
    await act(async () => root.render(<MemoryRouter initialEntries={[path]}><AuthContext.Provider value={auth}><Where /><Routes>
        <Route path="/teach/courses/:courseId/assessments" element={<AssessmentsPage courseId={courseId} skills={skills} />} />
        <Route path="/teach/courses/:courseId/quizzes/:quizId" element={<QuizRoute />} />
    </Routes></AuthContext.Provider></MemoryRouter>));
}
function QuizRoute() { const id = useParams().quizId!; return <QuizEditor key={id} courseId={courseId} quizId={id} skills={skills} />; }
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
    const labelNode = [...scope.querySelectorAll('label')].find((item) => item.textContent?.replace('*', '').trim() === label);
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
    const labelNode = [...scope.querySelectorAll('.MuiInputLabel-root')].find((item) => item.textContent?.replace('*', '').trim() === label)!;
    const combo = scope.querySelector<HTMLElement>(`[role="combobox"][aria-labelledby~="${labelNode.id}"]`)!;
    await act(async () => { combo.dispatchEvent(new MouseEvent('mousedown', { bubbles: true, button: 0 })); });
    const item = [...document.querySelectorAll<HTMLElement>('[role="option"]')].find((node) => node.textContent?.startsWith(option))!;
    await act(async () => item.click()); await flush();
}
const text = () => document.body.textContent ?? '';

describe('Assessments page (S19, D12-2)', () => {
    it('opens Questions for a missing or unknown view and shows rows in API order with graph skill names', async () => {
        await mount(`/teach/courses/${courseId}/assessments?view=assignments`);
        expect(document.querySelector('h1')?.textContent).toBe('Assessments');
        expect(listQuestions).toHaveBeenCalledWith(courseId, 0, 20, expect.objectContaining({ signal: expect.any(AbortSignal) }));
        const rows = [...document.querySelectorAll('tbody tr')].map((row) => row.textContent);
        expect(rows[0]).toContain('Question 2'); expect(rows[0]).toContain('Loops'); expect(rows[0]).toContain('Single choice');
        expect(rows[1]).toContain('Question 1'); expect(rows[1]).toContain('Arrays'); expect(rows[1]).toContain('True/false'); expect(rows[1]).toContain('Hard');
        expect(text()).not.toContain('Assignments'); expect(text()).not.toContain('Search');
        expect(listOwnedQuizzes).not.toHaveBeenCalled();
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

describe('production routes (CT16.routes)', () => {
    it('routes the Assessments page for Instructors and composes the skill graph read', async () => {
        vi.mocked(getOwnedSkillGraph).mockResolvedValue({ courseId, skills: [loops], prerequisites: [], mappings: [] });
        vi.mocked(listQuestions).mockResolvedValue(questionPage([q(1)]));
        await act(async () => root.render(<MemoryRouter initialEntries={[`/teach/courses/${courseId}/assessments`]}><AuthContext.Provider value={auth}><AppRouter /></AuthContext.Provider></MemoryRouter>));
        await flush();
        expect(document.querySelector('main h1')?.textContent).toBe('Assessments');
        expect(getOwnedSkillGraph).toHaveBeenCalledTimes(1); expect(listQuestions).toHaveBeenCalledTimes(1);
        expect([...document.querySelectorAll('tbody tr')][0].textContent).toContain('Loops');
    });
    it('denies other roles before any assessment request', async () => {
        auth = { ...auth, user: { id: loops.id, displayName: 'Learner', role: 'Learner' } };
        await act(async () => root.render(<MemoryRouter initialEntries={[`/teach/courses/${courseId}/quizzes/new`]}><AuthContext.Provider value={auth}><AppRouter /></AuthContext.Provider></MemoryRouter>));
        expect(text()).toContain("You don't have access to this page"); expect(getOwnedSkillGraph).not.toHaveBeenCalled(); expect(listQuestions).not.toHaveBeenCalled();
    });
});
