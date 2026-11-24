---
description: Change loop, step 2. After /fe-analyze: create the local branch cr/<CR-ID>, change or add the tests of the changed ACs first and show them red, change the code until green, run the affected checks, update the spec copy, commit locally on the branch, write report.md. No deploy, no push, no Jira change.
argument-hint: <CR-ID, e.g. CR-001>
---

# /fe-approve `$ARGUMENTS`

Read `.claude/skills/hitl-change-loop/SKILL.md` and `.claude/skills/tdd-workflow/SKILL.md` first. Running this command is the user's decision to use the **Jira version** of the ticket(s) (MODERNIZATION.md section 8).
**Never: deploy, `git push`, any Jira write (comment, transition, edit, link), a change to a test of an unchanged AC, an edit of a test to make code pass.** The only commits are on the branch `cr/$ARGUMENTS`.

## Preconditions (stop with a clear message if one fails)
1. `$ARGUMENTS` matches `[A-Za-z0-9][A-Za-z0-9_.-]*` and `modern/changes/$ARGUMENTS/plan.md` exists with `Status: PLAN` (not `NO CHANGE`), plus `diff.json`, `impact.json` and `jira/*.json`.
2. Current branch `modernization`; `git status --porcelain` shows nothing except untracked files under `modern/changes/` (the CR file and the CR folder). Anything else: list it and stop.
3. No local branch `cr/$ARGUMENTS` exists (`git branch --list`). If it does: stop ("run /fe-discard $ARGUMENTS first").
4. Docker answers (`docker info`; the module checks start Testcontainers; do not touch any container of project `pem` or of another project), no other Gradle build is running, and `git rev-parse --short HEAD` works.
5. The Jira snapshot is still current: if the user says the ticket changed again since `/fe-analyze`, stop and ask for a new `/fe-analyze`.

## Steps
1. **Branch and clock.** `git switch -c cr/$ARGUMENTS modernization` (the untracked CR folder comes along). Then
   `modern/kag/.venv/Scripts/python.exe -I modern/tools/changes/fe_report.py mark $ARGUMENTS start --base <short hash of modernization> --branch cr/$ARGUMENTS`.
2. **Red: tests first.** Hand the **test-writer** (Agent tool, `subagent_type: test-writer`, wait for its completion notice) exactly: the plan's section "Tests that must change", the `acs` of `diff.json` (old and new text), the snapshot and the
   named test files. Its rules: change or add tests **only for the ACs in the plan** (names `[<KEY> AC-n] ...`, Cucumber tags `@<KEY> @AC-n`), remove the tests of removed ACs, leave the tests of unchanged ACs byte for byte as they are, weaken no assertion, write no
   production code. It records its work in `modern/docs/reports/stories/<KEY>/red-report.md` under a heading `## Change $ARGUMENTS (red)`: the tests changed, added, removed, why per AC, the command it ran and the failure reason of each test.
   Then **you** run the changed and new tests (Gradle: `./gradlew --no-daemon :services:<svc>:test --tests '<class>'`; UI: `npx vitest run <file>`; a filtered Gradle run replaces that module's result files, the full `check` below restores them).
   - **Every test of a changed or added AC must fail, for the expected reason** (an assertion on the new value, or a missing class or endpoint), not for broken unrelated code. A test of a changed AC that **passes** means the change is not covered: stop and report it.
   - Re-record the story's test hashes with all its files: `modern/kag/.venv/Scripts/python.exe -I modern/tools/changes/fe_tests.py record-all <KEY> --add <new or changed files> --remove <deleted files>`.
   - `fe_report.py mark $ARGUMENTS red`. Show the user the red result (tests and failure reasons) in one short table.
3. **Green: code.** Hand the **java-developer** (production code of services and libs) or the **ui-developer** (web UI) the failing tests, the plan's "Files likely to change" and the rule that **no test file may be created, edited or deleted**. At most 3 attempts. After each attempt run
   `python modern/tools/checks/test_protection.py check <KEY>`: any difference -> `restore <KEY>`, warn the developer, count an attempt. If a contract or a schema must change and the plan did not announce it: **stop and ask the user**. After 3 failed attempts: BLOCKED, stop, report the last
   error and what was tried, **leave the branch as it is** (no commit), tell the user about `/fe-discard`. `fe_report.py mark $ARGUMENTS green`.
4. **Checks.** Run, one Gradle build at a time, every Gradle task of the plan's section "Checks that /fe-approve will run" (`./gradlew --no-daemon <task>`: unit, acceptance, integration, contract, ArchUnit, JaCoCo 85 %, PIT 70 %), then `cd modern/web-ui && npm test` if the plan lists the UI or any UI file was touched.
   All must pass with 0 failures and 0 skipped; then `test_protection.py check <KEY>` must print OK. Failures from a check that the change caused: back to step 3 (an attempt). The journeys of the plan **are not run** (they need a deployed stack; this loop does not deploy): they go into the report as "to run after a deploy".
   `fe_report.py mark $ARGUMENTS checks`.
5. **Spec copy and traceability (on the branch).** For each changed ticket: `modern/kag/.venv/Scripts/python.exe -I modern/tools/changes/fe_spec_update.py <KEY> modern/changes/$ARGUMENTS/jira/<KEY>.json`, then `.../modern/tools/trace/close_story.py <KEY>`,
   then `.../modern/tools/trace/build_rule_trace.py`. Append a short section `## Change $ARGUMENTS` to `modern/docs/reports/stories/<KEY>.md` (what changed, tests red -> green, checks). `fe_report.py mark $ARGUMENTS spec`.
6. **Secret check and local commit.** `modern/kag/.venv/Scripts/python.exe -I modern/tools/checks/scan_secrets.py` must be CLEAN. Stage **explicit paths only** (the files `git status` lists under `modern/` that belong to this change; never `git add -A`; never build output, `.env`, scratch). Commit on the branch:
   `change($ARGUMENTS): <KEY> <short summary of the changed ACs>` with the trailer that the session's git instructions give. No push.
7. **Report.** `fe_report.py mark $ARGUMENTS end`, then `modern/kag/.venv/Scripts/python.exe -I modern/tools/changes/fe_report.py report $ARGUMENTS --gradle <the tasks you ran> [--ui]`. Read `modern/changes/$ARGUMENTS/report.md`; fix anything that says MISSING (for example a red section that the test-writer did not write)
   and add what the tool cannot know under `## Notes (assistant)`. Then `git add modern/changes/$ARGUMENTS && git commit -m "change($ARGUMENTS): report"` on the branch.
8. **Tell the user**, in one message: the branch and its two commits, the Jira change (ACs old -> new), the red -> green evidence in a short table, the check results, the files touched against the plan, the time taken, and the line
   "Local only: no deploy, no push, no Jira change. `report.md` has the details. To throw it away: `/fe-discard $ARGUMENTS`." Stay on the branch (the user inspects it); do not switch back by yourself.

## If something goes wrong
- Any step fails twice: stop, report what failed and the state of the branch. Do not improvise around a failed check, a failed test-protection check or a missing red.
- If the session stops midway the branch and `state.json` show where it was: say so and continue from the last mark only after the user confirms.
