# Stage 2: Requirement & Test Specification - ATT-9343: Fix 512 MB Heap OutOfMemoryError During Dual Polar Live-IMU Streaming

**Ticket**: `ATT-9343` (`3 SP`, `Bug`, `high-risk`, `severity-critical`) | **Parent Epic**: `ATT-235` | **Target Release**: `V5.0.0`  
**Active Sprint**: `2026-11-20_03_Nacht` (Sprint 467) | **Requirement Mapping**: `REQ-STB-023` | **Test Spec ID**: `TST-STB-023` | **Status**: `[Planned]`  
**Branch**: `feature/ATT-9343` | **Sub-task**: `[Req & Test Spec]` | **Author**: AI Agent 1 (`--as agent1`, Neon) | **Date**: 2026-11-21

---

## 1. Requirement Specification (`REQ-STB-023`)

### 1.1 Problem Statement & Rationale
During dual Polar Live-IMU streaming (`200 Hz` H10 ACC + `52 Hz` Verity ACC/GYRO/PPI + `PolarRawTelemetryRecorder`), the process hit a `512 MB` Java heap `OutOfMemoryError` (`<1%` free after GC) at `15 min 17 s` inside `BleLogger.byteArrayToHex` -> `Arrays.copyOf` (`binder:24751_7`). Forensic audit (`ATT-9343_analysis.md`) proved: (1) live `SpeedAndLocationDevice_ATTFused` instantiates `UnicycleOdometryEkf()` with `recordSnapshots = true` and zero production readers, retaining `~1.2–1.35 KB` per `50–400 Hz` gyro/GNSS step indefinitely in unbounded `snapshotList` while also calling `DevicesDatabaseManager.setLastActive()` SQLite `UPDATE` at `50–400 Hz`; (2) `PolarBleApiDefaultImpl` registers `BDBleApiImpl$1` with `BleLogger.setLoggerInterface(...)` even when `logger == null`, causing `~850 KB/s` Binder-thread hex `String.format`/`Arrays.copyOf` churn under global `BleLogger.mutex`; and (3) `PolarPmdStreamCoordinator`/`PolarRawTelemetryRecorder` lack explicit `DROP_OLDEST` backpressure, `onUndeliveredElement` drop counting, allocation-free CSV formatting, and `scope.cancel()` on `stop()`.

### 1.2 Attributes
- **Priority**: Critical | **Type**: Non-Functional | **Verification Method**: Unit / Integration
- **`SYS.2` / `SYS.3` Impact (`REQ-PRO-131`)**: None (`STB` is `SWE_DERIVED_PREFIXES`; external BLE/ANT+/HAL interfaces and `SYS-EL-*`/`SYS-IF-*` topology unchanged; enforces `SYSREQ-TRK-001`, `SYSREQ-TRK-003`, `SYSREQ-TRK-010`, `SYSREQ-TRK-012` memory resilience).
- **9-Language Localization**: `Localization Parity: N/A - no UI strings`.

### 1.3 Functional & Architectural Requirements
1. **`REQ-STB-023.1` (Zero Live EKF Snapshot Retention, Bounded `UnicycleOdometryEkf` Cap & Throttled `setLastActive()`)**: `SpeedAndLocationDevice_ATTFused` (`:107`) SHALL construct `UnicycleOdometryEkf(recordSnapshots = false)` so live gyro/GNSS steps retain `0` snapshots. `UnicycleOdometryEkf` MUST preserve its `@JvmOverloads` primary constructor (`recordSnapshots: Boolean = true`) for existing test/RTS callers while bounding `snapshotList` to a fixed drop-oldest ring (`MAX_SNAPSHOTS = 4096`). `SpeedAndLocationDevice_ATTFused.processWorldYawStep()` (`:826`) SHALL throttle `setLastActive()` DB updates to `>= 5,000 ms` while keeping `SpeedAndLocationDevice_ATTFused.kt <= 1,500` LoC.
2. **`REQ-STB-023.2` (Disable Polar SDK Default `BleLogger` Hex Churn)**: `PolarOfflineGattCoordinator.acquirePolarApi()` (`:321-327`) SHALL clear `BleLogger.setLoggerInterface(null)` (via testable hook defaulting to `BleLogger.setLoggerInterface(null)`) immediately after creating a new `PolarBleApi` singleton when no external logger is attached, eliminating `BleLogger.byteArrayToHex` `Arrays.copyOf`/`String.format` churn and global `BleLogger.mutex` contention on every PMD packet.
3. **`REQ-STB-023.3` (Explicit `DROP_OLDEST` Backpressure & `onUndeliveredElement` Drop Accounting)**: `PolarPmdStreamCoordinator` SHALL buffer Polar SDK stream flows via `.buffer(capacity = 64, onBufferOverflow = BufferOverflow.DROP_OLDEST)` while keeping `PolarOnlineStreamingBridge.kt <= 1,500` LoC and preserving all public signatures (`InterfaceSignatureDriftContractTest.kt:79`). `PolarRawTelemetryRecorder` (`:81`) SHALL use `Channel<RawTelemetryRow>(capacity = CHANNEL_CAPACITY, onBufferOverflow = BufferOverflow.DROP_OLDEST, onUndeliveredElement = { droppedFrameCount.incrementAndGet() })` and expose `getDroppedFrameCount(): Long` (accounting evicted elements via `onUndeliveredElement` rather than `trySend().isFailure`).
4. **`REQ-STB-023.4` (Zero-`String.format` Sidecar CSV Serialization & Clean `CoroutineScope` Teardown)**: `RawTelemetryRow` SHALL provide `appendCsvLine(sb: StringBuilder)` producing byte-identical `REQ-SET-117.2` CSV lines (`Locale.US`, 2-decimal GYRO, 1-decimal RR) without `String.format` / `Formatter` allocations, reused via one `StringBuilder` inside `PolarRawTelemetryRecorder`'s IO drain loop. `PolarRawTelemetryRecorder.stop()` SHALL cancel its private `CoroutineScope` after `writeJob?.join()` finishes while preserving empty-session file discard (`REQ-SET-117.1`).
5. **`REQ-STB-023.5` (60-Min Bounded-Heap Stress Proof & Contract Compliance)**: Stress tests simulating `>= 60 min` of dual Polar Live-IMU (`200 Hz` H10 ACC + `52 Hz` Verity ACC/GYRO + `1 Hz` PPI + `PolarRawTelemetryRecorder`) and `>= 60 min` of `50 Hz` `SpeedAndLocationDevice_ATTFused` EKF ticks MUST maintain strictly bounded retained state with zero linear growth and pass `ArchitectureConformanceContractTest` & `InterfaceSignatureDriftContractTest`.

