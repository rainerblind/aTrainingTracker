# Stage 2: Requirement & Test Specification - ATT-5828: Inject testDispatcher into all PeriodsViewModel instances to prevent UncaughtExceptionsBeforeTest

**Ticket**: [ATT-5828](https://rainerblind.atlassian.net/browse/ATT-5828)  
**Sub-task**: Pending (`[Req & Test Spec]`)  
**Parent Epic**: None (Standalone)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-45.11`  
**Requirement Mapping**: `REQ-PER-022.5` (*Test State Isolation & Coroutine Dispatcher Injection*), `docs/coding_standards.md` §5.5  
**Test Spec ID**: `TST-PER-028`  
**Branch**: `feature/ATT-5828`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-07  

---

## 1. Requirement Specification (REQ-PER-022.5)

### 1.1 Problem Statement & Rationale
In `PeriodsViewModelTest.kt`, 8 of 11 tests instantiate `PeriodsViewModel(mockApplication)` without passing test dispatchers, defaulting to real `Dispatchers.IO` and `Dispatchers.Default`. Coroutines started during flow transformation (`filteredGroupedPeriods` using `SharingStarted.Eagerly`) leak into background thread pools. Under multi-test runner contention, uncaught exceptions bleed into subsequent tests, sporadically throwing `UncaughtExceptionsBeforeTest` and violating `docs/coding_standards.md` §5.5.

### 1.2 Functional & Architectural Requirements
1. `PeriodsViewModelTest.kt` SHALL provide a centralized helper `createViewModel(app = mockApplication, ioDispatcher = testDispatcher, defaultDispatcher = testDispatcher)` to standardize instantiation.
2. All 11 test cases in `PeriodsViewModelTest.kt` SHALL instantiate `PeriodsViewModel` exclusively through `createViewModel()`, guaranteeing 100% testDispatcher coverage for both `ioDispatcher` and `defaultDispatcher`.
3. In `PeriodsRepositorySyncTest.kt:178`, `PeriodsViewModel` instantiation SHALL explicitly pass `testDispatcher` (or `UnconfinedTestDispatcher`), eliminating background coroutine leakage.
4. Production code contracts, public `@JvmOverloads` constructor signatures, and SQLite/Compose behavior SHALL remain 100% untouched.

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (100% Test Dispatcher Injection)**:
  * *Given* all test methods in `PeriodsViewModelTest.kt`,
  * *When* `createViewModel()` is invoked,
  * *Then* both `ioDispatcher` and `defaultDispatcher` are bound to `testDispatcher`.
* **Criterion 2 (UncaughtExceptionsBeforeTest Elimination)**:
  * *Given* execution of `PeriodsViewModelTest.kt` across multiple JVM runner iterations,
  * *When* `testShowPeriodMap_withInitialRegionId_setsSelectedRegionId` and preceding tests run,
  * *Then* zero background thread leakage occurs and zero `UncaughtExceptionsBeforeTest` errors are thrown.

### Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-PER-022.5` (`docs/requirements/req_aftermath_periods.md:466`), `docs/coding_standards.md` §5.5.
* **Historical Origin & Commit Trace**: `ATT-4770`, `ATT-4935`, `ATT-5195`.
* **Root Reason for Existing Formulation**: Tests omitted dispatcher arguments relying on `@JvmOverloads` defaults for brevity; coroutine leakage across JVM worker forks was not initially observed.
* **Preservation of Core Invariants**: 100% existing assertions, sport filtering, map previews, and zero production code changes strictly preserved.

---

## 2. Test Specification (TST-PER-028)

### Test Case 1: `testPeriodsViewModel_testDispatcherInjection_preventsLeakage` (`TST-PER-028.1`)
* **Scope**: Unit Test (`app/src/test/java/com/atrainingtracker/trainingtracker/ui/aftermath/periodlist/PeriodsViewModelTest.kt`)
* **Action**: Audit all 11 test methods in `PeriodsViewModelTest.kt` to verify invocation of `createViewModel()`.
* **Expected Result**: 100% test suite passes under isolated test scheduler; zero asynchronous coroutines escape into real thread pools.

### Test Case 2: Synchronous Periods Repository Sync Isolation (`TST-PER-028.2`)
* **Scope**: Unit Test (`app/src/test/java/com/atrainingtracker/trainingtracker/ui/aftermath/periodlist/PeriodsRepositorySyncTest.kt`)
* **Action**: Verify `testPeriodsViewModel_loadPeriods_triggersCheckIntegrityAndSync` instantiates `PeriodsViewModel` with injected test dispatchers.
* **Expected Result**: Clean execution without lingering IO coroutines.

### Test Case 3: Tier 1 Targeted Regression Suite (`TST-PER-028.3`)
* **Command**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.aftermath.periodlist.*"`
* **Expected Result**: 100% pass across all 9 test classes in package `ui.aftermath.periodlist`.

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-PER-028.1` | Unit | `PeriodsViewModelTest.createViewModel()` (11 tests) | `REQ-PER-022.5` | Specified |
| `TST-PER-028.2` | Unit | `PeriodsRepositorySyncTest:178` | `REQ-PER-022.5` | Specified |
| `TST-PER-028.3` | Regression | Package `ui.aftermath.periodlist.*` (Tier 1) | `REQ-PRO-001` | Specified |
