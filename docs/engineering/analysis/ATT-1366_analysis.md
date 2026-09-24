# Problem Domain & Architectural Impact Analysis - ATT-1366: Auto Learned Altitude Does Not Work Properly

## 1. Feature / Bug Overview & Domain Context

* **Issue Key**: `ATT-1366` / `ATT-1372`
* **Parent Epic**: `ATT-235` / Sensor Calibration & Data Integrity
* **Target Version**: `V4.9.38` (Sprint `2026-39.2`)
* **Related Issues**:
  - `ATT-1278`: *[Feature] Altitude Correction: Get correct altitude from the internet* (Precursor: Schema V5 & DEM Client)
  - `ATT-919`: *[Feature] Altitude Correction: Create UI for this DB such that the user can set the correct altitude that is then never ever updated* (Successor: Map UI & User Lock)
  - `ATT-448` / `ATT-39`: Historical tickets that introduced raw barometric moving average learning (`REQ-DAT-007`)
  - `ATT-1353`: *[Bug] AltitudeFromPressureDevice.setAltitudeCorrection*

### Problem Statement
In historical implementations (`ATT-39`, `ATT-448`, `REQ-DAT-007`), `KnownLocationsDatabaseManager.learnLocation()` refined the stored altitude of workout start locations using a weighted running average of incoming raw barometric altitudes:
$$\text{refinedAltitude} = \frac{\text{existingAltitude} \times \text{hitCount} + \text{rawAltitude}}{\text{hitCount} + 1}$$

As observed by users, this algorithm **does not work properly** and fails to converge to an accurate or stable altitude.

---

## 2. Physical, Mathematical & Meteorological Analysis of the Flaw

### A. The Physics of Raw Barometric Pressure
In `AltitudeFromPressureDevice`, `mLastRawAltitude` is calculated via:
```java
SensorManager.getAltitude(SensorManager.PRESSURE_STANDARD_ATMOSPHERE, pressure)
```
where `SensorManager.PRESSURE_STANDARD_ATMOSPHERE` is fixed to the standard sea-level reference pressure $P_0 = 1013.25\text{ hPa}$.

In atmospheric physics, the barometric formula dictates:
$$h = 44330 \times \left(1 - \left(\frac{P}{P_0}\right)^{\frac{1}{5.255}}\right)$$

At sea level, an atmospheric pressure shift of $\Delta P = 1\text{ hPa}$ translates to approximately:
$$\Delta h \approx -8.3\text{ meters}$$

### B. Why the Running Average Fails Mathematically
1. **Large Natural Variance ($\sigma \approx 100\text{m}$)**:
   Real-world weather fluctuations routinely move regional sea-level pressure between $980\text{ hPa}$ (low pressure system / storm) and $1035\text{ hPa}$ (strong high pressure / anticyclone). This corresponds to an uncalibrated altitude variance of:
   $$\Delta h_{\text{weather}} \approx \pm 220\text{ meters}$$
   at the *exact same geographic position*.

2. **Severe Weather Sampling Bias**:
   Athletes and outdoor runners do not work out randomly across all weather distributions; they train disproportionately during clear, sunny weather (anticyclones / high pressure, e.g., $P > 1020\text{ hPa}$).
   Consequently, the sample mean:
   $$E[\text{rawAltitude}] < h_{\text{true}}$$
   is systematically biased downwards (often by $50\text{m}$ to $120\text{m}$) relative to true topographic elevation.

3. **Cold-Start Trapping**:
   On the first workout at a new start location (`hitCount = 1`), whatever weather happens to be active is recorded as the location's baseline. A single workout during an autumn storm or summer anticyclone traps an initial error of $\pm 150\text{m}$ that requires dozens of sessions to even partially dampen.

4. **Destruction of Authoritative Calibrations**:
   With the implementation of `ATT-1278` (Internet DEM) and the upcoming `ATT-919` (User manual lock), ground-truth reference altitudes (Copernicus 30m DEM or user survey heights) are available. Feeding noisy, weather-dependent `mLastRawAltitude` into a running mean degrades and corrupts authoritative ground-truth values.

---

## 3. Code-Level Root Cause & Concurrency Race Analysis

### A. Race Condition on New Location Discovery
In `AltitudeFromPressureDevice.java`:
```java
if (myLocation != null) {
    ...
} else {
    fetchDemOrFallbackAsync(latitude, longitude);
}
// Called synchronously right after asynchronous dispatch:
knownLocationsDb.learnLocation(currentLatLng, mLastRawAltitude, ExtremaType.START);
```
When a workout starts at an unknown location:
1. `fetchDemOrFallbackAsync()` is queued asynchronously to `mExecutor`.
2. Simultaneously on the calling thread, `knownLocationsDb.learnLocation()` executes immediately.
3. Because the background DEM request has not completed yet, `getMyLocation(currentLatLng)` returns `null`.
4. `learnLocation()` synchronously inserts a new location entry with source `AUTO_LEARNED` and altitude `mLastRawAltitude` (uncalibrated barometric reading).
5. When `fetchDemOrFallbackAsync()` finishes on `mExecutor`, it attempts to insert an `INTERNET_DEM` entry at the same coordinates, causing duplicate spatial entries or inconsistent cache states.

