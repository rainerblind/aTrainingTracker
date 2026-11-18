# Stage 3: Implementation Plan - ATT-8706: HRV/RR Monotonicity, Gap Flagging & DFA α1 Gate

**Ticket**: `ATT-8706` (`2 SP`, Epic `ATT-3539`) | **Target Release**: `V5.0.0` | **Sprint**: `sprint/2026-11-17_03_Nacht`
**Requirements**: `REQ-TRK-085` (`.1`–`.4`) | **Tests**: `TST-TRK-085` (`.1`–`.4`) | **Branch**: `feature/ATT-8706` | **Date**: 2026-11-18

## 1. Problem Description & Background
* Pixel 10 run `Kurze Runde var 2 #52` (`workoutID=2188`, Polar H10 + Verity Sense) persisted interleaved/non-monotonic `beat_timestamp_ms`, unflagged multi-second RR gaps (`5,196–6,378 ms`, `artifact_flag=0`), and `HRV_DFA_A1 = -1.0684` in `HrvPostWorkoutRecomputer`.

## 2. Traceability & Requirements Mapping
* `REQ-TRK-085.1` (`TST-TRK-085.1`): Single-primary `RrBeatSource` routing + monotonic `beatTimestampMs >= lastBeatTimestampMs`.
* `REQ-TRK-085.2` (`TST-TRK-085.2`): Physiological RR bounds (`250.0..2500.0 ms`) -> `ARTIFACT_GAP` (`5`) + `gapBeats > 0`.
* `REQ-TRK-085.3` (`TST-TRK-085.3`): Chronological sort + monotonic normalization before `filterBidirectionalBeats()`.
* `REQ-TRK-085.4` (`TST-TRK-085.4`): Reject non-finite / `<= 0.0` DFA α1 as `Double.NaN` / `null`.

## 3. System Invariants & Preserved Behavior
1. **NeuroKit2 0.2.10 Parity**: `HrvGoldenOracleTest` (`|Δα1| <= 0.02` / `<= 0.03`, `|ΔrMSSD| <= 0.5 ms`) and `DfaCalculatorTest.testConstantSignalDoesNotCrash` preserved.
2. **Thread Safety & Schema**: Single-thread/synchronized RR queues & dispatchers preserved; zero `WorkoutRr.db` schema bump; `REQ-TRK-084.4` conditional upsert preserved.
3. **Human Decision Gate**: Parent ticket stops at `Final Review (Human)`.

