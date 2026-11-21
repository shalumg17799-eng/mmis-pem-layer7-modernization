# MAM-75 red report (tmsis-service, the extract run: archive, counts, ExtractProduced)

No `red-report.md` existed for MAM-75 before 2026-11-21. Written by the lead from the test-writer's result and the diff
against commit a6a8814 (the test-writer of that round could not write report files).

## Test correction under Q-097 C and the user's decision Q-132 (2026-11-21)

Recorded files of this story that changed in the review fix of 2026-11-21: `TestProviderService.java` (test support: the fake
provider-service answers by key as contract 1.2.0 says), `acceptance/ExtractSteps.java` (steps added; the step that asserted
page numbers became a step for the keys) and `features/MAM-75.feature` (one scenario appended for AC-6: a run that was
RUNNING when the service stopped is FAILED after the start, with the reason "interrupted: tmsis-service stopped during
the run"). New for this story: `ServiceRestart.java` (support) and `infrastructure/InterruptedRunRecoveryTest` (9 tests, red
before the fix: the run stayed RUNNING and its job execution STARTED). No assertion of this story was changed or removed.
The full record, with the reasons, is in `../MAM-71/red-report.md` (both sections).
