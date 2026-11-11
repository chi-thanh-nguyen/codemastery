/*
 * Presentation view models for the Instructor skill graph editor (S20). NOT API
 * contracts: CT40 structural/coverage contracts and adaptive settings (keys, values,
 * validation) are DEFERRED. Cycle rejection and readiness are decided by the backend.
 */
export interface EditableSkill { id: string; name: string; description: string; prerequisiteIds: string[]; questionCount: number }
export interface MappingLesson { id: string; title: string; chapterTitle: string; published: boolean; skillIds: string[] }
export interface ReadinessCheck { id: string; label: string; met: boolean; detail?: string }
