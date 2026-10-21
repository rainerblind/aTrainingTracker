# Stage 3: Implementation Plan - ATT-4521: Update default Cycling FTP (240 W) and Heart Rate / Power zone boundaries to realistic, calculator-aligned baselines

**Ticket**: [ATT-4521](https://rainerblind.atlassian.net/browse/ATT-4521) | **Sub-task**: [ATT-4534](https://rainerblind.atlassian.net/browse/ATT-4534) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.40` | **Active Sprint**: `2026-43.5` | **Branch**: `feature/ATT-4521`  
**Requirement Mapping**: `REQ-UI-426` (amending `REQ-UI-370`, `REQ-UI-373`) | **Test Mapping**: `TST-UI-391`  
**Author**: AI Agent 1 (Implementer - `subagent_pro`) | **Date**: 2026-10-21  

---

## 1. Problem Description & Background
Synchronize default baseline constants and upper zone cutoffs across `SettingsDataStore.kt`, `ZoneCalculatorDialog.kt`, and `TrackingViewModel.kt` to realistic, calculator-aligned baselines:
* Cycling Power (`PWR_BIKE`): Default **FTP = 240 W**; Coggan cutoffs: `[132, 180, 216, 252, 288, 360 W]`.
* Heart Rate (`HR_RUN` & `HR_BIKE`): Karvonen baselines **HRmax = 185 bpm, HRrest = 55 bpm**; `HR_RUN` cutoffs: `[132, 145, 158, 171 bpm]`; `HR_BIKE` cutoffs (~5 bpm lower): `[127, 140, 153, 166 bpm]`.

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-UI-426` (`REQ-UI-426.1` Power, `REQ-UI-426.2` Heart Rate, `REQ-UI-426.3` Tracking HUD, `REQ-UI-426.4` Invariants).
* **Test Mapping**: `TST-UI-391` (`TST-UI-391.1` Constants/Snapshot, `TST-UI-391.2` Symmetry, `TST-UI-391.3` Dialog Contract, `TST-UI-391.4` TrackingViewModel, `TST-UI-391.5` Legacy Tests, `TST-UI-391.6` Full Suite).

---

## 3. System Invariants & Preserved Behavior
1. **Customized Profiles Preserved**: Athletes with `isCustomized == true` retain stored thresholds without overwrites.
2. **SQLite Immutability**: Historical workout analytics and samples in SQLite remain strictly immutable.
3. **Monotonicity & Ceilings**: Strict integer monotonicity ($Z_n \ge Z_{n-1} + 1$) and 2500 W ceiling remain guaranteed.
4. **Pure Math Integrity**: Zero changes to mathematical calculation algorithms in `ZoneCalculationMath.kt`.

---

## 4. Proposed Architectural Changes
* **`SettingsDataStore.kt`**: Update `DEFAULT_CYCLING_FTP = 240`; add `DEFAULT_HR_MAX = 185` and `DEFAULT_HR_REST = 55`. Synchronize `SettingsSnapshot.DEFAULT`, `getDefaultValue()`, `resetToDefaults()`, `mapSettingsSnapshot()`, and `calculateProportionalPowerDefaults()`.
* **`ZoneCalculatorDialog.kt`**: Default `ZoneCalculatorUiState` and `ZoneCalculatorStateHolder` fallbacks to `SettingsDataStore` baseline constants (`240`, `"185"`, `"55"`).
* **`TrackingViewModel.kt`**: Synchronize `zoneCutoffsMap` initial fallbacks to match canonical defaults.
* **UI Consistency (Rule 23)**: Existing Material 3 dialog (`ZoneCalculatorDialog.kt`) preserved; no new UI tokens or styles.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)
### Step 1: Update SettingsDataStore Baselines & Snapshots (`REQ-UI-426.1`, `REQ-UI-426.2`)
* Files: `app/src/main/java/.../settings/SettingsDataStore.kt`
* Changes: Set `DEFAULT_CYCLING_FTP = 240`; add `DEFAULT_HR_MAX = 185`, `DEFAULT_HR_REST = 55`; update `SettingsSnapshot.DEFAULT`, `getDefaultValue()`, `resetToDefaults()`, `mapSettingsSnapshot()`, and fallback pair `(288, 360)` in `calculateProportionalPowerDefaults()`.

### Step 2: Align ZoneCalculatorDialog Fallback Constants (`REQ-UI-426.1`, `REQ-UI-426.2`)
* Files: `app/src/main/java/.../ui/settings/ZoneCalculatorDialog.kt`
* Changes: Update `ZoneCalculatorUiState` default inputs to `"240"`, `"185"`, `"55"`; replace hardcoded fallback literals in `ZoneCalculatorStateHolder` and confirm/overwrite handlers with `SettingsDataStore` constants.

### Step 3: Synchronize TrackingViewModel Fallback Cutoff Map (`REQ-UI-426.3`)
* Files: `app/src/main/java/.../ui/tracking/tracking/TrackingViewModel.kt`
* Changes: Align `zoneCutoffsMap` initial map and fallback branches to `[132, 180, 216, 252, 288, 360]` for power, `[132, 145, 158, 171]` for run, and `[127, 140, 153, 166]` for bike.

### Step 4: Update Contract & Regression Test Baselines (`TST-UI-391.3`, `TST-UI-391.5`)
* Files: `ZoneCalculatorDialogContractTest.kt`, `SettingsDataStoreJavaHelperNonBlockingTest.kt`, `BatterySaverModelPureTest.kt`
* Changes: Update assertions expecting old `200` FTP or placeholder cutoffs to new calculator-aligned values.

### Step 5: Add Dedicated TST-UI-391 Baseline & Symmetry Verification Tests (`TST-UI-391.1`, `TST-UI-391.2`)
* Files: `app/src/test/java/.../settings/SettingsDataStoreBaselinesTest.kt`
* Changes: Add test cases verifying default constants, `SettingsSnapshot.DEFAULT`, and `resetToDefaults()` symmetry.

### Step 6: Execute Targeted Unit Tests
* Command: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.settings.*" --tests "com.atrainingtracker.trainingtracker.zones.*" --tests "com.atrainingtracker.trainingtracker.batterysaver.*"`

---

## 6. Verification & Rollback Plan
* **Verification**: Execute targeted unit test suites (Step 6) during Stage 4; execute Tier 1 full clean-room suite in Stage 5.
* **Rollback**: Clean revert via `git reset --hard HEAD` on `feature/ATT-4521` without touching `develop`.
