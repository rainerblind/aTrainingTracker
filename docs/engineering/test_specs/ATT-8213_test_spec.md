# Stage 2: Requirement & Test Specification - ATT-8213: Mandate and document weekly dual-vendor Gemini and Claude holistic code and architecture reviews

**Ticket**: [ATT-8213](https://rainerblind.atlassian.net/browse/ATT-8213) | **Epic**: [ATT-8138](https://rainerblind.atlassian.net/browse/ATT-8138) (*ASPICE 4.0 Audit Remediation 2026-11*)  
**Target Release**: `V5.0.0` | **Sprint**: `2026-46.8` | **Branch**: `feature/ATT-8213`  
**Requirement Mapping**: `REQ-PRO-095` (`REQ-PRO-095.1`..`REQ-PRO-095.5`) | **Test Spec ID**: `TST-PRO-095` (`TST-PRO-095.1`..`TST-PRO-095.5`)  
**Author**: AI Agent 1 (Implementer, `subagent_flash`) | **Date**: 2026-11-15  

---

## 1. Requirement Specification (REQ-PRO-095)

### 1.1 Problem Statement & Rationale
In-sprint Stage 6 reviews only audit single-ticket diffs in isolation. To systematically audit existing architectural debt, concurrency hazards, resource leaks, and test gaps across the ~200k+ LoC codebase, mandatory weekly dual-vendor reviews (Google Gemini + Anthropic Claude) inspecting entire subsystems alongside delta changes must be governed, tracked via `docs/engineering/code_reviews/coverage_matrix.md`, and automatically scheduled at the top of the Product Backlog (`rank-top`) via `ensure-weekly-reviews`.

### 1.2 Attributes
- **Priority**: Medium | **Type**: Non-Functional / Governance | **Verification Method**: Unit Test / Review

### 1.3 Functional & Governance Requirements
1. `docs/coding_standards.md`, `.agents/rules/aspice_governance.md`, `sprint-planner/SKILL.md`, `release-manager/SKILL.md`, and `cm_plan.md` SHALL mandate weekly dual-vendor holistic reviews by Google Gemini and Anthropic Claude across full subsystem slices (`tracker/`, `banalservice/`, `database/`, `exporter/`, `ui/tracking/`, `ui/aftermath/`, `ui/settings/`, `tools/`, `test/`) plus recent delta commits.
2. `docs/engineering/code_reviews/` SHALL be established as a canonical directory in `cm_plan.md` and `ALLOWED_ENGINEERING_ENTRIES`, containing `coverage_matrix.md` and standard review report template.
3. `tools/jira_util.py` SHALL implement `ensure-weekly-reviews` CLI (`ensure_weekly_review_ticket`), which checks if `> 7 days` have elapsed since the latest completed review or unreviewed subsystems exist; if overdue and no open review ticket exists in Jira, it creates a review ticket under `ATT-232` placed at `rank-top`.
4. `sprint-planner/SKILL.md` (Ceremony 1/2) SHALL automate `ensure-weekly-reviews`, and `release-manager/SKILL.md` SHALL gate production release readiness if weekly reviews are overdue.
5. `tools/test_jira_accounts.py` and `tools/test_verify_requirement_governance.py` SHALL maintain 100% test pass rate for CLI parsing, matrix slice rotation, deduplication, and directory verification.

### 1.4 Acceptance Criteria (Given-When-Then)
* *Given* `docs/coding_standards.md`, `cm_plan.md`, and `aspice_governance.md`, *When* inspected, *Then* the weekly dual-vendor full-subsystem review policy and canonical `code_reviews/` directory are formally specified and registered.
* *Given* `> 7 days` have elapsed since the last review or `UNREVIEWED` subsystems remain in `coverage_matrix.md`, *When* `ensure-weekly-reviews` runs, *Then* a deduplicated ticket targeting the next subsystem is created under `ATT-232` and ranked at `rank-top`.
* *Given* an overdue weekly review, *When* `release-manager` checks release preconditions, *Then* release readiness flags the overdue review.

### 1.5 System Invariants
Zero runtime changes in `app/src/main/`; Rule 3 Human Gate preserved; 100% living doc parity; pure Python standard library offline resilience.

---

## 2. Test Specification (TST-PRO-095)

### Test Case 1: `testCanonicalDirectoryRegistration` (`TST-PRO-095.1`)
* **Scope**: Unit Test (`tools/test_verify_requirement_governance.py`)
* **Expected Result**: `ALLOWED_ENGINEERING_ENTRIES` contains `code_reviews`; `verify_canonical_engineering_entries()` passes with `docs/engineering/code_reviews/`.

### Test Case 2: `testSubsystemCoverageMatrixParsingAndRotation` (`TST-PRO-095.2`)
* **Scope**: Unit Test (`tools/test_jira_accounts.py`)
* **Expected Result**: `get_next_review_subsystem()` parses `coverage_matrix.md` and deterministically selects the next unreviewed or oldest audited subsystem slice.

### Test Case 3: `testWeeklyReviewTicketCreationAndDeduplication` (`TST-PRO-095.3`)
* **Scope**: Unit Test (`tools/test_jira_accounts.py`)
* **Expected Result**: `ensure_weekly_review_ticket()` creates a ticket under `ATT-232` with `rank_top=True` when overdue; skips creation when an active review ticket already exists.

### Test Case 4: `testGovernanceDocumentationAndReleaseGate` (`TST-PRO-095.4`)
* **Scope**: Review / Process Verification
* **Expected Result**: Policy anchored in `docs/coding_standards.md`, `aspice_governance.md`, `sprint-planner/SKILL.md`, and `release-manager/SKILL.md`.

### Test Case 5: `testLivingDocumentationParity` (`TST-PRO-095.5`)
* **Scope**: Automated Governance Verification (`tools/verify_requirement_governance.py`)
* **Expected Result**: 100% parity across `docs/requirements.md` and `docs/tests.md` with zero dangling IDs.

---

## 3. Traceability Matrix

| Test Case | Scope | Component Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-PRO-095.1` | Governance | `verify_requirement_governance.py` | `REQ-PRO-095.2` | Specified |
| `TST-PRO-095.2` | Tooling | `jira_util.py:get_next_review_subsystem` | `REQ-PRO-095.2`, `REQ-PRO-095.3` | Specified |
| `TST-PRO-095.3` | Orchestration | `jira_util.py:ensure_weekly_review_ticket` | `REQ-PRO-095.3` | Specified |
| `TST-PRO-095.4` | Process | Governance standards & Release gate | `REQ-PRO-095.1`, `REQ-PRO-095.4` | Specified |
| `TST-PRO-095.5` | Traceability | `verify_requirement_governance.py` | `REQ-PRO-095.5` | Specified |
