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

/** RFC 3339 instant, including the backend's microsecond precision; reject calendar rollover. */
function timestamp(value: unknown): value is string {
    if (typeof value !== 'string') return false;
    const match = /^(\d{4})-(\d{2})-(\d{2})[Tt](\d{2}):(\d{2}):(\d{2})(?:\.\d+)?([Zz]|[+-]\d{2}:\d{2})$/.exec(value);
    if (!match) return false;
    const [, y, m, d, h, minute, second, zone] = match;
    const year = Number(y), month = Number(m), day = Number(d);
    const leap = year % 4 === 0 && (year % 100 !== 0 || year % 400 === 0);
    const days = [31, leap ? 29 : 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31];
    return month >= 1 && month <= 12 && day >= 1 && day <= days[month - 1] &&
        Number(h) <= 23 && Number(minute) <= 59 && Number(second) <= 59 &&
        (zone.length === 1 || Number(zone.slice(1, 3)) <= 23 && Number(zone.slice(4)) <= 59) &&
        Number.isFinite(Date.parse(value.toUpperCase()));
}

function decodeNotification(value: unknown): NotificationItemModel {
    const row = object(value, ['id', 'message', 'read', 'createdAt']);
    if (
        typeof row.id !== 'string' ||
        !uuid.test(row.id) ||
        typeof row.message !== 'string' ||
        row.message.trim().length === 0 ||
        typeof row.read !== 'boolean' ||
        !timestamp(row.createdAt)
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
    const content = row.content.map(decodeNotification);
    const expectedLength = Math.min(row.size, Math.max(0, row.totalElements - row.page * row.size));
    if (row.totalPages !== Math.ceil(row.totalElements / row.size) || content.length !== expectedLength ||
        new Set(content.map((item) => item.id.toLowerCase())).size !== content.length) throw protocolError();
    return {
        content,
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
    if (typeof unreadOnly !== 'boolean' || !integer(page, 0) || page > 2147483647 || !integer(size, 1) || size > 100) {
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
        decode: (value) => {
            const result = decodeNotificationPage(value);
            if (result.page !== page || result.size !== size || unreadOnly && result.content.some((item) => item.read)) throw protocolError();
            return result;
        },
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
    if (!uuid.test(notificationId)) return Promise.reject(new ApiError('validation', 'Please check the notification identifier.'));
    return request(`/me/notifications/${encodeURIComponent(notificationId)}/read`, {
        ...options,
        method: 'PUT',
        auth: 'authenticated',
        expectedStatus: 200,
        decode: (value) => {
            const result = decodeNotification(value);
            if (result.id.toLowerCase() !== notificationId.toLowerCase() || !result.read) throw protocolError();
            return result;
        },
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
