import { Box, Typography } from '@mui/material';
import type { ChangeEvent } from 'react';
import { CodeBlock } from '../../../shared/components/CodeBlock';
import type { QuestionView } from '../types/assessmentTypes';
import { color, radius } from '../../../app/theme/theme';

export interface QuestionRendererProps { question: QuestionView; selected: string[]; onChange: (optionIds: string[]) => void; disabled?: boolean; total?: number }

/** One question as a native radio/checkbox fieldset; option rows per DESIGN.md › Inputs › Quiz option row. */
export function QuestionRenderer({ question, selected, onChange, disabled, total }: QuestionRendererProps) {
    const multiple = question.kind === 'multiple_choice';
    const name = `question-${question.id}`;
    const toggle = (optionId: string) => (event: ChangeEvent<HTMLInputElement>) => {
        if (!multiple) onChange([optionId]);
        else onChange(event.target.checked ? [...selected, optionId] : selected.filter((id) => id !== optionId));
    };
    return <Box component="fieldset" disabled={disabled} sx={{ border: 0, m: 0, p: 0, minWidth: 0 }}>
        <Typography component="legend" sx={{ p: 0, mb: 2, width: '100%' }}>
            <Typography component="span" variant="caption" sx={{ display: 'block', color: 'text.secondary', mb: 1 }}>Question {question.position}{total ? ` of ${total}` : ''}</Typography>
            <Typography component="span" variant="h3" sx={{ display: 'block', overflowWrap: 'anywhere' }}>{question.prompt}</Typography>
            {multiple && <Typography component="span" variant="body2" sx={{ display: 'block', mt: 1, color: 'text.secondary' }}>Select all that apply.</Typography>}
        </Typography>
        {question.code && <CodeBlock code={question.code} caption="Read the code, then choose an answer. Nothing is run." />}
        <Box sx={{ display: 'grid', gap: 1.5 }}>
            {question.options.map((option, index) => {
                const checked = selected.includes(option.id);
                const letter = String.fromCharCode(65 + index);
                return <Box key={option.id} component="label" sx={{ position: 'relative', display: 'flex', alignItems: 'center', gap: 2, minHeight: 64, px: 2, py: 1.5, cursor: disabled ? 'default' : 'pointer',
                    bgcolor: color.surface, borderRadius: `${radius.md}px`, border: `${checked ? 2 : 1.5}px solid ${checked ? color.brand600 : color.line200}`,
                    '&:hover': disabled ? {} : { borderColor: checked ? color.brand600 : color.ink400 }, '&:has(input:focus-visible)': { outline: `2px solid ${color.brand600}`, outlineOffset: 2 } }}>
                    <Box component="input" type={multiple ? 'checkbox' : 'radio'} name={name} value={option.id} checked={checked} onChange={toggle(option.id)} disabled={disabled}
                        sx={{ position: 'absolute', opacity: 0, width: 1, height: 1, m: 0 }} />
                    <Box aria-hidden sx={{ width: 32, height: 32, borderRadius: multiple ? `${radius.xs}px` : '50%', flexShrink: 0, display: 'grid', placeItems: 'center', fontWeight: 600,
                        bgcolor: checked ? color.brand600 : color.fill150, color: checked ? color.surface : color.ink900 }}>{letter}</Box>
                    <Typography component="span" sx={{ flex: 1, fontSize: '1rem', fontWeight: 500, overflowWrap: 'anywhere' }}>{option.label}</Typography>
                </Box>;
            })}
        </Box>
    </Box>;
}
