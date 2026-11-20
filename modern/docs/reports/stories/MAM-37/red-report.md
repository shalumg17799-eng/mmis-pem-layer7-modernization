# MAM-37 red report: provider lookup and search (PINQ replacement)

The lookup and the search (batch A3) existed before their tests: the tests below were written after the code and were green at once, except the one defect found by the first
run of the earlier test-writer (18:36-18:44, 375 tests, 10 failures in the whole module; 1 of them belongs here). `MAM-37.tests.sha256` is new (the earlier lane was stopped
before it recorded).

| First-run failure | AC | Reason | Fixed |
|---|---|---|---|
| `ProviderReadFailureTest` fullExceptionIsLogged | AC-8 | `ProviderProblems` logged the exception class name only; the exception (type, message, cause, stack) was not attached to the log call (D-024): `Expecting actual not to be null` | yes, by the java-developer in `ProviderProblems` (the exception is passed to the log call); green in the runs of 20:06 to 21:09 |

Test hashes: `modern/docs/reports/stories/MAM-37.tests.sha256` (15 files: the 10 above plus 5 for AC-9, 2026-11-20 22:10), restore copies in `red-blobs.txt`.

## Tests per AC

| AC | Title | JUnit tests | Cucumber scenarios | Failing now |
|---|---|---|---|---|
| AC-1 | Look up a provider by NPI and see every field | 3 | 1 | 0 |
| AC-2 | An unknown NPI gives 404 | 1 | 1 | 0 |
| AC-3 | The NPI typed in the request is the key, with no carry-over | 2 | 1 | 0 |
| AC-4 | The status code is returned as stored, including S | 6 | 1 | 0 |
| AC-5 | Search by Medicaid ID or NPI | 13 | 1 | 0 |
| AC-6 | Search by name, status and screen flag with paging (work queues) | 15 | 2 | 0 |
| AC-7 | Role checks: a provider sees only their own record | 9 | 1 | 0 |
| AC-8 | A read failure is not reported as 'not found' | 6 | 1 | 0 |
| AC-9 | Walk all providers of a filter by key (afterNpi) in NPI order, none missed or repeated (added by Q-132, spec version 2) | 192 invocations of 37 test methods, see "AC-9" below | 1 | 139 of 193 (red by design) |

Tests by class: `ProviderOutageTest` 1, `ProviderReadFailureTest` 5, `ProviderSearchApiTest` 49.

- AC-1: every field of an individual (feature), of an organisation (legal name, DBA name, owner NPI, no individual part), the end date, the contract (`getProvider`) per record.
- AC-2: 404 problem+json that names only the NPI asked for and holds no data of another provider.
- AC-3: no session and no cookie; the sequence A, unknown, B, unknown, A returns A, 404, B, 404, A.
- AC-4: each code P, A, T, D and S is returned as stored in a lookup and in a search; S is never D (`statusIsReturnedAsStored`, `suspendedAndDeniedAreDifferent`, the outline).
- AC-5: exact NPI and Medicaid ID (a prefix or another case finds nothing, a short CHAR value is found), an unknown NPI is 200 with an empty page, malformed values are 400.
- AC-6: status, screen flag and name filters (AND); percent, underscore and backslash are literal characters; paging cut from the NPI order (25 providers: page 1 of size 20 has
  5); limits 1 to 200; unknown codes are 400.
- AC-7: ANALYST, SUPERVISOR, ADMIN and SYSTEM may search; a PROVIDER sees only their own NPI (403 for another, existing or not); no npi claim 403; no PEM role 403; no token 401 problem+json.
- AC-8: the table taken away for one request (503 problem of at most 200 characters, no SQL text, the whole exception logged with the correlation id, the same X-Correlation-Id back,
  a forged id replaced) and the **real outage** (`ProviderOutageTest`: the test's own Testcontainers Postgres is paused inside the test; the read is a 503 within the limit, never
  a 404 and never a hang, and works again when the database is back).

## Files

