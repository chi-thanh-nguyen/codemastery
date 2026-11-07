/*
 * Presentation view models for grading (S21). NOT API contracts: submission listing,
 * download (CT09) and grade/feedback operations are DEFERRED. Grading saves are
 * immutable once recorded (data-model §11) and do not update skill mastery.
 */
export interface SubmissionRow { id: string; learnerName: string; assignmentTitle: string; courseTitle: string; submittedAtLabel: string; fileName: string; graded?: { grade: number; feedback: string; gradedAtLabel: string } }
