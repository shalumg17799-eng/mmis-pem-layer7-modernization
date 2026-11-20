# MAM-76 red report

The first red run is in `red-run.txt` (AC-1: the console search still called GET /api/v1/providers/{npi}, expected for a story
not yet built). This file holds the test corrections made after it.

## Test correction under Q-097 (plain dates, user decision 2026-11-19 19:54)

Cause: the UI changed after a user decision, so the test's expectation of the displayed dates was out of date (not a test fault,
not production code). Every displayed date now reads "19 Nov 2026" (no leading zero on the day, months Jan to Dec); typed date
inputs stay MM/DD/YYYY. `formatDate` in `modern/web-ui/src/shared/format/format.ts` produces it.

- **File:** `modern/web-ui/e2e/journeys/j4-inquiry.spec.ts` (LF file; line endings kept, read and written with `newline=''`,
  `grep -c $'\r'` = 0 before and after). Helper `usDate` renamed `shownDate` (the old name would mislead). Used twice, on the
  provider detail page, for Effective date and Revalidation due. No assertion changed other than the expected date text; nothing
  typed into an input in this file used the helper.
```diff
-const usDate = (iso: string): string => `${iso.slice(5, 7)}/${iso.slice(8, 10)}/${iso.slice(0, 4)}`;
+/** "19 Nov 2026" for an ISO date (yyyy-mm-dd): the plain form the UI displays (Q-097, user decision 2026-11-19 19:54). */
+const MONTH = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
+const shownDate = (iso: string): string => `${Number(iso.slice(8, 10))} ${MONTH[Number(iso.slice(5, 7)) - 1]} ${iso.slice(0, 4)}`;
@@ (line 134 -> 136)
-      usDate(provider.effectiveDate), usDate(provider.revalidationDueDate), 'RIVERA', 'MARIA', 'MD12345']) {
+      shownDate(provider.effectiveDate), shownDate(provider.revalidationDueDate), 'RIVERA', 'MARIA', 'MD12345']) {
```
- **Blobs (`git hash-object`):** previous `849b9aa1e2553e1c13c26c8f07d81adc5da9f999`, new `ef9414b7850e578b52e0f49027af60ecd9b2e202`.
- **Checks (2026-11-19):** `npx tsc --noEmit -p tsconfig.json` and `npx eslint --max-warnings 0` on the file: no findings. The journey
  was not run by the test-writer (the lead runs it on the live stack).
- **Hashes:** re-recorded with `test_protection.py record MAM-76` (the one file already listed); `check MAM-76` printed OK.
