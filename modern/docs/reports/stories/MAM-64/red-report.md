# MAM-64 red report: Portal, apply for enrolment online (field errors E101 to E104)

Run on 2026-11-19, before any production code of the story existed (the routes /portal/apply,
/portal/applications/:applicationId and /portal/provider were placeholders).

Test hashes: `modern/docs/reports/stories/MAM-64.tests.sha256` (8 files), restore copies in `red-blobs.txt`.
`npx tsc -b` and `npx eslint tests e2e --max-warnings 0` pass for the new files.

## Result of the red run

| Level | Command | Tests | Result | Reason (all the same family: the page is missing) |
|---|---|---|---|---|
| Vitest | `npx vitest run tests/portal/apply-*.test.tsx` | 101 | 101 failed | 91 x `Unable to find role="button" and name "Submit application"` (placeholder page, no form); 10 x expected text not found on the placeholder pages (`No provider record exists yet`, `Validated`, `Rejected`, `1003000111`, `Application not found`, `could not be loaded`) |
| Playwright | `npx playwright test e2e/portal-apply.spec.ts --workers=4` | 25 | 25 failed | 25 x `expect(getByRole('button', { name: 'Submit application' })).toBeVisible()` failed (real Keycloak sign-in as provider1 worked; the form does not exist) |

No test failed for a test bug (typing, lint, a wrong helper) in the recorded run; two own test bugs were fixed before recording
(a regex helper in the e2e spec, an exact match of an accessible description that also holds the hint).

## AC to test

| AC | Vitest | Playwright |
|---|---|---|
| AC-1 | `apply-form.test.tsx` (labels, types, max lengths, hints, type list, group / facility switch, Submit button last) | `[MAM-64 AC-1]` x 3 |
| AC-2 | `apply-submit.test.tsx` (POST body, headers, redirect, stale list cache, group body), `apply-status-page.test.tsx` | `[MAM-64 AC-2]` |
| AC-3 | `apply-submit.test.tsx` (request sent, summary at the top, link, focus, field error, typed values kept, no success page, API message) | `[MAM-64 AC-3]` |
| AC-4 | `apply-submit.test.tsx` (a) to (d), link focus per code, second rejection replaces the first, unknown field | `[MAM-64 AC-4]` x 5 |
| AC-5 | `apply-followup.test.tsx`, `apply-status-page.test.tsx` | `[MAM-64 AC-5]` |
| AC-6 | `apply-failures.test.tsx` (400, 401, 403, 500, 503, network, 422 without errors) | `[MAM-64 AC-6]` x 5 |
| AC-7 | `apply-failures.test.tsx` (focus, tab order, Enter) | `[MAM-64 AC-7]` x 9 (axe on empty, group, one error, two errors, 500; labels; focus; keyboard only x 2) |

## Reading of the spec that the tests fix (decisions to confirm)

1. Before a type is chosen the form shows the individual inputs (as the wireframe does); Group / Facility swap them for the organisation inputs.
2. A 401 on the submit does not end the session by itself: the summary says to sign in again, keeps what was typed and offers a
   "Sign in again" button (AC-6 says the form is kept; the shared client ends the session for a 401 on a GET, MAM-62 AC-6).
3. The status page reads GET /api/v1/applications and picks the item by applicationId (there is no GET /api/v1/applications/{id} yet).

## Test change after review (2026-11-19, code review MUST-FIX 1)

- **Files:** `modern/web-ui/tests/portal/apply-failures.test.tsx` (400 fixture line 45, assertion line 84) and
  `modern/web-ui/e2e/portal-apply.spec.ts` (400 case line 258).
- **Fault (in the tests):** the 400 fixture's detail "The request is not valid." was the same text as the page's own fallback
  (`src/features/portal/apply/applicationForm.ts:137`), so "[MAM-64 AC-6] 400: the detail of the answer is shown" could not
  fail if the page stopped showing `problem.detail`.
- **Fix:** both fixtures now send the distinctive detail "licenseState must be 2 letters (detail sent by this test)" and the
  tests assert that exact text. Nothing was weakened; production code unchanged.
