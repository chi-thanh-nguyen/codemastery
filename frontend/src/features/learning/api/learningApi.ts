import { request } from '../../../shared/api/httpClient';
import { isRecord, protocolError } from '../../../shared/types/apiTypes';
import type { CourseMembership, MembershipFilter, MembershipPage, MembershipStatus } from '../types/learningTypes';

export interface MembershipOptions { signal?: AbortSignal; isCurrent?: () => boolean }
const uuid = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
const status = (value: unknown): value is MembershipStatus => ['active', 'completed', 'left'].includes(value as string);
function membership(value: unknown): CourseMembership {
    if (!isRecord(value) || Object.keys(value).length !== 2 || typeof value.courseId !== 'string' || !uuid.test(value.courseId)
        || value.status !== 'none' && !status(value.status)) throw protocolError();
    return { courseId: value.courseId, status: value.status };
}
function page(value: unknown): MembershipPage {
    if (!isRecord(value) || Object.keys(value).length !== 5 || !Array.isArray(value.content)
        || !Number.isSafeInteger(value.page) || (value.page as number) < 0
        || !Number.isSafeInteger(value.size) || (value.size as number) < 1 || (value.size as number) > 100
        || !Number.isSafeInteger(value.totalElements) || (value.totalElements as number) < 0
        || !Number.isSafeInteger(value.totalPages) || (value.totalPages as number) < 0) throw protocolError();
    const content = value.content.map((item) => {
        if (!isRecord(item) || Object.keys(item).length !== 6 || !status(item.status)
            || typeof item.id !== 'string' || !uuid.test(item.id)
            || typeof item.title !== 'string' || typeof item.description !== 'string'
            || typeof item.instructorId !== 'string' || !uuid.test(item.instructorId)
            || item.instructorDisplayName !== null && typeof item.instructorDisplayName !== 'string') throw protocolError();
        return { id: item.id, title: item.title, description: item.description, instructorId: item.instructorId,
            instructorDisplayName: item.instructorDisplayName as string | null, status: item.status };
    });
    return { content, page: value.page as number, size: value.size as number, totalElements: value.totalElements as number, totalPages: value.totalPages as number };
}
function operation(courseId: string, method: 'GET' | 'PUT' | 'DELETE', options: MembershipOptions) {
    return request(`/courses/${encodeURIComponent(courseId)}/membership`, { ...options, method, auth: 'authenticated', expectedStatus: 200,
        decode: (value) => { const result = membership(value); if (!uuid.test(courseId) || result.courseId.toLowerCase() !== courseId.toLowerCase()) throw protocolError(); return result; } });
}
export const getCourseMembership = (courseId: string, options: MembershipOptions = {}) => operation(courseId, 'GET', options);
export const enrollInCourse = (courseId: string, options: MembershipOptions = {}) => operation(courseId, 'PUT', options);
export const leaveCourse = (courseId: string, options: MembershipOptions = {}) => operation(courseId, 'DELETE', options);
export function getMyEnrolledCourses(pageIndex = 0, filter: MembershipFilter = 'joined', options: MembershipOptions = {}) {
    return request('/me/courses', { ...options, method: 'GET', auth: 'authenticated', expectedStatus: 200, decode: page,
        query: new URLSearchParams({ page: String(pageIndex), size: '20', status: filter }) });
}
