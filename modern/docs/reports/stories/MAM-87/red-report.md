# MAM-87 red report: Console, approve or deny an enrolment

Run on 2026-11-19, before any production code of the story existed (/console/providers/:npi and
/console/providers/:npi/approval were placeholders; the Provider search row had no link).

Test hashes: `modern/docs/reports/stories/MAM-87.tests.sha256` (5 files), restore copies in `red-blobs.txt`.
`npx tsc -b` and `npx eslint tests e2e --max-warnings 0` pass for the new files. The scripted gateway
`e2e/support/gateway-routes.ts` is shared with MAM-64 and stays recorded under MAM-64.

## Result of the red run

| Level | Command | Tests | Result | Reason |
|---|---|---|---|---|
| Vitest | `npx vitest run tests/console/approval-*.test.tsx` | 74 | 71 failed, 3 passed | 71 failures are all "the page is missing": no provider data (`Unable to find an element with the text: RIVERA MARIA` / `EXCLUDED CLINIC LLC` / `SUNSET HOSPICE INC`, 46), `fieldValue('NPI')` undefined on the detail page (12), `No provider found` / `provider could not be loaded` / `No screening results yet` / `screening results could not be loaded` (7), no `group "Decision"` or `radio` (4), no link on the NPI of the search result (2) |
| Playwright | `npx playwright test e2e/console-approval.spec.ts --workers=3` | 28 | 26 failed, 2 passed | 26 failures are the missing page: `getByRole('cell', { name: 'RIVERA MARIA' })` (and the two other names) not found (22), `Status` / `Screen flag` row missing on the detail page (3), NPI link missing in the search result (1; it first ended in a 90 s timeout on `click()`, so the test now asserts the link first and fails in 15 s) |

The 3 + 2 tests that pass in the red run are regression guards of AC-6 that MAM-62's role guard already satisfies:
admin1 and provider1 on /console/providers/:npi/approval and provider1 on /console/providers/:npi get the Access denied page and no API call.
They are kept because AC-6 asks for exactly that; they were not red because the behaviour already exists.

## AC to test

| AC | Vitest | Playwright |
|---|---|---|
| AC-1 | `approval-page.test.tsx` (provider table, calls, per-source results, E203, C, latest screening, 404, 5xx, bad NPI), `approval-reach.test.tsx` (detail page, "Approve or deny" for ANALYST/SUPERVISOR only when Pending with flag P or F, Provider search NPI link) | `[MAM-87 AC-1]` x 4 |
| AC-2 | `approval-decision.test.tsx` (POST APPROVE, Active (A), due date as returned, no further decision, detail page after) | `[MAM-87 AC-2]` |
| AC-3 | `approval-decision.test.tsx` (POST DENY + reason, Denied (D), 200 limit and counter, no reason, blanks) | `[MAM-87 AC-3]` x 2 |
| AC-4 | `approval-page.test.tsx` (Fail: Approve disabled with text, Deny only; Unscreened: nothing offered; nothing sent; non-Pending) | `[MAM-87 AC-4]` x 2 |
| AC-5 | `approval-decision.test.tsx` (409 detail and reload, 422 kept, 404, 500, 503, network, retry, focus) | `[MAM-87 AC-5]` x 6 |
| AC-6 | `approval-page.test.tsx` (analyst1, supervisor1 see the form; admin1, provider1 denied), `approval-decision.test.tsx` (403) | `[MAM-87 AC-6]` x 5 |
| AC-7 | `approval-page.test.tsx` (labelled group, Reason label, text plus code, headings and captions) | `[MAM-87 AC-7]` x 9 (axe on Pass, Pass with Deny, Fail, Unscreened, after 409, after 500; labels; keyboard approve; keyboard deny) |

## Reading of the spec that the tests fix (decisions to confirm)

