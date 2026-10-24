import { request } from '../../../shared/api/httpClient';
import type { CoursePage, CourseDetail } from '../types/catalogueTypes';

export async function getPublishedCourses(page = 0, size = 20): Promise<CoursePage> {
    const query = new URLSearchParams({
        page: String(page),
        size: String(size),
    });

    return request<CoursePage>(`/api/courses?${query.toString()}`);
}

export async function getPublishedCourseById(courseId: string): Promise<CourseDetail> {
    return request<CourseDetail>(`/api/courses/${courseId}`);
}