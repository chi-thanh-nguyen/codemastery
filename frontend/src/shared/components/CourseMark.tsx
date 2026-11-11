import { Box } from '@mui/material';
import { color, radius } from '../../app/theme/theme';
import { PresentationIcon } from './PresentationIcon';
import pythonLogo from './course-logo-python.svg';
import typescriptLogo from './course-logo-typescript.svg';

/**
 * Course marks (7 November 2026): the official full-colour logo of the programming language a
 * course teaches, in one neutral container, sized optically rather than by the file's box.
 *
 * Logo sources (unaltered artwork, official colours):
 * - Python: python-logo-only.svg from https://www.python.org/community/logos/. The file has no
 *   viewBox, so one equal to its own canvas was added to let an <img> scale it; nothing else changed.
 *   "Python" and the Python logos are trademarks or registered trademarks of the Python Software
 *   Foundation; used here only to name the language a course teaches (PSF Trademark Usage Policy).
 * - TypeScript: ts-logo-256.svg from the asset pack at https://www.typescriptlang.org/branding/
 *   (byte-identical). TypeScript is a trademark of Microsoft Corporation.
 *
 * The course API has no language field, so the language is read from what the course says about
 * itself: its title, else its description, and only when exactly one language is named. A course
 * that names no language, several languages, or one without an approved logo here gets the generic
 * course mark instead (a Deep Logo Blue code glyph in the same neutral container); no logo is ever guessed.
 */
export type CourseLanguage = 'python' | 'typescript';

interface LogoSpec {
    name: string;
    src: string;
    /** File width ÷ height. */
    aspect: number;
    /** Visible artwork inside the file, as fractions of its width and height (Python's file includes a soft shadow below). */
    art: { x: number; y: number; w: number; h: number };
    /** Longer side of the visible artwork as a fraction of the container: the per-logo optical size. */
    scale: number;
}

/** The one registry of course logos. Add a language here with its asset, source and optical scale. */
export const COURSE_LOGOS: Record<CourseLanguage, LogoSpec> = {
    // Open, two-colour interlocking shape: it reads light, so it may fill more of the container.
    // 0.66 also keeps the official soft shadow (below the visible artwork) inside the container.
    python: { name: 'Python', src: pythonLogo, aspect: 111.16136 / 134.66811, art: { x: 0, y: 0, w: 1, h: 0.8344 }, scale: 0.66 },
    // A solid filled square carries much more visual weight than an open shape of the same size.
    typescript: { name: 'TypeScript', src: typescriptLogo, aspect: 1, art: { x: 0, y: 0, w: 1, h: 1 }, scale: 0.56 },
};

// Every language name that may appear in a course title or description. Only names with an entry in
// COURSE_LOGOS resolve to a logo; the others still count, so "Python and Java" is ambiguous, not Python.
const LANGUAGE_NAMES: { pattern: RegExp; id: CourseLanguage | null }[] = [
    { pattern: /\bpython\b/i, id: 'python' },
    { pattern: /\btypescript\b/i, id: 'typescript' },
    { pattern: /\bjavascript\b/i, id: null }, { pattern: /\bjava\b/i, id: null }, { pattern: /(?:^|[^\w+#])c\+\+(?![\w+])/i, id: null },
    { pattern: /(?:^|[^\w+#])c#(?!\w)/i, id: null }, { pattern: /\b(?:kotlin|swift|ruby|php|rust|golang|scala|haskell|dart|perl|lua|matlab|fortran|cobol)\b/i, id: null },
];

function languageIn(text: string | null | undefined): CourseLanguage | null | 'none' {
    if (!text) return 'none';
    const named = LANGUAGE_NAMES.filter(({ pattern }) => pattern.test(text));
    if (named.length === 0) return 'none';
    return named.length === 1 ? named[0].id : null;
}

/** The course's language when its own title (else description) names exactly one language with a logo. */
export function courseLanguage(course: { title: string; description?: string | null }): CourseLanguage | null {
    const fromTitle = languageIn(course.title);
    if (fromTitle !== 'none') return fromTitle;
    const fromDescription = languageIn(course.description);
    return fromDescription === 'none' ? null : fromDescription;
}

export interface CourseMarkProps { title: string; description?: string | null; size?: number }

/** Decorative course mark (the title names the course): language logo, or the generic code glyph, in one container shape. */
export function CourseMark({ title, description, size = 48 }: CourseMarkProps) {
    const language = courseLanguage({ title, description });
    const logo = language ? COURSE_LOGOS[language] : null;
    // One neutral container for every course: canvas ground, inset hairline, rounded.md at the 48 px
    // tile size (scaled with the mark so smaller marks keep the same squircle).
    const container = { position: 'relative', width: size, height: size, flexShrink: 0, overflow: 'hidden',
        borderRadius: `${Math.round((radius.md * size) / 48)}px`, bgcolor: color.canvas100, boxShadow: `inset 0 0 0 1px ${color.line200}` } as const;
    if (!logo) {
        // No letters: one Deep Logo Blue code glyph, the same for every such course.
        return <Box aria-hidden data-course-mark="generic" sx={{ ...container, display: 'grid', placeItems: 'center', color: color.brand700 }}>
            <PresentationIcon name="code" sx={{ fontSize: Math.round(size * 0.5), display: 'block' }} />
        </Box>;
    }
    // Size the file so the visible artwork's longer side is `scale × size`, then centre that artwork
    // (not the file box) in the container. Proportions are never changed.
    const fileHeight = logo.art.h * (1 / logo.aspect) >= logo.art.w
        ? (logo.scale * size) / logo.art.h
        : (logo.scale * size) / (logo.art.w * logo.aspect);
    const fileWidth = fileHeight * logo.aspect;
    const left = size / 2 - (logo.art.x + logo.art.w / 2) * fileWidth;
    const top = size / 2 - (logo.art.y + logo.art.h / 2) * fileHeight;
    return <Box aria-hidden data-course-mark={language} sx={container}>
        <Box component="img" src={logo.src} alt="" draggable={false} width={fileWidth} height={fileHeight} decoding="async"
            sx={{ position: 'absolute', left, top, width: fileWidth, height: fileHeight, objectFit: 'contain', display: 'block' }} />
    </Box>;
}
