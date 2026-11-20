```
Phase / item:      MAM-44 Deterministic synthetic datasets covering at least 95% of the verified rules
Outcome:           BUILT (batch A6, build phase: tests in Part D). AC-1..AC-8 each checked once by running the tools below
What was done:     - Spec check: modern/specs/MAM-44.md read (8 ACs, an enabler with no legacy rules of its own); Jira not re-read
                   - Generator tools/data/generate.py (seed 20260115 = the frozen business date). It draws first exactly what
                     testdata/generate_seed.py draws (16 NPIs, then 16 Medicaid IDs from random.Random(20260115)), so the 16 seeded
                     providers keep their NPIs and Medicaid IDs; generate_seed.py and every file under seed/ are untouched. New values
                     follow in a fixed order (NPIs in the block 10061xxxxx, outside the seed block 10059xxxxx and the reserved ranges)
                   - Layout positions are never typed: pemdata.Layout reads the JSON layouts exported from the graph
                     (libs/fixed-width/src/main/resources/layouts/*.json) and builds / parses a record by field name and REDEFINES view
                   - Datasets (testdata/): fixed-width/provider-master-seed.dat (16 x 130, the same providers as the SQL seed, checked
                     row by row against seed/provider_db/01-providers.sql), fixed-width/appinput-baseline.dat (8 x 200), fixed-width/
                     sancin-baseline.dat (6 x 80), scenarios/appinput-scenarios.dat (8 x 200) with expected-outcomes.csv,
                     provider-type-mapping.csv (QF-2) and business-dates.csv, coverage.csv + coverage-gaps.csv, manifest.csv (layout,
                     records, size, sha256 of every generated file), .gitattributes (*.dat -text)
                   - Beyond the five listed outputs, two small additions that the 95% needed and the SQL seed forbade putting elsewhere:
                     fixed-width/provider-master-edge.dat (5 x 130: states the 16 seeded providers lack, kept OUT of the seed so MAM-50 and
                     MAM-51 AC-1 compare against exactly the 16 seeded NPIs) and negative/ (appinput-bad-length.dat, sancin-malformed.dat:
                     one 199-byte record, one 79-byte record, one impossible date; the readers must refuse them, D-024)
                   - Coverage by a tool, not by hand: tools/data/coverage_report.py maps every one of the 433 non-DROPPED rules of
                     rule-to-service.csv to (fixture file, predicate on a record), reads the real files back and writes the first matching
                     record numbers; a rule whose predicate matches nothing becomes a gap
                   - No-real-data scan tools/data/scan_no_real_data.py (with --self-test, which injects five violations); field and
                     determinism checks tools/data/check_datasets.py; AC-8 round trip tools/data/RoundTrip.java
                   - testdata/README.md updated: seed, frozen date, how to regenerate and check, datasets, conventions, coverage

Acceptance criteria (build phase: tests in Part D; each line is the command and its result)
  [x] AC-1  python modern/tools/data/check_datasets.py -> the generator ran twice in fresh processes (PYTHONHASHSEED=random) into
            scratch folders: 13 files, all sha256 equal; modern/testdata equals a fresh run; manifest.csv matches every file; the
            README records the seed 20260115 and the frozen date 2026-01-15
  [x] AC-2  same script -> appinput-baseline.dat: 8 records, all 200 bytes: valid IN (rec 1), GR (2), FA (3), E101 missing NPI (4),
            E102 invalid type (5), E104 missing taxonomy (6), E103 duplicate NPI (7), plus E102+E103 (8); the 3 valid NPIs are not seeded
  [x] AC-3  same script -> provider-master-seed.dat: 16 records, all 130 bytes; statuses A D P S T; flags F P U; tiers H L M; prefixes
            193 207 225 251 282 291 332 341; due dates 3 before / 1 equal / 8 after 2026-01-15 (4 none); every date 8 digits, 4-digit
            year, a real date; the 16 records equal the 16 SQL rows (NPI, Medicaid ID, type, taxonomy, tier, status, flag, dates,
            names, licence, owner); tiers follow the legacy prefix rule
  [x] AC-4  same script -> sancin-baseline.dat: 6 records, all 80 bytes; actions T S R X; an unknown NPI (rec 4); the invalid action X
  [x] AC-5  same script -> scenarios/appinput-scenarios.dat: 8 records of 200 bytes for 1003000111 ... 1003000888 verbatim; types
            IND,PT->IN, DME,GRP->GR, HOS,HOSP,AMB->FA (QF-2); synthetic distinct Medicaid IDs; taxonomies (207Q00000X for 1003000111);
            expected-outcomes.csv: 444 FAIL E203, 555 FAIL E202, 111 222 333 777 888 PASS, 666 NONE (QF-14)
  [x] AC-6  python modern/tools/data/coverage_report.py -> 433 rules, 413 covered = 95.38%, 20 uncovered, each listed with a reason in
            coverage-gaps.csv; coverage.csv recomputed from the files on disk equals the file. Covered by basis: record 253, date 31,
            run 107, lookup 22 (see Not verified)
  [x] AC-7  python modern/tools/data/scan_no_real_data.py -> OK, 23 generated files (data, scenarios, negative, coverage, manifest and
            the SQL seed): every NPI has a valid check digit and lies outside the reserved ranges; names obviously fake; the test-data
            NPIs and names appear only in scenarios/. --self-test: the clean copy passes and each of five injected violations (invalid
            check digit, Luhn-valid reserved-range NPI, test-data NPI outside scenarios/, real-looking name, street address) is reported
  [x] AC-8  javac of libs/fixed-width/src/main/java into scratch, then java tools/data/RoundTrip.java modern/testdata -> OK, 7 files,
            51 records, 0 failures: provider master 130, APPINPUT 200, SANCIN 80; every record read by FixedWidthCodec.read, its
            view fields read, written back with write(), bytes identical. The two negative files: the codec refuses the 199-byte
            record, the 79-byte record and the impossible date 20260230 (D-024). Not run through Gradle (see Not verified)

Files created:     modern/testdata/{fixed-width/{appinput-baseline,provider-master-seed,provider-master-edge,sancin-baseline}.dat,
                     scenarios/{appinput-scenarios.dat,expected-outcomes.csv,provider-type-mapping.csv,business-dates.csv},
                     negative/{appinput-bad-length,sancin-malformed}.dat, coverage.csv, coverage-gaps.csv, manifest.csv, .gitattributes}
                   modern/testdata/README.md (updated)
                   modern/tools/data/{pemdata.py, generate.py, coverage_report.py, scan_no_real_data.py, check_datasets.py, RoundTrip.java}
                   modern/docs/reports/stories/MAM-44.md
Checks run:        python modern/tools/data/generate.py; check_datasets.py (AC-1..AC-5, 0 failed); coverage_report.py (95.38%, OK);
                   scan_no_real_data.py and --self-test (OK); javac + java RoundTrip.java (OK, 51 records);
                   ./gradlew --no-daemon -q :libs:fixed-width:cleanTest :libs:fixed-width:check (Gradle lock held, 1857 MB free before
                     the run, waited for memory) -> exit 0; results re-read: 257 tests, 0 failures, 0 errors, 0 skipped (the first
                     attempt without cleanTest was UP-TO-DATE and re-ran nothing, so it was repeated with cleanTest)
Not verified:      - AC-8 itself did not need Gradle: free memory was 400-1200 MB for most of the batch (below the 1500 MB rule), so the
                     round trip compiled the library sources with javac into scratch and ran RoundTrip.java with java (same codec
                     classes and exported layout JSON). Nothing under libs/ was changed
                   - Coverage honesty: 129 of the 413 rows are run-level or lookup basis (opening, reading, end of file, totals; the
                     seeded NPI as the inquiry key), 284 are a specific record or run date. The 20 uncovered rules are triggers that
                     are not data: 10 I/O or storage faults (PRVDIR01 ADD-03, PRVENR01 00059, PRVREV01 ADD-04, PRVRSK01 ADD-05 and
                     ADD-06, PRVSAN01 ADD-05, PRVSCR01 ADD-04 and ADD-05, PRVTMS01 ADD-06, PRVCICS1 ADD-04), 3 absent notice programs
                     (PRVSAN01 00009, 00038, 00053), PRVTMS01 ADD-01 (a line past column 72) and ADD-03 (unused fields), PRVCICS1
                     ADD-03 (3270 protocol) and ADD-05 (roles), and 3 screen events (PRVCICS1 00014, 00016, 00020). Counting the three
                     notice rules as covered would give 96.07%
                   - Nothing was loaded into the stack: no Docker, no make seed, no service call. The edge, negative and scenario files
                     have not been fed to the real intake, sanction or screening services
                   - The two bad-record files were checked against the codec only, not against the MAM-55 and MAM-57 readers
Questions for you: 1. A pending or denied provider has no due date (SQL NULL), but the codec rejects a blank and 00000000, and every
                      dataset record must parse (AC-8). Default applied: the value 99991231 stands for "no date" in every fixed-width file
                      (blank tier, due date 99991231); a reader maps it to none; MAM-50 AC-1 must treat it as NULL. Options: A keep it
                      (recommended) | B let the codec read an all-zero date as none (library change, the open MAM-42 question) | C write
                      effective date + 5 years (contradicts D-003 and the SQL seed)
                   2. Does a run-level row (any run over the file exercises it) count as a fixture row for AC-6? Default applied: yes,
                      marked basis=run. Option: count only basis=record and date, which gives 284 of 433 = 65.6%, and add more records
Next step:         code-reviewer; Part D: tests for AC-1..AC-8 wrapping these tools; MAM-51 AC-1 can be checked now (provider list endpoint
                   MAM-37 exists): after ./pem.sh seed (twice) GET /api/v1/providers?size=100 with an ANALYST token must give total 16 and
                   exactly the NPIs in the first 10 bytes of each line of testdata/fixed-width/provider-master-seed.dat
```

