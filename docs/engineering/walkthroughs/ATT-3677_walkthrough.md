# Engineering Walkthrough - ATT-3677

**Ticket Key**: ATT-3677
**Summary**: Configure auto-pause speed thresholds and debounce windows independently per sport (Bike, Run, Other)
**Requirement**: REQ-TRK-018
**Test Specification**: TST-TRK-010
**Author**: Agent 1 (Pair Programming Assistant)
**Date**: 2026-10-15

---

## 1. Executive Summary

ATT-3677 decouples the auto-pause speed thresholds and debounce delay windows so that athletes can independently configure Cycling, Running, and Other sports in Advanced Tuning. 

Key technical accomplishments:
- **Integer-Only Sliders**: Replaced fractional float stepping with strictly whole integer ranges aligned with the user product vision ($1..5\text{ user units}$ pause speed threshold; $1..10\text{ s}$ Bike, $1..20\text{ s}$ Run, $1..60\text{ s}$ Other debounce delay).
- **Metric & Imperial Localization**: Unit-aware thresholds step in whole units ($1..5\text{ km/h}$ Metric, $1..5\text{ mph}$ Imperial) with tailored defaults (Bike: $3\text{ km/h}$ / $2\text{ mph}$; Run: $2\text{ km/h}$ / $1\text{ mph}$; Other: $1\text{ km/h}$ / $1\text{ mph}$).
- **Anti-Chatter Hysteresis Invariance**: Parameterized physical ceiling ($6.0\text{ km/h}$ Metric, $6.0\text{ mph}$ Imperial) prevents boundary inversion where resume speed could otherwise fall below pause speed under Imperial units.
- **1Hz Telemetry Loop Optimization**: Integrated a 5-second TTL in-memory cache with immediate reactive invalidation (`TrackerService.invalidateAutoPauseCache()`) on preference updates and global unit switches to prevent blocking DataStore reads on the 1Hz telemetry sampling tick.
- **Architectural Modularity**: Extracted `mapAutoPausePreferences(prefs)` into `TuningPreferencesDataStore` with robust legacy fallback mapping, verified by unit tests across all 9 supported languages.

---

## 2. Changes by Component

### 2.1 Domain & Tracking Service
- **`AutoPauseManager.kt`**:
  - Decoupled threshold and debounce lookups per `BSportType` (`BIKE`, `RUN`, `OTHER`/default).
  - Added `calculateResumeSpeedThresholdUserUnit(pauseThreshold, isMetric)` applying $+0.5$ deadband with unit-aware $6.0$ physical ceiling.
  - Added `@Deprecated` annotations with `ReplaceWith` suggestions to legacy fields.
- **`TrackerService.java`**:
  - Implemented thread-safe `mCachedAutoPauseConfig` with 5-second TTL, active unit switch detection, and static `invalidateAutoPauseCache()`.
  - Evaluates `TrainingApplication.isManualPauseLocked()` with top priority to prevent auto-resume while manually paused.
- **`SettingsDataStoreJavaHelper.kt`**:
  - Added synchronous `getAutoPauseConfig(context)` bridge evaluating active unit mode.

### 2.2 DataStore Persistence
- **`TuningPreferencesDataStore.kt`**:
  - Added 6 new integer keys:
    - `KEY_AUTO_PAUSE_BIKE_SPEED_THRESHOLD` (1..5)
    - `KEY_AUTO_PAUSE_BIKE_DEBOUNCE_SEC` (1..10)
    - `KEY_AUTO_PAUSE_RUN_SPEED_THRESHOLD` (1..5)
    - `KEY_AUTO_PAUSE_RUN_DEBOUNCE_SEC` (1..20)
    - `KEY_AUTO_PAUSE_OTHER_SPEED_THRESHOLD` (1..5)
    - `KEY_AUTO_PAUSE_OTHER_DEBOUNCE_SEC` (1..60)
  - Extracted `AutoPausePreferences` data class and `mapAutoPausePreferences(prefs)` companion method with backward compatibility migration logic.
  - Integrated `TrackerService.invalidateAutoPauseCache()` across `saveTuningConfig()` and all atomic mutators.

### 2.3 Compose UI & Localization
- **`SensorsGpsFilterSection.kt`**:
  - Embedded sport-specific speed threshold and debounce sliders directly under each sport toggle, displaying only when that sport is enabled.
  - Formatted slider values and default descriptions using localized string resources (`R.string.tuning_speed_value_format`, `R.string.tuning_seconds_value_format`) across all 9 supported languages.
  - Removed obsolete global auto-pause sliders from bottom of section.
- **`AdvancedTuningScreen.kt` & `AdvancedTuningDialog.kt`**:
  - Bound sport-specific thresholds and debounces to Compose state, `applyConfig`, and save routines.
- **`strings.xml` (9 Languages)**:
  - Added `tuning_speed_value_format` (`%1$d %2$s`) and `tuning_seconds_value_format` (`%1$d s`) across EN, DE, ES, FR, IT, JA, NL, PL, and PT.

---

## 3. Verification & Test Evidence

### 3.1 Targeted Unit Tests
- **`AutoPauseManagerTest.kt`**:
  - `TST-TRK-010_1`: Verified default activation contract (Bike enabled, Run/Other disabled).
  - `TST-TRK-010_2` & `TST-TRK-010_3`: Verified debounce counter and hysteresis deadband across sport profiles.
  - `TST-TRK-010_12`: Verified resume threshold calculation and unit ceiling boundary invariance ($6.0\text{ km/h}$ Metric, $6.0\text{ mph}$ Imperial).
  - `TST-TRK-010_13`: Verified sport-specific decoupling for Run (5s debounce) and Other (10s debounce).
  - `TST-TRK-010_14`: Verified Imperial unit conversion ($2\text{ mph} \to 0.894\text{ m/s}$ pause, $2.5\text{ mph} \to 1.118\text{ m/s}$ resume).
- **`TuningPreferencesDataStoreAutoPauseTest.kt`**:
  - Verified default constants, clamping bounds, and `ALL_KEYS` registration.
  - `mapAutoPausePreferences_migratesLegacyKeysCorrectly`: Verified legacy migration fallback (Scenario 1), sport-specific overrides (Scenario 2), and clean install defaults (Scenario 3).

### 3.2 Full Suite Regression
- Executed `./gradlew testDebugUnitTest` ensuring 100% clean-room test pass rate with zero regressions across the codebase.
