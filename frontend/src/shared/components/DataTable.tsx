import { useId } from 'react';
import type { ReactNode } from 'react';
import { Box, Pagination, Skeleton, Table, TableBody, TableCell, TableContainer, TableHead, TableRow, TableSortLabel, Typography, useMediaQuery, useTheme } from '@mui/material';
import { color, radius } from '../../app/theme/theme';

export interface Column<T> {
    key: string;
    header: string;
    render: (row: T) => ReactNode;
    width?: number | string;
    align?: 'left' | 'right' | 'center';
    sortable?: boolean;
    /** The row's name; shown as the title of a stacked row below 600 px. */
    primary?: boolean;
}

export interface TableSort { key: string; direction: 'asc' | 'desc'; onChange: (key: string) => void }

export interface DataTableProps<T> {
    /** Accessible table name (visually hidden caption). */
    label: string;
    columns: Column<T>[];
    rows: T[];
    getRowId: (row: T) => string;
    loading?: boolean;
    loadingRows?: number;
    empty?: ReactNode;
    sort?: TableSort;
    selectedId?: string;
    /** Trailing actions cell (icon buttons). */
    actions?: (row: T) => ReactNode;
    actionsHeader?: string;
    footer?: ReactNode;
}

/**
 * One white surface with hairline rows (DESIGN.md › Tables). 600–899 px: horizontal
 * scroll inside the surface with a sticky first column. < 600 px: stacked rows.
 */
export function DataTable<T>({ label, columns, rows, getRowId, loading = false, loadingRows = 5, empty, sort, selectedId, actions, actionsHeader = 'Actions', footer }: DataTableProps<T>) {
    const stacked = useMediaQuery(useTheme().breakpoints.down('sm'));
    const captionId = useId();
    const primary = columns.find((column) => column.primary) ?? columns[0];
    const showEmpty = !loading && rows.length === 0;
    return <Box sx={{ bgcolor: color.surface, borderRadius: `${radius.lg}px`, overflow: 'hidden', mb: 3 }}>
        {loading && <Typography id={`${captionId}-status`} role="status" className="cm-visually-hidden">Loading {label.toLowerCase()}</Typography>}
        {showEmpty ? <Box sx={{ p: { xs: 2, sm: 3 } }}>{empty}</Box> : stacked
            ? <Box component="ul" aria-label={label} aria-busy={loading || undefined} sx={{ listStyle: 'none', m: 0, p: 0 }}>
                {loading ? Array.from({ length: loadingRows }, (_, i) => <Box component="li" key={i} aria-hidden sx={{ p: 2, borderBottom: `1px solid ${color.line200}` }}><Skeleton height={22} width="70%" /><Skeleton height={16} sx={{ mt: 1 }} /></Box>)
                    : rows.map((row) => <Box component="li" key={getRowId(row)} aria-current={selectedId === getRowId(row) ? 'true' : undefined}
                        sx={{ p: 2, borderBottom: `1px solid ${color.line200}`, '&:last-child': { borderBottom: 0 }, bgcolor: selectedId === getRowId(row) ? color.brand100 : undefined }}>
                        <Box sx={{ fontWeight: 600, fontSize: '1rem', mb: 1, overflowWrap: 'anywhere' }}>{primary.render(row)}</Box>
                        <Box component="dl" sx={{ m: 0, display: 'grid', gridTemplateColumns: 'minmax(96px, auto) minmax(0, 1fr)', columnGap: 2, rowGap: .75 }}>
                            {columns.filter((column) => column !== primary).map((column) => <Box key={column.key} sx={{ display: 'contents' }}>
                                <Box component="dt" sx={{ fontSize: '0.8125rem', fontWeight: 600, color: 'text.secondary', pt: .25 }}>{column.header}</Box>
                                <Box component="dd" sx={{ m: 0, fontSize: '0.9375rem', minWidth: 0, overflowWrap: 'anywhere' }}>{column.render(row)}</Box>
                            </Box>)}
                        </Box>
                        {actions && <Box sx={{ mt: 1.5, display: 'flex', flexWrap: 'wrap', gap: 1 }}>{actions(row)}</Box>}
                    </Box>)}
            </Box>
            : <TableContainer sx={{ overflowX: 'auto', position: 'relative' }}>
                <Table aria-labelledby={captionId} aria-busy={loading || undefined} sx={{ minWidth: Math.max(560, columns.length * 140) }}>
                    <caption id={captionId} className="cm-visually-hidden">{label}</caption>
                    <TableHead><TableRow>
                        {columns.map((column, index) => <TableCell key={column.key} align={column.align} sx={{ width: column.width, ...(index === 0 ? { ...stickyFirst, backgroundColor: color.surface } : {}) }}
                            sortDirection={sort?.key === column.key ? sort.direction : false}>
                            {column.sortable && sort ? <TableSortLabel active={sort.key === column.key} direction={sort.key === column.key ? sort.direction : 'asc'} onClick={() => sort.onChange(column.key)}>{column.header}</TableSortLabel> : column.header}
                        </TableCell>)}
                        {actions && <TableCell align="right"><span className="cm-visually-hidden">{actionsHeader}</span></TableCell>}
                    </TableRow></TableHead>
                    <TableBody>
                        {loading ? Array.from({ length: loadingRows }, (_, i) => <TableRow key={i} aria-hidden>{columns.map((column) => <TableCell key={column.key}><Skeleton height={20} /></TableCell>)}{actions && <TableCell />}</TableRow>)
                            : rows.map((row) => <TableRow key={getRowId(row)} hover selected={selectedId === getRowId(row)} sx={{ bgcolor: color.surface, '&:hover': { bgcolor: `${color.canvas100} !important` }, '&.Mui-selected': { bgcolor: `${color.brand100} !important` } }}>
                                {columns.map((column, index) => <TableCell key={column.key} align={column.align} sx={{ minHeight: 60, overflowWrap: 'anywhere', ...(index === 0 ? stickyFirst : {}) }}
                                    component={index === 0 ? 'th' : 'td'} scope={index === 0 ? 'row' : undefined}>{column.render(row)}</TableCell>)}
                                {actions && <TableCell align="right" sx={{ whiteSpace: 'nowrap', py: 1 }}><Box sx={{ display: 'inline-flex', gap: .5 }}>{actions(row)}</Box></TableCell>}
                            </TableRow>)}
                    </TableBody>
                </Table>
            </TableContainer>}
        {footer && !showEmpty && <Box sx={{ borderTop: `1px solid ${color.line200}`, px: { xs: 2, sm: 3 }, py: 1.5 }}>{footer}</Box>}
    </Box>;
}
// The sticky first cell inherits the row background so hover and selection stay continuous.
const stickyFirst = { position: 'sticky', left: 0, zIndex: 1, backgroundColor: 'inherit', fontWeight: 600 } as const;

