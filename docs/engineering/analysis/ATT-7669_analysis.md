# Stage 1 Analysis: ATT-7669 - Honor Per-Workout Health Connect Sync Checkbox in EditWorkoutDialog and Avoid Duplicate Sync Triggers

**Ticket**: [ATT-7669](https://atrainingtracker.atlassian.net/browse/ATT-7669)  
**Parent Epic**: [ATT-162](https://atrainingtracker.atlassian.net/browse/ATT-162) (*Cloud integration*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.6`  
**Branch**: `feat/ATT-7669` | **Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-14  

---

## 1. Problem Statement & Motivation
In `EditWorkoutScreen.kt` (`EditWorkoutDialog`), the user is presented with a per-workout checkbox (`Zu Health Connect synchronisieren` / `syncToHealthConnect`), initialized from `TrainingApplication.syncToHealthConnect()`.

However, when the user taps **Save**:
1. `EditWorkoutDialog` calls `viewModel.saveChanges()` $\rightarrow$ `WorkoutRepository.saveWorkout(userEditedWorkout)` $\rightarrow$ `ExportManager.exportWorkout(userEditedWorkout)`.
2. Inside `ExportManager.exportWorkout(workoutData)` (lines 209–211), it checks the global preference `if (TrainingApplication.syncToHealthConnect()) HealthConnectManager.syncWorkoutAsync(mContext, workoutData)` unconditionally.
3. In addition, `EditWorkoutDialog` (lines 131–139) *also* directly invokes `HealthConnectManager.syncWorkoutAsync(localContext.applicationContext, data, force = true)`.

As a consequence:
- If the user **unchecks** `Zu Health Connect synchronisieren` in `EditWorkoutDialog`, `ExportManager.exportWorkout()` still synchronizes the workout to Health Connect anyway because global `TrainingApplication.syncToHealthConnect()` is `true`.
- If the user leaves `Zu Health Connect synchronisieren` **checked**, `HealthConnectManager.syncWorkoutAsync` is triggered **twice concurrently** for the same save action (once from `EditWorkoutDialog` with stale pre-save `workoutData`, and once from `ExportManager.exportWorkout` with `userEditedWorkout`).

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **RCA 1 (Decoupled Per-Workout State in UI Layer)**: In `EditWorkoutScreen.kt:119-121`, `var syncToHealthConnect by remember { mutableStateOf(...) }` holds the toggle in isolated composable state without propagating it to `WorkoutData` or `EditWorkoutViewModel`.
* **RCA 2 (Bypassed Pipeline & Redundant Trigger in UI onSave)**: In `EditWorkoutScreen.kt:131-139`, `onSave` manually executes `HealthConnectManager.syncWorkoutAsync(..., force = true)` right after `viewModel.saveChanges()`, violating `ExportManager`'s single ownership of export/sync triggers and firing race conditions.
* **RCA 3 (Lack of Per-Workout Inspection in Export Pipeline)**: In `ExportManager.java:209-211`, `exportWorkout()` queries only the global preference `TrainingApplication.syncToHealthConnect()`, unaware of any per-workout override carried on `WorkoutData`.
* **Independent Peer Review & Verification**:
  * Verified `EditWorkoutScreen.kt#L119-139`: Confirmed local `remember` toggle state and explicit concurrent `syncWorkoutAsync` invocation in `onSave`.
  * Verified `ExportManager.java#L209-211`: Confirmed unconditional evaluation of global `TrainingApplication.syncToHealthConnect()` without consulting `workoutData`.
  * Verified `WorkoutRepository.kt#L4285-4345`: Confirmed `saveWorkout` passes `userEditedWorkout` directly to `exportManager.exportWorkout(userEditedWorkout)`.
  * Verified `HealthConnectManager.kt#L165-185`: Confirmed `syncWorkout(..., force)` supports the `force` flag to bypass global settings when an explicit user sync is triggered.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  1. Add optional per-workout `syncToHealthConnect: Boolean? = null` field to `WorkoutData`.
  2. Elevate `syncToHealthConnect` state into `EditWorkoutViewModel` (initialized from `TrainingApplication.syncToHealthConnect()`, mirroring `uploadToStrava`).
  3. Update `EditWorkoutScreen.kt` to bind the Health Connect checkbox to ViewModel state (`viewModel.updateSyncToHealthConnect(it)`).
  4. Remove redundant `HealthConnectManager.syncWorkoutAsync` call from `EditWorkoutScreen.kt`'s `onSave` handler.
  5. Update `ExportManager.java:209-211` to inspect `workoutData.getSyncToHealthConnect()`: if non-null, honor it (passing `force = Boolean.TRUE.equals(syncHc)`); if null, fall back to global `TrainingApplication.syncToHealthConnect()`.
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  * Zero SQLite schema changes or database version bumps (transient session export override carried on `WorkoutData`).
  * Health Connect `ExerciseRoute` timestamp deduplication and `Community` export status UI (tracked in separate tickets).
  * Zero alterations to Strava upload or file export routines.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit (REQ-PRO-022)
* **Original Requirement ID & Target**: Refines `REQ-REV-013.2` (*Grouped Cloud Sync Card in EditWorkoutScreen*) and `REQ-SET-103` (*Health Connect Settings UI, Manual Re-Sync & Write Idempotency*). Formally codified as `REQ-REV-014` in `docs/requirements/req_aftermath_reviews.md`.
* **Historical Origin & Commit Trace**: `REQ-REV-013` added in `ATT-7517` (commit `6989ff14`); `REQ-SET-103` introduced in `ATT-3052` (Sprint 2026-44.5).
* **Root Reason for Existing Formulation**: In `ATT-7517`, the UI checkbox was grouped into the new card container, but the saving pipeline was not refactored—leaving the prototype call in `EditWorkoutScreen.onSave` while `ExportManager` retained its earlier automated hook.
* **Preservation of Core Invariants**: Automated background export, lap editing, crop export, Strava upload, and global Health Connect settings toggle remain 100% preserved.

---

## 5. Change Impact Analysis (SUP.10)
* **Affected Requirements**: Formally specified as `REQ-REV-014` (refining `REQ-REV-013.2` and `REQ-SET-103`).
* **Affected Architecture Elements**: `WorkoutData.kt`, `EditWorkoutViewModel.kt`, `EditWorkoutScreen.kt`, `WorkoutRepository.kt`, `ExportManager.java`.
* **Verification Impact & Test Surface**: `EditWorkoutViewModelTest.kt`, `EditWorkoutClassificationLayoutContractTest.kt`, `ExportManagerTest` / new `EditWorkoutHealthConnectSyncTest.kt`.
* **Risk Assessment & Invariants**: Low risk. Pipeline routing and UI state consolidation; preserves default behavior when flag is null.

---

## 6. Architectural Strategy & High-Level Solution
1. **Model Extension (`WorkoutData.kt`)**: Add `val syncToHealthConnect: Boolean? = null` to `WorkoutData`.
2. **ViewModel State (`EditWorkoutViewModel.kt`)**: Add `updateSyncToHealthConnect(isChecked: Boolean)` and initialize `syncToHealthConnect` on loaded workout from `TrainingApplication.syncToHealthConnect()`.
3. **UI Binding & Clean Save (`EditWorkoutScreen.kt`)**: Bind Health Connect checkbox to `workoutData?.syncToHealthConnect` and remove redundant `syncWorkoutAsync` from `AppDialogActions.SaveCancel.onSave`.
4. **Repository In-Memory Sync (`WorkoutRepository.kt`)**: Carry `syncToHealthConnect = userEditedWorkout.syncToHealthConnect` in memory update.
5. **Pipeline Honor (`ExportManager.java`)**: Check `workoutData.getSyncToHealthConnect()`. If `false`, skip sync. If `true`, invoke `syncWorkoutAsync(mContext, workoutData, true)`. If `null`, fall back to global preference.

---

## 7. System Invariants & Risk Assessment
* **Core Invariants**:
  1. Default export behavior when `syncToHealthConnect == null` is unchanged.
  2. Unchecking in dialog skips HC sync completely during save.
  3. Checking in dialog triggers sync exactly once with updated workout data.
  4. 100% backward compatibility for all existing callers of `exportWorkout()`.
* **Risk Rating**: **LOW** (Targeted pipeline and UI state alignment).
