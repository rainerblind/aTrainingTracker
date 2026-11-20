# Stage 2: Requirement & Test Specification - ATT-9160

**Ticket**: [ATT-9160](https://rainerblind.atlassian.net/browse/ATT-9160) | **Parent Epic**: [ATT-3539](https://rainerblind.atlassian.net/browse/ATT-3539)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-11-19_03_Nacht` | **Branch**: `feature/ATT-9160`  
**Requirement Mapping**: `REQ-HRV-048` (refines `REQ-PER-034.3`, `REQ-TRK-060.2`/`.4`/`.5`) | **Test Spec ID**: `TST-HRV-048` | **Date**: 2026-11-20

---

## 1. Requirement Specification (`REQ-HRV-048`)

### 1.1 Attributes & System Traceability
- **System Requirement(s)**: Derived (SW-only) — Post-workout persistence of per-beat Lipponen & Tarvainen artifact/gap flags in `WorkoutRr.db`, full trailing quartile filter context in `HRVMetricsDevice`, and unified artifact gate threshold plumbing in `HrvPostWorkoutRecomputer` (zero `SYS.2`/`SYS.3` impact).
- **Priority**: Medium | **Type**: Functional | **Verification Method**: Unit, Integration
- **Localization Parity**: `N/A - no UI strings`.

### 1.2 Functional & Architectural Requirements
1. **`REQ-HRV-048.1` (1:1 Per-Input-Beat Artifact/Gap Metadata in `RrArtifactFilter`)**: `FilterResult` SHALL expose optional backward-compatible `inputArtifactFlags: List<Int> = emptyList()` and `inputGapBeats: List<Int> = emptyList()` aligned 1:1 with the input `beats` list before split/merge expansion:
   - For `ARTIFACT_GAP (5)` (hardware RF dropout or out-of-range RR `< 250 ms` / `> 2500 ms`), `inputGapBeats[i]` SHALL preserve `max(event.gapBeats, gapCount - 1)` so hardware RF gap counts are never lost.
   - For `ARTIFACT_MISSED (4)` or split `ARTIFACT_LONG (3)` (`rrMs >= 1.5 * med`), `inputGapBeats[i]` SHALL compute `k - 1` freshly (`k = max(2, round(rrMs / med))`).
   - For merged `ARTIFACT_SHORT (2)` pairs, BOTH constituent input indices (`i` and `i + 1`) SHALL record `ARTIFACT_SHORT (2)` with `inputGapBeats = 0`.
   - For `ARTIFACT_NORMAL (0)`, `ARTIFACT_ECTOPIC (1)`, unmerged `ARTIFACT_SHORT (2)`, and non-split `ARTIFACT_LONG (3)`, `inputGapBeats[i]` SHALL explicitly be `0` so re-recomputation after Polar H10 offline backfill clears stale flags/gaps.
   - `filterLiveCausal` SHALL precompute `computeMedianDeviationsCentered(rawRrs)` once in $O(N)$ parity with `filterBidirectionalBeats`.
2. **`REQ-HRV-048.2` (Idempotent Delta-Batched Write-Back into `WorkoutRr.db`)**: `WorkoutRrDatabaseManager` and `WorkoutRrRepository` SHALL expose transactional `updateBeatQualityFlags(workoutId: Long, updates: List<BeatQualityFlagUpdate>): Int` using a single pre-compiled `UPDATE workout_rr_intervals SET artifact_flag = ?, gap_beats = ? WHERE workout_id = ? AND beat_index = ?` leveraging existing `idx_workout_rr_workout_beat` (`DB_VERSION = 2` unchanged). `HrvPostWorkoutRecomputer.recomputeWorkout` SHALL map each `orderedBeats[i].rawCounter` (`beatIndex`) to `(inputArtifactFlags[i], inputGapBeats[i])` and write ONLY changed beats (`newFlag != orderedBeats[i].artifactFlag || newGap != orderedBeats[i].gapBeats`), yielding `0` DB writes on idempotent re-runs while clearing stale flags/gaps after backfill.
3. **`REQ-HRV-048.3` (180s Causal Filter Context & Active 120s Window Scope in `HRVMetricsDevice`)**: `HRVMetricsDevice` SHALL retain `WINDOW_FILTER_CONTEXT_MS = WINDOW_DFA_MS + 60_000L` (`180_000L`) in `mBeatQueue` so every beat in `[now - 120s, now]` receives full 91-beat trailing Lipponen & Tarvainen quartile context. Before filtering, `calculateMetrics()` MUST check `mBeatQueue.none { it.beatTimestampMs >= now - WINDOW_DFA_MS }` and emit `null` to all 3 sensors + reset `signalQualityResult` when the active 120s window has drained (preserving Rule 51/52 and `+150s` drain clearing). Post-filtering, `HRVMetricsDevice` SHALL slice `dfaWindowBeats = filteredBeats.filter { it.beatTimestampMs >= now - WINDOW_DFA_MS }`, handle empty `dfaWindowBeats` cleanly (`artifactRate = 0.0`, `dfaAlpha1 = null`), compute `HRV_ARTIFACT_RATE` & `HRV_DFA_A1` strictly over `dfaWindowBeats`, and pass `dfaWindowBeats` (NOT the full 180s buffer) to `HrvSignalQualityAnalyzer.analyzeBeats(...)`.
4. **`REQ-HRV-048.4` (Unified Configurable Artifact Gate Threshold & Invariants)**: `HrvPostWorkoutRecomputer` SHALL add optional `@JvmOverloads` `artifactGateThresholdPercent: Double? = null` on `recomputeWorkout`/`recomputeWorkoutBlocking` and default `ARTIFACT_GATE_THRESHOLD_PERCENT` (`@JvmOverloads`) on `computeSessionSdnn`, resolving `TuningPreferencesDataStore.hrvArtifactGateThresholdPercent` clamped to `[3.0, 5.0]` with `hasInitializedInstance()` fallback to `5.0`. `DfaCalculator.kt` (NeuroKit2 `0.2.10` golden parity), `TrackerService.java`, `WorkoutRrDbHelper.kt` (`DB_VERSION = 2`), `HrvPostWorkoutRecomputer.MIN_RMSSD_PAIRS = 3`, and `values*/strings_*.xml` MUST remain untouched.

### 1.3 Acceptance Criteria (Given-When-Then)
- *Given* a workout in `WorkoutRr.db` with `~385 ms` beats containing `275.39 ms` (`SHORT`/ectopic), `779.30 ms` (`MISSED`, `k=2`), and an unflagged `5196 ms` gap (`ARTIFACT_GAP`) all initially stored as `(0, 0)`, *When* `HrvPostWorkoutRecomputer.recomputeWorkout` executes, *Then* `workout_rr_intervals` persists non-zero `artifact_flag` & `gap_beats` (`gap_beats = 1` on `779.30 ms`, `>= 5` on `5196 ms`), a second idempotent run performs `0` writes, and stale flags reset to `(0, 0)` when RR values are backfilled clean.
- *Given* a continuous `> 180 s` RR stream with steady-state running beats (`~385 ms`) and minor isolated outliers, *When* `HRVMetricsDevice` evaluates `[T - 120s, T]` vs `HrvPostWorkoutRecomputer` at phase-aligned center `T - 60s` (`[T - 120s, T]`), *Then* both streams use full 91-beat quartile context and identical clamped artifact gate thresholds `[3.0, 5.0]`, reconciling `HRV_ARTIFACT_RATE`, `HRV_RMSSD`, and `HRV_DFA_A1` without leading-edge quartile starvation (while intentional causal-vs-centered 60s phase-lag elimination per `REQ-PER-034.3` remains preserved).
- *Given* `HRVMetricsDevice` with beats at `t = 0..24 s` and clock advanced by `+150 s` (`> 120 s`, `< 180 s` context), *When* `calculateMetrics()` runs, *Then* all 3 sensors (`HRV_RMSSD`, `HRV_DFA_A1`, `HRV_ARTIFACT_RATE`) clear to `null` and `signalQualityResult` resets to `INSUFFICIENT_DATA`.

### Requirement Archaeology & Chesterton's Fence Audit
- **Original Requirement ID & Target**: `REQ-PER-034.1`/`.3`, `REQ-TRK-060.2`/`.4`/`.5`, `REQ-TRK-085.2`/`.3` targeting `RrArtifactFilter.kt`, `HRVMetricsDevice.kt`, `HrvPostWorkoutRecomputer.kt`, `WorkoutRrDatabaseManager.kt`, `WorkoutRrRepository.kt`.
- **Historical Origin & Commit Trace**: `ATT-3543` (`e87080a3`, `5808179d`), `ATT-3541` (`4bad7272`), `ATT-6714` (`8b2196d9`), `ATT-8706` (`7dcca5e7`, `6016d5fc`).
- **Root Reason for Existing Formulation**: `ATT-3543` ran bidirectional filtering in memory without persisting classifications back to `WorkoutRr.db`; `ATT-6714`/`ATT-8706` pruned `HRVMetricsDevice` at `120 s` to enforce drain-to-null clearing and out-of-order eviction; `HrvPostWorkoutRecomputer.MIN_RMSSD_PAIRS = 3` is asserted by `HrvPostWorkoutRecomputerTest.kt:87-104`.
- **Preservation of Core Invariants**: NeuroKit2 `0.2.10` golden tolerances (`HrvGoldenOracleTest`), `MIN_RMSSD_PAIRS = 3`, Rule 51/52, `120 s` drain-to-null, `REQ-PER-034.3` centered 120s/60s lag elimination, `REQ-TRK-084.4` conditional `AccumulatedSensors`, `DB_VERSION = 2`, and `0` `TrackerService.java` edits remain strictly preserved.

---

## 2. Test Specification (`TST-HRV-048`)
- **`TST-HRV-048.1` (`RrArtifactFilterTest.kt` & `HrvGoldenOracleTest.kt` — 1:1 Input Metadata & Golden Parity)**: Verify `inputArtifactFlags`/`inputGapBeats` size equals input `beats.size` across clean, ectopic (`1`, `gap=0`), merged short pair (both indices `2`, `gap=0`), missed `779.3 ms` amidst `385 ms` (`4`, `gap=1`), unflagged `5196 ms` gap (`5`, `gap>=5`), hardware `ARTIFACT_GAP` (`max(event.gapBeats, gapCount - 1)` preserved), and stale-flag reset (`-> 0`); verify `HrvGoldenOracleTest` passes within NeuroKit2 `0.2.10` tolerances (`|ΔrMSSD| <= 0.5 ms`, `|Δα1| <= 0.02`/`0.03`).
- **`TST-HRV-048.2` (`WorkoutRrDatabaseManagerTest.kt`, `WorkoutRrRepositoryTest.kt` & `HrvPostWorkoutRecomputerTest.kt` — Delta Persistence, Idempotency & Gate Threshold)**: Verify `updateBeatQualityFlags` persists `(artifact_flag, gap_beats)` by `(workout_id, beat_index)` transactionally without altering `rr_ms`/`beat_timestamp_ms`; verify `HrvPostWorkoutRecomputer.recomputeWorkout` persists flags for `275.39 ms` / `779.30 ms` / `5196 ms` outliers, performs `0` writes on second idempotent run, clears stale flags to `(0, 0)` after clean backfill, and honors configurable `artifactGateThresholdPercent` clamped to `[3.0, 5.0]` for both DFA $\alpha_1$ and `computeSessionSdnn`.
- **`TST-HRV-048.3` (`HRVMetricsDeviceTest.kt` & `HrvPostWorkoutRecomputerTest.kt` — 180s Filter Context, Drain-to-Null, Signal Quality Scope & Phase-Aligned Reconciliation)**: Verify 180s context retains trailing quartile accuracy across rolling 120s ticks without inflating leading-edge artifacts; verify `+150 s` drain (inside `180 s` context, outside `120 s` active window) clears all 3 sensors to `null`; verify empty `dfaWindowBeats` (all active beats in 5-beat lookahead tail) emits `artifactRate = 0.0`, `dfaAlpha1 = null`; verify `signalQualityResult.beatCount` reflects the 120s `dfaWindowBeats` slice (not 180s); verify phase-aligned live `[T - 120s, T]` vs recomputed `T - 60s` (`[T - 120s, T]`) reconcile tightly on steady-state streams.
- **`TST-HRV-048.4` (Tier 1 Targeted Regression & Architecture Contracts)**: Run locked incremental `./gradlew :app:testDebugUnitTest --rerun --tests "*RrArtifactFilterTest" --tests "*HrvGoldenOracleTest" --tests "*HRVMetricsDeviceTest" --tests "*WorkoutRrDatabaseManagerTest" --tests "*WorkoutRrRepositoryTest" --tests "*HrvPostWorkoutRecomputerTest" --tests "*DfaCalculatorTest" --tests "*ArchitectureConformanceContractTest" --tests "*DeadCodeEliminationContractTest"`.

---

## 3. Traceability Matrix

| Test Case | Scope | Target Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-HRV-048.1` | Unit (DSP/Oracle) | `RrArtifactFilter`, `HrvGoldenOracleTest` | `REQ-HRV-048.1` | Specified |
| `TST-HRV-048.2` | Robolectric DB | `WorkoutRrDatabaseManager`, `WorkoutRrRepository`, `HrvPostWorkoutRecomputer` | `REQ-HRV-048.2`, `REQ-HRV-048.4` | Specified |
| `TST-HRV-048.3` | Unit / Reconciliation | `HRVMetricsDevice`, `HrvPostWorkoutRecomputer` | `REQ-HRV-048.3`, `REQ-HRV-048.4` | Specified |
| `TST-HRV-048.4` | Contracts | LoC, Dead-Code & Full HRV Unit Suite | `REQ-HRV-048.1`..`.4` | Specified |
