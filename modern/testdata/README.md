# modern/testdata: synthetic data only

No real provider data, ever (MODERNIZATION.md section 6 rule 4). Names are obviously fake (TESTPROV ALPHA,
SAMPLE CLINIC 01); NPIs have a valid check digit (Luhn with the 80840 prefix) and are not looked up anywhere real.

| Item | Value |
|---|---|
| Seed | 20260115, also the frozen business date. One stream, `random.Random(20260115)`, in both generators. |
| Frozen business date | 2026-01-15 (`20260115` in the fixed-width files). Fixtures use no other date, except the named run dates of `scenarios/business-dates.csv`, which exist so that date rules can be run on purpose. |
| SQL seed generator | `generate_seed.py`: same seed, same files. Do not edit generated files by hand. |
| Fixed-width generator | `tools/data/generate.py` (MAM-44): draws first exactly what `generate_seed.py` draws (so the 16 seeded NPIs and Medicaid IDs are identical), then the new values in a fixed order. Layout positions are read from `libs/fixed-width/src/main/resources/layouts/*.json` (exported from the graph), never typed. |
| Load | `make seed` (= `infra/scripts/seed.sh`): every `seed/<service>_db/*.sql`, one transaction per file. Re-runnable: restores exactly the seed state. |

## Regenerate and check (MAM-44)

```
python modern/tools/data/generate.py            # writes fixed-width/, scenarios/, negative/, coverage*.csv, manifest.csv
python modern/tools/data/check_datasets.py      # AC-1 determinism (two fresh runs, sha256) and the AC-2 to AC-5 field checks
python modern/tools/data/coverage_report.py     # AC-6: recompute the rule coverage from the files and compare coverage*.csv
python modern/tools/data/scan_no_real_data.py   # AC-7: the no-real-data scan (--self-test injects violations)
java -cp "<lib classes>;<lib resources>;<jackson jars>" modern/tools/data/RoundTrip.java modern/testdata   # AC-8: libs/fixed-width
```

`generate.py` never touches `seed/`: the SQL seed stays exactly as `generate_seed.py` wrote it, and the provider master file below
is checked against it row by row. All generated files use LF line ends, one record per line; `.gitattributes` marks `*.dat` as
binary so Git never converts them. `manifest.csv` lists the layout, record count, size and sha256 of every generated file.

## Fixed-width datasets (MAM-44)

| File | Layout | Records | Content |
|---|---|---|---|
| `fixed-width/provider-master-seed.dat` | PRVRECRD 130 | 16 | The 16 providers of the table below: same NPIs, Medicaid IDs, names, statuses and dates as `seed/provider_db/01-providers.sql`. |
| `fixed-width/provider-master-edge.dat` | PRVRECRD 130 | 5 | States some rules need that the seed must not hold (it would change the SQL seed and the provider list of MAM-51): active with a future enrolment year (2027), active enrolled in 1998, active with no due date, active with screen flag F, pending with a blank taxonomy. NPIs in 10061xxxxx. |
| `fixed-width/appinput-baseline.dat` | APPINPUT 200 | 8 | 1 valid IN, 2 valid GR, 3 valid FA, 4 missing NPI (E101), 5 invalid type ZZ (E102), 6 missing taxonomy (E104), 7 duplicate of record 1 (E103), 8 invalid type and duplicate of record 2 (E102 and E103). New NPIs in 10061xxxxx, none seeded. |
| `fixed-width/sancin-baseline.dat` | SANCIN 80 | 6 | T on SAMPLE MEDICAL SUPPLY 01, S on SAMPLE HOSPITAL 01, R on SAMPLE THERAPY GROUP 01 (suspended), T on an unknown NPI, action X on SAMPLE CLINIC 01 (invalid, D-026), R on SAMPLE HOME HEALTH 01 (pending; D-027: the legacy rule is preserved). None of the journey providers ALPHA, BRAVO, CHARLIE, the hospices or the ambulance. |
| `scenarios/appinput-scenarios.dat` | APPINPUT 200 | 8 | The 8 applications of `aws-transform/test-data/providers.txt`: verbatim NPIs and names, feed types mapped (below), synthetic Medicaid IDs and taxonomies added. |
| `negative/appinput-bad-length.dat` | APPINPUT | 4 | Records 1, 2 and 4 are 200 bytes; record 3 is 199 bytes (an unreadable record: the run must fail naming it, MAM-57 AC-5, D-024). |
| `negative/sancin-malformed.dat` | SANCIN | 4 | Record 2 has the impossible effective date 20260230 (rejected, the run goes on); record 3 is 79 bytes (unreadable: the run fails, MAM-55 AC-6). |

