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
The application utilizes the device's onboard barometric pressure sensor (`TYPE_PRESSURE`) to measure relative altitude changes with sub-meter vertical precision. However, converting atmospheric pressure to absolute altitude requires reference to sea-level pressure ($P_0$, QNH). 

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

### Protocol Specification & Error Code Matrix
* **Request**:
  `GET https://api.open-meteo.com/v1/elevation?latitude=52.52&longitude=13.41`
* **Response Header**: `Content-Type: application/json; charset=utf-8`
* **Response Body (200 OK)**:
  ```json
  {
    "elevation": [38.0]
  }
  ```
* **HTTP Status Code Handling**:
  - `200 OK`: Parse `elevation[0]`. Valid range check ($-500\text{m} \le h \le 9000\text{m}$). On valid value, persist with `source = INTERNET_DEM`.
  - `429 Too Many Requests`: Trigger exponential backoff (initial delay: 60s, max: 1hr). Temporarily fall back to GPS altitude.
  - `503 Service Unavailable` / `5xx Server Error`: Log advisory warning. Fall back to GPS altitude without crashing. Trip short-term circuit breaker (cooldown: 5 minutes).
  - Network Timeout (5s) / `IOException`: Unreachable network. Gracefully degrade to GPS altitude (`source = GPS_FALLBACK`).

---

## 4. Architectural & Component Impact Analysis

### Call-Site Threading Map & Execution Architecture
To guarantee that UI responsiveness and hardware sensor processing are never stalled, all network interactions and database writes are strictly decoupled from the main thread and sensor dispatchers:

```
[SystemSensorManager / Binder Thread]
                 │
                 ▼
AltitudeFromPressureDevice.onSensorChanged()
                 │
                 ▼
AltitudeFromPressureDevice.initPressureSensor()
                 │
                 ├─► Check Cache: KnownLocationsDatabaseManager.getMyLocation(latLng) [Local SQLite, Fast]
                 │        ├─► Cache Hit & is_locked == 1 ──► Apply correction immediately
                 │        └─► Cache Hit & is_locked == 0 (Already has DEM) ──► Apply correction immediately
                 │
                 └─► Cache Miss or Uncalibrated:
                          │
                          ▼ (Non-blocking Dispatch)
                 CoroutineScope(Dispatchers.IO).launch
                          │
                          ▼
                 ElevationService.fetchElevation(lat, lng) [OkHttp, timeout: 5s]
                          │
                          ├─► [SUCCESS 200 OK] ──► KnownLocationsDatabaseManager.addNewLocation(...)
                          │                              │
                          │                              ▼
                          │                        AltitudeFromPressureDevice.setAltitudeCorrection(...)
                          │
                          └─► [FAIL / TIMEOUT] ──► Fallback to GPS Altitude (if available)
                                                         │
                                                         ▼
                                                   KnownLocationsDatabaseManager.addNewLocation(..., GPS_FALLBACK)
```

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

---

## 5. Spatial Caching, Precision Bounds & Jitter Prevention

### Spatial Matching & Geofence Bounds
`KnownLocationsDatabaseManager.getMyLocation(LatLng currentLatLng)` performs a spatial radius query:
- Each stored location has a configurable geofence `radius` (default: `DEFAULT_RADIUS = 200m`).
- A candidate point matches an existing entry if $\text{distanceTo}(\text{startLocation}) < \text{radius}$.
- If multiple locations match, the nearest entry ($\min \text{distance}$) is chosen.
- **Cache Hit Guarantee**: Any workout starting within $200\text{m}$ of a known location is a cache hit and bypasses all network queries.

### Coordinate Quantization
To prevent floating-point GPS jitter (e.g. $52.5200001$ vs $52.5200003$) from generating duplicate spatial records or unnecessary lookups:
- Coordinates stored in `StartLocation2Altitude.db` and sent to the elevation API are rounded to **5 decimal places**:
  $$\text{precision} = 10^{-5}\text{ degrees} \approx 1.11\text{ meters}$$
- This eliminates microscopic sensor jitter while preserving high topographic accuracy.

