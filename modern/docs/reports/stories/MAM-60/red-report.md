# MAM-60 red report

This file holds only the correction round below. The red run of the first build of the story is in `red-run.txt` of this folder and the story
report is `modern/docs/reports/stories/MAM-60.md`.

## Test correction under Q-097 (code review 2026-11-20, additive)

**Cause.** MUST-FIX 1 of the code review of notification-service (2026-11-20): the APPROVED, DENIED, REJECTED and REVALIDATION_DUE notices have no
D-024 failure handling, so a notice that could not be sent (mail server down, template missing) leaves no FAILED row and
`GET /api/v1/notices` shows nothing for it. MAM-60 AC-5 and its business requirement ("a notice that could not be sent shows as failed")
require the FAILED row. The full description, the interface the tests assume and the red evidence are in
`modern/docs/reports/stories/MAM-59/red-report.md` (the same round, the same classes); only what belongs to MAM-60 is listed here.

**Rule applied.** Q-097 C: additive, no assertion weakened or removed, no recorded MAM-60 assertion changed, production code not touched.

### Changes to recorded tests

| File | Previous blob | New blob | Line endings | Lines (added / removed) |
|---|---|---|---|---|
| `resources/features/MAM-60.feature` | `b9a4bf1adfdb5ca8cfeb6b4a3dfc4d98b9cf9164` | `NEWBLOB_FEATURE60` | LF | 12 / 0 |

(Under `modern/services/notification-service/src/test/`.) One scenario appended after the last one, tag `@AC-5`: "An approval that could not be sent
shows as FAILED to staff and to the provider" (the approval event is consumed while the APPROVED template is missing; an ANALYST lists
`npi=1003000111`, then the PROVIDER lists their own notices; both lists hold the APPROVED notice with channel EMAIL and status FAILED).
The existing AC-5 scenario (a FAILED row inserted by the step) is untouched.

### New test files added to this story's record

| File | MAM-60 part |
|---|---|
| `NoticeSendFailureTest.java` | `failedNoticeIsListedForStaff` x4 and `failedNoticeIsListedForTheProvider` x4 ([MAM-60 AC-5]): the notice of a send that failed because the mail server was down is listed with status FAILED, its template, channel EMAIL, no `sentAt: null`, for an ANALYST (`?npi=`) and for the PROVIDER of that NPI. The other methods of the class belong to MAM-59. |
| `acceptance/NoticeFailureSteps.java` | the steps "the {string} notice template is missing" and "the log holds the warning ..." used by the new scenario |

### Red evidence (scratch copy, behaviour of today; details in the MAM-59 red report)

- Run A (repository): `compileTestJava` fails only on the `SendNotice` constructor in three MAM-59 unit test classes; the MAM-60 tests compile.
- Run B (scratch copy, `SendNotice` with the new constructor and the behaviour of today): the 8 `NoticeSendFailureTest` list tests fail with
  `a send to a mail server that is down must not be thrown (D-024)` (the `MailSendException` is thrown out of `SendNotice.send`, so no FAILED row exists to
  list); the new MAM-60 scenario fails with `Expecting actual not to be empty` (the notice list holds no notice for 1003000111). The 6 older
  MAM-60 scenario groups pass.
- Run C (scratch copy with a reference fix): all of them pass; the whole module 346 tests, 0 failures, 0 skipped.

### Hashes

Re-recorded with `test_protection.py record MAM-60` (the 7 files of the previous record plus `NoticeSendFailureTest.java` and `NoticeFailureSteps.java`).
