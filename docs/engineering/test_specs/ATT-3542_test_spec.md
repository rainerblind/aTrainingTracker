# Stage 2: Requirement & Test Specification - ATT-3542: Live HRV Telemetry & Aerobic Threshold Guidance

**Ticket**: [ATT-3542](https://atrainingtracker.atlassian.net/browse/ATT-3542) | **Sub-task**: `[Req & Test Spec]`  
**Parent Epic**: [ATT-3539](https://atrainingtracker.atlassian.net/browse/ATT-3539) (*HRV & Autonomic Metrics*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.1` | **Branch**: `feature/ATT-3542`  
**Requirement Mapping**: `REQ-TRK-061` (`REQ-TRK-061.1`..`REQ-TRK-061.5`) | **Test Spec ID**: `TST-TRK-061` (`TST-TRK-061.1`..`TST-TRK-061.6`)  
**Author**: AI Agent 1 (Implementer, `subagent_flash`) | **Date**: 2026-11-10  

---

## 1. Requirement Specification (`REQ-TRK-061`)

### 1.1 Problem Statement & Rationale
Athletes need live autonomic feedback in the tracking cockpit (`SensorGridScreen` / `TrackingTabGridContent`) to guide aerobic pacing and monitor fatigue. Following the delivery of real-time RR acquisition (`ATT-3540`) and artifact-corrected HRV metric engines (`ATT-3541`), the telemetry tiles must support `HRV_DFA_A1`, `HRV_RMSSD`, `RR_INTERVAL`, and `HRV_ARTIFACT_RATE` with clear category partitioning, decimal formatting with artifact blanking (`--.--`), semantic aerobic threshold zone indicators conforming to Rule 71 theme color tokens, athlete-customizable threshold boundaries, and an educational In-App HRV Info & Disclaimer Sheet conforming to Rule 72 and 9-language translation parity.

### 1.2 Functional & Architectural Requirements
* **REQ-TRK-061.1** (*Sensor Selector Exposure & Category Partitioning*): The system SHALL include `HRV_DFA_A1`, `HRV_RMSSD`, `RR_INTERVAL`, and `HRV_ARTIFACT_RATE` in available cockpit telemetry fields (`ActivityType.getSensorTypeArray()`). In `SensorType.java`, `HRV_DFA_A1` and `HRV_RMSSD` SHALL belong to `SensorCategory.HEART_RATE_AND_ENERGY`, while `RR_INTERVAL` and `HRV_ARTIFACT_RATE` SHALL belong to `SensorCategory.SYSTEM_ENVIRONMENT_AND_DIAGNOSTICS` for clear partitioning in `EditSensorFieldDialog`.
* **REQ-TRK-061.2** (*DfaA1Formatter Scaling & Artifact Blanking*): `DfaA1Formatter` SHALL format dimensionless DFA $\alpha_1$ values to 2 decimal places (`0.00`). When the value is null, uninitialized, or artifact-gated ($> 5.0\%$), the formatter SHALL emit `--.--`.
* **REQ-TRK-061.3** (*Semantic Aerobic Threshold Zone Indicators & TTColor Tokens*): In `SensorFieldView.kt` and `TrackingViewModel.kt`, `HRV_DFA_A1` SHALL display semantic zone color coding conforming to Rule 71 (zero hardcoded hex literals outside `Color.kt`):
  * **Aerobic Base ($\alpha_1 > \alpha_{1,\text{T1}}$)**: Green (`TTColor.Zone2`).
  * **Threshold Transition ($\alpha_{1,\text{T2}} \le \alpha_1 \le \alpha_{1,\text{T1}}$)**: Orange / Tempo (`TTColor.Zone3`).
  * **High Intensity / Anaerobic ($\alpha_1 < \alpha_{1,\text{T2}}$)**: Red / Anaerobic (`TTColor.Zone4`).
  * **Muted / Artifact-Gated ($> 5.0\%$)**: Neutral transparent / theme outline (`--.--`).
* **REQ-TRK-061.4** (*User-Configurable Individual Threshold Overrides*): `TuningPreferencesDataStore.kt` SHALL support athlete-customizable threshold boundaries (`dfaA1ThresholdT1` default `0.75f`, `dfaA1ThresholdT2` default `0.50f`) with validation $\alpha_{1,\text{T2}} < \alpha_{1,\text{T1}}$ and range $[0.20, 1.50]$.
* **REQ-TRK-061.5** (*In-App HRV Info & Disclaimer Sheet with 9-Language Parity*): The system SHALL provide `HrvInfoBottomSheet.kt` using `AppModalBottomSheet` (conforming to Rule 72: zero nested `verticalScroll`) explaining trailing window lag, sensor recommendations, and medical disclaimers. All UI text SHALL be partitioned in `strings_hrv.xml` with 100% translation parity across all 9 canonical languages.

### 1.3 Acceptance Criteria (Given-When-Then)
* **AC-1 (Sensor Category Partitioning)**: *Given* `EditSensorFieldDialog`, *When* browsing sensors, *Then* `HRV_DFA_A1`/`HRV_RMSSD` appear in `HEART_RATE_AND_ENERGY`, while `RR_INTERVAL`/`HRV_ARTIFACT_RATE` appear in `SYSTEM_ENVIRONMENT_AND_DIAGNOSTICS`.
* **AC-2 (DFA $\alpha_1$ Formatting & Blanking)**: *Given* an active DFA $\alpha_1$ stream, *When* $\alpha_1 = 0.824$, *Then* it formats as `"0.82"`; *When* value is null or artifact rate $>5.0\%$, *Then* it formats as `"--.--"`.
* **AC-3 (Semantic Threshold Zone Coloring)**: *Given* thresholds $\alpha_{1,\text{T1}} = 0.75$ and $\alpha_{1,\text{T2}} = 0.50$, *When* $\alpha_1 = 0.85$, *Then* `TTColor.Zone2` (Green); *When* $\alpha_1 = 0.65$, *Then* `TTColor.Zone3` (Orange); *When* $\alpha_1 = 0.45$, *Then* `TTColor.Zone4` (Red); *When* gated, *Then* neutral theme outline.
* **AC-4 (Configurable Threshold Overrides)**: *Given* custom thresholds $\alpha_{1,\text{T1}} = 0.80$ and $\alpha_{1,\text{T2}} = 0.55$, *When* updated, *Then* zone boundaries reflect immediately in the live UI without session restart.
* **AC-5 (HRV Info Sheet & 9-Locale Parity)**: *Given* athlete opening `HrvInfoBottomSheet`, *When* displayed, *Then* zero scroll clipping occurs and all text is 100% translated across EN, DE, ES, FR, IT, JA, NL, PL, PT.

---

## 2. Test Specification & Traceability Matrix (`TST-TRK-061`)

| Req ID | Test Case | Target Component | Verification Scope |
| :--- | :--- | :--- | :--- |
| `REQ-TRK-061.1` | `TST-TRK-061.1` | `SensorCategoryAndSelectorContractTest.kt`, `ActivityTypeTest.kt` | Sensor selector exposure and category partitioning for 56 sensor constants |
| `REQ-TRK-061.2` | `TST-TRK-061.2` | `DfaA1FormatterTest.kt` | 2 decimal formatting (`0.00`) and `--.--` on null/invalid/gated values |
| `REQ-TRK-061.3` | `TST-TRK-061.3` | `TrackingHrvZoneIndicatorTest.kt`, `ThemeColorCentralizationAuditTest.kt` | Zone2/Zone3/Zone4 color mapping, neutral gate, Rule 71 zero hardcoded hex |
| `REQ-TRK-061.4` | `TST-TRK-061.4` | `TuningPreferencesDataStoreTest.kt` | Default $\alpha_{1,\text{T1}}=0.75$, $\alpha_{1,\text{T2}}=0.50$, persistence, bounds validation |
| `REQ-TRK-061.5` | `TST-TRK-061.5` | `HrvInfoBottomSheetTest.kt`, `TranslationParityTest.kt` | Rule 72 modal bottom sheet scroll safety, 9-language parity across all 9 locales |
| `REQ-TRK-061` | `TST-TRK-061.6` | Targeted Test Suite | Targeted tests: `banalservice.*`, `trainingtracker.*` pass 100% |
