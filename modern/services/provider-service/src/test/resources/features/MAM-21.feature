@MAM-21
Feature: Record the screening verdict (screen flag) on the provider
  provider-service consumes ScreeningCompleted from pem.screening.events and writes the verdict to the provider record
  (D-005, D-014). Screening never changes the enrolment status: only the analyst approval sets ACTIVE (D-019).

  @AC-1
  Scenario: A passed screening sets the screen flag to P
    Given provider "1003000111" is stored with status "P", screen flag "U", risk tier "none" and version 0
    When provider-service consumes a ScreeningCompleted event for "1003000111" with reason "INITIAL" and result "PASS"
    Then provider "1003000111" has status "P", screen flag "P", risk tier "none" and version 1

  @AC-2
  Scenario: A failed screening sets the screen flag to F
    Given provider "1003000444" is stored with status "P", screen flag "U", risk tier "none" and version 0
    When provider-service consumes a ScreeningCompleted event for "1003000444" with reason "INITIAL" and result "FAIL"
    Then provider "1003000444" has status "P", screen flag "F", risk tier "none" and version 1
    And searching providers with status "P" and screen flag "F" as ANALYST lists "1003000444"

  @AC-3
  Scenario: Screening never activates a provider
    Given provider "1003000111" is stored with status "P", screen flag "U", risk tier "none" and version 0
    When provider-service consumes a ScreeningCompleted event for "1003000111" with reason "INITIAL" and result "PASS"
    Then provider "1003000111" has status "P", screen flag "P", risk tier "none" and version 1
    And no ProviderStatusChanged event is written for "1003000111"

  @AC-4
  Scenario: Every verdict is saved
    Given provider "1003000111" is stored with status "P", screen flag "U", risk tier "none" and version 4
    When provider-service consumes a ScreeningCompleted event for "1003000111" with reason "INITIAL" and result "PASS"
    Then provider "1003000111" has status "P", screen flag "P", risk tier "none" and version 5

  @AC-5
  Scenario: A re-screening records its verdict the same way and never changes the status
    Given provider "1003000111" is stored with status "A", screen flag "P", risk tier "L" and version 2
    When provider-service consumes a ScreeningCompleted event for "1003000111" with reason "REVALIDATION" and result "FAIL"
    Then provider "1003000111" has status "A", screen flag "F", risk tier "L" and version 3
    When provider-service consumes a ScreeningCompleted event for "1003000111" with reason "MANUAL" and result "PASS"
    Then provider "1003000111" has status "A", screen flag "P", risk tier "L" and version 4

  @AC-6
  Scenario: A redelivered verdict is applied once
    Given provider "1003000111" is stored with status "P", screen flag "U", risk tier "none" and version 0
    When provider-service consumes a ScreeningCompleted event for "1003000111" with reason "INITIAL" and result "PASS"
    And the same event is delivered again
    Then provider "1003000111" has status "P", screen flag "P", risk tier "none" and version 1
