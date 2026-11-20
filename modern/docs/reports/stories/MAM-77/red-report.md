# MAM-77 red report: Console provider search by NPI, Medicaid ID or name (replaces CICS PINQ)

Run on 2026-11-19 (fast mode). Before the story the page was the MAM-34 single-provider lookup (one NPI input, GET /api/v1/providers/{npi}).
Hashes: `modern/docs/reports/stories/MAM-77.tests.sha256` (3 files), restore copies in `red-blobs.txt`.

| Level | Command | Tests | Result | Reason |
|---|---|---|---|---|
| Vitest | `vitest run tests/console/provider-search.test.tsx` (PowerShell, lowercase drive) | 29 | 28 failed, 1 passed | 26 x no label "Medicaid ID", 1 x no label "Name contains", 1 x no summary "1 error in the search". The 1 that passed (empty page, no call, NPI empty) was already true on the MAM-34 page. |
| Playwright | `playwright test e2e/console-search.spec.ts` | 6 | 5 failed, 1 passed | no "Medicaid ID" input, the result row (the page asks the single-provider endpoint, which the stub does not answer), no summary, no failure message, keyboard test timeout. The 1 that passed ("No provider found" for an unknown NPI) already held on the MAM-34 page. |

AC to test: AC-1 form and empty state (2); AC-2 `?npi=` exactly, not the single endpoint, table and NPI link (3); AC-3 `?medicaidId=` then `?name=`, all three together, several matches (3);
AC-4 empty, "12345", three malformed values, correction (6); AC-5 "No provider found" in a status area, replaced by results (2); AC-6 kept NPI, replaced by a later search, new tab empty, rejected NPI not kept (4);
AC-7 401, 403, 400 detail, 500 / 503 / network, retry (7); AC-8 Enter in each input, table headers (2). Playwright: empty + axe, NPI / Medicaid ID / name requests + axe, validation + axe, "No provider found" + axe, 503, keyboard only.

MAM-34 compatibility: the recorded MAM-34 tests (provider-lookup.test.tsx, provider-lookup.spec.ts) accept both GET /api/v1/providers/{npi} and GET /api/v1/providers?npi=, and keep passing.
Conflict (not edited, for the test-writer): three recorded MAM-87 tests stub only GET /api/v1/providers/{npi} for the search and therefore fail now, see the story report.
