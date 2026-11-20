@MAM-75
Feature: T-MSIS extract run: archive every file, report counts, publish ExtractProduced
  An administrator starts an extract for a chosen business date, or the monthly schedule does with the Clock's date
  (ADR-004). Each run reports its row count, archives its file as the next generation (PRVMTH01 STEP030) and publishes
  ExtractProduced keyed by the run id (Q-061 B); a failed run is visible and leaves nothing behind (D-024); ADMIN and
  SYSTEM start, only ADMIN lists and downloads (D-023).

  @AC-1
  Scenario: An administrator starts an extract for a business date
    Given provider-service holds ACTIVE providers with the NPIs "1003000111,1003000333"
    When the ADMIN starts the extract for "2026-03-31"
    Then the start answers 202 with a run for the job "PRVTMS01" and business date "2026-03-31"
    And the status of the answer is RUNNING or COMPLETED
    And once the run has ended the list of runs shows that run id

  @AC-2
  Scenario: The run reports its result and row count
    Given provider-service holds ACTIVE providers with the NPIs "1003000111,1003000333"
    When the ADMIN starts the extract for "2026-03-31" and waits for the run to end
    And the ADMIN downloads the file of the run
    Then the run is listed as "COMPLETED" with an end time
    And the run counts 2 rows, as many as the records of its file

  @AC-2
  Scenario: A run over no providers has 0 rows and an empty file
    Given provider-service holds no providers
    When the ADMIN starts the extract for "2026-03-31" and waits for the run to end
    And the ADMIN downloads the file of the run
    Then the run is listed as "COMPLETED" with an end time
    And the run counts 0 rows, as many as the records of its file
    And the file is empty

  @AC-3
  Scenario: The run uses the business date, not the system date
    Given the system clock shows "2026-05-10T09:00:00Z"
    And provider-service holds ACTIVE providers with the NPIs "1003000111,1003000333"
    When the ADMIN starts the extract for "2026-03-31" and waits for the run to end
    And the ADMIN downloads the file of the run
    Then the run shows the business date "2026-03-31"
    And every record of the file has the period "26090"
    And no record of the file has the period "26130"

  @AC-4
  Scenario: Every run's file is archived as a new generation
    Given the archive is empty
    And provider-service holds ACTIVE providers with the NPIs "1003000111,1003000333"
    When the ADMIN runs the extract for "2026-02-28" and waits for it
    And the ADMIN runs the extract for "2026-03-31" and waits for it
    And the ADMIN runs the extract for "2026-03-31" and waits for it
    Then the three runs show the archive generations "1,2,3"
    And the file of every earlier run can still be downloaded unchanged after the later runs
    And the third file is byte-identical to the second

  @AC-5
  Scenario: ExtractProduced is published when the extract is archived
    Given provider-service holds ACTIVE providers with the NPIs "1003000111,1003000333"
    When the ADMIN starts the extract for "2026-03-31" and waits for the run to end
    Then exactly one ExtractProduced event is published on "pem.tmsis.events" with source "tmsis-service", the run id as its key, business date "2026-03-31", period "26090" and row count 2
    And the run was COMPLETED and archived when the event could be read

  @AC-6
  Scenario: A failed run is visible and leaves nothing behind
    Given provider-service holds ACTIVE providers with the NPIs "1003000111,1003000333"
    And provider-service answers every request with a server error
    When the ADMIN starts the extract for "2026-03-31" and waits for the run to end
    Then the run is listed as "FAILED" with an end time and a non-empty error text
    And downloading the file of that run answers 404
    And no archive generation was added
    And no ExtractProduced event was published

  @AC-6
  Scenario: A provider-service that does not answer ends the run FAILED within the timeout
    Given provider-service holds ACTIVE providers with the NPIs "1003000111,1003000333"
    And provider-service does not answer in time
    When the ADMIN starts the extract for "2026-03-31" and waits for the run to end
    Then the run is listed as "FAILED" with an end time and a non-empty error text
    And the run ended long before the answer of provider-service would have come
    And no archive generation was added

  @AC-7
  Scenario: The monthly schedule uses the Clock's date
    Given the system clock shows "2026-04-30T22:00:00Z"
    And provider-service holds ACTIVE providers with the NPIs "1003000111,1003000333"
    When the monthly schedule fires
    And the ADMIN downloads the file of the run
    Then the list shows a COMPLETED run with the business date "2026-04-30"
    And every record of the file has the period "26120"

  @AC-8
  Scenario Outline: Access and input checks by role
    Given provider-service holds ACTIVE providers with the NPIs "1003000111,1003000333"
    And the ADMIN has run the extract for "2026-03-31"
    When a caller with the role "<role>" starts the extract for "2026-03-31"
    And the same caller lists the runs and downloads the file of the earlier run
    Then the start answers <start>, the list answers <list> and the download answers <download>

    Examples:
      | role       | start | list | download |
      | ADMIN      | 202   | 200  | 200      |
      | SYSTEM     | 202   | 403  | 403      |
      | ANALYST    | 403   | 403  | 403      |
      | SUPERVISOR | 403   | 403  | 403      |
      | PROVIDER   | 403   | 403  | 403      |
      | none       | 401   | 401  | 401      |

  @AC-8
  Scenario: A start without a business date is rejected
    Given the number of extract runs is remembered
    When the ADMIN starts the extract with the body '{}'
    Then the extract answers 400 problem+json
    And no extract run was created

  @AC-8
  Scenario: A business date with a time part is not a date and is rejected
    Given the number of extract runs is remembered
    When the ADMIN starts the extract with the body '{"businessDate":"2026-03-31T00:00:00"}'
    Then the extract answers 400 problem+json
    And no extract run was created
