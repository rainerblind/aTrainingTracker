# Stage 2: Requirement & Test Specification - ATT-9062: PhoneCarriageClassifier Indoor GNSS-Outage Stationary Transition, Cyclic-Immune Glance Gating, Multipath Speed Gating & Cross-Device Portability

**Ticket**: `ATT-9062` (`2 SP`, `Bug`, `severity-major`) | **Parent Epic**: `ATT-166` | **Target Release**: `V5.0.0`  
**Active Sprint**: `2026-11-19_03_Nacht` | **Branch**: `feature/ATT-9062` (base `b6a1eaea`)  
**Requirement Mapping**: `REQ-FIL-034` (`REQ-FIL-034.1`–`.4`, refining `REQ-FIL-028.1`/`.3`, `REQ-FIL-029`, `REQ-FIL-030`)  
**Test Spec ID**: `TST-FIL-034` (`TST-FIL-034.1`–`.4`) | **Author**: AI Agent 1 (`agent1`) | **Date**: 2026-11-20

---

## 1. Requirement Specification (`REQ-FIL-034`)

### 1.1 Attributes
- **Priority**: High | **Type**: Functional | **Verification Method**: Unit
- **System Requirement(s)**: `SYSREQ-TRK-003`, `SYSREQ-TRK-004`, `SYSREQ-TRK-010`
- **Implementation**: `PhoneCarriageClassifier.kt`, `SpeedAndLocationDevice_ATTFused.kt`

### 1.2 Functional & Architectural Requirements
1. **Indoor GNSS-Outage, Stagnant-Fix & Low-Speed Quiet-IMU `STATIONARY` Transition (`REQ-FIL-034.1`)**:
   - `PhoneCarriageClassifier.evaluateRawCandidate()` **SHALL** transition to `PhoneCarriageMode.STATIONARY (0)` (subject to normal dwell hysteresis) when at least one IMU stream is active (`hasOrientation || hasGyro`), `quietImu` holds (`swingSdDeg < 2.5`, `rawGyroRms < 0.14`, `stepFreqHz == null`, no active accelerometer footstrikes), and `!wheelActiveMoving`, under any of: (a) stale GNSS (`effectiveSpeed == null`) after prior speed ingestion or non-`UNKNOWN` committed mode (retaining initial `UNKNOWN` for pure IMU-only checks with no prior speed); (b) low/walking speed `effectiveSpeed <= 1.8 m/s`; or (c) position stagnation (`>= 4.0 s` time window, `>= 3` fixes, horizontal spread `< 2.0 m`) from stagnant cached OEM fixes.
   - `SpeedAndLocationDevice_ATTFused` **SHALL** override `triggerDecayTick()`, calling `super.triggerDecayTick()` and—when `mSpeed <= SPEED_ZERO_THRESHOLD`—propagating `0.0 m/s` to `carriageClassifier.ingestGnssSpeed(0.0, null, null, nowMs)` and invoking `updateCarriageClassification(nowMs, forceClassify = true)` using injectable `currentTimeMillis()`.
2. **Cyclic-Immune `detectGlanceShift()` & Front-Pocket Cycling Classification (`REQ-FIL-034.2`)**:
   - `PhoneCarriageClassifier.detectGlanceShift()` **SHALL** require a minimum prior time span `prevSpanMs >= 1_200L` (`prevN >= 4`) to prevent startup-sliver triggers, and **MUST** reject cyclic/periodic thigh flexion (`0.8–1.8 Hz ±20..35°`) whenever `(recSdDeg > 8.0 && deltaDeg < 2.6 * recSdDeg)` OR `(prevSdDeg >= 7.0 && deltaDeg <= 1.25 * prevPeakDeg)`, while continuing to detect genuine sustained wrist-glance shifts (e.g. `70° ± 22° -> 18–20°` and gentle `70° -> 35°`).
   - With cyclic false glances eliminated and `confirmedNoFootstrikes` active, front-pocket cycling acceleration (`2.5–5.4 m/s`, `sportHint == UNKNOWN`, `wheelSpeed == null`) **SHALL** classify as `BIKE_FRONT_POCKET (4)` rather than `RUN_HANDHELD (7)`.