## 4. Proposed Architectural Changes
* **Component 1 — Shared RR Normalization & Gap Helper (`RrBeatNormalization.kt`)**: Pure-Kotlin helper (`MIN_RR_MS = 250.0`, `MAX_RR_MS = 2500.0`, `DRIFT_THRESHOLD_MS = 2000L`) shared by `BTLEHeartRateDevice.java`, `PolarOnlineStreamingBridge.kt`, and `RrBeatTimestampReconstructor.kt` (zero duplication) enforcing `beatTimestampMs >= lastBeatTimestampMs` and physiological `ARTIFACT_GAP` (`5`) / `gapBeats` calculation even when `drift <= 2000 ms` or `k = 1`.
* **Component 2 — Primary `RrBeatSource` Arbitration (`DeviceManager.java`, `TrackerService.java`)**: `DeviceManager` arbitrates `RrBeatSource` priority (`BTLEHeartRateDevice` / non-Verity ECG > `ANTHeartRateDevice` > PPG `PolarOnlineStreamingBridge.isVeritySense`), forwarding beats from one active primary source at a time to `mHrvMetricsDevice` and `mRrBeatListeners`, with automatic fallback if primary disconnects or goes stale (`> 5000 ms`). `TrackerService.mRrBeatListener` additionally enforces monotonic `beatTimestampMs` floor on queued `WorkoutRrInterval`s. Update `docs/architecture/detailed_design/banal_service.md` (`REQ-PRO-114`).
* **Component 3 — Pre-Sort & Defensive Filter (`HrvPostWorkoutRecomputer.kt`, `RrArtifactFilter.kt`)**: Sort by `beatTimestampMs` (`thenBy { rawCounter }`) + monotonic normalization before `filterBidirectionalBeats()`; `RrArtifactFilter` (`filterBidirectionalBeats` & `filterLiveCausal`) defensively flags `< 250.0` or `> 2500.0 ms` intervals as `ARTIFACT_GAP`.
* **Component 4 — DFA α1 Validity Gate (`DfaCalculator.kt`, `HrvPostWorkoutRecomputer.kt`, `HRVMetricsDevice.kt`)**: Return `Double.NaN` in `DfaCalculator.computeDfaAlpha1()` when `!slope.isFinite() || slope <= 0.0`; gate `calculated > 0.0` at both `HrvPostWorkoutRecomputer` and `HRVMetricsDevice`.
* **UI Consistency (`Rule 23`)**: No UI changes (`N/A - no UI strings`).

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)
1. **Step 1 (`REQ-TRK-085.1/.2`)**: Create `RrBeatNormalization.kt`; refactor `BTLEHeartRateDevice.java`, `PolarOnlineStreamingBridge.kt`, and `RrBeatTimestampReconstructor.kt` to delegate monotonic clamping & physiological `[250.0, 2500.0] ms` gap flagging to it.
2. **Step 2 (`REQ-TRK-085.1`, `REQ-PRO-114`)**: Implement single-primary `RrBeatSource` arbitration + stale/disconnect fallback in `DeviceManager.java`, monotonic floor guard in `TrackerService.mRrBeatListener`, and update `docs/architecture/detailed_design/banal_service.md`.
3. **Step 3 (`REQ-TRK-085.2/.3`)**: Update `HrvPostWorkoutRecomputer.kt` to sort chronologically + normalize timestamps before `filterBidirectionalBeats()`, and add defensive `[250.0, 2500.0] ms` gap classification in `RrArtifactFilter.kt`.
4. **Step 4 (`REQ-TRK-085.4`)**: Add `<= 0.0` / non-finite slope gate in `DfaCalculator.kt`, `HrvPostWorkoutRecomputer.kt`, and `HRVMetricsDevice.kt`.
5. **Step 5 (`TST-TRK-085.1`..`.4`)**: Add/update offline unit & golden tests (`DeviceManagerRrSourceTest`, `TrackerServiceRrFlushTest`, `BTLEHeartRateDeviceTest`, `PolarOnlineStreamingBridgeTest`, `RrBeatTimestampReconstructionTest`, `RrArtifactFilterTest`, `HrvPostWorkoutRecomputerTest`, `DfaCalculatorTest`, `HRVMetricsDeviceTest`, `HrvGoldenOracleTest`, `DetailedDesignParityContractTest`); run Tier 1:
   `./gradlew :app:testDebugUnitTest --tests "*.BTLEHeartRateDeviceTest" --tests "*.PolarOnlineStreamingBridgeTest" --tests "*.RrBeatTimestampReconstructionTest" --tests "*.DeviceManagerRrSourceTest" --tests "*.TrackerServiceRrFlushTest" --tests "*.RrArtifactFilterTest" --tests "*.HrvPostWorkoutRecomputerTest" --tests "*.DfaCalculatorTest" --tests "*.HRVMetricsDeviceTest" --tests "*.HrvGoldenOracleTest" --tests "*.DetailedDesignParityContractTest"`

## 6. Verification & Rollback Plan
* **Verification**: Targeted Tier 1 JUnit/Robolectric/MockK tests + `verify_requirement_governance.py` (zero `src/main` reads per `REQ-PRO-049.1`).
* **Rollback**: Branch isolation on `feature/ATT-8706` allows full revert without affecting `sprint/2026-11-17_03_Nacht` or `develop`.
