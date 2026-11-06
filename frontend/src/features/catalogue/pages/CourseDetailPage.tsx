import { useEffect, useState } from 'react';
import type { ReactNode } from 'react';
import { Accordion, AccordionDetails, AccordionSummary, Box, Button, Typography } from '@mui/material';
import { PageHeader } from '../../../shared/components/PageHeader';
import { LoadingView, ErrorView } from '../../../shared/components/StatusViews';
import { PresentationIcon } from '../../../shared/components/PresentationIcon';
import { PathSpine } from '../../../shared/components/PathSpine';
import { FactList, Tile } from '../../../shared/components/Tile';
import { Page } from '../../../layouts/DashboardLayout';
import { getPublishedCourseById } from '../api/catalogueApi';
import { ApiError } from '../../../shared/types/apiTypes';
import type { CourseDetail, SyllabusChapter } from '../types/catalogueTypes';
import { instructorLabel } from '../components/CourseCard';
import { color, radius } from '../../../app/theme/theme';

export interface CourseDetailPageProps {
    courseId: string;
    onBack: () => void;
    membershipActions?: ReactNode;
}

const back = (onBack: () => void) => ({ label: 'Catalogue', href: '/courses', onClick: onBack });

/** S4 Course detail: published description, Instructor, prerequisites and syllabus headings (FE07). */
export function CourseDetailPage({ courseId, onBack, membershipActions }: CourseDetailPageProps) {
    const [course, setCourse] = useState<CourseDetail | null>(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<ApiError | string | null>(null);
    const [revision, retry] = useState(0);
    useEffect(() => {
        const controller = new AbortController();
        let current = true;
        setLoading(true); setError(null); setCourse(null);
        getPublishedCourseById(courseId, { signal: controller.signal, isCurrent: () => current }).then((data) => {
            if (current) setCourse(data);
        }).catch((error: unknown) => {
            if (current && !(error instanceof ApiError && error.kind === 'cancelled')) setError(error instanceof ApiError ? error : 'Failed to load course details.');
        }).finally(() => { if (current) setLoading(false); });
        return () => { current = false; controller.abort(); };
    }, [courseId, revision]);

    if (loading) return <Page><PageHeader title="Course details" back={back(onBack)} /><LoadingView message="Loading course syllabus..." variant="block" /></Page>;
    if (error || !course) {
        const missing = error instanceof ApiError && error.status === 404;
        return <Page><PageHeader title={missing ? 'Course not available' : 'Course details'} back={back(onBack)} />
            <ErrorView title={missing ? 'Course not available' : "Couldn't load this course"}
                message={missing ? "This course doesn't exist or isn't published." : error instanceof ApiError ? error.message : error ?? 'Failed to load course details.'}
                onRetry={() => retry((value) => value + 1)} action={<Button variant="outlined" size="small" onClick={onBack}>Back to catalogue</Button>} />
        </Page>;
    }
    const lessonCount = course.syllabus.reduce((total, chapter) => total + chapter.lessons.length, 0);
    // Keep the PageHeader at the same tree position in every state so route focus stays on the h1.
    return <Page>
            <PageHeader title={course.title} back={back(onBack)}
                meta={<Typography sx={{ color: 'text.secondary' }}>Instructor · {instructorLabel(course.instructorDisplayName)}</Typography>}
                actions={membershipActions} />
            <Box sx={{ display: 'grid', gap: 3, gridTemplateColumns: { xs: 'minmax(0, 1fr)', md: 'minmax(0, 2fr) minmax(260px, 1fr)' }, alignItems: 'start', overflowWrap: 'anywhere' }}>
                <Box sx={{ minWidth: 0, display: 'grid', gap: 3 }}>
                    <Tile component="section" aria-labelledby="course-about" padding={{ xs: 3, sm: 4 }}>
                        <Typography variant="h2" id="course-about" sx={{ mb: 2 }}>About this course</Typography>
                        <Typography variant="reading" component="p" sx={{ m: 0, maxWidth: '70ch', whiteSpace: 'pre-line' }}>{course.description}</Typography>
                    </Tile>
                    <Tile component="section" aria-labelledby="course-syllabus" padding={{ xs: 3, sm: 4 }}>
                        <Typography variant="h2" id="course-syllabus" sx={{ mb: .5 }}>Syllabus</Typography>
                        <Typography variant="body2" sx={{ color: 'text.secondary', mb: 3 }}>Published chapters and lessons, in course order.</Typography>
                        {course.syllabus.length === 0
                            ? <Typography sx={{ color: 'text.secondary' }}>No syllabus chapters are published yet.</Typography>
                            : course.syllabus.map((chapter, index) => <SyllabusChapterView key={chapter.id} courseId={course.id} chapter={chapter} defaultExpanded={index === 0} />)}
                    </Tile>
                </Box>
                <Box component="section" aria-labelledby="course-facts" sx={{ bgcolor: color.fill150, borderRadius: `${radius.lg}px`, p: { xs: 3, sm: 3.5 }, minWidth: 0 }}>
                    <Typography variant="h4" component="h2" id="course-facts" sx={{ mb: 2.5 }}>Course facts</Typography>
                    <FactList items={[
                        { label: 'Prerequisites', value: course.prerequisiteDescription ?? 'None specified' },
                        { label: 'Instructor', value: instructorLabel(course.instructorDisplayName) },
                        { label: 'Published content', value: `${course.syllabus.length} ${course.syllabus.length === 1 ? 'chapter' : 'chapters'} · ${lessonCount} ${lessonCount === 1 ? 'lesson' : 'lessons'}` },
                    ]} />
                </Box>
            </Box>
    </Page>;
}

function SyllabusChapterView({ courseId, chapter, defaultExpanded }: { courseId: string; chapter: SyllabusChapter; defaultExpanded: boolean }) {
    const base = `course-${courseId}-chapter-${chapter.id}`;
    return <Accordion defaultExpanded={defaultExpanded} sx={{ borderTop: `1px solid ${color.line200}`, '&:first-of-type': { borderTop: 0 } }}>
        <AccordionSummary id={`${base}-summary`} aria-controls={`${base}-panel`} expandIcon={<PresentationIcon name="chevron-down" />}
            sx={{ px: 0, minHeight: 56, '&.Mui-focusVisible': { bgcolor: 'transparent', outline: `2px solid ${color.brand600}`, outlineOffset: 2, borderRadius: `${radius.sm}px` } }}>
            <Typography component="span" variant="h4">Chapter {chapter.position}: {chapter.title}</Typography>
            <Typography component="span" variant="caption" sx={{ ml: 1.5, color: 'text.secondary', alignSelf: 'center' }}>{chapter.lessons.length} {chapter.lessons.length === 1 ? 'lesson' : 'lessons'}</Typography>
        </AccordionSummary>
        <AccordionDetails sx={{ px: 0, pt: 0, pb: 2 }}>
            {chapter.lessons.length === 0 ? <Typography variant="body2" sx={{ color: 'text.secondary' }}>No lessons published in this chapter yet.</Typography>
                : <PathSpine density="rows" listLabel={() => `Lessons in ${chapter.title}`} sections={[{ id: chapter.id, items: chapter.lessons.map((lesson) => ({
                    id: lesson.id, node: 'static' as const,
                    content: <Typography sx={{ py: 1 }}>{`${lesson.position}. ${lesson.title}`}</Typography>,
                })) }]} />}
        </AccordionDetails>
    </Accordion>;
}
