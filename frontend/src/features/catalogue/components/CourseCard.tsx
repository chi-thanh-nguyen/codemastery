import type { ReactNode } from 'react';
import type { CourseSummary } from '../types/catalogueTypes';
import { CourseTile } from '../../../shared/components/Tile';

/** FE07: a missing public display name renders this fallback; never an invented name. */
export const instructorLabel = (name: string | null) => name ?? 'Instructor name unavailable';

export interface CourseCardProps {
    course: Pick<CourseSummary, 'id' | 'title' | 'description' | 'instructorDisplayName'>;
    onViewDetails: (courseId: string) => void;
    href?: string;
    status?: ReactNode;
    headingLevel?: 'h2' | 'h3';
}

/** Catalogue course tile with real published data only. */
export function CourseCard({ course, onViewDetails, href, status, headingLevel = 'h2' }: CourseCardProps) {
    return <CourseTile title={course.title} description={course.description} byline={`Instructor · ${instructorLabel(course.instructorDisplayName)}`}
        href={href ?? `/courses/${encodeURIComponent(course.id)}`} onOpen={() => onViewDetails(course.id)} status={status} headingLevel={headingLevel} />;
}
