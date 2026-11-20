@MAM-32
Feature: Run screening as a batch job and produce the legacy SCRNOUT file
  PRVSCR01 as a Spring Batch job for an explicit business date (D-020): every provider that is still unscreened (screen
  flag U) and has no INITIAL screening yet is screened, four 67-byte SCRNOUT rows are written per screened provider
  (SCRNREC, ADR-005, question C-7), and the run records how many providers it read, screened, skipped and failed. A
  failure is reported, never silent (D-024).

  Background:
    Given the sources answer clean by default and the licence board knows no licence
    And provider-service lists these providers
      | npi        | screenFlag | licence | state |
      | 1003000111 | U          | MD12345 | TX    |
      | 1003000555 | U          | PT99900 | TX    |
      | 1003000222 | P          | MD22222 | TX    |
      | 1003000444 | F          | MD44444 | TX    |
    And the licence board answers "ACTIVE" until "2028-09" for licence "MD12345" "TX"
    And the licence board answers "EXPIRED" until "2024-01" for licence "PT99900" "TX"

  @AC-1
  Scenario Outline: Only an administrator or the scheduler starts screening for a business date
    When "<role>" starts a screening run for business date "2026-03-31"
    Then the start answers <status>
    And the started runs have ended
    Examples:
      | role       | status |
      | ADMIN      | 202    |
      | SYSTEM     | 202    |
      | ANALYST    | 403    |
      | SUPERVISOR | 403    |
      | PROVIDER   | 403    |
      | NONE       | 401    |

  @AC-1
  Scenario: The 202 answer is the RUNNING BatchRun of the screening job
    When "ADMIN" starts a screening run for business date "2026-03-31"
    Then the start answers 202 with a BatchRun for job "PRVSCR01", business date "2026-03-31" and status "RUNNING"
    And the started runs have ended

  @AC-1
  Scenario: A request without a business date is refused
    When "ADMIN" starts a screening run without a business date
    Then the start answers 400 problem+json
    And no screening run is stored

  @AC-2
  Scenario: Only unscreened providers are screened and every page is read once
    When ADMIN runs the screening for business date "2026-03-31" and waits for its end
    Then the run has created INITIAL screenings for exactly "1003000111,1003000555"
    And no screening exists for "1003000222" or "1003000444"
    And provider-service was asked for page "0" of the provider list exactly once
    And provider-service was not asked for any other page of the provider list

  @AC-2
  Scenario: A provider list of several pages is read page by page until no page remains
    Given provider-service lists 401 providers of which 3 are unscreened
    When ADMIN runs the screening for business date "2026-03-31" and waits for its end
    Then provider-service was asked for the pages "0,1,2" of the provider list, each exactly once
    And the run is "COMPLETED" with counts read 401, screened 3, skipped 398 and failed 3

  @AC-3
  Scenario: The SCRNOUT file has four 67-byte rows per screened provider
    When ADMIN runs the screening for business date "2026-03-31" and waits for its end
    And ADMIN downloads the results file of the run
    Then the results file has 8 records of 67 bytes
    And the results file lists the providers "1003000111,1003000555" in ascending NPI order, each with its four rows in the order LICBOARD, OIGLEIE, SAMGOV and SSADMF
    And every record of the results file repeats the stored result of its source with the verdict date "20260331", the source left-justified and the detail cut to 40 characters

  @AC-4
  Scenario: A failed provider's rows show the failing source
    When ADMIN runs the screening for business date "2026-03-31" and waits for its end
    And ADMIN downloads the results file of the run
    Then the results file row of "1003000555" for "LICBOARD" has result "F"
    And the results file rows of "1003000555" for "OIGLEIE", "SAMGOV" and "SSADMF" have result "P"
    And the results file rows of "1003000111" all have result "P"

  @AC-5
  Scenario: The run records how many providers it read, screened, skipped and failed
    When ADMIN runs the screening for business date "2026-03-31" and waits for its end
    Then the run is "COMPLETED" with counts read 4, screened 2, skipped 2 and failed 1
    And the run has an end time and no error

  @AC-6
  Scenario: A run during which provider-service answers 5xx is FAILED and stops reading
    Given provider-service answers HTTP 503 for the provider list
    When ADMIN runs the screening for business date "2026-03-31" and waits for its end
    Then the run is "FAILED" with a short reason in error and an end time
    And provider-service was asked for the provider list at most 3 times
    And the run has no results file

  @AC-6
  Scenario: A run whose SCRNOUT file cannot be written is FAILED, never COMPLETED
    Given the database rejects the writing of the SCRNOUT file
    When ADMIN runs the screening for business date "2026-03-31" and waits for its end
    Then the run is "FAILED" with a short reason in error and an end time
    And the run has no results file

  @AC-7
  Scenario: Running again does not screen anyone twice
    Given ADMIN has run the screening for business date "2026-03-31" and the run has ended
    When ADMIN runs the screening for business date "2026-03-31" and waits for its end
    Then the second run has created no screening and no ScreeningCompleted event for "1003000111" or "1003000555"
    And the SCRNOUT file of the second run is empty
    And exactly 1 INITIAL screening and 1 ScreeningCompleted event exist for "1003000111"
    And exactly 1 INITIAL screening and 1 ScreeningCompleted event exist for "1003000555"
    And the run is "COMPLETED" with counts read 4, screened 0, skipped 4 and failed 0

  @AC-8
  Scenario Outline: Only an administrator can download the SCRNOUT file
    Given ADMIN has run the screening for business date "2026-03-31" and the run has ended
    When "<role>" downloads the results file of that run
    Then the download answers <status>
    Examples:
      | role     | status |
      | ADMIN    | 200    |
      | SYSTEM   | 403    |
      | ANALYST  | 403    |
      | PROVIDER | 403    |
      | NONE     | 401    |

  @AC-8
  Scenario: An unknown run id is not found
    When "ADMIN" downloads the results file of an unknown run
    Then the download answers 404 problem+json
