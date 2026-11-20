# Stage 3: Implementation Plan - ATT-9062: Fix PhoneCarriageClassifier Indoor GNSS-Outage RUN_WAIST_HIP Freeze and Front-Pocket Pedaling RUN_HANDHELD Misclassification

**Ticket**: `ATT-9062` (`2 SP`, `Bug`, `severity-major`) | **Parent Epic**: `ATT-166` | **Target Release**: `V5.0.0`  
**Active Sprint**: `2026-11-19_03_Nacht` | **Branch**: `feature/ATT-9062` | **Author**: AI Agent 1 (`Pro` / Neon) | **Date**: 2026-11-20  
**Requirement Mapping**: `REQ-FIL-034` (`REQ-FIL-034.1`–`.4`) | **Test Mapping**: `TST-FIL-034` (`TST-FIL-034.1`–`.4`)

---

## 1. Architecture, Threading & LoC Budget (`SWE.2` / `SWE.3`)
* **Layer Boundaries**: Pure-JVM IMU/GNSS carriage classifier (`PhoneCarriageClassifier.kt` reusing package-internal `ImuCadenceDsp.kt` under `banalservice/odometry/`, `@Synchronized` thread safety) invoked from Android sensor/location callbacks in `SpeedAndLocationDevice_ATTFused.kt` (`banalservice/devices/`). Zero UI/Compose changes (`Localization Parity: N/A - no UI strings`), zero SQLite schema changes, zero `SWE.3` state-machine changes (`TrackerService`, `BANALService`, `MyBTLEDevice`, `ErtsPostWorkoutRecomputer` untouched).
* **Touched Production Files (Strictly 2 Non-Monoliths `<= 1,500` LoC)**:
  * `PhoneCarriageClassifier.kt`: `853 -> ~1,030 / 1,500` LoC (`~470` headroom)
  * `SpeedAndLocationDevice_ATTFused.kt`: `1,441 -> ~1,458 / 1,500` LoC (**strictly surgical `<= +20` LoC**; keeps `>= 40` LoC margin under the `1,500` God-class ceiling in `ArchitectureConformanceContractTest`)
* **Protected Monoliths & Siblings (`0` LoC Edits)**: `TrackerService.java` (`3,559`), `WorkoutSummariesDatabaseManager.java` (`3,148`), `WorkoutSamplesDatabaseManager.java` (`1,533`), `WorkoutRepository.kt` (`5,117`), `ImuCadenceDsp.kt` (`510`, reused as-is), `SpeedAndLocationDevice.java` (`810`), `ErtsPostWorkoutRecomputer.kt` (`1,054`), `strings*.xml`.

---

## 2. System Invariants & Preserved Contracts
1. **Enum & Profile Immutability**: `PhoneCarriageMode` codes (`-1..7`) and `CarriageFilterProfile` values remain 100% unchanged; `UnicycleOdometryEkf.MIN_DOPPLER_SPEED_MPS = 3.0` unchanged.
2. **Backward-Compatible Signatures**: `ingestGnssSpeed(...)` keeps `@JvmOverloads` with `latitude: Double? = null, longitude: Double? = null` defaults; `stepDetectorRegistered` defaults to `false` (`stepDetectorUnavailable`) so existing unit tests without step-detector registration remain unaffected.
3. **Existing Test Parity**: Pure IMU-only startup without prior speed stays `UNKNOWN`; `PhoneCarriageClassifierTest` all 9 modes, `Zum Bäcker` hysteresis, and step-detector-less walking (`:295`) pass; `SpeedAndLocationDevice_ATTFusedTest` handheld glance spike gating (`:813`), chord heading, cadence, and multisport integration pass.
4. **Governance & Hygiene**: Strict time-based windows (never raw OEM callback counts), `0` DB schema bumps, `0` new `src/main` test reads (`REQ-PRO-049.1`), Gate 3 Code Lock (`0` edits under `app/` in Stage 3), Parent Human Gate (`Rule 3.1`).

---

## 3. Atomic Stage 4 Implementation Sequence

