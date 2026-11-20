# Stage 3: Implementation Plan - ATT-9160: WorkoutRr.db Beat-Level Artifact Flag Persistence & Live vs Post-Workout HRV Reconciliation

**Ticket**: [ATT-9160](https://rainerblind.atlassian.net/browse/ATT-9160) | **Parent Epic**: [ATT-3539](https://rainerblind.atlassian.net/browse/ATT-3539)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-11-19_03_Nacht` | **Branch**: `feature/ATT-9160`  
**Requirement Mapping**: `REQ-HRV-048` (`REQ-HRV-048.1`..`REQ-HRV-048.4`, refines `REQ-PER-034.3`, `REQ-TRK-060.2`/`.4`/`.5`) | **Test Mapping**: `TST-HRV-048` (`TST-HRV-048.1`..`TST-HRV-048.4`)  
**Author**: AI Agent 1 (`--as agent1`) | **Date**: 2026-11-20

---

## 1. Problem Description & Background
Post-workout `HrvPostWorkoutRecomputer` executes Lipponen & Tarvainen Mode B filtering (`RrArtifactFilter.filterBidirectionalBeats`) in memory without persisting detected `artifact_flag` or `gap_beats` back to `WorkoutRr.db`, leaving every persisted row at `(0, 0)`. Simultaneously, live `HRVMetricsDevice` prunes `mBeatQueue` at `120 s` *before* `filterLiveCausal` (starving leading beats of their 91-beat trailing quartile context and inflating live edge artifacts) while `HrvPostWorkoutRecomputer` hardcodes `5.0%` instead of honoring `TuningPreferencesDataStore.hrvArtifactGateThresholdPercent`. Stage 4 resolves all three defects cleanly without altering the intentional 60s causal-vs-centered lag elimination (`REQ-PER-034.3`) or breaking NeuroKit2 `0.2.10` golden parity.

## 2. Traceability & Requirements Mapping
- `REQ-HRV-048.1` <-> `TST-HRV-048.1`: `RrArtifactFilter.kt` 1:1 per-input-beat `inputArtifactFlags`/`inputGapBeats` (strict `ARTIFACT_GAP` preservation, fresh `k - 1` on `MISSED`/split `LONG`, merged `SHORT` pair tagging, explicit `0` reset on normal/non-split) + $O(N)$ `mRrs` precomputation in `filterLiveCausal`.
- `REQ-HRV-048.2` <-> `TST-HRV-048.2`: `WorkoutRrDatabaseManager.kt` + `WorkoutRrRepository.kt` single-transaction pre-compiled `updateBeatQualityFlags` + `HrvPostWorkoutRecomputer.kt` delta-only idempotent write-back (`0` writes on re-run, stale flags cleared after backfill).
- `REQ-HRV-048.3` <-> `TST-HRV-048.3`: `HRVMetricsDevice.kt` `WINDOW_FILTER_CONTEXT_MS = 180_000L`, pre-filter active-120s drain guard, empty `dfaWindowBeats` lookahead-tail handling, and 120s `dfaWindowBeats` slice for `HRV_ARTIFACT_RATE`, `DFA a1`, and `HrvSignalQualityAnalyzer`.
- `REQ-HRV-048.4` <-> `TST-HRV-048.2`, `TST-HRV-048.3`, `TST-HRV-048.4`: `HrvPostWorkoutRecomputer.kt` configurable clamped `[3.0, 5.0]` artifact gate threshold (`@JvmOverloads` blocking/SDNN + Kotlin default on `suspend`) + full regression & contract verification.

## 3. System Invariants & Preserved Behavior
1. **Gate 3 Code Lock (`Rule 4.1`)**: Zero edits to `app/` until `python3 tools/jira_util.py check-gate <IMPL_PLAN_SUBTASK_KEY>` returns `GATE_PASSED`.
2. **Schema, Monolith & Golden Invariants**: Zero edits to `TrackerService.java` (`3,538 / 3,559` LoC), `DfaCalculator.kt`, `WorkoutRrDbHelper.kt` (`DB_VERSION = 2` unchanged; leverages existing `idx_workout_rr_workout_beat`), `values*/strings_*.xml` (`Localization Parity: N/A - no UI strings`), `docs/architecture*.md`, or `ANT-Android-SDKs`.
3. **Behavioral & Concurrency Invariants**: Preserve NeuroKit2 `0.2.10` golden tolerances (`HrvGoldenOracleTest`: `|ΔrMSSD| <= 0.5 ms`, `|Δα1| <= 0.02`/`0.03`), `HrvPostWorkoutRecomputer.MIN_RMSSD_PAIRS = 3`, `HRVMetricsDevice` Rule 51/52 + `+150 s` drain-to-null clearing, `@Synchronized` thread safety on `WorkoutRrDatabaseManager` & `HRVMetricsDevice`, `Dispatchers.IO` off-main DB I/O, and `REQ-TRK-084.4` conditional `AccumulatedSensors`.

## 4. Proposed Architectural Changes (SWE.2 Boundaries & Threading)
- **Component 1 (`RrArtifactFilter.kt`, `382` LoC — Pure DSP)**:
  - Extend `FilterResult` with optional `val inputArtifactFlags: List<Int> = emptyList()` and `val inputGapBeats: List<Int> = emptyList()`.
  - In `processBeats(...)`: allocate `IntArray(limit)` for flags/gaps:
    - `ARTIFACT_GAP (5)` / out-of-range: `flags[i] = ARTIFACT_GAP`, `gaps[i] = max(event.gapBeats, gapCount - 1)`.
    - Merged `ARTIFACT_SHORT (2)` pair (`i`, `i + 1`): `flags[i] = ARTIFACT_SHORT`, `flags[i + 1] = ARTIFACT_SHORT`, `gaps[i] = 0`, `gaps[i + 1] = 0`.
    - `ARTIFACT_MISSED (4)` or `ARTIFACT_LONG (3)` with `rrMs >= 1.5 * med`: `flags[i] = classification`, `gaps[i] = k - 1` (freshly computed).
    - All other (`NORMAL`, `ECTOPIC`, unmerged `SHORT`, non-split `LONG`): `flags[i] = classification`, `gaps[i] = 0`.
  - In `filterLiveCausal`: precompute `val allMRrs = computeMedianDeviationsCentered(rawRrs)` once before the loop ($O(N)$ parity with `filterBidirectionalBeats`).
- **Component 2 (`WorkoutRrDatabaseManager.kt`, `169` LoC & `WorkoutRrRepository.kt`, `100` LoC — SQLite Persistence)**:
  - Add `data class BeatQualityFlagUpdate(val beatIndex: Int, val artifactFlag: Int, val gapBeats: Int)`.
  - Add `@Synchronized fun updateBeatQualityFlags(workoutId: Long, updates: List<BeatQualityFlagUpdate>): Int` in `WorkoutRrDatabaseManager` executing a single-transaction pre-compiled `UPDATE workout_rr_intervals SET artifact_flag = ?, gap_beats = ? WHERE workout_id = ? AND beat_index = ?` (zero schema changes; `DB_VERSION = 2` unchanged).
  - Expose `updateBeatQualityFlagsBlocking` and `suspend fun updateBeatQualityFlags` (`Dispatchers.IO`) on `WorkoutRrRepository`.
- **Component 3 (`HrvPostWorkoutRecomputer.kt`, `419` LoC — Post-Workout Engine)**:
  - Add `@JvmStatic @JvmOverloads` optional `artifactGateThresholdPercent: Double? = null` to `recomputeWorkoutBlocking`, default `artifactGateThresholdPercent: Double? = null` to `suspend fun recomputeWorkout`, and `@JvmStatic @JvmOverloads` default `artifactGateThresholdPercent: Double = ARTIFACT_GATE_THRESHOLD_PERCENT` to `computeSessionSdnn`.
  - Resolve `effectiveGateThreshold` via `artifactGateThresholdPercent ?: TuningPreferencesDataStore` (`hasInitializedInstance()` guard, fallback `5.0`), clamped to `3.0..5.0` (only clamping DataStore values, preserving explicit test overrides if desired or clamping `[3.0, 5.0]` per spec).
  - After `filterBidirectionalBeats(orderedBeats)`: build delta list `updates` strictly for indices where `newFlag != orderedBeats[i].artifactFlag || newGap != orderedBeats[i].gapBeats`, keyed by `orderedBeats[i].rawCounter` (`beatIndex`), and call `rrRepository.updateBeatQualityFlags(workoutId, updates)` only when `updates.isNotEmpty()`.
- **Component 4 (`HRVMetricsDevice.kt`, `259` LoC — Live Streaming Engine)**:
  - Add `const val WINDOW_FILTER_CONTEXT_MS = WINDOW_DFA_MS + 60_000L` (`180_000L`) and prune `mBeatQueue` at `currentTimestampMs - WINDOW_FILTER_CONTEXT_MS`.
  - In `calculateMetrics()`: immediately check `val dfaCutoff = now - WINDOW_DFA_MS`; if `mBeatQueue.none { it.beatTimestampMs >= dfaCutoff }`, emit `null` to all 3 sensors, reset `signalQualityResult`, and return.
  - Run `filterLiveCausal(allBeats)`, then slice `dfaWindowBeats = filteredBeats.filter { it.beatTimestampMs >= dfaCutoff }`. If `dfaWindowBeats.isEmpty()`, set `artifactRate = 0.0`, `alpha1Value = null`; else compute `artifactRate` and `DfaCalculator.computeDfaAlpha1` over `dfaWindowBeats` using `effectiveGateThreshold`. Pass `dfaWindowBeats` (120s slice, NOT 180s `filteredBeats`) to `HrvSignalQualityAnalyzer.analyzeBeats(...)`.
- **UI Consistency (`docs/design_guidelines.md`)**: `N/A — pure DSP & SQLite backend fix; zero UI screens, composables, or string resources modified`.

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)
1. **Step 0 (Gate 3 Code Lock Verification)**: Run `python3 tools/jira_util.py --as agent1 check-gate <IMPL_PLAN_SUBTASK_KEY>` and confirm `GATE_PASSED`.
2. **Step 1 (`RrArtifactFilter.kt` — `REQ-HRV-048.1`)**: Expose `inputArtifactFlags`/`inputGapBeats` on `FilterResult` with exact `ARTIFACT_GAP`/`MISSED`/`SHORT`/`NORMAL` rules and $O(N)$ `allMRrs` in `filterLiveCausal`.
3. **Step 2 (`WorkoutRrDatabaseManager.kt` & `WorkoutRrRepository.kt` — `REQ-HRV-048.2`)**: Implement `BeatQualityFlagUpdate` and single-transaction pre-compiled `updateBeatQualityFlags` (`DB_VERSION = 2` unchanged).
4. **Step 3 (`HrvPostWorkoutRecomputer.kt` — `REQ-HRV-048.2`, `REQ-HRV-048.4`)**: Wire delta-only `updateBeatQualityFlags` write-back (`0` writes on idempotent re-run) and configurable clamped `[3.0, 5.0]` artifact gate threshold across `recomputeWorkout*` and `computeSessionSdnn`.
5. **Step 4 (`HRVMetricsDevice.kt` — `REQ-HRV-048.3`)**: Wire `WINDOW_FILTER_CONTEXT_MS = 180_000L`, pre-filter `120 s` drain-to-null guard, empty `dfaWindowBeats` lookahead-tail guard, and `dfaWindowBeats` slicing for `HRV_ARTIFACT_RATE`, `DFA a1`, and `HrvSignalQualityAnalyzer`.
6. **Step 5 (Unit/Robolectric Tests & Locked Incremental Verification — `TST-HRV-048.1`..`.4`)**:
   - Add unit/Robolectric tests in `RrArtifactFilterTest.kt`, `WorkoutRrDatabaseManagerTest.kt`, `WorkoutRrRepositoryTest.kt`, `HrvPostWorkoutRecomputerTest.kt`, and `HRVMetricsDeviceTest.kt` — EVERY new test MUST include explicit hyphenated `// REQ-HRV-048 (REQ-HRV-048.x) / TST-HRV-048 (TST-HRV-048.x)` comments for clean-checkout `REQ-PRO-099.3` parity!
   - Run under shared lock (strictly `--rerun`, NEVER `--rerun-tasks`, NEVER `-PstampGitHash=true`):
     `python3 tools/parallel_sprint_lock.py --timeout 1800 -- ./gradlew :app:testDebugUnitTest --rerun --tests "com.atrainingtracker.banalservice.devices.hrv.RrArtifactFilterTest" --tests "com.atrainingtracker.banalservice.devices.hrv.HrvGoldenOracleTest" --tests "com.atrainingtracker.banalservice.devices.hrv.HRVMetricsDeviceTest" --tests "com.atrainingtracker.banalservice.devices.hrv.DfaCalculatorTest" --tests "com.atrainingtracker.trainingtracker.database.hrv.WorkoutRrDatabaseManagerTest" --tests "com.atrainingtracker.trainingtracker.database.hrv.WorkoutRrRepositoryTest" --tests "com.atrainingtracker.trainingtracker.database.hrv.HrvPostWorkoutRecomputerTest" --tests "com.atrainingtracker.trainingtracker.build.ArchitectureConformanceContractTest" --tests "com.atrainingtracker.trainingtracker.build.DeadCodeEliminationContractTest"`
   - Promote `REQ-HRV-048` & `TST-HRV-048` to `[Implemented]` with `--sync-indexes` and verify `verify_requirement_governance.py` + clean-checkout `--check-verified-evidence --test-results-dir` exit `0`.

## 6. Verification & Rollback Plan
- **Verification**: 100% pass rate on all 9 targeted HRV/DB/contract suites (`TST-HRV-048.1`..`.4`), NeuroKit2 golden oracle parity, clean-checkout `extract_test_code_tags()` verification, and `verify_requirement_governance.py` exit `0`.
- **Rollback**: Isolated `feature/ATT-9160` branch with zero DB schema changes allows clean single-branch revert without impacting `sprint/2026-11-19_03_Nacht` or `develop`.
