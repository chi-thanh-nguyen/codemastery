/**
 * SAMPLE DATA for the design preview only (DESIGN.md › S26). Deterministic, synthetic
 * and never sent anywhere. Names, scores, reasons and states are illustrative; they do
 * not come from, or imply, any approved API, threshold or adaptive algorithm.
 * Course and lesson titles extend the synthetic seed course "Introduction to Programming".
 */
import type { OutlineChapterView, LessonBlock, LessonMaterialView } from '../../features/learning/types/learningTypes';
import type { RecommendationView, SkillView } from '../../features/mastery/types/masteryTypes';
import type { AssessmentItemView, AttemptView, AssignmentView, QuestionView, QuizSummaryView, SkillChangeView } from '../../features/assessment/types/assessmentTypes';
import type { DiscussionPostView, NotificationView } from '../../features/interaction/types/interactionTypes';
import type { AssignmentDraft, ChapterEditView, InstructorCourseView, LessonDraft, QuestionDraft, QuizDraft } from '../../features/instructor/course-management/types/courseManagementTypes';
import type { EditableSkill, MappingLesson } from '../../features/instructor/skill-graph-editor/types/skillGraphTypes';
import type { SubmissionRow } from '../../features/instructor/grading/types/gradingTypes';
import type { LearnerRow, RecommendationLogRow, ResultRow } from '../../features/instructor/learner-insights/types/learnerInsightsTypes';
import type { AuditEntry, CourseMetricRow, SkillDistributionRow } from '../../features/admin/reporting-dashboard/types/reportingTypes';
import type { UserRowView } from '../../features/admin/user-management/types/userManagementTypes';
import type { AdminCourseRow } from '../../features/admin/course-management/types/adminCourseTypes';
import type { ReportRow } from '../../features/admin/content-reports/types/contentReportTypes';

export const SAMPLE_TIMEZONE = 'ICT (UTC+7)';
export const sampleLearner = { name: 'Linh Pham', role: 'Learner' };
export const sampleInstructor = { name: 'Instructor Demo', role: 'Instructor' };
export const sampleAdmin = { name: 'Admin Demo', role: 'Administrator' };
export const COURSE_TITLE = 'Introduction to Programming';
/**
 * One description per sample course, shared by every workspace, so a course shows the same
 * course mark everywhere (the mark reads the language from the course's own text; the sample
 * lessons teach Python).
 */
export const COURSE_DESCRIPTIONS = {
    'c-intro': 'Variables, decisions, loops and functions in Python for first-year students, one short lesson at a time.',
    'c-practice': 'Weekly exercises that apply each chapter of Introduction to Programming.',
} as const;

// ── Learner: memberships (S5 sample; the live page reads CT22.membership) ──
export interface SampleMembership { id: string; title: string; description: string; instructorDisplayName: string | null; status: 'active' | 'completed' | 'left' }
export const learnerMemberships: SampleMembership[] = [
    { id: 'c-intro', title: COURSE_TITLE, description: COURSE_DESCRIPTIONS['c-intro'], instructorDisplayName: sampleInstructor.name, status: 'active' },
    { id: 'c-practice', title: 'Programming Practice', description: COURSE_DESCRIPTIONS['c-practice'], instructorDisplayName: sampleInstructor.name, status: 'active' },
    { id: 'c-thinking', title: 'Computational Thinking', description: 'Decomposition, patterns and abstraction before you write any code.', instructorDisplayName: null, status: 'completed' },
    { id: 'c-data', title: 'Working with Data', description: 'Lists, dictionaries and reading simple files.', instructorDisplayName: sampleInstructor.name, status: 'left' },
];

