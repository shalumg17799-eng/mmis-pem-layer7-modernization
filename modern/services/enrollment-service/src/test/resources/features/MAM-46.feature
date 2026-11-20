@MAM-46
Feature: Submit an enrolment application online and validate it (E101 to E104)
  PRVENR01 VALIDATE-APPLICATION rebuilt: one error per failed check with the full four-character code, the application
  stored once, announced once and never registered when it is rejected (D-001, D-016, D-019, D-023).
  "Published" means written to the transactional outbox; AC-1 also reads the event back from Kafka.

  Background:
    Given no applications exist
    And the provider-type mapping table holds IND to IN, GRP to GR and HOSP to FA

  @AC-1
  Scenario: A valid application is accepted
    When a PROVIDER submits an application for NPI "1003000111" with type "IN" and taxonomy "207Q00000X"
    Then the answer is 201
    And the application has status "VALIDATED", source "PORTAL", provider type "IN" and no errors
    And the answer matches the enrollment-service contract for submitApplication
    And 1 "ApplicationValidated" event is published for NPI "1003000111"
    And the ApplicationValidated event for NPI "1003000111" carries Medicaid ID "M00000111", type "IN", taxonomy "207Q00000X" and the individual RIVERA MARIA MD12345 TX
    And the ApplicationValidated event of the application reaches Kafka with key "1003000111"
    And 0 "ApplicationRejected" events are published for NPI "1003000111"

  @AC-2
  Scenario Outline: A missing or malformed NPI fails with E101 and announces nothing
    When a PROVIDER submits an application for NPI "<npi>" with type "IN" and taxonomy "207Q00000X"
    Then the answer is 422
    And the problem lists exactly these errors:
      | code | field |
      | E101 | npi   |
    And the last application is stored as "REJECTED" with exactly these errors:
      | code | field |
      | E101 | npi   |
    And the same provider sees it under the REJECTED applications
    And no event is published
    Examples:
      | npi   |
      |       |
      | 12345 |

  @AC-3
  Scenario: An invalid provider type fails with E102, and IN, GR and FA are accepted
    When a PROVIDER submits an application for NPI "1003000222" with type "XX" and taxonomy "207Q00000X"
    Then the answer is 422
    And the problem lists exactly these errors:
      | code | field        |
      | E102 | providerType |
    And the last application is stored as "REJECTED" with exactly these errors:
      | code | field        |
      | E102 | providerType |
    And 1 "ApplicationRejected" event is published for NPI "1003000222"
    And the ApplicationRejected event for NPI "1003000222" carries exactly these errors:
      | code | field        |
      | E102 | providerType |
    And 0 "ApplicationValidated" events are published for NPI "1003000222"
    When a PROVIDER submits an application for NPI "1003000111" with type "IN" and taxonomy "207Q00000X"
    Then the answer is 201
    And the application has status "VALIDATED", source "PORTAL", provider type "IN" and no errors
    When a PROVIDER submits an application for NPI "1003000444" with type "GR" and taxonomy "193200000X"
    Then the answer is 201
    And the application has status "VALIDATED", source "PORTAL", provider type "GR" and no errors
    When a PROVIDER submits an application for NPI "1003000777" with type "FA" and taxonomy "282N00000X"
    Then the answer is 201
    And the application has status "VALIDATED", source "PORTAL", provider type "FA" and no errors

  @AC-4
  Scenario: A missing taxonomy fails with E104 (journey J2)
    When a PROVIDER submits an application for NPI "1003000111" with type "IN" and taxonomy ""
    Then the answer is 422
    And the problem lists exactly these errors:
      | code | field    |
      | E104 | taxonomy |
    And the last application is stored as "REJECTED" with exactly these errors:
      | code | field    |
      | E104 | taxonomy |
    And 1 "ApplicationRejected" event is published for NPI "1003000111"
    And the ApplicationRejected event for NPI "1003000111" carries exactly these errors:
      | code | field    |
      | E104 | taxonomy |
    And 0 "ApplicationValidated" events are published for NPI "1003000111"

  @AC-5
  Scenario: A duplicate NPI fails with E103; an earlier rejection is no duplicate
    Given a PROVIDER submits an application for NPI "1003000111" with type "IN" and taxonomy "207Q00000X"
    And the answer is 201
    When a PROVIDER submits an application for NPI "1003000111" with type "IN" and taxonomy "207Q00000X"
    Then the answer is 422
    And the problem lists exactly these errors:
      | code | field |
      | E103 | npi   |
    And the last application is stored as "REJECTED" with exactly these errors:
      | code | field |
      | E103 | npi   |
    And 1 "ApplicationRejected" event is published for NPI "1003000111"
    And 1 "ApplicationValidated" event is published for NPI "1003000111"
    And the first application for NPI "1003000111" is still "VALIDATED"
    Given a PROVIDER submits an application for NPI "1003000222" with type "IN" and taxonomy ""
    And the answer is 422
    When a PROVIDER submits an application for NPI "1003000222" with type "IN" and taxonomy "207Q00000X"
    Then the answer is 201
    And the application has status "VALIDATED", source "PORTAL", provider type "IN" and no errors

  @AC-6
  Scenario: Several failed checks give one error each, stored once and announced once
    When a PROVIDER submits an application for NPI "1003000333" with type "XX" and taxonomy ""
    Then the answer is 422
    And the problem lists exactly these errors:
      | code | field        |
      | E102 | providerType |
      | E104 | taxonomy     |
    And 1 application is stored for NPI "1003000333"
    And the last application is stored as "REJECTED" with exactly these errors:
      | code | field        |
      | E102 | providerType |
      | E104 | taxonomy     |
    And 1 "ApplicationRejected" event is published for NPI "1003000333"
    And the ApplicationRejected event for NPI "1003000333" carries exactly these errors:
      | code | field        |
      | E102 | providerType |
      | E104 | taxonomy     |
    And 0 "ApplicationValidated" events are published for NPI "1003000333"

  @AC-7
  Scenario: Feed provider-type codes are mapped to IN, GR or FA
    When a PROVIDER submits an application for NPI "1003000111" with type "IND" and taxonomy "207Q00000X"
    Then the answer is 201
    And the application has status "VALIDATED", source "PORTAL", provider type "IN" and no errors
    And the ApplicationValidated event for NPI "1003000111" has provider type "IN"
    When a PROVIDER submits an application for NPI "1003000444" with type "GRP" and taxonomy "193200000X"
    Then the answer is 201
    And the application has status "VALIDATED", source "PORTAL", provider type "GR" and no errors
    And the ApplicationValidated event for NPI "1003000444" has provider type "GR"
    When a PROVIDER submits an application for NPI "1003000777" with type "HOSP" and taxonomy "282N00000X"
    Then the answer is 201
    And the application has status "VALIDATED", source "PORTAL", provider type "FA" and no errors
    And the ApplicationValidated event for NPI "1003000777" has provider type "FA"
    When a PROVIDER submits an application for NPI "1003000888" with type "ZZZ" and taxonomy "207Q00000X"
    Then the answer is 422
    And the problem lists exactly these errors:
      | code | field        |
      | E102 | providerType |

  @AC-8
  Scenario Outline: Only PROVIDER and ANALYST may submit
    When a "<role>" submits a valid application for NPI "1003000555"
    Then the answer is <status>
    And the answer is problem+json unless it is 201
    Examples:
      | role       | status |
      | PROVIDER   | 201    |
      | ANALYST    | 201    |
      | SUPERVISOR | 403    |
      | ADMIN      | 403    |
      | SYSTEM     | 403    |

  @AC-8
  Scenario: No token gives 401, a broken body gives 400 and nothing is stored
    When a valid application for NPI "1003000555" is submitted without a token
    Then the answer is 401
    When a PROVIDER submits the raw body "{not json"
    Then the answer is 400
    And the answer is problem+json unless it is 201
    When a PROVIDER submits the raw body "{\"medicaidId\":\"M00000555\",\"providerType\":\"IN\",\"taxonomy\":\"207Q00000X\"}"
    Then the answer is 400
    And the answer is problem+json unless it is 201
    And 0 applications are stored
