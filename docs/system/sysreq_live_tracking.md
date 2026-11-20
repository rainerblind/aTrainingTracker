# System Requirements Specification: Live Tracking with Sensors

**Document ID**: `DOC-SYS-TRK`  
**Version**: 1.2.11  
**ASPICE Process Areas**: SYS.2 (System Requirements Analysis, BP1–BP3)  
**Requirement Reference**: `REQ-PRO-054` (`REQ-PRO-054.1`, `REQ-PRO-054.2`)  
**Test Reference**: `TST-PRO-054` (`TST-PRO-054.1`–`TST-PRO-054.3`)  
**Author**: Antigravity Autonomous Engineering Pair  
**Status**: Reviewed  
**Approver**: Pending PO Sign-Off  

---

## 1. Scope & Operational Context

This document establishes the formal, black-box system requirements (`SYSREQ-TRK-001` through `SYSREQ-TRK-013`) for endurance sports tracking in **aTrainingTracker**. It defines the end-to-end system capabilities encompassing wireless sensor telemetry acquisition, connection resilience, deterministic sampling, GNSS positioning, foreground power management, real-time sensory feedback, bidirectional radar/light actuation, barometric/EKF climb detection, GPX turn-by-turn navigation, smartphone IMU kinematics/iTPMS, swimming telemetry, HRV/DFA alpha-1 analysis, and process-death session recovery.

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
* **Verification Method**: Demonstration & Test (`SystemLiveTrackingRequirementsContractTest.kt`, `BANALServiceLifecycleTest.kt`, `BANALServiceRepositoryTest.kt`, `BluetoothConstants.java`)
* **Traced Software Components**: `BANALService.java`, `MyBTLEDevice.java`, `BluetoothConstants.java`, `BTLERadarDevice.java`, `MySensorManager.java`, `DeviceType.java`

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
* **Verification Method**: Test & Analysis (`SystemLiveTrackingRequirementsContractTest.kt`, `MyBTLEDevice.java`, `BTLERadarDevice.java`)
* **Traced Software Components**: `MyBTLEDevice.java`, `BTLERadarDevice.java`, `FilterManager.kt`, `BANALService.java`

### SYSREQ-TRK-003: Deterministic 1-Hz Technical Sampling Loop & Time-Series Recording
* **Title**: Deterministic 1-Hz Technical Sampling Loop & Time-Series Recording
* **Statement**: During active activity recording, the system SHALL execute a periodic fixed-rate 1-Hz technical sampling tick (`period = 1,000 ms`, `TimeUnit.SECONDS` via `ScheduledExecutorService`):
  1. Synchronously capture instantaneous readings across all connected sensors (speed, cadence, heart rate, power, GNSS, altitude).
  2. Compute derived physiological and spatial metrics (e.g. current lap metrics, rolling power, HR zones, distance increments).
  3. Dispatch time-series data asynchronously to a dedicated database executor thread (`mDbExecutor`), serializing SQLite sample writes into `WorkoutSamples.db` without starving the Android UI main looper or sampling timer.
* **Rationale**: Accurate sport analytics (power duration curves, normalized power, segment pacing) require a strictly deterministic 1-Hz discrete time-base.
* **Priority**: High
* **Verification Method**: Test & Inspection (`SystemLiveTrackingRequirementsContractTest.kt`, `TrackerService.java`, `WorkoutSamplesDatabaseManager.java`)
* **Traced Software Components**: `TrackerService.java`, `FilterManager.kt`, `WorkoutSamplesDatabaseManager.java`

### SYSREQ-TRK-004: Multi-Source GNSS Acquisition & Position Telemetry
* **Title**: Multi-Source GNSS Acquisition & Position Telemetry
* **Statement**: The system SHALL acquire geospatial coordinates and velocity from Android location providers:
  1. Support direct hardware satellite fixes via `LocationManager.GPS_PROVIDER` (raw NMEA and location callbacks) for autonomous and offline operation.
  2. Support Google Play Services fused location via `SpeedAndLocationDevice_GoogleFused.java` (`LocationServices.FusedLocationApi` with `GoogleApiClient` and `LocationListener`) for battery-efficient city tracking.
  3. Filter out invalid null-island fixes (`lat = 0.0, lng = 0.0`) and enforce accuracy threshold rejection before distance accumulation and trackpoint recording.
* **Rationale**: Robust GPS trajectory recording is fundamental to distance calculation, segment matching, elevation profiling, and emergency location awareness.
* **Priority**: High
* **Verification Method**: Test (`SystemLiveTrackingRequirementsContractTest.kt`, `SpeedAndLocationDevice_GPS.java`, `SpeedAndLocationDevice_GoogleFused.java`)
* **Traced Software Components**: `SpeedAndLocationDevice_GPS.java`, `SpeedAndLocationDevice_GoogleFused.java`, `MapDetailViewportMath.kt`, `TrackerService.java`

### SYSREQ-TRK-005: Background Foreground Service Execution & Power Management
* **Title**: Background Foreground Service Execution & Power Management
* **Statement**: The system SHALL maintain continuous, uninterrupted recording when the user turns off the display or switches to another mobile application:
  1. Execute tracking within an Android Foreground Service (`TrackerService`) displaying an ongoing notification with active workout elapsed time and distance.
  2. Dynamically declare and verify required runtime Foreground Service types (`FOREGROUND_SERVICE_TYPE_LOCATION`, `FOREGROUND_SERVICE_TYPE_HEALTH`) complying with Android 14+ platform security constraints.
  3. Acquire a partial WakeLock (`PowerManager.PARTIAL_WAKE_LOCK`) on activity start and reliably release the WakeLock upon workout stop, discard, or crash teardown.
  4. Provide a direct platform intent flow (`ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`) requesting system power management whitelisting.
* **Rationale**: Android aggressively throttles background execution and suspends CPU cores unless a properly typed foreground service and partial WakeLock are active.
* **Priority**: High
* **Verification Method**: Test & Static Audit (`SystemLiveTrackingRequirementsContractTest.kt`, `TrackerService.java`, `MergedManifestFgsAuditTest.kt`)
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
* **Verification Method**: Test & Demonstration (`SystemLiveTrackingRequirementsContractTest.kt`, `RadarEdgeOverlay.kt`, `BikeBellAudioSynthesizer.kt`, `BikeBellAudioSynthesizerTest.kt`, `NavigationAudioAlertManagerTest.kt`)
* **Traced Software Components**: `SensorGridScreen.kt`, `RadarEdgeOverlay.kt`, `BikeBellAudioSynthesizer.kt`, `RadarAlertManager.kt`, `RadarAudioSynthesizer.kt`, `NavigationAudioAlertManager.kt`

