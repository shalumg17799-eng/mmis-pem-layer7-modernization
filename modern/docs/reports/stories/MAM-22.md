```
Phase / item:      MAM-22 Screen the owner of a group or facility (E203 owner excluded)
Outcome:           BUILT (build-phase: tests in Part D), batch A3
What was done:     - Spec check: Jira MAM-22 description = modern/specs/MAM-22.md (same 5 ACs)
                   - Built in screening-service: the OIGLEIE source looks up the provider's own NPI and, when the
                     provider record holds an owner NPI (organisation view only), the owner as a second lookup, in
                     parallel, each with the usual one retry (D-005, R-PRVSCR01-cbl-00033). SourceRules.leie(npi, own,
                     ownerNpi, owner): both listed -> F E201 "NPI <npi> AND OWNER <owner> EXCLUDED" (question C-6);
                     own only -> F E201 as before; owner only -> F E203 "OWNER <owner> EXCLUDED <type> SINCE <date>"
                     (D-016); neither -> P "NOT EXCLUDED"; either lookup unanswered -> C without a code (AC-5). One
                     OIGLEIE result per screening, as before
                   - AC status: AC-1 built, checked live (J3 set-up: analyst1 submitted GR 1003000444 with owner
                     1990000111 -> OIGLEIE F E203 "OWNER 1990000111 EXCLUDED 1128(a)(1) SINCE 2023-01-15", the other
                     three sources recorded, screening FAIL, listed by ?result=FAIL, screen flag F, status P); AC-2
                     built, checked live (GR 1003000222 owner 1990000999: OIGLEIE P; WireMock journal shows one lookup
                     of each NPI); AC-3 built, checked live for an IN provider (MANUAL screening of ALPHA: exactly one
                     LEIE lookup); the FA-without-owner case not checked live (no seeded organisation without an owner);
                     AC-4 built (rule above), not checked live (the LEIE mock lists only 1990000111); AC-5 built, not
                     checked live (the error stub needs the X-Pem-Stub header, which only a test sends)
Files created/changed: modern/services/screening-service/src/main/java/gov/medicaid/pem/screening/
                     domain/SourceRules (Exclusion record, leie with owner), infrastructure/HttpScreeningSources
Checks run:        ./gradlew --no-daemon :services:screening-service:test -> 49/49 existing tests pass; bootJar;
                   pem-apps restarted and healthy; live checks above; ./pem.sh seed afterwards
Not verified:      automated tests (Part D); AC-3 facility without owner, AC-4 and AC-5 live
Questions for you: 1. When the provider itself is listed but the owner lookup gets no answer, the result is C (the AC-5
                      wording "an owner lookup that fails is recorded as not answered"), so the E201 is not shown.
                      Options: A) keep C (literal AC-5, current build); B) F E201 for the provider, noting the owner
                      was not checked. Recommended: A until the business says otherwise
```
