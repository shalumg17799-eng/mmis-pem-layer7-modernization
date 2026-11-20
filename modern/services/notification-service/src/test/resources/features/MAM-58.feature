@MAM-58
Feature: Send sanction notices by email (TRM, SUS, RST), reject unknown actions and handle failures
  The PRVNTTRM, PRVNTSUS and PRVNTRST programs that PRVSAN01 GENERATE-NOTICE called dynamically are missing from the
  estate; notification-service sends the matching notice by email from SanctionApplied instead (D-012). An action
  outside T, S and R is refused, not sent as the general notice (D-026, Q-A10). A failed send or a missing template is
  logged and recorded as FAILED and never stops the processing (D-024). One notice per event (ADR-003).

  @AC-1
  Scenario: A termination sends the TRM notice
    Given the sanction notice templates TRM, SUS and RST are seeded
    And Mailpit is empty
    When notification-service consumes SanctionApplied for "1003000111" with action "T", reason "LEIE exclusion" and effective date "2026-03-31"
    Then Mailpit holds exactly 1 email to "1003000111@providers.pem.local" with the subject of template "TRM"
    And the email to "1003000111@providers.pem.local" has the body of template "TRM" filled in with NPI "1003000111"

  @AC-2
  Scenario: A suspension sends the SUS notice
    Given Mailpit is empty
    When notification-service consumes SanctionApplied for "1003000111" with action "S", reason "LEIE exclusion" and effective date "2026-03-31"
    Then Mailpit holds exactly 1 email to "1003000111@providers.pem.local" with the subject of template "SUS"
    And the email to "1003000111@providers.pem.local" has the body of template "SUS" filled in with NPI "1003000111"

  @AC-3
  Scenario: A reinstatement sends the RST notice
    Given Mailpit is empty
    When notification-service consumes SanctionApplied for "1003000111" with action "R", reason "Exclusion lifted" and effective date "2026-04-30"
    Then Mailpit holds exactly 1 email to "1003000111@providers.pem.local" with the subject of template "RST"
    And the email to "1003000111@providers.pem.local" has the body of template "RST" filled in with NPI "1003000111"

  @AC-4
  Scenario: An action outside T, S and R is refused, not sent as a general notice
    Given Mailpit is empty
    When notification-service consumes SanctionApplied for "1003000111" with action "X", reason "LEIE exclusion" and effective date "2026-03-31"
    And notification-service consumes SanctionApplied for "1003000444" with action "S", reason "LEIE exclusion" and effective date "2026-03-31"
    Then Mailpit holds exactly 0 emails to "1003000111@providers.pem.local" with the subject of template "GEN"
    And Mailpit holds exactly 1 email to "1003000444@providers.pem.local" with the subject of template "SUS"
    And Mailpit holds 1 email in total
    And 0 NoticeSent events are written for "1003000111"
    And an error log line names the first SanctionApplied event id and the action "X"

  @AC-5
  Scenario: Every sent notice is announced
    Given Mailpit is empty
    And notification-service consumes SanctionApplied for "1003000111" with action "T", reason "LEIE exclusion" and effective date "2026-03-31"
    When the outbox poller has published the NoticeSent event of "1003000111"
    Then exactly 1 NoticeSent event of this notice is on topic "pem.notification.events" with key "1003000111", npi "1003000111", template "TRM" and the stored noticeId

  @AC-6
  Scenario: A failed send is logged, surfaced and does not stop the processing
    Given the mail server cannot be reached
    When notification-service consumes SanctionApplied for "1003000111" with action "S", reason "LEIE exclusion" and effective date "2026-03-31"
    And notification-service consumes SanctionApplied for "1003000444" with action "T", reason "LEIE exclusion" and effective date "2026-03-31"
    Then a warning log line names template "SUS", NPI "1003000111", status FAILED and a cause
    And a warning log line names template "TRM", NPI "1003000444", status FAILED and a cause
    And the warning for "SUS" of "1003000111" comes before the warning for "TRM" of "1003000444"
    And 0 NoticeSent events are written for "1003000111"
    And 0 NoticeSent events are written for "1003000444"
    And the notice "SUS" for "1003000111" is recorded with status FAILED
    And the notice "TRM" for "1003000444" is recorded with status FAILED
    And notification-service has no client that could change or roll back a provider status

  @AC-7
  Scenario: A missing template is a named failure, not a crash
    Given the notice template "TRM" has been removed
    And Mailpit is empty
    When notification-service consumes SanctionApplied for "1003000111" with action "T", reason "LEIE exclusion" and effective date "2026-03-31"
    And notification-service consumes SanctionApplied for "1003000444" with action "S", reason "LEIE exclusion" and effective date "2026-03-31"
    Then a warning log line contains "NOTICE TEMPLATE NOT FOUND TRM" and status FAILED
    And Mailpit holds exactly 0 emails to "1003000111@providers.pem.local" with the subject "Termination notice"
    And 0 NoticeSent events are written for "1003000111"
    And the notice "TRM" for "1003000111" is recorded with status FAILED
    And Mailpit holds exactly 1 email to "1003000444@providers.pem.local" with the subject of template "SUS"

  @AC-8
  Scenario: A redelivered event sends one notice
    Given Mailpit is empty
    And notification-service consumes SanctionApplied for "1003000111" with action "S", reason "LEIE exclusion" and effective date "2026-03-31"
    When the same SanctionApplied event is delivered again
    Then Mailpit holds exactly 1 email to "1003000111@providers.pem.local" with the subject of template "SUS"
    And 1 NoticeSent event with template "SUS" is written for "1003000111"
