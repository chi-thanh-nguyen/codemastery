import { useState } from 'react';
import type { Dispatch, SetStateAction } from 'react';
import { Box, Typography } from '@mui/material';
import { SegmentedControl } from '../../shared/components/ViewRail';
import type { GuidanceEntryData } from '../../shared/components/GuidanceStack';
import { InstructorCoursesPage } from '../../features/instructor/course-management/pages/InstructorCoursesPage';
import { CourseEditorPage } from '../../features/instructor/course-management/pages/CourseEditorPage';
import type { CourseEditorTab } from '../../features/instructor/course-management/pages/CourseEditorPage';
import { LessonEditorPage } from '../../features/instructor/course-management/pages/LessonEditorPage';
import { QuestionBankPage } from '../../features/instructor/course-management/pages/QuestionBankPage';
import { QuizEditorPage } from '../../features/instructor/course-management/pages/QuizEditorPage';
import { AssignmentEditorPage } from '../../features/instructor/course-management/pages/AssignmentEditorPage';
import { LessonQaPage } from '../../features/instructor/course-management/pages/LessonQaPage';
import type { AssignmentDraft, ChapterEditView, InstructorCourseView, LessonDraft, QuestionDraft, QuizDraft } from '../../features/instructor/course-management/types/courseManagementTypes';
import { SkillGraphEditorPage } from '../../features/instructor/skill-graph-editor/pages/SkillGraphEditorPage';
import type { SkillEditorTab } from '../../features/instructor/skill-graph-editor/pages/SkillGraphEditorPage';
import type { EditableSkill, MappingLesson, ReadinessCheck } from '../../features/instructor/skill-graph-editor/types/skillGraphTypes';
import { SubmissionListPage } from '../../features/instructor/grading/pages/SubmissionListPage';
import type { GradingFilter } from '../../features/instructor/grading/pages/SubmissionListPage';
import { GradeSubmissionPage } from '../../features/instructor/grading/pages/GradeSubmissionPage';
import type { SubmissionRow } from '../../features/instructor/grading/types/gradingTypes';
import { LearnerInsightsPage } from '../../features/instructor/learner-insights/pages/LearnerInsightsPage';
import { LearnerDetailPage } from '../../features/instructor/learner-insights/pages/LearnerDetailPage';
import type { LearnerDetailView } from '../../features/instructor/learner-insights/pages/LearnerDetailPage';
import { LearnerTable } from '../../features/instructor/learner-insights/components/LearnerTable';
import { DiscussionPanel } from '../../features/interaction/components/DiscussionPanel';
import type { DiscussionPostView } from '../../features/interaction/types/interactionTypes';
import { MasteryMapPage } from '../../features/mastery/pages/MasteryMapPage';
import type { MasteryView } from '../../features/mastery/pages/MasteryMapPage';
import * as fx from './fixtures';
import { ContractNote, later, nowLabel, usePreview } from './previewKit';

/** Sample configuration value, shown as configuration, never hard-coded in product code. */
const SAMPLE_MIN_QUESTIONS_PER_SKILL = 3;

export interface InstructorState {
    courses: (InstructorCourseView & { prerequisiteDescription: string })[];
    chapters: ChapterEditView[];
    drafts: Record<string, LessonDraft>;
    questions: QuestionDraft[];
    quizzes: QuizDraft[];
    assignments: AssignmentDraft[];
    skills: EditableSkill[];
    mapping: MappingLesson[];
    adaptiveEnabled: boolean;
    submissions: SubmissionRow[];
    posts: DiscussionPostView[];
}
export const initialInstructorState = (): InstructorState => structuredClone({
    courses: fx.instructorCourses, chapters: fx.editChapters, drafts: {}, questions: fx.questionBank, quizzes: fx.quizDrafts, assignments: fx.assignmentDrafts,
    skills: fx.editableSkills, mapping: fx.mappingLessons, adaptiveEnabled: false, submissions: fx.submissions, posts: fx.discussionPosts,
});

