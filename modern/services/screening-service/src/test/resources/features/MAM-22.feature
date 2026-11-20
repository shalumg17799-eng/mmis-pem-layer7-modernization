@MAM-22
Feature: Screen the owner of a group or facility (E203 owner excluded)
  A group or facility is only as trustworthy as its owner. The OIGLEIE source looks up the provider's own NPI and, for an
  organisation with an owner NPI, the owner as well (R-PRVSCR01-cbl-00033, D-016, D-005, question C-5). There is one
  OIGLEIE result per screening (question C-6): E203 when only the owner is excluded, E201 when the provider is.

  Background:
    Given the sources answer clean by default and the licence board knows no licence

  @AC-1
  Scenario: An excluded owner fails the group (E203)
    Given provider-service holds the organisation "EXCLUDED CLINIC LLC" of type "GR" with NPI "1003000444" and owner NPI "1990000111"
    And the LEIE lists NPI "1990000111" with exclusion type "1128(a)(1)" since "2023-01-15"
    When provider-service publishes ProviderRegistered for NPI "1003000444" and screening-service has processed it
    Then the "OIGLEIE" result for NPI "1003000444" is "F" with error code "E203" and a detail containing "1990000111"
    And the "OIGLEIE" result for NPI "1003000444" is "F" and the other three are "P"
    And only the "OIGLEIE" result of NPI "1003000444" carries an error code, "E203"
    And reading the screening of NPI "1003000444" by id gives status "COMPLETED", result "FAIL" and a completion time
    When an ANALYST lists the screenings with result "FAIL"
    Then the page lists the screening of NPI "1003000444" with OIGLEIE "F" and error code "E203", and has items, page, size and total
    And a ScreeningCompleted event with key "1003000444" reaches topic "pem.screening.events" with reason "INITIAL", result "FAIL", taxonomy "251G00000X" and the four sources with result and detail

  @AC-2
  Scenario: A clean owner passes, and both NPIs are looked up
    Given provider-service holds the organisation "NORTHSIDE CLINIC GR" of type "GR" with NPI "1003000222" and owner NPI "1990000999"
    When provider-service publishes ProviderRegistered for NPI "1003000222" and screening-service has processed it
    Then the "OIGLEIE" result for NPI "1003000222" is "P" with no error code and a detail containing "NOT EXCLUDED"
    And the request log of the "OIGLEIE" source shows 1 lookup for "1003000222"
    And the request log of the "OIGLEIE" source shows 1 lookup for "1990000999"
    And the request log of the "OIGLEIE" source shows 2 lookups in all

  @AC-3
  Scenario: Providers without an owner NPI are checked on their own NPI only
    Given provider-service holds the individual "MARIA" "RIVERA" with NPI "1003000111", taxonomy "207Q00000X" and licence "MD12345" "TX"
    And the licence board answers "ACTIVE" until "2028-09" for licence "MD12345" "TX"
    And provider-service holds the organisation "SUNSET HOSPICE INC" of type "FA" with NPI "1003000333" and no owner NPI
    When provider-service publishes ProviderRegistered for NPI "1003000111" and screening-service has processed it
    And provider-service publishes ProviderRegistered for NPI "1003000333" and screening-service has processed it
    Then the request log of the "OIGLEIE" source shows 1 lookup for "1003000111"
    And the request log of the "OIGLEIE" source shows 1 lookup for "1003000333"
    And the request log of the "OIGLEIE" source shows 2 lookups in all
    And the "OIGLEIE" result for NPI "1003000111" is "P" with no error code and a detail containing "NOT EXCLUDED"
    And the "OIGLEIE" result for NPI "1003000333" is "P" with no error code and a detail containing "NOT EXCLUDED"

  @AC-3
  Scenario Outline: A blank or empty owner NPI is no owner
    Given provider-service holds the organisation "SUNSET HOSPICE INC" of type "FA" with NPI "1003000333" and the owner NPI member <member>
    When provider-service publishes ProviderRegistered for NPI "1003000333" and screening-service has processed it
    Then the request log of the "OIGLEIE" source shows 1 lookup for "1003000333"
    And the request log of the "OIGLEIE" source shows 1 lookup in all
    And the "OIGLEIE" result for NPI "1003000333" is "P" with no error code and a detail containing "NOT EXCLUDED"
    Examples:
      | member         |
      | "blank"        |
      | "empty"        |
      | "null"         |

  @AC-4
  Scenario: A provider and its owner both on the LEIE report E201 naming both
    Given provider-service holds the organisation "EXCLUDED CLINIC LLC" of type "GR" with NPI "1003000444" and owner NPI "1990000111"
    And the LEIE lists NPI "1990000111" with exclusion type "1128(a)(1)" since "2023-01-15"
    And the LEIE lists NPI "1003000444" with exclusion type "1128(a)(1)" since "2024-02-01"
    When provider-service publishes ProviderRegistered for NPI "1003000444" and screening-service has processed it
    Then the single OIGLEIE result for NPI "1003000444" is "F" with error code "E201" and a detail naming "1003000444" and "1990000111"
    And reading the screening of NPI "1003000444" by id gives status "COMPLETED", result "FAIL" and a completion time

  @AC-5
  Scenario: An owner lookup that fails is recorded as not answered after one retry
    Given provider-service holds the organisation "NORTHSIDE CLINIC GR" of type "GR" with NPI "1003000222" and owner NPI "1990000999"
    And the "OIGLEIE" source answers HTTP 500 for "1990000999"
    When provider-service publishes ProviderRegistered for NPI "1003000222" and screening-service has processed it
    Then the request log of the "OIGLEIE" source shows 2 lookups for "1990000999"
    And the request log of the "OIGLEIE" source shows 1 lookup for "1003000222"
    And the "OIGLEIE" result for NPI "1003000222" is "C" with no error code and a detail containing "COULD NOT BE REACHED"
    And reading the screening of NPI "1003000222" by id gives status "COMPLETED", result "FAIL" and a completion time