### Step 0: Gate 3 Code Lock Verification (`Rule 4`)
* Run `python3 tools/jira_util.py check-gate <IMPL_PLAN_SUBTASK_KEY>` and confirm `GATE_PASSED` (exit `0`) before modifying any file under `app/src/`.

### Step 1: Harden `PhoneCarriageClassifier.kt` (`REQ-FIL-034.1`, `.2`, `.4` -> `TST-FIL-034.1`, `.2`, `.4`)
* **Target**: `app/src/main/java/com/atrainingtracker/banalservice/odometry/PhoneCarriageClassifier.kt`
* **Design**:
  1. **Step-Detector Registration & Accelerometer Footstrike DSP (`REQ-FIL-034.4`)**:
     * Add `private var stepDetectorRegistered = false` + `@Synchronized fun setStepDetectorRegistered(registered: Boolean)` (not cleared by `reset()`, mirroring `sportHint`).
     * Add `UniformGridResampler(GRID_STEP_MS)`, `BiquadBandPass(FS_HZ, 2.1, 4.2)`, `DoubleRingBuffer(WINDOW_SAMPLES)` (`5.0 s`), `firstAccelTimestampMs`, `lastAccelTimestampMs`, and `@Synchronized fun ingestAccelerometer(axMps2, ayMps2, azMps2, timestampMs)` (ignoring non-finite/negative inputs; reset all DSP state in `reset()`).
     * Add `evaluateAccelFootstrike(nowMs)` returning `(footstrikeFreqHz: Double?, confirmedNoFootstrikes: Boolean)`: requires fresh accel (`nowMs - lastAccelTimestampMs <= STALENESS_MS`) and time-based active span `>= 2_000L ms` (`impactRing.size >= 100`); computes `impactRms = impactRing.recentRms(impactRing.size)` and `findFundamentalLagResult(minLag = 13, maxLag = 22, minOverlap = 50, availableSamples = impactRing.size, minPeakCorrelation = 0.42)` (`2.27..3.85 Hz` at `50 Hz`). Reports `footstrikeFreqHz = 50.0 / lagSamples` **strictly** when BOTH `impactRms >= 1.2` AND `peakCorrelation >= 0.42`; otherwise sets `confirmedNoFootstrikes = true` (rejecting low-energy `< 1.2 m/s²` `3.0 Hz` 2nd harmonics of `1.5 Hz` pedaling).
  2. **Stagnant Cached Position & Indoor GNSS-Outage `STATIONARY` Transition (`REQ-FIL-034.1`)**:
     * Add `private data class PositionSample(timestampMs: Long, lat: Double, lon: Double)` + `ArrayDeque<PositionSample>` pruned to `windowMs` (cleared in `reset()`). Extend `ingestGnssSpeed(...)` with `@JvmOverloads latitude: Double? = null, longitude: Double? = null` appending finite `(lat, lon)` in `[-90, 90] x [-180, 180]`.
     * Add `isPositionStagnant(nowMs)`: requires `positionSamples.size >= 3`, time span `(newest - oldest) >= 4_000L`, and max horizontal Euclidean approximation distance from first sample `< 2.0 m`.
     * In `evaluateRawCandidate(nowMs)`: evaluate IMU stats (`hasOrientation`, `hasGyro`, `quietImu`, `accelFootstrike`) before the `effectiveSpeed == null && stepFreqHz == null` early return.
     * Define `hadPriorSpeedOrMode = committedMode != UNKNOWN || lastGnssTimestampMs > Long.MIN_VALUE || lastWheelTimestampMs > Long.MIN_VALUE`, `effectiveRunStepFreqHz = stepFreqHz ?: accelFootstrikeFreqHz`, and `quietImu = (!hasOrientation || swingSdDeg < 2.5) && (!hasGyro || rawGyroRms < 0.14) && stepFreqHz == null && accelFootstrikeFreqHz == null`.
     * If `effectiveSpeed == null && effectiveRunStepFreqHz == null`: return `STATIONARY` when `hadPriorSpeedOrMode && (hasOrientation || hasGyro) && quietImu`; else return `UNKNOWN` (preserving pure IMU-only startup `UNKNOWN`).
     * In Section 1 (`STATIONARY`): return `STATIONARY` when `(lowSpeed && (quietImu || (effectiveSpeed <= 0.10 && (wheelSpeed == null || wheelSpeed <= 0.10))))` OR `(!wheelActiveMoving && (hasOrientation || hasGyro) && quietImu && ((effectiveSpeed != null && effectiveSpeed <= 1.8) || isPositionStagnant(nowMs)))`.
  3. **Cyclic-Immune `detectGlanceShift()` (`REQ-FIL-034.2`)**:
     * Keep `rec` (`ageMs in 0..700L`, `recN >= 2`) and `prev` (`ageMs in 1_000..4_500L`, `prevN >= 4`), enforcing minimum time span `prevSpanMs = prevNewestMs - prevOldestMs >= 1_200L` (eliminates startup-sliver false glances).
     * Compute `recSdDeg` (RMS angular deviation of `rec` samples around `rec` mean), `prevSdDeg` & `prevPeakDeg` (RMS and max angular deviation of `prev` samples around `prev` mean), and `deltaDeg` (angle between `rec` and `prev` means).
     * Reject as cyclic thigh oscillation when `(recSdDeg > 8.0 && deltaDeg < 2.6 * recSdDeg)` OR `(prevSdDeg >= 7.0 && deltaDeg <= 1.25 * prevPeakDeg)`; otherwise fire glance when `deltaDeg >= GLANCE_SHIFT_THRESHOLD_DEG (15.0)`.
  4. **Kinematic-First Run/Bike Priors & Uphill MTB Gate (`REQ-FIL-034.2`, `.4`)**:
     * Define `stepDetectorActiveAndZero = stepDetectorRegistered && stepFreqHz == null` and `confirmedNoFootstrikes = stepDetectorActiveAndZero || accelConfirmedNoFootstrikes` (stays `false` when `stepDetectorRegistered == false` and no accel is fed!).
     * Gate Section 2 (`WALKING_TRANSITION` lines `379-388`) with `!(confirmedNoFootstrikes && effectiveSpeed >= 1.6)` so `2.0 m/s` `60 RPM` uphill MTB climbers do not short-circuit into `WALKING_TRANSITION`, while `PhoneCarriageClassifierTest:295` stays `WALKING_TRANSITION`.
     * In Section 3 priors: prioritize `effectiveRunStepFreqHz != null && effectiveRunStepFreqHz >= 2.25 -> runningPrior += 5.0` ahead of `effectiveSpeed >= 5.5` (so `5.6 m/s` interval runners and permission-denied/baro-free runners classify `RUN_*`); in `else`, when `confirmedNoFootstrikes && effectiveSpeed != null && effectiveSpeed >= 1.8`, add `cyclingPrior += 1.5` (and do NOT add `runningPrior += 0.6`).

