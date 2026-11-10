# Living Requirements: Tracking & Live Sensor Cockpit

**Domain**: `tracking_cockpit`  
**Prefixes**: `CON`, `FIL`, `FUSION`, `SEN`, `TRK`, `WEA`  
**Scope**: Live sensor telemetry, Bluetooth/ANT+ connectivity, filtering, GPS/tracking lifecycle.  
**Total Requirements**: 84

---

### REQ-CON-001: Support multiple ANT+ sensor profiles (HR, Speed, Cadence, Power, Env) [Verified]
- **Tests**: `TST-MAN-001`
- **Implementation**: `BANALService.java`, `ant_plus/`
- **Rationale**: Provide comprehensive data for diverse athletic activities.
- **Description**: Support multiple ANT+ sensor profiles (HR, Speed, Cadence, Power, Env).

### REQ-CON-002: Support Bluetooth LE (BLE) sensor profiles (HR, Speed, Cadence, Power) [Verified]
- **Tests**: `TST-MAN-001`
- **Implementation**: `BANALService.java`, `bluetooth_le/`
- **Rationale**: Ensure compatibility with modern fitness hardware.
- **Description**: Support Bluetooth LE (BLE) sensor profiles (HR, Speed, Cadence, Power).

### REQ-CON-003: Implement "Best Sensor" selection logic using priority lists [Verified]
- **Tests**: `TST-MAN-001`
- **Implementation**: `MySensorManager.java`
- **Rationale**: Maximize data quality by preferring hardware over GPS.
- **Description**: Implement "Best Sensor" selection logic using priority lists.

### REQ-CON-004: Provide asynchronous background searching for devices [Verified]
- **Tests**: `TST-MAN-001`
- **Implementation**: `DeviceManager.java`
- **Rationale**: Allow users to discover sensors without interrupting app use.
- **Description**: Provide asynchronous background searching for devices.

### REQ-CON-005: All BLE characteristic reads must be null-safe [Verified]
- **Tests**: `TST-STR-001`
- **Implementation**: `MyBTLEDevice.java`
- **Rationale**: Prevent application crashes when communicating with hardware.
- **Description**: All BLE characteristic reads must be null-safe.

### REQ-CON-006: Support barometric pressure sensors for altitude [Verified]
- **Tests**: `TST-MAN-001`
- **Implementation**: `AltitudeFromPressureDevice.java`
- **Rationale**: Provide precise elevation data where GPS is insufficient.
- **Description**: Support barometric pressure sensors for altitude.

### REQ-CON-007: Continuous Sensor Battery State Persistence, Live Battery Display in Sensors List & Post-Workout Alerting [Verified]
- **Tests**: `MainActivityWithNavigation.kt`, `SensorBatteryNotificationManager.kt`, `TrackerService.java`, `ActiveDevicesDbHelper.java`, `MyBTLEDevice.java`, `GetMergedDevicesUseCase.kt`, `DeviceDataRepository.kt`, `DevicesDatabaseManager.java`
- **Implementation**: Continuous sensor battery persistence, live battery display in sensors list, and dual-channel post-workout low battery alerting.
- **Rationale**: The system SHALL continuously persist remote sensor battery level updates, maintain real-time session participation records in `ActiveDevicesDbHelper`, dynamically reflect live battery levels in "Meine Sensoren", and reliably alert athletes about critical battery levels following a completed workout (ATT-2192, ATT-3312):
1. *Elimination of Modal Disruption*: The system SHALL NOT display modal dialogs (`AlertDialog`) upon workout finalization or stopping (`checkBatteryStatus()`).
2. *Session-Aware Critical Battery Evaluation*:
• Upon tracking finalization (`TRACKING_FINISHED_INTENT`), the system SHALL evaluate the battery levels of remote sensors that were actively connected during the workout session via `ActiveDevicesDbHelper.getDatabaseIdsOfActiveDevices(workoutId)`.
• Evaluation SHALL execute asynchronously on a background I/O dispatcher (`Dispatchers.IO`) to eliminate SQLite query latency on the Main Looper during screen transitions.
• A sensor SHALL be classified as critical if its recorded `LAST_BATTERY_PERCENTAGE` is $\ge 0\%$ AND $\le 20\%$ (`CRITICAL_BATTERY_LEVEL = 20`).
3. *In-App Material 3 Snackbar Presentation*:
• When one or more session sensors are critical, the system SHALL emit a sticky `LowBatteryAlert` event via `WorkoutNavigationEvents` (`replay = 1`, `DROP_OLDEST`).
• Upon composition of the post-workout destination screen (`WorkoutSummariesTabbedScreen`), the screen SHALL collect the alert and display a non-blocking Material 3 `Snackbar`.
• Format: `⚠️ [Sensor Name]: Akku schwach ([X]%)` (single sensor) or `⚠️ [N] Sensoren: Akku schwach ([Names])` (multiple sensors).
• Action button: `[Zu Sensoren]` (EN: `[View Sensors]`). Tapping the action SHALL invoke `navigateToDrawerItem(R.id.drawer_my_sensors)`.
• Once displayed, the alert SHALL be cleared via `WorkoutNavigationEvents.consumeLowBatteryAlert()` to prevent duplicate popups upon recomposition.
4. *Persistent Android System Notification*:
• The system SHALL post an Android system notification on a dedicated notification channel `NOTIFICATION_CHANNEL__SENSOR_BATTERY` (`"NOTIFICATION_CHANNEL__SENSOR_BATTERY"`, importance `IMPORTANCE_DEFAULT`).
• Notification ID: `NOTIFICATION_ID_SENSOR_BATTERY = 4300`.
• Title: Localized `R.string.sensor_battery_notification_title_1` or `R.string.sensor_battery_notification_title_many`.
• Text: Localized sensor name(s) and battery percentage.
• Tap Action: PendingIntent targeting `MainActivityWithNavigation` with extra `SELECTED_FRAGMENT = SelectedFragment.SENSORS.name` / `EXTRA_DRAWER_ITEM_ID = R.id.drawer_my_sensors` to launch directly into "Meine Sensoren" with `EXTRA_FILTER_CRITICAL_BATTERY = true`.
• Behavior: `setAutoCancel(true)`, persisting across screen sleep and lock screen until dismissed or tapped.
5. *Normal Battery Neutrality*:
• When all sensors active in the workout have battery $> 20\%$ (or if no remote sensors were connected), the system SHALL NOT post any system notification or in-app Snackbar.
6. *100% 9-Language Localization Parity*:
• All notification channel names, descriptions, notification titles, notification text, snackbar messages, and action buttons SHALL maintain 100% parity across EN, DE, ES, FR, IT, JA, NL, PL, PT.
7. *Continuous Active Session Device Tracking (`TrackerService.java`, `ActiveDevicesDbHelper.java`)*:
• `TrackerService` SHALL maintain an in-memory session set of remote device IDs that connected or transmitted telemetry at any point during `mWorkoutID`.
• Whenever active remote devices are reported by `mBanalService.getDatabaseIdsOfActiveRemoteDevices()`, newly observed device IDs SHALL be immediately inserted into `ActiveDevicesDbHelper` (`ActiveDevices.TABLE`) for `mWorkoutID`, ensuring sensors that disconnect or deplete their battery mid-workout are reliably preserved in `ActiveDevicesDbHelper` for post-workout evaluation.
8. *GATT Characteristic Notification Subscription (`MyBTLEDevice.java`)*:
• Upon discovering the standard Bluetooth SIG Battery Service (UUID `0x180F`) and Battery Level Characteristic (UUID `0x2A19`), `MyBTLEDevice` SHALL enable notifications via `gatt.setCharacteristicNotification(btGattChar, true)`.
• If the characteristic properties include `PROPERTY_NOTIFY`, `MyBTLEDevice` SHALL write the Client Characteristic Configuration Descriptor (CCCD UUID `0x2902`) with `BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE`.
• In `onCharacteristicChanged()`, incoming battery updates SHALL immediately invoke `setBatteryPercentage(batteryPercentage)`.
9. *Live Battery Display in Sensors List (`GetMergedDevicesUseCase.kt`, `DeviceDataRepository.kt`)*:
• In `GetMergedDevicesUseCase.kt`, when merging active device telemetry, `knownDevice.copy()` SHALL set `batteryPercentage = if (telemetry.batteryPercentage >= 0) telemetry.batteryPercentage else knownDevice.batteryPercentage`, immediately propagating live battery levels into `mergedDevices` StateFlow.
• In `DeviceDataRepository.kt`, when `DevicesDatabaseManager.setBatteryPercentage()` updates the `DEVICES` table, `DeviceDataRepository` SHALL update its cached `_allDevices` list, ensuring immediate UI reflection even when telemetry stream is inactive or upon sensor disconnection.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: `REQ-CON-007` (*Notify user about low battery levels in sensors*), targeting `MainActivityWithNavigation.kt`, `TrackerService.java`, `ActiveDevicesDbHelper.java`, `DevicesDatabaseManager.java`, `MyDevice.java`, `MyBTLEDevice.java`, `GetMergedDevicesUseCase.kt`, and `DeviceDataRepository.kt`.
2. *Historical Origin & Commit Trace*: Commit `ac493d2bdf652746b9177a2c574b220285b3bf8b` (Initial ASPICE baseline import), ticket `ATT-2192`, Sprint `2026-40.16`. Refined and extended in ticket `ATT-3312`, Sprint `2026-42.1`.
3. *Root Reason for Existing Formulation*: In ATT-2192, `ActiveDevicesDbHelper` was populated exclusively within `TrackerService.java:endWorkout()` by querying `mBanalService.getDatabaseIdsOfActiveRemoteDevices()`. This assumed all session devices remain connected at teardown. Mid-workout disconnects and real-time battery drops were missed. Extending with continuous session device tracking, GATT notification subscriptions, and live UI merging eliminates stale battery levels and missed alerts.
4. *Preservation of Core Invariants*: Critical battery evaluation threshold ($\le 20\%$), sensor name mapping, non-blocking asynchronous dispatch, navigation to sensor settings (`drawer_my_sensors`), normal battery neutrality, and 9-language translation parity MUST NOT be broken.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an active workout with a sensor whose battery level is 15%,
• *When* the workout is stopped and finalized,
• *Then* no modal `AlertDialog` SHALL be shown over the screen transition, an Android system notification SHALL be posted on `NOTIFICATION_CHANNEL__SENSOR_BATTERY`, and an M3 Snackbar SHALL appear on `WorkoutSummariesTabbedScreen` offering navigation to sensor management.
• *Given* a sensor active during a workout that transmits 12% battery and disconnects before workout end,
• *When* the workout is stopped,
• *Then* the sensor is evaluated from `ActiveDevicesDbHelper` and triggers the low battery alert.
• *Given* the sensors list with an active BLE or ANT+ sensor sending battery updates,
• *When* the battery percentage updates in telemetry or database,
• *Then* `GetMergedDevicesUseCase.mergedDevices` immediately reflects the updated battery percentage.
• *Given* an active workout where all sensors have battery $\ge 50\%$,
• *When* the workout is stopped,
• *Then* zero system notifications and zero snackbars SHALL be dispatched.
• *Given* an athlete tapping the notification or Snackbar action,
• *When* triggered,
• *Then* the app SHALL navigate directly to "Meine Sensoren" (`R.id.drawer_my_sensors`).

**Invariants**: Battery percentage schema ($0..100$ int), background thread safety (`Dispatchers.IO`), non-blocking navigation, and 9-language translation parity.

### REQ-CON-008: Detect missing ANT+ services and link to Play Store [Verified]
- **Tests**: `TST-MAN-001`
- **Implementation**: `BANALService.java`
- **Rationale**: Ensure users can easily set up required hardware drivers.
- **Description**: Detect missing ANT+ services and link to Play Store.

### REQ-CON-009: Support per-device calibration (wheel circumference) [Verified]
- **Tests**: `TST-MAN-001`
- **Implementation**: `EditSimpleBikeDeviceFragment.kt`
- **Rationale**: Ensure distance and speed accuracy across setups.
- **Description**: Support per-device calibration (wheel circumference).

### REQ-FIL-001: Flexible filtering (Moving Avg, Exponential Smoothing) [Verified]
- **Tests**: `TST-UNT-004`
- **Implementation**: `FilterManager.java`
- **Rationale**: Allow for smooth visualization of noisy sensor data.
- **Description**: Flexible filtering (Moving Avg, Exponential Smoothing).

### REQ-FIL-002: Configurable smoothing factors (α) for Power [Verified]
- **Tests**: `TST-UNT-005`
- **Implementation**: `ExponentialSmoothingFilter.java`
- **Rationale**: Balance responsiveness vs. stability.
- **Description**: Configurable smoothing factors (α) for Power.

### REQ-FIL-003: Calculate derived metrics (VAM, slope, pace) [Verified]
- **Tests**: `TST-UNT-002`
- **Implementation**: `VerticalSpeedAndSlopeDevice.java`
- **Rationale**: Provide advanced metrics for climbing and analysis.
- **Description**: Calculate derived metrics (VAM, slope, pace).

### REQ-FIL-004: Atomic Altitude Correction [Verified]
- **Tests**: `TST-FIL-003`
- **Implementation**: `TrackerService.java`, `KnownLocationsDatabaseManager.java`
- **Rationale**: The system SHALL apply altitude corrections from known locations atomically across all data structures. This MUST include shifting previous sample points, updating summary extrema (Min/Max), and recalculating total ascent/descent for the active workout to ensure 100% data consistency.

### REQ-FIL-005: Implement "Proxy Sensors" and "Accumulator Sensors" [Verified]
- **Tests**: `TST-STR-001`
- **Implementation**: `ProxySensor.java`
- **Rationale**: Extensible architecture for complex multi-sensor data.
- **Description**: Implement "Proxy Sensors" and "Accumulator Sensors".

### REQ-FIL-006: Geographical rolling averages for start altitude [Verified]
- **Tests**: `TST-STR-001`
- **Implementation**: `WorkoutSamplesDatabaseManager.java`
- **Rationale**: Filter out momentary GPS or barometric jitter.
- **Description**: Geographical rolling averages for start altitude.

### REQ-FIL-012: Stabilized VAM (Vertical Speed) Metric, Location Accuracy Gating & Indoor Drift Suppression Architecture [Verified]
- **Tests**: `TST-FIL-004` | **Tickets**: `ATT-1621`, `ATT-1738`, `ATT-5373`
- **Implementation**: `VerticalSpeedAndSlopeDevice.java`, `TimedMovingAverageFilter.java`, `FilterManager.java`, `BANALService.java`
- **Rationale**: The system SHALL calculate the vertical ascent/descent rate (VAM / Vertical Speed in m/h) in `VerticalSpeedAndSlopeDevice` using linear regression slope smoothing over a sliding window, suppress indoor GPS wander and stationary drift, enforce location accuracy gating, maintain a noise deadband, reject outlier anomalies, and synchronize filter pipelines across altitude source transitions and calibrations (ATT-1621, ATT-1738, ATT-5373):
1. *Sliding Window Linear Regression Derivative ($N = 15$ seconds)*:
• The system SHALL maintain an equidistant 1-second circular buffer of up to $N = 15$ recent altitude samples.
• For a full buffer of $N = 15$ samples, the ascent rate slope $\hat{\beta}$ (in m/s) SHALL be evaluated using least-squares linear regression: $\hat{\beta} = \sum_{k=0}^{N-1} w_k \cdot a_k$ where $w_k = \frac{k - \frac{N-1}{2}}{\sum_{j=0}^{N-1} (j - \frac{N-1}{2})^2}$, minimizing total squared error across all samples in the window.
• For an initial buffer with $M$ samples ($2 \le M < 15$), the system SHALL evaluate the least-squares slope dynamically over the available $M$ points, enabling immediate responsiveness during warm-up without artificial startup delay.
• The unconstrained vertical speed in m/h SHALL be computed as $\text{raw\_vam} = \hat{\beta} \times 3600$.
2. *Location Accuracy Gating ($> 20.0\text{ m}$)*:
• The system SHALL query horizontal accuracy (`SensorType.ACCURACY`) via `cAccuracyFilter = new FilterData(null, SensorType.ACCURACY, FilterType.INSTANTANEOUS, 1)`.
• If GPS accuracy is degraded ($> 20.0\text{ m}$), the system SHALL suppress the calculated vertical speed to `0 m/h` to reject multipath altitude jitter in indoor, basement, or canyon environments.
3. *Stationary Gating & Low-Speed Drift Damping*:
• The system SHALL query horizontal speed `speed_mps` from `speedFilteredSensorData` and the dynamic minimum speed threshold `minSpeed = SettingsDataStoreJavaHelper.getSlopeMinSpeed(mContext)` (default `0.5` m/s).
• If stationary (`speed_mps < minSpeed` or `speed_mps < 0.5` m/s):
- If $|	ext{raw\_vam}| < 150\text{ m/h}$, clamp vertical speed to `0 m/h`.
- If genuine vertical displacement occurs while stationary in the horizontal plane ($|	ext{raw\_vam}| \ge 150\text{ m/h}$, such as an elevator, cable car, or ski lift), broadcast measured vertical speed.
• If moving at low drift/walking speeds ($0.5\text{ m/s} \le \text{speed\_mps} < 1.2\text{ m/s}$):
- The system SHALL enforce an elevated drift deadband ($|	ext{raw\_vam}| < 150\text{ m/h}$ clamped to `0 m/h`), preventing phantom GPS wander speeds from triggering false climbing rates.
4. *Flat Terrain Athletic Noise Deadband & Outlier Clamping*:
• When moving horizontally at athletic speed ($	ext{speed\_mps} \ge 1.2\text{ m/s}$) with valid GPS accuracy ($\le 20.0\text{ m}$), if $|	ext{raw\_vam}| < 35\text{ m/h}$, the system SHALL clamp vertical speed to `0 m/h`.
• Stabilized vertical speed SHALL be bounded strictly to $[-3000, 3000]\text{ m/h}$ to reject single-tick step glitches.
5. *Active Altitude Source Transition Synchronization & Re-seeding (`REQ-FIL-012.5`, ATT-5373)*:
• `VerticalSpeedAndSlopeDevice` SHALL monitor the active source device backing `SensorType.ALTITUDE` by unwrapping `ProxySensor.getBestSensor()` / `getSourceSensor()`.
• When the active `ALTITUDE` source device transitions (e.g. from `SPEED_AND_LOCATION_GPS` to `ALTITUDE_FROM_POSITION` or `ALTITUDE_FROM_PRESSURE`), `VerticalSpeedAndSlopeDevice` SHALL immediately re-seed and synchronize `cAltitudeFilter` (21s), `cAltitudeSuperFilter` (300s), `mLastAltitudeSuperFiltered`, and populate the 15-second `mAltitudeHistory` circular buffer uniformly with the initial altitude emitted by the new authoritative source.
• Standstill descent accumulation (`mDescentSensor`) SHALL remain strictly `0.0 m` and slope (`mSlopeSensor`) and vertical speed (`mVerticalSpeedSensor`) SHALL NOT exhibit transient negative spikes upon source transition.
6. *Filter Shift & Barometric Calibration Parity (`REQ-FIL-012.6`, ATT-5373)*:
• `TimedMovingAverageFilter` and `FilterManager` SHALL support uniform vertical translation (`shiftValues(delta)` and `shiftFilter(filterData, delta)`).
• Upon receiving `AltitudeFromPressureDevice.ALTITUDE_CORRECTION_INTENT`, `VerticalSpeedAndSlopeDevice` SHALL shift `cAltitudeFilter` and `cAltitudeSuperFilter` in `FilterManager` alongside `mLastAltitudeSuperFiltered` and `mAltitudeHistory` by `deltaOffset`, preventing rolling-average drift divergence during barometric recalibration.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: `REQ-FIL-012` (*Stabilized VAM Metric & Stationary Suppression Architecture*), `REQ-SEN-025` (*Pairable Position-Based Elevation Sensor*), and `REQ-SEN-021` (*Proxy & Accumulator Sensors*). Targets `VerticalSpeedAndSlopeDevice.java`, `TimedMovingAverageFilter.java`, `FilterManager.java`, and `BANALService.java`.
2. *Historical Origin & Commit Trace*: `ATT-1621` / `ATT-1738` (VAM linear regression and gating), `ATT-4845` (`31553134`, initial `mAltitudeCorrectionReceiver`), `ATT-4859` / `ATT-5199` / `ATT-5312` (`REQ-SEN-025`, DEM elevation device).
3. *Root Reason for Existing Formulation*: `ATT-4845` shifted local variables on barometric calibration but lacked access to internal filters in `FilterManager`. `ATT-4859` introduced DEM altitude as higher priority than GPS, causing `ProxySensor` to switch sources abruptly without triggering source-transition synchronization in `VerticalSpeedAndSlopeDevice`.
4. *Preservation of Core Invariants*: Stationary gating ($|	ext{raw\_vam}| < 150\text{ m/h}$), low-speed drift deadbands, genuine ski-lift/elevator motion ($\ge 150\text{ m/h}$), linear regression slope calculation, and outlier clamping $[-3000, 3000]\text{ m/h}$ remain 100% intact.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an athlete at standstill (`distance = 0.0 m`, `speed = 0.0 m/s`) with GPS ellipsoid altitude (e.g. 492.0 m) streaming during initial lock,
• *When* `AltitudeFromPositionDevice` resolves DEM elevation (e.g. 438.0 m) and registers as the active `ALTITUDE` source,
• *Then* `VerticalSpeedAndSlopeDevice` re-seeds `cAltitudeFilter`, `cAltitudeSuperFilter`, `mLastAltitudeSuperFiltered`, and `mAltitudeHistory` to 438.0 m,
• *And* `mDescentSensor` accumulation remains `0.0 m`, `mVerticalSpeedSensor` remains `0 m/h`, and `mSlopeSensor` exhibits no negative spike when motion begins.
• *Given* an active tracking session with barometric altitude in `cAltitudeFilter` and `cAltitudeSuperFilter`,
• *When* `ALTITUDE_CORRECTION_INTENT` is broadcast with `deltaOffset = +20.0 m` (or `-20.0 m`),
• *Then* `cAltitudeFilter` and `cAltitudeSuperFilter` buffered timestamped samples are uniformly shifted by `deltaOffset` alongside `mLastAltitudeSuperFiltered` and `mAltitudeHistory`.

**Invariants**: REQ-FIL-012 deadbands, ski-lift/elevator detection ($\ge 150$ m/h), thread-safe monitor synchronization in filters, and 100% clean-room test pass rate.

### REQ-TRK-001: Support Start, Stop, Pause with Service persistence [Verified]
- **Tests**: `TST-REG-001`
- **Implementation**: `TrackerService.java`
- **Rationale**: Ensure data integrity in the background.
- **Description**: Support Start, Stop, Pause with Service persistence.

### REQ-TRK-002: Workout Lap Recording Lifecycle, Manual Splitting & Pause/Resume Neutrality [Verified]
- **Tests**: `TrainingApplication.java`, `TrackerService.java`, `LapsDatabaseManager.java`
- **Implementation**: Eliminate confusing phantom zero-duration laps from workout summaries, preserve Rabbit/Hedgehog performance badge integrity, and ensure clean TCX exports by aligning pause/resume behavior with standard athletic tracking.
- **Rationale**: The system SHALL manage the lifecycle of workout laps deterministically during recording, tracking termination, and database retrieval, preventing the creation and presentation of zero-duration phantom laps:
1. *Pause and Resume Neutrality*: Transitioning active tracking into a paused state (`pauseTracking()`) or resuming tracking from paused (`resumeFromPaused()`) SHALL NOT broadcast `REQUEST_NEW_LAP` and SHALL NOT create, increment, or close workout laps. Active lap duration and distance accumulation SHALL pause and resume transparently with the session.
2. *Manual Lap Splitting Integrity*: Explicit lap requests initiated by the user (via `TrackingViewsRepository.requestNewLap()` in the tracking UI or `BUTTON_LAP` from a connected smartwatch) SHALL finalize the active lap, persist its split metrics to `Laps.db` (`saveLap(...)`), broadcast `BANALService.LAP_SUMMARY`, and initialize the subsequent sequential lap index ($N+1$).
3. *Workout Finalization & Termination Guards*: Upon tracking completion (`TrackerService.endWorkout()`), `createNewLap()` SHALL finalize and persist the active in-progress lap if and only if `lapTime_s > 0` or `lapDistance > 0.0`. If the active lap has zero duration and zero distance, the system SHALL discard it and MUST NOT persist it to `Laps.db`.
4. *Defensive Database & Receiver Validation*: `TrackerService.mLapSummaryReceiver` and `LapsDatabaseManager.saveLap(...)` SHALL reject any lap record where `timeTotal <= 0 && distanceTotal <= 0.0`, ensuring invalid or empty splits are never written to SQLite.
5. *Historical & Vectorized Query Sanitization*: In `LapsDatabaseManager.getLaps(workoutId)` and `getLapsForWorkouts(workoutIds)`, queries SHALL filter out legacy zero-duration records (`TIME_TOTAL_s > 0 OR DISTANCE_TOTAL_m > 0`), ensuring corrupted historical splits do not render in workout summaries (`WorkoutLaps.kt`) or export files (`TCXFileWriter.java`, `GPXFileWriter.java`).

**Acceptance Criteria (Given-When-Then)**:
• *Given* an active live tracking session with no manual lap presses,
• *When* the user pauses tracking, resumes tracking, pauses again, and ends the workout,
• *Then* `Laps.db` SHALL contain exactly 1 lap record whose duration and distance match the total active workout metrics, with zero 00:00:00 phantom laps.
• *Given* an active live tracking session where the user explicitly taps "New Lap" once at minute 10 and ends the workout at minute 25,
• *Then* `Laps.db` SHALL contain exactly 2 lap records (Lap 1 = 10 min, Lap 2 = 15 min), and zero zero-duration laps.
• *Given* an attempt to invoke `LapsDatabaseManager.saveLap()` with `lapTime = 0` and `lapDistance = 0.0`,
• *Then* the method SHALL discard the write and log a warning without inserting into `Laps.TABLE`.
• *Given* a database containing a historical zero-duration lap from prior app versions,
• *When* `LapsDatabaseManager.getLaps(workoutId)` is queried,
• *Then* the zero-duration lap SHALL be excluded from the returned list.

**Invariants**: Manual lap splitting (`TrackingViewsRepository`, `PebbleService`), total active time/distance calculation (`REQ-TRK-001`), live pause duration updates (`REQ-UI-038`), and TCX/GPX export schema compliance (`REQ-DAT-012`) MUST NOT be broken.

### REQ-TRK-003: Use WakeLocks during active tracking [Verified]
- **Tests**: `TST-REG-001`
- **Implementation**: `TrackerService.java`
- **Rationale**: Prevent CPU sleep and data gaps.
- **Description**: Use WakeLocks during active tracking.

### REQ-TRK-004: Automatically guess Sport Type and Equipment [Verified]
- **Tests**: `TST-UNT-003`
- **Implementation**: `EquipmentDiscoveryManager.kt`
- **Rationale**: Minimize user effort via automated categorization.
- **Description**: Automatically guess Sport Type and Equipment.

### REQ-TRK-005: Implement session recovery ("Resume from Crash") [Verified]
- **Tests**: `TST-STR-001`
- **Implementation**: `TrackerService.java`
- **Rationale**: Ensure data integrity against system pressure.
- **Description**: Implement session recovery ("Resume from Crash").

### REQ-TRK-006: Support sport-specific search triggers [Verified]
- **Tests**: `TST-MAN-001`
- **Implementation**: `TrainingApplication.java`
- **Rationale**: Sensors ready when user changes activity.
- **Description**: Support sport-specific search triggers.

### REQ-TRK-007: Deterministic 1Hz sampling rate during recording [Verified]
- **Tests**: `TST-REG-001`
- **Implementation**: `TrackerService.java`
- **Rationale**: Consistent time-base for analysis and export.
- **Description**: Deterministic 1Hz sampling rate during recording.

### REQ-TRK-008: Align session averages with core summary metrics [Verified]
- **Tests**: `TST-STR-004`
- **Implementation**: `TrackerService.java`
- **Rationale**: Ensure background tracking focus matches optimized UI summary for consistency and performance.
- **Description**: Align session averages with core summary metrics.

### REQ-TRK-009: Total Time Persistence [Verified]
- **Tests**: `TST-REG-007`
- **Implementation**: `TrackerService.java`
- **Rationale**: The `TrackerService` SHALL continue to update the `TIME_TOTAL_s` field in the `WorkoutSummaries` database table even when the recording is in a PAUSED state.
- **Description**: Total Time Persistence.

### REQ-TRK-010: Live Tracking Finalization Event Dispatch [Verified]
- **Tests**: `TrackerService.java`, `WorkoutRepository.kt`, `PeriodsRepository.kt`
- **Implementation**: Ensure live-tracked workouts immediately generate and update period summaries, matching the reactive behavior of TCX imports.
- **Rationale**: Upon tracking completion (`endWorkout`), the system SHALL broadcast `WORKOUT_UPDATED_INTENT` and `TRACKING_FINISHED_INTENT` with the workout ID parameter (`WORKOUT_ID`) via `LocalBroadcastManager`, while preserving the package-targeted system broadcast of `TRACKING_FINISHED_INTENT`. The system SHALL ensure downstream subscribers (`WorkoutRepository`, `PeriodsRepository`, `WorkoutClusterEngine`) consume the finalized session to reactively create and update period summaries (Day, Week, Month, Year).
**Acceptance Criteria**:
• *Given* an active live tracking session,
• *When* the workout is finished,
• *Then* `LocalBroadcastManager` SHALL dispatch `WORKOUT_UPDATED_INTENT` with extra `WORKOUT_ID` = `mWorkoutID`, triggering atomic rollup in `PeriodsRepository`.
**Invariants**: Existing intent action names and extras MUST NOT be changed; system-level broadcast receivers (`TrainingApplication`, `MainActivityWithNavigation`) MUST NOT be disrupted.

### REQ-TRK-011: Intelligent Workout Auto-Naming based on Recognized Start and Destination Lieblingsorte [Verified]
- **Tests**: `TrackerService.java`, `WorkoutAutoNamingHelper.kt`, `WorkoutClusterEngine.kt`, `KnownLocationsDatabaseManager.java`
- **Implementation**: Provide meaningful automatic workout names for unclustered activities using recognized start and destination locations, eliminating manual rename friction in the app and Strava.
- **Rationale**: The system SHALL automatically generate meaningful, localized workout names based on recognized favorite locations (*Lieblingsorte*) for recorded sessions that do not match an existing Route Cluster (ATT-1398):
1. *Start & Destination Location Resolution*:
• Upon workout finalization in `TrackerService.java`, when a session does not match an existing cluster (`suggestion == null`), the system SHALL resolve start and end coordinates (`startPosRaw`, `endPosRaw`) against `KnownLocationsDatabaseManager.getMyLocation(...)`.
2. *Naming Heuristics*:
• *Round Trips (Start == Destination within geofence)*: When both start and end resolve to the same location (or $\le \text{geofence radius}$), the name SHALL format based on activity sport type:
- Running (`BSportType.RUN`): Localized \"Run from %1$s\" (e.g. German: \"Lauf ab %1$s\").
- Cycling (`BSportType.BIKE`): Localized \"Ride from %1$s\" (e.g. German: \"Fahrt ab %1$s\").
- General / Other (`BSportType.UNKNOWN` / `CONFLICT`): Localized \"Loop from %1$s\" (e.g. German: \"Runde ab %1$s\").
• *Point-to-Point (Start != Destination, both recognized)*: When start and destination resolve to distinct locations:
- Running: Localized \"Run from %1$s to %2$s\" (German: \"Lauf von %1$s nach %2$s\").
- Cycling: Localized \"Ride from %1$s to %2$s\" (German: \"Fahrt von %1$s nach %2$s\").
- Other: Localized \"Activity from %1$s to %2$s\" (German: \"Aktivität von %1$s nach %2$s\").
• *Start Only Recognized*: When only start resolves to a known location, format with single-location prefix (e.g. \"Run from %1$s\" / \"Lauf ab %1$s\").
• *Destination Only Recognized*: When only destination resolves, format as \"Run to %1$s\" / \"Lauf nach %1$s\".
• *Neither Recognized (Fallback)*: If neither start nor destination matches a known location, the system SHALL retain standard default naming (`mBaseFileName`).
3. *Cluster Proposal Seeding (`WorkoutClusterEngine.kt`)*:
• When suggesting or creating a new Route Cluster for an unclustered track that starts at a known location, the default cluster name SHALL be seeded with \"Loop from %1$s\" / \"Runde ab %1$s\" instead of generic \"Workout at YYYY-MM-DD\".
4. *User Sovereignty & Invariants*:
• Established route cluster matches (`suggestion != null`) SHALL take precedence over location naming.
• Manual user edits to workout names (`WorkoutRepository.setWorkoutName`) SHALL remain strictly immutable.
• Single-thread database serialization (`KnownLocationsDB-Thread`), hardware sport type inference, and 9-language localization parity SHALL be preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an unclustered run recorded from \"Zuhause\" returning to \"Zuhause\",
• *When* the workout is finalized,
• *Then* `WORKOUT_NAME` in SQLite SHALL be set to \"Lauf ab Zuhause\" (in German) or \"Run from Zuhause\" (in English).
• *Given* an unclustered bike ride from \"Zuhause\" to \"Büro\",
• *When* finalized,
• *Then* `WORKOUT_NAME` SHALL be set to \"Fahrt von Zuhause nach Büro\" (DE) or \"Ride from Zuhause to Büro\" (EN).
• *Given* a workout matching an existing cluster \"Isarrunde #5\",
• *When* finalized,
• *Then* the cluster name \"Isarrunde #5\" SHALL take precedence over location auto-naming.
• *Given* an unclustered workout in a wilderness area with no known locations,
• *When* finalized,
• *Then* the standard base file name fallback SHALL be retained.

**Invariants**: Cluster naming priority, user manual edit immutability, hardware sport type inference, and 9-language localization parity MUST NOT be broken.

### REQ-TRK-012: Distance-Enriched Auto-Generated Workout Names for Start-Location Sessions [Verified]
- **Tests**: `TrackerService.java`, `WorkoutAutoNamingHelper.kt`
- **Implementation**: Append formatted total distance to auto-generated workout names for start-location activities, eliminating duplicate titles in workout history and Strava feeds while providing immediate session scope glanceability.
- **Rationale**: The system SHALL append the formatted total workout distance to automatically generated workout names for unclustered activities when the title is derived solely from a recognized starting location (*Lieblingsort*) (ATT-2247):
1. *Total Distance Parameter Integration (`WorkoutAutoNamingHelper.kt`)*:
• `WorkoutAutoNamingHelper.generateWorkoutName()` SHALL accept parameter `distanceTotalMeters: Double = 0.0` with `@JvmOverloads` to preserve binary and source compatibility for existing callers.
• In `TrackerService.java:onStopTracking()`, `mDistanceTotal_m` SHALL be passed into `generateWorkoutName()`.
2. *Activation Heuristics & Distance Formatting*:
• When `distanceTotalMeters > 0.0` and the naming condition is Case 2 (Round Trip / Loop: start matches end or returns within start geofence) or Case 3 (Start Location Only Known):
- The system SHALL format total distance according to athlete unit preferences (`TrainingApplication.getUnit()`).
- In Metric mode (`MyUnits.METRIC`): distance in kilometers (`km = distanceTotalMeters / 1000.0`) with unit suffix `km`.
- In Imperial mode (`MyUnits.IMPERIAL`): distance in miles (`mi = distanceTotalMeters / BANALService.METER_PER_MILE`) with unit suffix `mi`.
- Clean Decimal Precision:
* For distances $< 10.0\text{ km/mi}$: formatted with exactly 1 decimal place (`%.1f`, e.g. `8.2 km`, `5.1 mi`).
* For distances $\ge 10.0\text{ km/mi}$: formatted with 0 decimal places if within 0.05 of an integer (`%.0f`, e.g. `42 km`, `26 mi`), otherwise with 1 decimal place (`%.1f`, e.g. `42.5 km`, `26.2 mi`).
3. *Localized Naming Templates Across 9 Locales*:
• Running (`BSportType.RUN`): `R.string.workout_name_run_from_with_distance` (EN: `Run from %1$s (%2$s)`, DE: `Lauf ab %1$s (%2$s)`).
• Cycling (`BSportType.BIKE`): `R.string.workout_name_ride_from_with_distance` (EN: `Ride from %1$s (%2$s)`, DE: `Radfahrt ab %1$s (%2$s)`).
• Activity / General (`BSportType.UNKNOWN` / `CONFLICT`): `R.string.workout_name_activity_from_with_distance` (EN: `Activity from %1$s (%2$s)`, DE: `Aktivität ab %1$s (%2$s)`).
• Round Trip / Loop fallback: `R.string.workout_name_round_trip_with_distance` (EN: `Loop from %1$s (%2$s)`, DE: `Runde ab %1$s (%2$s)`).
• All format strings SHALL maintain 100% translation coverage across EN, DE, ES, FR, IT, JA, NL, PL, PT with exact `%1$s` and `%2$s` format specifier parity.
4. *Exclusion Invariants*:
• Point-to-Point sessions where both start and destination are recognized (`startLocation != null && endLocation != null && startLocation.id != endLocation.id`, Case 1: e.g. `Fahrt von Zuhause nach Büro`) SHALL NOT append distance.
• Destination-Only sessions (Case 4: e.g. `Run to Office`) SHALL NOT append distance.
• When `distanceTotalMeters <= 0.0`, the system SHALL generate standard titles without distance per `REQ-TRK-011`.
• Route cluster suggestions (`suggestion != null`) and manual user edits (`WorkoutRepository.setWorkoutName`) SHALL take precedence and remain immutable.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement extending `REQ-TRK-011` (*Intelligent Workout Auto-Naming based on Recognized Start and Destination Lieblingsorte*).
2. *Historical Origin & Commit Trace*: Ticket `ATT-2247`, Sprint `2026-40.16`, Target Release `V4.9.39`, Epic `ATT-1396` (*[Epic] Lieblingsorte: Visible Value, Auto-Naming & Tracking Integration*).
3. *Root Reason for Existing Formulation*: `REQ-TRK-011` established baseline location auto-naming, but sessions departing from the athlete's home or office produced identical titles (e.g. `Radfahrt ab Zuhause`), making it impossible to differentiate short recovery spins from gran fondos in list views, calendar, and Strava feeds without opening details.
4. *Preservation of Core Invariants*: Point-to-point naming, cluster suggestion precedence, user rename immutability, hardware sport type inference, single-thread DB serialization, and 9-language translation parity MUST NOT be broken. Backward-compatible calls omitting distance continue to generate standard titles without distance.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an unclustered bike ride starting and finishing at \"Zuhause\" covering 42,000 meters in Metric mode,
• *When* the workout is finalized in `TrackerService`,
• *Then* `WORKOUT_NAME` SHALL be set to \"Radfahrt ab Zuhause (42 km)\" (DE) or \"Ride from Zuhause (42 km)\" (EN).
• *Given* an unclustered run starting from \"Büro\" covering 8,200 meters in Metric mode,
• *When* finalized,
• *Then* `WORKOUT_NAME` SHALL be set to \"Lauf ab Büro (8,2 km)\" (DE) or \"Run from Büro (8.2 km)\" (EN).
• *Given* an athlete with Imperial units (`MyUnits.IMPERIAL`) running 26.2 miles (42,164.8 m),
• *When* finalized,
• *Then* `WORKOUT_NAME` SHALL be set to \"Run from Home (26.2 mi)\".
• *Given* a point-to-point ride from \"Zuhause\" to \"Büro\" covering 15,000 meters,
• *When* finalized,
• *Then* `WORKOUT_NAME` SHALL remain \"Fahrt von Zuhause nach Büro\" without distance suffix.
• *Given* a call to `generateWorkoutName` without passing `distanceTotalMeters`,
• *When* executed,
• *Then* it SHALL format the workout name without distance, preserving 100% backward compatibility with `REQ-TRK-011`.

**Invariants**: Cluster naming priority, user rename immutability, hardware sport inference, single-thread DB serialization, and 9-language translation parity MUST NOT be broken.

### REQ-TRK-013: GPS Speed Inactivity Watchdog, Gradual Speed Decay & Pace Nullification Architecture [Verified]
- **Tests**: `SpeedAndLocationDevice.java`, `SpeedAndLocationDevice_GoogleFused.java`, `tools/replay_workout.py`
- **Implementation**: Prevent frozen cockpit speed and pace metrics when athletes come to a complete stop or enter GPS shadow by decaying speed to zero after location silence, while tolerating multi-second update intervals without premature false-positive decay or corrupted smoothing (ATT-2616, ATT-4802).
- **Rationale**: The system SHALL monitor elapsed time since the last valid `Location` fix during active tracking and execute deterministic speed decay when location updates cease:
1. *Location Inactivity Watchdog ($T_{\text{timeout}} = 10.0\text{ s}$)*:
• In `SpeedAndLocationDevice.java`, the system SHALL maintain an inactivity timeout of 10.0 seconds ($10000\text{ ms}$, `INACTIVITY_TIMEOUT_MS`).
• If elapsed time since the last valid location update reaches or exceeds 10.0 seconds ($10000\text{ ms}$), the system SHALL initiate periodic speed decay and mark `mIsDecaying = true`.
2. *Periodic Speed Decay ($1.0\text{ s}$ intervals)*:
• Every 1.0 second ($1000\text{ ms}$) following the 10-second timeout where location updates remain absent, the system SHALL decay active speed by 50% ($mSpeed = mSpeed \times 0.5$).
• When decayed speed drops below $0.1\text{ m/s}$ (or reaches 0.0), the system SHALL clamp active speed strictly to $0.0\text{ m/s}$ and publish $0.0$ to `mSpeedSensor`.
3. *Pace Resolution & Division-by-Zero Protection*:
• Whenever active speed is clamped to $0.0\text{ m/s}$ or is $< 0.1\text{ m/s}$, `mPaceSensor` SHALL emit `null` (rendering as `'--:--'` in cockpit tiles).
• Division-by-zero ($1.0 / 0.0$) SHALL NEVER be passed into `mPaceSensor`.
4. *Decoupled Speed Recovery on Fresh Fix*:
• Upon receiving a valid new `Location` update, pending decay ticks SHALL be cancelled, the watchdog timer SHALL reset, and `mIsDecaying` SHALL be cleared.
• If `mIsDecaying` was active prior to the fix, or if `mPrevLocation == null`, or if elapsed time $\Delta t \ge 3000\text{ ms}$, the system SHALL assign `mSpeed = speed` directly rather than averaging with an artificially decayed or stale speed value. Standard exponential smoothing $mSpeed = (mSpeed + \text{speed}) / 2$ SHALL strictly apply only to continuous fixes arriving within nominal intervals ($\Delta t < 3000\text{ ms}$) without prior decay.
5. *Provider Cadence & Tooling Synchronization*:
• In `SpeedAndLocationDevice_GoogleFused.java`, the location request SHALL explicitly configure fastest interval via `mLocationRequest.setFastestInterval(SAMPLING_TIME)` (1000 ms) in addition to `setInterval(SAMPLING_TIME)` to prevent OS throttling.
• In `tools/replay_workout.py`, `ReplayEngine.step()` SHALL suppress dispatching new location updates upon reaching EOF, and mock location provider cleanup in `MockGpsManager.release_providers()` SHALL be thread-safe with worker loop processing to prevent test providers from lingering on test devices.
6. *Lifecycle Cleanliness*:
• In `SpeedAndLocationDevice.shutDown()`, all scheduled watchdog timers/handlers SHALL be cleanly removed and released without memory leaks.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: `REQ-TRK-013` (GPS Speed Inactivity Watchdog, Gradual Speed Decay & Pace Nullification Architecture [`TST-TRK-005`]).
2. *Historical Origin & Commit Trace*: Introduced in `ATT-2616` (commit `de8f92c4`, sprint `sprint/2026-41.3`); amended in `ATT-4802` (`sprint/2026-43.10`).
3. *Root Reason for Existing Formulation*: A 5.0-second timeout was originally selected assuming continuous 1-Hz GPS updates, where 5 missing samples reliably signaled an athlete stop or tunnel entry, preventing speed from remaining frozen indefinitely.
4. *Preservation of Core Invariants*: Stationary athletes still decay to 0.0 m/s and nullify pace without division-by-zero; distance accumulators never accumulate phantom distance; genuine GPS fixes take precedence. Extending timeout to 10.0s and bypassing smoothing after decay avoids false-positive speed collapse on 7-8s multi-second location cadences.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an athlete tracking speed via GPS at 25 km/h who stops at a traffic light or enters GPS shadow,
• *When* 10.0 seconds elapse without receiving a valid location fix,
• *Then* speed SHALL begin halving every 1.0 second until clamped to 0.0 m/s.
• *Given* an athlete riding with location fixes arriving at 7–8 second intervals,
• *When* consecutive fixes arrive,
• *Then* the 10.0-second watchdog SHALL NOT trigger premature speed decay between fixes.
• *Given* an inactive decaying speed state,
• *When* a new valid location fix is received,
• *Then* live speed evaluation SHALL immediately resume from the incoming fix without averaging against the decayed watchdog value.
• *Given* a decaying speed reaching 0.0 m/s,
• *When* inspected in `mSpeedSensor` and `mPaceSensor`,
• *Then* `mSpeedSensor` SHALL equal 0.0 m/s and `mPaceSensor` SHALL equal `null`.

**Invariants**: Distance accumulator sensors (`mDistanceSensor`, `mLapDistanceSensor`) MUST NOT accumulate phantom distance during decay; start location and line distance MUST NOT be altered by decay ticks; broadcast intents (`NEW_LOCATION_INTENT`) MUST NOT be emitted by watchdog ticks.

### REQ-TRK-014: Successive Geodesic Location Delta Fallback for Missing GPS Bearing and Speed in SpeedAndLocationDevice [Verified]
- **Tests**: `SpeedAndLocationDevice.java`, `TrackingTabsScreen.kt`
- **Implementation**: Successive geodesic location delta fallback for missing GPS bearing and speed in SpeedAndLocationDevice.
- **Rationale**: The system SHALL calculate forward heading bearing and travel speed from the geodesic displacement and elapsed time interval between successive location fixes when incoming `Location` fixes lack explicit bearing or speed data (ATT-2467):
1. *Bearing Fallback Calculation (`SpeedAndLocationDevice.java`)*:
• The system SHALL inspect `location.getBearing()`.
• If `location.getBearing() != 0.0f`, the reported bearing SHALL be used directly.
• If `location.getBearing() == 0.0f` (e.g. system test providers or hardware GNSS without heading lock):
- If `mPrevLocation != null` and displacement `delta_distance > 0.5\text{ m}`, the system SHALL derive the forward initial azimuth bearing from `(mPrevLocation.bearingTo(location) + 360.0) % 360.0`.
- If stationary or paused (`delta_distance \le 0.5\text{ m}`), the system SHALL retain the last valid non-null bearing (`mBearingSensor.getValue()`) to prevent the compass and map chevrons from snapping back to 0° (North).
- If no previous fix exists, bearing defaults to `0.0`.
2. *Speed Fallback Calculation (`SpeedAndLocationDevice.java`)*:
• The system SHALL inspect `location.getSpeed()`.
• If `location.getSpeed() > 0.0`, the reported speed SHALL be used directly.
• If `location.getSpeed() == 0.0` (e.g. mock locations without velocity attribute):
- If `mPrevLocation != null` and `delta_distance > 0.1\text{ m}`, the system SHALL evaluate elapsed time `dt_ms = location.getTime() - mPrevLocation.getTime()`.
- If `dt_ms > 0 && dt_ms < 30000`, the system SHALL calculate velocity as `delta_distance / (dt_ms / 1000.0)`.
- Plausibility Clamping: If calculated speed $\le 70.0\text{ m/s}$ ($252\text{ km/h}$), it SHALL be accepted; teleportation or seek jumps $> 70.0\text{ m/s}$ SHALL be ignored.
- Calculated speed SHALL feed into standard smoothing `mSpeed = (mSpeed + speed) / 2` and `mSpeedSensor`.
3. *Navigation & Route Auto-Detection Synergy (`TrackingTabsScreen.kt`)*:
• When dispatching location updates to `routeSelectorViewModel.onLocationChanged(location)`, the system SHALL populate `location.bearing = currentBearing.toFloat()` and `location.speed = currentSpeed.toFloat()`, ensuring `RouteAutoDetector` evaluates heading alignment without being suppressed by `!location.hasBearing()`.
4. *Preservation of System Invariants*:
• Real hardware GNSS fixes providing valid bearing/speed MUST NOT be overridden.
• GPS watchdog inactivity decay (`REQ-TRK-013`) remains fully operational.
• 100% unit test pass rate across the full test suite.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement (`REQ-TRK-014`).
2. *Historical Origin & Commit Trace*: Ticket `ATT-2467`, Sprint `Human_Review`. Arises because Android OS's native `cmd location providers set-test-provider-location` shell tool drops bearing and speed parameters (`IllegalArgumentException: Unknown option: --bearing`), preventing desk testing of navigation chevrons, turn cues, and fork alerts.
3. *Root Reason for Existing Formulation*: `SpeedAndLocationDevice` originally read `location.getBearing()` and `location.getSpeed()` directly from Android's `Location` object, assuming hardware GPS always provides them. Mock location testing revealed that without fallback delta math, heading is permanently frozen at North and speed remains at 0.
4. *Preservation of Core Invariants*: Hardware GPS fixes take absolute precedence if non-zero; watchdog timeout and decay invariants are untouched; 100% test pass rate preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an active tracking session receiving location fixes with `bearing == 0.0f` and `speed == 0.0f` (e.g. from `fake_gps.py`),
• *When* successive fixes advance along an East-bound trajectory 7 meters apart at 1-second intervals,
• *Then* `SpeedAndLocationDevice` SHALL update `mBearingSensor` to `90.0°` and `mSpeedSensor` to `7.0 m/s` (smoothed: `3.5 m/s` on first move).
• *Given* a simulation that pauses or becomes stationary (`delta_distance \le 0.5\text{ m}`),
• *When* stationary fixes arrive,
• *Then* `mBearingSensor` SHALL retain its previous heading of `90.0°` rather than resetting to `0.0°`.
• *Given* a hardware GPS fix with non-zero bearing `180.0f`,
• *When* received,
• *Then* `SpeedAndLocationDevice` SHALL use the hardware bearing directly.

**Invariants**: Hardware non-zero metrics take precedence; watchdog decay preserved; 100% test pass rate MUST NOT be broken.

### REQ-TRK-015: Permission-Gated Foreground Service Types in TrackerService [Verified]
- **Tests**: Prevent fatal SecurityException on Android 14+ when promoting TrackerService to foreground service.
- **Implementation**: CONNECTED_DEVICE` and SHALL NOT contain `HEALTH`.
• *Given* an Android 14+ device where health runtime permission IS granted,
• *When* `TrackerService.onStartCommand()` derives foreground service types,
• *Then* the derived bitmask SHALL include `HEALTH`.

**Invariants**: Location and Bluetooth connected device tracking must remain functional; no SecurityException thrown.
- **Rationale**: The system SHALL dynamically evaluate held permissions before promoting `TrackerService` to an Android 14+ (API 34+) Foreground Service (ATT-2972):
1. *Permission Gating for FGS Types*:
• `FOREGROUND_SERVICE_TYPE_LOCATION` SHALL always be included if `ACCESS_FINE_LOCATION` or `ACCESS_COARSE_LOCATION` is granted.
• `FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE` SHALL only be included if `BLUETOOTH_CONNECT` (or `BLUETOOTH` on API < 31) is granted.
• `FOREGROUND_SERVICE_TYPE_HEALTH` SHALL strictly be gated by verifying that the app holds `FOREGROUND_SERVICE_HEALTH` AND at least one health runtime permission (e.g. `ACTIVITY_RECOGNITION`). If health runtime permissions are not held, `FOREGROUND_SERVICE_TYPE_HEALTH` SHALL be omitted.
2. *Crash & Interruption Prevention*:
• Omitting unpermitted FGS types prevents `ActivityManagerService` from throwing fatal `SecurityException`, ensuring `mTrackingInterrupted` is NOT falsely set to `true` during normal startup.
3. *Preservation of Invariants*:
• Background BLE sensor polling via `FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE` remains enabled when Bluetooth permissions are present.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an Android 14+ device where location and bluetooth permissions are granted but health runtime permissions are absent,
• *When* `TrackerService.onStartCommand()` derives foreground service types,
• *Then* the derived bitmask SHALL contain `LOCATION

### REQ-TRK-016: Guaranteed Workout Finalization and Unconditional Completion Broadcast on Stop [Verified]
- **Tests**: `TrackerService.java`
- **Implementation**: Guarantee workout finalization and edit navigation when tracking is stopped.
- **Rationale**: The system SHALL guarantee the finalization and closure of an active workout session when tracking terminates (ATT-2972):
1. *Guaranteed Finalization Protocol*:
• In `TrackerService.onDestroy()`: If a workout session was created (`mWorkoutID > 0`), the system SHALL invoke `endWorkout()` regardless of `mTrackingInterrupted` unless the session failed during initial synchronous table allocation before any workout row existed.
2. *Defensive Database Commit & Intent Broadcast*:
• `endWorkout()` SHALL structure execution using `try/finally` blocks such that updating `WorkoutSummaries.FINISHED = 1` and dispatching `TRACKING_FINISHED_INTENT` with `WORKOUT_ID` are guaranteed to execute even if subsidiary operations (e.g. `createNewLap()`, peripheral persistence, or file exports) throw an unexpected exception.
3. *App Restart Cleanliness*:
• Setting `FINISHED = 1` guarantees that `hasUnfinishedWorkout()` evaluates to `false` on subsequent app launches, preventing false crash warning dialogs.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an active workout tracking session with `mWorkoutID > 0`,
• *When* the workout is stopped (via UI Stop, notification stop, or service teardown),
• *Then* `WorkoutSummaries.FINISHED = 1` SHALL be committed to SQLite and `TRACKING_FINISHED_INTENT` SHALL be broadcast containing `WORKOUT_ID`.
• *Given* a transient failure occurs during peripheral device persistence or live session export in `endWorkout()`,
• *When* the service tears down,
• *Then* `WorkoutSummaries.FINISHED = 1` SHALL still be committed and `TRACKING_FINISHED_INTENT` dispatched.
• *Given* a cleanly finished workout,
• *When* the athlete relaunches the application,
• *Then* `hasUnfinishedWorkout()` SHALL return `false` and `StartOrResumeDialog` SHALL NOT be shown.

**Invariants**: SQLite summary row integrity is strictly preserved; completion broadcasts reach MainActivityWithNavigation for automatic edit navigation.

### REQ-CON-010: Internal Sensor Pairing [Verified]
- **Tests**: `TST-BUG-009`
- **Implementation**: `DeviceManager.java`, `AltitudeFromPressureDevice.java`, `DeviceItem.kt`
- **Rationale**: The system SHALL allow the user to manually activate or deactivate internal smartphone sensors (e.g., Barometric Pressure Sensor) via the "My Sensors" interface. The system MUST respect this "Paired" state and only utilize telemetry from internal sources that are explicitly activated by the user.

### REQ-CON-011: Barometric Cold-Start Baseline Protection [Verified]
- **Tests**: `AltitudeFromPressureDevice.java`
- **Implementation**: Ensure barometric pressure sensors do not inject false extrema prior to calibration.
- **Rationale**: The `AltitudeFromPressureDevice` SHALL NOT emit uncalibrated standard atmosphere altitude readings (calculated using the default 1013.25 hPa assumption) to session listeners or running statistics prior to establishing a calibrated baseline (via known locations or valid GPS altitude fix), preventing cold-start pressure offsets from polluting session extrema.

### REQ-CON-012: Asynchronous BLE GATT Lifecycle, Thread-Safe Concurrency & Read Queue Null-Safety [Verified]
- **Tests**: `MyBTLEDevice.java`
- **Implementation**: Eliminate fatal NullPointerExceptions during asynchronous Bluetooth Low Energy communication and ensure thread-safe GATT connection lifecycle.
- **Rationale**: The system SHALL guarantee thread-safe lifecycle and communication management for all Bluetooth Low Energy (BLE) peripherals (`MyBTLEDevice`):
1. *Thread Synchronization & TOCTOU Immunity*: All operations interacting with `mBluetoothGatt` and `mReadCharacteristicQueue` SHALL use thread-safe synchronization primitives (a dedicated in-memory mutex `mGattLock` and thread-safe queue collections) and the local variable capture snapshot idiom (`final BluetoothGatt gatt = mBluetoothGatt; if (gatt != null) ...`), eliminating Time-of-Check to Time-of-Use (TOCTOU) race conditions between Binder callback threads and the UI Looper.
2. *Defensive Execution Guards*: Every characteristic read, characteristic write, descriptor write, and service discovery invocation SHALL verify that both `mBluetoothGatt != null` and the target characteristic/descriptor are non-null and that `Manifest.permission.BLUETOOTH_CONNECT` is granted before invoking framework APIs.
3. *Queue Purging & Connection Cleanup*: When transitioning to `STATE_DISCONNECTED`, upon search timeout, or during `shutDown()`, the system SHALL immediately clear `mReadCharacteristicQueue` and close and nullify the `mBluetoothGatt` reference under `mGattLock`, preventing stale or zombie operations against closed GATT instances.
4. *Handler Callback Lifecycle & Delayed Task Cancellation*: The system SHALL manage delayed periodic runnables (including the 5-minute battery percentage re-read `mBatteryReadRunnable`) using explicit object references and cancel them via `mHandler.removeCallbacks(...)` immediately upon disconnection or shutdown.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an active or disconnected `MyBTLEDevice` instance where `mBluetoothGatt` is null or closed,
• *When* `readNextCharacteristic()` is invoked (from a Binder thread or Looper),
• *Then* the method and any posted runnables SHALL complete cleanly without throwing `NullPointerException` or `SecurityException`.
• *Given* a periodic battery re-read scheduled via `mHandler.postDelayed`,
• *When* the peripheral disconnects or `shutDown()` is called prior to timer expiration,
• *Then* the pending runnable SHALL be immediately cancelled from `mHandler` and `mReadCharacteristicQueue` SHALL be emptied.
• *Given* an empty characteristic queue and `sensorsRegistered() == false`,
• *When* service discovery finalizes and descriptor notifications are configured,
• *Then* all GATT service, characteristic, and descriptor references SHALL be guarded against null references prior to invocation.

**Invariants**: Normal BLE connection, service discovery, telemetry reception (HR, Bike Cadence/Speed/Power, Run Speed), battery reporting, and Android 12+ permission handling MUST NOT be regressed.

### REQ-CON-013: Barometric Altitude Sensor Initialization & Null-Safe Correction Dispatch [Verified]
- **Tests**: `AltitudeFromPressureDevice.java`
- **Implementation**: Eliminate fatal JNI SIGABRT crashes caused by NullPointerException unboxing during hardware barometric sensor event dispatch on workout start.
- **Rationale**: The system SHALL guarantee null-safe initialization and altitude correction dispatch within `AltitudeFromPressureDevice`:
1. *Defensive Sensor Value Resolution*: In `AltitudeFromPressureDevice.setAltitudeCorrection(double correctAltitude)`, the system SHALL verify that `mAltitudeSensor != null` and `mAltitudeSensor.getValue() != null` prior to unboxing the current sensor reading.
2. *Cold-Start Raw Altitude Fallback*: If `mAltitudeSensor.getValue()` is null, the system SHALL calculate the altitude correction relative to the captured raw barometric altitude `mLastRawAltitude` (`mAltitudeCorrection = correctAltitude - mLastRawAltitude`).
3. *Double-Fault Graceful Degradation*: If both `mAltitudeSensor.getValue()` is null and `mLastRawAltitude` is `Double.NaN`, the system SHALL log an advisory warning and return gracefully without mutating `mAltitudeCorrection` or throwing any runtime exception.
4. *Atomic Broadcast Notification*: When `mAltitudeCorrection` is computed and non-zero, the system SHALL broadcast `ALTITUDE_CORRECTION_INTENT` containing `ALTITUDE_CORRECTION_VALUE` to ensure downstream consumers (`TrackerService`) atomically shift existing samples.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an `AltitudeFromPressureDevice` where `mAltitudeSensor.getValue()` is null and `mLastRawAltitude` is 500.0m,
• *When* `setAltitudeCorrection(520.0)` is invoked (e.g. from `initPressureSensor()` upon matching a known location with reference altitude 520.0m),
• *Then* `mAltitudeCorrection` SHALL be set to +20.0m, a broadcast with value 20.0 SHALL be sent, and no `NullPointerException` SHALL be thrown.
• *Given* an `AltitudeFromPressureDevice` where `mAltitudeSensor.getValue()` is 505.0m,
• *When* `setAltitudeCorrection(520.0)` is invoked,
• *Then* `mAltitudeCorrection` SHALL be set to +15.0m and broadcasted.
• *Given* an `AltitudeFromPressureDevice` where `mAltitudeSensor.getValue()` is null and `mLastRawAltitude` is `Double.NaN`,
• *When* `setAltitudeCorrection(520.0)` is invoked,
• *Then* the method SHALL log a warning and return cleanly without throwing an exception or sending an invalid broadcast.

**Invariants**: Existing downstream broadcast consumption by `TrackerService` (`REQ-FIL-004`), location learning using uncorrected raw measurements (`REQ-DAT-007`), and cold-start baseline protection (`REQ-CON-011`) MUST NOT be altered.

### REQ-CON-014: BLE GATT Measurement Packet Parsing Resilience & Null-Safe Telemetry Extraction [Verified]
- **Tests**: `BTLEBikePowerDevice.java`, `BTLEBikeDevice.java`, `BTLERunSpeedDevice.java`, `BTLEHeartRateDevice.java`
- **Implementation**: Eliminate fatal NullPointerExceptions during asynchronous Bluetooth Low Energy characteristic parsing caused by auto-unboxing null Integers on truncated sensor notifications.
- **Rationale**: The system SHALL guarantee null-safe and length-validated parsing for all incoming Bluetooth Low Energy (BLE) GATT measurement characteristics across all supported sensor device types (`BTLEBikePowerDevice`, `BTLEBikeDevice`, `BTLERunSpeedDevice`, `BTLEHeartRateDevice`):
1. *Mandatory Header Length Verification*: Prior to reading flags or mandatory telemetry metrics, the system SHALL verify that `characteristic.getValue()` is non-null and that its byte length is greater than or equal to the minimum required specification length (e.g. 4 bytes for Cycling Power: 2-byte flags + 2-byte instantaneous power). If the payload is truncated below this threshold, the packet SHALL be discarded with an advisory warning.
2. *Conditional Field Bounds Checking*: For each optional telemetry field indicated by packet flags (Pedal Power Balance, Accumulated Torque, Wheel Revolution Data, Crank Revolution Data, Stride Length, Total Distance, Heart Rate UINT16):
• The system SHALL verify that the payload length is at least `offset + fieldWidth` before reading the field or advancing the offset pointer.
• If the remaining bytes are insufficient, the system SHALL safely suppress parsing of that field and subsequent dependent fields.
3. *Null-Safe Auto-Unboxing Immunity*: All calls to `characteristic.getIntValue(...)` SHALL be guarded against `null` returns prior to primitive assignment (`int`, `long`, `double`). If `getIntValue()` returns `null`, the system SHALL NOT attempt primitive unboxing.
4. *Main Looper Crash Immunity*: Malformed, truncated, or out-of-order BLE GATT packets received from physical sensors SHALL NOT cause `NullPointerException` or unhandled exceptions on the UI/Main Looper thread.

**Acceptance Criteria (Given-When-Then)**:
• *Given* a connected `BTLEBikePowerDevice`,
• *When* a notification arrives with flags `0x0020` (Crank Revolution Data Present) and Instantaneous Power, but the payload has fewer than 8 bytes (e.g. 6 bytes missing crank event timestamp),
• *Then* the device SHALL parse instantaneous power normally, discard the truncated crank data without throwing `NullPointerException`, and log an advisory warning.
• *Given* a connected `BTLEBikePowerDevice`,
• *When* a notification arrives with flags `0x0010` (Wheel Revolution Data Present) but only 4 bytes of total payload,
• *Then* the device SHALL process power and safely skip wheel data without throwing NPE.
• *Given* a notification where `characteristic.getValue()` is empty (`new byte[0]`) or `null`,
• *When* `measurementCharacteristicUpdate` is invoked,
• *Then* the method SHALL return immediately without error.
• *Given* `BTLEBikeDevice`, `BTLERunSpeedDevice`, or `BTLEHeartRateDevice`,
• *When* a truncated packet is received,
• *Then* the devices SHALL safely drop the invalid packet with zero crashes.
• *Given* valid, full-length BLE packets conforming to Bluetooth SIG specifications,
• *Then* power, cadence, speed, distance, and heart rate metrics SHALL be parsed and emitted with 100% mathematical fidelity.

**Invariants**: BLE telemetry calculation formulas (power, cadence, speed, distance, wheel revolutions, power balance) and GATT connection lifecycle management (`REQ-CON-012`) MUST NOT be altered.

### REQ-CON-015: Smartphone Battery Level and Estimated Remaining Duration Telemetry Sensors (`PHONE_BATTERY` & `BATTERY_REMAINING_TIME`) [Verified]
- **Tests**: `SensorType.java`, `BatteryDevice.java`, `DeviceManager.java`, `ActivityType.java`, `BatteryRemainingTimeFormatter.java`
- **Implementation**: Provide real-time visibility into smartphone battery level and projected remaining duration to prevent unexpected device shutdown during long endurance athletic sessions.
- **Rationale**: The system SHALL provide live telemetry sensors for monitoring the host smartphone's internal battery level and projecting remaining operating duration during athletic workouts (ATT-1454):
1. *Base Smartphone Battery Percentage (`PHONE_BATTERY`)*:
• The system SHALL register an internal `BatteryDevice` (`MyDevice`) that listens to Android system battery state broadcasts (`Intent.ACTION_BATTERY_CHANGED`).
• The sensor SHALL report instantaneous state of charge as an integer percentage in the range $0\dots 100\%$ with unit `%` (`IntegerFormatter`).
2. *Derived Remaining Battery Duration (`BATTERY_REMAINING_TIME`)*:
• The system SHALL compute an internal battery drain rate ($\Delta\% / \Delta t$ in $\%/\text{h}$) strictly over active (non-paused) workout recording duration.
• The projected remaining duration SHALL be derived via $\text{Remaining Time } [h] = \frac{\text{Current Battery } [\%]}{\text{Drain Rate } [\%/h]}$.
• The sensor reading SHALL be formatted in hours and minutes (`H:MM h`, e.g. `8:45 h`) via a dedicated formatter.
3. *Graceful Fallback & Charging States*:
• During the initial workout baseline stabilization period (first 5 minutes of active recording or prior to measuring $\ge 1\%$ battery consumption), the remaining time sensor SHALL display `--:--`.
• When connected to external power (`BatteryManager.BATTERY_STATUS_CHARGING` or `BATTERY_STATUS_FULL`), the remaining time sensor SHALL display the localized string for charging (`Lädt` / `Charging`) or `--:--` and MUST NOT project false depletion times.
• If computed drain rate is $\le 0$ without charging status, the sensor SHALL display `--:--`.
4. *Cockpit & Activity Integration*:
• `PHONE_BATTERY` and `BATTERY_REMAINING_TIME` SHALL be integrated into `SensorType.java` and available for selection across all activity sport types in `ActivityType.java`.
• 100% localization parity for sensor names, short names, units, and fallback strings SHALL be maintained across all 9 supported locales (`de`, `en`, `es`, `fr`, `it`, `ja`, `nl`, `pl`, `pt`).
5. *Efficiency & Lifecycle Invariants*:
• Sensor evaluation SHALL execute strictly in-memory during 1Hz telemetry updates with zero wake-locks and zero background disk I/O.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an active workout session with the host smartphone battery at 84%,
• *When* the athlete views a cockpit tab configured with `PHONE_BATTERY`,
• *Then* the tile SHALL render `84` with unit `%`.
• *Given* an active workout in its first 3 minutes of recording,
• *When* the cockpit renders `BATTERY_REMAINING_TIME`,
• *Then* the tile value SHALL render `--:--`.
• *Given* an active workout where 2% battery has drained over 30 minutes of active recording (drain rate = 4.0%/h) and current battery is 80%,
• *When* `BATTERY_REMAINING_TIME` is resolved,
• *Then* the tile SHALL display `20:00 h`.
• *Given* the smartphone is connected to a power bank during tracking,
• *When* `BATTERY_REMAINING_TIME` is rendered,
• *Then* the tile SHALL display `Lädt` (in German) or `Charging` (in English).

**Invariants**: Existing sensor pipelines, peripheral sensor battery alerts (`REQ-CON-007`), cockpit rendering layouts, and 1Hz dispatch threading MUST NOT be regressed.

### REQ-CON-016: Smartphone Battery Remaining Duration Continuous Countdown & Discrete Transition Drain Rate Architecture [Verified]
- **Tests**: `BatteryDevice.java`
- **Implementation**: Compute battery drain rate strictly across discrete percentage step-down events and provide a continuous monotonic countdown while percentage is constant, eliminating upward drift and abrupt jumps.
- **Rationale**: The system SHALL compute smartphone battery drain rate strictly across discrete percentage step-down transition events and provide a continuous, monotonic countdown during periods of constant battery level, eliminating sampling-induced upward drift and abrupt multi-hour estimate plunges (ATT-1631):
1. *Discrete Transition-Triggered Drain Rate Computation (`BatteryDevice.java`)*:
• The system SHALL record discrete battery percentage transition points: `BatteryStep(int activeSeconds, int percent)`.
• Baseline initialization: At workout start or accumulator reset, record `(mActiveRecordingSeconds, mBatteryLevel)`.
• Step-down recording: A new `BatteryStep` SHALL be recorded strictly when `mBatteryLevel < lastRecordedStep.percent`.
• Drain rate evaluation: When evaluating drain rate, the system SHALL calculate the rate between the oldest step in the rolling window ($t_0, P_0$) and the latest step ($t_k, P_k$): $\Delta t = t_k - t_0$, $\Delta P = P_0 - P_k$, $\text{drainRatePerHour} = \frac{\Delta P}{\Delta t / 3600.0}$. When $\Delta t \ge \text{MIN\_STABILIZATION\_SECONDS}$ (300s) and $\Delta P \ge 1$, update `mEstablishedDrainRatePerHour = drainRatePerHour`, `mBaseRemainingSeconds = round((mBatteryLevel / drainRatePerHour) * 3600.0)`, and `mLastStepActiveSeconds = t_k`.
2. *Continuous Monotonic Countdown Between Step Transitions*:
• During active recording while the battery percentage remains constant ($t > mLastStepActiveSeconds$), remaining seconds SHALL count down: $\text{elapsedSinceStep} = mActiveRecordingSeconds - mLastStepActiveSeconds$, $\text{remainingSeconds} = mBaseRemainingSeconds - elapsedSinceStep$.
• *Monotonicity Invariant*: Remaining duration SHALL NEVER increase or drift upwards while battery percentage is constant.
• *Lower Bound Floor Clamping*: Remaining seconds SHALL be clamped to the floor of $(P - 1)$: $\text{floorSeconds} = \text{round}(((mBatteryLevel - 1) / mEstablishedDrainRatePerHour) * 3600.0)$, $\text{remainingSeconds} = \max(\text{floorSeconds}, \text{remainingSeconds})$, $\text{remainingSeconds} = \max(0, \text{remainingSeconds})$.
3. *Warmup & Stabilization*:
• The sensor SHALL emit `BatteryRemainingTimeFormatter.STABILIZING_STATUS_CODE` (`-2`, formatting as `--:--`) until active recording time reaches $\ge 300\text{s}$ AND a drop of $\ge 1\%$ is measured.
• If a 1% drop occurs prior to 300s, stabilization completes once total active recording reaches 300s using the effective elapsed time.
4. *Charging & Pause Invariants*:
• When connected to charger (`mIsCharging == true`), the sensor emits `CHARGING_STATUS_CODE` (`-1`).
• When tracking is paused, `mActiveRecordingSeconds` does not increment, preserving countdown state without drift.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: `REQ-CON-015` (*Smartphone Battery Level and Estimated Remaining Duration Telemetry Sensors*), targeting `BatteryDevice.java`.
2. *Historical Origin & Commit Trace*: Commit `809bc479` (Ticket `ATT-1454`, Sprint `2026-40.4`).
3. *Root Reason for Existing Formulation*: `ATT-1454` added periodic 30s samples to estimate drain rate, not anticipating that discrete 1% quantization causes time $\Delta t$ to increase while $\Delta P$ is frozen at an integer, driving projected remaining time artificially upward.
4. *Refinement Reason*: On-road endurance testing demonstrated that constant battery levels caused remaining time to climb by several hours, followed by a violent downward jump upon the next 1% drop. Transition-based rate calculation combined with intra-step countdown restores accurate physical behavior.
5. *Preservation of Core Invariants*: 1Hz sensor dispatch, zero wake-locks, zero disk I/O, `PHONE_BATTERY` accuracy, and charging detection remain 100% intact.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an active workout tracking session where a battery drop has established a drain rate (e.g. 5.0%/h at 80%),
• *When* the battery level remains at 80% for several subsequent minutes,
• *Then* `BATTERY_REMAINING_TIME` SHALL count down steadily (decreasing second-by-second) and SHALL NEVER increase.
• *Given* an established drain rate and countdown at level $N$,
• *When* the battery level transitions from $N$ to $N - 1$,
• *Then* the new drain rate computed from the transition window SHALL smoothly adjust without erratic multi-hour jumps.
• *Given* active tracking in the first 300 seconds without confirmed drop,
• *When* `BATTERY_REMAINING_TIME` is queried,
• *Then* it SHALL return `STABILIZING_STATUS_CODE` (`--:--`).
• *Given* an established drain rate of 10%/h (expected 360s per 1%),
• *When* active recording extends past 360s at the same percentage (e.g. 600s),
• *Then* remaining seconds SHALL NOT count down below the $(P - 1)$ threshold.

**Invariants**: PHONE_BATTERY reporting (0–100%), charging status detection, 1Hz telemetry dispatch, and zero wake-lock/disk-I/O invariants MUST NOT be broken.

### REQ-CON-017: Barometric Altimeter Idempotent Calibration, Delta Shift Dispatch, Physical Sanity Limiting & Historical Workout Data Sanitization [Verified]
- **Tests**: \ge 0.1\text{ m}$. If $
- **Implementation**: \text{deltaOffset}
- **Rationale**: The system SHALL eliminate barometric calibration oscillations, prevent runaway retroactive sample accumulation, enforce physical climb rate sanity limits, and strictly protect legitimate high-altitude workout data (ATT-1810, ATT-3990):
1. *Absolute Baseline Correction Formulation (`AltitudeFromPressureDevice.java`)*:
• In `AltitudeFromPressureDevice.setAltitudeCorrection(double correctAltitude)`, `mAltitudeCorrection` SHALL be computed strictly relative to raw barometric pressure: $\text{newCorrection} = \text{correctAltitude} - \text{mLastRawAltitude}$.
• The method SHALL compute $\text{deltaOffset} = \text{newCorrection} - \text{mAltitudeCorrection}$, update $\text{mAltitudeCorrection} = \text{newCorrection}$, set $\text{mIsCalibrated} = \text{true}$, and publish $\text{mAltitudeSensor.newValue}(\text{mLastRawAltitude} + \text{mAltitudeCorrection})$.
• `ALTITUDE_CORRECTION_INTENT` SHALL be broadcast containing `ALTITUDE_CORRECTION_VALUE = deltaOffset` IF AND ONLY IF $

### REQ-CON-018: Robust Bluetooth LE Sensor Discovery: GATT Queue Deadlock Prevention and State Reset in BTSearchForNewDevicesEngine [Verified]
- **Tests**: `BTSearchForNewDevicesEngine.java`
- **Implementation**: Eliminate silent BLE discovery freezes caused by missing optional GATT characteristics and ensure connection state is cleared across searches.
- **Rationale**: The system SHALL guarantee robust, non-blocking GATT characteristic queue management and state reset in `BTSearchForNewDevicesEngine` during peripheral discovery (ATT-2224):
1. *Defensive Queue Enqueueing*: In `onServicesDiscovered()`, only non-null `BluetoothGattCharacteristic` references returned by `service.getCharacteristic(...)` SHALL be appended to `mReadCharacteristicQueue`.
2. *Deadlock-Free Queue Progression*: In `readNextCharacteristic(String address)`:
• If the polled characteristic is `null`, the engine SHALL immediately advance by invoking `readNextCharacteristic(address)`.
• When dispatching `gatt.readCharacteristic(characteristic)`, the engine SHALL evaluate the boolean return value. If `readCharacteristic(...)` returns `false` (indicating failure to enqueue in the Bluetooth stack), the engine SHALL immediately advance by invoking `readNextCharacteristic(address)`.
3. *Resilient Device Notification (`newDeviceFound`)*: When all characteristics in the queue are read or skipped, the engine SHALL invoke `newDeviceFound(address)` to notify the listener, even if optional characteristics (Manufacturer Name, Battery Level) were omitted or failed to read.
4. *Comprehensive Lifecycle & State Reset*: In `stopAsyncSearch()` and `startAsyncSearch()`, the engine SHALL reset tracking state, clearing `mBTGatts`, `mReadCharacteristicQueue`, `mInformedDevices`, `mNameMap`, `mManufacturerMap`, and `mBatteryPercentage`. On `stopAsyncSearch()`, all active `BluetoothGatt` connections SHALL be disconnected and closed prior to clearing the map.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement (`REQ-CON-018`), extending GATT queue safety and connection lifecycle guarantees established in `REQ-CON-012` to `BTSearchForNewDevicesEngine.java` under Epic `ATT-355` / Sensor Connectivity.
2. *Historical Origin & Commit Trace*: Sprint 2026-40.15 (`ATT-2224`).
3. *Root Reason for Existing Formulation*: `BTSearchForNewDevicesEngine` added unchecked nulls to the read queue and lacked fail-forward handling, permanently deadlocking BLE device discovery on simulated or third-party sensors lacking optional descriptors.
4. *Preservation of Core Invariants*: Runtime telemetry parsing (`REQ-CON-012`, `REQ-CON-014`), BLE permissions (`BLUETOOTH_CONNECT`), and 100% test suite pass rate are strictly preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* a discovered BLE peripheral whose Device Information or Battery service returns null for requested characteristics,
• *When* `onServicesDiscovered` executes,
• *Then* zero null elements SHALL be added to `mReadCharacteristicQueue`.
• *Given* a characteristic queue containing a null entry or where `gatt.readCharacteristic()` returns `false`,
• *When* `readNextCharacteristic()` is executed,
• *Then* the engine SHALL skip to the next characteristic without stalling or terminating the discovery loop.
• *Given* a Bike Power or Heart Rate sensor lacking optional Device Information or Battery descriptors,
• *When* discovery finishes reading available characteristics,
• *Then* `newDeviceFound(address)` SHALL be invoked and the device SHALL be presented to the user.
• *Given* an active search where devices were detected and connected,
• *When* `stopAsyncSearch()` is called and a new search is started,
• *Then* previously connected devices SHALL be redisoverable and reconnectable.

**Invariants**: Runtime sensor operation in `MyBTLEDevice.java`, Bluetooth permissions, and clean-room test suite pass rate MUST NOT be altered.

### REQ-CON-019: Robust Bluetooth LE Sensor Discovery & Direct LE Transport Connection Architecture [Verified]
- **Tests**: connectedDevice"`.
• In `TrackerService.java`, `performStartForeground` SHALL include `ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH` and `ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE` in its bitmask on API 34+.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement (`REQ-CON-019`), extending and refining `REQ-CON-012` and `REQ-CON-018` under Epic `ATT-355` / Sensor Connectivity & Protocols.
2. *Historical Origin & Commit Trace*: Sprint 2026-41.4 (`ATT-2773`), refining legacy GATT connection code from Android 4.3 and Android 12 migration snippet from commit `67451a9`, refined in Sprint 2026-43.4 under `ATT-4449`.
3. *Root Reason for Existing Formulation*: Omitting `TRANSPORT_LE` defaulted to `TRANSPORT_AUTO`, failing with status 133 on BLE-only peripherals. Asserting `neverForLocation` was mistakenly omitted in ATT-2773, which caused Android 12+ Bluetooth framework to require runtime `ACCESS_FINE_LOCATION` to deliver BLE scan results; restoring `neverForLocation` guarantees scan delivery without location. Permissions and foreground service types lacked API 31+ and API 34+ compliance.
4. *Preservation of Core Invariants*: Thread safety and TOCTOU immunity with `mGattLock` (`REQ-CON-012`), GATT queue progression and state reset (`REQ-CON-018`), telemetry parsing algorithms (`REQ-CON-014`), location tracking independence, and 100% full-suite unit test pass rate are strictly preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an active paired BLE sensor instance in `MyBTLEDevice` running on Android 6.0+ (API 23+),
• *When* `startSearching()` is executed,
• *Then* the device SHALL invoke `device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)`.
• *Given* a discovered BLE peripheral in `BTSearchForNewDevicesEngine` running on Android 6.0+ (API 23+),
• *When* `mScanCallback.onScanResult()` handles the advertisement,
• *Then* the engine SHALL invoke `device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)`.
• *Given* an Android 12+ device running `aTrainingTracker` with granted fine location permissions,
• *When* scanning for new devices with `deviceType == DeviceType.ALL` (`ScanFilter == null`),
• *Then* the OS Bluetooth stack SHALL NOT discard incoming advertisements and all nearby BLE peripherals SHALL be delivered to `mScanCallback`.
• *Given* an Android 12+ device where `BLUETOOTH_SCAN` permission is not granted,
• *When* `startAsyncSearch()` is invoked,
• *Then* the engine SHALL abort scan initialization gracefully without throwing `SecurityException`.
• *Given* `startAsyncSearch()` running on API 23+,
• *When* `ScanSettings` is constructed,
• *Then* the settings SHALL incorporate `CALLBACK_TYPE_ALL_MATCHES`, `MATCH_MODE_AGGRESSIVE`, and `MATCH_NUM_ONE_ADVERTISEMENT`.
• *Given* tracking active on Android 14+ with connected BLE peripherals,
• *When* `TrackerService` or `BANALService` runs in the foreground,
• *Then* the service declarations and runtime invocations SHALL include `connectedDevice` and `health`.
• *Given* an Android 12+ device running `aTrainingTracker` with granted `BLUETOOTH_SCAN` permission and WITHOUT `ACCESS_FINE_LOCATION`,
• *When* scanning for BLE sensors,
• *Then* incoming BLE advertisements SHALL NOT be discarded and nearby BLE peripherals SHALL be delivered to `mScanCallback`.

**Invariants**: Thread safety (`mGattLock`), GATT queue deadlock immunity (`REQ-CON-018`), telemetry measurement parsing (`REQ-CON-014`), and 100% clean-room test pass rate MUST NOT be broken.
- **Implementation**: health
- **Rationale**: The system SHALL guarantee direct Low Energy physical transport connections, unsuppressed BLE scan result delivery, modern scan settings, robust runtime permission verification, and Android 14+ foreground service compliance across all Bluetooth LE discovery and communication workflows (ATT-2773, refined by ATT-4449):
1. *Direct Low Energy Transport Specification (`TRANSPORT_LE`)*:
• In `MyBTLEDevice.java` (`startSearching()`) and `BTSearchForNewDevicesEngine.java` (`mScanCallback.onScanResult`), on Android 6.0+ (`Build.VERSION.SDK_INT >= Build.VERSION_CODES.M`), the system SHALL invoke `device.connectGatt(mContext, false, mGattCallback, BluetoothDevice.TRANSPORT_LE)`.
• On legacy platforms (`Build.VERSION.SDK_INT < Build.VERSION_CODES.M`), the system SHALL fall back cleanly to `device.connectGatt(mContext, false, mGattCallback)`.
2. *Unsuppressed BLE Scan Result Delivery (`AndroidManifest.xml`)*:
• In `AndroidManifest.xml`, `<uses-permission android:name="android.permission.BLUETOOTH_SCAN" />` SHALL declare `android:usesPermissionFlags="neverForLocation"` and `tools:targetApi="s"` (refined by ATT-4449).
• The application asserts to the Android platform that BLE scanning is used exclusively for wireless sensor telemetry and never to derive user location, enabling `BluetoothLeScanner` to deliver scan advertisements on Android 12+ (API 31+) without requiring runtime location permissions (`ACCESS_FINE_LOCATION`).
3. *Robust Runtime Permission Verification (`BTSearchForNewDevicesEngine.java`)*:
• In `startAsyncSearch()`: On Android 12+ (`Build.VERSION.SDK_INT >= Build.VERSION_CODES.S`), verify `ContextCompat.checkSelfPermission(mContext, Manifest.permission.BLUETOOTH_SCAN) == PERMISSION_GRANTED` before invoking `scanner.startScan()`. On Android < 12, verify `ACCESS_FINE_LOCATION`. If denied, log an advisory warning and return cleanly without throwing `SecurityException`.
• In `stopAsyncSearch()`: On Android 12+, verify `BLUETOOTH_SCAN` before invoking `scanner.stopScan()`.
• In `mScanCallback.onScanResult`: On Android 12+, verify `BLUETOOTH_CONNECT` before invoking `device.connectGatt()`.
4. *Low-Latency Aggressive Scan Configuration (`BTSearchForNewDevicesEngine.java`)*:
• On Android 6.0+ (`Build.VERSION.SDK_INT >= Build.VERSION_CODES.M`), `ScanSettings.Builder` SHALL configure `SCAN_MODE_LOW_LATENCY`, `CALLBACK_TYPE_ALL_MATCHES`, `MATCH_MODE_AGGRESSIVE`, and `MATCH_NUM_ONE_ADVERTISEMENT`.
• On Android < 6.0, `ScanSettings.Builder` SHALL configure `SCAN_MODE_LOW_LATENCY`.
5. *Android 14+ Foreground Service Compliance (`AndroidManifest.xml` & `TrackerService.java`)*:
• `AndroidManifest.xml` SHALL declare `<uses-permission android:name="android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE" />`.
• `BANALService` and `TrackerService` declarations SHALL include `android:foregroundServiceType="location

### REQ-WEA-001: Support Pebble smartwatches for live metrics [Verified]
- **Tests**: `TST-MAN-001`
- **Implementation**: `PebbleService.java`
- **Rationale**: Allow users to view data on their wrist.
- **Description**: Support Pebble smartwatches for live metrics.

### REQ-FIL-007: EKF Sensor Fusion [Planned]
- **Tests**: `TST-FUSION-001`
- **Implementation**: TBD
- **Rationale**: The system SHALL implement an Extended Kalman Filter to fuse GPS, IMU (Accel/Mag), and Hardware Speed Sensors for high-precision state estimation.
- **Description**: EKF Sensor Fusion.

### REQ-FIL-008: Body-to-World Frame Transformation [Planned]
- **Tests**: `TST-FUSION-001`
- **Implementation**: TBD
- **Rationale**: The system SHALL rotate raw phone MEMS data into the geographic East-North-Up (ENU) frame using a 3x3 Rotation Matrix or Quaternions.
- **Description**: Body-to-World Frame Transformation.

### REQ-FIL-009: Dead Reckoning Navigation [Planned]
- **Tests**: `TST-FUSION-002`
- **Implementation**: TBD
- **Rationale**: In GPS-denied environments, the system SHALL maintain location and speed predictions using integrated IMU and wheel speed data.
- **Description**: Dead Reckoning Navigation.

### REQ-FIL-010: Speed Sensor Auto-Calibration [Planned]
- **Tests**: `TST-FUSION-003`
- **Implementation**: TBD
- **Rationale**: The system SHALL dynamically solve for the "Wheel Scale Factor" within the EKF state vector to automate circumference calibration.
- **Description**: Speed Sensor Auto-Calibration.

### REQ-FIL-011: Null-Safe Sensor Filtering [Verified]
- **Tests**: `TST-FIL-001`
- **Implementation**: `MyFilter.java`, `AverageFilter.java`, `ExponentialSmoothingFilter.java`, `NumberedMovingAverageFilter.java`
- **Rationale**: All sensor filters SHALL gracefully handle null values passed from the sensor manager. The filters SHALL NOT attempt to process null data and SHALL NOT crash when a sensor provides a null value (e.g., during initialization or signal loss).

### REQ-TRK-017: Guaranteed Defensive Location Device Initialization on Tracking Startup and BANAL Connection [Verified]
- **Tests**: `TrackerService.java`, `BANALService.java`, `DeviceManager.java`, `TrainingApplication.java`
- **Implementation**: Guarantee defensive location device instantiation and update registration on tracking startup and BANAL connection.
- **Rationale**: The system SHALL guarantee the lazy instantiation and update registration of GPS, Fused, and paired Network location devices when starting a workout or connecting `TrackerService` to `BANALService`, preventing zero-distance trackpoints and missing coordinates on fresh installations or when permissions are granted dynamically (ATT-3045):
1. *Pre-Flight & Service Connection Initialization (`TrackerService.java`)*:
• In `TrackerService.mBanalConnection.onServiceConnected()`: The service SHALL defensively invoke `BANALService.checkOrInitializeLocationDevices()` prior to evaluating `!BANALService.isSearching()` or calling `onSearchingFinished()`. This ensures that when `onSearchingFinished()` reads `mBanalService.getGCDataString()`, location sensors (`G`, `D`, `S`) are already instantiated and registered in `MySensorManager`..
• In `TrackerService.onStartCommand()`: If `mBanalService != null`, the system SHALL defensively invoke `BANALService.checkOrInitializeLocationDevices()`..
• In `TrainingApplication.startTracking()`: Prior to starting `TrackerService`, the application SHALL invoke `BANALService.checkOrInitializeLocationDevices()`..
2. *Service & Interface Parity (`BANALService.java`)*:
• `BANALServiceComm` interface SHALL expose `void checkOrInitializeLocationDevices()`..
• In `BANALService.onCreate()`: After initializing `cDeviceManager`, `checkOrInitializeLocationDevices()` SHALL be invoked to capture permissions granted prior to or during service creation..
3. *Unified Device Initialization & Active Re-Registration (`DeviceManager.java` & `SpeedAndLocationDevice_GPS.java`)*:
• In `DeviceManager.java`: The constructor SHALL delegate location device setup to `checkOrInitializeLocationDevices()`..
• In `DeviceManager.checkOrInitializeLocationDevices()`: If `mSpeedAndLocationDevice_GPS == null`, paired in DB, and `ACCESS_FINE_LOCATION` is granted, instantiate `SpeedAndLocationDevice_GPS`. If `mSpeedAndLocationDevice_GoogleFused == null`, paired in DB, `ACCESS_FINE_LOCATION` is granted, and Play Services is available, instantiate `SpeedAndLocationDevice_GoogleFused`. If `mSpeedAndLocationDevice_Network == null`, paired in DB, and `ACCESS_COARSE_LOCATION` is granted, instantiate `SpeedAndLocationDevice_Network`..
• If `mSpeedAndLocationDevice_GPS != null`, `DeviceManager` SHALL ensure its location updates are registered with `LocationManager` (`checkOrReRegisterLocationUpdates()`), recovering from transient provider unavailability or permission escalation..
4. *Preservation of System Invariants*:
• Precise Location gating in `ControlTrackingScreen.kt` (`REQ-PRI-004`), Android 14+ FGS bitmask derivation (`REQ-TRK-015`), wake lock acquisition (`REQ-TRK-003`), 1Hz deterministic sampling (`REQ-TRK-007`), and 100% full clean-room unit test pass rate MUST be strictly preserved..

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement (`REQ-TRK-017`), refining and hardening `REQ-PRI-004` (*Mandatory Precise Location Gating and Dynamic Sensor Device Instantiation on Permission Escalation*, Sprint 2026-41.1, ATT-2357).
2. *Historical Origin & Commit Trace*: Sprint 2026-41.1 (`ATT-2357`, commit `54e20790`).
3. *Root Reason for Existing Formulation*: `checkOrInitializeLocationDevices()` was previously only invoked in UI callbacks (`ControlTrackingScreen`, `MainActivityWithNavigation`), which dropped silently when `BANALService` was not yet bound on fresh installs.
4. *Preservation of Core Invariants*: Precise location gating, FGS foreground service promotion, sensor GCData string integrity, and full-suite test pass rate remain strictly preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* a fresh install of the app where `ACCESS_FINE_LOCATION` was granted immediately before starting tracking,
• *When* `TrackerService` connects to `BANALService`,
• *Then* `BANALService.checkOrInitializeLocationDevices()` executes before `onSearchingFinished()`, `mSpeedAndLocationDevice_GPS` is instantiated, and `getGCDataString()` contains 'G', 'D', and 'S'.
• *Given* `SpeedAndLocationDevice_GPS` initialized when GPS provider was temporarily unavailable or disabled,
• *When* tracking starts or `checkOrInitializeLocationDevices()` is invoked after provider becomes enabled,
• *Then* location listener updates are actively registered without requiring app restart.
• *Given* an active tracking session started after fresh permission grant,
• *When* recorded trackpoints are saved,
• *Then* `WorkoutSamples.db` contains non-null `LATITUDE`, `LONGITUDE`, and non-zero cumulative `DISTANCE_m`.

**Invariants**: Location gating (`REQ-PRI-004`), Android 14+ FGS bitmask (`REQ-TRK-015`), and 100% clean-room test pass rate MUST NOT be broken.

### REQ-FIL-013: Continuous Pre-Start Rolling Telemetry Filtering, Session Reset Isolation & Live Cockpit Metric Readiness [Verified]
- **Tests**: `ExponentialSmoothingFilter.java`, `TimedMovingAverageFilter.java`, `NumberedMovingAverageFilter.java`, `AverageFilter.java`, `MaxValueFilter.java`, `MyFilter.java`, `FilterManager.java`, `VerticalSpeedAndSlopeDevice.java`
- **Implementation**: Decouple rolling smoothing filters from pause state, process sensor samples continuously in pre-start ready mode, isolate cumulative workout session statistics, and execute lifecycle accumulator resets upon starting recording.
- **Rationale**: The system SHALL decouple rolling telemetry smoothing filters from recording state, process sensor samples continuously in pre-start ready mode, isolate cumulative workout session statistics, and execute lifecycle accumulator resets upon starting recording (ATT-3222, refining `REQ-FIL-001`, `REQ-FIL-002`, `REQ-FIL-003`, and `REQ-FIL-011`):
1. *Continuous Rolling Smoothing Telemetry*:
• In `ExponentialSmoothingFilter.java`, `TimedMovingAverageFilter.java`, and `NumberedMovingAverageFilter.java`, the system SHALL remove the `!TrainingApplication.isPaused()` guard from `newValue(Number value)`.
• Non-null sensor readings emitted to rolling smoothing filters SHALL be processed continuously regardless of tracking state (`TrackingMode.READY`, `TrackingMode.TRACKING`, or `TrackingMode.PAUSED`).
2. *Null-Safety Invariant (`REQ-FIL-011`)*:
• Passing `null` to `newValue()` MUST NOT cause exceptions or mutate internal filter state.
3. *Cumulative Workout Session Statistics Isolation*:
• Cumulative session aggregators (`AverageFilter.java`, `MaxValueFilter.java`) SHALL accumulate samples ONLY during active recording (`!TrainingApplication.isPaused()`). Readings in `TrackingMode.READY` or `TrackingMode.PAUSED` MUST NOT pollute recorded workout summary statistics.
4. *Filter Lifecycle Reset Contract*:
• Base class `MyFilter<T>` SHALL declare `public void reset()` (default no-op).
• `ExponentialSmoothingFilter` SHALL implement `reset()` by resetting `mFilteredValue = 0.0`.
• `TimedMovingAverageFilter` SHALL implement `reset()` by clearing `mTimestampedValues`.
• `NumberedMovingAverageFilter` SHALL implement `reset()` by clearing `mValues` and resetting `mIndex = 0`.
• `AverageFilter` SHALL implement `reset()` by resetting `mAccumulatedValue = 0.0` and `mNumberOfSamples = 0`.
• `MaxValueFilter` SHALL implement `reset()` by resetting `mMaxValue = 0`.
5. *Accumulator Reset Intent Dispatch*:
• `FilterManager.java` SHALL register a `BroadcastReceiver` for `BANALService.RESET_ACCUMULATORS_INTENT` (`ContextCompat.RECEIVER_NOT_EXPORTED`).
• Upon receiving `RESET_ACCUMULATORS_INTENT`, `FilterManager` SHALL execute `reset()` across all registered filters in `myFilterMap`.
• `FilterManager.shutDown()` SHALL safely unregister the receiver.
6. *Virtual Device Pre-Start Telemetry*:
• `VerticalSpeedAndSlopeDevice` SHALL query live filtered altitude and speed from `cAltitudeFilter` and `cSpeedFilter` in `TrackingMode.READY`, calculating and broadcasting live vertical speed (VAM) and slope percentage prior to workout start.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Target & Historical Trace*: Refines `REQ-FIL-001` (*Flexible filtering*), `REQ-FIL-002` (*Exponential smoothing for power*), and `REQ-FIL-003` (*Derived metrics*). Originates from commit `d937073c` where `!isPaused()` was added indiscriminately to all filters.
2. *Root Reason for Existing Formulation*: Conflating rolling smoothing filters (which represent a window of current physical reality) with cumulative aggregators (which represent workout totals) caused rolling filters to freeze and show dashes before tracking starts.
3. *Preservation of Core Invariants*: Null-safety (`REQ-FIL-011`), VAM stabilization gating (`REQ-FIL-012`), recorded workout statistical isolation, and full clean-room test pass rate remain strictly preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an active BLE or ANT+ power meter or heart rate sensor connected in `TrackingMode.READY`,
• *When* the athlete pedals or warms up,
• *Then* cockpit tiles configured with rolling filters (3s power, smoothed HR) render live calculated wattage/BPM instead of `--`.
• *Given* GPS and altitude sensors streaming in `TrackingMode.READY`,
• *When* `VerticalSpeedAndSlopeDevice.calculateMetrics()` executes,
• *Then* `mVerticalSpeedSensor` updates with live VAM.
• *Given* sensors emitting in `TrackingMode.READY`,
• *When* `AverageFilter` or `MaxValueFilter` receives samples,
• *Then* the samples are ignored until active tracking begins.
• *Given* rolling filters populated with pre-start samples,
• *When* the athlete taps "Start Tracking" (dispatching `RESET_ACCUMULATORS_INTENT`),
• *Then* all filters execute `reset()`, starting recorded workout statistics cleanly.

**Invariants**: Thread safety across filter methods, null-safety (`REQ-FIL-011`), VAM stability gating (`REQ-FIL-012`), and 100% clean-room test pass rate MUST NOT be broken.

### REQ-TRK-018: Sport-Specific Auto-Pause Engine with Anti-Chatter Hysteresis, Mutex Precedence, Sensor Dropout Guard, Synchronous Ingestion Gating, and Sport-Decoupled Thresholds [Verified]
- **Tests**: `AutoPauseManager.kt`, `TrainingApplication.java`, `TrackerService.java`, `TuningPreferencesDataStore.kt`, `SensorsGpsFilterSection.kt`, `SensorGridScreen.kt`
- **Implementation**: Sport-specific auto-pause engine with sport-decoupled integer thresholds and debounce windows, anti-chatter hysteresis, mutex precedence, and synchronous ingestion gating.
- **Rationale**: The system SHALL provide an automated, sport-specific Auto-Pause Engine (`AutoPauseManager.kt`) that suspends active recording when an athlete becomes stationary and seamlessly resumes recording upon departure, maintaining strict metric integrity, crash resilience, and manual user sovereignty (ATT-45, ATT-3677, extending `REQ-TRK-001` and `REQ-TRK-002`, complementing `REQ-FIL-012`, `REQ-FIL-013`, and `REQ-UI-262`):
1. *Sport-Specific Default Activation*:
• The system SHALL enable Auto-Pause by default for Cycling (`BSportType.BIKE`).
• The system SHALL disable Auto-Pause by default for Running (`BSportType.RUN`) and Other sports (`BSportType.UNKNOWN`), ensuring interval workouts and track drills are never inadvertently paused.
• Sports-specific activation SHALL be independently configurable via `TuningPreferencesDataStore`.
2. *Sport-Decoupled Thresholds, Integer Configuration, Anti-Chatter Hysteresis Deadband & Hard Ceilings (`ATT-3677`)*:
• The system SHALL provide independent Speed Thresholds and Debounce Windows per sport (`Bike`, `Run`, `Other`), configured strictly as integer (`Int`) values in `TuningPreferencesDataStore`:
- **Cycling (`BSportType.BIKE`)**: Speed threshold $1 \dots 5$ in user speed unit ($1 \dots 5\text{ km/h}$ Metric, $1 \dots 5\text{ mph}$ Imperial), step $1$, default $3\text{ km/h}$ / $2\text{ mph}$; Debounce window $1 \dots 10\text{ s}$, step $1\text{ s}$, default $2\text{ s}$.
- **Running (`BSportType.RUN`)**: Speed threshold $1 \dots 5$ in user speed unit ($1 \dots 5\text{ km/h}$ Metric, $1 \dots 5\text{ mph}$ Imperial), step $1$, default $2\text{ km/h}$ / $1\text{ mph}$; Debounce window $1 \dots 20\text{ s}$, step $1\text{ s}$, default $5\text{ s}$.
- **Other (`BSportType.UNKNOWN`)**: Speed threshold $1 \dots 5$ in user speed unit ($1 \dots 5\text{ km/h}$ Metric, $1 \dots 5\text{ mph}$ Imperial), step $1$, default $1\text{ km/h}$ / $1\text{ mph}$; Debounce window $1 \dots 60\text{ s}$, step $1\text{ s}$, default $10\text{ s}$.
• Sliders in `SensorsGpsFilterSection.kt` SHALL be rendered conditionally directly below each sport's auto-pause toggle (collapsible when enabled) and formatted using dynamic speed units ($v\text{ km/h}$ or $v\text{ mph}$) via `R.string.units_speed` / `MyHelper.getUnitsId(SensorType.SPEED_mps)`.
• **Hysteresis Deadband & Ceiling**: Resumption strictly requires $v_{\text{resume}} \ge \min(v_{\text{pause}} + 0.5\text{ [user unit]}, v_{\text{ceiling}})$ sustained for a confirmation tick ($1\text{ second}$). The upper ceiling is clamped to $6.0\text{ km/h}$ in Metric ($1.667\text{ m/s}$) or $4.0\text{ mph}$ in Imperial, ensuring a deterministic and reachable threshold even at maximum threshold, while suppressing chattering near the pause threshold.
• **Numerical Sanitization**: Any non-finite speed values (`Double.NaN`, `Double.POSITIVE_INFINITY`, `Double.NEGATIVE_INFINITY`) SHALL be sanitized and rejected prior to evaluation.
3. *Speed Hierarchy, 5-Second Stale Packet TTL, SENSOR_LOST State & Audit Trail*:
• Speed Hierarchy: The engine consumes `BANALService.getBestSensorData(SensorType.SPEED_mps)`. High-precision hardware wheel sensors (ANT+/BLE) take primary precedence. If no hardware sensor is connected, the engine seamlessly falls back to GPS speed (`GPS_SPEED` / Location provider).
• Stale Packet Rejection: The engine rejects sensor packets where `System.currentTimeMillis() - packet.timestamp > 5000ms`. If the newest speed packet is older than 5 seconds, the engine treats the feed as stale/disconnected and transitions to `SENSOR_LOST` rather than falsely assuming stationary 0 km/h.
• Stationary vs Signal Loss: Periodic location/sensor updates arriving with low speed ($v < v_{\text{pause}}$) and valid accuracy ($\le 30\text{ m}$) evaluate as stationary and trigger auto-pause. Total signal outage ($> 5\text{ s}$) transitions to `SENSOR_LOST`, suspends auto-pause, and maintains recording state.
• State Change Audit Logging: When transitioning between `TRACKING`, `AUTO_PAUSED`, and `SENSOR_LOST`, the system SHALL persist a state-transition event record in SQLite session metadata for full auditability, while keeping the continuous `WorkoutSamples` trackpoint stream free of synthetic coordinate/speed spikes to preserve clean FIT/GPX exports.
• Recovery Contract: When valid speed readings resume and remain stable for $\ge 2$ consecutive seconds, `AutoPauseManager` transitions from `SENSOR_LOST` back to `TRACKING` and re-engages regular stationary speed evaluation.
4. *Synchronous Hierarchical Gating & Elevation Drift Protection*:
• In `VerticalSpeedAndSlopeDevice`, `MyAccumulatorSensor<Double>` (for `SensorType.DISTANCE`), and `LocationManager` callbacks, gating executes sequentially in the sensor ingestion thread: first `if (TrainingApplication.isPaused()) return;` (enforcing manual pause priority), then `if (AutoPauseManager.isAutoPaused()) return;` (enforcing auto-pause gating).
• Gating is 100% synchronous in memory, preventing any elevation drift or ghost trackpoints before the database persistence completes.
• Horizontal distance accumulation and vertical elevation gain/loss (`mAscentSensor`, `mDescentSensor`) immediately halt. `ElevationProfile` path-point logging and `WorkoutSamples` database writes are suspended during `AUTO_PAUSED`.
• Authoritative average speed in `WorkoutSummaries` strictly evaluates $v_{\text{avg}} = \frac{\text{Distance}}{\text{TimeActive}}$ (moving time), completely uncorrupted by stationary pause duration.
5. *Strict Mutex Enforcement (TrackingLock), Persistence & Race-Free Reset*:
• An explicit manual pause sets `mManualPauseLock = true` in `TrainingApplication`. While `mManualPauseLock` is active, `AutoPauseManager` is strictly blocked from issuing auto-resume events.
• Lock Persistence: `mManualPauseLock` is immediately persisted to `DataStore`, surviving process death or background OS killing.
• Centralized Lock Reset: `mManualPauseLock` is cleared (`mManualPauseLock = false`) and persisted whenever ANY explicit user resume action is dispatched (in-app Cockpit button, "Resume Now" badge button, or background notification action). All actions funnel through `TrainingApplication.resumeFromPaused()`.
6. *Strictly Ordered FIFO Asynchronous Flush & Crash Invariants*:
• Single-Threaded FIFO Queue: `mDbExecutor` in `TrackerService` enqueues the final stationary `WorkoutSample` and accumulator snapshots immediately followed by the summary paused-state update, guaranteeing strict in-order persistence.
• Crash Recovery during Debounce: The `debounceCounter` is transient in-memory. If process death occurs while in `PAUSE_DEBOUNCE_PENDING`, the counter is reset to 0 and the restored session strictly defaults to `TRACKING` (active recording).
7. *Deterministic Startup State-Recovery Hierarchy*:
• Upon application restart or crash recovery in `TrackerService`:
- Tier 1 (User Sovereignty): If `mManualPauseLock == true` in DataStore, the session MUST restore in `MANUAL_PAUSED` state regardless of the last `WorkoutSample` velocity. The user's explicit manual intent strictly overrides telemetry samples.
- Tier 2 (Auto-Paused): If `mManualPauseLock == false` and `isAutoPaused == true`, the session restores in `AUTO_PAUSED`. Sensor evaluation is re-engaged. If immediate fresh speed readings arrive exceeding $v_{\text{resume}}$, the engine smoothly transitions to `TRACKING`.
- Tier 3 (Active Tracking): If both flags are false, the session restores to `TRACKING`.
• In-memory sensor accumulators are re-initialized with the last persisted totals from `WorkoutSummaries` to avoid value jumping/spikes upon resume.
8. *Cockpit UI & Audio/Haptic Feedback*:
• When in `AUTO_PAUSED` state, `SensorGridScreen.kt` renders an amber pill badge displaying "Auto-Pause" along with an immediate "Resume Now" button.
• When in `SENSOR_LOST` state, `SensorGridScreen.kt` renders an amber pill badge displaying "Speed Sensor Lost".
• Tapping "Resume Now" clears `mManualPauseLock`, sets `isAutoPaused = false`, and immediately forces the session back to `TRACKING`. Audio tones are emitted on pause/resume.
9. *Localization (9 Languages)*:
• Full copy parity across EN, DE, ES, FR, IT, JA, NL, PL, PT for all settings sliders, toggles, badge text, and manual override controls.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Extends and refines `REQ-TRK-001` (*Support Start, Stop, Pause with Service persistence*) and `REQ-TRK-002` (*Workout Lap Recording Lifecycle, Manual Splitting & Pause/Resume Neutrality*). Targets `AutoPauseManager.kt`, `TrainingApplication.java`, `TrackerService.java`, `TuningPreferencesDataStore.kt`, `SensorsGpsFilterSection.kt`, and `SensorGridScreen.kt`.
2. *Historical Origin & Commit Trace*: Commit `3c09e3e7` (Initial baseline import, 2011), `ATT-166` Epic (*Filtering to improve sensor values*), `ATT-45` (Sprint 2026-42.4, commit `53c20db4`), and `ATT-3677` (Sprint 2026-42.5). `TrainingApplication.pauseTracking()` and `resumeFromPaused()` historically supported manual user pause only; ATT-45 added auto-pause with global thresholds; ATT-3677 decouples thresholds and debounces per sport with integer-only sliders.
3. *Root Reason for Existing Formulation*: Manual pause was designed as an absolute binary state to suspend recording active time, trackpoints, and lap splitting per `REQ-TRK-002` when an athlete took a break. When introducing automated speed-drop pausing, naive auto-resume would violate user intent if an athlete deliberately paused at a cafe and walked with their bike. Furthermore, different sports (cycling vs running vs other) operate at vastly different low-speed dynamics, necessitating independent thresholds and debounce windows per sport without fractional float jitter. Therefore, Chesterton's Fence mandates that manual pause semantics must remain sovereign via `mManualPauseLock`, and per-sport thresholds must maintain integer cleanliness while preserving lap neutrality and continuous `TIME_TOTAL_s` accumulation.
4. *Preservation of Core Invariants*: Manual user pause sovereignty via persisted `mManualPauseLock`, zero lap increments or split disruptions on auto-pause/resume (`REQ-TRK-002`), continuous `TIME_TOTAL_s` elapsed tracking during pause (`REQ-TRK-009`), strict moving-time average speed calculation ($v_{\text{avg}} = \frac{\text{Distance}}{\text{TimeActive}}$), backward compatibility bridges for legacy global properties, and 100% full clean-room unit test pass rate strictly preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* active cycling tracking with default settings (threshold 3 km/h, debounce 2s),
• *When* speed drops below 3 km/h for 2 seconds,
• *Then* `AutoPauseManager` transitions to `AUTO_PAUSED`, halts distance and elevation accumulation, flushes final sample asynchronously, and renders amber badge in Cockpit.
• *Given* active running tracking with default settings (threshold 2 km/h, debounce 5s),
• *When* speed drops below 2 km/h for 4 seconds,
• *Then* engine remains in `PAUSE_DEBOUNCE_PENDING`; when 5th second elapses at $\le 2\text{ km/h}$, transitions to `AUTO_PAUSED`.
• *Given* a session in `AUTO_PAUSED`,
• *When* speed fluctuates below $v_{\text{pause}} + 0.5$,
• *Then* session remains paused due to 0.5 hysteresis deadband.
• *Given* a session in `AUTO_PAUSED`,
• *When* speed exceeds $v_{\text{resume}}$ for 1s,
• *Then* recording resumes automatically.
• *Given* Imperial units active,
• *When* configuring auto-pause sliders,
• *Then* speed thresholds step in whole mph integers (1..5 mph) with defaults Bike=2, Run=1, Other=1.
• *Given* a user manual pause,
• *When* athlete speeds up,
• *Then* auto-resume is strictly blocked by `mManualPauseLock`.
• *Given* an athlete in `AUTO_PAUSED` tapping "Resume Now",
• *Then* `mManualPauseLock` clears and session immediately resumes active tracking.
• *Given* total speed signal dropout exceeding 5 seconds,
• *Then* engine enters `SENSOR_LOST`, logs transition in session metadata, suspends auto-pause, and trackpoint logging remains clean.

**Invariants**: Moving average speed calculation, manual pause precedence, zero elevation drift during pause, and 100% clean-room test pass rate MUST NOT be broken.

### REQ-TRK-019: Real-Time Power-to-Weight (W/kg) Cockpit Telemetry & Athlete Weight Baseline Architecture [Verified]
- **Tests**: `SettingsDataStore.kt`, `SensorType.java`, `CyclingPowerMetricsDevice.java`, `ZonesSettingsActivity.kt`, `SensorGridScreen.kt`
- **Implementation**: Real-time power-to-weight (W/kg) cockpit metric, athlete weight baseline persistence, unit conversion, and quick fallback dialog.
- **Rationale**: The system SHALL provide a real-time power-to-weight ratio (W/kg) computed metric for cycling workouts, maintaining persistent athlete weight baselines, unit-aware conversions, seamless cockpit grid integration, and zero-disruption unconfigured prompt workflows (ATT-1289, complementing `REQ-UI-370`, `REQ-UI-373`, and `REQ-UI-374`):
1. *Persistent Athlete Weight Preference (`SettingsDataStore.kt`)*:
• The system SHALL maintain a persistent float preference `KEY_ATHLETE_WEIGHT_KG = floatPreferencesKey("athlete_weight_kg")` (default `0.0f`).
• The system SHALL expose a reactive flow `val athleteWeightKgFlow: Flow<Float>` and thread-safe `@JvmStatic` accessors in `SettingsDataStoreJavaHelper.kt`: `getAthleteWeightKg(context)` and `setAthleteWeightKg(context, weightKg)`.
• Validation & Clamping: Stored athlete weight SHALL validate that $m_{\text{athlete}} \in [30.0, 300.0]\text{ kg}$ (or $0.0\text{f}$ representing unconfigured). Values outside this interval SHALL be clamped to boundaries.
• Unit Parity: When Imperial units are active (`TrainingApplication.getUnit() == MyUnits.IMPERIAL`), the UI SHALL display and accept body weight in pounds (`lbs`) with conversion factor $1\text{ kg} = 2.20462262185\text{ lbs}$. Stored DataStore representation strictly remains canonical kilograms.
2. *First-Class Sensor & Metric Definition (`SensorType.POWER_TO_WEIGHT`)*:
• The system SHALL register `POWER_TO_WEIGHT` in `SensorType.java` with `fullNameId = R.string.power_to_weight`, `shortNameId = R.string.power_to_weight_short`, `unitId = R.string.units_power_to_weight`, `SensorValueType.DOUBLE`, and `PowerToWeightFormatter`.
• Formatter Contract (`PowerToWeightFormatter.java`): When the input value is null, non-finite, or $\le 0.0$, the formatter SHALL return `"--"`. Otherwise, it SHALL format the value with exactly 1 decimal place (`#0.0`, e.g. `4.0` or `3.8`) using the active locale decimal separator.
• Filter Defaults: In `SensorFilterDefaults.kt`, `SensorType.POWER_TO_WEIGHT` SHALL default to a 3-second moving average (`FilterType.MOVING_AVERAGE_TIME`, 3.0s), matching raw power smoothing, while supporting 10s, 30s, session average (`FilterType.AVERAGE`), and instantaneous (`FilterType.INSTANTANEOUS`).
3. *Decoupled 1Hz Computed Metrics Engine (`CyclingPowerMetricsDevice.java`)*:
• The system SHALL provide a dedicated computed device `CyclingPowerMetricsDevice` extending `MyDevice` with `DeviceType.CYCLING_POWER_METRICS`.
• The device SHALL observe 1-second filtered cycling power (`SensorType.POWER`).
• On a periodic 1-second scheduled tick, the device SHALL check synchronous pause gating (`TrainingApplication.isRecordingSuspended()`), retrieve power and athlete weight, and publish $w\_per\_kg = \frac{P}{m_{\text{athlete}}}$ via `mPowerToWeightSensor.newValue(w_per_kg)` when $m_{\text{athlete}} > 0.0$ and $P \ge 0.0$, or `null` when unconfigured.
4. *Physiological Baselines UI in Zone Settings (`ZonesSettingsActivity.kt`)*:
• In "My Stuff" -> "Training Zones" (`ZoneSettingsScreen.kt`), the system SHALL render an "Athlete Baselines / Physiologische Eckdaten" section alongside cycling FTP.
• The section SHALL display current body weight formatted in the active unit (e.g. `75.0 kg` or `165.3 lbs`).
• Tapping the row SHALL open a modal dialog allowing the athlete to update their body weight with instantaneous validation.
5. *Cockpit Integration & Fallback Dialog (`SensorGridScreen.kt` & `EditSensorFieldViewModel.kt`)*:
• When athlete weight is unconfigured ($m_{\text{athlete}} \le 0.0$), the cockpit W/kg tile SHALL render `"-- W/kg"`.
• Tapping an unconfigured W/kg tile during active tracking, or adding `POWER_TO_WEIGHT` in `EditSensorFieldViewModel` when unconfigured, SHALL launch `AthleteWeightDialog`.
• Upon saving valid weight, `SettingsDataStore` SHALL be updated and live W/kg telemetry SHALL immediately commence without requiring service reconnection.
6. *9-Language Localization Parity*:
• All string resources SHALL be defined across all 9 application locales: EN, DE, ES, FR, IT, JA, NL, PL, PT.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement `REQ-TRK-019`. Complements `REQ-TRK-001` (*Sensor Ingestion Pipeline*), `REQ-UI-370` (*7-Zone Coggan Cycling Power Model*), `REQ-UI-373` (*Automated Training Zone Calculation*), and `REQ-UI-374` (*Contextual Zone Settings Navigation*). Targets `SettingsDataStore.kt`, `SensorType.java`, `CyclingPowerMetricsDevice.java`, `SensorGridScreen.kt`, `ZonesSettingsActivity.kt`, and `strings.xml`.
2. *Historical Origin & Commit Trace*: Cycling power support originally introduced in `BANALService` (`ANTBikePowerDevice`, `BTLEBikePowerDevice`). Modernized to Compose 7-zone Coggan model in Sprint `2026-42.4` (`ATT-3184`, commit `6ae4625b`). Active ticket `ATT-1289`.
3. *Root Reason for Existing Formulation*: Previously, power telemetry was treated solely as raw sensor input without secondary mass-normalized calculations. Adding W/kg requires persistent body mass baselines.
4. *Preservation of Core Invariants*: DataStore preference integrity preserved, SQLite `WorkoutSamples` column creation preserved (`makeColumns(SensorType.values())`), and 100% clean-room test pass rate strictly preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an athlete entering their body weight (e.g. 75.0 kg or 165 lbs),
• *When* saving in Zone Settings or via the fallback dialog,
• *Then* the value is stored in `SettingsDataStore` in canonical kilograms and emitted across `athleteWeightKgFlow`.
• *Given* an athlete weighing 75 kg producing 300 Watts smoothed power,
• *When* observing the cockpit W/kg tile,
• *Then* it displays "4.0 W/kg" in real time.
• *Given* an athlete with unconfigured weight (0.0 kg),
• *When* viewing the W/kg tile,
• *Then* it displays "-- W/kg", and tapping the tile opens `AthleteWeightDialog`, after which live W/kg computation commences immediately upon saving.
• *Given* Imperial units selected in application settings,
• *When* entering and viewing weight,
• *Then* values are displayed in lbs with accurate conversion.

**Invariants**: DataStore preference integrity, SQLite column schema, unit conversion precision, and 100% clean-room test pass rate MUST NOT be broken.

### REQ-TRK-020: Live Normalized Power (NP) and Intensity Factor (IF) Calculations & Cockpit Integration [Verified]
- **Tests**: `CyclingPowerMetricsDevice.java`, `SensorType.java`, `IntensityFactorFormatter.java`, `EditSensorFieldDialog.kt`
- **Implementation**: Real-time Normalized Power (NP) and Intensity Factor (IF) calculations, 30s rolling buffer, 4th power weighting, pause gating, and cockpit integration.
- **Rationale**: The system SHALL calculate Dr. Andrew Coggan's Normalized Power (NP) and Intensity Factor (IF) in real time during cycling workouts, integrating these metrics into the telemetry engine, filter presets, and tracking cockpit (ATT-1290, complementing `REQ-TRK-019`, `REQ-UI-370`, `REQ-UI-373`, and `REQ-UI-374`):
1. *First-Class Sensor & Metric Definitions*:
• `SensorType.NORMALIZED_POWER`: fullNameId `R.string.normalized_power`, shortNameId `R.string.normalized_power_short` ("NP"), unitId `R.string.units_power` ("W"), `SensorValueType.DOUBLE`, `IntegerFormatter`.
• `SensorType.INTENSITY_FACTOR`: fullNameId `R.string.intensity_factor`, shortNameId `R.string.intensity_factor_short` ("IF"), unitId `R.string.units_intensity_factor` (""), `SensorValueType.DOUBLE`, `IntensityFactorFormatter` (2 decimal places `#0.00`).
• Icon mapping: `case POWER, POWER_TO_WEIGHT, NORMALIZED_POWER, INTENSITY_FACTOR -> R.drawable.ic_power;`.
2. *Decoupled 1Hz Computed Engine Architecture (`CyclingPowerMetricsDevice.java`)*:
• The system SHALL maintain a 30-sample rolling FIFO window of 1-second power samples (t) \ge 0.0\text{ W}$.
• Active Pause Gating: Calculation and window updates SHALL be strictly gated by `TrainingApplication.isRecordingSuspended()`. When paused or auto-paused, the state of the 30-sample queue and cumulative sums MUST NOT advance.
• Coasting Policy: 0 W power samples are genuine physiological recovery points and MUST be ingested into the 30-sample rolling window.
• Warm-Up Window ( < 30\text{ s}$): While the queue contains fewer than 30 samples, Normalized Power and Intensity Factor are undefined; `newValue(null)` SHALL be published and formatted as `"--"`.
• Calculation ( \ge 30\text{ s}$):
a. Compute rolling 30s average power: {30s} = \frac{1}{30}\sum_{i=0}^{29} P(t-i)$.
b. Raise to 4th power: (t) = (P_{30s})^4$.
c. Accumulate cumulative 4th power sum  = \sum y(t)$ and valid tick counter $.
d. Take 4th root of cumulative 4th power average: $\text{NP} = \sqrt[4]{\frac{S_4}{N}}$.
e. Publish $\text{NP}$ via `mNormalizedPowerSensor.newValue(np)`.
• Intensity Factor:
a. Retrieve athlete FTP via `mFtpProvider.getFtp(context)`.
b. If $\text{FTP} > 0$ and $\text{NP} \ge 0$, compute $\text{IF} = \frac{\text{NP}}{\text{FTP}}$ and publish via `mIntensityFactorSensor.newValue(ifVal)`.
c. If FTP is unconfigured ($\le 0$ or null), publish `null` to `mIntensityFactorSensor.newValue(null)`.
3. *Thread Safety & Lifecycle Invariants*:
• All calculation, queue mutation, and sensor emission logic SHALL be synchronized within `CyclingPowerMetricsDevice` monitors.
• Scheduled executor service SHALL be explicitly terminated via `shutdownNow()` during service destruction.
4. *Sensor Filter Defaults*:
• `NORMALIZED_POWER` and `INTENSITY_FACTOR` SHALL default to `FilterType.INSTANTANEOUS` (1.0s direct).
5. *Activity Types*:
• `SensorType.NORMALIZED_POWER` and `SensorType.INTENSITY_FACTOR` SHALL be registered in `ActivityType.BIKE_POWER`.
6. *Cockpit Grid & Guidance UI*:
• Cockpit tiles for NP and IF SHALL render `"--"` when undefined or unconfigured.
• In `EditSensorFieldDialog.kt`, selecting `INTENSITY_FACTOR` when FTP is unconfigured SHALL display a guidance prompt with direct access to Zone Settings.
7. *9-Language Localization Parity*:
• All string resources SHALL be defined across all 9 application locales: EN, DE, ES, FR, IT, JA, NL, PL, PT.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement `REQ-TRK-020`. Complements `REQ-TRK-019` (*Power-to-weight ratio*), `REQ-UI-370` (*7-Zone Coggan Cycling Power Model*), `REQ-UI-373` (*Automated Training Zone Calculation*), and `REQ-UI-374` (*Contextual Zone Settings Navigation*). Targets `CyclingPowerMetricsDevice.java`, `SensorType.java`, `IntensityFactorFormatter.java`, `EditSensorFieldDialog.kt`, and `strings.xml`.
2. *Historical Origin & Commit Trace*: Cycling power support introduced originally in `BANALService`. Modernized to Compose 7-zone Coggan model in Sprint `2026-42.4` (`ATT-3184`, commit `6ae4625b`), automatic zone calculation from FTP (`ATT-3188`), contextual pairing navigation (`ATT-3189`), and real-time power-to-weight ratio (`ATT-1289`, commit `5eee662f`). Active ticket `ATT-1290`.
3. *Root Reason for Existing Formulation*: Previous power telemetry handled raw and smoothed instantaneous metrics. NP requires cumulative session state across 30s rolling buffers and 4th power summation.
4. *Preservation of Core Invariants*: Pause gating invariant strictly preserved, FTP preference integrity preserved, SQLite schema integrity preserved, and 100% test pass rate preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an active cycling workout with power data,
• *When* fewer than 30 seconds have elapsed,
• *Then* NP and IF emit null, displaying "-- W" and "--".
• *Given* constant 200 Watts for 60 seconds,
• *When* 30 seconds elapse,
• *Then* NP displays exactly 200 W.
• *Given* alternating intervals of 100 W and 300 W (mean 200 W),
• *When* evaluated,
• *Then* NP strictly exceeds 200 W.
• *Given* FTP of 300 W and NP of 240 W,
• *When* observing IF,
• *Then* it displays "0.80".
• *Given* unconfigured FTP,
• *When* observing IF,
• *Then* it displays "--", and selecting the tile in edit mode prompts the user to configure FTP.

**Invariants**: Pause gating, FTP DataStore integrity, SQLite column schema, and 100% clean-room test pass rate MUST NOT be broken.

### REQ-TRK-021: Real-Time Mechanical Work (kJ) and Training Stress Score (TSS) Telemetry [Verified]
- **Tests**: `CyclingPowerMetricsDevice.java`, `SensorType.java`, `EditSensorFieldDialog.kt`
- **Implementation**: Real-time Mechanical Work (kJ) and Training Stress Score (TSS) telemetry.
- **Rationale**: The system SHALL integrate Mechanical Work (kJ) and Dr. Andrew Coggan's Training Stress Score (TSS) in real time during cycling workouts, integrating these cumulative load metrics into the telemetry engine, filter presets, and tracking cockpit (ATT-1291, complementing `REQ-TRK-019`, `REQ-TRK-020`, `REQ-UI-370`, `REQ-UI-373`, and `REQ-UI-374`):
1. *First-Class Sensor & Metric Definitions*:
• `SensorType.WORK_KJ`: fullNameId `R.string.work_kj`, shortNameId `R.string.work_kj_short` ("kJ"), unitId `R.string.units_work_kj` ("kJ"), `SensorValueType.DOUBLE`, `IntegerFormatter`, `isInstantaneous = false`.
• `SensorType.TSS`: fullNameId `R.string.tss`, shortNameId `R.string.tss_short` ("TSS"), unitId `R.string.units_none`, `SensorValueType.DOUBLE`, `IntegerFormatter`, `isInstantaneous = false`.
• Icon mapping: `case POWER, POWER_TO_WEIGHT, NORMALIZED_POWER, INTENSITY_FACTOR, WORK_KJ, TSS -> R.drawable.ic_power;`.
2. *Decoupled 1Hz Computed Engine Architecture (`CyclingPowerMetricsDevice.java`)*:
• Mechanical Work Integration: For every active 1-second tick when recording is not suspended and power $P(t) \ge 0.0\text{ W}$, the system SHALL integrate mechanical energy: $W_{\text{joules}} \leftarrow W_{\text{joules}} + (P(t) \times 1.0\text{ s})$, and publish $\text{Work (kJ)} = \frac{W_{\text{joules}}}{1000.0}$ via `mWorkKjSensor.newValue(workKj)`. Coasting ($0\text{ W}$) adds $0\text{ J}$.
• Active Duration Tracking: Active power duration $t$ (seconds) advances by 1 second on each valid non-suspended tick.
• Warm-Up Window ($t < 30\text{ s}$): While $t < 30\text{ s}$, Normalized Power and Intensity Factor are undefined (`null`); TSS publishes `null` (formatted as `"--"`).
• TSS Calculation ($t \ge 30\text{ s}$): If athlete Functional Threshold Power is configured ($\text{FTP} > 0$) and $\text{NP} \ge 0.0$ and $\text{IF} \ge 0.0$, the system SHALL compute live TSS: $\text{TSS} = \frac{t \times \text{NP} \times \text{IF}}{\text{FTP} \times 3600.0} \times 100.0$, and publish via `mTssSensor.newValue(tss)`. If FTP is unconfigured ($\le 0$ or null), TSS publishes `null`.
• Active Pause Gating: When paused or auto-paused (`isRecordingSuspended() == true`), $W_{\text{joules}}$, $t$, and TSS MUST NOT advance.
• Session Reset: `resetSession()` SHALL reset $W_{\text{joules}} = 0.0$, $t = 0\text{ s}$, and publish `null` to both sensors.
3. *Thread Safety & Lifecycle Invariants*:
• All calculation, queue mutation, and sensor emission logic SHALL be synchronized within `CyclingPowerMetricsDevice` monitors.
4. *Sensor Filter Defaults*:
• `WORK_KJ` and `TSS` SHALL default to `FilterType.INSTANTANEOUS` (1.0s direct).
5. *Activity Types*:
• `SensorType.WORK_KJ` and `SensorType.TSS` SHALL be registered in `ActivityType.BIKE_POWER`.
6. *Cockpit Grid & Guidance UI*:
• Cockpit tiles for WORK_KJ and TSS SHALL render `"--"` when undefined or unconfigured.
• In `EditSensorFieldDialog.kt`, selecting `TSS` when FTP is unconfigured SHALL display the guidance prompt with direct access to Zone Settings.
7. *9-Language Localization Parity*:
• All string resources (`work_kj`, `work_kj_short`, `units_work_kj`, `tss`, `tss_short`) SHALL be defined across all 9 application locales: EN, DE, ES, FR, IT, JA, NL, PL, PT.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement `REQ-TRK-021`. Complements `REQ-TRK-019` (*Power-to-weight ratio*) and `REQ-TRK-020` (*Normalized Power & Intensity Factor*). Targets `CyclingPowerMetricsDevice.java`, `SensorType.java`, `ActivityType.java`, `SensorFilterDefaults.kt`, `EditSensorFieldDialog.kt`, and `strings.xml`.
2. *Historical Origin & Commit Trace*: Cycling power support introduced originally in `BANALService`. `CyclingPowerMetricsDevice` created in Sprint `2026-42.4` (`ATT-1289`, commit `5eee662f`) and extended for NP/IF (`ATT-1290`, commit `4fe4996c`). Active ticket `ATT-1291`.
3. *Root Reason for Existing Formulation*: Athletes require external mechanical work (kJ) for fueling/caloric tracking and live TSS for endurance pacing.
4. *Preservation of Core Invariants*: Pause gating invariant strictly preserved, FTP preference integrity preserved, SQLite schema integrity preserved, and 100% test pass rate preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an active cycling workout with power data,
• *When* riding at constant 200 Watts for 10 seconds,
• *Then* Work (kJ) accumulates to 2.0 kJ.
• *Given* constant 250 Watts for 3600 seconds (1 hour) with FTP = 250 W,
• *When* evaluating TSS at 1 hour,
• *Then* TSS displays exactly 100.
• *Given* an athlete in the first 29 seconds of a ride ($t < 30\text{ s}$),
• *When* observing TSS,
• *Then* TSS displays "--", while Work (kJ) accumulates normally from second 1.
• *Given* an athlete with unconfigured FTP,
• *When* observing TSS,
• *Then* TSS displays "--", and selecting the tile in edit mode prompts the user to configure FTP.
• *Given* active recording suspended (paused),
• *When* 1Hz ticks occur,
• *Then* neither Work (kJ) nor TSS advance.

**Invariants**: Pause gating, FTP DataStore integrity, SQLite column schema, and 100% clean-room test pass rate MUST NOT be broken.

### REQ-TRK-022: Streamlined Dual-Type (Location and Health) Foreground Service Architecture & Removal of Redundant Connected Device Permission [Verified]

**Invariants**: Zero SecurityExceptions on service start; uninterrupted workout tracking; 100% clean-room test pass rate.]
- **Tests**: health"` without `connectedDevice`.
• *Given* `TrackerService.determineForegroundServiceType()` evaluated on Android 14+ (API 34+),
• *When* health runtime permission is NOT granted,
• *Then* the bitmask equals strictly `ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION`.
• *Given* `TrackerService.determineForegroundServiceType()` evaluated on Android 14+ (API 34+),
• *When* health runtime permission IS granted,
• *Then* the bitmask equals `ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
- **Implementation**: health"`.
• The string `connectedDevice` SHALL be completely purged from all service definitions.
3. *Runtime FGS Bitmask Simplification (`REQ-TRK-022.3`)*:
• In `TrackerService.java`, `determineForegroundServiceType()` SHALL start with `ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION`.
• On Android 14+ (`Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE`), if `hasHealthPermission()` returns `true`, the bitmask SHALL add `ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH`.
• `ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE` SHALL NEVER be included in the returned bitmask.
• The unused `hasBluetoothPermission()` helper in `TrackerService.java` SHALL be removed.
4. *Preservation of System Invariants (`REQ-TRK-022.4`)*:
• Uninterrupted workout session recording on Android 14+ (API 34+).
• Zero `SecurityException` during `Service.startForeground()` promotion.
• Active Bluetooth LE GATT sensor telemetry collection and background CPU wake lock upkeep.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Amends `REQ-CON-019` (*Robust Bluetooth LE Sensor Discovery & Direct LE Transport Connection Architecture*, ATT-2773) and `REQ-TRK-015` (*Permission-Gated Foreground Service Types in TrackerService*, ATT-2972). Targets `app/src/main/AndroidManifest.xml`, `TrackerService.java`, and unit tests `TrackerServiceFGSTypeTest.kt` and `TrackerServiceFinalizationTest.kt`. Formally tracked as `REQ-TRK-022`.
2. *Historical Origin & Commit Trace*: Sprint 2026-41.4 (`ATT-2773`, commit `67451a9`): added `FOREGROUND_SERVICE_CONNECTED_DEVICE` and `connectedDevice` attribute. Sprint 2026-41.7 (`ATT-2972`, commit `4466c40`): gated FGS types in `TrackerService.determineForegroundServiceType()`.
3. *Root Reason for Existing Formulation*: The initial Android 14 compliance migration defensively adopted all available peripheral types. Subsequent operational testing and Google Play Console policy enforcement confirm that `FOREGROUND_SERVICE_LOCATION` and `FOREGROUND_SERVICE_HEALTH` completely satisfy Android 14 background execution requirements for athletic workouts while eliminating unnecessary Google Play Console policy questionnaires regarding external hardware file transfers.
4. *Preservation of Core Invariants*: Process foreground priority, GPS track recording, health sensor data ingestion, and 100% clean-room test pass rate remain strictly preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* the application manifest (`AndroidManifest.xml`) and merged manifest output,
• *When* inspecting declared permissions,
• *Then* `FOREGROUND_SERVICE_CONNECTED_DEVICE` is completely absent.
• *Given* the `<service>` declarations for `TrackerService` and `BANALService`,
• *When* inspecting `android:foregroundServiceType`,
• *Then* both specify `"location
- **Rationale**: The system SHALL streamline its Foreground Service architecture by eliminating `android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE` from the application manifest, removing the `connectedDevice` service attribute from `TrackerService` and `BANALService`, and simplifying `TrackerService.determineForegroundServiceType()` to derive strictly `FOREGROUND_SERVICE_TYPE_LOCATION` and permission-gated `FOREGROUND_SERVICE_TYPE_HEALTH` (ATT-3567, amending `REQ-CON-019` and `REQ-TRK-015`):
1. *Manifest Permission Cleanup (`REQ-TRK-022.1`)*:
• The application manifest (`AndroidManifest.xml`) SHALL NOT declare `<uses-permission android:name="android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE" />`.
• `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_LOCATION`, and `FOREGROUND_SERVICE_HEALTH` SHALL remain fully declared and intact.
2. *Service Declaration Attribute Cleanup (`REQ-TRK-022.2`)*:
• In `AndroidManifest.xml`, the `<service>` declarations for `TrackerService` and `BANALService` SHALL specify `android:foregroundServiceType="location

### REQ-TRK-023: Growing 1..29s Warm-Up Rolling Window for Live Normalized Power, Intensity Factor, and TSS [Verified]
- **Tests**: `CyclingPowerMetricsDevice.java`, `NormalizedPowerMathTest.kt`, `MechanicalWorkAndTssMathTest.kt`, `CyclingPowerMetricsDeviceTest.kt`
- **Implementation**: Growing 1..29s warm-up rolling window for live Normalized Power, Intensity Factor, and TSS (Rule 40).
- **Rationale**: The system SHALL compute live Normalized Power (`NORMALIZED_POWER`), Intensity Factor (`INTENSITY_FACTOR`), and Training Stress Score (`TSS`) over the growing rolling window ($1 \le k < 30$) during the initial 29-second warm-up phase and after long-pause window flushes (`> 30s`), adhering to Rule 40 (`docs/coding_standards.md` Section 1.6, ATT-3778, amending `REQ-TRK-020` and `REQ-TRK-021`):
1. *Growing Rolling Window Evaluation (`REQ-TRK-023.1`)*:
• In `CyclingPowerMetricsDevice.calculateMetrics()`, whenever `!mPowerWindow30s.isEmpty()` (`1 <= mPowerWindow30s.size() <= 30`), the rolling average SHALL be computed over the currently accumulated $k = \text{mPowerWindow30s.size()}$ samples (`double pRolling = sum / (double) mPowerWindow30s.size()`).
• Welford's online 4th-power running mean (`mCount4thPower++`, `mMean4thPower += (pRollingPow4 - mMean4thPower) / (double) mCount4thPower`) and `mLastCalculatedNp = Math.pow(mMean4thPower, 0.25)` SHALL update on every valid tick starting at $k = 1$.
2. *Immediate Live Telemetry Emission From Second 1 (`REQ-TRK-023.2`)*:
• Starting from the first valid power sample (`k = 1`), `CyclingPowerMetricsDevice` SHALL publish non-null live `NORMALIZED_POWER` (`mLastCalculatedNp`) and, when `ftp != null && ftp > 0`, non-null live `INTENSITY_FACTOR` (`NP / FTP`) and `TSS` (`(mActivePowerDurationSec * NP * IF) / (FTP * 36.0)`).
3. *Seamless Transition to Full 30s Window & Empty Guard (`REQ-TRK-023.3`)*:
• From second 30 onward (`mPowerWindow30s.size() == 30`), `pRolling` SHALL evaluate over the full 30-sample FIFO sliding window (`sum / 30.0`).
• Only when `mPowerWindow30s.isEmpty()` (or when instantaneous input `power` is `null`, `NaN`, infinite, or `< 0.0`) SHALL `NORMALIZED_POWER`, `INTENSITY_FACTOR`, and `TSS` emit `null`.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement `REQ-TRK-023`, amending the initial 29-second warm-up behavior of `REQ-TRK-020` (`ATT-1290`) and `REQ-TRK-021` (`ATT-1291`). Targets `CyclingPowerMetricsDevice.java`, `NormalizedPowerMathTest.kt`, `MechanicalWorkAndTssMathTest.kt`, and `CyclingPowerMetricsDeviceTest.kt`.
2. *Historical Origin & Commit Trace*: Sprint 2026-42.4 (`ATT-1290` commit `6e7d4512`, `ATT-1291` commit `f81ba92a`).
3. *Root Reason for Existing Formulation*: Offline post-processing tools traditionally wait for 30 full samples before computing NP, which caused a misleading 29-second `"--"` display on live cockpit tiles.
4. *Preservation of Core Invariants*: Welford's online 4th-power running mean, 0W coasting inclusion, recording suspension gating, long-pause (`> 30s`) buffer flushing, and unconfigured FTP (`null`/`<= 0`) handling remain 100% preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an active cycling workout with valid power samples (`250.0 W`) and configured FTP (`250 W`),
• *When* `calculateMetrics()` runs during seconds `1..29` (`1 <= mPowerWindow30s.size() < 30`),
• *Then* `NORMALIZED_POWER` emits `250.0`, `INTENSITY_FACTOR` emits `1.0`, and `TSS` emits `(t * 250 * 1.0) / (250 * 36.0)` from second 1 without emitting `null`.
• *Given* a long pause (`> 30s`) that flushes `mPowerWindow30s`,
• *When* recording resumes with a new valid power sample (`100.0 W`),
• *Then* `mPowerWindow30s` computes its post-pause rolling average over the growing `1..29s` post-pause window (`sum / k`) and updates Welford's online running mean seamlessly.

**Invariants**: Immediate live feedback from second 1 (Rule 40); Welford's online 4th-power running mean; pause gating; 100% clean-room test pass rate MUST NOT be broken.

### REQ-TRK-024: Google Fused & Topographic DEM Priority Ordering in `MySensorManager`, Enum Declaration Alignment & `LINE_DISTANCE_m` Priority [Verified]
- **Tests**: `MySensorManager.java`, `DeviceType.java`, `DevicesDatabaseManager.java`, `MySensorManagerPriorityTest.kt`, `DevicesDatabaseManagerOrderingTest.kt`, `SensorSourceDialogPriorityTest.kt`
- **Implementation**: Prioritize `SPEED_AND_LOCATION_GOOGLE_FUSED` over `SPEED_AND_LOCATION_GPS` in `MySensorManager.SPEED_PRIORITY`, prioritize `ALTITUDE_FROM_POSITION` over `ALTITUDE_FROM_PRESSURE` in `ALTITUDE_PRIORITY`, align `DeviceType` enum order, register `LINE_DISTANCE_m` in `LOCATION_PRIORITY`, provide canonical rank helper, and pair Google Fused by default on fresh install in `DevicesDatabaseManager` when Google Play Services is available (falling back to GPS).
- **Rationale**: `MySensorManager` (`app/src/main/java/com/atrainingtracker/banalservice/sensor/MySensorManager.java`), `DeviceType` (`app/src/main/java/com/atrainingtracker/banalservice/devices/DeviceType.java`), and `DevicesDatabaseManager` (`app/src/main/java/com/atrainingtracker/banalservice/database/DevicesDatabaseManager.java`) SHALL maintain strict, consistent location provider priority ordering across speed, altitude, location, distance, and database storage (ATT-3961, ATT-4375, ATT-5491, ATT-5492, ATT-6264):
1. *`SPEED_PRIORITY`, `ALTITUDE_PRIORITY` & `LOCATION_PRIORITY` Ordering (`REQ-TRK-024.1`)*:
• `MySensorManager.SPEED_PRIORITY` SHALL be ordered `[DeviceType.RUN_SPEED, DeviceType.BIKE_SPEED, DeviceType.BIKE_SPEED_AND_CADENCE, DeviceType.BIKE_POWER, DeviceType.SPEED_AND_LOCATION_GOOGLE_FUSED, DeviceType.SPEED_AND_LOCATION_GPS]`.
• `MySensorManager.ALTITUDE_PRIORITY` SHALL be ordered `[DeviceType.ALTITUDE_FROM_POSITION, DeviceType.ALTITUDE_FROM_PRESSURE, DeviceType.SPEED_AND_LOCATION_GOOGLE_FUSED, DeviceType.SPEED_AND_LOCATION_GPS]`.
• `MySensorManager.LOCATION_PRIORITY` SHALL be ordered `[DeviceType.SPEED_AND_LOCATION_GOOGLE_FUSED, DeviceType.SPEED_AND_LOCATION_GPS]`.
2. *`ProxySensor` Retention, Zero-Latency Update Loop & Dynamic Source Upgrade/Downgrade (`REQ-TRK-024.2`)*:
• When a `DeviceType.SPEED_AND_LOCATION_GOOGLE_FUSED` device registers `SPEED_mps`, `PACE_spm`, `DISTANCE_m`, and `DISTANCE_m_LAP` in `MySensorManager` and no higher-priority speed sensor is registered, `getBestSensor(sensorType)` SHALL return the Google Fused sensor, `getSensor(sensorType)` SHALL retain an active `ProxySensor` (instead of removing it via `removeSensor(sensorType)`), and `mSensorsSensor` (`SensorType.SENSORS` GC string) SHALL mark `'D'` (index `1`) and `'S'` (index `2`).
• When a higher-priority device (`RUN_SPEED`, `BIKE_SPEED`, `BIKE_SPEED_AND_CADENCE`, `BIKE_POWER`) or alternate location device (`SPEED_AND_LOCATION_GPS`) registers or unregisters, `MySensorManager` SHALL dynamically upgrade or fall back the `ProxySensor` source according to `SPEED_PRIORITY` in bounded $O(1)$ time over the 6-element `EnumMap` priority list without introducing latency spikes in the `ProxySensor` update loop.
3. *`DeviceType.java` Enum Declaration Order Alignment (`REQ-TRK-024.3`)*:
• In `DeviceType.java`, the declaration order of location enum constants SHALL be `SPEED_AND_LOCATION_GPS`, then `SPEED_AND_LOCATION_GOOGLE_FUSED`.
• This ensures that natural `Enum.ordinal()` order maintains determinism across enum sets while explicit priority lists govern telemetry selection.
4. *`LINE_DISTANCE_m` Location Priority Registration (`REQ-TRK-024.4`)*:
• In `MySensorManager`'s constructor, `setPriorityList(SensorType.LINE_DISTANCE_m, LOCATION_PRIORITY)` SHALL be registered.
• `getBestSensor(SensorType.LINE_DISTANCE_m)` SHALL prioritize `SPEED_AND_LOCATION_GOOGLE_FUSED` over `SPEED_AND_LOCATION_GPS` when multiple location sensors are active.
5. *Canonical Priority Rank Helper (`REQ-TRK-024.5`)*:
• `MySensorManager` SHALL expose a public static method `getPriorityRank(SensorType sensorType, DeviceType deviceType)` returning the 0-based priority index of `deviceType` for `sensorType` across all sensor types (`SPEED`, `ALTITUDE`, `LOCATION`, `CADENCE`, single-device sensors).
• For `LOCATION_PRIORITY` metrics, `SPEED_AND_LOCATION_GOOGLE_FUSED` SHALL return rank `0` and `SPEED_AND_LOCATION_GPS` SHALL return rank `1`. For `SPEED_PRIORITY` metrics, `SPEED_AND_LOCATION_GOOGLE_FUSED` SHALL return rank `4` and `SPEED_AND_LOCATION_GPS` SHALL return rank `5`. For `ALTITUDE_PRIORITY`, `ALTITUDE_FROM_POSITION` SHALL return rank `0`, `ALTITUDE_FROM_PRESSURE` SHALL return rank `1`, `SPEED_AND_LOCATION_GOOGLE_FUSED` SHALL return rank `2`, and `SPEED_AND_LOCATION_GPS` SHALL return rank `3`.
• If `deviceType` is not in the priority list, it SHALL return fallback rank `1000 + deviceType.ordinal()`. If `deviceType == null`, it SHALL return `Integer.MAX_VALUE`.
6. *`DevicesDatabaseManager.java` Location Insertion Order & Fresh Install Default Pairing (`REQ-TRK-024.6`)*:
• In `DevicesDatabaseManager.addLocationDevicesToDb(SQLiteDatabase db)`, location devices SHALL be inserted in the sequence `SPEED_AND_LOCATION_GPS` and `SPEED_AND_LOCATION_GOOGLE_FUSED`.
• When Google Play Services is available (`GooglePlayServicesUtil.isGooglePlayServicesAvailable(mContext) == ConnectionResult.SUCCESS`), `SPEED_AND_LOCATION_GOOGLE_FUSED` SHALL be initialized as `PAIRED = true` and `SPEED_AND_LOCATION_GPS` as `PAIRED = false`.
• If Google Play Services is unavailable (de-Googled devices), `SPEED_AND_LOCATION_GPS` SHALL remain `PAIRED = true` and `SPEED_AND_LOCATION_GOOGLE_FUSED` as `PAIRED = false`.
• Existing database installations and upgrades (`oldVersion > 6`) SHALL NOT alter or overwrite the athlete's existing pairing configuration.

### Requirement Archaeology & Chesterton's Fence Audit
1. *Original Requirement ID & Target*: Net-new requirement `REQ-TRK-024` targeting `MySensorManager.java`, extended by `ATT-4375` targeting `DeviceType.java`, `MySensorManager.java`, and `DevicesDatabaseManager.java`, amended by `ATT-5491`, `ATT-5492`, and amended by `ATT-6264`.
2. *Historical Origin & Commit Trace*: Commit `fc28443c` (`ATT-3961`, Sprint `2026-43.1`), refined in `ATT-4375` (Sprint `2026-43.2`), amended in `ATT-5491` (Sprint `2026-45.8`), `ATT-5492` (Sprint `2026-45.9`), and `ATT-6264` (Sprint `2026-46.1`).
3. *Root Reason for Existing Formulation*: In `ATT-5491`/`ATT-5492`, `ALTITUDE_FROM_PRESSURE` was placed at rank 0 ahead of `ALTITUDE_FROM_POSITION` because barometric altitude provides smooth 1-Hz relative elevation without requiring GPS fixes or network connectivity. However, hardcoding barometer above DEM meant explicitly pairing DEM had zero effect unless the user manually unpaired the barometer.
4. *Preservation of Core Invariants*: `ALTITUDE_FROM_POSITION` remains default unpaired (`PAIRED = false`) in `DeviceManager` and `DevicesDatabaseManager`. Fresh installs and unconfigured users retain `ALTITUDE_FROM_PRESSURE` as the active default altitude source. When DEM is paired, seamless fallback to barometer operates whenever DEM is not yet resolved. Dedicated wheel speed and power sensors still strictly supersede all location sensors. Existing user databases and pairings are 100% untouched.

**Acceptance Criteria (Given-When-Then)**:
• *Given* a `MySensorManager` with only a `DeviceType.SPEED_AND_LOCATION_GOOGLE_FUSED` device registered,
• *When* `SPEED_mps`, `PACE_spm`, `DISTANCE_m`, `DISTANCE_m_LAP`, `ALTITUDE`, `LATITUDE`, and `LONGITUDE` are registered,
• *Then* `getSensor(...)` retains active `ProxySensor` instances for all 7 sensors and `getSensor(SensorType.SENSORS).getValue()` equals `"-DS----AG--"`.
• *Given* `SPEED_AND_LOCATION_GPS`, `SPEED_AND_LOCATION_GOOGLE_FUSED`, and `BIKE_SPEED` sensors registered and unregistered,
• *When* `getBestSensor(SensorType.SPEED_mps)` and `getBestSensor(SensorType.DISTANCE_m)` are evaluated,
• *Then* they follow `BIKE_SPEED > SPEED_AND_LOCATION_GOOGLE_FUSED > SPEED_AND_LOCATION_GPS`.
• *Given* `SPEED_AND_LOCATION_GOOGLE_FUSED` and `SPEED_AND_LOCATION_GPS` registered for `SensorType.LATITUDE`, `SensorType.LONGITUDE`, `SensorType.BEARING`, `SensorType.ACCURACY`, or `SensorType.LINE_DISTANCE_m`,
• *When* `getBestSensor(...)` is evaluated,
• *Then* it SHALL return `SPEED_AND_LOCATION_GOOGLE_FUSED`.
• *Given* `DevicesDatabaseManager.addLocationDevicesToDb` executed on a fresh database with Google Play Services available,
• *When* queried,
• *Then* `SPEED_AND_LOCATION_GOOGLE_FUSED` is `PAIRED = 1` and `SPEED_AND_LOCATION_GPS` is `PAIRED = 0`.
• *Given* `DevicesDatabaseManager.addLocationDevicesToDb` executed on a fresh database with Google Play Services unavailable,
• *When* queried,
• *Then* `SPEED_AND_LOCATION_GPS` is `PAIRED = 1` and `SPEED_AND_LOCATION_GOOGLE_FUSED` is `PAIRED = 0`.
• *Given* any `SensorType` and `DeviceType`,
• *When* `MySensorManager.getPriorityRank(sensorType, deviceType)` is called,
• *Then* it returns canonical priority rank 0 for GOOGLE_FUSED and 1 for GPS in location metrics; 4 for GOOGLE_FUSED and 5 for GPS in speed metrics; and 2 for GOOGLE_FUSED and 3 for GPS in altitude metrics.

**Invariants**: Dedicated physical sensor precedence (`RUN_SPEED > BIKE_SPEED > BIKE_POWER > GNSS`, `PRESSURE > POSITION > GNSS`), zero overwrite of existing user pairings, fallback to raw GPS on de-Googled devices, and 100% clean-room test pass rate MUST NOT be broken.

### REQ-TRK-025: Deterministic `AdbGpsInjector` Mock Provider Lifecycle Bound to `PLAYING` State in `tools/replay_workout.py` [Verified]
- **Tests**: `tools/replay_workout.py`, `tools/test_replay_workout.py`
- **Implementation**: Release ADB mock location test providers in `tools/replay_workout.py` immediately on Pause, Stop, or completion, and clean up lingering providers on startup.
- **Rationale**: `AdbGpsInjector` (`tools/replay_workout.py`) SHALL bind ADB mock location test provider registration (`add-test-provider gps/network/fused`) strictly to the active `PLAYING` state and immediately remove test providers (`remove-test-provider gps/network/fused`) whenever replay is `PAUSED`, `STOPPED`, `kill_gps`, or reaches workout completion (`EOF`), as well as on startup cleanup and `--cleanup` CLI invocation, so Android's hardware `LocationManager.GPS_PROVIDER` is never left trapped in `[mock]` mode if the phone is unplugged while paused or stopped (ATT-3961 / ATT-3971, amending `REQ-TOOL-002` Clause 4):
1. *Proactive Startup & `--cleanup` Test Provider Removal (`REQ-TRK-025.1`)*:
• On startup (while `ReplayState.STOPPED`) and when invoked with `--cleanup`, `AdbGpsInjector` SHALL proactively remove any lingering `gps`, `network`, and `fused` test providers (`cmd location providers set-test-provider-enabled <p> false` and `cmd location providers remove-test-provider <p>`) without registering new test providers while `STOPPED`.
2. *Lazy Registration on `PLAYING` & Immediate Removal on `PAUSED` / `STOPPED` / `EOF` (`REQ-TRK-025.2`)*:
• `AdbGpsInjector` SHALL track `self.providers_registered: bool = False`, register `add-test-provider gps/network/fused` lazily only when entering `PLAYING` (or inside `inject_location` when `not self.is_killed and not self.providers_registered`), and immediately execute `release_providers()` (`remove-test-provider gps/network/fused` and `self.providers_registered = False`) whenever playback transitions to `PAUSED`, `STOPPED`, `kill_gps(True)`, timeline completion (`EOF`), or `teardown()`.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement `REQ-TRK-025`, amending `REQ-TOOL-002` (`ATT-2969`) Clause 4. Targets `tools/replay_workout.py` and `tools/test_replay_workout.py`.
2. *Historical Origin & Commit Trace*: Sprint 2026-41.6 (`ATT-2969`, commit `1523f683`).
3. *Root Reason for Existing Formulation*: `AdbGpsInjector.setup()` originally registered `add-test-provider` once at startup and removed it only in `teardown()` at process exit.
4. *Preservation of Core Invariants*: Lazy registration on `PLAYING` and immediate removal on `PAUSED`/`STOPPED`/`EOF` preserves 100% of live GPS injection during active playback while ensuring hardware GNSS (`gps_hardware`) is immediately restored whenever replay is paused, stopped, or completed.

**Acceptance Criteria (Given-When-Then)**:
• *Given* `AdbGpsInjector.setup()` called at startup while `STOPPED`,
• *When* inspected before playback starts,
• *Then* lingering `gps`, `network`, and `fused` test providers are removed (`remove-test-provider`) and `providers_registered` is `False`.
• *Given* active playback (`providers_registered == True`),
• *When* the user pauses, stops, toggles `kill_gps(True)`, or playback reaches the end of the timeline (`EOF`),
• *Then* `release_providers()` immediately executes `remove-test-provider` for `gps`, `network`, and `fused` and sets `providers_registered = False`.

**Invariants**: Deterministic mock provider state, immediate hardware GPS restoration on pause/stop/EOF, and 100% test pass rate MUST NOT be broken.

### REQ-TRK-026: Resumed Workout Sample Table Resolution, Asynchronous BANAL Binding Barrier, and Sample Write Error Surfacing in `TrackerService` [Verified]
- **Tests**: `TrackerService.java`, `WorkoutSamplesDatabaseManager.java`, `TrackerServiceResumeTest.kt` | **Tickets**: `ATT-3980`, `ATT-5827`
- **Implementation**: Ensure resumed workouts resolve `mSamplesTableName` after asynchronous `BANALService` binding, reject `"null"` table names, order `recreateValuesWhenResuming()` before `createNewLap()`/`onSearchingFinished()`, and surface sample write SQLite exceptions.
- **Rationale**: `TrackerService` (`app/src/main/java/com/atrainingtracker/trainingtracker/tracker/TrackerService.java`) and `WorkoutSamplesDatabaseManager` (`app/src/main/java/com/atrainingtracker/trainingtracker/database/WorkoutSamplesDatabaseManager.java`) SHALL guarantee that resumed workouts (`RESUME_BY_USER` and `RESUME_SERVICE_RECREATION`) always resolve and write 1 Hz samples to the resumed workout's existing SQLite sample table (`"\"<BASE_FILE_NAME>\""`), even when `BANALService` binds asynchronously after `onStartCommand()` returns (ATT-3980, extending `REQ-TRK-015`, `REQ-TRK-016`, and `REQ-TRK-017`):
1. *Sample Table Name Synchronization on Resume (`REQ-TRK-026.1`)*:
• `TrackerService.applyResumedState(@NonNull ResumedWorkoutState state)` SHALL immediately compute and assign `mSamplesTableName = WorkoutSamplesDatabaseManager.getTableName(mBaseFileName)` whenever `mBaseFileName` is restored from `state.baseFileName`, and complete `mTableInitializationFuture.complete(null)`.
2. *Non-Null Guards for Sample Table Name Computation (`REQ-TRK-026.2`)*:
• `TrackerService.onStartCommand()` SHALL only compute `mSamplesTableName = WorkoutSamplesDatabaseManager.getTableName(mBaseFileName)` when `mBaseFileName != null && !mBaseFileName.trim().isEmpty()`.
• `WorkoutSamplesDatabaseManager.getTableName(@NonNull String baseFileName)` SHALL reject `null`, empty/blank, or literal `"null"` (case-insensitive) input by throwing an `IllegalArgumentException`, preventing silent generation of `"\"null\""` table identifiers.
3. *Asynchronous Resume Readiness Barrier & Ordered `onServiceConnected()` (`REQ-TRK-026.3`)*:
• At the start of `TrackerService.onStartCommand()` for `START_NORMAL`, `RESUME_BY_USER`, and `RESUME_SERVICE_RECREATION`, `mTableInitializationFuture` SHALL be initialized as an incomplete `new CompletableFuture<>()` barrier and completed only when table creation (`START_NORMAL`) or `applyResumedState()` (`RESUME_BY_USER`, `RESUME_SERVICE_RECREATION`) completes. `mTrackerHandle` SHALL be managed with reusable cancellation (`cancelTrackerHandle()`), allowing test fixtures and teardown paths to halt the periodic 1 Hz sampling loop deterministically without thread leakage (ATT-5827).
• In `mBanalConnection.onServiceConnected()`, when `mStartType` is `RESUME_BY_USER` or `RESUME_SERVICE_RECREATION`, `recreateValuesWhenResuming()` SHALL execute **before** `mBanalService.createNewLap()` and `onSearchingFinished()` so that `mWorkoutID`, `mLapNr`, `mBaseFileName`, and `mSamplesTableName` are restored prior to lap increments or active tracking transitions.
• On deferred resume (`mBanalService == null` during `onStartCommand()`), `notifyTrackingStarted(mWorkoutID)` SHALL be invoked inside `applyResumedState()` once `mWorkoutID > 0` is restored (with a null-safe `getBaseContext() == null` guard in `notifyTrackingStarted` for unit tests), and `mAltitudeCorrectionReceiver` SHALL capture `mWorkoutID` and `mSamplesTableName` inside `mTableInitializationFuture.thenAcceptAsync(...)`.
4. *Explicit Error Surfacing & Diagnostic Telemetry on Sample Write Failure (`REQ-TRK-026.4`)*:
• `TrackerService.sampleAndWriteToDb()` SHALL guard against a `null`, empty, or `"\"null\""` `mSamplesTableName`, logging `Log.e(TAG, ...)` and aborting the write.
• `TrackerService.handleMissingColumns()` SHALL log `Log.e(TAG, ...)` and rethrow any `SQLiteException` whose message does not indicate a missing column (`!message.contains(" has no column named ")`), or when `ALTER TABLE` fails, preventing silent sample loss.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement `REQ-TRK-026`, extending `REQ-TRK-015` (`ATT-2641`), `REQ-TRK-016` (`ATT-2691`), and `REQ-TRK-017` (`ATT-2710`). Targets `TrackerService.java`, `WorkoutSamplesDatabaseManager.java`, and `TrackerServiceResumeTest.kt`.
2. *Historical Origin & Commit Trace*: `ATT-2641` (`d497f85b`), `ATT-2691` (`03be2e2e`), and `ATT-2710` (`272d10a5`).
3. *Root Reason for Existing Formulation*: `ATT-2691` extracted `applyResumedState()` for synchronous unit testing where `mBanalService` was pre-injected, overlooking that in production `bindService()` in `onCreate()` is asynchronous (`mBanalService == null` in `onStartCommand()`), which caused line 613 to evaluate `getTableName(null)` -> `"\"null\""` and `applyResumedState()` in `onServiceConnected()` never recomputed `mSamplesTableName`.
4. *Preservation of Core Invariants*: Preserves `START_NORMAL` async table initialization (`REQ-TRK-015`), synchronous resume behavior (`REQ-TRK-016`), dynamic missing-column recovery (`ALTER TABLE ... ADD COLUMN`), and `BANALService.checkOrInitializeLocationDevices()` structural contract.

**Acceptance Criteria (Given-When-Then)**:
• *Given* a workout resumed via `RESUME_BY_USER` or `RESUME_SERVICE_RECREATION` while `mBanalService` is initially `null` in `onStartCommand()`,
• *When* `mBanalConnection.onServiceConnected()` fires,
• *Then* `recreateValuesWhenResuming()` runs before `createNewLap()` and `onSearchingFinished()`, `mSamplesTableName` is updated to `"\"<BASE_FILE_NAME>\""` (never `"\"null\""`), `mTableInitializationFuture` completes, and subsequent `sampleAndWriteToDb()` calls insert rows into `"\"<BASE_FILE_NAME>\""`.
• *Given* `WorkoutSamplesDatabaseManager.getTableName(baseFileName)` called with `null`, `""`, or `"null"`,
• *When* evaluated,
• *Then* it throws `IllegalArgumentException`.

**Invariants**: `REQ-TRK-015`, `REQ-TRK-016`, `REQ-TRK-017`, `FreshInstallLocationInitTest` structural contract, and 100% clean-room test pass rate MUST NOT be broken.

### REQ-TRK-027: Fault-Tolerant 1 Hz Sampling Loop, Clock Timer Exception Guard, and Volatile `BANALServiceComm` Snapshot Concurrency Safety [Verified]
- **Tests**: `TrackerService.java`, `ClockDevice.java`, `DeviceManager.java`, `TrackerServiceResilienceTest.kt`, `ClockDeviceResilienceTest.kt`
- **Implementation**: Guard `TrackerService.tracker.run()` and `ClockDevice.ClockTimeTask.run()` with `try / catch (Throwable)` and make `mBanalService` `volatile` with local reference snapshotting so a single unchecked exception or concurrent service disconnect never stops the 1 Hz sampling loop or clock timer.
- **Rationale**: `TrackerService` (`app/src/main/java/com/atrainingtracker/trainingtracker/tracker/TrackerService.java`) and `ClockDevice` (`app/src/main/java/com/atrainingtracker/banalservice/devices/ClockDevice.java`) SHALL guarantee that no single unchecked exception or concurrent `BANALService` disconnection permanently stops the 1 Hz workout sampling loop (`ScheduledExecutorService.scheduleAtFixedRate`) or the 1 Hz clock timer (`java.util.Timer`) (ATT-3981, complementing `REQ-TRK-015` and `REQ-TRK-026`):
1. *Top-Level `try / catch (Throwable t)` Guard in `TrackerService.tracker.run()` (`REQ-TRK-027.1`)*:
• `TrackerService.tracker.run()` SHALL wrap its entire execution body in `try { ... } catch (Throwable t)` that increments an atomic error counter (`mSamplingErrorCount`, exposed via `@VisibleForTesting int getSamplingErrorCount()`) and logs `Log.e(TAG, ...)` so that no uncaught `Throwable` escapes to `ScheduledExecutorService.scheduleAtFixedRate` or suppresses subsequent 1 Hz ticks.
2. *Volatile `mBanalService` Visibility & Local Reference Snapshotting (`REQ-TRK-027.2`)*:
• `TrackerService.mBanalService` SHALL be declared `volatile` (`@Nullable volatile BANALServiceComm mBanalService;`).
• `TrackerService.tracker.run()` SHALL snapshot `final BANALServiceComm banalService = mBanalService;` into a local variable once per tick and pass that snapshot through `sampleAndWriteToDb(@Nullable BANALServiceComm banalService)`, `updateActiveSessionDevices(@Nullable BANALServiceComm banalService)`, and `evaluateAutoPauseTick(@Nullable BANALServiceComm banalService)` (retaining a delegating parameterless `sampleAndWriteToDb()` for reflection tests), and all other cross-thread/lifecycle readers (`createNewLap()`, `getSportTypeId()`, `onSearchingFinished()`, `endWorkout()`, `finalizeLiveSession()`) SHALL likewise snapshot `mBanalService` into a local variable before null-checking and dereferencing.
3. *Top-Level `try / catch (Throwable t)` Guard in `ClockDevice.ClockTimeTask.run()` (`REQ-TRK-027.3`)*:
• `ClockDevice.ClockTimeTask.run()` SHALL wrap its entire execution body in `try { ... } catch (Throwable t)` that increments an atomic error counter (`mClockTimerErrorCount`, exposed via `@VisibleForTesting int getClockTimerErrorCount()`) and logs `Log.e(TAG, ...)` so that no uncaught `Throwable` terminates the `java.util.Timer` thread.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement `REQ-TRK-027`, complementing `REQ-TRK-015` (`ATT-2641`) and `REQ-TRK-026` (`ATT-3980`). Targets `TrackerService.java`, `ClockDevice.java`, `TrackerServiceResilienceTest.kt`, and `ClockDeviceResilienceTest.kt`.
2. *Historical Origin & Commit Trace*: Legacy `TrackerService` (`mScheduler.scheduleAtFixedRate`) and `ClockDevice` (`timer.scheduleAtFixedRate`).
3. *Root Reason for Existing Formulation*: Both periodic tasks were originally written assuming `run()` would never throw unchecked exceptions and `mBanalService` would not be concurrently nulled mid-tick.
4. *Preservation of Core Invariants*: Preserves 100% of normal-path 1 Hz sampling, SQLite transaction semantics, auto-pause evaluation, and clock sensor increments while preventing permanent timer death on transient exceptions.

**Acceptance Criteria (Given-When-Then)**:
• *Given* the 1 Hz sampling loop (`TrackerService.tracker`) is running,
• *When* one tick throws an unchecked exception,
• *Then* the exception is caught and logged via `Log.e`, `getSamplingErrorCount()` increments, and subsequent ticks continue to sample and write.
• *Given* the clock timer task (`ClockDevice.ClockTimeTask`) throws an unchecked exception on one tick,
• *When* the next period elapses,
• *Then* `getClockTimerErrorCount()` increments and time sensors (`TIME_TOTAL`, `TIME_ACTIVE`, `TIME_LAP`) continue to advance.

**Invariants**: `REQ-TRK-015`, `REQ-TRK-026`, parameterless `sampleAndWriteToDb()` reflection compatibility, and 100% clean-room test pass rate MUST NOT be broken.

### REQ-TRK-028: `ProxySensor` Timestamp Delegation, Registered-Source Standstill Evaluation, Manual Resume State Synchronization & `ToneGenerator` Lifecycle Management [Verified]
- **Tests**: packetTimestampMs > 0L)`, and `TrackerService.evaluateAutoPauseTick(banalService)` SHALL pass `hasSpeedSource = (speedData != null)`.
• When `(currentSpeedMps != null && !isSpeedFiniteAndValid(currentSpeedMps))` OR `!hasSpeedSource`: `AutoPauseManager` SHALL transition to `AutoPauseState.SENSOR_LOST` and require `SENSOR_LOST_RECOVERY_TICKS` (2 consecutive fresh valid ticks) to recover to `TRACKING`.
• When `hasSpeedSource == true` and `currentSpeedMps` is `null` or finite $\ge 0.0$: if `currentSpeedMps == null` or the packet timestamp is stale (`packetTimestampMs <= 0L
- **Rationale**: `MySensor` (`app/src/main/java/com/atrainingtracker/banalservice/sensor/MySensor.java`), `ProxySensor` (`app/src/main/java/com/atrainingtracker/banalservice/sensor/ProxySensor.java`), `AutoPauseManager` (`app/src/main/java/com/atrainingtracker/trainingtracker/tracker/autopause/AutoPauseManager.kt`), `TrackerService` (`app/src/main/java/com/atrainingtracker/trainingtracker/tracker/TrackerService.java`), and `TrainingApplication` (`app/src/main/java/com/atrainingtracker/trainingtracker/TrainingApplication.java`) SHALL guarantee reliable end-to-end auto-pause and auto-resume evaluation through `ProxySensor` (ATT-3984, refining and extending `REQ-TRK-018`):
1. *`MySensor` & `ProxySensor` Timestamp and `SensorData` Delegation (`REQ-TRK-028.1`)*:
• `MySensor.java` SHALL expose `public long getLastUpdateTimestamp()` returning `mLastUpdateTimestamp`, and `MySensor.getSensorData()` SHALL use `getLastUpdateTimestamp()` when constructing `SensorData`.
• `ProxySensor.java` SHALL override `getLastUpdateTimestamp()` to return `mSourceSensor != null ? mSourceSensor.getLastUpdateTimestamp() : 0L` and override `getSensorData()` to return `SensorData<T>` with the delegated value (`getValue()`), string value (`getStringValue()`), unit (`getSensorType().getUnitId()`), `getDeviceName()` (`null`), and delegated timestamp (`getLastUpdateTimestamp()`), guarding all `mSourceSensor` delegations against `null`.
2. *Registered Speed Source Standstill vs. `SENSOR_LOST` Discrimination (`REQ-TRK-028.2`)*:
• `AutoPauseManager.evaluateTick(...)` SHALL accept `@JvmOverloads hasSpeedSource: Boolean = (isSpeedFiniteAndValid(currentSpeedMps)

### REQ-TRK-029: Safe SQLite Column Identifier Escaping for Custom Device Names & Rate-Limited Sample Insert Failure Telemetry in `TrackerService` [Verified]
- **Tests**: count % 60 == 0`.

**Invariants**: `<SensorType> (<DeviceName>)` column naming convention (`CSVFileWriter`, `.attbackup`, `WorkoutSamples.db`), `REQ-TRK-026` transaction semantics, and 100% clean-room test pass rate MUST NOT be broken.
- **Rationale**: `TrackerService` (`app/src/main/java/com/atrainingtracker/trainingtracker/tracker/TrackerService.java`) SHALL safely quote and unquote SQLite sample column identifiers using SQL-standard double quotes (`"..."` with `""` escaping for embedded double quotes) and rate-limit sample insertion error telemetry while preserving 100% backward compatibility with the `<SensorType> (<DeviceName>)` column naming convention (ATT-3985, extending `REQ-TRK-026.4`):
1. *Canonical SQLite Column Identifier Quoting & Unquoting Helpers (`REQ-TRK-029.1`)*:
• `TrackerService.quoteSqlColumnIdentifier(@NonNull String rawColumnName)` SHALL wrap `rawColumnName` in standard SQL double quotes and escape any embedded double quote `"` by doubling it (`""`): `"\"" + rawColumnName.replace("\"", "\"\"") + "\""`.
• `TrackerService.unquoteSqlColumnIdentifier(@NonNull String sqlColumnIdentifier)` SHALL recover the exact raw unquoted column name stored in SQLite (`sqlite_master` / `Cursor.getColumnNames()`): stripping only the outer `"` delimiter characters and replacing `""` with `"` when double-quoted, stripping only outer `'` delimiter characters and replacing `''` with `'` when single-quoted, and returning `sqlColumnIdentifier` unchanged otherwise, without ever stripping internal apostrophes (`'`) or quotes (`"`).
2. *Coordinated 3-Point Column Identifier Handling in `TrackerService` (`REQ-TRK-029.2`)*:
• **Point 1 (`sampleAndWriteToDb` Key Construction)**: For non-location device names (`deviceName != null` and not `"gps"`, `"network"`, or `"google_fused"`), `TrackerService.sampleAndWriteToDb()` SHALL construct the raw column name `sensorType.name() + " (" + deviceName + ")"` and quote it via `quoteSqlColumnIdentifier(...)` before inserting into `samplingValues` and `sensorName2Type`.
• **Point 2 (`handleMissingColumns` Column Existence Check)**: In `TrackerService.handleMissingColumns()`, each `key` in `values.keySet()` SHALL be unquoted via `String queryKey = unquoteSqlColumnIdentifier(key)` before checking `cursor.getColumnIndex(queryKey)` (with a fallback scan of `cursor.getColumnNames()` if `getColumnIndex` returns `< 0`).
• **Point 3 (`handleMissingColumns` `ALTER TABLE` DDL)**: When adding a missing column in `handleMissingColumns()`, `TrackerService` SHALL quote the column identifier via `quoteSqlColumnIdentifier(queryKey)` in `samplesDb.execSQL("ALTER TABLE " + tableName + " ADD COLUMN " + quoteSqlColumnIdentifier(queryKey) + " " + type + " null;");`.
3. *Rate-Limited Sample Insert Failure Counter & Diagnostic Telemetry (`REQ-TRK-029.3`)*:
• `TrackerService` SHALL maintain an atomic counter `mSampleInsertFailureCount` (`AtomicInteger`, exposed via `@VisibleForTesting public int getSampleInsertFailureCount()`).
• Whenever a 1 Hz sample row fails to insert, `TrackerService` SHALL increment `mSampleInsertFailureCount` and emit rate-limited `Log.e(TAG, ...)` diagnostics on the initial burst (`count <= 3`) and every `SAMPLE_INSERT_FAILURE_LOG_INTERVAL` (`60`) failures thereafter (`count % 60 == 0`), including `failureCount` in the log message.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement `REQ-TRK-029`, extending `REQ-TRK-026.4` (`ATT-3980`). Targets `TrackerService.java` and `TrackerServiceDeviceNameQuotingTest.kt`.
2. *Historical Origin & Commit Trace*: Legacy commits `a7049ef9` and `fb9fac41`, and Sprint `2026-42.9` commit `f4650d74` (`ATT-3980`).
3. *Root Reason for Existing Formulation*: Android's `SQLiteDatabase.insertOrThrow()` concatenates `ContentValues` keys directly into `INSERT INTO <table>(<key1>, ...)` without quoting identifiers; the original author wrapped `<SensorType> (<DeviceName>)` in single quotes (`'...'`) and stripped quotes via `key.replace("'", "")`, assuming device names would never contain apostrophes or quotes.
4. *Preservation of Core Invariants*: Switching to SQL-standard double quotes (`"..."` with `""` escaping) and outer-delimiter unquoting preserves the exact unquoted SQLite column name `<SensorType> (<DeviceName>)` in `sqlite_master` and `Cursor.getColumnNames()`, maintaining 100% compatibility with existing `WorkoutSamples.db` tables, `.attbackup` archives, `CSVFileWriter`, and `REQ-TRK-026` transaction semantics.

**Acceptance Criteria (Given-When-Then)**:
• *Given* a connected device whose name contains an apostrophe (`Rainer's HRM`), double quotes (`Pro "2" Power`), or both (`Rainer's "Pro" Cadence`),
• *When* 1 Hz workout samples are recorded via `TrackerService.sampleAndWriteToDb()`,
• *Then* the device columns are dynamically added via `ALTER TABLE`, all 1 Hz sample rows are stored without SQL errors, and subsequent ticks or mid-workout sensor additions never re-add existing columns.
• *Given* a persistent unrecoverable SQLite sample insert failure,
• *When* repeated 1 Hz sample writes fail,
• *Then* `getSampleInsertFailureCount()` increments on every failure and `Log.e` is rate-limited to `count <= 3

### REQ-TRK-030: Fault-Isolated Workout Finalization, Dedicated `FINISHED=1` Commit, Defensive `AccumulatedSensorTypeSet` Copy, and Zero-Duration Average Speed Guard [Verified]
- **Tests**: `TrackerService.java`, `MySensorManager.java`, `TrackerServiceFinalizationTest.kt`
- **Implementation**: Isolate `FINISHED = 1` in its own dedicated minimal transaction in `endWorkout()`, return a synchronized defensive copy from `MySensorManager.getAccumulatedSensorTypeSet()`, guard `getAverageSpeed()` against `0 s` active time (`NaN`/`Infinity`), and split `finalizeLiveSession()` into independently guarded steps with map/streams persisted first.
- **Rationale**: `TrackerService` (`app/src/main/java/com/atrainingtracker/trainingtracker/tracker/TrackerService.java`) and `MySensorManager` (`app/src/main/java/com/atrainingtracker/banalservice/sensor/MySensorManager.java`) SHALL guarantee that stopping any workout deterministically marks `WorkoutSummaries.FINISHED = 1`, prevents `NaN`/`Infinity` average speed at `0 s` active time, avoids `ConcurrentModificationException` on accumulated sensor types, and isolates post-workout finalization steps so map polylines and altitude/distance streams are always persisted (ATT-3986, complementing `REQ-TRK-015`, `REQ-TRK-016`, and `REQ-TRK-027`):
1. *Dedicated, Minimal `FINISHED = 1` Transaction First in `endWorkout()` (`REQ-TRK-030.1`)*:
• `TrackerService.endWorkout()` SHALL persist `WorkoutSummaries.FINISHED = 1` first in its own dedicated, minimal SQLite transaction on `WorkoutSummaries.TABLE` (`WHERE C_ID = mWorkoutID`) before executing auxiliary summary updates.
• Auxiliary operations in `endWorkout()` (`saveAccumulatedSensorTypes`, `GC_DATA`, `SPORT_ID`, and `B_SPORT` updates) SHALL execute in separate `try / catch (Throwable t)` guards so an exception in any auxiliary step never rolls back `FINISHED = 1`.
2. *Defensive Copy & Synchronized Mutation in `MySensorManager.getAccumulatedSensorTypeSet()` (`REQ-TRK-030.2`)*:
• `MySensorManager.getAccumulatedSensorTypeSet()` SHALL return `new HashSet<>(mAccumulatedSensorTypeSet)` while holding the `synchronized` monitor on `MySensorManager`.
• `MySensorManager.notifyNewSensor(MySensor mySensor)` SHALL be `synchronized` so concurrent sensor registrations during `endWorkout()` cannot throw `ConcurrentModificationException`.
3. *Zero-Duration & Non-Finite Average Speed Guard (`REQ-TRK-030.3`)*:
• `TrackerService.averageSpeedCalculateable()` SHALL check `mTimeActive_s > 0 && Double.isFinite(mDistanceTotal_m) && mDistanceTotal_m >= 0.0`.
• `TrackerService.getAverageSpeed()` SHALL return `0.0` whenever `!averageSpeedCalculateable()` or when `mDistanceTotal_m / mTimeActive_s` is non-finite, guaranteeing `getAverageSpeed()` never returns `NaN` or `Infinity`.
4. *Local `BANALServiceComm` Snapshot & Reordered, Independently Guarded Steps in `finalizeLiveSession()` (`REQ-TRK-030.4`)*:
• `TrackerService.finalizeLiveSession()` SHALL capture `final BANALServiceComm banalService = mBanalService;` once at entry with null checks throughout.
• `TrackerService.finalizeLiveSession()` SHALL execute independently `try / catch (Throwable t)`-guarded steps in priority order: (1) Map Polyline & Elevation/Distance Streams first, (2) Start/End Positions, Sensor Extrema & Max Line Distance, (3) Aerobic Efficiency / Decoupling & Power Training Metrics, (4) `guessCommuteAndTrainer()`, and (5) Identity Arbitration, Route Clustering & Auto-Naming.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement `REQ-TRK-030`, complementing `REQ-TRK-015` / `REQ-TRK-016` (`ATT-2641`, `ATT-2972`) and `REQ-TRK-027` (`ATT-3981`). Targets `TrackerService.java`, `MySensorManager.java`, and `TrackerServiceFinalizationTest.kt`.
2. *Historical Origin & Commit Trace*: `TrackerService.endWorkout()` transaction wrapping (`ATT-2641`, `ATT-2972`) and incremental `finalizeLiveSession()` additions (`SCRUM-108`, `SCRUM-200`, `ATT-528`, `ATT-773`, `ATT-1398`, `ATT-3860`, `ATT-3861`).
3. *Root Reason for Existing Formulation*: `endWorkout()` bundled all summary updates into one transaction assuming `saveAccumulatedSensorTypes()` and `getSportTypeId()` could not fail, while `finalizeLiveSession()` appended new enrichment features ahead of the original map/stream persistence step.
4. *Preservation of Core Invariants*: Preserves 100% of normal-path workout finalization, extrema, physiological metrics, clustering, auto-naming, and terminal `TRACKING_FINISHED_INTENT` broadcasts while guaranteeing `FINISHED = 1` and map/stream persistence under zero-duration stops, concurrent sensor registration, or sub-step faults.

**Acceptance Criteria (Given-When-Then)**:
• *Given* a workout with `0 s` active time (`mTimeActive_s == 0`),
• *When* the user stops the workout,
• *Then* `getAverageSpeed()` returns `0.0` (no `NaN`/`Infinity`) and the workout is marked `FINISHED = 1`.
• *Given* one finalization sub-step throws or `mBanalService == null`,
• *When* the workout is finalized via `endWorkout()`,
• *Then* `FINISHED = 1`, the encoded polyline, and elevation/distance streams are still persisted.
• *Given* concurrent sensor registration in `MySensorManager`,
• *When* `getAccumulatedSensorTypeSet()` is iterated,
• *Then* it returns a defensive copy without throwing `ConcurrentModificationException`.

**Invariants**: `REQ-TRK-015`, `REQ-TRK-016`, `REQ-TRK-026`–`029`, terminal `TRACKING_FINISHED_INTENT` dispatch, and 100% clean-room test pass rate MUST NOT be broken.

### REQ-TRK-031: LiveWorkoutSession Instantiation & State Rehydration on Workout Resume (`RESUME_BY_USER` & `RESUME_SERVICE_RECREATION`) [Verified]
- **Tests**: `WorkoutSummariesDatabaseManager.java`, `LiveWorkoutSession.java`, `TrackerService.java`, `LiveWorkoutSessionTest.kt`, `TrackerServiceResumeTest.kt`
- **Implementation**: Instantiate and rehydrate `LiveWorkoutSession` (streams + extrema with sample counts) on workout resume (`RESUME_BY_USER` and `RESUME_SERVICE_RECREATION`) so resumed workouts record extrema, map streams, and derived metrics.
- **Rationale**: `TrackerService` (`app/src/main/java/com/atrainingtracker/trainingtracker/tracker/TrackerService.java`), `LiveWorkoutSession` (`app/src/main/java/com/atrainingtracker/trainingtracker/tracker/LiveWorkoutSession.java`), and `WorkoutSummariesDatabaseManager` (`app/src/main/java/com/atrainingtracker/trainingtracker/database/WorkoutSummariesDatabaseManager.java`) SHALL instantiate and rehydrate `LiveWorkoutSession` whenever a workout is resumed (`StartMode.RESUME_BY_USER` or `StartMode.RESUME_SERVICE_RECREATION`), preserving delta-encoded 20-second map/altitude/distance streams, per-sensor `MIN`/`MAX`/`AVG` statistics (including mid-workout sensor sample counts), and full post-workout finalization (ATT-3987, complementing `REQ-TRK-015`, `REQ-TRK-016`, `REQ-TRK-026`, and `REQ-TRK-030`):
1. *Sample Count Persistence in `TABLE_EXTREMA_VALUES` (`REQ-TRK-031.1` — Rule 20)*:
• `WorkoutSummariesDbHelper` SHALL increment `DB_VERSION` from `24` to `25` and define column `WorkoutSummaries.COUNT = "count"` (`INTEGER`) on `TABLE_EXTREMA_VALUES` (`ExtremumValues`) in both `CREATE_TABLE_EXTREMA_VALUES` and `onUpgrade` (`if (oldVersion < 25) db.execSQL("ALTER TABLE ... ADD COLUMN count INTEGER")`).
• `WorkoutSummariesDatabaseManager.ExtremaRecord` SHALL include `@Nullable public final Integer count` with a 6-argument constructor `ExtremaRecord(workoutId, sensorType, extremaType, value, position, count)` and a backward-compatible 5-argument constructor delegating with `count = null`, mapped symmetrically in `getExtremaForWorkouts(List<Long> workoutIds)`.
• `WorkoutSummariesDatabaseManager` SHALL provide `updateExtremaValue(long workoutId, SensorType sensorType, ExtremaType extremaType, double value, @Nullable Integer count)` (with the 4-argument overload delegating with `count = null`).
2. *`LiveWorkoutSession` Stream & Sensor Stats Rehydration API (`REQ-TRK-031.2`)*:
• `LiveWorkoutSession.RunningStats` SHALL expose `getCount()` and `rehydrate(@Nullable Double minValue, @Nullable Double maxValue, @Nullable Double avgValue, int sampleCount)` restoring `min`, `max`, `count`, and `sum = avgValue * count` without setting `minChanged` or `maxChanged`.
• `LiveWorkoutSession` SHALL expose `rehydrateSensorStats(...)` and `rehydrateStreams(@Nullable List<LatLng> preResumeLatLngs, @Nullable List<Double> preResumeAltitudes, @Nullable List<Double> preResumeDistances)` restoring `rawLatLngs`, `sampledLatLngs`, `sampledAltitudes`, `sampledDistances`, and the delta-encoding anchors `lastLatE5`, `lastLngE5`, `lastAltE2`, and `lastDistE2`.
3. *`TrackerService` Resume Rehydration & Finalization Continuity (`REQ-TRK-031.3`)*:
• In `TrackerService.applyResumedState(@NonNull ResumedWorkoutState state)`, `TrackerService` SHALL instantiate `mLiveSession = new LiveWorkoutSession(mTimeActive_s, IMPORTANT_SENSOR_TYPES)` and invoke `rehydrateLiveSession(mWorkoutID, mTimeActive_s)` to decode persisted streams (`getEncodedStreams`) and restore per-sensor `MIN`/`MAX`/`AVG` and sample counts (`getExtremaForWorkouts`, using `record.count` when `> 0` and falling back to `mTimeActive_s` for legacy rows).
• In `TrackerService.sampleAndWriteToDb()`, `TrackerService` SHALL pass `stats.getCount()` when persisting `AVG`, `MIN`, and `MAX` extrema and SHALL NOT overwrite `ExtremaType.START` if a `START` position was already persisted prior to resume.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement `REQ-TRK-031`, complementing `REQ-TRK-015` (`ATT-2641`), `REQ-TRK-016` (`ATT-2691`), `REQ-TRK-026` (`ATT-3980`), and `REQ-TRK-030` (`ATT-3986`). Targets `WorkoutSummariesDatabaseManager.java`, `LiveWorkoutSession.java`, `TrackerService.java`, `LiveWorkoutSessionTest.kt`, and `TrackerServiceResumeTest.kt`.
2. *Historical Origin & Commit Trace*: `LiveWorkoutSession` was wired only in `TrackerService.onStartCommand()` `case START_NORMAL:`, leaving `mLiveSession == null` on `RESUME_BY_USER` and `RESUME_SERVICE_RECREATION`.
3. *Root Reason for Existing Formulation*: Resume pathways originally restored only scalar summary fields (`mTimeActive_s`, `mDistanceTotal_m`, `mAscend_m`, `mDescend_m`, `mLapNr`) before `LiveWorkoutSession` became the authority for 20-second streams, running extrema, and post-workout finalization.
4. *Preservation of Core Invariants*: Preserves the 7-column `RESUME_WORKOUT_PROJECTION` query in `recreateValuesWhenResuming()`, Rule 20 DTO backward compatibility (`ExtremaRecord` 5-arg constructor), and `REQ-TRK-030` fault-isolated finalization.

**Acceptance Criteria (Given-When-Then)**:
• *Given* a workout with pre-resume 20s stream points and sensor extrema,
• *When* the workout is resumed (`RESUME_BY_USER` or `RESUME_SERVICE_RECREATION`), records post-resume samples, and ends via `endWorkout()`,
• *Then* `mLiveSession` is instantiated and rehydrated with pre-resume streams and `RunningStats` (honoring persisted `COUNT` with fallback to `TIME_ACTIVE_s`), post-resume 20s stream increments delta-encode relative to the last pre-resume point without jump, post-resume samples update `MIN`/`MAX`/`AVG` and `COUNT` without overwriting `START`, and `finalizeLiveSession()` persists the complete map polyline, streams, and derived metrics.

**Invariants**: `RESUME_WORKOUT_PROJECTION` (`REQ-TRK-016`), Rule 20 DTO symmetry, `REQ-TRK-030` fault-isolated finalization, and 100% clean-room test pass rate MUST NOT be broken.

### REQ-TRK-032: Thread-Safe Sensor, Device, Filter, Service & Live Session Concurrency Architecture [Verified]
- **Tests**: `DeviceManager.java`, `MySensor.java`, `ProxySensor.java`, `MyAccumulatorSensor.java`, `MySensorManager.java`, `MyDevice.java`, `MyRemoteDevice.java`, `MyBTLEDevice.java`, `MyANTDevice.java`, `FilterManager.java`, `BANALService.java`, `TrackerService.java`, `LiveWorkoutSession.java`, `SensorAndDeviceConcurrencyStressTest.kt`
- **Implementation**: Make sensor, device, filter, service, and live session layers thread-safe across BLE GATT, ANT+, Timer, 1 Hz Scheduler, DB Executor, and Main threads.
- **Rationale**: `DeviceManager`, `MySensor`, `ProxySensor`, `MyAccumulatorSensor`, `MySensorManager`, `MyDevice`, `MyRemoteDevice`, `MyBTLEDevice`, `MyANTDevice`, `FilterManager`, `BANALService`, `TrackerService`, and `LiveWorkoutSession` SHALL guarantee thread-safe concurrent access across Main/UI, BLE GATT binder, ANT+ PCC, 1 Hz `ClockDevice` Timer, 1 Hz `TrackerService` scheduler, and DB executor threads without `ConcurrentModificationException`, post-shutdown `NullPointerException`, duplicate device registration, or lock-ordering deadlocks (ATT-3988, complementing `REQ-PRO-006`, `REQ-TRK-027`, `REQ-TRK-028`, `REQ-TRK-030`, and `REQ-TRK-031`):
1. *Thread-Safe `DeviceManager` Collections, Atomic Registration & Non-Null Teardown (`REQ-TRK-032.1`)*:
• `mMyRemoteDevices` and `mMyRemoteDeviceTries` SHALL be `final` `ConcurrentHashMap` instances (`mMyRemoteDeviceTries.clear()` instead of `new HashMap<>()`); `mSearchQueue` SHALL be a `final` `ConcurrentLinkedQueue`; `mNewlyFoundDevices` SHALL be a `final` `CopyOnWriteArrayList` and `getIdsOfNewlyFoundDevices()` SHALL return `new ArrayList<>(mNewlyFoundDevices)`.
• `createNewANTDevice` and `createNewBluetoothLEDevice` SHALL use atomic `mMyRemoteDevices.putIfAbsent(deviceID, device)` (shutting down any race-lost duplicate without starting or enqueuing it twice).
• `cMyRemoteDeviceCurrentlySearchingFor`, `mAntAsyncSearchEngine`, and `mBTSearchForNewDevicesEngine` SHALL be `volatile` with local variable snapshots at all read/dereference sites.
• `DeviceManager.shutDown()` SHALL call `.clear()` on `mMyRemoteDevices`, `mSearchQueue`, `mMyRemoteDeviceTries`, and `mNewlyFoundDevices` and SHALL NEVER assign `null` to collection fields.
2. *Thread-Safe `MySensor`, `ProxySensor`, `MyAccumulatorSensor` & Lock-Free `MySensorManager` Listener Dispatch (`REQ-TRK-032.2`)*:
• `MySensor.mSensorListeners` SHALL be a `final CopyOnWriteArrayList<SensorListener>()` (`addIfAbsent` in `addSensorListener`); `mValue`, `mActivated`, and `mLastUpdateTimestamp` SHALL be `volatile`; `newValue(T value)` SHALL pass the local `value` snapshot to `sensorListener.newValue(value)` without holding any lock.
• `ProxySensor.setSourceSensor` SHALL synchronize source sensor and listener migration cleanly while `getValue()` and `newValue()` remain lock-free on `volatile` `mSourceSensor`.
• `MyAccumulatorSensor.mInitialValue` and `mLastAbsoluteValue` SHALL be `volatile` and `accumulate()`, `increment()`, `reset()`, and `setInitialValue()` SHALL compute updated values atomically before calling `super.newValue(...)` outside the lock.
• `MySensorManager.mInitialValues` SHALL be a `ConcurrentHashMap`, and `registerSensor()` / `unregisterSensor()` SHALL compute updated GC strings inside `synchronized (this)` and invoke `mSensorsSensor.newValue(...)` / `mAccumulatedSensorsSensor.newValue(...)` **outside** the `synchronized` block.
3. *Device State Visibility & `FilterManager` Concurrency (`REQ-TRK-032.3`)*:
• `MyBTLEDevice.mState`, `MyBTLEDevice.mBatteryCharacteristic`, `MyANTDevice.mDeviceState`, `MyRemoteDevice.mSearching`, `MyRemoteDevice.mCalibrationFactor`, `MyDevice.mSensorsRegistered`, and `MyDevice.mBatteryPercentage` SHALL be `volatile`.
• `FilterManager.mRequiredButNotAvailableFilters`, `myFilterMap`, and `mMapFilter2Sensor` SHALL use `ConcurrentHashMap` / `CopyOnWriteArrayList`.
4. *Null-Safe & Volatile `BANALService` Static and Binder Accessors (`REQ-TRK-032.4`)*:
• `BANALService.cDeviceManager`, `cSensorManager`, and `cFilterManager` SHALL be `volatile`, and all static and `BANALServiceComm` binder methods SHALL capture local references and guard against `null`, returning safe empty defaults when accessed before `onCreate()` or after `onDestroy()`.
5. *Thread-Safe `TrackerService` Fields & Synchronized `LiveWorkoutSession` (`REQ-TRK-032.5`)*:
• `TrackerService.mSearching`, `mTrackerHandle`, `mTrackingInterrupted`, `mLiveSession`, `mBaseFileName`, `mTimeTotal_s`, `mTimeActive_s`, and `mDistanceTotal_m` SHALL be `volatile` with local reference snapshots.
• `LiveWorkoutSession` SHALL synchronize all mutators and accessors and return defensive copies from `getRawLatLngs()`, `getSampledLatLngs()`, `getSampledAltitudes()`, `getSampledDistances()`, and `getSensorStats()`.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement `REQ-TRK-032`, complementing `REQ-PRO-006`, `REQ-TRK-027` (`ATT-3981`), `REQ-TRK-028` (`ATT-3984`), `REQ-TRK-030` (`ATT-3986`), and `REQ-TRK-031` (`ATT-3987`).
2. *Historical Origin & Commit Trace*: `ATT-3986` synchronized `MySensorManager` maps and noted in `ATT-3988` that `registerSensor`/`unregisterSensor` still held the `MySensorManager` lock during `newValue()` callbacks; `ATT-3921`/`ATT-3922` established `DeviceManager.shutDown()` and `MyBTLEDevice` teardown contracts.
3. *Root Reason for Existing Formulation*: `DeviceManager`, `MySensor`, `FilterManager`, `BANALService`, and `LiveWorkoutSession` were originally authored assuming single-threaded main-looper execution prior to multi-threaded BLE GATT, ANT+ PCC, `ClockDevice` Timer, and `TrackerService` `ScheduledExecutorService` / `mDbExecutor` concurrency.
4. *Preservation of Core Invariants*: Avoids holding coarse locks across `SensorListener.newValue()` (preventing lock-ordering deadlocks), replaces `null` collection teardown with `.clear()` (preserving `DeviceManagerTeardownTest` while preventing post-shutdown NPEs), and maintains 100% compatibility with all existing tests.

**Acceptance Criteria (Given-When-Then)**:
• *Given* concurrent device discovery, sensor registration, 1 Hz sampling, and `DeviceManager.shutDown()`,
• *When* multiple threads mutate and iterate `DeviceManager`, `MySensor`, `ProxySensor`, `MySensorManager`, `FilterManager`, `BANALService`, and `LiveWorkoutSession`,
• *Then* zero `ConcurrentModificationException` or `NullPointerException` errors occur, `!Thread.holdsLock(mySensorManager)` holds during `mSensorsSensor.newValue()`, `getIdsOfNewlyFoundDevices()` and `LiveWorkoutSession` getters return defensive copies, and `putIfAbsent` prevents duplicate remote devices.

**Invariants**: Zero coarse locks held across `SensorListener.newValue()`, existing `DeviceManagerTeardownTest` and `MyBTLEDeviceLifecycleTest` contracts, and 100% clean-room test pass rate MUST NOT be broken.

### REQ-TRK-033: Non-Blocking `TrackerService.onDestroy()` Finalization on `mDbExecutor`, Deterministic `mScheduler` Thread Teardown, and `TrainingApplication` State Recovery on Async Init Failure [Verified]
- **Tests**: `TrackerService.java`, `TrainingApplication.java`, `TrackerServiceFinalizationTest.kt`, `TrackerServiceResilienceTest.kt`, `TrackerServiceAsyncInitTest.kt`
- **Implementation**: Shut down `mScheduler` in `TrackerService.onDestroy()`, move `endWorkout()` and `mBanalService`/`WakeLock` release to a terminal FIFO task on `mDbExecutor` instead of blocking the main thread with `awaitTermination(5s)`, and reset `TrainingApplication` tracking mode to `READY` on async DB initialization failure.
- **Rationale**: `TrackerService` (`app/src/main/java/com/atrainingtracker/trainingtracker/tracker/TrackerService.java`) and `TrainingApplication` (`app/src/main/java/com/atrainingtracker/trainingtracker/TrainingApplication.java`) SHALL guarantee that stopping a workout in `TrackerService.onDestroy()` never blocks the Android main thread on database operations (`awaitTermination` or `endWorkout()`), deterministically shuts down the 1 Hz `mScheduler` thread pool prior to queuing finalization on `mDbExecutor`, and resets `TrainingApplication`'s tracking state to `TrackingMode.READY` if asynchronous workout initialization fails (ATT-4006, extending `REQ-TRK-015`, `REQ-TRK-027`, `REQ-TRK-028`, `REQ-TRK-030`, and `REQ-TRK-032`):
1. *Deterministic `mScheduler` Shutdown Before `mDbExecutor` Finalization (`REQ-TRK-033.1`)*:
• In `TrackerService.onDestroy()`, after cancelling `mTrackerHandle` (`handle.cancel(true)`), `TrackerService` SHALL invoke `mScheduler.shutdownNow()` so the `ScheduledThreadPoolExecutor` worker thread is terminated and cannot enqueue further 1 Hz ticks onto `mDbExecutor` after `onDestroy()` begins.
• `TrackerService` SHALL expose `@VisibleForTesting boolean isSchedulerShutdownForTesting()` and `@VisibleForTesting boolean isDbExecutorShutdownForTesting()`.
2. *Non-Blocking Main-Thread `onDestroy()` & FIFO-Ordered `endWorkout()` on `mDbExecutor` (`REQ-TRK-033.2`)*:
• `TrackerService.onDestroy()` SHALL execute `mAutoPauseManager.release()`, `safeUnregisterReceiver(...)`, `unbindService(mBanalConnection)`, and `stopForeground(true)` on the main thread, and SHALL NOT call `mDbExecutor.awaitTermination(...)` or `endWorkout()` on the main thread.
• `TrackerService.onDestroy()` SHALL create `CompletableFuture<Void> finalizationFuture = new CompletableFuture<>()` (stored in `volatile` `mFinalizationFuture`, exposed via `@VisibleForTesting public CompletableFuture<Void> getFinalizationFuture()`), enqueue a terminal `finalizationTask` onto the single-threaded FIFO `mDbExecutor` (`mDbExecutor.execute(finalizationTask)`), and immediately call `mDbExecutor.shutdown()`.
• Inside `finalizationTask` on `mDbExecutor`: `try { if (!interrupted) endWorkout(); } catch (Throwable t) { Log.e(TAG, ...); } finally { mBanalService = null; releaseWakeLock(); finalizationFuture.complete(null); }`, guaranteeing all prior queued sample writes complete before `endWorkout()` runs and `mBanalService` / `mWakeLock` remain valid throughout `endWorkout()`.
• If `mDbExecutor.execute(finalizationTask)` throws `RejectedExecutionException`, `onDestroy()` SHALL run `finalizationTask.run()` inline in the `catch` fallback.
3. *`TrainingApplication` Tracking State Recovery on Asynchronous `START_NORMAL` Initialization Failure (`REQ-TRK-033.3`)*:
• `TrainingApplication.cTrackingMode` SHALL be declared `volatile`.
• `TrainingApplication` SHALL expose `public void onTrackingAborted()` and `public static void resetTrackingStateOnAbort()` resetting `cTrackingMode = TrackingMode.READY`, `cManualPauseLocked = false`, and `cAutoPaused = false`, plus `sendBroadcast(new Intent(TRACKING_STATE_CHANGED).setPackage(getPackageName()))`.
• `TrackerService` SHALL define `TRACKING_ABORTED_INTENT = "com.atrainingtracker.trainingtracker.tracker.TrackerService.TRACKING_ABORTED_INTENT"` and `handleTrackingAborted()` (dispatched via `runOnMainThread(this::handleTrackingAborted)` inside the `START_NORMAL` `mDbExecutor.submit(...)` `catch (Exception e)` block before `stopSelf()`), resetting `TrainingApplication` state and broadcasting `TRACKING_ABORTED_INTENT`.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement `REQ-TRK-033`, extending `REQ-TRK-015` (`ATT-2641`), `REQ-TRK-027` (`ATT-3981`), `REQ-TRK-028` (`ATT-3984`), `REQ-TRK-030` (`ATT-3986`), and `REQ-TRK-032` (`ATT-3988`). Targets `TrackerService.java`, `TrainingApplication.java`, `TrackerServiceFinalizationTest.kt`, `TrackerServiceResilienceTest.kt`, and `TrackerServiceAsyncInitTest.kt`.
2. *Historical Origin & Commit Trace*: `ATT-2641` (`d497f85b`) introduced `mDbExecutor.awaitTermination(5, TimeUnit.SECONDS)` in `onDestroy()` and `stopSelf()` on async DB init failure; `ATT-3986` (`REQ-TRK-030`) expanded `endWorkout()` and `finalizeLiveSession()`.
3. *Root Reason for Existing Formulation*: `mDbExecutor.awaitTermination(5, TimeUnit.SECONDS)` was originally added to ensure pending 1 Hz sample writes finished before `endWorkout()` ran on the main thread.
4. *Preservation of Core Invariants*: Queuing `finalizationTask` onto the single-threaded FIFO `mDbExecutor` before `mDbExecutor.shutdown()` strictly preserves the `REQ-TRK-015` invariant that all prior sample writes complete before `endWorkout()` runs, while moving both the queue drain and `endWorkout()` off the main thread (`< 5 ms` `onDestroy()`), keeping `unbindService(mBanalConnection)` on the main thread, and keeping `mBanalService` and `mWakeLock` alive until `endWorkout()` finishes.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an active `TrackerService` instance,
• *When* `onDestroy()` is invoked,
• *Then* `mScheduler.isShutdown()` is `true`, `mDbExecutor.isShutdown()` is `true`, and `onDestroy()` returns in `< 100 ms` on the caller thread even while a slow task (`300 ms`) is running on `mDbExecutor`, with all prior queued tasks executing in strict FIFO order before `endWorkout()` runs on `mDbExecutor`.
• *Given* `TrainingApplication.getTrackingMode() == TrackingMode.TRACKING` and `START_NORMAL` database initialization throws an exception,
• *When* the `mDbExecutor` initialization task fails,
• *Then* `TrainingApplication.getTrackingMode()` resets to `TrackingMode.READY`, `TRACKING_ABORTED_INTENT` is broadcast, and `mTrackingInterrupted` is `true`.

**Invariants**: `REQ-TRK-015` FIFO sample write completion before `endWorkout()`, `REQ-TRK-030` fault-isolated `endWorkout()` / `finalizeLiveSession()`, main-thread `unbindService(mBanalConnection)`, and 100% clean-room test pass rate MUST NOT be broken.

### REQ-SEN-020: Streamlined Sensor Onboarding, Empty-State Action & Discovery Auto-Pairing [Verified]
- **Tests**: `ControlTrackingScreen.kt`, `TrackingTabsScreen.kt`, `DeviceListScreen.kt`, `DevicesTabbedScreen.kt`, `DeviceManager.java`, `ANTSearchForNewDevicesEngineMultiDeviceSearch.java`, `values*/strings.xml`
- **Implementation**: Provide "Pair Sensor" onboarding button in start cockpit, actionable empty state in My Sensors, auto-pair newly discovered sensors by default, and eliminate duplicate '+' prefix across all 9 locales.
- **Rationale**: The system SHALL provide streamlined sensor onboarding, actionable empty-state search controls, and default discovery auto-pairing across `ControlTrackingScreen.kt`, `TrackingTabsScreen.kt`, `DeviceListScreen.kt`, `DevicesTabbedScreen.kt`, `DeviceManager.java`, and `ANTSearchForNewDevicesEngineMultiDeviceSearch.java` with 9-language localization parity and text-only action strings without duplicate '+' glyphs (ATT-4051, refined by ATT-4288, extending `REQ-UI-301` / `REQ-UI-393`):
1. *Start Screen Onboarding Entry Point (`REQ-SEN-020.1`)*:
• In `ControlTrackingScreen.kt`, `ControlTrackingScreen` SHALL accept parameter `onPairSensor: () -> Unit = {}`.
• When `devices.isEmpty()`, `!showResearchButton` (`hasPairedRemoteDevices == false`), and `searchingFor == null`, the top sensor slot SHALL render a compact, prominent `OutlinedButton` displaying icon `Icons.Default.Add` and text `stringResource(R.string.onboarding_pair_sensor_action)` ("Pair Sensor" / "Sensor koppeln" without duplicate '+' prefix).
• Tapping this button SHALL invoke `onPairSensor()`.
• In `TrackingTabsScreen.kt`, `onPairSensor` SHALL execute `(activity as? MainActivityWithNavigation)?.navigateToDrawerItem(R.id.drawer_my_sensors)`.
• When at least one remote sensor is paired (`hasPairedRemoteDevices == true`) or `devices.isNotEmpty()`, the onboarding button SHALL disappear, restoring the standard `RemoteDevices` / `ResearchButton` layout.
• The *"My Stuff"* section in `AppNavigationDrawer.kt` SHALL remain `collapsible = true` and collapsed by default.
2. *Actionable Empty State in "My Sensors" & Comprehensive Scan Lifecycle Guard (`REQ-SEN-020.2`)*:
• In `DeviceListScreen.kt`, `DeviceListScreen` SHALL accept parameter `onScanForSensors: (() -> Unit)? = null`.
• When `devices.isEmpty()`, `!isSearching`, and `filterCriteria.isEmpty`:
• `EmptyStatePlaceholder`'s `hint` parameter SHALL display `stringResource(R.string.devices_empty_state_search_hint)`.
• `EmptyStatePlaceholder`'s `content` slot SHALL render an `OutlinedButton` displaying icon `Icons.Default.Add` and text `stringResource(R.string.devices_action_search_sensors)` ("Search for Sensors" / "Sensoren suchen" without duplicate '+' prefix) invoking `onScanForSensors`.
• In `DevicesTabbedScreen.kt`, `onScanForSensors` SHALL be wired to `{ showProtocolSheet = true }` across Available, Paired, and All Known tabs.
• *Explicit Scan Lifecycle Trigger & Timeout Guard*: The transition of `isSearching` back to `false` SHALL be triggered by: (a) `onSearchStopped()` callbacks from `ANTSearchForNewDevicesEngineMultiDeviceSearch` and `BTSearchForNewDevicesEngine` upon timeout/finish, (b) manual stop via `tabViewModel.stopSearching()`, and (c) `DisposableEffect(Unit)` navigation teardown invoking `stopSearching()`, immediately restoring the actionable empty state when no devices are found without ghost buttons.
3. *Safe Auto-Pairing for Explicitly Scanned Sensors, UI State Flow & Immediate Unpair Safety (`REQ-SEN-020.3`)*:
• Auto-pairing is strictly bounded to explicit, user-initiated search sessions (triggered via `PairingProtocolBottomSheet` or FAB); the app does not run background discovery sniffing.
• In `DeviceManager.java` (`mBTSearchInterface.onNewDeviceFound`): when an explicitly scanned Bluetooth LE sensor is not in the database (`!mDevicesDatabaseManager.isBluetoothDeviceInDB`), it SHALL be inserted with `paired = true` (`insertNewBluetoothDeviceIntoDB(deviceType, BluetoothMACAddress, name, manufacturer, batteryPercentage, true)`).
• In `DeviceManager.java` (`mANTAsyncSearchEngineInterface.onNewDeviceFound`): when an explicitly scanned ANT+ sensor is not in the database (`!mDevicesDatabaseManager.isANTDeviceInDB`), it SHALL be inserted with `paired = true` (`insertNewAntDeviceIntoDB(deviceType, deviceFound.getDeviceDisplayName(), deviceFound.getAntDeviceNumber(), true, manufacturer, batteryPercentage)`).
• In `ANTSearchForNewDevicesEngineMultiDeviceSearch.java`: `pairingRecommendation` for newly discovered sensors SHALL default to `true`.
• In `DeviceManager.java`, database insertion and state notification SHALL be synchronized on `mDevicesDatabaseManager`. When `deviceId > 0` is confirmed, `pairingChanged(deviceId, true)` SHALL be called. The `DeviceDataRepository.devicesFlow` / `DevicesTabbedViewModel` observing database changes SHALL emit the updated device list with `isPaired == true` to the Compose UI layer atomically.
• *User Reversal & Safety Invariant*: Every auto-paired device SHALL display its discovery notification via the existing discovery snackbar (`sendNewDeviceFoundBroadcast`) and SHALL present an immediate unpair toggle switch (`DeviceItem.kt` `onPairClick`) and delete action (`onLongClick`). If a user toggles pairing to off, `pairingChanged(deviceId, false)` is invoked immediately and `paired = 0` is persisted in SQLite.
• *Workout Session Safety*: `pairingChanged(deviceId, true)` initializes remote device instances safely without corrupting or restarting active workout recordings; telemetry validation in `FilteredSensorData` ensures zero NaN or spike emissions.
4. *9-Language Localization Parity & Text-Only Action Strings (`REQ-SEN-020.4`)*:
• All 3 strings SHALL exist across all 9 supported application locales (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`): `onboarding_pair_sensor_action`, `devices_empty_state_search_hint`, `devices_action_search_sensors`.
• Under Rule 49 and Design Guidelines Section 5.5, `onboarding_pair_sensor_action` and `devices_action_search_sensors` SHALL NOT include leading `'+'` or other ASCII symbol prefixes in any locale, keeping action iconography strictly in Compose `Icon` slots (`Icons.Default.Add`) and XML strings purely textual to eliminate duplicate `+ +` glyphs (ATT-4288).

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement `REQ-SEN-020` (*Streamlined Sensor Onboarding, Empty-State Action & Discovery Auto-Pairing*), extending `REQ-UI-301` / `REQ-UI-393`, refined by `ATT-4288`. Targets `ControlTrackingScreen.kt`, `TrackingTabsScreen.kt`, `DeviceListScreen.kt`, `DevicesTabbedScreen.kt`, `DeviceManager.java`, `ANTSearchForNewDevicesEngineMultiDeviceSearch.java`, `app/src/main/res/values*/strings.xml`, and `SensorOnboardingLocalizationTest.kt`.
2. *Historical Origin & Commit Trace*: Ticket `ATT-4051` (commit `1835aaac`, Sprint `2026-42.10`), refined by bug ticket `ATT-4288` (Sprint `2026-43.1`).
3. *Root Reason for Existing Formulation*: `ATT-4051` introduced onboarding and search buttons with `Icons.Default.Add` while inadvertently including a literal `"+ "` character in `onboarding_pair_sensor_action` and `devices_action_search_sensors` across all 9 locales, producing visible duplicate `+ +` glyphs.
4. *Preservation of Core Invariants*: Removing `"+ "` eliminates duplicate glyphs while preserving 100% of user orientation, translations across all 9 locales, navigation to *"My Sensors"*, protocol bottom sheets, auto-pairing, manual unpairing, and test suite integrity.

**Acceptance Criteria (Given-When-Then)**:
• *Given* a fresh install or state with zero paired sensors (`hasPairedRemoteDevices == false`),
• *When* viewing `ControlTrackingScreen`,
• *Then* the "Pair Sensor" button is rendered in the top sensor slot with `Icons.Default.Add` without a duplicate '+' prefix, and tapping it navigates to *"My Sensors"* (`R.id.drawer_my_sensors`).
• *Given* at least one sensor is paired (`hasPairedRemoteDevices == true`),
• *When* viewing `ControlTrackingScreen`,
• *Then* the "Pair Sensor" button is not rendered, and the standard `RemoteDevices` row is displayed.
• *Given* no sensors are listed in `DeviceListScreen` and no scan is running (`!isSearching`),
• *When* the empty state is displayed,
• *Then* the hint and "Search for Sensors" button are rendered with `Icons.Default.Add` without a duplicate '+' prefix, and tapping the button opens `PairingProtocolBottomSheet`.
• *Given* a scan is running with no devices found yet,
• *When* the scan completes via timeout, manual stop, or screen navigation disposal (`onSearchStopped()` / `onDispose`),
• *Then* `isSearching` transitions to `false` and the empty-state action button is immediately restored.
• *Given* an active scan discovering a new Bluetooth LE or ANT+ device,
• *When* the device is inserted into `DevicesDatabaseManager`,
• *Then* `paired` is stored as `1` (`true`), `pairingChanged(deviceId, true)` is called, the reactive Flow emits to the UI, and the device is automatically ready.
• *Given* an auto-paired device in `DeviceListScreen`,
• *When* the user toggles the pair switch to OFF,
• *Then* `pairingChanged(deviceId, false)` is dispatched and `paired` is persisted as `0`.
• *Given* `onboarding_pair_sensor_action` and `devices_action_search_sensors` across all 9 locales,
• *When* audited by `SensorOnboardingLocalizationTest`,
• *Then* none contain leading '+' characters.

**Invariants**: "My Stuff" drawer group remains collapsed by default; manual unpairing via `DeviceItem.kt` intact; 100% clean-room test pass rate MUST NOT be broken.

### REQ-TRK-034: Foreground-Protected Asynchronous Workout Finalization and Stop Lifecycle Isolation [Verified]
- **Tests**: `TrackerService.java`, `TrainingApplication.java`, `TrackerServiceFinalizationTest.kt`, `TrackerServiceResilienceTest.kt`
- **Implementation**: Protect async workout finalization with foreground service lifecycle (ACTION_STOP), keep BANAL bound throughout endWorkout(), and consolidate duplicate tracking state reset.
- **Rationale**: `TrackerService` (`app/src/main/java/com/atrainingtracker/trainingtracker/tracker/TrackerService.java`) and `TrainingApplication` (`app/src/main/java/com/atrainingtracker/trainingtracker/TrainingApplication.java`) SHALL guarantee that asynchronous workout finalization on `mDbExecutor` is protected within the Android Foreground Service lifecycle, keeping process priority elevated and `BANALService` bound until `endWorkout()` completes (ATT-4098, extending `REQ-TRK-015`, `REQ-TRK-030`, and `REQ-TRK-033`):
1. *Graceful Stop Command Dispatch via `ACTION_STOP` (`REQ-TRK-034.1`)*:
• `TrackerService` SHALL define `public static final String ACTION_STOP = "com.atrainingtracker.trainingtracker.tracker.TrackerService.ACTION_STOP";` and expose `public static boolean isRunning()` (returning `sInstance != null`).
• In `TrainingApplication.stopTracking()`, instead of issuing `stopService(new Intent(this, TrackerService.class))`, `TrainingApplication` SHALL issue `if (TrackerService.isRunning()) { Intent stopIntent = new Intent(this, TrackerService.class); stopIntent.setAction(TrackerService.ACTION_STOP); startService(stopIntent); }`.
• `TrainingApplication.stopTracking()` SHALL immediately reset manual/auto pause locks (`setManualPauseLock(false, this)`, `setAutoPaused(false, this)`), stop the Pebble watchapp, reset `cTrackingMode = TrackingMode.READY`, `sActiveWorkoutId = -1`, and broadcast `TRACKING_STATE_CHANGED`.
2. *Foreground Priority Protection During Asynchronous Finalization (`REQ-TRK-034.2`)*:
• In `TrackerService.onStartCommand(@Nullable Intent intent, int flags, int startId)`: when `intent != null && ACTION_STOP.equals(intent.getAction())`:
• If `TrackerService` is already stopping (`mIsStopping == true`) or not actively tracking (`mWorkoutID <= 0` and `mBaseFileName == null`), it SHALL immediately return `START_NOT_STICKY` (calling `performStopSelf()` if idle).
• Otherwise, `TrackerService` SHALL set `volatile boolean mIsStopping = true;`, immediately cancel `mTrackerHandle` and invoke `mScheduler.shutdownNow()` so no further 1 Hz ticks are sampled or submitted to `mDbExecutor`.
• `TrackerService` SHALL unregister transient receivers (`mAltitudeCorrectionReceiver`, `mSearchingFinishedReceiver`, `mUserSelectedSportTypeChangedReceiver`, `mLapSummaryReceiver`) and release `mAutoPauseManager`.
• `TrackerService` SHALL **retain** its Foreground Service status (`stopForeground` is NOT called yet) and maintain its active foreground notification, guaranteeing `PROCESS_STATE_FOREGROUND_SERVICE` priority throughout database finalization.
• `TrackerService` SHALL enqueue the terminal `finalizationTask` onto `mDbExecutor`.
3. *Deferred Service Teardown and BANAL Unbinding on Completion (`REQ-TRK-034.3`)*:
• Inside `finalizationTask` on `mDbExecutor`: `try { if (!mTrackingInterrupted) { endWorkout(); } } catch (Throwable t) { Log.e(TAG, ...); } finally { ... }`. Throughout `endWorkout()`, `mBanalConnection` SHALL remain actively bound and `mBanalService` available to query active devices, lap numbers, and sensor accumulations.
• Inside the `finally` block of `finalizationTask`: `mBanalService = null; releaseWakeLock(); mFinalizationFuture.complete(null);` and dispatch to the Android main thread via `runOnMainThread(...)`: `unbindService(mBanalConnection); performStopForeground(); performStopSelf();`.
4. *Safety Net in `onDestroy()` and Async-Init Abort Consolidation (`REQ-TRK-034.4`)*:
• If `TrackerService.onDestroy()` is invoked unexpectedly before `ACTION_STOP` finalization runs: if `mFinalizationFuture != null`, `onDestroy()` SHALL wait for `mFinalizationFuture` up to a bounded timeout (5 seconds); if `mFinalizationFuture == null`, `onDestroy()` SHALL execute the fallback finalization as specified in `REQ-TRK-033`.
• In `TrackerService.java:624-638` (the `mDbExecutor.submit` `catch` block on `START_NORMAL` initialization failure): the duplicate call to `TrainingApplication.resetTrackingStateOnAbort()` SHALL be removed, relying strictly on `runOnMainThread(this::handleTrackingAborted)` which resets `TrainingApplication` tracking state and broadcasts `TRACKING_ABORTED_INTENT` on the main thread.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement `REQ-TRK-034`, extending `REQ-TRK-015` (`ATT-2641`), `REQ-TRK-030` (`ATT-3986`), and `REQ-TRK-033` (`ATT-4006`). Targets `TrackerService.java`, `TrainingApplication.java`, `TrackerServiceFinalizationTest.kt`, `TrackerServiceResilienceTest.kt`.
2. *Historical Origin & Commit Trace*: `ATT-4006` (`814919cd`) moved `endWorkout()` to `mDbExecutor` from `onDestroy()`. Review Finding 2 identified that calling `stopForeground()` before `mDbExecutor` finishes demotes the process, risking LMK kill.
3. *Root Reason for Existing Formulation*: `stopForeground()` was previously placed in `onDestroy()` because `stopService()` was the sole stop trigger.
4. *Preservation of Core Invariants*: Retains non-blocking UI, strict FIFO ordering on `mDbExecutor`, dedicated `FINISHED = 1` transaction, and `TRACKING_FINISHED_INTENT` broadcast.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an active workout tracking session in `TrackerService`,
• *When* `ACTION_STOP` is received by `onStartCommand()`,
• *Then* `stopForeground()` and `stopSelf()` are NOT invoked while `endWorkout()` runs on `mDbExecutor`, and are only called on the main thread after `finalizationTask` finishes.
• *Given* an active workout with bound `mBanalConnection`,
• *When* workout finalization runs via `ACTION_STOP`,
• *Then* `mBanalService` remains non-null and bound throughout `endWorkout()` execution, and `unbindService(mBanalConnection)` is invoked only after `endWorkout()` finishes.
• *Given* a `TrackerService` currently executing `ACTION_STOP` finalization,
• *When* a duplicate `ACTION_STOP` is delivered,
• *Then* the duplicate intent is safely ignored without re-triggering finalization or crashing.
• *Given* `TrackerService` fails during async `START_NORMAL` database creation,
• *When* the exception is caught,
• *Then* `TrainingApplication.resetTrackingStateOnAbort()` is invoked once on the main thread via `handleTrackingAborted()`, restoring state to `TrackingMode.READY`.

**Invariants**: Rule 29 (`FINISHED = 1` committed first), `WakeLock` held until finalization completes, zero main-thread database blocking, and 100% clean-room test pass rate MUST NOT be broken.

### REQ-SEN-021: Lock-Free Initial newValue Dispatch & Decoupled ProxySensor Monitor Lifecycle [Verified]
- **Tests**: `ProxySensor.java`, `MySensorManager.java`, `SensorAndDeviceConcurrencyStressTest.kt`
- **Implementation**: Eliminate potential MySensorManager and ProxySensor lock-order inversion by dispatching initial newValue callbacks outside locks.
- **Rationale**: `ProxySensor` (`app/src/main/java/com/atrainingtracker/banalservice/sensor/ProxySensor.java`) and `MySensorManager` (`app/src/main/java/com/atrainingtracker/banalservice/sensor/MySensorManager.java`) SHALL guarantee that initial `newValue()` callbacks to registered `SensorListener`s are never executed while holding monitor locks on `ProxySensor` or `MySensorManager`, eliminating circular A/B lock-order inversion and thread deadlock risks between sensor registration and UI/filter listener subscription (ATT-4096, extending `REQ-TRK-032`):
1. *Elimination of `synchronized` Monitor on `ProxySensor` Public API (`REQ-SEN-021.1`)*:
• `ProxySensor.addSensorListener(SensorListener sensorListener)` and `ProxySensor.removeSensorListener(SensorListener sensorListener)` SHALL NOT be declared `synchronized` on `ProxySensor.this`.
• `addSensorListener()` SHALL delegate to `super.addSensorListener(sensorListener)` and `source.addSensorListener(sensorListener)` without acquiring any monitor or coarse lock.
• `removeSensorListener()` SHALL delegate to `super.removeSensorListener(sensorListener)` and `source.removeSensorListener(sensorListener)` without acquiring any monitor or coarse lock.
2. *Dedicated State-Transition Synchronization Lock (`REQ-SEN-021.2`)*:
• `ProxySensor` SHALL encapsulate source sensor mutations behind a private lock object `private final Object mSourceLock = new Object();` rather than synchronizing on the public `ProxySensor` instance.
• In `ProxySensor.setSourceSensor(MySensor<T> sourceSensor)`: state transitions and listener migration SHALL acquire `synchronized (mSourceLock)` to prevent concurrent source re-assignments from racing, remove registered listeners from `previousSource`, add registered listeners to `sourceSensor`, update `mSourceSensor = sourceSensor`, snapshot the current source value and listeners, and release `mSourceLock` BEFORE dispatching any initial `newValue()` callbacks.
3. *Decoupled `setBestSensorForProxySensor` Execution in `MySensorManager` (`REQ-SEN-021.3`)*:
• In `MySensorManager.registerSensor(MySensor mySensor)` and `MySensorManager.unregisterSensor(MySensor mySensor)`, `setBestSensorForProxySensor(sensorType)` (or deferred `proxySensor.setSourceSensor(...)` invocations) SHALL be executed strictly **outside** `synchronized (this)`, guaranteeing that `ProxySensor.setSourceSensor` is NEVER called while holding the `MySensorManager` monitor lock.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement `REQ-SEN-021` (*Lock-Free Initial newValue Dispatch & Decoupled ProxySensor Monitor Lifecycle*), extending `REQ-TRK-032.2` (`ATT-3988`). Targets `ProxySensor.java`, `MySensorManager.java`, and `SensorAndDeviceConcurrencyStressTest.kt`.
2. *Historical Origin & Commit Trace*: Ticket `ATT-4096`, Sprint `2026-42.10`, following Sprint `2026-42.9` code review of `ATT-3988` (commit `2a5a6103`).
3. *Root Reason for Existing Formulation*: `ATT-3988` eliminated lock holds for `mSensorsSensor.newValue()` in `registerSensor`/`unregisterSensor`, but left `setBestSensorForProxySensor` inside `synchronized (this)` and left `ProxySensor.addSensorListener()` / `setSourceSensor()` synchronized on `this`. Under concurrent sensor registration on background threads and UI/filter listener queries on main threads, an A/B circular wait was possible.
4. *Preservation of Core Invariants*: Full listener migration on best sensor change, immediate initial value emission upon listener subscription, priority hierarchy in `MySensorManager`, and 100% clean-room test pass rate MUST NOT be broken.

**Acceptance Criteria (Given-When-Then)**:
• *Given* multi-threaded execution where Thread A calls `MySensorManager.registerSensor()` / `unregisterSensor()` and Thread B calls `ProxySensor.addSensorListener()` with a listener whose `newValue()` callback queries `MySensorManager` (`getSensors()`, `getAccumulatedSensorTypeSet()`, or `registerSensor()`),
• *When* executed concurrently across 8 worker threads for 1,000 iterations,
• *Then* zero deadlocks, thread starvations, or timeouts occur, and all threads complete cleanly in under 5 seconds.
• *Given* a `ProxySensor` with registered listeners when a new best sensor is selected in `MySensorManager`,
• *When* `setBestSensorForProxySensor()` updates the source,
• *Then* `ProxySensor.setSourceSensor()` executes without holding `MySensorManager` monitor, listeners receive the initial value from the new source, and subsequent source updates propagate to all listeners.
• *Given* `ProxySensor.addSensorListener(listener)` or `removeSensorListener(listener)`,
• *When* inspected via thread lock reflection,
• *Then* `!Thread.holdsLock(proxySensor)` holds true throughout listener execution.

**Invariants**: Listener update delivery parity, initial value notification, priority hierarchy, and 100% clean-room test pass rate MUST NOT be broken.

### REQ-TRK-036: In-Memory `LiveWorkoutSession` Reuse on User Resume and Safe Publication after Rehydration [Verified]
- **Tests**: mLiveSession.getWorkoutId() != state.workoutId` or `mIsRehydrating == true`), `TrackerService` SHALL set `mIsRehydrating = true` prior to task submission.
• In the rehydration task on `mDbExecutor`: `LiveWorkoutSession newSession = new LiveWorkoutSession(resumedWorkoutId, IMPORTANT_SENSOR_TYPES);`
• `rehydrateLiveSession(newSession, resumedWorkoutId, resumedActiveTimeS)` SHALL populate the candidate session directly.
• The newly rehydrated session SHALL be published to the `volatile mLiveSession` reference (`mLiveSession = newSession;`) strictly **after** database stream and extrema decoding completes.
• In `finally`: `mIsRehydrating = false`, `mTableInitializationFuture.complete(null)`, and `notifyTrackingStarted(resumedWorkoutId)`.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement `REQ-TRK-036`, extending `REQ-TRK-031` (`ATT-3987`). Targets `TrackerService.java`, `LiveWorkoutSession.java`, and `TrackerServiceResumeTest.kt`.
2. *Historical Origin & Commit Trace*: Ticket `ATT-4095`, Sprint `2026-42.10`, following Sprint `2026-42.9` code review of `ATT-3987` (commit `8123bb6f`).
3. *Root Reason for Existing Formulation*: `ATT-3987` added rehydration to recover sessions after process death, but unconditionally recreated `mLiveSession` even on simple user pause/resume, losing 1 Hz pre-pause trackpoints to 20s downsampled DB points and exposing empty sessions during asynchronous rehydration.
4. *Preservation of Core Invariants*: Full stream and extrema rehydration on service recreation/process death, `mTableInitializationFuture` barrier, non-blocking main-thread resume, and 100% clean-room test pass rate strictly preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an active `TrackerService` with `mLiveSession` holding 1 Hz `rawLatLngs` for `workoutId = 100`,
• *When* the user pauses and resumes via `RESUME_BY_USER`,
• *Then* `applyResumedState()` preserves the identical `mLiveSession` instance and its 1 Hz `rawLatLngs` without database rehydration.
• *Given* `TrackerService` starting after process death (`mLiveSession == null`),
• *When* `applyResumedState()` executes,
• *Then* a new `LiveWorkoutSession` is created and rehydrated on `mDbExecutor`, and `mLiveSession` is published only after rehydration completes.

**Invariants**: Rehydration on service recreation, non-blocking main thread, and 100% clean-room test pass rate MUST NOT be broken.
- **Rationale**: `TrackerService` (`app/src/main/java/com/atrainingtracker/trainingtracker/tracker/TrackerService.java`) SHALL reuse the active in-memory `LiveWorkoutSession` when resuming an active session via `RESUME_BY_USER` while the service process remains alive in memory, preserving full 1 Hz GPS trajectory points (`rawLatLngs`), and SHALL construct, rehydrate, and safely publish a new `LiveWorkoutSession` to `volatile mLiveSession` only after database rehydration completes on `mDbExecutor` (ATT-4095, extending `REQ-TRK-031`):
1. *In-Memory Session Reuse on `RESUME_BY_USER` (`REQ-TRK-036.1`)*:
• In `TrackerService.applyResumedState(@NonNull ResumedWorkoutState state)`, when `mLiveSession != null && mLiveSession.getWorkoutId() == state.workoutId && !mIsRehydrating`, `TrackerService` SHALL reuse the existing `mLiveSession` instance and SHALL NOT overwrite it with a new `LiveWorkoutSession` or trigger stream rehydration from SQLite.
• Reusing the in-memory session SHALL preserve all 1 Hz pre-pause trackpoints in `mLiveSession.getRawLatLngs()`, enabling high-fidelity Douglas-Peucker route simplification upon workout stop.
• In this reuse path, `TrackerService` SHALL complete `mTableInitializationFuture` (`complete(null)`) and broadcast `notifyTrackingStarted(mWorkoutID)` immediately.
2. *Atomic Construction, Rehydration & Publication on `mDbExecutor` (`REQ-TRK-036.2`)*:
• When a new session must be created (`mLiveSession == null

### REQ-CON-020: Resilient Bluetooth LE & ANT+ Reconnection, Immutable Telemetry Dispatch, and Queue Integrity Architecture [Verified]
- **Tests**: `MyBTLEDevice.java`, `DeviceManager.java`, `MyANTDevice.java`
- **Implementation**: Resilient Bluetooth LE & ANT+ reconnection, immutable telemetry dispatch, and queue integrity architecture.
- **Rationale**: The system (`MyBTLEDevice`, `DeviceManager`, `MyANTDevice`) SHALL guarantee immutable BLE notification payload dispatch to eliminate main-thread data corruption from Binder buffer reuse, provide deterministic search timeout lifecycle management with exponential backoff on disconnect to prevent GATT 133/257 storms, enforce search queue device matching in `DeviceManager` to prevent alien event preemption, and ensure clean handle release and non-locking recovery on ANT+ `DEAD` state (ATT-4007, extending `REQ-CON-019`):
1. *Immutable BLE Notification Payload Dispatch (`REQ-CON-020.1`)*:
• In `MyBTLEDevice.java`, the `BluetoothGattCallback` SHALL implement the Android 13+ (API 33) signature `onCharacteristicChanged(BluetoothGatt, BluetoothGattCharacteristic, byte[])` alongside the legacy fallback.
• When a notification arrives, the raw payload bytes SHALL immediately be defensively cloned on the Binder callback thread (`(value != null ? value : characteristic.getValue()).clone()`).
• A decoupled snapshot characteristic populated with the cloned byte buffer SHALL be dispatched to `mHandler`, guaranteeing that subsequent incoming BLE packets cannot mutate payload data before or during main-thread processing.
2. *Deterministic Search Timeout Lifecycle & Reconnect Backoff (`REQ-CON-020.2`)*:
• `MyBTLEDevice` SHALL maintain a single, reusable `mSearchTimeoutRunnable`.
• All pending search timeouts SHALL be explicitly cancelled via `mHandler.removeCallbacks(mSearchTimeoutRunnable)` upon: starting a new search (`startSearching()`), disconnecting from GATT (`disconnectFromGatt()`), explicit teardown (`shutDown()`), and successful service discovery (`notifyStopSearching(true)`).
• `MyBTLEDevice` SHALL maintain a volatile `mIsShutDown` guard; disconnections arriving during or after `shutDown()` SHALL be ignored.
• When an active connection drops unexpectedly (`STATE_DISCONNECTED`), `MyBTLEDevice` SHALL NOT reconnect immediately with 0 ms delay. It SHALL schedule `startSearching()` via `mReconnectRunnable` with exponential backoff: base delay 1,000 ms, doubling upon consecutive search failures up to 16,000 ms (`MAX_RECONNECT_DELAY_MS`), and resetting to 1,000 ms upon successful service discovery.
3. *Search Queue Device Integrity in `DeviceManager` (`REQ-CON-020.3`)*:
• In `DeviceManager.mSearchingStoppedForOneReceiver`, advancing the search queue (`searchForNextRemoteDevice()`) and modifying retry counters SHALL strictly execute ONLY if the event's `deviceID` matches the active target `cMyRemoteDeviceCurrentlySearchingFor.getDeviceId()`.
• Before invoking `searchForNextRemoteDevice()`, `cMyRemoteDeviceCurrentlySearchingFor` SHALL be cleared to `null`.
• Any `SEARCHING_STOPPED_FOR_ONE_INTENT` from a device ID not currently matching `cMyRemoteDeviceCurrentlySearchingFor` SHALL be logged and discarded without modifying `mSearchQueue` or popping the next device.
4. *ANT+ Handle Hygiene & DEAD State Recovery (`REQ-CON-020.4`)*:
• In `MyANTDevice.startSearching()`, `releaseAccess()` SHALL be invoked before requesting new access (`requestAccess()`), ensuring any stale `pccReleaseHandle` is cleanly closed.
• In `MyANTDevice.onDeviceStateChangeHandler(DeviceState.DEAD)`, `releaseAccess()` SHALL be invoked, `pccReleaseHandle` SHALL be set to `null`, `notifyStopSearching(false)` SHALL be ensured, and sensors unregistered without leaving dangling channel allocations.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement `REQ-CON-020`, extending `REQ-CON-019` (`ATT-2773`). Targets `MyBTLEDevice.java`, `DeviceManager.java`, `MyANTDevice.java`, and peripheral drivers under `com.atrainingtracker.banalservice.devices`.
2. *Historical Origin & Commit Trace*: Ticket `ATT-4007`, Sprint `2026-42.10`, discovered during whole-app code review of `feature/ATT-3921` (`0234bfc0`). `DeviceManager` and `MyBTLEDevice` were part of initial BLE/ANT+ architecture and lacked concurrency/lifecycle hardening for modern Android 13+ APIs.
3. *Root Reason for Existing Formulation*: Early BLE APIs on Android (API 18–32) passed `BluetoothGattCharacteristic` directly without byte arrays, and single-threaded looper assumptions obscured race conditions. In `DeviceManager`, `searchForNextRemoteDevice()` was placed outside the check under the assumption that `SEARCHING_STOPPED` would only fire for the active device.
4. *Preservation of Core Invariants*: Existing peripheral drivers (`BTLEHeartRateDevice`, `BTLEBikePowerDevice`, `BTLEBikeDevice`, `BTLERunSpeedDevice`), service discovery sequences, and pairing flows must continue to function with 100% fidelity. Clean-room test suite must pass completely.

**Acceptance Criteria (Given-When-Then)**:
• *Given* a `MyBTLEDevice` receiving rapid BLE notifications,
• *When* the Bluetooth callback thread receives consecutive packets where the underlying characteristic byte array is mutated in place,
• *Then* the main-thread handler receives independent immutable byte snapshots matching each exact packet payload without data corruption.
• *Given* a connected BLE sensor that disconnects unexpectedly,
• *When* `onConnectionStateChange` receives `STATE_DISCONNECTED`,
• *Then* it cancels any prior timeout, schedules reconnection with initial 1,000 ms backoff, doubles backoff on successive disconnects up to 16,000 ms, and cancels all pending timeouts when shut down.
• *Given* `DeviceManager` actively searching for Device A with Device B queued in `mSearchQueue`,
• *When* a `SEARCHING_STOPPED_FOR_ONE_INTENT` arrives with `deviceID = C` (alien device),
• *Then* the event is discarded, `cMyRemoteDeviceCurrentlySearchingFor` remains Device A, and Device B is NOT popped from `mSearchQueue`.
• *Given* an active or failing ANT+ device transition to `DeviceState.DEAD`,
• *When* `onDeviceStateChangeHandler(DEAD)` executes,
• *Then* `pccReleaseHandle` is closed and nulled, preventing `DEVICE_ALREADY_IN_USE` errors on subsequent search.

**Invariants**: Zero regression in peripheral drivers; single-thread Looper contract on `mHandler` preserved; 100% clean-room test pass rate.

### REQ-TRK-037: Speed Sensor Standstill vs. In-Motion Signal Loss (Tunnel / Dropout) Discrimination in AutoPauseManager and Static Coupling Decoupling [Verified]
- **Tests**: lastValidSpeedMps <= resumeThresholdMps`: the athlete decelerated to standstill or was already stationary before packet silence (e.g. traffic light stop). `AutoPauseManager` SHALL evaluate effective speed as `0.0 m/s`, advancing `debounceCounter` toward `AUTO_PAUSED` after `pauseDebounceSeconds`.
2. *Dynamic Sensor Unpairing & Disconnection Reset (`REQ-TRK-037.2`)*:
• When a speed sensor is dynamically unpaired, powered off, or disconnected mid-session such that `hasSpeedSource == false`: `AutoPauseManager` SHALL immediately transition to `SENSOR_LOST` (`auditLogEvent = "SENSOR_LOST_STALE_TTL_EXCEEDED"`).
• In addition, `AutoPauseManager` SHALL explicitly reset `lastValidSpeedMps = null` upon `!hasSpeedSource` or when reset via `setState(TRACKING)` / `setState(MANUAL_PAUSED)`, guaranteeing that stale historic speed values cannot linger across sensor unpairing or distinct workout sessions.
3. *Sensor Recovery Invariant (`REQ-TRK-037.3`)*:
• When in `SENSOR_LOST`, the engine SHALL require 2 consecutive fresh valid readings (`hasFreshValidReading == true`) to recover back to `TRACKING` (`auditLogEvent = "SENSOR_RECOVERED"`).
4. *Decoupled Manual Resume Method Signature (`REQ-TRK-037.4`)*:
• `AutoPauseManager.onManualResume(manualPauseLocked: Boolean = false)` SHALL accept `manualPauseLocked: Boolean` with `@JvmOverloads` defaulting to `false`, eliminating static access to `TrainingApplication.isManualPauseLocked()`.
• Callers in `TrackerService.java` (`notifyManualResume()`, `onStartCommand()` for `RESUME_BY_USER`, and `evaluateAutoPauseTick(...)`) SHALL explicitly pass `TrainingApplication.isManualPauseLocked()` to `onManualResume(...)`.
• When `manualPauseLocked == true` or `currentState == AutoPauseState.MANUAL_PAUSED`, `onManualResume` SHALL return immediately without modifying state.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement `REQ-TRK-037`, extending `REQ-TRK-018` (`ATT-2642`) and `REQ-TRK-028` (`ATT-3984`). Targets `AutoPauseManager.kt`, `TrackerService.java`, and `AutoPauseManagerTest.kt`.
2. *Historical Origin & Commit Trace*: Ticket `ATT-4094`, Sprint `2026-42.10`, following Sprint `2026-42.9` code review of `ATT-3984` (commit `69e984f1`).
3. *Root Reason for Existing Formulation*: `ATT-3984` fixed the bug where auto-pause failed to trigger due to zero timestamps in `ProxySensor`, but unconditionally evaluated stale packets as `0.0 m/s`. This worked for standstill red lights, but caused false auto-pauses when cruising athletes entered tunnels or suffered temporary RF dropouts.
4. *Preservation of Core Invariants*: Standstill auto-pause debounce, 2-tick `SENSOR_LOST` recovery hysteresis, manual pause lock precedence, and 100% clean-room test pass rate strictly preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an athlete cruising at 8.0 m/s with `resumeThresholdMps = 1.0 m/s`,
• *When* speed packets stop arriving for > 5000 ms (e.g. entering a tunnel),
• *Then* `AutoPauseManager` transitions to `SENSOR_LOST` with audit event `"SENSOR_LOST_IN_MOTION_DROPOUT"` and does NOT enter `AUTO_PAUSED`.
• *Given* an athlete slowing down to 0.5 m/s ($\le resumeThresholdMps$),
• *When* speed packets stop arriving for > 5000 ms,
• *Then* `AutoPauseManager` evaluates effective speed as `0.0 m/s`, increments debounce counter, and transitions to `AUTO_PAUSED` after debounce expires.
• *Given* an active speed sensor mid-session,
• *When* the sensor is unpaired or powered off (`hasSpeedSource == false`),
• *Then* `AutoPauseManager` transitions to `SENSOR_LOST` and resets `lastValidSpeedMps` to `null`.
• *Given* `AutoPauseManager` in `AUTO_PAUSED` state,
• *When* `onManualResume(manualPauseLocked = true)` is invoked,
• *Then* the state remains `AUTO_PAUSED` without clearing debounce or recovery counters.
• *Given* `AutoPauseManager` in `AUTO_PAUSED` state,
• *When* `onManualResume(manualPauseLocked = false)` is invoked,
• *Then* the state transitions to `TRACKING` and debounce counters are reset.

**Invariants**: Standstill auto-pause debounce, 2-tick recovery hysteresis, thread synchronization, static decoupling, and 100% clean-room test pass rate.
- **Rationale**: `AutoPauseManager` (`app/src/main/java/com/atrainingtracker/trainingtracker/tracker/AutoPauseManager.kt`) and `TrackerService` (`app/src/main/java/com/atrainingtracker/trainingtracker/tracker/TrackerService.java`) SHALL distinguish physical standstill from in-motion sensor loss based on the last recorded valid speed (`lastValidSpeedMps`), transitioning in-motion dropouts to `SENSOR_LOST` without pausing while debouncing decelerated standstill to `AUTO_PAUSED`, resetting `lastValidSpeedMps` on dynamic unpairing, and decoupling manual resume from static application state (ATT-4094, extending `REQ-TRK-018` and `REQ-TRK-028`):
1. *Stale Packet Speed Classification (`REQ-TRK-037.1`)*:
• In `AutoPauseManager.evaluateTick(...)`, when a speed source is registered (`hasSpeedSource == true`) and packet updates become stale (`isPacketStale == true`, delta > `STALE_PACKET_TIMEOUT_MS = 5000L`):
- The system SHALL inspect the last recorded valid speed (`lastValidSpeedMps`).
- If `lastValidSpeedMps != null && lastValidSpeedMps > resumeThresholdMps`: the athlete was in motion at high speed before packets ceased (e.g. tunnel entry, GPS multipath shadow, BLE wheel sensor dropout). `AutoPauseManager` SHALL transition to `AutoPauseState.SENSOR_LOST` (from `TRACKING` or `PAUSE_DEBOUNCE_PENDING`), log `auditLogEvent = "SENSOR_LOST_IN_MOTION_DROPOUT"`, and reset `debounceCounter = 0`. It SHALL NOT evaluate effective speed as `0.0 m/s` and SHALL NOT transition to `AUTO_PAUSED`.
- If `lastValidSpeedMps == null

### REQ-TRK-038: Constructor State Seeding Parity (Rule 51), Telemetry-Gated W'bal Emission (Rule 52), Stationary/Paused W/kg Alignment, and Aftermath Presentation Defense [Verified]
- **Tests**: `TST-TRK-031`
- **Implementation**: `CyclingPowerMetricsDevice.java`, `TelemetryMetricUtils.kt`, `WorkoutDataMapper.kt`
- **Rationale**: The system SHALL maintain internal constructor and session reset capacity parity in `CyclingPowerMetricsDevice` (Rule 51) while strictly gating `W_PRIME_BAL` sensor emissions and pause reconstitution on the receipt of actual numeric power telemetry (Rule 52) to prevent persisting phantom 20.0 kJ samples on power-less bike workouts with offline/paired sensors, and SHALL gate aftermath W'-Bilanz chart rendering in `TelemetryMetricUtils.hasWPrimeBalData()` on `hasPowerData(path)` (ATT-5068, refining `REQ-TRK-038` and `REQ-UI-399.3`):
1. *Constructor State Seeding Parity & Telemetry-Gated W'bal Emission (`REQ-TRK-038.1` — Rule 51 & Rule 52)*:
• `CyclingPowerMetricsDevice` constructors SHALL invoke `resetSession()` (or seed baseline capacity identically) upon instantiation, ensuring internal capacity state parity (Rule 51).
• When `resolveEffectiveCpWatts() > 0 && mWPrimeKjProvider.getWPrimeKj(mContext) > 0.0f`, internal accumulator `mCurrentWPrimeBalJoules` SHALL be initialized to `wPrimeKj * 1000.0`.
• In `resetSession()`, `mWPrimeBalSensor.newValue(null)` SHALL be emitted unconditionally, regardless of whether a paired `SensorType.POWER` is registered, preventing `TrackerService` from recording phantom `W_PRIME_BAL` extrema before active power telemetry arrives (Rule 52).
• In `calculateMetrics()`, `mWPrimeBalSensor.newValue(...)` SHALL be emitted if and only if actual valid numeric power telemetry (`power >= 0.0`) has been received in the active session (`mActivePowerDurationSec > 0`).
• When $CP \le 0$ or $W' \le 0.0\text{f}$, `mCurrentWPrimeBalJoules` SHALL be initialized to `null` and `mWPrimeBalSensor.newValue(null)` emitted.
2. *Stationary & Paused W/kg Alignment (`REQ-TRK-038.2`)*:
• In `calculateMetrics()`, when `isSuspended == true` (Auto-Pause or Manual Pause), the engine SHALL retrieve instantaneous power (falling back to raw sensor data) and athlete weight `athleteWeightKg`.
• When `athleteWeightKg > 0.0f`: if `power != null && power >= 0.0`, the system SHALL publish `mPowerToWeightSensor.newValue(power / athleteWeightKg)`; if `power == null` and a power sensor is present, it SHALL publish `0.0`.
• When `athleteWeightKg <= 0.0f` (unconfigured), `mPowerToWeightSensor.newValue(null)` SHALL be published.
• Continuously updating `mPowerToWeightSensor` during pause prevents `TimedMovingAverageFilter` (3-second window) from starving and purging all samples, ensuring `W/kg (3 sec Ø)` displays `0.0 W/kg` (or `0,0 W/kg`) alongside `0 W` instead of expiring to `-- W/kg`.
3. *Transient Dropout Resilience for W'bal & Cumulative Metrics (`REQ-TRK-038.3`)*:
• In `calculateMetrics()`, when `power == null || Double.isNaN(power) || Double.isInfinite(power) || power < 0.0` during an active non-suspended session, the engine SHALL NOT publish `null` to `mWPrimeBalSensor`, `mNormalizedPowerSensor`, `mIntensityFactorSensor`, or `mTssSensor`, preserving existing session values for `TrackingViewModel` staleness timeout.
4. *Cumulative Metric Pause Invariant & Suspended W'bal Reconstitution Gating (`REQ-TRK-038.4` — Rule 52)*:
• While `isSuspended == true`, Normalized Power (NP), Mechanical Work (kJ), Intensity Factor (IF), and Training Stress Score (TSS) SHALL remain strictly gated and SHALL NOT accumulate, integrate work, or advance FIFO samples.
• $W'_{\text{bal}}$ exponential reconstitution at $P = 0.0\text{ W}$ during pause SHALL step and publish to `mWPrimeBalSensor` if and only if `mCurrentWPrimeBalJoules != null` AND actual numeric power telemetry has been received in the active session (`mActivePowerDurationSec > 0`). If no power samples were received prior to pause, `mWPrimeBalSensor` SHALL remain `null`.
5. *Aftermath Presentation Defense & Chart Power Gating (`REQ-TRK-038.5` — Rule 20, Rule 52 & REQ-UI-399.3)*:
• In `TelemetryMetricUtils.kt`, `hasWPrimeBalData(path)` SHALL evaluate `hasPowerData(path) && path.any { it.wPrimeBalKj != null && it.wPrimeBalKj >= 0.0 }`, suppressing phantom flat-line W'-Bilanz charts in `MapDetailLayout`, `TrackOnMapScreen`, and `WorkoutSummary` on workouts lacking power telemetry.
• In `WorkoutDataMapper.kt`, `resolveWPrimeBalSummary()` SHALL continue to gate `minWPrimeBalKj` and `minWPrimeBalPct` on `hasPowerData`.

### REQ-FIL-014: Backend Filter Key Normalization, Runtime 'Best Sensor' Field Registration & Post-Trim Rolling Filter NaN Guard [Verified]
- **Tests**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/editsensorfield/EditSensorFieldViewModel.kt`, `app/src/main/java/com/atrainingtracker/banalservice/filters/TimedMovingAverageFilter.java`
- **Implementation**: Normalize sentinel device IDs to null for backend filter registration and prevent division by zero in TimedMovingAverageFilter when all window samples expire.
- **Rationale**: The system SHALL normalize numeric sentinel device IDs (`selectedDeviceId <= 0`) to `null` before constructing backend filter keys in `EditSensorFieldViewModel.kt` (`saveChanges()`), ensuring newly created or edited sensor fields configured with "Best Sensor" register active filters in `BANALService` via `MySensorManager.getSensor()` rather than leaking localized display strings (`context.getString(R.string.bestSensor)`) into `FilterData.deviceName`, and SHALL execute `trimValues()` before checking collection emptiness in `TimedMovingAverageFilter.java` (`getFilteredValue()`), returning `null` when all window samples age out and strictly preventing division-by-zero (`0.0 / 0 = Double.NaN`), while preserving explicit device mapping (`selectedDeviceId > 0`), thread safety, and 9-language localization parity without requiring UI alterations or database migrations (ATT-4282, amending `REQ-FIL-011` and `REQ-UI-167`, and enforcing Governance Rule 50):
1. *Sentinel ID Normalization in `EditSensorFieldViewModel.saveChanges()` (`REQ-FIL-014.1`)*:
• In `EditSensorFieldViewModel.kt`, when saving sensor field configuration in `saveChanges()`, the system SHALL evaluate `currentState.selectedDeviceId`.
• If `currentState.selectedDeviceId <= 0` (e.g. `-1L` representing "Best Sensor"): `newSourceDeviceName` SHALL be normalized to `null`, and `newSourceDeviceId` SHALL be normalized to `null`.
• If `currentState.selectedDeviceId > 0`: `newSourceDeviceName` SHALL retain `currentState.selectedDeviceName`, and `newSourceDeviceId` SHALL retain `currentState.selectedDeviceId`.
• `FilterData` passed to `banalServiceRepository.createFilter(filterData)` SHALL receive `newSourceDeviceName` (`null` for "Best Sensor"), ensuring `FilterManager.java` resolves the proxy sensor via `mMySensorManager.getSensor(filterData.sensorType)` and registers the filter under matching hash keys (`Objects.hash(..., null)`).
• `trackingViewsRepository.insertSensorFieldConfig(...)` and `trackingViewsRepository.updateSensorFieldConfig(...)` SHALL receive the normalized `newSourceDeviceId` and `newSourceDeviceName`.
2. *Post-Trim Emptiness Guard & NaN Elimination in `TimedMovingAverageFilter` (`REQ-FIL-014.2`)*:
• In `TimedMovingAverageFilter.java`, `getFilteredValue()` SHALL invoke `trimValues()` prior to checking collection emptiness.
• Immediately following `trimValues()`, the method SHALL evaluate `if (mTimestampedValues.isEmpty()) return null;` before computing sample sums or dividing by collection size.
• When all buffered samples age beyond `1000 * mSeconds` (e.g. athlete stops pedaling or signal silence occurs), `getFilteredValue()` SHALL return `null` instead of computing `0.0 / 0 = Double.NaN`.
• Downstream telemetry layers and formatters SHALL render standard fallback dashes (`"--"`) rather than literal `"NaN"` or arithmetic distortions.
3. *Multi-Locale Parity & Zero UI Alterations (`REQ-FIL-014.3`)*:
• Sentinel normalization SHALL operate strictly on numeric IDs (`selectedDeviceId <= 0`), eliminating any coupling to localized UI strings (`context.getString(R.string.bestSensor)`).
• Existing string resources across all 9 canonical locales (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`) SHALL remain untouched with zero new string resources required.
4. *Preservation of System Invariants (`REQ-FIL-014.4`)*:
• Specific paired device selection (`selectedDeviceId > 0`) continues to register device-bound filters and persist non-null foreign keys to SQLite.
• All filter operations in `TimedMovingAverageFilter` retain `synchronized` thread safety.
• Unaffected filter implementations (`NumberedMovingAverageFilter`, `AverageFilter`, `ExponentialSmoothingFilter`, `MaxValueFilter`, `InstantaneousFilter`) remain strictly untouched.
• Zero SQLite schema migrations or alterations.
• 100% full clean-room unit regression test suite pass rate under `./gradlew testDebugUnitTest`.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement `REQ-FIL-014`, amending `REQ-FIL-011` (*Null-Safe Sensor Filtering*) and `REQ-UI-167` (*Intuitive Smoothing Presets, Simplified Filter Configuration & Smart Athletic Defaults*). Targets `EditSensorFieldViewModel.kt`, `TrackingViewsRepository.kt`, and `TimedMovingAverageFilter.java`.
2. *Historical Origin & Commit Trace*: `REQ-FIL-011` was introduced in baseline architecture and formalized in Sprint 2026-41.10 (`ATT-3222`). `REQ-UI-167` was introduced in Sprint 2026-40.4 (`ATT-1276`, commit `69e4f1a2`) when Compose-based sensor field editing was created. Discovered during on-device testing and Sprint Review 2026-42.10 (`ATT-1296`, commit `889ebd18` / `c8939a3f`) on Google Pixel 10 (attached `w_prime_cockpit.png`), where `W_PRIME_BAL` added at runtime remained stuck displaying `--` and `TimedMovingAverageFilter` evaluated `0.0 / 0 = Double.NaN`. Codified into Document Triad Rule 50 (`docs/coding_standards.md` §1.7).
3. *Root Reason for Existing Formulation*: In `EditSensorFieldViewModel`, `selectedDeviceId` was assigned `-1` as a sentinel for "Best Sensor", while `selectedDeviceName` was populated with `context.getString(R.string.bestSensor)` to supply a display string for the dropdown spinner. When `saveChanges()` was wired to notify `BANALService.createFilter()`, the display string was forwarded without normalizing sentinel IDs to `null`. In `TimedMovingAverageFilter`, `getFilteredValue()` placed the check `if (mTimestampedValues.size() == 0) return null;` before `trimValues()` under the assumption that `trimValues()` would only discard older samples while leaving at least one fresh sample, overlooking the boundary condition where all samples in the window age out simultaneously between evaluations.
4. *Preservation of Core Invariants*: Explicit device selection when `selectedDeviceId > 0` is strictly preserved; "Best Sensor" proxy resolution when `selectedDeviceId <= 0` is restored; mathematical moving average contract returning `null` on empty windows is preserved; 9-locale parity is preserved with zero new strings; 100% clean-room test pass rate is strictly maintained.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an athlete configuring a new sensor field or editing an existing field with "Best Sensor" (`selectedDeviceId = -1`),
• *When* `saveChanges()` is invoked,
• *Then* `banalServiceRepository.createFilter` receives `FilterData(deviceName = null, ...)`, registering the filter with `ProxySensor`, and the cockpit tile streams live data immediately without restarting the app.
• *Given* an athlete configuring a sensor field with a specific paired device (`selectedDeviceId = 123L`, `selectedDeviceName = "Stages Power"`),
• *When* `saveChanges()` is invoked,
• *Then* `banalServiceRepository.createFilter` receives `FilterData(deviceName = "Stages Power", ...)`, binding the filter to the specific device.
• *Given* a `TimedMovingAverageFilter` containing samples where all samples have aged past the time window threshold,
• *When* `getFilteredValue()` is called,
• *Then* `trimValues()` clears the expired samples, and the method returns `null` instead of `Double.NaN`.
• *Given* a `TimedMovingAverageFilter` with fresh incoming samples within the time window,
• *When* `getFilteredValue()` is called,
• *Then* the arithmetic mean of the valid samples is returned accurately.
• *Given* any of the 9 supported application locales,
• *When* saving sensor fields in `EditSensorFieldViewModel`,
• *Then* filter registration behavior is 100% identical and independent of the locale's `R.string.bestSensor` translation.

**Invariants**: Rule 50 compliance, thread safety across filter methods, mathematical filter null contract, zero SQLite schema alterations, and 100% clean-room test pass rate.

### REQ-CON-021: BLUETOOTH_ADMIN Manifest Declaration for Android 8–11 Compatibility and Defensive BLE Scan Exception Handling [Verified]
- **Tests**: IllegalStateException e)` block.
• If the platform throws an exception during teardown (e.g., Bluetooth adapter disabled or permissions revoked mid-scan), the engine SHALL log a warning, ensure `scanning = false` is maintained, and proceed to invoke `resetTrackingState()` cleanly.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement `REQ-CON-021`, extending and refining `REQ-CON-019` (`ATT-2773`). Targets `app/src/main/AndroidManifest.xml` and `BTSearchForNewDevicesEngine.java`.
2. *Historical Origin & Commit Trace*: Ticket `ATT-3997`, Sprint `2026-43.1`. Discovered during independent whole-app code review of `feature/ATT-3921` (`0234bfc0`). `BLUETOOTH_ADMIN` was present in Initial Commit (`845818c2`, 2019) and was commented out in commit `f2f55e11cf48` ("feat: Android permissions", July 26, 2024). Ticket `ATT-2773` (`REQ-CON-019`) introduced Android 12 permissions and `TRANSPORT_LE` but omitted legacy `BLUETOOTH_ADMIN` verification and scan exception guards.
3. *Root Reason for Existing Formulation*: Commit `f2f55e11cf48` commented out `BLUETOOTH_ADMIN` under the erroneous assumption that modern `BLUETOOTH_SCAN` superseded it globally across all Android versions. On API $\le 30$, `BluetoothLeScanner.startScan` strictly enforces `BLUETOOTH_ADMIN` at the framework Binder boundary. Furthermore, `startScan()` and `stopScan()` omitted defensive exception handling under the assumption that pre-flight permission checks would prevent all platform runtime exceptions.
4. *Preservation of Core Invariants*: Android 12+ runtime permission model (`BLUETOOTH_SCAN`, `BLUETOOTH_CONNECT`) remains strictly intact (`REQ-CON-019`); existing BLE peripheral drivers (`BTLEHeartRateDevice`, `BTLEBikePowerDevice`, `BTLEBikeDevice`, `BTLERunSpeedDevice`) remain 100% untouched; search engine lifecycle state consistency (`scanning` flag) is preserved; and clean-room full test suite pass rate remains 100%.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an Android 8.0–11 device (API 26–30) with `BLUETOOTH_ADMIN` declared with `maxSdkVersion="30"` in `AndroidManifest.xml`,
• *When* the application is installed and executed,
• *Then* `BLUETOOTH_ADMIN` is granted at install time without runtime prompt and without triggering Google Play Store policy warnings on API 31+.
• *Given* an Android 8.0–11 device where `BLUETOOTH_ADMIN` is not granted,
• *When* `BTSearchForNewDevicesEngine.startAsyncSearch()` executes,
• *Then* the method logs a warning, aborts cleanly before calling `scanner.startScan()`, and leaves `scanning = false`.
• *Given* an Android 8.0–11 device where `ACCESS_FINE_LOCATION` is not granted,
• *When* `BTSearchForNewDevicesEngine.startAsyncSearch()` executes,
• *Then* the method logs a warning, aborts cleanly before calling `scanner.startScan()`, and leaves `scanning = false`.
• *Given* a running `BTSearchForNewDevicesEngine` where `scanner.startScan(...)` throws `SecurityException` or `IllegalStateException`,
• *When* `startAsyncSearch()` executes,
• *Then* the exception is caught and logged, `scanning` is restored to `false`, and subsequent calls to `startAsyncSearch()` are not blocked by the `already searching` guard.
• *Given* an active scan where `scanner.stopScan(...)` throws `SecurityException` or `IllegalStateException`,
• *When* `stopAsyncSearch()` executes,
• *Then* the exception is caught and logged, `scanning` is set to `false`, and `resetTrackingState()` executes cleanly.
• *Given* an Android 12+ device (API 31+) with `BLUETOOTH_SCAN` granted,
• *When* `startAsyncSearch()` executes,
• *Then* scanning proceeds using low-latency aggressive scan settings without requiring `BLUETOOTH_ADMIN`.

**Invariants**: Android 8–11 compatibility (API 26–30), Android 12+ permission boundaries (`REQ-CON-019`), search engine lifecycle state consistency (`scanning` flag), zero regression in existing BLE peripheral drivers, and 100% clean-room test pass rate.
- **Implementation**: IllegalStateException e)` block.
• In the event of a `SecurityException` or `IllegalStateException`, the engine SHALL log an error with the exception details, restore `scanning = false`, and return cleanly without crashing the caller thread or permanently locking the engine out of subsequent scan attempts.
4. *Defensive Scan Teardown (`REQ-CON-021.4`)*:
• In `BTSearchForNewDevicesEngine.stopAsyncSearch()`, the call to `scanner.stopScan(mScanCallback)` SHALL be wrapped in a `try { ... } catch (SecurityException
- **Rationale**: The application manifest (`AndroidManifest.xml`) and BLE search engine (`BTSearchForNewDevicesEngine.java`) SHALL declare `BLUETOOTH_ADMIN` bounded to Android 8.0 through Android 11 (API 26–30), verify `BLUETOOTH_ADMIN` permission alongside `ACCESS_FINE_LOCATION` on API $< 31$ before initiating BLE scanning, and defensively handle `SecurityException` and `IllegalStateException` during scan start and scan teardown to prevent search engine state lockup and application crashes (ATT-3997, extending `REQ-CON-019`):
1. *Manifest Permission Declaration (`REQ-CON-021.1`)*:
• In `app/src/main/AndroidManifest.xml`, the system SHALL declare `<uses-permission android:name="android.permission.BLUETOOTH_ADMIN" android:maxSdkVersion="30" />`.
• The declaration SHALL be active (not commented out), ensuring that devices running Android 8.0 through Android 11 obtain install-time Bluetooth admin authorization required by the framework Bluetooth service for BLE discovery, while modern devices running Android 12+ (API 31+) ignore it in favor of runtime `BLUETOOTH_SCAN` without triggering Google Play Store policy warnings.
2. *Legacy Permission Guard in Search Engine (`REQ-CON-021.2`)*:
• In `BTSearchForNewDevicesEngine.startAsyncSearch()`, when executing on Android versions below API 31 (`Build.VERSION.SDK_INT < Build.VERSION_CODES.S`), the engine SHALL explicitly verify both `BLUETOOTH_ADMIN` and `ACCESS_FINE_LOCATION` via `ContextCompat.checkSelfPermission`.
• If either `BLUETOOTH_ADMIN` or `ACCESS_FINE_LOCATION` is not granted (`!= PackageManager.PERMISSION_GRANTED`), the engine SHALL log a descriptive warning (`Log.w`), abort execution before calling `scanner.startScan(...)`, and ensure `scanning` remains `false`.
3. *Defensive Scan Execution & Lifecycle State Restoration (`REQ-CON-021.3`)*:
• In `BTSearchForNewDevicesEngine.startAsyncSearch()`, the call to `scanner.startScan(filters, settings, mScanCallback)` SHALL be wrapped in a `try { ... } catch (SecurityException

### REQ-CON-022: Automated BLE/ANT+ Pedal Power Balance Reference Normalization & Obsolete UI Checkbox Deprecation [Verified]
- **Tests**: `BTLEBikePowerDevice.java`, `ANTBikePowerDevice.java`, `BikePowerSensorsHelper.java`, `DeviceData.kt`, `DeviceDataRepository.kt`, `EditDeviceDialog.kt`, `EditDeviceViewModel.kt`
- **Implementation**: Automated BLE/ANT+ pedal power balance reference flag normalization and removal of obsolete UI correction checkboxes.
- **Rationale**: The system SHALL automatically normalize cycling pedal power balance telemetry from both Bluetooth Low Energy (BLE) and ANT+ power meters to represent the Right pedal percentage $P_R \in [0, 100]\%$ according to protocol reference indicators, strictly adhering to `REQ-UI-377.1`, AND SHALL eliminate obsolete manual correction checkboxes from device configuration UI and data models (ATT-4554, parent epic ATT-355, status: Verified, complementing `REQ-UI-377`):
1. *Automated BLE Pedal Power Balance Reference Normalization (`BTLEBikePowerDevice.java`, `REQ-CON-022.1`)*:
• When parsing Cycling Power Measurement characteristic (`0x2A63`) with Pedal Power Balance present (`isPowerBalancePresent(flags) == true`), `BTLEBikePowerDevice` SHALL read raw balance byte with standard $0.5\%$ resolution (`balanceValue = rawBalance / 2.0`).
• The system SHALL inspect Bit 1 of `flags` (`PEDAL_POWER_BALANCE_REFERENCE_MASK = 1 << 1`):
- If `(flags & PEDAL_POWER_BALANCE_REFERENCE_MASK) != 0` (Bit 1 == 1, denoting Left pedal reference per Bluetooth SIG CPS), the system SHALL invert the value to Right pedal percentage: `balanceValue = 100.0 - balanceValue`.
- If `(flags & PEDAL_POWER_BALANCE_REFERENCE_MASK) == 0` (Bit 1 == 0, denoting Unknown/Right pedal reference), `balanceValue` SHALL remain `rawBalance / 2.0`.
• The resulting value $P_R \in [0.0, 100.0]\%$ SHALL be emitted to `mPowerBalanceSensor.newValue(balanceValue)`.
2. *Automated ANT+ Pedal Power Balance Reference Normalization (`ANTBikePowerDevice.java`, `REQ-CON-022.2`)*:
• In `ANTBikePowerDevice.onNewPedalPowerBalance(long estTimestamp, EnumSet<EventFlag> eventFlags, boolean rightPedalIndicator, int pedalPowerPercentage)`, the system SHALL evaluate `rightPedalIndicator` for valid non-negative balance values ($P \ge 0$):
- If `rightPedalIndicator == true` (Right pedal reference), the percentage SHALL be emitted as `pedalPowerPercentage`.
- If `rightPedalIndicator == false` (Left pedal reference), the system SHALL automatically invert to Right pedal percentage: `100 - pedalPowerPercentage`.
• Negative values ($P < 0$, unconfigured/invalid) SHALL continue to publish `null`.
3. *Deprecation and Removal of Obsolete Manual Correction Checkboxes (`REQ-CON-022.3`)*:
• The manual checkboxes `doublePowerBalanceValues` and `invertPowerBalanceValues` SHALL be completely removed from `EditDeviceDialog.kt` (`PowerMeterSection`), simplifying the screen into a clean read-only list of detected hardware power features.
• Corresponding mutable handlers and properties SHALL be pruned from `EditDeviceViewModel.kt` (`onDoublePowerBalanceValuesChanged`, `onInvertPowerBalanceValuesChanged`), `DeviceData.kt` (`BikePowerFeatures.doublePowerBalanceValues`, `invertPowerBalanceValues`), `DeviceDataRepository.kt` (manual flag masking), and `BikePowerSensorsHelper.java` (`DOUBLE_POWER_BALANCE_VALUES`, `INVERT_POWER_BALANCE_VALUES`).
4. *9-Language Localization Cleanup (`REQ-CON-022.4`)*:
• Obsolete string resources `bike_power__pedal_power_balance_correction`, `bike_power__double_power_balance_values`, and `bike_power__invert_power_balance_values` SHALL be cleanly deleted across all 9 application locales (`values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`).
5. *Preservation of System Invariants (`REQ-CON-022.5`)*:
• `SensorType.PEDAL_POWER_BALANCE` telemetry contract strictly preserved ($P_R \in [0, 100]\%$, `REQ-UI-377.1`).
• Zero SQLite schema migrations (`DevicesDbHelper` bitmask preserves unused bits safely).
• Zero regression in other bike power metrics (instantaneous power, cadence, torque, accumulated energy).
• Clean-room full test suite pass rate under `./gradlew testDebugUnitTest` SHALL remain 100%.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement `REQ-CON-022` under Epic `ATT-355` (*Good and consistent UI*), complementing `REQ-UI-377` (*Pedal Power Balance Real-Time Visual Split Indicator Cockpit Integration*). Targets `BTLEBikePowerDevice.java`, `ANTBikePowerDevice.java`, `BikePowerSensorsHelper.java`, `DeviceData.kt`, `DeviceDataRepository.kt`, `EditDeviceDialog.kt`, `EditDeviceViewModel.kt`, and `values*/strings.xml`.
2. *Historical Origin & Commit Trace*: Manual correction checkboxes were introduced in early ANT+/BLE power meter integration (~2014–2018) in `BikePowerSensorsHelper` and `BTLEBikePowerDevice` when power meter BLE firmware implementations were inconsistent and occasionally omitted standard 0.5% scaling or had inverted left/right references.
3. *Root Reason for Existing Formulation*: Early power meter hardware lacked uniform compliance with Bluetooth SIG Cycling Power Service (CPS) v1.0 and ANT+ Bike Power profiles. Manual checkboxes were provided as an escape hatch for athletes with legacy pedals to double or invert readouts.
4. *Preservation of Core Invariants*: Modern ANT+ and BLE dual-sided power meters strictly transmit the reference flag in every packet. Automating reference evaluation in device drivers guarantees `REQ-UI-377.1` contract ($P_R \in [0, 100]\%$) automatically across all compliant pedals without requiring manual athlete trial-and-error, while eliminating dead UI controls and preserving database schema backward compatibility.

**Acceptance Criteria (Given-When-Then)**:
• *Given* a BLE Cycling Power Measurement packet with Bit 0 = 1 (Balance Present) and Bit 1 = 0 (Right pedal reference) and raw balance 104 (52.0%),
• *When* `BTLEBikePowerDevice` parses the packet,
• *Then* `mPowerBalanceSensor.newValue(52.0)` is published.
• *Given* a BLE Cycling Power Measurement packet with Bit 0 = 1 (Balance Present) and Bit 1 = 1 (Left pedal reference) and raw balance 96 (48.0% Left),
• *When* `BTLEBikePowerDevice` parses the packet,
• *Then* the driver automatically inverts to Right pedal percentage and publishes `mPowerBalanceSensor.newValue(52.0)`.
• *Given* an ANT+ Bike Power balance event with `rightPedalIndicator == true` and `pedalPowerPercentage == 52`,
• *When* `ANTBikePowerDevice` receives the event,
• *Then* `mPowerBalanceSensor.newValue(52)` is published.
• *Given* an ANT+ Bike Power balance event with `rightPedalIndicator == false` (Left reference) and `pedalPowerPercentage == 48` (48% Left),
• *When* `ANTBikePowerDevice` receives the event,
• *Then* `mPowerBalanceSensor.newValue(52)` (100 - 48) is published.
• *Given* an athlete viewing `EditDeviceDialog` for a BLE or ANT+ power meter,
• *When* inspecting the Power Meter section,
• *Then* no manual balance correction checkboxes are rendered, and only detected power capabilities are shown.
• *Given* the 9 localized `strings.xml` resource files,
• *When* running `TranslationParityTest`,
• *Then* the 3 deprecated string keys are absent across all 9 locales without parity mismatch.

**Invariants**: Right-pedal telemetry contract `REQ-UI-377.1`, zero SQLite schema migrations, and 100% clean-room test pass rate.

### REQ-TRK-039: Tracking Subsystem Robustness, Resource Lifecycle & Rate-Limited Error Telemetry Hygiene [Verified]
- **Tests**: `TST-TRK-039`
- **Implementation**: `BANALService.java`, `MyDevice.java`, `TrackerService.java`, `TrainingApplication.java`, `ClockDevice.java`, `DeviceManager.java`, `AltitudeFromPressureDevice.java`, `BatterySaverController.kt`
- **Rationale**: The application tracking and sensor subsystem SHALL enforce robust defensive numerics, lifecycle-aware notification gating, rate-limited failure telemetry, cache-backed scheduler efficiency, and leak-free resource teardown across tracking engines (`BANALService`, `MyDevice`, `TrackerService`, `TrainingApplication`, `ClockDevice`, `DeviceManager`, `AltitudeFromPressureDevice`, `BatterySaverController`) (ATT-4032, extending `REQ-TRK-027` and `REQ-TRK-029`).
- **Description**:
1. *Defensive Numerics & List Boundary Hardening (`REQ-TRK-039.1`)*:
• In `BANALService.newLap`, the system SHALL guard lap speed calculation against zero or negative duration (`lapTime_s > 0 ? (lapDistance / lapTime_s) : 0.0`), strictly preventing `Double.NaN` and `Double.POSITIVE_INFINITY`.
• In `BANALService.getSportTypeId(avgSpd)`, the system SHALL verify `!sportTypeIds.isEmpty()` before accessing `sportTypeIds.get(0)`, returning a safe fallback default sport ID when the queried sport list is empty.
2. *Scheduler Thread SQLite Overhead Elimination & Caching (`REQ-TRK-039.2`)*:
• In `MyDevice.java`, the device name SHALL be cached in memory (`mCachedDeviceName`) upon resolution or mutation, eliminating repeated synchronous SQLite reads on the 1 Hz sensor sampling tick from `SensorData.getDeviceName()`.
• In `TrackerService.java`, the active sport UI name SHALL be cached keyed by sport ID, eliminating synchronous SQLite queries to `SportTypeDatabaseManager.getUIName` on every 1 Hz tracking tick.
3. *Notification Lifecycle Gating & Resurrection Prevention (`REQ-TRK-039.3`)*:
• In `TrainingApplication.updateTimeAndDistanceToNotification`, the system SHALL verify that tracking is currently active or paused (`isTracking() || isPaused()`) before mutating `mTrackingAndSearchingNotificationBuilder` or invoking `mNotificationManager.notify()`, preventing late background sampling ticks from resurrecting dismissed notifications after tracking has stopped.
4. *Rate-Limited Telemetry Logging & Clock Monotonicity (`REQ-TRK-039.4`)*:
• In `TrackerService.tracker` and `ClockDevice.ClockTimeTask`, catch-all exception logging SHALL enforce rate-limiting using a burst-then-interval policy (burst up to 3 consecutive logs, then throttled to every 60th occurrence), preventing logcat flooding (~3600 logs/hour) during persistent errors while preserving exception isolation.
• `ClockDevice` timer ticks SHALL be anchored to monotonic elapsed real time (`SystemClock.elapsedRealtime`) to prevent timer tick drift over extended sessions.
5. *Deterministic Resource Cleanup & Lifecycle Teardown (`REQ-TRK-039.5`)*:
• In `DeviceManager.createPairedRemoteDevices`, SQLite `Cursor` instances SHALL be managed via try-with-resources to prevent cursor leaks on query failure or early return.
• In `AltitudeFromPressureDevice.shutDown()`, redundant/accidental `registerReceiver` invocations prior to receiver unregistration SHALL be excised.
• In `BatterySaverController.release()`, the internal coroutine `scope` SHALL be cleanly cancelled, and `getSystemBrightness()` SHALL safely clamp extracted brightness within safety boundaries (`DimmingLevel.SAFETY_FLOOR` to `1.0f`).
6. *Behavioral Unit Testing & Governance Verification (`REQ-TRK-039.6`)*:
• Automated unit tests SHALL verify: (a) `newLap` zero-duration lap speed fallback and non-empty `getSportTypeId` resolution, (b) notification suppression when tracking is stopped, (c) rate-limited error logging in `TrackerService` and `ClockDevice`, (d) receiver teardown in `AltitudeFromPressureDevice.shutDown()`, and (e) coroutine scope cancellation and brightness clamping in `BatterySaverController`.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement `REQ-TRK-039` under Epic `None` (*Tracking Subsystem Robustness, Resource Lifecycle & Rate-Limited Error Telemetry Hygiene*), extending `REQ-TRK-027` (*Clock and Sensor Resilience*) and `REQ-TRK-029` (*TrackerService State Management*). Targets `BANALService.java`, `MyDevice.java`, `TrackerService.java`, `TrainingApplication.java`, `ClockDevice.java`, `DeviceManager.java`, `AltitudeFromPressureDevice.java`, and `BatterySaverController.kt`.
2. *Historical Origin & Commit Trace*: Independent whole-app code review (`feature/ATT-3921` @ `0234bfc0`, Oct 2026). Accidental receiver registration in `AltitudeFromPressureDevice.shutDown()` was traced to commit `2afc198549`. Unbounded logging was introduced when adding exception barriers in `ATT-3981` (`REQ-TRK-027`).
3. *Root Reason for Existing Formulation*: Blanket exception catches were added without rate limiting to prevent background timer aborts; notification updates were triggered whenever a timer tick fired without checking tracking mode lifecycle; `shutDown()` receiver call was a copy-paste error during Android 14 receiver flag migration.
4. *Preservation of Core Invariants*: Full tracking accuracy, lap calculation, SQLite sample recording, notification updates during active sessions, and 100% clean-room unit test suite pass rate remain strictly preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* a new lap triggered with `lapTime_s == 0`,
• *When* `BANALService.newLap()` executes,
• *Then* lap speed is calculated as `0.0` without producing `NaN` or `Infinity`.
• *Given* `BANALService.getSportTypeId` invoked when `sportTypeIds` is empty,
• *When* queried,
• *Then* a safe fallback sport ID is returned without `IndexOutOfBoundsException`.
• *Given* `updateTimeAndDistanceToNotification` called after tracking stopped (`TrackingMode.READY`),
• *When* invoked,
• *Then* it returns early and does NOT post or resurrect a notification.
• *Given* repeated errors in `TrackerService.tracker` or `ClockDevice.ClockTimeTask`,
• *When* failures persist,
• *Then* logging is throttled after the burst threshold rather than logging every second.
• *Given* `AltitudeFromPressureDevice.shutDown()` invoked,
• *When* executed,
• *Then* `registerReceiver` is NOT called, and receiver is cleanly unregistered.
• *Given* `BatterySaverController.release()` invoked,
• *When* executed,
• *Then* the coroutine scope is cancelled.

**Invariants**: Zero regression in tracking accuracy, database recording, and 100% clean-room test pass rate.

### REQ-TRK-040: Landscape Cockpit Dual-Pane Layout, Widescreen Sensor Grid & Sub-Pane Overlay Docking [Verified]
- **Tests**: `TST-TRK-040` | **Tickets**: `ATT-52`
- **Implementation**: `SensorGridScreen.kt`, `SensorFieldView.kt`, `TrackingTabsScreen.kt`, `MainActivityWithNavigation.kt`
- **Rationale**: The tracking cockpit SHALL support landscape device orientation with a responsive dual-pane layout when maps are active, a full-width widescreen grid when maps are disabled, sub-sheet docking within the map pane to prevent telemetry masking, and strict adherence to the athlete's portrait lock preference (ATT-52, ATT-2144):
1. *Orientation Adaptation & Preference Gating (`REQ-TRK-040.1`)*:
• The Cockpit SHALL adapt dynamically to `Configuration.ORIENTATION_LANDSCAPE` via `LocalConfiguration.current.orientation`.
• When *"Hochformat erzwingen"* (`forcePortrait`) is enabled in Display Settings (`SettingsDataStore` / `TrainingApplication.forcePortrait()`), the screen SHALL remain locked in portrait mode (`ActivityInfo.SCREEN_ORIENTATION_PORTRAIT`), preventing automatic rotation.
2. *Dual-Pane Map & Telemetry Split Layout (`REQ-TRK-040.2`)*:
• When `showMap == true` in landscape mode, `SensorGridScreen` SHALL render a horizontal split layout (`Row(Modifier.fillMaxSize())`) allocating equal 50/50 flexible width (`weight(1f)`) between telemetry and map panes.
• The left pane SHALL display configured sensor fields in their regular rows/columns, bounded within the viewport and scrolling vertically if exceeding available screen height.
• The right pane SHALL host the interactive `TrackingMapContainer` and `ElevationProfile`.
3. *Sub-Pane Sheet Docking (`REQ-TRK-040.3`)*:
• When a Live Segment (`LiveSegmentSheet`) or ClimbPro (`LiveClimbSheet`) sheet is active in landscape mode with map enabled, the bottom sheet container SHALL dock exclusively within the right (map) pane.
• Crucial Invariant: The left telemetry pane (Speed, HR, Power, Cadence, etc.) SHALL remain 100% visible, unoccluded, and interactive at all times.
4. *Widescreen Sensor Grid (`REQ-TRK-040.4`)*:
• When `showMap == false` in landscape mode, the sensor grid SHALL occupy the entire widescreen canvas (`fillMaxSize()`).
• Grid cells SHALL expand horizontally with proportional padding, rendering scalable high-contrast numerals (`SensorFieldView` view sizes `XSMALL` through `XXXHUGE`) optimized for glanceability.
5. *Configuration Mode Operability & Ambient Overlays (`REQ-TRK-040.5`)*:
• In `ScreenMode.CONFIGURATION`, sensor field editing, row/col addition, deletion, and pick-and-place reordering SHALL remain fully functional in landscape orientation.
• Ambient overlays (`TurnPromptBanner`, `ForkDecisionCard`, `ReturnNavigationHud`, `AutoPauseHudBanner`) SHALL remain centered and elevated above the active tracking viewport without displacing pane allocations.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement `REQ-TRK-040` under Epic `ATT-2144` (*Landscape Mode*), complementing `REQ-UI-358` (*Cockpit Viewport Allocation*), `REQ-UI-359` (*Live Segment/Climb Bottom Sheet*), and `REQ-SET-052` (*Display Settings*). Targets `SensorGridScreen.kt`, `SensorFieldView.kt`, `TrackingTabsScreen.kt`, and `MainActivityWithNavigation.kt`.
2. *Historical Origin & Commit Trace*: Ticket `ATT-52`, Sprint `2026-43.9`. Extends previous portrait-only viewport bounds (`f48e24c7`, `ATT-3343`) and full-screen bottom sheet scaffold (`69e4f1a2`, `ATT-3341`).
3. *Root Reason for Existing Formulation*: Cockpit layout was originally designed exclusively for vertical portrait viewports. Mounting smartphones on aero handlebars, out-front bike mounts, and indoor smart trainers requires a horizontal layout that avoids compressing sensor cells or occluding telemetry during climb and segment alerts.
4. *Preservation of Core Invariants*: 100% byte-for-byte portrait layout fidelity, `forcePortrait` setting enforcement, zero callback suppression (Rule 34), and 100% test pass rate remain strictly preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an active cockpit with map enabled (`showMap == true`),
• *When* the device is rotated to landscape orientation,
• *Then* `SensorGridScreen` splits into a dual-pane horizontal row with sensor fields on the left (50%) and the map on the right (50%).
• *Given* an active cockpit with map disabled (`showMap == false`),
• *When* the device is rotated to landscape orientation,
• *Then* the sensor grid spans the full screen width with scalable numerals.
• *Given* an athlete riding in landscape mode with map active,
• *When* approaching a Live Segment or ClimbPro hill,
• *Then* the bottom sheet displays exclusively within the right pane, keeping left telemetry values permanently visible.
• *Given* *"Hochformat erzwingen"* is toggled ON in Display Settings,
• *When* the device is physically rotated to landscape,
• *Then* the screen remains locked in portrait orientation.
• *Given* `SensorGridScreen` in `ScreenMode.CONFIGURATION` in landscape orientation,
• *When* interacting with sensor tiles,
• *Then* field editing, row/col additions, and pick-and-place reordering work without error.

**Invariants**: Zero regression in portrait mode layout, `forcePortrait` lock, gesture and callback contracts (Rule 34), and 100% clean-room test pass rate.

### REQ-TRK-041: Geodesic Displacement Plausibility Gating for Distance Accumulation & Canonical Multi-Sensor Summary Filtering [Verified]
- **Tests**: `TST-TRK-041` | **Tickets**: `ATT-4843`
- **Implementation**: `SpeedAndLocationDevice.java`, `TrackerService.java`
- **Rationale**: The tracking subsystem SHALL prevent multi-kilometer teleportation / cellular triangulation multipath spikes from accumulating into distance metrics and SHALL guarantee that secondary multi-sensor telemetry streams do not overwrite canonical summary distance and speed metrics in `WorkoutSummaries` (ATT-4843):
1. *Geodesic Displacement Plausibility Clamping in `SpeedAndLocationDevice` (`REQ-TRK-041.1`)*:
• In `SpeedAndLocationDevice.onNewLocation()`, before incrementing distance accumulators (`mDistanceSensor`, `mLapDistanceSensor`), the system SHALL evaluate displacement velocity against physical plausibility:
  - If `mPrevLocation != null` and elapsed time `dt_ms = location.getTime() - mPrevLocation.getTime()` satisfies `dt_ms > 0 && dt_ms < 30000`:
    * The system SHALL compute displacement speed `calcSpeed = delta_distance / (dt_ms / 1000.0)`.
    * If `calcSpeed > 70.0\text{ m/s}` ($252\text{ km/h}$, matching the speed plausibility ceiling in `REQ-TRK-014`), the displacement SHALL be classified as an impossible multipath / cell-tower triangulation jump: `delta_distance` SHALL be clamped to `0.0` for distance accumulators, suppressing increments to `mDistanceSensor` and `mLapDistanceSensor`.
  - Legitimate human velocities ($\le 70.0\text{ m/s}$) SHALL continue to accumulate distance without attenuation.
2. *Canonical Multi-Sensor Summary Filtering in `TrackerService` (`REQ-TRK-041.2`)*:
• In `TrackerService.sampleAndWriteToDb()`, when capturing multi-sensor telemetry across `banalService.getAllSensorData()`, updating `summaryValues` and internal accumulators (`mDistanceTotal_m`, `mTimeTotal_s`, `mTimeActive_s`, `CALORIES`, `LAPS`, `ASCENDING`, `DESCENDING`) SHALL strictly require `sensorData.getDeviceName() == null` (the canonical/primary proxy sensor).
• Secondary, device-qualified sensor streams (`sensorData.getDeviceName() != null`, such as `"Google Fused"` or `"GPS"`) SHALL continue logging to raw `samplingValues` table columns (e.g. `"DISTANCE_m (Google Fused)"`), but SHALL NEVER overwrite `WorkoutSummaries.DISTANCE_TOTAL_m` or `mDistanceTotal_m`.
3. *Authoritative Average Speed Consistency (`REQ-TRK-041.3`)*:
• `WorkoutSummaries.SPEED_AVERAGE_mps` SHALL be calculated strictly from canonical `mDistanceTotal_m / mTimeActive_s`, harmonizing summary cards, elevation profile X-axis, and lap tables.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement `REQ-TRK-041` complementing `REQ-TRK-008` (*Align session averages with core summary metrics*) and `REQ-TRK-014` (*Location Delta Fallback for Missing GPS Bearing and Speed*). Targets `SpeedAndLocationDevice.java` and `TrackerService.java`.
2. *Historical Origin & Commit Trace*: Ticket `ATT-4843`, Sprint `2026-44.1`. Rooted in forensic analysis of workout 434 where indoor cell-tower triangulation during a pause added 8.5 km to `Google Fused` distance, which then overwrote canonical GPS summary distance in `TrackerService`.
3. *Root Reason for Existing Formulation*: `REQ-TRK-014` introduced 70 m/s plausibility clamping for instantaneous speed, but omitted distance accumulator increments. `REQ-TRK-008` aligned summary averages with session metrics, but iterating `getAllSensorData()` in `TrackerService` without `deviceName == null` filtering allowed the last enumerated hardware device to overwrite proxy totals.
4. *Preservation of Core Invariants*: Full multi-sensor logging in `WorkoutSamples.db` remains intact. Legitimate cycling/running movements ($\le 70\text{ m/s}$) accumulate full distance. Single-thread database serialization on `mDbExecutor` is preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an active location device receiving consecutive location fixes with $\Delta t = 1000\text{ ms}$ and $\Delta s = 4,245.6\text{ m}$ ($v = 4,245.6\text{ m/s} > 70.0\text{ m/s}$),
• *When* `onNewLocation` processes the jump,
• *Then* `mDistanceSensor` and `mLapDistanceSensor` SHALL NOT increment by $4,245.6\text{ m}$ (`delta_distance` clamped to `0.0`).
• *Given* an active tracking session with canonical `ProxySensor` (`DISTANCE_m = 4370.0 m`, `deviceName == null`) and secondary `Google Fused` (`DISTANCE_m = 13377.3 m`, `deviceName == "Google Fused"`),
• *When* `sampleAndWriteToDb()` executes,
• *Then* `summaryValues.getAsDouble(DISTANCE_TOTAL_m)` SHALL equal `4370.0 m`, and `samplingValues` SHALL record both `DISTANCE_m` (`4370.0 m`) and `"DISTANCE_m (Google Fused)"` (`13377.3 m`).
• *Given* an athlete running or cycling at $15\text{ m/s}$ ($\le 70\text{ m/s}$),
• *When* fixes arrive at 1-second intervals with $\Delta s = 15\text{ m}$,
• *Then* `mDistanceSensor` SHALL increment by the full $15\text{ m}$.

**Invariants**: Secondary multi-sensor logging in `WorkoutSamples.db` MUST NOT be dropped; legitimate speeds ($\le 70.0\text{ m/s}$) MUST accumulate distance accurately; single-thread serialization on `mDbExecutor` MUST be maintained.

### REQ-TRK-042: 1-Hz Live-Tracking Allocation Hotspot Elimination, Recomposition Decoupling & Bounded Polyline Serialization [Verified]
- **Tests**: `TST-TRK-042` | **Tickets**: `ATT-4863`, `ATT-4923`
- **Implementation**: `BANALServiceRepository.kt`, `MapLayers.kt`, `SensorGridScreen.kt`, `TrackingViewModel.kt`
- **Rationale**: The live-tracking subsystem SHALL eliminate high-frequency object allocations, O(N^2) list copies, Compose snapshot invalidations during composition, and unbounded Binder IPC transfers during 1-Hz tracking to protect JVM heap stability and prevent `OutOfMemoryError` on long-running workouts (ATT-4863, ATT-4923):
1. *Amortized O(1) Snapshot Append & Deduplicated PathPoint Invariants (`REQ-TRK-042.1`)*:
• In `BANALServiceRepository.kt`, `_currentTrack` and `_currentPathPoints` SHALL use an amortized $O(1)$ append snapshot structure (`SnapshotChunkedList<T>`), eliminating full-list copying upon each moving coordinate or distance advancement while preserving standard `List<T>` random-access read fidelity. Appending SHALL strictly occur only when `_currentLocation.value` is non-null AND either geographical position (`newLocation != _currentLocation.value`) or cumulative distance (`newDistance != _currentDistance.value`) has advanced outside the stationary deadband.
2. *Incremental Chunked Live Track Decimation (`REQ-TRK-042.2`)*:
• In `MapLayers.kt` (`LiveTrackLayer`), track decimation SHALL operate incrementally via `IncrementalTrackDecimator` (a plain holder object adhering to Rule 66 without Compose `MutableState` mutation in composition). Historical points exceeding the unsimplified tail ($M = 200$) SHALL be simplified in fixed frozen chunks ($C = 200$) using `PolyUtil.simplify(chunk, 3.0)` once upon chunk boundary completion and permanently cached, preventing re-simplification of older history and eliminating coordinate flutter across 1-Hz ticks.
3. *Non-State Index Caching & Elevation Profile Composition Gating (`REQ-TRK-042.3`)*:
• In `SensorGridScreen.kt`, the last route segment index SHALL be cached inside a non-state holder (`SegmentIndexHolder`) rather than a `MutableState`, preventing Compose snapshot writes inside `remember` during composition.
• `TrackingElevationProfileResolver.resolve(...)` SHALL be evaluated strictly when `state.showElevationProfile == true`. When hidden, resolution and recomposition on 1-Hz path updates SHALL be bypassed.
4. *Memoized Base Sensor Fields & Combine Decoupling (`REQ-TRK-042.4`)*:
• In `TrackingViewModel.kt:loadSensorFieldStates()`, `baseFields` (`configs.map { SensorFieldState(...) }`) SHALL be precomputed and memoized when view configurations, activity type, or zone options change, so 1-Hz `allFilteredSensorData` updates only execute `applySensorData(...)` without reallocating base field descriptors.
• `currentPathPoints` SHALL be decoupled from `loadSensorFieldStates()` combine and observed strictly when `showElevationProfile == true`, ensuring sensor field states evaluate at most once per sensor tick.
5. *Structural Value Equality for Device Telemetry (`REQ-TRK-042.5`)*:
• In `BANALServiceRepository.kt`, `DeviceTelemetry` SHALL NOT contain synthetic non-deterministic fields such as `version = System.nanoTime()`, restoring data class structural equality so `_allActiveDevicesTelemetry` only emits downstream when device status or sensor values genuinely change.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: `REQ-TRK-042` (*1-Hz Live-Tracking Allocation Hotspot Elimination, Recomposition Decoupling & Bounded Polyline Serialization*).
2. *Historical Origin & Commit Trace*: Ticket `ATT-4863` (commit `06195f2a`) and follow-up `ATT-4923`. Follows Crashlytics issue `16e0317dd5b3aa5a53b9248f50f05822` in release `4.9.38.5 (269)`.
3. *Root Reason for Existing Formulation*: `ATT-4863` stopped allocations on stationary ticks and memoized base fields, leaving $O(N)$ copy-on-append and full re-decimation active during movement. `ATT-4923` introduces $O(1)$ amortized snapshot appending, incremental chunked decimation, and combine decoupling.
4. *Preservation of Core Invariants*: Visual smoothness of live map traces and elevation profiles, 200-point full-fidelity tail, thread-safe snapshot reading, and 100% test pass rate are strictly preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an active tracking session remaining stationary for 60 seconds,
• *When* the 1-Hz observation loop executes,
• *Then* `_currentPathPoints` SHALL NOT accumulate 60 duplicate points.
• *Given* a long workout track with 15,000 recorded points,
• *When* appending new points during live movement,
• *Then* `SnapshotChunkedList` appends in amortized $O(1)$ time and memory without copying the prior 15,000 points.
• *Given* a growing workout track exceeding 400 points,
• *When* `LiveTrackLayer` renders,
• *Then* `IncrementalTrackDecimator` permanently freezes simplified historical chunks, preserving historical vertex stability and keeping the unsimplified tail at 200 points without full re-simplification.
• *Given* a tracking cockpit tab with `showElevationProfile == false`,
• *When* 1-Hz path points update,
• *Then* `TrackingElevationProfileResolver.resolve` is bypassed and `loadSensorFieldStates()` emits at most once per sensor tick.
• *Given* consecutive 1-Hz ticks where device telemetry is identical,
• *When* `DeviceTelemetry` objects are compared,
• *Then* `_allActiveDevicesTelemetry` suppresses duplicate downstream emissions.

**Invariants**: Visual smoothness and accuracy of live map traces and elevation profiles are preserved; raw sample storage in `WorkoutSamples.db` remains 100% uncompromised; 100% clean-room test pass rate.



### REQ-TRK-043: Sport-Specific Auto-Pause Resume Debounce & Symmetric Single-Threshold Evaluation [Verified]
- **Tests**: `TST-TRK-043` | **Tickets**: `ATT-4839`
- **Implementation**: `AutoPauseManager.kt`, `TuningConfig.kt`, `TuningPreferencesDataStore.kt`, `AutoPauseTabsScreen.kt`, `AutoPauseViewModel.kt`
- **Rationale**: The auto-pause engine SHALL replace immediate single-tick resumption and asymmetric `+0.5` speed hysteresis deadbands with configurable sport-specific resume debounce timers evaluated against the exact configured sport speed threshold, eliminating false resume triggers from transient GPS jitter spikes (ATT-4839, extending `REQ-TRK-018` and `REQ-SET-080`):
1. *Configurable Sport-Specific Resume Debounce Window (`REQ-TRK-043.1`)*:
• `TuningConfig`, `TuningPreferencesDataStore`, and `AutoPauseConfig` SHALL persist independent resume debounce times in whole seconds:
  - **Cycling (`BSportType.BIKE`)**: Range $1 \dots 20\text{ s}$, default $2\text{ s}$.
  - **Running (`BSportType.RUN`)**: Range $1 \dots 20\text{ s}$, default $5\text{ s}$.
  - **Other Sports (`BSportType.UNKNOWN`)**: Range $1 \dots 60\text{ s}$, default $10\text{ s}$.
2. *Symmetric Single-Threshold Speed Evaluation (`REQ-TRK-043.2`)*:
• `AutoPauseManager` SHALL compare both entering Auto-Pause ($v < v_{\text{threshold}}$) and leaving Auto-Pause ($v \ge v_{\text{threshold}}$) against the exact configured sport speed threshold, eliminating the hidden `+0.5` speed hysteresis offset deadband.
3. *Consecutive-Tick Resume Confirmation & Counter Reset (`REQ-TRK-043.3`)*:
• While in `AutoPauseState.AUTO_PAUSED`, transitioning back to `AutoPauseState.TRACKING` and emitting `SoundCue.AUTO_RESUME` SHALL require `resumeDebounceSec` consecutive 1-Hz ticks of fresh, valid speed $\ge v_{\text{threshold}}$.
• Any tick with speed $< v_{\text{threshold}}$, stale packet timestamp ($> 5000\text{ ms}$), or corrupt numerical reading SHALL reset the resume counter to 0 and maintain `AUTO_PAUSED`.
4. *Settings UI Resume Detection Time Sliders (`REQ-TRK-043.4`)*:
• In `AutoPauseTabsScreen.kt`, each sport tab (*Bike*, *Run*, *Other*) SHALL provide a *Resume Detection Time* slider component (`TuningSliderItem`) with immediate asynchronous persistence and reset support via `TuningPreferencesDataStore.resetAutoPausePreferences(sportType)`.
5. *Localization Parity (`REQ-TRK-043.5`)*:
• String resources for the resume detection time title, description, and default hints SHALL maintain full parity across all 9 supported languages (`values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`).

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement `REQ-TRK-043`, refining `REQ-TRK-018` (`ATT-45` / `ATT-3677`) and `REQ-SET-080` (`ATT-4836`). Targets `AutoPauseManager.kt`, `TuningPreferencesDataStore.kt`, `AutoPauseTabsScreen.kt`, and `AutoPauseViewModel.kt`.
2. *Historical Origin & Commit Trace*: Ticket `ATT-4839`, Sprint `2026-44.1`. Follows `ATT-45` (which added the `+0.5` speed offset as a proxy for debounce) and `ATT-4836` (which established the dedicated Auto-Pause settings screen).
3. *Root Reason for Existing Formulation*: The legacy `+0.5` speed offset deadband was created because `AUTO_PAUSED` lacked an independent time confirmation counter. However, a single 1-Hz GPS spike above the deadband immediately resumed tracking. Replacing the speed offset with time-based multi-tick debounce provides physical stability without distorting threshold values.
4. *Preservation of Core Invariants*: Manual user pause lock precedence (`mManualPauseLock`), 2-tick `SENSOR_LOST` recovery hysteresis, synchronous sensor gating, and 100% test pass rate strictly preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an active cycling session in `AUTO_PAUSED` with default settings (threshold 3 km/h, resume debounce 2s),
• *When* a single GPS jitter spike of 5 km/h arrives for 1 second followed by 0 km/h,
• *Then* `AutoPauseManager` increments resume counter to 1, resets counter to 0 on the stationary tick, and remains in `AUTO_PAUSED` with zero resume cues emitted.
• *Given* an active cycling session in `AUTO_PAUSED` with threshold 3 km/h and resume debounce 2s,
• *When* fresh speed readings of 3.5 km/h arrive continuously for 2 consecutive seconds,
• *Then* `AutoPauseManager` transitions to `TRACKING` and emits `SoundCue.AUTO_RESUME` on the 2nd second.
• *Given* an active running session in `AUTO_PAUSED` with threshold 2 km/h and resume debounce 5s,
• *When* speed $\ge 2\text{ km/h}$ is maintained for 4 seconds and drops below 2 km/h on second 5,
• *Then* `AutoPauseManager` resets resume counter to 0 and remains `AUTO_PAUSED`.

**Invariants**: Manual pause lock precedence, sensor lost state recovery, moving average calculations, and 100% clean-room test pass rate MUST NOT be broken.



### REQ-TRK-044: Optional Automatic Stationary Auto-Pause at Workout Start [Verified]
- **Tests**: `TST-TRK-044` | **Tickets**: `ATT-4840`, `ATT-4925`
- **Implementation**: `AutoPauseManager.kt`, `TuningConfig.kt`, `TuningPreferencesDataStore.kt`, `AutoPauseTabsScreen.kt`, `AutoPauseViewModel.kt`, `TrainingApplication.java`, `TrackerService.java`, `BANALServiceRepository.kt`
- **Rationale**: When starting a workout while stationary, the tracker SHALL optionally transition directly into `AUTO_PAUSED` without waiting for the pause debounce timer to elapse (ATT-4840, extending `REQ-TRK-018`, `REQ-SET-080`, and `REQ-TRK-043`):
1. *Global Start in Auto-Paused Preference (`REQ-TRK-044.1`)*:
• `TuningConfig`, `TuningPreferencesDataStore`, and `AutoPauseConfig` SHALL persist a global boolean preference `startInAutoPauseEnabled` (default `false`).
2. *Settings UI Global Toggle (`REQ-TRK-044.2`)*:
• `AutoPauseTabsScreen.kt` SHALL render a global *Start in Auto-Paused* toggle (`TuningToggleItem`) at the top of the Auto-Pause settings screen with reactive updates via `AutoPauseViewModel.updateStartInAutoPauseEnabled()`.
3. *Domain Startup Predicate (`REQ-TRK-044.3`)*:
• `AutoPauseManager` SHALL expose a static pure function `shouldStartInAutoPause(config, sportType, currentSpeedMps, hasSpeedSource)` evaluating whether all startup conditions are satisfied: `hasSpeedSource == true`, `config.startInAutoPauseEnabled == true`, auto-pause is enabled for `sportType`, and `currentSpeedMps` is below the configured sport speed threshold (or null/0.0/corrupt). If `hasSpeedSource` is `false`, it SHALL return `false` immediately.
4. *Direct Workout Start Transition & Speed Source Removal (`REQ-TRK-044.4`)*:
• When the user starts tracking with `startInAutoPauseEnabled == true` and `shouldStartInAutoPause` evaluates to `true`, tracking SHALL initialize directly in `TrackingMode.PAUSED` (`isAutoPaused = true`) and `AutoPauseState.AUTO_PAUSED` without accumulating debounce ticks. If `hasSpeedSource` is `false` (e.g. indoor workout without speed sensors), tracking SHALL initialize directly in `TrackingMode.TRACKING`.
• Mid-Workout Speed Source Removal: In `evaluateTick`, when `currentState == AutoPauseState.AUTO_PAUSED` and all speed sources are removed or unavailable (`hasSpeedSource == false`), `AutoPauseManager` SHALL transition to `AutoPauseState.SENSOR_LOST` with `triggerResume = true`, preventing the workout from remaining auto-paused indefinitely while preserving tunnel dropout protections when `hasSpeedSource == true`.
5. *Localization Parity (`REQ-TRK-044.5`)*:
• String resources for the toggle title and description SHALL maintain full parity across all 9 supported languages.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement `REQ-TRK-044`, refining `REQ-TRK-018` (`ATT-45` / `ATT-3677`), `REQ-TRK-037` (`ATT-4094`), and `REQ-SET-080` (`ATT-4836`). Targets `AutoPauseManager.kt`, `TuningPreferencesDataStore.kt`, `AutoPauseTabsScreen.kt`, `TrainingApplication.java`, and `TrackerService.java`.
2. *Historical Origin & Commit Trace*: Ticket `ATT-4840`, Sprint `2026-44.1` (commit `e64ff057`) and `ATT-4925`, Sprint `2026-44.2`. Follows `ATT-4839` (which introduced configurable resume debounce) and addresses user complaints of stationary duration polluting moving time during equipment setup, as well as deadlocks when no speed source is available.
3. *Root Reason for Existing Formulation*: Tracking historically always started in `TRACKING` because the debounce timer was designed to detect deceleration from active motion. When starting from complete rest, waiting for the debounce delay is unnecessary when the user explicitly opts into immediate auto-pause. However, treating null speed without a speed source as stationary caused indoor workouts without speed sensors to stay paused forever.
4. *Preservation of Core Invariants*: Moving average speed calculations, manual pause precedence, resume debounce confirmation (`REQ-TRK-043`), GPS fix acquisition waiting, and tunnel dropout protection (`REQ-TRK-037`) are 100% preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* `startInAutoPauseEnabled` is `true`, bike auto-pause is enabled, `hasSpeedSource` is `true`, and phone is stationary (speed 0.0 m/s or null GPS acquiring),
• *When* the user starts a cycling workout,
• *Then* tracking initializes directly in `TrackingMode.PAUSED` (`isAutoPaused = true`) and `AutoPauseState.AUTO_PAUSED`.
• *Given* `startInAutoPauseEnabled` is `true`, but `hasSpeedSource` is `false` (indoor HR-only workout),
• *When* the user starts a workout,
• *Then* tracking initializes in `TrackingMode.TRACKING`.
• *Given* the workout is currently in `AutoPauseState.AUTO_PAUSED` and all speed sources are removed or disconnected (`hasSpeedSource == false`),
• *When* `evaluateTick` executes,
• *Then* `AutoPauseManager` transitions to `AutoPauseState.SENSOR_LOST` with `triggerResume = true`.
• *Given* `startInAutoPauseEnabled` is `true`, but run auto-pause is disabled,
• *When* the user starts a running workout while stationary,
• *Then* tracking initializes in `TrackingMode.TRACKING`.
• *Given* `startInAutoPauseEnabled` is `false`,
• *When* the user starts a workout while stationary,
• *Then* tracking initializes in `TrackingMode.TRACKING` and waits for normal debounce.

**Invariants**: Manual pause lock precedence, sensor lost recovery, tunnel dropout protection, and 100% clean-room test pass rate MUST NOT be broken.


### REQ-TRK-045: Clear Non-Recording Status Indication: Unified HUD Banner and Grid Dimming [Verified]
- **Tests**: `TST-TRK-045` | **Tickets**: `ATT-4856`
- **Implementation**: `SensorGridScreen.kt`, `Color.kt`, `strings.xml`
- **Rationale**: When viewing live sensor tabs in `ScreenMode.TRACKING`, athletes SHALL have immediate, unambiguous visual clarity when workout recording is not active (ATT-4856, extending `REQ-TRK-018`):
1. *Stationary Floating Non-Recording Top HUD Banner across Tabs (`REQ-TRK-045.1`)*:
• In `ScreenMode.TRACKING`, when workout recording is not active (`trackingMode != TrackingMode.TRACKING`), the system SHALL render `NonRecordingStatusBanner` as a stationary floating overlay at `Alignment.TopCenter` in `TrackingTabsScreen.kt` outside the `HorizontalPager` across all sensor/telemetry/map/lap tabs (`pagerState.currentPage > 0`), ensuring the banner stays in a fixed position when swiping between tabs (ATT-5283).
• On the Control tab (`pagerState.currentPage == 0`), the stationary `NonRecordingStatusBanner` SHALL be cleanly hidden.
• `NonRecordingStatusBanner` SHALL use the application's primary blue theme palette (`MaterialTheme.colorScheme.primaryContainer` / `onPrimaryContainer` container with `MaterialTheme.colorScheme.primary` / `onPrimary` action button and border), aligning with `ControlTrackingScreen` Start/Resume controls.
• When `trackingMode == TrackingMode.READY`, the banner SHALL display "Not Started" (`cockpit_status_not_started`), icon `Icons.Default.PlayCircle`, and action button "Start" (`Start`), dispatching `REQUEST_START_TRACKING`.
• When `trackingMode == TrackingMode.PAUSED` and `!isAutoPaused`, the banner SHALL display "Paused" (`cockpit_status_paused`), icon `Icons.Default.PauseCircle`, and action button "Resume Now" (`cockpit_auto_pause_resume`), dispatching `REQUEST_RESUME_FROM_PAUSED`.
• When `trackingMode == TrackingMode.PAUSED` and `isAutoPaused`, the banner SHALL display "Auto-Paused" (`cockpit_auto_pause_badge`), icon `Icons.Default.PauseCircle`, and action button "Resume Now" (`cockpit_auto_pause_resume`), dispatching `REQUEST_RESUME_FROM_PAUSED`.
2. *Sensor Grid Opacity Dimming (`REQ-TRK-045.2`)*:
• In `ScreenMode.TRACKING`, whenever `trackingMode != TrackingMode.TRACKING` (`READY`, `PAUSED`, `AUTO_PAUSED`), the sensor grid content SHALL be rendered with `alpha = 0.6f` via compositing graphics layer, while the top status banner remains at full `1.0f` brightness.
• In `ScreenMode.CONFIGURATION` and `ScreenMode.PREVIEW`, sensor grid content SHALL remain undimmed (`alpha = 1.0f`).
3. *Reactive Tracking State Tracking (`REQ-TRK-045.3`)*:
• `SensorGridScreen` and `TrackingTabsScreen` SHALL observe tracking state and auto-paused status synchronously, updating both `trackingMode` and `isAutoPaused`.
4. *Localization Parity (`REQ-TRK-045.4`)*:
• All new string resources (`cockpit_status_not_started`, `cockpit_status_paused`) SHALL maintain complete translation parity across all 9 supported languages.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement `REQ-TRK-045`, amended by `ATT-5283`, extending `REQ-TRK-018` (AutoPauseHudBanner). Targets `TrackingTabsScreen.kt`, `SensorGridScreen.kt`, and `strings.xml`.
2. *Historical Origin & Commit Trace*: Ticket `ATT-4856`, Sprint `2026-44.1`. Follows `ATT-45` which added the small amber auto-pause pill. Sprint `2026-45.3` (`ATT-5283`) hoists the banner out of `SensorGridScreen` to a stationary floating overlay in `TrackingTabsScreen` across tabs (`currentPage > 0`) and changes amber warning colors to blue palette tokens.
3. *Root Reason for Existing Formulation*: Originally, `REQ-TRK-045.1` embedded the banner inside `SensorGridScreen` because each sensor tab was isolated. However, during horizontal swiping across tabs, the banner scrolled with the page and jumped vertically based on per-tab navigation hint visibility. Amber warning tokens were retained from `AutoPauseHudBanner`, but clash once `BikeBellButton` is styled in orange.
4. *Preservation of Core Invariants*: Intent action broadcasts (`REQUEST_START_TRACKING`, `REQUEST_RESUME_FROM_PAUSED`), opacity dimming `alpha = 0.6f` in `SensorGridScreen`, banner suppression on Control tab (`page == 0`) and in non-TRACKING modes, and TPMS banner clearance are 100% preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* the sensor grid tab is displayed in `ScreenMode.TRACKING` and tracking is `READY`,
• *When* the screen renders,
• *Then* the top HUD banner displays "Not Started" with a "Start" action button, and the grid is dimmed to `alpha = 0.6f`.
• *Given* the sensor grid tab is displayed in `ScreenMode.TRACKING` and tracking is manually `PAUSED`,
• *When* the screen renders,
• *Then* the top HUD banner displays "Paused" with a "Resume Now" action button, and the grid is dimmed to `alpha = 0.6f`.
• *Given* the sensor grid tab is displayed in `ScreenMode.TRACKING` and tracking is `TRACKING`,
• *When* the screen renders,
• *Then* no top HUD banner is displayed and the grid is at `alpha = 1.0f`.
• *Given* the screen is in `ScreenMode.CONFIGURATION` or `ScreenMode.PREVIEW`,
• *When* tracking is `READY` or `PAUSED`,
• *Then* no top HUD banner is displayed and the grid is at `alpha = 1.0f`.

**Invariants**: Live telemetry updates, zero hue shift in training zones, and 100% clean-room test pass rate MUST NOT be broken.


### REQ-TRK-046: Per-Sensor-Field Display Elements Configuration: Value and Bar Gating [Verified]
- **Tests**: `TST-TRK-046` | **Tickets**: `ATT-4841`, `ATT-4927`
- **Implementation**: `TrackingViewsDatabaseManager.java`, `TrackingViewsRepository.kt`, `SensorFieldConfig`, `SensorFieldState.kt`, `EditSensorFieldViewModel.kt`, `EditSensorFieldDialog.kt`, `SensorFieldView.kt`, `TrackingViewModel.kt`, `DatabaseSchemaRegistry.kt`
- **Rationale**: Cockpit sensor fields SHALL support granular, per-field configuration of which visual elements are rendered (numeric Value + Unit row vs. horizontal Bar), allowing athletes to customize tiles (e.g. bar-only, value-only, or both) independently for instantaneous vs. average metrics (ATT-4841, Parent Epic ATT-355, extending `REQ-UI-376`, `REQ-UI-377`, `REQ-UI-416`). Bar availability SHALL depend on both sensor type and layout sport type, tiles configured as bar-only SHALL gracefully fall back to displaying the numeric value if no bar can be drawn, and database migrations SHALL carry over the athlete's previous global `showGauge` setting per zone profile rather than forcing bars on existing fields (ATT-4927):
1. *SQLite Schema v18 & Migration Parity (`REQ-TRK-046.1`)*:
• `TrackingViewsDbHelper.ROWS_TABLE` SHALL be upgraded to `DB_VERSION = 18`.
• On database upgrade to v18, rows SHALL be joined with `VIEWS_TABLE` to evaluate `ActivityType` -> `BSportType`:
  - For `HR` and `POWER`, `ShowBar` SHALL be set based on the athlete's previous global preference (`SettingsDataStoreJavaHelper.getZoneDisplayOptions(context, zoneType).showGauge`) if the sport type supports zones (`HR` in `RUN`/`BIKE`, `POWER` in `BIKE`), and `0` otherwise.
  - For `PEDAL_POWER_BALANCE` and `W_PRIME_BAL`, `ShowBar` SHALL be set to `1` for `BSportType.BIKE`, and `0` otherwise.
  - For all other sensor types, `ShowBar` SHALL be set to `0`.
  - Whenever `ShowBar == 0`, `ShowValue` SHALL be enforced as `1`.
• `DatabaseSchemaRegistry.kt` SHALL reference `TrackingViewsDatabaseManager.TrackingViewsDbHelper.DB_VERSION` directly (Rule 62).
2. *Unified Sport-Dependent Bar Support & Dialog Constraints (`REQ-TRK-046.2`)*:
• `TrackingViewsDatabaseManager` SHALL provide a canonical predicate `isBarSupported(SensorType, BSportType)`:
  - `HR`: returns `true` for `BSportType.RUN` and `BSportType.BIKE`.
  - `POWER`, `PEDAL_POWER_BALANCE`, `W_PRIME_BAL`: returns `true` strictly for `BSportType.BIKE`.
  - All other combinations: return `false`.
• In `EditSensorFieldDialog` and `EditSensorFieldViewModel`, bar support SHALL be evaluated via `isBarSupported(selectedSensorType, activityType.sportType)`. If false, element toggles (Value/Bar) SHALL be hidden, and the field SHALL be saved as `showValue = true, showBar = false`.
• The system SHALL enforce that at least one visual element remains selected at all times (preventing simultaneous deselection of both Value and Bar).
3. *Cockpit Empty Tile Fallback (`REQ-TRK-046.3`)*:
• In `SensorFieldView.kt`, the numeric Value + Unit row SHALL be rendered if `fieldState.showValue == true || !hasActiveBar` (where `hasActiveBar = fieldState.showBar && (zoneGaugeState != null || pedalBalanceState != null || wPrimeBatteryState != null)`).
• Under no circumstances SHALL a cockpit sensor card render completely empty.
4. *Single Source of Truth & Global Toggle Deprecation (`REQ-TRK-046.4`)*:
• The global `showGauge` toggle row SHALL be removed from `ZonesSettingsActivity.kt`, making per-field `showBar` the single source of truth for cockpit gauge bars.
5. *9-Language Localization Parity (`REQ-TRK-046.5`)*:
• All new UI strings (`sensor_field_display_elements_title`, `sensor_field_show_value`, `sensor_field_show_bar`) SHALL maintain complete translation parity across all 9 supported languages.
6. *Sport-Gated Telemetry Flow Publishing (`REQ-TRK-046.6`)*:
• `TrackingViewModel` SHALL restrict `pedalBalanceState` and `wPrimeBatteryState` publishing strictly to `BSportType.BIKE`.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement `REQ-TRK-046`, extending `REQ-UI-376` (Zone Gauge Bar), `REQ-UI-377` (Pedal Balance Bar), and `REQ-UI-416` (W' Battery Gauge). Refined in `ATT-4927` for sport-dependent bar support, empty-tile fallback, and v18 upgrade parity.
2. *Historical Origin & Commit Trace*: `ATT-3574` (`showGauge` in `ZoneDisplayOptions`), `ATT-4841` (commit `050f1437`), `ATT-4861` (commit `e3713601`).
3. *Root Reason for Existing Formulation*: `ATT-4841` introduced per-field `ShowValue`/`ShowBar` in `v16`, but assumed sensor type alone determined bar capability and defaulted `ShowBar = 1` for all HR/Power fields, ignoring legacy `showGauge` and sport validity.
4. *Preservation of Core Invariants*: Mutual exclusion, layout stability, AMOLED compliance, zero empty tiles, and user settings preservation are 100% preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an existing user database upgrading to version 18 where legacy `showGauge` was `false` for `HR_BIKE`,
• *When* the database migration executes,
• *Then* `ShowBar` evaluates to `0` and `ShowValue` evaluates to `1` on existing HR fields in Bike layouts.
• *Given* an athlete configuring an HR sensor field in an "Other" / Generic sport layout,
• *When* `EditSensorFieldDialog` opens,
• *Then* Value and Bar toggles are hidden, and saving preserves `showValue = true, showBar = false`.
• *Given* a sensor field configured as bar-only (`showValue = false, showBar = true`),
• *When* the sport is not yet resolved or no bar state is available,
• *Then* `SensorFieldView` falls back to rendering the numeric value row, preventing an empty tile.

**Invariants**: Non-destructive SQLite migration, zero empty tiles, true zone color preservation, and 100% clean-room test pass rate.


### REQ-TRK-047: Cockpit Manual Altitude Calibration with Permanent Known Location Persistence and Retroactive Piecewise-Linear Drift Correction [Verified]
- **Tests**: `TST-TRK-047` | **Tickets**: `ATT-4845`, `ATT-5072`
- **Implementation**: `SensorSourceDialog.kt`, `SensorStatus.kt`, `AltitudeFromPressureDevice.java`, `TrackerService.java`, `LiveWorkoutSession.java`, `WorkoutSamplesDatabaseManager.java`, `WorkoutSummariesDatabaseManager.java`, `BANALServiceRepository.kt`, `KnownLocationsDatabaseManager.java`
- **Rationale**: When passing a landmark with known altitude during outdoor workouts, athletes SHALL be able to trigger manual altimeter calibration from the tracking cockpit altitude icon, permanently persist the landmark to Known Locations (`KnownLocations.db`), and apply retroactive elevation history correction without step discontinuities or false ascent/descent spikes (ATT-4845, Parent Epic ATT-166, extending `REQ-UI-199` and `REQ-CON-017`):
1. *Cockpit Header Manual Altitude Calibration & Geofence Persistence (`REQ-TRK-047.1`)*:
• When tapping the `SensorType.ALTITUDE` sensor icon in the tracking header (`SensorStatus`), `SensorSourceDialog.kt` SHALL render a dedicated "Calibrate Altitude" input section.
• The reference altitude input SHALL pre-fill with the current altitude reading formatted in the athlete's configured unit (`m` or `ft`) via canonical `AltitudeFormatter` per Rule 36.
• When not at a matched known location (`matchedKnownLocation == null`):
  - The "Als Lieblingsort speichern" (*Save as Known Location*) checkbox SHALL be unchecked (`false`) by default (ATT-5072).
  - The location name text input field SHALL be conditionally displayed and enabled only when the "Save as Known Location" checkbox is checked (`true`).
  - When confirmed with save unchecked, the system SHALL dispatch altimeter calibration directly without persisting to `KnownLocations.db`.
  - When confirmed with save checked, the system SHALL persist `(current LatLng, referenceAltitudeMeters, name)` to `KnownLocationsDatabaseManager` with `ElevationSource.MANUAL_USER` and `isLocked = true`.
• When at a matched known location (`matchedKnownLocation != null`):
  - The dialog SHALL display the matched location name.
  - The redundant "Save as Known Location" checkbox and new location name input field SHALL be hidden.
  - The reference altitude input SHALL pre-fill with the matched location's stored reference altitude.
  - When confirmed, the system SHALL update the existing `KnownLocations.db` record with the calibrated elevation (`ElevationSource.MANUAL_USER`, `isLocked = true`) and dispatch altimeter calibration.
• The system SHALL dispatch immediate calibration to `AltitudeFromPressureDevice.calibrate(referenceAltitudeMeters)` via `BANALServiceRepository.calibrateAltimeter`.
2. *Retroactive Workout History Elevation Correction (`REQ-TRK-047.2`)*:
• Let $t_1$ be the current calibration timestamp, $h_{\text{ref}}$ the target reference elevation, $h_{\text{baro}}(t_1)$ the pre-calibration altitude reading, and $\Delta h = h_{\text{ref}} - h_{\text{baro}}(t_1)$.
• *Case A — Previously Uncalibrated in This Workout ($t_0 = \text{none}$)*:
  - The system SHALL shift all already-recorded altitude samples in $t \in [t_{\text{start}}, t_1]$ across `WorkoutSamples.db` (or SQLite `samplesTable`), `LiveWorkoutSession` (`sampledAltitudes`, `RunningStats`), and `BANALServiceRepository._currentPathPoints` uniformly by $+\Delta h$:
    $$h_{\text{corr}}(t) = h(t) + \Delta h$$
• *Case B — Previously Calibrated at Timestamp $t_0 < t_1$*:
  - The system SHALL apply a linear interpolation of $\Delta h$ over time across all recorded samples in $t \in [t_0, t_1]$:
    $$h_{\text{corr}}(t) = h(t) + \Delta h \cdot \frac{t - t_0}{t_1 - t_0}$$
    so the correction is $0$ at previous calibration point $t_0$ and reaches $+\Delta h$ at $t_1$, eliminating barometric weather drift without step discontinuities or false ascent/descent spikes.
• After applying either Case A or Case B, the system SHALL update $t_0 \leftarrow t_1$ and recompute running `ASCENDING` / `DESCENDING` and min-max altitude extrema over the corrected interval.
3. *IPC Contract & Drift Protection (`REQ-TRK-047.3`)*:
• `ALTITUDE_CORRECTION_INTENT` SHALL carry `ALTITUDE_CORRECTION_VALUE = deltaOffset` and timestamp $t_1$ (or `TrackerService` captures $t_1$ atomically upon receipt), enforcing $|\Delta h| \le 500\text{ m}$ guard pursuant to `REQ-CON-017`.
• `_currentPathPoints` in `BANALServiceRepository` SHALL update existing `PathPoint` elevations accordingly to ensure live elevation profile continuity.
4. *9-Language Localization Parity (`REQ-TRK-047.4`)*:
• All manual altitude calibration strings (`altitude_calibration_title`, `altitude_calibration_reference_label`, `altitude_calibration_location_name`, `altitude_calibration_save_location`, `altitude_calibration_action`, `altitude_calibrated_success`) SHALL maintain 100% translation parity across all 9 supported application locales.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement `REQ-TRK-047`, extending `REQ-UI-199` (Cockpit Calibration Badge & Altimeter Dispatch) and `REQ-CON-017` (Barometric Calibration Limiting & IPC). Targets `SensorSourceDialog.kt`, `SensorStatus.kt`, `AltitudeFromPressureDevice.java`, `TrackerService.java`, `LiveWorkoutSession.java`, `WorkoutSamplesDatabaseManager.java`, and `BANALServiceRepository.kt`.
2. *Historical Origin & Commit Trace*: Sprint 2026-44.2 (`ATT-4845`). Builds upon `ATT-1617` (geofence auto-calibration), `ATT-1810` (delta offset IPC contract), and `ATT-3990` (500m sanity limit).
3. *Root Reason for Existing Formulation*: Prior implementations only supported automatic calibration at pre-saved favorite locations or applied uniform sample shifts across the entire workout, distorting calibrated starting elevations and failing to correct gradual atmospheric pressure drift.
4. *Preservation of Core Invariants*: Preservation of `LocationCalibrationBadge` placement, 9-language localization, imperial/metric unit formatting, single-thread SQLite concurrency in `mDbExecutor`, and `ALTITUDE_CORRECTION_INTENT` $|\Delta h| \le 500\text{ m}$ guard.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an active tracking session with a valid GPS fix,
• *When* the athlete taps the ALTITUDE icon in the header and submits a reference altitude (e.g. 1215 m) with save enabled,
• *Then* the barometric altimeter immediately calibrates to 1215 m and the location is permanently stored in `KnownLocations.db` with `ElevationSource.MANUAL_USER`.
• *Given* a workout that started uncalibrated and recorded samples for 20 minutes before the athlete calibrates with offset $\Delta h = +35\text{ m}$,
• *When* the calibration is applied,
• *Then* all past altitude samples in `WorkoutSamples.db`, `LiveWorkoutSession`, and `_currentPathPoints` are shifted by $+35\text{ m}$.
• *Given* a workout previously calibrated at $t_0$ and recalibrated at $t_1 > t_0$ with accumulated drift $\Delta h = -18\text{ m}$,
• *When* the second calibration is applied at $t_1$,
• *Then* recorded samples between $t_0$ and $t_1$ are adjusted linearly by $\Delta h \cdot \frac{t - t_0}{t_1 - t_0}$, with $0\text{ m}$ change at $t_0$ and $-18\text{ m}$ at $t_1$.

**Invariants**: $C^0$ continuity (zero vertical step discontinuities), Rule 36 unit parity, single-thread SQLite transaction execution, and 100% clean-room test pass rate.


### REQ-SEN-025: Pairable Position-Based Elevation Sensor & Dedicated Spatial DEM Cache [Verified]
- **Tests**: `TST-SEN-025` | **Tickets**: `ATT-4859`, `ATT-5199`, `ATT-5312`
- **Implementation**: `AltitudeFromPositionDevice.java`, `DeviceType.java`, `ElevationCacheDatabaseManager.kt`, `DeviceManager.java`, `DevicesDatabaseManager.java`, `SensorSourceDialog.kt`, `MySensorManager.java`, `DatabaseSchemaRegistry.kt`, `WorkoutRepository.kt`, `ElevationSmoothingMath.kt`
- **Rationale**: Currently, `SensorType.ALTITUDE` is supplied either by internal barometric pressure (`AltitudeFromPressureDevice`) or raw GNSS altitude ($\pm 20\text{--}50\text{ m}$ vertical GPS multipath noise). Introducing a pairable `AltitudeFromPositionDevice` (`DeviceType.ALTITUDE_FROM_POSITION`, `Protocol.SMARTPHONE`) backed by a dedicated spatial cache (`ElevationCache.db`) provides continuous topographic/DEM altitude based on GPS coordinates, seeded from route elevations (`Routes.db`) and Open-Meteo DEM queries (`ElevationService`), strictly separated from *Lieblingsorte* (`KnownLocations.db`) (ATT-4859, Parent Epic ATT-166):
1. *Device Registration & Smartphone Pairing Lifecycle (`REQ-SEN-025.1`)*:
• The system SHALL register `DeviceType.ALTITUDE_FROM_POSITION` under `Protocol.SMARTPHONE` with main sensor type `SensorType.ALTITUDE`.
• The system SHALL manage pairing via `DevicesDatabaseManager` and instantiate/destroy `AltitudeFromPositionDevice` dynamically in `DeviceManager` based on pairing state.
• The system SHALL support selecting `ALTITUDE_FROM_POSITION` in `SensorSourceDialog` for `SensorType.ALTITUDE`.
2. *Dedicated Spatial Elevation Cache Database & Source Priority (`REQ-SEN-025.2`)*:
• The system SHALL persist elevation points in a dedicated SQLite database `ElevationCache.db` managed by `ElevationCacheDatabaseManager` (`DB_VERSION = 1`), registered with `DatabaseSchemaRegistry` per Rule 62.
• Table `elevation_cache` SHALL store quantized coordinates `(lat_e5 INT, lon_e5 INT, altitude REAL, source TEXT, updated_at INT)` with unique compound index on `(lat_e5, lon_e5)` for $O(\log N)$ spatial queries.
• The system SHALL enforce a strict source priority hierarchy: `DEM_CACHE` (priority 30) > `ROUTE_SEED` (priority 20) > `SEGMENT_SEED` (priority 10).
• `putBatchElevations()` for lower-priority sources (`ROUTE_SEED`, `SEGMENT_SEED`) SHALL use `CONFLICT_IGNORE` so authoritative `DEM_CACHE` entries are NEVER overwritten by seeded trackpoints.
• Seeding via `seedFromRoutes()` SHALL update existing `SEGMENT_SEED` entries to `ROUTE_SEED`, while `seedFromSegments()` SHALL NEVER overwrite existing `ROUTE_SEED` or `DEM_CACHE` entries.
• Queries SHALL support nearest-neighbor matching within spatial tolerance ($\approx 20\text{ m}$, $\Delta\text{E5} \le 2$) via `getElevation()` and `getElevationItem()`.
3. *Background Route Seeding & Asynchronous Internet DEM Caching (`REQ-SEN-025.3`)*:
• The system SHALL asynchronously seed `ElevationCache.db` with valid trackpoint elevations from stored routes (`Routes.db`) and segments (`Segments.db`) without network overhead using non-destructive conflict resolution.
• When a location fix misses the local cache, `AltitudeFromPositionDevice` SHALL asynchronously query `ElevationService.getInstance().fetchElevation(lat, lon)` (Open-Meteo DEM), cache the result in `ElevationCache.db` with source `DEM_CACHE`, and emit the resolved altitude to `mAltitudeSensor`.
• When a location fix matches a cached non-authoritative entry (`source != DEM_CACHE`), `AltitudeFromPositionDevice` SHALL immediately emit the cached altitude for zero-latency offline display AND SHALL asynchronously query `ElevationService` when online to upgrade the cached coordinate to `DEM_CACHE`.
• Asynchronous DEM queries SHALL be protected by an atomic in-flight guard flag (`AtomicBoolean`) to prevent executor task queue congestion during network outages or high-frequency location fixes.
4. *Strict Decoupling from Known Locations (*Lieblingsorte*) (`REQ-SEN-025.4`)*:
• Background position tracking and DEM queries SHALL NEVER insert, alter, or reconcile entries in `KnownLocations.db` (`KnownLocationsDatabaseManager`), preserving athlete favorite landmarks and manual calibrations.
5. *9-Language Localization Parity (`REQ-SEN-025.5`)*:
• All `ALTITUDE_FROM_POSITION` device strings (`device_type_altitude_from_position`, `device_type_short_altitude_from_position`, `devices_altitude_from_position`, `devices_altitude_from_position_description`) SHALL maintain 100% translation parity across all 9 supported locales.
6. *2D Route/Segment Zero Elevation Rejection & Self-Healing Purge (`REQ-SEN-025.6`)*:
• The system SHALL filter out trackpoints from routes or segments where `abs(elevation) < 1e-4` or where all points in the route/segment have zero elevation, preventing 2D dummy coordinates from being inserted as `ROUTE_SEED` or `SEGMENT_SEED` into `ElevationCache.db`.
• The cache manager SHALL execute a self-healing purge (`purgeCorruptZeroSeeds()`) on initialization and background seeding to delete existing corrupt zero-elevation records (`altitude == 0.0 AND source IN ('ROUTE_SEED', 'SEGMENT_SEED')`) while preserving genuine sea-level points and authoritative `DEM_CACHE` records.
7. *Neighbor Seed DEM Upgrade & Anti-Poisoning (`REQ-SEN-025.7`)*:
• When `AltitudeFromPositionDevice` resolves an altitude from a non-authoritative neighbor seed cell (`ROUTE_SEED` or `SEGMENT_SEED` where `cachedItem.latE5 != latE5 || cachedItem.lonE5 != lonE5`), upon successful completion of the asynchronous DEM fetch (`fetchDemElevationAsync`), the system SHALL update both the current position `(latE5, lonE5)` AND the matched neighbor seed cell to the authoritative `demElevation` with source `DEM_CACHE` (or purge the corrupt seed cell), preventing repetitive re-poisoning on subsequent passes.
8. *Telemetry Null-Altitude Parity & Outlier Smoothing (`REQ-SEN-025.8`)*:
• `WorkoutRepository.loadWorkoutDetailTelemetry` SHALL extract missing altitude samples as `null` (`if (altIdx != -1 && !cursor.isNull(altIdx)) cursor.getDouble(altIdx) else null`), maintaining symmetry with `getWorkoutTrackPoints` and `getWorkoutTelemetryPoints`.
• `ElevationSmoothingMath.smoothAltitudes` and `calculateElevationBounds` SHALL evaluate the workout's median altitude (`medianAlt`); when `medianAlt > 50.0` m, isolated dropouts where `abs(alt) < 1e-4` SHALL be treated as missing/invalid and interpolated across surrounding terrain, while workouts at true sea level (`medianAlt <= 50.0` m) SHALL preserve `0.0 m` samples.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: `REQ-SEN-025.2`, `REQ-SEN-025.3`, `REQ-SEN-025.6`, `REQ-SEN-025.7`, `REQ-SEN-025.8`. Targets `ElevationCacheDatabaseManager.kt`, `AltitudeFromPositionDevice.java`, `WorkoutRepository.kt`, and `ElevationSmoothingMath.kt`.
2. *Historical Origin & Commit Trace*: Sprint 2026-44.2 (`ATT-4859`, commit `a11e13d7`) & Sprint 2026-45.2 (`ATT-5199`, commit `fa3c161b`); amended in Sprint 2026-45.4 (`ATT-5312`).
3. *Root Reason for Existing Formulation*: Route/segment seeding previously accepted any non-null elevation, allowing imported 2D routes with dummy 0.0m points to poison the cache as `ROUTE_SEED`. Neighbor tolerance matching hit poisoned seeds without upgrading the neighbor cell upon DEM resolution. Additionally, `loadWorkoutDetailTelemetry` defaulted missing altitude to `0.0` instead of `null`, preventing Loess smoothing interpolation and causing 0.0m profile drops.
4. *Preservation of Core Invariants*: Valid coastal workouts at true sea level (`medianAlt <= 50.0` m) preserve `0.0 m` values. Authoritative `DEM_CACHE` entries are never purged or overwritten by seeded points. Offline zero-latency seed emission is preserved while corrupt zero-seeds are self-healed.

**Acceptance Criteria (Given-When-Then)**:
• *Given* the Smartphone devices list in Settings/Devices,
• *When* the user pairs `AltitudeFromPositionDevice`,
• *Then* `AltitudeFromPositionDevice` initializes and emits continuous topographic elevation derived from `ElevationCache.db` or `ElevationService`.
• *Given* stored routes with elevation points in `Routes.db`,
• *When* the cache indexer executes,
• *Then* route trackpoints are indexed into `ElevationCache.db` and queryable offline without network calls.
• *Given* imported 2D routes with dummy 0.0m elevations,
• *When* `seedFromRoutes()` runs,
• *Then* zero-elevation dummy points are rejected and never inserted into `ElevationCache.db`.
• *Given* an asynchronous DEM query successfully resolves after matching a neighbor seed point,
• *When* `fetchDemElevationAsync` completes,
• *Then* the matched neighbor seed cell is updated with authoritative `DEM_CACHE` elevation.
• *Given* workout telemetry points where GPS fix exists but altitude sensor is pending,
• *When* `loadWorkoutDetailTelemetry` is called,
• *Then* altitude returns `null` and is interpolated smoothly across the terrain by `ElevationSmoothingMath`.
• *Given* continuous position tracking across a novel workout route,
• *When* `ElevationCache.db` is queried and updated,
• *Then* zero records are inserted into or modified within `KnownLocations.db`.

**Invariants**: Zero mutation of `KnownLocations.db`, non-blocking I/O (`Dispatchers.IO`), Rule 62 schema registry integration, authentic sea-level 0.0m preservation, and 100% clean-room test pass rate.


### REQ-TRK-048: Stationary GPS Jitter Suppression, Doppler Zero Preservation & Anchor Pinning [Verified]
- **Tests**: `TST-TRK-048` | **Tickets**: `ATT-4850`
- **Implementation**: `SpeedAndLocationDevice.java`, `BANALServiceRepository.kt`
- **Rationale**: When an athlete stops at traffic lights, cafés, or rests indoors, GNSS multipath reflections cause horizontal coordinates to wander 1–4 m per second. Overriding GNSS Doppler zero speed with geometric displacement creates phantom 5–14 km/h speed readings, prevents or breaks Auto-Pause, accumulates creeping distance, spins compass bearing, and records dense spiderweb track artifacts (ATT-4850, Parent Epic ATT-166):
1. *Doppler Zero Velocity Preservation (`REQ-TRK-048.1`)*:
• When a location fix provides speed (`location.hasSpeed() == true`) and `location.getSpeed() <= SPEED_ZERO_THRESHOLD` (0.05 m/s), `SpeedAndLocationDevice` SHALL clamp speed to 0.0 m/s and SHALL NOT calculate or substitute geometric speed `delta_distance / dt` from GPS multipath jitter.
2. *Stationary Anchor Pinning & Distance Gating (`REQ-TRK-048.2`)*:
• When stationary (`speed <= SPEED_ZERO_THRESHOLD`), `SpeedAndLocationDevice` SHALL establish and maintain a stationary anchor coordinate.
• While stationary, `SpeedAndLocationDevice` SHALL NOT increment `mDistanceSensor` (`DISTANCE_m`) or `mLapDistanceSensor` (`DISTANCE_m_LAP`) until displacement from the anchor exceeds the dynamic accuracy deadband radius $R = \max(3.0\text{ m},\, 0.6 \cdot \text{accuracy})$.
3. *Stationary Bearing Lock & Coordinate Stabilization (`REQ-TRK-048.3`)*:
• While pinned within the stationary deadband radius, `SpeedAndLocationDevice` SHALL maintain the last valid bearing without angular drift or spinning, and SHALL publish the stabilized anchor coordinates.
4. *Provider Without Doppler Velocity Fallback (`REQ-TRK-048.4`)*:
• For location providers without Doppler velocity (`!location.hasSpeed()`), `SpeedAndLocationDevice` SHALL compute geometric speed `delta_distance / dt` and accumulate distance if and only if displacement exceeds the accuracy deadband radius $R = \max(3.0\text{ m},\, 0.6 \cdot \text{accuracy})$.
5. *Spider-Map Breadcrumb Suppression (`REQ-TRK-048.5`)*:
• `BANALServiceRepository` SHALL suppress appending coordinate breadcrumbs to `_currentTrack` when the athlete is stationary and displacement remains within the stationary deadband radius.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement `REQ-TRK-048`, extending `REQ-TRK-013` (Speed Decay Watchdog), `REQ-TRK-024` (Speed & Distance Priority), and `REQ-TRK-041.1` (Teleportation Spike Gating). Targets `SpeedAndLocationDevice.java` and `BANALServiceRepository.kt`.
2. *Historical Origin & Commit Trace*: Sprint 2026-44.2 (`ATT-4850`). Addresses longstanding issue from Epic `ATT-166` (High-Value Pillar 1: Stationary Pinning / Zero-Velocity Update).
3. *Root Reason for Existing Formulation*: `SpeedAndLocationDevice` previously used a simplistic `speed == 0.0 && delta_distance > 0.1` condition that mistook GPS multipath noise for motion, designed prior to modern GNSS Doppler baseband firmware.
4. *Preservation of Core Invariants*: Preservation of speed decay watchdog timeout (`REQ-TRK-013`), plausible teleportation speed limits $\le 70\text{ m/s}$ (`REQ-TRK-041.1`), and high-speed cornering responsiveness without lag.

**Acceptance Criteria (Given-When-Then)**:
• *Given* consecutive 1-Hz GPS fixes with `hasSpeed() == true`, `getSpeed() == 0.0f`, and horizontal coordinates jittering by 1–4 m within reported accuracy,
• *When* `SpeedAndLocationDevice.onNewLocation()` processes the fixes,
• *Then* published `SPEED_mps` remains 0.0, `DISTANCE_m` does not increment, `BEARING` does not spin, and published coordinates remain pinned to the anchor without drawing a spider-map.
• *Given* location fixes from a provider without Doppler speed (`hasSpeed() == false`) advancing steadily along a route beyond the deadband radius,
• *When* `SpeedAndLocationDevice.onNewLocation()` processes the fixes,
• *Then* geometric speed `delta_distance / dt` and distance increments are computed accurately.
• *Given* an athlete stationary at an intersection resuming movement,
• *When* displacement from the anchor exceeds $\max(3.0\text{ m},\, 0.6 \cdot \text{accuracy})$ with non-zero speed,
• *Then* the anchor unpins immediately, distance accumulation resumes, and bearing updates along the direction of motion.

**Invariants**: Zero distance creep while stationary, valid non-Doppler geometric fallback, preservation of speed decay watchdog and teleportation spike gating, 100% clean-room test pass rate.


### REQ-TRK-049: Post-Workout State Cleansing, Breadcrumb Purge & Idle Service Dormancy [Verified]
- **Tests**: `TST-TRK-049` | **Tickets**: `ATT-4926`, `ATT-5080`, `ATT-5191`
- **Implementation**: `TrainingApplication.java`, `BANALServiceRepository.kt`, `MainActivityWithNavigation.kt`, `ProxySensor.java`, `MyAccumulatorSensor.java`, `InstantaneousFilter.java`, `FilterManager.java`, `DefaultStringFormatter.java`, `SensorData.java`, `TrackingViewModel.kt`
- **Rationale**: When an athlete completes or stops a workout and re-enters the app in `TrackingMode.READY`, lingering historical polylines, elevation profiles, and accumulator values create a confusing UI and cause phantom metric accumulation during idle periods. Clearing breadcrumbs on workout stop/abort, normalizing the service binding lifecycle, gating accumulator sensors to active tracking, dispatching zero-value resets, re-seeding accumulator filters from baseline values, and guarding against null formatted strings ensures a clean, crash-free slate for upcoming sessions (ATT-4926, ATT-5080, ATT-5191, Parent Epic ATT-355, extending `REQ-TRK-001`, `REQ-TRK-002`, and refining `REQ-FIL-013`):
1. *Map & Elevation Breadcrumb Cleansing (`REQ-TRK-049.1`)*:
• `TrainingApplication` SHALL invoke `BANALServiceRepository.clearBreadcrumbs()` in `stopTracking()`, `trackingStopped()`, and `onTrackingAborted()`.
• `BANALServiceRepository` SHALL clear `_currentTrack` and `_currentPathPoints` upon entering `TrackingMode.READY`.
2. *Idle Service Binding Lifecycle Normalization (`REQ-TRK-049.2`)*:
• In `MainActivityWithNavigation.kt`, `bindToBANALService()` on resume SHALL be paired with unbind handling, or `disconnectFromBANALService()` SHALL force unbinding when `bindReferenceCount > 0`, ensuring `BANALService` stops cleanly when idle without lingering background timers.
3. *Idle Accumulator Gating (`REQ-TRK-049.3`)*:
• In `MyAccumulatorSensor.increment()`, when `mRespectPause` is true, the sensor SHALL accumulate only if `TrainingApplication.isTracking() && !isPaused() && !isAutoPaused()`.
• Timer and distance sensors SHALL NOT increment while in `TrackingMode.READY`.
4. *Reset Notification & Filter Cache Invalidation (`REQ-TRK-049.4`)*:
• `MyAccumulatorSensor.reset()` SHALL notify all registered `mSensorListeners` with `mZeroValue`.
• `InstantaneousFilter.java` SHALL override `reset()` to invalidate cached telemetry on `FilterManager.resetAllFilters()`.
• In `FilterManager.resetAllFilters()`, the system SHALL unwrap any `ProxySensor` (via `getSourceSensor()`) to its underlying source sensor. When the sensor is a `MyAccumulatorSensor`, the filter SHALL be re-seeded directly with `accumulator.getZeroValue()` (e.g. `0` -> `0:00:00`, `0.00 km`, `1` for `LAP_NR`), ensuring accumulator fields retain their baseline zero values regardless of broadcast receiver execution order between `FilterManager` and `MyDevice` (ATT-5080, ATT-5191).
5. *Non-Null Formatter Fallback & Null-Safety Defenses (`REQ-TRK-049.5`)*:
• `DefaultStringFormatter.format(String value)` SHALL return `"--"` instead of `null` when `value == null`, aligning with numeric formatters.
• In `SensorData.java`, `getStringValue()` SHALL return `"--"` when `mStringValue` is null.
• In `TrackingViewModel.applySensorData()`, `newFormattedValue` SHALL guard against Java nulls (`sensorData.stringValue ?: "--"`), preventing fatal `NullPointerException` crashes during Compose state updates (ATT-5080).

### Requirement Archaeology & Chesterton's Fence Audit
1. *Original Requirement ID & Target*: Amends `REQ-TRK-049.4` (*Reset Notification & Filter Cache Invalidation*) and `REQ-FIL-013` (*Session Reset Isolation*), Parent Epic `ATT-355`. Targets `ProxySensor.java`, `MyAccumulatorSensor.java`, `FilterManager.java`, `DefaultStringFormatter.java`, `SensorData.java`, and `TrackingViewModel.kt`.
2. *Historical Origin & Commit Trace*: Traced to `080d68c3` (ATT-4926, Sprint 2026-44.2) and `0087756f` (ATT-5080, Sprint 2026-45.1). ATT-4926 introduced `InstantaneousFilter.reset() { mValue = null; }`, and ATT-5080 introduced accumulator re-seeding in `resetAllFilters()`, but tested directly against `MyDoubleAccumulatorSensor` mocks without unwrapping `ProxySensor` or accounting for broadcast receiver execution ordering.
3. *Root Reason for Existing Formulation*: ATT-5080 aimed to prevent stale telemetry leaks by restricting re-seeding to `MyAccumulatorSensor`, but overlooked wrapper composition (`ProxySensor` extends `MySensor`, not `MyAccumulatorSensor`) and broadcast listener sequencing between `FilterManager` and `MyDevice`.
4. *Preservation of Core Invariants*: Stale live telemetry from disconnected sensors continues to clear to `--`; accumulators reliably display baseline zeroes (`0:00:00`, `0.00 km`, `1`) in `READY` mode; zero NPE crashes on Compose state updates.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an active workout with map breadcrumbs and elevation points,
• *When* the workout is stopped or aborted, or when the app enters `TrackingMode.READY`,
• *Then* `_currentTrack` and `_currentPathPoints` are cleared to empty lists.
• *Given* the app running in `TrackingMode.READY`,
• *When* background timer ticks or location updates arrive,
• *Then* accumulator sensors do not increment.
• *Given* an active workout with accumulator sensors running (direct or `ProxySensor`-wrapped "Best Sensor"),
• *When* the workout is stopped and `resetAllFilters()` executes,
• *Then* accumulator fields display baseline zero values (`0:00:00`, `0.00 km`, `1`) in `TrackingMode.READY` instead of `--:--:--`, regardless of broadcast receiver execution order.
• *Given* string-valued sensor data with null value during filter reset,
• *When* `applySensorData()` processes the update in `TrackingViewModel`,
• *Then* `newFormattedValue` falls back to `"--"` and Compose state updates proceed without `NullPointerException`.

**Invariants**: Unaffected active tracking and auto-pause, continuous pre-start live rolling sensor display (HR, power, cadence), and 100% clean-room test pass rate.


### REQ-TRK-050: Indirect Tire Pressure Monitoring (iTPMS) via GPS vs. Wheel Circumference Drift [Verified]
- **Tests**: `TST-TRK-050` | **Tickets**: `ATT-4880`
- **Implementation**: `IndirectTpmsEstimator.kt`, `DevicesDatabaseManager.java`, `DeviceData.kt`, `EditDeviceViewModel.kt`, `SensorGridScreen.kt`
- **Rationale**: When riding with a paired ANT+/BLE wheel speed sensor alongside GNSS, comparing 3D ground distance with wheel revolutions yields the effective rolling circumference $C_{\text{eff}} = \Delta s_{3D} / \Delta N_{\text{wheel}}$. Air deflation increases vertical tire deflection under load, reducing $C_{\text{eff}}$ by $1.0\%\text{--}2.2\%$. Tracking $C_{\text{eff}}$ across straight-line, good-GPS segments enables early warnings for slow punctures and reminders for multi-day low tire pressure without requiring valve-stem hardware sensors (ATT-4880, Parent Epic ATT-166, complementing `REQ-CON-009`, `REQ-TRK-024`, and `REQ-TRK-048`):
1. *Straight-Line, 3D-Slope & GNSS Quality Gating (`REQ-TRK-050.1`)*:
• `IndirectTpmsEstimator` SHALL accumulate $(\Delta s_{3D}, \Delta N_{\text{wheel}})$ sample windows ONLY when:
  a. $|\Delta\text{bearing}/\Delta t| < 3.0^\circ/\text{s}$ (straight-line riding; rejects corner-cutting and lean-angle distortion).
  b. Horizontal GNSS accuracy $\le 6.0\text{ m}$ and speed $\ge 4.0\text{ m/s}$ ($14.4\text{ km/h}$; rejects stationary wander and stop-and-go noise).
  c. 3D ground distance correction $\Delta s_{3D} = \sqrt{\Delta s_{2D}^2 + \Delta h^2}$ is applied using barometric/fused elevation $\Delta h$.
2. *Ride-Start Baseline Calibration & Multi-Day Low Pressure Reminder (`REQ-TRK-050.2`)*:
• `IndirectTpmsEstimator` SHALL lock the ride-start baseline circumference $C_{\text{start}}$ after accumulating between $1.5\text{--}2.5\text{ km}$ ($2.0\text{ km}$ target) of gated straight-line riding.
• If a stored freshly-pumped reference baseline $C_{\text{baseline}} > 0$ exists and $C_{\text{start}} / C_{\text{baseline}} \le 1 - 0.006$ ($-0.6\%$), the system SHALL surface a low tire pressure reminder.
3. *Real-Time Sliding-Window CUSUM Slow Puncture Detection (`REQ-TRK-050.3`)*:
• `IndirectTpmsEstimator` SHALL compute a rolling effective circumference $C_{\text{short}}(t)$ over an exponentially weighted / sliding $2.0\text{ km}$ window of gated riding, and execute a one-sided CUSUM change-point detector against $C_{\text{start}}$.
• When cumulative negative drift reaches $C_{\text{short}} / C_{\text{start}} \le 1 - 0.010$ ($-1.0\%$), the system SHALL trigger an in-ride slow puncture warning.
4. *Persistent Fresh-Pump Baseline & Device Configuration Action (`REQ-TRK-050.4`)*:
• `DevicesDatabaseManager` SHALL store `baselineCircumference REAL DEFAULT 0.0` in `Devices.db` (migrated to `DB_VERSION = 8`).
• `EditDeviceDialog` / `EditDeviceViewModel` SHALL provide an action "Reset Tire Pressure Baseline (Just Pumped Tires)" that updates $C_{\text{baseline}}$ to the current wheel circumference calibration value.
5. *Design System Semantic Alert Containers & 9-Language Parity (`REQ-TRK-050.5`)*:
• The UI SHALL render low pressure reminders using amber warning containers (`LightWarningContainer` / `DarkWarningContainer`) and in-ride slow puncture warnings using error red containers (`MaterialTheme.colorScheme.errorContainer`), localized symmetrically across all 9 supported application locales.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement `REQ-TRK-050`, extending `REQ-CON-009` (Per-Device Wheel Circumference), `REQ-TRK-024` (Speed & Distance Priority), and `REQ-TRK-048` (Stationary Pinning). Targets `IndirectTpmsEstimator.kt`, `DevicesDatabaseManager.java`, `DeviceData.kt`, `EditDeviceViewModel.kt`, and `SensorGridScreen.kt`.
2. *Historical Origin & Commit Trace*: Sprint 2026-44.2 (`ATT-4880`). Epic `ATT-166` (High-Value Pillar 2: ANT+/BLE Wheel Sensor + GPS Auto-Calibration).
3. *Root Reason for Existing Formulation*: Prior to ATT-4880, GNSS distance and wheel revolutions operated in isolated silos without dynamic effective rolling circumference estimation or puncture detection.
4. *Preservation of Core Invariants*: Manual wheel circumference calibration factor (`CALIBRATION_FACTOR`), hardware wheel distance accumulation (`mDistanceSensor.accumulate(...)`), and sensor priority rankings remain 100% unaltered.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an active cycling workout with paired ANT+/BLE wheel speed sensor and GPS where $C_{\text{start}}$ calibrates to $2128\text{ mm}$,
• *When* $C_{\text{short}}(t)$ on straight, good-GPS segments drops progressively by $\ge 1.0\%$ ($\le 2106\text{ mm}$) over $2\text{--}3\text{ km}$,
• *Then* the in-ride slow puncture warning triggers, whereas tight switchbacks ($|\Delta\text{bearing}/\Delta t| \ge 3^\circ/\text{s}$) or degraded GPS ($\text{accuracy} > 6\text{ m}$) are gated out and do not trigger false alarms.
• *Given* a stored freshly-pumped reference baseline $C_{\text{baseline}} = 2130\text{ mm}$,
• *When* a new ride's initial calibrated circumference $C_{\text{start}}$ stabilizes at $\le 2115\text{ mm}$ ($-0.7\%$),
• *Then* the app surfaces a non-intrusive low tire pressure reminder.
• *Given* an athlete in the Wheel Sensor configuration dialog,
• *When* tapping "Reset Tire Pressure Baseline (Just Pumped Tires)",
• *Then* $C_{\text{baseline}}$ is saved to `Devices.db` matching the current wheel circumference.

**Invariants**: Zero mutation of raw wheel speed and distance accumulators, fresh install (`onCreate`) vs upgrade (`onUpgrade`) schema parity in `Devices.db`, zero cornering false positives, and 100% clean-room test pass rate.


### REQ-SEN-026: Specialized Altitude Devices: Barometric Altimeter Naming Parity, Idempotent SQLite Migration & Educational Operation Details [Verified]
- **Tests**: `TST-SEN-026` | **Tickets**: `ATT-5073`, `ATT-5198`
- **Implementation**: `strings_devices.xml` (all 9 locales), `strings.xml` (all 9 locales), `DevicesDatabaseManager.java`, `EditDeviceDialog.kt`
- **Rationale**: Following the introduction of `ALTITUDE_FROM_POSITION` ("Topografische Höhe (DEM)"), the internal barometric altimeter (`ALTITUDE_FROM_PRESSURE`) remained named simply "Luftdruck" ("Air Pressure") via `R.string.devices_altitude_from_pressure`, creating a naming asymmetry and confusing athletes about what the sensor produces. Furthermore, athletes in the device details bottom sheet (`EditDeviceDialog.kt`) have no visibility into how specialized altitude devices work or their respective advantages and disadvantages (e.g. barometer smooth relative ascent vs. weather drift; DEM weather immunity vs. grid resolution) (ATT-5073, Parent Epic ATT-166, complementing `REQ-SEN-025` and `REQ-UI-149`):
1. *Barometric Altimeter Naming Parity Across 9 Locales (`REQ-SEN-026.1`)*:
• The system SHALL rename `devices_altitude_from_pressure` across all 9 supported application locales to reflect its functional role as an altitude sensor ("Höhe aus Luftdruck" in DE, "Altitude from Pressure" in EN, and equivalents in ES, FR, IT, JA, NL, PL, PT).
2. *Idempotent SQLite Default Name Migration (`REQ-SEN-026.2`)*:
• In `DevicesDatabaseManager`, the system SHALL execute an idempotent migration updating existing `ALTITUDE_FROM_PRESSURE` records in `Devices.db` whose name matches any known legacy default name ("Luftdruck", "Air Pressure", etc.) to the new localized string resource.
• Any custom user-edited device name MUST NOT be overwritten or modified.
3. *Educational Operation & Trade-Offs Details in EditDeviceDialog (`REQ-SEN-026.3`)*:
• When viewing `EditDeviceDialog.kt` for `DeviceType.ALTITUDE_FROM_PRESSURE` or `DeviceType.ALTITUDE_FROM_POSITION`, the dialog SHALL display an educational `OutlinedCard` detailing:
  a. "How it works" (`device_altitude_how_it_works_header`): Explanation of barometric formula + calibration vs. GPS position + DEM topographic grid matching.
  b. "Pros & Cons" (`device_altitude_pros_cons_header`): Unbiased trade-offs (+ smooth relative response, zero vertical GPS jitter / - weather drift for barometer; + weather immunity, absolute height / - GPS accuracy dependency, bridge/cliff stepped response, and explicit active internet connection requirement for uncached areas outside saved routes for DEM).
4. *100% 9-Language Parity & M3 Typography (`REQ-SEN-026.4`)*:
• All explanation strings SHALL maintain 100% translation parity across all 9 locales with literal `\n` linebreaks.
• The card SHALL use Material 3 semantic color tokens (`surfaceVariant`, `onSurfaceVariant`) with accessible contrast.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement `REQ-SEN-026`, extending `REQ-SEN-025` (*Pairable Position-Based Elevation Sensor*) and `REQ-UI-149` (*Modernized Modal Bottom Sheet Dialogs & Selection Flows*). Targets `strings_devices.xml`, `strings.xml`, `DevicesDatabaseManager.java`, and `EditDeviceDialog.kt`.
2. *Historical Origin & Commit Trace*: Sprint 2026-45.1 (`ATT-5073`). Builds on `ATT-4859` (`REQ-SEN-025`, Sprint 2026-44.2). Amended in Sprint 2026-45.2 (`ATT-5198`) to explicitly require active internet connection notice for uncached areas outside saved routes.
3. *Root Reason for Existing Formulation*: "Luftdruck" was inherited from the initial ANT+/BLE sensor stack. `EditDeviceDialog.kt` focused solely on hardware sensors with calibration parameters (wheel circumference), omitting educational guidance for virtual/internal sensors. In ATT-5073, educational strings focused on spatial terrain limitations (GPS noise, cliffs) but omitted network dependencies for uncached DEM queries.
4. *Preservation of Core Invariants*: Custom device names in `Devices.db` strictly preserved; sensor data delivery, calibration dispatch, literal `\n` formatting, and 100% test pass rate across all 9 locales remain invariant.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an existing user upgrade with default-named `ALTITUDE_FROM_PRESSURE` in `Devices.db`,
• *When* the database is opened,
• *Then* the device name updates to "Höhe aus Luftdruck" (or language equivalent), while custom user renames remain unmodified.
• *Given* an athlete opening `EditDeviceDialog` for `ALTITUDE_FROM_PRESSURE` or `ALTITUDE_FROM_POSITION`,
• *When* the dialog renders,
• *Then* an educational card displays "How it works" and "Pros & Cons" explaining the sensor's measurement mechanism and trade-offs.
• *Given* an athlete viewing "Pros & Cons" for `ALTITUDE_FROM_POSITION` across any of the 9 supported locales,
• *When* inspecting the cons (`-`) section,
• *Then* the text explicitly informs the user that an active internet connection is required for uncached areas outside saved routes.

**Invariants**: Custom device names preserved, sensor data delivery unchanged, 100% 9-language translation parity, literal `\n` linebreaks preserved, zero crash on null device data.


### REQ-TRK-051: Dynamic SQLite Schema Discovery via PRAGMA table_info & Decoupled Summary Telemetry [Verified]
- **Tests**: `TST-TRK-051` | **Tickets**: `ATT-4968`
- **Implementation**: `TrackerService.java`, `TrackOnMapAftermathViewModel.kt`
- **Rationale**: During workouts on Android $\le 14$ (API $\le 34$), `SQLiteConnection.mPreparedStatementCache` caches compiled statements across transactions and does NOT invalidate them upon dynamic `ALTER TABLE ... ADD COLUMN` DDL. In `TrackerService.handleMissingColumns()`, querying via `samplesDb.query(tableName, null, null, null, null, null, null, "1")` returns a stale cached cursor missing newly added columns, causing false negatives in column existence checks and throwing `SQLiteException: duplicate column name` on subsequent ticks. Additionally, in `TrackerService.sampleAndWriteToDb()`, an abort guard `if (!sampleInserted) return;` prematurely halts execution upon sample insert failures, freezing `WorkoutSummaries.db` updates (duration, distance, energy) at 1 second and 0.00 km. In `TrackOnMapAftermathViewModel`, Phase 4 overwrites valid multi-point summary fast tracks with single-point sample tracks. Formulating `PRAGMA table_info` schema inspection, decoupling live summary updates from sample write failures, and gating map track replacement ensures continuous, fault-tolerant workout recording across all Android API levels (ATT-4968, Parent Epic ATT-115, extending `REQ-TRK-026`, `REQ-TRK-029`, and `REQ-DAT-033`):
1. *Uncached Real-Time Schema Discovery via PRAGMA table_info (`REQ-TRK-051.1`)*:
• In `TrackerService.java`, `handleMissingColumns()` SHALL discover existing table columns using `PRAGMA table_info("<tableName>")` instead of `samplesDb.query(..., "1")`.
• `PRAGMA table_info` results SHALL be queried and mapped into a case-insensitive `Set<String>` (with a synchronized per-table in-memory set cache), guaranteeing real-time schema visibility immune to `SQLiteConnection.mPreparedStatementCache` staleness across all Android versions (API 26–36).
2. *Decoupled Summary Persistence & Fault-Tolerant Recording (`REQ-TRK-051.2`)*:
• In `TrackerService.sampleAndWriteToDb()`, if a sample insert into `WorkoutSamples.db` fails (`sampleInserted == false`), the service SHALL log a rate-limited warning and increment an atomic error counter (`mSampleInsertFailureCount`), but SHALL NOT abort execution (`return;`).
• The service SHALL proceed unconditionally to update `WorkoutSummaries.db` (`updateWorkoutSummary()`) with current elapsed time, active time, distance, and live extrema, preventing summary freeze.
3. *Map Polyline Fallback Protection in Track Inspection (`REQ-TRK-051.3`)*:
• In `TrackOnMapAftermathViewModel.kt`, Phase 4 (loading detailed sample tracks from `WorkoutSamples.db`) SHALL verify that the loaded tracks contain at least two coordinate points (`fullTracks.any { it.path.size >= 2 }`) before replacing `_uiState.value.tracks`.
• If `fullTracks` has fewer than 2 valid points, the system SHALL retain the multi-point `fastTrack` loaded from summary `mapPolyline`.
4. *Transaction Integrity & Service Stability (`REQ-TRK-051.4`)*:
• Foreground service thread execution, SQLite transaction boundaries, and rate-limited logging SHALL remain 100% resilient with zero unhandled exceptions.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Amends `REQ-TRK-029` (*Safe SQLite Column Escaping & Sample Insert Telemetry*) and `REQ-DAT-033` (*Fast-Track Map Telemetry*), Parent Epic `ATT-115`. Targets `TrackerService.java` and `TrackOnMapAftermathViewModel.kt`.
2. *Historical Origin & Commit Trace*: `ATT-3980` introduced dynamic column addition via `handleMissingColumns()`; `ATT-3985` added column escaping; commit `f4650d74` added `if (!sampleInserted) return;` in `TrackerService.java`.
3. *Root Reason for Existing Formulation*: `query(..., "1")` was assumed to return an updated cursor reflecting recent DDL alterations; `if (!sampleInserted) return;` was intended to avoid updating summaries when sample inserts failed under the assumption that sample insert failures were fatal.
4. *Preservation of Core Invariants*: Dynamic sensor additions (mid-ride slope, pace, cadence, power), double-quoted column escaping, rate-limited logging, and summary transaction finalization remain strictly preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an active workout on Android <= 14 (API <= 34) where dynamic sensor columns (e.g. `SLOPE`, `PACE`) join on Tick 2+,
• *When* `handleMissingColumns()` inspects existing columns,
• *Then* `PRAGMA table_info` reflects previously added columns accurately, preventing duplicate `ALTER TABLE ... ADD COLUMN` executions and `SQLiteException`.
• *Given* an isolated sample insert failure in `WorkoutSamples.db`,
• *When* `sampleAndWriteToDb()` executes,
• *Then* the failure is logged with rate-limiting, and `WorkoutSummaries.db` continues to update duration and distance without freezing.
• *Given* a workout completed with few sample points (< 2) but a valid multi-point `mapPolyline` in summary,
• *When* `TrackOnMapAftermathViewModel` Phase 4 loads sample tracks,
• *Then* the multi-point `fastTrack` is preserved and remains visible on the map.

**Invariants**: Zero service crashes, transaction boundary safety, rate-limited logging, and 100% clean-room test pass rate.

### REQ-CON-023: Paired BLE Random Static Address Resolution, Scan-Assisted Connection & Configurable Search Timeout [Verified]
- **Tests**: `MyBTLEDeviceTest.java`, `SensorSearchPreferencesTest.kt`, `SensorSearchTuningSectionTest.kt`, `SearchSettingsDialogTest.kt`, `TranslationParityTest.kt`
- **Implementation**: `MyBTLEDevice.java`, `TrainingApplication.java`, `SensorSearchPreferences`, `SensorSearchTuningSection.kt`, `SearchSettingsDialog.kt`
- **Rationale**: Paired BLE fitness peripherals utilizing BLE Random Static Addresses fail to connect during paired search when Android's `BluetoothRemoteDevices` cache is empty because `getRemoteDevice(mAddress)` defaults to `ADDRESS_TYPE_PUBLIC`. The system SHALL resolve Random Static addresses using `BluetoothDevice.ADDRESS_TYPE_RANDOM` (via `getRemoteLeDevice` on API 33+), provide active filtered BLE scan assistance to warm the OS remote device cache across all supported Android versions, and expose a configurable BLE search timeout per attempt ($3\text{--}15$ seconds, default 5 seconds) across Expert Settings and Search Settings Dialog to prevent head-of-line blocking in sequential paired search (ATT-5489, refining `REQ-CON-019` and `REQ-UI-154`):
1. *BLE Random Static MAC Classification & Address Type Resolution (`MyBTLEDevice.java`, `REQ-CON-023.1`)*:
• The system SHALL classify a MAC address as Random Static when the two most significant bits of the first byte are set (`(firstOctet & 0xC0) == 0xC0`), adhering to Bluetooth Core Spec Vol 6 Part B §1.3.2.1.
• On Android 13+ (API 33+, `Build.VERSION_CODES.TIRAMISU`), `MyBTLEDevice` SHALL obtain the remote device handle via `BluetoothAdapter.getRemoteLeDevice(mAddress, addressType)` using `BluetoothDevice.ADDRESS_TYPE_RANDOM` for Random Static addresses and `BluetoothDevice.ADDRESS_TYPE_PUBLIC` otherwise, falling back to `BluetoothAdapter.getRemoteDevice(mAddress)` on API < 33.
2. *Active Scan-Assisted BLE Discovery & Guaranteed Scanner Teardown (`MyBTLEDevice.java`, `REQ-CON-023.2`)*:
• During `startSearching()`, if `BLUETOOTH_SCAN` runtime permission is granted, `MyBTLEDevice` SHALL start a targeted `BluetoothLeScanner` scan filtered by `mAddress` to accelerate cache population in `BluetoothRemoteDevices` and trigger direct GATT connection upon discovering the advertising peripheral.
• Active scans SHALL be defensively stopped and cleaned up unconditionally upon GATT connection (`STATE_CONNECTED`), search timeout (`mSearchTimeoutRunnable`), explicit disconnect (`disconnectFromGatt()`), and device shutdown (`shutDown()`).
3. *Configurable BLE Search Duration Persistence & Application Plumbing (`TrainingApplication.java`, `SensorSearchPreferences`, `REQ-CON-023.3`)*:
• The system SHALL persist the athlete's configured BLE search timeout in `SharedPreferences` under key `SP_BTLE_SEARCH_PERIOD_SEC = "btleSearchPeriodSec"`, constrained to discrete integer seconds in $[3, 15]$ with default $5$ seconds.
• `TrainingApplication.getBtleSearchPeriodMs()` SHALL read `btleSearchPeriodSec` (defaulting to 5) and return the timeout in milliseconds ($[3000, 15000]\text{ ms}$).
• `MyBTLEDevice.startSearching()` SHALL delay `mSearchTimeoutRunnable` using `TrainingApplication.getBtleSearchPeriodMs()` instead of the legacy hardcoded 10-second constant.
4. *Expert Settings & Search Settings Dialog Slider UI (`SensorSearchTuningSection.kt`, `SearchSettingsDialog.kt`, `REQ-CON-023.4`)*:
• In `SensorSearchTuningSection.kt` and `SearchSettingsDialog.kt`, the system SHALL render a discrete Material 3 Slider for the BLE search duration directly below the search retry attempts control with range $3\text{--}15$ seconds (11 snap steps), dynamic value badge (`"%d s"`), explanatory scale summary, and transactional save/cancel support.
5. *9-Language Localization Parity (`REQ-CON-023.5`)*:
• Search duration UI strings (`prefs_btle_search_period_title` and `prefs_btle_search_period_summary`) SHALL be localized with 100% parity across all 9 application locales without missing entries or format specifier mismatches.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Amends `REQ-CON-019` (*Robust Bluetooth LE Sensor Discovery & Direct LE Transport Connection Architecture*, Sprint 2026-41.4, `ATT-2773`) and `REQ-UI-154` (*Search Settings Modal Bottom Sheet & Navigation Integration*, Sprint 2026-41.2, `ATT-2780`), targeting `MyBTLEDevice.java`, `TrainingApplication.java`, `SensorSearchTuningSection.kt`, `SearchSettingsDialog.kt`, and `values*/strings.xml`.
2. *Historical Origin & Commit Trace*: `ATT-2773` established `device.connectGatt(mContext, false, mGattCallback, TRANSPORT_LE)` using `BluetoothAdapter.getRemoteDevice()`. `ATT-2780` introduced search attempt tuning (1..5 tries) with hardcoded 10s timeout.
3. *Root Reason for Existing Formulation*: `getRemoteDevice(mAddress)` was standard before API 33 introduced `getRemoteLeDevice(address, addressType)`. When sensors were paired, Android typically retained cached records while Bluetooth stayed active. Cold boots and BT toggles evict unbonded records, forcing default public addressing. 10s was inherited from ANT+ search intervals.
4. *Preservation of Core Invariants*: Direct LE connection (`TRANSPORT_LE`), search queue sequential rotation in `DeviceManager`, zero leak of GATT/scan instances, and 100% test suite pass rate remain strictly intact.

**Acceptance Criteria (Given-When-Then)**:
• *Given* a paired BLE sensor with a Random Static MAC (e.g. `C6:CC:E3:F5:F0:37` or `E8:4D:74:07:F4:DD`) and an uncached Android BLE stack after cold reboot,
• *When* `MyBTLEDevice.startSearching()` is initiated,
• *Then* the device handle is resolved with `ADDRESS_TYPE_RANDOM` (API 33+) or scan assistance, and GATT connection succeeds without timing out with `addr_type=public`.
• *Given* an athlete adjusting the BLE search timeout in Expert Settings or Search Settings Dialog to $4$ seconds,
• *When* search executes for an offline peripheral,
• *Then* `MyBTLEDevice` advances to the next paired device after 4 seconds instead of 10 seconds.
• *Given* the 9 localized `strings.xml` resource files,
• *When* executing `TranslationParityTest`,
• *Then* `prefs_btle_search_period_title` and `prefs_btle_search_period_summary` pass with zero discrepancies.

**Invariants**: `TRANSPORT_LE` direct connect, non-leaking scanner lifecycle, and 100% clean-room test pass rate.

### REQ-CON-024: BLE Crank Revolution 16-Bit Rollover, Precision & Disconnect State Reset [Verified]
- **Tests**: `TST-CON-024` | **Tickets**: `ATT-6162`
- **Implementation**: `BTLEBikeDevice.java`, `BTLEBikePowerDevice.java`
- **Rationale**: The system SHALL handle 16-bit UINT16 counter rollover for cumulative crank revolutions in BLE CSC and CPS GATT measurements, prevent integer division truncation, discard non-physiological cadence samples, and reset state on sensor unregistration (Sprint 2026-46.1, ATT-6162, refining `REQ-CON-002` and `REQ-CON-014`):
1. *16-Bit Crank Revolution Rollover Masking (`REQ-CON-024.1`)*:
• In `BTLEBikeDevice.java` and `BTLEBikePowerDevice.java`, revolution difference SHALL be calculated with 16-bit UINT16 modular arithmetic: `long revDiff = (cumulativeCrankRevolutions - mLastCrankRevolutions) & 0xFFFFL;`.
• Subtraction resulting in negative values during counter rollover from 65535 to 0 or 1 SHALL be normalized into a non-negative delta, preventing negative cadence spikes (~ -3.9 million rpm).
2. *Floating-Point Precision Division (`REQ-CON-024.2`)*:
• Cadence RPM SHALL be computed using floating-point arithmetic: `double cadence = (60.0 * revDiff * 1024.0) / timeDiff;`, eliminating integer truncation of fractional RPM values.
3. *Physiological Cadence Plausibility Guard (`REQ-CON-024.3`)*:
• Recomputed cadence samples SHALL be gated by a physiological plausibility check: `if (cadence >= 0.0 && cadence <= 250.0)`.
• Samples exceeding 250.0 rpm or negative values SHALL be discarded and NOT forwarded to `mCadenceSensor.newValue(...)`.
4. *Sensor Disconnect State Reset (`REQ-CON-024.4`)*:
• `BTLEBikeDevice` and `BTLEBikePowerDevice` SHALL override `unregisterSensors()` (invoking `super.unregisterSensors()`).
• On unregistration/disconnect, both classes SHALL reset revolution tracking flags: `mLastCrankRevolutionsValid = false`, `mLastWheelRevolutionsValid = false`, `mIdenticalCrankTime = 0`, and `mIdenticalWheelTime = 0`, ensuring reconnected sensors cleanly reinitialize without false deltas.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new refinement `REQ-CON-024` refining `REQ-CON-002` (*Bluetooth LE Sensor Support*) and `REQ-CON-014` (*BLE GATT Measurement Packet Parsing Resilience*).
2. *Historical Origin & Commit Trace*: Introduced in initial BLE CSC (`c89e2113`) and CPS (`49b5df12`). Subtraction assumed monotonic counters without accounting for 16-bit UINT16 rollover.
3. *Root Reason for Existing Formulation*: `crankEventTime > mLastCrankEventTime` guarded against reverse time jumps, but omitted counter wrap-around handling.
4. *Preservation of Core Invariants*: Wheel revolution processing and distance/speed math remain intact; GATT connection lifecycles remain unchanged; 100% test pass rate.

**Acceptance Criteria (Given-When-Then)**:
• *Given* last crank count `mLastCrankRevolutions = 65535` and new crank count `cumulativeCrankRevolutions = 1` with `timeDiff = 1024` ticks (1.0 s),
• *When* processed by `BTLEBikeDevice` or `BTLEBikePowerDevice`,
• *Then* `revDiff` evaluates to 2 and cadence evaluates to `120.0 rpm` with zero negative spikes and zero integer truncation.
• *Given* a parsed packet yielding cadence `> 250.0 rpm` or `< 0.0 rpm`,
• *When* evaluated,
• *Then* the implausible sample is discarded and not emitted to `mCadenceSensor`.
• *Given* a BLE device disconnect (`unregisterSensors()`),
• *When* executed,
• *Then* `mLastCrankRevolutionsValid` and `mLastWheelRevolutionsValid` are reset to `false`, and `mIdenticalCrankTime`/`mIdenticalWheelTime` are reset to `0`.

**Invariants**: Wheel speed/distance calculations remain completely unaffected; zero UI or database schema changes; 100% full-suite test pass rate.

### REQ-TRK-052: Zero-Allocation 1-Hz ClockTimeTask Time-of-Day Formatting [Verified]
- **Tests**: `TST-TRK-052` | **Tickets**: `ATT-5481`
- **Implementation**: `ClockDevice.java`, `ClockDeviceResilienceTest.kt`
- **Rationale**: During live tracking, `ClockDevice.ClockTimeTask` executes on the background timer thread at 1 Hz. Calling `Calendar.getInstance().getTime()` on every tick instantiates fresh `GregorianCalendar`, internal `int[]` field arrays, and `Date` instances ($\ge 43,200$ ephemeral objects per 4-hour workout), inducing garbage collection pauses and unnecessary CPU/battery consumption. The system SHALL reuse a pre-allocated `Date` instance mutated via `setTime(System.currentTimeMillis())` to achieve 0-byte heap allocation per 1-Hz tick while 100% preserving localized time-of-day formatting output (ATT-5481, extending `REQ-TRK-027` and `REQ-TRK-039`):
1. *Pre-Allocated Date Holder (`ClockDevice.java`, `REQ-TRK-052.1`)*:
• `ClockDevice` SHALL maintain a private final `Date` instance (`private final Date mReusableDate = new Date();`) instantiated once during device initialization.
2. *Zero-Allocation Time Mutation in ClockTimeTask (`ClockDevice.java`, `REQ-TRK-052.2`)*:
• In `ClockDevice.ClockTimeTask.run()`, the task SHALL update `mReusableDate.setTime(System.currentTimeMillis())` and format via `df.format(mReusableDate)` directly.
• Invoking `Calendar.getInstance()` or constructing new `Date` objects in `ClockTimeTask.run()` SHALL be strictly prohibited.
3. *Output Parity & Confinement Invariance (`REQ-TRK-052.3`)*:
• The formatted time string emitted via `mTimeOfDaySensor.newValue(...)` SHALL remain identical in content, format, locale, and timezone to the legacy `Calendar` output.
• Thread safety SHALL be guaranteed by thread confinement to the single-threaded `java.util.Timer` loop (`Timer-0`).

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement `REQ-TRK-052`, extending `REQ-TRK-027` (*Clock & Sensor Resilience*) and `REQ-TRK-039` (*Subsystem Resource Hygiene*). Targets `ClockDevice.java` and `ClockDeviceResilienceTest.kt`.
2. *Historical Origin & Commit Trace*: Initial commit `be30d9a2` (2019) introduced `Calendar.getInstance().getTime()` in `ClockDevice.java` as standard boilerplate without allocation profiling; `ATT-3981` (`REQ-TRK-027`) added top-level `try/catch` exception barriers; `ATT-4032` (`REQ-TRK-039`) added rate-limited error telemetry.
3. *Root Reason for Existing Formulation*: `Calendar.getInstance()` was standard Java idiom for time queries; individual allocations were negligible in one-shot use cases but became a major churn hotspot when invoked at 1 Hz indefinitely.
4. *Preservation of Core Invariants*: Time-of-day formatting format (`SimpleDateFormat.getTimeInstance()`), timezone alignment, 1-Hz broadcast cadence (`NEW_TIME_EVENT_INTENT`), active/total/lap sensor increments, and 100% clean-room test pass rate remain strictly preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an active workout tracking session running `ClockDevice`,
• *When* `ClockTimeTask.run()` executes at 1 Hz,
• *Then* `mTimeOfDaySensor.newValue(...)` receives the current formatted time of day using `mReusableDate.setTime(System.currentTimeMillis())` without allocating `Calendar` or `Date` instances on the heap.
• *Given* unit tests in `ClockDeviceResilienceTest.kt`,
• *When* `ClockTimeTask.run()` is evaluated,
• *Then* `mTimeOfDaySensor` receives a valid time string matching `df.format(new Date(timestamp))`.

**Invariants**: Exact time-of-day format preservation, zero thread-safety contention, and 100% test pass rate.

### REQ-SEN-027: Complete Removal of Obsolete Network Location Provider & Devices.db Version 10 Migration [Verified]
- **Tests**: `TST-SEN-027` | **Tickets**: `ATT-5492`
- **Implementation**: `DeviceType.java`, `SpeedAndLocationDevice_Network.java` (deleted), `DeviceManager.java`, `MySensorManager.java`, `DevicesDatabaseManager.java`, `DatabaseSchemaRegistry.kt`, `DeviceData.kt`, `UIHelper.java`, `TrainingApplication.java`, `strings_devices.xml`
- **Rationale**: Raw cell-tower and Wi-Fi network triangulation has no athletic tracking value (accuracy 50–1,500 m, no reliable speed/altitude), especially since Google Fused already incorporates network signals. Removing it eliminates dead code, declutters the UI, and avoids wasting Free-tier sensor slots.
- **Description**:
1. *Sensor Driver and DeviceType Excision (`REQ-SEN-027.1`)*:
• `SpeedAndLocationDevice_Network.java` SHALL be deleted from `app/src/main/java/com/atrainingtracker/banalservice/devices/`.
• `DeviceType.SPEED_AND_LOCATION_NETWORK` SHALL be removed from `DeviceType.java`.
• All references in `DeviceManager.java`, `MySensorManager.java`, `TrainingApplication.java`, `UIHelper.java`, and `DeviceData.kt` SHALL be excised.
• Localized string `devices_location_Network` SHALL be removed from `strings_devices.xml` across all 9 supported locales.
2. *Devices Database Version 10 Schema Migration (`REQ-SEN-027.2`)*:
• In `DevicesDatabaseManager.java`, `DevicesDbHelper.DB_VERSION` SHALL be incremented to `10`.
• `DevicesDbHelper.onCreate()` SHALL declare `case 10:` in its version switch.
• `DevicesDbHelper.onUpgrade()` SHALL execute an idempotent migration step for `oldVersion <= 9` that deletes any existing record with `DEVICE_TYPE = 'SPEED_AND_LOCATION_NETWORK'` from `Devices4` table and defensively cleans up any orphaned equipment references.
• Fresh database creation (`addLocationDevicesToDb`) SHALL NOT insert `SPEED_AND_LOCATION_NETWORK`.
• `getSpeedAndLocationNetworkDeviceId()` SHALL be excised from `DevicesDatabaseManager.java`.
3. *DatabaseSchemaRegistry Contract Synchronization (`REQ-SEN-027.3`)*:
• `DatabaseSchemaRegistry` SHALL automatically validate schema version 10 via its direct reference to `DevicesDbHelper.DB_VERSION` (Rule 2.2 / Rule 62).
4. *Living Requirements & Tests Parity (`REQ-SEN-027.4`)*:
• `REQ-TRK-024` and `REQ-SET-083` SHALL be updated to reflect the excision of `SPEED_AND_LOCATION_NETWORK`.

**Acceptance Criteria (Given-When-Then)**:
• *Given* the codebase after ATT-5492 implementation,
• *When* inspecting `DeviceType.values()`,
• *Then* `SPEED_AND_LOCATION_NETWORK` is completely absent.
• *Given* an existing user database at version $\le 9$ containing a row with `DEVICE_TYPE = 'SPEED_AND_LOCATION_NETWORK'`,
• *When* the database is opened and upgraded to version 10 via `onUpgrade()`,
• *Then* the obsolete network row is deleted and no longer present in `Devices4`.
• *Given* a fresh install database created via `onCreate()`,
• *When* querying the `DEVICES` table for smartphone location providers,
• *Then* only `SPEED_AND_LOCATION_GPS` and `SPEED_AND_LOCATION_GOOGLE_FUSED` are present.

**Invariants**: Dedicated physical sensor precedence (`BIKE_SPEED > GOOGLE_FUSED > GPS`) is 100% maintained; raw GPS fallback on de-Googled devices is 100% preserved; existing user device pairings are untouched; `DatabaseSchemaRegistry` matches `DevicesDbHelper.DB_VERSION` (Rule 62).

### REQ-TRK-053: Persisted Deduplicated Workout Diagnostic Errors & Actionable Report Dialog [Verified]
- **Tests**: `TST-TRK-053` | **Tickets**: `ATT-5886`
- **Implementation**: `TrackerService.java`, `WorkoutSummariesDatabaseManager.java`, `WorkoutData.kt`, `WorkoutHeaderData.kt`, `WorkoutDataMapper.kt`, `WorkoutRepository.kt`, `WorkoutSummary.kt`, `WorkoutDetailTabbedContent.kt`, `EditWorkoutScreen.kt`, `WorkoutDiagnosticBottomSheet.kt`
- **Rationale**: Runtime exceptions during table initialization, 1-Hz sample recording, or post-workout finalization are caught defensively in `TrackerService` to prevent foreground service crashes. Currently, these caught exceptions are logged exclusively to volatile Android logcat, which rolls over outdoors before an athlete can inspect or export diagnostics. Persisting a compact, deduplicated diagnostic log in `WorkoutSummaries.TABLE` (which commits independently from sample insertion) guarantees error capture across the entire workout lifecycle, preserves zero storage/UI overhead on clean workouts, and empowers athletes to report or clear actionable diagnostics.
- **Description**:
1. *WorkoutSummaries Database Schema & Helper API (`REQ-TRK-053.1`)*:
• `WorkoutSummariesDbHelper.DB_VERSION` SHALL be incremented to `28`.
• `WorkoutSummaries.TABLE` SHALL include a nullable `TEXT` column `RECORDING_ERRORS` (`"recordingErrors"`, default `NULL`).
• Both fresh database initialization (`onCreate`) and incremental upgrade (`onUpgrade`, for `oldVersion < 28`) SHALL create/alter the table symmetrically (`Rule 64`).
• `DatabaseSchemaRegistry` SHALL automatically validate schema version 28 via its direct reference to `WorkoutSummariesDbHelper.DB_VERSION` (`Rule 62`).
• `WorkoutSummariesDatabaseManager` SHALL provide `clearRecordingErrors(long workoutId)` to reset `RECORDING_ERRORS` to `NULL`.
• When a workout records and finalizes with zero caught exceptions, `RECORDING_ERRORS` SHALL remain `NULL`.
2. *Bounded Lifecycle Exception Capture & 1-Hz Deduplication (`REQ-TRK-053.2`)*:
• `TrackerService` SHALL maintain an in-memory `WorkoutDiagnosticCollector` per active workout session.
• Caught exceptions across table initialization (`mTableInitializationFuture`), 1-Hz sample recording/schema healing (`logSampleInsertFailure`, `handleMissingColumns`, async write failures), and post-workout finalization (`finalizeLiveSession`: map polyline, extrema, aerobic efficiency, commute/trainer, clustering, milestone auto-naming) SHALL be recorded into the collector.
• The collector SHALL deduplicate repeated exceptions by `(stage, exceptionClass, message)`. For each distinct entry, the collector SHALL record: stage (`INIT`, `SAMPLE_WRITE`, `FINALIZE`), first occurrence timestamp, last occurrence timestamp, total occurrence count, exception class, message, and compact sanitized stack trace.
• Total persisted error payload length SHALL be capped at a maximum of 8 KB to prevent database bloat during extended workouts.
• `TrackerService` SHALL persist/update `WorkoutSummaries.RECORDING_ERRORS` when a new distinct error occurs and commit the final aggregated summary counts during `finalizeLiveSession`.
3. *Symmetrical DTO & Repository Mapping (`REQ-TRK-053.3`)*:
• `WorkoutData` and `WorkoutHeaderData` SHALL expose nullable `recordingErrors: String?`.
• `WorkoutDataMapper.fromCursor(Cursor)` and batch mapper `fromSnapshot(CursorSnapshot)` SHALL map `recordingErrors` symmetrically (`Rule 20`).
• `WorkoutRepository` SHALL expose `clearRecordingErrors(workoutId: Long)` delegating to the database manager.
4. *Actionable User-Facing UI & Non-Intrusive Option C Presentation (`REQ-TRK-053.4`)*:
• When `recordingErrors` is `NULL` or blank, all error badges, warning banners, and diagnostic cards SHALL remain 100% invisible across the application.
• When `recordingErrors` is non-empty:
  - The workout card in the workout list (`WorkoutSummary` / `WorkoutHeader`) SHALL display a warning icon/badge.
  - `WorkoutDetailTabbedContent` SHALL display a prominent, tappable warning banner at the top of the detail view.
  - `EditWorkoutScreen` SHALL display a diagnostic warning card.
• Tapping the warning badge, banner, or card SHALL open `WorkoutDiagnosticBottomSheet` containing:
  - Plain-language explanation dynamically categorized by failure cause:
    - `SQLiteFullException`: storage full guidance (`R.string.workout_diagnostic_explanation_storage_full`).
    - `SAMPLE_WRITE`: per-second data write failure notice with summary preservation confirmation (`R.string.workout_diagnostic_explanation_sample_write`).
    - `FINALIZE`: post-workout calculation warning (`R.string.workout_diagnostic_explanation_finalize`).
    - Generic/other: fallback explanation (`R.string.workout_diagnostic_explanation_generic`).
  - Collapsible "Technische Details" accordion displaying formatted diagnostic JSON/text with a 1-tap "In Zwischenablage kopieren" action.
  - Primary action: "Fehlerbericht per E-Mail senden" opening an `Intent.ACTION_SENDTO` (`mailto:rainer.blind@gmail.com`) with pre-filled subject (`aTrainingTracker Fehlerbericht (${BuildConfig.VERSION_NAME})`), device metadata (model, Android OS version, app version, sport type), and the diagnostic error payload.
  - Secondary action: "Warnung löschen" with confirmation dialog, which resets `RECORDING_ERRORS` to `NULL` via `WorkoutRepository.clearRecordingErrors`, immediately dismissing and hiding all warning indicators.
5. *9-Language Localization Parity (`REQ-TRK-053.5`)*:
• All user-visible strings for banners, dialogs, plain explanations, technical details, email subject, copy toast, and clear actions SHALL be translated across all 9 application locales (`values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`) with 100% parity and zero inline emojis (`ATT-5400`).

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement `REQ-TRK-053`, extending `REQ-TRK-029` (*Sample Insert Failure Telemetry*), `REQ-TRK-051.2` (*Decoupled Summary Persistence*), and `REQ-REV-003` (*Jubilee Action Flow*). Targets `TrackerService.java`, `WorkoutSummariesDatabaseManager.java`, and aftermath UI screens.
2. *Historical Origin & Commit Trace*: Initial rate-limiting in `ATT-3985` (`f4650d74`) protected logcat from 1-Hz floods; `ATT-4968` decoupled summary database transactions from sample table insertions.
3. *Root Reason for Existing Formulation*: Logging to logcat was minimal and lightweight, but field failures without attached ADB hosts suffered silent data loss due to logcat ringbuffer eviction.
4. *Preservation of Core Invariants*: Zero UI clutter on successful clean workouts, decoupled summary commits, non-blocking asynchronous persistence, and 100% test pass rate remain strictly preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* a workout recorded without any caught exceptions,
• *When* `WorkoutSummaries` is queried and viewed in `Einheiten`, `WorkoutDetailTabbedContent`, or `EditWorkoutScreen`,
• *Then* `RECORDING_ERRORS` is `NULL` and zero warning icons, banners, or cards are displayed.
• *Given* 100 consecutive 1-Hz sample insertion failures throw `SQLiteException` in `TrackerService`,
• *When* the service handles the exceptions,
• *Then* the service does not crash, deduplicates the failure into a single entry with `count = 100`, records first/last timestamps, bounds total text payload $\le 8\text{ KB}$, and persists it to `WorkoutSummaries.RECORDING_ERRORS`.
• *Given* an exception during table init or any block in `finalizeLiveSession`,
• *When* the exception is caught,
• *Then* it is captured with stage `INIT` or `FINALIZE` and persisted to `WorkoutSummaries.RECORDING_ERRORS`.
• *Given* a workout with non-null `RECORDING_ERRORS`,
• *When* the athlete taps the warning banner in `WorkoutDetailTabbedContent`,
• *Then* `WorkoutDiagnosticBottomSheet` opens displaying the plain-language explanation, expandable technical details, 1-tap email report button pre-filling `mailto:rainer.blind@gmail.com`, and "Warnung löschen" button that resets `RECORDING_ERRORS` to `NULL` and hides the warning.

**Invariants**: Clean workouts incur zero storage and zero UI overhead; sample failure handling never blocks the live tracking foreground thread; `WorkoutSummaries` schema migration to version 28 is safe and idempotent; `fromCursor` and `readCursorSnapshot` mapping is 100% symmetrical (`Rule 20`).

### REQ-TRK-054: Hardware Volume Keys Cockpit Tab Cycling with Cyclic Wrap-Around [Verified]
- **Tests**: `TST-TRK-054` | **Tickets**: `ATT-5905`
- **Implementation**: `TrackingTabsPageResolver.kt`, `MainActivityWithNavigation.kt`, `TrackingTabsScreen.kt`, `DisplaySettingsDialog.kt`, `TrainingApplication.java`
- **Rationale**: When riding or running in rain, cold weather, or with thick gloves, capacitive touch-screen swipes between Cockpit tabs (`Standard`, `Karte`, `Runden`, `Live Segments`) in `TrackingTabsScreen` can be unreliable or hazardous. Utilizing physical hardware volume buttons (`KEYCODE_VOLUME_UP`, `KEYCODE_VOLUME_DOWN`) provides dedicated bike computer-like physical ergonomics. Cyclic wrap-around ensures athletes can navigate through all configured Cockpit tabs even if handlebar mounts block one physical volume button.
- **Description**:
1. *Pure Domain Tab Mathematics & Cyclic Wrap-Around (`REQ-TRK-054.1`)*:
• `TrackingTabsPageResolver` SHALL provide `resolveNextPage(currentPage: Int, pageCount: Int, forward: Boolean): Int`.
• When `forward` is `true` (`Volume Up`), target page SHALL be `(currentPage + 1) % pageCount`.
• When `forward` is `false` (`Volume Down`), target page SHALL be `(currentPage - 1 + pageCount) % pageCount`.
• Boundary guard: When `pageCount <= 1`, target page SHALL be `0`.
2. *Window-Level Volume Key Interception & Audio HUD Suppression (`REQ-TRK-054.2`)*:
• `MainActivityWithNavigation` SHALL intercept `KEYCODE_VOLUME_UP` and `KEYCODE_VOLUME_DOWN` before system volume processing.
• When an active volume key interceptor is registered, consuming both `ACTION_DOWN` and `ACTION_UP` SHALL return `true`, completely preventing the Android system volume slider HUD from appearing.
3. *Lifecycle-Bound Registration in TrackingTabsScreen (`REQ-TRK-054.3`)*:
• `TrackingTabsScreen` SHALL register the volume key interceptor via `DisposableEffect` ONLY when `screenMode == ScreenMode.TRACKING` AND `TrainingApplication.volumeKeysSwitchCockpitTabs()` is enabled.
• When a volume key event is handled in `ScreenMode.TRACKING`, the screen SHALL animate the pager state to `resolveNextPage(...)`.
• When `screenMode != ScreenMode.TRACKING` (e.g. `ScreenMode.CONFIGURATION`), when navigating away from Cockpit, or when the setting is disabled, the interceptor SHALL be unregistered so volume keys immediately perform standard Android audio volume control.
4. *Display Settings Integration & Preference Persistence (`REQ-TRK-054.4`)*:
• `TrainingApplication.DEFAULT_DISPLAY_OPTIONS` SHALL include `"volumeKeysSwitchCockpitTabs"`.
• `TrainingApplication` SHALL expose static helper `boolean volumeKeysSwitchCockpitTabs()` returning `getDisplayOptions().contains("volumeKeysSwitchCockpitTabs")`.
• `DisplaySettingsDialog.kt` SHALL render a toggle for `volumeKeysSwitchCockpitTabs` alongside existing display options (`forcePortrait`, `keepScreenOn`, `noUnlocking`).
5. *9-Language Localization Parity (`REQ-TRK-054.5`)*:
• Toggle title `prefs_volume_keys_switch_cockpit_tabs_title` and subtitle `prefs_volume_keys_switch_cockpit_tabs_summary` SHALL be localized across all 9 supported languages (`values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`) with 100% parity.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement `REQ-TRK-054`, extending `REQ-SET-052` (*Display Settings Modal Dialog & Preferences*) and `REQ-UI-435` (*TrackingTabs Pager Resolution*). Targets `TrackingTabsPageResolver.kt`, `MainActivityWithNavigation.kt`, `TrackingTabsScreen.kt`, `DisplaySettingsDialog.kt`, and `TrainingApplication.java`.
2. *Historical Origin & Commit Trace*: `ATT-261` / `ATT-1016` (DisplaySettings modal), `ATT-4838` (`TrackingTabsPageResolver`), and `ATT-5879` (Cockpit layout refinements).
3. *Root Reason for Existing Formulation*: Volume keys were previously left unintercepted because Compose views do not request key events by default, leaving volume handling to Android's default audio stream manager.
4. *Preservation of Core Invariants*: Existing display preferences (`forcePortrait`, `keepScreenOn`, `noUnlocking`) remain unchanged; volume buttons function normally on all other screens and in configuration mode; 9-language localization parity is maintained.

**Acceptance Criteria (Given-When-Then)**:
• *Given* `volumeKeysSwitchCockpitTabs` is enabled and `TrackingTabsScreen` is displayed in `ScreenMode.TRACKING` with 3 tabs (0, 1, 2) on tab 0,
• *When* `KEYCODE_VOLUME_UP` is pressed,
• *Then* the pager smoothly navigates to tab 1, and no audio volume HUD appears.
• *Given* the athlete is on tab 0,
• *When* `KEYCODE_VOLUME_DOWN` is pressed,
• *Then* the pager wraps around and smoothly navigates to tab 2 (last tab).
• *Given* the athlete is on tab 2 (last tab),
• *When* `KEYCODE_VOLUME_UP` is pressed,
• *Then* the pager wraps around and smoothly navigates to tab 0.
• *Given* `volumeKeysSwitchCockpitTabs` is disabled, or the screen mode is `ScreenMode.CONFIGURATION`, or the app is on a non-Cockpit screen,
• *When* `KEYCODE_VOLUME_UP` or `KEYCODE_VOLUME_DOWN` is pressed,
• *Then* system audio volume adjusts normally.
• *Given* `DisplaySettingsDialog` is opened,
• *When* viewed in any of the 9 supported languages,
• *Then* the hardware volume keys toggle title and summary are rendered with 100% localization parity.

**Invariants**: Hardware volume buttons MUST function normally on all non-Cockpit screens and in `ScreenMode.CONFIGURATION`; both `ACTION_DOWN` and `ACTION_UP` MUST be consumed in `TRACKING` mode to avoid audio HUD flicker; cyclic wrap-around MUST handle `pageCount <= 1` gracefully without `IndexOutOfBoundsException`.

### REQ-TRK-055: Persistent Sensor Proxy Binding & Automatic Filter Re-Connection [Verified]
- **Tests**: `TST-TRK-055` | **Tickets**: `ATT-6257`
- **Implementation**: `MySensorManager.java`, `ProxySensor.java`, `FilterManager.java`, `BANALService.java`, `DeviceManager.java`
- **Rationale**: Cockpit tiles (`TrackingViewModel` via `allFilteredSensorData`) and derived sensor devices (`VerticalSpeedAndSlopeDevice` via `getFilteredSensorData`) read sensor values through `FilterManager`. When a sensor disconnects or unregisters (e.g., GPS signal loss via `LocationUnavailable()`, BLE/ANT+ peripheral disconnect, pairing changes), previous implementations removed the `ProxySensor` from `MySensorManager.mSensorMap`, creating a new instance on reconnection. Existing filters in `FilterManager` remained permanently attached to the orphaned `ProxySensor` or previous `MySensor` instance, causing live Cockpit tiles (Speed, Distance, Altitude, Slope) to permanently freeze or show `--` / `0,00 km` even while background logging and map polylines continued. Retaining persistent `ProxySensor` instances and dynamically re-binding existing filters guarantees uninterrupted live telemetry updates across all reconnection and service lifecycle transitions.
- **Description**:
1. *Persistent ProxySensor Instance Retention (`REQ-TRK-055.1`)*:
• `MySensorManager` SHALL retain its `ProxySensor` instance per `SensorType` across sensor unregistration and re-registration cycles instead of removing it from `mSensorMap`.
• When the last active source sensor for a `SensorType` unregisters (`newBestSensor == null`), `MySensorManager` SHALL call `proxy.setSourceSensor(null)`.
• When a source sensor registers or becomes the new best sensor, `MySensorManager` SHALL reuse the existing `ProxySensor` and invoke `proxy.setSourceSensor(newBestSensor)`.
• `computeGCDataLocked()` and `getAvailableSensorTypeSet()` SHALL evaluate `proxy.getSourceSensor() != null` so disconnected sensors are not falsely reported as active.
2. *Dynamic Sensor Filter Re-Binding in FilterManager (`REQ-TRK-055.2`)*:
• `FilterManager.createFilter()` SHALL audit existing filters: when `myFilterMap.containsKey(hashKey)` is true, it SHALL resolve the current active sensor for `filterData` (`filterData.deviceName == null ? mMySensorManager.getSensor(filterData.sensorType) : myDevice.getSensor(filterData.sensorType)`). If the active sensor differs from the recorded sensor in `mMapFilter2Sensor`, `FilterManager` SHALL rebind the filter by unregistering from the old sensor, registering as a listener on the new active sensor, and updating `mMapFilter2Sensor`.
• On receiving `BANALService.SENSORS_CHANGED`, `FilterManager` SHALL audit all existing filters in `myFilterMap` and re-bind any filter whose active source sensor has changed or become newly available, in addition to attempting creation of pending `mRequiredButNotAvailableFilters`.
3. *Named Device Sensor Re-Registration Re-Binding (`REQ-TRK-055.3`)*:
• When a named peripheral device (`deviceName != null`) is re-paired, rediscovered, or recreated in `DeviceManager`, `FilterManager` SHALL dynamically re-bind all existing device-specific filters for that device name to the new `MySensor` instance upon receiving `SENSORS_CHANGED` or during `createFilter`.
4. *Defensive Service Teardown & Static Reference Hygiene (`REQ-TRK-055.4`)*:
• `BANALService.onDestroy()` MUST cleanly shut down and reset static manager references (`cDeviceManager = null`, `cSensorManager = null`, `cFilterManager = null`) if the destroying service matches the active service instance, preventing subsequent service re-creations or static queries from accessing shut-down manager singletons.
• `DeviceManager.shutDown()` SHALL cleanly nullify its device references (`mSpeedAndLocationDevice_GPS = null`, `mSpeedAndLocationDevice_GoogleFused = null`, `mAltitudeFromPressureDevice = null`, `mAltitudeFromPositionDevice = null`) upon teardown.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement `REQ-TRK-055`, extending `REQ-SEN-021` (*ProxySensor Thread Safety & Listener Migration*) and `REQ-FIL-011` / `REQ-FIL-013` (*Filter Manager Lifecycle & Accumulator Reset*). Targets `MySensorManager.java`, `ProxySensor.java`, `FilterManager.java`, `BANALService.java`, and `DeviceManager.java`.
2. *Historical Origin & Commit Trace*: `ATT-4096` (`REQ-SEN-021`, eliminating lock-order inversions during listener migration) and `ATT-5191` (`REQ-FIL-013`, zero-value baseline seeding with ProxySensor unwrapping).
3. *Root Reason for Existing Formulation*: Prior designs removed `ProxySensor` from `mSensorMap` when `bestSensor == null` under the assumption that sensor lifecycles were strictly static. This destroyed listener continuity whenever a sensor disconnected and reconnected.
4. *Preservation of Core Invariants*: Lock-order inversion prevention outside monitors (`REQ-SEN-021.2/.3`), sensor priority resolution hierarchy (`SPEED_PRIORITY`, `CADENCE_PRIORITY`, etc.), and zero-value accumulator seeding (`REQ-FIL-013`) MUST remain preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an active workout with Cockpit tiles registered for `SPEED_mps`, `DISTANCE_m`, and `ALTITUDE`,
• *When* the primary sensor unregisters (e.g. GPS provider temporary loss) and subsequent new samples arrive after sensor re-registration,
• *Then* the existing `MyFilter` instances SHALL receive the new samples via the persistent `ProxySensor` without requiring Cockpit screen recreation.
• *Given* an existing filter in `FilterManager` bound to an old sensor instance,
• *When* a new sensor instance registers for that device or type and `SENSORS_CHANGED` is broadcast,
• *Then* `FilterManager` SHALL re-bind the filter listener to the new sensor and emit updated values.
• *Given* `BANALService` is destroyed and restarted (e.g. after background disconnect timeout),
• *When* the service re-initializes,
• *Then* static managers SHALL be fresh instances and all required filters SHALL bind to active sensors.

**Invariants**: `ProxySensor` listener migration MUST NOT execute inside monitor locks; sensor priorities MUST be strictly respected; accumulator filters MUST preserve baseline zero seeding; no UI layout changes.

### REQ-TRK-056: Google Fused Doppler Velocity Plausibility & Movement Pinning Prevention [Verified]
- **Tests**: `TST-TRK-056` | **Tickets**: `ATT-6256`
- **Implementation**: `SpeedAndLocationDevice.java`
- **Rationale**: When tracking with Google Fused (`SpeedAndLocationDevice_GoogleFused`), Android's location provider fuses GNSS coordinates with smartphone IMU motion sensors. When the phone is mounted steadily or in mock/replay runs, the provider frequently outputs location fixes with Doppler speed damped near or at zero (`rawSpeed <= 0.1 m/s` or `0.10 < rawSpeed <= 0.25 m/s`) despite true coordinate displacement exceeding 7–10 m/s. Prior implementations checked Doppler zero speed unconditionally before testing displacement, causing an oscillation loop where the device toggled between pinned (0.0 km/h) and unpinned (~25–30 km/h) every second, or stuck at 0.5 km/h while distance accumulated steadily. Requiring spatial displacement to be within the escape radius before pinning on Doppler zero and falling back to geometric displacement speed when Doppler speed is unplausibly suppressed eliminates toggling while preserving true stationary jitter suppression (ATT-6256, Parent Epic ATT-166, refining `REQ-TRK-048`):
- **Description**:
1. *Movement-Aware Stationary Pinning Gating (`REQ-TRK-056.1`)*:
• In `SpeedAndLocationDevice.onNewLocation()`, when `isDopplerStationary` is true (`hasSpeed && rawSpeed <= SPEED_ZERO_THRESHOLD`), the device SHALL establish or maintain stationary pinning ONLY IF `mPrevLocation == null` or spatial displacement `rawDelta <= escapeRadius` where `escapeRadius = Math.max(3.0, 1.5 * location.getAccuracy())`.
• If `rawDelta > escapeRadius`, the device SHALL NOT pin the stationary anchor and SHALL treat the fix as active movement.
2. *Damped Doppler & Zero-Speed Geometric Fallback (`REQ-TRK-056.2`)*:
• When unpinned or when displacement exceeds the escape radius (`rawDelta > escapeRadius`), and the provider reports zero or implausibly damped speed (`!hasSpeed || rawSpeed <= SPEED_ZERO_THRESHOLD || (rawSpeed < 0.2 * calcSpeed && calcSpeed > 1.0)`), `SpeedAndLocationDevice` SHALL calculate geometric speed `calcSpeed = delta_distance / (dt_ms / 1000.0)` and assign `speed = calcSpeed` (subject to the $\le 70.0\text{ m/s}$ teleportation spike guard in `REQ-TRK-041.1`).
• This eliminates 1-Hz toggling between 0.0 km/h and plausible velocity and prevents stuck 0.5 km/h readings when coordinates advance steadily.
3. *True Standstill & Chesterton's Fence Preservation (`REQ-TRK-056.3`)*:
• When an athlete is genuinely stationary at an intersection, café, or rest stop (`rawDelta <= escapeRadius`), `SpeedAndLocationDevice` SHALL continue to immediately clamp speed to 0.0 m/s, pin the anchor coordinates, suppress distance increments, and lock compass bearing, fully preserving all invariants of `REQ-TRK-048.1`..`REQ-TRK-048.3`.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement `REQ-TRK-056`, refining `REQ-TRK-048` (*Stationary GPS Jitter Suppression, Doppler Zero Preservation & Anchor Pinning*). Targets `SpeedAndLocationDevice.java`.
2. *Historical Origin & Commit Trace*: `ATT-4850` (Sprint 2026-44.2). Introduced Doppler zero velocity priority to eliminate GPS multipath jitter wander while stopped at traffic lights.
3. *Root Reason for Existing Formulation*: Pure GNSS chips reliably report Doppler zero velocity only when stopped. However, Google Fused combines hardware IMUs which damp or zero Doppler velocity during smooth steady movement or playback testing while coordinate deltas advance by 7–10 m/s.
4. *Preservation of Core Invariants*: True stationary jitter within the accuracy noise floor (`rawDelta <= escapeRadius`) continues to pin immediately with zero speed and distance suppression (`REQ-TRK-048.1`..`REQ-TRK-048.3`); teleportation spike limit $\le 70\text{ m/s}$ (`REQ-TRK-041.1`) is strictly preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* consecutive 1-Hz location fixes advancing 8 m/s with `hasSpeed() == true` and `getSpeed() == 0.0f` (accuracy = 3.0 m, escapeRadius = 4.5 m),
• *When* `SpeedAndLocationDevice.onNewLocation()` processes the fixes,
• *Then* published `SPEED_mps` SHALL calculate geometric velocity (~8 m/s), `DISTANCE_m` SHALL increment steadily, and no stationary anchor SHALL be pinned.
• *Given* consecutive 1-Hz location fixes advancing 8 m/s with `hasSpeed() == true` and `getSpeed() == 0.15f` (damped Doppler speed),
• *When* `SpeedAndLocationDevice.onNewLocation()` processes the fixes,
• *Then* published `SPEED_mps` SHALL calculate geometric velocity (~8 m/s) instead of staying damped at 0.15 m/s.
• *Given* location fixes with Doppler zero speed where displacement remains within the escape radius (`rawDelta <= escapeRadius`),
• *When* `SpeedAndLocationDevice.onNewLocation()` processes the fixes,
• *Then* `SpeedAndLocationDevice` SHALL pin the stationary anchor, clamp speed to 0.0 m/s, suppress distance increments, and lock bearing.

**Invariants**: True standstill immediately pinned with zero speed and zero distance creep (`REQ-TRK-048`); teleportation spike protection ($\le 70\text{ m/s}$, `REQ-TRK-041.1`) preserved; 100% test pass rate maintained.

### REQ-TRK-057: Barometer Altitude Pocket-Compression Spike Rejection & Sourced DB Sync [Verified]
- **Tests**: `TST-TRK-057` | **Tickets**: `ATT-6265`
- **Implementation**: `AltitudeFromPressureDevice.java`, `BarometricAltitudeFilter.kt`, `WorkoutSamplesDatabaseManager.java`
- **Rationale**: Carrying a smartphone in a jacket or trouser pocket causes abrupt 1-second pressure transients of ±20m to ±96m due to body movement and fabric compression. Linear moving averages fail because single-second pulses smear into multi-second waves that trip the 5m hysteresis elevation accumulator, creating massive phantom elevation gain (>1,500m on flat rides). A causal non-linear filter (physical slew-rate limiter + sliding median + EMA) eliminates pocket noise while preserving genuine mountain climbs up to 3,000 m/h with zero attenuation. Additionally, anchoring calibration to the filtered baseline and synchronizing retroactive drift corrections across all source-attributed altitude columns ensures database and sensor consistency (ATT-6265, Parent Epic ATT-166, extending `REQ-CON-017` and `REQ-TRK-047`).
- **Description**:
1. *Causal Non-Linear Outlier Rejection (`REQ-TRK-057.1`)*:
• In `AltitudeFromPressureDevice.java`, raw pressure-derived altitude samples SHALL be filtered through a causal non-linear filter (`BarometricAltitudeFilter`) consisting of a physical vertical slew-rate limiter ($|\Delta h| \le 1.2\text{ m/s} \cdot \Delta t$), a 7-sample sliding median window, and an exponential moving average ($\alpha = 0.18$) before publishing to `mAltitudeSensor`.
• The filter SHALL reject isolated 1–2s transient compression spikes ($\pm 20\text{--}96\text{ m}$) while tracking continuous mountain climbs up to $0.83\text{ m/s}$ (3,000 m/h) with $< 0.5\text{ m}$ phase lag and zero attenuation.
2. *Calibration Baseline Stabilization (`REQ-TRK-057.2`)*:
• `AltitudeFromPressureDevice.setAltitudeCorrection()` and `calibrate()` SHALL compute `mAltitudeCorrection` strictly relative to the outlier-filtered baseline (`mFilteredRawAltitude`) rather than a single raw pressure sample, preventing pocket-compression pulses at workout start from permanently corrupting the activity calibration offset.
3. *Sourced-Column Retroactive Altitude Drift Synchronization (`REQ-TRK-057.3`)*:
• In `WorkoutSamplesDatabaseManager.applyAltitudeDriftCorrection()`, when shifting `SensorType.ALTITUDE.name()`, the database manager SHALL dynamically discover and apply the identical uniform (Case A) or piecewise-linear (Case B) shift to all existing source-attributed altitude columns matching `^ALTITUDE($| \(.*\)$)` (e.g. `"ALTITUDE (Höhe aus Luftdruck)"`) within the same atomic SQLite transaction.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement `REQ-TRK-057`, extending `REQ-CON-017` (*Barometric Calibration Limiting*) and `REQ-TRK-047` (*Piecewise-Linear Drift Correction*). Targets `AltitudeFromPressureDevice.java` and `WorkoutSamplesDatabaseManager.java`.
2. *Historical Origin & Commit Trace*: `ATT-1810` / `ATT-3990` (Sprint 2026-38.2) established `REQ-CON-017` to stop runaway oscillations ($|\Delta h| > 500\text{ m}$). `ATT-4845` established `REQ-TRK-047` for two-point drift correction.
3. *Root Reason for Existing Formulation*: Prior tickets addressed macroscopic weather drift over hours. High-frequency 1-second pocket-pumping transients and source-attributed database columns were unmodeled.
4. *Preservation of Core Invariants*: The 500 m sanity limit (`REQ-CON-017`), idempotent calibration, and two-point linear interpolation (`REQ-TRK-047`) remain strictly preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* a 1-Hz barometric altitude stream with an isolated 1-second $+50\text{ m}$ pocket-compression pulse,
• *When* processed by `AltitudeFromPressureDevice`,
• *Then* single-step output change is clamped $\le 1.2\text{ m/s}$, the spike is rejected by the sliding median, and total phantom elevation gain remains $\le 1.0\text{ m}$.
• *Given* a continuous 300-second mountain ascent at $0.80\text{ m/s}$ (2,880 m/h, total $+240.0\text{ m}$ climb),
• *When* processed,
• *Then* the final elevation matches ground truth within $\pm 0.5\text{ m}$ with zero gradient attenuation.
• *Given* a barometric sensor experiencing a $+40\text{ m}$ pocket-compression spike,
• *When* `calibrate(500.0)` is invoked,
• *Then* `mAltitudeCorrection` anchors to the filtered baseline, differing from true ground truth by $< 1.5\text{ m}$ rather than $40\text{ m}$.
• *Given* a workout sample table containing both `ALTITUDE` and `"ALTITUDE (Höhe aus Luftdruck)"`,
• *When* `applyAltitudeDriftCorrection()` executes with `deltaOffset = +20.0 m`,
• *Then* both `ALTITUDE` and `"ALTITUDE (Höhe aus Luftdruck)"` are shifted by $+20.0\text{ m}$ across all target rows.

**Invariants**: True mountain climbs track without attenuation; IPC contract (`ALTITUDE_CORRECTION_INTENT`) payload and $|\Delta h| \le 500\text{ m}$ guard preserved; database updates remain atomic.

### REQ-TRK-058: Live Mean Maximal Power (MMP) Accumulators, Benchmark Durations (5s, 1m, 5m, 20m) & Cockpit MMP Curve Chart [Verified]
- **Tests**: `TST-TRK-058` | **Tickets**: `ATT-1298`
- **Implementation**: `CyclingPowerMetricsDevice.java`, `SlidingWindowPowerAccumulator.kt`, `SensorType.java`, `LiveMmpCurveChart.kt`
- **Rationale**: Cyclists evaluating high-intensity efforts require real-time pacing feedback and Mean Maximal Power (MMP) benchmarking during active sessions. Calculating rolling means and session peak powers for benchmark durations (5s, 1m, 5m, 20m) and rendering an interactive logarithmic MMP vector curve in the cockpit provides live tactical feedback without degrading CPU or battery performance (ATT-1298, Parent Epic ATT-5908, extending `REQ-TRK-019`, `REQ-TRK-020`, `REQ-TRK-021`, and `REQ-TRK-023`).
- **Description**:
1. *O(1) Circular Sliding-Window Benchmark Accumulators (`REQ-TRK-058.1`)*:
• In `CyclingPowerMetricsDevice.java`, the system SHALL maintain circular ring-buffer sliding window accumulators (`SlidingWindowPowerAccumulator`) for benchmark durations: 5 seconds (Sprint), 1 minute / 60 seconds (Anaerobic Capacity), 5 minutes / 300 seconds (VO2max), and 20 minutes / 1200 seconds (FTP / Threshold benchmark).
• In accordance with Rule 40 (`REQ-TRK-023`), rolling mean power for each duration bucket SHALL compute over the available samples during the initial warm-up phase ($1 \le k < W$) without emitting `null` (`"--"`).
2. *Rolling Mean & Session Peak Power Telemetry (`REQ-TRK-058.2`)*:
• At 1Hz during active tracking, the system SHALL calculate current rolling mean power (`POWER_5S`, `POWER_1M`, `POWER_5M`, `POWER_20M`) and update session peak power (`POWER_PEAK_5S`, `POWER_PEAK_1M`, `POWER_PEAK_5M`, `POWER_PEAK_20M`).
• In accordance with Rule 52 (`REQ-TRK-038`), virtual metric devices SHALL NOT emit values to `MySensor` before valid power telemetry is received from a connected physical sensor.
3. *SensorType Registration & 9-Language Localization Parity (`REQ-TRK-058.3`)*:
• The system SHALL register 8 new `SensorType` entries in `SensorType.java` under `SensorCategory.POWER_AND_PEDALING` with formatters, icons, and 100% 9-language localization parity across EN, DE, ES, FR, IT, JA, NL, PL, PT.
4. *Live Graphical Vector MMP Curve Cockpit Component (`REQ-TRK-058.4`)*:
• The tracking cockpit SHALL provide a Jetpack Compose vector chart component (`LiveMmpCurveChart`) rendering the live session power-duration curve on a logarithmic time scale ($1\text{s} \dots 7200\text{s}$).
• The chart SHALL display visual callout pins for benchmark durations (5s, 1m, 5m, 20m) and visually highlight session breakthrough peaks without snapshot state writes during composition (`Rule 66` / `ATT-4863`).
5. *Pause Lifecycle Invariant & Session Reset Parity (`REQ-TRK-058.5`)*:
• During recording suspension (`isSuspended`), accumulators and peak tracking SHALL freeze without accumulating zero-watt samples or corrupting running sums.
• In accordance with Rule 51, `resetSession()` SHALL clear all accumulators, ring buffers, and session peak registers to ensure clean-state parity across workout boundaries.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement `REQ-TRK-058`, extending `REQ-TRK-019` (W/kg), `REQ-TRK-020` (NP/IF), `REQ-TRK-021` (Work/TSS), and `REQ-TRK-023` (Warm-Up Window). Targets `CyclingPowerMetricsDevice.java`, `SensorType.java`, `LiveMmpCurveChart.kt`, and `strings*.xml`.
2. *Historical Origin & Commit Trace*: `ATT-1289` (commit `5eee662f`), `ATT-1290` (commit `4fe4996c`), `ATT-1291`, and `ATT-3778` established baseline power metric architecture.
3. *Root Reason for Existing Formulation*: Prior tickets implemented Coggan summary metrics and $W'_{\text{bal}}$; real-time benchmark durations and live MMP curves were scoped for Epic `ATT-5908`.
4. *Preservation of Core Invariants*: 1-Hz sampling rate (`REQ-TRK-007`), pause suspension, Rule 40 growing window, Rule 52 sensor-presence gating, and non-blocking main-thread performance are strictly preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an active cycling workout with incoming power samples at 1Hz,
• *When* the first 5 seconds elapse with power [200, 250, 300, 350, 400] W,
• *Then* `POWER_5S` emits rolling mean 300 W, and `POWER_PEAK_5S` tracks 300 W.
• *Given* an active workout at $t = 3\text{s}$ before the 5-second window is full,
• *When* power is received,
• *Then* `POWER_5S` emits the average of the 3 available samples without blank `"--"` delay (`Rule 40`).
• *Given* an athlete pauses tracking,
• *When* paused,
• *Then* sliding window accumulators freeze without adding 0 W samples, preserving rolling means and session peaks.
• *Given* `resetSession()` is called,
• *When* reset executes,
• *Then* all accumulators and peak registers clear, and sensors emit `null`.
• *Given* the live MMP curve chart rendered in the cockpit,
• *When* composing or updating live telemetry,
• *Then* the chart plots power on a logarithmic duration axis with callout markers for 5s, 1m, 5m, and 20m, producing zero recomposition churn (`Rule 66`).

**Invariants**: Existing power telemetry (`POWER`, `NP`, `IF`, `WORK_KJ`, `TSS`, `W_PRIME_BAL`) preserved; pause freeze integrity; 100% 9-language translation parity; zero composition snapshot state writes.




