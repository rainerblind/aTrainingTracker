# Stage 2: Requirement & Test Specification - ATT-8212: Establish SUP.1 QA plan and anchor regular weekly dual-vendor Gemini and Claude ASPICE 4.0 cross-audits

**Ticket**: [ATT-8212](https://rainerblind.atlassian.net/browse/ATT-8212) | **Epic**: [ATT-8138](https://rainerblind.atlassian.net/browse/ATT-8138) (*ASPICE 4.0 Audit Remediation 2026-11*)  
**Target Release**: `V5.0.0` | **Sprint**: `2026-46.8` | **Branch**: `feature/ATT-8212`  
**Requirement Mapping**: `REQ-PRO-096` (`REQ-PRO-096.1`..`REQ-PRO-096.5`) | **Test Spec ID**: `TST-PRO-096` (`TST-PRO-096.1`..`TST-PRO-096.5`)  
**Author**: AI Agent 1 (Implementer, `subagent_flash`) | **Date**: 2026-11-15  

---

## 1. Requirement Specification (REQ-PRO-096)

### 1.1 Problem Statement & Rationale
Under ASPICE 4.0 `SUP.1` (Quality Assurance) and `GP 2.1.6`, quality assurance activities must maintain demonstrable independence from work product authors. While companion ticket `ATT-8213` anchored weekly dual-vendor code reviews, the overarching ASPICE 4.0 process quality plan (`SUP.1.BP1-BP6`), regular weekly dual-vendor (Google Gemini + Anthropic Claude) ASPICE process/evidence cross-audits, canonical audit evidence storage (`docs/engineering/aspice_audits/`), and automated backlog scheduling are required to guarantee independent quality governance.

### 1.2 Attributes
- **Priority**: Medium | **Type**: Non-Functional / Governance | **Verification Method**: Unit Test / Review

### 1.3 Functional & Governance Requirements
1. `docs/management/qa_plan.md` SHALL define the comprehensive ASPICE 4.0 `SUP.1` strategy covering `BP1` (strategy), `BP2` (criteria), `BP3` (work product assurance), `BP4` (process assurance), `BP5` (escalation & resolution), and `BP6` (records), anchoring cross-vendor independence (`GP 2.1.6`) across 5 verification layers.
2. `docs/engineering/aspice_audits/` SHALL be established as a canonical directory in `cm_plan.md` and `verify_requirement_governance.py` (`ALLOWED_ENGINEERING_ENTRIES`), storing `README.md` and versioned audit records (`YYYY-MM-DD_dual_vendor_aspice_audit.md`), starting with the `2026-11-15` baseline audit for Epic `ATT-8138`.
3. `tools/jira_util.py` SHALL implement `ensure_weekly_aspice_audit_ticket()` (`ensure-weekly-reviews` CLI), which verifies whether `> 7 days` have elapsed since the latest completed ASPICE audit in `docs/engineering/aspice_audits/`; if overdue and no open weekly ASPICE audit ticket exists, it SHALL automatically create a review ticket under Epic `ATT-232` placed at `rank-top`.
4. `sprint-planner/SKILL.md` (Ceremonies 1 & 2) and `release-manager/SKILL.md` SHALL verify that weekly ASPICE cross-audits and code reviews are current ($\le 7$ days) prior to closing sprint reviews or initiating production releases.
5. `tools/test_jira_accounts.py` and `tools/test_verify_requirement_governance.py` SHALL maintain 100% test pass rate for `aspice_audits` registration, currency checks, and deduplicated ticket creation.

### 1.4 Acceptance Criteria (Given-When-Then)
* *Given* `docs/management/qa_plan.md`, *When* inspected, *Then* it specifies all 5 QA layers, independence guarantees, escalation paths, and weekly dual-vendor ASPICE cross-audits.
* *Given* `docs/engineering/aspice_audits/` and `cm_plan.md`, *When* inspected, *Then* `aspice_audits` is registered in `cm_plan.md` and contains the `2026-11-15` baseline record referencing `ATT-8138`.
* *Given* `> 7 days` have elapsed since the last ASPICE audit, *When* `ensure-weekly-reviews` runs, *Then* a deduplicated Weekly Dual-Vendor ASPICE Audit ticket is created under `ATT-232` at `rank-top`.

### 1.5 System Invariants
Zero runtime changes in `app/src/main/`; Rule 3 Human Gate preserved; 100% living doc parity; pure Python standard library offline resilience.

---

## 2. Test Specification (TST-PRO-096)

### Test Case 1: `testCanonicalAspiceAuditsDirectoryRegistration` (`TST-PRO-096.1`)
* **Scope**: Unit Test (`tools/test_verify_requirement_governance.py`)
* **Expected Result**: `ALLOWED_ENGINEERING_ENTRIES` contains `aspice_audits`; `verify_canonical_engineering_entries()` passes with `docs/engineering/aspice_audits/`.

### Test Case 2: `testAspiceAuditCurrencyCheck` (`TST-PRO-096.2`)
* **Scope**: Unit Test (`tools/test_jira_accounts.py`)
* **Expected Result**: `is_weekly_aspice_audit_overdue()` evaluates elapsed days against records in `docs/engineering/aspice_audits/` accurately.

### Test Case 3: `testWeeklyAspiceAuditTicketCreationAndDeduplication` (`TST-PRO-096.3`)
* **Scope**: Unit Test (`tools/test_jira_accounts.py`)
* **Expected Result**: `ensure_weekly_aspice_audit_ticket()` creates an audit ticket under `ATT-232` with `rank_top=True` when overdue; reuses open ticket idempotently without creating duplicates.

### Test Case 4: `testQaPlanGovernanceAndReleaseGate` (`TST-PRO-096.4`)
* **Scope**: Review / Process Verification
* **Expected Result**: QA strategy anchored across `docs/management/qa_plan.md`, `cm_plan.md`, `sprint-planner/SKILL.md`, and `release-manager/SKILL.md`.

### Test Case 5: `testLivingDocumentationParity` (`TST-PRO-096.5`)
* **Scope**: Automated Governance Verification (`tools/verify_requirement_governance.py`)
* **Expected Result**: 100% parity across `docs/requirements.md` and `docs/tests.md` with zero dangling IDs.

---

## 3. Traceability Matrix

| Test Case | Scope | Component Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-PRO-096.1` | Governance | `verify_requirement_governance.py` | `REQ-PRO-096.2` | Specified |
| `TST-PRO-096.2` | Tooling | `jira_util.py:is_weekly_aspice_audit_overdue` | `REQ-PRO-096.3` | Specified |
| `TST-PRO-096.3` | Orchestration | `jira_util.py:ensure_weekly_aspice_audit_ticket` | `REQ-PRO-096.3` | Specified |
| `TST-PRO-096.4` | Process | QA Plan & Release Gate | `REQ-PRO-096.1`, `REQ-PRO-096.4` | Specified |
| `TST-PRO-096.5` | Traceability | `verify_requirement_governance.py` | `REQ-PRO-096.5` | Specified |
