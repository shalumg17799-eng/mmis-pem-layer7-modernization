# MAM-90 red report: Console, record a sanction (supervisor)

Run on 2026-11-19 (fast mode), before any production code of the story existed (`/console/providers/:npi/sanctions/new` was a placeholder, the detail page had no "Record sanction").
Hashes: `modern/docs/reports/stories/MAM-90.tests.sha256` (3 files), restore copies in `red-blobs.txt`.

| Level | Command | Tests | Result | Reason |
|---|---|---|---|---|
| Vitest | `vitest run tests/console/sanction.test.tsx` (PowerShell, lowercase drive) | 35 | 30 failed, 5 passed | no label "NPI" / "Action" / "Reason" (25), no button "Record sanction" (2), no link "Record sanction" (1), no history call or message (2). The 5 that passed are the negative role guards that MAM-62 already satisfies (analyst1 and admin1 have no "Record sanction" link; analyst1, admin1 and provider1 get Access denied on the form address). |
| Playwright | `playwright test e2e/console-sanction.spec.ts` | 9 | 9 failed | 8 x test timeout: the first form field (`Action`, `Effective date`) never exists, so the locator action waits for the 90 s test timeout; 1 x NPI field has no value |

AC to test: AC-1 link for supervisor1, none for analyst1 / admin1, Access denied for three roles (6); AC-2 fields, empty submit, summary focus and links, five impossible dates, typed kept, ISO date (10);
AC-3 S / T / R with statuses, never Denied, next action on the same page (5); AC-4 history call, two rows after Suspend and Reinstate, history failure (3); AC-5 422 at the Action field and in the summary, status and history unchanged, 422 for effectiveDate (3);
AC-6 three actions offered, Reinstate for a denied provider (2); AC-7 404, 403, 500 / 503 / network, focus and retry (6). Playwright: link, empty form + axe, errors + axe + summary link, Suspend then Reinstate + history + axe, 422, 404 / 403 / 503 + axe, keyboard-only.

Reading fixed by the tests: the page does not read the provider (D-027: no status is blocked), so the status it shows is the `resultingStatus` of the 201 ("Sanction recorded. Status: Suspended (S)."); the 422 detail of the service ("Field action must be one of T, S or R ...") is placed at the field it names;
a 503 (provider-service did not change the status, not yet in the contract, Q-096) and any other 5xx read "The sanction could not be applied. Nothing was recorded."; after a success the form is emptied and can record the next action.

## Test correction under Q-097 (plain dates + Reference, user decision 2026-11-19 19:54)

Cause: **the user decisions of 2026-11-19 19:54 that the UI now implements**, under the Q-097 C standing rule (superseded tests; scoping and wording only, no assertion weakened or removed, production code unchanged). **A2, plain dates:** every displayed date reads "19 Nov 2026", the day without a leading zero (`formatDate` in `web-ui/src/shared/format/format.ts`); typed date inputs stay MM/DD/YYYY, so no value a test types into an input was changed. **A1, application status page:** the application ID is the single text "Reference: <id>" under the h1, the "Application" row of the summary list is gone (the list keeps its title "Application", so region lookups still work), and the paragraph "Your application was accepted. ..." is removed.

Exact changes (`-` before, `+` after; the previous blob is the state at the start of this correction, line endings kept, a plain `diff` against it shows only these lines):

| File | Previous blob | New blob | Line endings | Changed lines (removed / added) |
|---|---|---|---|---|
| `e2e/console-sanction.spec.ts` | `dc6f6d1cbced7af65130e2351958d8d4d0019ee7` | `68d8e0a2b45c8228ae64738f8b24b0872f8041dc` | LF | 2 / 2 |
| `tests/console/sanction.test.tsx` | `e29000adfd8df2bb9e380f6684f785dc38e8be27` | `c29ad96b75b4d0740c5c6ce23de4fd31fb46dd47` | LF | 2 / 2 |

`e2e/console-sanction.spec.ts` (LF)
```diff
@@ -98,2 +98,2 @@ test('[MAM-90 AC-3][MAM-90 AC-4][MAM-90 AC-8] Suspend then Reinstate: the page c
-  await expect(history.locator('tbody tr').nth(0).getByRole('cell')).toHaveText(['03/20/2026', 'Reinstate (R)', 'Licence confirmed', 'supervisor1']);
-  await expect(history.locator('tbody tr').nth(1).getByRole('cell')).toHaveText(['03/10/2026', 'Suspend (S)', 'Licence under review', 'supervisor1']);
+  await expect(history.locator('tbody tr').nth(0).getByRole('cell')).toHaveText(['20 Mar 2026', 'Reinstate (R)', 'Licence confirmed', 'supervisor1']);
+  await expect(history.locator('tbody tr').nth(1).getByRole('cell')).toHaveText(['10 Mar 2026', 'Suspend (S)', 'Licence under review', 'supervisor1']);
```

`tests/console/sanction.test.tsx` (LF)
```diff
@@ -195,2 +195,2 @@ describe('[MAM-90 AC-4] suspend and reinstate keep a history (journey J5)', () =
-      ['03/20/2026', 'Reinstate (R)', 'Licence confirmed', 'supervisor1'],
-      ['03/10/2026', 'Suspend (S)', 'Licence under review', 'supervisor1'],
+      ['20 Mar 2026', 'Reinstate (R)', 'Licence confirmed', 'supervisor1'],
+      ['10 Mar 2026', 'Suspend (S)', 'Licence under review', 'supervisor1'],
```

Test titles that named the old format were corrected for honesty (AC tags kept); typed date inputs were not touched.

**Runs (2026-11-19, one worker because the host was short of memory):** before the correction the full Vitest suite (`--maxWorkers=1 --testTimeout=60000 --hookTimeout=60000`, PowerShell, lowercase path) had **36 failures in 15 files (611 of 647 pass)**: 35 are exactly the lines of the A1/A2 list; the 36th is a timing failure in an unedited file, see the MAM-64 note. After the correction: **647 of 647 pass in 29 files**. `npx tsc -b` (it includes `tests` and `e2e`) and `eslint --max-warnings 0` on the 24 changed files: no findings.
The full default Playwright suite (16 specs, journeys excluded, `--workers=1`, `NODE_OPTIONS=--dns-result-order=ipv4first`, one run): **152 of 152 pass** (8.4 min). The Playwright suite was not run before the correction (no time; the UI agent's static list was used), so there is no before count for it.

**Hashes:** re-recorded with `test_protection.py record MAM-90` with exactly the files of the story's `.tests.sha256`; `check` and `sha256sum -c` are OK.
