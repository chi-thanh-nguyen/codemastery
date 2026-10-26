// @vitest-environment jsdom
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { bindAuthSession } from '../../../shared/api/httpClient';
import { getCourseMembership, getMyEnrolledCourses, enrollInCourse, leaveCourse } from './learningApi';
const id = '00000000-0000-4000-8000-000000000001';
let fetcher: ReturnType<typeof vi.fn>, unbind: () => void, invalidate: ReturnType<typeof vi.fn<(generation: number) => void>>;
const json = (value: unknown, status = 200) => new Response(JSON.stringify(value), { status, headers: { 'Content-Type': 'application/json' } });
beforeEach(() => {
    vi.stubEnv('VITE_API_BASE_URL', 'http://localhost:8080'); fetcher = vi.fn(); vi.stubGlobal('fetch', fetcher); invalidate = vi.fn<(generation: number) => void>();
    unbind = bindAuthSession({ capture: () => ({ generation: 1, accessToken: 'synthetic-test-credential', expiresAt: Date.now() + 10000 }), isCurrent: (generation) => generation === 1, invalidate });
});
afterEach(() => { unbind(); vi.unstubAllGlobals(); vi.unstubAllEnvs(); });
it.each([[getCourseMembership, 'GET'], [enrollInCourse, 'PUT'], [leaveCourse, 'DELETE']] as const)('uses the approved authenticated singleton with %s', async (operation, method) => {
    fetcher.mockResolvedValue(json({ courseId: id, status: 'active' }));
    expect(await operation(id)).toEqual({ courseId: id, status: 'active' });
    const [url, init] = fetcher.mock.calls[0]; expect(url).toBe(`http://localhost:8080/api/courses/${id}/membership`); expect(init.method).toBe(method);
    expect(init.headers.get('Authorization')).toBe('Bearer synthetic-test-credential'); expect(init.credentials).toBe('omit'); expect(init.redirect).toBe('error'); expect(init.body).toBeUndefined();
});
it('reads the actual paged My Courses representation without invented dates', async () => {
    const result = { content: [{ id, title: 'Course', description: 'Description', instructorId: id, instructorDisplayName: null, status: 'left' }], page: 2, size: 20, totalElements: 41, totalPages: 3 };
    fetcher.mockResolvedValue(json(result)); expect(await getMyEnrolledCourses(2, 'left')).toEqual(result);
    expect(fetcher.mock.calls[0][0]).toBe('http://localhost:8080/api/me/courses?page=2&size=20&status=left');
});
it.each([{ courseId: id, status: 'DROPPED' }, { courseId: 'invalid', status: 'active' }, { courseId: id, status: 'none', token: 'invalid' }, { courseId: '00000000-0000-4000-8000-000000000002', status: 'active' }])('rejects unapproved or mismatched membership representations', async (value) => {
    fetcher.mockResolvedValue(json(value)); await expect(getCourseMembership(id)).rejects.toMatchObject({ kind: 'protocol' });
});
it('rejects malformed My Courses data', async () => {
    fetcher.mockResolvedValue(json({ content: [{ id, status: 'active' }], page: 0, size: 20, totalElements: 1, totalPages: 1 }));
    await expect(getMyEnrolledCourses()).rejects.toMatchObject({ kind: 'protocol' });
});
it('invalidates only current authenticated 401 and preserves a session after 403', async () => {
    fetcher.mockResolvedValueOnce(json(null, 403)); await expect(enrollInCourse(id)).rejects.toMatchObject({ status: 403 }); expect(invalidate).not.toHaveBeenCalled();
    fetcher.mockResolvedValueOnce(json(null, 401)); await expect(enrollInCourse(id)).rejects.toMatchObject({ status: 401 }); expect(invalidate).toHaveBeenCalledWith(1);
});
it('does not send after expiry or publish a cancelled response', async () => {
    unbind(); unbind = bindAuthSession({ capture: () => ({ generation: 1, accessToken: 'synthetic', expiresAt: Date.now() - 1 }), isCurrent: () => true, invalidate });
    await expect(enrollInCourse(id)).rejects.toMatchObject({ kind: 'cancelled' }); expect(fetcher).not.toHaveBeenCalled();
    await expect(getCourseMembership(id, { isCurrent: () => false })).rejects.toMatchObject({ kind: 'cancelled' });
});
