# Problem Domain & Architectural Impact Analysis - ATT-1278: Altitude Correction - Get Correct Altitude from the Internet

## 1. Feature Overview & Domain Context

* **Issue Key**: ATT-1278
* **Parent Epic**: ATT-235 / Sensor Calibration & Data Integrity
* **Target Version**: `V4.9.38` (Sprint `2026-39.2`)
* **Related Issues**: 
  - ATT-1366: *[Bug] Auto learned altitude does not work properly*
  - ATT-919: *[Feature] Altitude Correction: Create UI for this DB such that the user can set the correct altitude that is then never ever updated*
  - ATT-448: *Refined Altitude Learning*
  - ATT-1353: *[Bug] AltitudeFromPressureDevice.setAltitudeCorrection*

### Domain Motivation
The application utilizes the device's onboard barometric pressure sensor (`TYPE_PRESSURE`) to measure relative altitude changes with sub-meter vertical precision. However, converting atmospheric pressure to absolute altitude requires reference to the sea-level pressure ($P_0$, QNH). 

Currently, `KnownLocationsDatabaseManager.java` attempts to learn this reference altitude by calculating a weighted moving average of uncorrected raw pressure measurements (`mLastRawAltitude`). Because meteorological atmospheric pressure fluctuates by $\pm 20\text{ to } 30\text{ hPa}$ (equating to $\pm 165\text{m}$ to $\pm 250\text{m}$ altitude variation at the same spot), the moving average fails to converge cleanly and creates severe cold-start calibration errors (ATT-1366).

ATT-1278 introduces an internet-based Digital Elevation Model (DEM) lookup mechanism to fetch authoritative, true ground-truth elevation for any geographic coordinate (latitude, longitude). This provides instant, highly accurate calibration upon location discovery, completely bypassing meteorological pressure drift.

---

## 2. Physical & Meteorological Principles

### The Barometric Altimetry Formula
Under the International Standard Atmosphere (ISA), altitude $h$ is calculated from pressure $P$ and sea-level reference pressure $P_0$:
$$h = 44330 \times \left(1 - \left(\frac{P}{P_0}\right)^{\frac{1}{5.255}}\right)$$

In Android, `SensorManager.getAltitude(SensorManager.PRESSURE_STANDARD_ATMOSPHERE, pressure)` fixes $P_0 = 1013.25\text{ hPa}$.

### Why Weather Flaws the Current Moving Average
1. **Atmospheric Variation**: Real sea-level pressure varies dynamically between $970\text{ hPa}$ (low pressure/storm) and $1045\text{ hPa}$ (high pressure/anticyclone).
2. **Cold-Start Trapping**: On session 1 at a new location, whatever weather is active is locked into the database as the reference altitude (`hitCount = 1`). A sunny day ($1030\text{ hPa}$) traps a baseline $\sim 140\text{m}$ too low.
3. **Sampling Bias**: Athletes record workouts predominantly in fair weather (high pressure). The mathematical expected value of raw pressure across workouts is systematically biased towards higher pressure (lower estimated elevation).

### The DEM Ground-Truth Alternative
A Digital Elevation Model (DEM) records the topographic surface height of the Earth above mean sea level (EGM96 / WGS84 ellipsoid). A single query with $(\text{lat}, \text{lng})$ yields the true ground elevation within $\pm 1\text{ to } 3\text{ meters}$, independent of atmospheric conditions.

---

## 3. Technical Feasibility & API Selection

Three primary public elevation API options were evaluated:

| Criteria | **Open-Meteo Elevation API** (Selected) | **Open-Elevation** | **Google Maps Elevation API** |
| :--- | :--- | :--- | :--- |
| **Endpoint** | `https://api.open-meteo.com/v1/elevation` | `https://api.open-elevation.com/api/v1/lookup` | `https://maps.googleapis.com/maps/api/elevation/json` |
| **Authentication** | **No API key required** (Free for non-commercial) | No API key required | Requires Google Cloud API Key & Billing |
| **Underlying DEM** | Copernicus 30m / 90m & SRTM | SRTM 90m | Proprietary Google DEM |
| **Rate Limit** | 10,000 requests/day (far exceeds 1-5 queries/day app need) | Community-hosted, frequent downtime | Pay-per-use, billing liability |
| **Payload Size** | Minimal JSON: `{"elevation": [38.0]}` | JSON: `{"results": [{"latitude": ..., "longitude": ..., "elevation": 312.0}]}` | Larger JSON with status/resolution |
| **Latency** | < 150ms | 500ms - 2000ms | < 200ms |
| **Recommendation** | **PRIMARY SELECTION** | Secondary Fallback | Excluded (unnecessary cost & setup) |