### SYSREQ-TRK-007: Bidirectional BLE & ANT+ Radar / Tail-Light Actuation & Smart Braking
* **Title**: Bidirectional BLE & ANT+ Radar / Tail-Light Actuation & Smart Braking
* **Statement**: The system SHALL transmit bidirectional control commands to paired BLE and ANT+ rearview radar and bike tail-light peripherals:
  1. Support Garmin Varia BLE Light Control (`6a4e3202-667b-11e3-949a-0800200c9a66`, `CHARACTERISTIC_RADAR_LIGHT_CONTROL`), Garmin Varia RearVue control service (`6a4e2f00-667b-11e3-949a-0800200c9a66`, write `6a4e2f11-667b-11e3-949a-0800200c9a66`, notify `6a4e2f14-667b-11e3-949a-0800200c9a66`), and ANT+ Bike Lights (`0x23`, `ANTBikeLightDevice.kt`) / ANT+ Radar (`0x28`, `ANTBikeRadarDevice.kt`).
  2. Allow manual cockpit mode switching (`RadarLightController.kt`, `RadarLightDeviceClient.kt`, `RadarLightQuickSwitchSheet.kt`, `RadarLightModeGrid.kt`) and autonomous Smart Radar Light / Brake Light actuation driven by ambient light (`Sensor.TYPE_LIGHT`), proximity (`Sensor.TYPE_PROXIMITY`), and IMU deceleration (`SmartRadarLightEngine.kt`, `SmartBrakeLightDetector.kt`, `SolarElevationCalculator.kt`).
* **Rationale**: Cyclists require active tail-light mode control, solar-aware lighting automation, and deceleration brake-light signaling across both BLE and ANT+ radar/light ecosystems.
* **Priority**: High
* **Verification Method**: Test & Demonstration (`SystemLiveTrackingRequirementsContractTest.kt`, `BluetoothConstants.java`, `ANTBikeLightDevice.kt`, `SmartBrakeLightDetector.kt`)
* **Traced Software Components**: `BluetoothConstants.java`, `BTLERadarDevice.java`, `ANTBikeRadarDevice.kt`, `ANTBikeLightDevice.kt`, `RadarLightController.kt`, `RadarLightDeviceClient.kt`, `SmartRadarLightEngine.kt`, `SmartBrakeLightDetector.kt`, `RadarLightQuickSwitchSheet.kt`

### SYSREQ-TRK-008: Barometric Pressure, EKF Altitude/Slope Fusion, Live Climbs & Segments
* **Title**: Barometric Pressure, EKF Altitude/Slope Fusion, Live Climbs & Segments
* **Statement**: The system SHALL fuse onboard barometric pressure (`Sensor.TYPE_PRESSURE`) with GNSS/DEM elevation via an Extended Kalman Filter to detect climbs and live segments:
  1. Acquire barometric atmospheric pressure (`AltitudeFromPressureDevice.java`, `HavePressureSensor.java`), fuse altitude and slope kinematics (`KinematicAltitudeSlopeEkf.kt`, `AltitudeAndSlopeDevice_ATTFused.kt`), and persist climb/elevation caches (`ClimbsDatabaseManager.kt`, `ElevationCacheDatabaseManager.kt`).
  2. Detect approaching and active climbs (`ClimbDetector.kt`, `ClimbDeduplicator.kt`, `LiveClimbsRepository.kt`, `LiveClimbSheet.kt`) and live Strava segments (`LiveSegmentsRepository.kt`) with real-time distance/gradient feedback.
* **Rationale**: Raw GNSS altitude suffers from vertical multipath drift; barometric EKF fusion delivers smooth instantaneous grade and accurate climb/segment tracking.
* **Priority**: High
* **Verification Method**: Test & Analysis (`SystemLiveTrackingRequirementsContractTest.kt`, `KinematicAltitudeSlopeEkf.kt`, `ClimbDetector.kt`)
* **Traced Software Components**: `AltitudeFromPressureDevice.java`, `HavePressureSensor.java`, `KinematicAltitudeSlopeEkf.kt`, `AltitudeAndSlopeDevice_ATTFused.kt`, `ClimbDetector.kt`, `ClimbDeduplicator.kt`, `LiveClimbsRepository.kt`, `LiveClimbSheet.kt`, `LiveSegmentsRepository.kt`, `ClimbsDatabaseManager.kt`

### SYSREQ-TRK-009: Google Maps GPX Follow-Me & Turn-by-Turn Audio Navigation
* **Title**: Google Maps GPX Follow-Me & Turn-by-Turn Audio Navigation
* **Statement**: The system SHALL import GPX route tracks and deliver real-time map overlay and turn-by-turn audio guidance during active workouts:
  1. Parse and store GPX route polylines (`GpxRouteImporter.kt`) and render interactive Google Maps vector/lite overlays (`ATrainingTrackerMap.kt`, `ElevationProfile.kt`).
  2. Track route progression, detect off-course divergence (`TurnByTurnNavigationEngine.kt`, `TurnByTurnNavigationRepository.kt`, `RouteDivergenceDetector.kt`), and emit spoken/acoustic turn alerts (`NavigationAudioAlertManager.kt`).
* **Rationale**: Endurance athletes navigating unfamiliar routes require hands-free off-course alerts and upcoming turn cues synchronized with live workout tracking.
* **Priority**: High
* **Verification Method**: Test (`SystemLiveTrackingRequirementsContractTest.kt`, `GpxRouteImporter.kt`, `TurnByTurnNavigationEngine.kt`, `NavigationAudioAlertManager.kt`)
* **Traced Software Components**: `GpxRouteImporter.kt`, `TurnByTurnNavigationEngine.kt`, `TurnByTurnNavigationRepository.kt`, `RouteDivergenceDetector.kt`, `NavigationAudioAlertManager.kt`, `ATrainingTrackerMap.kt`, `ElevationAwareEtaCalculator.kt`

