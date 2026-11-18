# How to run the PEM modernization with Claude Code

## 1. Prerequisites on your machine

| Tool | Version | Check |
|---|---|---|
| Claude Code | latest | `claude --version` |
| Git | any recent | `git --version` |
| Java (JDK) | 21 | `java -version` |
| Docker + Compose | Docker 24+, Compose v2 | `docker compose version` |
| Node.js | 20+ | `node -v` |
| Python | 3.11+ | `python3 --version` |
| uv (to run the Neo4j MCP server) | any | `uv --version` |

Gradle does not need to be installed: Phase 5 adds the Gradle wrapper.

## 2. Install the kit

1. Copy everything in this kit into the **root of your `Medco-Transform` folder**, next to
   `CLAUDE.md`, `source-code/` and `aws-transform/`:
   - `MODERNIZATION.md`
   - `HOW-TO-RUN.md`
   - `.claude/` (agents, skills, commands, settings.json)
   - `.env.example`
2. Open `CLAUDE.md` and add this as its **first line**:
   ```
   @MODERNIZATION.md
   ```
3. Copy `.env.example` to `.env`. Leave the values empty for now: each phase tells you
   which ones to fill in.

## 3. Start Claude Code with your environment loaded

macOS / Linux:
```bash
cd Medco-Transform
set -a && source .env && set +a && claude
```
Windows PowerShell:
```powershell
cd Medco-Transform
Get-Content .env | ForEach-Object { if ($_ -match '^\s*([^#=]+)=(.*)$') { [Environment]::SetEnvironmentVariable($matches[1], $matches[2]) } }
claude
```
Restart Claude Code this way whenever you change `.env`.

## 4. Run the phases, one at a time

| Step | Type in Claude Code | What you do afterwards |
|---|---|---|
| 0 | `/phase-0-setup` | Read the report, then type `approve phase 0` |
| 1 | `/phase-1-knowledge-graph` | Give Neo4j details when asked; approve |
| 2 | `/phase-2-legacy-analysis` | Answer the business questions; approve |
| 3 | `/phase-3-architecture` | Review services, contracts, wireframes; approve |
| 4 | `/phase-4-backlog` | Give Jira details; review the draft before it goes to Jira; approve |
| 5 | `/phase-5-platform` | Choose Postgres (local container or yours); approve |
| 6 | `/phase-6-build-service <service>` | Run once per service, in the order Phase 4 sets; approve each |
| 7 | `/phase-7-build-ui <area>` | Run for `portal`, then `console`; approve each |
| 8 | `/phase-8-e2e-hardening` | Approve |
| 9 | `/phase-9-parity` | Later, once you have legacy job outputs from the other environment |

At any time: `/status` shows where you are.

To reject a phase: `reject phase <n>: <what to fix>`.

## 5. Where to look

- `modern/docs/PROGRESS.md`: phase status, decisions, open questions, configured connections.
- `modern/docs/analysis/questions.md`: everything waiting on a business answer.
- `modern/docs/reports/`: one report per phase and per service.

## 6. What is intentionally left open

- **The Jira change loop** (a human edits a story and agents rebuild from the change).
  It will be designed later. Until then, Claude stops if a story in Jira differs from its
  baseline in `modern/specs/`.
- **Parity with legacy outputs** (Phase 9). It waits until you run the legacy jobs in
  another environment. The capture checklist is in `.claude/skills/parity-testing/SKILL.md`.
