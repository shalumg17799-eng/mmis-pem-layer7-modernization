@MAM-65
Feature: FHIR R4 Practitioner, Organization and PractitionerRole (read-only, public)
  GET /fhir/Practitioner, /fhir/Organization and /fhir/PractitionerRole answer anyone, with no token, from the
  directory's own data (ADR-006, Q-008, D-028, D-008, D-006): ACTIVE providers only, searchset Bundles as
  application/fhir+json, only the facts the legacy master holds (identifiers, name, type, taxonomy, licence), no write.

  @AC-1
  Scenario: Practitioner search by NPI answers a searchset Bundle without a token
    Given the FHIR directory holds:
      | npi        | medicaidId | type | tier | status | taxonomy   | firstName | lastName | legalName          | licenseNo | licenseState |
      | 1003000111 | M00000111  | IN   | L    | A      | 207Q00000X | MARIA     | RIVERA   |                    | MD12345   | TX           |
      | 1003000333 | M00000333  | FA   | H    | A      | 251G00000X |           |          | SUNSET HOSPICE INC |           |              |
    When anyone searches FHIR "Practitioner" with "identifier=http://hl7.org/fhir/sid/us-npi|1003000111"
    Then the FHIR answer is 200 with content type "application/fhir+json"
    And the FHIR Bundle is a searchset with total 1
    And the FHIR Bundle entries are "Practitioner" resources for the NPIs "1003000111"
    And the FHIR Practitioner "1003000111" carries the NPI identifier "1003000111"

  @AC-2
  Scenario: The Practitioner carries only facts the legacy record holds
    Given the FHIR directory holds:
      | npi        | medicaidId | type | tier | status | taxonomy   | firstName | lastName | legalName | licenseNo | licenseState |
      | 1003000111 | M00000111  | IN   | L    | A      | 207Q00000X | MARIA     | RIVERA   |           | MD12345   | TX           |
    When anyone searches FHIR "Practitioner" with "name=RIVERA"
    Then the FHIR Bundle entries are "Practitioner" resources for the NPIs "1003000111"
    And the FHIR Practitioner "1003000111" has the identifiers NPI "1003000111" and Medicaid ID "M00000111"
    And the FHIR Practitioner "1003000111" has the name family "RIVERA" and given "MARIA"
    And the FHIR Practitioner "1003000111" has the qualification "MD12345" issued by "TX"
    And the FHIR Practitioner "1003000111" holds no address, telecom or network element

  @AC-3
  Scenario: Organization search finds groups and facilities, never individuals
    Given the FHIR directory holds:
      | npi        | medicaidId | type | tier | status | taxonomy   | firstName | lastName | legalName          | licenseNo | licenseState |
      | 1003000333 | M00000333  | FA   | H    | A      | 251G00000X |           |          | SUNSET HOSPICE INC |           |              |
      | 1003000111 | M00000111  | IN   | L    | A      | 207Q00000X | MARIA     | RIVERA   |                    | MD12345   | TX           |
    When anyone searches FHIR "Organization" with "name=HOSPICE"
    Then the FHIR Bundle is a searchset with total 1
    And the FHIR Bundle entries are "Organization" resources for the NPIs "1003000333"
    And the FHIR Organization "1003000333" has the identifiers NPI "1003000333" and Medicaid ID "M00000333"
    And the FHIR Organization "1003000333" is named "SUNSET HOSPICE INC" with the legacy type code "FA"
    When anyone searches FHIR "Organization" with "identifier=http://hl7.org/fhir/sid/us-npi|1003000333"
    Then the FHIR Bundle is a searchset with total 1
    And the FHIR Bundle entries are "Organization" resources for the NPIs "1003000333"
    And the FHIR Organization "1003000333" is named "SUNSET HOSPICE INC" with the legacy type code "FA"
    When anyone searches FHIR "Organization" with "identifier=http://hl7.org/fhir/sid/us-npi|1003000111"
    Then the FHIR Bundle is a searchset with total 0

  @AC-4
  Scenario: PractitionerRole carries the taxonomy as the specialty
    Given the FHIR directory holds:
      | npi        | medicaidId | type | tier | status | taxonomy   | firstName | lastName | legalName | licenseNo | licenseState |
      | 1003000111 | M00000111  | IN   | L    | A      | 207Q00000X | MARIA     | RIVERA   |           | MD12345   | TX           |
      | 1003000555 | M00000555  | IN   |      | A      | 225100000X | PAT       | SAMPLE   |           |           |              |
    When anyone searches FHIR "PractitionerRole" with "specialty=207Q00000X"
    Then the FHIR Bundle is a searchset with total 1
    And the FHIR Bundle entries are "PractitionerRole" resources for the NPIs "1003000111"
    And the FHIR PractitionerRole "1003000111" references the practitioner "1003000111" with the specialty code "207Q00000X"

  @AC-5
  Scenario: Only ACTIVE providers appear, and a reinstatement brings the provider back
    Given the FHIR directory holds:
      | npi        | medicaidId | type | tier | status | taxonomy   | firstName | lastName | legalName          | licenseNo | licenseState |
      | 1003000333 | M00000333  | FA   | H    | S      | 251G00000X |           |          | SUNSET HOSPICE INC |           |              |
      | 1003000111 | M00000111  | IN   | L    | P      | 207Q00000X | MARIA     | RIVERA   |                    | MD12345   | TX           |
    And provider-service holds the facility "SUNSET HOSPICE INC" doing business as "SUNSET HOSPICE CARE" with NPI "1003000333", Medicaid ID "M00000333" and taxonomy "251G00000X"
    When anyone searches FHIR "Practitioner" with "name=RIVERA"
    Then the FHIR Bundle is a searchset with total 0
    When anyone searches FHIR "Organization" with "name=HOSPICE"
    Then the FHIR Bundle is a searchset with total 0
    When anyone searches FHIR "PractitionerRole" with "specialty=207Q00000X"
    Then the FHIR Bundle is a searchset with total 0
    When directory-service consumes ProviderStatusChanged for NPI "1003000333" from "S" to "A"
    And anyone searches FHIR "Organization" with "identifier=http://hl7.org/fhir/sid/us-npi|1003000333"
    Then the FHIR Bundle entries are "Organization" resources for the NPIs "1003000333"
    And the FHIR Organization "1003000333" is named "SUNSET HOSPICE INC" with the legacy type code "FA"
    When anyone searches FHIR "Practitioner" with "identifier=http://hl7.org/fhir/sid/us-npi|1003000111"
    Then the FHIR Bundle is a searchset with total 0
    When anyone searches FHIR "PractitionerRole" with "specialty=207Q00000X"
    Then the FHIR Bundle is a searchset with total 0
    When anyone searches FHIR "Organization" with "name=SUNSET"
    Then the FHIR Bundle entries are "Organization" resources for the NPIs "1003000333"

  @AC-6
  Scenario: The FHIR API is read-only
    Given the FHIR directory holds:
      | npi        | medicaidId | type | tier | status | taxonomy   | firstName | lastName | legalName | licenseNo | licenseState |
      | 1003000111 | M00000111  | IN   | L    | A      | 207Q00000X | MARIA     | RIVERA   |           | MD12345   | TX           |
    And the FHIR Practitioner for the NPI "1003000111" is remembered
    When a caller sends POST, PUT and DELETE to "/fhir/Practitioner"
    Then each of the three calls was rejected with a 4xx status and never a 2xx
    And the FHIR Practitioner for the NPI "1003000111" is unchanged

  @AC-7
  Scenario: The resources are valid FHIR R4 (structural check here; the HAPI validator checks the same Bundles in FhirR4HapiValidatorTest)
    Given the FHIR directory holds:
      | npi        | medicaidId | type | tier | status | taxonomy   | firstName | lastName | legalName           | licenseNo | licenseState |
      | 1003000111 | M00000111  | IN   | L    | A      | 207Q00000X | MARIA     | RIVERA   |                     | MD12345   | TX           |
      | 1003000444 | M00000444  | GR   |      | A      | 251E00000X |           |          | EXCLUDED CLINIC LLC |           |              |
      | 1003000333 | M00000333  | FA   | H    | A      | 251G00000X |           |          | SUNSET HOSPICE INC  |           |              |
    When anyone searches FHIR "Practitioner" with ""
    And anyone searches FHIR "Organization" with ""
    And anyone searches FHIR "PractitionerRole" with ""
    Then every FHIR Bundle answered so far has no structural error against FHIR R4
    And the FHIR Bundles answered so far hold 4 resources in total

  @AC-8
  Scenario: A malformed identifier search is rejected
    Given the FHIR directory holds:
      | npi        | medicaidId | type | tier | status | taxonomy   | firstName | lastName | legalName | licenseNo | licenseState |
      | 1003000111 | M00000111  | IN   | L    | A      | 207Q00000X | MARIA     | RIVERA   |           | MD12345   | TX           |
    When anyone searches FHIR "Practitioner" with "identifier=http://hl7.org/fhir/sid/us-npi|ABC"
    Then the FHIR answer is 400 with content type "application/problem+json"
