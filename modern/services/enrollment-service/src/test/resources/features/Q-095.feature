@MAM-46 @Q-095
Feature: An individual provider applies only under the NPI in their own token (Q-095, user decision 2026-11-19)
  POST /api/v1/applications: a PROVIDER may submit an individual application (type IN, or a feed type mapped to IN)
  only for the npi claim of their token; any other NPI, or a token without an npi claim, is 403 problem+json and
  nothing is stored or published. Group and facility applications, and staff callers, are not restricted.

  Background:
    Given no applications exist
    And the provider-type mapping table holds IND to IN, GRP to GR and HOSP to FA

  Scenario Outline: An individual application under the provider's own NPI is accepted
    When the PROVIDER whose token holds NPI "1003000111" applies for NPI "1003000111" with type "<type>"
    Then the own-NPI answer is 201 with status "VALIDATED" and provider type "IN"
    And 1 "ApplicationValidated" event is published for NPI "1003000111"

    Examples:
      | type |
      | IN   |
      | IND  |

  Scenario Outline: An individual application under another NPI is refused, nothing is stored or published
    When the PROVIDER whose token holds NPI "1003000111" applies for NPI "1003000222" with type "<type>"
    Then the own-NPI answer is 403 problem+json
    And 0 applications are stored
    And no event is published

    Examples:
      | type |
      | IN   |
      | IND  |

  Scenario Outline: A group or facility application under another NPI is handled as before
    When the PROVIDER whose token holds NPI "1003000111" applies for NPI "1003000444" with type "<type>"
    Then the own-NPI answer is 201 with status "VALIDATED" and provider type "<legacy>"
    And 1 "ApplicationValidated" event is published for NPI "1003000444"

    Examples:
      | type | legacy |
      | GR   | GR     |
      | GRP  | GR     |
      | FA   | FA     |

  Scenario: An individual application from a provider token without an npi claim is refused
    When a PROVIDER whose token has no npi claim applies for NPI "1003000111" with type "IN"
    Then the own-NPI answer is 403 problem+json
    And 0 applications are stored
    And no event is published

  Scenario: Staff are not restricted by the rule
    When a "ANALYST" submits a valid application for NPI "1003000555"
    Then the answer is 201
    And 1 "ApplicationValidated" event is published for NPI "1003000555"
