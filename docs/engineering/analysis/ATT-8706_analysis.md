# Stage 1 Analysis: ATT-8706 - Unflagged Multi-Second RR Gaps, Out-of-Order Beat Timestamps, and Negative HRV_DFA_A1

**Ticket**: `ATT-8706` (`2 SP`, Epic `ATT-3539`) | **Target Release**: `V5.0.0` | **Sprint**: `sprint/2026-11-17_03_Nacht` | **Branch**: `feature/ATT-8706` | **Date**: 2026-11-17

## 1. Problem Statement & Motivation
* On Pixel 10 run `Kurze Runde var 2 #52` (`2026-11-17_173305`, `workoutID=2188`, **Polar H10 + Polar Verity Sense**), `WorkoutRr.db` persisted non-monotonic `beat_timestamp_ms` (`beat 109->110: -430 ms`; `4144..4147` interleaved at `+1685.3s -> +1685.6s -> +1690.2s -> +1690.8s`) and unflagged multi-second RR intervals (`5,196.29 ms`, `5,035.16 ms`, `3,748.05 ms`, `max = 6,377.93 ms`, all `artifact_flag = 0`, `gap_beats = 0`).
* `HrvPostWorkoutRecomputer` wrote `HRV_DFA_A1 = -1.0684` (`rows 1627–1631`) while live `HRVMetricsDevice` reported `0.875–0.886`. Expected: monotonic `beat_timestamp_ms`, multi-second RR gaps flagged as `ARTIFACT_GAP` (`5`), sorted beats before filtering, and no non-physical negative `HRV_DFA_A1`.

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **Independent Peer Review & Verification (Live `34b78941`)**:
  1. **Non-Monotonic `beat_timestamp_ms` (Dual-Source Multiplexing + Unclamped Re-Anchor)**:
     * `DeviceManager.java:459-475, 1278-1286` registers **every** `RrBeatSource` (`BTLEHeartRateDevice`, `PolarOnlineStreamingBridge`, `ANTHeartRateDevice`) onto `TrackerService.mRrBeatListener` (`TrackerService.java:279-297, 941`) and `mHrvMetricsDevice`. Simultaneous Polar H10 + Verity Sense streams interleave into one `mRrBeatIndexCounter` (`4144->4146` `+1685.3s->+1685.6s`, `4145->4147` `+1690.2s->+1690.8s`, where `1685.6 + 5.196 = 1690.8`). Fractional values (`5,196.29`, `6,377.93`) confirm `1/1024 s` BLE `0x2A37` decoding (`BTLEHeartRateDevice.java:177`).
     * On `drift > 2000 ms`, `BTLEHeartRateDevice.java:218-252`, `PolarOnlineStreamingBridge.kt:513-540`, and `RrBeatTimestampReconstructor.kt:70-79` reset `timestamps[k-1] = packetArrivalMs` and back-calculate earlier bundle beats **without** clamping `beatTimestampMs >= lastBeatTimestampMs`, allowing `-430 ms` backward jumps (`beat 109->110`).
  2. **Unflagged Multi-Second RR Gaps (`artifact_flag = 0, gap_beats = 0`)**:
     * `BTLEHeartRateDevice.java:215-274`, `PolarOnlineStreamingBridge.kt:512-558`, and `RrBeatTimestampReconstructor.kt:67-84` detect gaps solely via wall-clock drift (`|packetArrivalMs - candidateFinalMs| > 2000 ms`). When a strap transmits a single multi-second RR (`5,196 ms`) on time, `drift <= 2000 ms` enters the continuous branch hardcoding `gapBeats = 0, artifactFlag = ARTIFACT_NORMAL (0)`. Even in the drift branch with `k = 1`, `round(elapsed / rrMs) - 1 = 0`. Neither ingestion nor `RrArtifactFilter.kt:108, 157` enforces physiological RR bounds (`[250, 2000] ms` / `[250, 2500] ms`).
  3. **Filter-Before-Sort in `HrvPostWorkoutRecomputer.kt:98-101` (Ticket Claim Verified)**:
     * `WorkoutRrDatabaseManager.kt:111` returns beats sorted by `beat_index ASC`. `HrvPostWorkoutRecomputer.kt:99-101` executes `artifactFilter.filterBidirectionalBeats(beats)` **before** `filteredBeats.sortedBy { it.beatTimestampMs }`. Out-of-order/interleaved beats inflate `dRrs`/`mRrs` and the 91-beat quartile thresholds (`th1`/`th2`, `RrArtifactFilter.kt:119-120`), letting multi-second gaps slip through at `2.44% <= 5.0%` (`ARTIFACT_GATE_THRESHOLD_PERCENT`).
  4. **Negative DFA $\alpha_1$ (`-1.0684`) (`DfaCalculator.kt:59-108`, `HrvPostWorkoutRecomputer.kt:192-196`)**:
     * Unflagged multi-second gaps / interleaved high-frequency alternations entering `DfaCalculator.computeDfaAlpha1(nnIntervals)` cause small-box variance `F(4)` to exceed large-box variance `F(16)`, inverting the log-log OLS slope (`computeLinearSlope`, `:139-160`) to `-1.0684`. Neither `DfaCalculator`, `HrvPostWorkoutRecomputer` (`:195`), nor `HRVMetricsDevice` (`:189`) rejects non-physiological `alpha1 <= 0.0` (only `isFinite() && !isNaN()` is checked).