3. **Degraded Multipath GNSS Speed & `isMovingByGnss` Accuracy Gating (`REQ-FIL-034.3`)**:
   - `SpeedAndLocationDevice_ATTFused.onLocationChanged()` **SHALL** compute `hasTrustedGpsSpeed = hasGpsSpeed && acc > 0.0 && acc <= UnicycleOdometryEkf.MAX_GNSS_ACCURACY_METERS` and gate **BOTH** `speedForClassifier` (falling back to `gnssDisplacementSpeedMps` or `ekf.speedMps`) **AND** `isMovingByGnss` on `hasTrustedGpsSpeed`, passing optional `latitude`/`longitude` to `carriageClassifier.ingestGnssSpeed()` strictly when `acc > 0.0 && acc <= MAX_GNSS_ACCURACY_METERS`, so indoor multipath spikes (`acc = 43.9 m`, `speed = 2.89 m/s`) neither trigger `RUN_*` nor defeat `forceStationaryPinning`.
4. **Cross-Device & Cross-Athlete Portability (`REQ-FIL-034.4`)**:
   - `PhoneCarriageClassifier` **SHALL** expose `setStepDetectorRegistered(Boolean)` (default `false`) and `ingestAccelerometer(ax, ay, az, timestampMs)` reusing `ImuCadenceDsp` (`UniformGridResampler` `50 Hz` time-based grid, `BiquadBandPass(50.0, 2.1, 4.2)`, `DoubleRingBuffer(250)`, `findFundamentalLagResult`), strictly using time-based windows (never raw callback counts).
   - Running footstrike detection from `TYPE_ACCELEROMETER` **MUST** require **BOTH** `2.1–4.2 Hz` bandpass RMS `>= 1.2 m/s²` (blocking low-energy `3.0 Hz` 2nd harmonics of `1.5 Hz` pedaling) **AND** normalized ACF `peakCorrelation >= 0.42` over `2.3..3.8 Hz` (lags `13..22` at `50 Hz`); `hasConfirmedNoFootstrikes` **MUST** require `>= 2.0 s` of active accel span with no detected footstrikes.
   - `effectiveRunStepFreqHz = stepFreqHz ?: accelFootstrikeFreqHz` **SHALL** award `runningPrior += 5.0` before speed cutoffs (`>= 5.5 m/s`) so a `5.6 m/s` (`3:00 min/km`) interval runner and an `ACTIVITY_RECOGNITION`-denied / barometer-free runner classify as `RUN_*`.
   - `confirmedNoFootstrikes = stepDetectorActiveAndZero || hasConfirmedNoFootstrikes` (remaining `false` when `stepDetectorRegistered == false` and no accel is fed so `PhoneCarriageClassifierTest:295` stays `WALKING_TRANSITION`) **SHALL** gate the `WALKING_TRANSITION` short-circuit via `!(confirmedNoFootstrikes && effectiveSpeed >= 1.6)` and award `cyclingPrior` (not `runningPrior += 0.6`) when `confirmedNoFootstrikes && effectiveSpeed >= 1.8`, so a `2.0 m/s` `60 RPM` uphill MTB climber classifies as `BIKE_*` rather than `RUN_*`/`WALKING_TRANSITION`.

