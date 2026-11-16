# Stage 3: Implementation Plan - ATT-8142: Record skipped reviews as NOT PERFORMED and correct 2026-46.6 record

**Ticket**: [ATT-8142](https://rainerblind.atlassian.net/browse/ATT-8142) | **Sub-task**: [ATT-8269](https://rainerblind.atlassian.net/browse/ATT-8269) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-8138](https://rainerblind.atlassian.net/browse/ATT-8138) (*ASPICE 4.0 Audit Remediation 2026-11*)  
**Target Release**: `V5.0.0` | **Sprint**: `2026-46.8` | **Branch**: `feature/ATT-8142`  
**Requirement Mapping**: `REQ-PRO-062` (`REQ-PRO-062.1`, `REQ-PRO-062.2`, `REQ-PRO-062.4`)  
**Test Mapping**: `TST-PRO-062` (`TST-PRO-062.1`, `TST-PRO-062.2`, `TST-PRO-062.3`)  
**Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-16  

---

## 1. Problem Description & Background
Under ASPICE 4.0 Critical Finding `K2`, `docs/engineering/reviews/sprint_2026-46.6.md` recorded a skipped Ceremony 2 Sprint Review as `PASS` with fabricated text claiming `"Zero critical ... deviations"` due to a hard-coded default in `tools/jira_util.py` (`record_review()`). Review records must truthfully document reality; QA evidence must never claim ceremonies that did not take place. This plan eliminates permissive defaults in `record-review`, requires explicit `--findings` on `PASS`/`FAIL`, adds support for `SKIPPED`, `NOT PERFORMED`, and `PARTIAL` verdicts with mandatory deviation rationales (Core Rule 1.3), corrects `sprint_2026-46.6.md`, verifies governance checking, and updates unit tests and skills.

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-PRO-062` (*Independent QA & Cross-Vendor Review Evidence Persistence*)
* **Test Mapping**: `TST-PRO-062` (`TST-PRO-062.1`..`TST-PRO-062.3`) in `docs/tests/tst_process_stability.md`

---

## 3. System Invariants & Preserved Behavior
1. **Zero Android Production Runtime Code Changes**: `app/src/main/` is 100% untouched.
2. **Truth-in-Evidence Invariant**: No review record may claim `PASS` without authentic findings.
3. **Pure Python Stdlib Resilience**: Tooling runs without third-party dependencies and supports offline execution.
4. **Parent Human Gate Invariance**: Rule 3 Human Decision Gate remains inviolable.

---

## 4. Proposed Architectural Changes
* **Component 1 (`tools/jira_util.py` & `tools/hoechstmass/tools/jira_util.py`)**:
  - In `format_review_markdown()`: Remove hardcoded default string `"Independent review conducted successfully..."`.
  - In `record_review()` & CLI parser: Validate verdict against `PASS`, `FAIL`, `SKIPPED`, `NOT PERFORMED`, `PARTIAL`.
  - Require `--findings` for `PASS` and `FAIL`; fail with exit code 1 if omitted or empty.
  - Require `--findings` containing rationale for `SKIPPED` / `NOT PERFORMED` and inject `[PROCESS DEVIATION — Rule 1.3]` header.
* **Component 2 (`docs/engineering/reviews/sprint_2026-46.6.md`)**:
  - Correct Verdict from `PASS` to `SKIPPED`.
  - Replace false findings with authentic explanation and formal correction note citing Finding K2.
* **Component 3 (`tools/verify_requirement_governance.py` & `sprint-planner/SKILL.md`)**:
  - Ensure `--check-reviews` validates all supported verdicts and rejects empty/placeholder findings on PASS.
  - Update `sprint-planner/SKILL.md` Ceremony 2 documentation reflecting non-defaulting `record-review`.
* **UI Consistency**: No UI changes.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)
* **Risk Classification**: **STANDARD (Low Risk)** — developer tooling, QA markdown records, and contract unit tests; executed by **`subagent_flash`**.
* **Step 1: Non-Defaulting `record-review` Tooling**: Refactor `record_review()`, `format_review_markdown()`, and CLI parsing in `tools/jira_util.py` and `tools/hoechstmass/tools/jira_util.py`.
* **Step 2: Sprint 46.6 Review Record Correction**: Correct `docs/engineering/reviews/sprint_2026-46.6.md` with finding K2 correction notice.
* **Step 3: Governance Check & Skill Alignment**: Verify `verify_requirement_governance.py --check-reviews` and update `sprint-planner/SKILL.md`.
* **Step 4: Unit Testing & Living Docs Parity**: Author unit tests in `tools/test_jira_accounts.py` and `tools/hoechstmass/tools/test_hoechstmass_tools.py`; update `REQ-PRO-062` / `TST-PRO-062` to `[Implemented]`.
* **Tier 1 Test Command**:
  ```bash
  python3 -m unittest tools/test_jira_accounts.py && python3 tools/verify_requirement_governance.py --check-reviews
  ```

---

## 6. Verification & Rollback Plan
* **Verification**: Execute targeted unit tests in `test_jira_accounts.py` and `verify_requirement_governance.py --check-reviews`.
* **Rollback**: Branch isolation (`feature/ATT-8142`) enables complete rollback without impact on `develop` or active sprint.