1. The Reason box appears only when Deny is chosen (the wireframe shows it always); a reason is never sent with Approve.
2. Record decision with no choice sends nothing and shows "1 error in the decision" with the link "Choose Approve or Deny" (not in the ACs; the alternative is a disabled button).
3. A source result C is written "Conditional (C)" (SCR-COND in SCRNREC.cpy); the meaning is inferred from the 88-level name.
4. A 401 on a decision ends the session as for every call (MAM-62 AC-6): the typed reason is lost. AC-5 and AC-6 do not cover 401.
5. The queue part of AC-1 ("Review" in the Pending approval queue) and "no longer listed under Pending approval / Failed screening" (AC-2, AC-3) belong to ui-console-queues and are not tested.

## Test change for Q-093 (user decision 2026-11-19)

- **Files:** `modern/web-ui/tests/console/approval-page.test.tsx` line 294 and `modern/web-ui/e2e/console-approval.spec.ts`
  line 418 ("[MAM-87 AC-7] ... status and flag are text plus code").
- **Fault (in the tests):** `getByRole('cell', { name: 'Pass (P)' })` matched five cells: the screen flag plus the four
  per-source results that the same story requires (Vitest: "Found multiple elements"; Playwright: strict mode, 5 elements).
- **Fix (user-approved, Q-093 option A):** both lookups are scoped to the provider table:
  `within(screen.getByRole('table', { name: 'Provider' })).getByRole('cell', { name: 'Pass (P)' })` and
  `page.getByRole('table', { name: 'Provider' }).getByRole('cell', { name: 'Pass (P)' })`. Nothing else changed.
- **Runs:** Vitest approval-*.test.tsx 74/74; Playwright console-approval.spec.ts 28/28. Production code unchanged.
- **Hashes:** re-recorded with `test_protection.py record MAM-87` (same 5 files).
- **Code-reviewer confirmation (2026-11-19):** the scoping is the only logic change and nothing is weakened; the provider
  table is captioned "Provider" and holds the screen-flag cell. The review found the e2e file had been rewritten from CRLF
  to LF by the edit; CRLF was restored, and a byte-level diff against the previous blobs now shows only line 418 (e2e) and
  line 294 (page). Previous blobs: console-approval.spec.ts `3ab6aad7bd76907c1c12a3ec07f7db638249ec32`,
  approval-page.test.tsx `f32331dc8acf10c6d23c848e6235417b8fdcf4ad`. Hashes re-recorded again.

## Test correction under Q-097 (user decision 2026-11-19)

Cause: **racy test** (failed 1 of 5 runs). Q-097 option A, item 3.

- **File:** `modern/web-ui/e2e/console-approval.spec.ts`, test "[MAM-87 AC-6] provider1 sees the access-denied page and no
  provider data is fetched" (the loop also runs it for admin1; lines 335-336). CRLF file; line endings kept.
- **Fault (in the test):** after the Keycloak sign-in the test cleared the call log (`api.calls.length = 0`) as soon as the menu
  was visible. The portal home of provider1 calls `GET /api/v1/applications` and `GET /api/v1/providers/{npi}` when it mounts;
  when one of them was still in flight it reached the gateway stub after the clear and `expect(api.calls).toEqual([])` failed.
- **Fix:** the test waits for the home page's requests to settle before the log is cleared (`page.waitForLoadState('networkidle')`:
  no request in flight for 500 ms; it works for admin1, whose page makes no call, and for provider1). No assertion was changed.
```diff
       await expect(page.getByRole('navigation').getByRole('link').first()).toBeVisible();
+      // Q-097: the portal home's own calls (GET /api/v1/applications, GET /api/v1/providers/{npi}) can still be in flight here; let them settle before the log is cleared
+      await page.waitForLoadState('networkidle');
       api.calls.length = 0;
```
- **Blobs:** previous `ab8f073c3bb15a844b11d1f99dfe52ae71629346`, new `141cf94f5d4158e7ba68b848168e969c578bb493`. A plain `diff`
  (no `--strip-trailing-cr`) against the previous blob shows only the two added lines. The other four files of the story are
  unchanged and are re-recorded with the same content. Production code unchanged.
