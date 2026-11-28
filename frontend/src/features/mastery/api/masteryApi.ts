import { request } from '../../../shared/api/httpClient';
import { isRecord, protocolError } from '../../../shared/types/apiTypes';
import type { MasteryState } from '../../../shared/components/StatusPill';
import type {
    B6AdaptiveGuidance,
    B6CurrentRecommendation,
    B6LessonGuidance,
    B6MasteryMap,
    B6MasterySkill,
    MasteryLessonProgressStatus,
    MasteryLessonTitleEntry,
} from '../types/masteryTypes';

export interface MasteryRequestOptions {
    signal?: AbortSignal;
    isCurrent?: () => boolean;
}

const uuid = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
const masteryStates = new Set<MasteryState>(['unknown', 'learning', 'mastered', 'weak']);

function object(value: unknown, keys: string[]): Record<string, unknown> {
    if (
        !isRecord(value) ||
        Object.keys(value).length !== keys.length ||
        !keys.every((key) => Object.hasOwn(value, key))
    ) {
        throw protocolError();
    }
    return value;
}

function decodeUuidArray(value: unknown): string[] {
    if (!Array.isArray(value) || !value.every((id) => typeof id === 'string' && uuid.test(id))) {
        throw protocolError();
    }
    return value as string[];
}

function decodeMasterySkill(value: unknown): B6MasterySkill {
    const row = object(value, [
        'id',
        'name',
        'description',
        'state',
        'prerequisiteSkillIds',
        'relatedLessonIds',
        'latestScore',
        'latestAssessedAt',
    ]);
    if (
        typeof row.id !== 'string' ||
        !uuid.test(row.id) ||
        typeof row.name !== 'string' ||
        row.name.length < 1 ||
        (row.description !== null && typeof row.description !== 'string') ||
        typeof row.state !== 'string' ||
        !masteryStates.has(row.state as MasteryState) ||
        (row.latestScore !== null &&
            (typeof row.latestScore !== 'number' || row.latestScore < 0 || row.latestScore > 100)) ||
        (row.latestAssessedAt !== null && typeof row.latestAssessedAt !== 'string')
    ) {
        throw protocolError();
    }
    return {
        id: row.id,
        name: row.name,
        description: row.description as string | null,
        state: row.state as MasteryState,
        prerequisiteSkillIds: decodeUuidArray(row.prerequisiteSkillIds),
        relatedLessonIds: decodeUuidArray(row.relatedLessonIds),
        latestScore: row.latestScore as number | null,
        latestAssessedAt: row.latestAssessedAt as string | null,
    };
}

function decodeMasteryMap(value: unknown): B6MasteryMap {
    const row = object(value, ['courseId', 'learnerId', 'adaptiveEnabled', 'skills']);
    if (
        typeof row.courseId !== 'string' ||
        !uuid.test(row.courseId) ||
        typeof row.learnerId !== 'string' ||
        !uuid.test(row.learnerId) ||
        typeof row.adaptiveEnabled !== 'boolean' ||
        !Array.isArray(row.skills)
    ) {
        throw protocolError();
    }
    return {
        courseId: row.courseId,
        learnerId: row.learnerId,
        adaptiveEnabled: row.adaptiveEnabled,
        skills: row.skills.map(decodeMasterySkill),
    };
}

function decodeCurrentRecommendation(value: unknown): B6CurrentRecommendation {
    const row = object(value, ['type', 'targetLessonId', 'reason', 'recommendedAt', 'targetCompleted']);
    if (
        (row.type !== 'continue' && row.type !== 'remedial') ||
        typeof row.targetLessonId !== 'string' ||
        !uuid.test(row.targetLessonId) ||
        typeof row.reason !== 'string' ||
        row.reason.trim().length === 0 ||
        typeof row.recommendedAt !== 'string' ||
        typeof row.targetCompleted !== 'boolean'
    ) {
        throw protocolError();
    }
    return {
        type: row.type,
        targetLessonId: row.targetLessonId,
        reason: row.reason,
        recommendedAt: row.recommendedAt,
        targetCompleted: row.targetCompleted,
    };
}

function decodeLessonGuidance(value: unknown): B6LessonGuidance {
    const row = object(value, ['lessonId', 'unmasteredPrerequisiteSkillIds']);
    if (typeof row.lessonId !== 'string' || !uuid.test(row.lessonId)) {
        throw protocolError();
    }
    return {
        lessonId: row.lessonId,
        unmasteredPrerequisiteSkillIds: decodeUuidArray(row.unmasteredPrerequisiteSkillIds),
    };
}

