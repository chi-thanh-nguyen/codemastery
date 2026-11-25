import { createContext, useCallback, useContext, useMemo, useRef, useState } from 'react';
import { DEFAULT_PAGE_SIZE } from '../api/assessmentAuthoringApi';
import {
    createCourseChapter, createCourseLesson, createOwnedCourse, getOwnedCourse, listOwnedCourses,
    orderChapterLessons, orderCourseChapters, setCourseLessonPublication, setOwnedCoursePublication,
    updateCourseChapter, updateOwnedCourse,
} from '../api/instructorCourseApi';
import type {
    CoursePublicationStatus, OwnedCourse, OwnedCourseSummaryPage, OwnedCourseWrite, OwnedLessonWrite,
} from '../api/instructorCourseApi';
import type { SkillSource } from '../components/QuestionForm';
import { useAuthoringOperation, useOwnerRead } from './useAssessmentEditor';
import type { OwnerRead } from './useAssessmentEditor';
import { useOwnedSkillGraph } from '../../skill-graph-editor/hooks/useSkillGraph';
import type { OwnedSkillGraphRead } from '../../skill-graph-editor/hooks/useSkillGraph';

/*
 * Instructor course authoring state & shared course editor context (DESIGN.md S17, S18, D12-3;
 * CT21.authoring, CT05.representation). One `getOwnedCourse` read and one `getOwnedSkillGraph`
 * read are held by the course editor shell for the active `courseId` and session generation, so
 * Content, Skills, Assessments, Settings and the quiz/assignment editors share them without
 * duplicate requests and discard them cleanly when the course or session changes.
 */

export function useOwnedCourses(page: number, size = DEFAULT_PAGE_SIZE): OwnerRead<OwnedCourseSummaryPage> {
    return useOwnerRead(`owned-courses|${page}|${size}`, (options) => listOwnedCourses(page, size, options));
}

export function useCourseCreation() {
    const { busy, run } = useAuthoringOperation();
    const create = useCallback((write: OwnedCourseWrite) => run((options) => createOwnedCourse(write, options)), [run]);
    return { busy, create };
}

export function useCourseOperations(courseId: string) {
    const { busy, run } = useAuthoringOperation();
    const actions = useMemo(() => ({
        updateMetadata: (write: OwnedCourseWrite) => run((options) => updateOwnedCourse(courseId, write, options)),
        setPublication: (status: CoursePublicationStatus) => run((options) => setOwnedCoursePublication(courseId, status, options)),
        createChapter: (title: string) => run((options) => createCourseChapter(courseId, title, options)),
        updateChapter: (chapterId: string, title: string) => run((options) => updateCourseChapter(courseId, chapterId, title, options)),
        orderChapters: (orderedIds: readonly string[]) => run((options) => orderCourseChapters(courseId, orderedIds, options)),
        createLesson: (chapterId: string, write: OwnedLessonWrite) => run((options) => createCourseLesson(courseId, chapterId, write, options)),
        orderLessons: (chapterId: string, orderedIds: readonly string[]) => run((options) => orderChapterLessons(courseId, chapterId, orderedIds, options)),
        setLessonPublication: (lessonId: string, status: CoursePublicationStatus) => run((options) => setCourseLessonPublication(courseId, lessonId, status, options)),
    }), [courseId, run]);
    return { busy, ...actions };
}

export interface SharedCourseContextState {
    courseId: string;
    course: {
        status: 'loading' | 'ready' | 'error';
        data: OwnedCourse | null;
        error: unknown;
        reload: () => void;
    };
    skillGraph: OwnedSkillGraphRead;
    skills: SkillSource;
    /** Marks that S20 changed skills in this document, and triggers a fresh graph read. */
    notifySkillsChanged: () => void;
    /** Re-reads the skill graph if an earlier S20 skill mutation has not been freshly read yet (D12-3). */
    refreshSkillsIfStale: () => void;
    /** Registers whether the active tab currently holds unsaved changes for shell tab/back navigation. */
    setTabDirty: (dirty: boolean) => void;
    isTabDirty: () => boolean;
}

export const CourseEditorContext = createContext<SharedCourseContextState | null>(null);

export function useCourseEditorContext(): SharedCourseContextState | null {
    return useContext(CourseEditorContext);
}

export function useSharedCourseState(courseId: string): SharedCourseContextState {
    const courseRead = useOwnerRead(`owned-course|${courseId}`, (options) => getOwnedCourse(courseId, options));
    const [overrideCourse, setOverrideCourse] = useState<{ id: string; data: OwnedCourse } | null>(null);
    const skillGraph = useOwnedSkillGraph(courseId);
    const skillsStaleRef = useRef(false);
    const tabDirtyRef = useRef(false);

    // Clear stale flag once a new graph arrives.
    if (skillGraph.graph && !skillGraph.loading) {
        skillsStaleRef.current = false;
    }

    const reloadCourse = useCallback(() => {
        setOverrideCourse(null);
        courseRead.reload();
    }, [courseRead]);

    const notifySkillsChanged = useCallback(() => {
        skillsStaleRef.current = true;
        skillGraph.reload();
    }, [skillGraph]);

    const refreshSkillsIfStale = useCallback(() => {
        if (skillsStaleRef.current || skillGraph.error) {
            skillsStaleRef.current = false;
            skillGraph.reload();
        }
    }, [skillGraph]);

    const setTabDirty = useCallback((dirty: boolean) => {
        tabDirtyRef.current = dirty;
    }, []);
    const isTabDirty = useCallback(() => tabDirtyRef.current, []);

    const currentData = overrideCourse && overrideCourse.id === courseId && !courseRead.loading
        ? overrideCourse.data
        : courseRead.data;
    const courseStatus: 'loading' | 'ready' | 'error' = courseRead.error && !currentData
        ? 'error'
        : currentData
            ? 'ready'
            : 'loading';

    const skills = useMemo<SkillSource>(() => ({
        status: skillGraph.error ? 'error' : skillGraph.graph ? 'ready' : 'loading',
        skills: skillGraph.graph?.skills ?? [],
        reload: skillGraph.reload,
    }), [skillGraph.error, skillGraph.graph, skillGraph.reload]);

    return useMemo(() => ({
        courseId,
        course: {
            status: courseStatus,
            data: currentData,
            error: courseRead.error,
            reload: reloadCourse,
        },
        skillGraph,
        skills,
        notifySkillsChanged,
        refreshSkillsIfStale,
        setTabDirty,
        isTabDirty,
    }), [courseId, courseStatus, currentData, courseRead.error, reloadCourse, skillGraph, skills, notifySkillsChanged, refreshSkillsIfStale, setTabDirty, isTabDirty]);
}
