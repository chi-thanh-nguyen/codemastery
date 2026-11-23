import { request } from '../../../shared/api/httpClient';
import { ApiError, isRecord, protocolError } from '../../../shared/types/apiTypes';
import type {
    ContentReportReceipt,
    ContentReportWrite,
    DiscussionAuthorRole,
    DiscussionPage,
    DiscussionPost,
    DiscussionPostWrite,
    DiscussionReply,
    DiscussionThread,
} from '../types/interactionTypes';

export interface DiscussionRequestOptions {
    signal?: AbortSignal;
    isCurrent?: () => boolean;
}

const uuid = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
const roles = new Set<DiscussionAuthorRole>(['Learner', 'Instructor', 'Administrator']);

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

function decodeRole(value: unknown): DiscussionAuthorRole | null {
    if (value === null) return null;
    if (typeof value === 'string' && roles.has(value as DiscussionAuthorRole)) {
        return value as DiscussionAuthorRole;
    }
    throw protocolError();
}

function decodeReply(value: unknown): DiscussionReply {
    const row = object(value, [
        'id',
        'parentPostId',
        'authorId',
        'authorDisplayName',
        'authorRole',
        'mine',
        'content',
        'createdAt',
    ]);
    if (
        typeof row.id !== 'string' ||
        !uuid.test(row.id) ||
        typeof row.parentPostId !== 'string' ||
        !uuid.test(row.parentPostId) ||
        typeof row.authorId !== 'string' ||
        !uuid.test(row.authorId) ||
        (row.authorDisplayName !== null && typeof row.authorDisplayName !== 'string') ||
        typeof row.mine !== 'boolean' ||
        typeof row.content !== 'string' ||
        row.content.trim().length === 0 ||
        typeof row.createdAt !== 'string'
    ) {
        throw protocolError();
    }
    return {
        id: row.id,
        parentPostId: row.parentPostId,
        authorId: row.authorId,
        authorDisplayName: row.authorDisplayName as string | null,
        authorRole: decodeRole(row.authorRole),
        mine: row.mine,
        content: row.content,
        createdAt: row.createdAt,
    };
}

function decodeThread(value: unknown): DiscussionThread {
    const row = object(value, [
        'id',
        'authorId',
        'authorDisplayName',
        'authorRole',
        'mine',
        'content',
        'createdAt',
        'replies',
    ]);
    if (
        typeof row.id !== 'string' ||
        !uuid.test(row.id) ||
        typeof row.authorId !== 'string' ||
        !uuid.test(row.authorId) ||
        (row.authorDisplayName !== null && typeof row.authorDisplayName !== 'string') ||
        typeof row.mine !== 'boolean' ||
        typeof row.content !== 'string' ||
        row.content.trim().length === 0 ||
        typeof row.createdAt !== 'string' ||
        !Array.isArray(row.replies)
    ) {
        throw protocolError();
    }
    return {
        id: row.id,
        authorId: row.authorId,
        authorDisplayName: row.authorDisplayName as string | null,
        authorRole: decodeRole(row.authorRole),
        mine: row.mine,
        content: row.content,
        createdAt: row.createdAt,
        replies: row.replies.map(decodeReply),
    };
}

function decodeDiscussionPage(value: unknown): DiscussionPage {
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
        content: row.content.map(decodeThread),
        page: row.page,
        size: row.size,
        totalElements: row.totalElements,
        totalPages: row.totalPages,
    };
}

function decodePost(value: unknown): DiscussionPost {
    const row = object(value, [
        'id',
        'lessonId',
        'parentPostId',
        'authorId',
        'authorDisplayName',
        'authorRole',
        'mine',
        'content',
        'createdAt',
    ]);
    if (
        typeof row.id !== 'string' ||
        !uuid.test(row.id) ||
        typeof row.lessonId !== 'string' ||
        !uuid.test(row.lessonId) ||
        (row.parentPostId !== null &&
            (typeof row.parentPostId !== 'string' || !uuid.test(row.parentPostId))) ||
        typeof row.authorId !== 'string' ||
        !uuid.test(row.authorId) ||
        (row.authorDisplayName !== null && typeof row.authorDisplayName !== 'string') ||
        typeof row.mine !== 'boolean' ||
        typeof row.content !== 'string' ||
        row.content.trim().length === 0 ||
        typeof row.createdAt !== 'string'
    ) {
        throw protocolError();
    }
    return {
        id: row.id,
        lessonId: row.lessonId,
        parentPostId: row.parentPostId as string | null,
        authorId: row.authorId,
        authorDisplayName: row.authorDisplayName as string | null,
        authorRole: decodeRole(row.authorRole),
        mine: row.mine,
        content: row.content,
        createdAt: row.createdAt,
    };
}

