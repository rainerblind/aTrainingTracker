# Stage 1 Analysis: ATT-8154 - Close gate bypass paths in jira_util stage transitions

**Ticket**: [ATT-8154](https://rainerblind.atlassian.net/browse/ATT-8154)  
**Parent Epic**: [ATT-8138](https://rainerblind.atlassian.net/browse/ATT-8138) (*ASPICE 4.0 Audit Remediation 2026-11*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-11-16_03_Nacht` | **SP**: 2 SP  
**Branch**: `feature/ATT-8154` | **Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-17  

---

## 1. Problem Statement & Motivation
ASPICE 4.0 Audit Finding `Observation (SUP)` (SUP.1 BP3, GP 2.1.4) identified four critical gate bypass paths in `tools/jira_util.py`:
1. **Multi-Stage Parent Skipping in `submit_stage`**: L1528–1533 auto-advances the parent through all intermediate stages if `curr_idx < target_idx`, allowing an agent to jump from `Analysis` to `Implementation` or `Test` without gate verdicts.
2. **Stage 6 Parent Forwarding in `final_review_verdict`**: L2242–2247 auto-advances parents from any prior stage to `Final Review (Agent)`, bypassing Gate 5 verification.
3. **Unvalidated Key Checking in `check_gate`**: L769–779 only checks `status == "Erledigt"` on any issue key, accepting arbitrary tickets or non-Impl-Plan subtasks.
4. **Naive Pros/Cons Check**: L2251–2256 relies on plain substring search (`any(kw in report_lower)`), allowing unstructured keyword stuffing to pass Stage 6 review.

---

## 2. Root Cause Analysis & Independent Peer Review
* **RCA 1 (Permissive Stage Walking)**: `submit_stage` and `final_review_verdict` iteratively walked `PARENT_STAGE_ORDER` forward rather than asserting strict predecessor state.
* **RCA 2 (Unconstrained Gate Target)**: `check_gate` omitted `issuetype.subtask` and `[Impl-Plan]` summary validation.
* **RCA 3 (Unstructured Review Validation)**: Stage 6 review validation lacked regex/structural verification for ceremony questions.
* **Independent Peer Review & Verification (ATT-3534)**:
  * Audited `submit_stage`: Confirmed that calling `submit-stage 4` while parent is in `Analysis` transitions parent through `Test Spec` and `Implementation Plan` without Gate 1–3 approvals.
  * Audited `final_review_verdict`: Confirmed calling `final-review-verdict PASS` on a ticket in `Implementation` advances parent to `Final Review (Agent)` and then `Final Review (Human)`.
  * Audited `check_gate`: Verified that passing any closed issue key (e.g. an Analysis subtask or closed bug) exits 0.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  1. Enforce strict predecessor status in `submit_stage`: Stage 1 allows `zu erledigen` -> `analysis` (1 step) or `analysis`; Stages 2..5 require parent to already be in `desired_parent_stage`.
  2. Enforce strict predecessor status in `final_review_verdict`: reject execution unless parent is in `Final Review (Agent)`.
  3. Validate `check_gate` targets: assert issue is a sub-task and summary matches `[Impl-Plan]`.
  4. Require structured Pros/Cons & recommendation sections (or markdown table) in `final_review_verdict`.
  5. Add unit tests in `tools/test_jira_accounts.py` covering all four bypass paths.
* **Out-of-Scope Non-Goals**:
  - Modifying Android runtime code in `app/src/main/`.
  - Disturbing sanctioned coordinator commands (`pack-sprint`, `return-unstarted`, `carryover-wip`, `archive-po-acceptance`, `defer-systemtest`), `ATT-8153` QA escalation, `ATT-8426` 7-day review gate, or bounce healing transitions back to `Final Review (Agent)`.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Net-new requirement `REQ-PRO-109` / `TST-PRO-109` (reserved for `ATT-8154`), refining `REQ-PRO-016`, `REQ-PRO-058`, and `REQ-PRO-060`. Targets `tools/jira_util.py` and `tools/test_jira_accounts.py`.
* **Historical Origin & Commit Trace**: Early composite CLI tooling (`ATT-4290`, `ATT-7101`) permitted multi-stage forward loops for developer convenience, leaving bypass tripwires.
* **Root Reason for Existing Formulation**: Permissive loops simplified local testing across stages before strict ASPICE role separation was established.
* **Preservation of Core Invariants**: Inviolable human decision gate (`REQ-PRO-016`), role separation (`agent1` vs `agent2`), 3-bounce healing loop, POSIX flock merge lock, and pure Python stdlib offline resilience strictly preserved.

---

## 5. Change-Request Impact Analysis (SUP.10)
* **Affected Requirements**: Net-new `REQ-PRO-109` (`REQ-PRO-109.1..5`) and `TST-PRO-109` in `req_process_stability.md` / `tst_process_stability.md`.
* **Affected Architecture Elements**: `tools/jira_util.py`, `tools/test_jira_accounts.py`.
* **Verification Impact & Test Surface**: Unit tests in `test_jira_accounts.py` covering multi-stage jump rejection, non-Agent-Review verdict rejection, `check-gate` subtask validation, and structured Pros/Cons parsing.
* **Risk Assessment & Invariants**: **LOW** (2 SP tooling defect fix; zero Android production runtime impact).

---

## 6. Architectural Strategy & High-Level Solution
* **Strict Predecessor Assertion**: In `submit_stage`, fail if `current_parent_status != desired_parent_stage` (allowing Stage 1 `zu erledigen` -> `analysis` single step).
* **Final Review Guard**: In `final_review_verdict`, immediately fail if `current_status != "final review (agent)"`.
* **Gate 3 Type & Summary Guard**: In `check_gate`, fetch `issuetype,summary,status` and reject non-subtasks or summaries not matching `[Impl-Plan]`.
* **Structured Ceremony Validator**: In `final_review_verdict`, validate structured Markdown sections (`* *Pros...*:`, `* *Cons...*:`, `* *Würdest du...*:`) with non-empty bullets or markdown table.

---

## 7. System Invariants & Risk Assessment
* **Core Invariants**: (1) `app/src/main/` untouched; (2) Sanctioned coordinator/healing flows intact; (3) Pure Python stdlib; (4) `ANT-Android-SDKs` intact at `0e8cea71`.
* **Risk Rating**: **LOW** (2 SP developer tooling defect fix).
