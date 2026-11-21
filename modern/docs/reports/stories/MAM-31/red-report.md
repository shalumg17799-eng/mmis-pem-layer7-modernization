# MAM-31 red report

The tests recorded under MAM-31 before this section are listed in `MAM-31.tests.sha256` (9 files: `HelpersTest`,
`PemKafkaAutoConfigurationTest`, `PemKitAutoConfigurationTest`, `EventEnvelopeTest`, `OutboxRelayTest`, `OutboxWriterTest`,
`ProcessedEventsTest` of `libs/service-kit`, and `EventProbe`, `PemContainers` of `libs/test-support`); the key switch is in
`key-switch.md`. This file did not exist before; it starts with the section below. The `Outcome` line of `MAM-31.md` is not
changed.

## Test addition for Q-142 (2026-11-21, additive)

### What and why

Defect (Q-142, `modern/docs/analysis/questions.md`): `events.yaml` says the envelope member `correlationId` is a uuid, but the
request filter keeps any SAFE id (letters, digits, `. _ : -`, 1 to 64 characters) and `OutboxWriter.enqueue` copies
`CorrelationId.currentOrNew()` into every event. A caller that sends `X-Correlation-Id: trace-42` gets events whose
`correlationId` is `trace-42`; the same happens when a consumed event with such an id is handled and a new event is written
(`CorrelationIdRecordInterceptor` puts the id of the consumed envelope into the MDC). Decision: Q-142 option A, applied as a
default: fix it once in the kit; an event carries the current correlation id only when that id is a uuid, otherwise the event
gets a new uuid. `enrollment-service` already does this in its own `KitEventOutbox` (left as it is).

**Additive only (Q-097 C).** No recorded file was edited: the 9 recorded files are byte-identical (sha256 equal before and
after, `test_protection.py check MAM-31` prints OK). **No production file was touched.** One new test class:

| File (under `modern/libs/service-kit/src/test/java/gov/medicaid/pem/kit/events/`) | Blob id (`git hash-object`) | sha256 | Tests |
|---|---|---|---|
| `OutboxWriterCorrelationIdTest.java` (unit, JUnit 5 + Mockito `JdbcTemplate`, fixed `Clock` 2026-01-15, the envelope is read back from the stored payload as in `OutboxWriterTest`) | `23e16310578dd45afd609623e0b01644758bbb9d` | `9e8d87cd41058e654cf1a65497b2629127b7cc45bc1c889fd57e37cbadf851d2` | 71 invocations in 20 test methods |

`MAM-31.tests.sha256` now lists 10 files (the 9 recorded files unchanged + the new one); its sha256 is
`0d960dd1241473aaa63b4b539e6ddec73743d47129abb7a353de584986d03cda`. The `testsSha256` in `specs/MAM-31.md` is still the old
`5af3536d...`; the lead updates it (as for MAM-26).

### Which AC the tests belong to

The story is MAM-31 (the event backbone; its report lists `OutboxWriterTest` under AC-2 and AC-5). Two ACs fit, so each test is
tagged with the one it is about plus `Q-142` in the text (the traceability tag stays the plain `[MAM-31 AC-n]`):

