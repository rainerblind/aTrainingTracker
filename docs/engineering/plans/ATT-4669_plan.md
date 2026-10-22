# Stage 3: Implementation Plan - ATT-4669: Fix Full-Suite Unit Test Failures

**Ticket**: [ATT-4669](https://rainerblind.atlassian.net/browse/ATT-4669)  
**Sub-task**: [ATT-4674](https://rainerblind.atlassian.net/browse/ATT-4674) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-2466](https://rainerblind.atlassian.net/browse/ATT-2466) (*Developer Testing Tools*)  
**Target Release**: `Sprint 2026-43.7` | **Active Sprint**: `2026-43.7`  
**Requirement Mapping**: `REQ-TST-003` (*Deterministic Test State Isolation, Scaffolding Parity & Coroutine Teardown*)  
**Test Mapping**: `TST-TST-003`  
**Branch**: `feature/ATT-4669` | **Author**: AI Agent 1 (Implementer) | **Date**: 2026-10-22  

---

## 1. Problem Description & Background
During Tier 2 Sprint Integration Gate (`./gradlew testDebugUnitTest`), 5 tests fail across 3 categories (contract collision, missing static mock, teardown coroutine leakage / uncoordinated dispatchers). All 3,840+ unit tests must achieve 100% clean-room execution.

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-TST-003` (clauses 1–6) extending `REQ-TST-001`, `REQ-BLD-002`, and `REQ-ARC-003`.
* **Test Mapping**: `TST-TST-003` (subtests 1–6) mapped in `docs/tests.md`.

---

## 3. System Invariants & Preserved Behavior
1. **Zero Runtime Mutation**: Production clustering, backup logic, and data schemas remain strictly unmodified.
2. **Rule 62 Bytecode Compatibility**: `BackupRestoreViewModel` uses `@JvmOverloads constructor(...)` for dispatcher injection.
3. **Clean-Room Test Determinism**: Tests pass independently and sequentially in any JVM execution order.
4. **Human Decision Gate**: Parent ticket advances to `Final Review (Human)`; never to `Erledigt`.

---

## 4. Proposed Architectural Changes
* **Architecture**: Production ViewModels follow constructor dependency injection for coroutine dispatchers; test harnesses maintain deterministic `@Before`/`@After` lifecycle isolation and coroutine scope cancellation.
* **UI Consistency**: No UI changes (test infrastructure & ViewModel constructor injection only).

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

| Step | Target Files & Exact Lines | Description | Targeted Test Command |
| :--- | :--- | :--- | :--- |
| **Step 1** | `app/src/test/.../localization/HardcodedStringsAndUnitsContractTest.kt:165-170` | Delete obsolete `TrainingApplication todo method is marked as Deprecated` test method. | `./gradlew testDebugUnitTest --tests "...localization.HardcodedStringsAndUnitsContractTest"` |
| **Step 2** | `app/src/test/.../database/AltitudeAwareClusterMatchingTest.kt:321-376` | Supply `mockkStatic(KnownLocationsDatabaseManager::class)` and relaxed mock in `testLearnFromWorkout`. | `./gradlew testDebugUnitTest --tests "...database.AltitudeAwareClusterMatchingTest"` |
| **Step 3** | `app/src/test/.../devicetabs/DevicesTabbedViewModelTest.kt:70-95, 248-251` | Add `tempFolder` rule for `mockApplication.filesDir` in `setUp()`; inject `mockSettingsDataStore` in `testNewDeviceFoundBroadcast`. | `./gradlew testDebugUnitTest --tests "...devicetabs.DevicesTabbedViewModelTest"` |
| **Step 4** | `app/src/main/.../migration/BackupRestoreViewModel.kt:47, 456-476`<br>`app/src/test/.../migration/BackupRestoreViewModelLocalRestoreTest.kt:207, 230, 249` | Add `@JvmOverloads constructor(..., ioDispatcher = Dispatchers.IO)`; use `ioDispatcher` in `restoreFromLocalBackup`; inject `testDispatcher` in tests. | `./gradlew testDebugUnitTest --tests "...migration.BackupRestoreViewModel*"` |
| **Step 5** | `app/src/test/.../navigation/TrackingTabsViewModelInteropTest.kt:66-116, 101-106` | Track created ViewModels in `createdViewModels` list; invoke `it.viewModelScope.cancel()` in `@After` before `Dispatchers.resetMain()` and `unmockkAll()`. | `./gradlew testDebugUnitTest --tests "...navigation.TrackingTabsViewModelInteropTest"` |
| **Step 6** | Full Unit Test Suite | Execute clean-room full suite regression check across all 3,840+ unit tests with zero flakiness. | `./gradlew :app:testDebugUnitTest` |

---

## 6. Verification & Rollback Plan
* **Verification**: Execute targeted tests incrementally (Steps 1–5), then execute full clean-room suite `./gradlew :app:testDebugUnitTest` and `python3 tools/verify_requirement_governance.py`.
* **Rollback**: Atomic branch revert (`git checkout feature/ATT-4669 && git reset --hard HEAD`).
