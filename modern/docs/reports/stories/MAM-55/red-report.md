# MAM-55 red report (sanctions-service, SANCIN intake as a Spring Batch job)

No `red-report.md` existed for MAM-55 before this correction (the folder held only `red-blobs.txt`), so this file records the corrections made after the red step. The state before the
correction is kept in git's object store: the previous hash file is `MAM-55.tests.sha256` with sha256 `b4c0eaad03e4d7b1951783513452c7776845efbf9e776faf1a951dbd845b5116` (20 files), and every
file of it keeps the blob id it had in `red-blobs.txt` (the diff below shows added lines only).

## Test correction under Q-097 (code review 2026-11-19, additive)

Cause: the code review of sanctions-service (verdict MUST-FIX) found four defects the recorded tests do not catch. The user's standing rule **Q-097 C** applies: additive, no assertion weakened,
removed or relaxed, documented here and re-recorded; the code-reviewer confirms.

| Review item | AC | What the recorded tests miss | Evidence in the code (before the fix) |
|---|---|---|---|
| MUST-FIX 1 | AC-6, AC-7 | The key a re-run uses to recognise an applied record (`sanction.intake_run_id`) is written outside the transaction of the history row and the SanctionApplied outbox row. The recorded tests use a fake run table and a fake history, so a failing key write is never seen with a real database. | `SanctionIntake.java:126-133` calls `runs.link` after `RecordSanction.record` (`RecordSanction.java:93-101`) has committed |
| MUST-FIX 2 | AC-6 (and MAM-52 AC-7) | For a database error the run's `error` (202 answer and GET) and the stored `failure_reason` are Spring's whole wrapped text with the SQL statement (262 to 426 characters). Recorded `historyFailure` and `launchFailure*` only require the message to contain the record or a given text. | `SanctionIntake.process`, `SanctionIntakeJobConfig.failedRunListener`, `SanctionIntakeController` pass `e.getMessage()` through; the check `alreadyApplied` is outside the try, so it also names no record |
| MUST-FIX 3 | AC-6 (and MAM-52 AC-7) | provider-service is called with no timeout, so a status change that never answers keeps a run RUNNING or an online POST open. | `ProviderServiceClient` builds `RestClient::builder` with no read timeout; `pem.services.provider-timeout` is not read |
| SHOULD-FIX 4 | AC-3 (and MAM-52 AC-1) | A sanction with a blank or absent reason is announced with `"reason": null`, but `SanctionAppliedData.reason` of `events.yaml` (1.1.0) is a non-nullable string. | `application/SanctionAppliedData.java:7` serialises nulls |

**This correction is additive.** No recorded file was edited: the 20 recorded files of MAM-55 (and the 7 of MAM-52) are byte-identical, and 6 new files are added to MAM-55's hash file (4 test classes, the shared base class `ReviewFixesTest`, and the contract helper `EventsContractFile`). The two files
used by both stories (`ReviewFixesTest`, `EventsContractFile`) are listed in the hash files of both. MAM-52's own new files and their red run are in `MAM-52/red-report.md`. No Cucumber scenario was added: AC-1 to AC-7 already have scenarios, and these tests refine the same ACs at the real-database level.

### Files (all under `modern/services/sanctions-service/src/test/java/gov/medicaid/pem/sanctions/`; CRLF kept, formatted by Spotless on test sources only)

| File | Blob id (recorded) | In hash file of | Tests |
|---|---|---|---|
| `ReviewFixesTest.java` (abstract base, no test) | `eaad3c476dea089765d29c67f49361978f3f14fb` | MAM-55, MAM-52 | 0 |
| `EventsContractFile.java` (reads `contracts/asyncapi/events.yaml`) | `1b16ec105f5da09ee24c034b494f74b21b3ee3f0` | MAM-55, MAM-52 | 0 |
| `infrastructure/SanctionIntakeAtomicityTest.java` | `e76142a3efdbfcda876e24ea86849953aadbd51b` | MAM-55 | 6 |
| `infrastructure/SanctionIntakeFailureReasonTest.java` | `95837928c858901e695e0913ec512575345973e5` | MAM-55 | 22 (5 methods; 4 are parameterised over 5 failures, 1 over 2) |
| `infrastructure/ProviderTimeoutIntakeTest.java` | `c784ec36edc4358026e2538ce36bfaebc4a0712c` | MAM-55 | 3 |
| `infrastructure/SanctionIntakeEventContractTest.java` | `35c3080319a33cb91566edee623e41e48a873f19` | MAM-55 | 2 |

