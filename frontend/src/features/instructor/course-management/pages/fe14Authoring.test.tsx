// @vitest-environment jsdom
import { act } from 'react';
import { createRoot } from 'react-dom/client';
import type { Root } from 'react-dom/client';
import { MemoryRouter, useNavigate } from 'react-router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { AuthContext } from '../../../../app/providers/AuthProvider';
import type { AuthContextValue } from '../../../../app/providers/AuthProvider';
import { AppRouter } from '../../../../routes';
import { ApiError } from '../../../../shared/types/apiTypes';
import * as api from '../api/instructorCourseApi';
import * as graph from '../../skill-graph-editor/api/skillGraphApi';
import * as notifications from '../../../interaction/api/notificationApi';
import type { OwnedCourse, OwnedMaterial } from '../api/instructorCourseApi';
import { FileDownloadButton, FileUploadField } from '../../../../shared/components/FileUploadField';
vi.mock('../api/instructorCourseApi', async (original) => {
    const actual = await original<typeof api>();
    return Object.fromEntries(Object.entries(actual).map(([key, value]) => [key, typeof value === 'function' ? vi.fn() : value]));
});
vi.mock('../../skill-graph-editor/api/skillGraphApi', async (original) => ({ ...await original<object>(), getOwnedSkillGraph: vi.fn(), mapCourseLessonSkill: vi.fn(), unmapCourseLessonSkill: vi.fn() }));
vi.mock('../../../interaction/api/notificationApi', async (original) => ({ ...await original<object>(), countUnreadNotifications: vi.fn(), listMyNotifications: vi.fn() }));
Object.assign(globalThis, { IS_REACT_ACT_ENVIRONMENT: true });
const c = '00000000-0000-4000-8000-000000000001', l = '00000000-0000-4000-8000-000000000002', ch = '00000000-0000-4000-8000-000000000003', m = '00000000-0000-4000-8000-000000000004', skill = '00000000-0000-4000-8000-000000000005';
const initial = (): OwnedCourse => ({ id: c, title: 'Course', description: 'Description', prerequisiteDescription: null, publicationStatus: 'hidden', chapters: [{ id: ch, title: 'Chapter', position: 1, lessons: [{ id: l, chapterId: ch, title: 'Lesson', contentMarkdown: '# Content', position: 1, publicationStatus: 'hidden', materials: [] }] }] });
let course: OwnedCourse, root: Root, host: HTMLDivElement, auth: AuthContextValue;
const lesson = () => course.chapters[0].lessons[0];
const flush = () => act(async () => { await new Promise((resolve) => setTimeout(resolve, 0)); });
const controls = (scope: ParentNode = document) => [...scope.querySelectorAll<HTMLElement>('button,a,[role="button"]')];
function button(text: string, scope: ParentNode = document) { const found = controls(scope).find((item) => item.textContent?.trim() === text || item.getAttribute('aria-label') === text); if (!found) throw new Error(`Missing ${text} in ${controls(scope).map(x => x.textContent).join('|')}`); return found as HTMLButtonElement; }
const click = async (text: string, scope: ParentNode = document) => { await act(async () => button(text, scope).click()); await flush(); };
const modal = () => [...document.querySelectorAll<HTMLElement>('[role="dialog"]')].at(-1)!;
function field(label: string, scope: ParentNode = document) { const node = [...scope.querySelectorAll('label')].find((x) => [...x.childNodes].filter(n => n.nodeType === Node.TEXT_NODE).map(n => n.textContent).join('').trim() === label); if (!node) throw new Error(`Missing field ${label}`); return document.getElementById(node.htmlFor) as HTMLInputElement; }
async function type(label: string, value: string, scope: ParentNode = document) { const el = field(label, scope); await act(async () => { Object.getOwnPropertyDescriptor(el instanceof HTMLTextAreaElement ? HTMLTextAreaElement.prototype : HTMLInputElement.prototype, 'value')!.set!.call(el, value); el.dispatchEvent(new Event('input', { bubbles: true })); }); }
function HistoryProbe() { const navigate = useNavigate(); return <><button onClick={() => navigate(-1)}>Native back probe</button><button onClick={() => navigate(1)}>Native forward probe</button></>; }
async function mount(path = `/teach/courses/${c}/lessons/${l}`) { await act(async () => root.render(<MemoryRouter initialEntries={[`/teach/courses/${c}`, path]}><HistoryProbe /><AuthContext.Provider value={auth}><AppRouter /></AuthContext.Provider></MemoryRouter>)); await flush(); }
const text = () => document.body.textContent ?? '';
beforeEach(() => {
    vi.resetAllMocks(); vi.stubGlobal('ResizeObserver', class { observe() {} disconnect() {} unobserve() {} }); vi.stubGlobal('DOMMatrixReadOnly', class { m22 = 1; });
    host = document.createElement('div'); document.body.append(host); root = createRoot(host); course = initial();
    auth = { user: { id: skill, displayName: 'Instructor', role: 'Instructor' }, generation: 1, isAuthenticated: true, restoring: false, isCurrent: n => n === 1, login: vi.fn(), register: vi.fn(), logout: vi.fn(), rememberProfile: vi.fn(), startPath: '/teach', consumeReturn: () => '/teach' };
    vi.mocked(api.getOwnedCourse).mockImplementation(async () => structuredClone(course));
    vi.mocked(graph.getOwnedSkillGraph).mockImplementation(async () => ({ courseId: c, skills: [{ id: skill, name: 'Loops', description: null }], prerequisites: [], mappings: [] }));
    vi.mocked(notifications.countUnreadNotifications).mockResolvedValue({ unreadCount: 2 });
    vi.mocked(notifications.listMyNotifications).mockResolvedValue({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 });
});
afterEach(async () => { await act(async () => root.unmount()); host.remove(); vi.unstubAllGlobals(); });
describe('FE14 production authoring and reconciliation', () => {
    it('deep-links into one shared owner tree; Preview is AST-rendered and no mutation runs on entry', async () => {
        await mount(); expect(document.querySelector('h1')?.textContent).toBe('Lesson'); expect(api.getOwnedCourse).toHaveBeenCalledTimes(1); expect(graph.getOwnedSkillGraph).toHaveBeenCalledTimes(1);
        await click('Preview'); expect(document.querySelector('h2')?.textContent).toBe('Lesson'); expect(text()).toContain('Content'); expect(api.updateCourseLesson).not.toHaveBeenCalled();
    });
    it('keeps edits unsaved until the response, then blocks until a causally eligible tree re-read', async () => {
        let resolve!: (body: api.OwnedLesson) => void;
        vi.mocked(api.updateCourseLesson).mockImplementation(() => new Promise(r => { resolve = r; }));
        await mount(); await type('Lesson title', 'Edited'); await click('Save changes'); expect(text()).toContain('Saving…'); expect(button('Save changes').disabled).toBe(true);
        expect(api.updateCourseLesson).toHaveBeenCalledWith(c, l, { title: 'Edited', contentMarkdown: '# Content' }, expect.objectContaining({ signal: expect.any(AbortSignal) }));
        lesson().title = 'Edited'; await act(async () => resolve(structuredClone(lesson()))); await flush(); expect(field('Lesson title').value).toBe('Edited'); expect(text()).toContain('Saved'); expect(api.getOwnedCourse).toHaveBeenCalledTimes(2);
    });
    it('asks before shell/back navigation with a draft and does not require mappings to save', async () => {
        await mount(); await type('Lesson title', 'Draft'); await click('Content'); expect(text()).toContain('Leave without saving?'); await click('Keep editing', modal()); expect(field('Lesson title').value).toBe('Draft'); expect(api.updateCourseLesson).not.toHaveBeenCalled();
    });
    it('FE14/FE28: the actual Lesson draft intercepts the bell while modified clicks stay native', async () => {
        await mount(); await type('Lesson title', 'Private draft');
        const bell = document.querySelector<HTMLAnchorElement>('header a[href="/notifications"]')!;
        const modified = new MouseEvent('click', { bubbles: true, cancelable: true, ctrlKey: true });
        await act(async () => bell.dispatchEvent(modified));
        expect(modified.defaultPrevented).toBe(false); expect(text()).not.toContain('Leave without saving?');
        await act(async () => bell.click()); await flush();
        expect(text()).toContain('Leave without saving?');
        expect(notifications.listMyNotifications).not.toHaveBeenCalled();
        await click('Keep editing', modal()); expect(field('Lesson title').value).toBe('Private draft');
        expect(api.getOwnedCourse).toHaveBeenCalledTimes(1); expect(notifications.countUnreadNotifications).toHaveBeenCalledTimes(1);
        await act(async () => bell.click()); await click('Leave', modal());
        expect(document.querySelector('h1')?.textContent).toBe('Notifications');
        expect(bell.getAttribute('aria-current')).toBe('page');
        expect(notifications.listMyNotifications).toHaveBeenCalledTimes(1);
        expect(notifications.countUnreadNotifications).toHaveBeenCalledTimes(2);
        expect(api.getOwnedCourse).toHaveBeenCalledTimes(1); expect(api.updateCourseLesson).not.toHaveBeenCalled();
        expect(text()).not.toContain('Private draft');
    });
    it('FE14/FE28: bell Keep editing retains a pending write and failed recovery permits deliberate navigation without replay', async () => {
        let settle!: (value: api.OwnedLesson) => void;
        vi.mocked(api.updateCourseLesson).mockImplementation(() => new Promise(resolve => { settle = resolve; }));
        await mount(); await type('Lesson title', 'Acknowledged'); await click('Save changes');
        const bell = document.querySelector<HTMLAnchorElement>('header a[href="/notifications"]')!;
        await act(async () => bell.click()); await flush(); await click('Keep editing', modal());
        expect(button('Save changes').disabled).toBe(true); expect(api.getOwnedCourse).toHaveBeenCalledTimes(1);
        expect(notifications.listMyNotifications).not.toHaveBeenCalled();
        lesson().title = 'Acknowledged'; vi.mocked(api.getOwnedCourse).mockRejectedValueOnce(new ApiError('network', 'offline'));
        await act(async () => settle(structuredClone(lesson()))); await flush();
        expect(text()).toContain("We couldn't load this course. The last loaded version is shown.");
        expect(button('Save changes').disabled).toBe(true); expect(api.getOwnedCourse).toHaveBeenCalledTimes(2);
        await act(async () => bell.click()); await flush();
        expect(document.querySelector('h1')?.textContent).toBe('Notifications');
        await click('Native back probe');
        expect(field('Lesson title').value).toBe('Acknowledged'); expect(api.getOwnedCourse).toHaveBeenCalledTimes(3);
        expect(api.updateCourseLesson).toHaveBeenCalledTimes(1); expect(notifications.listMyNotifications).toHaveBeenCalledTimes(1);
    });
    it('reconciles an unknown save without replay and preserves the draft when refresh fails', async () => {
        await mount(); vi.mocked(api.updateCourseLesson).mockRejectedValue(new ApiError('network', 'offline')); vi.mocked(api.getOwnedCourse).mockRejectedValue(new ApiError('network', 'offline'));
        await type('Lesson title', 'Draft'); await click('Save changes'); expect(api.updateCourseLesson).toHaveBeenCalledTimes(1); expect(field('Lesson title').value).toBe('Draft'); expect(button('Save changes').disabled).toBe(true); expect(text()).toContain('Checking the current lesson');
    });
    it('creates text material independently of unsaved Lesson fields and retains returned material order', async () => {
        vi.mocked(api.createRelationalMaterial).mockImplementation(async (_, __, payload) => { const row = { ...payload, id: m, publicationStatus: 'hidden' as const }; lesson().materials = [row]; return row; });
        await mount(); await type('Lesson title', 'Draft'); await click('Add text'); await type('Title', 'Notes', modal()); await type('Text (Markdown)', '**Body**', modal()); await click('Add material', modal());
        expect(api.createRelationalMaterial).toHaveBeenCalledWith(c, l, { materialType: 'text', title: 'Notes', textContent: '**Body**' }, expect.anything()); expect(field('Lesson title').value).toBe('Draft'); expect(text()).toContain('Notes'); expect(api.updateCourseLesson).not.toHaveBeenCalled();
    });
    it('validates the canonical video address with exact helper text', async () => { await mount(); await click('Add video link'); expect(text()).toContain('Use the embed address that starts with https://www.youtube-nocookie.com/embed/'); await type('Title', 'Video', modal()); await type('Video link', 'https://youtube.com/watch?v=x', modal()); await click('Add material', modal()); expect(api.createRelationalMaterial).not.toHaveBeenCalled(); expect(text()).toContain('Enter a YouTube privacy-enhanced embed address.'); });
    it('retains actual File bytes and locks another upload until unknown-outcome resource reconciliation', async () => {
        let reject!: (error: unknown) => void, resolveRead!: (value: OwnedCourse) => void;
        vi.mocked(api.b5CreateFileMaterial).mockImplementation(() => new Promise((_, r) => { reject = r; }));
        await mount(); await click('Add PDF file'); await type('Title', 'PDF', modal());
        const input = modal().querySelector<HTMLInputElement>('input[type=file]')!, file = new File(['%PDF-actual'], 'actual.pdf');
        await act(async () => { Object.defineProperty(input, 'files', { value: [file], configurable: true }); input.dispatchEvent(new Event('change', { bubbles: true })); });
        vi.mocked(api.getOwnedCourse).mockImplementation(() => new Promise(r => { resolveRead = r; }));
        await click('Add material', modal()); expect(vi.mocked(api.b5CreateFileMaterial).mock.calls[0][3]).toBe(file); expect(input.disabled).toBe(true);
        await act(async () => reject(new ApiError('unknown-outcome', 'unknown'))); await flush(); expect(text()).toContain('The upload outcome is unknown'); expect(button('Add material', modal()).disabled).toBe(true); expect(api.b5CreateFileMaterial).toHaveBeenCalledTimes(1);
        lesson().materials = [{ id: m, title: 'PDF', materialType: 'file', publicationStatus: 'hidden' }]; await act(async () => resolveRead(structuredClone(course))); await flush(); expect(text()).toContain('Review the returned list'); expect(button('Add material', modal()).disabled).toBe(false);
    });
    it('requires publication confirmation and refreshes the authoritative graph after a 204 mapping', async () => {
        vi.mocked(api.setCourseLessonPublication).mockImplementation(async (_, __, next) => { lesson().publicationStatus = next; return structuredClone(lesson()); });
        await mount(); await click('Publish lesson'); expect(api.setCourseLessonPublication).not.toHaveBeenCalled(); await click('Publish lesson', modal()); expect(api.setCourseLessonPublication).toHaveBeenCalledTimes(1);
        vi.mocked(graph.mapCourseLessonSkill).mockResolvedValue(undefined); await click('Loops'); expect(graph.mapCourseLessonSkill).toHaveBeenCalledWith(c, l, skill, expect.anything()); expect(graph.getOwnedSkillGraph).toHaveBeenCalledTimes(2);
    });
    it('keeps mutation reconciliation across a Lesson remount and rejects the stale tree as a write base', async () => {
        let resolveRead!: (value: OwnedCourse) => void;
        await mount();
        vi.mocked(api.updateCourseLesson).mockImplementation(async (_, __, write) => { lesson().title = write.title; return structuredClone(lesson()); });
        vi.mocked(api.getOwnedCourse).mockImplementation(() => new Promise(r => { resolveRead = r; }));
        await type('Lesson title', 'Confirmed title'); await click('Save changes');
        await click('Content');
        // Content has only the old body while the post-mutation GET is pending. It must not let
        // a fresh child editor (or another structure mutation) use that body as a write base.
        expect(button('Edit').disabled).toBe(true); expect(button('Add chapter').disabled).toBe(true);
        await act(async () => resolveRead(structuredClone(course))); await flush();
        await click('Edit'); expect(field('Lesson title').value).toBe('Confirmed title');
        expect(api.updateCourseLesson).toHaveBeenCalledTimes(1);
    });
    it('cancels an upload through its signal and reconciles instead of offering an immediate retry', async () => {
        let signal!: AbortSignal;
        vi.mocked(api.b5CreateFileMaterial).mockImplementation((_, __, ___, ____, options) => new Promise((_, reject) => { signal = options!.signal!; signal.addEventListener('abort', () => reject(new ApiError('cancelled', 'cancelled'))); }));
        await mount(); await click('Add PDF file'); await type('Title', 'PDF', modal());
        const input = modal().querySelector<HTMLInputElement>('input[type=file]')!;
        await act(async () => { Object.defineProperty(input, 'files', { value: [new File(['bytes'], 'actual.pdf')], configurable: true }); input.dispatchEvent(new Event('change', { bubbles: true })); });
        vi.mocked(api.getOwnedCourse).mockRejectedValue(new ApiError('network', 'offline'));
        await click('Add material', modal()); await click('Cancel upload', modal());
        expect(signal.aborted).toBe(true); expect(text()).toContain('The upload outcome is unknown'); expect(button('Add material', modal()).disabled).toBe(true); expect(api.b5CreateFileMaterial).toHaveBeenCalledTimes(1);
    });
    it('suppresses a late upload after the session generation changes', async () => {
        let resolve!: (row: OwnedMaterial) => void;
        vi.mocked(api.b5CreateFileMaterial).mockImplementation(() => new Promise(r => { resolve = r; }));
        await mount(); await click('Add PDF file'); await type('Title', 'PDF', modal());
        const input = modal().querySelector<HTMLInputElement>('input[type=file]')!;
        await act(async () => { Object.defineProperty(input, 'files', { value: [new File(['bytes'], 'actual.pdf')] }); input.dispatchEvent(new Event('change', { bubbles: true })); });
        await click('Add material', modal());
        auth = { ...auth, generation: 2, isCurrent: n => n === 2 };
        await act(async () => root.render(<MemoryRouter initialEntries={[`/teach/courses/${c}/lessons/${l}`]}><AuthContext.Provider value={auth}><AppRouter /></AuthContext.Provider></MemoryRouter>));
        await act(async () => resolve({ id: m, title: 'Old-session PDF', materialType: 'file', publicationStatus: 'hidden' })); await flush();
        expect(text()).not.toContain('Old-session PDF'); expect(document.querySelector('[aria-labelledby=production-material-title]')).toBeNull();
    });
    it('exposes Chapter edit/reorder and Lesson Edit from Content without reading the course twice', async () => {
        await mount(`/teach/courses/${c}`); expect(button('Edit chapter')).toBeTruthy(); await click('Edit'); expect(field('Lesson title').value).toBe('Lesson'); expect(api.getOwnedCourse).toHaveBeenCalledTimes(1);
    });
    it.each([403, 404])('drops the editor after authoring refusal %s', async status => { vi.mocked(api.updateCourseLesson).mockRejectedValue(new ApiError('http', 'refused', status)); await mount(); await type('Lesson title', 'Draft'); await click('Save changes'); expect(document.querySelector('input')).toBeNull(); expect(text()).toContain(status === 403 ? "You don't have access" : 'Course not available'); });
    it('renders a missing Lesson as concealed unavailable without a mutation', async () => { await mount(`/teach/courses/${c}/lessons/${m}`); expect(text()).toContain('Lesson not available'); expect(api.updateCourseLesson).not.toHaveBeenCalled(); });
    it.each([413, 415])('keeps known file rejection %s distinct from an unknown upload', async status => {
        vi.mocked(api.b5CreateFileMaterial).mockRejectedValue(new ApiError('http', 'The uploaded media type is not supported.', status));
        await mount(); await click('Add PDF file'); await type('Title', 'PDF', modal());
        const input = modal().querySelector<HTMLInputElement>('input[type=file]')!;
        await act(async () => { Object.defineProperty(input, 'files', { value: [new File(['bytes'], 'actual.pdf')] }); input.dispatchEvent(new Event('change', { bubbles: true })); });
        await click('Add material', modal()); expect(text()).toContain(status === 413 ? 'The file is larger than the allowed size.' : 'The uploaded media type is not supported.');
        expect(api.getOwnedCourse).toHaveBeenCalledTimes(1); expect(button('Add material', modal()).disabled).toBe(false);
    });
    it('shows server-confirmed material field errors verbatim without claiming success', async () => {
        vi.mocked(api.createRelationalMaterial).mockRejectedValue(new ApiError('http', 'Please check the highlighted fields.', 400, undefined, { title: 'Server title validation.' }));
        await mount(); await click('Add text'); await type('Title', 'Title', modal()); await type('Text (Markdown)', 'Body', modal()); await click('Add material', modal());
        expect(text()).toContain('Server title validation.'); expect(text()).not.toContain('Material saved.'); expect(field('Title', modal()).getAttribute('aria-invalid')).toBe('true');
    });
    it('rejects multi-file selection and locks selection while uploading', async () => {
        const select = vi.fn();
        await act(async () => root.render(<FileUploadField label="PDF file" state={{ kind: 'idle' }} onSelect={select} />));
        const input = host.querySelector<HTMLInputElement>('input[type=file]')!;
        await act(async () => { Object.defineProperty(input, 'files', { value: [new File(['a'], 'a.pdf'), new File(['b'], 'b.pdf')], configurable: true }); input.dispatchEvent(new Event('change', { bubbles: true })); });
        expect(select).not.toHaveBeenCalled(); expect(text()).toContain('Choose one file at a time.');
        await act(async () => root.render(<FileUploadField label="PDF file" state={{ kind: 'uploading', fileName: 'a.pdf' }} onSelect={select} />)); expect(input.disabled).toBe(true); expect(button('Choose a file').disabled).toBe(true);
    });
    it('revokes transient download URLs and uses only the server filename', async () => {
        const create = vi.fn(() => 'blob:synthetic'), revoke = vi.fn(), clickAnchor = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(function (this: HTMLAnchorElement) { expect(this.download).toBe('server.pdf'); });
        URL.createObjectURL = create; URL.revokeObjectURL = revoke;
        await act(async () => root.render(<FileDownloadButton load={async () => ({ blob: new Blob(['pdf']), filename: 'server.pdf' })} />)); await click('Download PDF'); expect(create).toHaveBeenCalledTimes(1); expect(revoke).toHaveBeenCalledWith('blob:synthetic'); expect(clickAnchor).toHaveBeenCalledTimes(1); clickAnchor.mockRestore();
    });
});


