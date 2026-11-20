# Web UI test coverage (measurement only)

Date: 2026-11-20 (run started 20:48:11, 219 s)
Scope: `modern/web-ui/src` (React 19 + Vite), measured while running the whole Vitest suite.
Result of the run: 43 test files passed (43), 1084 tests passed (1084), no skipped tests (`scripts/no-skipped.mjs`: "no skipped tests (1084 tests)"), exit code 0.

No threshold is enforced. The project's 85 % gate is the JaCoCo gate of the Java services; the user asked for the figure only (19:32, item 4). Nothing in `npm test`, CI or the build fails on these numbers.

## Command

```
cd modern/web-ui
npm run coverage            # = vitest run --coverage
```

The measurement run added only a JSON reporter whose output went to the session scratchpad:
`npm run coverage -- --reporter=default --reporter=json --outputFile=<scratchpad>/ui-results/vitest-coverage.json`.

Outputs (git-ignored, `modern/web-ui/coverage/`): `coverage-summary.json`, `index.html` and one HTML page per file, plus the `text-summary` printed to the console.

Setup: `@vitest/coverage-v8` 5.1.1 (exactly the installed Vitest 5.1.1, `--save-exact`), provider `v8`, reporters `text-summary`, `json-summary`, `html`, `reportsDirectory: coverage`, `include: src/**/*.ts, src/**/*.tsx`, no `thresholds`. See `modern/web-ui/vite.config.ts`.

## Totals (64 source files)

| Metric | Covered / total | Per cent |
|---|---|---|
| Lines | 1131 / 1192 | 94.88 % |
| Statements | 1293 / 1383 | 93.49 % |
| Branches | 1128 / 1292 | 87.30 % |
| Functions | 447 / 506 | 88.33 % |

46 of the 64 files have 100 % line coverage. Every file in scope is reported, including any that no test loads; no file is at 0 %. 6 files are under 85 % of lines (the first six rows below).

## The ten files with the lowest line coverage

| File (under `modern/web-ui/`) | Lines | Statements | Branches | Functions |
|---|---|---|---|---|
| `src/features/console/providers/panels/SanctionTable.tsx` | 6 / 9 (66.66 %) | 6 / 9 (66.66 %) | 4 / 8 (50 %) | 3 / 6 (50 %) |
| `src/app/pages/SignedOut.tsx` | 2 / 3 (66.66 %) | 2 / 3 (66.66 %) | 0 / 0 | 1 / 2 (50 %) |
| `src/features/console/queues/QueuesPage.tsx` | 37 / 50 (74 %) | 41 / 57 (71.92 %) | 25 / 39 (64.10 %) | 18 / 33 (54.54 %) |
| `src/features/console/providers/panels/DetailPanels.tsx` | 41 / 55 (74.54 %) | 45 / 59 (76.27 %) | 49 / 70 (70 %) | 21 / 35 (60 %) |
| `src/app/pages/Placeholder.tsx` | 3 / 4 (75 %) | 3 / 4 (75 %) | 2 / 2 | 1 / 2 (50 %) |
| `src/shared/ui/Pager.tsx` | 4 / 5 (80 %) | 5 / 6 (83.33 %) | 2 / 2 | 2 / 3 (66.66 %) |
| `src/app/pages/SignInPages.tsx` | 21 / 24 (87.5 %) | 21 / 27 (77.77 %) | 4 / 8 (50 %) | 7 / 10 (70 %) |
| `src/features/console/approval/DecisionForm.tsx` | 14 / 16 (87.5 %) | 15 / 18 (83.33 %) | 20 / 23 (86.95 %) | 6 / 7 (85.71 %) |
| `src/features/portal/home/ApplicationsSection.tsx` | 8 / 9 (88.88 %) | 8 / 9 (88.88 %) | 13 / 14 (92.85 %) | 4 / 5 (80 %) |
| `src/features/portal/notices/NoticesPage.tsx` | 18 / 20 (90 %) | 19 / 21 (90.47 %) | 25 / 36 (69.44 %) | 7 / 9 (77.77 %) |

Ordering is by line per cent, then by line count (larger file first on a tie). The biggest absolute gaps are `QueuesPage.tsx` (13 lines, 15 functions) and `DetailPanels.tsx` (14 lines, 14 functions).

## What is measured, what is excluded and why

Measured: every `.ts` and `.tsx` file under `src/` except the two exclusions below (64 files).

Excluded:
- `src/shared/api/generated/**` (9 files). These are TypeScript declarations produced by `npm run api:gen` (`openapi-typescript`) from the OpenAPI contracts. They contain no runtime code, are not hand-written, and are checked by `npm run api:check` instead. They are also already excluded from ESLint.
- `src/main.tsx`. The bootstrap file mounts the app into `document.getElementById('root')` and imports the USWDS stylesheet; it cannot be loaded in a Vitest test. Its work (the providers around `App`) is exercised through the component tests, but the file itself is not.

Not matched by the `include` globs (not code): the six `.css` files and `src/theme/README.md`.

Not excluded although it looks like a types file: `src/shared/api/types.ts` has type exports and runtime path helpers (`providerPath`, `withQuery` and others), so it is measured.

`tests/**`, `e2e/**` and the config files are outside `src/` and are not measured.

## How to read the figure

- It is the code the 1084 jsdom component tests execute. The Playwright journeys (J1 to J7) and the axe scans run against the built bundle in a real browser and do not add to it.
- V8 coverage is mapped back to source through source maps, so a line counts as covered when any of its code ran. Branch and function figures are stricter than the line figure, which is why they are lower.
- This is a one-time measurement on the working tree of 2026-11-20 (it includes the Batch jobs page follow-up for T-MSIS runs). It is not tracked over time.

## Not verified

- No run in CI: the `coverage` script is not part of `npm test`, `.github/workflows/ci.yml` or `make test`.
- The HTML report was generated but not opened in a browser.
- No comparison with an Istanbul instrumented run; the figures are V8 only.
