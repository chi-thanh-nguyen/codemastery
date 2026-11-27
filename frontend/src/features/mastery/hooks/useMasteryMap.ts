import { useCallback, useEffect, useRef, useState } from 'react';
import { useAuth } from '../../auth/hooks/useAuth';
import { ApiError } from '../../../shared/types/apiTypes';
import type { MasteryState } from '../../../shared/components/StatusPill';
import {
    readAdaptiveGuidance,
    readCourseLessonTitlesForMap,
    readCourseProgressForMap,
    readMasteryMap,
} from '../api/masteryApi';
import type {
    LessonProgressLabel,
    RecommendationView,
    SkillView,
} from '../types/masteryTypes';

// Session-only memory for "Updated" marks (D8-14: never persisted to browser storage)
const sessionPreviousStates = new Map<string, { generation: number; states: Map<string, MasteryState> }>();

export function useMasteryMap(courseId: string | undefined) {
    const auth = useAuth();
    const [courseTitle, setCourseTitle] = useState('');
    const [adaptiveEnabled, setAdaptiveEnabled] = useState(true);
    const [placementOffered, setPlacementOffered] = useState(false);
    const [skills, setSkills] = useState<SkillView[]>([]);
    const [recommendation, setRecommendation] = useState<RecommendationView | null>(null);
    const [loading, setLoading] = useState(true);
    const [errorStatus, setErrorStatus] = useState<number | null>(null);
    const [errorMessage, setErrorMessage] = useState<string | null>(null);

    const alive = useRef(true);
    const work = useRef<AbortController | null>(null);

    // Clear stale session memory on auth generation change
    useEffect(() => {
        for (const [key, entry] of sessionPreviousStates.entries()) {
            if (entry.generation !== auth.generation) {
                sessionPreviousStates.delete(key);
            }
        }
    }, [auth.generation]);

    useEffect(() => {
        alive.current = true;
        return () => {
            alive.current = false;
            work.current?.abort();
        };
    }, []);

    const loadMap = useCallback(async () => {
        if (!courseId || !auth.isAuthenticated) {
            setLoading(false);
            return;
        }

        const controller = new AbortController();
        work.current?.abort();
        work.current = controller;
        const gen = auth.generation;
        const opts = { signal: controller.signal, isCurrent: () => alive.current && auth.generation === gen };

        setLoading(true);
        setErrorStatus(null);
        setErrorMessage(null);

        try {
            const [mapRes, guidanceRes, courseRes, progressRes] = await Promise.all([
                readMasteryMap(courseId, opts),
                readAdaptiveGuidance(courseId, opts),
                readCourseLessonTitlesForMap(courseId, opts),
                readCourseProgressForMap(courseId, opts),
            ]);

            if (!alive.current || work.current !== controller || controller.signal.aborted) return;

            const titleByLesson = new Map(courseRes.lessons.map((l) => [l.id, l.title]));
            const statusByLesson = new Map(progressRes.map((p) => [p.lessonId, p]));
            const targetLessonId = guidanceRes.currentRecommendation?.targetLessonId ?? null;

            // D8-14: detect state changes since previous read in this session
            const cacheKey = `${courseId}:${gen}`;
            const prevEntry = sessionPreviousStates.get(cacheKey);
            const prevMap = prevEntry && prevEntry.generation === gen ? prevEntry.states : null;
            const nextMap = new Map<string, MasteryState>();

            const mappedSkills: SkillView[] = mapRes.skills.map((s) => {
                nextMap.set(s.id, s.state);
                const wasUpdated = prevMap !== null && prevMap.has(s.id) && prevMap.get(s.id) !== s.state;

                return {
                    id: s.id,
                    name: s.name,
                    description: s.description ?? undefined,
                    state: s.state,
                    prerequisiteIds: s.prerequisiteSkillIds,
                    latestScore: s.latestScore,
                    latestAssessedAtLabel: s.latestAssessedAt
                        ? new Date(s.latestAssessedAt).toLocaleString('en-US', { dateStyle: 'medium', timeStyle: 'short' })
                        : undefined,
                    updated: wasUpdated,
                    relatedLessons: s.relatedLessonIds.map((lid) => {
                        const p = statusByLesson.get(lid);
                        const progressLabel: LessonProgressLabel = p?.masterySkipped
                            ? 'Mastered'
                            : p?.completed
                                ? 'Completed'
                                : 'Not started';
                        return {
                            id: lid,
                            title: titleByLesson.get(lid) ?? 'Lesson',
                            progress: progressLabel,
                            recommended: targetLessonId === lid, // D12-4: mark target Lesson
                        };
                    }),
                };
            });

            sessionPreviousStates.set(cacheKey, { generation: gen, states: nextMap });

            setCourseTitle(courseRes.courseTitle);
            setAdaptiveEnabled(mapRes.adaptiveEnabled);
            setPlacementOffered(guidanceRes.placementOffered);
            setSkills(mappedSkills);

            if (guidanceRes.currentRecommendation) {
                const rec = guidanceRes.currentRecommendation;
                setRecommendation({
                    id: `${rec.type}-${rec.targetLessonId}`,
                    type: rec.type,
                    targetLessonId: rec.targetLessonId,
                    targetLessonTitle: titleByLesson.get(rec.targetLessonId) ?? 'Recommended Lesson',
                    reason: rec.reason,
                    targetCompleted: rec.targetCompleted,
                    createdAtLabel: new Date(rec.recommendedAt).toLocaleString('en-US', {
                        dateStyle: 'medium',
                        timeStyle: 'short',
                    }),
                });
            } else {
                setRecommendation(null);
            }
        } catch (err: unknown) {
            if (
                !alive.current ||
                work.current !== controller ||
                controller.signal.aborted ||
                (err instanceof ApiError && err.kind === 'cancelled')
            ) {
                return;
            }
            if (err instanceof ApiError) {
                setErrorStatus(err.status ?? null);
                setErrorMessage(err.message);
            } else {
                setErrorMessage("Couldn't load the Mastery Map. Try again.");
            }
        } finally {
            if (alive.current && work.current === controller) {
                setLoading(false);
            }
        }
    }, [courseId, auth.isAuthenticated, auth.generation]);

    useEffect(() => {
        void loadMap();
    }, [loadMap]);

    return {
        courseTitle,
        adaptiveEnabled,
        placementOffered,
        skills,
        recommendation,
        loading,
        errorStatus,
        errorMessage,
        refresh: loadMap,
    };
}