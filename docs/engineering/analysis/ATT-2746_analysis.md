# Stage 1 Analysis: ATT-2746 - Make elevation profile smoothing sigma configurable in expert settings and reduce default to 21 m

**Ticket**: [ATT-2746](https://atrainingtracker.atlassian.net/browse/ATT-2746)  
**Sub-task**: [ATT-2817](https://atrainingtracker.atlassian.net/browse/ATT-2817) (`[Analysis]`)  
**Parent Epic**: [ATT-2582](https://atrainingtracker.atlassian.net/browse/ATT-2582) (*Segments: Live Tracking, Exploration & Route Integration*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `2026-41.4`  
**Branch**: `improvement/ATT-2746`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-09  

---

## 1. Problem Statement & Motivation

During Sprint Review 2026-41.3 on a Google Pixel 10 (physical device verification of ATT-2512), the athlete observed that while the distance-weighted Gaussian kernel smoothing filter successfully eliminated discrete sensor quantization staircases and derivative zebra artifacts, the hardcoded spatial bandwidth $\sigma = 35.0\,\text{m}$ (with $3\sigma = 105.0\,\text{m}$ cutoff radius) smooths the elevation profile slightly too aggressively:

> *"Approved. But I have the feeling that the filtering is too much. Could we please reduce the sigma? E.g. to 21 m. Or even make this configurable within the advanced settings."*

### Current vs. Desired Behavior
* **Current Behavior**:
  - `ElevationSmoothingMath.DEFAULT_SIGMA` is hardcoded to `35.0` meters (`DEFAULT_CUTOFF = 105.0` meters).
  - Short-pitch ramps, punchy rolling hills, and sudden slope transitions are over-smoothed, blunting genuine elevation crests.
  - No preference exists in `TuningPreferencesDataStore` or `AdvancedTuningDialog` to allow athletes to adjust the spatial filter radius.
* **Desired Behavior**:
  - Reduce the factory default bandwidth to $\sigma = 21.0\,\text{m}$ ($3\sigma = 63.0\,\text{m}$ cutoff), restoring responsiveness to short-pitch terrain nuances while remaining wide enough to bridge 0.2m–0.4m sensor quantization steps.
  - Expose `elevationSmoothingSigmaMeters` as an adjustable slider preference (range: 10.0m to 50.0m, default: 21.0m, step: 1.0m) under Expert Settings (`AftermathAnalysisSection` inside `AdvancedTuningDialog`).
  - Wire the configured smoothing bandwidth reactively into `ElevationProfile`, updating profile rendering dynamically when the athlete adjusts the preference.

---

## 2. Root Cause Analysis (Forensic Investigation)

### 2.1 Architectural Investigation of Current State
1. **Mathematical Engine (`ElevationSmoothingMath.kt`)**:
   - `DEFAULT_SIGMA = 35.0` and `DEFAULT_CUTOFF = 105.0` are declared as static `const val` constants.
   - `smoothAltitudes(pathPoints: List<PathPoint>, sigma: Double = DEFAULT_SIGMA, cutoff: Double = DEFAULT_CUTOFF)` accepts parameters, but defaults to static constants. If callers pass a custom `sigma`, `cutoff` defaults to `DEFAULT_CUTOFF` unless callers also compute `3.0 * sigma`.
   - Modifying default `cutoff` to `3.0 * sigma` ensures that whenever a custom `sigma` is supplied, the spatial cutoff radius dynamically adapts to $3\sigma$.
2. **Preference Storage (`TuningPreferencesDataStore.kt`)**:
   - Manages expert preferences via Jetpack DataStore Preferences.
   - `TuningPreferencesDefaults` defines defaults and clamping ranges for various tuning categories.
   - `TuningConfig` holds an immutable snapshot collected via `tuningConfigFlow`.
   - `KEY_ELEVATION_SMOOTHING_SIGMA_METERS` is currently absent from `TuningPreferencesDataStore`.
3. **UI Configuration (`AdvancedTuningDialog.kt` & `AftermathAnalysisSection.kt`)**:
   - `AftermathAnalysisSection` currently houses:
     - `TuningSliderItem` for Minimum Pace Ceiling (`DEFAULT_PACE_CEILING_MIN_KM`).
     - Chips for Elevation Profile X-Axis Domain (`ProfileXAxisDomain.DISTANCE` vs. `ProfileXAxisDomain.TIME`).
     - Chips for Telemetry Graphs X-Axis Domain (`ProfileXAxisDomain.DISTANCE` vs. `ProfileXAxisDomain.TIME`).
   - A dedicated `TuningSliderItem` for elevation profile smoothing sigma fits naturally into `AftermathAnalysisSection`.
4. **Elevation Profile Presentation (`ElevationProfile.kt`)**:
   - `ElevationProfile` computes `smoothedAltitudes = ElevationSmoothingMath.smoothAltitudes(pathPointsDownsampled)` without passing a `sigma` parameter.
   - In `ElevationProfile`, `LocalContext.current` is available. Observing `tuningDataStore.tuningConfigFlow` allows dynamic extraction of `elevationSmoothingSigmaMeters`.
   - Adding an optional parameter `smoothingSigma: Double? = null` allows callers or previews/tests to override or supply a fixed sigma directly, falling back to `tuningConfig.elevationSmoothingSigmaMeters.toDouble()`.

---

## 3. User Scope Grounding (ATT-1250)

### In-Scope Goals
1. **Reduce Default Bandwidth**:
   - Update `DEFAULT_SIGMA` from `35.0` to `21.0` and `DEFAULT_CUTOFF` from `105.0` to `63.0` in `ElevationSmoothingMath.kt`.
   - Ensure `smoothAltitudes` parameter `cutoff` defaults dynamically to `3.0 * sigma`.
2. **Tuning Preferences Infrastructure**:
   - Add `DEFAULT_ELEVATION_SMOOTHING_SIGMA_METERS = 21.0f`, `MIN_ELEVATION_SMOOTHING_SIGMA_METERS = 10.0f`, `MAX_ELEVATION_SMOOTHING_SIGMA_METERS = 50.0f`, `STEP_ELEVATION_SMOOTHING_SIGMA_METERS = 1.0f` to `TuningPreferencesDefaults`.
   - Add `elevationSmoothingSigmaMeters: Float` to `TuningConfig` with default `21.0f`.
   - Add `KEY_ELEVATION_SMOOTHING_SIGMA_METERS` to `TuningPreferencesDataStore`, include in `ALL_KEYS`, map in `tuningConfigFlow`, implement `updateElevationSmoothingSigmaMeters(sigma: Float)`, and handle in `resetToDefaults()`.
3. **Expert Settings UI**:
   - Add a `TuningSliderItem` for elevation smoothing sigma in `AftermathAnalysisSection.kt` with range `10.0f..50.0f`, 40 steps (1m step increments).
   - Wire state in `AdvancedTuningDialog.kt`, update `TuningSubtitleFormatter.formatAftermathSubtitle` to reflect current sigma, and hook into reset action.
   - Add localized string resources across all 9 supported languages (`values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`).
4. **Dynamic Profile Integration**:
   - Wire `elevationSmoothingSigmaMeters` into `ElevationProfile.kt` so the cached profile data re-evaluates dynamically with the configured sigma.
5. **Contract & Regression Testing**:
   - Update `ElevationSmoothingMathTest` and `ElevationProfileSmoothingContractTest`.
   - Add unit tests for `TuningPreferencesDataStore` elevation smoothing sigma clamping and persistence.
   - Add UI contract tests for `AftermathAnalysisSection` and 9-language localization tests.

### Out-of-Scope Non-Goals (Scope Bounding)
1. **Ascent / Descent Calculation Changes**: Total ascent and descent algorithms in `TrackAnalysis` / `WorkoutSummaries` remain strictly untouched.
2. **Database Schema Alterations**: No SQLite migrations or alterations (`Segments.db`, `WorkoutSummaries.db`, `Tracks.db`).
3. **Telemetry Smoothing**: Heart rate, cadence, power, and speed telemetry smoothing algorithms are distinct and out of scope.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit

* **Original Requirement ID & Target**: `REQ-UI-297` (*Distance-Weighted Elevation Profile Gaussian Smoothing & Continuous Slope Grade Evaluation*).
* **Historical Origin & Commit Trace**: Commit `7630148d` (Ticket `ATT-2512`, Sprint `2026-41.3`, Epic `ATT-2582`).
* **Root Reason for Existing Formulation**: Commit `7630148d` introduced a fixed $\sigma = 35.0\,\text{m}$ ($3\sigma = 105.0\,\text{m}$) to eliminate 0.2m–0.4m sensor quantization plateaus and derivative zebra artifacts. While mathematically robust against discrete quantization noise, $\sigma = 35.0\,\text{m}$ acts slightly too aggressively on punchy climbs and short terrain features, damping authentic terrain inflections.
* **Preservation of Core Invariants**: Reducing the default bandwidth to $\sigma = 21.0\,\text{m}$ ($3\sigma = 63.0\,\text{m}$) continues to span multiple GPS/barometer points (typically 1 Hz, 2–8 m spacing on climbs), reliably preventing quantization staircasing while sharpening short-pitch gradient response. Exposing $\sigma$ as an adjustable parameter (10.0m–50.0m) empowers the athlete to tune the filter to their terrain preference. System invariants, zero-allocation inner loops, and continuous grade evaluation are 100% preserved.

---

## 5. Architectural Strategy & High-Level Solution

### Component Overview
```
+-----------------------------------------------------------------+
|                    AdvancedTuningDialog                         |
|  +-----------------------------------------------------------+  |
|  |                AftermathAnalysisSection                   |  |
|  |  - Pace Ceiling Slider                                    |  |
|  |  - Elevation Profile X-Axis Domain Chips                  |  |
|  |  - Telemetry Graphs X-Axis Domain Chips                   |  |
|  |  - [NEW] Elevation Smoothing Sigma Slider (10m - 50m)     |  |
|  +-----------------------------------------------------------+  |
+-----------------------------------------------------------------+
                                |
                                v
+-----------------------------------------------------------------+
|                  TuningPreferencesDataStore                     |
|  - Key: tuning_elevation_smoothing_sigma_meters                |
|  - Default: 21.0f (clamped between 10.0f and 50.0f)            |
|  - tuningConfigFlow emits updated TuningConfig                  |
+-----------------------------------------------------------------+
                                |
                                v
+-----------------------------------------------------------------+
|                       ElevationProfile                          |
|  - Observes tuningConfigFlow                                    |
|  - Uses effectiveSigma = smoothingSigma ?: config.sigma.toDouble()
|  - Calls ElevationSmoothingMath.smoothAltitudes(..., sigma)     |
+-----------------------------------------------------------------+
                                |
                                v
+-----------------------------------------------------------------+
|                    ElevationSmoothingMath                       |
|  - DEFAULT_SIGMA = 21.0                                         |
|  - DEFAULT_CUTOFF = 63.0                                        |
|  - smoothAltitudes(pathPoints, sigma = 21.0, cutoff = 3*sigma)  |
+-----------------------------------------------------------------+
```

---

## 6. System Invariants & Risk Assessment

* **Core Invariants**:
  1. Zero regression in existing features and unit tests.
  2. Local linear regression Loess boundary condition logic ($d=0, d=D_{total}$) remains mathematically exact.
  3. No heap allocations in inner convolution loop of `smoothAltitudes`.
  4. Parent ticket Human Decision Gate remains strictly enforced.
* **Risk Rating**: **LOW**
  - Isolated mathematical constant reduction and standard DataStore preference wiring.
  - Completely non-breaking change; existing tests and callers continue to operate seamlessly with new default or configured values.
