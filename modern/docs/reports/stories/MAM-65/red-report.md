# MAM-65 red report (directory-service, FHIR R4 searches)

No `red-report.md` existed for MAM-65 before this correction (the folder held only `red-blobs.txt`), so this file records the corrections made after the red step. The state
before each correction is kept in git's object store as the blob ids named below (`git cat-file blob <id>`).

## Test correction under Q-097 (HAPI validation, Q-112)

Cause: **MAM-65 AC-7** says "every returned Bundle is checked with the HAPI FHIR validator against FHIR R4; the validator reports no errors for any Bundle or resource". The red
tests could not run HAPI (it was not in the build) and checked the R4 structure with `Fhir4Shape` instead, saying so in their comments. Decision **Q-112** (technical default, in
`modern/docs/analysis/questions.md`) added the HAPI validator as a test-only dependency (`hapi-fhir-structures-r4`, `hapi-fhir-validation`, `hapi-fhir-validation-resources-r4`,
`hapi-fhir-caching-caffeine`, 8.12.1, pinned in `gradle/libs.versions.toml`, ADR-010). The user's standing rule **Q-097 C** applies: an additive correction, no assertion weakened,
removed or relaxed, documented here and re-recorded; the code-reviewer confirms.

**This correction is additive.** Every assertion of every recorded test is unchanged. What changed:

1. **New test class** `FhirR4HapiValidatorTest` (16 tests, all tagged `[MAM-65 AC-7]`): the HAPI check of every Bundle and resource of the three searches.
2. **Comment-only edits** in two recorded files whose Javadoc said "HAPI is not a dependency of directory-service yet, and adding it needs a decision" (now false): `Fhir4Shape.java`
   and `api/FhirR4ValidityTest.java`. No code line, import, annotation, display name or assertion changed. `Fhir4Shape` and the `Fhir4Shape` assertions stay in place.

| File | Previous blob | New blob | Line endings | Change |
|---|---|---|---|---|
| `src/test/java/gov/medicaid/pem/directory/api/FhirR4HapiValidatorTest.java` | (new file) | `21d50b0fa1dda4d7ae96526cb68a86d047328611` | CRLF | new, 16 tests |
| `src/test/java/gov/medicaid/pem/directory/Fhir4Shape.java` | `434fa14cc180dadd627417a699ab743eae41a0a9` | `8baf08dba7f31e3465cf47ecd4247c79b8dde43c` | CRLF | Javadoc only, 5 lines reflowed |
| `src/test/java/gov/medicaid/pem/directory/api/FhirR4ValidityTest.java` | `1f8de2681a886379e308e03e7aab1393d6c7d3dc` | `95b5deaa18aa1c714cf34cdec1f857a527b92d6b` | CRLF | Javadoc only, 3 lines reflowed |

(all paths under `modern/services/directory-service/`; the 10 other files of `MAM-65.tests.sha256` are byte-identical to the red recording.)

`Fhir4Shape.java` (CRLF; diff made with `--ignore-cr-at-eol`, the line endings of the file were kept)
```diff
@@ -12,7 +12,7 @@ import java.util.regex.Pattern;
  * A structural FHIR R4 check of a searchset Bundle and the Practitioner, Organization and
- * PractitionerRole resources in it (MAM-65 AC-7). It is NOT the HAPI FHIR validator: HAPI is not a
- * dependency of directory-service yet, and adding it needs a decision (reported with the Lane 3
- * tests). It checks what the R4 specification fixes without profiles or terminology: the element
- * names of each type, cardinalities, the Bundle and search codes, the id format, absolute URIs, and
- * that no string, array or object is empty. Each violation is one readable line.
+ * PractitionerRole resources in it (MAM-65 AC-7). It is NOT the HAPI FHIR validator: that is run on
+ * the same Bundles by FhirR4HapiValidatorTest (decision Q-112), and this check stays next to it. It
+ * checks what the R4 specification fixes without profiles or terminology: the element names of each
+ * type, cardinalities, the Bundle and search codes, the id format, absolute URIs, and that no
+ * string, array or object is empty. Each violation is one readable line.
  */
```

