@MAM-55
Feature: Run the legacy SANCIN sanction intake as a batch job
  PRVSAN01 rebuilt as the Spring Batch job PRVSAN01, started with POST /api/v1/batch/sanction-intake-runs: a SANCIN file of
  80-byte records and an explicit business date (D-020). Every record is applied like a sanction entered by a supervisor,
  through provider-service's status-change API (D-013) and kept in the history (D-018) whatever the provider's current
  status (D-027). Records for unknown providers, with unknown actions or an impossible date are counted and skipped, the
  run goes on (D-024, D-026); an unreadable record or a failure to apply ends the run FAILED with a reason; a business date
  that was loaded before applies nothing twice; only ADMIN and SYSTEM start a run and only ADMIN reads it (D-023).
  Provider-service is a WireMock stand-in that follows its contract.

  Background:
    Given the sanction intake runs, history and outbox are empty

  @AC-1
  Scenario: A run starts with an explicit business date
    Given provider-service holds provider "1003000111" with status "A"
    And a SANCIN file with these records:
      | npi        | action | reason                | effectiveDate |
      | 1003000111 | T      | SYNTHETIC TEST REASON | 20260331      |
    When "ADMIN" starts a sanction intake run with the file for business date "2026-03-31"
    Then the start answer is 202 with a run id, job "PRVSAN01", business date "2026-03-31" and status RUNNING or COMPLETED
    And reading the run as "ADMIN" shows business date "2026-03-31"

  @AC-1
  Scenario: A start without a business date is a 400 and creates no run
    Given provider-service holds provider "1003000111" with status "A"
    And a SANCIN file with these records:
      | npi        | action | reason                | effectiveDate |
      | 1003000111 | T      | SYNTHETIC TEST REASON | 20260331      |
    When "ADMIN" starts a sanction intake run with the file and no business date
    Then the start answer is 400 problem+json whose detail names "businessDate"
    And no sanction intake run exists
    And provider-service received no status change for "1003000111"

  @AC-1
  Scenario: A start without the file is a 400 and creates no run
    When "ADMIN" starts a sanction intake run for business date "2026-03-31" without the file
    Then the start answer is 400 problem+json whose detail names "file"
    And no sanction intake run exists

  @AC-2
  Scenario Outline: Every record is read from its fixed positions until the last one
    Given provider-service holds provider "1003000111" with status "A"
    And provider-service holds provider "1003000333" with status "A"
    And provider-service holds provider "1003000222" with status "S"
    And a SANCIN file with these records:
      | npi        | action | reason                  | effectiveDate |
      | 1003000111 | T      | SYNTHETIC TEST REASON   | 20260331      |
      | 1003000333 | S      | SYNTHETIC SECOND REASON | 20260430      |
      | 1003000222 | R      | SYNTHETIC THIRD REASON  | 20260515      |
    And the file is written with line ends "<lineEnds>"
    When "ADMIN" starts a sanction intake run with the file for business date "2026-03-31"
    And the run has ended
    Then the run is COMPLETED with an endedAt and no error
    And the run counts are:
      | read | applied | notFound | rejected | skipped |
      | 3    | 3       | 0        | 0        | 0       |
    And the sanction history of "1003000111" shows 1 row with effective date "2026-03-31" and reason "SYNTHETIC TEST REASON"
    And the sanction history of "1003000333" shows 1 row with effective date "2026-04-30" and reason "SYNTHETIC SECOND REASON"
    And the sanction history of "1003000222" shows 1 row with effective date "2026-05-15" and reason "SYNTHETIC THIRD REASON"

    Examples:
      | lineEnds |
      | LF       |
      | CRLF     |
      | NONE     |

  @AC-3
  Scenario Outline: Each record is applied through provider-service and kept in the history
    Given provider-service holds provider "1003000111" with status "A"
    And provider-service holds provider "1003000333" with status "A"
    And provider-service holds provider "1003000222" with status "S"
    And a SANCIN file with these records:
      | npi        | action | reason                | effectiveDate |
      | 1003000111 | T      | SYNTHETIC TEST REASON | 20260331      |
      | 1003000333 | S      | SYNTHETIC TEST REASON | 20260331      |
      | 1003000222 | R      | SYNTHETIC TEST REASON | 20260331      |
    When "<starter>" starts a sanction intake run with the file for business date "2026-03-31"
    And the run has ended
    Then the run is COMPLETED with an endedAt and no error
    And provider-service now shows status "T" for "1003000111"
    And provider-service now shows status "S" for "1003000333"
    And provider-service now shows status "A" for "1003000222"
    And provider-service received exactly 1 status change for "1003000111" with action "TERMINATE", effective date "2026-03-31" and the SYSTEM token
    And provider-service received exactly 1 status change for "1003000333" with action "SUSPEND", effective date "2026-03-31" and the SYSTEM token
    And provider-service received exactly 1 status change for "1003000222" with action "REINSTATE", effective date "2026-03-31" and the SYSTEM token
    And the sanction history holds 3 rows for the NPIs "1003000111, 1003000333, 1003000222"
    And every sanction history row has sourceFeed "SANCIN" and recordedBy "<account>"
    And exactly 1 SanctionApplied event is written for "1003000111" with action "T"
    And exactly 1 SanctionApplied event is written for "1003000333" with action "S"
    And exactly 1 SanctionApplied event is written for "1003000222" with action "R"
    And the SanctionApplied event for "1003000111" carries the sanctionId of its history row
    And the SanctionApplied event for "1003000111" reaches topic "pem.sanctions.events"

    Examples:
      | starter | account                      |
      | ADMIN   | admin1                       |
      | SYSTEM  | service-account-pem-services |

  @AC-4
  Scenario: Records that cannot be applied are counted, the run goes on and completes
    Given provider-service holds provider "1003000111" with status "A"
    And provider-service has no provider "1003000999"
    And provider-service holds provider "1003000333" with status "A"
    And provider-service holds provider "1003000222" with status "S"
    And a SANCIN file with these records:
      | npi        | action | reason                | effectiveDate |
      | 1003000111 | T      | SYNTHETIC TEST REASON | 20260331      |
      | 1003000999 | T      | SYNTHETIC TEST REASON | 20260331      |
      | 1003000333 | X      | SYNTHETIC TEST REASON | 20260331      |
      | 1003000333 | S      | SYNTHETIC TEST REASON | 20261331      |
      | 1003000222 | T      | SYNTHETIC TEST REASON | 20260331      |
    When "ADMIN" starts a sanction intake run with the file for business date "2026-03-31"
    And the run has ended
    Then the run is COMPLETED with an endedAt and no error
    And the run counts are:
      | read | applied | notFound | rejected | skipped | terminated |
      | 5    | 2       | 1        | 2        | 0       | 2          |
    And the sanction history holds 2 rows for the NPIs "1003000111, 1003000222"
    And exactly 1 SanctionApplied event is written for "1003000111" with action "T"
    And exactly 1 SanctionApplied event is written for "1003000222" with action "T"
    And no SanctionApplied event is written for "1003000999"
    And no SanctionApplied event is written for "1003000333"
    And provider-service received no status change for "1003000333"
    And provider-service now shows status "A" for "1003000333"
    And provider-service now shows status "T" for "1003000222"
    And the service log contains the line "PRVSAN01: NPI NOT FOUND 1003000999"

  @AC-5
  Scenario: Only terminations are counted as terminated
    Given provider-service holds provider "1003000111" with status "A"
    And provider-service holds provider "1003000333" with status "A"
    And provider-service holds provider "1003000222" with status "S"
    And provider-service holds provider "1003000555" with status "A"
    And a SANCIN file with these records:
      | npi        | action | reason                | effectiveDate |
      | 1003000111 | T      | SYNTHETIC TEST REASON | 20260331      |
      | 1003000333 | S      | SYNTHETIC TEST REASON | 20260331      |
      | 1003000222 | R      | SYNTHETIC TEST REASON | 20260331      |
      | 1003000555 | T      | SYNTHETIC TEST REASON | 20260331      |
    When "ADMIN" starts a sanction intake run with the file for business date "2026-03-31"
    And the run has ended
    Then the run is COMPLETED with an endedAt and no error
    And the run counts are:
      | read | applied | terminated |
      | 4    | 4       | 2          |
    And the service log contains the line "PRVSAN01: PROVIDERS TERMINATED=00002"

  @AC-5
  Scenario: A run without a termination reports the legacy counter as 00000
    Given provider-service holds provider "1003000333" with status "A"
    And a SANCIN file with these records:
      | npi        | action | reason                | effectiveDate |
      | 1003000333 | S      | SYNTHETIC TEST REASON | 20260331      |
    When "ADMIN" starts a sanction intake run with the file for business date "2026-03-31"
    And the run has ended
    Then the run is COMPLETED with an endedAt and no error
    And the run counts are:
      | read | applied | terminated |
      | 1    | 1       | 0          |
    And the service log contains the line "PRVSAN01: PROVIDERS TERMINATED=00000"

  @AC-6
  Scenario: An unreadable record ends the run FAILED and names the record
    Given provider-service holds provider "1003000111" with status "A"
    And provider-service holds provider "1003000333" with status "A"
    And provider-service holds provider "1003000222" with status "S"
    And a SANCIN file with these records:
      | npi        | action | reason                | effectiveDate |
      | 1003000111 | T      | SYNTHETIC TEST REASON | 20260331      |
      | 1003000333 | S      | SYNTHETIC TEST REASON | 20260331      |
      | 1003000222 | R      | SYNTHETIC TEST REASON | 20260331      |
    And record 2 of the file is cut to 60 bytes
    When "ADMIN" starts a sanction intake run with the file for business date "2026-03-31"
    And the run has ended
    Then the run is FAILED with an endedAt and an error naming "record 2"
    And the run counts are:
      | applied |
      | 1       |
    And the sanction history holds 1 row for the NPIs "1003000111"
    And exactly 1 SanctionApplied event is written for "1003000111" with action "T"
    And provider-service now shows status "T" for "1003000111"
    And provider-service received no status change for "1003000333"
    And provider-service received no status change for "1003000222"
    And no SanctionApplied event is written for "1003000222"

  @AC-6
  Scenario Outline: A failure to apply ends the run FAILED and names the record and its NPI
    Given provider-service holds provider "1003000111" with status "A"
    And provider-service holds provider "1003000333" with status "A"
    And provider-service holds provider "1003000222" with status "S"
    And provider-service fails with "<fault>" for "1003000333"
    And a SANCIN file with these records:
      | npi        | action | reason                | effectiveDate |
      | 1003000111 | T      | SYNTHETIC TEST REASON | 20260331      |
      | 1003000333 | S      | SYNTHETIC TEST REASON | 20260331      |
      | 1003000222 | R      | SYNTHETIC TEST REASON | 20260331      |
    When "ADMIN" starts a sanction intake run with the file for business date "2026-03-31"
    And the run has ended
    Then the run is FAILED with an endedAt and an error naming "record 2" and "1003000333"
    And the run counts are:
      | applied |
      | 1       |
    And the sanction history holds 1 row for the NPIs "1003000111"
    And exactly 1 SanctionApplied event is written for "1003000111" with action "T"
    And no SanctionApplied event is written for "1003000333"
    And no SanctionApplied event is written for "1003000222"
    And provider-service now shows status "T" for "1003000111"
    And provider-service received no status change for "1003000222"

    Examples:
      | fault        |
      | server error |
      | unreachable  |

  @AC-7
  Scenario: Running the same business date again applies nothing twice
    Given provider-service holds provider "1003000111" with status "A"
    And provider-service holds provider "1003000333" with status "A"
    And a SANCIN file with these records:
      | npi        | action | reason                | effectiveDate |
      | 1003000111 | T      | SYNTHETIC TEST REASON | 20260331      |
      | 1003000333 | S      | SYNTHETIC TEST REASON | 20260331      |
    When "ADMIN" starts a sanction intake run with the file for business date "2026-03-31"
    And the run has ended
    Then the run is COMPLETED with an endedAt and no error
    And the run counts are:
      | read | applied | skipped |
      | 2    | 2       | 0       |
    When "ADMIN" starts a sanction intake run with the file for business date "2026-03-31"
    And the run has ended
    Then the run is COMPLETED with an endedAt and no error
    And the run counts are:
      | read | applied | notFound | rejected | skipped |
      | 2    | 0       | 0        | 0        | 2       |
    And the sanction history holds 2 rows for the NPIs "1003000111, 1003000333"
    And exactly 1 SanctionApplied event is written for "1003000111" with action "T"
    And exactly 1 SanctionApplied event is written for "1003000333" with action "S"
    And provider-service received exactly 1 status change for "1003000111" with action "TERMINATE"
    And provider-service received exactly 1 status change for "1003000333" with action "SUSPEND"
    And provider-service now shows status "T" for "1003000111"
    And provider-service now shows status "S" for "1003000333"

  @AC-8
  Scenario Outline: ADMIN and SYSTEM may start a run
    Given provider-service holds provider "1003000111" with status "A"
    And a SANCIN file with these records:
      | npi        | action | reason                | effectiveDate |
      | 1003000111 | T      | SYNTHETIC TEST REASON | 20260331      |
    When "<role>" starts a sanction intake run with the file for business date "2026-03-31"
    Then the start answer is 202 with a run id, job "PRVSAN01", business date "2026-03-31" and status RUNNING or COMPLETED

    Examples:
      | role   |
      | ADMIN  |
      | SYSTEM |

  @AC-8
  Scenario Outline: Every other role is refused a start and no run is created
    Given provider-service holds provider "1003000111" with status "A"
    And a SANCIN file with these records:
      | npi        | action | reason                | effectiveDate |
      | 1003000111 | T      | SYNTHETIC TEST REASON | 20260331      |
    When "<role>" starts a sanction intake run with the file for business date "2026-03-31"
    Then the start answer is 403 problem+json
    And no sanction intake run exists
    And provider-service received no status change for "1003000111"

    Examples:
      | role       |
      | ANALYST    |
      | SUPERVISOR |
      | PROVIDER   |

  @AC-8
  Scenario: A start without a token is 401 and no run is created
    Given provider-service holds provider "1003000111" with status "A"
    And a SANCIN file with these records:
      | npi        | action | reason                | effectiveDate |
      | 1003000111 | T      | SYNTHETIC TEST REASON | 20260331      |
    When "NONE" starts a sanction intake run with the file for business date "2026-03-31"
    Then the start answer is 401
    And no sanction intake run exists
    And provider-service received no status change for "1003000111"

  @AC-8
  Scenario: Only ADMIN reads a run
    Given provider-service holds provider "1003000111" with status "A"
    And a SANCIN file with these records:
      | npi        | action | reason                | effectiveDate |
      | 1003000111 | T      | SYNTHETIC TEST REASON | 20260331      |
    When "ADMIN" starts a sanction intake run with the file for business date "2026-03-31"
    And the run has ended
    Then reading the run as "ADMIN" answers 200
    And reading the run as "SYSTEM" answers 403 problem+json
    And reading the run as "ANALYST" answers 403 problem+json
    And reading the run as "SUPERVISOR" answers 403 problem+json
    And reading the run as "PROVIDER" answers 403 problem+json
    And reading the run as "NONE" answers 401

  @AC-8
  Scenario: A run that does not exist is a 404
    When "ADMIN" reads the run "00000000-0000-0000-0000-000000000000"
    Then the read answer is 404 problem+json whose detail names "00000000-0000-0000-0000-000000000000"
