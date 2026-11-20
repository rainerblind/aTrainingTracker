# Stage 5 Walkthrough: ATT-9158 - Fix ATT Fused Cold-Start 23-Second Pinning Freeze and Post-Unpin ERTS Catch-Up Velocity Overshoot

**Ticket**: `ATT-9158` (`2 SP`, `Bug`, `severity-major`) | **Parent Epic**: `ATT-166` | **Target Release**: `V5.0.0`  
**Active Sprint**: `2026-11-20_01_Früh` (`465`) | **Branch**: `feature/ATT-9158` (base `6b4ac2c5`, Gate 4 verified code SHA `97f49453`) | **Author**: AI Agent 1 (`Pro` / Neon) | **Date**: 2026-11-20  
**Verified Requirements**: `REQ-FIL-036` (`REQ-FIL-036.1`–`.3`, `[Verified]`) | **Verified Tests**: `TST-FIL-036` (`TST-FIL-036.1`–`.3`, `[Verified]`)

---

## 1. Summary of Changes & Bug Fix Verification (`REQ-PRO-121.2`)
* **Pre-Fix Defect Profile (`Kurze Runde var 2 #53`, Pixel 10)**:
  1. `att_fused` stayed pinned at `0.0 m/s` / `0.0 m` for `23 s` (`t = 1..23 s`) under `9.2–18.9 m` cold-start GPS accuracy because `SpeedAndLocationDevice.onNewLocation()` required `distFromAnchor > max(3.0, 0.6 * acc)` (`11.34 m`) on lagged EKF coordinates while unanchored default North (`PI/2`) heading crushed `ekf.speedMps` and unbounded `(acc * 0.25)^2` (`22.3 (m/s)^2`) Doppler variance starved convergence, causing `9.34 m` (`t = 24 s`) and `12.20 m` (`t = 31 s`) unpin/cluster-snap jumps.
  2. Post-workout `ExtendedRtsOdometrySmoother` ran `updateGnssPosition()` before `updateSpeed()` and coupled backward RTS `C_k[2, 0..1]` across the teleport jump, synthesizing an artificial `5.73 m/s` (`20.6 km/h`) peak (`t = 30–34 s`) that overwrote `ExtremumValues.MAX(SPEED_mps)` on an `8:15/km` easy run.
* **Post-Fix Implementation (All Touched Files `<= 1,500` LoC, `0` Protected Monolith Edits)**:
  1. **`AttFusedColdStartHelper.kt` (`256 / 1,500` LoC, new pure-JVM `@Synchronized` helper in `banalservice.odometry`)**:
     - Implements `calibratedDopplerSpeedVariance(acc)` returning `((acc * 0.08)^2).coerceIn(0.25, 2.25)` strictly for trusted fixes (`0 < acc <= 20 m`) while preserving coarse `max(0.5, (acc * 0.25)^2)` distrust for `acc > 20 m`.
     - Releases cold-start pinning strictly on `>= 2` consecutive corroborated motion fixes AND `>= 3` directed raw-GNSS samples (`netDist >= 3.0 m`, straightness `>= 0.55`), blocking indoor handheld oscillating multipath while unpinning outdoor easy runs within `<= 5 s` without `>= 10 m` teleports.
     - Emits one-shot raw-chord heading seed (`>= 3` samples, `netDist >= 4.0 m`, straightness `>= 0.55`) and `decouplePositionVelocity = true` while heading remains unanchored.
  2. **`SpeedAndLocationDevice.java` (`824 / 1,500` LoC) & `SpeedAndLocationDevice_ATTFused.kt` (`1,481 / 1,500` LoC, `19` LoC headroom)**:
     - Added default-`false` `protected boolean shouldReleaseColdStartStationaryPin(Location)` in `SpeedAndLocationDevice.java`, evaluated strictly AFTER `if (mIsIndoorMode)` and BEFORE `if (shouldUnpin && (extStationary || accelAtRest))`, keeping indoor/wheel/rest vetoes structurally unbypassable and `GPS`/`GoogleFused` byte-identical.
     - Surgically wired `coldStartHelper` in `SpeedAndLocationDevice_ATTFused.kt` (`+22` LoC net), seeding initial heading **before** `ekf.updateGnssPosition()` on the same tick.
  3. **`UnicycleOdometryEkf.kt` (`844 / 1,500` LoC)**:
     - Added one-shot `seedInitialHeading(headingRad, varianceRad, timestampMs)` zeroing heading cross-covariances so 180° South/West starts align cleanly in one tick.
     - Added `@JvmOverloads decoupleVelocity: Boolean = false` on `updateGnssPosition()` and automatic velocity decoupling during `confirmedPostOutage` covariance inflation so neither unanchored updates nor outage snaps corrupt `x[2]`.
  4. **`ExtendedRtsOdometrySmoother.kt` (`920 / 1,500` LoC)**:
     - Reordered `updateSpeed()` before `updateGnssPosition()` with `calibratedDopplerSpeedVariance(acc)`; added non-stationary-neighbor 1-tick geometric speed glitch pre-gating (`>= 2` moving neighbors, preserving genuine launches & multi-second `6.0 m/s` sprints); added `dt_gnss`-normalized step-discontinuity detection (`dt_gnss <= 5.0 s` using pre-gated speed, preserving `45 s` curved tunnels); and decoupled forward+backward `[0..1, 2]` covariance with a transition speed envelope applied to both `lastIdx` and `n - 2 downTo 0` in `smoothSnapshots()`.
  5. **Protected Monoliths & Localization (`0` LoC Edits)**: `TrackerService.java`, `DeviceManager.java`, `DevicesDatabaseManager.java`, `WorkoutSummariesDatabaseManager.java`, `WorkoutSamplesDatabaseManager.java`, `WorkoutRepository.kt`, `ErtsPostWorkoutRecomputer.kt`, and `values*/strings_*.xml` (`Localization Parity: N/A - no UI strings`) remain 100% untouched.

