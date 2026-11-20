@MAM-49
Feature: Revalidation case states: re-screening requested and provider no longer active
  A case moves from DUE to IN_SCREENING once its RevalidationDue event has been published (Q C-13); a provider that
  is no longer ACTIVE cancels its open case (Q C-14, D-006, D-029); a late screening result does not reopen a
  cancelled case; a provider that is reinstated while still overdue gets a new case at the next sweep.

  Background:
    Given the revalidation tables are empty

  @AC-1
  Scenario: A case moves to IN_SCREENING when the re-screening is requested
    Given revalidation-service tracks provider "1003000222" as status "A" with revalidation due date "2026-02-28"
    When the revalidation sweep runs for "2026-03-31"
    And the RevalidationDue event of NPI "1003000222" has been published
    Then NPI "1003000222" has a case in status "IN_SCREENING" within 45 seconds

  @AC-1
  Scenario: An IN_SCREENING case is still an open case that is not queued again
    Given revalidation-service tracks provider "1003000222" as status "A" with revalidation due date "2026-02-28"
    And the revalidation sweep runs for "2026-03-31"
    And the RevalidationDue event of NPI "1003000222" has been published
    And NPI "1003000222" has a case in status "IN_SCREENING" within 45 seconds
    When the revalidation sweep runs for "2026-04-30"
    Then NPI "1003000222" has exactly 1 case
    And the REVALQ file of the last run has 0 records

  @AC-2
  Scenario Outline: A provider that is no longer ACTIVE cancels its open case
    Given a "<stage>" case for NPI "1003000222" with due date "2026-02-28"
    When provider-service publishes ProviderStatusChanged for "1003000222" from "A" to "<to>" with revalidation due date "2026-02-28"
    Then NPI "1003000222" has a case in status "CANCELLED" with a closedAt
    And "ANALYST" listing revalidation cases with status "CANCELLED" shows NPI "1003000222"
    And NPI "1003000222" has no open case

    Examples:
      | stage        | to |
      | DUE          | S  |
      | DUE          | T  |
      | DUE          | D  |
      | IN_SCREENING | S  |
      | IN_SCREENING | T  |
      | IN_SCREENING | D  |

  @AC-2
  Scenario: A provider that stays ACTIVE keeps its open case
    Given a "DUE" case for NPI "1003000222" with due date "2026-02-28"
    When provider-service publishes ProviderStatusChanged for "1003000222" from "A" to "A" with revalidation due date "2026-02-28"
    Then the case of NPI "1003000222" is still "DUE"

  @AC-2
  Scenario: A status change of a provider without an open case opens nothing
    When provider-service publishes ProviderStatusChanged for "1003000444" from "A" to "S" with revalidation due date "2026-02-28"
    Then NPI "1003000444" has no revalidation case

  @AC-2
  Scenario: Only the cancelled provider's case is cancelled
    Given a "DUE" case for NPI "1003000222" with due date "2026-02-28"
    And a "DUE" case for NPI "1003000333" with due date "2026-03-31"
    When provider-service publishes ProviderStatusChanged for "1003000222" from "A" to "S" with revalidation due date "2026-02-28"
    Then NPI "1003000222" has a case in status "CANCELLED" with a closedAt
    And the case of NPI "1003000333" is still "DUE"

  @AC-3
  Scenario: A late screening result does not reopen a cancelled case
    Given a "CANCELLED" case for NPI "1003000222" with due date "2026-02-28"
    When screening-service publishes ScreeningCompleted with reason "REVALIDATION" and result "PASS" for "1003000222"
    Then the event was processed without error
    And NPI "1003000222" has exactly 1 case
    And the case of NPI "1003000222" is unchanged

  @AC-4
  Scenario: A reinstated provider that is still overdue gets a new case
    Given revalidation-service tracks provider "1003000222" as status "A" with revalidation due date "2026-02-28"
    And a "CANCELLED" case for NPI "1003000222" with due date "2026-02-28"
    When provider-service publishes ProviderStatusChanged for "1003000222" from "S" to "A" with revalidation due date "2026-02-28"
    And the revalidation sweep runs for "2026-03-31"
    Then NPI "1003000222" has exactly one open case with due date "2026-02-28"
    And NPI "1003000222" has exactly 2 cases
    And NPI "1003000222" has a case in status "CANCELLED" with a closedAt
    And "ANALYST" listing revalidation cases with status "CANCELLED" shows NPI "1003000222"

  @AC-4
  Scenario: A provider that was suspended and cancelled is not swept while it is suspended
    Given revalidation-service tracks provider "1003000222" as status "A" with revalidation due date "2026-02-28"
    And the revalidation sweep runs for "2026-03-31"
    When provider-service publishes ProviderStatusChanged for "1003000222" from "A" to "S" with revalidation due date "2026-02-28"
    And the revalidation sweep runs for "2026-04-30"
    Then NPI "1003000222" has no open case
    And NPI "1003000222" has exactly 1 case

  @AC-3
  Scenario: A late failed result does not reopen a cancelled case either
    Given a "CANCELLED" case for NPI "1003000222" with due date "2026-02-28"
    When screening-service publishes ScreeningCompleted with reason "REVALIDATION" and result "FAIL" for "1003000222"
    Then the event was processed without error
    And NPI "1003000222" has exactly 1 case
    And the case of NPI "1003000222" is unchanged

  @AC-2
  Scenario: A cancelled case is listed with its closing time and no longer as open work
    Given a "IN_SCREENING" case for NPI "1003000222" with due date "2026-02-28"
    And a "DUE" case for NPI "1003000333" with due date "2026-03-31"
    When provider-service publishes ProviderStatusChanged for "1003000222" from "A" to "T" with revalidation due date "2026-02-28"
    Then "ANALYST" listing revalidation cases with status "IN_SCREENING" does not show NPI "1003000222"
    And "ANALYST" listing revalidation cases with status "CANCELLED" shows NPI "1003000222"
    And "ANALYST" listing revalidation cases with status "DUE" shows NPI "1003000333"
