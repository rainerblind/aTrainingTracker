# Stage 3 Implementation Plan - ATT-7521: Global Typography DFA α1

## 1. Architecture & Component Decomposition
- **Target Components**:
  - `app/src/main/res/values*/strings_hrv.xml` (all 9 locales: `values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`): Update 26 string definitions from ASCII `DFA a1` / `a1` to authentic Greek `DFA α1` / `α1` (`REQ-UI-526.1`).
  - `ui/activities/ZonesSettingsActivity.kt`: Standardize dynamic range badge templates from subscript `α₁` to standard Greek `α1` (`"α1 > %.2f"`, `"%.2f ≤ α1 ≤ %.2f"`, `"α1 < %.2f"`) (`REQ-UI-526.2`).
  - `banalservice/sensor/SensorTypeHrvTest.kt`: Add unit assertion verifying `SensorType.HRV_DFA_A1` resolves to `'DFA α1'` and `'α1'` (`TST-UI-526.1`).
  - `trainingtracker/zones/HrvDfaA1SettingsContractTest.kt`: Add contract assertion verifying `ZonesSettingsActivity.kt` contains `"α1 > "` and zero `"α₁"` (`TST-UI-526.2`).
  - UI Test Fixtures (`HrvInfoBottomSheetTest.kt`, `SensorFieldZoneDisplayTest.kt`, `TrackingViewModelHrvTest.kt`): Align mock label strings from `"DFA a1"` to `"DFA α1"` (`TST-UI-526.3`).
  - `localization/TranslationParityTest.kt`: Verify 100% 9-language translation parity (`TST-UI-526.4`).
- **Scope Boundary (ATT-1250)**: Internal enum names (`SensorType.HRV_DFA_A1`), resource IDs (`R.string.hrv_dfa_a1`), DataStore keys, database columns, and UI test tags (`hrv_test_live_a1`) remain 100% invariant.

## 2. Atomic Implementation Steps
1. **Step 1 - XML String Resources Typography Update (9 Locales) (`REQ-UI-526.1`, `REQ-UI-526.5`)**:
   - In `app/src/main/res/values/strings_hrv.xml` and all 8 localized counterparts, update the 26 strings (`hrv_dfa_a1`, `hrv_dfa_a1_short`, `tab_hrv_dfa_a1`, `hrv_test_live_a1`, etc.) to Greek `DFA α1` / `α1`.
   - Validate XML resource parsing via `./gradlew processDebugResources`.
2. **Step 2 - ZonesSettingsActivity Range Badge Alignment (`REQ-UI-526.2`)**:
   - In `ZonesSettingsActivity.kt` lines 1162, 1170, 1178, replace `"α₁ > %.2f"`, `"%.2f ≤ α₁ ≤ %.2f"`, and `"α₁ < %.2f"` with standard Greek `"α1 > %.2f"`, `"%.2f ≤ α1 ≤ %.2f"`, and `"α1 < %.2f"`.
3. **Step 3 - Test Fixtures Alignment & Typography Contracts (`TST-UI-526.1`, `TST-UI-526.2`, `TST-UI-526.3`)**:
   - In `SensorTypeHrvTest.kt`, add verification that `R.string.hrv_dfa_a1` and `R.string.hrv_dfa_a1_short` contain `"DFA α1"` and `"α1"`.
   - In `HrvDfaA1SettingsContractTest.kt`, add contract assertion ensuring zero occurrences of subscript `"α₁"` and presence of `"α1 > "`.
   - In `HrvInfoBottomSheetTest.kt`, `SensorFieldZoneDisplayTest.kt`, and `TrackingViewModelHrvTest.kt`, update mock sensor field labels to `"DFA α1"`.
4. **Step 4 - Verification & CI Test Suite (`TST-UI-526.4`, `TST-UI-526.5`)**:
   - Run `python3 tools/parallel_sprint_lock.py ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.localization.TranslationParityTest"`.
   - Run targeted unit tests for `com.atrainingtracker.banalservice.sensor.*`, `com.atrainingtracker.trainingtracker.zones.*`, and `com.atrainingtracker.trainingtracker.ui.tracking.*`.
   - Run `python3 tools/verify_requirement_governance.py`.

## 3. Invariant Protection & System Properties
- **DataStore & DB Stability**: Zero modifications to DataStore preference keys (`tuning_dfa_a1_threshold_*`, `hrv_dfa_a1_zone_*`) or SQLite schemas (`WorkoutRrIntervals`).
- **Identifier Stability**: Zero changes to enum names or resource IDs.
- **Localization Parity**: 100% key and format specifier match across all 9 supported locales.

## 4. Verification & Testing Strategy
- Automated tests: `SensorTypeHrvTest` (`TST-UI-526.1`), `HrvDfaA1SettingsContractTest` (`TST-UI-526.2`), UI fixtures (`TST-UI-526.3`), `TranslationParityTest` (`TST-UI-526.4`), targeted regression (`TST-UI-526.5`).
- Governance: `python3 tools/verify_requirement_governance.py`.

## 5. UI Consistency & Design Guidelines Compliance
- Eliminates mixed ASCII/subscript typography, establishing uniform Greek `DFA α1` / `α1` across Cockpit, Zones, Workout Details, and Ramp Test.

## 6. Living Documentation Updates
- Update `docs/requirements/req_ui_design_system.md` (`REQ-UI-526`) and `docs/tests/tst_ui_design_system.md` (`TST-UI-526`) to `[Verified]` in Stage 5.
