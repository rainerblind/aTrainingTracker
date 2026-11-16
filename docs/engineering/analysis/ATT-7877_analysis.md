# Stage 1 Analysis: ATT-7877 - Rehydrate Cockpit sparkline buffers and live map track after process restart

**Ticket**: [ATT-7877](https://rainerblind.atlassian.net/browse/ATT-7877)  
**Parent Epic**: [ATT-5908](https://rainerblind.atlassian.net/browse/ATT-5908) (*Cockpit & Live-Tracking UX*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.8` | **Branch**: `feature/ATT-7877`  
**Author**: AI Agent 1 (Implementer) | **Date**: 2026-11-16  

---

## 1. Problem Statement & Motivation
During active athletic tracking, background Android process termination and restart (`RESUME_SERVICE_RECREATION`) or UI reconnection restores accumulator state in `TrackerService` (ATT-7875), but leaves `BANALServiceRepository` with empty in-memory collections: `_currentTrack`, `_currentPathPoints`, and `sparklineBuffers`. Because these collections only append live 1-Hz ticks going forward, the athlete encounters empty Cockpit sparkline graphs, zero elapsed span, and a live map / elevation profile truncated to only post-restart coordinates, giving the false visual appearance that the workout restarted from scratch.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **Root Cause**: `BANALServiceRepository` initializes `_currentTrack` and `_currentPathPoints` to `SnapshotChunkedList.empty()`, and creates new `LiveSparklineBuffer` instances upon `registerSparklineBuffer()` without backfilling from the existing `WorkoutSamples.db` table (`baseFileName`).
* **Independent Peer Review & Verification**:
  * Audited `BANALServiceRepository.kt` (lines 210–215, 836–854): buffers are empty on process creation and only receive live points in `updateSparklineBuffers()` and location callbacks.
  * Audited `TrackerService.java` (lines 1837–1930, ATT-7875): `applyResumedState()` restores scalar accumulators, but does not rehydrate the UI repository's sparklines or GPS breadcrumbs.
  * Audited `TrackingViewModel.kt` (ATT-7874): `_isUiActive` gates map emissions to prevent OOMs; rehydrated collections must integrate with `SnapshotChunkedList` to keep memory bounded.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  * Asynchronously rehydrate `_currentTrack` (`List<LatLng>`) and `_currentPathPoints` (`List<PathPoint>`) in `BANALServiceRepository` from `WorkoutSamples.db` for the active workout on restart.
  * Backfill registered `LiveSparklineBuffer` instances over their `horizonSeconds` window from persisted numeric sensor columns (`HR`, `SPEED_mps`, `POWER_W`, `CADENCE`, `ALTITUDE`).
  * Ensure monotonic sample ordering, seamless deduplication against incoming live ticks, and zero UI stutter.
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  * No modification to sensor acquisition drivers or Bluetooth/ANT+ connection logic.
  * No redesign of Cockpit layout grids or `TrackingViewModel` map lifecycle gating (`ATT-7874`).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Net-new requirement only (`REQ-TRK-080` / `TST-TRK-080`)**: Extends `REQ-TRK-059` (Sparklines), `REQ-TRK-042` (Snapshot Chunked Track), and `REQ-TRK-078` (Crash Recovery Accumulators).
* **Preservation of Core Invariants**: Preserves `SnapshotChunkedList` memory bounds (`REQ-TRK-042.1`), `ATT-7874` offscreen map lifecycle gating (`REQ-TRK-079`), and `ATT-7875` accumulator recovery (`REQ-TRK-078`).

---

## 5. Change-Request Impact Analysis (SUP.10)
* **Affected Requirements**: `REQ-TRK-080` (Cockpit Telemetry & Map Track Rehydration on Process Restart).
* **Affected Architecture Elements**: `BANALServiceRepository.kt`, `LiveSparklineBuffer.kt`, `WorkoutRepository.kt`, `WorkoutSamplesDatabaseManager.java`.
* **Verification Impact & Test Surface**: Unit tests in `BANALServiceRepositoryTest.kt`, `LiveSparklineBufferTest.kt`, and `TrackerServiceResumeTest.kt`.
* **Risk Assessment & Invariants**: Low-to-medium risk; offloaded to `Dispatchers.IO` to ensure main-thread Cockpit fluidity; strictly preserves `SnapshotChunkedList` immutability.

---

## 6. Architectural Strategy & High-Level Solution
1. In `BANALServiceRepository`, introduce an asynchronous `rehydrateActiveWorkoutTelemetry(baseFileName: String)` executed on `Dispatchers.IO`.
2. Trigger rehydration when observing `TrackingMode.TRACKING` or `PAUSED` while `_currentTrack.value.isEmpty()` and an active workout exists (`WorkoutRepository.getResumedWorkoutState()` or `TrainingApplication.getActiveWorkoutID()`).
3. Query `WorkoutSamples.db` for coordinates (`LATITUDE`, `LONGITUDE`, `DISTANCE_m`, `ALTITUDE`) and backfill `_currentTrack` / `_currentPathPoints` via `SnapshotChunkedList.from()`.
4. In `LiveSparklineBuffer`, add `backfillHistoricalSamples(samples: List<Float>)` to thread-safely prepend or populate historical samples under `synchronized(lock)`.

---

## 7. System Invariants & Risk Assessment
* **Core Invariants**:
  1. Main-thread execution time must not be blocked during SQLite rehydration query.
  2. Memory usage must remain strictly bounded via `SnapshotChunkedList` and `LiveSparklineBuffer` decimation.
  3. Seamless deduplication: live ticks arriving during async DB read must not be lost or re-ordered.
* **Risk Rating**: **LOW** (Targeted repository-level state initialization on restart without schema changes).
