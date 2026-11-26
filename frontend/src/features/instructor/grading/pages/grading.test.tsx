// @vitest-environment jsdom
import { act } from 'react';
import { createRoot } from 'react-dom/client';
import type { Root } from 'react-dom/client';
import { MemoryRouter, Route, Routes } from 'react-router';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { ThemeProvider } from '@mui/material';
import { appTheme } from '../../../../app/theme/theme';
import { AuthContext } from '../../../../app/providers/AuthProvider';
import type { AuthContextValue } from '../../../../app/providers/AuthProvider';
import { ApiError } from '../../../../shared/types/apiTypes';
import * as api from '../api/gradingApi';
import { gradingPath, readReviewedState, reviewedState } from '../hooks/useSubmissions';
import { ProductionGradeSubmissionPage, gradeErrors } from './GradeSubmissionPage';
import { ProductionSubmissionListPage } from './SubmissionListPage';
import { readOwnedAssignment, listOwnedAssignments } from '../../course-management/api/assessmentAuthoringApi';
import { listOwnedCourses } from '../../course-management/api/instructorCourseApi';
vi.mock('../api/gradingApi', async original => ({ ...await original<typeof import('../api/gradingApi')>(), findSubmission: vi.fn(), listSubmissions: vi.fn(), gradeSubmission: vi.fn(), downloadSubmission: vi.fn() }));
vi.mock('../../course-management/api/assessmentAuthoringApi', async original => ({ ...await original<typeof import('../../course-management/api/assessmentAuthoringApi')>(), readOwnedAssignment: vi.fn(), listOwnedAssignments: vi.fn() }));
vi.mock('../../course-management/api/instructorCourseApi', async original => ({ ...await original<typeof import('../../course-management/api/instructorCourseApi')>(), listOwnedCourses: vi.fn() }));
Object.assign(globalThis, { IS_REACT_ACT_ENVIRONMENT: true });
const c = '00000000-0000-4000-8000-000000000001', a = '00000000-0000-4000-8000-000000000002', s = '00000000-0000-4000-8000-000000000003';
const row = { id: s, assignmentId: a, learnerId: c, learnerDisplayName: 'Student', originalFilename: 'work.zip', submittedAt: '2026-11-20T09:15:02.123456Z', graded: false, gradePercentage: null, feedback: null, gradedAt: null };
const assignment = { id: a, courseId: c, title: 'Lab', instructions: 'Submit', lessonId: null, deadlineAt: '2026-12-01T00:00:00Z', publicationStatus: 'published' as const, submissionCount: 3, gradedCount: 1 };
const page = <T,>(content: T[]) => ({ content, page: 0, size: 20, totalElements: content.length, totalPages: content.length ? 1 : 0 });
let root: Root, host: HTMLDivElement, auth: AuthContextValue;
beforeEach(() => {
    host = document.createElement('div'); document.body.append(host); root = createRoot(host);
    auth = { user: { id: c, displayName: 'Teacher', role: 'Instructor' }, isAuthenticated: true, generation: 1, restoring: false, isCurrent: g => g === auth.generation, login: vi.fn(), register: vi.fn(), logout: vi.fn(), rememberProfile: vi.fn(), startPath: '/teach', consumeReturn: () => '/teach' };
    vi.mocked(api.findSubmission).mockResolvedValue({ ...row }); vi.mocked(api.listSubmissions).mockResolvedValue(page([{ ...row }]));
    vi.mocked(readOwnedAssignment).mockResolvedValue(assignment); vi.mocked(listOwnedAssignments).mockResolvedValue(page([assignment]));
    vi.mocked(listOwnedCourses).mockResolvedValue(page([{ id: c, title: 'Course', description: 'Course', publicationStatus: 'published' }]));
    vi.stubGlobal('ResizeObserver', class { observe() {} unobserve() {} disconnect() {} });
    URL.createObjectURL = vi.fn(() => 'blob:synthetic'); URL.revokeObjectURL = vi.fn();
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {});
    vi.mocked(api.downloadSubmission).mockResolvedValue({ blob: new Blob(['PK']), filename: 'work.zip' });
});
afterEach(async () => { await act(async () => root.unmount()); host.remove(); document.body.innerHTML = ''; vi.restoreAllMocks(); vi.resetAllMocks(); vi.unstubAllGlobals(); });
async function mount(path = gradingPath(c,a,s), state?: unknown) {
    await act(async () => root.render(<ThemeProvider theme={appTheme}><MemoryRouter initialEntries={[{ pathname: path.split('?')[0], search: path.includes('?') ? `?${path.split('?')[1]}` : '', state }]}><AuthContext.Provider value={auth}><Routes><Route path="/teach/grading" element={<ProductionSubmissionListPage />} /><Route path="/teach/grading/:submissionId" element={<ProductionGradeSubmissionPage />} /></Routes></AuthContext.Provider></MemoryRouter></ThemeProvider>));
}
const text = () => document.body.textContent ?? '';
const button = (label: string) => Array.from(document.querySelectorAll('button')).find(b => b.textContent === label && !b.closest('[role="dialog"]'))!;
const click = async (label: string) => { await act(async () => button(label).click()); };
const field = (label: string) => { const l = Array.from(document.querySelectorAll('label')).find(e => e.textContent?.startsWith(label))!; return document.getElementById(l.htmlFor) as HTMLInputElement; };
async function input(label: string, value: string) { await act(async () => { const e = field(label); Object.getOwnPropertyDescriptor(e.tagName === 'TEXTAREA' ? HTMLTextAreaElement.prototype : HTMLInputElement.prototype, 'value')!.set!.call(e, value); e.dispatchEvent(new Event('input', { bubbles: true })); }); }
async function submit() { await act(async () => document.querySelector('form')!.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }))); }
async function draft() { await input('Grade', '85.25'); await input('Feedback', '  Keep\n going. '); }
async function confirm() { await submit(); expect(text()).toContain('The learner will see this grade and feedback on the assignment page.'); await act(async () => (document.querySelector('[role="dialog"] button:last-child') as HTMLButtonElement).click()); }
it.each(['', '-1', '100.01', '1.234', '1e2', '0x10'])('rejects invalid decimal input %s', grade => expect(gradeErrors({ grade, feedback: 'ok' }).grade).toBeTruthy());
it.each(['0', '0.29', '100.00'])('accepts valid decimal %s', grade => expect(gradeErrors({ grade, feedback: 'ok' })).toEqual({}));
it('requires nonblank feedback', () => expect(gradeErrors({ grade: '10', feedback: ' \n' }).feedback).toBeTruthy());
it('loads a direct link, reads its title and sends the exact token and feedback once', async () => {
    vi.mocked(api.gradeSubmission).mockResolvedValue({ ...row, graded: true, gradePercentage: 85.25, feedback: '  Keep\n going. ', gradedAt: '2026-11-20T10:00:00Z' });
    await mount(); expect(api.findSubmission).toHaveBeenCalledOnce(); expect(readOwnedAssignment).toHaveBeenCalledOnce(); expect(text()).toContain('Lab');
    await draft(); await confirm(); expect(api.gradeSubmission).toHaveBeenCalledOnce(); expect(vi.mocked(api.gradeSubmission).mock.calls[0][3]).toEqual({ gradePercentage: 85.25, feedback: '  Keep\n going. ', submittedAt: row.submittedAt }); expect(text()).toContain('Grade saved.'); expect(button('Update grade')).toBeTruthy();
});
it('prefills regrading and never promises notifications', async () => {
    vi.mocked(api.findSubmission).mockResolvedValue({ ...row, graded: true, gradePercentage: 0, feedback: 'Original', gradedAt: '2026-11-20T10:00:00Z' });
    await mount(); expect(field('Grade').value).toBe('0'); expect(field('Feedback').value).toBe('Original'); expect(button('Update grade')).toBeTruthy(); expect(text()).not.toMatch(/notif/i);
});
it('accepts only a current-document context with matching session and three identities', async () => {
    const state = reviewedState({ generation: 1, courseId:c, assignmentId:a, row, title:'Context lab' });
    expect(readReviewedState({ gradingContext:'retained-history' },1,c,a,s)).toBeNull();
    expect(readReviewedState(state,2,c,a,s)).toBeNull(); expect(readReviewedState(state,1,s,a,s)).toBeNull(); expect(readReviewedState(state,1,c,a,c)).toBeNull();
    await mount(gradingPath(c,a,s), state); expect(api.findSubmission).not.toHaveBeenCalled(); expect(readOwnedAssignment).not.toHaveBeenCalled(); expect(text()).toContain('Context lab');
});
it('ignores retained or foreign navigation state and scans instead', async () => { await mount(gradingPath(c,a,s), { row, generation:1, gradingContext:'old-document' }); expect(api.findSubmission).toHaveBeenCalledOnce(); });
it('treats an unsuccessful scan as unknown, with explicit retry and no grading', async () => {
    vi.mocked(api.findSubmission).mockResolvedValueOnce(null); await mount(); expect(text()).toContain("We couldn't find this submission in the current list."); expect(text()).toContain('Back to submissions'); expect(document.querySelector('form')).toBeNull(); await click('Try again'); expect(api.findSubmission).toHaveBeenCalledTimes(2);
});
it.each([`/teach/grading/${s}`, `/teach/grading/${s}?courseId=${c}`, `/teach/grading/${s}?courseId=bad&assignmentId=${a}`, `/teach/grading/bad?courseId=${c}&assignmentId=${a}`, `/teach/grading/${s}?courseId=${c}&courseId=${c}&assignmentId=${a}`])('refuses invalid identity %s without requests', async path => { await mount(path); expect(text()).toContain("We can't open this submission from this link."); expect(api.findSubmission).not.toHaveBeenCalled(); });
it('retains draft after 409, refreshes row, and requires reviewing its download before another deliberate save', async () => {
    vi.mocked(api.gradeSubmission).mockRejectedValue(new ApiError('http','conflict',409));
    vi.mocked(api.findSubmission).mockResolvedValueOnce(row).mockResolvedValue({ ...row, submittedAt:'2026-11-20T09:15:02.123457Z', originalFilename:'new.zip' });
    await mount(); await draft(); await confirm();
    expect(text()).toContain('The learner replaced the file after you opened it. Review the new file before grading.'); expect(api.findSubmission).toHaveBeenCalledTimes(2);
    expect(field('Grade').value).toBe('85.25'); expect(field('Feedback').value).toBe('  Keep\n going. '); expect(button('Save grade').disabled).toBe(true);
    expect(button('I have reviewed the new file').disabled).toBe(true); await click('Download'); expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:synthetic'); await click('I have reviewed the new file'); expect(button('Save grade').disabled).toBe(false); expect(api.gradeSubmission).toHaveBeenCalledOnce();
});
it('keeps draft and locks unknown outcome until explicit current-result review, with no replay', async () => {
    vi.mocked(api.gradeSubmission).mockRejectedValue(new ApiError('network','lost'));
    await mount(); await draft(); await confirm(); expect(text()).toContain("We couldn't confirm whether your grade was saved."); expect(field('Feedback').value).toBe('  Keep\n going. '); expect(button('Save grade').disabled).toBe(true); expect(api.gradeSubmission).toHaveBeenCalledOnce(); await click('I have reviewed the current result'); expect(button('Save grade').disabled).toBe(false);
});
it('keeps draft locked when conflict refresh fails; retry is a read', async () => {
    vi.mocked(api.gradeSubmission).mockRejectedValue(new ApiError('http','conflict',409)); vi.mocked(api.findSubmission).mockResolvedValueOnce(row).mockRejectedValue(new ApiError('network','lost'));
    await mount(); await draft(); await confirm(); expect(field('Grade').value).toBe('85.25'); expect(button('Save grade').disabled).toBe(true); await click('Try again'); expect(api.gradeSubmission).toHaveBeenCalledOnce();
});
it('lists assignments and unfiltered submissions, deriving To grade from producer counts', async () => {
    await mount(gradingPath(c,a)); expect(text()).toContain('To grade'); expect(text()).toContain('Student'); expect(text()).toContain('work.zip'); expect(text()).not.toContain('Ungraded'); expect(listOwnedCourses).toHaveBeenCalled(); expect(listOwnedAssignments).toHaveBeenCalled(); expect(api.listSubmissions).toHaveBeenCalledWith(c,a,0,20,expect.objectContaining({signal:expect.any(AbortSignal)}));
});
it('does not publish an abandoned direct-link response', async () => {
    let resolve!: (v: typeof row) => void; vi.mocked(api.findSubmission).mockImplementation(() => new Promise(r => { resolve = r; })); await mount(); expect(text()).toContain('Loading submission…'); const signal = vi.mocked(api.findSubmission).mock.calls[0][3]!.signal!; await act(async () => root.unmount()); expect(signal.aborted).toBe(true); await act(async () => resolve(row)); root = createRoot(host); expect(host.textContent).toBe('');
});
it('shows an error summary, focuses the first invalid field, and associates its message', async () => {
    await mount(); await submit(); expect(text()).toContain('Check the grade and feedback fields below.'); expect(document.activeElement).toBe(field('Grade')); expect(field('Grade').getAttribute('aria-invalid')).toBe('true'); expect(field('Grade').getAttribute('aria-describedby')).toBeTruthy(); expect(api.gradeSubmission).not.toHaveBeenCalled();
});
it('download failure never creates an object URL or enables replacement review', async () => {
    vi.mocked(api.downloadSubmission).mockRejectedValue(new ApiError('http','unavailable',404));
    await mount(); await click('Download'); expect(text()).toContain('The download could not be completed. Try again.'); expect(URL.createObjectURL).not.toHaveBeenCalled(); expect(api.gradeSubmission).not.toHaveBeenCalled();
});
it('keeps validation refusal editable without losing the draft or replaying', async () => {
    vi.mocked(api.gradeSubmission).mockRejectedValue(new ApiError('http','invalid',400)); await mount(); await draft(); await confirm(); expect(text()).toContain('The draft has been kept.'); expect(field('Feedback').value).toBe('  Keep\n going. '); expect(button('Save grade').disabled).toBe(false); expect(api.gradeSubmission).toHaveBeenCalledOnce();
});
it.each([403,404])('refuses grade access after HTTP %s and hides the form', async status => {
    vi.mocked(api.gradeSubmission).mockRejectedValue(new ApiError('http','refused',status)); await mount(); await draft(); await confirm(); expect(document.querySelector('form')).toBeNull(); expect(text()).toContain('Back to submissions'); expect(api.gradeSubmission).toHaveBeenCalledOnce();
});
it('single-flights a pending save and ignores its late result after session replacement', async () => {
    let resolve!: (v: import('../types/gradingTypes').SubmissionSummary) => void;
    vi.mocked(api.gradeSubmission).mockImplementation(() => new Promise(r => { resolve = r; }));
    await mount(); await draft(); await confirm(); expect(button('Saving grade…').disabled).toBe(true); await submit(); expect(api.gradeSubmission).toHaveBeenCalledOnce();
    const signal = vi.mocked(api.gradeSubmission).mock.calls[0][4]!.signal!;
    auth = { ...auth, generation:2, user:{id:s, displayName:'Other instructor',role:'Instructor'} };
    vi.mocked(api.findSubmission).mockResolvedValue({ ...row, learnerDisplayName:'Current session learner' });
    await mount(); expect(signal.aborted).toBe(true); expect(field('Feedback').value).toBe('');
    await act(async () => resolve({ ...row, graded:true,gradePercentage:85.25,feedback:'Late prior session',gradedAt:'2026-11-20T10:00:00Z' }));
    expect(text()).not.toContain('Grade saved.'); expect(text()).not.toContain('Late prior session'); expect(text()).toContain('Current session learner');
});
it('treats changed learner/file identity in a mutation response as unconfirmed, then reads current evidence', async () => {
    vi.mocked(api.gradeSubmission).mockResolvedValue({ ...row, learnerId:s, originalFilename:'other.zip', graded:true,gradePercentage:85.25,feedback:'  Keep\n going. ',gradedAt:'2026-11-20T10:00:00Z' });
    await mount(); await draft(); await confirm(); expect(text()).toContain("We couldn't confirm whether your grade was saved."); expect(text()).not.toContain('Grade saved.'); expect(api.findSubmission).toHaveBeenCalledTimes(2);
});
it('does not label an unsuccessful submission read as an empty assignment', async () => {
    vi.mocked(api.listSubmissions).mockRejectedValue(new ApiError('network','lost')); await mount(gradingPath(c,a)); expect(text()).toContain("Submissions couldn't be loaded."); expect(text()).not.toContain('No submissions yet');
});
it('uses the Assignment title fallback when its direct read fails', async () => { vi.mocked(readOwnedAssignment).mockRejectedValue(new ApiError('network','lost')); await mount(); expect(host.querySelector('h1')?.textContent).toBe('Assignment'); expect(field('Grade')).toBeTruthy(); });
