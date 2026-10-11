# Stage 3: Implementation Plan - ATT-3102: Map camera continuously alternates between center and bottom lookahead position during tracking

**Ticket**: [ATT-3102](https://atrainingtracker.atlassian.net/browse/ATT-3102)  
**Sub-task**: [ATT-3135](https://atrainingtracker.atlassian.net/browse/ATT-3135) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-2564](https://atrainingtracker.atlassian.net/browse/ATT-2564) (*Navigation: Turn-by-Turn Guidance & Cockpit Prompts*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `2026-41.8`  
**Requirement Mapping**: `REQ-MAP-046` (*Unified Viewport Anchor Authority for Follow-Me Map Tracking & Analytical Forward Lookahead Calculation*)  
**Test Mapping**: `TST-MAP-048` (*Unified Viewport Anchor Authority & Lookahead Camera Targeting Verification*)  
**Branch**: `feature/ATT-3102`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-10  

---

## 1. Problem Description & Background

During active workout tracking on the Map tab (`Karte`, observed on Google Pixel 10 during the Sprint 2026-41.7 Joint Review), the map camera continuously alternates every second between two distinct focal positions:
1. Centering the athlete marker in the exact physical middle of the map viewport.
2. Positioning the athlete marker at the lower third (~35% from bottom / ~65% from top via forward lookahead padding).

As uncovered in Stage 1 analysis, this 1 Hz vertical oscillation is caused by a violation of **Rule 33 (Unified Viewport Anchor Authority for Map Tracking)**:
* `ATrainingTrackerMap.kt` applied dynamic top padding to `GoogleMap(contentPadding = PaddingValues(top = topPadding))`.
* Concurrently, `followMeController` in `MapBehaviors.kt` invoked `cameraPositionState.animate(CameraUpdateFactory.newCameraPosition(CameraPosition.builder().target(currentLocation)...), 400)`.
* In the Google Maps Android SDK, `newCameraPosition` places `CameraPosition.target` at the **unpadded physical screen center (50% Y)**. The competing native Google Maps padding mechanism then pulled it down to the lower third, creating a conflicting 1 Hz animation loop at every GPS telemetry tick.

This plan specifies the architecture to unify camera positioning and forward lookahead under a **single spatial authority** inside `followMeController` (`MapBehaviors.kt`), decoupling `contentPadding` on `GoogleMap` in `FOLLOW_ME` mode.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-MAP-046` (*Unified Viewport Anchor Authority for Follow-Me Map Tracking & Analytical Forward Lookahead Calculation*)
* **Test Mapping**: `TST-MAP-048` (*Unified Viewport Anchor Authority & Lookahead Camera Targeting Verification*)

---

## 3. System Invariants & Preserved Behavior

1. **Rule 33 Viewport Anchor Authority**: A single spatial authority (`followMeController`) governs camera positioning and forward lookahead in `FOLLOW_ME` mode. No competing `contentPadding` recalculations in `BoxWithConstraints` may conflict with `cameraPositionState.animate`.
2. **Forward Lookahead Positioning Invariant**: With default $30\%$ padding preference, the athlete remains stably positioned in the lower third (~35% from bottom / ~65% from top), dedicating ~65%-70% of the visible viewport to the forward road ahead.
3. **Speed-Dependent Zoom Curve**: Velocity-dependent zoom scaling ($0\text{ km/h} \to 20.0$, $20\text{ km/h} \to 18.0$) must remain strictly intact (`REQ-MAP-042`).
4. **Perspective Tilt**: Pitch angles from $0^\circ$ to $70^\circ$ must remain supported.
5. **DataStore Settings Intactness**: `mapFollowMeLookaheadPaddingPercent` slider ($0\% - 50\%$) and default value ($30\%$) in Advanced Tuning must continue to control lookahead magnitude.
6. **Non-Follow-Me Modes Untouched**: In `EXPLICIT_BOUNDS`, `TRACK_AND_MARKERS`, `LOCAL_SEGMENTS`, and `FIT_PRIMARY`, `contentPadding` remains `0.dp` and `MapBoundsController` governs camera framing.
7. **Sub-Task Self-Sufficiency**: Sub-tasks transition autonomously to `Erledigt` upon passing automated Gate audits (`freigabe`).
8. **Parent Human Gate Invariance**: Final completion of parent ticket `ATT-3102` is reserved exclusively for the human user in `Final Review (Human)`.

---

## 4. Proposed Architectural Changes

### Component 1: `MapBehaviors.kt` (Unified Authority & Lookahead Math)
1. Implement pure helper function `calculateFollowMeCameraTarget`:
   ```kotlin
   fun calculateFollowMeCameraTarget(
       currentLocation: LatLng,
       bearing: Float,
       zoom: Float,
       lookaheadPaddingPercent: Float = TuningPreferencesDefaults.DEFAULT_MAP_FOLLOW_ME_LOOKAHEAD_PADDING_PERCENT,
       viewportHeightDp: Float = 0f
   ): LatLng {
       if (lookaheadPaddingPercent <= 0f || viewportHeightDp <= 0f) {
           return currentLocation
       }
       val latRad = Math.toRadians(currentLocation.latitude)
       val metersPerDp = (156543.03392 * Math.cos(latRad) / Math.pow(2.0, zoom.toDouble())).toFloat()
       val offsetFraction = (lookaheadPaddingPercent / 100f) * 0.5f
       val offsetMeters = viewportHeightDp * offsetFraction * metersPerDp
       return SphericalUtil.computeOffset(currentLocation, offsetMeters.toDouble(), bearing.toDouble())
   }
   ```
2. Update `followMeController` signature to accept `lookaheadPaddingPercent: Float` and `viewportHeightDp: Float`.
3. In `followMeController`, compute the lookahead target before animating:
   ```kotlin
   val cameraTarget = calculateFollowMeCameraTarget(
       currentLocation = currentLocation,
       bearing = filteredBearing,
       zoom = targetZoom,
       lookaheadPaddingPercent = lookaheadPaddingPercent,
       viewportHeightDp = viewportHeightDp
   )
   cameraPositionState.animate(
       CameraUpdateFactory.newCameraPosition(
           CameraPosition.builder()
               .target(cameraTarget)
               .bearing(filteredBearing)
               .zoom(targetZoom)
               .tilt(effectiveTilt)
               .build()
       ),
       400
   )
   ```

### Component 2: `ATrainingTrackerMap.kt` (Decouple GoogleMap Content Padding)
1. In `ATrainingTrackerMap.kt`, within `BoxWithConstraints`:
   ```kotlin
   val topPadding = 0.dp // REQ-MAP-046: Unified authority in followMeController per Rule 33
   ```
   Set `contentPadding = PaddingValues(0.dp)` on `GoogleMap`.
2. Pass `lookaheadPaddingPercent = tuningConfig.mapFollowMeLookaheadPaddingPercent` and `viewportHeightDp = maxHeight.value` to `followMeController`.

### Component 3: Unit & Contract Tests
1. Author `app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/FollowMeCameraTargetTest.kt` (`TST-MAP-048.1`):
   - Tests projection of target ahead of athlete along cardinal bearings (0°, 90°, 180°, 270°).
   - Tests edge cases: `lookaheadPaddingPercent == 0f`, `viewportHeightDp <= 0f`.
   - Tests scaling across latitudes and zoom levels.
2. Author `app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/FollowMeUnifiedAnchorContractTest.kt` (`TST-MAP-048.2`):
   - Verifies `ATrainingTrackerMap.kt` sets `contentPadding = PaddingValues(0.dp)` (or `topPadding = 0.dp`) in `FOLLOW_ME` mode.
   - Verifies `ATrainingTrackerMap.kt` passes `lookaheadPaddingPercent` and `viewportHeightDp` to `followMeController`.
   - Verifies `followMeController` invokes `calculateFollowMeCameraTarget`.
3. Update `app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/FollowMePaddingContractTest.kt` to align with `REQ-MAP-046`.

### UI Consistency (Rule 23)
* **Reference screen / component**: Live tracking map HUD (`SensorGridScreen` / `ATrainingTrackerMap`).
* **Reused components**: `ATrainingTrackerMap`, `followMeController`, `SphericalUtil`.
* **Theme tokens**: No visual UI elements changed; spatial positioning algorithm only.
* **New one-off styles & justification**: None.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Pre-Gate Verification
* Run `python3 tools/jira_util.py check-gate ATT-3135` to verify Gate 3 status before code modification.

### Step 2: Implement Analytical Lookahead Calculation in `MapBehaviors.kt`
* File: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapBehaviors.kt`
* Add `calculateFollowMeCameraTarget(...)`.
* Update `followMeController` to accept `lookaheadPaddingPercent` and `viewportHeightDp`, calculating `cameraTarget` and passing it to `CameraPosition.builder().target(cameraTarget)`.

### Step 3: Decouple GoogleMap Content Padding in `ATrainingTrackerMap.kt`
* File: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/ATrainingTrackerMap.kt`
* Set `topPadding = 0.dp` / `contentPadding = PaddingValues(0.dp)`.
* Forward `lookaheadPaddingPercent = tuningConfig.mapFollowMeLookaheadPaddingPercent` and `viewportHeightDp = maxHeight.value` into `followMeController`.

### Step 4: Implement Unit and Contract Tests
* Create `app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/FollowMeCameraTargetTest.kt` (`TST-MAP-048.1`).
* Create `app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/FollowMeUnifiedAnchorContractTest.kt` (`TST-MAP-048.2`).
* Update `FollowMePaddingContractTest.kt` to assert unified anchor authority under `REQ-MAP-046`.

### Step 5: Targeted Unit Test Execution
* Execute:
  `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.map.FollowMeCameraTargetTest" --tests "com.atrainingtracker.trainingtracker.ui.map.FollowMeUnifiedAnchorContractTest" --tests "com.atrainingtracker.trainingtracker.ui.map.FollowMeCameraZoomTest" --tests "com.atrainingtracker.trainingtracker.settings.MapCameraTuningPreferencesTest"`
* Confirm 100% pass rate.

---

## 6. Verification & Rollback Plan

* **Verification**: Targeted unit tests, contract tests, and full regression `./gradlew testDebugUnitTest`.
* **Rollback Plan**: In the event of an unexpected regression, git revert cleanly restores previous state.
