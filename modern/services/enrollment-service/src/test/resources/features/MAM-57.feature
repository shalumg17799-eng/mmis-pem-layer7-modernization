@MAM-57
Feature: Report the result of an intake run: exclusive counts, completion, failures and safe re-runs
  After an intake run, operations see how many applications were read, written, errored and skipped, and whether the
  run finished cleanly, finished with rejected applications, or failed and why. A run can be repeated for the same
  business date without loading anything twice (D-001, D-016, D-023, D-024).

  Background:
    Given no intake runs or applications exist

  @AC-1
  Scenario: A clean run reports its counts, and each run starts from zero
    Given an APPINPUT file of valid records for NPIs "1003000111,1003000222,1003000333"
    And an ADMIN has run the file for business date "2026-03-31"
    And an APPINPUT file of valid records for NPIs "1003000777"
    And an ADMIN has run the file for business date "2026-04-01"
    Then run 1 shows status "COMPLETED" with end time set, error null and counts read 3, written 3, errored 0, skipped 0
    And run 2 shows status "COMPLETED" with end time set, error null and counts read 1, written 1, errored 0, skipped 0

  @AC-2
  Scenario: Every record is counted exactly once
    Given a valid APPINPUT record for NPI "1003000111"
    And a valid APPINPUT record for NPI "1003000777"
    And an APPINPUT record with a blank NPI
    And an APPINPUT record for NPI "1003000222" with type "XX" and an empty taxonomy
    When an ADMIN runs the file for business date "2026-03-31"
    And an ADMIN downloads the error file of the run
    Then run 1 shows status "COMPLETED" with end time set, error null and counts read 4, written 2, errored 2, skipped 0
    And the counts of run 1 add up: read equals written plus errored plus skipped
    And the error file has 2 rows

  @AC-3
  Scenario: A duplicate NPI is counted as skipped
    Given a valid APPINPUT record for NPI "1003000111"
    And a valid APPINPUT record for NPI "1003000111"
    And a valid APPINPUT record for NPI "1003000222"
    When an ADMIN runs the file for business date "2026-03-31"
    And an ADMIN downloads the error file of the run
    Then the ANALYST sees these applications:
      | npi        | status    | source      | errors   |
      | 1003000111 | VALIDATED | LEGACY_FILE |          |
      | 1003000111 | REJECTED  | LEGACY_FILE | E103/npi |
      | 1003000222 | VALIDATED | LEGACY_FILE |          |
    And the error file has exactly these rows in input order:
      | npi        | type |
      | 1003000111 | IN   |
    And run 1 shows status "COMPLETED" with end time set, error null and counts read 3, written 2, errored 0, skipped 1
    And 1 "ApplicationValidated" event is published for NPI "1003000111"

  @AC-4
  Scenario: A run with rejected applications completes, not as a failure
    Given a valid APPINPUT record for NPI "1003000111"
    And an APPINPUT record for NPI "1003000222" with an empty taxonomy
    And a valid APPINPUT record for NPI "1003000333"
    When an ADMIN runs the file for business date "2026-03-31"
    Then run 1 shows status "COMPLETED" with end time set, error null and counts read 3, written 2, errored 1, skipped 0
    And the application for NPI "1003000111" is still "VALIDATED"
    And the application for NPI "1003000333" is still "VALIDATED"

  @AC-5
  Scenario: An unreadable record ends the run with a stated failure
    Given a valid APPINPUT record for NPI "1003000111"
    And a valid APPINPUT record for NPI "1003000222"
    And a valid APPINPUT record for NPI "1003000333"
    And a valid APPINPUT record for NPI "1003000777"
    And the third record is cut to 150 bytes
    When an ADMIN runs the file for business date "2026-03-31"
    Then run 1 ends FAILED with an error naming record 3 and an end time
    And run 1 shows counts read 3, written 2, errored 1, skipped 0
    And the application for NPI "1003000111" is still "VALIDATED"
    And the application for NPI "1003000222" is still "VALIDATED"
    And 0 applications are stored for NPI "1003000777"

  @AC-6
  Scenario: A hard failure is never shown as a warning
    Given an APPINPUT record for NPI "1003000222" with an empty taxonomy
    And a valid APPINPUT record for NPI "1003000111"
    And a valid APPINPUT record for NPI "1003000333"
    And the database rejects the storage of an application for NPI "1003000111"
    When an ADMIN runs the file for business date "2026-03-31"
    And an ADMIN downloads the error file of the run
    Then run 1 ends FAILED with an error naming record 2 and NPI "1003000111" and an end time
    And run 1 shows counts read 2, written 0, errored 2, skipped 0
    And the application for NPI "1003000222" is still "REJECTED"
    And the error file has exactly these rows in input order:
      | npi        | type |
      | 1003000222 | IN   |
    And 0 "ApplicationValidated" events are published for NPI "1003000111"
    And 0 applications are stored for NPI "1003000333"

  @AC-7
  Scenario Outline: Run results are for ADMIN only
    Given an APPINPUT file of valid records for NPIs "1003000111"
    And an ADMIN has run the file for business date "2026-03-31"
    When "<role>" reads the run
    Then the intake answer is <status>
    And the intake answer is problem+json when it is not 200

    Examples:
      | role       | status |
      | ADMIN      | 200    |
      | SYSTEM     | 403    |
      | PROVIDER   | 403    |
      | ANALYST    | 403    |
      | SUPERVISOR | 403    |
      | NONE       | 401    |

  @AC-7
  Scenario: An unknown run is not found
    When an ADMIN reads the run "00000000-0000-0000-0000-000000000000"
    Then the intake answer is 404
    And the intake answer is problem+json when it is not 200

  @AC-8
  Scenario: Re-running a business date loads nothing twice
    Given a valid APPINPUT record for NPI "1003000111"
    And a valid APPINPUT record for NPI "1003000333"
    And an APPINPUT record for NPI "1003000222" with an empty taxonomy
    And a valid APPINPUT record for NPI "1003000777"
    And the database rejects the storage of an application for NPI "1003000777"
    When an ADMIN runs the file for business date "2026-03-31"
    Then run 1 ends FAILED with an error naming record 4 and NPI "1003000777" and an end time
    When the database accepts the storage again
    And an ADMIN runs the same file again for business date "2026-03-31"
    And an ADMIN downloads the error file of the run
    Then run 2 shows status "COMPLETED" with end time set, error null and counts read 4, written 1, errored 0, skipped 3
    And the error file is empty
    And 1 "ApplicationValidated" event is published for NPI "1003000777"
    And 1 application is stored for NPI "1003000111"
    And 1 application is stored for NPI "1003000333"
    And 1 application is stored for NPI "1003000222"
    When an ADMIN runs the same file again for business date "2026-03-31"
    Then run 3 shows status "COMPLETED" with end time set, error null and counts read 4, written 0, errored 0, skipped 4
    And exactly 4 events are published
