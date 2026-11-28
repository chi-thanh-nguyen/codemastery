/**
 * Presentation view models for the Mastery Map. These are NOT API contracts:
 * the Adaptive read operations are deferred (adaptive-learning.md §15), so these
 * shapes only describe what the approved user stories (US-06/US-07) display.
 * A future approved contract maps into them; no field implies numeric mastery.
 */
import type { MasteryState, RecommendationType } from '../../../shared/components/StatusPill';

export type LessonProgressLabel = 'Not started' | 'Completed' | 'Mastered';

export interface SkillLessonView { id: string; title: string; progress: LessonProgressLabel; recommended?: boolean; }

export interface SkillView {
    id: string;
    name: string;
    description?: string;
    state: MasteryState;
    prerequisiteIds: string[];
    latestScore?: number | null;
    latestAssessedAtLabel?: string;
    updated?: boolean;
    relatedLessons: SkillLessonView[];
}

export interface RecommendationView {
    id: string;
    type: RecommendationType;
    targetLessonId: string;
    targetLessonTitle: string;
    reason: string;
    createdAtLabel: string;
    targetCompleted?: boolean;
    skillId?: string;
}

export interface B6MasterySkill {
    id: string;
    name: string;
    description: string | null;
    state: MasteryState;
    prerequisiteSkillIds: string[];
    relatedLessonIds: string[];
    latestScore: number | null;
    latestAssessedAt: string | null;
}

export interface B6MasteryMap {
    courseId: string;
    learnerId: string;
    adaptiveEnabled: boolean;
    skills: B6MasterySkill[];
}

export interface B6CurrentRecommendation {
    type: 'continue' | 'remedial';
    targetLessonId: string;
    reason: string;
    recommendedAt: string;
    targetCompleted: boolean;
}

export interface B6LessonGuidance {
    lessonId: string;
    unmasteredPrerequisiteSkillIds: string[];
}

export interface B6AdaptiveGuidance {
    courseId: string;
    adaptiveEnabled: boolean;
    placementOffered: boolean;
    placementQuizId: string | null;
    currentRecommendation: B6CurrentRecommendation | null;
    lessons: B6LessonGuidance[];
}

export interface MasteryLessonTitleEntry {
    id: string;
    title: string;
}

export interface MasteryLessonProgressStatus {
    lessonId: string;
    completed: boolean;
    masterySkipped: boolean;
}
