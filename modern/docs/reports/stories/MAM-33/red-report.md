# MAM-33 red report

## Lane 3 additions (2026-11-20)

Names only (Q-097 C; the byte-level diff against the red blobs shows only the `name =` pattern of the parameterized tests): the invocation name of a parameterized test replaces its display name in the JUnit XML, so the traceability tool saw no story tag; the pattern now starts with the tag. No assertion changed.
`StatusActionTest` `from {0}` is now `[MAM-33 AC-4] from {0}`.
New tests (green at once unless stated): `ProviderOutboxContractTest` [MAM-33 AC-5] (6 invocations), `ProviderWriteInputTest` [MAM-33 AC-6, AC-8] (a date-time, another date format or an impossible day is a 400 and changes nothing),
`ProviderWriteAtomicityTest` [MAM-33 AC-5], `ChangeProviderStatusReasonTest` [MAM-33 AC-6] (60 characters accepted, 61 refused with 422, none accepted), `StatusActionResultTest` [MAM-33 AC-1/AC-2/AC-3].
First-run (18:36-18:44) failures that were production defects and are fixed: 3 status changes without a reason announced `reason: null` (contract: string); 2 effective dates with a time part were accepted (lesson b).
`MAM-33.tests.sha256` re-recorded (11 files).
