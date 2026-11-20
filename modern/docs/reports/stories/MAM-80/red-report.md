# MAM-80 red report: Console provider detail, every field, status as text and code

Run on 2026-11-19 (fast mode). Before the story the detail page was the minimal page of MAM-87 (status and screen flag in a short table).
Hashes: `modern/docs/reports/stories/MAM-80.tests.sha256` (3 files), restore copies in `red-blobs.txt`.

| Level | Command | Tests | Result | Reason |
|---|---|---|---|---|
| Vitest | `vitest run tests/console/provider-detail.test.tsx` (PowerShell, lowercase drive) | 21 | 20 failed, 1 passed | 15 x "the page shows no Provider record table yet", no "Back to provider search" link (1), no "You are not allowed to see this provider" (1), no "The provider could not be loaded. Try again." (3). The 1 that passed is the 401 case (MAM-62's shared client already sends the user to sign in). |
| Playwright | `playwright test e2e/console-detail.spec.ts` | 10 | 10 failed | no h2 "Status: ..." banner (7), the Owner NPI and Risk tier rows of the new table missing (2), and the "Back to provider search" link missing (the keyboard step `tabTo` waits for the link until the 90 s test timeout) |

AC to test: AC-1 call, NPI heading, 13 rows with header cells, Not assigned / Not set (3); AC-2 organisation rows (1); AC-3 five statuses, banner above the table (6);
AC-4 Denied (1); AC-5 Suspended never Denied (1); AC-6 X and blank (2); AC-7 404 with link and no table, 401, 403, 500/503, network, address (7); Playwright: individual, organisation, five statuses, X, not found with keyboard, pending with keyboard.

Reading fixed by the tests: the banner is an h2 "Status: <words> (<code>)" (one only); the NPI in full is in an h2 "<NPI> <name>" (the h1 stays "Provider detail", other pages and recorded tests rely on it);
the compact summary table of MAM-87 (NPI, Name, Status, Screen flag, Revalidation due) stays above the new "Provider record" table so that the recorded MAM-87 detail tests keep passing.

## Test correction under Q-097 (UI polish B-iv, user decision 2026-11-19)

Cause: **the UI polish of 2026-11-19 (B-iv)**, under the Q-097 rule and the user's explicit permission to use it for any selector change. The key-value tables "Provider record", "Provider", "Application" and the portal home's "My provider record" are now USWDS summary lists (`<section aria-labelledby>`, a region named by its title, holding a `<dl>` of `div > dt + dd`); the label "Screen flag" is "Screening result"; the screening sources are shown by name (Licence board, OIG exclusion list (LEIE), SAM.gov debarment, SSA death records); and the status page has a step indicator whose step-2 segment reads "Validated" ("Validation failed" for a rejected application). Scoping and renaming only: no assertion was weakened or removed; production code unchanged. Test titles keep their wording unless said otherwise.

What changed in MAM-80:
- `tests/support/detail.ts`: `recordTable()` finds the region "Provider record" (the name is kept; it now returns the list) and `recordRows()` reads `[dt, dd]` of every row.
- `provider-detail.test.tsx`: the label "Screen flag" is "Screening result".
- `console-detail.spec.ts`: `valueOf` reads the `dd` of the row in the region "Provider record" (the page also shows the list "Provider", which has some of the same labels, so the region is named);
  the AC-2 "no Last name cell" assertion is the same assertion on the `dt` term.
- **Held assertion, decided by the user (2026-11-19: equivalent accessibility check for a summary list; Jira AC wording (MAM-68 AC-7, MAM-80) to be updated through the change loop):**
  the assertion that the record table has the header cells "Field" and "Value" (`provider-detail.test.tsx` AC-1 and `console-detail.spec.ts` line 37) is replaced by: the region named
  "Provider record" exists, holds a `dl`, every expected term (the 13 fields the test already checks) is a `dt` and each has a following `dd`. The field list checks are kept. The Vitest test
  title now reads "the region \"Provider record\" is a list in which every value is a dd paired with its dt term, and shows every field with its value and meaning".

Exact changes (`-` before, `+` after; the previous blob is the state at the start of this correction, line endings kept, a plain `diff` against it shows only these lines):

| File | Previous blob | New blob | Line endings | Changed lines |
|---|---|---|---|---|
| `tests/support/detail.ts` | `de90eebaae6609ae6515ff84d821864b436a5551` | `ca655e52b5d8f89882ba7c030186bef24ad29ade` | LF | 11 |
| `tests/console/provider-detail.test.tsx` | `ea08ca295c91058d511bf46a543fcbc38af74745` | `98f074b423fc4c031bd091883188915e2003e28f` | LF | 26 |
| `e2e/console-detail.spec.ts` | `0670015c9ad1691f8ec27c5c2fe228f1b6c50627` | `eefb8fb98fce7b5c0bfda6c4cc4e61f9cd684f96` | LF | 23 |

`tests/support/detail.ts` (LF)
```diff
@@ -55 +55 @@
-/** The table "Provider record": the table with that caption. */
+/** The list "Provider record": the region with that name (a key-value list since the UI polish, Q-097; the name recordTable is kept). */
@@ -57,5 +57 @@
-  return waitFor(() => {
-    const table = screen.queryAllByRole('table').find((candidate) => candidate.querySelector('caption')?.textContent === 'Provider record');
-    if (!table) throw new Error('the page shows no "Provider record" table yet');
-    return table;
-  });
+  return waitFor(() => screen.getByRole('region', { name: 'Provider record' }));
@@ -63,0 +60 @@
+/** The terms and values of the list: [dt, dd] of every row. */
@@ -65 +62 @@
-  return Array.from(table.querySelectorAll('tbody tr')).map((row) => [text(row.children[0]), text(row.children[1])] as [string, string]);
+  return Array.from(table.querySelectorAll('dl > div')).map((row) => [text(row.querySelector('dt')), text(row.querySelector('dd'))] as [string, string]);
```

`tests/console/provider-detail.test.tsx` (LF)
```diff
@@ -4 +4 @@
-import { screen, within } from '@testing-library/react';
+import { screen } from '@testing-library/react';
@@ -28 +28 @@
-  it('[MAM-80 AC-1] the table "Provider record" has header cells and every field with its value and meaning', async () => {
+  it('[MAM-80 AC-1] the region "Provider record" is a list in which every value is a dd paired with its dt term, and shows every field with its value and meaning', async () => {
@@ -32 +32,19 @@
-    expect(within(table).getAllByRole('columnheader').map((header) => text(header))).toEqual(['Field', 'Value']);
+    // user decision 2026-11-19 (Q-097): the equivalent accessibility check for a summary list, which has no header cells
+    expect(table.querySelector('dl')).not.toBeNull();
+    const terms = Array.from(table.querySelectorAll('dt'));
+    expect(terms.map((term) => text(term))).toEqual([
+      'NPI',
+      'Medicaid ID',
+      'Provider type',
+      'Taxonomy',
+      'Enrolment status',
+      'Screening result',
+      'Risk tier',
+      'Effective date',
+      'Revalidation due',
+      'Last name',
+      'First name',
+      'Licence number',
+      'Licence state',
+    ]);
+    for (const term of terms) expect(term.nextElementSibling?.tagName).toBe('DD');
@@ -39 +57 @@
-      ['Screen flag', 'Pass (P)'],
+      ['Screening result', 'Pass (P)'],
```

`e2e/console-detail.spec.ts` (LF)
```diff
@@ -29 +29,2 @@
-const valueOf = (page: Page, label: string) => page.getByRole('row').filter({ has: page.getByRole('cell', { name: label, exact: true }) }).getByRole('cell').nth(1);
+/** The value of a row of the list "Provider record" (UI polish, Q-097): <div><dt>label</dt><dd>value</dd></div>. The page also shows the list "Provider", so the region is named. */
+const valueOf = (page: Page, label: string) => page.getByRole('region', { name: 'Provider record', exact: true }).locator('dl > div').filter({ has: page.getByText(label, { exact: true }) }).locator('dd');
@@ -36,4 +37,7 @@
-  const record = page.getByRole('table', { name: 'Provider record' });
-  await expect(record.getByRole('columnheader')).toHaveText(['Field', 'Value']);
-  await expect(record.getByRole('row').filter({ has: page.getByRole('cell', { name: 'Licence number', exact: true }) }).getByRole('cell').nth(1)).toHaveText('MD12345');
-  await expect(record.getByRole('row').filter({ has: page.getByRole('cell', { name: 'Revalidation due', exact: true }) }).getByRole('cell').nth(1)).toHaveText('03/02/2031');
+  const record = page.getByRole('region', { name: 'Provider record', exact: true });
+  // user decision 2026-11-19 (Q-097): the equivalent accessibility check for a summary list, which has no header cells: every value is a dd paired with its dt term
+  await expect(record.locator('dl')).toHaveCount(1);
+  await expect(record.locator('dt')).toHaveText(['NPI', 'Medicaid ID', 'Provider type', 'Taxonomy', 'Enrolment status', 'Screening result', 'Risk tier', 'Effective date', 'Revalidation due', 'Last name', 'First name', 'Licence number', 'Licence state']);
+  await expect(record.locator('dt + dd')).toHaveCount(13);
+  await expect(valueOf(page, 'Licence number')).toHaveText('MD12345');
+  await expect(valueOf(page, 'Revalidation due')).toHaveText('03/02/2031');
@@ -47,3 +51,3 @@
-  const record = page.getByRole('table', { name: 'Provider record' });
-  await expect(record.getByRole('row').filter({ has: page.getByRole('cell', { name: 'Owner NPI', exact: true }) }).getByRole('cell').nth(1)).toHaveText('1990000111');
-  await expect(record.getByRole('cell', { name: 'Last name', exact: true })).toHaveCount(0);
+  const record = page.getByRole('region', { name: 'Provider record', exact: true });
+  await expect(valueOf(page, 'Owner NPI')).toHaveText('1990000111');
+  await expect(record.locator('dt').filter({ hasText: /^Last name$/ })).toHaveCount(0);
@@ -59,2 +63 @@
-    const record = page.getByRole('table', { name: 'Provider record' });
-    await expect(record.getByRole('row').filter({ has: page.getByRole('cell', { name: 'Enrolment status', exact: true }) }).getByRole('cell').nth(1)).toHaveText(label);
+    await expect(valueOf(page, 'Enrolment status')).toHaveText(label);
```

Runs (2026-11-19; the host was short of memory, so one worker): before the correction Vitest had 77 failures in 11 files (570 of 647 passed, as reported by the lead and re-run here)
and Playwright 46 failures in 8 specs (106 of 152, as reported by the lead). After it: the full Vitest suite (`--maxWorkers=1 --testTimeout=60000 --hookTimeout=60000`, PowerShell, lowercase path)
**647 of 647 pass** in 29 files; the full default Playwright suite (16 specs in 4 batches, `--workers=1`, journeys excluded) **152 of 152 pass**; `npx tsc -b` and `npx eslint --max-warnings 0`
on the changed files: no findings. The journey J3 (MAM-74) was not run (no journeys, no seeding).
**Hashes:** re-recorded with `test_protection.py record <KEY>` with exactly the files of the story's `.tests.sha256`; `check` and `sha256sum -c` are OK.

## Test correction under Q-097 (plain dates + Reference, user decision 2026-11-19 19:54)

Cause: **the user decisions of 2026-11-19 19:54 that the UI now implements**, under the Q-097 C standing rule (superseded tests; scoping and wording only, no assertion weakened or removed, production code unchanged). **A2, plain dates:** every displayed date reads "19 Nov 2026", the day without a leading zero (`formatDate` in `web-ui/src/shared/format/format.ts`); typed date inputs stay MM/DD/YYYY, so no value a test types into an input was changed. **A1, application status page:** the application ID is the single text "Reference: <id>" under the h1, the "Application" row of the summary list is gone (the list keeps its title "Application", so region lookups still work), and the paragraph "Your application was accepted. ..." is removed.

**Reviewer MUST-FIX of the B-iv round (code-reviewer, 2026-11-19), folded in here, additive only:** the "no table" guards were vacuous after the record became a `dl` in a named region: `provider-detail.test.tsx` (404, 401 and 403 of AC-7) now also asserts `queryByRole('region', { name: 'Provider record' })` and `{ name: 'Provider' }` are null; `console-detail.spec.ts` (the unknown provider page) now also asserts both regions have count 0 (`getByRole('region', { name, exact: true })`). The table checks stay.

Exact changes (`-` before, `+` after; the previous blob is the state at the start of this correction, line endings kept, a plain `diff` against it shows only these lines):

| File | Previous blob | New blob | Line endings | Changed lines (removed / added) |
|---|---|---|---|---|
| `e2e/console-detail.spec.ts` | `eefb8fb98fce7b5c0bfda6c4cc4e61f9cd684f96` | `ea30e927c6e66f41118cda75ad2018209f05ce38` | LF | 1 / 3 |
| `tests/console/provider-detail.test.tsx` | `98f074b423fc4c031bd091883188915e2003e28f` | `64d41bb815a666de2145f6073d0ba2a4a061284b` | LF | 2 / 8 |

`e2e/console-detail.spec.ts` (LF)
```diff
@@ -43 +43 @@ test('[MAM-80 AC-1][MAM-80 AC-8] an individual record: NPI in the heading, the b
-  await expect(valueOf(page, 'Revalidation due')).toHaveText('03/02/2031');
+  await expect(valueOf(page, 'Revalidation due')).toHaveText('2 Mar 2031');
@@ -79,0 +80,2 @@ test('[MAM-80 AC-7][MAM-80 AC-8] an unknown provider: "No provider found" and th
+  await expect(page.getByRole('region', { name: 'Provider record', exact: true })).toHaveCount(0);
+  await expect(page.getByRole('region', { name: 'Provider', exact: true })).toHaveCount(0);
```

`tests/console/provider-detail.test.tsx` (LF)
```diff
@@ -59,2 +59,2 @@ describe('[MAM-80 AC-1] an individual record is shown in full, with meanings (jo
-      ['Effective date', '03/02/2026'],
-      ['Revalidation due', '03/02/2031'],
+      ['Effective date', '2 Mar 2026'],
+      ['Revalidation due', '2 Mar 2031'],
@@ -166,0 +167,2 @@ describe('[MAM-80 AC-7] unknown providers and failures are told apart', () => {
+    expect(screen.queryByRole('region', { name: 'Provider record' })).toBeNull();
+    expect(screen.queryByRole('region', { name: 'Provider' })).toBeNull();
@@ -174,0 +177,2 @@ describe('[MAM-80 AC-7] unknown providers and failures are told apart', () => {
+    expect(screen.queryByRole('region', { name: 'Provider record' })).toBeNull();
+    expect(screen.queryByRole('region', { name: 'Provider' })).toBeNull();
@@ -182,0 +187,2 @@ describe('[MAM-80 AC-7] unknown providers and failures are told apart', () => {
+    expect(screen.queryByRole('region', { name: 'Provider record' })).toBeNull();
+    expect(screen.queryByRole('region', { name: 'Provider' })).toBeNull();
```

Test titles that named the old format were corrected for honesty (AC tags kept); typed date inputs were not touched.

**Runs (2026-11-19, one worker because the host was short of memory):** before the correction the full Vitest suite (`--maxWorkers=1 --testTimeout=60000 --hookTimeout=60000`, PowerShell, lowercase path) had **36 failures in 15 files (611 of 647 pass)**: 35 are exactly the lines of the A1/A2 list; the 36th is a timing failure in an unedited file, see the MAM-64 note. After the correction: **647 of 647 pass in 29 files**. `npx tsc -b` (it includes `tests` and `e2e`) and `eslint --max-warnings 0` on the 24 changed files: no findings.
The full default Playwright suite (16 specs, journeys excluded, `--workers=1`, `NODE_OPTIONS=--dns-result-order=ipv4first`, one run): **152 of 152 pass** (8.4 min). The Playwright suite was not run before the correction (no time; the UI agent's static list was used), so there is no before count for it.

**Hashes:** re-recorded with `test_protection.py record MAM-80` with exactly the files of the story's `.tests.sha256`; `check` and `sha256sum -c` are OK.
