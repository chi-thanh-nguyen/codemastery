import { request } from '../../../shared/api/httpClient';
import { ApiError, isRecord, protocolError } from '../../../shared/types/apiTypes';
import type { PageResponse } from '../../../shared/types/apiTypes';
import type { LearnerSubmission, LearnerAssignmentSummary, LearnerAssignment } from '../types/assessmentTypes';

export interface AssignmentRequestOptions { signal?: AbortSignal; isCurrent?: () => boolean }
/** Preserve the incoming client names while reusing main's AS09 wire types. */
export type B5LearnerSubmission = LearnerSubmission;
export type B5LearnerAssignmentSummary = LearnerAssignmentSummary;
export type B5LearnerAssignment = LearnerAssignment;
export const assignmentIdValid = (v: unknown): v is string => typeof v === 'string' && /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(v);
const same = (a: string, b: string) => a.toLowerCase() === b.toLowerCase();
const text = (v: unknown): v is string => typeof v === 'string' && /\S/.test(v);
const integer = (v: unknown): v is number => Number.isSafeInteger(v) && (v as number) >= 0;
/** Producer UTC instants, microseconds preserved for ordering; never round through Date. */
export function assignmentInstant(v: unknown): bigint | null {
    if (typeof v !== 'string') return null;
    const m = /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2}):(\d{2})(?:\.(\d{1,6}))?Z$/.exec(v);
    if (!m) return null;
    const y = +m[1], mo = +m[2], d = +m[3];
    const days = [31, y % 4 === 0 && (y % 100 !== 0 || y % 400 === 0) ? 29 : 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31];
    if (mo < 1 || mo > 12 || d < 1 || d > days[mo - 1] || +m[4] > 23 || +m[5] > 59 || +m[6] > 59) return null;
    const seconds = Date.parse(`${v.slice(0, 19)}Z`);
    return Number.isFinite(seconds) ? BigInt(seconds) * 1000n + BigInt((m[7] ?? '').padEnd(6, '0')) : null;
}
function exact(v: unknown, keys: string[]): Record<string, unknown> {
    if (!isRecord(v) || Object.keys(v).length !== keys.length || !keys.every(k => Object.hasOwn(v, k))) throw protocolError();
    return v;
}
export function decodeLearnerSubmission(value: unknown): B5LearnerSubmission {
    const r = exact(value, ['originalFilename', 'submittedAt', 'graded', 'gradePercentage', 'feedback', 'gradedAt']);
    const submitted = assignmentInstant(r.submittedAt), graded = assignmentInstant(r.gradedAt);
    if (!text(r.originalFilename) || /[\\/\u0000-\u001f\u007f]/.test(r.originalFilename)
        || new TextEncoder().encode(r.originalFilename).length > 255 || !/\.(pdf|zip)$/i.test(r.originalFilename)
        || submitted === null || typeof r.graded !== 'boolean'
        || (r.graded ? typeof r.gradePercentage !== 'number' || !Number.isFinite(r.gradePercentage) || r.gradePercentage < 0 || r.gradePercentage > 100
            || !/^\d+(?:\.\d{1,2})?$/.test(String(r.gradePercentage)) || !text(r.feedback) || graded === null || graded < submitted
            : r.gradePercentage !== null || r.feedback !== null || r.gradedAt !== null)) throw protocolError();
    return { ...r } as unknown as B5LearnerSubmission;
}
function checkAssignment(r: Record<string, unknown>, courseId: string, assignmentId?: string) {
    if (!assignmentIdValid(r.id) || !assignmentIdValid(r.courseId) || !same(r.courseId, courseId) || assignmentId && !same(r.id, assignmentId)
        || !(r.lessonId === null || assignmentIdValid(r.lessonId)) || !text(r.title) || assignmentInstant(r.deadlineAt) === null || typeof r.open !== 'boolean') throw protocolError();
}
export function decodeLearnerAssignment(value: unknown, courseId: string, assignmentId: string): B5LearnerAssignment {
    const r = exact(value, ['id', 'courseId', 'lessonId', 'title', 'instructions', 'deadlineAt', 'open', 'submission']);
    checkAssignment(r, courseId, assignmentId);
    if (!text(r.instructions)) throw protocolError();
    return { ...r, submission: r.submission === null ? null : decodeLearnerSubmission(r.submission) } as unknown as B5LearnerAssignment;
}
function base(courseId: string, assignmentId?: string) {
    if (!assignmentIdValid(courseId) || assignmentId !== undefined && !assignmentIdValid(assignmentId)) throw new ApiError('validation', 'Invalid assignment link.');
    return `/courses/${encodeURIComponent(courseId)}/assignments${assignmentId === undefined ? '' : `/${encodeURIComponent(assignmentId)}`}`;
}
export function readLearnerAssignment(courseId: string, assignmentId: string, options: AssignmentRequestOptions = {}) {
    return request(base(courseId, assignmentId), { ...options, method: 'GET', auth: 'authenticated', expectedStatus: 200,
        decode: v => decodeLearnerAssignment(v, courseId, assignmentId) });
}
export function listLearnerAssignments(courseId: string, page = 0, size = 20, options: AssignmentRequestOptions = {}): Promise<PageResponse<B5LearnerAssignmentSummary>> {
    if (!integer(page) || page > 2147483647 || !integer(size) || size < 1 || size > 100) throw new ApiError('validation', 'Invalid assignment page.');
    return request(base(courseId), { ...options, method: 'GET', auth: 'authenticated', expectedStatus: 200,
        query: new URLSearchParams({ page: String(page), size: String(size) }), decode: v => {
            const r = exact(v, ['content', 'page', 'size', 'totalElements', 'totalPages']);
            if (!Array.isArray(r.content) || r.page !== page || r.size !== size || !integer(r.totalElements) || !integer(r.totalPages)
                || r.totalPages !== Math.ceil(r.totalElements / size) || r.content.length !== Math.min(size, Math.max(0, r.totalElements - page * size))) throw protocolError();
            const content = r.content.map(value => {
                const row = exact(value, ['id', 'courseId', 'lessonId', 'title', 'deadlineAt', 'open', 'submissionStatus']);
                checkAssignment(row, courseId);
                if (!['none', 'submitted', 'graded'].includes(row.submissionStatus as string)) throw protocolError();
                return { ...row } as unknown as B5LearnerAssignmentSummary;
            });
            if (new Set(content.map(r => r.id.toLowerCase())).size !== content.length) throw protocolError();
            return { content, page, size, totalElements: r.totalElements, totalPages: r.totalPages };
        } });
}
export function validateAssignmentFile(file: File) {
    if (!(file instanceof File) || file.size === 0 || !/\.(pdf|zip)$/i.test(file.name)) throw new ApiError('validation', 'Choose a non-empty PDF or ZIP file.');
}
export async function submitAssignmentFile(courseId: string, assignmentId: string, file: File, options: AssignmentRequestOptions = {}) {
    validateAssignmentFile(file);
    const body = new FormData(); body.append('file', file);
    return request(`${base(courseId, assignmentId)}/submission`, { ...options, method: 'PUT', auth: 'authenticated',
        expectedStatus: [200, 201], body, bodyFormat: 'multipart', decode: value => {
            const row = decodeLearnerSubmission(value);
            if (row.graded || row.originalFilename !== file.name) throw protocolError();
            return row;
        } });
}
export function downloadOwnSubmission(courseId: string, assignmentId: string, options: AssignmentRequestOptions = {}) {
    return request(`${base(courseId, assignmentId)}/submission/file`, { ...options, method: 'GET', auth: 'authenticated', expectedStatus: 200, responseMode: 'binary' });
}
