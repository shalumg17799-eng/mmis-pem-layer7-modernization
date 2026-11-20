-- Journey start state (MAM-69 AC-8: every journey run starts from the seed state). Empties the journey tables of this
-- service; reference data (mappings, rule sets, templates) and processed_event are kept. Synthetic data only.
TRUNCATE directory_entry RESTART IDENTITY;
