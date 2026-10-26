// @vitest-environment jsdom
import { act } from 'react';
import { createRoot } from 'react-dom/client';
import type { Root } from 'react-dom/client';
import { MemoryRouter } from 'react-router';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { AuthContext } from '../../../app/providers/AuthProvider';
import type { AuthContextValue } from '../../../app/providers/AuthProvider';
import { CourseMembershipActions, MyCoursesPage } from './MyCoursesPage';
import { getCourseMembership, getMyEnrolledCourses, enrollInCourse, leaveCourse } from '../api/learningApi';
import type { CourseMembership, MembershipPage } from '../types/learningTypes';
import { ApiError } from '../../../shared/types/apiTypes';
vi.mock('../api/learningApi', () => ({ getCourseMembership: vi.fn(), getMyEnrolledCourses: vi.fn(), enrollInCourse: vi.fn(), leaveCourse: vi.fn() }));
Object.assign(globalThis, { IS_REACT_ACT_ENVIRONMENT: true });
const id = '00000000-0000-4000-8000-000000000001';
const listing: MembershipPage = { content: [{ id, title: 'Programming', description: 'Foundations', instructorId: id, instructorDisplayName: 'Instructor', status: 'active' }], page: 0, size: 20, totalElements: 1, totalPages: 1 };
let host: HTMLDivElement, root: Root, auth: AuthContextValue;
beforeEach(() => {
    host = document.createElement('div'); document.body.append(host); root = createRoot(host);
    auth = { user: { id, displayName: 'Learner', role: 'Learner' }, generation: 1, isAuthenticated: true,
        isCurrent: (generation) => generation === 1, login: vi.fn(), register: vi.fn(), logout: vi.fn(), rememberProfile: vi.fn(), consumeReturn: () => '/courses' };
    vi.mocked(getCourseMembership).mockResolvedValue({ courseId: id, status: 'none' }); vi.mocked(getMyEnrolledCourses).mockResolvedValue(listing);
    vi.mocked(enrollInCourse).mockResolvedValue({ courseId: id, status: 'active' }); vi.mocked(leaveCourse).mockResolvedValue({ courseId: id, status: 'left' });
});
afterEach(async () => { await act(async () => root.unmount()); host.remove(); vi.resetAllMocks(); });
async function mount(element = <CourseMembershipActions courseId={id} />) { await act(async () => root.render(<MemoryRouter><AuthContext.Provider value={auth}>{element}</AuthContext.Provider></MemoryRouter>)); }
async function click(text: string, scope: ParentNode = host) {
    const button = [...scope.querySelectorAll<HTMLElement>('button,a')].find((node) => node.textContent === text)!; expect(button).toBeTruthy(); await act(async () => button.click());
}
it('joins, requires leave confirmation, preserves feedback and re-enrolls', async () => {
    await mount(); await click('Enroll in course'); expect(enrollInCourse).toHaveBeenCalledTimes(1); expect(host.textContent).toContain('Membership: active');
    await click('Leave course'); expect(leaveCourse).not.toHaveBeenCalled(); expect(document.querySelector('[role="dialog"]')?.textContent).toContain('history will be preserved');
    await click('Cancel', document); expect(leaveCourse).not.toHaveBeenCalled();
    await click('Leave course'); await click('Leave course', document.querySelector('[role="dialog"]')!);
    expect(leaveCourse).toHaveBeenCalledTimes(1); expect(host.textContent).toContain('Membership: left');
    await click('Re-enroll'); expect(enrollInCourse).toHaveBeenCalledTimes(2); expect(host.querySelector('a[href="/my-courses"]')).toBeTruthy();
});
it('blocks duplicate rapid mutations and aborts the operation on unmount', async () => {
    let resolve!: (value: CourseMembership) => void;
    vi.mocked(enrollInCourse).mockImplementation(() => new Promise((done) => { resolve = done; }));
    await mount(); await act(async () => { const button = [...host.querySelectorAll('button')].find((node) => node.textContent === 'Enroll in course')!; button.click(); button.click(); });
    expect(enrollInCourse).toHaveBeenCalledTimes(1); const options = vi.mocked(enrollInCourse).mock.calls[0][1]!;
    await act(async () => root.unmount()); root = createRoot(host); expect(options.signal?.aborted).toBe(true); expect(options.isCurrent?.()).toBe(false);
    await act(async () => resolve({ courseId: id, status: 'active' })); expect(host.textContent).toBe('');
});
it('keeps failed mutation feedback safe and allows a status refresh before retry', async () => {
    vi.mocked(enrollInCourse).mockRejectedValueOnce(new ApiError('http', 'Access is denied.', 403));
    await mount(); await click('Enroll in course'); expect(host.textContent).toContain('Access is denied.'); expect(host.textContent).toContain('Membership: none');
    await click('Retry'); expect(getCourseMembership).toHaveBeenCalledTimes(2); await click('Enroll in course'); expect(host.textContent).toContain('Membership: active');
});
it('offers sign-in to anonymous users and avoids calls for other roles', async () => {
    auth = { ...auth, isAuthenticated: false, user: null }; await mount(); expect(host.querySelector('a[href="/login"]')?.textContent).toBe('Sign in to enroll'); expect(getCourseMembership).not.toHaveBeenCalled();
    auth = { ...auth, isAuthenticated: true, user: { id, displayName: 'Instructor', role: 'Instructor' } }; await mount(); expect(host.textContent).toContain('available to learners'); expect(getCourseMembership).not.toHaveBeenCalled();
});
it('shows actual My Courses results, refreshes and links to the membership detail', async () => {
    await mount(<MyCoursesPage />); expect(getMyEnrolledCourses).toHaveBeenCalledWith(0, 'joined', expect.objectContaining({ signal: expect.any(AbortSignal) })); expect(host.textContent).toContain('Programming');
    expect(host.querySelector(`a[href="/courses/${id}"]`)).toBeTruthy(); vi.mocked(getMyEnrolledCourses).mockResolvedValue({ ...listing, content: [], totalElements: 0, totalPages: 0 });
    await click('Refresh'); expect(host.textContent).toContain('No courses found');
});
it('suppresses stale My Courses responses after refresh and reports retryable failures', async () => {
    let resolve!: (value: MembershipPage) => void; vi.mocked(getMyEnrolledCourses).mockImplementationOnce(() => new Promise((done) => { resolve = done; }));
    await mount(<MyCoursesPage />); const options = vi.mocked(getMyEnrolledCourses).mock.calls[0][2]!;
    await click('Refresh'); expect(options.signal?.aborted).toBe(true); await act(async () => resolve({ ...listing, content: [{ ...listing.content[0], title: 'Obsolete' }] })); expect(host.textContent).not.toContain('Obsolete');
    vi.mocked(getMyEnrolledCourses).mockRejectedValueOnce(new ApiError('network', 'Unable to connect. Please try again.')); await click('Refresh'); expect(host.textContent).toContain('Unable to connect');
    await click('Retry'); expect(host.textContent).toContain('Programming');
});
it('does not publish protected results from a superseded session', async () => {
    let resolve!: (value: CourseMembership) => void; vi.mocked(getCourseMembership).mockImplementationOnce(() => new Promise((done) => { resolve = done; }));
    await mount(); auth.isCurrent = () => false;
    await act(async () => resolve({ courseId: id, status: 'active' })); expect(host.textContent).not.toContain('Membership: active');
});

