# Test Specification & Requirement Synchronization - ATT-1447: Decouple Altimeter Calibration from Workout Start Counting

## 1. Feature / Bug Overview & Test Scope

* **Issue Key**: `ATT-1447` (Parent) / `ATT-1469` (Stage 2: Test-Spec)
* **Parent Issue**: `ATT-1447` (*[Bug] Lieblingssorte: Start ist not the correct word. We count the app opens here.*)
* **Target Version**: `V4.9.38` (Sprint `2026-39.3`)
* **Related Requirements**: `REQ-DAT-015` (*Decoupled Barometric Altimeter Calibration and Authoritative Workout Start Counting*), refining `REQ-DAT-007` (*Known Location Hit Count Tracking*)
* **Related Tests**: `TST-DAT-010` (*Decoupled Altimeter Calibration and Workout Start Counting Verification*)

### Objective
Decouple barometric altimeter calibration from location visit/start frequency counting (`hitCount`). Ensure that sensor warmup in `AltitudeFromPressureDevice.java` operates strictly as a read-only calibration client without mutating SQLite records or incrementing location "Starts" on application launch. Shift authoritative workout start counting exclusively to athlete-initiated workout sessions in `TrackerService.java`, atomically dispatched to `KnownLocationsDatabaseManager.recordWorkoutStart()`.

---

## 2. Requirement Archaeology & Chesterton's Fence Audit

1. **Original Requirement ID & Target**: `REQ-DAT-007` (item 2: *Hit Count Frequency Tracking*) in `docs/requirements.md`, targeting `AltitudeFromPressureDevice.java` and `KnownLocationsDatabaseManager.java`.
2. **Historical Origin & Commit Trace**: Introduced in ticket `ATT-1366` via commit `637eaefb` to track location usage frequency without corrupting established reference altitudes through running averages.
3. **Root Reason for Existing Formulation**: `ATT-1366` placed `learnLocation(..., ExtremaType.START)` inside `initPressureSensor()` under the assumption that sensor warmup coincided with workout recording. However, sensor warmup executes on every application cold-start and cockpit display, resulting in every app launch near a known location falsely incrementing the location's "Starts" badge.
4. **Preservation of Core Invariants**: Reference altitude preservation against barometric drift (`REQ-DAT-007`), spatial geofence matching radius ($200\text{m}$), schema version (V5), locked location immutability (`REQ-DAT-014`), null-safe correction dispatch (`REQ-CON-013`), cold-start baseline protection (`REQ-CON-011`), and altimeter calibration functionality (`setAltitudeCorrection`) remain 100% strictly intact.

---

## 3. Requirement Specification (`REQ-DAT-015`)

### REQ-DAT-015: Decoupled Barometric Altimeter Calibration and Authoritative Workout Start Counting

The system SHALL decouple barometric altimeter calibration from location visit/start frequency counting, ensuring that location hit counts exclusively reflect athlete-initiated workout recording sessions (ATT-1447):

1. **Read-Only Sensor Warmup Calibration (`AltitudeFromPressureDevice.java`)**:
   - During sensor initialization (`initPressureSensor()`), when GPS coordinates (`currentLatLng`) and barometric pressure are available, the system SHALL query `knownLocationsDb.getMyLocation(currentLatLng)` strictly as a read-only lookup to apply `setAltitudeCorrection(myLocation.altitude)` and update `mAltitudeSensor`.
   - The system SHALL NOT invoke `knownLocationsDb.learnLocation(...)` or mutate SQLite database records during sensor warmup or application launches.

2. **Authoritative Workout Start Counting (`KnownLocationsDatabaseManager.java`)**:
   - The system SHALL provide dedicated, atomic workout start tracking methods: `recordWorkoutStart(@NonNull LatLng pos)` and `recordWorkoutStart(@NonNull LatLng pos, @Nullable Double altitude)`.
   - When a workout start is recorded, if `pos` falls within the geofence radius ($200\text{m}$) of an existing known location, the system SHALL increment `hitCount = existing.hitCount + 1` in SQLite within an atomic transaction boundary.
   - If `pos` does not match an existing location within $200\text{m}$, the system SHALL create a new location entry with `hitCount = 1`, resolve its human-readable name via `LocationNameResolver`, and store reference elevation (`altitude`).

