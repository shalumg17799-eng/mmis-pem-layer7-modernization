-- MAM-45: the sweep run also counts the ACTIVE providers it could not evaluate because they have no due date
-- (Q-049 / C-11 answer A: not queued, made visible), read through the run-status call (Q-046 / C-8 answer A).
ALTER TABLE sweep_run ADD COLUMN no_due_date_count INTEGER NOT NULL DEFAULT 0;
