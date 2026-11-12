# Stage 5 Verification & Walkthrough: Track Morning Sleep & Minimal Wellness (ATT-7034)

* **Ticket**: [ATT-7034](https://rainerblind.atlassian.net/browse/ATT-7034) (*Track Morning Sleep & Minimal Wellness with or without HR strap*)
* **Sub-task**: ATT-7185 (*[Implementation] Track Morning Sleep & Minimal Wellness*)
* **Sprint**: `2026-46.3`
* **Target Release Version**: `V5.0.0`
* **Branch**: `subagent-ATT-7034---ATT-3539-Flash-Agent-self-6e4b15d7`

---

## 1. Verification Scope & Test Results

### 1.1 Tier 1 Targeted Unit & Contract Tests
* **HRV Database & Migration Tests**:
  - `HrvReadinessDatabaseManagerTest`: Schema V2 creation, table-copy migration V1->V2, nullable HRV persistence, and baseline window filtering (`rmssdMs IS NOT NULL`). (PASS)
  - `HrvReadinessEntryTest`: Symmetric single and batch `fromCursor` mapping and `toContentValues` conversion (Rule 20). (PASS)
  - `DatabaseSchemaRegistryHrvTest`: Direct `DB_VERSION = 2` schema registration parity (Rule 62). (PASS)
* **ViewModel & UX Logic Tests**:
  - `MorningHrvCheckViewModelTest`: Standalone wellness saving, combined HRV + wellness saving, toggle reactivity, and Health Connect sync. (PASS)
* **Design System & Localization Parity**:
  - `TranslationParityTest`: 100% parity across all 9 supported locales for 19 new string keys. (PASS)
  - `ThemeColorCentralizationAuditTest`: 0 hardcoded hex colors outside `Color.kt` (REQ-UI-381). (PASS)
* **Requirement Governance**:
  - `python3 tools/verify_requirement_governance.py`: PASS (living docs & architecture parity verified).

---

## 2. Implemented Architecture & Deliverables

### 2.1 Database Schema Evolution (DB_VERSION = 2)
* **`HrvReadinessDatabaseManager.kt`**: Upgraded SQLite database to `DB_VERSION = 2`.
  - Added columns: `sleep_duration_min`, `sleep_quality_1_5`, `morning_energy_1_5`, `muscle_soreness_1_5`, `context_tags`.
  - Relaxed non-null constraints on `resting_hr_bpm`, `rmssd_ms`, `ln_rmssd`, `hrv_score_100`, `duration_sec` via table-copy migration.
  - Filtered `rmssd_ms IS NOT NULL` in baseline queries to preserve SWC corridor integrity.

### 2.2 Settings Progressive Disclosure
* **`TuningPreferencesDataStore.kt` & `PhysiologyModelsSection.kt`**:
  - Master toggle `enableMorningSleepAndWellness` (default `OFF`).
  - Subordinate checkboxes: `enableSleepDurationAndQuality` (default ON), `enableMorningEnergy` (default ON), `enableMuscleSoreness` (default OFF), `enableContextTags` (default OFF).
  - Synchronized across `AdvancedTuningScreen` and `AdvancedTuningDialog`.

### 2.3 Standalone & In-Measurement UX
* **`MorningHrvCheckScreen.kt` & `MorningHrvCheckViewModel.kt`**:
  - Progressive rendering of `MorningWellnessInputCard`.
  - Standalone "Save Morning Wellness" action in `READY` phase allowing athletes to log without wearing an HR strap.
  - Seamless combined save when completing the 60s waking HRV measurement.

### 2.4 Health Connect Prefill & 9-Language Parity
* **`HealthConnectManager.kt` & `AndroidManifest.xml`**:
  - Added `READ_SLEEP` permission and `getLastNightSleepDurationMinutes()` auto-prefill.
* **Localization**: Full parity across English, German, Spanish, French, Italian, Japanese, Dutch, Polish, and Portuguese.

---

## 3. Acceptance Criteria Verification

| Requirement | Acceptance Criteria Summary | Status | Evidence |
|---|---|---|---|
| `REQ-HRV-038` | Progressive disclosure in Advanced Settings (default OFF) | **Verified** | `PhysiologyModelsSectionTest`, `TuningPreferencesDataStore` |
| `REQ-HRV-039` | SQLite schema evolution to `DB_VERSION = 2` with migration | **Verified** | `HrvReadinessDatabaseManagerTest.databaseUpgrade_fromV1ToV2` |
| `REQ-HRV-040` | Standalone wellness save without HR chest strap | **Verified** | `MorningHrvCheckViewModelTest.testStandaloneWellnessSave` |
| `REQ-HRV-041` | Combined objective HRV + subjective wellness record | **Verified** | `MorningHrvCheckViewModelTest.saveReading`, `MorningHrvCheckScreen` |
| `REQ-HRV-042` | Health Connect sleep prefill & 9-language parity | **Verified** | `HealthConnectManagerTest`, `TranslationParityTest` |
