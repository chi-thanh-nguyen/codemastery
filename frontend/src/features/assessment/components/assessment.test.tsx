// @vitest-environment jsdom
import { act, useState } from 'react';
import type { ReactNode } from 'react';
import { createRoot } from 'react-dom/client';
import type { Root } from 'react-dom/client';
import { ThemeProvider } from '@mui/material';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { appTheme, ThemePreferenceContext } from '../../../app/theme/theme';
import type { ThemePreference } from '../../../app/theme/theme';
import { QuizAttemptPage } from '../pages/QuizAttemptPage';
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

describe('FE34 Theme control in the quiz Focus bar (D10-1)', () => {
    function ThemedAttempt(props: { onAnswer: () => void; onNavigate: () => void; onSubmit: () => void; onExit: () => void }) {
        const [preference, setPreference] = useState<ThemePreference>('system');
        return <ThemePreferenceContext.Provider value={{ preference, systemScheme: 'light', setPreference, previewPreference: null, setPreviewPreference: () => undefined }}>
            <QuizAttemptPage quizTitle="Loops check" questions={[{ id: 'q1', position: 1, kind: 'single_choice', prompt: 'Pick one', options }, { id: 'q2', position: 2, kind: 'single_choice', prompt: 'Pick again', options }]}
                answers={{ q1: ['b'] }} currentIndex={0} remainingSeconds={540} totalSeconds={600} phase="answering" {...props} />
        </ThemePreferenceContext.Provider>;
    }
    it('sits right of the exit button and never touches the timer, answers, navigation or submission', async () => {
        const spies = { onAnswer: vi.fn(), onNavigate: vi.fn(), onSubmit: vi.fn(), onExit: vi.fn() };
        await render(<ThemedAttempt {...spies} />);
        const bar = host.querySelector('header')!;
        const leave = bar.querySelector<HTMLButtonElement>('button[aria-label="Leave quiz"]')!;
        const theme = bar.querySelector<HTMLButtonElement>('button[aria-label^="Theme:"]')!;
        expect(host.querySelectorAll('button[aria-label^="Theme:"]')).toHaveLength(1);
        // Immediately right of the exit button: no focusable control between them.
        const controls = [...bar.querySelectorAll<HTMLElement>('button, a, [tabindex="0"]')];
        expect(controls.indexOf(theme)).toBe(controls.indexOf(leave) + 1);
        const timerText = () => bar.textContent?.match(/\d+:\d{2}/)?.[0];
        const before = timerText();
        const checked = () => [...host.querySelectorAll<HTMLInputElement>('main input[type="radio"]')].map((input) => input.checked);
        const answersBefore = checked();
        await act(async () => theme.click());
        expect(document.activeElement?.getAttribute('role')).toBe('menuitemradio');
        const dark = [...document.querySelectorAll<HTMLElement>('[role="menuitemradio"]')].find((node) => node.textContent?.startsWith('Dark'))!;
        await act(async () => dark.click());
        expect(theme.getAttribute('aria-label')).toBe('Theme: Dark');
        expect(document.activeElement).toBe(dark);
        await act(async () => { dark.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true })); });
        expect(document.activeElement).toBe(theme);
        expect(document.querySelector('[role="menu"]')).toBeNull();
        expect(document.querySelector('[role="dialog"]')).toBeNull(); // no leave or submit confirmation opened
        expect(timerText()).toBe(before);
        expect(checked()).toEqual(answersBefore);
        for (const spy of Object.values(spies)) expect(spy).not.toHaveBeenCalled();
    });
});
