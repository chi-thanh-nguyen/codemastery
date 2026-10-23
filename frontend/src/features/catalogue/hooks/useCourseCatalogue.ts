import { useState, useEffect, useCallback } from 'react';
import { getPublishedCourses } from '../api/catalogueApi';
import type { CourseSummary } from '../types/catalogueTypes';

export function useCourseCatalogue(initialPage = 0, initialSize = 10) {
    const [courses, setCourses] = useState<CourseSummary[]>([]);
    const [page, setPage] = useState(initialPage);
    const [size] = useState(initialSize);
    const [totalPages, setTotalPages] = useState(0);
    const [totalElements, setTotalElements] = useState(0);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<string | null>(null);

    const fetchCourses = useCallback(async (targetPage: number) => {
        try {
            setLoading(true);
            setError(null);
            const data = await getPublishedCourses(targetPage, size);
            setCourses(data.content);
            setPage(data.page);
            setTotalPages(data.totalPages);
            setTotalElements(data.totalElements);
        } catch (err: unknown) {
            const message = err instanceof Error ? err.message : 'Failed to fetch course catalogue';
            setError(message);
        } finally {
            setLoading(false);
        }
    }, [size]);

    useEffect(() => {
        fetchCourses(page);
    }, [fetchCourses, page]);

    return {
        courses,
        page,
        totalPages,
        totalElements,
        loading,
        error,
        setPage,
        refetch: () => fetchCourses(page),
    };
}