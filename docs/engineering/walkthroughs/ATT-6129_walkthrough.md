# Stage 5 Walkthrough: ATT-6129 - Backfill test evidence for Aftermath Periods requirements

**Ticket**: [ATT-6129](https://rainerblind.atlassian.net/browse/ATT-6129)  
**Sub-task**: Pending (`[Test]`)  
**Parent Epic**: [ATT-6119](https://rainerblind.atlassian.net/browse/ATT-6119) (*ASPICE CL2 – Evidence Integrity & Bidirectional Traceability*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-46.4`  
**Requirement Mapping**: `REQ-PRO-080` (`REQ-PRO-080.1`–`REQ-PRO-080.4`)  
**Test Spec ID**: `TST-PRO-080` (`TST-PRO-080.1`–`TST-PRO-080.4`)  
**Branch**: `subagent-Lane-2-Agent---ATT-6129-self-43cd1c63`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-12  

---

## 1. Problem Statement & Motivation

Prior to this remediation (Remediation ID: A-08.3, ASPICE SWE.6 BP4, SWE.1 BP5), the `aftermath_periods` requirement and test specification shards had evidence and traceability gaps:
* `REQ-SPT-002` and `REQ-SPT-005` in `docs/requirements/req_aftermath_periods.md` lacked `Tests:` fields, triggering legacy ungrounded Verified warnings.
* Multiple test specifications in `docs/tests/tst_aftermath_periods.md` (`TST-PER-021`, `TST-PER-022`, `TST-PER-025`, `TST-PER-026`, `TST-PER-027`, `TST-PER-028`, `TST-PER-034`, `TST-PERF-004`, `TST-SPT-005`, `TST-SPT-006`) cited requirements that did not cite them back.
* Test code tags for `TST-PER-024`, `TST-PER-029`, `TST-PER-036`, `TST-PER-037`, and `TST-SPT-005` were missing or using non-standard notation in test source files, while `TST-SPT-002`, `TST-SPT-003`, and `TST-SPT-008` appeared as unmapped test tags in Category 3.
* Shard headers had outdated counters (`Total Requirements: 35`, `Total Tests: 44`) out of sync with actual entries (41 REQs and 50 TSTs).
* All 41 requirements in `req_aftermath_periods.md` lacked explicit metadata attributes (`Priority`, `Type`, `Verification Method`) required by `REQ-PRO-077`.

---

## 2. Key Changes & Architecture

1. **Test Code Annotation**:
   - `PeriodBarGraphTest.kt`: Added `TST-PER-024`, `TST-PER-029`, and `TST-PER-036` tags in class-level KDoc.
   - `WorkoutShareBottomSheetContractTest.kt`: Added `TST-PER-037` tag in class-level KDoc.
   - `SportingJourneyRepositoryTest.kt`, `SportingJourneyViewModelTest.kt`, `SportingJourneyCalculatorTest.kt`: Standardized on `TST-SPT-005` (retiring unmapped `TST-SPT-002`, `TST-SPT-003`, `TST-SPT-008`).
2. **Living Requirements Shard (`docs/requirements/req_aftermath_periods.md`)**:
   - Updated header to `Total Requirements: 41` and prefixes to `PER, PERF, RAD, SPT`.
   - Added `- **Tests**: `TST-SPT-005`` to `REQ-SPT-002` and `- **Tests**: `TST-SPT-006`` to `REQ-SPT-005`.
   - Updated `REQ-PER-001` Tests to `- **Tests**: `TST-PERF-002`, `TST-PERF-004``.
   - Updated `REQ-PER-024` Tests to `- **Tests**: `TST-PER-030`, `TST-PER-032`, `TST-PER-034``.
   - Backfilled `Priority`, `Type`, and `Verification Method` on all 41 requirements.
   - Retained `[Verified]` status for all 41 requirements backed by active automated test suites.
3. **Living Test Specification Shard (`docs/tests/tst_aftermath_periods.md`)**:
   - Updated header to `Total Tests: 50` and prefixes to `PER, PERF, RAD, SPT`.
   - Refined `Requirement(s):` fields on `TST-PER-021`, `TST-PER-022`, `TST-PER-025`, `TST-PER-026`, `TST-PER-027`, `TST-PER-028` to ensure 100% bidirectional parity.
4. **Governance Verifier Allowlist Cleanup (`tools/verify_requirement_governance.py`)**:
   - Removed `REQ-SPT-002` and `REQ-SPT-005` from `LEGACY_UNGROUNDED_VERIFIED_REQS`.
5. **Process Governance Registration (`req_process_stability.md`, `tst_process_stability.md`)**:
   - Registered `REQ-PRO-080` and `TST-PRO-080` under Epic ATT-6119.
6. **Master Index Tables Synchronization (`docs/requirements.md`, `docs/tests.md`)**:
   - Recalculated and synchronized master index tables via `verify_requirement_governance.py --sync-indexes` with 100% mathematical parity (Total=1082 / Total=1088).

---

## 3. Verification & Test Evidence

* **Traceability & Verification Coverage Report (`python3 tools/verify_requirement_governance.py --coverage`)**:
  - **Category 1 (REQs without TST)**: 0 aftermath_periods gaps (100% of 41 REQs cite valid TSTs).
  - **Category 2 (TSTs without code)**: Verified active test code tags for all period test specifications.
  - **Category 3 (Code tags without spec)**: Eliminated unmapped tags `TST-SPT-002`, `TST-SPT-003`, `TST-SPT-008`.
  - **Category 4 (TSTs not cited back)**: 0 aftermath_periods gaps (100% bidirectional citation across all 50 items).
* **Reference & Evidence Governance**:
  - `python3 tools/verify_requirement_governance.py --check-links`: PASS (0 dangling references in aftermath_periods).
  - `python3 tools/verify_requirement_governance.py --check-evidence`: PASS (0 ungrounded Verified claims).
  - `python3 tools/verify_requirement_governance.py --check-attributes`: PASS (0 missing attributes in aftermath_periods).
  - `python3 tools/verify_requirement_governance.py --check-tests-format`: PASS (100% valid Tests syntax).
  - `python3 tools/verify_requirement_governance.py --sync-indexes`: PASS (100% mathematical parity).
* **Python Tooling Unit Tests**:
  - `python3 -m unittest tools/test_verify_requirement_governance.py`: 50/50 tests passed in 11.1s.
* **Targeted Unit Test Execution**:
  - `./gradlew testDebugUnitTest --tests "*PeriodBarGraph*" --tests "*SportingJourney*"`: BUILD SUCCESSFUL (100% green).

| Test Case | Scope | Method Under Test | Status | Evidence |
| :--- | :--- | :--- | :---: | :--- |
| `TST-PRO-080.1` | Governance Tooling | `verify_requirement_governance.py --coverage` | **PASS** | 0 aftermath_periods gaps in Cat 1 and Cat 4 (100% coverage and bidirectional back-links) |
| `TST-PRO-080.2` | Governance Tooling | `--check-links`, `--check-evidence`, `--check-attributes`, `--check-tests-format` | **PASS** | 0 dangling references, 0 ungrounded Verified statuses, 0 missing attributes |
| `TST-PRO-080.3` | Unit Test | `PeriodBarGraphTest`, `SportingJourney*Test` | **PASS** | BUILD SUCCESSFUL (33 actionable tasks executed) |
| `TST-PRO-080.4` | Living Doc Parity | `verify_requirement_governance.py --sync-indexes` | **PASS** | 100% mathematical parity across `docs/requirements.md` and `docs/tests.md` |

---

## 4. System Invariants & Rollback Safety

- **Zero Android Production Runtime Code Modification**: Only living documentation shards, test tag comments, and process stability governance records modified.
- **Traceability Guarantee**: 100% of requirements in `aftermath_periods` possess verified test evidence.
- **Master Index Parity**: Mathematical consistency maintained across master tables and shards.
