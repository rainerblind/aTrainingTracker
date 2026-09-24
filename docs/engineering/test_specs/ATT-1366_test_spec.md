# Test Specification & Requirement Synchronization - ATT-1366: Auto Learned Altitude Does Not Work Properly

## 1. Feature / Bug Overview & Test Scope

* **Issue Key**: `ATT-1366` / `ATT-1373`
* **Parent Epic**: `ATT-235` / Sensor Calibration & Data Integrity
* **Target Version**: `V4.9.38` (Sprint `2026-39.2`)
* **Related Requirements**: `REQ-DAT-007` (Synchronized / Re-specified), `REQ-DAT-014`, `REQ-CON-011`, `REQ-CON-013`
* **Related Tests**: `TST-DAT-009` (New Verification Specification)

### Objective
Eliminate the flawed running mean altitude calculation in `KnownLocationsDatabaseManager.learnLocation()`. Ensure that reference elevations (particularly authoritative `INTERNET_DEM` and `MANUAL_USER` entries) are strictly preserved against barometric weather pressure drift, while continuing to track location usage frequency via `hitCount` increments. Ensure atomic serializability under concurrent sensor starts and asynchronous DEM resolution.

---

### Requirement Archaeology & Chesterton's Fence Audit

1. **Original Requirement ID & Target**: `REQ-DAT-007` (*Automated Altitude Reference Discovery*) in `docs/requirements.md`, mapped to `KnownLocationsDatabaseManager.java` and `AltitudeFromPressureDevice.java`.
2. **Historical Origin & Commit Trace**: Introduced in commit `9feb03b3b8792a0b0962574c0713eb16319b2180` (Ticket `ATT-39`, dated 2026-07-27); refined in commit `308f9392` (Ticket `ATT-448`).
3. **Root Reason for Existing Formulation**: Prior to `ATT-1278` (Internet DEM client), the application lacked any external source of absolute ground-truth topographic elevation, attempting to approximate reference altitude by calculating a running average of uncalibrated barometric start altitudes (`mLastRawAltitude`).
4. **Preservation of Core Invariants**: `ATT-1278` (`REQ-DAT-014`) provides authoritative Copernicus 30m DEM ground truth; location discovery, spatial geofence clustering (200m radius), usage frequency tracking (`hitCount`), sensor initialization (`setAltitudeCorrection`), cold-start baseline protection (`REQ-CON-011`), and null-safe correction dispatch (`REQ-CON-013`) remain 100% intact.

---

## 3. Synchronized Requirement Specification (`REQ-DAT-007`)

### REQ-DAT-007: Automated Altitude Reference Discovery & Stable Reference Elevation Preservation
The system SHALL maintain a database of known locations in `StartLocation2Altitude.db` based on workout start points (`ExtremaType.START`) without corrupting them through running averages of barometric pressure:
1. **Zero Altitude Mutation**: In `KnownLocationsDatabaseManager.learnLocation(LatLng pos, Double altitude, ExtremaType type)`, the system SHALL NOT modify the stored `altitude` of an existing location entry within the spatial geofence radius ($200\text{m}$).
2. **Hit Count Frequency Tracking**: When matching an existing known location, the system SHALL increment `hitCount = existing.hitCount + 1` to track location usage frequency and recency.
3. **Authoritative Reference Preservation**: Reference elevations obtained from authoritative digital elevation models (`INTERNET_DEM`), manual user definitions (`MANUAL_USER`), or verified fallbacks SHALL remain pristine and protected against atmospheric weather pressure variations.
4. **Synchronous Insert Protection**: When encountering an uncalibrated or unknown location (`myLocation == null`), `AltitudeFromPressureDevice` SHALL NOT synchronously insert speculative uncalibrated entries using raw barometric pressure (`mLastRawAltitude`). Instead, it SHALL asynchronously request DEM elevation (`REQ-DAT-014`) and fall back cleanly to GPS altitude.
5. **Atomic Geofence Upsert**: Geofence checking and record creation/updating in `KnownLocationsDatabaseManager` SHALL be encapsulated within an explicit SQLite transaction boundary to eliminate TOCTOU race conditions and prevent duplicate spatial rows under concurrent sensor and asynchronous DEM resolution.

