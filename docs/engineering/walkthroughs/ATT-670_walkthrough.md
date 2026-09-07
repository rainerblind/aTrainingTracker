# Implementation Walkthrough: Unified 5-Stage ASPICE Workflow & Automated Jira Sub-Tasks (ATT-670)

## 1. Overview & Objectives
This task upgrades the project's engineering documentation, AI operational guidelines, and CLI utilities to establish a unified 5-stage ASPICE engineering lifecycle across all Jira ticket types (Bug, Improvement, Feature):
$$\text{Analysis} \longrightarrow \text{Test Spec} \longrightarrow \text{Implementation Plan} \longrightarrow \text{Implementation} \longrightarrow \text{Test} \longrightarrow \text{Erledigt}$$

It incorporates Jira Automation for automatic sub-task generation (`[Analysis]`, `[Test-Spec]`, `[Impl-Plan]`, `[Implementation]`, `[Test]`) and automated parent state advancement upon sub-task closure, while strictly preserving the human-only decision gate for `Erledigt`. Furthermore, it explicitly mandates that **Agent 2 always conducts the independent gate audit** across all stages.

## 2. Changes Implemented

### A. Protocol Realignment (`docs/project_protocol.md`)
* **Unified 5-Stage ASPICE Lifecycle**:
  * Unifies all ticket types (Bugs, Improvements, Features) into the same 5-stage workflow matching native Jira states: `Zu erledigen` -> `Analysis` -> `Test Spec` -> `Implementation Plan` -> `Implementation` -> `Test` -> `Erledigt`.
  * Preserves domain-specific analysis focus: forensic RCA for bugs vs. domain scope, user motivation, and side effects for features/improvements.
* **Jira Automation Codification**:
  * Codifies that lifecycle sub-tasks are auto-spawned when the parent ticket enters a stage.
  * Codifies that human release to `Erledigt` on a sub-task automatically advances the parent ticket to the next stage and spawns the next sub-task.
* **Mandatory Automated Agent 2 Reviews**:
  * Formally mandates that Agent 2 *always* executes the independent gate review upon any sub-task entering `In Überprüfung`, without requiring explicit user prompting.
* **Separation of Concerns**:
  * Primary deliverables (Analysis, Test Spec, Plan, Walkthrough, Test Evidence) reside exclusively in the sub-task **Description**.
  * Independent reviews/critiques reside exclusively as Jira **Comments**.
* **Five-Gate Review Protocol**:
  * Upgraded from 3-Gate to 5-Gate Review Protocol corresponding to each ASPICE phase.

### B. AI Operational Instructions (`.cursorrules`)
* Updated Section 16 ("Mandatory Workflow") to reflect the unified 5-stage ASPICE lifecycle.
* Codified automated sub-task creation and automatic Agent 2 audits.

### C. Tooling Visibility (`tools/jira_util.py`)
* Updated `show_issue` to query and display `subtasks` for instant operational inspection.

## 3. Verification & Evidence
* **CLI Tooling**:
  * Executed `python3 ./tools/jira_util.py show ATT-670`.
  * Verified output renders all sub-tasks with keys, summaries, and statuses:
    * `ATT-684: [Analysis] ... [Erledigt]`
    * `ATT-685: [Test-Spec] ... [Erledigt]`
    * `ATT-686: [Impl-Plan] ... [Erledigt]`
    * `ATT-687: [Implementation] ... [In Bearbeitung]`
* **Protocol & Rule Consistency**:
  * Verified `git diff` shows complete harmonization between `docs/project_protocol.md`, `.cursorrules`, `docs/requirements.md`, and `docs/tests.md`.

## 4. Conventional Commit Message
```text
feat(process): implement unified 5-stage ASPICE workflow and automated Jira sub-tasks (ATT-670, ATT-687)

* Restructure project_protocol.md to define unified 5-stage ASPICE lifecycle
* Codify automated Jira sub-task generation and parent stage advancement
* Mandate automated Agent 2 independent audits across all 5 review gates
* Update .cursorrules with 5-stage ASPICE workflow guidelines
* Enhance tools/jira_util.py show command to display subtasks
```
