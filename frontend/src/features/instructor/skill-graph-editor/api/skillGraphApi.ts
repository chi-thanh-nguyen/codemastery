import { request } from '../../../../shared/api/httpClient';
import { isRecord, protocolError } from '../../../../shared/types/apiTypes';

/*
 * CT05.representation owner read (ADR005; OpenAPI `getOwnedSkillGraph`, implemented by AD07 #165).
 * This file currently holds only the read that the question bank needs for skill names and the
 * question form's skill selector (DESIGN.md D12-3). The S20 graph, mapping and adaptive-settings
 * mutations belong to FE18 (#203) and are not implemented here.
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
export interface SkillGraphRequestOptions { signal?: AbortSignal; isCurrent?: () => boolean }

const uuid = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
const id = (value: unknown): value is string => typeof value === 'string' && uuid.test(value);
const text = (value: unknown): value is string => typeof value === 'string' && /\S/.test(value);
function exact(value: unknown, keys: readonly string[]): Record<string, unknown> {
    if (!isRecord(value) || Object.keys(value).length !== keys.length || !keys.every((key) => Object.hasOwn(value, key))) throw protocolError();
    return value;
}

function decodeGraph(courseId: string) {
    return (value: unknown): OwnedSkillGraph => {
        const row = exact(value, ['courseId', 'skills', 'prerequisites', 'mappings']);
        if (!id(row.courseId) || row.courseId.toLowerCase() !== courseId.toLowerCase() ||
            !Array.isArray(row.skills) || !Array.isArray(row.prerequisites) || !Array.isArray(row.mappings)) throw protocolError();
        const known = new Set<string>();
        const skills = row.skills.map((item) => {
            const skill = exact(item, ['id', 'name', 'description']);
            if (!id(skill.id) || !text(skill.name) || skill.description !== null && !text(skill.description) || known.has(skill.id.toLowerCase())) throw protocolError();
            known.add(skill.id.toLowerCase());
            return { id: skill.id, name: skill.name, description: skill.description as string | null };
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

/** `getOwnedSkillGraph`: every skill, prerequisite and Lesson mapping of an owned Course, unpaginated. */
export function getOwnedSkillGraph(courseId: string, options: SkillGraphRequestOptions = {}): Promise<OwnedSkillGraph> {
    return request(`/courses/${encodeURIComponent(courseId)}/skill-graph`, { ...options, method: 'GET', auth: 'authenticated', expectedStatus: 200,
        decode: decodeGraph(courseId) });
}
