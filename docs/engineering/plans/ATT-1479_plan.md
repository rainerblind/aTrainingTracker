# Stage 3: Implementation Plan - ATT-1479: Universal Dark Map Styling & Anti-Flash Protection

**Ticket**: [ATT-1479](https://atrainingtracker.atlassian.net/browse/ATT-1479)  
**Sub-task**: [ATT-1538](https://atrainingtracker.atlassian.net/browse/ATT-1538) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-1157](https://atrainingtracker.atlassian.net/browse/ATT-1157) (*Optimize dark mode*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.2`  
**Requirement Mapping**: `REQ-MAP-021` (*Dark Mode Map Styling for Live Route Tracking, Navigation, Cockpit, and Secondary Map Views*)  
**Test Mapping**: `TST-MAP-023.1`, `TST-MAP-023.2`, `TST-MAP-023.3`, `TST-MAP-023.4`  
**Branch**: `feature/ATT-1479`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-09-28  

---

## 1. Problem Description & Background

While `ATrainingTrackerMap` received dark styling in ATT-1266, secondary preview maps, cluster heatmaps, lap editors, and dialog mini-maps across the app instantiate `GoogleMap` with hardcoded `MapType.TERRAIN` or default unstyled properties. Furthermore, map containers lack explicit dark background styling, causing the underlying light surface to flash white while tiles load over the network. Establishing a universal resolver in `DarkMapStyle.kt` and enforcing double-layer anti-flash background protection eliminates all white screen glare in Dark Mode.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-MAP-021` (*Dark Mode Map Styling for Live Route Tracking, Navigation, Cockpit, and Secondary Map Views*)
* **Test Mapping**:
  * `TST-MAP-023.1` (*Unit test: Dynamic MapProperties resolution, dark vector tile loading, and light fallback*)
  * `TST-MAP-023.2` (*Static audit: Verification of dynamic properties and anti-flash container background in all secondary map composables*)
  * `TST-MAP-023.3` (*Localization parity: 9-language translation validation via TranslationParityTest*)
  * `TST-MAP-023.4` (*Regression: Full test suite execution `./gradlew testDebugUnitTest`*)

---

## 3. System Invariants & Preserved Behavior

1. **Light Mode Baseline Preservation**: When `isDark == false`, maps continue to use standard light styling (`MapType.TERRAIN` with null style options), preserving topographic contours.
2. **Singleton Memory & Performance Efficiency**: Map style options in `DarkMapStyle` remain singleton-cached (<10 KB RAM, <3ms parse), preventing repeated disk I/O on recompositions.
3. **Graceful Fallback on Parse Failure**: If `MapStyleOptions` JSON parsing fails, `DarkMapStyle` catches the exception and falls back safely to unstyled `MapType.NORMAL`.
4. **Polyline & Overlay Hierarchy**: Custom route colors, Strava segments, cluster heatmaps, and live tracking cyan polylines remain completely intact.
5. **Subtask Self-Sufficiency**: Subtasks transition directly to `Erledigt` upon passing Gate audit via `freigabe`.
6. **Parent Human Gate Invariance**: Terminal completion of parent tickets remains reserved for the human user in `Final Review (Human)`. AI agents never transition parent tickets to `Erledigt`.

---

## 4. Proposed Architectural Changes

### Component 1: `DarkMapStyle.kt` Central Factory (`SWE.2`)
* Add `resolveMapProperties(isDark: Boolean, context: Context, isMyLocationEnabled: Boolean = false, lightMapType: MapType = MapType.TERRAIN): MapProperties`.
* When `isDark == true`, retrieve `DarkMapStyle.getMapStyleOptions(context)` and return `MapProperties(mapType = MapType.NORMAL, mapStyleOptions = styleOptions, isMyLocationEnabled = isMyLocationEnabled)`.
* When `isDark == false`, return `MapProperties(mapType = lightMapType, mapStyleOptions = null, isMyLocationEnabled = isMyLocationEnabled)`.

### Component 2: Secondary Map Composables & Container Anti-Flash (`SWE.2`)
* In each secondary map composable:
  * Detect dark state: `val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f`.
  * Compute `val mapProperties = remember(isDark, context) { DarkMapStyle.resolveMapProperties(isDark, context) }`.
  * Enforce container/map background: `Modifier.background(if (isDark) Color(0xFF121212) else Color.White)`.
* Target call sites:
  1. `ATrainingTrackerMap.kt`: Add `.background(...)` to `GoogleMap` container modifier.
  2. `PathPreviewMap.kt`: Replace `MapProperties(mapType = MapType.TERRAIN)` with `mapProperties` and add container background.
  3. `EditKnownLocationDialog.kt`: Update `LocationMiniMap` with `mapProperties` and container background.
  4. `WorkoutClusterComponents.kt`: Update `ClusterMapPreview` and `ClusterSummaryHeader` with `mapProperties` and container background.
  5. `ManualClusterScreen.kt`: Update manual map canvas with `DarkMapStyle.resolveMapProperties(isDark, context, isMyLocationEnabled = currentLocation != null)` and container background.
  6. `PeriodSummaryCard.kt`: Update `PeriodMapPreview` with `mapProperties` and container background.
  7. `LapEditBottomSheet.kt`: Update `LapMapSection` with `mapProperties` and container background.
  8. `ImportBackupTabsScreen.kt`: Update cluster backup import map preview with `mapProperties` and container background.

### Component 3: Automated Unit & Audit Test Suite (`SWE.4`)
* Create `app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/DarkMapStyleResolutionTest.kt`:
  * Verify `resolveMapProperties` behavior in dark and light modes.
  * Verify light map type fallback customization.
  * Audit secondary map source files ensuring no unconditioned `MapProperties(mapType = MapType.TERRAIN)` remain and all define container background anti-flash protection.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Implement `DarkMapStyle.resolveMapProperties`
* Target: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/DarkMapStyle.kt`
* Changes: Add `resolveMapProperties` with full parameterization.

### Step 2: Update Secondary Map Composables & Container Anti-Flash
* Target: `ATrainingTrackerMap.kt`, `PathPreviewMap.kt`, `EditKnownLocationDialog.kt`, `WorkoutClusterComponents.kt`, `ManualClusterScreen.kt`, `PeriodSummaryCard.kt`, `LapEditBottomSheet.kt`, `ImportBackupTabsScreen.kt`.
* Changes: Integrate `DarkMapStyle.resolveMapProperties` and `Modifier.background(if (isDark) Color(0xFF121212) else Color.White)`.

### Step 3: Implement Automated Unit Test Suite
* Target: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/DarkMapStyleResolutionTest.kt`
* Verification: Run targeted test `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.map.DarkMapStyleResolutionTest"`.

### Step 4: Verification & Clean-Room Regression
* Run targeted tests:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.map.DarkMapStyleResolutionTest"
  ./gradlew testDebugUnitTest --tests "*TranslationParityTest*"
  ```
* Defer full regression suite to Stage 5.

---

## 6. Verification & Rollback Plan

* **Verification**:
  * Unit tests (`DarkMapStyleResolutionTest`, `MapThemeResolutionTest`) in Stage 4.
  * Full regression test suite (`./gradlew testDebugUnitTest`) in Stage 5.
* **Rollback**: Branch isolation (`feature/ATT-1479`) enables full revert without impacting `sprint/2026-40.2` or other features.
