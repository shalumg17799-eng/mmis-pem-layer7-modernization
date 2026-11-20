@MAM-37
Feature: Search and read providers by NPI, Medicaid ID, name, status and screen flag (PINQ replacement back end)
  PRVCICS1 rebuilt as GET /api/v1/providers/{npi} and GET /api/v1/providers (D-022): the typed NPI is the key and nothing is
  carried over between requests, an unknown NPI is a 404, a read failure is never reported as 'not found' (D-024), status S is
  returned as S (D-006), and every endpoint is role-checked (D-023, ADR-007).

  @AC-1
  Scenario: Look up a provider by NPI and see every field
    Given only these providers are stored:
      | npi        | medicaidId | providerType | taxonomy   | riskTier | enrollStatus | screenFlag | effectiveDate | revalidationDueDate | lastName | firstName | licenseNo | licenseState |
      | 1003000111 | MCD000111  | IN           | 207Q00000X | L        | A            | P          | 2026-11-18    | 2031-11-18          | RIVERA   | MARIA     | MD12345   | TX           |
    When an "ANALYST" caller sends GET "/api/v1/providers/1003000111"
    Then answer 1 has status 200
    And answer 1 has these fields:
      | /npi                     | 1003000111 |
      | /medicaidId              | MCD000111  |
      | /providerType            | IN         |
      | /taxonomy                | 207Q00000X |
      | /riskTier                | L          |
      | /enrollStatus            | A          |
      | /screenFlag              | P          |
      | /effectiveDate           | 2026-11-18 |
      | /revalidationDueDate     | 2031-11-18 |
      | /individual/lastName     | RIVERA     |
      | /individual/firstName    | MARIA      |
      | /individual/licenseNo    | MD12345    |
      | /individual/licenseState | TX         |
      | /organization            | <absent>   |
    And answer 1 matches the provider-service contract for getProvider

  @AC-2
  Scenario: An unknown NPI gives 404 and no data of any other provider
    Given only these providers are stored:
      | npi        | medicaidId | lastName | firstName |
      | 1003000111 | MCD000111  | RIVERA   | MARIA     |
    When an "ANALYST" caller sends GET "/api/v1/providers/1003000999"
    Then answer 1 is a problem+json with status 404
    And answer 1 does not contain "1003000111"
    And answer 1 does not contain "RIVERA"
    And answer 1 does not contain "MCD000111"

  @AC-3
  Scenario: The NPI typed in the request is the key, with no carry-over
    Given only these providers are stored:
      | npi        | medicaidId | lastName | firstName |
      | 1003000111 | MCD000111  | RIVERA   | MARIA     |
      | 1003000444 | MCD000444  | OKAFOR   | JAMES     |
    When an "ANALYST" caller sends GET "/api/v1/providers/1003000111"
    And an "ANALYST" caller sends GET "/api/v1/providers/1003000444"
    And an "ANALYST" caller sends GET "/api/v1/providers/1003000999"
    Then answer 1 has these fields:
      | /npi                  | 1003000111 |
      | /individual/lastName  | RIVERA     |
    And answer 2 has these fields:
      | /npi                  | 1003000444 |
      | /individual/lastName  | OKAFOR     |
    And answer 3 is a problem+json with status 404
    And answer 3 does not contain "OKAFOR"

  @AC-4
  Scenario Outline: The status code is returned as stored, including S
    Given only these providers are stored:
      | npi        | medicaidId | enrollStatus |
      | <npi>      | MCD000999  | <status>     |
    When an "ANALYST" caller sends GET "/api/v1/providers/<npi>"
    Then answer 1 has status 200
    And answer 1 has these fields:
      | /enrollStatus | <status> |

    Examples:
      | npi        | status |
      | 1003000211 | P      |
      | 1003000212 | A      |
      | 1003000213 | T      |
      | 1003000214 | D      |
      | 1003000215 | S      |

  @AC-5
  Scenario: Search by Medicaid ID or NPI
    Given only these providers are stored:
      | npi        | medicaidId | lastName | firstName |
      | 1003000111 | MCD000111  | RIVERA   | MARIA     |
      | 1003000444 | MCD000444  | OKAFOR   | JAMES     |
    When an "ANALYST" caller sends GET "/api/v1/providers?medicaidId=MCD000111"
    And an "ANALYST" caller sends GET "/api/v1/providers?npi=1003000111"
    And an "ANALYST" caller sends GET "/api/v1/providers?npi=1003000999"
    Then answer 1 lists exactly the providers "1003000111" with total 1
    And answer 2 lists exactly the providers "1003000111" with total 1
    And answer 3 has status 200
    And answer 3 lists exactly the providers "" with total 0
    And answer 1 matches the provider-service contract for searchProviders

  @AC-6
  Scenario: Search by name, status and screen flag with paging (work queues)
    Given only these providers are stored:
      | npi        | medicaidId | lastName | firstName | enrollStatus | screenFlag |
      | 1003000111 | MCD000111  | RIVERA   | MARIA     | P            | P          |
      | 1003000444 | MCD000444  | OKAFOR   | JAMES     | P            | F          |
      | 1003000222 | MCD000222  | LINDQVIST | ASTRID   | P            | U          |
    And 22 further providers are stored with status "A" and screen flag "P"
    When an "ANALYST" caller sends GET "/api/v1/providers?status=P&screenFlag=P"
    And an "ANALYST" caller sends GET "/api/v1/providers?status=P&screenFlag=F"
    And an "ANALYST" caller sends GET "/api/v1/providers?name=RIVERA"
    And an "ANALYST" caller sends GET "/api/v1/providers?page=1&size=20"
    Then answer 1 lists exactly the providers "1003000111" with total 1
    And answer 2 lists exactly the providers "1003000444" with total 1
    And answer 3 lists exactly the providers "1003000111" with total 1
    And answer 3 lists only providers whose name contains "RIVERA"
    And answer 4 has 5 items, page 1, size 20 and total 25
    And answer 4 lists the providers "1004000018,1004000019,1004000020,1004000021,1004000022" in that order

  @AC-6
  Scenario: A name search treats % and _ as plain characters
    Given only these providers are stored:
      | npi        | medicaidId | providerType | lastName | firstName | legalName       | dbaName     |
      | 1003000511 | MCD000511  | GR           |          |           | 50% OFF CLINIC  | HALF PRICE  |
      | 1003000522 | MCD000522  | GR           |          |           | A_B HEALTH      | AB HEALTH   |
      | 1003000533 | MCD000533  | GR           |          |           | AXB HEALTH      | AXB         |
      | 1003000544 | MCD000544  | IN           | RIVERA   | MARIA     |                 |             |
    When an "ANALYST" caller sends GET "/api/v1/providers?name=%"
    And an "ANALYST" caller sends GET "/api/v1/providers?name=50%"
    And an "ANALYST" caller sends GET "/api/v1/providers?name=_"
    And an "ANALYST" caller sends GET "/api/v1/providers?name=A_B"
    And an "ANALYST" caller sends GET "/api/v1/providers?name=R_VERA"
    Then answer 1 lists exactly the providers "1003000511" with total 1
    And answer 2 lists exactly the providers "1003000511" with total 1
    And answer 3 lists exactly the providers "1003000522" with total 1
    And answer 4 lists exactly the providers "1003000522" with total 1
    And answer 5 lists exactly the providers "" with total 0

  @AC-7
  Scenario Outline: Role checks: a provider sees only their own record
    Given only these providers are stored:
      | npi        | medicaidId | enrollStatus | lastName | firstName |
      | 1003000111 | MCD000111  | A            | RIVERA   | MARIA     |
      | 1003000444 | MCD000444  | A            | OKAFOR   | JAMES     |
    When a "<caller>" caller sends GET "<target>"
    Then answer 1 has status <status> and media type "<media>"

    Examples: provider1 sees only their own record
      | caller              | target                       | status | media                    |
      | PROVIDER:1003000111 | /api/v1/providers/1003000111 | 200    | application/json         |
      | PROVIDER:1003000111 | /api/v1/providers/1003000444 | 403    | application/problem+json |
      | PROVIDER:1003000111 | /api/v1/providers            | 403    | application/problem+json |

    Examples: staff and system accounts read everything
      | caller     | target                       | status | media            |
      | ANALYST    | /api/v1/providers/1003000111 | 200    | application/json |
      | ANALYST    | /api/v1/providers/1003000444 | 200    | application/json |
      | ANALYST    | /api/v1/providers            | 200    | application/json |
      | SUPERVISOR | /api/v1/providers/1003000111 | 200    | application/json |
      | SUPERVISOR | /api/v1/providers/1003000444 | 200    | application/json |
      | SUPERVISOR | /api/v1/providers            | 200    | application/json |
      | ADMIN      | /api/v1/providers/1003000111 | 200    | application/json |
      | ADMIN      | /api/v1/providers/1003000444 | 200    | application/json |
      | ADMIN      | /api/v1/providers            | 200    | application/json |
      | SYSTEM     | /api/v1/providers/1003000111 | 200    | application/json |
      | SYSTEM     | /api/v1/providers/1003000444 | 200    | application/json |
      | SYSTEM     | /api/v1/providers            | 200    | application/json |

    Examples: no token
      | caller | target                       | status | media                    |
      | NONE   | /api/v1/providers/1003000111 | 401    | application/problem+json |
      | NONE   | /api/v1/providers/1003000444 | 401    | application/problem+json |
      | NONE   | /api/v1/providers            | 401    | application/problem+json |

  @AC-8
  Scenario: A read failure is not reported as 'not found'
    Given only these providers are stored:
      | npi        | medicaidId | lastName | firstName |
      | 1003000111 | MCD000111  | RIVERA   | MARIA     |
    And the provider records cannot be read
    When an "ANALYST" caller sends GET "/api/v1/providers/1003000111" with correlation id "7d2f9c4e-1b3a-4c5d-8e6f-000000000123"
    Then answer 1 is a server error and not a 404
    And answer 1 has status 503 and media type "application/problem+json"
    And answer 1 carries the header "X-Correlation-Id" with value "7d2f9c4e-1b3a-4c5d-8e6f-000000000123"
    And the failure is logged at ERROR with correlation id "7d2f9c4e-1b3a-4c5d-8e6f-000000000123"

  @AC-9
  Scenario: Walk all providers of a filter by key (afterNpi) in NPI order, none missed or repeated
    Given only these providers are stored:
      | npi        | medicaidId | enrollStatus | screenFlag | riskTier |
      | 1003001500 | MCD001500  | P            | P          | L        |
    And 450 ACTIVE providers are stored with consecutive NPIs from "1003001001"
    When an "ANALYST" caller sends GET "/api/v1/providers?status=A&page=1&size=200"
    And an "ANALYST" caller starts a walk by key over "status=A" with size 200
    And a "SUPERVISOR" caller sends the status change "TERMINATE" with reason "LEIE exclusion" and effective date "2026-01-15" for provider "1003001100"
    And "ANALYST" sends the decision "APPROVE" for provider "1003001500"
    And the walk continues by key until a page holds fewer than 200 items
    And an "ANALYST" caller sends GET "/api/v1/providers?status=A&afterNpi=ABC"
    And an "ANALYST" caller sends GET "/api/v1/providers?status=A&afterNpi=12345"
    Then answer 1 has 200 items, page 1, size 200 and total 450
    And the walk has 3 pages
    And the walk lists the providers in ascending NPI order
    And the walk lists every NPI from "1003001001" to "1003001450" except "1003001100" exactly once
    And the walk lists no provider twice
    And page 2 of the walk lists "1003001201"
    And the walk lists "1003001100,1003001500" at most once
    And answer 2 is a problem+json with status 400
    And answer 3 is a problem+json with status 400
