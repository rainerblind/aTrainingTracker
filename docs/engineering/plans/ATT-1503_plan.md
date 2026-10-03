# Stage 3: Implementation Plan - ATT-1503: Standardize edit action symbols across the entire application

**Ticket**: [ATT-1503](https://atrainingtracker.atlassian.net/browse/ATT-1503)  
**Sub-task**: [ATT-1520](https://atrainingtracker.atlassian.net/browse/ATT-1520) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-355](https://atrainingtracker.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.2`  
**Requirement Mapping**: `REQ-UI-182` (*Unified Edit Action Symbols & Design Token Tinting*)  
**Test Mapping**: `TST-UI-134.1`, `TST-UI-134.2`, `TST-UI-134.3`, `TST-UI-134.4`, `TST-UI-134.5`  
**Branch**: `feature/ATT-1503`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-09-28  

---

## 1. Problem Description & Background

Multiple disparate icons (`ic_table_edit`, `@android:drawable/ic_menu_edit`, `Icons.Default.Edit`) and divergent color tints (`primary`, `secondary`, `onSurfaceVariant`, and un-tinted `LocalContentColor`) are currently used across the application to trigger or represent "Edit" operations.

This visual fragmentation creates cognitive friction: athletes encounter distinct visual metaphors and mismatched color treatments for the exact same conceptual operation ("start editing"). Standardizing on the official Material 3 diagonal pencil symbol (`Icons.Default.Edit` in Compose, and a canonical `@drawable/ic_edit` in XML resources) and establishing 100% uniform design token tinting (`MaterialTheme.colorScheme.primary` for interactive edit action buttons and dialog headers) harmonizes the entire application.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-UI-182` (*Unified Edit Action Symbols & Design Token Tinting*)
* **Test Mapping**:
  * `TST-UI-134.1` (*Unit test: Edit icon vector and tint consistency verification*)
  * `TST-UI-134.2` (*Localization parity: 9-language translation validation*)
  * `TST-UI-134.3` (*Compose preview: Visual inspection and tint verification in Light/Dark mode*)
  * `TST-UI-134.4` (*Resource guard: CI/unit test asserting absence of legacy drawables and XML references*)
  * `TST-UI-134.5` (*Regression: Full test suite execution `./gradlew testDebugUnitTest`*)

---

## 3. System Invariants & Preserved Behavior

1. **Zero Functional & Navigation Regressions**: All edit action callbacks (`onEditWorkout`, `onRename`, `onEditFingerprint`, `onToggleMode`, `onEdit`), dialog dismiss mechanics, state retention, and underlying ViewModels remain completely unaltered.
2. **Specialized Geofence Pin Invariant**: Specialized spatial coordinate adjustment in `WorkoutClusterHeatmapScreen.kt` retains `Icons.Default.EditLocationAlt`, unified to `tint = MaterialTheme.colorScheme.primary`.
3. **Touch Geometry & Accessibility Invariant**: All interactive edit buttons preserve standard touch targets ($\ge 48\times 48\text{dp}$ touch bounds with $24\times 24\text{dp}$ or $32\times 32\text{dp}$ visual footprint) and localized `contentDescription` attributes.
4. **Subtask Self-Sufficiency**: Subtasks transition directly to `Erledigt` upon passing Gate audit via `freigabe`.
5. **Parent Human Gate Invariance**: Terminal completion of parent tickets remains reserved for the human user in `Final Review (Human)`. AI agents never transition parent tickets to `Erledigt`.

---

## 4. Proposed Architectural Changes

### Component 1: Vector Drawables & XML Menus (`SWE.2`)
* Add `app/src/main/res/drawable/ic_edit.xml` containing the canonical Material 3 diagonal pencil vector asset ($24\times 24\text{dp}$, viewport $24\times 24$, tinted `?attr/colorControlNormal` for XML usage).
* Update `app/src/main/res/menu/device_list_context_menu.xml`:
  * Replace `@android:drawable/ic_menu_edit` on `editDevice` with `@drawable/ic_edit`.
  * Replace `@android:drawable/ic_menu_edit` on `deleteDevice` with `@android:drawable/ic_menu_delete`.
* Update `app/src/main/java/com/atrainingtracker/trainingtracker/ui/navigation/AppNavigationDrawer.kt:191`:
  * Replace `R.drawable.ic_table_edit` with `R.drawable.ic_edit`.
* Permanently delete `app/src/main/res/drawable/ic_table_edit.xml`.

### Component 2: Compose Interactive Buttons & Dialog Headers (`SWE.2`)
* [WorkoutHeader.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/components/workoutheader/WorkoutHeader.kt):
  * Replace `painter = painterResource(id = R.drawable.ic_table_edit)` with `imageVector = Icons.Default.Edit`.
  * Retain `tint = MaterialTheme.colorScheme.primary`.
* [WorkoutClusterHeatmapScreen.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/clusters/WorkoutClusterHeatmapScreen.kt):
  * Replace `painter = painterResource(id = R.drawable.ic_table_edit)` with `imageVector = Icons.Default.Edit`, with `tint = MaterialTheme.colorScheme.primary`.
  * Retain `imageVector = Icons.Default.EditLocationAlt`, adding explicit `tint = MaterialTheme.colorScheme.primary`.
* [EditKnownLocationDialog.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/knownlocations/EditKnownLocationDialog.kt):
  * In `AppModalBottomSheet` and `AppBottomSheetContent`, replace `iconPainter = painterResource(R.drawable.ic_table_edit)` with `icon = Icons.Default.Edit`.
* [ActivityTypeSelectionDialog.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/trackingtabs/ActivityTypeSelectionDialog.kt):
  * In `AppBottomSheetContent`, replace `iconPainter = painterResource(id = R.drawable.ic_table_edit)` with `icon = Icons.Default.Edit`.
* [TrackingTabPreviewHeader.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/trackingtabs/TrackingTabPreviewHeader.kt):
  * Change `tint` from `MaterialTheme.colorScheme.secondary` to `MaterialTheme.colorScheme.primary`.
* [MapScreenWithTrack.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapScreenWithTrack.kt):
  * Change `tint` from `MaterialTheme.colorScheme.onSurfaceVariant` to `MaterialTheme.colorScheme.primary`.

### Component 3: Automated Consistency & Resource Guard Test Suite (`SWE.4`)
* Create `app/src/test/java/com/atrainingtracker/trainingtracker/ui/EditIconConsistencyTest.kt`:
  * Assert `ic_table_edit.xml` does not exist on disk.
  * Assert `ic_edit.xml` exists on disk and has valid vector XML root.
  * Assert `device_list_context_menu.xml` contains zero occurrences of `@android:drawable/ic_menu_edit` and contains `@drawable/ic_edit`.
  * Assert source files `WorkoutHeader.kt`, `WorkoutClusterHeatmapScreen.kt`, `EditKnownLocationDialog.kt`, `ActivityTypeSelectionDialog.kt`, and `AppNavigationDrawer.kt` contain zero references to `ic_table_edit`.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Create `ic_edit.xml` Vector Drawable & Remove `ic_table_edit.xml`
* Target: `app/src/main/res/drawable/ic_edit.xml`
* Target: `app/src/main/res/drawable/ic_table_edit.xml` (delete)
* Verification: File system check.

### Step 2: Update XML Menus and Navigation Drawer
* Target: `app/src/main/res/menu/device_list_context_menu.xml`
* Target: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/navigation/AppNavigationDrawer.kt`
* Changes: Switch references to `@drawable/ic_edit` and `@android:drawable/ic_menu_delete`.

### Step 3: Standardize Compose Edit Action Triggers & Headers
* Target: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/components/workoutheader/WorkoutHeader.kt`
* Target: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/clusters/WorkoutClusterHeatmapScreen.kt`
* Target: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/knownlocations/EditKnownLocationDialog.kt`
* Target: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/trackingtabs/ActivityTypeSelectionDialog.kt`
* Target: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/trackingtabs/TrackingTabPreviewHeader.kt`
* Target: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapScreenWithTrack.kt`
* Changes: Unify symbols to `Icons.Default.Edit` and tints to `MaterialTheme.colorScheme.primary`.

### Step 4: Add Automated Consistency & Resource Guard Unit Test
* Target: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/EditIconConsistencyTest.kt`
* Verification: Run targeted test `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.EditIconConsistencyTest"`.

### Step 5: Verification & Full Clean-Room Suite
* Run targeted tests:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.EditIconConsistencyTest"
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.TranslationParityTest"
  ```
* Defer full-suite regression (`./gradlew testDebugUnitTest`) to Stage 5.

---

## 6. Verification & Rollback Plan

* **Verification**:
  * Targeted unit tests (`EditIconConsistencyTest`, `TranslationParityTest`) executed during Stage 4.
  * Full regression test suite (`./gradlew testDebugUnitTest`) executed during Stage 5.
* **Rollback**: Branch isolation (`feature/ATT-1503`) allows complete rollback or cherry-pick reversion without impacting `develop` or other sprint activities.
