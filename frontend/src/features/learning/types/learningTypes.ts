export type MembershipStatus = 'ACTIVE' | 'DROPPED' | 'COMPLETED';

export interface CourseMembership {
    courseId: string; // UUID
    courseTitle: string;
    courseDescription?: string;
    enrolledAt: string; // ISO date string
    status: MembershipStatus;
}

export interface EnrollCoursePayload {
    courseId: string;
}