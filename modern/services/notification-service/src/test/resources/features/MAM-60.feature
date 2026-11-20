@MAM-60
Feature: List the notices sent and the notice templates
  The notice log is readable by staff and, for their own NPI only, by the provider (D-012, D-023); the templates are
  listed for administrators.

  Background:
    Given the notice log holds:
      | npi        | template | status |
      | 1003000111 | APPROVED | SENT   |
      | 1003000444 | DENIED   | SENT   |

  @AC-1
  Scenario: Staff list the notices of a provider
    When "ANALYST" lists the notices with "npi=1003000111"
    Then the notice list answers 200 with only notices of "1003000111", each with noticeId, npi, template, channel EMAIL, status and sentAt, page 0, size 20 and a total

  @AC-2
  Scenario: Invalid filters are refused
    When "ANALYST" lists the notices with "npi=ABC"
    Then the notice list answers 400 problem+json
    When "ANALYST" lists the notices with "size=500"
    Then the notice list answers 400 problem+json

  @AC-3
  Scenario: A provider sees only their own notices
    When "PROVIDER" lists the notices with ""
    Then the notice list answers 200 with only notices of "1003000111", each with noticeId, npi, template, channel EMAIL, status and sentAt, page 0, size 20 and a total
    When "PROVIDER" lists the notices with "npi=1003000444"
    Then the notice list answers 403 problem+json

  @AC-4
  Scenario: The provider sees the approval notice
    Given the notice log is empty
    When notification-service consumes ProviderStatusChanged for "1003000111" from "P" to "A"
    And "PROVIDER" lists the notices with ""
    Then the notice list holds a notice for "1003000111" with template "APPROVED", channel "EMAIL" and status "SENT"

  @AC-5
  Scenario: A failed notice shows as FAILED
    Given the notice log also holds a "FAILED" notice "SUS" for "1003000111"
    When "ANALYST" lists the notices with "npi=1003000111"
    Then the notice list holds a notice for "1003000111" with template "SUS", channel "EMAIL" and status "FAILED"

  @AC-6
  Scenario Outline: Who may list notices
    When "<role>" lists the notices with ""
    Then the notice list answers <status> and shows NPIs "<npis>"
    Examples:
      | role       | status | npis                  |
      | ANALYST    | 200    | 1003000111,1003000444 |
      | SUPERVISOR | 200    | 1003000111,1003000444 |
      | ADMIN      | 200    | 1003000111,1003000444 |
      | PROVIDER   | 200    | 1003000111            |
      | SYSTEM     | 403    |                       |
      | NONE       | 401    |                       |

  @AC-7
  Scenario: An administrator lists the templates
    When "ADMIN" lists the notice templates
    Then the template list answers 200 with exactly the codes "TRM,SUS,RST,GEN,APPROVED,DENIED,REJECTED,REVALIDATION_DUE,EXTRACT_READY", each with code, subject and body

  @AC-8
  Scenario Outline: Only ADMIN can list templates
    When "<role>" lists the notice templates
    Then the template list answers <status>
    Examples:
      | role       | status |
      | ANALYST    | 403    |
      | SUPERVISOR | 403    |
      | PROVIDER   | 403    |
      | SYSTEM     | 403    |
      | NONE       | 401    |
