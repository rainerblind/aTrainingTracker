# Stage 1 Analysis: Live HRV Metrics & Aerobic Threshold Indicators (ATT-3542)

**Ticket**: [ATT-3542](https://rainerblind.atlassian.net/browse/ATT-3542) | **Epic**: [ATT-3539](https://rainerblind.atlassian.net/browse/ATT-3539) (*HRV & Autonomic Metrics*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.1` | **Branch**: `feature/ATT-3542`  
**Author**: AI Agent 1 (`subagent_pro`) | **Date**: 2026-11-10  

---

## 1. Problem Statement & Motivation
Following backend RR streaming (`ATT-3540`) and real-time artifact correction / HRV engine (`ATT-3541`), athletes need cockpit visualization of autonomic telemetry on customizable tracking tabs (`SensorGridScreen` / `TrackingTabGridContent`). Athletes require:
1. `HRV_DFA_A1` (`0.00`) and `HRV_RMSSD` (`0 ms`) selectable as primary cardiovascular HRV metrics, while `RR_INTERVAL` (`0 ms`) and `HRV_ARTIFACT_RATE` (`0.0 %`) serve as diagnostic telemetry.
2. Semantic aerobic threshold zone visualization for `HRV_DFA_A1` based on population defaults ($\alpha_{1,\text{T1}} = 0.75$, $\alpha_{1,\text{T2}} = 0.50$) or individual overrides, with safe muted states (`--.--`) when artifact rates exceed $5.0\%$.
3. An In-App HRV Info & Disclaimer Sheet guiding athletes on the 1–2 min trailing window lag, recommending moistened BLE ECG chest straps, and stating medical/arrhythmia disclaimers.

---

## 2. Root Cause Analysis & Technical Design
* **Cockpit Field Integration**:
  * Update `ActivityType.getSensorTypeArray()` across generic HR, running, cycling, and default profiles to include `HRV_DFA_A1`, `HRV_RMSSD`, `RR_INTERVAL`, and `HRV_ARTIFACT_RATE`.
  * In `SensorType.java`, retain `HRV_DFA_A1` and `HRV_RMSSD` in `SensorCategory.HEART_RATE_AND_ENERGY`, and assign `RR_INTERVAL` and `HRV_ARTIFACT_RATE` to `SensorCategory.SYSTEM_ENVIRONMENT_AND_DIAGNOSTICS` so primary HRV metrics and technical diagnostics appear cleanly partitioned in `EditSensorFieldDialog`.
  * Provide `DfaA1Formatter.java` formatting dimensionless ratios to 2 decimal places (`0.00`) and emitting `--.--` on null / artifact-gated values.
* **Semantic Threshold Zone Indication (`ThemeColorCentralizationAuditTest` Compliant)**:
  * In `TrackingViewModel.kt`, map `HRV_DFA_A1` to canonical theme tokens without hardcoded hex colors:
    * **Aerobic Base ($\alpha_1 > \alpha_{1,\text{T1}}$)**: Green (`TTColor.Zone2`).
    * **Threshold Transition ($\alpha_{1,\text{T2}} \le \alpha_1 \le \alpha_{1,\text{T1}}$)**: Orange / Tempo (`TTColor.Zone3`).
    * **High Intensity ($\alpha_1 < \alpha_{1,\text{T2}}$)**: Red / Anaerobic (`TTColor.Zone4`).
    * **Muted / Artifact-Gated ($> 5.0\%$)**: Neutral transparent / theme outline (`--.--`).
  * Support athlete-specific thresholds via `TuningPreferencesDataStore.kt` (`dfaA1ThresholdT1` default `0.75f`, `dfaA1ThresholdT2` default `0.50f`).
* **In-App HRV Info & Disclaimer Sheet**:
  * Implement `HrvInfoBottomSheet.kt` with `AppModalBottomSheet` explaining trailing window lag, sensor recommendations, and medical disclaimers. Accessible from `EditSensorFieldDialog` and `SensorFieldView`.
  * Localize all strings in partitioned `strings_hrv.xml` across all 9 locales (`REQ-PRO-041.3`).

---

## 3. User Scope Grounding (ATT-1250) & Complexity Split Guardrail
* **In-Scope Goals**:
  * Expose HRV sensor types in `ActivityType.java` and `EditSensorFieldDialog`.
  * Implement `DfaA1Formatter.java` and update `SensorCategoryAndSelectorContractTest.kt` for 56 constants.
  * Implement `HRV_DFA_A1` semantic zone coloring and configurable thresholds in `TuningPreferencesDataStore.kt`.
  * Implement `HrvInfoBottomSheet.kt` and 9-language localized strings in `strings_hrv.xml`.
  * Propose net-new living doc specifications `REQ-TRK-061` and `TST-TRK-061`.
* **Out-of-Scope Non-Goals**:
  * Dedicated SQLite RR persistence tables (`WorkoutRrIntervals`, isolated in `ATT-3543`).
  * Post-workout centered-window threshold analytics and scatter plots (`ATT-3543` / `ATT-3544`).
  * Guided ramp test execution (`ATT-4969`).
* **Complexity Rating**: **2 SP (Standard)**. Focused Compose UI, theme token integration, and settings.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Net-new `REQ-TRK-061` and `TST-TRK-061` in `docs/requirements/req_tracking_cockpit.md` and `docs/tests/tst_tracking_cockpit.md`, extending `REQ-TRK-060` (HRV engine) and `REQ-UI-395` (Sensor Selector). Targets `SensorFieldView.kt`, `TrackingViewModel.kt`, `EditSensorFieldDialog.kt`, `HrvInfoBottomSheet.kt`.
* **Historical Origin & Commit Trace**: `ATT-3541` (`4bad7272`) delivered the real-time HRV engine; `ATT-3852` established categorized sensor selection.
* **Preservation of Core Invariants**:
  1. Theme Color Centralization: zero hardcoded hex colors outside `Color.kt` (Rule 71).
  2. Modal Bottom Sheet Scroll Safety: zero nested `verticalScroll` inside `AppModalBottomSheet` (Rule 72).
  3. Clean-room test pass rate and 100% 9-language translation parity.

---

## 5. Architectural Strategy & High-Level Solution
* **Formatting**: `DfaA1Formatter.java` for 2-decimal scaling and `--.--` artifact-muted display.
* **UI Coloring**: Dynamic tile accenting via `TTColor` tokens based on configurable $\alpha_{1,\text{T1}}$/$\alpha_{1,\text{T2}}$ thresholds.
* **Guidance**: `HrvInfoBottomSheet.kt` for educational and medical transparency.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**: Zero hex literals outside `Color.kt`; 100% translation parity in `strings_hrv.xml`; 0 regression in existing cockpit layouts.
* **Risk Rating**: **LOW**. Standard Jetpack Compose component extension with established design patterns.
