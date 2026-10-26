import type { CourseSummary } from '../../catalogue/types/catalogueTypes';

/** CT22.membership only; progress/resume types remain FE08-owned. */
export type MembershipStatus = 'active' | 'completed' | 'left';
export type MembershipFilter = MembershipStatus | 'joined' | 'all';
export interface CourseMembership { courseId: string; status: MembershipStatus | 'none' }
export interface MembershipCourse extends CourseSummary { status: MembershipStatus }
export interface MembershipPage { content: MembershipCourse[]; page: number; size: number; totalElements: number; totalPages: number }
