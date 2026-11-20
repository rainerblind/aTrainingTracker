# Stage 2: Requirement & Test Specification - ATT-8990

**Ticket**: [ATT-8990](https://rainerblind.atlassian.net/browse/ATT-8990) | **Parent Epic**: [ATT-3539](https://rainerblind.atlassian.net/browse/ATT-3539)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-11-20_01_Früh` | **Branch**: `feature/ATT-8990`  
**Requirement Mapping**: `REQ-HRV-050` (refines `REQ-TRK-060.1`/`.5`, `REQ-TRK-061`, `REQ-HRV-048.3`) | **Test Spec ID**: `TST-HRV-050` | **Date**: 2026-11-20

---

## 1. Requirement Specification (`REQ-HRV-050`)

### 1.1 Attributes & System Traceability
- **System Requirement(s)**: Derived (SW-only) — Per-device HRV source selection (`DeviceType.HRM`), `HRV_PRIORITY` best-sensor preservation, lazy per-device live HRV (`HRV_DFA_A1`, `HRV_RMSSD`, `HRV_ARTIFACT_RATE`, rolling `HRV_SDNN`), and isolated cockpit telemetry lookup (refines `SYSREQ-TRK-012`; zero `SYS.2`/`SYS.3` impact).
- **Priority**: Medium | **Type**: Functional | **Verification Method**: Unit, Integration
- **Localization Parity**: `N/A - no new UI strings`.

### 1.2 Functional & Architectural Requirements
1. **`REQ-HRV-050.1` (`DeviceType.HRM` Source Mapping & `MySensorManager` Best-Sensor Arbitration)**:
   - `DeviceType.getDeviceTypeList(SensorType)` SHALL map `HRV_RMSSD`, `HRV_DFA_A1`, `HRV_ARTIFACT_RATE`, and `HRV_SDNN` to `DeviceType.HRM` (retaining enum constant `DeviceType.HRV_METRICS` for global `HRVMetricsDevice`) so `DevicesDatabaseManager.getDeviceIdAndNameLists()`, `EditSensorFieldDialog`, and `SensorSourceDialog` list all paired HR sensors alongside `"Bester Sensor"` (`-1` / `null`).
   - `MySensorManager` SHALL define `HRV_PRIORITY = { DeviceType.HRV_METRICS, DeviceType.HRM }` and register it in `setPriorityList(...)` and `getPriorityArray(...)` for all 4 `HRV_*` types so `"Bester Sensor"` (`deviceName == null`), `BANALService.getBestSensorData()`, and `TrackerService` ALWAYS bind to the `ECG > ANT+ > PPG` arbitrated global `HRVMetricsDevice`, while pinned cockpit tiles (`deviceName != null`) resolve via `FilterManager.resolveSensor()` -> `DeviceManager.getMyDeviceByName(deviceName).getSensor(sensorType)`.
2. **`REQ-HRV-050.2` (Shared `HrvRollingEngine`, Live Rolling `HRV_SDNN` & `HrvTelemetryProvider`)**:
   - Extract causal 180s-context HRV evaluation (`WINDOW_FILTER_CONTEXT_MS = 180_000L`, `WINDOW_DFA_MS = 120_000L`, `WINDOW_RMSSD_MS = 60_000L`, `120 s` drain-to-null, configurable `[3.0, 5.0]%` artifact gate with `5.0%` fallback, `HrvSignalQualityAnalyzer`) into `HrvRollingEngine` (`@JvmOverloads`).
   - Both `HRVMetricsDevice` (`getHrvSdnnSensor()`) and per-device controllers SHALL publish live rolling `SensorType.HRV_SDNN` over active `120 s` `dfaWindowBeats` as unbiased sample SD (`sqrt(sum((nn_i - mean)^2) / (N - 1))`) of valid non-artifact NN intervals (`!isArtifact`, `correctedRrMs`), requiring `>= 10` valid beats (`MIN_SDNN_VALID_BEATS = 10`) AND `artifactRate <= effectiveGateThreshold` (`[3.0, 5.0]%`, aligned with `HrvPostWorkoutRecomputer.computeSessionSdnn`), emitting `null` when gated, insufficient, or drained.
   - Expose `HrvTelemetryProvider` (`signalQualityResult: HrvSignalQualityResult`, `latestArtifactRatePercent: Double?`) and `HrvDeviceLookup.resolveProvider(deviceManager, deviceName)` so `DeviceManager.java`, `TrackerService.java`, and `DevicesDatabaseManager.java` require **0 LoC edits**.
3. **`REQ-HRV-050.3` (Lazy Per-Device HRV Sensors, Disconnect Stale-Value Guard & Scheduler Lifecycle)**:
   - `PerDeviceHrvController` (`@JvmOverloads`, injectable `TimeProvider` & `startScheduler: Boolean = true`) SHALL attach to `BTLEHeartRateDevice`, `ANTHeartRateDevice`, and `PolarOnlineStreamingBridge` (all implementing `HrvTelemetryProvider`) via Java-compatible host callbacks (`addAndRegisterSensors`, `unregisterSensor`, `removeSensor`, `notifySensorsChanged`).
   - **Rule 52 Lazy Registration & Single Broadcast**: Zero HRV sensors and zero scheduler tasks SHALL be created before the first RR beat; on first RR beat, batch-register all 4 `MySensor<Double>` instances (`HRV_RMSSD`, `HRV_DFA_A1`, `HRV_ARTIFACT_RATE`, `HRV_SDNN`) bound to the host `MyDevice` via one `addAndRegisterSensors(list)` call (single `SENSORS_CHANGED`) and lazily start the `5 s` scheduler.
   - **Disconnect Stale-Value Guard & Rule 51 Reset**: On `unregisterSensors()`, `onAccumulatorsReset()` (overridden on all 3 HRM devices), and `shutDown()`, `PerDeviceHrvController` MUST synchronize state, cancel the scheduler task cleanly, push `newValue(null)` to all 4 HRV `MySensor`s **while still activated** *before* `deactivateSensor()`/`unregisterSensor()`/`removeSensor()` (clearing pinned tiles to `--`/`--.--`), reset rolling state, and re-arm cleanly on reconnect without thread leaks across GATT/ANT+/Polar/scheduler/UI threads.
4. **`REQ-HRV-050.4` (Per-Device Cockpit Isolation, `HRV_SDNN` Disclaimer & Ramp-Test Guard)**:
   - Add `val sourceDeviceName: String? = null` to `SensorFieldState`; replace scalar `latestArtifactRate` in `TrackingViewModel` with per-device tracking (`latestArtifactRateByDevice[deviceName] ?: HrvDeviceLookup.resolveProvider(dm, deviceName)?.latestArtifactRatePercent`) so pinned `HRV_DFA_A1` tiles gate accurately even without a co-located `HRV_ARTIFACT_RATE` tile.
   - Resolve `HrvSignalQualityStatus` per `deviceName` via `HrvDeviceLookup.resolveProvider(...)` in `TrackingViewModel` and `EditSensorFieldDialog.kt:660` (`deviceName = if (uiState.selectedDeviceId == -1L) null else deviceList.find { it.first == uiState.selectedDeviceId }?.second`).
   - Add `SensorType.HRV_SDNN` to `isHrvMetric` in `SensorFieldView.kt` & `EditSensorFieldDialog.kt`; guard `HrvRampTestViewModel` to ignore `FilteredSensorData` where `data.deviceName != null`.

### 1.3 Acceptance Criteria (Given-When-Then)
- *Given* paired HRMs (`Polar H10`, `Polar Verity Sense`), *When* `DeviceType.getDeviceTypeList` and `DevicesDatabaseManager.getDeviceIdAndNameLists` run for `HRV_RMSSD`, `HRV_DFA_A1`, `HRV_ARTIFACT_RATE`, `HRV_SDNN`, *Then* `DeviceType.HRM` is returned and both sensors appear alongside `"Bester Sensor"` in `EditSensorFieldDialog` & `SensorSourceDialog`.
- *Given* two connected HRMs emitting distinct RR streams concurrently, *When* both compute live HRV via `PerDeviceHrvController` and `FilterManager` resolves pinned tiles vs `"Bester Sensor"`, *Then* pinned tiles receive each device's distinct `HRV_DFA_A1`, `HRV_RMSSD`, `HRV_ARTIFACT_RATE`, and rolling `HRV_SDNN`, while `"Bester Sensor"` (`deviceName == null`) stays bound to `ECG > ANT+ > PPG` arbitrated `HRVMetricsDevice`.
- *Given* an active HRM disconnects or `RESET_ACCUMULATORS_INTENT` fires, *When* `unregisterSensors()`/`onAccumulatorsReset()`/`shutDown()` runs, *Then* all 4 per-device HRV sensors emit `null` before deactivation/unregistration, the scheduler cancels cleanly, and reconnecting re-arms fresh calculation.
- *Given* side-by-side pinned `HRV_DFA_A1 [Polar H10 (VALID, 1% art)]` and `HRV_DFA_A1 [Verity Sense (SMOOTHED_STREAM, 12% art)]` tiles (with no `HRV_ARTIFACT_RATE` tile) plus active `HrvRampTestViewModel`, *When* telemetry ticks arrive, *Then* Polar H10 displays zones un-gated, Verity Sense suppresses zones and shows artifact gating, and `HrvRampTestViewModel` reads only `deviceName == null`.

### Requirement Archaeology & Chesterton's Fence Audit
- **Original Requirement ID & Target**: `REQ-TRK-060.1`/`.5`, `REQ-TRK-061`, `REQ-HRV-048.3`, `REQ-TRK-085` (`DeviceType.java`, `HRVMetricsDevice.kt`, `MySensorManager.java`, `TrackingViewModel.kt`, `SensorTypeHrvTest.kt`).
- **Historical Origin & Commit Trace**: `ATT-3541` (`4bad7272`), `ATT-8706` (`7dcca5e7`, `6016d5fc`), `ATT-9160` (`bd7d6627`).
- **Root Reason for Existing Formulation**: Only global `HRVMetricsDevice` existed originally; `HRV_SDNN` was post-workout only (`HrvPostWorkoutRecomputer.kt:315`).
- **Preservation of Core Invariants**: Retains `DeviceType.HRV_METRICS` for global `HRVMetricsDevice` under `HRV_PRIORITY`; preserves `ECG > ANT+ > PPG` arbitration, Rule 51/52, 180s context + 120s drain-to-null, NeuroKit2 `0.2.10` golden parity, `@JvmOverloads`, and `0` LoC added to `DeviceManager.java` (`1,707/1,708`), `TrackerService.java` (`3,538/3,559`), and `DevicesDatabaseManager.java` (`1,669/1,671`).

---

## 2. Test Specification (`TST-HRV-050`)
- **`TST-HRV-050.1` (`SensorTypeHrvTest.kt`, `MySensorManagerHrvPriorityTest.kt` — Source Mapping & Priority)**: Verify all 4 `HRV_*` sensors map to `DeviceType.HRM`; verify `MySensorManager` `HRV_PRIORITY = { HRV_METRICS, HRM }` keeps `"Bester Sensor"` on `HRVMetricsDevice` even when `HRM` devices register HRV sensors first; verify `FilterManager.resolveSensor()` routes pinned `deviceName` to the named HRM device and `null` to `HRVMetricsDevice`.
- **`TST-HRV-050.2` (`HRVMetricsDeviceTest.kt`, `HrvRollingEngineTest.kt`, `HrvGoldenOracleTest.kt` — Rolling Engine & Live `HRV_SDNN`)**: Verify `HrvRollingEngine` and `HRVMetricsDevice.getHrvSdnnSensor()` compute unbiased $N-1$ rolling `HRV_SDNN` over 120s non-artifact NN intervals (`>= 10` valid beats), suppress `HRV_SDNN` to `null` when artifact rate exceeds configured `[3.0, 5.0]%` gate or `< 10` beats or `+150 s` drained, expose `latestArtifactRatePercent` & `signalQualityResult`, and keep `HrvGoldenOracleTest` bit-identical.
- **`TST-HRV-050.3` (`PerDeviceHrvControllerTest.kt`, `BTLEHeartRateDeviceTest.kt`, `ANTHeartRateDeviceTest.kt`, `PolarOnlineStreamingBridgeTest.kt` — Per-Device Lifecycle & Parallel Streams)**: Verify Rule 52 lazy single-batch registration on first RR beat across all 3 HRM types, parallel independent live HRV values for 2 concurrent HRMs, `newValue(null)` pushed *while still activated* before deactivation/unregistration on disconnect/`onAccumulatorsReset()`/`shutDown()`, clean scheduler cancellation & reconnect re-arming, and thread-safe concurrent beat/calculate/reset calls.
- **`TST-HRV-050.4` (`TrackingViewModelHrvTest.kt`, `HrvRampTestViewModelTest.kt`, Contracts — Cockpit Isolation & Governance)**: Verify side-by-side pinned `HRV_DFA_A1` tiles isolate `hrvSignalQualityStatus`, zone suppression, and `isArtifactGated` (including without co-located `HRV_ARTIFACT_RATE` tile via `HrvDeviceLookup`), verify `HRV_SDNN` info-sheet inclusion, verify `HrvRampTestViewModel` ignores `deviceName != null`, and verify `ArchitectureConformanceContractTest` (`0` LoC added to `DeviceManager.java`, `TrackerService.java`, `DevicesDatabaseManager.java`), `DeadCodeEliminationContractTest`, and `InterfaceSignatureDriftContractTest`.

---

## 3. Traceability Matrix

| Test Case | Scope | Target Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-HRV-050.1` | Unit | `DeviceType`, `MySensorManager`, `FilterManager` | `REQ-HRV-050.1` | Specified |
| `TST-HRV-050.2` | Unit / Oracle | `HrvRollingEngine`, `HRVMetricsDevice`, `HrvTelemetryProvider` | `REQ-HRV-050.2` | Specified |
| `TST-HRV-050.3` | Unit / Integration | `PerDeviceHrvController`, `BTLEHeartRateDevice`, `ANTHeartRateDevice`, `PolarOnlineStreamingBridge` | `REQ-HRV-050.3` | Specified |
| `TST-HRV-050.4` | ViewModel / Contracts | `TrackingViewModel`, `EditSensorFieldDialog`, `SensorFieldView`, `HrvRampTestViewModel`, Contracts | `REQ-HRV-050.4` | Specified |
