# Stage 1 Analysis: ATT-3227 - Smooth follow-me speed zoom and calibrate speed and bearing smoothing filters in advanced settings

**Ticket**: [ATT-3227](https://atrainingtracker.atlassian.net/browse/ATT-3227)  
**Sub-task**: [ATT-3295](https://atrainingtracker.atlassian.net/browse/ATT-3295) (`[Analysis]`)  
**Parent Epic**: [ATT-2564](https://atrainingtracker.atlassian.net/browse/ATT-2564) (*Navigation: Turn-by-Turn Guidance & Cockpit Prompts*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `2026-41.10`  
**Branch**: `feature/ATT-3227`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-11  

---

## 1. Problem Statement & Motivation

In Follow-Me tracking mode, the map camera continuously centers on the athlete's position, aligns with the athlete's direction of travel, and optionally scales zoom dynamically based on instantaneous speed (`calculateFollowMeTargetZoom()`, `REQ-MAP-042`).

However, two significant usability and visual stability issues exist in the current implementation:

1. **Jittery Speed Zoom Oscillations ("Camera Pumping")**:
   `followMeController` in `MapBehaviors.kt:274-279` passes raw instantaneous speed directly into `calculateFollowMeTargetZoom(speedMps = speed, ...)`. During real-world cycling or running (accelerating, sprinting, cadence bursts, cornering, coasting, or tree-cover GPS noise) and during simulated TCX replay, instantaneous GPS speed fluctuates continuously by $1 - 5\text{ km/h}$ each second. Because camera zoom updates with every location sample without damping or deadband, the map camera incessantly zooms in and out. This creates severe visual fatigue, disorientation, and jittery map framing.

2. **Hardcoded, Inflexible Bearing Smoothing Constant**:
   While bearing is smoothed using a low-pass exponential filter in `MapBehaviors.kt:266`:
   ```kotlin
   val alpha = 0.15f
   ```
   this smoothing factor is hardcoded and cannot be calibrated. Different sports and athlete preferences require different responsiveness:
   - High-speed road cyclists or motor-pacers may prefer faster, snappier map rotation ($\alpha \approx 0.25 - 0.35$).
   - Trail runners, hikers, or mountain bikers navigating winding switchbacks may prefer heavier damping ($\alpha \approx 0.05 - 0.10$) to eliminate erratic compass heading swings.

To provide a buttery-smooth, customizable camera experience, the system must:
1. Apply low-pass exponential smoothing to speed before calculating target zoom.
2. Introduce a hysteresis deadband ($\Delta \text{zoom} \ge 0.10f$) to prevent micro-animations when speed variations are negligible.
3. Expose configurable filter constants for both speed and bearing smoothing in Advanced Settings under Navigation.
4. Support 100% localization parity across all 9 supported application locales.

---

## 2. Forensic Investigation & Root Cause Analysis

### 2.1 Raw Speed Ingestion in `MapBehaviors.kt`
In `app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapBehaviors.kt:264-280`:
```kotlin
LaunchedEffect(currentLocation, bearing, speed, baseZoom, speedZoomEnabled, cruisingZoom, tiltAngle, lookaheadPaddingPercent, viewportHeightDp) {
    if (zoomFocus == MapZoomFocus.FOLLOW_ME && currentLocation != null) {
        val alpha = 0.15f
        var diff = bearing - filteredBearing
        while (diff < -180f) diff += 360f
        while (diff > 180f) diff -= 360f

        filteredBearing += alpha * diff
        filteredBearing = (filteredBearing + 360f) % 360f

        val targetZoom = calculateFollowMeTargetZoom(
            speedMps = speed, // <-- Raw instantaneous speed!
            baseZoom = baseZoom,
            speedZoomEnabled = speedZoomEnabled,
            cruisingZoom = cruisingZoom
        )
        ...
        cameraPositionState.animate(..., 400)
    }
}
```
`filteredBearing` is maintained via `remember { mutableFloatStateOf(bearing) }` and smoothed via exponential filter, but `speed` is ingested unbuffered and un-smoothed. Any 1-second noise or cadence pulsation directly alters `targetZoom` and triggers a 400ms camera zoom animation.

### 2.2 Lack of Hysteresis Deadband
Even with smoothing, continuous infinitesimal changes in floating-point speed yield $\Delta \text{zoom} \approx 0.005 - 0.02$, triggering perpetual camera animation calls every second. A hysteresis deadband (e.g. $|\Delta \text{zoom}| \ge 0.10f$) is necessary so camera zoom only adjusts when speed changes meaningfully (by $\ge 1.0\text{ km/h}$ under default zoom slope).

### 2.3 Hardcoded Filter Factor
The constant `alpha = 0.15f` for bearing smoothing is hardcoded inside `followMeController`. It is not bound to `TuningPreferencesDataStore` or `TuningConfig`, preventing athlete calibration.

---

## 3. Chesterton's Fence Archaeology

The hardcoded `val alpha = 0.15f` was introduced in commit `300470962` during the transition from `MapState` to Compose `MapBehaviors.kt`. It was intended as a moderate filter to stabilize compass bearing without causing excessive turning lag. The fence is respected by keeping `DEFAULT_MAP_FOLLOW_ME_BEARING_SMOOTHING_ALPHA = 0.15f` as the default value, while parameterizing it and extending the same exponential smoothing paradigm to speed.

---

## 4. Proposed Solution Architecture

### 4.1 DataStore & TuningConfig (`TuningPreferencesDataStore.kt`)
Add the following preference definitions:
- **Speed Smoothing Alpha**:
  - Key: `KEY_MAP_FOLLOW_ME_SPEED_SMOOTHING_ALPHA = floatPreferencesKey("tuning_map_follow_me_speed_smoothing_alpha")`
  - Default: `0.10f`
  - Range: `0.02f` to `0.50f` (step `0.02f`)
- **Bearing Smoothing Alpha**:
  - Key: `KEY_MAP_FOLLOW_ME_BEARING_SMOOTHING_ALPHA = floatPreferencesKey("tuning_map_follow_me_bearing_smoothing_alpha")`
  - Default: `0.15f`
  - Range: `0.05f` to `0.50f` (step `0.05f`)
- Include in `ALL_KEYS`, `TuningConfig`, and DataStore flows/mutators.
- Defaults reset safely on `resetToDefaults()`.

### 4.2 Speed Smoothing & Hysteresis in `followMeController` (`MapBehaviors.kt`)
- Maintain `var filteredSpeed by remember { mutableFloatStateOf(speed) }` and `var activeZoom by remember { mutableFloatStateOf(baseZoom) }`.
- When `zoomFocus == MapZoomFocus.FOLLOW_ME` is initialized, snap `filteredSpeed = speed` and compute initial `activeZoom`.
- In location LaunchedEffect:
  ```kotlin
  val effectiveSpeedAlpha = speedSmoothingAlpha.coerceIn(
      TuningPreferencesDefaults.MIN_MAP_FOLLOW_ME_SPEED_SMOOTHING_ALPHA,
      TuningPreferencesDefaults.MAX_MAP_FOLLOW_ME_SPEED_SMOOTHING_ALPHA
  )
  filteredSpeed += effectiveSpeedAlpha * (speed - filteredSpeed)

  val effectiveBearingAlpha = bearingSmoothingAlpha.coerceIn(
      TuningPreferencesDefaults.MIN_MAP_FOLLOW_ME_BEARING_SMOOTHING_ALPHA,
      TuningPreferencesDefaults.MAX_MAP_FOLLOW_ME_BEARING_SMOOTHING_ALPHA
  )
  var diff = bearing - filteredBearing
  while (diff < -180f) diff += 360f
  while (diff > 180f) diff -= 360f
  filteredBearing += effectiveBearingAlpha * diff
  filteredBearing = (filteredBearing + 360f) % 360f

  val targetZoom = calculateFollowMeTargetZoom(
      speedMps = filteredSpeed,
      baseZoom = baseZoom,
      speedZoomEnabled = speedZoomEnabled,
      cruisingZoom = cruisingZoom
  )

  if (!speedZoomEnabled) {
      activeZoom = targetZoom
  } else if (kotlin.math.abs(targetZoom - activeZoom) >= 0.10f) {
      activeZoom = targetZoom
  }
  ```
- Use `activeZoom` for both `calculateFollowMeCameraTarget` and `CameraPosition.builder().zoom(activeZoom)`.

### 4.3 Advanced Settings UI (`NavigationSection.kt` & `AdvancedTuningDialog.kt`)
- In `NavigationSection.kt`, render `TuningSliderItem` for:
  - **Speed Smoothing**: displayed when `mapFollowMeSpeedZoomEnabled` is `true`. Range `0.02f..0.50f`, step 0.02f, 24 steps, format `"%.2f"`.
  - **Bearing Smoothing**: displayed in Follow-Me camera settings. Range `0.05f..0.50f`, step 0.05f, 9 steps, format `"%.2f"`.
- Wire state and callbacks cleanly in `AdvancedTuningDialog.kt` and `ATrainingTrackerMap.kt`.

### 4.4 9-Language Localization Parity
Add titles and descriptions for:
- `tuning_map_speed_smoothing_title`
- `tuning_map_speed_smoothing_desc`
- `tuning_map_bearing_smoothing_title`
- `tuning_map_bearing_smoothing_desc`
across all 9 locales: EN, DE, ES, FR, IT, JA, NL, PL, PT.

---

## 5. Verification Plan

1. **Unit Tests (`FollowMeSmoothingTest.kt`)**:
   - Verify speed exponential smoothing convergence and dampening.
   - Verify zoom hysteresis deadband filters small speed fluctuations while honoring significant speed changes.
   - Verify bearing smoothing uses configured alpha.
2. **DataStore Tests (`TuningConfigTest.kt`)**:
   - Verify default values for `mapFollowMeSpeedSmoothingAlpha` (0.10) and `mapFollowMeBearingSmoothingAlpha` (0.15).
   - Verify serialization, deserialization, clamping, and `resetToDefaults()`.
3. **UI Contract Tests (`NavigationSectionTest.kt`)**:
   - Verify sliders render with correct ranges and steps.
   - Verify callbacks invoke appropriately on user interaction.
4. **9-Language Parity Audit**:
   - Verify `TranslationParityTest.kt` passes with 100% string presence across all locales.
5. **Clean-Room Full Suite Regression Execution**:
   - Execute `./gradlew testDebugUnitTest` ensuring 0 regressions across the codebase.
