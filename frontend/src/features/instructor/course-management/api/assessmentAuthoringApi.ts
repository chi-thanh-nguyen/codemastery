import { request } from '../../../../shared/api/httpClient';
import { ApiError, isRecord, protocolError } from '../../../../shared/types/apiTypes';
import type { Difficulty } from '../types/courseManagementTypes';

export type QuestionKind = 'single_choice' | 'multiple_choice' | 'true_false';

export interface AuthoringRequestOptions {
    signal?: AbortSignal;
    isCurrent?: () => boolean;
}

export type QuizKind = 'diagnostic' | 'practice' | 'test';

export interface B3QuestionWrite {
    skillId: string;
    prompt: string;
    kind: QuestionKind;
    difficulty: Difficulty;
    options: string[];
    correctOptions: boolean[];
}

export interface B3Question extends B3QuestionWrite {
    id: string;
}

export interface B3QuestionPage {
    content: B3Question[];
    page: number;
    size: number;
    totalElements: number;
    totalPages: number;
}

export interface B3QuizWrite {
    title: string;
    kind: QuizKind;
    timeLimitSeconds: number;
    questionIds: string[];
}

export interface B3QuizOwned {
    id: string;
    courseId: string;
    title: string;
    kind: QuizKind;
    timeLimitSeconds: number;
    published: boolean;
    questionCount: number;
    questions: {
        position: number;
        question: B3Question;
    }[];
}

export interface B3QuizOwnedPage {
    content: B3QuizOwned[];
    page: number;
    size: number;
    totalElements: number;
    totalPages: number;
}

export interface PublicationWrite {
    publicationStatus: 'hidden' | 'published';
}

const uuid = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
const questionKinds = new Set<QuestionKind>(['single_choice', 'multiple_choice', 'true_false']);
const difficulties = new Set<Difficulty>(['easy', 'medium', 'hard']);
const quizKinds = new Set<QuizKind>(['diagnostic', 'practice', 'test']);

const integer = (value: unknown, minimum: number): value is number =>
    Number.isSafeInteger(value) && (value as number) >= minimum;

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

function decodeQuestion(value: unknown): B3Question {
    const row = object(value, [
        'id',
        'skillId',
        'prompt',
        'kind',
        'difficulty',
        'options',
        'correctOptions',
    ]);
    if (
        typeof row.id !== 'string' ||
        !uuid.test(row.id) ||
        typeof row.skillId !== 'string' ||
        !uuid.test(row.skillId) ||
        typeof row.prompt !== 'string' ||
        row.prompt.trim().length === 0 ||
        typeof row.kind !== 'string' ||
        !questionKinds.has(row.kind as QuestionKind) ||
        typeof row.difficulty !== 'string' ||
        !difficulties.has(row.difficulty as Difficulty) ||
        !Array.isArray(row.options) ||
        row.options.length < 2 ||
        !row.options.every((opt) => typeof opt === 'string' && opt.trim().length > 0) ||
        !Array.isArray(row.correctOptions) ||
        row.correctOptions.length !== row.options.length ||
        !row.correctOptions.every((opt) => typeof opt === 'boolean')
    ) {
        throw protocolError();
    }
    return {
        id: row.id,
        skillId: row.skillId,
        prompt: row.prompt,
        kind: row.kind as QuestionKind,
        difficulty: row.difficulty as Difficulty,
        options: row.options as string[],
        correctOptions: row.correctOptions as boolean[],
    };
}

function decodeQuestionPage(value: unknown): B3QuestionPage {
    const row = object(value, ['content', 'page', 'size', 'totalElements', 'totalPages']);
    if (
        !Array.isArray(row.content) ||
        !integer(row.page, 0) ||
        !integer(row.size, 1) ||
        row.size > 100 ||
        !integer(row.totalElements, 0) ||
        !integer(row.totalPages, 0)
    ) {
        throw protocolError();
    }
    return {
        content: row.content.map(decodeQuestion),
        page: row.page,
        size: row.size,
        totalElements: row.totalElements,
        totalPages: row.totalPages,
    };
}

function decodeQuizOwned(value: unknown): B3QuizOwned {
    const row = object(value, [
        'id',
        'courseId',
        'title',
        'kind',
        'timeLimitSeconds',
        'published',
        'questionCount',
        'questions',
    ]);
    if (
        typeof row.id !== 'string' ||
        !uuid.test(row.id) ||
        typeof row.courseId !== 'string' ||
        !uuid.test(row.courseId) ||
        typeof row.title !== 'string' ||
        row.title.trim().length === 0 ||
        typeof row.kind !== 'string' ||
        !quizKinds.has(row.kind as QuizKind) ||
        !integer(row.timeLimitSeconds, 1) ||
        typeof row.published !== 'boolean' ||
        !integer(row.questionCount, 0) ||
        !Array.isArray(row.questions)
    ) {
        throw protocolError();
    }

    const questions = row.questions.map((item) => {
        const qRow = object(item, ['position', 'question']);
        if (!integer(qRow.position, 1)) throw protocolError();
        return {
            position: qRow.position,
            question: decodeQuestion(qRow.question),
        };
    });

    return {
        id: row.id,
        courseId: row.courseId,
        title: row.title,
        kind: row.kind as QuizKind,
        timeLimitSeconds: row.timeLimitSeconds,
        published: row.published,
        questionCount: row.questionCount,
        questions,
    };
}