### Protocol Specification
* **Request**:
  `GET https://api.open-meteo.com/v1/elevation?latitude=52.52&longitude=13.41`
* **Response Header**: `Content-Type: application/json; charset=utf-8`
* **Response Body**:
  ```json
  {
    "elevation": [38.0]
  }
  ```
* **Failure Handling**: HTTP status $\ge 400$, connection timeout (5s), or invalid JSON parses gracefully without throwing.

---

## 4. Architectural & Component Impact Analysis

### Affected Classes & Modules

```
com.atrainingtracker.
├── banalservice.devices
│   └── AltitudeFromPressureDevice.java       <-- Initiates location discovery & calibration
├── trainingtracker.database
│   ├── KnownLocationsDatabaseManager.java     <-- Manages StartLocation2Altitude.db (Schema V5)
│   └── ElevationSource.kt                     <-- NEW: Enum (AUTO_LEARNED, INTERNET_DEM, MANUAL_USER, GPS_FALLBACK)
└── trainingtracker.elevation
    ├── ElevationService.kt                    <-- NEW: Non-blocking client for DEM API
    └── ElevationResult.kt                     <-- NEW: Sealed class / model for success/failure
```

### Detailed Impact:
1. **`KnownLocationsDatabaseManager.java`**:
   - Upgrade SQLite schema from `DB_VERSION = 4` to `DB_VERSION = 5`.
   - Add columns:
     - `is_locked INTEGER DEFAULT 0`
     - `source TEXT DEFAULT 'AUTO_LEARNED'`
   - Update model `MyLocation` to expose `isLocked` (boolean) and `source` (`ElevationSource`).
   - Invariant: If `is_locked == 1`, `learnLocation()` MUST NOT mutate or overwrite the altitude.

2. **`ElevationService.kt`**:
   - Implemented in Kotlin using existing dependencies (`OkHttp` and `kotlinx.serialization` or lightweight `org.json`).
   - Executes asynchronous network calls using Kotlin coroutines (`Dispatchers.IO`).
   - Thread-safe and decoupled from UI.

3. **`AltitudeFromPressureDevice.java`**:
   - When a location is discovered at workout start:
     - If the location is already known and `is_locked`: use existing altitude immediately.
     - If new or not locked and internet is available: trigger async DEM lookup.
     - Barometric calibration (`setAltitudeCorrection`) applies authoritative altitude as soon as available.

4. **Offline Resilience & Invariants**:
   - If device is offline (airplane mode, deep forest, lack of cellular signal):
     - Fall back to GPS altitude if available, or temporary raw barometric altitude.
     - Tag entry with `source = GPS_FALLBACK` and `is_locked = 0`.
     - When network connectivity returns, a deferred lookup can upgrade the entry to `source = INTERNET_DEM`.

---

## 5. System Invariants & Anti-Regression Rules

1. **Non-Blocking Operation**:
   - Network calls to the elevation API MUST NEVER run on the main (UI) thread or block the sensor event processing thread (`onSensorChanged`).
2. **Offline Immunity**:
   - Sensor initialization and workout tracking MUST proceed uninterrupted even if network is completely unreachable or times out.
3. **Chesterton's Fence & Cold-Start Protection**:
   - Must preserve `REQ-CON-011` (cold-start baseline protection) and `REQ-CON-013` (null-safe correction dispatch).
4. **Lock Invariant (Precursor to ATT-919)**:
   - Any entry marked `is_locked = 1` MUST be strictly immutable against automated refinements.

---

## 6. Risk Rating & Mitigation

* **Risk Rating**: **MEDIUM**
* **Technical Justification**: Introducing an external network call into sensor initialization workflows introduces potential latency, timeouts, and connectivity failures.
* **Mitigation**:
  1. Strict 5-second timeout on network queries.
  2. Asynchronous execution via coroutines / background thread pool.
  3. Seamless fallback to local/GPS altitude upon network unavailability.
  4. Comprehensive unit tests using mocked HTTP server / client.

---

## 7. Recommendation

**RECOMMEND PASS for Stage 1**. Proceed to Stage 2 (`ATT-1368`: Test Specification & Requirements Formulation).