// ── Learner: course structure and progress ─────────────────────────────────
export const learnerChapters: OutlineChapterView[] = [
    { id: 'ch1', title: 'Getting Started', position: 1, lessons: [
        { id: 'l1', title: 'What a Program Is', position: 1, progress: 'completed', summary: 'Programs as precise instructions a computer follows in order.', skills: [{ id: 'output', name: 'Output', state: 'mastered' }] },
        { id: 'l2', title: 'Breaking a Problem into Steps', position: 2, progress: 'completed', summary: 'Turn a task into small, ordered steps before writing code.', skills: [{ id: 'debugging', name: 'Debugging basics', state: 'unknown' }] },
        { id: 'l3', title: 'Variables and Values', position: 3, progress: 'mastered-skip', summary: 'Store values with names and update them.', skills: [{ id: 'variables', name: 'Variables', state: 'mastered' }] },
    ] },
    { id: 'ch2', title: 'Making Decisions', position: 2, lessons: [
        { id: 'l4', title: 'Expressions and Operators', position: 4, progress: 'completed', summary: 'Arithmetic, comparison and how Python evaluates expressions.', skills: [{ id: 'expressions', name: 'Expressions', state: 'mastered' }] },
        { id: 'l5', title: 'If Statements', position: 5, progress: 'not-started', markers: ['remedial'], summary: 'Run code only when a condition is true.', skills: [{ id: 'conditionals', name: 'Conditionals', state: 'learning' }] },
        { id: 'l6', title: 'Combining Conditions', position: 6, progress: 'not-started', summary: 'Use and, or and not to build conditions.', skills: [{ id: 'boolean', name: 'Boolean logic', state: 'learning' }] },
    ] },
    { id: 'ch3', title: 'Repetition', position: 3, lessons: [
        { id: 'l7', title: 'For Loops', position: 7, progress: 'not-started', isResume: true, markers: ['prerequisite-gap'], summary: 'Repeat steps for each item in a sequence.', skills: [{ id: 'for-loops', name: 'For loops', state: 'weak' }] },
        { id: 'l8', title: 'While Loops', position: 8, progress: 'not-started', markers: ['prerequisite-gap'], summary: 'Repeat while a condition stays true, and stop safely.', skills: [{ id: 'while-loops', name: 'While loops', state: 'unknown' }] },
        { id: 'l9', title: 'Practice: Trace and Fix Short Programs', position: 9, progress: 'not-started', summary: 'Read loops line by line and fix off-by-one mistakes.', skills: [{ id: 'debugging', name: 'Debugging basics', state: 'unknown' }] },
    ] },
    { id: 'ch4', title: 'Functions and Data', position: 4, lessons: [
        { id: 'l10', title: 'Defining Functions', position: 10, progress: 'not-started', summary: 'Name a block of steps and reuse it with parameters.', skills: [{ id: 'functions', name: 'Functions', state: 'unknown' }] },
        { id: 'l11', title: 'Lists', position: 11, progress: 'not-started', summary: 'Keep many values in order and loop over them.', skills: [{ id: 'lists', name: 'Lists', state: 'unknown' }] },
        { id: 'l12', title: 'Strings and Text', position: 12, progress: 'not-started', summary: 'Slice, join and format text.', skills: [{ id: 'strings', name: 'Strings', state: 'learning' }] },
    ] },
];
export const learnerProgress = { completed: 4, total: 12, percent: 33 };

export const learnerSkills: SkillView[] = [
    { id: 'variables', name: 'Variables', state: 'mastered', prerequisiteIds: [], latestScore: 100, relatedLessons: [{ id: 'l3', title: 'Variables and Values', progress: 'Mastered' }] },
    { id: 'expressions', name: 'Expressions', state: 'mastered', prerequisiteIds: ['variables'], latestScore: 92, relatedLessons: [{ id: 'l4', title: 'Expressions and Operators', progress: 'Completed' }] },
    { id: 'output', name: 'Output', state: 'mastered', prerequisiteIds: ['variables'], latestScore: 100, relatedLessons: [{ id: 'l1', title: 'What a Program Is', progress: 'Completed' }] },
    { id: 'conditionals', name: 'Conditionals', state: 'learning', prerequisiteIds: ['expressions'], latestScore: 60, description: 'Choosing what code runs with if, elif and else.', relatedLessons: [{ id: 'l5', title: 'If Statements', progress: 'Not started' }] },
    { id: 'boolean', name: 'Boolean logic', state: 'learning', prerequisiteIds: ['expressions'], latestScore: 67, relatedLessons: [{ id: 'l6', title: 'Combining Conditions', progress: 'Not started' }] },
    { id: 'strings', name: 'Strings', state: 'learning', prerequisiteIds: ['expressions'], latestScore: 70, relatedLessons: [{ id: 'l12', title: 'Strings and Text', progress: 'Not started' }] },
    { id: 'for-loops', name: 'For loops', state: 'weak', prerequisiteIds: ['conditionals'], latestScore: 40, description: 'Repeating steps for each item in a sequence.', relatedLessons: [{ id: 'l7', title: 'For Loops', progress: 'Not started' }] },
    { id: 'debugging', name: 'Debugging basics', state: 'unknown', prerequisiteIds: ['output', 'conditionals'], latestScore: null, relatedLessons: [{ id: 'l2', title: 'Breaking a Problem into Steps', progress: 'Completed' }, { id: 'l9', title: 'Practice: Trace and Fix Short Programs', progress: 'Not started' }] },
    { id: 'while-loops', name: 'While loops', state: 'unknown', prerequisiteIds: ['for-loops', 'boolean'], latestScore: null, relatedLessons: [{ id: 'l8', title: 'While Loops', progress: 'Not started' }] },
    { id: 'functions', name: 'Functions', state: 'unknown', prerequisiteIds: ['for-loops'], latestScore: null, relatedLessons: [{ id: 'l10', title: 'Defining Functions', progress: 'Not started' }] },
    { id: 'lists', name: 'Lists', state: 'unknown', prerequisiteIds: ['for-loops'], latestScore: null, relatedLessons: [{ id: 'l11', title: 'Lists', progress: 'Not started' }] },
];

