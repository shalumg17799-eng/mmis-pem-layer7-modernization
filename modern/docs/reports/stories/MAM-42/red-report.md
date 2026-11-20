# MAM-42 red report: Fixed-width codec with layouts exported from the graph

Run: `./gradlew --no-daemon :libs:fixed-width:test` on 2026-11-19, before any production code of the story existed
(`libs/fixed-width` has only `build.gradle.kts`; there is no `src/main`).
Result: `compileTestJava` fails with **99 errors, every one "cannot find symbol"**, and only for the six classes the tests
specify: `FixedWidthCodec`, `FixedRecord`, `FixedWidthException`, `RecordLayout`, `FieldDef`, `FieldType`. No unrelated
failure: test dependencies (JUnit params, AssertJ, Jackson) resolve; the build gets as far as the missing production
classes. (javac stops reporting at 99 errors; the missing-symbol list is complete for the six types.)

Test hashes: `modern/docs/reports/stories/MAM-42.tests.sha256` (10 files), restore copies in
`modern/docs/reports/stories/MAM-42/red-blobs.txt`. `test_protection.py check MAM-42` prints OK. Spotless was applied
before hashing. Nothing is committed.

## Test count

| Level | Methods | Invocations | Where |
|---|---|---|---|
| Unit (JUnit, plain Java, no Spring) | 64 | 257 | `libs/fixed-width/src/test/java/gov/medicaid/pem/fixedwidth/` |
| Acceptance (Cucumber) | 0 | 0 | library story: no Cucumber (orchestrator instruction) |
| Integration (Testcontainers) | 0 | 0 | the story has no persistence and no events |
| Contract (OpenApiContract) | 0 | 0 | the story has no endpoint and no event |

Eight of the 64 methods are in `LayoutFilesTest` and read the exported JSON and `modern/docs/analysis/data-layouts.md`
directly, so they pin the exporter's output format (section "Layout JSON the tests expect"). Two files are helpers, not
tests: `Samples.java` (synthetic records) and `DataLayoutsDoc.java` (reads `data-layouts.md`).

## What the tests specify (the API the java-developer must provide)

The story had no API, so the tests define it. Package `gov.medicaid.pem.fixedwidth`, all in `libs/fixed-width/src/main`.
Everything is unchecked (`FixedWidthException extends RuntimeException`). Bytes are ISO-8859-1, one byte per character
(EBCDIC is out of scope).

| Type | What the tests use |
|---|---|
| `enum FieldType` | `ALPHANUMERIC`, `NUMERIC`, `DATE_CCYYMMDD`, `DATE_YYDDD` |
| `record FieldDef(String name, int start, int length, FieldType type, Set<String> views)` | `start` is 1-based; `views` is the set of discriminator values for which the field exists, empty = always present |
| `record RecordLayout(String name, int length, String readQuery, String discriminator, List<FieldDef> fields)` | `field(String name)`; `discriminator` is a field name or null |
| `FixedWidthCodec` | `forLayout(String name)` (loads `/layouts/<name>.json` from the classpath), `forLayout(RecordLayout)`, `layout()`, `newRecord()` (alphanumerics spaces, numerics zero), `read(byte[], long recordNumber)`, `write(FixedRecord)` returning exactly `layout.length()` bytes |
| `FixedRecord` | `get(field)` (alphanumeric without trailing spaces; numeric and date as the stored digits), `set(field, String)`, `setNumber(field, long)`, `getNumber(field)`, `setDate(field, LocalDate)`, `getDate(field)` (CCYYMMDD only), `setYyddd(field, LocalDate)`, `getYyddd(field, int century)` (the only `getYyddd` overload) |
| `FixedWidthException` | `layout()`, `recordNumber()` (long), `field()` (null for a whole-record error); the message contains the layout, the record number and the field |

Meaning of the arguments that need a decision:
- `century` in `getYyddd` is the **CC of CCYY**: 20 for 2000-2099, 19 for 1900-1999. `getYyddd("26015")` with 20 gives
  2026-01-15, with 19 gives 1926-01-15. There is no pivot year and no one-argument overload (a reflection test enforces it).
- `read` checks everything it can before returning: length, digits in every numeric/date field, CCYYMMDD validity. YYDDD is
  checked for digits at `read`; its day-of-year is checked in `getYyddd` (366 needs the century to know the year).
- `get` of a field of the other view, and `set` of a field of the other view, throw `FixedWidthException` with `field()` set.
  A type code that is neither IN, GR nor FA (including blank and lower case `in`) still **reads** (service validation raises
  E102), but no view is chosen: view fields throw, the raw `PRV-VARIANT` field stays readable.