### 1.3 Acceptance Criteria (Given-When-Then)
* **AC-1 (`REQ-FIL-034.1`)**: *Given* a workout entering a bakery (`t=236 s`, `1.44 m/s`, `acc = 48.5 m`) followed by `120 s` indoor standstill with zero GNSS callbacks (or stagnant cached fixes), zero steps, and quiet IMU (`swingSd < 2.5°`, `rawGyroRms < 0.14 rad/s`), *When* `PhoneCarriageClassifier.classify()` runs (or `SpeedAndLocationDevice_ATTFused.triggerDecayTick()` decays to `0.0 m/s`), *Then* mode transitions to and stays in `STATIONARY (0)` rather than freezing on `RUN_WAIST_HIP (5)` (while pure IMU-only checks with no prior speed remain `UNKNOWN`).
* **AC-2 (`REQ-FIL-034.2`)**: *Given* front-pocket cycling acceleration `2.5 -> 5.4 m/s` (`sportHint == UNKNOWN`, `wheelSpeed == null`, `stepDetectorActiveAndZero`, no `2.4–3.6 Hz` footstrikes, `0.8–1.8 Hz ±25°` cyclic thigh flexion, `yaw >= 0.45 rad/s`, `baro >= 0.40 m/s`), *When* `PhoneCarriageClassifier.classify()` evaluates, *Then* `detectGlanceShift()` fires `0` false glances (including during startup) and output is `BIKE_FRONT_POCKET (4)` (while genuine `70° -> 18–20°` and `70° -> 35°` wrist glances still trigger `isGlanceActive == true`).
* **AC-3 (`REQ-FIL-034.3`)**: *Given* a walking transition (`ekf.speedMps ~ 1.0 m/s`) receiving an indoor multipath fix (`acc = 43.9 m`, `location.speed = 2.89 m/s`), *When* `SpeedAndLocationDevice_ATTFused.onLocationChanged()` executes, *Then* neither `speedForClassifier` nor `isMovingByGnss` accepts `2.89 m/s`, `forceStationaryPinning` is not defeated, and mode remains `WALKING_TRANSITION (1)` / `STATIONARY (0)`.
* **AC-4 (`REQ-FIL-034.4`)**: *Given* (a) a barometer-free, `ACTIVITY_RECOGNITION`-denied runner (`stepDetectorUnavailable`, `2.8 Hz` accel footstrikes), (b) a fast `5.6 m/s` (`3:00 min/km`) runner with `3.0 Hz` footstrikes, and (c) a `2.0 m/s` uphill MTB climber at `60 RPM` (`1.0 Hz` pedaling, no footstrikes), *When* `PhoneCarriageClassifier` classifies each stream, *Then* (a) and (b) classify as `RUN_*`, (c) classifies as `BIKE_*` (never `RUN_*`), and step-detector-less walking (`PhoneCarriageClassifierTest:295`) stays `WALKING_TRANSITION`.

### 1.4 System Invariants & Localization
- `PhoneCarriageMode` enum codes (`-1..7`) & `CarriageFilterProfile` untouched; `UnicycleOdometryEkf.MIN_DOPPLER_SPEED_MPS = 3.0` unchanged; `SpeedAndLocationDevice_ATTFused.kt <= 1,500` LoC (`0` protected monolith edits); `PhoneCarriageClassifier.kt < 1,500` LoC; time-based windows only; `0` DB schema bumps; `0` new `src/main` test reads (`REQ-PRO-049.1`); `Localization Parity: N/A - no UI strings`.

---

## 2. Test Specification (`TST-FIL-034`)

