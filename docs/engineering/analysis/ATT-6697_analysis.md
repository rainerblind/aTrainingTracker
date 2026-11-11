# Stage 1 Analysis: ATT-6697 - UI & Activities Layer: Sweep Remaining Direct DatabaseManager/DbHelper Call Sites

**Ticket**: [ATT-6697](https://rainerblind.atlassian.net/browse/ATT-6697)  
**Sub-task**: `[Analysis]` (Created on submission)  
**Parent Epic**: [ATT-431](https://rainerblind.atlassian.net/browse/ATT-431) (*Clean Code*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `sprint/2026-46.2`  
**Branch**: `feature/ATT-6697` | **Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-11  

---

## 1. Problem Statement & Motivation
Phase A (`ATT-432`) decoupled 4 prominent UI entry points (`MainActivityWithNavigation`, `ImportBackupTabsScreen`, `EquipmentViewModel`, `ControlTrackingViewModel`). However, 8 residual call sites across `ui/` and `activities/` still instantiate SQLite managers via `*DatabaseManager.getInstance(...)` or `*DbHelper.getInstance(...)` directly. `RepositoryUiCleanlinessContractTest` currently guards only those 4 files, leaving the presentation layer vulnerable to creeping database coupling.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
Grep sweeps independently confirmed all 8 call sites cited in `ATT-6697` plus 1 redundant manager in `EditWorkoutViewModel`:
1. `WorkoutClustersViewModel.kt:597`: Calls `KnownLocationsDatabaseManager.getInstance(...)` directly despite already having `knownLocationsRepo` injected.
2. `TrackingTabsViewModel.kt:574,597`: Calls `KnownLocationsDatabaseManager.getInstance(...)` for `updateLocation` and `upsertLocationByGeofence` despite having `knownLocationsRepo`.
3. `AthleteProfileViewModel.kt:285,298`: Calls `WorkoutSummariesDatabaseManager.getInstance(...)` for load & cardio dynamics trends.
4. `StravaActivitySection.kt:67`: Calls `SegmentsDatabaseManager.getInstance(...)` for starred segment summaries.
5. `ExportStatusDataProvider.kt:35`: Calls `ExportStatusDatabaseManager.getInstance(...)` for export rows.
6. `WorkoutDataMapper.kt:64,115,264`: Calls `KnownLocationsDatabaseManager`, `WorkoutClusterDatabaseManager`, and `LapsDatabaseManager` `getInstance` methods.
7. `MapInitialLocationResolver.kt:65,75,86`: Accepts raw database managers as default fallbacks.
8. `ImportBackupTabsScreen.kt:1101`: Calls `EquipmentDbHelper.getInstance(...)` for local equipment list.
9. `MainActivityWithNavigation.kt:919`: Calls `ActiveDevicesDbHelper.getInstance(...)` for workout active device IDs.
10. `EditWorkoutViewModel.kt:55`: Redundantly instantiates `SportTypeDatabaseManager` alongside `sportTypesRepository`.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  * Route all 8 (+1) call sites through domain repositories (`KnownLocationsRepository`, `WorkoutRepository`, `SegmentsRepository`, `ExportStatusRepository`, `EquipmentRepository`, `DeviceDataRepository`, `SportTypesRepository`).
  * Expose missing delegates on domain repositories (`upsertLocationByGeofence`, `getWorkoutTrainingLoadRecords`, `getCardioPowerDynamicsFitnessTrendSamples`, `allSegmentSummaries`, `getActiveDeviceIdsForWorkout`).
  * Upgrade `RepositoryUiCleanlinessContractTest` to a package-wide boundary test across `ui/` and `activities/` asserting zero `*.getInstance(...)` calls for `*DatabaseManager` / `*DbHelper` (excluding `*Repository.kt` and `MapFragmentWithTrackViewModel.kt` reserved for `ATT-6692`).
* **Out-of-Scope Non-Goals**:
  * Refactoring `MapFragmentWithTrackViewModel.kt`'s `ClimbsDatabaseManager` (tracked in `ATT-6692`).
  * Database schema alterations or query logic modifications.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-UI-507` (*Repository-Only UI Database Access Layer*) in `docs/requirements/req_ui_design_system.md`.
* **Historical Origin & Commit Trace**: Commit `d650ce5a` (`ATT-432: Phase A: Residual UI & ViewModel Cleanup`).
* **Root Reason for Existing Formulation**: Scoped strictly to the first 4 UI classes to deliver initial architectural cleanup without broadening test blast radius.
* **Preservation of Core Invariants**: Extends `REQ-UI-507` to package-wide UI/activities coverage (`REQ-UI-507.6`). All underlying SQLite queries, threading (`Dispatchers.IO`), and UI lifecycles remain identical.

---

## 5. Architectural Strategy & High-Level Solution
* **Repositories as Single Source of Truth**: Presentation components interact exclusively with domain repositories.
* **Package-Wide Contract Guard**: `RepositoryUiCleanlinessContractTest` recursively scans `ui/` and `activities/` ASTs to prevent regression.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**:
  1. 100% parity on all domain queries; zero behavioral changes in ViewModels or Composables.
  2. All repository disk I/O remains off the main thread (`Dispatchers.IO`).
  3. Clean architecture boundary: UI $\to$ Repository $\to$ DatabaseManager.
* **Risk Rating**: **MEDIUM** (Multi-file presentation sweep touching 8 components and ViewModels, but operations are pure delegation refactors without logic changes; 2 SP).
