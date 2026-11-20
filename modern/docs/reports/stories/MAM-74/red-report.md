# MAM-74 red report

The red run of this story is in `red-run.txt`; this file holds the corrections made to its recorded tests.

## Test correction under Q-097 (UI polish B-iv, user decision 2026-11-19)

Cause: **the UI polish of 2026-11-19 (B-iv)**, under the Q-097 rule and the user's explicit permission to use it for any selector change. The key-value tables "Provider record", "Provider", "Application" and the portal home's "My provider record" are now USWDS summary lists (`<section aria-labelledby>`, a region named by its title, holding a `<dl>` of `div > dt + dd`); the label "Screen flag" is "Screening result"; the screening sources are shown by name (Licence board, OIG exclusion list (LEIE), SAM.gov debarment, SSA death records); and the status page has a step indicator whose step-2 segment reads "Validated" ("Validation failed" for a rejected application). Scoping and renaming only: no assertion was weakened or removed; production code unchanged. Test titles keep their wording unless said otherwise.

What changed in MAM-74:
- `j3-screening-fails.spec.ts` (AC-3): the row filter "OIGLEIE" in the screening panel is "OIG exclusion list (LEIE)" (the source is shown by name). The API checks on `r.source === 'OIGLEIE'`
  are the data and stay.
- Not run: it is a journey (needs the freshly seeded stack and changes its data); the coordinator did not allow journeys or seeding for this correction.

Exact changes (`-` before, `+` after; the previous blob is the state at the start of this correction, line endings kept, a plain `diff` against it shows only these lines):

| File | Previous blob | New blob | Line endings | Changed lines |
|---|---|---|---|---|
| `e2e/journeys/j3-screening-fails.spec.ts` | `01f4fa17e93bab9a5666dd7093f3d60cdaefb1a2` | `b7e2e8e8cec6f3cd9caf95030c9432bcba6f2d5d` | LF | 2 |

`e2e/journeys/j3-screening-fails.spec.ts` (LF)
```diff
@@ -122 +122 @@
-    const leieRow = screeningPanel.getByRole('row').filter({ hasText: 'OIGLEIE' });
+    const leieRow = screeningPanel.getByRole('row').filter({ hasText: 'OIG exclusion list (LEIE)' });
```

Runs (2026-11-19; the host was short of memory, so one worker): before the correction Vitest had 77 failures in 11 files (570 of 647 passed, as reported by the lead and re-run here)
and Playwright 46 failures in 8 specs (106 of 152, as reported by the lead). After it: the full Vitest suite (`--maxWorkers=1 --testTimeout=60000 --hookTimeout=60000`, PowerShell, lowercase path)
**647 of 647 pass** in 29 files; the full default Playwright suite (16 specs in 4 batches, `--workers=1`, journeys excluded) **152 of 152 pass**; `npx tsc -b` and `npx eslint --max-warnings 0`
on the changed files: no findings. The journey J3 (MAM-74) was not run (no journeys, no seeding).
**Hashes:** re-recorded with `test_protection.py record <KEY>` with exactly the files of the story's `.tests.sha256`; `check` and `sha256sum -c` are OK.
