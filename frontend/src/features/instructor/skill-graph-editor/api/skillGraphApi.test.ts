// @vitest-environment jsdom
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { bindAuthSession } from '../../../../shared/api/httpClient';
import {
    addCoursePrerequisite, createCourseSkill, deleteCourseSkill, getOwnedSkillGraph,
    mapCourseLessonSkill, removeCoursePrerequisite, unmapCourseLessonSkill, updateCourseSkill,
} from './skillGraphApi';

const courseId = '00000000-0000-4000-8000-0000000000c1';
const loops = { id: '00000000-0000-4000-8000-0000000000a1', name: 'Loops', description: null };
const lists = { id: '00000000-0000-4000-8000-0000000000a2', name: 'Lists', description: 'Indexing and slicing' };
const lessonId = '00000000-0000-4000-8000-0000000000b1';
const graph = { courseId, skills: [loops, lists], prerequisites: [{ skillId: lists.id, prerequisiteSkillId: loops.id }], mappings: [{ lessonId, skillId: loops.id }] };
const json = (value: unknown, status = 200) => new Response(JSON.stringify(value), { status, headers: { 'Content-Type': 'application/json' } });
let fetcher: ReturnType<typeof vi.fn>, unbind: () => void;
beforeEach(() => {
    vi.stubEnv('VITE_API_BASE_URL', 'http://localhost:8080'); fetcher = vi.fn(); vi.stubGlobal('fetch', fetcher);
    unbind = bindAuthSession({ capture: () => ({ generation: 1, accessToken: 'synthetic-test-credential', expiresAt: Date.now() + 60000 }), isCurrent: () => true, invalidate: vi.fn() });
});
afterEach(() => { unbind(); vi.unstubAllGlobals(); vi.unstubAllEnvs(); });

it('reads the owner graph with the bearer token and keeps the returned order', async () => {
    fetcher.mockResolvedValue(json(graph));
    expect(await getOwnedSkillGraph(courseId)).toEqual(graph);
    const [url, init] = fetcher.mock.calls[0];
    expect(url).toBe(`http://localhost:8080/api/courses/${courseId}/skill-graph`);
    expect(init.method).toBe('GET'); expect(init.headers.get('Authorization')).toBe('Bearer synthetic-test-credential');
});
it('accepts an empty graph for draft authoring', async () => {
    fetcher.mockResolvedValue(json({ courseId, skills: [], prerequisites: [], mappings: [] }));
    expect((await getOwnedSkillGraph(courseId)).skills).toEqual([]);
});
it.each([
    ['another course', { ...graph, courseId: lessonId }],
    ['a blank skill name', { ...graph, skills: [{ ...loops, name: ' ' }, lists] }],
    ['a blank description', { ...graph, skills: [{ ...loops, description: '' }, lists] }],
    ['a missing description member', { ...graph, skills: [{ id: loops.id, name: 'Loops' }, lists] }],
    ['a duplicate skill', { ...graph, skills: [loops, loops] }],
    ['an edge to an unknown skill', { ...graph, prerequisites: [{ skillId: lists.id, prerequisiteSkillId: lessonId }] }],
    ['a mapping to an unknown skill', { ...graph, mappings: [{ lessonId, skillId: lessonId }] }],
    ['an unknown member', { ...graph, mastery: [] }],
])('rejects %s', async (_, value) => {
    fetcher.mockResolvedValue(json(value));
    await expect(getOwnedSkillGraph(courseId)).rejects.toMatchObject({ kind: 'protocol' });
});

it('sends CT05 skill, prerequisite and lesson-mapping mutations with 201, 200 and 204 no-content modes', async () => {
    const empty204 = () => new Response(null, { status: 204 });
    fetcher.mockResolvedValueOnce(json(loops, 201));
    expect(await createCourseSkill(courseId, { name: 'Loops', description: null })).toEqual(loops);
    expect(fetcher.mock.calls[0][0]).toBe(`http://localhost:8080/api/courses/${courseId}/skills`);

    fetcher.mockResolvedValueOnce(json({ ...loops, description: 'Updated' }));
    expect((await updateCourseSkill(courseId, loops.id, { name: 'Loops', description: 'Updated' })).description).toBe('Updated');

    fetcher.mockResolvedValueOnce(empty204());
    await deleteCourseSkill(courseId, loops.id);
    expect(fetcher.mock.calls[2][1].method).toBe('DELETE');

    fetcher.mockResolvedValueOnce(empty204());
    await addCoursePrerequisite(courseId, lists.id, loops.id);
    expect(fetcher.mock.calls[3][0]).toBe(`http://localhost:8080/api/courses/${courseId}/skills/${lists.id}/prerequisites/${loops.id}`);

    fetcher.mockResolvedValueOnce(empty204());
    await removeCoursePrerequisite(courseId, lists.id, loops.id);
    expect(fetcher.mock.calls[4][1].method).toBe('DELETE');

    fetcher.mockResolvedValueOnce(empty204());
    await mapCourseLessonSkill(courseId, lessonId, loops.id);
    expect(fetcher.mock.calls[5][0]).toBe(`http://localhost:8080/api/courses/${courseId}/lessons/${lessonId}/skills/${loops.id}`);

    fetcher.mockResolvedValueOnce(empty204());
    await unmapCourseLessonSkill(courseId, lessonId, loops.id);
    expect(fetcher.mock.calls[6][1].method).toBe('DELETE');
});
