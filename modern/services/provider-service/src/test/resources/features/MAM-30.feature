@MAM-30
Feature: Revalidation due date: five years, four-digit years, leap day and each completed revalidation
  The due date is set at approval (MAM-27) and at each passed revalidation, never otherwise (D-003); it is the
  service clock date plus five years, a real date with a four-digit year (D-004). The approval dates of AC-1 to AC-3
  need a chosen clock date and are unit-tested (ApprovalTest, RevalidationDueDateTest); these scenarios use the
  service clock.

  @AC-4 @AC-5
  Scenario: A passed revalidation sets the next due date and announces it
    Given provider "1003000111" is ACTIVE with screen flag "P" and revalidation due date "2026-03-31"
    When provider-service consumes a ScreeningCompleted event for "1003000111" with reason "REVALIDATION" and result "PASS"
    Then reading provider "1003000111" gives status "A" and the service clock date plus 5 years as revalidation due date
    And provider "1003000111" now has screen flag "P"
    And 1 ProviderStatusChanged event for "1003000111" goes from "A" to "A" with the service clock date plus 5 years as due date
    And a ProviderStatusChanged event with key "1003000111" reaches topic "pem.provider.events" with to "A"

  @AC-6
  Scenario: A failed re-screening leaves the due date and status alone
    Given provider "1003000111" is ACTIVE with screen flag "P" and revalidation due date "2026-03-31"
    When provider-service consumes a ScreeningCompleted event for "1003000111" with reason "REVALIDATION" and result "FAIL"
    Then reading provider "1003000111" gives status "A" and revalidation due date "2026-03-31"
    And no ProviderStatusChanged event is written for "1003000111"

  @AC-7
  Scenario: Only approval and a passed revalidation move the date
    Given provider "1003000111" is stored with status "P", screen flag "U", risk tier "none" and version 0
    And provider "1003000333" is stored with status "A", screen flag "P", risk tier "L" and version 0
    When provider-service consumes a ScreeningCompleted event for "1003000111" with reason "INITIAL" and result "PASS"
    And provider-service consumes a ScreeningCompleted event for "1003000333" with reason "MANUAL" and result "PASS"
    Then reading provider "1003000111" gives status "P" and no revalidation due date
    And reading provider "1003000333" gives status "A" and revalidation due date "2031-11-18"
    And no ProviderStatusChanged event is written for "1003000111"
    And no ProviderStatusChanged event is written for "1003000333"

  @AC-8
  Scenario: A redelivered revalidation result is applied once
    Given provider "1003000111" is ACTIVE with screen flag "P" and revalidation due date "2026-03-31"
    And provider-service consumes a ScreeningCompleted event for "1003000111" with reason "REVALIDATION" and result "PASS"
    When the same event is delivered again
    Then reading provider "1003000111" gives status "A" and the service clock date plus 5 years as revalidation due date
    And 1 ProviderStatusChanged event for "1003000111" goes from "A" to "A" with the service clock date plus 5 years as due date
