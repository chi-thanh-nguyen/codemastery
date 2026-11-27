import { request } from '../../../../shared/api/httpClient';
import { ApiError, isRecord, protocolError } from '../../../../shared/types/apiTypes';

/*
 * CT23 & CT10 Instructor authoring client (ADR007 §§4–7, ADR010 §§8, 12; OpenAPI b3* and b5* owner
 * operations). Every response is decoded strictly: exact members, UUID identities bound to the
 * request, question kind/mask rules, RFC 3339 timestamps and coherent pages. Ordering is the
 * server's (questions by UUID; quizzes and assignments by title then UUID); the client never sorts,
 * filters or derives usage, which no owner DTO returns (DESIGN.md A16).
 */
export type QuestionKind = 'single_choice' | 'multiple_choice' | 'true_false';
export type QuestionDifficulty = 'easy' | 'medium' | 'hard';
export type QuizKind = 'diagnostic' | 'practice' | 'test';
export type AssignmentPublicationStatus = 'hidden' | 'published';

export interface AuthoringRequestOptions { signal?: AbortSignal; isCurrent?: () => boolean }

export interface B3QuestionWrite {
    skillId: string;
    prompt: string;
    kind: QuestionKind;
    difficulty: QuestionDifficulty;
    options: string[];
    correctOptions: boolean[];
}
export interface B3Question extends B3QuestionWrite { id: string }
export interface B3Page<T> { content: T[]; page: number; size: number; totalElements: number; totalPages: number }
export type B3QuestionPage = B3Page<B3Question>;

export interface B3QuizWrite { title: string; kind: QuizKind; timeLimitSeconds: number; questionIds: string[] }
export interface B3QuizQuestion { position: number; question: B3Question }
export interface B3QuizOwned {
    id: string;
    courseId: string;
    title: string;
    kind: QuizKind;
    timeLimitSeconds: number;
    published: boolean;
    questionCount: number;
    /** Ordered by `position` (1…n) exactly as returned. */
    questions: B3QuizQuestion[];
}
export type B3QuizOwnedPage = B3Page<B3QuizOwned>;

export interface B5AssignmentWrite {
    title: string;
    instructions: string;
    deadlineAt: string;
    lessonId: string | null;
}
export interface B5OwnedAssignment {
    id: string;
    courseId: string;
    lessonId: string | null;
    title: string;
    instructions: string;
    deadlineAt: string;
    publicationStatus: AssignmentPublicationStatus;
    submissionCount: number;
    gradedCount: number;
}
export type B5OwnedAssignmentPage = B3Page<B5OwnedAssignment>;

/** OpenAPI `B3QuizWrite.timeLimitSeconds` and `B3QuizOwned` integer bounds. */
export const MAX_TIME_LIMIT_SECONDS = 2147483647;
export const MAX_PAGE_SIZE = 100;
export const DEFAULT_PAGE_SIZE = 20;

const uuid = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
const rfc3339Micros = /^(\d{4})-(\d{2})-(\d{2})[Tt](\d{2}):(\d{2}):(\d{2})(?:\.\d{1,6})?([Zz]|[+-]\d{2}:\d{2})$/;
const questionKinds: readonly QuestionKind[] = ['single_choice', 'multiple_choice', 'true_false'];
const difficulties: readonly QuestionDifficulty[] = ['easy', 'medium', 'hard'];
const quizKinds: readonly QuizKind[] = ['diagnostic', 'practice', 'test'];
const publicationStatuses: readonly AssignmentPublicationStatus[] = ['hidden', 'published'];
const integer = (value: unknown, minimum: number, maximum = Number.MAX_SAFE_INTEGER): value is number =>
    Number.isSafeInteger(value) && (value as number) >= minimum && (value as number) <= maximum;
const text = (value: unknown): value is string => typeof value === 'string' && /\S/.test(value);
const id = (value: unknown): value is string => typeof value === 'string' && uuid.test(value);
const same = (left: string, right: string) => left.toLowerCase() === right.toLowerCase();

