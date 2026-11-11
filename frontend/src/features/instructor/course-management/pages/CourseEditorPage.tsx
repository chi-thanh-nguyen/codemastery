import { useId } from 'react';
import { Box, TextField, Button } from '@mui/material';
import { useState } from 'react';
import type { ReactNode } from 'react';
import { Page } from '../../../../layouts/DashboardLayout';
import { PageHeader } from '../../../../shared/components/PageHeader';
import type { Crumb } from '../../../../shared/components/PageHeader';
import { SegmentedControl } from '../../../../shared/components/ViewRail';
import { Tile } from '../../../../shared/components/Tile';
import { ChapterList } from '../components/ChapterList';
import type { ChapterListProps } from '../components/ChapterList';
import { PublishToggle } from '../components/PublishToggle';
import type { InstructorCourseView, Publication } from '../types/courseManagementTypes';

export type CourseEditorTab = 'content' | 'skills' | 'assessments' | 'learners' | 'qa' | 'settings';
export interface CourseEditorPageProps extends Omit<ChapterListProps, never> {
    course: InstructorCourseView & { prerequisiteDescription: string };
    tab: CourseEditorTab;
    onTabChange: (tab: CourseEditorTab) => void;
    onPublicationChange: (next: Publication) => void;
    onSaveSettings: (settings: { title: string; description: string; prerequisiteDescription: string }) => void;
    /** Tabs owned by other Instructor features are composed by the caller. */
    tabContent?: Partial<Record<Exclude<CourseEditorTab, 'content' | 'settings'>, ReactNode>>;
    back?: Crumb;
    status?: ReactNode;
}

/** S18 Course editor: structure, publication and course settings for an owned course (US-10). */
export function CourseEditorPage({ course, tab, onTabChange, onPublicationChange, onSaveSettings, tabContent = {}, back, status, ...chapterProps }: CourseEditorPageProps) {
    const regionId = `${useId()}-section`;
    return <Page>
        <PageHeader title={course.title} back={back} actions={<PublishToggle publication={course.publication} itemLabel="course" onChange={onPublicationChange} />} />
        {status}
        <Box sx={{ mb: 4 }}><SegmentedControl label="Course editor sections" value={tab} onChange={onTabChange} controls={regionId}
            options={[{ value: 'content', label: 'Content' }, { value: 'skills', label: 'Skills' }, { value: 'assessments', label: 'Assessments' }, { value: 'learners', label: 'Learners' }, { value: 'qa', label: 'Q&A' }, { value: 'settings', label: 'Settings' }]} /></Box>
        <Box role="region" id={regionId} aria-label={`${tab === 'qa' ? 'Q&A' : tab[0].toUpperCase() + tab.slice(1)} section`}>
            {tab === 'content' && <ChapterList {...chapterProps} />}
            {tab === 'settings' && <CourseSettings course={course} onSave={onSaveSettings} />}
            {tab !== 'content' && tab !== 'settings' && tabContent[tab]}
        </Box>
    </Page>;
}

function CourseSettings({ course, onSave }: { course: CourseEditorPageProps['course']; onSave: CourseEditorPageProps['onSaveSettings'] }) {
    const [title, setTitle] = useState(course.title), [description, setDescription] = useState(course.description), [prerequisites, setPrerequisites] = useState(course.prerequisiteDescription);
    const [error, setError] = useState<string | null>(null);
    return <Tile padding={{ xs: 3, sm: 4 }} sx={{ maxWidth: 760 }}>
        <Box component="form" noValidate onSubmit={(event) => { event.preventDefault(); if (!title.trim()) { setError('Enter a course title.'); return; } setError(null); onSave({ title: title.trim(), description, prerequisiteDescription: prerequisites }); }} sx={{ display: 'grid', gap: 2.5 }}>
            <TextField label="Course title" value={title} onChange={(event) => setTitle(event.target.value)} error={!!error} helperText={error} required />
            <TextField label="Description" multiline minRows={4} value={description} onChange={(event) => setDescription(event.target.value)} helperText="Shown on the course detail page." />
            <TextField label="Prerequisites (optional)" multiline minRows={2} value={prerequisites} onChange={(event) => setPrerequisites(event.target.value)} helperText="A learner-facing description. It never blocks enrolment." />
            <Box><Button type="submit" variant="contained">Save changes</Button></Box>
        </Box>
    </Tile>;
}
