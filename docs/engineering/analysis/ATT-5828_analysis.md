# Stage 1 Analysis: ATT-5828 - [Bug] Unit Tests (PeriodsViewModelTest): Inject testDispatcher into all PeriodsViewModel instances to prevent UncaughtExceptionsBeforeTest

**Ticket**: [ATT-5828](https://rainerblind.atlassian.net/browse/ATT-5828)  
**Sub-task**: Pending (`[Analysis]`)  
**Parent Epic**: None (Standalone)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-45.11`  
**Branch**: `feature/ATT-5828`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-07  

---

## 1. Problem Statement & Motivation
In `PeriodsViewModelTest.kt`, multiple tests (e.g., line 290 `testFilteredGroupedPeriods_emitsTransformedHierarchy` and line 330 `testSelectedMetric_defaultAndHoisting`) instantiate `PeriodsViewModel(mockApplication)` without passing test dispatchers.
Consequently, default parameters (`Dispatchers.IO` and `Dispatchers.Default`) are bound. Asynchronous background coroutines and flow transformations (`filteredGroupedPeriods` with `SharingStarted.Eagerly`) can leak into background pools. Under test runner contention across JVM forks, unhandled asynchronous exceptions leak into subsequent `runTest` executions (such as line 310 `testShowPeriodMap_withInitialRegionId_setsSelectedRegionId`), intermittently triggering `kotlinx.coroutines.test.UncaughtExceptionsBeforeTest` and violating `docs/coding_standards.md` Section 5.5.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **Root Cause**: `PeriodsViewModel`'s constructor declares `@JvmOverloads constructor(application: Application, ioDispatcher: CoroutineDispatcher = Dispatchers.IO, defaultDispatcher: CoroutineDispatcher = Dispatchers.Default)`. Omitting dispatchers binds real background threads. In tests with mocked contexts, lingering coroutines survive test completion and pollute the shared JVM worker state.
* **Independent Peer Review & Verification (4-Augen-Prinzip)**:
  * *Path Discrepancy*: Ticket and prompt referenced `ui/periods/PeriodsViewModelTest.kt`; authentic codebase location is `app/src/test/java/com/atrainingtracker/trainingtracker/ui/aftermath/periodlist/PeriodsViewModelTest.kt` (and companion `PeriodsViewModel.kt`).
  * *Parameter Discrepancy*: Ticket stated injection for "mainDispatcher and ioDispatcher". Forensic audit of `PeriodsViewModel.kt` reveals constructor parameters are `(application, ioDispatcher, defaultDispatcher)`. `Dispatchers.Main` is controlled via `Dispatchers.setMain(testDispatcher)` in `@Before setUp()`. Thus, `testDispatcher` must be injected for both `ioDispatcher` and `defaultDispatcher`.
  * *Full Call-Site Audit*: 8 of 11 tests in `PeriodsViewModelTest.kt` omit test dispatchers (lines 130, 167, 221, 290, 330, 345, 392, 441). Only 3 tests (lines 313, 516, 577) currently inject `testDispatcher`.
  * *Adjacent Scope*: `PeriodsRepositorySyncTest.kt:178` also calls `PeriodsViewModel(mockApp)`; audit indicates isolation hardening there as well.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Objectives**:
  * Introduce DRY helper `createViewModel(app, ioDispatcher, defaultDispatcher)` in `PeriodsViewModelTest.kt` defaulting to `mockApplication` and `testDispatcher`.
  * Update all 11 test cases in `PeriodsViewModelTest.kt` to use the helper, ensuring 100% testDispatcher coverage.
  * Audit adjacent `PeriodsRepositorySyncTest.kt:178` for test dispatcher consistency.
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  * Zero modification to `PeriodsViewModel.kt` production code or public API contracts.
  * No modification to period aggregation algorithms, database schemas, or Compose UI rendering.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-PER-022`, `REQ-PER-024`, `REQ-PER-025`, `REQ-PER-027` in `docs/requirements/req_aftermath_periods.md`, slaved to `docs/coding_standards.md` Section 5.5 (*Test Isolation: No Real Background Work with Mocked Android Contexts*).
* **Historical Origin & Commit Trace**: Added across `ATT-4770`, `ATT-4833`, `ATT-4935`, `ATT-5195`.
* **Root Reason for Existing Formulation**: Early unit tests tested purely synchronous filter math and relied on Kotlin default constructor parameters for convenience, not anticipating coroutine leakage in multi-test JVM test suites.
* **Preservation of Core Invariants**: All existing test assertions, sport filter logic, and test coverage remain 100% intact.

---

## 5. Architectural Strategy & High-Level Solution
1. In `PeriodsViewModelTest.kt`, add:
   ```kotlin
   private fun createViewModel(
       app: Application = mockApplication,
       ioDispatcher: CoroutineDispatcher = testDispatcher,
       defaultDispatcher: CoroutineDispatcher = testDispatcher
   ): PeriodsViewModel = PeriodsViewModel(app, ioDispatcher, defaultDispatcher)
   ```
2. Refactor all 11 test cases in `PeriodsViewModelTest.kt` to instantiate via `createViewModel()`.
3. In `PeriodsRepositorySyncTest.kt:178`, inject `UnconfinedTestDispatcher()` / `testScheduler` dispatcher.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**: Zero production code changes; 100% unit test pass rate across `com.atrainingtracker.trainingtracker.ui.aftermath.periodlist.*`; full compliance with Rule 66 / Section 5.5.
* **Risk Rating**: **LOW** (Test-only refactoring and fixture standardization with zero blast radius on production code).
