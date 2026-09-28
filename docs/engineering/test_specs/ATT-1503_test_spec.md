# Stage 2: Requirement & Test Specification - ATT-1503: Standardize edit action symbols across the entire application

**Ticket**: [ATT-1503](https://atrainingtracker.atlassian.net/browse/ATT-1503)  
**Sub-task**: [ATT-1519](https://atrainingtracker.atlassian.net/browse/ATT-1519) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-355](https://atrainingtracker.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.2`  
**Requirement Mapping**: `REQ-UI-182` (*Unified Edit Action Symbols & Design Token Tinting*)  
**Test Spec ID**: `TST-UI-134`  
**Branch**: `feature/ATT-1503`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-09-28  

---

## 1. Requirement Specification (REQ-UI-182)

### 1.1 Problem Statement & Rationale
Multiple disparate icons (`ic_table_edit`, `@android:drawable/ic_menu_edit`, `Icons.Default.Edit`) and divergent color tints (`primary`, `secondary`, `onSurfaceVariant`, and un-tinted `LocalContentColor`) are currently used across the application to trigger or represent "Edit" operations. This visual divergence causes user confusion and undermines design consistency. Establishing a single canonical symbol (`Icons.Default.Edit` in Compose, `@drawable/ic_edit` in XML) and strict design token color tinting (`MaterialTheme.colorScheme.primary` for interactive buttons and dialog headers) guarantees visual harmony and removes cognitive friction.

### 1.2 Functional & Architectural Requirements
The system SHALL standardize all general edit action buttons, dialog headers, and menus across the entire application to use a unified symbol and theme-aware design token color tinting:

1. **Unified Symbol Standard**:
   * All Jetpack Compose interactive edit action buttons and dialog headers SHALL use the canonical Material 3 diagonal pencil symbol (`Icons.Default.Edit`).
   * XML menu resources and navigation drawer configs SHALL reference a dedicated vector asset (`@drawable/ic_edit`) matching the Material 3 pencil symbol, eliminating `@android:drawable/ic_menu_edit` and `ic_table_edit` from edit operations.
   * `WorkoutClusterHeatmapScreen.kt` SHALL retain `Icons.Default.EditLocationAlt` strictly for specialized geographic coordinate boundary adjustment, while general cluster identity renaming SHALL use `Icons.Default.Edit`.

2. **100% Color & Design Token Tinting Uniformity**:
   * All interactive edit action buttons across the application (including [WorkoutHeader.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/components/workoutheader/WorkoutHeader.kt), [WorkoutClusterHeatmapScreen.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/clusters/WorkoutClusterHeatmapScreen.kt), [TrackingTabPreviewHeader.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/trackingtabs/TrackingTabPreviewHeader.kt), [MapScreenWithTrack.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapScreenWithTrack.kt)) SHALL be tinted with `MaterialTheme.colorScheme.primary`. Hardcoded colors or non-primary tints (`secondary`, `onSurfaceVariant`) SHALL NOT be used for interactive edit action buttons.
   * Modal bottom sheet dialog headers ([EditKnownLocationDialog.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/knownlocations/EditKnownLocationDialog.kt), [ActivityTypeSelectionDialog.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/trackingtabs/ActivityTypeSelectionDialog.kt), [EditWorkoutScreen.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/editworkout/EditWorkoutScreen.kt), [EditRouteScreen.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/routes/EditRouteScreen.kt), [EditSensorFieldDialog.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/editsensorfield/EditSensorFieldDialog.kt)) SHALL standardize on `icon = Icons.Default.Edit` with default `iconTint = MaterialTheme.colorScheme.primary`.

3. **Accessibility & Geometry Preservation**:
   * All interactive edit buttons SHALL maintain a minimum touch target bounding box of $48\times 48\text{dp}$ with a $24\times 24\text{dp}$ or $32\times 32\text{dp}$ visual footprint.
   * All call sites SHALL preserve localized accessibility content descriptions (`@string/edit_workout`, `@string/Edit`, `@string/edit_workout_name`, `@string/known_location_edit`).

4. **CI & Static Lint Enforcement**:
   * `app/src/main/res/drawable/ic_table_edit.xml` SHALL be permanently deleted from the codebase.
   * Automated verification tests SHALL audit project resources and source files to guarantee that legacy `@android:drawable/ic_menu_edit` is completely absent from all menu XML files, `ic_table_edit` is never referenced in source code, and `ic_table_edit.xml` does not exist on disk.

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Workout Header & Cards)**:
  * *Given* an athlete viewing a workout summary in list or map peek views,
  * *When* the header action row is composed,
  * *Then* the edit button SHALL render `Icons.Default.Edit` tinted with `MaterialTheme.colorScheme.primary`.
* **Criterion 2 (Cluster & Map Peek Views)**:
  * *Given* an athlete viewing the cluster heatmap screen or favorite location peek sheet on the map,
  * *When* edit action buttons are rendered,
  * *Then* the general edit buttons SHALL render `Icons.Default.Edit` tinted with `MaterialTheme.colorScheme.primary`.
* **Criterion 3 (Dialog Headers)**:
  * *Given* an athlete opening `EditKnownLocationDialog` or `ActivityTypeSelectionDialog`,
  * *When* the modal bottom sheet appears,
  * *Then* the header leading icon SHALL display `Icons.Default.Edit` tinted with `MaterialTheme.colorScheme.primary`.
* **Criterion 4 (Tracking Tabs Cockpit)**:
  * *Given* an athlete previewing tracking tabs in `TrackingTabPreviewHeader`,
  * *When* the edit button is rendered,
  * *Then* it SHALL display `Icons.Default.Edit` tinted with `MaterialTheme.colorScheme.primary` (replacing previous `secondary` tint).
* **Criterion 5 (Legacy Menus & Drawer)**:
  * *Given* the device list context menu or navigation drawer,
  * *When* the menu items are inflated/rendered,
  * *Then* the edit action SHALL reference `@drawable/ic_edit` (zero references to `@android:drawable/ic_menu_edit`).

### 1.4 System Invariants
* Existing business logic, ViewModel states, click handlers, and database persistence MUST remain completely unaltered.
* Dialog dismiss behavior, backstack navigation, and sheet heights MUST NOT change.
* 100% full-suite unit test pass rate (`./gradlew testDebugUnitTest`).

---

## 2. Requirement Archaeology & Chesterton's Fence Audit (REQ-PRO-022)

### Requirement Archaeology & Chesterton's Fence Audit

#### 1. REQ-SET-070: Direct Navigation to Workout Editor from Workout Detail Popup & Unified Action Button Layout
* **Original Requirement ID & Target**: `REQ-SET-070` targeting [WorkoutHeader.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/components/workoutheader/WorkoutHeader.kt) and `ClusterSummaryHeader`.
* **Historical Origin & Commit Trace**: Commit `16ac39f3` (ATT-506, 2026-08-16).
* **Root Reason for Existing Formulation**: `R.drawable.ic_table_edit` was chosen as a quick visual placeholder/drawable asset for the workout header edit action before an app-wide Compose Material 3 vector icon standard was established.
* **Preservation of Core Invariants**: Replacing `R.drawable.ic_table_edit` with `Icons.Default.Edit` preserves the required button position (very right of action row), touch target size (`32.dp` icon button with $48\text{dp}$ touch target), click-to-edit invocation of `onEditWorkout`, and localized accessibility descriptions without altering backstack or map navigation.

#### 2. REQ-SET-071: Consistent Workout Click-to-Map Navigation & Dedicated Header Edit Action
* **Original Requirement ID & Target**: `REQ-SET-071` targeting [WorkoutSummary.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/components/workoutsummary/WorkoutSummary.kt) and [WorkoutHeader.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/components/workoutheader/WorkoutHeader.kt).
* **Historical Origin & Commit Trace**: Commit `2bf79cdf` (ATT-850, 2026-08-25).
* **Root Reason for Existing Formulation**: Explicitly mentioned `R.drawable.ic_table_edit` to mandate a dedicated visual button distinct from map click navigation.
* **Preservation of Core Invariants**: Switching to `Icons.Default.Edit` preserves click-to-map navigation precedence and header button ordering, while eliminating visual inconsistency and guaranteeing design token tinting (`primary`).

#### 3. REQ-UI-149: Modernized Modal Bottom Sheet Dialogs & Selection Flows
* **Original Requirement ID & Target**: `REQ-UI-149` targeting [ActivityTypeSelectionDialog.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/trackingtabs/ActivityTypeSelectionDialog.kt).
* **Historical Origin & Commit Trace**: Commit `be9d84d7` (ATT-900, 2026-08-30).
* **Root Reason for Existing Formulation**: `ActivityTypeSelectionDialog` used `ic_table_edit` as a leading header drawable because it was available in `res/drawable`.
* **Preservation of Core Invariants**: Transitioning `iconPainter` to `icon = Icons.Default.Edit` preserves the bottom sheet structure, authentic sport icons, and selection callbacks while harmonizing header icons with other bottom sheet dialogs.

#### 4. REQ-UI-151: Workout Editing Modal Bottom Sheet & Fragment Viewport Layering
* **Original Requirement ID & Target**: `REQ-UI-151` targeting [EditWorkoutScreen.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/editworkout/EditWorkoutScreen.kt) / [EditWorkoutDialog.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/editworkout/EditWorkoutDialog.kt).
* **Historical Origin & Commit Trace**: Commit `4452a85e` (ATT-1043, 2026-09-02).
* **Root Reason for Existing Formulation**: Permitted `Icons.Default.Edit or ic_table_edit` during the transition period.
* **Preservation of Core Invariants**: Eliminating the legacy option and locking strictly to `Icons.Default.Edit` enforces single-standard parity across all editing flows.

---

## 3. Test Specification (TST-UI-134)

### Test Case 1: Edit Icon Vector & Tint Consistency Verification (`TST-UI-134.1`)
* **Scope**: Automated Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/EditIconConsistencyTest.kt`
* **Preconditions**: Codebase compiles with all edit action triggers updated.
* **Action**:
  1. Inspect XML menu resource [device_list_context_menu.xml](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/res/menu/device_list_context_menu.xml) to verify `@android:drawable/ic_menu_edit` is absent and replaced by `@drawable/ic_edit`.
  2. Verify [ic_edit.xml](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/res/drawable/ic_edit.xml) exists and contains a valid vector drawable definition with viewport $960\times 960$ or $24\times 24$.
  3. Verify source files [WorkoutHeader.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/components/workoutheader/WorkoutHeader.kt), [WorkoutClusterHeatmapScreen.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/clusters/WorkoutClusterHeatmapScreen.kt), [TrackingTabPreviewHeader.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/trackingtabs/TrackingTabPreviewHeader.kt), [EditKnownLocationDialog.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/knownlocations/EditKnownLocationDialog.kt), and [ActivityTypeSelectionDialog.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/trackingtabs/ActivityTypeSelectionDialog.kt) reference `Icons.Default.Edit` and do not reference `ic_table_edit`.
* **Expected Result**: Assertions pass 100%, confirming zero usage of legacy icons for edit actions.

### Test Case 2: 9-Language Localization & Content Description Parity (`TST-UI-134.2`)
* **Scope**: Automated Localization Parity Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/TranslationParityTest.java`
* **Preconditions**: All strings used for edit content descriptions (`edit_workout`, `Edit`, `edit_workout_name`, `known_location_edit`, `edit_device`) exist.
* **Action**: Run existing translation parity suite across all 9 locales:
  * EN, DE, ES, FR, IT, JA, NL, PL, PT
* **Expected Result**: 100% parity, zero missing keys, zero specifier mismatches.

### Test Case 3: Visual Compose Preview & Tinting Verification (`TST-UI-134.3`)
* **Scope**: Compose Component Visual Inspection & Previews
* **Target Components**: [WorkoutHeader.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/components/workoutheader/WorkoutHeader.kt), [EditKnownLocationDialog.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/knownlocations/EditKnownLocationDialog.kt), [TrackingTabPreviewHeader.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/trackingtabs/TrackingTabPreviewHeader.kt)
* **Preconditions**: Light and Dark theme `@Preview` annotations configured.
* **Action**:
  1. Inspect composable `@Preview` rendering in Light Mode and AMOLED Dark Mode.
  2. Verify edit icon maintains unclipped $24\text{dp}$ visual scale within $32\text{dp}$ / $48\text{dp}$ IconButton bounds.
  3. Verify `tint = MaterialTheme.colorScheme.primary` resolves dynamically against Light (`primary`) and Dark (`primary`) palettes.
* **Expected Result**: Clean visual rendering without clipping or color divergence.

### Test Case 4: CI & Resource Lint Guard (`TST-UI-134.4`)
* **Scope**: Automated Static Analysis & Resource Guard
* **Target**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/EditIconConsistencyTest.kt`
* **Action**:
  1. Scan all files in `app/src/main/res/menu/` for occurrences of `@android:drawable/ic_menu_edit`.
  2. Scan all Compose UI files for references to `ic_table_edit` in interactive action buttons.
  3. Verify that `app/src/main/res/drawable/ic_table_edit.xml` does not exist on disk (`assertFalse(File("app/src/main/res/drawable/ic_table_edit.xml").exists())`).
* **Expected Result**: 0 occurrences found across all resource trees, and `ic_table_edit.xml` is confirmed deleted.

### Test Case 5: Clean-Room Regression Suite (`TST-UI-134.5`)
* **Scope**: Full Clean-Room Test Suite
* **Command**: `./gradlew testDebugUnitTest`
* **Expected Result**: 100% pass rate (0 failures, 0 regressions).

---

## 4. Traceability Matrix

| Test Case ID | Test Scope | Component Under Test | Requirement ID | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-UI-134.1` | Unit | `EditIconConsistencyTest` | `REQ-UI-182` | Specified |
| `TST-UI-134.2` | Localization | `TranslationParityTest` | `REQ-UI-182`, `REQ-UI-106` | Specified |
| `TST-UI-134.3` | Visual Preview | `WorkoutHeader`, `TrackingTabPreviewHeader` | `REQ-UI-182` | Specified |
| `TST-UI-134.4` | Resource Guard | `EditIconConsistencyTest` | `REQ-UI-182` | Specified |
| `TST-UI-134.5` | Regression | `./gradlew testDebugUnitTest` | `REQ-PRO-001` | Specified |
| `TST-SET-059` | Layout/Nav | `WorkoutHeader.kt`, `WorkoutClusterHeatmapScreen.kt` | `REQ-SET-070` | Specified |
| `TST-SET-060` | Click/Nav | `WorkoutSummary.kt`, `WorkoutHeader.kt` | `REQ-SET-071` | Specified |
| `TST-UI-102` | BottomSheet | `ActivityTypeSelectionDialog.kt`, `EditKnownLocationDialog.kt` | `REQ-UI-149` | Specified |
| `TST-UI-104` | BottomSheet | `EditWorkoutScreen.kt`, `EditWorkoutDialog.kt` | `REQ-UI-151` | Specified |