- **AC-1** ("the message is JSON with id (uuid), ... correlationId (uuid) ... and a contract test fails when a message does not match
  events.yaml"): a stored `correlationId` that is not a uuid, and the stored payload against the Envelope of `events.yaml`.
- **AC-5** ("the envelope correlationId equals that id ... the next event carries the same id"): holds for a uuid; the tests for a
  uuid that must travel unchanged, for the id in the log context, and for the "no current id" case carry AC-5.

AC-5 as written says the envelope id *equals* the request's id; for an id that is no uuid that sentence contradicts AC-1 and
events.yaml. Q-142 resolves it in favour of the contract (AC-5 holds for every uuid). If the lead prefers to reword AC-5, that is a
story change, not a test change.

### The decision the tests pin (stated, so the reviewer can overrule it)

1. **Current id is a uuid** (`java.util.UUID.fromString` accepts it: the same test as `KitEventOutbox.uuidOf` of
   enrollment-service, so every service behaves the same): the stored `correlationId` is its canonical lower-case text
   (`UUID.toString()`). A lower-case canonical uuid is stored unchanged (including the nil uuid and a version 7 uuid);
   `5A1E7B20-3C4D-4E5F-9A6B-00000000ABCD` is stored as `5a1e7b20-3c4d-4e5f-9a6b-00000000abcd`; a form that `UUID.fromString` accepts
   with short groups is stored as the canonical uuid it stands for (`1-1-1-1-1` is stored as `00000001-0001-0001-0001-000000000001`,
   `A-B-C-D-E` as `0000000a-000b-000c-000d-00000000000e`, `5a1e7b20-3c4d-4e5f-9a6b-45` as `5a1e7b20-3c4d-4e5f-9a6b-000000000045`). The contract
   says uuid, and the stored value must be a canonical lower-case uuid; the alternative "only a canonical 8-4-4-4-12 text counts as a
   uuid, everything else gets a new uuid" would also satisfy the contract and differs only in these 3 rows (`aNonCanonicalUuidIsStoredInCanonicalForm`;
   proven below: a strict-regex reference fix fails exactly those 3). Chosen for parity with the enrollment wrapper.
2. **Current id is anything else** (safe ids such as `trace-42`, `svc:req:42`, a 64-character id, a uuid without dashes, a uuid with a
   suffix, a uuid shape with a non-hex digit; and values the filter never lets in but any code can put in the MDC: empty, blank, a line
   break, a uuid followed by a line break or a space, 65 characters): the event carries a **new canonical lower-case uuid**, never
   that text, and two events of two such requests get two different uuids.
3. **No current id:** a new canonical uuid, different for every event.
4. **Nothing else changes:** the id in the log context (MDC) is exactly what it was before `enqueue` (also when the insert fails and
   `enqueue` throws), for a consumed event the interceptor's id (`trace-42`) stays in the MDC while the handler runs;
   `CorrelationId.currentOrNew()` keeps returning the MDC value as it is (the recorded `ObservabilityKitTest` MAM-36 AC-4 asserts
   `currentOrNew()` returns `mam36-corr-1`), `CorrelationId.sanitize`, the filter, the interceptor and `enrollment-service`'s
   `KitEventOutbox` are not part of this item. Not pinned (a design choice for the developer): whether a new uuid is also put into the
   MDC for the rest of the request (the tests forbid it: the MDC must be unchanged), and whether the new uuid may equal the event's own id.

### Tests, AC, and how each fails today

Unit level (no integration or contract-test level is needed: the writer is a pure class over `JdbcTemplate`; the contract is read from
`modern/contracts/asyncapi/events.yaml` by a small checker inside the test class, see the last row). Test method (invocations) | AC | today:

