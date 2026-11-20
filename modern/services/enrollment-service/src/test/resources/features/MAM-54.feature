@MAM-54
Feature: Read application status (a provider sees their own; an analyst sees all)
  The APPERROR file review replaced by a read API: a PROVIDER reads only the applications they submitted or that carry
  their own NPI; staff read every application (D-023); every failed check is listed once (D-001, D-016).

  Background:
    Given no applications exist
    And user A (PROVIDER, NPI "1003000111") submitted a valid application for NPI "1003000111"
    And user B (PROVIDER, NPI "1003000222") submitted a valid application for NPI "1003000222"

  @AC-1
  Scenario: A provider lists only their own applications
    When user A lists the applications
    Then the list holds only the applications for NPI "1003000111" and its total is 1
    When user A lists the applications for NPI "1003000222"
    Then the list is empty and its total is 0

  @AC-2
  Scenario: A provider cannot open someone else's application
    When user A reads user B's application by id
    Then the read answers 403 problem+json without "1003000222"

  @AC-3
  Scenario: A validated application shows its status
    When user A reads their own application by id
    Then the read answers 200 with status "VALIDATED", no errors, source "PORTAL", NPI "1003000111", Medicaid ID "M00000111", type "IN", taxonomy "207Q00000X", the individual RIVERA MARIA MD12345 TX and a submission time

  @AC-4
  Scenario: A rejected application shows exactly one error per failed check
    Given no applications exist
    And user A submitted an application for NPI "1003000111" with type "IN" and an empty taxonomy
    And user A submitted an application for NPI "1003000111" with type "XX" and an empty taxonomy
    When user A reads the first rejected application by id
    Then the read answers 200 with status "REJECTED" and exactly these errors: "E104/taxonomy"
    When user A reads the second rejected application by id
    Then the read answers 200 with status "REJECTED" and exactly these errors: "E102/providerType,E104/taxonomy"

  @AC-5
  Scenario Outline: An analyst sees every application and can filter
    Given a REJECTED application loaded from a legacy file for NPI "1003000333"
    And user A submitted an application for NPI "1003000111" with type "IN" and an empty taxonomy
    When "<role>" lists the applications with status "REJECTED"
    Then the list holds the REJECTED applications of NPI "1003000333" from "LEGACY_FILE" and NPI "1003000111" from "PORTAL", with page 0 and size 20
    When "<role>" lists the applications for NPI "1003000111"
    Then every listed application is for NPI "1003000111"
    Examples:
      | role       |
      | ANALYST    |
      | SUPERVISOR |

  @AC-6
  Scenario: Paging and filter values are checked
    When "ANALYST" lists the applications with the query "size=201"
    Then the list answers 400 problem+json
    When "ANALYST" lists the applications with the query "status=FOO"
    Then the list answers 400 problem+json
    When "ANALYST" lists the applications with the query "npi=123"
    Then the list answers 400 problem+json
    When "ANALYST" lists the applications with the query "size=200"
    Then the list answers 200

  @AC-7
  Scenario: An unknown application id is not found
    When "ANALYST" reads the application "00000000-0000-0000-0000-000000000000"
    Then the read answers 404 problem+json without "1003000111"

  @AC-8
  Scenario Outline: Only PROVIDER, ANALYST and SUPERVISOR may read applications
    When "<role>" lists the applications and reads user A's application by id
    Then both answers are <status>
    Examples:
      | role       | status |
      | PROVIDER   | 200    |
      | ANALYST    | 200    |
      | SUPERVISOR | 200    |
      | ADMIN      | 403    |
      | SYSTEM     | 403    |
      | NONE       | 401    |