export const sampleRecommendation: RecommendationView = {
    id: 'rec-1', type: 'remedial', targetLessonId: 'l5', targetLessonTitle: 'If Statements', skillId: 'conditionals', createdAtLabel: 'Thu 5 Nov, 21:12',
    reason: 'For loops is Weak and its prerequisite Conditionals is not mastered yet. Review If Statements before continuing with loops.',
};

export const lessonBlocks: Record<string, LessonBlock[]> = {
    l7: [
        { kind: 'paragraph', text: 'A for loop repeats the same steps once for every item in a sequence. Instead of writing print three times, you describe the step once and let Python repeat it.' },
        { kind: 'code', code: 'for name in ["Ana", "Binh", "Chi"]:\n    print("Hello,", name)', caption: 'The indented line runs once for each name.' },
        { kind: 'heading', text: 'Counting with range' },
        { kind: 'paragraph', text: 'range(n) produces the numbers 0 up to, but not including, n. It is the usual way to repeat something a fixed number of times.' },
        { kind: 'code', code: 'for i in range(3):\n    print(i)\n# prints 0, 1 and 2 on separate lines' },
        { kind: 'list', items: ['The loop variable (here, i) changes on every repetition.', 'Everything indented under the for line belongs to the loop.', 'The loop ends after the last item.'] },
        { kind: 'note', text: 'If a loop gives one result too many or too few, check where range starts and stops. This is called an off-by-one mistake.' },
    ],
};
export const lessonMaterials: Record<string, LessonMaterialView[]> = {
    l7: [{ id: 'm1', kind: 'file', name: 'for-loops-slides.pdf', description: 'Slides · PDF' }, { id: 'm2', kind: 'external_video', title: 'Loops walkthrough (external)', url: 'https://example.com/video/loops' }],
};

export const discussionPosts: DiscussionPostView[] = [
    { id: 'p1', author: 'Quang Le', role: 'Learner', createdAtLabel: 'Wed 4 Nov, 19:40', body: 'Why does range(1, 5) stop at 4 and not 5?', replies: [
        { id: 'p1r1', author: 'Instructor Demo', role: 'Instructor', createdAtLabel: 'Wed 4 Nov, 20:05', body: 'The stop value is excluded. range(1, 5) gives 1, 2, 3 and 4 — four numbers. Use range(1, 6) to include 5.' },
    ] },
    { id: 'p2', author: 'Linh Pham', role: 'Learner', createdAtLabel: 'Thu 5 Nov, 08:15', body: 'Can I change the list while looping over it?', replies: [] },
];