function rfc3339Valid(value: unknown): value is string {
    if (typeof value !== 'string') return false;
    const match = rfc3339Micros.exec(value);
    if (!match) return false;
    const year = Number(match[1]), month = Number(match[2]), day = Number(match[3]);
    const hour = Number(match[4]), minute = Number(match[5]), second = Number(match[6]);
    const leap = year % 4 === 0 && (year % 100 !== 0 || year % 400 === 0);
    const days = [31, leap ? 29 : 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31];
    if (month < 1 || month > 12 || day < 1 || day > days[month - 1] || hour > 23 || minute > 59 || second > 59) return false;
    const instant = new Date(value.toUpperCase());
    return !Number.isNaN(instant.getTime()) && instant.getUTCFullYear() >= 0 && instant.getUTCFullYear() <= 9999;
}

function exact(value: unknown, keys: readonly string[]): Record<string, unknown> {
    if (!isRecord(value) || Object.keys(value).length !== keys.length || !keys.every((key) => Object.hasOwn(value, key))) throw protocolError();
    return value;
}

/**
 * The V004 kind/mask rules (OpenAPI `B3QuestionWrite`): at least two non-blank options with a mask of the
 * same length; true/false labels exactly True, False; single choice and true/false exactly one correct
 * option, multiple choice at least one.
 */
export function questionShapeValid(kind: QuestionKind, options: readonly string[], correct: readonly boolean[]) {
    if (options.length < 2 || correct.length !== options.length || !options.every(text)) return false;
    const marked = correct.filter(Boolean).length;
    if (kind === 'true_false') return options.length === 2 && options[0] === 'True' && options[1] === 'False' && marked === 1;
    return kind === 'single_choice' ? marked === 1 : marked >= 1;
}

function decodeQuestion(value: unknown): B3Question {
    const row = exact(value, ['id', 'skillId', 'prompt', 'kind', 'difficulty', 'options', 'correctOptions']);
    if (!id(row.id) || !id(row.skillId) || !text(row.prompt) || !questionKinds.includes(row.kind as QuestionKind) ||
        !difficulties.includes(row.difficulty as QuestionDifficulty) || !Array.isArray(row.options) || !Array.isArray(row.correctOptions) ||
        !row.options.every((option) => typeof option === 'string') || !row.correctOptions.every((flag) => typeof flag === 'boolean') ||
        !questionShapeValid(row.kind as QuestionKind, row.options as string[], row.correctOptions as boolean[])) throw protocolError();
    return { id: row.id, skillId: row.skillId, prompt: row.prompt, kind: row.kind as QuestionKind, difficulty: row.difficulty as QuestionDifficulty,
        options: [...row.options as string[]], correctOptions: [...row.correctOptions as boolean[]] };
}

function decodeQuiz(value: unknown): B3QuizOwned {
    const row = exact(value, ['id', 'courseId', 'title', 'kind', 'timeLimitSeconds', 'published', 'questionCount', 'questions']);
    if (!id(row.id) || !id(row.courseId) || !text(row.title) || !quizKinds.includes(row.kind as QuizKind) ||
        !integer(row.timeLimitSeconds, 1, MAX_TIME_LIMIT_SECONDS) || typeof row.published !== 'boolean' ||
        !integer(row.questionCount, 0, MAX_TIME_LIMIT_SECONDS) || !Array.isArray(row.questions)) throw protocolError();
    const seen = new Set<string>();
    const questions = row.questions.map((item, index) => {
        const entry = exact(item, ['position', 'question']);
        // Membership is explicit and ordered: positions 1…n in the returned order, each question once.
        if (entry.position !== index + 1) throw protocolError();
        const question = decodeQuestion(entry.question), key = question.id.toLowerCase();
        if (seen.has(key)) throw protocolError();
        seen.add(key);
        return { position: index + 1, question };
    });
    if (row.questionCount !== questions.length) throw protocolError();
    return { id: row.id, courseId: row.courseId, title: row.title, kind: row.kind as QuizKind, timeLimitSeconds: row.timeLimitSeconds,
        published: row.published, questionCount: row.questionCount, questions };
}