- **Runs (2026-11-19):** `npx playwright test e2e/console-approval.spec.ts -g "access-denied page and no provider data" --repeat-each=10`:
  20 of 20 pass (admin1 and provider1, 10 times each; before the fix the provider1 case failed 1 of 5 runs). Full Playwright (all 15
  default specs in two batches, `--workers=2`): 143 of 146; the corrected test passes. `npx eslint ... --max-warnings 0` and
  `npx tsc -b`: no findings.
- **Hashes:** re-recorded with `test_protection.py record MAM-87` (same 5 files); `check MAM-87` printed OK right after, and `sha256sum -c MAM-87.tests.sha256` is OK for all 5 files
  (a later `check` also lists the unrecorded test files of another story, `admin-risk`, as NEW; that is not a MAM-87 change).
- **Open, not part of this correction (found by the full runs):** two Vitest tests in `approval-reach.test.tsx` and one Playwright
  test in `console-approval.spec.ts` ("[MAM-87 AC-1] ... NPI of a Provider search result links to the provider detail (MAM-77 AC-2)"
  / "in Provider search the NPI of the result is a link to the provider detail") now fail with "The search could not be completed".
  Their stub answers `GET /api/v1/providers/{npi}`; the Provider search page of MAM-77 (in build) calls
  `GET /api/v1/providers?npi=` and never `GET /api/v1/providers/{npi}` (MAM-77 tests: provider-search.test.tsx header). It is
  the same kind of case as the MAM-64 one (superseded by a later story); left unchanged because it is outside the three
  corrections of this task and MAM-77 is still being built. Needs the stub to answer `GET /api/v1/providers?npi=...` with a
  provider page containing the same provider.

## Test correction under Q-097 C (superseded by MAM-77; user decision 2026-11-19)

Standing rule Q-097 C, explicitly approved by the user for this case. It closes the "Open" finding above and also replaces the
`networkidle` wait of the racy-test correction above (reviewer note: Playwright discourages `waitForLoadState('networkidle')`).
No assertion was weakened or removed; production code unchanged.

**1. Search stubs (superseded by MAM-77).** MAM-77's Provider search calls `GET /api/v1/providers?npi=` (or `medicaidId=` / `name=`)
and never `GET /api/v1/providers/{npi}`. The two MAM-87 tests that reach the approval page from a search result stubbed only the
single-provider lookup, so the search ended in "The search could not be completed". The stubs now also answer the search
(`GET /api/v1/providers`) with a provider page that holds the same provider (the page shape of MAM-77's `tests/support/search.ts`
and `e2e/console-search.spec.ts`: `{ items, page: 0, size: 20, total: 1 }`). The single-provider stub stays, because the detail
page that the link opens reads it. Flip of a recorded assertion: none (no recorded MAM-87 assertion says that the single-provider
lookup is the search call).

`tests/console/approval-reach.test.tsx` (CRLF), 2 tests: "[MAM-87 AC-1] the NPI in the result row is a link ... and is still shown
in full" and "[MAM-87 AC-1] following the link opens the provider detail page of that provider" (both use `searchFor`)
```diff
       ...approvalWorld(ALPHA, passedScreening(npi)).routes,
+      'GET /api/v1/providers': () => response(200, { items: [ALPHA], page: 0, size: 20, total: 1 }),
       [`GET /api/v1/providers/${npi}`]: () => response(200, ALPHA),
```
`e2e/console-approval.spec.ts` (CRLF), "[MAM-87 AC-1] in Provider search the NPI of the result is a link to the provider detail"
```diff
-    await mockApi(page, world(RIVERA, PASSED).routes);
+    await mockApi(page, {
+      ...world(RIVERA, PASSED).routes,
+      'GET /api/v1/providers': () => json(200, { items: [RIVERA], page: 0, size: 20, total: 1 }),
+    });
     await signIn(page, 'analyst1', '/console/providers');
```

