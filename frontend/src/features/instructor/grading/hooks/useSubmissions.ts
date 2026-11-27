import { useState } from 'react';
import { useOwnerRead } from '../../course-management/hooks/useAssessmentEditor';
import { decodeSubmission, findSubmission, listSubmissions } from '../api/gradingApi';
import type { SubmissionSummary } from '../types/gradingTypes';

/** Route queries belong to FE17 under CT16.routes; selected by explicit Leader delegation (G4). */
export const gradingPath = (courseId?: string, assignmentId?: string, submissionId?: string) => {
    const query = new URLSearchParams();
    if (courseId) query.set('courseId', courseId);
    if (assignmentId) query.set('assignmentId', assignmentId);
    return `/teach/grading${submissionId ? `/${encodeURIComponent(submissionId)}` : ''}${query.size ? `?${query}` : ''}`;
};
interface ReviewedContext { generation: number; courseId: string; assignmentId: string; row: SubmissionSummary; title: string }
// A random state reference is useful only in this document, and never serializes authoritative rows to history.
const contexts = new Map<string, ReviewedContext>();
export function reviewedState(value: ReviewedContext) {
    const key = crypto.randomUUID();
    contexts.set(key, { ...value, row: { ...value.row } });
    if (contexts.size > 100) contexts.delete(contexts.keys().next().value!);
    return { gradingContext: key };
}
export function readReviewedState(state: unknown, generation: number, courseId: string, assignmentId: string, submissionId: string): ReviewedContext | null {
    const key = (state as { gradingContext?: unknown } | null)?.gradingContext;
    const value = typeof key === 'string' ? contexts.get(key) : null;
    if (!value || value.generation !== generation || value.courseId !== courseId || value.assignmentId !== assignmentId) return null;
    try { return { ...value, row: decodeSubmission(value.row, assignmentId, submissionId) }; } catch { return null; }
}
export function useSubmissions(courseId: string, assignmentId: string, page: number) {
    return useOwnerRead(`submissions|${courseId}|${assignmentId}|${page}`, options => listSubmissions(courseId, assignmentId, page, 20, options));
}
export function useReviewedSubmission(courseId: string, assignmentId: string, submissionId: string, initial: SubmissionSummary | null) {
    // A distinct read identity synchronously withdraws readiness on EVERY recovery/retry.
    const [revision, setRevision] = useState(0);
    const read = useOwnerRead(`reviewed-submission|${courseId}|${assignmentId}|${submissionId}|${revision}`, options =>
        revision === 0 && initial ? Promise.resolve(initial) : findSubmission(courseId, assignmentId, submissionId, options));
    return { ...read, reload: () => setRevision(value => value + 1) };
}
