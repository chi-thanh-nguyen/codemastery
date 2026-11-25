/*
 * Presentation view models for the Instructor skill graph editor (S20). NOT API
 * contracts: CT40 structural/coverage contracts and adaptive settings (keys, values,
 * validation) are DEFERRED. Cycle rejection and readiness are decided by the backend.
 */
export interface EditableSkill { id: string; name: string; description: string; prerequisiteIds: string[]; questionCount: number }
/**
 * A skill as the graph canvas shows it. `questionCount` is present only when its source returns one (the
 * preview's sample data); CT05 `getOwnedSkillGraph` returns no question references, so production omits it
 * rather than showing an invented figure (DESIGN.md: no figure the named operations do not return).
 */
export type GraphSkill = Omit<EditableSkill, 'questionCount'> & { questionCount?: number };
export interface MappingLesson { id: string; title: string; chapterTitle: string; published: boolean; skillIds: string[] }
export interface ReadinessCheck { id: string; label: string; met: boolean; detail?: string }
