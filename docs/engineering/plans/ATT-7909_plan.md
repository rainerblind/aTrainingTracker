# Stage 3: Implementation Plan - ATT-7909: Java Core Migration: Modernize SQLite database managers via repository interfaces

**Ticket**: [ATT-7909](https://rainerblind.atlassian.net/browse/ATT-7909)  
**Parent Epic**: [ATT-431](https://rainerblind.atlassian.net/browse/ATT-431) (*Clean Code*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.8`  
**Requirement Mapping**: `REQ-ARC-009` (*Modernize SQLite Database Managers via Repository Interfaces*)  
**Test Mapping**: `TST-ARC-009`  
**Risk Classification**: `STANDARD` (pure repository encapsulation & ViewModel decoupling; underlying managers remain Keep in Java)  
**Author**: AI Agent 1 (Implementer) | **Date**: 2026-11-16  

---

## 1. Problem Description & Background
Under ADR-001 (`REQ-ARC-005`, Tier 4 Persistence), legacy SQLite database managers (`WorkoutSummariesDatabaseManager`, `WorkoutSamplesDatabaseManager`, `DevicesDatabaseManager` — 6,006 LOC) manage high-frequency telemetry and workout persistence. In compliance with the PO decision from retro `ATT-7749`, the underlying database managers remain in Java to prevent regressions in sensor/GPS ingestion. This plan modernizes their boundary:
- Eliminates direct SQLite manager instantiation in ViewModels (`DevicesTabbedViewModel`, `SportTypeViewModel`).
- Encapsulates `getSportTypeStats` and `getSportTypeStatsForPeriod` in `WorkoutRepository` on `Dispatchers.IO`.
- Null-hardens cursor consumption in `WorkoutRepository` and `PeriodsRepository` (`?.use {} ?: emptyList()`).
- Removes public `DevicesDatabaseManager` parameters from `TrackingViewsRepository`.

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-ARC-009` (`REQ-ARC-009.1`–`REQ-ARC-009.4`)
* **Test Mapping**: `TST-ARC-009` (`TST-ARC-009.1`–`TST-ARC-009.5`)

---

## 3. System Invariants & Preserved Behavior
1. **Underlying Database Engine Invariance**: 6,006 LOC SQLite managers remain in Java (`Keep`). Zero schema, table, or PRAGMA changes.
2. **Merged Features Regression Parity**: `ATT-8000` (Polar sync pending methods in `WorkoutSummariesDatabaseManager`) and `ATT-7875` (crash recovery accumulator preservation in `TrackerService`/`WorkoutRepository`) remain 100% intact.
3. **Thread Safety & Dispatcher Confinement**: All asynchronous queries in repositories execute strictly on `Dispatchers.IO`.
4. **Try-With-Resources Invariance**: `TryWithResourcesCursorAuditTest` and `RepositoryCleanlinessContractTest` maintain 100% pass rate.
5. **No Broken Windows**: Downward ratcheting enforced in `ArchitectureConformanceContractTest`.

---

## 4. Proposed Architectural Changes
* **`WorkoutRepository.kt`**: Add `getSportTypeStats(sportTypeId)` and `getSportTypeStatsForPeriod(sportTypeId, startS, endS)` on `Dispatchers.IO`. Null-harden cursor acquisition in `loadAllWorkouts()`.
* **`PeriodsRepository.kt`**: Null-harden `getCursorForAllWorkouts()` and `getWorkoutsInRangeCursor()` consumption.
* **`SportTypeViewModel.kt`**: Inject `WorkoutRepository`, replace direct `WorkoutSummariesDatabaseManager` instantiation, and route stats calls through repository.
* **`DevicesTabbedViewModel.kt`**: Route `getDeviceName` and `getDeviceType` through `DeviceDataRepository`, eliminating direct `DevicesDatabaseManager.getInstance` calls.
* **`TrackingViewsRepository.kt`**: Encapsulate `DevicesDatabaseManager` usage, preventing manager exposure in public signatures.
* **`RepositoryCleanlinessContractTest.kt`**: Add contract test assertions enforcing zero DB manager instantiation in `DevicesTabbedViewModel` and `SportTypeViewModel`.
* **UI Consistency**: No UI changes (pure architectural repository and ViewModel refactoring).

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Encapsulate Sport Type Stats & Null-Harden Cursors in Repositories
* Files: `WorkoutRepository.kt`, `PeriodsRepository.kt`.
* Changes: Expose `getSportTypeStats` and `getSportTypeStatsForPeriod` on `Dispatchers.IO`. Guard `cursor?.use {} ?: emptyList()`.

### Step 2: Decouple SportTypeViewModel from WorkoutSummariesDatabaseManager
* Files: `SportTypeViewModel.kt`.
* Changes: Remove `dbSummariesManager` instantiation; route queries through `workoutRepository`.

### Step 3: Decouple DevicesTabbedViewModel from DevicesDatabaseManager
* Files: `DevicesTabbedViewModel.kt`.
* Changes: Replace `DevicesDatabaseManager.getInstance(...)` with `deviceDataRepository.getDeviceName(...)` and `getDeviceType(...)`.

### Step 4: Encapsulate TrackingViewsRepository Signatures
* Files: `TrackingViewsRepository.kt`.
* Changes: Make internal `devicesDbManager` default parameter private; shield callers from passing raw manager instances.

### Step 5: Architectural Contract Testing & Verification
* Files: `RepositoryCleanlinessContractTest.kt`, `ArchitectureConformanceContractTest.kt`.
* Changes: Add assertions prohibiting `DatabaseManager.getInstance` in `DevicesTabbedViewModel` and `SportTypeViewModel`; update ratcheted allow-list.
* Targeted Unit Test Command:
  `./gradlew :app:testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.RepositoryServiceCleanlinessContractTest" --tests "com.atrainingtracker.architecture.TryWithResourcesCursorAuditTest" --tests "com.atrainingtracker.trainingtracker.build.ArchitectureConformanceContractTest" --tests "com.atrainingtracker.banalservice.ui.sporttype.*" --tests "com.atrainingtracker.banalservice.ui.devices.devicetabs.*"`

---

## 6. Verification & Rollback Plan
* **Verification**: Targeted unit tests, TryWithResources audit, architecture conformance contract tests, and requirement governance audit (`python3 tools/verify_requirement_governance.py`).
* **Rollback**: Branch isolation allows complete discard via `git checkout -- .` before merge.
