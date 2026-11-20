@MAM-17
Feature: Register a validated application as a PENDING, UNSCREENED provider
  provider-service consumes ApplicationValidated from pem.enrollment.events (a real Kafka) and becomes the one owner of the
  new provider record (D-014, D-019). The status is always set by the service (D-001); no due date before approval (D-003).

  @AC-1
  Scenario: A validated individual application becomes a PENDING, UNSCREENED provider
    Given no provider with NPI "1003000111" exists
    When provider-service consumes an ApplicationValidated event for the individual "RIVERA" "MARIA" with NPI "1003000111", Medicaid ID "MCD000111", type "IN", taxonomy "207Q00000X" and licence "MD12345" "TX"
    Then reading provider "1003000111" as SYSTEM answers 200 with status "P", screen flag "U", no risk tier and no revalidation due date
    And provider "1003000111" has Medicaid ID "MCD000111", type "IN", taxonomy "207Q00000X" and the individual "RIVERA" "MARIA" "MD12345" "TX"

  @AC-2
  Scenario: Registration publishes ProviderRegistered
    Given no provider with NPI "1003000111" exists
    When provider-service consumes an ApplicationValidated event for the individual "RIVERA" "MARIA" with NPI "1003000111", Medicaid ID "MCD000111", type "IN", taxonomy "207Q00000X" and licence "MD12345" "TX"
    Then 1 ProviderRegistered event is written for NPI "1003000111"
    And a ProviderRegistered event with key "1003000111" reaches topic "pem.provider.events" with Medicaid ID "MCD000111", type "IN", taxonomy "207Q00000X", status "P", screen flag "U" and display name "MARIA RIVERA"

  @AC-3
  Scenario: A group keeps every field, including the owner NPI
    Given no provider with NPI "1003000444" exists
    When provider-service consumes an ApplicationValidated event for the organisation "EXCLUDED CLINIC LLC" "EXCLUDED CLINIC" owned by "1990000111" with NPI "1003000444", Medicaid ID "MCD000444", type "GR" and taxonomy "251E00000X"
    Then reading provider "1003000444" as SYSTEM answers 200 with status "P", screen flag "U", no risk tier and no revalidation due date
    And provider "1003000444" has the organisation "EXCLUDED CLINIC LLC" "EXCLUDED CLINIC" owned by "1990000111" and no individual block
    And a ProviderRegistered event with key "1003000444" reaches topic "pem.provider.events" with display name "EXCLUDED CLINIC LLC"

  @AC-4
  Scenario: A duplicate NPI never overwrites the existing provider
    Given provider "1003000111" is already registered with status "A" and version 3
    When a second ApplicationValidated event for NPI "1003000111" with a new event id is consumed
    Then provider "1003000111" still has status "A" and version 3
    And 0 ProviderRegistered events are written for NPI "1003000111"
    And the service log contains "DUPLICATE NPI 1003000111"
    And the skipped-registrations counter rose by 1

  @AC-5
  Scenario: A rejected application never creates a provider
    Given no provider with NPI "1003000222" exists
    And no provider with NPI "1003000111" exists
    When enrollment-service publishes ApplicationRejected for NPI "1003000222" with error E104 on taxonomy and ApplicationValidated for NPI "1003000111", and provider-service has processed both
    Then reading provider "1003000222" as ANALYST answers 404
    And searching providers by NPI "1003000222" as ANALYST finds 0
    And 0 ProviderRegistered events are written for NPI "1003000222"
    And reading provider "1003000111" as SYSTEM answers 200 with status "P", screen flag "U", no risk tier and no revalidation due date

  @AC-6
  Scenario: A redelivered event registers once
    Given no provider with NPI "1003000111" exists
    When the same ApplicationValidated event for NPI "1003000111" is delivered twice
    Then exactly 1 provider with NPI "1003000111" exists
    And 1 ProviderRegistered event is written for NPI "1003000111"
    And the skipped-registrations counter is unchanged

  @AC-7
  Scenario: The effective date is the registration date, in ISO form
    Given no provider with NPI "1003000111" exists
    When provider-service consumes an ApplicationValidated event for the individual "RIVERA" "MARIA" with NPI "1003000111", Medicaid ID "MCD000111", type "IN", taxonomy "207Q00000X" and licence "MD12345" "TX"
    Then provider "1003000111" has the service clock date as effective date in ISO form and no revalidation due date
