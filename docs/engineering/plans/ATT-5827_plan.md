# Stage 3: Implementation Plan - ATT-5827: Eliminate 1s ScheduledThreadPoolExecutor timing race in TrackerServiceResumeTest

**Ticket**: [ATT-5827](https://rainerblind.atlassian.net/browse/ATT-5827)  
**Sub-task**: Pending (`[Impl-Plan]`)  
**Parent Epic**: None (Standalone)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-45.11`  
**Requirement Mapping**: `REQ-TRK-026` (`REQ-TRK-026.3`)  
**Test Mapping**: `TST-TRK-018` (`TST-TRK-018.2a`, `TST-TRK-018.2b`, `TST-TRK-018.3`)  
**Branch**: `feature/ATT-5827`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-07  

---

## 1. Problem Description & Background
In `TrackerServiceResumeTest.kt`, `onStartCommand()` schedules a periodic 1 Hz background sampler on `mScheduler`. When `BANALService` connects asynchronously, `mBanalService` is populated. Under runner load, the background scheduler runs `sampleAndWriteToDb()` concurrently with the test's manual write call, causing `verify(exactly = 1) { mockSamplesDb.setTransactionSuccessful() }` to fail with "2 matching calls found".

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-TRK-026` / `REQ-TRK-026.3` (*Asynchronous Resume Readiness Barrier & Deterministic Scheduler Cancellation*)
* **Test Mapping**: `TST-TRK-018` (`TST-TRK-018.2a`, `TST-TRK-018.2b`, `TST-TRK-018.3`)

---

## 3. System Invariants & Preserved Behavior
1. **Production Scheduling Invariance**: Production 1 Hz sampling during active tracking and scheduler lifecycle in `TrackerService` remain 100% untouched.
2. **Strict Transaction Assertion**: `verify(exactly = 1)` remains strict on both `mockSamplesDb` and `mockDb` without relaxed matchers.
3. **DRY Teardown Paths**: Inline `mTrackerHandle` cancellation in `TrackerService` is centralized into a single reusable helper.
4. **Code Lock Enforced**: Production code edits remain locked until Gate 3 audit passes.

---

## 4. Proposed Architectural Changes
* **`TrackerService.java` (Domain Service)**:
  * Introduce `@VisibleForTesting void cancelTrackerHandle()` encapsulating `mTrackerHandle.cancel(true)` and `mTrackerHandle = null`.
  * Replace 4 duplicate inline cancellation blocks in `handleTrackingAborted()`, FGS exception catch, `handleActionStop()`, and `onDestroy()`.
* **`TrackerServiceResumeTest.kt` (Test Suite)**:
  * In `testDeferredResumeServiceRecreation_...`, invoke `service.cancelTrackerHandle()` after `onStartCommand()` before connecting BANAL.
  * In `testDeferredResumeByUser_...`, invoke `service.cancelTrackerHandle()` after `onStartCommand()` to prevent thread leakage.
* **UI Consistency**: No UI changes.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Encapsulate `mTrackerHandle` Cancellation in `TrackerService.java`
* Files: `app/src/main/java/com/atrainingtracker/trainingtracker/tracker/TrackerService.java`
* Add `@VisibleForTesting void cancelTrackerHandle()`.
* Refactor lines ~855, ~940, ~1007, and ~1195 to call `cancelTrackerHandle()`.

### Step 2: Halt Periodic Background Sampler in `TrackerServiceResumeTest.kt`
* Files: `app/src/test/java/com/atrainingtracker/trainingtracker/tracker/TrackerServiceResumeTest.kt`
* Call `service.cancelTrackerHandle()` post-`onStartCommand()` in `testDeferredResumeServiceRecreation_...` and `testDeferredResumeByUser_...`.

### Step 3: Targeted Unit & Regression Test Verification
* Execute:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.tracker.TrackerServiceResumeTest"
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.tracker.*"
  ```

---

## 6. Verification & Rollback Plan
* **Verification**: Verify 100% pass across all `TrackerService` unit tests with zero timing races.
* **Rollback**: `git checkout -- .` on `feature/ATT-5827` cleanly restores pre-implementation state.
