import { request } from '../../../shared/api/httpClient';
import { ApiError, isRecord, protocolError } from '../../../shared/types/apiTypes';
import type { B3QuizSummary, B3QuizSummaryPage } from '../types/assessmentTypes';
export interface QuizRequestOptions { signal?: AbortSignal; isCurrent?: () => boolean }
const uuid = (v: unknown): v is string => typeof v === 'string' && /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(v);
const integer = (v: unknown): v is number => Number.isSafeInteger(v) && (v as number) >= 0;
const exact = (v: unknown, keys: string[]) => { if (!isRecord(v) || Object.keys(v).length !== keys.length || !keys.every(k => Object.hasOwn(v, k))) throw protocolError(); return v; };
/** Bounded FE10 contribution: summary read only; no attempt, answer or result behavior. */
export function listLearnerQuizzes(courseId: string, page = 0, size = 20, options: QuizRequestOptions = {}): Promise<B3QuizSummaryPage> {
    if (!uuid(courseId) || !integer(page) || page > 2147483647 || !integer(size) || size < 1 || size > 100) throw new ApiError('validation', 'Invalid quiz list link.');
    return request(`/courses/${encodeURIComponent(courseId)}/quizzes`, { ...options, method: 'GET', auth: 'authenticated', expectedStatus: 200,
        query: new URLSearchParams({ page: String(page), size: String(size) }), decode: v => {
            const r = exact(v, ['content', 'page', 'size', 'totalElements', 'totalPages']);
            if (!Array.isArray(r.content) || r.page !== page || r.size !== size || !integer(r.totalElements) || !integer(r.totalPages)
                || r.totalPages !== Math.ceil(r.totalElements / size) || r.content.length !== Math.min(size, Math.max(0, r.totalElements - page * size))) throw protocolError();
            const content = r.content.map(value => {
                const q = exact(value, ['id', 'courseId', 'title', 'kind', 'timeLimitSeconds', 'published', 'questionCount']);
                if (!uuid(q.id) || !uuid(q.courseId) || q.courseId.toLowerCase() !== courseId.toLowerCase() || typeof q.title !== 'string' || !/\S/.test(q.title)
                    || !['diagnostic', 'practice', 'test'].includes(q.kind as string) || !integer(q.timeLimitSeconds) || q.timeLimitSeconds < 1 || q.timeLimitSeconds > 2147483647
                    || q.published !== true || !integer(q.questionCount) || q.questionCount > 2147483647) throw protocolError();
                return { ...q } as unknown as B3QuizSummary;
            });
            if (new Set(content.map(r => r.id.toLowerCase())).size !== content.length) throw protocolError();
            return { content, page, size, totalElements: r.totalElements, totalPages: r.totalPages };
        } });
}
