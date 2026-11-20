@MAM-27
Feature: Analyst approves or denies an enrolment; approval sets ACTIVE and the revalidation due date
  Only an analyst decision sets ACTIVE (D-019); the due date is the approval date + 5 years in 4-digit-year form
  (D-003, D-004); roles are checked on the endpoint (D-023); failures are explicit problem+json answers (D-024).

  @AC-1 @AC-2
  Scenario: Approval sets ACTIVE and the revalidation due date, and publishes ProviderStatusChanged
    Given provider "1003000111" is stored with status "P", screen flag "P", risk tier "L" and version 0
    When "ANALYST" sends the decision "APPROVE" for provider "1003000111"
    Then the decision answer is 200 with status "A" and the service clock date plus 5 years as revalidation due date
    And reading provider "1003000111" gives status "A" and the service clock date plus 5 years as revalidation due date
    And 1 ProviderStatusChanged event is written for "1003000111" from "P" to "A" by "analyst1" with the due date, type "IN", name "MARIA RIVERA", taxonomy "207Q00000X" and Medicaid ID "MCD000111"
    And a ProviderStatusChanged event with key "1003000111" reaches topic "pem.provider.events" with to "A"

  @AC-3
  Scenario: Denial sets DENIED and leaves no due date
    Given provider "1003000444" is stored with status "P", screen flag "F", risk tier "none" and version 0
    When "ANALYST" sends the decision "DENY" with reason "Owner 1990000111 is on the OIG LEIE" for provider "1003000444"
    Then the decision answer is 200 with status "D" and no revalidation due date
    And 1 ProviderStatusChanged event is written for "1003000444" from "P" to "D" with reason "Owner 1990000111 is on the OIG LEIE"

  @AC-4
  Scenario: Approval needs a passed screening; denial needs a finished one
    Given provider "1003000444" is stored with status "P", screen flag "F", risk tier "none" and version 0
    And provider "1003000222" is stored with status "P", screen flag "U", risk tier "none" and version 0
    When "ANALYST" sends the decision "APPROVE" for provider "1003000444"
    Then the decision answer is 422 problem+json
    When "ANALYST" sends the decision "APPROVE" for provider "1003000222"
    Then the decision answer is 422 problem+json
    When "ANALYST" sends the decision "DENY" for provider "1003000222"
    Then the decision answer is 422 problem+json
    And reading provider "1003000444" gives status "P" and no revalidation due date
    And reading provider "1003000222" gives status "P" and no revalidation due date
    And no ProviderStatusChanged event is written for "1003000444"
    And no ProviderStatusChanged event is written for "1003000222"

  @AC-5
  Scenario: A decision cannot be taken twice and never resets the due date
    Given provider "1003000111" is stored with status "A", screen flag "P", risk tier "L" and version 0
    When "ANALYST" sends the decision "APPROVE" for provider "1003000111"
    Then the decision answer is 409 problem+json
    When "ANALYST" sends the decision "DENY" for provider "1003000111"
    Then the decision answer is 409 problem+json
    And reading provider "1003000111" gives status "A" and revalidation due date "2031-11-18"
    And no ProviderStatusChanged event is written for "1003000111"

  @AC-6
  Scenario: An unknown provider gives 404
    Given no provider with NPI "1003000999" exists
    When "ANALYST" sends the decision "APPROVE" for provider "1003000999"
    Then the decision answer is 404 problem+json
    And exactly 0 providers with NPI "1003000999" exist

  @AC-7
  Scenario: Only ANALYST and SUPERVISOR can decide
    Given provider "1003000111" is stored with status "P", screen flag "P", risk tier "none" and version 0
    And provider "1003000333" is stored with status "P", screen flag "P", risk tier "none" and version 0
    When "PROVIDER" sends the decision "APPROVE" for provider "1003000111"
    Then the decision answer is 403 problem+json
    When "ADMIN" sends the decision "APPROVE" for provider "1003000111"
    Then the decision answer is 403 problem+json
    When "SYSTEM" sends the decision "APPROVE" for provider "1003000111"
    Then the decision answer is 403 problem+json
    When "NONE" sends the decision "APPROVE" for provider "1003000111"
    Then the decision answer is 401
    And reading provider "1003000111" gives status "P" and no revalidation due date
    When "ANALYST" sends the decision "APPROVE" for provider "1003000111"
    Then the decision answer is 200 with status "A" and the service clock date plus 5 years as revalidation due date
    When "SUPERVISOR" sends the decision "APPROVE" for provider "1003000333"
    Then the decision answer is 200 with status "A" and the service clock date plus 5 years as revalidation due date

  @AC-8
  Scenario: Approval does not wait for the risk tier
    Given provider "1003000111" is stored with status "P", screen flag "P", risk tier "none" and version 0
    When "ANALYST" sends the decision "APPROVE" for provider "1003000111"
    Then the decision answer is 200 with status "A" and the service clock date plus 5 years as revalidation due date
    And the decision answer has no risk tier
