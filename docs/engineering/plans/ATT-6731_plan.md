# Stage 3: Implementation Plan - ATT-6731: Move TranslationParityTest and repo governance guards from source-reading baseline to allow-list and ratchet ceiling

**Ticket**: [ATT-6731](https://rainerblind.atlassian.net/browse/ATT-6731)  
**Sub-task**: `[Impl-Plan]`  
**Parent Epic**: [ATT-6142](https://rainerblind.atlassian.net/browse/ATT-6142) (*ASPICE CL2 – System Level, Architecture Conformance, Integration & Quality Gates*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-46.2`  
**Requirement Mapping**: `REQ-PRO-049` (`REQ-PRO-049.1`–`REQ-PRO-049.4`)  
**Test Mapping**: `TST-PRO-049` (`TST-PRO-049.1`–`TST-PRO-049.5`)  
**Branch**: `feature/ATT-6731`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-11  

---

## 1. Problem Description & Background

Sprint review of ATT-6186 revealed that 6 repository-wide policy and invariant scanners (`LocalizationTestCache`, `ThemeColorCentralizationAuditTest`, `RepositoryEngineCleanlinessContractTest`, `RepositoryUiCleanlinessContractTest`, `GlobalDeleteContextMenuAuditTest`, `EditIconConsistencyTest`) were misclassified into `app/source-reading-baseline.txt` (legacy debt slated for deletion by ATT-6564..ATT-6569) rather than `LEGITIMATE_GOVERNANCE_GUARDS`. Additionally, `testBaseline_containsOnlyExpectedLegacyCount()` used rigid count equality (`assertEquals(280)`) without stale-entry detection (`(baseline - detectedReaders).isEmpty()`), and reader detection missed stream calls (`useLines`, `bufferedReader`).

---

## 2. Traceability & Requirements Mapping

* **REQ-PRO-049.1**: Extended reader scan detecting `readText`, `readLines`, `readString`, `useLines`, `bufferedReader` targeting `src/main`.
* **REQ-PRO-049.2**: Allow-list expansion in `LEGITIMATE_GOVERNANCE_GUARDS` to 22 verified policy guards with disk existence checks.
* **REQ-PRO-049.3**: Ratcheted ceiling (`baseline.size <= 276`) and stale-entry tripwire (`(baseline - detectedReaders).isEmpty()`).
* **REQ-PRO-049.4**: Documented baseline package breakdown (118 entries for ATT-6569) and `ui.components` split recommendation.
* **Test Mapping**: `TST-PRO-049.1`..`TST-PRO-049.5` in `SourceTextReadingAuditContractTest.kt`.

---

## 3. System Invariants & Preserved Behavior

1. **Zero Unintended Regressions**: Existing 3,660+ unit tests continue to pass with 100% success rate.
2. **Permanent Guard Protection**: Promoted governance guards are immune to automated phase-out deletion in cleanup tickets.
3. **Monotonic Debt Ratcheting**: Legacy debt ceiling strictly ratchets downward; stale baseline entries trigger test failure.
4. **Code Lock Until Gate 3**: Zero modifications to production source code; test infrastructure changes execute only in Stage 4.

---

## 4. Proposed Architectural Changes

### Component 1: `SourceTextReadingAuditContractTest.kt`
* Expand `LEGITIMATE_GOVERNANCE_GUARDS` from 14 to 22 entries by adding the 8 approved repository-wide policy scanners.
* In `scanTestSourceFiles()`, expand `hasReadInvocation` to detect `useLines` and `bufferedReader`.
* In `testGovernanceGuards_allExplicitlyAllowed()`, assert size is 22, verify `.endsWith("Test.kt") || .endsWith("Cache.kt")`, and assert file existence on disk via `findTestDir()`.
* In `testBaseline_containsOnlyExpectedLegacyCount()`, assert `(baseline - detectedReaders).isEmpty()` and `baseline.size <= 276`.

### Component 2: `app/source-reading-baseline.txt`
* Remove 6 promoted governance guards.
* Add 2 recent sprint UI layout debt entries (`CardioPowerTrendsCardTest.kt`, `SensorFieldViewGraphOnlyLayoutTest.kt`).
* Update header and sort 276 entries alphabetically.

### UI Consistency (Rule 23)
* No UI changes.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Update Baseline Debt Inventory
* Files: `app/source-reading-baseline.txt`
* Changes: Remove the 6 promoted governance guards, record 2 recent UI layout tests, sort alphabetically (276 entries total).

### Step 2: Update Audit Scanner & Allow-List
* Files: `app/src/test/java/com/atrainingtracker/trainingtracker/build/SourceTextReadingAuditContractTest.kt`
* Changes: Expand `LEGITIMATE_GOVERNANCE_GUARDS` to 22 entries; extend `scanTestSourceFiles()` to include `useLines` and `bufferedReader`; update test methods for 22 guards, guard file existence, `(baseline - detectedReaders).isEmpty()`, and ratcheted ceiling 276.

### Step 3: Targeted Unit Test Execution
* Command: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.build.SourceTextReadingAuditContractTest"`
* Expected Result: 4/4 tests pass with 100% success rate.

### Step 4: Requirement Governance Parity Verification
* Command: `python3 tools/verify_requirement_governance.py`
* Expected Result: PASS across all 9 domain shards with 100% table and header parity.

---

## 6. Verification & Rollback Plan

* **Verification**: Run targeted suite `SourceTextReadingAuditContractTest`, then full clean-room `./gradlew testDebugUnitTest` in Stage 5.
* **Rollback**: Branch `feature/ATT-6731` is isolated; changes can be reverted atomically via git reset.
