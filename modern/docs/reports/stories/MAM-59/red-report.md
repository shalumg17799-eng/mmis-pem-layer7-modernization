# MAM-59 red report

This file holds only the red evidence of the correction round below. The first build of the story is described in
`modern/docs/reports/stories/MAM-59.md`.

## Test correction under Q-097 (code review 2026-11-20, additive)

**Cause.** The code review of notification-service (2026-11-20, about 20:00) returned MUST-FIX 1: `SendNotice` (APPROVED, DENIED,
REJECTED, REVALIDATION_DUE) has no D-024 failure handling. MAM-59 says the failed-send and missing-template handling "applies to
these notice types" (out-of-scope note), and MAM-60 AC-5 requires that a notice that could not be sent shows as FAILED. Two paths are
wrong today: (1) a missing template only logs `no notice template ...` and stores nothing; (2) a mail failure is not caught, so the
listener transaction rolls back, the kit retries twice and parks the event on `<topic>.DLT`, and no FAILED notice and no
`NOTICE <code> FOR NPI <npi> STATUS FAILED ... cause` warning exist. Two recorded tests asserted the wrong behaviour
(`SendNoticeResultTest.missingTemplateIsReturnedAndLogged`, `SendNoticeTest.missingTemplate`).

**Rule applied.** Q-097 C (standing rule): the assertion follows the acceptance criterion, no assertion is weakened or removed, new
test classes are preferred, every change is documented here, hashes are re-recorded. Production code (`src/main/**`) was not touched.
The failure words are the ones of `SendSanctionNotice` (`SendSanctionNoticeFailureTest`), so the two notice families cannot drift apart.

### 1. Changes to recorded tests (previous blob is the state before this round; line endings kept)

| File | Previous blob | New blob | Line endings | Lines (added / removed) |
|---|---|---|---|---|
| `application/SendNoticeResultTest.java` | `bfc3ff7868e4784b65fbcff3e51ee8f33b8f73fa` | `661dc4923686bd6b35b9b82242b317a7dc88d917` | CRLF | 14 / 4 |
| `application/SendNoticeTest.java` | `05280ca97cf26cd05c6b5fd14c68e98694cb3948` | `e9871b36d255a738fe4e509511e8121674285fb1` | CRLF | 13 / 2 |
| `resources/features/MAM-59.feature` | `bd057a54ebe119586190a2610f9e551edf228e92` | `db307d11bc166399a13fd37e66abdb11f37581f8` | LF | 40 / 0 |

(All under `modern/services/notification-service/src/test/`.)

`SendNoticeResultTest` and `SendNoticeTest`: the fake ports get a fifth collaborator (`FailedNotices`, a `Failed(notice, cause)` list) because
`SendNotice` now needs it (constructor of `SendSanctionNotice`: templates, mailer, store, failedNotices, outbox, clock). Nothing else in the
setup changed.

```diff
 SendNoticeResultTest.missingTemplateIsReturnedAndLogged   (DisplayName extended: FAILED notice, cause, D-024)
     assertThat(result).isEmpty();                                   // kept
-    "no notice template EXTRACT_READY: nothing sent for NPI 1003000111 (event <id>)"
+    "NOTICE EXTRACT_READY FOR NPI 1003000111 STATUS FAILED (event <id>), cause: NOTICE TEMPLATE NOT FOUND EXTRACT_READY"
+    assertThat(failed).hasSize(1);  templateCode, npi, eventId of the FAILED notice
+    assertThat(failed.get(0).cause()).isEqualTo("NOTICE TEMPLATE NOT FOUND EXTRACT_READY");
     assertThat(mails).isEmpty(); assertThat(stored).isEmpty(); assertThat(sent).isEmpty();   // kept

 SendNoticeTest.missingTemplate                            (DisplayName extended)
     mails, stored, sent stay empty                                  // kept
+    one FAILED notice for REVALIDATION_DUE with the event id and cause "NOTICE TEMPLATE NOT FOUND REVALIDATION_DUE"
```

