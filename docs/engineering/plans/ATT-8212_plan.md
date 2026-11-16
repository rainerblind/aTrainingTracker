# Stage 3: Implementation Plan - ATT-8212: Establish SUP.1 QA plan and anchor regular weekly dual-vendor Gemini and Claude ASPICE 4.0 cross-audits

**Ticket**: [ATT-8212](https://rainerblind.atlassian.net/browse/ATT-8212) | **Sub-task**: [ATT-8239](https://rainerblind.atlassian.net/browse/ATT-8239) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-8138](https://rainerblind.atlassian.net/browse/ATT-8138) (*ASPICE 4.0 Audit Remediation 2026-11*)  
**Target Release**: `V5.0.0` | **Sprint**: `2026-46.8` | **Branch**: `feature/ATT-8212`  
**Requirement Mapping**: `REQ-PRO-096` | **Test Mapping**: `TST-PRO-096`  
**Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-15  

---

## 1. Problem Description & Background
Under ASPICE 4.0 `SUP.1` and `GP 2.1.6`, quality assurance activities must maintain demonstrable independence from work product authors. While `ATT-8213` established weekly dual-vendor code reviews, this plan establishes the overarching ASPICE 4.0 project quality assurance plan (`docs/management/qa_plan.md`), canonical audit evidence storage (`docs/engineering/aspice_audits/`), the `2026-11-15` dual-vendor baseline record, and automated backlog scheduling via `ensure-weekly-reviews`.

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-PRO-096` (*ASPICE 4.0 SUP.1 Quality Assurance Plan & Weekly Dual-Vendor Cross-Auditing Governance*)
* **Test Mapping**: `TST-PRO-096` (`TST-PRO-096.1`..`TST-PRO-096.5`) in `docs/tests/tst_process_stability.md`

---

## 3. System Invariants & Preserved Behavior
1. **Zero Production Runtime Code Changes**: `app/src/main/` is 100% untouched.
2. **Pure Python Stdlib Resilience**: Tooling runs without pip dependencies and gracefully handles mock/offline modes.
3. **Parent Human Gate Invariance**: Rule 3 (`Erledigt` reserved exclusively for human user) strictly preserved.
4. **Clean Coordination with ATT-8213**: Zero duplication or conflicting commands; `ensure-weekly-reviews` harmoniously orchestrates both code reviews and ASPICE audits.

---

## 4. Proposed Architectural Changes
* **Component 1 (`docs/management/qa_plan.md`)**: Expand to complete ASPICE 4.0 `SUP.1.BP1-BP6` and `GP 2.1.6` coverage, including quality objectives, 5-layer verification architecture, non-conformance escalation paths, and quality records.
* **Component 2 (`docs/engineering/aspice_audits/`)**: Create canonical directory registered in `cm_plan.md` and `ALLOWED_ENGINEERING_ENTRIES`. Author `README.md` and `2026-11-15_dual_vendor_aspice_audit.md` baseline linking to Epic `ATT-8138`.
* **Component 3 (`tools/jira_util.py`)**: Implement `is_weekly_aspice_audit_overdue()` and `ensure_weekly_aspice_audit_ticket()`, hooked into `ensure-weekly-reviews` CLI creating tickets under `ATT-232` at `rank-top`.
* **Component 4 (Governance & Skills)**: Update `.agents/rules/aspice_governance.md`, `sprint-planner/SKILL.md` (Ceremonies 1 & 2), and `release-manager/SKILL.md` (release gating).
* **UI Consistency**: No UI changes.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)
* **Risk Classification**: **STANDARD (Low/Medium Risk)** — pure Python tooling, test suites, and documentation; executed by **`subagent_flash`**.
* **Step 1: Canonical Audit Directory & Baseline**: Create `docs/engineering/aspice_audits/README.md` and `2026-11-15_dual_vendor_aspice_audit.md`. Register `aspice_audits` in `ALLOWED_ENGINEERING_ENTRIES` in `tools/verify_requirement_governance.py` and `cm_plan.md`.
* **Step 2: Comprehensive QA Plan**: Update `docs/management/qa_plan.md` to full `SUP.1.BP1-BP6` compliance.
* **Step 3: CLI Cadence Automation**: Add `is_weekly_aspice_audit_overdue()` and `ensure_weekly_aspice_audit_ticket()` to `tools/jira_util.py`, hooked into `ensure-weekly-reviews`.
* **Step 4: Governance & Skills Integration**: Update `.agents/rules/aspice_governance.md`, `sprint-planner/SKILL.md`, and `release-manager/SKILL.md`.
* **Step 5: Unit Testing & Living Docs**: Author unit tests in `tools/test_jira_accounts.py` and `tools/test_verify_requirement_governance.py`; update `REQ-PRO-096` and `TST-PRO-096` to `[Implemented]`.
* **Tier 1 Test Command**:
  ```bash
  python3 -m unittest tools/test_jira_accounts.py tools/test_verify_requirement_governance.py && python3 tools/verify_requirement_governance.py
  ```

---

## 6. Verification & Rollback Plan
* **Verification**: Execute targeted unit tests and living document governance verifier; test live CLI dry-run.
* **Rollback**: Branch isolation (`feature/ATT-8212`) ensures safe revert without impact on `sprint/2026-46.8`.
