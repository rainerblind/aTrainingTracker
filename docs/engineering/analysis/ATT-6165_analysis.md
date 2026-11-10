# Stage 1 Analysis: ATT-6165 - Catch Unhandled Exceptions in Strava Segment Sync

**Ticket**: [ATT-6165](https://rainerblind.atlassian.net/browse/ATT-6165)  
**Parent Epic**: [ATT-6033](https://rainerblind.atlassian.net/browse/ATT-6033) (*Code Architecture, Deduplication & Clean Code Refactoring*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-46.1`  
**Branch**: `feature/ATT-6165`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-09  

---

## 1. Problem Statement & Motivation
In `SegmentsRepository.kt:204-214`, `syncSegmentsAsync` launches an unmanaged `CoroutineScope(Dispatchers.IO)` with its `try/catch` block commented out, while `syncStarredSegments` rethrows all exceptions (`throw e`, L236). Consequently, network errors, deserialization failures, or Strava API timeouts during initial auto-sync crash the process. Furthermore, in `SegmentListViewModel.kt:167-171`, `onRefresh` calls `viewModelScope.launch { segmentsRepository.syncStarredSegments(sport) }` without `try/catch`, crashing the app upon pull-to-refresh during network outages.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **Independent Peer Review & Verification**:
  * `SegmentsRepository.kt:204-214`: Confirmed. The `try/catch` is commented out (`// try { ... }`), and it creates a new unmanaged `CoroutineScope(Dispatchers.IO)` instead of utilizing the existing `repositoryScope` (`SupervisorJob() + Dispatchers.IO`, L139).
  * `SegmentListViewModel.kt:167-171`: Confirmed. `onRefresh` launches `syncStarredSegments(sport)` in `viewModelScope` with no exception handling.
  * `StravaSegmentsSyncWorker.kt:63-75`: Confirmed. The worker explicitly catches exceptions from `syncStarredSegments` to trigger `Result.retry()`. The rethrow in `syncStarredSegments` (L236) and `finally { _isSyncing.value = false }` MUST remain intact.
  * `CancellationException` Invariant: Exception handlers in coroutines must rethrow `CancellationException` (`if (e is CancellationException) throw e`) to prevent breaking structured cancellation.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  * In `SegmentsRepository.syncSegmentsAsync`: use existing `repositoryScope`, catch `Exception`, rethrow `CancellationException`, log error.
  * In `SegmentListViewModel.onRefresh`: wrap `syncStarredSegments(sport)` in `try/catch (e: Exception)`, rethrow `CancellationException`, log error.
  * In `SegmentsRepository.syncStarredSegments`: retain `throw e` and `finally { _isSyncing.value = false }`.
  * Unit tests: assert exception containment in `syncSegmentsAsync` and `onRefresh`, and exception propagation with `isSyncing = false` in `syncStarredSegments`.
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  * No modifications to `StravaSegmentsSyncWorker` retry logic or worker parameters.
  * No alterations to SQLite database schemas or UI composables.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Net-new refinement `REQ-MAP-061` (*Defensive Strava Segment Synchronization Exception Handling*), refining `REQ-MAP-005` and `REQ-EXP-014`.
* **Historical Origin & Commit Trace**: `ATT-1078` introduced `syncSegmentsAsync` and `syncStarredSegments` with commented-out catch block. `SegmentListViewModel` added in Compose migration without try/catch.
* **Root Reason for Existing Formulation**: `syncStarredSegments` throws so `StravaSegmentsSyncWorker` can return `Result.retry()`. `syncSegmentsAsync` was an asynchronous fire-and-forget helper where error handling was temporarily commented out during development.
* **Preservation of Core Invariants**: Retaining `throw e` in `syncStarredSegments` preserves worker retry; preserving `CancellationException` maintains coroutine lifecycle; `isSyncing` always resets to `false` in `finally`.

---

## 5. Architectural Strategy & High-Level Solution
* In `SegmentsRepository.kt`: Replace `CoroutineScope(Dispatchers.IO).launch` with `repositoryScope.launch`, adding `try { syncStarredSegments(bSportType) } catch (e: Exception) { if (e is CancellationException) throw e; Log.e(TAG, "Async segment sync failed", e) }`.
* In `SegmentListViewModel.kt`: In `onRefresh`, add `try/catch (e: Exception)` guarding `syncStarredSegments(sport)`, rethrowing `CancellationException` and logging other exceptions.
* Add unit tests in `SegmentsRepositorySyncTest` and `SegmentListViewModelTest` asserting non-crashing behavior on network/DB failure.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**:
  1. `syncStarredSegments` continues to rethrow non-cancellation exceptions for `StravaSegmentsSyncWorker`.
  2. `isSyncing` is strictly reset to `false` in `finally`.
  3. Structured coroutine cancellation is preserved (`CancellationException` rethrown).
* **Risk Rating**: **LOW**. Localized exception safety boundaries on async entry points without data schema or API changes.