`api/FhirR4ValidityTest.java` (CRLF)
```diff
@@ -22,5 +22,5 @@ import org.springframework.test.web.servlet.MockMvc;
  * MAM-65 AC-7: every Bundle and resource the live API returns for ACTIVE providers of type IN, GR
- * and FA is valid FHIR R4. The HAPI FHIR validator is not a dependency of directory-service, and
- * adding it needs a decision, so this checks the R4 structure with Fhir4Shape instead (see its
- * description); the fullUrl rule of R4 is its own test.
+ * and FA is valid FHIR R4. This class checks the R4 structure with Fhir4Shape (see its
+ * description); the fullUrl rule of R4 is its own test. The HAPI FHIR validator check of the same
+ * Bundles (decision Q-112) is FhirR4HapiValidatorTest, and both checks stay.
  */
```

### The new tests (`FhirR4HapiValidatorTest`, extends `IntegrationTest`: real application, Testcontainers Postgres and Kafka, MockMvc)

Data: the same synthetic ACTIVE rows as `FhirR4ValidityTest`: individuals `1003000111` (names, licence, taxonomy `207Q00000X`) and `1003000777` (minimal), group `1003000444` (GR),
facility `1003000333` (FA). Each response body is validated as the API returned it (raw JSON string), not a re-serialised copy.

| Test | Count | What it asserts |
|---|---|---|
| `bundleHasNoErrors` | 9 | HAPI reports no ERROR or FATAL for the Bundle of each search: Practitioner (all, `identifier`, `name`), Organization (all, `identifier`, `name`), PractitionerRole (all, `specialty`) and an empty search (`name=NOSUCHNAME`, total 0). Errors are listed in the failure message as `label \| severity \| path \| message`. |
| `everyResourceHasNoErrors` | 3 | HAPI reports no ERROR or FATAL for each single resource (validated on its own) of the Practitioner, Organization and PractitionerRole searches; the search must return at least one resource. |
| `inGrFaAreValidated` | 1 | The validated searches really hold an IN (`1003000111`), a GR and an FA (`1003000333`, `1003000444`) and a positive PractitionerRole total, so the check is not vacuous. |
| `unknownExternalCodeSystemIsNotAnError` | 1 | The NUCC taxonomy code system (cannot be checked offline) produces a message that is WARNING only, never ERROR. |
| `controlMissingFullUrl` | 1 | Control: the same validator reports an ERROR for a real Bundle whose entries had their `fullUrl` removed (the defect fixed earlier), so the check would have caught it. |
| `controlInvalidCode` | 1 | Control: the same validator reports an ERROR for `gender: robot` (a code outside a required FHIR value set), so terminology checking is still on. |

### HAPI configuration (offline, HAPI 8.12.1)

- `FhirContext.forR4()`, `FhirValidator` with one `FhirInstanceValidator`, created once per class in `@BeforeAll` and released in `@AfterAll` (it holds the R4 definitions).
- Support chain, wrapped in a `CachingValidationSupport`: `DefaultProfileValidationSupport` (R4 structure definitions, code systems, value sets from the jars), then
  `InMemoryTerminologyServerValidationSupport` (checks and expands those value sets in memory), then `CommonCodeSystemsTerminologyService` (language tags, mime types, currencies,
  countries, UCUM, USPS). **No remote terminology server (no tx.fhir.org).**
