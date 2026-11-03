# Stage 1 Analysis: ATT-4880 - Indirect Tire Pressure Monitoring (iTPMS) via GPS vs. Wheel Speed Circumference Drift

**Ticket**: [ATT-4880](https://rainerblind.atlassian.net/browse/ATT-4880)  
**Sub-task**: Pending (`[Analysis]`)  
**Parent Epic**: [ATT-166](https://rainerblind.atlassian.net/browse/ATT-166) (*Filtering to improve sensor values (especially locations)*)  
**Target Release**: `V4.9.41`  
**Active Sprint**: `2026-44.2`  
**Branch**: `feature/ATT-4880`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-01  

---

## 1. Problem Statement & Motivation
Under rider load, tire deflation ($4.5 \rightarrow 1.8\text{ bar}$) increases vertical deflection $\delta(p)$ from $\sim 3\text{ mm}$ to $\sim 14\text{ mm}$, decreasing effective rolling circumference $C_{\text{eff}} = \Delta s_{3D} / \Delta N_{\text{wheel}}$ by $1.0\%\text{--}2.2\%$ ($20\text{--}45\text{ mm/rev}$). Without hardware valve sensors, comparing 3D GNSS ground distance with wheel sensor revolutions over straight-line segments enables indirect tire pressure monitoring (iTPMS): early detection of in-ride slow punctures before rim strikes, and multi-day reminders when natural air diffusion lowers tire pressure.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **Root Cause & Gap Analysis**:
  * `SpeedAndLocationDevice.java` & `BTLEBikeDevice.java`/`ANTBikeSpeedDevice.java`: GPS distance and wheel revolutions operate in decoupled silos; no cross-domain rolling circumference estimator or gating filter exists.
  * `DevicesDatabaseManager.java`: `DevicesDbHelper.DB_VERSION = 7` only stores static `calibrationFactor` (`CALIBRATION_FACTOR`), lacking persistent fresh-pump baseline circumference $C_{\text{baseline}}$.
  * `EditDeviceDialog.kt` (`WheelCircumferenceSelector`): Lacks baseline visualization and "Reset Tire Pressure Baseline (Just Pumped Tires)" user action.
* **Independent Peer Review & Verification (ATT-3534)**:
  * Verified: Turning introduces GPS corner-cutting (underestimating distance) and lean-angle radius reduction; gating on $|\Delta\text{bearing}/\Delta t| < 3^\circ/\text{s}$ successfully isolates straight roads where GPS error averages to $< 0.2\%$ over $2\text{ km}$.
  * Verified: Vertical grade introduces geometric discrepancy ($\Delta s_{3D} = \sqrt{\Delta s_{2D}^2 + \Delta h^2}$); at 10% grade, $s_{3D} / s_{2D} \approx 1.005$ (0.5%), false-triggering low-pressure alerts unless corrected by fused elevation $\Delta h$.
  * Verified: Gating on horizontal accuracy $\le 6.0\text{ m}$ and speed $\ge 4.0\text{ m/s}$ ($14.4\text{ km/h}$) eliminates stationary wander and stop-and-go quantization noise.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  1. Gated Estimator: Accumulate $(\Delta s_{3D}, \Delta N_{\text{wheel}})$ when $|\Delta\text{bearing}/\Delta t| < 3^\circ/\text{s}$, $\text{accuracy} \le 6\text{ m}$, $\text{speed} \ge 4\text{ m/s}$, with 3D slope correction $\sqrt{\Delta s_{2D}^2 + \Delta h^2}$.
  2. Dual-Timescale Estimator & CUSUM Detector:
     - Ride-Start $C_{\text{start}}$: Lock after $1.5\text{--}2.5\text{ km}$ of gated riding; trigger Low Tire Pressure Reminder if $C_{\text{start}} / C_{\text{baseline}} \le 1 - 0.006$ ($-0.6\%$).
     - Live Rolling $C_{\text{short}}(t)$ ($\approx 2\text{ km}$ sliding window) + one-sided CUSUM detector: trigger In-Ride Slow Puncture Warning if $C_{\text{short}} / C_{\text{start}} \le 1 - 0.010$ ($-1.0\%$).
  3. Persistence & UI Action: Migrate `Devices.db` to v8 adding `baselineCircumference REAL DEFAULT 0.0` with Rule 62 `DatabaseSchemaRegistry` parity; add "Reset Tire Pressure Baseline" in `WheelCircumferenceSelector`.
  4. Design System Banners: Render amber warning container for low pressure reminder, error red critical container for slow puncture HUD banner/notification (Design Guidelines Section 5.4).
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  1. BLE direct TPMS valve-stem protocol integration (hardware-free indirect TPMS only).
  2. Running stride factor or footpod calibration (bike wheel sensors only).
  3. Modifying `WorkoutSummaries.db` or persisting per-second $C_{\text{eff}}$ traces to workout files.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Requirement Reference**: Net-new `REQ-TRK-050` (Indirect Tire Pressure Monitoring via GPS vs. Wheel Circumference Drift).
* **Chesterton's Fence**:
  * `REQ-CON-009` (Per-Device Wheel Circumference): Preserved as the base manual calibration factor; $C_{\text{baseline}}$ is stored alongside without altering legacy speed/distance computation.
  * Invariant: `MySensorManager.SPEED_PRIORITY` and hardware wheel speed distance accumulation remain 100% unaltered.

---

## 5. Architectural Strategy & High-Level Solution
* **Database & Repository Layer**:
  * `DevicesDbHelper.DB_VERSION` $7 \rightarrow 8$: add `baselineCircumference REAL DEFAULT 0.0` in `onCreate()` and `onUpgrade()`. Update `DatabaseSchemaRegistry` to reference constant directly (Rule 62).
  * `DevicesDatabaseManager`: add `getBaselineCircumference()`, `setBaselineCircumference()`, `resetBaselineCircumference()`.
  * `DeviceUiData` & `EditDeviceViewModel`: expose `baselineCircumference` and `onResetTirePressureBaseline(deviceId)`.
* **iTPMS Estimator Engine (`IndirectTpmsEstimator.kt`)**:
  * Stateful component hosted in tracking session / BANAL repository.
  * Ingests 1-Hz GPS $(\text{lat}, \text{lon}, \text{bearing}, \text{acc}, \text{speed})$, altitude $\Delta h$, and wheel revolutions $\Delta N_{\text{wheel}}$.
  * Gating checks: $|\Delta\text{bearing}/\Delta t| < 3^\circ/\text{s}$, $\text{acc} \le 6\text{ m}$, $\text{speed} \ge 4.0\text{ m/s}$.
  * Dual-timescale state machine: `ACCUMULATING_START` $\rightarrow$ `LOCKED_START` (checks $C_{\text{start}}/C_{\text{baseline}}$) $\rightarrow$ `MONITORING_SHORT` (CUSUM change-point test against $C_{\text{start}}$).
* **UI & Notification Integration**:
  * Dispatch alerts via `WorkoutNavigationEvents` / `BANALServiceRepository`.
  * Compose banner in `SensorGridScreen.kt` / `TrackingTabsScreen.kt` using `LightWarningContainer`/`DarkWarningContainer` (warning) and `errorContainer`/`onErrorContainer` (critical).

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**:
  1. Wheel sensor distance accumulation (`mDistanceSensor.accumulate(...)`) and speed are never mutated or disrupted.
  2. Fresh database creation (`onCreate`) and incremental upgrade (`onUpgrade`) yield identical schema structures (Rule 64).
  3. Gating guarantees zero false-positive puncture warnings during tight mountain switchbacks or urban GPS signal loss.
* **Risk Rating**: **MEDIUM** — Database schema migration (`Devices.db` v7 $\rightarrow$ v8) and real-time multi-sensor telemetry fusion. Mitigated by strict gating criteria, isolated estimator component, and deterministic unit/contract tests.
