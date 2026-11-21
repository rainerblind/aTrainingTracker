# Stage 1 Analysis: ATT-9343 - Fix 512 MB Heap OutOfMemoryError During Dual Polar Live-IMU Streaming

**Ticket**: [ATT-9343](https://rainerblind.atlassian.net/browse/ATT-9343) | **Parent Epic**: [ATT-235](https://rainerblind.atlassian.net/browse/ATT-235) | **Story Points**: `3 SP` (`severity-critical`, confirmed Complex — no split per `REQ-PRO-029`)  
**Target Release**: `V5.0.0` | **Sprint**: `2026-11-20_03_Nacht` (Sprint 467) | **Base SHA**: `ed05d679` (`sprint/2026-11-20_03_Nacht`, post-`ATT-9175`)  
**Date**: 2026-11-20 | **Author**: AI Agent 1 (`--as agent1`, Neon)

---

## 1. Problem Statement & Live Crash Evidence
On Pixel 10 (`5.0.0-dev-b956b379`, run `Kurze Runde var 2 #54`, `2026-11-20_173833`, `_id = 2422`) with both **Polar H10** (`200 Hz` ACC) and **Polar Verity Sense** (`52 Hz` ACC + `52 Hz` GYRO + PPI) streaming Live-IMU (`polarLiveImuEnabled = 1`) and `PolarRawTelemetryRecorder` active (`3.33 MB` `.polar_raw.csv.gz.tmp` at `17:53`), the process crashed at `15 min 17 s` (`PID 24751`) with a `512 MB` Java heap `OutOfMemoryError` (`<1%` heap free after GC) inside `BleLogger$Companion.byteArrayToHex` -> `Arrays.copyOf` (`BlePMDClient.processPmdData:159`, `binder:24751_7`).

---

## 2. Independent Peer Review & Verification (`Rules 69 & 70`)
Audited against live `ed05d679` source and `polar-ble-sdk:8.6.0` bytecode (`javap -c` on `polar-ble-sdk-8.6.0-runtime.jar`):
1. **New Primary Retained-Heap Leak Discovered (Blind Spot Outside Ticket Hypothesis) — `SpeedAndLocationDevice_ATTFused.kt:107` & `UnicycleOdometryEkf.kt:59, 124, 812-830`**:
   - `SpeedAndLocationDevice_ATTFused` instantiates `val ekf = UnicycleOdometryEkf()` with default `recordSnapshots = true`, even though `ekf.getSnapshots()` (`:797`) has **zero production callers** (`ExtendedRtsOdometrySmoother.kt:381` creates its own `UnicycleOdometryEkf(recordSnapshots = false)`).
   - Every `TYPE_GYROSCOPE` callback (`SENSOR_DELAY_GAME`: `50 Hz` nominal, up to `200–400 Hz` on Pixel IMU hardware) and every `1 Hz` GNSS update (`predict`, `updateSpeed`, `updateGnssPosition`, `updateDopplerHeading`, `updateOfflineHeading`) calls `commitSnapshot()`, allocating and retaining `21` heap objects (`OdometrySnapshot` + `2× DoubleArray(5)` + `3× Array<DoubleArray>(5)`, `~1.2–1.35 KB` retained/step) in unbounded `snapshotList`. It is never cleared across workout resets (`onAccumulatorsReset()`), only in `shutDown()` (`:1416`) when `BANALService` dies — growing linearly across service uptime until `<1%` of the `512 MB` heap remains after GC.
   - **Secondary Hot-Path Churn (`SpeedAndLocationDevice_ATTFused.kt:826`)**: `processWorldYawStep()` calls `setLastActive()` -> `DevicesDatabaseManager.setLastActive()` (`SQLiteDatabase.update` + `ContentValues` + timestamp) on **every gyro tick (`50–400 Hz`)** on the main Looper instead of throttling (`>= 5 s`).
2. **Confirmed Hypothesis 3 — Polar SDK `BleLogger` Unconditional Binder-Thread Hex Churn (`PolarOfflineGattCoordinator.kt:312`)**:
   - Bytecode disassembly proves `BDBleApiImpl.<init>` unconditionally registers `BDBleApiImpl$1` via `BleLogger.setLoggerInterface(...)` even when `BDBleApiImpl.logger == null` (`setApiLogger` never called).
   - Every incoming PMD packet (`BlePMDClient.processPmdData`) calls `BleLogger.d_hex("BlePMDClient", "pmd data:", data)`, acquiring global `synchronized(BleLogger.mutex)` across both Polar devices and running `BleLogger.byteArrayToHex(data)` (`Arrays.copyOf` + `String.format("%02X")` per byte: `~1,700` bytes/s across dual Polar IMU -> `~850 KB/s` / `~50 MB/min` GC churn on `binder` threads) before `BDBleApiImpl.log()` discards the string because `logger == null`. Calling public `BleLogger.setLoggerInterface(null)` in `PolarOfflineGattCoordinator.acquirePolarApi()` makes `BleLogger.d_hex` short-circuit before `byteArrayToHex()`.
3. **Refined Hypothesis 1 — `PolarRawTelemetryRecorder` (`PolarRawTelemetryRecorder.kt:81, 165-168, 287-296`, `RawTelemetryRow.kt:55-66`)**:
   - Already bounded via `Channel<RawTelemetryRow>(capacity = 8192)` + non-blocking `trySend()` (`3.33 MB` `.tmp` confirms IO writer drained ~304 rows/s), so it is NOT the `508 MB` retained leak.
   - However: (a) default `BufferOverflow.SUSPEND` drops *newest* instead of `BufferOverflow.DROP_OLDEST` with no dropped-frame counter; (b) `RawTelemetryRow.toCsvLine()` allocates `3× String.format(Locale.US, "%.2f", ...)` per GYRO row (`156/s`) + intermediate `String`s instead of formatting into a reusable writer `StringBuilder`; (c) `stop()` closes `channel` and joins `writeJob` without cancelling `scope` (`SupervisorJob`).
4. **Refined Hypothesis 2 — `PolarOnlineStreamingBridge`, Estimators & HRV Buffers**:
   - Verified strictly bounded: `PolarLiveRunningCadenceEstimator` (`DoubleRingBuffer(250)`, `Vector3RingBuffer(250)`), `PolarH10RunningDynamicsEstimator` (`DoubleArray(600)`, `DoubleArray(6)`), `PolarPpiStreamProcessor` (scalars), `HrvRollingEngine` (`180 s` pruned `ArrayDeque`), and Polar SDK `ChannelUtils.monitorNotifications` (`Channel(BUFFERED = 64)` + `trySend`, cleaned in `awaitClose`).
   - Gap: `PolarOnlineStreamingBridge` (`:942, 1006, 1040, 1054`) collects SDK flows without explicit `.buffer(capacity = 64, onBufferOverflow = BufferOverflow.DROP_OLDEST)` decoupling/backpressure.

---

## 3. Root Cause Summary
1. **Unbounded Retained Heap Leak (`UnicycleOdometryEkf.snapshotList`)**: `SpeedAndLocationDevice_ATTFused` leaves `recordSnapshots = true` with zero production readers and no capacity cap, retaining `~1.2–1.35 KB` per gyro/GNSS step indefinitely across `BANALService` lifetime (`<1%` heap free after GC).
2. **Binder-Thread Hex Allocation Storm & Lock Contention (`BleLogger`)**: `PolarBleApiDefaultImpl` activates `BleLogger` unconditionally with a null `PolarBleApiLogger`, triggering `Arrays.copyOf` + `String.format("%02X")` per PMD byte on every H10/Verity packet — directly tripping the fatal 16-byte OOM on `binder:24751_7`.
3. **Missing Explicit Drop-Oldest Backpressure & Writer Hygiene**: `PolarOnlineStreamingBridge` PMD flow collectors lack `.buffer(..., DROP_OLDEST)`, `PolarRawTelemetryRecorder` uses default `SUSPEND` (`trySend` drop-newest) + per-row `String.format` churn + uncancelled `scope` in `stop()`, and `SpeedAndLocationDevice_ATTFused` writes `Devices.db` `setLastActive()` at `50–400 Hz`.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit (`REQ-PRO-022`)
- **Original Requirement ID & Target**: `REQ-FIL-007` (`ATT-131`, `UnicycleOdometryEkf.kt`), `REQ-SET-112` (`ATT-8016`, `PolarOnlineStreamingBridge.kt`), `REQ-SET-117` (`ATT-8019`, `PolarRawTelemetryRecorder.kt`), `REQ-SET-118` (`ATT-8690`, `PolarOfflineGattCoordinator.kt`), `REQ-CON-053` (`ATT-9161`, `PolarPmdStreamCoordinator.kt`), `REQ-STB-020` (`ATT-8999`) & `REQ-NFR-005`.
- **Historical Origin & Commit Trace**: `7d2836ef` (`ATT-7751`) wired live `SpeedAndLocationDevice_ATTFused` before `14935efb` (`ATT-8880`) added `recordSnapshots = false` to `ExtendedRtsOdometrySmoother` (`:381`) while leaving default `true` on `SpeedAndLocationDevice_ATTFused`; `e8735fcb` (`ATT-8019`) introduced `PolarRawTelemetryRecorder` `Channel(8192)`; `34b78941` (`ATT-8690`) centralized `PolarBleApi` creation in `PolarOfflineGattCoordinator.acquirePolarApi()`.
- **Root Reason for Existing Formulation**: `UnicycleOdometryEkf.recordSnapshots` defaulted to `true` for unit tests (`UnicycleOdometryEkfTest.kt:200-203`) expecting `getSnapshots()` without explicit args; `PolarOfflineGattCoordinator` omitted `BleLogger.setLoggerInterface(null)` assuming SDK logging was off when `setApiLogger` was not called.
- **Preservation of Core Invariants**:
  - `UnicycleOdometryEkf()` default constructor MUST keep `recordSnapshots = true` (capped by bounded `maxSnapshots` ring/drop-oldest) so existing `UnicycleOdometryEkfTest` snapshot assertions pass while preventing unbounded growth; live `SpeedAndLocationDevice_ATTFused` passes `recordSnapshots = false`.
  - `.polar_raw.csv.gz` CSV header, column order, precision (`%.2f` gyro, `%.1f` RR), and empty-file discard (`REQ-SET-117.1`/`.2`) MUST stay byte-compatible.
  - `PolarOfflineGattCoordinator` singleton ref-counting, `ATT-9175` offline GATT yield, `ATT-9161` PMD `ERROR_ALREADY_IN_STATE` recovery, and `ATT-9176` Verity PPI isolation MUST stay intact.
  - Non-allowlisted LoC ceilings (`ArchitectureConformanceContractTest.kt:116`, `<= 1,500` LoC): `SpeedAndLocationDevice_ATTFused.kt` (`1,481/1,500`, `19` lines headroom) and `PolarOnlineStreamingBridge.kt` (`1,469/1,500`, `31` lines headroom) MUST stay strictly `<= 1,500` LoC.

---

## 5. Change-Request Impact Analysis (`REQ-PRO-064` / ASPICE SUP.10 & `REQ-PRO-131.4`)
- **Affected Requirements**: Reserve net-new `REQ-STB-022` / `TST-STB-022` in `docs/requirements/req_process_stability_part2.md` (`2,611/4,000` lines) and `docs/tests/tst_process_stability_part2.md` (`1,658/4,000` lines), refining `REQ-NFR-005`, `REQ-FIL-007`, `REQ-SET-117.1`, `REQ-SET-118.1`, and `REQ-CON-053`. **SYS.2/SYS.3 Impact (`REQ-PRO-131.4`)**: None — no new external hardware profiles, BLE/ANT+ services, Android FGS/HAL permissions, or `SYS-EL-*`/`SYS-IF-*` topology changes (enforces existing `SYSREQ-TRK-001`, `SYSREQ-TRK-003`, `SYSREQ-TRK-010`, `SYSREQ-TRK-012` memory resilience). `Localization Parity: N/A - no new or modified UI strings`.
- **Affected Architecture Elements**: `SpeedAndLocationDevice_ATTFused.kt` (`1,481 -> <=1,486/1,500` LoC: `recordSnapshots = false` + throttled `setLastActive()`), `UnicycleOdometryEkf.kt` (`844` LoC: bounded `maxSnapshots` ring/cap), `PolarOfflineGattCoordinator.kt` (`1,283` LoC: `BleLogger.setLoggerInterface(null)` on newly created SDK singleton), `PolarPmdStreamCoordinator.kt` (`316` LoC: bounded `DROP_OLDEST` flow buffering helper to keep `PolarOnlineStreamingBridge.kt <= 1,475/1,500` LoC), `PolarRawTelemetryRecorder.kt` (`443` LoC: `Channel(..., BufferOverflow.DROP_OLDEST)` + dropped-frame counter + reusable writer `StringBuilder` + `scope.cancel()` in `stop()`), and `RawTelemetryRow.kt` (`98` LoC: append-to-`StringBuilder` helper).
- **Verification Impact & Test Surface**: Targeted suites (`PolarRawTelemetryRecorderTest`, `PolarOfflineGattCoordinatorCoexistenceTest`, `PolarOnlineStreamingBridgeTest`, `PolarPmdStreamCoordinatorTest`, `UnicycleOdometryEkfTest`, `SpeedAndLocationDevice_ATTFusedTest`) plus new 60-min dual-Polar (`200 Hz` H10 ACC + `52 Hz` Verity ACC/GYRO + PPI + `PolarRawTelemetryRecorder`) and 60-min `50 Hz` `SpeedAndLocationDevice_ATTFused` EKF stress tests verifying strictly flat bounded heap & drop-oldest backpressure, plus `ArchitectureConformanceContractTest` & `InterfaceSignatureDriftContractTest`.
- **Risk Assessment & Invariants**: **HIGH** (`3 SP`) — touches core live sensor/odometry/BLE streaming pipelines (`SpeedAndLocationDevice_ATTFused`, `UnicycleOdometryEkf`, `PolarOfflineGattCoordinator`, `PolarOnlineStreamingBridge`, `PolarRawTelemetryRecorder`); mitigated by zero public JVM signature breaks, `<= 1,500` LoC compliance via `PolarPmdStreamCoordinator`, preserving `UnicycleOdometryEkfTest` default snapshot API, and deterministic 60-min stress tests.

---

## 6. Scope Bounding
- **In Scope**: (1) Disable EKF snapshot retention in live `SpeedAndLocationDevice_ATTFused` + add bounded `maxSnapshots` cap in `UnicycleOdometryEkf` + throttle 50 Hz `setLastActive()` SQLite writes; (2) Disable Polar SDK default `BleLogger` hex formatter in `PolarOfflineGattCoordinator.acquirePolarApi()`; (3) Enforce explicit `BufferOverflow.DROP_OLDEST` bounded buffering on `PolarPmdStreamCoordinator`/`PolarOnlineStreamingBridge` and `PolarRawTelemetryRecorder` (with dropped-frame metric, reusable `StringBuilder` CSV serialization, and `scope.cancel()` on `stop()`); (4) 60-min dual-Polar & EKF bounded-heap stress tests and living docs (`REQ-STB-022` / `TST-STB-022`).
- **Out of Scope**: Polar SDK version upgrades, CSV sidecar schema version changes, UI/localization changes, or physical Pixel 10 endurance rides (deferred to PO Ceremony 2 per `Rule 7.6`).
