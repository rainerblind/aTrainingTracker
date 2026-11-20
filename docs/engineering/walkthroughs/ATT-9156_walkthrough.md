# Stage 5: Verification & Walkthrough - ATT-9156: Gate DEM Grid-Step Jumps and Retune DEM Variance in Live ATT Fused Altitude/Slope EKF

**Ticket**: `ATT-9156` (`2 SP`, `Bug`, `severity-major`) | **Parent Epic**: `ATT-166` | **Target Release**: `V5.0.0`  
**Active Sprint**: `2026-11-20_01_Früh` (`465`) | **Branch**: `feature/ATT-9156` (preserved unmerged for Stage 6 Cold Final Review)  
**Verified Production SHA (Gate 4 `ATT-9260`)**: `0b792832` (`git diff 0b792832..HEAD -- app/` = EMPTY, `Rule 7.5` doc-only Stage 5)  
**Requirement / Test IDs**: `REQ-FIL-037` (`REQ-FIL-037.1`–`.3`) [Verified] ↔ `TST-FIL-037` (`TST-FIL-037.1`–`.3`) [Verified]  
**Author**: AI Agent 1 (`agent1`, Pro/Neon) | **Date**: 2026-11-20

---

## 1. Root Cause & Bug Fix Verification Evidence (`REQ-PRO-121.2`)

### Pre-Fix Defect Profile (Pixel 10 `Kurze Runde var 2 #53`, `2026-11-19_184953`, `_id = 2414`)
* Live `att_fused_altitude_slope` reported **`167.1 m` ascent / `166.0 m` descent** (vs `~58–64 m` true gain/loss), **`-35%..+34%` slope swings**, and **`253` samples with `|Δalt/dt| > 0.5 m/s`** because `KinematicAltitudeSlopeEkf` trusted DEM at `DEFAULT_VAR_DEM = 4.0 m²` (`±2 m`), coupled DEM grid jumps (`13–21 m` in `1–2 s`) directly into `slope` (`k1Eff`) even with an active barometer, lacked baro-vs-DEM step-discrepancy jump gating and innovation Huber variance inflation, and used non-Joseph `(I - KH)P` covariance updates.

### Post-Fix Behavior & Quantitative Proof (Verified on SHA `0b792832`)
1. **Coherent DEM/GPS Variance Retuning, Slope Decoupling & Exact Joseph SPD (`REQ-FIL-037.1` / `TST-FIL-037.1`)**:
   * Retuned `KinematicAltitudeSlopeEkf.DEFAULT_VAR_DEM = 25.0 m²` (`±5 m`) and `DEFAULT_VAR_GPS = 64.0 m²` (`±8 m`, strictly `> DEFAULT_VAR_DEM` preserving `REQ-FIL-020.1` DEM precedence), added `!refAltM.isFinite() || !varRef.isFinite() || varRef <= 0.0` guard, zeroed `k1Eff = 0.0` whenever `hasBarometerSample || isStandstill`, and replaced `(I - KH)P` with exact symmetric `josephUpdate2x2()` and `josephUpdate3x3()` (`P^+ = (I - KH)P(I - KH)^T + K R K^T`).
   * *Proof*: `KinematicAltitudeSlopeEkfTest.testCoherentVarianceRetuningAndBaroSlopeDecouplingWithJosephSpd_tstFil037_1` — `-13 m` / `+21 m` DEM jumps with active flat barometer leave `slope` decoupled (`|slopePercent| < 1%`), `|Δalt| < 0.5 m`, and 3×3 covariance symmetric SPD (`p00, p11, p22 > 0`, positive principal minors & determinant), while barometer-free DEM-only climb (`!hasBarometerSample`) still estimates positive slope (`> 3%`).
2. **Post-Anchor Step-Discrepancy Gating, Tracker Advancement, Quadratic Huber Inflation & High-Precision Guard (`REQ-FIL-037.2` / `TST-FIL-037.2`)**:
   * Added `@JvmOverloads enableStepGating: Boolean = varRef > DEM_HIGH_PRECISION_VAR_THRESHOLD` (`1.0 m²`), post-anchor `|Δref - Δbaro| > 3.0 m` quadratic variance inflation (with `4×` abrupt flat-baro multiplier for `|Δref| >= 8.0 m && |Δbaro| < 1.0 m`), unconditional `lastRefAltM`/`lastRefBaroAltM` advancement, `Ref-before-Baro` & standstill `lastBaroAltM` tracking (separate from `lastMovingBaroAltM`), and innovation-based quadratic Huber variance inflation + residual bound (`DEM_INNOVATION_HUBER_K = 2.5`).
   * *Proof*: `KinematicAltitudeSlopeEkfTest.testStepDiscrepancyGatingTrackerAdvancementHuberAndHighPrecisionGuard_tstFil037_2` (`440 -> 427 -> 440 -> 461 -> 461 -> 440 m` bounces stay `< 0.5 m`, zero `p00` collapse; `Ref-before-Baro` + standstill drift gates first post-anchor `-15 m` jump; `varRef = 0.5` calibration anchor pulls `> 6.0 m` immediately) and non-regression `KinematicAltitudeSlopeEkfTest.testThreeStateGpsBeforeDem_recoversFromCoarseGpsAnchorWithoutBiasFreeze` (`453 m` GPS -> `438 m` DEM converges `< 1.0 m` with accurate `+130.4 m` `baroBias`).
