# Stage 2: Requirement & Test Specification - ATT-6150: Record story points and sprint metrics every sprint

**Ticket**: [ATT-6150](https://rainerblind.atlassian.net/browse/ATT-6150)  
**Sub-task**: `[Req & Test Spec]` (Created on submission)  
**Parent Epic**: [ATT-6141](https://rainerblind.atlassian.net/browse/ATT-6141) (*ASPICE CL2 – Role Integrity, Independent QA & Configuration Management*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `sprint/2026-46.3`  
**Requirement Mapping**: `REQ-PRO-060` (*Sprint Metrics Persistence & Story Point Governance*)  
**Test Spec ID**: `TST-PRO-060`  
**Branch**: `feature/ATT-6150`  
**Author**: AI Agent 1 (Implementer, `subagent_flash`)  
**Date**: 2026-11-12  

---

## 1. Requirement Specification (`REQ-PRO-060`)

### 1.1 Problem Statement & Rationale
Under ASPICE MAN.3 (Project Monitoring and Control) and GP 2.1 (Resource Management), sprint velocity and quality gate metrics must be systematically persisted and verified in configuration management. Historical process metrics in `docs/engineering/metrics/process_metrics.jsonl` had gaps and recorded zero story points for early sprints. This requirement establishes mandatory sprint metrics verification (`--check-metrics`), ensures story points are recorded every sprint, and optimizes query execution.

### 1.2 Functional & Architectural Requirements
1. **Story Points Extraction & Recording (`REQ-PRO-060.1`)**: `tools/sprint_metrics.py` and `tools/jira_util.py` SHALL extract planned and completed story points (`customfield_10016` / `customfield_10026`) and serialize them under `throughput` in `docs/engineering/metrics/process_metrics.jsonl`.
2. **Ceremony 2 Metrics Mandate (`REQ-PRO-060.2`)**: The sprint retrospective process in `.agents/skills/sprint-planner/SKILL.md` SHALL mandate execution of `jira_util.py sprint-metrics --record` and `verify_requirement_governance.py --check-metrics` at sprint close.
3. **Governance Verification CLI (`REQ-PRO-060.3`)**: `tools/verify_requirement_governance.py` and `tools/hoechstmass/tools/verify_requirement_governance.py` SHALL implement `--check-metrics [SPRINT]`, validating file presence, JSONL schema integrity, and requiring `total_story_points > 0` and `completed_story_points > 0` for evaluated sprints.
4. **Query Performance Optimization (`REQ-PRO-060.4`)**: `collect_sprint_metrics_for_id` in `tools/jira_util.py` and `tools/hoechstmass/tools/jira_util.py` SHALL index subtask comments from `fetch_all_sprint_issues` in memory to eliminate serial per-subtask REST requests.

### 1.3 Requirement Archaeology & Chesterton's Fence Audit
* `Original Requirement ID & Target`: Net-new requirement `REQ-PRO-060` (`REQ-PRO-060.1`–`REQ-PRO-060.4`). Targets `tools/sprint_metrics.py`, `tools/jira_util.py`, `tools/verify_requirement_governance.py`, and `tools/hoechstmass/`.
* `Historical Origin & Commit Trace`: Introduced in Sprint 2026-46.3 via ATT-6150 under Epic ATT-6141 to resolve ASPICE pre-assessment finding B-05.
* `Root Reason for Existing Formulation`: Gaps in historical metric records and lack of CI verification allowed sprints to close without validated velocity metrics.
* `Preservation of Core Invariants`: Zero changes to Android runtime code; pure Python 3 standard library; offline mock testing resilience preserved.

### 1.4 Acceptance Criteria (Given-When-Then)
* **AC-1 (Metrics Recording at Sprint Close)**:
  * *Given* completed sprint issues with story points assigned
  * *When* `sprint-metrics --record` is executed
  * *Then* a record with `total_story_points > 0` and `completed_story_points > 0` is appended to `process_metrics.jsonl`.
* **AC-2 (Missing or Zero Story Points Failure)**:
  * *Given* a sprint name with no metrics record or with `0` story points
  * *When* `verify_requirement_governance.py --check-metrics <sprint>` runs
  * *Then* it aborts with exit code 1.
* **AC-3 (Valid Metrics Governance Pass)**:
  * *Given* a sprint record with valid schema and positive story points
  * *When* `verify_requirement_governance.py --check-metrics <sprint>` runs
  * *Then* it passes with exit code 0.

---

## 2. Test Specification (`TST-PRO-060`)

### Test Case 1: Story Point Extraction & Schema Persistence (`TST-PRO-060.1`)
* **Scope**: Unit Test (`tools/test_sprint_metrics.py`)
* **Action**: Verify `parse_sprint_gate_metrics` populates `total_story_points` and `completed_story_points` from custom fields.

### Test Case 2: Governance Verification Check (`TST-PRO-060.2`)
* **Scope**: Unit Test (`tools/test_verify_requirement_governance.py`)
* **Action**: Run `verify_sprint_metrics` against valid, missing, empty, and zero-story-point files; assert pass/fail responses.

### Test Case 3: Counterpart Parity & Indexing Optimization (`TST-PRO-060.3`)
* **Scope**: Unit Test (`tools/hoechstmass/tools/test_hoechstmass_tools.py`)
* **Action**: Assert `verify_sprint_metrics` parity and verify `collect_sprint_metrics_for_id` uses pre-indexed comments without extra network requests.

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-PRO-060.1` | Unit | `parse_sprint_gate_metrics` | `REQ-PRO-060.1`, `REQ-PRO-060.2` | Specified |
| `TST-PRO-060.2` | Unit | `verify_sprint_metrics` | `REQ-PRO-060.3` | Specified |
| `TST-PRO-060.3` | Unit | `collect_sprint_metrics_for_id`, `verify_sprint_metrics` | `REQ-PRO-060.4`, `REQ-PRO-060.3` | Specified |