## 3. User Scope Grounding (`ATT-1250`)
* **In-Scope**: (1) Sort & normalize non-monotonic `beatTimestampMs` before `filterBidirectionalBeats()` in `HrvPostWorkoutRecomputer`; (2) Enforce hard physiological RR bounds (`rrMs` gap/outlier detection -> `ARTIFACT_GAP` / `gapBeats`) at ingestion (`BTLEHeartRateDevice`, `PolarOnlineStreamingBridge`, `RrBeatTimestampReconstructor`) and defensively in `RrArtifactFilter`; (3) Prevent concurrent dual-HRM `RrBeatSource` interleaving / enforce monotonic timestamp clamping across `TrackerService` / `DeviceManager` ingestion; (4) Gate non-physical `alpha1 <= 0.0` in `DfaCalculator` / `HrvPostWorkoutRecomputer` / `HRVMetricsDevice`.
* **Out-of-Scope**: `WorkoutRr.db` schema migration, `PolarH10OfflineController` PFTP protocol redesign, `FitFileWriter` changes, UI/strings (`N/A - no UI strings`), `ANT-Android-SDKs`.

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-CON-027` (`req_tracking_cockpit.md:3719`), `REQ-TRK-060` (`:3784`), `REQ-PER-034` (`req_aftermath_periods.md:1043`). Targets `BTLEHeartRateDevice.java`, `PolarOnlineStreamingBridge.kt`, `RrBeatTimestampReconstructor.kt`, `RrArtifactFilter.kt`, `DfaCalculator.kt`, `HrvPostWorkoutRecomputer.kt`, `HRVMetricsDevice.kt`, `TrackerService.java`.
* **Historical Origin & Commit Trace**: `8b2196d9` (`ATT-6714`, split/merge & `overlap=True`), `50ffedc6` (`ATT-4970`, durability), `c54768d2` (`ATT-7530`), `42d1e4c9` (`ATT-8578`).
* **Root Reason for Existing Formulation**: `REQ-CON-027.3` & `REQ-PER-034.2` only checked wall-clock drift (`> 2000 ms`) assuming a single continuous sensor stream; `HrvPostWorkoutRecomputer` relied on `beat_index ASC` ordering from `WorkoutRrDatabaseManager`.
* **Preservation of Core Invariants**: Must preserve `HrvGoldenOracleTest` NeuroKit2 0.2.10 parity (`|ΔrMSSD| <= 0.5 ms`, `|Δα1| <= 0.02` pre-corrected / `<= 0.03` end-to-end), `DfaCalculatorTest.testConstantSignalDoesNotCrash` (`NaN || 0.0`), `RrArtifactFilterTest` split/merge semantics, and `REQ-TRK-084.4` conditional `AccumulatedSensors` upsert.

## Change Impact Analysis
* **Affected Requirements**: Net-new `REQ-PER-*` / `TST-PER-*` (or sub-clause refining `REQ-PER-034.2/.3`, `REQ-CON-027.3`, `REQ-TRK-060.2/.4`).
* **Affected Architecture Elements**: `BTLEHeartRateDevice.java`, `PolarOnlineStreamingBridge.kt`, `RrBeatTimestampReconstructor.kt`, `TrackerService.java`, `RrArtifactFilter.kt`, `HrvPostWorkoutRecomputer.kt`, `DfaCalculator.kt`, `HRVMetricsDevice.kt`.
* **Verification Impact & Test Surface**: `HrvPostWorkoutRecomputerTest`, `RrArtifactFilterTest`, `DfaCalculatorTest`, `HrvGoldenOracleTest`, `HRVMetricsDeviceTest`, `BTLEHeartRateDeviceTest`, `PolarOnlineStreamingBridgeTest`, `RrBeatTimestampReconstructionTest`, `TrackerServiceRrFlushTest` (`N/A - no UI strings`).
* **Risk Assessment & Invariants**: **MEDIUM** (`2 SP`). Preserves NeuroKit2 tolerances, monotonic SQLite `WorkoutRr.db` inserts without schema bump, and `@Synchronized` thread safety across BLE/coroutine dispatchers.

## 6. Architectural Strategy & High-Level Solution
1. **Ingestion Monotonicity & Physiological RR Gap Flagging**: Clamp reconstructed `beatTimestampMs >= lastBeatTimestampMs` in `BTLEHeartRateDevice`, `PolarOnlineStreamingBridge`, and `RrBeatTimestampReconstructor`; flag implausible RR durations (`rrMs > 2500.0 || rrMs < 250.0` ms) as `ARTIFACT_GAP` with estimated `gapBeats`, and prevent dual-`RrBeatSource` timestamp/index interleaving in `TrackerService.mRrBeatListener` / `DeviceManager`.
2. **Pre-Sort & Defensive Gap Guard in Filtering**: Sort `beats` by `beatTimestampMs` (normalizing/deduplicating backward timestamps) **before** `filterBidirectionalBeats(beats)` in `HrvPostWorkoutRecomputer`; treat unflagged extreme RR (`> 2500.0 ms` or `< 250.0 ms`) as `ARTIFACT_GAP` inside `RrArtifactFilter`.
3. **DFA Sanity Gate**: Reject non-positive/non-finite slopes (`!alpha1.isFinite() || alpha1 <= 0.0`) as `Double.NaN` / `null` in `DfaCalculator` (preserving `testConstantSignalDoesNotCrash`), `HrvPostWorkoutRecomputer`, and `HRVMetricsDevice`.

## 7. System Invariants & Risk Assessment
* **Core Invariants**: (1) NeuroKit2 0.2.10 golden parity (`<= 0.02 / 0.03` & `<= 0.5 ms`); (2) Zero `WorkoutRr.db` schema bump; (3) Thread safety on single-thread/synchronized RR queues; (4) Human Decision Gate enforced.
* **Risk Rating**: **MEDIUM** — Multi-file RR ingestion + post-workout pipeline fix; bounded by comprehensive golden and unit test suites.