| Test method (invocations) | AC | Today (before the fix) | How it can fail / what it protects |
|---|---|---|---|
| `aUuidTravelsUnchanged` (4: v4, nil, v7, every hex letter) | AC-5 | **green at once**: the kit copies the id, a uuid is already stored as is | fails for a fix that always generates a new uuid (mutant `alwaysNew`: 4 red, plus the 2 consumer/`with` uuid tests) |
| `aUuidIsStoredInLowerCase` (2: upper, mixed case) | AC-1 | **red**: `expected "5a1e7b20-...-00000000abcd" but was "5A1E7B20-...-00000000ABCD"` (the id is copied as typed) | fails for a fix that checks the uuid but stores the original text (mutant `keepOriginalText`: 2 red) |
| `aNonCanonicalUuidIsStoredInCanonicalForm` (3: `1-1-1-1-1`, `A-B-C-D-E`, short last group) | AC-1 | **red**: `expected "00000001-0001-0001-0001-000000000001" but was "1-1-1-1-1"` | fails for lower-casing without padding (mutant `lowerCaseOnlyNoPadding`) and for the strict-regex alternative (see decision 1) |
| `anIdThatIsNoUuidIsReplacedByANewUuid` (7 safe ids) | AC-1 | **red**: `Expecting actual "trace-42" to match pattern [0-9a-f]{8}-...` | the envelope carries a new canonical uuid, not the text |
| `anUnsafeIdIsReplacedByANewUuid` (6: empty, blank, line break, uuid+LF, uuid+space, 65 characters) | AC-1 | **red**: the raw value is stored (`""`, `" "`, `"bad\nvalue"`, ...) | a fix that trims or keeps a near-uuid would still pass only if it stores the canonical uuid; a raw value fails |
| `twoRequestsWithTheSameIdGetDifferentUuids` (4: trace-42, colons, dots/underscores, 64 characters) | AC-1 | **red**: `Found duplicate(s): ["trace-42"] in: ["trace-42", "trace-42"]` | fails for a fix that derives the uuid from the text (mutant `deterministicForNonUuid`: 4 red) |
| `withoutACurrentIdTheEventGetsAUuid` (1) | AC-5 | **green at once**: `currentOrNew()` already generates a uuid when the MDC is empty | fails for a fix that stores `""` or `null` when no id is current (mutants `blankWhenNoId`, `nullWhenNoId`) |
| `withoutACurrentIdTwoEventsGetDifferentUuids` (1) | AC-5 | **green at once** (same reason) | fails for a constant fallback such as the nil uuid (mutant `nilWhenNoId`) and for a fix that writes the new uuid into the MDC (`overwriteMdc`) |
| `theLogContextIsNotChanged` (7: uuid, upper-case, non-canonical, trace-42, colons, 64 characters, no id) | AC-5 | **green at once**: the kit does not touch the MDC | fails for a fix that writes the replacement or the canonical form into the MDC (mutant `overwriteMdc`: 6 red; the "no id" row sees the MDC filled) |
| `theLogContextIsNotChangedWhenTheInsertFails` (1) | AC-5 | **green at once** | fails for a fix that swaps the MDC for the insert and restores it only after success, without `finally` (mutant `mdcNotRestoredOnFailure`) |
| `aConsumedEventWithAUuidGivesTheNextEventTheSameUuid` (1: through the real `CorrelationIdRecordInterceptor.intercept`) | AC-5 | **green at once** | fails for `alwaysNew` |
| `aConsumedEventWithANonUuidIdGivesTheNextEventANewUuid` (1: consumed `correlationId` `trace-42`) | AC-1 | **red**: `Expecting actual "trace-42" to match pattern ...` | the "incoming event" path of Q-142 |
| `aConsumedEventKeepsItsOwnIdInTheLogContext` (1) | AC-5 | **green at once** | fails for `overwriteMdc` |
| `eventWorkWithANonUuidIdWritesANewUuid` (1: `CorrelationId.with("trace-42", ...)`) | AC-1 | **red**: `Expecting actual "trace-42" to match pattern ...` | the listener path that the services use (`CorrelationId.with`) |
| `eventWorkWithAUuidWritesThatUuid` (1) | AC-5 | **green at once** | fails for `alwaysNew` |
| `theStoredEnvelopeConformsToTheContract` (7: uuid, upper-case, non-canonical, trace-42, colons, 64 characters, no id) | AC-1 | **4 red** (non-canonical, trace-42, colons, 64 characters): `Expecting empty but was: ["$.correlationId = trace-42 is not a valid uuid"]`; 3 green at once (uuid, upper-case: the format is case-insensitive, no id) | the stored payload is a valid Envelope (all 7 members, none outside the contract, `id` and `correlationId` uuid, `time` date-time, `subject` NPI or uuid, `data` object); fails for `blankWhenNoId` / `nullWhenNoId` and `keepOriginalText` |
| `theContractCheckAcceptsAValidEnvelope` (1) and `theContractCheckReportsABrokenEnvelope` (8: non-uuid and non-canonical correlationId, id, time, subject, missing member, extra member, data not an object) | AC-1 | **green at once** (they test the checker inside the test class, so that "conforms" cannot pass vacuously) | fail when the checker becomes lenient (for example `UUID.fromString` instead of the 8-4-4-4-12 form would accept `1-1-1-1-1`) |
| `theRequestFilterKeepsTheSampleIds` (7) and `theSampleIdsAreNoUuid` (7) | AC-1 | **green at once**: the premise of the defect, checked for every sample: `CorrelationId.sanitize` keeps the id, and `UUID.fromString` rejects it | fail when a sample is edited into something the filter rejects or the JDK parses (the samples must stay "safe but no uuid") |

