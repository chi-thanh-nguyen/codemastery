// `npm run stack:down`: reconciles whatever the disposable E2E run left behind, under the r2
// protocol (ADR008 CT34-FIXTURES-2026-11-10-r2). It is also the step the lifecycle coordinator runs
// after every Playwright execution, so normal teardown and interrupted-run recovery follow exactly
// the same path, the same persistent inventory and the same exclusive lock.
//
// It must be entered through fixtures/lifecycle.py, which holds that lock: a direct invocation
// would reconcile ownership state without exclusivity, so it fails closed instead. Removal is
// limited to resources the inventory proves belong to the recorded run; anything live, foreign,
// unreadable, ambiguous or unaccounted is preserved and reported, and that makes this command exit
// nonzero so an incomplete recovery can never look like success.
import { reconcile } from './stack-recovery.mjs';

const argv = process.argv.slice(2);
const flag = (name, fallback = null) => {
    const at = argv.indexOf(name);
    return at === -1 || at === argv.length - 1 ? fallback : argv[at + 1];
};

const inventory = process.env.CODEMASTERY_E2E_INVENTORY;
if (!inventory) {
    process.stderr.write('stack-down: this command must run through the lifecycle coordinator, which holds the exclusive '
        + 'lock for this checkout. Use `npm run stack:down` from e2e/.\n');
    process.exit(2);
}

const outcome = await reconcile(inventory, { reason: flag('--reason', 'stack:down') });
const counts = (outcome.record?.intents ?? []).reduce((all, intent) => {
    all[intent.state] = (all[intent.state] ?? 0) + 1;
    return all;
}, {});
const summary = Object.entries(counts).map(([state, total]) => `${total} ${state}`).join(', ') || 'no recorded resources';
process.stdout.write(`stack-down: ${outcome.record?.runId ?? 'no run'} — ${summary}; `
    + `${outcome.complete ? 'recovery complete' : 'RECOVERY INCOMPLETE (inventory retained for retry)'}\n`);
process.exit(outcome.complete ? 0 : 1);
