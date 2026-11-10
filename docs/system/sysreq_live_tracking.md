# System Requirements Specification: Live Tracking with Sensors

**Document Version**: 1.0.0  
**ASPICE Process Areas**: SYS.2 (System Requirements Analysis, BP1–BP3)  
**Requirement Reference**: `REQ-PRO-054` (`REQ-PRO-054.1`, `REQ-PRO-054.2`)  
**Test Reference**: `TST-PRO-054` (`TST-PRO-054.1`–`TST-PRO-054.3`)  
**Author**: Antigravity Autonomous Engineering Pair  
**Status**: Approved & Grounded in Live Source Code  

---

## 1. Scope & Operational Context

This document establishes the formal, black-box system requirements (`SYSREQ-TRK-*`) for endurance sports tracking in **aTrainingTracker**. It defines the end-to-end system capabilities encompassing wireless sensor telemetry acquisition, connection resilience, deterministic sampling, GNSS positioning, foreground power management, and real-time sensory feedback.

These requirements satisfy ASPICE 4.0 **SYS.2 (System Requirements Analysis)** Base Practices:
* **SYS.2 BP1**: *Specify system requirements* – Define functional, non-functional, timing, and interface requirements.
* **SYS.2 BP2**: *Structure system requirements* – Organize requirements by capability and operational lifecycle.
* **SYS.2 BP3**: *Analyze system requirements* – Ensure verifiability, risk management, and complete traceability.

---

## 2. System Requirements Catalog

### SYSREQ-TRK-001: Multi-Sensor Acquisition & Profile Interoperability
* **Title**: Multi-Sensor Telemetry Acquisition & Standard Profile Interoperability
* **Statement**: The system SHALL discover, pair, connect, and concurrently acquire real-time telemetry from multiple external Bluetooth Low Energy (BLE) peripherals and ANT+ sports devices without cross-channel interference or dropped samples. Supported peripheral profiles SHALL include:
  1. *Heart Rate*: Standard BLE GATT Heart Rate Service (`0x180D`, Measurement `0x2A37`) and ANT+ Heart Rate profile.
  2. *Cycling Speed & Cadence*: BLE CSC Service (`0x1816`, Measurement `0x2A5B`) and ANT+ Bike Speed & Cadence profiles.
  3. *Cycling Power*: BLE Cycling Power Service (`0x1818`, Measurement `0x2A63`) and ANT+ Bike Power profile.
  4. *Running Speed & Cadence*: BLE RSC Service (`0x1814`, Measurement `0x2A53`) and ANT+ Stride profile.
  5. *Peripheral Battery*: BLE Battery Service (`0x180F`, Level `0x2A19`) with 5-minute periodic state monitoring.
  6. *Rear-Facing Radar*: BLE Garmin Varia Radar custom service (`6a4e3200-667b-11e3-949a-0800200c9a66`, characteristic `6a4e3204-667b-11e3-949a-0800200c9a66`) delivering vehicle approach velocities and threat ranges.
* **Rationale**: Athletes utilize heterogeneous multisport sensor setups (e.g. chest strap, power meter, wheel speed sensor, and rearview radar simultaneously). The system must reliably multiplex telemetry streams.
* **Priority**: High
* **Verification Method**: Demonstration & Test (`SystemLiveTrackingRequirementsContractTest`, `BANALServiceTest`, `BluetoothConstants`)
* **Traced Software Components**: `BANALService.java`, `MyBTLEDevice.java`, `BluetoothConstants.java`, `BTLERadarDevice.java`, `MySensorManager.java`

### SYSREQ-TRK-002: Automatic Reconnection & Exponential Backoff Resilience
* **Title**: Automatic Reconnection & Exponential Backoff Resilience
* **Statement**: When a paired peripheral experiences unexpected radio packet loss or GATT disconnection during active tracking, the system SHALL:
  1. Maintain active workout recording uninterrupted without blocking the 1-Hz sampling thread.
  2. Initiate an automatic reconnection schedule starting with an initial delay of `1,000 ms` (`INITIAL_RECONNECT_DELAY_MS`).
  3. Exponentially double the retry delay upon successive connection failures up to a maximum backoff cap of `16,000 ms` (`MAX_RECONNECT_DELAY_MS`) to protect host device battery life.
  4. Enforce an autonomous `3,500 ms` packet starvation watchdog (`WATCHDOG_TIMEOUT_MS`) on safety-critical rearview radar telemetry, transitioning the sensor to a disconnected fail-safe state if packet delivery ceases.
  5. Automatically re-bind telemetry filters and resume metric updates immediately upon physical peripheral re-establishment.
