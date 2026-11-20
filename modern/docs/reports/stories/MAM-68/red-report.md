# MAM-68 red report: Portal, see my provider record

Run on 2026-11-19 (fast mode). Before the story the route showed the minimal page of MAM-64 (only "No provider record exists yet").
Hashes: `modern/docs/reports/stories/MAM-68.tests.sha256` (2 files), restore copies in `red-blobs.txt`.

| Level | Command | Tests | Result | Reason |
|---|---|---|---|---|
| Vitest | `npx vitest run tests/portal/provider-record.test.tsx --pool=vmThreads` | 26 | 24 failed, 2 passed | 21 x "the page shows no provider record table yet", no access-denied text (1), no "A record is created when an application is accepted" (1), no My enrolment link (1). The 2 that passed (no table and no error code on a 404; a 500 is not explained as "no record") were already true on the minimal page. |
| Playwright | `npx playwright test e2e/portal-record.spec.ts` | 4 | 4 failed | the table rows (Risk tier, Legal name), the explanation text and the access-denied text are missing |

AC to test: AC-1 call, 13 rows with header cells, "Active (A)" in main (3); AC-2 GR and FA (2); AC-3 five statuses, three flags, four tiers incl. "Not assigned",
"Not set", Suspended is not Denied (14); AC-4 one provider request only, no input, 403 (2); AC-5 explanation, two links, no table or code, not for a 500 (4);
AC-6 2070 and 2026 dates (1); Playwright: individual, organisation, no-record with keyboard, 403, each with axe.

## Test correction under Q-097 (UI polish B-iv, user decision 2026-11-19)

Cause: **the UI polish of 2026-11-19 (B-iv)**, under the Q-097 rule and the user's explicit permission to use it for any selector change. The key-value tables "Provider record", "Provider", "Application" and the portal home's "My provider record" are now USWDS summary lists (`<section aria-labelledby>`, a region named by its title, holding a `<dl>` of `div > dt + dd`); the label "Screen flag" is "Screening result"; the screening sources are shown by name (Licence board, OIG exclusion list (LEIE), SAM.gov debarment, SSA death records); and the status page has a step indicator whose step-2 segment reads "Validated" ("Validation failed" for a rejected application). Scoping and renaming only: no assertion was weakened or removed; production code unchanged. Test titles keep their wording unless said otherwise.

What changed in MAM-68:
- `provider-record.test.tsx`: the local `recordTable()` finds the region "Provider record" in main and `rows()` reads `[dt, dd]` of every `div`; the label "Screen flag" is "Screening result"
  (two places).
- `portal-record.spec.ts`: `valueOf` reads the `dd` after the `dt`; "Screen flag" is "Screening result"; the AC-2 assertion that no "Last name" **cell** exists (it would now be vacuous)
  is the same assertion on the **dt** term.
- **Held assertion, decided by the user (2026-11-19: equivalent accessibility check for a summary list; Jira AC wording (MAM-68 AC-7, MAM-80) to be updated through the change loop):**
  the assertion that the record table has the header cells "Field" and "Value" (`provider-record.test.tsx` AC-1 and `portal-record.spec.ts` line 36) is replaced by: the region named
  "Provider record" exists, holds a `dl`, every expected term (the 13 fields the test already checks) is a `dt` and each has a following `dd`. The field list checks are kept. The Vitest test
  title now reads "the list in which every value is a dd paired with its dt term shows every field with its value and meaning".

Exact changes (`-` before, `+` after; the previous blob is the state at the start of this correction, line endings kept, a plain `diff` against it shows only these lines):

| File | Previous blob | New blob | Line endings | Changed lines |
|---|---|---|---|---|
| `tests/portal/provider-record.test.tsx` | `ce7fa0f5d109baf1b36394475791332cadfec2cd` | `484a7e85c44985f2b1ec697b173e5d99b06df480` | LF | 36 |
| `e2e/portal-record.spec.ts` | `b9fece69cfe101a96f8230f4b156faad458626db` | `c2d201fb47bbb9fd5acc2f897e8d8bbe8981aa09` | LF | 13 |