**2. Racy AC-6 test: a deterministic wait instead of `networkidle`.** Test "[MAM-87 AC-6] provider1 sees the access-denied page and no
provider data is fetched" (the loop also runs admin1). The portal home of provider1 makes two calls when it mounts
(`GET /api/v1/applications` and `GET /api/v1/providers/{npi}` of the token's NPI; `retry: false` in `main.tsx`, so no later
retries). The test now clears the call log only after both calls were recorded by the stub, so neither can arrive after the clear.
admin1's page (`/console/providers`) makes no call on open, so nothing is awaited for admin1. The provider1 NPI claim is
1003000111 (`infra/keycloak/realm-pem.json`), the same as `RIVERA_NPI`. No assertion changed.
```diff
-      // Q-097: the portal home's own calls (GET /api/v1/applications, GET /api/v1/providers/{npi}) can still be in flight here; let them settle before the log is cleared
-      await page.waitForLoadState('networkidle');
+      // Q-097 C: the portal home of provider1 makes two calls when it mounts (GET /api/v1/applications and GET /api/v1/providers/{npi} of the
+      // token's NPI); the log is cleared only after both reached the stub, so that neither can arrive after the clear. admin1's page makes no call.
+      if (who === 'provider1') {
+        await expect.poll(() => ['/api/v1/applications', `/api/v1/providers/${RIVERA_NPI}`].every((path) => api.calls.some((call) => call.path === path))).toBe(true);
+      }
```

| File | Previous blob | New blob | Line endings |
|---|---|---|---|
| `tests/console/approval-reach.test.tsx` | `b78072a87fd37b2f678598d6029cd854762e5cf8` | `34a4b787a6f030c1713d3a8215b12052c7bdc9cd` | CRLF (184/184) |
| `e2e/console-approval.spec.ts` | `141cf94f5d4158e7ba68b848168e969c578bb493` | `ba73782900889e6403a9bf7980436db449c3d93f` | CRLF (459/459) |

A plain `diff` (no `--strip-trailing-cr`) of each previous blob (`git cat-file -p <blob>`) against the new file shows only the lines
above (1 line in approval-reach, 12 in console-approval; the earlier `141cf94...` already held the correction of the racy test).
Hashes re-recorded with `test_protection.py record MAM-87` (same 5 files); `check MAM-87` prints OK; `sha256sum -c` OK.

Runs: Vitest `approval-reach.test.tsx` 17/17 (the 2 search tests were red, "The search could not be completed"); Playwright
`console-approval.spec.ts` 28/28; `-g "access-denied page and no provider data" --repeat-each=10` 20/20 (admin1 and provider1 10 times each).
Full Vitest (`--maxWorkers=2 --testTimeout=30000 --hookTimeout=30000`): 647/647 in 29 files. Full default Playwright (all 16 specs in two batches,
`--workers=2`): 152/152. `npx eslint ... --max-warnings 0`: no findings.

## Test correction under Q-097 (UI polish B-iv, user decision 2026-11-19)

Cause: **the UI polish of 2026-11-19 (B-iv)**, under the Q-097 rule and the user's explicit permission to use it for any selector change. The key-value tables "Provider record", "Provider", "Application" and the portal home's "My provider record" are now USWDS summary lists (`<section aria-labelledby>`, a region named by its title, holding a `<dl>` of `div > dt + dd`); the label "Screen flag" is "Screening result"; the screening sources are shown by name (Licence board, OIG exclusion list (LEIE), SAM.gov debarment, SSA death records); and the status page has a step indicator whose step-2 segment reads "Validated" ("Validation failed" for a rejected application). Scoping and renaming only: no assertion was weakened or removed; production code unchanged. Test titles keep their wording unless said otherwise.

What changed in MAM-87:
- `approval-page.test.tsx`: `resultRow()` finds the row by the name the page shows for the source ("Licence board", "OIG exclusion list (LEIE)", "SAM.gov debarment", "SSA death records");
  "Screen flag" is "Screening result"; the AC-7 test that the status and the flag are text plus code finds each value as the `dd` in the region "Provider" (it found table cells).
- `approval-reach.test.tsx`: "Screen flag" is "Screening result" (two places).
- `console-approval.spec.ts`: `openApproval` waits for the provider name as a `dd` of the region "Provider" (one line that failed 22 tests); `valueOf` reads the `dd` of the row in the region
  "Provider" (the detail page also shows "Provider record" with the same labels); "Screen flag" is "Screening result" (3); the screening table rows are found by "Licence board"; the AC-7
  lines that found cells "Pending (P)" and "Pass (P)" find the text in the region "Provider".