export interface PaginationBarProps {
    /** Zero-based page as used by the API; display is one-based. */
    page: number;
    totalPages: number;
    totalItems: number;
    pageSize: number;
    itemsOnPage: number;
    onChange: (page: number) => void;
    noun?: string;
    /** Singular form used when there is exactly one item. */
    nounOne?: string;
}
/** "Showing 1–20 of 124" with previous/next and page numbers. */
export function PaginationBar({ page, totalPages, totalItems, pageSize, itemsOnPage, onChange, noun = 'items', nounOne }: PaginationBarProps) {
    const first = totalItems === 0 ? 0 : page * pageSize + 1, last = page * pageSize + itemsOnPage;
    return <Box sx={{ display: 'flex', flexWrap: 'wrap', alignItems: 'center', justifyContent: 'space-between', gap: 1.5 }}>
        <Typography role="status" className="cm-tabular" sx={{ color: 'text.secondary', fontSize: '0.9375rem' }}>Showing {first === last ? first : `${first}–${last}`} of {totalItems} {totalItems === 1 && nounOne ? nounOne : noun}</Typography>
        {totalPages > 1 && <Pagination count={totalPages} page={page + 1} onChange={(_, value) => onChange(value - 1)} siblingCount={1} boundaryCount={1} />}
    </Box>;
}

/** Toolbar above a table or grid: search, filters, result count. */
export function TableToolbar({ children, count }: { children?: ReactNode; count?: ReactNode }) {
    return <Box sx={{ display: 'flex', flexWrap: 'wrap', alignItems: 'center', gap: 1.5, mb: 2 }}>
        {children}
        {count !== undefined && <Typography className="cm-tabular" sx={{ ml: 'auto', color: 'text.secondary', fontWeight: 500 }}>{count}</Typography>}
    </Box>;
}
