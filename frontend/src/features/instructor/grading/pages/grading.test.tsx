// @vitest-environment jsdom
import { act, useLayoutEffect } from 'react';
import { createRoot } from 'react-dom/client';
import type { Root } from 'react-dom/client';
import { MemoryRouter, Route, Routes, useNavigate } from 'react-router';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { ThemeProvider } from '@mui/material';
import { appTheme } from '../../../../app/theme/theme';
import { AuthContext } from '../../../../app/providers/AuthProvider';
import type { AuthContextValue } from '../../../../app/providers/AuthProvider';
import { ApiError } from '../../../../shared/types/apiTypes';
import * as api from '../api/gradingApi';
import { gradingPath, readReviewedState, reviewedState, useReviewedSubmission } from '../hooks/useSubmissions';
import { ProductionGradeSubmissionPage, gradeErrors } from './GradeSubmissionPage';
import { ProductionSubmissionListPage } from './SubmissionListPage';
import { readOwnedAssignment, listOwnedAssignments } from '../../course-management/api/assessmentAuthoringApi';
import { getOwnedCourse, listOwnedCourses } from '../../course-management/api/instructorCourseApi';
vi.mock('../api/gradingApi', async original => ({ ...await original<typeof import('../api/gradingApi')>(), findSubmission: vi.fn(), listSubmissions: vi.fn(), gradeSubmission: vi.fn(), downloadSubmission: vi.fn() }));
vi.mock('../../course-management/api/assessmentAuthoringApi', async original => ({ ...await original<typeof import('../../course-management/api/assessmentAuthoringApi')>(), readOwnedAssignment: vi.fn(), listOwnedAssignments: vi.fn() }));
vi.mock('../../course-management/api/instructorCourseApi', async original => ({ ...await original<typeof import('../../course-management/api/instructorCourseApi')>(), listOwnedCourses: vi.fn(), getOwnedCourse: vi.fn() }));
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
    vi.mocked(getOwnedCourse).mockResolvedValue({ id:c,title:'Selected course',description:'Course',publicationStatus:'published',prerequisiteDescription:null,chapters:[] });
    vi.stubGlobal('ResizeObserver', class { observe() {} unobserve() {} disconnect() {} });
    URL.createObjectURL = vi.fn(() => 'blob:synthetic'); URL.revokeObjectURL = vi.fn();
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {});
    vi.mocked(api.downloadSubmission).mockResolvedValue({ blob: new Blob(['PK']), filename: 'work.zip' });
});
afterEach(async () => { await act(async () => root.unmount()); host.remove(); document.body.innerHTML = ''; vi.restoreAllMocks(); vi.resetAllMocks(); vi.unstubAllGlobals(); });
let navigate: ReturnType<typeof useNavigate>;
function NavigationProbe() { navigate = useNavigate(); return null; }
async function mount(path = gradingPath(c,a,s), state?: unknown) {
    await act(async () => root.render(<ThemeProvider theme={appTheme}><MemoryRouter initialEntries={[{ pathname: path.split('?')[0], search: path.includes('?') ? `?${path.split('?')[1]}` : '', state }]}><AuthContext.Provider value={auth}><NavigationProbe /><Routes><Route path="/teach/grading" element={<ProductionSubmissionListPage />} /><Route path="/teach/grading/:submissionId" element={<ProductionGradeSubmissionPage />} /></Routes></AuthContext.Provider></MemoryRouter></ThemeProvider>));
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

// Permanent controls adapted from the preceding independent probes; author execution below.
it('IR01 control: committed unknown outcome displays current saved result, retains draft, and never replays',async()=>{
 vi.mocked(api.findSubmission).mockResolvedValueOnce({...row}).mockResolvedValue({...row,graded:true,gradePercentage:85.25,feedback:'  Keep\n going. ',gradedAt:'2026-11-20T10:00:00Z'});
 vi.mocked(api.gradeSubmission).mockRejectedValue(new ApiError('network','lost'));
 await mount();await draft();await confirm();expect(text()).toContain('Current saved grade: 85.25 / 100');expect(api.gradeSubmission).toHaveBeenCalledOnce();await click('I have reviewed the current result');expect(api.gradeSubmission).toHaveBeenCalledOnce();expect(button('Update grade').disabled).toBe(false);
});
it('RT03 control: successive 409 replaces B with C and invalidates previous download acknowledgement',async()=>{
 const b={...row,submittedAt:'2026-11-20T09:15:02.123457Z',originalFilename:'B.zip'},cc={...row,submittedAt:'2026-11-20T09:15:02.123458Z',originalFilename:'C.zip'};
 vi.mocked(api.findSubmission).mockResolvedValueOnce({...row}).mockResolvedValueOnce({...b}).mockResolvedValue({...cc});
 vi.mocked(api.gradeSubmission).mockRejectedValue(new ApiError('http','changed',409));
 await mount();await draft();await confirm();await click('Download');await click('I have reviewed the new file');await confirm();
 expect(button('I have reviewed the new file').disabled).toBe(true);expect(button('Save grade').disabled).toBe(true);
 await click('Download');await click('I have reviewed the new file');await confirm();
 expect(vi.mocked(api.gradeSubmission).mock.calls.map(x=>x[3].submittedAt)).toEqual([row.submittedAt,b.submittedAt,cc.submittedAt]);
});
it('RT04 control: a late protected download after unmount publishes no object URL',async()=>{
 let done!:(v:{blob:Blob;filename:string})=>void; vi.mocked(api.downloadSubmission).mockImplementation(()=>new Promise(r=>{done=r}));
 await mount();await click('Download');const signal=vi.mocked(api.downloadSubmission).mock.calls[0][3]!.signal!;await act(async()=>root.unmount());expect(signal.aborted).toBe(true);await act(async()=>done({blob:new Blob(['PK']),filename:'late.zip'}));expect(URL.createObjectURL).not.toHaveBeenCalled();root=createRoot(host);
});

it('RT02 control: backend serves C while metadata B; acknowledging download only sends stale B and 409, never succeeds',async()=>{
 const b={...row,submittedAt:'2026-11-20T09:15:02.123457Z'},cc={...row,submittedAt:'2026-11-20T09:15:02.123458Z'};
 vi.mocked(api.findSubmission).mockResolvedValueOnce({...row}).mockResolvedValueOnce({...b}).mockResolvedValue({...cc});
 vi.mocked(api.gradeSubmission).mockRejectedValue(new ApiError('http','stale',409));
 vi.mocked(api.downloadSubmission).mockResolvedValue({blob:new Blob(['actual C']),filename:'work.zip'});
 await mount();await draft();await confirm();await click('Download');await click('I have reviewed the new file');await confirm();
 expect(vi.mocked(api.gradeSubmission).mock.calls[1][3].submittedAt).toBe(b.submittedAt);expect(text()).not.toContain('Grade saved.');expect(button('I have reviewed the new file').disabled).toBe(true);
});
it('RT03 control: pending B download cannot unlock C after 409 refresh',async()=>{
 const b={...row,submittedAt:'2026-11-20T09:15:02.123457Z'},cc={...row,submittedAt:'2026-11-20T09:15:02.123458Z'};
 vi.mocked(api.findSubmission).mockResolvedValueOnce({...row}).mockResolvedValueOnce({...b}).mockResolvedValue({...cc});vi.mocked(api.gradeSubmission).mockRejectedValue(new ApiError('http','stale',409));
 await mount();await draft();await confirm();await click('Download');await click('I have reviewed the new file');
 let done!:(v:{blob:Blob;filename:string})=>void;vi.mocked(api.downloadSubmission).mockImplementation(()=>new Promise(r=>{done=r}));await click('Download');const signal=vi.mocked(api.downloadSubmission).mock.calls.at(-1)![3]!.signal!;
 await confirm();expect(signal.aborted).toBe(true);await act(async()=>done({blob:new Blob(['B']),filename:'work.zip'}));expect(button('I have reviewed the new file').disabled).toBe(true);expect(button('Save grade').disabled).toBe(true);
});
it('RT04 control: confirmed PUT resolving after route unmount is cancelled',async()=>{
 let done!:(v:import('../types/gradingTypes').SubmissionSummary)=>void;vi.mocked(api.gradeSubmission).mockImplementation(()=>new Promise(r=>{done=r}));await mount();await draft();await confirm();const signal=vi.mocked(api.gradeSubmission).mock.calls[0][4]!.signal!;await act(async()=>root.unmount());expect(signal.aborted).toBe(true);await act(async()=>done({...row,graded:true,gradePercentage:85.25,feedback:'  Keep\n going. ',gradedAt:'2026-11-20T10:00:00Z'}));expect(host.textContent).toBe('');root=createRoot(host);
});
it('RT05 control: canonical uppercase UUID route maps to lower scoped request',async()=>{
 const uppercase=gradingPath(c.toUpperCase(),a.toUpperCase(),s.toUpperCase());await mount(uppercase);expect(api.findSubmission).toHaveBeenCalledWith(c,a,s,expect.anything());
});

const replacement = (n: number, name = row.originalFilename) => ({ ...row, submittedAt: `2026-11-20T09:15:02.12345${n}Z`, originalFilename: name });
const savedRow = (r = row) => ({ ...r, graded:true, gradePercentage:85.25, feedback:'  Keep\n going. ', gradedAt:'2026-11-20T10:00:00Z' });
const failure = (kind: string) => kind === 'server' ? new ApiError('http','unknown',500) : new ApiError(kind as 'network' | 'protocol','unknown');
it.each(['network','server','protocol'].flatMap(kind => ['work.zip','different.zip'].map(name => [kind,name])))('IR01: %s unknown then %s replacement needs current download AND review before exact B PUT', async (kind,name) => {
    const b = replacement(7,name);
    vi.mocked(api.findSubmission).mockResolvedValueOnce(row).mockResolvedValue(b);
    vi.mocked(api.gradeSubmission).mockRejectedValueOnce(failure(kind)).mockResolvedValue(savedRow(b));
    await mount(); await click('Download'); await draft(); await confirm();
    expect(text()).toContain("We couldn't confirm whether your grade was saved.");
    expect(text()).toContain('Current result: not graded.'); expect(text()).not.toContain('Grade saved.');
    expect(button('I have reviewed the new file').disabled).toBe(true);
    await submit(); await click('I have reviewed the new file'); expect(api.gradeSubmission).toHaveBeenCalledOnce();
    expect(field('Feedback').value).toBe('  Keep\n going. '); expect(field('Grade').value).toBe('85.25');
    await click('Download'); expect(button('Save grade').disabled).toBe(true); expect(api.gradeSubmission).toHaveBeenCalledOnce();
    await click('I have reviewed the new file'); await confirm();
    expect(vi.mocked(api.gradeSubmission).mock.calls.map(call => call[3].submittedAt)).toEqual([row.submittedAt,b.submittedAt]);
    expect(text()).toContain('Grade saved.'); expect(api.downloadSubmission).toHaveBeenCalledTimes(2);
});
it('IR01: repeated unknown outcomes compose with successive A→B→C replacements and retain the draft', async () => {
    const b=replacement(7), cRow=replacement(8);
    vi.mocked(api.findSubmission).mockResolvedValueOnce(row).mockResolvedValueOnce(b).mockResolvedValue(cRow);
    vi.mocked(api.gradeSubmission).mockRejectedValueOnce(failure('network')).mockRejectedValueOnce(failure('server')).mockResolvedValue(savedRow(cRow));
    await mount(); await draft(); await confirm(); await click('Download'); await click('I have reviewed the new file'); await confirm();
    expect(button('I have reviewed the new file').disabled).toBe(true); expect(button('Save grade').disabled).toBe(true);
    expect(field('Feedback').value).toBe('  Keep\n going. '); expect(api.gradeSubmission).toHaveBeenCalledTimes(2);
    await click('Download'); await click('I have reviewed the new file'); await confirm();
    expect(vi.mocked(api.gradeSubmission).mock.calls.map(call=>call[3].submittedAt)).toEqual([row.submittedAt,b.submittedAt,cRow.submittedAt]);
    expect(text()).toContain('Grade saved.');
});
it('IR01: a replacement during pending recovery cannot be acknowledged before its read or download', async () => {
    let done!: (r: typeof row) => void;
    vi.mocked(api.findSubmission).mockResolvedValueOnce(row).mockImplementationOnce(()=>new Promise(r=>{done=r;}));
    vi.mocked(api.gradeSubmission).mockRejectedValue(failure('network'));
    await mount(); await draft(); await confirm(); expect(button('Save grade').disabled).toBe(true);
    expect(button('I have reviewed the current result')).toBeUndefined(); await submit(); expect(api.gradeSubmission).toHaveBeenCalledOnce();
    await act(async()=>done(replacement(7))); expect(button('I have reviewed the new file').disabled).toBe(true);
});
it('IR01: failed recovery and unknown lookup stay locked; read-only retries cannot promote B', async () => {
    vi.mocked(api.findSubmission).mockResolvedValueOnce(row).mockRejectedValueOnce(failure('network')).mockResolvedValueOnce(null).mockResolvedValue(replacement(7));
    vi.mocked(api.gradeSubmission).mockRejectedValue(failure('network'));
    await mount(); await draft(); await confirm(); expect(button('Save grade').disabled).toBe(true);
    await click('Try again'); expect(text()).toContain('Submission lookup incomplete'); expect(button('Save grade').disabled).toBe(true);
    await click('Try again'); expect(button('I have reviewed the new file').disabled).toBe(true); expect(api.gradeSubmission).toHaveBeenCalledOnce();
    expect(field('Feedback').value).toBe('  Keep\n going. ');
});
it.each(['failed','cancelled'])('IR01: %s replacement download never unlocks acknowledgement', async kind => {
    vi.mocked(api.findSubmission).mockResolvedValueOnce(row).mockResolvedValue(replacement(7));
    vi.mocked(api.gradeSubmission).mockRejectedValue(failure('network'));
    vi.mocked(api.downloadSubmission).mockRejectedValue(new ApiError(kind==='cancelled'?'cancelled':'network','download unavailable'));
    await mount(); await draft(); await confirm(); await click('Download');
    expect(button('I have reviewed the new file').disabled).toBe(true); expect(button('Save grade').disabled).toBe(true); expect(api.gradeSubmission).toHaveBeenCalledOnce();
});
it('IR01: unchanged-token unknown recovery retains explicit result review and deliberate save', async () => {
    vi.mocked(api.gradeSubmission).mockRejectedValueOnce(failure('network')).mockResolvedValue(savedRow());
    await mount(); await draft(); await confirm(); expect(button('I have reviewed the current result').disabled).toBe(false);
    await click('I have reviewed the current result'); expect(api.gradeSubmission).toHaveBeenCalledOnce(); await confirm();
    expect(vi.mocked(api.gradeSubmission).mock.calls[1][3].submittedAt).toBe(row.submittedAt); expect(text()).toContain('Grade saved.');
});
it('IR01: a pending replacement download cannot settle into a new session', async () => {
    let done!: (v:{blob:Blob;filename:string})=>void;
    vi.mocked(api.findSubmission).mockResolvedValueOnce(row).mockResolvedValue(replacement(7)); vi.mocked(api.gradeSubmission).mockRejectedValue(failure('network'));
    await mount(); await draft(); await confirm(); vi.mocked(api.downloadSubmission).mockImplementation(()=>new Promise(r=>{done=r;})); await click('Download');
    const signal=vi.mocked(api.downloadSubmission).mock.calls[0][3]!.signal!;
    auth={...auth,generation:2}; await mount(); expect(signal.aborted).toBe(true);
    await act(async()=>done({blob:new Blob(['late']),filename:'work.zip'})); expect(URL.createObjectURL).not.toHaveBeenCalled(); expect(field('Feedback').value).toBe('');
});
it('IR03: one external submit action retains native form association, validation and confirmation', async () => {
    await mount(); const save=button('Save grade'); expect(save.form).toBe(document.querySelector('form'));
    expect(document.querySelectorAll('button[type="submit"]')).toHaveLength(1);
    await click('Save grade'); expect(document.activeElement).toBe(field('Grade'));
    await draft(); await click('Save grade'); expect(document.querySelector('[role="dialog"]')).not.toBeNull(); expect(api.gradeSubmission).not.toHaveBeenCalled();
});
const otherCourse='00000000-0000-4000-8000-000000000004', otherAssignment='00000000-0000-4000-8000-000000000005';
const outsideCourses=Array.from({length:20},(_,i)=>({id:`00000000-0000-4000-8002-${String(i).padStart(12,'0')}`,title:`Other course ${i}`,description:'Course',publicationStatus:'published' as const}));
const outsideAssignments=Array.from({length:20},(_,i)=>({...assignment,id:`00000000-0000-4000-8005-${String(i).padStart(12,'0')}`,title:`Other assignment ${i}`}));
const windowPage=<T,>(content:T[],page=0)=>({content,page,size:20,totalElements:21,totalPages:2});
function outsideSelection() {
    vi.mocked(listOwnedCourses).mockImplementation(async (page=0)=>windowPage(page ? [{id:c,title:'Selected course',description:'Course',publicationStatus:'published'}] : outsideCourses,page));
    vi.mocked(listOwnedAssignments).mockImplementation(async (_course,page=0)=>windowPage(page ? [assignment] : outsideAssignments,page));
}
async function namedButton(label:string) { await act(async()=>Array.from(document.querySelectorAll('button')).filter(b=>b.getAttribute('aria-label')==='Go to next page')[label.endsWith('courses')?0:1]!.click()); }
it('IR02: direct later-page Course and Assignment recover from bounded owner reads with exact scope', async () => {
    outsideSelection(); await mount(gradingPath(c,a));
    expect(document.querySelector('[role="combobox"]')?.textContent).toBe('Selected course');
    expect(document.querySelector('section h2')?.textContent).toBe('Lab');
    expect(getOwnedCourse).toHaveBeenCalledExactlyOnceWith(c,expect.objectContaining({signal:expect.any(AbortSignal)}));
    expect(readOwnedAssignment).toHaveBeenCalledExactlyOnceWith(c,a,expect.objectContaining({signal:expect.any(AbortSignal)}));
    expect(api.listSubmissions).toHaveBeenCalledWith(c,a,0,20,expect.anything());
    await namedButton('Next page of courses'); await namedButton('Next page of assignments');
    expect(document.querySelector('[role="combobox"]')?.textContent).toBe('Selected course'); expect(document.querySelector('section h2')?.textContent).toBe('Lab');
    expect(getOwnedCourse).toHaveBeenCalledOnce(); expect(readOwnedAssignment).toHaveBeenCalledOnce();
});
it('IR02: visible selected summaries survive paging away without owner-tree reads', async () => {
    vi.mocked(listOwnedCourses).mockResolvedValueOnce(windowPage([{id:c,title:'Course',description:'Course',publicationStatus:'published'},...outsideCourses.slice(0,19)])).mockResolvedValue(windowPage(outsideCourses.slice(-1),1));
    vi.mocked(listOwnedAssignments).mockResolvedValueOnce(windowPage([assignment,...outsideAssignments.slice(0,19)])).mockResolvedValue(windowPage(outsideAssignments.slice(-1),1));
    await mount(gradingPath(c,a)); await namedButton('Next page of courses'); await namedButton('Next page of assignments');
    expect(document.querySelector('[role="combobox"]')?.textContent).toBe('Course'); expect(document.querySelector('section h2')?.textContent).toBe('Lab');
    expect(getOwnedCourse).not.toHaveBeenCalled(); expect(readOwnedAssignment).not.toHaveBeenCalled();
});
it.each(['course','assignment'])('IR02: obsolete pending %s owner read cannot overwrite a new URL selection', async resource => {
    outsideSelection(); let done!: (v:any)=>void;
    if(resource==='course') vi.mocked(getOwnedCourse).mockImplementationOnce(()=>new Promise(r=>{done=r;}));
    else vi.mocked(readOwnedAssignment).mockImplementationOnce(()=>new Promise(r=>{done=r;}));
    await mount(gradingPath(c,a));
    vi.mocked(getOwnedCourse).mockResolvedValue({id:otherCourse,title:'New course',description:'Course',publicationStatus:'published',prerequisiteDescription:null,chapters:[]});
    vi.mocked(readOwnedAssignment).mockResolvedValue({...assignment,id:otherAssignment,courseId:otherCourse,title:'New lab'});
    await act(async()=>navigate(gradingPath(otherCourse,otherAssignment)));
    await act(async()=>done(resource==='course'?{id:c,title:'OBSOLETE',description:'Course',publicationStatus:'published',prerequisiteDescription:null,chapters:[]}:assignment));
    expect(document.querySelector('[role="combobox"]')?.textContent).toBe('New course'); expect(document.querySelector('section h2')?.textContent).toBe('New lab'); expect(text()).not.toContain('OBSOLETE');
    expect(api.listSubmissions).toHaveBeenLastCalledWith(otherCourse,otherAssignment,0,20,expect.anything());
});
it('IR02: failed selected read stays explicit, retries only that read and never shows another Assignment title', async () => {
    outsideSelection(); vi.mocked(readOwnedAssignment).mockRejectedValueOnce(failure('network')).mockResolvedValue(assignment);
    await mount(gradingPath(c,a)); expect(text()).toContain("Selected assignment couldn't be loaded."); expect(document.querySelector('section')).toBeNull();
    await click('Try again'); expect(document.querySelector('section h2')?.textContent).toBe('Lab'); expect(readOwnedAssignment).toHaveBeenCalledTimes(2); expect(getOwnedCourse).toHaveBeenCalledOnce();
});
it.each([403,404])('IR02: selected owner-read HTTP %s hides scoped submissions', async status => {
    outsideSelection(); vi.mocked(getOwnedCourse).mockRejectedValue(new ApiError('http','refused',status)); await mount(gradingPath(c,a));
    expect(document.querySelector('section')).toBeNull(); expect(text()).not.toContain('Student');
});
it('IR02: generation replacement discards selected summaries and aborts pending owner reads', async () => {
    outsideSelection(); let done!:(v:any)=>void; vi.mocked(getOwnedCourse).mockImplementationOnce(()=>new Promise(r=>{done=r;}));
    await mount(gradingPath(c,a)); const signal=vi.mocked(getOwnedCourse).mock.calls[0][1]!.signal!; auth={...auth,generation:2};
    vi.mocked(getOwnedCourse).mockResolvedValue({id:c,title:'Current session course',description:'Course',publicationStatus:'published',prerequisiteDescription:null,chapters:[]});
    await mount(gradingPath(c,a)); expect(signal.aborted).toBe(true); await act(async()=>done({id:c,title:'Old session title'}));
    expect(document.querySelector('[role="combobox"]')?.textContent).toBe('Current session course'); expect(text()).not.toContain('Old session title');
});

it('IR01: every recovery revision withdraws authoritative readiness before effects start a repeated read', async () => {
    let reload!: () => void, done!: (r: typeof row) => void;
    const observations: Array<{ loading:boolean; data:unknown }> = [];
    function ReadProbe() {
        const read = useReviewedSubmission(c,a,s,null); reload=read.reload;
        useLayoutEffect(()=>{observations.push({loading:read.loading,data:read.data});});
        return null;
    }
    await act(async()=>root.render(<AuthContext.Provider value={auth}><ReadProbe /></AuthContext.Provider>));
    expect(observations.at(-1)?.data).toEqual(row);
    for(const value of [replacement(7),replacement(8)]) {
        vi.mocked(api.findSubmission).mockImplementationOnce(()=>new Promise(r=>{done=r;})); observations.length=0;
        await act(async()=>reload());
        expect(observations.length).toBeGreaterThan(0); expect(observations.every(o=>o.loading && o.data===null)).toBe(true);
        await act(async()=>done(value)); expect(observations.at(-1)?.data).toEqual(value);
    }
    expect(api.findSubmission).toHaveBeenCalledTimes(3); expect(api.gradeSubmission).not.toHaveBeenCalled();
});
it('IR02: changing only Assignment while its summary is pending preserves Course and ignores stale title', async () => {
    outsideSelection(); let done!:(v: typeof assignment)=>void;
    vi.mocked(readOwnedAssignment).mockImplementationOnce(()=>new Promise(r=>{done=r;}));
    await mount(gradingPath(c,a)); const signal=vi.mocked(readOwnedAssignment).mock.calls[0][2]!.signal!;
    vi.mocked(readOwnedAssignment).mockResolvedValue({...assignment,id:otherAssignment,title:'New selected assignment'});
    await act(async()=>navigate(gradingPath(c,otherAssignment))); expect(signal.aborted).toBe(true);
    await act(async()=>done({...assignment,title:'OBSOLETE assignment'}));
    expect(document.querySelector('[role="combobox"]')?.textContent).toBe('Selected course'); expect(document.querySelector('section h2')?.textContent).toBe('New selected assignment');
    expect(text()).not.toContain('OBSOLETE assignment'); expect(getOwnedCourse).toHaveBeenCalledOnce();
    expect(api.listSubmissions).toHaveBeenLastCalledWith(c,otherAssignment,0,20,expect.anything());
});
