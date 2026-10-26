import React from 'react';
import {
    Card,
    CardContent,
    CardActions,
    Typography,
    Button,
    Box,
} from '@mui/material';
import type { CourseSummary } from '../types/catalogueTypes';

export interface CourseCardProps {
    course: CourseSummary;
    onViewDetails: (courseId: string) => void;
}

export const CourseCard: React.FC<CourseCardProps> = ({
    course,
    onViewDetails,
}) => {
    return (
        <Card
            sx={{
                display: 'flex',
                flexDirection: 'column',
                height: '100%',
                transition: 'transform 0.2s, box-shadow 0.2s',
                '&:hover': {
                    transform: 'translateY(-2px)',
                    boxShadow: 3,
                },
            }}
        >
            <CardContent sx={{ flexGrow: 1 }}>
                <Typography variant="h6" component="h2" sx={{ fontWeight: 'bold', mb: 1 }}>
                    {course.title}
                </Typography>
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
                    {course.description}
                </Typography>
                <Typography variant="body2" color="text.secondary" sx={{ mt: 2 }}>
                    Instructor: {course.instructorDisplayName ?? 'Instructor name unavailable'}
                </Typography>
            </CardContent>
            <CardActions sx={{ px: 2, pb: 2 }}>
                <Box sx={{ width: '100%', display: 'flex', justifyContent: 'flex-end' }}>
                    <Button
                        size="small"
                        variant="contained"
                        onClick={() => onViewDetails(course.id)}
                    >
                        View Syllabus
                    </Button>
                </Box>
            </CardActions>
        </Card>
    );
};