---

## 2. Verification Evidence (Gate 4 Reuse per `Rule 7.5` — `0` `app/` Diff Since `97f49453`)
- **Tier 1 Targeted Incremental `--rerun` Suite Results (`ATT-9246` Gate 4 Independently Verified on `97f49453` — `144/144 PASS`, `0` Failures, `0` Errors, `0` Skipped, `0` New Compiler Warnings, `:app:assembleDebug` PASS)**:

| Test Suite | Result | Verified Scope / Verbatim Test Methods |
| :--- | :---: | :--- |
| `AttFusedColdStartHelperTest` | `3/3 PASS` | `TST-FIL-036.1`/`.2`: `testDirectedOutdoorMotion_releasesColdStartPinAndSeedsSouthHeading_tstFil036_1`, `testIndoorHandheldOscillatingMultipath_rejectedByAntiOscillationGuard_tstFil036_1`, `testCalibratedDopplerSpeedVariance_boundsTrustedFixesAndPreservesUntrustedDistrust_tstFil036_2` |
| `SpeedAndLocationDevice_ATTFusedTest` | `33/33 PASS` | `TST-FIL-036.1`/`.2`: `testColdStartSouthEasyRun_releasesPinPromptlyWithout23sFreezeOrTeleport_tstFil036_1_2`, `testIndoorHandheldOscillatingMultipath_remainsPinnedWithZeroDistanceCreep_tstFil036_1` + all 31 regressions (`TST-FIL-023.1`, `TST-FIL-028`, `TST-FIL-034`) |
| `SpeedAndLocationDeviceIndoorAndRestCouplingTest` | `11/11 PASS` | `TST-FIL-036.1`: `testColdStartReleaseHook_cannotBypassIndoorExternalWheelOrAccelRestVetoes_tstFil036_1` + all 10 `TST-TRK-077` regressions |
| `SpeedAndLocationDeviceStationaryJitterTest` | `15/15 PASS` | `REQ-TRK-048`/`074` stationary jitter & deadband non-regression |
| `SpeedAndLocationDeviceDisplacementTest` | `4/4 PASS` | `REQ-TRK-056` displacement & escape-radius non-regression |
| `UnicycleOdometryEkfTest` | `18/18 PASS` | `TST-FIL-036.2`: `testSeedInitialHeadingAndVelocityDecoupling_snaps180DegAndBlocksOutageSpeedKick_tstFil036_2` + all 17 regressions |
| `ExtendedRtsOdometrySmootherTest` | `14/14 PASS` | `TST-FIL-036.3`: `testPostUnpinTeleportAnd1TickGlitch_suppresses573MpsCatchUpOvershoot_tstFil036_3`, `testGenuineLaunchAndMultiSecondSprint_notClippedByTeleportOrGlitchGates_tstFil036_3` + `45 s` curved tunnel & `acc > 20 m` Doppler dropout regressions |
| `ErtsPostWorkoutRecomputerTest` | `20/20 PASS` | `TST-FIL-036.3`: `testPostUnpinTeleportRun_boundsCanonicalSpeedAndMaxExtremaAndPreservesRawColumns_tstFil036_3` + all 19 end-to-end SQLite regressions |
| `ArchitectureConformanceContractTest` | `11/11 PASS` | Monolith LoC ceilings (`SpeedAndLocationDevice_ATTFused.kt = 1,481 / 1,500`, `0` protected monolith edits) |
| `DeadCodeEliminationContractTest` | `5/5 PASS` | Zero dead code / unused helpers |
| `SourceTextReadingAuditContractTest` | `6/6 PASS` | `0` `src/main` reads in tests (`REQ-PRO-049.1`) |
| `SystemLiveTrackingRequirementsContractTest` | `4/4 PASS` | `SYSREQ-TRK-003`/`004`/`010` <-> `REQ-FIL-036` traceability |

- **Safety Linters, Governance & Code-Freeze Checks (ALL `exit 0`)**:
  - **4/4 Python Safety Linters**: `check_secrets_hygiene.py`, `lint_cursor_safety.py`, `lint_lifecycle_safety.py`, `lint_safe_enum.py` -> ALL `PASS (exit 0)`.
  - **Governance & Clean-Checkout Evidence**: `verify_requirement_governance.py --repo-root . --sync-indexes`, `--base-ref refs/heads/sprint/2026-11-20_01_Früh`, and `--check-verified-evidence --test-results-dir /tmp/empty_check_ATT-9158_s5` -> ALL **`exit 0`** (`100%` `[Verified]` parity, static `TST-FIL-036` tags indexed across 6 suites).
  - **Zero `app/` Diff Check (`Rule 7.5`)**: `git diff 97f49453..HEAD -- app/` -> **empty (`0` bytes changed since Gate 4 `PASS`)**.

---

## 3. Handoff Status
- `feature/ATT-9158` preserved intact atop `sprint/2026-11-20_01_Früh` (`6b4ac2c5`, NO premature merge), ready for Gate 5 review and Stage 6 Cold Final Review (`Final Review (Agent)`).
