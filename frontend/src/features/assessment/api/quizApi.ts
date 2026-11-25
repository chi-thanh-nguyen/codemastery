import { request } from '../../../shared/api/httpClient';
import { ApiError, isRecord, protocolError } from '../../../shared/types/apiTypes';
import type {
    B3AnswersWrite,
    B3Attempt,
    B3AttemptHistory,
    B3AttemptQuestion,
    B3AttemptRead,
    B3AttemptResult,
    B3AttemptResultAnswer,
    B3AttemptSummary,
    B3QuizSummary,
    B3QuizSummaryPage,
    QuestionKind,
    QuizKind,
} from '../types/assessmentTypes';

export interface QuizRequestOptions {
    signal?: AbortSignal;
    isCurrent?: () => boolean;
}

const uuid = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
const questionKinds = new Set<QuestionKind>(['single_choice', 'multiple_choice', 'true_false']);
const quizKinds = new Set<QuizKind>(['diagnostic', 'practice', 'test']);

const integer = (value: unknown, minimum: number): value is number =>
    Number.isSafeInteger(value) && (value as number) >= minimum;

function object(value: unknown, keys: string[]): Record<string, unknown> {
    if (!isRecord(value) || Object.keys(value).length !== keys.length || !keys.every((key) => Object.hasOwn(value, key))) {
        throw protocolError();
    }
    return value;
}

function decodeQuizSummary(value: unknown): B3QuizSummary {
    const row = object(value, ['id', 'courseId', 'title', 'kind', 'timeLimitSeconds', 'published', 'questionCount']);
    if (
        typeof row.id !== 'string' || !uuid.test(row.id) ||
        typeof row.courseId !== 'string' || !uuid.test(row.courseId) ||
        typeof row.title !== 'string' || row.title.trim().length === 0 ||
        typeof row.kind !== 'string' || !quizKinds.has(row.kind as QuizKind) ||
        !integer(row.timeLimitSeconds, 1) || typeof row.published !== 'boolean' || !integer(row.questionCount, 0)
    ) {
        throw protocolError();
    }
    return {
        id: row.id,
        courseId: row.courseId,
        title: row.title,
        kind: row.kind as QuizKind,
        timeLimitSeconds: row.timeLimitSeconds,
        published: row.published,
        questionCount: row.questionCount,
    };
}

function decodeQuizSummaryPage(value: unknown): B3QuizSummaryPage {
    const row = object(value, ['content', 'page', 'size', 'totalElements', 'totalPages']);
    if (!Array.isArray(row.content) || !integer(row.page, 0) || !integer(row.size, 1) || row.size > 100 || !integer(row.totalElements, 0) || !integer(row.totalPages, 0)) {
        throw protocolError();
    }
    return {
        content: row.content.map(decodeQuizSummary),
        page: row.page,
        size: row.size,
        totalElements: row.totalElements,
        totalPages: row.totalPages,
    };
}

function decodeAttemptQuestion(value: unknown): B3AttemptQuestion {
    const row = object(value, ['questionId', 'position', 'prompt', 'kind', 'options', 'selectedOptions']);
    if (
        typeof row.questionId !== 'string' || !uuid.test(row.questionId) ||
        !integer(row.position, 1) || typeof row.prompt !== 'string' ||
        typeof row.kind !== 'string' || !questionKinds.has(row.kind as QuestionKind) ||
        !Array.isArray(row.options) || row.options.length < 2 || !row.options.every((o) => typeof o === 'string') ||
        !Array.isArray(row.selectedOptions) || row.selectedOptions.length !== row.options.length || !row.selectedOptions.every((s) => typeof s === 'boolean')
    ) {
        throw protocolError();
    }
    return {
        questionId: row.questionId,
        position: row.position,
        prompt: row.prompt,
        kind: row.kind as QuestionKind,
        options: row.options as string[],
        selectedOptions: row.selectedOptions as boolean[],
    };
}

function decodeAttempt(value: unknown): B3Attempt {
    const row = object(value, ['attemptId', 'quizId', 'courseId', 'attemptNumber', 'startedAt', 'expiresAt', 'status', 'questions']);
    if (
        typeof row.attemptId !== 'string' || !uuid.test(row.attemptId) ||
        typeof row.quizId !== 'string' || !uuid.test(row.quizId) ||
        typeof row.courseId !== 'string' || !uuid.test(row.courseId) ||
        !integer(row.attemptNumber, 1) || typeof row.startedAt !== 'string' || typeof row.expiresAt !== 'string' ||
        (row.status !== 'active' && row.status !== 'overdue') || !Array.isArray(row.questions) || row.questions.length < 1
    ) {
        throw protocolError();
    }
    return {
        attemptId: row.attemptId,
        quizId: row.quizId,
        courseId: row.courseId,
        attemptNumber: row.attemptNumber,
        startedAt: row.startedAt,
        expiresAt: row.expiresAt,
        status: row.status,
        questions: row.questions.map(decodeAttemptQuestion),
    };
}

