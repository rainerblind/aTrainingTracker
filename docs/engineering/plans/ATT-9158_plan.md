# Stage 3: Implementation Plan - ATT-9158: Fix ATT Fused Cold-Start 23-Second Pinning Freeze and Post-Unpin ERTS Catch-Up Velocity Overshoot

**Ticket**: `ATT-9158` (`2 SP`, `Bug`, `severity-major`) | **Parent Epic**: `ATT-166` | **Target Release**: `V5.0.0`  
**Active Sprint**: `2026-11-20_01_Früh` (`465`) | **Branch**: `feature/ATT-9158` | **Author**: AI Agent 1 (`Pro` / Neon) | **Date**: 2026-11-20  
**Requirement Mapping**: `REQ-FIL-036` (`REQ-FIL-036.1`–`.3`) | **Test Mapping**: `TST-FIL-036` (`TST-FIL-036.1`–`.3`)

---

## 1. Architecture, Threading & LoC Budget (`SWE.2` / `SWE.3`)
* **Layer Boundaries**: Extract pure-JVM cold-start motion release, raw-chord heading seeding, anti-oscillation progression checks, and bounded Doppler variance math into `AttFusedColdStartHelper.kt` (`banalservice/odometry/`, zero Android framework dependencies), invoked from `SpeedAndLocationDevice_ATTFused.kt` (`banalservice/devices/`) and shared by `ExtendedRtsOdometrySmoother.kt` (`banalservice/odometry/`). Add a single default-`false` protected hook in `SpeedAndLocationDevice.java` preserving structurally unbypassable indoor/wheel/rest vetoes. Zero GMS/Play Services dependency, zero DB schema changes, zero UI/Compose changes (`Localization Parity: N/A - no UI strings`).
* **Touched Production Files (All `<= 1,500` LoC; `0` Protected Monolith Edits)**:
  * **NEW** `app/src/main/java/com/atrainingtracker/banalservice/odometry/AttFusedColdStartHelper.kt`: `~160 / 1,500` LoC
  * `SpeedAndLocationDevice_ATTFused.kt`: `1,459 -> <= 1,485 / 1,500` LoC (**strictly surgical `<= +25` LoC**; keeps `>= 15` LoC margin under the `1,500` God-class ceiling in `ArchitectureConformanceContractTest`)
  * `SpeedAndLocationDevice.java`: `810 -> ~825 / 1,500` LoC (`+15` LoC max; default `false` hook leaves `GPS`/`GoogleFused` byte-identical)
  * `UnicycleOdometryEkf.kt`: `810 -> ~835 / 1,500` LoC (`+25` LoC max; `@JvmOverloads` preserves all existing callers)
  * `ExtendedRtsOdometrySmoother.kt`: `742 -> ~835 / 1,500` LoC (`+93` LoC max; `@JvmOverloads` preserves direct `smoothSnapshots()` callers)
  * `ErtsPostWorkoutRecomputer.kt`: `1,054 / 1,500` LoC (`0` edits required; verified end-to-end via `ErtsPostWorkoutRecomputerTest`)
* **Protected Monoliths (`0` LoC Touched)**: `TrackerService.java`, `WorkoutSummariesDatabaseManager.java`, `WorkoutSamplesDatabaseManager.java`, `WorkoutRepository.kt`, `DeviceManager.java`, `PhoneCarriageClassifier.kt`.

---