function decodeQuizOwnedPage(value: unknown): B3QuizOwnedPage {
    const row = object(value, ['content', 'page', 'size', 'totalElements', 'totalPages']);
    if (
        !Array.isArray(row.content) ||
        !integer(row.page, 0) ||
        !integer(row.size, 1) ||
        row.size > 100 ||
        !integer(row.totalElements, 0) ||
        !integer(row.totalPages, 0)
    ) {
        throw protocolError();
    }
    return {
        content: row.content.map(decodeQuizOwned),
        page: row.page,
        size: row.size,
        totalElements: row.totalElements,
        totalPages: row.totalPages,
    };
}

// --- Questions Operations (CT23.questions) ---

export function listQuestions(
    courseId: string,
    page = 0,
    size = 20,
    options: AuthoringRequestOptions = {}
): Promise<B3QuestionPage> {
    const query = new URLSearchParams({ page: String(page), size: String(size) });
    return request(`/courses/${encodeURIComponent(courseId)}/questions`, {
        ...options,
        method: 'GET',
        auth: 'authenticated',
        expectedStatus: 200,
        decode: decodeQuestionPage,
        query,
    });
}

export function createQuestion(
    courseId: string,
    payload: B3QuestionWrite,
    options: AuthoringRequestOptions = {}
): Promise<B3Question> {
    return request(`/courses/${encodeURIComponent(courseId)}/questions`, {
        ...options,
        method: 'POST',
        auth: 'authenticated',
        expectedStatus: 201,
        decode: decodeQuestion,
        body: payload,
    });
}

export function readQuestion(
    courseId: string,
    questionId: string,
    options: AuthoringRequestOptions = {}
): Promise<B3Question> {
    return request(`/courses/${encodeURIComponent(courseId)}/questions/${encodeURIComponent(questionId)}`, {
        ...options,
        method: 'GET',
        auth: 'authenticated',
        expectedStatus: 200,
        decode: decodeQuestion,
    });
}

export function replaceUnusedQuestion(
    courseId: string,
    questionId: string,
    payload: B3QuestionWrite,
    options: AuthoringRequestOptions = {}
): Promise<B3Question> {
    return request(`/courses/${encodeURIComponent(courseId)}/questions/${encodeURIComponent(questionId)}`, {
        ...options,
        method: 'PUT',
        auth: 'authenticated',
        expectedStatus: 200,
        decode: decodeQuestion,
        body: payload,
    });
}

export function deleteUnusedQuestion(
    courseId: string,
    questionId: string,
    options: AuthoringRequestOptions = {}
): Promise<B3Question> {
    return request(`/courses/${encodeURIComponent(courseId)}/questions/${encodeURIComponent(questionId)}`, {
        ...options,
        method: 'DELETE',
        auth: 'authenticated',
        expectedStatus: 200,
        decode: decodeQuestion,
    });
}

// --- Quizzes Operations (CT23.composition / publication) ---

export function listOwnedQuizzes(
    courseId: string,
    page = 0,
    size = 20,
    options: AuthoringRequestOptions = {}
): Promise<B3QuizOwnedPage> {
    const query = new URLSearchParams({ page: String(page), size: String(size) });
    return request(`/courses/${encodeURIComponent(courseId)}/owned-quizzes`, {
        ...options,
        method: 'GET',
        auth: 'authenticated',
        expectedStatus: 200,
        decode: decodeQuizOwnedPage,
        query,
    });
}

export function createQuiz(
    courseId: string,
    payload: B3QuizWrite,
    options: AuthoringRequestOptions = {}
): Promise<B3QuizOwned> {
    return request(`/courses/${encodeURIComponent(courseId)}/quizzes`, {
        ...options,
        method: 'POST',
        auth: 'authenticated',
        expectedStatus: 201,
        decode: decodeQuizOwned,
        body: payload,
    });
}

export function readOwnedQuiz(
    courseId: string,
    quizId: string,
    options: AuthoringRequestOptions = {}
): Promise<B3QuizOwned> {
    return request(`/courses/${encodeURIComponent(courseId)}/quizzes/${encodeURIComponent(quizId)}`, {
        ...options,
        method: 'GET',
        auth: 'authenticated',
        expectedStatus: 200,
        decode: decodeQuizOwned,
    });
}

export function replaceUnusedHiddenQuiz(
    courseId: string,
    quizId: string,
    payload: B3QuizWrite,
    options: AuthoringRequestOptions = {}
): Promise<B3QuizOwned> {
    return request(`/courses/${encodeURIComponent(courseId)}/quizzes/${encodeURIComponent(quizId)}`, {
        ...options,
        method: 'PUT',
        auth: 'authenticated',
        expectedStatus: 200,
        decode: decodeQuizOwned,
        body: payload,
    });
}

export function deleteUnusedHiddenQuiz(
    courseId: string,
    quizId: string,
    options: AuthoringRequestOptions = {}
): Promise<B3QuizOwned> {
    return request(`/courses/${encodeURIComponent(courseId)}/quizzes/${encodeURIComponent(quizId)}`, {
        ...options,
        method: 'DELETE',
        auth: 'authenticated',
        expectedStatus: 200,
        decode: decodeQuizOwned,
    });
}

export function setQuizPublication(
    courseId: string,
    quizId: string,
    published: boolean,
    options: AuthoringRequestOptions = {}
): Promise<B3QuizOwned> {
    const body: PublicationWrite = {
        publicationStatus: published ? 'published' : 'hidden',
    };
    return request(`/courses/${encodeURIComponent(courseId)}/quizzes/${encodeURIComponent(quizId)}/publication`, {
        ...options,
        method: 'PUT',
        auth: 'authenticated',
        expectedStatus: 200,
        decode: decodeQuizOwned,
        body,
    });
}