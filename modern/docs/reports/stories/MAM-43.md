```
Phase / item:      MAM-43 Export the provider master in the legacy 130-byte PRVRECRD layout
Outcome:           BUILT (build-phase: tests in Part D), batch A5 (not a batch job: no Spring Batch)
What was done:     - Spec check: modern/specs/MAM-43.md (8 ACs); Jira not re-read in this batch
                   - GET /api/v1/legacy/provider-master (ADMIN), text/plain: every provider in ascending NPI order, one
                     130-byte PRVRECRD record per line (lines joined by LF, no header or trailer)
                   - adapter/legacy/PrvrecrdFile through the MAM-42 codec (layout PRVRECRD): the type code selects the
                     REDEFINES view (IN: last name, first name, licence, state, filler; GR/FA: legal name 40, DBA name
                     30, owner NPI in 121-130); alphanumerics right-padded and cut on the right when longer (only in this
                     legacy writer); dates CCYYMMDD; an unset date stays 00000000 and an unset risk tier a space
                   - application/ExportProviderMaster with its ports AllProviders (JpaProviderStore.allByNpi) and
                     MasterFile; api/LegacyFileController
                   - AC status: AC-1 built, checked live (16 seeded providers -> 16 lines of exactly 130 characters);
                     AC-2 built, checked live (ascending NPI, no header or trailer); AC-3 built, checked live on the IN
                     providers (every field position equals the API value); AC-4 built, checked live on the GR/FA
                     providers (legal name 51-90, DBA 91-120, owner NPI 121-130); the cut of a legal name over 40
                     characters is coded, not seen live (no such seeded name); AC-5 built, checked live (FOXTROT and
                     THERAPY GROUP byte 33 'S'); AC-6 built, checked live (PENDING DELTA/HHA: risk tier space, due date
                     00000000, flag U); AC-7 checked live (every line decoded field by field equals GET
                     /api/v1/providers for that NPI: 0 differences); AC-8 built, checked live (ADMIN 200; ANALYST,
                     SUPERVISOR, PROVIDER, SYSTEM 403; no token 401)
Files created/changed: modern/services/provider-service/src/main/java/gov/medicaid/pem/provider/
                     application/ExportProviderMaster (new), adapter/legacy/PrvrecrdFile (new),
                     api/LegacyFileController (new), infrastructure/JpaProviderStore (allByNpi)
Checks run:        compile; ./gradlew --no-daemon :services:provider-service:test (see the batch line); bootJar; pem-apps
                   healthy; live checks above
Not verified:      automated tests (Part D); the 40-character cut of a long legal name live
Questions for you: none
```
