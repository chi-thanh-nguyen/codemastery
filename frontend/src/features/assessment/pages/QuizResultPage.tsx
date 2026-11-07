import type { ReactNode } from 'react';
import { Box, Button, Typography } from '@mui/material';
import { Page } from '../../../layouts/DashboardLayout';
import { PageHeader } from '../../../shared/components/PageHeader';
import type { Crumb } from '../../../shared/components/PageHeader';
import { StatGroup, StatTile, Tile } from '../../../shared/components/Tile';
import { MasteryTag, StatusPill } from '../../../shared/components/StatusPill';
import { PresentationIcon } from '../../../shared/components/PresentationIcon';
import { CodeBlock } from '../../../shared/components/CodeBlock';
import type { ReviewItemView, SkillChangeView } from '../types/assessmentTypes';
import { color, radius } from '../../../app/theme/theme';

export interface QuizResultPageProps {
    quizTitle: string;
    kindLabel: string;
    score: number;
    correct: number;
    total: number;
    attemptNumber: number;
    submittedAtLabel: string;
    review: ReviewItemView[];
    /** Only when the owning contract returns them; never computed in the client. */
    skillChanges?: SkillChangeView[];
    recommendation?: ReactNode;
    onBack: () => void;
    onHistory?: () => void;
    onOpenMap?: () => void;
    back?: Crumb;
    notice?: ReactNode;
}

/** S12 Quiz result: score, per-question correctness, skill changes and the next step (US-05/US-07). */
export function QuizResultPage({ quizTitle, kindLabel, score, correct, total, attemptNumber, submittedAtLabel, review, skillChanges, recommendation, onBack, onHistory, onOpenMap, back, notice }: QuizResultPageProps) {
    return <Page width="reading">
        <PageHeader title={quizTitle} subtitle={`${kindLabel} · Submitted ${submittedAtLabel}`} back={back}
            actions={<>{onHistory && <Button variant="outlined" onClick={onHistory}>Attempt history</Button>}<Button variant="contained" onClick={onBack}>Back to course</Button></>} />
        {notice}
        <Box sx={{ mb: 5 }}><StatGroup label="Result">
            <StatTile value={<>{score}<Box component="span" sx={{ fontSize: '1.5rem' }}>%</Box></>} label="Score" tone="sky" />
            <StatTile value={`${correct}/${total}`} label="Correct" tone="mint" />
            <StatTile value={attemptNumber} label="Attempt" tone="white" />
        </StatGroup></Box>
        {skillChanges && skillChanges.length > 0 && <Box component="section" aria-labelledby="result-skills" sx={{ mb: 5 }}>
            <Typography id="result-skills" variant="h2" sx={{ mb: 2 }}>Skills updated</Typography>
            <Box component="ul" sx={{ listStyle: 'none', m: 0, p: 0, bgcolor: color.surface, borderRadius: `${radius.lg}px`, overflow: 'hidden' }}>
                {skillChanges.map((change) => <Box component="li" key={change.skillId} sx={{ display: 'flex', flexWrap: 'wrap', alignItems: 'center', gap: 1.5, px: 3, py: 2, borderBottom: `1px solid ${color.line200}`, '&:last-child': { borderBottom: 0 } }}>
                    <Typography sx={{ fontWeight: 600, flex: '1 1 160px' }}>{change.name}</Typography>
                    <MasteryTag state={change.before} /><PresentationIcon name="arrow-right" aria-label="changed to" sx={{ fontSize: 18, color: 'text.secondary' }} /><MasteryTag state={change.after} />
                </Box>)}
            </Box>
            {onOpenMap && <Button variant="text" sx={{ mt: 1.5 }} onClick={onOpenMap} endIcon={<PresentationIcon name="arrow-right" />}>Open Mastery Map</Button>}
        </Box>}
        {recommendation && <Box component="section" aria-labelledby="result-next" sx={{ mb: 5, maxWidth: 560 }}>
            <Typography id="result-next" variant="h2" sx={{ mb: 2 }}>Next step</Typography>{recommendation}
        </Box>}
        <Box component="section" aria-labelledby="result-review">
            <Typography id="result-review" variant="h2" sx={{ mb: 2 }}>Question review</Typography>
            <Box component="ol" sx={{ listStyle: 'none', m: 0, p: 0, display: 'grid', gap: 2 }}>
                {review.map((item) => <Tile key={item.questionId} component="li">
                    <Box sx={{ display: 'flex', alignItems: 'flex-start', gap: 2, flexWrap: 'wrap', justifyContent: 'space-between' }}>
                        <Box sx={{ minWidth: 0, flex: '1 1 320px' }}>
                            <Typography variant="caption" sx={{ color: 'text.secondary' }}>Question {item.position}</Typography>
                            <Typography variant="h4" component="h3" sx={{ mt: .5 }}>{item.prompt}</Typography>
                        </Box>
                        <StatusPill size="tag" tone={item.correct ? 'mint' : 'rose'} icon={item.correct ? 'check-circle' : 'alert-circle'}>{item.correct ? 'Correct' : 'Incorrect'}</StatusPill>
                    </Box>
                    {item.code && <CodeBlock code={item.code} />}
                    <Typography variant="body2" sx={{ mt: 1.5, maxWidth: '70ch' }}><Box component="span" sx={{ color: 'text.secondary' }}>Your answer: </Box>{item.selectedLabels.length ? item.selectedLabels.join(', ') : 'Not answered'}</Typography>
                    {item.correctLabels && !item.correct && <Typography variant="body2" sx={{ mt: .5, maxWidth: '70ch' }}><Box component="span" sx={{ color: 'text.secondary' }}>Correct answer: </Box>{item.correctLabels.join(', ')}</Typography>}
                </Tile>)}
            </Box>
        </Box>
    </Page>;
}