## 2. System Invariants & Preserved Contracts
1. **Unbypassable Stationary Vetoes (`REQ-TRK-077`, `REQ-TRK-048`, Gate 1 Advisory 1)**: `shouldReleaseColdStartStationaryPin(Location)` defaults to `false` in `SpeedAndLocationDevice.java` and runs strictly **AFTER** `if (mIsIndoorMode)` and **BEFORE** `if (shouldUnpin && (extStationary || accelAtRest)) { shouldUnpin = false; }`.
2. **Cold Stationary Convergence & Accuracy Floor (`REQ-FIL-023.1`)**: `location.accuracy = max(acc, sqrt(fusedPosVar))` remains intact; `25.1 m -> 16.0 m` stationary convergence jump at `0.0 m/s` remains suppressed; `UnicycleOdometryEkf.MIN_DOPPLER_SPEED_MPS = 3.0` unchanged.
3. **ERTS Immunities (`REQ-FIL-017.3`, `REQ-FIL-025`, `REQ-FIL-032`, Gate 2 Advisories 1 & 2)**: Raw device-attributed SQLite columns remain byte-identical; `acc > 20.0 m` retains coarse variance (`testDopplerDropoutReplay_maintainsContinuousSpeed`); `45 s` curved tunnels (`dt_gnss = 45 s`) never false-trigger discontinuity gating; genuine launches (`0, 0, 2.0, 2.2`) and multi-second sprints (`2.0 -> 3.5 -> 5.0 -> 6.0 m/s`) are never clipped; `0.00 m` indoor stationary ERTS pinning preserved.
4. **Governance & Hygiene**: Gate 3 Code Lock (`0` edits under `app/` in Stage 3), `0` new `src/main` reads in tests (`REQ-PRO-049.1`), explicit hyphenated `// REQ-FIL-036.x / TST-FIL-036.x` comments in all new tests, and `Localization Parity: N/A - no UI strings`.

---

## 3. Atomic Stage 4 Implementation Sequence

### Step 0: Gate 3 Code Lock Verification (`Rule 4`)
* Run `python3 /home/rainer/AndroidStudioProjects/aTrainingTracker/tools/jira_util.py check-gate <IMPL_PLAN_SUBTASK_KEY>` and verify `GATE_PASSED` (exit `0`) before modifying any file under `app/src/`.

### Step 1: Create Pure Helper `AttFusedColdStartHelper.kt` (`REQ-FIL-036.1`, `REQ-FIL-036.2` -> `TST-FIL-036.1`, `TST-FIL-036.2`)
* **Target**: `app/src/main/java/com/atrainingtracker/banalservice/odometry/AttFusedColdStartHelper.kt` (`~160` LoC)
* **Design**:
  1. **Calibrated Bounded Doppler Variance (`companion object`, Gate 1 Advisory 5 & Gate 2 Advisory 1)**:
     * `fun calibratedDopplerSpeedVariance(accuracyMeters: Double): Double`:
       - When `accuracyMeters.isFinite() && accuracyMeters > 0.0 && accuracyMeters <= UnicycleOdometryEkf.MAX_GNSS_ACCURACY_METERS` (`20.0`): return `((accuracyMeters * 0.08) * (accuracyMeters * 0.08)).coerceIn(0.25, 2.25)`.
       - Otherwise (`acc > 20.0` or invalid): return `if (accuracyMeters.isFinite() && accuracyMeters > 0.0) max(0.5, (accuracyMeters * 0.25) * (accuracyMeters * 0.25)) else 1.0`.
  2. **Cold-Start Corroborated Release & Anti-Oscillation Window (`REQ-FIL-036.1`, Gate 1 Advisory 3)**:
     * Maintain a bounded `15_000L ms` deque of moving raw ENU fixes `(eastM, northM, timestampMs)`, `consecutiveMotionFixes`, and `hasAnchoredHeading`. Expose `markHeadingAnchored()` and `reset()`.
     * In `evaluateStep(eastM, northM, accM, timestampMs, hasTrustedGpsSpeed, gpsSpeedMps, bearingDeg, isWheelMoving, isStationary, isStationaryByClassifier, activeMotionModeOrSteps): ColdStartDecision`:
       - On `isStationary || isStationaryByClassifier`: clear streak & raw moving window; return `ColdStartDecision(shouldReleasePin = false, seedHeadingRad = null, seedHeadingVarRad2 = 0.0, decouplePositionVelocity = false)`.
       - Reset streak/window on timestamp gaps `> 5_000L ms`. Increment `consecutiveMotionFixes` when corroborated moving (`(hasTrustedGpsSpeed && gpsSpeedMps >= 0.8) || isWheelMoving || (activeMotionModeOrSteps && hasTrustedGpsSpeed && gpsSpeedMps >= 0.5)`).
       - Append finite `(eastM, northM, timestampMs)` (pruning `> 15 s` old entries). Compute `netDist = hypot(last.e - first.e, last.n - first.n)`, `pathDist = sum(stepDist)`, and `straightness = if (pathDist > 1e-6) netDist / pathDist else 0.0`.
       - `directedProgression = samples.size >= 3 && netDist >= 3.0 && straightness >= 0.55` (blocks indoor handheld oscillating multipath bouncing ±6 m around origin!).
       - `shouldReleasePin = consecutiveMotionFixes >= 2 && (isWheelMoving || directedProgression)`.
  3. **Cold-Start Raw-Chord Heading Seeding & Unanchored Decoupling (`REQ-FIL-036.2`, Gate 1 Advisory 2)**:
     * When `!hasAnchoredHeading`:
       - Primary: if `samples.size >= 2 && netDist >= 4.0 && straightness >= 0.55`, emit `seedHeadingRad = atan2(last.n - first.n, last.e - first.e)` and `seedHeadingVarRad2 = (atan2(accM, netDist)^2).coerceIn(0.04, 0.35)`.
       - Secondary: else if `bearingDeg != null && bearingDeg.isFinite() && hasTrustedGpsSpeed && gpsSpeedMps >= 1.0 && consecutiveMotionFixes >= 2 && netDist >= 2.0 && straightness >= 0.55`, emit `seedHeadingRad = UnicycleOdometryEkf.normalizeAngle(Math.toRadians(90.0 - bearingDeg))` and `seedHeadingVarRad2 = 0.16`.
     * Set `decouplePositionVelocity = !hasAnchoredHeading && seedHeadingRad == null` so EKF position updates never crush `x[2]` before heading anchors.

