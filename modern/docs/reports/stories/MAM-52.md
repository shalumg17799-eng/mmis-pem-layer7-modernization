```
Phase / item:      MAM-52 Record a sanction (terminate, suspend, reinstate) with history and apply it through provider-service
Outcome:           DONE (fast-mode, gates postponed)
What was done:     - Spec check: Jira MAM-52 description = modern/specs/MAM-52.md (same 8 ACs; markdown spacing only);
                     dependsOn MAM-33 built first
                   - Red: first test scaffolding of sanctions-service (CucumberSpringConfig, CucumberSuiteTest,
                     TestProviderService = WireMock playing provider-service by its contract + the token endpoint),
                     MAM-52.feature (8 ACs, 10 scenarios incl. 2 outline examples each for AC-5 and AC-7),
                     SanctionSteps, SanctionActionTest (4), RecordSanctionTest (7). The scenarios failed on the
                     missing endpoints (404); the unit tests on the missing classes (MAM-52/red-run.txt)
                   - Green: POST /api/v1/sanctions (SUPERVISOR only): action T/S/R else 422 problem+json naming
                     action and T, S, R with no code (Q-B10 A); npi 10 digits, effectiveDate required, reason <= 60,
                     sourceFeed <= 20 (422); then provider-service POST /status-changes with TERMINATE / SUSPEND /
                     REINSTATE, effectiveDate, reason and the new sanctionId, SYSTEM token (D-013); 404 from it -> 404
                     naming the NPI; any other error or no answer -> 503 naming the NPI (Q-B13 A); only after an
                     applied change: sanction row (resultingStatus T/S/A, recordedBy = preferred_username,
                     recordedAt = service clock) + SanctionApplied in the outbox, one transaction (D-012, D-018);
                     no current-status check (D-027). GET /api/v1/sanctions?npi&page&size (ANALYST, SUPERVISOR,
                     ADMIN; newest first; defaults 0/20; bad npi/page/size 400)
Files created/changed: modern/services/sanctions-service/build.gradle.kts (testImplementation wiremock-standalone)
                   src/main/resources/sanctions-service.yml (pem.services.provider-url)
                   src/main/java/gov/medicaid/pem/sanctions/ domain/SanctionAction.java, domain/Sanction.java,
                     application/RecordSanction.java, ListSanctions.java, SanctionCommand.java, SanctionPage.java,
                     SanctionHistory.java, ProviderStatusPort.java, EventOutbox.java, SanctionAppliedData.java,
                     infrastructure/ProviderServiceClient.java, JdbcSanctionHistory.java, KitEventOutbox.java,
                     api/SanctionController.java (all new; no migration: the V1 sanction table already fits)
                   tests: src/test/resources/features/MAM-52.feature, TestProviderService.java,
                     acceptance/CucumberSpringConfig.java, CucumberSuiteTest.java, SanctionSteps.java,
                     domain/SanctionActionTest.java, application/RecordSanctionTest.java
Checks run:        ./gradlew --no-daemon :services:sanctions-service:spotlessApply :services:sanctions-service:test
                     -> BUILD SUCCESSFUL, 21 tests, 0 failures, 0 skipped (10 scenarios + 11 unit tests), 1st attempt
                   test_protection check MAM-52 -> OK (no other sanctions-service story has hashes)
Not verified:      the check gate (JaCoCo 85%, PIT 70%, ArchUnit PlatformChecksTest: none exists yet for this
                   service) is postponed (fast mode); no independent code review; not deployed to the running stack.
                   "GET /api/v1/providers/{npi} now shows status X" is verified against the WireMock stand-in of
                   provider-service (stateful per NPI, built from the provider-service contract), not against the real
                   service; the real endpoint is covered by MAM-33. If the history insert fails after provider-service
                   applied the change, the status stays changed with no history row (no compensation; not in any AC)
Questions for you: 1. Contract: sanctions-service.yaml lists no 503 for POST /api/v1/sanctions, but AC-7 / Q-B13
                      (default A) require it; Q-B13 A says "added to the contract at the next revision (additive,
                      user approval)". Options: A) add the 503 response to the contract now (recommended);
                      B) leave the contract as is until Phase 8
Next step:         MAM-58 (notification-service) consumes SanctionApplied
```
