import { SvgIcon } from '@mui/material';
import type { SvgIconProps } from '@mui/material';

export type PresentationIconName = 'grid' | 'book' | 'layers' | 'arrow' | 'menu' | 'check' | 'clock' | 'code' | 'chart' | 'search' | 'alert' | 'spark';

// Original geometric outline glyphs; no external icon font or asset package.
export function PresentationIcon({ name, ...props }: SvgIconProps & { name: PresentationIconName }) {
    const paths: Record<PresentationIconName, React.ReactNode> = {
        grid: <><rect x="3" y="3" width="7" height="7" rx="1.5" /><rect x="14" y="3" width="7" height="7" rx="1.5" /><rect x="3" y="14" width="7" height="7" rx="1.5" /><rect x="14" y="14" width="7" height="7" rx="1.5" /></>,
        book: <><path d="M12 5C9 3 5 3 3 4v15c3-1 6-1 9 1 3-2 6-2 9-1V4c-2-1-6-1-9 1Z" /><path d="M12 5v15" /></>,
        layers: <><path d="m3 8 9-5 9 5-9 5-9-5Zm0 5 9 5 9-5M3 18l9 5 9-5" /></>,
        arrow: <path d="M4 12h15m-6-6 6 6-6 6" />,
        menu: <path d="M4 6h16M4 12h16M4 18h16" />,
        check: <><circle cx="12" cy="12" r="9" /><path d="m8 12 3 3 5-6" /></>,
        clock: <><circle cx="12" cy="12" r="9" /><path d="M12 7v5l4 2" /></>,
        code: <><path d="m8 6-6 6 6 6m8-12 6 6-6 6m-3-14-2 16" /></>,
        chart: <><path d="M4 3v17h17M8 15v-4m5 4V7m5 8V4" /></>,
        search: <><circle cx="10.5" cy="10.5" r="6.5" /><path d="m16 16 5 5" /></>,
        alert: <><path d="M10 4a2.3 2.3 0 0 1 4 0l8 14a2 2 0 0 1-2 3H4a2 2 0 0 1-2-3Z" /><path d="M12 9v5m0 3h.01" /></>,
        spark: <><path d="m12 3 2.5 6.5L21 12l-6.5 2.5L12 21l-2.5-6.5L3 12l6.5-2.5L12 3Z" /></>,
    };
    return <SvgIcon viewBox="0 0 24 24" {...props} sx={{ fill: 'none', stroke: 'currentColor', strokeWidth: 1.65, strokeLinecap: 'round', strokeLinejoin: 'round', ...props.sx }}>{paths[name]}</SvgIcon>;
}
