# Stage 2: Requirement & Test Specification - ATT-3907: Real-Time Live HR Drift Metric

**Ticket**: [ATT-3907](https://rainerblind.atlassian.net/browse/ATT-3907)  
**Parent Epic**: [ATT-3903](https://rainerblind.atlassian.net/browse/ATT-3903) (*Dynamical Power-to-Heart-Rate System Identification*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.1` | **Branch**: `feature/ATT-3907`  
**Requirement Mapping**: `REQ-REV-012` (*Real-Time Live HR Drift Metric & Tracking Cockpit Integration*)  
**Test Spec ID**: `TST-REV-013`  
**Author**: AI Agent 1 (Implementer, `subagent_flash`) | **Date**: 2026-11-10  

---

## 1. Requirement Specification (REQ-REV-012)

### 1.1 Problem Statement & Rationale
During endurance cycling, cardiac drift ($\text{HR}_{\text{actual}}(t) - \text{HR}_{\text{fresh\_model}}(t)$) provides immediate, power-normalized indicator of autonomic fatigue and thermal stress. Static ratios fail on variable terrain due to cardiorespiratory response latency ($\tau \approx 25\text{--}45\text{ s}$). The system SHALL compute real-time lag-compensated cardiac drift at 1 Hz in `CyclingPowerMetricsDevice.java`, publish `SensorType.LIVE_HR_DRIFT`, and integrate it into the customizable tracking cockpit with semantic color-coding.

### 1.2 Functional & Architectural Requirements
1. **Sensor Definition (`SensorType.java`)**:
   - `LIVE_HR_DRIFT` SHALL be defined in `SensorCategory.HEART_RATE_AND_ENERGY`, formatted via `SignedIntegerFormatter` (`+%d` / `%d bpm`), `SensorValueType.INTEGER`, and assigned `R.drawable.ic_heart_rate`.
2. **Lag-Compensated Computation (`CyclingPowerMetricsDevice.java`)**:
   - At 1 Hz during active pedaling ($P(t) \ge 0\text{ W}$, $\text{HR}(t) > 0$), propagate discrete state $x_k = \alpha x_{k-1} + (1-\alpha) P_k$ ($\alpha = \exp(-1.0/\tau)$) using baseline $(\tau, \text{HR}_0, K)$ seeded from recent qualified workouts ($R^2 \ge 0.50$, default fallback $\tau = 40\text{ s}$, $\text{HR}_0 = 90\text{ bpm}$, $K = 0.20$).
   - Compute instantaneous residual $\text{drift}_k = \text{HR}_k - (\text{HR}_0 + K x_k)$ and emit smoothed average over a 30-sample FIFO window.
   - *Growing Window (Rule 40)*: During warm-up ($1 \le k < 30$), average over available samples to prevent 30s blank `"--"` delay.
   - *Sensor-Presence Gating (Rule 52)*: Computation and emissions SHALL be strictly gated on active `SensorType.POWER` and `SensorType.HR`, and `enableCardioPowerDynamics == true`. Unlinked or non-power sessions emit `null`.
   - *Lifecycle Parity (Rule 51)*: `resetSession()` and constructor initialize state identically; long pause (> 30s) flushes residual FIFO.
3. **Cockpit Integration & Semantic Zones**:
   - Wire `LIVE_HR_DRIFT` into `SensorFieldState.kt` and `EditSensorFieldViewModel.kt`.
   - `TrackingViewModel.kt` SHALL map drift to semantic zone colors: $\le +3\text{ bpm}$ (neutral/green `Zone2`), $+4\dots+8\text{ bpm}$ (moderate `Zone3`), $\ge +9\text{ bpm}$ (elevated `Zone4`).
4. **9-Locale Localization Parity**:
   - `live_hr_drift` and `live_hr_drift_short` in `strings_cardio_power.xml` across all 9 locales without inline emojis.

### 1.3 Acceptance Criteria (Given-When-Then)
* **AC 1 (Live Drift Emission)**: *Given* active Power & HR sensors and `enableCardioPowerDynamics == true`, *When* cycling power fluctuates, *Then* `CyclingPowerMetricsDevice` emits `LIVE_HR_DRIFT` at 1 Hz with signed integer format without initial 30s blank delay.
* **AC 2 (Rule 52 Sensor Gating)**: *Given* non-power workouts or disconnected HR straps, *When* tracking is active, *Then* `LIVE_HR_DRIFT` emits `null` (`"--"`).
* **AC 3 (Cockpit Accent Zones)**: *Given* cockpit tile with `LIVE_HR_DRIFT`, *When* drift crosses $+4\text{ bpm}$ and $+9\text{ bpm}$, *Then* cell renders semantic accent colors.
* **AC 4 (9-Locale Parity)**: *Given* all 9 app locales, *When* running `TranslationParityTest`, *Then* all string tokens exist with 100% parity.

---

## 2. Test Specification (TST-REV-013)

### Test Case 1: Real-Time Filter & Propagation Unit Test (`[TST-REV-013.1]`)
* **Scope**: `CyclingPowerMetricsDeviceTest.kt`. Verify 1st-order state propagation ($x_k = \alpha x_{k-1} + (1-\alpha) P_k$), lag compensation on step power, 30s growing-window residual calculation, and signed integer emission.

### Test Case 2: Sensor Gating & Lifecycle Governance Test (`[TST-REV-013.2]`)
* **Scope**: `CyclingPowerMetricsDeviceGatingTest.kt`. Verify Rule 51 constructor parity, Rule 52 gating on POWER+HR, null suppression when unlinked or disabled, and > 30s pause flush.

### Test Case 3: Formatter & SensorType Contract Test (`[TST-REV-013.3]`)
* **Scope**: `SignedIntegerFormatterTest.kt`, `SensorTypeTest.kt`. Verify `SignedIntegerFormatter` handles positive (`+5`), zero (`0`), negative (`-2`), and null/NaN (`--`), and verify `SensorType.LIVE_HR_DRIFT` category, icon, and parcelable.

### Test Case 4: Cockpit Grid & Semantic Color State Test (`[TST-REV-013.4]`)
* **Scope**: `TrackingViewModelLiveDriftTest.kt`. Verify drift ranges ($\le +3$, $+4\dots+8$, $\ge +9$) map to correct semantic zone colors.

### Test Case 5: 9-Locale Localization Parity Test (`[TST-REV-013.5]`)
* **Scope**: `TranslationParityTest.kt` verifying `strings_cardio_power.xml` across EN, DE, ES, FR, IT, JA, NL, PL, PT.

---

## 3. Traceability Matrix

| Test Case | Scope | Target / Assertion | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `[TST-REV-013.1]` | Unit | `CyclingPowerMetricsDeviceTest`: 1st-order state propagation & growing window | `REQ-REV-012` | Specified |
| `[TST-REV-013.2]` | Governance | `LiveHrDriftGatingTest`: Rules 40, 51, 52 sensor-presence & pause gating | `REQ-REV-012` | Specified |
| `[TST-REV-013.3]` | Contract | `SignedIntegerFormatterTest`: Signed formatting & SensorType metadata | `REQ-REV-012` | Specified |
| `[TST-REV-013.4]` | UI / ViewModel | `TrackingViewModelLiveDriftTest`: Drift range semantic zone color mapping | `REQ-REV-012` | Specified |
| `[TST-REV-013.5]` | Localization | `TranslationParityTest`: 9-locale parity for `strings_cardio_power.xml` | `REQ-REV-012`, `REQ-LOC-001` | Specified |
