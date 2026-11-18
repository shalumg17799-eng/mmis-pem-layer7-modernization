---
name: synthetic-data
description: How to build the fixed-width codec, synthetic legacy-format files and seed data for the PEM modernization. Covers layouts from the graph, coverage of every rule branch, the converted sample feeds, and the no-real-data rules. Use in Phase 5 and whenever a test needs data.
---

# Synthetic data and legacy formats

## 1. Rules

- **Never use real provider data.** Names are obviously fake (`TESTPROV ALPHA`,
  `SAMPLE CLINIC 01`); addresses use `123 TEST ST`.
- NPIs: 10 digits with a **valid** NPI check digit (Luhn with the 80840 prefix), so that
  future NPI validation will not reject them. Generate them from a seeded random
  generator. They are not looked up anywhere real.
- The generator is deterministic: the same seed always gives the same files. Store the
  seed in `modern/testdata/README.md`.

## 2. Fixed-width codec (`modern/libs/fixed-width`)

- Small Java library: a layout is a list of fields (name, start, length, type).
- Layouts are generated from the graph (`Record`/`Field` nodes), exported to
  `modern/libs/fixed-width/src/main/resources/layouts/<record>.json`. Never typed by hand.
- Types: alphanumeric (right-pad with spaces), numeric display (left zero-fill),
  CCYYMMDD date, YYDDD date. A REDEFINES discriminator picks the view.
- Every layout has a round-trip test: parse, then write, gives identical bytes.
- Record-length decision D-007 controls the provider master layout (121 or 130).

## 3. Layouts needed

| File | Record | Length | Used by |
|---|---|---|---|
| APPINPUT | application input | 200 | enrollment-service intake |
| APPERROR | application error (free text) | 250 | enrollment-service |
| Provider master | PRVRECRD | 130 (or 121, per D-007) | seed data, parity |
| SCRNOUT | SCRNREC | 67 | screening-service legacy writer |
| SANCIN | sanction input | 80 | sanctions-service intake |
| REVALQ | revalidation queue | 80 | revalidation-service legacy writer |
| TMSISOUT | T-MSIS extract | 120 | tmsis-service legacy writer |
| DIROUT | directory extract | 100 | directory-service legacy writer |

The APPINPUT layout is inline in PRVENR01 (no copybook). The legacy-analyst documents it
in `modern/docs/analysis/data-layouts.md` in Phase 2.

## 4. Datasets to generate (`modern/testdata/`)

- `fixed-width/appinput-baseline.dat`: at least one record per verified enrolment
  validation branch (valid IN, GR and FA; missing NPI; invalid type; missing taxonomy;
  duplicate NPI).
- `fixed-width/provider-master-seed.dat`: providers covering every status, screen flag,
  risk tier and taxonomy prefix (332, 251, 341, 291, 225, other), with revalidation dates
  in the past, today and the future relative to the frozen business date.
- `fixed-width/sancin-baseline.dat`: T, S and R actions, an unknown NPI, an invalid action.
- `scenarios/`: the 8 applications from `aws-transform/test-data/providers.txt`
  converted to APPINPUT format and mapped to legacy type codes (D-008), plus the
  matching WireMock stubs: `1990000111` on LEIE, licence `PT99900` expired, SAM empty.
- `sql/<service>-seed.sql`: the same providers as rows for each service's own tables.
- `coverage.csv`: `rule_id,fixture_file,record_ref` for every verified rule branch.
  Target ≥ 95% of verified rules; list the rest with a reason.
- Frozen business date for all fixtures: `2026-01-15` (recorded in `testdata/README.md`).

## 5. Mocked external sources (WireMock, `modern/infra/wiremock/`)

One folder per source: `leie`, `sam`, `nppes`, `license-board`, `ssa-dmf`. Each has:
- a hit stub (excluded, expired or deceased)
- a clean stub
- a 500-error stub
- a slow-response stub

These let screening-service's retry and manual-review paths be tested. The response
shapes are our own simple JSON (documented in the stub README), because the real
APIs will be wired in a later release.
