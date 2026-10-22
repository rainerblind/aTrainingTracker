# Stage 3: Implementation Plan - ATT-4543: [Verbesserung] Update default Cycling FTP to 270 W and align default PWR_BIKE Coggan 7-zone cutoffs (149, 203, 243, 284, 324, 405 W)

**Ticket**: [ATT-4543](https://rainerblind.atlassian.net/browse/ATT-4543) | **Sub-task**: Pending Submission (`[Impl-Plan]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.40` | **Active Sprint**: `2026-43.5` | **Branch**: `feature/ATT-4543`  
**Requirement Mapping**: `REQ-UI-426` (amending `REQ-UI-370`, `REQ-UI-373`) | **Test Mapping**: `TST-UI-391`  
**Author**: AI Agent 1 (Implementer - `subagent_pro`) | **Date**: 2026-10-21  

---

## 1. Architecture & Invariant Compliance

* **Baseline Alignment**: Synchronize `PWR_BIKE` default Functional Threshold Power to **270 W** across DataStore, UI calculator dialogs, and tracking ViewModels. Canonical Coggan cutoffs: `[149, 203, 243, 284, 324, 405 W]`.
* **Layering & SWE.2 Boundaries**: DataStore layer (`SettingsDataStore.kt`, `SettingsSnapshot`), UI layer (`ZoneCalculatorDialog.kt`), and presentation/HUD layer (`TrackingViewModel.kt`). Zero SQLite schema or mathematical engine mutations.
* **Core Invariants**:
  1. *Custom Profiles Preserved*: Athletes with `isCustomized == true` retain stored thresholds without overwrites.
  2. *SQLite Immutability*: Historical workout summaries and samples in SQLite remain strictly immutable.
  3. *Monotonic Order & Ceilings*: Integer monotonicity ($0 < 149 < 203 < 243 < 284 < 324 < 405 < 2500\text{ W}$) strictly preserved.
  4. *HR Baseline Stability*: Heart Rate baselines (`DEFAULT_HR_MAX = 185`, `DEFAULT_HR_REST = 55`) remain untouched.
* **UI Consistency (Rule 23)**: Existing Material 3 dialog (`ZoneCalculatorDialog.kt`) preserved; no new tokens or styles.

---

## 2. Atomic Implementation Steps

* **Step 1 — Update SettingsDataStore Baselines & Snapshots (`REQ-UI-426.1`)**:
  * Set `DEFAULT_CYCLING_FTP = 270`.
  * Update `SettingsSnapshot.DEFAULT.pwrBikeZoneMax = listOf(149, 203, 243, 284, 324, 405)`.
  * Update `getDefaultValue(ZoneType.PWR_BIKE, ...)` and `resetToDefaults(ZoneType.PWR_BIKE)` cutoffs.
  * Update `calculateProportionalPowerDefaults()` unconfigured Z5/Z6 fallback to `Pair(324, 405)` when `z4Max <= 0`.
  * Update fallback cutoffs in `getZoneMaxFlow()`, `mapSettingsSnapshot()`, and `checkAndMigratePowerZones()`.
* **Step 2 — Align ZoneCalculatorDialog Default State (`REQ-UI-426.2`)**:
  * Update `ZoneCalculatorUiState.ftpInput` fallback default to `"270"`.
* **Step 3 — Synchronize TrackingViewModel Fallback Cutoff Map (`REQ-UI-426.3`)**:
  * Align `zoneCutoffsMap[PWR_BIKE]` and fallback branches in `calculateZoneColor()` and `calculateZoneGaugeState()` to `[149, 203, 243, 284, 324, 405]`.
* **Step 4 — Update Unit & Contract Test Baselines (`TST-UI-391.1`–`TST-UI-391.5`)**:
  * Update assertions in `SettingsDataStoreBaselinesTest.kt`, `ZoneCalculatorDialogContractTest.kt`, `SettingsDataStorePowerZonesTest.kt`, and `SettingsDataStoreJavaHelperNonBlockingTest.kt` to reflect 270 W baseline and cutoffs.

---

## 3. File-Level Change Matrix

| File Path | Role | Nature of Modifications |
| :--- | :--- | :--- |
| `app/.../settings/SettingsDataStore.kt` | Production | `DEFAULT_CYCLING_FTP = 270`, snapshot defaults, `getDefaultValue`, `resetToDefaults`, `mapSettingsSnapshot`. |
| `app/.../ui/settings/ZoneCalculatorDialog.kt` | Production | Update `ZoneCalculatorUiState.ftpInput` default to `"270"`. |
| `app/.../ui/tracking/tracking/TrackingViewModel.kt` | Production | Align `zoneCutoffsMap[PWR_BIKE]` and color/gauge fallback lists to `[149, 203, 243, 284, 324, 405]`. |
| `app/.../zones/SettingsDataStoreBaselinesTest.kt` | Test | Assert `DEFAULT_CYCLING_FTP == 270`, snapshot defaults, and `resetToDefaults()` cutoffs. |
| `app/.../zones/ZoneCalculatorDialogContractTest.kt` | Test | Assert `initialState_populatesStoredFtpOrFallback270W` defaults to `"270"` and `[149, 203, 243, 284, 324, 405]`. |
| `app/.../settings/SettingsDataStoreJavaHelperNonBlockingTest.kt` | Test | Update fallback cutoff assertion for `ZoneType.PWR_BIKE` Zone 1 from `132` to `149`. |
| `app/.../settings/SettingsDataStorePowerZonesTest.kt` | Test | Update unconfigured `PWR_BIKE` Zone 1 max assertion from `132` to `149`. |

---

## 4. Test Execution Strategy

* **Tier 1 — Per-Ticket Verification (Stages 4 & 5)**:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.settings.*" \
    --tests "com.atrainingtracker.trainingtracker.zones.*" \
    --tests "com.atrainingtracker.trainingtracker.batterysaver.*"
  ```
* **Governance Verification**:
  ```bash
  python3 tools/verify_requirement_governance.py
  ```

---

## 5. Rollback & Risk Mitigation

* **Risk Level**: **LOW**. Pure constant baseline and fallback cutoff adjustment across existing configuration pathways.
* **Rollback Plan**: Zero database schema mutations. Full rollback achievable via `git checkout` / `git revert` on `feature/ATT-4543`.