- `FhirInstanceValidator` is left at its defaults, with `setNoTerminologyChecks(false)` stated explicitly. **No setting was needed to make unknown external code systems non-errors:**
  with this chain HAPI itself reports `CodeSystem is unknown and can't be validated: http://nucc.org/provider-taxonomy` as a WARNING (the binding of `PractitionerRole.specialty` is preferred, not
  required), and `Identifier.system` (the NPI system `http://hl7.org/fhir/sid/us-npi`) is a plain URI with no
  code check, so it gives no message at all. **`setNoTerminologyChecks(true)` was deliberately not used**: it would also silence wrong codes in the FHIR code systems; `controlInvalidCode`
  proves they still are errors. The WARNING for NUCC is asserted by `unknownExternalCodeSystemIsNotAnError`, which records the limit that NUCC code values are not verified offline.

### Validator results (tests run 2026-11-19; messages are printed on stdout with the prefix `HAPI-MAM-65-AC-7` in `build/test-results/test/TEST-gov.medicaid.pem.directory.api.FhirR4HapiValidatorTest.xml`)

- **Errors and fatal messages: 0** for the 9 Bundles, the 6 single resources (2 Practitioner, 2 Organization, 2 PractitionerRole) and the extra PractitionerRole Bundle of the NUCC
  test. The only ERRORs that appear are the 4 expected ones of the two control tests.
- **Information messages: 0. Warnings: 48** in the validations of the real responses, by kind:

| Count | Warning (not asserted, not an AC failure) |
|---|---|
| 19 | `dom-6`: a resource should have narrative (best practice) |
| 10 | `Identifier.type`: no code from value set `IdentifierType` (the Medicaid ID identifier has `type.text` only) |
| 9 | SearchSet Bundles should have a `self` link that says what the search was |
| 6 | `Organization.type[0]`: the Coding has a `code` (GR or FA) but no `system` |
| 4 | NUCC `http://nucc.org/provider-taxonomy` unknown code system, cannot be validated offline |

  These four are observations for the java-developer and the user, not defects against AC-7 (no errors). Not changed.

### Runs (all `--no-daemon`, under the Gradle lock, 2026-11-19; free memory 2.3 GB at the start)

- First run of the new class alone (before the NUCC test was added): 15 of 15 pass (34 s). It never failed, so there is no red for these tests: they are additive checks of
  behaviour the earlier defect fix already delivers, and the two controls show they would have failed on the earlier defect (missing `fullUrl`).
- Final `:services:directory-service:check --continue`: **BUILD SUCCESSFUL in 6m 39s**; **407 tests, 0 failures, 0 errors, 0 skipped** (was 391 before; 391 + 16 = 407);
  Spotless check passes; **JaCoCo lines 491 of 497 = 98.8%** (unchanged); **PIT 33 of 34 mutations killed = 97%**, test strength 97%, line coverage of mutated classes 80/81 (99%), 25 tests
  examined, 86 tests run (unchanged score). No out-of-memory in the 448 MB test JVM or in the 256 MB PIT minions; no build setting was changed.
- Spotless was applied to `src/test` only (init script, production code not touched) and the reflowed Javadoc is in the diffs above.

**Hashes:** re-recorded with `test_protection.py record MAM-65` with exactly the 12 files of the previous `MAM-65.tests.sha256` plus `FhirR4HapiValidatorTest.java` (13 files); `check`
prints `OK: 13 test files of MAM-65 unchanged; no unrecorded test files` and `sha256sum -c` is OK for all 13.

## Test correction under Q-097 (code review 2026-11-19, additive)

Cause: the code review of directory-service (verdict MUST-FIX) found that `FhirController.bundle(...)` builds every `fullUrl` from the request
(`ServletUriComponentsBuilder.fromCurrentContextPath()`, `FhirController.java` ~99-121) and that `directory-service.yml` sets `server.forward-headers-strategy: framework`
(line 14). A caller, or any proxy in front, can therefore steer the URLs that the public FHIR API publishes by sending `X-Forwarded-Host`, `X-Forwarded-Proto`, `X-Forwarded-Port`,
`X-Forwarded-Prefix` or `Forwarded`, or by using another `Host`. No recorded test covered this, because the recorded fullUrl tests only look for "has an absolute fullUrl". Rule:
every Bundle entry's `fullUrl` is the configured public base URL, property **`pem.fhir.public-base-url`** (default `http://localhost:8080`), followed by `/fhir/<Type>/<id>`; never the
request's host and never a forwarded header. The user's standing rule **Q-097 C** applies: additive, no assertion weakened, removed or relaxed, documented here and re-recorded; the
code-reviewer confirms.

