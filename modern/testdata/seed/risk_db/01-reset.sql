-- Journey start state (MAM-69 AC-8: every journey run starts from the seed state). Empties the journey tables of this
-- service; reference data (mappings, rule sets, templates) and processed_event are kept. Synthetic data only.
TRUNCATE risk_assessment, outbox_event RESTART IDENTITY;
-- MAM-38: rule set versions saved through PUT /api/v1/risk-rules are removed, so the seed state is version 1 again
-- (the PRVRSK01 table of V2__legacy_rule_set.sql, which is never touched).
DELETE FROM risk_rule WHERE rule_set_id IN (SELECT id FROM risk_rule_set WHERE rule_set_version > 1);
DELETE FROM risk_rule_set WHERE rule_set_version > 1;
