-- tmsis-service V1 (from modern/docs/architecture/data-model/tmsis-service.sql, Phase 3 design).
-- Conventions: java-microservices skill section 8 (id, created_at, updated_at, version; outbox; idempotent consumers).

-- One T-MSIS extract (PRVMTH01 STEP020; D-015).
CREATE TABLE extract_run (
  id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  public_id UUID NOT NULL UNIQUE,
  business_date DATE NOT NULL,
  period_yyddd CHAR(5) NOT NULL,
  status VARCHAR(10) NOT NULL,
  row_count INTEGER NOT NULL DEFAULT 0,
  failure_reason VARCHAR(500),
  started_at TIMESTAMPTZ NOT NULL,
  ended_at TIMESTAMPTZ,
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  version     INTEGER     NOT NULL DEFAULT 0
);

-- One 120-byte TMSISOUT row.
CREATE TABLE extract_row (
  id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  extract_run_id BIGINT NOT NULL REFERENCES extract_run(id),
  record_type CHAR(5) NOT NULL DEFAULT 'PRV01',
  medicaid_id CHAR(9) NOT NULL,
  npi CHAR(10) NOT NULL,
  status CHAR(1) NOT NULL,
  period_yyddd CHAR(5) NOT NULL,
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  version     INTEGER     NOT NULL DEFAULT 0
);

-- Archive generations (GDG MMPRV.TMSIS.ARCHIVE(+1); PRVMTH01 STEP030).
CREATE TABLE extract_archive (
  id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  extract_run_id BIGINT NOT NULL REFERENCES extract_run(id),
  generation INTEGER NOT NULL UNIQUE,
  content TEXT NOT NULL,
  archived_at TIMESTAMPTZ NOT NULL,
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  version     INTEGER     NOT NULL DEFAULT 0
);

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
