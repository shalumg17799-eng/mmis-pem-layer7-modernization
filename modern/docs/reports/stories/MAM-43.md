```
Phase / item:      MAM-43 Export the provider master in the legacy 130-byte PRVRECRD layout
Outcome:           BUILT (build-phase: tests in Part D), batch A5 (not a batch job: no Spring Batch)
What was done:     - Spec check: modern/specs/MAM-43.md (8 ACs); Jira not re-read in this batch
                   - GET /api/v1/legacy/provider-master (ADMIN), text/plain: every provider in ascending NPI order, one
                     130-byte PRVRECRD record per line (lines joined by LF, no header or trailer)
                   - adapter/legacy/PrvrecrdFile through the MAM-42 codec (layout PRVRECRD): the type code selects the
                     REDEFINES view (IN: last name, first name, licence, state, filler; GR/FA: legal name 40, DBA name
                     30, owner NPI in 121-130); alphanumerics right-padded and cut on the right when longer (only in this
                     legacy writer); dates CCYYMMDD; an unset date stays 00000000 and an unset risk tier a space
                   - application/ExportProviderMaster with its ports AllProviders (JpaProviderStore.allByNpi) and
                     MasterFile; api/LegacyFileController
                   - AC status: AC-1 built, checked live (16 seeded providers -> 16 lines of exactly 130 characters);
                     AC-2 built, checked live (ascending NPI, no header or trailer); AC-3 built, checked live on the IN
                     providers (every field position equals the API value); AC-4 built, checked live on the GR/FA
                     providers (legal name 51-90, DBA 91-120, owner NPI 121-130); the cut of a legal name over 40
                     characters is coded, not seen live (no such seeded name); AC-5 built, checked live (FOXTROT and
                     THERAPY GROUP byte 33 'S'); AC-6 built, checked live (PENDING DELTA/HHA: risk tier space, due date
                     00000000, flag U); AC-7 checked live (every line decoded field by field equals GET
                     /api/v1/providers for that NPI: 0 differences); AC-8 built, checked live (ADMIN 200; ANALYST,
                     SUPERVISOR, PROVIDER, SYSTEM 403; no token 401)
Files created/changed: modern/services/provider-service/src/main/java/gov/medicaid/pem/provider/
                     application/ExportProviderMaster (new), adapter/legacy/PrvrecrdFile (new),
                     api/LegacyFileController (new), infrastructure/JpaProviderStore (allByNpi)
Checks run:        compile; ./gradlew --no-daemon :services:provider-service:test (see the batch line); bootJar; pem-apps
                   healthy; live checks above
Not verified:      automated tests (Part D); the 40-character cut of a long legal name live
Questions for you: none
```

## Lane 3 tests (2026-11-20)

Outcome line unchanged. Tests for every AC of MAM-43 (spec version 2, Q-124 A).

- Test files (10, recorded in `MAM-43.tests.sha256`): `PrvrecrdFileTest`, `LegacyMasterExportApiTest`, `ExportProviderMasterTest`, `features/MAM-43.feature`, the step class `ProviderMasterExportSteps`,
  the shared support `HttpAnswerSteps`, `ProviderFixtureSteps`, `ProviderRows`, `LogCapture`, and `ProviderReadFailureTest` (the MAM-43 AC-1 test of a database failure).
- Tests per AC (JUnit + Cucumber scenarios): AC-1 6+1, AC-2 3+1, AC-3 10+1, AC-4 6+2, AC-5 8+1, AC-6 7+2, AC-7 2+1, AC-8 6+1.
- Written after the code and green at once, except the tests of **Q-124 A** (AC-6: 99991231 for no due date), which are **red for the expected reason**: 9 failures; `PrvrecrdFile.java:42` leaves the zero default of the codec for a null due date.
  Details and the change of the earlier AC-6 test: `MAM-43/red-report.md`.
- Defect (open until the java-developer changes the code): `modern/services/provider-service/src/main/java/gov/medicaid/pem/provider/adapter/legacy/PrvrecrdFile.java:42` - AC-6 - expected `99991231` in bytes 43-50 for a provider with no due date,
  actual `00000000` (and the codec rejects that line). The class Javadoc (lines 18-19, "an unset date is zeros") needs the same correction.
Runs (all on 2026-11-20, from `modern/`, one Gradle build at a time through the shared lock, `--no-daemon`):
- `./gradlew -I <init script: Spotless on src/test of provider-service only> :services:provider-service:spotlessApply` (formats the test sources only).
- `./gradlew :services:provider-service:check --continue` (tests, Spotless on everything, ArchUnit, JaCoCo, PIT). Last run 21:00-21:09: **417 tests, 9 failures, 0 skipped**;
  the 9 failures are the Q-124 tests (red by design until `PrvrecrdFile` is changed, see MAM-43); PIT **130 of 130 mutations killed (100 %)** on `domain` and `application`;
  Spotless and ArchUnit passed.
- `./gradlew :services:provider-service:jacocoTestReport -x test` (the report of the same run; with a failing test `check` does not reach `jacocoTestCoverageVerification`):
  **line coverage 98.34 %** (768 of 781 lines; 98.84 % without `*Application`, as the gate counts), branch 88.31 %. Gate 85 %.
- Not verified: the 40-character cut of a long legal name against the running stack (checked at byte level in `PrvrecrdFileTest` and the feature); the effect of Q-124 on the parity report (the lead re-runs it).
