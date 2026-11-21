# MAM-71 red report (tmsis-service, monthly T-MSIS extract in the legacy 120-byte layout)

No `red-report.md` existed for MAM-71 before this correction (the folder held only `red-blobs.txt` and `trace.json`), so this file records the correction made after the red step.
The state before it is kept in git's object store: the previous hash file `MAM-71.tests.sha256` had 31 files and sha256
`74974d132be0958c9a41f3c6fe7fc70a2c39bc6ecc8bb70e9d4147dc23a5eec8`; every file of it keeps the blob id it had in `red-blobs.txt` (a copy of the previous hash file is in the session scratchpad).

## Test correction under Q-097 (code review 2026-11-20, additive)

Cause: the second code review of tmsis-service (verdict MUST-FIX, one item) found that the walk over the provider data ends too early.
`ProviderPagesReader.java:76` (javadoc :29-34) ends the walk when the answer is short **or when the rows read have reached the total** (`rowsRead >= answer.total()`). With paging by key (Q-132, contract
provider-service 1.2.0) a provider that is terminated before the key (one already read) and one that is approved after it keep the total; the second answer is then a full one of 200, the rows read equal the total
(400), and the last provider, ACTIVE for the whole run, is never asked for: the run is COMPLETED, archived and announced with a file one record short. This breaks MAM-71 AC-5 ("no provider is missed or repeated"),
Q-132 and the walk end of the contract ("the walk ends at the first answer whose items list is empty; an answer with fewer items than size is the last one that holds providers"), R-PRVTMS01-cbl-00027 (the loop reads
until the end of the master). The total says nothing about where the list ends once it can be the same while the list shifts. Stories MAM-71 and MAM-75 were reopened in Jira for the review items; this one is MAM-71 AC-5.

The user's standing rule **Q-097 C** applies: no assertion weakened, removed or relaxed, new test classes preferred, every change documented here and re-recorded.

### What changed in recorded files (4 expected values moved with the contract, 2 scenarios appended; nothing removed)

| Recorded file | Change | Why it is not a weakening |
|---|---|---|
| `infrastructure/ProviderPagesReaderTest.java` `pageBoundaries` (3 rows of the `@CsvSource`) | the number of requests: `200, 200, 1` -> `2`; `400, 400, 2` -> `3`; `1000, 1000, 5` -> `6` | each is still one exact value (`hasSize(n)`); the old values were the defect itself: an exact multiple of 200 providers needs the request that gets the empty answer |
| `infrastructure/ProviderPagesReaderTest.java` `requestsAreInOrder` | the list of requests of 1000 providers gets a sixth: `1003001000/200` (key = the last NPI) | `containsExactly` stays exact, the list is the walk the contract describes |
| `infrastructure/ProviderPagesReaderTest.java` class javadoc | a paragraph that names the correction | text only |
| `resources/features/MAM-71.feature` | two `@AC-5` scenarios appended at the end (lines 138 to 156 of the file) | additive; no step, tag or line of the scenarios before was touched |

All other rows (`0, 1, 199, 201, 399, 401, 450` providers), `fourHundredFifty`, `endOfFileIsStable`, `lazy`, `shortPageEndsTheRead`, `failingPageSurfaces` and the 14 tests of `ProviderKeysetReaderTest` are byte-identical and stay green.

### New files (under `modern/services/tmsis-service/src/test/java/gov/medicaid/pem/tmsis/`; CRLF kept, formatted by Spotless on test sources only)

| File | Blob id (recorded) | Tests |
|---|---|---|
| `infrastructure/ProviderWalkEndTest.java` (unit: the reader against the contract-1.2.0 fake `ProviderKeysetReaderTest.Directory` and the scripted `Scripted`) | `eb51d31ebd7aaa4935b072bf53559b3008b1f07f` | 10 methods, 13 invocations |
| `api/ProviderWalkEndApiTest.java` (the whole application: Spring Batch job, Postgres, Kafka, fake provider-service whose data changes between two answers) | `f8d224f6d6bfaca2e23d426432f8a0f0e33eb9e2` | 5 methods, 6 invocations |
| `acceptance/WalkEndSteps.java` (one Cucumber step: providers with every second NPI, so a provider can enter between two that are there) | `0bea9d7956599c5d24b7766ddad0eb913abd796f` | 0 (glue) |