---

## 2. Requirement Archaeology & Chesterton's Fence Audit (`REQ-PRO-022`)
- **Original Requirement ID & Target**: Net-new `REQ-STB-023` (`REQ-STB-023.1`–`.5`) in `req_process_stability_part2.md`, refining `REQ-NFR-005`, `REQ-FIL-007` (`ATT-131`), `REQ-SET-112` (`ATT-8016`), `REQ-SET-117` (`ATT-8019`), `REQ-SET-118` (`ATT-8690`), and `REQ-CON-053` (`ATT-9161`).
- **Historical Origin & Commit Trace**: `7d2836ef` (`ATT-7751`), `14935efb` (`ATT-8880`), `e8735fcb` (`ATT-8019`), `34b78941` (`ATT-8690`), `104a028f` (`ATT-9161`), `ed05d679` (`ATT-9175`).
- **Root Reason for Existing Formulation**: `UnicycleOdometryEkf` defaulted `recordSnapshots = true` for `UnicycleOdometryEkfTest` snapshot verification; `PolarOfflineGattCoordinator` assumed SDK hex logging was inactive without `setApiLogger`; `PolarRawTelemetryRecorder` used default `Channel(8192)` (`SUSPEND`) with `trySend`.
- **Preservation of Core Invariants**: `UnicycleOdometryEkf` `@JvmOverloads` signature & `UnicycleOdometryEkfTest` small-run `getSnapshots()` contract preserved; `.polar_raw.csv.gz` header/columns/precision/empty-discard byte-compatible; `PolarOfflineGattCoordinator` ref-counting & `ATT-9175` yield preserved; `ATT-9161` PMD recovery & `ATT-9176` PPI isolation preserved; `SpeedAndLocationDevice_ATTFused.kt <= 1,500` & `PolarOnlineStreamingBridge.kt <= 1,500` LoC.

---

## 3. Acceptance Criteria (Given-When-Then) & Invariants
- **AC-1 (Zero Live EKF Snapshot Leak & Bounded Ring)**: *Given* `SpeedAndLocationDevice_ATTFused` running `180,000` `50 Hz` gyro `predict()` + `3,600` GNSS updates (`60 min`), *When* complete, *Then* `device.ekf.getSnapshots()` is empty (`0`), `setLastActive()` DB writes are throttled (`<= 721` vs `180,000`), and an explicit `UnicycleOdometryEkf(recordSnapshots = true)` caps `getSnapshots().size <= MAX_SNAPSHOTS` (`4096`) retaining newest entries.
- **AC-2 (Zero Polar SDK `BleLogger` Hex Churn)**: *Given* `PolarOfflineGattCoordinator.acquirePolarApi()` instantiating a new shared `PolarBleApi` singleton, *When* acquired, *Then* `BleLogger.setLoggerInterface(null)` is invoked once per created singleton (and `BleLogger.d_hex(...)` performs zero hex formatting/callbacks).
- **AC-3 (Drop-Oldest Backpressure & `onUndeliveredElement` Accounting)**: *Given* `PolarRawTelemetryRecorder` with stalled/unstarted drain receiving `> 8,192` frames, *When* drained, *Then* `getDroppedFrameCount() == excessFrames` via `onUndeliveredElement`, newest `8,192` frames survive, CSV output is byte-identical to `toCsvLine()`, and `stop()` cancels the internal `CoroutineScope`; *Given* a slow collector in `PolarPmdStreamCoordinator`, *When* `> 64` bursts emit, *Then* `DROP_OLDEST` keeps newest frames without blocking/growing unboundedly.
- **AC-4 (60-Min Dual Polar Live-IMU Stress)**: *Given* `60 min` simulated dual Polar streaming (`200 Hz` H10 ACC = `720,000` samples + `52 Hz` Verity ACC/GYRO = `374,400` samples + `3,600` PPI beats + `PolarRawTelemetryRecorder`), *When* processed end-to-end, *Then* retained estimator/HRV/recorder structures stay strictly flat/bounded with zero OOM.

