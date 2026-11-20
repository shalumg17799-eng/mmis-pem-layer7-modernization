# MAM-85 red report: Console work queues

Run on 2026-11-19 (fast mode), before any production code of the story existed (`/console/queues` was a placeholder).
Hashes: `modern/docs/reports/stories/MAM-85.tests.sha256` (3 files), restore copies in `red-blobs.txt`.

| Level | Command | Tests | Result | Reason |
|---|---|---|---|---|
| Vitest | `npx vitest run tests/console/queues.test.tsx --pool=vmThreads` | 30 | 28 failed, 2 passed | 28 x `Unable to find role="heading"`: the placeholder has no h2 sections. The 2 that passed are the AC-7 guards (admin1 and provider1 get Access denied and no queue request), already true through MAM-62's role guard. |
| Playwright | `npx playwright test e2e/console-queues.spec.ts` | 5 | 5 failed | section "Pending approval", the empty-state messages, "The list could not be loaded", the Review link and the NPI link are not on the page. |

AC to test: AC-1 calls + Pending row + Review link (4 tests); AC-2 calls, failing source "OIGLEIE (E203 owner excluded)", NPI link,
screening failure (3); AC-3 two calls, once each, "In screening" (3); AC-4 call + rows (2); AC-5 counts, four empty messages, paging (7);
AC-6 5xx, 404, network, Retry, a failing Pending list (6); AC-7 four roles (4); AC-8 structure + Enter on Review (1) and Playwright axe x3 + keyboard x2.

Reading fixed by the tests (decisions to confirm): the Revalidation section asks for DUE and IN_SCREENING with the same page and merges
the answers by due date (the heading count is the sum of the two totals); any failure of an endpoint, including a missing one (404),
is "The list could not be loaded" with Retry; the Review link is named exactly "Review" (the row gives the context).

## Test correction under Q-097 (UI polish B-iv, user decision 2026-11-19)

Cause: **the UI polish of 2026-11-19 (B-iv)**, under the Q-097 rule and the user's explicit permission to use it for any selector change. The key-value tables "Provider record", "Provider", "Application" and the portal home's "My provider record" are now USWDS summary lists (`<section aria-labelledby>`, a region named by its title, holding a `<dl>` of `div > dt + dd`); the label "Screen flag" is "Screening result"; the screening sources are shown by name (Licence board, OIG exclusion list (LEIE), SAM.gov debarment, SSA death records); and the status page has a step indicator whose step-2 segment reads "Validated" ("Validation failed" for a rejected application). Scoping and renaming only: no assertion was weakened or removed; production code unchanged. Test titles keep their wording unless said otherwise.

What changed in MAM-85:
- `queues.test.tsx`: the waits for the failing-source section `/OIGLEIE/` are `/OIG exclusion list/` (5 places) and the failing-source text "OIGLEIE (E203 owner excluded)" is
  "OIG exclusion list (LEIE): E203 owner excluded".
- `console-queues.spec.ts` (AC-2): the same failing-source text.
- **Found by my run, not in the list from the UI agent (text only):** the column header "Screened" of the "Pending approval" queue is "Screening result" (`queues.test.tsx` AC-1); the
  expected header list keeps its three entries.

Exact changes (`-` before, `+` after; the previous blob is the state at the start of this correction, line endings kept, a plain `diff` against it shows only these lines):

| File | Previous blob | New blob | Line endings | Changed lines |
|---|---|---|---|---|
| `tests/console/queues.test.tsx` | `afdaa867f51e3524350fbbb655454f3f138e7089` | `f940b3aa915ea9fda01e06e21df943cff921ba90` | LF | 16 |
| `e2e/console-queues.spec.ts` | `0652ed2d43c82d25a124e375035213c9fb4d0d5d` | `260a6db410717e985179c19c6690843c401bf6cf` | CRLF | 2 |