New hash file: `modern/docs/reports/stories/MAM-55.tests.sha256` = 26 files, sha256 `846046de712431e122e924c18f2b841e547338f876e040f4455df1ce2d6e1041`.

```diff
--- MAM-55.tests.sha256 (previous, 20 files)  +++ MAM-55.tests.sha256 (now, 26 files): added lines only
+ 5ef6f9dd0d3b...  .../sanctions/EventsContractFile.java
+ def2b3100cc4...  .../sanctions/ReviewFixesTest.java
+ c894e8812a07...  .../sanctions/infrastructure/ProviderTimeoutIntakeTest.java
+ 0828c0ad4aee...  .../sanctions/infrastructure/SanctionIntakeAtomicityTest.java
+ e0580e9371ec...  .../sanctions/infrastructure/SanctionIntakeEventContractTest.java
+ 1cf0f722835a...  .../sanctions/infrastructure/SanctionIntakeFailureReasonTest.java
```

### From the stalled draft to these files

An earlier test-writer stalled after creating 8 unrecorded files. They were reviewed, kept and corrected; nothing was dropped. Draft blob ids (git object store): `ReviewFixesTest` `33ce0dd2...`, `EventsContractFile` `1b16ec10...` (unchanged),
`SanctionIntakeAtomicityTest` `51fa6f60...`, `SanctionIntakeEventContractTest` `de2356f4...`, `SanctionIntakeFailureReasonTest` `49336266...`, `ProviderTimeoutIntakeTest` `64c4825f...`,
`ProviderTimeoutOnlineTest` `cbfdff4f...`, `SanctionAppliedEventContractTest` `445553ab...`. The draft's Gradle run had in fact finished: its tests ran in about 3 minutes (JUnit timestamps 12:36 to 12:39); the 12h 55m total of that log is time spent before the tests started, whose cause is not established, so the stall was not a test hang.

Changes made to the draft:
- **Time guards (MUST-FIX 3).** `ProviderTimeoutIntakeTest` and `ProviderTimeoutOnlineTest` carry JUnit `@Timeout(30)`; the online POST also runs under `assertTimeoutPreemptively(15 s)`; a run is awaited for at most `RUN_BOUND` (20 s) by the new `ReviewFixesTest.runToEndWithin`, which fails with a message when the run is still RUNNING. The WireMock delay stays 3 s, the timeout 1 s (`pem.services.provider-timeout=1s`).
- **Package.** The three database classes moved from `...sanctions.application` to `...sanctions.infrastructure`: the build's PIT run uses `targetTests = application.*` and runs them per mutant with a 256 MB heap, which only fits the unit tests that were already there; the Testcontainers classes belong next to `SanctionIntakeJobTest`.
- **Shared base.** `mvc` and `json` moved into `ReviewFixesTest` (no duplicate fields), and one warm-up call (a sanction for an unknown NPI: 404, nothing kept) runs once per JVM, so the first provider call of the context cannot be the one that exceeds the one-second read timeout once the fix exists.

### The new tests

All display names start `[MAM-55 AC-n]` and end `(code review 2026-11-19)`.

