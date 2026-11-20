-- sanctions-service: draft Flyway V1 schema for database sanctions_db (Phase 3 design; becomes
-- services/sanctions-service/src/main/resources/db/migration/V1__init.sql in Phase 5). Replaces: PRVSAN01.
-- Conventions: java-microservices skill section 8 (id, created_at, updated_at, version; outbox; idempotent consumers).

-- One legacy SANCIN load.
CREATE TABLE sanction_intake_run (
  id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  public_id UUID NOT NULL UNIQUE,
  business_date DATE NOT NULL,
  status VARCHAR(10) NOT NULL,
  read_count INTEGER NOT NULL DEFAULT 0,
  applied_count INTEGER NOT NULL DEFAULT 0,
  not_found_count INTEGER NOT NULL DEFAULT 0,
  rejected_count INTEGER NOT NULL DEFAULT 0,
  failure_reason VARCHAR(500),
  started_at TIMESTAMPTZ NOT NULL,
  ended_at TIMESTAMPTZ,
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  version     INTEGER     NOT NULL DEFAULT 0
);

-- Sanction history (DB2 SANCTION, SANCTION.MASTER KSDS; D-018).
CREATE TABLE sanction (
  id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  public_id UUID NOT NULL UNIQUE,
  npi CHAR(10) NOT NULL,
  action CHAR(1) NOT NULL CHECK (action IN ('T','S','R')),
  reason VARCHAR(60),
  effective_date DATE NOT NULL,
  source_feed VARCHAR(20),
  recorded_by VARCHAR(100) NOT NULL,
  resulting_status CHAR(1) CHECK (resulting_status IN ('T','S','A')),
  intake_run_id BIGINT REFERENCES sanction_intake_run(id),
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  version     INTEGER     NOT NULL DEFAULT 0
);
CREATE INDEX sanction_npi ON sanction (npi);

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
