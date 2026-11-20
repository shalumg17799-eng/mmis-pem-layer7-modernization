```
Phase / item:      MAM-41 Assess a risk tier on request (risk assessment API)
Outcome:           BUILT (build-phase: tests in Part D), batch A6
What was done:     - risk-service POST /api/v1/risk-assessments (ANALYST, SUPERVISOR, SYSTEM): the tier of the table in
                     force for the taxonomy (first three characters; blank or shorter -> default tier) with ruleSetVersion
                     and assessedAt; no taxonomy, more than 10 characters or a malformed npi -> 400; no side effect
                     (nothing stored, no event) - application/AssessOnRequest
                   - AC status (live): AC-1 checked (H, H, M, M, M, L, version 1); AC-2 checked (3320000000 and 332ZZZZZZZ
                     -> H); AC-3 checked (blank and 33 -> L); AC-4 checked (none and 11 characters -> 400); AC-5 checked
                     (with npi ALPHA: tier H answered, ALPHA still L); AC-6 built, not live (needs version 2); AC-7 checked
                     (ANALYST, SUPERVISOR, SYSTEM 200; PROVIDER, ADMIN 403; no token 401)
Files created/changed: risk-service application/AssessOnRequest; api/RiskController
Checks run:        risk-service:test 28/28; bootJar; live checks above
Not verified:      automated tests (Part D); AC-6 live
Questions for you: none
```

## Lane 3 tests (2026-11-19)

```
Written by:        test-writer, Lane 3, AFTER the build (so the tests were green on first run, not red first). No production
                   code was changed (see "Production files" at the end for one incident that was reverted).
Test files (modern/services/risk-service/src/test):
  resources/features/MAM-41.feature                          18 Cucumber scenarios (outline examples expanded)
  java/gov/medicaid/pem/risk/acceptance/AssessmentSteps.java steps; the table set-up steps are those of MAM-38
  java/gov/medicaid/pem/risk/acceptance/RuleTableSteps.java  shared with MAM-38 (reset of the table, "version 2 in force")
  java/gov/medicaid/pem/risk/domain/RuleSetPrefixTest.java   28 unit tests (first three characters, blank, short, default)
  java/gov/medicaid/pem/risk/application/AssessOnRequestTest.java  34 unit tests (fake RuleSets, fixed Clock 2026-01-15)
  java/gov/medicaid/pem/risk/api/RiskAssessmentsApiTest.java 41 integration tests (MockMvc, real app, Testcontainers
                                                             Postgres and Kafka): roles, 400, no side effect, version 2,
                                                             contract assessRisk
  java/gov/medicaid/pem/risk/infrastructure/JdbcRiskStoreTest.java  3 of its 11 tests (the other 8 are MAM-38)
  java/gov/medicaid/pem/risk/RiskFixtures.java               shared fixtures
Tests per AC:      JUnit (unit + integration) / Cucumber scenarios
                   AC-1  18 / 6   the six prefixes: H, H, M, M, M, L under version 1, contract
                   AC-2  19 / 2   3320000000 and 332ZZZZZZZ -> H (and 332 at the start only)
                   AC-3  25 / 1   "", "   ", "33" -> 200 and L (and 3, 10 blanks)
                   AC-4  26 / 1   no taxonomy, null, no body, 11 characters, bad NPI, unreadable body; exactly 10 accepted
                   AC-5   4 / 1   with and without NPI: tier H; nothing stored, no outbox row, counter unchanged
                   AC-6   7 / 1   version 2 with 207 M: 207Q00000X -> M, ruleSetVersion 2
                   AC-7   7 / 6   ANALYST, SUPERVISOR, SYSTEM 200; PROVIDER, ADMIN, no role 403; no token 401
                   Total 106 JUnit (62 unit, 44 integration) + 18 Cucumber scenarios
Hashes:            modern/docs/reports/stories/MAM-41.tests.sha256 (8 files); test_protection.py check MAM-41 -> OK
Run:               ./gradlew --no-daemon :services:risk-service:check --continue   (2026-11-19, 21:19-21:24)
Result:            316 tests in the module (45 Cucumber scenarios, 271 JUnit), 310 passed, 6 FAILED, 0 skipped.
                   The 6 failures are the new tests "[MAM-38 AC-4] / [MAM-41 AC-4] the unreadable body ... is answered
                   with a 400 problem+json body" (defect D-1 below: kept red on purpose; they are not weakened).
                   Everything else passes, including all 33 new Cucumber scenarios.
                   PIT (domain + application): 41 mutations, 41 killed = 100 % (threshold 70 %), report
                   modern/services/risk-service/build/reports/pitest/index.html
                   JaCoCo line coverage: 100 % (185 of 185 lines, *Application excluded; 97.9 % with it), threshold 85 %;
                   jacocoTestCoverageVerification passed in the run before the 6 red tests were added (same production code),
                   report modern/services/risk-service/build/reports/jacoco/test/html/index.html
                   Spotless: FAILS on 3 production files (D-2 below); the test files are Spotless-clean.
                   ArchUnit (PlatformChecksTest): passes.
Defects found:     D-1  an unreadable JSON body gets a 400 with NO body; the contract says problem+json. Same cause as MAM-38
                        (libs/service-kit ProblemHandler.java has no HttpMessageNotReadableException handler);
                        AC-4 (assessRisk 400), RiskController.java:75-78 (@RequestBody). 3 red tests in RiskAssessmentsApiTest.
                   D-2  Spotless fails on production file AssessOnRequest.java (Javadoc lines 11-15 over 100 columns).
Observations:      O-1  the "instance" member of problem responses is not in the contract (see the MAM-38 report); the contract
                        checks of 400 and 403 remove it first.
                   O-4  AC-5 also says GET /api/v1/providers/1003000111 still shows riskTier L in provider-service. That is
                        another service: not testable here. In risk-service the tests prove the endpoint stores nothing,
                        writes no outbox row (so no RiskTierAssigned can be published) and does not move the counter. The
                        provider-service half belongs to the cross-service run (J1).
Not verified:      the provider-service half of AC-5; the 400 body of an unreadable request on the running stack
Production files:  same incident as in the MAM-38 report (spotlessApply run by mistake, reverted byte for byte).
```
