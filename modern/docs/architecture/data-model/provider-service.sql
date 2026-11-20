-- provider-service: draft Flyway V1 schema for database provider_db (Phase 3 design; becomes
-- services/provider-service/src/main/resources/db/migration/V1__init.sql in Phase 5). Replaces: VSAM master + DB2 PROVIDER/PROVIDER_OWNER; status changes; PINQ lookup.
-- Conventions: java-microservices skill section 8 (id, created_at, updated_at, version; outbox; idempotent consumers).

-- The single provider golden record (VSAM master + DB2 PROVIDER; D-014). Only provider-service writes status, screen flag, risk tier and due date.
CREATE TABLE provider (
  id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  public_id UUID NOT NULL UNIQUE,
  npi CHAR(10) NOT NULL UNIQUE,
  medicaid_id CHAR(9) NOT NULL,
  provider_type CHAR(2) NOT NULL CHECK (provider_type IN ('IN','GR','FA')),
  taxonomy CHAR(10) NOT NULL,
  risk_tier CHAR(1) CHECK (risk_tier IN ('L','M','H')),
  enroll_status CHAR(1) NOT NULL CHECK (enroll_status IN ('P','A','T','D','S')),
  screen_flag CHAR(1) NOT NULL CHECK (screen_flag IN ('U','P','F')),
  effective_date DATE NOT NULL,
  end_date DATE,
  reval_due_date DATE,
  last_name VARCHAR(30),
  first_name VARCHAR(20),
  license_no VARCHAR(15),
  license_state CHAR(2),
  legal_name VARCHAR(60),
  dba_name VARCHAR(30),
  owner_npi CHAR(10),
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  version     INTEGER     NOT NULL DEFAULT 0
);
CREATE INDEX provider_medicaid_id ON provider (medicaid_id);   -- the legacy alternate index
CREATE INDEX provider_reval_due_date ON provider (reval_due_date);

-- 5%+ owners and managing employees (DB2 PROVIDER_OWNER).
CREATE TABLE provider_owner (
  id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  provider_id BIGINT NOT NULL REFERENCES provider(id),
  owner_npi CHAR(10),
  owner_name VARCHAR(60) NOT NULL,
  owner_pct NUMERIC(5,2),
  disclosure_date DATE NOT NULL,
  UNIQUE (provider_id, owner_name),
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  version     INTEGER     NOT NULL DEFAULT 0
);

-- Every provider state change (java-microservices skill section 8).
CREATE TABLE audit_log (
  id              BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  npi             CHAR(10)     NOT NULL,
  actor           VARCHAR(100) NOT NULL,
  action          VARCHAR(40)  NOT NULL,
  before_json     JSONB,
  after_json      JSONB,
  correlation_id  UUID,
  at              TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX audit_log_npi ON audit_log (npi, at);

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
