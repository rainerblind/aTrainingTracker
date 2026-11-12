# Stage 2: Requirement & Test Specification - ATT-6155: Record cross-vendor independent reviews as QA evidence

**Ticket**: [ATT-6155](https://rainerblind.atlassian.net/browse/ATT-6155)  
**Sub-task**: `[Req & Test Spec]` (Created on submission)  
**Parent Epic**: [ATT-6141](https://rainerblind.atlassian.net/browse/ATT-6141) (*ASPICE CL2 – Role Integrity, Independent QA & Configuration Management*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `sprint/2026-46.3`  
**Requirement Mapping**: `REQ-PRO-062` (*Independent QA & Cross-Vendor Review Evidence Persistence*)  
**Test Spec ID**: `TST-PRO-062`  
**Branch**: `feature/ATT-6155`  
**Author**: AI Agent 1 (Implementer, `subagent_flash`)  
**Date**: 2026-11-12  

---

## 1. Requirement Specification (`REQ-PRO-062`)

### 1.1 Problem Statement & Rationale
Under ASPICE SUP.1 (Quality Assurance) and GP 2.2 (Work Product Evidence), QA activities must demonstrate adequate independence from the work product author (Finding B-10). While in-sprint agents operate on the same vendor framework, independent reviews conducted by the Human (during Ceremony 2 Sprint Review before `Erledigt`) and cross-vendor AI models (Layer 5) previously lacked persistent repository records. This requirement establishes a structured review evidence format and CLI tooling in `docs/engineering/reviews/`.

### 1.2 Functional & Architectural Requirements
1. **Review Record Schema (`REQ-PRO-062.1`)**: Deliverables in `docs/engineering/reviews/` SHALL record traceable metadata: Reviewer (`Human` or external AI vendor), Model/Version (if AI), Date/Timestamp, Reviewed Ticket(s) or Commit(s), Scope, and Findings / Verdict (`PASS` / `FAIL`).
2. **Independent Review CLI Helper (`REQ-PRO-062.2`)**: Tooling in `tools/jira_util.py` and `tools/hoechstmass/tools/jira_util.py` SHALL implement `record-review` supporting both Human Sprint Reviews and Cross-Vendor AI Reviews with parameters `--sprint`, `--ticket`, `--reviewer`, `--vendor`, `--model`, `--version`, `--scope`, `--verdict`, and `--findings`.
3. **Directory Governance (`REQ-PRO-062.3`)**: `ALLOWED_ENGINEERING_ENTRIES` in `tools/verify_requirement_governance.py` and `hoechstmass_config.json` SHALL register `"reviews"`.
4. **Governance Enforcement CLI (`REQ-PRO-062.4`)**: `tools/verify_requirement_governance.py` and Höchstmaß mirror SHALL implement `--check-reviews [SPRINT]`, validating existence and schema compliance of review records.
5. **Autonomous Execution Decoupling (`REQ-PRO-062.5`)**: Autonomous Phase 2 lifecycle execution (`Stage 5 -> Final Review`) SHALL NOT be blocked by missing human review records, preserving automated in-sprint throughput.
6. **Ceremony 2 Review Mandate (`REQ-PRO-062.6`)**: `sprint-planner/SKILL.md` Ceremony 2 SHALL document recording human review acceptance into `docs/engineering/reviews/` prior to sprint closure.

### 1.3 Requirement Archaeology & Chesterton's Fence Audit
* `Original Requirement ID & Target`: Net-new requirement `REQ-PRO-062` (`REQ-PRO-062.1`–`REQ-PRO-062.6`). Targets `tools/jira_util.py`, `tools/verify_requirement_governance.py`, `docs/engineering/reviews/`, and `tools/hoechstmass/`.
* `Historical Origin & Commit Trace`: Introduced in Sprint 2026-46.3 via ATT-6155 under Epic ATT-6141 to satisfy ASPICE pre-assessment finding B-10.
* `Root Reason for Existing Formulation`: Gaps in independent QA evidence under ASPICE SUP.1; lack of repository records for independent human and cross-vendor reviews.
* `Preservation of Core Invariants`: Zero changes to Android runtime code; pure Python 3 standard library; autonomous Phase 2 execution strictly preserved.

### 1.4 Acceptance Criteria (Given-When-Then)
* **AC-1 (Review Record Generation)**:
  * *Given* a completed Ceremony 2 or cross-vendor AI review,
  * *When* `record-review` is executed,
  * *Then* it generates a markdown file in `docs/engineering/reviews/` with traceable reviewer metadata, scope, and verdict.
* **AC-2 (Review Governance Verification)**:
  * *Given* a review file in `docs/engineering/reviews/`,
  * *When* `verify_requirement_governance.py --check-reviews` runs,
  * *Then* it verifies that all required metadata fields are present and valid.
* **AC-3 (Autonomous Phase 2 Decoupling)**:
  * *Given* a ticket progressing through Stages 1..5 in Phase 2,
  * *When* transitioning to `Final Review (Agent)`,
  * *Then* the transition is unblocked regardless of whether a human review record exists.

---

## 2. Test Specification (`TST-PRO-062`)

### Test Case 1: record-review CLI Command & Report Generation (`TST-PRO-062.1`)
* **Scope**: Unit Test (`tools/test_jira_accounts.py`)
* **Action**: Execute `record-review` for Human Ceremony 2 review and Cross-Vendor AI review; verify structured markdown output and metadata serialization.

### Test Case 2: Independent Review Governance Verification (`TST-PRO-062.2`)
* **Scope**: Unit Test (`tools/test_verify_requirement_governance.py`)
* **Action**: Run `verify_independent_reviews` against valid vs missing and malformed review records; assert pass/fail status and schema diagnostics.

### Test Case 3: Counterpart Parity & Tooling Integration (`TST-PRO-062.3`)
* **Scope**: Unit Test (`tools/hoechstmass/tools/test_hoechstmass_tools.py`)
* **Action**: Assert `record-review` and `verify_independent_reviews` in Höchstmaß mirror match root tooling behavior.

### Test Case 4: Canonical Directory Registration (`TST-PRO-062.4`)
* **Scope**: Unit Test (`tools/test_verify_requirement_governance.py`)
* **Action**: Assert `"reviews"` exists in `ALLOWED_ENGINEERING_ENTRIES` and `hoechstmass_config.json`.

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-PRO-062.1` | Unit | `record_review` | `REQ-PRO-062.1`, `REQ-PRO-062.2` | Specified |
| `TST-PRO-062.2` | Unit | `verify_independent_reviews` | `REQ-PRO-062.4` | Specified |
| `TST-PRO-062.3` | Unit | `record_review`, `verify_independent_reviews` | `REQ-PRO-062.1`, `REQ-PRO-062.4` | Specified |
| `TST-PRO-062.4` | Unit | `verify_engineering_directory_structure` | `REQ-PRO-062.3` | Specified |