- **Runs:** Vitest apply-failures.test.tsx 37/37; Playwright portal-apply.spec.ts -g "AC-6" 5/5.
- **Hashes:** re-recorded with `test_protection.py record MAM-64` (same 8 files).
- **Line endings (found by the Q-093 confirmation review):** the edit above first rewrote both files from CRLF to LF. CRLF
  was restored; a byte-level diff against the previously recorded blobs now shows only the intended lines.
  Previous blobs: apply-failures.test.tsx `ba9929acb0239a227acea90b9b479770d221816e`, portal-apply.spec.ts
  `5ed0a1cb1f3eca34310e28d7d87ef23081f88275`. Hashes re-recorded again.

## Test correction under Q-097 (user decision 2026-11-19)

Q-097 option A (and C as a standing rule): the test-writer corrects recorded tests that a later story supersedes. Cause here:
**superseded by MAM-66.** MAM-66 AC-1 requires the status page `/portal/applications/:applicationId` to call
`GET /api/v1/applications/{applicationId}` (built in MAM-54, deployed). Decision 3 above ("there is no GET by id yet") was the
interim reading; the page now calls by id, so the MAM-64 stubs, which answered only `GET /api/v1/applications`, gave 404.
Before the correction 15 Vitest tests failed (apply-status-page 5, apply-submit 3, apply-failures 6, apply-followup 1) and 4
Playwright tests in portal-apply.spec.ts (AC-2, AC-4 corrected application, AC-5, AC-7 keyboard only).

- **Correction:** the stubs also answer `GET /api/v1/applications/{id}` with the same application as the list stub (404 where the
  test expects "not found", 500 where it is about a load failure). The list stubs stay. The shared support files
  (`tests/support/applyForm.ts`, `e2e/support/gateway-routes.ts`) did not need a change; the stubs live in the test files.
- **Assertions:** nothing was weakened or removed. The two assertions that expressed the interim reading in
  apply-status-page.test.tsx ("the list was read" and "no by-id call") are flipped to the behaviour MAM-66 requires, the same
  one the passing MAM-66 e2e asserts (`e2e/portal-status.spec.ts:43-44`: by id called, list not called). Every other assertion
  is byte-identical. Three test titles that named the list were reworded; the tags `[MAM-64 AC-n]` are unchanged.
- **Production code:** unchanged.

Exact changes (`diff` of the previous blob against the new file, line endings kept; `-` before, `+` after):

