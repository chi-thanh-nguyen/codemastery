/* Presentation view model for course administration (S24). Separate from the CT12 wire contract below. */
export interface AdminCourseRow { id: string; title: string; instructorName: string | null; publication: 'published' | 'hidden'; enrolments: number }

/** CT12-COURSE r2 / CT12-REST r1 wire shapes (PR #312); no screen activation. */
export interface AdminCourse {
    courseId: string;
    title: string;
    publicationStatus: 'hidden' | 'published';
    instructorId: string;
    instructorDisplayName: string | null;
}
export type AdminCoursePage = import('../../../../shared/types/apiTypes').PageResponse<AdminCourse>;
export interface AdminCoursePublicationWrite { publicationStatus: 'hidden' }
export interface AdminCourseChange { course: AdminCourse; changed: boolean }
