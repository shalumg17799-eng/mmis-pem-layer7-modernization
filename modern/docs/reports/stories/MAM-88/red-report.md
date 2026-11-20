# MAM-88 red report: the 10,000-provider synthetic set (data side of AC-1, and the data AC-4 needs)

Run on 2026-11-20 (late evening, preparation only: nothing was loaded into the stack, no load was run, no Gradle).
Hashes: `modern/docs/reports/stories/MAM-88.tests.sha256` (1 file, `test-protection check MAM-88` prints OK), restore copy in `red-blobs.txt`.
The file is a continuation of an earlier, stopped run: its unfinished test file was read, every assertion checked against the repository, and the sound part kept (see "Changes to the earlier draft").

| Level | Command | Tests | Result | Reason |
|---|---|---|---|---|
| pytest (tool tests) | `modern/kag/.venv/Scripts/python.exe -m pytest modern/tools/checks/tests/test_perf_dataset.py -q -p no:cacheprovider` | 35 | 35 failed | 31 x `generate.py perf did not run (exit 2): the perf mode is missing or broken` (the generator answers `unrecognized arguments: perf --count 10000`), 2 x the same exit 2 for `--count 50` and for the default count, 1 x no "required ... --out" message (the generator says `unrecognized arguments`), 1 x `modern/testdata/README.md` does not yet name `generate.py perf` |
| Unit / Cucumber / integration / contract | none in this step | 0 | n/a | The story is service `e2e`: AC-2 to AC-5 and the live part of AC-1 (load, total >= 10,000 through the gateway, k6, timings, 90 s jobs, the report) are measurements by the runner and the report, not tests; no endpoint or event changes |

Every failure is an assertion on a missing interface (exit code 2 of an unknown sub-command, a missing README paragraph). None comes from broken unrelated code.
The test file reads the small data set and the wiremock mappings only; the autouse guard fails the module if a file under `modern/testdata` changes, and no test writes outside `tmp_path`.

## Interface the tests drive (the smallest addition to `modern/tools/data/generate.py`)

```
modern/kag/.venv/Scripts/python.exe modern/tools/data/generate.py perf --count 10000 --out <folder>
```

`--out` is required (no default folder: the files are never stored in the repository); `--count` defaults to 10000; the seed is the recorded one (20260115, `modern/testdata/README.md`).
The folder receives exactly three files, LF line ends:

| File | Content |
|---|---|
| `provider-master-perf.dat` | PRVRECRD, 130-byte records (D-007), one LF after each, via `pemdata.Layout` |
| `provider-perf.sql` | the same providers as rows of `provider_db.provider`: the 18-column list of `testdata/seed/provider_db/01-providers.sql`, one row per line, no TRUNCATE/DELETE, every INSERT ends `ON CONFLICT DO NOTHING`; header says GENERATED, `seed 20260115`, `do not edit by hand` |
| `revalidation-perf.sql` | the same providers as rows of `revalidation_db.tracked_provider (npi, enroll_status, reval_due_date)`, same rules; the sweep reads this read model (`ExecuteSweep`, `JdbcRevalidationStore`), which a load of `provider_db` does not fill |

Existing commands and outputs stay byte for byte (a test regenerates the small set after a perf run and compares the 13 files with `modern/testdata`). `modern/testdata/README.md` documents the command and the three files.

## Decisions fixed by the tests

