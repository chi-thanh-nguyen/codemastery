/*
 * Presentation view models for learner insights (S22). NOT API contracts: scoped
 * insight and recommendation-history reads are DEFERRED. Instructors see only
 * learners enrolled in their own courses (US-14), enforced by the backend.
 */
import type { MasteryState, RecommendationType } from '../../../../shared/components/StatusPill';

export interface LearnerRow { id: string; name: string; courseTitle: string; progress: { completed: number; total: number; percent: number }; latestScore: number | null; mastery: Record<MasteryState, number>; lastRecommendation?: { type: RecommendationType; atLabel: string } }
export interface RecommendationLogRow { id: string; atLabel: string; type: RecommendationType; targetLesson: string; reason: string }
export interface ResultRow { id: string; title: string; kindLabel: string; attempt: number; submittedAtLabel: string; score: number }
