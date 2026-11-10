# Stage 3: Implementation Plan - ATT-3542: Live HRV Telemetry & Aerobic Threshold Guidance

**Ticket**: [ATT-3542](https://rainerblind.atlassian.net/browse/ATT-3542) | **Epic**: [ATT-3539](https://rainerblind.atlassian.net/browse/ATT-3539) (*HRV & Autonomic Metrics*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.1` | **Branch**: `feature/ATT-3542`  
**Requirement Mapping**: `REQ-TRK-061` (`REQ-TRK-061.1`..`REQ-TRK-061.5`)  
**Test Mapping**: `TST-TRK-061` (`TST-TRK-061.1`..`TST-TRK-061.6`)  
**Author**: AI Agent 1 (`subagent_pro`) | **Date**: 2026-11-10  

---

## 1. Problem Description & Background
Following real-time RR acquisition (`ATT-3540`) and the artifact-corrected HRV engine (`ATT-3541`), athletes need cockpit visualization of autonomic telemetry on customizable tracking tabs. This plan defines the architectural integration of `HRV_DFA_A1` and `HRV_RMSSD` (primary cardiovascular) vs. `RR_INTERVAL` and `HRV_ARTIFACT_RATE` (diagnostic) in `ActivityType` and `EditSensorFieldDialog`, `DfaA1Formatter` with `--.--` artifact blanking, semantic threshold zone coloring conforming to Rule 71 theme tokens, customizable threshold settings, and `HrvInfoBottomSheet` conforming to Rule 72 and 9-language translation parity.

---

## 2. Traceability & Requirements Mapping
* **Requirements**: `REQ-TRK-061.1` (Sensor Selector & Categories), `REQ-TRK-061.2` (`DfaA1Formatter`), `REQ-TRK-061.3` (Semantic Threshold Zone Coloring), `REQ-TRK-061.4` (Customizable Thresholds), `REQ-TRK-061.5` (HRV Info Sheet & 9-Locale Parity).
* **Test Mapping**: `TST-TRK-061.1` (`SensorCategoryAndSelectorContractTest.kt`), `TST-TRK-061.2` (`DfaA1FormatterTest.kt`), `TST-TRK-061.3` (`TrackingHrvZoneIndicatorTest.kt`, `ThemeColorCentralizationAuditTest.kt`), `TST-TRK-061.4` (`TuningPreferencesDataStoreTest.kt`), `TST-TRK-061.5` (`HrvInfoBottomSheetTest.kt`, `TranslationParityTest.kt`), `TST-TRK-061.6` (Targeted suite).

---

## 3. System Invariants & Preserved Behavior
1. **Rule 71 Theme Color Centralization**: Zero hardcoded hex colors (`Color(0x...)`) outside `Color.kt`. Use `TTColor.Zone2`, `TTColor.Zone3`, `TTColor.Zone4`.
2. **Rule 72 Modal Bottom Sheet Scroll Safety**: Zero nested `verticalScroll` inside `AppModalBottomSheet` in `HrvInfoBottomSheet.kt`.
3. **Artifact-Gating Blanking**: When artifact rate exceeds 5.0% (`null` telemetry), DFA $\alpha_1$ renders `--.--` with neutral tile outline.
4. **Code Lock (Rule 4)**: Zero modifications to production code until Gate 3 is approved.
5. **UI Consistency**: Reuses `AppModalBottomSheet`, `SensorFieldView`, and standard `TTColor` zone palette tokens.

---

## 4. Proposed Architectural Changes (SWE.2)
### Component 1: `SensorType.java`, `ActivityType.java` & Category Partitioning
* In `SensorType.java`: assign `HRV_DFA_A1` and `HRV_RMSSD` to `SensorCategory.HEART_RATE_AND_ENERGY`; assign `RR_INTERVAL` and `HRV_ARTIFACT_RATE` to `SensorCategory.SYSTEM_ENVIRONMENT_AND_DIAGNOSTICS`.
* In `ActivityType.java`: append the 4 HRV metrics to `getSensorTypeArray()` for `GENERIC_HR`, `RUN_SPEED_AND_CADENCE`, `BIKE_SPEED_AND_CADENCE`, `BIKE_POWER`, and default.
* Update `SensorCategoryAndSelectorContractTest.kt` for 56 constants and partition mappings.

### Component 2: `DfaA1Formatter.java` (`banalservice.sensor.formater`)
* Formats DFA $\alpha_1$ to 2 decimal places (`0.00`). Emits `--.--` for null, negative, NaN, infinite, or artifact-gated values. Wire to `SensorType.HRV_DFA_A1`.

### Component 3: Semantic Threshold Visualization & Settings Integration
* In `TrackingViewModel.kt`: calculate `zoneColor` for `HRV_DFA_A1` using athlete thresholds $\alpha_{1,\text{T1}}$ (default `0.75f`) and $\alpha_{1,\text{T2}}$ (default `0.50f`):
  * $\alpha_1 > \alpha_{1,\text{T1}}$: `TTColor.Zone2` (Green, Aerobic Base).
  * $\alpha_{1,\text{T2}} \le \alpha_1 \le \alpha_{1,\text{T1}}$: `TTColor.Zone3` (Orange, Threshold Transition).
  * $\alpha_1 < \alpha_{1,\text{T2}}$: `TTColor.Zone4` (Red, High Intensity).
  * Null / Artifact-gated: `defaultZoneColor` (`Color.Transparent`).
* In `TuningPreferencesDataStore.kt`: add `KEY_DFA_A1_THRESHOLD_T1` and `KEY_DFA_A1_THRESHOLD_T2` with bounds check $[0.20, 1.50]$.

### Component 4: `HrvInfoBottomSheet.kt` & 9-Locale Partitioned Strings
* Implement `HrvInfoBottomSheet.kt` with `AppModalBottomSheet` explaining trailing lag, BLE ECG strap recommendations, and medical disclaimers.
* Triggerable from `EditSensorFieldDialog` and `SensorFieldView` on HRV tiles.
* Partition all strings in `strings_hrv.xml` across EN, DE, ES, FR, IT, JA, NL, PL, PT.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)
1. **Step 1 (Formatter & Sensor Categories)**: Implement `DfaA1Formatter.java`; configure categories in `SensorType.java` and `ActivityType.java`; update `SensorCategoryAndSelectorContractTest.kt`.
2. **Step 2 (Threshold Preferences)**: Add $\alpha_{1,\text{T1}}$ / $\alpha_{1,\text{T2}}$ keys, defaults, and flows in `TuningPreferencesDataStore.kt` and `TuningConfig`.
3. **Step 3 (Cockpit Zone Coloring)**: Wire `HRV_DFA_A1` semantic coloring in `TrackingViewModel.kt` using `TTColor` tokens; write `TrackingHrvZoneIndicatorTest.kt`.
4. **Step 4 (HRV Info Sheet & Strings)**: Implement `HrvInfoBottomSheet.kt`; populate `strings_hrv.xml` across all 9 locales; verify `HrvInfoBottomSheetTest.kt`.
5. **Step 5 (Full Verification)**: Run `ThemeColorCentralizationAuditTest.kt`, `TranslationParityTest.kt`, and targeted tests.

---

## 6. Verification & Rollback Plan
* **Targeted Verification**:
  ```bash
  python3 tools/parallel_sprint_lock.py ./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.sensor.*" --tests "com.atrainingtracker.trainingtracker.ui.tracking.*" --tests "com.atrainingtracker.trainingtracker.settings.*" --tests "com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest" --tests "com.atrainingtracker.trainingtracker.localization.TranslationParityTest"
  ```
* **Rollback Plan**: Revert cleanly via `git checkout feature/ATT-3542 -- .`.
