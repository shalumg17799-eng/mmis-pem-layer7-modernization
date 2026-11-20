@MAM-40
Feature: Provider history: every change and every notice sent is audited and readable
  Every change of a provider's record writes one entry to audit_log in the transaction of the change (who, when, what, before
  and after, which request), a NoticeSent adds a reference once (D-012), the history is read oldest first and paged by
  SUPERVISOR and ADMIN only (D-023), and nothing can edit or delete it (D-018, Q-012).

  Background:
    Given no provider and no history exist
    And enrollment-service registered provider "1003000111" as the individual "RIVERA" "MARIA" with Medicaid ID "MCD000111"
    And screening-service reported "PASS" for provider "1003000111"
    And risk-service assigned risk tier "L" to provider "1003000111"
    And "analyst1" approved provider "1003000111" with correlation id "5a1e7b20-3c4d-4e5f-9a6b-000000000456"
    And a SYSTEM caller suspended provider "1003000111" with reason "LEIE exclusion", effective date "2026-03-31" and sanction id "0a3c1f7e-5b2d-4c8a-9e61-000000000001"

  @AC-1
  Scenario: Every change writes one history entry, oldest first
    When a "SUPERVISOR" caller sends GET "/api/v1/providers/1003000111/audit"
    Then answer 1 has status 200
    And answer 1 lists exactly 5 history entries
    And answer 1 lists the history actions "REGISTERED, SCREENING_VERDICT, RISK_TIER_ASSIGNED, APPROVED, SUSPENDED" oldest first
    And answer 1 matches the provider-service contract for getProviderAudit

  @AC-2
  Scenario: An entry shows before, after and the request it belongs to
    When a "SUPERVISOR" caller sends GET "/api/v1/providers/1003000111/audit"
    Then the "APPROVED" entry of answer 1 has these fields:
      | /actor                  | analyst1                             |
      | /action                 | APPROVED                             |
      | /before/enrollStatus    | P                                    |
      | /after/enrollStatus     | A                                    |
      | /correlationId          | 5a1e7b20-3c4d-4e5f-9a6b-000000000456 |
    And the "APPROVED" entry of answer 1 has a time and the due date five years after today
    And the "REGISTERED" entry of answer 1 has these fields:
      | /before                 | <absent>                             |
      | /after/enrollStatus     | P                                    |
      | /after/screenFlag       | U                                    |

  @AC-3
  Scenario: The actor is the user or the producing service
    When a "SUPERVISOR" caller sends GET "/api/v1/providers/1003000111/audit"
    Then the "APPROVED" entry of answer 1 has these fields:
      | /actor | analyst1 |
    And the "SUSPENDED" entry of answer 1 has these fields:
      | /actor | service-account-pem-services |
    And the "REGISTERED" entry of answer 1 has these fields:
      | /actor | enrollment-service |
    And the "SCREENING_VERDICT" entry of answer 1 has these fields:
      | /actor | screening-service |
    And the "RISK_TIER_ASSIGNED" entry of answer 1 has these fields:
      | /actor | risk-service |

  @AC-4
  Scenario: A notice sent is recorded once
    Given the fields and version of provider "1003000111" are noted
    When notification-service sends NoticeSent "e5e5e5e5-0000-4000-8000-0000000000e5" with notice "a1a1a1a1-0000-4000-8000-0000000000a1" and template "APPROVED" for provider "1003000111"
    And the same NoticeSent is delivered a second time
    Then the history of provider "1003000111" has exactly 1 entry referring to notice "a1a1a1a1-0000-4000-8000-0000000000a1"
    And provider "1003000111" has the same fields and version as noted

  @AC-5
  Scenario: A status change entry carries reason, effective date and sanction id
    When a "SUPERVISOR" caller sends GET "/api/v1/providers/1003000111/audit"
    And a "SUPERVISOR" caller sends GET "/api/v1/providers/1003000111"
    Then the "SUSPENDED" entry of answer 1 has these fields:
      | /before/enrollStatus         | A                                    |
      | /after/enrollStatus          | S                                    |
      | /after/statusChange/reason        | LEIE exclusion                       |
      | /after/statusChange/effectiveDate | 2026-03-31                           |
      | /after/statusChange/sanctionId    | 0a3c1f7e-5b2d-4c8a-9e61-000000000001 |
    And answer 2 has these fields:
      | /enrollStatus | S        |
      | /endDate      | <absent> |

  @AC-6
  Scenario: The history is paged
    Given only these providers are stored:
      | npi        | medicaidId | lastName | firstName |
      | 1003000111 | MCD000111  | RIVERA   | MARIA     |
    And provider "1003000111" has 25 history entries
    When a "SUPERVISOR" caller sends GET "/api/v1/providers/1003000111/audit?page=1&size=20"
    Then answer 1 has 5 items, page 1, size 20 and total 25
    And answer 1 lists the history actions "ENTRY_21, ENTRY_22, ENTRY_23, ENTRY_24, ENTRY_25" on this page

  @AC-7
  Scenario Outline: Only SUPERVISOR and ADMIN can read it; an unknown provider gives 404
    When a "<caller>" caller sends GET "<target>"
    Then answer 1 has status <status> and media type "<media>"

    Examples:
      | caller              | target                       | status | media                    |
      | SUPERVISOR          | /api/v1/providers/1003000111/audit | 200    | application/json         |
      | ADMIN               | /api/v1/providers/1003000111/audit | 200    | application/json         |
      | ANALYST             | /api/v1/providers/1003000111/audit | 403    | application/problem+json |
      | PROVIDER:1003000111 | /api/v1/providers/1003000111/audit | 403    | application/problem+json |
      | SYSTEM              | /api/v1/providers/1003000111/audit | 403    | application/problem+json |
      | NONE                | /api/v1/providers/1003000111/audit | 401    | application/problem+json |
      | SUPERVISOR          | /api/v1/providers/1003000999/audit | 404    | application/problem+json |

  @AC-8
  Scenario: The history is append-only
    When a "SUPERVISOR" caller sends GET "/api/v1/providers/1003000111/audit"
    And notification-service sends NoticeSent "e6e6e6e6-0000-4000-8000-0000000000e6" with notice "b2b2b2b2-0000-4000-8000-0000000000b2" and template "SUSPENDED" for provider "1003000111"
    And a "SUPERVISOR" caller sends GET "/api/v1/providers/1003000111/audit"
    And a "SUPERVISOR" caller sends PUT "/api/v1/providers/1003000111/audit"
    And a "SUPERVISOR" caller sends PATCH "/api/v1/providers/1003000111/audit"
    And a "SUPERVISOR" caller sends DELETE "/api/v1/providers/1003000111/audit"
    Then the first 5 entries of answer 2 are unchanged in answer 1
    And answer 2 has 6 entries and the entry "NOTICE_SENT" is the last one
    And answer 3 is a problem+json with status 405
    And answer 4 is a problem+json with status 405
    And answer 5 is a problem+json with status 405
    And answer 3 allows only GET
    And answer 4 allows only GET
    And answer 5 allows only GET