`tests/console/queues.test.tsx` (LF)
```diff
@@ -51 +51 @@
-    expect(headers.slice(0, 3)).toEqual(['NPI', 'Name', 'Screened']);
+    expect(headers.slice(0, 3)).toEqual(['NPI', 'Name', 'Screening result']);
@@ -76 +76 @@
-    await findQueueSection('Failed screening', /OIGLEIE/);
+    await findQueueSection('Failed screening', /OIG exclusion list/);
@@ -84,3 +84,3 @@
-    const section = await findQueueSection('Failed screening', /OIGLEIE/);
-
-    expect(rowsOf(section)[0]).toEqual([CLINIC_NPI, 'EXCLUDED CLINIC LLC', 'OIGLEIE (E203 owner excluded)']);
+    const section = await findQueueSection('Failed screening', /OIG exclusion list/);
+
+    expect(rowsOf(section)[0]).toEqual([CLINIC_NPI, 'EXCLUDED CLINIC LLC', 'OIG exclusion list (LEIE): E203 owner excluded']);
@@ -211 +211 @@
-    expect(text(await findQueueSection('Failed screening', /OIGLEIE/))).toContain('EXCLUDED CLINIC LLC');
+    expect(text(await findQueueSection('Failed screening', /OIG exclusion list/))).toContain('EXCLUDED CLINIC LLC');
@@ -264 +264 @@
-    expect(text(await findQueueSection('Failed screening', /OIGLEIE/))).toContain('EXCLUDED CLINIC LLC');
+    expect(text(await findQueueSection('Failed screening', /OIG exclusion list/))).toContain('EXCLUDED CLINIC LLC');
@@ -291 +291 @@
-    await findQueueSection('Failed screening', /OIGLEIE/);
+    await findQueueSection('Failed screening', /OIG exclusion list/);
```

`e2e/console-queues.spec.ts` (CRLF)
```diff
@@ -58 +58 @@
-  await expect(section(page, /^Failed screening \(1\)/).getByRole('row').filter({ hasText: 'OIGLEIE (E203 owner excluded)' })).toHaveCount(1);
+  await expect(section(page, /^Failed screening \(1\)/).getByRole('row').filter({ hasText: 'OIG exclusion list (LEIE): E203 owner excluded' })).toHaveCount(1);
```

Runs (2026-11-19; the host was short of memory, so one worker): before the correction Vitest had 77 failures in 11 files (570 of 647 passed, as reported by the lead and re-run here)
and Playwright 46 failures in 8 specs (106 of 152, as reported by the lead). After it: the full Vitest suite (`--maxWorkers=1 --testTimeout=60000 --hookTimeout=60000`, PowerShell, lowercase path)
**647 of 647 pass** in 29 files; the full default Playwright suite (16 specs in 4 batches, `--workers=1`, journeys excluded) **152 of 152 pass**; `npx tsc -b` and `npx eslint --max-warnings 0`
on the changed files: no findings. The journey J3 (MAM-74) was not run (no journeys, no seeding).
**Hashes:** re-recorded with `test_protection.py record <KEY>` with exactly the files of the story's `.tests.sha256`; `check` and `sha256sum -c` are OK.

## Test correction under Q-097 (plain dates + Reference, user decision 2026-11-19 19:54)

Cause: **the user decisions of 2026-11-19 19:54 that the UI now implements**, under the Q-097 C standing rule (superseded tests; scoping and wording only, no assertion weakened or removed, production code unchanged). **A2, plain dates:** every displayed date reads "19 Nov 2026", the day without a leading zero (`formatDate` in `web-ui/src/shared/format/format.ts`); typed date inputs stay MM/DD/YYYY, so no value a test types into an input was changed. **A1, application status page:** the application ID is the single text "Reference: <id>" under the h1, the "Application" row of the summary list is gone (the list keeps its title "Application", so region lookups still work), and the paragraph "Your application was accepted. ..." is removed.

Exact changes (`-` before, `+` after; the previous blob is the state at the start of this correction, line endings kept, a plain `diff` against it shows only these lines):

| File | Previous blob | New blob | Line endings | Changed lines (removed / added) |
|---|---|---|---|---|
| `e2e/console-queues.spec.ts` | `260a6db410717e985179c19c6690843c401bf6cf` | `b55e119a0f75018122d528b244789db621e010f3` | CRLF | 1 / 1 |
| `tests/console/queues.test.tsx` | `f940b3aa915ea9fda01e06e21df943cff921ba90` | `89ef01a1598688131ee8fb9355eb08c72b02615d` | LF | 7 / 7 |