Total: **71 invocations, 28 red, 43 green at once** (the 43 are regression guards for the parts of the decision that already hold, and
for the checker). Every red test fails with an assertion failure about the stored `correlationId`, not with a missing class or a broken
set-up.

### Run (red)

`./gradlew --no-daemon --continue -I <init script: Spotless on kit src/test only, class-path printer> :libs:service-kit:spotlessApply
:libs:service-kit:printKitTestClasspath :libs:service-kit:test --tests gov.medicaid.pem.kit.events.OutboxWriterCorrelationIdTest
:testSourceCheck`, 2026-11-21 07:57 to 07:58 (waited 120 s for the shared lock and memory, 1940 MB free at start; BUILD FAILED in 59 s as
intended): `71 tests completed, 28 failed`, 0 skipped; `testSourceCheck` passed (401 test sources in 13 modules: no skip mechanism).
Spotless formatted the new file only (the 9 recorded files were not touched: check OK). The failures listed by display name are in
`libs/service-kit/build/test-results/test/TEST-gov.medicaid.pem.kit.events.OutboxWriterCorrelationIdTest.xml`.

Second run on the final file (it was converted to CRLF like the other kit tests after Spotless, then recorded): `./gradlew --no-daemon
--continue -I <init script> :libs:service-kit:spotlessCheck :libs:service-kit:test --tests ...OutboxWriterCorrelationIdTest --rerun`,
2026-11-21 08:16 (waited 239 s for the lock, 2796 MB free): `spotlessJavaCheck` (kit `src/test`) passed, again 71 tests, **28 failed**,
0 skipped (BUILD FAILED in 41 s, as intended).

### Proof that the tests can be satisfied and can fail (throw-away copies, scratchpad only, no Gradle, repository untouched)

The kit's compiled classes were put on a plain JVM class path with a patched `OutboxWriter` in front, and the new class was run with the
JUnit launcher (cwd `modern/libs/service-kit`, so the contract file is found as in Gradle):

| Variant of `OutboxWriter` | Result of the 71 tests |
|---|---|
| unchanged (today) | 28 failed, 43 passed (equals the Gradle run) |
| **reference fix** `UUID.fromString(id).toString()`, else `UUID.randomUUID()` (what `KitEventOutbox.uuidOf` does) | **71 passed** (the tests are satisfiable) |
| strict 8-4-4-4-12 regex, lower-cased, else new uuid | 68 passed, 3 failed: exactly `aNonCanonicalUuidIsStoredInCanonicalForm` (decision 1) |
| mutant: uuid check but original text stored | 6 failed (3 non-canonical, 2 case, 1 contract) |
| mutant: lower-case only, no padding | 4 failed |
| mutant: always a new uuid | 11 failed (4 unchanged-uuid, 2 case, 3 non-canonical, 2 uuid-path) |
| mutant: uuid derived from the text of a non-uuid id | 4 failed (`twoRequests...`) |
| mutant: nil uuid when no id | 1 failed |
| mutant: `""` / `null` when no id | 3 failed each |
| mutant: replacement written into the MDC | 9 failed |
| mutant: MDC swapped for the insert, not restored when the insert fails | 1 failed (`theLogContextIsNotChangedWhenTheInsertFails`) |

### Conflict with a recorded test (not edited: reported)

