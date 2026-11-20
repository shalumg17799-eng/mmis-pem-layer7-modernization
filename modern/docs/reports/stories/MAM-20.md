```
Phase / item:      MAM-20 Handle unknown licences, organisation providers and sources that fail or are slow
Outcome:           BUILT (build-phase: tests in Part D), batch A3
What was done:     - Spec check: Jira MAM-20 description = modern/specs/MAM-20.md (same 5 ACs)
                   - AC-1, AC-3 and AC-4 were already met by MAM-18/MAM-25 (unreachable source C "COULD NOT BE
                     REACHED" after one retry; unknown licence F "LICENCE NOT FOUND" without a code; organisation
                     LICBOARD P "NOT APPLICABLE" with no lookup)
                   - AC-2 built: a source whose last attempt ran out of its 2-second limit (HttpTimeoutException or
                     SocketTimeoutException in the cause chain) is recorded as C without a code, detail "DID NOT ANSWER
                     IN TIME" (SourceRules.timedOut); any other failure keeps "COULD NOT BE REACHED"
                   - AC-5 built: provider-service answering 5xx or not answering is reported by ProviderServiceClient as
                     ProviderFactsSource.Unavailable (a 4xx such as 404 is passed on unchanged). ProviderReadRetry sets a
                     back-off function on the kit's DefaultErrorHandler in screening-service only: for that failure the
                     event is retried with an exponential pause (1 s doubling, capped by
                     pem.screening.provider-retry-max-interval, default 60 s) with no attempt limit, instead of 2
                     retries then DLT; the listener transaction (processed_event, screening, outbox) rolls back with each
                     failed attempt, so nothing is COMPLETED or published until provider-service answers, then exactly
                     once. Every other failure keeps the kit's retries and DLT
                   - AC status: AC-1 built (earlier), not re-checked live (error stub needs the X-Pem-Stub header);
                     AC-2 built, not checked live (same reason); AC-3 built (earlier), not re-checked live; AC-4 built
                     (earlier), checked live in passing (GR 1003000444 and 1003000222: LICBOARD P NOT APPLICABLE); AC-5
                     built, not checked live (provider-service cannot be made to fail without stopping a container)
Files created/changed: modern/services/screening-service/src/main/java/gov/medicaid/pem/screening/
                     application/ProviderFactsSource (Unavailable), domain/SourceRules (timedOut),
                     infrastructure/HttpScreeningSources (timeout detail), ProviderServiceClient (5xx/no answer ->
                     Unavailable), ProviderReadRetry (new)
Checks run:        ./gradlew --no-daemon :services:screening-service:test -> 49/49 existing tests pass; bootJar;
                   pem-apps restarted and healthy; live checks above
Not verified:      automated tests (Part D); AC-1, AC-2, AC-5 live. AC-2's 5-second budget: 2 attempts x 2 s = 4 s plus
                   the provider read, so it is close to the limit; a Part D test must measure it
Questions for you: none
```
