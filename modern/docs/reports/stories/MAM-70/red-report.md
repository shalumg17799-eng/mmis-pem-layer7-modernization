# MAM-70 red report: Portal, see my notices

Run on 2026-11-19 (fast mode), before any production code of the story existed (`/portal/notices` was a placeholder).
Hashes: `modern/docs/reports/stories/MAM-70.tests.sha256` (3 files), restore copies in `red-blobs.txt`.

| Level | Command | Tests | Result | Reason |
|---|---|---|---|---|
| Vitest | `npx vitest run tests/portal/notices.test.tsx --pool=vmThreads` | 17 | 17 failed | no notices table (9), "No notices yet" missing (1), no 20 rows (1), "The notices could not be loaded. Try again." missing (6) |
| Playwright | `npx playwright test e2e/portal-notices.spec.ts` | 4 | 4 failed | the approval row, "No notices yet", the failure message and "Page 1 of 2" are not on the page |

AC to test: AC-1 call without npi, columns and row, h1 + caption (3); AC-2 (1); AC-3 all templates and an unknown one (1); AC-4 Sent / Failed (1);
AC-5 only own notices, no input and no npi in request or address (2); AC-6 none, 45 with paging, <= 20 without, 401 / 500 / 503 / network,
no redirect after a 401, Retry (9); Playwright: notices + axe, none + axe, failure + axe, paging with the keyboard + axe.

Reading fixed by the tests: a 401 on this page is shown as "The notices could not be loaded. Try again." as AC-6 says, not turned into a
sign-in redirect (`get(..., { endSessionOn401: false })`); a notice whose NPI is not the token's NPI is dropped by the page even if the API returned one.

## Test correction under Q-097 (plain dates + Reference, user decision 2026-11-19 19:54)

Cause: **the user decisions of 2026-11-19 19:54 that the UI now implements**, under the Q-097 C standing rule (superseded tests; scoping and wording only, no assertion weakened or removed, production code unchanged). **A2, plain dates:** every displayed date reads "19 Nov 2026", the day without a leading zero (`formatDate` in `web-ui/src/shared/format/format.ts`); typed date inputs stay MM/DD/YYYY, so no value a test types into an input was changed. **A1, application status page:** the application ID is the single text "Reference: <id>" under the h1, the "Application" row of the summary list is gone (the list keeps its title "Application", so region lookups still work), and the paragraph "Your application was accepted. ..." is removed.

Exact changes (`-` before, `+` after; the previous blob is the state at the start of this correction, line endings kept, a plain `diff` against it shows only these lines):

| File | Previous blob | New blob | Line endings | Changed lines (removed / added) |
|---|---|---|---|---|
| `e2e/portal-notices.spec.ts` | `f49fba2b87b9b8bd319700b41847c69493ad1e94` | `181e8df47c7e6f42fd03cc8fd420d24239a2b3c3` | LF | 1 / 1 |
| `tests/portal/notices.test.tsx` | `8b486d5592427b8e54d999673597a1533f22e166` | `2c5dc8ac12facf975dee74c7176fb764ba3df7c4` | LF | 2 / 2 |

`e2e/portal-notices.spec.ts` (LF)
```diff
@@ -34 +34 @@ test('[MAM-70 AC-1][MAM-70 AC-7] provider1 sees the approval notice and the page
-  await expect(row.getByRole('cell')).toHaveText(['03/05/2026', 'Enrolment approved (APPROVED)', 'Email', 'Sent']);
+  await expect(row.getByRole('cell')).toHaveText(['5 Mar 2026', 'Enrolment approved (APPROVED)', 'Email', 'Sent']);
```

`tests/portal/notices.test.tsx` (LF)
```diff
@@ -33 +33 @@ describe('[MAM-70 AC-1] the approval notice is listed (journey J1)', () => {
-  it('[MAM-70 AC-1] the table has the columns Sent, Notice, Channel and Status and the row 03/05/2026, "Enrolment approved (APPROVED)", Email, Sent', async () => {
+  it('[MAM-70 AC-1] the table has the columns Sent, Notice, Channel and Status and the row 5 Mar 2026, "Enrolment approved (APPROVED)", Email, Sent', async () => {
@@ -38 +38 @@ describe('[MAM-70 AC-1] the approval notice is listed (journey J1)', () => {
-    expect(noticeRows()).toEqual([['03/05/2026', 'Enrolment approved (APPROVED)', 'Email', 'Sent']]);
+    expect(noticeRows()).toEqual([['5 Mar 2026', 'Enrolment approved (APPROVED)', 'Email', 'Sent']]);
```

Test titles that named the old format were corrected for honesty (AC tags kept); typed date inputs were not touched.

**Runs (2026-11-19, one worker because the host was short of memory):** before the correction the full Vitest suite (`--maxWorkers=1 --testTimeout=60000 --hookTimeout=60000`, PowerShell, lowercase path) had **36 failures in 15 files (611 of 647 pass)**: 35 are exactly the lines of the A1/A2 list; the 36th is a timing failure in an unedited file, see the MAM-64 note. After the correction: **647 of 647 pass in 29 files**. `npx tsc -b` (it includes `tests` and `e2e`) and `eslint --max-warnings 0` on the 24 changed files: no findings.
The full default Playwright suite (16 specs, journeys excluded, `--workers=1`, `NODE_OPTIONS=--dns-result-order=ipv4first`, one run): **152 of 152 pass** (8.4 min). The Playwright suite was not run before the correction (no time; the UI agent's static list was used), so there is no before count for it.

**Hashes:** re-recorded with `test_protection.py record MAM-70` with exactly the files of the story's `.tests.sha256`; `check` and `sha256sum -c` are OK.