**Tag: `[MAM-65 AC-7]`** (not AC-2). AC-7 is "resources are valid FHIR R4", and the absolute, well-formed `fullUrl` of every entry is the R4 rule the existing fullUrl tests of this
story already sit under (`FhirR4ValidityTest.fullUrl`, `FhirR4HapiValidatorTest.controlMissingFullUrl`). AC-2 is the content of the Practitioner and has no URL in it.

**This correction is additive.** Every assertion of every recorded test is unchanged. What changed:

1. **Three new test classes, 14 new tests, all `[MAM-65 AC-7]`** (details below): `FhirFullUrlChecks` (abstract helper, no test of its own), `FhirPublicBaseUrlConfiguredTest` (7),
   `FhirPublicBaseUrlDefaultTest` (7). Both concrete classes extend `IntegrationTest` (real application, Testcontainers Postgres and Kafka, MockMvc). The configured class adds
   `@TestPropertySource(properties = "pem.fhir.public-base-url=https://pem.example.test")`, so it runs in a second Spring context; the default class sets nothing and shares the existing one.
2. **Title-only edit** in `MAM-65.feature` (review item SHOULD-FIX 8): the AC-7 scenario title said "the HAPI validator is not a dependency yet", which stopped being true when Q-112 added the
   HAPI validator (`FhirR4HapiValidatorTest`). Tag `@AC-7`, every step and every assertion are unchanged. No other file refers to the title (searched the whole of `modern/`).

| File | Previous blob | New blob | Line endings | Change |
|---|---|---|---|---|
| `src/test/java/gov/medicaid/pem/directory/api/FhirFullUrlChecks.java` | (new file) | `4a04d08a0f6c1329687ece209d50372261eb3c25` | CRLF | new, abstract helper |
| `src/test/java/gov/medicaid/pem/directory/api/FhirPublicBaseUrlConfiguredTest.java` | (new file) | `fdff55e0c113499340a3421dda1f20a11102b631` | CRLF | new, 7 tests |
| `src/test/java/gov/medicaid/pem/directory/api/FhirPublicBaseUrlDefaultTest.java` | (new file) | `295a1c27e6abed72f94187fd405aa8eda3f10087` | CRLF | new, 7 tests |
| `src/test/resources/features/MAM-65.feature` | `32952e88c5e82ac7d2bb6c0840f8fdc68db6ec88` | `84017e8a979e5e5197cac165fd1398bf2e816316` | LF (as before) | scenario title only, 1 line |

(all paths under `modern/services/directory-service/`; the other 12 files of the previous `MAM-65.tests.sha256` are byte-identical to the previous recording.)

`MAM-65.feature` (LF)
```diff
@@ -92,7 +92,7 @@
   @AC-7
-  Scenario: The resources are valid FHIR R4 (structural check; the HAPI validator is not a dependency yet)
+  Scenario: The resources are valid FHIR R4 (structural check here; the HAPI validator checks the same Bundles in FhirR4HapiValidatorTest)
     Given the FHIR directory holds:
```

### The new tests

Data (both classes, `FhirFullUrlChecks.directory()`): the synthetic ACTIVE rows individual `1003000111` (a Practitioner and a PractitionerRole), group `1003000444` and facility `1003000333`
(two Organizations). `assertFullUrls(base, type, request)` performs the search, requires status 200 and at least one entry, and requires for every entry, in order, the fullUrl
`<base>/fhir/<type>/<resource id>` exactly (so a wrong host, port, scheme or prefix all fail).