function decodeAssignment(value: unknown): B5OwnedAssignment {
    const row = exact(value, ['id', 'courseId', 'lessonId', 'title', 'instructions', 'deadlineAt', 'publicationStatus', 'submissionCount', 'gradedCount']);
    if (!id(row.id) || !id(row.courseId) || (row.lessonId !== null && !id(row.lessonId)) ||
        !text(row.title) || !text(row.instructions) || !rfc3339Valid(row.deadlineAt) ||
        !publicationStatuses.includes(row.publicationStatus as AssignmentPublicationStatus) ||
        !integer(row.submissionCount, 0, MAX_TIME_LIMIT_SECONDS) ||
        !integer(row.gradedCount, 0, row.submissionCount as number)) throw protocolError();
    return {
        id: row.id,
        courseId: row.courseId,
        lessonId: row.lessonId as string | null,
        title: row.title,
        instructions: row.instructions,
        deadlineAt: row.deadlineAt,
        publicationStatus: row.publicationStatus as AssignmentPublicationStatus,
        submissionCount: row.submissionCount as number,
        gradedCount: row.gradedCount as number,
    };
}

function pageDecoder<T>(item: (value: unknown) => T, page: number, size: number) {
    return (value: unknown): B3Page<T> => {
        const row = exact(value, ['content', 'page', 'size', 'totalElements', 'totalPages']);
        // CT18.minimum envelope invariants (PageResponse): the requested page and size echoed, a page count
        // derived from the total, and no content beyond the size, the total or the last page.
        if (!Array.isArray(row.content) || row.page !== page || row.size !== size || !integer(row.totalElements, 0) ||
            row.totalPages !== Math.ceil(row.totalElements / size) || row.content.length > size || row.content.length > row.totalElements ||
            row.content.length > 0 && page * size >= row.totalElements) throw protocolError();
        return { content: row.content.map(item), page, size, totalElements: row.totalElements, totalPages: row.totalPages as number };
    };
}

const questionFor = (questionId: string) => (value: unknown) => {
    const question = decodeQuestion(value);
    if (!same(question.id, questionId)) throw protocolError();
    return question;
};
const quizFor = (courseId: string, quizId?: string) => (value: unknown) => {
    const quiz = decodeQuiz(value);
    if (!same(quiz.courseId, courseId) || quizId !== undefined && !same(quiz.id, quizId)) throw protocolError();
    return quiz;
};
const assignmentFor = (courseId: string, assignmentId?: string) => (value: unknown) => {
    const assignment = decodeAssignment(value);
    if (!same(assignment.courseId, courseId) || assignmentId !== undefined && !same(assignment.id, assignmentId)) throw protocolError();
    return assignment;
};
const pageCourse = <T extends { courseId: string }>(courseId: string, decode: (value: unknown) => B3Page<T>) => (value: unknown) => {
    const result = decode(value);
    if (result.content.some((entry) => !same(entry.courseId, courseId))) throw protocolError();
    return result;
};

const course = (courseId: string) => `/courses/${encodeURIComponent(courseId)}`;
const invalidPage = () => Promise.reject(new ApiError('validation', 'Please check the requested page.'));
const pageQuery = (page: number, size: number) => new URLSearchParams({ page: String(page), size: String(size) });
const pageValid = (page: number, size: number) => integer(page, 0) && integer(size, 1, MAX_PAGE_SIZE);

// --- CT23.questions (AS05) ---

