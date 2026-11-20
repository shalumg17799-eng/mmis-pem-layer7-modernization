# MAM-66 red report: Portal, see my application status

Run on 2026-11-19 (fast mode). Before the story the route showed the minimal page of MAM-64, which reads GET /api/v1/applications and picks the item by id.
Hashes: `modern/docs/reports/stories/MAM-66.tests.sha256` (2 files), restore copies in `red-blobs.txt`.

| Level | Command | Tests | Result | Reason |
|---|---|---|---|---|
| Vitest | `npx vitest run tests/portal/application-status.test.tsx --pool=vmThreads` | 15 | 15 failed | the page never calls GET /api/v1/applications/{id}: no `Validated` / `Rejected` (7), no `No validation errors` (1), no access-denied text (1), `Application not found` missing (1), no sign-in message (1), no `The application could not be loaded. Try again.` (4) |
| Playwright | `npx playwright test e2e/portal-status.spec.ts` | 5 | 5 failed | status text, `Application not found`, access denied and the failure message are not on the page (the by-id call is answered 404 by the stub) |

AC to test: AC-1 by-id call, all fields, "No validation errors", "Validated" once in main, a group (5); AC-2 one error row E104 (1); AC-3 two rows in order, an unknown type shown as sent (2);
AC-4 403 and 404 (2); AC-5 401 with "Sign in again", 500, 503, network, Retry (5); Playwright: validated + axe, rejected with two errors + axe, 404 with keyboard + axe, 403, failed load + keyboard Retry + axe.

Conflict with MAM-64 (decision needed): the tests of MAM-64 were recorded for the interim list-based page (the by-id endpoint did not exist). Switching to GET /api/v1/applications/{id}
makes 15 recorded MAM-64 Vitest tests and 4 recorded MAM-64 Playwright tests fail, because they stub only GET /api/v1/applications (see the story report for the list). They were not edited.

## Test correction under Q-097 (timeline wording, user decision Q-104)

Reviewed, **no change needed** (hashes of MAM-66 unchanged). The status page will show a progress list whose step 2 reads "Validated" ("Step 2 of 5: Validated - done"),
so a lookup of the word that matches by substring would find two elements. None of the MAM-66 lookups does:
- Vitest `application-status.test.tsx`: every `findByText` / `queryByText` / `getAllByText('Validated' | 'Rejected')` is an exact whole-text match, and the step text is longer.
  This includes the "appears once in the main landmark" test (`getAllByText('Validated')` has length 1). The regex lookups (`/Validated|Rejected/`, `/Validated|Rejected|Submitted/`) are
  on the 401 and failed-load pages, which show no progress list. `fieldValue('Status')` reads the table row.
- Playwright `portal-status.spec.ts`: `getByText('Validated' | 'Rejected', { exact: true })` (whole text, case-sensitive) and a count of 0 of the regex on the failed-load page.
Shown with a throw-away copy of `src` and `tests` with the new step wording (not in the repo, deleted): `tests/portal` 10 files, 193/193 pass; and a static-HTML check in
Chromium: `getByText('Validated', { exact: true })` finds 1 element. The substring lookups of MAM-64 (`e2e/portal-apply.spec.ts`) were narrowed; see the MAM-64 report.

## Test correction under Q-097 (UI polish B-iv, user decision 2026-11-19)

Cause: **the UI polish of 2026-11-19 (B-iv)**, under the Q-097 rule and the user's explicit permission to use it for any selector change. The key-value tables "Provider record", "Provider", "Application" and the portal home's "My provider record" are now USWDS summary lists (`<section aria-labelledby>`, a region named by its title, holding a `<dl>` of `div > dt + dd`); the label "Screen flag" is "Screening result"; the screening sources are shown by name (Licence board, OIG exclusion list (LEIE), SAM.gov debarment, SSA death records); and the status page has a step indicator whose step-2 segment reads "Validated" ("Validation failed" for a rejected application). Scoping and renaming only: no assertion was weakened or removed; production code unchanged. Test titles keep their wording unless said otherwise.

What changed in MAM-66:
- `application-status.test.tsx`: a helper `inApplication()` finds the region "Application"; the five lookups of "Validated" (the three waits, the "appears once"
  count and the Retry test) go through it. The count stays `toHaveLength(1)`, now "once in the Application list" (the segment label is the second "Validated" of the page).
  Four tests no longer bind `main` (unused). The "Rejected" lookups and the `queryByText('Validated')` null checks are unchanged: a rejected page says "Validation failed" at step 2.
- `portal-status.spec.ts`: the `valueOf` helper reads the `dd` after the `dt`. The `getByText('Validated' | 'Rejected', { exact: true })` lookups are unchanged: the text of the
  segment label includes its screen-reader text ("Validated completed"), so only the badge matches a whole-text lookup.