### Step 2: Surgical Multipath Gating & Zero-Decay Propagation in `SpeedAndLocationDevice_ATTFused.kt` (`REQ-FIL-034.1`, `.3`, `.4` -> `TST-FIL-034.1`, `.3`, `.4`)
* **Target**: `app/src/main/java/com/atrainingtracker/banalservice/devices/SpeedAndLocationDevice_ATTFused.kt` (`<= +20` LoC, staying `<= 1,465 / 1,500` LoC!)
* **Design**:
  1. `registerImuSensors()` (`:360`): call `carriageClassifier.setStepDetectorRegistered(true)` when `TYPE_STEP_DETECTOR` is successfully registered; `shutDown()`: call `carriageClassifier.setStepDetectorRegistered(false)`.
  2. `onSensorChanged(TYPE_ACCELEROMETER)` (`:432`): call `carriageClassifier.ingestAccelerometer(event.values[0].toDouble(), event.values[1].toDouble(), event.values[2].toDouble(), nowMs)`.
  3. `onLocationChanged()` (`:502-523`): define `val accValid = acc > 0.0 && acc <= UnicycleOdometryEkf.MAX_GNSS_ACCURACY_METERS` and `val hasTrustedGpsSpeed = hasGpsSpeed && accValid`. Gate **BOTH** `speedForClassifier` (`hasTrustedGpsSpeed -> gpsSpeed`) **AND** `isMovingByGnss` (`hasTrustedGpsSpeed && gpsSpeed > 0.25 || ...`) on `hasTrustedGpsSpeed`, and pass `latitude = if (accValid) lat else null, longitude = if (accValid) lon else null` to `carriageClassifier.ingestGnssSpeed(...)`.
  4. Override `triggerDecayTick()`: invoke `super.triggerDecayTick()`, and when `mSpeed <= SPEED_ZERO_THRESHOLD`, compute `val nowMs = currentTimeMillis()`, call `carriageClassifier.ingestGnssSpeed(0.0, null, null, nowMs)`, and invoke `updateCarriageClassification(nowMs, forceClassify = true)`.

