import { MasteryTag } from '../../../shared/components/StatusPill';
import type { MasteryState } from '../../../shared/components/StatusPill';

/** Mastery state tag with its one-line meaning as a tooltip. Exactly four states; never a number. */
export function MasteryStateChip({ state, size = 'tag' }: { state: MasteryState; size?: 'pill' | 'tag' }) {
    return <MasteryTag state={state} size={size} withMeaning />;
}
