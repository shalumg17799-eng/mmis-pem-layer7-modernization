```
Phase / item:      MAM-78 Warn about providers whose enrolment year is later than the business year
Outcome:           BUILT (build-phase: tests in Part D), done with batch A4 (same service, tmsis-service)
What was done:     - Spec check: modern/specs/MAM-78.md (6 ACs); Jira not re-read in this batch
                   - domain/FutureEnrolment.flagged: the 4-digit enrolment year is strictly greater than the year of
                     the business date (R-PRVTMS01-cbl-00005/-00035 with D-004; Q-062 default B), so 1998 is not flagged
                     and 2101 is
                   - application/ExtractItem: only ACTIVE providers become items (so only they are checked); a flagged
                     provider is logged at WARN with the legacy DISPLAY text 'PRVTMS01: FUTURE ENROLLMENT? NPI <npi>' and
                     still reported unchanged (R-PRVTMS01-cbl-00006)
                   - The extract step's writer adds the flagged count to extract_run.future_enrolment_count (Flyway V4);
                     BatchRun.counts.futureEnrolments (Q-060 default A, no contract change: counts is a free map)
                   - ProviderRow/ProviderServiceClient now also read effectiveDate from provider-service
                   - AC status: AC-1 built, checked live (businessDate 2024-12-31: exactly one WARN line per flagged NPI,
                     1003000111 effective 2026-11-19 and 1005992312 effective 2025-01-01, counts.futureEnrolments 2);
                     AC-2 built, checked live (both still in the file; rows 9 = every ACTIVE provider); AC-3 built,
                     checked live in part (DME effective 2024-08-01 is not flagged for business year 2024); AC-4 and AC-5
                     built (rule above), not checked live (no seeded provider with a 1998 or 2101 date); AC-6 built (a
                     non-ACTIVE provider gives no item, so no line and no count), seen live (the PENDING providers with
                     2026 dates were neither reported nor flagged)
Files created/changed: modern/services/tmsis-service/src/main/java/gov/medicaid/pem/tmsis/ domain/FutureEnrolment (new),
                     application/ExtractItem (new), ProviderRow (effectiveDate), ExtractRun (futureEnrolments),
                     ExtractRuns (addFutureEnrolments), StartTmsisExtract; infrastructure/ProviderServiceClient,
                     JdbcExtractRuns, TmsisExtractJobConfig; api/TmsisExtractController (counts.futureEnrolments);
                     resources/db/migration/tmsis/V4__future_enrolment_count.sql
Checks run:        compile; no existing tests in tmsis-service; bootJar; pem-apps healthy; live checks above
Not verified:      automated tests (Part D); AC-4 and AC-5 live
Questions for you: none
```