### B. Inappropriate Altitude Mutation in `learnLocation()`
In `KnownLocationsDatabaseManager.java`:
```java
double refinedAlt = (existing.altitude * existing.hitCount + altitude) / (existing.hitCount + 1);
ContentValues values = new ContentValues();
values.put(KnownLocationsDbHelper.ALTITUDE, refinedAlt);
values.put(KnownLocationsDbHelper.HIT_COUNT, existing.hitCount + 1);
updateId(existing.id, values);
```
Even for locations that are not explicitly locked, mutating the altitude upon every workout start introduces noise instead of stability.

---

## 4. Required Architecture & Changes

### A. Deprecate and Eliminate Running Mean Altitude Mutation
* **Zero Altitude Mutation**: `learnLocation()` shall **never** modify the stored `altitude` of an existing location.
* **Hit Count Tracking**: `learnLocation()` shall only increment `hitCount = existing.hitCount + 1` to track location usage frequency and recency.
* **Preserve Provenance**: The existing `altitude` and `source` (`INTERNET_DEM`, `MANUAL_USER`, `GPS_FALLBACK`, `LEGACY_RAW`) remain strictly untouched.

### B. Eliminate Synchronous `AUTO_LEARNED` Race in `AltitudeFromPressureDevice`
* When a location is unknown (`myLocation == null`), `AltitudeFromPressureDevice` shall rely exclusively on `fetchDemOrFallbackAsync()` to establish the initial location record with `INTERNET_DEM` or a validated fallback.
* It shall **not** create a preliminary uncalibrated `AUTO_LEARNED` record using `mLastRawAltitude`.
* For existing locations, `learnLocation()` will record the visit (`hitCount++`) without touching the altitude.

### C. Concurrency & Thread-Safety Guarantees
* All mutations in `KnownLocationsDatabaseManager` (`addNewLocation`, `updateMyLocation`, `learnLocation`) are guarded by `synchronized (this)` monitor locking on the singleton manager.
* In `fetchDemOrFallbackAsync()`, before creating a new record upon receiving DEM elevations, the callback executes a synchronized geofence query (`getMyLocation(latLng)`). If a location within the 200m radius was concurrently inserted or matched, it atomically updates the existing record rather than creating a duplicate row.

### D. Migration & Automatic Elevation Healing for Existing `AUTO_LEARNED` Records
* **Schema Version**: Database remains at **Schema V5** (`DB_VERSION = 5` introduced in `ATT-1278`). No schema bump is required because `is_locked` and `source` columns already exist.
* **Legacy & Auto-Learned Healing**:
  All records currently in the database with `source = 'AUTO_LEARNED'`, `source = 'LEGACY_RAW'`, or `source IS NULL` (as long as `is_locked == 0`) are automatically queried on app launch via `healLegacyLocationsAsync()` and batch-updated to `INTERNET_DEM` with authoritative Copernicus 30m DEM elevations.
  Locked records (`is_locked == 1`) and user-modified locations (`MANUAL_USER`) remain strictly immutable.

---

## 5. Requirement Delta & ASPICE Traceability

* **Superseded**: `REQ-DAT-007` (Automated Altitude Reference Discovery via running mean of raw pressure).
* **New Requirement**: `REQ-DAT-015` (Stable Reference Altitude Preservation & Hit Count Tracking).
  - The system SHALL maintain reference altitudes in `StartLocation2Altitude.db` without corrupting them through running averages of barometric pressure.
  - The system SHALL preserve authoritative elevations (`INTERNET_DEM`, `MANUAL_USER`) and only increment `hitCount` upon subsequent visits to a known location.
* **Related Requirements**:
  - `REQ-DAT-014` (DEM Altitude Retrieval & Healing)
  - `REQ-CON-011` (Cold-Start Baseline Protection)
  - `REQ-CON-013` (Null-Safe Correction Dispatch)

---

## 6. Verification Strategy

1. **Unit Tests in `KnownLocationsDatabaseManagerTest.kt`**:
   - Verify `learnLocation()` increments `hitCount` on repeat visits.
   - Verify `learnLocation()` strictly preserves existing `altitude`, regardless of the `altitude` parameter passed into it.
   - Verify `source` remains intact.
   - **Concurrency / Geofence Test**: Simulate concurrent `learnLocation()` and `fetchDemOrFallbackAsync()` completion to verify zero duplicate spatial rows.
2. **Integration Tests in `AltitudeFromPressureDeviceTest.kt`**:
   - Verify sensor initialization does not trigger competing uncalibrated inserts.
3. **Clean-Room Regression**:
   - Verify `./gradlew testDebugUnitTest` passes with zero failures across the entire test suite.
