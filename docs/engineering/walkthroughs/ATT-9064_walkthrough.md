# Stage 5 Walkthrough: ATT-9064 - Fix TIME_TOTAL Clock-Beat Stretching, speedAverage_mps Desync & Stationary Indoor ERTS Drift

**Ticket**: `ATT-9064` (`2 SP`, `Bug`, `severity-major`) | **Parent Epic**: `ATT-166` | **Target Release**: `V5.0.0`  
**Active Sprint**: `2026-11-19_02_Spät` | **Branch**: `feature/ATT-9064` (squashed atop `2d7f1489`) | **Author**: AI Agent 1 (`Pro` / Neon) | **Date**: 2026-11-19  
**Verified Requirements**: `REQ-FIL-032` (`REQ-FIL-032.1`–`.3`, `[Verified]`) | **Verified Tests**: `TST-FIL-032` (`TST-FIL-032.1`–`.3`, `[Verified]`)

---

## 1. Summary of Changes (Strictly 3 Non-Monolith Files `< 1,500` LoC)
1. **Beat-Immune 1-Hz ERTS Timestamp Reconstruction (`REQ-FIL-032.1`, `ErtsPostWorkoutRecomputer.kt` `1,051 / 1,500` LoC)**:
   - Extracted `reconstructTimestamps()` invoked from `readWorkoutSamples()` before barometer/ERTS filtering.
   - Advances active 1-Hz samples via `oneHzTs = prevTs + 1000L` without ratcheting `baseEpochMs` on asynchronous `ClockDevice`/`TrackerService` `{0, 2}` `TIME_TOTAL` beat pairs in any order (`2 -> 0`, `0 -> 2`, or separated), eliminating the `+78 s` (`584 s -> 662 s`) stretch.
   - Re-anchors strictly on genuine `> 5 s` pauses (`parsedTs - oneHzTs > 5_000L` including `timeTotalSec == prevTimeTotalSec` and `null` rows, or `extraGapSec >= 5L`, taking `max` when both fire), preserving `TST-FIL-027.1` `6 s` batch-flush stall immunity and `30 s`/`25 s` double pauses.
2. **Authoritative `speedAverage_mps` & `ExtremaType.AVG(SPEED_mps)` Parity (`REQ-FIL-032.2`, `ErtsPostWorkoutRecomputer.kt`)**:
   - Added `computeAuthoritativeAverageSpeedMps()` persisting one unified finite non-negative speed identically to `WorkoutSummaries.SPEED_AVERAGE_mps` and `ExtremumValues(SPEED_mps, AVG)`.
   - Precedence: `totalDistance / TIME_ACTIVE_s` when persisted `TIME_ACTIVE_s > 0` (matching `TrackerService.getAverageSpeed()` and `LapsDatabaseManager.syncLapDistancesAndSpeeds()`) -> matched `activeDist / activeDt` over `dt in (0.0, 5.0]` (preventing pause-gap inflation when `TIME_ACTIVE_s` is `NULL`/`<= 0`) -> finite mean fallback (`0.0` if empty/non-finite).
3. **Stationary Indoor `[E, N]` Covariance Freeze & Backward RTS Pinning (`REQ-FIL-032.3`)**:
   - `UnicycleOdometryEkf.kt` (`810 / 1,500` LoC): added `@JvmOverloads isStationary: Boolean = false` to `predict()` (live `SpeedAndLocationDevice_ATTFused` untouched); when `true`, freezes `x[0..1]`, zeroes `F[0..1, 2..4]`, and zeroes `Q_POS` (preventing `~2.0 m^2/s` `F * P * F^T` blow-up from `ZUPT_SPEED_VARIANCE` while preserving SPD invertibility of `P_{k+1|k}`).
   - `ExtendedRtsOdometrySmoother.kt` (`742 / 1,500` LoC): passes `isStationary` to `ekf.predict()` and pins `smoothedStates[k][0..1] = smoothedStates[k + 1][0..1]` for `currStationary && nextStationary` in `smoothSnapshots()`, reducing `118 s` indoor stop Haversine drift from `35.66 m` to `0.00 m`.
4. **Protected Monoliths (`0` LoC Edits)**: `TrackerService.java`, `SpeedAndLocationDevice_ATTFused.kt`, `WorkoutSummariesDatabaseManager.java`, `WorkoutSamplesDatabaseManager.java`, `WorkoutRepository.kt`, `DeviceManager.java`, `LapsDatabaseManager.java`, and `values*/strings_*.xml` (`Localization Parity: N/A - no UI strings`) remain 100% untouched.

---

## 2. Verification Evidence (Gate 4 Reuse per `Rule 7.5` — `0` `app/` Diff Since `75f3bff9`)
- **Gradle Incremental `--rerun` Suite Results (`ATT-9167` Gate 4 Verified — `84/84 PASS`, `0` Failures, `0` Errors, `0` Skipped, `0` Warnings in Touched Files)**:

| Test Suite | Result | Verified Scope |
| :--- | :---: | :--- |
| `ErtsPostWorkoutRecomputerTest` | `17/17 PASS` | `TST-FIL-032.1`/`.2`/`.3` (`584`-row `78`-pair `0/2` beats, pauses/flush-stall, `speedAverage_mps`/`Extrema`/`Laps` parity, `NULL` fallback, `Zum Bäcker #58` E2E) |
| `ExtendedRtsOdometrySmootherTest` | `12/12 PASS` | `TST-FIL-032.3` (`118 s` indoor stop `0.00 m` Haversine drift, direct `smoothSnapshots` pinning, `TST-FIL-027.3` & canopy Doppler-dropout) |
| `UnicycleOdometryEkfTest` | `17/17 PASS` | `TST-FIL-032.3` (`predict(isStationary = true)` `0` `P_pos` growth over `118 s`, SPD invertibility, default `isStationary = false` live parity) |
| `TrackerServiceErtsFinalizationTest` | `2/2 PASS` | Post-workout ERTS hook integration |
| `LapsDatabaseManagerTest` | `10/10 PASS` | `Laps.db` distance & average-speed sync parity |
| `ArchitectureConformanceContractTest` | `11/11 PASS` | Monolith LoC ceilings (`0` protected edits; all 3 touched files `< 1,500` LoC) & layer isolation |
| `SystemLiveTrackingRequirementsContractTest` | `4/4 PASS` | `SYSREQ-TRK-003` / `SYSREQ-TRK-004` <-> `REQ-FIL-032` bidirectional traceability |
| `DeadCodeEliminationContractTest` | `5/5 PASS` | Zero dead code / unused helpers |
| `SourceTextReadingAuditContractTest` | `6/6 PASS` | `0` `src/main` reads in tests (`REQ-PRO-049.1`) |

- **Safety Linters & Governance**:
  - `lint_cursor_safety.py`, `lint_lifecycle_safety.py`, `lint_safe_enum.py`, `check_secrets_hygiene.py`: **all `exit 0`**.
  - `verify_requirement_governance.py --repo-root . --sync-indexes` & `--base-ref refs/heads/sprint/2026-11-19_02_Spät --check-verified-evidence`: **`exit 0`** (`100%` `[Verified]` parity).
  - `git diff 75f3bff9..HEAD -- app/`: **empty (`0` bytes changed since Gate 4 `PASS`)**.

---

## 3. Handoff Status
- `feature/ATT-9064` preserved intact as `1` clean squashed commit atop `sprint/2026-11-19_02_Spät` (`2d7f1489`), ready for Gate 5 review and Stage 6 Cold Final Review (`Final Review (Agent)`).
