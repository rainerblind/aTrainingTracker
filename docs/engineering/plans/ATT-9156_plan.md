# Stage 3: Implementation Plan - ATT-9156: Gate DEM Grid-Step Jumps and Retune DEM Variance in Live ATT Fused Altitude/Slope EKF

**Ticket**: `ATT-9156` (`2 SP`, `Bug`, `severity-major`) | **Parent Epic**: `ATT-166` | **Target Release**: `V5.0.0`  
**Active Sprint**: `2026-11-20_01_Früh` (`465`) | **Branch**: `feature/ATT-9156` | **Author**: AI Agent 1 (`Pro` / Neon) | **Date**: 2026-11-20  
**Requirement Mapping**: `REQ-FIL-037` (`REQ-FIL-037.1`–`.3`) | **Test Mapping**: `TST-FIL-037` (`TST-FIL-037.1`–`.3`)

---

## 1. Architecture, Threading & LoC Budget (`SWE.2` / `SWE.3`)
* **Layer Boundaries**: All algorithmic changes stay strictly inside the pure-JVM EKF (`KinematicAltitudeSlopeEkf.kt`, `banalservice/odometry/`, `@Synchronized`, zero Android dependencies) and its verified live host (`AltitudeAndSlopeDevice_ATTFused.kt`, `banalservice/devices/`, `@Synchronized` 1-Hz `ScheduledExecutorService`, zero UI-thread I/O). Zero GMS/Play Services dependency, zero DB schema changes, zero UI/Compose changes (`Localization Parity: N/A - no UI strings`), and zero edits to `TrackerService`/`BANALService`/`MyBTLEDevice` (`REQ-PRO-114` N/A).
* **Touched Production Files (All `<= 1,500` LoC; `0` Protected Monolith Edits)**:
  * `app/src/main/java/com/atrainingtracker/banalservice/odometry/KinematicAltitudeSlopeEkf.kt`: `503 -> ~590 / 1,500` LoC (`+87` LoC max; `> 900` LoC headroom; `@JvmOverloads` preserves all existing 1-arg/2-arg callers).
  * `app/src/main/java/com/atrainingtracker/banalservice/devices/AltitudeAndSlopeDevice_ATTFused.kt`: `513 -> ~515 / 1,500` LoC (`0–2` LoC; already passes `KinematicAltitudeSlopeEkf.DEFAULT_VAR_DEM` / `DEFAULT_VAR_GPS` at `:362`, `:416`, `:442`; verified end-to-end via `AltitudeAndSlopeDevice_ATTFusedTest`).
* **Protected Monoliths (`0` LoC Touched)**: `TrackerService.java`, `WorkoutSummariesDatabaseManager.java`, `WorkoutSamplesDatabaseManager.java`, `WorkoutRepository.kt`, `DeviceManager.java`, `PhoneCarriageClassifier.kt`, `SpeedAndLocationDevice_ATTFused.kt`.

---

## 2. System Invariants & Preserved Contracts
1. **Barometer & 2-State Continuity (`REQ-FIL-016`, `REQ-FIL-026`)**: `DEFAULT_VAR_BARO = 0.25`, 2-state `p00 = varBaro`, 1-Hz barometric median bucketing, and adaptive pocket-jitter variance remain untouched.
2. **Initial Weather-Offset Re-Anchoring (`REQ-FIL-022`)**: `!isInitialized` and `Baro-before-Ref` (`is3StateMode && !hasReferenceAnchor`) snaps (`+130.4 m`) plus `resetAscentDescentAnchor()` are completely preserved; step-discrepancy gating applies strictly post-anchor (`hasReferenceAnchor == true`).
3. **DEM > GPS Precedence & Hysteresis (`REQ-FIL-020.1`, `REQ-FIL-020.2`)**: `DEFAULT_VAR_GPS (64.0) > DEFAULT_VAR_DEM (25.0)` preserves DEM accuracy superiority and `< 1.0 m` recovery in `testThreeStateGpsBeforeDem`; `2.0 m` ascent/descent hysteresis deadband remains intact.
4. **Barometer-Free Slope & High-Precision Calibration**: When `!hasBarometerSample && !isStandstill`, `k1Eff = (p10 / s) * gainScale` remains active for DEM-only slope tracking; when `varRef <= 1.0` (`enableStepGating = false`), high-precision known-location/manual anchors bypass step gating and Huber inflation completely.
5. **Governance & Hygiene**: Gate 3 Code Lock (`0` edits under `app/` in Stage 3), `0` new `src/main` reads in tests (`REQ-PRO-049.1`), explicit hyphenated `// REQ-FIL-037.x / TST-FIL-037.x` comments in all new tests (`REQ-PRO-099.3`), and `Localization Parity: N/A - no UI strings`.

