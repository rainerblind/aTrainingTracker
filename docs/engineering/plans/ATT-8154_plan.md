# Stage 3: Implementation Plan - ATT-8154: Close gate bypass paths in jira_util stage transitions

**Ticket**: [ATT-8154](https://rainerblind.atlassian.net/browse/ATT-8154)  
**Parent Epic**: [ATT-8138](https://rainerblind.atlassian.net/browse/ATT-8138) (*ASPICE 4.0 Audit Remediation 2026-11*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-11-16_03_Nacht` | **SP**: 2 SP  
**Requirement Mapping**: `REQ-PRO-109` (`REQ-PRO-109.1..5`)  
**Test Mapping**: `TST-PRO-109` (`TST-PRO-109.1..5`)  
**Branch**: `feature/ATT-8154` | **Author**: AI Agent 1 (`subagent_pro`) | **Date**: 2026-11-17  

---

## 1. Problem Description & Architectural Context
ASPICE 4.0 Audit Finding `Observation (SUP)` (SUP.1 BP3, GP 2.1.4) identified four critical gate bypass paths in `tools/jira_util.py`:
1. `submit_stage` auto-advances the parent through all intermediate stages if `curr_idx < target_idx`, allowing multi-stage skipping (e.g. Stage 4 from `Analysis`).
2. `final_review_verdict` auto-advances parents from any prior stage to `Final Review (Agent)`, skipping Gate 5.
3. `check_gate` checks only `status == "Erledigt"` on any issue key without verifying subtask type or `[Impl-Plan]` summary.
4. `final_review_verdict` Pros/Cons check relies on plain substring search (`any(kw in report_lower)`), susceptible to keyword stuffing.

---

## 2. Traceability & Requirements Mapping
* **`REQ-PRO-109.1` $\to$ `TST-PRO-109.1`**: Strict predecessor equality in `submit_stage()`; reject forward multi-stage skipping.
* **`REQ-PRO-109.2` $\to$ `TST-PRO-109.2`**: Require parent strictly in `Final Review (Agent)` in `final_review_verdict()`; reject Gate 5 bypass.
* **`REQ-PRO-109.3` $\to$ `TST-PRO-109.3`**: Validate `issuetype.subtask == True` and `[Impl-Plan]` summary pattern in `check_gate()`.
* **`REQ-PRO-109.4` $\to$ `TST-PRO-109.4`**: Validate structured Pros/Cons and recommendation markdown sections or options table in `final_review_verdict()`.
* **`REQ-PRO-109.5` $\to$ `TST-PRO-109.5`**: Preserve Coordinator commands (`pack-sprint`, `return-unstarted`, `carryover-wip`, `archive-po-acceptance`, `defer-systemtest`), `ATT-8153`, and `ATT-8426`.

---

## 3. System Invariants & Preserved Behavior
1. **Inviolable Human Gate**: Parent ticket transitions to `Erledigt` strictly reserved for humans.
2. **Technical Role Separation**: Role separation between `agent1` (author) and `agent2` (reviewer) strictly enforced.
3. **Zero Production Code Edits**: Zero edits to `app/src/main/` Android runtime code.
4. **Sanctioned Coordinator & Healing Workflows**: Coordinator operations and Stage 6 bounce healing transitions to `Final Review (Agent)` preserved.
5. **Pure Python Stdlib**: Zero external pip dependencies; offline execution resilience.

---

## 4. Step-by-Step Implementation Sequence (Stage 4 Construction)
* **Step 1 (`REQ-PRO-109.1` / `tools/jira_util.py`)**:
  - In `submit_stage()`, remove the forward loop across `PARENT_STAGE_ORDER`.
  - For Stage 1: allow parent transition from `zu erledigen` -> `analysis` (1 step), or accept if already in `analysis`. Reject any other parent state.
  - For Stages 2..5: strictly assert `current_parent_status == desired_parent_stage` (`test spec`, `implementation plan`, `implementation`, `test`). Reject multi-stage skips with exit code 1.
* **Step 2 (`REQ-PRO-109.2`, `REQ-PRO-109.4` / `tools/jira_util.py`)**:
  - In `final_review_verdict()`, remove the forward loop across `PARENT_STAGE_ORDER`. Reject execution immediately unless `current_status == "final review (agent)"`.
  - In Pros/Cons validation, require structured Markdown section headers with non-empty content (`* *Pros*:` / `* **Pros**:`, `* *Cons*:` / `* **Cons**:`, `* *Würdest du...*:`) or a markdown table with >= 2 options, rejecting bare keyword stuffing.
* **Step 3 (`REQ-PRO-109.3` / `tools/jira_util.py`)**:
  - In `check_gate()`, query `fields=status,summary,issuetype,parent`.
  - Assert `issuetype.subtask == True` and summary matches `[Impl-Plan]` prefix pattern. If not, exit 1.
  - Assert `status == "Erledigt"`. If not, exit 1 (`GATE_BLOCKED`). Return True on success.
* **Step 4 (`TST-PRO-109.1..5` / `tools/test_jira_accounts.py` & `tools/test_jira_epics.py`)**:
  - In `tools/test_jira_accounts.py`, add test methods covering: (1) `submit_stage` multi-stage jump rejection; (2) `final_review_verdict` non-Agent-Review parent rejection; (3) `check_gate` non-subtask and non-Impl-Plan rejection; (4) structured Pros/Cons validation and keyword stuffing rejection; (5) Coordinator workflow preservation.
  - Update `tools/test_jira_epics.py` fixture with structured report text.
* **Step 5 (Verification & Governance Parity)**:
  - Run `python3 -m unittest tools/test_jira_accounts.py tools/test_jira_epics.py`.
  - Run `python3 tools/verify_requirement_governance.py`.

---

## 5. Verification & Rollback Plan
* **Verification**: 100% test pass on `tools/test_jira_accounts.py` and exit code 0 on `tools/verify_requirement_governance.py`.
* **Rollback**: `git checkout feature/ATT-8154 -- tools/` cleanly reverts modifications.