// ── Learner: assessments ───────────────────────────────────────────────────
export const loopsQuiz: QuizSummaryView = { id: 'q-loops', title: 'Loops practice quiz', kind: 'practice', questionCount: 6, timeLimitSeconds: 600, nextAttemptNumber: 3 };
export const loopsQuestions: QuestionView[] = [
    { id: 'qq1', position: 1, kind: 'single_choice', prompt: 'What does this program print?', code: 'for i in range(3):\n    print(i * 2)', options: [{ id: 'a', label: '0, 2 and 4 on separate lines' }, { id: 'b', label: '2, 4 and 6 on separate lines' }, { id: 'c', label: '0, 1 and 2 on separate lines' }, { id: 'd', label: 'Nothing' }] },
    { id: 'qq2', position: 2, kind: 'multiple_choice', prompt: 'Which of these loops run exactly 5 times?', options: [{ id: 'a', label: 'for i in range(5):' }, { id: 'b', label: 'for i in range(1, 6):' }, { id: 'c', label: 'for i in range(0, 10, 2):' }, { id: 'd', label: 'for i in range(5, 0):' }] },
    { id: 'qq3', position: 3, kind: 'true_false', prompt: 'A while loop always runs its body at least once.', options: [{ id: 't', label: 'True' }, { id: 'f', label: 'False' }] },
    { id: 'qq4', position: 4, kind: 'single_choice', prompt: 'What does this program print?', code: 'total = 0\nfor n in [2, 4, 6]:\n    total = total + n\nprint(total)', options: [{ id: 'a', label: '6' }, { id: 'b', label: '12' }, { id: 'c', label: '2 4 6' }, { id: 'd', label: '0' }] },
    { id: 'qq5', position: 5, kind: 'single_choice', prompt: 'Which keyword stops a loop immediately?', options: [{ id: 'a', label: 'stop' }, { id: 'b', label: 'continue' }, { id: 'c', label: 'break' }, { id: 'd', label: 'return' }] },
    { id: 'qq6', position: 6, kind: 'true_false', prompt: 'range(3) produces 1, 2 and 3.', options: [{ id: 't', label: 'True' }, { id: 'f', label: 'False' }] },
];
/** Preview-only answer key used to show a sample result. The server is the scoring authority. */
export const loopsAnswerKey: Record<string, string[]> = { qq1: ['a'], qq2: ['a', 'b', 'c'], qq3: ['f'], qq4: ['b'], qq5: ['c'], qq6: ['f'] };
export const diagnosticQuiz: QuizSummaryView = { id: 'q-diag', title: 'Placement diagnostic', kind: 'diagnostic', questionCount: 4, timeLimitSeconds: 480, nextAttemptNumber: 1 };
export const sampleSkillChanges: SkillChangeView[] = [
    { skillId: 'for-loops', name: 'For loops', before: 'weak', after: 'learning' },
    { skillId: 'while-loops', name: 'While loops', before: 'unknown', after: 'learning' },
];
export const loopsAttempts: AttemptView[] = [
    { id: 'att-1', attemptNumber: 1, submittedAtLabel: 'Mon 2 Nov, 20:30', score: 50, correct: 3, total: 6 },
    { id: 'att-2', attemptNumber: 2, submittedAtLabel: 'Thu 5 Nov, 21:10', score: 66.67, correct: 4, total: 6 },
];
export const assessmentItems: AssessmentItemView[] = [
    { id: 'q-diag', type: 'quiz', title: 'Placement diagnostic', kind: 'diagnostic', timeLimitSeconds: 480, attemptsLabel: 'Skipped', lastScore: null },
    { id: 'q-loops', type: 'quiz', title: 'Loops practice quiz', kind: 'practice', timeLimitSeconds: 600, attemptsLabel: '2 attempts', lastScore: 66.67 },
    { id: 'q-midterm', type: 'quiz', title: 'Chapters 1–2 test', kind: 'test', timeLimitSeconds: 1800, attemptsLabel: 'Not started', lastScore: null },
    { id: 'as-trace', type: 'assignment', title: 'Trace and fix three short programs', deadlineLabel: 'Fri 13 Nov, 23:59', deadlineState: 'soon', submissionLabel: 'Not submitted', grade: null },
    { id: 'as-vars', type: 'assignment', title: 'Variables worksheet', deadlineLabel: 'Fri 30 Oct, 23:59', deadlineState: 'closed', submissionLabel: 'Graded', grade: 85 },
];
export const assignments: Record<string, AssignmentView> = {
    'as-trace': { id: 'as-trace', title: 'Trace and fix three short programs', deadlineLabel: 'Fri 13 Nov, 23:59', deadlineState: 'soon', lessonTitle: 'Practice: Trace and Fix Short Programs',
        instructions: ['Each of the three programs in the attached file has one loop bug. For each program, write down what it prints now, what it should print, and the one-line fix.', 'Submit a single PDF or text file. You can replace it until the deadline while it is ungraded.'] },
    'as-vars': { id: 'as-vars', title: 'Variables worksheet', deadlineLabel: 'Fri 30 Oct, 23:59', deadlineState: 'closed', lessonTitle: 'Variables and Values',
        instructions: ['Answer the five questions about naming and updating variables.'] },
};
export const varsSubmission = { fileName: 'variables-worksheet-linh.pdf', submittedAtLabel: 'Thu 29 Oct, 18:02', graded: { grade: 85, feedback: 'Clear answers. In question 4, remember that x = x + 1 reads the old value first. Well done overall.', gradedAtLabel: 'Sat 31 Oct, 10:20' } };