* **`TST-FIL-034.1` (Indoor GNSS-Outage, Stagnant-Fix & Decay-to-Zero `STATIONARY` Suite — `PhoneCarriageClassifierTest.kt`, `SpeedAndLocationDevice_ATTFusedTest.kt`)**: Replay bakery walk-in (`1.44 m/s`) -> `120 s` GNSS outage with quiet IMU asserting transition to & persistence in `STATIONARY (0)`; replay stagnant cached indoor fixes (`>= 4 s`, `< 2 m` spread, fake `2.0 m/s`, `acc = 5 m`) + quiet IMU asserting `STATIONARY (0)`; verify pure IMU-only startup without prior speed stays `UNKNOWN`; verify `SpeedAndLocationDevice_ATTFused.triggerDecayTickForTesting()` with injectable clock propagates `0.0 m/s` to `carriageClassifier` and transitions to `STATIONARY (0)`.
* **`TST-FIL-034.2` (Cyclic-Immune `detectGlanceShift()` & Front-Pocket Cycling Suite — `PhoneCarriageClassifierTest.kt`)**: Sweep cyclic thigh flexion (`0.8–1.8 Hz ±25°`, `2.5 -> 5.4 m/s`, `sportHint == UNKNOWN`, `wheelSpeed == null`, `stepDetectorRegistered = true` with zero steps) verifying `0` false glances (including startup `0..2 s`) and `BIKE_FRONT_POCKET (4)` output; verify genuine `70° ± 22° -> 18–20°` and gentle `70° -> 35°` wrist glances still trigger `isGlanceActive == true` and `RUN_HANDHELD`.
* **`TST-FIL-034.3` (Degraded Multipath Speed & `isMovingByGnss` Gating Suite — `SpeedAndLocationDevice_ATTFusedTest.kt`)**: Feed walking transition (`ekf.speedMps ~ 1.0 m/s`) followed by indoor multipath fixes (`acc = 43.9 m`, `location.speed = 2.89 m/s`); assert degraded speed is rejected from `speedForClassifier` and `isMovingByGnss`, `forceStationaryPinning` stays intact, and mode remains `WALKING_TRANSITION (1)` / `STATIONARY (0)`.
* **`TST-FIL-034.4` (Cross-Device & Cross-Athlete Portability & Contract Suite — `PhoneCarriageClassifierTest.kt`, `SpeedAndLocationDevice_ATTFusedTest.kt`, Contracts)**: Verify (a) barometer-free + `ACTIVITY_RECOGNITION`-denied runner (`2.8 Hz` accel footstrikes, `RMS >= 1.2 m/s²`), (b) `5.6 m/s` fast interval runner (`3.0 Hz` footstrikes, with and without step detector), (c) `2.0 m/s` uphill `60 RPM` MTB climber (smooth `1.0 Hz` pedaling + low-energy `3.0 Hz` harmonic `< 1.2 m/s²`, `0` footstrikes) -> `BIKE_*` (never `RUN_*`), (d) regression of step-detector-less walking (`PhoneCarriageClassifierTest:295`), and (e) `ArchitectureConformanceContractTest` (`SpeedAndLocationDevice_ATTFused.kt <= 1,500` LoC, `0` protected monolith edits), `DeadCodeEliminationContractTest`, `SystemLiveTrackingRequirementsContractTest`, and `SourceTextReadingAuditContractTest`.

---

## 3. Traceability Matrix

| Test Case | Scope | Test Suite(s) | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-FIL-034.1` | Indoor GNSS-outage, stagnant-fix, decay-to-zero `STATIONARY` | `PhoneCarriageClassifierTest`, `SpeedAndLocationDevice_ATTFusedTest` | `REQ-FIL-034.1` | Planned |
| `TST-FIL-034.2` | Cyclic-immune `detectGlanceShift()` & front-pocket cycling | `PhoneCarriageClassifierTest` | `REQ-FIL-034.2` | Planned |
| `TST-FIL-034.3` | Degraded multipath speed & `isMovingByGnss` gating | `SpeedAndLocationDevice_ATTFusedTest` | `REQ-FIL-034.3` | Planned |
| `TST-FIL-034.4` | Cross-device/athlete portability, `ImuCadenceDsp` RMS floor, contracts | `PhoneCarriageClassifierTest`, `SpeedAndLocationDevice_ATTFusedTest`, `ArchitectureConformanceContractTest`, `SystemLiveTrackingRequirementsContractTest`, `SourceTextReadingAuditContractTest` | `REQ-FIL-034.4` | Planned |
