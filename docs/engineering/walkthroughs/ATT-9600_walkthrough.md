# Stage 5 Walkthrough — ATT-9600: Unified Monotonic `elapsedRealtime` Clock Domain in `SpeedAndLocationDevice_ATTFused`

- **Ticket**: `ATT-9600` (`2 SP`, `Bug`, `tier-a`, Epic `ATT-166`) | **Target Release**: `V5.0.0` | **Gate 3**: `ATT-9654` (`GATE_PASSED`)
- **Traceability**: `REQ-FIL-042` (`.1`–`.3`, `[Verified]`, `Derived (SW-only)`) <-> `TST-FIL-042` (`.1`–`.3`, `[Verified]`)
- **Branch**: `feature/ATT-9600` (branched cleanly off `sprint/2026-11-22_03_Nacht` `25208596`; preserved intact for Stage 6 Cold Review)

## 1. Summary of Changes & All 4 Gate 3 Guardrails Enforced

1. **Pure-Kotlin Monotonic Clock Normalizer (`AttFusedTimestampNormalizer.kt`, `278` LoC, `REQ-FIL-042.1`/`.2`)**:
   - Created `app/src/main/java/com/atrainingtracker/banalservice/odometry/AttFusedTimestampNormalizer.kt` and registered it in `docs/architecture.md` (`v1.0.29`, `Rule 8.8`).
   - Unifies GNSS (`location.elapsedRealtimeNanos / 1_000_000L`), standard `CLOCK_BOOTTIME` IMU (`|sensorMs - refNowMs| <= 5 s`, `offset = 0`), divergent OEM HALs (`uptimeMillis` pausing across deep sleep & Unix epoch ns with NTP jumps), and wall-clock `MySensorManager` wheel callbacks onto monotonic `elapsedRealtime` milliseconds.
   - **Gate 3 Guardrail #2 (Robolectric Frozen-Clock Coherence)**: Updates `lastResolvedMonotonicMs` across ALL resolution methods, latches initial per-sensor-type offsets against `refNowMs = max(rawPlatformMonoMs, lastResolvedMonotonicMs)`, evaluates forward epoch-ns NTP jump re-anchors against `rawPlatformMonoMs` BEFORE updating `lastResolvedMonotonicMs`, and clears all maps/origins/high-water marks in `reset()`.
   - **Gate 3 Guardrail #4 (Sentinel Hygiene)**: Guards `Long.MIN_VALUE` before all timestamp subtractions.
2. **Device Integration & Monolith Reduction (`SpeedAndLocationDevice_ATTFused.kt`, `1,498 -> 1,472 / 1,500` LoC, `-26` LoC)**:
   - Added `protected open fun elapsedRealtimeNanos(): Long` (plain-JVM safe `try/catch -> 0L`), wired `timestampNormalizer` across `onSensorChanged()`, `onLocationChanged()`, `onWheelMeasurement()` (3-arg & 2-arg), `mSportChangedReceiver`, `triggerDecayTick()`, `ingestImuMeasurement()`, and `resetOriginAndEkf()`.
   - **Gate 3 Guardrail #1 (2-Arg Wheel Sidecar UTC)**: 2-arg `onWheelMeasurement()` passes `currentTimeMillis()` and 3-arg writes `rawEpochMs` (`>= 1.5e12` or `toWallClockEpochMs(monoMs)`) into `SmartphoneRawTelemetryRecorder.recordWheel()`, while `updateCarriageMode()` passes `currentTimeMillis()` and `evaluateMultisportTransitionIfDue()` passes NTP-immune `toWallClockEpochMs(timestampMs)`.
   - **Gate 3 Guardrail #3 (Plain-JVM Location Safety)**: Guarded `location.elapsedRealtimeNanos` with `try { location.elapsedRealtimeNanos } catch (_: Throwable) { 0L }`.
