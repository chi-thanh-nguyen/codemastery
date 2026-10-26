import { useState, useEffect, useCallback } from 'react';
import { getPublishedCourses } from '../api/catalogueApi';
import type { CourseSummary } from '../types/catalogueTypes';
import { ApiError } from '../../../shared/types/apiTypes';

export function useCourseCatalogue(initialPage = 0, initialSize = 20) {
    const [courses, setCourses] = useState<CourseSummary[]>([]);
    const [page, setPage] = useState(initialPage);
    const [filters, setFilters] = useState<{ q: string; prerequisites: 'any' | 'none' | 'described' }>({ q: '', prerequisites: 'any' });
    const [size] = useState(initialSize);
    const [totalPages, setTotalPages] = useState(0);
    const [totalElements, setTotalElements] = useState(0);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);
    const [revision, retry] = useState(0);
    const refetch = useCallback(() => retry((value) => value + 1), []);

    useEffect(() => {
        const controller = new AbortController();
        let current = true, recovering = false;
        setLoading(true); setError(null);
        getPublishedCourses(page, size, { ...filters, signal: controller.signal, isCurrent: () => current }).then((data) => {
            if (!current) return;
            if (data.totalPages > 0 && page >= data.totalPages) {
                // A non-empty result set shrank: keep loading until the last valid page arrives.
                recovering = true;
                setPage(data.totalPages - 1);
                return;
            }
            setCourses(data.content); setTotalPages(data.totalPages); setTotalElements(data.totalElements);
        }).catch((error: unknown) => {
            if (current && !(error instanceof ApiError && error.kind === 'cancelled')) {
                setError(error instanceof ApiError ? error.message : 'Failed to fetch course catalogue.');
            }
        }).finally(() => { if (current && !recovering) setLoading(false); });
        return () => { current = false; controller.abort(); };
    }, [page, size, revision, filters]);

    return { courses, page, totalPages, totalElements, loading, error, setPage: (next: number) => { setLoading(true); setError(null); setPage(next); }, refetch, applyFilters: (next: typeof filters) => { setLoading(true); setError(null); setPage(0); setFilters(next); } };
}
