@MAM-52
Feature: Record a sanction (terminate, suspend, reinstate) with history and apply it through provider-service
  PRVSAN01 rebuilt as POST /api/v1/sanctions: the action must be T, S or R (D-026); the status is changed only through
  provider-service's status-change API (D-013), here played by a WireMock stand-in that follows the provider-service
  contract; every applied sanction is kept in the history (D-018) and announced as SanctionApplied (D-012); a sanction
  applies whatever the current status (D-027, Q-006); an apply failure is a 503 and nothing is recorded (D-024, Q-B13).

  Background:
    Given the sanctions history and outbox are empty

  @AC-1
  Scenario: A termination sets the provider to TERMINATED
    Given provider-service holds provider "1003000111" with status "A"
    When "SUPERVISOR" records the sanction "T" with reason "SYNTHETIC TEST REASON" and effective date "2026-03-31" for NPI "1003000111"
    Then the sanction answer is 201 with npi "1003000111", action "T", resulting status "T", effective date "2026-03-31", reason "SYNTHETIC TEST REASON", recordedBy "supervisor1" and a recordedAt
    And provider-service received exactly 1 status change for "1003000111" with action "TERMINATE", effective date "2026-03-31", the answered sanctionId and the SYSTEM token
    And provider-service now shows status "T" for "1003000111"
    And exactly 1 SanctionApplied event is written for "1003000111" with action "T", effective date "2026-03-31" and the answered sanctionId
    And the SanctionApplied event for "1003000111" reaches topic "pem.sanctions.events"

  @AC-2
  Scenario: A suspension sets the provider to SUSPENDED, not DENIED
    Given provider-service holds provider "1003000333" with status "A"
    When "SUPERVISOR" records the sanction "S" with reason "SYNTHETIC TEST REASON" and effective date "2026-03-31" for NPI "1003000333"
    Then the sanction answer is 201 with resulting status "S"
    And provider-service received exactly 1 status change for "1003000333" with action "SUSPEND"
    And provider-service now shows status "S" for "1003000333"
    And exactly 1 SanctionApplied event is written for "1003000333" with action "S"

  @AC-3
  Scenario: A reinstatement sets the provider to ACTIVE
    Given provider-service holds provider "1003000333" with status "S"
    When "SUPERVISOR" records the sanction "R" with reason "SYNTHETIC TEST REASON" and effective date "2026-04-30" for NPI "1003000333"
    Then the sanction answer is 201 with resulting status "A"
    And provider-service received exactly 1 status change for "1003000333" with action "REINSTATE"
    And provider-service now shows status "A" for "1003000333"
    And exactly 1 SanctionApplied event is written for "1003000333" with action "R"

  @AC-4
  Scenario: A sanction is applied whatever the current status
    Given provider-service holds provider "1003000444" with status "D"
    And provider-service holds provider "1003000555" with status "P"
    And provider-service holds provider "1003000666" with status "D"
    When "SUPERVISOR" records the sanction "R" with reason "SYNTHETIC TEST REASON" and effective date "2026-04-30" for NPI "1003000444"
    Then the sanction answer is 201 with resulting status "A"
    When "SUPERVISOR" records the sanction "R" with reason "SYNTHETIC TEST REASON" and effective date "2026-04-30" for NPI "1003000555"
    Then the sanction answer is 201 with resulting status "A"
    When "SUPERVISOR" records the sanction "T" with reason "SYNTHETIC TEST REASON" and effective date "2026-03-31" for NPI "1003000666"
    Then the sanction answer is 201 with resulting status "T"
    And provider-service now shows status "A" for "1003000444"
    And provider-service now shows status "A" for "1003000555"
    And provider-service now shows status "T" for "1003000666"
    And exactly 1 SanctionApplied event is written for "1003000444" with action "R"
    And exactly 1 SanctionApplied event is written for "1003000555" with action "R"
    And exactly 1 SanctionApplied event is written for "1003000666" with action "T"

  @AC-5
  Scenario Outline: An unknown action code is rejected
    Given provider-service holds provider "1003000111" with status "A"
    And the sanction history of "1003000111" is read as "ANALYST"
    When "SUPERVISOR" records the sanction "<action>" with reason "SYNTHETIC TEST REASON" and effective date "2026-03-31" for NPI "1003000111"
    Then the sanction answer is 422 problem+json whose detail names "action", "T", "S" and "R" and that has no code
    And the sanction history of "1003000111" is unchanged
    And provider-service received no status change for "1003000111"
    And provider-service now shows status "A" for "1003000111"
    And no SanctionApplied event is written for "1003000111"

    Examples:
      | action |
      | X      |
      |        |

  @AC-6
  Scenario: A sanction for an unknown provider is not found
    Given provider-service has no provider "1003000999"
    When "SUPERVISOR" records the sanction "T" with reason "SYNTHETIC TEST REASON" and effective date "2026-03-31" for NPI "1003000999"
    Then the sanction answer is 404 problem+json whose detail names "1003000999"
    And no sanction is stored for "1003000999"
    And no SanctionApplied event is written for "1003000999"

  @AC-7
  Scenario Outline: A failure to apply is never reported as not found or as applied
    Given provider-service holds provider "1003000111" with status "A"
    And provider-service fails with "<fault>" for "1003000111"
    When "SUPERVISOR" records the sanction "T" with reason "SYNTHETIC TEST REASON" and effective date "2026-03-31" for NPI "1003000111"
    Then the sanction answer is 503 problem+json whose detail names "1003000111"
    And no sanction is stored for "1003000111"
    And no SanctionApplied event is written for "1003000111"

    Examples:
      | fault        |
      | server error |
      | unreachable  |

  @AC-8
  Scenario: The sanction history is readable, and roles are enforced
    Given provider-service holds provider "1003000111" with status "A"
    And "SUPERVISOR" recorded the sanctions "T" and then "R" for NPI "1003000111"
    Then reading the sanction history of "1003000111" as "ANALYST" gives 2 rows with resulting statuses "T" and "A", page 0 and size 20
    And reading the sanction history of "1003000111" as "SUPERVISOR" gives 2 rows with resulting statuses "T" and "A", page 0 and size 20
    And reading the sanction history of "1003000111" as "ADMIN" gives 2 rows with resulting statuses "T" and "A", page 0 and size 20
    And reading the sanction history of "1003000111" as "PROVIDER" answers 403
    And reading the sanction history of "1003000111" as "SYSTEM" answers 403
    And reading the sanction history of "1003000111" as "NONE" answers 401
    When "ANALYST" records the sanction "S" with reason "SYNTHETIC TEST REASON" and effective date "2026-03-31" for NPI "1003000111"
    Then the sanction answer is 403
    When "ADMIN" records the sanction "S" with reason "SYNTHETIC TEST REASON" and effective date "2026-03-31" for NPI "1003000111"
    Then the sanction answer is 403
    When "SYSTEM" records the sanction "S" with reason "SYNTHETIC TEST REASON" and effective date "2026-03-31" for NPI "1003000111"
    Then the sanction answer is 403
    When "PROVIDER" records the sanction "S" with reason "SYNTHETIC TEST REASON" and effective date "2026-03-31" for NPI "1003000111"
    Then the sanction answer is 403
    When "NONE" records the sanction "S" with reason "SYNTHETIC TEST REASON" and effective date "2026-03-31" for NPI "1003000111"
    Then the sanction answer is 401
    And reading the sanction history of "1003000111" as "SUPERVISOR" gives 2 rows with resulting statuses "T" and "A", page 0 and size 20
