---
name: jira-backlog
description: How the PEM backlog is structured and written to Jira. Covers epics, story template, acceptance-criteria rules, labels, the draft-then-create procedure, the Jira key map and the spec baseline files. Use in Phase 4 and whenever a story is read, commented on or transitioned.
---

# Jira backlog conventions

## 1. Before touching Jira

- Get from the user: Jira site URL, project key, and whether to use the Atlassian remote
  MCP (OAuth login in the browser, recommended) or an API token in `.env`.
- Discover, don't assume: through the MCP, read the project's issue types, workflow
  statuses and available fields. Write what you found into
  `modern/docs/backlog/jira-config.md`.
- If the project has no custom fields for our metadata, use **labels** (section 4).
- If Jira is not ready, work from local IDs (`L-E01` for epics, `L-S001` for stories) in
  `modern/docs/backlog/backlog-draft.md`. Create in Jira later with the same content.

## 2. Hierarchy

- **Epic** per business service (9: provider, enrollment, screening, risk, revalidation,
  sanctions, notification, directory, tmsis; api-gateway work goes under Platform and
  CI), plus these enabler epics: Platform and CI, Synthetic data and legacy formats, Web UI portal, Web UI
  console, End-to-end and hardening, Parity (deferred).
- **Story** per AWS KeyFunctionality that survives Phase 2, grouped under the owning
  service's epic. Split any story with more than 8 ACs.
- **Task** for technical enablers inside an epic.
- Every FIX or REPLACE decision in the defect register gets at least one AC that states
  the new behaviour.
- Non-functional needs (security, audit, accessibility, performance) are separate stories.

## 3. Story description template (exact headings, in this order)

```markdown
## Business requirement
<one paragraph, plain language, no code terms>

## Acceptance criteria
### AC-1 <short title>
Given <context>
When <action>
Then <observable result>

### AC-2 <short title>
...

## Out of scope
- <item>

## Legacy trace (agent-owned, do not edit)
Rules: <rule IDs>
Programs: <program and line ranges>
Decisions: <D-0xx and outcome>

## Technical notes (agent-owned)
Service: <service>. Endpoints/events: <list>. Roles: <roles>.
```

- AC IDs are stable. A new AC takes the next number; numbers are never reused.
- Every Then must be observable through an API, an event, a file or the UI.

## 4. Labels (when there are no custom fields)

`svc-<service>`, `kag-<story key or local id>`, `rule-<ruleId>` (one per rule; if Jira
limits labels, list the rules only in the Legacy trace section), `decision-<D-0xx>`,
`agentic`.

## 5. Procedure (Phase 4)

1. Write the full backlog to `modern/docs/backlog/backlog-draft.md`: epics, stories, ACs,
   dependencies, and a suggested build order.
2. **Show the user a summary and wait for approval.** Nothing goes to Jira before that.
3. Create the epics, then the stories, through the MCP. After each one, append a row to
   `modern/docs/backlog/jira-map.csv`: `local_id,jira_key,type,title,epic_key`.
4. Add "blocks" issue links for the dependencies.
5. Write `modern/specs/<KEY>.md` (section 6) for every story.
6. Hand the map to kag-curator to load Epic, Story and AcceptanceCriterion nodes.
7. If a create call fails, stop, report which items exist, and never create duplicates on
   retry: check `jira-map.csv` first.

## 6. Spec baseline file `modern/specs/<KEY>.md`

```markdown
---
key: <KEY>
epic: <EPIC KEY>
service: <service>
specVersion: 1
descriptionSha256: <sha256 of the Jira description, normalised: LF line endings, trimmed>
jiraUpdated: <timestamp from Jira>
rules: [<rule IDs>]
decisions: [<D-0xx>]
builtCommit:            # "pending-user-commit" once built (no commits until the user says "commit")
testsSha256:            # sha256 of modern/docs/reports/stories/<KEY>.tests.sha256, set when built
---
<the description exactly as written to Jira>
```

## 7. Comments and transitions

- Agent comments start with `[agent:<name>]` and contain the phase report for that story.
- Agents may move a story to the "in progress" and "in review" statuses (use the names
  found in step 1).
- **Agents never move a story to Done.** At the service gate, after the user approves,
  the orchestrator moves that service's reviewed stories to Done.
- Jira change loop (a human edits a story, agents rebuild): **open, not designed yet.**
  Follow MODERNIZATION.md section 8.
