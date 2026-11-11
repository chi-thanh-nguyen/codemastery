/**
 * Presentation view models for the Mastery Map. These are NOT API contracts:
 * the Adaptive read operations are deferred (adaptive-learning.md §15), so these
 * shapes only describe what the approved user stories (US-06/US-07) display.
 * A future approved contract maps into them; no field implies numeric mastery.
 */
import type { MasteryState, RecommendationType } from '../../../shared/components/StatusPill';

export type LessonProgressLabel = 'Not started' | 'Completed' | 'Mastered';

export interface SkillLessonView { id: string; title: string; progress: LessonProgressLabel }

export interface SkillView {
    id: string;
    name: string;
    description?: string;
    /** One of the four approved states; missing learner data is shown as Unknown. */
    state: MasteryState;
    prerequisiteIds: string[];
    /** Latest quiz score for this skill (0–100), when the owning contract provides one. */
    latestScore?: number | null;
    relatedLessons: SkillLessonView[];
}

export interface RecommendationView {
    id: string;
    type: RecommendationType;
    targetLessonId: string;
    targetLessonTitle: string;
    /** Human-readable reason supplied by the backend, displayed verbatim. */
    reason: string;
    /** Display label of the recommendation time (caller-formatted). */
    createdAtLabel: string;
    skillId?: string;
}
