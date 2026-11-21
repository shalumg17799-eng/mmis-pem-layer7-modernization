# MAM-36 red report: provider-service AC-5 (health follows the database) and the correction of a hanging test

`MAM-36.tests.sha256` was re-recorded on 2026-11-20 (4 provider-service files plus the kit and enrollment files recorded before): the files recorded before, plus
`ProviderOutageTest.java` and `junit-platform.properties`.

## AC-5 on provider-service (Q-099 answer B)

`ProviderOutageTest` (new): `healthFollowsTheDatabase` pauses the test's own Testcontainers Postgres inside the test (no other container is touched), waits 1.5 s so that the connection
pool validates its idle connections, calls `/actuator/health` in a separate thread with a 90 s limit, expects **503 with status DOWN**, un-pauses in a `finally`, and expects 200 UP again
within 60 s. `readFailsVisiblyAndRecovers` is the same outage for MAM-37 AC-8. Written after the code: green at once (63 s for both).

## Correction of a recorded test that hung the build (Q-097 C, no assertion weakened)

`ObservabilityTest.healthFollowsTheDatabase` ([MAM-36 AC-1], recorded) paused the database and called `/actuator/health` on the test thread at once. When the pool had used its connection less
than 500 ms before, it handed that connection out unchecked, `DataSourceHealthIndicator` called `isValid` on it, and pgjdbc (no socket timeout) **blocked for as long as the database was
frozen**: the test never reached its `finally` that un-pauses the container. This is the run that "was still running after 31 minutes" (18:58). It happened again in this lane at 20:29: the run was found
14 minutes into that call with a thread dump, and the test's own throwaway Postgres container of that run was un-paused by hand once. The default limit of 6 minutes in `junit-platform.properties` then
reported the test as failed ("timed out after 6 minutes") but could not interrupt the blocked read by itself.

Byte-level difference of `ObservabilityTest.java` (CRLF kept): imports of `ExecutionException, ExecutorService, Executors, Future, TimeUnit, TimeoutException`; in `healthFollowsTheDatabase` the call
`assertThat(healthAnswer(503).path("status").asText()).isEqualTo("DOWN")` became `Thread.sleep(1500)`, the call `healthAnswer(503)` in a single-thread executor with `Future.get(90, SECONDS)`
(a missing answer is an `AssertionError`, not a hang), the same assertion on the result, and `probe.shutdownNow()` in the `finally`. After the correction the test takes 31 s and is green (runs of 20:48 and 21:00).

Production observation (no code touched): with a frozen database `/actuator/health` can block as long as the freeze lasts, because the datasource has no JDBC socket timeout. A stopped database
(the normal failure) is answered at once, and the compose health check has its own timeout, so the container still turns unhealthy. A `socketTimeout` on the datasource would make the answer prompt in both cases.

## Files

| File (under `modern/services/provider-service/src/test/`) | Blob id |
|---|---|
| `java/gov/medicaid/pem/provider/ObservabilityTest.java` | `cdf0e6e80632f5668dbc94288b5877a1342a8bdc` |
| `java/gov/medicaid/pem/provider/PlatformChecksTest.java` | `adff04be6197e25b699d8b7451f7c816b6fa068a` |
| `java/gov/medicaid/pem/provider/ProviderOutageTest.java` | `1af7fd03c5f493bea173187a1fb77944f21ee2d3` |
| `resources/junit-platform.properties` | `9d87aa62f037594f1504a3edb60e3a0419190af2` |

## Test correction under Q-097 C for Q-142 (2026-11-21)

Why: Q-142 (option A, applied as a default by the lead): the kit's `OutboxWriter` writes every event with a uuid as `correlationId` (as `events.yaml` says): the
current correlation id is used only when it is a uuid, anything else is replaced by a new uuid. The recorded test
`ObservabilityTest#correlationIdFollowsTheEvent` ([MAM-36 AC-4], provider-service) publishes an `ApplicationValidated` whose `correlationId` is the text
`mam36-corr-p1` (safe to log, no uuid), lets the consumer register the provider, and asserts that the `ProviderRegistered` envelope in `outbox_event`
(written by `KitEventOutbox` through the kit's `OutboxWriter`, which has no correlation logic of its own) carries exactly that text. With the fix the envelope carries a new
uuid and the test turns red, although its assertion (the event caused by a consumed event carries that event's correlation id) is right for a valid id.
Found by the search of 2026-11-21; it is the only such hit outside the kit's `OutboxWriterTest` (see the MAM-31 report).

File: `modern/services/provider-service/src/test/java/gov/medicaid/pem/provider/ObservabilityTest.java` (CRLF kept; one line changed, 219 lines before and after;
the change was made on the bytes, and the diff against HEAD shows exactly this one line). Both assertions use the variable, so the assertions are
byte for byte as before and the expected value changes together with the input.

| Line | Before | After |
|---|---|---|
| 187 (input, in `correlationIdFollowsTheEvent`) | `    String correlationId = "mam36-corr-p1";` | `    String correlationId = "6b3a9f10-2c4e-4d7a-8b5c-000000000361";` |
| 210-211 (assertion on the outbox envelope, not edited) | `assertThat(json.readTree(envelopes.get(0)).path("correlationId").asText()).isEqualTo(correlationId);` | same text (the expected value is now the uuid) |
| 212-217 (assertion on the log line, not edited) | log line with the NPI and `correlationId.equals(l.correlationId())` | same text (the log context of the consumer holds the uuid) |

Why this is not a weaker test: the same two assertions on the same two things (the `ProviderRegistered` envelope in the outbox and the registration log line carry the
correlation id of the consumed event), with an id that the contract allows; nothing removed, no assertion loosened. A lower-case canonical uuid is copied
unchanged by the old and by the new writer, so the test passes with and without the fix and still fails if the correlation id of the consumed event is not carried
into the event or the log line. The non-uuid case is asserted the other way round by the kit's `OutboxWriterCorrelationIdTest` and, for forged ids, by
`UnsafeCorrelationIdFromEventsTest` (MAM-17 AC-2: one generated uuid in the event).

Recorded again: `ObservabilityTest.java` sha256 `7071774731d4eb0cf3f90e399e22f9fc90c51be6fe582ff7c0cc6b4e051b0571` ->
`c32274577beeb517ed6bdac6139bb22f4f23acd23b5d9fd18fbe13e7259670a6`, blob id `cdf0e6e80632f5668dbc94288b5877a1342a8bdc` ->
`393ad1c1ed6fd7820621127b4634f95560dc47f2`. `MAM-36.tests.sha256` still lists the same 8 files and one line differs; its sha256 is now
`bf5b549f1b6f199e6fb455ef8bb79877fb0f767d149edf4bbf7922cecf8597d1` (the `testsSha256` in `specs/MAM-36.md` is still `252f6531...`; the lead updates it).
`test_protection.py check MAM-36` prints `OK: 8 test files of MAM-36 unchanged; no unrecorded test files`.
Not run: the corrected test needs Docker (Testcontainers Postgres and Kafka) and this item allowed one Gradle run, the kit test only. The correction was checked by reading
the code path (Kafka record -> `CorrelationIdRecordInterceptor` -> `RegisterProvider` -> `KitEventOutbox` -> `OutboxWriter`), not by a run.
