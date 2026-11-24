import { request } from '../../../shared/api/httpClient';
import { ApiError, isRecord, protocolError } from '../../../shared/types/apiTypes';
import type {
    CourseMembership,
    CourseProgress,
    CourseProgressPage,
    CourseProgressSummary,
    CourseResume,
    LearnerLesson,
    LearnerResume,
    LearningCourseStructure,
    LessonProgressEntry,
    MembershipFilter,
    MembershipPage,
    MembershipStatus,
    RelationalMaterial,
} from '../types/learningTypes';

export interface MembershipOptions { signal?: AbortSignal; isCurrent?: () => boolean }
const uuid = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
const status = (value: unknown): value is MembershipStatus => ['active', 'completed', 'left'].includes(value as string);
const integer = (value: unknown, minimum: number): value is number => Number.isSafeInteger(value) && (value as number) >= minimum;

function object(value: unknown, keys: string[]): Record<string, unknown> {
    if (!isRecord(value) || Object.keys(value).length !== keys.length || !keys.every((key) => Object.hasOwn(value, key))) {
        throw protocolError();
    }
    return value;
}

function membership(value: unknown): CourseMembership {
    if (!isRecord(value) || Object.keys(value).length !== 2 || typeof value.courseId !== 'string' || !uuid.test(value.courseId)
        || value.status !== 'none' && !status(value.status)) throw protocolError();
    return { courseId: value.courseId, status: value.status };
}
function page(value: unknown): MembershipPage {
    if (!isRecord(value) || Object.keys(value).length !== 5 || !Array.isArray(value.content)
        || !Number.isSafeInteger(value.page) || (value.page as number) < 0
        || !Number.isSafeInteger(value.size) || (value.size as number) < 1 || (value.size as number) > 100
        || !Number.isSafeInteger(value.totalElements) || (value.totalElements as number) < 0
        || !Number.isSafeInteger(value.totalPages) || (value.totalPages as number) < 0) throw protocolError();
    const content = value.content.map((item) => {
        if (!isRecord(item) || Object.keys(item).length !== 6 || !status(item.status)
            || typeof item.id !== 'string' || !uuid.test(item.id)
            || typeof item.title !== 'string' || typeof item.description !== 'string'
            || typeof item.instructorId !== 'string' || !uuid.test(item.instructorId)
            || item.instructorDisplayName !== null && typeof item.instructorDisplayName !== 'string') throw protocolError();
        return {
            id: item.id, title: item.title, description: item.description, instructorId: item.instructorId,
            instructorDisplayName: item.instructorDisplayName as string | null, status: item.status
        };
    });
    return { content, page: value.page as number, size: value.size as number, totalElements: value.totalElements as number, totalPages: value.totalPages as number };
}
function operation(courseId: string, method: 'GET' | 'PUT' | 'DELETE', options: MembershipOptions) {
    return request(`/courses/${encodeURIComponent(courseId)}/membership`, {
        ...options, method, auth: 'authenticated', expectedStatus: 200,
        decode: (value) => { const result = membership(value); if (!uuid.test(courseId) || result.courseId.toLowerCase() !== courseId.toLowerCase()) throw protocolError(); return result; }
    });
}
export const getCourseMembership = (courseId: string, options: MembershipOptions = {}) => operation(courseId, 'GET', options);
export const enrollInCourse = (courseId: string, options: MembershipOptions = {}) => operation(courseId, 'PUT', options);
export const leaveCourse = (courseId: string, options: MembershipOptions = {}) => operation(courseId, 'DELETE', options);
export function getMyEnrolledCourses(pageIndex = 0, filter: MembershipFilter = 'joined', options: MembershipOptions = {}) {
    return request('/me/courses', {
        ...options, method: 'GET', auth: 'authenticated', expectedStatus: 200, decode: page,
        query: new URLSearchParams({ page: String(pageIndex), size: '20', status: filter })
    });
}

// --- Approved CT22 & CT21 Operations (FE08) ---

