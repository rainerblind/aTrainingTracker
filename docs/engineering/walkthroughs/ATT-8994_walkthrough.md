# Stage 5 Verification & Walkthrough: ATT-8994 - Sensor Disconnect Filter Reset & Sparkline Flat-Line Fix

**Ticket**: [ATT-8994](https://rainerblind.atlassian.net/browse/ATT-8994) | **Severity**: Major (`2 SP`) | **Parent Epic**: [ATT-3539](https://rainerblind.atlassian.net/browse/ATT-3539)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-11-20_01_Früh` | **Branch**: `feature/ATT-8994`  
**Verified Production SHA**: `b34c62cc` (Gate 4 `ATT-9293` PASS; `Rule 7.5` doc-only Stage 5 with `0` `app/` drift: `git diff b34c62cc..HEAD -- app/` is empty)  
**Requirements / Test Spec**: `REQ-FIL-038` (`REQ-FIL-038.1`..`.4`) / `TST-FIL-038` (`TST-FIL-038.1`..`.4`) — `[Verified]`  
**Date**: 2026-11-20 | **Author**: AI Agent 1 (`--as agent1`)

---

## 1. Root Cause & Verified Solution
When multi-sensor ANT+/BLE HR straps (`Magene HR ANT+`, `Polar H10 ANT+`) disconnected, device-pinned cockpit tiles stayed frozen at their last reading (`791 ms`, `827 ms`) and fed a horizontal blue flat-line into `LiveSparklineBuffer` because: (1) `MySensor.deactivateSensor()` only set `mActivated = false` without notifying listeners `null`; (2) `ANTHeartRateDevice`, `BTLEHeartRateDevice`, and `PolarOnlineStreamingBridge` removed dynamic sensors before `super.unregisterSensors()` without calling `deactivateSensor()`; and (3) `FilterManager.rebindExistingFilters()` ignored `currentSensor == null || !isActivated`.

### Production Changes Verified on SHA `b34c62cc`
1. **`MySensor.java` (`142` LoC, `REQ-FIL-038.1`)**: `deactivateSensor()` sets `mActivated = false; mValue = null;` before iterating `CopyOnWriteArrayList<SensorListener> mSensorListeners` lock-free with `newValue(null)`.
2. **`MyAccumulatorSensor.java` (`207` LoC, `REQ-FIL-038.1`)**: Overrides `deactivateSensor()` to set `mActivated = false` silently without clearing or notifying `null`, preserving session totals (`DISTANCE_m`, `ASCENT`, `DESCENT`, `TIME_ACTIVE`, `STRIDES`, `LAP_NR`, `CALORIES`).
3. **`ProxySensor.java` (`166` LoC, `REQ-FIL-038.1`)**: Tracks `mAccumulatorBacked` (`isAccumulatorBacked()`) in constructor and `setSourceSensor()`; overrides `deactivateSensor()` silently for lock safety during `synchronized MySensorManager.shutDown()`; notifies `listenersSnapshot` with `null` strictly outside `mSourceLock` (`REQ-SEN-021.2`) on `setSourceSensor(null)` only when `previousSource != null && !(previousSource instanceof MyAccumulatorSensor)`.
4. **`ANTHeartRateDevice.java` (`409` LoC), `BTLEHeartRateDevice.java` (`361` LoC), `PolarOnlineStreamingBridge.kt` (`1,306 / 1,500` LoC, `REQ-FIL-038.2`)**: Invoke `deactivateSensor()` before `unregisterSensor()`/`removeSensor()` for all dynamic sensors (`mRrIntervalSensor`, IMU cadence/strides/VO/GCT) across `unregisterSensors()`, `unregisterImuCadenceSensors()`, and `shutDown()`.
5. **`FilterManager.kt` (`370` LoC, `REQ-FIL-038.3`, `REQ-FIL-038.4`)**: Explicitly tracks `mAccumulatorFilters` (`ConcurrentHashMap.newKeySet()`, cleared in `shutDown()`, marked on both `oldSensor` and `newSensor` in `rebindFilter()`); unifies `rebindExistingFilters()` and `createFilter()` existing-filter sync via `syncFilterBinding()` with nested `ProxySensor` loop unwrapping, retained `ProxySensor` `"Bester Sensor"` binding (`REQ-TRK-055.1`), non-`ProxySensor` `!isActivated` deferral in `createFilter()`, and live-only clearing (`InstantaneousFilter` via `newValue(null)`, `MovingAverageFilter` via `reset()`) while preserving `AverageFilter`, `MaxValueFilter`, and `mAccumulatorFilters`.
6. **Zero-Edit Audit (Protected Monoliths & Sparkline)**: `0` edits to `DeviceManager.java` (`1,707/1,708`), `TrackerService.java` (`3,538/3,559`), `DevicesDatabaseManager.java` (`1,669/1,671`), `BANALServiceRepository.kt` (`1,186`), and `SensorFieldView.kt` (`913`).

---

## 2. Verification Evidence (`Rule 7.5` Reused Gate 4 `ATT-9293` @ `b34c62cc` — `140/140 PASS`)
- **Build & Compiler Hygiene**: `:app:compileDebugKotlin`, `:app:compileDebugUnitTestKotlin`, `:app:assembleDebug` -> **PASS** with **0 new Kotlin compiler warnings**.
- **Test Execution (`140/140 PASS`, `0` failed, `0` errors, `0` skipped across all 16 suites)**:

| Test Suite | Result | Requirement / Scope |
| :--- | :---: | :--- |
| `MySensorDeactivationAndProxyTest` | `3/3 PASS` | `REQ-FIL-038.1` / `TST-FIL-038.1` |
| `MySensorManagerProxyTest` | `5/5 PASS` | `REQ-TRK-055.1` / `REQ-FIL-038.1` |
| `MyAccumulatorSensorResetToTest` | `2/2 PASS` | Accumulator preservation regression |
| `SensorAndDeviceConcurrencyStressTest` | `6/6 PASS` | `REQ-SEN-021` lock-free concurrency |
| `SpeedAndLocationDeviceDecayTest` | `14/14 PASS` | `LocationUnavailable() -> deactivateSensor()` regression |
| `FilterManagerRebindTest` | `7/7 PASS` | `REQ-FIL-038.3`, `.4` + sparkline flat-line stop |
| `FilterNullSafetyTest` | `7/7 PASS` | `REQ-FIL-011` null safety |
| `FilterResetAndPreStartTest` | `8/8 PASS` | `REQ-FIL-013` cumulative filter isolation |
| `FilterManagerAccumulatorResetTest` | `5/5 PASS` | `REQ-TRK-049.4` zero baseline reset |
| `ANTHeartRateDeviceTest` | `11/11 PASS` | `REQ-FIL-038.2` / `TST-FIL-038.2` |
| `BTLEHeartRateDeviceTest` | `14/14 PASS` | `REQ-FIL-038.2` / `TST-FIL-038.2` |
| `PolarOnlineStreamingBridgeTest` | `32/32 PASS` | `REQ-FIL-038.2` / `TST-FIL-038.2` |
| `ArchitectureConformanceContractTest` | `11/11 PASS` | Protected monolith caps (`0` edits) |
| `DeadCodeEliminationContractTest` | `6/6 PASS` | Zero dead code |
| `InterfaceSignatureDriftContractTest` | `3/3 PASS` | Zero signature drift |
| `SourceTextReadingAuditContractTest` | `6/6 PASS` | `0` `src/main` reads (`REQ-PRO-049.1`) |
| **Total** | **`140/140 PASS`** | **All 16 suites green (`0` failed, `0` skipped)** |

### Verbatim `TST-FIL-038` Method Citations (10/10 Verified in `app/src/test/`)
- `MySensorDeactivationAndProxyTest#testMySensorDeactivate_clearsValueAndNotifiesListenersNullLockFree_TST_FIL_038_1`
- `MySensorDeactivationAndProxyTest#testMyAccumulatorSensorDeactivate_staysSilentAndPreservesTotals_TST_FIL_038_1`
- `MySensorDeactivationAndProxyTest#testProxySensorDeactivateAndSetSourceNull_notifiesOnlyNonAccumulatorsAndTracksAccumulatorBacked_TST_FIL_038_1`
- `ANTHeartRateDeviceTest#testRrIntervalSensor_deactivatesAndNotifiesNullOnUnregisterAndShutDown_TST_FIL_038_2`
- `BTLEHeartRateDeviceTest#testRrIntervalSensor_deactivatesAndNotifiesNullOnUnregisterAndShutDown_TST_FIL_038_2`
- `PolarOnlineStreamingBridgeTest#testDynamicRrAndImuSensors_deactivateOnDisconnectWhileStridesPreservesTotal_TST_FIL_038_2`
- `FilterManagerRebindTest#testDevicePinnedDisconnect_clearsInstantaneousAndMovingAverage_preservesCumulativeAndStrides_andRebinds_TST_FIL_038_3`
- `FilterManagerRebindTest#testBestSensorProxyDisconnect_preservesFallback_clearsOnLastDisconnect_andRetainsProxyBinding_TST_FIL_038_3`
- `FilterManagerRebindTest#testCreateFilter_defersInactiveDevicePinnedSensor_andNeverDefersRetainedProxy_TST_FIL_038_3`
- `FilterManagerRebindTest#testDisconnectStopsFlatLineSparklineFeedingAcrossRepeatedTicks_TST_FIL_038_4`

---

## 3. Static Analysis, Localization & Governance
- **4/4 Python Safety Linters**: `check_secrets_hygiene.py`, `lint_cursor_safety.py`, `lint_lifecycle_safety.py`, `lint_safe_enum.py` -> **PASS**.
- **Localization Parity**: `N/A - no new UI strings` (`"--"` formatter reused across all 9 locales).
- **Governance & Clean-Checkout Verification**: `--sync-indexes`, `verify_requirement_governance.py --repo-root . --base-ref refs/heads/sprint/2026-11-20_01_Früh`, and `--check-verified-evidence --test-results-dir /tmp/empty_check_ATT-8994_s5` -> **EXIT `0`**.
- **Branch Preservation**: `feature/ATT-8994` kept intact (NOT merged) for Stage 6 Cold Final Review.
