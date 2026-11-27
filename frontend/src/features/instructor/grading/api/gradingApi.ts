import { request } from '../../../../shared/api/httpClient';
import { ApiError, cancelled, isRecord, protocolError } from '../../../../shared/types/apiTypes';
import type { AuthoringRequestOptions } from '../../course-management/api/assessmentAuthoringApi';
import type { GradeSubmissionWrite, SubmissionSummary, SubmissionPage } from '../types/gradingTypes';

export const gradingId = (value: unknown): value is string => typeof value === 'string' && /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(value);
const same = (a: string, b: string) => a.toLowerCase() === b.toLowerCase();
const text = (v: unknown): v is string => typeof v === 'string' && /\S/.test(v);
const integer = (v: unknown): v is number => Number.isSafeInteger(v) && (v as number) >= 0;
/** Accept the producer's UTC microsecond instants; return precision for comparisons, never rewrite the token. */
function instant(v: unknown): bigint | null {
    if (typeof v !== 'string') return null;
    const m = /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2}):(\d{2})(?:\.(\d{1,6}))?Z$/.exec(v);
    if (!m) return null;
    const y = +m[1], mo = +m[2], d = +m[3];
    const days = [31, y % 4 === 0 && (y % 100 !== 0 || y % 400 === 0) ? 29 : 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31];
    if (mo < 1 || mo > 12 || d < 1 || d > days[mo - 1] || +m[4] > 23 || +m[5] > 59 || +m[6] > 59) return null;
    const seconds = Date.parse(`${v.slice(0, 19)}Z`);
    return Number.isFinite(seconds) ? BigInt(seconds) * 1000n + BigInt((m[7] ?? '').padEnd(6, '0')) : null;
}
export const validGrade = (v: unknown): v is number => typeof v === 'number' && Number.isFinite(v) && v >= 0 && v <= 100 && /^\d+(?:\.\d{1,2})?$/.test(String(v));
function exact(value: unknown, keys: string[]): Record<string, unknown> {
    if (!isRecord(value) || Object.keys(value).length !== keys.length || !keys.every(k => Object.hasOwn(value, k))) throw protocolError();
    return value;
}
export function decodeSubmission(value: unknown, assignmentId: string, submissionId?: string): SubmissionSummary {
    const r = exact(value, ['id', 'assignmentId', 'learnerId', 'learnerDisplayName', 'originalFilename', 'submittedAt', 'graded', 'gradePercentage', 'feedback', 'gradedAt']);
    const submitted = instant(r.submittedAt), graded = instant(r.gradedAt);
    if (!gradingId(r.id) || !gradingId(r.assignmentId) || !same(r.assignmentId, assignmentId) || submissionId && !same(r.id, submissionId) || !gradingId(r.learnerId)
        || !(r.learnerDisplayName === null || text(r.learnerDisplayName)) || !text(r.originalFilename)
        || /[\\/\u0000-\u001f\u007f]/.test(r.originalFilename) || new TextEncoder().encode(r.originalFilename).length > 255 || !/\.(pdf|zip)$/i.test(r.originalFilename)
        || submitted === null || typeof r.graded !== 'boolean'
        || (r.graded ? !validGrade(r.gradePercentage) || !text(r.feedback) || graded === null || graded < submitted
            : r.gradePercentage !== null || r.feedback !== null || r.gradedAt !== null)) throw protocolError();
    return { ...r } as unknown as SubmissionSummary;
}
function base(courseId: string, assignmentId: string, submissionId?: string) {
    if (![courseId, assignmentId, ...(submissionId === undefined ? [] : [submissionId])].every(gradingId)) throw new ApiError('validation', 'Invalid grading link.');
    return `/courses/${encodeURIComponent(courseId)}/owned-assignments/${encodeURIComponent(assignmentId)}/submissions${submissionId === undefined ? '' : `/${encodeURIComponent(submissionId)}`}`;
}
export function listSubmissions(courseId: string, assignmentId: string, page = 0, size = 20, options: AuthoringRequestOptions = {}): Promise<SubmissionPage> {
    if (!integer(page) || page > 2147483647 || !integer(size) || size < 1 || size > 100) throw new ApiError('validation', 'Invalid submission page.');
    return request(base(courseId, assignmentId), { ...options, method: 'GET', auth: 'authenticated', expectedStatus: 200,
        query: new URLSearchParams({ page: String(page), size: String(size) }), decode: value => {
            const r = exact(value, ['content', 'page', 'size', 'totalElements', 'totalPages']);
            if (!Array.isArray(r.content) || r.page !== page || r.size !== size || !integer(r.totalElements) || !integer(r.totalPages)
                || r.totalPages !== Math.ceil(r.totalElements / size) || r.content.length !== Math.min(size, Math.max(0, r.totalElements - page * size))) throw protocolError();
            const content = r.content.map(v => decodeSubmission(v, assignmentId));
            if (new Set(content.map(v => v.id.toLowerCase())).size !== content.length || new Set(content.map(v => v.learnerId.toLowerCase())).size !== content.length) throw protocolError();
            return { content, page, size, totalElements: r.totalElements, totalPages: r.totalPages };
        } });
}
export function downloadSubmission(courseId: string, assignmentId: string, submissionId: string, options: AuthoringRequestOptions = {}) {
    return request(`${base(courseId, assignmentId, submissionId)}/file`, { ...options, method: 'GET', auth: 'authenticated', expectedStatus: 200, responseMode: 'binary' });
}
export function gradeSubmission(courseId: string, assignmentId: string, submissionId: string, write: GradeSubmissionWrite, options: AuthoringRequestOptions = {}) {
    if (!validGrade(write.gradePercentage) || !text(write.feedback) || instant(write.submittedAt) === null) throw new ApiError('validation', 'Check the grade and feedback.');
    return request(`${base(courseId, assignmentId, submissionId)}/grade`, { ...options, method: 'PUT', auth: 'authenticated', expectedStatus: 200,
        body: { gradePercentage: write.gradePercentage, feedback: write.feedback, submittedAt: write.submittedAt }, decode: value => {
            const row = decodeSubmission(value, assignmentId, submissionId);
            if (!row.graded || row.submittedAt !== write.submittedAt || row.gradePercentage !== write.gradePercentage || row.feedback !== write.feedback) throw protocolError();
            return row;
        } });
}
/** D10-2: a bounded scan is a lookup, never proof of absence. No retries or mutations. */
export async function findSubmission(courseId: string, assignmentId: string, submissionId: string, options: AuthoringRequestOptions = {}) {
    const current = () => { if (options.signal?.aborted || options.isCurrent?.() === false) throw cancelled(); };
    current();
    const first = await listSubmissions(courseId, assignmentId, 0, 100, options);
    current();
    for (let page = 0, data = first; page < Math.max(1, first.totalPages); page++) {
        if (page > 0) data = await listSubmissions(courseId, assignmentId, page, 100, options);
        current();
        const row = data.content.find(r => same(r.id, submissionId));
        if (row) return row;
    }
    return null;
}
