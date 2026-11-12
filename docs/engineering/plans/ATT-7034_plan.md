# Stage 3: Implementation Plan - ATT-7034: Track Morning Sleep & Minimal Wellness with or without HR strap

**Ticket**: [ATT-7034](https://rainerblind.atlassian.net/browse/ATT-7034) | **Parent Epic**: [ATT-3539](https://rainerblind.atlassian.net/browse/ATT-3539) (*HRV Autonomic Metrics*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.3` | **Branch**: `subagent-ATT-7034---ATT-3539-Pro-Agent-self-b201a997`  
**Requirement Mapping**: `REQ-HRV-038`..`REQ-HRV-042` | **Test Mapping**: `TST-HRV-038`..`TST-HRV-042`  
**Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-12  

---

## 1. Problem Description & Background
Athletes currently lack a mechanism to track subjective morning sleep and wellness either during the idle 60–120s morning HRV countdown or on days when no heart rate strap is worn. This implementation provides configurable progressive disclosure in Advanced Settings (default `OFF`), standalone (no-strap) and in-countdown wellness tracking on `MorningHrvCheckScreen`, SQLite schema migration to `DB_VERSION = 2` with nullable HRV metrics, and optional Health Connect `SleepSessionRecord` prefill.

---

## 2. Traceability & Requirements Mapping
* **Requirements**: `REQ-HRV-038` (Settings Progressive Disclosure), `REQ-HRV-039` (SQLite Schema Evolution V2), `REQ-HRV-040` (Standalone No-Strap Logging), `REQ-HRV-041` (In-Measurement Combined Logging), `REQ-HRV-042` (Health Connect Prefill & 9-Locale Parity).
* **Test Mapping**: `TST-HRV-038` (Settings/DataStore unit tests), `TST-HRV-039` (Database migration & schema parity), `TST-HRV-040` (Standalone save & SWC isolation), `TST-HRV-041` (In-measurement unified save), `TST-HRV-042` (Health Connect fallback, string parity & Tier 1 tests).

---

## 3. System Invariants & Preserved Behavior
1. **Default OFF & UI Invariance**: When disabled, `MorningHrvCheckScreen` remains 100% identical to V1.
2. **SWC Corridor & Math Isolation**: Standalone entries (`rmssd_ms IS NULL`) are strictly excluded from `ln(rMSSD)` SWC corridors in `HrvReadinessTrendMath` and `HrvReadinessCalculator`.
3. **Database Schema Parity**: Full `onCreate`/`onUpgrade` parity (Rule 64) and `DatabaseSchemaRegistry` reference for `DB_VERSION = 2` (Rule 62).
4. **Zero Workout Overhead**: Never touches `TrackerService` or creates rows in `WorkoutSummaries.db`.
5. **Code Lock**: Production code modifications remain locked until Gate 3 sign-off (`Rule 4`).

---

## 4. Proposed Architectural Changes & UI Consistency
* **Settings Layer (`TuningPreferencesDataStore.kt`, `SettingsDataStore.kt`)**: Add master key `enableMorningSleepAndWellness` (default `false`) and subordinate checkbox flags in `TuningConfig`.
* **Database Layer (`HrvReadinessDatabaseManager.kt`, `HrvReadinessEntry.kt`)**: Bump `DB_VERSION = 2`. Migrate `hrv_readiness_entries` to allow nullable HRV columns and add 5 new columns (`sleep_duration_min`, `sleep_quality_1_5`, `morning_energy_1_5`, `muscle_soreness_1_5`, `context_tags`).
* **UI & ViewModels (`PhysiologyModelsSection.kt`, `MorningHrvCheckScreen.kt`, `MorningHrvCheckViewModel.kt`)**: Add `MorningWellnessInputCard` interactive before/during/after countdown; add `Save Morning Wellness` standalone action.
* **Health Connect (`HealthConnectManager.kt`)**: Query `SleepSessionRecord` to prefill sleep duration if permission is granted.
* **UI Consistency**: Reuses `SubordinateTuningToggleItem`, M3 Sliders/FilterChips, `MaterialTheme.colorScheme`, and `12.dp` card corners per `docs/design_guidelines.md`.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: SQLite Schema Evolution V2 & DTO Mapping
* Files: `HrvReadinessDatabaseManager.kt`, `HrvReadinessEntry.kt`, `HrvReadinessCalculator.kt`, `HrvReadinessTrendMath.kt`, `DatabaseSchemaRegistry.kt`
* Implement `DB_VERSION = 2`, `onUpgrade` table migration, nullable HRV fields, and 5 wellness columns. Update `DatabaseSchemaRegistryHrvTest`.

### Step 2: Settings DataStore & Progressive Disclosure Controls
* Files: `TuningPreferencesDataStore.kt`, `SettingsDataStore.kt`, `PhysiologyModelsSection.kt`, `AdvancedTuningScreen.kt`, `AdvancedTuningDialog.kt`
* Implement master toggle (default OFF) and 4 subordinate toggles with reactive StateFlows.

### Step 3: Standalone & In-Measurement UX on Morning HRV Screen
* Files: `MorningHrvCheckScreen.kt`, `MorningHrvCheckViewModel.kt`
* Build `MorningWellnessInputCard`, wire reactive wellness state, standalone `saveStandaloneWellness()` button, and combined completion save.

### Step 4: Health Connect SleepSessionRecord Optional Prefill
* Files: `HealthConnectManager.kt`, `AndroidManifest.xml`
* Implement optional read for `SleepSessionRecord`, auto-populating sleep duration slider when granted.

### Step 5: 9-Language Localization & Verification Tests
* Files: `res/values*/strings_hrv.xml`, `res/values*/strings_tuning.xml`, `MorningHrvCheckViewModelTest.kt`, `HrvReadinessDatabaseManagerTest.kt`
* Add translations across all 9 locales. Run targeted package unit tests and `TranslationParityTest`.
* Test Command: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.database.hrv.*" --tests "com.atrainingtracker.trainingtracker.ui.hrv.*" --tests "com.atrainingtracker.trainingtracker.settings.*" --tests "com.atrainingtracker.trainingtracker.localization.TranslationParityTest" --tests "com.atrainingtracker.trainingtracker.build.*"`

---

## 6. Verification & Rollback Plan
* **Verification**: Unit tests covering V1->V2 migration, standalone/combined saves, settings toggle gating, and string parity.
* **Rollback**: Isolated branch revert leaves `sprint/2026-46.3` unaffected.