### SYSREQ-TRK-010: Smartphone IMU Kinematics, Carriage Classification, Cadence, Fused Odometry & Indirect TPMS
* **Title**: Smartphone IMU Kinematics, Carriage Classification, Cadence, Fused Odometry & Indirect TPMS
* **Statement**: The system SHALL acquire smartphone inertial measurement unit (IMU) streams for fused odometry, activity/phone carriage mode classification (`PhoneCarriageMode`, `PhoneCarriageClassifier`, persisted at `1 Hz` via `SensorType.ACTIVITY_AND_CARRIAGE_MODE`), carriage-classified cycling crank & running stride cadence estimation, stationary pinning, brake-light deceleration, and indirect tire pressure monitoring (iTPMS):
  1. Subscribe to authentic Android IMU channels (`Sensor.TYPE_GYROSCOPE`, `Sensor.TYPE_ROTATION_VECTOR`, `Sensor.TYPE_GAME_ROTATION_VECTOR`, `Sensor.TYPE_LINEAR_ACCELERATION`, `Sensor.TYPE_GRAVITY`, `Sensor.TYPE_ACCELEROMETER`, and opportunistically gated `Sensor.TYPE_STEP_DETECTOR` with sensor-absent fallback) via `SpeedAndLocationDevice_ATTFused.kt`, `QuaternionGyroProjector.kt`, `PhoneCarriageClassifier.kt`, `SmartBrakeLightDetector.kt`, and `SpeedAndLocationDevice.java`, smoothly adapting live EKF/barometer parameters (`AltitudeAndSlopeDevice_ATTFused.kt`, `BarometricAltitudeFilter.kt`) and post-workout ERTS filtering (`ErtsPostWorkoutRecomputer.kt`).
  2. Estimate indirect tire pressure drift and puncture anomalies (`IndirectTpmsEstimator.kt`, `DualWheelTpmsEstimator.kt`) by correlating wheel circumference ratios and GNSS/speed telemetry.
  3. Derive carriage-classified cycling crank cadence (`BIKE_FRONT_POCKET`, `BIKE_JERSEY_BACK`) and running stride cadence/`STRIDES` (`RUN_WAIST_HIP`, `RUN_BICEP_ARM`, `RUN_HANDHELD`) via shared pure-Kotlin IMU autocorrelation (`ImuCadenceDsp.kt`, `SmartphoneImuCadenceEstimator.kt`, `KinematicSportTypeDiscriminator.kt`, `PolarLiveRunningCadenceEstimator.kt`) with 50-Hz `Sensor.TYPE_ACCELEROMETER`, wrist-glance blanking, `Sensor.TYPE_STEP_DETECTOR` fusion, strict `BIKE_HANDLEBAR` suppression, and lower `MySensorManager` priority (`CADENCE_PRIORITY`, `STRIDES_PRIORITY`) than external ANT+/BLE sensors (`DeviceType.java`, `MySensorManager.java`).
  4. Infer live `BSportType` (`BIKE` vs. `RUN`) and stop-filtered moving-speed identity (`KinematicSportTypeDiscriminator.kt`, `MovingSpeedHistogram`, `EquipmentAndSportTypeDiscoveryManager.kt`, `BANALService.java`) during sensorless unselected sessions from bicycle coasting, step-length and permission-free 50-Hz `Sensor.TYPE_ACCELEROMETER` footstrike coherence, carriage mode coupling, and $O(1)$ moving-speed quantiles while strictly preserving Tier 1 user selection and Tier 2 external sensor sovereignty (`REQ-FIL-030`).
  5. Detect sustained triathlon, duathlon, and brick macro-sport transitions (`BIKE <-> TRANSITION <-> RUN`) with retroactive dismount/mount anchoring (`MultisportTransitionDetector.kt`, `MultisportTransitionBridge.kt`, `MultisportLapCoordinator.kt`, `MyAccumulatorSensor.java`), automatic 9-locale named laps (`Bike`, `Transition`, `Run`) in `Laps.db`, `WorkoutSamples.db` `LAP_NR` backfilling, same-sport stop immunity, and live cockpit sport switching (`REQ-FIL-031`).
* **Rationale**: Smartphone IMU fusion stabilizes indoor/tunnel speed/orientation tracking, optimizes EKF/barometer filtering across diverse phone mounting regimes and dismounts, supplies zero-extra-hardware cycling/running cadence without handlebar vibration pollution, infers sensorless sport identity cleanly across multi-stop errand rides and budget devices, powers brake-light triggers without external hardware, and detects tire deflation early.
* **Priority**: Medium
* **Verification Method**: Test (`SystemLiveTrackingRequirementsContractTest.kt`, `SpeedAndLocationDevice_ATTFused.kt`, `PhoneCarriageClassifier.kt`, `SmartphoneImuCadenceEstimator.kt`, `KinematicSportTypeDiscriminator.kt`, `IndirectTpmsEstimator.kt`, `DualWheelTpmsEstimator.kt`)
* **Traced Software Components**: `SpeedAndLocationDevice_ATTFused.kt`, `QuaternionGyroProjector.kt`, `PhoneCarriageClassifier.kt`, `PhoneCarriageMode.kt`, `ImuCadenceDsp.kt`, `SmartphoneImuCadenceEstimator.kt`, `KinematicSportTypeDiscriminator.kt`, `MultisportTransitionDetector.kt`, `MultisportTransitionBridge.kt`, `MultisportLapCoordinator.kt`, `PolarLiveRunningCadenceEstimator.kt`, `MySensorManager.java`, `DeviceType.java`, `AltitudeAndSlopeDevice_ATTFused.kt`, `BarometricAltitudeFilter.kt`, `ErtsPostWorkoutRecomputer.kt`, `SmartBrakeLightDetector.kt`, `SpeedAndLocationDevice.java`, `IndirectTpmsEstimator.kt`, `DualWheelTpmsEstimator.kt`, `UnicycleOdometryEkf.kt`

### SYSREQ-TRK-011: Pool & Open-Water Swimming Telemetry & Lap Adjustment
* **Title**: Pool & Open-Water Swimming Telemetry & Lap Adjustment
* **Statement**: The system SHALL support dedicated pool and open-water swimming tracking, post-workout pool-length/lap recalculation, and FIT swimming export:
  1. Configure swimming sport profiles and pool-length presets (`SwimmingSection.kt`), ingest Polar Verity Sense swim backfill (`PolarVeritySenseOfflineController.kt`), and export `Sport.SWIMMING` FIT summaries (`FitFileWriter.kt`).
  2. Provide post-workout swim lap/distance adjustment and recalculation (`SwimWorkoutAdjustCard.kt`, `WorkoutRepository.kt`).
* **Rationale**: Optical wrist/temple swimming sensors and pool workouts require sport-specific pool-length configuration and post-workout lap reconciliation.
* **Priority**: Medium
* **Verification Method**: Test (`SystemLiveTrackingRequirementsContractTest.kt`, `SwimWorkoutAdjustCard.kt`, `PolarVeritySenseOfflineController.kt`, `FitFileWriter.kt`)
* **Traced Software Components**: `SwimWorkoutAdjustCard.kt`, `SwimmingSection.kt`, `PolarVeritySenseOfflineController.kt`, `FitFileWriter.kt`, `WorkoutRepository.kt`

### SYSREQ-TRK-012: Live HRV / RR-Interval Streaming & DFA Alpha-1 Threshold Estimation
* **Title**: Live HRV / RR-Interval Streaming & DFA Alpha-1 Threshold Estimation
* **Statement**: The system SHALL compute real-time heart rate variability (HRV) and Detrended Fluctuation Analysis (DFA alpha-1) aerobic threshold indicators from RR-interval streams:
  1. Validate RR-interval signal quality (`HrvSignalQualityAnalyzer.kt`), compute rolling DFA alpha-1 (`DfaCalculator.kt`, `HRVMetricsDevice.kt`, `DfaA1Formatter.kt`), and display live zone indicators (`HrvZoneIndicator.kt`, `HrvInfoBottomSheet.kt`).
  2. Support guided HRV ramp-test protocols (`HrvRampTestScreen.kt`, `HrvRampTestViewModel.kt`) and post-workout threshold/durability estimation (`HrvThresholdEstimator.kt`, `HrvDurabilityCalculator.kt`, `HrvPostWorkoutRecomputer.kt`).
* **Rationale**: Real-time DFA alpha-1 and RR artifact filtering allow athletes to pace aerobic threshold efforts without invasive lactate sampling.
* **Priority**: Medium
* **Verification Method**: Test (`SystemLiveTrackingRequirementsContractTest.kt`, `DfaCalculator.kt`, `HrvSignalQualityAnalyzer.kt`, `HrvRampTestScreen.kt`)
* **Traced Software Components**: `DfaCalculator.kt`, `HRVMetricsDevice.kt`, `HrvSignalQualityAnalyzer.kt`, `HrvRampTestScreen.kt`, `HrvRampTestViewModel.kt`, `HrvThresholdEstimator.kt`, `HrvDurabilityCalculator.kt`, `HrvPostWorkoutRecomputer.kt`, `HrvZoneIndicator.kt`

