// @vitest-environment jsdom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { getPublishedCourseById, getPublishedCourses } from './catalogueApi';
const id = '00000000-0000-4000-8000-000000000001';
const summary = { id, title: 'Programming', description: 'Foundations', instructorId: id, instructorDisplayName: 'Instructor Demo' };
const page = { content: [summary], page: 0, size: 20, totalElements: 1, totalPages: 1 };
const detail = { ...summary, prerequisiteDescription: null, syllabus: [{ id, title: 'Chapter', position: 1, lessons: [{ id, title: 'Lesson', position: 1 }] }] };
let fetcher: ReturnType<typeof vi.fn>;
const json = (value: unknown, status = 200) => new Response(JSON.stringify(value), { status, headers: { 'Content-Type': 'application/json' } });
beforeEach(() => { vi.stubEnv('VITE_API_BASE_URL', 'http://localhost:8080'); fetcher = vi.fn(); vi.stubGlobal('fetch', fetcher); });
afterEach(() => { vi.unstubAllEnvs(); vi.unstubAllGlobals(); vi.restoreAllMocks(); });
describe('catalogue bindings to current shared HTTP', () => {
    it('uses the public API root once and the approved paging defaults', async () => {
        fetcher.mockResolvedValue(json(page)); expect(await getPublishedCourses()).toEqual(page);
        const [url, init] = fetcher.mock.calls[0]; expect(url).toBe('http://localhost:8080/api/courses?page=0&size=20');
        expect(init.method).toBe('GET'); expect(init.headers.has('Authorization')).toBe(false);
    });
    it.each([1, 100])('accepts approved size boundary %s', async (size) => {
        fetcher.mockResolvedValue(json({ ...page, size })); await getPublishedCourses(3, size);
        expect(fetcher.mock.calls[0][0]).toBe(`http://localhost:8080/api/courses?page=3&size=${size}`);
    });
    it.each([[-1, 20], [0, 0], [0, 101], [0.5, 20], [0, 1.5]])('rejects invalid page/size %s/%s without a request', async (page, size) => {
        await expect(getPublishedCourses(page, size)).rejects.toMatchObject({ kind: 'validation' }); expect(fetcher).not.toHaveBeenCalled();
    });
    it('preserves a beyond-last empty page and server totals', async () => {
        const empty = { ...page, content: [], page: 10 }; fetcher.mockResolvedValue(json(empty));
        expect(await getPublishedCourses(10)).toEqual(empty);
    });
    it('decodes required-nullable prerequisites and ordered syllabus detail', async () => {
        fetcher.mockResolvedValue(json(detail)); expect(await getPublishedCourseById(id)).toEqual(detail);
        expect(fetcher.mock.calls[0][0]).toBe(`http://localhost:8080/api/courses/${id}`);
    });
    it.each([
        { ...page, content: [{ ...summary, id: 'invalid' }] },
        { ...page, content: [{ ...summary, instructorId: 3 }] },
        { ...page, content: [{ ...summary, adaptive_settings: {} }] },
        { ...page, totalPages: '1' }, { ...page, size: 101 },
    ])('rejects malformed or private catalogue payloads', async (value) => {
        fetcher.mockResolvedValue(json(value)); await expect(getPublishedCourses()).rejects.toMatchObject({ kind: 'protocol' });
    });
    it.each([
        { ...summary, syllabus: [] },
        { ...detail, prerequisiteDescription: 0 },
        { ...detail, syllabus: [{ ...detail.syllabus[0], position: 0 }] },
        { ...detail, syllabus: [{ ...detail.syllabus[0], lessons: [{ ...detail.syllabus[0].lessons[0], id: 'invalid' }] }] },
    ])('rejects invalid detail shapes', async (value) => {
        fetcher.mockResolvedValue(json(value)); await expect(getPublishedCourseById(id)).rejects.toMatchObject({ kind: 'protocol' });
    });
    it('retains the same safe unavailable result for a missing or hidden course', async () => {
        fetcher.mockResolvedValue(json(null, 404)); await expect(getPublishedCourseById(id)).rejects.toMatchObject({ status: 404, message: 'The requested resource is unavailable.' });
    });
    it('passes cancellation and current-operation guards through to transport', async () => {
        const controller = new AbortController(); controller.abort();
        await expect(getPublishedCourses(0, 20, { signal: controller.signal })).rejects.toMatchObject({ kind: 'cancelled' });
        await expect(getPublishedCourseById(id, { isCurrent: () => false })).rejects.toMatchObject({ kind: 'cancelled' }); expect(fetcher).not.toHaveBeenCalled();
    });
});

it('encodes literal search and the selected prerequisite filter separately from the path', async () => {
    fetcher.mockResolvedValue(json(page)); await getPublishedCourses(0, 20, { q: '100%_ basics', prerequisites: 'none' });
    const url = new URL(fetcher.mock.calls[0][0]); expect(url.searchParams.get('q')).toBe('100%_ basics'); expect(url.searchParams.get('prerequisites')).toBe('none'); expect(url.pathname).toBe('/api/courses');
});
