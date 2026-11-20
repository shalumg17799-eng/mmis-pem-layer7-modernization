@MAM-59
Feature: Send notices by email for approvals, denials, rejections and revalidation
  The missing PRVNT* programs rebuilt as notification-service (D-012): ProviderStatusChanged P to A gives APPROVED and
  P to D gives DENIED, every other transition none (Q-A7 default); ApplicationRejected gives REJECTED when it names an
  NPI; RevalidationDue gives REVALIDATION_DUE; ExtractProduced gives nothing (Q-023 default). One notice per event.

  Background:
    Given Mailpit is empty

  @AC-1
  Scenario: An approval sends the 'Enrolment approved' notice
    When notification-service consumes ProviderStatusChanged for "1003000111" from "P" to "A"
    Then Mailpit holds exactly 1 email to "1003000111@providers.pem.local" with the subject "Enrolment approved"
    And 1 NoticeSent event with template "APPROVED" is written for "1003000111"

  @AC-2
  Scenario: A denial sends the DENIED notice
    When notification-service consumes ProviderStatusChanged for "1003000444" from "P" to "D"
    Then Mailpit holds exactly 1 email to "1003000444@providers.pem.local" with the subject of template "DENIED"
    And 1 NoticeSent event with template "DENIED" is written for "1003000444"

  @AC-3
  Scenario: A sanction-driven status change sends no second notice
    Given the "Suspension notice" email for "1003000111" was already sent and is the only email in Mailpit
    When notification-service consumes ProviderStatusChanged for "1003000111" from "A" to "S"
    And notification-service consumes ProviderStatusChanged for "1003000111" from "S" to "A"
    And notification-service consumes ProviderStatusChanged for "1003000111" from "A" to "T"
    And notification-service consumes ProviderStatusChanged for "1003000111" from "A" to "A"
    Then Mailpit holds 1 email in total
    And 0 NoticeSent events are written for "1003000111"

  @AC-4
  Scenario: A rejected application sends the 'Application rejected' notice
    When notification-service consumes ApplicationRejected for "1003000222" with error "E104" on "taxonomy"
    Then Mailpit holds exactly 1 email to "1003000222@providers.pem.local" with the subject "Application rejected"
    And 1 NoticeSent event with template "REJECTED" is written for "1003000222"

  @AC-5
  Scenario: A rejected application without an NPI gets no notice
    When notification-service consumes ApplicationRejected without an NPI with error "E101" on "npi"
    Then Mailpit holds 0 emails in total
    And no NoticeSent event is written at all
    And an information log line names the event id

  @AC-6
  Scenario: A revalidation due sends the REVALIDATION_DUE notice
    When notification-service consumes RevalidationDue for "1003000111" due on "2026-03-31"
    Then Mailpit holds exactly 1 email to "1003000111@providers.pem.local" with the subject of template "REVALIDATION_DUE"
    And 1 NoticeSent event with template "REVALIDATION_DUE" is written for "1003000111"

  @AC-7
  Scenario: An extract-produced event is consumed without a notice
    When notification-service consumes ExtractProduced for business date "2026-03-31" with 3 rows
    Then Mailpit holds 0 emails in total
    And no NoticeSent event is written at all

  @AC-8
  Scenario: A redelivered approval event sends one notice
    When notification-service consumes ProviderStatusChanged for "1003000111" from "P" to "A"
    And the same event is delivered to notification-service again
    Then Mailpit holds exactly 1 email to "1003000111@providers.pem.local" with the subject "Enrolment approved"
    And 1 NoticeSent event with template "APPROVED" is written for "1003000111"

  # Code review 2026-11-20, MUST-FIX 1: the failed-send and missing-template handling of D-024 (MAM-58 AC-6 and AC-7) applies to
  # these four notices too ("the same handling applies to these notice types", out of scope above). The fault is a notice
  # template that is not set up; a failed notice is recorded as FAILED, logged, and the event is acknowledged.

  @AC-1
  Scenario: An approval whose notice template is missing is recorded as FAILED and is not lost
    Given the "APPROVED" notice template is missing
    When notification-service consumes ProviderStatusChanged for "1003000111" from "P" to "A"
    Then the notice "APPROVED" for "1003000111" is recorded with status FAILED
    And the log holds the warning "NOTICE APPROVED FOR NPI 1003000111 STATUS FAILED" with the cause "NOTICE TEMPLATE NOT FOUND APPROVED"
    And Mailpit holds 0 emails in total
    And 0 NoticeSent events are written for "1003000111"

  @AC-2
  Scenario: A denial whose notice template is missing is recorded as FAILED and is not lost
    Given the "DENIED" notice template is missing
    When notification-service consumes ProviderStatusChanged for "1003000444" from "P" to "D"
    Then the notice "DENIED" for "1003000444" is recorded with status FAILED
    And the log holds the warning "NOTICE DENIED FOR NPI 1003000444 STATUS FAILED" with the cause "NOTICE TEMPLATE NOT FOUND DENIED"
    And Mailpit holds 0 emails in total
    And 0 NoticeSent events are written for "1003000444"

  @AC-4
  Scenario: A rejection whose notice template is missing is recorded as FAILED and is not lost
    Given the "REJECTED" notice template is missing
    When notification-service consumes ApplicationRejected for "1003000222" with error "E104" on "taxonomy"
    Then the notice "REJECTED" for "1003000222" is recorded with status FAILED
    And the log holds the warning "NOTICE REJECTED FOR NPI 1003000222 STATUS FAILED" with the cause "NOTICE TEMPLATE NOT FOUND REJECTED"
    And Mailpit holds 0 emails in total
    And 0 NoticeSent events are written for "1003000222"

  @AC-6
  Scenario: A revalidation notice whose template is missing is recorded as FAILED and is not lost
    Given the "REVALIDATION_DUE" notice template is missing
    When notification-service consumes RevalidationDue for "1003000111" due on "2026-03-31"
    Then the notice "REVALIDATION_DUE" for "1003000111" is recorded with status FAILED
    And the log holds the warning "NOTICE REVALIDATION_DUE FOR NPI 1003000111 STATUS FAILED" with the cause "NOTICE TEMPLATE NOT FOUND REVALIDATION_DUE"
    And Mailpit holds 0 emails in total
    And 0 NoticeSent events are written for "1003000111"
