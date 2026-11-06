/* Presentation view model for course administration (S24). NOT an API contract (CT12 DEFERRED). */
export interface AdminCourseRow { id: string; title: string; instructorName: string | null; publication: 'published' | 'hidden'; enrolments: number }