With the reference fix, one **recorded** test fails: `OutboxWriterTest#writesTheEnvelopeToTheOutboxTable`
(`[MAM-31 AC-2] an event is written to outbox_event in the caller's transaction with the full envelope`): it puts the non-uuid id
`corr-42` into the MDC and asserts `correlationId` equals `corr-42` (`expected: "corr-42" but was: "c13d27aa-..."`). That assertion
is exactly the defect Q-142 removes, so no fix of Q-142 can keep it green. The task said to add a new class and not to edit a
recorded test, so it was **not changed**. The correction under Q-097 C (a test superseded by a later decision, nothing weakened) is one
line: use a uuid (for example `5a1e7b20-3c4d-4e5f-9a6b-000000000456`) instead of `corr-42` in the MDC and in the final assertion; the other
11 tests of the recorded classes `OutboxWriterTest`, `EventEnvelopeTest`, `HelpersTest` and `CorrelationIdFilterTest` pass with the
reference fix. It must be done (and `MAM-31.tests.sha256` re-recorded) before the developer's `:libs:service-kit:check`, otherwise
that check cannot be green. `OutboxWriterTest#generatesACorrelationIdWhenNoneIsActive` (AC-5) still holds.

### For the developer (constraints from the existing tests)

- Fix `OutboxWriter.enqueue` (or a helper it calls). Do **not** change `CorrelationId.currentOrNew()` or `sanitize`: the recorded
  `ObservabilityKitTest` (MAM-36 AC-4) asserts that `currentOrNew()` returns the MDC id as it is.
- Do not put the replacement into the MDC (the new tests assert the MDC is unchanged, also when the insert throws).
- Services: provider, sanctions and screening wrap the kit writer in a `KitEventOutbox` without any correlation logic; risk,
  revalidation, notification and tmsis call `OutboxWriter` from their `*Adapters` classes; enrollment's wrapper already makes the id a
  uuid and becomes redundant but harmless after the kit fix (left as it is by decision). The developer's regression of the eight
  services must show that no recorded service test expects a non-uuid id in an event (a read-only grep of 2026-11-21, not exhaustive:
  the service tests that compare an event's `correlationId` with an id use `UUID.randomUUID()`, a uuid constant, or the id the filter
  generated).

### Not verified

- The Linux run of the new class and the GitHub pipeline (the class is written without Windows paths or line-ending assumptions: the
  contract file is found by walking up from the working directory and read as UTF-8; Spotless runs with the kit's `PRESERVE` line-ending
  policy and passed on the CRLF file here).
- The slow kit classes (`PemKitAutoConfigurationTest`, `OutboxRelayTest`, `ObservabilityKitTest`, `ProcessedEventsTest`, which need a
  Spring context or timeouts) and all eight service modules were not run against the reference fix: the developer runs the regression.
- `MAM-29`'s `test_protection.py check` currently reports CHANGED for two api-gateway test files; that is the api-gateway lane's work in
  progress, not this item (the kit files of MAM-26, MAM-34 and MAM-36 check OK).

## Test correction under Q-097 C for Q-142 (2026-11-21)

Why: the section "Conflict with a recorded test" above reported that `OutboxWriterTest#writesTheEnvelopeToTheOutboxTable` ([MAM-31 AC-2]) puts the
non-uuid text `corr-42` into the log context and asserts that the stored envelope carries it. Q-142 (option A: an event carries the current
correlation id only when it is a uuid, otherwise a new uuid) makes that input produce a new uuid, so the recorded test turns red although the
assertion (the envelope carries the current correlation id) is right for a valid id. The lead applied Q-142 A as a default, so the test is corrected
under Q-097 C (a recorded test may be corrected when its assertion follows the criterion; nothing weakened, nothing removed).

File: `modern/libs/service-kit/src/test/java/gov/medicaid/pem/kit/events/OutboxWriterTest.java` (CRLF kept; two lines changed, 80 lines before and after; the
change was made on the bytes, and the diff against HEAD shows exactly these two lines).

