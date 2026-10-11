# Stage 3: Implementation Plan - ATT-3171

## 1. Executive Summary & Architectural Invariants (SWE.2)
* **Issue**: [ATT-3171](https://atrainingtracker.atlassian.net/browse/ATT-3171): `[Bug] Modifying Vorausschau-Kameraabstand setting has barely noticeable effect on map follow-me camera offset`
* **Parent Epic**: [ATT-355](https://atrainingtracker.atlassian.net/browse/ATT-355): *Good and consistent UI*
* **Requirement**: `REQ-MAP-048` (amending `REQ-MAP-046` and aligning with Design Guidelines §3.1 & §6.1)
* **Test Specification**: `TST-MAP-050`
* **Sprint**: `Sprint 2026-41.9`
* **Author**: AI Agent 1 (Implementer)
* **Reviewer**: AI Agent 2 (Auditor)

### Architectural Invariants
1. **Unified Viewport Anchor Authority (Rule 33 / Design Guidelines §6.1)**:
   - `followMeController` remains the single spatial authority governing camera target, bearing smoothing, speed-dependent zoom, and 3D pitch tilt.
   - GoogleMap native padding in `FOLLOW_ME` mode strictly remains `PaddingValues(0.dp)`, completely avoiding competing recentering layout loops.
2. **Analytical Projection Scaling**:
   - `calculateFollowMeCameraTarget` scales `offsetFraction = (lookaheadPaddingPercent / 100f) * 0.6f`, cleanly mapping slider values from 10% to 50% to 6% to 30% of viewport height ($56\%$ Y to $80\%$ Y, with the 30% default landing at $68\%$ Y in the lower third).
3. **3D Perspective Tilt Foreshortening Compensation**:
   - Ground offset projection compensates for perspective pitch foreshortening:
     $$\text{tiltFactor} = \cos(\text{Math.toRadians(tiltAngle.toDouble().coerceIn(0.0, 70.0))).toFloat().coerceAtLeast(0.34f)}$$
     $$\text{offsetMeters} = \frac{\text{viewportHeightDp} \times \text{offsetFraction} \times \text{metersPerDp}}{\text{tiltFactor}}$$
   - At 70° tilt, ground offset is scaled by $\approx 2.92\times$, neutralizing camera foreshortening.
4. **Follow-Me Controller Delegation & Backwards Compatibility**:
   - `calculateFollowMeCameraTarget` provides default parameter `tiltAngle: Float = 0f`.
   - `followMeController` passes `tiltAngle = effectiveTilt`.
5. **Settings Scale Direction Transparency (Design Guidelines §3.1)**:
   - String resource `tuning_map_lookahead_padding_desc` is updated across all 9 supported locales to explicitly state scale direction ("higher value = rider further down / more road ahead" / "höherer Wert = Fahrer weiter unten / mehr Vorausschau").
6. **UI Consistency (Rule 23)**:
   - Closest reference: `MapTuningScreen.kt` (`TuningSliderRow`).
   - No new UI components or custom theme tokens introduced; existing slider and theme hierarchy strictly respected.
7. **Scope Bounding (Rule 26 / ATT-1250)**:
   - Confined strictly to `MapBehaviors.kt`, `strings.xml` (all 9 locales), and corresponding test suites.

---

## 2. Component Architecture & Data Flow

```
User Adjusts "Vorausschau-Kameraabstand" Slider (10% - 50%)
       │
       ▼
DataStore (mapFollowMeLookaheadPaddingPercent)
       │
       ▼
ATrainingTrackerMap.kt
       │
       ▼ Passes viewportHeightDp & lookaheadPaddingPercent
followMeController (MapBehaviors.kt)
       │
       ├── Speed-dependent targetZoom calculation
       ├── Low-pass filtered bearing (alpha = 0.15)
       ├── Effective tilt angle evaluation (e.g. 70.0f)
       │
       ▼ Passes tiltAngle = effectiveTilt
calculateFollowMeCameraTarget()
       │
       ├── offsetFraction = (lookaheadPaddingPercent / 100f) * 0.6f
       ├── tiltFactor = cos(rad(tilt)).coerceAtLeast(0.34f)
       ├── offsetMeters = (viewportHeightDp * offsetFraction * metersPerDp) / tiltFactor
       └── SphericalUtil.computeOffset(currentLocation, offsetMeters, filteredBearing)
       │
       ▼
CameraPosition.builder().target(target).bearing(bearing).zoom(zoom).tilt(tilt)
       │
       ▼
cameraPositionState.animate(...) [contentPadding = 0.dp]
       │
       ▼
Rider marker stably positioned in lower viewport; slider adjustment produces clear 5x travel!
```

---

## 3. Step-by-Step Construction Plan (Atomic Steps)

### Step 1: Update Lookahead Projection & Tilt Compensation in `MapBehaviors.kt`
* **Target File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapBehaviors.kt`
* **Action**:
  1. Modify `calculateFollowMeCameraTarget` signature to accept `tiltAngle: Float = 0f`.
  2. Update mathematical formulation:
     ```kotlin
     fun calculateFollowMeCameraTarget(
         currentLocation: LatLng,
         filteredBearing: Float,
         targetZoom: Float,
         viewportHeightDp: Float,
         lookaheadPaddingPercent: Float,
         tiltAngle: Float = 0f
     ): LatLng {
         if (lookaheadPaddingPercent <= 0f || viewportHeightDp <= 0f) {
             return currentLocation
         }
         val offsetFraction = (lookaheadPaddingPercent / 100f) * 0.6f
         val latRad = Math.toRadians(currentLocation.latitude)
         val metersPerDp = (156543.03392 * cos(latRad)) / 2.0.pow(targetZoom.toDouble())
         val clampedTilt = tiltAngle.toDouble().coerceIn(0.0, 70.0)
         val tiltFactor = cos(Math.toRadians(clampedTilt)).toFloat().coerceAtLeast(0.34f)
         val offsetMeters = (viewportHeightDp * offsetFraction * metersPerDp) / tiltFactor
         return SphericalUtil.computeOffset(currentLocation, offsetMeters, filteredBearing.toDouble())
     }
     ```
  3. In `followMeController`, pass `tiltAngle = effectiveTilt` to `calculateFollowMeCameraTarget`.
* **Test Verification**:
  `./gradlew testDebugUnitTest --tests com.atrainingtracker.trainingtracker.ui.map.FollowMeUnifiedAnchorContractTest`

### Step 2: Implement Comprehensive Unit & Contract Tests
* **Target Files**:
  - `app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/FollowMeCameraTargetTest.kt` (new)
  - `app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/FollowMeUnifiedAnchorContractTest.kt`
* **Action**:
  1. Create `FollowMeCameraTargetTest.kt` with tests for:
     - `testLookaheadPaddingScaling_increasesProportionally`: Validates 5x ground offset increase from 10% to 50% padding, and 3x increase from 10% to 30%.
     - `testTiltForeshorteningCompensation_scalesGroundOffsetAtSteepTilt`: Validates ~2.92x scaling at 70° tilt vs 0° tilt.
     - `testTiltForeshorteningCompensation_clampsExtremeTiltSafely`: Validates safe execution for 90° and negative tilt angles.
     - `testZeroPaddingOrZeroHeight_returnsCurrentLocationUnchanged`: Edge case preservation.
  2. Update `FollowMeUnifiedAnchorContractTest.kt` to verify that `followMeController` passes `tiltAngle = effectiveTilt`.
* **Test Verification**:
  `./gradlew testDebugUnitTest --tests com.atrainingtracker.trainingtracker.ui.map.FollowMeCameraTargetTest`

### Step 3: Update Settings Descriptions in All 9 Locales & Parity Audit
* **Target Files**:
  - `app/src/main/res/values/strings.xml`
  - `app/src/main/res/values-de/strings.xml`
  - `app/src/main/res/values-es/strings.xml`
  - `app/src/main/res/values-fr/strings.xml`
  - `app/src/main/res/values-it/strings.xml`
  - `app/src/main/res/values-ja/strings.xml`
  - `app/src/main/res/values-nl/strings.xml`
  - `app/src/main/res/values-pl/strings.xml`
  - `app/src/main/res/values-pt/strings.xml`
* **Action**:
  Update `tuning_map_lookahead_padding_desc` across all 9 locales with explicit scale direction text.
* **Test Verification**:
  `./gradlew testDebugUnitTest --tests com.atrainingtracker.trainingtracker.TranslationParityTest`

---

## 4. Verification & Testing Strategy

| Step | Scope | Command | Expected Result |
| :--- | :--- | :--- | :--- |
| Step 1 & 2 | Lookahead math & tilt scaling | `./gradlew testDebugUnitTest --tests com.atrainingtracker.trainingtracker.ui.map.FollowMeCameraTargetTest` | 100% Pass |
| Step 1 & 2 | Unified anchor contract | `./gradlew testDebugUnitTest --tests com.atrainingtracker.trainingtracker.ui.map.FollowMeUnifiedAnchorContractTest` | 100% Pass |
| Step 3 | 9-locale parity | `./gradlew testDebugUnitTest --tests com.atrainingtracker.trainingtracker.TranslationParityTest` | 100% Pass |
| Clean-Room | Full regression suite | `./gradlew testDebugUnitTest` | 100% Pass |