`tests/portal/apply-status-page.test.tsx` (LF)
```diff
-// (/portal/applications/:applicationId shows 'Validated' or 'Rejected'). There is no GET /api/v1/applications/{id} yet,
-// so the page reads GET /api/v1/applications (a PROVIDER sees only their own) and picks the item by applicationId.
+// (/portal/applications/:applicationId shows 'Validated' or 'Rejected'). Corrected under Q-097 (2026-11-19): since MAM-66 AC-1
+// the page reads GET /api/v1/applications/{id} (MAM-54), so the stubs answer that too (404 for an unknown id).
+const BY_ID = `GET /api/v1/applications/${APPLICATION_ID}`;
-    const { main } = await openStatus(APPLICATION_ID, { [LIST_ROUTE]: () => response(200, applicationPage([application({ status: 'VALIDATED' })])) });
+    const { main } = await openStatus(APPLICATION_ID, {
+      [LIST_ROUTE]: () => response(200, applicationPage([application({ status: 'VALIDATED' })])),
+      [BY_ID]: () => response(200, application({ status: 'VALIDATED' })),
+    });
-    const { main } = await openStatus(APPLICATION_ID, { [LIST_ROUTE]: () => response(200, applicationPage([application({ status: 'REJECTED' })])) });
+    const { main } = await openStatus(APPLICATION_ID, {
+      [LIST_ROUTE]: () => response(200, applicationPage([application({ status: 'REJECTED' })])),
+      [BY_ID]: () => response(200, application({ status: 'REJECTED' })),
+    });
-    const { main } = await openStatus(APPLICATION_ID, { [LIST_ROUTE]: () => response(200, applicationPage([application()])) });
+    const { main } = await openStatus(APPLICATION_ID, {
+      [LIST_ROUTE]: () => response(200, applicationPage([application()])),
+      [BY_ID]: () => response(200, application()),
+    });
-  it('[MAM-64 AC-2] the page picks its application by id from GET /api/v1/applications and shows nothing of the other applications', async () => {
+  it('[MAM-64 AC-2] the page reads its application by id from GET /api/v1/applications/{id} and shows nothing of the other applications', async () => {
+      [BY_ID]: () => response(200, application({ status: 'VALIDATED' })),
-    expect(api.to('GET', '/api/v1/applications').length).toBeGreaterThan(0);
-    expect(api.calls.filter((call) => call.url.pathname.startsWith(`/api/v1/applications/`))).toHaveLength(0);
+    expect(api.to('GET', '/api/v1/applications')).toHaveLength(0);
+    expect(api.to('GET', `/api/v1/applications/${APPLICATION_ID}`).length).toBeGreaterThan(0);
-  it('[MAM-64 AC-2] an id that is not in the list of the provider shows "Application not found"', async () => {
-    const { main } = await openStatus(OTHER_ID, { [LIST_ROUTE]: () => response(200, applicationPage([application()])) });
+  it('[MAM-64 AC-2] an id that the service does not know (404) shows "Application not found"', async () => {
+    const { main } = await openStatus(OTHER_ID, {
+      [LIST_ROUTE]: () => response(200, applicationPage([application()])),
+      [`GET /api/v1/applications/${OTHER_ID}`]: () => response(404, problem(404, 'Not Found')),
+    });
-  it('[MAM-64 AC-2] when the list cannot be loaded the page says so and shows no status', async () => {
-    const { main } = await openStatus(APPLICATION_ID, { [LIST_ROUTE]: () => response(500, problem(500, 'Internal Server Error')) });
+  it('[MAM-64 AC-2] when the application cannot be loaded the page says so and shows no status', async () => {
+    const { main } = await openStatus(APPLICATION_ID, {
+      [LIST_ROUTE]: () => response(500, problem(500, 'Internal Server Error')),
+      [BY_ID]: () => response(500, problem(500, 'Internal Server Error')),
+    });
```

`tests/portal/apply-submit.test.tsx` (LF)
```diff
-/** The service accepts the application; from then on the list of the provider contains it. */
+/** The service accepts the application; from then on the list of the provider contains it and GET by id returns it (MAM-66 AC-1, Q-097). */
+    [`GET /api/v1/applications/${APPLICATION_ID}`]: () => (created ? response(200, CREATED) : response(404, { title: 'Not Found', status: 404 })),
```

`tests/portal/apply-failures.test.tsx` (CRLF)
```diff
-  const rendered = await openApplyForm({ [SUBMIT_ROUTE]: answer, [LIST_ROUTE]: () => response(200, applicationPage([application()])) });
+  const rendered = await openApplyForm({
+    [SUBMIT_ROUTE]: answer,
+    [LIST_ROUTE]: () => response(200, applicationPage([application()])),
+    [`GET /api/v1/applications/${APPLICATION_ID}`]: () => response(200, application()),
+  });
```

`tests/portal/apply-followup.test.tsx` (LF)
```diff
+  [`GET /api/v1/applications/${APPLICATION_ID}`]: () => response(200, REJECTED),
```

`e2e/portal-apply.spec.ts` (CRLF; the four routes are in AC-2, AC-5, AC-7 keyboard only and AC-4 corrected application)
```diff
+/** MAM-66 AC-1 (Q-097): the status page reads GET /api/v1/applications/{id} (MAM-54); the stubs answer it as they answer the list. */
+const BY_ID = `GET /api/v1/applications/${APPLICATION_ID}`;
+      [BY_ID]: () => (created ? json(200, VALIDATED) : json(404, { title: 'Not Found', status: 404 })),   (AC-2, after the list route)
+      [BY_ID]: () => json(200, REJECTED),                                                                  (AC-5, after the list route)
+      [BY_ID]: () => (created ? json(200, VALIDATED) : json(404, { title: 'Not Found', status: 404 })),   (AC-7 keyboard only)
+    [BY_ID]: () => (created ? json(200, VALIDATED) : json(404, { title: 'Not Found', status: 404 })),     (AC-4 corrected application)
```

