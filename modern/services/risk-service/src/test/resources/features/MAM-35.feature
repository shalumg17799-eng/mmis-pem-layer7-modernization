@MAM-35
Feature: Assign the risk tier from the taxonomy prefix after a passed screening
  PRVRSK01 rebuilt as a data-driven rule table in risk-service (Q-005): prefixes 332 and 251 High, 341, 291 and 225
  Moderate, anything else the default Limited (rule set version 1 = the legacy table). Only a PASS is tiered (D-002).

  @AC-1
  Scenario: A passed screening gets a risk tier, which is announced for saving
    When risk-service processes ScreeningCompleted "PASS" for NPI "1003000111" with taxonomy "207Q00000X"
    Then 1 RiskTierAssigned event is written for NPI "1003000111" with taxonomy "207Q00000X", tier "L" and rule set version 1
    And a RiskTierAssigned event with key "1003000111" reaches topic "pem.risk.events" with tier "L" and rule set version 1

  @AC-2 @AC-3 @AC-4
  Scenario Outline: Prefixes 332 and 251 are High, 341, 291 and 225 Moderate, every other prefix Limited
    When risk-service processes ScreeningCompleted "PASS" for NPI "<npi>" with taxonomy "<taxonomy>"
    Then 1 RiskTierAssigned event is written for NPI "<npi>" with taxonomy "<taxonomy>", tier "<tier>" and rule set version 1
    Examples:
      | npi        | taxonomy   | tier |
      | 1003000222 | 332B00000X | H    |
      | 1003000333 | 251E00000X | H    |
      | 1003000444 | 341600000X | M    |
      | 1003000555 | 291U00000X | M    |
      | 1003000666 | 225100000X | M    |
      | 1003000777 | 207Q00000X | L    |
      | 1003000888 | 999Z00000X | L    |

  @AC-5
  Scenario: Each assessment carries exactly one tier, and a later passed screening is assessed again
    When risk-service processes ScreeningCompleted "PASS" for NPI "1003000111" with taxonomy "207Q00000X"
    And risk-service processes ScreeningCompleted "PASS" with reason "REVALIDATION" for NPI "1003000111" with taxonomy "207Q00000X"
    Then 2 RiskTierAssigned events are written for NPI "1003000111", each with exactly one tier "L" and rule set version 1

  @AC-6
  Scenario: A failed screening gets no tier
    When risk-service processes ScreeningCompleted "FAIL" for NPI "1003000444" with taxonomy "251E00000X"
    Then 0 RiskTierAssigned events are written for NPI "1003000444"

  @AC-7
  Scenario: Every passed screening is classified exactly once
    When risk-service processes ScreeningCompleted "PASS" for NPI "1003000111" with taxonomy "207Q00000X"
    And risk-service processes ScreeningCompleted "PASS" for NPI "1003000222" with taxonomy "332B00000X", delivered twice with the same event id
    And risk-service processes ScreeningCompleted "PASS" for NPI "1003000333" with taxonomy "341600000X"
    Then 1 RiskTierAssigned event is written for NPI "1003000111" with taxonomy "207Q00000X", tier "L" and rule set version 1
    And 1 RiskTierAssigned event is written for NPI "1003000222" with taxonomy "332B00000X", tier "H" and rule set version 1
    And 1 RiskTierAssigned event is written for NPI "1003000333" with taxonomy "341600000X", tier "M" and rule set version 1

  @AC-8
  Scenario: A failure to save is not silent, and assignments are counted
    Given the risk assessment table cannot be written
    When ScreeningCompleted "PASS" for NPI "1003000111" with taxonomy "207Q00000X" fails to be processed
    Then the log holds the failure with the event's correlation id
    And 0 RiskTierAssigned events are written for NPI "1003000111"
    When the risk assessment table can be written again and the same event is delivered again
    Then 1 RiskTierAssigned event is written for NPI "1003000111" with taxonomy "207Q00000X", tier "L" and rule set version 1
    And the tiers-assigned counter rose by 1
