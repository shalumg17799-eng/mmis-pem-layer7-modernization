```
Phase / item:      MAM-19 Web UI toolchain scaffold: React, TypeScript, Vite, USWDS, Vitest, Playwright with axe, clients generated from the contracts
Outcome:           DONE (Phase 5 platform task; tests recorded in MAM-19.tests.sha256); AC-1..AC-7 checked 2026-11-19 20:54
What was done:     Scaffold built in Phase 5; used by every UI story since. This report written by the lead from the checks below.
Files created/changed: web-ui/package.json, vite.config.ts, tsconfig.json, public/config.js, scripts/gen-api.mjs,
                   scripts/no-skipped.mjs, src/{app,features/portal,features/console,shared,theme} (tests: MAM-19.tests.sha256)
Checks run:        AC-1 npm run build (tsc -b + vite) clean at 20:08 (UI agent); git check-ignore web-ui/dist -> ignored; dist holds
                        index.html, assets/, config.js
                   AC-2 TypeScript strict true; Vite 8.3.8, React 19.3.1, React Router 7.18.5, TanStack Query 5.108.0; folders
                        src/app, src/features/portal, src/features/console, src/shared, src/theme, tests/, e2e/
                   AC-3 @trussworks/react-uswds 13.0.1 (exact) with @uswds/uswds 3.14.0, no other component library; every
                        dependency exact (no ^ ~ * ranges); versions recorded in ADR-010
                   AC-4 npm run api:check (regenerates from the contracts and compares) -> "generated API types match the
                        contracts", exit 0
                   AC-5 npm test = vitest run + scripts/no-skipped.mjs (fails on any skipped test); full Vitest 647/647 at 20:38
                   AC-6 e2e/scaffold-a11y.spec.ts (axe; red in Phase 5 on a missing label) is part of Playwright 152/152 at 20:38
                   AC-7 vite server/preview port 5173 strictPort; gateway http://localhost:8080 and Keycloak http://localhost:8180
                        realm pem, client pem-web read from public/config.js at run time; no secret in any committed file (MAM-26)
Not verified:      npm ci was not re-run now (it would reinstall node_modules under memory pressure); the 20:38 Vitest run used
                   vitest directly, so no-skipped.mjs ran in earlier npm test runs, not in that one
Questions for you: none
Next step:         Jira Done (user approval 2026-11-19 20:34)
```
