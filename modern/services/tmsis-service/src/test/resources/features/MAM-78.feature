@MAM-78
Feature: Warn about providers whose enrolment year is later than the business year
  While the T-MSIS file is prepared, an ACTIVE provider whose enrolment year is later than the year of the run's business
  date is logged with the legacy text 'PRVTMS01: FUTURE ENROLLMENT? NPI <npi>' and counted in counts.futureEnrolments. The
  warning never excludes the provider or changes its record. The years are compared with four digits (D-004, Q-062 B).

  @AC-1
  Scenario: A later enrolment year raises a warning that names the provider
    Given provider-service holds these providers:
      | npi        | medicaidId | status | effectiveDate |
      | 1003000777 | M00000777  | A      | 2027-01-15    |
    When the ADMIN starts the extract for "2026-03-31" and waits for the run to end
    Then the log holds exactly one line "PRVTMS01: FUTURE ENROLLMENT? NPI 1003000777"
    And the run counts 1 flagged future enrolments

  @AC-2
  Scenario: The flagged provider is still reported unchanged
    Given provider-service holds these providers:
      | npi        | medicaidId | status | effectiveDate |
      | 1003000777 | M00000777  | A      | 2027-01-15    |
    When the ADMIN starts the extract for "2026-03-31" and waits for the run to end
    And the ADMIN downloads the file of the run
    Then the file has exactly 1 record of 120 bytes each and nothing else
    And record 1 of the file is for the NPI "1003000777"
    And bytes 25 to 25 of record 1 are "A"
    And bytes 26 to 30 of record 1 are "26090"
    And the run counts 1 rows, as many as the records of its file

  @AC-3
  Scenario: Only a year strictly later than the business year is flagged
    Given provider-service holds these providers:
      | npi        | medicaidId | status | effectiveDate |
      | 1003000111 | M00000111  | A      | 2026-03-31    |
      | 1003000333 | M00000333  | A      | 2026-12-31    |
      | 1003000777 | M00000777  | A      | 2027-01-01    |
    When the ADMIN starts the extract for "2026-03-31" and waits for the run to end
    Then the log holds exactly one line "PRVTMS01: FUTURE ENROLLMENT? NPI 1003000777"
    And no warning line names the NPIs "1003000111,1003000333"
    And the run counts 1 flagged future enrolments

  @AC-4
  Scenario: Enrolments before the year 2000 are not flagged
    Given provider-service holds these providers:
      | npi        | medicaidId | status | effectiveDate |
      | 1003000333 | M00000333  | A      | 1998-05-01    |
    When the ADMIN starts the extract for "2026-03-31" and waits for the run to end
    And the ADMIN downloads the file of the run
    Then no warning line names the NPIs "1003000333"
    And the run counts 0 flagged future enrolments
    And record 1 of the file is for the NPI "1003000333"

  @AC-5
  Scenario: Years after 2099 are flagged
    Given provider-service holds these providers:
      | npi        | medicaidId | status | effectiveDate |
      | 1003000888 | M00000888  | A      | 2101-01-01    |
    When the ADMIN starts the extract for "2026-03-31" and waits for the run to end
    Then the log holds exactly one line "PRVTMS01: FUTURE ENROLLMENT? NPI 1003000888"
    And the run counts 1 flagged future enrolments

  @AC-6
  Scenario: Only ACTIVE providers are checked
    Given provider-service holds these providers:
      | npi        | medicaidId | status | effectiveDate |
      | 1003000555 | M00000555  | P      | 2027-01-01    |
    And provider-service ignores the status filter of the search
    When the ADMIN starts the extract for "2026-03-31" and waits for the run to end
    And the ADMIN downloads the file of the run
    Then the file is empty
    And no warning line names the NPIs "1003000555"
    And the run counts 0 flagged future enrolments
