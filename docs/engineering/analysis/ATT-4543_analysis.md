# Stage 1 Analysis: ATT-4543 - [Verbesserung] Update default Cycling FTP to 270 W and align default PWR_BIKE Coggan 7-zone cutoffs (149, 203, 243, 284, 324, 405 W)

**Ticket**: [ATT-4543](https://rainerblind.atlassian.net/browse/ATT-4543)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.40` | **Active Sprint**: `2026-43.5` | **Branch**: `feature/ATT-4543`  
**Author**: AI Agent 1 (Implementer - `subagent_pro`) | **Date**: 2026-10-21  

---

## 1. Problem Domain & Root Cause Analysis

* **Context**: In ATT-4521, default Cycling FTP was updated to 240 W with cutoffs (132, 180, 216, 252, 288, 360 W). Because aTrainingTracker specifically targets serious endurance cyclists using ANT+/BLE power meters, 240 W (~3.0 W/kg at 80 kg) is on the low side.
* **Target Baseline**: Update default Cycling FTP to **270 W** (~3.38 W/kg at 80 kg, 3.6 W/kg at 75 kg). Applying canonical Coggan percentages (`ZoneCalculationMath.calculateCogganPowerZones(270)`) yields:
  * Z1 (Active Recovery, $\le 55\%$): **149 W**
  * Z2 (Endurance, $\le 75\%$): **203 W**
  * Z3 (Tempo, $\le 90\%$): **243 W**
  * Z4 (Lactate Threshold, $\le 105\%$): **284 W**
  * Z5 (VO2 Max, $\le 120\%$): **324 W**
  * Z6 (Anaerobic Capacity, $\le 150\%$): **405 W**
  * Z7 (Neuromuscular): $> 405\text{ W}$, capped at $2500\text{ W}$ ceiling.

---

## 2. Call Site & Blast Radius Audit

| Component | Target File | Nature of Change & Impact |
| :--- | :--- | :--- |
| **DataStore Constants & Defaults** | `SettingsDataStore.kt` | Update `DEFAULT_CYCLING_FTP = 270`, `SettingsSnapshot.DEFAULT.pwrBikeZoneMax = [149, 203, 243, 284, 324, 405]`, `getDefaultValue()`, `calculateProportionalPowerDefaults()`, and fallback cutoffs in `mapSettingsSnapshot()`, `getZoneMaxFlow()`, `checkAndMigratePowerZones()`, `resetToDefaults()`. |
| **Zone Calculator Dialog** | `ZoneCalculatorDialog.kt` | Update `ZoneCalculatorUiState.ftpInput` default to `"270"` (or `DEFAULT_CYCLING_FTP.toString()`). |
| **Tracking HUD** | `TrackingViewModel.kt` | Update `zoneCutoffsMap[PWR_BIKE]` and fallback cutoffs in `calculateZoneColor()` and `calculateZoneGaugeState()`. |
| **Unit & Contract Tests** | `app/src/test/...` | Update baseline assertions in `SettingsDataStoreBaselinesTest.kt`, `ZoneCalculatorDialogContractTest.kt`, `SettingsDataStorePowerZonesTest.kt`, `BatterySaverModelPureTest.kt`, `SettingsDataStoreJavaHelperNonBlockingTest.kt`, `TrackingViewModelGridTest.kt`. |
| **Living Documentation** | `docs/requirements.md`, `docs/tests.md` | Synchronize `REQ-UI-426` and `TST-UI-391` with the new 270 W baseline and cutoffs. |

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  1. Update `DEFAULT_CYCLING_FTP` to `270` in `SettingsDataStore.kt`.
  2. Update all canonical `PWR_BIKE` default cutoffs to `[149, 203, 243, 284, 324, 405]` across `SettingsDataStore.kt`, `ZoneCalculatorDialog.kt`, and `TrackingViewModel.kt`.
  3. Update proportional power default calculation for unconfigured Z5/Z6 when Z4 is missing/zero (`Pair(324, 405)`).
  4. Update all affected unit/contract tests and living documentation.
* **Out-of-Scope Non-Goals**:
  1. No changes to Heart Rate baselines (`DEFAULT_HR_MAX = 185`, `DEFAULT_HR_REST = 55`, `HR_RUN`, `HR_BIKE`).
  2. No changes to mathematical formulas or percentages in `ZoneCalculationMath.kt`.
  3. No changes to user-customized zone configurations (`isCustomized == true`).
  4. No schema or data migrations in SQLite tables (`WorkoutSummaries.db`).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit (REQ-PRO-022)

* **Original Requirement ID & Target**: Amends `REQ-UI-426` (*Realistic Default Cycling FTP and Calculator-Aligned Training Zone Baselines*) and `REQ-UI-370` (*Decoupled Training Zone Architecture & 7-Zone Coggan Cycling Power Model*).
* **Historical Origin & Commit Trace**: `ATT-4521` (`7051a307ddf7`, Sprint 2026-43.5), `ATT-3184` (`25828a66`), `ATT-3188` (`889fcebf`).
* **Root Reason for Existing Formulation**: ATT-4521 set 240 W as an initial recreational baseline to synchronize calculator dialogs and DataStore. Athletic calibration indicates 270 W is the optimal target baseline for competitive power meter cyclists.
* **Preservation of Core Invariants**: Customized user profiles retain stored values; strict integer monotonicity ($Z_n \ge Z_{n-1} + 1$) and 2500 W ceiling remain guaranteed; calculation engine formulas remain pure and unchanged.

---

## 5. Architectural Strategy & System Invariants

* **Single Source of Truth**: Centralize `DEFAULT_CYCLING_FTP = 270` in `SettingsDataStore.kt`.
* **Monotonic Order**: $0 < 149 < 203 < 243 < 284 < 324 < 405 < 2500\text{ W}$ satisfies all boundary validation assertions (`ZoneValidator.validatePowerZoneStack()`).
* **Risk Rating**: **LOW** — Pure constant baseline and fallback cutoff adjustment across existing configuration pathways; zero architectural, concurrency, or database schema risk.
