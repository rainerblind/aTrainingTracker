# Stage 2: Requirement & Test Specification - ATT-6156: Add severity classification and problem trend metrics

**Ticket**: [ATT-6156](https://rainerblind.atlassian.net/browse/ATT-6156)  
**Sub-task**: `[Req & Test Spec]` (Created on submission)  
**Parent Epic**: [ATT-6141](https://rainerblind.atlassian.net/browse/ATT-6141) (*ASPICE CL2 – Role Integrity, Independent QA & Configuration Management*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `sprint/2026-46.3`  
**Requirement Mapping**: `REQ-PRO-063` (*Problem Severity Classification & Defect Trend Metrics*)  
**Test Spec ID**: `TST-PRO-063`  
**Branch**: `feature/ATT-6156`  
**Author**: AI Agent 1 (Implementer, `subagent_flash`)  
**Date**: 2026-11-12  

---

## 1. Requirement Specification (`REQ-PRO-063`)

### 1.1 Problem Statement & Rationale
Under ASPICE SUP.9 and MAN.3, defect tracking requires classifying problem severity independently of scheduling priority, and tracking resolution trends over time (Finding B-11). Jira issues currently carry priority (`Highest`..`Lowest`) but lack mandatory severity categorization (`Critical`, `Major`, `Minor`, `Cosmetic`), and sprint metrics lack bug distribution and defect escape telemetry.

### 1.2 Functional & Architectural Requirements
1. **Mandatory Severity on Bug Creation (`REQ-PRO-063.1`)**: When creating an issue of type `Bug` / `Fehler` (`10006`) via `create_issue()` or `jira_util.py create-issue`, specifying a valid severity level (`Critical`, `Major`, `Minor`, `Cosmetic`) SHALL be mandatory. Omission MUST cause immediate validation failure with exit code 1. Non-bug issue types SHALL NOT require severity.
2. **Severity Labeling & Metadata (`REQ-PRO-063.2`)**: When a Bug is created with a valid severity, the tooling SHALL append a standardized label `severity-<level>` (lowercased) and embed severity metadata in the issue description.
3. **Problem Trend Metrics Extraction (`REQ-PRO-063.3`)**: `parse_sprint_gate_metrics()` in `tools/sprint_metrics.py` SHALL identify all Bug issues in the sprint, aggregate counts by severity (`Critical`, `Major`, `Minor`, `Cosmetic`), track `open_bugs` vs `closed_bugs`, and calculate sprint defect escape and resolution rate metrics.
4. **Quantitative Process Metrics Schema (`REQ-PRO-063.4`)**: Sprint metrics records serialized to `docs/engineering/metrics/process_metrics.jsonl` SHALL include the `problem_trends` object containing `total_bugs`, `by_severity`, `open_bugs`, `closed_bugs`, and `resolution_rate_percent`.
5. **Markdown Report Formatting (`REQ-PRO-063.5`)**: `format_metrics_markdown()` in `tools/sprint_metrics.py` and Höchstmaß mirror SHALL render a dedicated "Problem Trends & Defect Severity" section.
6. **Governance Verification CLI (`REQ-PRO-063.6`)**: `verify_sprint_metrics()` in `tools/verify_requirement_governance.py` and Höchstmaß mirror SHALL accept and validate the `problem_trends` schema fields.

### 1.3 Requirement Archaeology & Chesterton's Fence Audit
* `Original Requirement ID & Target`: Net-new requirement `REQ-PRO-063` (`REQ-PRO-063.1`–`REQ-PRO-063.6`). Targets `tools/jira_util.py`, `tools/sprint_metrics.py`, `docs/engineering/metrics/process_metrics.jsonl`, and `tools/hoechstmass/`.
* `Historical Origin & Commit Trace`: Introduced in Sprint 2026-46.3 via ATT-6156 under Epic ATT-6141 to satisfy ASPICE pre-assessment finding B-11.
* `Root Reason for Existing Formulation`: Gaps in defect severity categorization and quantitative problem resolution tracking under ASPICE SUP.9.
* `Preservation of Core Invariants`: Zero changes to Android runtime code; pure Python 3 standard library; backward compatibility for non-bug issue creation preserved.

### 1.4 Acceptance Criteria (Given-When-Then)
* **AC-1 (Mandatory Bug Severity)**:
  * *Given* a new Bug issue being created via `jira_util.py create-issue`,
  * *When* `--severity` is omitted,
  * *Then* creation is rejected with exit code 1 and error message detailing mandatory severity levels.
* **AC-2 (Bug Creation with Severity)**:
  * *Given* a new Bug issue being created with `--severity=critical`,
  * *When* created,
  * *Then* the issue is created with label `severity-critical` and description metadata.
* **AC-3 (Non-Bug Creation Invariance)**:
  * *Given* a non-bug issue being created,
  * *When* created without severity,
  * *Then* creation succeeds normally without severity mandate.
* **AC-4 (Problem Trend Telemetry)**:
  * *Given* sprint issues processed by `sprint_metrics.py`,
  * *When* `parse_sprint_gate_metrics()` runs,
  * *Then* it outputs `problem_trends` with bug counts by severity and resolution rates.

---

## 2. Test Specification (`TST-PRO-063`)

### Test Case 1: Bug Creation Severity Mandate (`TST-PRO-063.1`)
* **Scope**: Unit Test (`tools/test_jira_accounts.py` & `tools/hoechstmass/tools/test_hoechstmass_tools.py`)
* **Action**: Assert creating a Bug without `--severity` fails with exit code 1; assert creating a Bug with `--severity=critical` attaches `severity-critical` label and succeeds. Verify non-bugs can be created without severity.
### Test Case 2: Problem Trend Metrics Extraction (`TST-PRO-063.2`)
* **Scope**: Unit Test (`tools/test_sprint_metrics.py` & `tools/hoechstmass/tools/test_sprint_metrics.py`)
* **Action**: Feed sprint issues containing bugs with severity labels; verify `parse_sprint_gate_metrics()` aggregates bug counts by severity, open vs closed, and calculates defect escape/resolution rates.
### Test Case 3: Metrics Markdown Reporting & Schema Validation (`TST-PRO-063.3`)
* **Scope**: Unit Test (`tools/test_sprint_metrics.py` & `tools/test_verify_requirement_governance.py`)
* **Action**: Verify `format_metrics_markdown()` includes the "Problem Trends & Defect Severity" section; verify `verify_sprint_metrics()` accepts `problem_trends` schema fields.
### Test Case 4: Counterpart Parity & Tooling Integration (`TST-PRO-063.4`)
* **Scope**: Unit Test (`tools/hoechstmass/tools/test_hoechstmass_tools.py` & `tools/hoechstmass/tools/test_sprint_metrics.py`)
* **Action**: Assert Höchstmaß mirror tools maintain 100% functional parity for bug severity enforcement and metrics extraction.

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-PRO-063.1` | Unit | `create_issue` | `REQ-PRO-063.1`, `REQ-PRO-063.2` | Specified |
| `TST-PRO-063.2` | Unit | `parse_sprint_gate_metrics` | `REQ-PRO-063.3`, `REQ-PRO-063.4` | Specified |
| `TST-PRO-063.3` | Unit | `format_metrics_markdown`, `verify_sprint_metrics` | `REQ-PRO-063.5`, `REQ-PRO-063.6` | Specified |
| `TST-PRO-063.4` | Unit | `create_issue`, `parse_sprint_gate_metrics` (hoechstmass) | `REQ-PRO-063.1`–`REQ-PRO-063.6` | Specified |
