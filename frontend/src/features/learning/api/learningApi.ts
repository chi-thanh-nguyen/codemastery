import { request } from '../../../shared/api/httpClient';
import type { CourseMembership } from '../types/learningTypes';


export async function getMyEnrolledCourses(token: string): Promise<CourseMembership[]> {
    return request<CourseMembership[]>('/api/learning/my-courses', {
        method: 'GET',
        token,
    });
}


export async function enrollInCourse(courseId: string, token: string): Promise<CourseMembership> {
    return request<CourseMembership>(`/api/courses/${courseId}/enroll`, {
        method: 'POST',
        token,
    });
}

/**
 * Protected operation: Leave/drop an enrolled course.
 * Path: DELETE /api/courses/{courseId}/enroll
 */
export async function leaveCourse(courseId: string, token: string): Promise<void> {
    return request<void>(`/api/courses/${courseId}/enroll`, {
        method: 'DELETE',
        token,
    });
}