Exact changes (`-` before, `+` after; the previous blob is the state at the start of this correction, line endings kept, a plain `diff` against it shows only these lines):

| File | Previous blob | New blob | Line endings | Changed lines |
|---|---|---|---|---|
| `tests/portal/application-status.test.tsx` | `9497438e6fc960674e0baf5be742bb5baaef7076` | `9b487b93cb6ff1b1fcd93b2e6271c708d983afea` | LF | 25 |
| `e2e/portal-status.spec.ts` | `6da2868dbdcaeb53dfe94e8ce7a919d870420761` | `abc6d8a34fcb409117ef8abb0c682d893e282c0e` | LF | 3 |

`tests/portal/application-status.test.tsx` (LF)
```diff
@@ -37,0 +38,5 @@
+/** The "Application" summary list (region): the status badge is looked up inside it, because the step indicator names step 2 "Validated" too (Q-104, UI polish). */
+async function inApplication() {
+  return within(await screen.findByRole('region', { name: 'Application' }));
+}
+
@@ -45,2 +50,2 @@
-    const { main } = await openStatus(APPLICATION_ID, { [BY_ID]: () => response(200, VALIDATED) });
-    await main.findByText('Validated');
+    await openStatus(APPLICATION_ID, { [BY_ID]: () => response(200, VALIDATED) });
+    await (await inApplication()).findByText('Validated');
@@ -52,2 +57,2 @@
-    const { main } = await openStatus(APPLICATION_ID, { [BY_ID]: () => response(200, VALIDATED) });
-    await main.findByText('Validated');
+    await openStatus(APPLICATION_ID, { [BY_ID]: () => response(200, VALIDATED) });
+    await (await inApplication()).findByText('Validated');
@@ -76,2 +81,2 @@
-    const { main } = await openStatus(APPLICATION_ID, { [BY_ID]: () => response(200, VALIDATED) });
-    await main.findByText('Validated');
+    await openStatus(APPLICATION_ID, { [BY_ID]: () => response(200, VALIDATED) });
+    await (await inApplication()).findByText('Validated');
@@ -79 +84 @@
-    expect(main.getAllByText('Validated')).toHaveLength(1);
+    expect((await inApplication()).getAllByText('Validated')).toHaveLength(1);
@@ -89,2 +94,2 @@
-    const { main } = await openStatus(APPLICATION_ID, { [BY_ID]: () => response(200, group) });
-    await main.findByText('Validated');
+    await openStatus(APPLICATION_ID, { [BY_ID]: () => response(200, group) });
+    await (await inApplication()).findByText('Validated');
@@ -188 +193 @@
-    expect(await main.findByText('Validated')).toBeInTheDocument();
+    expect(await (await inApplication()).findByText('Validated')).toBeInTheDocument();
```

`e2e/portal-status.spec.ts` (LF)
```diff
@@ -31 +31,2 @@
-const valueOf = (page: Page, label: string) => page.getByRole('row').filter({ has: page.getByRole('cell', { name: label, exact: true }) }).getByRole('cell').nth(1);
+/** The value of a row of a key-value list (UI polish, Q-097): <div><dt>label</dt><dd>value</dd></div>. */
+const valueOf = (page: Page, label: string) => page.locator('dl > div').filter({ has: page.getByText(label, { exact: true }) }).locator('dd');
```

Runs (2026-11-19; the host was short of memory, so one worker): before the correction Vitest had 77 failures in 11 files (570 of 647 passed, as reported by the lead and re-run here)
and Playwright 46 failures in 8 specs (106 of 152, as reported by the lead). After it: the full Vitest suite (`--maxWorkers=1 --testTimeout=60000 --hookTimeout=60000`, PowerShell, lowercase path)
**647 of 647 pass** in 29 files; the full default Playwright suite (16 specs in 4 batches, `--workers=1`, journeys excluded) **152 of 152 pass**; `npx tsc -b` and `npx eslint --max-warnings 0`
on the changed files: no findings. The journey J3 (MAM-74) was not run (no journeys, no seeding).
**Hashes:** re-recorded with `test_protection.py record <KEY>` with exactly the files of the story's `.tests.sha256`; `check` and `sha256sum -c` are OK.

## Test correction under Q-097 (plain dates + Reference, user decision 2026-11-19 19:54)

Cause: **the user decisions of 2026-11-19 19:54 that the UI now implements**, under the Q-097 C standing rule (superseded tests; scoping and wording only, no assertion weakened or removed, production code unchanged). **A2, plain dates:** every displayed date reads "19 Nov 2026", the day without a leading zero (`formatDate` in `web-ui/src/shared/format/format.ts`); typed date inputs stay MM/DD/YYYY, so no value a test types into an input was changed. **A1, application status page:** the application ID is the single text "Reference: <id>" under the h1, the "Application" row of the summary list is gone (the list keeps its title "Application", so region lookups still work), and the paragraph "Your application was accepted. ..." is removed.