describe('FE14-IR-01 sent work outlives the initiating child', () => {
    it('retains the committed Lesson PUT through app Leave, then prevents a stale full replacement', async () => {
        let settle!: (value: api.OwnedLesson) => void, signal!: AbortSignal, isCurrent!: () => boolean;
        vi.mocked(api.updateCourseLesson).mockImplementation((_, __, write, options) => {
            lesson().title = write.title;
            signal = options!.signal!; isCurrent = options!.isCurrent!;
            return new Promise(r => { settle = r; });
        });
        await mount(); await type('Lesson title', 'Server committed title'); await click('Save changes');
        await click('Content'); await click('Leave', modal());
        expect(signal.aborted).toBe(false); expect(isCurrent()).toBe(true);
        expect(button('Edit').disabled).toBe(true); expect(button('Add chapter').disabled).toBe(true);
        expect(api.getOwnedCourse).toHaveBeenCalledTimes(1);
        await act(async () => settle(structuredClone(lesson()))); await flush();
        expect(button('Edit').disabled).toBe(false);
        await click('Edit'); expect(field('Lesson title').value).toBe('Server committed title');
        vi.mocked(api.updateCourseLesson).mockImplementation(async (_, __, write) => { Object.assign(lesson(), write); return structuredClone(lesson()); });
        await type('Content (Markdown)', 'Later content only'); await click('Save changes');
        expect(vi.mocked(api.updateCourseLesson).mock.calls[1][2].title).toBe('Server committed title');
    });
    it('keeps an accepted PDF pending through native Back/Forward, failed recovery and explicit retry', async () => {
        let settle!: (value: OwnedMaterial) => void, signal!: AbortSignal;
        const row: OwnedMaterial = { id: m, title: 'Committed PDF', materialType: 'file', publicationStatus: 'hidden' };
        vi.mocked(api.b5CreateFileMaterial).mockImplementation((_, __, ___, ____, options) => {
            lesson().materials = [row]; signal = options!.signal!;
            return new Promise(r => { settle = r; });
        });
        await mount(); await click('Add PDF file'); await type('Title', row.title, modal());
        const input = modal().querySelector<HTMLInputElement>('input[type=file]')!;
        await act(async () => { Object.defineProperty(input, 'files', { value: [new File(['%PDF-1.7'], 'actual.pdf')] }); input.dispatchEvent(new Event('change', { bubbles: true })); });
        await click('Add material', modal()); await click('Native back probe'); await click('Native forward probe');
        expect(signal.aborted).toBe(false); expect(button('Add PDF file').disabled).toBe(true);
        expect(api.getOwnedCourse).toHaveBeenCalledTimes(1);
        vi.mocked(api.getOwnedCourse).mockRejectedValueOnce(new ApiError('network', 'offline'));
        await act(async () => settle(row)); await flush();
        expect(button('Add PDF file').disabled).toBe(true); expect(api.b5CreateFileMaterial).toHaveBeenCalledTimes(1);
        await click('Try again');
        expect(button('Add PDF file').disabled).toBe(false); expect(text()).toContain('Committed PDF');
        expect(lesson().materials).toHaveLength(1); expect(api.b5CreateFileMaterial).toHaveBeenCalledTimes(1);
    });
});

it('preserves the acknowledged Lesson fields while its post-settlement read is delayed', async () => {
    let resolveRead!: (body: OwnedCourse) => void;
    await mount();
    vi.mocked(api.getOwnedCourse).mockImplementation(() => new Promise(r => { resolveRead = r; }));
    vi.mocked(api.updateCourseLesson).mockImplementation(async (_, __, write) => { lesson().title = write.title; return structuredClone(lesson()); });
    await type('Lesson title', 'Acknowledged title'); await click('Save changes');
    expect(field('Lesson title').value).toBe('Acknowledged title');
    expect(button('Save changes').disabled).toBe(true);
    await act(async () => resolveRead(structuredClone(course))); await flush();
    expect(field('Lesson title').value).toBe('Acknowledged title');
});

it('CodeQL #8: distinguishes literal asterisks in label text from the required marker', async () => {
    await act(async () => root.render(<><label htmlFor="literal-stars">Two * literal * marks<span aria-hidden="true">*</span></label><input id="literal-stars" /></>));
    expect(field('Two * literal * marks').id).toBe('literal-stars');
});
