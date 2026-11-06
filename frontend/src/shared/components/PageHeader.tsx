import { useRef } from 'react';
import type { MouseEvent, ReactNode } from 'react';
import { Box, Breadcrumbs, IconButton, InputAdornment, Link, TextField, Typography } from '@mui/material';
import { PresentationIcon } from './PresentationIcon';

export interface SearchPillProps {
    label: string;
    value: string;
    onChange: (value: string) => void;
    placeholder?: string;
    maxLength?: number;
    name?: string;
    id?: string;
    onClear?: () => void;
}
/**
 * 52 px search pill (DESIGN.md › Inputs). The caller decides live versus submitted search.
 * With `onClear`, Escape and the clear button empty the field and keep focus in it.
 */
export function SearchPill({ label, value, onChange, placeholder, maxLength, name, id, onClear }: SearchPillProps) {
    const input = useRef<HTMLInputElement>(null);
    const clear = () => { onClear?.(); input.current?.focus(); };
    return <TextField hiddenLabel fullWidth id={id} name={name} value={value} placeholder={placeholder ?? label} onChange={(event) => onChange(event.target.value)} inputRef={input}
        onKeyDown={(event) => { if (event.key === 'Escape' && onClear && value) { event.preventDefault(); event.stopPropagation(); clear(); } }}
        slotProps={{
            htmlInput: { 'aria-label': label, maxLength, type: 'search', enterKeyHint: 'search' },
            input: {
                startAdornment: <InputAdornment position="start" sx={{ ml: .5, mr: 0 }}><PresentationIcon name="search" sx={{ fontSize: 20, color: 'text.secondary' }} /></InputAdornment>,
                endAdornment: onClear && value ? <InputAdornment position="end"><IconButton aria-label={`Clear ${label.toLowerCase()}`} onClick={clear} sx={{ width: 36, height: 36, mr: -.5 }}>
                    <PresentationIcon name="close" sx={{ fontSize: 18 }} /></IconButton></InputAdornment> : undefined,
            },
        }}
        sx={{ '& input[type="search"]::-webkit-search-cancel-button': { display: 'none' } }} />;
}

export interface Crumb { label: string; href?: string; onClick?: () => void }
export interface PageHeaderProps {
    title: string;
    /** One-line description under the title (max 65ch). */
    subtitle?: string;
    actions?: ReactNode;
    /** Search pill or other control aligned to the title row's end. */
    search?: ReactNode;
    breadcrumbs?: Crumb[];
    /** Back link for depth-two pages ("‹ Catalogue"). */
    back?: Crumb;
    /** Content below the title (status pills, metadata). */
    meta?: ReactNode;
    titleId?: string;
}

function CrumbLink({ crumb, children }: { crumb: Crumb; children: ReactNode }) {
    return <Link href={crumb.href ?? '#'} onClick={(event: MouseEvent) => { if (crumb.onClick) { event.preventDefault(); crumb.onClick(); } }}
        sx={{ color: 'text.secondary', fontWeight: 500, display: 'inline-flex', alignItems: 'center', gap: .5, minHeight: 32, '&:hover': { color: 'text.primary' } }}>{children}</Link>;
}

/** Title row: h1 (route focus target), description, actions, search (DESIGN.md › Page header). */
export function PageHeader({ title, subtitle, actions, search, breadcrumbs, back, meta, titleId }: PageHeaderProps) {
    return <Box component="header" sx={{ mb: 4 }}>
        {back && <Box sx={{ mb: 1.5 }}><CrumbLink crumb={back}><PresentationIcon name="chevron-left" sx={{ fontSize: 18 }} />{back.label}</CrumbLink></Box>}
        {breadcrumbs && breadcrumbs.length > 0 && <Breadcrumbs aria-label="Breadcrumb" separator="/" sx={{ mb: 1.5 }}>
            {breadcrumbs.map((crumb, index) => index === breadcrumbs.length - 1 && !crumb.href && !crumb.onClick
                ? <Typography key={crumb.label} component="span" aria-current="page" sx={{ fontSize: 'inherit', fontWeight: 500, color: 'text.primary' }}>{crumb.label}</Typography>
                : <CrumbLink key={crumb.label} crumb={crumb}>{crumb.label}</CrumbLink>)}
        </Breadcrumbs>}
        <Box sx={{ display: 'flex', flexWrap: 'wrap', alignItems: { xs: 'flex-start', md: 'center' }, justifyContent: 'space-between', columnGap: 3, rowGap: 2 }}>
            <Box sx={{ minWidth: 0, flex: '1 1 320px' }}>
                <Typography variant="h1" id={titleId} sx={{ overflowWrap: 'anywhere', textWrap: 'balance' }}>{title}</Typography>
                {subtitle && <Typography sx={{ mt: 1, color: 'text.secondary', maxWidth: '65ch' }}>{subtitle}</Typography>}
                {meta && <Box sx={{ mt: 1.5, display: 'flex', flexWrap: 'wrap', alignItems: 'center', gap: 1 }}>{meta}</Box>}
            </Box>
            {(actions || search) && <Box sx={{ display: 'flex', flexWrap: 'wrap', alignItems: 'center', gap: 1.5, flex: { xs: '1 1 100%', md: '0 1 auto' }, minWidth: 0 }}>
                {actions}
                {search && <Box sx={{ flex: { xs: '1 1 100%', sm: '1 1 320px' }, minWidth: 0, maxWidth: { md: 420 } }}>{search}</Box>}
            </Box>}
        </Box>
    </Box>;
}
