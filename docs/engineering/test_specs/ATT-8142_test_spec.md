# Stage 2: Requirement & Test Specification - ATT-8142: Record skipped Sprint Reviews as NOT PERFORMED

**Ticket**: [ATT-8142](https://rainerblind.atlassian.net/browse/ATT-8142)  
**Parent Epic**: [ATT-8138](https://rainerblind.atlassian.net/browse/ATT-8138) (*ASPICE 4.0 Audit Remediation 2026-11*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.8`  
**Requirement Mapping**: `REQ-PRO-062` (*Independent QA & Cross-Vendor Review Evidence Persistence*)  
**Test Spec ID**: `TST-PRO-062`  
**Branch**: `feature/ATT-8142` | **Author**: AI Agent 2 (Reviewer, `subagent_flash`) | **Date**: 2026-11-16  

---

## 1. Requirement Specification (REQ-PRO-062)
* **Attributes**: Priority: `High` | Type: `Non-Functional` | Verification Method: `Unit / Review`
* **REQ-PRO-062.1 (Truthful Record Schema & Finding K2 Correction)**: Review records SHALL truthfully record metadata, scope, and verdict (`PASS`, `FAIL`, `SKIPPED`, `NOT PERFORMED`, `PARTIAL`). Skipped ceremonies MUST record `SKIPPED` or `NOT PERFORMED` with explicit rationale under a `[PROCESS DEVIATION — Rule 1.3]` header. `docs/engineering/reviews/sprint_2026-46.6.md` SHALL be corrected to `SKIPPED` with an auditable correction note referencing Finding K2.
* **REQ-PRO-062.2 (Non-Defaulting CLI Validation)**: `record-review` in `tools/jira_util.py` (and Höchstmaß mirror) SHALL disallow default `PASS` and fallback positive strings. Missing `--findings` on `PASS`/`FAIL` MUST fail with exit code 1. Skipped verdicts REQUIRE `--findings`. Invalid verdicts MUST fail.
* **REQ-PRO-062.4 (Governance Schema Parity)**: `tools/verify_requirement_governance.py` `--check-reviews` SHALL accept `PASS`, `FAIL`, `ACCEPTED`, `COMPLIANT`, `NOT PERFORMED`, `SKIPPED`, and `PARTIAL`.

---

## 2. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-PRO-062` (`REQ-PRO-062.1`, `REQ-PRO-062.2`, `REQ-PRO-062.4`) in `docs/requirements/req_process_stability.md`.
* **Historical Origin & Commit Trace**: Introduced in Sprint `2026-46.3` via `ATT-6155` under Epic `ATT-6141`; amended in Sprint `2026-46.8` via `ATT-8142` (Finding `K2`).
* **Root Reason for Existing Formulation**: `record-review` was created as an automated helper but included permissive defaults (`verdict="PASS"`, generic success string) to avoid breaking scripts.
* **Preservation of Core Invariants**: Retains review record persistence under `docs/engineering/reviews/` while enforcing truth-in-evidence: records may never claim success without explicit findings; zero Android runtime code changes; Rule 3 Human Gate intact.

---

## 3. Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Non-defaulting & Mandatory Findings)**: *Given* `record-review --verdict PASS` without `--findings`, *When* executed, *Then* the command fails with exit code 1 requiring findings.
* **Criterion 2 (Truthful Skipped Recording)**: *Given* a skipped ceremony with rationale, *When* `record-review --verdict SKIPPED --findings "<rationale>"` runs, *Then* the output record reflects `SKIPPED` and documents a Rule 1.3 deviation without success claims.
* **Criterion 3 (File Correction & Governance)**: *Given* corrected `docs/engineering/reviews/sprint_2026-46.6.md`, *When* `verify_requirement_governance.py --check-reviews` runs, *Then* verification passes with exit code 0.

---

## 4. Test Specification (TST-PRO-062)
* **TST-PRO-062.1 (CLI Validation & Skipped Handling)**: In `tools/test_jira_accounts.py`, assert missing findings for PASS/FAIL raises exit code 1, invalid verdict is rejected, and SKIPPED generates deviation header without default success text.
* **TST-PRO-062.2 (Governance Schema Check)**: In `tools/test_verify_requirement_governance.py`, assert `verify_independent_reviews()` accepts `PASS`, `FAIL`, `SKIPPED`, `NOT PERFORMED`, and `PARTIAL`.
* **TST-PRO-062.3 (Sprint 46.6 Evidence Integrity)**: Verify `docs/engineering/reviews/sprint_2026-46.6.md` contains verdict `SKIPPED` and correction note referencing Finding K2.

---

## 5. Traceability Matrix

| Test Case | Scope | Target File / Class | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-PRO-062.1` | Unit | `tools/jira_util.py` | `REQ-PRO-062.2` | Specified |
| `TST-PRO-062.2` | Unit | `tools/verify_requirement_governance.py` | `REQ-PRO-062.4` | Specified |
| `TST-PRO-062.3` | Review | `docs/engineering/reviews/sprint_2026-46.6.md` | `REQ-PRO-062.1` | Specified |
