# MAM-43 red report: the 130-byte PRVRECRD export of the provider master

The export (build-phase batch A5) existed before its tests. The tests of this report were written after the code, so they were green at once, **except the tests of the
user's decision Q-124 A (2026-11-20 19:32), which were written first and are red for the expected reason** (section 1). Nothing was recorded before 2026-11-20 (the earlier
test-writer of this lane was stopped at 18:35 before it recorded), so `MAM-43.tests.sha256` is new.

Test hashes: `modern/docs/reports/stories/MAM-43.tests.sha256` (10 files), restore copies in `red-blobs.txt`.

## 1. The change of Q-124 A, tests first

User decision Q-124 A: a provider without a revalidation due date is exported with bytes 43-50 = `99991231` (spec `MAM-43.md` specVersion 2, AC-6; Jira changed; v1 said
`00000000`). Only PRV-REVAL-DUE-DATE changes; PRV-EFFECTIVE-DATE is unchanged.

Change of existing tests (documented as the lead asked; no assertion weakened, the expected value was replaced by the value of the decision):
- `PrvrecrdFileTest.unsetValuesUseTheLegacyPadding`: the display name "... an unset due date is **00000000** ..." became "... is **99991231** ..."; the assertion
  `bytes(written, 43, 50)` expected `"00000000"` and now expects `"99991231"`. (The file had not been recorded yet; this report is the record of the change.)
- `features/MAM-43.feature`, scenario "Unset values use the legacy padding": `And the line of NPI "1003000222" has "00000000" in bytes 43 to 50` became `"99991231"`.

New tests for the decision (all red now):
- `PrvrecrdFileTest`: `onlyTheDueDateIsNineNines` (an unset effective date stays `00000000`, the due date is `99991231`), `dueDateIsDecidedPerRecord` (set and unset side by
  side), `organisationWithoutDueDate` (GR/FA keep the owner NPI in 121-130), `recordWithoutDueDateIsReadBackByTheCodec` (**the line is read back by the PRVRECRD codec
  without an error**, due date 9999-12-31).
- `LegacyMasterExportApiTest`: `unsetColumnsUseTheLegacyPadding` (a database row with null tier and null due date), `mixedDueDatesAreReadBackByTheCodec` (every line of a
  mixed export is read by the codec).
- `features/MAM-43.feature`: a second AC-6 scenario "A provider without a due date is exported with 99991231 and the codec reads the line back" (new step
  "... is read back by the PRVRECRD codec with the due date ...").

Red run (`check --continue`, first 20:06-20:13, unchanged at 20:48 and 21:00): 9 failures, **all for the expected reason**: `PrvrecrdFile` leaves the zero default of the codec
for a null due date (`PrvrecrdFile.java:42`).

| Test | Failure |
|---|---|
| `PrvrecrdFileTest` unsetValuesUseTheLegacyPadding | expected `99991231` but was `00000000` (bytes 43-50) |
| `PrvrecrdFileTest` onlyTheDueDateIsNineNines | expected `99991231` but was `00000000` |
| `PrvrecrdFileTest` dueDateIsDecidedPerRecord | expected `99991231` but was `00000000` (second record) |
| `PrvrecrdFileTest` organisationWithoutDueDate | expected `99991231` but was `00000000` |
| `PrvrecrdFileTest` recordWithoutDueDateIsReadBackByTheCodec | `FixedWidthException: PRVRECRD record 1 field PRV-REVAL-DUE-DATE: '00000000' is not a possible date` |
| `LegacyMasterExportApiTest` unsetColumnsUseTheLegacyPadding | expected `99991231` but was `00000000` |
| `LegacyMasterExportApiTest` mixedDueDatesAreReadBackByTheCodec | `FixedWidthException ... record 2 ... '00000000' is not a possible date` |
| Cucumber "Unset values use the legacy padding" | expected `99991231` but was `00000000` |
| Cucumber "A provider without a due date is exported with 99991231 and the codec reads the line back" | expected `99991231` but was `00000000` |

Green step (java-developer, not part of this report): in `PrvrecrdFile` write `LocalDate.of(9999, 12, 31)` into PRV-REVAL-DUE-DATE when the due date is null (and only there)
and correct the class Javadoc ("an unset date is zeros"). The codec needs no change: it reads 99991231 as a real date.

## 2. Tests per AC (after the change)