Changed recorded files, new blob ids: `ProviderPagesReaderTest.java` `f8e34d6218bf9de969d75286d91e76794bbec6d8` (was `78fa4729f2035f5bb1e974d180eaddf26e7d957c9ff034a56398623c16e13e06` as sha256), `MAM-71.feature` `5b0caa673a05868bde3ceb5c4f620f76c1150108`.

Hash file now: **34 files**, sha256 `0a742ff7353ca4c1096e941ca6643bf6bf2f3ce1cebc12d11a8beb388774670a` (previous: 31 files). `test_protection.py check MAM-71`, `MAM-75` and `MAM-78` all print OK. MAM-75 and MAM-78 are not affected:
their files are byte-identical (25 and 10 files) and the three new files are recorded in MAM-71 only (the test-protection check reads every hash file).

### The reviewer's case, as written

`Directory.spaced(400)`: 400 ACTIVE providers with every second NPI, 1003002002 to 1003002800. After the first answer (1003002002 to 1003002400) the provider 1003002010 (already read) is terminated and 1003002501 is
approved; the total stays 400. Expected: 1003002800 is read, in 3 requests (none, after 1003002400, after 1003002798); the third answer holds only 1003002800 and ends the walk.

## The red run

2026-11-21 07:48 to 07:51, `./gradlew --no-daemon -I <spotless init script, test sources only> :services:tmsis-service:spotlessApply :services:tmsis-service:test --tests ...ProviderWalkEndTest --tests ...ProviderPagesReaderTest
--tests ...ProviderKeysetReaderTest --tests ...ProviderWalkEndApiTest --tests ...CucumberSuiteTest --continue`: **89 tests, 25 failed, 0 skipped**. Every failure is one of the new or corrected tests, and each fails for the
reason below (an assertion about the end of the walk, not a compile or set-up error). Everything else of the five classes is green (ProviderKeysetReaderTest 14 of 14, ProviderPagesReaderTest 12 of 16, the other 38 of 40 scenarios).

| Test (display names start `[MAM-71 AC-5]`) | AC | Fails how (today) |
|---|---|---|
| `ProviderWalkEndTest#lastProviderIsReadAfterAPairedChange` | AC-5 | `Expecting ListN ... to contain "1003002800"`: the walk ended after 2 requests with 400 rows |
| `ProviderWalkEndTest#everyProviderOfTheWalkIsReadOnce` | AC-5 | the 400 NPIs read lack `1003002800`; 401 expected |
| `ProviderWalkEndTest#reviewersCaseIsThreeRequests` | AC-5 | requests `[none/200, 1003002400/200]`; `1003002798/200` not asked |
| `ProviderWalkEndTest#pairedChangeAfterAnyAnswer` (answer 1, answer 2) | AC-5 | 600 ACTIVE, one leaves and one enters: the rows lack the last provider `1003003200` (the walk ended at the third answer) |
| `ProviderWalkEndTest#exactMultipleEndsWithTheEmptyAnswer` (200, 400, 600) | AC-5 | `Expected size: 2 / 3 / 4 but was: 1 / 2 / 3`: the request that gets the empty answer is never made |
| `ProviderWalkEndTest#endOfFileIsStableAfterTheEmptyAnswer` | AC-5 | `Expected size: 2 but was: 1` (200 providers: one request) |
| `ProviderWalkEndTest#totalDoesNotEndTheWalk` | AC-5 | `Expected size: 400 but was: 200`: total 100 on every answer ended the walk after the first full answer |
| `ProviderWalkEndTest#rowsAboveTheTotalAreNotAFailure` | AC-5 | `Expected size: 401 but was: 400`: rows read (400) reached the total (400) and the 401st row was never asked for |
| `ProviderWalkEndTest#terminationAfterTheLastFullAnswerIsSeenOnTheEmptyAnswer` | AC-5 | `the walk must fail, but it completed with 400 rows`: the answer that would show the changed total (399) is never requested |
| `ProviderWalkEndTest#approvalAfterTheLastFullAnswerIsSeenOnTheNextAnswer` | AC-5 | `the walk must fail, but it completed with 400 rows`: same, total 401 |
| `ProviderWalkEndApiTest#fileHoldsEveryProviderOnce` | AC-5 | the file of a COMPLETED run lacks `1003002800` |
| `ProviderWalkEndApiTest#runCountsTheRowsOfTheFile` | AC-5 | `expected: 401 but was: 400` (rows of the run) |
| `ProviderWalkEndApiTest#eventCountsTheRowsOfTheFile` | AC-5 | `expected: 401 but was: 400` (rowCount of ExtractProduced) |
| `ProviderWalkEndApiTest#searchesOfTheReviewersCase` | AC-5 | afterNpi of the searches `[null, 1003002400]`; `1003002798` not asked |
| `ProviderWalkEndApiTest#exactMultipleEndsWithTheEmptyAnswer` (200, 400) | AC-5 | `Expected size: 2 / 3 but was: 1 / 2` searches |
| Scenario "One provider leaves and one enters after the first answer, the total stays the same: the last provider is not lost behind a full answer" | AC-5 | `Expected size: 48120 but was: 48000`: 400 records, not 401 |
| Scenario "An exact multiple of 200 providers ends with the empty answer, so the last provider is read and the walk ends" | AC-5 | `Expected size: 3 but was: 2`: provider-service asked twice |
| `ProviderPagesReaderTest#pageBoundaries` (200, 400, 1000; corrected values) | AC-5 | `Expected size: 2 / 3 / 6 but was: 1 / 2 / 5` requests |
| `ProviderPagesReaderTest#requestsAreInOrder` (corrected list) | AC-5 | five requests, the sixth (`1003001000/200`) is missing |