type Go = (screen: string, extra?: Record<string, string | undefined>) => void;
interface Props { state: InstructorState; setState: Dispatch<SetStateAction<InstructorState>>; screen: string; item?: string; tab?: string; go: Go }

/** Preview-only cycle check standing in for the server's rejection (the backend owns cycle validation). */
function createsCycle(skills: EditableSkill[], skillId: string, prerequisiteIds: string[]) {
    const parents = new Map(skills.map((skill) => [skill.id, skill.id === skillId ? prerequisiteIds : skill.prerequisiteIds]));
    const seen = new Set<string>(), stack = [...prerequisiteIds];
    while (stack.length) { const id = stack.pop()!; if (id === skillId) return true; if (seen.has(id)) continue; seen.add(id); stack.push(...(parents.get(id) ?? [])); }
    return false;
}

export function InstructorPreview({ state, setState, screen, item, tab, go }: Props) {
    const { notify } = usePreview();
    const course = state.courses.find((entry) => entry.id === (item ?? 'c-intro')) ?? state.courses[0];
    const attention: GuidanceEntryData[] = [
        { id: 'att-grade', tone: 'butter', icon: 'assignment', type: 'Ungraded submissions', meta: `${state.submissions.filter((row) => !row.graded).length} waiting`, body: <Typography>Trace and fix three short programs</Typography>, action: { label: 'Open grading', onClick: () => go('grading') } },
        { id: 'att-qa', tone: 'sky', icon: 'message', type: 'Unanswered question', meta: 'For Loops', body: <Typography>“Can I change the list while looping over it?”</Typography>, action: { label: 'Answer', onClick: () => go('course-editor', { item: 'c-intro', tab: 'qa' }) } },
        { id: 'att-adaptive', tone: 'lilac', icon: 'route', type: 'Adaptive mode not ready', meta: fx.COURSE_TITLE, body: <Typography>One published lesson has no skill, and some skills need more questions.</Typography>, action: { label: 'Review readiness', onClick: () => go('course-editor', { item: 'c-intro', tab: 'skills' }) } },
    ];
    if (screen === 'teaching') return <InstructorCoursesPage instructorName={fx.sampleInstructor.name} courses={state.courses} attention={attention} onOpen={(id) => go('course-editor', { item: id })}
        onCreate={() => { const id = `c-new-${state.courses.length + 1}`; setState((current) => ({ ...current, courses: [...current.courses, { id, title: 'Untitled course', description: 'Add a description in Settings.', publication: 'hidden', lessonCount: 0, learnerCount: 0, prerequisiteDescription: '' }] })); notify('Course created as Hidden (sample).'); go('course-editor', { item: id, tab: 'settings' }); }} />;

    if (screen === 'course-editor') {
        const current = (tab as CourseEditorTab) ?? 'content';
        const chapters = course.id === 'c-intro' ? state.chapters : [];
        return <CourseEditorPage course={course} tab={current} onTabChange={(next) => go('course-editor', { item: course.id, tab: next })} back={{ label: 'Teaching', href: '#', onClick: () => go('teaching') }}
            status={<ContractNote>Sample authoring. Changes stay in this preview; Course and Assessment authoring contracts are pending.</ContractNote>}
            chapters={chapters} onEditLesson={(id) => go('lesson-editor', { item: id })}
            onMoveLesson={(chapterId, lessonId, direction) => { setState((value) => ({ ...value, chapters: value.chapters.map((chapter) => {
                if (chapter.id !== chapterId) return chapter; const list = [...chapter.lessons], index = list.findIndex((lesson) => lesson.id === lessonId);
                [list[index], list[index + direction]] = [list[index + direction], list[index]];
                const base = Math.min(...chapter.lessons.map((lesson) => lesson.position));
                return { ...chapter, lessons: list.map((lesson, position) => ({ ...lesson, position: base + position })) };
            }) })); const title = chapters.flatMap((chapter) => chapter.lessons).find((lesson) => lesson.id === lessonId)?.title; notify(`Moved ${title} ${direction < 0 ? 'up' : 'down'}.`); }}
            onAddLesson={(chapterId) => { const id = `l-new-${Date.now()}`; setState((value) => ({ ...value, chapters: value.chapters.map((chapter) => chapter.id === chapterId ? { ...chapter, lessons: [...chapter.lessons, { id, title: 'New lesson', position: Math.max(0, ...value.chapters.flatMap((c) => c.lessons.map((l) => l.position))) + 1, publication: 'hidden', skillIds: [] }] } : chapter) })); go('lesson-editor', { item: id }); }}
            onAddChapter={() => setState((value) => ({ ...value, chapters: [...value.chapters, { id: `ch-new-${Date.now()}`, title: 'New chapter', position: value.chapters.length + 1, lessons: [] }] }))}
            onPublicationChange={(publication) => { setState((value) => ({ ...value, courses: value.courses.map((entry) => entry.id === course.id ? { ...entry, publication } : entry) })); notify(publication === 'published' ? 'Course published (sample).' : 'Course hidden (sample).'); }}
            onSaveSettings={(settings) => { setState((value) => ({ ...value, courses: value.courses.map((entry) => entry.id === course.id ? { ...entry, ...settings } : entry) })); notify('Course settings saved (sample).'); }}
            tabContent={{ skills: <SkillsTab state={state} setState={setState} />, assessments: <AssessmentsTab state={state} setState={setState} />,
                learners: <LearnerTable rows={fx.learnerRows} onOpen={(id) => go('learner', { item: id })} />, qa: <QaTab state={state} setState={setState} /> }} />;
    }

    if (screen === 'lesson-editor') {
        const id = item ?? 'l7';
        const row = state.chapters.flatMap((chapter) => chapter.lessons).find((lesson) => lesson.id === id);
        const initial = state.drafts[id] ?? { ...fx.lessonDraft(id), title: row?.title ?? 'New lesson', publication: row?.publication ?? 'hidden', skillIds: row?.skillIds ?? [] };
        return <LessonEditorPage key={id} courseTitle={fx.COURSE_TITLE} initial={initial} skills={fx.skillOptions} back={{ label: 'Course content', href: '#', onClick: () => go('course-editor', { item: 'c-intro' }) }}
            onLeave={() => go('course-editor', { item: 'c-intro' })}
            notice={<ContractNote>Sample lesson editor. Saving updates this preview only.</ContractNote>}
            onSave={(draft) => { setState((value) => ({ ...value, drafts: { ...value.drafts, [id]: draft }, chapters: value.chapters.map((chapter) => ({ ...chapter, lessons: chapter.lessons.map((lesson) => lesson.id === id ? { ...lesson, title: draft.title, publication: draft.publication, skillIds: draft.skillIds } : lesson) })),
                mapping: value.mapping.map((lesson) => lesson.id === id ? { ...lesson, title: draft.title, skillIds: draft.skillIds, published: draft.publication === 'published' } : lesson) })); notify('Lesson saved (sample).'); }} />;
    }

    if (screen === 'grading') return <GradingList state={state} go={go} />;
    if (screen === 'grade') {
        const submission = state.submissions.find((row) => row.id === item) ?? state.submissions[0];
        return <GradeSubmissionPage key={submission.id} submission={submission} back={{ label: 'Grading', href: '#', onClick: () => go('grading') }} onDownload={() => notify('Download is pending the storage contract (CT09).')}
            notice={<ContractNote>Sample grading. Saving updates this preview only; in production the learner is notified by the Interaction module.</ContractNote>}
            onSave={(grade, feedback) => { setState((value) => ({ ...value, submissions: value.submissions.map((row) => row.id === submission.id ? { ...row, graded: { grade, feedback, gradedAtLabel: nowLabel } } : row) })); notify('Grade saved (sample).'); }} />;
    }
    if (screen === 'learners') return <LearnerInsightsPage rows={fx.learnerRows} courses={[fx.COURSE_TITLE]} onOpen={(id) => go('learner', { item: id })} />;
    if (screen === 'learner') return <LearnerDetail id={item ?? 'u-linh'} go={go} />;
    return null;
}