function decodeLessonProgressEntry(value: unknown): LessonProgressEntry {
    const row = object(value, ['lessonId', 'completed', 'masterySkipped']);
    if (
        typeof row.lessonId !== 'string' || !uuid.test(row.lessonId) ||
        typeof row.completed !== 'boolean' || typeof row.masterySkipped !== 'boolean' ||
        (row.masterySkipped && !row.completed)
    ) {
        throw protocolError();
    }
    return { lessonId: row.lessonId, completed: row.completed, masterySkipped: row.masterySkipped };
}

function decodeCourseProgressSummary(value: unknown): CourseProgressSummary {
    const row = object(value, ['courseId', 'title', 'membershipStatus', 'completedLessons', 'totalLessons', 'progressPercentage', 'isComplete', 'nextLessonId']);
    if (
        typeof row.courseId !== 'string' || !uuid.test(row.courseId) ||
        typeof row.title !== 'string' || row.title.length < 1 ||
        (row.membershipStatus !== 'active' && row.membershipStatus !== 'completed') ||
        !integer(row.completedLessons, 0) || !integer(row.totalLessons, 0) ||
        !integer(row.progressPercentage, 0) || row.progressPercentage > 100 ||
        typeof row.isComplete !== 'boolean' ||
        (row.nextLessonId !== null && (typeof row.nextLessonId !== 'string' || !uuid.test(row.nextLessonId)))
    ) {
        throw protocolError();
    }
    return {
        courseId: row.courseId,
        title: row.title,
        membershipStatus: row.membershipStatus,
        completedLessons: row.completedLessons,
        totalLessons: row.totalLessons,
        progressPercentage: row.progressPercentage,
        isComplete: row.isComplete,
        nextLessonId: row.nextLessonId as string | null,
    };
}

function decodeCourseProgress(value: unknown): CourseProgress {
    const row = object(value, ['courseId', 'title', 'membershipStatus', 'completedLessons', 'totalLessons', 'progressPercentage', 'isComplete', 'nextLessonId', 'lessons']);
    if (!Array.isArray(row.lessons)) throw protocolError();
    const base = decodeCourseProgressSummary({
        courseId: row.courseId,
        title: row.title,
        membershipStatus: row.membershipStatus,
        completedLessons: row.completedLessons,
        totalLessons: row.totalLessons,
        progressPercentage: row.progressPercentage,
        isComplete: row.isComplete,
        nextLessonId: row.nextLessonId,
    });
    return { ...base, lessons: row.lessons.map(decodeLessonProgressEntry) };
}

function decodeCourseProgressPage(value: unknown): CourseProgressPage {
    const row = object(value, ['content', 'page', 'size', 'totalElements', 'totalPages']);
    if (!Array.isArray(row.content) || !integer(row.page, 0) || !integer(row.size, 1) || row.size > 100 || !integer(row.totalElements, 0) || !integer(row.totalPages, 0)) {
        throw protocolError();
    }
    return {
        content: row.content.map(decodeCourseProgressSummary),
        page: row.page,
        size: row.size,
        totalElements: row.totalElements,
        totalPages: row.totalPages,
    };
}

function decodeCourseResume(value: unknown): CourseResume {
    const row = object(value, ['courseId', 'resumeLessonId', 'nextLessonId']);
    if (
        typeof row.courseId !== 'string' || !uuid.test(row.courseId) ||
        (row.resumeLessonId !== null && (typeof row.resumeLessonId !== 'string' || !uuid.test(row.resumeLessonId))) ||
        (row.nextLessonId !== null && (typeof row.nextLessonId !== 'string' || !uuid.test(row.nextLessonId)))
    ) {
        throw protocolError();
    }
    return {
        courseId: row.courseId,
        resumeLessonId: row.resumeLessonId as string | null,
        nextLessonId: row.nextLessonId as string | null,
    };
}

