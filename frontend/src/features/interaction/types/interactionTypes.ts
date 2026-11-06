/*
 * Presentation view models for in-app notifications, lesson Q&A and content reports.
 * NOT API contracts: Interaction-owned operations, visibility and delivery are
 * DEFERRED (CT10/CT11). Notifications are in-app only (no email/SMS/push).
 */
export type NotificationKind = 'content' | 'deadline' | 'result' | 'reply' | 'recommendation';
export interface NotificationView { id: string; kind: NotificationKind; message: string; createdAtLabel: string; dayGroup: string; read: boolean; targetLabel: string }
export interface DiscussionReplyView { id: string; author: string; role: 'Learner' | 'Instructor'; createdAtLabel: string; body: string; hidden?: boolean }
export interface DiscussionPostView extends DiscussionReplyView { replies: DiscussionReplyView[] }