| File | Previous blob | New blob | Line endings |
|---|---|---|---|
| `tests/portal/apply-status-page.test.tsx` | `0c1d5e2a3effae1908e073af75718eb8eb3278f6` | `7590903ba72e0bc9ef952591c680b255c9c31622` | LF |
| `tests/portal/apply-submit.test.tsx` | `7762ff862d8271c789d9be57ebc1a70a25e73c6e` | `9be2a0625064fa4fa6c9490c578f8c52eb171e7d` | LF |
| `tests/portal/apply-failures.test.tsx` | `4d8f148732570b5d87052c3eb962276c24c680f8` | `efe2df280bda6269d302ba44bb639bd84abc47cb` | CRLF |
| `tests/portal/apply-followup.test.tsx` | `7c51142249b0f79a9de99c59a011cac04a5a208c` | `b8cb3f30792290e3fa58d5d2ea84d22c9782f178` | LF |
| `e2e/portal-apply.spec.ts` | `089e6719065dde7d59825fdff6f440855d8d75a7` | `83d9255a78b905754c0d2c60e99c3c86106a09b7` | CRLF |

Unchanged and re-recorded with the same content: `apply-form.test.tsx`, `tests/support/applyForm.ts`, `e2e/support/gateway-routes.ts`.
A plain `diff` (no `--strip-trailing-cr`) of each previous blob (`git cat-file -p <blob>`) against the new file shows only the lines above.

Runs after the correction (2026-11-19, machine at 100 % CPU while other agents built stories):
- Vitest: apply-status-page 6/6, apply-submit + apply-followup 33/33, apply-failures 37/37 when run alone. In one combined run
  the first test of apply-failures hit the 5000 ms default timeout under load (cold start, not an assertion); the same file
  alone passed 37/37 with the first test taking 2.7 s.
- Playwright: `npx playwright test e2e/portal-apply.spec.ts` 25/25 (the 4 corrected tests included).
- Full Vitest (`--maxWorkers=2 --testTimeout=30000 --hookTimeout=30000`): 553 of 555 pass; all MAM-64 tests pass (the 2 failures are
  in MAM-87 `approval-reach.test.tsx`, see the MAM-87 report). A full run with the default 5 s timeout and parallel workers
  on the loaded machine gave 70 failures, 44 of them `TestTimeoutError` (not a test fault).
- Full Playwright (all 15 default specs, run in two batches with `--workers=2`): 143 of 146 pass; no MAM-64 test fails.
- `npx eslint` on the five changed files `--max-warnings 0` and `npx tsc -b`: no findings.
- **Hashes:** re-recorded with `test_protection.py record MAM-64` (same 8 files); `check MAM-64` printed OK right after, and `sha256sum -c MAM-64.tests.sha256` is OK for all 8 files
  (a later `check` also lists the unrecorded test files of another story, `admin-risk`, as NEW; that is not a MAM-64 change).

## Test correction under Q-097 (breadcrumb nav, user decision Q-101, 2026-11-19)

Cause: **the UI will get a real USWDS breadcrumb, `<nav aria-label="Breadcrumb">`**, so there will be two navigation landmarks. Two MAM-64
queries read "the navigation" without a name. Scoping only: both now select the main menu landmark, `<PrimaryNav aria-label="Primary navigation">`
(`src/app/AppShell.tsx:47`). No assertion weakened or removed; production code unchanged. No breadcrumb exists today, so the tests pass now
and the scoping keeps them passing once it exists.

