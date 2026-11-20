---
name: tdd-workflow
description: The mandatory test-first workflow for every story. Covers red/green/refactor steps, who may touch which files, test naming with story and AC IDs, the check that production code did not change tests, quality thresholds and failure handling. Use for every story in Phase 6 and Phase 7.
---

# Test-driven workflow (mandatory)

## 1. Roles

- **test-writer** writes and changes tests only: `src/test/**`, `features/**`,
  `web-ui/tests/**`, `web-ui/e2e/**`.
- **java-developer** and **ui-developer** write production code only. They never create,
  edit or delete a test file.
- **code-reviewer** reads and runs everything, and changes nothing.

## 2. The loop for one story

1. **Spec check.** Read the story (Jira or `modern/docs/backlog/backlog-draft.md`) and
   `modern/specs/<KEY>.md`. If they differ, stop and ask the user (MODERNIZATION.md
   section 8).
2. **Red.** test-writer writes the tests for every AC:
   - a Cucumber scenario per AC, tagged `@<KEY> @AC-<n>`
   - unit tests for the domain rules involved
   - an integration test (Testcontainers) for persistence and events, if the story has them
   - a contract test, if the story changes an endpoint or event

   Run the tests. **Every new test must fail, and fail for the expected reason**: an
   assertion failure, or a missing endpoint/class. A failure from broken unrelated code
   does not count. **No git commit** (user rule, 2026-11-18: nothing is committed until the
   user says "commit"; this replaces every commit step in the kit).
3. **Record the test hashes** (replaces the red commit). test-writer runs
   ```bash
   python modern/tools/checks/test_protection.py record <KEY> <every test file of the story>
   ```
   It writes `modern/docs/reports/stories/<KEY>.tests.sha256` (`sha256sum` format, one
   line per file, sorted by path) and keeps a restore copy of each file in git's object
   store (`git hash-object -w`, no commit), listed in
   `modern/docs/reports/stories/<KEY>/red-blobs.txt`. "Every test file" = the feature file,
   step definitions, unit, integration, contract, Vitest and Playwright files the story
   created or changed, including shared test support it touched.
4. **Green.** java-developer (or ui-developer) writes the least production code that
   makes the tests pass. At most 3 attempts.
5. **Test-protection check** (the orchestrator runs this after every green attempt):
   ```bash
   python modern/tools/checks/test_protection.py check <KEY>
   ```
   It recomputes the hashes and also lists test files that are in no `*.tests.sha256`
   file. The result must be `OK`. Any difference means a test was changed: restore it
   (`python modern/tools/checks/test_protection.py restore <KEY>`), then redo the green
   step (step 4) with a warning to the developer. A restore counts as an attempt.
6. **Refactor.** The developer improves names and removes duplication, with all tests
   still green. No behaviour change.
7. **Full verification.** Run `./gradlew :services:<svc>:check` (unit, acceptance,
   integration, contract, ArchUnit, JaCoCo, PIT), or `npm test && npm run e2e` for UI.
   Run the test-protection check again. No commit.
8. **Review.** code-reviewer reviews the story's changes (section 5): the test files in
   `<KEY>.tests.sha256` and the production files in `trace.json` (nothing is committed, so
   `git status --porcelain` and the file lists are the diff). Any MUST-FIX → back to step 4
   (it counts as an attempt).
9. **Done for the story** when every item in section 6 is true.

## 3. Naming

- Test display names start with the story and AC: `@DisplayName("[PEM-123 AC-2] taxonomy 341 gives Moderate tier")`.
- One Cucumber feature file per story: `src/test/resources/features/<KEY>.feature`.
- Test data comes from `modern/testdata/` or builders in `libs/test-support`; never real data.

## 4. Thresholds (enforced by the build)

- JaCoCo line coverage ≥ 85% for the service.
- PIT mutation score ≥ 70% on `domain` and `application` packages.
- Zero skipped or `@Disabled` tests. A test cannot be skipped to get to green.

## 5. Review checklist (code-reviewer)

- Every AC has at least one test naming its ID, and the test really asserts the AC
  (not just "no exception").
- No mocking of the class under test; mocks only at the boundaries (HTTP clients, Kafka).
- Behaviour matches the verified rule or the approved decision. Cite the rule ID.
- Contracts unchanged, or the change is approved.
- No secrets, no real data, no `LocalDate.now()` in business logic, no `double` for money
  or rates.
- Security: the endpoint has role checks, and role tests exist.
- Result: `APPROVE` or `MUST-FIX: <numbered list with file:line>`, plus optional
  `SHOULD-FIX` items.

## 6. Definition of Done (story)

- [ ] All AC tests pass; the full service check passes.
- [ ] The test-protection check output is empty.
- [ ] code-reviewer says APPROVE.
- [ ] Traceability written to `modern/docs/reports/stories/<KEY>/trace.json` (story, ACs, test IDs, code classes,
      rule IDs); then run `python modern/tools/trace/build_rule_trace.py` to refresh
      `modern/docs/traceability/rule-trace.csv` (traceability is in files and Jira, never in Neo4j).
- [ ] `modern/specs/<KEY>.md` updated: `builtCommit: pending-user-commit` and
      `testsSha256:` the sha256 of `modern/docs/reports/stories/<KEY>.tests.sha256`.
- [ ] Story report written in `modern/docs/reports/stories/<KEY>.md` (phase report format).

## 7. When things go wrong

- After 3 failed attempts, stop the story. Mark it BLOCKED in the service report, list
  the last error, what was tried, and options for the user.
- A flaky test (passes and fails with no change) is a defect. Report it; never retry
  until it passes.
- If making a test pass needs a contract or schema change in another service, stop and
  ask the user.
