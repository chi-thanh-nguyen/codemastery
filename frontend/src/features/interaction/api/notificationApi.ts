import { request } from '../../../shared/api/httpClient';
import { ApiError, isRecord, protocolError } from '../../../shared/types/apiTypes';

export interface NotificationItemModel {
    id: string;
    message: string;
    read: boolean;
    createdAt: string;
}

export interface NotificationPage {
    content: NotificationItemModel[];
    page: number;
    size: number;
    totalElements: number;
    totalPages: number;
}

export interface UnreadCountResponse {
    unreadCount: number;
}

export interface MarkedCountResponse {
    markedCount: number;
}

export interface NotificationRequestOptions {
    signal?: AbortSignal;
    isCurrent?: () => boolean;
}

const uuid = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
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

function decodeNotification(value: unknown): NotificationItemModel {
    const row = object(value, ['id', 'message', 'read', 'createdAt']);
    if (
        typeof row.id !== 'string' ||
        !uuid.test(row.id) ||
        typeof row.message !== 'string' ||
        row.message.trim().length === 0 ||
        typeof row.read !== 'boolean' ||
        typeof row.createdAt !== 'string'
    ) {
        throw protocolError();
    }
    return {
        id: row.id,
        message: row.message,
        read: row.read,
        createdAt: row.createdAt,
    };
}

function decodeNotificationPage(value: unknown): NotificationPage {
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
        content: row.content.map(decodeNotification),
        page: row.page,
        size: row.size,
        totalElements: row.totalElements,
        totalPages: row.totalPages,
    };
}

export function listMyNotifications(
    unreadOnly = false,
    page = 0,
    size = 20,
    options: NotificationRequestOptions = {}
): Promise<NotificationPage> {
    if (!integer(page, 0) || !integer(size, 1) || size > 100) {
        return Promise.reject(new ApiError('validation', 'Please check the requested page.'));
    }
    const query = new URLSearchParams({
        unreadOnly: String(unreadOnly),
        page: String(page),
        size: String(size),
    });
    return request('/me/notifications', {
        ...options,
        method: 'GET',
        auth: 'authenticated',
        expectedStatus: 200,
        decode: decodeNotificationPage,
        query,
    });
}

export function countUnreadNotifications(
    options: NotificationRequestOptions = {}
): Promise<UnreadCountResponse> {
    return request('/me/notifications/unread-count', {
        ...options,
        method: 'GET',
        auth: 'authenticated',
        expectedStatus: 200,
        decode: (value) => {
            const row = object(value, ['unreadCount']);
            if (!integer(row.unreadCount, 0)) throw protocolError();
            return { unreadCount: row.unreadCount };
        },
    });
}

export function markNotificationRead(
    notificationId: string,
    options: NotificationRequestOptions = {}
): Promise<NotificationItemModel> {
    return request(`/me/notifications/${encodeURIComponent(notificationId)}/read`, {
        ...options,
        method: 'PUT',
        auth: 'authenticated',
        expectedStatus: 200,
        decode: decodeNotification,
    });
}

export function markAllNotificationsRead(
    options: NotificationRequestOptions = {}
): Promise<MarkedCountResponse> {
    return request('/me/notifications/read-all', {
        ...options,
        method: 'PUT',
        auth: 'authenticated',
        expectedStatus: 200,
        decode: (value) => {
            const row = object(value, ['markedCount']);
            if (!integer(row.markedCount, 0)) throw protocolError();
            return { markedCount: row.markedCount };
        },
    });
}