- `set` of a value longer than the field and `setNumber` of a number with more digits than the field throw (never truncate).

## Layout JSON the tests expect (`src/main/resources/layouts/<NAME>.json`)

`LayoutFilesTest` reads these files from `src/main/resources/layouts` (relative to the module folder) with Jackson:

- `name` equals the file name; `length` equals 130, 67, 200, 250, 80, 80, 120, 100 for PRVRECRD, SCRNREC, APPINPUT, APPERROR,
  SANCIN, REVALQ, TMSISOUT, DIROUT.
- `readQuery`: the Cypher read query that produced the file; must contain `MATCH` and `RETURN` and no
  `CREATE|MERGE|DELETE|DETACH|SET|REMOVE|DROP` word.
- `fields[]` with `name`, `start`, `length`. The model also needs `type` and `views`, plus a top-level `discriminator`
  (`PRV-TYPE-CODE` for PRVRECRD and APPINPUT, null elsewhere); the tests read those through `FixedWidthCodec.layout()`.
- Field names are the COBOL names of `data-layouts.md` (`PRV-NPI`, `SAN-EFF-DATE`, `WS-T-PERIOD-YYDDD`, ...). Fillers may be
  named anything starting with `FILLER` (the graph calls them `FILLER@L<line>`). Group items (`PRV-INDIVIDUAL-DATA`,
  `PRV-ORG-DATA`) may be exported or left out.
