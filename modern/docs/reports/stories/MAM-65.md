```
Phase / item:      MAM-65 FHIR R4 Practitioner, Organization and PractitionerRole (read-only, public)
Outcome:           BUILT (build-phase: tests in Part D), batch A6
What was done:     - directory-service GET /fhir/Practitioner (identifier system|NPI, name), /fhir/Organization (identifier,
                     name), /fhir/PractitionerRole (specialty = taxonomy, code or system|code): ACTIVE only, searchset
                     Bundle (total = every match, at most 200 entries, search.mode match), application/fhir+json, PUBLIC;
                     only legacy facts (Q-008): NPI (us-npi) and Medicaid ID identifiers, names, licence as qualification
                     (issuer = state), legal name, provider type code, taxonomy (NUCC system) as specialty; malformed
                     identifier -> 400 problem+json; only GET mapped (POST/PUT/DELETE 405)
                   - The profile (first/last/legal name, licence) is kept in the existing directory_entry columns when a
                     provider becomes ACTIVE (ProjectDirectory reads it from provider-service); the MAM-51 directory seed
                     fills those columns (generator extended; provider seed byte-identical)
                   - AC status (live, read-only): AC-1 checked (ALPHA by identifier: total 1); AC-2 checked (family/given,
                     Medicaid ID, licence SD10001 TX, no address/telecom); AC-3 checked (HOSPICE by name and identifier:
                     SAMPLE HOSPICE 01, type FA, no individual); AC-4 checked (specialty 207Q00000X: ACTIVE IN only); AC-5
                     checked in part (suspended FOXTROT total 0), reinstatement not live; AC-6 checked (405); AC-7 not run
                     (no HAPI validator here); AC-8 checked (ABC -> 400)
Files created/changed: directory-service application/ProviderProfile, ProviderProfiles, FhirDirectory, ProjectDirectory;
                     infrastructure/JdbcFhirDirectory, ProviderServiceClient; api/FhirController; testdata/generate_seed.py,
                     testdata/seed/directory_db/02-directory.sql
Checks run:        directory-service:test 29/29; bootJar; live checks above
Not verified:      automated tests (Part D); AC-7 HAPI validation; AC-5 reinstatement live; providers activated before
                   this deploy get their profile only at the next seed or status change
Questions for you: none
```

