@MAM-28
Feature: Rescreen a provider on request or when revalidation is due
  An analyst, a supervisor or the system can ask for a fresh MANUAL screening; RevalidationDue starts exactly one
  REVALIDATION screening per case. Every screening uses the provider's current data from provider-service and the same
  four sources (D-005), and earlier screenings are kept unchanged (D-018).

  Background:
    Given the sources answer clean by default and the licence board knows no licence

  @AC-1
  Scenario: An analyst starts a screening on request
    Given provider-service holds the individual "TESTPROV" "ECHO" with NPI "1003000555", taxonomy "207Q00000X" and licence "MD55555" "TX"
    And provider-service publishes ProviderRegistered for NPI "1003000555" and screening-service has processed it
    And the first screening of NPI "1003000555" is remembered
    When "ANALYST" requests a screening of NPI "1003000555"
    Then the request answers 202 with a MANUAL screening of NPI "1003000555" in status "RUNNING"
    And the requested screening completes with exactly 4 results, one per source
    And the remembered screening of NPI "1003000555" is unchanged
    And the analyst sees 2 screenings for NPI "1003000555", each with its own 4 results

  @AC-2
  Scenario: A malformed or unknown NPI is refused
    Given provider-service does not know NPI "1003000999"
    When "ANALYST" requests a screening of NPI "123"
    Then the request answers 400 problem+json
    When "ANALYST" requests a screening of NPI "1003000999"
    Then the request answers 404 problem+json
    And no screening is stored for NPI "1003000999"

  @AC-3 @AC-4
  Scenario: Revalidation due starts exactly one re-screening, also when it is delivered twice
    Given provider-service holds the individual "TESTPROV" "BRAVO" with NPI "1003000222", taxonomy "207Q00000X" and licence "MD22222" "TX"
    When RevalidationDue for NPI "1003000222" due "2026-02-28" is delivered twice with the same event id
    Then exactly 1 screening with reason "REVALIDATION" exists for NPI "1003000222" and it is COMPLETED
    And 1 ScreeningCompleted event is written for NPI "1003000222"
    And the ScreeningCompleted event of NPI "1003000222" has reason "REVALIDATION"

  @AC-5
  Scenario: A re-screening uses the provider's current data
    Given provider-service holds the individual "TESTPROV" "ALPHA" with NPI "1003000111", taxonomy "207Q00000X" and licence "MD12345" "TX"
    And the licence board answers "ACTIVE" until "2030-12-31" for licence "MD12345" "TX"
    And provider-service publishes ProviderRegistered for NPI "1003000111" and screening-service has processed it
    And provider-service holds the individual "TESTPROV" "ALPHA" with NPI "1003000111", taxonomy "207Q00000X" and licence "PT99900" "TX"
    And the licence board answers "EXPIRED" until "2025-12-31" for licence "PT99900" "TX"
    And the sources' request log is cleared
    When "ANALYST" requests a screening of NPI "1003000111"
    Then the requested screening completes with exactly 4 results, one per source
    And the licence board was asked about licence "PT99900" in "TX"
    And the licence board was not asked about licence "MD12345"
    And the requested screening's "LICBOARD" result is "F" with error code "E202"

  @AC-6
  Scenario: Only analysts, supervisors and the system can start a screening
    Given provider-service holds the individual "TESTPROV" "ALPHA" with NPI "1003000111", taxonomy "207Q00000X" and licence "MD12345" "TX"
    Then requesting a screening of NPI "1003000111" answers 202 for "ANALYST", "SUPERVISOR" and "SYSTEM"
    And requesting a screening of NPI "1003000111" answers 403 for "PROVIDER" and "ADMIN"
    And requesting a screening of NPI "1003000111" answers 401 without a token
