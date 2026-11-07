# Stage 3: Implementation Plan - ATT-5828: Inject testDispatcher into all PeriodsViewModel instances to prevent UncaughtExceptionsBeforeTest

**Ticket**: [ATT-5828](https://rainerblind.atlassian.net/browse/ATT-5828)  
**Sub-task**: Pending (`[Impl-Plan]`)  
**Parent Epic**: None (Standalone)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-45.11`  
**Requirement Mapping**: `REQ-PER-022.5` (`REQ-PER-022`), `docs/coding_standards.md` §5.5  
**Test Mapping**: `TST-PER-028` (`TST-PER-028.1`, `TST-PER-028.2`, `TST-PER-028.3`)  
**Branch**: `feature/ATT-5828`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-07  

---

## 1. Problem Description & Background
In `PeriodsViewModelTest.kt`, 8 of 11 tests instantiate `PeriodsViewModel(mockApplication)` without passing test dispatchers, defaulting to real `Dispatchers.IO` and `Dispatchers.Default`. Eager coroutines started during flow transformations (`filteredGroupedPeriods`) leak into background thread pools. Under parallel runner load across JVM forks, lingering asynchronous work triggers sporadic `UncaughtExceptionsBeforeTest` in subsequent tests (such as line 310 `testShowPeriodMap_withInitialRegionId_setsSelectedRegionId`), violating `docs/coding_standards.md` §5.5.

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-PER-022.5` (*Test State Isolation & Coroutine Dispatcher Injection*) slaved to `docs/coding_standards.md` §5.5
* **Test Mapping**: `TST-PER-028.1` (100% `createViewModel()` injection in `PeriodsViewModelTest`), `TST-PER-028.2` (dispatcher injection in `PeriodsRepositorySyncTest:178`), `TST-PER-028.3` (Tier 1 targeted test suite)

---

## 3. System Invariants & Preserved Behavior
1. **Production Code Immutability**: Zero changes to production code in `app/src/main/` (`PeriodsViewModel.kt` retains `@JvmOverloads` defaults for production callers).
2. **Behavioral Invariance**: All existing unit test assertions, filter invariants (empty guard, multi-sport projection, polyline filtering, spatial clustering), and mock behaviors remain 100% intact.
3. **DRY Instantiation**: Centralized `createViewModel()` helper encapsulates `testDispatcher` binding, preventing future tests from omitting dispatchers.
4. **Code Lock**: Strict enforcement of Rule 4; changes restricted entirely to test fixtures.

---

## 4. Proposed Architectural Changes
* **`PeriodsViewModelTest.kt` (Test Suite)**:
  * Add private factory helper:
    ```kotlin
    private fun createViewModel(
        app: Application = mockApplication,
        ioDispatcher: CoroutineDispatcher = testDispatcher,
        defaultDispatcher: CoroutineDispatcher = testDispatcher
    ): PeriodsViewModel = PeriodsViewModel(app, ioDispatcher, defaultDispatcher)
    ```
  * Refactor all 11 test methods to use `createViewModel()` instead of direct constructor calls.
* **`PeriodsRepositorySyncTest.kt` (Adjacent Test Suite)**:
  * In `testPeriodsViewModel_loadPeriods_triggersCheckIntegrityAndSync`, instantiate `PeriodsViewModel(mockApp, testDispatcher, testDispatcher)` using a scoped `StandardTestDispatcher()`.
* **UI Consistency**: No UI changes.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Add `createViewModel()` Helper & Refactor `PeriodsViewModelTest.kt`
* File: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/aftermath/periodlist/PeriodsViewModelTest.kt`
* Add `createViewModel(app, ioDispatcher, defaultDispatcher)`.
* Replace direct `PeriodsViewModel(...)` constructor calls in all 11 test cases (lines 130, 167, 221, 290, 313, 330, 345, 392, 441, 516, 577) with `createViewModel()`.

### Step 2: Harden Adjacent Instantiation in `PeriodsRepositorySyncTest.kt`
* File: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/aftermath/periodlist/PeriodsRepositorySyncTest.kt`
* In `testPeriodsViewModel_loadPeriods_triggersCheckIntegrityAndSync` (line 178), inject `StandardTestDispatcher()` for both `ioDispatcher` and `defaultDispatcher`.

### Step 3: Targeted Unit & Regression Test Verification
* Execute:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.aftermath.periodlist.*" --max-workers=2
  python3 tools/verify_requirement_governance.py
  ```

---

## 6. Verification & Rollback Plan
* **Verification**: Verify 100% clean-room test pass rate across `ui.aftermath.periodlist.*` with zero lingering coroutine leakage or `UncaughtExceptionsBeforeTest`.
* **Rollback**: `git checkout -- app/src/test/` cleanly restores previous test state.
