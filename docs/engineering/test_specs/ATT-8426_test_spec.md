# Stage 2: Requirement & Test Specification - ATT-8426: Dual-Vendor Reviews: Model Specification, Claude Protocol & 7-Day Release Gate

**Ticket**: [ATT-8426](https://rainerblind.atlassian.net/browse/ATT-8426)  
**Parent Epic**: [ATT-8138](https://rainerblind.atlassian.net/browse/ATT-8138) (*ASPICE 4.0 Audit Remediation 2026-11*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-11-16_03_Nacht` | **SP**: 2 SP  
**Requirement Mapping**: `REQ-PRO-095` (`REQ-PRO-095.1..4`), `REQ-PRO-097` (`REQ-PRO-097.3`)  
**Test Spec ID**: `TST-PRO-095` (`TST-PRO-095.2..5`), `TST-PRO-097` (`TST-PRO-097.3`)  
**Branch**: `feature/ATT-8426` | **Author**: AI Agent 1 (`subagent_flash`) | **Date**: 2026-11-17  

---

## 1. Requirement Specification (REQ-PRO-095 & REQ-PRO-097)
* **Attributes**: Priority: `Medium` | Type: `Non-Functional` | Verification Method: `Unit / Review`
* **REQ-PRO-095.1 (Generic Top-Model Mandate & Claude Non-Simulation Protocol — [Amended] ATT-8426)**: Policy, templates, `coding_standards.md` §5.12, `release_plan.md` §1.1, and `jira_util.py` review generators SHALL generically mandate the *current top model* of Google Gemini and Anthropic Claude without hardcoding transient version strings. Executed model IDs/versions are recorded dynamically in deliverables and `coverage_matrix.md`. Antigravity agents running on Google models SHALL NEVER simulate/hallucinate Anthropic Claude reviews; missing Claude executions remain explicitly marked `PENDING / EXTERNAL HANDOFF REQUIRED`.
* **REQ-PRO-095.2 (Exhaustive Subsystem Coverage Matrix — [Amended] ATT-8426)**: `coverage_matrix.md` SHALL map all codebase packages, explicitly incorporating previously omitted core packages (`repositories/`, `cloud/`, `onlinecommunities/`, `segments/`, `climbs/`).
* **REQ-PRO-095.3 / REQ-PRO-095.4 (Decoupled 7-Day Review Currency Gate — [Amended] ATT-8426)**: `is_weekly_review_overdue()` SHALL evaluate whether the latest review in `docs/engineering/code_reviews/` is >= 7 days old, strictly decoupling release branch gating from requiring that all matrix subsystems be audited first, preventing a 9-week deadlock while `get_next_review_subsystem()` prioritizes `UNREVIEWED` slices.
* **REQ-PRO-097.3 (Release Plan Synchronization & Staging — [Amended] ATT-8426)**: `release_plan.md` Section 3 SHALL synchronize with Finding K1 / ATT-8141 evidence architecture by referencing `docs/releases/V5.0.0/sys_verification_record.md` (replacing `docs/engineering/field_tests/`) and staging physical on-device verification in pseudo-sprint `Bereit für PO Systemtest` (ID 454).

---

## 2. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Amends `REQ-PRO-095.1..4` and `REQ-PRO-097.3` (`docs/requirements/req_process_stability.md`).
* **Historical Origin & Commit Trace**: Introduced in `ATT-8213` (`dee6f097`) and `ATT-8162` (`366e60b2`), Sprint 2026-46.8.
* **Root Reason for Existing Formulation**: `ATT-8213` enforced whole-codebase audits but coupled slice selection to release gating. Model names were specified for immediate clarity.
* **Preservation of Core Invariants**: ASPICE GP 2.1.6 cross-vendor independence, 7-day review cadence, Finding K1/K2 zero fabrication, PO operational rules (05:00/15:00/22:00 cadence, 2-lane throttle, Stage 6 Pro Sparhebel, weekly Kover coupling), pure Python stdlib offline resilience strictly preserved.

---

## 3. Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Generic Top-Model Specification)**: *Given* `docs/coding_standards.md`, `release_plan.md`, `template_dual_vendor_code_review.md`, and `jira_util.py`, *When* inspected, *Then* they specify generic current top models for Google Gemini and Anthropic Claude without hardcoded version strings.
* **Criterion 2 (Strict Claude Non-Simulation Protocol)**: *Given* an Antigravity agent executing a dual-vendor review ticket without Claude API/CLI access, *When* generating the review artifact, *Then* it records the Claude section as `PENDING / EXTERNAL HANDOFF REQUIRED` without fabricating Claude findings.
* **Criterion 3 (Exhaustive Coverage Matrix Mapping)**: *Given* `docs/engineering/code_reviews/coverage_matrix.md`, *When* inspected, *Then* all core packages (`repositories/`, `cloud/`, `onlinecommunities/`, `segments/`, `climbs/`) are comprehensively mapped into domain slices.
* **Criterion 4 (Decoupled 7-Day Release Currency Gate)**: *Given* at least one completed review in `docs/engineering/code_reviews/` <= 7 days old and remaining `UNREVIEWED` slices, *When* `is_weekly_review_overdue()` executes, *Then* it returns `False`, eliminating the 9-week release deadlock.
* **Criterion 5 (Release Plan Field Test Path & Pseudo-Sprint)**: *Given* `docs/management/release_plan.md` Section 3, *When* inspected, *Then* it references `docs/releases/V5.0.0/sys_verification_record.md` and pseudo-sprint `Bereit für PO Systemtest` (ID 454).

---

## 4. Test Specification (TST-PRO-095 & TST-PRO-097)
* **TST-PRO-095.2 (Extended Coverage Matrix Test)**: In `tools/test_jira_accounts.py`, verify `get_next_review_subsystem()` parses the extended matrix and selects `UNREVIEWED` domains in order.
* **TST-PRO-095.3 (Generic Model Ticket Creation Test)**: In `tools/test_jira_accounts.py`, verify `ensure_weekly_review_ticket()` creates review tickets citing generic top models under `ATT-232` at `rank-top`.
* **TST-PRO-095.4 (Decoupled Currency Gate Unit Test)**: In `tools/test_jira_accounts.py`, verify `is_weekly_review_overdue()` returns `False` when a review is 2 days old even when slices remain `UNREVIEWED`, and returns `True` only when elapsed >= 7 days or zero reviews exist.
* **TST-PRO-097.3 (Release Plan Field Test & Staging Review)**: Verify `release_plan.md` references authentic verification path and pseudo-sprint ID 454.

---

## 5. Traceability Matrix

| Test Case | Scope | Target File / Class | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-PRO-095.2` | Unit | `tools/jira_util.py`, `tools/test_jira_accounts.py` | `REQ-PRO-095.2` | Specified |
| `TST-PRO-095.3` | Unit | `tools/jira_util.py`, `tools/test_jira_accounts.py` | `REQ-PRO-095.1`, `REQ-PRO-095.3` | Specified |
| `TST-PRO-095.4` | Unit | `tools/jira_util.py`, `tools/test_jira_accounts.py` | `REQ-PRO-095.4` | Specified |
| `TST-PRO-097.3` | Review | `docs/management/release_plan.md` | `REQ-PRO-097.3` | Specified |
