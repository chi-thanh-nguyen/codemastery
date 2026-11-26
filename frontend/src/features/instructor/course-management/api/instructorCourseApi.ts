import { request } from '../../../../shared/api/httpClient';
import { ApiError, isRecord, protocolError } from '../../../../shared/types/apiTypes';
import { DEFAULT_PAGE_SIZE, MAX_PAGE_SIZE } from './assessmentAuthoringApi';
import type { AuthoringRequestOptions, B3Page } from './assessmentAuthoringApi';

/*
 * CT21.authoring Instructor course and structure client (ADR006 §5; OpenAPI OwnedCourse*,
 * OwnedChapter, OwnedLesson; implemented on backend by CO06 #172). Every response is decoded
 * strictly against the approved contract shapes.
 */
export type CoursePublicationStatus = 'hidden' | 'published';
export type OwnedMaterialType = 'text' | 'external_video' | 'file';

export interface OwnedCourseSummary {
    id: string;
    title: string;
    description: string;
    publicationStatus: CoursePublicationStatus;
}
export type OwnedCourseSummaryPage = B3Page<OwnedCourseSummary>;

export interface OwnedMaterial {
    id: string;
    title: string;
    publicationStatus: CoursePublicationStatus;
    materialType: OwnedMaterialType;
    textContent?: string;
    externalVideoUrl?: string;
}

export interface OwnedLesson {
    id: string;
    chapterId: string;
    title: string;
    contentMarkdown: string | null;
    position: number;
    publicationStatus: CoursePublicationStatus;
    materials: OwnedMaterial[];
}

export interface OwnedChapter {
    id: string;
    title: string;
    position: number;
    lessons: OwnedLesson[];
}

export interface OwnedCourse {
    id: string;
    title: string;
    description: string;
    publicationStatus: CoursePublicationStatus;
    prerequisiteDescription: string | null;
    chapters: OwnedChapter[];
}

export interface OwnedCourseWrite {
    title: string;
    description: string;
    prerequisiteDescription: string | null;
}

export interface OwnedLessonWrite {
    title: string;
    contentMarkdown: string | null;
}

const uuid = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
const publicationStatuses: readonly CoursePublicationStatus[] = ['hidden', 'published'];
const materialTypes: readonly OwnedMaterialType[] = ['text', 'external_video', 'file'];

const id = (value: unknown): value is string => typeof value === 'string' && uuid.test(value);
const text = (value: unknown): value is string => typeof value === 'string' && /\S/.test(value);
const nullableText = (value: unknown): value is string | null => value === null || text(value);
const integer = (value: unknown, minimum: number, maximum = Number.MAX_SAFE_INTEGER): value is number =>
    Number.isSafeInteger(value) && (value as number) >= minimum && (value as number) <= maximum;
const same = (left: string, right: string) => left.toLowerCase() === right.toLowerCase();

function exact(value: unknown, keys: readonly string[]): Record<string, unknown> {
    if (!isRecord(value) || Object.keys(value).length !== keys.length || !keys.every((key) => Object.hasOwn(value, key))) {
        throw protocolError();
    }
    return value;
}

export function decodeMaterial(value: unknown): OwnedMaterial {
    if (!isRecord(value)) throw protocolError();
    const type = value.materialType;
    if (!materialTypes.includes(type as OwnedMaterialType)) throw protocolError();
    if (type === 'text') {
        const row = exact(value, ['id', 'title', 'publicationStatus', 'materialType', 'textContent']);
        if (!id(row.id) || !text(row.title) || !publicationStatuses.includes(row.publicationStatus as CoursePublicationStatus) || !text(row.textContent)) {
            throw protocolError();
        }
        return {
            id: row.id,
            title: row.title,
            publicationStatus: row.publicationStatus as CoursePublicationStatus,
            materialType: 'text',
            textContent: row.textContent,
        };
    }
    if (type === 'external_video') {
        const row = exact(value, ['id', 'title', 'publicationStatus', 'materialType', 'externalVideoUrl']);
        if (!id(row.id) || !text(row.title) || !publicationStatuses.includes(row.publicationStatus as CoursePublicationStatus) || !text(row.externalVideoUrl)) {
            throw protocolError();
        }
        return {
            id: row.id,
            title: row.title,
            publicationStatus: row.publicationStatus as CoursePublicationStatus,
            materialType: 'external_video',
            externalVideoUrl: row.externalVideoUrl,
        };
    }
    const row = exact(value, ['id', 'title', 'publicationStatus', 'materialType']);
    if (!id(row.id) || !text(row.title) || !publicationStatuses.includes(row.publicationStatus as CoursePublicationStatus)) {
        throw protocolError();
    }
    return {
        id: row.id,
        title: row.title,
        publicationStatus: row.publicationStatus as CoursePublicationStatus,
        materialType: 'file',
    };
}