Exact changes (`-` before, `+` after; the previous blob is the state at the start of this correction, line endings kept, a plain `diff` against it shows only these lines):

| File | Previous blob | New blob | Line endings | Changed lines |
|---|---|---|---|---|
| `tests/console/approval-page.test.tsx` | `bdb8de4f2f3470d7cf3f6cf41f19234c698cbda1` | `1da52f6131bc48f1727280b59fc534c4cf9d972c` | LF | 25 |
| `tests/console/approval-reach.test.tsx` | `34a4b787a6f030c1713d3a8215b12052c7bdc9cd` | `b7b3e0af80e047bc485ce4420ac3a51c68f709d9` | CRLF | 4 |
| `e2e/console-approval.spec.ts` | `ba73782900889e6403a9bf7980436db449c3d93f` | `88bd21ca51fbb708fd29cb3f08c979341872918a` | CRLF | 20 |

`tests/console/approval-page.test.tsx` (LF)
```diff
@@ -46 +46 @@
-/** Cell texts of the row of one source, keyed by column header. */
+/** Cell texts of the row of one source, found by the name the page shows for it (UI polish: names, not codes), keyed by column header. */
@@ -65 +65 @@
-    expect(fieldValue('Screen flag')).toBe('Pass (P)');
+    expect(fieldValue('Screening result')).toBe('Pass (P)');
@@ -84,2 +84,2 @@
-    expect(bodyRows(table).map((row) => text(row.children[0]))).toEqual(['LICBOARD', 'OIGLEIE', 'SAMGOV', 'SSADMF']);
-    const licence = resultRow('LICBOARD');
+    expect(bodyRows(table).map((row) => text(row.children[0]))).toEqual(['Licence board', 'OIG exclusion list (LEIE)', 'SAM.gov debarment', 'SSA death records']);
+    const licence = resultRow('Licence board');
@@ -89 +89 @@
-    for (const name of ['OIGLEIE', 'SAMGOV', 'SSADMF']) expect(resultRow(name)['Result']).toBe('Pass (P)');
+    for (const name of ['OIG exclusion list (LEIE)', 'SAM.gov debarment', 'SSA death records']) expect(resultRow(name)['Result']).toBe('Pass (P)');
@@ -96 +96 @@
-    const row = resultRow('OIGLEIE');
+    const row = resultRow('OIG exclusion list (LEIE)');
@@ -107 +107 @@
-    expect(resultRow('LICBOARD')['Result']).toBe('Conditional (C)');
+    expect(resultRow('Licence board')['Result']).toBe('Conditional (C)');
@@ -115 +115 @@
-    expect(fieldValue('Screen flag')).toBe('Fail (F)');
+    expect(fieldValue('Screening result')).toBe('Fail (F)');
@@ -124 +124 @@
-    expect(resultRow('LICBOARD')['Detail']).toBe('Newer result (test)');
+    expect(resultRow('Licence board')['Detail']).toBe('Newer result (test)');
@@ -209 +209 @@
-    expect(fieldValue('Screen flag')).toBe('Unscreened (U)');
+    expect(fieldValue('Screening result')).toBe('Unscreened (U)');
@@ -293,2 +293,3 @@
-    expect(screen.getByRole('cell', { name: 'Pending (P)' })).toBeInTheDocument();
-    expect(within(screen.getByRole('table', { name: 'Provider' })).getByRole('cell', { name: 'Pass (P)' })).toBeInTheDocument();
+    const providerRegion = within(screen.getByRole('region', { name: 'Provider' }));
+    expect(providerRegion.getByText('Pending (P)', { selector: 'dd' })).toBeInTheDocument();
+    expect(providerRegion.getByText('Pass (P)', { selector: 'dd' })).toBeInTheDocument();
```

