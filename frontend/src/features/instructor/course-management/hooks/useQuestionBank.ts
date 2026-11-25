import { useMemo } from 'react';
import { createQuestion, DEFAULT_PAGE_SIZE, deleteUnusedQuestion, listQuestions, readQuestion, replaceUnusedQuestion } from '../api/assessmentAuthoringApi';
import type { B3QuestionPage, B3QuestionWrite } from '../api/assessmentAuthoringApi';
import { useAuthoringOperation, useOwnerRead } from './useAssessmentEditor';
import type { OwnerRead } from './useAssessmentEditor';

/** `b3ListQuestions` for the Questions view and the quiz editor's picker: one page in UUID order, never filtered or sorted here. */
export function useQuestionBank(courseId: string, page: number, size = DEFAULT_PAGE_SIZE): OwnerRead<B3QuestionPage> {
    return useOwnerRead(`questions|${courseId}|${page}|${size}`, (options) => listQuestions(courseId, page, size, options));
}

/** The question form's operations (CT23.questions). A refused or unknown outcome is the caller's to present; nothing is retried. */
export function useQuestionEditor(courseId: string) {
    const { busy, run } = useAuthoringOperation();
    // Stable per course and session, so effects that read on opening run once.
    const operations = useMemo(() => ({
        read: (questionId: string) => run((options) => readQuestion(courseId, questionId, options)),
        create: (write: B3QuestionWrite) => run((options) => createQuestion(courseId, write, options)),
        replace: (questionId: string, write: B3QuestionWrite) => run((options) => replaceUnusedQuestion(courseId, questionId, write, options)),
        remove: (questionId: string) => run((options) => deleteUnusedQuestion(courseId, questionId, options)),
    }), [courseId, run]);
    return { busy, ...operations };
}
export type QuestionEditorOperations = ReturnType<typeof useQuestionEditor>;
