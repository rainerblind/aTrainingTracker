# Stage 1 Analysis: ATT-432 - Access to Database only via Repository (Phase A: Residual UI & ViewModel Cleanup)

**Ticket**: [ATT-432](https://rainerblind.atlassian.net/browse/ATT-432)  
**Sub-task**: `[Analysis]`  
**Parent Epic**: [ATT-431](https://rainerblind.atlassian.net/browse/ATT-431) (*Clean Code*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-46.1`  
**Branch**: `feature/ATT-432`  
**Author**: AI Agent 1 (Implementer, `subagent_pro`)  
**Date**: 2026-11-10  

---

## 1. Problem Statement & Motivation
Direct SQLite database manager access (`*DatabaseManager.getInstance()`) historically permeated the application. While 18 domain repositories encapsulate database access for Compose UI, residual direct calls remain in 4 UI and ViewModel classes:
1. `MainActivityWithNavigation.kt`: 6 direct calls to `DevicesDatabaseManager`, `TrackingViewsDatabaseManager`, and `WorkoutSummariesDatabaseManager` (low-battery checks, filter generation, unfinished workout recovery).
2. `ImportBackupTabsScreen.kt`: 4 direct calls to `SportTypeDatabaseManager` (sport name, UI type resolution).
3. `EquipmentViewModel.kt`: 3 direct constructor manager instances (`SportTypeDatabaseManager`, `DevicesDatabaseManager`, `WorkoutSummariesDatabaseManager`).
4. `ControlTrackingViewModel.kt`: 2 direct calls to `DevicesDatabaseManager` (cold-start state seeding) and `WorkoutSummariesDatabaseManager` (unfinished workout check on start).

---

## 2. Root Cause Analysis & Independent Peer Review (4-Augen-Prinzip)
* **Root Cause**: Partial migration to MVVM repositories across previous sprints left these 4 UI/ViewModel components directly instantiating database helper singletons.
* **Independent Peer Review & Verification**:
  - *Call-Site Audit*: Forensic audit confirmed exact call sites: `MainActivityWithNavigation.kt:419, 606, 926, 977, 1021, 1165, 1180`; `ImportBackupTabsScreen.kt:888, 889, 938, 1101`; `EquipmentViewModel.kt:82, 83, 84`; `ControlTrackingViewModel.kt:80, 112, 127, 187`.
  - *Repository Readiness*: Domain repositories (`WorkoutRepository`, `DeviceDataRepository`, `SportTypesRepository`, `TrackingViewsRepository`) already exist as application singletons and manage dispatchers (`Dispatchers.IO`). Missing repository convenience delegates (`hasUnfinishedWorkout`, `discardOrFinishUnfinishedWorkout`, `getAllFilterData`, `getSportTypes`, `getUIName`) will be exposed directly on the repositories without breaking existing APIs.
  - *Constructor Test Invariants*: `EquipmentViewModel` tests (`EquipmentViewModelLifecycleTest`, etc.) pass mock managers. Backward-compatible secondary constructor overloads preserve full test harness compatibility.

---

## 3. Scope Grounding & Complexity Split (ATT-1250, REQ-PRO-029)
* **In-Scope Goals (Phase A Only — 2 SP)**:
  - Migrate all direct `DatabaseManager.getInstance()` calls in `MainActivityWithNavigation.kt`, `ImportBackupTabsScreen.kt`, `EquipmentViewModel.kt`, and `ControlTrackingViewModel.kt` to repository delegates.
  - Expose clean repository delegates on `WorkoutRepository`, `DeviceDataRepository`, `SportTypesRepository`, and `TrackingViewsRepository`.
* **Out-of-Scope Non-Goals (Strict Guardrail)**:
  - `ATT-6426` (Phase B: Engines, Exporters & Migration Layer — `WorkoutClusterEngine`, `LegacyImportEngine`, `BackupManager`, `StravaUploader`, file writers).
  - `ATT-6427` (Phase C: Live Recording Services — `TrackerService` & `BANALService`).
  - No database schema migrations or Room conversions (SQLiteOpenHelper stability preserved).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Refined Requirements**: Refines `REQ-UI-507` (*Repository-Only UI Database Access Layer*) in living shard `docs/requirements/req_ui_design_system.md` (tested by `TST-UI-507`), amending architectural presentation standards of `REQ-UI-010` (Equipment), `REQ-UI-257` (Reactive Links), and `REQ-UI-259` (Paired Remote Devices).
* **Historical Origin**: `ATT-510`, `ATT-3390`, `ATT-3998`.
* **Root Reason for Existing Formulation**: Quick prototyping in UI controllers prior to unified repository layer introduction.
* **Preservation of Core Invariants**: Concurrency thread dispatching (`Dispatchers.IO`), battery snackbar thresholds, and unfinished workout lifecycle flows remain 100% identical.

---

## 5. Architectural Strategy & High-Level Solution
* Add necessary query delegates to `WorkoutRepository`, `DeviceDataRepository`, `SportTypesRepository`, and `TrackingViewsRepository`.
* Refactor `MainActivityWithNavigation.kt`, `ImportBackupTabsScreen.kt`, `EquipmentViewModel.kt`, and `ControlTrackingViewModel.kt` to consume repositories.
* Maintain backward-compatible constructors and verify existing tests remain green.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**: Zero UI regressions; zero database schema alterations; zero changes to sensor tracking lifecycle or battery alert thresholds.
* **Risk Rating**: **LOW** (Pure architectural delegation to existing repositories within presentation layer).
