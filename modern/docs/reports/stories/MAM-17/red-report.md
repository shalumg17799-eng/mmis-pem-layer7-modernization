# MAM-17 red report

## Lane 3 additions (2026-11-20)

New tests of the lane (written after the code, green at once): `ListenerTransactionTest` [MAM-17 AC-6] (the processed marker is rolled back with a failed registration; the re-run registers once and announces once),
`ProviderOutboxContractTest` [MAM-17 AC-2] (ProviderRegistered keeps to events.yaml for an individual and a group), `UnsafeCorrelationIdFromEventsTest` [MAM-17 AC-2] (a forged correlation id on ApplicationValidated through the real Kafka: one generated uuid in the log, the history and the event).
`MAM-17.tests.sha256` re-recorded (10 files).
