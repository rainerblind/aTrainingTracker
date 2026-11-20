# Stage 1 Analysis: ATT-9160 - Persist Beat-Level Artifact Flags in WorkoutRr.db & Reconcile Live vs Post-Workout HRV Divergence

**Ticket**: [ATT-9160](https://rainerblind.atlassian.net/browse/ATT-9160)  
**Parent Epic**: [ATT-3539](https://rainerblind.atlassian.net/browse/ATT-3539) (*Heart Rate Variability (HRV)*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-11-19_03_Nacht` | **Branch**: `feature/ATT-9160`  
**Author**: AI Agent 1 (`--as agent1`) | **Date**: 2026-11-20

---

## 1. Problem Statement & Motivation
Field telemetry from Pixel 10 (`workout_id = 2414`, Polar H10, `6,863` RR beats) exposed two defects:
1. Every persisted row in `WorkoutRr.db` (`workout_rr_intervals`) had `artifact_flag = 0` and `gap_beats = 0` despite clear outliers (`275.39 ms`, `779.30 ms` amidst `~385 ms` running beats) and live `HRV_ARTIFACT_RATE` peaking at `9.85%`.
2. Canonical post-workout `HRV_DFA_A1` diverged from live `HRV_DFA_A1 (HRV metrics)` on `0 / 2,617` exact row matches with a `0.811` delta at `t = 120 s` (`0.697` vs `1.508`) and max artifact rate `3.22%` vs `9.85%`.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
- **Independent Peer Review & Verification** (audited live `sprint/2026-11-19_03_Nacht` @ `56eb3702`):
  1. **Defect 1 — Missing Post-Workout Write-Back of Beat Classifications (`HrvPostWorkoutRecomputer.kt:87-106`, `RrArtifactFilter.kt:94-284`, `WorkoutRrDatabaseManager.kt`)**:
     - During live ingestion (`BTLEHeartRateDevice.java:230`, `PolarOnlineStreamingBridge.kt:900`), `RrBeatNormalization.reconstructBundle` ONLY flags `ARTIFACT_GAP (5)` on wall-clock drift (`> 2000 ms`) or out-of-range RR (`< 250 ms` or `> 2500 ms`). Intra-physiological outliers (`275.39 ms`, `779.30 ms`) arrive at `TrackerService.mRrBeatListener.onRrBeat` (`L292-307`) as `(0, 0)` and are flushed raw.
     - Post-workout, `HrvPostWorkoutRecomputer.recomputeWorkout` (`L103`) runs `RrArtifactFilter.filterBidirectionalBeats(orderedBeats)` — detecting every `ECTOPIC (1)`, `SHORT (2)`, `LONG (3)`, `MISSED (4)`, and `GAP (5)` beat plus split counts (`k - 1` missed beats) — **but discards per-input-beat classifications and never updates `WorkoutRr.db`**.
     - *Side-Effect Audit (`Rule 68`)*: `WorkoutRepository.isWorkoutHrvSmoothed` (`L4910-4922`) reads `interval.artifactFlag` directly from `WorkoutRr.db`, seeing `0%` artifacts; `FitFileWriter` (`L358`) and analytics queries likewise see zero quality flags.
  2. **Defect 2 — Live `HRVMetricsDevice` Starved Trailing Quartile Context (`HRVMetricsDevice.kt:136-180`, `RrArtifactFilter.kt:143-189`)**:
     - `pruneOldBeats(now)` evicts beats `< now - 120_000L` (`WINDOW_DFA_MS`) *before* `filterLiveCausal(allBeats)`. Consequently, the first `~86` beats (`qdStart = max(0, i - 86) = 0`) of **every** 120s rolling window compute Lipponen & Tarvainen `th1`/`th2` over a truncated `6..90`-beat window instead of a full `91`-beat window — creating edge-effect false artifacts that inflate live `HRV_ARTIFACT_RATE` (e.g. `9.85%` vs centered `3.22%`).
     - Retaining `WINDOW_FILTER_CONTEXT_MS = WINDOW_DFA_MS + 60_000L` (`180 s`) in `mBeatQueue` for causal filtering, verifying active `[now - 120s, now]` beat presence first (preserving queue-drain `null` clearing), and slicing `dfaWindowBeats = filteredBeats.filter { it.beatTimestampMs >= now - WINDOW_DFA_MS }` gives every beat in the 120s window full trailing quartile context and computes `artifactRate` strictly over `[now - 120s, now]`. Precomputing `mRrs` once in `filterLiveCausal` also matches `filterBidirectionalBeats` (`L102`) in $O(N)$.
  3. **Defect 3 — Artifact Gate Threshold Mismatch (`HrvPostWorkoutRecomputer.kt:196,347` vs `HRVMetricsDevice.kt:185`)**:
     - `HRVMetricsDevice` honors user-configured `TuningPreferencesDataStore.hrvArtifactGateThresholdPercent` (`3.0–5.0%`, default `5.0%`), whereas `HrvPostWorkoutRecomputer` hardcodes `5.0%` for both DFA $\alpha_1$ gating and session SDNN.
  4. **Intentional Causal vs Centered Phase Offset (Explaining `t = 120 s` `0.697` vs `1.508` & `0 / 2617` Exact Matches)**:
     - Live `HRV_DFA_A1 (HRV metrics)` evaluates the causal trailing window `[t - 120s, t]` (center `t - 60s`), whereas canonical `HRV_DFA_A1` (`REQ-PER-034.3`) evaluates the centered window `[t - 60s, t + 60s]` specifically to eliminate the ~60s causal lag. At `t = 120 s`, live `[0..120s]` still covers the standing-start/warmup HR ramp (monotonic RR drop -> $\alpha_1 \approx 1.50$), while canonical `[60..180s]` is steady-state aerobic running ($\alpha_1 \approx 0.70$). Comparing phase-aligned centers (`live(T)` vs `canonical(T - 60s)`) after fixing Defects 2 & 3 reconciles both streams tightly while preserving intentional lag elimination.
  5. **Call-Site Audit & LoC Ceilings**:
     - Callers of `HrvPostWorkoutRecomputer.recomputeWorkout*`: `TrackerService.java:2491`, `PolarH10OfflineController.kt:406,593`, `LegacyImportEngine.kt:887` — all benefit automatically with `0` edits to `TrackerService.java` (`3,538 / 3,559` LoC ceiling untouched!).
     - Target files all well below `1,500` LoC: `RrArtifactFilter.kt` (`382`), `HRVMetricsDevice.kt` (`259`), `HrvPostWorkoutRecomputer.kt` (`419`), `WorkoutRrDatabaseManager.kt` (`169`), `WorkoutRrRepository.kt` (`100`). Zero `WorkoutRrDbHelper.kt` schema changes (`DB_VERSION = 2` unchanged; `idx_workout_rr_workout_beat` unique index already exists!). `Localization Parity: N/A - no UI strings`.

---

## 3. User Scope Grounding (`ATT-1250`)
- **In-Scope Goals (`2 SP` confirmed; no split needed)**:
  1. Expose 1:1 per-input-beat `inputArtifactFlags` & `inputGapBeats` from `RrArtifactFilter` (capturing `ECTOPIC`/`SHORT` merged pairs/`LONG`/`MISSED` splits/`GAP` physiological counts) and persist them transactionally into `WorkoutRr.db` during `HrvPostWorkoutRecomputer.recomputeWorkout` (idempotent on re-runs, `DB_VERSION = 2` unchanged).
  2. Retain `60 s` extra filter context (`WINDOW_FILTER_CONTEXT_MS = 180_000L`) in `HRVMetricsDevice` before `filterLiveCausal`, slice `[now - 120s, now]` for `HRV_ARTIFACT_RATE` & `HRV_DFA_A1`, align `HrvPostWorkoutRecomputer` to `TuningPreferencesDataStore.hrvArtifactGateThresholdPercent`, and verify phase-aligned live-vs-recomputed reconciliation.
- **Out-of-Scope Non-Goals**: Zero edits to `DfaCalculator.kt` (NeuroKit2 `0.2.10` oracle), `TrackerService.java`, `WorkoutRrDbHelper.kt` schema, `(HRV metrics)` device columns in `WorkoutSamples.db`, UI screens, or `values*/strings_*.xml`.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit (`REQ-PRO-022`)
- **Original Requirement ID & Target**: `REQ-PER-034.1`/`.3` (`ATT-3543`, `e87080a3`, `5808179d`), `REQ-TRK-060.2`/`.4`/`.5` (`ATT-3541`, `4bad7272`; `ATT-6714`, `8b2196d9`), `REQ-TRK-085.2`/`.3` (`ATT-8706`, `7dcca5e7`, `6016d5fc`), targeting `RrArtifactFilter.kt`, `HRVMetricsDevice.kt`, `HrvPostWorkoutRecomputer.kt`, `WorkoutRrDatabaseManager.kt`, `WorkoutRrRepository.kt`.
- **Root Reason for Existing Formulation**: `ATT-3543` persisted raw beats during tracking and ran Mode B recomputation in memory without writing flags back; `ATT-6714`/`ATT-8706` pruned `HRVMetricsDevice.mBeatQueue` at `120 s` to enforce stale-metric clearing on disconnect (`testStaleMetricsClearedOnQueueDrain`) and out-of-order eviction (`testPruneOldBeats_evictsOutOfOrderStaleBeatsBehindNewerBeats`), and `HrvPostWorkoutRecomputer.MIN_RMSSD_PAIRS = 3` is asserted by `HrvPostWorkoutRecomputerTest.kt:87-104` (4 valid pairs).
- **Preservation of Core Invariants**: Preserves NeuroKit2 `0.2.10` golden parity (`HrvGoldenOracleTest`: `|ΔrMSSD| <= 0.5 ms`, `|Δα1| <= 0.02`/`<= 0.03`), `HrvPostWorkoutRecomputer.MIN_RMSSD_PAIRS = 3`, `HRVMetricsDevice` Rule 51 (`resetSession`), Rule 52 (zero pre-RR emission), `120 s` queue-drain null clearing, `REQ-PER-034.3` centered 120s/60s phase-lag elimination, `REQ-TRK-084.4` conditional `AccumulatedSensors`, and `WorkoutRrDbHelper.DB_VERSION = 2`.

---

## 5. Change-Request Impact Analysis (`SUP.10` / `REQ-PRO-064`)
- **Affected Requirements**: Net-new candidate **`REQ-HRV-048`** (`REQ-HRV-048.1`..`REQ-HRV-048.4`) / **`TST-HRV-048`** (`TST-HRV-048.1`..`TST-HRV-048.4`) in `docs/requirements/req_aftermath_periods.md` & `docs/tests/tst_aftermath_periods.md` (refining `REQ-PER-034.3`, `REQ-TRK-060.2`/`.4`/`.5`). (`SYS.2`/`SYS.3` Impact Check `REQ-PRO-131.4`: **None — Derived SW-only**.)
- **Affected Architecture Elements**: `RrArtifactFilter.kt` (`382` LoC), `HRVMetricsDevice.kt` (`259` LoC), `HrvPostWorkoutRecomputer.kt` (`419` LoC), `WorkoutRrDatabaseManager.kt` (`169` LoC), `WorkoutRrRepository.kt` (`100` LoC). Zero edits to `TrackerService.java`, `DfaCalculator.kt`, `WorkoutRrDbHelper.kt`, or `ANT-Android-SDKs`.
- **Verification Impact & Test Surface**: `RrArtifactFilterTest.kt`, `HRVMetricsDeviceTest.kt`, `HrvGoldenOracleTest.kt`, `WorkoutRrDatabaseManagerTest.kt`, `WorkoutRrRepositoryTest.kt`, `HrvPostWorkoutRecomputerTest.kt`, `ArchitectureConformanceContractTest.kt`, `DeadCodeEliminationContractTest.kt`.
- **Risk Assessment & Invariants**: **LOW-MEDIUM** — Pure DSP context + transactional DB flag write-back backed by NeuroKit2 golden tests and Robolectric SQLite tests.

---

## 6. Architectural Strategy & High-Level Solution
1. **`RrArtifactFilter.kt`**: Add optional `inputArtifactFlags: List<Int> = emptyList()` and `inputGapBeats: List<Int> = emptyList()` to `FilterResult`, populated 1:1 with input `beats` in `processBeats` (flagging both halves of merged `ARTIFACT_SHORT` pairs and recording `max(event.gapBeats, splitCount - 1)` for `ARTIFACT_MISSED`/`ARTIFACT_LONG >= 1.5*med`/`ARTIFACT_GAP`). Precompute `mRrs` once in `filterLiveCausal`.
2. **`WorkoutRrDatabaseManager.kt` & `WorkoutRrRepository.kt`**: Add `@Synchronized fun updateBeatQualityFlags(workoutId: Long, updates: List<BeatQualityFlagUpdate>): Int` executing a single-transaction pre-compiled `UPDATE workout_rr_intervals SET artifact_flag = ?, gap_beats = ? WHERE workout_id = ? AND beat_index = ?` + suspending/blocking wrappers on `WorkoutRrRepository`.
3. **`HrvPostWorkoutRecomputer.kt`**: Map `orderedBeats[i].rawCounter` (`beatIndex`) to `(filterResult.inputArtifactFlags[i], filterResult.inputGapBeats[i])` and call `rrRepository.updateBeatQualityFlags(workoutId, updates)` (strictly idempotent). Resolve `effectiveGateThreshold` from `TuningPreferencesDataStore` (fallback `5.0%`) for DFA $\alpha_1$ and `computeSessionSdnn`.
4. **`HRVMetricsDevice.kt`**: Retain `WINDOW_FILTER_CONTEXT_MS = WINDOW_DFA_MS + 60_000L` (`180 s`) in `mBeatQueue`; in `calculateMetrics()`, check `mBeatQueue.any { it.beatTimestampMs >= now - WINDOW_DFA_MS }` first (clearing all 3 sensors to `null` on `120 s` drain), run `filterLiveCausal`, and evaluate `artifactRate` & `DfaCalculator` strictly over `dfaWindowBeats = filteredBeats.filter { it.beatTimestampMs >= now - WINDOW_DFA_MS }`.

---

## 7. System Invariants & Risk Assessment
- **Core Invariants**: `HrvGoldenOracleTest` (NeuroKit2 `0.2.10` `|ΔrMSSD| <= 0.5 ms`, `|Δα1| <= 0.02`/`0.03`) bit-identical; `TrackerService.java` & `WorkoutRrDbHelper.DB_VERSION = 2` untouched; Rule 51/52 & `120 s` drain-to-null preserved; `MIN_RMSSD_PAIRS = 3` preserved; `Localization Parity: N/A - no UI strings`.
- **Risk Rating**: **LOW** — Surgical 5-file fix with zero schema migration, zero monolith bloat, and deterministic golden/SQLite test verification.
