# Stage 2: Requirement & Test Specification - ATT-7753: Per-bike Front/Rear wheel circumference time-series history & GPS-free dual-wheel iTPMS puncture detection

**Ticket**: [ATT-7753](https://rainerblind.atlassian.net/browse/ATT-7753)  
**Parent Epic**: [ATT-166](https://rainerblind.atlassian.net/browse/ATT-166) (*Filtering to improve sensor values (especially locations)*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `sprint/2026-46.6`  
**Requirement Mapping**: `REQ-SEN-030` (*Per-Bike Front/Rear Wheel Circumference Time-Series History & GPS-Free Dual-Wheel iTPMS Puncture Detection*)  
**Test Spec ID**: `TST-SEN-030`  
**Branch**: `feat/ATT-7753`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-14  

---

## 1. Requirement Specification (REQ-SEN-030)

### 1.1 Problem Statement & Rationale
Single-wheel iTPMS against GNSS requires 1.5–2.0 km of open-sky riding and cannot detect punctures on the uninstrumented wheel or in tunnels/forests. Tracking the revolution ratio $\rho_k = \frac{\Delta N_{rear,k}}{\Delta N_{front,k}} = \frac{C_{front}}{C_{rear}}$ between `FRONT` and `REAR` wheel sensors eliminates GNSS noise 100%, detecting slow punctures within 150–250 m while pinpointing front vs. rear deflation. Persisting converged post-workout circumferences enables longitudinal tire pressure diffusion and wear tracking per bike.

### 1.2 Attributes
- **Priority**: High
- **Type**: Functional
- **Verification Method**: Unit

### 1.3 Functional & Architectural Requirements
1. When active `FRONT` and `REAR` wheel sensors are connected to the current bike, `DualWheelTpmsEstimator` SHALL monitor $\rho_k = \frac{\Delta N_{rear,k}}{\Delta N_{front,k}}$, gated on low yaw-rate ($|\omega_{ENU,z}| < 5^\circ/\text{s}$) and speed $v \ge 3.0\text{ m/s}$. The detector SHALL lock $\rho_{start}$ after $300\text{ m}$ of straight riding.
2. A sliding-window CUSUM detector over 150–250 m SHALL compute deviation $\delta = \frac{\rho_k - \rho_{start}}{\rho_{start}}$. If $\delta \ge +0.008$ ($+0.8\%$), trigger *"Druckverlust am Hinterrad!"*; if $\delta \le -0.008$ ($-0.8\%$), trigger *"Druckverlust am Vorderrad!"*. Turn samples ($|\omega_{ENU,z}| \ge 5^\circ/\text{s}$) SHALL be discarded.
3. `BANALServiceRepository.kt` SHALL map each active wheel speed sensor to an independent `IndirectTpmsEstimator` instance to evaluate multi-day storage pressure loss against GNSS when both tires deflate equally.
4. `EquipmentDbHelper.java` (`DB_VERSION = 5`) SHALL declare table `WheelCircumferenceHistory`. Converged post-workout circumferences SHALL be persisted on session completion.
5. In Bike Equipment details, `WheelCircumferenceHistoryChart` SHALL render Front and Rear circumference curves over time with a *"Reifen gerade aufgepumpt (Baseline setzen)"* reset action (`Rule 84`).

### 1.4 Acceptance Criteria (Given-When-Then)
* *Given* active `FRONT` and `REAR` sensors during a GPS-degraded forest ride ($35\text{ m}$ accuracy), *When* rear tire circumference drops by $\ge 0.8\%$ over $250\text{ m}$, *Then* `DualWheelTpmsAlert.RearPuncture` is triggered within $\le 250\text{ m}$ while sharp corners produce zero false alarms.
* *Given* both tires simultaneously deflate by $0.8\%$ in storage, *When* riding 2 km on open road, *Then* per-sensor GNSS estimators trigger low-pressure pump reminders for both wheels.
* *Given* completed workouts with active wheel sensors, *When* session finalizes, *Then* circumferences are persisted in `WheelCircumferenceHistory` and rendered as a time-series chart per bike.

### 1.5 System Invariants
Zero regression for single-wheel bikes (`REQ-TRK-050`); zero false alarms in hairpins; 100% 9-language localization parity; 100% unit test pass rate.

---

## 2. Test Specification (TST-SEN-030)

### Test Case 1: `testDualWheelRatio_rearAndFrontPunctures_detectedWithin250m` (`TST-SEN-030.1`)
* **Scope**: Unit / Math Engine Test (`DualWheelTpmsEstimatorTest.kt`)
* **Action**: Feed baseline revolutions ($\rho_{start}=1.0$), then simulate $1\%$ deflation on rear, then front.
* **Expected Result**: Rear alert at $\delta \ge +0.008$, front alert at $\delta \le -0.008$; turns ($15^\circ/\text{s}$) rejected with zero alerts.

### Test Case 2: `testMultiWheelTelemetryRouting_independentEstimators` (`TST-SEN-030.2`)
* **Scope**: Unit / Repository Test (`BANALServiceRepositoryMultiWheelTpmsTest.kt`)
* **Expected Result**: Both active wheel sensors instantiate separate GNSS estimators and feed `DualWheelTpmsEstimator`.

### Test Case 3: `testWheelCircumferenceHistory_sqliteUpgradeV4ToV5AndQuery` (`TST-SEN-030.3`)
* **Scope**: Unit / SQLite Test (`WheelCircumferenceHistoryDbTest.kt`)
* **Action**: Run `onUpgrade(db, 4, 5)`; insert history records.
* **Expected Result**: Table created; records queried by `equipmentId` in chronological order.

### Test Case 4: `testCircumferenceChart_renderAndBaselineReset` (`TST-SEN-030.4`)
* **Scope**: Unit / UI Test (`WheelCircumferenceHistoryChartTest.kt`)
* **Expected Result**: Dual curves rendered without text clipping (`Rule 84`); reset baseline updates repository.

### Test Case 5: 9-Language Localization & Format Parity (`TST-SEN-030.5`)
* **Scope**: Localization Parity Test (`TranslationParityTest.kt`)
* **Expected Result**: 100% parity across all 9 locales for all new TPMS alert and chart strings.

### Test Case 6: Tier 1 Targeted Regression Suite (`TST-SEN-030.6`)
* **Command**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.tpms.*" --tests "com.atrainingtracker.trainingtracker.database.Equipment*"`
* **Expected Result**: 100% pass rate.

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-SEN-030.1` | Unit (Math) | `DualWheelTpmsEstimator` | `REQ-SEN-030.1` | Specified |
| `TST-SEN-030.2` | Unit (Repo) | `BANALServiceRepository` | `REQ-SEN-030.2` | Specified |
| `TST-SEN-030.3` | Unit (DB) | `EquipmentDbHelper.onUpgrade` | `REQ-SEN-030.3` | Specified |
| `TST-SEN-030.4` | Unit (UI) | `WheelCircumferenceHistoryChart` | `REQ-SEN-030.4` | Specified |
| `TST-SEN-030.5` | Localization | `TranslationParityTest` | `REQ-SEN-030.5`, `REQ-LOC-001` | Specified |
| `TST-SEN-030.6` | Regression | Targeted Gradle Suite | `REQ-PRO-001` | Specified |
