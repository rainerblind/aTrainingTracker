# Implementation Plan: ATT-7877 - Rehydrate Cockpit sparkline buffers and live map track from WorkoutSamples after process restart
**Ticket**: [ATT-7877](https://rainerblind.atlassian.net/browse/ATT-7877)
**Parent Epic**: [ATT-5908](https://rainerblind.atlassian.net/browse/ATT-5908)
**Target Release**: `2026.8` | **Active Sprint**: `2026-46.8`
**Requirement Mapping**: `REQ-TRK-080` | **Test Spec**: `TST-TRK-080`
**Author**: AI Agent 1 (Implementer) | **Date**: 2026-11-16
---
## 1. Architectural Strategy & Overview
- Rehydrate in-memory Cockpit telemetry in `BANALServiceRepository` upon process restart/reconnect (`RESUME_SERVICE_RECREATION` or non-empty active session) without blocking main thread.
- Asynchronously query `WorkoutSamplesDatabaseManager` on `Dispatchers.IO` for active `baseFileName`.
- Monotonically prepend/merge historical GPS coordinates into `_currentTrack` / `_currentPathPoints` using `SnapshotChunkedList.from()`.
- Prepend historical metric series into `LiveSparklineBuffer` via `backfillHistoricalSamples()` while preserving 1-Hz concurrent live ticks and capacity limits.
- On clean start (`START_NORMAL`), skip rehydration to maintain empty telemetry state.

## 2. Atomic Implementation Steps
- **Step 1**: Add `backfillHistoricalSamples(samples: List<Float>)` to `LiveSparklineBuffer.kt` under synchronized lock to safely prepend historical data ahead of concurrent live ticks.
- **Step 2**: Add `rehydrateActiveWorkoutTelemetry(baseFileName: String)` in `BANALServiceRepository.kt` on `Dispatchers.IO`:
  - Fetch stored GPS coordinates, altitude, HR, speed, power, and cadence from `WorkoutSamplesDatabaseManager`.
  - Atomically construct and update `_currentTrack` and `_currentPathPoints` using `SnapshotChunkedList.from()`.
  - Backfill registered `sparklineBuffers` via `backfillHistoricalSamples()`.
- **Step 3**: Trigger rehydration in `BANALServiceRepository.bindService()` or resume callback when `isResumedSession == true` and `baseFileName` is present.
- **Step 4**: Unit tests in `LiveSparklineBufferTest.kt` and `BANALServiceRepositoryTest.kt` verifying monotonic merge, buffer capacity truncation, and clean start skipping.
- **Step 5**: Update living docs (`req_tracking_cockpit.md`, `tst_tracking_cockpit.md`) to `[Implemented]`.

## 3. UI Consistency & Style Guide Compliance
- Preserves offscreen map lifecycle gating from `ATT-7874` (`isUiActive` / `Lifecycle.State.STARTED`).
- All state updates emitted via existing StateFlows (`currentTrack`, `currentPathPoints`, `sparklineBuffers`) for Compose reactivity without jank.

## 4. Verification Plan & Test Commands
- Tier 1 Unit Tests:
  `./gradlew :app:testDebugUnitTest --tests "*BANALServiceRepositoryTest" --tests "*LiveSparklineBufferTest"`
- Requirement & Governance Check:
  `python3 tools/verify_requirement_governance.py`

## 5. System Invariants & Rollback Safety
- **ATT-7874 Invariant**: Offscreen GPS rendering remains decoupled from background GPS collection.
- **ATT-7875 Invariant**: `TrackerService` crash accumulators and state machine are preserved intact.
- **Thread Safety**: DB queries strictly isolated on `Dispatchers.IO`; buffer mutations synchronized.
- **Rollback Safety**: Rehydration is additive; failure or empty DB yields empty buffers gracefully.
* **Risk Classification**: **STANDARD** (Risk Rating: **LOW**)
