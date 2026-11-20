```
Phase / item:      MAM-35 Assign the risk tier from the taxonomy prefix after a passed screening
Outcome:           DONE (built test-first), In Review
What was done:     rule set v1 = the PRVRSK01 prefix table as data (Q-005 A): 332/251 H, 341/291/225 M, other L; only PASS screenings; RiskTierAssigned; failures logged with the correlation id and counted
Files created/changed: RuleSet (domain); AssessRisk, RuleSets, Assessments, EventOutbox, RiskTierAssignedData, TierMetrics (application); JdbcRiskStore, RiskAdapters, ScreeningEventsListener (infrastructure); V2__legacy_rule_set.sql in modern/services/risk-service (see MAM-35/trace.json)
Checks run:        ./gradlew :services:risk-service:check -> green, 28 tests, PIT 90 %; rule trace: all 34 rules have a passing test; live: J1 AC-3
Not verified:      independent code review per service (Part D)
Questions for you: none
Next step:         Part D gates (PIT, coverage, review)
```
Backfilled 2026-11-19 by the lead from the session record (PROGRESS.md activity rows and Jira report comments); the story was
built test-first earlier the same day.
