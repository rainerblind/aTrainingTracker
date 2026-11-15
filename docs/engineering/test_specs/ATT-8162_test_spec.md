# Stage 2: Requirement & Test Specification - ATT-8162: Enforce capacity-bound sprint commitment and establish a V5.0.0 release plan

**Ticket**: [ATT-8162](https://rainerblind.atlassian.net/browse/ATT-8162) | **Epic**: [ATT-8138](https://rainerblind.atlassian.net/browse/ATT-8138) (*ASPICE 4.0 Audit Remediation 2026-11*)  
**Target Release**: `V5.0.0` | **Sprint**: `2026-46.8` | **Branch**: `feature/ATT-8162`  
**Requirement Mapping**: `REQ-PRO-097` (`REQ-PRO-097.1`..`REQ-PRO-097.5`) | **Test Spec ID**: `TST-PRO-097` (`TST-PRO-097.1`..`TST-PRO-097.5`)  
**Author**: AI Agent 1 (Implementer, `subagent_flash`) | **Date**: 2026-11-15  

---

## 1. Requirement Specification (REQ-PRO-097)

### 1.1 Problem Statement & Rationale
Under ASPICE 4.0 `MAN.3` (Finding `M10`), sprint commitments were historically overloaded (104–283 SP planned vs 24–30 SP envelope, completion down to 35%), mid-sprint aborts caused PO waiting time, no formal `V5.0.0` release plan existed, and `show_issue()` in `jira_util.py` emitted false positive FixVersion warnings on active in-sprint tickets, conflicting with Core Rule 8.3.

### 1.2 Attributes
- **Priority**: Medium | **Type**: Non-Functional / Governance | **Verification Method**: Unit Test / Review

### 1.3 Functional & Governance Requirements
1. `docs/management/release_plan.md` SHALL be established as a canonical configuration item in `cm_plan.md`, specifying target release `V5.0.0`, milestone epics, scheduled 12h sprint cycles, cumulative Google Pixel 10 physical daily-ride workout verification (`ATT-8141`), quality criteria, and gate exit criteria.
2. `select_greedy_bin_pack_tickets()` in `tools/jira_util.py` SHALL iterate candidate tickets from `Vom PO Freigegeben` (ID `447`) in strict `ORDER BY rank ASC`, filling up to `max_lanes` (default 3) disjoint Epic lanes up to a configurable per-lane capacity ceiling (`max_sp_per_lane`, default 10 SP). When a lane reaches capacity on Epic A, excess tickets of Epic A SHALL remain in `Vom PO Freigegeben` without skipping higher-ranked eligible tickets, while subsequent disjoint Epics fill Lanes 2 and 3.
3. In `tools/jira_util.py` (`show_issue()`), the warning `WARNING: Mandatory field missing!` for missing `FixVersion` SHALL be suppressed for active in-sprint and in-flight tickets (status != `Erledigt`), and SHALL be emitted strictly when an issue is in terminal status (`Erledigt`) without an assigned `fixVersions` array, aligning tooling with Core Rule 8.3.
4. `.agents/rules/aspice_governance.md`, `sprint-planner/SKILL.md`, and `release-manager/SKILL.md` SHALL reflect capacity-bound multi-lane packing from `Vom PO Freigegeben`, the 12h continuous cadence (`04:00` / `16:00`), and `V5.0.0` release plan compliance.
5. `tools/test_jira_accounts.py` SHALL verify multi-ticket lane packing, capacity boundary constraints, rank order integrity, and FixVersion warning emission logic with 100% test pass rate.

### 1.4 Acceptance Criteria (Given-When-Then)
* *Given* `Vom PO Freigegeben` (ID 447) with multiple tickets across disjoint Epics, *When* `select_greedy_bin_pack_tickets()` runs, *Then* it selects tickets in strict rank order across disjoint Epics up to per-lane capacity and leaves excess same-Epic tickets in the queue.
* *Given* an active in-sprint ticket without `FixVersion`, *When* `python3 tools/jira_util.py show <KEY>` is run, *Then* no `WARNING: Mandatory field missing!` warning is emitted for `FixVersion`.
* *Given* `docs/management/release_plan.md`, *When* inspected, *Then* it specifies `V5.0.0` scope epics, schedule, cumulative Pixel 10 workout verification, and release exit criteria.

### 1.5 System Invariants
Zero runtime changes in `app/src/main/`; Rule 3 Human Gate preserved; 100% living doc parity; pure Python standard library offline resilience.

---

## 2. Test Specification (TST-PRO-097)

### Test Case 1: `testMultiLaneCapacityBinPacking` (`TST-PRO-097.1`)
* **Scope**: Unit Test (`tools/test_jira_accounts.py`)
* **Expected Result**: `select_greedy_bin_pack_tickets()` packs tickets in strict `ORDER BY rank ASC` across disjoint Epics up to `max_sp_per_lane` / ticket limit; preserves excess tickets in queue.

### Test Case 2: `testFixVersionWarningSuppressionOnActiveTickets` (`TST-PRO-097.2`)
* **Scope**: Unit Test (`tools/test_jira_accounts.py`)
* **Expected Result**: `show_issue()` suppresses FixVersion missing warning on non-terminal statuses, emitting it only when status is `Erledigt` with empty `fixVersions`.

### Test Case 3: `testReleasePlanCompletenessAndIntegrity` (`TST-PRO-097.3`)
* **Scope**: Review / Process Verification
* **Expected Result**: `docs/management/release_plan.md` covers `V5.0.0` scope epics, 12h cadence, Pixel 10 daily-ride verification, and quality gate exit criteria.

### Test Case 4: `testGovernanceSkillsParity` (`TST-PRO-097.4`)
* **Scope**: Review / Process Verification
* **Expected Result**: `aspice_governance.md`, `sprint-planner/SKILL.md`, and `release-manager/SKILL.md` aligned with capacity-bound model and Rule 8.3.

### Test Case 5: `testLivingDocumentationParity` (`TST-PRO-097.5`)
* **Scope**: Automated Governance Verification (`tools/verify_requirement_governance.py`)
* **Expected Result**: 100% parity across `docs/requirements.md` and `docs/tests.md` with zero dangling IDs.

---

## 3. Traceability Matrix

| Test Case | Scope | Component Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-PRO-097.1` | Tooling | `jira_util.py:select_greedy_bin_pack_tickets` | `REQ-PRO-097.2` | Specified |
| `TST-PRO-097.2` | Tooling | `jira_util.py:show_issue` | `REQ-PRO-097.3` | Specified |
| `TST-PRO-097.3` | Process | `docs/management/release_plan.md` | `REQ-PRO-097.1` | Specified |
| `TST-PRO-097.4` | Process | Skills & Governance Rules | `REQ-PRO-097.4` | Specified |
| `TST-PRO-097.5` | Traceability | `verify_requirement_governance.py` | `REQ-PRO-097.5` | Specified |
