# Stage 1 Analysis: ATT-8212 - Establish SUP.1 QA plan and anchor regular weekly dual-vendor Gemini and Claude ASPICE 4.0 cross-audits

**Ticket**: [ATT-8212](https://rainerblind.atlassian.net/browse/ATT-8212)  
**Sub-task**: [ATT-8237](https://rainerblind.atlassian.net/browse/ATT-8237) (`[Analysis]`)  
**Parent Epic**: [ATT-8138](https://rainerblind.atlassian.net/browse/ATT-8138) (*ASPICE 4.0 Audit Remediation 2026-11*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.8`  
**Branch**: `feature/ATT-8212` | **Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-15  

---

## 1. Problem Statement & Motivation
Under ASPICE 4.0 `SUP.1` (Quality Assurance) and `GP 2.1.6`, quality assurance activities must maintain demonstrable independence from work product authors. Previous audits revealed rubber-stamp gate approvals, ineffective QA evaluations without non-conformance follow-ups, and a lack of formal QA planning. While companion ticket `ATT-8213` anchored weekly dual-vendor code reviews, the broader ASPICE 4.0 process quality plan (`SUP.1.BP1-BP6`), independent weekly dual-vendor (Google Gemini + Anthropic Claude) process/evidence cross-audits, and canonical audit evidence storage (`docs/engineering/aspice_audits/`) are not yet fully institutionalized.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **Forensic Investigation**:
  * `docs/management/qa_plan.md` was initiated under `ATT-8213` (46 lines) outlining 5 QA layers, but lacks complete ASPICE `SUP.1.BP1-BP6` governance (escalation paths, corrective actions, communication protocols, and quality records).
  * `docs/engineering/aspice_audits/` does not exist; `ALLOWED_ENGINEERING_ENTRIES` in `tools/verify_requirement_governance.py` and `cm_plan.md` lack `"aspice_audits"`.
  * `tools/jira_util.py` lacks automated cadence scheduling for weekly ASPICE audits (`ensure_weekly_aspice_audit_ticket`).
  * The baseline `2026-11-15` dual-vendor audit (which motivated Epic `ATT-8138`) is not yet stored as versioned repository evidence.
* **Independent Peer Review & Verification**:
  * Verified: `ATT-8213` created `docs/management/qa_plan.md` and `code_reviews/`. `ATT-8212` completes `qa_plan.md` with zero duplication and establishes `aspice_audits/` as the second Layer 5 pillar.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  1. Expand `docs/management/qa_plan.md` to comprehensively cover ASPICE 4.0 `SUP.1.BP1-BP6` and `GP 2.1.6`.
  2. Establish `docs/engineering/aspice_audits/` registered in `cm_plan.md`, containing `README.md` and baseline `2026-11-15_dual_vendor_aspice_audit.md`.
  3. Extend `tools/jira_util.py` (`ensure-weekly-reviews`) to create deduplicated weekly ASPICE audit tickets under `ATT-232` at `rank-top`.
  4. Register `"aspice_audits"` in `ALLOWED_ENGINEERING_ENTRIES` and update release manager pre-release checks.
  5. Define living documentation `REQ-PRO-096` and `TST-PRO-096` with 100% mathematical parity.
* **Out-of-Scope Non-Goals**:
  1. Modifying Android runtime source code in `app/src/main/`.
  2. Duplicating code review mechanisms already implemented in `ATT-8213`.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Net-new requirement `REQ-PRO-096` extending `REQ-PRO-061` and `REQ-PRO-091`.
* **Historical Origin & Commit Trace**: Traced to Pre-Assessment Finding B-09 (`ATT-6149`), F1 auditor independence (`ATT-7558`), and Epic `ATT-8138`.
* **Root Reason for Existing Formulation**: Previous requirements covered in-sprint ticket sampling (`qa-audit`), but lacked project-level QA planning and cross-vendor process cross-auditing.
* **Preservation of Core Invariants**: Retains Rule 3 human gates, pure Python stdlib resilience, and autonomous Phase 2 execution.

---

## 5. Change-Request Impact Analysis (SUP.10)
* **Affected Requirements**: Net-new `REQ-PRO-096` in `docs/requirements/req_process_stability.md`.
* **Affected Architecture Elements**: `docs/management/qa_plan.md`, `docs/management/cm_plan.md`, `tools/jira_util.py`, `tools/verify_requirement_governance.py`, `.agents/rules/aspice_governance.md`, `sprint-planner/SKILL.md`, `release-manager/SKILL.md`, `docs/engineering/aspice_audits/`.
* **Verification Impact & Test Surface**: Net-new `TST-PRO-096` in `docs/tests/tst_process_stability.md`; unit tests in `tools/test_jira_accounts.py` and `tools/test_verify_requirement_governance.py`.
* **Risk Assessment & Invariants**: Low risk. Process governance, documentation, and tooling automation only; zero Android runtime changes.

---

## 6. Architectural Strategy & High-Level Solution
* **Comprehensive QA Plan**: Full `SUP.1.BP1-BP6` plan defining independence, 5-layer verification, corrective action escalation, and quality records.
* **Audit Evidence Store**: Canonical `docs/engineering/aspice_audits/` housing `2026-11-15` baseline and subsequent weekly audit records.
* **Backlog Orchestration**: `ensure_weekly_aspice_audit_ticket()` checks `> 7 days` since latest audit and creates `rank-top` ticket under `ATT-232`.

---

## 7. System Invariants & Risk Assessment
* **Core Invariants**: Zero runtime code impact; Rule 3 human-only gate preserved; Python stdlib resilience.
* **Risk Rating**: **LOW** (process governance and tooling extensions).
