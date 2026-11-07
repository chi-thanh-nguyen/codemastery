/*
 * Presentation view models for Administrator reporting and audit (S23–S24). NOT API
 * contracts: reporting definitions and read operations, audit actions (CT12/CT31)
 * are DEFERRED. Metric definitions must come from the reporting contract.
 */
import type { MasteryState } from '../../../../shared/components/StatusPill';

export interface CourseMetricRow { courseId: string; courseTitle: string; enrolments: number; completionRate: number; averageQuizScore: number | null }
export interface SkillDistributionRow { skillId: string; skillName: string; counts: Record<MasteryState, number> }
export interface AuditEntry { id: string; atLabel: string; atSort: string; actor: string; action: string; target: string }
