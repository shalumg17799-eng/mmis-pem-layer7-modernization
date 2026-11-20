# MAM-62 red report: Web UI shell (sign-in, role menu, portal home)

Tests written by the test-writer (UI lane, 2026-11-19); the lane was stopped before it recorded them; the orchestrator
ran them unchanged and recorded the hashes. Run: `npx vitest run tests/shell tests/portal` -> 7 files, 145 tests, all
failing for the expected reason: the app only renders the scaffold frame (no sign-in redirect, no role menu, no access-
denied page, no portal home, no sign-out). Lint is clean. Playwright `e2e/ui-shell.spec.ts` (sign-in through the real
Keycloak, menus per role, axe) is written and runs against the stack after green.

| AC | Component tests | Expected failure |
|---|---|---|
| AC-1 sign-in with Keycloak (PKCE, return to the asked route, bearer token to the gateway) | tests/shell/sign-in.test.tsx | signinRedirect never called |
| AC-2 menu by role | tests/shell/menu.test.tsx | no navigation landmark |
| AC-3 access denied for other roles' routes | tests/shell/access-denied.test.tsx | no access-denied page |
| AC-4 portal home: applications table and Apply button | tests/portal/home-applications.test.tsx | no table |
| AC-5 portal home: own provider summary from the npi claim | tests/portal/home-provider-summary.test.tsx | no summary |
| AC-6 sign-out and 401 handling | tests/shell/sign-out-and-session.test.tsx | no sign-out control |
| AC-7 one h1, landmarks, skip link, keyboard | tests/shell/accessibility-structure.test.tsx (+ axe in e2e) | missing nav/structure |

## Test correction under Q-097 (breadcrumb nav, user decision Q-101, 2026-11-19)

Cause: **the UI will get a real USWDS breadcrumb, `<nav aria-label="Breadcrumb">`.** The MAM-62 menu helpers read the links of every
navigation landmark, so a breadcrumb nav would add its links to the menu that the tests assert (Q-097 rule: scoping only, no
assertion weakened or removed; production code unchanged). The main menu landmark is `<PrimaryNav aria-label="Primary navigation">`
(`src/app/AppShell.tsx:47`). No breadcrumb exists today, so the tests pass before and must pass after it exists.

**Scoped (4 queries in 3 files)**

`tests/support/world.tsx` (LF), `menuEntries()`: the helper behind every menu assertion of `menu.test.tsx`, `sign-in.test.tsx`,
`access-denied.test.tsx`, `accessibility-structure.test.tsx` and `sign-out-and-session.test.tsx` (their assertions are untouched)
```diff
-/** Links of every navigation landmark (the menu), without the sign-out control. */
+/** Links of the main menu landmark, named "Primary navigation" (a breadcrumb nav is not the menu; Q-101), without the sign-out control. */
-    .queryAllByRole('navigation')
+    .queryAllByRole('navigation', { name: 'Primary navigation' })
```
`e2e/support/page.ts` (LF), `menuEntries(page)` (used by `ui-shell.spec.ts` only)
```diff
-/** Links of the navigation landmark(s): [visible text, path]; the sign-out control is not a menu entry. */
+/** Links of the main menu landmark, named "Primary navigation" (a breadcrumb nav is not the menu; Q-101): [visible text, path]; the sign-out control is not a menu entry. */
-  const links = await page.getByRole('navigation').getByRole('link').all();
+  const links = await page.getByRole('navigation', { name: 'Primary navigation' }).getByRole('link').all();
```
`e2e/ui-shell.spec.ts` (LF), the two menu-entry queries that look a link up by its name in "the navigation" (a breadcrumb link with the
same name, for example "My enrolment", would make `getByRole('link', { name, exact: true })` match twice and fail in strict mode)
```diff
-    const nav = page.getByRole('navigation');
+    const nav = page.getByRole('navigation', { name: 'Primary navigation' });
-    await tabTo(page, page.getByRole('navigation').getByRole('link', { name: 'My notices', exact: true }));
+    await tabTo(page, page.getByRole('navigation', { name: 'Primary navigation' }).getByRole('link', { name: 'My notices', exact: true }));
```

**Reviewed and not scoped** (a second nav changes neither their meaning nor their result)
- `tests/shell/accessibility-structure.test.tsx:41` `getAllByRole('navigation').length >= 1` (and `e2e/support/page.ts` `expectShellStructure`
  `getByRole('navigation')` `not.toHaveCount(0)`): "at least one navigation landmark" stays true.
