# Stage 1 Analysis: ATT-5827 - [Bug] Unit Tests (TrackerServiceResumeTest): Eliminate 1s ScheduledThreadPoolExecutor timing race on setTransactionSuccessful verification

**Ticket**: [ATT-5827](https://rainerblind.atlassian.net/browse/ATT-5827)  
**Sub-task**: Pending (`[Analysis]`)  
**Parent Epic**: None (Standalone)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-45.11`  
**Branch**: `feature/ATT-5827`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-07  

---

## 1. Problem Statement & Motivation
In `TrackerServiceResumeTest.kt` (`testDeferredResumeServiceRecreation_whenBanalServiceConnectsLater_restoresTableNameAndWritesSamplesToResumedTable`), `verify(exactly = 1) { mockSamplesDb.setTransactionSuccessful() }` intermittently fails under runner load with `"2 matching calls found, exactly 1 expected"`.
During `onStartCommand()`, `TrackerService` starts a periodic 1 Hz background sampler via `mScheduler.scheduleAtFixedRate(tracker, 0, 1, TimeUnit.SECONDS)`. When `banalConnectionForTesting.onServiceConnected()` binds `mockBanalComm`, `tracker.run()` sees non-null `mBanalService` and triggers `sampleAndWriteToDb(banalService)` in the background if $\ge 1.0\text{ s}$ elapses before or during the test's manual `sampleAndWriteToDb()` call, causing duplicate transactions.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **Root Cause**: `onStartCommand()` schedules `tracker` at fixed 1-second intervals on a real `ScheduledThreadPoolExecutor` (`mScheduler`). At $T=0$, `mBanalService == null`, so the initial tick is a no-op. Once step 2 binds `mockBanalComm`, subsequent 1 Hz ticks execute `sampleAndWriteToDb()` on background thread pool workers. Under CPU contention (CI or parallel Gradle workers), $T \ge 1.0\text{ s}$ is reached before step 4 finishes, executing `sampleAndWriteToDb()` twice (once in background, once via manual invocation).
* **Independent Peer Review & Verification**:
  * Hypothesis Verified: Race condition confirmed. However, the ticket noted `startTracking()` as the initiator; forensic analysis shows `startTracking()` does not exist—scheduling occurs directly inside `onStartCommand()` (`TrackerService.java:925`), and `onServiceConnected()` supplies the non-null `mBanalService` that activates the sampling body (`TrackerService.java:599`).
  * Secondary Call Site Audit: `testDeferredResumeByUser_...` (line 474) also invokes `onStartCommand()` without cancelling `mTrackerHandle`, leaking active background threads until `tearDown()`.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  * Consolidate inline `mTrackerHandle` cancellation into `@VisibleForTesting void cancelTrackerHandle()` in `TrackerService.java` (DRY).
  * Deterministically stop `mTrackerHandle` immediately following `onStartCommand()` in `TrackerServiceResumeTest.kt` before BANAL service connection.
  * Guarantee 100% deterministic single-execution verification (`exactly = 1`) under all runner load conditions.
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  * No modification to production sampling rate (1 Hz), database schema, or production service lifecycle.
  * No alteration of `verify(exactly = 1)` to relaxed matchers (`atLeast = 1`), preserving strict transaction count assertion.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-TRK-026` / `TST-TRK-018` (Vector 2a) in `docs/requirements/req_tracking_cockpit.md` and `docs/tests/tst_tracking_cockpit.md`.
* **Historical Origin & Commit Trace**: Added in `ATT-3980` to verify sample table resolution and asynchronous BANAL binding barrier.
* **Root Reason for Existing Formulation**: `onStartCommand()` was invoked to model the full deferred service startup. Under fast, unloaded execution ($<1\text{ s}$), the background timer never reached its first periodic tick before assertion completion.
* **Preservation of Core Invariants**: Cancelling `mTrackerHandle` in the test halts the unneeded background thread without changing `TrackerService` production concurrency semantics, fully preserving `REQ-TRK-026.3`.

---

## 5. Architectural Strategy & High-Level Solution
1. **`TrackerService.java`**: Extract existing 4 duplicate inline cancellation blocks (`handleTrackingAborted`, FGS exception catch, `handleActionStop`, `onDestroy`) into `@VisibleForTesting void cancelTrackerHandle()`.
2. **`TrackerServiceResumeTest.kt`**:
   - In `testDeferredResumeServiceRecreation_...`, invoke `service.cancelTrackerHandle()` immediately after `onStartCommand()`.
   - In `testDeferredResumeByUser_...`, invoke `service.cancelTrackerHandle()` immediately after `onStartCommand()` to prevent background thread leakage.
   - Retain `verify(exactly = 1)` on both `mockSamplesDb` and `mockDb` transactions.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**:
  1. `REQ-TRK-026` / `TST-TRK-018`: Resumed sample table restoration and single-sample write semantics remain strictly verified.
  2. Production `mScheduler` lifecycle and 1 Hz sampling during active tracking remain completely untouched.
  3. Zero regressions across `TrackerService` test suite (`TrackerServiceResumeTest`, `TrackerServiceResilienceTest`, `TrackerServiceFinalizationTest`).
* **Risk Rating**: **LOW** (Zero production logic change; DRY helper method extraction with targeted unit test determinism fix).