function GradingList({ state, go }: { state: InstructorState; go: Go }) {
    const [filter, setFilter] = useState<GradingFilter>('ungraded');
    return <SubmissionListPage rows={state.submissions} filter={filter} onFilterChange={setFilter} onOpen={(id) => go('grade', { item: id })} />;
}

function LearnerDetail({ id, go }: { id: string; go: Go }) {
    const learner = fx.learnerRows.find((row) => row.id === id) ?? fx.learnerRows[0];
    const [view, setView] = useState<LearnerDetailView>('progress'), [mapView, setMapView] = useState<MasteryView>('graph'), [selected, setSelected] = useState<string | null>(null);
    return <LearnerDetailPage learner={learner} view={view} onViewChange={setView} results={learner.id === 'u-linh' ? fx.learnerResults : []} log={learner.id === 'u-linh' ? fx.recommendationLog : []}
        back={{ label: 'Learners', href: '#', onClick: () => go('learners') }}
        progressDetail={<ContractNote>Sample learner data. Scoped insight reads are pending; only learners enrolled in your own courses would appear.</ContractNote>}
        masteryMap={<MasteryMapPage embedded courseTitle={fx.COURSE_TITLE} skills={fx.learnerSkills} view={mapView} onViewChange={setMapView} selectedId={selected} onSelect={setSelected} />} />;
}

