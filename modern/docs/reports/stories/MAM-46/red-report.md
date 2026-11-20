# MAM-46 red report

- Red set: 8 test files (feature MAM-46 with one scenario per AC, steps, Cucumber suite and Spring config, test base,
  platform checks, ApplicationValidatorTest for the E101-E104 rules and the type mapping, SubmitApplicationTest for
  the use case: what is stored, which event is announced, who lists what).
- First red run: compile errors only, because ApplicationValidator, Validation, ProviderTypeMapping (domain) and
  SubmitApplication, ListApplications, ApplicationStore, EventOutbox and the event data records (application) did
  not exist. Expected reason.
- Corrections made by the test-writer before or during green, each re-recorded in MAM-46.tests.sha256:
  1. SubmitApplicationSteps had two methods named `application` (did not compile); one renamed. Before green.
  2. SubmitApplicationTest added before any production code existed, so that the application layer that PIT
     mutates has a unit test.
  3. During green attempt 1: two steps carried both @Given and @When / @Then with the same expression, which
     Cucumber rejects as duplicate step definitions; the extra annotation was removed. No assertion changed.

## Test change for Q-095 (user decision 2026-11-19)

- **File:** `modern/services/enrollment-service/src/test/java/gov/medicaid/pem/enrollment/application/SubmitApplicationTest.java`,
  test `duplicate()` ([MAM-46 AC-5]).
- **Why:** Q-095 now refuses (403) an individual (IN) application from a PROVIDER under any NPI other than the npi claim
  of their token. The second half of the test submitted IN for NPI `1003000222` as `PROVIDER` (token NPI `1003000111`),
  which is now refused by the new requirement, not by a defect.
- **Change (test data only, no assertion changed):** those two submissions now use a local
  `provider2` (`PROVIDER`, npi claim `1003000222`), matching what the MAM-46 AC-5 scenario already does (its token is the
  application's NPI). The assertions (`E103/npi`, the event types, `retry` VALIDATED) are unchanged.
- **Not changed:** MAM-46 AC-2 (empty and `12345` NPI, IN, PROVIDER token `1003000111`) still expects 422 E101: a missing
  or malformed NPI is not another provider's NPI, so it is left to E101 (see the Q-095 report, question 1).
- **Hashes:** re-recorded with `test_protection.py record MAM-46`.

## Test change of the Lane 3 tests (2026-11-21)

- **File:** `modern/services/enrollment-service/src/test/java/gov/medicaid/pem/enrollment/api/ApplicationLessonsTest.java`, test `unsafeCorrelationIdIsReplaced` ([MAM-46 AC-1]).
- **Why:** the test sent an X-Correlation-Id with a line break and expected 201. That cannot happen over HTTP; with MockMvc the application is stored and then Spring Security's firewall answers 400 with an empty body
  (found by the first whole run of the module). The premise of the test is not a case of the real system.
- **Change (inputs only, no assertion removed or weakened):** the test is parameterized over six unsafe header values that can travel over HTTP (a forged log line after a space, a forged JSON log line, 65 characters,
  a path with a quote, markup, an empty string); every assertion is kept (201, safe echoed id, no log line or event carries the raw value, the event correlationId is a UUID).
  New test `tabInTheCorrelationIdIsAnsweredConsistently`.
- **Hashes:** MAM-46 and MAM-54 recorded again (both contain this file). Details: MAM-56/red-report.md, "Test corrections" item 4.
