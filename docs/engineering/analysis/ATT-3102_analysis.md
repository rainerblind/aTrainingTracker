# Stage 1 Analysis: ATT-3102 - Map camera continuously alternates between center and bottom lookahead position during tracking

**Ticket**: [ATT-3102](https://atrainingtracker.atlassian.net/browse/ATT-3102)  
**Sub-task**: [ATT-3133](https://atrainingtracker.atlassian.net/browse/ATT-3133) (`[Analysis]`)  
**Parent Epic**: [ATT-2564](https://atrainingtracker.atlassian.net/browse/ATT-2564) (*Navigation: Turn-by-Turn Guidance & Cockpit Prompts*)  
**Active Sprint**: `Sprint 2026-41.8`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-10  

---

## 1. Problem Statement & User Observation

During active workout tracking on the Map tab (`Karte`, observed on Google Pixel 10 during the Sprint 2026-41.7 Joint Review), the map camera continuously jitters and alternates every second between two distinct centerpoints:
1. Centering the athlete marker in the exact physical middle of the map viewport.
2. Positioning the athlete marker at the lower third (~35% from bottom / 65% from top via forward lookahead padding).

This produces a disorienting, continuous 1 Hz vertical oscillation loop that compromises cockpit readability during navigation.

### Evidence & Symptoms
* Video evidence captured directly on Google Pixel 10: `map_jitter_video.mp4` attached to [ATT-3102](https://atrainingtracker.atlassian.net/browse/ATT-3102).
* Athlete location icon visibly snaps between the middle of the screen (50% Y) and the lower third (65% Y) at every incoming GPS telemetry tick.

---

## 2. Root Cause Analysis & Forensic Mechanics

### 2.1 The Competing Authorities Failure Mechanism
The failure is caused by an architectural violation of **Rule 33 (Unified Viewport Anchor Authority for Map Tracking)**:
1. **Authority 1 (GoogleMap Content Padding)**:
   In `ATrainingTrackerMap.kt`, lookahead was implemented via:
   ```kotlin
   val topPadding = if (zoomFocus == MapZoomFocus.FOLLOW_ME) {
       maxHeight * (tuningConfig.mapFollowMeLookaheadPaddingPercent / 100f)
   } else {
       0.dp
   }
   GoogleMap(
       ...
       contentPadding = PaddingValues(top = topPadding)
   )
   ```
   This passes `topPadding` to `googleMap.setPadding(0, topPaddingPx, 0, 0)`, informing the Google Maps native SDK that the top of the map is obscured. Google Maps shifts its visible center downward (to ~65% from top).

2. **Authority 2 (Follow-Me Camera Controller)**:
   Concurrently, in `MapBehaviors.kt`, `followMeController` executes every time a new GPS location arrives (~1 Hz):
   ```kotlin
   cameraPositionState.animate(
       CameraUpdateFactory.newCameraPosition(
           CameraPosition.builder()
               .target(currentLocation)
               .bearing(filteredBearing)
               .zoom(targetZoom)
               .tilt(effectiveTilt)
               .build()
       ),
       400
   )
   ```

3. **The SDK Conflict**:
   In the Google Maps Android SDK, `CameraPosition.target` defines the exact 3D camera focal target. Crucially, `CameraUpdateFactory.newCameraPosition(CameraPosition)` animates the camera such that `CameraPosition.target` is positioned at the **physical center of the MapView (unpadded center, 50% Y)**. Unlike `CameraUpdateFactory.newLatLng(LatLng)`, `newCameraPosition` does not apply asymmetric viewport padding translation because `CameraPosition` represents an absolute 3D camera orientation.

4. **The 1 Hz Oscillation Cycle**:
   - **t = 0 ms**: GPS update arrives. `followMeController` fires `cameraPositionState.animate(newCameraPosition(target = currentLocation), 400)`. Over the 400 ms animation, the camera pulls `currentLocation` toward the unpadded physical center (50% Y).
   - **t = 400 ms**: Animation finishes or Compose recomposition re-applies `contentPadding` via `MapUpdater`, shifting the map canvas downward to the lower third (65% Y).
   - **t = 1000 ms**: Next GPS tick arrives. `followMeController` restarts the 400 ms animation toward the physical center.
   - Result: Continuous 1 Hz oscillation between center and lower third.

---

## 3. Chesterton's Fence Archaeology (`REQ-PRO-022`)

### 3.1 Historical Origin & Commit Trace
- **ATT-2946 (commit `d8177e06`, Sprint 2026-41.6)**: Introduced `mapFollowMeLookaheadPaddingPercent` (default 30%) and initially applied bottom padding (`contentPadding = PaddingValues(bottom = bottomPadding)`).
- **ATT-3054 (commit `8cdc39f0`, Sprint 2026-41.7)**: Discovered that bottom padding inverted the camera, pushing the athlete under the top telemetry tiles. Inverted bottom padding to `topPadding` to push the athlete downward, but retained `GoogleMap(contentPadding = ...)` without reconciling `CameraPosition.builder().target(currentLocation)`.
- **Sprint Review & Retrospective (ATT-3106)**: Pixel 10 testing revealed the oscillation bug, leading directly to the codification of **Rule 33**.

### 3.2 Preservation of Core Invariants
1. **Lookahead Viewport Ratio**: With default $30\%$ padding, the athlete must remain stably anchored at $\approx 65\%$ from top ($\approx 35\%$ from bottom), dedicating $\approx 65\%-70\%$ of visible viewport to the forward road ahead.
2. **Speed-Dependent Zoom**: Linear zoom scaling ($20.0$ stationary to $18.0$ cruising at $20\text{ km/h}$) must remain fully functional.
3. **Perspective Tilt**: Pitch angles from $0^\circ$ to $70^\circ$ must remain supported.
4. **DataStore Tuning Settings**: `mapFollowMeLookaheadPaddingPercent` ($0\% - 50\%$) slider and default value ($30\%$) in Advanced Tuning must continue to control lookahead magnitude.
5. **Zero Padding in Non-Follow-Me Modes**: In `EXPLICIT_BOUNDS`, `TRACK_AND_MARKERS`, `LOCAL_SEGMENTS`, etc., camera centering must not be offset.

---

## 4. Scope Bounding & Proposed Architecture (Rule 33)

### 4.1 In-Scope: Single Spatial Authority
Under Rule 33 (*Unified Viewport Anchor Authority for Map Tracking*), camera positioning and forward lookahead offset are unified under a **single spatial authority** inside `followMeController`:
1. **Decouple GoogleMap Content Padding**: Set `contentPadding = PaddingValues(0.dp)` during `FOLLOW_ME` mode in `ATrainingTrackerMap.kt`. This completely eliminates Google Maps native padding recalculation loops and layout fighting.
2. **Analytical Forward Lookahead Calculation in `followMeController`**:
   Compute the camera focal target ahead of the athlete along `filteredBearing`:
   $$\text{offsetFraction} = \left(\frac{\text{lookaheadPaddingPercent}}{100\text{f}}\right) \times 0.5\text{f}$$
   $$\text{metersPerDp} = \frac{156543.03392 \times \cos(\text{latRad})}{2^{\text{targetZoom}}}$$
   $$\text{offsetMeters} = \text{viewportHeightDp} \times \text{offsetFraction} \times \text{metersPerDp}$$
   $$\mathbf{x}_{\text{target}} = \text{SphericalUtil.computeOffset}(\text{currentLocation}, \text{offsetMeters}, \text{filteredBearing})$$
3. **Smooth Camera Animation**: Pass $\mathbf{x}_{\text{target}}$ to `CameraPosition.builder().target(target)`. Since the camera centers at $\mathbf{x}_{\text{target}}$, the athlete at $\text{currentLocation}$ is placed exactly at the lower lookahead position on screen. As the athlete turns, the offset rotates smoothly with `filteredBearing` with zero jitter.

### 4.2 Out-of-Scope
- Altering the DataStore preference key or defaults.
- Altering speed zoom slope or pitch angle limits.
- Modifying non-map tracking tab components (sensor tiles, elevation profile).

---

## 5. Verification Strategy
1. **Mathematical Unit Tests (`FollowMeUnifiedAnchorTest.kt`)**: Verify offset calculation across various latitudes, zoom levels, and padding percentages.
2. **Contract Tests (`FollowMeUnifiedAnchorContractTest.kt`)**: Verify `ATrainingTrackerMap.kt` passes `0.dp` content padding to GoogleMap in `FOLLOW_ME` mode and delegates lookahead parameters to `followMeController`.
3. **Full Clean-Room Regression**: `./gradlew testDebugUnitTest` ensuring zero regressions across all test suites.