function decodeLearnerResume(value: unknown): LearnerResume {
    const row = object(value, ['position']);
    if (row.position === null) return { position: null };
    const pos = object(row.position, ['courseId', 'lessonId', 'lastVisitedAt']);
    if (
        typeof pos.courseId !== 'string' || !uuid.test(pos.courseId) ||
        typeof pos.lessonId !== 'string' || !uuid.test(pos.lessonId) ||
        typeof pos.lastVisitedAt !== 'string'
    ) {
        throw protocolError();
    }
    return { position: { courseId: pos.courseId, lessonId: pos.lessonId, lastVisitedAt: pos.lastVisitedAt } };
}

function decodeMaterial(value: unknown): RelationalMaterial {
    if (!isRecord(value) || typeof value.materialType !== 'string') throw protocolError();
    if (value.materialType === 'text') {
        const row = object(value, ['id', 'title', 'publicationStatus', 'materialType', 'textContent']);
        if (typeof row.id !== 'string' || !uuid.test(row.id) || typeof row.title !== 'string' || (row.publicationStatus !== 'hidden' && row.publicationStatus !== 'published') || typeof row.textContent !== 'string') {
            throw protocolError();
        }
        return { id: row.id, title: row.title, publicationStatus: row.publicationStatus, materialType: 'text', textContent: row.textContent };
    }
    if (value.materialType === 'external_video') {
        const row = object(value, ['id', 'title', 'publicationStatus', 'materialType', 'externalVideoUrl']);
        if (typeof row.id !== 'string' || !uuid.test(row.id) || typeof row.title !== 'string' || (row.publicationStatus !== 'hidden' && row.publicationStatus !== 'published') || typeof row.externalVideoUrl !== 'string') {
            throw protocolError();
        }
        return { id: row.id, title: row.title, publicationStatus: row.publicationStatus, materialType: 'external_video', externalVideoUrl: row.externalVideoUrl };
    }
    if (value.materialType === 'file') {
        const row = object(value, ['id', 'title', 'publicationStatus', 'materialType']);
        if (typeof row.id !== 'string' || !uuid.test(row.id) || typeof row.title !== 'string' || (row.publicationStatus !== 'hidden' && row.publicationStatus !== 'published')) {
            throw protocolError();
        }
        return { id: row.id, title: row.title, publicationStatus: row.publicationStatus, materialType: 'file' };
    }
    throw protocolError();
}

function decodeLearnerLesson(value: unknown): LearnerLesson {
    const row = object(value, ['id', 'courseId', 'chapterId', 'title', 'contentMarkdown', 'materials']);
    if (
        typeof row.id !== 'string' || !uuid.test(row.id) ||
        typeof row.courseId !== 'string' || !uuid.test(row.courseId) ||
        typeof row.chapterId !== 'string' || !uuid.test(row.chapterId) ||
        typeof row.title !== 'string' ||
        (row.contentMarkdown !== null && typeof row.contentMarkdown !== 'string') ||
        !Array.isArray(row.materials)
    ) {
        throw protocolError();
    }
    return {
        id: row.id,
        courseId: row.courseId,
        chapterId: row.chapterId,
        title: row.title,
        contentMarkdown: row.contentMarkdown as string | null,
        materials: row.materials.map(decodeMaterial),
    };
}