| Class | Tests | AC | What each asserts (one focus per test) |
|---|---|---|---|
| `SanctionIntakeAtomicityTest` | `noHistoryRowRemains` | AC-6 | A trigger on `sanction` rejects the write of `intake_run_id` for record 2 (the real database fails the link). No `sanction` row of NPI 1003000333 remains. |
| | `noEventRemains` | AC-6 | No SanctionApplied outbox row of 1003000333 remains. |
| | `failedRunNamesTheRecord` | AC-6 | The run is FAILED with `endedAt` set and the error contains `record 2` and `1003000333`. (guard, passes today) |
| | `recordOneKeepsItsResult` | AC-6 | Record 1 keeps its history row, its run key and its event: the fix must not roll the whole run back. (guard, passes today) |
| | `rerunLeavesOneHistoryRowPerRecord` | AC-7 | After the failed run, with the write let through, the same file for the same business date leaves exactly one row each for 1003000111, 1003000222 and 1003000333 (the re-run COMPLETED). |
| | `rerunPublishesOneEventPerRecord` | AC-7 | The same, for SanctionApplied events in the outbox. |
| `SanctionIntakeFailureReasonTest` | `errorHasNoSqlText` x5 | AC-6 | For a database error at HISTORY_INSERT, ALREADY_APPLIED_CHECK, RUN_COUNTS, RUN_COMPLETE or JOB_LAUNCH, the `error` of the 202 answer (when present) and of the GET, lower-cased, hold none of `select`, `insert`, `update`, `sql`. |
| | `errorIsShort` x5 | AC-6 | The same texts are not blank and at most 200 characters. |
| | `storedReasonHasNoSqlText` x5 | AC-6 | `sanction_intake_run.failure_reason` is not blank and holds none of the four fragments. |
| | `storedReasonIsShort` x5 | AC-6 | `failure_reason` is not blank and at most 200 characters. |
| | `errorNamesTheRecord` x2 | AC-6 | For HISTORY_INSERT and ALREADY_APPLIED_CHECK the GET `error` starts with `record 2 NPI 1003000333`. HISTORY_INSERT is a guard (passes today: the prefix is already there); ALREADY_APPLIED_CHECK fails today. |
| `ProviderTimeoutIntakeTest` | `timeoutEndsTheRunFailed` | AC-6 | provider-service answers record 2's status change after 3 s, the client timeout is 1 s: the run ends FAILED with `endedAt`, not RUNNING and not COMPLETED. |
| | `timeoutNamesTheRecord` | AC-6 | The error contains `record 2` and `1003000333`. |
| | `timeoutRecordsOnlyRecordOne` | AC-6 | The `sanction` table holds only 1003000111: record 2 not recorded, record 3 not processed. |
| `SanctionIntakeEventContractTest` | `blankReasonIsOmitted` | AC-3 | The SanctionApplied envelope of a record with a blank reason (bytes 12-51 spaces) has no `data.reason` member. |
| | `eventOfBlankReasonConformsToTheContract` | AC-3 | The same envelope has no violation against `SanctionAppliedEnvelope` of `events.yaml` (required members, closed envelope, uuid/date/date-time formats, enum, patterns, non-nullable strings). |

The failures are made with the **real database** (triggers that reject one write, lifted after each test: the model is `DirectoryExtractJobTest.rejectCompletedRuns`), except ALREADY_APPLIED_CHECK, which is a `SELECT` no trigger can reject: there the run table is a Mockito spy that stays real and throws Spring's own `UncategorizedSQLException` (with the statement text) for record 2 only.

### Red run (2026-11-20, `--no-daemon`, under the Gradle lock and a shell `timeout 900`), twice on the final files with identical results

Command: `:services:sanctions-service:test --tests <the 6 new classes>`. **39 tests (6 classes), 35 fail, 4 pass; no hang, no skip**; class times 1 to 12 seconds, whole build about 3 minutes. The 4 passes are guards (below); every other test fails for the expected reason, an assertion.

