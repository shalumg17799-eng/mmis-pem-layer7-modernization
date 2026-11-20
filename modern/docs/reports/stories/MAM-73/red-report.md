# MAM-73 red report: Public directory page (no sign-in)

Run on 2026-11-19 (fast mode), before any production code of the story existed (`/directory` showed "Page not found").
Hashes: `modern/docs/reports/stories/MAM-73.tests.sha256` (3 files), restore copies in `red-blobs.txt`.

| Level | Command | Tests | Result | Reason |
|---|---|---|---|---|
| Vitest | `npx vitest run tests/public/directory.test.tsx --pool=vmThreads` | 25 | 25 failed | 25 x `Unable to find role="heading" and name "Find a Medicaid provider"` (no such page) |
| Playwright | `npx playwright test e2e/public-directory.spec.ts` | 7 | 7 failed | the h1 "Find a Medicaid provider" is not visible (no Keycloak sign-in is used) |

AC to test: AC-1 no redirect, the form, no Authorization header (also for a signed-in user), no request on open (4); AC-2 `?name=` calls and rows (3);
AC-3 providerType=FA / none / both (2); AC-4 displayName as returned, long name in full (2); AC-5 only what the API returns (2); AC-6 four columns,
no Medicaid ID or risk tier (2); AC-7 no match, 45 matches with paging, 400 detail, 500/503/network, recovery (8); AC-8 structure and Enter (2);
Playwright: AC-1, AC-2 with the Authorization check, axe on empty, results, no-match and failure pages, keyboard-only search.

Defect in a recorded test (found in green, not edited): `[MAM-73 AC-8] one h1, the landmarks banner and main, ...` uses
`screen.getByRole('banner')`. Testing Library counts the USWDS GovBanner `<header>` as a banner too, so it finds two. Proposed fix:
`getAllByRole('banner').length >= 1` or `container.querySelector('header.usa-header')`. The page itself is right (Playwright `expectShellStructure` passes).

## Test correction under Q-097 (user decision 2026-11-19)

Cause: **ambiguous test** (the finding above). Q-097 option A, item 2.

- **File:** `modern/web-ui/tests/public/directory.test.tsx`, test "[MAM-73 AC-8] one h1, the landmarks banner and main, a
  "Skip to main content" link, labelled inputs and a table with header cells" (line 277). CRLF file; line endings kept.
- **Fault (in the test):** `screen.getByRole('banner')` found 2 elements in jsdom: the site `<header class="usa-header">` and the
  `<header class="usa-banner__header">` inside the USWDS government banner (`section.usa-banner`). The browser has one site banner.
- **Fix (not weakened):** the test still asserts exactly one site banner. The government banner's inner header is excluded; the
  other assertions of the test are untouched.
```diff
-    expect(screen.getByRole('banner')).toBeInTheDocument();
+    // jsdom also gives the <header> inside the USWDS government banner (section.usa-banner) the role banner; the page has one site banner
+    expect(screen.getAllByRole('banner').filter((banner) => !banner.closest('.usa-banner'))).toHaveLength(1);
```
- **Blobs:** previous `f595beb33eefd14d4798741f485b92490024a9f5`, new `5a7583de6644bf5562e48dec7acbf46b1c0a661c`. A plain `diff`
  (no `--strip-trailing-cr`) against the previous blob shows only these lines. The other two files of the story are unchanged
  (`public-directory.spec.ts`, `tests/support/directory.ts`) and are re-recorded with the same content. Production code unchanged.
- **Runs (2026-11-19):** Vitest `tests/public/directory.test.tsx` 25/25 (the corrected test passes; before it failed with "Found
  multiple elements"). Playwright `e2e/public-directory.spec.ts` passes in the full default run (see below). Full Vitest
  (`--maxWorkers=2 --testTimeout=30000 --hookTimeout=30000`): 553 of 555, no MAM-73 failure. Full Playwright (all 15 default specs in two
  batches, `--workers=2`): 143 of 146, no MAM-73 failure. `npx eslint ... --max-warnings 0` and `npx tsc -b`: no findings.
- **Hashes:** re-recorded with `test_protection.py record MAM-73` (same 3 files); `check MAM-73` printed OK right after, and `sha256sum -c MAM-73.tests.sha256` is OK for all 3 files
  (a later `check` also lists the unrecorded test files of another story, `admin-risk`, as NEW; that is not a MAM-73 change).
