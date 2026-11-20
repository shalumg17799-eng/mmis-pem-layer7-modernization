# MAM-40 red report: provider history (audit)

The history (batch A3) existed before its tests; the tests below were written after the code and were green at once, except the two that disagreed with the contract in the first
run of the earlier test-writer (18:36-18:44). `MAM-40.tests.sha256` is new.

| First-run failure | AC | Reason | Fixed |
|---|---|---|---|
| `ProviderAuditApiTest` answerMatchesContract | AC-2 | the answer of `getProviderAudit` broke the contract: `$.items[0].after.enrollStatus is not in the contract` (AuditEntry declared `before` and `after` as `[object, null]` with no members) | yes, **Q-131 A** (user, 19:32): `provider-service.yaml` 1.1.0 declares `ProviderSnapshot` and five detail schemas; the test is unchanged and green |
| Cucumber "Every change writes one history entry, oldest first" | AC-1 | the same contract check: `$.items[0].after.npi is not in the contract` | yes, the same contract change; the scenario is unchanged and green |

Test hashes: `modern/docs/reports/stories/MAM-40.tests.sha256` (14 files), restore copies in `red-blobs.txt`.

## Tests per AC

| AC | Title | JUnit tests | Cucumber scenarios | Failing now |
|---|---|---|---|---|
| AC-1 | Every change writes one history entry, oldest first | 12 | 1 | 0 |
| AC-2 | An entry shows before, after and the request it belongs to | 10 | 1 | 0 |
| AC-3 | The actor is the user or the producing service | 3 | 1 | 0 |
| AC-4 | A notice sent is recorded once | 9 | 1 | 0 |
| AC-5 | A status change entry carries reason, effective date and sanction id | 7 | 1 | 0 |
| AC-6 | The history is paged | 6 | 1 | 0 |
| AC-7 | Only SUPERVISOR and ADMIN can read it; an unknown provider gives 404 | 7 | 1 | 0 |
| AC-8 | The history is append-only | 9 | 1 | 0 |

Tests by class: `AuditHistoryUseCasesTest` 15, `AuditTrailTest` 8, `NoticeEventsListenerTest` 8, `ProviderAuditApiTest` 25, `ProviderReadFailureTest` 1, `ReadProviderHistoryTest` 3, `RecordNoticeSentTest` 3.

- AC-1: one entry per change in the transaction of the change (REGISTERED, SCREENING_VERDICT, RISK_TIER_ASSIGNED, APPROVED, DENIED, TERMINATED, SUSPENDED, REINSTATED,
  REVALIDATION_PASSED) and nothing appended for a refused or not-applied change (`AuditHistoryUseCasesTest`); oldest first, equal times keep the insertion order; the contract per
  entry; atomic with the change and its event (`ProviderWriteAtomicityTest`, filed under MAM-27 and MAM-33).
- AC-2: at, actor, action, before, after and correlationId (a uuid) as stored; before null for the registration; a snapshot of exactly 11 record fields.
- AC-3: the actor is the preferred_username for approval and status change and the envelope source for events; defaults screening-service and risk-service.
- AC-4: one NOTICE_SENT entry per notice; a redelivery of the same event id adds nothing; another notice adds one; an unknown NPI adds nothing; the marker and the entry share one
  transaction; the provider row (version, updated_at) is untouched.
- AC-5: reason, effective date and sanction id in `after.statusChange`; end date unchanged.
- AC-6: 25 entries, page 1 of size 20 has 5; limits 1 to 200; a page beyond the end is empty with the total.
- AC-7: SUPERVISOR and ADMIN 200; ANALYST, PROVIDER, SYSTEM and a role-less user 403; no token 401; an unknown NPI 404; a database failure is a 503, not a 404 and not an empty history.
- AC-8: PUT, PATCH, DELETE and POST are 405 problem+json with `Allow: GET`, also for an ADMIN; no production code updates, deletes or truncates `audit_log`.

## Files

| File (under `modern/services/provider-service/src/test/`) | Blob id |
|---|---|
| `java/gov/medicaid/pem/provider/LogCapture.java` | `59bb423f6e4ba8c10ea96584fe7d45e2f7700640` |
| `java/gov/medicaid/pem/provider/ProviderRows.java` | `667a9c1ba95ba3f69bbda0f96b9a980fdf0f6a70` |
| `java/gov/medicaid/pem/provider/TestEvents.java` | `33229c8cf04a97462f1cc3e3ae8f70ae5cbe702c` |
| `java/gov/medicaid/pem/provider/acceptance/HttpAnswerSteps.java` | `38fcb570a3260aa0a5d6bdfdfe91fb26142ae0c4` |
| `java/gov/medicaid/pem/provider/acceptance/ProviderAuditSteps.java` | `654ecffc2693859df5ea59d1fd54a69640843483` |
| `java/gov/medicaid/pem/provider/acceptance/ProviderFixtureSteps.java` | `5d84f3e26010b903302854d8b5dd3117b4b61841` |
| `java/gov/medicaid/pem/provider/api/ProviderAuditApiTest.java` | `54b394583f223cf71c274aacd5b1e44bcc64be3e` |
| `java/gov/medicaid/pem/provider/api/ProviderReadFailureTest.java` | `832401e54f78bd1a34f749c554f2270f8d1d9fb3` |
| `java/gov/medicaid/pem/provider/application/AuditHistoryUseCasesTest.java` | `b4f17d4e7dd6e4a319635dfa9483f5e63ce355ab` |
| `java/gov/medicaid/pem/provider/application/AuditTrailTest.java` | `c2e9508cce3414e6faeab53284914be83454eaf0` |
| `java/gov/medicaid/pem/provider/application/ReadProviderHistoryTest.java` | `05f29a02aef2ba04726f92e5ed579540791fb96f` |
| `java/gov/medicaid/pem/provider/application/RecordNoticeSentTest.java` | `14e4f46d280a67a327db97c9c3ec7961b753c485` |
| `java/gov/medicaid/pem/provider/infrastructure/NoticeEventsListenerTest.java` | `d41daf3e8cd5c26379dc12874b2b51150893cf15` |
| `resources/features/MAM-40.feature` | `dccfac715674eb3c1afb241feeebd73d8879deb9` |

Runs (all on 2026-11-20, from `modern/`, one Gradle build at a time through the shared lock, `--no-daemon`):
- `./gradlew -I <init script: Spotless on src/test of provider-service only> :services:provider-service:spotlessApply` (formats the test sources only).
- `./gradlew :services:provider-service:check --continue` (tests, Spotless on everything, ArchUnit, JaCoCo, PIT). Last run 21:00-21:09: **417 tests, 9 failures, 0 skipped**;
  the 9 failures are the Q-124 tests (red by design until `PrvrecrdFile` is changed, see MAM-43); PIT **130 of 130 mutations killed (100 %)** on `domain` and `application`;
  Spotless and ArchUnit passed.
- `./gradlew :services:provider-service:jacocoTestReport -x test` (the report of the same run; with a failing test `check` does not reach `jacocoTestCoverageVerification`):
  **line coverage 98.34 %** (768 of 781 lines; 98.84 % without `*Application`, as the gate counts), branch 88.31 %. Gate 85 %.