3. **End-to-End Synthetic Pixel 10 Loop Replay (`REQ-FIL-037.3` / `TST-FIL-037.3`)**:
   * *Proof*: `AltitudeAndSlopeDevice_ATTFusedTest.testSyntheticPixel10KurzeRunde53LoopReplay_gatesDemGridJumpsAndPreservesMslClosure_tstFil037_3` — `720 s` `2.0 m/s` loop (`~60 m` true climb/descent, repeated `13–21 m` DEM grid jumps, `+130.4 m` initial offset + gradual `+3.0 m` weather drift) keeps live `ASCENT` and `DESCENT` in `55–75 m` (eliminating `167 m` inflation), live `|SLOPE| <= 20%` (no `-35%/+34%` artifacts), `|Δalt/dt| < 1.5 m/s`, and closing `ALTITUDE` within `±5.0 m` of true `440.0 m` MSL.

---

## 2. Independent Verification Evidence (`Rule 7.5` Reuse of Gate 4 `ATT-9260` on `0b792832`)

Zero files under `app/` were modified after Gate 4 verified commit `0b792832` (`git diff 0b792832..HEAD -- app/` is empty).

| Verification Suite / Check | Scope | Result |
| :--- | :--- | :--- |
| `KinematicAltitudeSlopeEkfTest` (`app/src/test/java/com/atrainingtracker/banalservice/odometry/KinematicAltitudeSlopeEkfTest.kt`) | `TST-FIL-037.1`, `TST-FIL-037.2`, `TST-FIL-016`, `TST-FIL-022`, `TST-FIL-023`, `TST-FIL-026` | **16/16 PASS** |
| `AltitudeAndSlopeDevice_ATTFusedTest` (`app/src/test/java/com/atrainingtracker/banalservice/devices/AltitudeAndSlopeDevice_ATTFusedTest.kt`) | `TST-FIL-037.3` Pixel 10 loop replay, `TST-FIL-020`, `TST-FIL-022`, `TST-FIL-026`, `TST-FIL-028` | **22/22 PASS** |
| `ExtendedRtsAltitudeSlopeSmootherTest` (`app/src/test/java/com/atrainingtracker/banalservice/odometry/ExtendedRtsAltitudeSlopeSmootherTest.kt`) | `TST-FIL-033` offline altitude/slope ERTS parity non-regression | **8/8 PASS** |
| `ArchitectureConformanceContractTest` (`app/src/test/java/com/atrainingtracker/trainingtracker/build/ArchitectureConformanceContractTest.kt`) | LoC ceilings & protected monoliths (`0` monolith edits) | **11/11 PASS** |
| `DeadCodeEliminationContractTest` (`app/src/test/java/com/atrainingtracker/trainingtracker/build/DeadCodeEliminationContractTest.kt`) | Production reachability & zero dead code | **5/5 PASS** |
| `SourceTextReadingAuditContractTest` (`app/src/test/java/com/atrainingtracker/trainingtracker/build/SourceTextReadingAuditContractTest.kt`) | `REQ-PRO-049.1` (`0` new `src/main` reads) | **6/6 PASS** |
| `SystemLiveTrackingRequirementsContractTest` (`app/src/test/java/com/atrainingtracker/trainingtracker/build/SystemLiveTrackingRequirementsContractTest.kt`) | `SYSREQ-TRK-003`/`004`/`008` parity | **4/4 PASS** |
| **Total Unit & Contract Suites (`--rerun`)** | **7 suites + `:app:assembleDebug` GREEN (`0` new Kotlin compiler warnings)** | **72/72 PASS** (`0` fail, `0` err, `0` skip) |
| **Python Safety Linters (4/4)** | `check_secrets_hygiene.py`, `lint_cursor_safety.py`, `lint_lifecycle_safety.py`, `lint_safe_enum.py` | **4/4 PASS (`exit 0`)** |
| **Governance & Clean-Checkout Evidence** | `verify_requirement_governance.py --base-ref` & `--check-verified-evidence` (empty test dir) | **PASS (`exit 0`)** |

---

## 3. Architecture, LoC Budget & Non-Functional Invariants
* `KinematicAltitudeSlopeEkf.kt`: **`614 / 1,500` LoC** (`886` LoC headroom; `@Synchronized` thread-safe pure JVM).
* `AltitudeAndSlopeDevice_ATTFused.kt`: **`514 / 1,500` LoC** (`986` LoC headroom).
* Protected Monoliths: **`0` LoC touched** (`TrackerService.java`, `WorkoutSummariesDatabaseManager.java`, `WorkoutSamplesDatabaseManager.java`, `WorkoutRepository.kt`, `DeviceManager.java`, `PhoneCarriageClassifier.kt`, `SpeedAndLocationDevice_ATTFused.kt`).
* `Localization Parity: N/A - no UI strings` | Database schema migrations: `0` | On-device hardware check: `N/A` (pure-JVM algorithmic EKF fix with synthetic Pixel 10 loop replay + existing Robolectric device integration tests).
* **Branch Preservation**: `feature/ATT-9156` preserved intact and unmerged for Stage 6 Cold Final Review (`--merge-ticket` deferred strictly post-Stage 6 PASS).
