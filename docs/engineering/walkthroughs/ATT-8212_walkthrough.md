# Stage 5 Walkthrough: ATT-8212 - Establish SUP.1 QA plan and anchor regular weekly dual-vendor Gemini and Claude ASPICE 4.0 cross-audits

**Ticket**: [ATT-8212](https://rainerblind.atlassian.net/browse/ATT-8212)  
**Parent Epic**: [ATT-8138](https://rainerblind.atlassian.net/browse/ATT-8138) (*ASPICE 4.0 Audit Remediation 2026-11*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.8`  
**Requirement Mapping**: `REQ-PRO-096` (`REQ-PRO-096.1`..`REQ-PRO-096.5`) [Verified]  
**Test Spec Mapping**: `TST-PRO-096` (`TST-PRO-096.1`..`TST-PRO-096.5`) [Verified]  
**Author**: AI Agent 1 (Implementer, `subagent_flash`) | **Date**: 2026-11-15  

---

## 1. Executive Summary & Verification Walkthrough
This ticket formalizes the project Quality Assurance strategy and institutionalizes automated weekly dual-vendor (Google Gemini + Anthropic Claude) ASPICE 4.0 cross-audits satisfying ASPICE 4.0 `SUP.1.BP1-BP6` and `GP 2.1.6`:
1. **Formal SUP.1 Project Quality Assurance Plan (`docs/management/qa_plan.md`)**:
   - Expanded to full ASPICE 4.0 SUP.1 compliance: Quality strategy (BP1), Quality criteria (BP2), Work product compliance (BP3), Process compliance (BP4), Problem escalation & resolution (BP5), and Quality records (BP6).
   - Anchored technical role independence (GP 2.1.6) across 5 verification layers: Layer 1 Static & Architecture, Layer 2 Per-ticket Stage 1–5 ASPICE Gates, Layer 3 In-Sprint QA Sampling (`qa-audit`), Layer 4 End-of-Sprint Integration Gate, and Layer 5 Weekly Dual-Vendor Audits (Code & ASPICE 4.0).
2. **Canonical ASPICE Audit Evidence Store (`docs/engineering/aspice_audits/`)**:
   - `README.md`: Canonical structure and dual-vendor audit protocol.
   - `2026-11-15_dual_vendor_aspice_audit.md`: Formal baseline audit record for Epic ATT-8138.
   - Registered `aspice_audits` in `ALLOWED_ENGINEERING_ENTRIES` (`tools/verify_requirement_governance.py`) and `docs/management/cm_plan.md`.
3. **Automated Top-of-Backlog Audit Ticket Creation (`tools/jira_util.py`)**:
   - Implemented `is_weekly_aspice_audit_overdue()`, `ensure_weekly_aspice_audit_ticket()`, and composite `ensure_weekly_reviews()`.
   - Automatically generates deduplicated ASPICE cross-audit tickets under Epic `ATT-232` ranked at `rank-top` whenever `> 7 days` elapse.
4. **Governance Rules & Skills Parity**:
   - Anchored dual-pillar review policy in `.agents/rules/aspice_governance.md` (Rules 3.5 & 8.4), `sprint-planner/SKILL.md` (Ceremonies 1 & 2), and `release-manager/SKILL.md` (pre-release currency gate).

---

## 2. Verification Evidence & Test Execution

### Targeted Python Unit & Governance Tests
* **Test Command**:
  ```bash
  python3 -m unittest tools/test_jira_accounts.py tools/test_verify_requirement_governance.py && python3 tools/verify_requirement_governance.py
  ```
* **Execution Results**:
  * `tools/test_jira_accounts.py` & `tools/test_verify_requirement_governance.py`: **113 / 113 tests PASSED** in 14.41s (exit code 0).
    - `test_scenario_weekly_dual_vendor_aspice_audits_registration`: Verified `aspice_audits` in `ALLOWED_ENGINEERING_ENTRIES`.
    - `test_is_weekly_aspice_audit_overdue`: Verified 7-day threshold and overdue detection logic.
    - `test_ensure_weekly_aspice_audit_creates_and_deduplicates`: Verified `create_issue` with `rank_top=True` under `ATT-232` and deduplication.
    - `test_ensure_weekly_reviews_coordinates_both_pillars`: Verified coordinated dispatch of both code review and ASPICE audit tickets.
  * `tools/verify_requirement_governance.py`: **11 / 11 checks PASSED** (exit code 0) confirming 100% master index parity, zero dangling references, and zero orphan requirements.

---

## 3. Living Documentation Status Parity

| Requirement / Test ID | Component | Target Release | Status | Verification Method |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-PRO-096` | `docs/requirements/req_process_stability.md` | `V5.0.0` | **`[Verified]`** | Unit Test & Governance Verification |
| `TST-PRO-096` | `docs/tests/tst_process_stability.md` | `V5.0.0` | **`[Verified]`** | Automated Unit Tests (`test_jira_accounts.py`) |

* **Master Index Parity**:
  - `docs/requirements.md`: Total=1149, Verified=1135, Implemented=14, Planned=0 (100% parity).
  - `docs/tests.md`: Total=1151, Verified=1150, Implemented=1, Planned=0 (100% parity).

---

## 4. System Invariants Certification
* **Zero Production Runtime Code Changes**: `app/src/main/` is 100% untouched.
* **Human Decision Gate Intact**: Rule 3.1 preserved; sub-tasks end cleanly at `Erledigt`, parent transitions governed.
* **Pure Python Stdlib**: Tooling runs without external third-party dependencies.
