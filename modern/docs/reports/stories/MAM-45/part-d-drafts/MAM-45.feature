@MAM-45
Feature: Monthly revalidation sweep: open a case for every ACTIVE provider that is due, with the legacy REVALQ file
  PRVREV01 rebuilt: the due date of each tracked provider (read model from ProviderStatusChanged, D-014) is compared with
  the explicit business date (D-020, D-004); an ACTIVE provider that is due gets exactly one open case (D-029, D-006);
  each new case is announced as RevalidationDue; the run keeps its counts and its 80-byte REVALQ file (ADR-005, D-024).

  Background:
    Given revalidation-service has no tracked providers, cases or sweep runs

  @AC-1
  Scenario: An administrator or the scheduler starts the sweep for a business date
    When "ADMIN" starts a revalidation sweep for business date "2026-03-31"
    Then the sweep answer is 202 with a RUNNING revalidation-sweep run for business date "2026-03-31"
    When "SYSTEM" starts a revalidation sweep for business date "2026-03-31"
    Then the sweep answer is 202 with a RUNNING revalidation-sweep run for business date "2026-03-31"
    When "ANALYST" starts a revalidation sweep for business date "2026-03-31"
    Then the sweep answer is 403
    When "PROVIDER" starts a revalidation sweep for business date "2026-03-31"
    Then the sweep answer is 403
    When "NONE" starts a revalidation sweep for business date "2026-03-31"
    Then the sweep answer is 401
    When "ADMIN" starts a revalidation sweep without a business date
    Then the sweep answer is 400

  @AC-2
  Scenario: An ACTIVE provider whose due date has arrived gets one case
    Given revalidation-service has consumed ProviderStatusChanged for "1003000222" to status "A" with revalidation due date "2026-02-28"
    And revalidation-service has consumed ProviderStatusChanged for "1003000333" to status "A" with revalidation due date "2026-03-31"
    When the revalidation sweep runs for business date "2026-03-31" and completes
    Then the cases of "1003000222" are exactly 1 open case due "2026-02-28"
    And the cases of "1003000333" are exactly 1 open case due "2026-03-31"

  @AC-3
  Scenario: Providers that are not due, not ACTIVE or moved forward get no case
    Given revalidation-service has consumed ProviderStatusChanged for "1003000777" to status "A" with revalidation due date "2026-04-01"
    And revalidation-service has consumed ProviderStatusChanged for "1003000888" to status "S" with revalidation due date "2026-02-28"
    And revalidation-service has consumed ProviderStatusChanged for "1003000555" to status "T" with revalidation due date "2026-02-28"
    And revalidation-service has consumed ProviderStatusChanged for "1003000111" to status "A" with revalidation due date "2026-02-28"
    And revalidation-service has consumed ProviderStatusChanged for "1003000111" to status "A" with revalidation due date "2031-03-31"
    When the revalidation sweep runs for business date "2026-03-31" and completes
    Then "1003000777" has no revalidation case
    And "1003000888" has no revalidation case
    And "1003000555" has no revalidation case
    And "1003000111" has no revalidation case
    And the queue file of the last run is empty

  @AC-4
  Scenario: Opening a case announces it
    Given revalidation-service has consumed ProviderStatusChanged for "1003000222" to status "A" with revalidation due date "2026-02-28"
    When the revalidation sweep runs for business date "2026-03-31" and completes
    Then exactly 1 RevalidationDue event is written for "1003000222" with the case id and due date "2026-02-28"
    And the RevalidationDue event for "1003000222" reaches topic "pem.revalidation.events"

  @AC-5
  Scenario: A provider that already has an open case is not queued again
    Given revalidation-service has consumed ProviderStatusChanged for "1003000222" to status "A" with revalidation due date "2026-02-28"
    And the revalidation sweep runs for business date "2026-03-31" and completes
    When the revalidation sweep runs for business date "2026-03-31" and completes
    Then the queue file of the last run is empty
    When the revalidation sweep runs for business date "2026-04-30" and completes
    Then the queue file of the last run is empty
    And the cases of "1003000222" are exactly 1 open case due "2026-02-28"
    And exactly 1 RevalidationDue event is written for "1003000222" with the case id and due date "2026-02-28"

  @AC-6
  Scenario: A provider with no due date is not queued, and the gap is visible
    Given revalidation-service has consumed ProviderStatusChanged for "1003000666" to status "A" with revalidation due date "none"
    When the revalidation sweep runs for business date "2026-03-31" and completes
    Then "1003000666" has no revalidation case
    And the queue file of the last run is empty
    And the last run read by "ADMIN" is COMPLETED with 0 queued and 1 without due date

  @AC-7
  Scenario: The legacy REVALQ file lists the cases the run opened
    Given revalidation-service has consumed ProviderStatusChanged for "1003000333" to status "A" with revalidation due date "2026-03-31"
    And revalidation-service has consumed ProviderStatusChanged for "1003000222" to status "A" with revalidation due date "2026-02-28"
    When the revalidation sweep runs for business date "2026-03-31" and completes
    Then the queue file of the last run read by "ADMIN" holds exactly these 80-byte records:
      | npi        | dueDate  | action            |
      | 1003000222 | 20260228 | RESCREEN-REQUIRED |
      | 1003000333 | 20260331 | RESCREEN-REQUIRED |
    And the queue file of the last run answers 403 for "SYSTEM", "ANALYST" and "PROVIDER"
    And the queue file of an unknown run answers 404

  @AC-8
  Scenario: The run reports how many cases it queued
    Given revalidation-service has consumed ProviderStatusChanged for "1003000222" to status "A" with revalidation due date "2026-02-28"
    And revalidation-service has consumed ProviderStatusChanged for "1003000333" to status "A" with revalidation due date "2026-03-31"
    When the revalidation sweep runs for business date "2026-03-31" and completes
    Then the last run read by "ADMIN" is COMPLETED with 2 queued and 0 without due date