| AC | Title | JUnit tests | Cucumber scenarios | Failing now |
|---|---|---|---|---|
| AC-1 | Every record is exactly 130 bytes | 6 | 1 | 0 |
| AC-2 | Records are in NPI order with no header or trailer | 3 | 1 | 0 |
| AC-3 | An individual record has the PRVRECRD field positions | 10 | 1 | 0 |
| AC-4 | A group record keeps the owner NPI in bytes 121-130 | 6 | 2 | 0 |
| AC-5 | A suspended provider is exported with status S | 8 | 1 | 0 |
| AC-6 | Unset values use the legacy padding | 7 | 2 | 9 |
| AC-7 | The export decodes to the same values as the API | 2 | 1 | 0 |
| AC-8 | Only ADMIN can export | 6 | 1 | 0 |

Tests by class: `ExportProviderMasterTest` 2, `LegacyMasterExportApiTest` 16, `ProviderReadFailureTest` 1, `PrvrecrdFileTest` 29.

- Byte level: `PrvrecrdFileTest` (each position of the IN, GR and FA view, every status, flag and tier code, dates with 4-digit years, the cut of long names, one LF between
  records, no header or trailer), `LegacyMasterExportApiTest` (real database: CHAR padding, ascending NPI order of 30 scrambled rows, plain ASCII, roles 403 and 401 as
  problem+json, the codec read-back).
- Acceptance: `features/MAM-43.feature` (10 scenarios, one outline for the 6 callers of AC-8).
- Unit: `ExportProviderMasterTest` (store order is kept, empty master).
- `ProviderReadFailureTest` [MAM-43 AC-1]: a database failure is a 503 problem, never a short or empty file.

## 3. Files

| File (under `modern/services/provider-service/src/test/`) | Blob id |
|---|---|
| `java/gov/medicaid/pem/provider/LogCapture.java` | `59bb423f6e4ba8c10ea96584fe7d45e2f7700640` |
| `java/gov/medicaid/pem/provider/ProviderRows.java` | `667a9c1ba95ba3f69bbda0f96b9a980fdf0f6a70` |
| `java/gov/medicaid/pem/provider/acceptance/HttpAnswerSteps.java` | `38fcb570a3260aa0a5d6bdfdfe91fb26142ae0c4` |
| `java/gov/medicaid/pem/provider/acceptance/ProviderFixtureSteps.java` | `5d84f3e26010b903302854d8b5dd3117b4b61841` |
| `java/gov/medicaid/pem/provider/acceptance/ProviderMasterExportSteps.java` | `cb67437055ddb6029d5de5bfbc75f810c890aac0` |
| `java/gov/medicaid/pem/provider/adapter/legacy/PrvrecrdFileTest.java` | `1344a1791395d5515f97eb5b38aac700427e93c7` |
| `java/gov/medicaid/pem/provider/api/LegacyMasterExportApiTest.java` | `c1948ca955f881b47da71deb80c6a33e3cbf44b7` |
| `java/gov/medicaid/pem/provider/api/ProviderReadFailureTest.java` | `832401e54f78bd1a34f749c554f2270f8d1d9fb3` |
| `java/gov/medicaid/pem/provider/application/ExportProviderMasterTest.java` | `a9fe884dab86fc13a19e689731e472c88748de36` |
| `resources/features/MAM-43.feature` | `25145dc7a48edecdeafa7fd71ffeaef9eff44532` |

Runs (all on 2026-11-20, from `modern/`, one Gradle build at a time through the shared lock, `--no-daemon`):
- `./gradlew -I <init script: Spotless on src/test of provider-service only> :services:provider-service:spotlessApply` (formats the test sources only).
- `./gradlew :services:provider-service:check --continue` (tests, Spotless on everything, ArchUnit, JaCoCo, PIT). Last run 21:00-21:09: **417 tests, 9 failures, 0 skipped**;
  the 9 failures are the Q-124 tests (red by design until `PrvrecrdFile` is changed, see MAM-43); PIT **130 of 130 mutations killed (100 %)** on `domain` and `application`;
  Spotless and ArchUnit passed.
- `./gradlew :services:provider-service:jacocoTestReport -x test` (the report of the same run; with a failing test `check` does not reach `jacocoTestCoverageVerification`):
  **line coverage 98.34 %** (768 of 781 lines; 98.84 % without `*Application`, as the gate counts), branch 88.31 %. Gate 85 %.
