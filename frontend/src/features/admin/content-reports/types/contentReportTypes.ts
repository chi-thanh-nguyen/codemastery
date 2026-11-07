/*
 * Presentation view models for the content-report queue (S24). NOT API contracts:
 * moderation operations (CT12 with Interaction) are DEFERRED. Stored statuses are
 * open, resolved (content hidden) and dismissed (data-model §11).
 */
export interface ReportRow { id: string; targetType: 'Post' | 'Lesson'; targetLabel: string; excerpt: string; courseTitle: string; reporter: string; reportedAtLabel: string; status: 'open' | 'resolved' | 'dismissed'; resolvedAtLabel?: string }