### Step 2: Protected Veto Hook in `SpeedAndLocationDevice.java` & Surgical Wiring in `SpeedAndLocationDevice_ATTFused.kt` (`REQ-FIL-036.1`, `.2`)
* **Target 1**: `app/src/main/java/com/atrainingtracker/banalservice/devices/SpeedAndLocationDevice.java` (`810 -> ~825` LoC)
  * Add `protected boolean shouldReleaseColdStartStationaryPin(Location location) { return false; }`.
  * In `onNewLocation()` (`:614-628`), strictly **AFTER** `if (mIsIndoorMode)` (`:604`) and **BEFORE** `if (shouldUnpin && (extStationary || accelAtRest)) { shouldUnpin = false; }` (`:626`), add:
    `if (!shouldUnpin && shouldReleaseColdStartStationaryPin(location)) { shouldUnpin = true; }`
* **Target 2**: `app/src/main/java/com/atrainingtracker/banalservice/devices/SpeedAndLocationDevice_ATTFused.kt` (`1,459 -> <= 1,485 / 1,500` LoC, `<= +25` LoC net)
  * Add `internal val coldStartHelper = AttFusedColdStartHelper()` and `@Volatile private var mAllowColdStartPinRelease = false`; override `shouldReleaseColdStartStationaryPin(location: Location): Boolean = mAllowColdStartPinRelease`.
  * In `onLocationChanged()`: use `AttFusedColdStartHelper.calibratedDopplerSpeedVariance(acc)` at `:576`; evaluate `coldStartHelper.evaluateStep(...)`; seed heading via `ekf.updateOfflineHeading(...)` + `coldStartHelper.markHeadingAnchored()` when `seedHeadingRad != null`; pass `decoupleVelocity = coldStart.decouplePositionVelocity` to `ekf.updateGnssPosition(...)`; call `coldStartHelper.markHeadingAnchored()` on Doppler (`:615`) and running chord (`:640`) heading updates; set `mAllowColdStartPinRelease = coldStart.shouldReleasePin` before `onNewLocation(location)` (cleared in `resetOriginAndEkf()` along with `coldStartHelper.reset()`).

### Step 3: Unanchored & Post-Outage Velocity Decoupling in `UnicycleOdometryEkf.kt` (`REQ-FIL-036.2` -> `TST-FIL-036.2`)
* **Target**: `app/src/main/java/com/atrainingtracker/banalservice/odometry/UnicycleOdometryEkf.kt` (`810 -> ~835` LoC)
* **Design**:
  * Extend `updateGnssPosition(...)` with `@JvmOverloads decoupleVelocity: Boolean = false`.
  * Maintain `var decoupleSpeedUpdate = isStationary || decoupleVelocity`.
  * Inside `if (confirmedPostOutage)` (`:548-555`), after inflating `p[0][0]`/`p[1][1]`, also set `decoupleSpeedUpdate = true` and zero `p[0][2] = p[2][0] = p[1][2] = p[2][1] = 0.0` so post-outage covariance inflation never leaks a position-snap velocity spike into `x[2]`.
  * At `:589` and `:610`, zero `k[2][0] = 0.0; k[2][1] = 0.0` and call `decoupleVelocityCovariance(pNew)` whenever `decoupleSpeedUpdate` is `true` (keeping `x[2] = 0.0` clamp strictly gated on `isStationary || x[2] < 0.0` so moving speed is preserved!).

