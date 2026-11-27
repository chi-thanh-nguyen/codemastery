// @vitest-environment jsdom
import { act, StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import type { Root } from 'react-dom/client';
import { MemoryRouter, Route, Routes, useNavigate } from 'react-router';
import { ThemeProvider } from '@mui/material';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { appTheme } from '../../../app/theme/theme';
import { AuthContext } from '../../../app/providers/AuthProvider';
import type { AuthContextValue } from '../../../app/providers/AuthProvider';
import { ApiError } from '../../../shared/types/apiTypes';
import * as api from '../api/assignmentApi';
import * as quiz from '../api/quizApi';
import { useAssignment } from '../hooks/useAssignment';
import { LiveAssignmentDetailPage, assignmentTime } from './AssignmentDetailPage';
import { LiveCourseAssessmentsPage } from './CourseAssessmentsPage';
vi.mock('../api/assignmentApi',async original=>({...await original<typeof import('../api/assignmentApi')>(),readLearnerAssignment:vi.fn(),submitAssignmentFile:vi.fn(),downloadOwnSubmission:vi.fn(),listLearnerAssignments:vi.fn()}));
vi.mock('../api/quizApi',()=>({listLearnerQuizzes:vi.fn()}));
Object.assign(globalThis,{IS_REACT_ACT_ENVIRONMENT:true});
const c='00000000-0000-4000-8000-000000000001',a='00000000-0000-4000-8000-000000000002',b='00000000-0000-4000-8000-000000000003';
const sub={originalFilename:'original.pdf',submittedAt:'2026-11-20T09:00:00.123456Z',graded:false,gradePercentage:null,feedback:null,gradedAt:null};
const detail:api.B5LearnerAssignment={id:a,courseId:c,lessonId:b,title:'Assignment lab',instructions:'# Read this\n\n**Strong** [bad](javascript:alert(1))',deadlineAt:'2099-12-01T00:00:00Z',open:true,submission:null};
const page=<T,>(content:T[],p=0,total=content.length)=>({content,page:p,size:20,totalElements:total,totalPages:Math.ceil(total/20)});
let host:HTMLDivElement,root:Root,auth:AuthContextValue,navigate:ReturnType<typeof useNavigate>,hook:ReturnType<typeof useAssignment>;
let generation=1;
beforeEach(()=>{
    generation=1;host=document.createElement('div');document.body.append(host);root=createRoot(host);
    auth={user:{id:c,displayName:'Learner',role:'Learner'},generation:1,isAuthenticated:true,restoring:false,isCurrent:g=>g===generation,login:vi.fn(),register:vi.fn(),logout:vi.fn(),rememberProfile:vi.fn(),startPath:'/home',consumeReturn:()=>'/home'};
    vi.mocked(api.readLearnerAssignment).mockResolvedValue({...detail});vi.mocked(api.submitAssignmentFile).mockResolvedValue({...sub});
    vi.mocked(api.downloadOwnSubmission).mockResolvedValue({blob:new Blob(['pdf']),filename:'server.pdf'});
    vi.mocked(api.listLearnerAssignments).mockResolvedValue(page([{id:a,courseId:c,lessonId:null,title:'Assignment lab',deadlineAt:detail.deadlineAt,open:true,submissionStatus:'none'}]));
    vi.mocked(quiz.listLearnerQuizzes).mockResolvedValue(page([]));
    vi.stubGlobal('ResizeObserver',class{observe(){}unobserve(){}disconnect(){}});URL.createObjectURL=vi.fn(()=> 'blob:synthetic');URL.revokeObjectURL=vi.fn();vi.spyOn(HTMLAnchorElement.prototype,'click').mockImplementation(()=>{});
});
afterEach(async()=>{await act(async()=>root.unmount());host.remove();document.body.innerHTML='';vi.restoreAllMocks();vi.resetAllMocks();vi.unstubAllGlobals();vi.useRealTimers();});
function Probe(){navigate=useNavigate();return null;}
function Hook({id=a}:{id?:string}){hook=useAssignment(c,id);return <span>{hook.phase}:{hook.assignment?.id}</span>;}
async function mount(path=`/courses/${c}/assignments/${a}`,probe=false,id=a){await act(async()=>root.render(<ThemeProvider theme={appTheme}><AuthContext.Provider value={auth}><MemoryRouter initialEntries={[path]}><Probe/>{probe?<Hook id={id}/>:<Routes><Route path="/courses/:courseId/assignments/:assignmentId" element={<LiveAssignmentDetailPage/>}/><Route path="/courses/:courseId/assessments" element={<LiveCourseAssessmentsPage/>}/></Routes>}</MemoryRouter></AuthContext.Provider></ThemeProvider>));}
const text=()=>document.body.textContent??'';
const button=(name:string)=>Array.from(document.querySelectorAll('button')).find(x=>x.textContent===name&&!x.closest('[role="dialog"]'))!;
async function click(name:string){await act(async()=>button(name).click());}
async function choose(name='new.pdf',bytes='%PDF-bytes'){await act(async()=>{const input=document.querySelector('input[type="file"]') as HTMLInputElement;Object.defineProperty(input,'files',{value:[new File([bytes],name)],configurable:true});input.dispatchEvent(new Event('change',{bubbles:true}));});}
const deferred=<T,>()=>{let resolve!:(v:T)=>void,reject!:(e:unknown)=>void;const promise=new Promise<T>((y,n)=>{resolve=y;reject=n;});return{promise,resolve,reject};};
it('renders shared Markdown, timezone, dead-destination fallback and no unsupported claims',async()=>{await mount();expect(document.querySelector('strong')?.textContent).toBe('Strong');expect(document.querySelector('a[href^="javascript:"]')).toBeNull();expect(text()).toContain(assignmentTime(detail.deadlineAt));expect(text()).toContain('Due in');expect(text()).toContain('Related lesson is not available yet.');expect(text()).not.toMatch(/mastery|count toward|notification/i);expect(document.querySelector('a[href$="/lessons/'+b+'"]')).toBeNull();});
it('first submission reloads authoritative state and sends once',async()=>{vi.mocked(api.readLearnerAssignment).mockResolvedValueOnce({...detail}).mockResolvedValue({...detail,submission:sub});await mount();await choose();expect(api.submitAssignmentFile).toHaveBeenCalledOnce();expect(api.readLearnerAssignment).toHaveBeenCalledTimes(2);expect(text()).toContain('original.pdf');expect(text()).toContain('File submitted.');});
it('replacement confirms current name/time, Cancel has focus and does not upload',async()=>{vi.mocked(api.readLearnerAssignment).mockResolvedValue({...detail,submission:sub});await mount();await choose();expect(text()).toContain('original.pdf');expect(text()).toContain('will be replaced.');expect(document.activeElement?.textContent).toBe('Cancel');await act(async()=>Array.from(document.querySelectorAll('[role="dialog"] button')).find(x=>x.textContent==='Cancel')!.dispatchEvent(new MouseEvent('click',{bubbles:true})));expect(api.submitAssignmentFile).not.toHaveBeenCalled();await choose();await act(async()=>(document.querySelector('[role="dialog"] button:last-child') as HTMLButtonElement).click());expect(api.submitAssignmentFile).toHaveBeenCalledOnce();});
it.each([false,true])('closed/graded page excludes mutations but keeps Download (%s)',async graded=>{vi.mocked(api.readLearnerAssignment).mockResolvedValue({...detail,open:graded,submission:graded?{...sub,graded:true,gradePercentage:85.25,feedback:'**Plain**\nNext.',gradedAt:sub.submittedAt}:sub});await mount();expect(document.querySelector('input[type="file"]')).toBeNull();expect(button('Download')).toBeTruthy();if(graded){expect(text()).toContain('85.25 / 100');expect(text()).toContain('**Plain**');expect(text()).toContain('can no longer be replaced.');}else expect(text()).toContain('The deadline has passed. Submissions are closed.');});
it('shared Download uses server filename and revokes object URL',async()=>{vi.mocked(api.readLearnerAssignment).mockResolvedValue({...detail,submission:sub,open:false});await mount();await click('Download');expect(api.downloadOwnSubmission).toHaveBeenCalledWith(c,a,expect.objectContaining({signal:expect.any(AbortSignal)}));expect(URL.createObjectURL).toHaveBeenCalledOnce();expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:synthetic');expect(document.querySelector('a[download]')).toBeNull();});
it('local file validation rejects invalid extension and empty bytes',async()=>{await mount();await choose('bad.exe');expect(text()).toContain('Choose a non-empty PDF or ZIP file.');await choose('empty.pdf','');expect(text()).toContain('Choose a non-empty PDF or ZIP file.');expect(api.submitAssignmentFile).not.toHaveBeenCalled();});
it.each([403,404,500])('read failure %s has safe recovery/access state',async status=>{vi.mocked(api.readLearnerAssignment).mockRejectedValue(new ApiError('http','Safe failure',status));await mount();expect(document.querySelector('input[type="file"]')).toBeNull();expect(text()).toContain(status===403?'Rejoin the course to see this assignment.':status===404?'Assignment not available':"Couldn't load assignment");});
it('unknown upload and failed reconciliation stay locked until deliberate read succeeds',async()=>{
    vi.mocked(api.submitAssignmentFile).mockRejectedValue(new ApiError('unknown-outcome','unknown',500));vi.mocked(api.readLearnerAssignment).mockResolvedValueOnce({...detail}).mockRejectedValueOnce(new ApiError('http','Read failed',503)).mockResolvedValue({...detail,submission:sub});
    await mount();await choose();expect(text()).toContain('outcome is unknown');expect(text()).toContain("Couldn't load assignment");expect(document.querySelector('input[type="file"]')).toBeNull();expect(api.submitAssignmentFile).toHaveBeenCalledOnce();await click('Try again');expect(text()).toContain('original.pdf');expect(api.submitAssignmentFile).toHaveBeenCalledOnce();
});
it.each([false,true])('409 reload resolves deadline/grading refusal (%s)',async graded=>{vi.mocked(api.submitAssignmentFile).mockRejectedValue(new ApiError('http','conflict',409));vi.mocked(api.readLearnerAssignment).mockResolvedValueOnce({...detail}).mockResolvedValue({...detail,open:graded,submission:graded?{...sub,graded:true,gradePercentage:0,feedback:'x',gradedAt:sub.submittedAt}:null});await mount();await choose();expect(text()).toContain(graded?'This submission has been graded and can no longer be replaced.':'The deadline has passed.');expect(document.querySelector('input[type="file"]')).toBeNull();});
it('locks synchronously against duplicate invocations and all mutation while reconciling',async()=>{
    const upload=deferred<api.B5LearnerSubmission>(),read=deferred<api.B5LearnerAssignment>();vi.mocked(api.submitAssignmentFile).mockReturnValue(upload.promise);await mount(undefined,true);
    let first!:Promise<void>;await act(async()=>{first=hook.submitFile(new File(['x'],'x.pdf'),null);await expect(hook.submitFile(new File(['y'],'y.pdf'),null)).rejects.toMatchObject({kind:'cancelled'});});expect(api.submitAssignmentFile).toHaveBeenCalledOnce();
    vi.mocked(api.readLearnerAssignment).mockReturnValue(read.promise);await act(async()=>upload.resolve(sub));expect(hook.phase).toBe('checking');expect(hook.canSubmit).toBe(false);await expect(hook.submitFile(new File(['y'],'y.pdf'),null)).rejects.toMatchObject({kind:'cancelled'});await act(async()=>{read.resolve({...detail,open:false});await first;});expect(hook.canSubmit).toBe(false);
});
it.each([false,true])('invocation blocks closed or graded state (%s)',async graded=>{vi.mocked(api.readLearnerAssignment).mockResolvedValue({...detail,open:graded,submission:graded?{...sub,graded:true,gradePercentage:0,feedback:'x',gradedAt:sub.submittedAt}:null});await mount(undefined,true);await expect(hook.submitFile(new File(['x'],'x.pdf'),null)).rejects.toMatchObject({kind:'cancelled'});expect(api.submitAssignmentFile).not.toHaveBeenCalled();});
it('replacement confirmation token cannot name a previous current file',async()=>{vi.mocked(api.readLearnerAssignment).mockResolvedValue({...detail,submission:sub});await mount(undefined,true);await expect(hook.submitFile(new File(['x'],'x.pdf'),'2020-01-01T00:00:00Z')).rejects.toMatchObject({kind:'cancelled'});expect(api.submitAssignmentFile).not.toHaveBeenCalled();});
it('session replacement blocks captured callbacks before React rerenders',async()=>{await mount(undefined,true);generation=2;await expect(hook.submitFile(new File(['x'],'x.pdf'),null)).rejects.toMatchObject({kind:'cancelled'});expect(api.submitAssignmentFile).not.toHaveBeenCalled();});
it('late old-route read cannot overwrite newer assignment',async()=>{const slow=deferred<api.B5LearnerAssignment>();vi.mocked(api.readLearnerAssignment).mockReturnValueOnce(slow.promise).mockResolvedValue({...detail,id:b,title:'New assignment'});await mount();await act(async()=>navigate(`/courses/${c}/assignments/${b}`));expect(text()).toContain('New assignment');await act(async()=>slow.resolve({...detail}));expect(text()).not.toContain('Assignment lab');expect(vi.mocked(api.readLearnerAssignment).mock.calls[0][2]?.signal?.aborted).toBe(true);});
it('route change during mutation cancels and discards settlement',async()=>{const slow=deferred<api.B5LearnerSubmission>();vi.mocked(api.submitAssignmentFile).mockReturnValue(slow.promise);await mount();await choose();await act(async()=>navigate(`/courses/${c}/assessments`));expect(vi.mocked(api.submitAssignmentFile).mock.calls[0][3]?.signal?.aborted).toBe(true);await act(async()=>slow.resolve(sub));expect(text()).toContain('Assessments');expect(text()).not.toContain('File submitted.');});
it('cancelled upload reconciles before another upload',async()=>{vi.mocked(api.submitAssignmentFile).mockImplementation((_c,_a,_f,o)=>new Promise((_yes,no)=>o?.signal?.addEventListener('abort',()=>no(new ApiError('cancelled','cancelled')))));await mount();await choose();await click('Cancel upload');expect(api.readLearnerAssignment).toHaveBeenCalledTimes(2);expect(text()).toContain('outcome is unknown');expect(api.submitAssignmentFile).toHaveBeenCalledOnce();});
it('a newer read failure cannot be undone by an older success',async()=>{const old=deferred<api.B5LearnerAssignment>();await mount(undefined,true);vi.mocked(api.readLearnerAssignment).mockReturnValueOnce(old.promise).mockRejectedValueOnce(new ApiError('http','new failure',503));let first!:Promise<void>;await act(async()=>{first=hook.refresh();});await act(async()=>hook.refresh());await act(async()=>{old.resolve(detail);await first;});expect(hook.phase).toBe('error');expect(hook.canSubmit).toBe(false);});
it('confirmation is withdrawn by authoritative deadline refresh',async()=>{vi.mocked(api.readLearnerAssignment).mockResolvedValueOnce({...detail,submission:sub}).mockResolvedValue({...detail,submission:sub,open:false});await mount();await choose();expect(document.querySelector('[role="dialog"]')).not.toBeNull();await click('Refresh assignment');expect(text()).toContain('Submissions are closed.');expect(api.submitAssignmentFile).not.toHaveBeenCalled();});
it('assignment statuses and separately paginated quiz/assignment reads',async()=>{
    vi.mocked(api.listLearnerAssignments).mockResolvedValueOnce(page([{id:a,courseId:c,lessonId:null,title:'First',deadlineAt:detail.deadlineAt,open:true,submissionStatus:'none'}],0,21)).mockResolvedValue(page([{id:b,courseId:c,lessonId:null,title:'Second',deadlineAt:detail.deadlineAt,open:false,submissionStatus:'graded'}],1,21));
    await mount(`/courses/${c}/assessments`);expect(text()).toContain('Not submitted');expect(text()).not.toContain('Last score');const next=document.querySelector('[aria-label="Go to next page"]') as HTMLButtonElement;await act(async()=>next.click());expect(api.listLearnerAssignments).toHaveBeenLastCalledWith(c,1,20,expect.any(Object));expect(text()).toContain('Graded');
    await click('Quizzes');expect(quiz.listLearnerQuizzes).toHaveBeenCalledWith(c,0,20,expect.any(Object));expect(text()).toContain('No quizzes published yet');await click('Assignments');expect(api.listLearnerAssignments).toHaveBeenLastCalledWith(c,1,20,expect.any(Object));expect(text()).toContain('Second');
});
it('assignment list error and empty state have deliberate recovery',async()=>{vi.mocked(api.listLearnerAssignments).mockRejectedValueOnce(new ApiError('http','safe',503)).mockResolvedValue(page([]));await mount(`/courses/${c}/assessments`);expect(text()).toContain("Couldn't load assignments");await click('Try again');expect(text()).toContain('No assignments published yet');});
it('deadline expiration during confirmation refreshes state without uploading',async()=>{
    vi.useFakeTimers();vi.setSystemTime(new Date('2026-11-20T09:00:00Z'));vi.mocked(api.readLearnerAssignment).mockResolvedValueOnce({...detail,deadlineAt:'2026-11-20T09:00:01Z',submission:sub}).mockResolvedValue({...detail,deadlineAt:'2026-11-20T09:00:01Z',open:false,submission:sub});
    await mount();await choose();await act(async()=>vi.advanceTimersByTimeAsync(1001));expect(text()).toContain('Submissions are closed.');expect(api.submitAssignmentFile).not.toHaveBeenCalled();
});
it('a replaced session discards old mutation success and aborts its signal',async()=>{
    const old=deferred<api.B5LearnerSubmission>();vi.mocked(api.submitAssignmentFile).mockReturnValue(old.promise);await mount();await choose();generation=2;auth={...auth,generation:2};await mount();await act(async()=>old.resolve(sub));expect(vi.mocked(api.submitAssignmentFile).mock.calls[0][3]?.signal?.aborted).toBe(true);expect(text()).not.toContain('File submitted.');
});
it.each([['none',false,'Closed'],['submitted',false,'Submitted'],['graded',false,'Graded']] as const)('list status %s with open %s renders %s',async(submissionStatus,open,label)=>{
    vi.mocked(api.listLearnerAssignments).mockResolvedValue(page([{id:a,courseId:c,lessonId:null,title:'Lab',deadlineAt:detail.deadlineAt,open,submissionStatus}]));await mount(`/courses/${c}/assessments`);expect(text()).toContain(label);expect(document.querySelector(`a[href="/courses/${c}/assignments/${a}"]`)).not.toBeNull();
});

it('keeps the current file action before grade content',async()=>{vi.mocked(api.readLearnerAssignment).mockResolvedValue({...detail,submission:{...sub,graded:true,gradePercentage:85.25,feedback:'Long feedback '.repeat(100),gradedAt:sub.submittedAt}});await mount();const submission=document.querySelector('[aria-labelledby="assignment-submission"]')!,grade=document.querySelector('[aria-labelledby="assignment-grade"]')!;expect(submission.compareDocumentPosition(grade)&Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();});
it('post-success read displays concurrent replacement/grading instead of the upload response',async()=>{vi.mocked(api.readLearnerAssignment).mockResolvedValueOnce({...detail}).mockResolvedValue({...detail,submission:{...sub,originalFilename:'concurrent.zip',graded:true,gradePercentage:42,feedback:'Latest',gradedAt:sub.submittedAt}});await mount();await choose();expect(text()).toContain('concurrent.zip');expect(text()).toContain('42 / 100');expect(document.querySelector('input[type="file"]')).toBeNull();});

// FE11-IR-01: faithful adaptation of the independent lifecycle-required suite.
// Keep the callback inside the same act scope as settlement: act must not commit first.
it.each(['closed','failure','replacement'] as const)('IR01 reviewer pre-commit refresh blocks obsolete callback: %s',async kind=>{
    await mount(undefined,true);const old=hook;
    if(kind==='failure')vi.mocked(api.readLearnerAssignment).mockRejectedValueOnce(new ApiError('http','Read failed',503));
    else vi.mocked(api.readLearnerAssignment).mockResolvedValueOnce(kind==='closed'?{...detail,open:false}:{...detail,submission:sub});
    vi.mocked(api.submitAssignmentFile).mockImplementation(()=>new Promise(()=>{}));
    await act(async()=>{
        await old.refresh();
        expect(host.textContent).toContain('ready:'); // presentation still contains the old evidence
        const attempt=old.submitFile(new File(['%PDF-x'],'x.pdf'),null);void attempt.catch(()=>{});
        await Promise.resolve();expect(api.submitAssignmentFile).not.toHaveBeenCalled();
        await expect(attempt).rejects.toMatchObject({kind:'cancelled'});
    });
    vi.mocked(api.readLearnerAssignment).mockResolvedValue({...detail});await act(async()=>hook.refresh());
    vi.mocked(api.submitAssignmentFile).mockResolvedValue(sub);
    const file=new File(['%PDF-recovered'],'recovered.pdf');await act(async()=>hook.submitFile(file,null));
    expect(api.submitAssignmentFile).toHaveBeenCalledExactlyOnceWith(c,a,file,expect.anything());
});
it('IR01 reviewer control: committed closed read rejects the same callback',async()=>{
    await mount(undefined,true);const old=hook;vi.mocked(api.readLearnerAssignment).mockResolvedValueOnce({...detail,open:false});
    await act(async()=>old.refresh());await expect(old.submitFile(new File(['x'],'x.pdf'),null)).rejects.toMatchObject({kind:'cancelled'});
    expect(api.submitAssignmentFile).not.toHaveBeenCalled();
});
it('IR01 reviewer fast PUT and reconciliation cannot admit another unconfirmed PUT before commit',async()=>{
    await mount(undefined,true);const old=hook,file=new File(['%PDF-first'],'first.pdf');
    vi.mocked(api.readLearnerAssignment).mockResolvedValue({...detail,submission:sub});
    await act(async()=>{
        await old.submitFile(file,null);
        await expect(old.submitFile(new File(['%PDF-second'],'second.pdf'),null)).rejects.toMatchObject({kind:'cancelled'});
        expect(api.submitAssignmentFile).toHaveBeenCalledExactlyOnceWith(c,a,file,expect.anything());
    });
    await expect(hook.submitFile(file,null)).rejects.toMatchObject({kind:'cancelled'});
    await act(async()=>hook.submitFile(file,sub.submittedAt));expect(api.submitAssignmentFile).toHaveBeenCalledTimes(2);
});
it('IR01 reviewer control: cancelled response may leave server work pending without automatic replay',async()=>{
    let serverPending=false;
    vi.mocked(api.submitAssignmentFile).mockImplementation((_c,_a,_f,options)=>{
        serverPending=true;
        return new Promise((_yes,no)=>options?.signal?.addEventListener('abort',()=>no(new ApiError('cancelled','cancelled'))));
    });
    await mount(undefined,true);await act(async()=>{void hook.submitFile(new File(['x'],'x.pdf'),null);});
    await act(async()=>hook.cancelUpload());expect(serverPending).toBe(true);expect(hook.canSubmit).toBe(true);
    expect(hook.notice).toContain('outcome is unknown');expect(api.submitAssignmentFile).toHaveBeenCalledOnce();
});
it.each(['closed','replacement','failure'] as const)('IR01 reviewer production deadline/file-change blocks before %s commits',async kind=>{
    await mount(undefined,true); // original reviewer harness's initial read
    vi.useFakeTimers();vi.setSystemTime(new Date('2026-11-28T00:00:00Z'));
    const soon={...detail,deadlineAt:'2026-11-28T00:00:01Z'};
    vi.mocked(api.readLearnerAssignment).mockResolvedValueOnce(soon);
    if(kind==='failure')vi.mocked(api.readLearnerAssignment).mockRejectedValueOnce(new ApiError('http','Read failed',503));
    else vi.mocked(api.readLearnerAssignment).mockResolvedValueOnce(kind==='closed'?{...soon,open:false}:{...soon,submission:sub});
    await mount();vi.mocked(api.submitAssignmentFile).mockImplementation(()=>new Promise(()=>{}));
    await act(async()=>{
        vi.advanceTimersByTime(1000);
        for(let i=0;i<8;i++)await Promise.resolve();
        expect(api.readLearnerAssignment).toHaveBeenCalledTimes(3);
        const input=host.querySelector('input[type=file]') as HTMLInputElement;
        expect(input.disabled).toBe(false); // intentionally do not commit refreshed presentation
        Object.defineProperty(input,'files',{value:[new File(['%PDF-x'],'x.pdf')],configurable:true});
        input.dispatchEvent(new Event('change',{bubbles:true}));
        expect(api.submitAssignmentFile).not.toHaveBeenCalled();expect(document.querySelector('[role=dialog]')).toBeNull();
    });
    expect(text()).toContain(kind==='closed'?'Submissions are closed.':kind==='replacement'?'original.pdf':"Couldn't load assignment");
});

it.each(['closed','graded','failure','changed','same'] as const)('IR01 stale confirmation cannot revive after authoritative %s settlement',async kind=>{
    vi.mocked(api.readLearnerAssignment).mockResolvedValue({...detail,submission:sub});await mount(undefined,true);const old=hook;
    if(kind==='failure')vi.mocked(api.readLearnerAssignment).mockRejectedValueOnce(new ApiError('http','Read failed',503));
    else vi.mocked(api.readLearnerAssignment).mockResolvedValueOnce({...detail,open:kind!=='closed',submission:kind==='graded'?{...sub,graded:true,gradePercentage:5,feedback:'Result',gradedAt:sub.submittedAt}:kind==='changed'?{...sub,submittedAt:'2026-11-20T09:00:00.123457Z',originalFilename:'new.zip'}:sub});
    await act(async()=>{
        await old.refresh();await expect(old.submitFile(new File(['x'],'x.pdf'),sub.submittedAt)).rejects.toMatchObject({kind:'cancelled'});
        expect(api.submitAssignmentFile).not.toHaveBeenCalled();
    });
    if(kind==='changed')await expect(hook.submitFile(new File(['x'],'x.pdf'),sub.submittedAt)).rejects.toMatchObject({kind:'cancelled'});
    if(kind==='same'){
        // A callback from withdrawn evidence remains obsolete even when the token stayed the same.
        await expect(old.submitFile(new File(['x'],'x.pdf'),sub.submittedAt)).rejects.toMatchObject({kind:'cancelled'});
        await act(async()=>hook.submitFile(new File(['x'],'x.pdf'),sub.submittedAt));expect(api.submitAssignmentFile).toHaveBeenCalledOnce();
    }
});
it('IR01 active reads invalidate callbacks synchronously; obsolete cleanup cannot release the newer read',async()=>{
    await mount(undefined,true);const old=hook,first=deferred<api.B5LearnerAssignment>(),second=deferred<api.B5LearnerAssignment>();
    vi.mocked(api.readLearnerAssignment).mockReturnValueOnce(first.promise).mockReturnValueOnce(second.promise);
    await act(async()=>{
        const p1=old.refresh(),p2=old.refresh();
        await expect(old.submitFile(new File(['x'],'x.pdf'),null)).rejects.toMatchObject({kind:'cancelled'});
        first.resolve({...detail});await p1;
        await expect(old.submitFile(new File(['x'],'x.pdf'),null)).rejects.toMatchObject({kind:'cancelled'});
        expect(api.submitAssignmentFile).not.toHaveBeenCalled();second.resolve({...detail});await p2;
    });
    await act(async()=>hook.submitFile(new File(['x'],'x.pdf'),null));expect(api.submitAssignmentFile).toHaveBeenCalledOnce();
});
it('IR01 older read failure cannot revoke newer ready evidence or its microsecond token',async()=>{
    await mount(undefined,true);const old=deferred<api.B5LearnerAssignment>();
    vi.mocked(api.readLearnerAssignment).mockReturnValueOnce(old.promise).mockResolvedValueOnce({...detail,submission:sub});
    let first!:Promise<void>;await act(async()=>{first=hook.refresh();await hook.refresh();});
    await act(async()=>{old.reject(new ApiError('http','Obsolete',503));await first;});
    expect(hook.canSubmit).toBe(true);await act(async()=>hook.submitFile(new File(['x'],'x.pdf'),sub.submittedAt));expect(api.submitAssignmentFile).toHaveBeenCalledOnce();
});
it.each(['network','unknown-outcome','cancelled'] as const)('IR01 %s reconciliation requires presentation before deliberate upload',async kind=>{
    await mount(undefined,true);const old=hook;vi.mocked(api.submitAssignmentFile).mockRejectedValueOnce(new ApiError(kind,'safe'));
    await act(async()=>{
        await old.submitFile(new File(['x'],'x.pdf'),null);
        await expect(old.submitFile(new File(['y'],'y.pdf'),null)).rejects.toMatchObject({kind:'cancelled'});
        expect(api.submitAssignmentFile).toHaveBeenCalledOnce();
    });
    expect(hook.notice).toContain('outcome is unknown');vi.mocked(api.submitAssignmentFile).mockResolvedValue(sub);
    await act(async()=>hook.submitFile(new File(['y'],'y.pdf'),null));expect(api.submitAssignmentFile).toHaveBeenCalledTimes(2);
});
it.each([[400,'Please check the supplied information.'],[415,'The request could not be completed.'],[413,'The file is larger than the allowed size.']] as const)('IR02 shared safe %s fallback survives reconciliation',async(status,message)=>{
    vi.mocked(api.submitAssignmentFile).mockRejectedValue(new ApiError('http',status===413?'safe too large':message,status));
    await mount();await choose();expect(text()).toContain(message);expect(text()).not.toMatch(/format was rejected|file was rejected/);
    expect(api.submitAssignmentFile).toHaveBeenCalledOnce();expect(api.readLearnerAssignment).toHaveBeenCalledTimes(2);
});
it('409 with reloaded eligible state uses neutral recovery instead of guessing deadline or grading',async()=>{
    vi.mocked(api.submitAssignmentFile).mockRejectedValue(new ApiError('http','safe conflict',409));await mount();await choose();
    expect(text()).toContain('The assignment changed. Review its current state before choosing a file again.');
    expect(text()).not.toContain('The deadline has passed.');expect(api.submitAssignmentFile).toHaveBeenCalledOnce();
});
it.each(['closed','graded','changed','failure','same'] as const)('IR01 obsolete production confirmation event after %s read cannot dispatch',async kind=>{
    vi.useFakeTimers();vi.setSystemTime(new Date('2026-11-28T00:00:00Z'));
    const soon={...detail,deadlineAt:'2026-11-28T00:00:01Z',submission:sub};
    vi.mocked(api.readLearnerAssignment).mockResolvedValueOnce(soon);await mount();await choose();
    const confirm=document.querySelector('[role=dialog] button:last-child') as HTMLButtonElement;
    expect(text()).toContain(`Your current file "${sub.originalFilename}"`);
    if(kind==='failure')vi.mocked(api.readLearnerAssignment).mockRejectedValueOnce(new ApiError('http','Read failed',503));
    else vi.mocked(api.readLearnerAssignment).mockResolvedValueOnce({...soon,open:kind!=='closed',submission:kind==='graded'?{...sub,graded:true,gradePercentage:10,feedback:'Result',gradedAt:sub.submittedAt}:kind==='changed'?{...sub,originalFilename:'new.zip',submittedAt:'2026-11-20T09:00:00.123457Z'}:sub});
    await act(async()=>{
        vi.advanceTimersByTime(1000);for(let i=0;i<8;i++)await Promise.resolve();
        // The old dialog is still displayed; its event must not confirm new or withdrawn evidence.
        expect(confirm.disabled).toBe(false);confirm.click();expect(api.submitAssignmentFile).not.toHaveBeenCalled();
    });
    if(kind==='changed')expect(text()).not.toContain('Your current file "new.zip"');
});
it('IR01 StrictMode cancelled read and its cleanup cannot release the reinitialized read',async()=>{
    const first=deferred<api.B5LearnerAssignment>(),second=deferred<api.B5LearnerAssignment>();
    vi.mocked(api.readLearnerAssignment).mockReturnValueOnce(first.promise).mockReturnValueOnce(second.promise);
    await act(async()=>root.render(<StrictMode><AuthContext.Provider value={auth}><Hook/></AuthContext.Provider></StrictMode>));
    expect(api.readLearnerAssignment).toHaveBeenCalledTimes(2);expect(vi.mocked(api.readLearnerAssignment).mock.calls[0][2]?.signal?.aborted).toBe(true);
    await act(async()=>first.resolve({...detail}));
    await expect(hook.submitFile(new File(['x'],'x.pdf'),null)).rejects.toMatchObject({kind:'cancelled'});
    expect(hook.phase).toBe('loading');await act(async()=>second.resolve({...detail}));
    expect(hook.canSubmit).toBe(true);await act(async()=>hook.submitFile(new File(['x'],'x.pdf'),null));expect(api.submitAssignmentFile).toHaveBeenCalledOnce();
});
it('IR01 old-route mutation settlement cannot release the new route mutation reservation',async()=>{
    const first=deferred<api.B5LearnerSubmission>(),second=deferred<api.B5LearnerSubmission>();
    await mount(undefined,true);const old=hook;
    vi.mocked(api.submitAssignmentFile).mockReturnValueOnce(first.promise).mockReturnValueOnce(second.promise);
    let p1!:Promise<void>,p2!:Promise<void>;
    await act(async()=>{p1=hook.submitFile(new File(['x'],'old.pdf'),null);});
    vi.mocked(api.readLearnerAssignment).mockResolvedValue({...detail,id:b});await mount(undefined,true,b);
    await act(async()=>{p2=hook.submitFile(new File(['y'],'new.pdf'),null);});
    await act(async()=>{first.resolve(sub);await p1;});
    expect(hook.phase).toBe('uploading');
    await expect(old.submitFile(new File(['x'],'old.pdf'),null)).rejects.toMatchObject({kind:'cancelled'});
    await expect(hook.submitFile(new File(['z'],'extra.pdf'),null)).rejects.toMatchObject({kind:'cancelled'});
    expect(api.submitAssignmentFile).toHaveBeenCalledTimes(2);expect(api.readLearnerAssignment).toHaveBeenCalledTimes(2);
    await act(async()=>{second.resolve(sub);await p2;});expect(hook.canSubmit).toBe(true);expect(api.readLearnerAssignment).toHaveBeenCalledTimes(3);
});
it('IR01 logout and unmount revoke captured callbacks',async()=>{
    await mount(undefined,true);const old=hook;generation=2;auth={...auth,generation:2,isAuthenticated:false,user:null};await mount(undefined,true);
    await expect(old.submitFile(new File(['x'],'x.pdf'),null)).rejects.toMatchObject({kind:'cancelled'});
    const loggedOut=hook;await act(async()=>root.unmount());root=createRoot(host);
    await expect(loggedOut.submitFile(new File(['x'],'x.pdf'),null)).rejects.toMatchObject({kind:'cancelled'});expect(api.submitAssignmentFile).not.toHaveBeenCalled();
});
it.each([a,b])('IR01 obsolete Cancel callback cannot abort a newer upload on resource %s',async id=>{
    const first=deferred<api.B5LearnerSubmission>(),second=deferred<api.B5LearnerSubmission>();
    vi.mocked(api.submitAssignmentFile).mockReturnValueOnce(first.promise).mockReturnValueOnce(second.promise);await mount(undefined,true);
    let p1!:Promise<void>,p2!:Promise<void>;await act(async()=>{p1=hook.submitFile(new File(['x'],'first.pdf'),null);});
    const obsoleteCancel=hook.cancelUpload;
    await act(async()=>{first.resolve(sub);await p1;});
    vi.mocked(api.readLearnerAssignment).mockResolvedValue({...detail,id});await mount(undefined,true,id);
    await act(async()=>{p2=hook.submitFile(new File(['y'],'second.pdf'),null);});
    obsoleteCancel();expect(vi.mocked(api.submitAssignmentFile).mock.calls[1][3]?.signal?.aborted).toBe(false);
    await act(async()=>{second.resolve(sub);await p2;});
});
it('IR01 repeated production confirmation events reserve exactly one upload',async()=>{
    const upload=deferred<api.B5LearnerSubmission>();vi.mocked(api.submitAssignmentFile).mockReturnValueOnce(upload.promise);
    vi.mocked(api.readLearnerAssignment).mockResolvedValue({...detail,submission:sub});await mount();await choose();
    await act(async()=>{
        const confirm=document.querySelector('[role=dialog] button:last-child') as HTMLButtonElement;
        confirm.click();confirm.click();expect(api.submitAssignmentFile).toHaveBeenCalledOnce();
    });
    await act(async()=>upload.resolve(sub));expect(api.readLearnerAssignment).toHaveBeenCalledTimes(2);
});
