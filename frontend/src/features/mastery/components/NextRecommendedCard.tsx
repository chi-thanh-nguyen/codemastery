import { Typography } from '@mui/material';
import { GuidanceEntry } from '../../../shared/components/GuidanceStack';
import type { GuidanceEntryData } from '../../../shared/components/GuidanceStack';
import { recommendationMeta } from '../../../shared/components/StatusPill';
import type { RecommendationView } from '../types/masteryTypes';

/** Guidance entry for one recommendation: type, target lesson and the backend's reason, verbatim. */
export function recommendationEntry(recommendation: RecommendationView, onOpen?: (lessonId: string) => void): GuidanceEntryData {
    const meta = recommendationMeta[recommendation.type];
    return {
        id: recommendation.id, tone: 'lilac', icon: meta.icon, type: `Recommended · ${meta.label}`, meta: recommendation.createdAtLabel,
        body: <>
            <Typography sx={{ fontWeight: 600 }}>{recommendation.targetLessonTitle}</Typography>
            <Typography variant="body2" sx={{ mt: .5, color: 'text.secondary' }}>{recommendation.reason}</Typography>
        </>,
        action: onOpen ? { label: 'Open lesson', onClick: () => onOpen(recommendation.targetLessonId) } : undefined,
    };
}

export function NextRecommendedCard({ recommendation, onOpen }: { recommendation: RecommendationView; onOpen?: (lessonId: string) => void }) {
    return <GuidanceEntry component="div" entry={recommendationEntry(recommendation, onOpen)} />;
}