`tests/portal/provider-record.test.tsx` (LF)
```diff
@@ -42 +42 @@
-/** The record table: the table with the caption "Provider record" (field in the first cell, value in the second). */
+/** The record list: the region named "Provider record" (term in the dt, value in the dd; a key-value list since the UI polish, Q-097). */
@@ -44,5 +44 @@
-  return waitFor(() => {
-    const table = within(screen.getByRole('main')).queryAllByRole('table').find((candidate) => candidate.querySelector('caption')?.textContent === 'Provider record');
-    if (!table) throw new Error('the page shows no provider record table yet');
-    return table;
-  });
+  return waitFor(() => within(screen.getByRole('main')).getByRole('region', { name: 'Provider record' }));
@@ -52 +48 @@
-  return Array.from(table.querySelectorAll('tbody tr')).map((row) => [text(row.children[0]), text(row.children[1])] as [string, string]);
+  return Array.from(table.querySelectorAll('dl > div')).map((row) => [text(row.querySelector('dt')), text(row.querySelector('dd'))] as [string, string]);
@@ -63 +59 @@
-  it('[MAM-68 AC-1] the table with header cells shows every field with its value and meaning', async () => {
+  it('[MAM-68 AC-1] the list in which every value is a dd paired with its dt term shows every field with its value and meaning', async () => {
@@ -67 +63,19 @@
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
@@ -74 +88 @@
-      ['Screen flag', 'Pass (P)'],
+      ['Screening result', 'Pass (P)'],
@@ -136 +150 @@
-    expect(fieldValue('Screen flag')).toBe(label);
+    expect(fieldValue('Screening result')).toBe(label);
```

`e2e/portal-record.spec.ts` (LF)
```diff
@@ -23 +23,2 @@
-const valueOf = (page: Page, label: string) => page.getByRole('row').filter({ has: page.getByRole('cell', { name: label, exact: true }) }).getByRole('cell').nth(1);
+/** The value of a row of a key-value list (UI polish, Q-097): <div><dt>label</dt><dd>value</dd></div>. */
+const valueOf = (page: Page, label: string) => page.locator('dl > div').filter({ has: page.getByText(label, { exact: true }) }).locator('dd');
@@ -30 +31 @@
-  await expect(valueOf(page, 'Screen flag')).toHaveText('Pass (P)');
+  await expect(valueOf(page, 'Screening result')).toHaveText('Pass (P)');
@@ -36 +37,5 @@
-  await expect(page.getByRole('columnheader')).toHaveText(['Field', 'Value']);
+  // user decision 2026-11-19 (Q-097): the equivalent accessibility check for a summary list, which has no header cells: every value is a dd paired with its dt term
+  const record = page.getByRole('region', { name: 'Provider record', exact: true });
+  await expect(record.locator('dl')).toHaveCount(1);
+  await expect(record.locator('dt')).toHaveText(['NPI', 'Medicaid ID', 'Provider type', 'Taxonomy', 'Enrolment status', 'Screening result', 'Risk tier', 'Effective date', 'Revalidation due', 'Last name', 'First name', 'Licence number', 'Licence state']);
+  await expect(record.locator('dt + dd')).toHaveCount(13);
@@ -48 +53 @@
-  await expect(page.getByRole('cell', { name: 'Last name', exact: true })).toHaveCount(0);
+  await expect(page.locator('dt').filter({ hasText: /^Last name$/ })).toHaveCount(0);
```

Runs (2026-11-19; the host was short of memory, so one worker): before the correction Vitest had 77 failures in 11 files (570 of 647 passed, as reported by the lead and re-run here)
and Playwright 46 failures in 8 specs (106 of 152, as reported by the lead). After it: the full Vitest suite (`--maxWorkers=1 --testTimeout=60000 --hookTimeout=60000`, PowerShell, lowercase path)
**647 of 647 pass** in 29 files; the full default Playwright suite (16 specs in 4 batches, `--workers=1`, journeys excluded) **152 of 152 pass**; `npx tsc -b` and `npx eslint --max-warnings 0`
on the changed files: no findings. The journey J3 (MAM-74) was not run (no journeys, no seeding).
**Hashes:** re-recorded with `test_protection.py record <KEY>` with exactly the files of the story's `.tests.sha256`; `check` and `sha256sum -c` are OK.

## Test correction under Q-097 (plain dates + Reference, user decision 2026-11-19 19:54)

Cause: **the user decisions of 2026-11-19 19:54 that the UI now implements**, under the Q-097 C standing rule (superseded tests; scoping and wording only, no assertion weakened or removed, production code unchanged). **A2, plain dates:** every displayed date reads "19 Nov 2026", the day without a leading zero (`formatDate` in `web-ui/src/shared/format/format.ts`); typed date inputs stay MM/DD/YYYY, so no value a test types into an input was changed. **A1, application status page:** the application ID is the single text "Reference: <id>" under the h1, the "Application" row of the summary list is gone (the list keeps its title "Application", so region lookups still work), and the paragraph "Your application was accepted. ..." is removed.

