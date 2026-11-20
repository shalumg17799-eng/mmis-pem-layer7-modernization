# MAM-93 red report: Console, run the batch jobs and download their legacy files (admin)

Run on 2026-11-19 (fast mode), before any production code of the story existed (`/console/admin/jobs` was a placeholder).
Hashes: `modern/docs/reports/stories/MAM-93.tests.sha256` (3 files), restore copies in `red-blobs.txt`.

| Level | Command | Tests | Result | Reason |
|---|---|---|---|---|
| Vitest | `vitest run tests/admin/batch-jobs.test.tsx` (PowerShell, lowercase drive) | 50 | 47 failed, 3 passed | no label "Job" (42), no "Business date (MM/DD/YYYY)" (1), no button "Run job" (2), no run row (1), no "The T-MSIS runs could not be listed" (1). The 3 that passed are the negative role guards that MAM-62 already satisfies (analyst1, supervisor1, provider1 get Access denied and no batch request). |
| Playwright | `playwright test e2e/admin-jobs.spec.ts` (25 s per test for the red run) | 7 | 7 failed | the page has no Job list / date input (6 x timeout on the first locator action, 1 x date input missing) |

AC to test: AC-1 empty form, the date is never filled in, file input + Batch id for the two intakes only, empty submit, three impossible dates, intake without a file (13);
AC-2 POST body, the three statuses in words with counts, a second run (5); AC-3 the three JSON jobs, the T-MSIS list and a new run adding a row, the list failing with Retry (5);
AC-4 multipart with file, businessDate and batchId for both intakes (headers: token, no Content-Type), no batch id, polling until Completed with the counts, polling until Failed and then stopping (6);
AC-5 a failed run with its error, 400 and 422 detail in a summary with the focus, 403, 500 and network (6); AC-6 five downloads byte for byte with the token and a DD file name, the error file of the intake,
no file for the sanction intake, 404, no button for Running or Failed, 5xx (9); AC-7 admin1 and menu entry, three roles denied, the token on every request (5); AC-8 labels, headers, Download names (1).
Playwright: empty + axe, intake selected + axe + field errors, runs + axe, failed run and 422 + axe, keyboard-only run, byte-for-byte download with the token, multipart intake followed to Completed.

Reading fixed by the tests: the rows are the runs started on this page plus the T-MSIS runs the service lists (the other services have no run list); only intakes are polled (QE-7: the other jobs end at the 202);
Download is offered for Completed runs of the five jobs that produce a legacy file and saved as `<DD>.D<yyyymmdd>` (SCRNOUT, REVALQ, DIROUT, TMSISOUT, APPERROR); the page does not choose a date for the administrator.

## Test correction under Q-097 (plain dates + Reference, user decision 2026-11-19 19:54)

Cause: **the user decisions of 2026-11-19 19:54 that the UI now implements**, under the Q-097 C standing rule (superseded tests; scoping and wording only, no assertion weakened or removed, production code unchanged). **A2, plain dates:** every displayed date reads "19 Nov 2026", the day without a leading zero (`formatDate` in `web-ui/src/shared/format/format.ts`); typed date inputs stay MM/DD/YYYY, so no value a test types into an input was changed. **A1, application status page:** the application ID is the single text "Reference: <id>" under the h1, the "Application" row of the summary list is gone (the list keeps its title "Application", so region lookups still work), and the paragraph "Your application was accepted. ..." is removed.

Exact changes (`-` before, `+` after; the previous blob is the state at the start of this correction, line endings kept, a plain `diff` against it shows only these lines):

| File | Previous blob | New blob | Line endings | Changed lines (removed / added) |
|---|---|---|---|---|
| `e2e/admin-jobs.spec.ts` | `e4bb16d12a0e5a39e6851de3ecf7876aebd3b698` | `796afa6491c60682a9494788ccda66d186305580` | LF | 3 / 3 |
| `tests/admin/batch-jobs.test.tsx` | `82c3595ba5933cd9c6d454cf98da13bbd37312cb` | `25c63ae8c9eb920404bb5f7b33cf5dafd86f5c7b` | LF | 6 / 6 |

