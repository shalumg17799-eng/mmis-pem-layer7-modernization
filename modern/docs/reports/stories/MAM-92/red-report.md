# MAM-92 red report: Console, view and change the risk rules (admin)

Run on 2026-11-19 (fast mode), before any production code of the story existed (`/console/admin/risk-rules` was a placeholder).
Hashes: `modern/docs/reports/stories/MAM-92.tests.sha256` (3 files), restore copies in `red-blobs.txt`.

| Level | Command | Tests | Result | Reason |
|---|---|---|---|---|
| Vitest | `vitest run tests/admin/risk-rules.test.tsx` (PowerShell, lowercase drive) | 27 | 24 failed, 3 passed | no h2 "Version N (default tier: ...)" and no table (23), no labelled cells (rest). The 3 that passed are the negative role guards that MAM-62 already satisfies (analyst1, supervisor1, provider1 get Access denied and no risk-rules request). |
| Playwright | `playwright test e2e/admin-risk.spec.ts` | 6 | 6 failed | the page has no h2 "Version N" (6 x timeout on the first expectation) |

AC to test: AC-1 the legacy values with version, default tier, labelled cells and no specialty names; AC-2 add a row and save as version 2 with the loaded version in the PUT body;
AC-3 change a tier, remove a row, change the default tier, the PUT body holds exactly those rows, the tier choices are H, M, L only; AC-4 '20', '2o7' and an empty prefix send nothing, the summary links to the cell, the message is the cell description;
AC-5 422 and 400 show the detail and reload the table the service holds, the rejected table is not shown as saved; AC-6 admin1 sees the table, analyst1, supervisor1 and provider1 are denied with no request, a 403 on save says "You are not allowed to change the risk rules";
AC-7 axe scans (table, field errors, page after a refusal) and a keyboard-only edit and save in a real browser.

Reading fixed by the tests: the prefix is a text input with the accessible name "Taxonomy prefix, row N", the tier a select named "Tier, row N", Remove is named "Remove row N (prefix)"; Add row moves the focus to the new prefix; a prefix is validated as `^[0-9A-Z]{3}$` before anything is sent.

## Additive tests after the code review of the web UI (2026-11-20, MUST-FIX 1: lost update)

Cause: **the code-reviewer of the web UI returned a MUST-FIX on `RiskRulesPage.tsx:97`**: the editable table is built once from the first answer, but the PUT sends `query.data.version`, the version of the newest answer. The query uses the TanStack defaults (`main.tsx` sets only `retry: false`), so a stale query is refetched when the window is focused again. admin1 edits version 1, admin2 saves version 2, admin1 comes back to the tab, and Save then sends version 2 with the rows of version 1: the service accepts it and admin2's change is gone with no 422. The AC-5 tests above could not see this (their fake always answers 422 and never compares the version sent, and no test refetches between load and save). Under Q-097 C (standing rule): **additive only, no assertion weakened or removed, production code untouched**.

New tests in `tests/admin/risk-rules.test.tsx`, a new `describe` "[MAM-92 AC-5] a table that another administrator saved meanwhile is never overwritten (lost update)". The fake service holds one table and accepts a PUT only with the version it holds (then it stores version + 1), else 422, as the contract `replaceRiskRules` says. The test loads version 1, adds the row 207, lets the service move to version 2 (prefix 111, default tier Moderate), simulates the tab switch with `focusManager.setFocused(false)` then `(true)` (the real trigger of the refetch; the world's QueryClient has the same defaults as `main.tsx`), and chooses Save.

| Test | Result before the fix | Reason |
|---|---|---|
| Save sends the version the rows are based on (1), not the version of the newest answer (2) | FAIL | `AssertionError: expected 2 to be 1` (PUT body version) |
| the table admin2 saved (version 2, prefix 111) is still what the service holds after Save | FAIL | `expected { version: 3, defaultTier: 'L', ... } to deeply equal { version: 2, defaultTier: 'M', ... }`: admin2's table was overwritten |
| the service answers 422, the page shows its detail, does not say "saved", and shows the table the service holds now (version 2) | FAIL | `Unable to find an element with the text: The table was changed by someone else: you edited version 1, the service holds version 2`: the page took the PUT as accepted |
| while the unsaved rows of admin1 (version 1 plus 207) are on screen, the page does not call them "Version 2" | FAIL | `expected <h2></h2> to be null`, received `Version 2 (default tier: Moderate (M))` above the rows of version 1 |

The tests do not prescribe the fix: a draft that keeps its base version and sends it, or `refetchOnWindowFocus: false` on this query, both satisfy them (the wait for the refetch is bounded, so a page that does not refetch is accepted). Control run (scratch copy, deleted): with the `focusManager` step removed, i.e. a page that does not carry a newer answer into the PUT, all 4 pass, so the fake and the assertions are satisfiable. The other 27 tests of the file still pass (31 in the file now).

Exact change (line endings LF, kept): previous blob `cf48fb87eb7388e530e356d3f4000887181c21b3`, new blob `bac0701878ceae791f3efbbb016263e786796a29`; 89 lines added, 2 lines changed (the two import lines `@testing-library/react` and `vitest`, widened by `act` and `afterEach`), plus the import of `focusManager` and of the type `RiskRuleSet` (both additive).
`tests/support/risk.ts` and `e2e/admin-risk.spec.ts` are unchanged. `tsc --noEmit -p tsconfig.json` and `eslint --max-warnings 0` on the file: no findings.