- **The 16 seeded providers are NOT among the perf providers (disjoint, not identical).** `01-providers.sql` TRUNCATEs `provider` and re-inserts exactly the 16 rows (`make seed` restores the seed state); `provider.npi` and `provider.public_id` are UNIQUE. A perf set that repeated a seeded NPI would clash, so the perf rows are loaded on top of the seed (16 + 10,000), additive, and no perf NPI, Medicaid ID or public_id equals one of the seed, of any other small-set file (edge, intake, scenarios, sanctions, negative), of the reserved ranges, of the `aws-transform/test-data` feed or of a wiremock hit stub.
- **NPI block:** the small set's 10061xxxxx block has 10,000 slots in all (10061 + 4 digits) and 14 of them are taken, so 10,000 perf NPIs cannot come from it. The tests do not name a block; a block such as 1007xxxxxx (10070 to 10079 + 5 digits) has the room. The seed block 10059xxxxx also holds the NPPES, SAM and SSA-DMF hit stubs (1005999036, 1005999010, 1005999028).
- **Licences:** the licence-board stub answers ACTIVE only for `^SD1000[1-7]$` / TX (`clean-seeded.json`, read by the test). Screening of an individual calls it with the provider's licence, so every perf individual carries one of those (shared by many providers; `license_no` is not unique). Groups and facilities have no licence check (Q-041 A).
- **Seed conventions kept:** screen flag by status (P:U, D:F, A/T/S:P); tier only for A, T, S by the legacy rule (332 and 251 H, 341 291 225 M, else L); pending and denied carry `99991231` (NULL in SQL); end date only for T, inside the enrolment; individuals have no owner, groups and facilities an owner who is a perf individual.
- **Dates:** effective 2000-01-01 to 2026-01-15 (a future start or a start before 2000 would trigger the PRVTMS01 date anomalies of the edge file, not the ordinary path); A, T, S have a due date after the effective date, so no ACTIVE provider hits the open no-due-date question (Q-049).

## Minimums asserted for the 10,000, and why

| Minimum | Why |
|---|---|
| each of P A T D S >= 100 (1%) | T-MSIS and the sweep must leave the non-active ones out; an all-ACTIVE set would not exercise a filter |
| ACTIVE >= 5,000 | PRVTMS01 extracts ACTIVE only; the 90 s target of AC-4 is for an extract that carries a large share of the 10,000 |
| ACTIVE with due <= 2026-01-15 >= 500 | PRVREV01 queues ACTIVE and due <= business date; due on the frozen date and on any later sweep date, so the sweep opens a real number of cases |
| ACTIVE with due from 2027-01-01 >= 1,000 | not due on any 2026 date: the sweep's date filter is exercised |
| T or S with due <= 2026-01-15 >= 100 | overdue but not ACTIVE: the sweep's status filter is exercised at scale (the expected queue is ACTIVE and due only) |
| IN, GR, FA >= 500 each; every risk prefix 332 251 341 291 225 and one other | search and detail read both views of the REDEFINES |

## AC to test mapping (all in `modern/tools/checks/tests/test_perf_dataset.py`, named `test_mam88_ac1_<words>`)

