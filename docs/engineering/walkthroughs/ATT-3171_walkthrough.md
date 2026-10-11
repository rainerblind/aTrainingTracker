# ATT-3171 Walkthrough: Scale Follow-Me Camera Target Forward Lookahead Projection & Compensate 3D Tilt Foreshortening

## 1. Overview
- **Issue**: [ATT-3171](https://atrainingtracker.atlassian.net/browse/ATT-3171): `[Bug] Modifying Vorausschau-Kameraabstand setting has barely noticeable effect on map follow-me camera offset`
- **Parent Epic**: [ATT-355](https://atrainingtracker.atlassian.net/browse/ATT-355): *Good and consistent UI*
- **Requirement**: `REQ-MAP-048` (amending `REQ-MAP-046`)
- **Test Specification**: `TST-MAP-050`
- **Sprint**: `2026-41.9`
- **Fix Version**: `V4.9.39`
- **Status**: Verified

---

## 2. Problem Addressed
During Sprint 2026-41.8 (`ATT-3102`), the unified viewport anchor authority was established to decouple follow-me camera positioning from Google Maps content padding, eliminating 1 Hz camera jitter.
However, in `calculateFollowMeCameraTarget` (`MapBehaviors.kt`), forward lookahead offset projection was calculated as:
```kotlin
val offsetFraction = (lookaheadPaddingPercent / 100f) * 0.5f
val offsetMeters = viewportHeightDp * offsetFraction * metersPerDp
```
This formula exhibited two critical defects:
1. **Severe 3D Tilt Foreshortening Squashing**: The calculation did not account for perspective pitch tilt (`DEFAULT_MAP_FOLLOW_ME_TILT_ANGLE = 70.0f`). Because Google Maps projects ground-plane coordinates into a 3D perspective viewport, ground distances along the camera heading are foreshortened by $\cos(70^\circ) \approx 0.342$. Consequently, the physical visual displacement on screen was squashed by $\approx 65.8\%$, rendering changes across the $10\% \dots 50\%$ slider imperceptible ($\approx 6.8\%$ total viewport shift).
2. **Conservative Forward Scaling Multiplier**: The `* 0.5f` factor dampened the slider range to $5\% \dots 25\%$, which after foreshortening produced negligible visual distinction between minimum and maximum lookahead settings.
3. **Ambiguous Scale Direction**: The settings description in `strings.xml` did not state whether higher percentages shifted the camera forward or backward, leaving users confused about which direction to slide.

---

## 3. Architectural Changes & Solution

### 1. Analytical Projection & Tilt Foreshortening Compensation (`MapBehaviors.kt`)
Extended `calculateFollowMeCameraTarget` to accept `tiltAngle: Float = 0f` and calibrated the analytical ground offset projection:
```kotlin
fun calculateFollowMeCameraTarget(
    currentLocation: LatLng,
    bearing: Float,
    viewportHeightDp: Float,
    zoomLevel: Float,
    lookaheadPaddingPercent: Float,
    tiltAngle: Float = 0f
): LatLng {
    if (lookaheadPaddingPercent <= 0f || viewportHeightDp <= 0f) {
        return currentLocation
    }

    // Calibrated projection: 10%..50% slider maps to 6%..30% of viewport height (REQ-MAP-048)
    val offsetFraction = (lookaheadPaddingPercent / 100f) * 0.6f
    val metersPerDp = 156543.03392f * Math.cos(Math.toRadians(currentLocation.latitude)).toFloat() / (1 shl zoomLevel.toInt()).toFloat()

    // Compensate for 3D perspective tilt foreshortening along the camera pitch axis
    val clampedTilt = tiltAngle.toDouble().coerceIn(0.0, 70.0)
    val tiltFactor = Math.cos(Math.toRadians(clampedTilt)).toFloat().coerceAtLeast(0.34f)
    val offsetMeters = (viewportHeightDp * offsetFraction * metersPerDp) / tiltFactor

    // Project camera target forward along camera bearing
    return SphericalUtil.computeOffset(currentLocation, offsetMeters.toDouble(), bearing.toDouble())
}
```
Key mathematical properties:
- At `tiltAngle = 0°`, `tiltFactor = 1.0`, yielding clean 2D projection.
- At `tiltAngle = 70°`, `tiltFactor \approx 0.342`, scaling the ground distance by $\approx 2.92\times$ so that after Google Maps perspective projection, the athlete marker is positioned exactly at the intended viewport height fraction ($56\% \dots 80\%$, with canonical $30\%$ default at $68\%$).
- Extreme tilts are safely clamped to $[0^\circ, 70^\circ]$ and `tiltFactor >= 0.34f`, eliminating any possibility of division by zero.

### 2. Follow-Me Controller Propagation (`MapBehaviors.kt`)
Updated `followMeController` to pass `tiltAngle = effectiveTilt` directly to `calculateFollowMeCameraTarget`:
```kotlin
val cameraTarget = calculateFollowMeCameraTarget(
    currentLocation = filteredLocation,
    bearing = smoothBearing,
    viewportHeightDp = viewportHeightDp,
    zoomLevel = targetZoom,
    lookaheadPaddingPercent = config.lookaheadPaddingPercent,
    tiltAngle = effectiveTilt
)
```

### 3. Transparent Settings Scale Direction Across 9 Locales (`strings.xml`)
Updated `tuning_map_lookahead_padding_desc` across all 9 supported locales (`values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`) to explicitly clarify scale direction:
- **EN**: "Adjusts forward road visibility while tracking (10% - 50%). Higher values position the rider further down, showing more road ahead."
- **DE**: "Passt die Vorausschau beim Tracking an (10% - 50%). Höhere Werte positionieren den Fahrer weiter unten und zeigen mehr Strecke voraus."

---

## 4. Verification & Clean-Room Regression Results

### 1. Targeted Unit & Contract Tests
- `FollowMeCameraTargetTest`:
  - `testLookaheadPaddingScaling_increasesProportionally`: Validates ground offset scales monotonically and proportionally across slider range (3x at 30%, 5x at 50% vs 10%).
  - `testTiltForeshorteningCompensation_scalesGroundOffsetAtSteepTilt`: Asserts ground offset distance scales by $\approx 2.92\times$ at 70° tilt relative to flat 0° tilt.
  - `testTiltForeshorteningCompensation_clampsExtremeTiltSafely`: Validates safe clamping at extreme tilt values (-30°, 90°) without NaN or division by zero.
- `FollowMeUnifiedAnchorContractTest`:
  - Validates `followMeController` passes `tiltAngle = effectiveTilt` to `calculateFollowMeCameraTarget`.
  - Asserts preservation of single spatial authority (Rule 33 / Design Guidelines §6.1).
- `TranslationParityTest`:
  - Confirmed 100% parity across all 9 supported locales for `tuning_map_lookahead_padding_desc`.

### 2. Clean-Room Full Suite Regression
- Command: `./gradlew testDebugUnitTest`
- Result: **BUILD SUCCESSFUL** (100% pass rate across entire unit test suite, zero regressions).

---

## 5. Living Documentation & Traceability
- `docs/requirements.md`: `REQ-MAP-048` transitioned from `In Progress` to `Verified`.
- `docs/tests.md`: `TST-MAP-050` transitioned from `In Progress` to `Verified`.
- Parent ticket [ATT-3171](https://atrainingtracker.atlassian.net/browse/ATT-3171) assigned Fix Version `V4.9.39` and advanced through workflow to `Test`.
