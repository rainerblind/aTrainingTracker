# Stage 2: Requirement & Test Specification - ATT-7532: Configurable Morning HRV Check on Start Screen

**Ticket**: [ATT-7532](https://rainerblind.atlassian.net/browse/ATT-7532)  
**Parent Epic**: [ATT-3539](https://rainerblind.atlassian.net/browse/ATT-3539) (*Heart Rate Variability (HRV)*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `Sprint 2026-46.5`  
**Requirement Mapping**: `REQ-UI-525` (*Configurable Morning HRV Check Launch Button on Start Tracking Screen*)  
**Test Spec ID**: `TST-UI-525` (`TST-UI-525.1`–`TST-UI-525.6`) | **Branch**: `feature/ATT-7532`  
**Author**: AI Agent 2 (Implementer, `subagent_flash`) | **Date**: 2026-11-12  

---

## 1. Requirement Specification (REQ-UI-525)

### 1.1 Problem Statement & Rationale
Following `REQ-UI-513`, standalone morning HRV check was removed from the drawer, leaving athletes without a quick 1-tap entry point on waking. A contextual launcher on `ControlTrackingScreen` in `TrackingMode.READY` allows athletes to launch their morning check immediately, while remaining hidden during tracking, for users without HR sensors, after today's reading, or past morning cutoff hours.

### 1.2 Attributes
- **Priority**: Medium | **Type**: Functional | **Verification Method**: Unit

### 1.3 Functional & Architectural Requirements
1. `TuningPreferencesDataStore.kt` & `TuningConfig.kt` SHALL provide `showMorningCheckOnStartScreen: Boolean` (default `true`) and `morningCheckCutoffHour: Int` (default `11`, range 6..15), configurable in `PhysiologyModelsSection.kt` (`REQ-UI-525.1`).
2. `DevicesDatabaseManager.java` & `DeviceDataRepository.kt` SHALL provide `hasPairedHeartRateDevices(): Boolean` querying `PAIRED > 0 AND DEVICE_TYPE = 'HRM'` (`REQ-UI-525.2`).
3. `HrvReadinessDatabaseManager.kt` & `HrvReadinessRepository.kt` SHALL provide `hasCompletedMorningCheck(dateIso: String): Boolean` (`REQ-UI-525.3`).
4. `ControlTrackingViewModel.kt` SHALL expose `isMorningHrvCheckVisible: StateFlow<Boolean>` evaluating to true iff `TrackingMode == READY`, pref enabled, HRM paired, hour < cutoff, and no check completed today. When visible, `ControlTrackingScreen.kt` renders a CTA button navigating to `NavRoutes.MORNING_HRV`, auto-hiding once recorded (`REQ-UI-525.4`).
5. All newly added strings SHALL maintain 100% parity across all 9 supported locales (`REQ-UI-525.5`).

### 1.4 Acceptance Criteria (Given-When-Then)
* **AC1 (Morning Check CTA Visibility)**:
  * *Given* an athlete with a paired HRM sensor, `showMorningCheckOnStartScreen == true`, current hour < cutoff, and no check completed today,
  * *When* `ControlTrackingScreen` is viewed in `TrackingMode.READY`,
  * *Then* the "Morgendlicher Check" CTA button is visible and tapping it navigates to `NavRoutes.MORNING_HRV`.
* **AC2 (Auto-Hide on Completion or Invalidation)**:
  * *Given* a morning check is saved, or hour >= cutoff, or workout starts (`RECORDING`), or no HRM paired,
  * *When* `ControlTrackingScreen` renders or resumes,
  * *Then* the CTA button is hidden.

### 1.5 System Invariants
- Zero SQLite migrations on `devices.db` or `hrv_readiness.db`.
- Zero alterations to morning HRV calculation algorithms, SWC baseline corridors, or workout recording lifecycle.

---

## 2. Test Specification (TST-UI-525)

### Test Case 1: Tuning Preferences DataStore & Config Verification (`TST-UI-525.1`)
* **Scope**: Unit (`TuningConfigTest.kt`)
* **Verification**: Verify default values (`showMorningCheckOnStartScreen=true`, `morningCheckCutoffHour=11`), persistence, and hour clamping (6..15).

### Test Case 2: Paired HRM Sensor Query Verification (`TST-UI-525.2`)
* **Scope**: Unit (`DevicesDatabaseManagerTest.kt`)
* **Verification**: Verify `hasPairedHeartRateDevices()` returns false when empty or only non-HRM devices exist; returns true when `DeviceType.HRM` with `PAIRED > 0` exists.

### Test Case 3: Today's Morning Check Completion Verification (`TST-UI-525.3`)
* **Scope**: Unit (`HrvReadinessDatabaseManagerTest.kt`)
* **Verification**: Verify `hasCompletedMorningCheck(dateIso)` returns false when absent, true when entry exists for date.

### Test Case 4: ControlTrackingViewModel Reactive Visibility Rules (`TST-UI-525.4`)
* **Scope**: Unit (`ControlTrackingViewModelTest.kt`)
* **Verification**: Test all permutations of mode (READY vs RECORDING), preference toggle, paired sensor, hour cutoff, and today completion.

### Test Case 5: 9-Language Localization Parity Verification (`TST-UI-525.5`)
* **Scope**: Unit (`TranslationParityTest.kt`)
* **Verification**: Assert zero missing keys and format parity across all 9 locales.

### Test Case 6: Tier 1 Targeted Unit Test Regression Suite (`TST-UI-525.6`)
* **Scope**: Integration / Unit
* **Command**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.settings.*" --tests "com.atrainingtracker.banalservice.database.*" --tests "com.atrainingtracker.trainingtracker.database.hrv.*" --tests "com.atrainingtracker.trainingtracker.ui.tracking.controltracking.*"`

---

## 3. Traceability Matrix

| Test Case | Scope | Target File | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-UI-525.1` | Unit | `TuningConfigTest.kt` | `REQ-UI-525.1` | Specified |
| `TST-UI-525.2` | Unit | `DevicesDatabaseManagerTest.kt` | `REQ-UI-525.2` | Specified |
| `TST-UI-525.3` | Unit | `HrvReadinessDatabaseManagerTest.kt` | `REQ-UI-525.3` | Specified |
| `TST-UI-525.4` | Unit | `ControlTrackingViewModelTest.kt` | `REQ-UI-525.4` | Specified |
| `TST-UI-525.5` | Unit | `TranslationParityTest.kt` | `REQ-UI-525.5` | Specified |
| `TST-UI-525.6` | Tier 1 Regr | Targeted test suites | `REQ-PRO-001` | Specified |
