# MAM-54 red report

## Test change after recording (2026-11-19)

- **File:** `modern/services/enrollment-service/src/test/resources/features/MAM-54.feature`, scenario AC-4.
- **Fault (in the test, not the code):** AC-4 submitted its two rejected applications for NPI `1003000111`. The Background
  already holds a VALIDATED application for that NPI, so the validator correctly adds E103 (duplicate NPI, MAM-46 AC,
  R-PRVENR01 duplicate rule). The scenario then saw `E103/npi, E104/taxonomy` instead of the `E104/taxonomy` the spec asks for.
- **Fix:** AC-4 now submits for NPI `1003000119`, which has no VALIDATED application. The spec text of AC-4 ("rejected for
  an empty taxonomy") is unchanged; only the test data moved. User A may still read them because they submitted them (D-023).
- **Production code:** not changed for this.
- **Hashes:** re-recorded with `test_protection.py record MAM-54`.

## Test change for Q-095 (user decision 2026-11-19)

- **File:** `modern/services/enrollment-service/src/test/resources/features/MAM-54.feature`, scenario AC-4.
- **Why:** Q-095 now refuses (403) an individual (IN) application from a PROVIDER under any NPI other than the npi claim
  of their token. AC-4 had user A (token NPI `1003000111`) submit its IN application for NPI `1003000119`, which is now
  a 403 by the new requirement, not by a defect.
- **Change (test data only, no assertion changed):** the scenario now starts with the existing step
  `Given no applications exist` and submits both rejected applications (IN and XX, empty taxonomy) for NPI `1003000111`,
  user A's own NPI. Clearing the Background's VALIDATED application keeps the expected errors exactly `E104/taxonomy`
  and `E102/providerType,E104/taxonomy` (no E103, which was the reason for moving to `1003000119` earlier).
- **Production code:** the Q-095 rule (enrollment-service `OwnNpiRule`, `SubmitApplication`).
- **Hashes:** re-recorded with `test_protection.py record MAM-54`.
