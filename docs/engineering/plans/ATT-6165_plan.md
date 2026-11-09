# Stage 3: Implementation Plan - ATT-6165: Catch Unhandled Exceptions in Strava Segment Sync

**Ticket**: [ATT-6165](https://rainerblind.atlassian.net/browse/ATT-6165)  
**Parent Epic**: [ATT-6033](https://rainerblind.atlassian.net/browse/ATT-6033) (*Code Architecture, Deduplication & Clean Code Refactoring*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.1` | **Branch**: `feature/ATT-6165`  
**Requirement Mapping**: `REQ-MAP-061` (`REQ-MAP-061.1`..`REQ-MAP-061.3`)  
**Test Mapping**: `TST-MAP-063` (`TST-MAP-063.1`..`TST-MAP-063.6`)  
**Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-09  

---

## 1. Problem Description & Background
In `SegmentsRepository.kt`, `syncSegmentsAsync` launches an unmanaged `CoroutineScope(Dispatchers.IO)` with commented-out error handling, while `syncStarredSegments` rethrows errors. In `SegmentListViewModel.kt`, `onRefresh` invokes `syncStarredSegments(sport)` in `viewModelScope` without `try/catch`. Network dropouts or Strava API errors crash the process. Meanwhile, `StravaSegmentsSyncWorker` relies on `syncStarredSegments` rethrowing exceptions to return `Result.retry()`.

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-MAP-061` (*Strava Segment Synchronization Exception Containment & Coroutine Safety*)
  * `REQ-MAP-061.1`: `SegmentsRepository.syncSegmentsAsync` runs on `repositoryScope`, catches `Exception`, rethrows `CancellationException`, logs errors.
  * `REQ-MAP-061.2`: `SegmentListViewModel.onRefresh` catches `Exception`, rethrows `CancellationException`, logs errors.
  * `REQ-MAP-061.3`: `SegmentsRepository.syncStarredSegments` preserves `throw e` for Worker retry and guarantees `_isSyncing.value = false` in `finally`.
* **Test Mapping**: `TST-MAP-063` (`TST-MAP-063.1`..`TST-MAP-063.6`)

---

## 3. System Invariants & Preserved Behavior
1. **Worker Retry Integrity**: `syncStarredSegments` continues to rethrow non-cancellation exceptions so `StravaSegmentsSyncWorker.doWork()` returns `Result.retry()`.
2. **Defensive Sync State Reset**: `_isSyncing.value = false` is always executed in `finally`.
3. **Structured Concurrency**: `CancellationException` is never swallowed, preserving coroutine hierarchy.
4. **Zero Regressions & Architecture Invariance**: Zero SQLite schema changes, zero UI layout alterations, and 100% full-suite test pass rate.

---

## 4. Proposed Architectural Changes
* **Repository Layer (`SegmentsRepository.kt`)**: Use existing `repositoryScope` (`SupervisorJob() + Dispatchers.IO`) in `syncSegmentsAsync`, wrapping `syncStarredSegments(bSportType)` with defensive `try/catch (e: Exception) { if (e is CancellationException) throw e; Log.e(TAG, ...) }`. Retain existing `throw e` in `syncStarredSegments`.
* **ViewModel Layer (`SegmentListViewModel.kt`)**: In `onRefresh(sport)`, wrap `segmentsRepository.syncStarredSegments(sport)` with `try/catch (e: Exception) { if (e is CancellationException) throw e; Log.e(TAG, ...) }`.
* **UI Consistency (Rule 23)**: No UI changes.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)
1. **Step 1 (Repository Exception Safety)**: In `SegmentsRepository.kt:204-214`, switch `syncSegmentsAsync` to `repositoryScope.launch` and restore active `try/catch (e: Exception)` with `CancellationException` rethrow.
2. **Step 2 (ViewModel Refresh Guard)**: In `SegmentListViewModel.kt:167-171`, wrap `syncStarredSegments(sport)` in `try/catch (e: Exception)` with `CancellationException` rethrow.
3. **Step 3 (Unit Test Suite `TST-MAP-063`)**:
   - Update `SegmentsRepositorySyncTest.kt` with tests for `syncSegmentsAsync` error containment and cancellation propagation.
   - Create `SegmentListViewModelTest.kt` verifying `onRefresh` error containment and cancellation propagation.
   - Run targeted tests:
     ```bash
     ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.segments.*" --tests "com.atrainingtracker.trainingtracker.ui.segments.segmentlist.*" --tests "com.atrainingtracker.trainingtracker.build.*"
     ```

---

## 6. Verification & Rollback Plan
* **Verification**: Execute targeted Tier 1 unit tests (`TST-MAP-063.6`) and `python3 tools/verify_requirement_governance.py`.
* **Rollback**: Atomic Git branch isolation (`feature/ATT-6165`) allows immediate revert via `git reset --hard` before merge.
