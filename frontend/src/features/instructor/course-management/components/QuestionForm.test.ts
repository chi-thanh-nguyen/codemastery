import { describe, expect, it } from 'vitest';
import { draftFromQuestion, emptyQuestionDraft, questionWrite, sameQuestion, sortSkills, validateQuestion, withKind } from './QuestionForm';
import type { QuestionFormDraft } from './QuestionForm';

const skillId = '00000000-0000-4000-8000-0000000000a1';
const opts = (...correct: boolean[]) => correct.map((value, index) => ({ key: String(index), label: `Option ${index}`, correct: value }));
const base = (patch: Partial<QuestionFormDraft>): QuestionFormDraft => ({ ...emptyQuestionDraft(skillId), prompt: 'Q', difficulty: 'easy', options: opts(true, false), ...patch });

describe('question field rules mirror the approved constraints (OpenAPI B3QuestionWrite)', () => {
    it('requires exactly one correct option for single choice and true/false', () => {
        expect(validateQuestion(base({}))).toEqual({});
        expect(validateQuestion(base({ options: opts(true, true) })).correct).toBe('Mark exactly one correct option.');
        expect(validateQuestion(withKind(base({}), 'true_false')).correct).toBe('Mark exactly one correct option.');
    });
    it('requires at least one correct option for multiple choice', () => {
        expect(validateQuestion(base({ kind: 'multiple_choice', options: opts(true, true, false) }))).toEqual({});
        expect(validateQuestion(base({ kind: 'multiple_choice', options: opts(false, false) })).correct).toBe('Mark at least one correct option.');
    });
    it('requires a prompt, option text, two options, a skill and a difficulty', () => {
        const errors = validateQuestion({ ...base({ prompt: ' \n ', options: [{ key: 'a', label: ' ', correct: true }, { key: 'b', label: 'B', correct: false }] }), skillId: '', difficulty: '' });
        expect(errors).toEqual({ prompt: 'Enter the question.', options: 'Every option needs text.', skillId: 'Choose the skill this question checks.', difficulty: 'Choose a difficulty.' });
        expect(validateQuestion(base({ options: [{ key: 'a', label: 'A', correct: true }] })).options).toBe('Add at least two options.');
    });
    it('fixes True/False labels and clears keys when switching to single choice', () => {
        const tf = withKind(base({ kind: 'multiple_choice', options: opts(true, true, true) }), 'true_false');
        expect(tf.options.map((item) => [item.label, item.correct])).toEqual([['True', false], ['False', false]]);
        expect(withKind(base({ kind: 'multiple_choice', options: opts(true, true) }), 'single_choice').options.every((item) => !item.correct)).toBe(true);
        expect(withKind(tf, 'single_choice').options.map((item) => item.label)).toEqual(['', '']);
    });
    it('sends the prompt with its line breaks and compares drafts with the server version exactly', () => {
        const question = { id: skillId, skillId, prompt: 'x = 1\nprint(x)', kind: 'single_choice' as const, difficulty: 'hard' as const, options: ['1', '2'], correctOptions: [true, false] };
        const draft = draftFromQuestion(question);
        expect(questionWrite(draft)).toEqual({ skillId, prompt: 'x = 1\nprint(x)', kind: 'single_choice', difficulty: 'hard', options: ['1', '2'], correctOptions: [true, false] });
        expect(sameQuestion(draft, question)).toBe(true);
        expect(sameQuestion({ ...draft, prompt: 'x = 1 print(x)' }, question)).toBe(false);
    });
    it('orders skills alphabetically with ties by UUID', () => {
        const skills = [{ id: 'b', name: 'loops', description: null }, { id: 'c', name: 'Arrays', description: null }, { id: 'a', name: 'Loops', description: null }];
        expect(sortSkills(skills).map((skill) => skill.id)).toEqual(['c', 'a', 'b']);
    });
});