`tests/console/approval-reach.test.tsx` (CRLF)
```diff
@@ -47 +47 @@
-    expect(fieldValue('Screen flag')).toBe('Pass (P)');
+    expect(fieldValue('Screening result')).toBe('Pass (P)');
@@ -76 +76 @@
-    expect(fieldValue('Screen flag')).toBe('Unscreened (U)');
+    expect(fieldValue('Screening result')).toBe('Unscreened (U)');
```

`e2e/console-approval.spec.ts` (CRLF)
```diff
@@ -116 +116 @@
-  await expect(page.getByRole('cell', { name })).toBeVisible();
+  await expect(page.getByRole('region', { name: 'Provider', exact: true }).locator('dd').filter({ hasText: name })).toBeVisible();
@@ -125,2 +125,2 @@
-/** Value of a row of the provider table: <td>label</td><td>value</td>. */
-const valueOf = (page: Page, label: string): Locator => page.getByRole('row').filter({ has: page.getByRole('cell', { name: label, exact: true }) }).getByRole('cell').nth(1);
+/** Value of a row of the list "Provider" (UI polish, Q-097): <div><dt>label</dt><dd>value</dd></div>. The detail page also shows the list "Provider record", so the region is named. */
+const valueOf = (page: Page, label: string): Locator => page.getByRole('region', { name: 'Provider', exact: true }).locator('dl > div').filter({ has: page.getByText(label, { exact: true }) }).locator('dd');
@@ -143 +143 @@
-    await expect(valueOf(page, 'Screen flag')).toHaveText('Pass (P)');
+    await expect(valueOf(page, 'Screening result')).toHaveText('Pass (P)');
@@ -145,2 +145,2 @@
-    await expect(table.getByRole('row')).toHaveCount(5); // header + LICBOARD, OIGLEIE, SAMGOV, SSADMF
-    await expect(table.getByRole('row').filter({ hasText: 'LICBOARD' }).getByRole('cell').nth(1)).toHaveText('Pass (P)');
+    await expect(table.getByRole('row')).toHaveCount(5); // header + Licence board, OIG exclusion list (LEIE), SAM.gov debarment, SSA death records
+    await expect(table.getByRole('row').filter({ hasText: 'Licence board' }).getByRole('cell').nth(1)).toHaveText('Pass (P)');
@@ -155 +155 @@
-    await expect(valueOf(page, 'Screen flag')).toHaveText('Unscreened (U)');
+    await expect(valueOf(page, 'Screening result')).toHaveText('Unscreened (U)');
@@ -255 +255 @@
-    await expect(valueOf(page, 'Screen flag')).toHaveText('Unscreened (U)');
+    await expect(valueOf(page, 'Screening result')).toHaveText('Unscreened (U)');
@@ -425,2 +425,2 @@
-    await expect(page.getByRole('cell', { name: 'Pending (P)' })).toBeVisible();
-    await expect(page.getByRole('table', { name: 'Provider' }).getByRole('cell', { name: 'Pass (P)' })).toBeVisible();
+    await expect(page.getByRole('region', { name: 'Provider', exact: true }).getByText('Pending (P)', { exact: true })).toBeVisible();
+    await expect(page.getByRole('region', { name: 'Provider', exact: true }).getByText('Pass (P)', { exact: true })).toBeVisible();
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
| `e2e/console-approval.spec.ts` | `88bd21ca51fbb708fd29cb3f08c979341872918a` | `87c0cccccee832d8014130527f097411570f228f` | CRLF | 3 / 3 |
| `tests/console/approval-decision.test.tsx` | `f4b9af7d6be169f735866a00b08f3149214cd2c9` | `1f4672c25e83d498434755730086338e568c8fdb` | CRLF | 4 / 4 |
| `tests/console/approval-page.test.tsx` | `1da52f6131bc48f1727280b59fc534c4cf9d972c` | `dd70ae02d849dfd0478f2c3e1f3f57de39447c48` | LF | 1 / 1 |

`e2e/console-approval.spec.ts` (CRLF)
```diff
@@ -187 +187 @@ test.describe('[MAM-87 AC-2] Approve makes the provider Active (journey J1)', ()
-  test('[MAM-87 AC-2] analyst1 approves 1003000111: POST with decision APPROVE, then Active (A) with the due date 03/05/2031', async ({ page }) => {
+  test('[MAM-87 AC-2] analyst1 approves 1003000111: POST with decision APPROVE, then Active (A) with the due date 5 Mar 2031', async ({ page }) => {
@@ -194 +194 @@ test.describe('[MAM-87 AC-2] Approve makes the provider Active (journey J1)', ()
-    await expect(page.getByText(/Decision recorded/)).toContainText('03/05/2031');
+    await expect(page.getByText(/Decision recorded/)).toContainText('5 Mar 2031');
@@ -196 +196 @@ test.describe('[MAM-87 AC-2] Approve makes the provider Active (journey J1)', ()
-    await expect(valueOf(page, 'Revalidation due')).toHaveText('03/05/2031');
+    await expect(valueOf(page, 'Revalidation due')).toHaveText('5 Mar 2031');
```

`tests/console/approval-decision.test.tsx` (CRLF)
```diff
@@ -69 +69 @@ describe('[MAM-87 AC-2] Approve makes the provider Active (journey J1)', () => {
-  it('[MAM-87 AC-2] the page confirms Active (A) and the revalidation due date five years later: approval on 03/05/2026 shows 03/05/2031', async () => {
+  it('[MAM-87 AC-2] the page confirms Active (A) and the revalidation due date five years later: approval on 5 Mar 2026 shows 5 Mar 2031', async () => {
@@ -77 +77 @@ describe('[MAM-87 AC-2] Approve makes the provider Active (journey J1)', () => {
-    expect(text(confirmation)).toContain('03/05/2031');
+    expect(text(confirmation)).toContain('5 Mar 2031');
@@ -79 +79 @@ describe('[MAM-87 AC-2] Approve makes the provider Active (journey J1)', () => {
-    expect(fieldValue('Revalidation due')).toBe('03/05/2031');
+    expect(fieldValue('Revalidation due')).toBe('5 Mar 2031');
@@ -90 +90 @@ describe('[MAM-87 AC-2] Approve makes the provider Active (journey J1)', () => {
-    expect(fieldValue('Revalidation due')).toBe('04/09/2031');
+    expect(fieldValue('Revalidation due')).toBe('9 Apr 2031');
```

`tests/console/approval-page.test.tsx` (LF)
```diff
@@ -88 +88 @@ describe('[MAM-87 AC-1] the approval page shows the provider and its screening r
-    expect(Object.entries(licence).find(([header]) => /^Verdict/.test(header))?.[1]).toContain('03/02/2026');
+    expect(Object.entries(licence).find(([header]) => /^Verdict/.test(header))?.[1]).toContain('2 Mar 2026');
```

Test titles that named the old format were corrected for honesty (AC tags kept); typed date inputs were not touched.

**Runs (2026-11-19, one worker because the host was short of memory):** before the correction the full Vitest suite (`--maxWorkers=1 --testTimeout=60000 --hookTimeout=60000`, PowerShell, lowercase path) had **36 failures in 15 files (611 of 647 pass)**: 35 are exactly the lines of the A1/A2 list; the 36th is a timing failure in an unedited file, see the MAM-64 note. After the correction: **647 of 647 pass in 29 files**. `npx tsc -b` (it includes `tests` and `e2e`) and `eslint --max-warnings 0` on the 24 changed files: no findings.
The full default Playwright suite (16 specs, journeys excluded, `--workers=1`, `NODE_OPTIONS=--dns-result-order=ipv4first`, one run): **152 of 152 pass** (8.4 min). The Playwright suite was not run before the correction (no time; the UI agent's static list was used), so there is no before count for it.

**Hashes:** re-recorded with `test_protection.py record MAM-87` with exactly the files of the story's `.tests.sha256`; `check` and `sha256sum -c` are OK.

Record correction (lead, 2026-11-19 20:58, code-reviewer round 2): line(s) 268, 269, 272, 285 labelled the file LF; it is CRLF (byte check by the reviewer). Only the label changed; the test file and its hash are unchanged.
