# Stage 3 Implementation Plan - ATT-3581: Calibrate Follow-Me High-Speed Zoom with Logarithmic Scaling and Remove Camera Sliders from Advanced Settings

**Ticket**: [ATT-3581](https://rainerblind.atlassian.net/browse/ATT-3581)  
**Sub-task**: [ATT-3717](https://rainerblind.atlassian.net/browse/ATT-3717) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-2564](https://rainerblind.atlassian.net/browse/ATT-2564) (*Navigation: Turn-by-Turn Guidance & Cockpit Prompts*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-42.5` (Sprint ID `372`)  
**Requirement Mapping**: `REQ-MAP-050` (*Calibrated Piecewise Logarithmic Follow-Me High-Speed Zoom and Autonomous Camera Control*)  
**Test Spec ID**: `TST-MAP-052` (*Follow-Me Piecewise Logarithmic Zoom Calibration & UI Simplification Verification Suite*)  
**Branch**: `feature/ATT-3581`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-15  

---

## 1. Executive Summary & Goals

Ticket ATT-3581 addresses two interrelated usability and navigational camera problems in Cockpit:
1. **Calibrate High-Speed Descent Zoom via Logarithmic Scaling**:
   - Replace the unbounded linear zoom slope above $20\text{ km/h}$ with a base-2 logarithmic scaling model ($\Delta \text{zoom} \propto \log_2(\text{speed} / 20)$) so that visible ground distance ahead grows linearly with velocity ($D \propto v$), preserving a constant lookahead time horizon ($T_{\text{lookahead}} = \text{const}$).
   - Set the minimum camera zoom level default to `minZoom = 16.0f` (`DEFAULT_MAP_FOLLOW_ME_MIN_ZOOM = 16.0f`), preventing excessive zoom-out and preserving turn-by-turn situational awareness even on rapid $80\text{+ km/h}$ alpine descents.
   - Preserve 100% of the proven linear progression between $0\text{ km/h}$ ($20.0f$) and $20\text{ km/h}$ ($18.0f$) for uphill climbing and relaxed cruising.
2. **Autonomous Camera Wiring, DataStore Migration & Advanced Settings Simplification**:
   - Wire `followMeController(...)` in `ATrainingTrackerMap.kt` to use the canonical constants in `TuningPreferencesDefaults` directly, preventing legacy persisted DataStore tweaks from corrupting calibrated physics.
   - Strip the 7 manual Follow-Me camera sliders/toggles from `NavigationSection.kt`, `AdvancedTuningScreen.kt`, and `AdvancedTuningDialog.kt` to eliminate UI clutter while keeping remaining navigation items intact.
   - **DataStore Migration**: Provide `purgeLegacyMapCameraPreferences()` and map `tuningConfigFlow` to strictly return canonical defaults for camera parameters, purging legacy orphaned keys from persistent storage so no "ghost settings" can linger.

---

## 2. Architecture & Design (SWE.2)

### 2.1 Component Boundaries
```
+--------------------------------------------------------------------------------+
|                        Presentation / UI Layer                                 |
|                                                                                |
|  +---------------------------+         +------------------------------------+  |
|  | ATrainingTrackerMap       | ------> | followMeController                 |  |
|  |  - SensorGridScreen       |         |  - Invoked with canonical defaults |  |
|  |  - Replay / Exploration   |         |  - Speed/Bearing exp smoothing     |  |
|  +---------------------------+         |  - 0.10f zoom hysteresis deadband  |  |
|                                        +------------------------------------+  |
|                                                           |                    |
|  +---------------------------+                            v                    |
|  | NavigationSection         |         +------------------------------------+  |
|  |  - Route Selection Rad.   |         | calculateFollowMeTargetZoom        |  |
|  |  - Cue Dismiss Duration   |         |  - Piecewise Linear / Logarithmic  |  |
|  |  - Turn Cue Distance      |         |  - minZoom clamped at 16.0f        |  |
|  |  - Auto-Follow Snapping   |         +------------------------------------+  |
|  |  (Camera sliders REMOVED) |                                                 |
|  +---------------------------+                                                 |
+--------------------------------------------------------------------------------+
                                       ^
                                       |
+--------------------------------------------------------------------------------+
|                          Data / Persistence Layer                              |
|                                                                                |
|  +--------------------------------------------------------------------------+  |
|  | TuningPreferencesDataStore                                               |  |
|  |  - purgeLegacyMapCameraPreferences(): purges orphaned camera keys        |  |
|  |  - tuningConfigFlow: returns canonical defaults directly for camera      |  |
|  |  - resetToDefaults(): clears legacy camera keys                          |  |
|  +--------------------------------------------------------------------------+  |
+--------------------------------------------------------------------------------+
```

### 2.2 Mathematical Model & Continuity ($C^0$)
Google Maps ground resolution scales inversely with power of two: $R(z) \propto 2^{-z}$. Visible distance on screen is $D(z) \propto 2^{-z}$.

1. **Low-Speed Region ($0.0 \le \text{speedKmh} \le 20.0\text{ km/h}$)**:
   $$\text{targetZoom} = \text{baseZoom} - \left(\frac{\text{baseZoom} - \text{cruisingZoom}}{20.0}\right) \times \text{speedKmh}$$
   - At $0\text{ km/h}$: $20.0 - 0 = 20.0f$
   - At $10\text{ km/h}$: $20.0 - 0.1 \times 10 = 19.0f$
   - At $20\text{ km/h}$: $20.0 - 0.1 \times 20 = 18.0f$

2. **High-Speed Region ($\text{speedKmh} > 20.0\text{ km/h}$)**:
   $$\text{logScale} = \frac{\text{baseZoom} - \text{cruisingZoom}}{2.0} = 1.0f$$
   $$\text{logDelta} = \text{logScale} \times \frac{\ln(\text{speedKmh} / 20.0)}{\ln(2.0)}$$
   $$\text{targetZoom} = (\text{cruisingZoom} - \text{logDelta}).\text{coerceIn}(\text{minZoom}, \text{maxZoom})$$
   - At $20\text{ km/h}$: $\ln(1.0) = 0 \implies 18.0 - 0 = 18.0f$ (perfect $C^0$ continuity with linear region).
   - At $30\text{ km/h}$: $18.0 - \log_2(1.5) \approx 18.0 - 0.585 = 17.42f$ ($1.5\times$ cruising distance).
   - At $40\text{ km/h}$: $18.0 - \log_2(2.0) = 18.0 - 1.0 = 17.00f$ ($2.0\times$ cruising distance).
   - At $50\text{ km/h}$: $18.0 - \log_2(2.5) \approx 18.0 - 1.322 = 16.68f$ ($2.5\times$ cruising distance).
   - At $60\text{ km/h}$: $18.0 - \log_2(3.0) \approx 18.0 - 1.585 = 16.42f$ ($3.0\times$ cruising distance).
   - At $80\text{ km/h}$: $18.0 - \log_2(4.0) = 18.0 - 2.0 = 16.00f$ ($4.0\times$ cruising distance).
   - At $\ge 80\text{ km/h}$: Clamped firmly to `minZoom = 16.0f`.

3. **Boundary Condition & Floating-Point Precision ($20.0\text{ km/h}$)**:
   - Branch condition uses `if (speedKmh <= 20.0f)`.
   - At exact boundary $20.0f$, linear formula evaluates: $20.0 - 0.1 \times 20.0 = 18.0f$.
   - For an epsilon deviation above boundary ($20.0001f$), $\ln(1.000005) \approx 0.000005 \implies 18.0 - 0.000005 = 18.0f$.
   - Because $\lim_{v \to 20.0^-} z(v) = \lim_{v \to 20.0^+} z(v) = 18.0f$, floating-point evaluation is guaranteed stable, monotonic, and devoid of jitter or singularities.

4. **Speed Zoom Disabled**:
   $$\text{targetZoom} = \text{baseZoom}.\text{coerceIn}(\text{minZoom}, \text{maxZoom}) = 20.0f$$

5. **Canonical `baseZoom` Grounding**:
   - `baseZoom` in `calculateFollowMeTargetZoom` and `followMeController` defaults to `TuningPreferencesDefaults.DEFAULT_MAP_FOLLOW_ME_INITIAL_ZOOM` ($20.0f$).
   - `ATrainingTrackerMap.kt` invokes `followMeController` with default parameters, ensuring `baseZoom` is strictly grounded in canonical constants and never dependent on dynamic or legacy UI state.

---

## 3. DataStore Migration & Legacy Purge Strategy

To prevent orphaned or legacy user-customized camera preferences from persisting in `DataStore` or creating "ghost settings":
1. **DataStore Read Decoupling**:
   - In `TuningPreferencesDataStore.kt:679–703`, `tuningConfigFlow` maps the camera fields (`mapFollowMeInitialZoom`, `mapFollowMeSpeedZoomEnabled`, `mapFollowMeCruisingZoom`, `mapFollowMeTiltAngle`, `mapFollowMeLookaheadPaddingPercent`, `mapFollowMeSpeedSmoothingAlpha`, `mapFollowMeBearingSmoothingAlpha`) directly to `TuningPreferencesDefaults` constants, ignoring any persisted legacy values.
2. **Explicit Legacy Key Purge**:
   - Provide `suspend fun purgeLegacyMapCameraPreferences()` in `TuningPreferencesDataStore` that executes:
     ```kotlin
     dataStore.edit { prefs ->
         prefs.remove(KEY_MAP_FOLLOW_ME_INITIAL_ZOOM)
         prefs.remove(KEY_MAP_FOLLOW_ME_SPEED_ZOOM_ENABLED)
         prefs.remove(KEY_MAP_FOLLOW_ME_CRUISING_ZOOM)
         prefs.remove(KEY_MAP_FOLLOW_ME_TILT_ANGLE)
         prefs.remove(KEY_MAP_FOLLOW_ME_LOOKAHEAD_PADDING_PERCENT)
         prefs.remove(KEY_MAP_FOLLOW_ME_SPEED_SMOOTHING_ALPHA)
         prefs.remove(KEY_MAP_FOLLOW_ME_BEARING_SMOOTHING_ALPHA)
     }
     ```
   - In `TuningPreferencesDataStore.resetToDefaults()`, ensure the legacy keys are removed.
   - Trigger `purgeLegacyMapCameraPreferences()` during application startup / DataStore initialization.

---

## 4. UI Consistency (Rule 23 & Design Guidelines §5)

* **Reference Screen**: `AdvancedTuningScreen.kt` and `AdvancedTuningDialog.kt` mounted from Settings / Navigation.
* **Component Pruning**:
  - The sub-section divider and header `Text(text = stringResource(R.string.tuning_map_camera_section_title))` and the 7 tuning items are removed from `NavigationSection.kt`.
  - The remaining 4 items (`routeSelectionRadiusKm`, `navigationCueDismissDurationSec`, `turnCueCountdownDistanceMeters`, `autoFollowUniqueRouteEnabled`) retain standard 14dp spacing and `TuningSliderItem` / `TuningToggleItem` styling.
* **Zero UI Regression in Cockpit**:
  - The camera controller continues to animate smoothly via `followMeController`, with bearing/speed smoothing and deadband hysteresis intact.

---

## 5. Invariants & Governance Protection

1. **Gate 3 Prerequisites Guard**: Gate 3 approval required before production file edits.
2. **Zero Regressions to Non-Follow-Me Map Modes**: `FIT_ALL`, `EXPLICIT_BOUNDS`, and `FREE_PAN` camera handling in `ATrainingTrackerMap.kt` remain 100% untouched.
3. **Continuity Invariant ($C^0$)**: Zoom function is continuous at the piecewise boundary $v = 20\text{ km/h}$ ($\lim_{v \to 20^-} z(v) = \lim_{v \to 20^+} z(v) = 18.0f$).
4. **Street-Level Visibility Guard**: Minimum zoom clamped at $16.0f$, guaranteeing roads and upcoming junctions remain clearly visible on steep descents.
5. **No Ghost Settings Invariant**: Legacy persisted DataStore keys are purged and completely bypassed by `tuningConfigFlow`.
6. **Living Documentation Synchronization**: `docs/requirements.md` (`REQ-MAP-050`) and `docs/tests.md` (`TST-MAP-052`) synchronized.
7. **Clean-Room Regression Suite**: 100% pass rate across the full 2,964+ unit test suite.
8. **Strategy A Integration**: Feature branch merges cleanly into `sprint/2026-42.5` (`--no-ff`).
9. **Human Final Review Authority**: Parent ticket strictly transitioned to `Final Review (Human)`.

---

## 6. Step-by-Step Construction Plan (Atomic Steps)

### Step 1: Default Constant & Migration in `TuningPreferencesDataStore.kt`
* In `app/src/main/java/com/atrainingtracker/trainingtracker/settings/TuningPreferencesDataStore.kt`:
  - Add `const val DEFAULT_MAP_FOLLOW_ME_MIN_ZOOM = 16.0f` to `TuningPreferencesDefaults`.
  - Add `purgeLegacyMapCameraPreferences()` method to purge legacy keys.
  - In `tuningConfigFlow`, map camera parameters directly from `TuningPreferencesDefaults`.
  - In `resetToDefaults()`, remove legacy camera keys.

### Step 2: Piecewise Linear-to-Logarithmic Calculation in `MapBehaviors.kt`
* In `app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapBehaviors.kt`:
  - Update `calculateFollowMeTargetZoom` signature default `minZoom = TuningPreferencesDefaults.DEFAULT_MAP_FOLLOW_ME_MIN_ZOOM`.
  - Implement piecewise branch with $C^0$ continuity:
    ```kotlin
    if (!speedZoomEnabled) {
        return baseZoom.coerceIn(minZoom, maxZoom)
    }
    val speedKmh = (speedMps * 3.6f).coerceAtLeast(0.0f)
    if (speedKmh <= 20.0f) {
        val zoomSlope = (baseZoom - cruisingZoom) / 20.0f
        return (baseZoom - zoomSlope * speedKmh).coerceIn(minZoom, maxZoom)
    } else {
        val logScale = (baseZoom - cruisingZoom) / 2.0f
        val logDelta = logScale * (kotlin.math.ln(speedKmh / 20.0f) / kotlin.math.ln(2.0f))
        val rawZoom = cruisingZoom - logDelta
        return rawZoom.coerceIn(minZoom, maxZoom)
    }
    ```

### Step 3: Remove Camera Sliders from NavigationSection and Tuning Screens
* In `NavigationSection.kt`:
  - Remove Follow-Me camera parameters from function signature.
  - Remove Follow-Me camera sub-section header and 7 slider/toggle items.
* In `AdvancedTuningScreen.kt` & `AdvancedTuningDialog.kt`:
  - Remove local Follow-Me state variables and arguments passed to `NavigationSection`.

### Step 4: Autonomous Controller Wiring in `ATrainingTrackerMap.kt`
* In `ATrainingTrackerMap.kt:247–261`:
  - Call `followMeController(...)` without passing manual `tuningConfig.mapFollowMe*` parameters, using canonical defaults from `TuningPreferencesDefaults`.

### Step 5: Unit & Contract Tests
* In `FollowMeCameraZoomTest.kt`:
  - Update tests to assert new piecewise linear ($\le 20\text{ km/h}$) and logarithmic ($> 20\text{ km/h}$) values, boundary at $20.0\text{ km/h}$, $16.0f$ minimum clamp, and disabled speed zoom.
* In `AdvancedTuningVisualContractTest.kt`:
  - Assert `NavigationSection.kt` no longer references camera slider title strings.
* In `FollowMePaddingContractTest.kt`:
  - Update `ATrainingTrackerMap.kt` invocation assertions.
* In `MapCameraTuningPreferencesTest.kt`:
  - Add migration test `testLegacyCameraPreferences_purgedAndIgnoredByCalibratedEngine` verifying that legacy keys are purged and ignored.

### Step 6: Full Clean-Room Regression Suite Execution
* Run `./gradlew testDebugUnitTest` asserting 100% pass rate.

---

## 7. Verification & Rollback Strategy

* **Targeted Verification Commands**:
  ```bash
  ./gradlew testDebugUnitTest --tests com.atrainingtracker.trainingtracker.ui.map.FollowMeCameraZoomTest
  ./gradlew testDebugUnitTest --tests com.atrainingtracker.trainingtracker.ui.settings.tuning.AdvancedTuningVisualContractTest
  ./gradlew testDebugUnitTest --tests com.atrainingtracker.trainingtracker.ui.map.FollowMePaddingContractTest
  ./gradlew testDebugUnitTest --tests com.atrainingtracker.trainingtracker.settings.MapCameraTuningPreferencesTest
  ```
* **Full Clean-Room Suite Command**:
  ```bash
  ./gradlew testDebugUnitTest
  ```
* **Rollback Strategy**:
  Changes are purely in map camera mathematics, DataStore configuration mapping, and Advanced Settings composables. Rollback can be performed cleanly via `git checkout origin/sprint/2026-42.5 -- <target_files>` with zero impact on database schemas or persistent user records.