export const notifications: NotificationView[] = [
    { id: 'n1', kind: 'recommendation', message: 'New recommendation: review If Statements before continuing with loops.', createdAtLabel: '21:12', dayGroup: 'Today', read: false, targetLabel: COURSE_TITLE },
    { id: 'n2', kind: 'result', message: 'Your result for Loops practice quiz is available.', createdAtLabel: '21:10', dayGroup: 'Today', read: false, targetLabel: COURSE_TITLE },
    { id: 'n3', kind: 'deadline', message: '“Trace and fix three short programs” is due in 8 days.', createdAtLabel: '08:00', dayGroup: 'Today', read: false, targetLabel: COURSE_TITLE },
    { id: 'n4', kind: 'reply', message: 'Instructor Demo replied to a question in For Loops.', createdAtLabel: '20:05', dayGroup: 'Yesterday', read: true, targetLabel: 'For Loops' },
    { id: 'n5', kind: 'content', message: 'A new lesson was published: Strings and Text.', createdAtLabel: '14:30', dayGroup: 'Mon 2 Nov', read: true, targetLabel: COURSE_TITLE },
];

// ── Instructor ─────────────────────────────────────────────────────────────
export const instructorCourses: (InstructorCourseView & { prerequisiteDescription: string })[] = [
    { id: 'c-intro', title: COURSE_TITLE, description: COURSE_DESCRIPTIONS['c-intro'], publication: 'published', lessonCount: 12, learnerCount: 6, prerequisiteDescription: 'No prior programming. Comfortable using a computer and a text editor.' },
    { id: 'c-practice', title: 'Programming Practice', description: COURSE_DESCRIPTIONS['c-practice'], publication: 'hidden', lessonCount: 4, learnerCount: 0, prerequisiteDescription: '' },
];
export const editChapters: ChapterEditView[] = learnerChapters.map((chapter) => ({ id: chapter.id, title: chapter.title, position: chapter.position, lessons: chapter.lessons.map((lesson) => ({
    id: lesson.id, title: lesson.title, position: lesson.position, publication: lesson.id === 'l12' ? 'hidden' : 'published', skillIds: lesson.id === 'l9' ? [] : (lesson.skills ?? []).map((skill) => skill.id),
})) }));
export const lessonDraft = (id: string): LessonDraft => {
    const lesson = learnerChapters.flatMap((chapter) => chapter.lessons).find((item) => item.id === id);
    return { id, title: lesson?.title ?? 'New lesson', content: lesson?.summary ?? '', skillIds: (lesson?.skills ?? []).map((skill) => skill.id), publication: id === 'l12' ? 'hidden' : 'published',
        materials: id === 'l7' ? [{ id: 'm1', kind: 'file', title: 'For loops slides', fileName: 'for-loops-slides.pdf' }, { id: 'm2', kind: 'external_video', title: 'Loops walkthrough', url: 'https://example.com/video/loops' }] : [] };
};
export const skillOptions = learnerSkills.map((skill) => ({ id: skill.id, name: skill.name }));
export const editableSkills: EditableSkill[] = learnerSkills.map((skill, index) => ({ id: skill.id, name: skill.name, description: skill.description ?? '', prerequisiteIds: skill.prerequisiteIds, questionCount: [12, 10, 8, 9, 6, 7, 11, 4, 3, 2, 5][index] }));
export const mappingLessons: MappingLesson[] = editChapters.flatMap((chapter) => chapter.lessons.map((lesson) => ({ id: lesson.id, title: lesson.title, chapterTitle: chapter.title, published: lesson.publication === 'published', skillIds: lesson.skillIds })));
export const questionBank: QuestionDraft[] = [
    ...loopsQuestions.map((question, index): QuestionDraft => ({ id: question.id, kind: question.kind, prompt: question.prompt, code: question.code, skillId: index === 2 ? 'while-loops' : 'for-loops',
        difficulty: (['easy', 'medium', 'easy', 'medium', 'easy', 'easy'] as const)[index], usedInQuizzes: 1, usedInAttempts: index < 4,
        options: question.options.map((option) => ({ id: option.id, label: option.label, correct: loopsAnswerKey[question.id].includes(option.id) })) })),
    { id: 'qb7', kind: 'single_choice', prompt: 'Which line correctly stores the number 5 in a variable named count?', skillId: 'variables', difficulty: 'easy', usedInQuizzes: 0, usedInAttempts: false,
        options: [{ id: 'a', label: 'count = 5', correct: true }, { id: 'b', label: '5 = count', correct: false }, { id: 'c', label: 'count == 5', correct: false }] },
    { id: 'qb8', kind: 'multiple_choice', prompt: 'Which conditions are True when x = 3?', skillId: 'boolean', difficulty: 'hard', usedInQuizzes: 0, usedInAttempts: false,
        options: [{ id: 'a', label: 'x > 1 and x < 5', correct: true }, { id: 'b', label: 'not x == 3', correct: false }, { id: 'c', label: 'x == 3 or x == 4', correct: true }] },
];
export const quizDrafts: QuizDraft[] = [{ id: 'q-loops', title: 'Loops practice quiz', kind: 'practice', timeLimitMinutes: 10, questionIds: loopsQuestions.map((question) => question.id), publication: 'published' }];
export const assignmentDrafts: AssignmentDraft[] = [{ id: 'as-trace', title: 'Trace and fix three short programs', instructions: assignments['as-trace'].instructions.join('\n\n'), deadline: '2026-11-13T23:59', lessonId: 'l9', publication: 'published' }];
export const submissions: SubmissionRow[] = [
    { id: 's1', learnerName: 'Linh Pham', assignmentTitle: 'Trace and fix three short programs', courseTitle: COURSE_TITLE, submittedAtLabel: 'Thu 5 Nov, 22:41', fileName: 'trace-and-fix-linh.pdf' },
    { id: 's2', learnerName: 'Quang Le', assignmentTitle: 'Trace and fix three short programs', courseTitle: COURSE_TITLE, submittedAtLabel: 'Thu 5 Nov, 17:03', fileName: 'loops-fixes.txt' },
    { id: 's3', learnerName: 'Mai Tran', assignmentTitle: 'Trace and fix three short programs', courseTitle: COURSE_TITLE, submittedAtLabel: 'Wed 4 Nov, 09:12', fileName: 'mai-trace.pdf' },
    { id: 's4', learnerName: 'Linh Pham', assignmentTitle: 'Variables worksheet', courseTitle: COURSE_TITLE, submittedAtLabel: 'Thu 29 Oct, 18:02', fileName: 'variables-worksheet-linh.pdf', graded: varsSubmission.graded },
    { id: 's5', learnerName: 'Duc Vo', assignmentTitle: 'Variables worksheet', courseTitle: COURSE_TITLE, submittedAtLabel: 'Fri 30 Oct, 21:47', fileName: 'worksheet.pdf', graded: { grade: 72, feedback: 'Questions 2 and 5 mix up = and ==. Review the Variables lesson.', gradedAtLabel: 'Sat 31 Oct, 10:31' } },
];
const mastery = (mastered: number, learning: number, weak: number, unknown: number) => ({ mastered, learning, weak, unknown });
export const learnerRows: LearnerRow[] = [
    { id: 'u-linh', name: 'Linh Pham', courseTitle: COURSE_TITLE, progress: learnerProgress, latestScore: 66.67, mastery: mastery(3, 3, 1, 4), lastRecommendation: { type: 'remedial', atLabel: 'Thu 5 Nov, 21:12' } },
    { id: 'u-quang', name: 'Quang Le', courseTitle: COURSE_TITLE, progress: { completed: 9, total: 12, percent: 75 }, latestScore: 83.33, mastery: mastery(7, 2, 0, 2), lastRecommendation: { type: 'continue', atLabel: 'Thu 5 Nov, 17:20' } },
    { id: 'u-mai', name: 'Mai Tran', courseTitle: COURSE_TITLE, progress: { completed: 7, total: 12, percent: 58 }, latestScore: 100, mastery: mastery(8, 1, 0, 2), lastRecommendation: { type: 'skip', atLabel: 'Wed 4 Nov, 09:40' } },
    { id: 'u-duc', name: 'Duc Vo', courseTitle: COURSE_TITLE, progress: { completed: 2, total: 12, percent: 17 }, latestScore: 33.33, mastery: mastery(1, 2, 2, 6), lastRecommendation: { type: 'remedial', atLabel: 'Tue 3 Nov, 15:02' } },
    { id: 'u-hoa', name: 'Hoa Bui', courseTitle: COURSE_TITLE, progress: { completed: 0, total: 12, percent: 0 }, latestScore: null, mastery: mastery(0, 0, 0, 11) },
    { id: 'u-tuan', name: 'Tuan Do', courseTitle: COURSE_TITLE, progress: { completed: 5, total: 12, percent: 42 }, latestScore: 50, mastery: mastery(3, 4, 1, 3), lastRecommendation: { type: 'continue', atLabel: 'Mon 2 Nov, 11:18' } },
];
export const recommendationLog: RecommendationLogRow[] = [
    { id: 'r3', atLabel: 'Thu 5 Nov, 21:12', type: 'remedial', targetLesson: 'If Statements', reason: sampleRecommendation.reason },
    { id: 'r2', atLabel: 'Mon 2 Nov, 20:31', type: 'continue', targetLesson: 'For Loops', reason: 'Conditionals is Learning. Continue with the next lesson in course order and practise loops.' },
    { id: 'r1', atLabel: 'Sun 1 Nov, 10:05', type: 'skip', targetLesson: 'Variables and Values', reason: 'Variables is Mastered from the placement diagnostic, so this lesson can be skipped (Test-out).' },
];
export const learnerResults: ResultRow[] = [
    { id: 'rr2', title: 'Loops practice quiz', kindLabel: 'Practice quiz', attempt: 2, submittedAtLabel: 'Thu 5 Nov, 21:10', score: 66.67 },
    { id: 'rr1', title: 'Loops practice quiz', kindLabel: 'Practice quiz', attempt: 1, submittedAtLabel: 'Mon 2 Nov, 20:30', score: 50 },
];
export const qaLessons = [{ id: 'l7', title: 'For Loops', open: 1, total: 2 }, { id: 'l5', title: 'If Statements', open: 0, total: 1 }, { id: 'l11', title: 'Lists', open: 0, total: 0 }];