function membershipResult(total: number, index: number): MembershipPage {
    return { ...listing, page: index, totalElements: total, totalPages: Math.ceil(total / 20),
        content: Array.from({ length: Math.max(0, Math.min(20, total - index * 20)) }, (_, offset) => ({
            ...listing.content[0], id: `00000000-0000-4000-8000-${String(index * 20 + offset + 1).padStart(12, '0')}`,
            title: `Programming ${index * 20 + offset + 1}`,
        })) };
}
it.each([[21, 20], [61, 21]])('recovers My Courses pagination when %i results shrink to %i', async (before, after) => {
    const oldLast = Math.ceil(before / 20) - 1, last = Math.ceil(after / 20) - 1;
    let recover!: (value: MembershipPage) => void;
    vi.mocked(getMyEnrolledCourses).mockResolvedValueOnce(membershipResult(before, 0))
        .mockResolvedValueOnce(membershipResult(before, oldLast))
        .mockResolvedValueOnce(membershipResult(after, oldLast))
        .mockImplementationOnce(() => new Promise((resolve) => { recover = resolve; }));
    await mount(<MyCoursesPage />);
    await act(async () => host.querySelector<HTMLButtonElement>(`[aria-label="Go to page ${oldLast + 1}"]`)!.click());
    const seen: string[] = [], observer = new MutationObserver(() => seen.push(host.textContent ?? ''));
    observer.observe(host, { childList: true, subtree: true });
    try {
        await click('Refresh');
        expect(vi.mocked(getMyEnrolledCourses).mock.calls.map(([index]) => index)).toEqual([0, oldLast, oldLast, last]);
        expect(host.textContent).toContain('Loading your courses');
        expect(host.textContent).not.toContain('No courses found');
        await act(async () => recover(membershipResult(after, last)));
        expect(host.querySelectorAll('h2')).toHaveLength(Math.min(20, after - last * 20));
        expect(seen.every((text) => !text.includes('No courses found'))).toBe(true);
        expect(getMyEnrolledCourses).toHaveBeenCalledTimes(4);
    } finally { observer.disconnect(); }
});
it.each([21, 0])('does not issue an extra My Courses request for valid or zero results (%i)', async (total) => {
    vi.mocked(getMyEnrolledCourses).mockResolvedValueOnce(membershipResult(21, 0)).mockResolvedValueOnce(membershipResult(total, 1));
    await mount(<MyCoursesPage />); await act(async () => host.querySelector<HTMLButtonElement>('[aria-label="Go to page 2"]')!.click());
    expect(vi.mocked(getMyEnrolledCourses).mock.calls.map(([index]) => index)).toEqual([0, 1]);
    expect(host.textContent?.includes('No courses found')).toBe(total === 0);
    expect(host.textContent).not.toContain('Loading your courses');
});
it('retries a failed My Courses recovery on the corrected page', async () => {
    vi.mocked(getMyEnrolledCourses).mockResolvedValueOnce(membershipResult(21, 0)).mockResolvedValueOnce(membershipResult(20, 1))
        .mockRejectedValueOnce(new ApiError('network', 'Unable to connect. Please try again.')).mockResolvedValueOnce(membershipResult(20, 0));
    await mount(<MyCoursesPage />); await act(async () => host.querySelector<HTMLButtonElement>('[aria-label="Go to page 2"]')!.click());
    expect(host.textContent).toContain('Unable to connect'); expect(host.textContent).not.toContain('No courses found');
    await click('Retry'); expect(host.querySelectorAll('h2')).toHaveLength(20);
    expect(vi.mocked(getMyEnrolledCourses).mock.calls.map(([index]) => index)).toEqual([0, 1, 0, 0]);
});
it('cancels My Courses recovery on unmount and ignores a late private response', async () => {
    let recover!: (value: MembershipPage) => void;
    vi.mocked(getMyEnrolledCourses).mockResolvedValueOnce(membershipResult(21, 0)).mockResolvedValueOnce(membershipResult(20, 1))
        .mockImplementationOnce(() => new Promise((resolve) => { recover = resolve; }));
    await mount(<MyCoursesPage />); await act(async () => host.querySelector<HTMLButtonElement>('[aria-label="Go to page 2"]')!.click());
    const options = vi.mocked(getMyEnrolledCourses).mock.calls[2][2]!;
    await act(async () => root.unmount()); root = createRoot(host);
    expect(options.signal?.aborted).toBe(true); expect(options.isCurrent?.()).toBe(false);
    await act(async () => recover(membershipResult(20, 0))); expect(host.textContent).toBe(''); expect(getMyEnrolledCourses).toHaveBeenCalledTimes(3);
});
it('does not recover an invalid page from a superseded session', async () => {
    let resolve!: (value: MembershipPage) => void;
    vi.mocked(getMyEnrolledCourses).mockResolvedValueOnce(membershipResult(21, 0))
        .mockImplementationOnce(() => new Promise((done) => { resolve = done; }));
    await mount(<MyCoursesPage />); await act(async () => host.querySelector<HTMLButtonElement>('[aria-label="Go to page 2"]')!.click());
    auth.isCurrent = () => false;
    await act(async () => resolve(membershipResult(20, 1)));
    expect(getMyEnrolledCourses).toHaveBeenCalledTimes(2); expect(host.textContent).not.toContain('No courses found');
});
