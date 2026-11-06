import { Box, FormControlLabel, Switch, Typography } from '@mui/material';
import { PresentationIcon } from '../../../../shared/components/PresentationIcon';
import { Tile } from '../../../../shared/components/Tile';
import type { ReadinessCheck } from '../types/skillGraphTypes';
import { color } from '../../../../app/theme/theme';

/**
 * Adaptive mode switch, enabled only when every readiness check from the backend passes
 * (US-11). Thresholds and minimum counts come from configuration; none is hard-coded here.
 */
export function AdaptiveSettingsPanel({ checks, enabled, onToggle }: { checks: ReadinessCheck[]; enabled: boolean; onToggle: (next: boolean) => void }) {
    const ready = checks.every((check) => check.met);
    return <Tile padding={{ xs: 3, sm: 4 }} sx={{ maxWidth: 760 }}>
        <Typography variant="h3" component="h2">Adaptive mode</Typography>
        <Typography sx={{ mt: 1, color: 'text.secondary', maxWidth: '62ch' }}>When adaptive mode is on, quiz results update each learner's skill states and CodeMastery recommends the next step with a reason. Course order stays the same.</Typography>
        <Box component="ul" aria-label="Readiness checks" sx={{ listStyle: 'none', m: 0, mt: 3, p: 0, display: 'grid', gap: 1.5 }}>
            {checks.map((check) => <Box component="li" key={check.id} sx={{ display: 'flex', gap: 1.5, alignItems: 'flex-start' }}>
                <PresentationIcon name={check.met ? 'check-circle' : 'alert-triangle'} sx={{ fontSize: 20, mt: .25, color: check.met ? color.mint700 : color.butter800 }} />
                <Box><Typography sx={{ fontWeight: 600 }}>{check.label}<span className="cm-visually-hidden">{check.met ? ', passed' : ', not passed'}</span></Typography>
                    {check.detail && <Typography variant="body2" sx={{ color: 'text.secondary' }}>{check.detail}</Typography>}</Box>
            </Box>)}
        </Box>
        <Box sx={{ mt: 3, pt: 3, borderTop: `1px solid ${color.line200}` }}>
            <FormControlLabel control={<Switch checked={enabled} disabled={!ready && !enabled} onChange={(event) => onToggle(event.target.checked)} />} label={<Typography sx={{ fontWeight: 600 }}>Adaptive mode {enabled ? 'on' : 'off'}</Typography>} />
            {!ready && !enabled && <Typography variant="body2" sx={{ mt: 1, color: color.butter800 }}>Fix the items above to turn on adaptive mode.</Typography>}
        </Box>
    </Tile>;
}
