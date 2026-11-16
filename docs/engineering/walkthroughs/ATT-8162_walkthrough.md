# Stage 5 Walkthrough: ATT-8162 - Enforce capacity-bound sprint commitment and establish a V5.0.0 release plan

**Ticket**: [ATT-8162](https://rainerblind.atlassian.net/browse/ATT-8162)  
**Parent Epic**: [ATT-8138](https://rainerblind.atlassian.net/browse/ATT-8138) (*ASPICE 4.0 Audit Remediation 2026-11*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.8`  
**Requirement Mapping**: `REQ-PRO-097` (`REQ-PRO-097.1`..`REQ-PRO-097.5`) [Verified]  
**Test Spec Mapping**: `TST-PRO-097` (`TST-PRO-097.1`..`TST-PRO-097.5`) [Verified]  
**Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-15  

---

## 1. Executive Summary & Verification Walkthrough
This ticket operationalizes capacity-bound sprint commitments, multi-lane bin-packing, formal release governance, and FixVersion warning alignment resolving ASPICE 4.0 `MAN.3` Finding `M10`, `SUP.8`, and `SUP.10`:
1. **Formal V5.0.0 Product Release Plan (`docs/management/release_plan.md`)**:
   - Established as canonical configuration item in `docs/management/cm_plan.md`.
   - Specifies `V5.0.0` scope epics (`ATT-8138`, `ATT-8141`, `ATT-235`, `ATT-5910`), 12h continuous sprint cadence (`04:00` / `16:00`), cumulative Google Pixel 10 physical daily-ride workout verification (`ATT-8141`), quality criteria, and release gate exit criteria.
2. **Multi-Lane Greedy Bin-Packing with Capacity Ceiling (`tools/jira_util.py`)**:
   - Enhanced `select_greedy_bin_pack_tickets(ranked_issues, max_lanes=3, max_sp_per_lane=None)` to accumulate Story Points per disjoint Epic lane up to `max_sp_per_lane` (defaulting unpointed issues to 3.0 SP).
   - Strict `ORDER BY rank ASC` order is maintained without skipping eligible higher-ranked tickets; excess tickets of saturated Epics remain safely in `Vom PO Freigegeben` (ID `447`).
   - Extended `pack-sprint` CLI subcommand with `--max-sp-per-lane` option.
3. **FixVersion Warning Alignment with Core Rule 8.3 (`tools/jira_util.py`)**:
   - Updated `show_issue()` to suppress `WARNING: Mandatory field missing!` on non-terminal statuses (`Zu erledigen`, `In Bearbeitung`, `In Überprüfung`).
   - Subtasks explicitly note `None on subtask (per Core Rule 8.3)`.
   - Terminal tickets (`Erledigt`, `Closed`, `Done`) without `fixVersions` explicitly alert `WARNING: Mandatory field missing on completed issue!`.
4. **Governance Rules & Skills Parity**:
   - Updated `.agents/rules/aspice_governance.md` (Rules 8.3 & 9.1), `sprint-planner/SKILL.md` (Ceremony 1 multi-lane packing), `release-manager/SKILL.md` (release gating and FixVersion stamping), and `tools/hoechstmass` mirrors.

---

## 2. Verification Evidence & Test Execution

### Targeted Python Unit & Governance Tests
* **Test Command**:
  ```bash
  python3 -m unittest tools/test_jira_accounts.py tools/test_verify_requirement_governance.py && python3 tools/verify_requirement_governance.py
  ```
* **Execution Results**:
  * `tools/test_jira_accounts.py` & `tools/test_verify_requirement_governance.py`: **115 / 115 tests PASSED** in 14.74s (exit code 0).
    - `test_select_greedy_bin_pack_tickets_with_capacity_ceiling` (`TST-PRO-097.1`): Verified multi-lane greedy bin-packing accumulates tickets up to `max_sp_per_lane` across disjoint Epics and preserves excess tickets in queue.
    - `test_show_issue_fixversion_warning_alignment` (`TST-PRO-097.2`): Verified suppression of FixVersion missing warning on active issues and emission on completed issues.
  * `tools/verify_requirement_governance.py`: **11 / 11 checks PASSED** (exit code 0) confirming 100% master index parity, zero dangling references, and zero orphan requirements.

---

## 3. Living Documentation Status Parity

| Requirement / Test ID | Component | Target Release | Status | Verification Method |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-PRO-097` | `docs/requirements/req_process_stability.md` | `V5.0.0` | **`[Verified]`** | Unit Test & Governance Verification |
| `TST-PRO-097` | `docs/tests/tst_process_stability.md` | `V5.0.0` | **`[Verified]`** | Automated Unit Tests (`test_jira_accounts.py`) |

* **Master Index Parity**:
  - `docs/requirements.md`: Total=1151, Verified=1137, Implemented=14, Planned=0 (100% parity).
  - `docs/tests.md`: Total=1153, Verified=1152, Implemented=1, Planned=0 (100% parity).

---

## 4. System Invariants Certification
* **Zero Production Runtime Code Changes**: `app/src/main/` is 100% untouched.
* **Human Decision Gate Intact**: Rule 3.1 preserved; parent ticket transitions governed.
* **Pure Python Stdlib**: Tooling runs without external third-party dependencies.
