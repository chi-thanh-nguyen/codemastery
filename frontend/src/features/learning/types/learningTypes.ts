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

/*
 * Presentation view models for the learning journey (S6–S8). NOT API contracts:
 * lesson content, progress, resume and recommendations are DEFERRED (CT21.remaining,
 * CT22 progress/resume, CT09, Adaptive). A future approved contract maps into these.
 */
/** Mirrors LessonProgress: completion flag plus mastery-skip flag (skip implies completion). */
export type LessonProgressState = 'not-started' | 'completed' | 'mastered-skip';
/** Adaptive guidance shown on a lesson; never reorders or locks the canonical path. */
export type LessonAdaptiveMarker = 'recommended' | 'skippable' | 'remedial' | 'prerequisite-gap';
export interface SkillTagView { id: string; name: string; state: 'unknown' | 'learning' | 'mastered' | 'weak' }
export interface OutlineLessonView {
    id: string;
    title: string;
    position: number;
    summary?: string;
    progress: LessonProgressState;
    isResume?: boolean;
    markers?: LessonAdaptiveMarker[];
    skills?: SkillTagView[];
}
export interface OutlineChapterView { id: string; title: string; position: number; lessons: OutlineLessonView[] }
export interface CourseProgressView { completed: number; total: number; percent: number }

export type LessonBlock =
    | { kind: 'paragraph'; text: string }
    | { kind: 'heading'; text: string }
    | { kind: 'list'; items: string[] }
    | { kind: 'code'; code: string; caption?: string }
    | { kind: 'note'; text: string };
export type LessonMaterialView =
    | { id: string; kind: 'file'; name: string; description: string }
    | { id: string; kind: 'external_video'; title: string; url: string };
