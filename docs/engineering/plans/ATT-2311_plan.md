# Stage 3 Implementation Plan: ATT-2311 - Position Elevation Profile Below Map and Enable Interactive Zooming in Routes and Segments

**Ticket**: [ATT-2311](https://rainerblind.atlassian.net/browse/ATT-2311)  
**Sub-task**: [ATT-2351](https://rainerblind.atlassian.net/browse/ATT-2351) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-66](https://rainerblind.atlassian.net/browse/ATT-66) (*Improve Routes*) / [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-40.15`  
**Requirement Mapping**: `REQ-UI-267`  
**Test Spec Mapping**: `TST-UI-226`  
**Branch**: `feature/ATT-2311`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-04  

---

## 1. Architecture & Component Decomposition (SWE.2)

### 1.1 Structural Component Mapping
```
MapDetailLayout.kt
  ├── Upper Section: Header & Collapsible Metadata (zIndex = 1f)
  └── Lower Viewport: BoxWithConstraints
        ├── Branch 1: if (showMap && hasLowerSection) [Routes, Segments, Workouts]
        │     ├── mapBox (Modifier.weight(splitFraction).heightIn(min = 120.dp))
        │     ├── SplitPaneDivider (draggable splitter with double-tap reset)
        │     └── Box (Modifier.weight(1f - splitFraction)) [Lower Viewport Container]
        │           ├── Column
        │           │     ├── GlobalTelemetryZoomToolbar (+, -, reset, pan) [when hasZoomToolbar]
        │           │     └── lowerColumn (ElevationProfile + charts, verticalScroll)
        │           └── ScrubbingTelemetryBadge (synchronized to scrubbed point)
        ├── Branch 2: else if (showMap) [Standalone maps, Heatmap]
        │     └── mapBox (Modifier.fillMaxSize())
        ├── Branch 3: else if (hasScrollableContent) [Trackless/indoor workouts]
        │     └── Full-screen scrollable lowerColumn with ZoomToolbar & ScrubbingOverlay
        └── Branch 4: else if (hasLowerSection) [Bottom sheets: LiveSegmentSheet]
              └── Compact wrapContentHeight container with lowerColumn
```

### 1.2 Lower Section Gating Invariant
Currently:
```kotlin
val hasScrollableContent = metadataContent != null || analyticsContent != null || hasTelemetryGraphs
```
Because routes and segments do not contain telemetry streams (HR, Speed, Power) or metadata slots, `hasScrollableContent` evaluates to `false`. Gating the split pane layout solely on `hasScrollableContent` forced routes and segments into the unaligned fallback `Box`, where `lowerColumn` was drawn on top of the map at `TopStart`.

Solution:
Introduce:
```kotlin
val hasLowerSection = (showElevationProfile || hasTelemetryGraphs) && !activeScrubPath.isNullOrEmpty() || metadataContent != null || analyticsContent != null
```
And gate the split-pane layout on `showMap && hasLowerSection`.

---

## 2. Step-by-Step Atomic Construction Sequence

### Step 1: Update `MapDetailLayout.kt` Viewport Branching (`REQ-UI-267`)
* **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapDetailLayout.kt`
* **Changes**:
  1. Define `val hasLowerSection = (showElevationProfile || hasTelemetryGraphs) && !activeScrubPath.isNullOrEmpty() || metadataContent != null || analyticsContent != null`.
  2. Preserve `val hasScrollableContent` for backward compatibility with existing tests and trackless workout logic.
  3. Replace `if (showMap && hasScrollableContent)` with `if (showMap && hasLowerSection)`.
  4. In the `else` branch, replace the unaligned stacking with explicit mutually exclusive branches:
     - `else if (showMap)`: `mapBox(Modifier.fillMaxSize())`.
     - `else if (hasScrollableContent)`: full-screen scrollable column with `GlobalTelemetryZoomToolbar` and `lowerColumn`.
     - `else if (hasLowerSection)`: compact `wrapContentHeight` column for bottom sheets (`LiveSegmentSheet.kt`).

### Step 2: Update Architectural & Layout Contract Tests (`TST-UI-226`)
* **Files**:
  - `app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/MapDetailLayoutTest.kt`
  - `app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/MapDetailLayoutZoomContractTest.kt`
* **Changes**:
  1. In `MapDetailLayoutTest.kt`:
     - Add `testMapDetailLayout_detectsLowerSection`: asserts that `hasLowerSection` is computed taking into account `showElevationProfile` when `activeScrubPath` is non-empty.
     - Add `testMapDetailLayout_activatesSplitPaneForLowerSection`: asserts that `if (showMap && hasLowerSection)` activates two-pane split with `mapBox` weighted with `splitFraction`, `SplitPaneDivider`, and `lowerColumn` in the lower container.
  2. In `MapDetailLayoutZoomContractTest.kt`:
     - Verify `GlobalTelemetryZoomToolbar` gating and dynamic available height calculations.

### Step 3: Targeted Unit Test Execution
* **Commands**:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.map.*"
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.segments.*"
  ```
* **Success Criteria**: 100% pass rate across all map and segment tests.

---

## 3. Invariants & Risk Mitigation

1. **Non-Breaking Invariant for Workout Details (`TrackOnMapScreen`)**:
   - In `TrackOnMapScreen`, `hasLowerSection` remains `true`. The layout continues to render `mapBox` on top, `SplitPaneDivider` in the middle, and `GlobalTelemetryZoomToolbar` + `lowerColumn` on the bottom.
2. **Non-Breaking Invariant for Standalone Map Views (`WorkoutClusterHeatmapScreen`)**:
   - Standalone map views have `showElevationProfile == false`, `activeScrubPath == null`, so `hasLowerSection == false`. The layout hits `else if (showMap) mapBox(Modifier.fillMaxSize())`, filling 100% of the viewport with no divider or empty space.
3. **Non-Breaking Invariant for Live Tracking Bottom Sheets (`LiveSegmentSheet`)**:
   - In `LiveSegmentSheet`, `showMap == false` and `hasScrollableContent == false`. The layout hits `else if (hasLowerSection)` with `wrapContentHeight()`, preserving compact bottom sheet dimensions without full-screen stretching.
4. **Interactive Zooming & Scrubbing Synchronization**:
   - Zoom scale and pan mode parameters in `GlobalTelemetryZoomToolbar` connect directly to `profileZoomScale`, `viewportStartFraction`, and `isPanMode` of `ElevationProfile`.
   - `selectedDistance` scrubbing updates the map cursor marker via `ScrubMarkerLayer` in `ATrainingTrackerMap`.
5. **No Regressions in Clean-Room Test Suite**:
   - Zero test failures across the unit test suite.
