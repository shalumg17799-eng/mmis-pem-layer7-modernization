@MAM-20
Feature: Handle unknown licences, organisation providers and sources that fail or are slow
  Real sources are sometimes down, slow or do not know a licence, and groups and facilities hold no individual licence
  (D-024, D-016, D-005, questions C-1 and C-3). A source without an answer is C (SCR-COND), never P, so nobody is
  approved on an answer that was never received (R-PRVSCR01-cbl-00010).

  Background:
    Given the sources answer clean by default and the licence board knows no licence

  @AC-1
  Scenario: A source that returns an error is recorded as not answered
    Given provider-service holds the individual "MARIA" "RIVERA" with NPI "1003000111", taxonomy "207Q00000X" and licence "MD12345" "TX"
    And the licence board answers "ACTIVE" until "2028-09" for licence "MD12345" "TX"
    And the "OIGLEIE" source answers HTTP 500 for "1003000111"
    When provider-service publishes ProviderRegistered for NPI "1003000111" and screening-service has processed it
    Then the request log of the "OIGLEIE" source shows 2 lookups for "1003000111"
    And the "OIGLEIE" result for NPI "1003000111" is "C" with no error code and a detail containing "COULD NOT BE REACHED"
    And the results for NPI "1003000111" are LICBOARD "P", OIGLEIE "C", SAMGOV "P" and SSADMF "P", each with a verdict time and a detail
    And reading the screening of NPI "1003000111" by id gives status "COMPLETED", result "FAIL" and a completion time
    And a ScreeningCompleted event with key "1003000111" reaches topic "pem.screening.events" with reason "INITIAL", result "FAIL", taxonomy "207Q00000X" and the four sources with result and detail

  @AC-1
  Scenario Outline: Whichever source returns an error is recorded as not answered after one retry, the other three are still recorded
    Given provider-service holds the individual "MARIA" "RIVERA" with NPI "1003000111", taxonomy "207Q00000X" and licence "MD12345" "TX"
    And the licence board answers "ACTIVE" until "2028-09" for licence "MD12345" "TX"
    And the "<source>" source answers HTTP 500 for "<key>"
    When provider-service publishes ProviderRegistered for NPI "1003000111" and screening-service has processed it
    Then the request log of the "<source>" source shows 2 lookups for "<key>"
    And the "<source>" result for NPI "1003000111" is "C" with no error code and a detail containing "COULD NOT BE REACHED"
    And the "<source>" result for NPI "1003000111" is "C" and the other three are "P"
    And reading the screening of NPI "1003000111" by id gives status "COMPLETED", result "FAIL" and a completion time
    Examples:
      | source   | key        |
      | LICBOARD | MD12345    |
      | OIGLEIE  | 1003000111 |
      | SAMGOV   | 1003000111 |
      | SSADMF   | 1003000111 |

  @AC-2
  Scenario: A slow source does not hold the screening
    Given provider-service holds the individual "MARIA" "RIVERA" with NPI "1003000111", taxonomy "207Q00000X" and licence "MD12345" "TX"
    And the licence board answers "ACTIVE" until "2028-09" for licence "MD12345" "TX"
    And the "SAMGOV" source answers only after 3000 milliseconds for "1003000111"
    When provider-service publishes ProviderRegistered for NPI "1003000111" and screening-service has processed it
    Then the "SAMGOV" result for NPI "1003000111" is "C" with no error code and a detail containing "DID NOT ANSWER IN TIME"
    And the "SAMGOV" result for NPI "1003000111" is "C" and the other three are "P"
    And the screening of NPI "1003000111" took less than 5 seconds
    And reading the screening of NPI "1003000111" by id gives status "COMPLETED", result "FAIL" and a completion time

  @AC-2
  Scenario Outline: Whichever source is slow, the screening is completed within 5 seconds
    Given provider-service holds the individual "MARIA" "RIVERA" with NPI "1003000111", taxonomy "207Q00000X" and licence "MD12345" "TX"
    And the licence board answers "ACTIVE" until "2028-09" for licence "MD12345" "TX"
    And the "<source>" source answers only after 3000 milliseconds for "<key>"
    When provider-service publishes ProviderRegistered for NPI "1003000111" and screening-service has processed it
    Then the "<source>" result for NPI "1003000111" is "C" with no error code and a detail containing "DID NOT ANSWER IN TIME"
    And the screening of NPI "1003000111" took less than 5 seconds
    Examples:
      | source   | key        |
      | LICBOARD | MD12345    |
      | OIGLEIE  | 1003000111 |
      | SSADMF   | 1003000111 |

  @AC-3
  Scenario: A licence the board does not know fails without an error code
    Given provider-service holds the individual "TESTPROV" "FOXTROT" with NPI "1003000666", taxonomy "207Q00000X" and licence "XX00000" "TX"
    When provider-service publishes ProviderRegistered for NPI "1003000666" and screening-service has processed it
    Then the "LICBOARD" result for NPI "1003000666" is "F" with no error code and a detail containing "LICENCE NOT FOUND"
    And the "LICBOARD" result for NPI "1003000666" is "F" and the other three are "P"
    And reading the screening of NPI "1003000666" by id gives status "COMPLETED", result "FAIL" and a completion time

  @AC-4
  Scenario Outline: Groups and facilities are not checked against a licence
    Given provider-service holds the organisation "<name>" of type "<type>" with NPI "<npi>" and no owner NPI
    When provider-service publishes ProviderRegistered for NPI "<npi>" and screening-service has processed it
    Then the "LICBOARD" result for NPI "<npi>" is "P" with no error code and a detail containing "NOT APPLICABLE"
    And the licence board request log shows no lookup
    And the request log of the "OIGLEIE" source shows 1 lookup for "<npi>"
    And the request log of the "SAMGOV" source shows 1 lookup for "<npi>"
    And the request log of the "SSADMF" source shows 1 lookup for "<npi>"
    And reading the screening of NPI "<npi>" by id gives status "COMPLETED", result "PASS" and a completion time
    Examples:
      | name                | type | npi        |
      | SUNSET HOSPICE INC  | FA   | 1003000333 |
      | NORTHSIDE CLINIC GR | GR   | 1003000222 |

  @AC-5
  Scenario: A provider that cannot be read is not screened on made-up data and is screened once when provider-service is back
    Given provider-service answers HTTP 503 for NPI "1003000777"
    When provider-service publishes ProviderRegistered for NPI "1003000777" and does not wait for it
    Then provider-service has been asked about NPI "1003000777" at least 3 times
    And no screening is stored for NPI "1003000777"
    And no ScreeningCompleted event is written for NPI "1003000777"
    And no source was asked about anything
    When provider-service is back with the individual "TESTPROV" "ECHO" with NPI "1003000777" and licence "MD77777" "TX"
    Then the registration of NPI "1003000777" is processed by screening-service
    And exactly 1 COMPLETED screening exists for NPI "1003000777"
    And 1 ScreeningCompleted event is written for NPI "1003000777"
