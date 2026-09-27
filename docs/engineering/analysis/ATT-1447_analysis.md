# Architectural Analysis - ATT-1447: [Lieblingsorte] Decouple altimeter calibration from workout start counting

## 1. Context & Executive Summary

* **Issue Key**: `ATT-1447`
* **Sub-tasks**: `ATT-1468` (Stage 1: Analysis [In Bearbeitung])
* **Parent Issue**: `ATT-1447` (*[Bug] Lieblingssorte: Start ist not the correct word. We count the app opens here.*)
* **Target Version**: `V4.9.38` (Sprint `2026-39.3`)
* **Associated Requirements**: `REQ-DAT-015` (*Decoupled Barometric Altimeter Calibration and Authoritative Workout Start Counting*), refining `REQ-DAT-007` (*Known Location Hit Count Tracking*)
* **Associated Verification**: `TST-DAT-010` (*Decoupled Altimeter Calibration and Workout Start Counting Verification*)
* **Branch**: `feature/ATT-1447`

---

## 2. Problem Statement & Root Cause Analysis

### A. Symptom Description
In the application's "Lieblingsorte" (Favorite Locations) manager, locations are tagged with badges indicating frequency of use (e.g. *"15 Starts"*) and can be sorted by *"Starts"*.

However, athletes observe that locations register additional "Starts" simply by opening the application (e.g. while sitting on the couch reviewing past workouts or changing settings in the living room), even when no workout session is ever started or recorded. This introduces a major semantic mismatch between the user-facing metric (*"Starts"*) and what the underlying system actually tracks (*sensor initialization / app opens*).

---

### B. Reproduction & Architectural Trace

#### 1. Sensor Warmup Trigger in `AltitudeFromPressureDevice.java`
When the application opens or when the workout cockpit is displayed, the phone's internal sensor manager initializes device wrappers, including `AltitudeFromPressureDevice`.

