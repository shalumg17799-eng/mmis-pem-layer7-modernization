```
Phase / item:      MAM-72 Journey J2 end to end: an application is rejected
Outcome:           DONE (AC-1..AC-5), fast-mode (code review postponed), In Review
What was done:     J2 Playwright test on the live stack: empty taxonomy -> E104 in the summary (link focuses the
                   field) and at the field only -> Rejected with exactly one error row (E104/taxonomy, also via
                   GET by id) -> REJECTED notice in the API and "Application rejected (REJECTED)" on /portal/notices ->
                   provider still 404; axe on every page
Files created/changed: modern/web-ui/e2e/journeys/j2-rejected.spec.ts, docs/reports/stories/MAM-72*
Checks run:        ./pem.sh journey j2-rejected 3 -> 3/3 PASSED (9.9 s, 10.1 s, 11.3 s); without reseed -> fails at the
                   precondition (non-vacuity); test_protection MAM-72 -> OK
Not verified:      the ApplicationRejected payload inside J2 (Q-094 A: proven by MAM-46 on its topic); Chromium only
Questions for you: none
Next step:         J3 (MAM-74) after the screening stories, J4 (MAM-76) after MAM-37/77
```
