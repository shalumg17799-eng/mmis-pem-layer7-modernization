@MAM-45
Feature: Monthly revalidation sweep: open one case for every ACTIVE provider that is due, with the legacy REVALQ file
  PRVREV01 rebuilt as POST /api/v1/batch/revalidation-sweeps with an explicit business date (D-020): an ACTIVE provider
  whose due date has arrived gets one DUE case (D-029) and one RevalidationDue event (D-003, D-006), the run records its
  counts and failure reason (D-024) and its REVALQ file keeps the legacy 80-byte layout (D-004, ADR-005). The tracked
  status and due date come from ProviderStatusChanged events (D-014).

  Background:
    Given the revalidation tables are empty

  @AC-1
  Scenario Outline: ADMIN and SYSTEM start the sweep and get a RUNNING BatchRun
    When "<role>" starts the revalidation sweep for business date "2026-03-31"
    Then the sweep start answer is 202 with a BatchRun for business date "2026-03-31", job "revalidation-sweep" and status "RUNNING"

    Examples:
      | role   |
      | ADMIN  |
      | SYSTEM |

  @AC-1
  Scenario Outline: Other roles and a missing token cannot start the sweep
    When "<role>" starts the revalidation sweep for business date "2026-03-31"
    Then the sweep start answer is <status>
    And no sweep run is recorded

    Examples:
      | role     | status |
      | ANALYST  | 403    |
      | SUPERVISOR | 403  |
      | PROVIDER | 403    |
      | NONE     | 401    |

  @AC-1
  Scenario: A body without businessDate is refused
    When "ADMIN" starts the revalidation sweep without a business date
    Then the sweep start answer is 400 with a problem
    And no sweep run is recorded

  @AC-1
  Scenario Outline: A business date that is not an ISO date (yyyy-mm-dd) is refused
    When "ADMIN" starts the revalidation sweep with the body '<body>'
    Then the sweep start answer is 400 with a problem
    And no sweep run is recorded

    Examples:
      | body                                    |
      | {"businessDate":"2026-03-31T00:00:00"}  |
      | {"businessDate":"2026-03-31T00:00:00Z"} |
      | {"businessDate":"2026-03-31 "}          |
      | {"businessDate":" 2026-03-31"}          |
      | {"businessDate":"20260331"}             |
      | {"businessDate":"2026-3-31"}            |
      | {"businessDate":"31/03/2026"}           |
      | {"businessDate":"2026-02-30"}           |
      | {"businessDate":[2026,3,31]}            |
      | {"businessDate":20260331}               |
      | {"businessDate":""}                     |

  @AC-1
  Scenario: Due dates are compared with the business date of the run, not with the clock date
    Given revalidation-service tracks provider "1003000333" as status "A" with revalidation due date "2026-03-31"
    When the revalidation sweep runs for "2026-03-30"
    Then the run counts show queued 0 and noDueDate 0
    And NPI "1003000333" has no revalidation case
    When the revalidation sweep runs for "2026-03-31"
    Then the run counts show queued 1 and noDueDate 0
    And NPI "1003000333" has exactly one open case with due date "2026-03-31"

  @AC-2
  Scenario: An ACTIVE provider whose due date has arrived gets one case
    Given revalidation-service tracks provider "1003000222" as status "A" with revalidation due date "2026-02-28"
    And revalidation-service tracks provider "1003000333" as status "A" with revalidation due date "2026-03-31"
    When the revalidation sweep runs for "2026-03-31"
    Then NPI "1003000222" has exactly one open case with due date "2026-02-28"
    And NPI "1003000333" has exactly one open case with due date "2026-03-31"

  @AC-3
  Scenario: Providers that are not due, not ACTIVE or moved forward get no case
    Given revalidation-service tracks provider "1003000777" as status "A" with revalidation due date "2026-04-01"
    And revalidation-service tracks provider "1003000888" as status "S" with revalidation due date "2026-02-28"
    And revalidation-service tracks provider "1003000555" as status "T" with revalidation due date "2026-02-28"
    And revalidation-service tracks provider "1003000111" as status "A" with revalidation due date "2026-02-28"
    And provider-service publishes ProviderStatusChanged for "1003000111" from "A" to "A" with revalidation due date "2031-03-31"
    When the revalidation sweep runs for "2026-03-31"
    Then NPI "1003000777" has no revalidation case
    And NPI "1003000888" has no revalidation case
    And NPI "1003000555" has no revalidation case
    And NPI "1003000111" has no revalidation case
    And the REVALQ file of the last run has 0 records

  @AC-3
  Scenario Outline: Every status other than ACTIVE is skipped even when its due date has passed
    Given revalidation-service tracks provider "1003000444" as status "<status>" with revalidation due date "2026-02-28"
    When the revalidation sweep runs for "2026-03-31"
    Then NPI "1003000444" has no revalidation case

    Examples:
      | status |
      | P      |
      | D      |
      | T      |
      | S      |

  @AC-4
  Scenario: Opening a case announces it once
    Given revalidation-service tracks provider "1003000222" as status "A" with revalidation due date "2026-02-28"
    When the revalidation sweep runs for "2026-03-31"
    Then exactly 1 RevalidationDue event is written for NPI "1003000222" with the case id, npi "1003000222" and due date "2026-02-28"
    And that RevalidationDue event reaches topic "pem.revalidation.events" with message key "1003000222"

  @AC-5
  Scenario: A provider that already has an open case is not queued again
    Given revalidation-service tracks provider "1003000222" as status "A" with revalidation due date "2026-02-28"
    And the revalidation sweep runs for "2026-03-31"
    When the revalidation sweep runs for "2026-03-31"
    And the revalidation sweep runs for "2026-04-30"
    Then NPI "1003000222" has exactly one open case with due date "2026-02-28"
    And exactly 1 RevalidationDue event is written for NPI "1003000222" with the case id, npi "1003000222" and due date "2026-02-28"
    And the REVALQ file of run 2 has 0 records
    And the REVALQ file of run 3 has 0 records
    And the run counts of run 2 show queued 0 and noDueDate 0
    And the run counts of run 3 show queued 0 and noDueDate 0

  @AC-6
  Scenario: A provider with no due date is not queued, and the gap is visible
    Given revalidation-service tracks provider "1003000666" as status "A" with no revalidation due date
    When the revalidation sweep runs for "2026-03-31"
    Then NPI "1003000666" has no revalidation case
    And the REVALQ file of the last run has 0 records
    And the run counts show queued 0 and noDueDate 1

  @AC-6
  Scenario: A provider that is not ACTIVE and has no due date is not counted under noDueDate
    Given revalidation-service tracks provider "1003000666" as status "S" with no revalidation due date
    When the revalidation sweep runs for "2026-03-31"
    Then the run counts show queued 0 and noDueDate 0

  @AC-7
  Scenario: The REVALQ file lists the cases the run opened in ascending NPI order
    Given revalidation-service tracks provider "1003000333" as status "A" with revalidation due date "2026-03-31"
    And revalidation-service tracks provider "1003000222" as status "A" with revalidation due date "2026-02-28"
    When the revalidation sweep runs for "2026-03-31"
    Then the REVALQ file of the last run is a text file with these 2 records of 80 bytes
      | npi        | dueDate  |
      | 1003000222 | 20260228 |
      | 1003000333 | 20260331 |

  @AC-7
  Scenario: A run that opens no case returns an empty file
    Given revalidation-service tracks provider "1003000777" as status "A" with revalidation due date "2026-04-01"
    When the revalidation sweep runs for "2026-03-31"
    Then the REVALQ file of the last run has 0 records

  @AC-7
  Scenario Outline: Only ADMIN reads the REVALQ file
    Given revalidation-service tracks provider "1003000222" as status "A" with revalidation due date "2026-02-28"
    And the revalidation sweep runs for "2026-03-31"
    When "<role>" reads the REVALQ file of the last run
    Then the file answer is <status>

    Examples:
      | role     | status |
      | ADMIN    | 200    |
      | SYSTEM   | 403    |
      | ANALYST  | 403    |
      | SUPERVISOR | 403  |
      | PROVIDER | 403    |
      | NONE     | 401    |

  @AC-7
  Scenario: An unknown run has no file
    When "ADMIN" reads the REVALQ file of run "7d2f9c3a-1b84-4e5d-8f60-5a9c1e2b3d44"
    Then the file answer is 404

  @AC-8
  Scenario: A completed run reports how many cases it queued after evaluating every tracked provider
    Given revalidation-service tracks provider "1003000222" as status "A" with revalidation due date "2026-02-28"
    And revalidation-service tracks provider "1003000333" as status "A" with revalidation due date "2026-03-31"
    And revalidation-service tracks provider "1003000666" as status "A" with no revalidation due date
    And revalidation-service tracks provider "1003000777" as status "A" with revalidation due date "2026-04-01"
    When the revalidation sweep runs for "2026-03-31"
    Then "ADMIN" reads the last run as a BatchRun with status "COMPLETED", queued 2, noDueDate 1, business date "2026-03-31", a startedAt, an endedAt and no error

  @AC-8
  Scenario Outline: Only ADMIN reads a run
    Given revalidation-service tracks provider "1003000222" as status "A" with revalidation due date "2026-02-28"
    And the revalidation sweep runs for "2026-03-31"
    When "<role>" reads the last run
    Then the run answer is <status>

    Examples:
      | role     | status |
      | ADMIN    | 200    |
      | SYSTEM   | 403    |
      | ANALYST  | 403    |
      | SUPERVISOR | 403  |
      | PROVIDER | 403    |
      | NONE     | 401    |

  @AC-8
  Scenario: An unknown run is not found
    When "ADMIN" reads the run "7d2f9c3a-1b84-4e5d-8f60-5a9c1e2b3d44"
    Then the run answer is 404

  @AC-8
  Scenario: A run whose database write fails is FAILED with the reason and never COMPLETED
    Given revalidation-service tracks provider "1003000222" as status "A" with revalidation due date "2026-02-28"
    And writing a revalidation case fails with "SYNTHETIC DISK FULL"
    When the revalidation sweep is started for "2026-03-31" and finishes
    Then "ADMIN" reads the last run as a BatchRun with status "FAILED" and an error containing "SYNTHETIC DISK FULL"
    And NPI "1003000222" has no revalidation case
    And no RevalidationDue event is written for NPI "1003000222"
    And the REVALQ file of the last run is not found

  @AC-8
  Scenario: After a failed run the next run still works
    Given revalidation-service tracks provider "1003000222" as status "A" with revalidation due date "2026-02-28"
    And writing a revalidation case fails with "SYNTHETIC DISK FULL"
    And the revalidation sweep is started for "2026-03-31" and finishes
    And writing a revalidation case works again
    When the revalidation sweep runs for "2026-03-31"
    Then "ADMIN" reads the last run as a BatchRun with status "COMPLETED", queued 1, noDueDate 0, business date "2026-03-31", a startedAt, an endedAt and no error
    And NPI "1003000222" has exactly one open case with due date "2026-02-28"

  @AC-7
  Scenario: The file of an earlier run stays available after a later run
    Given revalidation-service tracks provider "1003000222" as status "A" with revalidation due date "2026-02-28"
    And the revalidation sweep runs for "2026-03-31"
    And the revalidation sweep runs for "2026-04-30"
    Then the REVALQ file of run 1 has 1 record
    And the REVALQ file of run 2 has 0 records

  @AC-8
  Scenario: The reason of a failed run is short and has no SQL text
    Given revalidation-service tracks provider "1003000222" as status "A" with revalidation due date "2026-02-28"
    And writing a revalidation case fails with "SYNTHETIC DISK FULL"
    When the revalidation sweep is started for "2026-03-31" and finishes
    Then "ADMIN" reads the last run as a FAILED BatchRun with a short reason without SQL text
