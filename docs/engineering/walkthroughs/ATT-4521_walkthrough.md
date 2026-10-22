# Stage 5: Walkthrough & Verification - ATT-4521: [Verbesserung] Update default Cycling FTP (240 W) and Heart Rate / Power zone boundaries to realistic, calculator-aligned baselines

**Ticket**: [ATT-4521](https://rainerblind.atlassian.net/browse/ATT-4521)  
**Sub-task**: [ATT-4536](https://rainerblind.atlassian.net/browse/ATT-4536) (`[Test]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.40` | **Active Sprint**: `2026-43.5` | **Branch**: `feature/ATT-4521`  
**Requirement Mapping**: `REQ-UI-426` (amending `REQ-UI-370`, `REQ-UI-373`) | **Test Mapping**: `TST-UI-391`  
**Author**: AI Agent 1 (Implementer) | **Date**: 2026-10-21  

---

## 1. Executive Summary & Verification Overview

Ticket **ATT-4521** harmonizes default training zone baselines and thresholds across `SettingsDataStore.kt`, `ZoneCalculatorDialog.kt`, and `TrackingViewModel.kt`:
1. **Cycling Power Defaults (`REQ-UI-426.1`)**: Set `DEFAULT_CYCLING_FTP = 240` in `SettingsDataStore.kt` and default `ftpInput = "240"` in `ZoneCalculatorDialog.kt`. Aligned `SettingsSnapshot.DEFAULT`, `getDefaultValue()`, and `resetToDefaults()` to Coggan 7-zone cutoffs for 240 W: `[132, 180, 216, 252, 288, 360]`.
2. **Heart Rate Defaults (`REQ-UI-426.2`)**: Added `DEFAULT_HR_MAX = 185` and `DEFAULT_HR_REST = 55` in `SettingsDataStore.kt`; aligned `hrMaxInput = "185"` and `hrRestInput = "55"` in `ZoneCalculatorDialog.kt`. Aligned default `HR_RUN` cutoffs to Karvonen baselines `[132, 145, 158, 171]` and `HR_BIKE` to cycling cutoffs `[127, 140, 153, 166]`.
3. **Tracking HUD Runtime Fallbacks (`REQ-UI-426.3`)**: Aligned initial `zoneCutoffsMap` and HUD fallback branches in `TrackingViewModel.kt` to canonical baselines.
4. **System Invariants (`REQ-UI-426.4`)**: Preserved customized user profiles (`isCustomized == true`), SQLite historical immutability, boundary integer monotonicity ($Z_n \ge Z_{n-1} + 1$), and mathematical purity of `ZoneCalculationMath.kt`.

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Method | Result | Status in Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-UI-426.1`, `REQ-UI-426.2` | `TST-UI-391.1` | Unit Test (`SettingsDataStoreBaselinesTest.kt`): `defaultConstantsAndSnapshot_matchCanonicalBaselines` | **PASSED** | `Verified` |
| `REQ-UI-426.1`, `REQ-UI-426.2` | `TST-UI-391.2` | Unit Test (`SettingsDataStoreBaselinesTest.kt`): `getDefaultValueAndResetToDefaults_matchCanonicalBaselines` | **PASSED** | `Verified` |
| `REQ-UI-426.1`, `REQ-UI-426.2` | `TST-UI-391.3` | Contract Test (`ZoneCalculatorDialogContractTest.kt`): `initialState_populatesStoredFtpOrFallback240W` | **PASSED** | `Verified` |
| `REQ-UI-426.3` | `TST-UI-391.4` | Architecture / Code Audit (`TrackingViewModel.kt`): `zoneCutoffsMap` and fallback branches | **PASSED** | `Verified` |
| `REQ-UI-426.1`, `REQ-UI-426.2` | `TST-UI-391.5` | Unit Tests (`BatterySaverModelPureTest.kt`, `SettingsDataStoreJavaHelperNonBlockingTest.kt`, `SettingsDataStorePowerZonesTest.kt`) | **PASSED** | `Verified` |
| `REQ-UI-370` | `TST-UI-333` | Regression Audit & Governance (`verify_requirement_governance.py`) | **PASSED** | `Verified` |
| `REQ-PRO-001` | `TST-UI-391.6` | Tier 1 Unit Test Suite (`settings.*`, `zones.*`, `batterysaver.*`, `ui.settings.*`) | **PASSED** (100%) | `Verified` |

---

## 3. Automated Test Evidence

### 3.1 Tier 1 Unit Test Suite (`./gradlew testDebugUnitTest`)
```text
BUILD SUCCESSFUL in 40s
32 actionable tasks: 12 executed, 20 up-to-date
100% pass rate across targeted settings, zones, batterysaver, and ui.settings test suites.
```

### 3.2 Build & Packaging Integrity (`./gradlew assembleDebug`)
```text
BUILD SUCCESSFUL in 16s
43 actionable tasks: 4 executed, 39 up-to-date
DEX, manifest, and debug APK packaging compiled cleanly with 0 errors.
```

### 3.3 Requirement Governance Audit (`python3 tools/verify_requirement_governance.py`)
```text
Modified/Altered existing requirement(s) detected: REQ-UI-370.
PASS: Requirement Archaeology & Chesterton's Fence Audit successfully verified with all 4 mandatory fields.
Exit Code: 0
```

---

## 4. Hardware / Physical Verification (Pixel 10) & UI Consistency (Rule 23)

* **Physical Device**: `adb devices` evaluated; no physical device attached in headless agent runtime.
* **UI Consistency (Rule 23)**:
  * Component: Existing Material 3 `ZoneCalculatorDialog.kt` reused without structural or token alterations.
  * Typography & Colors: Standard Material 3 typography (`titleMedium`, `bodyMedium`) and color scheme tokens (`primary`, `surface`, `error`).
  * Checked against `docs/design_guidelines.md` §5: shapes ☑ spacing ☑ colors/themes ☑ typography/icons ☑ placement ☑. Deviations: None.

---

## 5. Invariant & Governance Verification

1. **Zero Regressions**: Tier 1 targeted unit test suite passed with 100% pass rate.
2. **Living Documentation Synchronized**: `docs/requirements.md` (`REQ-UI-426`, `REQ-UI-370`) and `docs/tests.md` (`TST-UI-391`) auto-flipped to `Verified`.
3. **Core Rule 8 Respected**: No intermediate git commits created during Stages 1–5; single atomic commit reserved for Gate 5 / sprint branch integration.
