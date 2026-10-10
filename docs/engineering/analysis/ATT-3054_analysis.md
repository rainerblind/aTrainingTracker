# Stage 1 Analysis: ATT-3054 - Invert map camera lookahead padding and clarify zoom level direction in settings

**Ticket**: [ATT-3054](https://atrainingtracker.atlassian.net/browse/ATT-3054)  
**Sub-task**: [ATT-3077](https://atrainingtracker.atlassian.net/browse/ATT-3077) (`[Analysis]`)  
**Parent Epic**: [ATT-2564](https://atrainingtracker.atlassian.net/browse/ATT-2564) (*Navigation: Turn-by-Turn Guidance & Cockpit Prompts*)  
**Active Sprint**: `Sprint 2026-41.7`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-10  

---

## 1. Problem Statement & Field Observations

During on-device testing and Sprint Review of ATT-2946 (*Speed-Dependent Map Zoom Calibration, Lookahead Padding & Tuning Preferences*), two critical UX defects were uncovered in follow-me navigation and camera settings:

### 1.1 Inverted Lookahead Camera Padding in `ATrainingTrackerMap.kt`
In `ATrainingTrackerMap.kt` (lines 202–213), forward lookahead padding was implemented as:
```kotlin
val bottomPadding = if (zoomFocus == MapZoomFocus.FOLLOW_ME) {
    maxHeight * (tuningConfig.mapFollowMeLookaheadPaddingPercent / 100f)
} else {
    0.dp
}

GoogleMap(
    ...
    contentPadding = PaddingValues(bottom = bottomPadding),
    ...
)
```
**The Mathematical / Geometric Defect**:
In Google Maps Android SDK (`GoogleMap.setPadding` / Compose `contentPadding`), padding indicates where the map surface is obstructed by floating system UI (e.g., bottom sheets or action bars). Consequently, Google Maps shifts the camera focus **away** from the padded edge:
- Defining **bottom padding** informs Google Maps that the lower viewport is obscured, forcing the camera focal point (the athlete's GPS location in `FOLLOW_ME` mode) to shift **upwards** into the upper half of the display.
- In heading-up navigation mode, this pushes the rider icon directly beneath the top telemetry HUD overlays (speed, power, cadence, heart rate), leaving $\approx 75\% - 80\%$ of the screen displaying traversed path *behind* the rider, and only $20\%$ showing the road ahead.
- To achieve the intended forward lookahead (anchoring the rider in the lower third, e.g. $\approx 30\%$ from the bottom, dedicating $\approx 70\%$ to the forward route corridor), Google Maps must be told that the **top** of the screen is padded. Applying `topPadding` shifts the camera center **downwards** towards the bottom.

### 1.2 Ambiguous Zoom Level Direction in Settings (`NavigationSection.kt`)
In Advanced Tuning Settings (`NavigationSection.kt`), sliders for *Follow-Me Base Zoom* (`tuning_map_base_zoom_title`) and *Cruising Speed Zoom* (`tuning_map_cruising_zoom_title`) display numerical zoom levels (e.g., 20.0, 18.0) and generic descriptions.
- Athletes configuring their camera cannot determine whether a higher numerical value zooms *closer* (more detail, tighter street view) or *further away* (broader map overview).
- In standard Mercator tile cartography, higher zoom numbers represent higher magnification (closer to the ground).
- Athletes require clear, explicit helper guidance across all 9 supported languages stating that higher numerical values correspond to closer zoom (e.g., German: *"Höherer Wert = Näher herangezoomt"*, English: *"Higher value = closer zoom"*).
- Similarly, the lookahead padding setting title and description currently refer to "Bottom Padding", which is confusing and contradictory given the camera inversion fix; it should be cleanly termed *Forward Lookahead Padding* with an explanation that it positions the rider in the lower portion of the screen.

---

## 2. Chesterton's Fence Archaeology & Requirement History (`REQ-PRO-022`)

### 2.1 Original Requirement ID & Target
- Amends `REQ-MAP-042` (*Calibratable Speed-Dependent Map Zoom Curve, Follow-Me Forward Lookahead Camera Padding & Pitch Tuning*, introduced in Sprint 2026-41.6 under ATT-2946).
- Formulates `REQ-MAP-044` (*Corrected Forward Lookahead Camera Top Padding & Zoom Level Directional Guidance*).

### 2.2 Historical Origin & Commit Trace
- Introduced in commit `d8177e06` (`feat(map): calibrate speed zoom curve, add forward lookahead padding, and wire camera tuning (ATT-2946)`).
- Commit `d8177e06` added `mapFollowMeLookaheadPaddingPercent` to `TuningConfig` and mapped it to `contentPadding = PaddingValues(bottom = bottomPadding)` in `ATrainingTrackerMap.kt`.

### 2.3 Root Reason for Existing Formulation
The implementer of ATT-2946 intuitively reasoned: *"The athlete icon belongs at the bottom, so we should apply bottom padding."* The developer did not realize that Google Maps camera projection inverts content padding relative to the focal point offset (padding bottom shifts focal center top; padding top shifts focal center bottom).

### 2.4 Preservation of Core Invariants
1. **Speed Zoom Linear Curve**: Velocity-dependent zoom scaling between stationary ($0\text{ km/h}$) and cruising speed ($20\text{ km/h}$) must remain strictly intact.
2. **Tilt Perspective**: Configurable $0^\circ - 70^\circ$ camera pitch angle must remain unchanged.
3. **DataStore Architecture**: DataStore keys (`KEY_MAP_FOLLOW_ME_LOOKAHEAD_PADDING_PERCENT`, etc.), default values ($30\%$), and `resetToDefaults()` must be preserved without breaking migrations.
4. **Non-Follow-Me Modes**: In `FIT_ALL`, `EXPLICIT_BOUNDS`, or manual pan/zoom modes, camera content padding must remain `0.dp`.
5. **Clean-Room Test Regression**: 100% unit test pass rate across all 2300+ tests must be preserved.

---

## 3. Scope Bounding & User Grounding (`ATT-1250`)

### In-Scope:
1. **Map Camera Padding Inversion (`ATrainingTrackerMap.kt`)**:
   - Change `bottomPadding` calculation to `topPadding`:
     $$\text{topPadding} = \text{maxHeight} \times \left(\frac{\text{tuningConfig.mapFollowMeLookaheadPaddingPercent}}{100\text{f}}\right)$$
   - Pass `PaddingValues(top = topPadding)` to `GoogleMap(contentPadding = ...)`.
2. **Settings Copy & Directional Clarity (`strings.xml` & `NavigationSection.kt`)**:
   - Update `tuning_map_base_zoom_desc` and `tuning_map_cruising_zoom_desc` to explicitly include zoom direction guidance across all 9 languages (EN, DE, ES, FR, IT, JA, NL, PL, PT).
   - Update `tuning_map_lookahead_padding_title` and `tuning_map_lookahead_padding_desc` to remove misleading "Bottom" references, standardizing on *Forward Lookahead Padding* across all 9 languages.
3. **Contract & Regression Unit Tests**:
   - Update `FollowMePaddingContractTest.kt` to enforce `topPadding` and `PaddingValues(top = topPadding)`.
   - Update `MapCameraLocalizationTest.kt` to verify that all 9 language resource files contain non-empty strings with the required direction guidance.

### Out-of-Scope:
- Modifying camera animation durations or animation curves in `followMeController`.
- Changing the min/max/step ranges of the tuning parameters.
- Modifying camera behaviors for non-map screens.

---

## 4. Verification & Validation Strategy

1. **Structural Contract Verification**:
   - `FollowMePaddingContractTest.kt`: Verify `ATrainingTrackerMap.kt` applies `topPadding` during `FOLLOW_ME` and `0.dp` otherwise.
2. **Localization Parity Verification**:
   - `MapCameraLocalizationTest.kt`: Verify all 11 map camera tuning string keys across all 9 locales.
3. **End-to-End Clean-Room Regression**:
   - Run `./gradlew testDebugUnitTest` to guarantee 0 regressions across the entire test suite.
