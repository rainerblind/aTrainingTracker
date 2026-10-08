# Stage 1 Analysis: ATT-2661 - TrainingApplication.startTracking FGS Background Crash

**Ticket**: [ATT-2661](https://atrainingtracker.atlassian.net/browse/ATT-2661)  
**Sub-task**: [ATT-2662](https://atrainingtracker.atlassian.net/browse/ATT-2662) (`[Analysis]`)  
**Parent Epic**: [ATT-235](https://atrainingtracker.atlassian.net/browse/ATT-235) (*No crashs*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `2026-41.3`  
**Branch**: `feature/ATT-2661`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-08  

---

## 1. Problem Statement & Motivation

In production release `4.9.38.1 (263)`, a fatal application crash was captured by Firebase Crashlytics:
* **Crashlytics Issue**: `e8da0510ece240b5986dce3f7efb8ebb`
* **Session Event**: `6AC6D1A40021000117BEAE9C132C0B6B_DNE_0_v2`
* **Crash Trace**:
  ```text
  Fatal Exception: java.lang.RuntimeException: Error receiving broadcast Intent 
      { act=com.atrainingtracker.trainingapplication.REQUEST_START_TRACKING flg=0x400010 pkg=com.atrainingtracker } 
      in com.atrainingtracker.trainingtracker.TrainingApplication$3@f91d52b
         at android.app.LoadedApk$ReceiverDispatcher$Args.lambda$getRunnable$0$LoadedApk$ReceiverDispatcher$Args(LoadedApk.java:1592)
         ...
  Caused by java.lang.IllegalStateException: Not allowed to start service Intent 
      { cmp=com.atrainingtracker/.trainingtracker.tracker.TrackerService (has extras) }: 
      app is in background uid UidRecord{1739747 u0a137 RCVR idle change:uncached procs:1 seq(0,0,0)}
         at android.app.ContextImpl.startServiceCommon(ContextImpl.java:1778)
         at android.app.ContextImpl.startService(ContextImpl.java:1733)
         at android.content.ContextWrapper.startService(ContextWrapper.java:728)
         at com.atrainingtracker.trainingtracker.TrainingApplication.startTracking(TrainingApplication.java:1330)
         at com.atrainingtracker.trainingtracker.TrainingApplication$3.onReceive(TrainingApplication.java:259)
  ```

### Impact & User Expectation
When starting or resuming workout tracking, the athlete expects the workout recording session to begin reliably. If Android OS restricts background service creation at the exact instant the tracking broadcast is processed, the application must handle the restriction gracefully and inform the athlete (e.g. via tap-to-resume notification) without crashing the application process.

---

## 2. Forensic Root Cause Analysis

### 2.1 The Legacy `startService()` API
In `TrainingApplication.java` (line 1443 in current master):
```java
Intent intent = new Intent(this, TrackerService.class);
if (cResumeFromCrash) {
    intent.putExtra(TrackerService.START_TYPE, TrackerService.StartType.RESUME_BY_USER.name());
    cResumeFromCrash = false;
} else {
    intent.putExtra(TrackerService.START_TYPE, TrackerService.StartType.START_NORMAL.name());
}
startService(intent); // <-- Legacy pre-Android 8.0 invocation
```
* `TrackerService` is configured in `AndroidManifest.xml` as a foreground service with `android:foregroundServiceType="location"`, and invokes `startForeground(id, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)` inside its `onStartCommand()`.
* However, `TrainingApplication.startTracking()` calls `Context.startService(intent)` rather than `ContextCompat.startForegroundService(this, intent)`.
* Since Android 8.0 (API 26), calling `Context.startService()` informs the Android system that the caller is attempting to start a **background service**. If the calling process is not currently evaluated as in the foreground, Android immediately aborts execution and throws `java.lang.IllegalStateException: Not allowed to start service: app is in background` before `TrackerService.onStartCommand()` is ever invoked.

### 2.2 Process State & Asynchronous Broadcast Race Condition
The Crashlytics UID trace records:
`uid UidRecord{1739747 u0a137 RCVR idle change:uncached procs:1 seq(0,0,0)}`
* `REQUEST_START_TRACKING` is dispatched via broadcast from `ControlTrackingViewModel` (on user tap) or from `MainActivityWithNavigation.chooseResume()` (during interrupted workout recovery).
* Because broadcasts are queued and dispatched asynchronously through the main looper, there are valid timing windows where the process is evaluated by Android ActivityManager as `RCVR idle` (a background broadcast receiver) rather than an active foreground activity:
  1. The user taps "Start" or "Resume" and immediately turns off the display or presses the home button.
  2. The activity is starting from an interrupted notification (`handleIntent`) but the WindowManager has not completed the transition to `RESUMED` (`TOP`).
  3. A permission dialog or system overlay temporarily obscured the activity.
* When `startTracking()` is invoked in this state, calling `startService()` unconditionally fails.

### 2.3 Unguarded Call Site in `TrainingApplication.java`
While `TrackerService.java` underwent hardening in [ATT-622](https://atrainingtracker.atlassian.net/browse/ATT-622) (adding try-catch around `startForeground()`), `TrainingApplication.java:startTracking()` was left completely unguarded. An unhandled `IllegalStateException` or Android 12+ (API 31+) `ForegroundServiceStartNotAllowedException` crashes the process inside `BroadcastReceiver.onReceive()`.

---

## 3. Chesterton's Fence Requirement Archaeology (`REQ-PRO-022`)

1. **Original Requirement ID & Target**:
   Refines `REQ-STB-002` (*Resilient TrackerService Foreground Service Lifecycle & Crash Protection*) and `REQ-STB-003` (*Interrupted Workout Resumption & Unfinished Workout Recovery*), targeting `TrainingApplication.java:startTracking()`.
2. **Historical Origin & Commit Trace**:
   * `ATT-622`: Commit `6fd19862` guarded `TrackerService.onStartCommand()` against `SecurityException` during `START_STICKY` recreation when background location was missing.
   * `ATT-635`: Commit `595a6769` routed interrupted workout resumption through `REQUEST_START_TRACKING` under the assumption that the initiating Activity was already in the foreground.
3. **Root Reason for Existing Formulation**:
   Previous work focused exclusively on the internal lifecycle of `TrackerService` once running, assuming that any start request coming from `TrainingApplication` would only occur when the app was in the foreground. It overlooked that `TrainingApplication.java` still used the legacy `startService()` call without `ContextCompat.startForegroundService()` and lacked exception shielding.
4. **Preservation of Core Invariants**:
   * `START_STICKY` lifecycle (`REQ-STB-002`) MUST remain intact.
   * `RESUME_BY_USER` and `START_NORMAL` intent contracts (`REQ-STB-003`) MUST NOT be altered.
   * Pebble watchapp integration, paired sensor discovery, and accumulator reset behavior MUST NOT be degraded.
   * 100% test pass rate across the test suite.

---

## 4. User Scope Grounding (`ATT-1250`)

* **In-Scope**:
  1. Replace legacy `startService(intent)` with `ContextCompat.startForegroundService(this, intent)` in `TrainingApplication.startTracking()`.
  2. Guard the service start with localized `try-catch` handling `IllegalStateException` (including `ForegroundServiceStartNotAllowedException`) and `SecurityException`.
  3. In failure cases: log diagnostic details, reset `cTrackingMode = TrackingMode.READY`, broadcast tracking state change, and post `TrackerService.showTrackingInterruptedNotification(this)` to allow safe user resumption when foregrounded.
  4. Unit test verification covering `startTracking` foreground service delegation and exception resilience.
* **Out-of-Scope**:
  1. No changes to SQLite / Room database schemas (`WorkoutSummaries.db`, `WorkoutSamples.db`).
  2. No changes to `TrackerService` 1Hz recording loop or metrics calculation.
  3. No changes to user-facing UI screens or layout themes.

---

## 5. Proposed Architectural Remediation

```
+---------------------------------------------------------------------------------+
|                       TrainingApplication.startTracking()                       |
|                                                                                 |
|  1. Prepare intent with START_NORMAL or RESUME_BY_USER                          |
|  2. Try:                                                                        |
|        ContextCompat.startForegroundService(this, intent)                       |
|        cTrackingMode = TrackingMode.TRACKING                                    |
|        notifyTrackingStateChanged()                                             |
|  3. Catch (IllegalStateException | SecurityException e):                        |
|        Log.e(TAG, "Failed to start TrackerService as foreground service", e)    |
|        cTrackingMode = TrackingMode.READY                                       |
|        notifyTrackingStateChanged()                                             |
|        TrackerService.showTrackingInterruptedNotification(this)                 |
+---------------------------------------------------------------------------------+
```

---

## 6. Invariants & Risk Assessment

1. **Service Promotion SLA**: `ContextCompat.startForegroundService()` requires the target service to call `startForeground()` within 5 seconds. `TrackerService.java:518` immediately calls `performStartForeground()` in `onStartCommand()`, satisfying this constraint.
2. **Zero Crash Invariant**: Any platform restriction (e.g. background execution limits, missing foreground exemptions) is safely caught, logging the event and offering a one-tap notification for the athlete to resume when returning to the app.
3. **Release Target**: In accordance with user decision, this fix will be included in the active sprint (`sprint/2026-41.3`) for target release `V4.9.39` rather than branching a separate hotfix.
