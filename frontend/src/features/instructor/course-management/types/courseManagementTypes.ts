/*
 * Presentation view models for Instructor authoring (S17–S19). NOT API contracts:
 * Course authoring (CT21.remaining), Assessment authoring and file materials (CT09)
 * are DEFERRED. Ownership is enforced by the backend; these shapes carry no permission.
 */
export type Publication = 'published' | 'hidden';
export type Difficulty = 'easy' | 'medium' | 'hard';
export interface InstructorCourseView { id: string; title: string; description: string; publication: Publication; lessonCount: number; learnerCount?: number }
export interface LessonEditRow { id: string; title: string; position: number; publication: Publication; skillIds: string[] }
export interface ChapterEditView { id: string; title: string; position: number; lessons: LessonEditRow[] }
export type MaterialDraft =
    | { id: string; kind: 'text'; title: string; text: string }
    | { id: string; kind: 'file'; title: string; fileName: string }
    | { id: string; kind: 'external_video'; title: string; url: string };
/** A material before it has an identifier (distributive over the union). */
export type MaterialInput = { [K in MaterialDraft['kind']]: Omit<Extract<MaterialDraft, { kind: K }>, 'id'> }[MaterialDraft['kind']];
export interface LessonDraft { id: string; title: string; content: string; materials: MaterialDraft[]; skillIds: string[]; publication: Publication }
export interface SkillOption { id: string; name: string }
export interface QuestionDraft {
    id: string;
    kind: 'single_choice' | 'multiple_choice' | 'true_false';
    prompt: string;
    code?: string;
    options: { id: string; label: string; correct: boolean }[];
    skillId: string;
    difficulty: Difficulty;
    usedInQuizzes: number;
    usedInAttempts: boolean;
}
export interface QuizDraft { id: string; title: string; kind: 'diagnostic' | 'practice' | 'test'; timeLimitMinutes: number; questionIds: string[]; publication: Publication }
export interface AssignmentDraft { id: string; title: string; instructions: string; deadline: string; lessonId?: string; publication: Publication }
export const difficultyLabel: Record<Difficulty, string> = { easy: 'Easy', medium: 'Medium', hard: 'Hard' };
export const questionKindLabel: Record<QuestionDraft['kind'], string> = { single_choice: 'Single choice', multiple_choice: 'Multiple choice', true_false: 'True / false' };
