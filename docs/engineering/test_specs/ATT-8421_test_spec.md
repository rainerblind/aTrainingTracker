# Stage 2: Requirement & Test Specification - ATT-8421: iTPMS Persisted Baseline Wiring, Null GNSS Accuracy/Bearing Gating & Baseline Label Audit

**Ticket**: `ATT-8421` (`2 SP`, `Bug`, `Priority: Medium`, `severity-major`) | **Parent Epic**: `ATT-166` | **Target Release**: `V5.0.0`  
**Active Sprint**: `2026-11-20_01_Früh` (`465`) | **Branch**: `feature/ATT-8421` (base `00549967`)  
**Requirement Mapping**: `REQ-SEN-034` (`REQ-SEN-034.1`–`.3`)  
**Test Spec ID**: `TST-SEN-034` (`TST-SEN-034.1`–`.3`) | **Author**: AI Agent 1 (`agent1`) | **Date**: 2026-11-20

---

## 1. Requirement Specification (`REQ-SEN-034`)

### 1.1 Attributes
- **Priority**: High | **Type**: Functional | **Verification Method**: Unit
- **System Requirement(s)**: `SYSREQ-TRK-004`, `SYSREQ-TRK-010`
- **Implementation**: `app/src/main/java/com/atrainingtracker/trainingtracker/repositories/BANALServiceRepository.kt`, `app/src/main/java/com/atrainingtracker/banalservice/ui/devices/devicedata/DeviceDataRepository.kt`
- **Localization Parity**: `N/A - already localized by ATT-8422 (00549967) via R.string.devices_wheel_baseline_format across all 9 active locales (values, de, es, fr, it, ja, nl, pl, pt)` (regression guarded by `TranslationParityTest` & `EditDeviceWheelCircumferenceTest`).

### 1.2 Normative Clauses
1. **Persisted `baselineCircumference` Loading, Live Propagation & Zero-Baseline Overwrite (`REQ-SEN-034.1`)**:
   - `BANALServiceRepository` **SHALL** load per-device `DevicesDatabaseManager.getBaselineCircumference(devId)` off the main thread (`Dispatchers.IO`, via an injectable baseline loader for JVM testing) into a thread-safe cache (`ConcurrentHashMap<Long, Double>`) and apply the resolved baseline to both newly created and live cached `perDeviceItpmsEstimators[devId]` instances so `IndirectTpmsEstimator.TpmsAlertResult.LowPressure` fires when `C_start` drops `>= 0.6%` below a persisted baseline `> 0.0`.
   - Whenever an async `Dispatchers.IO` load, `DeviceDataRepository.baselineCircumferenceChanged` event, `BANALService.CALIBRATION_FACTOR_CHANGED` broadcast, `clearBreadcrumbs()`, or `TrackingMode.READY -> TRACKING` transition resolves a device baseline, `BANALServiceRepository` **MUST** push the resolved value onto the live cached `perDeviceItpmsEstimators[devId]` instance — explicitly overwriting `baselineCircumferenceMeters = 0.0` when the persisted baseline is `<= 0.0` or non-finite so stale non-zero baselines can never linger.
   - `DeviceDataRepository` **SHALL** expose a buffered reactive `SharedFlow<Long>` (`baselineCircumferenceChanged` with `extraBufferCapacity = 64, onBufferOverflow = BufferOverflow.DROP_OLDEST`, mirroring `EquipmentRepository.equipmentLinksChanged`) emitted on BOTH branches of `resetTirePressureBaseline(deviceId, circumferenceMm)` (`circumferenceMm > 0` and `null`) and when `updateDevice()` changes `baselineCircumference`.
   - `BANALServiceRepository` **MUST** register any `BANALService.CALIBRATION_FACTOR_CHANGED` receiver strictly via `registerInternalReceiver()` so plain JVM unit tests (`Build.VERSION.SDK_INT == 0`) remain safe.
2. **Strict Null/Non-Finite GNSS Accuracy & Bearing Gating with GPS-Free Dual-Wheel Isolation (`REQ-SEN-034.2`)**:
   - `BANALServiceRepository` **SHALL** remove the synthetic defaults `?: 5.0` (for `SensorType.ACCURACY`) and `?: 0.0` (for `_currentBearing.value`).
   - Section 1 (per-device GNSS `IndirectTpmsEstimator`) **SHALL** execute ONLY when `location != null`, `acc != null && acc.isFinite() && acc >= 0.0`, and `bearing != null && bearing.isFinite()`; otherwise the GNSS iTPMS sample **MUST** be skipped without fabricating gating values.
   - `BANALServiceRepository` **SHALL** scope the `location != null` and GNSS `acc`/`bearing` guards strictly to Section 1 so Section 2 (GPS-free `DualWheelTpmsEstimator`, `REQ-SEN-030.1`) continues running under `TrackingMode.TRACKING` whenever `wheelDevices.size >= 2` even when GNSS `location`, `accuracy`, or `bearing` is `null` or non-finite.
