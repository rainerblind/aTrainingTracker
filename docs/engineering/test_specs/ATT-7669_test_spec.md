# Stage 2: Requirement & Test Specification - ATT-7669: Honor Per-Workout Health Connect Sync Checkbox in EditWorkoutDialog & Avoid Duplicate Sync Triggers

**Ticket**: [ATT-7669](https://atrainingtracker.atlassian.net/browse/ATT-7669)  
**Parent Epic**: [ATT-162](https://atrainingtracker.atlassian.net/browse/ATT-162) (*Cloud integration*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.6` | **Branch**: `feat/ATT-7669`  
**Requirement Mapping**: `REQ-REV-014` (`REQ-REV-014.1`..`REQ-REV-014.3`)  
**Test Spec ID**: `TST-REV-015` (`TST-REV-015.1`..`TST-REV-015.4`)  
**Author**: AI Agent 2 (Flash Agent / Pro Agent Pairing) | **Date**: 2026-11-14  

---

## 1. Requirement Specification (REQ-REV-014)

### 1.1 Problem Statement & Rationale
In `EditWorkoutScreen.kt` (`EditWorkoutDialog`), the user is presented with a per-workout checkbox (`Zu Health Connect synchronisieren` / `syncToHealthConnect`), initialized from `TrainingApplication.syncToHealthConnect()`. However, `ExportManager.exportWorkout()` historically evaluated only the global setting `TrainingApplication.syncToHealthConnect()` while `EditWorkoutScreen` simultaneously triggered an independent `HealthConnectManager.syncWorkoutAsync()` call on save. This caused duplicate concurrent sync requests on save and prevented users from opting out of Health Connect sync for individual workouts. Propagating the per-workout choice into `WorkoutData`, binding it in `EditWorkoutViewModel`, delegating single-trigger execution exclusively to `ExportManager`, and honoring user opt-out establishes a single source of truth and prevents race conditions.

### 1.2 Attributes
- **Priority**: Medium | **Type**: Functional | **Verification Method**: Unit, Integration

### 1.3 Functional & Architectural Requirements
* **REQ-REV-014.1** (*Per-Workout Health Connect State in Model & ViewModel*):
  - `WorkoutData` SHALL define a nullable property `val syncToHealthConnect: Boolean? = null`.
  - `EditWorkoutViewModel` SHALL expose `fun updateSyncToHealthConnect(isChecked: Boolean)` to update `workoutData.value.syncToHealthConnect`.
  - Upon initial workout load in `EditWorkoutViewModel`, if `syncToHealthConnect == null`, `syncToHealthConnect` SHALL be initialized to `TrainingApplication.syncToHealthConnect()`.
* **REQ-REV-014.2** (*EditWorkoutScreen UI Binding & Single Responsibility Save*):
  - In `EditWorkoutScreen.kt`, the Health Connect sync row checkbox SHALL bind its checked state to `workoutData?.syncToHealthConnect ?: TrainingApplication.syncToHealthConnect()`.
  - Checkbox toggle events SHALL dispatch to `viewModel.updateSyncToHealthConnect(it)`.
  - `EditWorkoutDialog.onSave` SHALL NOT invoke `HealthConnectManager.syncWorkoutAsync()` directly from the UI layer; all save and export orchestration SHALL delegate exclusively to `viewModel.saveChanges()`.
* **REQ-REV-014.3** (*ExportManager Pipeline Opt-Out Honor & Single Trigger*):
  - When `ExportManager.exportWorkout(WorkoutData workoutData)` executes, it SHALL evaluate `workoutData.getSyncToHealthConnect()`.
  - If `workoutData.getSyncToHealthConnect()` is `false`, Health Connect synchronization SHALL NOT be triggered, regardless of `TrainingApplication.syncToHealthConnect()`.
  - If `workoutData.getSyncToHealthConnect()` is `true`, Health Connect synchronization SHALL be invoked exactly once via `HealthConnectManager.syncWorkoutAsync(mContext, workoutData, force = true)`.
  - If `workoutData.getSyncToHealthConnect()` is `null`, `ExportManager` SHALL preserve default legacy behavior by evaluating `TrainingApplication.syncToHealthConnect()` with `force = false`.

### 1.4 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Opt-Out Skips Sync)**: *Given* global Health Connect sync is enabled (`TrainingApplication.syncToHealthConnect() == true`), *When* the user unchecks `Zu Health Connect synchronisieren` in `EditWorkoutDialog` and taps Save, *Then* `ExportManager.exportWorkout` does NOT trigger Health Connect synchronization.
* **Criterion 2 (Checked Syncs Exactly Once)**: *Given* `Zu Health Connect synchronisieren` is checked in `EditWorkoutDialog`, *When* the user edits workout metadata and taps Save, *Then* `HealthConnectManager.syncWorkoutAsync` is invoked exactly once with the updated `WorkoutData`.
* **Criterion 3 (Null Fallback Preserves Existing Operations)**: *Given* a workout export triggered from background crop or lap editing where `workoutData.syncToHealthConnect` is `null`, *When* `ExportManager.exportWorkout` executes, *Then* it synchronizes to Health Connect if and only if `TrainingApplication.syncToHealthConnect()` is true.

### 1.5 System Invariants & Scope Bounding
* Zero SQLite table schema modifications (`DB_VERSION` preserved).
* Existing background auto-sync, crop export, and lap edit export behavior preserved.
* Strava upload exclusion (`uploadToStrava == 0`) and file format exports unaffected.

---

## 2. Test Specification (TST-REV-015)

### Test Case 1: `testEditWorkoutViewModel_initializesAndUpdatesSyncToHealthConnect` (`TST-REV-015.1`)
* **Scope**: Unit Test (`EditWorkoutViewModelTest.kt`)
* **Procedure**:
  1. Initialize `EditWorkoutViewModel` with sample workout data having `syncToHealthConnect = null`.
  2. Verify loaded `workoutData.value?.syncToHealthConnect` matches `TrainingApplication.syncToHealthConnect()`.
  3. Invoke `viewModel.updateSyncToHealthConnect(false)`, assert `workoutData.value?.syncToHealthConnect == false`.
  4. Invoke `viewModel.updateSyncToHealthConnect(true)`, assert `workoutData.value?.syncToHealthConnect == true`.

### Test Case 2: `testEditWorkoutScreen_removesDirectSyncCallInOnSave` (`TST-REV-015.2`)
* **Scope**: Contract / UI Test (`EditWorkoutClassificationLayoutContractTest.kt`)
* **Procedure**:
  1. Inspect `EditWorkoutScreen.kt` AST/source.
  2. Verify `HealthConnectManager.syncWorkoutAsync` is NOT called within `EditWorkoutDialog` or `AppDialogActions.SaveCancel.onSave`.
  3. Verify `updateSyncToHealthConnect` is bound to the Health Connect toggleable row.

### Test Case 3: `testExportManager_honorsPerWorkoutSyncOptOutAndForceSync` (`TST-REV-015.3`)
* **Scope**: Unit Test (`EditWorkoutHealthConnectSyncTest.kt`)
* **Procedure**:
  1. Setup mock `ExportManager` and `HealthConnectManager`.
  2. When `TrainingApplication.syncToHealthConnect() == true` and `workoutData.syncToHealthConnect == false`: assert `syncWorkoutAsync` is NEVER called.
  3. When `workoutData.syncToHealthConnect == true`: assert `syncWorkoutAsync(mContext, workoutData, force = true)` is called exactly once.
  4. When `TrainingApplication.syncToHealthConnect() == true` and `workoutData.syncToHealthConnect == null`: assert `syncWorkoutAsync(mContext, workoutData, force = false)` is called once.
  5. When `TrainingApplication.syncToHealthConnect() == false` and `workoutData.syncToHealthConnect == null`: assert `syncWorkoutAsync` is NEVER called.

### Test Case 4: `testWorkoutRepository_preservesSyncToHealthConnectOnSave` (`TST-REV-015.4`)
* **Scope**: Unit Test (`EditWorkoutHealthConnectSyncTest.kt`)
* **Procedure**:
  1. Call `WorkoutRepository.saveWorkout(workoutData.copy(syncToHealthConnect = false))` or `true`.
  2. Assert `exportManager.exportWorkout` receives the instance with the exact `syncToHealthConnect` value intact.

---

## 3. Traceability Matrix

| Test Case | Scope | Target Component | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-REV-015.1` | Unit | `EditWorkoutViewModel.kt` | `REQ-REV-014.1` | Specified |
| `TST-REV-015.2` | Contract / UI | `EditWorkoutScreen.kt` | `REQ-REV-014.2` | Specified |
| `TST-REV-015.3` | Unit | `ExportManager.java` | `REQ-REV-014.3` | Specified |
| `TST-REV-015.4` | Unit / Repo | `WorkoutRepository.kt` | `REQ-REV-014.1`, `REQ-REV-014.3` | Specified |
