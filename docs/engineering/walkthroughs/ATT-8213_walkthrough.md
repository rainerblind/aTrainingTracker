# Stage 5 Walkthrough: ATT-8213 - Mandate and document weekly dual-vendor Gemini and Claude holistic code and architecture reviews

**Ticket**: [ATT-8213](https://rainerblind.atlassian.net/browse/ATT-8213)  
**Parent Epic**: [ATT-8138](https://rainerblind.atlassian.net/browse/ATT-8138) (*ASPICE 4.0 Audit Remediation 2026-11*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.8`  
**Requirement Mapping**: `REQ-PRO-095` (`REQ-PRO-095.1`..`REQ-PRO-095.5`) [Verified]  
**Test Spec Mapping**: `TST-PRO-095` (`TST-PRO-095.1`..`TST-PRO-095.5`) [Verified]  
**Author**: AI Agent 1 (Implementer, `subagent_flash`) | **Date**: 2026-11-15  

---

## 1. Executive Summary & Verification Walkthrough
This ticket mandates and operationalizes weekly dual-vendor (Google Gemini + Anthropic Claude) holistic code and architecture reviews across full subsystem slices of the existing ~200k+ LoC codebase alongside delta commits:
1. **Canonical Evidence Store & Coverage Matrix (`docs/engineering/code_reviews/`)**:
   - `coverage_matrix.md`: Maps all 9 codebase packages (`tracker/`, `banalservice/`, `database/`, `exporter/`, `ui/tracking/`, `ui/aftermath/`, `ui/settings/`, `tools/`, `test/`) with status (`UNREVIEWED` $\to$ `AUDITED`) to drive systematic full-codebase audits.
   - `template_dual_vendor_code_review.md`: Seeded standard dual-vendor report template.
   - Registered `code_reviews` in `ALLOWED_ENGINEERING_ENTRIES` (`tools/verify_requirement_governance.py`) and `docs/management/cm_plan.md`.
2. **Backlog Automation (`tools/jira_util.py`)**:
   - Implemented `get_next_review_subsystem()`, `is_weekly_review_overdue()`, and `ensure_weekly_review_ticket()` (`ensure-weekly-reviews` CLI).
   - Whenever `> 7 days` elapse or unreviewed subsystems exist, creates a deduplicated review ticket under `ATT-232` ranked at `rank-top`.
3. **Governance & Skills Integration**:
   - Anchored review policy in `docs/coding_standards.md` (Sec 5.12), `docs/management/qa_plan.md` (Layer 5), `.agents/rules/aspice_governance.md` (Rule 3.5 & Rule 8.4), `sprint-planner/SKILL.md` (Ceremonies 1 & 2), and `release-manager/SKILL.md` (pre-release currency gate).

---

## 2. Verification Evidence & Test Execution

### Targeted Python Unit & Governance Tests
* **Test Command**:
  ```bash
  python3 -m unittest tools/test_jira_accounts.py tools/test_verify_requirement_governance.py && python3 tools/verify_requirement_governance.py
  ```
* **Execution Results**:
  * `tools/test_jira_accounts.py` & `tools/test_verify_requirement_governance.py`: **109 / 109 tests PASSED** in 15.63s (exit code 0).
    - `test_scenario_weekly_dual_vendor_code_reviews_registration`: Verified `code_reviews` allowed in `ALLOWED_ENGINEERING_ENTRIES`.
    - `test_get_next_review_subsystem_unreviewed_and_rotation`: Verified matrix parsing, UNREVIEWED selection, and oldest AUDITED rotation.
    - `test_is_weekly_review_overdue`: Verified 7-day threshold and UNREVIEWED detection.
    - `test_ensure_weekly_review_creates_and_deduplicates`: Verified `create_issue` with `rank_top=True` under `ATT-232` and deduplication.
  * `tools/verify_requirement_governance.py`: **11 / 11 checks PASSED** (exit code 0) confirming 100% master index parity, zero dangling references, and zero orphan requirements.

---

## 3. Living Documentation Status Parity

| Requirement / Test ID | Component | Target Release | Status | Verification Method |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-PRO-095` | `docs/requirements/req_process_stability.md` | `V5.0.0` | **`[Verified]`** | Unit Test & Governance Verification |
| `TST-PRO-095` | `docs/tests/tst_process_stability.md` | `V5.0.0` | **`[Verified]`** | Automated Unit Tests (`test_jira_accounts.py`) |

* **Master Index Parity**:
  - `docs/requirements.md`: Total=1148, Verified=1134, Implemented=14, Planned=0 (100% parity).
  - `docs/tests.md`: Total=1150, Verified=1149, Implemented=1, Planned=0 (100% parity).

---

## 4. System Invariants Certification
* **Zero Production Runtime Code Changes**: `app/src/main/` is 100% untouched.
* **Human Decision Gate Intact**: Rule 3.1 preserved; sub-tasks end cleanly at `Erledigt`, parent transitions governed.
* **Pure Python Stdlib**: Tooling runs without external third-party pip dependencies.