3. **Baseline Label Localization Parity Regression & Architectural Guardrails (`REQ-SEN-034.3`)**:
   - Confirm `0` hardcoded English `"Baseline"` UI labels remain in `EditDeviceDialog.kt`, `WheelCircumferenceSection.kt`, `WheelCircumferenceHistoryChart.kt`, or `EditEquipmentDialog.kt`, retaining 9-locale parity (`R.string.devices_wheel_baseline_format` from `ATT-8422`) via `TranslationParityTest` & `EditDeviceWheelCircumferenceTest`.
   - `BANALServiceRepository.kt` (`<= 1,500` LoC) and `DeviceDataRepository.kt` (`<= 1,500` LoC) **MUST** respect file-size caps with `0` edits to protected monoliths (`DevicesDatabaseManager.java`, `EditDeviceDialog.kt`, `DeviceManager.java`, `TrackerService.java`), `0` SQLite schema migrations, and `0` `src/main` file-text reads in new tests (`REQ-PRO-049.1`).

---

## 2. Requirement Archaeology & Chesterton's Fence Audit (REQ-PRO-022)
1. **Original Requirement ID & Target**: Net-new `REQ-SEN-034`, hardening runtime wiring for `REQ-TRK-050` (`ATT-4880`), `REQ-SEN-030.2` (`ATT-7753`), and verifying `REQ-SEN-033.3` (`ATT-8422`) across `BANALServiceRepository.kt` and `DeviceDataRepository.kt`.
2. **Historical Origin & Commit Trace**: `e7cc0f39` (`ATT-4880` iTPMS + `Devices.db` v8 `baselineCircumference`), `a5665f9a` (`ATT-7753` per-device estimators + `DualWheelTpmsEstimator`), `00549967` (`ATT-8422` localized `devices_wheel_baseline_format` + `WheelCircumferenceSection.kt`).
3. **Root Reason for Existing Formulation**: `ATT-4880` unit-tested `IndirectTpmsEstimator` directly with constructor baselines while `BANALServiceRepository` left default `IndirectTpmsEstimator()` and `?: 5.0` / `?: 0.0` fallbacks inside its 1-Hz coroutine loop, and `ATT-7753` placed Section 2 (`DualWheelTpmsEstimator`) inside the outer `location != null` block.
4. **Preservation of Core Invariants**: `IndirectTpmsEstimator` math/thresholds (`<= 6.0 m`, `< 3.0 deg/s`, `>= 4.0 m/s`, `2.0 km` lock, `-0.6%` low-pressure, `-1.0%` CUSUM) unchanged; `persistConvergedCircumferences()`, `clearBreadcrumbs()` session resets, `Devices.db` v8 schema, and `CALIBRATION_FACTOR` / wheel accumulators unchanged; `0` edits to protected monoliths; `0` `src/main` file reads in new tests.

---

## 3. Acceptance Criteria
* **AC-1 (`REQ-SEN-034.1`)**: *Given* a wheel sensor with persisted `baselineCircumference = 2.130 m` in `Devices.db`, *When* `BANALServiceRepository` creates/reloads its `IndirectTpmsEstimator` and `C_start` locks at `<= 2.115 m` (`-0.7%`), *Then* `TpmsAlertResult.LowPressure` fires; and *When* the baseline is reset in `DeviceDataRepository` (either `circumferenceMm > 0`, `null`, or `updateDevice`) or cleared to `0.0` in DB and reloaded via `clearBreadcrumbs()` / `READY -> TRACKING` / `CALIBRATION_FACTOR_CHANGED`, *Then* the live cached estimator's `baselineCircumferenceMeters` updates immediately (including overwriting with `0.0`).
* **AC-2 (`REQ-SEN-034.2`)**: *Given* active wheel sensor(s) under `TrackingMode.TRACKING` where GNSS `accuracy` is `null`/`NaN`/`< 0`, or `bearing` is `null`/`NaN`, or `location` is `null`, *When* `BANALServiceRepository` processes a telemetry tick, *Then* Section 1 (`IndirectTpmsEstimator`) skips the sample (zero gated distance accumulated and zero fabricated `5.0`/`0.0` values), while Section 2 (GPS-free `DualWheelTpmsEstimator` for active `FRONT` + `REAR` wheel sensors) continues processing and detecting punctures uninterrupted.
* **AC-3 (`REQ-SEN-034.3`)**: *Given* the localized baseline label from `ATT-8422` (`R.string.devices_wheel_baseline_format`) and repository changes in `BANALServiceRepository.kt` / `DeviceDataRepository.kt`, *When* full regression and governance contract suites run, *Then* `TranslationParityTest`, `EditDeviceWheelCircumferenceTest`, `ArchitectureConformanceContractTest`, `DeadCodeEliminationContractTest`, `SourceTextReadingAuditContractTest`, and `SystemLiveTrackingRequirementsContractTest` all pass with `0` monolith edits and `0` new `src/main` reads.

