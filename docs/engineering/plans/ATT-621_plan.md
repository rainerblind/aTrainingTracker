# Implementation Plan - ATT-621: Resilient WorkManager Initialization & Startup Crash Protection

## Problem Statement
In production, Android 14 (`API 34`) devices running certain non-standard, custom, or fragmented firmware builds crash fatally immediately upon application launch (`java.lang.NoSuchMethodError: No virtual method forNamespace(Ljava/lang/String;)Landroid/app/job/JobScheduler; in class Landroid/app/job/JobScheduler;`). 
This fatal exception occurs inside `androidx.work.impl.background.systemjob.JobScheduler34.forNamespace` called automatically during early ContentProvider startup (`androidx.startup.InitializationProvider` -> `WorkManagerInitializer.create()`), before `Application.onCreate()` runs. Because it occurs in early startup, it is completely unhandled and prevents users from opening the app.

## User Review Required

> [!IMPORTANT]
> **Decoupled Startup & Guarded Initialization**:
> 1. Automatic execution of `WorkManagerInitializer` during the ContentProvider attachment phase is removed in `AndroidManifest.xml`.
> 2. `WorkManager` is initialized manually in `TrainingApplication.onCreate()` inside a guarded `try ... catch (Throwable)` block. If a device has a malformed `JobScheduler`, the initialization error is logged safely to Crashlytics/logcat and the app launches smoothly without crashing.
> 3. Downstream callers (`ExportManager.java`, `BackupWorker.kt`) are guarded so that if `WorkManager` is unavailable on affected devices, background exports/backups log an error rather than throwing an unhandled `IllegalStateException`.

> [!NOTE]
> **System Invariants Maintained**:
> - Standard compliant Android devices continue to have 100% functional background tasks (Strava/Dropbox uploads, database backups).
> - Any other initializers managed by `androidx.startup` remain unaffected.
> - App startup latency is not impacted.

---

## Requirement & Test Mapping

| Requirement ID | Description | Component(s) | Test ID | Jira Sub-task |
|:---|:---|:---|:---|:---|
| **REQ-STB-001** | **Resilient WorkManager Initialization & Startup Protection.** Decouple `WorkManagerInitializer` from AndroidX Startup, manually initialize `WorkManager` with `Throwable` guarding in `TrainingApplication.onCreate()`, and protect downstream callers against uninitialized WorkManager state. | `AndroidManifest.xml`, `TrainingApplication.java`, `ExportManager.java`, `BackupWorker.kt` | **TST-STB-001** | Current Plan |

---

## Proposed Changes

### Component: `manifest` (`app/src/main/`)

#### [MODIFY] [AndroidManifest.xml](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/AndroidManifest.xml)
- Add `<provider>` tag for `androidx.startup.InitializationProvider` with `tools:node="merge"`.
- Within the provider, specify `<meta-data android:name="androidx.work.WorkManagerInitializer" android:value="androidx.startup" tools:node="remove" />`.
- This halts automatic WorkManager execution during early ContentProvider setup.

---

### Component: `app_lifecycle` (`com.atrainingtracker.trainingtracker`)

#### [MODIFY] [TrainingApplication.java](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/TrainingApplication.java)
- In `onCreate()`:
  - Add manual WorkManager initialization:
    ```java
    try {
        WorkManager.initialize(this, new androidx.work.Configuration.Builder().build());
    } catch (Throwable t) {
        Log.e(TAG, "WorkManager failed to initialize (non-standard platform JobScheduler)", t);
        try {
            com.google.firebase.crashlytics.FirebaseCrashlytics.getInstance().recordException(t);
        } catch (Throwable ignored) {}
    }
    ```
  - Provide a safe helper method `public static boolean isWorkManagerAvailable()` or wrap access to check if WorkManager is ready.

---

### Component: `workers` (`com.atrainingtracker.trainingtracker.exporter`, `migration`)

#### [MODIFY] [ExportManager.java](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/exporter/ExportManager.java)
- In `exportWorkoutTo(...)`:
  - Wrap `WorkManager.getInstance(mContext)` and worker enqueueing in `try ... catch (Throwable t)`.
  - If WorkManager is unavailable on the device, log the error and notify user/record status without crashing.

#### [MODIFY] [BackupWorker.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/migration/BackupWorker.kt)
- In `enqueue(...)` and `cancel(...)`:
  - Wrap `WorkManager.getInstance(context)` calls in `try ... catch (t: Throwable)`.
  - Log warning if WorkManager is unavailable on the device.

---

### Component: `tests` (`app/src/test/java/`)

#### [NEW] [WorkManagerResilienceTest.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/test/java/com/atrainingtracker/trainingtracker/util/WorkManagerResilienceTest.kt)
- Create unit test verifying:
  1. Safe handling when `WorkManager.getInstance` throws `IllegalStateException`.
  2. Safe error logging and non-crash behavior during worker dispatch.

---

## Verification Plan

### Automated Tests
* Execute new resilience test:
  ```bash
  ./gradlew testDebugUnitTest --tests com.atrainingtracker.trainingtracker.util.WorkManagerResilienceTest
  ```
* Execute full project regression suite:
  ```bash
  ./gradlew testDebugUnitTest
  ```

### Manual Verification
1. Verify merged manifest (`app/build/intermediates/merged_manifest/debug/processDebugMainManifest/AndroidManifest.xml`) does not contain `WorkManagerInitializer`.
2. Inspect logcat on app startup to verify clean startup.
