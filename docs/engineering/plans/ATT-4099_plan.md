# Stage 3: Implementation Plan - ATT-4099: Keep BANALService bound or snapshot BANAL state until async endWorkout() finalization completes in TrackerService

**Ticket**: [ATT-4099](https://rainerblind.atlassian.net/browse/ATT-4099)  
**Sub-task**: [ATT-4128](https://rainerblind.atlassian.net/browse/ATT-4128) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-431](https://rainerblind.atlassian.net/browse/ATT-431) (*Stability & Resource Leaks*)  
**Target Release**: `None` (Assigned upon Sprint Review completion per Rule 14)  
**Active Sprint**: `Sprint 2026-42.10`  
**Requirement Mapping**: `REQ-TRK-035` (*Deterministic BANAL State Snapshotting and Bound Lifecycle Preservation During Asynchronous Workout Finalization*)  
**Test Mapping**: `TST-TRK-027` (*Deterministic BANAL State Snapshotting and Bound Lifecycle Preservation Verification Suite*)  
**Branch**: `feature/ATT-4099`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-18  

---

## 1. Problem Description & Background

In Sprint `2026-42.9`, `ATT-4006` moved `endWorkout()` off the Android main thread onto `mDbExecutor`. However, during review, Finding 1 identified a critical lifecycle and thread-safety race:
1. When a workout is stopped by the user while running in the background, `TrackerService` may be the sole bound client of `BANALService`.
2. In `TrackerService.onDestroy()`, `safeUnbindBanalService()` (`unbindService(mBanalConnection)`) was called synchronously on the main thread before `finalizationTask` (`endWorkout()`) was submitted to `mDbExecutor`.
3. Consequently, Android immediately initiated `BANALService.onDestroy()`, shutting down `DeviceManager`, `MySensorManager`, and `FilterManager`.
4. Concurrently on `mDbExecutor`, `endWorkout()` attempted to query `mBanalService`:
   - `createNewLap()` read 0 or null for `LAP_NR`, `TIME_LAP`, and `DISTANCE_m_LAP`. Because `lapTime_s <= 0 && lapDistance <= 0.0` evaluated to true, the entire final workout lap was silently dropped from `LapsTable`.
   - `saveAccumulatedSensorTypes()` read an empty sensor set, corrupting recorded sensor capabilities in `WorkoutSummariesDatabaseManager`.
   - `getAccumulatedGCDataString()` read null or an empty string, corrupting GoldenCheetah metadata strings.
   - `getSportTypeId()`, `getDatabaseIdsOfActiveRemoteDevices()`, and `getBSportType()` returned default `UNKNOWN` values and empty device sets, breaking hardware identity determination, automated workout naming (`ATT-1398`), and favorite route clustering (`WorkoutClusterEngine`, `ATT-773`).
5. In addition, when `mScheduler.shutdownNow()` is invoked at stop time, any in-flight 1 Hz tick already executing in `tracker.run()` could proceed through `sampleAndWriteToDb()` without checking an explicit stopping guard, attempting to submit samples or update UI notifications after finalization had started.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-TRK-035` (*Deterministic BANAL State Snapshotting and Bound Lifecycle Preservation During Asynchronous Workout Finalization*)
  * `REQ-TRK-035.1`: Immutable main-thread `BanalFinalizationSnapshot` record/class capturing lap metrics, active devices, sensor accumulations, GC data string, sport type, and user-selected sport type before enqueuing `finalizationTask`.
  * `REQ-TRK-035.2`: In-flight 1 Hz tick suppression: `tracker.run()` and `sampleAndWriteToDb()` check `mIsStopping` and abort immediately.
  * `REQ-TRK-035.3`: Snapshot-driven `endWorkout(@Nullable BanalFinalizationSnapshot snapshot)`: `createNewLap`, `updateActiveSessionDevices`, `saveAccumulatedSensorTypes`, summary metadata, and `finalizeLiveSession` consume the snapshot directly.
  * `REQ-TRK-035.4`: Universal deferred service unbinding in both `handleActionStop()` and `onDestroy()` fallback paths.
* **Test Mapping**: `TST-TRK-027` (`TST-TRK-027.1`, `TST-TRK-027.2`, `TST-TRK-027.3`, `TST-TRK-027.4`, `TST-TRK-027.5`)

---

## 3. System Invariants & Preserved Behavior

1. **Non-Blocking Main Thread UI**: `handleActionStop()` and `onDestroy()` return immediately (`< 5 ms`) without blocking on database I/O.
2. **Dedicated `FINISHED = 1` Transaction First (`REQ-TRK-030`)**: `endWorkout()` commits `FINISHED = 1` in its own isolated transaction before auxiliary metadata writes.
3. **Zero BANALService Leaks**: `unbindService(mBanalConnection)` is guaranteed to be invoked exactly once on the Android main thread after `finalizationTask` completes.
4. **UI Consistency (Rule 23)**: No UI changes are introduced in this backend service lifecycle ticket.
5. **Human Gate & Versioning Invariance (Rule 3 & Rule 14)**: Subtasks transition to `Erledigt` via `freigabe` upon passing Gate audit without `fixVersion`; parent ticket transitions to `Final Review (Human)`.
6. **One Commit per Ticket (Rule 44)**: Changes remain on `feature/ATT-4099` without intermediate commits until squashed into `sprint/2026-42.10` in Stage 5.

---

## 4. Proposed Architectural Changes

### 4.1 Component 1: `TrackerService.java` - `BanalFinalizationSnapshot` Data Class
Create a static, immutable data class inside `TrackerService.java`:
```java
public static class BanalFinalizationSnapshot {
    public final int lapNr;
    public final int lapTime_s;
    public final double lapDistance_m;
    @NonNull public final Set<Long> activeRemoteDeviceIds;
    @NonNull public final Set<SensorType> accumulatedSensorTypes;
    @Nullable public final String accumulatedGCDataString;
    @NonNull public final BSportType bSportType;
    @Nullable public final BSportType userSelectedSportType;
    public final long sportTypeId;

    public BanalFinalizationSnapshot(
            int lapNr,
            int lapTime_s,
            double lapDistance_m,
            @Nullable Set<Long> activeRemoteDeviceIds,
            @Nullable Set<SensorType> accumulatedSensorTypes,
            @Nullable String accumulatedGCDataString,
            @Nullable BSportType bSportType,
            @Nullable BSportType userSelectedSportType,
            long sportTypeId) { ... }

    @NonNull
    public static BanalFinalizationSnapshot capture(
            @Nullable BANALServiceComm banalService,
            @NonNull Context context,
            double avgSpeed,
            boolean avgSpeedCalculable) { ... }
}
```

### 4.2 Component 2: In-Flight Tick Guard
In `TrackerService.java`:
```java
final Runnable tracker = new Runnable() {
    @Override
    public void run() {
        if (mIsStopping) {
            return;
        }
        ...
```
And at the top of `sampleAndWriteToDb(@Nullable final BANALServiceComm banalService)`:
```java
if (mIsStopping || banalService == null) {
    return;
}
```

### 4.3 Component 3: Snapshot Consumption in `endWorkout()`
Refactor `endWorkout()` and its internal helper methods:
1. `public void endWorkout()` delegates to `endWorkout(null)` (live query fallback for backward compatibility).
2. `public void endWorkout(@Nullable BanalFinalizationSnapshot snapshot)`:
   - `createNewLap(snapshot)`: Reads `snapshot.lapNr`, `snapshot.lapTime_s`, `snapshot.lapDistance_m` if snapshot is present; otherwise reads `mBanalService`.
   - `updateActiveSessionDevices(snapshot)`: Populates `mActiveSessionDeviceIds` from `snapshot.activeRemoteDeviceIds`.
   - `summariesDatabaseManager.saveAccumulatedSensorTypes`: Uses `snapshot.accumulatedSensorTypes`.
   - `summaryValues.put(WorkoutSummaries.GC_DATA, snapshot.accumulatedGCDataString)`.
   - `summaryValues.put(WorkoutSummaries.SPORT_ID, snapshot.sportTypeId)` and `bSportType`.
   - `finalizeLiveSession(snapshot)`: Consumes `snapshot.userSelectedSportType`, `snapshot.bSportType`, and `snapshot.activeRemoteDeviceIds` for identity resolution and auto-naming.

### 4.4 Component 4: Unified Main-Thread Snapshot Capture and Deferred Unbinding
In `handleActionStop()`:
```java
// Main thread: capture snapshot synchronously while BANALService is fully alive and bound
final BanalFinalizationSnapshot snapshot = BanalFinalizationSnapshot.capture(
        mBanalService, this, getAverageSpeed(), averageSpeedCalculateable());
...
Runnable finalizationTask = () -> {
    try {
        if (!interrupted) {
            endWorkout(snapshot);
        }
    } ...
```
In `onDestroy()`:
```java
// Remove early safeUnbindBanalService() call
// Capture snapshot synchronously on main thread
final BanalFinalizationSnapshot snapshot = BanalFinalizationSnapshot.capture(
        mBanalService, this, getAverageSpeed(), averageSpeedCalculateable());
...
Runnable finalizationTask = () -> {
    try {
        if (!interrupted) {
            endWorkout(snapshot);
        }
    } finally {
        mBanalService = null;
        releaseWakeLock();
        runOnMainThread(() -> {
            try {
                safeUnbindBanalService();
                performStopForeground();
            } finally {
                finalizationFuture.complete(null);
            }
        });
    }
};
```

---

## 5. Atomic Implementation Steps

### Step 1: Define `BanalFinalizationSnapshot` in `TrackerService.java`
- Implement static data class `BanalFinalizationSnapshot` with fields and static factory `capture()`.
- Add defensive null-handling fallback defaults.

### Step 2: Add In-Flight Tick Suppression
- Insert `if (mIsStopping) return;` check in `tracker.run()`.
- Insert `if (mIsStopping || banalService == null) return;` in `sampleAndWriteToDb()`.

### Step 3: Refactor `endWorkout`, `createNewLap`, `finalizeLiveSession` to Consume Snapshot
- Add overload `endWorkout(@Nullable BanalFinalizationSnapshot snapshot)`.
- Update `createNewLap(@Nullable BanalFinalizationSnapshot snapshot)` to insert final lap from snapshot without querying `mBanalService`.
- Update `saveAccumulatedSensorTypes`, summary metadata, and `finalizeLiveSession` to prefer snapshot values.

### Step 4: Wire Snapshot Capture in `handleActionStop()` and `onDestroy()`
- In `handleActionStop()`: capture `snapshot` on main thread and pass to `endWorkout(snapshot)`.
- In `onDestroy()`: capture `snapshot` on main thread, pass to `endWorkout(snapshot)`, and move `safeUnbindBanalService()` into the main-thread callback inside `finally`.

### Step 5: Unit Tests in `TrackerServiceFinalizationTest.kt`
- Test snapshot capture and fallback defaults (`TST-TRK-027.1`).
- Test in-flight tick suppression on `mIsStopping == true` (`TST-TRK-027.2`).
- Test final lap and sensor persistence when `mBanalService == null` at `endWorkout()` execution time (`TST-TRK-027.3`).
- Test deferred `onDestroy()` service unbinding (`TST-TRK-027.4`).

---

## 6. Targeted Verification Commands

```bash
# Compile and run targeted finalization tests
./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.tracker.TrackerServiceFinalizationTest"

# Verify full test suite
./gradlew testDebugUnitTest
```
