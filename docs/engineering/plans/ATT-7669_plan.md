# Stage 3: Implementation Plan - ATT-7669: Honor Per-Workout Health Connect Sync Checkbox in EditWorkoutDialog & Avoid Duplicate Sync Triggers

**Ticket**: [ATT-7669](https://atrainingtracker.atlassian.net/browse/ATT-7669)  
**Parent Epic**: [ATT-162](https://atrainingtracker.atlassian.net/browse/ATT-162) (*Cloud integration*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.6` | **Branch**: `feat/ATT-7669`  
**Requirement Mapping**: `REQ-REV-014` (`REQ-REV-014.1`..`REQ-REV-014.3`)  
**Test Mapping**: `TST-REV-015` (`TST-REV-015.1`..`TST-REV-015.4`)  
**Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-14  

---

## 1. Problem Description & Background
In `EditWorkoutScreen.kt` (`EditWorkoutDialog`), the user is presented with a per-workout checkbox (`Zu Health Connect synchronisieren`), initialized from `TrainingApplication.syncToHealthConnect()`. However, `ExportManager.exportWorkout()` historically evaluated only the global setting `TrainingApplication.syncToHealthConnect()` while `EditWorkoutScreen` simultaneously triggered an independent `HealthConnectManager.syncWorkoutAsync()` call on save. This caused duplicate concurrent sync requests on save and prevented users from opting out of Health Connect sync for individual workouts.

---

## 2. Traceability & Requirements Mapping
* **REQ-REV-014.1** (*Per-Workout Health Connect State in Model & ViewModel*): Nullable `syncToHealthConnect: Boolean? = null` on `WorkoutData`, `updateSyncToHealthConnect()` in `EditWorkoutViewModel`, and initial load fallback to `TrainingApplication.syncToHealthConnect()` $\rightarrow$ `TST-REV-015.1`, `TST-REV-015.4`.
* **REQ-REV-014.2** (*EditWorkoutScreen UI Binding & Single Responsibility Save*): Bind checkbox to ViewModel state, dispatch via `updateSyncToHealthConnect`, and eliminate redundant `HealthConnectManager.syncWorkoutAsync` from `onSave` $\rightarrow$ `TST-REV-015.2`.
* **REQ-REV-014.3** (*ExportManager Pipeline Opt-Out Honor & Single Trigger*): Inspect `workoutData.getSyncToHealthConnect()`; skip when `false`, trigger single sync with `force = true` when `true`, and fall back to global preferences when `null` $\rightarrow$ `TST-REV-015.3`.

---

## 3. System Invariants & Preserved Behavior
1. **Database Schema Invariance**: Zero SQLite table migrations (`DB_VERSION = 30` preserved); `syncToHealthConnect` is a transient session/export property on `WorkoutData`.
2. **Background Operations Invariance**: Background automated tracking export, crop export, and lap edit export pass `syncToHealthConnect = null` and fall back to `TrainingApplication.syncToHealthConnect()`.
3. **Cloud & File Export Integrity**: Strava export exclusion (`uploadToStrava == 0`) and all file format exports (FIT, GPX, TCX, CSV) remain 100% untouched.
4. **Localization Parity**: All 9 locales for `health_connect_sync_manual` preserved.

---

## 4. Proposed Architectural Changes

### Component 1: Domain Model (`WorkoutData.kt`)
* Add `val syncToHealthConnect: Boolean? = null` to `WorkoutData` data class with default argument `null` to ensure 100% backward compatibility for all existing call sites and constructors.

### Component 2: ViewModel State Management (`EditWorkoutViewModel.kt`)
* In `loadWorkoutData()`, when initially populating `_workoutData`, initialize `syncToHealthConnect` using `TrainingApplication.syncToHealthConnect()`.
* Add public method:
  ```kotlin
  fun updateSyncToHealthConnect(isChecked: Boolean) {
      _workoutData.update { it?.copy(syncToHealthConnect = isChecked) }
  }
  ```

### Component 3: Presentation Layer (`EditWorkoutScreen.kt`)
* Replace local `var syncToHealthConnect by remember { mutableStateOf(...) }` with reactive observation:
  ```kotlin
  val syncToHealthConnect = workoutData?.syncToHealthConnect ?: TrainingApplication.syncToHealthConnect()
  ```
* In `Health Connect Sync Row`, set `onValueChange = { viewModel.updateSyncToHealthConnect(it) }`.
* In `AppDialogActions.SaveCancel.onSave`, delete lines 131–139 (the redundant `HealthConnectManager.syncWorkoutAsync` call), keeping only `viewModel.saveChanges()` and `onDismiss()`.

### Component 4: Repository In-Memory State (`WorkoutRepository.kt`)
* In `saveWorkout(userEditedWorkout: WorkoutData?)`, ensure the in-memory `_allWorkouts.update` carries `syncToHealthConnect = userEditedWorkout.syncToHealthConnect`.

### Component 5: Export Pipeline (`ExportManager.java`)
* Update `exportWorkout(WorkoutData workoutData)`:
  ```java
  // Android Health Connect automatic workout synchronization (ATT-3052 / REQ-SET-103, ATT-7669)
  Boolean syncHc = workoutData.getSyncToHealthConnect();
  boolean shouldSyncHc = (syncHc != null) ? syncHc : TrainingApplication.syncToHealthConnect();
  if (shouldSyncHc && mContext != null) {
      boolean forceSync = Boolean.TRUE.equals(syncHc);
      com.atrainingtracker.trainingtracker.cloud.healthconnect.HealthConnectManager.syncWorkoutAsync(mContext, workoutData, forceSync);
  }
  ```

### UI Consistency (`docs/design_guidelines.md` §1 & §5)
* Preserves grouped `Surface` (shape `10.dp`) established in REQ-REV-013 with `R.drawable.ic_health_connect` vector asset, `Color.Unspecified` tint, and trailing Checkbox with `onCheckedChange = null` inside `Modifier.toggleable`.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Model & ViewModel Extensions (`WorkoutData.kt`, `EditWorkoutViewModel.kt`)
* Add `val syncToHealthConnect: Boolean? = null` to `WorkoutData.kt` (`REQ-REV-014.1`).
* In `EditWorkoutViewModel.kt`, add `updateSyncToHealthConnect(isChecked: Boolean)` and set initial value in `loadWorkoutData()` (`REQ-REV-014.1`).

### Step 2: UI Binding & Dialog Save Consolidation (`EditWorkoutScreen.kt`)
* Bind `syncToHealthConnect` state and `onValueChange` to `EditWorkoutViewModel` (`REQ-REV-014.2`).
* Remove redundant `HealthConnectManager.syncWorkoutAsync` block from `onSave` (`REQ-REV-014.2`).

### Step 3: Repository & Export Pipeline Integration (`WorkoutRepository.kt`, `ExportManager.java`)
* In `WorkoutRepository.kt`, propagate `syncToHealthConnect` in `saveWorkout` in-memory update (`REQ-REV-014.1`).
* In `ExportManager.java`, evaluate `workoutData.getSyncToHealthConnect()` to skip when `false` and force when `true` (`REQ-REV-014.3`).

### Step 4: Unit & Contract Tests
* Update `EditWorkoutViewModelTest.kt` to verify `syncToHealthConnect` initialization and updates (`TST-REV-015.1`).
* Update `EditWorkoutClassificationLayoutContractTest.kt` to verify removal of direct UI sync invocation in `EditWorkoutScreen.kt` (`TST-REV-015.2`).
* Create `EditWorkoutHealthConnectSyncTest.kt` to test `ExportManager.exportWorkout` opt-out honor and force sync invocation (`TST-REV-015.3`, `TST-REV-015.4`).
* Targeted test command:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.aftermath.editworkout.*"
  ```

---

## 6. Verification & Rollback Plan
* **Verification**: All targeted unit tests pass; `verify_requirement_governance.py --sync-indexes` retains 100% parity; clean Detekt run without new violations.
* **Rollback**: `git checkout sprint/2026-46.6 -- .` and reset branch.
