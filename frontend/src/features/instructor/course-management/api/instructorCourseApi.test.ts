import { youtubeEmbedAddress } from '../components/MaterialForm';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { bindAuthSession } from '../../../../shared/api/httpClient';
import * as api from './instructorCourseApi';
const course = '00000000-0000-4000-8000-000000000001', lesson = '00000000-0000-4000-8000-000000000002', chapter = '00000000-0000-4000-8000-000000000003', material = '00000000-0000-4000-8000-000000000004';
const pdf = { id: material, title: 'PDF', materialType: 'file' as const, publicationStatus: 'hidden' as const };
const text = { ...pdf, materialType: 'text' as const, textContent: '# Heading' };
const lessonBody = { id: lesson, chapterId: chapter, title: 'Lesson', contentMarkdown: '  code\n', position: 1, publicationStatus: 'hidden', materials: [text, pdf] };
const json = (body: unknown, status = 200) => new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
let fetcher: ReturnType<typeof vi.fn>, unbind: () => void;
beforeEach(() => { vi.stubEnv('VITE_API_BASE_URL', 'http://localhost:8080'); fetcher = vi.fn(); vi.stubGlobal('fetch', fetcher); unbind = bindAuthSession({ capture: () => ({ generation: 1, accessToken: 'synthetic', expiresAt: Date.now() + 60000 }), isCurrent: () => true, invalidate: vi.fn() }); });
afterEach(() => { unbind(); vi.unstubAllGlobals(); vi.unstubAllEnvs(); });
const call = (i = 0) => { const [url, init] = fetcher.mock.calls[i]; return { url: String(url), init: init as RequestInit & { headers: Headers } }; };
describe('FE14 approved operation consumers', () => {
    it('replaces the whole Lesson, preserving Markdown and material order', async () => {
        fetcher.mockResolvedValue(json(lessonBody)); expect(await api.updateCourseLesson(course, lesson, { title: 'Lesson', contentMarkdown: '  code\n' })).toEqual(lessonBody);
        expect(call().url).toContain(`/instructor/courses/${course}/lessons/${lesson}`); expect(call().init.method).toBe('PUT'); expect(JSON.parse(call().init.body as string)).toEqual({ title: 'Lesson', contentMarkdown: '  code\n' });
    });
    it('creates and replaces text/video with exclusive approved payloads', async () => {
        const write = { title: text.title, materialType: 'text' as const, textContent: text.textContent };
        fetcher.mockResolvedValueOnce(json(text, 201)); await api.createRelationalMaterial(course, lesson, write); expect(call().init.method).toBe('POST'); expect(JSON.parse(call().init.body as string)).toEqual(write);
        const video = { ...pdf, materialType: 'external_video' as const, externalVideoUrl: 'https://www.youtube-nocookie.com/embed/abc' };
        fetcher.mockResolvedValueOnce(json(video)); await api.updateRelationalMaterial(course, lesson, material, { title: video.title, materialType: video.materialType, externalVideoUrl: video.externalVideoUrl }); expect(call(1).init.method).toBe('PUT'); expect(call(1).url).toContain(`/materials/${material}`);
    });
    it('uses separate explicit relational and file publication endpoints', async () => {
        fetcher.mockResolvedValueOnce(json({ ...text, publicationStatus: 'published' })); await api.setRelationalMaterialPublication(course, lesson, text, 'published');
        fetcher.mockResolvedValueOnce(json({ ...pdf, publicationStatus: 'published' })); await api.b5SetFileMaterialPublication(course, lesson, material, 'published');
        expect(call(0).url).toContain(`/materials/${material}/publication`); expect(call(1).url).toContain(`/file-materials/${material}/publication`); expect(JSON.parse(call(1).init.body as string)).toEqual({ publicationStatus: 'published' });
    });
    it('sends exact raw File bytes and lets the browser own multipart Content-Type', async () => {
        const file = new File(['%PDF-1.7\nbytes'], 'actual.pdf', { type: 'application/pdf' });
        fetcher.mockResolvedValueOnce(json(pdf, 201)); await api.b5CreateFileMaterial(course, lesson, 'PDF', file);
        const form = call().init.body as FormData; expect([...form.keys()]).toEqual(['title', 'file']); expect(await (form.get('file') as File).text()).toBe(await file.text()); expect(call().init.headers.has('Content-Type')).toBe(false); expect(call().init.headers.get('Authorization')).toBe('Bearer synthetic'); expect(call().init.credentials).toBe('omit');
        fetcher.mockResolvedValueOnce(json(pdf)); await api.b5ReplaceFileMaterialFile(course, lesson, material, file); expect([...(call(1).init.body as FormData).keys()]).toEqual(['file']); expect(call(1).url).toContain(`/file-materials/${material}/file`);
    });
    it('updates the PDF title without a file, and downloads protected bytes with server filename', async () => {
        fetcher.mockResolvedValueOnce(json(pdf)); await api.b5UpdateFileMaterialTitle(course, lesson, material, 'PDF'); expect(JSON.parse(call().init.body as string)).toEqual({ title: 'PDF' });
        fetcher.mockResolvedValueOnce(new Response('%PDF-bytes', { headers: { 'Content-Type': 'application/pdf', 'Content-Disposition': "attachment; filename=\"fallback.pdf\"; filename*=UTF-8''T%C3%A0i.pdf" } }));
        const download = await api.b5DownloadMaterialFile(course, lesson, material); expect(download.filename).toBe('Tài.pdf'); expect(await download.blob.text()).toBe('%PDF-bytes'); expect(call(1).url).toBe(`http://localhost:8080/api/courses/${course}/lessons/${lesson}/materials/${material}/file`);
    });
    it.each([{ ...pdf, objectKey: 'secret' }, { ...pdf, id: chapter }, { ...pdf, materialType: 'text', textContent: 'x' }, { ...pdf, publicationStatus: 'draft' }])('rejects malformed or unrelated file metadata', async (body) => { fetcher.mockResolvedValue(json(body)); await expect(api.b5UpdateFileMaterialTitle(course, lesson, material, 'PDF')).rejects.toMatchObject({ kind: 'protocol' }); });
    it.each([500, 'network', 'bad-json'] as const)('does not replay an upload with unknown outcome %s', async (failure) => {
        if (failure === 'network') fetcher.mockRejectedValue(new TypeError('offline')); else fetcher.mockResolvedValue(failure === 'bad-json' ? new Response('bad', { status: 201 }) : new Response('', { status: failure }));
        await expect(api.b5CreateFileMaterial(course, lesson, 'PDF', new File(['x'], 'x.pdf'))).rejects.toMatchObject({ kind: 'unknown-outcome' }); expect(fetcher).toHaveBeenCalledTimes(1);
    });
    it('suppresses an upload after cancellation and a superseded generation', async () => { const controller = new AbortController(); controller.abort(); await expect(api.b5CreateFileMaterial(course, lesson, 'PDF', new File(['x'], 'x.pdf'), { signal: controller.signal })).rejects.toMatchObject({ kind: 'cancelled' }); expect(fetcher).not.toHaveBeenCalled(); });
    it('rejects a Lesson response for a different identity', async () => { fetcher.mockResolvedValue(json({ ...lessonBody, id: chapter })); await expect(api.updateCourseLesson(course, lesson, { title: 'Lesson', contentMarkdown: null })).rejects.toMatchObject({ kind: 'protocol' }); });
});

describe('CT37 canonical YouTube admission', () => {
    it('admits only the exact privacy-enhanced embed serialization with outer ASCII spaces', () => { expect(youtubeEmbedAddress(' https://www.youtube-nocookie.com/embed/a_B-1 ')).toBe('https://www.youtube-nocookie.com/embed/a_B-1'); });
    it.each(['https://youtube.com/watch?v=a', 'https://www.youtube-nocookie.com:443/embed/a', 'https://www.youtube-nocookie.com/embed/a?x=1', 'https://www.youtube-nocookie.com/embed/a#x', 'https://www.youtube-nocookie.com/embed/%61', 'https://www.youtube-nocookie.com.evil.test/embed/a', 'https://evil@www.youtube-nocookie.com/embed/a', 'https://www.youtube-nocookie.com/embed/a/b', 'HTTPS://www.youtube-nocookie.com/embed/a', '\thttps://www.youtube-nocookie.com/embed/a', 'https://www.youtube-nocookie.com/embed/'])('rejects %s', (source) => expect(youtubeEmbedAddress(source)).toBeNull());
});