function decodeLesson(value: unknown, expectedChapterId?: string): OwnedLesson {
    const row = exact(value, ['id', 'chapterId', 'title', 'contentMarkdown', 'position', 'publicationStatus', 'materials']);
    if (!id(row.id) || !id(row.chapterId) || (expectedChapterId !== undefined && !same(row.chapterId, expectedChapterId)) ||
        !text(row.title) || !nullableText(row.contentMarkdown) || !integer(row.position, 1) ||
        !publicationStatuses.includes(row.publicationStatus as CoursePublicationStatus) || !Array.isArray(row.materials)) {
        throw protocolError();
    }
    return {
        id: row.id,
        chapterId: row.chapterId,
        title: row.title,
        contentMarkdown: row.contentMarkdown,
        position: row.position,
        publicationStatus: row.publicationStatus as CoursePublicationStatus,
        materials: row.materials.map(decodeMaterial),
    };
}

function decodeChapter(value: unknown): OwnedChapter {
    const row = exact(value, ['id', 'title', 'position', 'lessons']);
    if (!id(row.id) || !text(row.title) || !integer(row.position, 1) || !Array.isArray(row.lessons)) {
        throw protocolError();
    }
    const lessons = row.lessons.map((lesson, index) => {
        const decoded = decodeLesson(lesson, row.id as string);
        if (decoded.position !== index + 1) throw protocolError();
        return decoded;
    });
    return { id: row.id, title: row.title, position: row.position, lessons };
}

function decodeOwnedCourse(value: unknown, expectedCourseId?: string): OwnedCourse {
    const row = exact(value, ['id', 'title', 'description', 'publicationStatus', 'prerequisiteDescription', 'chapters']);
    if (!id(row.id) || (expectedCourseId !== undefined && !same(row.id, expectedCourseId)) ||
        !text(row.title) || !text(row.description) ||
        !publicationStatuses.includes(row.publicationStatus as CoursePublicationStatus) ||
        !nullableText(row.prerequisiteDescription) || !Array.isArray(row.chapters)) {
        throw protocolError();
    }
    const chapters = row.chapters.map((chapter, index) => {
        const decoded = decodeChapter(chapter);
        if (decoded.position !== index + 1) throw protocolError();
        return decoded;
    });
    return {
        id: row.id,
        title: row.title,
        description: row.description,
        publicationStatus: row.publicationStatus as CoursePublicationStatus,
        prerequisiteDescription: row.prerequisiteDescription,
        chapters,
    };
}

function decodeOwnedCourseSummary(value: unknown): OwnedCourseSummary {
    const row = exact(value, ['id', 'title', 'description', 'publicationStatus']);
    if (!id(row.id) || !text(row.title) || !text(row.description) ||
        !publicationStatuses.includes(row.publicationStatus as CoursePublicationStatus)) {
        throw protocolError();
    }
    return {
        id: row.id,
        title: row.title,
        description: row.description,
        publicationStatus: row.publicationStatus as CoursePublicationStatus,
    };
}

function decodePage<T>(item: (value: unknown) => T, page: number, size: number) {
    return (value: unknown): B3Page<T> => {
        const row = exact(value, ['content', 'page', 'size', 'totalElements', 'totalPages']);
        if (!Array.isArray(row.content) || row.page !== page || row.size !== size || !integer(row.totalElements, 0) ||
            row.totalPages !== Math.ceil(row.totalElements / size) || row.content.length > size ||
            row.content.length > row.totalElements || (row.content.length > 0 && page * size >= row.totalElements)) {
            throw protocolError();
        }
        return {
            content: row.content.map(item),
            page,
            size,
            totalElements: row.totalElements,
            totalPages: row.totalPages as number,
        };
    };
}