Lane 3 tests (2026-11-19)
```
Item:              MAM-65 FHIR R4 Practitioner, Organization and PractitionerRole (test-writer, Lane 3)
Files (all under modern/services/directory-service/src/test; hashes in modern/docs/reports/stories/MAM-65.tests.sha256):
                   resources/features/MAM-65.feature; java/gov/medicaid/pem/directory/: acceptance/FhirSteps,
                   api/FhirApiTest, api/FhirContractTest, api/FhirResourceMappingTest, api/FhirR4ValidityTest,
                   infrastructure/JdbcFhirDirectoryTest, application/ProjectDirectoryProfileTest, Fhir4Shape,
                   Fhir4ShapeTest; shared with MAM-67: DirectoryFixtures, ContractFile
Counts:            172 tests = 8 Cucumber scenarios + 69 unit + 81 integration (Testcontainers Postgres, Kafka for
                   the reinstatement) + 14 contract; 169 pass, 3 fail (below)
Command / result:  ./gradlew --no-daemon :services:directory-service:check --continue -> FAILED: 5 tests fail (3 of
                   MAM-65, 2 of MAM-67) and spotlessJavaCheck fails on 4 production files; of 391 tests 386 pass.
                   jacocoTestCoverageVerification (run with -x test on the same run) passed: line coverage 99.39 %
                   (488/491); pitest passed: 97 % (33/34, the survivor is the equivalent boundary mutant
                   DirectoryRules.java:22). Reports: build/reports/jacoco/test/html/index.html and
                   build/reports/pitest/index.html
AC -> test:        AC-1 feature @AC-1; FhirApiTest (practitionerByNpi, publicWithoutToken, signedInSameAnswer,
                   practitionersInNpiOrder, unknownNpi, fhirAccept); FhirResourceMappingTest (searchset, search.mode,
                   total beyond entries, limit 200, identifier forms); FhirContractTest (PUBLIC, fhir+json,
                   FhirBundle); JdbcFhirDirectoryTest (individuals, limit and total)
                   AC-2 feature @AC-2; FhirApiTest (practitionerFacts, noContactData, name any case);
                   FhirResourceMappingTest (identifiers, name, qualification, only legacy facts); JdbcFhirDirectoryTest
                   (name filters, literal wildcards, saveProfile cuts); ProjectDirectoryProfileTest
                   AC-3 feature @AC-3; FhirApiTest (organizationSearches, organizationsOnly); FhirResourceMappingTest
                   (type code FA/GR, legal name, fallbacks); JdbcFhirDirectoryTest (organizations)
                   AC-4 feature @AC-4; FhirApiTest (roleBySpecialty, unknown, organisation taxonomy);
                   FhirResourceMappingTest (reference, specialty, system|code, blank 400); JdbcFhirDirectoryTest
                   (taxonomy exact, limit)
                   AC-5 feature @AC-5 (suspended + pending, then ProviderStatusChanged S to A through Kafka);
                   FhirApiTest (onlyActive for P, S, T, D; suspendedByNpi; reactivated); ProjectDirectoryProfileTest
                   (profile kept after the entry, not read for P, S, T, D, reinstated)
                   AC-6 feature @AC-6; FhirApiTest (readOnly: POST, PUT, DELETE, PATCH on the 3 paths -> 405;
                   adminCannotWrite); FhirContractTest (only GET in the contract)
                   AC-7 feature @AC-7; FhirR4ValidityTest; Fhir4ShapeTest (the shape check catches what it must)
                   AC-8 feature @AC-8; FhirApiTest (5 malformed identifiers x 2 paths, Accept fhir+json);
                   FhirResourceMappingTest (11 identifiers, 4 specialties); FhirContractTest (Problem, 3 operations)
Failing tests (kept, not weakened):
                   FhirR4ValidityTest.fullUrl x3 (Practitioner, Organization, PractitionerRole), AC-7:
                   "Bundle.entry[0].fullUrl is missing". DEFECT: FhirController.java:106-109 (bundle(): each entry
                   has only resource and search). R4: "Except for transactions and batches, each entry in a Bundle
                   must have a fullUrl", which the HL7 validator reports as an error. Fix: add fullUrl
                   (<server base>/fhir/<Type>/<npi>); FhirBundle.entry is an open object in the contract, so no
                   contract change. The rule is stated from the R4 specification, not checked with HAPI (below).
NEEDS DECISION:    AC-7 asks for the HAPI FHIR validator. It is an allowed library (java-microservices 4, directory
                   and provider only) but is not in the build, and adding it needs a change to directory-service/
                   build.gradle.kts (testImplementation hapi-fhir-validation, structures-r4, validation-resources-r4)
                   and gradle/libs.versions.toml. Not added. AC-7 is tested with Fhir4Shape, a structural R4 check
                   (element names per type, Bundle and search codes, id format, absolute URIs, no empty values,
                   qualification.code 1..1, org-1). Recommended default: add HAPI as a test-only dependency with a
                   pinned version, then replace Fhir4Shape in FhirR4ValidityTest.
Other findings:    (1) Spotless fails on production files DiroutFile.java, FhirController.java (also a line over
                   100 columns at :88), FhirDirectory.java, DirectoryExtractJobConfig.java (formatting only; fix
                   with spotlessApply on them). (2) The contract's Problem schema has no 'instance' member, but
                   Spring adds it to every problem answer in every service; the Problem checks here allow it
                   (contract gap, a change needs approval). (3) Hashes recorded for the 12 files; test_protection
                   check OK for MAM-65, MAM-67, MAM-61 and MAM-63.
```
