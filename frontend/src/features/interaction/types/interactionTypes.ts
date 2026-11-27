/*
 * Preview presentation models for notifications, lesson Q&A and content reports.
 * NOT API contracts: production CT11 rows are decoded by notificationApi.ts.
 * Notification kind/target labels stay fixture metadata and are never rendered
 * or used for notification navigation. Delivery is in-app only (no email/SMS/push).
 */
export type NotificationKind = 'content' | 'deadline' | 'result' | 'reply' | 'recommendation';
export interface NotificationView { id: string; kind: NotificationKind; message: string; createdAtLabel: string; dayGroup: string; read: boolean; targetLabel: string }
export interface DiscussionReplyView { id: string; author: string; role: 'Learner' | 'Instructor'; createdAtLabel: string; body: string; hidden?: boolean; mine?: boolean }
export interface DiscussionPostView extends DiscussionReplyView { replies: DiscussionReplyView[] }
export type DiscussionAuthorRole = 'Learner' | 'Instructor' | 'Administrator';

export interface DiscussionReply {
    id: string;
    parentPostId: string;
    authorId: string;
    authorDisplayName: string | null;
    authorRole: DiscussionAuthorRole | null;
    mine: boolean;
    content: string;
    createdAt: string;
}

export interface DiscussionThread {
    id: string;
    authorId: string;
    authorDisplayName: string | null;
    authorRole: DiscussionAuthorRole | null;
    mine: boolean;
    content: string;
    createdAt: string;
    replies: DiscussionReply[];
}

export interface DiscussionPage {
    content: DiscussionThread[];
    page: number;
    size: number;
    totalElements: number;
    totalPages: number;
}

export interface DiscussionPostWrite {
    content: string;
    parentPostId?: string | null;
}

export interface DiscussionPost {
    id: string;
    lessonId: string;
    parentPostId: string | null;
    authorId: string;
    authorDisplayName: string | null;
    authorRole: DiscussionAuthorRole | null;
    mine: boolean;
    content: string;
    createdAt: string;
}

export interface ContentReportWrite {
    reason: string;
}

export interface ContentReportReceipt {
    id: string;
    status: 'open';
    createdAt: string;
}