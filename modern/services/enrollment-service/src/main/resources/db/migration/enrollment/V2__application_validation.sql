-- MAM-46: submit and validate an application.
-- The feed provider-type codes named by MAM-46 AC-7 (D-008): IND, GRP and HOSP. Other feed codes are added only by a
-- decision; until then they fail E102.
INSERT INTO provider_type_mapping (feed_code, legacy_code, description) VALUES
  ('IND', 'IN', 'Individual (feed code)'),
  ('GRP', 'GR', 'Group (feed code)'),
  ('HOSP', 'FA', 'Hospital facility (feed code)')
ON CONFLICT (feed_code) DO NOTHING;

-- At most one VALIDATED application per NPI (E103, R-PRVENR01-ADD-04), also under concurrent submissions.
CREATE UNIQUE INDEX application_one_validated_per_npi ON application (npi) WHERE status = 'VALIDATED';
CREATE INDEX application_submitted_by ON application (submitted_by);
