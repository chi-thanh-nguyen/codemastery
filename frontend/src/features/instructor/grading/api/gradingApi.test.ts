import { beforeEach, describe, expect, it, vi } from 'vitest';
import { request } from '../../../../shared/api/httpClient';
import { decodeSubmission, downloadSubmission, findSubmission, gradeSubmission, listSubmissions } from './gradingApi';
vi.mock('../../../../shared/api/httpClient', () => ({ request: vi.fn() }));
const course = '00000000-0000-4000-8000-000000000001', assignment = '00000000-0000-4000-8000-000000000002', id = '00000000-0000-4000-8000-000000000003';
const row = (patch = {}) => ({ id, assignmentId: assignment, learnerId: course, learnerDisplayName: null, originalFilename: 'work.ZIP', submittedAt: '2026-11-20T09:15:02.123456Z', graded: false, gradePercentage: null, feedback: null, gradedAt: null, ...patch });
const page = (content: unknown[], index = 0, total = content.length) => ({ content, page: index, size: 100, totalElements: total, totalPages: Math.ceil(total / 100) });
beforeEach(() => vi.resetAllMocks());
function options() { return vi.mocked(request).mock.calls.at(-1)![1] as { decode: (v: unknown) => unknown; body: unknown; query: URLSearchParams; auth: string; responseMode?: string }; }
describe('FE17 strict CT09/CT10 client', () => {
    it('binds assignment identity and retains exact microsecond strings and nullable name', () => expect(decodeSubmission(row(), assignment)).toEqual(row()));
    it.each([
        { extra: 'key' }, { assignmentId: course }, { id: 'bad' }, { learnerId: 'bad' }, { learnerDisplayName: '' },
        { originalFilename: '../work.zip' }, { originalFilename: 'work.exe' }, { originalFilename: 'x'.repeat(256) + '.zip' },
        { submittedAt: '2026-02-30T00:00:00Z' }, { submittedAt: '2026-01-01T24:00:00Z' }, { submittedAt: '2026-01-01T00:00:00.1234567Z' },
        { graded: 'false' }, { gradePercentage: 0 }, { feedback: '' }, { gradedAt: '2026-11-20T09:15:03Z' },
        { graded: true }, { graded: true, gradePercentage: 1.001, feedback: 'ok', gradedAt: '2026-11-20T09:15:03Z' },
        { graded: true, gradePercentage: 10, feedback: 'ok', gradedAt: '2026-11-20T09:15:02.123455Z' },
    ])('rejects malformed/incoherent row %j', patch => expect(() => decodeSubmission(row(patch), assignment)).toThrow());
    it('accepts coherent graded data and feedback verbatim', () => {
        const v = row({ graded: true, gradePercentage: 0.29, feedback: '  <b>plain</b>\n ', gradedAt: '2026-11-20T09:15:02.123456Z' });
        expect(decodeSubmission(v, assignment)).toEqual(v);
    });
    it('uses approved authenticated list path, pagination and identity decoding', async () => {
        await listSubmissions(course, assignment, 0, 100);
        expect(vi.mocked(request).mock.calls[0][0]).toBe(`/courses/${course}/owned-assignments/${assignment}/submissions`);
        expect(options().query.toString()).toBe('page=0&size=100'); expect(options().auth).toBe('authenticated');
        expect(options().decode(page([row()]))).toEqual(page([row()]));
        for (const broken of [page([row()], 1), { ...page([row()]), size: 20 }, { ...page([row()]), totalPages: 5 }, page([row(), row()]), { ...page([]), totalElements: 5 }]) expect(() => options().decode(broken)).toThrow();
    });
    it.each([-1, 1.5, 101])('refuses invalid size %s before sending', size => { expect(() => listSubmissions(course, assignment, 0, size)).toThrow(); expect(request).not.toHaveBeenCalled(); });
    it('uses binary transport and exact scoped download identity', async () => {
        await downloadSubmission(course, assignment, id); expect(options().responseMode).toBe('binary'); expect(vi.mocked(request).mock.calls[0][0]).toContain(`/submissions/${id}/file`);
    });
    it('sends exact token and verbatim feedback; checks returned grade and submission', async () => {
        const write = { gradePercentage: 95.25, feedback: '  Good\n ', submittedAt: row().submittedAt };
        await gradeSubmission(course, assignment, id, write);
        expect(options().body).toEqual(write);
        expect(options().decode(row({ ...write, graded: true, gradedAt: '2026-11-20T10:00:00Z' }))).toMatchObject(write);
        expect(() => options().decode(row({ ...write, graded: true, gradePercentage: 95, gradedAt: '2026-11-20T10:00:00Z' }))).toThrow();
    });
    it.each([NaN, -1, 101, 0.001])('never rounds invalid grades %s', gradePercentage => expect(() => gradeSubmission(course, assignment, id, { gradePercentage, feedback: 'ok', submittedAt: row().submittedAt })).toThrow());
    it('bounds a mutable scan by the first response and requests size 100', async () => {
        vi.mocked(request).mockResolvedValueOnce(page(Array.from({ length: 100 }, (_, n) => ({ ...row(), id: String(n) })), 0, 201))
            .mockResolvedValueOnce(page([], 1, 0)).mockResolvedValueOnce(page([], 2, 0));
        expect(await findSubmission(course, assignment, id)).toBeNull(); expect(request).toHaveBeenCalledTimes(3);
        expect(vi.mocked(request).mock.calls.map(c => c[1].query?.get('size'))).toEqual(['100','100','100']);
    });
    it('finds later rows without further scans', async () => {
        vi.mocked(request).mockResolvedValueOnce(page([], 0, 300)).mockResolvedValueOnce(page([row()], 1, 500));
        expect(await findSubmission(course, assignment, id)).toEqual(row()); expect(request).toHaveBeenCalledTimes(2);
    });
    it('cancels an abandoned scan between responses', async () => {
        const controller = new AbortController();
        vi.mocked(request).mockImplementationOnce(async () => { controller.abort(); return page([], 0, 500); });
        await expect(findSubmission(course, assignment, id, { signal: controller.signal })).rejects.toMatchObject({ kind: 'cancelled' }); expect(request).toHaveBeenCalledTimes(1);
    });
});