- `tests/portal/apply-followup.test.tsx:83` (LF): `screen.getByRole('navigation')` throws "multiple elements" as soon as a second landmark
  exists, so this test would have failed. It opens "My provider record" from the menu ("opening /portal and then My provider record says
  that no record exists yet").
- `e2e/portal-apply.spec.ts:253` (CRLF): `page.getByRole('navigation').getByRole('link', { name: 'My provider record' }).click()` would match
  twice if the breadcrumb held a link with the same name (strict mode). Same journey step, AC-5.

`tests/portal/apply-followup.test.tsx`
```diff
-    await user.click(within(screen.getByRole('navigation')).getByRole('link', { name: 'My provider record' }));
+    await user.click(within(screen.getByRole('navigation', { name: 'Primary navigation' })).getByRole('link', { name: 'My provider record' }));
```
`e2e/portal-apply.spec.ts`
```diff
-    await page.getByRole('navigation').getByRole('link', { name: 'My provider record' }).click();
+    await page.getByRole('navigation', { name: 'Primary navigation' }).getByRole('link', { name: 'My provider record' }).click();
```

| File | Previous blob | New blob | Line endings |
|---|---|---|---|
| `tests/portal/apply-followup.test.tsx` | `b8cb3f30792290e3fa58d5d2ea84d22c9782f178` | `35cd74949f9ef43ef47d35e211850aa12a8e0920` | LF (0 CR, 118 LF; unchanged) |
| `e2e/portal-apply.spec.ts` | `83d9255a78b905754c0d2c60e99c3c86106a09b7` | `207aa02caedebdcd830db660f205a20b167b8ff3` | CRLF (441/441; unchanged) |

(The previous blobs are the state after the Q-097 correction of this report's section above; the earlier ones are in that section.) A plain `diff` (no
`--strip-trailing-cr`) of each previous blob (`git cat-file -p <blob>`) against the new file shows exactly 1 changed line (2 with its old line).

Runs: Vitest `apply-followup.test.tsx` 7/7. Playwright `e2e/portal-apply.spec.ts` 25/25. A first run had 24 of 25: "[MAM-64 AC-7] the form for a group
has zero axe violations" did not get past sign-in because the web-ui container answered `500 Internal Server Error` (nginx page in the failure screenshot): a
stack hiccup, not a test or product fault; the rerun passed 25/25. `npx eslint ... --max-warnings 0` on both files: no findings.
**Hashes:** re-recorded with `test_protection.py record MAM-64` (the same 8 files; only `portal-apply.spec.ts` and `apply-followup.test.tsx` changed);
`check MAM-64` prints OK; `sha256sum -c` OK for all 8.

## Test correction under Q-097 (timeline wording, user decision Q-104)

Cause: **the status page has a progress list (`Timeline.tsx`), and the user wants step 2 to read "Validated"** ("Step 2 of 5: Validated - done";
today "Accepted", because recorded tests look the word up as a substring). Scoping only, nothing weakened: the lookups that would then match
twice (the status badge and the step) are narrowed to the status badge, which is the `Status` row of the `Application` table
(`ApplicationStatus.tsx`: `<caption>Application</caption>`, `<Tag>` in the Status cell). Production code unchanged.

**Why only Playwright lookups are ambiguous.** `getByText('Validated')` in Playwright is a case-insensitive substring match, so it finds the badge
and the step text. In Testing Library `findByText('Validated')` is an exact whole-text match, and the step "Step 2 of 5: Validated - done" is a
different, longer text, so it is not found.

**Changed: 4 lookups in `e2e/portal-apply.spec.ts` (CRLF), plus one helper line**
```diff
+/** The "Application" table of the status page: the status badge is looked up inside it, because the progress list above it names the steps too (Q-104). */
+const applicationTable = (page: Page) => page.getByRole('main').getByRole('table', { name: 'Application', exact: true });
```
| Was line | Test | Before | After |
|---|---|---|---|
| 171 | `[MAM-64 AC-2]` provider1 submits ..., then the status page shows Validated | `page.getByRole('main').getByText('Validated')` `.toBeVisible()` | `applicationTable(page).getByText('Validated')` `.toBeVisible()` |
| 251 | `[MAM-64 AC-5]` after the E104 rejection ..., the status page shows it | `page.getByRole('main').getByText('Rejected')` `.toBeVisible()` | `applicationTable(page).getByText('Rejected')` `.toBeVisible()` |
| 390 | `[MAM-64 AC-7]` the whole form is completed and sent with Tab, Space and Enter only | `page.getByRole('main').getByText('Validated')` `.toBeVisible()` | `applicationTable(page).getByText('Validated')` `.toBeVisible()` |
| 439 | `[MAM-64 AC-4]` after a rejection the corrected application is accepted and the old errors are gone | `page.getByRole('main').getByText('Validated')` `.toBeVisible()` | `applicationTable(page).getByText('Validated')` `.toBeVisible()` |

Exact diff (previous blob `207aa02caedebdcd830db660f205a20b167b8ff3`, new `6a3e4b46cba38d56a41c198fd20330c6e416c399`; CRLF 441/441 before, 443/443 after;
a plain `diff` shows 10 changed lines):
```diff
+/** The "Application" table of the status page: the status badge is looked up inside it, because the progress list above it names the steps too (Q-104). */
+const applicationTable = (page: Page) => page.getByRole('main').getByRole('table', { name: 'Application', exact: true });
-    await expect(page.getByRole('main').getByText('Validated')).toBeVisible();
+    await expect(applicationTable(page).getByText('Validated')).toBeVisible();
-    await expect(page.getByRole('main').getByText('Rejected')).toBeVisible();
+    await expect(applicationTable(page).getByText('Rejected')).toBeVisible();
-    await expect(page.getByRole('main').getByText('Validated')).toBeVisible();
+    await expect(applicationTable(page).getByText('Validated')).toBeVisible();
-  await expect(page.getByRole('main').getByText('Validated')).toBeVisible();
+  await expect(applicationTable(page).getByText('Validated')).toBeVisible();
```

**Reviewed and not changed**
- `e2e/portal-apply.spec.ts:201` `getByRole('main').getByText('Validated')` `.toHaveCount(0)` and `:279` `getByText(/Validated|Rejected/)` `.toHaveCount(0)`: both on the
  apply form page after a failed submit; no progress list there, and a count of 0 cannot become ambiguous.
- Vitest, all MAM-64 files (`apply-status-page`, `apply-submit`, `apply-failures`, `apply-followup`): every `findByText` / `queryByText('Validated' | 'Rejected')` is an exact
  whole-text match (the step text is longer, so it does not match). The `/Validated|Rejected/` regex lookups (`apply-failures.test.tsx:119`,
  `apply-status-page.test.tsx:95`, `apply-followup.test.tsx:107`) are on pages with no progress list (the apply form, a failed load, the provider record).
  Table-cell lookups (`cells[2]).toBe('Rejected')` on the portal home) are unaffected: the progress list is only on the status page.
- The Playwright journeys J1 / J2 use `getByText('Validated' | 'Rejected', { exact: true })`, a whole-text match: left alone.

**Shown with the new wording, without touching `src/`:**
- A throw-away copy of `src` and `tests` in the session scratchpad with step 2 renamed to "Validated" (`STEPS = ['Submitted', 'Validated', ...]`; a rejected
  application then reads "Step 2 of 5: Validated - not passed"): `tests/portal` 10 files, 193/193 pass, so no Vitest lookup needed a change. The copy was deleted afterwards.
- A static-HTML check of the new status-page markup in Chromium (also a scratchpad script, not in the repo): the old lookup `getByRole('main').getByText('Validated')`
  finds 2 elements (badge and step), `{ exact: true }` finds 1, and the narrowed `applicationTable(page).getByText('Validated')` finds 1. For a rejected
  application with step 2 reading "Rejected - not passed" (the worst case) the old `getByText('Rejected')` finds 2 and the narrowed one 1. The other word is never in the table.

Runs (2026-11-19, with today's wording "Accepted"): Playwright `portal-apply.spec.ts` 25/25 and `portal-status.spec.ts` 5/5 (30/30); `npx eslint ... --max-warnings 0`: no findings.
**Hashes:** re-recorded with `test_protection.py record MAM-64` (the same 8 files; only `portal-apply.spec.ts` changed, sha256 now `07f9b2cf...`); `check MAM-64` prints OK; `sha256sum -c` OK for all 8.

## Test correction under Q-097 (UI polish B-iv, user decision 2026-11-19)

Cause: **the UI polish of 2026-11-19 (B-iv)**, under the Q-097 rule and the user's explicit permission to use it for any selector change. The key-value tables "Provider record", "Provider", "Application" and the portal home's "My provider record" are now USWDS summary lists (`<section aria-labelledby>`, a region named by its title, holding a `<dl>` of `div > dt + dd`); the label "Screen flag" is "Screening result"; the screening sources are shown by name (Licence board, OIG exclusion list (LEIE), SAM.gov debarment, SSA death records); and the status page has a step indicator whose step-2 segment reads "Validated" ("Validation failed" for a rejected application). Scoping and renaming only: no assertion was weakened or removed; production code unchanged. Test titles keep their wording unless said otherwise.

What changed in MAM-64:
- Vitest (`apply-status-page`, `apply-submit`, `apply-failures`): the five lookups `findByText('Validated')` on the status page are scoped to the region "Application"
  (`within(await screen.findByRole('region', { name: 'Application' }))`), because the segment label of the step indicator is also "Validated" and Testing Library
  matches an element's own text. In `apply-status-page.test.tsx` the first test no longer binds `main` (it was unused).
- Playwright `portal-apply.spec.ts`: the helper `applicationTable` of the Q-104 correction is renamed `applicationRegion` and finds the region "Application"
  (`getByRole('region', { name: 'Application', exact: true })`); its four uses (AC-2, AC-5, AC-7 keyboard only, AC-4 corrected application) follow.

Exact changes (`-` before, `+` after; the previous blob is the state at the start of this correction, line endings kept, a plain `diff` against it shows only these lines):

| File | Previous blob | New blob | Line endings | Changed lines |
|---|---|---|---|---|
| `tests/portal/apply-status-page.test.tsx` | `7590903ba72e0bc9ef952591c680b255c9c31622` | `333ddf2daf8613368a4b960b7aa09718b65ac5be` | LF | 6 |
| `tests/portal/apply-submit.test.tsx` | `9be2a0625064fa4fa6c9490c578f8c52eb171e7d` | `ae5b109fad175599105de7e3d83a8aa8483ea7bd` | LF | 6 |
| `tests/portal/apply-failures.test.tsx` | `efe2df280bda6269d302ba44bb639bd84abc47cb` | `d5f49a03b5bec2cd131d5081a2667b194aec7a90` | CRLF | 2 |
| `e2e/portal-apply.spec.ts` | `6a3e4b46cba38d56a41c198fd20330c6e416c399` | `0a126f3508dd7cc16744e24e7d4034eb55d64be7` | CRLF | 12 |

`tests/portal/apply-status-page.test.tsx` (LF)
```diff
@@ -32 +32 @@
-    const { main } = await openStatus(APPLICATION_ID, {
+    await openStatus(APPLICATION_ID, {
@@ -38 +38 @@
-    expect(await main.findByText('Validated')).toBeInTheDocument();
+    expect(await within(await screen.findByRole('region', { name: 'Application' })).findByText('Validated')).toBeInTheDocument();
@@ -71 +71 @@
-    expect(await main.findByText('Validated')).toBeInTheDocument();
+    expect(await within(await screen.findByRole('region', { name: 'Application' })).findByText('Validated')).toBeInTheDocument();
```

`tests/portal/apply-submit.test.tsx` (LF)
```diff
@@ -89 +89 @@
-    expect(await within(screen.getByRole('main')).findByText('Validated')).toBeInTheDocument();
+    expect(await within(await screen.findByRole('region', { name: 'Application' })).findByText('Validated')).toBeInTheDocument();
@@ -103 +103 @@
-    expect(await within(screen.getByRole('main')).findByText('Validated')).toBeInTheDocument();
+    expect(await within(await screen.findByRole('region', { name: 'Application' })).findByText('Validated')).toBeInTheDocument();
@@ -113 +113 @@
-    await within(screen.getByRole('main')).findByText('Validated');
+    await within(await screen.findByRole('region', { name: 'Application' })).findByText('Validated');
```

`tests/portal/apply-failures.test.tsx` (CRLF)
```diff
@@ -131 +131 @@
-    expect(await within(screen.getByRole('main')).findByText('Validated')).toBeInTheDocument();
+    expect(await within(await screen.findByRole('region', { name: 'Application' })).findByText('Validated')).toBeInTheDocument();
```

`e2e/portal-apply.spec.ts` (CRLF)
```diff
@@ -85,2 +85,2 @@
-/** The "Application" table of the status page: the status badge is looked up inside it, because the progress list above it names the steps too (Q-104). */
-const applicationTable = (page: Page) => page.getByRole('main').getByRole('table', { name: 'Application', exact: true });
+/** The "Application" summary list (region) of the status page: the status badge is looked up inside it, because the step indicator above it names step 2 "Validated" too (Q-104, UI polish). */
+const applicationRegion = (page: Page) => page.getByRole('region', { name: 'Application', exact: true });
@@ -173 +173 @@
-    await expect(applicationTable(page).getByText('Validated')).toBeVisible();
+    await expect(applicationRegion(page).getByText('Validated')).toBeVisible();
@@ -253 +253 @@
-    await expect(applicationTable(page).getByText('Rejected')).toBeVisible();
+    await expect(applicationRegion(page).getByText('Rejected')).toBeVisible();
@@ -392 +392 @@
-    await expect(applicationTable(page).getByText('Validated')).toBeVisible();
+    await expect(applicationRegion(page).getByText('Validated')).toBeVisible();
@@ -441 +441 @@
-  await expect(applicationTable(page).getByText('Validated')).toBeVisible();
+  await expect(applicationRegion(page).getByText('Validated')).toBeVisible();
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
| `tests/portal/apply-status-page.test.tsx` | `333ddf2daf8613368a4b960b7aa09718b65ac5be` | `1b32aa94a7b3e02d26bc685a815644e238e53d35` | LF | 2 / 2 |

`tests/portal/apply-status-page.test.tsx` (LF)
```diff
@@ -51 +51 @@ describe('[MAM-64 AC-2][MAM-64 AC-5] the application status page', () => {
-  it('[MAM-64 AC-2] the page shows the NPI in full and the submission date as MM/DD/YYYY', async () => {
+  it('[MAM-64 AC-2] the page shows the NPI in full and the submission date as a plain date (1 Mar 2026)', async () => {
@@ -58 +58 @@ describe('[MAM-64 AC-2][MAM-64 AC-5] the application status page', () => {
-    expect(main.getByText('03/01/2026')).toBeInTheDocument();
+    expect(main.getByText('1 Mar 2026')).toBeInTheDocument();
```

Test titles that named the old format were corrected for honesty (AC tags kept); typed date inputs were not touched.

**Runs (2026-11-19, one worker because the host was short of memory):** before the correction the full Vitest suite (`--maxWorkers=1 --testTimeout=60000 --hookTimeout=60000`, PowerShell, lowercase path) had **36 failures in 15 files (611 of 647 pass)**: 35 are exactly the lines of the A1/A2 list; the 36th is a timing failure in an unedited file, see the MAM-64 note. After the correction: **647 of 647 pass in 29 files**. `npx tsc -b` (it includes `tests` and `e2e`) and `eslint --max-warnings 0` on the 24 changed files: no findings.
The full default Playwright suite (16 specs, journeys excluded, `--workers=1`, `NODE_OPTIONS=--dns-result-order=ipv4first`, one run): **152 of 152 pass** (8.4 min). The Playwright suite was not run before the correction (no time; the UI agent's static list was used), so there is no before count for it.
**Not covered by A1/A2 (reported, nothing changed):** in the run before the correction `tests/portal/apply-submit.test.tsx:87` (MAM-64 AC-2 "after the 201 the page opens /portal/applications/:applicationId and shows Validated") failed once with `Unable to find role="heading" and name "Application status"` (the first test of that file took 8.7 s and the file 83 s in a 790 s run under memory pressure); the file is unchanged by this correction and it passed in the run after it (647/647). A timing flake of the default `findBy` timeout under load, to watch; it is not an A1/A2 effect.
**Hashes:** re-recorded with `test_protection.py record MAM-64` with exactly the files of the story's `.tests.sha256`; `check` and `sha256sum -c` are OK.