Conventions of the fixed-width files:
- **No tier** (a pending or denied provider, SQL NULL) is a blank in byte 32.
- **No due date** (SQL NULL) is `99991231`. The codec accepts only real dates (a blank or `00000000` is an error, D-024) and every
  record of a dataset must parse, so "no date" needs a real value that no sweep ever reaches. A reader maps `99991231` to "none".
  Pending and denied providers, the intake applications and the edge provider with no due date carry it (default applied; see the
  report of MAM-44, question 1).
- **Application records** (APPINPUT) leave bytes 32-34 (tier, status, flag) blank (PRVENR01 sets them), carry the application date
  2026-01-15 as effective date and no due date, and keep bytes 131-200 blank (D-025). The reader takes bytes 1-130 only.
- The end date of a terminated provider (SQL `end_date`) has no field in the 130-byte record and is not written.
- Individuals: last name, then first name (`TESTPROV` is the first name, `ALPHA` the last name). A feed individual is split at its
  first space, so `last + ' ' + first` is the feed name verbatim.

### Scenarios (AC-5)

`scenarios/expected-outcomes.csv` lists the expected screening outcome of each application: 1003000444 FAIL E203 (owner 1990000111 is
on the LEIE list), 1003000555 FAIL E202 (licence PT99900 expired), 1003000111, 1003000222, 1003000333, 1003000777 and 1003000888 PASS,
and 1003000666 `NONE`: no expected outcome yet (QF-14, default A: the screening stories decide how a licence that is not found is
classified, then this row is updated).
`scenarios/provider-type-mapping.csv` is the QF-2 mapping (default A, business to confirm): IND and PT to IN; GRP and DME to GR; HOSP,
HOS, AMB, LAB and HHA to FA. The feed has no taxonomy: one synthetic code per feed type (207Q00000X for IND). The licence numbers of the
group and facility applications have no field in the organisation view and are not carried; the licence-board stub (dat-wiremock) answers them.
`scenarios/business-dates.csv` holds the frozen date and three run dates that make a date rule run on purpose: 2026-03-31 (the
month-end sweep of J6), 2028-02-29 (legacy due-date arithmetic on a leap day, Q-017) and 2065-01-15 (the legacy 2-digit pivot at 70, D-004).

### Coverage (AC-6)

`coverage.csv` (`rule_id,fixture_file,record_ref,basis`) has a row for every rule of `docs/architecture/rule-to-service.csv` that is not
DROPPED (433, QF-9 default A) that the datasets exercise; `coverage-gaps.csv` (`rule_id,reason`) lists each one they do not. `record_ref` is
`rec:N;N;N` (record numbers, at most three) or `all`. `basis`: `record` (a specific record triggers the branch), `run` (any run over the file
exercises it: opening, reading, end of file, totals), `lookup` (the NPI of a record is the key the rule looks up) or `date` (a business
date of `scenarios/business-dates.csv`). Rows are computed from the files, not typed: each rule maps to a predicate on the real records, and a
rule whose predicate matches nothing becomes a gap. The uncovered rules are the ones whose trigger is not data: I/O faults, the absent
notice programs, a source line past column 72, unused fields and three screen events of the 3270 inquiry.

### No real data (AC-7)