`e2e/console-queues.spec.ts` (CRLF)
```diff
@@ -59 +59 @@ test('[MAM-85 AC-8] all four sections filled: Pending approval, Failed screening
-  await expect(section(page, /^Revalidation due \(1\)/).getByRole('row').filter({ hasText: '02/28/2026' })).toHaveCount(1);
+  await expect(section(page, /^Revalidation due \(1\)/).getByRole('row').filter({ hasText: '28 Feb 2026' })).toHaveCount(1);
```

`tests/console/queues.test.tsx` (LF)
```diff
@@ -112 +112 @@ describe('[MAM-85 AC-3] Revalidation due lists open cases once each (journey J6)
-  it('[MAM-85 AC-3] the case of 1003000222 is listed once with NPI, Due 02/28/2026 and Case "Due"', async () => {
+  it('[MAM-85 AC-3] the case of 1003000222 is listed once with NPI, Due 28 Feb 2026 and Case "Due"', async () => {
@@ -117 +117 @@ describe('[MAM-85 AC-3] Revalidation due lists open cases once each (journey J6)
-    expect(rowsOf(section)).toEqual([[DUE_NPI, '02/28/2026', 'Due']]);
+    expect(rowsOf(section)).toEqual([[DUE_NPI, '28 Feb 2026', 'Due']]);
@@ -127 +127 @@ describe('[MAM-85 AC-3] Revalidation due lists open cases once each (journey J6)
-    expect(rowsOf(section)).toEqual([[DUE_NPI, '02/28/2026', 'In screening']]);
+    expect(rowsOf(section)).toEqual([[DUE_NPI, '28 Feb 2026', 'In screening']]);
@@ -139 +139 @@ describe('[MAM-85 AC-4] Rejected applications are listed for follow-up', () => {
-  it('[MAM-85 AC-4] each rejection shows NPI (or "(no NPI)"), Submitted as MM/DD/YYYY, the error codes and the source', async () => {
+  it('[MAM-85 AC-4] each rejection shows NPI (or "(no NPI)"), Submitted as a plain date (1 Mar 2026), the error codes and the source', async () => {
@@ -156,2 +156,2 @@ describe('[MAM-85 AC-4] Rejected applications are listed for follow-up', () => {
-      ['1003000111', '03/01/2026', 'E104', 'Portal'],
-      ['(no NPI)', '03/02/2026', 'E101', 'Legacy file'],
+      ['1003000111', '1 Mar 2026', 'E104', 'Portal'],
+      ['(no NPI)', '2 Mar 2026', 'E101', 'Legacy file'],
@@ -257 +257 @@ describe('[MAM-85 AC-6] one failing queue does not hide the others', () => {
-    expect(rowsOf(section)).toEqual([[DUE_NPI, '02/28/2026', 'Due']]);
+    expect(rowsOf(section)).toEqual([[DUE_NPI, '28 Feb 2026', 'Due']]);
```

Test titles that named the old format were corrected for honesty (AC tags kept); typed date inputs were not touched.

**Runs (2026-11-19, one worker because the host was short of memory):** before the correction the full Vitest suite (`--maxWorkers=1 --testTimeout=60000 --hookTimeout=60000`, PowerShell, lowercase path) had **36 failures in 15 files (611 of 647 pass)**: 35 are exactly the lines of the A1/A2 list; the 36th is a timing failure in an unedited file, see the MAM-64 note. After the correction: **647 of 647 pass in 29 files**. `npx tsc -b` (it includes `tests` and `e2e`) and `eslint --max-warnings 0` on the 24 changed files: no findings.
The full default Playwright suite (16 specs, journeys excluded, `--workers=1`, `NODE_OPTIONS=--dns-result-order=ipv4first`, one run): **152 of 152 pass** (8.4 min). The Playwright suite was not run before the correction (no time; the UI agent's static list was used), so there is no before count for it.

**Hashes:** re-recorded with `test_protection.py record MAM-85` with exactly the files of the story's `.tests.sha256`; `check` and `sha256sum -c` are OK.

Record correction (lead, 2026-11-19 20:58, code-reviewer round 2): line(s) 84, 87 labelled the file LF; it is CRLF (byte check by the reviewer). Only the label changed; the test file and its hash are unchanged.