3. **Defensive Out-of-Order Classifier & EKF Guards (`REQ-FIL-042.3`)**:
   - Hardened `PhoneCarriageClassifier.classify(nowMs)` (`1,175 / 1,500` LoC) with `effectiveNowMs = max(nowMs, lastEventTimestampMs)` and out-of-order GNSS speed/bearing guard in `ingestGnssSpeed()`.
   - Hardened `UnicycleOdometryEkf.kt` (`888 / 1,500` LoC) with monotonic `advanceTimestamp()` while keeping `reset()` assigning `currentTimestampMs = timestampMs` directly (Gate 3 Guardrail #4).

## 2. Verification Results (`160/160` Tier-1 Targeted PASS, `0` New Warnings)

- Command: `python3 tools/parallel_sprint_lock.py -- ./gradlew :app:testDebugUnitTest --rerun --tests "com.atrainingtracker.banalservice.odometry.AttFusedTimestampNormalizerTest" --tests "com.atrainingtracker.banalservice.odometry.PhoneCarriageClassifierTest" --tests "com.atrainingtracker.banalservice.odometry.UnicycleOdometryEkfTest" --tests "com.atrainingtracker.banalservice.odometry.AttFusedColdStartHelperTest" --tests "com.atrainingtracker.banalservice.odometry.KinematicSportTypeDiscriminatorTest" --tests "com.atrainingtracker.banalservice.devices.SpeedAndLocationDevice_ATTFusedTest" --tests "com.atrainingtracker.banalservice.devices.SpeedAndLocationDevice_ATTFusedBANALServiceIntegrationTest" --tests "com.atrainingtracker.banalservice.devices.SpeedAndLocationDeviceStationaryJitterTest" --tests "com.atrainingtracker.banalservice.devices.SpeedAndLocationDeviceDecayTest" --tests "com.atrainingtracker.banalservice.devices.SpeedAndLocationDeviceIndoorAndRestCouplingTest" --tests "com.atrainingtracker.banalservice.replay.*" --tests "com.atrainingtracker.trainingtracker.build.ArchitectureConformanceContractTest"`
- **Results (`160/160` PASS across 13 suites, `0` fail, `0` err, `0` skip, `0` warnings in modified files)**:
  - `AttFusedTimestampNormalizerTest`: `3/3` PASS (`TST-FIL-042.1`, `.2`)
  - `SpeedAndLocationDevice_ATTFusedTest`: `42/42` PASS (`TST-FIL-042.1` Pixel 10 `-2.18 s` replay `0` sub-`2 s` flips + UTC sidecar; `TST-FIL-042.2` OEM `uptimeMillis` + epoch-ns alignment)
  - `SpeedAndLocationDevice_ATTFusedBANALServiceIntegrationTest` (Robolectric): `3/3` PASS
  - `PhoneCarriageClassifierTest`: `16/16` PASS (`TST-FIL-042.3`) | `UnicycleOdometryEkfTest`: `21/21` PASS (`TST-FIL-042.3`)
  - `AttFusedColdStartHelperTest`: `3/3` PASS | `KinematicSportTypeDiscriminatorTest`: `6/6` PASS
  - `SpeedAndLocationDeviceStationaryJitterTest`: `15/15` PASS | `SpeedAndLocationDeviceDecayTest`: `14/14` PASS | `SpeedAndLocationDeviceIndoorAndRestCouplingTest`: `11/11` PASS
  - `OfflineTelemetryReplayHarnessTest`: `11/11` PASS | `OfflineReplayCliTest`: `4/4` PASS | `ArchitectureConformanceContractTest`: `11/11` PASS (`SpeedAndLocationDevice_ATTFused.kt = 1,472 / 1,500` LoC)
- **Governance & Evidence Bundle**: `verify_requirement_governance.py --base-ref 25208596` `exit 0`; `auto_gate.py --ticket ATT-9600 --tier tier-a --base-ref 25208596 --write-bundle` (commit `70e01927`) passed `static_safety_linters`, `deliverable_lint`, `conflict_markers`, `evidence_bundle` (`10` JUnit XMLs / `11` SHA-256 hashed files under `docs/engineering/test_evidence/tickets/ATT-9600/`), and `9/9` in-scope Android suites (`126/128` total, incl. `ExtendedRtsOdometrySmootherTest`, `MultisportTransitionDetectorTest`, `OdometrySimulationTest`, `PolarDualImuBoundedHeapStressTest`).
- **Documented Pre-Existing Baseline Deviation**: `auto_gate.py` reported only the known pre-existing baseline failure unrelated to `ATT-9600` — `MeasuredNfrBenchmarkTest.testNfr014_cockpitTelemetryThrottling_measuredMaxOneHzOverFiveSeconds` (+ `classMethod`, tracked in **`ATT-9639`**).
