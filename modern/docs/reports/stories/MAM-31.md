```
Phase / item:      MAM-31 Event backbone: CloudEvents-style envelope, transactional outbox, idempotent consumers
Outcome:           DONE (Phase 5 platform task; tests recorded in MAM-31.tests.sha256); AC-1..AC-8 checked 2026-11-20
What was done:     Backbone built in Phase 5 in the shared module libs/service-kit (package gov.medicaid.pem.kit.events and
                   kit.config): EventEnvelope, OutboxWriter, OutboxRelay, ProcessedEvents, PemKafkaAutoConfiguration (topics,
                   retries, dead-letter recoverer). Used by every service since. This report was written by the lead from the
                   recorded tests, today's gate runs and read-only live checks.
Files created/changed: libs/service-kit/src/main/java/gov/medicaid/pem/kit/events/*, .../kit/config/PemKafkaAutoConfiguration.java,
                   libs/test-support (EventProbe, PemContainers); docs/adr/ADR-012 (tests: MAM-31.tests.sha256, 9 files)
Checks run:        AC-1 envelope (id, type, source, time, subject, correlationId, data), topic pem.<service>.events, NPI as key:
                        kit EventEnvelopeTest and OutboxWriterTest; per-service envelope contract tests against
                        contracts/asyncapi/events.yaml (for example sanctions SanctionAppliedEventContractTest, added 2026-11-20);
                        live: the parked message below shows the envelope and the NPI key. Approved deviation: ExtractProduced is
                        keyed by the run id (Q-061 B).
                   AC-2 written in the same transaction as the change (commit publishes once, rollback publishes nothing):
                        OutboxWriterTest, OutboxRelayTest; strengthened on 2026-11-20 for sanctions (link + history + event atomic)
                   AC-3 nothing is lost while Kafka is unavailable: OutboxRelayTest (the relay retries; "outbox event ... not
                        published yet (will retry)")
                   AC-4 a repeated event is applied once: ProcessedEventsTest (processed_event table); used in J1-J7
                   AC-5 the correlation id travels with the event: kit tests; risk-service ScreeningEventsCorrelationIdTest
                        (35 tests, added 2026-11-20 after a review finding: the id is sanitised before it reaches the log or
                        the next event)
                   AC-6 the eight topics exist: live read-only listing of the pem Kafka on 2026-11-20 16:20 (a one-file Admin
                        client): pem.enrollment.events, pem.notification.events, pem.provider.events, pem.revalidation.events,
                        pem.risk.events, pem.sanctions.events, pem.screening.events, pem.tmsis.events
                   AC-7 a message that cannot be processed is parked, not dropped: kit PemKafkaAutoConfigurationTest; live
                        evidence: topic pem.revalidation.events.DLT holds 1 message (2026-11-19 11:16 UTC, RevalidationDue for a
                        provider that a reseed had removed during a test) with its original envelope and the headers
                        kafka_dlt-original-topic, kafka_dlt-original-consumer-group = screening-service and the exception message
                        (404 "No provider found"); later messages were processed (J6 passed 3 of 3 afterwards)
                   AC-8 one shared module: libs:service-kit holds envelope, outbox writer and relay, idempotent-consumer helper
                        and dead-letter handler; every service depends on it (ADR-012)
                   Kit gate: :libs:service-kit:check BUILD SUCCESSFUL on 2026-11-20 14:45, 110 tests, 0 failures, 0 skipped,
                        JaCoCo line 91.85 %; test_protection check MAM-31 OK; code review of the kit: APPROVE (2026-11-20 15:3x,
                        with risk-service)
Not verified:      AC-3 against the live stack (Kafka of project pem was not stopped: other work shares the stack); the dead
                   letter message of 2026-11-19 is old test data and stays on the topic (not replayed)
Questions for you: none
Next step:         Jira Done (user approval 2026-11-19 20:34)
```

## Follow-up (2026-11-21, question Q-142, option A)

The event contract says the correlation id of an event is a uuid. The kit copied the id of the request into every event, and
the request filter accepts any safe text (for example `trace-42`), so such a request wrote events against the contract.

- Tests first (test-writer, 07:46 to 08:18): `OutboxWriterCorrelationIdTest`, 71 tests: a non-uuid or unsafe current id
  is replaced by a new uuid, a uuid is kept in its canonical lower-case form, without a current id the event gets a uuid,
  the log context is never changed, the stored envelope conforms to `events.yaml`; 28 were red before the fix.
- One recorded test changed under rule Q-097 C: `OutboxWriterTest#writesTheEnvelopeToTheOutboxTable` used the id `corr-42`
  and now uses a uuid (2 lines; the assertion that the envelope carries the current id is unchanged). The same was done
  for provider-service's `ObservabilityTest` (MAM-36, 1 line). Both are described in the red reports of MAM-31 and MAM-36.
- Fix (java-developer, 08:19 to 08:33): `OutboxWriter.eventCorrelationId()`; nothing else in the kit changed.
- Checks: `:libs:service-kit:check` 181 tests, 0 failures, 0 skipped, JaCoCo 91.9 % of lines (the kit has no PIT); the test
  tasks of risk-service (351 tests) and notification-service (346 tests): 0 failures. The regression of the other services
  is the GitHub pipeline for the commit that carries this change.
- One code review (08:46): APPROVE, no MUST-FIX. Notes in `modern/docs/reports/review-should-fix.md`.
- For the user (recorded under Q-142): events of one request with a non-uuid id no longer share a correlation id; a text
  that Java accepts as a uuid without the 8-4-4-4-12 form is kept in canonical form; AC-5 of this story says "equals that
  id", which now holds for a uuid only (a text change of the story is to follow).