function decodeAdaptiveGuidance(value: unknown): B6AdaptiveGuidance {
    const row = object(value, [
        'courseId',
        'adaptiveEnabled',
        'placementOffered',
        'placementQuizId',
        'currentRecommendation',
        'lessons',
    ]);
    if (
        typeof row.courseId !== 'string' ||
        !uuid.test(row.courseId) ||
        typeof row.adaptiveEnabled !== 'boolean' ||
        typeof row.placementOffered !== 'boolean' ||
        (row.placementQuizId !== null &&
            (typeof row.placementQuizId !== 'string' || !uuid.test(row.placementQuizId))) ||
        (row.currentRecommendation !== null && typeof row.currentRecommendation !== 'object') ||
        !Array.isArray(row.lessons)
    ) {
        throw protocolError();
    }
    return {
        courseId: row.courseId,
        adaptiveEnabled: row.adaptiveEnabled,
        placementOffered: row.placementOffered,
        placementQuizId: row.placementQuizId as string | null,
        currentRecommendation: row.currentRecommendation
            ? decodeCurrentRecommendation(row.currentRecommendation)
            : null,
        lessons: row.lessons.map(decodeLessonGuidance),
    };
}

function decodeCourseLessonTitles(value: unknown): { courseTitle: string; lessons: MasteryLessonTitleEntry[] } {
    const row = object(value, [
        'id',
        'title',
        'description',
        'instructorId',
        'instructorDisplayName',
        'prerequisiteDescription',
        'syllabus',
    ]);
    if (typeof row.title !== 'string' || !Array.isArray(row.syllabus)) {
        throw protocolError();
    }
    const lessons: MasteryLessonTitleEntry[] = [];
    for (const ch of row.syllabus) {
        if (!isRecord(ch) || !Array.isArray(ch.lessons)) throw protocolError();
        for (const l of ch.lessons) {
            if (!isRecord(l) || typeof l.id !== 'string' || typeof l.title !== 'string') {
                throw protocolError();
            }
            lessons.push({ id: l.id, title: l.title });
        }
    }
    return { courseTitle: row.title, lessons };
}

function decodeCourseProgressStatuses(value: unknown): MasteryLessonProgressStatus[] {
    if (!isRecord(value) || !Array.isArray(value.lessons)) throw protocolError();
    return value.lessons.map((l) => {
        const row = object(l, ['lessonId', 'completed', 'masterySkipped']);
        if (
            typeof row.lessonId !== 'string' ||
            typeof row.completed !== 'boolean' ||
            typeof row.masterySkipped !== 'boolean'
        ) {
            throw protocolError();
        }
        return {
            lessonId: row.lessonId,
            completed: row.completed,
            masterySkipped: row.masterySkipped,
        };
    });
}

/** CT33 (b6ReadMasteryMap): Read course skills, prerequisites, and stored learner mastery states. */
export function readMasteryMap(
    courseId: string,
    options: MasteryRequestOptions = {}
): Promise<B6MasteryMap> {
    return request(`/courses/${encodeURIComponent(courseId)}/mastery-map`, {
        ...options,
        method: 'GET',
        auth: 'authenticated',
        expectedStatus: 200,
        decode: decodeMasteryMap,
    });
}

/** CT33 (b6ReadAdaptiveGuidance): Read placement offer, visible current recommendation, and lesson warnings. */
export function readAdaptiveGuidance(
    courseId: string,
    options: MasteryRequestOptions = {}
): Promise<B6AdaptiveGuidance> {
    return request(`/courses/${encodeURIComponent(courseId)}/adaptive-guidance`, {
        ...options,
        method: 'GET',
        auth: 'authenticated',
        expectedStatus: 200,
        decode: decodeAdaptiveGuidance,
    });
}

/** Mastery-scoped read of published course syllabus titles (CT16-FINAL F1 isolation). */
export function readCourseLessonTitlesForMap(
    courseId: string,
    options: MasteryRequestOptions = {}
): Promise<{ courseTitle: string; lessons: MasteryLessonTitleEntry[] }> {
    return request(`/courses/${encodeURIComponent(courseId)}`, {
        ...options,
        method: 'GET',
        auth: 'public',
        expectedStatus: 200,
        decode: decodeCourseLessonTitles,
    });
}

/** Mastery-scoped read of lesson completion status for related lessons (CT16-FINAL F1 isolation). */
export function readCourseProgressForMap(
    courseId: string,
    options: MasteryRequestOptions = {}
): Promise<MasteryLessonProgressStatus[]> {
    return request(`/courses/${encodeURIComponent(courseId)}/progress`, {
        ...options,
        method: 'GET',
        auth: 'authenticated',
        expectedStatus: 200,
        decode: decodeCourseProgressStatuses,
    });
}