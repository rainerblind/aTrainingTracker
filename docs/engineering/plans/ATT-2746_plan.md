# Stage 3: Implementation Plan - ATT-2746: Make elevation profile smoothing sigma configurable in expert settings and reduce default to 21 m

**Ticket**: [ATT-2746](https://atrainingtracker.atlassian.net/browse/ATT-2746)  
**Sub-task**: [ATT-2819](https://atrainingtracker.atlassian.net/browse/ATT-2819) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-2582](https://atrainingtracker.atlassian.net/browse/ATT-2582) (*Segments: Live Tracking, Exploration & Route Integration*)  
**Target Release**: `Unscheduled` (In-Sprint `2026-41.4`)  
**Active Sprint**: `2026-41.4`  
**Requirement Mapping**: `REQ-UI-297` (*Distance-Weighted Elevation Profile Gaussian Smoothing & Continuous Slope Grade Evaluation*)  
**Test Mapping**: `TST-UI-257` (*Distance-Weighted Elevation Profile Gaussian Smoothing & Continuous Slope Grade Verification*)  
**Branch**: `improvement/ATT-2746`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-09  

---

## 1. Problem Description & Background

During Sprint Review 2026-41.3 on a Google Pixel 10 (ATT-2512 verification), the athlete observed that while distance-weighted Gaussian kernel smoothing effectively eliminates discrete 0.2m–0.4m sensor quantization steps, the hardcoded spatial bandwidth $\sigma = 35.0\,\text{m}$ (cutoff $3\sigma = 105.0\,\text{m}$) overdamps authentic terrain inflections on punchy ramps and rolling hills. The athlete requested:
1. Reducing the factory default spatial bandwidth to $\sigma = 21.0\,\text{m}$ ($3\sigma = 63.0\,\text{m}$).
2. Exposing the smoothing sigma as a configurable slider in Expert Settings (range 10.0m to 50.0m, default 21.0m) to allow personalization.
3. Enabling dynamic profile reactivity so changes in preferences immediately re-render elevation profiles without requiring app restarts.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-UI-297` (*Distance-Weighted Elevation Profile Gaussian Smoothing & Continuous Slope Grade Evaluation*)
  - Clause 1: Reduced default $\sigma = 21.0\,\text{m}$, cutoff $3\sigma = 63.0\,\text{m}$, dynamic cutoff scaling ($3.0 * \sigma$).
  - Clause 2: Configurable DataStore preference in `TuningPreferencesDataStore.kt` with safety bounds $[10.0\text{f}, 50.0\text{f}]$ and factory reset to $21.0\text{f}$.
  - Clause 3: Expert Settings slider item in `AftermathAnalysisSection.kt` and `AdvancedTuningDialog.kt`.
  - Clause 4: Dynamic reactivity in `ElevationProfile.kt` observing `tuningConfigFlow`.
  - Clause 5: 100% 9-language localization parity for title and description strings.
  - Clause 6: Strict preservation of linear $O(N)$ sliding window, Loess first-order boundary regression, and continuous slope grade evaluation.
* **Test Mapping**: `TST-UI-257` (*Distance-Weighted Elevation Profile Gaussian Smoothing & Continuous Slope Grade Verification*)
  - `TST-UI-257.1`: Pure kernel mathematical unit tests in `ElevationSmoothingMathTest.kt`.
  - `TST-UI-257.2`: Performance benchmark tests ($N=10,000$ in $< 15\,\text{ms}$).
  - `TST-UI-257.3`: DataStore preference persistence and clamping unit tests in `TuningPreferencesDataStoreTest.kt`.
  - `TST-UI-257.4`: UI contract and dynamic reactivity tests in `AftermathAnalysisSectionContractTest.kt`, `AdvancedTuningDialogTest.kt`, and `ElevationProfileSmoothingContractTest.kt`.
  - `TST-UI-257.5`: 9-language localization parity verification in `AftermathAnalysisLocalizationTest.kt`.
  - `TST-UI-257.6`: Clean-room regression suite pass rate 100%.

---

## 3. System Invariants & Preserved Behavior

1. **Zero Unintended Regressions**: Existing feature suites continue to pass cleanly with 100% pass rate.
2. **Loess Boundary Linear Regression Invariant**: First-order boundary slope correction at terrain endpoints ($d=0, d=D_{total}$) remains intact, preventing endpoint sag and summit distortion.
3. **Primitive Complexity Invariant**: $O(N)$ sliding two-pointer window over primitive `DoubleArray` with zero inner-loop heap allocations remains strictly preserved.
4. **Physical Derivative Evaluation**: `calculateGrade` continuous percentage slope calculation and zone color mapping (`Zone1`..`Zone5`) remain unchanged.
5. **Database & Analysis Invariance**: Total ascent / descent calculations in `TrackAnalysis` and database schemas (`Tracks.db`, `Segments.db`, `WorkoutSummaries.db`) are not touched.
6. **Subtask Self-Sufficiency**: Subtasks transition directly to `Erledigt` upon passing Gate audit via `freigabe`.
7. **Parent Human Gate Invariance**: Terminal completion of parent tickets remains reserved for the human user in `Final Review (Human)`.

---

## 4. Proposed Architectural Changes

### Component 1: `ElevationSmoothingMath` (`ui/map/ElevationSmoothingMath.kt`)
- Reduce `DEFAULT_SIGMA` constant from `35.0` to `21.0`.
- Reduce `DEFAULT_CUTOFF` constant from `105.0` to `63.0`.
- Update `smoothAltitudes` signature to dynamically default `cutoff = 3.0 * sigma`, ensuring custom sigma values automatically adjust their spatial cutoff window.

### Component 2: `TuningPreferencesDataStore` (`settings/TuningPreferencesDataStore.kt`)
- In `TuningPreferencesDefaults`:
  - `DEFAULT_ELEVATION_SMOOTHING_SIGMA_METERS = 21.0f`
  - `MIN_ELEVATION_SMOOTHING_SIGMA_METERS = 10.0f`
  - `MAX_ELEVATION_SMOOTHING_SIGMA_METERS = 50.0f`
  - `STEP_ELEVATION_SMOOTHING_SIGMA_METERS = 1.0f`
- In `TuningConfig`:
  - Add property `val elevationSmoothingSigmaMeters: Float = TuningPreferencesDefaults.DEFAULT_ELEVATION_SMOOTHING_SIGMA_METERS`.
- In `TuningPreferencesDataStore.Companion`:
  - Add `KEY_ELEVATION_SMOOTHING_SIGMA_METERS: Preferences.Key<Float> = floatPreferencesKey("tuning_elevation_smoothing_sigma_meters")`.
  - Add key to `ALL_KEYS`.
- In `tuningConfigFlow`:
  - Map and coerce `KEY_ELEVATION_SMOOTHING_SIGMA_METERS` into range `[10.0f, 50.0f]`.
- In `saveTuningConfig`:
  - Persist clamped `config.elevationSmoothingSigmaMeters`.
- Add updater function:
  - `suspend fun updateElevationSmoothingSigmaMeters(sigma: Float)`.
- In `resetToDefaults()`:
  - Key is removed through `ALL_KEYS`, cleanly falling back to default $21.0\text{f}$.

### Component 3: Expert Settings UI (`AftermathAnalysisSection.kt` & `AdvancedTuningDialog.kt`)
- In `AftermathAnalysisSection.kt`:
  - Accept `elevationSmoothingSigmaMeters: Float = TuningPreferencesDefaults.DEFAULT_ELEVATION_SMOOTHING_SIGMA_METERS` and `onElevationSmoothingSigmaChange: (Float) -> Unit = {}`.
  - Render a `TuningSliderItem` with title `R.string.tuning_elevation_smoothing_sigma_title`, helper `R.string.tuning_elevation_smoothing_sigma_desc`, range `10.0f..50.0f`, and 39 intermediate steps (1m step increments).
- In `AdvancedTuningDialog.kt`:
  - Declare state `var elevationSmoothingSigmaMeters by remember { mutableFloatStateOf(TuningPreferencesDefaults.DEFAULT_ELEVATION_SMOOTHING_SIGMA_METERS) }`.
  - Sync state from `persistedConfig.elevationSmoothingSigmaMeters` in `LaunchedEffect(persistedConfig)`.
  - Forward state and setter to `AftermathAnalysisSection`.
  - Persist in `saveTuningConfig(newConfig)`.
  - Reset to `TuningPreferencesDefaults.DEFAULT_ELEVATION_SMOOTHING_SIGMA_METERS` in the Reset to Factory Defaults button action.
- In `AdvancedTuningAccordion.kt` (`TuningSubtitleFormatter`):
  - Update `formatAftermathSubtitle` to accept `smoothingSigma: Float = TuningPreferencesDefaults.DEFAULT_ELEVATION_SMOOTHING_SIGMA_METERS` and append ` · σ: ${smoothingSigma.roundToInt()}m`.

### Component 4: Reactive Profile Reactivity (`ElevationProfile.kt`)
- Add parameter `smoothingSigma: Double? = null` to `ElevationProfile`.
- Collect `tuningConfigFlow` from `TuningPreferencesDataStore`.
- Compute `effectiveSigma = smoothingSigma ?: tuningConfig.elevationSmoothingSigmaMeters.toDouble()`.
- Include `effectiveSigma` in `remember` key for `cachedData`.
- Invoke `ElevationSmoothingMath.smoothAltitudes(pathPointsDownsampled, sigma = effectiveSigma)`.

### Component 5: Multi-Language Localization Parity (9 Locales)
- Add `tuning_elevation_smoothing_sigma_title` and `tuning_elevation_smoothing_sigma_desc` to:
  - `app/src/main/res/values/strings.xml` (EN)
  - `app/src/main/res/values-de/strings.xml` (DE)
  - `app/src/main/res/values-es/strings.xml` (ES)
  - `app/src/main/res/values-fr/strings.xml` (FR)
  - `app/src/main/res/values-it/strings.xml` (IT)
  - `app/src/main/res/values-ja/strings.xml` (JA)
  - `app/src/main/res/values-nl/strings.xml` (NL)
  - `app/src/main/res/values-pl/strings.xml` (PL)
  - `app/src/main/res/values-pt/strings.xml` (PT)

### UI Consistency (Rule 23)
* **Reference screen / component**: `AdvancedTuningDialog.kt` and `AftermathAnalysisSection.kt` (Pace Ceiling slider, GPS filter sliders in `SensorsGpsFilterSection.kt`).
* **Reused components**: `TuningSliderItem`, `TuningAccordionSection`.
* **Theme tokens**: 8 dp spacing grid, `MaterialTheme.typography.titleSmall`, `MaterialTheme.typography.bodySmall`, `MaterialTheme.colorScheme.onSurfaceVariant`.
* **New one-off styles & justification**: None. Reuses existing standardized `TuningSliderItem` component.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Mathematical Engine Update (`ElevationSmoothingMath.kt`)
* Files: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/ElevationSmoothingMath.kt`
* Changes: Update `DEFAULT_SIGMA = 21.0`, `DEFAULT_CUTOFF = 63.0`, and `cutoff: Double = 3.0 * sigma`.
* Verification: Update unit tests in `ElevationSmoothingMathTest.kt` asserting new defaults and dynamic cutoff scaling.

### Step 2: DataStore Preference & Clamping (`TuningPreferencesDataStore.kt`)
* Files: `app/src/main/java/com/atrainingtracker/trainingtracker/settings/TuningPreferencesDataStore.kt`
* Changes: Add constants, `elevationSmoothingSigmaMeters` in `TuningConfig`, `KEY_ELEVATION_SMOOTHING_SIGMA_METERS`, flow mapping with clamping $[10.0\text{f}, 50.0\text{f}]$, `saveTuningConfig`, and `updateElevationSmoothingSigmaMeters`.
* Verification: Create/update `TuningPreferencesDataStoreElevationSmoothingTest.kt` asserting persistence, bounds clamping, and factory reset.

### Step 3: Localization String Resources (9 Locales)
* Files: `app/src/main/res/values*/strings.xml` (9 locales)
* Changes: Add `tuning_elevation_smoothing_sigma_title` and `tuning_elevation_smoothing_sigma_desc` across EN, DE, ES, FR, IT, JA, NL, PL, PT.
* Verification: Unit test in `AftermathAnalysisLocalizationTest.kt`.

### Step 4: Expert Settings UI Integration (`AftermathAnalysisSection.kt`, `AdvancedTuningDialog.kt`, `AdvancedTuningAccordion.kt`)
* Files:
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/categories/AftermathAnalysisSection.kt`
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/AdvancedTuningDialog.kt`
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/AdvancedTuningAccordion.kt`
* Changes: Integrate `TuningSliderItem`, wire state, persistence, factory reset, and subtitle formatter.
* Verification: Compose contract tests in `AftermathAnalysisSectionContractTest.kt` and `AdvancedTuningDialogContractTest.kt`.

### Step 5: Dynamic Elevation Profile Reactivity (`ElevationProfile.kt`)
* Files: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/ElevationProfile.kt`
* Changes: Accept `smoothingSigma: Double? = null`, collect `tuningConfigFlow`, incorporate `effectiveSigma` into `cachedData` remember key, and pass `sigma = effectiveSigma` to `smoothAltitudes`.
* Verification: Update `ElevationProfileSmoothingContractTest.kt` asserting dynamic observation and custom parameter delegation.

### Step 6: Targeted Unit & Contract Tests
* Command:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.map.ElevationSmoothingMathTest" --tests "com.atrainingtracker.trainingtracker.ui.map.ElevationProfileSmoothingContractTest" --tests "com.atrainingtracker.trainingtracker.settings.TuningPreferencesDataStoreTest" --tests "com.atrainingtracker.trainingtracker.ui.settings.tuning.*"
  ```

---

## 6. Verification & Rollback Plan

* **Verification**: Targeted tests during construction, followed by full clean-room test suite (`./gradlew clean testDebugUnitTest`) in Stage 5.
* **Rollback**: Branch isolation (`improvement/ATT-2746`) allows full revert without impacting `develop` or sprint branches.
