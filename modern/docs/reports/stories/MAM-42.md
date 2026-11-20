```
Phase / item:      MAM-42 Fixed-width codec with layouts exported from the graph (PRVRECRD 130 bytes and every legacy file)
Outcome:           DONE (fast-mode; this story's own gate, :libs:fixed-width:check, was run and passes)
What was done:     - Spec check: Jira MAM-42 description = modern/specs/MAM-42.md (same 7 ACs; markdown spacing only)
                   - Red (earlier session, unchanged): 10 hashed test files, 64 methods / 257 invocations; evidence in
                     MAM-42/red-report.md (compile failure on the six missing classes only)
                   - Layouts exported, not typed: new read-only exporter modern/tools/data/export_layouts.py (READ
                     session, MATCH ... RETURN only) wrote layouts/PRVRECRD, SCRNREC, APPINPUT, APPERROR, SANCIN, REVALQ,
                     TMSISOUT, DIROUT .json from the Record/Field nodes of the graph database medicaid-kag; each file
                     carries its readQuery and source record id. Rules not in the graph are stated in the exporter with
                     their decision: date typing (D-004, D-015), REDEFINES views IN / GR+FA by PRV-TYPE-CODE (D-008),
                     APPINPUT = PRVRECRD + RESERVED 131-200 (D-025), elementary APPERROR = one X(250) field
                   - Green: FieldType, FieldDef, RecordLayout, FixedWidthException, FixedRecord, FixedWidthCodec
                     (ISO-8859-1 bytes; views by discriminator; read checks length, digits and CCYYMMDD validity; YYDDD
                     read only with the caller's century; too-long values and other-view access are errors)
Files created/changed: modern/libs/fixed-width/src/main/java/gov/medicaid/pem/fixedwidth/ FieldType.java, FieldDef.java,
                     RecordLayout.java, FixedWidthException.java, FixedRecord.java, FixedWidthCodec.java (all new)
                   modern/libs/fixed-width/src/main/resources/layouts/*.json (8 files, generated)
                   modern/tools/data/export_layouts.py (new)
                   tests: none changed (the 10 files of MAM-42.tests.sha256)
Checks run:        NEO4J_DATABASE=medicaid-kag modern/kag/.venv/Scripts/python.exe modern/tools/data/export_layouts.py
                     -> 8 layouts written (130/67/200/250/80/80/120/100 bytes)
                   ./gradlew --no-daemon :libs:fixed-width:spotlessApply :libs:fixed-width:check -> BUILD SUCCESSFUL,
                     257 tests, 0 failures, 0 skipped; JaCoCo lines 158/182 (86.8%, gate 85%) (1st attempt)
                   test_protection check MAM-42 -> OK
Not verified:      no code review; the services that depend on libs:fixed-width were not re-run after this change
                   (they had no use of it yet, so nothing calls the new classes); the Neo4j MCP server cannot read the
                   graph: its NEO4J_DATABASE is the old typo 'medicade-kag' (database not found); the exporter was run
                   with NEO4J_DATABASE=medicaid-kag for the process only. The user should fix .env (already noted in
                   PROGRESS.md Q-002)
Questions for you: none new (the test-writer's question on the all-zero date 00000000 stays open: today it is rejected
                   as an impossible date, option A)
Next step:         MAM-36 (platform: health, structured logs, correlation IDs)
```
