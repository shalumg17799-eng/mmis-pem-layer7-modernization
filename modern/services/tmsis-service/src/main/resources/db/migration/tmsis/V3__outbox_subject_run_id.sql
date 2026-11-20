-- tmsis-service V3 (Q-061 / OQ-D8 answer B, user decision 2026-11-19; events.yaml 1.1.0): ExtractProduced is keyed by the
-- extract run id (a uuid, 36 characters), not by an NPI, so the outbox subject (also the Kafka message key) is widened here.
-- Only tmsis-service's outbox changes; every other service keeps CHAR(10) NPI subjects.
ALTER TABLE outbox_event ALTER COLUMN subject TYPE VARCHAR(36);
