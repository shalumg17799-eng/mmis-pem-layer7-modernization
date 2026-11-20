# UX-OVERHAUL red report

This story had no red-report.md; this file holds only the correction below (the tests of the story are listed in `modern/docs/reports/stories/UX-OVERHAUL.tests.sha256`).

## Test correction under Q-097 (plain dates + Reference, user decision 2026-11-19 19:54)

Cause: **the user decisions of 2026-11-19 19:54 that the UI now implements**, under the Q-097 C standing rule (superseded tests; scoping and wording only, no assertion weakened or removed, production code unchanged). **A2, plain dates:** every displayed date reads "19 Nov 2026", the day without a leading zero (`formatDate` in `web-ui/src/shared/format/format.ts`); typed date inputs stay MM/DD/YYYY, so no value a test types into an input was changed. **A1, application status page:** the application ID is the single text "Reference: <id>" under the h1, the "Application" row of the summary list is gone (the list keeps its title "Application", so region lookups still work), and the paragraph "Your application was accepted. ..." is removed.

Exact changes (`-` before, `+` after; the previous blob is the state at the start of this correction, line endings kept, a plain `diff` against it shows only these lines):

| File | Previous blob | New blob | Line endings | Changed lines (removed / added) |
|---|---|---|---|---|
| `tests/ux/portal-home.test.tsx` | `15f122d52f4f689ccc50333e20c47ad6f6fd9c4a` | `563d63a2275b2e606683c8e287725278624af6e5` | CRLF | 1 / 1 |

`tests/ux/portal-home.test.tsx` (CRLF)
```diff
@@ -67 +67 @@ describe('[UX] portal home: the "Your enrolment" summary', () => {
-    ['A', 'Active (A)', 'Your next revalidation is due on 03/02/2031.'],
+    ['A', 'Active (A)', 'Your next revalidation is due on 2 Mar 2031.'],
```

Test titles that named the old format were corrected for honesty (AC tags kept); typed date inputs were not touched.

**Runs (2026-11-19, one worker because the host was short of memory):** before the correction the full Vitest suite (`--maxWorkers=1 --testTimeout=60000 --hookTimeout=60000`, PowerShell, lowercase path) had **36 failures in 15 files (611 of 647 pass)**: 35 are exactly the lines of the A1/A2 list; the 36th is a timing failure in an unedited file, see the MAM-64 note. After the correction: **647 of 647 pass in 29 files**. `npx tsc -b` (it includes `tests` and `e2e`) and `eslint --max-warnings 0` on the 24 changed files: no findings.
The full default Playwright suite (16 specs, journeys excluded, `--workers=1`, `NODE_OPTIONS=--dns-result-order=ipv4first`, one run): **152 of 152 pass** (8.4 min). The Playwright suite was not run before the correction (no time; the UI agent's static list was used), so there is no before count for it.

**Hashes:** re-recorded with `test_protection.py record UX-OVERHAUL` with exactly the files of the story's `.tests.sha256`; `check` and `sha256sum -c` are OK.

Record correction (lead, 2026-11-19 20:58, code-reviewer round 2): line(s) 13, 15 labelled the file LF; it is CRLF (byte check by the reviewer). Only the label changed; the test file and its hash are unchanged.
