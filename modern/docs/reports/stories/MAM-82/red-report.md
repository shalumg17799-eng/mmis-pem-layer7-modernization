# MAM-82 red report: Console detail panels (screening, sanctions, revalidation, notices, audit)

Run on 2026-11-19 (fast mode), before any production code of the story existed (the detail page had no panels).
Hashes: `modern/docs/reports/stories/MAM-82.tests.sha256` (3 files), restore copies in `red-blobs.txt`.

| Level | Command | Tests | Result | Reason |
|---|---|---|---|---|
| Vitest | `vitest run tests/console/detail-panels.test.tsx` (PowerShell, lowercase drive) | 32 | 32 failed | no panel found: `no panel "Screening" yet` (9), `"Sanctions"` (6), `"Revalidation"` (7), `"Notices"` (3), `"Audit"` (4), no panel at all for the role test (3) |
| Playwright | `playwright test e2e/console-panels.spec.ts` | 6 | 6 failed | the section with the heading "Screening" (3), the audit row, the Sanctions failure message and the Revalidation failure message are not on the page |

AC to test: AC-1 call with npi, reason + date + overall result, the 5 columns and the OIGLEIE row "E203 owner excluded", four Pass rows, Conditional, "Not screened yet" (5);
AC-2 call, 4 columns and rows, Terminate (T), "None recorded", "Showing 1 of 45" (4); AC-3 call, due date and 4 columns, four case states, "No revalidation case" (6);
AC-4 call, Sent / Notice / Status, "No notices sent" (2); AC-5 supervisor1 and admin1, "No audit entries" (3); AC-6 analyst1, supervisor1, admin1 and the no-403 check (4);
AC-7 5xx with the others normal, Retry, recovery, Revalidation / Notices / Screening 404, Audit 500 (7); AC-8 structure (1). Playwright: three roles populated + axe, audit row, failed panel with keyboard Retry + axe, revalidation and audit endpoints not built (404).

Reading fixed by the tests: panel failures say "The screening results / sanctions / revalidation cases / notices / audit history could not be loaded" with Retry; only the first page (20) of a list is shown and the panel says "Showing 20 of N" when there is more.

## Test correction under Q-097 (UI polish B-iv, user decision 2026-11-19)

Cause: **the UI polish of 2026-11-19 (B-iv)**, under the Q-097 rule and the user's explicit permission to use it for any selector change. The key-value tables "Provider record", "Provider", "Application" and the portal home's "My provider record" are now USWDS summary lists (`<section aria-labelledby>`, a region named by its title, holding a `<dl>` of `div > dt + dd`); the label "Screen flag" is "Screening result"; the screening sources are shown by name (Licence board, OIG exclusion list (LEIE), SAM.gov debarment, SSA death records); and the status page has a step indicator whose step-2 segment reads "Validated" ("Validation failed" for a rejected application). Scoping and renaming only: no assertion was weakened or removed; production code unchanged. Test titles keep their wording unless said otherwise.

What changed in MAM-82:
- `detail-panels.test.tsx` (AC-1): the source rows are found by the names the page shows ("OIG exclusion list (LEIE)", "Licence board", "SAM.gov debarment", "SSA death records").
- `console-panels.spec.ts` (AC-6, AC-7): the screening rows are filtered by "Licence board" and "SAM.gov debarment".
- **Found by my run, not in the list from the UI agent (text only):** the audit panel now shows the action with its code, "Approve (APPROVE)" instead of "APPROVE"
  (`detail-panels.test.tsx` AC-5, 2 tests; `console-panels.spec.ts` AC-5). The expected cell text follows; the check stays exact. Please confirm with the UI agent that this is intended.

Exact changes (`-` before, `+` after; the previous blob is the state at the start of this correction, line endings kept, a plain `diff` against it shows only these lines):

| File | Previous blob | New blob | Line endings | Changed lines |
|---|---|---|---|---|
| `tests/console/detail-panels.test.tsx` | `85b946047f320be8937bdf039e8d44c18ef7e31e` | `e2b629e375e71af6240e2ca2bd497591da4306b3` | CRLF | 12 |
| `e2e/console-panels.spec.ts` | `dc351c0b24ef8691e20355bedacb2feb19b70e52` | `d25eabe9cb0e23307bee40761290501a2ff5c194` | LF | 6 |

`tests/console/detail-panels.test.tsx` (CRLF)
```diff
@@ -41 +41 @@
-    expect(panelRows(panel)).toContainEqual(['OIGLEIE', 'Fail (F)', '03/02/2026', 'Owner 1990000111 is on the OIG exclusion list', 'E203 owner excluded']);
+    expect(panelRows(panel)).toContainEqual(['OIG exclusion list (LEIE)', 'Fail (F)', '03/02/2026', 'Owner 1990000111 is on the OIG exclusion list', 'E203 owner excluded']);
@@ -51,4 +51,4 @@
-      ['LICBOARD', 'Pass (P)'],
-      ['OIGLEIE', 'Pass (P)'],
-      ['SAMGOV', 'Pass (P)'],
-      ['SSADMF', 'Pass (P)'],
+      ['Licence board', 'Pass (P)'],
+      ['OIG exclusion list (LEIE)', 'Pass (P)'],
+      ['SAM.gov debarment', 'Pass (P)'],
+      ['SSA death records', 'Pass (P)'],
@@ -189 +189 @@
-    expect(panelRows(panel)).toEqual([['03/02/2026', 'analyst1', 'APPROVE']]);
+    expect(panelRows(panel)).toEqual([['03/02/2026', 'analyst1', 'Approve (APPROVE)']]);
```

