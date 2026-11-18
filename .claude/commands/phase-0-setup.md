---
description: Phase 0. Check tools, set up git, verify the read-only protections, create the modern/ skeleton and PROGRESS.md.
---

# Phase 0: Setup

**Goal:** a safe, version-controlled workspace where agents cannot touch the legacy
estate, and a `modern/` folder ready for the work.

## Preconditions
None. This is the first phase.

## Steps

1. **Location check.** Confirm the current folder contains `CLAUDE.md`, `source-code/`,
   `aws-transform/` and `MODERNIZATION.md`. If any is missing, stop and tell the user.
2. **Import check.** The first line of `CLAUDE.md` must be `@MODERNIZATION.md`. If it is
   not, ask the user for permission, then add it. Change nothing else in `CLAUDE.md`.
3. **Tool check.** Run each command and build a table of tool, required, found, OK:
   `java -version` (21), `docker compose version`, `node -v` (≥ 20), `python3 --version`
   (≥ 3.11), `git --version`, `uv --version`. For anything missing, give the official
   install link. **Do not install system software yourself.**
4. **Git.**
   - If this is not a git repository, ask the user, then run `git init`.
   - Create or update `.gitignore` with these lines: `.env`, `.work/`,
     `modern/kag/.venv/`, `**/build/`, `**/.gradle/`, `**/node_modules/`,
     `modern/web-ui/dist/`.
   - Commit everything as `chore: baseline legacy estate and agent kit`.
   - Create and switch to branch `modernization`.
5. **Protection check.** These must be **refused** by the permission settings:
   - Try to create the file `source-code/.permcheck`.
   - Try to read `.env` (create an empty `.env` first if none exists, by asking the user
     to copy `.env.example`).

   If either is **not** refused, stop and tell the user that `.claude/settings.json` is
   not active.
6. **Create the `modern/` skeleton** (empty folders get a `.gitkeep`):
   ```
   modern/docs/{analysis/programs,architecture/data-model,adr,ux/wireframes,backlog,reports/stories,runbooks}
   modern/{kag/loaders,kag/tests,specs,contracts/openapi,contracts/asyncapi,services,libs,web-ui,infra,testdata,parity/golden,tools/data}
   ```
   Then:
   - Copy `.claude/templates/PROGRESS.md` to `modern/docs/PROGRESS.md`.
   - Create `modern/docs/analysis/questions.md` with the header `# Open business questions`.
   - Write `modern/README.md`: one line per folder saying what goes there.
7. **Kit check.** Confirm all 11 agents exist in `.claude/agents/` and all 10 skills exist
   in `.claude/skills/*/SKILL.md`, and that every file listed under "Read first" in each
   agent exists or is created in a later phase. List any problem.
8. Commit: `chore(phase-0): workspace and modern skeleton`.

## Exit checks
- [ ] The tool table shows everything OK, or the missing tools are listed for the user.
- [ ] Writing to `source-code/` and reading `.env` were both refused.
- [ ] `modern/docs/PROGRESS.md` exists. Branch is `modernization`. The working tree is clean.

## Finish
Update `modern/docs/PROGRESS.md`: phase 0 → `AWAITING APPROVAL`. Give the phase report
(MODERNIZATION.md section 9). Next step: `/phase-1-knowledge-graph`. **Stop.**