* **Rationale**: Wireless RF dropouts (due to athlete distance, terrain obstruction, or body attenuation) are routine during outdoor workouts; recovery must be autonomous and energy-efficient.
* **Priority**: High
* **Verification Method**: Test & Analysis (`SystemLiveTrackingRequirementsContractTest`, `MyBTLEDevice`, `BTLERadarDevice`)
* **Traced Software Components**: `MyBTLEDevice.java`, `BTLERadarDevice.java`, `FilterManager.java`, `BANALService.java`

### SYSREQ-TRK-003: Deterministic 1-Hz Technical Sampling Loop & Time-Series Recording
* **Title**: Deterministic 1-Hz Technical Sampling Loop & Time-Series Recording
* **Statement**: During active activity recording, the system SHALL execute a periodic fixed-rate 1-Hz technical sampling tick (`period = 1,000 ms`, `TimeUnit.SECONDS` via `ScheduledExecutorService`):
  1. Synchronously capture instantaneous readings across all connected sensors (speed, cadence, heart rate, power, GNSS, altitude).
  2. Compute derived physiological and spatial metrics (e.g. current lap metrics, rolling power, HR zones, distance increments).
  3. Dispatch time-series data asynchronously to a dedicated database executor thread (`mDbExecutor`), serializing SQLite sample writes into `WorkoutSamples.db` without starving the Android UI main looper or sampling timer.
* **Rationale**: Accurate sport analytics (power duration curves, normalized power, segment pacing) require a strictly deterministic 1-Hz discrete time-base.
* **Priority**: High
* **Verification Method**: Test & Inspection (`SystemLiveTrackingRequirementsContractTest`, `TrackerService.java`, `WorkoutSamplesDatabaseManager.java`)
* **Traced Software Components**: `TrackerService.java`, `FilterManager.java`, `WorkoutSamplesDatabaseManager.java`

### SYSREQ-TRK-004: Multi-Source GNSS Acquisition & Position Telemetry
* **Title**: Multi-Source GNSS Acquisition & Position Telemetry
* **Statement**: The system SHALL acquire geospatial coordinates and velocity from Android location providers:
  1. Support direct hardware satellite fixes via `LocationManager.GPS_PROVIDER` (raw NMEA and location callbacks) for autonomous and offline operation.
  2. Support Google Play Services fused location via `FusedLocationProviderClient` for battery-efficient city tracking.
  3. Filter out invalid null-island fixes (`lat = 0.0, lng = 0.0`) and enforce accuracy threshold rejection before distance accumulation and trackpoint recording.
* **Rationale**: Robust GPS trajectory recording is fundamental to distance calculation, segment matching, elevation profiling, and emergency location awareness.
* **Priority**: High
* **Verification Method**: Test (`SystemLiveTrackingRequirementsContractTest`, `SpeedAndLocationDevice_GPS.java`, `SpeedAndLocationDevice_GoogleFused.java`)
* **Traced Software Components**: `SpeedAndLocationDevice_GPS.java`, `SpeedAndLocationDevice_GoogleFused.java`, `MapViewportHelper.kt`, `TrackerService.java`

### SYSREQ-TRK-005: Background Foreground Service Execution & Power Management
* **Title**: Background Foreground Service Execution & Power Management
* **Statement**: The system SHALL maintain continuous, uninterrupted recording when the user turns off the display or switches to another mobile application:
  1. Execute tracking within an Android Foreground Service (`TrackerService`) displaying an ongoing notification with active workout elapsed time and distance.
  2. Dynamically declare and verify required runtime Foreground Service types (`FOREGROUND_SERVICE_TYPE_LOCATION`, `FOREGROUND_SERVICE_TYPE_HEALTH`, `FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE`) complying with Android 14+ platform security constraints.
  3. Acquire a partial WakeLock (`PowerManager.PARTIAL_WAKE_LOCK`) on activity start and reliably release the WakeLock upon workout stop, discard, or crash teardown.
  4. Provide a direct platform intent flow (`ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`) requesting system power management whitelisting.