- `tests/shell/accessibility-structure.test.tsx:103` `getAllByRole('navigation')[0]`: the first landmark in document order is the one in
  the page header (the breadcrumb sits in or above `main`, after the header), so it stays the primary navigation. It relies on that order.
- `e2e/ui-shell.spec.ts:106` `getByRole('navigation').getByRole('link').first()`: only waits for the first link to be visible; the first
  link in document order is the menu's.

**Not navigation-landmark queries, but a same-named breadcrumb link could make them match twice (not changed; to watch when the breadcrumb is built)**
`getByRole('link', { name: 'Provider search' })` in `tests/shell/sign-in.test.tsx:91` and `e2e/ui-shell.spec.ts:81`; `'Work queues'` in
`sign-in.test.tsx:104`; `getByRole('link', { name: 'My enrolment' })` in `e2e/ui-shell.spec.ts:156,202,213`. A USWDS breadcrumb shows
the current page as text, not as a link, so these hold if the breadcrumb of a start page lists only text or other pages.

| File | Previous blob | New blob | Line endings |
|---|---|---|---|
| `tests/support/world.tsx` | `b5d53c2c2fedf657429cbbee2b82298b80ee0ce0` | `0ab4b230a68ba07b60a22e3b9fdda9da82a98edb` | LF |
| `e2e/support/page.ts` | `9c57bd8dece3c520f7fdc637e25cc5532d21de3d` | `d2b4cf8e39e426a421025680acfd68f38e7fb105` | LF |
| `e2e/ui-shell.spec.ts` (this scoping and the racy-test fix below) | `3e343f30497e48d0cac5a6534470f6ed6bcb696c` | `f5bffcbf42133e52d195cd6c729579f8fdcb6e97` | LF |

The previous blobs are those in the table above (`red-blobs.txt` was rewritten by the re-record and now lists the new blobs); a plain `diff` (no `--strip-trailing-cr`) against them shows only the lines in this section.

**Racy test found by the Playwright run (same pattern as the MAM-87 AC-6 correction; Q-097 C)**

`e2e/ui-shell.spec.ts`, "[MAM-62 AC-3] provider1 typing /console/providers in the address bar sees Access denied (403), a link to the
start page, and no API call is made" failed once in a run of this spec (it passed in an earlier run): the call log was cleared as soon
as the menu was visible, while the portal home's `GET /api/v1/applications` and `GET /api/v1/providers/1003000111` could still be in
flight, and they reached the stub after the clear (`expect(gateway.calls).toEqual([])`). Not caused by the scoping above. The test now
clears the log only after both calls were recorded (provider1 only; the console start page of analyst1 and admin1 makes no call).
No assertion changed.
```diff
       await expect(page.getByRole('navigation').getByRole('link').first()).toBeVisible();
+      // Q-097 C (racy test): the portal home of provider1 makes two calls when it mounts (GET /api/v1/applications and GET /api/v1/providers/{npi}
+      // of the token's NPI); the log is cleared only after both reached the stub, so that neither can arrive after the clear. The console start page makes no call.
+      if (user === 'provider1') {
+        await expect.poll(() => ['/api/v1/applications', `/api/v1/providers/${PROVIDER_NPI}`].every((path) => gateway.calls.some((call) => call.path === path))).toBe(true);
+      }
       gateway.calls.length = 0;
```

**Runs (2026-11-19; no breadcrumb exists yet, so these show the tests pass now; the scoping only removes a dependency on there being one nav)**
- Vitest `tests/shell` and `tests/portal/home-*.test.tsx` (the 7 MAM-62 files): 145/145.
- Playwright `e2e/ui-shell.spec.ts`: 26/26 in the full default run (the run right after the scoping had 25 of 26 pass; the one failure is the racy AC-3 test
  above, which is fixed); `-g "MAM-62 AC-3" --repeat-each=8`: 24/24. `ui-shell.spec.ts` is the only spec that uses `menuEntries(page)`.
- Full Vitest (`--maxWorkers=2 --testTimeout=30000 --hookTimeout=30000`): 647/647 in 29 files. Full default Playwright (all 16 specs in two batches,
  `--workers=2`): 152/152. `npx eslint ... --max-warnings 0` on the three changed files: no findings.
- **Hashes:** re-recorded with `test_protection.py record MAM-62` (the same 15 files); `check MAM-62` prints OK; `sha256sum -c` OK for all 15 files.

## Test correction under Q-097 (UI polish B-iv, user decision 2026-11-19)

