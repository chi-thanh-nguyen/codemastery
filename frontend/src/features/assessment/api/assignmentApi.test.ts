import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { bindAuthSession } from '../../../shared/api/httpClient';
import { decodeLearnerAssignment, decodeLearnerSubmission, readLearnerAssignment, listLearnerAssignments, submitAssignmentFile, downloadOwnSubmission } from './assignmentApi';
import { listLearnerQuizzes } from './quizApi';
const c = '00000000-0000-4000-8000-000000000001', a = '00000000-0000-4000-8000-000000000002';
const sub = { originalFilename:'work.pdf',submittedAt:'2026-11-20T09:00:00.123456Z',graded:false,gradePercentage:null,feedback:null,gradedAt:null };
const detail = {id:a,courseId:c,lessonId:null,title:'Lab',instructions:'# Read',deadlineAt:'2026-12-01T00:00:00Z',open:true,submission:null};
const summary = {id:a,courseId:c,lessonId:null,title:'Lab',deadlineAt:detail.deadlineAt,open:true,submissionStatus:'none'};
const json = (v:unknown,status=200)=>new Response(JSON.stringify(v),{status,headers:{'Content-Type':'application/json'}});
let cleanup:()=>void, fetcher:ReturnType<typeof vi.fn>;
beforeEach(()=>{ vi.stubEnv('VITE_API_BASE_URL','https://api.test');fetcher=vi.fn();vi.stubGlobal('fetch',fetcher);cleanup=bindAuthSession({capture:()=>({generation:1,accessToken:'synthetic',expiresAt:Date.now()+60000}),isCurrent:g=>g===1,invalidate:vi.fn()}); });
afterEach(()=>{cleanup();vi.unstubAllGlobals();vi.unstubAllEnvs();vi.restoreAllMocks();});
it.each([200,201])('sends one actual File and accepts %s',async status=>{
    const file=new File(['%PDF-exact bytes'],'work.pdf',{type:'application/pdf'});fetcher.mockResolvedValue(json(sub,status));
    expect(await submitAssignmentFile(c,a,file)).toEqual(sub);expect(fetcher).toHaveBeenCalledOnce();const [url,init]=fetcher.mock.calls[0];
    expect(url).toBe(`https://api.test/api/courses/${c}/assignments/${a}/submission`);expect(init.body).toBeInstanceOf(FormData);expect([...init.body.keys()]).toEqual(['file']);expect(init.body.get('file')).toBe(file);
    expect(init.headers.has('Content-Type')).toBe(false);expect(init.headers.get('Authorization')).toBe('Bearer synthetic');expect(init.credentials).toBe('omit');
    const wire=new Request(url,{method:'PUT',body:init.body});const raw=await wire.text();expect(wire.headers.get('Content-Type')).toMatch(/^multipart\/form-data; boundary=/);expect(raw).toContain('name="file"; filename="work.pdf"');expect(raw).toContain('%PDF-exact bytes');
});
it.each([202,203,204,206])('unexpected mutation success %s is uncertain and never replayed',async status=>{
    fetcher.mockResolvedValue(status===204?new Response(null,{status}):json(sub,status));await expect(submitAssignmentFile(c,a,new File(['x'],'work.pdf'))).rejects.toMatchObject({kind:'unknown-outcome'});expect(fetcher).toHaveBeenCalledOnce();
});
it.each([{}, {...sub,graded:true},{...sub,originalFilename:'foreign.pdf'},{...sub,submittedAt:'2026-02-30T00:00:00Z'}])('malformed upload decoded within unknown-outcome lifecycle %#',async value=>{
    fetcher.mockResolvedValue(json(value,201));await expect(submitAssignmentFile(c,a,new File(['x'],'work.pdf'))).rejects.toMatchObject({kind:'unknown-outcome'});expect(fetcher).toHaveBeenCalledOnce();
});
it.each([new File([],'empty.pdf'),new File(['x'],'bad.exe')])('rejects local invalid file %#',async file=>{await expect(submitAssignmentFile(c,a,file)).rejects.toMatchObject({kind:'validation'});expect(fetcher).not.toHaveBeenCalled();});
it.each([400,403,404,409,413,415])('preserves known rejection %s',async status=>{fetcher.mockResolvedValue(new Response('',{status}));await expect(submitAssignmentFile(c,a,new File(['x'],'work.pdf'))).rejects.toMatchObject({kind:'http',status});expect(fetcher).toHaveBeenCalledOnce();});
it.each([500,503,'network'])('upload failure %s is unknown',async status=>{if(status==='network')fetcher.mockRejectedValue(new TypeError('offline'));else fetcher.mockResolvedValue(new Response('',{status:status as number}));await expect(submitAssignmentFile(c,a,new File(['x'],'work.pdf'))).rejects.toMatchObject({kind:'unknown-outcome'});});
it.each(['application/pdf','application/zip'])('downloads protected %s with server UTF-8 name',async media=>{
    fetcher.mockResolvedValue(new Response('binary',{headers:{'Content-Type':media,'Content-Disposition':"attachment; filename=\"fallback.pdf\"; filename*=UTF-8''b%C3%A0i.zip"}}));
    const file=await downloadOwnSubmission(c,a);expect(file.filename).toBe('bài.zip');expect(await file.blob.text()).toBe('binary');const[url,init]=fetcher.mock.calls[0];expect(url).toBe(`https://api.test/api/courses/${c}/assignments/${a}/submission/file`);expect(init.headers.get('Authorization')).toBe('Bearer synthetic');expect(url).not.toContain('synthetic');
});
it('rejects HTML, missing filename, stale owner and aborted downloads',async()=>{
    fetcher.mockResolvedValue(new Response('x',{headers:{'Content-Type':'text/html'}}));await expect(downloadOwnSubmission(c,a)).rejects.toMatchObject({kind:'protocol'});
    fetcher.mockResolvedValue(new Response('x',{headers:{'Content-Type':'application/pdf'}}));await expect(downloadOwnSubmission(c,a)).rejects.toMatchObject({kind:'protocol'});
    await expect(downloadOwnSubmission(c,a,{isCurrent:()=>false})).rejects.toMatchObject({kind:'cancelled'});const controller=new AbortController();controller.abort();await expect(downloadOwnSubmission(c,a,{signal:controller.signal})).rejects.toMatchObject({kind:'cancelled'});
});
it.each([
    {submittedAt:'not-time'},{submittedAt:'2026-02-29T00:00:00Z'},{submittedAt:'2026-11-20T09:00:00.1234567Z'},
    {originalFilename:'../x.pdf'},{originalFilename:'x.exe'},{originalFilename:'é'.repeat(126)+'.pdf'},
    {gradePercentage:1},{feedback:'unexpected'},{gradedAt:'2026-11-20T10:00:00Z'},
    {graded:true,gradePercentage:101,feedback:'x',gradedAt:'2026-11-20T10:00:00Z'},
    {graded:true,gradePercentage:0.001,feedback:'x',gradedAt:'2026-11-20T10:00:00Z'},
    {graded:true,gradePercentage:0,feedback:' ',gradedAt:'2026-11-20T10:00:00Z'},
    {graded:true,gradePercentage:0,feedback:'x',gradedAt:'2026-11-20T09:00:00.123455Z'},
])('rejects submission invariant %#',patch=>expect(()=>decodeLearnerSubmission({...sub,...patch})).toThrow());
it.each([0,0.29,85.25,100])('accepts valid grade %s and verbatim feedback',gradePercentage=>{const row={...sub,graded:true,gradePercentage,feedback:'  **Plain**\nNext. ',gradedAt:sub.submittedAt};expect(decodeLearnerSubmission(row)).toEqual(row);});
it.each([{id:c},{courseId:a},{lessonId:'bad'},{deadlineAt:'2026-13-01T00:00:00Z'},{open:1},{submission:[]},{extra:true},{instructions:' '}])('rejects detail identity/shape %#',patch=>expect(()=>decodeLearnerAssignment({...detail,...patch},c,a)).toThrow());
it('binds detail to request identity',async()=>{fetcher.mockResolvedValue(json({...detail,id:c}));await expect(readLearnerAssignment(c,a)).rejects.toMatchObject({kind:'protocol'});});
it.each(['none','submitted','graded'])('decodes status %s with exact requested page',async submissionStatus=>{fetcher.mockResolvedValue(json({content:[{...summary,submissionStatus}],page:1,size:20,totalElements:21,totalPages:2}));expect((await listLearnerAssignments(c,1)).content[0].submissionStatus).toBe(submissionStatus);expect(fetcher.mock.calls[0][0]).toContain('page=1&size=20');});
it.each([{page:1},{size:100},{totalPages:9},{totalElements:-1},{content:[{...summary,courseId:a}]},{content:[{...summary,submissionStatus:'closed'}]},{content:[summary,summary]}])('rejects list metadata/identity/status %#',async patch=>{fetcher.mockResolvedValue(json({content:[summary],page:0,size:20,totalElements:1,totalPages:1,...patch}));await expect(listLearnerAssignments(c)).rejects.toMatchObject({kind:'protocol'});});
it('quiz summary respects its own page and course identity',async()=>{const q={id:a,courseId:c,title:'Quiz',kind:'practice',published:true,timeLimitSeconds:300,questionCount:1};fetcher.mockResolvedValue(json({content:[q],page:1,size:20,totalElements:21,totalPages:2}));expect((await listLearnerQuizzes(c,1)).page).toBe(1);fetcher.mockResolvedValue(json({content:[{...q,courseId:a}],page:0,size:20,totalElements:1,totalPages:1}));await expect(listLearnerQuizzes(c)).rejects.toMatchObject({kind:'protocol'});});
it.each([[],[200,200],[200,500],[NaN]].map(expectedStatus=>({expectedStatus})))('rejects invalid explicit success set %# before dispatch',async ({expectedStatus})=>{const {createHttpClient}=await import('../../../shared/api/httpClient');await expect(createHttpClient('https://api.test')('/courses',{method:'GET',auth:'public',expectedStatus,decode:v=>v})).rejects.toMatchObject({kind:'configuration'});expect(fetcher).not.toHaveBeenCalled();});