function SkillsTab({ state, setState }: { state: InstructorState; setState: Props['setState'] }) {
    const { notify } = usePreview();
    const [tab, setTab] = useState<SkillEditorTab>('graph'), [selected, setSelected] = useState<string | null>(null), [adding, setAdding] = useState(false), [error, setError] = useState<string | null>(null);
    const published = state.mapping.filter((lesson) => lesson.published), unmapped = published.filter((lesson) => lesson.skillIds.length === 0);
    const thin = state.skills.filter((skill) => skill.questionCount < SAMPLE_MIN_QUESTIONS_PER_SKILL);
    const readiness: ReadinessCheck[] = [
        { id: 'mapped', label: 'Every published lesson is mapped to at least one skill', met: unmapped.length === 0, detail: unmapped.length ? `Not mapped: ${unmapped.map((lesson) => lesson.title).join(', ')}` : undefined },
        { id: 'questions', label: 'Every skill has the minimum number of questions', met: thin.length === 0, detail: `Sample configuration: ${SAMPLE_MIN_QUESTIONS_PER_SKILL} per skill.${thin.length ? ` Below minimum: ${thin.map((skill) => skill.name).join(', ')}` : ''}` },
        { id: 'acyclic', label: 'The skill graph has no cycles', met: true },
    ];
    return <SkillGraphEditorPage courseTitle={fx.COURSE_TITLE} tab={tab} onTabChange={setTab} skills={state.skills} selectedId={adding ? null : selected} onSelect={(id) => { setAdding(false); setError(null); setSelected(id); }}
        adding={adding} onAdd={() => { setSelected(null); setError(null); setAdding(true); }} serverError={error} lessons={state.mapping}
        status={<ContractNote>Sample editor. Cycle rejection and readiness are decided by the server; this preview imitates its responses (CT40 pending).</ContractNote>}
        onSaveSkill={(input) => {
            const id = input.id ?? `skill-${Date.now()}`;
            if (createsCycle(state.skills, id, input.prerequisiteIds)) { setError('Sample server response: this prerequisite would create a cycle. A skill cannot depend on itself through other skills.'); return; }
            setError(null);
            setState((value) => ({ ...value, skills: input.id ? value.skills.map((skill) => skill.id === id ? { ...skill, ...input, id } : skill) : [...value.skills, { id, name: input.name, description: input.description, prerequisiteIds: input.prerequisiteIds, questionCount: 0 }] }));
            setAdding(false); setSelected(id); notify(input.id ? 'Skill saved (sample).' : 'Skill added (sample).');
        }}
        onMappingChange={(lessonId, skillIds) => setState((value) => ({ ...value, mapping: value.mapping.map((lesson) => lesson.id === lessonId ? { ...lesson, skillIds } : lesson) }))}
        readiness={readiness} adaptiveEnabled={state.adaptiveEnabled} onAdaptiveToggle={(next) => { setState((value) => ({ ...value, adaptiveEnabled: next })); notify(next ? 'Adaptive mode on (sample).' : 'Adaptive mode off (sample).'); }} />;
}