| File (under `modern/services/provider-service/src/test/`) | Blob id |
|---|---|
| `java/gov/medicaid/pem/provider/LogCapture.java` | `59bb423f6e4ba8c10ea96584fe7d45e2f7700640` |
| `java/gov/medicaid/pem/provider/ProviderOutageTest.java` | `1af7fd03c5f493bea173187a1fb77944f21ee2d3` |
| `java/gov/medicaid/pem/provider/ProviderRows.java` | `667a9c1ba95ba3f69bbda0f96b9a980fdf0f6a70` |
| `java/gov/medicaid/pem/provider/acceptance/HttpAnswerSteps.java` | `38fcb570a3260aa0a5d6bdfdfe91fb26142ae0c4` |
| `java/gov/medicaid/pem/provider/acceptance/ProviderFixtureSteps.java` | `5d84f3e26010b903302854d8b5dd3117b4b61841` |
| `java/gov/medicaid/pem/provider/acceptance/ProviderSearchSteps.java` | `6b3e50067a6db7bdad6d9704f5bf1644122786ad` |
| `java/gov/medicaid/pem/provider/api/ProviderReadFailureTest.java` | `832401e54f78bd1a34f749c554f2270f8d1d9fb3` |
| `java/gov/medicaid/pem/provider/api/ProviderSearchApiTest.java` | `566bdc8e44a7d827d9da8f9c3b85152462d9463d` |
| `resources/features/MAM-37.feature` | `a72b9e7406d5d0ac3188b8488d03e08559f408ed` |
| `resources/junit-platform.properties` | `9d87aa62f037594f1504a3edb60e3a0419190af2` |

`src/test/resources/junit-platform.properties` (new, recorded here and in MAM-36): every test method and lifecycle method gets a default limit of 6 minutes. That limit alone cannot
interrupt a thread blocked in a socket read (see the MAM-36 report for the hang it was added for), so every test that waits on a frozen database carries its own bounded wait as well.

Runs (all on 2026-11-20, from `modern/`, one Gradle build at a time through the shared lock, `--no-daemon`):
- `./gradlew -I <init script: Spotless on src/test of provider-service only> :services:provider-service:spotlessApply` (formats the test sources only).
- `./gradlew :services:provider-service:check --continue` (tests, Spotless on everything, ArchUnit, JaCoCo, PIT). Last run 21:00-21:09: **417 tests, 9 failures, 0 skipped**;
  the 9 failures are the Q-124 tests (red by design until `PrvrecrdFile` is changed, see MAM-43); PIT **130 of 130 mutations killed (100 %)** on `domain` and `application`;
  Spotless and ArchUnit passed.
- `./gradlew :services:provider-service:jacocoTestReport -x test` (the report of the same run; with a failing test `check` does not reach `jacocoTestCoverageVerification`):
  **line coverage 98.34 %** (768 of 781 lines; 98.84 % without `*Application`, as the gate counts), branch 88.31 %. Gate 85 %.

## AC-9: keyset paging of the provider search (Q-132), red evidence of 2026-11-20 22:10

**Why.** Q-132 (user, 2026-11-20 20:22): provider-service gets the optional query parameter `afterNpi` on `searchProviders` (contract 1.2.0, additive), so that the T-MSIS extract
can walk the ACTIVE providers by key and a concurrent approval or termination can no longer make it miss or repeat a provider. Spec `MAM-37.md` is at version 2 (AC-9 added by the
backlog-planner, Jira MAM-37 updated). The contract text (architect) is `modern/contracts/openapi/provider-service.yaml` 1.2.0: RULE 1 order, RULE 2 keyset (strictly greater, `size`
works, every filter combines), RULE 3 `page` absent or 0 (any other page is a 400), RULE 4 `total` counts ALL matches (never only those after the key), RULE 5 the walk, RULE 6 nothing
changes without the key, and strict input (not exactly 10 ASCII digits, empty, or given more than once is a 400 problem). **No production code was written or changed.**

