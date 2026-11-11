import { Box, ToggleButton, ToggleButtonGroup, Tooltip, useMediaQuery, useTheme } from '@mui/material';
import { PresentationIcon } from './PresentationIcon';
import type { PresentationIconName } from './PresentationIcon';
import { color, control, radius } from '../../app/theme/theme';
import { CircleAction } from './Tile';

export interface SegmentOption<T extends string> { value: T; label: string; count?: number; icon?: PresentationIconName }

export interface SegmentedControlProps<T extends string> {
    label: string;
    value: T;
    options: SegmentOption<T>[];
    onChange: (value: T) => void;
    fullWidth?: boolean;
    /** Id of the region whose content the selection replaces (aria-controls). */
    controls?: string;
}
/** Pill segmented control: single-select toggle group with aria-pressed items (DESIGN.md › Tabs and segmented filters). */
export function SegmentedControl<T extends string>({ label, value, options, onChange, fullWidth, controls }: SegmentedControlProps<T>) {
    return <ToggleButtonGroup exclusive value={value} aria-label={label} onChange={(_, next: T | null) => { if (next !== null) onChange(next); }}
        sx={{ display: fullWidth ? 'flex' : 'inline-flex', maxWidth: '100%', minWidth: 0, overflowX: 'auto', flexWrap: { xs: 'nowrap', sm: 'wrap' }, '& .MuiToggleButton-root': fullWidth ? { flex: 1 } : {} }}>
        {options.map((option) => <ToggleButton key={option.value} value={option.value} disableRipple aria-controls={controls}
            sx={{ gap: .75, '& svg': { fontSize: 18 } }}>
            {option.icon && <PresentationIcon name={option.icon} />}
            {option.label}
            {option.count !== undefined && <Box component="span" className="cm-tabular" sx={{ ml: .25, color: color.ink600, fontWeight: 500 }}>{option.count}</Box>}
        </ToggleButton>)}
    </ToggleButtonGroup>;
}

export interface RailTool { label: string; icon: PresentationIconName; onClick: () => void }
export interface ViewRailProps<T extends string> {
    label: string;
    value: T;
    options: (SegmentOption<T> & { icon: PresentationIconName })[];
    onChange: (value: T) => void;
    tools?: RailTool[];
}
/**
 * One dataset, several views. Vertical circular rail at >= 1200 px (placed in the
 * page's left gutter), horizontal segmented pill below (DESIGN.md › View Rail).
 */
export function ViewRail<T extends string>({ label, value, options, onChange, tools = [] }: ViewRailProps<T>) {
    const vertical = useMediaQuery(useTheme().breakpoints.up('lg'));
    if (!vertical) return <Box sx={{ display: 'flex', flexWrap: 'wrap', alignItems: 'center', gap: 1 }}>
        <SegmentedControl label={label} value={value} options={options} onChange={onChange} />
        {tools.length > 0 && <Box role="group" aria-label={`${label} tools`} sx={{ display: 'flex', gap: .5 }}>{tools.map((tool) => <CircleAction key={tool.label} label={tool.label} icon={tool.icon} onClick={tool.onClick} />)}</Box>}
    </Box>;
    return <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2, alignItems: 'center' }}>
        <ToggleButtonGroup exclusive orientation="vertical" value={value} aria-label={label} onChange={(_, next: T | null) => { if (next !== null) onChange(next); }}
            sx={{ width: 52, p: '4px', gap: '4px', borderRadius: `${radius.pill}px`, flexWrap: 'nowrap' }}>
            {options.map((option) => <Tooltip key={option.value} title={option.label} placement="right">
                <ToggleButton value={option.value} aria-label={option.label} disableRipple
                    sx={{ width: 44, height: 44, minHeight: 44, p: 0, borderRadius: '50% !important', color: color.ink900,
                        '&.Mui-selected, &.Mui-selected:hover': { bgcolor: control.primary, color: color.surface, boxShadow: 'none' }, '& svg': { fontSize: 20 } }}>
                    <PresentationIcon name={option.icon} />
                </ToggleButton>
            </Tooltip>)}
        </ToggleButtonGroup>
        {tools.length > 0 && <Box role="group" aria-label={`${label} tools`} sx={{ display: 'flex', flexDirection: 'column', gap: .75, p: '4px', bgcolor: color.fill150, borderRadius: `${radius.pill}px` }}>
            {tools.map((tool) => <CircleAction key={tool.label} label={tool.label} icon={tool.icon} onClick={tool.onClick} variant="plain" />)}
        </Box>}
    </Box>;
}
