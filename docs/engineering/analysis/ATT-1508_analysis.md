# Stage 1 Analysis: ATT-1508 - Modular Skill-based Workflow Architecture

**Ticket**: [ATT-1508](https://rainerblind.atlassian.net/browse/ATT-1508)  
**Sub-task**: [ATT-1512](https://rainerblind.atlassian.net/browse/ATT-1512) (`[Analysis]`)  
**Parent Epic**: [ATT-232](https://rainerblind.atlassian.net/browse/ATT-232) (*Process*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.1`  
**Branch**: `feature/ATT-1508`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-09-28  

---

## 1. Problem Statement & Motivation

Currently, the engineering lifecycle and developer governance of **aTrainingTracker** are defined in a single monolithic document: `docs/project_protocol.md` (and `.agents/rules/aspice_governance.md`).
While this methodology (inspired by ASPICE and Agile/Scrum) has achieved world-class reliability and zero-regression releases across hundreds of versions, the monolithic format imposes tangible constraints on AI coding assistants:

1. **Context Window Inflation & Token Inefficiency**:
   The entire 150-line protocol—covering all 6 stages, Jira state machines, branching mandates, localization rules, and coding standards—must be kept in memory or repeatedly read, even when performing a focused, single-phase task (such as formulating an analysis or drafting a test specification).
2. **Instruction Drift & Cognitive Dilution**:
   When an LLM agent processes dozens of disparate rules spanning multiple distinct phases simultaneously, instructions situated in the middle of long documents are vulnerable to partial neglect or cognitive dilution ("lost in the middle").
3. **Lack of Encapsulated Assets & Executable Templates**:
   Templates for Stage deliverables (Analysis, Test-Spec, Impl-Plan, Walkthrough) are described in prose rather than being packaged as dedicated, directly usable markdown files.
4. **Missing Specialized Agent Roles Outside the Core Sprint Pipeline**:
   Beyond the linear 5-stage ticket implementation pipeline, modern autonomous development requires two specialized, rapid-feedback roles:
   * **`ui-designer`**: Fast exploratory UI iteration using Jetpack Compose `@Preview` composables, rapid code tweaks, and instant human visual verification without full APK deployments or heavy stage ceremony.
   * **`brainstormer`**: Socratic product discovery, requirement probing, and automated backlog ticket formulation (`tools/jira_util.py create-issue`) to feed ideas into the Product Backlog cleanly without interrupting active sprints.

---

## 2. Technical Architecture: Native Antigravity Custom Skills

Antigravity provides a native, standardized **Custom Skills** architecture:
* **Directory Root**: `.agents/skills/<skill-name>/`
* **Entry Point**: `SKILL.md` with standardized YAML frontmatter:
  ```yaml
  ---
  name: <skill-name>
  description: <concise summary of capability and trigger conditions>
  ---
  ```
* **Modular Composition**: Each skill directory encapsulates its own instructions, templates (`templates/`), and validation scripts (`scripts/`).
* **On-Demand Activation**: The agent discovers skills dynamically and loads only the skill relevant to the active stage, keeping the context window lean and eliminating instruction drift.

---

## 3. Skill Decomposition Matrix

The monolithic workflow is decomposed into **8 discrete, focused skills**:

| Skill Name | Purpose | Key Responsibilities & Invariants | Encapsulated Assets |
| :--- | :--- | :--- | :--- |
| **`stage1-analysis`** | Stage 1 Problem Domain & Root Cause Analysis | Forensic RCA (bugs), scope boundaries (features), Chesterton's Fence archaeology (`git log`), call-site audit. User Scope Grounding (ATT-1250). | `templates/analysis_template.md` |
| **`stage2-req-test-spec`** | Stage 2 Requirements & Test Specification | Requirement formulation (`REQ-XXX`, SHALL/MUST, Given-When-Then), test procedure design (`TST-XXX`), 9-language localization audit, `docs/requirements.md` and `docs/tests.md` updates. | `templates/test_spec_template.md` |
| **`stage3-impl-plan`** | Stage 3 Architecture & Invariant Plan | Bite-sized atomic step breakdown, SWE.2 component boundaries, invariant protection, rollback safety, automated test mapping. | `templates/plan_template.md` |
| **`stage4-implementation`** | Stage 4 Implementation | Code construction, Gate 3 check (`check-gate`), targeted test execution, KDoc/JavaDoc standards, 9-language strings. | N/A (code construction) |
| **`stage5-verification`** | Stage 5 Clean-Room Regression & Release | Full test suite execution (`./gradlew testDebugUnitTest`), on-device hardware verification (Pixel 10), walkthrough documentation, FixVersion audit. | `templates/walkthrough_template.md` |
| **`jira-workflow`** | Jira Interaction & Subtask Governance | Role-safe execution with `tools/jira_util.py`, coordinator/agent1/agent2 conventions, subtask state transitions, human gate enforcement (`Erledigt` human gate). | N/A (tool orchestration) |
| **`ui-designer`** | Rapid Compose UI Prototyping & Visual Loop | High-velocity `@Preview` iteration, theme token compliance (AMOLED `#000000`, WCAG AA/AAA contrast), visual variant diffing, immediate human feedback loop. | N/A (interactive design) |
| **`brainstormer`** | Socratic Ideation & Backlog Ticket Creation | Socratic questioning, idea evaluation, edge-case probing, user story formulation, Epic alignment, automated Jira backlog ticket creation (`create-issue`). | `templates/backlog_ticket_template.md` |

---

## 4. Phased Incubation & Standalone Extraction Strategy

1. **Phase 1: In-Tree Rapid Incubation (`.agents/skills/`)**:
   * Skills are created directly inside `aTrainingTracker` under `.agents/skills/`.
   * Directly version-controlled in Git alongside code and tests.
   * Permeates daily development immediately with zero network or package registry overhead.
   * Enables continuous tuning and refinement every few days during sprint execution.
2. **Phase 2: Standalone Extraction (`agile-aspice-skills`)**:
   * All skills are authored with clean modular boundaries (no hardcoded absolute workspace paths).
   * Once battle-tested, `.agents/skills/` can be extracted into its own public GitHub repository (`agile-aspice-skills` / `scrum-agent-skills`) via `git subtree split` or direct repo publishing.
   * Easiest re-linking back into `aTrainingTracker` via Git Submodule (`git submodule add <url> .agents/skills`).

---

## 5. Living Documentation & Protocol Streamlining

* **`docs/project_protocol.md`**:
  * Streamlined into an architectural index and sitemap.
  * Preserves high-level vision, role definitions (Agent 1, Agent 2, Coordinator, Human), and human decision gate invariants.
  * References the modular skills under `.agents/skills/` for execution details.
* **Traceability Integration**:
  * Map to new process requirement `REQ-PRO-019` (*Modular Skill-Based Agile Architecture & Role-Specialized On-Demand Workflows*) in `docs/requirements.md`.
  * Map to verification test `TST-PRO-012` (*Skill-Based Workflow Verification & Directory Parity*) in `docs/tests.md`.

---

## 6. System Invariants & Risk Assessment

* **Invariants**:
  1. *Human Gate Integrity*: Moving subtasks or parent tickets to `Erledigt` remains strictly reserved for the human user.
  2. *ASPICE Rigor*: Stages 1 through 5, Chesterton's Fence archaeology, and Given-When-Then acceptance criteria MUST NOT be compromised or bypassed.
  3. *Zero Source Code Regressions*: No production code in `app/src/...` is altered by this process modernization.
* **Risk Rating**: **LOW**. Pure process, documentation, and skill tooling enhancement. Zero impact on runtime Android app code.
