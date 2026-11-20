-- MAM-35: rule set version 1 = the PRVRSK01 table, kept exactly as data with no meanings attached (Q-005 option A):
-- prefixes 332 and 251 High, 341, 291 and 225 Moderate, everything else the default Limited.
INSERT INTO risk_rule_set (rule_set_version, effective_from, default_tier) VALUES (1, DATE '2000-01-01', 'L');
INSERT INTO risk_rule (rule_set_id, taxonomy_prefix, tier)
SELECT s.id, r.prefix, r.tier
FROM risk_rule_set s,
     (VALUES ('332', 'H'), ('251', 'H'), ('341', 'M'), ('291', 'M'), ('225', 'M')) AS r(prefix, tier)
WHERE s.rule_set_version = 1;
