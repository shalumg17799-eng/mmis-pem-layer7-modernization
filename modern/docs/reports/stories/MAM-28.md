```
Phase / item:      MAM-28 Rescreen a provider on request or when revalidation is due
Outcome:           BUILT (build-phase: tests in Part D), batch A1
What was done:     - Spec check: Jira MAM-28 description = modern/specs/MAM-28.md (same 6 ACs)
                   - Built in screening-service: POST /api/v1/screenings {npi} (ANALYST, SUPERVISOR, SYSTEM): malformed
                     NPI 400; NPI unknown to provider-service 404 (nothing created); else 202 with a MANUAL screening in
                     status RUNNING, run in the background with the same id (ScreenProvider.screen(id, npi, reason));
                     RevalidationEventsListener: RevalidationDue starts one REVALIDATION screening (processed_event makes
                     a redelivery a no-op); every screening reads the provider's current data from provider-service
                   - AC status: AC-1 built, checked live (202 RUNNING for ALPHA 1005940865, then COMPLETED PASS with the
                     source results; earlier screenings are only ever added to, D-018); AC-2 built, checked live (123 ->
                     400, 1003000999 -> 404); AC-3 built (seen live: the RevalidationDue for 1003000111 reached
                     screening-service, but that NPI was no longer in provider-service, so the screening failed and went
                     to the retry/DLT path); AC-4 built (idempotent consumer), not checked live; AC-5 built (provider data
                     read per screening), not checked live; AC-6 built, checked live (PROVIDER and ADMIN 403, no token 401,
                     ANALYST 202)
Files created/changed: modern/services/screening-service/src/main/java/gov/medicaid/pem/screening/
                     application/RequestScreening (new), application/ScreenProvider (screen with a given id),
                     infrastructure/RevalidationEventsListener, ScreeningExecutorConfig (new), api/ScreeningController (POST)
                   test drafts for Part D (not run): modern/docs/reports/stories/MAM-28/part-d-drafts/
Checks run:        ./gradlew --no-daemon :services:screening-service:test -> 49/49 existing tests pass; bootJar;
                   pem-apps healthy; live checks above
Not verified:      automated tests (Part D); the RUNNING row is not stored: GET by id answers 404 until the screening
                   completes (a few seconds); AC-4/AC-5 live
Questions for you: none
```
