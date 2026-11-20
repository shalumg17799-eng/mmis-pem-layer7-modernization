```
Phase / item:      MAM-40 Provider history: every change and every notice sent is audited and readable
Outcome:           BUILT (build-phase: tests in Part D), batch A3
What was done:     - Spec check: Jira MAM-40 description = modern/specs/MAM-40.md (same 8 ACs)
                   - Built in provider-service on the existing audit_log table (no schema change): AuditTrail (append
                     only) and AuditHistory ports, JdbcAuditTrail (INSERT only; before/after as JSONB; read ORDER BY at,
                     id with LIMIT/OFFSET), ReadProviderHistory (404 for an unknown NPI), GET
                     /api/v1/providers/{npi}/audit (SUPERVISOR, ADMIN; page/size as the contract)
                   - Entries written in the transaction of each change, with the correlation id of the request or
                     event: REGISTERED (before null), SCREENING_VERDICT, RISK_TIER_ASSIGNED, APPROVED / DENIED,
                     TERMINATED / SUSPENDED / REINSTATED (after carries statusChange: action, reason, effectiveDate,
                     sanctionId), REVALIDATION_PASSED (MAM-30), NOTICE_SENT (after carries notice: noticeId, template;
                     the provider row is not touched). before/after hold npi, medicaidId, providerType, taxonomy,
                     riskTier, enrollStatus, screenFlag, effectiveDate, endDate, revalidationDueDate, version, plus the
                     named detail part
                   - Actor: the user's preferred_username for approval and status change; the envelope source
                     (enrollment-service, screening-service, risk-service, notification-service) for events
                   - New NoticeEventsListener on pem.notification.events (processed_event: a redelivery is ignored)
                   - api/ProviderProblems: PUT/PATCH/DELETE (any undefined method) -> 405 problem+json with Allow
                   - The use cases keep their constructors (recorded unit tests construct them); the history is wired
                     through an @Autowired setter that defaults to no history
                   - AC status: AC-1 built, checked live (GR 1003000222: REGISTERED, SCREENING_VERDICT, RISK_TIER_ASSIGNED,
                     APPROVED, SUSPENDED oldest first; plus the NOTICE_SENT of the approval notice, since live a notice is
                     sent); AC-2 built, checked live (approval with X-Correlation-Id 5a1e7b20-...-000000000456: actor
                     analyst1, before P, after A with revalidationDueDate 2031-11-19, that correlationId; registration
                     before null); AC-3 built, checked live (analyst1, service-account-pem-services, enrollment-service,
                     screening-service, risk-service); AC-4 built, checked live for one delivery (one NOTICE_SENT with
                     noticeId and template APPROVED); redelivery not checked live; AC-5 built, checked live (SYSTEM
                     SUSPEND: before A, after S, reason 'LEIE exclusion', effectiveDate 2026-03-31, sanctionId; endDate
                     unchanged); AC-6 built, checked live (page 1 size 4 of 6 -> 2 items, total 6); AC-7 built, checked
                     live (SUPERVISOR, ADMIN 200; ANALYST, PROVIDER, SYSTEM 403; no token 401; 1003000999 404); AC-8
                     built, checked live (PUT, PATCH, DELETE -> 405 application/problem+json, Allow: GET)
                   - Defect found live and fixed before the final deploy: the risk-tier detail was stored under the
                     key riskTier and hid the provider's riskTier in after (now under "risk"); after now re-reads the
                     record so its version is the saved one
Files created/changed: modern/services/provider-service/src/main/java/gov/medicaid/pem/provider/
                     application/AuditEntry, AuditTrail, AuditHistory, ReadProviderHistory, RecordNoticeSent (new);
                     RegisterProvider, RecordVerdicts, DecideProvider, ChangeProviderStatus, CompleteRevalidation
                     (history entries; actor overloads); infrastructure/JdbcAuditTrail, NoticeEventsListener (new);
                     EnrollmentEventsListener, VerdictEventsListener (actor = envelope source); api/ProviderController
                     (getProviderAudit), api/ProviderProblems (405)
Checks run:        ./gradlew --no-daemon :services:provider-service:test -> 142/142 existing tests pass (run twice, before
                   and after the fix); bootJar; pem-apps restarted and healthy; live checks above
Not verified:      automated tests (Part D); AC-4 redelivery of the same event id live
Questions for you: none
```

## Lane 3 tests (2026-11-20)

Outcome line unchanged. Tests for every AC of MAM-40.

- Test files (14, recorded in `MAM-40.tests.sha256`): `ProviderAuditApiTest`, `AuditHistoryUseCasesTest`, `AuditTrailTest`, `ReadProviderHistoryTest`, `RecordNoticeSentTest`, `NoticeEventsListenerTest`, `ProviderReadFailureTest` (AC-7, database failure),
  `features/MAM-40.feature`, `ProviderAuditSteps`, `TestEvents`, `HttpAnswerSteps`, `ProviderFixtureSteps`, `LogCapture`, `ProviderRows`.
- Tests per AC (JUnit + Cucumber scenarios): AC-1 12+1, AC-2 10+1, AC-3 3+1, AC-4 9+1, AC-5 7+1, AC-6 6+1, AC-7 7+1, AC-8 9+1.
- Written after the code. The two tests that failed in the first run (AC-1 scenario, AC-2 contract check) agreed with the story and the code and disagreed with the contract: **Q-131 A**, contract 1.1.0; both are unchanged and green.
- No open defect of this story.
Runs (all on 2026-11-20, from `modern/`, one Gradle build at a time through the shared lock, `--no-daemon`):
- `./gradlew -I <init script: Spotless on src/test of provider-service only> :services:provider-service:spotlessApply` (formats the test sources only).
- `./gradlew :services:provider-service:check --continue` (tests, Spotless on everything, ArchUnit, JaCoCo, PIT). Last run 21:00-21:09: **417 tests, 9 failures, 0 skipped**;
  the 9 failures are the Q-124 tests (red by design until `PrvrecrdFile` is changed, see MAM-43); PIT **130 of 130 mutations killed (100 %)** on `domain` and `application`;
  Spotless and ArchUnit passed.
- `./gradlew :services:provider-service:jacocoTestReport -x test` (the report of the same run; with a failing test `check` does not reach `jacocoTestCoverageVerification`):
  **line coverage 98.34 %** (768 of 781 lines; 98.84 % without `*Application`, as the gate counts), branch 88.31 %. Gate 85 %.
- Not verified: reading a history of very many entries (not an AC).
