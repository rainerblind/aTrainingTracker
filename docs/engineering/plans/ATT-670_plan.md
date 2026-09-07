# Implementation Plan: Unified 5-Stage ASPICE Workflow & Automated Jira Sub-tasks (ATT-670)

## 1. Goal & Context
Upgrade `docs/project_protocol.md`, `.cursorrules`, and `tools/jira_util.py` to establish a unified 5-stage ASPICE engineering workflow across all Jira ticket types (Bug, Improvement, Feature). This aligns development with native Jira states (`Analysis` -> `Test Spec` -> `Implementation Plan` -> `Implementation` -> `Test` -> `Erledigt`), integrates Jira Automation for automated sub-task creation and parent ticket state advancement, and mandates automated independent gate audits by Agent 2.

## 2. Traceability & Scope Mapping
* **Requirement**: `REQ-PRO-014` (Unified 5-Stage ASPICE Dual-Agent Quality Gates & Automated Jira Workflow)
* **Verification Test**: `TST-PRO-008` (Automated 5-Stage ASPICE Workflow & Independent Agent 2 Audit Verification)
* **Target Files**:
  1. `docs/project_protocol.md`
  2. `.cursorrules`
  3. `tools/jira_util.py`
  4. `docs/requirements.md` (Already synchronized in Stage 2)
  5. `docs/tests.md` (Already synchronized in Stage 2)

## 3. Detailed Component Changes

### A. `docs/project_protocol.md`
* **Section 1: Requirement Synchronization**:
  * Specify that requirements synchronization is performed during Stage 2 (Test Spec), informed by the Stage 1 Analysis.
* **Section 2: Test Definition (Stage 2 Test Spec)**:
  * Restructure to define the `[Test-Spec]` stage and sub-task.
  * Document automated sub-task generation by Jira Automation upon entering `Test Spec`.
  * Define deliverable documentation directly in the sub-task's Description (Agent 1) and independent audit in Comments (Agent 2).
* **Section 4: Jira Ticket Management & Dual-Agent Workflow**:
  * Unify Bug, Feature, and Improvement lifecycles into a single 5-stage ASPICE workflow.
  * Define parent ticket states: `Zu erledigen` -> `Analysis` -> `Test Spec` -> `Implementation Plan` -> `Implementation` -> `Test` -> `Erledigt`.
  * Detail stage behaviors:
    * *Stage 1 Analysis (`[Analysis]`)*: Root Cause Analysis (RCA) for Bugs vs. Domain Scope / User Motivation / Side-effect Analysis for Features and Improvements.
    * *Stage 2 Test Spec (`[Test-Spec]`)*: Requirement synchronization (`docs/requirements.md`) and test case specification (`docs/tests.md`).
    * *Stage 3 Implementation Plan (`[Impl-Plan]`)*: Architectural plan formulation (`docs/engineering/plans/ATT-XXX_plan.md`).
    * *Stage 4 Implementation (`[Implementation]`)*: Code changes, KDoc/JavaDoc headers, 9-language localization, unit testing.
    * *Stage 5 Test (`[Test]`)*: Verification evidence, clean-room regression (`./gradlew testDebugUnitTest`), and requirements/tests status update to `Verified`.
    * *Stage 6 Erledigt*: Non-fast-forward merge to `develop`.
  * Explicitly mandate that **Agent 2 always conducts the review** upon any sub-task entering `In Überprüfung`.
  * Codify automated parent ticket transitions driven by Jira Automation when sub-tasks reach `Erledigt`.
* **Section 6: Implementation Planning (SWE.2 / SWE.3 Phase)**:
  * Align with `[Impl-Plan]` sub-task and Stage 3 prerequisites.
* **Section 7: Execution & Verification (SWE.3 / SWE.4 / SWE.5 Phase)**:
  * Align with `[Implementation]` and `[Test]` sub-tasks.
* **Review Protocol Section**:
  * Upgrade from "Three-Gate" to "Five-Gate AI Review Protocol" (Gate 1 Analysis, Gate 2 Test Spec, Gate 3 Plan, Gate 4 Implementation, Gate 5 Clean-Room Regression & Release).

### B. `.cursorrules`
* **Section 16: Mandatory Workflow**:
  * Update git branch creation step.
  * Update workflow steps from 3-stage to 5-stage ASPICE lifecycle (`Analysis` -> `Test Spec` -> `Implementation Plan` -> `Implementation` -> `Test`).
  * Codify that sub-tasks are auto-created by Jira Automation and Agent 2 automatically audits each stage.

### C. `tools/jira_util.py`
* **Sub-task Visibility in `show_issue`**:
  * Add `subtasks` to the requested fields in the Jira issue query:
    `url = f"{config['JIRA_URL']}/rest/api/2/issue/{issue_key}?fields=summary,description,comment,attachment,parent,issuetype,status,subtasks"`
  * Iterate over `subtasks` in the response and format them clearly:
    ```
    *Sub-tasks*:
    * ATT-684: [Analysis] Further Improve Workflow: Automatic Ticket creating and transitions [Erledigt]
    * ATT-685: [Test-Spec] Further Improve Workflow: Automatic Ticket creating and transitions [Erledigt]
    * ATT-686: [Impl-Plan] Further Improve Workflow: Automatic Ticket creating and transitions [In Bearbeitung]
    ```

## 4. Verification Plan
* **Automated & Static Verifications**:
  1. Audit `tools/jira_util.py show ATT-670` to verify sub-tasks are rendered accurately with their status.
  2. Run `git diff` across `docs/project_protocol.md`, `.cursorrules`, and `tools/jira_util.py` to ensure consistency and precision.
  3. Execute full regression suite `./gradlew testDebugUnitTest` to ensure zero regression in project unit tests.
* **Manual Verification**:
  1. Inspect that all 5 gates are documented with clear Agent 1 deliverables (Description) and Agent 2 reviews (Comment).
  2. Verify that the human gate guard in `tools/jira_util.py` continues to block AI transitions to `Erledigt`.

## 5. System Invariants & Risk Assessment
* **Invariants**:
  * Transitioning to `Erledigt` remains an inviolable human-only gate.
  * Sub-task 5-state lifecycle (`Zu erledigen` -> `In Bearbeitung` -> `In Überprüfung` -> `Freigabe (Human)` -> `Erledigt`) is strictly preserved.
  * Non-fast-forward merge to `develop` is strictly conditioned upon 100% sub-tasks completed.
* **Risk Rating**: **LOW**. Modifications affect documentation and CLI tooling with no changes to Android runtime code or APK build scripts.