**Reviewer MUST-FIX of the B-iv round (code-reviewer, 2026-11-19), folded in here, additive only:** the "no table" guards were vacuous after the record became a `dl` in a named region: `provider-record.test.tsx` (AC-5 "it shows no table and no error code") now also asserts `queryByRole('region', { name: 'Provider record' })` is null; `portal-record.spec.ts` (the no-record page and the 403 page) now also asserts `getByRole('region', { name: 'Provider record', exact: true })` has count 0. The table checks stay.

Exact changes (`-` before, `+` after; the previous blob is the state at the start of this correction, line endings kept, a plain `diff` against it shows only these lines):

| File | Previous blob | New blob | Line endings | Changed lines (removed / added) |
|---|---|---|---|---|
| `e2e/portal-record.spec.ts` | `c2d201fb47bbb9fd5acc2f897e8d8bbe8981aa09` | `3eccd7d8517389cfb9ff83a3438d791bf94e8e79` | LF | 2 / 4 |
| `tests/portal/provider-record.test.tsx` | `484a7e85c44985f2b1ec697b173e5d99b06df480` | `8763688c60648aede29f44020e22673a9f834ff3` | LF | 5 / 6 |

`e2e/portal-record.spec.ts` (LF)
```diff
@@ -33,2 +33,2 @@ test('[MAM-68 AC-1][MAM-68 AC-7] the individual record shows every field and has
-  await expect(valueOf(page, 'Effective date')).toHaveText('03/02/2026');
-  await expect(valueOf(page, 'Revalidation due')).toHaveText('03/02/2031');
+  await expect(valueOf(page, 'Effective date')).toHaveText('2 Mar 2026');
+  await expect(valueOf(page, 'Revalidation due')).toHaveText('2 Mar 2031');
@@ -63,0 +64 @@ test('[MAM-68 AC-5][MAM-68 AC-7] the no-record page explains why, has no table a
+  await expect(page.getByRole('region', { name: 'Provider record', exact: true })).toHaveCount(0);
@@ -76,0 +78 @@ test('[MAM-68 AC-4] after a 403 the page shows an access-denied message and no p
+  await expect(page.getByRole('region', { name: 'Provider record', exact: true })).toHaveCount(0);
```

`tests/portal/provider-record.test.tsx` (LF)
```diff
@@ -90,2 +90,2 @@ describe('[MAM-68 AC-1] the individual record is shown in full (journey J1)', ()
-      ['Effective date', '03/02/2026'],
-      ['Revalidation due', '03/02/2031'],
+      ['Effective date', '2 Mar 2026'],
+      ['Revalidation due', '2 Mar 2031'],
@@ -227,0 +228 @@ describe('[MAM-68 AC-5] no record yet is explained', () => {
+    expect(screen.queryByRole('region', { name: 'Provider record' })).toBeNull();
@@ -240 +241 @@ describe('[MAM-68 AC-6] dates keep four-digit years', () => {
-  it('[MAM-68 AC-6] a revalidation due date of 2070-03-02 reads 03/02/2070 and an effective date of 2026-03-02 reads 03/02/2026', async () => {
+  it('[MAM-68 AC-6] a revalidation due date of 2070-03-02 reads 2 Mar 2070 and an effective date of 2026-03-02 reads 2 Mar 2026', async () => {
@@ -244,2 +245,2 @@ describe('[MAM-68 AC-6] dates keep four-digit years', () => {
-    expect(fieldValue('Revalidation due')).toBe('03/02/2070');
-    expect(fieldValue('Effective date')).toBe('03/02/2026');
+    expect(fieldValue('Revalidation due')).toBe('2 Mar 2070');
+    expect(fieldValue('Effective date')).toBe('2 Mar 2026');
```

Test titles that named the old format were corrected for honesty (AC tags kept); typed date inputs were not touched.

**Runs (2026-11-19, one worker because the host was short of memory):** before the correction the full Vitest suite (`--maxWorkers=1 --testTimeout=60000 --hookTimeout=60000`, PowerShell, lowercase path) had **36 failures in 15 files (611 of 647 pass)**: 35 are exactly the lines of the A1/A2 list; the 36th is a timing failure in an unedited file, see the MAM-64 note. After the correction: **647 of 647 pass in 29 files**. `npx tsc -b` (it includes `tests` and `e2e`) and `eslint --max-warnings 0` on the 24 changed files: no findings.
The full default Playwright suite (16 specs, journeys excluded, `--workers=1`, `NODE_OPTIONS=--dns-result-order=ipv4first`, one run): **152 of 152 pass** (8.4 min). The Playwright suite was not run before the correction (no time; the UI agent's static list was used), so there is no before count for it.

**Hashes:** re-recorded with `test_protection.py record MAM-68` with exactly the files of the story's `.tests.sha256`; `check` and `sha256sum -c` are OK.