---

## 3. Atomic Stage 4 Implementation Sequence

### Step 0: Gate 3 Code Lock Verification (`Rule 4`)
* Run `python3 /home/rainer/AndroidStudioProjects/aTrainingTracker/tools/jira_util.py check-gate <IMPL_PLAN_SUBTASK_KEY>` and verify `GATE_PASSED` (exit `0`) before modifying any file under `app/src/`.

### Step 1: Coherent DEM/GPS Variance Retuning & Reference/Baro Tracker State (`REQ-FIL-037.1`, `REQ-FIL-037.2` -> `TST-FIL-037.1`, `TST-FIL-037.2`)
* **Target**: `app/src/main/java/com/atrainingtracker/banalservice/odometry/KinematicAltitudeSlopeEkf.kt` (`:52-176`, `:260-307`)
* **Design**:
  1. **Constants**: Retune `DEFAULT_VAR_DEM = 25.0` (`±5 m`, from `4.0`) and `DEFAULT_VAR_GPS = 64.0` (`±8 m`, from `16.0`, strictly `> DEFAULT_VAR_DEM`). Add `DEM_STEP_GATE_THRESHOLD_M = 3.0`, `DEM_ABRUPT_STEP_THRESHOLD_M = 8.0`, `DEM_ABRUPT_STEP_FLAT_BARO_M = 1.0`, and `DEM_HIGH_PRECISION_VAR_THRESHOLD = 1.0`.
  2. **Tracker Fields (Gate 2 Tip #2)**: Add private fields `lastBaroAltM = Double.NaN` (strictly separate from `lastMovingBaroAltM`), `lastRefAltM = Double.NaN`, and `lastRefBaroAltM = Double.NaN`.
  3. **Lifecycle Reset**: Clear all three (`lastBaroAltM`, `lastRefAltM`, `lastRefBaroAltM = Double.NaN`) in `reset()`; clear `lastRefAltM = Double.NaN` and `lastRefBaroAltM = Double.NaN` in `setMode()`.
  4. **Barometer Tracker Updates (Gate 2 Tips #1 & #2)**:
     * In `updateBarometer()` `!isInitialized` (`:268-279`): set `lastBaroAltM = baroAltM`.
     * In `updateBarometer()` `Ref-before-Baro` (`:281-287`): set BOTH `lastBaroAltM = baroAltM` AND `lastRefBaroAltM = baroAltM` so the first post-anchor DEM update computes true `Δbaro` rather than `NaN` or `baroAltM - 0.0`.
     * In `updateBarometer()` `isStandstill` (`:290-306`) and moving path (`:308+`): update `lastBaroAltM = baroAltM` on BOTH standstill and moving samples so standstill barometric drift is captured in `Δbaro` on post-stop DEM updates.

### Step 2: Post-Anchor Step-Discrepancy Gating, Huber Variance Inflation, Slope Decoupling & 3×3 Joseph SPD Update (`REQ-FIL-037.1`, `REQ-FIL-037.2` -> `TST-FIL-037.1`, `TST-FIL-037.2`)
* **Target**: `app/src/main/java/com/atrainingtracker/banalservice/odometry/KinematicAltitudeSlopeEkf.kt` (`:396-482`)
* **Design**:
  1. **Signature**: Annotate `updateReferenceAltitude` with `@JvmOverloads`:
     `fun updateReferenceAltitude(refAltM: Double, varRef: Double = rDem, enableStepGating: Boolean = varRef > DEM_HIGH_PRECISION_VAR_THRESHOLD): Boolean`.
  2. **Initial Anchor Tracker Seeding (Gate 2 Tip #1)**: On both `!isInitialized` (`:406-416`) and `Baro-before-Ref` (`:418-434`), seed `lastRefAltM = refAltM` and `lastRefBaroAltM = if (hasBarometerSample) lastBaroAltM else Double.NaN`.
  3. **Post-Anchor Baro-vs-Ref Step-Discrepancy Gating + Tracker Advancement (Gate 1 Advisories #1 & #4)**:
     * Compute `stepVarRef = varRef`. When `enableStepGating && hasBarometerSample && !lastRefAltM.isNaN() && !lastRefBaroAltM.isNaN() && !lastBaroAltM.isNaN()`:
       `deltaRef = refAltM - lastRefAltM`, `deltaBaro = lastBaroAltM - lastRefBaroAltM`, `stepDiscrepancy = abs(deltaRef - deltaBaro)`.
       If `stepDiscrepancy > DEM_STEP_GATE_THRESHOLD_M`: inflate quadratically `baseFactor = 1.0 + stepDiscrepancy * stepDiscrepancy`; if `abs(deltaRef) >= DEM_ABRUPT_STEP_THRESHOLD_M && abs(deltaBaro) < DEM_ABRUPT_STEP_FLAT_BARO_M`, multiply by `4.0`; `stepVarRef = varRef * factor`.
     * **Always advance** `lastRefAltM = refAltM` and (when `hasBarometerSample && !lastBaroAltM.isNaN()`) `lastRefBaroAltM = lastBaroAltM` so persistent corrections (`453 -> 438 m`) converge cleanly on ticks `2..10` while transient DEM bounces (`440 -> 427 -> 440 m`) inflate on BOTH edges.
  4. **Quadratic Innovation-Based Huber Variance Inflation (Gate 1 Advisory #2)**:
     * Compute `y = refAltM - altitude`, `sStep = p00 + stepVarRef`, `normRes = abs(y) / sqrt(max(1e-9, sStep))`.
     * When `enableStepGating && normRes > huberK`: `effectiveVarRef = stepVarRef * (normRes / huberK) * (normRes / huberK)`; otherwise `effectiveVarRef = stepVarRef`.
     * Use `s = p00 + effectiveVarRef` and unclipped `y` with `effectiveVarRef` (for `enableStepGating == true`, quadratic variance inflation smoothly bounds `k0 * y` without collapsing `p00` and preserves Joseph consistency; for `enableStepGating == false`, high-precision calibration anchors apply unimpeded).
  5. **Standstill & Baro-Active Slope Decoupling + Exact Joseph SPD Covariance (Gate 1 Advisory #3)**:
     * Compute `isStandstill = lastSpeedMps < standstillSpeedThresholdMps` and `k1Eff = if (hasBarometerSample || isStandstill) 0.0 else (p10 / s) * gainScale` in BOTH 2-state and 3-state branches.
     * Apply exact symmetric 3×3 Joseph SPD update `P^+ = (I - KH)P(I - KH)^T + K R K^T` (`H = [1, 0, 0]`, `K = [k0, k1Eff, k2]^T`, `R = effectiveVarRef`, mirroring `ExtendedRtsAltitudeSlopeSmoother.kt:428-451`) in 3-state mode and exact 2×2 Joseph SPD update in 2-state mode, clamping diagonals (`p00, p22 >= 1e-6`, `p11 >= 1e-8`) and enforcing exact symmetry (`p10 = p01`, `p20 = p02`, `p21 = p12`).

### Step 3: Live Host Verification (`AltitudeAndSlopeDevice_ATTFused.kt`, `REQ-FIL-037.3` -> `TST-FIL-037.3`)
* **Target**: `app/src/main/java/com/atrainingtracker/banalservice/devices/AltitudeAndSlopeDevice_ATTFused.kt` (`:354-368`, `:405-448`)
* **Design**: Confirm all reference calls (`handleReferenceAltitude`, `checkReferenceAltitudeFromSensors`) invoke `ekf.updateReferenceAltitude(alt, KinematicAltitudeSlopeEkf.DEFAULT_VAR_DEM)` / `DEFAULT_VAR_GPS` (auto-enabling `enableStepGating = true` via default parameter) without touching `REQ-FIL-020.1`/`.2` or `REQ-FIL-022.3` logic; add `// REQ-FIL-037.3` KDoc/inline traceability comment if needed (`<= 2` LoC).

### Step 4: Targeted Unit Tests & Locked Incremental Gradle Verification (`TST-FIL-037.1`–`.3`)
* **Test Files** (every new test tagged with explicit hyphenated `// REQ-FIL-037.x / TST-FIL-037.x` comments; `0` `src/main` source-text reads):
  1. `app/src/test/java/com/atrainingtracker/banalservice/odometry/KinematicAltitudeSlopeEkfTest.kt`:
     * `TST-FIL-037.1`: verify `DEFAULT_VAR_DEM == 25.0`, `DEFAULT_VAR_GPS == 64.0 > DEFAULT_VAR_DEM`, `DEFAULT_VAR_BARO == 0.25`; verify `13–21 m` DEM jump with active flat barometer leaves `slope` decoupled (`|slopePercent| < 1%`), `|Δalt|` strongly damped, and 3×3 covariance symmetric SPD (`p00, p11, p22 > 0`, `det(P) > 0`, principal minors `> 0`); verify barometer-free DEM-only climb (`!hasBarometerSample`) still produces positive `slope > 0`.
     * `TST-FIL-037.2`: verify transient `440 -> 427 -> 440 m` and `±21 m` DEM bounces with flat baro produce `|Δalt| < 0.5 m` without collapsing `p00`; verify `Ref-before-Baro` and post-standstill baro drift seed/track `Δbaro` accurately; verify persistent GPS (`453 m`) -> DEM (`438 m`) recovery converges `< 1.0 m` (`baroBias` accurate); verify high-precision anchor (`varRef = 0.5`, `enableStepGating = false`) applies unimpeded; verify `reset()`/`setMode()` clear step-gating trackers cleanly.
  2. `app/src/test/java/com/atrainingtracker/banalservice/devices/AltitudeAndSlopeDevice_ATTFusedTest.kt`:
     * `TST-FIL-037.3`: synthetic Pixel 10 `Kurze Runde var 2 #53` loop replay (`~60 m` true climb/descent at `2.0 m/s`, repeated `13–21 m` DEM grid jumps + slow barometric weather drift) asserting live `ASCENT` and `DESCENT` in `55–75 m` (not `167 m`), live `|SLOPE|` physically plausible (no `-35%/+34%` artifacts), `|Δalt/dt|` smooth, and closing `ALTITUDE` anchored near true MSL.
* **Locked Incremental Verification Command (`Rule 7.5` — NEVER `--rerun-tasks`, NEVER `-PstampGitHash=true`)**:
  ```bash
  python3 tools/parallel_sprint_lock.py --timeout 1800 -- ./gradlew :app:testDebugUnitTest --rerun \
    --tests "com.atrainingtracker.banalservice.odometry.KinematicAltitudeSlopeEkfTest" \
    --tests "com.atrainingtracker.banalservice.devices.AltitudeAndSlopeDevice_ATTFusedTest" \
    --tests "com.atrainingtracker.banalservice.odometry.ExtendedRtsAltitudeSlopeSmootherTest" \
    --tests "com.atrainingtracker.ArchitectureConformanceContractTest" \
    --tests "com.atrainingtracker.DeadCodeEliminationContractTest" \
    --tests "com.atrainingtracker.SystemLiveTrackingRequirementsContractTest" \
    --tests "com.atrainingtracker.SourceTextReadingAuditContractTest"
  ```

### Step 5: Living Docs Promotion to `[Implemented]` & Governance Gate
* Promote `REQ-FIL-037` (`docs/requirements/req_tracking_cockpit_part2.md`) and `TST-FIL-037` (`docs/tests/tst_tracking_cockpit.md`) from `[Planned]` -> `[Implemented]` with exact test citations, bump header versions + Change History rows, run `--sync-indexes`, and verify BOTH `verify_requirement_governance.py --base-ref refs/heads/sprint/2026-11-20_01_Früh` and `--check-verified-evidence` exit `0`.

---

## 4. Requirement-to-Step & Test Traceability Matrix

| Requirement | Plan Step(s) | Target File(s) | Test Case | Test Suite |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-FIL-037.1` | Steps 1, 2 | `KinematicAltitudeSlopeEkf.kt` | `TST-FIL-037.1` | `KinematicAltitudeSlopeEkfTest` |
| `REQ-FIL-037.2` | Steps 1, 2 | `KinematicAltitudeSlopeEkf.kt` | `TST-FIL-037.2` | `KinematicAltitudeSlopeEkfTest` |
| `REQ-FIL-037.3` | Steps 3, 4 | `AltitudeAndSlopeDevice_ATTFused.kt`, `KinematicAltitudeSlopeEkf.kt` | `TST-FIL-037.3` | `AltitudeAndSlopeDevice_ATTFusedTest`, Governance Contracts |