`e2e/admin-jobs.spec.ts` (LF)
```diff
@@ -72,2 +72,2 @@ test('[MAM-93 AC-2][MAM-93 AC-3][MAM-93 AC-8] runs of the sweep and the screenin
-  await expect(screening.getByRole('cell')).toHaveText(['Screening run', '03/31/2026', 'Completed', 'read 12, written 10', '', 'Download']);
-  await expect(screening.getByRole('button', { name: 'Download Screening run 03/31/2026' })).toBeVisible();
+  await expect(screening.getByRole('cell')).toHaveText(['Screening run', '31 Mar 2026', 'Completed', 'read 12, written 10', '', 'Download']);
+  await expect(screening.getByRole('button', { name: 'Download Screening run 31 Mar 2026' })).toBeVisible();
@@ -125 +125 @@ test('[MAM-93 AC-6] Download saves the legacy file byte for byte, with the admin
-  const download = page.getByRole('button', { name: 'Download Screening run 03/31/2026' });
+  const download = page.getByRole('button', { name: 'Download Screening run 31 Mar 2026' });
```

`tests/admin/batch-jobs.test.tsx` (LF)
```diff
@@ -143 +143 @@ describe('[MAM-93 AC-2] run the revalidation sweep (journey J6)', () => {
-  ] as const)('[MAM-93 AC-2] the new row reads Job "Revalidation sweep", Business date 03/31/2026 and the status %s as "%s" with the counts', async (status, label) => {
+  ] as const)('[MAM-93 AC-2] the new row reads Job "Revalidation sweep", Business date 31 Mar 2026 and the status %s as "%s" with the counts', async (status, label) => {
@@ -150 +150 @@ describe('[MAM-93 AC-2] run the revalidation sweep (journey J6)', () => {
-    expect(row.slice(0, 4)).toEqual(['Revalidation sweep', '03/31/2026', label, 'opened 3, skipped 1']);
+    expect(row.slice(0, 4)).toEqual(['Revalidation sweep', '31 Mar 2026', label, 'opened 3, skipped 1']);
@@ -177 +177 @@ describe('[MAM-93 AC-3] run the screening, directory and T-MSIS jobs (journey J7
-    await waitFor(() => expect(runRows().some((row) => row[0] === label && row[1] === '03/31/2026')).toBe(true));
+    await waitFor(() => expect(runRows().some((row) => row[0] === label && row[1] === '31 Mar 2026')).toBe(true));
@@ -194 +194 @@ describe('[MAM-93 AC-3] run the screening, directory and T-MSIS jobs (journey J7
-    expect(runRows().find((row) => row[0] === 'T-MSIS extract')?.[1]).toBe('02/28/2026');
+    expect(runRows().find((row) => row[0] === 'T-MSIS extract')?.[1]).toBe('28 Feb 2026');
@@ -199 +199 @@ describe('[MAM-93 AC-3] run the screening, directory and T-MSIS jobs (journey J7
-    expect(runRows().filter((row) => row[0] === 'T-MSIS extract').map((row) => row[1]).sort()).toEqual(['02/28/2026', '03/31/2026']);
+    expect(runRows().filter((row) => row[0] === 'T-MSIS extract').map((row) => row[1]).sort()).toEqual(['28 Feb 2026', '31 Mar 2026']);
@@ -470 +470 @@ describe('[MAM-93 AC-8] structure', () => {
-    expect(within(rowOf('Screening run')).getByRole('button', { name: 'Download Screening run 03/31/2026' })).toBeInTheDocument();
+    expect(within(rowOf('Screening run')).getByRole('button', { name: 'Download Screening run 31 Mar 2026' })).toBeInTheDocument();
```

Test titles that named the old format were corrected for honesty (AC tags kept); typed date inputs were not touched.

**Runs (2026-11-19, one worker because the host was short of memory):** before the correction the full Vitest suite (`--maxWorkers=1 --testTimeout=60000 --hookTimeout=60000`, PowerShell, lowercase path) had **36 failures in 15 files (611 of 647 pass)**: 35 are exactly the lines of the A1/A2 list; the 36th is a timing failure in an unedited file, see the MAM-64 note. After the correction: **647 of 647 pass in 29 files**. `npx tsc -b` (it includes `tests` and `e2e`) and `eslint --max-warnings 0` on the 24 changed files: no findings.
The full default Playwright suite (16 specs, journeys excluded, `--workers=1`, `NODE_OPTIONS=--dns-result-order=ipv4first`, one run): **152 of 152 pass** (8.4 min). The Playwright suite was not run before the correction (no time; the UI agent's static list was used), so there is no before count for it.

**Hashes:** re-recorded with `test_protection.py record MAM-93` with exactly the files of the story's `.tests.sha256`; `check` and `sha256sum -c` are OK.

## 2026-11-20 20:25 The T-MSIS run is followed on the Batch jobs page, without a reload (user order 19:32, item 5), by the test-writer

New file `modern/web-ui/tests/admin/batch-jobs-tmsis-follow.test.tsx` (20 tests, LF), added to `MAM-93.tests.sha256` (now 4 files; the other three are byte for byte unchanged).
Why: the page puts the answer of the POST (always RUNNING) into its local `runs`; the T-MSIS job has no `statusPath` (no run-status call in the contract, a list and a file only), so the poller of `JobsPage.tsx` never follows it;
and `JobsPage.tsx:172-173` throws away the service's copy of every run that is already in `runs`. So the row stays "Running" until a reload. (The older wording "until the list is read again" was imprecise: the list IS read again
right after the start, the stale local copy wins.) A refetch of the list alone does not fix it; the listed status has to replace the stale local entry, or the page has to follow the run through the list.

