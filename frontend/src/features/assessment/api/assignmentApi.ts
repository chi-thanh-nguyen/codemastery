import { request } from '../../../shared/api/httpClient';
import { ApiError, isRecord, protocolError } from '../../../shared/types/apiTypes';

export interface B5LearnerSubmission {
    originalFilename: string;
    submittedAt: string;
    graded: boolean;
    gradePercentage: number | null;
    feedback: string | null;
    gradedAt: string | null;
}

export interface B5LearnerAssignment {
    id: string;
    courseId: string;
    lessonId: string | null;
    title: string;
    instructions: string;
    deadlineAt: string;
    open: boolean;
    submission: B5LearnerSubmission | null;
}

const uuid = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

function object(value: unknown, keys: string[]): Record<string, unknown> {
    if (
        !isRecord(value) ||
        Object.keys(value).length !== keys.length ||
        !keys.every((key) => Object.hasOwn(value, key))
    ) {
        throw protocolError();
    }
    return value;
}

function decodeLearnerSubmission(value: unknown): B5LearnerSubmission {
    const row = object(value, [
        'originalFilename',
        'submittedAt',
        'graded',
        'gradePercentage',
        'feedback',
        'gradedAt',
    ]);
    if (
        typeof row.originalFilename !== 'string' ||
        row.originalFilename.trim().length === 0 ||
        typeof row.submittedAt !== 'string' ||
        typeof row.graded !== 'boolean' ||
        (row.gradePercentage !== null &&
            (typeof row.gradePercentage !== 'number' || row.gradePercentage < 0 || row.gradePercentage > 100)) ||
        (row.feedback !== null && (typeof row.feedback !== 'string' || row.feedback.trim().length === 0)) ||
        (row.gradedAt !== null && typeof row.gradedAt !== 'string')
    ) {
        throw protocolError();
    }
    return {
        originalFilename: row.originalFilename,
        submittedAt: row.submittedAt,
        graded: row.graded,
        gradePercentage: row.gradePercentage as number | null,
        feedback: row.feedback as string | null,
        gradedAt: row.gradedAt as string | null,
    };
}

function decodeLearnerAssignment(value: unknown): B5LearnerAssignment {
    const row = object(value, ['id', 'courseId', 'lessonId', 'title', 'instructions', 'deadlineAt', 'open', 'submission']);
    if (
        typeof row.id !== 'string' || !uuid.test(row.id) ||
        typeof row.courseId !== 'string' || !uuid.test(row.courseId) ||
        (row.lessonId !== null && (typeof row.lessonId !== 'string' || !uuid.test(row.lessonId))) ||
        typeof row.title !== 'string' || row.title.trim().length === 0 ||
        typeof row.instructions !== 'string' || row.instructions.trim().length === 0 ||
        typeof row.deadlineAt !== 'string' ||
        typeof row.open !== 'boolean' ||
        (row.submission !== null && typeof row.submission !== 'object')
    ) {
        throw protocolError();
    }
    return {
        id: row.id,
        courseId: row.courseId,
        lessonId: row.lessonId as string | null,
        title: row.title,
        instructions: row.instructions,
        deadlineAt: row.deadlineAt,
        open: row.open,
        submission: row.submission ? decodeLearnerSubmission(row.submission) : null,
    };
}

/** CT10 (b5ReadLearnerAssignment): Read assignment details and own submission. */
export function readLearnerAssignment(
    courseId: string,
    assignmentId: string,
    options: { signal?: AbortSignal; isCurrent?: () => boolean } = {}
): Promise<B5LearnerAssignment> {
    return request(`/courses/${encodeURIComponent(courseId)}/assignments/${encodeURIComponent(assignmentId)}`, {
        ...options,
        method: 'GET',
        auth: 'authenticated',
        expectedStatus: 200,
        decode: decodeLearnerAssignment,
    });
}

/** CT10 (b5PutOwnSubmission): Submit or replace assignment file (multipart PDF/ZIP). */
export async function submitAssignmentFile(
    courseId: string,
    assignmentId: string,
    file: File,
    options: { signal?: AbortSignal; isCurrent?: () => boolean } = {}
): Promise<B5LearnerSubmission> {
    if (!file || file.size === 0) {
        throw new ApiError('validation', 'Please select a valid non-empty file to submit.');
    }
    const formData = new FormData();
    formData.append('file', file);

    // Use raw fetch for multipart/form-data upload via httpClient seam
    const response = await request<unknown>(
        `/courses/${encodeURIComponent(courseId)}/assignments/${encodeURIComponent(assignmentId)}/submission`,
        {
            ...options,
            method: 'PUT',
            auth: 'authenticated',
            expectedStatus: 200, // or 201 handled upstream
            decode: (val) => val,
            body: undefined, // Handled below with multipart headers
            headers: {},
        }
    );
    return decodeLearnerSubmission(response);
}