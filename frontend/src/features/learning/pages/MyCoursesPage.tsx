import React, { useState, useEffect, useCallback } from 'react';
import {
    Box,
    Card,
    CardContent,
    CardActions,
    Typography,
    Button,
    Chip,
} from '@mui/material';
import { PageHeader } from '../../../shared/components/PageHeader';
import { LoadingView, EmptyView, ErrorView } from '../../../shared/components/StatusViews';
import { ConfirmDialog } from '../../../shared/components/ConfirmDialog';
import { useAuth } from '../../auth/hooks/useAuth';
import { getMyEnrolledCourses, leaveCourse } from '../api/learningApi';
import type { CourseMembership } from '../types/learningTypes';

export interface MyCoursesPageProps {
    onSelectCourse: (courseId: string) => void;
    onBrowseCatalogue: () => void;
}

export const MyCoursesPage: React.FC<MyCoursesPageProps> = ({
    onSelectCourse,
    onBrowseCatalogue,
}) => {
    const { token } = useAuth();
    const [memberships, setMemberships] = useState<CourseMembership[]>([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);

    // Confirm Leave Dialog state
    const [selectedCourseToLeave, setSelectedCourseToLeave] = useState<CourseMembership | null>(null);
    const [leaving, setLeaving] = useState(false);

    const fetchMyCourses = useCallback(async () => {
        if (!token) return;
        try {
            setLoading(true);
            setError(null);
            const data = await getMyEnrolledCourses(token);
            setMemberships(data);
        } catch (err: unknown) {
            const message = err instanceof Error ? err.message : 'Failed to fetch enrolled courses';
            setError(message);
        } finally {
            setLoading(false);
        }
    }, [token]);

    useEffect(() => {
        fetchMyCourses();
    }, [fetchMyCourses]);

    const handleConfirmLeave = async () => {
        if (!selectedCourseToLeave || !token) return;
        try {
            setLeaving(true);
            await leaveCourse(selectedCourseToLeave.courseId, token);
            setSelectedCourseToLeave(null);
            await fetchMyCourses(); // Refresh list
        } catch (err: unknown) {
            alert(err instanceof Error ? err.message : 'Could not leave course');
        } finally {
            setLeaving(false);
        }
    };

    if (loading) return <LoadingView message="Loading your enrolled courses..." />;

    return (
        <Box>
            <PageHeader
                title="My Courses"
                subtitle="Manage your active enrolled courses and progress"
                actions={
                    <Button variant="contained" onClick={onBrowseCatalogue}>
                        Browse More Courses
                    </Button>
                }
            />

            {error && (
                <ErrorView
                    title="Could not load courses"
                    message={error}
                    onRetry={fetchMyCourses}
                />
            )}

            {!error && memberships.length === 0 && (
                <EmptyView
                    title="You are not enrolled in any courses"
                    message="Browse the course catalogue to find a suitable programming course."
                    action={
                        <Button variant="contained" onClick={onBrowseCatalogue}>
                            Explore Catalogue
                        </Button>
                    }
                />
            )}

            {!error && memberships.length > 0 && (
                <Box
                    sx={{
                        display: 'grid',
                        gridTemplateColumns: {
                            xs: '1fr',
                            sm: 'repeat(2, 1fr)',
                            md: 'repeat(3, 1fr)',
                        },
                        gap: 3,
                    }}
                >
                    {memberships.map((item) => (
                        <Card
                            key={item.courseId}
                            sx={{
                                display: 'flex',
                                flexDirection: 'column',
                                height: '100%',
                                borderRadius: 2,
                            }}
                        >
                            <CardContent sx={{ flexGrow: 1 }}>
                                <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: 1 }}>
                                    <Typography variant="h6" component="h2" sx={{ fontWeight: 'bold' }}>
                                        {item.courseTitle}
                                    </Typography>
                                    <Chip
                                        label={item.status}
                                        color={item.status === 'ACTIVE' ? 'success' : 'default'}
                                        size="small"
                                    />
                                </Box>
                                {item.courseDescription && (
                                    <Typography
                                        variant="body2"
                                        color="text.secondary"
                                        sx={{
                                            display: '-webkit-box',
                                            WebkitLineClamp: 3,
                                            WebkitBoxOrient: 'vertical',
                                            overflow: 'hidden',
                                        }}
                                    >
                                        {item.courseDescription}
                                    </Typography>
                                )}
                                <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 2 }}>
                                    Enrolled on: {new Date(item.enrolledAt).toLocaleDateString()}
                                </Typography>
                            </CardContent>

                            <CardActions sx={{ px: 2, pb: 2, justifyContent: 'space-between' }}>
                                <Button
                                    size="small"
                                    color="error"
                                    onClick={() => setSelectedCourseToLeave(item)}
                                >
                                    Leave Course
                                </Button>
                                <Button
                                    size="small"
                                    variant="contained"
                                    onClick={() => onSelectCourse(item.courseId)}
                                >
                                    Enter Course
                                </Button>
                            </CardActions>
                        </Card>
                    ))}
                </Box>
            )}

            {/* Confirm Dialog when Leaving a Course */}
            <ConfirmDialog
                open={Boolean(selectedCourseToLeave)}
                title="Leave Course?"
                message={`Are you sure you want to unenroll from "${selectedCourseToLeave?.courseTitle}"? Your learning history for this course will be archived.`}
                confirmLabel={leaving ? 'Leaving...' : 'Confirm Leave'}
                cancelLabel="Keep Enrolled"
                isDestructive={true}
                onConfirm={handleConfirmLeave}
                onCancel={() => setSelectedCourseToLeave(null)}
            />
        </Box>
    );
};
