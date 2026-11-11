import { useState } from 'react';
import { Box, Button } from '@mui/material';
import { PageHeader, SearchPill } from '../../../shared/components/PageHeader';
import { LoadingView, EmptyView, ErrorView } from '../../../shared/components/StatusViews';
import { SegmentedControl } from '../../../shared/components/ViewRail';
import { PaginationBar, TableToolbar } from '../../../shared/components/DataTable';
import { Page } from '../../../layouts/DashboardLayout';
import { CourseCard } from '../components/CourseCard';
import { useCourseCatalogue } from '../hooks/useCourseCatalogue';

type Prerequisites = 'any' | 'none' | 'described';
export interface CourseCataloguePageProps { onSelectCourse: (courseId: string) => void }

/** S3 Catalogue: published courses, submitted search, approved prerequisite filter, server paging (FE07). */
export function CourseCataloguePage({ onSelectCourse }: CourseCataloguePageProps) {
    const [q, setQuery] = useState('');
    const [prerequisites, setPrerequisites] = useState<Prerequisites>('any');
    const [applied, setApplied] = useState<{ q: string; prerequisites: Prerequisites }>({ q: '', prerequisites: 'any' });
    const { courses, page, totalPages, totalElements, loading, error, setPage, refetch, applyFilters } = useCourseCatalogue();
    const apply = (next: { q: string; prerequisites: Prerequisites }) => { setApplied(next); applyFilters(next); };
    const filtered = applied.q !== '' || applied.prerequisites !== 'any';
    return <Page>
        <PageHeader title="Course catalogue" subtitle="Introductory programming courses built for mastery learning." />
        <Box component="form" role="search" aria-label="Search courses" onSubmit={(event) => { event.preventDefault(); apply({ q: q.trim(), prerequisites }); }}
            sx={{ display: 'flex', flexWrap: 'wrap', gap: 1.5, mb: 2, maxWidth: 720 }}>
            <Box sx={{ flex: '1 1 280px', minWidth: 0 }}>
                <SearchPill label="Search courses" name="q" value={q} onChange={setQuery} maxLength={100} placeholder="Search by title or description" />
            </Box>
            <Button type="submit" variant="contained">Search</Button>
        </Box>
        <TableToolbar count={!loading && !error ? `${totalElements} ${totalElements === 1 ? 'course' : 'courses'}` : undefined}>
            <SegmentedControl label="Prerequisites" value={prerequisites}
                options={[{ value: 'any', label: 'Any prerequisites' }, { value: 'none', label: 'No prerequisites' }, { value: 'described', label: 'Prerequisites described' }]}
                // Search is submit-only (DESIGN.md › Inputs): a filter change keeps the last submitted query, never unsent text.
                onChange={(next) => { setPrerequisites(next); apply({ q: applied.q, prerequisites: next }); }} />
        </TableToolbar>
        {loading && <LoadingView message="Loading available courses..." variant="tiles" />}
        {error && <ErrorView title="Couldn't load the catalogue" message={error} onRetry={refetch} />}
        {!loading && !error && totalElements === 0 && (filtered
            ? <EmptyView icon="search" title="No courses match these filters" message="Try a different search, or clear the filters to see every published course."
                action={<Button variant="outlined" onClick={() => { setQuery(''); setPrerequisites('any'); apply({ q: '', prerequisites: 'any' }); }}>Clear filters</Button>} />
            : <EmptyView icon="catalogue" title="No published courses yet" message="Published courses will appear here as soon as an instructor publishes them."
                action={<Button variant="outlined" onClick={refetch}>Refresh</Button>} />)}
        {!loading && !error && courses.length > 0 && <>
            <Box component="ul" aria-label="Published courses" sx={{ listStyle: 'none', m: 0, p: 0, display: 'grid', gap: 3, mb: 4,
                gridTemplateColumns: { xs: 'minmax(0, 1fr)', sm: 'repeat(2, minmax(0, 1fr))', lg: 'repeat(3, minmax(0, 1fr))' } }}>
                {courses.map((course) => <Box component="li" key={course.id} sx={{ minWidth: 0 }}><CourseCard course={course} onViewDetails={onSelectCourse} /></Box>)}
            </Box>
            <PaginationBar page={page} totalPages={totalPages} totalItems={totalElements} pageSize={20} itemsOnPage={courses.length} onChange={setPage} noun="published courses" nounOne="published course" />
        </>}
    </Page>;
}
