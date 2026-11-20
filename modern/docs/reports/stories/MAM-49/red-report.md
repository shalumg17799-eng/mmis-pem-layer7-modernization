# MAM-49 red report (test-writer, lane 3, 2026-11-21)

Situation: the story was built on 2026-11-19 without tests (Part D), so these tests were written after the code. "Red" means a
test fails only where the code does not meet an acceptance criterion or a lesson of the earlier reviews; tests of code that
already works are **green at once**. Nothing was skipped, disabled or filtered.

- Spec check: the tests follow `modern/specs/MAM-49.md` (4 ACs). Note for the reader: the task text of this lane describes MAM-49
  as "what happens on a failed re-screening"; the spec is different: AC-1 DUE becomes IN_SCREENING once RevalidationDue is out,
  AC-2 a provider that leaves ACTIVE cancels its open case, AC-3 a late result does not reopen a cancelled case, AC-4 a
  reinstated, still overdue provider gets a new case. The failed re-screening is MAM-48 AC-5. The tests follow the spec.
- The drafts of 2026-11-19/20 compiled as written; they were completed, formatted (test sources only) and recorded. The old drafts
  in `MAM-49/part-d-drafts/` are superseded and were left in place.
- Hashes: `MAM-49.tests.sha256` holds 17 files (recorded 2026-11-21 after run 2; `test_protection.py check MAM-49` prints OK).

## Runs

| Run | When | Result |
|---|---|---|
| 1 | 2026-11-21 03:00-03:10 | 499 tests of the module, 22 failed, none of them a test of MAM-49 |
| 2 | 2026-11-21 03:24-03:34 | **529 tests of the module, 19 failed, none of them a test of MAM-49**; PIT 41/41 (100 %); line coverage 99.74 % |

## Tests that fail

None for this story: all 60 tests of MAM-49 pass (run 2).

## Green at once (tests written after the code)

Per AC (JUnit + Cucumber examples, run 2): AC-1 16, AC-2 31, AC-3 6, AC-4 7.

- AC-1: the case moves DUE to IN_SCREENING when its RevalidationDue has been published, through the real relay and Kafka
  (scenario, 45 s guard); the move happens once, from DUE only (a COMPLETED, CANCELLED or IN_SCREENING case is not changed,
  an unknown case is ignored); a failed step leaves no processed-event marker and the redelivered event moves the case once.
- AC-2: S, T and D each cancel a DUE and an IN_SCREENING case (six combinations in unit tests and in the scenario outline),
  `closedAt` is set, the case is listed under CANCELLED and no longer under DUE or IN_SCREENING; a provider that stays ACTIVE
  keeps its case; only the provider that left ACTIVE loses its case; a failed cancel rolls back the tracked status as well.
- AC-3: a late REVALIDATION result (PASS and FAIL) leaves a CANCELLED case unchanged, no error, no new case.
- AC-4: after S to A with the due date 2026-02-28 the sweep of 2026-03-31 opens a new case and the cancelled case stays
  listed; a case that was cancelled while suspended is not swept while the provider is suspended.
- `ConsumedEventFixturesTest` (new in this lane): ProviderStatusChanged from A to each of P, A, T, D, S and the RevalidationDue
  of a case conform to the contract; the checker refuses a status outside the enum.