| Line | Before | After |
|---|---|---|
| 41 (input) | `    MDC.put(CorrelationId.MDC_KEY, "corr-42");` | `    MDC.put(CorrelationId.MDC_KEY, "5a1e7b20-3c4d-4e5f-9a6b-000000000456");` |
| 60 (expected value of the assertion) | `    assertThat(env.get("correlationId").asText()).isEqualTo("corr-42");` | `    assertThat(env.get("correlationId").asText()).isEqualTo("5a1e7b20-3c4d-4e5f-9a6b-000000000456");` |

Why this is not a weaker test: it is the same assertion (`env.get("correlationId")` equals the current correlation id, exact `isEqualTo` on the text), the same
test, the same mock verification of the insert and the same six other envelope assertions; only the input changed, to an id that the contract
(`events.yaml`: `correlationId` is a uuid) allows. The id is a lower-case canonical uuid, so it is stored unchanged both by the old code (copies the id) and
by the fix (`UUID.fromString(id).toString()`): the corrected test passes with and without the fix and still fails for a writer that drops or replaces a valid
current id. Line 60 is exactly 100 characters (the Google Java Format limit), so Spotless does not re-wrap it. The case that this test used to cover by
accident, a current id that is no uuid, is now asserted the other way round (a new canonical uuid, never the text) by
`OutboxWriterCorrelationIdTest` (`anIdThatIsNoUuidIsReplacedByANewUuid`, 7 safe ids including `trace-42`). `OutboxWriterTest#generatesACorrelationIdWhenNoneIsActive`
(AC-5) is unchanged.

Recorded again: `OutboxWriterTest.java` sha256 `9ca05edecbcd0300ff442a306d4721e890e006bb02ac3e97e4d9b1979ebc3033` ->
`2c7e89d18cf7cb45325786c6f829a991f3c42aa89080c108679602490226802f`, blob id `74542f1d73f37c69283b86eec6da2c3348528949` ->
`512fff9a51efd6ac846ff2a89fe7b24ce72c2c63`. `MAM-31.tests.sha256` still lists the same 10 files and one line differs; its sha256 is now
`bf0bd0c7b7962d44652dfd49fdbe9567dfa0c59f11cd7ac7b69d4c5f49b2ea24` (the `testsSha256` in `specs/MAM-31.md` is still the old `5af3536d...`; the lead updates it).
`test_protection.py check MAM-31` prints `OK: 10 test files of MAM-31 unchanged; no unrecorded test files`.

Search for the same conflict elsewhere (read-only grep and reading of every test of the kit, `test-support` and the ten services, 2026-11-21): exactly one other
recorded test asserts a non-uuid correlation id on an event written through the kit's `OutboxWriter`:
`provider-service` `ObservabilityTest#correlationIdFollowsTheEvent` ([MAM-36 AC-4]). It is corrected the same way and described in `MAM-36/red-report.md`
(section "Test correction under Q-097 C for Q-142 (2026-11-21)").

Run of the corrected kit test (2026-11-21, one Gradle run under the shared lock, waited 540 s for it; 1821 MB free): `./gradlew --no-daemon
:libs:service-kit:test --tests "gov.medicaid.pem.kit.events.OutboxWriterTest"` from `modern/`, 08:35:49 to 08:36:43: **BUILD SUCCESSFUL in 50 s**,
`OutboxWriterTest`: 2 tests, 0 failures, 0 errors, 0 skipped (`[MAM-31 AC-2]` and `[MAM-31 AC-5]`; `build/test-results/test/TEST-gov.medicaid.pem.kit.events.OutboxWriterTest.xml`).
`compileTestJava` was UP-TO-DATE (Gradle compares file contents, so the compiled class is the corrected file). `OutboxWriter` at that moment already held the
developer's Q-142 fix (`eventCorrelationId()` copies `UUID.fromString(currentOrNew()).toString()`, otherwise a new uuid); a uuid is copied in both versions, so the
result does not depend on the fix. No other Gradle task was run by this item.
