# Stage 1 Analysis: ATT-8426 - Dual-Vendor Reviews: Model Specification, Claude Protocol & 7-Day Release Gate

**Ticket**: [ATT-8426](https://rainerblind.atlassian.net/browse/ATT-8426)  
**Parent Epic**: [ATT-8138](https://rainerblind.atlassian.net/browse/ATT-8138) (*ASPICE 4.0 Audit Remediation 2026-11*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-11-16_03_Nacht` | **SP**: 2 SP  
**Branch**: `feature/ATT-8426` | **Author**: AI Agent 1 (Implementer, `subagent_flash`) | **Date**: 2026-11-17  

---

## 1. Problem Statement & Motivation
Reviews of ATT-8213 and ATT-8162 (Sprint 2026-46.8) revealed four governance and tooling defects:
1. **Hardcoded Model Names**: `tools/jira_util.py` (L3550, L3644), `coverage_matrix.md`, `template_dual_vendor_code_review.md`, `coding_standards.md` §5.12, and `release_plan.md` §1.1 hardcode transient model strings (`Gemini 2.5 Pro / Gemini 4 Carbon`, `Claude 3.7 Sonnet`).
2. **Missing Claude Protocol**: Native Claude API calls were retired in ATT-4290 (`tools/review_agent.py`). Gemini-based Antigravity agents cannot call Claude autonomously, risking simulated/hallucinated reviews violating Finding K2 (evidence integrity).
3. **9-Week Release Deadlock in `is_weekly_review_overdue()`**: `is_weekly_review_overdue()` returns `True` whenever any subsystem is `UNREVIEWED`, blocking `release-manager` until all 9 subsystems are audited (9 weeks). Core packages (`repositories/`, `cloud/`, `segments/`, `climbs/`) are also omitted from `coverage_matrix.md`.
4. **Obsolete Field Test Path in `release_plan.md`**: Section 3 cites deprecated `docs/engineering/field_tests/` instead of `docs/releases/V5.0.0/sys_verification_record.md` and lacks reference to pseudo-sprint `Bereit für PO Systemtest` (ID 454).

---

## 2. Root Cause Analysis & Independent Peer Review
* **RCA 1 (Model Drift)**: Explicit model IDs were embedded during initial drafting instead of generic top-model requirements.
* **RCA 2 (Missing Handoff Protocol)**: Retiring `call_claude_api` in ATT-4290 lacked a formal cross-vendor handoff procedure.
* **RCA 3 (Conflated Selection vs. Gate)**: `is_weekly_review_overdue()` coupled subsystem slice selection with the 7-day cadence check.
* **Independent Peer Review & Verification (ATT-3534)**:
  * Audited `tools/jira_util.py` L3490–3492: Confirmed `if status == "UNREVIEWED": return True` causes perpetual release deadlock.
  * Audited `coverage_matrix.md`: Confirmed core packages (`repositories/`, `cloud/`, `segments/`, `climbs/`) are missing from domain table.
  * Audited `release-manager/SKILL.md` L89: Confirmed release preparation strictly fails when `is_weekly_review_overdue()` returns `True`.
  * Audited `release_plan.md` L56: Confirmed obsolete `docs/engineering/field_tests/` path reference.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  1. Mandate generic 'current top model' across governance and tooling; record concrete models dynamically in review deliverables.
  2. Define an explicit Claude cross-vendor handoff protocol in review templates (strict non-simulation; honest pending markers).
  3. Decouple `is_weekly_review_overdue()` from `UNREVIEWED` status to check authentic 7-day review cadence; map missing packages.
  4. Update `release_plan.md` Section 3 to reference `docs/releases/V5.0.0/sys_verification_record.md` and pseudo-sprint ID 454.
* **Out-of-Scope Non-Goals**:
  - Disturbing PO rule updates (`05:00/15:00/22:00` cadence, 2-lane throttle + Stage 6 Pro Sparhebel, weekly Kover coupling).
  - Modifying Android production runtime code in `app/src/main/`.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Amends `REQ-PRO-095` (.1, .2, .3, .4) and `REQ-PRO-097` (.3) in `docs/requirements/req_process_stability.md`.
* **Historical Origin & Commit Trace**: Introduced in `ATT-8213` (`dee6f097`) and `ATT-8162` (`366e60b2`), Sprint 2026-46.8.
* **Root Reason for Existing Formulation**: `ATT-8213` enforced whole-codebase audits but coupled slice selection to release gating. Model names were specified for immediate clarity.
* **Preservation of Core Invariants**: ASPICE cross-vendor independence (`GP 2.1.6`), 7-day review cadence, zero-fabrication evidence integrity (Findings K1/K2), and pure Python stdlib offline execution strictly preserved.

---

## 5. Change-Request Impact Analysis (SUP.10)
* **Affected Requirements**: Amends `REQ-PRO-095` and `REQ-PRO-097`; test specs `TST-PRO-095` and `TST-PRO-097`.
* **Affected Architecture Elements**: `tools/jira_util.py`, `coverage_matrix.md`, `template_dual_vendor_code_review.md`, `coding_standards.md`, `release_plan.md`, `test_jira_accounts.py`.
* **Verification Impact & Test Surface**: Unit tests in `test_jira_accounts.py`, `tools/verify_requirement_governance.py`.
* **Risk Assessment & Invariants**: **LOW** (2 SP tooling & governance refinement; zero Android production runtime impact).

---

## 6. Architectural Strategy & High-Level Solution
* **Generic Model Policy**: Require 'current top model' (Google Gemini + Anthropic Claude) in policy; capture actual model/version in reports.
* **Honest Execution Protocol**: Mandate explicit Claude CLI / human handoff; prohibit Gemini agents from faking Claude reviews.
* **Cadence Gate Decoupling**: Check `latest_date` <= 7 days in `is_weekly_review_overdue()` without early exit on `UNREVIEWED` slices.
* **Exhaustive Package Matrix**: Group unmapped packages into coherent domain slices (`repositories/`, `cloud/`, `segments/`).

---

## 7. System Invariants & Risk Assessment
* **Core Invariants**: (1) `app/src/main/` untouched; (2) Evidence integrity preserved; (3) PO operational rules preserved.
* **Risk Rating**: **LOW** (2 SP governance & CLI tooling defect fix).
