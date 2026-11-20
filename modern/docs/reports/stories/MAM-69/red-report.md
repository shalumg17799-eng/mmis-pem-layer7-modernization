# MAM-69 red report

## Red run (2026-11-19 13:40)

`e2e/journeys/j1-enrolment.spec.ts` against the live stack after `./pem.sh seed`: AC-1..AC-3 passed (apply through the
portal -> Validated; screening PASS on the four sources; tier L), then failed at AC-4 because `/console/queues` was not
built (`red-run.txt`). That is the expected reason: the work-queues page is MAM-85.

## Test changes before green (2026-11-19, code review of MAM-64 / MAM-87 / MAM-69)

All made while the test was still red, nothing weakened:
- MUST-FIX 2: AC-2 now asserts exactly one screening, reason INITIAL, status COMPLETED, result PASS and exactly the four
  sources LICBOARD, OIGLEIE, SAMGOV, SSADMF each P (was: each SOURCE=P somewhere in any screening).
- MUST-FIX 3: the Kafka payload clauses are proven by the owning service tests, cited in the file header (Q-094 default A).
  A host Kafka probe (kafkajs) was the other option; installing the dependency was refused by the permission policy.
- SHOULD-FIX 7: AC-1 starts at the portal home (`/portal`, axe scan) and follows "Apply for enrolment".
- SHOULD-FIX 8: AC-4 first asserts the "Failed screening" section is visible, then that the NPI is not in it.
- SHOULD-FIX 10: the due date is computed like LocalDate.plusYears(5) (29 Feb -> 28 Feb) and accepted for the service date
  read just before or right after the decision (midnight boundary).
- SHOULD-FIX 9: `./pem.sh j1 [runs]` seeds and runs J1 N times; `playwright.config.ts` leaves `e2e/journeys/**` out of the
  default run unless `PEM_JOURNEYS=1`.

Hashes re-recorded with `test_protection.py record MAM-69`.

## Test correction under Q-097 (plain dates, user decision 2026-11-19 19:54)

Cause: the UI changed after a user decision, so the test's expectation of the displayed date was out of date (not a test fault,
not production code). Every displayed date now reads "19 Nov 2026" (no leading zero on the day, months Jan to Dec); typed date
inputs stay MM/DD/YYYY. `formatDate` in `modern/web-ui/src/shared/format/format.ts` produces it.

- **File:** `modern/web-ui/e2e/journeys/j1-enrolment.spec.ts` (LF file; line endings kept, read and written with `newline=''`,
  `grep -c $'\r'` = 0 before and after). Helper `usDate` renamed `shownDate` (the old name would mislead). Used once, on the
  "Decision recorded" message, to check the revalidation due date. No assertion changed other than the expected date text; nothing
  typed into an input in this file used the helper.
```diff
-/** MM/DD/YYYY of an ISO date (yyyy-mm-dd), as the UI writes dates. */
-const usDate = (iso: string): string => `${iso.slice(5, 7)}/${iso.slice(8, 10)}/${iso.slice(0, 4)}`;
+/** "19 Nov 2026" for an ISO date (yyyy-mm-dd): the plain form the UI displays (Q-097, user decision 2026-11-19 19:54). */
+const MONTH = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
+const shownDate = (iso: string): string => `${Number(iso.slice(8, 10))} ${MONTH[Number(iso.slice(5, 7)) - 1]} ${iso.slice(0, 4)}`;
@@ (line 199 -> 200)
-    await expect(consolePage.getByText(/Decision recorded/)).toContainText(usDate(decided.revalidationDueDate));
+    await expect(consolePage.getByText(/Decision recorded/)).toContainText(shownDate(decided.revalidationDueDate));
```
- **Blobs (`git hash-object`):** previous `7b97ba9111f8d1bd7d8593bed5598f5bd2c52e77`, new `a68c74745b70b2d4901473a862dd032aad8a6183`.
- **Checks (2026-11-19):** `npx tsc --noEmit -p tsconfig.json` and `npx eslint --max-warnings 0` on the file: no findings. The journey
  was not run by the test-writer (the lead runs it on the live stack).
- **Hashes:** re-recorded with `test_protection.py record MAM-69` (the one file already listed); `check MAM-69` printed OK.
