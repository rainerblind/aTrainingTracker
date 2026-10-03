# Walkthrough - ATT-1366: Eliminate Flawed Auto-Learned Running Mean Altitude

## 1. Executive Summary

Under **ATT-1366** (the second milestone of the altitude cluster: `ATT-1278`, `ATT-1366`, `ATT-919`), the legacy running average altitude mutation algorithm has been deprecated and eliminated from [KnownLocationsDatabaseManager.java](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/database/KnownLocationsDatabaseManager.java).

Previously, whenever a workout was started near a known location, the system updated the stored altitude using:
`((existing.altitude * existing.hitCount) + newRawAltitude) / (existing.hitCount + 1)`

Because incoming raw barometric altitudes are subject to day-to-day weather pressure swings (QNH variations of up to $\pm 20\text{ hPa} \approx \pm 165\text{m}$) and vertical GPS inaccuracy, averaging uncalibrated raw values caused continuous drift rather than convergence, polluting authoritative Copernicus 30m DEM and user-locked reference altitudes.

### Key Deliverables Implemented in Stage 4 (`ATT-1375`):
1. **Preservation of Reference Altitudes**: [KnownLocationsDatabaseManager.java](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/database/KnownLocationsDatabaseManager.java#L285) now strictly preserves reference altitudes (`INTERNET_DEM`, `MANUAL_USER`, and baseline altitudes) on existing locations, updating **only** `hitCount = existing.hitCount + 1`.
2. **Atomic Geofence Upsert**: Implemented `upsertLocationByGeofence()` within explicit SQLite transaction boundaries (`beginTransaction()` / `setTransactionSuccessful()` / `endTransaction()`), eliminating TOCTOU race conditions between concurrent sensor starts and asynchronous DEM lookups.
3. **Session-Level Idempotency**: Added an `AtomicBoolean sHealingDispatched` guard to `healLegacyLocationsAsync()` to prevent duplicate legacy healing sweeps within a single process lifecycle.
4. **Decoupled Cold-Start Initialization**: Modified [AltitudeFromPressureDevice.java](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/banalservice/devices/AltitudeFromPressureDevice.java#L143) to call `learnLocation()` only when an existing location is matched (`myLocation != null`). When no location is found (`myLocation == null`), speculative synchronous insertion of uncalibrated raw barometric altitude is bypassed, delegating discovery to `fetchDemOrFallbackAsync()` and `upsertLocationByGeofence()`.
5. **Comprehensive Automated Verification (`TST-DAT-009`)**: Added unit test coverage for altitude preservation, locked immutability, concurrent thread safety, and sensor startup invariants.

---

## 2. Code Changes Breakdown

### A. [KnownLocationsDatabaseManager.java](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/database/KnownLocationsDatabaseManager.java)
- **`learnLocation()` Refactored**:
  - Replaced floating-point running mean calculation with clean hit count increment:
    ```java
    ContentValues values = new ContentValues();
    values.put(KnownLocationsDbHelper.HIT_COUNT, existing.hitCount + 1);
    updateId(existing.id, values);
    ```
  - Wrapped inside SQLite transaction blocks (`db.beginTransaction()` / `db.setTransactionSuccessful()`).
  - Respects `isLocked` by skipping updates completely when locked.
- **`upsertLocationByGeofence()` Added**:
  - Checks if a record exists within the spatial geofence radius under SQLite transaction locks.
  - Updates unlocked records to authoritative DEM elevation and increments hitCount, or inserts a new authoritative location.
- **`healLegacyLocationsAsync()` Idempotency**:
  - Guarded with `sHealingDispatched.compareAndSet(false, true)`.

### B. [AltitudeFromPressureDevice.java](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/banalservice/devices/AltitudeFromPressureDevice.java)
- **`initPressureSensor()` Decoupling**:
  - Moved `knownLocationsDb.learnLocation(...)` inside the `if (myLocation != null)` branch.
  - Avoids inserting speculative `AUTO_LEARNED` rows with uncalibrated raw barometric pressure before the asynchronous DEM resolution returns.
- **`fetchDemOrFallbackAsync()` Integration**:
  - Switched from `addNewLocation()` to `upsertLocationByGeofence()` to avoid duplicating or conflicting with concurrently created locations.

### C. [KnownLocationsDatabaseManagerTest.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/test/java/com/atrainingtracker/trainingtracker/database/KnownLocationsDatabaseManagerTest.kt)
- **`testLearnLocation_preservesExistingAltitude_andIncrementsHitCount`** (`TST-DAT-009.1`):
  - Injects a 520.0m `INTERNET_DEM` location with `hitCount = 2`.
  - Simulates a 50m barometric weather drop (raw altitude = 470.0m).
  - Asserts `altitude` is **never** written to `ContentValues` and `hitCount` is updated to 3.
- **`testLearnLocation_lockedRecord_strictlyImmutable`** (`TST-DAT-009.2`):
  - Injects a locked record (`is_locked = 1`).
  - Asserts 0 updates and 0 inserts executed.
- **`testConcurrentUpsert_eliminatesTOCTOURace`** (`TST-DAT-009.3`):
  - Concurrently invokes `upsertLocationByGeofence` across multiple threads with `CountDownLatch`.
  - Asserts serialization and zero exceptions.

### D. [AltitudeFromPressureDeviceTest.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/test/java/com/atrainingtracker/banalservice/devices/AltitudeFromPressureDeviceTest.kt)
- **`testOnSensorChanged_whenKnownLocationNotFound_doesNotTriggerCorrection`** (`TST-DAT-009.4`):
  - Asserts `learnLocation()` is **not** called when known location is not found.

---

## 3. Verification & Test Evidence

### A. Full Clean-Room Regression Suite
```bash
./gradlew testDebugUnitTest
```
**Result**: `BUILD SUCCESSFUL in 2m 47s`. 100% of unit test suites passing across all modules.

### B. Requirement Governance & Chesterton's Fence Audit
```bash
python3 tools/verify_requirement_governance.py --text-file docs/engineering/test_specs/ATT-1366_test_spec.md
```
**Result**: `PASS: Requirement Archaeology & Chesterton's Fence Audit successfully verified with all 4 mandatory fields.`

---

## 4. ASPICE Traceability Matrix

| Requirement ID | Verification Test ID | Component | Status |
| :--- | :--- | :--- | :--- |
| `REQ-DAT-007` | `TST-DAT-009.1` | `KnownLocationsDatabaseManager.java` | Verified |
| `REQ-DAT-007` | `TST-DAT-009.2` | `KnownLocationsDatabaseManager.java` | Verified |
| `REQ-DAT-007` | `TST-DAT-009.3` | `KnownLocationsDatabaseManager.java` | Verified |
| `REQ-DAT-007` | `TST-DAT-009.4` | `AltitudeFromPressureDevice.java` | Verified |
| `REQ-DAT-014` | `TST-DAT-008` (Regression) | `KnownLocationsDatabaseManager.java` | Verified |
| `REQ-CON-011` | `TST-CON-002` (Regression) | `AltitudeFromPressureDevice.java` | Verified |