// ── Administrator ──────────────────────────────────────────────────────────
export const courseMetrics: CourseMetricRow[] = [
    { courseId: 'c-intro', courseTitle: COURSE_TITLE, enrolments: 6, completionRate: 17, averageQuizScore: 64 },
    { courseId: 'c-practice', courseTitle: 'Programming Practice', enrolments: 3, completionRate: 0, averageQuizScore: 71 },
    { courseId: 'c-data', courseTitle: 'Working with Data', enrolments: 2, completionRate: 50, averageQuizScore: null },
];
export const skillDistribution: Record<string, SkillDistributionRow[]> = {
    'c-intro': learnerSkills.slice(0, 8).map((skill, index) => ({ skillId: skill.id, skillName: skill.name, counts: [mastery(5, 1, 0, 0), mastery(4, 1, 1, 0), mastery(5, 0, 0, 1), mastery(2, 3, 1, 0), mastery(1, 3, 1, 1), mastery(1, 2, 0, 3), mastery(1, 2, 2, 1), mastery(0, 1, 1, 4)][index] })),
    'c-practice': learnerSkills.slice(0, 4).map((skill, index) => ({ skillId: skill.id, skillName: skill.name, counts: [mastery(2, 1, 0, 0), mastery(1, 1, 0, 1), mastery(1, 0, 1, 1), mastery(0, 1, 1, 1)][index] })),
    'c-data': [],
};
const userNames: [string, UserRowView['role'], UserRowView['status']][] = [['Linh Pham', 'Learner', 'active'], ['Quang Le', 'Learner', 'active'], ['Mai Tran', 'Learner', 'active'], ['Duc Vo', 'Learner', 'locked'],
    ['Hoa Bui', 'Learner', 'active'], ['Tuan Do', 'Learner', 'active'], ['Instructor Demo', 'Instructor', 'active'], ['Instructor Draft', 'Instructor', 'active'], ['Admin Demo', 'Administrator', 'active'],
    ['Khanh Ngo', 'Learner', 'active'], ['Phuong Dang', 'Learner', 'active'], ['Son Ly', 'Instructor', 'active']];
