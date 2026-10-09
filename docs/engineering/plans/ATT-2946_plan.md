# Stage 3 Implementation Plan: ATT-2946 - Calibrate speed-dependent map zoom curve and implement bottom camera padding for forward lookahead

**Ticket**: [ATT-2946](https://atrainingtracker.atlassian.net/browse/ATT-2946)  
**Sub-task**: [ATT-3011](https://atrainingtracker.atlassian.net/browse/ATT-3011) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-2564](https://atrainingtracker.atlassian.net/browse/ATT-2564) (*Navigation: Turn-by-Turn Guidance & Cockpit Prompts*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `Sprint 2026-41.6`  
**Branch**: `feature/ATT-2946`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-09  

---

## 1. Architectural Overview (SWE.2)

Ticket `ATT-2946` implements `REQ-MAP-042` and `TST-MAP-044` to calibrate the speed-dependent Follow-Me map zoom curve, anchor the rider in the lower third for forward road lookahead via bottom camera content padding, and expose comprehensive camera tuning in `AdvancedTuningDialog`:

```
┌─────────────────────────────────────────────────────────────┐
│                 TuningPreferencesDataStore                  │
│  - mapFollowMeInitialZoom: 20.0f (15.0 - 21.0)              │
│  - mapFollowMeSpeedZoomEnabled: true                        │
│  - mapFollowMeCruisingZoom: 18.0f (14.0 - 19.5 @ 20 km/h)   │
│  - mapFollowMeTiltAngle: 70.0f (0.0 - 70.0)                 │
│  - mapFollowMeLookaheadPaddingPercent: 30.0f (10.0 - 50.0)  │
└──────────────────────────────┬──────────────────────────────┘
                               │ tuningConfigFlow
                               ▼
┌─────────────────────────────────────────────────────────────┐
│                    ATrainingTrackerMap                      │
│  - BoxWithConstraints: measures maxHeight                   │
│  - bottomPadding = if (FOLLOW_ME) maxHeight * 0.30f else 0  │
│  - GoogleMap(contentPadding = PaddingValues(bottom = ...))  │
└──────────────────────────────┬──────────────────────────────┘
                               │ followMeController(..., tuning)
                               ▼
┌─────────────────────────────────────────────────────────────┐
│                      MapBehaviors.kt                        │
│  - speedKmh = speedMps * 3.6f                               │
│  - zoomSlope = (baseZoom - cruisingZoom) / 20.0f            │
│  - targetZoom = (baseZoom - zoomSlope * speedKmh)           │
│  - CameraPosition: target(loc), zoom(targetZoom), tilt(...) │
└─────────────────────────────────────────────────────────────┘
```

---

## 2. UI Consistency & Design Guidelines (§ 5)

* **Reference Screen**: `AdvancedTuningDialog.kt` and `NavigationSection.kt`.
* **Reused Components**:
  - `TuningSliderItem`: Used for continuous floating-point sliders (base zoom, cruising zoom, tilt angle, lookahead padding) with value formatting, helper descriptions, and default badges conforming to Design Guidelines § 5.2.
  - `TuningSwitchItem`: Used for the boolean toggle `mapFollowMeSpeedZoomEnabled`.
* **Theme Tokens**:
  - `MaterialTheme.typography.bodyMedium` for setting titles and subtitles.
  - `MaterialTheme.colorScheme.primary` for slider tracks and thumb accents.
  - `MaterialTheme.colorScheme.surfaceVariant` for disabled slider states.
* **Layout Geometry**:
  - Vertical spacing: `14.dp` matching `NavigationSection` items.

---

## 3. Atomic Implementation Steps

### Step 1: Preferences DataStore & Defaults
* **Target File**: `app/src/main/java/com/atrainingtracker/trainingtracker/settings/TuningPreferencesDataStore.kt`
* **Changes**:
  1. Add constants to `TuningPreferencesDefaults`:
     - `MAP_FOLLOW_ME_INITIAL_ZOOM = 20.0f` (min `15.0f`, max `21.0f`, step `0.5f`)
     - `MAP_FOLLOW_ME_SPEED_ZOOM_ENABLED = true`
     - `MAP_FOLLOW_ME_CRUISING_ZOOM = 18.0f` (min `14.0f`, max `19.5f`, step `0.5f`)
     - `MAP_FOLLOW_ME_TILT_ANGLE = 70.0f` (min `0.0f`, max `70.0f`, step `5.0f`)
     - `MAP_FOLLOW_ME_LOOKAHEAD_PADDING_PERCENT = 30.0f` (min `10.0f`, max `50.0f`, step `5.0f`)
  2. Extend `data class TuningConfig` with the 5 properties and default initializers.
  3. Add preference keys:
     - `KEY_MAP_FOLLOW_ME_INITIAL_ZOOM`
     - `KEY_MAP_FOLLOW_ME_SPEED_ZOOM_ENABLED`
     - `KEY_MAP_FOLLOW_ME_CRUISING_ZOOM`
     - `KEY_MAP_FOLLOW_ME_TILT_ANGLE`
     - `KEY_MAP_FOLLOW_ME_LOOKAHEAD_PADDING_PERCENT`
  4. Register all 5 keys in `ALL_KEYS`.
  5. Add preference read parsing with clamp boundaries in `tuningConfigFlow`.
  6. Add mutator helper functions: `setMapFollowMeInitialZoom`, `setMapFollowMeSpeedZoomEnabled`, etc.

### Step 2: Mathematical Follow-Me Camera Calibration
* **Target File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapBehaviors.kt`
* **Changes**:
  1. Extract pure math calculation function:
     ```kotlin
     fun calculateFollowMeTargetZoom(
         speedMps: Float,
         baseZoom: Float = TuningPreferencesDefaults.MAP_FOLLOW_ME_INITIAL_ZOOM,
         speedZoomEnabled: Boolean = TuningPreferencesDefaults.MAP_FOLLOW_ME_SPEED_ZOOM_ENABLED,
         cruisingZoom: Float = TuningPreferencesDefaults.MAP_FOLLOW_ME_CRUISING_ZOOM
     ): Float
     ```
  2. Normalize speed: `val speedKmh = speedMps * 3.6f`.
  3. When `speedZoomEnabled == true`:
     `val zoomSlope = (baseZoom - cruisingZoom) / 20.0f`
     `targetZoom = (baseZoom - zoomSlope * speedKmh).coerceIn(12f, 21f)`.
  4. When `speedZoomEnabled == false`:
     `targetZoom = baseZoom.coerceIn(12f, 21f)`.
  5. In `followMeController`, accept tuning parameters (with backward-compatible defaults) and apply `tilt(tiltAngle.coerceIn(0f, 70f))`.

### Step 3: Forward Lookahead Content Padding in Map Container
* **Target File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/ATrainingTrackerMap.kt`
* **Changes**:
  1. In `ATrainingTrackerMap`, instantiate and collect `tuningDataStore.tuningConfigFlow`.
  2. Wrap map rendering in `BoxWithConstraints` (or measure container height).
  3. Compute:
     ```kotlin
     val lookaheadPaddingBottom = if (zoomFocus == MapZoomFocus.FOLLOW_ME) {
         maxHeight * (tuningConfig.mapFollowMeLookaheadPaddingPercent / 100f)
     } else {
         0.dp
     }
     ```
  4. Pass `contentPadding = PaddingValues(bottom = lookaheadPaddingBottom)` to `GoogleMap`.
  5. Pass tuning configuration parameters to `followMeController`.

### Step 4: UI Integration in Advanced Settings
* **Target Files**:
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/categories/NavigationSection.kt`
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/AdvancedTuningDialog.kt`
* **Changes**:
  1. In `NavigationSection.kt`, add controls for:
     - Follow-Me Base Zoom slider (`15.0 - 21.0`)
     - Speed-Dependent Zoom switch
     - Cruising Speed Zoom slider (`14.0 - 19.5` at $20\text{ km/h}$, enabled if speed zoom is active)
     - Camera Tilt Angle slider (`0° - 70°`)
     - Forward Lookahead Padding slider (`10% - 50%`)
  2. In `AdvancedTuningDialog.kt`:
     - Bind local state for the 5 parameters.
     - Persist new values in `SaveCancel.onSave`.
     - Restore defaults in `TuningResetDefaultsButton`.

### Step 5: 9-Language Localization
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
* **Changes**: Add string resources for all 5 settings titles, descriptions, and value formatting.

### Step 6: Verification Tests
* **Target Files**:
  - `app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/FollowMeCameraZoomTest.kt`
  - `app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/FollowMePaddingContractTest.kt`
  - `app/src/test/java/com/atrainingtracker/trainingtracker/settings/MapCameraTuningPreferencesTest.kt`
* **Execution**:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.map.FollowMeCameraZoomTest" --tests "com.atrainingtracker.trainingtracker.ui.map.FollowMePaddingContractTest" --tests "com.atrainingtracker.trainingtracker.settings.MapCameraTuningPreferencesTest"
  ```

---

## 4. Invariant Protection & Verification Gates

1. **Bearing Stabilization Invariant**:
   - Low-pass filtered bearing calculation (`filteredBearing += alpha * diff`) MUST NOT be altered (`REQ-MAP-003`).
2. **Animation Duration**:
   - `cameraPositionState.animate(..., 400)` remains smooth and jitter-free.
3. **Non-Follow-Me Isolation**:
   - In `FIT_ALL`, `MANUAL`, or `EXPLICIT_BOUNDS` modes, `contentPadding` MUST be `0.dp`, preserving exact bounds fitting for route overviews (`REQ-MAP-022`).
4. **DataStore Resilience**:
   - Out-of-range preference values MUST be coerced safely within valid ranges during load.
   - `resetToDefaults()` MUST clear all camera keys.
5. **Clean-Room Regression**:
   - Full test suite `./gradlew testDebugUnitTest` must pass at 100%.