## Lane 3 tests (2026-11-19)

Tests for a story that was built without them (user approval 2026-11-19, lane 3: tests for every story built without tests). Written after the tools, so there was no red phase; the Outcome line above is unchanged.

- File: modern/tools/checks/tests/test_synthetic_datasets.py (58 test functions, 143 cases)
- Per AC, functions / cases: AC-1 8 / 10, AC-2 5 / 19, AC-3 9 / 31, AC-4 5 / 14, AC-5 7 / 13, AC-6 7 / 12, AC-7 9 / 25, AC-8 8 / 19. Every test is named test_mam44_acN_* and starts its docstring with "[MAM-44 AC-n]"
- Command: modern/kag/.venv/Scripts/python.exe -m pytest -q modern/tools/checks/tests/test_synthetic_datasets.py
- Result: 143 passed in 40 s (run in this session; no tool or dataset defect found). Run together with the MAM-53 tests: 261 passed in 103 s
- Test hashes: modern/docs/reports/stories/MAM-44.tests.sha256 (restore copy listed in MAM-44/red-blobs.txt); test_protection.py check MAM-44 gives OK
- How each AC is checked: generate.py runs twice into temp folders (other working folder, other PYTHONHASHSEED); the committed files under modern/testdata are only read, and a module guard fails the run if any of them changes. AC-1 compares the two runs, the committed tree, manifest.csv and the README seed and date. AC-2 to AC-5, AC-7 and AC-8 are written from the spec and the exported layout JSON with their own record reader, NPI check digit and mapping tables (they do not call the tools' reader); the same checks run on the committed tree and on the fresh run where a root can be chosen. AC-6 compares coverage.csv and coverage-gaps.csv with the 433 non-DROPPED rules (partition, reasons, record numbers inside the files) and runs coverage_report.py. AC-7 runs scan_no_real_data.py on both trees and its --self-test, and feeds it 12 violations of its own making plus one clean control. The shipped checkers (check_datasets.py, coverage_report.py, scan_no_real_data.py) are run as well
- AC-8 LIMIT: the Java round trip through libs/fixed-width (FixedWidthCodec.read, then write) is NOT run by these tests, and no Java is started from pytest. Python checks what it can: every record of every dataset under fixed-width/ and scenarios/ has the exact length of its layout (APPINPUT 200, PRVRECRD 130, SANCIN 80, read from the exported layout JSON), one LF per record and no CR, every date and digit field passes the codec's validation (real CCYYMMDD date, numeric), the provider master type code picks one of the three views, and each layout describes every byte of its record. The round trip itself stays covered by tools/data/RoundTrip.java, run by hand (result in this report above: 51 records, 0 failures, from the build phase); a test only checks that RoundTrip.java exists and drives the codec over the manifest
- Sensitivity: the tests were also run against thirteen deliberately damaged copies of the data (scratchpad, not the repo): no suspended provider, a 79-byte sanction record, no invalid sanction action, a flipped expected outcome, coverage rows removed, a 2-digit-year style date, no missing-NPI application, a gap row cut down to the text x, a changed README seed, a CRLF in a dataset, an NPI with a bad check digit, a duplicated provider NPI, a committed file that differs from the generator. Each one failed the tests of the AC it breaks
- Observations, none an AC failure: (1) The scan checks the first name of an individual (TESTPROV) and the legal and DBA names of an organisation, but not the last name or free text: a copy with last name JOHNSON beside TESTPROV, and a copy with a real-looking person named in a sanction reason, both passed the scan (exit 0). Spec AC-7 says names are obviously fake; the business may want the scan to cover free text (question below). (2) 129 of the 413 coverage rows are run or lookup basis (already in the Not verified list above, question 2); the tests accept them as fixture rows, as the spec words it. (3) The no-date value 99991231 (question 1 above) is excluded when the tests look for due dates before, equal to and after 2026-01-15; it would otherwise count as "after"
- Not covered: the Java round trip (see the limit above); loading the files into a running service (no stack was started); the intake, sanction and screening readers of other stories; the SQL seed files (out of scope of this story)
- Question for you: should scan_no_real_data.py also check the last name and the free-text reason fields for real-looking names? Options: A) leave as is, TESTPROV and SAMPLE mark the files as fake (recommended: free text is generated from our own fixed strings) | B) add a whitelist of every generated last name and reason text, and fail on anything else (a scan change, to be made by a developer, with a test added first)
