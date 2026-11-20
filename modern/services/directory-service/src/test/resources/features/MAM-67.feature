@MAM-67
Feature: Directory extract as a batch job in the legacy DIROUT format
  POST /api/v1/batch/directory-extracts runs the Spring Batch job PRVDIR01 for an explicit business date (D-020) and
  answers the finished run; GET .../{runId}/file downloads the DIROUT file: 100-byte records side by side, ACTIVE
  entries only, in ascending NPI order, with the composed name (D-028); a failed run is visible and has no file
  (D-024); ADMIN and SYSTEM start, only ADMIN reads and downloads (D-023).

  @AC-1
  Scenario: An administrator runs the extract and sees the result
    Given the directory holds:
      | npi        | medicaidId | type | tier | status | name               | taxonomy   |
      | 1003000111 | M00000111  | IN   | L    | A      | MARIA RIVERA       | 207Q00000X |
      | 1003000333 | M00000333  | FA   | H    | A      | SUNSET HOSPICE INC | 251G00000X |
    When the ADMIN starts the directory extract for "2026-03-31"
    Then the extract answers 202 with a run for the job "PRVDIR01", business date "2026-03-31" and status "COMPLETED"
    And the run has a run id, a start time and an end time
    And the run counts 2 rows
    And the downloaded file of the run holds 2 records, as many as the rows counted

  @AC-2
  Scenario: The business date is an explicit parameter
    Given the number of directory extract runs is remembered
    When the ADMIN starts the directory extract with the body '{}'
    Then the extract answers 400 problem+json
    When the ADMIN starts the directory extract with the body '{"businessDate":"31/03/2026"}'
    Then the extract answers 400 problem+json
    And no directory extract run was created

  @AC-3
  Scenario: Only ACTIVE entries are written, one record each, in NPI order
    Given the directory holds:
      | npi        | medicaidId | type | tier | status | name               | taxonomy   |
      | 1003000333 | M00000333  | FA   | H    | A      | SUNSET HOSPICE INC | 251G00000X |
      | 1003000111 | M00000111  | IN   | L    | A      | MARIA RIVERA       | 207Q00000X |
      | 1003000777 | M00000777  | FA   | H    | S      | SUSPENDED HOSPICE  | 251G00000X |
      | 1003000444 | M00000444  | GR   |      | D      | EXCLUDED CLINIC    | 251E00000X |
      | 1003000555 | M00000555  | IN   |      | P      | PAT SAMPLE         | 225100000X |
    When the ADMIN starts the directory extract for "2026-03-31"
    And the ADMIN downloads the file of the run
    Then the file has exactly 2 records of 100 bytes each and nothing else
    And record 1 of the file is for the NPI "1003000111"
    And record 2 of the file is for the NPI "1003000333"
    And the file holds no record for the NPIs "1003000777,1003000444,1003000555"

  @AC-3
  Scenario: With no ACTIVE entry the run is COMPLETED with 0 rows and an empty file
    Given the directory holds:
      | npi        | medicaidId | type | tier | status | name            | taxonomy   |
      | 1003000555 | M00000555  | IN   |      | P      | PAT SAMPLE      | 225100000X |
      | 1003000444 | M00000444  | GR   |      | D      | EXCLUDED CLINIC | 251E00000X |
    When the ADMIN starts the directory extract for "2026-03-31"
    And the ADMIN downloads the file of the run
    Then the extract answers 202 with a run for the job "PRVDIR01", business date "2026-03-31" and status "COMPLETED"
    And the run counts 0 rows
    And the file is empty

  @AC-4
  Scenario: The record has the legacy DIROUT layout
    Given the directory holds:
      | npi        | medicaidId | type | tier | status | name         | taxonomy   |
      | 1003000111 | M00000111  | IN   | L    | A      | MARIA RIVERA | 207Q00000X |
    When the ADMIN starts the directory extract for "2026-03-31"
    And the ADMIN downloads the file of the run
    Then the file has exactly 1 record of 100 bytes each and nothing else
    And bytes 1 to 9 of record 1 are "M00000111"
    And bytes 10 to 19 of record 1 are "1003000111"
    And bytes 20 to 21 of record 1 are "IN"
    And bytes 22 to 22 of record 1 are "L"
    And bytes 83 to 100 of record 1 are all spaces

  @AC-5
  Scenario: The name field holds the composed name
    Given provider-service holds the individual "MARIA" "RIVERA" with NPI "1003000111", Medicaid ID "M00000111", taxonomy "207Q00000X" and licence "MD12345" "TX"
    And provider-service holds the facility "SUNSET HOSPICE INC" doing business as "SUNSET HOSPICE CARE" with NPI "1003000333", Medicaid ID "M00000333" and taxonomy "251G00000X"
    When directory-service consumes ProviderStatusChanged for NPI "1003000111" from "P" to "A"
    And directory-service consumes ProviderStatusChanged for NPI "1003000333" from "P" to "A"
    And the ADMIN starts the directory extract for "2026-03-31"
    And the ADMIN downloads the file of the run
    Then the file has exactly 2 records of 100 bytes each and nothing else
    And the 60-byte name of record 1 is "MARIA RIVERA" followed by spaces
    And the 60-byte name of record 2 is "SUNSET HOSPICE INC" followed by spaces
    And no record of the file contains "MD12345"
    And no record of the file contains "SUNSET HOSPICE CARE"

  @AC-6
  Scenario: Running again for the same date creates a new run with an identical file
    Given the directory holds:
      | npi        | medicaidId | type | tier | status | name               | taxonomy   |
      | 1003000111 | M00000111  | IN   | L    | A      | MARIA RIVERA       | 207Q00000X |
      | 1003000333 | M00000333  | FA   | H    | A      | SUNSET HOSPICE INC | 251G00000X |
    When the ADMIN starts the directory extract for "2026-03-31"
    And the ADMIN starts the directory extract for "2026-03-31" again
    Then the two runs have different run ids
    And the file of the second run is byte-identical to the file of the first run
    And the file of the first run can still be downloaded

  @AC-7
  Scenario: A failed run is visible and produces no file
    Given the directory holds:
      | npi        | medicaidId | type | tier | status | name         | taxonomy   |
      | 1003000111 | M00000111  | IN   | L    | A      | MARIA RIVERA | 207Q00000X |
    And the database rejects the write that completes a directory extract run
    When the ADMIN starts the directory extract for "2026-03-31"
    Then the extract answers 202 with a run for the job "PRVDIR01", business date "2026-03-31" and status "FAILED"
    And the run has a non-empty error text and an end time
    And downloading the file of that run answers 404

  @AC-8
  Scenario Outline: Only the right roles can run and download
    Given the directory holds:
      | npi        | medicaidId | type | tier | status | name         | taxonomy   |
      | 1003000111 | M00000111  | IN   | L    | A      | MARIA RIVERA | 207Q00000X |
    And the ADMIN has started an earlier directory extract for "2026-03-31"
    When a caller with the role "<role>" starts the extract for "2026-03-31"
    And the same caller downloads the file of the earlier run
    Then the start answers <start> and the download answers <download>

    Examples:
      | role       | start | download |
      | ADMIN      | 202   | 200      |
      | SYSTEM     | 202   | 403      |
      | ANALYST    | 403   | 403      |
      | SUPERVISOR | 403   | 403      |
      | PROVIDER   | 403   | 403      |
      | no token   | 401   | 401      |
