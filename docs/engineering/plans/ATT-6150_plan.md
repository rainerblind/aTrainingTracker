# Stage 3: Implementation Plan - ATT-6150: Record story points and sprint metrics every sprint

**Ticket**: [ATT-6150](https://rainerblind.atlassian.net/browse/ATT-6150)  
**Sub-task**: `[Impl-Plan]` (Created on submission)  
**Parent Epic**: [ATT-6141](https://rainerblind.atlassian.net/browse/ATT-6141) (*ASPICE CL2 – Role Integrity, Independent QA & Configuration Management*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `sprint/2026-46.3`  
**Requirement Mapping**: `REQ-PRO-060` (`REQ-PRO-060.1`–`REQ-PRO-060.4`)  
**Test Mapping**: `TST-PRO-060` (`TST-PRO-060.1`–`TST-PRO-060.3`)  
**Branch**: `feature/ATT-6150`  
**Author**: AI Agent 1 (Implementer, `subagent_pro`)  
**Date**: 2026-11-12  

---

## 1. Problem Description & Background
ASPICE MAN.3 and GP 2.1 require systematic collection and configuration control of sprint velocity and process metrics. `docs/engineering/metrics/process_metrics.jsonl` contains gaps and early entries recorded 0.0 story points. This plan deconstructs the implementation into 4 atomic steps to enforce `--check-metrics` verification in requirement governance, optimize Jira metric collection, and integrate CI checks into Ceremony 2.

---

## 2. Traceability & Requirements Mapping
* **`REQ-PRO-060.1`**: Story points extraction & schema serialization (`TST-PRO-060.1`).
* **`REQ-PRO-060.2`**: Ceremony 2 retrospective execution mandate in sprint planner skill (`TST-PRO-060.1`).
* **`REQ-PRO-060.3`**: Governance verifier `--check-metrics [SPRINT]` validation (`TST-PRO-060.2`).
* **`REQ-PRO-060.4`**: In-memory subtask comment indexing optimization in `collect_sprint_metrics_for_id` (`TST-PRO-060.3`).

---

## 3. System Invariants & Preserved Behavior
1. **Zero Production Runtime Impact**: No modifications to `app/src/main/` or Android resources.
2. **Standard Library Footprint**: Pure Python standard library (`json`, `os`, `re`, `sys`).
3. **Inviolable Human Gate**: AI agent roles strictly barred from moving parent tickets to `Erledigt`.
4. **Tooling Mirror Parity**: 100% functional parity between root tools and `tools/hoechstmass/tools/`.

---

## 4. Proposed Architectural Changes
* **`tools/verify_requirement_governance.py` & `tools/hoechstmass/...`**: Implement `verify_sprint_metrics(repo_root=None, sprint_name=None)` and `--check-metrics` CLI flag. Validates file existence, JSONL schema, positive planned/completed story points (`> 0`), and returns `(bool, list[str])`.
* **`tools/jira_util.py` & `tools/hoechstmass/...`**: In `collect_sprint_metrics_for_id`, build in-memory comment lookup from top-level fetched issues to avoid sequential per-subtask REST calls.
* **`.agents/skills/sprint-planner/SKILL.md`**: Mandate `python3 tools/jira_util.py sprint-metrics --record` and `python3 tools/verify_requirement_governance.py --check-metrics` during Ceremony 2.
* **UI Consistency**: No UI changes.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Implement Governance Verifier `verify_sprint_metrics` & `--check-metrics`
* Files: `tools/verify_requirement_governance.py`, `tools/hoechstmass/tools/verify_requirement_governance.py`
* Action: Add `verify_sprint_metrics(repo_root=None, sprint_name=None)` and wire `--check-metrics` CLI parameter.

### Step 2: Optimize Comment Indexing in `collect_sprint_metrics_for_id`
* Files: `tools/jira_util.py`, `tools/hoechstmass/tools/jira_util.py`
* Action: Pre-populate subtask comments from `fetch_all_sprint_issues` mapping.

### Step 3: Update Sprint-Planner Ceremony 2 Governance
* Files: `.agents/skills/sprint-planner/SKILL.md`, `tools/hoechstmass/.agents/skills/sprint-planner/SKILL.md`
* Action: Add explicit `--check-metrics` step in Ceremony 2 checklist.

### Step 4: Unit Test Coverage & Verification
* Files: `tools/test_verify_requirement_governance.py`, `tools/test_sprint_metrics.py`, `tools/hoechstmass/tools/test_hoechstmass_tools.py`
* Action: Add test cases validating `verify_sprint_metrics` (pass, missing file, zero story points, specific sprint) and comment indexing.
* Commands:
  - `python3 -m unittest tools/test_verify_requirement_governance.py tools/test_sprint_metrics.py`
  - `PYTHONPATH=tools/hoechstmass/tools python3 -m unittest tools/hoechstmass/tools/test_hoechstmass_tools.py`
  - `python3 tools/verify_requirement_governance.py --check-metrics 2026-46.2`

---

## 6. Verification & Rollback Plan
* **Verification**: Run unit tests, verify CLI flags, and run `python3 tools/verify_requirement_governance.py`.
* **Rollback**: Branch isolation allows full revert without impacting `develop` or sprint branch.
