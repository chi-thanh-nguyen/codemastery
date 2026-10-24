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
import type { CourseDetail } from '../types/catalogueTypes';

export interface CourseDetailPageProps {
    courseId: string;
    onBack: () => void;
    onEnroll?: (courseId: string) => void;
    isEnrolled?: boolean;
}

export const CourseDetailPage: React.FC<CourseDetailPageProps> = ({
    courseId,
    onBack,
    onEnroll,
    isEnrolled = false,
}) => {
    const [course, setCourse] = useState<CourseDetail | null>(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);

    const fetchDetail = async () => {
        try {
            setLoading(true);
            setError(null);
            const data = await getPublishedCourseById(courseId);
            setCourse(data);
        } catch (err: unknown) {
            const message = err instanceof Error ? err.message : 'Failed to load course details';
            setError(message);
        } finally {
            setLoading(false);
        }
    };

    useEffect(() => {
        fetchDetail();
    }, [courseId]);

    if (loading) return <LoadingView message="Loading course syllabus..." />;
    if (error || !course) {
        return (
            <ErrorView
                title="Course not found"
                message={error || 'The requested course does not exist or has been unpublished.'}
                onRetry={fetchDetail}
            />
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
                        {onEnroll && (
                            <Button
                                variant="contained"
                                color={isEnrolled ? 'success' : 'primary'}
                                onClick={() => onEnroll(course.id)}
                                disabled={isEnrolled}
                            >
                                {isEnrolled ? 'Already Enrolled' : 'Enroll Now'}
                            </Button>
                        )}
                    </Box>
                }
            />

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
                        No specific prerequisites required. Beginner friendly!
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
                    .sort((a, b) => a.position - b.position)
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
                                        .sort((a, b) => a.position - b.position)
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