Two of these (the last full answer, `terminationAfterTheLastFullAnswer...` and `approvalAfterTheLastFullAnswer...`) are a consequence of the fix and not a new rule: once the reader asks for the answer after the last full
one, the total of that answer is compared like every other answer (Q-132: a changed total ends the run FAILED with "provider data changed during the extract; run it again"); today the change goes unseen because the
walk ends without that request. They pin that the check is not skipped on the answer that ends the walk.

## Shown to pass with a reference fix (scratch copy, no repository file touched)

The one-line fix of the item (a copy of `ProviderPagesReader` in the session scratchpad, put in front of the main classes on the class path: `lastAnswerRead = items.size() < ActiveProviders.PAGE_SIZE;`, the field
`rowsRead` removed) was run with the JUnit launcher, outside Gradle, with Docker for Testcontainers, 2026-11-21 07:58 to 08:01 (through the shared lock): **133 tests, 133 successful, 0 failed, 0 skipped**
(ProviderWalkEndTest 13, ProviderPagesReaderTest 16, ProviderKeysetReaderTest 14, ProviderWalkEndApiTest 6, ProviderDataChangeApiTest 8, ReportedProvidersApiTest 33, ProviderContractUseTest 3,
CucumberSuiteTest 40 scenarios). So the 25 red tests turn green with that change alone, and the recorded tests around them (the keyset cases, the concurrent-change cases, the 450-provider cases) stay green.
This proves the expected numbers of the new tests (3 requests with the keys 1003002400 and 1003002798, 401 rows, 601 rows for the 600-provider cases); it is not the java-developer's green step.

## For the java-developer (least change; the test-writer touches no production code)

