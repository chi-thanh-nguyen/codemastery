import React, { useState } from 'react';
import { Box, Button, MenuItem, Pagination, TextField, Typography } from '@mui/material';
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
    const [q, setQuery] = useState('');
    const [prerequisites, setPrerequisites] = useState<'any' | 'none' | 'described'>('any');
    const {
        courses,
        page,
        totalPages,
        totalElements,
        loading,
        error,
        setPage,
        refetch,
        applyFilters,
    } = useCourseCatalogue();

    return (
        <Box>
            <PageHeader
                title="Course Catalogue"
                subtitle="Explore introductory programming courses designed for mastery learning"
            />

            <Box component="form" aria-label="Filter courses" onSubmit={(event) => { event.preventDefault(); applyFilters({ q: q.trim(), prerequisites }); }}
                sx={{ display: 'flex', flexWrap: 'wrap', gap: 2, mb: 3, alignItems: 'center' }}>
                <TextField label="Search courses" value={q} onChange={(event) => setQuery(event.target.value)} slotProps={{ htmlInput: { maxLength: 100 } }} sx={{ flex: '1 1 200px' }} />
                {/* Reserve the longest option so selecting it cannot resize the search field. */}
                <TextField select label="Prerequisites" value={prerequisites} onChange={(event) => setPrerequisites(event.target.value as typeof prerequisites)} sx={{ flex: '0 0 13rem', maxWidth: '100%' }}>
                    <MenuItem value="any">Any prerequisites</MenuItem><MenuItem value="none">None specified</MenuItem><MenuItem value="described">Prerequisites described</MenuItem>
                </TextField>
                <Button type="submit" variant="outlined">Apply filters</Button>
            </Box>
            {loading && <LoadingView message="Loading available courses..." />}

            {error && (
                <ErrorView
                    title="Could not load catalogue"
                    message={error}
                    onRetry={refetch}
                />
            )}

            {!loading && !error && totalElements === 0 && (
                <EmptyView
                    title="No published courses found"
                    message="No published courses match these filters. Try another search."
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