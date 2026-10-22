# Stage 1 Analysis: ATT-4032 - Fix low-severity tracking robustness issues

**Ticket**: [ATT-4032](https://rainerblind.atlassian.net/browse/ATT-4032)  
**Parent Epic**: None | **Target Release**: `4.9.40` | **Active Sprint**: `2026-43.6` | **Branch**: `feature/ATT-4032`  
**Requirement Mapping**: `REQ-TRK-039` (*Tracking Subsystem Robustness, Resource Lifecycle & Rate-Limited Error Telemetry Hygiene*)  
**Author**: AI Agent 1 (Implementer) | **Date**: 2026-10-22  

---

## 1. Problem Statement & Motivation
An independent whole-app code review identified low-severity robustness, concurrency, resource handling, and lifecycle defects across tracking components:
1. **Numeric & List Safety**: `BANALService.newLap` computes `lapDistance / lapTime_s` yielding `NaN`/`Infinity` when `lapTime_s == 0`; `getSportTypeId(avgSpd)` performs unchecked `sportTypeIds.get(0)` throwing `IndexOutOfBoundsException` if empty.
2. **Scheduler Thread DB Overhead**: `MyDevice.getName()` (invoked per-sensor via `SensorData`) and `TrackerService.tracker` (`SportTypeDatabaseManager.getUIName`) perform synchronous SQLite queries on every 1 Hz sampling tick.
3. **Notification Resurrecting After Stop**: `TrainingApplication.updateTimeAndDistanceToNotification` mutates shared `mTrackingAndSearchingNotificationBuilder` without checking if tracking has finished, resurrecting the dismissed status bar notification on late scheduler ticks.
4. **Log Flooding & Clock Drift**: Catch-all blocks in `TrackerService.tracker` and `ClockDevice.ClockTimeTask` flood logs (~3600 `Log.e`/h on persistent failure) instead of rate-limiting; `ClockDevice` timer ticks drift from monotonic elapsed time.
5. **Resource & Lifecycle Bugs**: `DeviceManager.createPairedRemoteDevices` leaks `Cursor` without try-with-resources; `AltitudeFromPressureDevice.shutDown()` accidentally invokes `registerReceiver` right before `unregisterReceiver`; `BatterySaverController` leaks its coroutine scope on release.

---

## 2. Root Cause Analysis & Call Site Audit

| Component / File | Findings & Root Cause | Remediation Strategy | Affected Call Sites |
|---|---|---|---|
| `BANALService.java` | L410: `lapDistance / lapTime_s` divides by zero; L356: `sportTypeIds.get(0)` unchecked. | Guard `lapTime_s > 0 ? (lapDistance / lapTime_s) : 0.0`; check `!sportTypeIds.isEmpty()` or fallback. | `BANALService.java:356,410` |
| `MyDevice.java` & `TrackerService.java` | Per-second SQLite queries in `MyDevice.getName()` (L106 via `SensorData`) and `TrackerService.java:609` (`getUIName`). | Cache `mCachedDeviceName` in `MyDevice`; cache sport UI name in `TrackerService` keyed by sport ID. | `MyDevice.java:106`, `TrackerService.java:609` |
| `TrainingApplication.java` | L1491-1505: `updateTimeAndDistanceToNotification` re-posts notification even if tracking is stopped/ready. | Guard with `if (!isTracking() && !isPaused()) return;` before modifying builder or calling `notify()`. | `TrainingApplication.java:1491-1505` |
| `ClockDevice.java` & `TrackerService.java` | Unbounded `Log.e` in `ClockTimeTask` (L211) & `tracker` (L616); `ClockDevice` timer drift. | Reuse `shouldLogSampleInsertFailure` policy (burst 3, then every 60th); anchor timer to `elapsedRealtime`. | `ClockDevice.java:190-213`, `TrackerService.java:614-617` |
| `DeviceManager.java` | L1069-1108: `Cursor cursor = db.query(...)` closed manually; exception skips `close()`. | Enclose query in `try (Cursor cursor = db.query(...)) { ... }` with null guard. | `DeviceManager.java:1069-1108` |
| `AltitudeFromPressureDevice.java` | L120-121: `shutDown()` accidentally calls `registerReceiver` before `safeUnregisterReceiver`. | Remove redundant `registerReceiver` call in `shutDown()`. | `AltitudeFromPressureDevice.java:120-121` |
| `BatterySaverController.kt` | L30, 234: Coroutine scope never cancelled in `release()`; L174 brightness scaling boundary. | Cancel coroutine scope in `release()`; ensure robust fallback and clamping in `getSystemBrightness()`. | `BatterySaverController.kt:30,174,234` |

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  * Guard `newLap` against `NaN`/`Infinity` lap speed and guard `getSportTypeId` against empty list exceptions.
  * Cache device names and sport UI names to eliminate per-second SQLite reads on the 1 Hz scheduler loop.
  * Prevent notification resurrection when late sampling ticks arrive after tracking has stopped (`Criterion 2`).
  * Rate-limit persistent failure logging in `TrackerService.tracker` and `ClockDevice.ClockTimeTask` and harden clock time tracking.
  * Wrap `DeviceManager.createPairedRemoteDevices` cursor in try-with-resources.
  * Remove accidental `registerReceiver` from `AltitudeFromPressureDevice.shutDown()`.
  * Cancel coroutine scope in `BatterySaverController.release()` and clamp brightness extraction.
  * Add unit tests verifying numeric guards, notification suppression, rate-limiting, and lifecycle cleanup.
* **Out-of-Scope Non-Goals**:
  * Do NOT rewrite `SpeedAndLocationDevice_GoogleFused` to `FusedLocationProviderClient` (large-scale Play Services migration reserved for dedicated ticket).
  * Do NOT perform architectural overhaul of `BANALService` or `DeviceManager` connection engines.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **REQ-TRK-039** (*Tracking Subsystem Robustness, Resource Lifecycle & Rate-Limited Error Telemetry Hygiene*): Net-new requirement extending `REQ-TRK-027` and `REQ-TRK-029`.
* **Chesterton's Fence Invariants**:
  1. `AltitudeFromPressureDevice.java:120`: `git log` reveals `registerReceiver` was accidentally added in commit `2afc198549` during batch migration to `ContextCompat.registerReceiver`. Excision restores correct teardown semantics.
  2. `TrackerService` / `ClockDevice` logging: `REQ-TRK-027` added `catch(Throwable)` guards to prevent loop termination; rate-limiting preserves exception isolation while preventing log buffer exhaustion.
  3. `TrainingApplication` notification: Notification exists to provide live feedback; suppressing updates after `TrackingMode.READY` respects user stop intent.

---

## 5. Architectural Strategy & High-Level Solution
1. **Defensive Numerics**: Return `0.0` when `lapTime_s <= 0`; return fallback sport ID when list is empty.
2. **In-Memory Caching**: Introduce memory caches for device names and active sport UI name, refreshing on updates.
3. **Notification Lifecycle Gating**: Check `isTracking() || isPaused()` in `updateTimeAndDistanceToNotification`.
4. **Rate-Limited Logging & Monotonic Time**: Apply burst-then-interval rate limiting to `tracker` and `ClockTimeTask`; anchor timer ticks to monotonic elapsed time.
5. **Deterministic Teardown & Leak Prevention**: Use try-with-resources in `DeviceManager`; remove bogus receiver registration; cancel coroutine scope in `BatterySaverController`.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**:
  1. Zero regression in tracking accuracy, lap generation, database sample recording, and sensor telemetry.
  2. 100% clean-room test pass rate across existing unit test suites (`./gradlew testDebugUnitTest`).
  3. Background service memory and process priority remain strictly preserved.
* **Risk Rating**: **LOW**. Localized robustness guards, caching, and cleanup without structural changes or schema migrations.
