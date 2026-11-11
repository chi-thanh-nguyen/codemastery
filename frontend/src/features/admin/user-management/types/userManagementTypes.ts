/*
 * Presentation view models for user administration (S24). NOT API contracts: user
 * search, role changes and lock/unlock (CT12) with auditing (CT31) are DEFERRED.
 * Roles never inherit each other's rights; the backend enforces every change.
 */
export type Role = 'Learner' | 'Instructor' | 'Administrator';
export interface UserRowView { id: string; displayName: string; email?: string; role: Role; status: 'active' | 'locked' }
