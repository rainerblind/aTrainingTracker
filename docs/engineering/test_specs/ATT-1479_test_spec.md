# Stage 2: Requirement & Test Specification - ATT-1479: Universal Dark Map Styling & Anti-Flash Protection

**Ticket**: [ATT-1479](https://atrainingtracker.atlassian.net/browse/ATT-1479)  
**Sub-task**: [ATT-1537](https://atrainingtracker.atlassian.net/browse/ATT-1537) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-1157](https://atrainingtracker.atlassian.net/browse/ATT-1157) (*Optimize dark mode*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.2`  
**Requirement Mapping**: `REQ-MAP-021` (*Dark Mode Map Styling for Live Route Tracking, Navigation, Cockpit, and Secondary Map Views*)  
**Test Spec ID**: `TST-MAP-023`  
**Branch**: `feature/ATT-1479`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-09-28  

---

## 1. Requirement Specification (REQ-MAP-021)

### 1.1 Problem Statement & Rationale
While `ATrainingTrackerMap` received dark styling in ATT-1266, secondary preview maps, cluster heatmaps, lap editors, and dialog mini-maps across the app instantiate `GoogleMap` with hardcoded `MapType.TERRAIN` or default unstyled properties. Furthermore, map containers lack explicit dark background styling, causing the underlying light surface to flash white while tiles load over the network. Establishing a universal resolver in `DarkMapStyle.kt` and enforcing double-layer anti-flash background protection eliminates all white screen glare in Dark Mode.

### 1.2 Functional & Architectural Requirements
The system SHALL ensure that all map components throughout the application apply dark map styling in Dark Mode and prevent white tile loading flashes:

1. **Universal Dark Map Properties Factory (`DarkMapStyle.kt`)**:
   * `DarkMapStyle` SHALL expose a public, thread-safe function:
     ```kotlin
     fun resolveMapProperties(
         isDark: Boolean,
         context: Context,
         isMyLocationEnabled: Boolean = false,
         lightMapType: MapType = MapType.TERRAIN
     ): MapProperties
     ```
   * When `isDark == true`, the factory SHALL return `MapProperties(mapType = MapType.NORMAL, mapStyleOptions = DarkMapStyle.getMapStyleOptions(context), isMyLocationEnabled = isMyLocationEnabled)`.
   * When `isDark == false`, the factory SHALL return `MapProperties(mapType = lightMapType, mapStyleOptions = null, isMyLocationEnabled = isMyLocationEnabled)`.
   * Map style loading SHALL remain singleton-cached (<10 KB RAM, <3ms parse) with graceful fallback to unstyled `MapType.NORMAL` upon invalid JSON.

2. **Secondary Map View Adoption**:
   All secondary map composables across the application SHALL adopt dynamic `DarkMapStyle.resolveMapProperties`:
   * [PathPreviewMap.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/PathPreviewMap.kt): Route and Strava segment preview cards.
   * [EditKnownLocationDialog.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/knownlocations/EditKnownLocationDialog.kt): Favorite locations mini-map preview.
   * [WorkoutClusterComponents.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/clusters/WorkoutClusterComponents.kt): Workout cluster heatmap cards and detail views.
   * [ManualClusterScreen.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/clusters/ManualClusterScreen.kt): Manual cluster definition map canvas.
   * [PeriodSummaryCard.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/periodlist/PeriodSummaryCard.kt): Period summary map cards.
   * [LapEditBottomSheet.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/components/workoutlaps/LapEditBottomSheet.kt): Lap detail map preview.
   * [ImportBackupTabsScreen.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/migration/ImportBackupTabsScreen.kt): Cluster backup import map preview.
   * [ATrainingTrackerMap.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/ATrainingTrackerMap.kt): Central cockpit navigation map.

3. **Double-Layer Anti-Flash Protection**:
   * All map containers and `GoogleMap` composable modifiers SHALL set:
     `Modifier.background(if (isDark) Color(0xFF121212) else Color.White)` (or surface color token).
   * Host surfaces SHALL never expose a transparent or default white canvas while tiles are loading, decoding, or panning.

4. **Single Source of Truth for Dark State Detection**:
   * All Compose map composables SHALL detect dark mode via:
     `val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f` (unless explicitly overridden by caller).
   * This uniformly respects system dark mode, battery saver, and in-app AMOLED theme settings.

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Secondary Map Dark Styling)**:
  * *Given* the app running in Dark Mode (or AMOLED mode),
  * *When* viewing route previews in `PathPreviewMap`, cluster cards in `WorkoutClusterComponents`, or dialog mini-maps in `EditKnownLocationDialog`,
  * *Then* the map SHALL render with `MapType.NORMAL` and custom dark vector tile styling (`R.raw.map_style_dark`).
* **Criterion 2 (Elimination of White Flashes)**:
  * *Given* the app running in Dark Mode,
  * *When* any map composable mounts, loads tiles asynchronously, or pans into uncached boundaries,
  * *Then* the underlying container background SHALL render `#121212` (zero white flashes).
* **Criterion 3 (Light Mode Baseline)**:
  * *Given* the app running in Light Mode,
  * *When* viewing any map composable,
  * *Then* the map SHALL render with `MapType.TERRAIN` and null style options, preserving topographic shading.

### 1.4 System Invariants
* Existing map zoom behavior, polyline drawing, marker states, and GPS tracking MUST NOT be altered.
* Single-thread persistence on `dbDispatcher` and ViewModels remain completely untouched.
* Singleton caching in `DarkMapStyle` (<10 KB RAM, <3ms parse) is preserved across all call sites.
* 100% full-suite unit test pass rate (`./gradlew testDebugUnitTest`).

---

## 2. Requirement Archaeology & Chesterton's Fence Audit (REQ-PRO-022)

### Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-MAP-021` targeting [ATrainingTrackerMap.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/ATrainingTrackerMap.kt) and [DarkMapStyle.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/DarkMapStyle.kt).
* **Historical Origin & Commit Trace**: Commit `5d8a0c23` (ATT-1266, 2026-09-12).
* **Root Reason for Existing Formulation**: ATT-1266 was scoped specifically to live route tracking in the cockpit (`ATrainingTrackerMap`). Secondary map views (mini-maps, previews, cluster heatmaps, lap dialogs) were left with hardcoded `MapType.TERRAIN` because a reusable turnkey resolver was not yet centralized in `DarkMapStyle.kt`.
* **Preservation of Core Invariants**: Extending `REQ-MAP-021` to encompass all secondary map views preserves the light mode baseline (`MapType.TERRAIN`, null style options), singleton style caching (<10 KB RAM, <3ms parse), exception fallback to `MapType.NORMAL`, and custom polyline colors.

---

## 3. Test Specification (TST-MAP-023)

### Test Case 1: Dynamic MapProperties Resolution & Light Fallback (`TST-MAP-023.1`)
* **Scope**: Automated Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/DarkMapStyleResolutionTest.kt`
* **Preconditions**: `DarkMapStyle.resolveMapProperties` implemented.
* **Action**:
  1. Invoke `resolveMapProperties(isDark = true, context, isMyLocationEnabled = false)`: assert `mapType == MapType.NORMAL`, `mapStyleOptions != null`, `isMyLocationEnabled == false`.
  2. Invoke `resolveMapProperties(isDark = false, context, isMyLocationEnabled = false)`: assert `mapType == MapType.TERRAIN`, `mapStyleOptions == null`, `isMyLocationEnabled == false`.
  3. Invoke `resolveMapProperties(isDark = true, context, isMyLocationEnabled = true)`: assert `isMyLocationEnabled == true`.
  4. Invoke `resolveMapProperties(isDark = false, context, lightMapType = MapType.NORMAL)`: assert `mapType == MapType.NORMAL` and `mapStyleOptions == null`.
* **Expected Result**: Assertions pass 100%.

### Test Case 2: Source Code & Anti-Flash Audit (`TST-MAP-023.2`)
* **Scope**: Automated Static Analysis / Source Audit Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/DarkMapStyleResolutionTest.kt`
* **Action**:
  1. Inspect `PathPreviewMap.kt`, `EditKnownLocationDialog.kt`, `WorkoutClusterComponents.kt`, `ManualClusterScreen.kt`, `PeriodSummaryCard.kt`, `LapEditBottomSheet.kt`, `ImportBackupTabsScreen.kt`, and `ATrainingTrackerMap.kt`.
  2. Assert that none contain hardcoded unconditioned `MapProperties(mapType = MapType.TERRAIN)`.
  3. Assert that all map composables invoke `DarkMapStyle.resolveMapProperties` or dynamic `resolveMapProperties`.
  4. Assert that map containers define `Modifier.background(...)` to prevent white tile loading flashes.
* **Expected Result**: All source files pass inspection with 0 violations.

### Test Case 3: 9-Language Localization Audit (`TST-MAP-023.3`)
* **Scope**: Automated Localization Parity Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/localization/TranslationParityTest.kt`
* **Expected Result**: 100% parity across all 9 supported locales.

### Test Case 4: Clean-Room Full Suite Regression (`TST-MAP-023.4`)
* **Scope**: Full Clean-Room Test Suite
* **Command**: `./gradlew testDebugUnitTest`
* **Expected Result**: 100% pass rate (0 failures, 0 regressions).

---

## 4. Traceability Matrix

| Test Case ID | Test Scope | Component Under Test | Requirement ID | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-MAP-023.1` | Unit | `DarkMapStyle.resolveMapProperties` | `REQ-MAP-021` | Specified |
| `TST-MAP-023.2` | Static/Audit | Secondary Map Composables | `REQ-MAP-021` | Specified |
| `TST-MAP-023.3` | Localization | `TranslationParityTest` | `REQ-MAP-021`, `REQ-UI-106` | Specified |
| `TST-MAP-023.4` | Regression | `./gradlew testDebugUnitTest` | `REQ-PRO-001` | Specified |
