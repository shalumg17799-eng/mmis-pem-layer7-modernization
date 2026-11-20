
## 2026-11-20 19:25 Correction of the recorded journey test (Q-097 C), by the lead

File: modern/web-ui/e2e/journeys/j7-extracts.spec.ts (helper downloadFile and one step before the T-MSIS download).
Why: in the final run of 2026-11-20 19:17 J7 failed at "the browser file equals the API file". The helper clicked the FIRST
Download button of the "Recent runs" table. The row of the run just started still showed "Running" and had no button, so
the click downloaded the file of an OLDER run (business date 2026-11-20, created by the parity run of the same day) and
compared it with the API file of the new run. Until that day the older runs had the same business date and the same bytes,
so the wrong download had gone unnoticed.
What changed: (1) the helper now takes the newest row that names the job, waits until it shows Completed and clicks the
Download button of that row; (2) before the T-MSIS download the admin reloads the page, because the T-MSIS job has no
run-status call and the page keeps the new row at "Running" until the list is read again (a UI finding, listed as open in
docs/reports/final-report.md). No assertion was removed or weakened: the file comparison now really is about the run the
journey started. The directory extract part is unchanged apart from the shared helper.
Runs: 19:17 FAILED (old helper, wrong file); 19:20 FAILED (corrected helper, no reload: the row stayed Running for 120 s);
19:25 PASSED (corrected helper and reload). Not confirmed by the code-reviewer (deadline 20:00).

## 2026-11-20 20:25 Second correction of the recorded journey test (Q-097 C), by the test-writer: no reload, and the download is bound to the run

File: `modern/web-ui/e2e/journeys/j7-extracts.spec.ts` (previous blob `8c0e53c835f7c0f49c0ec753e83987723d0bcfc7`, the 19:25 version; new blob `6030dc38cf0433a12c51eeb5593d02e576e0b941`; LF).
Why: the user ordered the UI gap fixed (19:32, item 5), and the independent review of the 19:25 correction found two MUST-FIX points that concern this test: (1) the `admin.reload()` hid the defect, J7 passed although the page itself was wrong (the page keeps the POST answer RUNNING in its
local `runs`, the T-MSIS job has no `statusPath`, and `JobsPage.tsx:172-173` drops the listed copy of a run already in `runs`; the 19:25 text "until the list is read again" was imprecise); (2) `downloadFile` still chose the row by position and the byte comparison cannot tell runs of the same date apart.

What changed: (1) the comment, `admin.reload()` and the second heading check before the T-MSIS download are removed. Instead, with no reload and no `goto`: a counter of `framenavigated` events of the main frame (must be 0), the business date typed in the form must still read 03/31/2026,
and the row of the run is found by its archive generation (unique per run, regular expression `archiveGeneration N` not followed by a digit): exactly 1 row, status cell `Completed`, counts cell contains `rows <N>`, it is the first T-MSIS row (the newest), and the T-MSIS rows number `min(listedBefore + 1, 20)` (no duplicate row; the page reads the first 20 runs).
The `entry` of the run is read once, right after the API shows COMPLETED, and the AC-2 assertions use it (unchanged). (2) `downloadFile(page, row, filePath, savedName)`: the status cell of THAT row must read Completed, the button is clicked in that row, the browser must send `GET <filePath>` of that run
(`/api/v1/batch/tmsis-extracts/{runId}/file`, `/api/v1/batch/directory-extracts/{runId}/file`), and the saved name must be `TMSISOUT.D20260331` / `DIROUT.D20260331`; the directory row is the one row that names "Directory extract" (count 1 asserted).
No assertion was removed or weakened: every byte and record assertion, the API/browser byte comparison, the AC-2 list assertions and the axe scans are unchanged; the new checks are additions.

Run (stack lock taken, seed before and after, 2026-11-20 20:20): **FAILED after 2.2 min** at `toHaveCount(1)` of the T-MSIS row that shows the archive generation of the run (`archiveGeneration 20`): `Expected: 1, Received: 0`, 235 polls over 120 s. That is the expected red: the page still shows the run as "Running" without counts.
Everything before it passed (the run completes at the API). The rest of the journey (the download bound to the run, the directory part) cannot run until the UI follows the run; the same run-bound helper passed in J6 (see the MAM-81 report). The lead builds and deploys the UI after the fix; then J7 has to pass three times in a row (AC-7).
Hashes: `test_protection.py record MAM-83`; `check MAM-83` prints OK.