Cause: **the UI polish of 2026-11-19 (B-iv)**, under the Q-097 rule and the user's explicit permission to use it for any selector change. The key-value tables "Provider record", "Provider", "Application" and the portal home's "My provider record" are now USWDS summary lists (`<section aria-labelledby>`, a region named by its title, holding a `<dl>` of `div > dt + dd`); the label "Screen flag" is "Screening result"; the screening sources are shown by name (Licence board, OIG exclusion list (LEIE), SAM.gov debarment, SSA death records); and the status page has a step indicator whose step-2 segment reads "Validated" ("Validation failed" for a rejected application). Scoping and renaming only: no assertion was weakened or removed; production code unchanged. Test titles keep their wording unless said otherwise.

What changed in MAM-62:
- `home-provider-summary.test.tsx`: the label lookups "Screen flag" are "Screening result".
- `ui-shell.spec.ts` (AC-5): the `summary` helper reads the `dd` of the row whose `dt` has the label; "Screen flag" is "Screening result".
- `tests/support/world.tsx` `fieldValue()` is **unchanged**: it already reads the element after a `dt`, which is the `dd` of `div > dt + dd`, and it keeps the table-row
  behaviour for the real tables.

Exact changes (`-` before, `+` after; the previous blob is the state at the start of this correction, line endings kept, a plain `diff` against it shows only these lines):

| File | Previous blob | New blob | Line endings | Changed lines |
|---|---|---|---|---|
| `tests/portal/home-provider-summary.test.tsx` | `019777c0014956ddc1e930cfa6beacc0de76e6b7` | `04c6f8c32107992586a98da8ad4c2672f1894815` | LF | 4 |
| `e2e/ui-shell.spec.ts` | `f5bffcbf42133e52d195cd6c729579f8fdcb6e97` | `5f1667ea86ce5f8f11c24845fe347c7b3a2f8324` | LF | 4 |

`tests/portal/home-provider-summary.test.tsx` (LF)
```diff
@@ -72 +72 @@
-    expect([fieldValue('Enrolment status'), fieldValue('Screen flag')]).toEqual(['Active (A)', 'Pass (P)']);
+    expect([fieldValue('Enrolment status'), fieldValue('Screening result')]).toEqual(['Active (A)', 'Pass (P)']);
@@ -96 +96 @@
-    expect(fieldValue('Screen flag')).toBe(label);
+    expect(fieldValue('Screening result')).toBe(label);
```

