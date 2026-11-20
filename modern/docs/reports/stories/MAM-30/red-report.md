# MAM-30 red report

## Lane 3 additions (2026-11-20)

Names only (Q-097 C; the byte-level diff against the red blobs shows only the `name =` pattern of the parameterized tests): the invocation name of a parameterized test replaces its display name in the JUnit XML, so the traceability tool saw no story tag; the pattern now starts with the tag. No assertion changed.
`RevalidationDueDateTest`: `approved on ...` is now `[MAM-30 AC-1/AC-2/AC-3] ...` and `revalidated on ...` is `[MAM-30 AC-3/AC-4] ...`; before, MAM-30 AC-1, AC-2 and AC-3 showed as untested in the trace although 15 invocations ran.
New test (green at once): `ProviderOutboxContractTest` [MAM-30 AC-5] (a passed revalidation announces ProviderStatusChanged that keeps to the contract, with the due date = service clock date + 5 years; its first version expected the fixed date 2031-11-18 and failed because the service clock is the real clock; corrected before recording).
`MAM-30.tests.sha256` re-recorded (8 files).