---

## 6. Database Schema Evolution (V4 to V5) & Atomic Migration

### Schema Changes
The database `StartLocation2Altitude.db` (`KnownLocationsDatabaseManager.java`) will be upgraded from `DB_VERSION = 4` to `DB_VERSION = 5`:

```sql
-- Upgrading from V4:
ALTER TABLE StartLocation2Altitude ADD COLUMN is_locked INTEGER DEFAULT 0;
ALTER TABLE StartLocation2Altitude ADD COLUMN source TEXT DEFAULT 'AUTO_LEARNED';
```

### Adherence to `REQ-DAT-008` (Atomic Upgrade Invariant)
In accordance with `REQ-DAT-008` (*Atomic Database Upgrades*):
> "The system SHALL NOT perform manual transaction management (e.g., setTransactionSuccessful) within onUpgrade callbacks of SQLiteOpenHelper. All schema migrations MUST rely on the automatic transaction wrapper provided by the Android framework to prevent IllegalStateException and ensure data integrity."

- The Android framework's `SQLiteOpenHelper.getWritableDatabase()` automatically wraps the entire `onUpgrade` invocation inside an atomic SQLite transaction (`BEGIN EXCLUSIVE TRANSACTION` ... `COMMIT`).
- If an unhandled exception or crash occurs during column addition, Android automatically rolls back the transaction, preserving the intact V4 database.
- Migration will be thoroughly tested in JVM unit tests using real in-memory SQLite instances.

---

## 7. Formal Requirement Traceability Mapping

| Requirement ID | Standard / Title | Traceability & Compliance Impact |
| :--- | :--- | :--- |
| **REQ-CON-011** | *Barometric Cold-Start Baseline Protection* | **Preserved**. `AltitudeFromPressureDevice` continues to withhold uncalibrated barometric readings from active workout statistics until a valid baseline (DEM elevation, locked location, or GPS fix) is established. |
| **REQ-CON-013** | *Barometric Altitude Sensor Initialization & Null-Safe Correction Dispatch* | **Preserved**. When DEM elevation arrives asynchronously, `setAltitudeCorrection(double)` utilizes the defensive null-safe guards implemented in ATT-1353, preventing unboxing NPEs. |
| **REQ-DAT-007** | *Automated Altitude Reference Discovery* | **Extended / Evolved**. Automated discovery is upgraded from noisy raw pressure averaging to authoritative DEM querying. |
| **REQ-DAT-008** | *Atomic Database Upgrades* | **Verified**. Migration from V4 to V5 follows the automatic transaction wrapper invariant without manual transaction manipulation. |
| **REQ-DAT-014** (NEW) | *Internet Digital Elevation Model (DEM) Reference Altitude Retrieval & Spatial Caching* | **Formulated**. Specifies the asynchronous retrieval of topographic elevation from Open-Meteo, 5-decimal coordinate quantization, spatial caching within 200m radius, and offline GPS fallback. |

---

## 8. Risk Rating & Mitigation

* **Risk Rating**: **MEDIUM**
* **Technical Justification**: Introducing an external network call into sensor initialization workflows introduces potential latency, timeouts, and connectivity failures.
* **Mitigations**:
  1. **Strict 5-Second Timeout**: OkHttp client configured with a 5s connect/read timeout.
  2. **Non-Blocking Coroutines**: Executed strictly on `Dispatchers.IO`. Sensor thread (`onSensorChanged`) is never blocked.
  3. **Circuit Breaker & Backoff**: Consecutive network errors trip a 5-minute circuit breaker to avoid battery drain or socket exhaustion.
  4. **Seamless Offline Fallback**: Immediate fallback to GPS altitude (`source = GPS_FALLBACK`) if offline.
  5. **Deterministic Mock Testing**: `MockWebServer` / mocked HTTP engine in unit tests to verify all status codes (200, 429, 503, timeout).

---

## 9. Recommendation

**RECOMMEND PASS for Stage 1**. Proceed to Stage 2 (`ATT-1368`: Test Specification & Requirements Formulation).
