/*
 * Presentation view models for assessment screens (S9–S13). NOT API contracts:
 * Assessment operations (CT04/CT10/CT23), the Assessment → Adaptive event (CT08)
 * and file delivery (CT09) are DEFERRED. The backend is the authority for time
 * limits, expiry, attempt numbering, scoring and submission acceptance.
 */
import type { MasteryState } from '../../../shared/components/StatusPill';

/** Approved stored question kinds; "predict the output" uses these choice formats with a code block. */
export type QuestionKind = 'single_choice' | 'multiple_choice' | 'true_false';
export type QuizKind = 'diagnostic' | 'practice' | 'test';
export interface QuestionOptionView { id: string; label: string }
export interface QuestionView { id: string; position: number; kind: QuestionKind; prompt: string; code?: string; options: QuestionOptionView[] }
export interface QuizSummaryView { id: string; title: string; kind: QuizKind; questionCount: number; timeLimitSeconds: number; nextAttemptNumber: number }
export interface ReviewItemView { questionId: string; position: number; prompt: string; code?: string; selectedLabels: string[]; correct: boolean; correctLabels?: string[] }
export interface SkillChangeView { skillId: string; name: string; before: MasteryState; after: MasteryState }
export interface AttemptView { id: string; attemptNumber: number; submittedAtLabel: string; score: number; correct: number; total: number }

export type AssessmentItemView =
    | { id: string; type: 'quiz'; title: string; kind: QuizKind; timeLimitSeconds: number; attemptsLabel: string; lastScore?: number | null }
    | { id: string; type: 'assignment'; title: string; deadlineLabel: string; deadlineState: 'open' | 'soon' | 'closed'; submissionLabel: string; grade?: number | null };

export interface AssignmentView { id: string; title: string; instructions: string[]; deadlineLabel: string; deadlineState: 'open' | 'soon' | 'closed'; lessonTitle?: string }
export interface SubmissionView { fileName: string; submittedAtLabel: string; graded?: { grade: number; feedback: string; gradedAtLabel: string } }

export const quizKindLabel: Record<QuizKind, string> = { diagnostic: 'Placement diagnostic', practice: 'Practice quiz', test: 'Test' };
