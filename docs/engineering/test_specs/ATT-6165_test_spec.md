# Stage 2: Requirement & Test Specification - ATT-6165: Catch Unhandled Exceptions in Strava Segment Sync

**Ticket**: [ATT-6165](https://rainerblind.atlassian.net/browse/ATT-6165)  
**Parent Epic**: [ATT-6033](https://rainerblind.atlassian.net/browse/ATT-6033) (*Code Architecture, Deduplication & Clean Code Refactoring*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.1` | **Branch**: `feature/ATT-6165`  
**Requirement Mapping**: `REQ-MAP-061` | **Test Spec ID**: `TST-MAP-063`  
**Author**: AI Agent 1 (Implementer, `subagent_flash`) | **Date**: 2026-11-09  

---

## 1. Requirement Specification (REQ-MAP-061)

### 1.1 Problem Statement & Rationale
In `SegmentsRepository.kt`, `syncSegmentsAsync` launches an unmanaged `CoroutineScope(Dispatchers.IO)` with commented-out exception handling, and in `SegmentListViewModel.kt`, `onRefresh` calls `syncStarredSegments(sport)` within `viewModelScope` without `try/catch`. Network errors or deserialization failures crash the app. Meanwhile, `StravaSegmentsSyncWorker` relies on `syncStarredSegments` rethrowing exceptions to return `Result.retry()`.

### 1.2 Functional & Architectural Requirements
1. **Repository Async Scope & Error Containment (`REQ-MAP-061.1`)**:
   - `SegmentsRepository.syncSegmentsAsync(bSportType)` SHALL execute on `repositoryScope` (`SupervisorJob() + Dispatchers.IO`) instead of creating unmanaged scopes.
   - The call SHALL be wrapped in `try/catch (e: Exception)`. If `e is CancellationException`, it SHALL be rethrown; other exceptions SHALL be logged without crashing.
2. **ViewModel Pull-to-Refresh Safety (`REQ-MAP-061.2`)**:
   - `SegmentListViewModel.onRefresh(sport)` SHALL wrap `segmentsRepository.syncStarredSegments(sport)` in `try/catch (e: Exception)`.
   - `CancellationException` SHALL be rethrown; other exceptions SHALL be logged.
3. **Worker Retry & Invariant Preservation (`REQ-MAP-061.3`)**:
   - `SegmentsRepository.syncStarredSegments(bSportType)` SHALL strictly retain `throw e` so `StravaSegmentsSyncWorker` catches it and returns `Result.retry()`.
   - `_isSyncing.value = false` SHALL always be executed in `finally`.

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Non-Crashing Async Sync & Refresh)**: Given network errors in `syncStarredSegments`, when `syncSegmentsAsync` or `SegmentListViewModel.onRefresh` is invoked, then the exception is caught, logged, the app does not crash, and `isSyncing` resets to `false`.
* **Criterion 2 (Worker Retry on Rethrow)**: Given `syncStarredSegments` throwing an `Exception` during worker sync, when `StravaSegmentsSyncWorker.doWork()` executes, then the rethrown exception causes the worker to return `Result.retry()`.
* **Criterion 3 (Cancellation Preservation)**: Given a cancellation event during sync, when `CancellationException` is thrown, then it is rethrown to preserve structured concurrency.

### Requirement Archaeology & Chesterton's Fence Audit
1. *Original Requirement ID & Target*: Net-new refinement `REQ-MAP-061` refining `REQ-MAP-005` and `REQ-EXP-014`.
2. *Historical Origin & Commit Trace*: `ATT-1078` introduced `syncSegmentsAsync` with commented-out catch; `SegmentListViewModel` added in Compose migration.
3. *Root Reason for Existing Formulation*: `syncStarredSegments` throws so `StravaSegmentsSyncWorker` triggers `Result.retry()`. `syncSegmentsAsync` was an asynchronous helper whose catch block was inadvertently commented out.
4. *Preservation of Core Invariants*: `throw e` in `syncStarredSegments` preserved; `_isSyncing = false` in `finally` preserved; `CancellationException` rethrown.

---

## 2. Test Specification (TST-MAP-063)

### Test Case 1: `syncSegmentsAsync` Exception Containment (`TST-MAP-063.1`)
* **Scope**: Unit test in `SegmentsRepositorySyncTest.kt`. Force exception in worker/prune; invoke `repository.syncSegmentsAsync(BSportType.UNKNOWN)`. Assert no crash on `repositoryScope`, error logged, and `isSyncing` resets to `false`.

### Test Case 2: `syncStarredSegments` Rethrow & Finally Guarantee (`TST-MAP-063.2`)
* **Scope**: Unit test in `SegmentsRepositorySyncTest.kt`. Invoke `repository.syncStarredSegments(...)` with mock failure. Assert exception is rethrown and `isSyncing` resets to `false` in `finally`.

### Test Case 3: `SegmentListViewModel.onRefresh` Exception Containment (`TST-MAP-063.3`)
* **Scope**: Unit test in `SegmentListViewModelTest.kt`. Mock `syncStarredSegments` throwing `IOException`; invoke `viewModel.onRefresh(BSportType.BIKE)`. Assert no uncaught exception and error logged.

### Test Case 4: Structured Cancellation Integrity (`TST-MAP-063.4`)
* **Scope**: Unit tests in `SegmentsRepositorySyncTest.kt` & `SegmentListViewModelTest.kt`. Assert `CancellationException` is rethrown without suppression.

### Test Case 5: Tier 1 Targeted Unit Test Suite (`TST-MAP-063.5`)
* **Scope**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.segments.*" --tests "com.atrainingtracker.trainingtracker.ui.segments.segmentlist.*" --tests "com.atrainingtracker.trainingtracker.build.*"`. Assert 100% pass rate.

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `[TST-MAP-063.1]` | Unit | `SegmentsRepository.syncSegmentsAsync` | `REQ-MAP-061.1` | Specified |
| `[TST-MAP-063.2]` | Unit | `SegmentsRepository.syncStarredSegments` | `REQ-MAP-061.3` | Specified |
| `[TST-MAP-063.3]` | Unit | `SegmentListViewModel.onRefresh` | `REQ-MAP-061.2` | Specified |
| `[TST-MAP-063.4]` | Unit | `CancellationException` rethrow | `REQ-MAP-061.1`, `REQ-MAP-061.2` | Specified |
| `[TST-MAP-063.5]` | Regression | Tier 1 package test suite | `REQ-PRO-001` | Specified |
