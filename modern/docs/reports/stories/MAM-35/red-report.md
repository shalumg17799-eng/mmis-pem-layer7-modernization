# MAM-35 red report

The tests of MAM-35 recorded before this section are listed in `MAM-35.tests.sha256` (8 files: the feature file, `RiskSteps`,
`CucumberSpringConfig`, `CucumberSuiteTest`, `IntegrationTest`, `PlatformChecksTest`, `AssessRiskTest`, `RuleSetTest`). This file did not
exist before; it starts with the section below. MAM-38 and MAM-41 (the other stories of risk-service) are **not affected** by this
correction: their hash files are unchanged and `check MAM-38` and `check MAM-41` print OK.

## Test correction under Q-097 (re-review 2026-11-20, additive)

**Cause: the code review of risk-service and service-kit, round 1, returned MUST-FIX with one item**
(`ScreeningEventsListener.java:40`, also `:54` and `:58`):

> Raw envelope correlationId goes into the MDC. `EventEnvelope.correlationId` is a plain String and nothing enforces the uuid format.
> This overwrites the id the kit interceptor sanitized. `OutboxWriter` then copies it into `RiskTierAssigned`, `:54` logs it raw and
> `:58` clears the MDC early. Fix: remove the MDC put/remove (`CorrelationIdRecordInterceptor` already sets the sanitized id), or wrap the
> work in `CorrelationId.with(e.correlationId(), ...)`; log `MDC.get(CorrelationId.MDC_KEY)`. Test-writer adds a crafted-id test (CR/LF,
> more than 64 characters): log and outbox carry a safe id, a UUID is kept.

Rule applied: Q-097 C (additive). **No recorded test was edited, weakened or removed** (the 8 recorded files have the same sha256 as before),
**one new test class** was added, and **no production file was touched** (nothing under `src/main` was opened for writing; the kit did not
change, so no kit test was added and the kit hash files MAM-26, MAM-29 and MAM-31 are not re-recorded).

### Which story and AC, and why

All tests are tagged **`[MAM-35 AC-8]`**: the AC says the failure is "written to the JSON log with the event's correlation id", its basis cites
`java-microservices` section 11 (the correlation id passed through events and logged on every line), and `ScreeningEventsListener` is the
code of MAM-35. The part about the published envelope (`RiskTierAssigned` carries the id) is cross-referenced in the class comment to
MAM-36 AC-4 ("the id follows the events"), which is a platform story with no risk-service test of its own; the nearest AC was chosen
and is stated here so the reviewer can overrule it.

### New file (blob id is `git hash-object`; it is also in `red-blobs.txt`)

| File | Blob id | Line endings | Tests |
|---|---|---|---|
| `modern/services/risk-service/src/test/java/gov/medicaid/pem/risk/infrastructure/ScreeningEventsCorrelationIdTest.java` | `7d419ce2e7e7cf19d5af848e0ccf39edcb011d2f` | CRLF (Spotless output, like every other test file of risk-service) | 35 |

Formatted with Spotless through an init script that targets **only this file**
(`scratchpad/tw-spotless-risk-newfile.gradle`), so no recorded file could be reformatted.

### How the tests reach the code

Two ways in, both against the real Spring context with Testcontainers Postgres and Kafka (`IntegrationTest`):

1. **Through Kafka** (the real `CorrelationIdRecordInterceptor` and the real listener): a `ScreeningCompleted` PASS envelope with the
   crafted `correlationId` is published to `pem.screening.events`; the test waits (45 s, then an `AssertionError`) until the event is in
   `processed_event` and reads `payload->>'correlationId'` of the `RiskTierAssigned` row in `outbox_event`.
