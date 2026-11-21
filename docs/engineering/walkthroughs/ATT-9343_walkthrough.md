# Stage 5: Walkthrough & Verification - ATT-9343: Fix 512 MB Heap OutOfMemoryError During Dual Polar Live-IMU Streaming

**Ticket**: `ATT-9343` (`3 SP`, `Bug`, `high-risk`, `severity-critical`) | **Parent Epic**: `ATT-235` (*No Crashes & App Stability*) | **Target Release**: `V5.0.0`  
**Active Sprint**: `2026-11-20_03_Nacht` (Sprint 467) | **Requirement Mapping**: `REQ-STB-023` (`REQ-STB-023.1`–`REQ-STB-023.5`) | **Test Mapping**: `TST-STB-023` (`TST-STB-023.1`–`TST-STB-023.4`)  
**Branch**: `feature/ATT-9343` (Verified Code SHA: `344ad639`; Gate 4 `ATT-9406` PASS) | **Author**: AI Agent 1 (`--as agent1`, Neon) | **Date**: 2026-11-21

---

## 1. Executive Summary & Verification Overview
Resolved the `512 MB` ART Java heap `OutOfMemoryError` observed at `15 min 17 s` during dual Polar Live-IMU streaming (`200 Hz` H10 ACC + `52 Hz` Verity ACC/GYRO/PPI + `PolarRawTelemetryRecorder`) and live `SpeedAndLocationDevice_ATTFused` tracking across 7 production files and `docs/architecture.md` (`v1.0.16`, `§3.3.3` & `§4.9`), with zero UI/localization changes (`Localization Parity: N/A - no UI strings`):
- **Live EKF & DB Throttle (`REQ-STB-023.1`)**: `UnicycleOdometryEkf.kt` bounds `snapshotList` to `ArrayDeque` (`MAX_SNAPSHOTS = 4096`, drop-oldest) while preserving `@JvmOverloads` (`recordSnapshots = true`); `SpeedAndLocationDevice_ATTFused.kt` (`1,489/1,500` LoC) instantiates `UnicycleOdometryEkf(recordSnapshots = false)` and throttles `processWorldYawStep()` `setLastActive()` writes to `>= 5,000 ms` using deterministic `timestampMs` (handling clock rewind and resetting `mLastActiveWriteMs = Long.MIN_VALUE` in `resetOriginAndEkf()`).
- **Polar SDK `BleLogger` Hex Churn Elimination (`REQ-STB-023.2`)**: `PolarOfflineGattCoordinator.kt` clears `BleLogger.setLoggerInterface(null)` (via testable `bleLoggerDisabler` reset in `resetForTesting()`) immediately after instantiating a new shared `PolarBleApi` singleton in `acquirePolarApi()`, short-circuiting `BleLogger.d_hex()` before `byteArrayToHex()`.
- **`DROP_OLDEST` Backpressure & Zero-Alloc CSV (`REQ-STB-023.3`, `REQ-STB-023.4`)**: `PolarPmdStreamCoordinator.kt` adds `collectBounded(capacity = 64, BufferOverflow.DROP_OLDEST)` with `AtomicReference` terminal-failure preservation (wired into PMD recovery and `PolarOnlineStreamingBridge.kt` HR collection at `1,469/1,500` LoC, `+0` net lines, zero public signature drift); `PolarRawTelemetryRecorder.kt` switches to `Channel(8192, BufferOverflow.DROP_OLDEST, onUndeliveredElement = { droppedFrameCount.incrementAndGet() })`, reuses a single `StringBuilder(128)` with zero-`String.format` `RawTelemetryRow.appendCsvLine()` (`NaN`/`Infinity`/`-0.0f`/`Locale.US` parity), and cancels `scope` in `stop()`.

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verified Test Methods & Scope (Gate 4 `ATT-9406` @ `344ad639`) | Result | Living Docs |
| :--- | :--- | :--- | :---: | :---: |
| `REQ-STB-023.1` | `TST-STB-023.1` | `UnicycleOdometryEkfTest.testSnapshotRingBuffer_capsAtMaxSnapshotsAndDisablesCleanly_TST_STB_023_1` (`19/19`), `SpeedAndLocationDevice_ATTFusedTest.liveAttFused_disablesEkfSnapshotRetentionAndThrottlesGyroSetLastActive_TST_STB_023_1` (`34/34`), `SpeedAndLocationDevice_ATTFusedBANALServiceIntegrationTest` (`3/3`) | **PASS** | `[Verified]` |
| `REQ-STB-023.2` | `TST-STB-023.2` | `PolarOfflineGattCoordinatorCoexistenceTest.testAcquirePolarApi_disablesDefaultBleLoggerOnSingletonCreation_TST_STB_023_2` (`10/10`) | **PASS** | `[Verified]` |
| `REQ-STB-023.3` | `TST-STB-023.3` | `PolarPmdStreamCoordinatorTest.testCollectBounded_dropsOldestFramesWhenConsumerSlow_TST_STB_023_3` (`6/6`), `PolarOnlineStreamingBridgeTest` (`43/43`), `PolarRawTelemetryRecorderTest.testDropOldestBackpressure_onUndeliveredElementCountsDropsAndCancelsScopeOnStop_TST_STB_023_3` (`8/8`) | **PASS** | `[Verified]` |
| `REQ-STB-023.4` | `TST-STB-023.3` | `PolarRawTelemetryRecorderTest.testAppendCsvLine_exactParityWithStringFormatAcrossEdgeCases_TST_STB_023_3` (`8/8`), `PolarRawTelemetryReaderTest` (`3/3`) | **PASS** | `[Verified]` |
| `REQ-STB-023.5` | `TST-STB-023.4` | `PolarDualImuBoundedHeapStressTest.testSixtyMinuteDualPolarLiveImuAndLiveEkf_maintainsBoundedHeapAndExactAccounting_TST_STB_023_4` (`1/1`, `12.39s`), `ArchitectureConformanceContractTest` (`11/11`), `InterfaceSignatureDriftContractTest` (`3/3`) | **PASS** | `[Verified]` |

