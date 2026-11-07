import type { ReactNode } from 'react';
import { SvgIcon } from '@mui/material';
import type { SvgIconProps } from '@mui/material';

// Original outline glyphs on a 24 px grid: 1.75 stroke, round caps/joins, currentColor.
// No external icon font or icon package (DESIGN.md › Iconography).
const paths = {
    // Navigation and objects
    home: <><path d="M4 10.5 12 4l8 6.5V19a1 1 0 0 1-1 1h-4.5v-5.5h-5V20H5a1 1 0 0 1-1-1Z" /></>,
    book: <><path d="M12 6.5C10 5 6.5 4.5 4 5.2v13.3c2.5-.7 6-.2 8 1.3 2-1.5 5.5-2 8-1.3V5.2c-2.5-.7-6-.2-8 1.3Z" /><path d="M12 6.5v13.3" /></>,
    catalogue: <><rect x="4" y="4" width="6.5" height="6.5" rx="1.6" /><rect x="13.5" y="4" width="6.5" height="6.5" rx="1.6" /><rect x="4" y="13.5" width="6.5" height="6.5" rx="1.6" /><rect x="13.5" y="13.5" width="6.5" height="6.5" rx="1.6" /></>,
    grid: <><rect x="4" y="4" width="6.5" height="6.5" rx="1.6" /><rect x="13.5" y="4" width="6.5" height="6.5" rx="1.6" /><rect x="4" y="13.5" width="6.5" height="6.5" rx="1.6" /><rect x="13.5" y="13.5" width="6.5" height="6.5" rx="1.6" /></>,
    'my-courses': <><path d="M6 4h11a1 1 0 0 1 1 1v15l-6-3.5L6 20Z" /></>,
    path: <><circle cx="6" cy="6" r="2" /><circle cx="18" cy="18" r="2" /><path d="M8 6h6a3 3 0 0 1 0 6h-4a3 3 0 0 0 0 6h6" strokeDasharray="2.5 2.5" /></>,
    map: <><circle cx="6" cy="17" r="2.5" /><circle cx="12" cy="7" r="2.5" /><circle cx="18.5" cy="14" r="2.5" /><path d="m7.5 15 3.2-5.6m3.4-1 2.8 4.4" /></>,
    graph: <><circle cx="6" cy="17" r="2.5" /><circle cx="12" cy="7" r="2.5" /><circle cx="18.5" cy="14" r="2.5" /><path d="m7.5 15 3.2-5.6m3.4-1 2.8 4.4" /></>,
    quiz: <><rect x="5" y="3.5" width="14" height="17" rx="2.5" /><path d="M9 9.2a3 3 0 1 1 3.9 2.9c-.6.2-.9.7-.9 1.3v.6M12 17h.01" /></>,
    assignment: <><path d="M14 3.5H7a2 2 0 0 0-2 2v13a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2v-10Z" /><path d="M14 3.5v5h5M9 13h6M9 16.5h4" /></>,
    file: <><path d="M14 3.5H7a2 2 0 0 0-2 2v13a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2v-10Z" /><path d="M14 3.5v5h5" /></>,
    text: <><path d="M5 6h14M5 10h14M5 14h10M5 18h7" /></>,
    video: <><rect x="3.5" y="6" width="12.5" height="12" rx="2.5" /><path d="m16 10.5 4.5-2.5v8l-4.5-2.5" /></>,
    bell: <><path d="M6 10a6 6 0 0 1 12 0c0 4 1.5 6 2 6.5H4c.5-.5 2-2.5 2-6.5Z" /><path d="M10 19.5a2.2 2.2 0 0 0 4 0" /></>,
    user: <><circle cx="12" cy="8.5" r="3.8" /><path d="M5 20c.7-3.6 3.5-5.6 7-5.6s6.3 2 7 5.6" /></>,
    users: <><circle cx="9" cy="9" r="3.4" /><path d="M3 19.5c.6-3.1 3-4.9 6-4.9s5.4 1.8 6 4.9" /><path d="M15 5.8a3.4 3.4 0 0 1 0 6.4M17.5 14.9c1.8.6 3.1 2.2 3.5 4.6" /></>,
    shield: <><path d="M12 3.5 5 6v5.5c0 4.3 2.9 7.6 7 9 4.1-1.4 7-4.7 7-9V6Z" /><path d="m9 12 2.2 2.2L15.5 10" /></>,
    flag: <><path d="M5.5 21V4.5" /><path d="M5.5 4.5h11l-2.2 4 2.2 4h-11" /></>,
    audit: <><rect x="5" y="3.5" width="14" height="17" rx="2.5" /><path d="M9 8h6M9 12h6M9 16h3" /></>,
    chart: <><path d="M4 4v16h16" /><path d="M8.5 16v-4M13 16V8M17.5 16v-6" /></>,
    message: <><path d="M5 5h14a1.5 1.5 0 0 1 1.5 1.5v9A1.5 1.5 0 0 1 19 17h-8l-4.5 3.5V17H5a1.5 1.5 0 0 1-1.5-1.5v-9A1.5 1.5 0 0 1 5 5Z" /></>,
    reply: <><path d="M10 7 4.5 12l5.5 5" /><path d="M5 12h9a5.5 5.5 0 0 1 5.5 5.5V19" /></>,
    send: <><path d="m4 12 16-8-5 16-3-6.5Z" /><path d="m12 13.5 3.5-3.5" /></>,
    code: <><path d="m8 7-5 5 5 5m8-10 5 5-5 5m-2.5-12-3 14" /></>,
    layers: <><path d="m3 8 9-5 9 5-9 5-9-5Zm0 5 9 5 9-5M3 18l9 5 9-5" /></>,
    sliders: <><path d="M4 7h9m4 0h3M4 17h3m4 0h9" /><circle cx="15" cy="7" r="2" /><circle cx="9" cy="17" r="2" /></>,
    // Actions
    search: <><circle cx="10.5" cy="10.5" r="6" /><path d="m15 15 5 5" /></>,
    filter: <><path d="M4 5h16l-6.2 7.5V19l-3.6-1.8v-4.7Z" /></>,
    plus: <><path d="M12 5v14M5 12h14" /></>,
    minus: <><path d="M5 12h14" /></>,
    edit: <><path d="M14.5 5.5 18.5 9.5 9 19H5v-4Z" /><path d="m12.5 7.5 4 4" /></>,
    trash: <><path d="M4.5 7h15M10 4h4M6.5 7l.9 12.1A1.5 1.5 0 0 0 8.9 20.5h6.2a1.5 1.5 0 0 0 1.5-1.4L17.5 7M10 11v6M14 11v6" /></>,
    eye: <><path d="M2.5 12S6 5.5 12 5.5 21.5 12 21.5 12 18 18.5 12 18.5 2.5 12 2.5 12Z" /><circle cx="12" cy="12" r="3" /></>,
    'eye-off': <><path d="M4 4l16 16M10 6c.6-.3 1.3-.4 2-.4 6 0 9.5 6.4 9.5 6.4a17 17 0 0 1-2.6 3.4M14.1 14.2A3 3 0 0 1 9.8 9.9M6.5 7.5C4 9.2 2.5 12 2.5 12S6 18.4 12 18.4c1.5 0 2.9-.4 4.1-1" /></>,
    upload: <><path d="M12 15V4.5M7.5 9 12 4.5 16.5 9" /><path d="M4.5 15v3a2 2 0 0 0 2 2h11a2 2 0 0 0 2-2v-3" /></>,
    download: <><path d="M12 4.5V15M7.5 10.5 12 15l4.5-4.5" /><path d="M4.5 15v3a2 2 0 0 0 2 2h11a2 2 0 0 0 2-2v-3" /></>,
    external: <><path d="M14 4.5h5.5V10M19.5 4.5 11 13" /><path d="M17.5 13.5v4a2 2 0 0 1-2 2h-9a2 2 0 0 1-2-2v-9a2 2 0 0 1 2-2h4" /></>,
    copy: <><rect x="8.5" y="8.5" width="11" height="11" rx="2" /><path d="M15.5 8.5V6.5a2 2 0 0 0-2-2h-7a2 2 0 0 0-2 2v7a2 2 0 0 0 2 2h2" /></>,
    refresh: <><path d="M19.5 12a7.5 7.5 0 1 1-2.2-5.3" /><path d="M19.5 4.5v4h-4" /></>,
    more: <><path d="M6 12h.01M12 12h.01M18 12h.01" strokeWidth={3} /></>,
    menu: <><path d="M4 7h16M4 12h16M4 17h16" /></>,
    close: <><path d="M6 6l12 12M18 6 6 18" /></>,
    'sign-out': <><path d="M14 4.5h3.5a2 2 0 0 1 2 2v11a2 2 0 0 1-2 2H14" /><path d="M10 8 6 12l4 4M6 12h9.5" /></>,
    lock: <><rect x="5" y="10.5" width="14" height="10" rx="2.5" /><path d="M8 10.5V8a4 4 0 0 1 8 0v2.5" /></>,
    unlock: <><rect x="5" y="10.5" width="14" height="10" rx="2.5" /><path d="M8 10.5V8a4 4 0 0 1 7.6-1.7" /></>,
    play: <><path d="M8 5.5v13l10.5-6.5Z" /></>,
    // Arrows
    arrow: <><path d="M4.5 12h15m-6-6 6 6-6 6" /></>,
    'arrow-right': <><path d="M4.5 12h15m-6-6 6 6-6 6" /></>,
    'arrow-left': <><path d="M19.5 12h-15m6-6-6 6 6 6" /></>,
    'chevron-down': <><path d="m6 9 6 6 6-6" /></>,
    'chevron-up': <><path d="m6 15 6-6 6 6" /></>,
    'chevron-right': <><path d="m9 6 6 6-6 6" /></>,
    'chevron-left': <><path d="m15 6-6 6 6 6" /></>,
    'move-up': <><path d="M12 19V5m-5.5 5.5L12 5l5.5 5.5" /></>,
    'move-down': <><path d="M12 5v14m-5.5-5.5L12 19l5.5-5.5" /></>,
    // Time
    clock: <><circle cx="12" cy="12" r="8.5" /><path d="M12 7.5V12l3 2" /></>,
    calendar: <><rect x="4" y="5.5" width="16" height="14.5" rx="2.5" /><path d="M4 10h16M8.5 3.5v4M15.5 3.5v4" /></>,
    // States
    check: <><path d="m5 12.5 4.5 4.5L19 7.5" /></>,
    'check-circle': <><circle cx="12" cy="12" r="8.5" /><path d="m8.2 12.2 2.6 2.6 5-5.3" /></>,
    'half-circle': <><circle cx="12" cy="12" r="8.5" /><path d="M12 3.5a8.5 8.5 0 0 1 0 17Z" fill="currentColor" /></>,
    'dashed-circle': <><circle cx="12" cy="12" r="8.5" strokeDasharray="3.2 3" /></>,
    'alert-triangle': <><path d="M10.3 4.6a2 2 0 0 1 3.4 0l7.4 12.8a2 2 0 0 1-1.7 3H4.6a2 2 0 0 1-1.7-3Z" /><path d="M12 9.5v4.5M12 17h.01" /></>,
    alert: <><path d="M10.3 4.6a2 2 0 0 1 3.4 0l7.4 12.8a2 2 0 0 1-1.7 3H4.6a2 2 0 0 1-1.7-3Z" /><path d="M12 9.5v4.5M12 17h.01" /></>,
    'alert-circle': <><circle cx="12" cy="12" r="8.5" /><path d="M12 7.5v5.5M12 16.5h.01" /></>,
    info: <><circle cx="12" cy="12" r="8.5" /><path d="M12 11v5.5M12 7.5h.01" /></>,
    route: <><circle cx="6.5" cy="18" r="2" /><path d="M8.5 18h6a3.5 3.5 0 0 0 0-7h-5a3.5 3.5 0 0 1 0-7h8" /><path d="m15 1.5 2.5 2.5L15 6.5" /></>,
    skip: <><path d="m5 6 7 6-7 6Z" /><path d="m12 6 7 6-7 6M19.5 5.5v13" /></>,
    star: <><path d="m12 4 2.4 4.9 5.4.8-3.9 3.8.9 5.4L12 16.4 7.2 18.9l.9-5.4-3.9-3.8 5.4-.8Z" /></>,
    // Map controls
    'zoom-in': <><circle cx="10.5" cy="10.5" r="6" /><path d="m15 15 5 5M8 10.5h5M10.5 8v5" /></>,
    'zoom-out': <><circle cx="10.5" cy="10.5" r="6" /><path d="m15 15 5 5M8 10.5h5" /></>,
    fit: <><path d="M4.5 9V4.5H9M15 4.5h4.5V9M19.5 15v4.5H15M9 19.5H4.5V15" /></>,
    list: <><path d="M9 6.5h11M9 12h11M9 17.5h11M4.5 6.5h.01M4.5 12h.01M4.5 17.5h.01" /></>,
    grip: <><path d="M9 6h.01M9 12h.01M9 18h.01M15 6h.01M15 12h.01M15 18h.01" strokeWidth={2.6} /></>,
    spark: <><path d="m12 3 2.5 6.5L21 12l-6.5 2.5L12 21l-2.5-6.5L3 12l6.5-2.5L12 3Z" /></>,
    flame: <><path d="M13 2c1 5-4 6-4 10-2-1-3-3-3-3-1 2-2 4-2 6a8 8 0 0 0 16 0c0-5-4-9-7-13Z" /></>,
} satisfies Record<string, ReactNode>;

export type PresentationIconName = keyof typeof paths;

export function PresentationIcon({ name, ...props }: SvgIconProps & { name: PresentationIconName }) {
    return <SvgIcon viewBox="0 0 24 24" aria-hidden {...props}
        sx={{ fill: 'none', stroke: 'currentColor', strokeWidth: 1.75, strokeLinecap: 'round', strokeLinejoin: 'round', ...props.sx }}>{paths[name]}</SvgIcon>;
}