| Test | Failure today |
|---|---|
| `noHistoryRowRemains` | `expected: 0 but was: 1` (the history row of record 2 was committed before the key write failed) |
| `noEventRemains` | `expected: 0 but was: 1` (so was the SanctionApplied row) |
| `rerunLeavesOneHistoryRowPerRecord` | `[("1003000111", 1), ("1003000222", 1), ("1003000333", 2)] to contain exactly [(..111, 1), (..222, 1), (..333, 1)]`: record 2 applied twice |
| `rerunPublishesOneEventPerRecord` | the same counts for events: 2 SanctionApplied for 1003000333 |
| `errorHasNoSqlText` x5 | the lower-cased text holds `sql` and `insert`/`update`/`select`, for example "record 2 npi 1003000333 could not be applied: preparedstatementcallback; uncategorized sqlexception for sql [insert into sanction ..." |
| `errorIsShort` x5 | length 426 (HISTORY_INSERT), 262 (ALREADY_APPLIED_CHECK), 349 (RUN_COUNTS), 330 (RUN_COMPLETE), 324 (JOB_LAUNCH), all above 200 |
| `storedReasonHasNoSqlText` x5, `storedReasonIsShort` x5 | the stored `failure_reason` is the same text (lengths 426, 262, 349, 330, 324) |
| `errorNamesTheRecord[ALREADY_APPLIED_CHECK]` | the error is "PreparedStatementCallback; uncategorized SQLException for SQL [SELECT count(*) FROM sanction s JOIN ..." and does not start with `record 2 NPI 1003000333` |
| `timeoutEndsTheRunFailed` | `expected: "FAILED" but was: "COMPLETED"` (no timeout: the run waited three seconds and applied every record) |
| `timeoutNamesTheRecord` | `Expecting actual: "" to contain "record 2"` (error is absent) |
| `timeoutRecordsOnlyRecordOne` | `["1003000111", "1003000222", "1003000333"] to contain exactly ["1003000111"]` |
| `blankReasonIsOmitted` | `Expecting value to be false but was true` (`data` holds `"reason": null`) |
| `eventOfBlankReasonConformsToTheContract` | `Expecting empty but was: ["$.data.reason is null"]` |

**Guards that pass today (4 of 39).** They must stay green; each catches a wrong fix:
- `SanctionIntakeAtomicityTest.failedRunNamesTheRecord`: the run still ends FAILED naming record 2 and its NPI after the key write and the history row become one transaction (required by the review item).
- `SanctionIntakeAtomicityTest.recordOneKeepsItsResult`: record 1 stays committed, so a fix cannot wrap the whole run in one transaction.
- `SanctionIntakeFailureReasonTest.errorNamesTheRecord[HISTORY_INSERT]`: the shortened reason keeps the `record 2 NPI 1003000333` prefix that the recorded `historyFailure` already requires.
- `MAM-52` `SanctionAppliedEventContractTest.eventWithReasonConformsToTheContract` (see `MAM-52/red-report.md`): proves `EventsContractFile` accepts a valid envelope, so the red tests fail on `reason` and not on a too-strict checker.

**The time guards were exercised.** To show a missing timeout makes the tests fail instead of hanging, the two timeout classes were run once with the WireMock delay raised to 45 s (a temporary edit, restored byte-identically and checked by sha256 before recording; not part of the recorded files): the three intake tests failed after 20 s with `run ... is still RUNNING after PT20S: the call to provider-service is not cut off`, and the three online tests failed after 15 s with `POST /api/v1/sanctions did not answer within PT15S: provider-service has no timeout`. The build ended with a normal failure (about 6 minutes in total).

### Constraints for the developer from recorded tests (they stay green)

- `SanctionIntakeTest.historyFailure` (the unit fake throws `IllegalStateException("history unavailable for ...")`) requires the message `record 2 NPI 1003000333 could not be applied` plus the text `history unavailable`, and `SanctionIntakeControllerTest.launchFailure` / `launchFailureWithoutMessage` require `job PRVSAN01 not started: boom` and `java.lang.IllegalStateException` unchanged. So only a **database error** (a Spring `DataAccessException` or a `SQLException` in the cause chain) is shortened to its root-cause message, first line, without statement text; other messages pass through.
- The short reason needs the record named for a failure of the already-applied check (`errorNamesTheRecord[ALREADY_APPLIED_CHECK]`): that call is outside the try of `SanctionIntake.process` today.
- `SanctionIntakeRuns` keeps `alreadyApplied(UUID, LocalDate, String, String, String, LocalDate)`: the spy stubs it.
- `pem.services.provider-timeout` is a Spring `Duration` (the tests set `1s`); a timed-out call is "not applied" (`Outcome.FAILED`, 503 online).

### Hashes

Re-recorded with `test_protection.py record MAM-55` (the 20 recorded files plus the 6 new ones); `check` prints `OK: 26 test files of MAM-55 unchanged; no unrecorded test files`. The red copies are in git's object store, listed in `MAM-55/red-blobs.txt`; the draft copies stay under the blob ids named above.