---

## 3. Automated Test Evidence (Rule 7.5 Doc-Only Fast Path — Reusing Gate 4 `ATT-9406` @ `344ad639`)
Stage 5 modifies **zero** production or test source files (`344ad639` unchanged); per `Rule 7.5`, Gate 4's verified Tier 1 targeted Gradle execution (`--rerun --tests ...`, never `--rerun-tasks`) is reused directly:
```text
Command: ./gradlew :app:testDebugUnitTest --rerun \
  --tests "*UnicycleOdometryEkf*" --tests "*SpeedAndLocationDevice_ATTFused*" \
  --tests "*PolarOfflineGattCoordinator*" --tests "*PolarPmdStreamCoordinator*" \
  --tests "*PolarOnlineStreamingBridge*" --tests "*PolarRawTelemetryRecorder*" \
  --tests "*PolarRawTelemetryReader*" --tests "*PolarDualImuBoundedHeapStressTest*" \
  --tests "com.atrainingtracker.trainingtracker.build.ArchitectureConformanceContractTest" \
  --tests "com.atrainingtracker.trainingtracker.build.InterfaceSignatureDriftContractTest"
Result : BUILD SUCCESSFUL — 11 suites, 141/141 tests PASS (0 failures, 0 errors, 0 skipped, 0 compiler warnings in modified files)
```
- **60-Min Dual Polar + Live EKF Stress Proof (`PolarDualImuBoundedHeapStressTest`, `12.39s`)**: Replayed `720,000` H10 `200 Hz` ACC + `187,200` Verity `52 Hz` ACC + `187,200` Verity `52 Hz` GYRO + `3,600` PPI beats (`HrvRollingEngine` + `PolarRawTelemetryRecorder`) + `180,000` `50 Hz` live EKF predicts + `3,600` GNSS fixes + `BleLogger.d_hex` with `0` retained snapshots, exact `recorded + dropped == enqueued` accounting, cancelled `CoroutineScope`, and steady-state minute-15 vs minute-60 post-GC heap delta `< 32 MB`.
- **4/4 Safety Linters (`@ 344ad639`)**: `lint_cursor_safety.py`, `lint_lifecycle_safety.py`, `lint_safe_enum.py`, and `check_secrets_hygiene.py` all **PASS**.

---

## 4. Device / Visual Verification Scope (`Rule 7.6`, `Rule 8.5`)
- **Non-UI Backend Service & DSP Pipeline Ticket**: Zero UI, Compose, layout, drawable, or `strings*.xml` changes (`No UI changes.`). Per `stage5-verification/SKILL.md` §2, Stage 5 skips emulator screenshot capture; independent Stage 6 Cold Final Review (`Rule 3.3`) will perform live `emulator-5554` deployment/logcat verification prior to atomic `--merge-ticket`.

---

## 5. Invariant & Governance Verification
1. **LoC & Signature Contracts**: `SpeedAndLocationDevice_ATTFused.kt` = **`1,489/1,500` LoC**; `PolarOnlineStreamingBridge.kt` = **`1,469/1,500` LoC** (`+0` net lines); `ArchitectureConformanceContractTest` (`11/11`) & `InterfaceSignatureDriftContractTest` (`3/3`) **PASS**.
2. **SWE.2 & Living Docs Parity (`Rule 8.8`, `Rule 8.9`)**: `docs/architecture.md` (`v1.0.16`, `§3.3.3` & `§4.9`), `req_process_stability_part2.md` (`v1.0.51`), `tst_process_stability_part2.md` (`v1.0.44`), `docs/requirements.md` (`v1.0.99`), `docs/tests.md` (`v1.0.99`) synchronized to `[Verified]`.
3. **Clean-Checkout Evidence Governance**: `verify_requirement_governance.py --repo-root . --base-ref sprint/2026-11-20_03_Nacht --check-verified-evidence --test-results-dir /tmp/empty_check_9343` **PASS (`exit 0`)**.
4. **Branch Hygiene (`Rule 3.3`, `Rule 8.2`)**: `feature/ATT-9343` preserved intact (not prematurely merged onto `sprint/2026-11-20_03_Nacht`) for Stage 6 Cold Final Review.
