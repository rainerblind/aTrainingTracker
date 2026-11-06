# Stage 3: Implementation Plan - ATT-5560: Modernize ClusterNamingDialog & Edit Workout Name

**Ticket**: [ATT-5560](https://rainerblind.atlassian.net/browse/ATT-5560)  
**Parent Epic**: [ATT-176](https://rainerblind.atlassian.net/browse/ATT-176) (Auto Name / Route Clusters) | **Story Points**: 2 SP  
**Target Release**: `V5.0.0` | **Active Sprint**: `Sprint 2026-45.8` | **Branch**: `feature/ATT-5560`  
**Requirement Mapping**: `REQ-MIG-025` (referencing `REQ-UI-149`, `REQ-UI-123`)  
**Test Mapping**: `TST-MIG-022` (`TST-MIG-022.8`, `TST-MIG-022.9`), `TST-UI-102` (`TST-UI-102.14`), `TST-UI-078` (`TST-UI-078.2`)  
**Author**: AI Agent 1 (`subagent_pro`) | **Date**: 2026-11-06  

---

## 1. Problem Description & Background
When an imported workout cannot be automatically categorized into an existing route cluster, `ClusterNamingDialog` in `ImportBackupTabsScreen.kt` prompts the athlete for resolution. Currently, it renders as a legacy centered `AlertDialog` (violating `AppModalBottomSheet` standard), drops the imported workout's name from display, and provides no mechanism to edit or persist a custom workout title. This plan specifies converting the dialog to `AppModalBottomSheet`, enabling inline editing of `workoutName`, pre-filling cluster names via `stripHitCount`, and plumbing `ClusterDecision` through `BackupRestoreViewModel` and `LegacyImportEngine`.

---

## 2. Traceability & Requirements Mapping
* **Requirements**: `REQ-MIG-025` (Edit and persist imported `customWorkoutName`), `REQ-UI-149` (Convert `ClusterNamingDialog` to `AppModalBottomSheet`), `REQ-UI-123` (9-language localization parity).
* **Test Mapping**: `TST-UI-102.14` (`ModalBottomSheetDialogsIntegrityTest.kt`), `TST-MIG-022.8` (`LegacyImportEngineWorkoutNameTest.kt`), `TST-UI-078.2` (`TranslationParityTest.kt`), `TST-MIG-022.9` (Governance).

---

## 3. System Invariants & Preserved Behavior
1. **Code Lock (Rule 4)**: Zero production code edits in `app/src/` until Gate 3 passes audit (`check-gate`).
2. **Decision Sovereignty (`REQ-MIG-025`)**: Choosing "Leave Unclustered" strictly sets `clusterId = -1L` with NO fallback cluster creation, while preserving edited `customWorkoutName`.
3. **Concurrency & Lifecycle Invariants**: `Semaphore(3)` backpressure throttling, `recalculationMutex.withLock` thread safety, and `isMapLoaded` retention across sequential items (`REQ-MAP-031`) remain 100% strictly preserved.
4. **Rule 73 Button Ergonomics**: Vertically stack full-width primary (`Button`) and secondary (`OutlinedButton`) CTAs inside sheet content body to safely accommodate German, Polish, and Dutch translations $> 15$ chars.

---

## 4. Proposed Architectural Changes
### Component 1: `LegacyImportEngine.kt` (`migration/`)
* Define `data class ClusterDecision(val clusterId: Long? = null, val newClusterName: String? = null, val customWorkoutName: String? = null)`.
* Update `ProgressListener.onNewClusterCandidate` return type from `Pair<Long?, String?>` to `ClusterDecision`.
* In `persistParsedWorkout`:
  * Pass `decision.customWorkoutName` into `clusterEngine.assignClusterToWorkout(...)` when clustered.
  * When unclustered (`clusterId == null && newClusterName.isNullOrBlank()`), update `WorkoutSummaries.WORKOUT_NAME` in SQLite directly when `!decision.customWorkoutName.isNullOrBlank()`.

### Component 2: `BackupRestoreViewModel.kt` (`migration/`)
* Update `ClusterInteraction.deferred` to `CompletableDeferred<ClusterDecision>`.
* Update `provideClusterDecision(decision: ClusterDecision)` and overload `provideClusterDecision(clusterId: Long?, name: String?, customWorkoutName: String? = null)`.
* Delegate `onNewClusterCandidate` to await `ClusterDecision`.

### Component 3: `ImportBackupTabsScreen.kt` (`migration/`)
* Convert `ClusterNamingDialog` to `AppModalBottomSheet(skipPartiallyExpanded = true)`.
* Add `DarkMapAntiFlashOverlay(isMapLoaded = isMapLoaded, isDark = isDark)`.
* Display queue count badge in `headerActions` when `queueCount > 1`.
* Add `OutlinedTextField` for Workout Name (pre-filled with `state.workoutName ?: ""`).
* When `selectedCluster == null`, pre-fill new cluster name with `WorkoutClusterEngine.stripHitCount(state.workoutName ?: "")`.
* Place Rule 73 stacked full-width buttons (`Button` for Save, `OutlinedButton` for Leave Unclustered).
* Provide `@Preview` composables covering populated states.

### Component 4: 9-Language String Resources (`res/values*/strings.xml`)
* Define `cluster_naming__workout_name_label` across all 9 supported locales (EN, DE, ES, FR, IT, JA, NL, PL, PT).

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)
### Step 1: Add Localized Strings (`REQ-UI-123`)
* Add `cluster_naming__workout_name_label` across all 9 `strings.xml` resource files.

### Step 2: Define `ClusterDecision` & Update Import Pipeline (`REQ-MIG-025`)
* In `LegacyImportEngine.kt`, define `ClusterDecision`, update `ProgressListener`, and persist `customWorkoutName`.

### Step 3: Update `BackupRestoreViewModel.kt` Pipeline (`REQ-MIG-025`)
* Update `ClusterInteraction` and `provideClusterDecision` to handle `ClusterDecision`.

### Step 4: Modernize `ClusterNamingDialog` to `AppModalBottomSheet` (`REQ-UI-149`)
* In `ImportBackupTabsScreen.kt`, convert dialog to `AppModalBottomSheet`, wire inline workout name editing, stripHitCount pre-fill, anti-flash overlay, and Rule 73 stacked buttons.

### Step 5: Unit & Contract Tests (`TST-MIG-022.8`, `TST-UI-102.14`, `TST-UI-078.2`)
* Add unit test in `LegacyImportEngineWorkoutNameTest.kt` verifying `customWorkoutName` persistence for clustered and unclustered outcomes.
* Update `ModalBottomSheetDialogsIntegrityTest.kt` and run `TranslationParityTest` + `verify_requirement_governance.py`.

---

## 6. Verification & Rollback Plan
* **Verification**: Targeted tests: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.migration.*" --tests "com.atrainingtracker.trainingtracker.ui.components.core.ModalBottomSheetDialogsIntegrityTest" --tests "com.atrainingtracker.trainingtracker.localization.TranslationParityTest"`.
* **Rollback**: Branch isolation on `feature/ATT-5560` allows full git discard via `git checkout feature/ATT-5560 -- .`.