### Step 4: ERTS Ordering, `dt_gnss` Teleport Decoupling, 1-Tick Glitch Pre-Gate & Backward Catch-Up Bound in `ExtendedRtsOdometrySmoother.kt` (`REQ-FIL-036.3` -> `TST-FIL-036.3`)
* **Target**: `app/src/main/java/com/atrainingtracker/banalservice/odometry/ExtendedRtsOdometrySmoother.kt` (`742 -> ~835` LoC)
* **Design**:
  1. **Non-Stationary-Neighbor 1-Tick Speed Glitch Pre-Gate (Gate 2 Advisory 2)**:
     * Add `preGateIsolatedSpeedGlitches(samples, stationaryMask): DoubleArray` (NaN when original is null/non-finite).
     * For each moving sample `i` with `wheelSpeedMps == null` and finite `speedMps > STATIONARY_SPEED_THRESHOLD_MPS`: collect finite non-stationary neighbor speeds (`> 0.25 m/s`, `!stationaryMask[j]`) within `j in (i-3)..(i+3)` (`j != i`, `|ts[j] - ts[i]| <= 5_000L`). Only when `>= 2` moving neighbors exist, compute `localMedian`. If `s > max(2.0 * localMedian, localMedian + 2.5)` AND neither immediate neighbor `i-1`/`i+1` (when moving) is `>= 0.75 * s`, replace with `localMedian` (preserves genuine launch `0, 0, 2.0, 2.2, 2.3` and genuine multi-second sprints `2.0 -> 3.5 -> 5.0 -> 6.0`!).
  2. **Speed-Before-Position & Calibrated Bounded Variance (Gate 2 Advisory 1)**:
     * In `smoothSamples()`, move moving speed update (`validWheelSpeed` / gated `validSpeed`) **BEFORE** `ekf.updateGnssPosition()`, using `AttFusedColdStartHelper.calibratedDopplerSpeedVariance(acc)` so `acc <= 20.0` uses `0.25..2.25` while `acc > 20.0` retains coarse `max(0.5, (acc * 0.25)^2)`.
  3. **`dt_gnss`-Normalized Step Discontinuity Detection & Forward+Backward Decoupling (Gate 1 Advisory 4)**:
     * Track `lastValidGnssEast`, `lastValidGnssNorth`, `lastValidGnssTsMs` across valid GNSS fixes (`acc <= 20.0`).
     * Flag `discontinuityMask[i] = true` when `hasTrustedGnss && hasLastValidGnss` and `dtGnssSec = (ts - lastValidGnssTsMs) / 1000.0` satisfies `dtGnssSec in 0.001..5.0` and `(stepDist / dtGnssSec) > max(6.0, localSpeedRef * 2.2 + 2.5)` (or immediately after stationary with `stepDist > max(5.0, localSpeedRef * dtGnssSec * 2.0 + 2.0)`). Note: `45 s` curved tunnels have `dtGnssSec = 45.0 > 5.0` -> never false-triggered!
     * On `discontinuityMask[i]`, decouple `transJac[0..1, 2]` & `priorCov[0..1, 2]` and pass `decoupleVelocity = true` to `ekf.updateGnssPosition(...)`.
  4. **Backward RTS Decoupling & Local Moving Speed Envelope Bound**:
     * Extend `smoothSnapshots(..., discontinuityMask: BooleanArray? = null, maxSpeedEnvelope: DoubleArray? = null)` (`@JvmOverloads`).
     * Apply `copyAndDecoupleVelocityPosition` whenever `currStationary || nextStationary || currDiscontinuity || nextDiscontinuity`.
     * Compute `maxSpeedEnvelope[k]` near discontinuity/stationary transitions (`within ±5` samples of any `discontinuityMask` or `stationaryMask` boundary) as `max(localGatedMovingSpeeds[k ± 4]) * 1.25 + 0.5` (falling back to `Double.POSITIVE_INFINITY` outside transition zones or when no moving speeds exist); clamp `smoothedStates[k][2] = min(smoothedStates[k][2], maxSpeedEnvelope[k])` so backward RTS can never invent a `5.73 m/s` spike while leaving genuine multi-second sprints untouched.

