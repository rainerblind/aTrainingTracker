# Stage 2: Requirement & Test Specification - ATT-4669: Fix Full-Suite Unit Test Failures

**Ticket**: [ATT-4669](https://rainerblind.atlassian.net/browse/ATT-4669)  
**Sub-task**: Pending (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-2466](https://rainerblind.atlassian.net/browse/ATT-2466) (*Developer Testing Tools*)  
**Target Release**: `Sprint 2026-43.7` | **Active Sprint**: `2026-43.7` | **Branch**: `feature/ATT-4669`  
**Requirement Mapping**: `REQ-TST-003` (*Deterministic Test State Isolation, Scaffolding Parity, Coroutine Teardown & Clean-Room Suite Execution*)  
**Test Spec ID**: `TST-TST-003`  
**Author**: AI Agent 1 (Implementer) | **Date**: 2026-10-22  

---

## 1. Requirement Specification (`REQ-TST-003`)

### 1.1 Problem Statement & Rationale
During Tier 2 Sprint Integration Gate (`./gradlew testDebugUnitTest`), 5 tests fail across 3 categories (contract collision, missing static mock, teardown coroutine leakage / uncoordinated dispatchers). The test infrastructure and ViewModel scaffolding must ensure 100% test isolation, complete static mocks, and coroutine lifecycle teardown.

### 1.2 Functional & Architectural Requirements
1. **Contract Alignment (`REQ-TST-003.1`)**: Excise obsolete `TrainingApplication todo method is marked as Deprecated` test in `HardcodedStringsAndUnitsContractTest.kt`, aligning with `REQ-ARC-003.1`.
2. **Static Mock Scaffolding (`REQ-TST-003.2`)**: In `AltitudeAwareClusterMatchingTest.kt`, supply `mockkStatic(KnownLocationsDatabaseManager::class)` and mock instance for `WorkoutClusterEngine.learnFromWorkout()` callers.
3. **DataStore Isolation (`REQ-TST-003.3`)**: In `DevicesTabbedViewModelTest.kt`, inject `settingsDataStore = mockSettingsDataStore` and stub `mockApplication.filesDir`.
4. **Dispatcher Injection (`REQ-TST-003.4`)**: In `BackupRestoreViewModel.kt`, inject `CoroutineDispatcher = Dispatchers.IO` via `@JvmOverloads`, and pass `testDispatcher` in `BackupRestoreViewModelLocalRestoreTest.kt`.
5. **Coroutine Lifecycle Teardown (`REQ-TST-003.5`)**: In `TrackingTabsViewModelInteropTest.kt`, track all created ViewModels and invoke `viewModelScope.cancel()` in `@After` prior to `Dispatchers.resetMain()` and `unmockkAll()`.
6. **Clean-Room Regression Suite (`REQ-TST-003.6`)**: Achieve 100% pass rate across all 3,840+ tests under `./gradlew testDebugUnitTest`.

### 1.3 Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Net-new requirement `REQ-TST-003` under Epic `ATT-2466` (*Developer Testing Tools*), extending `REQ-TST-001` and `REQ-BLD-002`. Targets `HardcodedStringsAndUnitsContractTest.kt`, `AltitudeAwareClusterMatchingTest.kt`, `DevicesTabbedViewModelTest.kt`, `BackupRestoreViewModelLocalRestoreTest.kt`, `BackupRestoreViewModel.kt`, and `TrackingTabsViewModelInteropTest.kt`.
* **Historical Origin & Commit Trace**: Discovered during Sprint `2026-43.7` Tier 2 integration gating (`feature/ATT-4669`). Traces to `ATT-4024` (commit `040854ad`, catch excision), `ATT-4026` (commit `8529df98`, contract test), and `ATT-4031` (commit `f04b501c`, dead code removal).
* **Root Reason for Existing Formulation**: Tests lacked mocks when catch blocks were excised; contract tests retained checks for deleted dead code; ViewModels hardcoded `Dispatchers.IO` and omitted `viewModelScope.cancel()` in `@After`.
* **Preservation of Core Invariants**: No modifications to production runtime business logic, database schemas, or calculation formulas; thread safety and 100% clean-room test pass rate preserved.

### 1.4 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Contract Alignment)**: *Given* `HardcodedStringsAndUnitsContractTest`, *When* executed, *Then* it passes without asserting on deleted `todo()`.
* **Criterion 2 (Clustering Mock Scaffolding)**: *Given* `AltitudeAwareClusterMatchingTest`, *When* `testLearnFromWorkout` runs, *Then* `KnownLocationsDatabaseManager` static mock satisfies dependency and test passes.
* **Criterion 3 (DataStore Test Isolation)**: *Given* `DevicesTabbedViewModelTest`, *When* ViewModels are instantiated, *Then* `mockSettingsDataStore` and `filesDir` prevent NPE.
* **Criterion 4 (Dispatcher Synchronization)**: *Given* `BackupRestoreViewModelLocalRestoreTest`, *When* `restoreFromLocalBackup` executes, *Then* `advanceUntilIdle()` synchronizes `testDispatcher` deterministically.
* **Criterion 5 (Coroutine Teardown)**: *Given* `TrackingTabsViewModelInteropTest`, *When* teardown runs, *Then* `viewModelScope.cancel()` prevents `UncaughtExceptionsBeforeTest`.

---

## 2. Test Specification (`TST-TST-003`)

### Test Cases
* `TST-TST-003.1` (**Contract Alignment**): `HardcodedStringsAndUnitsContractTest` passes with excised `todo()` assertion.
* `TST-TST-003.2` (**Cluster Static Mock Scaffolding**): `AltitudeAwareClusterMatchingTest.testLearnFromWorkout_UpdatesAltitudeExtremaCentroidsInDatabase` passes with mocked `KnownLocationsDatabaseManager`.
* `TST-TST-003.3` (**DataStore Test Isolation**): `DevicesTabbedViewModelTest` passes all test cases with injected `mockSettingsDataStore`.
* `TST-TST-003.4` (**Dispatcher Injection & Synchronization**): `BackupRestoreViewModelLocalRestoreTest.testRestoreFromLocalBackup_whenRestoreThrowsException_emitsError` passes deterministically with `testDispatcher`.
* `TST-TST-003.5` (**Coroutine Scope Teardown**): `TrackingTabsViewModelInteropTest` passes without leaking uncaught exceptions into subsequent test cases.
* `TST-TST-003.6` (**Clean-Room Regression Suite**): `./gradlew testDebugUnitTest` and `TranslationParityTest` achieve 100% pass rate.

---

## 3. Traceability Matrix

| Test Case | Scope | Target File / Class Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-TST-003.1` | Contract | `HardcodedStringsAndUnitsContractTest.kt` | `REQ-TST-003.1` | Proposed |
| `TST-TST-003.2` | Unit | `AltitudeAwareClusterMatchingTest.kt` | `REQ-TST-003.2` | Proposed |
| `TST-TST-003.3` | Unit | `DevicesTabbedViewModelTest.kt` | `REQ-TST-003.3` | Proposed |
| `TST-TST-003.4` | Unit | `BackupRestoreViewModelLocalRestoreTest.kt`, `BackupRestoreViewModel.kt` | `REQ-TST-003.4` | Proposed |
| `TST-TST-003.5` | Unit | `TrackingTabsViewModelInteropTest.kt` | `REQ-TST-003.5` | Proposed |
| `TST-TST-003.6` | Regression | Full test suite (`./gradlew testDebugUnitTest`) | `REQ-TST-003.6` | Proposed |