`tools/data/scan_no_real_data.py` fails on any NPI without a valid check digit (Luhn with prefix 80840) or inside 1003000000-999 or
1990000000-999, any name that is not obviously fake (first name TESTPROV, legal name SAMPLE ...), any address, e-mail or SSN-shaped number.
The only exception: the NPIs and names of `aws-transform/test-data`, used verbatim in `scenarios/` and nowhere else (QF-12, option A).

## Provider seed (MAM-50): `seed/provider_db/01-providers.sql`

| NPI | Name | Type | Taxonomy | Tier | Status | Screen | Revalidation due | Licence (TX) | Used by |
|---|---|---|---|---|---|---|---|---|---|
| 1005940865 | TESTPROV ALPHA | IN | 207Q00000X | L | A | P | 2030-06-01 | SD10001 | J5 suspend and reinstate; walking skeleton lookup |
| 1005951276 | TESTPROV BRAVO | IN | 225100000X | M | A | P | 2026-01-15 | SD10002 | J6 revalidation due |
| 1005989276 | TESTPROV CHARLIE | IN | 207Q00000X | L | A | P | 2025-12-01 | SD10003 | J6 revalidation due (overdue) |
| 1005925213 | TESTPROV DELTA | IN | 207Q00000X | - | P | U | - | SD10004 | pending, unscreened |
| 1005960046 | TESTPROV ECHO | IN | 225100000X | - | D | F | - | SD10005 | denied |
| 1005988179 | TESTPROV FOXTROT | IN | 207Q00000X | L | S | P | 2027-05-01 | SD10006 | suspended |
| 1005918507 | TESTPROV GOLF | IN | 207Q00000X | L | T | P | 2024-02-01 | SD10007 | terminated (end 2025-06-30) |
| 1005912765 | SAMPLE CLINIC 01 | GR | 193200000X | L | A | P | 2028-04-01 | | owner 1005940865 |
| 1005947811 | SAMPLE HOSPICE 01 | FA | 251G00000X | H | A | P | 2026-02-15 | | J7 directory search HOSPICE (shown); J6 due |
| 1005932946 | SAMPLE HOSPICE 02 | FA | 251G00000X | H | T | P | 2025-03-01 | | J7 directory search HOSPICE (not shown) |
| 1005943141 | SAMPLE MEDICAL SUPPLY 01 | FA | 332B00000X | H | A | P | 2029-08-01 | | |
| 1005933068 | SAMPLE HOME HEALTH 01 | FA | 251E00000X | - | P | U | - | | pending, unscreened |
| 1005923986 | SAMPLE AMBULANCE 01 | FA | 341600000X | M | A | P | 2026-03-31 | | J6 due on the sweep date |
| 1005914035 | SAMPLE LAB 01 | FA | 291U00000X | - | D | F | - | | denied |
| 1005992312 | SAMPLE HOSPITAL 01 | FA | 282N00000X | L | A | P | 2030-01-01 | | |
| 1005919109 | SAMPLE THERAPY GROUP 01 | GR | 225100000X | M | S | P | 2027-09-01 | | suspended |

Coverage (AC-2): statuses P, A, T, D, S; screen flags U, P, F; tiers L, M, H; taxonomy prefixes 332, 251, 341, 291,
225 and the others 207, 193, 282. Tiers follow the legacy rule (332/251 High, 341/291/225 Moderate, else Limited).

**Licence-board stub (MAM-47):** the licences of the seeded individuals, SD10001, SD10002, SD10003, SD10004,
SD10005, SD10006 and SD10007 (state TX), must be answered ACTIVE. So must the licence of the valid intake individual
(application 1006133288 of `fixed-width/appinput-baseline.dat`): SD20001 (state TX), see `infra/wiremock/mappings/license-board/clean-SD20001.json`.

## Journey applicants: never seeded (MAM-50 AC-4)

1003000111, 1003000222, 1003000333, 1003000444, 1003000555, 1003000666, 1003000777, 1003000888 (the eight
applications of `aws-transform/test-data/providers.txt`) and 1990000111 (the LEIE-excluded owner). No seeded NPI lies
in 1003000000-1003000999 or 1990000000-1990000999, so applying with them is never a duplicate (E103).

