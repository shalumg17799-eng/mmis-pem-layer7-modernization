```
Phase / item:      MAM-62 Web UI shell: sign-in, menu per role, access denied, sign-out
Outcome:           DONE (built test-first), In Review
What was done:     Keycloak sign-in (PKCE), menu per role, access-denied page, sign-out and session end, accessible shell structure; portal home with the applications list and provider summary
Files created/changed: App, AppShell, RequireRole, screens, pages/AccessDenied, Landing, Placeholder, SignInPages, SignedOut in modern/web-ui/src/app (see MAM-62/trace.json)
Checks run:        npm test (shell + portal home) green; Playwright e2e/ui-shell.spec.ts green; code-reviewer APPROVE (review-2)
Not verified:      independent code review per service (Part D)
Questions for you: none
Next step:         Part D gates (PIT, coverage, review)
```
Backfilled 2026-11-19 by the lead from the session record (PROGRESS.md activity rows and Jira report comments); the story was
built test-first earlier the same day.
