# Stage 5: Walkthrough & Verification - ATT-4543: [Verbesserung] Update default Cycling FTP to 270 W and align default PWR_BIKE Coggan 7-zone cutoffs (149, 203, 243, 284, 324, 405 W)

**Ticket**: [ATT-4543](https://rainerblind.atlassian.net/browse/ATT-4543)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.40` | **Active Sprint**: `2026-43.5` | **Branch**: `feature/ATT-4543`  
**Requirement Mapping**: `REQ-UI-426` (refining `REQ-UI-370`, `REQ-UI-373`) | **Test Mapping**: `TST-UI-391`  
**Author**: AI Agent 1 (Implementer) | **Date**: 2026-10-21  

---

## 1. Executive Summary & Verification Overview

Ticket **ATT-4543** elevates default Cycling FTP from 240 W to 270 W (~3.38 W/kg for an 80 kg cyclist) and synchronizes default Coggan 7-zone cutoffs across the codebase:
1. **Cycling Power Defaults (`REQ-UI-426.1`)**: Set `DEFAULT_CYCLING_FTP = 270` in `SettingsDataStore.kt` and initial `ftpInput = "270"` in `ZoneCalculatorDialog.kt`. Aligned `SettingsSnapshot.DEFAULT`, `getDefaultValue()`, `calculateProportionalPowerDefaults()`, and `resetToDefaults()` to Coggan 7-zone cutoffs for 270 W: `[149, 203, 243, 284, 324, 405 W]`.
2. **Heart Rate Baselines Preserved (`REQ-UI-426.2`)**: Preserved `DEFAULT_HR_MAX = 185` and `DEFAULT_HR_REST = 55` with Karvonen baselines `[132, 145, 158, 171]` (run) and `[127, 140, 153, 166]` (bike).
3. **Tracking HUD Runtime Fallbacks (`REQ-UI-426.3`)**: Synchronized initial `zoneCutoffsMap` and HUD fallback branches in `TrackingViewModel.kt` to the 270 W cutoffs.
4. **System Invariants (`REQ-UI-426.4`)**: Customized user profiles (`isCustomized == true`), SQLite historical data, boundary integer monotonicity ($Z_n \ge Z_{n-1} + 1$), and pure mathematical algorithms in `ZoneCalculationMath.kt` remain strictly untouched.

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Method | Result | Status in Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-UI-426.1` | `TST-UI-391.1` | Unit Test (`SettingsDataStoreBaselinesTest.kt`): `defaultConstantsAndSnapshot_matchCanonicalBaselines` | **PASSED** | `Verified` |
| `REQ-UI-426.1` | `TST-UI-391.2` | Unit Test (`SettingsDataStoreBaselinesTest.kt`): `getDefaultValueAndResetToDefaults_matchCanonicalBaselines` | **PASSED** | `Verified` |
| `REQ-UI-426.1` | `TST-UI-391.3` | Contract Test (`ZoneCalculatorDialogContractTest.kt`): `initialState_populatesStoredFtpOrFallback270W` | **PASSED** | `Verified` |
| `REQ-UI-426.3` | `TST-UI-391.4` | Architecture / Code Audit (`TrackingViewModel.kt`): `zoneCutoffsMap` fallback cutoffs | **PASSED** | `Verified` |
| `REQ-UI-426.1` | `TST-UI-391.5` | Unit Tests (`BatterySaverModelPureTest.kt`, `SettingsDataStoreJavaHelperNonBlockingTest.kt`, `SettingsDataStorePowerZonesTest.kt`) | **PASSED** | `Verified` |
| `REQ-UI-426.4` | `TST-UI-391.6` | Requirement Governance Audit (`verify_requirement_governance.py`) | **PASSED** | `Verified` |
| `REQ-PRO-001` | `TST-UI-391.6` | Tier 1 Unit Test Suite (`settings.*`, `zones.*`, `batterysaver.*`, `ui.settings.*`) | **PASSED** (100%) | `Verified` |

---

## 3. Automated Test Evidence

### 3.1 Tier 1 Unit Test Suite (`./gradlew testDebugUnitTest`)
```text
BUILD SUCCESSFUL in 2m 44s
32 actionable tasks: 32 executed
31 targeted tests executed across SettingsDataStoreBaselinesTest, ZoneCalculatorDialogContractTest, SettingsDataStoreJavaHelperNonBlockingTest, SettingsDataStorePowerZonesTest, and BatterySaverModelPureTest: 0 failures, 0 errors, 0 skipped (100% pass rate).
```

### 3.2 Build & Packaging Integrity (`./gradlew assembleDebug`)
```text
BUILD SUCCESSFUL in 12s
43 actionable tasks: 4 executed, 39 up-to-date
DEX, manifest, and debug APK packaging compiled cleanly with 0 errors.
```

### 3.3 Requirement Governance Audit (`python3 tools/verify_requirement_governance.py`)
```text
Modified/Altered existing requirement(s) detected: REQ-UI-426.
PASS: Requirement Archaeology & Chesterton's Fence Audit successfully verified with all 4 mandatory fields.
Exit Code: 0
```

---

## 4. Hardware / Physical Verification (Pixel 10) & UI Consistency (Rule 23)

* **Physical Device**: Headless agent verification environment; no physical device attached (`adb devices` = empty).
* **UI Scope & Consistency (Rule 23)**:
  * Non-UI / Parameter Change: Updates configuration constants, ViewModels, and dialog initial state without altering layout hierarchy, composables, or styling.
  * Reused Component: Existing Material 3 `ZoneCalculatorDialog.kt` reused with standard color scheme (`primary`, `surface`, `error`) and typography (`titleMedium`, `bodyMedium`).
  * Checked against `docs/design_guidelines.md` §5: shapes ☑ spacing ☑ colors/themes ☑ typography/icons ☑ placement ☑. Deviations: None.

---

## 5. Invariant & Governance Verification

1. **Zero Regressions**: All 31 targeted unit and contract tests passed with 100% pass rate.
2. **Living Documentation Synchronized**: `docs/requirements.md` (`REQ-UI-426`) and `docs/tests.md` (`TST-UI-391`) transitioned to `Verified`.
3. **Core Rule 8 Respected**: Zero intermediate git commits on `feature/ATT-4543`; squashed integration reserved for Gate 5.