---

## 4. Test Specification
* **`TST-SEN-034.1` (Persisted Baseline Wiring, Live Reset Propagation & Zero-Baseline Overwrite — `BANALServiceRepositoryItpmsWiringTest.kt`)**:
  - Behavioral JVM unit tests (zero `src/main` reads) injecting a fake baseline loader (`DevicesDatabaseManager.getBaselineCircumference` delegate) into `BANALServiceRepository`:
    (a) Verify creating an estimator loads persisted `2.130 m` baseline and feeding `2.0 km` of gated riding at `2.115 m` (`-0.7%`) emits `TpmsAlertResult.LowPressure` (triggering `WorkoutNavigationEvents` `WARNING` alert).
    (b) Verify cold-start async `Dispatchers.IO` baseline hydration updates an already-instantiated `perDeviceItpmsEstimators[devId]` before `2.0 km` lock, and resolving `<= 0.0` explicitly overwrites any prior non-zero `baselineCircumferenceMeters` to `0.0`.
    (c) Verify `DeviceDataRepository.notifyBaselineCircumferenceChanged(devId)` (buffered `SharedFlow` `extraBufferCapacity = 64`), `resetTirePressureBaseline(id, mm)` / `resetTirePressureBaseline(id, null)` / `updateDevice()` baseline changes, `CALIBRATION_FACTOR_CHANGED` broadcast handling, and `clearBreadcrumbs()` / `READY -> TRACKING` reload update live cached estimators immediately (safe under `Build.VERSION.SDK_INT == 0`).
* **`TST-SEN-034.2` (Null/Non-Finite GNSS Skipping & GPS-Free Dual-Wheel Isolation — `BANALServiceRepositoryItpmsWiringTest.kt`)**:
  - Behavioral JVM unit tests invoking `BANALServiceRepository`'s `@VisibleForTesting` iTPMS tick processor:
    (a) Feed consecutive ticks with `accuracy = null`, `Double.NaN`, `-1.0`, or `bearing = null`, `Double.NaN`; assert `IndirectTpmsEstimator` accumulates `0.0 m` gated distance (`isStartLocked == false`) and never fabricates `5.0` or `0.0`.
    (b) Feed consecutive ticks with active `FRONT` + `REAR` wheel devices while `location = null`, `accuracy = null`, and `bearing = null`; assert Section 1 (`IndirectTpmsEstimator`) is skipped while Section 2 (`DualWheelTpmsEstimator`) locks `startRatio` and detects a `>= 0.8%` rear puncture completely GPS-free.
* **`TST-SEN-034.3` (9-Locale Baseline Parity & Architecture/Governance Contracts — `TranslationParityTest`, `EditDeviceWheelCircumferenceTest`, Contracts)**:
  - Execute `BANALServiceRepositoryMultiWheelTpmsTest`, `IndirectTpmsEstimatorTest`, `EditDeviceWheelCircumferenceTest`, `TranslationParityTest` (all 9 locales: `values`, `de/es/fr/it/ja/nl/pl/pt`), `ArchitectureConformanceContractTest` (`BANALServiceRepository.kt <= 1,500`, `DeviceDataRepository.kt <= 1,500`, `0` monolith growth), `DeadCodeEliminationContractTest`, `SourceTextReadingAuditContractTest`, and `SystemLiveTrackingRequirementsContractTest`.

---

## 5. Traceability Matrix

| Test Case | Scope | Test Suite(s) | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-SEN-034.1` | Persisted baseline loading (`Dispatchers.IO`), live reset propagation (`SharedFlow`/broadcast/`clearBreadcrumbs`), zero-baseline overwrite & `LowPressure` alert | `BANALServiceRepositoryItpmsWiringTest` | `REQ-SEN-034.1` | Planned |
| `TST-SEN-034.2` | Null/non-finite GNSS accuracy/bearing skip guard & GPS-free `DualWheelTpmsEstimator` isolation when `location`/`acc`/`bearing` is null | `BANALServiceRepositoryItpmsWiringTest` | `REQ-SEN-034.2` | Planned |
| `TST-SEN-034.3` | 9-locale `devices_wheel_baseline_format` regression (`ATT-8422`) & architecture/dead-code/source-read/SYS.2 contracts | `EditDeviceWheelCircumferenceTest`, `TranslationParityTest`, `BANALServiceRepositoryMultiWheelTpmsTest`, `IndirectTpmsEstimatorTest`, Governance Contracts | `REQ-SEN-034.3` | Planned |
