# MAM-52 red report (sanctions-service, record a sanction online)

The red run of the original red step is in `red-run.txt` (it is unchanged). This file records the correction made after that step. The previous hash file is `MAM-52.tests.sha256` with sha256
`f8cb75b87a8fb1170196945f5f48d167e5b55d82dc8f473e8aa5df506559c86f` (7 files, the value of `testsSha256` in `modern/specs/MAM-52.md`); every file of it keeps its blob id in `red-blobs.txt`.

## Test correction under Q-097 (code review 2026-11-19, additive)

Cause: two items of the sanctions-service code review (verdict MUST-FIX) reach the online endpoint POST /api/v1/sanctions. The full account, the draft history and the constraints for the developer are in
`modern/docs/reports/stories/MAM-55/red-report.md`; this section lists what MAM-52 owns. The user's standing rule **Q-097 C** applies: additive, no assertion weakened, removed or relaxed, documented and re-recorded; the code-reviewer confirms.

| Review item | AC | What the recorded tests miss |
|---|---|---|
| MUST-FIX 3 (no timeout) | AC-7 ("a failure to apply is never reported as not found or as applied") | AC-7 injects a server error and a connection reset, never a provider-service that does not answer. `ProviderServiceClient` has no read timeout, so POST /api/v1/sanctions stays open and, when the answer finally comes, is reported as applied. |
| SHOULD-FIX 4 (event contract) | AC-1 (the SanctionApplied event) | A sanction recorded without a reason is announced with `"reason": null`; `SanctionAppliedData.reason` of `events.yaml` 1.1.0 is a non-nullable string, so the member must be omitted. |

**This correction is additive.** None of the 7 recorded files of MAM-52 was edited (byte-identical); 4 files are added to its hash file, 2 of them also in MAM-55's (the shared base `ReviewFixesTest` and the helper `EventsContractFile`).

### Files (under `modern/services/sanctions-service/src/test/java/gov/medicaid/pem/sanctions/`; CRLF kept)

| File | Blob id (recorded) | In hash file of | Tests |
|---|---|---|---|
| `ReviewFixesTest.java` (abstract base) | `eaad3c476dea089765d29c67f49361978f3f14fb` | MAM-52, MAM-55 | 0 |
| `EventsContractFile.java` | `1b16ec105f5da09ee24c034b494f74b21b3ee3f0` | MAM-52, MAM-55 | 0 |
| `api/ProviderTimeoutOnlineTest.java` | `396abdad068c3e9a026b2db793d2f934ad43cfde` | MAM-52 | 3 |
| `api/SanctionAppliedEventContractTest.java` | `3a81378d4902592483cb3c48dca0067744421ee6` | MAM-52 | 3 |

New hash file: `modern/docs/reports/stories/MAM-52.tests.sha256` = 11 files, sha256 `5f94978ef3d3c0dbfd1ed6a7d153dbc524ecccb43e362d37784c6a3c51cd79f3`. **`testsSha256` in `modern/specs/MAM-52.md` (line 18) still holds the previous value** and has to be updated by whoever finishes the story (the test-writer may not write the spec).

```diff
--- MAM-52.tests.sha256 (previous, 7 files)  +++ MAM-52.tests.sha256 (now, 11 files): added lines only
+ 5ef6f9dd0d3b...  .../sanctions/EventsContractFile.java
+ def2b3100cc4...  .../sanctions/ReviewFixesTest.java
+ e7add8d631fe...  .../sanctions/api/ProviderTimeoutOnlineTest.java
+ efb1683974a9...  .../sanctions/api/SanctionAppliedEventContractTest.java
```

### The new tests

All display names start `[MAM-52 AC-n]` and end `(code review 2026-11-19)`. The POST is made as SUPERVISOR through MockMvc against the real application, Postgres and the WireMock that plays provider-service; the pem.services.provider-timeout of the test context is `1s`.

| Class | Test | AC | What it asserts |
|---|---|---|---|
| `ProviderTimeoutOnlineTest` | `timeoutIs503` | AC-7 | provider-service answers the status change after 3 s: the POST answers 503 with content type `application/problem+json`. |
| | `timeoutNamesTheNpi` | AC-7 | The problem body has `status` 503 and a `detail` naming NPI 1003000111. |
| | `timeoutRecordsNothing` | AC-7 | No `sanction` row and no SanctionApplied outbox row exist for 1003000111. |
| `SanctionAppliedEventContractTest` | `absentReasonIsOmitted` | AC-1 | A termination posted without a reason: the stored envelope has no `data.reason` member. |
| | `eventWithoutReasonConformsToTheContract` | AC-1 | That envelope has no violation against `SanctionAppliedEnvelope` of `events.yaml`. |
| | `eventWithReasonConformsToTheContract` | AC-1 | With reason `SYNTHETIC TEST REASON` the envelope has no violation and `data.reason` is that text. (guard / positive control, passes today) |

Time guards of `ProviderTimeoutOnlineTest`: JUnit `@Timeout(30)` on the class and `assertTimeoutPreemptively(15 s)` around the POST, so a missing timeout in the service fails the test.

### Red run (2026-11-20, `--no-daemon`, under the Gradle lock and a shell `timeout 900`), twice with identical results

Run together with the MAM-55 classes (see `MAM-55/red-report.md`): of the **6 tests of these two classes, 5 fail and 1 passes; no hang.**

| Test | Failure today |
|---|---|
| `timeoutIs503` | `expected: 503 but was: 201` (no timeout: the POST waited three seconds and recorded the sanction) |
| `timeoutNamesTheNpi` | `expected: 503 but was: 0` (the 201 body is a Sanction, it has no `status`) |
| `timeoutRecordsNothing` | `expected: 0 but was: 1` (the history row was written) |
| `absentReasonIsOmitted` | `Expecting value to be false but was true` (the member is there, with the value null) |
| `eventWithoutReasonConformsToTheContract` | `Expecting empty but was: ["$.data.reason is null"]` |
| `eventWithReasonConformsToTheContract` | passes today: the checker accepts a valid envelope, so the two failures above are about `reason`, not a too-strict checker; after the fix it guards that the reason is not dropped. |

The time guard was exercised once with a 45 s delay (a temporary edit, restored byte-identically): the three tests failed after 15 s with `POST /api/v1/sanctions did not answer within PT15S: provider-service has no timeout`.

### Hashes

Re-recorded with `test_protection.py record MAM-52` (the 7 recorded files plus the 4 new ones); `check` prints `OK: 11 test files of MAM-52 unchanged; no unrecorded test files`.
