// @vitest-environment jsdom
import { act } from 'react';
import { createRoot } from 'react-dom/client';
import type { Root } from 'react-dom/client';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { CourseCataloguePage } from './CourseCataloguePage';
import { getPublishedCourses } from '../api/catalogueApi';
import type { CoursePage } from '../types/catalogueTypes';
import { ApiError } from '../../../shared/types/apiTypes';
vi.mock('../api/catalogueApi', () => ({ getPublishedCourses: vi.fn() }));
Object.assign(globalThis, { IS_REACT_ACT_ENVIRONMENT: true });
let root: Root, host: HTMLDivElement;
const id = '00000000-0000-4000-8000-000000000001';
const page: CoursePage = { content: [{ id, title: 'Programming', description: 'Foundations', instructorId: id, instructorDisplayName: 'Instructor Demo' }], page: 0, size: 20, totalElements: 21, totalPages: 2 };
beforeEach(() => { host = document.createElement('div'); document.body.append(host); root = createRoot(host); vi.mocked(getPublishedCourses).mockResolvedValue(page); });
afterEach(async () => { await act(async () => root.unmount()); host.remove(); vi.resetAllMocks(); });
async function mount(select = vi.fn()) { await act(async () => root.render(<CourseCataloguePage onSelectCourse={select} />)); return select; }
describe('catalogue page on the merged client contract', () => {
    it('renders server totals, detail navigation and approved page size', async () => {
        const select = await mount(); expect(getPublishedCourses).toHaveBeenCalledWith(0, 20, expect.objectContaining({ signal: expect.any(AbortSignal), isCurrent: expect.any(Function) }));
        expect(host.textContent).toContain('Showing 1 of 21');
        await act(async () => [...host.querySelectorAll('button')].find((button) => button.textContent === 'View Syllabus')!.click()); expect(select).toHaveBeenCalledWith(id);
        await act(async () => host.querySelector<HTMLButtonElement>('[aria-label="Go to page 2"]')!.click()); expect(vi.mocked(getPublishedCourses).mock.calls.at(-1)?.slice(0, 2)).toEqual([1, 20]);
    });
    it('shows loading, ignores a superseded page, and cancels work on unmount', async () => {
        const pending: ((value: CoursePage) => void)[] = [];
        vi.mocked(getPublishedCourses).mockImplementation(() => new Promise((resolve) => pending.push(resolve)));
        await mount(); expect(host.textContent).toContain('Loading available courses');
        await act(async () => pending[0](page));
        await act(async () => host.querySelector<HTMLButtonElement>('[aria-label="Go to page 2"]')!.click());
        const oldOptions = vi.mocked(getPublishedCourses).mock.calls[1][2]!;
        await act(async () => root.unmount()); root = createRoot(host);
        expect(oldOptions.signal?.aborted).toBe(true); expect(oldOptions.isCurrent?.()).toBe(false);
        await mount(); await act(async () => pending[2]({ ...page, content: [{ ...page.content[0], title: 'Current page' }] }));
        await act(async () => pending[1]({ ...page, content: [{ ...page.content[0], title: 'Obsolete page' }] }));
        expect(host.textContent).toContain('Current page'); expect(host.textContent).not.toContain('Obsolete page');
    });
    it('renders empty results and permits an explicit retry after safe errors', async () => {
        vi.mocked(getPublishedCourses).mockRejectedValueOnce(new ApiError('network', 'Unable to connect. Please try again.'));
        await mount(); expect(host.textContent).toContain('Could not load catalogue');
        vi.mocked(getPublishedCourses).mockResolvedValue({ ...page, content: [], totalElements: 0, totalPages: 0 });
        await act(async () => [...host.querySelectorAll('button')].find((button) => button.textContent === 'Retry')!.click()); expect(host.textContent).toContain('No published courses found');
    });
});

it('submits search/filter criteria and resets a later page to zero', async () => {
    await mount(); await act(async () => host.querySelector<HTMLButtonElement>('[aria-label="Go to page 2"]')!.click());
    const input = host.querySelector<HTMLInputElement>('input:not([aria-hidden])')!;
    await act(async () => { Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value')!.set!.call(input, ' basics '); input.dispatchEvent(new Event('input', { bubbles: true })); });
    await act(async () => host.querySelector('form')!.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true })));
    expect(vi.mocked(getPublishedCourses).mock.calls.at(-1)).toEqual([0, 20, expect.objectContaining({ q: 'basics', prerequisites: 'any' })]);
});