---

## 4. Test Specification (`TST-STB-023`)
- **`TST-STB-023.1` (`UnicycleOdometryEkfTest`, `SpeedAndLocationDevice_ATTFusedTest`)**: Verify `SpeedAndLocationDevice_ATTFused.ekf.getSnapshots()` stays `0` across `180,000` `50 Hz` gyro + `3,600` GNSS updates (`60 min`), `setLastActive()` is throttled (`>= 5 s`), and `UnicycleOdometryEkf(recordSnapshots = true)` bounds `getSnapshots().size` at `MAX_SNAPSHOTS` (`4096`) dropping oldest while preserving `@JvmOverloads` & existing small-run snapshot tests.
- **`TST-STB-023.2` (`PolarOfflineGattCoordinatorCoexistenceTest`)**: Verify `acquirePolarApi()` clears `BleLogger.setLoggerInterface(null)` on new singleton creation, does not throw on error, and preserves singleton ref-counting & `ATT-9175` offline yield.
- **`TST-STB-023.3` (`PolarRawTelemetryRecorderTest`, `PolarPmdStreamCoordinatorTest`)**: Verify `PolarRawTelemetryRecorder` `DROP_OLDEST` retains newest `8,192` rows and reports exact `getDroppedFrameCount()` via `onUndeliveredElement`, `RawTelemetryRow.appendCsvLine()` matches `toCsvLine()` across negative/zero/fractional ACC/GYRO/HR/RR edge cases, `stop()` cancels internal `CoroutineScope`, and `PolarPmdStreamCoordinator` applies `DROP_OLDEST` buffering (`capacity = 64`) under slow consumers without breaking `ATT-9161` recovery.
- **`TST-STB-023.4` (60-Min Dual Polar Stress & Contracts)**: Add `PolarDualImuBoundedHeapStressTest` simulating `60 min` dual Polar (`200 Hz` H10 + `52 Hz` Verity ACC/GYRO + PPI + `PolarRawTelemetryRecorder` + `HrvRollingEngine`), asserting bounded footprint/flat heap & `0` errors; verify `ArchitectureConformanceContractTest` (`SpeedAndLocationDevice_ATTFused.kt <= 1,500`, `PolarOnlineStreamingBridge.kt <= 1,500`), `InterfaceSignatureDriftContractTest`, and `verify_requirement_governance.py`.

---

## 5. Traceability Matrix

| Requirement | Description | Test Case | Target Suites | Status |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-STB-023.1` | Live EKF `recordSnapshots = false`, bounded `MAX_SNAPSHOTS`, throttled `setLastActive()` | `TST-STB-023.1` | `UnicycleOdometryEkfTest`, `SpeedAndLocationDevice_ATTFusedTest` | `[Planned]` |
| `REQ-STB-023.2` | Disable Polar SDK `BleLogger.setLoggerInterface(null)` on singleton init | `TST-STB-023.2` | `PolarOfflineGattCoordinatorCoexistenceTest` | `[Planned]` |
| `REQ-STB-023.3` | `DROP_OLDEST` PMD flow & recorder channel + `onUndeliveredElement` drop count | `TST-STB-023.3` | `PolarRawTelemetryRecorderTest`, `PolarPmdStreamCoordinatorTest` | `[Planned]` |
| `REQ-STB-023.4` | Zero-`String.format` `appendCsvLine()` & `scope.cancel()` on `stop()` | `TST-STB-023.3` | `PolarRawTelemetryRecorderTest` | `[Planned]` |
| `REQ-STB-023.5` | 60-min dual Polar + EKF stress proof, `<= 1,500` LoC & signature contracts | `TST-STB-023.4` | `PolarDualImuBoundedHeapStressTest`, `ArchitectureConformanceContractTest`, `InterfaceSignatureDriftContractTest` | `[Planned]` |