AC-1 data side, determinism and files: `two_runs_with_the_recorded_seed_give_byte_identical_files` (other folder, other PYTHONHASHSEED), `the_output_folder_holds_exactly_the_three_perf_files`, `the_130_byte_file_holds_exactly_10000_records_of_130_bytes`, `every_byte_of_the_130_byte_file_is_printable_ascii`, `the_count_option_sets_the_number_of_providers`, `the_default_count_is_10000`, `perf_mode_requires_an_output_folder_and_writes_nothing_into_the_repository`, `the_existing_small_data_set_is_unchanged_by_the_perf_mode`.
AC-1 synthetic-data rules: `every_npi_is_unique_ten_digits_with_a_valid_check_digit`, `no_npi_is_in_a_reserved_journey_range_the_feed_or_a_stub_hit`, `every_medicaid_id_is_nine_digits_starting_with_9_and_unique`, `public_ids_are_unique_uuids`, `every_name_follows_the_synthetic_naming_rule`, `the_data_tools_own_no_real_data_rules_find_nothing_in_the_three_files` (the tools' own `scan_no_real_data` rules on every field of every record and on both SQL files), `individual_licences_are_ones_the_licence_board_stub_answers_active`, `every_owner_npi_is_valid_and_belongs_to_an_individual_of_the_set`.
AC-1 cannot clash with the seed: `none_of_the_16_seeded_providers_is_among_the_perf_providers`, `no_perf_npi_or_medicaid_id_is_in_the_small_data_set`.
AC-1 / AC-4 mix: `every_status_flag_and_tier_follows_the_seed_convention`, `each_of_the_five_enrolment_statuses_occurs_at_least_100_times`, `at_least_5000_providers_are_active_for_the_tmsis_extract`, `at_least_500_active_providers_are_due_on_the_frozen_business_date`, `at_least_1000_active_providers_are_not_due_before_2027`, `at_least_100_terminated_or_suspended_providers_are_overdue_but_must_not_be_queued`, `all_three_provider_types_and_every_risk_taxonomy_prefix_occur`, `dates_are_real_ccyymmdd_dates_and_no_enrolment_starts_after_the_business_date`, `active_terminated_and_suspended_have_a_due_date_after_enrolment_pending_and_denied_have_none`.
AC-1 SQL: `the_provider_sql_names_every_one_of_the_10000_npis_once`, `the_provider_sql_uses_the_column_list_of_the_seed_file`, `the_provider_sql_rows_equal_the_fixed_width_records`, `only_terminated_providers_carry_an_end_date_inside_their_enrolment`, `both_sql_files_are_additive_and_re_runnable`, `both_sql_files_state_the_recorded_seed_in_their_header`.
AC-4 data side: `the_revalidation_sql_tracks_every_perf_provider_with_its_status_and_due_date`.
Documentation: `the_testdata_readme_documents_the_perf_mode_and_its_three_files`.

Not covered here (live, run by the lead alone on the stack, then reported): loading the files, `GET /api/v1/providers` total >= 10,000 (AC-1 live part), the k6 runs (AC-2), submit and screening timings (AC-3), the 90 s sweep and extract (AC-4), the report with the DEFAULT marks (AC-5).

## Changes to the earlier draft (the stopped run's file)

Kept as written: the three-file interface, the helper imports from `test_synthetic_datasets.py` (no rule copied by hand), the minimums above, the SQL parser, the seed-state decision.
Changed: (1) the "requires --out" assertion no longer depends on the word order of argparse's message; (2) the no-real-data test now mirrors the per-record loop of `scan_no_real_data.scan` (text rules on every non-NPI field too); (3) the "additive and re-runnable" test ignores SQL comment lines (a header comment may say `ON CONFLICT DO NOTHING`); (4) the control of the small-set test required more than 50 NPIs (the small set holds 40) and now requires that the collected sets contain the 16 seeded providers plus other files' values; (5) one new test: every byte of a record is printable ASCII.

## Evidence that the tests are satisfiable and not vacuous (throwaway, outside the repository)

A scratch oracle generator (not the data-engineer's design, never handed over, kept under the session scratchpad `mam88-oracle/`) was run against the real test file with the tools folder patched: 34 of 35 tests pass, the 35th is the README test (README is the data-engineer's file). Nine single-fault variants of the oracle were each caught by the tests meant to catch them: a seeded NPI reused (7 tests), an unknown licence (1), all providers ACTIVE (2), a time-based seed (determinism), one future enrolment date (dates and SQL equality), an SQL row that disagrees with the record (2), a TRUNCATE in the SQL (1), a real-looking first name (2), and the clean run (README only).

## Notes for the runner and the lead

- `modern/tools/perf/out/` is NOT covered by `.gitignore` (`git check-ignore` exits 1). The runner must write its generated files to the session scratchpad (`perf-out`), or the lead adds an ignore line; the test file never writes into the repository.
- Loading the perf rows on top of the seed leaves `make seed` as the restore for `provider_db` and `revalidation_db`: `01-providers.sql` TRUNCATEs `provider` and `01-revalidation.sql` TRUNCATEs `tracked_provider`, the cases and the runs. There is no `testdata/seed/tmsis_db`, so the T-MSIS extract's `extract_run`, `extract_row` and `extract_archive` rows (thousands of rows) are not cleared by `make seed`; the runner should say so or the lead decides how to clean `tmsis_db`.
- A sweep over 500+ due providers opens that many cases and may publish RevalidationDue events that other services act on; the runner should expect that and reseed afterwards.
