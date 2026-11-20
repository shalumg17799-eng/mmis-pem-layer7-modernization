@MAM-33
Feature: Apply a sanction status change to the provider (TERMINATED, SUSPENDED, ACTIVE)
  PRVSAN01 APPLY-SANCTION rebuilt as POST /api/v1/providers/{npi}/status-changes: only provider-service changes the
  status (D-013, D-014); a suspension is SUSPENDED, not DENIED (D-006); the change applies whatever the current status
  (D-027, Q-006); unknown actions and incomplete requests are refused with 422 (D-026, D-024); roles SYSTEM and
  SUPERVISOR (D-023).

  @AC-1
  Scenario: Terminate sets TERMINATED and counts it
    Given provider "1003000111" is stored with status "A", screen flag "P", risk tier "L" and version 0
    When a "SYSTEM" caller sends the status change "TERMINATE" with reason "LEIE exclusion" and effective date "2026-03-31" for provider "1003000111"
    Then the status-change answer is 200 with enrollStatus "T"
    And the terminations counter rose by 1

  @AC-2
  Scenario: Suspend sets SUSPENDED, not DENIED
    Given provider "1003000111" is stored with status "A", screen flag "P", risk tier "L" and version 0
    When a "SYSTEM" caller sends the status change "SUSPEND" with reason "LEIE exclusion" and effective date "2026-03-31" for provider "1003000111"
    Then the status-change answer is 200 with enrollStatus "S"
    And the terminations counter is unchanged

  @AC-3
  Scenario: Reinstate sets ACTIVE
    Given provider "1003000111" is stored with status "S", screen flag "P", risk tier "L" and version 0
    When a "SYSTEM" caller sends the status change "REINSTATE" with effective date "2026-04-30" for provider "1003000111"
    Then the status-change answer is 200 with enrollStatus "A"
    And the terminations counter is unchanged

  @AC-4
  Scenario: The change applies whatever the current status is
    Given provider "1003000444" is stored with status "D", screen flag "F", risk tier "none" and version 0
    And provider "1003000222" is stored with status "P", screen flag "U", risk tier "none" and version 0
    When a "SYSTEM" caller sends the status change "REINSTATE" with effective date "2026-04-30" for provider "1003000444"
    Then the status-change answer is 200 with enrollStatus "A"
    When a "SYSTEM" caller sends the status change "TERMINATE" with effective date "2026-03-31" for provider "1003000222"
    Then the status-change answer is 200 with enrollStatus "T"
    And GET of provider "1003000444" returns enrollStatus "A"
    And GET of provider "1003000222" returns enrollStatus "T"

  @AC-5
  Scenario: The new status is saved and announced
    Given provider "1003000111" is stored with status "A", screen flag "P", risk tier "L" and version 0
    When a "SYSTEM" caller sends the status change "SUSPEND" with reason "LEIE exclusion" and effective date "2026-03-31" for provider "1003000111"
    Then the status-change answer is 200 with enrollStatus "S"
    And GET of provider "1003000111" returns enrollStatus "S"
    And the status change of "1003000111" is announced once from "A" to "S" with reason "LEIE exclusion" and actor "service-account-pem-services"
    And a ProviderStatusChanged event with key "1003000111" reaches topic "pem.provider.events" with to "S"

  @AC-6
  Scenario: An unknown action or an incomplete request is refused
    Given provider "1003000111" is stored with status "A", screen flag "P", risk tier "L" and version 0
    When a "SYSTEM" caller sends the status change "PURGE" with effective date "2026-03-31" for provider "1003000111"
    Then the status-change answer is 422 problem+json
    When a "SYSTEM" caller sends the status change "SUSPEND" without an effective date for provider "1003000111"
    Then the status-change answer is 422 problem+json
    And provider "1003000111" still has status "A" and version 0
    And no ProviderStatusChanged event is written for "1003000111"

  @AC-7
  Scenario: An unknown provider gives 404 and changes nothing
    Given no provider with NPI "1003000999" exists
    When a "SYSTEM" caller sends the status change "TERMINATE" with effective date "2026-03-31" for provider "1003000999"
    Then the status-change answer is 404 problem+json
    And exactly 0 providers with NPI "1003000999" exist
    And no event of any type is written for "1003000999"

  @AC-8
  Scenario: Only SYSTEM and SUPERVISOR can change the status
    Given provider "1003000111" is stored with status "A", screen flag "P", risk tier "L" and version 0
    And provider "1003000333" is stored with status "A", screen flag "P", risk tier "L" and version 0
    When a "ANALYST" caller sends the status change "SUSPEND" with effective date "2026-03-31" for provider "1003000111"
    Then the status-change answer is 403 problem+json
    When a "PROVIDER" caller sends the status change "SUSPEND" with effective date "2026-03-31" for provider "1003000111"
    Then the status-change answer is 403 problem+json
    When a "ADMIN" caller sends the status change "SUSPEND" with effective date "2026-03-31" for provider "1003000111"
    Then the status-change answer is 403 problem+json
    When a "NONE" caller sends the status change "SUSPEND" with effective date "2026-03-31" for provider "1003000111"
    Then the status-change answer is 401
    And provider "1003000111" still has status "A" and version 0
    When a "SYSTEM" caller sends the status change "SUSPEND" with effective date "2026-03-31" for provider "1003000111"
    Then the status-change answer is 200 with enrollStatus "S"
    When a "SUPERVISOR" caller sends the status change "SUSPEND" with effective date "2026-03-31" for provider "1003000333"
    Then the status-change answer is 200 with enrollStatus "S"
