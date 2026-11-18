---
description: Phase 4. Connect Jira, draft the backlog from the approved design, get approval, create it in Jira, and write a spec baseline per story.
---

# Phase 4: Backlog in Jira

**Goal:** every non-dropped rule is covered by a Jira story with stable acceptance
criteria and a git baseline, in an agreed build order.

## Preconditions
Phase 3 is `APPROVED`.

## Steps

1. **Jira details (ask now).** Ask the user for:
   - the Jira site URL (e.g. `https://yourcompany.atlassian.net`)
   - the project key (or "create a new project", which they must do themselves in Jira)
   - the connection method: **Atlassian remote MCP with browser login (recommended)**, or
     an API token
   - whether Jira is ready now. If not, continue with local IDs (jira-backlog skill,
     section 1) and connect Jira later.
2. **MCP configuration.**
   - **Remote MCP:** run
     `claude mcp add --transport sse atlassian https://mcp.atlassian.com/v1/sse --scope project`.
     Check Atlassian's current docs for the endpoint; record the one used. Tell the user
     to restart Claude Code and authenticate the `atlassian` server through `/mcp`, then
     re-run this command. Note `Phase 4: waiting for Jira auth` in PROGRESS and **stop**.
     On the re-run, continue from step 3.
   - **API token:** the user puts `JIRA_SITE_URL`, `JIRA_EMAIL` and `JIRA_API_TOKEN` in
     `.env`. Then configure an MCP server that reads those variables, agreed with the
     user, and restart as above.
3. **Verify.** Through the MCP, read the project: its name, issue types and workflow
   statuses. Delegate to backlog-planner step 1 (`jira-config.md`).
4. **Draft.** Delegate to backlog-planner steps 2–4. Then check its coverage table
   yourself: zero uncovered rules.
5. **Human review.** Show the user:
   - the counts: epics, stories, ACs
   - the build order
   - the stories of one sample epic, in full

   Ask: "Create this in Jira? (yes / changes: …)". Apply the changes and show it again.
6. **Create.** On "yes", delegate to backlog-planner step 5. Afterwards check:
   - `jira-map.csv` has one row per drafted item
   - `modern/specs/` has one file per story
   - spot-check 2 stories in Jira against the draft
7. **Load.** kag-curator runs the Phase 4 loader. Run the "rules not covered by any
   story" query: it must be empty.
8. **Build order.** Fill the PROGRESS services table (order, service, NOT STARTED).
9. Commit: `docs(phase-4): backlog, jira map, spec baselines`.

## Exit checks
- [ ] Every non-dropped rule is DERIVED_FROM a story (graph query empty).
- [ ] Every story has an AC list with stable IDs and a `specs/<KEY>.md`.
- [ ] No duplicate issues in Jira (compare the map with a JQL search on `labels = agentic`).

## Finish
PROGRESS: phase 4 → `AWAITING APPROVAL`; add `Jira | 4 | <method>` to Connections. Give
the phase report. Next: `/phase-5-platform`. **Stop.**