- TMSISOUT is the `PRVTMS01:WS-TMSIS-DETAIL` row layout (the 120-byte record's content). APPINPUT is the PRVRECRD fields plus
  one reserved field (name starting `RESERVED` or `FILLER`) for bytes 131-200 (D-025). APPERROR is one 250-byte field.
- Views: `PRV-LAST-NAME`, `PRV-FIRST-NAME`, `PRV-LICENSE-NO`, `PRV-LICENSE-STATE` and the 13-byte filler at 118 have views `{IN}`;
  `PRV-LEGAL-NAME`, `PRV-DBA-NAME`, `PRV-OWNER-NPI` have `{GR, FA}`; every other field has none. Types: the five date fields
  `PRV-EFFECTIVE-DATE`, `PRV-REVAL-DUE-DATE`, `SCR-VERDICT-DATE`, `SAN-EFF-DATE`, `REV-DUE-DATE` are `DATE_CCYYMMDD`;
  `WS-T-PERIOD-YYDDD` is `DATE_YYDDD` and no other layout has a YYDDD field (D-015).

## AC to test mapping and expected failure reasons

Every test below fails now with `cannot find symbol` (the six classes above are missing). The "Fails against" column gives the
behavioural reason it fails once the classes exist (checked against a signatures-only stub, see "How the tests were validated").

| AC | Test (class.method, invocations) | Fails against a stub because |
|---|---|---|
| AC-1 | `LayoutFilesTest.layoutFileExists` (8) | `layouts/<NAME>.json` does not exist (AssertionError: expecting path to be a regular file) |
| AC-1 | `LayoutFilesTest.layoutNamesRecordAndLength` (8) | file missing: name and length cannot be read |
| AC-1 | `LayoutFilesTest.layoutNamesItsReadQuery` (8), `readQueryOnlyReads` (8) | file missing: no `readQuery` |
| AC-1 | `LayoutFilesTest.everyDocumentedFieldIsExported` (6), `noExportedFieldIsOffThePage` (6) | file missing; positions cannot equal `data-layouts.md` |
| AC-1 | `LayoutFilesTest.flatRecordLengthsMatchTheDocument` (2) | APPINPUT and APPERROR files missing |
| AC-1 | `LayoutFilesTest.fieldsCoverTheRecord` (8) | `FixedWidthCodec.forLayout` not implemented, no layout to inspect |
| AC-2 | `ProviderMasterLayoutTest.masterLayoutIs130Bytes`, `writtenMasterIs130Bytes` (1 each) | no codec, no layout (D-007: 130) |
| AC-2 | `ProviderMasterLayoutTest.commonFieldPositions` (10) | NPI 1-10, Medicaid ID 11-19, type 20-21, taxonomy 22-31, tier 32, status 33, screen flag 34, dates 35-42 and 43-50, variant 51-130 not available |
| AC-2 | `ProviderMasterLayoutTest.writtenValuesLandAtTheirBytes` (12), `ownerNpiSurvivesWriteAndRead` (1) | cannot write or read; owner NPI at 121-130 not produced |
| AC-2 | `ProviderMasterLayoutTest.wrongLengthMasterIsRejected` (3: 121, 129, 131 bytes) | nothing is rejected: no codec |
| AC-2 | `ProviderMasterLayoutTest.intakeLayoutIs200Bytes`, `intakeStartsWithTheMasterLayout`, `intakeTailIsReserved`, `intakeReadsTheMasterFields`, `intakeIgnoresReservedContent`, `intakeWritesReservedAsSpaces`, `intakeWritesMasterThenReserved` (1 each) | no APPINPUT layout: 200 bytes, PRVRECRD in 1-130, reserved 131-200 (D-025) |
| AC-3 | `ProviderVariantTest.typeCodeIsTheDiscriminator` (1) | no discriminator in the layout |
| AC-3 | `ProviderVariantTest.individualViewPositions` (4), `individualViewHasThirteenBytesOfFiller` (1) | IN view: last name (30, byte 51), first name (20, 81), licence number (15, 101), state (2, 116), filler 13 at 118 not defined |
| AC-3 | `ProviderVariantTest.organisationViewPositions` (3), `everyViewCoversTheVariant` (3) | GR/FA view: legal name (40, byte 51), DBA name (30, 91), owner NPI (10, 121) not defined; views must tile 51-130 |
| AC-3 | `ProviderVariantTest.individualValues` (4), `groupValues` (3), `facilityValues` (3) | cannot read the variant by type |
| AC-3 | `ProviderVariantTest.individualIsWrittenByView`, `groupIsWrittenByView` (1 each) | cannot write by view |
| AC-3 | `ProviderVariantTest.individualRecordHasNoOrganisationFields` (1), `organisationRecordHasNoIndividualFields` (2), `otherViewIsNotWritable` (1), `unknownTypeHasNoView` (3), `rawVariantIsAlwaysReadable` (1) | wrong-view access is not an explicit error; unknown type behaviour undefined (interpretation, see below) |
| AC-4 | `FieldTypesTest.alphanumericIsRightPadded` (4) | no right-padding with spaces |
| AC-4 | `FieldTypesTest.numericIsLeftZeroFilled` (5), `numericReadsBack` (3), `writtenRecordIsThePaddedFieldsSideBySide` (1) | no left zero-fill of numeric display fields |
| AC-4 | `FieldTypesTest.alphanumericTooLongIsRejected` (1), `numericTooLongIsRejected` (3) | no explicit error instead of truncation (D-024) |
| AC-4 | `FieldTypesTest.exportedFieldTypes` (9), `yydddOnlyInTmsis` (7) | layouts do not type the date fields (CCYYMMDD, D-004) and YYDDD only in T-MSIS (D-015) |
| AC-4 | `DateFieldsTest.ccyymmddIsWrittenWithFourDigitYear`, `ccyymmddIsRead` (1 each) | 2026-01-15 is not written as 20260115 |
| AC-4 | `DateFieldsTest.yydddIsWrittenAsTwoDigitYearAndDayOfYear` (1), `yydddIsWritten` (10) | 2026-01-15 is not written as 26015; leap years: 2028-12-31 is 28366, 2000-02-29 is 00060, 1999-12-31 is 99365, 2000-01-01 is 00001 |
| AC-4 | `DateFieldsTest.yydddIsReadWithTheCallersCentury` (10), `centuryDecidesTheYear` (1), `noYydddReaderWithoutACentury` (1), `yydddFieldIsNotACcyymmddDate` (1) | no century parameter, no pivot-free read |
| AC-4 | `DateFieldsTest.impossibleYydddIsRejected` (6) | day 366 in a non-leap year, day 000, 367, 999 are not rejected (D-024) |
| AC-5 | `RoundTripTest.parseThenWriteGivesIdenticalBytes` (15: PRVRECRD IN/GR/FA, APPINPUT IN/GR/FA, SCRNREC x2, SANCIN x3, REVALQ, TMSISOUT, DIROUT, APPERROR) | no parse, no write |
| AC-5 | `RoundTripTest.tmsisConstantSurvives` (1), `individualAndGroupKeepTheirVariants` (1) | filler constant `PRV01` and the IN/GR variants must survive the round trip |
| AC-6 | `BadInputTest.wrongLengthNamesLayoutAndRecord` (15), `wrongLengthStatesBothLengths` (15) | wrong lengths (0, 121, 129, 131, 200 for PRVRECRD, and one short or long for every other layout) are not rejected with layout, record 4321 and both lengths |
| AC-6 | `BadInputTest.nonNumericNamesLayoutRecordAndField` (7) | non-numeric or blank characters in a date field of 7 layouts are not rejected with layout, record and field |
| AC-6 | `BadInputTest.impossibleDateNamesLayoutRecordAndField` (9), `impossibleDateNamesTheSecondDateField` (1) | 20260230, 20260229, 20261301, 20260431, 20260100, 20260132, 20260015, 19000229, 21000229 are not rejected |
| AC-6 | `BadInputTest.validDateEdgesAreAccepted` (5), `errorCarriesTheNumberOfTheBadRecord` (1) | leap days 20280229 and 20000229 and month ends must be accepted; the error carries record number 2, not 1 |
| AC-7 | `LibraryGateTest.everyLayoutHasARoundTripSample` (1) | `layouts/` does not exist; once it does, a layout without a round-trip sample fails the build |
| AC-7 | `./gradlew --no-daemon :libs:fixed-width:check` | the build gates (JaCoCo 85% lines, Spotless) are the shared `pem.java-library` conventions and are verified by the green run, not by a test |

## How the tests were validated (throwaway implementations in the scratchpad, outside the repo)

A test that cannot go green, or that passes for the wrong reason, would stall the green step because the developer may not edit
tests. So the tests were run against three throwaway builds kept only in the session scratchpad (none of it is in the repo):

1. **Reference implementation** (about 300 lines, plus layout JSON generated from `data-layouts.md`): **257 of 257 pass**.
   This run found and fixed one test bug before hashing (`00060` with century 19 is 1900-03-01, a valid date; it is now a
   valid-century case and `00366` with 19 remains the invalid one).
2. **Signatures-only stub** (classes exist, every method throws, no layout files): **0 of 257 pass**, so no test passes
   vacuously.
3. **Eight deliberate defects** in the reference, each caught: pivot-year YYDDD (3 tests fail), silent truncation of a text
   (1), no date validation on read (11), view fields readable for any type (7), record number always 1 (15), numeric overflow
   wrapped (3), longer records accepted (5), fillers blanked on write (10).

The only formatting step was `./gradlew --no-daemon :libs:fixed-width:spotlessApply`; the formatted files were re-run against
the reference (257 of 257) before hashing.

## Interpretations made by the test-writer (confirm or change)

The ACs do not say these; they follow D-024 ("explicit errors instead of silent handling") and the story's out-of-scope list.
None is a business rule.

1. **Unknown provider type code reads, but chooses no view.** Business validation (E102) is the enrolment service's, and it
   needs to read the bad record first. Reading `PRV-LAST-NAME` of such a record throws; the raw `PRV-VARIANT` is readable.
2. **Reading or writing a field of the other view is an error**, not a silent reinterpretation of the same 80 bytes.
3. **Too-long values on write are errors**, not truncated (AC-6 says "nothing is silently truncated" for reading; the write side
   follows the same decision).
4. **Question for the user (not asserted by any test): the all-zero date `00000000`.** D-029 says legacy PRVREV01 queues "an
   active provider with a zero due date", so zero dates can exist in legacy data. AC-6 as written rejects an impossible date,
   so the tests reject nothing special about `00000000` either way. Options: A) reject it like any impossible date (what AC-6
   says); B) accept an all-zero date as "no date" (null). **Recommended: A now**, and revisit when the provider master seed
   and the Phase 9 parity data are generated, because a legacy extract that contains zero dates would otherwise fail to load.
5. **Record separators.** The codec reads one record from a `byte[]` with a caller-supplied record number; reading a whole file
   (fixed-length records with or without line ends) stays in each service's adapter (the story's "out of scope").

## Not verified

- The Jira description was not compared with `modern/specs/MAM-42.md` by this step (the guide's section 2 spec check is the
  orchestrator's).
- The layout JSON files do not exist yet and nothing in the repo can export them: `modern/tools/data/` is empty and the graph
  is written only by kag-curator loaders. The green step needs a graph read (the `Record` and `Field` nodes of PRVRECRD,
  SCRNREC, `PRVENR01:APP-INPUT-RECORD`, `PRVENR01:APP-ERROR-RECORD`, SANCIN, REVALQ, `WS-TMSIS-DETAIL`, DIROUT) or an export
  script that reads the parsed copybooks the loaders use; the tests only require that the files exist with the content above.
  If Neo4j is not reachable in the green step, that is a blocker to report, not a reason to type the layouts by hand.
- JaCoCo 85% and Spotless on the production code are checked only in the green step.
- No Cucumber, integration or contract test exists for this story, by design (library; no endpoint, event or database).
