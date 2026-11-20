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