### SYSREQ-TRK-013: Three-Tier Process-Death Session Recovery & State Restoration
* **Title**: Three-Tier Process-Death Session Recovery & State Restoration
* **Statement**: The system SHALL detect and recover interrupted workout sessions across OS process termination or service recreation:
  1. Persist live session state and accumulated sensor baselines (`TrackerService.java`, `WorkoutSamplesDatabaseManager.java`) to restore interrupted workouts across `MANUAL_PAUSED`, `AUTO_PAUSED`, and `TRACKING` states without losing prior elapsed time, distance, or lap splits.
  2. Prompt the athlete for explicit resume or discard action via `StartOrResumeDialog.kt` (`StartOrResumeInterface.java`) when an unfinished session exists on startup.
* **Rationale**: Android background memory pressure or battery kills must never silently discard multi-hour workout telemetry already persisted to SQLite.
* **Priority**: High
* **Verification Method**: Test (`SystemLiveTrackingRequirementsContractTest.kt`, `TrackerService.java`, `StartOrResumeDialog.kt`)
* **Traced Software Components**: `TrackerService.java`, `StartOrResumeDialog.kt`, `StartOrResumeInterface.java`, `WorkoutSamplesDatabaseManager.java`, `WorkoutSummariesDatabaseManager.java`

---

## 3. System Requirements & Software Code Grounding Matrix

