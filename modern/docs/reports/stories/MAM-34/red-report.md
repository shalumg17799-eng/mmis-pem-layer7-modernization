# MAM-34 red report: Walking skeleton (provider lookup by NPI)

Run: `./gradlew :services:provider-service:test` on 2026-11-18, before any production code of the story existed.
Result: compilation of the tests fails for the expected reason only: the classes the tests specify do not exist yet
(`Provider`, `EnrollStatus`, `ScreenFlag`, `RiskTier`, `ProviderType`, `ProviderStore`, `FindProvider`); 56 "cannot find
symbol" errors, all in `domain` / `application` references. No unrelated failure (an earlier attempt failed inside
`libs/test-support` itself; that test-infrastructure dependency gap was fixed before this recorded run).

Test hashes: `modern/docs/reports/stories/MAM-34.tests.sha256` (16 files), restore copies in `red-blobs.txt`.

| AC | Test | Level | Expected failure |
|---|---|---|---|
| AC-1 | `features/MAM-34.feature` "An analyst reads a seeded provider" (+ contract check against `getProvider`) | acceptance | no endpoint yet |
| AC-2 | `features/MAM-34.feature` "without a token" (401), "another provider's record" (403) | acceptance | no endpoint yet |
| AC-1 | `ProviderLookupRolesTest.staffRoles` (ANALYST, SUPERVISOR, ADMIN, SYSTEM), `unknown` (404), `malformedNpi` (400) | integration (Testcontainers) | no endpoint yet |
| AC-2 | `ProviderLookupRolesTest.providerOwn`, `providerOther` (403), `noRole` (403), `anonymous` (401) | integration | no endpoint yet |
| AC-1 | `ProviderCodesTest` (status, screen flag, tier and type codes; unknown code rejected) | unit | missing classes |
| AC-1/AC-2 | `FindProviderTest` (found, own NPI only, no probing, 404) | unit | missing classes |
| AC-3 | console page "Provider lookup": `web-ui/tests/console/provider-lookup.test.tsx` and `web-ui/e2e/walking-skeleton.spec.ts` | component, e2e | written with the web-ui scaffold (same story, recorded separately) |

Platform checks in `PlatformChecksTest` belong to MAM-16 (ArchUnit), MAM-24 (Flyway schema) and MAM-36 (health,
protected actuator, correlation id); they are recorded in those stories' hash files.

## Lane 3 additions (2026-11-20)

Names only (Q-097 C; the byte-level diff against the red blobs shows only the `name =` pattern of the parameterized tests): the invocation name of a parameterized test replaces its display name in the JUnit XML, so the traceability tool saw no story tag; the pattern now starts with the tag. No assertion changed.
`ProviderCodesTest` (4 parameterized tests) and `ProviderLookupRolesTest` (1) now start with `[MAM-34 AC-1]`. `MAM-34.tests.sha256` re-recorded (18 files, the same files).