function decodeReportReceipt(value: unknown): ContentReportReceipt {
    const row = object(value, ['id', 'status', 'createdAt']);
    if (
        typeof row.id !== 'string' ||
        !uuid.test(row.id) ||
        row.status !== 'open' ||
        typeof row.createdAt !== 'string'
    ) {
        throw protocolError();
    }
    return {
        id: row.id,
        status: 'open',
        createdAt: row.createdAt,
    };
}

/**
 * CT30 (b7ListLessonDiscussion): List visible questions and replies for a lesson.
 */
export function listLessonDiscussion(
    lessonId: string,
    page = 0,
    size = 20,
    options: DiscussionRequestOptions = {}
): Promise<DiscussionPage> {
    if (!integer(page, 0) || !integer(size, 1) || size > 100) {
        return Promise.reject(new ApiError('validation', 'Please check the requested page.'));
    }
    const query = new URLSearchParams({ page: String(page), size: String(size) });
    return request(`/lessons/${encodeURIComponent(lessonId)}/discussion`, {
        ...options,
        method: 'GET',
        auth: 'authenticated',
        expectedStatus: 200,
        decode: decodeDiscussionPage,
        query,
    });
}

/**
 * CT30 (b7CreateLessonDiscussionPost): Post a question or a one-level reply.
 */
export function createLessonDiscussionPost(
    lessonId: string,
    payload: DiscussionPostWrite,
    options: DiscussionRequestOptions = {}
): Promise<DiscussionPost> {
    if (!payload.content || payload.content.trim().length === 0) {
        return Promise.reject(new ApiError('validation', 'Post content cannot be blank.'));
    }
    const body: DiscussionPostWrite = {
        content: payload.content, // Stored verbatim per CT30
        ...(payload.parentPostId ? { parentPostId: payload.parentPostId } : {}),
    };
    return request(`/lessons/${encodeURIComponent(lessonId)}/discussion`, {
        ...options,
        method: 'POST',
        auth: 'authenticated',
        expectedStatus: 201,
        decode: decodePost,
        body,
    });
}

/**
 * CT30 (b7ReportLesson): Report inappropriate lesson content.
 */
export function reportLesson(
    lessonId: string,
    payload: ContentReportWrite,
    options: DiscussionRequestOptions = {}
): Promise<ContentReportReceipt> {
    if (!payload.reason || payload.reason.trim().length === 0) {
        return Promise.reject(new ApiError('validation', 'Please provide a reason for your report.'));
    }
    return request(`/lessons/${encodeURIComponent(lessonId)}/reports`, {
        ...options,
        method: 'POST',
        auth: 'authenticated',
        expectedStatus: 201,
        decode: decodeReportReceipt,
        body: { reason: payload.reason },
    });
}

/**
 * CT30 (b7ReportDiscussionPost): Report an inappropriate discussion post or reply.
 */
export function reportDiscussionPost(
    lessonId: string,
    postId: string,
    payload: ContentReportWrite,
    options: DiscussionRequestOptions = {}
): Promise<ContentReportReceipt> {
    if (!payload.reason || payload.reason.trim().length === 0) {
        return Promise.reject(new ApiError('validation', 'Please provide a reason for your report.'));
    }
    return request(
        `/lessons/${encodeURIComponent(lessonId)}/discussion/${encodeURIComponent(postId)}/reports`,
        {
            ...options,
            method: 'POST',
            auth: 'authenticated',
            expectedStatus: 201,
            decode: decodeReportReceipt,
            body: { reason: payload.reason },
        }
    );
}
