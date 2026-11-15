# Stage 3: Implementation Plan - ATT-8162: Enforce capacity-bound sprint commitment and establish a V5.0.0 release plan

**Ticket**: [ATT-8162](https://rainerblind.atlassian.net/browse/ATT-8162) | **Sub-task**: [ATT-8247](https://rainerblind.atlassian.net/browse/ATT-8247) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-8138](https://rainerblind.atlassian.net/browse/ATT-8138) (*ASPICE 4.0 Audit Remediation 2026-11*)  
**Target Release**: `V5.0.0` | **Sprint**: `2026-46.8` | **Branch**: `feature/ATT-8162`  
**Requirement Mapping**: `REQ-PRO-097` | **Test Mapping**: `TST-PRO-097`  
**Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-15  

---

## 1. Problem Description & Background
Under ASPICE 4.0 `MAN.3` (Finding `M10`), sprint commitments were historically overloaded (104–283 SP planned vs 24–30 SP envelope), mid-sprint aborts caused PO waiting time, and no formal `V5.0.0` release plan existed. Furthermore, `show_issue()` in `jira_util.py` emitted false-positive FixVersion warnings on active in-sprint tickets (contradicting Core Rule 8.3). While Sprint `2026-46.7` (ATT-8030) introduced initial 12h cadence, queue swapping, and single-ticket per-lane selection, this plan establishes the formal `V5.0.0` release plan (`docs/management/release_plan.md`), completes multi-ticket lane-aware capacity bin-packing in `jira_util.py`, aligns FixVersion warnings, and updates living documentation and skills.

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-PRO-097` (*Capacity-Bound Sprint Commitment, Multi-Lane Bin-Packing & V5.0.0 Release Plan*)
* **Test Mapping**: `TST-PRO-097` (`TST-PRO-097.1`..`TST-PRO-097.5`) in `docs/tests/tst_process_stability.md`

---

## 3. System Invariants & Preserved Behavior
1. **Zero Production Runtime Code Changes**: `app/src/main/` is 100% untouched.
2. **Pure Python Stdlib Resilience**: Tooling runs without pip dependencies and handles mock/offline modes cleanly.
3. **Parent Human Gate Invariance**: Rule 3 (`Erledigt` reserved exclusively for human user) strictly preserved.
4. **PO Rank Order Preservation**: Tickets are strictly selected `ORDER BY rank ASC`; never skip eligible tickets out of order.
5. **Epics Disjoint Across Lanes**: Different lanes never work on the same Parent Epic simultaneously.

---

## 4. Proposed Architectural Changes
* **Component 1 (`docs/management/release_plan.md`)**: Create formal V5.0.0 release plan covering scope epics, 12h sprint cadence (`04:00` / `16:00`), cumulative Pixel 10 physical daily-ride verification (`ATT-8141`), quality criteria, and release gate exit checklist. Register in `cm_plan.md`.
* **Component 2 (`tools/jira_util.py`)**:
  - Enhance `select_greedy_bin_pack_tickets(ranked_issues, max_lanes=3, max_sp_per_lane=10)` to accumulate tickets per disjoint Epic lane up to SP capacity limit (parsing story points from `customfield_10016` or default 3 SP), leaving excess same-Epic tickets in `Vom PO Freigegeben`.
  - Update `show_issue()` to suppress `WARNING: Mandatory field missing!` on non-terminal statuses, emitting it strictly when status is `Erledigt` without `fixVersions`.
* **Component 3 (Governance & Skills)**: Update `.agents/rules/aspice_governance.md` (Rule 8.3 FixVersion clarification and Rule 9.1 capacity parameters), `sprint-planner/SKILL.md`, and `release-manager/SKILL.md`.
* **Component 4 (Living Docs & Tests)**: Update `docs/requirements/req_process_stability.md` and `docs/tests/tst_process_stability.md` to `[Implemented]`; add unit tests in `tools/test_jira_accounts.py`.
* **UI Consistency**: No UI changes.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)
* **Risk Classification**: **STANDARD (Low/Medium Risk)** — documentation, tooling, and test updates; executed by **`subagent_flash`**.
* **Step 1: V5.0.0 Release Plan & CM Registration**: Create `docs/management/release_plan.md` and register it in `docs/management/cm_plan.md`.
* **Step 2: Tooling - Multi-Lane Capacity Packing & FixVersion Warning**: Enhance `select_greedy_bin_pack_tickets()` with `max_sp_per_lane` capacity accumulator and fix `show_issue()` FixVersion warning condition in `tools/jira_util.py`.
* **Step 3: Governance Rules & Skills Alignment**: Update `aspice_governance.md`, `sprint-planner/SKILL.md`, and `release-manager/SKILL.md`.
* **Step 4: Unit Testing & Living Docs Parity**: Author unit tests for multi-lane capacity packing and FixVersion warnings in `tools/test_jira_accounts.py`; update `REQ-PRO-097` and `TST-PRO-097` to `[Implemented]`.
* **Tier 1 Test Command**:
  ```bash
  python3 -m unittest tools/test_jira_accounts.py && python3 tools/verify_requirement_governance.py
  ```

---

## 6. Verification & Rollback Plan
* **Verification**: Run unit tests in `tools/test_jira_accounts.py`, execute governance verifier `tools/verify_requirement_governance.py`, and test `jira_util.py` CLI dry-runs.
* **Rollback**: Branch isolation (`feature/ATT-8162`) ensures safe revert without impact on `sprint/2026-46.8`.
