# Stage 3 Implementation Plan: ATT-3054 - Invert map camera lookahead padding and clarify zoom level direction in settings

**Ticket**: [ATT-3054](https://atrainingtracker.atlassian.net/browse/ATT-3054)  
**Sub-task**: [ATT-3079](https://atrainingtracker.atlassian.net/browse/ATT-3079) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-2564](https://atrainingtracker.atlassian.net/browse/ATT-2564) (*Navigation: Turn-by-Turn Guidance & Cockpit Prompts*)  
**Active Sprint**: `Sprint 2026-41.7`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-10  

---

## 1. Architectural Decomposition (SWE.2)

### 1.1 Clean Architecture Boundaries & Responsibilities
- **Map Viewport & Camera Geometry Layer (`ui/map`)**:
  - `ATrainingTrackerMap.kt`: The Compose wrapper hosting the Google Maps native map surface. In `FOLLOW_ME` tracking mode, calculates `topPadding` as `maxHeight * (tuningConfig.mapFollowMeLookaheadPaddingPercent / 100f)` and supplies `contentPadding = PaddingValues(top = topPadding)` to `GoogleMap`. This informs Google Maps that the upper portion of the viewport is padded, shifting the camera focal center downwards and positioning the athlete icon in the lower third to maximize forward road visibility.
- **Settings & Advanced Tuning Presentation Layer (`ui/settings/tuning`)**:
  - `NavigationSection.kt`: Renders the Map Camera & Navigation configuration category inside `AdvancedTuningDialog`. Hosts sliders for base zoom, cruising zoom, tilt angle, and forward lookahead padding. Consumes localized string resources that explicitly clarify the zoom direction (higher numerical value = closer zoom) and forward lookahead padding semantics.
- **Resource & Localization Layer (`res/values*`)**:
  - Strings files across all 9 application locales (`values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`):
    - `tuning_map_base_zoom_desc`: Clarified with zoom direction guidance (higher value = closer zoom).
    - `tuning_map_cruising_zoom_desc`: Clarified with zoom direction guidance (higher value = closer zoom).
    - `tuning_map_lookahead_padding_title`: Updated to "Forward Lookahead Padding" without contradictory "bottom" references.
    - `tuning_map_lookahead_padding_desc`: Updated to explain viewport padding to anchor rider in lower portion and maximize road ahead.
- **Contract & Test Verification Layer (`app/src/test/java`)**:
  - `ui/map/FollowMePaddingContractTest.kt`: Structural contract test asserting `ATrainingTrackerMap.kt` calculates `topPadding` during `FOLLOW_ME` and applies `contentPadding = PaddingValues(top = topPadding)`.
  - `settings/MapCameraLocalizationTest.kt`: Parity test asserting all 11 map camera tuning string keys exist, are non-empty, and contain zoom direction guidance across all 9 locales.

---

## 2. UI Consistency Audit (Rule 23)

- **Closest Existing Reference Screen**:
  - `NavigationSection.kt` in `AdvancedTuningDialog.kt` (Sprint 2026-41.6, `ATT-2946`).
- **Reused Components (`ui/components/`)**:
  - `TuningSliderItem`: Standardized slider component in `TuningFormControls.kt` rendering `title`, `valueText`, `helperText`, `defaultText`, and `Slider`.
  - `TuningToggleItem`: Standardized switch component for `mapFollowMeSpeedZoomEnabled`.
  - `TuningPreferencesDefaults`: Constant definitions for camera bounds and defaults.
- **Design & Theme Tokens**:
  - `MaterialTheme.typography.bodySmall` for `helperText`.
  - `MaterialTheme.colorScheme.onSurfaceVariant` for helper copy color.
  - `MaterialTheme.colorScheme.primary` for section titles and active values.
- **Justification for New Styles**:
  - Zero new styles or custom form controls are introduced. All modifications are strictly confined to helper copy strings, titles, and camera padding parameters within existing M3 components.

---

## 3. Atomic Step Sequencing

### Step 1: Invert Lookahead Padding in `ATrainingTrackerMap.kt`
- File: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/ATrainingTrackerMap.kt`
- Replace `bottomPadding` calculation with `topPadding`:
  ```kotlin
  val topPadding = if (zoomFocus == MapZoomFocus.FOLLOW_ME) {
      maxHeight * (tuningConfig.mapFollowMeLookaheadPaddingPercent / 100f)
  } else {
      0.dp
  }

  GoogleMap(
      modifier = Modifier
          .fillMaxSize()
          .background(if (isDark) Color(0xFF121212) else Color.White),
      cameraPositionState = cameraPositionState,
      contentPadding = PaddingValues(top = topPadding),
      ...
  )
  ```
- Target Verification: Ensure GoogleMap compiles and receives `top = topPadding`.

### Step 2: Update String Resources Across All 9 Locales
- Files:
  - `app/src/main/res/values/strings.xml`
  - `app/src/main/res/values-de/strings.xml`
  - `app/src/main/res/values-es/strings.xml`
  - `app/src/main/res/values-fr/strings.xml`
  - `app/src/main/res/values-it/strings.xml`
  - `app/src/main/res/values-ja/strings.xml`
  - `app/src/main/res/values-nl/strings.xml`
  - `app/src/main/res/values-pl/strings.xml`
  - `app/src/main/res/values-pt/strings.xml`
- Update:
  - `tuning_map_base_zoom_desc`
  - `tuning_map_cruising_zoom_desc`
  - `tuning_map_lookahead_padding_title`
  - `tuning_map_lookahead_padding_desc`

### Step 3: Update Contract and Localization Unit Tests
- Files:
  - `app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/FollowMePaddingContractTest.kt`:
    - Assert `val topPadding = if (zoomFocus == MapZoomFocus.FOLLOW_ME)`
    - Assert `contentPadding = PaddingValues(top = topPadding)`
    - Assert `topPadding` percentage calculation ($30\%$ of $400\text{dp} = 120\text{dp}$).
  - `app/src/test/java/com/atrainingtracker/trainingtracker/settings/MapCameraLocalizationTest.kt`:
    - Assert parity across all 9 locales.
    - Assert non-empty and contains updated guidance.
- Target Verification:
  - Run `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.map.FollowMePaddingContractTest"`
  - Run `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.settings.MapCameraLocalizationTest"`

### Step 4: Full Clean-Room Regression Run
- Run `./gradlew testDebugUnitTest` verifying 100% pass rate across the full test suite.

---

## 4. Invariant Protection & Rollback Safety

- **Speed-Dependent Zoom Curve (`REQ-MAP-042`)**: Speed normalization ($\text{km/h}$) and linear scaling ($Z_{\text{base}}$ to $Z_{\text{cruise}}$) remain completely untouched.
- **Camera Pitch & Bearing (`REQ-MAP-003`)**: Tilt angle calculation and bearing filter smoothing remain 100% intact.
- **DataStore Storage Compatibility**: Preference keys (`map_follow_me_lookahead_padding_percent`, etc.) and default values remain unchanged, preventing data corruption or migration issues.
- **Non-Follow-Me Modes**: In `EXPLICIT_BOUNDS` and `FIT_ALL`, top padding resolves to `0.dp`.
