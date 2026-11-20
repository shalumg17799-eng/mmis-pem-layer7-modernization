-- risk-service V1 (from modern/docs/architecture/data-model/risk-service.sql, Phase 3 design).
-- Conventions: java-microservices skill section 8 (id, created_at, updated_at, version; outbox; idempotent consumers).

-- Versioned rule table.
CREATE TABLE risk_rule_set (
  id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  rule_set_version INTEGER NOT NULL UNIQUE,  -- the business version of the rule set (optimistic locking uses "version")
  effective_from DATE NOT NULL,
  default_tier CHAR(1) NOT NULL DEFAULT 'L' CHECK (default_tier IN ('L','M','H')),
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  version     INTEGER     NOT NULL DEFAULT 0
);

-- Legacy: 332, 251 H; 341, 291, 225 M; default L (data, not code).
CREATE TABLE risk_rule (
  id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  rule_set_id BIGINT NOT NULL REFERENCES risk_rule_set(id),
  taxonomy_prefix CHAR(3) NOT NULL,
  tier CHAR(1) NOT NULL CHECK (tier IN ('L','M','H')),
  UNIQUE (rule_set_id, taxonomy_prefix),
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  version     INTEGER     NOT NULL DEFAULT 0
);

-- Every tier assigned.
CREATE TABLE risk_assessment (
  id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  npi CHAR(10),
  taxonomy CHAR(10) NOT NULL,
  tier CHAR(1) NOT NULL,
  rule_set_version INTEGER NOT NULL,
  assessed_at TIMESTAMPTZ NOT NULL,
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
