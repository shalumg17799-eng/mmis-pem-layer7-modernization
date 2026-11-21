@MAM-71
Feature: Monthly T-MSIS provider extract in the legacy 120-byte layout (YYDDD period)
  POST /api/v1/batch/tmsis-extracts runs the Spring Batch job PRVTMS01 for an explicit business date (D-020); GET
  .../{runId}/file downloads the TMSISOUT file: one 120-byte record per ACTIVE provider (PRV01, Medicaid ID, NPI, status
  A, the period as YYDDD of the business date, 90 spaces), in ascending NPI order, no header or trailer (D-015, ADR-005).
  The providers are read page after page through provider-service's API with the SYSTEM token (D-014, ADR-002). Since
  provider-service 1.2.0 (Q-132, user decision 2026-11-20) the pages are walked by key: the first request has no key, each
  next one is after the last NPI of the answer before, so a provider approved or terminated during the run cannot be missed
  or repeated; a run that finds its provider data changed ends FAILED instead of writing a wrong file.

  @AC-1
  Scenario: Only ACTIVE providers are reported, in ascending NPI order
    Given provider-service holds these providers:
      | npi        | medicaidId | status | effectiveDate |
      | 1003000333 | M00000333  | A      | 2026-01-15    |
      | 1003000444 | M00000444  | D      | 2026-01-15    |
      | 1003000555 | M00000555  | P      | 2026-01-15    |
      | 1003000111 | M00000111  | A      | 2026-01-15    |
      | 1003000777 | M00000777  | S      | 2026-01-15    |
      | 1003000888 | M00000888  | T      | 2026-01-15    |
    When the ADMIN runs the extract for "2026-03-31" and downloads the file
    Then the file has exactly 2 records of 120 bytes each and nothing else
    And record 1 of the file is for the NPI "1003000111"
    And record 2 of the file is for the NPI "1003000333"
    And the file holds no record for the NPIs "1003000444,1003000555,1003000777,1003000888"

  @AC-1
  Scenario: Providers of every status in one answer are filtered by the extract itself
    Given provider-service holds these providers:
      | npi        | medicaidId | status | effectiveDate |
      | 1003000111 | M00000111  | A      | 2026-01-15    |
      | 1003000444 | M00000444  | D      | 2026-01-15    |
      | 1003000777 | M00000777  | S      | 2026-01-15    |
    And provider-service ignores the status filter of the search
    When the ADMIN runs the extract for "2026-03-31" and downloads the file
    Then the file has exactly 1 record of 120 bytes each and nothing else
    And record 1 of the file is for the NPI "1003000111"

  @AC-2
  Scenario: The record has the legacy 120-byte layout
    Given provider-service holds these providers:
      | npi        | medicaidId | status | effectiveDate |
      | 1003000111 | M00000111  | A      | 2026-01-15    |
    When the ADMIN runs the extract for "2026-03-31" and downloads the file
    Then the file has exactly 1 record of 120 bytes each and nothing else
    And bytes 1 to 5 of record 1 are "PRV01"
    And bytes 6 to 14 of record 1 are "M00000111"
    And bytes 15 to 24 of record 1 are "1003000111"
    And bytes 25 to 25 of record 1 are "A"
    And bytes 26 to 30 of record 1 are "26090"
    And bytes 31 to 120 of record 1 are all spaces

  @AC-3
  Scenario: The period is the business date as an explicit YYDDD field
    Given the system clock shows "2026-05-10T09:00:00Z"
    And provider-service holds these providers:
      | npi        | medicaidId | status | effectiveDate |
      | 1003000111 | M00000111  | A      | 2026-01-15    |
      | 1003000333 | M00000333  | A      | 2026-01-15    |
    When the ADMIN runs the extract for "2026-03-31" and downloads the file
    Then the file has exactly 2 records of 120 bytes each and nothing else
    And every record of the file has the period "26090"

  @AC-4
  Scenario Outline: The period names a day, not a month, and handles edge dates
    Given provider-service holds these providers:
      | npi        | medicaidId | status | effectiveDate |
      | 1003000111 | M00000111  | A      | 2026-01-15    |
    When the ADMIN runs the extract for "<date>" and downloads the file
    Then every record of the file has the period "<period>"

    Examples:
      | date       | period |
      | 2026-01-05 | 26005  |
      | 2026-03-30 | 26089  |
      | 2026-03-31 | 26090  |
      | 2028-12-31 | 28366  |

  @AC-5
  Scenario: Every ACTIVE provider is read, however many there are
    Given provider-service holds 450 ACTIVE providers with the NPIs from "1003001001" to "1003001450"
    When the ADMIN runs the extract for "2026-03-31" and downloads the file
    Then the file has exactly 450 records of 120 bytes each and nothing else
    And the NPIs of the file are ascending and each appears once
    And provider-service was asked in 3 requests of the size 200, the first without a key and the others after the NPIs "1003001200,1003001400"

  @AC-6
  Scenario: Providers are read through provider-service's API
    Given provider-service holds 450 ACTIVE providers with the NPIs from "1003001001" to "1003001450"
    When the ADMIN runs the extract for "2026-03-31" and downloads the file
    Then provider-service received 3 searches with the status "A", page after page, authenticated with the SYSTEM service token
    And tmsis-service holds no provider table of its own

  @AC-5
  Scenario: A provider terminated while the run reads the providers fails the run instead of giving a file with a hole
    Given provider-service holds 401 ACTIVE providers with the NPIs from "1003001001" to "1003001401"
    And provider-service terminates the provider "1003001005" after its first answer
    When the ADMIN starts the extract for "2026-03-31" and waits for the run to end
    Then the run is listed as "FAILED" with an end time and a non-empty error text
    And the error text of the run is "provider data changed during the extract; run it again"
    And downloading the file of that run answers 404
    And no archive generation was added
    And no ExtractProduced event was published

  @AC-5
  Scenario: A provider approved before the boundary fails the run instead of writing the boundary provider twice
    Given provider-service holds 400 ACTIVE providers with the NPIs from "1003001201" to "1003001600"
    And provider-service also holds the PENDING provider "1003001100"
    And provider-service approves the provider "1003001100" after its first answer
    When the ADMIN starts the extract for "2026-03-31" and waits for the run to end
    Then the run is listed as "FAILED" with an end time and a non-empty error text
    And the error text of the run is "provider data changed during the extract; run it again"
    And downloading the file of that run answers 404
    And no archive generation was added
    And no ExtractProduced event was published

  @AC-5
  Scenario: One provider leaves and one enters while the run reads, the total stays the same: by key every provider is read once
    Given provider-service holds 401 ACTIVE providers with the NPIs from "1003001001" to "1003001401"
    And provider-service also holds the PENDING provider "1003009999"
    And provider-service terminates the provider "1003001005" after its first answer
    And provider-service approves the provider "1003009999" after its first answer
    When the ADMIN runs the extract for "2026-03-31" and downloads the file
    Then the file has exactly 402 records of 120 bytes each and nothing else
    And the NPIs of the file are ascending and each appears once
    And the file holds a record for the NPIs "1003001201,1003009999"

  @AC-5
  Scenario: A provider-service that ignores the key fails the run instead of reading the first page again and again
    Given provider-service holds 450 ACTIVE providers with the NPIs from "1003001001" to "1003001450"
    And provider-service ignores the key of the search, as a provider-service older than 1.2.0 does
    When the ADMIN starts the extract for "2026-03-31" and waits for the run to end
    Then the run is listed as "FAILED" with an end time and a non-empty error text
    And downloading the file of that run answers 404
    And no archive generation was added
    And no ExtractProduced event was published

  @AC-5
  Scenario: One provider leaves and one enters after the first answer, the total stays the same: the last provider is not lost behind a full answer
    Given provider-service holds 400 ACTIVE providers with every second NPI from "1003002002" to "1003002800"
    And provider-service also holds the PENDING provider "1003002501"
    And provider-service terminates the provider "1003002010" after its first answer
    And provider-service approves the provider "1003002501" after its first answer
    When the ADMIN runs the extract for "2026-03-31" and downloads the file
    Then the file has exactly 401 records of 120 bytes each and nothing else
    And the NPIs of the file are ascending and each appears once
    And the file holds a record for the NPIs "1003002010,1003002501,1003002800"
    And provider-service was asked in 3 requests of the size 200, the first without a key and the others after the NPIs "1003002400,1003002798"

  @AC-5
  Scenario: An exact multiple of 200 providers ends with the empty answer, so the last provider is read and the walk ends
    Given provider-service holds 400 ACTIVE providers with the NPIs from "1003001001" to "1003001400"
    When the ADMIN runs the extract for "2026-03-31" and downloads the file
    Then the file has exactly 400 records of 120 bytes each and nothing else
    And the NPIs of the file are ascending and each appears once
    And provider-service was asked in 3 requests of the size 200, the first without a key and the others after the NPIs "1003001200,1003001400"
