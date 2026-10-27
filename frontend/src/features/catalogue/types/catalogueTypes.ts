export interface CourseSummary {
    id: string; // UUID
    title: string;
    description: string;
    instructorId: string;
    instructorDisplayName: string | null;
}

export interface SyllabusLesson {
    id: string;
    title: string;
    position: number;
}

export interface SyllabusChapter {
    id: string;
    title: string;
    position: number;
    lessons: SyllabusLesson[];
}

export interface CourseDetail {
    id: string;
    title: string;
    description: string;
    instructorId: string;
    instructorDisplayName: string | null;
    prerequisiteDescription: string | null;
    syllabus: SyllabusChapter[];
}

export interface CoursePage {
    content: CourseSummary[];
    page: number;
    size: number;
    totalElements: number;
    totalPages: number;
}
