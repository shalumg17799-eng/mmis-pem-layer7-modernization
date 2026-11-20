-- MAM-60 AC-7: the ninth template named by the contract (Notice.template EXTRACT_READY). It is listed for
-- administrators; no event sends it yet (Q-A9: ExtractProduced gives no notice). Synthetic text.
INSERT INTO notice_template (code, subject, body) VALUES
  ('EXTRACT_READY', 'Extract ready', 'The T-MSIS provider extract is ready. Reference NPI {npi}.')
ON CONFLICT (code) DO NOTHING;
