@MAM-48
Feature: Revalidation cases: list them and complete one
  Staff list the open and closed cases (D-029); a re-screening result closes the case by itself, an analyst or the
  system can close it by hand (PASS or FAIL both close it, Q C-12), and the next due date stays with provider-service
  (D-003). Only ANALYST and SUPERVISOR list; only SYSTEM and ANALYST complete (D-023).

  Background:
    Given the revalidation tables are empty

  @AC-1
  Scenario: Analysts list cases by status and by NPI
    Given a "DUE" case for NPI "1003000222" with due date "2026-02-28"
    And a "IN_SCREENING" case for NPI "1003000333" with due date "2026-03-31"
    And a "COMPLETED" case for NPI "1003000444" with due date "2026-01-31"
    And a "CANCELLED" case for NPI "1003000555" with due date "2026-01-15"
    When "ANALYST" lists revalidation cases with status "DUE", page 0 and size 20
    Then the list answer is 200 with total 1, page 0 and size 20 and exactly the cases of NPI "1003000222"
    And every listed case has a caseId, an npi, a dueDate, a status "DUE" and an openedAt, and no closedAt
    When "SUPERVISOR" lists revalidation cases for NPI "1003000222"
    Then the list answer is 200 with total 1, page 0 and size 20 and exactly the cases of NPI "1003000222"

  @AC-1
  Scenario Outline: Each status filter lists only the cases in that status
    Given a "DUE" case for NPI "1003000222" with due date "2026-02-28"
    And a "IN_SCREENING" case for NPI "1003000333" with due date "2026-03-31"
    And a "COMPLETED" case for NPI "1003000444" with due date "2026-01-31"
    And a "CANCELLED" case for NPI "1003000555" with due date "2026-01-15"
    When "ANALYST" lists revalidation cases with status "<status>", page 0 and size 20
    Then the list answer is 200 with total 1, page 0 and size 20 and exactly the cases of NPI "<npi>"
    And every listed case has the status "<status>"

    Examples:
      | status       | npi        |
      | DUE          | 1003000222 |
      | IN_SCREENING | 1003000333 |
      | COMPLETED    | 1003000444 |
      | CANCELLED    | 1003000555 |

  @AC-1
  Scenario: A closed case shows when it was closed
    Given a "COMPLETED" case for NPI "1003000444" with due date "2026-01-31"
    When "ANALYST" lists revalidation cases for NPI "1003000444"
    Then the list answer is 200 with total 1, page 0 and size 20 and exactly the cases of NPI "1003000444"
    And every listed case has a closedAt

  @AC-1
  Scenario: Cases are listed one page at a time, oldest due date first
    Given a "DUE" case for NPI "1003000333" with due date "2026-03-31"
    And a "DUE" case for NPI "1003000222" with due date "2026-02-28"
    And a "DUE" case for NPI "1003000444" with due date "2026-04-30"
    When "ANALYST" lists revalidation cases with status "DUE", page 1 and size 2
    Then the list answer is 200 with total 3, page 1 and size 2 and exactly the cases of NPI "1003000444"
    When "ANALYST" lists revalidation cases with status "DUE", page 0 and size 2
    Then the list answer is 200 with total 3, page 0 and size 2 and exactly the cases of NPI "1003000222,1003000333"

  @AC-1
  Scenario: The invalid status OPEN is refused with a problem
    Given a "DUE" case for NPI "1003000222" with due date "2026-02-28"
    When "ANALYST" lists revalidation cases with status "OPEN", page 0 and size 20
    Then the list answer is 400 with a problem

  @AC-1
  Scenario Outline: Invalid query values are refused with a problem
    When "ANALYST" lists revalidation cases with the query "<query>"
    Then the list answer is 400 with a problem

    Examples:
      | query           |
      | npi=123         |
      | npi=100300022X  |
      | page=-1         |
      | size=0          |
      | size=201        |

  @AC-2
  Scenario Outline: A case is completed with a PASS screening result
    Given a "<stage>" case for NPI "1003000222" with due date "2026-02-28"
    When "<role>" completes the case of NPI "1003000222" with a screening result "PASS"
    Then the completion answer is 200 with the case of NPI "1003000222" in status "COMPLETED", its due date "2026-02-28" and a closedAt
    And "ANALYST" listing revalidation cases with status "DUE" does not show NPI "1003000222"
    And "ANALYST" listing revalidation cases with status "IN_SCREENING" does not show NPI "1003000222"
    And "ANALYST" listing revalidation cases with status "COMPLETED" shows NPI "1003000222"

    Examples:
      | role    | stage        |
      | ANALYST | DUE          |
      | SYSTEM  | DUE          |
      | ANALYST | IN_SCREENING |

  @AC-2
  Scenario: A FAIL result also closes the case and leaves the due date alone
    Given revalidation-service tracks provider "1003000222" as status "A" with revalidation due date "2026-02-28"
    And a "DUE" case for NPI "1003000222" with due date "2026-02-28"
    When "ANALYST" completes the case of NPI "1003000222" with a screening result "FAIL"
    Then the completion answer is 200 with the case of NPI "1003000222" in status "COMPLETED", its due date "2026-02-28" and a closedAt
    And the tracked due date of NPI "1003000222" is still "2026-02-28"

  @AC-2
  Scenario Outline: A completion request without a screening id or with another result is refused
    Given a "DUE" case for NPI "1003000222" with due date "2026-02-28"
    When "ANALYST" completes the case of NPI "1003000222" with the body '<body>'
    Then the completion answer is 400 with a problem
    And the case of NPI "1003000222" is still "DUE"

    Examples:
      | body                                                                   |
      | {"result":"PASS"}                                                      |
      | {"screeningId":"0b9c5d1e-6a4f-4c1a-9a43-3c2d6f0a1b11"}                 |
      | {"screeningId":"0b9c5d1e-6a4f-4c1a-9a43-3c2d6f0a1b11","result":"MAYBE"} |

  @AC-3
  Scenario: An unknown case is not found
    When "ANALYST" completes the case "7d2f9c3a-1b84-4e5d-8f60-5a9c1e2b3d44" with a screening result "PASS"
    Then the completion answer is 404 with a problem

  @AC-3
  Scenario Outline: A closed case cannot be completed again and stays unchanged
    Given a "<status>" case for NPI "1003000222" with due date "2026-02-28"
    When "ANALYST" completes the case of NPI "1003000222" with a screening result "PASS"
    Then the completion answer is 409 with a problem
    And the case of NPI "1003000222" is unchanged

    Examples:
      | status    |
      | COMPLETED |
      | CANCELLED |

  @AC-4
  Scenario: A passed re-screening completes the case by itself
    Given a "IN_SCREENING" case for NPI "1003000222" with due date "2026-02-28"
    When screening-service publishes ScreeningCompleted with reason "REVALIDATION" and result "PASS" for "1003000222"
    Then NPI "1003000222" has a case in status "COMPLETED" with a closedAt

  @AC-5
  Scenario: A failed re-screening closes the case but does not move the due date
    Given revalidation-service tracks provider "1003000222" as status "A" with revalidation due date "2026-02-28"
    And a "DUE" case for NPI "1003000222" with due date "2026-02-28"
    And the revalidation outbox is empty
    When screening-service publishes ScreeningCompleted with reason "REVALIDATION" and result "FAIL" for "1003000222"
    Then NPI "1003000222" has a case in status "COMPLETED" with a closedAt
    And the tracked due date of NPI "1003000222" is still "2026-02-28"
    And revalidation-service wrote no event for NPI "1003000222"

  @AC-6
  Scenario Outline: Only revalidation screenings close cases
    Given a "DUE" case for NPI "1003000222" with due date "2026-02-28"
    When screening-service publishes ScreeningCompleted with reason "<reason>" and result "PASS" for "1003000222"
    Then the case of NPI "1003000222" is still "DUE"

    Examples:
      | reason  |
      | INITIAL |
      | MANUAL  |

  @AC-6
  Scenario: A revalidation screening of a provider without an open case is ignored
    Given a "DUE" case for NPI "1003000222" with due date "2026-02-28"
    When screening-service publishes ScreeningCompleted with reason "REVALIDATION" and result "PASS" for "1003000999"
    Then the event was processed without error
    And NPI "1003000999" has no revalidation case
    And the case of NPI "1003000222" is still "DUE"

  @AC-7
  Scenario: The next due date comes from provider-service and is followed here
    Given revalidation-service tracks provider "1003000222" as status "A" with revalidation due date "2026-02-28"
    And the revalidation sweep runs for "2026-03-31"
    And "ANALYST" completes the case of NPI "1003000222" with a screening result "PASS"
    When provider-service publishes ProviderStatusChanged for "1003000222" from "A" to "A" with revalidation due date "2031-03-31"
    And the revalidation sweep runs for "2026-04-30"
    Then NPI "1003000222" has no open case
    And NPI "1003000222" has exactly 1 case
    And the REVALQ file of the last run has 0 records
    And the tracked due date of NPI "1003000222" is still "2031-03-31"

  @AC-8
  Scenario Outline: Who may list cases
    Given a "DUE" case for NPI "1003000222" with due date "2026-02-28"
    When "<role>" lists revalidation cases with status "DUE", page 0 and size 20
    Then the list answer is <status>

    Examples:
      | role       | status |
      | ANALYST    | 200    |
      | SUPERVISOR | 200    |
      | SYSTEM     | 403    |
      | ADMIN      | 403    |
      | PROVIDER   | 403    |
      | NONE       | 401    |

  @AC-8
  Scenario Outline: Who may complete a case
    Given a "DUE" case for NPI "1003000222" with due date "2026-02-28"
    When "<role>" completes the case of NPI "1003000222" with a screening result "PASS"
    Then the completion answer is <status>
    And the case of NPI "1003000222" is "<after>"

    Examples:
      | role       | status | after     |
      | SYSTEM     | 200    | COMPLETED |
      | ANALYST    | 200    | COMPLETED |
      | SUPERVISOR | 403    | DUE       |
      | ADMIN      | 403    | DUE       |
      | PROVIDER   | 403    | DUE       |
      | NONE       | 401    | DUE       |

  @AC-4
  Scenario Outline: A passed re-screening completes the open case in either stage
    Given a "<stage>" case for NPI "1003000222" with due date "2026-02-28"
    When screening-service publishes ScreeningCompleted with reason "REVALIDATION" and result "PASS" for "1003000222"
    Then NPI "1003000222" has a case in status "COMPLETED" with a closedAt
    And NPI "1003000222" has no open case

    Examples:
      | stage        |
      | DUE          |
      | IN_SCREENING |

  @AC-5
  Scenario Outline: A failed re-screening closes the case in either stage and opens no new case
    Given revalidation-service tracks provider "1003000222" as status "A" with revalidation due date "2026-02-28"
    And a "<stage>" case for NPI "1003000222" with due date "2026-02-28"
    When screening-service publishes ScreeningCompleted with reason "REVALIDATION" and result "FAIL" for "1003000222"
    Then NPI "1003000222" has a case in status "COMPLETED" with a closedAt
    And NPI "1003000222" has exactly 1 case
    And the tracked due date of NPI "1003000222" is still "2026-02-28"

    Examples:
      | stage        |
      | DUE          |
      | IN_SCREENING |
