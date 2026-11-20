-- notification-service V1 (from modern/docs/architecture/data-model/notification-service.sql, Phase 3 design).
-- Conventions: java-microservices skill section 8 (id, created_at, updated_at, version; outbox; idempotent consumers).

-- TRM, SUS, RST, GEN (the missing PRVNT* programs) plus new notices.
CREATE TABLE notice_template (
  id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  code VARCHAR(20) NOT NULL UNIQUE,
  subject VARCHAR(120) NOT NULL,
  body TEXT NOT NULL,
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  version     INTEGER     NOT NULL DEFAULT 0
);

-- Notice log (D-012).
CREATE TABLE notice (
  id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  public_id UUID NOT NULL UNIQUE,
  npi CHAR(10) NOT NULL,
  template_code VARCHAR(20) NOT NULL,
  channel VARCHAR(10) NOT NULL DEFAULT 'EMAIL',
  recipient VARCHAR(200),
  status VARCHAR(10) NOT NULL CHECK (status IN ('SENT','FAILED')),
  event_id UUID NOT NULL,
  sent_at TIMESTAMPTZ,
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
