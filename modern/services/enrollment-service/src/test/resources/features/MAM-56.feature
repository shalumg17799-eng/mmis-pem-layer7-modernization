@MAM-56
Feature: Run the legacy APPINPUT intake as a batch job with one error row per rejected application
  An administrator or a scheduler loads the old fixed-width daily application file for a stated business date and batch
  identifier (PRVENR01 as a Spring Batch job). Each application is checked exactly like an online one, a bad
  application never stops the rest of the file, and every rejected application is listed once in the APPERROR file in
  the old layout (D-001, D-016, D-017, D-020, D-023, D-025).

  Background:
    Given no intake runs or applications exist

  @AC-1
  Scenario: An intake run starts with an explicit business date and batch id
    Given an APPINPUT file of valid records for NPIs "1003000111,1003000222,1003000333"
    When an ADMIN starts an intake run with business date "2026-03-31" and batch id "B0331001"
    Then the intake answer is 202 with a run id, job "PRVENR01", business date "2026-03-31" and status RUNNING or COMPLETED
    And reading the run again shows business date "2026-03-31"
    And the log contains the line "PRVENR01: STARTING ENROLLMENT LOAD - BATCH B0331001"

  @AC-2
  Scenario: A missing batch id defaults to DEFBATCH
    Given an APPINPUT file of valid records for NPIs "1003000111,1003000222,1003000333"
    When an ADMIN starts an intake run with business date "2026-03-31" and no batch id
    Then the intake answer is 202 with a run id, job "PRVENR01", business date "2026-03-31" and status RUNNING or COMPLETED
    And the log contains the line "PRVENR01: STARTING ENROLLMENT LOAD - BATCH DEFBATCH"

  @AC-2
  Scenario: A batch id of blanks defaults to DEFBATCH
    Given an APPINPUT file of valid records for NPIs "1003000111,1003000222,1003000333"
    When an ADMIN starts an intake run with business date "2026-03-31" and a batch id of blanks
    Then the intake answer is 202 with a run id, job "PRVENR01", business date "2026-03-31" and status RUNNING or COMPLETED
    And the log contains the line "PRVENR01: STARTING ENROLLMENT LOAD - BATCH DEFBATCH"

  @AC-3
  Scenario: Each record is read as the 130-byte PRVRECRD in bytes 1-130
    Given an APPINPUT individual record for NPI "1003000111" with the legacy bytes 32-50 and the reserved bytes 131-200 filled
    And an APPINPUT group record for NPI "1003000444" with legal name "EXCLUDED CLINIC LLC", no DBA name and owner NPI "1990000111"
    When an ADMIN runs the file for business date "2026-03-31"
    Then the ANALYST sees one VALIDATED application for NPI "1003000111" with source "LEGACY_FILE", Medicaid ID "M00000111", type "IN", taxonomy "207Q00000X" and the individual RIVERA MARIA MD12345 TX
    And the ANALYST sees the application for NPI "1003000444" with legal name "EXCLUDED CLINIC LLC", no DBA name and owner NPI "1990000111"
    And neither the applications nor their ApplicationValidated events carry the reserved bytes or the tier, status, flag and dates of the record

  @AC-4
  Scenario: Each record gets the same validation as an online application
    Given a valid APPINPUT record for NPI "1003000111"
    And an APPINPUT record with a blank NPI
    And an APPINPUT record for NPI "1003000222" with type "XX"
    And an APPINPUT record for NPI "1003000333" with an empty taxonomy
    When an ADMIN runs the file for business date "2026-03-31"
    Then the ANALYST sees these applications:
      | npi        | status    | source      | errors            |
      | 1003000111 | VALIDATED | LEGACY_FILE |                   |
      |            | REJECTED  | LEGACY_FILE | E101/npi          |
      | 1003000222 | REJECTED  | LEGACY_FILE | E102/providerType |
      | 1003000333 | REJECTED  | LEGACY_FILE | E104/taxonomy     |
    And 1 "ApplicationValidated" event is published for NPI "1003000111"
    And 1 "ApplicationRejected" event is published for NPI "1003000222"
    And 1 "ApplicationRejected" event is published for NPI "1003000333"
    And exactly 3 events are published

  @AC-5
  Scenario: One rejected application does not stop the load
    Given a valid APPINPUT record for NPI "1003000111"
    And an APPINPUT record for NPI "1003000222" with an empty taxonomy
    And a valid APPINPUT record for NPI "1003000333"
    When an ADMIN runs the file for business date "2026-03-31"
    Then the applications were stored in the file order of NPIs "1003000111,1003000222,1003000333"
    And the ANALYST sees these applications:
      | npi        | status    | source      | errors        |
      | 1003000111 | VALIDATED | LEGACY_FILE |               |
      | 1003000222 | REJECTED  | LEGACY_FILE | E104/taxonomy |
      | 1003000333 | VALIDATED | LEGACY_FILE |               |
    And the run's counts.read is 3
    And 0 "ApplicationValidated" events are published for NPI "1003000222"

  @AC-6
  Scenario: The APPERROR file has exactly one row per rejected application
    Given a valid APPINPUT record for NPI "1003000111"
    And an APPINPUT record with a blank NPI
    And an APPINPUT record for NPI "1003000222" with type "XX" and an empty taxonomy
    And an APPINPUT record for NPI "1003000333" with an empty taxonomy
    When an ADMIN runs the file for business date "2026-03-31"
    And an ADMIN downloads the error file of the run
    Then the intake answer is 200
    And the error file has exactly these rows in input order:
      | npi        | type |
      |            | IN   |
      | 1003000222 | XX   |
      | 1003000333 | IN   |
    And every row of the error file is 250 characters in the layout "NPI TYPE:tt ERR:VALIDATION-FAILED" and spaces

  @AC-6
  Scenario: A run with no rejected application has an empty error file
    Given an APPINPUT file of valid records for NPIs "1003000111,1003000222"
    When an ADMIN runs the file for business date "2026-03-31"
    And an ADMIN downloads the error file of the run
    Then the intake answer is 200
    And the error file is empty

  @AC-7
  Scenario Outline: Only ADMIN and SYSTEM may start a run
    Given an APPINPUT file of valid records for NPIs "1003000111"
    When "<role>" starts an intake run for business date "2026-03-31"
    Then the intake answer is <status>
    And the intake answer is problem+json when it is not 202
    And the number of intake runs is <runs>

    Examples:
      | role       | status | runs |
      | ADMIN      | 202    | 1    |
      | SYSTEM     | 202    | 1    |
      | PROVIDER   | 403    | 0    |
      | ANALYST    | 403    | 0    |
      | SUPERVISOR | 403    | 0    |
      | NONE       | 401    | 0    |

  @AC-7
  Scenario Outline: Only ADMIN may download the error file
    Given an APPINPUT file of valid records for NPIs "1003000111"
    And an ADMIN has run the file for business date "2026-03-31"
    When "<role>" downloads the error file of the run
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
  Scenario: An unknown run has no error file
    When an ADMIN downloads the error file of the run "00000000-0000-0000-0000-000000000000"
    Then the intake answer is 404
    And the intake answer is problem+json when it is not 200

  @AC-7
  Scenario: A run without a business date is refused and no run is created
    Given an APPINPUT file of valid records for NPIs "1003000111"
    When an ADMIN starts an intake run without a business date
    Then the intake answer is 400
    And the intake answer is problem+json when it is not 202
    And the number of intake runs is 0

  @AC-7
  Scenario: A run without a file is refused and no run is created
    When an ADMIN starts an intake run without a file
    Then the intake answer is 400
    And the intake answer is problem+json when it is not 202
    And the number of intake runs is 0
