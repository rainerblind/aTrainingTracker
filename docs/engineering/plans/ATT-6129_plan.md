# Stage 3: Implementation Plan - ATT-6129: Backfill test evidence for Aftermath Periods requirements

**Ticket**: [ATT-6129](https://rainerblind.atlassian.net/browse/ATT-6129)  
**Sub-task**: Pending (`[Impl-Plan]`)  
**Parent Epic**: [ATT-6119](https://rainerblind.atlassian.net/browse/ATT-6119) (*ASPICE CL2 – Evidence Integrity & Bidirectional Traceability*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-46.4`  
**Requirement Mapping**: `REQ-PRO-080`  
**Test Mapping**: `TST-PRO-080`  
**Branch**: `subagent-Lane-2-Agent---ATT-6129-self-43cd1c63`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-12  

---

## 1. Problem Description & Background

Remediation ID: A-08.3 under Epic ATT-6119 (ASPICE SWE.6 BP4, SWE.1 BP5). The aftermath periods domain (`docs/requirements/req_aftermath_periods.md` and `docs/tests/tst_aftermath_periods.md`) requires test evidence backfill, bidirectional citation integrity, metadata attribute population (`REQ-PRO-077`), header count reconciliation, legacy ungrounded allowlist retirement for `REQ-SPT-002` and `REQ-SPT-005`, and test code tag linkage.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-PRO-080` (*Aftermath Periods Test Evidence Backfill & Bidirectional Traceability Governance*)
* **Test Mapping**: `TST-PRO-080` (*Aftermath Periods Test Evidence Backfill & Traceability Verification Suite*)
* **Domain REQs**: `REQ-PER-001` through `REQ-PER-038`, `REQ-RAD-005`, `REQ-SPT-002`, `REQ-SPT-005` (41 total)
* **Domain TSTs**: `TST-PERF-001`..`010`, `TST-PER-009`..`046`, `TST-RAD-005`, `TST-SPT-005`, `TST-SPT-006` (50 total)

---

## 3. System Invariants & Preserved Behavior

1. **Zero Unintended Regressions**: Existing period analysis, map rendering, and trophy vault features continue to operate with 100% test pass rate.
2. **Master Index Mathematical Parity**: Master table parity across all 9 domain shards in `docs/requirements.md` and `docs/tests.md` remains 100% synchronized.
3. **Traceability Standards**: 100% of `aftermath_periods` REQs cite valid TSTs, zero ungrounded Verified claims remain, zero dangling references, and 100% bidirectional citation in this domain.
4. **Zero Production Runtime Risk**: Changes are strictly confined to living documentation, test tags, and process governance records.

---

## 4. Proposed Architectural Changes

### Component 1: Test Code Annotations in `app/src/test/`
* `PeriodBarGraphTest.kt`: Add standard `TST-PER-024`, `TST-PER-029`, and `TST-PER-036` tags.
* `WorkoutShareBottomSheetContractTest.kt`: Add `TST-PER-037` tag.
* `SportingJourneyRepositoryTest.kt`, `SportingJourneyViewModelTest.kt`, `SportingJourneyCalculatorTest.kt`: Annotate with `TST-SPT-005` (retiring unmapped `TST-SPT-002`, `TST-SPT-003`, `TST-SPT-008`).

### Component 2: Living Requirement Shard (`docs/requirements/req_aftermath_periods.md`)
* Correct shard header to `Total Requirements: 41`.
* Add `Tests: TST-SPT-005` to `REQ-SPT-002` and `Tests: TST-SPT-006` to `REQ-SPT-005`.
* Update `REQ-PER-001` Tests to include `TST-PERF-002, TST-PERF-004`.
* Update `REQ-PER-024` Tests to include `TST-PER-034`.
* Populate `Priority`, `Type`, and `Verification Method` on all 41 requirements.

### Component 3: Living Test Specification Shard (`docs/tests/tst_aftermath_periods.md`)
* Correct shard header to `Total Tests: 50`.
* Refine `Requirement(s):` fields on `TST-PER-021`, `TST-PER-022`, `TST-PER-025`, `TST-PER-026`, `TST-PER-027`, `TST-PER-028` to achieve 100% bidirectional parity.

### Component 4: Governance Verifier Allowlist Cleanup
* Remove `REQ-SPT-002` and `REQ-SPT-005` from `LEGACY_UNGROUNDED_VERIFIED_REQS` in `tools/verify_requirement_governance.py` and `tools/test_verify_requirement_governance.py`.

### Component 5: Process Stability Shards (`req_process_stability.md`, `tst_process_stability.md`)
* Register `REQ-PRO-080` and `TST-PRO-080` with complete ASPICE SWE.1/SWE.6 metadata.

### Component 6: Master Index Tables (`docs/requirements.md`, `docs/tests.md`)
* Recalculate and synchronize master tables via `verify_requirement_governance.py --sync-indexes`.

### UI Consistency
* No UI changes.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Annotate Test Files
* Files:
  - `app/src/test/java/com/atrainingtracker/trainingtracker/ui/aftermath/periodlist/PeriodBarGraphTest.kt`
  - `app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/WorkoutShareBottomSheetContractTest.kt`
  - `app/src/test/java/com/atrainingtracker/trainingtracker/ui/sportingjourney/SportingJourneyRepositoryTest.kt`
  - `app/src/test/java/com/atrainingtracker/trainingtracker/ui/sportingjourney/SportingJourneyViewModelTest.kt`
  - `app/src/test/java/com/atrainingtracker/trainingtracker/ui/sportingjourney/SportingJourneyCalculatorTest.kt`
* Changes: Add `TST-*` tags in Javadoc / test methods.
* Verification: `python3 -c "from tools.verify_requirement_governance import extract_test_code_tags; tags = extract_test_code_tags(); print([t in tags for t in ['TST-PER-024', 'TST-PER-029', 'TST-PER-036', 'TST-PER-037', 'TST-SPT-005']])"`

### Step 2: Update req_aftermath_periods.md
* Files: `docs/requirements/req_aftermath_periods.md`
* Changes: Update header to 41. Add `Tests` to `REQ-SPT-002` and `REQ-SPT-005`. Align `REQ-PER-001` and `REQ-PER-024`. Backfill `Priority`, `Type`, `Verification Method` for all 41 REQs.
* Verification: `python3 tools/verify_requirement_governance.py --check-attributes`

### Step 3: Update tst_aftermath_periods.md
* Files: `docs/tests/tst_aftermath_periods.md`
* Changes: Update header to 50. Reconcile bidirectional links.
* Verification: `python3 tools/verify_requirement_governance.py --check-links`

### Step 4: Governance Verifier Allowlist Cleanup
* Files: `tools/verify_requirement_governance.py`, `tools/test_verify_requirement_governance.py`
* Changes: Remove `REQ-SPT-002` and `REQ-SPT-005` from `LEGACY_UNGROUNDED_VERIFIED_REQS`.
* Verification: `python3 -m unittest tools/test_verify_requirement_governance.py`

### Step 5: Register REQ-PRO-080 and TST-PRO-080
* Files: `docs/requirements/req_process_stability.md`, `docs/tests/tst_process_stability.md`
* Changes: Append `REQ-PRO-080` and `TST-PRO-080`.
* Verification: `python3 tools/verify_requirement_governance.py --check-links`

### Step 6: Synchronize Master Indexes & Recalculate
* Files: `docs/requirements.md`, `docs/tests.md`
* Changes: Execute `python3 tools/verify_requirement_governance.py --sync-indexes`.
* Verification: `git diff docs/requirements.md docs/tests.md`

### Step 7: Governance & Test Execution
* Commands:
  - `python3 tools/verify_requirement_governance.py --coverage`
  - `python3 tools/verify_requirement_governance.py --check-links`
  - `python3 tools/verify_requirement_governance.py --check-evidence`
  - `python3 tools/verify_requirement_governance.py --check-attributes`
  - `python3 -m unittest tools/test_verify_requirement_governance.py`
  - `./gradlew testDebugUnitTest --tests "*PeriodBarGraph*" --tests "*SportingJourney*"`