**A1:** `application-status.test.tsx`, `expect(fieldValue('Application')).toBe(APPLICATION_ID)` is now `expect(text(screen.getByText(/^Reference: /))).toBe(`Reference: ${APPLICATION_ID}`)`: the same full-text equality, on the text that now carries the ID. `portal-status.spec.ts` has no lookup of the removed row.

**Reviewer MUST-FIX of the B-iv round (code-reviewer, 2026-11-19), folded in here, additive only:**
1. `application-status.test.tsx` "the word "Validated" appears once in the main landmark": the B-iv scoping to the region "Application" is kept, and the count over the whole main landmark is restored next to it: `within(screen.getByRole('main')).getAllByText('Validated', { ignore: 'script, style, .usa-step-indicator__segment-label' })` has length 1 (only the step-indicator label of the user-decided progress list, Q-104, is excluded; the badge is the one match).
2. The "no table" guards were vacuous after the data became a `dl` in a named region: `application-status.test.tsx` (404) now also asserts `queryByRole('region', { name: 'Application' })` is null; `portal-status.spec.ts` (403) now also asserts `getByRole('region', { name: 'Application', exact: true })` has count 0. The table checks stay. (The 403 and failed-load Vitest guards and the neighbouring text assertions are unchanged, as the reviewer said.)

Exact changes (`-` before, `+` after; the previous blob is the state at the start of this correction, line endings kept, a plain `diff` against it shows only these lines):

| File | Previous blob | New blob | Line endings | Changed lines (removed / added) |
|---|---|---|---|---|
| `e2e/portal-status.spec.ts` | `abc6d8a34fcb409117ef8abb0c682d893e282c0e` | `8f2b10b25e12bb64bde7caab2e6daad6771e2cd5` | LF | 1 / 2 |
| `tests/portal/application-status.test.tsx` | `9b487b93cb6ff1b1fcd93b2e6271c708d983afea` | `1f0d59bfda8b6d763cb3ddde6d9b9b294807ccbc` | LF | 2 / 4 |

`e2e/portal-status.spec.ts` (LF)
```diff
@@ -39 +39 @@ test('[MAM-66 AC-1][MAM-66 AC-6] a validated application shows its details and "
-  await expect(valueOf(page, 'Submitted')).toHaveText('03/01/2026');
+  await expect(valueOf(page, 'Submitted')).toHaveText('1 Mar 2026');
@@ -75,0 +76 @@ test('[MAM-66 AC-4] another provider\'s application (403) shows an access-denied
+  await expect(page.getByRole('region', { name: 'Application', exact: true })).toHaveCount(0);
```

`tests/portal/application-status.test.tsx` (LF)
```diff
@@ -60 +60 @@ describe('[MAM-66 AC-1] a validated application shows its status and details (jo
-    expect(fieldValue('Application')).toBe(APPLICATION_ID);
+    expect(text(screen.getByText(/^Reference: /))).toBe(`Reference: ${APPLICATION_ID}`);
@@ -62 +62 @@ describe('[MAM-66 AC-1] a validated application shows its status and details (jo
-    expect(fieldValue('Submitted')).toBe('03/01/2026');
+    expect(fieldValue('Submitted')).toBe('1 Mar 2026');
@@ -84,0 +85 @@ describe('[MAM-66 AC-1] a validated application shows its status and details (jo
+    expect(within(screen.getByRole('main')).getAllByText('Validated', { ignore: 'script, style, .usa-step-indicator__segment-label' })).toHaveLength(1);
@@ -153,0 +155 @@ describe('[MAM-66 AC-4] someone else\'s or an unknown application is never shown
+    expect(screen.queryByRole('region', { name: 'Application' })).toBeNull();
```

Test titles that named the old format were corrected for honesty (AC tags kept); typed date inputs were not touched.

**Runs (2026-11-19, one worker because the host was short of memory):** before the correction the full Vitest suite (`--maxWorkers=1 --testTimeout=60000 --hookTimeout=60000`, PowerShell, lowercase path) had **36 failures in 15 files (611 of 647 pass)**: 35 are exactly the lines of the A1/A2 list; the 36th is a timing failure in an unedited file, see the MAM-64 note. After the correction: **647 of 647 pass in 29 files**. `npx tsc -b` (it includes `tests` and `e2e`) and `eslint --max-warnings 0` on the 24 changed files: no findings.
The full default Playwright suite (16 specs, journeys excluded, `--workers=1`, `NODE_OPTIONS=--dns-result-order=ipv4first`, one run): **152 of 152 pass** (8.4 min). The Playwright suite was not run before the correction (no time; the UI agent's static list was used), so there is no before count for it.

**Hashes:** re-recorded with `test_protection.py record MAM-66` with exactly the files of the story's `.tests.sha256`; `check` and `sha256sum -c` are OK.
