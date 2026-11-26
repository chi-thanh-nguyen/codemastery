/*
 * Preview presentation models for notifications, lesson Q&A and content reports.
 * NOT API contracts: production CT11 rows are decoded by notificationApi.ts.
 * Notification kind/target labels stay fixture metadata and are never rendered
 * or used for notification navigation. Delivery is in-app only (no email/SMS/push).
 */
export type NotificationKind = 'content' | 'deadline' | 'result' | 'reply' | 'recommendation';
export interface NotificationView { id: string; kind: NotificationKind; message: string; createdAtLabel: string; dayGroup: string; read: boolean; targetLabel: string }
export interface DiscussionReplyView { id: string; author: string; role: 'Learner' | 'Instructor'; createdAtLabel: string; body: string; hidden?: boolean }
export interface DiscussionPostView extends DiscussionReplyView { replies: DiscussionReplyView[] }