| System Requirement | Primary Capabilities | Traced Software Classes | Grounded Constants / Parameters |
| :--- | :--- | :--- | :--- |
| `SYSREQ-TRK-001` | BLE GATT & ANT+ sensor acquisition | `REQ-CON-001`, `REQ-CON-002`, `REQ-CON-003`, `REQ-CON-004`, `REQ-CON-005`, `REQ-CON-006`, `REQ-CON-007`, `REQ-CON-009`, `REQ-CON-010`, `REQ-CON-011`, `REQ-CON-017`, `REQ-CON-028`, `REQ-CON-044`, `REQ-CON-045`, `REQ-CON-049`, `REQ-CON-050`, `REQ-FIL-029`, `REQ-SEN-020`, `REQ-SEN-021`, `REQ-SEN-025`, `REQ-SEN-026`, `REQ-SEN-027`, `REQ-SEN-028`, `REQ-SEN-029`, `REQ-SEN-030`, `REQ-SEN-031`, `REQ-TRK-083`, `REQ-TRK-085`, `REQ-TRK-088` | `SERVICE_HEART_RATE`, `SERVICE_CYCLING_POWER`, `SERVICE_CYCLING_SPEED_AND_CADENCE`, `SERVICE_RADAR` (`6a4e3200...`) |
| `SYSREQ-TRK-002` | Exponential backoff reconnect, radar watchdog | `REQ-CON-005`, `REQ-CON-012`, `REQ-CON-018`, `REQ-CON-019`, `REQ-CON-020`, `REQ-CON-023`, `REQ-CON-024`, `REQ-CON-025`, `REQ-CON-026`, `REQ-CON-027`, `REQ-CON-028`, `REQ-CON-029`, `REQ-CON-030`, `REQ-CON-031`, `REQ-CON-032`, `REQ-CON-033`, `REQ-CON-035`, `REQ-CON-036`, `REQ-CON-037`, `REQ-CON-038`, `REQ-CON-039`, `REQ-CON-040`, `REQ-CON-041`, `REQ-CON-042`, `REQ-CON-043`, `REQ-CON-044`, `REQ-CON-045`, `REQ-CON-046`, `REQ-CON-047`, `REQ-CON-048`, `REQ-CON-049`, `REQ-CON-050`, `REQ-TRK-013` | `INITIAL_RECONNECT_DELAY_MS = 1000L`, `MAX_RECONNECT_DELAY_MS = 16000L`, `WATCHDOG_TIMEOUT_MS = 3500L` |
| `SYSREQ-TRK-003` | Deterministic 1-Hz sampling loop | `REQ-FIL-001`, `REQ-FIL-002`, `REQ-FIL-003`, `REQ-FIL-004`, `REQ-FIL-005`, `REQ-FIL-006`, `REQ-FIL-007`, `REQ-FIL-008`, `REQ-FIL-009`, `REQ-FIL-010`, `REQ-FIL-011`, `REQ-FIL-012`, `REQ-FIL-013`, `REQ-FIL-014`, `REQ-FIL-015`, `REQ-FIL-016`, `REQ-FIL-017`, `REQ-FIL-018`, `REQ-FIL-019`, `REQ-FIL-020`, `REQ-FIL-021`, `REQ-FIL-022`, `REQ-FIL-023`, `REQ-FIL-024`, `REQ-FIL-025`, `REQ-FIL-026`, `REQ-FIL-027`, `REQ-FIL-028`, `REQ-FIL-029`, `REQ-FIL-030`, `REQ-FIL-031`, `REQ-FIL-032`, `REQ-FIL-033`, `REQ-FIL-034`, `REQ-FIL-035`, `REQ-FIL-036`, `REQ-FIL-037`, `REQ-SEN-020`, `REQ-SEN-021`, `REQ-SEN-025`, `REQ-SEN-028`, `REQ-SEN-029`, `REQ-SEN-031`, `REQ-TRK-001`, `REQ-TRK-002`, `REQ-TRK-004`, `REQ-TRK-007`, `REQ-TRK-009`, `REQ-TRK-010`, `REQ-TRK-069`, `REQ-TRK-083`, `REQ-TRK-088` | `scheduleAtFixedRate(..., 1, TimeUnit.SECONDS)`, `mDbExecutor` |
| `SYSREQ-TRK-004` | GNSS acquisition & null-island filtering | `REQ-CLM-008`, `REQ-CLM-009`, `REQ-CLM-010`, `REQ-CLM-011`, `REQ-FIL-003`, `REQ-FIL-004`, `REQ-FIL-006`, `REQ-FIL-007`, `REQ-FIL-009`, `REQ-FIL-010`, `REQ-FIL-012`, `REQ-FIL-013`, `REQ-FIL-016`, `REQ-FIL-017`, `REQ-FIL-018`, `REQ-FIL-019`, `REQ-FIL-020`, `REQ-FIL-021`, `REQ-FIL-022`, `REQ-FIL-023`, `REQ-FIL-024`, `REQ-FIL-025`, `REQ-FIL-026`, `REQ-FIL-027`, `REQ-FIL-028`, `REQ-FIL-030`, `REQ-FIL-032`, `REQ-FIL-033`, `REQ-FIL-034`, `REQ-FIL-035`, `REQ-FIL-036`, `REQ-FIL-037`, `REQ-GEO-001`, `REQ-GEO-002`, `REQ-MAP-003`, `REQ-MAP-004`, `REQ-MAP-005`, `REQ-MAP-008`, `REQ-MAP-009`, `REQ-MAP-011`, `REQ-MAP-012`, `REQ-MAP-016`, `REQ-MAP-018`, `REQ-MAP-019`, `REQ-MAP-020`, `REQ-MAP-021`, `REQ-MAP-022`, `REQ-MAP-023`, `REQ-MAP-024`, `REQ-MAP-025`, `REQ-MAP-026`, `REQ-MAP-027`, `REQ-MAP-028`, `REQ-MAP-029`, `REQ-MAP-030`, `REQ-MAP-031`, `REQ-MAP-032`, `REQ-MAP-033`, `REQ-MAP-034`, `REQ-MAP-035`, `REQ-MAP-036`, `REQ-MAP-037`, `REQ-MAP-038`, `REQ-MAP-039`, `REQ-MAP-040`, `REQ-MAP-041`, `REQ-MAP-042`, `REQ-MAP-044`, `REQ-MAP-045`, `REQ-MAP-046`, `REQ-MAP-047`, `REQ-MAP-048`, `REQ-MAP-049`, `REQ-MAP-050`, `REQ-MAP-051`, `REQ-MAP-052`, `REQ-MAP-053`, `REQ-MAP-054`, `REQ-MAP-055`, `REQ-MAP-056`, `REQ-MAP-057`, `REQ-MAP-058`, `REQ-MAP-059`, `REQ-MAP-060`, `REQ-MAP-061`, `REQ-MAP-062`, `REQ-MAP-063`, `REQ-MAP-064`, `REQ-MAP-065`, `REQ-MAP-066`, `REQ-MAP-067`, `REQ-NAV-043`, `REQ-NAV-056`, `REQ-TRK-003`, `REQ-TRK-011`, `REQ-TRK-012`, `REQ-TRK-013`, `REQ-TRK-014`, `REQ-TRK-017`, `REQ-TRK-022`, `REQ-TRK-024`, `REQ-TRK-025`, `REQ-TRK-026`, `REQ-TRK-029`, `REQ-TRK-030`, `REQ-TRK-031`, `REQ-TRK-032`, `REQ-TRK-034`, `REQ-TRK-036`, `REQ-TRK-037`, `REQ-TRK-039`, `REQ-TRK-040`, `REQ-TRK-041`, `REQ-TRK-042`, `REQ-TRK-043`, `REQ-TRK-044`, `REQ-TRK-047`, `REQ-TRK-048`, `REQ-TRK-049`, `REQ-TRK-050`, `REQ-TRK-052`, `REQ-TRK-055`, `REQ-TRK-056`, `REQ-TRK-057`, `REQ-TRK-062`, `REQ-TRK-066`, `REQ-TRK-070`, `REQ-TRK-071`, `REQ-TRK-072`, `REQ-TRK-074`, `REQ-TRK-075`, `REQ-TRK-076`, `REQ-TRK-077`, `REQ-TRK-078`, `REQ-TRK-079`, `REQ-TRK-080`, `REQ-TRK-081`, `REQ-TRK-084` | `LocationManager.GPS_PROVIDER`, `GoogleApiClient`, `LocationListener` |
| `SYSREQ-TRK-005` | Foreground service, WakeLock, power governance | `REQ-FIL-025`, `REQ-FIL-027`, `REQ-TRK-003`, `REQ-TRK-005`, `REQ-TRK-008`, `REQ-TRK-015`, `REQ-TRK-016`, `REQ-TRK-023`, `REQ-TRK-028`, `REQ-TRK-033`, `REQ-TRK-045`, `REQ-TRK-046`, `REQ-TRK-051`, `REQ-TRK-053`, `REQ-TRK-058`, `REQ-TRK-059`, `REQ-TRK-063`, `REQ-TRK-064` | `PowerManager.PARTIAL_WAKE_LOCK`, `FOREGROUND_SERVICE_TYPE_LOCATION`, `FOREGROUND_SERVICE_TYPE_HEALTH` |
| `SYSREQ-TRK-006` | Cockpit tiles, radar visual/audio warnings | `REQ-CON-013`, `REQ-CON-014`, `REQ-CON-015`, `REQ-CON-016`, `REQ-CON-021`, `REQ-CON-022`, `REQ-CON-028`, `REQ-CON-034`, `REQ-FIL-031`, `REQ-GEO-001`, `REQ-GEO-002`, `REQ-NAV-043`, `REQ-NAV-056`, `REQ-RAD-005`, `REQ-TRK-018`, `REQ-TRK-019`, `REQ-TRK-020`, `REQ-TRK-021`, `REQ-TRK-027`, `REQ-TRK-035`, `REQ-TRK-038`, `REQ-TRK-054`, `REQ-TRK-060`, `REQ-TRK-061`, `REQ-TRK-065`, `REQ-TRK-067`, `REQ-TRK-068`, `REQ-TRK-073`, `REQ-TRK-082`, `REQ-TRK-084`, `REQ-TRK-085`, `REQ-TRK-086`, `REQ-TRK-087` | `USAGE_ALARM`, `USAGE_MEDIA`, radar threat levels |
| `SYSREQ-TRK-007` | Bidirectional BLE/ANT+ radar & light control | `REQ-CON-035`, `REQ-CON-043`, `REQ-CON-046`, `REQ-CON-047`, `REQ-CON-048`, `REQ-TRK-064`, `REQ-RAD-005` | `6a4e3202`, `6a4e2f00`/`6a4e2f11`/`6a4e2f14`, ANT+ `0x28`/`0x23`, `Sensor.TYPE_LIGHT` |
| `SYSREQ-TRK-008` | Barometer, EKF altitude/slope, climbs & segments | `REQ-CON-017`, `REQ-FIL-037`, `REQ-SEN-025`, `REQ-SEN-026`, `REQ-CLM-008`, `REQ-CLM-009`, `REQ-CLM-010`, `REQ-CLM-011` | `Sensor.TYPE_PRESSURE`, `KinematicAltitudeSlopeEkf`, `ClimbDetector` |
| `SYSREQ-TRK-009` | GPX follow-me & turn-by-turn audio navigation | `REQ-MAP-003`, `REQ-NAV-043`, `REQ-NAV-056`, `REQ-GEO-001`, `REQ-GEO-002` | `GpxRouteImporter`, `TurnByTurnNavigationEngine`, `RouteDivergenceDetector` |
| `SYSREQ-TRK-010` | Smartphone IMU kinematics, carriage classification, cadence, odometry & iTPMS | `REQ-FIL-028`, `REQ-FIL-029`, `REQ-FIL-030`, `REQ-FIL-031`, `REQ-FIL-034`, `REQ-FIL-035`, `REQ-FIL-036`, `REQ-TRK-050`, `REQ-TRK-077`, `REQ-SEN-028`, `REQ-SEN-029`, `REQ-SEN-030`, `REQ-SEN-031` | `TYPE_GYROSCOPE`, `TYPE_ROTATION_VECTOR`, `TYPE_LINEAR_ACCELERATION`, `TYPE_ACCELEROMETER`, `TYPE_STEP_DETECTOR` |
| `SYSREQ-TRK-011` | Pool/open-water swimming telemetry & lap adjust | `REQ-TRK-084`, `REQ-DAT-017` | `SwimWorkoutAdjustCard`, `SwimmingSection`, `Sport.SWIMMING` |
| `SYSREQ-TRK-012` | Live HRV/RR streaming & DFA alpha-1 thresholds | `REQ-TRK-060`, `REQ-TRK-061`, `REQ-TRK-067`, `REQ-TRK-072`, `REQ-TRK-082`, `REQ-TRK-085` | `DfaCalculator`, `HrvSignalQualityAnalyzer`, `HrvThresholdEstimator` |
| `SYSREQ-TRK-013` | Three-tier process-death session recovery | `REQ-TRK-005`, `REQ-TRK-018`, `REQ-TRK-026`, `REQ-TRK-031`, `REQ-TRK-033`, `REQ-TRK-036`, `REQ-TRK-078` | `MANUAL_PAUSED`, `AUTO_PAUSED`, `TRACKING`, `StartOrResumeDialog` |

