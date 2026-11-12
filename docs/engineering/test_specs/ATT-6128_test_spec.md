# Stage 2: Requirement & Test Specification - ATT-6128: Backfill test evidence for Aftermath Reviews requirements

**Ticket**: [ATT-6128](https://rainerblind.atlassian.net/browse/ATT-6128)  
**Sub-task**: Pending (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-6119](https://rainerblind.atlassian.net/browse/ATT-6119) (*ASPICE CL2 – Evidence Integrity & Bidirectional Traceability*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-46.4`  
**Requirement Mapping**: `REQ-PRO-079` (*Aftermath Reviews Test Evidence Backfill & Bidirectional Traceability Governance*)  
**Test Spec ID**: `TST-PRO-079`  
**Branch**: `subagent-Lane-3-Agent---ATT-6128-self-3dc7c28c`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-12  

---

## 1. Requirement Specification (REQ-PRO-079)

### 1.1 Problem Statement & Rationale
Under ASPICE SWE.6 BP4, SWE.1 BP5, and Remediation A-08.2 (ATT-6128), all requirements in `docs/requirements/req_aftermath_reviews.md` must link to valid, existing test specifications in `docs/tests/tst_aftermath_reviews.md` with verified test code implementations in `app/src/test/` or be downgraded to `[Implemented]`. Bidirectional reference integrity must guarantee that every referenced TST cites all of its parent REQs back with zero dangling references.

### 1.2 Attributes
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit / Review

### 1.3 Functional & Architectural Requirements
1. *Complete TST Coverage & Evidence Verification (`REQ-PRO-079.1`)*:
   • Every requirement in `docs/requirements/req_aftermath_reviews.md` SHALL cite one or more valid, existing backticked `TST-*` IDs in its `Tests:` field.
   • All 12 `REQ-REV-*` requirements SHALL retain `[Verified]` status backed by automated unit, contract, or architecture tests in `app/src/test/`.
2. *Bidirectional Citation Integrity (`REQ-PRO-079.2`)*:
   • Every test specification in `docs/tests/tst_aftermath_reviews.md` SHALL be cited back by 100% of the requirements it declares in its `Requirement(s):` field (`REQ-REV-002` SHALL cite `TST-REV-005` in addition to `TST-REV-002`).
   • Zero dangling references SHALL exist within the `aftermath_reviews` domain.
3. *Test Code Tag Synchronization (`REQ-PRO-079.3`)*:
   • All 13 test specifications in `docs/tests/tst_aftermath_reviews.md` SHALL have corresponding test code tags recognized by `verify_requirement_governance.py --coverage`, including `TST-REV-009` tagged in `ZoneDistributionCardTest.kt`.
4. *Attribute Compliance & Header Parity (`REQ-PRO-079.4`)*:
   • All requirements in `docs/requirements/req_aftermath_reviews.md` SHALL declare valid `Priority`, `Type`, and `Verification Method` attributes per `REQ-PRO-077`.
   • Domain shard headers SHALL reflect accurate totals (`Total Requirements: 12`, `Total Tests: 13`) in 100% parity with master index tables.

### 1.4 Acceptance Criteria (Given-When-Then)
* **Criterion 1**:
  * *Given* `docs/requirements/req_aftermath_reviews.md`,
  * *When* evaluated via `python3 tools/verify_requirement_governance.py --coverage`,
  * *Then* 100% of requirements cite valid TSTs, 100% of TSTs have test code tags, and 100% of TSTs are cited back by all referenced REQs.
* **Criterion 2**:
  * *Given* `docs/requirements/req_aftermath_reviews.md` and `docs/tests/tst_aftermath_reviews.md`,
  * *When* checked for dangling references and attributes,
  * *Then* zero dangling references and zero missing attributes are detected.
* **Criterion 3**:
  * *Given* `tools/verify_requirement_governance.py --sync-indexes`,
  * *When* executed,
  * *Then* master index tables achieve 100% mathematical parity.

### 1.5 System Invariants
1. Pure Python 3 standard library governance execution; zero Android runtime production code changes.
2. Master table mathematical parity across all 9 domain shards remains 100% green.
3. All existing test suites continue to pass with 0 failures.

---

## 2. Test Specification (TST-PRO-079)

### Test Case 1: Coverage & Bidirectional Traceability Audit (`TST-PRO-079.1`)
* **Scope**: Governance Verification
* **Target File**: `tools/verify_requirement_governance.py`
* **Preconditions**: `req_aftermath_reviews.md`, `tst_aftermath_reviews.md`, and `ZoneDistributionCardTest.kt` updated.
* **Action**: `python3 tools/verify_requirement_governance.py --coverage`
* **Expected Result**: 0 gaps in Category 1 for REV (100% REQs with TST), 0 gaps in Category 2 for TST-REV (100% TSTs with code), 0 gaps in Category 3 for TST-REV, and 0 gaps in Category 4 for TST-REV (100% bidirectional citation).

### Test Case 2: Link Integrity, Evidence Status & Attribute Audit (`TST-PRO-079.2`)
* **Scope**: Governance Verification
* **Target File**: `tools/verify_requirement_governance.py`
* **Preconditions**: Shards updated with attributes and bidirectional links.
* **Action**:
  - `python3 tools/verify_requirement_governance.py --check-links`
  - `python3 tools/verify_requirement_governance.py --check-evidence`
  - `python3 tools/verify_requirement_governance.py --check-attributes`
* **Expected Result**: Zero dangling references, zero ungrounded Verified statuses, and zero missing attributes in `aftermath_reviews`.

### Test Case 3: Targeted Unit Test Execution (`TST-PRO-079.3`)
* **Command**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.aftermath.zones.ZoneDistributionCardTest"`
* **Goal**: Verify 100% pass rate across updated test suite.

---

## 3. Traceability Matrix

| Requirement | Description | Test Specification | Verification Status |
| :--- | :--- | :--- | :--- |
| `REQ-PRO-079.1` | Complete TST Coverage & Evidence Verification | `TST-PRO-079.1` | Planned |
| `REQ-PRO-079.2` | Bidirectional Citation Integrity | `TST-PRO-079.1`, `TST-PRO-079.2` | Planned |
| `REQ-PRO-079.3` | Test Code Tag Synchronization | `TST-PRO-079.1`, `TST-PRO-079.3` | Planned |
| `REQ-PRO-079.4` | Attribute Compliance & Header Parity | `TST-PRO-079.2` | Planned |
