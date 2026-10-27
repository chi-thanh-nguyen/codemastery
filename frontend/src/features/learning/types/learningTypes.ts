/** CT22.membership only; progress/resume types remain FE08-owned. */
export type MembershipStatus = 'active' | 'completed' | 'left';
export type MembershipFilter = MembershipStatus | 'joined' | 'all';
export interface CourseMembership { courseId: string; status: MembershipStatus | 'none' }
export interface MembershipCourse {
    id: string;
    title: string;
    description: string;
    instructorId: string;
    instructorDisplayName: string | null;
    status: MembershipStatus;
}
export interface MembershipPage { content: MembershipCourse[]; page: number; size: number; totalElements: number; totalPages: number }
