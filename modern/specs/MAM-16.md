---
key: MAM-16
localId: L-S001
epic: MAM-1
service: platform
type: Task
title: "Gradle multi-project build with quality gates (JaCoCo, PIT, ArchUnit, Spotless)"
slug: plt-build
specVersion: 1
descriptionSha256: ac80cdbf335761e3ee5d8c483ec8721c8467a9508960997dd9412741075b21d8
jiraUpdated: 2026-11-18T09:14:01.377-0600
rules: []
decisions: []
dependsOn: []
mainJourney: true
labels: [svc-platform, kag-L-S001, agentic, slice-main-journey]
builtCommit:
testsSha256:
---
## Business requirement
The delivery team needs one repeatable way to build every service and shared library on the agreed technology version, and a build that refuses to accept work that falls below the agreed quality bar (test coverage, test strength, architecture rules, code formatting). With this in place every later story is built and checked in exactly the same way.

## Acceptance criteria
### AC-1 One command builds everything on Java 21
Given a clean checkout of modern/ on a machine with Docker and Java 21, where Gradle is not installed locally
When the Gradle wrapper is generated once with a pinned gradle JDK 21 Docker image and ./gradlew build is run from modern/
Then the build exits with code 0 and every compiled class in every module targets Java 21 (class file major version 65)
Basis: .claude/commands/phase-5-platform.md step 2, .claude/agents/devops-engineer.md deliverable 1, modern/docs/adr/ADR-010-versions.md, .claude/skills/java-microservices/SKILL.md section 4

### AC-2 One module per service and library, standard package layout
Given settings.gradle.kts of modern/
When ./gradlew projects is run and the module folders are listed
Then the output lists api-gateway, the nine business services (provider, enrollment, screening, risk, revalidation, sanctions, notification, directory, tmsis), libs:fixed-width and libs:test-support; each business service has the packages gov.medicaid.pem.<svc>.api, .application, .domain and .infrastructure, plus .adapter.legacy in the services that keep a legacy file (provider, enrollment, screening, revalidation, sanctions, directory, tmsis)
Basis: .claude/skills/java-microservices/SKILL.md sections 1 and 5, .claude/agents/devops-engineer.md deliverable 2, modern/docs/adr/ADR-001-service-decomposition.md, modern/docs/adr/ADR-005-legacy-format-adapters.md

### AC-3 Coverage gate: 85% line coverage per service
Given a service module whose JaCoCo line coverage is 84%
When ./gradlew :services:<svc>:check is run
Then the build fails with a coverage-verification violation that names the 85% minimum, and the same check passes when coverage is 85% or higher (proved on a real module and recorded in the platform report, or by the build configuration lines)
Basis: .claude/skills/java-microservices/SKILL.md section 12, .claude/skills/tdd-workflow/SKILL.md section 4, .claude/agents/devops-engineer.md deliverable 1, .claude/commands/phase-5-platform.md step 5

### AC-4 Mutation gate: 70% on domain and application
Given a service module whose PIT mutation score on the domain and application packages is 69%
When ./gradlew :services:<svc>:check is run
Then the build fails naming the 70% threshold; at 70% or higher it passes; classes outside domain and application are not mutated
Basis: .claude/skills/java-microservices/SKILL.md section 12, .claude/skills/tdd-workflow/SKILL.md section 4, .claude/agents/devops-engineer.md deliverable 1

### AC-5 Architecture rules fail the build
Given a class in a domain package that imports a Spring class or a class of another layer, or a class in an api package that uses an infrastructure class directly
When ./gradlew :services:<svc>:check is run
Then the build fails and names the violated ArchUnit rule; code that respects the layering passes
Basis: .claude/skills/java-microservices/SKILL.md section 5, .claude/agents/devops-engineer.md deliverable 1

### AC-6 Formatting is checked and fixable
Given a Java source file that does not follow the project format
When ./gradlew spotlessCheck (part of check) is run, then ./gradlew spotlessApply
Then spotlessCheck fails and lists the file; after spotlessApply the same check passes
Basis: .claude/agents/devops-engineer.md deliverable 1

### AC-7 A skipped test cannot reach green
Given a test annotated @Disabled, or skipped at run time
When ./gradlew :services:<svc>:check is run
Then the build fails and names the skipped test
Basis: .claude/skills/tdd-workflow/SKILL.md section 4

### AC-8 Every version is pinned and recorded
Given modern/gradle/libs.versions.toml
When the build's version check runs
Then it fails on any dynamic version (+, latest, a range); every library and plugin has an exact version, Spring Boot is the latest 3.x GA at Phase 5 with its matching Spring Cloud release train, and the exact versions are recorded in the ADR Versions (ADR-010)
Basis: modern/docs/adr/ADR-010-versions.md, .claude/skills/java-microservices/SKILL.md section 4, .claude/commands/phase-5-platform.md step 2

## Out of scope
- Dependency vulnerability scan (nfr-security)
- Web UI toolchain (plt-build-2)
- CI pipeline (plt-ci)
- docker compose and service start-up (plt-compose)
- The shared event module libs/events (plt-events, QF-4)
- Business code of any service

## Legacy trace (agent-owned, do not edit)
Rules: none (enabler)
Programs: none (platform enabler; the legacy estate has no build system, CLAUDE.md section 1)
Decisions: none

## Technical notes (agent-owned)
Service: platform. Endpoints/events: none. Roles: none.
