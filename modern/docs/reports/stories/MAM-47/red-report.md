# MAM-47 red report

The test recorded under MAM-47 before this section is `modern/tools/checks/tests/test_wiremock_stubs.py` (calls the running
stack; unchanged). This file did not exist before; it starts with the section below.

## Intake licences known to the stub, added 2026-11-20 (parity U-10, Q-130 default A)

**Why.** The parity report (`modern/docs/reports/parity-report.md`, group U-10, section 6.1 and section 8 item 7) found that
application 1006133288 (`modern/testdata/fixed-width/appinput-baseline.dat`, record 1) fails the modern screening. The
generator (`modern/tools/data/generate.py:161`) creates it as a valid individual with licence SD20001 / TX, but the licence-board
stub knows only the six sample licences and SD10001-SD10007 (`clean-seeded.json` matches `^SD1000[1-7]$`), so the catch-all
`not-found.json` answers it and screening sets flag F (Q-041 default A). The lead applied default A of Q-130: add the licence
to the stub, test first. This is a test-data and mock gap; no business rule changes.

**AC chosen: MAM-47 AC-4** (the licence board knows the licences of the test data's individuals and answers any other licence
not found). AC-4 names "each licence of a seeded individual"; an intake applicant is a test-data individual of the same kind,
so this is an additive reading of AC-4, stated here so the reviewer can overrule it. No MAM-47 AC is about the intake file
itself, but no other AC fits better (AC-2 is the J1 applicant, AC-7 error and slow).

**New file** (additive; recorded in `MAM-47.tests.sha256` together with the existing file, `red-blobs.txt` updated by the tool):

| File | sha256 | Blob id |
|---|---|---|
| `modern/tools/checks/tests/test_intake_licences_known_to_stub.py` | `6c975f3935e9095faac5d2c00fb235266700cd7dc8d25b31b6fafe7fc1da3174` | `f9671454d3c43477d1bdaf0900c7333d0edf122a` |

**How it works.** No stack is needed. The test evaluates `modern/infra/wiremock/mappings/**/*.json` offline the way WireMock
does (method, `urlPath`, query-parameter and header matchers; lowest priority number wins; ties with different answers are an
error; `{{request.query.x}}` rendered; any matcher or template it does not know raises `NotImplementedError` instead of
guessing). "Valid" is the data set's own definition, not an NPI list: `tools/data/coverage_report.py` (`Fixture(..., "APP")`,
predicate `valid_any`: the PRVENR01 validation E101-E104 all clear), cross-checked with `testdata/coverage.csv` (rule
R-PRVENR01-cbl-00007 lists the same records). Records are decoded with the exported layout `APPINPUT.json` through
`tools/data/pemdata.py`. Only type IN carries a licence (the organisation view has no licence field), so records 2 and 3
(valid GR, FA) are not looked up.

| Test | AC | Result before the fix |
|---|---|---|
| `test_mam47_ac4_every_valid_intake_individual_has_an_active_licence_on_the_stub` | AC-4 | **FAILS (RED)**: names SD20001 / application 1006133288 only |
| `test_mam47_ac4_a_licence_no_mapping_knows_is_still_answered_not_found[ZZ99999-TX]`, `[ZZ99999-NY]` | AC-4 | passes: guard, a fix that accepts every licence turns it red |
| `test_mam47_ac4_the_offline_evaluator_gives_the_documented_answer[...]` (17 rows) | AC-4 | passes: premise, the evaluator reproduces every licence-board row of `infra/wiremock/README.md` (hit, clean, not-found, error and slow by header) |
| `test_mam47_ac4_the_valid_applications_are_the_ones_the_coverage_file_registers` | AC-4 | passes: premise, "valid" agrees with `coverage.csv` |

Count: 21 tests in the file (1 red, 20 green by design); level: Python tool tests (offline, no stack).

**RED output** (`modern/kag/.venv/Scripts/python.exe -m pytest modern/tools/checks/tests/test_intake_licences_known_to_stub.py -q -p no:cacheprovider`, from the repository root):

```
F....................                                                    [100%]
E       AssertionError: the licence-board stub does not know the licence of a valid intake individual:
E           application 1006133288 (record 1): licence SD20001 / TX is answered 200 {"source": "LICBOARD", "licenseNo": "SD20001", "state": "TX", "found": false} by the [not-found] mapping 'license-board not-found: any other licence / state is not found'; expected found=true, status=ACTIVE
modern\tools\checks\tests\test_intake_licences_known_to_stub.py:132: AssertionError
FAILED modern/tools/checks/tests/test_intake_licences_known_to_stub.py::test_mam47_ac4_every_valid_intake_individual_has_an_active_licence_on_the_stub
1 failed, 20 passed in 1.88s
```

The failure is the gap and nothing else: one valid individual (record 1), one licence, answered by the not-found catch-all.

**The test can go green, and the guard works** (checked on a copy of the mappings in the session scratchpad; the repository
mappings were not touched): with an added `clean` mapping for SD20001 / TX the first test and both guard cases pass; with the
wrong fix "the not-found mapping answers found=true" the first test passes and both guard cases fail.

**Not verified.** The running WireMock engine (the evaluator is a small re-implementation, validated against the 17 documented
licence-board answers only); screening, the master record or the parity numbers after the fix (the report expects flag P and tier
L for 1006133288, not verified); `modern/specs/MAM-47.md` field `testsSha256` still holds the sha256 of the previous hash file
and must be refreshed by the owner of that file (the hash file changed by one line).
