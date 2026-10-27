// @vitest-environment jsdom
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
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

function catalogueResult(total: number, index: number): CoursePage {
    return { ...page, page: index, totalElements: total, totalPages: Math.ceil(total / 20),
        content: Array.from({ length: Math.max(0, Math.min(20, total - index * 20)) }, (_, offset) => ({
            ...page.content[0], id: `00000000-0000-4000-8000-${String(index * 20 + offset + 1).padStart(12, '0')}`,
            title: `Programming ${index * 20 + offset + 1}`,
        })) };
}
it.each([[21, 20], [61, 21]])('recovers catalogue pagination when %i results shrink to %i', async (before, after) => {
    const oldLast = Math.ceil(before / 20) - 1, last = Math.ceil(after / 20) - 1;
    let recover!: (value: CoursePage) => void;
    vi.mocked(getPublishedCourses).mockResolvedValueOnce(catalogueResult(before, 0))
        .mockResolvedValueOnce(catalogueResult(after, oldLast))
        .mockImplementationOnce(() => new Promise((resolve) => { recover = resolve; }));
    await mount();
    const seen: string[] = [], observer = new MutationObserver(() => seen.push(host.textContent ?? ''));
    observer.observe(host, { childList: true, subtree: true });
    try {
        await act(async () => host.querySelector<HTMLButtonElement>(`[aria-label="Go to page ${oldLast + 1}"]`)!.click());
        expect(vi.mocked(getPublishedCourses).mock.calls.map(([index]) => index)).toEqual([0, oldLast, last]);
        expect(host.textContent).toContain('Loading available courses');
        expect(host.textContent).not.toContain('No published courses found');
        await act(async () => recover(catalogueResult(after, last)));
        expect(host.textContent).toContain(`Showing ${Math.min(20, after - last * 20)} of ${after}`);
        expect(seen.every((text) => !text.includes('No published courses found'))).toBe(true);
        expect(getPublishedCourses).toHaveBeenCalledTimes(3);
    } finally { observer.disconnect(); }
});
it.each([21, 0])('does not issue an extra catalogue request for valid or zero results (%i)', async (total) => {
    vi.mocked(getPublishedCourses).mockResolvedValueOnce(catalogueResult(21, 0)).mockResolvedValueOnce(catalogueResult(total, 1));
    await mount(); await act(async () => host.querySelector<HTMLButtonElement>('[aria-label="Go to page 2"]')!.click());
    expect(vi.mocked(getPublishedCourses).mock.calls.map(([index]) => index)).toEqual([0, 1]);
    expect(host.textContent?.includes('No published courses found')).toBe(total === 0);
    expect(host.textContent).not.toContain('Loading available courses');
});
it('retries a failed catalogue recovery on the corrected page', async () => {
    vi.mocked(getPublishedCourses).mockResolvedValueOnce(catalogueResult(21, 0)).mockResolvedValueOnce(catalogueResult(20, 1))
        .mockRejectedValueOnce(new ApiError('network', 'Unable to connect. Please try again.')).mockResolvedValueOnce(catalogueResult(20, 0));
    await mount(); await act(async () => host.querySelector<HTMLButtonElement>('[aria-label="Go to page 2"]')!.click());
    expect(host.textContent).toContain('Could not load catalogue'); expect(host.textContent).not.toContain('No published courses found');
    await act(async () => [...host.querySelectorAll('button')].find((button) => button.textContent === 'Retry')!.click());
    expect(host.textContent).toContain('Showing 20 of 20');
    expect(vi.mocked(getPublishedCourses).mock.calls.map(([index]) => index)).toEqual([0, 1, 0, 0]);
});
it('cancels catalogue recovery and ignores its late result after filters change', async () => {
    let recover!: (value: CoursePage) => void;
    vi.mocked(getPublishedCourses).mockResolvedValueOnce(catalogueResult(21, 0)).mockResolvedValueOnce(catalogueResult(20, 1))
        .mockImplementationOnce(() => new Promise((resolve) => { recover = resolve; })).mockResolvedValueOnce(catalogueResult(1, 0));
    await mount(); await act(async () => host.querySelector<HTMLButtonElement>('[aria-label="Go to page 2"]')!.click());
    const options = vi.mocked(getPublishedCourses).mock.calls[2][2]!;
    await act(async () => host.querySelector('form')!.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true })));
    expect(options.signal?.aborted).toBe(true); expect(options.isCurrent?.()).toBe(false);
    await act(async () => recover(catalogueResult(20, 0)));
    expect(host.textContent).toContain('Showing 1 of 1'); expect(getPublishedCourses).toHaveBeenCalledTimes(4);
});

it('keeps OpenAPI and the search input on the approved UTF-16-unit limit', async () => {
    const spec: { paths: Record<string, { get: { parameters: { name: string; schema: Record<string, unknown>; description: string }[] } }> } =
        JSON.parse(readFileSync(resolve(process.cwd(), '../docs/api/openapi.yaml'), 'utf8'));
    const query = spec.paths['/api/courses'].get.parameters.find((parameter) => parameter.name === 'q')!;
    expect(query.schema).not.toHaveProperty('maxLength');
    expect(query.schema['x-max-utf16-code-units']).toBe(100);
    expect(query.description).toContain('UTF-16');
    const allowed = new RegExp(String(query.schema.pattern));
    expect(allowed.test('a\0b')).toBe(false); expect(allowed.test('a%00b')).toBe(true);
    expect(allowed.test('😀'.repeat(51))).toBe(true); // Length is the explicit UTF-16 extension, not this NUL-only pattern.
    await mount();
    const input = host.querySelector<HTMLInputElement>('input:not([aria-hidden])')!;
    expect(input.maxLength).toBe(query.schema['x-max-utf16-code-units']);
    const value = '😀'.repeat(50); expect(value.length).toBe(100); expect('😀'.repeat(51).length).toBe(102);
    await act(async () => { Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value')!.set!.call(input, value); input.dispatchEvent(new Event('input', { bubbles: true })); });
    await act(async () => host.querySelector('form')!.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true })));
    expect(vi.mocked(getPublishedCourses).mock.calls.at(-1)?.[2]?.q).toBe(value);
});
