import { request } from '../../../shared/api/httpClient';
import { ApiError, isRecord, protocolError } from '../../../shared/types/apiTypes';
import type { CoursePage, CourseDetail, CourseSummary, SyllabusChapter, SyllabusLesson } from '../types/catalogueTypes';

interface ReadOptions { signal?: AbortSignal; isCurrent?: () => boolean; q?: string; prerequisites?: 'any' | 'none' | 'described' }
const uuid = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
const integer = (value: unknown, minimum: number): value is number => Number.isSafeInteger(value) && (value as number) >= minimum;
function object(value: unknown, keys: string[]): Record<string, unknown> {
    if (!isRecord(value) || Object.keys(value).length !== keys.length || !keys.every((key) => Object.hasOwn(value, key))) throw protocolError();
    return value;
}
function summary(value: Record<string, unknown>): CourseSummary {
    if (typeof value.id !== 'string' || !uuid.test(value.id) || typeof value.instructorId !== 'string' || !uuid.test(value.instructorId) ||
        typeof value.title !== 'string' || typeof value.description !== 'string' || value.instructorDisplayName !== null && typeof value.instructorDisplayName !== 'string') throw protocolError();
    return { id: value.id, title: value.title, description: value.description, instructorId: value.instructorId, instructorDisplayName: value.instructorDisplayName as string | null };
}
function lesson(value: unknown): SyllabusLesson {
    const row = object(value, ['id', 'title', 'position']);
    if (typeof row.id !== 'string' || !uuid.test(row.id) || typeof row.title !== 'string' || !integer(row.position, 1)) throw protocolError();
    return { id: row.id, title: row.title, position: row.position };
}
function chapter(value: unknown): SyllabusChapter {
    const row = object(value, ['id', 'title', 'position', 'lessons']);
    if (typeof row.id !== 'string' || !uuid.test(row.id) || typeof row.title !== 'string' || !integer(row.position, 1) || !Array.isArray(row.lessons)) throw protocolError();
    return { id: row.id, title: row.title, position: row.position, lessons: row.lessons.map(lesson) };
}
export function decodeSummary(value: unknown): CourseSummary {
    return summary(object(value, ['id', 'title', 'description', 'instructorId', 'instructorDisplayName']));
}
function decodePage(value: unknown): CoursePage {
    const row = object(value, ['content', 'page', 'size', 'totalElements', 'totalPages']);
    if (!Array.isArray(row.content) || !integer(row.page, 0) || !integer(row.size, 1) || row.size > 100 ||
        !integer(row.totalElements, 0) || !integer(row.totalPages, 0)) throw protocolError();
    return { content: row.content.map((item) => summary(object(item, ['id', 'title', 'description', 'instructorId', 'instructorDisplayName']))),
        page: row.page, size: row.size, totalElements: row.totalElements, totalPages: row.totalPages };
}
function decodeDetail(value: unknown): CourseDetail {
    const row = object(value, ['id', 'title', 'description', 'instructorId', 'instructorDisplayName', 'prerequisiteDescription', 'syllabus']);
    if (row.prerequisiteDescription !== null && typeof row.prerequisiteDescription !== 'string' || !Array.isArray(row.syllabus)) throw protocolError();
    return { ...summary(row), prerequisiteDescription: row.prerequisiteDescription as string | null, syllabus: row.syllabus.map(chapter) };
}
export function getPublishedCourses(page = 0, size = 20, options: ReadOptions = {}): Promise<CoursePage> {
    if (!integer(page, 0) || !integer(size, 1) || size > 100) return Promise.reject(new ApiError('validation', 'Please check the requested page.'));
    const query = new URLSearchParams({ page: String(page), size: String(size) });
    if (options.q) query.set('q', options.q);
    if (options.prerequisites && options.prerequisites !== 'any') query.set('prerequisites', options.prerequisites);
    return request('/courses', { ...options, method: 'GET', auth: 'public', expectedStatus: 200, decode: decodePage,
        query });
}
export function getPublishedCourseById(courseId: string, options: ReadOptions = {}): Promise<CourseDetail> {
    return request(`/courses/${encodeURIComponent(courseId)}`, { ...options, method: 'GET', auth: 'public', expectedStatus: 200, decode: decodeDetail });
}
