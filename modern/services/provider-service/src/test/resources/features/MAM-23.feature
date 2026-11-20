@MAM-23
Feature: Record the assigned risk tier on the provider
  provider-service consumes RiskTierAssigned from pem.risk.events and stores the tier it receives; it never recomputes it
  (D-002) and changes nothing else, in particular not the revalidation due date (D-003).

  @AC-1
  Scenario: The assigned tier is saved on the provider
    Given provider "1003000111" is stored with status "P", screen flag "P", risk tier "none" and version 0
    When provider-service consumes a RiskTierAssigned event for "1003000111" with tier "L"
    Then provider "1003000111" has status "P", screen flag "P", risk tier "L" and version 1

  @AC-2
  Scenario: The tier is saved for every provider, not only the first
    Given provider "1003000111" is stored with status "P", screen flag "P", risk tier "none" and version 0
    And provider "1003000222" is stored with status "P", screen flag "P", risk tier "none" and version 0
    And provider "1003000888" is stored with status "P", screen flag "P", risk tier "none" and version 0
    When provider-service consumes a RiskTierAssigned event for "1003000111" with tier "L"
    And provider-service consumes a RiskTierAssigned event for "1003000222" with tier "H"
    And provider-service consumes a RiskTierAssigned event for "1003000888" with tier "M"
    Then provider "1003000111" has status "P", screen flag "P", risk tier "L" and version 1
    And provider "1003000222" has status "P", screen flag "P", risk tier "H" and version 1
    And provider "1003000888" has status "P", screen flag "P", risk tier "M" and version 1

  @AC-3
  Scenario: A new tier replaces the old one
    Given provider "1003000111" is stored with status "P", screen flag "P", risk tier "L" and version 0
    When provider-service consumes a RiskTierAssigned event for "1003000111" with tier "M"
    Then provider "1003000111" has status "P", screen flag "P", risk tier "M" and version 1

  @AC-4
  Scenario: Assigning a tier changes nothing else, in particular not the due date
    Given provider "1003000111" is stored with status "A", screen flag "P", risk tier "L" and version 0
    When provider-service consumes a RiskTierAssigned event for "1003000111" with tier "M"
    Then provider "1003000111" has status "A", screen flag "P", risk tier "M" and version 1
    And provider "1003000111" still has effective date "2026-11-18" and revalidation due date "2031-11-18"
    And no ProviderStatusChanged event is written for "1003000111"

  @AC-5
  Scenario: Arrival order does not matter
    Given provider "1003000111" is stored with status "P", screen flag "U", risk tier "none" and version 0
    When provider-service consumes a RiskTierAssigned event for "1003000111" with tier "L"
    Then provider "1003000111" has status "P", screen flag "U", risk tier "L" and version 1
    When provider-service consumes a ScreeningCompleted event for "1003000111" with reason "INITIAL" and result "PASS"
    Then provider "1003000111" has status "P", screen flag "P", risk tier "L" and version 2

  @AC-6
  Scenario: A redelivered tier event is applied once
    Given provider "1003000111" is stored with status "P", screen flag "P", risk tier "none" and version 0
    When provider-service consumes a RiskTierAssigned event for "1003000111" with tier "L"
    And the same event is delivered again
    Then provider "1003000111" has status "P", screen flag "P", risk tier "L" and version 1