`MAM-59.feature`: four scenarios appended (tags `@AC-1`, `@AC-2`, `@AC-4`, `@AC-6`), none removed or edited.

### 2. New test files

| File | Level | Executions |
|---|---|---|
| `application/SendNoticeFailureTest.java` | unit, fake ports, fixed Clock 2026-11-20 | 46 (13 methods, four notice codes each) |
| `NoticeSendFailureTest.java` | integration, real Postgres, Mailpit, Kafka; use case and listener bean called directly with the mail sender on a closed port; MockMvc for the list | 30 (9 methods) |
| `NoticeSendFailureEventsTest.java` | integration through the real Kafka: Mailpit of the test JVM paused, template renamed in the shared database; DLT check | 16 (4 methods) |
| `acceptance/NoticeFailureSteps.java` | Cucumber steps (missing template, warning line) | used by 5 scenarios |

Why the Kafka tests do not point a context's mail sender at a closed port: an event can be consumed by the listener of another cached
Spring context of the test JVM (O-4 of the story report). The two faults used there (the Mailpit container paused with
`docker pause`, which only touches the throwaway container that this test run started, and a template renamed in the shared database)
are seen by every context. The tests resume the Mailpit and put the template name back in `@AfterEach`; every wait has a deadline and the
class has `@Timeout(4 minutes)`.

### 3. AC to test mapping

| AC | Tests |
|---|---|
| AC-1 approval | `SendNoticeFailureTest` (AC-1 rows, `causeWithoutMessage`, `nextNoticeIsProcessed`); `NoticeSendFailureTest` (APPROVED rows, `silentMailServer`, `listenerGoesOnAfterAFailure`); `NoticeSendFailureEventsTest` (APPROVED); Cucumber "An approval whose notice template is missing is recorded as FAILED and is not lost" |
| AC-2 denial | the DENIED rows of the three classes; Cucumber "A denial whose notice template is missing ..." |
| AC-4 rejection | the REJECTED rows; Cucumber "A rejection whose notice template is missing ..." |
| AC-6 revalidation due | the REVALIDATION_DUE rows; `SendNoticeResultTest.missingTemplateIsReturnedAndLogged`; `SendNoticeTest.missingTemplate`; Cucumber "A revalidation notice whose template is missing ..." |
| MAM-60 AC-5 (listed as FAILED) | `NoticeSendFailureTest.failedNoticeIsListedForStaff` x4, `failedNoticeIsListedForTheProvider` x4; Cucumber (MAM-60.feature) "An approval that could not be sent shows as FAILED to staff and to the provider" |

What the tests demand (D-024, as MAM-58 AC-6 and AC-7): a mail failure or a missing template gives one FAILED notice row (template, channel EMAIL,
recipient `<npi>@providers.pem.local`, event id, no `sent_at`), one WARN line
`NOTICE <code> FOR NPI <npi> STATUS FAILED (event <id>), cause: <cause>` (cause = exception message, the exception text when it has none,
`NOTICE TEMPLATE NOT FOUND <code>` for a missing template), no email, no `NoticeSent`, nothing thrown, so the event is in `processed_event`
and not on `<topic>.DLT`. A failure of the notice store or the outbox after the email was sent is still thrown (lesson c, one transaction).

### 4. Interface the tests assume (for the java-developer)

- `new SendNotice(templates, mailer, store, failedNotices, outbox, clock)`, the order of `SendSanctionNotice`.
- `Optional<Notice> send(String npi, String templateCode, UUID eventId)` keeps its signature: the sent notice, empty when nothing was sent (a
  FAILED notice is not returned). A shared deliver method for both use cases is the reviewer's recommendation; the tests do not prescribe it.
- No contract change: `Notice.status` already allows `FAILED`, `NoticeSent` is unchanged. The `FailedNotices` adapter does not store the cause
  (no column); the cause is in the WARN line, as for the sanction notices.

### 5. Red evidence

