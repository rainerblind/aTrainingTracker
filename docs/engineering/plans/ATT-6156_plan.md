# Stage 3: Implementation Plan - ATT-6156: Add severity classification and problem trend metrics

**Ticket**: [ATT-6156](https://rainerblind.atlassian.net/browse/ATT-6156)  
**Sub-task**: `[Impl-Plan]` (Created on submission)  
**Parent Epic**: [ATT-6141](https://rainerblind.atlassian.net/browse/ATT-6141) (*ASPICE CL2 – Role Integrity, Independent QA & Configuration Management*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `sprint/2026-46.3`  
**Requirement Mapping**: `REQ-PRO-063` (`REQ-PRO-063.1`–`REQ-PRO-063.6`)  
**Test Mapping**: `TST-PRO-063` (`TST-PRO-063.1`–`TST-PRO-063.4`)  
**Branch**: `feature/ATT-6156`  
**Author**: AI Agent 1 (Implementer, `subagent_pro`)  
**Date**: 2026-11-12  

---

## 1. Problem Description & Background
ASPICE SUP.9 and MAN.3 require defect severity classification distinct from priority and quantitative defect trend telemetry. Finding B-11 noted that problems only carry priority (`Highest`..`Lowest`) without mandatory severity or resolution trend telemetry. This plan specifies the 4 atomic steps to enforce mandatory severity during Bug creation, extract problem trends in `sprint_metrics.py`, update metrics reporting schemas, and guarantee counterpart parity.

---

## 2. Traceability & Requirements Mapping
* **`REQ-PRO-063.1`**: Mandatory severity validation during Bug creation (`TST-PRO-063.1`).
* **`REQ-PRO-063.2`**: Severity label attachment and description metadata (`TST-PRO-063.1`).
* **`REQ-PRO-063.3`**: Problem trend telemetry extraction by severity (`TST-PRO-063.2`).
* **`REQ-PRO-063.4`**: `problem_trends` serialization in `process_metrics.jsonl` (`TST-PRO-063.2`).
* **`REQ-PRO-063.5`**: Markdown scorecard reporting of problem trends (`TST-PRO-063.3`).
* **`REQ-PRO-063.6`**: Governance verification of `problem_trends` schema (`TST-PRO-063.3`).

---

## 3. System Invariants & Preserved Behavior
1. **Zero Runtime Impact**: Zero modifications to production Android code or application resources in `app/src/main/`.
2. **Non-Bug Creation Invariance**: Feature, improvement, and task creation unaffected; severity is mandatory only for Bugs (`10006`).
3. **Standard Library Footprint**: Pure Python standard library (`json`, `os`, `re`, `sys`).
4. **Tooling Mirror Parity**: 100% functional parity between root tools and `tools/hoechstmass/tools/`.

---

## 4. Proposed Architectural Changes
* **`tools/jira_util.py` & `tools/hoechstmass/...`**: In `create_issue()` and `create-issue` CLI, require `--severity=critical|major|minor|cosmetic` when `issuetype == Bug / 10006`. Attach `severity-<level>` label and metadata.
* **`tools/sprint_metrics.py` & `tools/hoechstmass/...`**: In `parse_sprint_gate_metrics()`, aggregate `problem_trends` (`total_bugs`, `open_bugs`, `closed_bugs`, `resolution_rate_percent`, `by_severity`) and render in `format_metrics_markdown()`.
* **`tools/verify_requirement_governance.py` & `tools/hoechstmass/...`**: Update `verify_sprint_metrics()` to accept and validate the `problem_trends` object in `process_metrics.jsonl`.
* **UI Consistency**: No UI changes.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Enforce Mandatory Bug Severity in Issue Creation
* Files: `tools/jira_util.py`, `tools/hoechstmass/tools/jira_util.py`
* Action: Add `severity` parameter to `create_issue()`, validate required severity for Bugs, attach `severity-<level>` label, and wire `--severity` CLI argument.

### Step 2: Implement Problem Trend Extraction in `sprint_metrics.py`
* Files: `tools/sprint_metrics.py`, `tools/hoechstmass/tools/sprint_metrics.py`
* Action: Extract bug issues in `parse_sprint_gate_metrics()`, categorize by severity, compute resolution rates, serialize `problem_trends`, and render in `format_metrics_markdown()`.

### Step 3: Extend Governance Metrics Verifier
* Files: `tools/verify_requirement_governance.py`, `tools/hoechstmass/tools/verify_requirement_governance.py`
* Action: Update `verify_sprint_metrics()` schema checks to validate `problem_trends` fields.

### Step 4: Unit Testing & Verification
* Files: `tools/test_jira_accounts.py`, `tools/test_sprint_metrics.py`, `tools/hoechstmass/tools/test_hoechstmass_tools.py`, `tools/hoechstmass/tools/test_sprint_metrics.py`
* Action: Add unit tests validating Bug severity mandate, label attachment, non-bug invariance, problem trend calculation, and counterpart parity.
* Commands:
  - `python3 -m unittest tools/test_jira_accounts.py tools/test_sprint_metrics.py`
  - `PYTHONPATH=tools/hoechstmass/tools python3 -m unittest tools/hoechstmass/tools/test_hoechstmass_tools.py tools/hoechstmass/tools/test_sprint_metrics.py`

---

## 6. Verification & Rollback Plan
* **Verification**: Execute unit test suites, run live `sprint-metrics` with problem trends, and run `verify_requirement_governance.py`.
* **Rollback**: Branch isolation allows full revert without impacting `develop` or sprint branch.
