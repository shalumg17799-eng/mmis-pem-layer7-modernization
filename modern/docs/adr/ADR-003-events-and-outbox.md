# ADR-003: Events and the transactional outbox

Status: ACCEPTED (user, 2026-11-18)

## Context

State changes must reach other services reliably without distributed transactions.

## Decision

Kafka topic per producer `pem.<service>.events`, envelope with id, type, source, time, subject (NPI), correlationId, data; outbox table and poller; consumers record processed_event ids.

## Consequences

At-least-once delivery with idempotent consumers; events are documented in `contracts/asyncapi/events.yaml`.
