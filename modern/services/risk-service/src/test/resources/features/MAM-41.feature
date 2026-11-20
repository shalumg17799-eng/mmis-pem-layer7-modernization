@MAM-41
Feature: Assess a risk tier on request (risk assessment API)
  Analysts and other services ask which risk level a specialty code would get, without changing anything (Q-005, D-002).
  The answer uses the same table as the real assignment, looks only at the first three characters of the taxonomy
  (PRVRSK01 CLASSIFY-ONE) and never records or announces anything.

  @AC-1
  Scenario Outline: Prefixes in the table are assessed with their tier
    Given the initial risk rule set is in force
    When caller "ANALYST" assesses the taxonomy "<taxonomy>"
    Then the assessment answers 200 with taxonomy "<taxonomy>", tier "<tier>" and rule set version 1
    Examples:
      | taxonomy   | tier |
      | 332B00000X | H    |
      | 251E00000X | H    |
      | 341600000X | M    |
      | 291U00000X | M    |
      | 225100000X | M    |
      | 207Q00000X | L    |

  @AC-2
  Scenario Outline: Only the first three characters count
    Given the initial risk rule set is in force
    When caller "ANALYST" assesses the taxonomy "<taxonomy>"
    Then the assessment answers 200 with taxonomy "<taxonomy>", tier "H" and rule set version 1
    Examples:
      | taxonomy   |
      | 3320000000 |
      | 332ZZZZZZZ |

  @AC-3
  Scenario: A blank or too-short taxonomy gets the default tier, as in the legacy
    Given the initial risk rule set is in force
    When caller "ANALYST" assesses the taxonomy ""
    Then the assessment answers 200 with taxonomy "", tier "L" and rule set version 1
    When caller "ANALYST" assesses the taxonomy "   "
    Then the assessment answers 200 with taxonomy "   ", tier "L" and rule set version 1
    When caller "ANALYST" assesses the taxonomy "33"
    Then the assessment answers 200 with taxonomy "33", tier "L" and rule set version 1

  @AC-4
  Scenario: A missing or too-long taxonomy is a bad request
    Given the initial risk rule set is in force
    When caller "ANALYST" asks for an assessment without a taxonomy
    Then the assessment answers 400 with a problem+json body
    When caller "ANALYST" assesses the taxonomy "332B00000XY"
    Then the assessment answers 400 with a problem+json body

  @AC-5
  Scenario: The assessment has no side effect
    Given the initial risk rule set is in force
    And what is stored, announced and counted is noted
    When caller "ANALYST" assesses the taxonomy "332B00000X"
    And caller "ANALYST" assesses the taxonomy "332B00000X" for NPI "1003000111"
    Then every assessment answered 200 with tier "H"
    And nothing was stored, announced or counted since it was noted

  @AC-6
  Scenario: The answer uses the table in force
    Given risk rule set version 2 is in force with prefix "207" as "M"
    When caller "ANALYST" assesses the taxonomy "207Q00000X"
    Then the assessment answers 200 with taxonomy "207Q00000X", tier "M" and rule set version 2

  @AC-7
  Scenario Outline: Only analysts, supervisors and the system can ask
    Given the initial risk rule set is in force
    When caller "<caller>" assesses the taxonomy "207Q00000X"
    Then the assessment answers <status>
    Examples:
      | caller     | status |
      | ANALYST    | 200    |
      | SUPERVISOR | 200    |
      | SYSTEM     | 200    |
      | PROVIDER   | 403    |
      | ADMIN      | 403    |
      | no token   | 401    |