3. **Single-Shot Workout Start Trigger (`TrackerService.java`)**:
   - In `TrackerService`, the system SHALL track session state with a single-shot flag (`mWorkoutStartLocationRecorded = false`).
   - When a new workout session starts (`mStartType == StartType.START_NORMAL`), upon acquiring the first valid GPS coordinate (`currentPos != null`) in `sampleAndWriteToDb()`, the system SHALL asynchronously dispatch `recordWorkoutStart(startPos, startAlt)` on the background database executor (`mDbExecutor`) and set `mWorkoutStartLocationRecorded = true`.
   - Resumed workouts (`RESUME_BY_USER`, `RESUME_SERVICE_RECREATION`) SHALL initialize `mWorkoutStartLocationRecorded = true`, ensuring that pausing, resuming, or service recreation never increments start counts.

4. **Historical Data Preservation (Variante A)**:
   - All existing `hitCount` values in `StartLocation2Altitude.db` SHALL be preserved as-is without destructive database resets or migrations.

#### Acceptance Criteria (Given-When-Then)

* **AC-1 (Read-Only Sensor Warmup / App Open Neutrality)**:
  - *Given* an existing known location with `hitCount = 5` and a device located within its 200m geofence,
  - *When* the user launches the application, opens the cockpit, or reviews past workouts without starting a session,
  - *Then* `AltitudeFromPressureDevice` SHALL apply reference altitude calibration (`setAltitudeCorrection`), and `hitCount` in `StartLocation2Altitude.db` SHALL remain strictly 5.
* **AC-2 (Authoritative Workout Start Increment)**:
  - *Given* an existing known location with `hitCount = 5`,
  - *When* the user starts a new workout (`START_NORMAL`) and the first valid GPS position is acquired,
  - *Then* `TrackerService` SHALL trigger `recordWorkoutStart()`, and `hitCount` in `StartLocation2Altitude.db` SHALL increment to 6.
* **AC-3 (Pause / Resume / Re-creation Idempotence)**:
  - *Given* an active workout that is paused and subsequently resumed by the athlete or recreated after service interruption,
  - *When* tracking resumes,
  - *Then* `mWorkoutStartLocationRecorded` SHALL prevent redundant execution, and `hitCount` SHALL NOT increment.
* **AC-4 (Novel Workout Start Discovery)**:
  - *Given* a workout started at a novel coordinate $>200\text{m}$ from any known location,
  - *When* tracking starts,
  - *Then* a new location record SHALL be created in SQLite with `hitCount = 1`, geocoded name, and reference altitude.

#### System Invariants

1. **Altimeter Calibration Calculations**: Barometric offset formula (`mAltitudeCorrection = correctAltitude - currentAltitude`) and broadcast dispatch (`ALTITUDE_CORRECTION_INTENT`) MUST NOT be altered.
2. **Geofence Matching Radius**: Spatial matching threshold ($200.0\text{m}$) MUST NOT be altered.
3. **Database Schema Version**: SQLite schema version remains V5 (`is_locked`, `source`); zero schema migrations required.
4. **Locked Location Protection**: Stored altitude and metadata of locked locations (`is_locked == 1`) MUST NOT be altered upon workout starts.
5. **Existing Data Preservation**: All existing `hitCount` counters and user-assigned names in `StartLocation2Altitude.db` MUST be preserved.

---

## 4. Test Verification Procedures (`TST-DAT-010`)

### TST-DAT-010: Decoupled Altimeter Calibration and Authoritative Workout Start Counting Verification

