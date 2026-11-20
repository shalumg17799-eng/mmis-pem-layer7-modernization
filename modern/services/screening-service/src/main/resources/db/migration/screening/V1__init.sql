-- screening-service V1 (from modern/docs/architecture/data-model/screening-service.sql, Phase 3 design).
-- Conventions: java-microservices skill section 8 (id, created_at, updated_at, version; outbox; idempotent consumers).

-- One screening batch (PRVDAY01 STEP030).
CREATE TABLE screening_run (
  id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  public_id UUID NOT NULL UNIQUE,
  business_date DATE NOT NULL,
  status VARCHAR(10) NOT NULL,
  provider_count INTEGER NOT NULL DEFAULT 0,
  fail_count INTEGER NOT NULL DEFAULT 0,
  results_file TEXT,
  failure_reason VARCHAR(500),
  started_at TIMESTAMPTZ NOT NULL,
  ended_at TIMESTAMPTZ,
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  version     INTEGER     NOT NULL DEFAULT 0
);

-- One screening of one provider.
CREATE TABLE screening (
  id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  public_id UUID NOT NULL UNIQUE,
  npi CHAR(10) NOT NULL,
  reason VARCHAR(12) NOT NULL CHECK (reason IN ('INITIAL','REVALIDATION','MANUAL')),
  status VARCHAR(10) NOT NULL CHECK (status IN ('RUNNING','COMPLETED')),
  result VARCHAR(4) CHECK (result IN ('PASS','FAIL')),
  run_id BIGINT REFERENCES screening_run(id),
  started_at TIMESTAMPTZ NOT NULL,
  completed_at TIMESTAMPTZ,
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  version     INTEGER     NOT NULL DEFAULT 0
);
CREATE INDEX screening_npi ON screening (npi);

-- One verdict per source: the 42 CFR 455 audit trail (SCRNREC, DB2 SCREENING, SCREEN.RESULTS KSDS; D-005, D-018).
CREATE TABLE screening_result (
  id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  screening_id BIGINT NOT NULL REFERENCES screening(id),
  source VARCHAR(8) NOT NULL CHECK (source IN ('NPPES','OIGLEIE','SAMGOV','LICBOARD','SSADMF')),
  result CHAR(1) NOT NULL CHECK (result IN ('P','F','C')),
  verdict_at TIMESTAMPTZ NOT NULL,
  detail VARCHAR(60),
  error_code CHAR(4) CHECK (error_code IN ('E201','E202','E203','E204','E205')),
  UNIQUE (screening_id, source),
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
