-- tmsis-service V4 (MAM-78, R-PRVTMS01-cbl-00005/00006, D-004, Q-060 default A): a run counts the ACTIVE providers whose
-- enrolment year is later than the business year (counts.futureEnrolments); the legacy only DISPLAYed them.
ALTER TABLE extract_run ADD COLUMN future_enrolment_count INTEGER NOT NULL DEFAULT 0;
