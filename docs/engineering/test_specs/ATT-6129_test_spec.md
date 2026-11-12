# Stage 2: Requirement & Test Specification - ATT-6129: Backfill test evidence for Aftermath Periods requirements

**Ticket**: [ATT-6129](https://rainerblind.atlassian.net/browse/ATT-6129)  
**Sub-task**: Pending (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-6119](https://rainerblind.atlassian.net/browse/ATT-6119) (*ASPICE CL2 – Evidence Integrity & Bidirectional Traceability*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-46.4`  
**Requirement Mapping**: `REQ-PRO-080` (*Aftermath Periods Test Evidence Backfill & Bidirectional Traceability Governance*)  
**Test Spec ID**: `TST-PRO-080`  
**Branch**: `subagent-Lane-2-Agent---ATT-6129-self-43cd1c63`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-12  

---

## 1. Requirement Specification (REQ-PRO-080)

### 1.1 Problem Statement & Rationale
Under ASPICE SWE.6 BP4, SWE.1 BP5, and Remediation A-08.3 (ATT-6129), all requirements in `docs/requirements/req_aftermath_periods.md` must link to valid, existing test specifications in `docs/tests/tst_aftermath_periods.md` with verified test code implementations in `app/src/test/` or be downgraded to `[Implemented]`. Bidirectional reference integrity must guarantee that every referenced TST is cited back by its parent REQs with zero dangling references.

### 1.2 Attributes
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit / Review

### 1.3 Functional & Architectural Requirements
1. *Complete TST Coverage & Evidence Verification (`REQ-PRO-080.1`)*:
   • Every requirement in `docs/requirements/req_aftermath_periods.md` SHALL cite one or more valid, existing backticked `TST-*` IDs in its `Tests:` field, achieving 100% test citation coverage.
   • `REQ-SPT-002` SHALL cite `TST-SPT-005` and `REQ-SPT-005` SHALL cite `TST-SPT-006`.
   • `REQ-SPT-002` and `REQ-SPT-005` SHALL be eliminated from `LEGACY_UNGROUNDED_VERIFIED_REQS` in `tools/verify_requirement_governance.py`.
2. *Bidirectional Citation Integrity (`REQ-PRO-080.2`)*:
   • Every test specification in `docs/tests/tst_aftermath_periods.md` SHALL be cited back by 100% of the requirements it declares in its `Requirement(s):` field.
   • Zero dangling references SHALL exist within the `aftermath_periods` domain.
3. *Test Code Tag Synchronization (`REQ-PRO-080.3`)*:
   • Test source files in `app/src/test/` SHALL be annotated with standardized `TST-*` comment tags for automated scanner discovery:
     - `PeriodBarGraphTest.kt`: `TST-PER-024`, `TST-PER-029`, `TST-PER-036`.
     - `WorkoutShareBottomSheetContractTest.kt`: `TST-PER-037`.
     - `SportingJourneyRepositoryTest.kt`, `SportingJourneyViewModelTest.kt`, `SportingJourneyCalculatorTest.kt`: `TST-SPT-005` (retiring unmapped `TST-SPT-002`, `TST-SPT-003`, `TST-SPT-008`).
4. *Attribute Compliance & Header Parity (`REQ-PRO-080.4`)*:
   • All 41 requirements in `docs/requirements/req_aftermath_periods.md` SHALL declare valid `Priority`, `Type`, and `Verification Method` attributes per `REQ-PRO-077`.
   • Domain shard headers SHALL reflect accurate totals (`Total Requirements: 41`, `Total Tests: 50`) in 100% parity with master index tables.

### 1.4 Acceptance Criteria (Given-When-Then)
* **Criterion 1**:
  * *Given* `docs/requirements/req_aftermath_periods.md`,
  * *When* evaluated via `python3 tools/verify_requirement_governance.py --coverage`,
  * *Then* 100% of requirements cite valid TSTs, Category 4 bidirectional gaps for the domain are 0, and Category 3 unmapped tags for SPT are eliminated.
* **Criterion 2**:
  * *Given* `docs/requirements/req_aftermath_periods.md` and `docs/tests/tst_aftermath_periods.md`,
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

## 2. Test Specification (TST-PRO-080)

### Test Case 1: Coverage & Bidirectional Traceability Audit (`TST-PRO-080.1`)
* **Scope**: Governance Verification
* **Target File**: `tools/verify_requirement_governance.py`
* **Preconditions**: `req_aftermath_periods.md`, `tst_aftermath_periods.md`, and test code annotations updated.
* **Action**: `python3 tools/verify_requirement_governance.py --coverage`
* **Expected Result**: 0 gaps in Category 1 for aftermath_periods (100% REQs with TST), 0 gaps in Category 4 for aftermath_periods, and 0 Category 3 tags for SPT.

### Test Case 2: Link Integrity, Evidence Status & Attribute Audit (`TST-PRO-080.2`)
* **Scope**: Governance Verification
* **Target File**: `tools/verify_requirement_governance.py`
* **Preconditions**: Shards updated with attributes and bidirectional links.
* **Action**:
  - `python3 tools/verify_requirement_governance.py --check-links`
  - `python3 tools/verify_requirement_governance.py --check-evidence`
  - `python3 tools/verify_requirement_governance.py --check-attributes`
  - `python3 tools/verify_requirement_governance.py --check-tests-format`
* **Expected Result**: Zero dangling references, zero ungrounded Verified statuses in `aftermath_periods`, and zero missing attributes.

### Test Case 3: Targeted Unit & Governance Test Execution (`TST-PRO-080.3`)
* **Commands**:
  - `python3 -m unittest tools/test_verify_requirement_governance.py`
  - `./gradlew testDebugUnitTest --tests "*PeriodBarGraph*" --tests "*SportingJourney*"`
* **Goal**: Verify 100% pass rate across updated test suites.

---

## 3. Traceability Matrix

| Requirement | Description | Test Specification | Verification Status |
| :--- | :--- | :--- | :--- |
| `REQ-PRO-080.1` | Complete TST Coverage & Evidence Verification | `TST-PRO-080.1`, `TST-PRO-080.2` | Planned |
| `REQ-PRO-080.2` | Bidirectional Citation Integrity | `TST-PRO-080.1`, `TST-PRO-080.2` | Planned |
| `REQ-PRO-080.3` | Test Code Tag Synchronization | `TST-PRO-080.1`, `TST-PRO-080.3` | Planned |
| `REQ-PRO-080.4` | Attribute Compliance & Header Parity | `TST-PRO-080.2` | Planned |
