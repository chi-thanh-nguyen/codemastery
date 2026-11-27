/*
 * Presentation view models for grading (S21). NOT API contracts: submission listing,
 * download (CT09) and grade/feedback wire contracts are implemented by AS09.
 * CT10 allows regrading with the exact submittedAt precondition; grades never update mastery.
 */
export interface SubmissionRow { id: string; learnerName: string; assignmentTitle: string; courseTitle: string; submittedAtLabel: string; fileName: string; graded?: { grade: number; feedback: string; gradedAtLabel: string } }

/** OpenAPI B5SubmissionSummary; backend owner admission applies to every read. */
export interface SubmissionSummary {
    id: string;
    assignmentId: string;
    learnerId: string;
    learnerDisplayName: string | null;
    originalFilename: string;
    /** Opaque microsecond token, echoed byte for byte when grading. */
    submittedAt: string;
    graded: boolean;
    gradePercentage: number | null;
    feedback: string | null;
    gradedAt: string | null;
}
export interface GradeSubmissionWrite {
    gradePercentage: number;
    feedback: string;
    submittedAt: string;
}

export type SubmissionPage = import('../../../../shared/types/apiTypes').PageResponse<SubmissionSummary>;