/** `b3ListQuestions`: one owner page in UUID order. */
export function listQuestions(courseId: string, page = 0, size = DEFAULT_PAGE_SIZE, options: AuthoringRequestOptions = {}): Promise<B3QuestionPage> {
    if (!pageValid(page, size)) return invalidPage();
    return request(`${course(courseId)}/questions`, { ...options, method: 'GET', auth: 'authenticated', expectedStatus: 200,
        decode: pageDecoder(decodeQuestion, page, size), query: pageQuery(page, size) });
}
/** `b3CreateQuestion`. */
export function createQuestion(courseId: string, payload: B3QuestionWrite, options: AuthoringRequestOptions = {}): Promise<B3Question> {
    return request(`${course(courseId)}/questions`, { ...options, method: 'POST', auth: 'authenticated', expectedStatus: 201, decode: decodeQuestion, body: payload });
}
/** `b3ReadQuestion`. */
export function readQuestion(courseId: string, questionId: string, options: AuthoringRequestOptions = {}): Promise<B3Question> {
    return request(`${course(courseId)}/questions/${encodeURIComponent(questionId)}`, { ...options, method: 'GET', auth: 'authenticated', expectedStatus: 200,
        decode: questionFor(questionId) });
}
/** `b3ReplaceUnusedQuestion`: a used question is refused with 409; nothing is replaced automatically. */
export function replaceUnusedQuestion(courseId: string, questionId: string, payload: B3QuestionWrite, options: AuthoringRequestOptions = {}): Promise<B3Question> {
    return request(`${course(courseId)}/questions/${encodeURIComponent(questionId)}`, { ...options, method: 'PUT', auth: 'authenticated', expectedStatus: 200,
        decode: questionFor(questionId), body: payload });
}
/** `b3DeleteUnusedQuestion`: returns the deleted definition after commit. */
export function deleteUnusedQuestion(courseId: string, questionId: string, options: AuthoringRequestOptions = {}): Promise<B3Question> {
    return request(`${course(courseId)}/questions/${encodeURIComponent(questionId)}`, { ...options, method: 'DELETE', auth: 'authenticated', expectedStatus: 200,
        decode: questionFor(questionId) });
}

// --- CT23.composition / CT23.publication (AS06) ---

/** `b3ListOwnedQuizzes`: hidden and published quizzes, title then UUID. */
export function listOwnedQuizzes(courseId: string, page = 0, size = DEFAULT_PAGE_SIZE, options: AuthoringRequestOptions = {}): Promise<B3QuizOwnedPage> {
    if (!pageValid(page, size)) return invalidPage();
    return request(`${course(courseId)}/owned-quizzes`, { ...options, method: 'GET', auth: 'authenticated', expectedStatus: 200,
        decode: pageCourse(courseId, pageDecoder(decodeQuiz, page, size)), query: pageQuery(page, size) });
}
/** `b3CreateQuiz`: always a hidden quiz; an empty membership is a valid draft. */
export function createQuiz(courseId: string, payload: B3QuizWrite, options: AuthoringRequestOptions = {}): Promise<B3QuizOwned> {
    return request(`${course(courseId)}/quizzes`, { ...options, method: 'POST', auth: 'authenticated', expectedStatus: 201, decode: quizFor(courseId), body: payload });
}
/** `b3ReadOwnedQuiz`. */
export function readOwnedQuiz(courseId: string, quizId: string, options: AuthoringRequestOptions = {}): Promise<B3QuizOwned> {
    return request(`${course(courseId)}/quizzes/${encodeURIComponent(quizId)}`, { ...options, method: 'GET', auth: 'authenticated', expectedStatus: 200,
        decode: quizFor(courseId, quizId) });
}
/** `b3ReplaceUnusedHiddenQuiz`: published or attempted quizzes are refused with 409. */
export function replaceUnusedHiddenQuiz(courseId: string, quizId: string, payload: B3QuizWrite, options: AuthoringRequestOptions = {}): Promise<B3QuizOwned> {
    return request(`${course(courseId)}/quizzes/${encodeURIComponent(quizId)}`, { ...options, method: 'PUT', auth: 'authenticated', expectedStatus: 200,
        decode: quizFor(courseId, quizId), body: payload });
}
/** `b3DeleteUnusedHiddenQuiz`: returns the deleted definition after commit. */
export function deleteUnusedHiddenQuiz(courseId: string, quizId: string, options: AuthoringRequestOptions = {}): Promise<B3QuizOwned> {
    return request(`${course(courseId)}/quizzes/${encodeURIComponent(quizId)}`, { ...options, method: 'DELETE', auth: 'authenticated', expectedStatus: 200,
        decode: quizFor(courseId, quizId) });
}
/** `b3SetQuizPublication`: an idempotent explicit state command; publishing an empty quiz is refused with 409. */
export function setQuizPublication(courseId: string, quizId: string, published: boolean, options: AuthoringRequestOptions = {}): Promise<B3QuizOwned> {
    return request(`${course(courseId)}/quizzes/${encodeURIComponent(quizId)}/publication`, { ...options, method: 'PUT', auth: 'authenticated', expectedStatus: 200,
        decode: (value) => {
            const quiz = quizFor(courseId, quizId)(value);
            if (quiz.published !== published) throw protocolError();
            return quiz;
        }, body: { publicationStatus: published ? 'published' : 'hidden' } });
}