## Other generated seeds (MAM-51)

`generate_seed.py` also writes the histories and read models of the other services, matching the provider seed:
`seed/screening_db/02-screenings.sql`, `seed/sanctions_db/01-sanctions.sql`, `seed/directory_db/02-directory.sql` and
`seed/revalidation_db/01-revalidation.sql`. MAM-51 AC-1 compares the provider list with `fixed-width/provider-master-seed.dat`
(exactly the 16 NPIs above); the edge providers live in a separate file for that reason.

## Large performance set (MAM-88): generated at run time, never stored

The performance smoke needs 10,000 providers. They are **not committed**: `generate.py perf` writes them into a folder that git ignores
(or outside the repository) and refuses an `--out` inside `modern/testdata`. Same seed (20260115, one `random.Random` stream, no set is
iterated, so the output does not depend on `PYTHONHASHSEED`), same bytes.

```
python modern/tools/data/generate.py perf --out <folder> [--count 10000]     # --out is required; --count defaults to 10000 (1 to 50000)
python modern/tools/perf/load_perf_dataset.py [--count 10000] [--out <folder>] [--dry-run]    # generate, check offline, load, print counts
bash modern/infra/scripts/seed.sh                                            # remove the set again (restores exactly the 16 seeded providers)
```

| File written to `--out` (exactly these three) | Content |
|---|---|
| `provider-master-perf.dat` | PRVRECRD, 130-byte records (layout read from the graph export), one LF after each, in NPI order. |
| `provider-perf.sql` | The same providers as rows of `provider_db.provider`: the 18-column list of `seed/provider_db/01-providers.sql`, one row per line, 1,000 rows per `INSERT ... ON CONFLICT DO NOTHING`. No `TRUNCATE`: it is loaded on top of the seed, 16 + 10,000, and may be loaded twice. |
| `revalidation-perf.sql` | The same providers as rows of `revalidation_db.tracked_provider` (`npi, enroll_status, reval_due_date`): the revalidation sweep reads that read model, which `ProviderStatusChanged` events fill and a SQL load does not. |

- **Disjoint from everything else:** NPIs in the block `1007000000`-`1007999999` (valid check digit), none of them an NPI of the 16 seeded providers, of any
  small-set file, of the reserved ranges, of `aws-transform/test-data` or of a `hit-<npi>.json` stub; Medicaid IDs `9` + 8 digits, none in the small set;
  `public_id = uuid5(seed namespace, npi)`.
- **Names:** individuals `TESTPROV PERFnnnnn` with a licence SD10001-SD10007 / TX (the licence-board stub answers these ACTIVE, so a perf screening takes the
  clean path); groups and facilities `SAMPLE PERF GROUP nnnnn` / `SAMPLE PERF FACILITY nnnnn`, half with a DBA `SAMPLE PERF DBA nnnnn`, owner = a perf individual.
- **Mix at 10,000 (exact quotas, not luck):** IN 6000, GR 1500, FA 2500; status A 6000, T 1200, S 800, P 1200, D 800; screen flag by status (P:U, D:F, A/T/S:P);
  tier only for A, T and S by the legacy taxonomy rule; of the ACTIVE ones 900 are due on or before 2026-01-15, 900 fall in the rest of 2026 and 4200 are due from
  2027; 840 terminated and 240 suspended providers are overdue (the sweep must skip them). Pending and denied carry the no-date value `99991231` (NULL in SQL).
  Effective dates 2000-01-01 to 2026-01-15; `end_date` only for terminated providers.
- **What the load fills:** `provider_db.provider` (read by provider-service; tmsis-service, screening-service and directory-service reach it only through
  provider-service's API) and `revalidation_db.tracked_provider`. Nothing else: `directory_db`, `enrollment_db` and the rest are filled by the runs themselves.
- **Removing it:** `make seed` (`seed.sh`) TRUNCATEs both tables (`provider`, `tracked_provider`). It does not reset `tmsis_db` (no seed folder), so the extract
  runs and rows of a measured T-MSIS extract stay.
