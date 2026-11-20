```
Phase / item:      MAM-38 View and change the taxonomy-prefix risk table (admin)
Outcome:           BUILT (build-phase: tests in Part D), batch A6
What was done:     - risk-service GET /api/v1/risk-rules (ANALYST, SUPERVISOR, ADMIN): the table in force (version,
                     effectiveFrom, defaultTier, rules in saved order); PUT (ADMIN): malformed (prefix not ^[0-9A-Z]{3}$,
                     tier not L/M/H, no defaultTier or version) -> 400; version not the one in force or a prefix listed
                     twice -> 422; else saved as version + 1, in force from the service date; nobody is re-tiered and no
                     RiskTierAssigned is published (AC-5); a concurrent save loses with 422 (domain/RuleTable, application/
                     ManageRiskRules, RuleTables, JdbcRiskStore). Seed: risk_db/01-reset.sql removes rule-set versions
                     above 1, so the seed state is version 1 again
                   - AC status (live, nothing changed): AC-1 checked (version 1, default L, 332 H, 251 H, 341 M, 291 M,
                     225 M); AC-2 built, not live (a successful PUT would change the shared table without a seed); AC-3
                     checked (stale 422, duplicate 422); AC-4 checked (2-char, lower case, tier X, no default -> 400);
                     AC-5 built, not live; AC-6 checked (PUT SUPERVISOR/ANALYST/PROVIDER 403, no token 401; GET PROVIDER
                     403, no token 401)
Files created/changed: risk-service domain/RuleTable; application/RuleTables, ManageRiskRules; infrastructure/
                     JdbcRiskStore; api/RiskController; testdata/seed/risk_db/01-reset.sql
Checks run:        risk-service:test 28/28; bootJar; live checks above
Not verified:      automated tests (Part D); AC-2 and AC-5 live
Questions for you: none
```

## Lane 3 tests (2026-11-19)

```
Written by:        test-writer, Lane 3, AFTER the build (so the tests were green on first run, not red first). No production
                   code was changed (see "Production files" at the end for one incident that was reverted).
Test files (modern/services/risk-service/src/test):
  resources/features/MAM-38.feature                          15 Cucumber scenarios (outline examples expanded)
  java/gov/medicaid/pem/risk/acceptance/RuleTableSteps.java  steps (also used by MAM-41); ScreeningCompleted and
                                                             RiskTierAssigned steps are reused from RiskSteps (MAM-35)
  java/gov/medicaid/pem/risk/domain/RuleTableTest.java       56 unit tests
  java/gov/medicaid/pem/risk/application/ManageRiskRulesTest.java  31 unit tests (fake RuleTables, fixed Clock 2026-01-15)
  java/gov/medicaid/pem/risk/api/RiskRulesApiTest.java       54 integration tests (MockMvc, real app, Testcontainers
                                                             Postgres and Kafka): roles, 400/422, versioning, concurrent
                                                             save, contract getRiskRules / replaceRiskRules
  java/gov/medicaid/pem/risk/infrastructure/JdbcRiskStoreTest.java  8 of its 11 tests (the other 3 are MAM-41)
  java/gov/medicaid/pem/risk/RiskFixtures.java               shared fixtures; resets risk_db to the seeded table (version 1)
Tests per AC:      JUnit (unit + integration) / Cucumber scenarios
                   AC-1  10 / 3   staff read the table (3 roles), version 1, default L, the 5 legacy rules, contract
                   AC-2  15 / 1   ADMIN saves version 2 (+207 M), GET shows it, next PASS for 207Q00000X -> M, version 2
                   AC-3  21 / 1   stale version 422, duplicate prefix 422, GET unchanged, two admins at once (one 200, one 422)
                   AC-4  86 / 4   2-char, lower-case, 4-char, symbol prefix; tier X; no defaultTier; unreadable body
                   AC-5   3 / 1   no event, no assessment; stored assessment keeps L@1; a later PASS is assessed M@2
                   AC-6  14 / 5   PUT and GET per role (ADMIN, SUPERVISOR, ANALYST, PROVIDER, SYSTEM, no role, no token)
                   Total 149 JUnit (87 unit, 62 integration) + 15 Cucumber scenarios
Hashes:            modern/docs/reports/stories/MAM-38.tests.sha256 (7 files); test_protection.py check MAM-38 -> OK
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
Defects found:     D-1  an unreadable JSON body (not JSON, wrong type, a JSON array) gets a 400 with NO body; the contract says
                        400 is application/problem+json. libs/service-kit ProblemHandler.java has no handler for
                        HttpMessageNotReadableException (directory-service has its own, DirectoryProblems.java:21).
                        AC-4 (replaceRiskRules 400), RiskController.java:59-61 (@RequestBody); ProblemHandler.java:21-58 lists no such
                        handler. 3 red tests in RiskRulesApiTest.
                   D-2  Spotless fails on production files AssessOnRequest.java, ManageRiskRules.java, RuleTable.java
                        (google-java-format, mechanical: AssessOnRequest.java:11-15 Javadoc, ManageRiskRules.java:66 long line,
                        RuleTable.java:12-18 Javadoc and record header).
                        Fix: java-developer runs spotlessApply for them.
Observations:      O-1  Every Problem response carries the RFC 7807 member "instance" (Spring adds the request path); the
                        Problem schema of all 9 contracts does not list it, and OpenApiContract refuses unlisted members. The
                        contract checks of the 400/422/403 responses therefore remove "instance" first
                        (RiskFixtures.withoutInstance). Fix needs the user's approval (contract): add an optional "instance".
                   O-2  AC-2 depends on the dates agreeing: ManageRiskRules saves effective_from = LocalDate.now(Clock UTC),
                        JdbcRiskStore reads "effective_from <= current_date" of the database session. With a JVM/DB time zone
                        behind UTC, a table saved late in the local evening would not be in force until local midnight.
                        Not visible in the UTC test JVM and the compose stack; not tested.
                   O-3  No AC says whether an empty rules list is a valid table. The code accepts it (every taxonomy then gets
                        the default tier) and 3 tests document that (RuleTableTest.emptyRulesAreWellFormed,
                        ManageRiskRulesTest.saveWithoutRules, RiskRulesApiTest.noRulesButDefaultTier). Confirm with the user.
Not verified:      the 400 body of an unreadable request on the running stack (MockMvc only); behaviour in a non-UTC time zone
Production files:  I ran spotlessApply twice by mistake (module-wide, once through the IDE hook); it reformatted the 3 files of D-2.
                   Both times I restored them byte for byte (sha256 compared with the state I found), so the files are as built.
```
