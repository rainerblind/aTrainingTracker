# Stage 3: Implementation Plan - ATT-7753: Per-bike Front/Rear wheel circumference time-series history & GPS-free dual-wheel iTPMS puncture detection

**Ticket**: [ATT-7753](https://rainerblind.atlassian.net/browse/ATT-7753)  
**Parent Epic**: [ATT-166](https://rainerblind.atlassian.net/browse/ATT-166) (*Filtering to improve sensor values (especially locations)*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `sprint/2026-46.6`  
**Requirement Mapping**: `REQ-SEN-030`  
**Test Mapping**: `TST-SEN-030`  
**Branch**: `feat/ATT-7753`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-14  

---

## 1. Problem Description & Background
Single-wheel iTPMS against GNSS requires 1.5–2.0 km of open-sky riding and cannot detect punctures on the unmonitored wheel or in tunnels/forests. Tracking the revolution ratio $\rho_k = \frac{\Delta N_{rear,k}}{\Delta N_{front,k}} = \frac{C_{front}}{C_{rear}}$ between `FRONT` and `REAR` wheel sensors eliminates GNSS noise 100%, detecting slow punctures within 150–250 m while pinpointing front vs. rear deflation. Persisting converged post-workout circumferences enables longitudinal tire pressure diffusion and wear tracking per bike.

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-SEN-030` (*Per-Bike Front/Rear Wheel Circumference Time-Series History & GPS-Free Dual-Wheel iTPMS Puncture Detection*)
* **Test Mapping**: `TST-SEN-030` (`TST-SEN-030.1`..`TST-SEN-030.6`)

---

## 3. System Invariants & Preserved Behavior
1. **Single-Wheel Fallback Invariance**: Single-wheel bikes maintain exact baseline behavior of `REQ-TRK-050` with zero regression.
2. **False Positive Immunity**: Hairpin cornering ($|\omega_{ENU,z}| \ge 5^\circ/\text{s}$) produces 0.0% false puncture alerts.
3. **Database Integrity**: Non-destructive SQLite migration from v4 to v5; existing tables and links remain 100% intact.
4. **Visual Layout Invariance**: Chart and reset actions satisfy `Rule 84` visual inspection with zero text clipping.

---

## 4. Proposed Architectural Changes
* **Math Layer (`DualWheelTpmsEstimator.kt`)**: Implements revolution ratio tracking $\rho_k = \Delta N_{rear} / \Delta N_{front}$. Gated on $|\omega_{ENU,z}| < 5^\circ/\text{s}$ and $v \ge 3.0\text{ m/s}$. CUSUM change detector over 150–250 m; emits `RearPuncture` ($\delta \ge +0.8\%$) or `FrontPuncture` ($\delta \le -0.8\%$).
* **Repository Layer (`BANALServiceRepository.kt`)**: Maintains `Map<Long, IndirectTpmsEstimator>` for multi-sensor GNSS evaluations and dispatches active `FRONT` + `REAR` pairs to `DualWheelTpmsEstimator`.
* **Database Layer (`EquipmentDbHelper.java`)**: Bump `DB_VERSION = 5`. Create table `WheelCircumferenceHistory`. Persist post-workout converged records.
* **UI Layer (`WheelCircumferenceHistoryChart.kt`)**: Compose canvas chart rendering Front and Rear curves over time with a *"Reifen gerade aufgepumpt (Baseline setzen)"* button.
* **UI Consistency (`docs/design_guidelines.md`)**:
  * Reference Screen: `EquipmentTabsScreen.kt`, `EditDeviceDialog.kt`.
  * Tokens: `MaterialTheme.colorScheme.primary` (Front), `secondary` (Rear), `surfaceContainer`.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Core Dual-Wheel Ratio Math Engine (`REQ-SEN-030.1`, `TST-SEN-030.1`)
* Files: `app/src/main/java/com/atrainingtracker/banalservice/tpms/DualWheelTpmsEstimator.kt`
* Changes: Implement ratio calculation, 300 m baseline locking, yaw-rate gating, and 150–250 m CUSUM detector.

### Step 2: Multi-Sensor Telemetry Dispatcher (`REQ-SEN-030.2`, `TST-SEN-030.2`)
* Files: `app/src/main/java/com/atrainingtracker/trainingtracker/repositories/BANALServiceRepository.kt`
* Changes: Map active wheel sensors to per-device estimators; pipe `FRONT` + `REAR` telemetry into `DualWheelTpmsEstimator`; trigger wheel-specific `WorkoutNavigationEvents`.

### Step 3: SQLite Circumference History Schema & Repository (`REQ-SEN-030.3`, `TST-SEN-030.3`)
* Files: `EquipmentDbHelper.java`, `EquipmentRepository.kt`, `EquipmentViewModel.kt`
* Changes: Bump `DB_VERSION = 5`. Create `WheelCircumferenceHistory` table. Add persistence and query methods.

### Step 4: UI Time-Series Chart & Baseline Reset (`REQ-SEN-030.4`, `TST-SEN-030.4`)
* Files: `WheelCircumferenceHistoryChart.kt`, `EquipmentTabsScreen.kt` (or `EditEquipmentDialog.kt`)
* Changes: Render Front vs. Rear time-series chart with "Reifen aufgepumpt" baseline reset action.

### Step 5: 9-Language Localization & Strings (`REQ-SEN-030.5`, `TST-SEN-030.5`)
* Files: `app/src/main/res/values*/strings_equipment.xml`, `strings.xml`
* Changes: Add localized puncture alerts and history chart strings across all 9 locales.

### Step 6: Targeted Unit & Integration Tests (`TST-SEN-030.6`)
* Run: `python3 tools/parallel_sprint_lock.py -- ./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.tpms.*" --tests "com.atrainingtracker.trainingtracker.database.Equipment*"`

---

## 6. Verification & Rollback Plan
* **Verification**: Unit tests on simulated telemetry, SQLite v4 $\rightarrow$ v5 migration tests, Compose chart rendering tests, translation parity.
* **Rollback**: Branch revert on `feat/ATT-7753` prior to sprint integration.
