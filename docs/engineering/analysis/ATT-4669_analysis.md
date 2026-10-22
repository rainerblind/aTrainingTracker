# Stage 1 Analysis: ATT-4669 - Fix Full-Suite Unit Test Failures

**Ticket**: [ATT-4669](https://rainerblind.atlassian.net/browse/ATT-4669)  
**Sub-task**: [ATT-4672](https://rainerblind.atlassian.net/browse/ATT-4672) (`[Analysis]`)  
**Parent Epic**: [ATT-2466](https://rainerblind.atlassian.net/browse/ATT-2466) (*Developer Testing Tools*)  
**Active Sprint**: `2026-43.7` | **Target Release**: `Sprint 2026-43.7`  
**Branch**: `feature/ATT-4669` | **Author**: AI Agent 1 (Implementer) | **Date**: 2026-10-22  

---

## 1. Problem Statement & Motivation
During Tier 2 Sprint Integration Gate (`./gradlew testDebugUnitTest`), 5 tests fail across 3 categories (contract collision, missing mock, teardown leakage), blocking CI release gating (`REQ-BLD-002`, `android-release.yml`, ATT-4025). The full suite of 3,840+ unit tests must achieve a 100% clean-room pass rate.

---

## 2. Root Cause Analysis (Forensic Investigation)
| Failing Test Class & Method | Root Cause & Failure Mechanism |
| :--- | :--- |
| `HardcodedStringsAndUnitsContractTest`<br>`> TrainingApplication todo method is marked as Deprecated` | **Contract Collision**: ATT-4026 added `@Deprecated todo()` check in `HardcodedStringsAndUnitsContractTest:168`. ATT-4031 (`REQ-ARC-003.1`) subsequently excised dead `todo()` and added `DeadCodeEliminationContractTest`, making ATT-4026's test obsolete. |
| `AltitudeAwareClusterMatchingTest`<br>`> testLearnFromWorkout_UpdatesAltitudeExtrema...` | **Missing Static Mock**: ATT-4024 excised test-only `catch (Throwable)` in `WorkoutClusterEngine.ensureStartingLocationAnchor()`. Invoking `learnFromWorkout` triggers unmocked `KnownLocationsDatabaseManager.getMyLocation()`, throwing unmocked `Location.setLatitude` RuntimeException. |
| `DevicesTabbedViewModelTest`<br>`> testNewDeviceFoundBroadcast_emitsResolved...` | **Uninjected DataStore / Mock Leakage**: Test constructs `DevicesTabbedViewModel(mockApplication, savedStateHandle)` omitting `settingsDataStore = mockSettingsDataStore`. Default constructor accesses `mockApplication.dataStore`, crashing in `PreferenceDataStoreFile` with NPE on un-stubbed `mockApplication.filesDir`. |
| `BackupRestoreViewModelLocalRestoreTest`<br>`> testRestoreFromLocalBackup_whenRestoreThrows...` | **Uncoordinated Dispatchers.IO Race**: `BackupRestoreViewModel.restoreFromLocalBackup` hardcodes `Dispatchers.IO`. Test `advanceUntilIdle()` only advances `testDispatcher`. Under full-suite CPU contention, async background coroutine finishes after assertion runs. |
| `TrackingTabsViewModelInteropTest`<br>`> testExitConfigurationSafeInTrackingMode` | **Teardown Coroutine Leakage**: Preceding test instantiates `TrackingTabsViewModel` whose `init` launches continuous `trackingMode.asFlow().collect` on `viewModelScope`. Test `@After` unmocks MockK and resets `Dispatchers.Main` without cancelling `viewModelScope`. Uncaught mock exception leaks into thread handler, failing the next test via `UncaughtExceptionsBeforeTest`. |

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  * Remove obsolete `TrainingApplication todo method is marked as Deprecated` test in `HardcodedStringsAndUnitsContractTest.kt`.
  * Supply `KnownLocationsDatabaseManager` mock and static teardown in `AltitudeAwareClusterMatchingTest.kt`.
  * Inject `mockSettingsDataStore` and stub `filesDir` in `DevicesTabbedViewModelTest.kt`.
  * Inject configurable `CoroutineDispatcher` (default `Dispatchers.IO`) in `BackupRestoreViewModel.kt` with `@JvmOverloads` (Rule 62) and pass `testDispatcher` in `BackupRestoreViewModelLocalRestoreTest.kt`.
  * Track and cancel `viewModelScope` on all test-created ViewModels in `TrackingTabsViewModelInteropTest.kt` (and related tracking tab tests) in `@After`.
  * Ensure 100% pass rate across all 3,840+ unit tests under `./gradlew testDebugUnitTest`.
* **Out-of-Scope Non-Goals**:
  * Refactoring production navigation, clustering algorithms, or DataStore storage structures.
  * Modifying existing requirements or adding new product features.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-TST-001` (*Deterministic JVM Test State Isolation*, Parent Epic `ATT-2466`), `REQ-BLD-002` (*Release CI Automated Test Gating*), `REQ-ARC-003` (*Dead Code Elimination*).
* **Historical Origin & Commit Trace**: `ATT-4024` (commit `040854ad`, Oct 2026), `ATT-4026` (commit `8529df98`, Oct 2026), and `ATT-4031` (commit `f04b501c`, Oct 2026).
* **Root Reason for Existing Formulation**: ATT-4024 removed test-only exception shields in `WorkoutClusterEngine`; ATT-4031 excised dead methods without back-porting cleanup to ATT-4026 contract tests; test-created ViewModels omitted explicit `viewModelScope.cancel()` in `@After`.
* **Preservation of Core Invariants**: No alterations to production runtime logic; 100% clean-room test pass rate; zero flakiness.

---

## 5. Architectural Strategy & High-Level Solution
1. **Contract Alignment**: Delete obsolete `todo()` test from `HardcodedStringsAndUnitsContractTest.kt`.
2. **Mock Scaffolding**: Add `mockkStatic(KnownLocationsDatabaseManager::class)` and relaxed mock in `AltitudeAwareClusterMatchingTest.kt`.
3. **DataStore Test Isolation**: Pass `mockSettingsDataStore` in `DevicesTabbedViewModelTest.kt` and stub `mockApplication.filesDir`.
4. **Dispatcher Injection**: Annotate `BackupRestoreViewModel` with `@JvmOverloads constructor(application: Application, private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO)`, using `ioDispatcher` in `viewModelScope.launch`, and inject `testDispatcher` in `BackupRestoreViewModelLocalRestoreTest.kt`.
5. **Coroutine Lifecycle Hygiene**: Track instantiated ViewModels in `TrackingTabsViewModelInteropTest.kt` (and adjacent tests) and invoke `it.viewModelScope.cancel()` in `@After` before `Dispatchers.resetMain()` and `unmockkAll()`.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**: Zero regression in production code; all 3,840+ unit tests pass deterministically; thread safety preserved.
* **Risk Rating**: **LOW** — Scoped entirely to test isolation scaffolding, obsolete contract excision, and standard ViewModel dispatcher injection without runtime behavior mutation.