export const users: UserRowView[] = userNames.map(([displayName, role, status], index) => ({ id: `user-${index + 1}`, displayName, role, status, email: `${displayName.toLowerCase().replace(/\s+/g, '.')}@example.test` }));
export const adminCourses: AdminCourseRow[] = [
    { id: 'c-intro', title: COURSE_TITLE, instructorName: 'Instructor Demo', publication: 'published', enrolments: 6 },
    { id: 'c-practice', title: 'Programming Practice', instructorName: 'Instructor Demo', publication: 'hidden', enrolments: 3 },
    { id: 'c-data', title: 'Working with Data', instructorName: 'Son Ly', publication: 'published', enrolments: 2 },
    { id: 'c-draft', title: 'Instructor Draft', instructorName: null, publication: 'hidden', enrolments: 0 },
];
export const reports: ReportRow[] = [
    { id: 'rep1', targetType: 'Post', targetLabel: 'Post by Khanh Ngo in For Loops', excerpt: 'Just copy the answers from last year, they are the same.', courseTitle: COURSE_TITLE, reporter: 'Mai Tran', reportedAtLabel: 'Thu 5 Nov, 18:22', status: 'open' },
    { id: 'rep2', targetType: 'Lesson', targetLabel: 'Lesson: Strings and Text', excerpt: 'The example output in section 2 is wrong and confusing.', courseTitle: COURSE_TITLE, reporter: 'Quang Le', reportedAtLabel: 'Wed 4 Nov, 11:05', status: 'open' },
    { id: 'rep3', targetType: 'Post', targetLabel: 'Post by Son Ly in Lists', excerpt: 'Off-topic advertisement link.', courseTitle: 'Working with Data', reporter: 'Phuong Dang', reportedAtLabel: 'Mon 2 Nov, 09:40', status: 'resolved', resolvedAtLabel: 'Mon 2 Nov, 12:10' },
    { id: 'rep4', targetType: 'Post', targetLabel: 'Post by Tuan Do in If Statements', excerpt: 'Is elif the same as else if?', courseTitle: COURSE_TITLE, reporter: 'Hoa Bui', reportedAtLabel: 'Sun 1 Nov, 16:00', status: 'dismissed', resolvedAtLabel: 'Sun 1 Nov, 17:45' },
];
const audit = (id: number, day: string, time: string, actor: string, action: string, target: string): AuditEntry => ({ id: `a${id}`, atLabel: `${day}, ${time}`, atSort: `2026-11-0${id < 6 ? 5 : id < 10 ? 4 : 2}T${time}`, actor, action, target });
export const auditEntries: AuditEntry[] = [
    audit(1, 'Thu 5 Nov', '18:40', 'Admin Demo', 'report.hide', 'DiscussionPost 9c1f…'), audit(2, 'Thu 5 Nov', '16:12', 'Admin Demo', 'user.lock', 'User Duc Vo'),
    audit(3, 'Thu 5 Nov', '10:03', 'Admin Demo', 'user.role.change', 'User Son Ly → Instructor'), audit(4, 'Thu 5 Nov', '09:30', 'Admin Demo', 'course.hide', 'Course Programming Practice'),
    audit(5, 'Thu 5 Nov', '08:55', 'Admin Demo', 'report.dismiss', 'DiscussionPost 41aa…'), audit(6, 'Wed 4 Nov', '17:20', 'Admin Demo', 'course.publish', 'Course Working with Data'),
    audit(7, 'Wed 4 Nov', '15:02', 'Admin Demo', 'user.unlock', 'User Hoa Bui'), audit(8, 'Wed 4 Nov', '11:47', 'Admin Demo', 'user.role.change', 'User Instructor Draft → Instructor'),
    audit(9, 'Wed 4 Nov', '09:15', 'Admin Demo', 'report.hide', 'Lesson 77b2…'), audit(10, 'Mon 2 Nov', '12:10', 'Admin Demo', 'report.hide', 'DiscussionPost 3d09…'),
    audit(11, 'Mon 2 Nov', '10:44', 'Admin Demo', 'user.lock', 'User Khanh Ngo'), audit(12, 'Mon 2 Nov', '10:50', 'Admin Demo', 'user.unlock', 'User Khanh Ngo'),
];