### Step 5: Targeted Unit Tests & Locked Incremental Gradle Verification (`TST-FIL-036.1`–`.3`)
* Add focused behavioral tests with **explicit hyphenated `// REQ-FIL-036.x / TST-FIL-036.x` comments** (`0` `src/main` reads per `REQ-PRO-049.1`):
  * **NEW** `AttFusedColdStartHelperTest.kt`: directed outdoor release, indoor handheld oscillating multipath rejection (straightness `< 0.55`), raw-chord heading seeding (`>= 4.0 m`), and bounded Doppler variance (`acc <= 20 m` vs `acc > 20 m`).
  * `SpeedAndLocationDevice_ATTFusedTest.kt`: Pixel 10 `t = 0..30 s` cold-start South/East `1.0–2.3 m/s` easy run (`acc = 18.9 -> 9.2 m`) releasing in `<= 5 s` without `>= 10 m` teleport; indoor handheld oscillating multipath negative test staying pinned (`0.0 m/s`, `0.0 m`).
  * `SpeedAndLocationDeviceIndoorAndRestCouplingTest.kt`: base-class veto precedence test confirming `shouldReleaseColdStartStationaryPin() == true` is STILL blocked by `mIsIndoorMode`, `extStationary`, and `accelAtRest`.
  * `UnicycleOdometryEkfTest.kt`: unanchored `decoupleVelocity = true` prevents Southward speed crushing, and `confirmedPostOutage` snap re-converges `[E, N]` without spiking `x[2]`.
  * `ExtendedRtsOdometrySmootherTest.kt` & `ErtsPostWorkoutRecomputerTest.kt`: `23 s` pinned standstill + `9.34 m` / `12.20 m` teleports (with & without isolated 1-tick `9.34 m/s` glitch) keeping canonical `SPEED_mps` & `ExtremumValues.MAX(SPEED_mps) < 3.5 m/s` with untouched raw columns; genuine launch (`0, 0, 2.0, 2.2`) & multi-second sprint (`2.0 -> 3.5 -> 5.0 -> 6.0 m/s`, peak `>= 5.5 m/s`); `45 s` curved tunnel & `acc > 20 m` Doppler dropout non-regression.
* **Locked Incremental Gradle Execution (`Rule 7.5` — NO `--rerun-tasks`, NO `-PstampGitHash=true`)**:
```bash
python3 tools/parallel_sprint_lock.py --timeout 1800 -- ./gradlew :app:testDebugUnitTest --rerun \
  --tests "*AttFusedColdStartHelperTest" \
  --tests "*SpeedAndLocationDevice_ATTFusedTest" \
  --tests "*SpeedAndLocationDeviceIndoorAndRestCouplingTest" \
  --tests "*SpeedAndLocationDeviceStationaryJitterTest" \
  --tests "*SpeedAndLocationDeviceDisplacementTest" \
  --tests "*UnicycleOdometryEkfTest" \
  --tests "*ExtendedRtsOdometrySmootherTest" \
  --tests "*ErtsPostWorkoutRecomputerTest" \
  --tests "*ArchitectureConformanceContractTest" \
  --tests "*DeadCodeEliminationContractTest" \
  --tests "*SystemLiveTrackingRequirementsContractTest" \
  --tests "*SourceTextReadingAuditContractTest"
```

---

## 4. Verification & Rollback Plan
* **Verification**: `100%` pass across all 12 targeted/contract suites above (`0` failures, `0` skipped, `0` new compiler warnings), `SpeedAndLocationDevice_ATTFused.kt <= 1,485 / 1,500` LoC, `0` protected monolith edits, and both `verify_requirement_governance.py --repo-root . --base-ref refs/heads/sprint/2026-11-20_01_Früh` and `--check-verified-evidence` exiting `0`.
* **Rollback**: Isolated cleanly on `feature/ATT-9158`; `sprint/2026-11-20_01_Früh` remains untouched until Stage 6 `PASS` and atomic `--merge-ticket`.
