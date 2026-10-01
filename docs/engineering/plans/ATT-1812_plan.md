# Stage 3: Implementation Plan - ATT-1812: Restore map preview visibility on detailed workout inspection screen

**Ticket**: [ATT-1812](https://rainerblind.atlassian.net/browse/ATT-1812)  
**Sub-task**: [ATT-1846](https://rainerblind.atlassian.net/browse/ATT-1846) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-111](https://rainerblind.atlassian.net/browse/ATT-111) (*Aftermath: Compact Post-Workout Visual Analytics & Graphs*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.7`  
**Requirement Mapping**: `REQ-UI-213` (*Aftermath: Resilient Map Preview Visibility & Scrollable Telemetry Layout Architecture*)  
**Test Mapping**: `TST-UI-167` (*Aftermath Resilient Map Preview Visibility & Scrollable Telemetry Layout Verification*)  
**Branch**: `feature/ATT-1812`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-01  

---

## 1. Problem Description & Background

In Sprint 2026-40.6 review on physical hardware (Pixel 10), navigating to the detailed workout inspection screen (`TrackOnMapScreen` / `MapDetailLayout`) revealed that the map preview is no longer visible. Non-weighted children in `MapDetailLayout` (`WorkoutHeader` ~150 dp, `ElevationProfile` ~160 dp, three telemetry line graphs ~480 dp, and analytics cards ~350 dp) demanded over 950–1100 dp, starving the `Modifier.weight(1f)` allocated to the Map down to `0 dp`.

Furthermore, because the root `Column` was unscrollable, lower cards were clipped and inaccessible below the fold, and workout snapshot sharing (`combineWorkoutAndShare`) failed due to an unmeasured 0x0 map bitmap.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-UI-213` (*Aftermath: Resilient Map Preview Visibility & Scrollable Telemetry Layout Architecture*)
* **Test Mapping**: `TST-UI-167` (*Aftermath Resilient Map Preview Visibility & Scrollable Telemetry Layout Verification*)
  * `[TST-UI-167.1]`: `MapDetailLayoutTest.kt` visual and layout contract unit tests.
  * `[TST-UI-167.2]`: Multi-screen regression tests in `ElevationProfileLayoutTest.kt`, `LiveSegmentSheetLayoutTest.kt`, and `BottomSheetVisualContractTest.kt`.
  * `[TST-UI-167.3]`: Multi-metric scrubbing and snapshot sharing contracts in `TelemetryMetricGraphTest.kt`.
  * `[TST-UI-167.4]`: 9-language localization audit in `TelemetryMetricLocalizationTest.kt`.
  * `[TST-UI-167.5]`: Clean-room full test suite regression (`./gradlew testDebugUnitTest`).

---

## 3. System Invariants & Preserved Behavior

1. **Zero Unintended Regressions**:
   - `RouteOnMapScreen` and `SegmentOnMapScreen` retain full-screen map expansion (`Modifier.weight(1f)`) with zero empty whitespace below the elevation profile.
   - `LIveSegmentSheet.kt` (`showMap = false`) strictly preserves compact `wrapContentHeight` bottom sheet geometry with unified surface styling (`REQ-UI-196`, `ATT-1735`).
2. **Gesture Isolation**:
   - The Map is strictly kept outside the vertical scroll container, guaranteeing native Google Maps touch handling (pan, pinch-to-zoom, rotate) with zero parent touch stealing.
3. **Synchronized Multi-Chart Scrubbing**:
   - Cross-chart scrubbing (`selectedDistance`) across Elevation, HR, Pace/Speed, Power, and the Map location pin remains synchronously wired.
4. **Snapshot Sharing Integrity**:
   - `elevationLayer` and `analyticsLayer` wrap their content inside the scroll container, capturing full graphics bitmaps for `combineWorkoutAndShare`.
5. **Subtask Self-Sufficiency & Parent Human Gate**:
   - Subtasks transition directly to `Erledigt` upon passing Gate audit via `freigabe`.
   - The parent ticket `ATT-1812` transitions only to `Final Review (Human)` and remains assigned to `human`.

---

## 4. Proposed Architectural Changes

### Component 1: `MapDetailLayout.kt` (UI Layout Architecture)
* **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapDetailLayout.kt`
* **Changes**:
  1. Parameter signature: Make `analyticsContent` nullable:
     `analyticsContent: (@Composable ColumnScope.() -> Unit)? = null`
  2. Dynamic overflow detection:
     ```kotlin
     val hasTelemetryGraphs = showZoomControls && activeScrubPath != null && (
         TelemetryMetricUtils.hasHeartRateData(activeScrubPath) ||
         TelemetryMetricUtils.hasSpeedData(activeScrubPath) ||
         TelemetryMetricUtils.hasPowerData(activeScrubPath)
     )
     val hasScrollableContent = analyticsContent != null || hasTelemetryGraphs
     ```
  3. Map Box sizing:
     - When `hasScrollableContent == true`: Apply `Modifier.weight(1f).heightIn(min = 240.dp).fillMaxWidth()`.
     - When `hasScrollableContent == false`: Apply `Modifier.weight(1f).fillMaxWidth()`.
  4. Lower Container:
     - Wrap Section 3 (Elevation & Telemetry Graphs) and Section 4 (Analytics) in a dedicated Column:
       ```kotlin
       val lowerModifier = if (showMap && hasScrollableContent) {
           Modifier
               .weight(1.2f)
               .fillMaxWidth()
               .verticalScroll(rememberScrollState())
       } else {
           Modifier.fillMaxWidth().wrapContentHeight()
       }
       ```
  5. Conditional analytics rendering: Only render Section 4 Surface when `analyticsContent != null`.

### Component 2: `MapDetailLayoutTest.kt` (Automated Contract Tests)
* **File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/MapDetailLayoutTest.kt`
* **Changes**:
  - Implement tests verifying default parameterization, dynamic overflow detection, min height application on Map Box, and scroll container enablement.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Implement Adaptive Sizing & Scroll Container in `MapDetailLayout.kt`
* **Target File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapDetailLayout.kt`
* **Changes**:
  - Add nullable default for `analyticsContent`.
  - Add `hasTelemetryGraphs` and `hasScrollableContent` state evaluation.
  - Apply `heightIn(min = 240.dp)` on Map Box when `hasScrollableContent` is true.
  - Wrap lower sections (elevation + telemetry graphs + analytics) in `Column(lowerModifier)`.

### Step 2: Create Comprehensive Layout Contract Tests in `MapDetailLayoutTest.kt`
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/MapDetailLayoutTest.kt`
* **Changes**:
  - Author test assertions for `heightIn(min = 240.dp)`, `verticalScroll`, `hasTelemetryGraphs`, and `hasScrollableContent`.

### Step 3: Run Targeted Unit & Contract Tests
* **Command**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.map.*" --tests "com.atrainingtracker.trainingtracker.ui.segments.*"`
* **Expected Result**: 100% test pass rate across map and segment layout suites.

---

## 6. Verification & Rollback Plan

* **Verification**: Run targeted layout tests during construction, followed by clean-room full test suite regression (`./gradlew testDebugUnitTest`), deployment to Pixel 10 hardware, and visual inspection of the workout detail screen.
* **Rollback Plan**: Git branch isolation (`feature/ATT-1812`) permits full rollback via `git reset --hard HEAD~1` or switching back to `sprint/2026-40.7` without leaving orphaned modifications.
