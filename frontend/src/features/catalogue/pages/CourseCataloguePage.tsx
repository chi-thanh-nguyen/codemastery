import React from 'react';
import { Box, Button, Pagination, Typography } from '@mui/material';
import { PageHeader } from '../../../shared/components/PageHeader';
import { LoadingView, EmptyView, ErrorView } from '../../../shared/components/StatusViews';
import { CourseCard } from '../components/CourseCard';
import { useCourseCatalogue } from '../hooks/useCourseCatalogue';

export interface CourseCataloguePageProps {
    onSelectCourse: (courseId: string) => void;
}

export const CourseCataloguePage: React.FC<CourseCataloguePageProps> = ({
    onSelectCourse,
}) => {
    const {
        courses,
        page,
        totalPages,
        totalElements,
        loading,
        error,
        setPage,
        refetch,
    } = useCourseCatalogue();

    return (
        <Box>
            <PageHeader
                title="Course Catalogue"
                subtitle="Explore introductory programming courses designed for mastery learning"
            />

            {loading && <LoadingView message="Loading available courses..." />}

            {error && (
                <ErrorView
                    title="Could not load catalogue"
                    message={error}
                    onRetry={refetch}
                />
            )}

            {!loading && !error && courses.length === 0 && (
                <EmptyView
                    title="No published courses found"
                    message="Courses are currently being authored by instructors. Please check back later."
                    action={
                        <Button variant="outlined" onClick={refetch}>
                            Refresh
                        </Button>
                    }
                />
            )}

            {!loading && !error && courses.length > 0 && (
                <Box>
                    <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
                        Showing {courses.length} of {totalElements} published courses
                    </Typography>

                    <Box
                        sx={{
                            display: 'grid',
                            gridTemplateColumns: {
                                xs: '1fr',
                                sm: 'repeat(2, 1fr)',
                                md: 'repeat(3, 1fr)',
                            },
                            gap: 3,
                            mb: 4,
                        }}
                    >
                        {courses.map((course) => (
                            <CourseCard
                                key={course.id}
                                course={course}
                                onViewDetails={onSelectCourse}
                            />
                        ))}
                    </Box>

                    {totalPages > 1 && (
                        <Box sx={{ display: 'flex', justifyContent: 'center', my: 3 }}>
                            <Pagination
                                count={totalPages}
                                page={page + 1}
                                onChange={(_, value) => setPage(value - 1)}
                                color="primary"
                            />
                        </Box>
                    )}
                </Box>
            )}
        </Box>
    );
};