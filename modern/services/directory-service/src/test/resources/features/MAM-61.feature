@MAM-61
Feature: Keep the directory of ACTIVE providers up to date from provider events
  PRVDIR01 rebuilt as a read model (D-013, D-014): only the enrolment status decides publication (status ACTIVE, as the
  legacy filter), the display name is composed from the name fields (D-028, Q-071 default) read from provider-service
  with a SYSTEM token, and searches are answered from the directory's own data.

  @AC-1
  Scenario: A pending provider is not published
    Given provider-service holds the individual "MARIA" "RIVERA" with NPI "1003000111", Medicaid ID "M00000111", taxonomy "207Q00000X" and licence "MD12345" "TX"
    When directory-service consumes ProviderRegistered for NPI "1003000111" with Medicaid ID "M00000111", type "IN", taxonomy "207Q00000X" and name "MARIA RIVERA"
    Then the public directory does not list NPI "1003000111"

  @AC-2
  Scenario: Approval publishes the provider with the directory fields
    Given provider-service holds the individual "MARIA" "RIVERA" with NPI "1003000111", Medicaid ID "M00000111", taxonomy "207Q00000X" and licence "MD12345" "TX"
    When directory-service consumes ProviderRegistered for NPI "1003000111" with Medicaid ID "M00000111", type "IN", taxonomy "207Q00000X" and name "MARIA RIVERA"
    And directory-service consumes RiskTierAssigned for NPI "1003000111" with tier "L"
    And directory-service consumes ProviderStatusChanged for NPI "1003000111" from "P" to "A"
    Then the public directory lists NPI "1003000111" once with Medicaid ID "M00000111", type "IN", risk tier "L", taxonomy "207Q00000X" and name "MARIA RIVERA"
    And provider-service was asked for NPI "1003000111" with the SYSTEM token

  @AC-3
  Scenario: The display name is composed from the name fields, never raw bytes
    Given provider-service holds the individual "MARIA" "RIVERA" with NPI "1003000111", Medicaid ID "M00000111", taxonomy "207Q00000X" and licence "MD12345" "TX"
    And provider-service holds the facility "SUNSET HOSPICE INC" doing business as "SUNSET HOSPICE CARE" with NPI "1003000333", Medicaid ID "M00000333" and taxonomy "251G00000X"
    When directory-service consumes ProviderStatusChanged for NPI "1003000111" from "P" to "A"
    And directory-service consumes ProviderStatusChanged for NPI "1003000333" from "P" to "A"
    Then the public directory shows name "MARIA RIVERA" for NPI "1003000111" without "MD12345", padding or more than 60 characters
    And the public directory shows name "SUNSET HOSPICE INC" for NPI "1003000333" without "SUNSET HOSPICE CARE", padding or more than 60 characters

  @AC-4
  Scenario: A provider that is no longer ACTIVE is withdrawn
    Given provider-service holds the facility "SUNSET HOSPICE INC" doing business as "SUNSET HOSPICE CARE" with NPI "1003000333", Medicaid ID "M00000333" and taxonomy "251G00000X"
    And provider-service holds the group "EXCLUDED CLINIC LLC" doing business as "EXCLUDED CLINIC" with NPI "1003000444", Medicaid ID "M00000444" and taxonomy "251E00000X"
    When directory-service consumes ProviderStatusChanged for NPI "1003000333" from "P" to "A"
    Then the public directory lists NPI "1003000333" once
    When directory-service consumes ProviderStatusChanged for NPI "1003000333" from "A" to "S"
    Then the public directory does not list NPI "1003000333"
    When directory-service consumes ProviderStatusChanged for NPI "1003000333" from "S" to "A"
    And directory-service consumes ProviderStatusChanged for NPI "1003000333" from "A" to "T"
    Then the public directory does not list NPI "1003000333"
    When directory-service consumes ProviderStatusChanged for NPI "1003000444" from "P" to "D"
    Then the public directory does not list NPI "1003000444"

  @AC-5
  Scenario: A reinstated provider is published again
    Given provider-service holds the facility "SUNSET HOSPICE INC" doing business as "SUNSET HOSPICE CARE" with NPI "1003000333", Medicaid ID "M00000333" and taxonomy "251G00000X"
    When directory-service consumes RiskTierAssigned for NPI "1003000333" with tier "H"
    And directory-service consumes ProviderStatusChanged for NPI "1003000333" from "P" to "A"
    And directory-service consumes ProviderStatusChanged for NPI "1003000333" from "A" to "S"
    And directory-service consumes ProviderStatusChanged for NPI "1003000333" from "S" to "A"
    Then the public directory lists NPI "1003000333" once with Medicaid ID "M00000333", type "FA", risk tier "H", taxonomy "251G00000X" and name "SUNSET HOSPICE INC"

  @AC-6
  Scenario: Only the enrolment status decides publication
    Given provider-service holds the group "EXCLUDED CLINIC LLC" doing business as "EXCLUDED CLINIC" with NPI "1003000444", Medicaid ID "M00000444" and taxonomy "251E00000X"
    When directory-service consumes ProviderStatusChanged for NPI "1003000444" from "D" to "A" with revalidation due date "2025-01-31"
    Then the public directory lists NPI "1003000444" once with Medicaid ID "M00000444", type "GR", no risk tier, taxonomy "251E00000X" and name "EXCLUDED CLINIC LLC"

  @AC-7
  Scenario: A redelivered event changes nothing
    Given provider-service holds the individual "MARIA" "RIVERA" with NPI "1003000111", Medicaid ID "M00000111", taxonomy "207Q00000X" and licence "MD12345" "TX"
    When directory-service consumes ProviderStatusChanged for NPI "1003000111" from "P" to "A"
    And the directory total is remembered
    And the same event is delivered to directory-service again
    Then the public directory lists NPI "1003000111" once with Medicaid ID "M00000111", type "IN", no risk tier, taxonomy "207Q00000X" and name "MARIA RIVERA"
    And the directory total is unchanged

  @AC-8
  Scenario: Searches are answered from the directory's own data
    Given provider-service holds the individual "MARIA" "RIVERA" with NPI "1003000111", Medicaid ID "M00000111", taxonomy "207Q00000X" and licence "MD12345" "TX"
    When directory-service consumes ProviderStatusChanged for NPI "1003000111" from "P" to "A"
    And provider-service becomes unreachable
    Then the public directory lists NPI "1003000111" once
