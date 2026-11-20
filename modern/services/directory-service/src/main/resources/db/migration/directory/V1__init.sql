-- directory-service V1 (from modern/docs/architecture/data-model/directory-service.sql, Phase 3 design).
-- Conventions: java-microservices skill section 8 (id, created_at, updated_at, version; outbox; idempotent consumers).

-- Read model from provider events; only ACTIVE rows are published (Q-008).
CREATE TABLE directory_entry (
  id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  npi CHAR(10) NOT NULL UNIQUE,
  medicaid_id CHAR(9),
  provider_type CHAR(2) NOT NULL,
  risk_tier CHAR(1),
  enroll_status CHAR(1) NOT NULL,
  display_name VARCHAR(60) NOT NULL,
  last_name VARCHAR(30),
  first_name VARCHAR(20),
  legal_name VARCHAR(60),
  taxonomy CHAR(10),
  license_no VARCHAR(15),
  license_state CHAR(2),
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  version     INTEGER     NOT NULL DEFAULT 0
);

-- One DIROUT file (PRVDIR01).
CREATE TABLE extract_run (
  id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  public_id UUID NOT NULL UNIQUE,
  business_date DATE NOT NULL,
  status VARCHAR(10) NOT NULL,
  row_count INTEGER NOT NULL DEFAULT 0,
  extract_file TEXT,
  failure_reason VARCHAR(500),
  started_at TIMESTAMPTZ NOT NULL,
  ended_at TIMESTAMPTZ,
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  version     INTEGER     NOT NULL DEFAULT 0
);

-- Idempotent consumers.
CREATE TABLE processed_event (
  event_id      UUID         PRIMARY KEY,       -- consumer idempotency
  processed_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