### Step 3: Targeted Unit & Integration Tests (`TST-FIL-034.1`–`.4`)
* Add focused behavioral unit tests with explicit hyphenated `// REQ-FIL-034.x / TST-FIL-034.x` comments in:
  * `PhoneCarriageClassifierTest.kt`: bakery indoor GNSS outage (`120 s` -> `STATIONARY`), stagnant cached fixes (`>= 4 s`, `< 2 m` -> `STATIONARY`), pure IMU-only startup stays `UNKNOWN`, cyclic `0.8–1.8 Hz ±25°` front-pocket pedaling (`2.5 -> 5.4 m/s`, `0` false glances incl. startup, `BIKE_FRONT_POCKET`) + genuine `70° -> 18°` & gentle `70° -> 35°` wrist glances, barometer-free permission-denied runner (`2.8 Hz` accel footstrikes -> `RUN_*`), `5.6 m/s` `3:00/km` interval runner (`3.0 Hz` footstrikes -> `RUN_*`), and `2.0 m/s` `60 RPM` uphill MTB climber (smooth `1.0 Hz` + low-energy `< 1.2 m/s²` `3.0 Hz` harmonic -> `BIKE_*`).
  * `SpeedAndLocationDevice_ATTFusedTest.kt`: `acc = 43.9 m` multipath `2.89 m/s` speed gated from `speedForClassifier` & `isMovingByGnss` (keeps `WALKING_TRANSITION` / `STATIONARY` & `forceStationaryPinning`), and `triggerDecayTickForTesting()` with injectable clock propagating `0.0 m/s` to `STATIONARY`.

### Step 4: Locked Incremental Gradle Verification (`Rule 7.5` — NO `--rerun-tasks`, NO `-PstampGitHash=true`)
```bash
python3 tools/parallel_sprint_lock.py --timeout 1800 -- ./gradlew :app:testDebugUnitTest --rerun \
  --tests "*PhoneCarriageClassifierTest" \
  --tests "*SpeedAndLocationDevice_ATTFusedTest" \
  --tests "*SpeedAndLocationDevice_ATTFusedBANALServiceIntegrationTest" \
  --tests "*KinematicSportTypeDiscriminatorTest" \
  --tests "*MultisportTransitionDetectorTest" \
  --tests "*SmartphoneImuCadenceEstimatorTest" \
  --tests "*ArchitectureConformanceContractTest" \
  --tests "*DeadCodeEliminationContractTest" \
  --tests "*SystemLiveTrackingRequirementsContractTest" \
  --tests "*SourceTextReadingAuditContractTest"
```

---

## 4. Verification & Rollback Plan
* **Verification**: `100%` pass across all 10 targeted/contract suites above + `SpeedAndLocationDevice_ATTFused.kt <= 1,500` LoC + `verify_requirement_governance.py --repo-root . --base-ref refs/heads/sprint/2026-11-19_03_Nacht --check-verified-evidence` (`exit 0`).
* **Rollback**: Isolated cleanly on `feature/ATT-9062`; `sprint/2026-11-19_03_Nacht` remains untouched until Stage 6 `PASS` and `--merge-ticket`.