---

## 4. System-to-Software Requirement Traceability Matrix (SYS.2 BP5 -> SWE.1 BP5)

The following matrix establishes bidirectional traceability between system tracking requirements and concrete refining software requirements in `docs/requirements/req_tracking_cockpit_part1.md` and `docs/requirements/req_tracking_cockpit_part2.md`:

| System Requirement | System Title | Refining Software Requirements (`REQ-*`) | Grounded Classes & Verification Test |
| :--- | :--- | :--- | :--- |
| `SYSREQ-TRK-001` | Multi-Sensor Profile Interoperability | `REQ-CON-001`, `REQ-CON-002`, `REQ-CON-003`, `REQ-CON-004`, `REQ-CON-005`, `REQ-CON-006`, `REQ-CON-007`, `REQ-CON-009`, `REQ-CON-010`, `REQ-CON-011`, `REQ-CON-017`, `REQ-CON-028`, `REQ-CON-044`, `REQ-CON-045`, `REQ-CON-049`, `REQ-CON-050`, `REQ-FIL-029`, `REQ-SEN-020`, `REQ-SEN-021`, `REQ-SEN-025`, `REQ-SEN-026`, `REQ-SEN-027`, `REQ-SEN-028`, `REQ-SEN-029`, `REQ-SEN-030`, `REQ-SEN-031`, `REQ-TRK-083`, `REQ-TRK-085`, `REQ-TRK-088` | `BANALService.java`, `BluetoothConstants.java`, `BTLERadarDevice.java`, `MySensorManager.java`, `DeviceType.java`, `SystemLiveTrackingRequirementsContractTest.kt` |
| `SYSREQ-TRK-002` | Reconnection Resilience & Watchdog | `REQ-CON-005`, `REQ-CON-012`, `REQ-CON-018`, `REQ-CON-019`, `REQ-CON-020`, `REQ-CON-023`, `REQ-CON-024`, `REQ-CON-025`, `REQ-CON-026`, `REQ-CON-027`, `REQ-CON-028`, `REQ-CON-029`, `REQ-CON-030`, `REQ-CON-031`, `REQ-CON-032`, `REQ-CON-033`, `REQ-CON-035`, `REQ-CON-036`, `REQ-CON-037`, `REQ-CON-038`, `REQ-CON-039`, `REQ-CON-040`, `REQ-CON-041`, `REQ-CON-042`, `REQ-CON-043`, `REQ-CON-044`, `REQ-CON-045`, `REQ-CON-046`, `REQ-CON-047`, `REQ-CON-048`, `REQ-CON-049`, `REQ-CON-050`, `REQ-TRK-013` | `MyBTLEDevice.java`, `BTLERadarDevice.java`, `FilterManager.kt`, `SystemLiveTrackingRequirementsContractTest.kt` |
| `SYSREQ-TRK-003` | Deterministic 1-Hz Sampling Loop | `REQ-FIL-001`, `REQ-FIL-002`, `REQ-FIL-003`, `REQ-FIL-004`, `REQ-FIL-005`, `REQ-FIL-006`, `REQ-FIL-007`, `REQ-FIL-008`, `REQ-FIL-009`, `REQ-FIL-010`, `REQ-FIL-011`, `REQ-FIL-012`, `REQ-FIL-013`, `REQ-FIL-014`, `REQ-FIL-015`, `REQ-FIL-016`, `REQ-FIL-017`, `REQ-FIL-018`, `REQ-FIL-019`, `REQ-FIL-020`, `REQ-FIL-021`, `REQ-FIL-022`, `REQ-FIL-023`, `REQ-FIL-024`, `REQ-FIL-025`, `REQ-FIL-026`, `REQ-FIL-027`, `REQ-FIL-028`, `REQ-FIL-029`, `REQ-FIL-030`, `REQ-FIL-031`, `REQ-FIL-032`, `REQ-FIL-033`, `REQ-FIL-034`, `REQ-FIL-035`, `REQ-FIL-036`, `REQ-FIL-037`, `REQ-SEN-020`, `REQ-SEN-021`, `REQ-SEN-025`, `REQ-SEN-028`, `REQ-SEN-029`, `REQ-SEN-031`, `REQ-TRK-001`, `REQ-TRK-002`, `REQ-TRK-004`, `REQ-TRK-007`, `REQ-TRK-009`, `REQ-TRK-010`, `REQ-TRK-069`, `REQ-TRK-083`, `REQ-TRK-088` | `TrackerService.java`, `WorkoutSamplesDatabaseManager.java`, `SystemLiveTrackingRequirementsContractTest.kt` |
| `SYSREQ-TRK-004` | Multi-Source GNSS Positioning | `REQ-CLM-008`, `REQ-CLM-009`, `REQ-CLM-010`, `REQ-CLM-011`, `REQ-FIL-003`, `REQ-FIL-004`, `REQ-FIL-006`, `REQ-FIL-007`, `REQ-FIL-009`, `REQ-FIL-010`, `REQ-FIL-012`, `REQ-FIL-013`, `REQ-FIL-016`, `REQ-FIL-017`, `REQ-FIL-018`, `REQ-FIL-019`, `REQ-FIL-020`, `REQ-FIL-021`, `REQ-FIL-022`, `REQ-FIL-023`, `REQ-FIL-024`, `REQ-FIL-025`, `REQ-FIL-026`, `REQ-FIL-027`, `REQ-FIL-028`, `REQ-FIL-030`, `REQ-FIL-032`, `REQ-FIL-033`, `REQ-FIL-034`, `REQ-FIL-035`, `REQ-FIL-036`, `REQ-FIL-037`, `REQ-GEO-001`, `REQ-GEO-002`, `REQ-MAP-003`, `REQ-MAP-004`, `REQ-MAP-005`, `REQ-MAP-008`, `REQ-MAP-009`, `REQ-MAP-011`, `REQ-MAP-012`, `REQ-MAP-016`, `REQ-MAP-018`, `REQ-MAP-019`, `REQ-MAP-020`, `REQ-MAP-021`, `REQ-MAP-022`, `REQ-MAP-023`, `REQ-MAP-024`, `REQ-MAP-025`, `REQ-MAP-026`, `REQ-MAP-027`, `REQ-MAP-028`, `REQ-MAP-029`, `REQ-MAP-030`, `REQ-MAP-031`, `REQ-MAP-032`, `REQ-MAP-033`, `REQ-MAP-034`, `REQ-MAP-035`, `REQ-MAP-036`, `REQ-MAP-037`, `REQ-MAP-038`, `REQ-MAP-039`, `REQ-MAP-040`, `REQ-MAP-041`, `REQ-MAP-042`, `REQ-MAP-044`, `REQ-MAP-045`, `REQ-MAP-046`, `REQ-MAP-047`, `REQ-MAP-048`, `REQ-MAP-049`, `REQ-MAP-050`, `REQ-MAP-051`, `REQ-MAP-052`, `REQ-MAP-053`, `REQ-MAP-054`, `REQ-MAP-055`, `REQ-MAP-056`, `REQ-MAP-057`, `REQ-MAP-058`, `REQ-MAP-059`, `REQ-MAP-060`, `REQ-MAP-061`, `REQ-MAP-062`, `REQ-MAP-063`, `REQ-MAP-064`, `REQ-MAP-065`, `REQ-MAP-066`, `REQ-MAP-067`, `REQ-NAV-043`, `REQ-NAV-056`, `REQ-TRK-003`, `REQ-TRK-011`, `REQ-TRK-012`, `REQ-TRK-013`, `REQ-TRK-014`, `REQ-TRK-017`, `REQ-TRK-022`, `REQ-TRK-024`, `REQ-TRK-025`, `REQ-TRK-026`, `REQ-TRK-029`, `REQ-TRK-030`, `REQ-TRK-031`, `REQ-TRK-032`, `REQ-TRK-034`, `REQ-TRK-036`, `REQ-TRK-037`, `REQ-TRK-039`, `REQ-TRK-040`, `REQ-TRK-041`, `REQ-TRK-042`, `REQ-TRK-043`, `REQ-TRK-044`, `REQ-TRK-047`, `REQ-TRK-048`, `REQ-TRK-049`, `REQ-TRK-050`, `REQ-TRK-052`, `REQ-TRK-055`, `REQ-TRK-056`, `REQ-TRK-057`, `REQ-TRK-062`, `REQ-TRK-066`, `REQ-TRK-070`, `REQ-TRK-071`, `REQ-TRK-072`, `REQ-TRK-074`, `REQ-TRK-075`, `REQ-TRK-076`, `REQ-TRK-077`, `REQ-TRK-078`, `REQ-TRK-079`, `REQ-TRK-080`, `REQ-TRK-081`, `REQ-TRK-084` | `SpeedAndLocationDevice_GPS.java`, `SpeedAndLocationDevice_GoogleFused.java`, `SystemLiveTrackingRequirementsContractTest.kt` |
| `SYSREQ-TRK-005` | Foreground Service & Power Management | `REQ-FIL-025`, `REQ-FIL-027`, `REQ-TRK-003`, `REQ-TRK-005`, `REQ-TRK-008`, `REQ-TRK-015`, `REQ-TRK-016`, `REQ-TRK-023`, `REQ-TRK-028`, `REQ-TRK-033`, `REQ-TRK-045`, `REQ-TRK-046`, `REQ-TRK-051`, `REQ-TRK-053`, `REQ-TRK-058`, `REQ-TRK-059`, `REQ-TRK-063`, `REQ-TRK-064` | `TrackerService.java`, `TrainingApplication.java`, `SystemLiveTrackingRequirementsContractTest.kt` |
| `SYSREQ-TRK-006` | Real-Time Alerts & Cockpit Feedback | `REQ-CON-013`, `REQ-CON-014`, `REQ-CON-015`, `REQ-CON-016`, `REQ-CON-021`, `REQ-CON-022`, `REQ-CON-028`, `REQ-CON-034`, `REQ-FIL-031`, `REQ-GEO-001`, `REQ-GEO-002`, `REQ-NAV-043`, `REQ-NAV-056`, `REQ-RAD-005`, `REQ-TRK-018`, `REQ-TRK-019`, `REQ-TRK-020`, `REQ-TRK-021`, `REQ-TRK-027`, `REQ-TRK-035`, `REQ-TRK-038`, `REQ-TRK-054`, `REQ-TRK-060`, `REQ-TRK-061`, `REQ-TRK-065`, `REQ-TRK-067`, `REQ-TRK-068`, `REQ-TRK-073`, `REQ-TRK-082`, `REQ-TRK-084`, `REQ-TRK-085`, `REQ-TRK-086`, `REQ-TRK-087` | `SensorGridScreen.kt`, `RadarEdgeOverlay.kt`, `RadarAlertManager.kt`, `SystemLiveTrackingRequirementsContractTest.kt` |
| `SYSREQ-TRK-007` | Bidirectional Radar & Light Actuation | `REQ-CON-035`, `REQ-CON-043`, `REQ-CON-046`, `REQ-CON-047`, `REQ-CON-048`, `REQ-TRK-064`, `REQ-RAD-005` | `RadarLightController.kt`, `SmartBrakeLightDetector.kt`, `ANTBikeLightDevice.kt`, `SystemLiveTrackingRequirementsContractTest.kt` |
| `SYSREQ-TRK-008` | Barometer, EKF & Climb/Segment Fusion | `REQ-CON-017`, `REQ-FIL-037`, `REQ-SEN-025`, `REQ-SEN-026`, `REQ-CLM-008`, `REQ-CLM-009`, `REQ-CLM-010`, `REQ-CLM-011` | `KinematicAltitudeSlopeEkf.kt`, `ClimbDetector.kt`, `LiveClimbsRepository.kt`, `SystemLiveTrackingRequirementsContractTest.kt` |
| `SYSREQ-TRK-009` | GPX Follow-Me & Turn Audio Nav | `REQ-MAP-003`, `REQ-NAV-043`, `REQ-NAV-056`, `REQ-GEO-001`, `REQ-GEO-002` | `GpxRouteImporter.kt`, `TurnByTurnNavigationEngine.kt`, `NavigationAudioAlertManager.kt`, `SystemLiveTrackingRequirementsContractTest.kt` |
| `SYSREQ-TRK-010` | Smartphone IMU, Carriage, Cadence & Indirect TPMS | `REQ-FIL-028`, `REQ-FIL-029`, `REQ-FIL-030`, `REQ-FIL-031`, `REQ-FIL-034`, `REQ-FIL-035`, `REQ-FIL-036`, `REQ-TRK-050`, `REQ-TRK-077`, `REQ-SEN-028`, `REQ-SEN-029`, `REQ-SEN-030`, `REQ-SEN-031` | `SpeedAndLocationDevice_ATTFused.kt`, `PhoneCarriageClassifier.kt`, `ImuCadenceDsp.kt`, `SmartphoneImuCadenceEstimator.kt`, `KinematicSportTypeDiscriminator.kt`, `PolarLiveRunningCadenceEstimator.kt`, `QuaternionGyroProjector.kt`, `IndirectTpmsEstimator.kt`, `DualWheelTpmsEstimator.kt` |
| `SYSREQ-TRK-011` | Swimming Telemetry & Lap Adjust | `REQ-TRK-084`, `REQ-DAT-017` | `SwimWorkoutAdjustCard.kt`, `SwimmingSection.kt`, `PolarVeritySenseOfflineController.kt`, `FitFileWriter.kt` |
| `SYSREQ-TRK-012` | Live HRV & DFA Alpha-1 Thresholds | `REQ-TRK-060`, `REQ-TRK-061`, `REQ-TRK-067`, `REQ-TRK-072`, `REQ-TRK-082`, `REQ-TRK-085` | `DfaCalculator.kt`, `HRVMetricsDevice.kt`, `HrvSignalQualityAnalyzer.kt`, `HrvThresholdEstimator.kt` |
| `SYSREQ-TRK-013` | Process-Death Session Recovery | `REQ-TRK-005`, `REQ-TRK-018`, `REQ-TRK-026`, `REQ-TRK-031`, `REQ-TRK-033`, `REQ-TRK-036`, `REQ-TRK-078` | `TrackerService.java`, `StartOrResumeDialog.kt`, `WorkoutSamplesDatabaseManager.java` |