Inspecting `AltitudeFromPressureDevice.java` (lines 140–160):
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

    // --- ATT-1366 / REQ-DAT-007: Hit Count Tracking ---
    // Record visit frequency without mutating the established reference altitude
    knownLocationsDb.learnLocation(currentLatLng, mLastRawAltitude, ExtremaType.START);
} else {
    fetchDemOrFallbackAsync(latitude, longitude);
}
```

#### 2. Root Cause Mechanism
1. Whenever GPS coordinates (`SensorType.LATITUDE`, `SensorType.LONGITUDE`) and barometric pressure (`Sensor.TYPE_PRESSURE`) are available during sensor warmup, `initPressureSensor()` executes.
2. If `currentLatLng` falls within the geofence radius (default 200m) of an existing known location, line 155 invokes:
   ```java
   knownLocationsDb.learnLocation(currentLatLng, mLastRawAltitude, ExtremaType.START);
   ```
3. Inside `KnownLocationsDatabaseManager.java` (lines 304–315):
   ```java
   MyLocation existing = getMyLocation(pos);
   if (existing != null) {
       ContentValues values = new ContentValues();
       values.put(KnownLocationsDbHelper.HIT_COUNT, existing.hitCount + 1);
       updateId(existing.id, values);
   }
   ```
4. This increments `hitCount` by 1 directly in SQLite.
5. Consequently, any app launch at home or near a known location that triggers sensor initialization permanently increments the "Start" count of that location without any workout being initiated, recorded, or saved.

---

## 3. Call Site & Architectural Scope

* **Impacted Components**:
  1. `app/src/main/java/com/atrainingtracker/banalservice/devices/AltitudeFromPressureDevice.java`:
     - Retain `getMyLocation(currentLatLng)` to apply `setAltitudeCorrection(myLocation.altitude)` for barometric calibration.
     - **Remove** `knownLocationsDb.learnLocation(...)` call. Altimeter calibration must remain strictly read-only with respect to location start counts.
  2. `app/src/main/java/com/atrainingtracker/trainingtracker/database/KnownLocationsDatabaseManager.java`:
     - Introduce `recordWorkoutStart(@NonNull LatLng pos, @Nullable Double altitude)` and `recordWorkoutStart(@NonNull LatLng pos)` to atomically record a workout start.
     - If `startLatLng` falls within the geofence radius of a known location, increment its `hitCount` by 1.
     - If no location matches, learn a new location with `hitCount = 1`.
  3. `app/src/main/java/com/atrainingtracker/trainingtracker/tracker/TrackerService.java`:
     - Track a state flag `private boolean mWorkoutStartLocationRecorded = false;`.
     - When `mStartType == StartType.START_NORMAL` and the first valid GPS coordinate (`startLatLng`) is acquired in `sampleAndWriteToDb()`, asynchronously dispatch `recordWorkoutStart(startLatLng, currentAlt)`.
     - Ensure resumed workouts (`RESUME_BY_USER`, `RESUME_SERVICE_RECREATION`) mark `mWorkoutStartLocationRecorded = true` to prevent duplicate start counts.

* **Unaffected Components & Layers**:
  - `KnownLocationsActivity`, `KnownLocationsAdapter`, and UI badges remain 100% unchanged.
  - Altimeter calibration calculations (`setAltitudeCorrection`) remain identical.
  - GPS tracking, live maps, sensor filtering, and workout export formats remain completely untouched.

---

## 4. Proposed Architectural Solution

### A. Decouple Sensor Initialization (`AltitudeFromPressureDevice.java`)
In `AltitudeFromPressureDevice.java`:
```java
if (myLocation != null) {
    if (DEBUG) Log.i(TAG, "Location found: " + myLocation.name + " (Reference Alt: " + myLocation.altitude + "m, source: " + myLocation.source + ")");
    setAltitudeCorrection(myLocation.altitude);
    mAltitudeSensor.newValue(myLocation.altitude);

    if (myLocation.source == ElevationSource.LEGACY_RAW && !myLocation.isLocked) {
        healLocationAsync(myLocation);
        knownLocationsDb.healLegacyLocationsAsync();
    }
    // REMOVED: knownLocationsDb.learnLocation(currentLatLng, mLastRawAltitude, ExtremaType.START);
}
```
Opening the app now performs barometric calibration purely as a reader; `hitCount` is never mutated on sensor initialization.

---

### B. Dedicated Workout Start Recording (`KnownLocationsDatabaseManager.java`)
Add explicit, atomic workout start tracking method:
```java
/**
 * ATT-1447 / REQ-DAT-015: Records an athlete-initiated workout start at the given GPS coordinate.
 * If the coordinate falls within the geofence radius of a known location, increments its hitCount by 1.
 * If no known location exists, discovers and creates a new location with hitCount = 1.
 */
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
                if (created != null) {
                    healLocationAsync(created);
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

---

### C. Live Workout Start Trigger in `TrackerService.java`
In `TrackerService.java`:
1. Add field: `private boolean mWorkoutStartLocationRecorded = false;`.
2. When starting a workout (`startType != StartType.START_NORMAL`), set `mWorkoutStartLocationRecorded = true` to prevent resumes from registering redundant starts.
3. In `sampleAndWriteToDb()`:
```java
if (mStartType == StartType.START_NORMAL && !mWorkoutStartLocationRecorded && currentPos != null) {
    mWorkoutStartLocationRecorded = true;
    final LatLng startPos = currentPos;
    SensorData<Number> altData = mBanalService.getBestSensorData(SensorType.ALTITUDE);
    final Double startAlt = (altData != null && altData.getValue() != null) ? altData.getValue().doubleValue() : null;
    mDbExecutor.execute(() -> {
        KnownLocationsDatabaseManager.getInstance(TrackerService.this).recordWorkoutStart(startPos, startAlt);
    });
}
```
Execution is offloaded to `mDbExecutor`, ensuring zero latency or thread blocking on the 1Hz sensor sampling loop.

---

## 5. Invariants & Guardrails

| Invariant | Protection Mechanism |
|:---|:---|
| **Altimeter Calibration Functionality** | `initPressureSensor()` continues to read reference elevations and apply `setAltitudeCorrection()`. Altimeter accuracy is completely unaffected. |
| **Existing Data Preservation (Variante A)** | Existing `hitCount` values in SQLite are preserved as-is. No destructive database wipes or historical migrations. |
| **Thread Safety & Database Integrity** | SQLite transaction boundaries (`beginTransaction()`, `setTransactionSuccessful()`, `endTransaction()`) and `synchronized (this)` guarantee thread safety. Background dispatch on `mDbExecutor` prevents main thread ANRs. |
| **Idempotent Workout Starts** | `mWorkoutStartLocationRecorded` ensures exactly 1 start is recorded per new workout (`START_NORMAL`). Pausing and resuming (`RESUME_BY_USER`, `RESUME_SERVICE_RECREATION`) never increments start counts. |
| **Semantic Truthfulness** | UI displays *"X Starts"* and sorts by *"Starts"*, which now 100% accurately reflects real athlete workouts. |

---

## 6. Risk Rating & Gate 1 Recommendation

* **Risk Rating**: **LOW**
  - Scope is cleanly separated between read-only sensor calibration in `AltitudeFromPressureDevice` and workout session startup in `TrackerService`.
  - Zero database schema changes required (schema remains V5).
* **Audit Recommendation**: **RECOMMEND PASS**
  - Conclusively addresses the root cause of the semantic defect.
  - Guarantees backward compatibility and architectural integrity.