`e2e/ui-shell.spec.ts` (LF)
```diff
@@ -148 +148 @@
-    const summary = (label: string) => page.getByRole('row').filter({ has: page.getByRole('cell', { name: label, exact: true }) }).getByRole('cell').nth(1);
+    const summary = (label: string) => page.locator('dl > div').filter({ has: page.getByText(label, { exact: true }) }).locator('dd');
@@ -152 +152 @@
-    await expect(summary('Screen flag')).toHaveText('Pass (P)');
+    await expect(summary('Screening result')).toHaveText('Pass (P)');
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
| `e2e/ui-shell.spec.ts` | `5f1667ea86ce5f8f11c24845fe347c7b3a2f8324` | `a852d0a794462442db05d531cb5cb99c2293ebbd` | LF | 7 / 7 |
| `tests/portal/home-applications.test.tsx` | `ca10b9a0a2770ed1c3eac287d2744b4852d84543` | `3231ef38dad7c5c180c945c69f876657d8c0350b` | LF | 6 / 6 |
| `tests/portal/home-provider-summary.test.tsx` | `04c6f8c32107992586a98da8ad4c2672f1894815` | `cb7a965b314530971ad4cb19c87765a505431192` | LF | 1 / 1 |

`e2e/ui-shell.spec.ts` (LF)
```diff
@@ -125 +125 @@ test.describe('[MAM-62 AC-4][MAM-62 AC-5] portal home', () => {
-  test('[MAM-62 AC-4] provider1 sees the application table with 03/01/2026, 1003000111 in full and Validated, and a View link', async ({ page }) => {
+  test('[MAM-62 AC-4] provider1 sees the application table with 1 Mar 2026, 1003000111 in full and Validated, and a View link', async ({ page }) => {
@@ -129 +129 @@ test.describe('[MAM-62 AC-4][MAM-62 AC-5] portal home', () => {
-    const row = page.getByRole('row').filter({ hasText: '03/01/2026' });
+    const row = page.getByRole('row').filter({ hasText: '1 Mar 2026' });
@@ -180 +180 @@ test.describe('[MAM-62 AC-6] sign-out and ended sessions', () => {
-    await expect(page.getByRole('row').filter({ hasText: '03/01/2026' })).toBeVisible();
+    await expect(page.getByRole('row').filter({ hasText: '1 Mar 2026' })).toBeVisible();
@@ -189 +189 @@ test.describe('[MAM-62 AC-7] the shell and portal home have zero axe violations'
-    await expect(page.getByRole('row').filter({ hasText: '03/01/2026' })).toBeVisible();
+    await expect(page.getByRole('row').filter({ hasText: '1 Mar 2026' })).toBeVisible();
@@ -228 +228 @@ test.describe('[MAM-62 AC-7] the shell and portal home have zero axe violations'
-    await expect(page.getByRole('row').filter({ hasText: '03/01/2026' })).toBeVisible();
+    await expect(page.getByRole('row').filter({ hasText: '1 Mar 2026' })).toBeVisible();
@@ -240 +240 @@ test.describe('[MAM-62 AC-7] the shell and portal home have zero axe violations'
-    await expect(page.getByRole('row').filter({ hasText: '03/01/2026' })).toBeVisible();
+    await expect(page.getByRole('row').filter({ hasText: '1 Mar 2026' })).toBeVisible();
@@ -263 +263 @@ test.describe('[MAM-62 AC-7] the shell and portal home have zero axe violations'
-    await expect(page.getByRole('row').filter({ hasText: '03/01/2026' })).toBeVisible();
+    await expect(page.getByRole('row').filter({ hasText: '1 Mar 2026' })).toBeVisible();
```

`tests/portal/home-applications.test.tsx` (LF)
```diff
@@ -52 +52 @@ describe('[MAM-62 AC-4] portal home lists my applications and leads to the form'
-  it('[MAM-62 AC-4] an application submitted on 03/01/2026 and Validated shows 03/01/2026, the NPI in full and Validated', async () => {
+  it('[MAM-62 AC-4] an application submitted on 2026-03-01 and Validated shows 1 Mar 2026, the NPI in full and Validated', async () => {
@@ -60 +60 @@ describe('[MAM-62 AC-4] portal home lists my applications and leads to the form'
-    expect(cellTexts(rows[0]!).slice(0, 3)).toEqual(['03/01/2026', '1003000111', 'Validated']);
+    expect(cellTexts(rows[0]!).slice(0, 3)).toEqual(['1 Mar 2026', '1003000111', 'Validated']);
@@ -64,4 +64,4 @@ describe('[MAM-62 AC-4] portal home lists my applications and leads to the form'
-    ['2026-03-01T10:00:00Z', '03/01/2026'],
-    ['2026-01-09T10:00:00Z', '01/09/2026'],
-    ['2026-12-31T10:00:00Z', '12/31/2026'],
-  ])('[MAM-62 AC-4] the submission instant %s is shown as MM/DD/YYYY: %s', async (submittedAt, shown) => {
+    ['2026-03-01T10:00:00Z', '1 Mar 2026'],
+    ['2026-01-09T10:00:00Z', '9 Jan 2026'],
+    ['2026-12-31T10:00:00Z', '31 Dec 2026'],
+  ])('[MAM-62 AC-4] the submission instant %s is shown as a plain date: %s', async (submittedAt, shown) => {
```

`tests/portal/home-provider-summary.test.tsx` (LF)
```diff
@@ -139 +139 @@ describe('[MAM-62 AC-5] portal home summarises my own provider record', () => {
-    expect(cellTexts(bodyRows(table)[0]!).slice(0, 3)).toEqual(['03/01/2026', '1003000111', 'Validated']);
+    expect(cellTexts(bodyRows(table)[0]!).slice(0, 3)).toEqual(['1 Mar 2026', '1003000111', 'Validated']);
```

Test titles that named the old format were corrected for honesty (AC tags kept); typed date inputs were not touched.

**Runs (2026-11-19, one worker because the host was short of memory):** before the correction the full Vitest suite (`--maxWorkers=1 --testTimeout=60000 --hookTimeout=60000`, PowerShell, lowercase path) had **36 failures in 15 files (611 of 647 pass)**: 35 are exactly the lines of the A1/A2 list; the 36th is a timing failure in an unedited file, see the MAM-64 note. After the correction: **647 of 647 pass in 29 files**. `npx tsc -b` (it includes `tests` and `e2e`) and `eslint --max-warnings 0` on the 24 changed files: no findings.
The full default Playwright suite (16 specs, journeys excluded, `--workers=1`, `NODE_OPTIONS=--dns-result-order=ipv4first`, one run): **152 of 152 pass** (8.4 min). The Playwright suite was not run before the correction (no time; the UI agent's static list was used), so there is no before count for it.

**Hashes:** re-recorded with `test_protection.py record MAM-62` with exactly the files of the story's `.tests.sha256`; `check` and `sha256sum -c` are OK.
