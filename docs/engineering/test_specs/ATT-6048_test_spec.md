# Stage 2: Requirement & Test Specification - ATT-6048: Complete `saveTuningConfig` Auto-Pause Persistence, `BoundedPref` Descriptors & Centralized Zone Defaults

**Ticket**: [ATT-6048](https://issue-tracker/ATT-6048)  
**Sub-task**: Pending creation (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-6033](https://issue-tracker/ATT-6033) (*Code Architecture, Deduplication & Clean Code Refactoring (OOP & FP)*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-11-20_03_Nacht` (Sprint 467)  
**Requirement Mapping**: `REQ-SET-121` (`REQ-SET-121.1`..`REQ-SET-121.4` — refines `REQ-SET-073`, `REQ-TRK-043`, `REQ-TRK-044`)  
**Test Spec ID**: `TST-SET-123` (`TST-SET-123.1`..`TST-SET-123.3`)  
**Branch**: `feature/ATT-6048`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-20  

---

## 1. Requirement Specification (`REQ-SET-121`)

### 1.1 Problem Statement & Rationale
* `TuningPreferencesDataStore.saveTuningConfig(config)` (`:1478-1731`) omits 4 Auto-Pause keys (`KEY_START_IN_AUTO_PAUSE_ENABLED`, `KEY_AUTO_PAUSE_BIKE_RESUME_DEBOUNCE_SEC`, `KEY_AUTO_PAUSE_RUN_RESUME_DEBOUNCE_SEC`, `KEY_AUTO_PAUSE_OTHER_RESUME_DEBOUNCE_SEC`) due to manual 3-place `.coerceIn(...)` duplication, while `SettingsDataStore.kt` hardcodes `HR_RUN`, `HR_BIKE`, and `PWR_BIKE` default zone boundaries across 7 sites.

### 1.2 Attributes
- **Priority**: Low | **Type**: Functional | **Verification Method**: Unit

### 1.3 Functional & Architectural Requirements
1. **`saveTuningConfig` Full Persistence (`REQ-SET-121.1`)**: `saveTuningConfig(config)` SHALL clamp and persist all 4 missing Auto-Pause keys (`KEY_START_IN_AUTO_PAUSE_ENABLED`, `KEY_AUTO_PAUSE_{BIKE,RUN,OTHER}_RESUME_DEBOUNCE_SEC`), preserving exact round-trip fidelity across all non-derived, non-hardcoded fields of `TuningConfig` (104 constructor fields total; excluding 7 `REQ-MAP-050` hardcoded `mapFollowMe*` fields, 2 derived legacy `autoPause*` fields, and matching `radarAutoLightControl`/`radarSmartLightAutomation` derivation from `radarLightControlMode`).
2. **`BoundedPref` Descriptors & Dependent Clamps (`REQ-SET-121.2`)**: Standard numeric range checks across `mapPreferencesToConfig`, `mapAutoPausePreferences`, `saveTuningConfig`, and `update*`/`set*` setters SHALL delegate to declarative `BoundedPref` descriptors while preserving identical behavior for dependent clamps (`mediumDimFactor >= clampedFullDim`, `slopeSteepThreshold >= clampedSlopeFlat`, `dfaA1 T1/T2` `MIN_DFA_A1_THRESHOLD_GAP`), `navigationCueDismissDurationSec == 0 -> DEFAULT`, legacy fallbacks/mirrors, `TrainingApplication` cluster sync, and existing `TrackerService.invalidateAutoPauseCache()` call sites.
3. **Centralized Zone Defaults (`REQ-SET-121.3`)**: `SettingsDataStore.kt` SHALL centralize `HR_RUN` (`132, 145, 158, 171`), `HR_BIKE` (`127, 140, 153, 166`), and `PWR_BIKE` (`149, 203, 243, 284, 324, 405`) default constants across all 7 sites (`SettingsSnapshot.DEFAULT`, `getDefaultValue`, `calculateProportionalPowerDefaults`, `getZoneMaxFlow`, `mapSettingsSnapshot`, `checkAndMigratePowerZones`, `resetToDefaults`).
4. **Architectural & Localization Invariants (`REQ-SET-121.4`)**: `TuningPreferencesDataStore.kt` MUST remain `<= 2,410` LoC with a non-public constructor; `SettingsDataStore.kt` MUST introduce zero `runBlocking` calls; 9-locale parity N/A (`0` UI string edits).

### 1.4 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (`TST-SET-123.1`)**: *Given* a `TuningConfig` where all non-derived, non-hardcoded fields (including `startInAutoPauseEnabled` and `autoPause*ResumeDebounceSec`) differ from defaults (with derived legacy/radar fields consistent with source fields), *When* `saveTuningConfig(config)` runs, *Then* `tuningConfigFlow.first()` emits an equal `TuningConfig` across all reflected properties.
* **Criterion 2 (`TST-SET-123.2`)**: *Given* out-of-range values and dependent inputs (`mediumDim < fullDim`, `slopeSteep < slopeFlat`, `dfaA1 T1/T2` gap violation, `navigationCueDismissDurationSec == 0`), *When* read or written, *Then* `BoundedPref` and dependent clamps yield identical clamped values, legacy mirrors, `TrainingApplication` sync, and `invalidateAutoPauseCache()` calls.
* **Criterion 3 (`TST-SET-123.3`)**: *Given* `SettingsDataStore` default zone reads, power-zone migration, and `resetToDefaults`, *When* evaluated, *Then* `HR_RUN`, `HR_BIKE`, and `PWR_BIKE` boundaries match the centralized constants and `TuningPreferencesDataStore.kt <= 2,410` LoC / zero-`runBlocking` contracts pass.

---

## 2. Test Specification (`TST-SET-123`)

### Test Case 1: Reflection-Assisted `TuningConfig` Round-Trip (`TST-SET-123.1`)
* **Scope**: Unit Test (`app/src/test/java/com/atrainingtracker/trainingtracker/settings/TuningPreferencesDataStoreRoundTripTest.kt`)
* **Preconditions**: Isolated `TuningPreferencesDataStore` backed by `InMemoryPreferencesDataStore` / temp `DataStore` + `testScope`.
* **Action**: Save a fully non-default `TuningConfig` via `saveTuningConfig(config)` and collect `tuningConfigFlow.first()`.
* **Expected Result**: All 104 `TuningConfig` properties match via Kotlin reflection (with `mapFollowMe*` at `REQ-MAP-050` defaults, legacy auto-pause equal to bike values, and radar legacy booleans derived from `radarLightControlMode`).

### Test Case 2: `BoundedPref`, Dependent Clamps, Legacy Mirrors & Zone Defaults (`TST-SET-123.2`)
* **Scope**: Unit Test (`TuningPreferencesDataStoreRoundTripTest.kt`, `SettingsDataStoreZoneAtomicTest.kt`)
* **Preconditions**: Out-of-range prefs/inputs (`mediumDim < fullDim`, `slopeSteep < slopeFlat`, `dfaA1` gap `< 0.05f`, `navigationCueDismiss = 0`, legacy auto-pause/radar keys) and default `SettingsDataStore`.
* **Action**: Read via `mapPreferencesToConfig`/`mapAutoPausePreferences`, write via `saveTuningConfig`/`update*`, and evaluate `SettingsSnapshot.DEFAULT`, `getDefaultValue`, `checkAndMigratePowerZones`, and `resetToDefaults`.
* **Expected Result**: Identical clamping, `0 -> DEFAULT`, legacy mirrors, and centralized `HR_RUN`/`HR_BIKE`/`PWR_BIKE` zone boundaries.

### Test Case 3: Targeted Settings & Contract Regression (`TST-SET-123.3`)
* **Scope**: Targeted Unit & Contract Suite
* **Command**: `./gradlew :app:testDebugUnitTest --rerun --tests "com.atrainingtracker.trainingtracker.settings.*" --tests "com.atrainingtracker.trainingtracker.build.ArchitectureConformanceContractTest"`
* **Expected Result**: 100% pass (`TuningPreferencesDataStore.kt <= 2,410` LoC, non-public constructor, zero `runBlocking` violations).

---

## 3. Traceability Matrix

| Requirement ID | Description | Test Case ID | Verification Method |
| :--- | :--- | :--- | :--- |
| `REQ-SET-121.1` | Full `saveTuningConfig` persistence incl. 4 Auto-Pause keys | `TST-SET-123.1` | Unit Test (Reflection Round-Trip) |
| `REQ-SET-121.2` | `BoundedPref` descriptors & dependent clamp/mirror parity | `TST-SET-123.2` | Unit Test (Characterization) |
| `REQ-SET-121.3` | Centralized `HR_RUN`/`HR_BIKE`/`PWR_BIKE` zone defaults | `TST-SET-123.2` | Unit Test |
| `REQ-SET-121.4` | `<= 2,410` LoC, non-public ctor, zero `runBlocking` | `TST-SET-123.3` | Contract Test |
