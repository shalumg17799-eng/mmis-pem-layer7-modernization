# MAM-21 red report

## Lane 3 additions (2026-11-20)

New tests of the lane (green at once): `ListenerTransactionTest` [MAM-21 AC-6], `RecordVerdictsOutcomeTest` [MAM-21 AC-1] (a verdict is reported applied or not; a verdict for a missing provider is logged once as a warning),
`UnsafeCorrelationIdFromEventsTest` [MAM-21 AC-1]. `MAM-21.tests.sha256` re-recorded (9 files). PIT had shown the return value of the 3-argument overloads and the `logged` condition unasserted; now killed.
