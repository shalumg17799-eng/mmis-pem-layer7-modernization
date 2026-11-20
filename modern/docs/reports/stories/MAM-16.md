```
Phase / item:      MAM-16 (local L-S001) Gradle multi-project build with quality gates (JaCoCo, PIT, ArchUnit, Spotless)
Outcome:           PARTIAL. AC-1, AC-3, AC-4, AC-5, AC-6, AC-7, AC-8 have evidence. AC-2 is open for its package part:
                   enrollment-service and sanctions-service have no gov.medicaid.pem.<svc>.adapter.legacy package (see AC-2)
What was done:     - Lead check 2026-11-20 12:55 found AC-1..AC-6 in place by configuration and AC-7, AC-8 missing; this change adds both
                   - AC-7: pem.java-conventions registers a TestListener (pem.build.SkippedTestGuard) on every module's test task. It
                     collects every test whose result is SKIPPED (a @Disabled method or class, a failed assumption, an unmet
                     enabled/disabled condition; Cucumber scenarios arrive as ordinary results of their suite class) and a doLast fails
                     the task, naming "<class> > <test>" for each. When tests also fail, Gradle's own failure output is unchanged and
                     the listener additionally logs the skipped tests at error level. A run without skipped tests prints nothing new
                   - AC-8: root task versionCheck (pem.build.VersionCheck, a small TOML reader for the catalog tables). It reads
                     gradle/libs.versions.toml, or the file of -PpemVersionCatalog=<path> (demonstration only), and fails, naming alias,
                     line and value, on "+", the word "latest", a range ("[" "]" "(" ")" or a comma), an empty version, a version.ref
                     that is not in [versions] and a plugin without a version. A library with no version at all is BOM-managed and
                     allowed (none exists today). It also scans the Kotlin DSL build files (root, settings, buildSrc, every module: 21
                     files) for the same dynamic forms written inline (group:name:version strings, version = "..", id() version ".."),
                     naming file:line. Wired: every module's check dependsOn(":versionCheck") (pem.java-conventions.gradle.kts:91) and
                     checkAll dependsOn(versionCheck) (build.gradle.kts:25); one run executes it once
                   - Compared for AC-8: catalog spring-boot 3.5.16 and spring-cloud 2025.0.3 with the ADR-010 rows "Spring Boot | 3.5.16
                     (latest 3.x GA on 2026-11-18)" and "Spring Cloud | 2025.0.3 (train for Boot 3.5)": equal. Every one of the 13
                     [versions] values appears in ADR-010 (checked by the demonstration script). Against Maven Central metadata
                     (2026-11-20): 3.5.16 is the newest spring-boot-dependencies 3.x release and 2025.0.3 the newest 2025.0.x train
                   - Catalog as written today: 13 versions, 14 libraries (all with version.ref, so none is BOM-managed without a
                     version: the starters that the Spring Boot BOM manages are written without version in the build files, not in the
                     catalog), no [plugins] table (plugins come in as buildSrc dependencies, which are libraries). No inline dynamic
                     version exists in the build files
                   - Repeatable demonstration tools/checks/demo-build-gates.sh (below); developer-guide.md section 4 mentions both gates

Acceptance criteria (one line of evidence each)
  [x] AC-1  javap -v on one compiled class in four modules: libs/fixed-width FieldDef.class, libs/service-kit ServiceClients.class,
            services/risk-service RiskController$RiskAssessmentRequest.class, services/sanctions-service
            SanctionController$SanctionPageResponse.class: "major version: 65" each. Toolchain: pem.java-conventions.gradle.kts:15
            (languageVersion = v("java") = 21). The four-module gate run below built them with BUILD SUCCESSFUL. A full
            ./gradlew build of all modules was not run (see Not verified)
  [ ] AC-2  ./gradlew --no-daemon -q projects lists :services:api-gateway and the nine services (provider, enrollment, screening, risk,
            revalidation, sanctions, notification, directory, tmsis), :libs:fixed-width, :libs:service-kit, :libs:test-support and
            :infra:local-runtime. Packages api, application, domain, infrastructure exist in all nine services; adapter.legacy exists
            in provider, screening, revalidation, directory, tmsis and is MISSING in enrollment-service and sanctions-service, which
            the AC names as legacy-file services. Both services do have the intake code (enrollment: application/IntakeLoad,
            IntakeLauncher; sanctions: application/SanctionIntake, infrastructure/SanctionIntakeJobConfig), only not in
            adapter.legacy (ADR-005 puts it there). Not changed here: production code belongs to the java-developer and an empty
            package would only hide the question (see Questions)
  [x] AC-3  pem.java-conventions.gradle.kts:73-80 jacocoTestCoverageVerification: counter LINE, value COVEREDRATIO, minimum 0.85;
            :89-90 check dependsOn it. Gate run: every verification task ran and passed (table below); not demonstrated at 84%
  [x] AC-4  pem.spring-service.gradle.kts:58 targetClasses = domain.* and application.* of the service package, :63
            mutationThreshold = 70, :69 check dependsOn pitest (classes outside those packages are not mutated). Gate run: PIT ran
            for risk, sanctions and directory (table below); not demonstrated at 69%
  [x] AC-5  libs/test-support PemArchitecture.check (PemArchitecture.java:18-51): domain depends on no Spring, jakarta.persistence,
            Hibernate, api, application, infrastructure or adapter class; api never touches infrastructure; every service calls it
            from services/<svc>/src/test/.../PlatformChecksTest (8 of the 9 services, see Not verified). The three PlatformChecksTest
            of risk, sanctions and directory passed in the gate run
  [x] AC-6  pem.java-conventions.gradle.kts:30-36 spotless { java { googleJavaFormat(), removeUnusedImports(), trimTrailingWhitespace(),
            endWithNewline() } }; the Spotless plugin makes spotlessCheck part of check: the gate log shows :spotlessJavaCheck and
            :spotlessCheck before :check in all four modules. Not demonstrated with an unformatted file
  [x] AC-7  demo-build-gates.sh steps AC-7: a temporary @Disabled class, a @Disabled method and a failed assumption make
            :libs:fixed-width:test fail with "3 test(s) skipped in :libs:fixed-width:test ..." and one line per test
            (class > method); after the files are removed the same task passes (257 tests, 0 failures, 0 skipped). Final gate
            run: the guard fired in none of the four modules (0 skipped in every result file; no @Disabled, assumption or
            conditional annotation exists in any test source)
  [x] AC-8  demo-build-gates.sh steps AC-8: a catalog copy with springdoc = "2.+" fails versionCheck naming
            "[versions] springdoc = "2.+" (line 7): contains '+'"; a second copy with "latest.release", a range, an empty version
            and a missing version.ref fails naming all four aliases and values; the real catalog passes ("versionCheck: 13 versions,
            14 libraries, 0 plugins ... and 21 build files: every version is exact"); ADR-010 records Spring Boot 3.5.16, Spring
            Cloud 2025.0.3 and every other version. In the gate run :versionCheck ran once and passed

Demonstration (tools/checks/demo-build-gates.sh, run from modern/, 2026-11-20 on the final build logic; 5 Gradle runs, 0 steps failed)
  PASS AC-8 a catalog with springdoc = "2.+" makes versionCheck fail (exit 1) and the message names the alias and the value
        | <copy>.toml: [versions] springdoc = "2.+" (line 7): contains '+' (a dynamic version)
  PASS AC-8 latest, a range, an empty version and a missing version.ref are each named (exit 1)
        | [versions] wiremock = "[3.0,4.0)" (line 9): is a version range
        | [versions] archunit = "latest.release" (line 11): contains 'latest' (a dynamic version)
        | [versions] pitest-junit5 = "" (line 13): is empty
        | [libraries] bad-ref version.ref "no-such-version" (line 35): does not exist in [versions]
  PASS AC-8 versionCheck on the real catalog succeeds (exit 0)
  PASS AC-8 ADR-010 records Spring Boot 3.5.16, Spring Cloud 2025.0.3 and every other version of the catalog
  PASS AC-7 with a @Disabled class, a @Disabled method and a failed assumption :libs:fixed-width:test fails (exit 1) and names each test
        | 3 test(s) skipped in :libs:fixed-width:test. A skipped or @Disabled test cannot reach green (tdd-workflow 4); run it or delete it:
        |   - gov.medicaid.pem.fixedwidth.TempSkippedDemoRuntimeTest > disabledByAnnotation()
        |   - gov.medicaid.pem.fixedwidth.TempSkippedDemoRuntimeTest > skippedAtRunTime()
        |   - gov.medicaid.pem.fixedwidth.TempSkippedDemoTest > insideDisabledClass()
  PASS AC-7 after the temporary classes are removed :libs:fixed-width:test passes again (exit 0; 257 tests, 0 failures, 0 skipped)
  PASS no file left behind: test_protection.py check MAM-42 -> OK: 10 test files of MAM-42 unchanged; no unrecorded test files

Final gate run (one invocation, 2026-11-20 16:02-16:15, waited 0 s for memory, no stale lock):
  timeout 4200 ./gradlew --no-daemon :libs:service-kit:check :services:risk-service:check :services:sanctions-service:check
  :services:directory-service:check --continue  ->  BUILD SUCCESSFUL in 12m 16s; every test task ran (none UP-TO-DATE)
  module                       tests  failures  skipped  JaCoCo lines (gate view)   PIT
  libs/service-kit               110      0        0     338/368 = 91.85%           no PIT in libraries
  services/risk-service          351      0        0     186/186 = 100.00%          41/41 = 100.00%
  services/sanctions-service     326      0        0     425/430 = 98.84%           75/87 = 86.21%
  services/directory-service     433      0        0     501/504 = 99.40%           35/36 = 97.22%
  Gate view = the classes the 85% rule looks at (without *Application and generated); the whole report is 97.91%, 97.93%
  and 98.62% for the three services. The skipped-test guard did not fire anywhere (no "test(s) skipped" message in the log)

Files created:     modern/buildSrc/src/main/kotlin/pem/build/{SkippedTestGuard.kt, VersionCheck.kt, Toml.kt}
                   modern/tools/checks/demo-build-gates.sh
                   modern/docs/reports/stories/MAM-16.md
Files changed:     modern/buildSrc/src/main/kotlin/pem.java-conventions.gradle.kts (guard, check dependsOn :versionCheck)
                   modern/build.gradle.kts (versionCheck task, checkAll)
                   modern/docs/runbooks/developer-guide.md (two sentences in section 4)
                   Untouched: every test file (MAM-16 hashes OK: 2 files unchanged; MAM-42 OK), modern/.github, modern/gradle/libs.versions.toml
Checks run:        ./gradlew --no-daemon -q help (after the buildSrc change) -> configures; first attempt failed on a Kotlin comment
                   that closed early ("@Enabled*/" inside a KDoc), fixed, second attempt OK
                   ./gradlew --no-daemon -q projects -> lists api-gateway, nine services, three libs, infra:local-runtime
                   tools/checks/demo-build-gates.sh -> run twice (the second after a message-path polish); 0 failed both times
                   the final gate run above; test_protection.py check MAM-16 -> OK (2 files); check MAM-42 -> OK (10 files)
                   javap -v major version on 4 classes -> 65; curl of Maven Central metadata for Spring Boot and Spring Cloud
Not verified:      - AC-1 literally: ./gradlew build of every module and the wrapper generation with the Docker image on a clean
                     checkout (only four modules were built, in the gate run; the wrapper 8.14.7 already existed)
                   - AC-3 at 84%, AC-4 at 69%, AC-5 with a violating class and AC-6 with an unformatted file as failing runs: they are
                     proved by the configuration lines and the passing gate results only, as AC-3 allows
                   - AC-7 with Cucumber: a skipped scenario goes through the same listener, but only JUnit forms (@Disabled class,
                     @Disabled method, failed assumption) were run. The extra error log when tests fail and tests are skipped in
                     the same run was not exercised
                   - AC-5 completeness: tmsis-service has no src/test yet (so its coverage and PIT gates pass with nothing to check)
                     and api-gateway has no PlatformChecksTest; both are lane-3 / design items, not changed here
                   - The other modules' check (provider, enrollment, screening, revalidation, notification, tmsis, api-gateway,
                     local-runtime) was not run with the new guard; their stored results hold no skipped test and no test source uses
                     @Disabled or an assumption, but the guard has not run on them
                   - The versionCheck inline scan reads Kotlin DSL files only (.kts), strings of the form group:name:version and
                     version assignments; a version built from a variable is covered through the catalog check
                   - The ci.yml was not run (not touched): it calls build and checkAll, which now include both gates
Questions for you: 1. enrollment-service and sanctions-service keep their legacy-file intake in application and infrastructure, not in
                      adapter.legacy as ADR-005 and AC-2 say. Options: (a) java-developer moves or adds the reader/writer classes into
                      adapter.legacy in lane 3 (recommended: it matches ADR-005 and AC-2; ArchUnit already allows it); (b) change the
                      wording of AC-2 for these two services; (c) add empty packages (not recommended: it only hides the gap)
                   2. tmsis-service has no tests yet; until lane 3 writes them its JaCoCo and PIT gates are vacuous. Option: keep MAM-16
                      Done and let the tmsis story's check prove the gates (recommended)
Next step:         lead decides question 1; then MAM-16 can go In Review (code-reviewer on the buildSrc change: Toml.kt,
                   VersionCheck.kt, SkippedTestGuard.kt, the two edited build files and the demonstration script)
```
