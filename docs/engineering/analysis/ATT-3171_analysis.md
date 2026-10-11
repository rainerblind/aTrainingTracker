# Stage 1: Problem Domain & Root Cause Analysis - ATT-3171

## 1. Problem Statement & User Observation
During Sprint 2026-41.8 Joint Review testing of [ATT-3102](https://atrainingtracker.atlassian.net/browse/ATT-3102) on Google Pixel 10, the user confirmed that the 1 Hz viewport oscillation/jitter bug was successfully eliminated ("The strange effect is gone").
However, the user observed that adjusting the "Vorausschau-Kameraabstand" (*Forward Lookahead Padding*) slider in Advanced Tuning (*Navigation*) has no discernible visual effect on the marker position / forward camera offset during active tracking. Regardless of whether the slider is set to 10% or 50%, the user location marker appears anchored in virtually the same location near the middle of the screen.

---

## 2. Forensic Investigation & Root Cause (RCA)

### 2.1 Code Traversal in `MapBehaviors.kt`
In `app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapBehaviors.kt`:
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

### 2.2 The Double Attenuation Mechanism
Forensic analysis identifies two compounding mathematical attenuations that squash the visible marker displacement:

1. **The Artificial Halving Factor (`* 0.5f`)**:
   - In ATT-3102, forward lookahead was decoupled from Google Maps `contentPadding` (Rule 33).
   - In the prior padding implementation, `topPadding = 30%` caused the visible map height to span $[30\% \dots 100\%]$ with midpoint at $65\%$ from top. The displacement from the unpadded center ($50\%$) was $65\% - 50\% = 15\%$, which is $30\% \times 0.5$.
   - To emulate this, the developer inserted `val offsetFraction = (lookaheadPaddingPercent / 100f) * 0.5f`.
   - Consequently, for a slider range of $10\% \dots 50\%$, `offsetFraction` only spans $0.05 \dots 0.25$. On a 400dp tall map canvas, this represents a ground distance corresponding to only 20dp to 100dp before tilt.

2. **Severe 3D Perspective Tilt Foreshortening (The Primary Culprit)**:
   - In `MapBehaviors.kt`:
     ```kotlin
     val effectiveTilt = tiltAngle.coerceIn(0f, 70f)
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
   - Crucially, `calculateFollowMeCameraTarget` **does not receive or factor in `tiltAngle`**!
   - `DEFAULT_MAP_FOLLOW_ME_TILT_ANGLE` is configured to **`70.0f`** (maximum 3D perspective pitch).
   - In 3D perspective projection, when the camera is tilted forward by angle $\theta = 70^\circ$, a ground displacement along the line of sight projects onto the 2D screen image plane foreshortened by $\cos(\theta)$:
     $$\cos(70^\circ) \approx 0.34202$$
   - Because `calculateFollowMeCameraTarget` assumed flat 2D projection ($\theta = 0^\circ$), the actual displacement of the marker on the screen was attenuated by an additional factor of ~0.342!

3. **Combined Attenuation Impact**:
   - The total effective displacement on screen was:
     $$\text{Screen Offset Fraction} = \left(\frac{\text{lookaheadPaddingPercent}}{100\text{f}}\right) \times 0.5\text{f} \times \cos(70^\circ) \approx \text{lookaheadPaddingPercent} \times 0.00171$$
   - At $10\%$ slider value: Screen offset from center was $10 \times 0.00171 \approx 1.7\%$! (Marker at $51.7\%$ from top).
   - At $30\%$ slider value (default): Screen offset from center was $30 \times 0.00171 \approx 5.1\%$! (Marker at $55.1\%$ from top).
   - At $50\%$ slider value: Screen offset from center was $50 \times 0.00171 \approx 8.5\%$! (Marker at $58.5\%$ from top).
   - **Net result**: Across the entire slider range from 10% to 50%, the user location marker on screen only moved between $51.7\%$ and $58.5\%$ Y — a total shift of just **6.8% of the viewport height** (~27 dp or ~4 mm on a phone display)! This made the slider adjustments virtually imperceptible to the athlete.

---

## 3. Mercator Ground Resolution & Unit Semantics
* In the Google Maps Android SDK, map tiles are scaled to **256 dp** (logical pixels).
* The ground resolution formula:
  $$\text{metersPerDp} = \frac{156543.03392 \times \cos(\text{latitude})}{2^{\text{zoom}}}$$
  correctly evaluates meters per **dp** at nadir ($\text{tilt} = 0^\circ$).
* When converting viewport height in dp (`viewportHeightDp`) to ground meters, the equation must incorporate tilt foreshortening compensation:
  $$\text{tiltFactor} = \cos(\text{Math.toRadians(tiltAngle.toDouble().coerceIn(0.0, 70.0))).toFloat().coerceAtLeast(0.34f)}$$
  $$\text{offsetMeters} = \frac{\text{viewportHeightDp} \times \text{offsetFraction} \times \text{metersPerDp}}{\text{tiltFactor}}$$
* Dividing by $\text{tiltFactor}$ expands the ground projection distance so that when Google Maps renders the 3D perspective camera at pitch $\theta$, the projected marker displacement on screen equals the intended fraction of viewport height.

---

## 4. Chesterton's Fence Archaeology (`REQ-PRO-022`)

1. **Original Requirement ID & Target**:
   - Amends `REQ-MAP-046` (*Unified Viewport Anchor Authority for Follow-Me Map Tracking*, Sprint 2026-41.8, [ATT-3102](https://atrainingtracker.atlassian.net/browse/ATT-3102)).
2. **Historical Origin & Commit Trace**:
   - Sprint 2026-41.8 (`ATT-3102`, commit `611d3781`).
3. **Root Reason for Existing Formulation**:
   - In ATT-3102, the primary objective was resolving the severe 1 Hz camera jitter caused by competing GoogleMap `contentPadding` and `cameraPositionState.animate(...)`.
   - The author unified spatial authority by analytically projecting camera target ahead of the athlete, using `* 0.5f` to mirror the previous 15% midpoint shift.
   - However, the author neglected the perspective foreshortening caused by `DEFAULT_MAP_FOLLOW_ME_TILT_ANGLE = 70.0f`, leaving `calculateFollowMeCameraTarget` decoupled from tilt angle and severely compressing visible offset.
4. **Preservation of Core Invariants**:
   - Single spatial authority (Rule 33 / Design Guidelines §6.1) without competing padding loops MUST be strictly preserved.
   - Smooth bearing filtering (`alpha = 0.15f`) and speed-dependent zoom MUST NOT be altered.
   - Clean-room unit test pass rate MUST remain 100%.

---

## 5. Scope Bounding (`ATT-1250`)

### In-Scope
1. **Camera Target Projection Calibration (`MapBehaviors.kt`)**:
   - Extend `calculateFollowMeCameraTarget` to accept `tiltAngle: Float = 0f`.
   - Implement tilt compensation factor:
     $$\text{tiltFactor} = \cos(\text{Math.toRadians(tiltAngle.toDouble().coerceIn(0.0, 70.0))).toFloat().coerceAtLeast(0.34f)}$$
   - Calibrate `offsetFraction`: Set $\text{offsetFraction} = (\text{lookaheadPaddingPercent} / 100\text{f}) \times 0.6\text{f}$ so that:
     - At 10%: offset is 6% of viewport height (marker at 56% Y, just below center).
     - At 30% (default): offset is 18% of viewport height (marker at 68% Y, lower third).
     - At 50%: offset is 30% of viewport height (marker at 80% Y, prominent forward cockpit lookahead).
   - In `followMeController`, pass `tiltAngle = effectiveTilt` to `calculateFollowMeCameraTarget`.
2. **Settings Scale Direction Transparency (Design Guidelines §3.1)**:
   - Update `tuning_map_lookahead_padding_desc` across all 9 supported locales to explicitly state scale direction:
     - EN: `"Viewport padding percent to position the rider in the lower portion and maximize visible road ahead (higher value = rider further down / more road ahead)"`
     - DE: `"Anzeigebereich-Puffer in Prozent, um die Fahrerposition nach unten zu versetzen und den Blick auf die Strecke nach vorn zu maximieren (höherer Wert = Fahrer weiter unten / mehr Vorausschau)"`
     - ES, FR, IT, JA, NL, PL, PT parity.
3. **Automated Verification**:
   - Extend `FollowMeCameraTargetTest` and contract tests to assert proportional offset scaling and tilt compensation across the entire slider range.

### Out-of-Scope
- Changing DataStore preference keys or default slider range ($10\% \dots 50\%$).
- Modifying speed-dependent zoom slope or cruising zoom bounds.
- Modifying other sections of `AdvancedTuningDialog` or `ATrainingTrackerMap`.

---

## 6. Verification Strategy & Acceptance Criteria
1. **Mathematical Verification**:
   - Unit tests assert that increasing `lookaheadPaddingPercent` from 10% to 50% produces a 5x increase in ground offset meters for any given tilt angle.
   - Unit tests assert that at 70° tilt, the computed ground offset is scaled up by $\approx 2.92\times$ relative to 0° tilt, maintaining constant screen displacement.
2. **Contract Verification**:
   - Verify `followMeController` passes `effectiveTilt` into `calculateFollowMeCameraTarget`.
3. **Localization Parity**:
   - Verify all 9 locales include explanatory scale direction.
4. **Clean-Room Regression**:
   - Full test suite `./gradlew testDebugUnitTest` passes 100%.
