@MAM-18
Feature: Screen a newly registered provider against the licence board, OIG LEIE, SAM and SSA DMF (mocked sources)
  PRVSCR01's four hard-coded Pass checks replaced by real calls to the mocked sources (D-005). The licence, the name and
  the type are read from provider-service with a SYSTEM token (Q-040 default A). Four sources, no NPPES (Q-042).

  Background:
    Given the sources answer clean by default and the licence board knows no licence

  @AC-1
  Scenario: Screening starts when a provider is registered
    Given provider-service holds the individual "MARIA" "RIVERA" with NPI "1003000111", taxonomy "207Q00000X" and licence "MD12345" "TX"
    And the licence board answers "ACTIVE" until "2028-09" for licence "MD12345" "TX"
    When provider-service publishes ProviderRegistered for NPI "1003000111" and screening-service has processed it
    Then the analyst sees 1 screening for NPI "1003000111" with reason "INITIAL" and one result for each of LICBOARD, OIGLEIE, SAMGOV and SSADMF
    And the licence board was asked about licence "MD12345" in "TX"

  @AC-2
  Scenario: A clean provider passes all four sources
    Given provider-service holds the individual "MARIA" "RIVERA" with NPI "1003000111", taxonomy "207Q00000X" and licence "MD12345" "TX"
    And the licence board answers "ACTIVE" until "2028-09" for licence "MD12345" "TX"
    When provider-service publishes ProviderRegistered for NPI "1003000111" and screening-service has processed it
    Then the results for NPI "1003000111" are LICBOARD "P", OIGLEIE "P", SAMGOV "P" and SSADMF "P", each with a verdict time and a detail
    And each of the four sources was asked exactly once about NPI "1003000111" with licence "MD12345"

  @AC-3
  Scenario: An expired licence fails the licence source (E202)
    Given provider-service holds the individual "PAT" "SAMPLE" with NPI "1003000555", taxonomy "225100000X" and licence "PT99900" "TX"
    And the licence board answers "EXPIRED" until "2024-01" for licence "PT99900" "TX"
    When provider-service publishes ProviderRegistered for NPI "1003000555" and screening-service has processed it
    Then the "LICBOARD" result for NPI "1003000555" is "F" with error code "E202" and a detail containing "EXPIRED"

  @AC-4
  Scenario: A provider on the OIG LEIE fails (E201)
    Given provider-service holds the individual "OWEN" "SAMPLE" with NPI "1990000111", taxonomy "207Q00000X" and licence "MD12345" "TX"
    And the licence board answers "ACTIVE" until "2028-09" for licence "MD12345" "TX"
    And the LEIE lists NPI "1990000111" with exclusion type "1128(a)(1)" since "2023-01-15"
    When provider-service publishes ProviderRegistered for NPI "1990000111" and screening-service has processed it
    Then the "OIGLEIE" result for NPI "1990000111" is "F" with error code "E201" and a detail containing "EXCLUDED"

  @AC-5
  Scenario: A debarred provider fails SAM (E205)
    Given provider-service holds the individual "MARIA" "RIVERA" with NPI "1003000111", taxonomy "207Q00000X" and licence "MD12345" "TX"
    And the licence board answers "ACTIVE" until "2028-09" for licence "MD12345" "TX"
    And SAM lists the name "MARIA RIVERA" as debarred
    When provider-service publishes ProviderRegistered for NPI "1003000111" and screening-service has processed it
    Then the "SAMGOV" result for NPI "1003000111" is "F" with error code "E205" and a detail containing "DEBARRED"

  @AC-6
  Scenario: A deceased provider fails SSA DMF (E204)
    Given provider-service holds the individual "MARIA" "RIVERA" with NPI "1003000111", taxonomy "207Q00000X" and licence "MD12345" "TX"
    And the licence board answers "ACTIVE" until "2028-09" for licence "MD12345" "TX"
    And the SSA DMF lists NPI "1003000111" as deceased on "2025-08-01"
    When provider-service publishes ProviderRegistered for NPI "1003000111" and screening-service has processed it
    Then the "SSADMF" result for NPI "1003000111" is "F" with error code "E204" and a detail containing "DECEASED"

  @AC-7
  Scenario: The same registration never creates two screenings
    Given provider-service holds the individual "MARIA" "RIVERA" with NPI "1003000111", taxonomy "207Q00000X" and licence "MD12345" "TX"
    And the licence board answers "ACTIVE" until "2028-09" for licence "MD12345" "TX"
    When ProviderRegistered for NPI "1003000111" is delivered twice with the same event id
    Then the analyst sees 1 screening for NPI "1003000111" with reason "INITIAL" and one result for each of LICBOARD, OIGLEIE, SAMGOV and SSADMF
    And each of the four sources was asked exactly once about NPI "1003000111" with licence "MD12345"
