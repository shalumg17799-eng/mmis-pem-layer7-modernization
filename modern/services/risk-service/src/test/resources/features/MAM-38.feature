@MAM-38
Feature: View and change the taxonomy-prefix risk table (admin)
  Staff can read the current table, an administrator can save a changed table as a new version, and each assignment
  records which version it used (Q-005, D-023, question C-9). Rule set version 1 holds exactly the legacy PRVRSK01
  values: 332 and 251 High, 341, 291 and 225 Moderate, default Limited. A saved version re-tiers nobody.

  @AC-1
  Scenario Outline: Staff can read the current table
    Given the initial risk rule set is in force
    When caller "<caller>" reads the risk rules
    Then the risk rules call returns 200
    And the risk rule set has version 1 and default tier "L"
    And the risk rule set lists exactly the rules "332=H, 251=H, 341=M, 291=M, 225=M"
    Examples:
      | caller     |
      | ANALYST    |
      | SUPERVISOR |
      | ADMIN      |

  @AC-2
  Scenario: An administrator saves a changed table as a new version
    Given the initial risk rule set is in force
    When caller "ADMIN" saves based on version 1 the default tier "L" and the legacy rules plus prefix "207" as "M"
    Then the risk rules call returns 200
    And the risk rule set has version 2 and default tier "L"
    When caller "ANALYST" reads the risk rules
    Then the risk rules call returns 200
    And the risk rule set has version 2 and default tier "L"
    And the risk rule set lists exactly the rules "332=H, 251=H, 341=M, 291=M, 225=M, 207=M"
    When risk-service processes ScreeningCompleted "PASS" for NPI "1003000222" with taxonomy "207Q00000X"
    Then 1 RiskTierAssigned event is written for NPI "1003000222" with taxonomy "207Q00000X", tier "M" and rule set version 2

  @AC-3
  Scenario: A stale or inconsistent table is refused
    Given risk rule set version 2 is in force with prefix "207" as "M"
    When caller "ADMIN" saves based on version 1 the default tier "L" and the legacy rules plus prefix "207" as "M"
    Then the risk rules call returns 422 with a problem+json body
    When caller "ADMIN" saves based on version 2 the default tier "L" and the rules "332=H, 251=H, 341=M, 291=M, 225=M, 207=M, 332=M"
    Then the risk rules call returns 422 with a problem+json body
    And the table in force is still version 2 with the rules "332=H, 251=H, 341=M, 291=M, 225=M, 207=M"

  @AC-4
  Scenario Outline: Malformed rules are rejected
    Given the initial risk rule set is in force
    When caller "ADMIN" saves based on version 1 a rule set with default tier "<default tier>" and the rule "<prefix>" as "<tier>"
    Then the risk rules call returns 400 with a problem+json body
    And the table in force is still version 1 with the rules "332=H, 251=H, 341=M, 291=M, 225=M"
    Examples:
      | case                 | default tier | prefix | tier |
      | a 2-character prefix | L            | 20     | M    |
      | a lower-case prefix  | L            | 20q    | M    |
      | a tier X             | L            | 207    | X    |
      | no defaultTier       | -            | 207    | M    |

  @AC-5
  Scenario: Saving a new version re-tiers nobody
    Given the initial risk rule set is in force
    And risk-service processes ScreeningCompleted "PASS" for NPI "1003000111" with taxonomy "207Q00000X"
    And 1 RiskTierAssigned event is written for NPI "1003000111" with taxonomy "207Q00000X", tier "L" and rule set version 1
    When caller "ADMIN" saves based on version 1 the default tier "L" and the legacy rules plus prefix "207" as "M"
    Then the risk rules call returns 200
    And no event was written by the change
    And 1 RiskTierAssigned event is written for NPI "1003000111" with taxonomy "207Q00000X", tier "L" and rule set version 1
    And the stored assessments of NPI "1003000111" are "L@1"
    When risk-service processes ScreeningCompleted "PASS" with reason "REVALIDATION" for NPI "1003000111" with taxonomy "207Q00000X"
    Then the stored assessments of NPI "1003000111" are "L@1, M@2"

  @AC-6
  Scenario Outline: Only an administrator can change the table
    Given the initial risk rule set is in force
    When caller "<caller>" saves based on version 1 the default tier "L" and the legacy rules plus prefix "207" as "M"
    Then the risk rules call returns <put status>
    When caller "<caller>" reads the risk rules
    Then the risk rules call returns <get status>
    And the table in force is version <version after>
    Examples:
      | caller     | put status | get status | version after |
      | ADMIN      | 200        | 200        | 2             |
      | SUPERVISOR | 403        | 200        | 1             |
      | ANALYST    | 403        | 200        | 1             |
      | PROVIDER   | 403        | 403        | 1             |
      | no token   | 401        | 401        | 1             |