`e2e/console-panels.spec.ts` (LF)
```diff
@@ -67 +67 @@
-    await expect(panel(page, 'Screening').getByRole('row').filter({ hasText: 'LICBOARD' }).getByRole('cell').nth(1)).toHaveText('Pass (P)');
+    await expect(panel(page, 'Screening').getByRole('row').filter({ hasText: 'Licence board' }).getByRole('cell').nth(1)).toHaveText('Pass (P)');
@@ -79 +79 @@
-  await expect(panel(page, 'Audit').getByRole('row').filter({ hasText: 'analyst1' }).getByRole('cell')).toHaveText(['03/02/2026', 'analyst1', 'APPROVE']);
+  await expect(panel(page, 'Audit').getByRole('row').filter({ hasText: 'analyst1' }).getByRole('cell')).toHaveText(['03/02/2026', 'analyst1', 'Approve (APPROVE)']);
@@ -89 +89 @@
-  await expect(panel(page, 'Screening').getByRole('row').filter({ hasText: 'SAMGOV' })).toHaveCount(1);
+  await expect(panel(page, 'Screening').getByRole('row').filter({ hasText: 'SAM.gov debarment' })).toHaveCount(1);
```

Runs (2026-11-19; the host was short of memory, so one worker): before the correction Vitest had 77 failures in 11 files (570 of 647 passed, as reported by the lead and re-run here)
and Playwright 46 failures in 8 specs (106 of 152, as reported by the lead). After it: the full Vitest suite (`--maxWorkers=1 --testTimeout=60000 --hookTimeout=60000`, PowerShell, lowercase path)
**647 of 647 pass** in 29 files; the full default Playwright suite (16 specs in 4 batches, `--workers=1`, journeys excluded) **152 of 152 pass**; `npx tsc -b` and `npx eslint --max-warnings 0`
on the changed files: no findings. The journey J3 (MAM-74) was not run (no journeys, no seeding).
**Hashes:** re-recorded with `test_protection.py record <KEY>` with exactly the files of the story's `.tests.sha256`; `check` and `sha256sum -c` are OK.

## Test correction under Q-097 (plain dates + Reference, user decision 2026-11-19 19:54)

Cause: **the user decisions of 2026-11-19 19:54 that the UI now implements**, under the Q-097 C standing rule (superseded tests; scoping and wording only, no assertion weakened or removed, production code unchanged). **A2, plain dates:** every displayed date reads "19 Nov 2026", the day without a leading zero (`formatDate` in `web-ui/src/shared/format/format.ts`); typed date inputs stay MM/DD/YYYY, so no value a test types into an input was changed. **A1, application status page:** the application ID is the single text "Reference: <id>" under the h1, the "Application" row of the summary list is gone (the list keeps its title "Application", so region lookups still work), and the paragraph "Your application was accepted. ..." is removed.

Exact changes (`-` before, `+` after; the previous blob is the state at the start of this correction, line endings kept, a plain `diff` against it shows only these lines):

| File | Previous blob | New blob | Line endings | Changed lines (removed / added) |
|---|---|---|---|---|
| `e2e/console-panels.spec.ts` | `d25eabe9cb0e23307bee40761290501a2ff5c194` | `2dd69242288cba07ef113ba8a4cf7554978eeba4` | LF | 3 / 3 |
| `tests/console/detail-panels.test.tsx` | `e2b629e375e71af6240e2ca2bd497591da4306b3` | `c5b15dd241c251fd49c91c8e2b3f8a3fc6591720` | CRLF | 10 / 10 |

`e2e/console-panels.spec.ts` (LF)
```diff
@@ -68 +68 @@ for (const who of ['analyst1', 'supervisor1', 'admin1'] as const) {
-    await expect(panel(page, 'Sanctions').getByRole('row').filter({ hasText: 'Suspend (S)' }).getByRole('cell')).toHaveText(['03/10/2026', 'Suspend (S)', 'Licence under review', 'supervisor1']);
+    await expect(panel(page, 'Sanctions').getByRole('row').filter({ hasText: 'Suspend (S)' }).getByRole('cell')).toHaveText(['10 Mar 2026', 'Suspend (S)', 'Licence under review', 'supervisor1']);
@@ -76 +76 @@ for (const who of ['analyst1', 'supervisor1', 'admin1'] as const) {
-test('[MAM-82 AC-5] supervisor1 sees the audit row 03/02/2026 | analyst1 | APPROVE', async ({ page }) => {
+test('[MAM-82 AC-5] supervisor1 sees the audit row 2 Mar 2026 | analyst1 | APPROVE', async ({ page }) => {
@@ -79 +79 @@ test('[MAM-82 AC-5] supervisor1 sees the audit row 03/02/2026 | analyst1 | APPRO
-  await expect(panel(page, 'Audit').getByRole('row').filter({ hasText: 'analyst1' }).getByRole('cell')).toHaveText(['03/02/2026', 'analyst1', 'Approve (APPROVE)']);
+  await expect(panel(page, 'Audit').getByRole('row').filter({ hasText: 'analyst1' }).getByRole('cell')).toHaveText(['2 Mar 2026', 'analyst1', 'Approve (APPROVE)']);
```

