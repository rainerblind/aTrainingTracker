# Stage 1 Analysis: ATT-8994 - Sensor Disconnect & FilterManager Stale-Value / Flat-Line Sparkline Fix

**Ticket**: [ATT-8994](https://rainerblind.atlassian.net/browse/ATT-8994) | **Severity**: Major (`2 SP`)  
**Parent Epic**: [ATT-3539](https://rainerblind.atlassian.net/browse/ATT-3539) (*Heart Rate Variability (HRV)*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-11-20_01_Früh` | **Branch**: `feature/ATT-8994`  
**Author**: AI Agent 1 (`--as agent1`) | **Date**: 2026-11-20

---

## 1. Problem Statement & Motivation
When comparing multiple HR sensors live on Pixel 10 (`Polar H10 BLE/ANT+`, `Magene HR BLE/ANT+`), device-pinned tiles (`Magene HR ANT+` at `791 ms`, `Polar H10 ANT+` at `827 ms`) remained permanently frozen on their last reading and fed a horizontal flat-line sparkline for 25+ minutes after both ANT+ sensors disconnected.

---

## 2. Independent Peer Review & Verification
Audited live `sprint/2026-11-20_01_Früh` @ `32ca4857` (4-Augen check + `Rule 68` cross-caller side-effect trace):
1. **Verified Root Cause 1 — `MySensor.deactivateSensor()` (`MySensor.java:49-51`)**: Only sets `mActivated = false`, leaving `mValue` populated and never notifying `mSensorListeners` (`InstantaneousFilter`, etc.) with `null`.
2. **Verified Root Cause 2 — Dynamic Sensor Removal Without Deactivation**: `ANTHeartRateDevice.java:374-380,393-399` (`unregisterSensors`/`shutDown`, shifted from ticket lines `301-307`), `BTLEHeartRateDevice.java:328-334,346-352` (`unregisterSensors`/`shutDown`, shifted from `256-262`), and `PolarOnlineStreamingBridge.kt:1110-1135,1218-1223` (`unregisterImuCadenceSensors`/`unregisterSensors` for `mRrIntervalSensor`, `mCadenceSensor`, `mStridesSensor`, `mVerticalOscillationSensor`, `mGroundContactTimeSensor`) all unregister and `removeSensor(...)` before `super.unregisterSensors()` without calling `deactivateSensor()`. (`SpeedAndLocationDevice_ATTFused.kt:1260,1289` and `PerDeviceHrvController.kt:309` already call `deactivateSensor()`.)
3. **Verified Root Cause 3 — `FilterManager.rebindExistingFilters()` (`FilterManager.kt:213-226`)**: Only rebinds when `currentSensor != null && boundSensor != currentSensor`. When a device-pinned sensor (`deviceName != null`) disconnects (`resolveSensor` returns `null` for dynamic sensors or `!currentSensor.isActivated` for static `HR`), `FilterManager` neither unbinds the listener nor resets live filter state.
4. **Hidden Side-Effect A — Accumulator & Cumulative Workout Filter Protection (`MyAccumulatorSensor`, `AverageFilter`, `MaxValueFilter`)**: Accumulators (`MyAccumulatorSensor` subclasses: `DISTANCE_m`, `ASCENT`, `DESCENT`, `TIME_ACTIVE`, `STRIDES`, `LAP_NR`, `CALORIES`) MUST retain session totals across transient disconnects -> override `MyAccumulatorSensor.deactivateSensor()` to set `mActivated = false` WITHOUT pushing `null` to listeners. Likewise, `FilterManager` disconnect handling MUST unwrap `ProxySensor`, skip `MyAccumulatorSensor`, and NEVER call `.reset()` on cumulative session filters (`AverageFilter`, `MaxValueFilter` — `REQ-FIL-013`).
5. **Hidden Side-Effect B — Live Moving-Average Filters vs `REQ-FIL-011` (`FilterNullSafetyTest`)**: `NumberedMovingAverageFilter` and `TimedMovingAverageFilter` ignore `newValue(null)` (`REQ-FIL-011` — verified by `FilterNullSafetyTest`). Therefore `FilterManager` disconnect teardown must clear `InstantaneousFilter` via `newValue(null)` AND clear `MovingAverageFilter` (`NumberedMovingAverageFilter` / `TimedMovingAverageFilter`) via `reset()` (while leaving `AverageFilter`/`MaxValueFilter` intact and keeping `ExponentialSmoothingFilter.filteredValue: Number` signature unchanged per `FilterResetAndPreStartTest`).
6. **Hidden Side-Effect C — `"Bester Sensor"` (`ProxySensor`) Fallback vs Last-Sensor Disconnect**: For `deviceName == null`, `MySensorManager` retains `ProxySensor` (`REQ-TRK-055.1`). If a fallback sensor exists (`sourceSensor != null && sourceSensor.isActivated`), `"Bester Sensor"` seamlessly switches to it (`setSourceSensor(newBest)` pushes live value immediately — MUST NOT be unbound!). If the last non-accumulator source unregisters (`setSourceSensor(null)`), `ProxySensor.setSourceSensor(null)` notifies `listenersSnapshot` with `null` strictly outside `mSourceLock` (`REQ-SEN-021.2`), guaranteeing `"--"` even if `unregisterSensor()` is called without `deactivateSensor()`.
7. **Verified Sparkline Stop (`BANALServiceRepository.kt:937-948`)**: Line 940 already checks `val numericVal = (sensorData.value as? Number)?.toFloat() ?: continue`; once `filteredSensorData.value == null`, 1-Hz sparkline feeding stops automatically with **0 edits** to `BANALServiceRepository.kt` (`1,186` LoC) or `SensorFieldView.kt` (`913` LoC).
8. **Target File LoC Audit (`ArchitectureConformanceContractTest.kt`)**: `MySensor.java` (`138`, `+8`), `MyAccumulatorSensor.java` (`200`, `+6`), `ProxySensor.java` (`146`, `+7`), `FilterManager.kt` (`315`, `+35`), `ANTHeartRateDevice.java` (`407`, `+2`), `BTLEHeartRateDevice.java` (`359`, `+2`), `PolarOnlineStreamingBridge.kt` (`1,301/1,500`, `+5` -> `1,306/1,500`). **0 edits** to `DeviceManager.java` (`1,707/1,708`), `TrackerService.java` (`3,538/3,559`), and `DevicesDatabaseManager.java` (`1,669/1,671`).

---

## 3. User Scope Grounding (`ATT-1250`)
- **In-Scope (`2 SP` confirmed; no split)**: (1) `MySensor.deactivateSensor()` clears `mValue` & pushes `null` to listeners lock-free, overridden as silent `mActivated = false` in `MyAccumulatorSensor`; (2) `ProxySensor.setSourceSensor(null)` pushes `null` outside `mSourceLock` when unbinding a non-accumulator source; (3) Call `deactivateSensor()` before `unregisterSensor()`/`removeSensor()` for dynamic sensors in `ANTHeartRateDevice`, `BTLEHeartRateDevice`, `PolarOnlineStreamingBridge`; (4) `FilterManager.rebindExistingFilters()`/`createFilter()`/`rebindFilter()` unbinds disconnected/deactivated sensors, clears non-accumulator live filters (`InstantaneousFilter`/`MovingAverageFilter`) while preserving `MyAccumulatorSensor`/`AverageFilter`/`MaxValueFilter` & `"Bester Sensor"` fallback, and rebinds cleanly on reconnect.
- **Out-of-Scope**: No changes to `ExponentialSmoothingFilter` return type, `AverageFilter`/`MaxValueFilter` session persistence, `BANALServiceRepository.kt`, `SensorFieldView.kt`, `DeviceManager.java`, `TrackerService.java`, or UI strings.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit (`REQ-PRO-022`)
- **Original Requirement ID & Target**: `REQ-TRK-055` (`MySensorManager.java`, `ProxySensor.java`, `FilterManager.kt`), `REQ-FIL-011` & `REQ-FIL-013` (`MyFilter.kt`, `InstantaneousFilter.kt`, `MovingAverageFilter.kt`, `AverageFilter.kt`, `MaxValueFilter.kt`), `REQ-SEN-021` (`ProxySensor.java`, `MySensorManager.java`), `REQ-TRK-049.4` (`FilterManager.resetAllFilters`), `REQ-HRV-050.3` (`PerDeviceHrvController.kt`).
- **Historical Origin & Commit Trace**: `ATT-6257` / `REQ-TRK-055` retained persistent `ProxySensor`s across reconnects; `ATT-7530` (`c54768d2`) added dynamic `mRrIntervalSensor` unregistration on BLE/ANT+ disconnect; `ATT-4096` / `REQ-SEN-021` enforced lock-free listener dispatch outside `mSourceLock`; `ATT-3222` / `REQ-FIL-013` isolated cumulative `AverageFilter`/`MaxValueFilter` from rolling filters; `ATT-7905` (`c8476b91`) migrated filters to Kotlin.
- **Root Reason for Existing Formulation**: `MySensor.deactivateSensor()` pre-dated device-pinned live tiles and `LiveSparklineBuffer`; `FilterManager.rebindExistingFilters()` only handled `oldSensor -> newSensor` migration on reconnect, overlooking `active -> disconnected` teardown.
- **Preservation of Core Invariants**: Preserves `REQ-SEN-021` lock-free listener callbacks, `REQ-TRK-055` persistent `ProxySensor` retention and seamless `"Bester Sensor"` fallback, `REQ-FIL-011` null-safety, `REQ-FIL-013` cumulative `AverageFilter`/`MaxValueFilter` & `MyAccumulatorSensor` session preservation, `REQ-TRK-049.4` zero-baseline reset, and `0` edits to protected monoliths.

---

## Change-Request Impact Analysis
- **Affected Requirements**: Net-new candidate **`REQ-FIL-038`** / **`TST-FIL-038`** (not yet reserved; to be authored in Stage 2 in `docs/requirements/req_tracking_cockpit_part2.md` & `docs/tests/tst_tracking_cockpit.md`, refining `REQ-TRK-055` & `REQ-FIL-013`). **`SYS.2`/`SYS.3` System-Level Impact Check (`REQ-PRO-131.4`)**: **None — Derived SW-only** (internal sensor-deactivation and filter-rebinding lifecycle bug fix; no new external BLE/ANT+ profiles, hardware interfaces, Android permissions, or system elements).
- **Affected Architecture Elements**: `MySensor.java`, `MyAccumulatorSensor.java`, `ProxySensor.java`, `FilterManager.kt`, `ANTHeartRateDevice.java`, `BTLEHeartRateDevice.java`, `PolarOnlineStreamingBridge.kt`. Zero edits to `DeviceManager.java`, `TrackerService.java`, `DevicesDatabaseManager.java`, `BANALServiceRepository.kt`, or `SensorFieldView.kt`.
- **Verification Impact & Test Surface**: Extend `FilterManagerRebindTest.kt`, `MySensorManagerProxyTest.kt`, `ANTHeartRateDeviceTest.kt`, `BTLEHeartRateDeviceTest.kt`, `PolarOnlineStreamingBridgeTest.kt`; add dedicated disconnect/sparkline-stop/accumulator-preservation/cumulative-filter tests; verify `FilterNullSafetyTest.kt`, `FilterResetAndPreStartTest.kt`, `FilterManagerAccumulatorResetTest.kt`, `SensorAndDeviceConcurrencyStressTest.kt`, `ArchitectureConformanceContractTest.kt`, `InterfaceSignatureDriftContractTest.kt`, `DeadCodeEliminationContractTest.kt`, `SourceTextReadingAuditContractTest.kt`.
- **Risk Assessment & Invariants**: **LOW-MEDIUM** — Incorrectly clearing accumulators or cumulative workout filters (`AverageFilter`/`MaxValueFilter`), breaking `"Bester Sensor"` fallback, or holding locks during listener callbacks could regress live tracking; mitigated by `MyAccumulatorSensor` override, filter-type discrimination (`InstantaneousFilter`/`MovingAverageFilter` only), lock-free `CopyOnWriteArrayList` / outside-`mSourceLock` dispatch, and concurrency + rebind unit tests.

---

## 6. Localization Parity
- **Localization Parity**: `N/A - no new UI strings` (reuses existing `"--"` null formatter across all 9 locales).