| Test Step | Target Component | Action / Inputs | Expected Result | Pass Criteria |
| :--- | :--- | :--- | :--- | :--- |
| **TST-DAT-010.1** | `AltitudeFromPressureDeviceTest.kt` | Configure mock `KnownLocationsDatabaseManager` with a known location (`520.0m`). Trigger `initPressureSensor()` with GPS fix and pressure reading. | `getMyLocation()` is called; `setAltitudeCorrection(520.0)` is applied; `learnLocation()` is NEVER invoked. | `verify(exactly = 0) { mockKnownLocationsDbManager.learnLocation(any(), any(), any()) }` passes. |
| **TST-DAT-010.2** | `KnownLocationsDatabaseManagerTest.kt` | Insert location with `hitCount = 3`. Call `recordWorkoutStart(pos, 520.0)`. | Database row increments `hitCount` to 4; stored altitude remains 520.0m. | `existing.hitCount == 4`, `existing.altitude == 520.0`. |
| **TST-DAT-010.3** | `KnownLocationsDatabaseManagerTest.kt` | Insert locked location (`is_locked = 1, altitude = 520.0, hitCount = 2`). Call `recordWorkoutStart(pos, 480.0)`. | `hitCount` increments to 3; stored altitude remains strictly 520.0m. | `existing.hitCount == 3`, `existing.altitude == 520.0`, `isLocked == true`. |
| **TST-DAT-010.4** | `KnownLocationsDatabaseManagerTest.kt` | Call `recordWorkoutStart(novelPos, 350.0)` where `novelPos` is $> 200\text{m}$ from all known locations. | New location record created with `hitCount = 1`, geocoded name, and reference elevation. | `newLocation.hitCount == 1`, `newLocation.name.isNotEmpty()`. |
| **TST-DAT-010.5** | `KnownLocationsDatabaseManagerTest.kt` | Call `recordWorkoutStart(null, 500.0)`. | Method returns immediately without null pointer exception or database corruption. | Zero exceptions thrown. |
| **TST-DAT-010.6** | `TrackerServiceWorkoutStartTest.kt` | Simulate `TrackerService` start with `mStartType == START_NORMAL`. Inject initial GPS coordinate. | `recordWorkoutStart` is executed exactly once on `mDbExecutor`. Subsequent GPS coordinates do not re-trigger. | Exactly 1 invocation of `recordWorkoutStart`. |
| **TST-DAT-010.7** | `TrackerServiceWorkoutStartTest.kt` | Simulate `TrackerService` with `mStartType == RESUME_BY_USER` or `RESUME_SERVICE_RECREATION`. Inject GPS coordinate. | `recordWorkoutStart` is NEVER invoked. | Exactly 0 invocations of `recordWorkoutStart`. |
| **TST-DAT-010.8** | Physical Pixel 10 Device | Open app, navigate around "Lieblingsorte", check start count for home location, force stop and restart app 3 times. | Start count remains strictly unchanged across app launches. | On-device verified neutrality. |
| **TST-DAT-010.9** | Physical Pixel 10 Device | Start an actual workout session from the home location. Wait for GPS fix, track 30 seconds, end workout. | Start count for home location increments by exactly 1. | On-device verified start counting. |
| **TST-DAT-010.10** | Full Repository | Execute full clean-room unit regression suite (`./gradlew testDebugUnitTest`). | All unit test suites pass with 0 failures, 0 errors, and 0 regressions. | Build Success. |

---

## 5. ASPICE Traceability Matrix

| Requirement ID | Test Specification ID | Verification Method | Status |
| :--- | :--- | :--- | :--- |
| `REQ-DAT-015` (New) | `TST-DAT-010` | Automated JUnit / MockK (`AltitudeFromPressureDeviceTest.kt`, `KnownLocationsDatabaseManagerTest.kt`, `TrackerServiceWorkoutStartTest.kt`) + On-Device | Specified |
| `REQ-DAT-007` (Refined) | `TST-DAT-009`, `TST-DAT-010` | Automated JUnit / MockK | Verified / Specified |
| `REQ-DAT-014` | `TST-DAT-008` | Automated JUnit | Verified |
| `REQ-CON-011` | `TST-CON-002` | Automated JUnit | Verified |
| `REQ-CON-013` | `TST-CON-004` | Automated JUnit | Verified |