function decodeSiblingOrder(expectedIds: readonly string[]) {
    return (value: unknown): string[] => {
        const row = exact(value, ['orderedIds']);
        if (!Array.isArray(row.orderedIds) || !row.orderedIds.every(id) ||
            (row.orderedIds.length !== expectedIds.length || !row.orderedIds.every((value, index) => same(value as string, expectedIds[index])) || new Set(row.orderedIds.map((value) => (value as string).toLowerCase())).size !== row.orderedIds.length)) {
            throw protocolError();
        }
        return [...(row.orderedIds as string[])];
    };
}

const courseBase = (courseId: string) => `/instructor/courses/${encodeURIComponent(courseId)}`;
const invalidPage = () => Promise.reject(new ApiError('validation', 'Please check the requested page.'));
const pageValid = (page: number, size: number) => integer(page, 0) && integer(size, 1, MAX_PAGE_SIZE);

/** `listOwnedCourses`: Instructor's owned courses (hidden and published). */
export function listOwnedCourses(page = 0, size = DEFAULT_PAGE_SIZE, options: AuthoringRequestOptions = {}): Promise<OwnedCourseSummaryPage> {
    if (!pageValid(page, size)) return invalidPage();
    return request('/instructor/courses', {
        ...options,
        method: 'GET',
        auth: 'authenticated',
        expectedStatus: 200,
        query: new URLSearchParams({ page: String(page), size: String(size) }),
        decode: decodePage(decodeOwnedCourseSummary, page, size),
    });
}

/** `createOwnedCourse`: creates a new hidden course owned by the signed-in Instructor. */
export function createOwnedCourse(payload: OwnedCourseWrite, options: AuthoringRequestOptions = {}): Promise<OwnedCourse> {
    return request('/instructor/courses', {
        ...options,
        method: 'POST',
        auth: 'authenticated',
        expectedStatus: 201,
        body: payload,
        decode: (value) => decodeOwnedCourse(value),
    });
}

/** `getOwnedCourse`: coherent owner tree in canonical order, hidden chapters/lessons included. */
export function getOwnedCourse(courseId: string, options: AuthoringRequestOptions = {}): Promise<OwnedCourse> {
    return request(courseBase(courseId), {
        ...options,
        method: 'GET',
        auth: 'authenticated',
        expectedStatus: 200,
        decode: (value) => decodeOwnedCourse(value, courseId),
    });
}

/** `updateOwnedCourse`: full metadata replacement. */
export function updateOwnedCourse(courseId: string, payload: OwnedCourseWrite, options: AuthoringRequestOptions = {}): Promise<OwnedCourse> {
    return request(courseBase(courseId), {
        ...options,
        method: 'PUT',
        auth: 'authenticated',
        expectedStatus: 200,
        body: payload,
        decode: (value) => decodeOwnedCourse(value, courseId),
    });
}

/** `setOwnedCoursePublication`: explicit `hidden` or `published` course publication status. */
export function setOwnedCoursePublication(courseId: string, publicationStatus: CoursePublicationStatus, options: AuthoringRequestOptions = {}): Promise<OwnedCourse> {
    return request(`${courseBase(courseId)}/publication`, {
        ...options,
        method: 'PUT',
        auth: 'authenticated',
        expectedStatus: 200,
        body: { publicationStatus },
        decode: (value) => {
            const decoded = decodeOwnedCourse(value, courseId);
            if (decoded.publicationStatus !== publicationStatus) throw protocolError();
            return decoded;
        },
    });
}

/** `createCourseChapter`: appends a chapter at the end of the course. */
export function createCourseChapter(courseId: string, title: string, options: AuthoringRequestOptions = {}): Promise<OwnedChapter> {
    return request(`${courseBase(courseId)}/chapters`, {
        ...options,
        method: 'POST',
        auth: 'authenticated',
        expectedStatus: 201,
        body: { title },
        decode: decodeChapter,
    });
}

