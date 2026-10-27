// @vitest-environment jsdom
import { act } from 'react';
import { createRoot } from 'react-dom/client';
import type { Root } from 'react-dom/client';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { CourseDetailPage } from './CourseDetailPage';
import { getPublishedCourseById } from '../api/catalogueApi';
import type { CourseDetail } from '../types/catalogueTypes';
import { ApiError } from '../../../shared/types/apiTypes';
vi.mock('../api/catalogueApi', () => ({ getPublishedCourseById: vi.fn() }));
Object.assign(globalThis, { IS_REACT_ACT_ENVIRONMENT: true });
let root: Root, host: HTMLDivElement;
const id = '00000000-0000-4000-8000-000000000001';
const detail: CourseDetail = { id, title: 'Programming', description: 'Foundations', instructorId: id, instructorDisplayName: 'Instructor Demo', prerequisiteDescription: 'Read the introduction', syllabus: [{ id, title: 'Chapter', position: 1, lessons: [{ id, title: 'Lesson', position: 1 }] }] };
beforeEach(() => { host = document.createElement('div'); document.body.append(host); root = createRoot(host); vi.mocked(getPublishedCourseById).mockResolvedValue(detail); });
afterEach(async () => { await act(async () => root.unmount()); host.remove(); vi.resetAllMocks(); });
async function mount(courseId = id, back = vi.fn()) { await act(async () => root.render(<CourseDetailPage courseId={courseId} onBack={back} />)); return back; }
describe('course detail integration', () => {
    it('renders approved detail, prerequisites and syllabus without membership controls', async () => {
        const back = await mount(); expect(host.textContent).toContain('Read the introduction'); expect(host.textContent).toContain('1. Lesson'); expect(host.textContent).not.toContain('Enroll Now');
        await act(async () => [...host.querySelectorAll('button')].find((button) => button.textContent === 'Back to Catalogue')!.click()); expect(back).toHaveBeenCalledOnce();
    });
    it('links multiple chapter controls to uniquely identified regions and keeps associations stable', async () => {
        const secondId = '00000000-0000-4000-8000-000000000002';
        vi.mocked(getPublishedCourseById).mockResolvedValue({ ...detail, syllabus: [
            detail.syllabus[0],
            { id: secondId, title: 'Practice', position: 2, lessons: [{ id: secondId, title: 'Apply concepts', position: 1 }] },
        ] });
        await mount();
        const controls = [...host.querySelectorAll<HTMLButtonElement>('.MuiAccordionSummary-root')];
        expect(controls).toHaveLength(2);
        const ids = controls.flatMap((control) => {
            expect(control.id).not.toBe('');
            const panelId = control.getAttribute('aria-controls');
            expect(panelId).toBeTruthy();
            const panel = document.getElementById(panelId!);
            expect(panel?.getAttribute('role')).toBe('region');
            expect(panel?.getAttribute('aria-labelledby')).toBe(control.id);
            expect(control.getAttribute('aria-expanded')).toBe('true');
            return [control.id, panelId];
        });
        expect(new Set(ids).size).toBe(4);
        expect(document.getElementById(ids[1]!)?.textContent).toContain('1. Lesson');
        expect(document.getElementById(ids[3]!)?.textContent).toContain('1. Apply concepts');
        await act(async () => controls[0].click());
        expect(controls[0].getAttribute('aria-expanded')).toBe('false');
        expect(controls[1].getAttribute('aria-expanded')).toBe('true');
        await act(async () => controls[0].click());
        await mount();
        expect(controls.flatMap((control) => [control.id, control.getAttribute('aria-controls')])).toEqual(ids);
        expect(controls[0].getAttribute('aria-expanded')).toBe('true');
    });
    it('suppresses an old detail response after the route ID changes', async () => {
        let old!: (value: CourseDetail) => void;
        vi.mocked(getPublishedCourseById).mockImplementationOnce(() => new Promise((resolve) => { old = resolve; }));
        await mount(); expect(host.textContent).toContain('Loading course syllabus');
        const options = vi.mocked(getPublishedCourseById).mock.calls[0][1]!;
        await mount('00000000-0000-4000-8000-000000000002'); expect(options.signal?.aborted).toBe(true); expect(options.isCurrent?.()).toBe(false);
        await act(async () => old({ ...detail, title: 'Obsolete course' })); expect(host.textContent).toContain('Programming'); expect(host.textContent).not.toContain('Obsolete course');
    });
    it('shows the uniform missing/hidden 404 and retries the current read', async () => {
        vi.mocked(getPublishedCourseById).mockRejectedValueOnce(new ApiError('http', 'The requested resource is unavailable.', 404));
        await mount(); expect(host.textContent).toContain('The requested resource is unavailable.');
        await act(async () => [...host.querySelectorAll('button')].find((button) => button.textContent === 'Retry')!.click()); expect(host.querySelector('h1')?.textContent).toBe('Programming');
    });
});