---

## Change History

| Version | Date | Author | Ticket | Summary of Changes |
| :--- | :--- | :--- | :--- | :--- |
| `1.0.0` | 2026-11-16 | AI Agent | `ATT-6142` | Prior system specification baseline. |
| `1.1.0` | 2026-11-17 | AI Agent (`ATT-8164`) | `ATT-8164` | Standardized `DOC-SYS-TRK` header, bumped version, reset status to `Reviewed` (`Pending PO Sign-Off`). |
| `1.2.0` | 2026-11-18 | AI Agent (`ATT-8201`) | `ATT-8201` | Added `SYSREQ-TRK-007` through `SYSREQ-TRK-013` (bidirectional radar/light actuation, barometer/EKF/climbs, GPX/audio nav, smartphone IMU/iTPMS, swimming, HRV/DFA alpha-1, 3-tier recovery) and updated matrices. |
| `1.2.1` | 2026-11-18 | AI Agent (`ATT-8201`) | `ATT-8201` | Cold Stage 6 heal: added `Sensor.TYPE_LIGHT` to `SYSREQ-TRK-007`, synchronized §3/§4 `REQ-*` links for `SYSREQ-TRK-001..006`, and updated pre-split shard references to `req_tracking_cockpit_part1/2.md`. |
| `1.2.2` | 2026-11-18 | AI Agent (`ATT-8890`, `--as agent1`) | `ATT-8890` | Updated `SYSREQ-TRK-010` for opportunistically gated `Sensor.TYPE_STEP_DETECTOR`, `PhoneCarriageMode`, `PhoneCarriageClassifier`, `SensorType.ACTIVITY_AND_CARRIAGE_MODE`, and adaptive EKF/ERTS filtering; added `REQ-FIL-028` to `SYSREQ-TRK-003`/`004`/`010` and `REQ-FIL-027` parity in §3/§4. |
| `1.2.3` | 2026-11-18 | AI Agent (`ATT-8895`, `--as agent1`) | `ATT-8895` | Updated `SYSREQ-TRK-010` for carriage-classified cycling/running smartphone IMU cadence (`ImuCadenceDsp.kt`, `SmartphoneImuCadenceEstimator.kt`, `PolarLiveRunningCadenceEstimator.kt`, `MySensorManager.java`, `DeviceType.java`), added `REQ-FIL-029` to `SYSREQ-TRK-001`/`003`/`010`, and synchronized §3/§4 parity. |
| `1.2.4` | 2026-11-19 | AI Agent (`ATT-8891`, `--as agent1`) | `ATT-8891` | Updated `SYSREQ-TRK-010` for kinematic `BSportType` discrimination and stop-filtered moving-speed identity (`KinematicSportTypeDiscriminator.kt`), added `REQ-FIL-030` to `SYSREQ-TRK-003`/`004`/`010`, and synchronized §3/§4 parity. |
| `1.2.5` | 2026-11-19 | AI Agent (`ATT-8917`, `--as agent1`) | `ATT-8917` | Updated `SYSREQ-TRK-010` for multisport transition detection, retroactive dismount/mount anchoring & automatic named laps (`MultisportTransitionDetector.kt`, `MultisportTransitionBridge.kt`, `MultisportLapCoordinator.kt`), added `REQ-FIL-031` to `SYSREQ-TRK-003`/`006`/`010`, and synchronized §3/§4 parity. |
| `1.2.6` | 2026-11-19 | AI Agent (`ATT-9064`, `--as agent1`) | `ATT-9064` | Linked `REQ-FIL-032` (`ErtsPostWorkoutRecomputer.kt`, `ExtendedRtsOdometrySmoother.kt`, `UnicycleOdometryEkf.kt`) under `SYSREQ-TRK-003` and `SYSREQ-TRK-004` in §3/§4 parity. |
| `1.2.7` | 2026-11-19 | AI Agent (`ATT-9063`, `--as agent1`) | `ATT-9063` | Linked `REQ-FIL-033` (`ExtendedRtsAltitudeSlopeSmoother.kt`, `BarometricAltitudeFilter.kt`, `ErtsPostWorkoutRecomputer.kt`) under `SYSREQ-TRK-003` and `SYSREQ-TRK-004` in §3/§4 parity. |
| `1.2.8` | 2026-11-20 | AI Agent (`ATT-9062`, `--as agent1`) | `ATT-9062` | Linked `REQ-FIL-034` (`PhoneCarriageClassifier.kt`, `SpeedAndLocationDevice_ATTFused.kt`) under `SYSREQ-TRK-003`, `SYSREQ-TRK-004`, and `SYSREQ-TRK-010` in §3/§4 parity. |
| `1.2.9` | 2026-11-20 | AI Agent (`ATT-9157`, `--as agent1`) | `ATT-9157` | Linked `REQ-FIL-035` (`PhoneCarriageClassifier.kt`) under `SYSREQ-TRK-003`, `SYSREQ-TRK-004`, and `SYSREQ-TRK-010` in §3/§4 parity. |
| `1.2.10` | 2026-11-20 | AI Agent (`ATT-9158`, `--as agent1`) | `ATT-9158` | Linked `REQ-FIL-036` (`SpeedAndLocationDevice_ATTFused.kt`, `SpeedAndLocationDevice.java`, `UnicycleOdometryEkf.kt`, `ExtendedRtsOdometrySmoother.kt`, `ErtsPostWorkoutRecomputer.kt`) under `SYSREQ-TRK-003`, `SYSREQ-TRK-004`, and `SYSREQ-TRK-010` in §3/§4 parity. |
| `1.2.11` | 2026-11-20 | AI Agent (`ATT-9156`, `--as agent1`) | `ATT-9156` | Linked `REQ-FIL-037` (`KinematicAltitudeSlopeEkf.kt`, `AltitudeAndSlopeDevice_ATTFused.kt`) under `SYSREQ-TRK-003`, `SYSREQ-TRK-004`, and `SYSREQ-TRK-008` in §3/§4 parity. |
