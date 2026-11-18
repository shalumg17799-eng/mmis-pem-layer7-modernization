---
description: Phase 7. Build one area of the web UI (portal or console) story by story with strict TDD, accessibility checks and review. Run for portal, then console.
argument-hint: <portal | console>
---

# Phase 7: Build UI area `$ARGUMENTS`

**Goal:** every screen of this area works against the real services, passes its
component and Playwright tests, and has zero accessibility violations.

## Preconditions
- `$ARGUMENTS` is `portal` or `console`; otherwise stop.
- Phase 5 is `APPROVED`.
- Every service that this area's screens call (see `modern/docs/ux/screen-inventory.md`)
  is `APPROVED` in PROGRESS. If not, list them and ask whether to continue against what
  exists.

## Steps

1. **Story list.** Get the stories of the "Web UI $ARGUMENTS" epic, in dependency order.
   Show the list.
2. **For each story:** follow exactly the same sub-steps as Phase 6 step 2, with these
   differences:
   - test-writer writes the component tests (Vitest + Testing Library) and the
     Playwright test with an axe scan, from the ACs and the wireframe.
   - ui-developer writes the production code (Phase 7 part of its file).
   - The test-protection check covers `modern/web-ui/tests` and `modern/web-ui/e2e`.
   - Verification: `cd modern/web-ui && npm run lint && npm test && npm run e2e`, with
     the full stack up (`make up && make seed`).
3. **Area verification (you):**
   - all of this area's Playwright tests pass with zero axe violations
   - a keyboard-only walk-through of each screen, written in the report as steps
     performed with the result (say clearly if it was not performed)
   - role checks: a PROVIDER cannot see the console, and cannot see another provider's
     data
4. Write `modern/docs/reports/ui-$ARGUMENTS.md`, plus screenshots of each screen
   (Playwright) in `modern/docs/reports/ui-$ARGUMENTS/`.
5. Commit: `feat(web-ui): $ARGUMENTS complete for review`.

## Gate
Show the report and ask for `approve ui $ARGUMENTS`. On approval: move the stories to
Done in Jira, and set the PROGRESS UI row to `APPROVED` (and the Phase 7 row once both
areas are approved).

## Finish
Give the phase report. Next: `/phase-7-build-ui console`, or `/phase-8-e2e-hardening`.
**Stop.**