2. **Calling `listener.onEvent` directly**, with the MDC set exactly as the interceptor sets it (`MDC.put("correlationId",
   CorrelationId.sanitize(raw))`). A failing event is made with a taxonomy of 13 characters, longer than the `CHAR(10)` column
   `risk_assessment.taxonomy`: a real database error at the store step of `AssessRisk` (the "database error while storing the
   assessment" of AC-8), asserted to be a `DataAccessException` so the test cannot pass for another reason. A log capture on the
   listener's logger copies each line **with its MDC at the moment it is written** (the MDC is thread-local; reading it later from the
   test thread would show the wrong thread's MDC) and keeps only the lines of the test's own thread, so a retry on the Kafka consumer
   thread can never add a second error line.

Every wait has its own guard: the producer has `max.block.ms`, `request.timeout.ms` and `delivery.timeout.ms` of 20, 10 and 20 s and the
send is read with `get(30 s)`; the poll for `processed_event` stops after 45 s with an `AssertionError`; and the class has `@Timeout(120 s)`
per test. A defect makes a test fail; nothing can hang.

Crafted ids (7): CR/LF then a forged JSON log line; a bare LF then a forged line; an ANSI escape and a tab; 65 characters (one over the
limit); 200 characters; a path and a quote (`../../etc/x"y`); an empty string. The safe form is the one of `CorrelationId.sanitize`:
`[A-Za-z0-9._:-]{1,64}`. Each crafted id is also not equal to what is logged or published.

### The tests, what each asserts, and how it fails today

Run: `./gradlew --no-daemon -I scratchpad/tw-spotless-risk-newfile.gradle :services:risk-service:spotlessApply
:services:risk-service:test --tests 'gov.medicaid.pem.risk.infrastructure.ScreeningEventsCorrelationIdTest'`. Final run: **35 tests,
33 failed, 2 passed, 0 skipped** (5 s of test time; Docker containers started by Testcontainers only).

| Method (item part it covers) | Cases | Today | Failure reason observed (the expected reason) |
|---|---|---|---|
| `publishedEventCarriesASafeId` (OutboxWriter copies the raw id into RiskTierAssigned) | 7 | 7 red | the envelope's `correlationId` is the raw string: `Expecting actual: "abc\nINFO FORGED line" to match pattern "[A-Za-z0-9._:-]{1,64}"` (same for the 65 and 200 character ids, the path and the empty string `""`) |
| `failureLogHoldsNoForgedText` (`:54` logs the raw id, log forging) | 3 | 3 red | the error line is `risk assessment failed for event ... of NPI 1003000772 (correlation id abc` + LF + `INFO FORGED line): ...DataIntegrityViolationException...`; `doesNotContain(raw)` fails |
| `failureLogCarriesASafeId` (the id of the log line, i.e. the MDC, is safe, and the message carries the same id) | 7 | 7 red | the MDC `correlationId` of the logged line is the raw crafted string, which does not match `[A-Za-z0-9._:-]{1,64}` |
| `mdcIsKeptAfterAHandledEvent` (`:58` clears the MDC early) | 8 (7 crafted + a UUID) | 8 red | `expected: "<the id the interceptor set>" but was: null` |
| `mdcIsKeptAfterAFailedEvent` (`:58`, failure path) | 8 (7 crafted + a UUID) | 8 red | `expected: "<the id the interceptor set>" but was: null` |
| `publishedEventKeepsAUuid` (a UUID is kept in the envelope) | 1 | **passes** | regression guard, see below |
| `failureLogKeepsAUuid` (a UUID is kept in the log line and the MDC) | 1 | **passes** | regression guard, see below |

**Two tests pass today, by design.** The review asks that "a UUID is kept". With a UUID the raw id and the sanitized id are the same
string, so today's code satisfies it. The two tests exist to stop an **over-correction** (a fix that always generates a new id would break
the correlation of AC-8). They assert exact equality with the UUID that was sent, in the published envelope and in the log line and its MDC.
This is the only exception to "every new test must fail"; the reviewer can overrule it.

### How the fix makes them pass (analysis; no production code may be edited by the test-writer)

Both fixes the reviewer proposes satisfy every test, because the tests only depend on what the interceptor sets:
- **Remove the listener's `MDC.put/remove`, log `MDC.get(CorrelationId.MDC_KEY)`**: the outbox and the log carry the interceptor's
  `sanitize(raw)` (a UUID stays, anything else becomes a generated UUID), and the MDC is left to the interceptor's `afterRecord`.
- **Wrap the work in `CorrelationId.with(e.correlationId(), ...)`**: the id is `sanitize(raw)` while the work runs and the previous MDC value
  is restored afterwards, including on an exception (`with` uses try/finally). The `catch` that logs must run inside the wrapper (or log
  `MDC.get(...)`), otherwise the line would still carry the raw id.

The tests do not require the id in a crafted case to equal a particular value (a generated UUID is unpredictable); they require it to be
safe, different from the crafted value, and the same id in the log message and in the log line's MDC.

### Hash record

`MAM-35.tests.sha256` now lists **9 files** (the 8 recorded files unchanged, plus the new class). New sha256 of the hash file:
`03c8b6ec1037ee9efcecf6a5cfaedac4e9e0d42960d742e2083a8c5b8d587f54`; the previous one, still in `specs/MAM-35.md` as `testsSha256`, is
`c52765b53bc6c703ba4e17932c36a5cc427bc7e435786174c16dbda487828f99` (the lead updates the spec). `check MAM-35`, `check MAM-38` and
`check MAM-41` all print OK.

### Not verified

- The new class was run **alone** (`--tests`), as asked; the whole `:services:risk-service:check` (Cucumber, PIT, JaCoCo) was not run.
- The tests were not run against a fixed listener (the test-writer may not edit production code); the passing side is shown by the analysis
  above. The two guard tests were not mutation-checked against an over-correcting fix.
- The `CorrelationIdRecordInterceptor` and `CorrelationId.sanitize` are used as they are (kit tests exist under MAM-36 and
  MAM-26/29/31 recordings); `OutboxWriter` and `CorrelationId.currentOrNew()` still copy whatever the MDC holds without sanitizing it. That is
  defence in depth beyond this item (the reviewer's fix is in the listener), so no kit test was written for it.
