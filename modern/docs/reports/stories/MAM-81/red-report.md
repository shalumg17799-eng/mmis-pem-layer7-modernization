# MAM-81 red report (corrections of recorded tests)

This file starts with the first correction of a recorded test of the story; no earlier red report existed in this folder.

## 2026-11-20 20:25 Correction of the recorded journey test (Q-097 C), by the test-writer: the REVALQ download is bound to the run

File: `modern/web-ui/e2e/journeys/j6-revalidation.spec.ts` (previous blob `cffab52a599666efbefeb6b01402d239bfe6e7fe`; new blob `f1b4015b7c521539ac08ac700f65ce16f6eae257`; LF).
Why: the independent review of the J7 correction (2026-11-20) found the same position-based pattern here that made J7 pass on the wrong row: `downloadFile` clicked the first Download button of the label ("Revalidation sweep"). It is not a false pass today (one sweep row exists
at that point), but it would take another run if the download moved after the second sweep or if sweeps were ever listed from the service.
What changed: `downloadFile(page, row, filePath, savedName)` as in J7: the status cell (third cell) of the row of the run must read `Completed`, the button is clicked in that row, the browser must send `GET /api/v1/batch/revalidation-sweeps/{runId}/queue-file` for the run the journey started,
and the saved name must be `REVALQ.D20260331`. The wait before the download now reads the status cell of the one "Revalidation sweep" row (`toHaveCount(1)` then `toHaveText('Completed')`) instead of filtering the row text by /Completed/.
No assertion was removed or weakened: the REVALQ record assertions, the API/browser byte comparison, the case, due-date and axe assertions are unchanged.
Run (stack lock taken, seed before and after, 2026-11-20 20:23): **PASSED** (16.8 s), as required: the journey must still pass.
Hashes: `test_protection.py record MAM-81`; `check MAM-81` prints OK.
