# Stage 3: Implementation Plan - ATT-1508: Modular Skill-based Workflow Architecture

**Ticket**: [ATT-1508](https://rainerblind.atlassian.net/browse/ATT-1508)  
**Sub-task**: [ATT-1514](https://rainerblind.atlassian.net/browse/ATT-1514) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-232](https://rainerblind.atlassian.net/browse/ATT-232) (*Process*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.1`  
**Requirement Mapping**: `REQ-PRO-024` (*Modular Skill-Based Agile Architecture & Role-Specialized On-Demand Workflows*)  
**Test Mapping**: `TST-PRO-017` (*Modular Skill-Based Agile Architecture & Directory Parity Verification*)  
**Branch**: `feature/ATT-1508`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-09-28  

---

## 1. Problem Description & Background

The engineering governance of `aTrainingTracker` is currently concentrated in a single monolithic document (`docs/project_protocol.md`). While this ASPICE-inspired and Scrum-aligned workflow has guaranteed zero-regression releases across hundreds of iterations, the monolithic structure creates operational challenges for autonomous coding agents:
1. **Context Window Inflation**: The entire multi-stage protocol is loaded into context even for tiny, isolated tasks.
2. **Instruction Drift**: LLM attention dilutes across dozens of unrelated lifecycle rules.
3. **Embedded Prose Templates**: Deliverable templates are embedded in descriptive text rather than packaged as clean, reusable markdown files.
4. **Missing Specialized Fast-Feedback Roles**: Two critical workflows—exploratory Compose UI prototyping (`ui-designer`) and backlog ideation (`brainstormer`)—lack dedicated skills.
5. **Subtask Workflow Friction**: Subtasks previously required intermediate human review stops; this has been simplified so subtasks transition directly to `Erledigt` upon passing automated gate audits, reserving human sign-off exclusively for parent tickets at release time.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-PRO-024` (*Modular Skill-Based Agile Architecture & Role-Specialized On-Demand Workflows*)
  * *Clause 1*: 8 distinct skills under `.agents/skills/<skill_name>/` with standardized YAML frontmatter.
  * *Clause 2*: Embedded deliverable templates under each skill's `templates/` directory.
  * *Clause 3*: Specialized roles (`ui-designer`, `brainstormer`).
  * *Clause 4*: Streamlined subtask lifecycle (`freigabe` -> `Erledigt`) and parent ticket Human Decision Gate.
  * *Clause 5*: Living protocol streamlined into an architectural index.
* **Test Verification**: `TST-PRO-017` (*Modular Skill-Based Agile Architecture & Directory Parity Verification*)
  * `TST-PRO-017.1`: Automated directory and frontmatter parity check.
  * `TST-PRO-017.2`: Template existence and structure verification.
  * `TST-PRO-017.3`: Subtask direct transition to `Erledigt` verification.
  * `TST-PRO-017.4`: Parent ticket human decision gate guard verification.
  * `TST-PRO-017.5`: Protocol index and rules consistency inspection.
  * `TST-PRO-017.6`: Clean-room regression suite (`./gradlew testDebugUnitTest`).

---

## 3. System Invariants & Preserved Behavior

1. **Zero APK Runtime Bytecode or Asset Modifications**: No source files in `app/src/main/...` or runtime resources are modified.
2. **Pure Python 3 Standard Library**: All developer tooling and verification scripts use standard library modules (`os`, `sys`, `re`, `subprocess`, `unittest`) with zero external pip dependencies.
3. **Inviolable Parent Ticket Human Decision Gate**: AI agents remain strictly prohibited from transitioning parent tickets to `Erledigt`.
4. **Streamlined Subtask Completion**: Subtasks can transition directly from `In Überprüfung` to `Erledigt` via `freigabe` upon Agent 2 audit pass.
5. **Full ASPICE Gate Integrity**: Stage 1 to 5 quality gates, Chesterton's Fence archaeology, and Given-When-Then acceptance criteria remain mandatory.

---

## 4. Proposed Architectural Changes

### Component 1: Native Antigravity Skills (`.agents/skills/`)
Create 8 modular skill directories conforming to the Antigravity skill specification (`SKILL.md` with YAML frontmatter `name` and `description`):

1. **`.agents/skills/stage1-analysis/`**:
   * `SKILL.md`: Problem domain forensics, root cause analysis, Chesterton's Fence archaeology (`git log -S`), scope bounding, User Scope Grounding (ATT-1250).
   * `templates/analysis_template.md`: Standardized Stage 1 template.
2. **`.agents/skills/stage2-req-test-spec/`**:
   * `SKILL.md`: Requirement specification (`REQ-XXX`, SHALL/MUST clauses, Given-When-Then, invariants), test matrix (`TST-XXX`), 9-language localization audit, living doc synchronization (`docs/requirements.md`, `docs/tests.md`).
   * `templates/test_spec_template.md`: Standardized Stage 2 template.
3. **`.agents/skills/stage3-impl-plan/`**:
   * `SKILL.md`: Architectural decomposition, SWE.2 layering, atomic bite-sized steps, invariant preservation, rollback safety, automated test mapping.
   * `templates/plan_template.md`: Standardized Stage 3 template.
4. **`.agents/skills/stage4-implementation/`**:
   * `SKILL.md`: Software construction, Gate 3 verification (`check-gate`), targeted unit testing, KDoc/JavaDoc standards, clean architecture invariants.
5. **`.agents/skills/stage5-verification/`**:
   * `SKILL.md`: Clean-room regression suite (`./gradlew testDebugUnitTest`), on-device hardware verification (Pixel 10), walkthrough documentation, FixVersion audit, living doc status update (`Verified`).
   * `templates/walkthrough_template.md`: Standardized Stage 5 template.
6. **`.agents/skills/jira-workflow/`**:
   * `SKILL.md`: Role conventions (`agent1`, `agent2`, `coordinator`), subtask lifecycle transitions (`Start`, `Review`, `Freigabe` -> `Erledigt`), parent ticket human gate enforcement.
7. **`.agents/skills/ui-designer/`**:
   * `SKILL.md`: Rapid Jetpack Compose `@Preview` loop, instant visual diffs (Variant 1 vs 2), theme token compliance (`#000000` AMOLED, WCAG AA/AAA), immediate human check feedback.
8. **`.agents/skills/brainstormer/`**:
   * `SKILL.md`: Socratic discovery, problem definition, Epic alignment, user story formulation, automated Jira backlog ticket creation (`tools/jira_util.py create-issue`).
   * `templates/backlog_ticket_template.md`: Standardized backlog ticket template.

### Component 2: Automated Verification Script (`tools/verify_skills.py`)
* Python 3 standard library script to verify:
  * Directory existence for all 8 skills.
  * `SKILL.md` presence and YAML frontmatter (`name`, `description`).
  * Availability of all 5 templates.
  * Deterministic exit codes: `0` on success, `1` on error.

### Component 3: Jira Tooling & Subtask Governance (`tools/jira_util.py` & `tools/test_jira_accounts.py`)
* Verification that `transition_issue` allows subtasks to move to `Erledigt` via `freigabe` while strictly blocking parent tickets.
* Automated unit tests asserting this behavior in `tools/test_jira_accounts.py`.

### Component 4: Living Protocol Streamlining (`docs/project_protocol.md` & `.cursorrules`)
* Condense `docs/project_protocol.md` into an architectural index.
* Reference `.agents/skills/` for procedural specifics.
* Update `.cursorrules` to direct agents to activate on-demand skills.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Implement Verification Tool (`tools/verify_skills.py`)
* Create `tools/verify_skills.py` with directory checks, regex frontmatter validation, and template assertions.

### Step 2: Implement the 8 Skills & Templates
* Author `.agents/skills/stage1-analysis/SKILL.md` and `templates/analysis_template.md`.
* Author `.agents/skills/stage2-req-test-spec/SKILL.md` and `templates/test_spec_template.md`.
* Author `.agents/skills/stage3-impl-plan/SKILL.md` and `templates/plan_template.md`.
* Author `.agents/skills/stage4-implementation/SKILL.md`.
* Author `.agents/skills/stage5-verification/SKILL.md` and `templates/walkthrough_template.md`.
* Author `.agents/skills/jira-workflow/SKILL.md`.
* Author `.agents/skills/ui-designer/SKILL.md`.
* Author `.agents/skills/brainstormer/SKILL.md` and `templates/backlog_ticket_template.md`.

### Step 3: Implement Unit Tests for Jira Subtask & Parent Transitions
* Add test cases in `tools/test_jira_accounts.py` verifying:
  * Subtask `transition_issue(..., "freigabe")` succeeds.
  * Parent ticket `transition_issue(..., "erledigt")` raises `SystemExit(1)`.

### Step 4: Streamline Living Documentation
* Refactor `docs/project_protocol.md` to serve as the agile ASPICE architectural index.
* Update `.cursorrules` with skill routing guidelines.

### Step 5: Automated Verification & Gate Audit
* Execute `python3 tools/verify_skills.py`.
* Execute `python3 -m unittest discover tools`.
* Execute `./gradlew testDebugUnitTest`.

---

## 6. Rollback & Contingency Plan
* If any skill structure causes agent confusion, `docs/project_protocol.md` retains complete high-level definitions.
* Git branch `feature/ATT-1508` isolates all changes until Stage 5 release verification.
