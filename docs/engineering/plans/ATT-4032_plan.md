# Stage 3: Implementation Plan - ATT-4032: Fix low-severity tracking robustness issues

**Ticket**: [ATT-4032](https://rainerblind.atlassian.net/browse/ATT-4032)  
**Parent Epic**: None | **Target Release**: `4.9.40` | **Active Sprint**: `2026-43.6` | **Branch**: `feature/ATT-4032`  
**Requirement Mapping**: `REQ-TRK-039`  
**Test Mapping**: `TST-TRK-039`  
**Author**: AI Agent 1 (Implementer) | **Date**: 2026-10-22  

---

## 1. Problem Description & Background
Independent whole-app code review identified low-severity tracking robustness issues: division by zero in `BANALService.newLap`, unchecked list indexing in `getSportTypeId`, per-second SQLite queries in `MyDevice.getName()` and `TrackerService.tracker` on the scheduler thread, status bar notification resurrection after stop in `TrainingApplication.updateTimeAndDistanceToNotification`, un-rate-limited error log flooding in `tracker` and `ClockTimeTask`, timer drift in `ClockDevice`, cursor leak in `DeviceManager.createPairedRemoteDevices`, accidental receiver re-registration in `AltitudeFromPressureDevice.shutDown()`, and leaked coroutine scope in `BatterySaverController.release()`.

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-TRK-039` (`REQ-TRK-039.1` – `REQ-TRK-039.5`)
  * `REQ-TRK-039.1`: Defensive numerics (`lapSpeed = lapTime_s > 0 ? (lapDistance / lapTime_s) : 0.0`) and non-empty list guard in `BANALService.java`.
  * `REQ-TRK-039.2`: In-memory caching for device names in `MyDevice.java` and sport UI names in `TrackerService.java`.
  * `REQ-TRK-039.3`: Notification lifecycle gating (`isTracking() || isPaused()`) in `TrainingApplication.java`.
  * `REQ-TRK-039.4`: Rate-limited error logging (burst 3, then every 60th) and monotonic clock anchoring in `ClockDevice.java` and `TrackerService.java`.
  * `REQ-TRK-039.5`: Try-with-resources cursor safety in `DeviceManager.java`, removal of bogus receiver registration in `AltitudeFromPressureDevice.java`, and coroutine scope cancellation in `BatterySaverController.kt`.
* **Test Mapping**: `TST-TRK-039` (`TST-TRK-039.1` – `TST-TRK-039.5`).

---

## 3. System Invariants & Preserved Behavior
1. **Tracking & Metric Integrity**: Distance, speed, lap, and sensor calculations continue with zero regression.
2. **Notification & Lifecycle Safety**: Foreground service lifecycle and notification state respect user start/stop transitions.
3. **Thread Dispatcher Affinity**: Background database and sensor processing remain on designated background executors.
4. **Clean-Room Test Suite Pass Rate**: Full test suite (`./gradlew testDebugUnitTest`) achieves 100% pass rate.

---

## 4. Proposed Architectural Changes
* **Component 1 (`BANALService.java` & `MyDevice.java`)**: Guard `lapSpeed` calculation against `lapTime_s <= 0`; guard `sportTypeIds` access with fallback; cache `mCachedDeviceName` in `MyDevice`.
* **Component 2 (`TrackerService.java` & `ClockDevice.java`)**: Cache active sport UI name in `TrackerService`; reuse burst-then-interval rate limiting in `tracker` loop and `ClockTimeTask`; anchor `ClockDevice` timer ticks to monotonic elapsed time.
* **Component 3 (`TrainingApplication.java`)**: Add active/paused tracking mode guard in `updateTimeAndDistanceToNotification`.
* **Component 4 (`DeviceManager.java`, `AltitudeFromPressureDevice.java`, `BatterySaverController.kt`)**: Wrap `createPairedRemoteDevices` cursor in try-with-resources; delete erroneous `registerReceiver` in `AltitudeFromPressureDevice.shutDown()`; cancel `scope` in `BatterySaverController.release()` and clamp brightness.
* **UI Consistency**: No visual UI changes.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)
### Step 1: BANALService & MyDevice Hardening
* In `BANALService.java`: Guard `lapSpeed = lapTime_s > 0 ? (lapDistance / lapTime_s) : 0.0` in `newLap(boolean)`; guard `sportTypeIds != null && !sportTypeIds.isEmpty()` in `getSportTypeId(double)`.
* In `MyDevice.java`: Add `mCachedDeviceName` initialized lazily/on resolution and invalidated on rename.

### Step 2: TrackerService & ClockDevice Resilience & Rate Limiting
* In `TrackerService.java`: Cache `mCachedSportTypeName` keyed by sport ID; apply `shouldLogSampleInsertFailure` policy to `tracker` runnable `catch (Throwable)`.
* In `ClockDevice.java`: Apply rate-limited error logging in `ClockTimeTask`; anchor active time to `SystemClock.elapsedRealtime()`.

### Step 3: TrainingApplication Notification Stop Guard
* In `TrainingApplication.java`: In `updateTimeAndDistanceToNotification`, check `if (!isTracking() && !isPaused()) return;` before building or posting the notification.

### Step 4: DeviceManager, Altitude Device & BatterySaver Cleanup
* In `DeviceManager.java`: Wrap `Cursor cursor = db.query(...)` in try-with-resources.
* In `AltitudeFromPressureDevice.java`: Remove line calling `ContextCompat.registerReceiver` in `shutDown()`.
* In `BatterySaverController.kt`: In `release()`, invoke `scope.cancel()`; in `getSystemBrightness()`, clamp and safely fallback.

### Step 5: Unit & Contract Tests
* Author targeted unit tests in `app/src/test/java/com/atrainingtracker/banalservice/TrackingRobustnessTest.kt` verifying numerics, caching, notification suppression, rate-limiting, and lifecycle cleanup.
* Command: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.TrackingRobustnessTest"`

---

## 6. Verification & Rollback Plan
* **Verification**: Targeted tests in `TrackingRobustnessTest`, adjacent suites (`ClockDeviceResilienceTest`, `BatterySaverControllerTest`), requirement governance script, and full clean-room test suite.
* **Rollback**: Atomic git revert on branch `feature/ATT-4032`.