**Changes to recorded tests (Q-097 C).** One: `resources/features/MAM-37.feature` got the scenario `@AC-9` appended after the AC-8 scenario (LF, no existing line touched; the first
10232 bytes are identical). Its hash and restore copy are new; the nine other hashes of the story are unchanged (diffed against the old hash file). Nothing was weakened, removed or skipped.

**New files** (all under `modern/services/provider-service/src/test/`, Java files CRLF like the other Java tests, formatted by the Spotless run on test sources only):

| File | Blob id |
|---|---|
| `java/gov/medicaid/pem/provider/api/ProviderKeysetSearchApiTest.java` | `6d29182669e9b3188e77338b4eabe238a1451b96` |
| `java/gov/medicaid/pem/provider/api/ProviderKeysetWalkTest.java` | `35036c861b9f8ecfa62bed138f2b8758b9834ba9` |
| `java/gov/medicaid/pem/provider/api/ProviderKeysetContractTest.java` | `f56d08131d0254880b401589b2a948f44beec618` |
| `java/gov/medicaid/pem/provider/api/KeysetFixtures.java` (data: the eight providers, the 20+2+4 of the concurrent cases, the 450+1 of AC-9) | `8a61804c3fe0bc95fe632cac27ab66b582df24fb` |
| `java/gov/medicaid/pem/provider/acceptance/ProviderKeysetWalkSteps.java` (steps of the scenario) | `06f3846a957b1f99a695649bb21e41a97cb22bb9` |
| `resources/features/MAM-37.feature` (changed: scenario AC-9 appended; was `a72b9e74...`) | `250f41d9ac3d4800d67fe4da0300b84b8c840c3c` |

**Levels.** Every test runs the real application (controller, use case, JPA, Flyway schema) on a Testcontainers Postgres, with Kafka as in every other integration test of the module
(`IntegrationTest`, `MockMvc`, signed-in JWTs of the realm roles). There is no pure domain class for the new rule (it is a predicate of the search query), so the rule is tested where
it lives: JUnit API tests 192 invocations of 37 test methods (`ProviderKeysetSearchApiTest` 122 of 22, `ProviderKeysetWalkTest` 46 of 10, `ProviderKeysetContractTest` 24 of 5) and 1
Cucumber scenario (`@MAM-37 @AC-9`). A unit test of a not yet existing class was not written on purpose: it would fix the developer's design and break the compilation of the module.

**AC-9 to test mapping** (every test name starts with `[MAM-37 AC-9]`):

