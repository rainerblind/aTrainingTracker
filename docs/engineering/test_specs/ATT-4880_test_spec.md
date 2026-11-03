# Stage 2: Requirement & Test Specification - ATT-4880: Indirect Tire Pressure Monitoring (iTPMS)

**Ticket**: [ATT-4880](https://rainerblind.atlassian.net/browse/ATT-4880)  
**Parent Epic**: [ATT-166](https://rainerblind.atlassian.net/browse/ATT-166) (*Filtering to improve sensor values*)  
**Target Release**: `V4.9.41`  
**Active Sprint**: `2026-44.2`  
**Requirement Mapping**: `REQ-TRK-050` (`REQ-TRK-050.1`..`REQ-TRK-050.5`)  
**Test Mapping**: `TST-TRK-050` (`TST-TRK-050.1`..`TST-TRK-050.5`)  
**Branch**: `feature/ATT-4880`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-01  

---

## 1. Overview & Verification Strategy
Comparing GNSS 3D ground distance with ANT+/BLE wheel speed revolutions yields effective rolling circumference $C_{\text{eff}} = \Delta s_{3D} / \Delta N_{\text{wheel}}$. As tire pressure drops, vertical deflection under rider load increases, reducing $C_{\text{eff}}$ by $1.0\%\text{--}2.2\%$. This specification defines a gated dual-timescale estimator: detecting in-ride slow punctures ($\ge 1.0\%$ drop) via a one-sided CUSUM change-point detector, and multi-day low pressure reminders ($\ge 0.6\%$ drop below pumped baseline).

---

## 2. Traceability Matrix

| Requirement ID | Test Specification ID | Verification Component | Scope & Target |
| :--- | :--- | :--- | :--- |
| `REQ-TRK-050.1` | `TST-TRK-050.1` | `IndirectTpmsEstimatorTest` | Straight-line ($|\Delta\text{bearing}/\Delta t| < 3^\circ/\text{s}$), accuracy $\le 6\text{ m}$, speed $\ge 4\text{ m/s}$, and 3D slope $\sqrt{\Delta s_{2D}^2 + \Delta h^2}$ gating. |
| `REQ-TRK-050.2` | `TST-TRK-050.2` | `IndirectTpmsEstimatorTest` | Ride-start baseline $C_{\text{start}}$ lock ($2.0\text{ km}$ gated) and multi-day low pressure reminder ($C_{\text{start}} / C_{\text{baseline}} \le 1 - 0.006$). |
| `REQ-TRK-050.3` | `TST-TRK-050.3` | `IndirectTpmsEstimatorTest` | Live sliding-window CUSUM slow puncture alert ($C_{\text{short}} / C_{\text{start}} \le 1 - 0.010$) with cornering false-alarm immunity. |
| `REQ-TRK-050.4` | `TST-TRK-050.4` | `DevicesDatabaseManagerMigrationTest` + `EditDeviceViewModelTest` | `Devices.db` v8 migration (`baselineCircumference REAL DEFAULT 0.0`), Rule 62 schema registry parity, and baseline reset action. |
| `REQ-TRK-050.5` | `TST-TRK-050.5` | `TranslationParityTest` | 9-locale string parity for iTPMS alerts/actions; semantic amber warning and red error container rendering (Section 5.4). |

---

## 3. Concrete Test Scenarios (Given-When-Then)

### TST-TRK-050.1: Straight-Line & 3D-Slope Gated Circumference Estimator
* **Given** 1-Hz GPS fixes and wheel revolution events on curves ($|\Delta\text{bearing}/\Delta t| \ge 3^\circ/\text{s}$) or degraded GPS ($\text{accuracy} > 6\text{ m}$),
* **When** processed by `IndirectTpmsEstimator`,
* **Then** samples are excluded; straight-line segments with 3D elevation correction accumulate accurate $C_{\text{eff}}$.

### TST-TRK-050.2: Ride-Start Baseline Lock & Low Pressure Reminder
* **Given** a stored pumped baseline $C_{\text{baseline}} = 2130\text{ mm}$ and a ride with under-inflated tires ($C_{\text{start}} = 2115\text{ mm}$, $-0.7\%$),
* **When** $2.0\text{ km}$ of gated straight-line riding accumulate,
* **Then** $C_{\text{start}}$ locks and a low tire pressure reminder event is emitted.

### TST-TRK-050.3: CUSUM In-Ride Slow Puncture Detection & Cornering Immunity
* **Given** a ride with locked $C_{\text{start}} = 2128\text{ mm}$ experiencing a slow leak,
* **When** rolling $C_{\text{short}}(t)$ drops by $\ge 1.0\%$ over $2\text{--}3\text{ km}$,
* **Then** the CUSUM change-point detector triggers an in-ride slow puncture alert, while hairpin turns are gated out without false alarms.

### TST-TRK-050.4: `Devices.db` v8 Migration & Baseline Reset Action
* **Given** an existing `Devices.db` at version 7,
* **When** upgraded to version 8,
* **Then** column `baselineCircumference` is added with `DEFAULT 0.0`, matching `onCreate()`, and "Reset Baseline" persists active circumference.

---

## 4. 9-Language Localization & Design Parity
* **Keys**: `devices_reset_tire_pressure_baseline`, `itpms_slow_puncture_alert_title`, `itpms_slow_puncture_alert_message`, `itpms_low_pressure_reminder_title`, `itpms_low_pressure_reminder_message`.
* **Parity**: EN, DE, ES, FR, IT, JA, NL, PL, PT with indexed positional specifier `%1$s`.
* **Visual containers**: Amber warning container for low pressure reminder; red error container for slow puncture HUD alert (Design Guidelines Section 5.4).
