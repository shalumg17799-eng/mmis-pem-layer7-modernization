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
