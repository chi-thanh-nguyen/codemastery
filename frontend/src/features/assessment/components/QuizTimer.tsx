import { useEffect, useRef, useState } from 'react';
import { Box } from '@mui/material';
import { PresentationIcon } from '../../../shared/components/PresentationIcon';
import { radius, roles } from '../../../app/theme/theme';

const pad = (value: number) => String(value).padStart(2, '0');
export const formatClock = (seconds: number) => {
    const whole = Math.max(0, Math.ceil(seconds));
    const hours = Math.floor(whole / 3600), minutes = Math.floor((whole % 3600) / 60), rest = whole % 60;
    return hours > 0 ? `${hours}:${pad(minutes)}:${pad(rest)}` : `${pad(minutes)}:${pad(rest)}`;
};

/**
 * Countdown display. The caller supplies remaining time (from the server's expiry
 * when a contract exists). Presentation thresholds only: butter at 20% left,
 * rose at the last minute (or 10%, whichever is shorter). Announced once per change.
 */
export function QuizTimer({ remainingSeconds, totalSeconds }: { remainingSeconds: number; totalSeconds: number }) {
    const urgent = remainingSeconds <= Math.min(60, totalSeconds * 0.1), low = remainingSeconds <= totalSeconds * 0.2;
    const level = urgent ? 'urgent' : low ? 'low' : 'normal';
    const [announcement, setAnnouncement] = useState('');
    const previous = useRef(level);
    useEffect(() => {
        if (level !== previous.current) {
            setAnnouncement(level === 'urgent' ? 'Less than a minute left.' : level === 'low' ? `${Math.ceil(remainingSeconds / 60)} minutes left.` : '');
            previous.current = level;
        }
    }, [level, remainingSeconds]);
    const palette = urgent ? { bg: roles.tone.rose.tint, fg: roles.tone.rose.strong } : low ? { bg: roles.tone.butter.tint, fg: roles.tone.butter.strong } : { bg: roles.bg.surface, fg: roles.text.primary };
    return <Box sx={{ display: 'inline-flex', alignItems: 'center', gap: 1, height: 40, px: 2, borderRadius: `${radius.pill}px`, bgcolor: palette.bg, color: roles.text.primary, fontWeight: 600 }}>
        <PresentationIcon name="clock" sx={{ fontSize: 18, color: palette.fg }} />
        <Box component="span" className="cm-tabular" aria-label={`Time left ${formatClock(remainingSeconds)}`} role="timer">{formatClock(remainingSeconds)}</Box>
        <span role="status" aria-live="polite" className="cm-visually-hidden">{announcement}</span>
    </Box>;
}
