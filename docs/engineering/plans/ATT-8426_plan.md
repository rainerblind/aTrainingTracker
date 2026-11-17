# Stage 3: Implementation Plan - ATT-8426: Dual-Vendor Reviews: Model Specification, Claude Protocol & 7-Day Release Gate

**Ticket**: [ATT-8426](https://rainerblind.atlassian.net/browse/ATT-8426)  
**Parent Epic**: [ATT-8138](https://rainerblind.atlassian.net/browse/ATT-8138) (*ASPICE 4.0 Audit Remediation 2026-11*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-11-16_03_Nacht` | **SP**: 2 SP  
**Requirement Mapping**: `REQ-PRO-095` (`REQ-PRO-095.1..4`), `REQ-PRO-097` (`REQ-PRO-097.3`)  
**Test Mapping**: `TST-PRO-095` (`TST-PRO-095.2..5`), `TST-PRO-097` (`TST-PRO-097.3`)  
**Branch**: `feature/ATT-8426` | **Author**: AI Agent 1 (`subagent_pro`) | **Date**: 2026-11-17  

---

## 1. Problem Description & Architectural Context
Sprint reviews of ATT-8213 / ATT-8162 identified four governance and tooling defects:
1. Hardcoded model identifiers (`Gemini 2.5 Pro / Gemini 4 Carbon`, `Claude 3.7 Sonnet`) in review generation and policy docs.
2. Lack of a formal Claude non-simulation execution protocol following ATT-4290 API retirement, risking synthetic reviews (Finding K2).
3. 9-week release preparation deadlock in `is_weekly_review_overdue()` due to early return on `status == "UNREVIEWED"`.
4. Omitted core packages (`repositories/`, `cloud/`, `onlinecommunities/`, `segments/`, `climbs/`) in `coverage_matrix.md`, and deprecated field test path in `release_plan.md` Section 3.

---

## 2. Traceability & Requirements Mapping
* **`REQ-PRO-095.1` $\to$ `TST-PRO-095.3`, `TST-PRO-095.5`**: Mandate generic current top models across review tickets, policy, and templates; enforce Claude non-simulation with `PENDING / EXTERNAL HANDOFF REQUIRED`.
* **`REQ-PRO-095.2` $\to$ `TST-PRO-095.2`**: Exhaustive `coverage_matrix.md` package mapping for all production components.
* **`REQ-PRO-095.3`, `REQ-PRO-095.4` $\to$ `TST-PRO-095.4`**: Decouple `is_weekly_review_overdue()` from `UNREVIEWED` status; retain slice selection priority in `get_next_review_subsystem()`.
* **`REQ-PRO-097.3` $\to$ `TST-PRO-097.3`**: Update `release_plan.md` Section 3 to `docs/releases/V5.0.0/sys_verification_record.md` and pseudo-sprint ID 454 (`Bereit für PO Systemtest`).

---

## 3. System Invariants & Preserved Behavior
1. **Cross-Vendor Independence (`GP 2.1.6`)**: Two distinct vendor evaluations preserved without single-vendor bias.
2. **Finding K1/K2 Zero-Fabrication Mandate**: Antigravity agents NEVER simulate or hallucinate Claude findings.
3. **Zero Production Code Edits**: Zero edits to `app/src/main/` Android runtime code.
4. **PO Rule Invariants**: Sprints (`05:00/15:00/22:00`), 2-lane throttle + Stage 6 Pro Sparhebel, and weekly Kover coupling strictly preserved.
5. **Pure Python Stdlib**: Offline CLI resilience without external pip packages.

---

## 4. Step-by-Step Implementation Sequence (Stage 4 Construction)
* **Step 1 (`REQ-PRO-095.3`, `REQ-PRO-095.4` / `tools/jira_util.py`)**:
  - Remove `if status == "UNREVIEWED": return True` from `is_weekly_review_overdue()`.
  - Evaluate overdue strictly against `latest_date` (overdue if `(now - latest_date).days >= days_threshold` or zero reviews exist).
  - Update `ensure_weekly_review_ticket()` and `ensure_weekly_aspice_audit_ticket()` to cite generic 'current top model' of Google Gemini and Anthropic Claude, and document the Claude non-simulation handoff protocol.
* **Step 2 (`REQ-PRO-095.1`, `REQ-PRO-095.2` / `docs/engineering/code_reviews/`)**:
  - Update `coverage_matrix.md` header to generic top-model mandate; insert missing package domain slices (`repositories/`, `cloud/`, `segments/`, `onlinecommunities/`, `climbs/`).
  - In `template_dual_vendor_code_review.md`, update model headers to generic placeholders and add the Claude Non-Simulation Protocol section with explicit `PENDING / EXTERNAL HANDOFF REQUIRED` state.
* **Step 3 (`REQ-PRO-095.1`, `REQ-PRO-097.3` / Governance & Release Docs)**:
  - Update `docs/coding_standards.md` §5.12 to reference generic current top models.
  - Update `docs/management/release_plan.md` Section 1.1 (generic models) and Section 3 (reference `docs/releases/V5.0.0/sys_verification_record.md` and pseudo-sprint `Bereit für PO Systemtest` ID 454).
  - Align `release-manager/SKILL.md` currency check description to decoupled 7-day cadence.
* **Step 4 (`TST-PRO-095.2..5` / `tools/test_jira_accounts.py`)**:
  - Update `test_is_weekly_review_overdue`: assert `False` when review is 2 days old even when `status == "UNREVIEWED"`; assert `True` when elapsed >= 7 days or zero reviews exist.
  - Add test verifying `get_next_review_subsystem()` parses the extended package matrix and selects `UNREVIEWED` slices in order.
  - Update `test_ensure_weekly_review_creates_and_deduplicates` asserting generic model description and rank-top placement.
* **Step 5 (Verification & Governance Parity)**:
  - Run `python3 -m unittest tools/test_jira_accounts.py`.
  - Run `python3 tools/verify_requirement_governance.py`.

---

## 5. Verification & Rollback Plan
* **Verification**: 100% test pass on `tools/test_jira_accounts.py` and exit code 0 on `tools/verify_requirement_governance.py`.
* **Rollback**: `git checkout feature/ATT-8426 -- tools/ docs/` cleanly reverts modifications.
