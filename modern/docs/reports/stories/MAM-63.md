```
Phase / item:      MAM-63 Public provider directory search
Outcome:           DONE (AC-1..AC-7), In Review
What was done:     - Red: MAM-63.feature (14 scenarios), DirectorySearchSteps; 2 failed for the right reasons
                     (AC-4 providerType XX gave 200, AC-6 order was by name); evidence MAM-63/red-run.txt
                   - Green: ORDER BY npi in JdbcDirectoryStore.published; providerType must be IN, GR or FA
                     (400 problem+json, D-008) in DirectoryController
Files created/changed: modern/services/directory-service/src/main/java/.../infrastructure/JdbcDirectoryStore.java,
                   .../api/DirectoryController.java; tests MAM-63.feature, DirectorySearchSteps.java
Checks run:        ./gradlew :services:directory-service:check -> BUILD SUCCESSFUL, 29 tests 0 failures,
                   PIT 15/16 (94%); test_protection MAM-63, MAM-61 -> OK
Not verified:      independent code review; not yet deployed to the running stack
Questions for you: none
Next step:         MAM-60
```
