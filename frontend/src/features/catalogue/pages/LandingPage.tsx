import type { ReactNode } from 'react';
import { Box, Button, Typography } from '@mui/material';
import { Link } from 'react-router';
import { BrandWordmark } from '../../../shared/components/BrandLogo';
import { PathSpine } from '../../../shared/components/PathSpine';
import { PresentationIcon } from '../../../shared/components/PresentationIcon';
import type { PresentationIconName } from '../../../shared/components/PresentationIcon';
import { color, presentationTokens, radius } from '../../../app/theme/theme';

export interface LandingPageProps {
    /** The labelled example Mastery Map illustration, composed by the caller (lazily loaded in production). */
    exampleMap: ReactNode;
}

const steps: { icon: PresentationIconName; title: string; body: string }[] = [
    { icon: 'quiz', title: 'Start with a short diagnostic', body: "Skip what you already know. Or start from the beginning — the course order stays the same either way." },
    { icon: 'book', title: 'Learn in course order', body: 'Lessons, practice quizzes and assignments from your instructor, in one shared path.' },
    { icon: 'map', title: 'See your skills and the next step', body: 'Quizzes update each skill as Unknown, Learning, Mastered or Weak. Every recommendation says why.' },
];

const onChrome = { '&& :focus-visible:not([tabindex="-1"]), && .Mui-focusVisible': { outline: `2px solid ${color.focusOnChrome}`, outlineOffset: 2 } };

/**
 * S25 Public landing page at `/` for visitors without a session. Public navigation:
 * Catalogue, Sign in and Create account. No testimonials, statistics or logos.
 */
export function LandingPage({ exampleMap }: LandingPageProps) {
    return <Box sx={{ bgcolor: color.ink950, minHeight: '100dvh', ...onChrome }}>
        <Box component="a" href="#main-content" sx={{ position: 'absolute', left: 16, top: -64, zIndex: 10, px: 2, py: 1.25, borderRadius: `${radius.sm}px`, bgcolor: color.surface, color: color.ink950, fontWeight: 600,
            '&:focus': { top: 12 } }}>Skip to main content</Box>
        <Box component="header" sx={{ display: 'flex', alignItems: 'center', gap: { xs: 1, sm: 2 }, px: { xs: 2, sm: 4 }, height: { xs: 64, sm: 80 }, maxWidth: 1440, mx: 'auto' }}>
            <BrandWordmark size={presentationTokens.brandMark} />
            <Box sx={{ flex: 1 }} />
            <Box component="nav" aria-label="Main navigation" sx={{ display: 'flex', alignItems: 'center', gap: { xs: .5, sm: 1.5 } }}>
                <Button component={Link} to="/courses" variant="text" color="inherit" sx={{ color: color.surface, px: { xs: 1.25, sm: 2 } }}>Catalogue</Button>
                <Button component={Link} to="/login" variant="text" color="inherit" sx={{ color: color.surface, px: { xs: 1.25, sm: 2 } }}>Sign in</Button>
                <Button component={Link} to="/register" variant="contained" size="small" sx={{ display: { xs: 'none', sm: 'inline-flex' }, bgcolor: color.surface, color: color.ink950, '&:hover': { bgcolor: color.fill150 } }}>Create account</Button>
            </Box>
        </Box>
        <Box component="main" id="main-content" tabIndex={-1}>
            <Box component="section" aria-labelledby="landing-title" sx={{ px: { xs: 2, sm: 4 }, pt: { xs: 6, md: 10 }, pb: { xs: 8, md: 12 }, maxWidth: 1440, mx: 'auto', color: color.surface,
                display: 'grid', gap: 6, gridTemplateColumns: { xs: '1fr', lg: 'minmax(0, 5fr) minmax(0, 6fr)' }, alignItems: 'center' }}>
                <Box>
                    <Typography id="landing-title" component="h1" variant="display">Learn programming at the pace of what you actually know.</Typography>
                    <Typography sx={{ mt: 3, color: color.chromeMuted, fontSize: '1.125rem', maxWidth: '46ch' }}>CodeMastery is a course-based platform for first programming courses. Quizzes show which skills you have mastered, and the next step comes with a reason.</Typography>
                    <Box sx={{ mt: 5, display: 'flex', gap: 1.5, flexWrap: 'wrap' }}>
                        <Button component={Link} to="/courses" variant="contained" sx={{ bgcolor: color.surface, color: color.ink950, '&:hover': { bgcolor: color.fill150 } }}>Browse courses</Button>
                        <Button component={Link} to="/register" variant="outlined" sx={{ bgcolor: color.chromeRaised, color: color.surface, '&:hover': { bgcolor: color.chromeHover } }}>Create account</Button>
                    </Box>
                </Box>
                <Box sx={{ bgcolor: color.canvas100, borderRadius: `${radius.xl}px`, p: { xs: 1.5, sm: 2.5 }, color: color.ink900 }}>{exampleMap}</Box>
            </Box>
            <Box sx={{ bgcolor: color.canvas100, borderRadius: { xs: `${radius.lg}px ${radius.lg}px 0 0`, sm: `${radius.xl}px ${radius.xl}px 0 0` }, px: { xs: 2, sm: 4 }, py: { xs: 6, md: 10 } }}>
                <Box sx={{ maxWidth: 1200, mx: 'auto' }}>
                    <Typography component="h2" variant="h2" sx={{ mb: 4, maxWidth: '24ch' }}>How a course works</Typography>
                    <Box sx={{ maxWidth: 720 }}>
                        <PathSpine listLabel={() => 'How a course works'} sections={[{ id: 'steps', items: steps.map((step, index) => ({
                            id: step.title, node: index === 0 ? 'done' as const : index === 1 ? 'current' as const : 'todo' as const,
                            content: <Box sx={{ pb: 4, pt: 1.25 }}>
                                <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.25 }}><PresentationIcon name={step.icon} sx={{ fontSize: 22, color: color.brand600 }} /><Typography component="h3" variant="h3">{step.title}</Typography></Box>
                                <Typography sx={{ mt: 1, color: 'text.secondary', maxWidth: '56ch' }}>{step.body}</Typography>
                            </Box>,
                        })) }]} density="rows" />
                    </Box>
                    <Box component="section" aria-labelledby="landing-instructors" sx={{ mt: 8, display: 'grid', gap: 3, gridTemplateColumns: { xs: '1fr', md: '1fr 1fr' }, alignItems: 'center' }}>
                        <Box><Typography id="landing-instructors" component="h2" variant="h2">For instructors</Typography>
                            <Typography sx={{ mt: 1.5, color: 'text.secondary', maxWidth: '52ch' }}>Define your course's skills and prerequisites, tag questions by skill and difficulty, and see each learner's skill states and recommendation history — not just a total score.</Typography></Box>
                        <Box sx={{ display: 'flex', gap: 1.5, flexWrap: 'wrap', justifyContent: { md: 'flex-end' } }}><Button component={Link} to="/login" variant="contained">Sign in to teach</Button></Box>
                    </Box>
                </Box>
            </Box>
        </Box>
    </Box>;
}