`ProviderPagesReader.readNextAnswer`: `lastAnswerRead = items.size() < ActiveProviders.PAGE_SIZE;` (drop `rowsRead`, `answer.total()` is still read by `checkTotal` only), and correct the class javadoc (the sentence "or when the rows read
reach the total" and the paragraph "The one gap ..." at :29-34): with the fix there is no gap. The story report text of MAM-71 AC-5 ("page loop until the last page") needs no change.

## Not testable / not covered

Nothing of the review item is untestable. Not covered by a test on purpose: a real provider-service (the fake answers as contract 1.2.0 says, and `TestProviderServiceKeysetTest` pins the fake to the contract).

## Test correction of the first round under Q-097 C and the user's decision Q-132 (2026-11-21, 05:38 to 07:07)

Written by the lead on 2026-11-21 from the test-writer's result and from the diff of the test tree against commit a6a8814,
because the test-writer of that round could not write report files. It records what changed in files that were already
recorded before the round; the section above records the correction round that followed (07:39 to 08:19).

Cause: the code review of 2026-11-20 rated two points MUST-FIX. (1) The extract read the providers by page number and never
noticed when the data changed between two pages, so a termination or an approval during a run could drop a provider from
the file or write one twice. The user decided the lasting fix on 2026-11-20 (Q-132): provider-service offers paging by key
(`afterNpi`, contract 1.2.0), the extract walks by key, and a run that finds its provider data changed fails with a clear
reason. (2) A run that was interrupted when the service stopped stayed RUNNING for ever.

Recorded files that changed, and why no assertion is weaker (lines added and removed against a6a8814):

| File | Recorded under | + / - | What changed |
|---|---|---|---|
| `api/ReportedProvidersApiTest.java` | MAM-71 | 7 / 5 | (a) The test "[MAM-71 AC-5] 450 providers are read as three pages of 200" asserted the page numbers 0, 1, 2 of the three requests; it now asserts the keys of the three requests (none, 1003001200, 1003001400) and that none asks for a page after the first. The assertion follows Q-132: the way of paging is what the user changed. (b) The test "[MAM-71 AC-1] the file is in ascending NPI order ..." made the fake provider-service answer in descending order; the two calls `answerInDescendingOrder()` are gone and the title says the providers were registered in descending order. Its assertion (the file is in ascending NPI order) is unchanged. A provider-service that answers out of order is now a FAILED run, asserted by new tests (`ProviderKeysetReaderTest#descendingAnswer`, `ProviderDataChangeApiTest#providerAnswersDescending`), so the old premise no longer describes a run that writes a file. |
| `infrastructure/ProviderPagesReaderTest.java` | MAM-71 | 47 / 7 | The fake port got the key form `after()`. Two tests that asserted the requests as pages ("0/200", "1/200", "2/200") assert them as keys ("none/200", "1003000200/200", "1003000400/200"). The table of requests at exact multiples of 200 changed in the correction round (see the section above). |
| `TestProviderService.java` | MAM-71, MAM-75 | 155 / 21 | Test support, no assertion: the fake provider-service answers `afterNpi` as contract 1.2.0 says (ascending order, 400 for a wrong parameter) and can change its data between two answers (`changeStatus`, `add`, `answerIgnoringTheKey`). `TestProviderServiceKeysetTest` (new, 21 tests) pins the fake to the contract. |
| `acceptance/ExtractSteps.java` | MAM-71, MAM-75, MAM-78 | 68 / 4 | The step "provider-service was asked for the pages {string} with the size {int}" is replaced by a step for the keys; new steps for a provider that is terminated or approved after the first answer, for a run that was RUNNING when the service stopped, and for the error text of a run. |
| `features/MAM-71.feature` | MAM-71 | 69 / 2 | The description says that the pages are walked by key since provider-service 1.2.0; the line "provider-service was asked for the pages "0,1,2" with the size 200" became "... in 3 requests of the size 200, the first without a key and the others after the NPIs "1003001200,1003001400""; scenarios for changed provider data were appended. |
| `features/MAM-75.feature` | MAM-75 | 10 / 0 | One scenario appended (a run that was RUNNING when the service stopped is FAILED after the start); nothing removed. |

New files of the round (all additive): `ServiceRestart.java` (support), `TestProviderServiceKeysetTest`,
`api/ProviderDataChangeApiTest`, `application/FailureReasonTest`, `infrastructure/InterruptedRunRecoveryTest`,
`infrastructure/ProviderContractUseTest`, `infrastructure/ProviderKeysetReaderTest`.

Red before the fix (test-writer's runs): whole module 542 tests, 40 red; the touched classes 142 tests, 41 red; reasons:
the run COMPLETED where FAILED is expected, a provider missing from the file, the three requests without a key, a run that
stays RUNNING and a job execution that stays STARTED. The old versions of the changed files are the blobs of commit a6a8814.
The second review (07:39) compared the changed files byte by byte with their old versions and found no weaker assertion.
