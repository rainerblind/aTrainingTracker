# Stage 2: Requirement & Test Specification - ATT-8154: Close gate bypass paths in jira_util stage transitions

**Ticket**: [ATT-8154](https://rainerblind.atlassian.net/browse/ATT-8154)  
**Parent Epic**: [ATT-8138](https://rainerblind.atlassian.net/browse/ATT-8138) (*ASPICE 4.0 Audit Remediation 2026-11*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-11-16_03_Nacht` | **SP**: 2 SP  
**Requirement Mapping**: `REQ-PRO-109` (`REQ-PRO-109.1..5`)  
**Test Spec ID**: `TST-PRO-109` (`TST-PRO-109.1..5`)  
**Branch**: `feature/ATT-8154` | **Author**: AI Agent 1 (`subagent_pro`) | **Date**: 2026-11-17  

---

## 1. Requirement Specification (REQ-PRO-109)
* **Attributes**: Priority: `High` | Type: `Non-Functional` | Verification Method: `Unit / Review`
* **REQ-PRO-109.1 (Strict Predecessor Stage Validation in `submit-stage` — [Planned])**: `tools/jira_util.py submit-stage` SHALL enforce that parent tickets are in the exact expected predecessor stage. Stage 1 permits `zu erledigen` $\to$ `analysis` (single step) or `analysis`; Stages 2..5 SHALL strictly require `current_parent_status == desired_parent_stage`, failing with exit code 1 if an agent attempts a multi-stage jump (e.g. Stage 4 from `Analysis`).
* **REQ-PRO-109.2 (Strict Gate 5 Prerequisite in `final-review-verdict` — [Planned])**: `tools/jira_util.py final-review-verdict` SHALL reject execution unless the parent ticket is strictly in `Final Review (Agent)`, preventing premature parent stage advancement past Gate 5, while preserving `--bounce-target` healing and standard bounce transitions.
* **REQ-PRO-109.3 (Strict Subtask & Impl-Plan Summary Validation in `check-gate` — [Planned])**: `tools/jira_util.py check-gate` SHALL validate that `issue_key` is a sub-task (`issuetype.subtask == True`) and that its summary matches the `[Impl-Plan]` prefix/pattern (Gate 3). Non-subtasks or non-Impl-Plan subtasks SHALL be rejected with exit code 1 regardless of status.
* **REQ-PRO-109.4 (Structured Pros/Cons & Recommendation Validation — [Planned])**: `tools/jira_util.py final-review-verdict` on `PASS` SHALL validate that report text contains structured markdown sections with non-empty content for Pros, Cons / Defects, and Recommendation, or a markdown table with >= 2 options, rejecting bare keyword stuffing.
* **REQ-PRO-109.5 (Sanctioned Workflow & Protection Invariants — [Planned])**: Coordinator workflows (`pack-sprint`, `return-unstarted`, `carryover-wip`, `archive-po-acceptance`, `defer-systemtest`), `ATT-8153` QA escalation, and `ATT-8426` 7-day review currency SHALL remain strictly preserved.

---

## 2. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Net-new requirement `REQ-PRO-109` refining `REQ-PRO-016`, `REQ-PRO-058`, and `REQ-PRO-060`. Targets `tools/jira_util.py` and `tools/test_jira_accounts.py`.
* **Historical Origin & Commit Trace**: Sprint 2026-11-16_03_Nacht via ticket `ATT-8154` under Epic `ATT-8138` (ASPICE 4.0 Audit Remediation).
* **Root Reason for Existing Formulation**: Early composite CLI commands auto-walked parent stages forward in a loop for local development convenience, creating bypass paths observed during ASPICE audit.
* **Preservation of Core Invariants**: Inviolable human decision gate (`REQ-PRO-016`), role separation (`agent1` vs `agent2`), 3-bounce healing loop, POSIX flock merge lock, pure Python stdlib offline resilience strictly preserved.

---

## 3. Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Multi-Stage Jump Rejection)**: *Given* a parent ticket in `Analysis`, *When* `submit-stage 4` is called, *Then* the command fails with exit code 1.
* **Criterion 2 (Premature Final Review Rejection)**: *Given* a parent ticket not in `Final Review (Agent)`, *When* `final-review-verdict PASS` is called, *Then* the command fails with exit code 1.
* **Criterion 3 (Strict Impl-Plan Gate Check)**: *Given* an issue key that is not an `[Impl-Plan]` subtask (e.g. an Analysis subtask or parent ticket), *When* `check-gate` is run, *Then* it fails with exit code 1 even if status is `Erledigt`.
* **Criterion 4 (Structured Pros/Cons Validation)**: *Given* a Stage 6 review report with unstructured keyword stuffing, *When* `final-review-verdict PASS` executes, *Then* it rejects the verdict with an error.

---

## 4. Test Specification (TST-PRO-109)
* **TST-PRO-109.1 (Submit-Stage Predecessor Enforcement Test)**: In `tools/test_jira_accounts.py`, verify `submit_stage` rejects forward stage jumps and accepts matching predecessor states.
* **TST-PRO-109.2 (Final-Review-Verdict Predecessor Enforcement Test)**: In `tools/test_jira_accounts.py`, verify `final_review_verdict` rejects parents not in `Final Review (Agent)`.
* **TST-PRO-109.3 (Check-Gate Subtask & Summary Validation Test)**: In `tools/test_jira_accounts.py`, verify `check_gate` validates subtask type and `[Impl-Plan]` summary.
* **TST-PRO-109.4 (Structured Pros/Cons Validation Test)**: In `tools/test_jira_accounts.py`, verify `final_review_verdict` accepts structured sections/tables and rejects keyword stuffing.
* **TST-PRO-109.5 (Coordinator & Bounce Workflow Invariants Test)**: Verify existing coordinator and bounce healing workflows remain fully functional.

---

## 5. Traceability Matrix

| Test Case | Scope | Target File / Class | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-PRO-109.1` | Unit | `tools/jira_util.py`, `tools/test_jira_accounts.py` | `REQ-PRO-109.1` | Specified |
| `TST-PRO-109.2` | Unit | `tools/jira_util.py`, `tools/test_jira_accounts.py` | `REQ-PRO-109.2` | Specified |
| `TST-PRO-109.3` | Unit | `tools/jira_util.py`, `tools/test_jira_accounts.py` | `REQ-PRO-109.3` | Specified |
| `TST-PRO-109.4` | Unit | `tools/jira_util.py`, `tools/test_jira_accounts.py` | `REQ-PRO-109.4` | Specified |
| `TST-PRO-109.5` | Unit | `tools/jira_util.py`, `tools/test_jira_accounts.py` | `REQ-PRO-109.5` | Specified |
