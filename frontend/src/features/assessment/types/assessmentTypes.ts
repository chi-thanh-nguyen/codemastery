/*
 * Presentation view models for assessment screens (S9–S13). NOT API contracts:
 * The wire contracts below are separate from presentation views. AS09 implements
 * CT10 assignment/submission and CT09 protected file endpoints; screens remain separate. The backend is the authority for time
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

/** AS09 wire contracts (OpenAPI B5*). Timestamps retain their exact microsecond text. */
export interface LearnerAssignmentSummary {
    id: string;
    courseId: string;
    lessonId: string | null;
    title: string;
    deadlineAt: string;
    open: boolean;
    submissionStatus: 'none' | 'submitted' | 'graded';
}
export interface LearnerSubmission {
    originalFilename: string;
    /** Echo this exact value as the grade precondition; never round-trip through Date. */
    submittedAt: string;
    graded: boolean;
    gradePercentage: number | null;
    feedback: string | null;
    gradedAt: string | null;
}
export interface LearnerAssignment extends Omit<LearnerAssignmentSummary, 'submissionStatus'> {
    instructions: string;
    submission: LearnerSubmission | null;
}
