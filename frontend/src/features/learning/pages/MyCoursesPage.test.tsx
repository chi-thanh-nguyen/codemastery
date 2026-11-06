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
    await mount(); await click('Enroll in course'); expect(enrollInCourse).toHaveBeenCalledTimes(1); expect(host.textContent).toContain('Membership: Active');
    await click('Leave course'); expect(leaveCourse).not.toHaveBeenCalled(); expect(document.querySelector('[role="dialog"]')?.textContent).toContain('history will be preserved');
    await click('Cancel', document); expect(leaveCourse).not.toHaveBeenCalled();
    await click('Leave course'); await click('Leave course', document.querySelector('[role="dialog"]')!);
    expect(leaveCourse).toHaveBeenCalledTimes(1); expect(host.textContent).toContain('Membership: Left');
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
    await mount(); await click('Enroll in course'); expect(host.textContent).toContain('Access is denied.'); expect(host.textContent).toContain('Membership: Not enrolled');
    await click('Try again'); expect(getCourseMembership).toHaveBeenCalledTimes(2); await click('Enroll in course'); expect(host.textContent).toContain('Membership: Active');
});
it('offers sign-in to anonymous users and avoids calls for other roles', async () => {
    auth = { ...auth, isAuthenticated: false, user: null }; await mount(); expect(host.querySelector('a[href="/login"]')?.textContent).toBe('Sign in to enroll'); expect(getCourseMembership).not.toHaveBeenCalled();
    auth = { ...auth, isAuthenticated: true, user: { id, displayName: 'Instructor', role: 'Instructor' } }; await mount(); expect(host.textContent).toContain('available to learners'); expect(getCourseMembership).not.toHaveBeenCalled();
});
it('shows actual My Courses results, refreshes and links to the membership detail', async () => {
    await mount(<MyCoursesPage />); expect(getMyEnrolledCourses).toHaveBeenCalledWith(0, 'joined', expect.objectContaining({ signal: expect.any(AbortSignal) })); expect(host.textContent).toContain('Programming');
    expect(host.querySelector(`a[href="/courses/${id}"]`)).toBeTruthy(); vi.mocked(getMyEnrolledCourses).mockResolvedValue({ ...listing, content: [], totalElements: 0, totalPages: 0 });
    await click('Refresh'); expect(host.textContent).toContain("You haven't joined a course yet");
});
it('suppresses stale My Courses responses after refresh and reports retryable failures', async () => {
    let resolve!: (value: MembershipPage) => void; vi.mocked(getMyEnrolledCourses).mockImplementationOnce(() => new Promise((done) => { resolve = done; }));
    await mount(<MyCoursesPage />); const options = vi.mocked(getMyEnrolledCourses).mock.calls[0][2]!;
    await click('Refresh'); expect(options.signal?.aborted).toBe(true); await act(async () => resolve({ ...listing, content: [{ ...listing.content[0], title: 'Obsolete' }] })); expect(host.textContent).not.toContain('Obsolete');
    vi.mocked(getMyEnrolledCourses).mockRejectedValueOnce(new ApiError('network', 'Unable to connect. Please try again.')); await click('Refresh'); expect(host.textContent).toContain('Unable to connect');
    await click('Try again'); expect(host.textContent).toContain('Programming');
});
it('does not publish protected results from a superseded session', async () => {
    let resolve!: (value: CourseMembership) => void; vi.mocked(getCourseMembership).mockImplementationOnce(() => new Promise((done) => { resolve = done; }));
    await mount(); auth.isCurrent = () => false;
    await act(async () => resolve({ courseId: id, status: 'active' })); expect(host.textContent).not.toContain('Membership: Active');
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
        expect(host.textContent).not.toContain("You haven't joined a course yet");
        await act(async () => recover(membershipResult(after, last)));
        expect(host.querySelectorAll('h2')).toHaveLength(Math.min(20, after - last * 20));
        expect(seen.every((text) => !text.includes("You haven't joined a course yet"))).toBe(true);
        expect(getMyEnrolledCourses).toHaveBeenCalledTimes(4);
    } finally { observer.disconnect(); }
});
it.each([21, 0])('does not issue an extra My Courses request for valid or zero results (%i)', async (total) => {
    vi.mocked(getMyEnrolledCourses).mockResolvedValueOnce(membershipResult(21, 0)).mockResolvedValueOnce(membershipResult(total, 1));
    await mount(<MyCoursesPage />); await act(async () => host.querySelector<HTMLButtonElement>('[aria-label="Go to page 2"]')!.click());
    expect(vi.mocked(getMyEnrolledCourses).mock.calls.map(([index]) => index)).toEqual([0, 1]);
    expect(host.textContent?.includes("You haven't joined a course yet")).toBe(total === 0);
    expect(host.textContent).not.toContain('Loading your courses');
});
it('retries a failed My Courses recovery on the corrected page', async () => {
    vi.mocked(getMyEnrolledCourses).mockResolvedValueOnce(membershipResult(21, 0)).mockResolvedValueOnce(membershipResult(20, 1))
        .mockRejectedValueOnce(new ApiError('network', 'Unable to connect. Please try again.')).mockResolvedValueOnce(membershipResult(20, 0));
    await mount(<MyCoursesPage />); await act(async () => host.querySelector<HTMLButtonElement>('[aria-label="Go to page 2"]')!.click());
    expect(host.textContent).toContain('Unable to connect'); expect(host.textContent).not.toContain("You haven't joined a course yet");
    await click('Try again'); expect(host.querySelectorAll('h2')).toHaveLength(20);
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
    expect(getMyEnrolledCourses).toHaveBeenCalledTimes(2); expect(host.textContent).not.toContain("You haven't joined a course yet");
});