**Run A, in the repository (2026-11-21 03:35, `spotlessApply compileTestJava`).** `compileTestJava` FAILED with 6 errors, all in the three classes that
construct `SendNotice` with the new constructor: `constructor SendNotice in class SendNotice cannot be applied to given types; required:
Templates,Mailer,NoticeStore,EventOutbox,Clock` (3 errors) and the 3 follow-up lambda errors of the same call. No compile error in
`NoticeSendFailureTest`, `NoticeSendFailureEventsTest`, `NoticeFailureSteps`. Expected reason: the constructor does not exist yet.

**Run B, in a scratch copy of `modern/` (session scratchpad, not the repository).** To see the assertion-level failures, `SendNotice` there got the
six-argument constructor and its behaviour was left exactly as it is today. Classes `SendNoticeFailureTest`, `SendNoticeResultTest`,
`SendNoticeTest`, `NoticeSendFailureTest`, `NoticeSendFailureEventsTest`, `CucumberSuiteTest`: **139 tests, 87 failed, 0 skipped** (10 min 41 s).

| Group | Failed / executed | Failure reason |
|---|---|---|
| `SendNoticeFailureTest`, mail failure (fields, cause, WARN words, nothing stored, lookup once, not thrown, next notice) | 26 | the `MailSendException` / `IllegalStateException` of the mail port is thrown out of `send` (no catch) |
| `SendNoticeFailureTest`, missing template (FAILED notice, WARN words) | 8 | `Expected size: 1 but was: 0` (no FAILED notice); WARN is `no notice template <code>: nothing sent for NPI ...` |
| `SendNoticeFailureTest` total | 34 / 46 | the other 12 are the guard tests below |
| `SendNoticeResultTest.missingTemplateIsReturnedAndLogged`, `SendNoticeTest.missingTemplate` | 2 / 9 | old WARN words; no FAILED notice |
| `NoticeSendFailureTest` (direct call, listener bean, list for staff and provider, silent server) | 30 / 30 | `a send to a mail server that is down must not be thrown (D-024)`, `the listener must not throw when the mail server is down`, `MailSendException ... Exception reading response` |
| `NoticeSendFailureEventsTest`, Mailpit paused (2 x 4) | 8 / 8 | `event <id> to be handled did not happen within 45 s; it was retried and parked on pem.provider.events.DLT (enrollment, revalidation), so the listener threw instead of recording a FAILED notice` |
| `NoticeSendFailureEventsTest`, template renamed (2 x 4) | 8 / 8 | no FAILED row; WARN is the old `no notice template <code>: nothing sent ...` |
| Cucumber, the 5 new scenarios | 5 / 38 | `expected: 1 but was: 0` (no FAILED row), `Expecting actual not to be empty` (nothing in the list); the 33 older scenarios pass |

Guard tests that pass today and must keep passing (they stop the fix from over-catching): `SendNoticeFailureTest.storeFailureIsThrown` x4,
`outboxFailureIsThrown` x4, `sentNoticeIsNotFailed` x4.

**Run C, scratch copy with a reference fix (proof that the tests can pass; not a deliverable).** `SendNotice` with `FailedNotices`, a
catch of `RuntimeException` around `mailer.send` and a `failed(...)` path as in `SendSanctionNotice`, the full module:
`test pitest jacocoTestCoverageVerification` **BUILD SUCCESSFUL, 346 tests, 0 failures, 0 skipped** (the 33 older scenarios and all older
classes included); PIT on domain and application 38 mutants, 36 killed (95 %; the 2 survivors are `return failed(...)` replaced by
`Optional.empty()`, equivalent in that implementation); JaCoCo gate passed. `NoticeSendFailureEventsTest` ran its 16 tests in 51 s.

### 6. Not tested

- The cause is not stored in `notice` (no column in V1); only the WARN line carries it, as for the sanction notices.
- A mail server that is down for a longer time: no resend, no rule or decision exists (MAM-60 out of scope).

### 7. Hashes

Re-recorded with `test_protection.py record MAM-59` (the 16 files of the previous record plus the 4 new files, 20 in all); `check MAM-59` prints
`OK: 20 test files of MAM-59 unchanged; no unrecorded test files`. `check MAM-60` and `check MAM-58` print OK as well (MAM-58 files were not changed).
