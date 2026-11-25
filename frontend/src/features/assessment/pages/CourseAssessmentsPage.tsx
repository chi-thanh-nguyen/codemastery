import type { ReactNode } from 'react';
import { Box, Typography } from '@mui/material';
import { Page } from '../../../layouts/DashboardLayout';
import { PageHeader } from '../../../shared/components/PageHeader';
import type { Crumb } from '../../../shared/components/PageHeader';
import { SegmentedControl } from '../../../shared/components/ViewRail';
import { DataTable } from '../../../shared/components/DataTable';
import { EmptyView } from '../../../shared/components/StatusViews';
import { StatusPill } from '../../../shared/components/StatusPill';
import { CircleAction } from '../../../shared/components/Tile';
import { quizKindLabel } from '../types/assessmentTypes';
import type { AssessmentItemView } from '../types/assessmentTypes';
import { formatClock } from '../components/QuizTimer';
import { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router';
import { Button } from '@mui/material';
import { ErrorView, LoadingView, SystemStateView } from '../../../shared/components/StatusViews';
import { useAuth } from '../../auth/hooks/useAuth';
import { listLearnerQuizzes } from '../api/quizApi';
import type { B3QuizSummary } from '../types/assessmentTypes';
import { ApiError } from '../../../shared/types/apiTypes';

export type AssessmentFilter = 'all' | 'quiz' | 'assignment';
export interface CourseAssessmentsPageProps {
    courseTitle: string;
    items: AssessmentItemView[];
    filter: AssessmentFilter;
    onFilterChange: (filter: AssessmentFilter) => void;
    onOpen: (item: AssessmentItemView) => void;
    sectionNav?: ReactNode;
    back?: Crumb;
}

const deadlineTone = { open: 'neutral', soon: 'butter', closed: 'rose' } as const;

/** S10 Course assessments: published quizzes and assignments for enrolled learners (US-12). */
export function CourseAssessmentsPage({ courseTitle, items, filter, onFilterChange, onOpen, sectionNav, back }: CourseAssessmentsPageProps) {
    const rows = items.filter((item) => filter === 'all' || item.type === filter);
    return <Page>
        <PageHeader title="Assessments" subtitle={`${courseTitle} · Published quizzes and assignments.`} back={back} />
        {sectionNav && <Box sx={{ mb: 4 }}>{sectionNav}</Box>}
        <Box sx={{ mb: 2 }}><SegmentedControl label="Assessment type" value={filter} onChange={onFilterChange}
            options={[{ value: 'all', label: 'All', count: items.length }, { value: 'quiz', label: 'Quizzes', count: items.filter((i) => i.type === 'quiz').length }, { value: 'assignment', label: 'Assignments', count: items.filter((i) => i.type === 'assignment').length }]} /></Box>
        <DataTable label="Assessments" rows={rows} getRowId={(item) => item.id}
            empty={<EmptyView icon="quiz" title="No assessments here yet" message="Published quizzes and assignments for this course will appear here." />}
            columns={[
                { key: 'name', header: 'Name', primary: true, render: (item) => <Box component="a" href="#" onClick={(event) => { event.preventDefault(); onOpen(item); }} sx={{ color: 'text.primary', fontWeight: 600, '&:hover': { textDecoration: 'underline' } }}>{item.title}</Box> },
                { key: 'type', header: 'Type', render: (item) => item.type === 'quiz' ? quizKindLabel[item.kind] : 'Assignment' },
                {
                    key: 'limit', header: 'Time limit or deadline', render: (item) => item.type === 'quiz' ? <span className="cm-tabular">{formatClock(item.timeLimitSeconds)}</span>
                        : <StatusPill size="tag" tone={deadlineTone[item.deadlineState]} icon="calendar">{item.deadlineState === 'closed' ? 'Closed' : item.deadlineLabel}</StatusPill>
                },
                { key: 'status', header: 'Attempts or submission', render: (item) => item.type === 'quiz' ? item.attemptsLabel : item.submissionLabel },
                {
                    key: 'score', header: 'Last score or grade', align: 'right', render: (item) => {
                        const value = item.type === 'quiz' ? item.lastScore : item.grade;
                        return <Typography component="span" className="cm-tabular" sx={{ fontWeight: 600 }}>{value === undefined || value === null ? '—' : item.type === 'quiz' ? `${value}%` : `${value} / 100`}</Typography>;
                    }
                },
            ]}
            actions={(item) => <CircleAction label={item.type === 'quiz' ? `Open ${item.title}` : `Open assignment ${item.title}`} icon="arrow-right" variant="ink" onClick={() => onOpen(item)} />} />
    </Page>;
}

export const CURRENT_DOC_INSTANCE = `${Date.now()}-${Math.random().toString(36).slice(2)}`;

export function LiveCourseAssessmentsPage({ courseId, courseTitle }: { courseId: string; courseTitle: string }) {
    const auth = useAuth();
    const navigate = useNavigate();
    const [tab, setTab] = useState<'quizzes' | 'assignments'>('quizzes');
    const [quizzes, setQuizzes] = useState<B3QuizSummary[]>([]);
    const [loading, setLoading] = useState(true);
    const [errorStatus, setErrorStatus] = useState<number | null>(null);
    const [errorMessage, setErrorMessage] = useState<string | null>(null);
    const [revision, retry] = useState(0);

    useEffect(() => {
        let alive = true;
        const controller = new AbortController();
        const gen = auth.generation;
        setLoading(true);
        setErrorStatus(null);
        setErrorMessage(null);

        listLearnerQuizzes(courseId, 0, 100, {
            signal: controller.signal,
            isCurrent: () => alive && auth.generation === gen,
        })
            .then((res) => {
                if (!alive || auth.generation !== gen) return;
                setQuizzes(res.content);
            })
            .catch((err: unknown) => {
                if (!alive || auth.generation !== gen || (err instanceof ApiError && err.kind === 'cancelled')) return;
                if (err instanceof ApiError) {
                    setErrorStatus(err.status ?? null);
                    setErrorMessage(err.message);
                } else {
                    setErrorMessage("Couldn't load assessments. Try again.");
                }
            })
            .finally(() => {
                if (alive && auth.generation === gen) setLoading(false);
            });

        return () => {
            alive = false;
            controller.abort();
        };
    }, [courseId, auth.generation, revision]);

    if (errorStatus === 403) {
        return (
            <Page>
                <SystemStateView
                    icon="lock"
                    title="Join this course to see assessments"
                    message="Your membership is required to access course quizzes and assignments."
                    actions={
                        <Button component={Link} to={`/courses/${encodeURIComponent(courseId)}`} variant="contained">
                            View course details
                        </Button>
                    }
                />
            </Page>
        );
    }

    if (errorStatus === 404) {
        return (
            <Page>
                <SystemStateView
                    icon="search"
                    title="Course not available"
                    message="This course doesn't exist or isn't published."
                    actions={
                        <Button component={Link} to="/courses" variant="contained">
                            Back to catalogue
                        </Button>
                    }
                />
            </Page>
        );
    }

    const openQuiz = (quiz: B3QuizSummary) => {
        const path =
            quiz.kind === 'diagnostic'
                ? `/courses/${encodeURIComponent(courseId)}/diagnostic`
                : `/courses/${encodeURIComponent(courseId)}/quizzes/${encodeURIComponent(quiz.id)}`;
        navigate(path, { state: { docInstance: CURRENT_DOC_INSTANCE, quizSummary: quiz } });
    };

    return (
        <Page>
            <PageHeader
                title="Assessments"
                subtitle={`${courseTitle} · Published quizzes and assignments.`}
                back={{ label: courseTitle, href: `/courses/${encodeURIComponent(courseId)}/learn` }}
            />
            <Box sx={{ mb: 3 }}>
                <SegmentedControl
                    label="Assessment type"
                    value={tab}
                    onChange={setTab}
                    options={[
                        { value: 'quizzes', label: 'Quizzes' },
                        { value: 'assignments', label: 'Assignments' },
                    ]}
                />
            </Box>

            {tab === 'assignments' ? (
                <EmptyView
                    icon="assignment"
                    title="Assignments view"
                    message="Assignment list integration is tracked in FE11."
                />
            ) : loading ? (
                <EmptyView icon="quiz" title="Loading quizzes…" message="Please wait while published quizzes load." />
            ) : errorMessage ? (
                <ErrorView title="Couldn't load quizzes" message={errorMessage} onRetry={() => retry((r) => r + 1)} />
            ) : (
                <DataTable
                    label="Quizzes"
                    rows={quizzes}
                    getRowId={(q) => q.id}
                    empty={<EmptyView icon="quiz" title="No quizzes published yet" message="Published quizzes for this course will appear here." />}
                    columns={[
                        {
                            key: 'name',
                            header: 'Name',
                            primary: true,
                            render: (q) => (
                                <Box
                                    component="a"
                                    href={q.kind === 'diagnostic' ? `/courses/${encodeURIComponent(courseId)}/diagnostic` : `/courses/${encodeURIComponent(courseId)}/quizzes/${encodeURIComponent(q.id)}`}
                                    onClick={(e) => {
                                        e.preventDefault();
                                        openQuiz(q);
                                    }}
                                    sx={{ color: 'text.primary', fontWeight: 600, '&:hover': { textDecoration: 'underline' } }}
                                >
                                    {q.title}
                                </Box>
                            ),
                        },
                        {
                            key: 'kind',
                            header: 'Kind',
                            render: (q) => <StatusPill size="tag" tone="neutral">{quizKindLabel[q.kind]}</StatusPill>,
                        },
                        {
                            key: 'questions',
                            header: 'Questions',
                            align: 'right',
                            render: (q) => <span className="cm-tabular">{q.questionCount}</span>,
                        },
                        {
                            key: 'limit',
                            header: 'Time limit',
                            render: (q) => <span className="cm-tabular">{formatClock(q.timeLimitSeconds)}</span>,
                        },
                    ]}
                    actions={(q) => (
                        <Button variant="text" size="small" onClick={() => openQuiz(q)}>
                            Open
                        </Button>
                    )}
                />
            )}
        </Page>
    );
}