| Test (class) | Count | What it asserts |
|---|---|---|
| (a) `configuredBase` (Configured) | 3 | With `pem.fhir.public-base-url=https://pem.example.test`, every fullUrl of the Practitioner, Organization and PractitionerRole Bundle is `https://pem.example.test/fhir/<Type>/<id>`. |
| (b) `forwardedHeadersAreIgnored` (Configured) | 3 | Same configured base although the request carries (1) `X-Forwarded-Host: evil.example`, `X-Forwarded-Proto: https`, `X-Forwarded-Port: 9999`; (2) `X-Forwarded-Prefix: /evil-prefix`; (3) the standard `Forwarded: for=192.0.2.60;proto=https;host=evil.example:9999`. Each case checks the Practitioner and the Organization Bundle. |
| `requestHostIsIgnored` (Configured) | 1 | Same configured base for a request that arrives at `http://attacker.example:7070`. |
| (c) `defaultBase` (Default) | 3 | With the property unset, every fullUrl of the three Bundles is `http://localhost:8080/fhir/<Type>/<id>`. |
| `forwardedHeadersAreIgnored` (Default) | 3 | The same three header sets with the property unset still give the default base. |
| `requestHostIsIgnored` (Default) | 1 | The attacker host and port with the property unset still give the default base. |

### Red run (2026-11-19, `--no-daemon`, under the Gradle lock): all 14 fail, for the expected reason (assertion failure on the fullUrl, no compile or setup error)

Run: `:services:directory-service:spotlessApply :services:directory-service:test --tests <the new classes, DirectoryNameSearchLiteralTest, DirectoryExtractJobTest, CucumberSuiteTest>`:
72 tests ran, 25 failed (all new: 14 here, 6 MAM-63, 2 Cucumber MAM-63, 3 MAM-67); the other 47 (every recorded test of those classes and of the Cucumber suite, plus one MAM-63 case
that was replaced afterwards, see the MAM-63 report) passed unchanged.

| Test | Failure today |
|---|---|
| `configuredBase` x3 (Configured) | the fullUrl is `http://localhost/fhir/<Type>/<id>`, the request's own host, instead of `https://pem.example.test/fhir/<Type>/<id>`: the property is not read. |
| `forwardedHeadersAreIgnored` (Configured), X-Forwarded-Host/Proto/Port | `https://evil.example:9999/fhir/Practitioner/1003000111`: the forwarded headers are echoed into the URL (the ForwardedHeaderFilter of `forward-headers-strategy: framework`). |
| same, X-Forwarded-Prefix | `http://localhost/evil-prefix/fhir/Practitioner/1003000111`. |
| same, `Forwarded` | `https://evil.example:9999/fhir/Practitioner/1003000111`. |
| `requestHostIsIgnored` (Configured) | `http://attacker.example:7070/fhir/Practitioner/1003000111`: the request's host is echoed. |
| `defaultBase` x3 (Default) | `http://localhost/fhir/<Type>/<id>` instead of `http://localhost:8080/fhir/<Type>/<id>` (no default base exists). |
| `forwardedHeadersAreIgnored` x3 (Default) | the same three leaks as above, expected `http://localhost:8080/...`. |
| `requestHostIsIgnored` (Default) | `http://attacker.example:7070/...`. |

Not red by design: the title edit (no behaviour). Fixing it is production work only (read the property, stop using the request and the forwarded headers). The configured class starts a
second Spring context in the 448 MB test JVM: watch memory in the first full `check`.

**Hashes:** re-recorded with `test_protection.py record MAM-65` with exactly the 13 files of the previous `MAM-65.tests.sha256` plus the 3 new files (16 files); `check` prints
`OK: 16 test files of MAM-65 unchanged; no unrecorded test files`. The previous versions stay in git's object store under the previous blob ids above.
