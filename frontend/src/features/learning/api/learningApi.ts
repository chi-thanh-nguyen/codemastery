import { request } from '../../../shared/api/httpClient';
import { isRecord, protocolError } from '../../../shared/types/apiTypes';
import { decodeSummary } from '../../catalogue/api/catalogueApi';
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
        if (!isRecord(item) || !status(item.status)) throw protocolError();
        const { status: state, ...summary } = item;
        return { ...decodeSummary(summary), status: state };
    });
    return { content, page: value.page as number, size: value.size as number, totalElements: value.totalElements as number, totalPages: value.totalPages as number };
}
function operation(courseId: string, method: 'GET' | 'PUT' | 'DELETE', options: MembershipOptions) {
    return request(`/courses/${encodeURIComponent(courseId)}/membership`, { ...options, method, auth: 'authenticated', expectedStatus: 200,
        decode: (value) => { const result = membership(value); if (result.courseId !== courseId) throw protocolError(); return result; } });
}
export const getCourseMembership = (courseId: string, options: MembershipOptions = {}) => operation(courseId, 'GET', options);
export const enrollInCourse = (courseId: string, options: MembershipOptions = {}) => operation(courseId, 'PUT', options);
export const leaveCourse = (courseId: string, options: MembershipOptions = {}) => operation(courseId, 'DELETE', options);
export function getMyEnrolledCourses(pageIndex = 0, filter: MembershipFilter = 'joined', options: MembershipOptions = {}) {
    return request('/me/courses', { ...options, method: 'GET', auth: 'authenticated', expectedStatus: 200, decode: page,
        query: new URLSearchParams({ page: String(pageIndex), size: '20', status: filter }) });
}
