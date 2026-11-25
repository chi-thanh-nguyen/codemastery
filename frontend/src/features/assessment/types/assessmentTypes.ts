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

export interface B3QuizSummary {
    id: string;
    courseId: string;
    title: string;
    kind: QuizKind;
    timeLimitSeconds: number;
    published: boolean;
    questionCount: number;
}

export interface B3QuizSummaryPage {
    content: B3QuizSummary[];
    page: number;
    size: number;
    totalElements: number;
    totalPages: number;
}

export interface B3AttemptQuestion {
    questionId: string;
    position: number;
    prompt: string;
    kind: QuestionKind;
    options: string[];
    selectedOptions: boolean[];
}

export interface B3Attempt {
    attemptId: string;
    quizId: string;
    courseId: string;
    attemptNumber: number;
    startedAt: string;
    expiresAt: string;
    status: 'active' | 'overdue';
    questions: B3AttemptQuestion[];
}

export interface B3AttemptSummary {
    attemptId: string;
    quizId: string;
    courseId: string;
    attemptNumber: number;
    startedAt: string;
    expiresAt: string;
    status: 'active' | 'overdue' | 'submitted';
    submittedAt: string | null;
    scorePercentage: number | null;
}

export interface B3AttemptHistory {
    content: B3AttemptSummary[];
    page: number;
    size: number;
    totalElements: number;
    totalPages: number;
}

export interface B3AttemptResultAnswer {
    questionId: string;
    position: number;
    prompt: string;
    kind: QuestionKind;
    options: string[];
    selectedOptions: boolean[];
    correct: boolean;
}

export interface B3AttemptResult {
    attemptId: string;
    quizId: string;
    courseId: string;
    attemptNumber: number;
    startedAt: string;
    expiresAt: string;
    status: 'submitted';
    submittedAt: string;
    scorePercentage: number;
    answers: B3AttemptResultAnswer[];
}

export type B3AttemptRead = B3Attempt | B3AttemptResult;

export interface B3AnswerEntry {
    questionId: string;
    selectedOptions: boolean[];
}

export interface B3AnswersWrite {
    answers: B3AnswerEntry[];
}

export type SaveMachineState =
    | 'Saved'
    | 'Pending'
    | 'Saving'
    | 'Checking'
    | 'Check failed'
    | 'Reconcile'
    | 'Time is up'
    | 'No access';