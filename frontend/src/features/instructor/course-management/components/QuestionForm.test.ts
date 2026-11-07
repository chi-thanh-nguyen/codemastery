import { describe, expect, it } from 'vitest';
import { validateQuestion, emptyQuestion } from './QuestionForm';
import type { QuestionDraft } from '../types/courseManagementTypes';

const base = (patch: Partial<QuestionDraft>): QuestionDraft => ({ ...emptyQuestion('loops'), prompt: 'Q', ...patch });
const opts = (...correct: boolean[]) => correct.map((value, index) => ({ id: String(index), label: `Option ${index}`, correct: value }));

describe('question field rules mirror the approved constraints (data-model §11)', () => {
    it('requires exactly one correct option for single choice and true/false', () => {
        expect(validateQuestion(base({ options: opts(true, false) }))).toEqual({});
        expect(validateQuestion(base({ options: opts(true, true) })).correct).toBe('Mark exactly one correct option.');
        expect(validateQuestion(base({ kind: 'true_false', options: [{ id: 't', label: 'True', correct: false }, { id: 'f', label: 'False', correct: false }] })).correct).toBe('Mark exactly one correct option.');
    });
    it('requires at least one correct option for multiple choice', () => {
        expect(validateQuestion(base({ kind: 'multiple_choice', options: opts(true, true, false) }))).toEqual({});
        expect(validateQuestion(base({ kind: 'multiple_choice', options: opts(false, false) })).correct).toBe('Mark at least one correct option.');
    });
    it('rejects blank prompts, blank options, fewer than two options and a missing skill', () => {
        const errors = validateQuestion({ ...base({ prompt: ' ', options: [{ id: 'a', label: ' ', correct: true }] }), skillId: '' });
        expect(errors.prompt).toBe('Enter the question.'); expect(errors.options).toBe('Every option needs text.'); expect(errors.skillId).toBe('Choose the skill this question checks.');
        expect(validateQuestion(base({ options: [{ id: 'a', label: 'A', correct: true }] })).options).toBe('Add at least two options.');
    });
});