| What AC-9 and the contract require | Tests |
|---|---|
| Only NPIs greater than the key, ascending, key itself excluded, key need not be on file | `keyListsOnlyGreaterNpisInOrder` (12 keys, from 0000000000 to 9999999999), `keysAboveTheIntRange` (10 keys around 2147483647 and 4294967295: the key is a position, not an int) |
| `size` respected (1 to 200, default 20), limits hold with a key | `sizeCutsTheAnswer` (6), `defaultSizeWithAKey`, `sizeLimitsHoldWithAKey` (0, 201, -1, abc) |
| Every filter (npi, medicaidId, name, status, screenFlag) combines by AND; the key need not match the filters | `filtersCombineWithTheKey` (20 combinations) |
| Last answer short, then empty with 200, `items` array, page 0, size and total of all matches | `lastAnswerIsShort`, `emptyAnswerAtTheEnd` |
| `total` = ALL matches, never only those after the key (the implementation trap named by the architect) | `totalCountsEveryMatch` (6 filter sets x 5 key variants) |
| `page` absent or 0; any other page is a 400 | `pageZeroWithAKey`, `pageOtherThanZeroIsRefused` (1, 2, 5, 100) |
| Strict input (lesson b): empty, blank, 9 or 11 digits, letters, padding, newline, sign, decimal, exponent, Arabic-Indic and full-width digits, "null": 400 problem+json, nothing coerced; also with other filters | `malformedKeyIsRefused` (19), `malformedKeyIsRefusedWithFilters` (19) |
| `afterNpi` given more than once: 400 | `repeatedKeyIsRefused` (3), `keyGivenThreeTimesIsRefused` |
| The answer keeps to the `ProviderPage` contract | `keysetAnswerMatchesContract` (5: full, short, empty and large page) |
| The contract declares the parameter, is additive and a minor bump; the service follows the contract's own pattern | `ProviderKeysetContractTest` (24: declaration, old parameters kept, 1.x version, 20 candidate values taken through the pattern read from the YAML, an admitted key with no provider) |
| RULE 6: nothing changes without the key (regression) | `withoutTheKeyNothingChanges` (offset paging, order, page and size echoed, totals) |
| Roles unchanged, no side door (the roles of `searchProviders` stay covered by `ProviderSearchApiTest` AC-7; only what the new parameter could open is added) | `staffMayPageByKey` (ANALYST, SUPERVISOR, ADMIN, SYSTEM), `providerCannotPageByKey`, `roleIsCheckedBeforeTheKey`, `noTokenCannotPageByKey`, `noRoleCannotPageByKey` |
| Complete walks: every provider once, in NPI order, any page size, full pages then a short or empty one | `walkReturnsEveryProviderOnce` (sizes 1, 2, 3, 4, 7, 8, 9, 200), `walkOverAFilter` (7 filters), `walkOverNothing`, `totalIsStableWhileNothingChanges`, `walkThroughFourHundredAndFifty` (the AC-9 data, sizes 200, 150, 90, compared with `SELECT npi ... ORDER BY npi`) |
| **The concurrent-change cases** (terminate and approve through the real API between two answers): every provider that stayed ACTIVE exactly once, none twice, ascending, the walk ends, the total follows the data | `keyWalkSurvivesChanges` (8 cases), `totalShowsTheChange` (8 cases). Cases: a returned provider is terminated; the provider at the key is terminated; a provider before the key is approved; a returned one terminated and one before the key approved; a returned one terminated and one after the key approved (the case of AC-9); one after the key approved; one not yet returned terminated; two terminated and two approved |
| The same changes make the OLD offset walk miss or repeat (documented behaviour of the old paging, not a requirement) | `offsetWalkMissesOrRepeats` (the same 8 cases: it misses 1003002060 in four of them, repeats 1003002050 in one, and is unaffected in the other three) |
| Many changes at once, the same every time | `seededRandomChanges` (60 ACTIVE and 30 PENDING providers, size 7, 6 random changes after each answer, seed 20261120: 8 answers, 21 terminated, 21 approved; checked with a model of the contract before the run) |
| A real second thread | `walkWhileAnotherThreadChangesProviders` (a thread flips 30 providers between ACTIVE and TERMINATED while the walk reads 150; the 120 that never change must each be returned once; the walk waits for at least 5 flips between answers, all waits bounded to 20 s) |
| The scenario of AC-9 as written (450 + 1 providers, page 1 without the key, walk by key with 200, 1003001100 terminated and 1003001500 approved after the first page, `afterNpi=ABC` and `12345`) | Cucumber `@MAM-37 @AC-9` (steps in `ProviderKeysetWalkSteps` and the existing status-change, decision and answer steps) |

Every walk has a limit on its answers, and each answer must start after the key, so a service that ignores the key fails the test with a message instead of looping; the class
`ProviderKeysetWalkTest` also carries `@Timeout` of 4 minutes (the module default is 6).

**Red run** (one Gradle build, through the shared lock, no wait for memory or lock; two `-I` init scripts kept in the scratchpad: Spotless on `src/test` of provider-service only, and the
Cucumber tag filter `@MAM-37 and @AC-9` for provider-service only, so that the red run does not repeat the other 100 scenarios):
`./gradlew --no-daemon :services:provider-service:spotlessApply :services:provider-service:test --tests 'gov.medicaid.pem.provider.api.ProviderKeyset*' --tests 'gov.medicaid.pem.provider.acceptance.CucumberSuiteTest' -x jacocoTestReport`
(22:04 to 22:09, 4 min 59 s; an earlier run of 21:58 to 22:01 with 4 fewer tests gave the same picture). Result: **193 tests (192 JUnit + 1 scenario), 139 failed, 54 passed**, none skipped by a
test (the 100 "skipped" Cucumber entries in the XML are the other scenarios, filtered out by the tag).

