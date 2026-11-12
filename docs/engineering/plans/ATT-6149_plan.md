# Stage 3: Implementation Plan - ATT-6149: Export Jira gate records into repository at sprint end

**Ticket**: [ATT-6149](https://rainerblind.atlassian.net/browse/ATT-6149) | **Sub-task**: `[Impl-Plan]` | **Parent**: [ATT-6141](https://rainerblind.atlassian.net/browse/ATT-6141) (*ASPICE CL2*)  
**Target Release**: `V5.0.0` | **Sprint**: `sprint/2026-46.3` | **Branch**: `feature/ATT-6149`  
**Requirement Mapping**: `REQ-PRO-059` (`REQ-PRO-059.1`, `REQ-PRO-059.2`, `REQ-PRO-059.3`, `REQ-PRO-059.4`)  
**Test Mapping**: `TST-PRO-059` (`TST-PRO-059.1`, `TST-PRO-059.2`, `TST-PRO-059.3`)  
**Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-12  

---

## 1. Problem Description & Background
ASPICE SUP.1 (Quality Assurance) and GP 2.2 (Work Product Evidence) require verifiable evidence of review verdicts and quality gate decisions. Currently, gate decisions exist only in Jira Cloud comments. The objective is to build an automated CLI export tool (`export-gates`) in `jira_util.py` to persist all sprint gate records into `docs/engineering/gates/<sprint>.md` (AC-1) and enforce its presence via `verify_requirement_governance.py --check-gates` (AC-2).

---

## 2. Traceability & Requirements Mapping
* **Requirements**:
  * `REQ-PRO-059.1`: `export-gates` command in `jira_util.py` and `hoechstmass/tools/jira_util.py`.
  * `REQ-PRO-059.2`: Structured Markdown schema (`docs/engineering/gates/<sprint>.md`).
  * `REQ-PRO-059.3`: Register `"gates"` in `ALLOWED_ENGINEERING_ENTRIES` and `hoechstmass_config.json`.
  * `REQ-PRO-059.4`: Governance check `verify_gate_records()` (`--check-gates [SPRINT]`).
* **Test Specs**: `TST-PRO-059.1` (`export-gates` unit test), `TST-PRO-059.2` (`verify_gate_records` unit test), `TST-PRO-059.3` (`"gates"` registration unit test).

---

## 3. System Invariants & Preserved Behavior
1. **Zero Runtime Impact**: Zero modifications to Android runtime bytecode or assets in `app/src/main/`.
2. **Pure Python Standard Library**: Standard library only (`json`, `os`, `sys`, `re`, `urllib`).
3. **Offline/Mock Resilience**: CLI command functions predictably with mocked Jira API responses in unit tests.
4. **Directory Structure Governance**: Canonical `docs/engineering/` verification passes without errors.

---

## 4. Proposed Architectural Changes
* **Component 1 (`export-gates` CLI Engine - `tools/jira_util.py`)**:
  Traverses sprint issues and subtasks, queries comments, parses Gate 1–5 (subtask audit comments) and Stage 6 (parent final review comments), extracts verdict, role, actor ID, and ISO timestamp. Writes formatted report to `docs/engineering/gates/<sprint>.md`.
* **Component 2 (Governance Verification - `tools/verify_requirement_governance.py`)**:
  Add `"gates"` to `ALLOWED_ENGINEERING_ENTRIES`. Implement `verify_gate_records(sprint_name, repo_root)` and CLI argument `--check-gates [SPRINT]`.
* **Component 3 (Höchstmaß Mirroring & Configuration)**:
  Add `export-gates` to `tools/hoechstmass/tools/jira_util.py` and add `"gates"` to `hoechstmass_config.json`.
* **Component 4 (Ceremony 2 Integration & Living Docs)**:
  Add `export-gates` execution to Ceremony 2 in `.agents/skills/sprint-planner/SKILL.md`. Document `REQ-PRO-059` and `TST-PRO-059` in living docs.
* **UI Consistency**: No UI changes.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)
* **Step 1: Implement `export_gates` Command**: Add `export_gates()` and CLI handler to `tools/jira_util.py` and `tools/hoechstmass/tools/jira_util.py`.
* **Step 2: Update Canonical Directory & Governance Verifier**: Add `"gates"` to `ALLOWED_ENGINEERING_ENTRIES` in `tools/verify_requirement_governance.py` and `hoechstmass_config.json`. Implement `verify_gate_records()`.
* **Step 3: Update Sprint Planner Skill**: Add `export-gates` step to Ceremony 2 in `.agents/skills/sprint-planner/SKILL.md`.
* **Step 4: Update Living Documentation**: Add `REQ-PRO-059` in `req_process_stability.md` and `TST-PRO-059` in `tst_process_stability.md`. Synchronize master index tables.
* **Step 5: Unit Test Coverage & Verification Run**:
  Add tests in `tools/test_jira_accounts.py` and `tools/test_verify_requirement_governance.py`.
  * Commands: `python3 -m unittest tools/test_jira_accounts.py && python3 -m unittest tools/test_verify_requirement_governance.py && python3 tools/verify_requirement_governance.py`

---

## 6. Verification & Rollback Plan
* **Verification**: 100% pass across all unit tests in `tools/`, `verify_requirement_governance.py` exits 0, and Gradle contract test passes.
* **Rollback**: `git reset --hard HEAD` on isolated branch `feature/ATT-6149`.