/** `updateCourseChapter`: updates a chapter's title. */
export function updateCourseChapter(courseId: string, chapterId: string, title: string, options: AuthoringRequestOptions = {}): Promise<OwnedChapter> {
    return request(`${courseBase(courseId)}/chapters/${encodeURIComponent(chapterId)}`, {
        ...options,
        method: 'PUT',
        auth: 'authenticated',
        expectedStatus: 200,
        body: { title },
        decode: (value) => {
            const decoded = decodeChapter(value);
            if (!same(decoded.id, chapterId)) throw protocolError();
            return decoded;
        },
    });
}

/** `orderCourseChapters`: replaces the complete sibling order of chapters. */
export function orderCourseChapters(courseId: string, orderedIds: readonly string[], options: AuthoringRequestOptions = {}): Promise<string[]> {
    return request(`${courseBase(courseId)}/chapters/order`, {
        ...options,
        method: 'PUT',
        auth: 'authenticated',
        expectedStatus: 200,
        body: { orderedIds: [...orderedIds] },
        decode: decodeSiblingOrder(orderedIds),
    });
}

/** `createCourseLesson`: appends a new hidden lesson at the end of a chapter. */
export function createCourseLesson(courseId: string, chapterId: string, payload: OwnedLessonWrite, options: AuthoringRequestOptions = {}): Promise<OwnedLesson> {
    return request(`${courseBase(courseId)}/chapters/${encodeURIComponent(chapterId)}/lessons`, {
        ...options,
        method: 'POST',
        auth: 'authenticated',
        expectedStatus: 201,
        body: payload,
        decode: (value) => decodeLesson(value, chapterId),
    });
}

/** `orderChapterLessons`: replaces the complete sibling order of lessons within a chapter. */
export function orderChapterLessons(courseId: string, chapterId: string, orderedIds: readonly string[], options: AuthoringRequestOptions = {}): Promise<string[]> {
    return request(`${courseBase(courseId)}/chapters/${encodeURIComponent(chapterId)}/lessons/order`, {
        ...options,
        method: 'PUT',
        auth: 'authenticated',
        expectedStatus: 200,
        body: { orderedIds: [...orderedIds] },
        decode: decodeSiblingOrder(orderedIds),
    });
}

/** `setCourseLessonPublication`: explicit `hidden` or `published` lesson publication status. */
export function setCourseLessonPublication(courseId: string, lessonId: string, publicationStatus: CoursePublicationStatus, options: AuthoringRequestOptions = {}): Promise<OwnedLesson> {
    return request(`${courseBase(courseId)}/lessons/${encodeURIComponent(lessonId)}/publication`, {
        ...options,
        method: 'PUT',
        auth: 'authenticated',
        expectedStatus: 200,
        body: { publicationStatus },
        decode: (value) => {
            const decoded = decodeLesson(value);
            if (!same(decoded.id, lessonId) || decoded.publicationStatus !== publicationStatus) throw protocolError();
            return decoded;
        },
    });
}

/** CO09 backend contracts; production FE14 material editor integration is a separate consumer. */
export type RelationalMaterialWrite =
    | { title: string; materialType: 'text'; textContent: string }
    | { title: string; materialType: 'external_video'; externalVideoUrl: string };
export interface FileMaterialTitleWrite { title: string }
export interface MaterialPublicationWrite { publicationStatus: CoursePublicationStatus }


const lessonBase = (courseId: string, lessonId: string) => `${courseBase(courseId)}/lessons/${encodeURIComponent(lessonId)}`;
const materialBase = (courseId: string, lessonId: string, materialId?: string, file = false) =>
    `${lessonBase(courseId, lessonId)}/${file ? 'file-materials' : 'materials'}${materialId ? `/${encodeURIComponent(materialId)}` : ''}`;

export function updateCourseLesson(courseId: string, lessonId: string, payload: OwnedLessonWrite, options: AuthoringRequestOptions = {}): Promise<OwnedLesson> {
    return request(lessonBase(courseId, lessonId), { ...options, method: 'PUT', auth: 'authenticated', expectedStatus: 200, body: payload,
        decode: (value) => { const lesson = decodeLesson(value); if (!same(lesson.id, lessonId)) throw protocolError(); return lesson; } });
}