function AssessmentsTab({ state, setState }: { state: InstructorState; setState: Props['setState'] }) {
    const { notify } = usePreview();
    const [view, setView] = useState<'bank' | 'quiz' | 'assignment'>('bank');
    return <Box>
        <Box sx={{ mb: 3 }}><SegmentedControl label="Assessment authoring" value={view} onChange={setView} options={[{ value: 'bank', label: 'Question bank' }, { value: 'quiz', label: 'Quizzes' }, { value: 'assignment', label: 'Assignments' }]} /></Box>
        {view === 'bank' && <QuestionBankPage questions={state.questions} skills={fx.skillOptions} onSave={(question) => {
            setState((value) => ({ ...value, questions: question.id ? value.questions.map((entry) => entry.id === question.id ? { ...question, usedInAttempts: false } : entry) : [...value.questions, { ...question, id: `qb-${Date.now()}` }] }));
            notify(question.usedInAttempts ? 'Saved as a new version (sample).' : 'Question saved (sample).'); }} />}
        {view === 'quiz' && <QuizEditorPage initial={state.quizzes[0]} questions={state.questions} skills={fx.skillOptions} onCancel={() => setView('bank')}
            onSave={(quiz) => { setState((value) => ({ ...value, quizzes: [quiz] })); notify('Quiz saved (sample).'); }} />}
        {view === 'assignment' && <AssignmentEditorPage initial={state.assignments[0]} lessons={state.mapping.map((lesson) => ({ id: lesson.id, title: lesson.title }))} timeZoneLabel={fx.SAMPLE_TIMEZONE}
            onCancel={() => setView('bank')} onSave={(assignment) => { setState((value) => ({ ...value, assignments: [assignment] })); notify('Assignment saved (sample).'); }} />}
    </Box>;
}

function QaTab({ state, setState }: { state: InstructorState; setState: Props['setState'] }) {
    const { notify } = usePreview();
    const [lesson, setLesson] = useState('l7');
    const title = fx.qaLessons.find((entry) => entry.id === lesson)?.title ?? '';
    return <LessonQaPage lessons={fx.qaLessons.map((entry) => entry.id === 'l7' ? { ...entry, open: state.posts.filter((post) => post.replies.length === 0).length } : entry)} selectedId={lesson} onSelect={setLesson}
        discussion={<DiscussionPanel title={`Questions in ${title}`} lessonTitle={title} posts={lesson === 'l7' ? state.posts : []} canReply
            onReply={(postId, text) => { setState((value) => ({ ...value, posts: value.posts.map((post) => post.id === postId ? { ...post, replies: [...post.replies, { id: `${postId}-r${Date.now()}`, author: fx.sampleInstructor.name, role: 'Instructor', createdAtLabel: nowLabel, body: text }] } : post) })); later(() => notify('Reply posted. The learner will be notified (sample).'), 0); }} />} />;
}