| Failures | Count | Reason (all: `afterNpi` does not exist yet, the service ignores it) |
|---|---|---|
| Cucumber `@AC-9` | 1 | `answer 2 must start after the key 1003001200 ... Expecting "1003001001" to be greater than "1003001200"`: the second page of the walk is the first page again |
| `ProviderKeysetWalkTest` | 31 | every walk fails with `answer N must start after the key ...` (the answers repeat the first page); this covers the complete walks with 2 or more answers, the 450 walks, all 8 keyset cases, all 8 total cases, the seeded random walk and the real-thread walk |
| `ProviderKeysetSearchApiTest`, wrong items | 45 | `Expecting actual [all providers] to contain exactly [the providers after the key]`; plus `emptyAnswerAtTheEnd`: `expected: 0 but was: 3` (the key is ignored) |
| `ProviderKeysetSearchApiTest`, 200 for a 400 | 46 | `expected: 400 but was: 200` (malformed key 19 + 19, repeated key 4, page other than 0 x 4) |
| `ProviderKeysetContractTest` | 16 | `expected: 400 but was: 200` for each of the 16 values the contract pattern refuses |

No test is red for a mistake of its own. The expected lists of every CSV row of the search and walk tests were checked against a model of the contract (0 mismatches in 61 rows); the 8
offset cases (which run against today's code) pass, which confirms the computed misses and repeats and that the fixtures (terminate and approve through the API) work; the keyset
assertions were checked with a model of a contract-conforming service for all 8 cases (the expectations hold).

**Passing now, by design (54).** They guard what must not change or what already holds. `ProviderKeysetContractTest` 8: the contract already declares `afterNpi` since 1.2.0 (3 tests), and
the 4 admitted values and the admitted key on an empty table answer 200 today because the key is ignored. `ProviderKeysetSearchApiTest` 31: `withoutTheKeyNothingChanges` (RULE 6), the 4
role and token checks (the role is checked first), `sizeLimitsHoldWithAKey` 4 (existing validation), `keysetAnswerMatchesContract` 5, `defaultSizeWithAKey`, `totalCountsEveryMatch` 6
(the current code already counts every match: these guard the implementation trap, so that the key is not added to the counting query), and 10 rows whose answer is the same with the key
ignored (a key before the first NPI, or a filter whose matches all lie after the key); they still discriminate against an implementation that drops a filter or the order.
`ProviderKeysetWalkTest` 15: the 8 offset cases (documented old behaviour), the walks that fit in one answer (size 9 and 200, four filters) and the walk over nothing.

**What the code must do (one defect, for the java-developer).** `GET /api/v1/providers` ignores `afterNpi`: add the optional parameter as the contract 1.2.0 states. A value that is not
exactly 10 ASCII digits (empty, padded, signed, Unicode digits, or given more than once) is a 400 problem+json; `afterNpi` with a `page` other than 0 is a 400; with a valid key the items
are the matches with NPI greater than the key, ascending, at most `size`, page 0 in the answer, and `total` is the count of ALL matches of the filters (put the key predicate on the item
query only, not on the counting query); without the key nothing changes. The role check stays first.

**Not verified.** The green state: no code exists yet, so the keyset assertions have only been checked against a model of the contract, not against the service. The Cucumber suite as a
whole and the other test classes were not run in this step (only the new classes and the AC-9 scenario). JaCoCo and PIT were not run (tests first). The web UI client
`src/shared/api/generated/provider-service.ts` was not regenerated (lead). The tmsis-service part of Q-132 (paging by key, the "data changed" safety net) is a later round.
