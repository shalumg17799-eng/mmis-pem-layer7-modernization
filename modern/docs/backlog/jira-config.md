# Jira configuration (discovered 2026-11-18, jira-backlog skill section 1)

Read through the Atlassian MCP (claude.ai Atlassian connector; the project-scoped `atlassian` server in `.mcp.json` is
configured too). Nothing here is a secret.

| Item | Value |
|---|---|
| Site | https://radiantexp.atlassian.net (cloud id `71224e28-fd5d-4660-b356-22299a3a7a4e`) |
| Project | `MAM` "medicaid-AWSmainframe-modernization" (id 13202), team-managed |
| Board | 1831 "MAM board", simple board; columns To Do, In Progress, Done; rank field `customfield_10023` |
| Issues before Phase 4 | none (`project = MAM` returned 0 issues on 2026-11-18), so nothing to reuse or de-duplicate |

## Issue types

| Name | Id | Use |
|---|---|---|
| Epic | 13395 | one per business service plus the enabler epics |
| Story | 13396 | stories (business behaviour, NFRs, journeys) |
| Task | 13398 | technical enablers (platform, data) |
| Bug | 13397 | defects found in Phase 8 (the kit's "Defect story") |
| Subtask | 13394 | not used |

Epic and Story both exist. Stories and tasks are linked to their epic with the `parent` field (team-managed project).

## Workflow statuses (all issue types)

| Status | Id | Category | Agent use |
|---|---|---|---|
| To Do | 10000 | new | created, not started |
| In Progress | 3 | indeterminate | being built; also "in review" (see below) |
| Done | 10001 | done | **only the orchestrator, only after the user approves the service** |

**There is no "In Review" status.** The closest status is In Progress. A story whose build is finished and reported
stays In Progress and gets the label `in-review` plus the `[agent:...]` report comment (DEFAULT APPLIED - revisit,
Q-013). If an In Review column is added to board 1831 later, the agents move such stories there instead.

## Fields

Required to create a Story, Task or Epic: project, issue type, summary. Optional fields used by the agents:
description (markdown), labels, parent (the epic). Not used: Story point estimate (`customfield_10304`), Sprint
(`customfield_10022`), Team, Start date, Due date. There are no custom fields for our metadata, so labels carry it
(skill section 4): `agentic`, `svc-<service>`, `kag-<local id>`, `decision-<D-0xx>`, `slice-main-journey`; rule IDs
are listed only in the Legacy trace section of the description.

## Issue link types

Dependencies use **Blocks** (id 10000; outward "blocks", inward "is blocked by"): for "B depends on A", A blocks B.
