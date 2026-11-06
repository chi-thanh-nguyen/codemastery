import { Box, Button } from '@mui/material';
import { Page } from '../../../../layouts/DashboardLayout';
import { PageHeader } from '../../../../shared/components/PageHeader';
import { GuidanceStack } from '../../../../shared/components/GuidanceStack';
import type { GuidanceEntryData } from '../../../../shared/components/GuidanceStack';
import { CourseTile } from '../../../../shared/components/Tile';
import { EmptyView } from '../../../../shared/components/StatusViews';
import { PresentationIcon } from '../../../../shared/components/PresentationIcon';
import { PublicationPill } from '../components/PublishToggle';
import type { InstructorCourseView } from '../types/courseManagementTypes';

export interface InstructorCoursesPageProps { instructorName: string; courses: InstructorCourseView[]; attention: GuidanceEntryData[]; onOpen: (courseId: string) => void; onCreate: () => void }

/** S17 Teaching home (Proposed route): owned courses and what needs attention. */
export function InstructorCoursesPage({ instructorName, courses, attention, onOpen, onCreate }: InstructorCoursesPageProps) {
    return <Page aside={<GuidanceStack title="Needs attention" entries={attention} emptyText="Nothing else needs your attention." />} asideLabel="Needs attention">
        <PageHeader title="Teaching" subtitle={`Courses you own, ${instructorName}. Learners see only published content.`}
            actions={<Button variant="contained" onClick={onCreate} startIcon={<PresentationIcon name="plus" />}>Create course</Button>} />
        {courses.length === 0 ? <EmptyView icon="book" title="You don't own a course yet" message="Create a course, add chapters and lessons, then publish it when it's ready." action={<Button variant="contained" onClick={onCreate}>Create course</Button>} />
            : <Box component="ul" aria-label="Your courses" sx={{ listStyle: 'none', m: 0, p: 0, display: 'grid', gap: 3, gridTemplateColumns: { xs: 'minmax(0,1fr)', sm: 'repeat(2, minmax(0, 1fr))' } }}>
                {courses.map((course) => <Box component="li" key={course.id} sx={{ minWidth: 0 }}>
                    <CourseTile title={course.title} description={course.description} href={`#course-${course.id}`} onOpen={() => onOpen(course.id)}
                        byline={`${course.lessonCount} lessons${course.learnerCount !== undefined ? ` · ${course.learnerCount} enrolled learners` : ''}`}
                        status={<PublicationPill publication={course.publication} />} />
                </Box>)}
            </Box>}
    </Page>;
}
