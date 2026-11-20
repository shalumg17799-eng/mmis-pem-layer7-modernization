-- revalidation-service: draft Flyway V1 schema for database revalidation_db (Phase 3 design; becomes
-- services/revalidation-service/src/main/resources/db/migration/V1__init.sql in Phase 5). Replaces: PRVREV01 / PRVMTH01 STEP010.
-- Conventions: java-microservices skill section 8 (id, created_at, updated_at, version; outbox; idempotent consumers).

-- Read model fed by ProviderStatusChanged (no access to provider_db).
CREATE TABLE tracked_provider (
  id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  npi CHAR(10) NOT NULL UNIQUE,
  enroll_status CHAR(1) NOT NULL,
  reval_due_date DATE,
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  version     INTEGER     NOT NULL DEFAULT 0
);

-- One sweep (PRVMTH01 STEP010).
CREATE TABLE sweep_run (
  id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  public_id UUID NOT NULL UNIQUE,
  business_date DATE NOT NULL,
  status VARCHAR(10) NOT NULL,
  queued_count INTEGER NOT NULL DEFAULT 0,
  queue_file TEXT,
  failure_reason VARCHAR(500),
  started_at TIMESTAMPTZ NOT NULL,
  ended_at TIMESTAMPTZ,
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  version     INTEGER     NOT NULL DEFAULT 0
);

-- One open case per provider at most (D-029).
CREATE TABLE revalidation_case (
  id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  public_id UUID NOT NULL UNIQUE,
  npi CHAR(10) NOT NULL,
  due_date DATE NOT NULL,
  status VARCHAR(12) NOT NULL CHECK (status IN ('DUE','IN_SCREENING','COMPLETED','CANCELLED')),
  sweep_run_id BIGINT REFERENCES sweep_run(id),
  screening_id UUID,
  opened_at TIMESTAMPTZ NOT NULL,
  closed_at TIMESTAMPTZ,
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  version     INTEGER     NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX revalidation_case_one_open ON revalidation_case (npi) WHERE status IN ('DUE','IN_SCREENING');

-- Transactional outbox (java-microservices skill section 7).
CREATE TABLE outbox_event (
  id            UUID         PRIMARY KEY,
  type          VARCHAR(60)  NOT NULL,
  subject       CHAR(10)     NOT NULL,          -- NPI, also the Kafka message key
  payload       JSONB        NOT NULL,          -- the full event envelope
  created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
  published_at  TIMESTAMPTZ
);
CREATE INDEX outbox_event_unpublished ON outbox_event (created_at) WHERE published_at IS NULL;

-- Idempotent consumers.
CREATE TABLE processed_event (
  event_id      UUID         PRIMARY KEY,       -- consumer idempotency
  processed_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