function decodeResultAnswer(value: unknown): B3AttemptResultAnswer {
    const row = object(value, ['questionId', 'position', 'prompt', 'kind', 'options', 'selectedOptions', 'correct']);
    if (
        typeof row.questionId !== 'string' || !uuid.test(row.questionId) ||
        !integer(row.position, 1) || typeof row.prompt !== 'string' ||
        typeof row.kind !== 'string' || !questionKinds.has(row.kind as QuestionKind) ||
        !Array.isArray(row.options) || row.options.length < 2 || !row.options.every((o) => typeof o === 'string') ||
        !Array.isArray(row.selectedOptions) || row.selectedOptions.length !== row.options.length || !row.selectedOptions.every((s) => typeof s === 'boolean') ||
        typeof row.correct !== 'boolean'
    ) {
        throw protocolError();
    }
    return {
        questionId: row.questionId,
        position: row.position,
        prompt: row.prompt,
        kind: row.kind as QuestionKind,
        options: row.options as string[],
        selectedOptions: row.selectedOptions as boolean[],
        correct: row.correct,
    };
}

function decodeAttemptResult(value: unknown): B3AttemptResult {
    const row = object(value, ['attemptId', 'quizId', 'courseId', 'attemptNumber', 'startedAt', 'expiresAt', 'status', 'submittedAt', 'scorePercentage', 'answers']);
    if (
        typeof row.attemptId !== 'string' || !uuid.test(row.attemptId) ||
        typeof row.quizId !== 'string' || !uuid.test(row.quizId) ||
        typeof row.courseId !== 'string' || !uuid.test(row.courseId) ||
        !integer(row.attemptNumber, 1) || typeof row.startedAt !== 'string' || typeof row.expiresAt !== 'string' ||
        row.status !== 'submitted' || typeof row.submittedAt !== 'string' ||
        typeof row.scorePercentage !== 'number' || row.scorePercentage < 0 || row.scorePercentage > 100 ||
        !Array.isArray(row.answers) || row.answers.length < 1
    ) {
        throw protocolError();
    }
    return {
        attemptId: row.attemptId,
        quizId: row.quizId,
        courseId: row.courseId,
        attemptNumber: row.attemptNumber,
        startedAt: row.startedAt,
        expiresAt: row.expiresAt,
        status: 'submitted',
        submittedAt: row.submittedAt,
        scorePercentage: row.scorePercentage,
        answers: row.answers.map(decodeResultAnswer),
    };
}

function decodeAttemptRead(value: unknown): B3AttemptRead {
    if (!isRecord(value) || typeof value.status !== 'string') throw protocolError();
    if (value.status === 'submitted') return decodeAttemptResult(value);
    return decodeAttempt(value);
}

function decodeAttemptSummary(value: unknown): B3AttemptSummary {
    const row = object(value, ['attemptId', 'quizId', 'courseId', 'attemptNumber', 'startedAt', 'expiresAt', 'status', 'submittedAt', 'scorePercentage']);
    if (
        typeof row.attemptId !== 'string' || !uuid.test(row.attemptId) ||
        typeof row.quizId !== 'string' || !uuid.test(row.quizId) ||
        typeof row.courseId !== 'string' || !uuid.test(row.courseId) ||
        !integer(row.attemptNumber, 1) || typeof row.startedAt !== 'string' || typeof row.expiresAt !== 'string' ||
        (row.status !== 'active' && row.status !== 'overdue' && row.status !== 'submitted') ||
        (row.submittedAt !== null && typeof row.submittedAt !== 'string') ||
        (row.scorePercentage !== null && (typeof row.scorePercentage !== 'number' || row.scorePercentage < 0 || row.scorePercentage > 100))
    ) {
        throw protocolError();
    }
    return {
        attemptId: row.attemptId,
        quizId: row.quizId,
        courseId: row.courseId,
        attemptNumber: row.attemptNumber,
        startedAt: row.startedAt,
        expiresAt: row.expiresAt,
        status: row.status,
        submittedAt: row.submittedAt as string | null,
        scorePercentage: row.scorePercentage as number | null,
    };
}

