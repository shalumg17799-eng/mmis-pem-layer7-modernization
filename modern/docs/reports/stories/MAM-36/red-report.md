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
