// @vitest-environment jsdom
import { act, useState } from 'react';
import type { ReactNode } from 'react';
import { createRoot } from 'react-dom/client';
import type { Root } from 'react-dom/client';
import { ThemeProvider } from '@mui/material';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { appTheme } from '../../../app/theme/theme';
import { QuestionRenderer } from './QuestionRenderer';
import { QuizTimer, formatClock } from './QuizTimer';
import type { QuestionView } from '../types/assessmentTypes';

Object.assign(globalThis, { IS_REACT_ACT_ENVIRONMENT: true });
let root: Root, host: HTMLDivElement;
beforeEach(() => { host = document.createElement('div'); document.body.append(host); root = createRoot(host); });
afterEach(async () => { await act(async () => root.unmount()); host.remove(); });
const render = async (node: ReactNode) => act(async () => root.render(<ThemeProvider theme={appTheme}>{node}</ThemeProvider>));

function Harness({ question }: { question: QuestionView }) {
    const [selected, setSelected] = useState<string[]>([]);
    return <><QuestionRenderer question={question} selected={selected} onChange={setSelected} total={6} /><output>{selected.join(',')}</output></>;
}
const options = [{ id: 'a', label: 'One' }, { id: 'b', label: 'Two' }, { id: 'c', label: 'Three' }];

describe('assessment presentation', () => {
    it('renders single choice as a named radio group in a fieldset with the question as legend', async () => {
        await render(<Harness question={{ id: 'q', position: 2, kind: 'single_choice', prompt: 'Pick one', options }} />);
        expect(host.querySelector('fieldset legend')?.textContent).toContain('Question 2 of 6');
        const radios = [...host.querySelectorAll<HTMLInputElement>('input[type="radio"]')];
        expect(radios).toHaveLength(3); expect(new Set(radios.map((radio) => radio.name)).size).toBe(1);
        await act(async () => radios[1].click()); await act(async () => radios[2].click());
        expect(host.querySelector('output')?.textContent).toBe('c');
    });
    it('renders multiple choice as checkboxes that toggle independently and says so', async () => {
        await render(<Harness question={{ id: 'q', position: 1, kind: 'multiple_choice', prompt: 'Pick many', options }} />);
        expect(host.textContent).toContain('Select all that apply.');
        const boxes = [...host.querySelectorAll<HTMLInputElement>('input[type="checkbox"]')];
        await act(async () => boxes[0].click()); await act(async () => boxes[2].click()); await act(async () => boxes[0].click());
        expect(host.querySelector('output')?.textContent).toBe('c');
    });
    it('shows predict-the-output code as text that is never executed', async () => {
        await render(<Harness question={{ id: 'q', position: 1, kind: 'single_choice', prompt: 'Output?', code: 'print("<b>x</b>")', options }} />);
        expect(host.querySelector('pre code')?.textContent).toBe('print("<b>x</b>")'); expect(host.querySelector('pre b')).toBeNull();
        expect(host.textContent).toContain('Nothing is run.');
    });
    it('formats the clock and exposes it as a timer with a readable name', async () => {
        expect(formatClock(600)).toBe('10:00'); expect(formatClock(59.2)).toBe('01:00'); expect(formatClock(3725)).toBe('1:02:05'); expect(formatClock(-3)).toBe('00:00');
        await render(<QuizTimer remainingSeconds={45} totalSeconds={600} />);
        expect(host.querySelector('[role="timer"]')?.getAttribute('aria-label')).toBe('Time left 00:45');
    });
});
