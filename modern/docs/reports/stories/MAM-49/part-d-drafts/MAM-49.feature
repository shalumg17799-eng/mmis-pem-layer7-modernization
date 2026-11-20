@MAM-49
Feature: Revalidation case states: re-screening requested and provider no longer active
  A case is DUE when the sweep opens it and IN_SCREENING once its RevalidationDue has been published (the re-screening
  is requested, C-13); a provider that leaves ACTIVE cancels its open case (C-14, D-006); a late result does not reopen
  a cancelled case; a reinstated provider that is still overdue gets a new case at the next sweep (D-029).

  Background:
    Given revalidation-service has no tracked providers, cases or sweep runs

  @AC-1
  Scenario: A case moves to IN_SCREENING when the re-screening is requested
    Given revalidation-service has consumed ProviderStatusChanged for "1003000222" to status "A" with revalidation due date "2026-02-28"
    When the revalidation sweep runs for business date "2026-03-31" and completes
    Then the case of "1003000222" opened by the sweep reaches status "IN_SCREENING" once its RevalidationDue is published

  @AC-2
  Scenario Outline: A provider that is no longer ACTIVE cancels its open case
    Given an open revalidation case for "1003000222" due "2026-02-28"
    When revalidation-service has consumed ProviderStatusChanged for "1003000222" to status "<status>" with revalidation due date "2026-02-28"
    Then the case of "1003000222" is CANCELLED with closedAt set
    And "ANALYST" lists the revalidation cases with "status=CANCELLED"
    And the case list answers 200 with the cases of "1003000222", page 0 and size 20

    Examples:
      | status |
      | S      |
      | T      |
      | D      |

  @AC-3
  Scenario: A late screening result does not reopen a cancelled case
    Given an open revalidation case for "1003000222" due "2026-02-28"
    And revalidation-service has consumed ProviderStatusChanged for "1003000222" to status "S" with revalidation due date "2026-02-28"
    When revalidation-service consumes ScreeningCompleted for "1003000222" with reason "REVALIDATION" and result "PASS"
    Then the case of "1003000222" still has status "CANCELLED"

  @AC-4
  Scenario: A reinstated provider that is still overdue gets a new case
    Given an open revalidation case for "1003000222" due "2026-02-28"
    And revalidation-service has consumed ProviderStatusChanged for "1003000222" to status "S" with revalidation due date "2026-02-28"
    And revalidation-service has consumed ProviderStatusChanged for "1003000222" to status "A" with revalidation due date "2026-02-28"
    When the revalidation sweep runs for business date "2026-03-31" and completes
    Then the cases of "1003000222" are 1 open case due "2026-02-28" and 1 CANCELLED case
