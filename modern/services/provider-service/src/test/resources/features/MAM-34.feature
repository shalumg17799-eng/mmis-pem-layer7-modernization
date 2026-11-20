@MAM-34
Feature: Walking skeleton - provider lookup by NPI
  One thin feature works from the analyst's screen to the database and back, with sign-in and access control.
  (Service level here; the same calls through the gateway are checked by tools/checks/smoke-walking-skeleton.sh and the
  Playwright test e2e/walking-skeleton.spec.ts.)

  Background:
    Given a seeded active individual provider with NPI "1005000101" named "Quinn Testa" with risk tier "L"

  @AC-1
  Scenario: An analyst reads a seeded provider
    When an "ANALYST" looks up NPI "1005000101"
    Then the answer is 200
    And the provider has NPI "1005000101", status "A", screen flag "P" and risk tier "L"
    And the provider's individual name is "Quinn" "Testa"
    And the answer matches the provider-service contract for getProvider

  @AC-2
  Scenario: A request without a token is refused
    When NPI "1005000101" is looked up without a token
    Then the answer is 401

  @AC-2
  Scenario: A provider cannot read another provider's record
    When a provider whose NPI is "1003000111" looks up NPI "1005000101"
    Then the answer is 403
