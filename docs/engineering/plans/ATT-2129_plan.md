# Stage 3: Implementation Plan - ATT-2129: Telemetry Graphs and Zone Cards in Workout Summary Do Not Open Workout Details on Click

**Ticket**: [ATT-2129](https://rainerblind.atlassian.net/browse/ATT-2129)  
**Sub-task**: [ATT-2134](https://rainerblind.atlassian.net/browse/ATT-2134) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-111](https://rainerblind.atlassian.net/browse/ATT-111) (*Compact Post-Workout Visual Analytics & Graphs*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `Sprint 2026-40.13`  
**Requirement Mapping**: `REQ-UI-247`  
**Test Mapping**: `TST-UI-206`  
**Branch**: `feature/ATT-2129`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-03  

---

## 1. Architecture & SWE.2 Detailed Design

```
+---------------------------------------------------------------------------------+
| WorkoutSummary.kt (Summary List Item)                                           |
|                                                                                 |
| 1. Header (WorkoutHeader) -> onMapClick()                                       |
| 2. Description (WorkoutDescription) -> mapClickModifier                         |
| 3. Main Details (WorkoutDetails) -> mapClickModifier                            |
| 4. Extrema (WorkoutExtrema) -> mapClickModifier                                 |
| 5. Map Preview (PathPreviewMap) -> onMapClick()                                 |
| 6. Elevation Profile (ElevationProfile) -> .clickable { onMapClick() }          |
|                                                                                 |
| 7. Telemetry Metric Graphs (NEW: REQ-UI-247):                                   |
|    +-- Column(modifier = mapClickModifier.fillMaxWidth())                       |
|        +-- Text("Pace / Speed / HR / Power")                                    |
|        +-- TelemetryMetricGraph(enableGestures = false)                         |
|            +-- Canvas (NO pointerInput attached -> click passes to parent)      |
|                                                                                 |
| 8. Zone Distribution Cards (NEW: REQ-UI-247):                                   |
|    +-- HeartRateZoneDistributionCard(modifier = ... .then(mapClickModifier))     |
|    +-- PowerZoneDistributionCard(modifier = ... .then(mapClickModifier))         |
+---------------------------------------------------------------------------------+
```

### Detailed Design & Gesture Isolation Strategy:
1. **Passive Canvas in Summary List**:
   When `enableGestures = false` is supplied to `TelemetryMetricGraph`:
   ```kotlin
   val baseCanvasModifier = Modifier.fillMaxWidth().height(110.dp)
   val canvasModifier = if (enableGestures) {
       baseCanvasModifier.pointerInput(totalSpan, isTimeDomain, isPanMode) { ... }
   } else {
       baseCanvasModifier
   }
   ```
   Omitting `pointerInput` ensures zero touch interception, allowing single taps to bubble up directly to `mapClickModifier` and vertical scrolling gestures to pass uninhibited to the parent `LazyColumn` / `verticalScroll` container.
2. **Interactive Canvas in MapDetailLayout**:
   In `MapDetailLayout.kt`, `TelemetryMetricGraph` is called with default `enableGestures = true`. Interactive scrubbing, panning, and lockstep synchronization remain 100% untouched.
3. **Zone Cards**:
   Passing `.then(mapClickModifier)` to `HeartRateZoneDistributionCard` and `PowerZoneDistributionCard` surfaces `onMapClick()` for card surface taps. Inner segmented buttons retain click absorption for mode toggling without triggering navigation.

---

## 2. Atomic Implementation Steps

### Step 1: Parameterize `TelemetryMetricGraph` with `enableGestures`
* **Target File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/TelemetryMetricGraph.kt`
* **Changes**:
  1. Add parameter `enableGestures: Boolean = true` to `@Composable fun TelemetryMetricGraph(...)`.
  2. Conditionally apply `.pointerInput(totalSpan, isTimeDomain, isPanMode) { ... }` to the `Canvas` modifier only when `enableGestures == true`.
  3. When `enableGestures == false`, use the unmodified base modifier `Modifier.fillMaxWidth().height(110.dp)`.

### Step 2: Wire `mapClickModifier` and `enableGestures = false` in `WorkoutSummary.kt`
* **Target File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/workoutlist/WorkoutSummary.kt`
* **Changes**:
  1. In section 8 (Telemetry Metric Graphs), wrap each metric block (Divider, Heading Text, and `TelemetryMetricGraph`) in a `Column(modifier = mapClickModifier.fillMaxWidth())`.
  2. Pass `enableGestures = false` to each `TelemetryMetricGraph` call (Speed/Pace, Heart Rate, and Power).
  3. In section 9 (Zone Distribution Cards), attach `.then(mapClickModifier)` to `HeartRateZoneDistributionCard` and `PowerZoneDistributionCard`.

### Step 3: Author Contract Unit Tests
* **Target Files**:
  * `app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/TelemetryMetricGraphGesturesContractTest.kt`
  * `app/src/test/java/com/atrainingtracker/trainingtracker/ui/aftermath/workoutlist/WorkoutSummaryClickContractTest.kt`
* **Assertions**:
  * Verify `TelemetryMetricGraph` declares `enableGestures: Boolean = true` with conditional pointerInput.
  * Verify `WorkoutSummary.kt` passes `enableGestures = false` to telemetry graphs and applies `mapClickModifier` to graph containers and zone cards.

### Step 4: Full Clean-Room Regression Verification
* Execute `./gradlew testDebugUnitTest` verifying 100% pass rate.

---

## 3. Invariants & Governance Pre-Check

1. **Zero Production Regressions**: Full unit test suite must pass with 0 failures, 0 errors.
2. **Backward Compatibility**: All existing callers of `TelemetryMetricGraph` in `MapDetailLayout` retain default `enableGestures = true`.
3. **Fluid Vertical Scrolling**: Summary list vertical scrolling must not stutter or trigger navigation on drag.
4. **Parent Ticket Terminal State**: Terminal state remains `Final Review (Human)`.
