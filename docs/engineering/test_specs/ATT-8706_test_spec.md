# Stage 2: Requirement & Test Specification - ATT-8706

**Ticket**: [ATT-8706](https://rainerblind.atlassian.net/browse/ATT-8706) | **Parent Epic**: [ATT-3539](https://rainerblind.atlassian.net/browse/ATT-3539)  
**Target Release**: `V5.0.0` | **Active Sprint**: `sprint/2026-11-17_03_Nacht` | **Branch**: `feature/ATT-8706`  
**Requirement Mapping**: `REQ-TRK-085` (refining `REQ-CON-027.3`, `REQ-TRK-060.2`/`.4`, `REQ-PER-034.2`/`.3`) | **Test Spec ID**: `TST-TRK-085` | **Date**: 2026-11-18  

---

## 1. Requirement Specification (`REQ-TRK-085`)

### 1.1 Attributes & Rationale
- **Priority**: High | **Type**: Functional | **Verification Method**: Unit | **Status**: `[Planned]`
- **Rationale**: Dual simultaneous HRMs (`BTLEHeartRateDevice` + `PolarOnlineStreamingBridge`) multiplexed into one `TrackerService.mRrBeatListener`/`mRrBeatIndexCounter`, unclamped drift re-anchoring caused negative `beat_timestamp_ms` jumps (`-430 ms`), drift-only gap detection left multi-second RR intervals (`5,196–6,378 ms`) unflagged (`artifact_flag = 0`), `HrvPostWorkoutRecomputer` filtered before sorting by `beatTimestampMs`, and `DfaCalculator` emitted negative slopes (`HRV_DFA_A1 = -1.0684`).

### 1.2 Functional & Architectural Requirements
1. **`REQ-TRK-085.1` (Single-Primary-Source RR Routing & Monotonic Clamping)**: `DeviceManager` / `TrackerService` SHALL route live RR beats to `TrackerService.mRrBeatListener` and `HRVMetricsDevice` from at most one active primary `RrBeatSource` stream at a time (preventing dual-HRM interleaving). `BTLEHeartRateDevice`, `PolarOnlineStreamingBridge`, and `RrBeatTimestampReconstructor` MUST enforce monotonic non-decreasing `beatTimestampMs >= lastBeatTimestampMs` across bundle back-calculation and drift re-anchoring (`drift > 2000 ms`).
2. **`REQ-TRK-085.2` (Physiological RR Gap Flagging)**: `BTLEHeartRateDevice`, `PolarOnlineStreamingBridge`, and `RrBeatTimestampReconstructor` at ingestion—and `RrArtifactFilter` (`filterBidirectionalBeats`, `filterLiveCausal`) defensively—SHALL flag implausible physiological RR intervals (`rrMs > 2500.0` ms or `rrMs < 250.0` ms) with `RrBeatEvent.ARTIFACT_GAP` (`5`) and positive estimated `gapBeats`, even when wall-clock `drift <= 2000 ms` or `k = 1`.
3. **`REQ-TRK-085.3` (Pre-Filter Chronological Sorting)**: `HrvPostWorkoutRecomputer` SHALL sort `beats` chronologically by `beatTimestampMs` (deterministic tie-break on `rawCounter` plus monotonic normalization of residual backward timestamps) BEFORE invoking `RrArtifactFilter.filterBidirectionalBeats()`, preventing out-of-order beats from inflating 91-beat quartile thresholds (`th1`/`th2`).
4. **`REQ-TRK-085.4` (DFA α1 Validity Sanity Gate)**: `DfaCalculator.computeDfaAlpha1()`, `HrvPostWorkoutRecomputer`, and `HRVMetricsDevice` SHALL reject non-finite or non-positive DFA α1 slopes (`!alpha1.isFinite() || alpha1 <= 0.0`) cleanly as `Double.NaN` / `null`, preserving `DfaCalculatorTest.testConstantSignalDoesNotCrash` and NeuroKit2 0.2.10 golden parity (`|Δα1| <= 0.02` pre-corrected / `<= 0.03` end-to-end, `|ΔrMSSD| <= 0.5 ms`; `Localization Parity: N/A - no UI strings`).

### 1.3 Acceptance Criteria (Given-When-Then) & Invariants
- *Given* concurrent Polar H10 + Verity Sense `RrBeatSource` devices OR a drift re-anchor (`drift > 2000 ms`) with multi-beat bundles, *When* RR beats are dispatched/reconstructed, *Then* only one active primary source feeds `mRrBeatListener`/`mHrvMetricsDevice` and `beatTimestampMs >= lastBeatTimestampMs`.
- *Given* an RR interval `rrMs > 2500.0 ms` (e.g., `5196.29 ms`) or `< 250.0 ms` with `drift <= 2000 ms`, *When* evaluated at ingestion or in `RrArtifactFilter`, *Then* it is flagged `ARTIFACT_GAP` (`5`) with `gapBeats > 0`.
- *Given* `WorkoutRr.db` beats with out-of-order `beat_timestamp_ms` and multi-second gaps, *When* `HrvPostWorkoutRecomputer.recomputeWorkout()` runs, *Then* beats sort by `beatTimestampMs` before `filterBidirectionalBeats()` and `HRV_DFA_A1` never persists `<= 0.0` values (`Double.NaN` / `null`).
- **Invariants**: NeuroKit2 0.2.10 parity (`|ΔrMSSD| <= 0.5 ms`, `|Δα1| <= 0.02`/`0.03`); `DfaCalculatorTest.testConstantSignalDoesNotCrash`; `REQ-TRK-084.4` conditional upsert; zero `WorkoutRr.db` schema bump; `@Synchronized` thread safety; `N/A - no UI strings`.

---

## 2. Test Specification (`TST-TRK-085`, Offline `Rule 5` / `REQ-PRO-049.1`)
- **`TST-TRK-085.1` (`DeviceManagerRrSourceTest`, `TrackerServiceRrFlushTest`, `BTLEHeartRateDeviceTest`, `PolarOnlineStreamingBridgeTest`, `RrBeatTimestampReconstructionTest`)**:
  - Register dual `RrBeatSource` devices and assert single-primary-source RR routing without index/timestamp interleaving; trigger drift re-anchoring and verify `beatTimestampMs >= lastBeatTimestampMs` across all emitted beats.
- **`TST-TRK-085.2` (`BTLEHeartRateDeviceTest`, `PolarOnlineStreamingBridgeTest`, `RrBeatTimestampReconstructionTest`, `RrArtifactFilterTest`)**:
  - Inject `rrMs > 2500.0` (`5196.29 ms`, `6377.93 ms`) and `< 250.0` ms at `drift <= 2000 ms` (`k = 1`); assert `artifactFlag == ARTIFACT_GAP` (`5`) and `gapBeats > 0`, while `[250, 2500] ms` remains `ARTIFACT_NORMAL`.
- **`TST-TRK-085.3` (`HrvPostWorkoutRecomputerTest`)**:
  - Seed interleaved/out-of-order `beat_timestamp_ms` + multi-second RR gaps in `WorkoutRr.db`; run `recomputeWorkout()` and assert chronological sort precedes `filterBidirectionalBeats()` and zero negative `HRV_DFA_A1` rows are persisted.
- **`TST-TRK-085.4` (`DfaCalculatorTest`, `HRVMetricsDeviceTest`, `HrvGoldenOracleTest`)**:
  - Feed negative-slope synthetic NN intervals; assert `DfaCalculator.computeDfaAlpha1()` returns `Double.NaN` and `HRVMetricsDevice`/`HrvPostWorkoutRecomputer` emit `null`, while `testConstantSignalDoesNotCrash` and `HrvGoldenOracleTest` (`|Δα1| <= 0.02`/`0.03`, `|ΔrMSSD| <= 0.5 ms`) pass strictly offline with zero `src/main` reads (`REQ-PRO-049.1`).

---

## 3. Traceability Matrix

| Test Case | Scope | Target Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-TRK-085.1` | Unit | `DeviceManager`, `TrackerService`, `BTLEHeartRateDevice`, `PolarOnlineStreamingBridge`, `RrBeatTimestampReconstructor` | `REQ-TRK-085.1` | Proposed |
| `TST-TRK-085.2` | Unit | `BTLEHeartRateDevice`, `PolarOnlineStreamingBridge`, `RrBeatTimestampReconstructor`, `RrArtifactFilter` | `REQ-TRK-085.2` | Proposed |
| `TST-TRK-085.3` | Unit | `HrvPostWorkoutRecomputer` | `REQ-TRK-085.3` | Proposed |
| `TST-TRK-085.4` | Unit / Golden | `DfaCalculator`, `HRVMetricsDevice`, `HrvGoldenOracleTest` | `REQ-TRK-085.4` | Proposed |
