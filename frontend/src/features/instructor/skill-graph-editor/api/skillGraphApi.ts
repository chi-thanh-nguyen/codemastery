import { request } from '../../../../shared/api/httpClient';
import { isRecord, protocolError } from '../../../../shared/types/apiTypes';

/*
 * CT05 owner skill-graph client (ADR005; OpenAPI `getOwnedSkillGraph`, `createCourseSkill`,
 * `updateCourseSkill`, `deleteCourseSkill`, `addCoursePrerequisite`, `removeCoursePrerequisite`,
 * `mapCourseLessonSkill`, `unmapCourseLessonSkill`; implemented by AD07 #165).
 * Adaptive-settings read/write (`getCourseAdaptiveSettings`, `replaceCourseAdaptiveSettings`)
 * stay deferred until AD08 (#166) delivers the backend endpoint.
 */
export interface SkillGraphSkill { id: string; name: string; description: string | null }
export interface SkillGraphPrerequisite { skillId: string; prerequisiteSkillId: string }
export interface SkillGraphMapping { lessonId: string; skillId: string }
export interface OwnedSkillGraph {
    courseId: string;
    /** Sorted by lowercase UUID text, as returned. */
    skills: SkillGraphSkill[];
    prerequisites: SkillGraphPrerequisite[];
    mappings: SkillGraphMapping[];
}
export interface SkillWritePayload { name: string; description: string | null }
export interface SkillGraphRequestOptions { signal?: AbortSignal; isCurrent?: () => boolean }

const uuid = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
const id = (value: unknown): value is string => typeof value === 'string' && uuid.test(value);
const text = (value: unknown): value is string => typeof value === 'string' && /\S/.test(value);
const same = (left: string, right: string) => left.toLowerCase() === right.toLowerCase();

function exact(value: unknown, keys: readonly string[]): Record<string, unknown> {
    if (!isRecord(value) || Object.keys(value).length !== keys.length || !keys.every((key) => Object.hasOwn(value, key))) throw protocolError();
    return value;
}

function decodeSkill(value: unknown, expectedId?: string): SkillGraphSkill {
    const skill = exact(value, ['id', 'name', 'description']);
    if (!id(skill.id) || (expectedId !== undefined && !same(skill.id, expectedId)) ||
        !text(skill.name) || (skill.description !== null && !text(skill.description))) {
        throw protocolError();
    }
    return { id: skill.id, name: skill.name, description: skill.description as string | null };
}

function decodeGraph(courseId: string) {
    return (value: unknown): OwnedSkillGraph => {
        const row = exact(value, ['courseId', 'skills', 'prerequisites', 'mappings']);
        if (!id(row.courseId) || !same(row.courseId, courseId) ||
            !Array.isArray(row.skills) || !Array.isArray(row.prerequisites) || !Array.isArray(row.mappings)) throw protocolError();
        const known = new Set<string>();
        const skills = row.skills.map((item) => {
            const decoded = decodeSkill(item);
            if (known.has(decoded.id.toLowerCase())) throw protocolError();
            known.add(decoded.id.toLowerCase());
            return decoded;
        });
        // Every edge and mapping refers to a skill of this graph.
        const member = (skillId: unknown): skillId is string => id(skillId) && known.has(skillId.toLowerCase());
        const prerequisites = row.prerequisites.map((item) => {
            const edge = exact(item, ['skillId', 'prerequisiteSkillId']);
            if (!member(edge.skillId) || !member(edge.prerequisiteSkillId)) throw protocolError();
            return { skillId: edge.skillId, prerequisiteSkillId: edge.prerequisiteSkillId };
        });
        const mappings = row.mappings.map((item) => {
            const mapping = exact(item, ['lessonId', 'skillId']);
            if (!id(mapping.lessonId) || !member(mapping.skillId)) throw protocolError();
            return { lessonId: mapping.lessonId, skillId: mapping.skillId };
        });
        return { courseId: row.courseId, skills, prerequisites, mappings };
    };
}