* **Rationale**: Android aggressively throttles background execution and suspends CPU cores unless a properly typed foreground service and partial WakeLock are active.
* **Priority**: High
* **Verification Method**: Test & Static Audit (`SystemLiveTrackingRequirementsContractTest`, `TrackerService.java`, `MergedManifestFgsAuditTest`)
* **Traced Software Components**: `TrackerService.java`, `TrainingApplication.java`, `AndroidManifest.xml`

### SYSREQ-TRK-006: Real-Time Sensory Alerts & Cockpit Feedback
* **Title**: Real-Time Sensory Alerts & Cockpit Feedback
* **Statement**: The system SHALL provide configurable, real-time sensory alerts and telemetry presentation to the athlete:
  1. Render live telemetry across customizable Jetpack Compose cockpit dashboard tiles with sub-second visual refresh.
  2. Trigger acoustic safety cues via `AudioAttributes.USAGE_ALARM` or `USAGE_MEDIA` ensuring audible delivery regardless of ringer silent mode.
  3. Surface multi-threat rearview radar proximity warnings with distinct visual threat color codings (green, amber, red) and auditory warning chirps.
  4. Surface automated GPS fix acquisition, auto-pause/auto-resume status transitions, and sensor battery low-capacity warnings.
* **Rationale**: Endurance athletes require instant, glanceable, and acoustically distinct status awareness while navigating traffic or maintaining pacing targets.
* **Priority**: Medium
* **Verification Method**: Test & Demonstration (`SystemLiveTrackingRequirementsContractTest`, `RadarThreatView`, `BikeBellAudioSynthesizer`)
* **Traced Software Components**: `SensorGridScreen.kt`, `RadarThreatView.kt`, `BikeBellAudioSynthesizer.java`, `SoundEngine.java`

---

## 3. System Requirements & Software Code Grounding Matrix

| System Requirement | Primary Capabilities | Traced Software Classes | Grounded Constants / Parameters |
| :--- | :--- | :--- | :--- |
| `SYSREQ-TRK-001` | BLE GATT & ANT+ sensor acquisition | `BANALService`, `BluetoothConstants`, `BTLERadarDevice` | `SERVICE_HEART_RATE`, `SERVICE_CYCLING_POWER`, `SERVICE_CYCLING_SPEED_AND_CADENCE`, `SERVICE_RADAR` (`6a4e3200...`) |
| `SYSREQ-TRK-002` | Exponential backoff reconnect, radar watchdog | `MyBTLEDevice`, `BTLERadarDevice`, `FilterManager` | `INITIAL_RECONNECT_DELAY_MS = 1000L`, `MAX_RECONNECT_DELAY_MS = 16000L`, `WATCHDOG_TIMEOUT_MS = 3500L` |
| `SYSREQ-TRK-003` | Deterministic 1-Hz sampling loop | `TrackerService`, `WorkoutSamplesDatabaseManager` | `scheduleAtFixedRate(..., 1, TimeUnit.SECONDS)`, `mDbExecutor` |
| `SYSREQ-TRK-004` | GNSS acquisition & null-island filtering | `SpeedAndLocationDevice_GPS`, `SpeedAndLocationDevice_GoogleFused` | `LocationManager.GPS_PROVIDER`, `FusedLocationProviderClient` |
| `SYSREQ-TRK-005` | Foreground service, WakeLock, power governance | `TrackerService`, `TrainingApplication` | `PowerManager.PARTIAL_WAKE_LOCK`, `FOREGROUND_SERVICE_TYPE_LOCATION`, `FOREGROUND_SERVICE_TYPE_HEALTH` |
| `SYSREQ-TRK-006` | Cockpit tiles, radar visual/audio warnings | `SensorGridScreen`, `RadarThreatView`, `SoundEngine` | `USAGE_ALARM`, `USAGE_MEDIA`, radar threat levels |
