---
description: Show where the modernization stands. Covers phase and service status, open questions, blocked stories, git state and the next command to run. Changes nothing.
---

# Status

Read-only. Do not change any file.

1. Read `modern/docs/PROGRESS.md`. If it does not exist, say "Phase 0 has not run yet.
   Next: `/phase-0-setup`" and stop.
2. Count the questions with status OPEN in `modern/docs/analysis/questions.md`.
3. List the stories marked BLOCKED in `modern/docs/reports/service-*.md` and
   `ui-*.md`.
4. Run `git status --short | head -20` and `git log --oneline -5`.
5. Reply in this format:
   ```
   Current phase:     <highest phase not APPROVED> (<status>)
   Approved phases:   <list>
   Services:          <service: status, …>
   UI areas:          <area: status, …>
   Open questions:    <n> (top 3 by ID)
   Blocked stories:   <keys with one-line reason>
   Uncommitted files: <count>
   Next command:      <exact command>
   ```