function materialDecoder(type: OwnedMaterialType, expectedId?: string, publication?: CoursePublicationStatus) {
    return (value: unknown): OwnedMaterial => {
        const material = decodeMaterial(value);
        if (material.materialType !== type || expectedId !== undefined && !same(material.id, expectedId) ||
            publication !== undefined && material.publicationStatus !== publication) throw protocolError();
        return material;
    };
}
export function createRelationalMaterial(courseId: string, lessonId: string, payload: RelationalMaterialWrite, options: AuthoringRequestOptions = {}): Promise<OwnedMaterial> {
    return request(materialBase(courseId, lessonId), { ...options, method: 'POST', auth: 'authenticated', expectedStatus: 201,
        body: payload, decode: materialDecoder(payload.materialType, undefined, 'hidden') });
}
export function updateRelationalMaterial(courseId: string, lessonId: string, materialId: string, payload: RelationalMaterialWrite, options: AuthoringRequestOptions = {}): Promise<OwnedMaterial> {
    return request(materialBase(courseId, lessonId, materialId), { ...options, method: 'PUT', auth: 'authenticated', expectedStatus: 200,
        body: payload, decode: materialDecoder(payload.materialType, materialId) });
}
export function setRelationalMaterialPublication(courseId: string, lessonId: string, material: OwnedMaterial, publicationStatus: CoursePublicationStatus, options: AuthoringRequestOptions = {}): Promise<OwnedMaterial> {
    return request(`${materialBase(courseId, lessonId, material.id)}/publication`, { ...options, method: 'PUT', auth: 'authenticated', expectedStatus: 200,
        body: { publicationStatus }, decode: materialDecoder(material.materialType, material.id, publicationStatus) });
}
export function b5CreateFileMaterial(courseId: string, lessonId: string, title: string, file: File, options: AuthoringRequestOptions = {}): Promise<OwnedMaterial> {
    const body = new FormData(); body.append('title', title); body.append('file', file);
    return request(materialBase(courseId, lessonId, undefined, true), { ...options, method: 'POST', auth: 'authenticated', expectedStatus: 201,
        bodyFormat: 'multipart', body, decode: materialDecoder('file', undefined, 'hidden') });
}
export function b5UpdateFileMaterialTitle(courseId: string, lessonId: string, materialId: string, title: string, options: AuthoringRequestOptions = {}): Promise<OwnedMaterial> {
    return request(materialBase(courseId, lessonId, materialId, true), { ...options, method: 'PUT', auth: 'authenticated', expectedStatus: 200,
        body: { title }, decode: materialDecoder('file', materialId) });
}
export function b5ReplaceFileMaterialFile(courseId: string, lessonId: string, materialId: string, file: File, options: AuthoringRequestOptions = {}): Promise<OwnedMaterial> {
    const body = new FormData(); body.append('file', file);
    return request(`${materialBase(courseId, lessonId, materialId, true)}/file`, { ...options, method: 'PUT', auth: 'authenticated', expectedStatus: 200,
        bodyFormat: 'multipart', body, decode: materialDecoder('file', materialId) });
}
export function b5SetFileMaterialPublication(courseId: string, lessonId: string, materialId: string, publicationStatus: CoursePublicationStatus, options: AuthoringRequestOptions = {}): Promise<OwnedMaterial> {
    return request(`${materialBase(courseId, lessonId, materialId, true)}/publication`, { ...options, method: 'PUT', auth: 'authenticated', expectedStatus: 200,
        body: { publicationStatus }, decode: materialDecoder('file', materialId, publicationStatus) });
}
export function b5DownloadMaterialFile(courseId: string, lessonId: string, materialId: string, options: AuthoringRequestOptions = {}) {
    return request(`/courses/${encodeURIComponent(courseId)}/lessons/${encodeURIComponent(lessonId)}/materials/${encodeURIComponent(materialId)}/file`,
        { ...options, method: 'GET', auth: 'authenticated', expectedStatus: 200, responseMode: 'binary' });
}
