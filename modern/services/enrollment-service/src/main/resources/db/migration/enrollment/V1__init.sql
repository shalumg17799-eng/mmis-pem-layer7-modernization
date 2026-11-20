-- enrollment-service V1 (from modern/docs/architecture/data-model/enrollment-service.sql, Phase 3 design).
-- Conventions: java-microservices skill section 8 (id, created_at, updated_at, version; outbox; idempotent consumers).

-- One legacy APPINPUT load (PRVDAY01 STEP010).
CREATE TABLE intake_run (
  id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  public_id UUID NOT NULL UNIQUE,
  business_date DATE NOT NULL,
  batch_id VARCHAR(8) NOT NULL,
  status VARCHAR(10) NOT NULL CHECK (status IN ('RUNNING','COMPLETED','FAILED')),
  read_count INTEGER NOT NULL DEFAULT 0,
  written_count INTEGER NOT NULL DEFAULT 0,
  error_count INTEGER NOT NULL DEFAULT 0,
  skipped_count INTEGER NOT NULL DEFAULT 0,
  error_file TEXT,
  failure_reason VARCHAR(500),
  started_at TIMESTAMPTZ NOT NULL,
  ended_at TIMESTAMPTZ,
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  version     INTEGER     NOT NULL DEFAULT 0
);

-- Applications from the portal or a legacy APPINPUT file (raw 200-byte record kept; D-025).
CREATE TABLE application (
  id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  public_id UUID NOT NULL UNIQUE,
  npi CHAR(10),
  medicaid_id CHAR(9),
  provider_type CHAR(2),
  taxonomy CHAR(10),
  last_name VARCHAR(30),
  first_name VARCHAR(20),
  license_no VARCHAR(15),
  license_state CHAR(2),
  legal_name VARCHAR(60),
  dba_name VARCHAR(30),
  owner_npi CHAR(10),
  status VARCHAR(10) NOT NULL CHECK (status IN ('SUBMITTED','VALIDATED','REJECTED')),
  source VARCHAR(12) NOT NULL CHECK (source IN ('PORTAL','LEGACY_FILE')),
  submitted_by VARCHAR(100),
  submitted_at TIMESTAMPTZ NOT NULL,
  intake_run_id BIGINT REFERENCES intake_run(id),
  raw_record CHAR(200),
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  version     INTEGER     NOT NULL DEFAULT 0
);

-- Exactly one row per failed check (D-001, D-016).
CREATE TABLE application_error (
  id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  application_id BIGINT NOT NULL REFERENCES application(id),
  code CHAR(4) NOT NULL CHECK (code IN ('E101','E102','E103','E104','E201','E202','E203','E204','E205')),
  field VARCHAR(40),
  message VARCHAR(200),
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  version     INTEGER     NOT NULL DEFAULT 0
);

-- Feed provider-type codes to IN/GR/FA (D-008); seeded in Phase 5.
CREATE TABLE provider_type_mapping (
  id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  feed_code VARCHAR(4) NOT NULL UNIQUE,
  legacy_code CHAR(2) NOT NULL CHECK (legacy_code IN ('IN','GR','FA')),
  description VARCHAR(60),
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
