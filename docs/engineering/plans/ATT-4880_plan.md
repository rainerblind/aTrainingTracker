# Stage 3: Implementation Plan - ATT-4880: Indirect Tire Pressure Monitoring (iTPMS)

**Ticket**: [ATT-4880](https://rainerblind.atlassian.net/browse/ATT-4880)  
**Sub-task**: Pending (`[Impl-Plan]`)  
**Parent Epic**: [ATT-166](https://rainerblind.atlassian.net/browse/ATT-166) (*Filtering to improve sensor values*)  
**Target Release**: `V4.9.41`  
**Active Sprint**: `2026-44.2`  
**Requirement Mapping**: `REQ-TRK-050` (`REQ-TRK-050.1`..`REQ-TRK-050.5`)  
**Test Mapping**: `TST-TRK-050` (`TST-TRK-050.1`..`TST-TRK-050.5`)  
**Branch**: `feature/ATT-4880`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-01  

---

## 1. Problem Description & Background
Under rider load, tire deflation ($4.5 \rightarrow 1.8\text{ bar}$) increases vertical tire deflection $\delta(p)$ from $\sim 3\text{ mm}$ to $\sim 14\text{ mm}$, shortening effective rolling circumference $C_{\text{eff}} = \Delta s_{3D} / \Delta N_{\text{wheel}}$ by $1.0\%\text{--}2.2\%$. By fusing straight-line, good-accuracy GNSS distance with ANT+/BLE wheel speed revolutions, we implement an indirect tire pressure monitoring system (iTPMS): locking a ride-start baseline $C_{\text{start}}$ to surface multi-day low-pressure reminders ($C_{\text{start}} / C_{\text{baseline}} \le 1 - 0.006$), and running a sliding-window one-sided CUSUM change-point detector to warn against in-ride slow punctures ($C_{\text{short}} / C_{\text{start}} \le 1 - 0.010$).

---

## 2. Traceability & Requirements Mapping
* **REQ-TRK-050.1**: Straight-line ($|\Delta\text{bearing}/\Delta t| < 3^\circ/\text{s}$), accuracy $\le 6\text{ m}$, speed $\ge 4\text{ m/s}$, and 3D-slope ($\sqrt{\Delta s_{2D}^2 + \Delta h^2}$) gating $\rightarrow$ `TST-TRK-050.1`.
* **REQ-TRK-050.2**: Ride-start baseline $C_{\text{start}}$ lock ($2.0\text{ km}$ gated) and low pressure reminder ($C_{\text{start}} / C_{\text{baseline}} \le 1 - 0.006$) $\rightarrow$ `TST-TRK-050.2`.
* **REQ-TRK-050.3**: Live rolling CUSUM slow puncture alert ($C_{\text{short}} / C_{\text{start}} \le 1 - 0.010$) with cornering immunity $\rightarrow$ `TST-TRK-050.3`.
* **REQ-TRK-050.4**: `Devices.db` v8 migration (`baselineCircumference REAL DEFAULT 0.0`), Rule 62 schema registry parity, and baseline reset UI action $\rightarrow$ `TST-TRK-050.4`.
* **REQ-TRK-050.5**: 9-language localization parity and semantic alert container styling (amber warning, red critical per Design Guidelines Sec 5.4) $\rightarrow$ `TST-TRK-050.5`.

---

## 3. System Invariants & Preserved Behavior
1. **Raw Telemetry Invariance**: Wheel speed sensor distance accumulation (`mDistanceSensor.accumulate(...)`), raw speed, and sensor priorities remain 100% unaltered.
2. **Database Integrity (Rules 20, 62, 64)**: `DevicesDbHelper.DB_VERSION = 8` has identical `onCreate` and `onUpgrade` schemas; `DatabaseSchemaRegistry` directly references the constant.
3. **Cornering & Multipath False-Positive Immunity**: Gating strictly drops samples with $|\Delta\text{bearing}/\Delta t| \ge 3^\circ/\text{s}$, accuracy $> 6\text{ m}$, or speed $< 4\text{ m/s}$.
4. **Code Lock Guard (Rule 4)**: No production code edits before Gate 3 passes.

---

## 4. Proposed Architectural Changes
* **Database & Persistence (`DevicesDatabaseManager.java`, `DatabaseSchemaRegistry.kt`)**: Increment `DevicesDbHelper.DB_VERSION` to 8; add `baselineCircumference REAL DEFAULT 0.0` in `onCreate()` and `onUpgrade()`; expose `getBaselineCircumference(id)` / `setBaselineCircumference(id, value)` / `resetBaselineCircumference(id)`. Update `DatabaseSchemaRegistry` to reference `DevicesDbHelper.DB_VERSION`.
* **Estimator Core (`IndirectTpmsEstimator.kt`)**: Pure domain component ingesting 1-Hz GPS $(\text{lat}, \text{lon}, \text{bearing}, \text{acc}, \text{speed})$, elevation $\Delta h$, and wheel revolutions $\Delta N_{\text{wheel}}$. Implements dual-timescale accumulator ($C_{\text{start}}$ lock after $2\text{ km}$ gated; rolling $2\text{ km}$ window $C_{\text{short}}$) and one-sided CUSUM detector.
* **Alert Events & Repositories (`WorkoutNavigationEvents.kt`, `BANALServiceRepository.kt`, `TrackingViewModel.kt`)**: Add `TpmsAlert` (with `Severity.WARNING` and `Severity.CRITICAL`), collected in ViewModels and exposed to cockpit HUDs.
* **UI Consistency (Rule 23 & Design Guidelines Sec 5.4)**:
  * Closest reference: `NonRecordingStatusBanner` in `SensorGridScreen.kt` and `LowBatteryAlert` snackbar in `TrackingTabsScreen.kt`.
  * Theme tokens: `RoundedCornerShape(16.dp)`, `LightWarningContainer`/`DarkWarningContainer` for low pressure reminder, `errorContainer`/`onErrorContainer` for slow puncture alert.
  * Device Edit: Add "Reset Tire Pressure Baseline (Just Pumped Tires)" action in `WheelCircumferenceSelector` (`EditDeviceDialog.kt`).

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)
* **Step 1: Database Migration & Schema Registry Parity**:
  * Files: `DevicesDatabaseManager.java`, `DatabaseSchemaRegistry.kt`.
  * Upgrade `DB_VERSION` to 8 with `baselineCircumference REAL DEFAULT 0.0`.
* **Step 2: Core Estimator & CUSUM Engine**:
  * Files: `com.atrainingtracker.banalservice.tpms.IndirectTpmsEstimator.kt`.
  * Implement gating ($|\Delta\text{bearing}/\Delta t| < 3^\circ/\text{s}$, $\text{acc} \le 6\text{ m}$, $\text{speed} \ge 4\text{ m/s}$, 3D slope $\sqrt{\Delta s_{2D}^2 + \Delta h^2}$), $C_{\text{start}}$ lock, low-pressure check, and CUSUM change-point detector.
* **Step 3: Alert Events & BANAL / Tracking Integration**:
  * Files: `WorkoutNavigationEvents.kt`, `BANALServiceRepository.kt`, `TrackingViewModel.kt`.
  * Wire 1-Hz sample updates from active GPS and wheel speed sensor; publish alerts to `WorkoutNavigationEvents.tpmsAlert`.
* **Step 4: UI Banners & Device Configuration Action**:
  * Files: `SensorGridScreen.kt`, `TrackingTabsScreen.kt`, `EditDeviceDialog.kt`, `EditDeviceViewModel.kt`, `DeviceData.kt`, `DeviceDataRepository.kt`.
  * Add in-ride TPMS HUD banner and "Reset Tire Pressure Baseline" action in wheel sensor config.
* **Step 5: 9-Language Localization Parity**:
  * Files: `app/src/main/res/values*/strings.xml`, `strings_devices.xml`.
  * Add `devices_reset_tire_pressure_baseline`, `itpms_slow_puncture_alert_title`, `itpms_slow_puncture_alert_message`, `itpms_low_pressure_reminder_title`, `itpms_low_pressure_reminder_message` across EN, DE, ES, FR, IT, JA, NL, PL, PT.
* **Step 6: Unit & Contract Tests**:
  * Commands:
    `./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.tpms.*" --tests "com.atrainingtracker.banalservice.database.*" --tests "com.atrainingtracker.banalservice.ui.devices.*" --tests "com.atrainingtracker.trainingtracker.localization.TranslationParityTest" --tests "com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest"`

---

## 6. Verification & Rollback Plan
* **Verification**: Tier 1 targeted unit and contract tests (~15s) and `python3 tools/verify_requirement_governance.py`.
* **Rollback**: Clean git revert of atomic commit on isolated `feature/ATT-4880` branch.
