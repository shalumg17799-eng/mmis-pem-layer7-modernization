# MAM-27 red report

## Lane 3 additions (2026-11-20)

New tests of the lane: `ProviderOutboxContractTest` [MAM-27 AC-2, AC-3] (events keep to events.yaml; the reason is left out when empty; an unsafe X-Correlation-Id is replaced, not refused),
`ProviderWriteInputTest` [MAM-27 AC-4, AC-7] (unreadable body 400, no token 401, wrong role 403, all problem+json, nothing changed), `ProviderWriteAtomicityTest` [MAM-27 AC-2] (a failing history entry or event rolls the approval back: the provider stays PENDING, nothing is announced, a plain 503, the retry applies it once).
First-run (18:36-18:44) failures that were production defects and are fixed: reason `null` in ProviderStatusChanged where the contract says string (2 tests); an unsafe X-Correlation-Id was refused with 400 instead of replaced (1 test). `MAM-27.tests.sha256` re-recorded (9 files).
