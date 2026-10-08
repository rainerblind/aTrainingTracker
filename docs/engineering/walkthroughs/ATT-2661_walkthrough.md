# Stage 5: Verification Walkthrough - ATT-2661: TrainingApplication.startTracking FGS Background Crash

**Ticket**: [ATT-2661](https://atrainingtracker.atlassian.net/browse/ATT-2661)  
**Sub-task**: [ATT-2666](https://atrainingtracker.atlassian.net/browse/ATT-2666) (`[Test] TrainingApplication.startTracking`)  
**Parent Epic**: [ATT-235](https://atrainingtracker.atlassian.net/browse/ATT-235) (*No crashs*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `2026-41.3`  
**Requirement Mapping**: `REQ-STB-014`  
**Test Mapping**: `TST-STB-014`  
**Branch**: `feature/ATT-2661`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-08  

---

## 1. Executive Summary

This walkthrough document verifies the complete resolution of the Foreground Service background start crash in `TrainingApplication.startTracking` for [ATT-2661](https://atrainingtracker.atlassian.net/browse/ATT-2661), fulfilling requirement `REQ-STB-014` and test specification `TST-STB-014`.

### Problem Statement & Forensic Root Cause
In production release `4.9.38.1 (263)`, Crashlytics issue `e8da0510ece240b5986dce3f7efb8ebb` occurred on Android 8.0:
```
Fatal Exception: java.lang.RuntimeException: Error receiving broadcast Intent { act=com.atrainingtracker.trainingapplication.REQUEST_START_TRACKING ... } in TrainingApplication$3
Caused by: java.lang.IllegalStateException: Not allowed to start service Intent { cmp=com.atrainingtracker/.trainingtracker.tracker.TrackerService (has extras) }: app is in background uid UidRecord{... RCVR idle}
```
The crash occurred when `TrainingApplication.java:1443` executed `startService(intent)` while the calling process was in background state (`RCVR idle`). On Android 8.0+ (API 26+), background service startup is strictly forbidden. Furthermore, on Android 12+ (API 31+), `ForegroundServiceStartNotAllowedException` is thrown if foreground service starts are disallowed from the background.

While `TrackerService.onStartCommand()` already handled system restarts (`intent == null`, `REQ-STB-002`), the caller site in `TrainingApplication.java` lacked both `ContextCompat.startForegroundService()` delegation and defensive exception shielding.

### Remediation Architecture
1. **Foreground Service Launch Delegation**:
   - In `TrainingApplication.startTracking()`, replaced legacy `startService(intent)` with `ContextCompat.startForegroundService(this, intent)`.
2. **Defensive Launch Exception Shielding**:
   - Encapsulated `startForegroundService` inside a `try ... catch (IllegalStateException | SecurityException e)` block.
3. **Graceful State Reset & User Alert**:
   - In case of background start rejections (`IllegalStateException`) or missing permissions (`SecurityException`), `cTrackingMode` is reset to `TrackingMode.READY`, observers are notified via `notifyTrackingStateChanged()`, and `TrackerService.showTrackingInterruptedNotification(this)` is displayed so the athlete can tap and resume tracking once the app returns to the foreground (`REQ-STB-003`).
4. **Context-Parametrized Notification Helper**:
   - Exposed `public static void showTrackingInterruptedNotification(Context context)` in `TrackerService.java`.

---

## 2. Verification Matrix

| Acceptance Criterion | Verification Method | Status | Evidence |
| :--- | :--- | :--- | :--- |
| **AC-1: Foreground Service Startup Delegation** | [TrainingApplicationTrackingTest.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/test/java/com/atrainingtracker/trainingtracker/TrainingApplicationTrackingTest.kt) | **PASSED** | Verifies `startTracking()` delegates to `ContextCompat.startForegroundService()` with `START_NORMAL` extra and sets mode to `TRACKING`. |
| **AC-2: Interrupted Workout Resumption Flag Handling** | [TrainingApplicationTrackingTest.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/test/java/com/atrainingtracker/trainingtracker/TrainingApplicationTrackingTest.kt) | **PASSED** | Verifies `cResumeFromCrash = true` passes `RESUME_BY_USER` extra and resets flag cleanly. |
| **AC-3: Background Launch Restriction Shielding** | [TrainingApplicationTrackingTest.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/test/java/com/atrainingtracker/trainingtracker/TrainingApplicationTrackingTest.kt) | **PASSED** | Verifies `IllegalStateException` is caught without crashing, `cTrackingMode` resets to `READY`, and `TrackerService.showTrackingInterruptedNotification()` is displayed. |
| **AC-4: SecurityException Permission Shielding** | [TrainingApplicationTrackingTest.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/test/java/com/atrainingtracker/trainingtracker/TrainingApplicationTrackingTest.kt) | **PASSED** | Verifies `SecurityException` is caught cleanly, logged, and tracking mode resets to `READY`. |
| **AC-5: Living Documentation Alignment** | [docs/requirements.md](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/requirements.md), [docs/tests.md](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/tests.md) | **PASSED** | `REQ-STB-014` and `TST-STB-014` updated to `Verified`. |
| **AC-6: Full Clean-Room Test Suite Regression** | `./gradlew testDebugUnitTest` | **PASSED** | 100% pass rate across entire unit test suite with zero failures. |

---

## 3. Code Modifications

### 1. `TrainingApplication.java`
```java
        Intent intent = new Intent(this, TrackerService.class);
        if (cResumeFromCrash) {
            intent.putExtra(TrackerService.START_TYPE, TrackerService.StartType.RESUME_BY_USER.name());
            cResumeFromCrash = false;
        } else {
            intent.putExtra(TrackerService.START_TYPE, TrackerService.StartType.START_NORMAL.name());
        }
        try {
            ContextCompat.startForegroundService(this, intent);
            startPebbleWatchapp();

            cTrackingMode = TrackingMode.TRACKING;
            notifyTrackingStateChanged();
        } catch (IllegalStateException | SecurityException e) {
            Log.e(TAG, "Failed to start TrackerService: app in background or permission denied: " + e.getMessage(), e);
            cTrackingMode = TrackingMode.READY;
            notifyTrackingStateChanged();
            TrackerService.showTrackingInterruptedNotification(this);
        }
```

### 2. `TrackerService.java`
```java
    public static void showTrackingInterruptedNotification(Context context) {
        if (context == null) {
            return;
        }
        Intent resumeIntent = new Intent(context, MainActivityWithNavigation.class);
        resumeIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        resumeIntent.putExtra(MainActivityWithNavigation.SELECTED_FRAGMENT, MainActivityWithNavigation.SelectedFragment.START_OR_TRACKING.name());
        resumeIntent.putExtra(MainActivityWithNavigation.EXTRA_RESUME_INTERRUPTED_WORKOUT, true);

        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, resumeIntent, flags);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, TrainingApplication.NOTIFICATION_CHANNEL__TRACKING_2)
                .setSmallIcon(R.drawable.logo)
                .setContentTitle(context.getString(R.string.tracking_interrupted_notification_title))
                .setContentText(context.getString(R.string.tracking_interrupted_notification_text))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true);

        try {
            NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);
            if (ActivityCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
                    || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                notificationManager.notify(TRACKING_INTERRUPTED_NOTIFICATION_ID, builder.build());
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to show tracking interrupted notification: " + e.getMessage(), e);
        }
    }

    protected void showTrackingInterruptedNotification() {
        showTrackingInterruptedNotification(this);
    }
```
