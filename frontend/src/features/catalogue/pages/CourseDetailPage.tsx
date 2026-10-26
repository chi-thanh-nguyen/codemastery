import React, { useState, useEffect } from 'react';
import {
    Box,
    Paper,
    Typography,
    Button,
    Accordion,
    AccordionSummary,
    AccordionDetails,
    List,
    ListItem,
    ListItemText,
    Alert,
    Divider,
} from '@mui/material';
import { PageHeader } from '../../../shared/components/PageHeader';
import { LoadingView, ErrorView } from '../../../shared/components/StatusViews';
import { getPublishedCourseById } from '../api/catalogueApi';
import { ApiError } from '../../../shared/types/apiTypes';
import type { CourseDetail } from '../types/catalogueTypes';

export interface CourseDetailPageProps {
    courseId: string;
    onBack: () => void;
    membershipActions?: React.ReactNode;
}

export const CourseDetailPage: React.FC<CourseDetailPageProps> = ({
    courseId,
    onBack,
    membershipActions,
}) => {
    const [course, setCourse] = useState<CourseDetail | null>(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);

    const [revision, retry] = useState(0);
    const fetchDetail = () => retry((value) => value + 1);
    useEffect(() => {
        const controller = new AbortController();
        let current = true;
        setLoading(true); setError(null); setCourse(null);
        getPublishedCourseById(courseId, { signal: controller.signal, isCurrent: () => current }).then((data) => {
            if (current) setCourse(data);
        }).catch((error: unknown) => {
            if (current && !(error instanceof ApiError && error.kind === 'cancelled')) {
                setError(error instanceof ApiError ? error.message : 'Failed to load course details.');
            }
        }).finally(() => { if (current) setLoading(false); });
        return () => { current = false; controller.abort(); };
    }, [courseId, revision]);

    if (loading) return <Box><PageHeader title="Course details" /><LoadingView message="Loading course syllabus..." /></Box>;
    if (error || !course) {
        return (
            <Box><PageHeader title="Course details" /><ErrorView
                title="Could not load course"
                message={error || 'The requested course does not exist or has been unpublished.'}
                onRetry={fetchDetail}
            /></Box>
        );
    }

    return (
        <Box>
            <PageHeader
                title={course.title}
                subtitle="Course Syllabus & Requirements"
                actions={
                    <Box sx={{ display: 'flex', gap: 1 }}>
                        <Button variant="outlined" onClick={onBack}>
                            Back to Catalogue
                        </Button>

                    </Box>
                }
            />

            {membershipActions}
            <Typography sx={{ mb: 2 }}>Instructor: {course.instructorDisplayName ?? 'Instructor name unavailable'}</Typography>
            {/* Description & Prerequisites */}
            <Paper sx={{ p: 3, mb: 3 }}>
                <Typography variant="h6" sx={{ fontWeight: 'bold', mb: 1 }}>
                    Overview
                </Typography>
                <Typography variant="body1" sx={{ mb: 2, color: 'text.secondary' }}>
                    {course.description}
                </Typography>

                <Divider sx={{ my: 2 }} />

                <Typography variant="subtitle2" sx={{ fontWeight: 'bold', mb: 1 }}>
                    Prerequisites:
                </Typography>
                {course.prerequisiteDescription ? (
                    <Alert severity="info" sx={{ mt: 1 }}>
                        {course.prerequisiteDescription}
                    </Alert>
                ) : (
                    <Typography variant="body2" color="text.secondary">
                        No prerequisites specified.
                    </Typography>
                )}
            </Paper>

            {/* Syllabus Chapters & Lessons */}
            <Typography variant="h6" sx={{ fontWeight: 'bold', mb: 2 }}>
                Course Syllabus ({course.syllabus.length} Chapters)
            </Typography>

            {course.syllabus.length === 0 ? (
                <Paper sx={{ p: 3, textAlign: 'center' }}>
                    <Typography variant="body2" color="text.secondary">
                        No syllabus chapters published yet.
                    </Typography>
                </Paper>
            ) : (
                course.syllabus
                    .map((chapter) => (
                        <Accordion key={chapter.id} defaultExpanded sx={{ mb: 1.5 }}>
                            <AccordionSummary>
                                <Typography sx={{ fontWeight: 'bold' }}>
                                    Chapter {chapter.position}: {chapter.title}
                                </Typography>
                            </AccordionSummary>
                            <AccordionDetails>
                                <List dense disablePadding>
                                    {chapter.lessons
                                        .map((lesson) => (
                                            <ListItem key={lesson.id} sx={{ pl: 2 }}>
                                                <ListItemText
                                                    primary={
                                                        <Typography variant="body2">
                                                            {`${lesson.position}. ${lesson.title}`}
                                                        </Typography>
                                                    }
                                                />
                                            </ListItem>
                                        ))}
                                </List>
                            </AccordionDetails>
                        </Accordion>
                    ))
            )}
        </Box>
    );
};
