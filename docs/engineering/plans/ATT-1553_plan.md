# Stage 3: Implementation Plan - ATT-1553: [Bug] [Map] [Dark Mode] Initial bright/white map flash when loading list views with maps

**Ticket**: [ATT-1553](https://atrainingtracker.atlassian.net/browse/ATT-1553)  
**Sub-task**: [ATT-1556](https://atrainingtracker.atlassian.net/browse/ATT-1556) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-1157](https://atrainingtracker.atlassian.net/browse/ATT-1157) (*Optimize dark mode*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.3`  
**Requirement Mapping**: `REQ-MAP-021`  
**Test Mapping**: `TST-MAP-023`  
**Branch**: `feature/ATT-1553`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-09-28  

---

## 1. Problem Description & Background

In dark mode, when list screens containing map preview cards (`PeriodSummaryCard` in period lists, `PathPreviewMap` in route lists, and cluster preview cards in `WorkoutClusterComponents`) mount or scroll, the native `MapView` surface of Google Play Services Maps SDK briefly paints default cream/white vector tiles before the asynchronous dark style JSON (`res/raw/map_style_dark.json`) can be dispatched and rendered. Because `Modifier.background(Color(0xFF121212))` on `GoogleMap` is drawn behind the native view, this creates a transient 50–300ms white glare on every newly visible card during list scrolling.

The solution is an **Active Anti-Flash Surface Mask** (`DarkMapAntiFlashOverlay`) rendered directly on top of `GoogleMap` within the map container until `onMapLoaded` fires.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-MAP-021` (*Dark Mode Map Styling for Live Route Tracking, Navigation, Cockpit, and Secondary Map Views*)
* **Test Mapping**: `TST-MAP-023` (*Dark Mode Map Styling, Vector Tile Configuration & Anti-Flash Surface Mask Verification*)

---

## 3. System Invariants & Preserved Behavior

1. **Light Mode Baseline Invariance**: Standard light mode (`MapType.TERRAIN`, null style options) MUST NOT render any dark mask (`isDark == false` bypasses the overlay completely).
2. **Zero Overhead in Steady State**: Once `onMapLoaded` fires, the overlay is dismissed with zero ongoing layout, draw, or recomposition overhead.
3. **Thread Safety & Dispatcher Affinity**: Zero database or background service modifications; UI operations remain strictly on `Dispatchers.Main`.
4. **Subtask Direct Completion**: Subtasks transition directly to `Erledigt` upon passing Gate audit via `freigabe`.
5. **Parent Human Gate Invariance**: Terminal completion of parent tickets remains reserved exclusively for the human user in `Final Review (Human)`.

---

## 4. Proposed Architectural Changes

### Component 1: `DarkMapAntiFlashOverlay` in `DarkMapStyle.kt`
* **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/DarkMapStyle.kt`
* **Implementation**:
  ```kotlin
  @Composable
  fun DarkMapAntiFlashOverlay(
      isMapLoaded: Boolean,
      isDark: Boolean,
      modifier: Modifier = Modifier,
      backgroundColor: Color = Color(0xFF121212)
  ) {
      if (isDark && !isMapLoaded) {
          Box(
              modifier = modifier
                  .fillMaxSize()
                  .background(backgroundColor)
          )
      }
  }
  ```

### Component 2: List Preview Map Layering
* **Target Files**:
  1. `app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/PathPreviewMap.kt`:
     * Wrap `GoogleMap` in a `Box(modifier = modifier.background(if (isDark) Color(0xFF121212) else Color.White))`.
     * Add `DarkMapAntiFlashOverlay(isMapLoaded = isMapLoaded, isDark = isDark)`.
  2. `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/periodlist/PeriodSummaryCard.kt`:
     * In `PeriodMultiWorkoutMap`, add `DarkMapAntiFlashOverlay(isMapLoaded = isMapLoaded, isDark = isDark)` on top of `GoogleMap` inside the map container `Box`.
  3. `app/src/main/java/com/atrainingtracker/trainingtracker/ui/clusters/WorkoutClusterComponents.kt`:
     * In `WorkoutClusterCard` (line 280) and `ClusterRepresentativeCard` (line 500), add `DarkMapAntiFlashOverlay(isMapLoaded = isMapLoaded, isDark = isDark)` on top of `GoogleMap` inside the `Box`.
  4. Secondary Map Screens (`ManualClusterScreen.kt`, `LapEditBottomSheet.kt`, `EditKnownLocationDialog.kt`):
     * Track `isMapLoaded`, set `onMapLoaded = { isMapLoaded = true }`, and add `DarkMapAntiFlashOverlay`.

### Component 3: Automated Verification Unit Test
* **File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/DarkMapAntiFlashOverlayTest.kt`
* Validates state-driven visibility matrix (Dark+Unloaded = visible, Dark+Loaded = gone, Light+Unloaded = gone, Light+Loaded = gone).

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Add `DarkMapAntiFlashOverlay` to `DarkMapStyle.kt`
* Define the reusable `@Composable fun DarkMapAntiFlashOverlay` in `DarkMapStyle.kt`.

### Step 2: Update List Preview Map Composables
* Update `PathPreviewMap.kt` to place `DarkMapAntiFlashOverlay` over `GoogleMap`.
* Update `PeriodSummaryCard.kt` (`PeriodMultiWorkoutMap`) to place `DarkMapAntiFlashOverlay` over `GoogleMap`.
* Update `WorkoutClusterComponents.kt` in both preview map locations to place `DarkMapAntiFlashOverlay` over `GoogleMap`.

### Step 3: Update Secondary Dialog Mini-Maps
* Update `ManualClusterScreen.kt`, `LapEditBottomSheet.kt`, and `EditKnownLocationDialog.kt` with `isMapLoaded` tracking and `DarkMapAntiFlashOverlay`.

### Step 4: Author Unit Tests
* Create `DarkMapAntiFlashOverlayTest.kt` verifying conditional rendering.

### Step 5: Execute Targeted Tests
* Run `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.map.*"`.

---

## 6. Verification & Rollback Plan

* **Verification**: Targeted module test execution in Stage 4, followed by full clean-room suite (`./gradlew testDebugUnitTest`) in Stage 5.
* **Rollback Plan**: All changes are isolated on `feature/ATT-1553`. In the event of an unexpected regression, git revert cleanly restores previous state without impacting `develop` or other sprint tickets.