#### Acceptance Criteria (Given-When-Then):
* **AC-1 (Zero Altitude Drift on Repeat Visits)**:
  - *Given* an existing known location entry with reference altitude $520.0\text{m}$ and `hitCount = 3`,
  - *When* `learnLocation()` is invoked with coordinates within $200\text{m}$ and an arbitrary uncalibrated pressure altitude (e.g. $480.0\text{m}$),
  - *Then* the stored `altitude` SHALL remain strictly $520.0\text{m}$, and `hitCount` SHALL increment to $4$.
* **AC-2 (Locked Immutability Invariant)**:
  - *Given* a location entry with `is_locked == 1`,
  - *When* `learnLocation()` is invoked,
  - *Then* the stored `altitude`, `hitCount`, and all metadata SHALL remain completely unchanged.
* **AC-3 (Elimination of Uncalibrated Cold-Start Race)**:
  - *Given* an unknown location at workout start (`myLocation == null`),
  - *When* sensor initialization occurs in `AltitudeFromPressureDevice`,
  - *Then* `fetchDemOrFallbackAsync()` SHALL be dispatched without synchronously inserting an uncalibrated `AUTO_LEARNED` record using raw barometric pressure.
* **AC-4 (Atomic Concurrency Serialization)**:
  - *Given* concurrent execution of `learnLocation()` and asynchronous DEM callback resolution across multiple worker threads,
  - *Then* atomic transaction serialization SHALL guarantee that exactly one location row exists in SQLite and no duplicate spatial entries are created.

---

## 4. Test Verification Procedures (`TST-DAT-009`)

### TST-DAT-009: Stable Reference Altitude Preservation, Hit Count Tracking & Concurrency Test

| Test Step | Target Component | Action / Inputs | Expected Result | Pass Criteria |
| :--- | :--- | :--- | :--- | :--- |
| **TST-DAT-009.1** | `KnownLocationsDatabaseManagerTest.kt` | Insert location with `altitude = 520.0m`, `hitCount = 1`, `source = INTERNET_DEM`. Call `learnLocation(pos, 480.0, ExtremaType.START)`. | Database row preserves `altitude = 520.0m` and updates `hitCount = 2`. | `altitude == 520.0`, `hitCount == 2`, `source == INTERNET_DEM`. |
| **TST-DAT-009.2** | `KnownLocationsDatabaseManagerTest.kt` | Set `is_locked = 1`. Call `learnLocation(pos, 600.0, ExtremaType.START)`. | Zero SQLite update or insert calls executed. | Total immutability. |
| **TST-DAT-009.3** | `KnownLocationsDatabaseManagerTest.kt` | Multi-threaded concurrency test with `CountDownLatch`: 2 worker threads trigger simultaneous `learnLocation()` and `upsertLocationByGeofence()` at same coordinates. | Database contains exactly 1 unique row within the 200m radius. Transaction executes atomically without SQLite exceptions. | `count == 1`, no duplicate primary key or spatial row. |
| **TST-DAT-009.4** | `AltitudeFromPressureDeviceTest.kt` | Simulate `onSensorChanged` with `myLocation == null`. | `fetchDemOrFallbackAsync()` is queued; zero synchronous calls to insert an uncalibrated `AUTO_LEARNED` row with `mLastRawAltitude`. | Zero uncalibrated preliminary records created. |
| **TST-DAT-009.5** | Full Repository | Execute clean-room unit regression: `./gradlew testDebugUnitTest`. | All test suites pass with 0 failures and 0 errors. | Build Success. |

---

## 5. ASPICE Traceability Matrix

| Requirement ID | Test Specification ID | Verification Method | Status |
| :--- | :--- | :--- | :--- |
| `REQ-DAT-007` (Updated) | `TST-DAT-009` | Automated JUnit / MockK (`KnownLocationsDatabaseManagerTest.kt`, `AltitudeFromPressureDeviceTest.kt`) | Specified |
| `REQ-DAT-014` | `TST-DAT-008` | Automated JUnit (`ElevationServiceTest.kt`, `KnownLocationsDatabaseManagerTest.kt`) | Verified |
| `REQ-CON-011` | `TST-CON-002` | Automated Unit Test | Verified |
| `REQ-CON-013` | `TST-CON-004` | Automated Unit Test (`AltitudeFromPressureDeviceTest.kt`) | Verified |
