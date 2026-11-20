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
