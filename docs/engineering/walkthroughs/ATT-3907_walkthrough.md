# ATT-3907 Walkthrough: Real-time Live HR Drift Metric in CyclingPowerMetricsDevice & Tracking Cockpit

**Ticket**: [ATT-3907](https://rainerblind.atlassian.net/browse/ATT-3907)  
**Parent Epic**: [ATT-3903](https://rainerblind.atlassian.net/browse/ATT-3903) (*Dynamical Power-to-Heart-Rate System Identification*)  
**Sprint**: `2026-46.1` | **Target Release**: `V5.0.0` | **Status**: Verified  
**Requirement Mapping**: `REQ-REV-012` | **Test Mapping**: `TST-REV-013`  

---

## 1. Executive Summary

`ATT-3907` delivers real-time, lag-compensated cardiac drift telemetry during cycling workouts by coupling instantaneous power $P_k$ to the dynamical first-order HR model baseline $(\tau, \text{HR}_0, K)$ identified in Epic `ATT-3903`.

Key deliverables implemented and verified:
- **Sensor Subsystem & Formatting (`REQ-REV-012.1`)**: Created `SignedIntegerFormatter` formatting values with explicit signs (`+X`, `-X`, `0`). Registered `SensorType.LIVE_HR_DRIFT` under `SensorCategory.HEART_RATE_AND_ENERGY` with `R.drawable.ic_heart_rate` and added to `ActivityType`.
- **1-Hz Dynamical State Propagation (`REQ-REV-012.2`)**: Implemented discrete lag propagation $x_k = \alpha x_{k-1} + (1 - \alpha) P_k$ ($\alpha = \exp(-\Delta t / \tau)$) and smoothed 30-sample growing-window residual calculation ($\text{HR}_{\text{actual}} - (\text{HR}_0 + K \cdot x_k)$) in `CyclingPowerMetricsDevice` (Rule 40).
- **Invariant & Gating Governance (`REQ-REV-012.3`)**: Enforced strict gating: drift is emitted only when both `POWER` and `HR` sensors are actively connected and `enableCardioPowerDynamics` is enabled. Handled session reset parity (Rule 51) and pause-recovery flush (resets filter state after pause $> 30$s). Fallback defaults $(\tau=40\text{s}, \text{HR}_0=90\text{ bpm}, K=0.20\text{ bpm/W})$ ensure graceful operation when no baseline exists.
- **Cockpit UI & Semantic Zone Colors (`REQ-REV-012.4`)**: Integrated `enableCardioPowerDynamics` visibility gating in `SensorFieldState` and `EditSensorFieldViewModel`. Mapped semantic zone colors in `TrackingViewModel`: $\le 3$ bpm $\to$ Zone 2 (fresh/neutral), $4\text{--}8$ bpm $\to$ Zone 3 (moderate drift), $\ge 9$ bpm $\to$ Zone 4 (high fatigue).

---

## 2. Traceability Matrix

| Requirement | Test Specification | Production Code | Verification Tests | Status |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-REV-012.1` | `TST-REV-013.3` | `SignedIntegerFormatter.java`, `SensorType.java` | `SignedIntegerFormatterTest` | **Verified** |
| `REQ-REV-012.2` | `TST-REV-013.1` | `CyclingPowerMetricsDevice.java` | `CyclingPowerMetricsDeviceLiveHrDriftTest` | **Verified** |
| `REQ-REV-012.3` | `TST-REV-013.2` | `CyclingPowerMetricsDevice.java` | `CyclingPowerMetricsDeviceLiveHrDriftTest` | **Verified** |
| `REQ-REV-012.4` | `TST-REV-013.4` | `TrackingViewModel.kt`, `SensorFieldState.kt` | `TrackingViewModelLiveDriftTest` | **Verified** |
| `REQ-REV-012.5` | `TST-REV-013.5` | `strings_cardio_power.xml`, `SensorCategory` | `TranslationParityTest`, `SensorCategoryAndSelectorContractTest` | **Verified** |

---

## 3. Verification & Test Execution

- **Tier 1 Targeted Verification Scope**:
  - Command: `python3 tools/parallel_sprint_lock.py --timeout 1800 ./gradlew testDebugUnitTest --max-workers=2 --tests "com.atrainingtracker.banalservice.devices.*" --tests "com.atrainingtracker.banalservice.sensor.*" --tests "com.atrainingtracker.trainingtracker.physiology.*" --tests "com.atrainingtracker.trainingtracker.localization.TranslationParityTest" --tests "com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest" --tests "com.atrainingtracker.trainingtracker.ui.components.core.*" --tests "com.atrainingtracker.trainingtracker.build.*"`
  - Scope: 548 unit and contract tests across BANAL sensor devices, physiology models, 9-language translation parity, theme centralization, and SWE architecture contracts.
  - Result: **100% PASS** (0 failures, 0 errors, BUILD SUCCESSFUL in 3m 6s).
- **Living Documentation Synchronization**:
  - Shards: `docs/requirements/req_aftermath_reviews.md` (`REQ-REV-012`) & `docs/tests/tst_aftermath_reviews.md` (`TST-REV-013`) updated to `[Verified]`.
  - Indexes: Synchronized master tables via `python3 tools/verify_requirement_governance.py --sync-indexes` (Total=981, Verified=975 in requirements; Total=983, Verified=983 in tests; 100% parity).
- **Physical Device Execution**:
  - `adb devices` reported no physical devices attached. Per Rule 79, emulator launch was omitted.