function decodeAttemptHistory(value: unknown): B3AttemptHistory {
    const row = object(value, ['content', 'page', 'size', 'totalElements', 'totalPages']);
    if (!Array.isArray(row.content) || !integer(row.page, 0) || !integer(row.size, 1) || row.size > 100 || !integer(row.totalElements, 0) || !integer(row.totalPages, 0)) {
        throw protocolError();
    }
    return {
        content: row.content.map(decodeAttemptSummary),
        page: row.page,
        size: row.size,
        totalElements: row.totalElements,
        totalPages: row.totalPages,
    };
}

export function listLearnerQuizzes(courseId: string, page = 0, size = 20, options: QuizRequestOptions = {}): Promise<B3QuizSummaryPage> {
    if (!integer(page, 0) || !integer(size, 1) || size > 100) {
        return Promise.reject(new ApiError('validation', 'Please check the requested page.'));
    }
    return request(`/courses/${encodeURIComponent(courseId)}/quizzes`, {
        ...options,
        method: 'GET',
        auth: 'authenticated',
        expectedStatus: 200,
        decode: decodeQuizSummaryPage,
        query: new URLSearchParams({ page: String(page), size: String(size) }),
    });
}

export function listAttemptHistory(courseId: string, quizId: string, page = 0, size = 20, options: QuizRequestOptions = {}): Promise<B3AttemptHistory> {
    if (!integer(page, 0) || !integer(size, 1) || size > 100) {
        return Promise.reject(new ApiError('validation', 'Please check the requested page.'));
    }
    return request(`/courses/${encodeURIComponent(courseId)}/quizzes/${encodeURIComponent(quizId)}/attempts`, {
        ...options,
        method: 'GET',
        auth: 'authenticated',
        expectedStatus: 200,
        decode: decodeAttemptHistory,
        query: new URLSearchParams({ page: String(page), size: String(size) }),
    });
}

export function startQuizAttempt(courseId: string, quizId: string, options: QuizRequestOptions = {}): Promise<B3Attempt> {
    return request(`/courses/${encodeURIComponent(courseId)}/quizzes/${encodeURIComponent(quizId)}/attempts`, {
        ...options,
        method: 'POST',
        auth: 'authenticated',
        expectedStatus: 201,
        decode: decodeAttempt,
    });
}

export function readQuizAttempt(courseId: string, quizId: string, attemptId: string, options: QuizRequestOptions = {}): Promise<B3AttemptRead> {
    return request(`/courses/${encodeURIComponent(courseId)}/quizzes/${encodeURIComponent(quizId)}/attempts/${encodeURIComponent(attemptId)}`, {
        ...options,
        method: 'GET',
        auth: 'authenticated',
        expectedStatus: 200,
        decode: decodeAttemptRead,
    });
}

export function saveQuizAnswers(courseId: string, quizId: string, attemptId: string, payload: B3AnswersWrite, options: QuizRequestOptions = {}): Promise<B3Attempt> {
    return request(`/courses/${encodeURIComponent(courseId)}/quizzes/${encodeURIComponent(quizId)}/attempts/${encodeURIComponent(attemptId)}/answers`, {
        ...options,
        method: 'PUT',
        auth: 'authenticated',
        expectedStatus: 200,
        decode: decodeAttempt,
        body: payload,
    });
}

export function submitQuizAttempt(courseId: string, quizId: string, attemptId: string, payload: B3AnswersWrite, options: QuizRequestOptions = {}): Promise<B3AttemptResult> {
    return request(`/courses/${encodeURIComponent(courseId)}/quizzes/${encodeURIComponent(quizId)}/attempts/${encodeURIComponent(attemptId)}/submission`, {
        ...options,
        method: 'POST',
        auth: 'authenticated',
        expectedStatus: 200,
        decode: decodeAttemptResult,
        body: payload,
    });
}

export function readAttemptResult(courseId: string, quizId: string, attemptId: string, options: QuizRequestOptions = {}): Promise<B3AttemptResult> {
    return request(`/courses/${encodeURIComponent(courseId)}/quizzes/${encodeURIComponent(quizId)}/attempts/${encodeURIComponent(attemptId)}/result`, {
        ...options,
        method: 'GET',
        auth: 'authenticated',
        expectedStatus: 200,
        decode: decodeAttemptResult,
    });
}