# HITL change loop: from an edited Jira story to tested code on a local branch

A person edits a requirement in Jira. Claude finds out what changed, tells you what it touches, and, only when you say so, builds the change test first on a **local
branch**. It never deploys, never pushes and never writes to Jira. Rules and design: `.claude/skills/hitl-change-loop/SKILL.md`.

## 1. Before you start
- The **Atlassian connector** is authorized in the Claude Code session (`/mcp`) and its tools are in the session. Without them `/fe-analyze` stops: it never works from the spec instead.
- Neo4j (`medicaid-kag`) is up and the variables `NEO4J_URI`, `NEO4J_USERNAME`, `NEO4J_PASSWORD` are loaded (see `HOW-TO-RUN.md`). Without it the plan says "legacy context not available" and goes on.
- For `/fe-approve`: Docker is running (the module checks use Testcontainers), you are on branch `modernization` with no uncommitted change of tracked files, and no other Gradle build runs.
- The story exists in `modern/specs/<KEY>.md` (its baseline). A story that was never built has no tests: the plan says so.

## 2. The three commands

| Step | You type | What happens | What it never does |
|---|---|---|---|
| 1 | edit the story in Jira, copy `modern/changes/TEMPLATE.md` to `modern/changes/<name>.md`, set `cr` and `jira` | the file only points at the ticket(s) | carry a requirement |
| 2 | `/fe-analyze modern/changes/<name>.md` | reads the ticket(s) from Jira (read-only), diffs description and ACs against `modern/specs/<KEY>.md` by AC id (added / changed / removed), analyses the impact, writes `modern/changes/<CR-ID>/plan.md`, **stops** | change code, tests, specs, git, or Jira |
| 3 | `/fe-approve <CR-ID>` | branch `cr/<CR-ID>` from `modernization`; the test-writer changes or adds the tests of the changed ACs and shows them **red**; the java-developer / ui-developer change the code until **green**; the affected checks and UI tests run; the spec copy on the branch is updated; one local commit (and one for the report); `report.md` | deploy, push, comment or move a Jira issue, touch tests of unchanged ACs, edit a test to make code pass |
| 4 | `/fe-discard <CR-ID>` | back to `modernization`; the local branch is deleted | touch anything but `cr/<CR-ID>` and its untracked generated folder |

## 3. What the plan contains (`modern/changes/<CR-ID>/plan.md`)
1. What changed in Jira, ticket by ticket: every AC added, removed or changed, with the old and the new text of each field; title, business requirement and out-of-scope changes. The two agent-owned sections are reported but never act as requirements.
2. The impact by rule -> story -> AC -> test -> code (from `modern/docs/traceability/rule-trace.csv` and the story's `trace.json`).
3. **Tests that must change**: only tests that cite a changed AC, each with the reason (the AC delta); new tests for added ACs; tests to remove for removed ACs. Tests of unchanged ACs are named as untouched.
4. **Files likely to change**: first the code of the rules the changed ACs cite, then the rest of the story's code.
5. Contracts (OpenAPI paths, AsyncAPI events), web UI files that show the named fields (a heuristic), batch jobs (modern batch endpoints, legacy programs and job steps), and the journeys that must run after a deploy.
6. The checks `/fe-approve` will run, the legacy context of the cited rules from Neo4j, and the risks (other stories citing the same rules, ACs without a test, contract questions).

## 4. What you get from `/fe-approve` (`modern/changes/<CR-ID>/report.md`, on the branch)
The Jira change; plan against actual (what was planned and touched, planned and not touched, touched and not planned); every file touched; the red evidence of the test-writer; the test results of the
affected modules; the test-protection check; the time taken per step; the git diff summary. The journeys are **listed, not run** (they need a deploy).

## 5. After the proof (your decisions, not the loop's)
Read `report.md` and look at the branch (`git log modernization..cr/<CR-ID>`, `git diff modernization...cr/<CR-ID>`). Then either merge or push the branch yourself, deploy and run the journeys, and update Jira (comment, transition), or run `/fe-discard <CR-ID>`.

## 6. The tools (`modern/tools/changes/`)
`fe_plan.py` (CR file + snapshots -> diff.json, impact.json, plan.md), `fe_diff.py`, `fe_impact.py`, `fe_tests.py` (re-records a story's test hashes with all its files), `fe_spec_update.py` (spec copy), `fe_report.py` (time marks, report).
Tests: `modern/kag/.venv/Scripts/python.exe -I -m pytest -q -p no:cacheprovider modern/tools/changes/tests`.

## 7. Troubleshooting
| Symptom | Cause and fix |
|---|---|
| `/fe-analyze` says the Atlassian tools are missing | authorize the connector with `/mcp` and restart Claude Code; the loop never falls back to the spec |
| exit code 3, "Jira snapshot missing" | the ticket was not written to `modern/changes/<CR-ID>/jira/<KEY>.json`; run `/fe-analyze` again |
| the plan says "no difference" although you edited the story | the edit is not saved in Jira, or only formatting changed (Markdown escapes, blanks); the plan prints both sha256 values |
| "Legacy context not available" | Neo4j variables not loaded or the graph is down; the rest of the plan is complete. The database name is `medicaid-kag` (override `PEM_KAG_DATABASE`) |
| a changed AC lists no test | no test cites that AC by name; the test-writer must find or write one, and the plan lists it as a risk |
| the checks fail on result files that look wrong | a filtered Gradle run replaced a module's result files: run the module's full `check`; run the UI tests last (`npm test`) |
| `/fe-approve` refuses to start | not on `modernization`, tracked files changed, or the branch `cr/<CR-ID>` exists: it names which |
