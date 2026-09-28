# Stage 1 Analysis: ATT-1503 - Standardize edit action symbols across the entire application

**Ticket**: [ATT-1503](https://atrainingtracker.atlassian.net/browse/ATT-1503)  
**Sub-task**: [ATT-1518](https://atrainingtracker.atlassian.net/browse/ATT-1518) (`[Analysis]`)  
**Parent Epic**: [ATT-355](https://atrainingtracker.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.2`  
**Branch**: `feature/ATT-1503`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-09-28  

---

## 1. Problem Statement & Motivation

Across the aTrainingTracker application, multiple disparate symbols, vector drawables, and color tint tokens are currently used to indicate or trigger the "Edit" action:

1. **Disparate Visual Metaphors**:
   * `R.drawable.ic_table_edit` (a grid/table sheet with a pencil overlay) is used in [WorkoutHeader.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/components/workoutheader/WorkoutHeader.kt) (workout editing), [WorkoutClusterHeatmapScreen.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/clusters/WorkoutClusterHeatmapScreen.kt) (cluster renaming), [ActivityTypeSelectionDialog.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/trackingtabs/ActivityTypeSelectionDialog.kt) (header icon), [EditKnownLocationDialog.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/knownlocations/EditKnownLocationDialog.kt) (header icon), and [AppNavigationDrawer.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/navigation/AppNavigationDrawer.kt).
   * `Icons.Default.Edit` (the clean, canonical Material 3 diagonal pencil) is used in [EditWorkoutScreen.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/editworkout/EditWorkoutScreen.kt), [EditRouteScreen.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/routes/EditRouteScreen.kt), [EditSensorFieldDialog.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/editsensorfield/EditSensorFieldDialog.kt), [KnownLocationsScreen.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/knownlocations/KnownLocationsScreen.kt), [TrackingTabPreviewHeader.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/trackingtabs/TrackingTabPreviewHeader.kt), and [MapScreenWithTrack.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapScreenWithTrack.kt).
   * `@android:drawable/ic_menu_edit` (Android framework legacy Holo/AOSP drawable) is referenced in [device_list_context_menu.xml](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/res/menu/device_list_context_menu.xml).

2. **Inconsistent Color Tinting**:
   * [WorkoutHeader.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/components/workoutheader/WorkoutHeader.kt): explicit `tint = MaterialTheme.colorScheme.primary`.
   * [TrackingTabPreviewHeader.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/trackingtabs/TrackingTabPreviewHeader.kt): explicit `tint = MaterialTheme.colorScheme.secondary` (divergent color accent).
   * [MapScreenWithTrack.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapScreenWithTrack.kt): explicit `tint = MaterialTheme.colorScheme.onSurfaceVariant` (muted auxiliary tint for an interactive edit trigger).
   * [WorkoutClusterHeatmapScreen.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/clusters/WorkoutClusterHeatmapScreen.kt): un-tinted / defaulted to `LocalContentColor.current`.
   * Bottom sheet headers ([EditWorkoutScreen.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/editworkout/EditWorkoutScreen.kt), [EditRouteScreen.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/routes/EditRouteScreen.kt), [EditSensorFieldDialog.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/editsensorfield/EditSensorFieldDialog.kt)): default `iconTint = MaterialTheme.colorScheme.primary`.

This visual fragmentation creates cognitive friction: athletes encounter distinct visual metaphors and mismatched color treatments for the exact same conceptual operation ("start editing"). Standardizing on the official Material 3 diagonal pencil symbol (`Icons.Default.Edit`) and establishing 100% uniform design token tinting (`MaterialTheme.colorScheme.primary` for interactive edit action buttons and dialog headers) harmonizes the entire application.

---

## 2. Root Cause Analysis (Forensic Investigation) & Call-Site Audit

### Forensic Gap Analysis
The visual and tinting divergence developed organically across multiple sprints:
* Sprints 24–38: `ic_table_edit.xml` was introduced for tabular preference and drawer layouts (`prefsConfigureDisplaysTitle`), and subsequently adopted as a convenient drawable asset for workout and cluster headers in ATT-506 and ATT-850.
* Sprint 36–39: When modernizing dialogs to `AppModalBottomSheet` (ATT-900, ATT-1043), several screens adopted Compose M3 `Icons.Default.Edit`, while other components retained `R.drawable.ic_table_edit`.
* Legacy XML Menus: [device_list_context_menu.xml](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/res/menu/device_list_context_menu.xml) persisted from legacy pre-Compose architectures and still references system drawables (`@android:drawable/ic_menu_edit`).

### Complete Call-Site Audit
| Component / File | Current Symbol | Current Tint | Target Symbol | Target Tint |
| :--- | :--- | :--- | :--- | :--- |
| [WorkoutHeader.kt:262](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/components/workoutheader/WorkoutHeader.kt#L262) | `R.drawable.ic_table_edit` | `primary` | `Icons.Default.Edit` | `primary` |
| [WorkoutClusterHeatmapScreen.kt:746](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/clusters/WorkoutClusterHeatmapScreen.kt#L746) | `R.drawable.ic_table_edit` | `LocalContentColor` | `Icons.Default.Edit` | `primary` |
| [WorkoutClusterHeatmapScreen.kt:742](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/clusters/WorkoutClusterHeatmapScreen.kt#L742) | `Icons.Default.EditLocationAlt` | `LocalContentColor` | `Icons.Default.EditLocationAlt` | `primary` |
| [EditKnownLocationDialog.kt:122, 263](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/knownlocations/EditKnownLocationDialog.kt#L122) | `R.drawable.ic_table_edit` | `primary` (default) | `Icons.Default.Edit` | `primary` (default) |
| [ActivityTypeSelectionDialog.kt:46](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/trackingtabs/ActivityTypeSelectionDialog.kt#L46) | `R.drawable.ic_table_edit` | `primary` (default) | `Icons.Default.Edit` | `primary` (default) |
| [TrackingTabPreviewHeader.kt:98](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/trackingtabs/TrackingTabPreviewHeader.kt#L98) | `Icons.Default.Edit` | `secondary` | `Icons.Default.Edit` | `primary` |
| [MapScreenWithTrack.kt:321](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapScreenWithTrack.kt#L321) | `Icons.Default.Edit` | `onSurfaceVariant` | `Icons.Default.Edit` | `primary` |
| [AppNavigationDrawer.kt:191](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/navigation/AppNavigationDrawer.kt#L191) | `R.drawable.ic_table_edit` | Drawer tint | `R.drawable.ic_edit` | Drawer tint |
| [device_list_context_menu.xml:23](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/res/menu/device_list_context_menu.xml#L23) | `@android:drawable/ic_menu_edit` | Framework menu | `@drawable/ic_edit` | Framework menu |
| [EditWorkoutScreen.kt:93](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/editworkout/EditWorkoutScreen.kt#L93) | `Icons.Default.Edit` | `primary` | `Icons.Default.Edit` (Unchanged) | `primary` (Unchanged) |
| [EditRouteScreen.kt:63](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/routes/EditRouteScreen.kt#L63) | `Icons.Default.Edit` | `primary` | `Icons.Default.Edit` (Unchanged) | `primary` (Unchanged) |
| [EditSensorFieldDialog.kt:55](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/editsensorfield/EditSensorFieldDialog.kt#L55) | `Icons.Default.Edit` | `primary` | `Icons.Default.Edit` (Unchanged) | `primary` (Unchanged) |
| [KnownLocationsScreen.kt:409](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/knownlocations/KnownLocationsScreen.kt#L409) | `Icons.Default.Edit` | M3 Dropdown default | `Icons.Default.Edit` (Unchanged) | M3 Dropdown default (Unchanged) |

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  1. Standardize all general edit action triggers, dialog headers, and menus to use the single canonical Material 3 pencil symbol (`Icons.Default.Edit` in Compose, and `@drawable/ic_edit` in XML resources).
  2. Enforce 100% color/tint uniformity across all interactive edit action buttons using `MaterialTheme.colorScheme.primary`.
  3. Replace legacy `@android:drawable/ic_menu_edit` in [device_list_context_menu.xml](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/res/menu/device_list_context_menu.xml) with `@drawable/ic_edit`.
  4. Ensure all interactive edit buttons maintain touch targets of at least $48\times 48\text{dp}$ and retain complete localized content descriptions (`@string/edit_workout`, `@string/Edit`, `@string/edit_workout_name`, etc.).
  5. Retain specialized spatial coordinate adjustment icon (`Icons.Default.EditLocationAlt`) for geographic cluster boundary tuning while unifying its tint to `MaterialTheme.colorScheme.primary`.

* **Out-of-Scope Non-Goals (Scope Bounding)**:
  1. No modification to workout editing business logic, data models, ViewModels, or database queries.
  2. No restructuring of dialog contents, input fields, or save/cancel mechanics.
  3. No changes to unrelated context menu items (e.g. delete actions).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit (REQ-PRO-022)

The forensic investigation identified 4 existing requirements in [docs/requirements.md](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/requirements.md) that explicitly reference edit symbols:

### 1. REQ-SET-070: Direct Navigation to Workout Editor from Workout Detail Popup & Unified Action Button Layout
* **Original Requirement ID & Target**: `REQ-SET-070` targeting [WorkoutHeader.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/components/workoutheader/WorkoutHeader.kt) and `ClusterSummaryHeader`.
* **Historical Origin & Commit Trace**: Commit `16ac39f3` (ATT-506).
* **Root Reason for Existing Formulation**: Specified `R.drawable.ic_table_edit` as a quick visual placeholder asset before an app-wide Compose Material 3 vector icon standard was established.
* **Preservation of Core Invariants**: Upgrading `R.drawable.ic_table_edit` to `Icons.Default.Edit` preserves the required button position (very right of action row), touch target size (`32.dp` icon button with $48\text{dp}$ touch target), click-to-edit invocation of `onEditWorkout`, and localized accessibility descriptions.

### 2. REQ-SET-071: Consistent Workout Click-to-Map Navigation & Dedicated Header Edit Action
* **Original Requirement ID & Target**: `REQ-SET-071` targeting [WorkoutSummary.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/components/workoutsummary/WorkoutSummary.kt) and [WorkoutHeader.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/components/workoutheader/WorkoutHeader.kt).
* **Historical Origin & Commit Trace**: Commit `2bf79cdf` (ATT-850).
* **Root Reason for Existing Formulation**: Explicitly mentioned `R.drawable.ic_table_edit` to mandate a dedicated visual button distinct from map click navigation.
* **Preservation of Core Invariants**: Switching to `Icons.Default.Edit` preserves click-to-map navigation precedence and header button ordering, while eliminating visual inconsistency.

### 3. REQ-UI-149: Modernized Modal Bottom Sheet Dialogs & Selection Flows
* **Original Requirement ID & Target**: `REQ-UI-149` targeting [ActivityTypeSelectionDialog.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/trackingtabs/ActivityTypeSelectionDialog.kt).
* **Historical Origin & Commit Trace**: Commit `be9d84d7` (ATT-900).
* **Root Reason for Existing Formulation**: Referenced `R.drawable.ic_table_edit` for the activity type selection bottom sheet header icon.
* **Preservation of Core Invariants**: Transitioning `iconPainter` to `icon = Icons.Default.Edit` preserves the bottom sheet structure, authentic sport icons, and selection callbacks while harmonizing header icons with other bottom sheet dialogs.

### 4. REQ-UI-151: Workout Editing Modal Bottom Sheet & Fragment Viewport Layering
* **Original Requirement ID & Target**: `REQ-UI-151` targeting [EditWorkoutScreen.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/editworkout/EditWorkoutScreen.kt) / [EditWorkoutDialog.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/editworkout/EditWorkoutDialog.kt).
* **Historical Origin & Commit Trace**: Commit `4452a85e` (ATT-1043).
* **Root Reason for Existing Formulation**: Allowed `Icons.Default.Edit or ic_table_edit` as transitional flexibility.
* **Preservation of Core Invariants**: Eliminating the legacy option and locking strictly to `Icons.Default.Edit` enforces single-standard parity across all editing flows.

---

## 5. Architectural Strategy & High-Level Solution

### Architectural Principles (SWE.2)
1. **Single Source of Truth for Vector Icons**:
   * Compose components: Standardize directly on Material 3 `androidx.compose.material.icons.filled.Edit` (`Icons.Default.Edit`).
   * XML Menu Resources / Navigation Drawer: Provide a dedicated vector drawable asset [app/src/main/res/drawable/ic_edit.xml](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/res/drawable/ic_edit.xml) that perfectly mirrors the canonical Material 3 diagonal pencil.
2. **Strict Color & Tint Uniformity**:
   * Interactive edit action buttons ([WorkoutHeader.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/components/workoutheader/WorkoutHeader.kt), [WorkoutClusterHeatmapScreen.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/clusters/WorkoutClusterHeatmapScreen.kt), [TrackingTabPreviewHeader.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/trackingtabs/TrackingTabPreviewHeader.kt), [MapScreenWithTrack.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapScreenWithTrack.kt)): Unify strictly to `tint = MaterialTheme.colorScheme.primary`.
   * Dialog Headers ([EditKnownLocationDialog.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/knownlocations/EditKnownLocationDialog.kt), [ActivityTypeSelectionDialog.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/trackingtabs/ActivityTypeSelectionDialog.kt), [EditWorkoutScreen.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/editworkout/EditWorkoutScreen.kt), [EditRouteScreen.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/routes/EditRouteScreen.kt), [EditSensorFieldDialog.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/editsensorfield/EditSensorFieldDialog.kt)): Standardize on `icon = Icons.Default.Edit` leveraging [AppModalBottomSheet.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/components/core/AppModalBottomSheet.kt)'s default `iconTint = MaterialTheme.colorScheme.primary`.
3. **Accessibility & Touch Geometry**:
   * All edit action triggers preserve standard touch bounds ($\ge 48\times 48\text{dp}$) with $24\times 24\text{dp}$ or $32\times 32\text{dp}$ visual footprint and localized `contentDescription`.

---

## 6. System Invariants & Risk Assessment

* **Core Invariants**:
  1. All workout editing, cluster renaming, location editing, tracking tab editing, and sensor field editing interactions must function identically with zero regression.
  2. Dialog dismiss mechanics, state retention, and underlying view composure must remain completely unchanged.
  3. Parent ticket Human Decision Gate remains strictly enforced (AI agents never close parent tickets to `Erledigt`).
  4. Full test suite `./gradlew testDebugUnitTest` must pass with 100% success (0 failures).

* **Risk Rating**: **LOW**  
  *Justification*: The refactoring is purely visual and stylistic (icon assets, vector references, and color scheme tokens). No database schema, concurrency dispatchers, ViewModel states, or navigation backstacks are altered.
