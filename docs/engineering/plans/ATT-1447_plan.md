# Implementation Plan - ATT-1447: [Lieblingsorte] Decouple altimeter calibration from workout start counting

**Parent Ticket**: [ATT-1447](https://rainerblind.atlassian.net/browse/ATT-1447)  
**Parent Epic**: [ATT-235](https://rainerblind.atlassian.net/browse/ATT-235) (*Sensor Calibration & Data Integrity*)  
**Sub-task**: [ATT-1470](https://rainerblind.atlassian.net/browse/ATT-1470) (`[Impl-Plan]`)  
**Target Release**: `V4.9.38` (Sprint `2026-39.3`)  
**Requirement ID**: `REQ-DAT-015`  
**Test ID**: `TST-DAT-010`  
**Analysis Reference**: `docs/engineering/analysis/ATT-1447_analysis.md`  
**Test Spec Reference**: `docs/engineering/test_specs/ATT-1447_test_spec.md`  
**Branch**: `feature/ATT-1447`  

---

## 1. Executive Summary & Architectural Scope

The objective of **ATT-1447** is to eliminate false workout start increments in "Lieblingsorte" (Favorite Locations) by decoupling automatic barometric altimeter calibration from workout start counting (`hitCount`).

### Root Cause
In `AltitudeFromPressureDevice.java:155`, `initPressureSensor()` currently invokes:
```java
knownLocationsDb.learnLocation(currentLatLng, mLastRawAltitude, ExtremaType.START);
```
whenever GPS coordinates and barometric pressure are available during sensor warmup. Because sensor warmup executes on every app launch and whenever the tracking cockpit is displayed (e.g. while sitting on the couch reviewing past workouts or changing settings), the location's `hitCount` in `StartLocation2Altitude.db` is permanently incremented. The UI presents this as *"X Starts"* and provides a sort option *"Starts"*, creating a major semantic discrepancy.

### Proposed Architectural Solution
1. **Pure Read-Only Altimeter Calibration (`AltitudeFromPressureDevice.java`)**:
   - Retain `getMyLocation(currentLatLng)` to read the established reference altitude and apply `setAltitudeCorrection(myLocation.altitude)` and `mAltitudeSensor.newValue(myLocation.altitude)`.
   - **Remove** the `knownLocationsDb.learnLocation(...)` call. Opening the app or initializing sensors will NEVER mutate SQLite records or increment start counts.
2. **Dedicated Workout Start Recording (`KnownLocationsDatabaseManager.java`)**:
   - Provide `recordWorkoutStart(@NonNull LatLng pos)` and `recordWorkoutStart(@NonNull LatLng pos, @Nullable Double altitude)`.
   - When called:
     - If `pos` falls within the geofence radius ($200\text{m}$) of an existing location, atomically increment `existing.hitCount + 1`. If `existing.name` is a placeholder, resolve and update the name.
     - If no location matches, create a new entry with `hitCount = 1`, resolve human-readable place name via `LocationNameResolver`, and store reference altitude.
3. **Single-Shot Live Workout Trigger (`TrackerService.java`)**:
   - Add state field: `private boolean mWorkoutStartLocationRecorded = false;` and promote `startType` to `mStartType`.
   - On `START_NORMAL`, upon acquiring the first valid GPS coordinate in `sampleAndWriteToDb()`, set `mWorkoutStartLocationRecorded = true`, capture `currentPos` and current altitude, and asynchronously dispatch `recordWorkoutStart(startPos, startAlt)` on `mDbExecutor`.
   - On resumed workouts (`RESUME_BY_USER`, `RESUME_SERVICE_RECREATION`), initialize `mWorkoutStartLocationRecorded = true` so resumes never count as additional starts.
4. **Historical Data Preservation (Variante A)**:
   - Existing SQLite `hitCount` values are preserved as-is. No destructive data wipes or migrations.

---

## 2. Step-by-Step Implementation Strategy

### Phase 1: Decouple Sensor Warmup in `AltitudeFromPressureDevice.java`
In [AltitudeFromPressureDevice.java](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/banalservice/devices/AltitudeFromPressureDevice.java) (lines 140–160):
1. In `initPressureSensor()`:
   - Retain:
     ```java
     KnownLocationsDatabaseManager knownLocationsDb = KnownLocationsDatabaseManager.getInstance(mContext);
     KnownLocationsDatabaseManager.MyLocation myLocation = knownLocationsDb.getMyLocation(currentLatLng);
     if (myLocation != null) {
         if (DEBUG) Log.i(TAG, "Location found: " + myLocation.name + " (Reference Alt: " + myLocation.altitude + "m, source: " + myLocation.source + ")");
         setAltitudeCorrection(myLocation.altitude);
         mAltitudeSensor.newValue(myLocation.altitude);

         if (myLocation.source == ElevationSource.LEGACY_RAW && !myLocation.isLocked) {
             healLocationAsync(myLocation);
             knownLocationsDb.healLegacyLocationsAsync();
         }
     } else {
         fetchDemOrFallbackAsync(latitude, longitude);
     }
     ```
   - **Delete line 155**:
     ```java
     // REMOVED: knownLocationsDb.learnLocation(currentLatLng, mLastRawAltitude, ExtremaType.START);
     ```

### Phase 2: Add `recordWorkoutStart` in `KnownLocationsDatabaseManager.java`
In [KnownLocationsDatabaseManager.java](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/database/KnownLocationsDatabaseManager.java):
1. Add public methods:
   ```java
   public void recordWorkoutStart(@NonNull LatLng pos) {
       recordWorkoutStart(pos, null);
   }

   public void recordWorkoutStart(@NonNull LatLng pos, @Nullable Double altitude) {
       if (pos == null) return;

       synchronized (this) {
           SQLiteDatabase db = getDatabase();
           db.beginTransaction();
           try {
               MyLocation existing = getMyLocation(pos);
               if (existing != null) {
                   ContentValues values = new ContentValues();
                   values.put(KnownLocationsDbHelper.HIT_COUNT, existing.hitCount + 1);
                   if (com.atrainingtracker.trainingtracker.location.LocationNameResolver.isPlaceholderName(existing.name)) {
                       String resolvedName = com.atrainingtracker.trainingtracker.location.LocationNameResolver.resolveLocationNameBlocking(mContext, pos.latitude, pos.longitude);
                       if (!com.atrainingtracker.trainingtracker.location.LocationNameResolver.isPlaceholderName(resolvedName)) {
                           values.put(KnownLocationsDbHelper.NAME, resolvedName);
                       }
                   }
                   updateId(existing.id, values);
                   if (DEBUG) Log.d(TAG, "Recorded workout start for '" + existing.name + "': hitCount incremented to " + (existing.hitCount + 1));
               } else {
                   String name = com.atrainingtracker.trainingtracker.location.LocationNameResolver.resolveLocationNameBlocking(mContext, pos.latitude, pos.longitude);
                   double altToStore = (altitude != null && !altitude.isNaN()) ? Math.round(altitude) : 0.0;
                   ElevationSource source = (altitude != null && !altitude.isNaN()) ? ElevationSource.AUTO_LEARNED : ElevationSource.LEGACY_RAW;
                   MyLocation created = addNewLocation(name, altToStore, DEFAULT_RADIUS, pos.latitude, pos.longitude, ExtremaType.START, false, source);
                   if (created != null && source == ElevationSource.LEGACY_RAW) {
                       healLegacyLocationsAsync();
                   }
                   if (DEBUG) Log.d(TAG, "Recorded workout start at new location: " + name + " (hitCount = 1)");
               }
               db.setTransactionSuccessful();
           } finally {
               db.endTransaction();
           }
       }
   }
   ```

### Phase 3: Single-Shot Workout Start Trigger in `TrackerService.java`
In [TrackerService.java](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/tracker/TrackerService.java):
1. Add class members:
   ```java
   private StartType mStartType;
   private boolean mWorkoutStartLocationRecorded = false;
   ```
2. In `onStartCommand(Intent intent, int flags, int startId)`:
   - Assign `mStartType = startType;`
   - If `startType != StartType.START_NORMAL`:
     ```java
     mWorkoutStartLocationRecorded = true;
     ```
   - Else:
     ```java
     mWorkoutStartLocationRecorded = false;
     ```
3. In `sampleAndWriteToDb()` (lines ~966):
   - Immediately after resolving `currentPos`:
     ```java
     if (mStartType == StartType.START_NORMAL && !mWorkoutStartLocationRecorded && currentPos != null) {
         mWorkoutStartLocationRecorded = true;
         final LatLng startPos = currentPos;
         SensorData<Number> altData = (mBanalService != null) ? mBanalService.getBestSensorData(SensorType.ALTITUDE) : null;
         final Double startAlt = (altData != null && altData.getValue() != null) ? altData.getValue().doubleValue() : null;
         mDbExecutor.execute(() -> {
             KnownLocationsDatabaseManager.getInstance(TrackerService.this).recordWorkoutStart(startPos, startAlt);
         });
     }
     ```

### Phase 4: Automated Unit Tests
1. **`AltitudeFromPressureDeviceTest.kt`**:
   - Update existing test / add test `testInitPressureSensorWithKnownLocationDoesNotCallLearnLocation`:
     - Provide known location in mock database manager.
     - Trigger `initPressureSensor()`.
     - Verify `setAltitudeCorrection(refAlt)` is called.
     - Verify `verify(exactly = 0) { mockKnownLocationsDbManager.learnLocation(any(), any(), any()) }`.
2. **`KnownLocationsDatabaseManagerTest.kt`**:
   - `testRecordWorkoutStartIncrementsHitCountForExistingLocation`:
     - Insert location with `hitCount = 3`, reference altitude 520.0m.
     - Invoke `recordWorkoutStart(pos, 480.0)`.
     - Query location: verify `hitCount == 4` and stored `altitude == 520.0`.
   - `testRecordWorkoutStartPreservesLockedLocationAltitude`:
     - Insert locked location (`is_locked = 1, altitude = 520.0, hitCount = 2`).
     - Invoke `recordWorkoutStart(pos, 480.0)`.
     - Query location: verify `hitCount == 3`, `altitude == 520.0`, `isLocked == true`.
   - `testRecordWorkoutStartCreatesNewLocationWhenOutsideGeofence`:
     - Invoke `recordWorkoutStart(novelPos, 350.0)`.
     - Query location: verify new location created with `hitCount == 1`.
   - `testRecordWorkoutStartNullCoordinatesSafety`:
     - Invoke `recordWorkoutStart(null, 500.0)`.
     - Verify zero exceptions and zero database writes.
3. **`TrackerServiceWorkoutStartTest.kt`**:
   - Verify single-shot start dispatch on `START_NORMAL`.
   - Verify zero dispatch on `RESUME_BY_USER` and `RESUME_SERVICE_RECREATION`.

### Phase 5: On-Device Verification (Google Pixel 10)
1. Verify Pixel 10 is in Light Mode (`adb shell cmd uimode night` -> `Night mode: no`).
2. Build and install debug APK on Pixel 10.
3. Open "Lieblingsorte" via navigation drawer and record the start count of the current location (e.g. 15).
4. Force-stop the app and re-open it 3 times without starting a workout.
   - Re-open "Lieblingsorte" and confirm start count remains strictly unchanged (15).
5. Start a live workout session from the home location, acquire GPS fix, wait 10 seconds, end workout.
   - Open "Lieblingsorte" and confirm start count incremented to 16.
6. Capture on-device screenshots demonstrating start count behavior.

### Phase 6: Clean-Room Full Suite Regression Execution
Execute `./gradlew testDebugUnitTest` across all modules to verify 100% test pass rate with zero regressions.

---

## 3. Preserved Invariants & Boundary Verification

| Invariant | Protection Mechanism |
|:---|:---|
| **Altimeter Calibration (REQ-CON-013, REQ-CON-011)** | `initPressureSensor()` continues to read reference elevations and apply `setAltitudeCorrection()`. Barometric offset calculation and cold-start baseline protection remain completely untouched. |
| **Existing Data Preservation (Variante A)** | Existing `hitCount` values in SQLite are preserved as-is. No destructive table resets or schema migrations. |
| **Locked Location Immutability (REQ-DAT-014)** | In `recordWorkoutStart()`, `hitCount` increments while stored `altitude` and metadata remain strictly immutable when `is_locked == 1`. |
| **Thread Safety & Database Integrity** | SQLite transaction boundaries (`beginTransaction()`, `setTransactionSuccessful()`, `endTransaction()`) and `synchronized (this)` guarantee ACID compliance. Background dispatch on `mDbExecutor` prevents main-thread ANRs. |
| **Idempotent Workout Starts** | `mWorkoutStartLocationRecorded` ensures exactly 1 start is recorded per new workout session (`START_NORMAL`). Resumed sessions (`RESUME_BY_USER`, `RESUME_SERVICE_RECREATION`) never record extra starts. |
| **Semantic Truthfulness** | The UI displays *"X Starts"* and sorts by *"Starts"*, which now 100% accurately reflects real athlete workouts. |

---

## 4. ASPICE Traceability Matrix

| Requirement ID | Test Specification ID | Target Implementation Files | Status |
|:---|:---|:---|:---|
| `REQ-DAT-015` | `TST-DAT-010` | `AltitudeFromPressureDevice.java`, `KnownLocationsDatabaseManager.java`, `TrackerService.java` | Planned |
| `REQ-DAT-007` | `TST-DAT-009`, `TST-DAT-010` | `KnownLocationsDatabaseManager.java` | Maintained |
| `REQ-DAT-014` | `TST-DAT-008` | `KnownLocationsDatabaseManager.java`, `AltitudeFromPressureDevice.java` | Maintained |
| `REQ-CON-013` | `TST-CON-004` | `AltitudeFromPressureDevice.java` | Maintained |
