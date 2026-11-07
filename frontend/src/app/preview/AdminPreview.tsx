import { useState } from 'react';
import type { Dispatch, SetStateAction } from 'react';
import { ReportingDashboardPage } from '../../features/admin/reporting-dashboard/pages/ReportingDashboardPage';
import { AuditLogPage } from '../../features/admin/reporting-dashboard/pages/AuditLogPage';
import type { AuditEntry } from '../../features/admin/reporting-dashboard/types/reportingTypes';
import { UserManagementPage } from '../../features/admin/user-management/pages/UserManagementPage';
import type { UserRowView } from '../../features/admin/user-management/types/userManagementTypes';
import { AdminCoursesPage } from '../../features/admin/course-management/pages/AdminCoursesPage';
import type { AdminCourseRow } from '../../features/admin/course-management/types/adminCourseTypes';
import { ContentReportQueuePage } from '../../features/admin/content-reports/pages/ContentReportQueuePage';
import type { ReportRow } from '../../features/admin/content-reports/types/contentReportTypes';
import * as fx from './fixtures';
import { ContractNote, nowLabel, usePreview } from './previewKit';

export interface AdminState { users: UserRowView[]; courses: AdminCourseRow[]; reports: ReportRow[]; audit: AuditEntry[] }
export const initialAdminState = (): AdminState => structuredClone({ users: fx.users, courses: fx.adminCourses, reports: fx.reports, audit: fx.auditEntries });

interface Props { state: AdminState; setState: Dispatch<SetStateAction<AdminState>>; screen: string }

/** Administrator preview. Each sample action appends a sample audit entry, standing in for the backend's audit trail. */
export function AdminPreview({ state, setState, screen }: Props) {
    const { notify } = usePreview();
    const [courseId, setCourseId] = useState('c-intro');
    const audit = (action: string, target: string) => setState((value) => ({ ...value, audit: [{ id: `a-${Date.now()}`, atLabel: nowLabel, atSort: '2026-11-06T23:59', actor: fx.sampleAdmin.name, action, target }, ...value.audit] }));
    const note = <ContractNote>Sample administration. Actions change this preview only; user, course and moderation operations (CT12) and their audit records (CT31) are pending.</ContractNote>;
    if (screen === 'admin-overview') return <ReportingDashboardPage courses={fx.courseMetrics} selectedCourseId={courseId} onCourseChange={setCourseId} distribution={fx.skillDistribution[courseId] ?? []}
        notice={<ContractNote>Sample metrics. Definitions and values must come from the reporting contract; nothing here is calculated from real data.</ContractNote>} />;
    if (screen === 'admin-users') return <UserManagementPage users={state.users} showEmail notice={note}
        onRoleChange={(id, role) => { const user = state.users.find((entry) => entry.id === id)!; setState((value) => ({ ...value, users: value.users.map((entry) => entry.id === id ? { ...entry, role } : entry) })); audit('user.role.change', `User ${user.displayName} → ${role}`); notify(`${user.displayName} is now ${role === 'Administrator' ? 'an' : 'a'} ${role} (sample).`); }}
        onLockChange={(id, locked) => { const user = state.users.find((entry) => entry.id === id)!; setState((value) => ({ ...value, users: value.users.map((entry) => entry.id === id ? { ...entry, status: locked ? 'locked' : 'active' } : entry) })); audit(locked ? 'user.lock' : 'user.unlock', `User ${user.displayName}`); notify(`${user.displayName}'s account ${locked ? 'locked' : 'unlocked'} (sample).`); }} />;
    if (screen === 'admin-courses') return <AdminCoursesPage courses={state.courses} notice={note}
        onPublicationChange={(id, publication) => { const course = state.courses.find((entry) => entry.id === id)!; setState((value) => ({ ...value, courses: value.courses.map((entry) => entry.id === id ? { ...entry, publication } : entry) })); audit(publication === 'hidden' ? 'course.hide' : 'course.publish', `Course ${course.title}`); notify(`${course.title} ${publication === 'hidden' ? 'hidden' : 'published'} (sample).`); }} />;
    if (screen === 'admin-reports') return <ContentReportQueuePage reports={state.reports} notice={note}
        onHide={(id) => { setState((value) => ({ ...value, reports: value.reports.map((entry) => entry.id === id ? { ...entry, status: 'resolved', resolvedAtLabel: nowLabel } : entry) })); audit('report.hide', `Report ${id}`); notify('Content hidden. Report resolved (sample).'); }}
        onDismiss={(id) => { setState((value) => ({ ...value, reports: value.reports.map((entry) => entry.id === id ? { ...entry, status: 'dismissed', resolvedAtLabel: nowLabel } : entry) })); audit('report.dismiss', `Report ${id}`); notify('Report dismissed (sample).'); }} />;
    if (screen === 'admin-audit') return <AuditLogPage entries={state.audit} />;
    return null;
}
