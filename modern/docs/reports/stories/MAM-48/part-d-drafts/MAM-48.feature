@MAM-48
Feature: Revalidation cases: list them and complete one
  Analysts see the cases and can close one with a screening result; a REVALIDATION ScreeningCompleted closes the open
  case by itself; the next due date comes only from provider-service through ProviderStatusChanged (D-003, D-029).
  The cases of these scenarios are written straight into revalidation_db, so that they keep the status given.

  Background:
    Given revalidation-service has no tracked providers, cases or sweep runs

  @AC-1
  Scenario: Analysts list revalidation cases
    Given revalidation_db holds these cases:
      | npi        | dueDate    | status       |
      | 1003000222 | 2026-02-28 | DUE          |
      | 1003000333 | 2026-03-31 | IN_SCREENING |
      | 1003000444 | 2026-01-31 | COMPLETED    |
      | 1003000555 | 2026-01-15 | CANCELLED    |
    When "ANALYST" lists the revalidation cases with "status=DUE&page=0&size=20"
    Then the case list answers 200 with the cases of "1003000222", page 0 and size 20
    When "SUPERVISOR" lists the revalidation cases with "npi=1003000222"
    Then the case list answers 200 with the cases of "1003000222", page 0 and size 20
    When "ANALYST" lists the revalidation cases with "status=OPEN"
    Then the case list answers 400 problem+json

  @AC-2
  Scenario Outline: An analyst or the system completes a case with a screening result
    Given an open revalidation case for "1003000222" due "2026-02-28"
    When "<role>" completes the case of "1003000222" with a new screening id and result "PASS"
    Then the completion answers 200 with the case COMPLETED and closedAt set
    And "1003000222" has no case with status "DUE"
    And "1003000222" has no case with status "IN_SCREENING"

    Examples:
      | role    |
      | ANALYST |
      | SYSTEM  |

  @AC-3
  Scenario: Unknown and closed cases are refused
    Given an open revalidation case for "1003000222" due "2026-02-28"
    And revalidation_db holds these cases:
      | npi        | dueDate    | status    |
      | 1003000555 | 2026-01-15 | CANCELLED |
    When "ANALYST" completes an unknown case with result "PASS"
    Then the completion answers 404 problem+json
    When "ANALYST" completes the case of "1003000222" with a new screening id and result "PASS"
    And "ANALYST" completes the case of "1003000222" with a new screening id and result "FAIL"
    Then the completion answers 409 problem+json
    And the case of "1003000222" is still COMPLETED with its first closedAt
    When "ANALYST" completes the case of "1003000555" with a new screening id and result "PASS"
    Then the completion answers 409 problem+json
    And the case of "1003000555" still has status "CANCELLED"

  @AC-4
  Scenario: A passed re-screening completes the case by itself
    Given an open revalidation case for "1003000222" due "2026-02-28"
    When revalidation-service consumes ScreeningCompleted for "1003000222" with reason "REVALIDATION" and result "PASS"
    Then the case of "1003000222" is COMPLETED with closedAt set

  @AC-5
  Scenario: A failed re-screening closes the case but does not move the due date
    Given an open revalidation case for "1003000222" due "2026-02-28"
    When revalidation-service consumes ScreeningCompleted for "1003000222" with reason "REVALIDATION" and result "FAIL"
    Then the case of "1003000222" is COMPLETED with closedAt set
    And the tracked due date of "1003000222" is still "2026-02-28"
    And revalidation-service has written no event for "1003000222"

  @AC-6
  Scenario: Only revalidation screenings close cases
    Given an open revalidation case for "1003000222" due "2026-02-28"
    When revalidation-service consumes ScreeningCompleted for "1003000222" with reason "INITIAL" and result "PASS"
    And revalidation-service consumes ScreeningCompleted for "1003000222" with reason "MANUAL" and result "PASS"
    And revalidation-service consumes ScreeningCompleted for "1003000999" with reason "REVALIDATION" and result "PASS"
    Then the case of "1003000222" still has status "DUE"
    And "1003000999" has no revalidation case

  @AC-7
  Scenario: The next due date comes from provider-service and is followed here
    Given an open revalidation case for "1003000222" due "2026-02-28"
    And revalidation-service consumes ScreeningCompleted for "1003000222" with reason "REVALIDATION" and result "PASS"
    And revalidation-service has consumed ProviderStatusChanged for "1003000222" to status "A" with revalidation due date "2031-03-31"
    When the revalidation sweep runs for business date "2026-04-30" and completes
    Then "1003000222" has no case with status "DUE"
    And "1003000222" has no case with status "IN_SCREENING"
    And the queue file of the last run is empty

  @AC-8
  Scenario: Only the right roles can list and complete cases
    Given an open revalidation case for "1003000222" due "2026-02-28"
    Then listing the revalidation cases answers 200 for "ANALYST" and "SUPERVISOR"
    And listing the revalidation cases answers 403 for "SYSTEM", "ADMIN" and "PROVIDER"
    And listing the revalidation cases answers 401 without a token
    And completing the case of "1003000222" answers 403 for "SUPERVISOR", "ADMIN" and "PROVIDER"
    And completing the case of "1003000222" answers 401 without a token
    And the case of "1003000222" still has status "DUE"