// --- CT10.rest owner assignments (AS09) ---

/** `b5ListOwnedAssignments`: hidden and published assignments, title COLLATE C then UUID. */
export function listOwnedAssignments(courseId: string, page = 0, size = DEFAULT_PAGE_SIZE, options: AuthoringRequestOptions = {}): Promise<B5OwnedAssignmentPage> {
    if (!pageValid(page, size)) return invalidPage();
    return request(`${course(courseId)}/owned-assignments`, { ...options, method: 'GET', auth: 'authenticated', expectedStatus: 200,
        decode: pageCourse(courseId, pageDecoder(decodeAssignment, page, size)), query: pageQuery(page, size) });
}
/** `b5CreateAssignment`: creates a hidden assignment. */
export function createAssignment(courseId: string, payload: B5AssignmentWrite, options: AuthoringRequestOptions = {}): Promise<B5OwnedAssignment> {
    return request(`${course(courseId)}/assignments`, { ...options, method: 'POST', auth: 'authenticated', expectedStatus: 201,
        decode: assignmentFor(courseId), body: payload });
}
/** `b5ReadOwnedAssignment`. */
export function readOwnedAssignment(courseId: string, assignmentId: string, options: AuthoringRequestOptions = {}): Promise<B5OwnedAssignment> {
    return request(`${course(courseId)}/owned-assignments/${encodeURIComponent(assignmentId)}`, { ...options, method: 'GET', auth: 'authenticated', expectedStatus: 200,
        decode: assignmentFor(courseId, assignmentId) });
}
/** `b5ReplaceAssignment`: full replacement of title, instructions, deadlineAt and lessonId. */
export function replaceAssignment(courseId: string, assignmentId: string, payload: B5AssignmentWrite, options: AuthoringRequestOptions = {}): Promise<B5OwnedAssignment> {
    return request(`${course(courseId)}/owned-assignments/${encodeURIComponent(assignmentId)}`, { ...options, method: 'PUT', auth: 'authenticated', expectedStatus: 200,
        decode: assignmentFor(courseId, assignmentId), body: payload });
}
/** `b5SetAssignmentPublication`: explicit `hidden` or `published` assignment publication status. */
export function setAssignmentPublication(courseId: string, assignmentId: string, publicationStatus: AssignmentPublicationStatus, options: AuthoringRequestOptions = {}): Promise<B5OwnedAssignment> {
    return request(`${course(courseId)}/owned-assignments/${encodeURIComponent(assignmentId)}/publication`, { ...options, method: 'PUT', auth: 'authenticated', expectedStatus: 200,
        decode: (value) => {
            const assignment = assignmentFor(courseId, assignmentId)(value);
            if (assignment.publicationStatus !== publicationStatus) throw protocolError();
            return assignment;
        }, body: { publicationStatus } });
}
