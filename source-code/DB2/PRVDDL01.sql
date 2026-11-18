-- =================================================================
-- PRVDDL01 - DB2 SCHEMA FOR THE PROVIDER ENROLLMENT ESTATE
-- Reference only on TK5 (DB2 not installed). Models the relational
-- side of the provider record that coexists with the VSAM master.
--
-- ANTI-PATTERN: DUAL SYSTEM OF RECORD
--   The provider identity lives in BOTH the VSAM master
--   (MMPRV.PROVIDER.MASTER.KSDS) and these DB2 tables. They drift.
--   The modernization MDM golden record exists to resolve this.
-- =================================================================

CREATE DATABASE MMPRVDB;

CREATE TABLESPACE PROVTS IN MMPRVDB;

-- ----------------------------------------------------------------
-- PROVIDER - the relational provider master
-- ----------------------------------------------------------------
CREATE TABLE MMPRV.PROVIDER (
    NPI             CHAR(10)    NOT NULL,
    MEDICAID_ID     CHAR(9)     NOT NULL,
    PROVIDER_TYPE   CHAR(2)     NOT NULL,     -- IN / GR / FA
    TAXONOMY_CODE   CHAR(10),
    RISK_TIER       CHAR(1),                  -- L / M / H
    ENROLL_STATUS   CHAR(1)     NOT NULL,     -- P / A / T / D
    SCREEN_FLAG     CHAR(1)     NOT NULL,     -- U / P / F
    EFFECTIVE_DATE  DATE        NOT NULL,
    END_DATE        DATE,
    REVAL_DUE_DATE  DATE,
    LEGAL_NAME      VARCHAR(60),
    PRIMARY KEY (NPI)
) IN MMPRVDB.PROVTS;

-- ----------------------------------------------------------------
-- PROVIDER_OWNER - 5%+ owners & managing employees (42 CFR 455.104)
-- ----------------------------------------------------------------
CREATE TABLE MMPRV.PROVIDER_OWNER (
    NPI             CHAR(10)    NOT NULL,
    OWNER_NPI       CHAR(10),
    OWNER_NAME      VARCHAR(60) NOT NULL,
    OWNER_PCT       DECIMAL(5,2),
    DISCLOSURE_DATE DATE        NOT NULL,
    PRIMARY KEY (NPI, OWNER_NAME),
    FOREIGN KEY (NPI) REFERENCES MMPRV.PROVIDER (NPI)
) IN MMPRVDB.PROVTS;

-- ----------------------------------------------------------------
-- SCREENING - one row per external source verdict (audit trail)
-- ----------------------------------------------------------------
CREATE TABLE MMPRV.SCREENING (
    NPI             CHAR(10)    NOT NULL,
    SOURCE          CHAR(8)     NOT NULL,     -- NPPES/OIGLEIE/SAMGOV/...
    RESULT          CHAR(1)     NOT NULL,     -- P / F / C
    VERDICT_TS      TIMESTAMP   NOT NULL,
    DETAIL          VARCHAR(60),
    PRIMARY KEY (NPI, SOURCE, VERDICT_TS),
    FOREIGN KEY (NPI) REFERENCES MMPRV.PROVIDER (NPI)
) IN MMPRVDB.PROVTS;

-- ----------------------------------------------------------------
-- SANCTION - exclusion / termination / reinstatement history
-- ----------------------------------------------------------------
CREATE TABLE MMPRV.SANCTION (
    NPI             CHAR(10)    NOT NULL,
    ACTION          CHAR(1)     NOT NULL,     -- T / S / R
    REASON          VARCHAR(60),
    EFFECTIVE_DATE  DATE        NOT NULL,
    SOURCE_FEED     VARCHAR(20),              -- OIG LEIE / SAM / state
    PRIMARY KEY (NPI, EFFECTIVE_DATE, ACTION),
    FOREIGN KEY (NPI) REFERENCES MMPRV.PROVIDER (NPI)
) IN MMPRVDB.PROVTS;

CREATE INDEX MMPRV.PROVIDER_MCD ON MMPRV.PROVIDER (MEDICAID_ID);
CREATE INDEX MMPRV.PROVIDER_REVAL ON MMPRV.PROVIDER (REVAL_DUE_DATE);
