
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