const menuItem = (text: string) => [...document.querySelectorAll<HTMLElement>('[role="menuitem"]')].find((node) => node.textContent === text);
async function openTileMenu(title = 'Programming') {
    const trigger = host.querySelector<HTMLButtonElement>(`button[aria-label="More actions for ${title}"]`)!; expect(trigger).toBeTruthy();
    expect(trigger.getAttribute('aria-haspopup')).toBe('menu'); await act(async () => trigger.click()); return trigger;
}
it('leaves from the tile menu only after confirmation, then re-reads the list and announces where the course went', async () => {
    await mount(<MyCoursesPage />);
    await openTileMenu(); expect(menuItem('View course details')?.getAttribute('href')).toBe(`/courses/${id}`); expect(menuItem('Re-enroll')).toBeUndefined();
    await act(async () => menuItem('Leave course')!.click());
    const dialog = document.querySelector<HTMLElement>('[role="dialog"]')!; expect(dialog.textContent).toContain('Your learning history will be preserved.');
    expect(leaveCourse).not.toHaveBeenCalled(); expect(document.activeElement?.textContent).toBe('Cancel');
    await click('Cancel', dialog); expect(leaveCourse).not.toHaveBeenCalled();
    vi.mocked(getMyEnrolledCourses).mockResolvedValue({ ...listing, content: [], totalElements: 0, totalPages: 0 });
    await openTileMenu(); await act(async () => menuItem('Leave course')!.click());
    await click('Leave course', document.querySelector('[role="dialog"]')!);
    expect(leaveCourse).toHaveBeenCalledTimes(1); expect(vi.mocked(leaveCourse).mock.calls[0][0]).toBe(id);
    expect(getMyEnrolledCourses).toHaveBeenCalledTimes(2); expect(enrollInCourse).not.toHaveBeenCalled();
    expect(host.querySelector('[role="status"]')?.textContent).toContain("You left Programming. It's listed under Left, where you can re-enroll.");
    expect(host.textContent).toContain("You haven't joined a course yet");
});
it('re-enrolls a left course from its tile menu without confirmation and keeps failures readable', async () => {
    const left: MembershipPage = { ...listing, content: [{ ...listing.content[0], status: 'left' }] };
    vi.mocked(getMyEnrolledCourses).mockResolvedValue(left);
    vi.mocked(enrollInCourse).mockRejectedValueOnce(new ApiError('http', 'This course is no longer published.', 404));
    await mount(<MyCoursesPage />); expect(host.textContent).toContain('Membership: Left');
    await openTileMenu(); expect(menuItem('Leave course')).toBeUndefined(); await act(async () => menuItem('Re-enroll')!.click());
    expect(document.querySelector('[role="dialog"]')).toBeNull(); expect(enrollInCourse).toHaveBeenCalledTimes(1);
    expect(host.querySelector('[role="alert"]')?.textContent).toContain('This course is no longer published.'); expect(getMyEnrolledCourses).toHaveBeenCalledTimes(1);
    await openTileMenu(); await act(async () => menuItem('Re-enroll')!.click());
    expect(enrollInCourse).toHaveBeenCalledTimes(2); expect(getMyEnrolledCourses).toHaveBeenCalledTimes(2);
    expect(host.querySelector('[role="status"]')?.textContent).toContain('You re-enrolled in Programming.');
});
it('allows one tile membership change at a time and aborts it on unmount', async () => {
    let resolve!: (value: CourseMembership) => void;
    vi.mocked(leaveCourse).mockImplementation(() => new Promise((done) => { resolve = done; }));
    await mount(<MyCoursesPage />);
    await openTileMenu(); await act(async () => menuItem('Leave course')!.click());
    const confirm = [...document.querySelectorAll<HTMLButtonElement>('[role="dialog"] button')].find((node) => node.textContent === 'Leave course')!;
    await act(async () => { confirm.click(); confirm.click(); });
    expect(leaveCourse).toHaveBeenCalledTimes(1);
    expect(host.querySelector<HTMLButtonElement>('button[aria-label="More actions for Programming"]')?.disabled).toBe(true);
    const options = vi.mocked(leaveCourse).mock.calls[0][1]!;
    await act(async () => root.unmount()); root = createRoot(host); expect(options.signal?.aborted).toBe(true); expect(options.isCurrent?.()).toBe(false);
    await act(async () => resolve({ courseId: id, status: 'left' })); expect(host.textContent).toBe('');
});
