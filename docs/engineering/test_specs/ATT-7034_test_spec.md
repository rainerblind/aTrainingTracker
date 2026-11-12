# Stage 2: Requirement & Test Specification - ATT-7034: Track Morning Sleep & Minimal Wellness with or without HR strap

**Ticket**: [ATT-7034](https://rainerblind.atlassian.net/browse/ATT-7034) | **Parent Epic**: [ATT-3539](https://rainerblind.atlassian.net/browse/ATT-3539) (*HRV Autonomic Metrics*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.3` | **Branch**: `subagent-ATT-7034---ATT-3539-Flash-Agent-self-6e4b15d7`  
**Requirement Mapping**: `REQ-HRV-038`..`REQ-HRV-042` | **Test Spec ID**: `TST-HRV-038`..`TST-HRV-042`  
**Author**: AI Agent 1 (Implementer, `subagent_flash`) | **Date**: 2026-11-12  

---

## 1. Requirement Specification (REQ-HRV-038..042)

### 1.1 Problem Statement & Rationale
Morning readiness evaluation is significantly more actionable when objective autonomic metrics (`rMSSD`, `ln(rMSSD)`) are complemented by subjective sleep and wellness markers. Athletes currently wait idly during the 60s/120s countdown and cannot log morning sleep/feel on non-strap days or without an HR chest strap. The system must provide configurable, progressive disclosure in Advanced Settings (default `OFF`), standalone (no-strap) and in-countdown wellness logging on `MorningHrvCheckScreen`, and seamless SQLite schema evolution to `DB_VERSION = 2` without corrupting historical baseline math.

### 1.2 Functional & Architectural Requirements
* **REQ-HRV-038** (*Settings Progressive Disclosure*): `SettingsDataStore.kt` & `PhysiologyModelsSection.kt` SHALL provide master toggle `enableMorningSleepAndWellness: Boolean` (default `false`). When `true`, expose subordinate checkboxes: `enableSleepDurationAndQuality` (default `true`), `enableMorningEnergy` (default `true`), `enableMuscleSoreness` (default `false`), and `enableContextTags` (default `false`).
* **REQ-HRV-039** (*SQLite Schema Evolution & Parity*): `HrvReadinessDbHelper` SHALL increment to `DB_VERSION = 2`. `hrv_readiness_entries` SHALL allow `resting_hr_bpm`, `rmssd_ms`, `ln_rmssd`, `hrv_score_100`, and `duration_sec` to be nullable for standalone wellness entries. Add nullable columns: `sleep_duration_min` (`INTEGER`), `sleep_quality_1_5` (`INTEGER`), `morning_energy_1_5` (`INTEGER`), `muscle_soreness_1_5` (`INTEGER`), and `context_tags` (`TEXT`). Enforce `onCreate` and `onUpgrade` parity (Rule 64) and `DatabaseSchemaRegistry` parity (Rule 62).
* **REQ-HRV-040** (*Standalone No-Strap Logging*): When master toggle is `ON`, `MorningHrvCheckScreen.kt` SHALL allow entering Sleep Duration/Quality and Morning Energy and saving directly (`Save Morning Wellness`) without connecting an HR sensor. Standalone entries SHALL record `(date_iso, posture)` with null HRV metrics and participate in daily deduplication without corrupting SWC baseline corridors (`WHERE rmssd_ms IS NOT NULL`).
* **REQ-HRV-041** (*In-Measurement Combined Logging*): When master toggle is `ON`, wellness inputs SHALL remain interactive before, during, and after the countdown timer. When an HR measurement completes, saving SHALL persist both objective HRV metrics and subjective wellness values into the same daily record.
* **REQ-HRV-042** (*Health Connect Auto-Prefill & 9-Language Parity*): `HealthConnectManager.kt` SHALL optionally prefill last night's `Sleep Duration` from `SleepSessionRecord` if granted. All user-facing strings SHALL maintain 100% parity across all 9 supported locales (values, de, es, fr, it, ja, nl, pl, pt).

### 1.3 Acceptance Criteria (Given-When-Then) & System Invariants
* *Given* default installation, *When* viewing settings or morning screen, *Then* sleep/wellness is `OFF` and screen layout is 100% unchanged.
* *Given* master toggle `ON` and no HR strap connected, *When* user inputs sleep/energy and taps `Save`, *Then* record is saved with null HRV metrics and verified via `getPrimaryEntryForDate`.
* *Given* master toggle `ON` and HR strap measuring, *When* user inputs wellness and saves on completion, *Then* unified record with both HRV and wellness metrics is stored.
* *Invariants*: Rule 62 (`DatabaseSchemaRegistry`), Rule 64 (`onCreate`/`onUpgrade` parity), Rule 20 (symmetric DTO mapping), zero schema regression on existing HRV baselines.

---

## 2. Test Specification (TST-HRV-038..042)

### Test Case 1: Advanced Settings & DataStore State (`TST-HRV-038`)
* **Scope**: Unit Test (`SettingsDataStoreTest.kt`, `PhysiologyModelsSectionTest.kt`)
* **Assertion**: Verify default `OFF`, subordinate toggle visibility gating, and preference persistence round-trip.

### Test Case 2: Database Migration V1->V2 & Symmetric Mapping (`TST-HRV-039`)
* **Scope**: Unit Test (`HrvReadinessDatabaseManagerTest.kt`, `DatabaseSchemaRegistryTest.kt`)
* **Assertion**: Verify `onUpgrade` from V1 preserves legacy records and adds 5 nullable columns; `onCreate` equals `onUpgrade` schema; `DatabaseSchemaRegistry.SUPPORTED_DB_VERSIONS` matches `DB_VERSION = 2`; `fromCursor` and `fromCursorBatch` map all fields symmetrically per Rule 20.

### Test Case 3: Standalone No-Strap Wellness Logging (`TST-HRV-040`)
* **Scope**: Unit Test (`MorningHrvCheckViewModelTest.kt`)
* **Assertion**: Verify saving with no HR connection inserts entry with null `rmssdMs`; SWC baseline query excludes standalone entries.

### Test Case 4: Combined In-Measurement Logging (`TST-HRV-041`)
* **Scope**: Unit Test (`MorningHrvCheckViewModelTest.kt`)
* **Assertion**: Verify entries saved after countdown contain both calculated HRV metrics (`rmssdMs`, `restingHrBpm`) and input wellness fields.

### Test Case 5: Health Connect Prefill, 9-Language Parity & Regression (`TST-HRV-042`)
* **Scope**: Integration & Contract Test (`HealthConnectManagerTest.kt`, `TranslationParityTest.kt`)
* **Assertion**: Verify graceful fallback when Health Connect permission absent; 100% string parity across all 9 locales; Tier 1 package test suite PASS (`./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.database.hrv.*" --tests "com.atrainingtracker.trainingtracker.ui.hrv.*"`).

---

## 3. Traceability Matrix

| Test Case | Scope | Target Class / File | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-HRV-038` | Unit | `SettingsDataStore`, `PhysiologyModelsSection` | `REQ-HRV-038` | Specified |
| `TST-HRV-039` | Database / Contract | `HrvReadinessDbHelper`, `HrvReadinessDatabaseManager` | `REQ-HRV-039` | Specified |
| `TST-HRV-040` | Unit / ViewModel | `MorningHrvCheckViewModel` | `REQ-HRV-040` | Specified |
| `TST-HRV-041` | Unit / ViewModel | `MorningHrvCheckViewModel`, `MorningHrvCheckScreen` | `REQ-HRV-041` | Specified |
| `TST-HRV-042` | Localization / Tier 1 | `HealthConnectManager`, `TranslationParityTest` | `REQ-HRV-042`, `REQ-UI-106` | Specified |