`tests/console/detail-panels.test.tsx` (CRLF)
```diff
@@ -31 +31 @@ describe('[MAM-82 AC-1] screening results per source (journey J3)', () => {
-    expect(text(panel)).toMatch(/Initial screening on 03\/02\/2026/);
+    expect(text(panel)).toMatch(/Initial screening on 2 Mar 2026/);
@@ -41 +41 @@ describe('[MAM-82 AC-1] screening results per source (journey J3)', () => {
-    expect(panelRows(panel)).toContainEqual(['OIG exclusion list (LEIE)', 'Fail (F)', '03/02/2026', 'Owner 1990000111 is on the OIG exclusion list', 'E203 owner excluded']);
+    expect(panelRows(panel)).toContainEqual(['OIG exclusion list (LEIE)', 'Fail (F)', '2 Mar 2026', 'Owner 1990000111 is on the OIG exclusion list', 'E203 owner excluded']);
@@ -85,2 +85,2 @@ describe('[MAM-82 AC-2] the sanctions history (journey J5)', () => {
-      ['03/20/2026', 'Reinstate (R)', 'Licence confirmed', 'supervisor1'],
-      ['03/10/2026', 'Suspend (S)', 'Licence under review', 'supervisor1'],
+      ['20 Mar 2026', 'Reinstate (R)', 'Licence confirmed', 'supervisor1'],
+      ['10 Mar 2026', 'Suspend (S)', 'Licence under review', 'supervisor1'],
@@ -129 +129 @@ describe('[MAM-82 AC-3] revalidation cases, one per provider and period', () =>
-    expect(text(panel)).toContain('Revalidation due: 02/28/2026');
+    expect(text(panel)).toContain('Revalidation due: 28 Feb 2026');
@@ -132,2 +132,2 @@ describe('[MAM-82 AC-3] revalidation cases, one per provider and period', () =>
-      ['02/28/2026', 'Due', '03/01/2026', '-'],
-      ['02/28/2021', 'Completed', '03/01/2021', '03/15/2021'],
+      ['28 Feb 2026', 'Due', '1 Mar 2026', '-'],
+      ['28 Feb 2021', 'Completed', '1 Mar 2021', '15 Mar 2021'],
@@ -168,2 +168,2 @@ describe('[MAM-82 AC-4] notices sent to the provider', () => {
-      ['03/10/2026', 'Suspension notice (SUS)', 'Sent'],
-      ['03/20/2026', 'Reinstatement notice (RST)', 'Failed'],
+      ['10 Mar 2026', 'Suspension notice (SUS)', 'Sent'],
+      ['20 Mar 2026', 'Reinstatement notice (RST)', 'Failed'],
@@ -189 +189 @@ describe('[MAM-82 AC-5] the audit trail (supervisor and administrator)', () => {
-    expect(panelRows(panel)).toEqual([['03/02/2026', 'analyst1', 'Approve (APPROVE)']]);
+    expect(panelRows(panel)).toEqual([['2 Mar 2026', 'analyst1', 'Approve (APPROVE)']]);
```

Test titles that named the old format were corrected for honesty (AC tags kept); typed date inputs were not touched.

**Runs (2026-11-19, one worker because the host was short of memory):** before the correction the full Vitest suite (`--maxWorkers=1 --testTimeout=60000 --hookTimeout=60000`, PowerShell, lowercase path) had **36 failures in 15 files (611 of 647 pass)**: 35 are exactly the lines of the A1/A2 list; the 36th is a timing failure in an unedited file, see the MAM-64 note. After the correction: **647 of 647 pass in 29 files**. `npx tsc -b` (it includes `tests` and `e2e`) and `eslint --max-warnings 0` on the 24 changed files: no findings.
The full default Playwright suite (16 specs, journeys excluded, `--workers=1`, `NODE_OPTIONS=--dns-result-order=ipv4first`, one run): **152 of 152 pass** (8.4 min). The Playwright suite was not run before the correction (no time; the UI agent's static list was used), so there is no before count for it.

**Hashes:** re-recorded with `test_protection.py record MAM-82` with exactly the files of the story's `.tests.sha256`; `check` and `sha256sum -c` are OK.

Record correction (lead, 2026-11-19 20:58, code-reviewer round 2): line(s) 82, 97 labelled the file LF; it is CRLF (byte check by the reviewer). Only the label changed; the test file and its hash are unchanged.
