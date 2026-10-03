# Stage 2: Requirement & Test Specification - ATT-1508: Modular Skill-based Workflow Architecture

**Ticket**: [ATT-1508](https://rainerblind.atlassian.net/browse/ATT-1508)  
**Sub-task**: [ATT-1513](https://rainerblind.atlassian.net/browse/ATT-1513) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-232](https://rainerblind.atlassian.net/browse/ATT-232) (*Process*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.1`  
**Requirement Mapping**: `REQ-PRO-024` (*Modular Skill-Based Agile Architecture & Role-Specialized On-Demand Workflows*)  
**Test Spec ID**: `TST-PRO-017`  
**Branch**: `feature/ATT-1508`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-09-28  

---

## 1. Requirement Specification (REQ-PRO-024)

### 1.1 Problem Statement & Rationale
Currently, the entire software engineering lifecycle, ASPICE quality gate definitions, Jira state machines, and developer guidelines are aggregated in a single monolithic document (`docs/project_protocol.md`). While rigorous, this structure causes token inflation, cognitive dilution ("lost in the middle"), lacks standalone reusable deliverable templates, and does not provide specialized rapid-feedback roles for exploratory UI prototyping and backlog ideation.

### 1.2 Functional & Architectural Requirements
The development workflow and agent orchestration SHALL transition from a monolithic protocol document into an on-demand, modular skill-based architecture under `.agents/skills/`, complemented by role-specialized agents:

1. **Modular Skill Layout**:
   The workflow SHALL encapsulate procedural knowledge, quality gates, and domain practices into 8 discrete skills located under `.agents/skills/<skill_name>/`:
   * `stage1-analysis`: Problem domain & root cause analysis, Chesterton's Fence archaeology (`git log`), scope boundaries, User Scope Grounding (ATT-1250).
   * `stage2-req-test-spec`: Requirement formulation (`REQ-XXX`, SHALL/MUST, Given-When-Then), test procedure design (`TST-XXX`), 9-language localization audit, living documentation synchronization (`docs/requirements.md`, `docs/tests.md`).
   * `stage3-impl-plan`: Bite-sized atomic step breakdown, SWE.2 component boundaries, invariant protection, rollback safety, automated test mapping.
   * `stage4-implementation`: Software construction, Gate 3 verification, targeted unit test execution, KDoc/JavaDoc standards, 9-language strings.
   * `stage5-verification`: Clean-room regression suite (`./gradlew testDebugUnitTest`), on-device hardware verification (Pixel 10), walkthrough documentation, FixVersion audit.
   * `jira-workflow`: Role-safe execution with `tools/jira_util.py`, coordinator/agent1/agent2 conventions, subtask state transitions, parent ticket human gate enforcement.
   * `ui-designer`: High-velocity Compose UI prototyping using `@Preview` composables, instant visual diffs, and immediate human visual verification loops.
   * `brainstormer`: Socratic discovery, problem definition, Epic alignment, user story formulation, and automated Jira backlog ticket formulation (`tools/jira_util.py create-issue`).
   Each skill directory SHALL contain a valid `SKILL.md` file with standardized YAML frontmatter (`name`, `description`) conforming to the Antigravity skill specification.

2. **Standardized Deliverable Templates**:
   Each stage skill SHALL package dedicated, directly usable markdown templates under its `templates/` directory:
   * `stage1-analysis/templates/analysis_template.md`
   * `stage2-req-test-spec/templates/test_spec_template.md`
   * `stage3-impl-plan/templates/plan_template.md`
   * `stage5-verification/templates/walkthrough_template.md`
   * `brainstormer/templates/backlog_ticket_template.md`

3. **Role-Specialized Rapid Feedback Workflows**:
   * **`ui-designer`**: Fast exploratory UI iteration using Jetpack Compose `@Preview` composables, rapid code tweaks, and instant human visual verification without full APK deployments or heavy stage ceremony.
   * **`brainstormer`**: Socratic product discovery, requirement probing, and automated backlog ticket formulation (`tools/jira_util.py create-issue`) to feed ideas into the Product Backlog cleanly without interrupting active sprints.

4. **Streamlined Subtask Lifecycle & Human Gate Invariants**:
   * **Subtasks**: Subtasks no longer require an intermediate Human review pause; transition `Freigabe` moves subtasks directly from `In Überprüfung` to `Erledigt` upon passing Agent 2 Gate audit.
   * **Parent Tickets**: Parent tickets retain the mandatory final Human Decision Gate at release / sprint completion; AI agents remain strictly forbidden from transitioning parent tickets to `Erledigt`.

5. **Living Protocol Streamlining**:
   `docs/project_protocol.md` and `.cursorrules` SHALL be refactored into concise architectural indices, maintaining high-level process invariants while referencing `.agents/skills/` for execution specifics.

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Skill Layout & Parity)**:
  * *Given* the `.agents/skills/` directory,
  * *When* inspected,
  * *Then* all 8 skills (`stage1-analysis`, `stage2-req-test-spec`, `stage3-impl-plan`, `stage4-implementation`, `stage5-verification`, `jira-workflow`, `ui-designer`, `brainstormer`) SHALL exist and each contain a valid `SKILL.md` with YAML frontmatter `name` and `description`.
* **Criterion 2 (Deliverable Templates)**:
  * *Given* an engineer or agent initiating any stage deliverable or backlog ticket,
  * *When* inspecting the corresponding skill directory,
  * *Then* a complete, ready-to-use template SHALL be available in `templates/`.
* **Criterion 3 (Subtask Direct Transition to Erledigt)**:
  * *Given* an active subtask in status `In Überprüfung` that has received a passing audit (`RECOMMEND PASS`),
  * *When* `python3 tools/jira_util.py move <SUBTASK_KEY> freigabe` is executed,
  * *Then* the subtask SHALL transition directly to `Erledigt`.
* **Criterion 4 (Parent Human Gate Invariance)**:
  * *Given* an AI agent attempting to transition a parent ticket to `Erledigt`,
  * *When* `tools/jira_util.py` executes,
  * *Then* the tool SHALL block the action, output an error to `sys.stderr`, and exit with code 1.
* **Criterion 5 (Zero Code Regressions)**:
  * *Given* the existing test suite,
  * *When* `./gradlew testDebugUnitTest` is executed,
  * *Then* 100% of unit tests SHALL pass without regression.

### 1.4 System Invariants
* Zero APK runtime bytecode or asset modifications in `app/src/main/...`.
* Python 3 standard library only for all developer tooling (zero pip dependencies).
* Human decision gate on parent tickets remains inviolable.
* ASPICE Gates 1 through 5 quality audits remain strictly mandatory.

---

## 2. Test Specification (TST-PRO-017)

### Test Case 1: `testSkillDirectoryAndFrontmatterParity` (`TST-PRO-017.1`)
* **Scope**: Automated Verification Script (`tools/verify_skills.py` or unit test).
* **Goal**: Verify that all 8 skill directories exist and each contains a valid `SKILL.md` with compliant YAML frontmatter.
* **Preconditions**:
  * Working tree on `feature/ATT-1508`.
* **Action**:
  * Execute directory discovery across `.agents/skills/`.
  * For each skill (`stage1-analysis`, `stage2-req-test-spec`, `stage3-impl-plan`, `stage4-implementation`, `stage5-verification`, `jira-workflow`, `ui-designer`, `brainstormer`):
    * Assert directory exists.
    * Assert `SKILL.md` exists.
    * Parse YAML frontmatter and assert `name` and `description` are non-empty.
* **Expected Result**:
  * All 8 skills pass verification with zero errors.

### Test Case 2: `testTemplateAvailabilityAndCompleteness` (`TST-PRO-017.2`)
* **Scope**: File & Asset Verification.
* **Goal**: Verify that all mandatory deliverable templates exist and contain required section headings:
  * `stage1-analysis/templates/analysis_template.md` (Problem Statement, Root Cause, User Scope Grounding, Archaeology).
  * `stage2-req-test-spec/templates/test_spec_template.md` (Requirement Specification, Test Cases, Traceability).
  * `stage3-impl-plan/templates/plan_template.md` (Architecture, Invariants, Atomic Steps, Test Strategy).
  * `stage5-verification/templates/walkthrough_template.md` (Overview, Results, Evidence, Invariant Verification).
  * `brainstormer/templates/backlog_ticket_template.md` (Summary, User Story, Acceptance Criteria, Priority).
* **Expected Result**:
  * All 5 template files exist and are syntactically valid markdown.

### Test Case 3: `testJiraSubtaskDirectErledigtTransition` (`TST-PRO-017.3`)
* **Scope**: Jira Tooling Unit & Integration Test (`tools/jira_util.py`).
* **Goal**: Verify that `transition_issue` detects subtasks and allows transition to `Erledigt` via `freigabe` (or ID `4`).
* **Preconditions**:
  * Jira subtask issue key.
* **Action**:
  * Inspect `transition_issue` logic for subtasks vs parent tickets.
  * Execute unit test verifying subtask transition does not trip the Human Decision Gate guard.
* **Expected Result**:
  * Subtask transitions to `Erledigt` smoothly.

### Test Case 4: `testJiraParentHumanGateGuard` (`TST-PRO-017.4`)
* **Scope**: Jira Tooling Guard Verification.
* **Goal**: Verify that attempting to transition a parent ticket to `Erledigt` or `Done` raises an error and terminates with exit code 1.
* **Action**:
  * Execute `tools/test_jira_accounts.py` or unit test asserting `transition_issue` raises `SystemExit(1)` when target is a parent ticket and destination is `Erledigt`.
* **Expected Result**:
  * Exit code 1; diagnostic message printed to `sys.stderr`.

### Test Case 5: `testProtocolIndexAndRulesConsistency` (`TST-PRO-017.5`)
* **Scope**: Documentation Inspection.
* **Goal**: Verify that `docs/project_protocol.md` and `.cursorrules` are aligned with the modular skill architecture and link to `.agents/skills/`.
* **Expected Result**:
  * `docs/project_protocol.md` serves as a concise index while delegating execution details to skills.

### Test Case 6: Clean-Room Regression Suite (`TST-PRO-017.6`)
* **Command**: `./gradlew testDebugUnitTest`
* **Goal**: Verify 100% test pass rate across the Android test suite with zero regressions.

---

## 3. Traceability Matrix

| Test Case | Scope | Target / Deliverable | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-PRO-017.1` | Automated Verification | `.agents/skills/*/SKILL.md` | `REQ-PRO-024` | Specified |
| `TST-PRO-017.2` | Asset Inspection | `.agents/skills/*/templates/*.md` | `REQ-PRO-024` | Specified |
| `TST-PRO-017.3` | Tooling Verification | `tools/jira_util.py` (Subtask Erledigt) | `REQ-PRO-024` | Specified |
| `TST-PRO-017.4` | Tooling Security | `tools/jira_util.py` (Parent Human Gate) | `REQ-PRO-024`, `REQ-PRO-016` | Specified |
| `TST-PRO-017.5` | Documentation | `docs/project_protocol.md`, `.cursorrules` | `REQ-PRO-024` | Specified |
| `TST-PRO-017.6` | Full Regression | `./gradlew testDebugUnitTest` | `REQ-PRO-001` | Specified |
