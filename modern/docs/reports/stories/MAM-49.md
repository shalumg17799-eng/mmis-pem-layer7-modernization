```
Phase / item:      MAM-49 Revalidation case states: re-screening requested and provider no longer active
Outcome:           BUILT (build-phase: tests in Part D), batch A1
What was done:     - Spec check: Jira MAM-49 description = modern/specs/MAM-49.md (same 4 ACs)
                   - Built in revalidation-service: RevalidationEventsListener reads the service's own
                     pem.revalidation.events: once a RevalidationDue is published its case moves DUE -> IN_SCREENING
                     (C-13); FollowProvider: every ProviderStatusChanged updates the read model, and a status other than
                     A (S, T, D, P) cancels the open case with closedAt (C-14, D-006); a cancelled case is never reopened
                     (completion only touches DUE/IN_SCREENING); a reinstated, still overdue provider has no open case,
                     so the next sweep opens a new one (MAM-45 rule)
                   - AC status: AC-1 built, checked live (the 2032 sweep's case was IN_SCREENING once its event was out);
                     AC-2 built, not checked live (needs a sanction on a tracked provider with an open case); AC-3 built
                     (close() only updates open cases); AC-4 built (falls out of MAM-45 + AC-2), not checked live
Files created/changed: revalidation-service application/FollowProvider, ProviderTracking, CaseStages,
                     RequestScreeningStage; infrastructure/RevalidationEventsListener, ProviderEventsListener (now via
                     FollowProvider), JdbcRevalidationStore (markInScreening)
                   test drafts for Part D (not run): modern/docs/reports/stories/MAM-49/part-d-drafts/
Checks run:        compile + bootJar + pem-apps healthy + live check above
Not verified:      automated tests (Part D); AC-2..AC-4 live
Questions for you: none
```

## Lane 3 tests (2026-11-20)

Outcome line unchanged. `MAM-49.tests.sha256` holds 17 files (recorded 2026-11-21, `test_protection.py check MAM-49`: OK). Tests were
written after the code (see `MAM-49/red-report.md`); all 60 tests of this story pass (run 2).

- Test files: `MAM-49.feature` and `RevalidationSteps` (Cucumber, 16 scenarios and examples), unit `FollowProviderTest`,
  `CaseStagesAndListTest`, `CompleteCaseTest`, `ExecuteSweepTest`, `CaseStatusTest`; integration `EventListenersTest`,
  `JdbcRevalidationStoreTest`, `PlatformChecksTest`; contract `ConsumedEventFixturesTest`; support files shared with MAM-45 and
  MAM-48.
- Tests per AC (JUnit + Cucumber examples, run 2): AC-1 16, AC-2 31, AC-3 6, AC-4 7.
- Edge cases: each of S, T and D with a DUE and with an IN_SCREENING case; a provider that stays ACTIVE keeps its case; only
  the provider that left ACTIVE loses its case; closed cases are not touched; the DUE to IN_SCREENING step happens once and only
  from DUE (also through the real relay and Kafka, 45 s guard); a failed cancel or step rolls back with the processed-event
  marker and the redelivered event applies once; a late PASS or FAIL does not reopen a cancelled case; a reinstated overdue
  provider gets a new case and the cancelled case stays listed; the unique index allows a new open case next to closed ones.
- Commands and results: as in the MAM-45 section (run 2, 2026-11-21 03:24-03:34): 529 tests of the module, 19 failed (none of
  MAM-49), 0 skipped; PIT 41/41 (100 %); JaCoCo line coverage 99.74 % computed from the report.
- Defects: none. Note on the task text of this lane: MAM-49 is not "what happens on a failed re-screening" (that is MAM-48 AC-5,
  tested there); the tests follow the spec of MAM-49.
- Not verified: Jira text against the spec; code review.