const courseBase = (courseId: string) => `/courses/${encodeURIComponent(courseId)}`;

/** `getOwnedSkillGraph`: every skill, prerequisite and Lesson mapping of an owned Course, unpaginated. */
export function getOwnedSkillGraph(courseId: string, options: SkillGraphRequestOptions = {}): Promise<OwnedSkillGraph> {
    return request(`${courseBase(courseId)}/skill-graph`, { ...options, method: 'GET', auth: 'authenticated', expectedStatus: 200,
        decode: decodeGraph(courseId) });
}

/** `createCourseSkill`: creates a new skill in the course's skill graph (201). */
export function createCourseSkill(courseId: string, payload: SkillWritePayload, options: SkillGraphRequestOptions = {}): Promise<SkillGraphSkill> {
    return request(`${courseBase(courseId)}/skills`, { ...options, method: 'POST', auth: 'authenticated', expectedStatus: 201,
        body: payload, decode: (value) => decodeSkill(value) });
}

/** `updateCourseSkill`: replaces a skill's name and optional description (200). */
export function updateCourseSkill(courseId: string, skillId: string, payload: SkillWritePayload, options: SkillGraphRequestOptions = {}): Promise<SkillGraphSkill> {
    return request(`${courseBase(courseId)}/skills/${encodeURIComponent(skillId)}`, { ...options, method: 'PUT', auth: 'authenticated', expectedStatus: 200,
        body: payload, decode: (value) => decodeSkill(value, skillId) });
}

/** `deleteCourseSkill`: deletes an unreferenced skill (204; 409 when referenced). */
export function deleteCourseSkill(courseId: string, skillId: string, options: SkillGraphRequestOptions = {}): Promise<void> {
    return request(`${courseBase(courseId)}/skills/${encodeURIComponent(skillId)}`, { ...options, method: 'DELETE', auth: 'authenticated', expectedStatus: 204, responseMode: 'no-content' });
}

/** `addCoursePrerequisite`: idempotent prerequisite edge write; cycles and self-edges are rejected (204). */
export function addCoursePrerequisite(courseId: string, skillId: string, prerequisiteSkillId: string, options: SkillGraphRequestOptions = {}): Promise<void> {
    return request(`${courseBase(courseId)}/skills/${encodeURIComponent(skillId)}/prerequisites/${encodeURIComponent(prerequisiteSkillId)}`,
        { ...options, method: 'PUT', auth: 'authenticated', expectedStatus: 204, responseMode: 'no-content' });
}

/** `removeCoursePrerequisite`: idempotent prerequisite edge removal (204). */
export function removeCoursePrerequisite(courseId: string, skillId: string, prerequisiteSkillId: string, options: SkillGraphRequestOptions = {}): Promise<void> {
    return request(`${courseBase(courseId)}/skills/${encodeURIComponent(skillId)}/prerequisites/${encodeURIComponent(prerequisiteSkillId)}`,
        { ...options, method: 'DELETE', auth: 'authenticated', expectedStatus: 204, responseMode: 'no-content' });
}

/** `mapCourseLessonSkill`: idempotent Lesson → Skill mapping (204). */
export function mapCourseLessonSkill(courseId: string, lessonId: string, skillId: string, options: SkillGraphRequestOptions = {}): Promise<void> {
    return request(`${courseBase(courseId)}/lessons/${encodeURIComponent(lessonId)}/skills/${encodeURIComponent(skillId)}`,
        { ...options, method: 'PUT', auth: 'authenticated', expectedStatus: 204, responseMode: 'no-content' });
}

/** `unmapCourseLessonSkill`: idempotent Lesson → Skill unmapping (204). */
export function unmapCourseLessonSkill(courseId: string, lessonId: string, skillId: string, options: SkillGraphRequestOptions = {}): Promise<void> {
    return request(`${courseBase(courseId)}/lessons/${encodeURIComponent(lessonId)}/skills/${encodeURIComponent(skillId)}`,
        { ...options, method: 'DELETE', auth: 'authenticated', expectedStatus: 204, responseMode: 'no-content' });
}
