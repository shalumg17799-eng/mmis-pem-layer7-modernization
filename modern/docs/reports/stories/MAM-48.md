```
Phase / item:      MAM-48 Revalidation cases: list them and complete one
Outcome:           BUILT (build-phase: tests in Part D), batch A1
What was done:     - Spec check: Jira MAM-48 description = modern/specs/MAM-48.md (same 8 ACs)
                   - Built in revalidation-service: GET /api/v1/revalidations (ANALYST, SUPERVISOR; status DUE,
                     IN_SCREENING, COMPLETED, CANCELLED, else 400; npi 10 digits; page/size); POST
                     /api/v1/revalidations/{caseId}/completion (SYSTEM, ANALYST; screeningId + result PASS|FAIL) ->
                     200 COMPLETED with closedAt; 404 unknown; 409 already COMPLETED/CANCELLED (case unchanged);
                     ScreeningCompleted consumer: reason REVALIDATION (PASS or FAIL) completes the provider's open case,
                     any other reason or no open case is ignored; the due date is never set here (D-003, C-10)
                   - AC status: AC-1 built, checked live (200 page, status=OPEN 400); AC-2 built, checked live (ANALYST
                     200 COMPLETED with closedAt); AC-3 built, checked live (unknown 404, second completion 409);
                     AC-4 built (consumer), not seen live (the live REVALIDATION screening could not run: the tracked
                     NPI was no longer in provider-service after a seed); AC-5 built (FAIL completes; read model only
                     changes from ProviderStatusChanged; publishes nothing); the cross-service part "listed by GET
                     /api/v1/screenings?result=FAIL" is screening-service behaviour (MAM-25), not re-checked; AC-6 built;
                     AC-7 built (tracked due date follows ProviderStatusChanged), not checked live; AC-8 built, checked
                     live in part (ADMIN GET 403, SUPERVISOR POST 403)
Files created/changed: revalidation-service application/CompleteCase, CaseCompletions; infrastructure/
                     ScreeningEventsListener, JdbcRevalidationStore (completion methods); api/RevalidationController (POST)
                   test drafts for Part D (not run): modern/docs/reports/stories/MAM-48/part-d-drafts/
Checks run:        compile + bootJar + pem-apps healthy + live checks above
Not verified:      automated tests (Part D); AC-4, AC-6, AC-7 live
Questions for you: none
```
