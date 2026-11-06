import { Box, Button, Typography } from '@mui/material';
import { PresentationIcon } from '../../../shared/components/PresentationIcon';
import { color, radius } from '../../../app/theme/theme';

export interface PlacementPromptProps { onStart: () => void; onSkip: () => void; busy?: boolean }

/** US-03 offer for an enrolled learner without mastery data. Skipping starts the default path. */
export function PlacementPrompt({ onStart, onSkip, busy }: PlacementPromptProps) {
    return <Box component="section" aria-labelledby="placement-title" sx={{ bgcolor: color.lilac100, borderRadius: `${radius.lg}px`, p: { xs: 2.5, sm: 3 }, display: 'flex', gap: 2, alignItems: 'flex-start', flexWrap: 'wrap', mb: 4 }}>
        <PresentationIcon name="quiz" sx={{ fontSize: 24, color: color.lilac700, mt: .25 }} />
        <Box sx={{ flex: '1 1 320px', minWidth: 0 }}>
            <Typography variant="h4" component="h2" id="placement-title">Take a short placement diagnostic</Typography>
            <Typography sx={{ mt: .5, color: color.ink700 }}>CodeMastery uses it to mark what you already know, so you can skip it. You can also start from the beginning.</Typography>
        </Box>
        <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 1, alignSelf: 'center' }}>
            <Button variant="contained" onClick={onStart} disabled={busy}>Start diagnostic</Button>
            <Button variant="text" color="inherit" onClick={onSkip} disabled={busy} sx={{ color: color.ink900 }}>Skip and start from the beginning</Button>
        </Box>
    </Box>;
}
