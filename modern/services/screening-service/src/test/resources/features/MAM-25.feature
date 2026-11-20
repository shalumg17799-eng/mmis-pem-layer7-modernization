@MAM-25
Feature: Decide the screening verdict, store the results per source and publish ScreeningCompleted
  Any result that is not P fails the screening (R-PRVSCR01-cbl-00010, D-005). One stored result per source, never
  overwritten (D-018). The verdict is announced on pem.screening.events.

  Background:
    Given the sources answer clean by default and the licence board knows no licence

  @AC-1
  Scenario: All four sources pass: the screening passes
    Given provider-service holds the individual "MARIA" "RIVERA" with NPI "1003000111", taxonomy "207Q00000X" and licence "MD12345" "TX"
    And the licence board answers "ACTIVE" until "2028-09" for licence "MD12345" "TX"
    When provider-service publishes ProviderRegistered for NPI "1003000111" and screening-service has processed it
    Then reading the screening of NPI "1003000111" by id gives status "COMPLETED", result "PASS" and a completion time

  @AC-2
  Scenario Outline: One failing source fails the screening, whichever it is
    Given provider-service holds the individual "MARIA" "RIVERA" with NPI "1003000444", taxonomy "207Q00000X" and licence "MD12345" "TX"
    And the licence board answers "ACTIVE" until "2028-09" for licence "MD12345" "TX"
    And only the source "<source>" fails for NPI "1003000444" named "MARIA RIVERA" with licence "MD12345" "TX"
    When provider-service publishes ProviderRegistered for NPI "1003000444" and screening-service has processed it
    Then reading the screening of NPI "1003000444" by id gives status "COMPLETED", result "FAIL" and a completion time
    And the "<source>" result for NPI "1003000444" is "F" and the other three are "P"
    Examples:
      | source   |
      | LICBOARD |
      | OIGLEIE  |
      | SAMGOV   |
      | SSADMF   |

  @AC-3
  Scenario: One stored result per source, with an error code only where one applies
    Given provider-service holds the individual "PAT" "SAMPLE" with NPI "1003000555", taxonomy "225100000X" and licence "PT99900" "TX"
    And the licence board answers "EXPIRED" until "2024-01" for licence "PT99900" "TX"
    When provider-service publishes ProviderRegistered for NPI "1003000555" and screening-service has processed it
    Then the screening of NPI "1003000555" read by id holds exactly 4 results, one per source, each with a result, a verdict time and a detail
    And only the "LICBOARD" result of NPI "1003000555" carries an error code, "E202"

  @AC-4
  Scenario: Results are kept and never overwritten
    Given provider-service holds the individual "PAT" "SAMPLE" with NPI "1003000555", taxonomy "225100000X" and licence "PT99900" "TX"
    And the licence board answers "EXPIRED" until "2024-01" for licence "PT99900" "TX"
    When provider-service publishes ProviderRegistered for NPI "1003000555" and screening-service has processed it
    And the first screening of NPI "1003000555" is remembered
    And the licence board answers "ACTIVE" until "2029-12" for licence "PT99900" "TX"
    And provider-service publishes ProviderRegistered for NPI "1003000555" again and screening-service has processed it
    Then the analyst sees 2 screenings for NPI "1003000555", each with its own 4 results
    And the remembered screening of NPI "1003000555" is unchanged

  @AC-5
  Scenario: ScreeningCompleted is published for every completed screening
    Given provider-service holds the individual "MARIA" "RIVERA" with NPI "1003000111", taxonomy "207Q00000X" and licence "MD12345" "TX"
    And the licence board answers "ACTIVE" until "2028-09" for licence "MD12345" "TX"
    When provider-service publishes ProviderRegistered for NPI "1003000111" and screening-service has processed it
    Then 1 ScreeningCompleted event is written for NPI "1003000111"
    And a ScreeningCompleted event with key "1003000111" reaches topic "pem.screening.events" with reason "INITIAL", result "PASS", taxonomy "207Q00000X" and the four sources with result and detail

  @AC-6
  Scenario: Failed screenings can be listed with the failing source
    Given a completed FAIL screening of NPI "1003000444" whose OIGLEIE result is "F" with error code "E203"
    When an ANALYST lists the screenings with result "FAIL"
    Then the page lists the screening of NPI "1003000444" with OIGLEIE "F" and error code "E203", and has items, page, size and total

  @AC-7
  Scenario: One screening can be read, an unknown id is not found
    Given a completed FAIL screening of NPI "1003000444" whose OIGLEIE result is "F" with error code "E203"
    Then reading that screening by id answers 200 and an unknown id answers 404 with problem+json

  @AC-8
  Scenario Outline: Only staff can read screenings
    Given a completed FAIL screening of NPI "1003000444" whose OIGLEIE result is "F" with error code "E203"
    Then "<role>" gets <status> from the screening list and from reading that screening
    Examples:
      | role       | status |
      | ANALYST    | 200    |
      | SUPERVISOR | 200    |
      | ADMIN      | 200    |
      | PROVIDER   | 403    |
      | SYSTEM     | 403    |
      | NONE       | 401    |
