@MAM-43
Feature: Export the provider master in the legacy 130-byte PRVRECRD layout
  GET /api/v1/legacy/provider-master writes every provider as one 130-byte PRVRECRD record in ascending NPI order, so that the
  file can be compared byte for byte with the legacy master (D-007, ADR-005): CCYYMMDD dates (D-004), status S for a suspended
  provider (D-006), the owner NPI of a group or facility kept in bytes 121-130 (D-007), ADMIN only (D-023).

  @AC-1
  Scenario: Every record is exactly 130 bytes
    Given only these providers are stored:
      | npi        | medicaidId | providerType | taxonomy   | riskTier | enrollStatus | screenFlag | effectiveDate | revalidationDueDate | lastName | firstName | licenseNo | licenseState | legalName          | dbaName         | ownerNpi   |
      | 1003000111 | MCD000111  | IN           | 207Q00000X | L        | A            | P          | 2026-11-18    | 2031-11-18          | RIVERA   | MARIA     | MD12345   | TX           |                    |                 |            |
      | 1003000444 | MCD000444  | GR           | 261QM0801X | M        | A            | P          | 2026-11-18    | 2031-11-18          |          |           |           |              | EXCLUDED CLINIC LLC | EXCLUDED CLINIC | 1990000111 |
      | 1003000777 | MCD000777  | FA           | 282N00000X | H        | A            | P          | 2026-11-18    | 2031-11-18          |          |           |           |              | SAMPLE HOSPITAL 01 | SAMPLE HOSP     | 1990000222 |
    When an "ADMIN" caller exports the provider master
    Then the export has status 200 and media type "text/plain"
    And the export has exactly 3 lines
    And every export line is exactly 130 characters long

  @AC-2
  Scenario: Records are in NPI order with no header or trailer
    Given only these providers are stored:
      | npi        | medicaidId | providerType | legalName          | dbaName     | ownerNpi   |
      | 1003000777 | MCD000777  | FA           | SAMPLE HOSPITAL 01 | SAMPLE HOSP | 1990000222 |
      | 1003000111 | MCD000111  | IN           |                    |             |            |
      | 1003000444 | MCD000444  | GR           | EXCLUDED CLINIC LLC | EXCLUDED CLINIC | 1990000111 |
    When an "ADMIN" caller exports the provider master
    Then the export lines are for the NPIs "1003000111,1003000444,1003000777" in that order
    And the export does not end with a line break

  @AC-3
  Scenario: An individual record has the PRVRECRD field positions
    Given only these providers are stored:
      | npi        | medicaidId | providerType | taxonomy   | riskTier | enrollStatus | screenFlag | effectiveDate | revalidationDueDate | lastName | firstName | licenseNo | licenseState |
      | 1003000111 | MCD000111  | IN           | 207Q00000X | L        | A            | P          | 2026-11-18    | 2031-11-18          | RIVERA   | MARIA     | MD12345   | TX           |
    When an "ADMIN" caller exports the provider master
    Then the line of NPI "1003000111" has "1003000111" in bytes 1 to 10
    And the line of NPI "1003000111" has "MCD000111" in bytes 11 to 19
    And the line of NPI "1003000111" has "IN" in bytes 20 to 21
    And the line of NPI "1003000111" has "207Q00000X" in bytes 22 to 31
    And the line of NPI "1003000111" has "L" in bytes 32 to 32
    And the line of NPI "1003000111" has "A" in bytes 33 to 33
    And the line of NPI "1003000111" has "P" in bytes 34 to 34
    And the line of NPI "1003000111" has "20261118" in bytes 35 to 42
    And the line of NPI "1003000111" has "20311118" in bytes 43 to 50
    And the line of NPI "1003000111" has "RIVERA" space-padded in bytes 51 to 80
    And the line of NPI "1003000111" has "MARIA" space-padded in bytes 81 to 100
    And the line of NPI "1003000111" has "MD12345" space-padded in bytes 101 to 115
    And the line of NPI "1003000111" has "TX" in bytes 116 to 117
    And the line of NPI "1003000111" has only spaces in bytes 118 to 130

  @AC-4
  Scenario: A group record keeps the owner NPI in bytes 121-130
    Given only these providers are stored:
      | npi        | medicaidId | providerType | taxonomy   | riskTier | enrollStatus | screenFlag | effectiveDate | revalidationDueDate | legalName           | dbaName         | ownerNpi   |
      | 1003000444 | MCD000444  | GR           | 261QM0801X | M        | A            | P          | 2026-11-18    | 2031-11-18          | EXCLUDED CLINIC LLC | EXCLUDED CLINIC | 1990000111 |
    When an "ADMIN" caller exports the provider master
    Then the line of NPI "1003000444" has "GR" in bytes 20 to 21
    And the line of NPI "1003000444" has "EXCLUDED CLINIC LLC" space-padded in bytes 51 to 90
    And the line of NPI "1003000444" has "EXCLUDED CLINIC" space-padded in bytes 91 to 120
    And the line of NPI "1003000444" has "1990000111" in bytes 121 to 130

  @AC-4
  Scenario: A legal name longer than 40 characters is cut to its first 40 and the owner NPI is not cut off
    Given only these providers are stored:
      | npi        | medicaidId | providerType | taxonomy   | enrollStatus | screenFlag | effectiveDate | revalidationDueDate | legalName                                        | dbaName         | ownerNpi   |
      | 1003000444 | MCD000444  | GR           | 261QM0801X | A            | P          | 2026-11-18    | 2031-11-18          | NORTHWEST REGIONAL COMMUNITY HEALTH ALLIANCE LLC | EXCLUDED CLINIC | 1990000111 |
    When an "ADMIN" caller exports the provider master
    Then the line of NPI "1003000444" has "NORTHWEST REGIONAL COMMUNITY HEALTH ALLI" in bytes 51 to 90
    And the line of NPI "1003000444" has "EXCLUDED CLINIC" space-padded in bytes 91 to 120
    And the line of NPI "1003000444" has "1990000111" in bytes 121 to 130
    And every export line is exactly 130 characters long

  @AC-5
  Scenario: A suspended provider is exported with status S
    Given only these providers are stored:
      | npi        | medicaidId | enrollStatus | riskTier | screenFlag | revalidationDueDate |
      | 1003000888 | MCD000888  | S            | M        | P          | 2031-11-18          |
    When an "ADMIN" caller exports the provider master
    Then the line of NPI "1003000888" has "S" in bytes 33 to 33

  @AC-6
  Scenario: Unset values use the legacy padding
    Given only these providers are stored:
      | npi        | medicaidId | enrollStatus | screenFlag | riskTier | revalidationDueDate |
      | 1003000222 | MCD000222  | P            | U          |          |                     |
    When an "ADMIN" caller exports the provider master
    Then the line of NPI "1003000222" has " " in bytes 32 to 32
    And the line of NPI "1003000222" has "U" in bytes 34 to 34
    And the line of NPI "1003000222" has "99991231" in bytes 43 to 50

  @AC-6
  Scenario: A provider without a due date is exported with 99991231 and the codec reads the line back
    Given only these providers are stored:
      | npi        | medicaidId | providerType | enrollStatus | screenFlag | riskTier | effectiveDate | revalidationDueDate | lastName | firstName | licenseNo | licenseState |
      | 1003000222 | MCD000222  | IN           | P            | U          |          | 2026-11-18    |                     | LINDQVIST | ASTRID   | MD22222   | TX           |
      | 1003000111 | MCD000111  | IN           | A            | P          | L        | 2026-11-18    | 2031-11-18          | RIVERA   | MARIA     | MD12345   | TX           |
    When an "ADMIN" caller exports the provider master
    Then the line of NPI "1003000222" has "20261118" in bytes 35 to 42
    And the line of NPI "1003000222" has "99991231" in bytes 43 to 50
    And the line of NPI "1003000111" has "20311118" in bytes 43 to 50
    And the line of NPI "1003000222" is read back by the PRVRECRD codec with the due date "9999-12-31"
    And the line of NPI "1003000111" is read back by the PRVRECRD codec with the due date "2031-11-18"

  @AC-7
  Scenario: The export decodes to the same values as the API
    Given only these providers are stored:
      | npi        | medicaidId | providerType | taxonomy   | riskTier | enrollStatus | screenFlag | effectiveDate | revalidationDueDate | lastName | firstName | licenseNo | licenseState | legalName           | dbaName         | ownerNpi   |
      | 1003000111 | MCD000111  | IN           | 207Q00000X | L        | A            | P          | 2026-11-18    | 2031-11-18          | RIVERA   | MARIA     | MD12345   | TX           |                     |                 |            |
      | 1003000444 | MCD000444  | GR           | 261QM0801X | M        | S            | P          | 2026-11-18    | 2031-11-18          |          |           |           |              | EXCLUDED CLINIC LLC | EXCLUDED CLINIC | 1990000111 |
      | 1003000777 | MCD000777  | FA           | 282N00000X |          | P            | U          | 2026-11-18    | 2031-12-31          |          |           |           |              | SAMPLE HOSPITAL 01  | SAMPLE HOSP     | 1990000222 |
    When an "ADMIN" caller exports the provider master
    Then every export line decodes with the PRVRECRD codec to the fields the API returns for that NPI

  @AC-8
  Scenario Outline: Only ADMIN can export
    Given only these providers are stored:
      | npi        | medicaidId | revalidationDueDate |
      | 1003000111 | MCD000111  | 2031-11-18          |
    When a "<caller>" caller exports the provider master
    Then the export has status <status> and media type "<media>"

    Examples:
      | caller              | status | media                    |
      | ADMIN               | 200    | text/plain               |
      | ANALYST             | 403    | application/problem+json |
      | SUPERVISOR          | 403    | application/problem+json |
      | PROVIDER:1003000111 | 403    | application/problem+json |
      | SYSTEM              | 403    | application/problem+json |
      | NONE                | 401    | application/problem+json |
