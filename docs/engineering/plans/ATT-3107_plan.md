# Stage 3: Implementation Plan - ATT-3107: Display elevation profile with adequate vertical height when map is disabled on tracking screen

**Ticket**: [ATT-3107](https://atrainingtracker.atlassian.net/browse/ATT-3107)  
**Sub-task**: [ATT-3150](https://atrainingtracker.atlassian.net/browse/ATT-3150) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-355](https://atrainingtracker.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `2026-41.8`  
**Requirement Mapping**: `REQ-UI-339` (*Dynamic Cockpit Weight Allocation & Flexible Vertical Expansion for Elevation Profile when Map is Disabled*)  
**Test Mapping**: `TST-UI-299` (*Dynamic Cockpit Layout Weighting, Sensor Grid Scrollability & Elevation Profile Vertical Expansion Verification*)  
**Branch**: `feature/ATT-3107`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-10  

---

## 1. Problem Description & Background

On the live tracking screen (`SensorGridScreen` in `ScreenMode.TRACKING`), toggling the map off while keeping the elevation profile enabled (`showMap == false` and `showElevationProfile == true`) causes the elevation profile to render as an unusable 15–20dp slit at the very bottom of the screen.

Root cause analysis showed that:
1. The sensor grid `Column` had no vertical weight constraint, taking all vertical height required by configured sensor tiles.
2. The map `Box` had `weight(1f)`, but was not emitted when `showMap == false`, discarding flexible weight.
3. `ElevationProfile` hardcoded `.height(totalCanvasHeight)` unconditionally, preventing expansion even when inside a weighted container.

This plan details the atomic steps to constrain the sensor grid with `weight(1f, fill = false)`, dynamically allocate `weight(1f)` to the elevation profile when `!showMap`, decouple canvas height with `fillParentHeight: Boolean = false`, and add contract tests.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-UI-339` (*Dynamic Cockpit Weight Allocation & Flexible Vertical Expansion for Elevation Profile when Map is Disabled*)
* **Test Mapping**: `TST-UI-299` (*Dynamic Cockpit Layout Weighting, Sensor Grid Scrollability & Elevation Profile Vertical Expansion Verification*)

---

## 3. System Invariants & Preserved Behavior

1. **Minimum Vertical Height Invariant**: When `showMap == false`, the elevation profile expands to fill remaining space ($\ge 120\text{dp}$), rendering the full curve and gradient shading without clipping.
2. **List Preview Backward Compatibility**: All list previews (`WorkoutSummary.kt`, `RouteItem.kt`, `SegmentItem.kt`) continue using fixed/wrapped height via default `fillParentHeight = false`.
3. **Map + Elevation Profile Coexistence**: When `showMap == true`, the map retains primary `weight(1f)`, and the elevation profile renders at its standard adaptive height below it.
4. **Sensor Grid Wrap-Content**: When sensor rows are few (1–2 rows), `fill = false` ensures the grid wraps naturally without forced expansion.
5. **Sparse Path Safety**: When `state.pathPoints.size < 2`, the elevation profile is not rendered, avoiding empty stretched canvas artifacts.
6. **Sub-Task Self-Sufficiency**: Sub-tasks transition autonomously to `Erledigt` upon passing automated Gate audits (`freigabe`).
7. **Parent Human Gate Invariance**: Final completion of parent ticket `ATT-3107` is reserved exclusively for the human user in `Final Review (Human)`.

---

## 4. Proposed Architectural Changes

### Step 1: `ElevationProfile.kt` - Add `fillParentHeight: Boolean = false`
In `app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/ElevationProfile.kt`:
1. Add `fillParentHeight: Boolean = false` to both `ElevationProfile` composables.
2. Forward `fillParentHeight` from the encoded strings overload to the `List<PathPoint>` overload.
3. Update `baseCanvasModifier`:
   ```kotlin
   val baseCanvasModifier = if (fillParentHeight) {
       Modifier
           .fillMaxWidth()
           .defaultMinSize(minHeight = totalCanvasHeight)
           .fillMaxHeight()
   } else {
       Modifier
           .fillMaxWidth()
           .height(totalCanvasHeight)
   }
   ```

### Step 2: `SensorGridScreen.kt` - Dynamic Layout Weighting
In `app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/SensorGridScreen.kt`:
1. On the sensor grid `Column`:
   ```kotlin
   Column(
       modifier = Modifier
           .fillMaxWidth()
           .weight(1f, fill = false)
           .verticalScroll(rememberScrollState()),
       horizontalAlignment = Alignment.CenterHorizontally,
       ...
   )
   ```
2. On the Elevation Profile section:
   ```kotlin
   // 3. The Elevation Profile (Below the Map or taking remaining space if map is hidden)
   if (state.showElevationProfile && state.pathPoints.size >= 2) {
       Surface(
           modifier = Modifier
               .fillMaxWidth()
               .then(
                   if (!state.mapState.showMap) Modifier.weight(1f) else Modifier
               )
               .padding(top = 8.dp)
       ) {
           ElevationProfile(
               pathPoints = state.pathPoints,
               currentDistance = state.pathPoints.lastOrNull()?.distance,
               fillParentHeight = !state.mapState.showMap,
               modifier = if (!state.mapState.showMap) Modifier.fillMaxSize() else Modifier.fillMaxWidth()
           )
       }
   }
   ```

### Step 3: Contract Tests in `SensorGridScreenLayoutTest.kt`
Create `app/src/test/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/SensorGridScreenLayoutTest.kt`:
- Verify sensor grid `Column` specifies `weight(1f, fill = false)`.
- Verify elevation profile container specifies `if (!state.mapState.showMap) Modifier.weight(1f) else Modifier`.
- Verify `ElevationProfile` receives `fillParentHeight = !state.mapState.showMap`.
- Verify `pathPoints.size >= 2` guard.

### Step 4: Contract Tests in `ElevationProfileLayoutTest.kt`
Add test verifying:
- Both `ElevationProfile` overloads declare `fillParentHeight: Boolean = false`.
- Conditional attachment of `.height(totalCanvasHeight)` vs `.defaultMinSize(minHeight = totalCanvasHeight).fillMaxHeight()`.

---

## 5. Verification Plan

1. **Targeted Unit Tests**:
   - `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.map.ElevationProfileLayoutTest"`
   - `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.tracking.tracking.SensorGridScreenLayoutTest"`
2. **Clean-Room Full Suite Regression**:
   - `./gradlew testDebugUnitTest` verifying 100% test pass rate with zero regressions.
3. **Living Documentation Synchronization**:
   - Update `REQ-UI-339` and `TST-UI-299` from `Draft` to `Verified`.
   - Run `python3 tools/verify_requirement_governance.py --base-ref sprint/2026-41.8`.