Which story and AC: **MAM-93**, the page story, not the journey story MAM-83. AC-4 ("refreshes the run until its status is Completed or Failed, showing the counts the service returns") is the follow rule, now also for T-MSIS; AC-5 ("no failure is shown as Completed or silently dropped") covers
Failed and the unreadable list; AC-6 (a Completed run has a Download button that requests the file of that run) carries the MAM-83 AC-1 need (the admin downloads the file of the run just started), tagged `[MAM-93 AC-6][MAM-83 AC-1]`; AC-8 is the screen-reader rule.
The spec text of MAM-93 AC-4 names the file intakes only and its "Out of scope" line still says polling beyond the 202 answer is out of scope (QE-7, overtaken by the contracts 1.1.0 for three jobs and by this order for T-MSIS): the spec was not edited (not a test-writer file); the lead decides whether to add one sentence to AC-4 (MODERNIZATION.md section 8: spec and Jira must agree).

| Level | Command | Tests | Result | Reason |
|---|---|---|---|---|
| Vitest | `vitest run tests/admin/batch-jobs-tmsis-follow.test.tsx` (PowerShell, lowercase drive, real timers, 114 s) | 20 | **17 failed, 3 passed** | the row of the started run stays "Running" (the list is never read again after the start): 9 x `expected 'Running' to be 'Completed'` or `'Failed'`; counts `''` instead of `rows 12, archiveGeneration 7`; 4 x the Error cell is `''` instead of "The status could not be refreshed"; 3 x the list is not read again (`expected 0 to be greater than or equal to 2`, `2` vs `4`, `2` vs `5`) |
| Vitest | same file, the 3 regression checks | 3 | 3 passed | the follow of the screening run, the revalidation sweep and the directory extract is unchanged, and the T-MSIS list is not read again for them |

AC to test (all `[MAM-93 AC-n]`): AC-4 (8): Running to Completed from the service list; counts of the finished row; one row per run and the older run keeps its row; the form is not reset (no reload); a run the list does not show yet keeps its Running row; the list is read about once per
`POLL_INTERVAL_MS` while Running (between floor(3.5 s / 1 s) - 1 and ceil(3.5 s / 1 s) + 1 reads: steady, no storm); no further list request once Completed; none once Failed. AC-5 (6): Failed with its error text; no Download on a Failed run; the unreadable list gives the existing wording
"The status could not be refreshed" in the Error cell; the status is not guessed (still Running); another job still runs meanwhile; when the list is readable again the row is Completed with its counts and the note is gone. AC-6 + MAM-83 AC-1 (1): Download of the followed run requests
`/api/v1/batch/tmsis-extracts/{runId}/file` of THAT run with the token, saves `TMSISOUT.D20260331` byte for byte, and never requests the older run's file. Regression AC-4 (3, it.each): the other jobs with a status call (the two intakes keep the tests of `batch-jobs.test.tsx`).
AC-8 (2): while the run is followed, and while the list keeps failing, no live-region text (`role` status or alert, `aria-live`) appears twice; the page has no live region of its own for run status, its live regions are "Loading recent runs" and the alerts, and a poll must not announce them again.
The list handler answers after 40 ms, so that a read under way can show in the page (a MutationObserver then sees a "Loading..." flash on every poll).

**Checked that the tests can go green and catch the defects** (in a copy of `src` and `tests` in the session's scratchpad, never in the repository): a small fix (read the list every `POLL_INTERVAL_MS` while a local T-MSIS entry is RUNNING in the list, show the listed status and counts, set the failure note while the list read fails)
made 70 of 70 pass (the 20 new tests and the 50 of `batch-jobs.test.tsx`). A first version of that fix kept asking after the run had ended (the follow was keyed on the local status): the two "stops asking" tests failed with `expected 5 to be 3`. A variant that shows "Loading recent runs" on every refetch
made both AC-8 tests fail with `[ 'Loading recent runs' ] to deeply equal []`.

`tsc --noEmit -p tsconfig.json` and `eslint --max-warnings 0` on the new file: no findings. Hashes: `test_protection.py record MAM-93` with the three files of the story plus the new file; `check MAM-93` prints OK.