function decodeCourseStructure(value: unknown): LearningCourseStructure {
    const row = object(value, ['id', 'title', 'description', 'instructorId', 'instructorDisplayName', 'prerequisiteDescription', 'syllabus']);
    if (
        typeof row.id !== 'string' || !uuid.test(row.id) ||
        typeof row.title !== 'string' || typeof row.description !== 'string' ||
        typeof row.instructorId !== 'string' || !uuid.test(row.instructorId) ||
        (row.instructorDisplayName !== null && typeof row.instructorDisplayName !== 'string') ||
        (row.prerequisiteDescription !== null && typeof row.prerequisiteDescription !== 'string') ||
        !Array.isArray(row.syllabus)
    ) {
        throw protocolError();
    }
    return {
        id: row.id,
        title: row.title,
        description: row.description,
        instructorId: row.instructorId,
        instructorDisplayName: row.instructorDisplayName as string | null,
        prerequisiteDescription: row.prerequisiteDescription as string | null,
        syllabus: row.syllabus.map((ch) => {
            const chRow = object(ch, ['id', 'title', 'position', 'lessons']);
            if (typeof chRow.id !== 'string' || !uuid.test(chRow.id) || typeof chRow.title !== 'string' || !integer(chRow.position, 1) || !Array.isArray(chRow.lessons)) {
                throw protocolError();
            }
            return {
                id: chRow.id,
                title: chRow.title,
                position: chRow.position,
                lessons: chRow.lessons.map((l) => {
                    const lRow = object(l, ['id', 'title', 'position']);
                    if (typeof lRow.id !== 'string' || !uuid.test(lRow.id) || typeof lRow.title !== 'string' || !integer(lRow.position, 1)) {
                        throw protocolError();
                    }
                    return { id: lRow.id, title: lRow.title, position: lRow.position };
                }),
            };
        }),
    };
}

export function getCourseProgress(courseId: string, options: MembershipOptions = {}): Promise<CourseProgress> {
    return request(`/courses/${encodeURIComponent(courseId)}/progress`, {
        ...options,
        method: 'GET',
        auth: 'authenticated',
        expectedStatus: 200,
        decode: decodeCourseProgress,
    });
}

export function completeCourseLesson(courseId: string, lessonId: string, options: MembershipOptions = {}): Promise<CourseProgress> {
    return request(`/courses/${encodeURIComponent(courseId)}/lessons/${encodeURIComponent(lessonId)}/completion`, {
        ...options,
        method: 'PUT',
        auth: 'authenticated',
        expectedStatus: 200,
        decode: decodeCourseProgress,
        body: { completed: true },
    });
}

export function listMyCourseProgress(pageIndex = 0, size = 20, options: MembershipOptions = {}): Promise<CourseProgressPage> {
    if (!integer(pageIndex, 0) || !integer(size, 1) || size > 100) {
        return Promise.reject(new ApiError('validation', 'Please check the requested page.'));
    }
    return request('/me/course-progress', {
        ...options,
        method: 'GET',
        auth: 'authenticated',
        expectedStatus: 200,
        decode: decodeCourseProgressPage,
        query: new URLSearchParams({ page: String(pageIndex), size: String(size) }),
    });
}

export function getCourseResume(courseId: string, options: MembershipOptions = {}): Promise<CourseResume> {
    return request(`/courses/${encodeURIComponent(courseId)}/resume`, {
        ...options,
        method: 'GET',
        auth: 'authenticated',
        expectedStatus: 200,
        decode: decodeCourseResume,
    });
}

export function recordCourseVisit(courseId: string, lessonId: string, options: MembershipOptions = {}): Promise<CourseResume> {
    return request(`/courses/${encodeURIComponent(courseId)}/resume`, {
        ...options,
        method: 'PUT',
        auth: 'authenticated',
        expectedStatus: 200,
        decode: decodeCourseResume,
        body: { lessonId },
    });
}

export function getMyResume(options: MembershipOptions = {}): Promise<LearnerResume> {
    return request('/me/resume', {
        ...options,
        method: 'GET',
        auth: 'authenticated',
        expectedStatus: 200,
        decode: decodeLearnerResume,
    });
}

export function getLearnerLesson(courseId: string, lessonId: string, options: MembershipOptions = {}): Promise<LearnerLesson> {
    return request(`/courses/${encodeURIComponent(courseId)}/lessons/${encodeURIComponent(lessonId)}`, {
        ...options,
        method: 'GET',
        auth: 'authenticated',
        expectedStatus: 200,
        decode: decodeLearnerLesson,
    });
}

export function getCourseSyllabusForLearning(courseId: string, options: MembershipOptions = {}): Promise<LearningCourseStructure> {
    return request(`/courses/${encodeURIComponent(courseId)}`, {
        ...options,
        method: 'GET',
        auth: 'public',
        expectedStatus: 200,
        decode: decodeCourseStructure,
    });
}