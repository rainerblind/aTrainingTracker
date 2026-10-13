# Stage 2: Requirement & Test Specification - ATT-2746: Make elevation profile smoothing sigma configurable in expert settings and reduce default to 21 m

**Ticket**: [ATT-2746](https://atrainingtracker.atlassian.net/browse/ATT-2746)  
**Sub-task**: [ATT-2818](https://atrainingtracker.atlassian.net/browse/ATT-2818) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-2582](https://atrainingtracker.atlassian.net/browse/ATT-2582) (*Segments: Live Tracking, Exploration & Route Integration*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `2026-41.4`  
**Requirement Mapping**: `REQ-UI-297` (*Distance-Weighted Elevation Profile Gaussian Smoothing & Continuous Slope Grade Evaluation*)  
**Test Spec ID**: `TST-UI-257` (*Distance-Weighted Elevation Profile Gaussian Smoothing & Continuous Slope Grade Verification*)  
**Branch**: `improvement/ATT-2746`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-09  

---

## 1. Requirement Specification (REQ-UI-297)

### 1.1 Problem Statement & Rationale
During Sprint Review 2026-41.3 on a Google Pixel 10 (physical device verification of ATT-2512), the athlete observed that while distance-weighted Gaussian smoothing eliminated sensor quantization staircases and derivative zebra artifacts, the initial spatial bandwidth $\sigma = 35.0\,\text{m}$ (with $3\sigma = 105.0\,\text{m}$ cutoff) smooths the elevation profile slightly too aggressively:

> *"Approved. But I have the feeling that the filtering is too much. Could we please reduce the sigma? E.g. to 21 m. Or even make this configurable within the advanced settings."*

On punchy rolling hills, steep ramps, and sudden pitch transitions, $\sigma = 35.0\,\text{m}$ damps genuine terrain inflections. Reducing the factory default to $\sigma = 21.0\,\text{m}$ ($3\sigma = 63.0\,\text{m}$) sharpens profile responsiveness while remaining wide enough to bridge 0.2m–0.4m sensor quantization steps (recorded at 1 Hz, 2–8 m spacing). Furthermore, exposing $\sigma$ as an adjustable slider in Expert Settings empowers athletes to tune the smoothing trade-off according to their personal preference.

### 1.2 Functional & Architectural Requirements
The system SHALL refine `REQ-UI-297` as follows:

1. **Refined Default Spatial Bandwidth (`ElevationSmoothingMath.kt`)**:
   - `DEFAULT_SIGMA` SHALL be reduced from `35.0` to `21.0` meters.
   - `DEFAULT_CUTOFF` SHALL be reduced from `105.0` to `63.0` meters ($3\sigma$).
   - `smoothAltitudes` parameter `cutoff` SHALL default dynamically to `3.0 * sigma` so custom sigma values automatically scale their spatial cutoff window.

2. **Configurable Spatial Bandwidth in Tuning Preferences (`TuningPreferencesDataStore.kt`)**:
   - `TuningPreferencesDefaults` SHALL declare:
     - `DEFAULT_ELEVATION_SMOOTHING_SIGMA_METERS = 21.0f`
     - `MIN_ELEVATION_SMOOTHING_SIGMA_METERS = 10.0f`
     - `MAX_ELEVATION_SMOOTHING_SIGMA_METERS = 50.0f`
     - `STEP_ELEVATION_SMOOTHING_SIGMA_METERS = 1.0f`
   - `TuningConfig` SHALL include `val elevationSmoothingSigmaMeters: Float = TuningPreferencesDefaults.DEFAULT_ELEVATION_SMOOTHING_SIGMA_METERS`.
   - `TuningPreferencesDataStore` SHALL declare `KEY_ELEVATION_SMOOTHING_SIGMA_METERS = floatPreferencesKey("tuning_elevation_smoothing_sigma_meters")`, add it to `ALL_KEYS`, map and clamp it in `tuningConfigFlow` within `[10.0f, 50.0f]`, provide `suspend fun updateElevationSmoothingSigmaMeters(sigma: Float)`, and restore `21.0f` in `resetToDefaults()`.

3. **Expert Settings UI Integration (`AftermathAnalysisSection.kt` & `AdvancedTuningDialog.kt`)**:
   - In `AftermathAnalysisSection.kt`, the system SHALL declare a `TuningSliderItem` for elevation smoothing sigma:
     - Title: `R.string.tuning_elevation_smoothing_sigma_title`
     - Value text: formatted in meters (e.g. "21 m" or localized)
     - Helper text: `R.string.tuning_elevation_smoothing_sigma_desc`
     - Default text: formatted default string
     - Range: `10.0f..50.0f`, steps: `40` (1m discrete increments)
   - `AdvancedTuningDialog.kt` SHALL initialize `elevationSmoothingSigmaMeters` state from `persistedConfig`, forward changes to `AftermathAnalysisSection`, persist the updated value in `saveTuningConfig`, and reset the state in the factory defaults action.
   - `TuningSubtitleFormatter.kt` SHALL incorporate the active elevation smoothing sigma into `formatAftermathSubtitle`.

4. **Dynamic Profile Reactivity (`ElevationProfile.kt`)**:
   - `ElevationProfile` SHALL accept an optional parameter `smoothingSigma: Double? = null`.
   - When `smoothingSigma != null`, `ElevationProfile` uses `smoothingSigma`.
   - When `smoothingSigma == null`, `ElevationProfile` reactively reads `tuningConfig.elevationSmoothingSigmaMeters.toDouble()` from `TuningPreferencesDataStore`.
   - `cachedData` SHALL include `effectiveSigma` in its `remember` keys, triggering recalculation of `smoothedAltitudes` with the updated sigma whenever preferences change.

5. **100% 9-Language Localization Parity**:
   - String resources `tuning_elevation_smoothing_sigma_title` and `tuning_elevation_smoothing_sigma_desc` SHALL exist, be non-empty, and contain zero unescaped format tokens across all 9 supported application locales: `values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`.

6. **Preservation of System Invariants**:
   - Exact first-order boundary Loess linear regression logic ($d=0, d=D_{total}$) remains strictly preserved without boundary sag or peaking.
   - Sliding two-pointer window maintaining $O(N)$ linear complexity and primitive `DoubleArray` operations with zero inner-loop heap allocations remains intact.
   - Continuous slope grade derivative evaluation and slope zone colors (`Zone1`..`Zone5`) remain intact.
   - Total ascent / descent calculations in `TrackAnalysis` and database schemas (`Segments.db`, `WorkoutSummaries.db`, `Tracks.db`) MUST NOT be altered.
   - Zero regression across the entire clean-room test suite (`./gradlew testDebugUnitTest`).

### Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-UI-297` (*Distance-Weighted Elevation Profile Gaussian Smoothing & Continuous Slope Grade Evaluation*).
* **Historical Origin & Commit Trace**: Commit `7630148d` (Ticket `ATT-2512`, Sprint `2026-41.3`, Epic `ATT-2582`).
* **Root Reason for Existing Formulation**: Commit `7630148d` introduced a fixed $\sigma = 35.0\,\text{m}$ ($3\sigma = 105.0\,\text{m}$) to eliminate 0.2m–0.4m sensor quantization plateaus and derivative zebra artifacts. While mathematically robust against discrete quantization noise, $\sigma = 35.0\,\text{m}$ acts slightly too aggressively on punchy climbs and short terrain features, damping authentic terrain inflections.
* **Preservation of Core Invariants**: Reducing the default bandwidth to $\sigma = 21.0\,\text{m}$ ($3\sigma = 63.0\,\text{m}$) continues to span multiple GPS/barometer points (typically 1 Hz, 2–8 m spacing on climbs), reliably preventing quantization staircasing while sharpening short-pitch gradient response. Exposing $\sigma$ as an adjustable parameter (10.0m–50.0m) empowers the athlete to tune the filter to their terrain preference. System invariants, zero-allocation inner loops, and continuous grade evaluation are 100% preserved.

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Reduced Default Sigma)**:
  * *Given* default settings without custom user override,
  * *When* `ElevationSmoothingMath.smoothAltitudes` is invoked with default arguments,
  * *Then* it SHALL apply $\sigma = 21.0\,\text{m}$ and cutoff $3\sigma = 63.0\,\text{m}$, producing a sharper profile on short-pitch gradient changes than $\sigma = 35.0\,\text{m}$ while maintaining smooth continuous slope grades.
* **Criterion 2 (Expert Setting Configuration)**:
  * *Given* the athlete in `AdvancedTuningDialog` under the Aftermath & Analysis section,
  * *When* observing the controls,
  * *Then* an adjustable slider for elevation smoothing sigma is present with range 10m to 50m and default 21m.
* **Criterion 3 (Preference Clamping & Persistence)**:
  * *Given* a value updated via slider or programmatic call,
  * *When* saved to `TuningPreferencesDataStore`,
  * *Then* values below 10m are clamped to 10m, values above 50m are clamped to 50m, and the persisted value is emitted via `tuningConfigFlow`.
* **Criterion 4 (Dynamic Profile Reactivity)**:
  * *Given* an active `ElevationProfile` rendered on screen,
  * *When* `elevationSmoothingSigmaMeters` in preferences changes from 21m to 12m,
  * *Then* the elevation profile cache SHALL invalidate and re-render with $\sigma = 12.0\,\text{m}$.
* **Criterion 5 (Factory Reset)**:
  * *Given* a customized sigma (e.g. 15m),
  * *When* the athlete taps "Reset to Factory Defaults",
  * *Then* `elevationSmoothingSigmaMeters` SHALL be restored to `21.0f`.
* **Criterion 6 (9-Language Parity)**:
  * *Given* all 9 supported application locales,
  * *When* executing the localization parity audit,
  * *Then* all newly added string keys SHALL exist and be non-empty across all locales.

---

## 2. Test Specification (TST-UI-257)

### Test Case 1: Pure Kernel Mathematical & Dynamic Cutoff Tests (`[TST-UI-257.1]`)
* **Scope**: Mathematical Unit Tests
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/ElevationSmoothingMathTest.kt`
* **Preconditions**: Monotonically distance-ordered synthetic path points with quantization steps (0.2m–0.4m).
* **Actions & Assertions**:
  - Verify `DEFAULT_SIGMA == 21.0` and `DEFAULT_CUTOFF == 63.0`.
  - Verify `smoothAltitudes` with custom `sigma = 15.0` dynamically uses `cutoff = 45.0` ($3\sigma$).
  - Verify boundary weight renormalization at $d=0$ and $d=D_{total}$ matches endpoint altitudes within 0.1%.
  - Verify sharp gradient responsiveness: compare smoothed output of $\sigma = 21.0\,\text{m}$ vs $\sigma = 35.0\,\text{m}$ on a short punchy ramp (e.g. 20m span with 10% grade jump) asserting higher peak preservation with $\sigma = 21.0\,\text{m}$.
  - Verify degenerate inputs (empty, single point, duplicate coordinates) return cleanly.
  - Verify benchmark latency on 10,000 points executes in $< 15\,\text{ms}$.

### Test Case 2: Tuning Preferences Clamping & Persistence Unit Tests (`[TST-UI-257.2]`)
* **Scope**: DataStore Unit Tests
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/settings/TuningPreferencesDataStoreTest.kt`
* **Preconditions**: Initialized or mocked `TuningPreferencesDataStore`.
* **Actions & Assertions**:
  - Verify default `TuningConfig().elevationSmoothingSigmaMeters == 21.0f`.
  - Verify clamping bounds: input `5.0f` clamped to `10.0f`; input `75.0f` clamped to `50.0f`.
  - Verify `updateElevationSmoothingSigmaMeters(25.0f)` persists and emits `25.0f`.
  - Verify `resetToDefaults()` restores `21.0f`.

### Test Case 3: UI Contract & Dynamic Reactivity Tests (`[TST-UI-257.3]`)
* **Scope**: Compose Contract & Architecture Tests
* **Target Files**:
  - `app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/ElevationProfileSmoothingContractTest.kt`
  - `app/src/test/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/AftermathAnalysisSectionContractTest.kt`
* **Assertions**:
  - Verify `ElevationProfile.kt` accepts `smoothingSigma` parameter and delegates to `ElevationSmoothingMath.smoothAltitudes(..., sigma = ...)`.
  - Verify `ElevationProfile.kt` observes `tuningConfigFlow` from `TuningPreferencesDataStore`.
  - Verify `AftermathAnalysisSection.kt` renders `TuningSliderItem` with title `tuning_elevation_smoothing_sigma_title`, range `10.0f..50.0f`, and 40 steps.
  - Verify `AdvancedTuningDialog.kt` binds `elevationSmoothingSigmaMeters` to state, `saveTuningConfig`, and factory reset.
  - Verify `TuningSubtitleFormatter.kt` formats the aftermath subtitle with sigma.

### Test Case 4: 9-Language Localization Parity Audit (`[TST-UI-257.4]`)
* **Scope**: Localization Parity Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/AftermathAnalysisLocalizationTest.kt`
* **Goal**: Verify string presence and valid tokens across all 9 locales:
  - `tuning_elevation_smoothing_sigma_title`
  - `tuning_elevation_smoothing_sigma_desc`
* **Expected Result**: 100% parity across `values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`.

### Test Case 5: Clean-Room Regression Suite (`[TST-UI-257.5]`)
* **Command**: `./gradlew testDebugUnitTest`
* **Goal**: Verify 100% pass rate across the full test suite with 0 regressions.

---

## 3. Traceability Matrix

| Test Case | Scope | Target File / Class | Requirement Mapping | Status |
| :--- | :--- | :--- | :--- | :--- |
| `[TST-UI-257.1]` | Pure Math Unit | `ElevationSmoothingMathTest.kt` | `REQ-UI-297` (clauses 1, 2, 4) | Specified |
| `[TST-UI-257.2]` | Preferences Unit | `TuningPreferencesDataStoreTest.kt` | `REQ-UI-297` (clause 2) | Specified |
| `[TST-UI-257.3]` | UI Contract | `ElevationProfileSmoothingContractTest.kt`, `AftermathAnalysisSectionContractTest.kt` | `REQ-UI-297` (clauses 3, 4) | Specified |
| `[TST-UI-257.4]` | Localization | `AftermathAnalysisLocalizationTest.kt` | `REQ-UI-297` (clause 5), `REQ-UI-106` | Specified |
| `[TST-UI-257.5]` | Clean-Room Suite | `./gradlew testDebugUnitTest` | `REQ-PRO-001` | Specified |
