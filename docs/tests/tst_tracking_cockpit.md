# Living Test Specification: Tracking & Live Sensor Cockpit

**Domain**: `tracking_cockpit`  
**Prefixes**: `CON`, `FIL`, `FUSION`, `SEN`, `STP`, `TRK`, `WEA`  
**Scope**: Live sensor telemetry, Bluetooth/ANT+ connectivity, filtering, GPS/tracking lifecycle.  
**Total Tests**: 54

---

### TST-FIL-002: WorkoutSamplesDb [Verified]
- **Requirement(s)**: `REQ-DAT-003`
- **Component**: `WorkoutSamplesDb`
- **Test Description**: Verify that `calcExtremaValue` returns `null` (not 0.0) when querying a sensor that has only NULL values or does not exist in the table.

### TST-FIL-001: **Null-Safe Filter Verification** [1. The system MUST NOT throw a NullPointerException. 2. The filtered value SHALL remain consistent with previous data or return null if no valid data exists.]
- **Requirement(s)**: `REQ-FIL-011`
- **Component**: **Null-Safe Filter Verification**
- **Jira**: ``ATT-275``
- **Test Description**: 1. Execute `AverageFilterTest` (or similar unit test). 2. Pass `null` as a new value to the filter. 3. Call `getFilteredValue`.

### TST-FIL-003: **Atomic Altitude Correction** [1. Samples table altitudes MUST be shifted by +100m. 2. Summary Min/Max altitude MUST be updated. 3. Encoded altitude stream MUST reflect the shift.]
- **Requirement(s)**: `REQ-FIL-004`
- **Component**: **Atomic Altitude Correction**
- **Jira**: ``ATT-425``
- **Test Description**: 1. Start a workout with barometric altitude at 100m. 2. Record data for 1 minute. 3. Trigger a correction to 200m (e.g. by nearing a known location). 4. Stop workout and inspect summary.

### TST-STP-001: **Localization** [Adhere to Google Play character limits.]
- **Requirement(s)**: `REQ-STP-001`
- **Component**: **Localization**
- **Test Description**: Review generated markdown files in `docs/store_presence/`. Verify that Title, Short Description and Full Description are correctly translated.

### TST-STP-002: **Messaging Review** [Description is punchy, highlights key features (Automation, Privacy, Clusters), and maintains professional tone.]
- **Requirement(s)**: `REQ-STP-002`
- **Component**: **Messaging Review**
- **Jira**: ``ATT-276``
- **Test Description**: 1. Compare new Play Store description with user's draft and project vision.

### TST-CON-002: **Barometric Cold-Start Baseline Protection Audit** [Cold-start uncalibrated pressure readings cannot contaminate session extrema before reference anchor or calibration is established.]
- **Requirement(s)**: `REQ-CON-011`
- **Component**: **Barometric Cold-Start Baseline Protection Audit**
- **Jira**: ``ATT-508``
- **Test Description**:
  1. Verify `AltitudeFromPressureDevice` does not publish uncalibrated standard atmosphere altitude (using 1013.25 hPa assumption) to session listeners or running statistics before baseline calibration.
  2. Simulate cold start with sea level pressure = 1020.8 hPa (raw altitude = -63.0 m); verify that -63 m is suppressed from session extrema.
  3. Execute unit tests in `AltitudeFromPressureDeviceTest.kt`.

### TST-TRK-002: **Workout Lap Lifecycle, Pause Neutrality & Zero-Duration Phantom Prevention** [1. Pausing and resuming does not split or increment laps.
2. Single-lap tracked workouts contain exactly 1 lap with correct duration and distance, with 0 phantom laps.
3. Workouts with $N$ manual laps contain exactly $N+1$ laps.
4. `LapsDatabaseManager` rejects zero-duration writes and filters legacy zero-duration rows.
5. 100% of unit tests pass with zero regressions.]
- **Requirement(s)**: `REQ-TRK-002`
- **Component**: **Workout Lap Lifecycle, Pause Neutrality & Zero-Duration Phantom Prevention**
- **Jira**: ``ATT-896``
- **Test Description**:
  1. *Pause/Resume Neutrality Unit Test (`TrainingApplicationLapTest.kt`)*:
  • Verify that `TrainingApplication.pauseTracking()` transitions `cTrackingMode` to `PAUSED` without broadcasting `REQUEST_NEW_LAP`.
  • Verify that `TrainingApplication.resumeFromPaused()` transitions `cTrackingMode` to `TRACKING` without broadcasting `REQUEST_NEW_LAP`.
  2. *Workout Finalization & Zero-Duration Guard Unit Test (`TrackerServiceLapTest.kt`)*:
  • Simulate workout tracking with 1 active lap. Pause tracking, then trigger `TrackerService.endWorkout()`.
  • Verify that `endWorkout()` / `createNewLap()` does NOT persist a second zero-duration lap to `LapsDatabaseManager`.
  • Verify that for a workout with duration 1200s and distance 3000m without manual laps, exactly 1 lap record is written with 1200s and 3000m.
  • Verify that for a workout with 1 manual lap at 500s and final stop at 1200s, exactly 2 laps are written (Lap 1: 500s, Lap 2: 700s) and zero phantom laps.
  3. *Database Input Guard & Query Filtering Unit Test (`LapsDatabaseManagerGuardTest.kt`)*:
  • Invoke `LapsDatabaseManager.saveLap(workoutId, 1, 0, 0.0, 0.0)`: verify 0 rows are inserted into `Laps.TABLE`.
  • Directly insert a raw legacy row with `timeTotal_s = 0, distanceTotal_m = 0.0` into `Laps.TABLE`. Query `getLaps(workoutId)` and `getLapsForWorkouts(listOf(workoutId))`: verify the zero-duration record is excluded from results.
  4. *Clean-Room Regression Verification*:
  • Execute full clean-room unit test suite (`./gradlew testDebugUnitTest`): BUILD SUCCESSFUL (0 regressions).

### TST-TRK-003: **Lieblingsorte Start & Destination Workout Auto-Naming Unit & Localization Tests** [Unclustered recorded activities and new cluster proposals automatically receive meaningful, localized names based on recognized Lieblingsorte across all sport profiles and languages; established cluster names and manual user edits remain strictly intact; zero test regressions.]
- **Requirement(s)**: `REQ-TRK-011`
- **Component**: **Lieblingsorte Start & Destination Workout Auto-Naming Unit & Localization Tests**
- **Jira**: ``ATT-1398``
- **Test Description**:
  1. *Round-Trip Auto-Naming Test (`testGenerateWorkoutName_roundTrip_formatsCorrectly`)*:
  • Mock `startLocation` and `endLocation` resolving to identical location \"Zuhause\".
  • Verify RUN formats to \"Lauf ab Zuhause\" (DE) / \"Run from Zuhause\" (EN).
  • Verify BIKE formats to \"Fahrt ab Zuhause\" (DE) / \"Ride from Zuhause\" (EN).
  • Verify UNKNOWN/other formats to \"Runde ab Zuhause\" (DE) / \"Loop from Zuhause\" (EN).
  2. *Point-to-Point Auto-Naming Test (`testGenerateWorkoutName_pointToPoint_formatsCorrectly`)*:
  • Mock `startLocation` = \"Zuhause\" and `endLocation` = \"Büro\".
  • Verify RUN formats to \"Lauf von Zuhause nach Büro\" (DE) / \"Run from Zuhause to Büro\" (EN).
  • Verify BIKE formats to \"Fahrt von Zuhause nach Büro\" (DE) / \"Ride from Zuhause to Büro\" (EN).
  3. *Start-Only & Destination-Only Fallback Tests*:
  • Verify start-only formats to \"Lauf ab Zuhause\" / \"Fahrt ab Zuhause\".
  • Verify end-only formats to \"Lauf nach Büro\" / \"Fahrt nach Büro\".
  4. *Neither-Known Fallback Test*:
  • When start and end are null, verify generator returns null, preserving base file name fallback.
  5. *New Route Cluster Seeding Test (`testSuggestCluster_seedsLoopFromLocationName`)*:
  • Create unclustered track starting at known location \"Olympiapark\".
  • Verify default cluster proposal name is \"Runde ab Olympiapark\" / \"Loop from Olympiapark\".
  6. *9-Language Localization Audit*:
  • Verify all auto-naming format templates exist across all 9 supported application locales (EN, DE, ES, FR, IT, JA, NL, PL, PT) with 0 missing keys or broken specifiers.
  7. *Clean-Room Regression Suite*:
  • Execute full unit test suite (`./gradlew testDebugUnitTest`) to guarantee 100% pass rate.

### TST-TRK-004: **Distance-Enriched Auto-Generated Workout Names for Start-Location Sessions Verification** [Start-location and round-trip auto-generated workout names cleanly append session distance in the user's preferred unit (km / mi); point-to-point and destination-only names remain uncluttered; backward-compatible calls without distance preserved; 9-language translation parity verified; 100% test pass rate.]
- **Requirement(s)**: `REQ-TRK-012`
- **Component**: **Distance-Enriched Auto-Generated Workout Names for Start-Location Sessions Verification**
- **Jira**: ``ATT-2247``
- **Test Description**:
  1. *Metric & Imperial Distance Unit Formatting Unit Tests (`WorkoutAutoNamingHelperTest.kt`)*:
  • Verify Metric distances $< 10.0\text{ km}$ format with 1 decimal place (e.g. `8.2 km`).
  • Verify Metric distances $\ge 10.0\text{ km}$ integer format with 0 decimal places (e.g. `42 km`).
  • Verify Metric distances $\ge 10.0\text{ km}$ non-integer format with 1 decimal place (e.g. `42.5 km`).
  • Verify Imperial distances $< 10.0\text{ mi}$ format with 1 decimal place (e.g. `5.1 mi`).
  • Verify Imperial distances $\ge 10.0\text{ mi}$ integer format with 0 decimal places (e.g. `25 mi`).
  • Verify Imperial distances $\ge 10.0\text{ mi}$ non-integer format with 1 decimal place (e.g. `26.2 mi`).
  2. *Start-Location & Round-Trip Distance Enrichment Tests (`WorkoutAutoNamingHelperTest.kt`)*:
  • Verify Case 2 (Round Trip / Loop) appends formatted distance for `BSportType.RUN` (`"Run from Zuhause (42 km)"`), `BSportType.BIKE` (`"Ride from Zuhause (42 km)"`), and other (`"Loop from Zuhause (42 km)"`).
  • Verify Case 3 (Start Location Only) appends formatted distance for `BSportType.RUN` (`"Run from Büro (8.2 km)"`), `BSportType.BIKE` (`"Ride from Büro (8.2 km)"`), and other (`"Activity from Büro (8.2 km)"`).
  • Verify Case 1 (Point-to-Point) does NOT append distance (e.g. `"Ride from Zuhause to Büro"`).
  • Verify Case 4 (Destination Only) does NOT append distance (e.g. `"Run to Büro"`).
  • Verify backward-compatible calls (`distanceTotalMeters == 0.0`) produce standard names without distance per `REQ-TRK-011`.
  3. *TrackerService Integration Wire Verification*:
  • Verify `TrackerService.java` passes `mDistanceTotal_m` to `WorkoutAutoNamingHelper.generateWorkoutName`.
  4. *9-Language Localization Audit*:
  • Verify `workout_name_run_from_with_distance`, `workout_name_ride_from_with_distance`, `workout_name_activity_from_with_distance`, and `workout_name_round_trip_with_distance` exist across all 9 locales (EN, DE, ES, FR, IT, JA, NL, PL, PT) with `%1$s` and `%2$s` format specifier parity.
  5. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` verifying 100% pass rate with zero regressions.

### TST-TRK-005: **GPS Speed Inactivity Watchdog, Progressive Decay & Recovery Verification** [Speed decays smoothly to 0.0 m/s after 5 seconds of location silence; pace sensor cleanly nullifies without division-by-zero; fresh location immediately resumes active speed calculation; zero phantom distance accumulated; 100% test pass rate.]
- **Requirement(s)**: `REQ-TRK-013`
- **Component**: **GPS Speed Inactivity Watchdog, Progressive Decay & Recovery Verification**
- **Jira**: ``ATT-2616``
- **Test Description**:
  1. *Continuous Fix Speed Stability Unit Test (`SpeedAndLocationDeviceDecayTest.kt`)*:
  • Initialize `SpeedAndLocationDevice` and emit continuous valid location fixes at 1.0-second intervals.
  • Verify active speed (`mSpeed`) and `mSpeedSensor` remain stable and watchdog does not trigger decay.
  2. *Inactivity Timeout & Progressive Decay Test (`SpeedAndLocationDeviceDecayTest.kt`)*:
  • Emit a location fix with speed 10.0 m/s.
  • Advance virtual time by 5.0 seconds (timeout threshold).
  • Verify decay begins: tick 1 (at 5.0s) halves speed to 5.0 m/s; tick 2 (at 6.0s) halves speed to 2.5 m/s; tick 3 (at 7.0s) halves speed to 1.25 m/s; tick 4 (at 8.0s) halves speed to 0.625 m/s.
  • Verify subsequent ticks continue until speed drops below 0.1 m/s, where it is clamped strictly to 0.0 m/s.
  3. *Pace Sensor Nullification & Zero-Division Safety Test (`SpeedAndLocationDeviceDecayTest.kt`)*:
  • When speed decays to 0.0 m/s, verify `mPaceSensor.getValue()` is strictly `null` (no `Double.POSITIVE_INFINITY` or NaN emitted).
  • Verify cockpit pace display displays fallback dashes (`--:--`).
  4. *Immediate Recovery on Fresh Location Fix Test (`SpeedAndLocationDeviceDecayTest.kt`)*:
  • Following complete speed decay to 0.0 m/s, emit a new valid location fix with speed 6.0 m/s.
  • Verify speed recovers immediately to 3.0 m/s (smoothed) or appropriate formula, pending decay ticks are cancelled, and pace sensor resumes emitting valid numeric pace.
  5. *Distance Invariant Verification (`SpeedAndLocationDeviceDecayTest.kt`)*:
  • Verify that throughout the inactivity timeout and decay ticks, `mDistanceSensor` and `mLapDistanceSensor` accumulate zero phantom distance.
  6. *Clean Teardown & Lifecycle Verification (`SpeedAndLocationDeviceDecayTest.kt`)*:
  • Trigger `device.shutDown()` during an active decay state.
  • Verify all watchdog callbacks/tasks are cancelled and no memory leaks or post-termination callbacks occur.
  7. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` verifying 100% pass rate with zero regressions.

### TST-TRK-006: **Successive Geodesic Location Delta Fallback for Missing GPS Bearing & Speed Verification** [`SpeedAndLocationDevice` reliably calculates forward bearing and speed from location deltas when hardware/test provider fixes omit them; stationary fixes retain compass heading without snapping to North; hardware non-zero metrics take precedence; 100% unit test pass rate.]
- **Requirement(s)**: `REQ-TRK-014`
- **Component**: **Successive Geodesic Location Delta Fallback for Missing GPS Bearing & Speed Verification**
- **Jira**: ``ATT-2467``
- **Test Description**:
  1. *Geodesic Bearing Fallback Unit Tests (`SpeedAndLocationDeviceDecayTest.kt`)*:
  • Verify that when incoming `Location` fixes have `bearing == 0.0f` (e.g. from system mock location or hardware GNSS without heading) and successive displacement exceeds $0.5\text{ m}$, `SpeedAndLocationDevice` derives forward azimuth bearing from `(mPrevLocation.bearingTo(location) + 360.0) % 360.0` and updates `mBearingSensor` (e.g. moving East produces $90.0^\circ$).
  • Verify that when stationary or paused (displacement $\le 0.5\text{ m}$), `mBearingSensor` retains the previous valid bearing value rather than snapping to $0.0^\circ$ (North).
  • Verify that when incoming fix provides a non-zero hardware bearing (e.g. $180.0^\circ$), the hardware bearing is preserved directly without fallback override.
  2. *Geodesic Speed Fallback Unit Tests (`SpeedAndLocationDeviceDecayTest.kt`)*:
  • Verify that when incoming `Location` fixes have `speed == 0.0f` and successive displacement exceeds $0.1\text{ m}$ with valid time delta ($0 < dt < 30000\text{ ms}$), `SpeedAndLocationDevice` calculates speed from $\Delta d / \Delta t$ and updates `mSpeedSensor` with exponential smoothing.
  • Verify that when displacement $\le 0.1\text{ m}$, speed falls back to $0.0\text{ m/s}$.
  • Verify plausibility clamping: unrealistic velocity jumps $> 70.0\text{ m/s}$ ($252\text{ km/h}$) are ignored.
  • Verify that when incoming fix provides a non-zero hardware speed (e.g. $5.0\text{ m/s}$), the reported hardware speed is preserved directly.
  3. *Navigation & Route Auto-Detection Integration Wire (`TrackingTabsScreen.kt`)*:
  • Verify that `TrackingTabsScreen` injects current bearing and speed into `Location` before passing to `routeSelectorViewModel.onLocationChanged(location)`, ensuring route matching algorithms and navigation chevrons evaluate valid motion vectors.
  4. *Full Clean-Room Regression Suite*:
  • Execute `./gradlew testDebugUnitTest` verifying 100% test pass rate with zero regressions across all tracking and route navigation test suites.

### TST-TRK-007: **Permission-Gated Foreground Service Types Verification** [`TrackerService` gates Android 14+ FGS health type by runtime permission, preventing fatal `SecurityException` during foreground promotion; 100% unit test pass rate.]
- **Requirement(s)**: `REQ-TRK-015`
- **Component**: **Permission-Gated Foreground Service Types Verification**
- **Jira**: ``ATT-2972``
- **Test Description**:
  1. *Missing Health Runtime Permission Unit Test (`TrackerServiceFGSTypeTest.kt`)*:
  • Initialize `TrackerService` without `ACTIVITY_RECOGNITION` runtime permission.
  • Invoke `determineForegroundServiceType()` on API 34+.
  • Verify bitmask omits `FOREGROUND_SERVICE_TYPE_HEALTH` and includes `LOCATION` and `CONNECTED_DEVICE` (if Bluetooth held).
  2. *Granted Health Runtime Permission Unit Test (`TrackerServiceFGSTypeTest.kt`)*:
  • Initialize `TrackerService` with `ACTIVITY_RECOGNITION` runtime permission granted.
  • Invoke `determineForegroundServiceType()` on API 34+.
  • Verify bitmask includes `FOREGROUND_SERVICE_TYPE_HEALTH`.
  3. *Pre-API 34 Legacy Unit Test (`TrackerServiceFGSTypeTest.kt`)*:
  • Test on API < 34.
  • Verify `determineForegroundServiceType()` returns `0`.
  4. *Full Clean-Room Regression Suite*:
  • Execute `./gradlew testDebugUnitTest` verifying 100% test pass rate with zero regressions.

### TST-TRK-008: **Guaranteed Workout Finalization and Completion Broadcast Test** [Workout termination reliably finalizes session in SQLite and broadcasts completion intent; false unclosed workouts eliminated; 100% unit test pass rate.]
- **Requirement(s)**: `REQ-TRK-016`
- **Component**: **Guaranteed Workout Finalization and Completion Broadcast Test**
- **Jira**: ``ATT-2972``
- **Test Description**:
  1. *End-Workout Finalization Unit Test (`TrackerServiceFinalizationTest.kt`)*:
  • Trigger `endWorkout()` for an active workout session.
  • Verify `WorkoutSummaries.FINISHED = 1` is updated in SQLite.
  • Verify `TRACKING_FINISHED_INTENT` is broadcast with `WORKOUT_ID`.
  2. *Defensive Try-Finally Resilience Unit Test (`TrackerServiceFinalizationTest.kt`)*:
  • Inject an exception in auxiliary operations during `endWorkout()`.
  • Verify `FINISHED = 1` and `TRACKING_FINISHED_INTENT` are still dispatched via `finally`.
  3. *OnDestroy Lifecyle Invariant Unit Test (`TrackerServiceFinalizationTest.kt`)*:
  • Verify normal service teardown triggers `endWorkout()` and resets state cleanly.
  4. *Full Clean-Room Regression Suite*:
  • Execute `./gradlew testDebugUnitTest` verifying 100% test pass rate with zero regressions.

### TST-CON-003: **BLE GATT Lifecycle, Thread-Safe Concurrency & Null-Safety Unit Tests** [Robust execution across all BLE lifecycle transitions with 0 NPEs.]
- **Requirement(s)**: `REQ-CON-012`
- **Component**: **BLE GATT Lifecycle, Thread-Safe Concurrency & Null-Safety Unit Tests**
- **Jira**: ``ATT-1348``
- **Test Description**:
  1. *Null GATT Resilience*: Instantiate `MyBTLEDevice` with null `mBluetoothGatt`; populate `mReadCharacteristicQueue`; invoke `readNextCharacteristic()`; drain main Looper; verify zero NPEs.
  2. *TOCTOU Race Simulation*: Trigger `readNextCharacteristic()`; nullify `mBluetoothGatt` concurrently before the Looper executes the runnable; verify the captured local snapshot prevents crash.
  3. *Disconnection Draining*: Enqueue characteristics; trigger `onConnectionStateChange(..., STATE_DISCONNECTED)`; verify `mReadCharacteristicQueue` is empty and `mBluetoothGatt` is null.
  4. *Delayed Battery Callback Cancellation*: Trigger battery update; verify delayed runnable is scheduled; trigger disconnection; verify delayed runnable is cancelled from `mHandler` and does not run.
  5. *Empty Queue Descriptor Safety*: Invoke empty queue branch when `mBluetoothGatt` is null or service/characteristic is missing; verify zero crashes.

### TST-CON-004: **Barometric Altitude Sensor Initialization & Null-Safe Correction Dispatch Test** [Hardware barometric sensor initialization and altitude correction dispatch are 100% null-safe; cold-start NPE and native JNI SIGABRT aborts are eliminated; downstream atomic shift and reference learning invariants remain fully intact.]
- **Requirement(s)**: `REQ-CON-013`
- **Component**: **Barometric Altitude Sensor Initialization & Null-Safe Correction Dispatch Test**
- **Jira**: ``ATT-1353``
- **Test Description**:
  1. *Cold-Start Null Sensor Value Resilience*: Verify that invoking `setAltitudeCorrection(correctAltitude)` when `mAltitudeSensor.getValue()` is null and `mLastRawAltitude` is a valid number (e.g. 500.0m) calculates `mAltitudeCorrection = correctAltitude - mLastRawAltitude` (e.g. +20.0m for reference 520.0m) without throwing `NullPointerException`.
  2. *Subsequent Non-Null Value Calibration*: Verify that invoking `setAltitudeCorrection(correctAltitude)` when `mAltitudeSensor.getValue()` has an existing reading (e.g. 505.0m) calculates `mAltitudeCorrection = correctAltitude - currentAltitude` (+15.0m for reference 520.0m).
  3. *Double-Fault Graceful Degradation*: Verify that invoking `setAltitudeCorrection(correctAltitude)` when `mAltitudeSensor.getValue()` is null and `mLastRawAltitude` is `Double.NaN` logs a warning and returns cleanly without mutating `mAltitudeCorrection` or throwing any exception.
  4. *Broadcast Dispatch Integrity*: Verify that `ALTITUDE_CORRECTION_INTENT` is broadcast with `ALTITUDE_CORRECTION_VALUE` matching the computed non-zero correction.
  5. *Zero Correction Suppression*: Verify that when computed correction is 0.0, no broadcast is dispatched.
  6. *Sensor Event Integration*: Verify that in `onSensorChanged()`, the initial pressure event with known location initializes cleanly, sets `mPressureSensorInitialized = true`, updates `mAltitudeSensor` with reference altitude, and dispatches the correction atomically.
  7. *Regression Suite*: Execute full test suite (`./gradlew testDebugUnitTest`) to ensure zero regressions across all modules.

### TST-CON-005: **BLE GATT Measurement Packet Parsing Resilience & Null-Safe Telemetry Test** [All BLE characteristic measurement updates are protected against truncated packets and null-unboxing NPEs across all BLE sensor profiles; production crash ATT-1485 is completely eliminated; valid telemetry calculations remain 100% intact.]
- **Requirement(s)**: `REQ-CON-014`
- **Component**: **BLE GATT Measurement Packet Parsing Resilience & Null-Safe Telemetry Test**
- **Jira**: ``ATT-1485``
- **Test Description**:
  1. *Production Truncated Crank Crash Reproduction (`testBikePower_truncatedCrankData_doesNotCrashAndDiscardsGracefully`)*:
  • Initialize `BTLEBikePowerDevice` with cadence enabled.
  • Provide notification with flags `0x0020` (crank present), power 200W, and truncated 2-byte crank data missing timestamp (6 bytes total instead of 8).
  • Verify 0 exceptions thrown, power parsed as 200W, crank parsing skipped cleanly without NPE.
  2. *Truncated Wheel Data Test (`testBikePower_truncatedWheelData_doesNotCrash`)*:
  • Provide notification with flags `0x0010` (wheel present) and only 4 bytes total payload.
  • Verify 0 exceptions thrown, power parsed cleanly, wheel skipped.
  3. *Truncated Power Balance Test (`testBikePower_truncatedPowerBalance_doesNotCrash`)*:
  • Flags `0x0001` (balance present), 4 bytes total payload.
  • Verify 0 exceptions thrown, balance skipped.
  4. *Header Length Guard Test (`testBikePower_emptyOrTruncatedHeader_doesNotCrash`)*:
  • Payloads of length 0, 1, 2, 3 bytes and null value.
  • Verify immediate safe return with 0 exceptions.
  5. *Specification Conformance Test (`testBikePower_validFullPacket_parsesAllSensorsCorrectly`)*:
  • Valid Cycling Power notification containing power, balance, wheel, and crank data.
  • Verify telemetry is computed and emitted with 100% mathematical fidelity.
  6. *Sister BLE Device Parity Tests*:
  • `testBikeDevice_truncatedPackets_doesNotCrash` for `BTLEBikeDevice`.
  • `testRunSpeedDevice_truncatedPackets_doesNotCrash` for `BTLERunSpeedDevice`.
  • `testHeartRateDevice_truncatedPackets_doesNotCrash` for `BTLEHeartRateDevice`.
  7. *Clean-Room Regression Suite*:
  • Execute `./gradlew testDebugUnitTest` to guarantee 0 regressions across all test suites.

### TST-CON-006: **Smartphone Battery Level & Remaining Duration Telemetry Unit & Localization Tests** [Smartphone battery level and remaining tracking duration telemetry are 100% verified across all hardware states (discharging, charging, warmup, idle) with zero memory leaks, zero wake-locks, and full 9-language localization parity.]
- **Requirement(s)**: `REQ-CON-015`
- **Component**: **Smartphone Battery Level & Remaining Duration Telemetry Unit & Localization Tests**
- **Jira**: ``ATT-1454``
- **Test Description**:
  1. *Battery Broadcast Parsing Test (`testBatteryLevel_broadcastReceived_updatesPhoneBatterySensor`)*:
  • Provide mock battery changed Intent with level = 75, scale = 100.
  • Verify `PHONE_BATTERY` sensor receives integer value 75 and formats to "75 %".
  2. *Stabilization Fallback Test (`testRemainingDuration_warmupPeriod_displaysDashes`)*:
  • Simulate active recording elapsed time < 300s (5 minutes).
  • Verify `BATTERY_REMAINING_TIME` emits fallback `--:--`.
  3. *Drain Rate & Remaining Time Projection Test (`testRemainingDuration_activeDrain_projectsRemainingHoursAndMinutes`)*:
  • Simulate active recording where 5% battery drains in 60 minutes (5.0%/h drain rate) with current battery at 50%.
  • Verify projected remaining time is 10 hours (`10:00 h`).
  4. *Charging State Detection Test (`testRemainingDuration_deviceCharging_displaysChargingOrDashes`)*:
  • Provide battery Intent with `BATTERY_STATUS_CHARGING`.
  • Verify sensor displays "Lädt" (DE) / "Charging" (EN) and does not project depletion time.
  5. *Zero/Negative Drain Test (`testRemainingDuration_zeroDrain_displaysDashes`)*:
  • Simulate battery level constant over active session without charging status.
  • Verify sensor displays `--:--`.
  6. *9-Language Localization Audit*:
  • Verify string resources for sensor names, short names, units, and fallback strings across all 9 supported locales (`de`, `en`, `es`, `fr`, `it`, `ja`, `nl`, `pl`, `pt`).
  7. *Clean-Room Regression Suite*:
  • Run `./gradlew testDebugUnitTest` to guarantee 100% pass rate.

### TST-CON-007: **Battery Remaining Duration Monotonic Countdown and Step-Transition Recalibration Verification** [Battery remaining time counts down monotonically during constant battery percentage without upward drift; step-down transitions recalibrate rate smoothly; floor clamping protects against premature depletion; 100% test pass rate.]
- **Requirement(s)**: `REQ-CON-016`
- **Component**: **Battery Remaining Duration Monotonic Countdown and Step-Transition Recalibration Verification**
- **Jira**: ``ATT-1631``
- **Test Description**:
  1. *Monotonic Countdown Verification (`testRemainingDuration_constantPercentage_countsDownMonotonicallyWithoutDrift`)*:
  • Simulate active recording where a baseline drain rate is established (e.g. 5.0%/h at 80%).
  • Advance recording time by 300s, 600s, 900s while battery percentage remains constant at 80%.
  • Verify that `BATTERY_REMAINING_TIME` counts down steadily second by second and never increases.
  2. *Step Transition Recalibration Verification (`testRemainingDuration_stepDrop_recalibratesSmoothly`)*:
  • Trigger a battery step transition (e.g. 80% to 79%).
  • Verify that new drain rate is calculated based on step points and remaining time adjusts smoothly.
  3. *Lower Bound Floor Clamping Verification (`testRemainingDuration_floorClamping_preventsPrematureDepletion`)*:
  • Simulate an extended period at constant percentage exceeding the expected step interval.
  • Verify remaining time clamps at the floor of $(P - 1)$ and does not collapse to zero.
  4. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` across all modules to verify 100% test pass rate with 0 regressions.

### TST-CON-008: **Barometric Altimeter Idempotent Calibration, Delta Shift Dispatch, Physical Sanity Limiting & Mountain Climb Preservation Test** [Barometric calibration oscillations eliminated; runaway retroactive sample accumulation prevented; altimeter calibration gated to geofence transitions; physical sanity limits enforced; legitimate mountain climbs 100% preserved; thread leaks eliminated; 100% test pass rate.]
- **Requirement(s)**: `REQ-CON-017`
- **Component**: **Barometric Altimeter Idempotent Calibration, Delta Shift Dispatch, Physical Sanity Limiting & Mountain Climb Preservation Test**
- **Jira**: ``ATT-1810`, `ATT-3990``
- **Test Description**:
  1. *Absolute Baseline Correction Unit Test (`AltitudeFromPressureDeviceTest.kt`)*:
  • Set `lastRawAltitude = 450.0m`; call `setAltitudeCorrection(507.0)`.
  • Verify `altitudeCorrection` is set to 57.0m, broadcast dispatched with value 57.0, and sensor value equals 507.0m.
  2. *Oscillation Immunity Test (`AltitudeFromPressureDeviceTest.kt`)*:
  • Simulate 10 iterations of interleaved `handlePressureMeasurement(pressure)` and `setAltitudeCorrection(507.0)`.
  • Verify `altitudeCorrection` remains strictly 57.0m without oscillating to 0.0, sensor value remains locked at 507.0m, and zero additional broadcasts are dispatched.
  3. *Idempotent Calibration Guard Test (`AltitudeFromPressureDeviceTest.kt`)*:
  • Call `device.calibrate(507.0)` when already calibrated within 0.5m.
  • Verify returns `true` immediately and emits zero broadcasts.
  4. *Geofence Transition Calibration Gating Unit Test (`TrackingTabsViewModelLocationTest.kt`)*:
  • Emit 5 consecutive GPS location fixes inside the same geofence.
  • Verify `banalServiceRepository.calibrateAltimeter` is invoked exactly once upon transition and suppressed on subsequent updates until geofence changes.
  5. *Physical Sanity Clamping & Atomic DB Transactions Test*:
  • Verify vertical rates $> 30\text{ m/s}$ and retroactive shifts $> 500\text{ m}$ are rejected/clamped, and SQLite batch updates run in an atomic transaction.
  6. *High-Altitude Climb Negative Test & Zero DB Open Mutation Test (`WorkoutSummariesDatabaseManagerSanitizationTest.kt`)*:
  • Provide a synthetic workout summary with an ascent profile from 600m to 4200m (`[600, 1200, 1800, 2400, 3000, 3600, 4200]`).
  • Trigger database open and query operations.
  • Verify 0 modifications are made, stream points remain exactly `[600, 1200, 1800, 2400, 3000, 3600, 4200]`, and extrema max remains 4200.0m.
  7. *Database Open Thread Leak Elimination Test (`WorkoutSummariesDatabaseManagerSanitizationTest.kt`)*:
  • Trigger `WorkoutSummariesDbHelper.onOpen(mockDb)` repeatedly.
  • Verify zero `Executors.newSingleThreadExecutor()` instances are created and zero background threads are leaked.
  8. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` across all modules verifying 0 test failures.

### TST-CON-009: **Robust Bluetooth LE Sensor Discovery: GATT Queue Deadlock Prevention and State Reset Test** [BLE device discovery queue deadlock eliminated; missing optional characteristics handled gracefully; state maps cleanly purged on search stop; 100% test pass rate.]
- **Requirement(s)**: `REQ-CON-018`
- **Component**: **Robust Bluetooth LE Sensor Discovery: GATT Queue Deadlock Prevention and State Reset Test**
- **Jira**: ``ATT-2224``
- **Test Description**:
  1. *Null Characteristic Enqueue Prevention Test (`testEnqueue_omitsNullCharacteristics`)*:
  • Mock `BluetoothGattService` returning `null` for `UUID_CHARACTERISTIC_MANUFACTURER_NAME`.
  • Invoke service discovery handling.
  • Verify characteristic queue contains zero null entries.
  2. *Queue Progression Resilience Test (`testReadNextCharacteristic_handlesNullAndReadFailureGracefully`)*:
  • Populate queue with a null characteristic followed by a valid characteristic where `readCharacteristic()` returns `false`.
  • Invoke `readNextCharacteristic(address)`.
  • Verify the queue advances immediately and drains completely without deadlocking.
  3. *Missing Optional Descriptors Discovery Test*:
  • Simulate Bike Power peripheral lacking Device Information and Battery services.
  • Verify `newDeviceFound(address)` is successfully invoked upon completing queue draining.
  4. *State Reset & Rediscovery Test (`testResetTrackingState_clearsAllMapsAndEnablesRediscovery`)*:
  • Populate `mBTGatts`, `mInformedDevices`, `mReadCharacteristicQueue`, `mNameMap`, `mManufacturerMap`, `mBatteryPercentage`.
  • Trigger `stopAsyncSearch()` / `resetTrackingState()`.
  • Verify all maps are emptied and subsequent scans redisover the same MAC address cleanly.
  5. *Clean-Room Regression Suite*:
  • Execute `./gradlew testDebugUnitTest` to guarantee 0 regressions across all test suites.

### TST-CON-010: **Continuous Sensor Battery State Persistence, Live Battery Display in Sensors List & Post-Workout Alerting Verification** [Continuous active session device persistence; real-time GATT battery notifications; live battery merging in sensors list; reliable post-workout alerts even for sensors disconnecting mid-session; 100% test pass rate.]
- **Requirement(s)**: `REQ-CON-007`
- **Component**: **Continuous Sensor Battery State Persistence, Live Battery Display in Sensors List & Post-Workout Alerting Verification**
- **Jira**: ``ATT-2192` / `ATT-3312``
- **Test Description**:
  1. *Live Battery Merging Unit Test (`GetMergedDevicesUseCaseTest.kt`)*:
  • Verify `mergedDevices` maps `batteryPercentage` from `telemetry.batteryPercentage` when $\ge 0$.
  • Verify `knownDevice.batteryPercentage` is preserved when telemetry battery is negative ($-1$).
  2. *DeviceDataRepository Reactive Battery Cache Invalidation (`DeviceDataRepositoryBatteryTest.kt`)*:
  • Verify `DeviceDataRepository.notifyBatteryUpdated(deviceId, percentage)` updates `_allDevices` in memory immediately without requiring a full SQLite query.
  3. *Continuous Active Session Device Persistence Unit Test (`ActiveDevicesDbHelperTest.kt`)*:
  • Verify `ActiveDevicesDbHelper` correctly persists active device IDs incrementally during tracking and retrieves them for `workoutId`.
  4. *Mid-Workout Disconnected Sensor Low Battery Alert Test (`SensorBatteryAlertTest.kt`)*:
  • Simulate active workout with sensor ID 1 reporting 15% (critical) and disconnecting before workout stop.
  • Verify `ActiveDevicesDbHelper` retains sensor ID 1 and `checkSensorBatteryAfterWorkout` flags sensor 1 as critical.
  • Verify normal sensor (80%) and inactive sensor in drawer (10%) are excluded.
  5. *Modal Dialog Elimination & In-App M3 Snackbar Test (`SensorBatteryAlertTest.kt`, `WorkoutNavigationEventsTest.kt`)*:
  • Verify zero `AlertDialog` invocations upon stopping.
  • Verify `WorkoutNavigationEvents.triggerLowBatteryAlert` emits sticky alert event and `consumeLowBatteryAlert()` clears it.
  • Verify snackbar action navigates to `R.id.drawer_my_sensors`.
  6. *System Notification Construction & Channel Test (`SensorBatteryNotificationManagerTest.kt`)*:
  • Verify `SensorBatteryNotificationManager.showLowBatteryNotification` constructs notification on channel `NOTIFICATION_CHANNEL__SENSOR_BATTERY` with ID 4300 and deep link intent.
  7. *Normal Battery Neutrality Test (`SensorBatteryAlertTest.kt`)*:
  • Simulate active sensors all having battery $> 20\%$.
  • Verify zero notifications and zero snackbars dispatched.
  8. *9-Language Localization Audit (`TranslationParityTest.kt`)*:
  • Verify 100% translation parity across EN, DE, ES, FR, IT, JA, NL, PL, PT.
  9. *Full Suite Clean-Room Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` verifying 100% pass rate.

### TST-CON-011: **Bluetooth LE Sensor Discovery, LE Transport Selection & Foreground Service Compliance Test** [BLE sensor discovery and reconnection use direct Low Energy transport (TRANSPORT_LE); scan results delivered unsuppressed without neverForLocation; runtime permissions checked safely; aggressive scan settings prevent OS batching; Android 14+ foreground service compliant; 100% test pass rate.]
- **Requirement(s)**: `REQ-CON-019`
- **Component**: **Bluetooth LE Sensor Discovery, LE Transport Selection & Foreground Service Compliance Test**
- **Jira**: ``ATT-2773`, `ATT-4449``
- **Test Description**:
  1. *TRANSPORT_LE Selection in MyBTLEDevice (`testStartSearching_onApi23Plus_connectsGattWithTransportLe`)*:
  • Verify that `MyBTLEDevice.startSearching()` passes `BluetoothDevice.TRANSPORT_LE` on Android 6.0+ (API 23+) when connecting GATT.
  • Verify fallback to 3-argument `connectGatt` on API < 23.
  2. *TRANSPORT_LE Selection in BTSearchForNewDevicesEngine (`testScanResult_onApi23Plus_connectsGattWithTransportLe`)*:
  • Verify that `BTSearchForNewDevicesEngine` uses `TRANSPORT_LE` when connecting to discovered devices on API 23+.
  3. *Runtime Permission Verification (`testStartAsyncSearch_missingBluetoothScanPermission_returnsWithoutCrash`)*:
  • Verify that missing `BLUETOOTH_SCAN` on API 31+ or `ACCESS_FINE_LOCATION` on API < 31 returns cleanly without throwing `SecurityException`.
  4. *Aggressive ScanSettings Configuration (`testStartAsyncSearch_grantedBluetoothScanPermission_configuresAggressiveSettings`)*:
  • Verify that `ScanSettings` has `SCAN_MODE_LOW_LATENCY`, `CALLBACK_TYPE_ALL_MATCHES`, and `MATCH_MODE_AGGRESSIVE` configured on API 23+.
  5. *Manifest & Foreground Service Integrity Audit*:
  • Verify `AndroidManifest.xml` lacks `neverForLocation` on `BLUETOOTH_SCAN` and includes `FOREGROUND_SERVICE_CONNECTED_DEVICE` for `BANALService` and `TrackerService`.
  6. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` across all modules verifying 100% pass rate with zero regressions.

### TST-FIL-004: **Stabilized VAM (Vertical Speed) Metric, Location Accuracy Gating & Indoor Drift Suppression Verification** [\text{raw\_vam}]
- **Requirement(s)**: `REQ-FIL-012`
- **Component**: **Stabilized VAM (Vertical Speed) Metric, Location Accuracy Gating & Indoor Drift Suppression Verification**
- **Jira**: ``ATT-1621`, `ATT-1738``
- **Test Description**:
  1. *Stationary Indoor Drift Suppression Tests (`VerticalSpeedAndSlopeDeviceTest.kt`)*:
  • Simulate stationary athlete (`speed = 0.0 m/s` or `speed = 0.2 m/s`) with altitude oscillating randomly by $\pm 0.5\text{ m}$ around 100m.
  • Verify `mVerticalSpeedSensor` broadcasts `0 m/h` and does not emit phantom climbing rates.
  2. *Location Accuracy Gating Verification (`VerticalSpeedAndSlopeDeviceTest.kt`)*:
  • Simulate athlete with degraded GPS accuracy ($> 20.0\text{ m}$, e.g. 25m or 35m) indoors/basement while altitude fluctuates.
  • Verify `calculateVam` suppresses vertical speed to `0 m/h` whenever GPS accuracy exceeds `VAM_MAX_GPS_ACCURACY_METERS = 20.0`.
  3. *Low-Speed Indoor GPS Wander Suppression Tests (`VerticalSpeedAndSlopeDeviceTest.kt`)*:
  • Simulate indoor wander with phantom horizontal speed between $0.5\text{ m/s}$ and $1.1\text{ m/s}$ ($2.0\dots 4.0\text{ km/h}$) and altitude jitter ($

### TST-TRK-009: **Guaranteed Defensive Location Device Initialization on Tracking Startup and BANAL Connection Verification** [Location devices reliably initialize and register update listeners when tracking begins after dynamic permission grants; GCData includes GPS/Distance/Speed; 100% test pass rate.]
- **Requirement(s)**: `REQ-TRK-017`
- **Component**: **Guaranteed Defensive Location Device Initialization on Tracking Startup and BANAL Connection Verification**
- **Jira**: ``ATT-3045``
- **Test Description**:
  1. *Permission Escalation Lazy GPS Instantiation Unit Test (`FreshInstallLocationInitTest.kt`)*:
  • Initialize `DeviceManager` with `ACCESS_FINE_LOCATION` revoked/false.
  • Verify `mSpeedAndLocationDevice_GPS` is null.
  • Grant permission and invoke `DeviceManager.checkOrInitializeLocationDevices()`.
  • Verify `mSpeedAndLocationDevice_GPS` is instantiated and sensors registered in `MySensorManager`..
  2. *TrackerService Connection Initialization Flow Contract Test (`FreshInstallLocationInitTest.kt`)*:
  • Simulate `TrackerService` startup connecting to `BANALService` via `mBanalConnection` on a freshly granted permission state.
  • Verify `BANALService.checkOrInitializeLocationDevices()` is invoked prior to `onSearchingFinished()`..
  • Verify `getGCDataString()` contains sensor flags `'G'`, `'D'`, and `'S'`..
  3. *GPS Provider Re-Registration Resilience Test (`FreshInstallLocationInitTest.kt`)*:
  • Initialize `SpeedAndLocationDevice_GPS` when GPS provider is disabled or unavailable.
  • Enable provider and invoke `checkOrReRegisterLocationUpdates()`.
  • Verify location manager update requests are successfully attached..
  4. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` verifying 100% test pass rate with zero regressions.

### TST-FIL-005: **Continuous Rolling Smoothing Telemetry, Session Reset Isolation & Live Pre-Start Metric Verification** [Rolling smoothing filters compute continuously in pre-start ready mode; cumulative session filters isolate workout statistics until active tracking; FilterManager executes reset() on RESET_ACCUMULATORS_INTENT; 100% unit test pass rate.]
- **Requirement(s)**: `REQ-FIL-013`
- **Component**: **Continuous Rolling Smoothing Telemetry, Session Reset Isolation & Live Pre-Start Metric Verification**
- **Jira**: ``ATT-3222``
- **Test Description**:
  1. *Rolling Smoothing Continuous Execution Unit Tests (`ExponentialSmoothingFilterTest.java`, `TimedMovingAverageFilterTest.java`, `NumberedMovingAverageFilterTest.java`)*:
  • When `TrainingApplication.isPaused() == true` (ready / paused mode), verify non-null values update `mFilteredValue` and `getFilteredValue()` returns smoothed values.
  • Verify passing `null` does not mutate filter state or throw `NullPointerException` (`REQ-FIL-011`).
  2. *Cumulative Aggregator Session Isolation Unit Tests (`AverageFilterTest.java`, `MaxValueFilterTest.java`)*:
  • When `TrainingApplication.isPaused() == true`, verify values are discarded without updating accumulated sum, sample count, or max value.
  • When active tracking starts (`isPaused() == false`), verify values accumulate normally.
  3. *Filter Lifecycle Reset Contract Unit Tests (`FilterResetTest.java`)*:
  • Verify `reset()` on `ExponentialSmoothingFilter` resets `filteredValue` to `0.0`.
  • Verify `reset()` on `TimedMovingAverageFilter` clears sample queue.
  • Verify `reset()` on `NumberedMovingAverageFilter` clears samples and resets index.
  • Verify `reset()` on `AverageFilter` resets sum and sample count.
  • Verify `reset()` on `MaxValueFilter` resets max value.
  4. *FilterManager Accumulator Reset Broadcast Contract Tests (`FilterManagerTest.java`)*:
  • Verify `FilterManager` registers receiver for `BANALService.RESET_ACCUMULATORS_INTENT`.
  • When broadcast is received, verify `reset()` is invoked across all filters.
  • Verify `shutDown()` safely unregisters receiver.
  5. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with zero regressions.

### TST-TRK-010: **Sport-Specific Auto-Pause Engine, Anti-Chatter Hysteresis, Unit Conversion & Mutex Verification** [Sport-specific auto-pause engine verified for sport defaults, decoupled thresholds and debounces, unit conversion, debounce filtering, hysteresis deadband with ceiling, synchronous sensor gating, moving average speed, sensor dropout/recovery with audit trail, mutex lock precedence, FIFO flush ordering, and deterministic crash recovery hierarchy; 100% unit test pass rate.]
- **Requirement(s)**: `REQ-TRK-018`
- **Component**: **Sport-Specific Auto-Pause Engine, Anti-Chatter Hysteresis, Unit Conversion & Mutex Verification**
- **Jira**: ``ATT-45`, `ATT-3677``
- **Test Description**:
  1. *Sport-Specific Default Activation & Decoupled Thresholds Tests (`AutoPauseManagerTest.kt`, `TuningPreferencesDataStoreAutoPauseTest.kt`)*:
  • Verify default preferences enable auto-pause for `BSportType.BIKE` and disable for `BSportType.RUN` and `BSportType.UNKNOWN`.
  • Verify default speed thresholds (Metric: Bike 3 km/h, Run 2 km/h, Other 1 km/h; Imperial: Bike 2 mph, Run 1 mph, Other 1 mph) and debounces (Bike 2s, Run 5s, Other 10s).
  • Verify integer stepping (1..5 user units for thresholds; 1..10s Bike, 1..20s Run, 1..60s Other for debounces).
  • Verify `AutoPauseManager` evaluates speed thresholds using sport-specific values and metric/imperial `UserUnit2mps` conversions.
  2. *Debounce Counter & Stationary Transition Tests (`AutoPauseManagerTest.kt`)*:
  • Simulate cycling speed drop to 0 m/s: verify 1-second tick remains in `PAUSE_DEBOUNCE_PENDING`; 2nd second tick transitions to `AUTO_PAUSED` and sets `isAutoPaused = true`.
  • Simulate running speed drop with 5s debounce: verify ticks 1..4 remain in `PAUSE_DEBOUNCE_PENDING`; 5th second tick transitions to `AUTO_PAUSED`.
  3. *Anti-Chatter Hysteresis Deadband & Ceiling Tests (`AutoPauseManagerTest.kt`)*:
  • While paused with $v_{\text{pause}} = 3\text{ km/h}$, simulate speed flicker at 3.1, 3.3, 3.4 km/h: verify session remains paused.
  • Simulate speed reaching 3.5 km/h ($v_{\text{pause}} + 0.5\text{ km/h}$) for 1s: verify auto-resume triggers.
  • For max threshold $v_{\text{pause}} = 5\text{ km/h}$, verify resume threshold clamps to $6.0\text{ km/h}$ physical ceiling in Metric ($4.0\text{ mph}$ in Imperial).
  4. *Sequential Synchronous Ingestion Gating & Elevation Invariant Tests (`AutoPauseManagerTest.kt`, `VerticalSpeedAndSlopeDeviceTest.kt`)*:
  • Verify `MyAccumulatorSensor.increment()` and `VerticalSpeedAndSlopeDevice.calculateMetrics()` drop updates synchronously when `isAutoPaused == true`.
  5. *Authoritative Moving Average Speed Preservation Tests (`WorkoutSummariesTest.kt`)*:
  • Verify $v_{\text{avg}} = \frac{\text{Distance}}{\text{TimeActive}}$ ignores paused duration.
  6. *Stale Packet Rejection, SENSOR_LOST & Audit Trail Tests (`AutoPauseManagerTest.kt`)*:
  • Verify sensor packet age $> 5000\text{ ms}$ transitions engine to `SENSOR_LOST`, logs state transition in session metadata, and suspends auto-pause.
  • Verify $\ge 2\text{ s}$ of fresh stable packets recovers engine to `TRACKING`.
  7. *Mutex Precedence & Manual Override Tests (`AutoPauseManagerTest.kt`, `TrainingApplicationTest.kt`)*:
  • Verify manual pause sets `mManualPauseLock = true` and blocks auto-resume.
  • Verify explicit user resume or "Resume Now" clears lock and resumes tracking.
  8. *Deterministic Startup State-Recovery Hierarchy Tests (`TrackerServiceTest.kt`)*:
  • When `mManualPauseLock == true` in DataStore, session restores in `MANUAL_PAUSED` regardless of last sample velocity.
  • When `mManualPauseLock == false` and `isAutoPaused == true`, session restores in `AUTO_PAUSED`.
  9. *FIFO Single-Threaded Database Flush Tests (`TrackerServiceTest.kt`)*:
  • Verify final stationary sample is written before paused state summary update.
  10. *Crash Recovery Debounce Counter Reset Tests (`AutoPauseManagerTest.kt`)*:
  • Verify crash during debounce resets counter and defaults to `TRACKING`.
  11. *Non-Finite Speed Sanitization Tests (`AutoPauseManagerTest.kt`)*:
  • Verify `NaN` and `Infinity` are rejected safely.
  12. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with zero regressions across all unit tests.

### TST-TRK-011: **Real-Time Power-to-Weight (W/kg) Calculation, Weight Persistence & Unit Parity Test Suite** [Real-time power-to-weight ratio verified for weight persistence in canonical kg, metric/imperial conversion, 1Hz computed metrics engine, pause gating, 1-decimal formatting, filter defaults, and 9-language localization parity.]
- **Requirement(s)**: `REQ-TRK-019`
- **Component**: **Real-Time Power-to-Weight (W/kg) Calculation, Weight Persistence & Unit Parity Test Suite**
- **Jira**: ``ATT-1289``
- **Test Description**:
  1. *DataStore Weight Persistence & Clamping Tests (`SettingsDataStoreWeightTest.kt`)*:
  • Verify default athlete weight is `0.0f`.
  • Verify saving valid weight (`75.0f` kg) persists and emits on `athleteWeightKgFlow`.
  • Verify clamping and validation ($30.0 \le \text{weight} \le 300.0\text{ kg}$).
  2. *Metric & Imperial Conversion Tests (`SettingsDataStoreWeightTest.kt`)*:
  • In Metric: 75.0 kg displays as "75.0 kg".
  • In Imperial: 75.0 kg displays as "165.3 lbs". Input of 165.3 lbs converts back to 74.98 ~ 75.0 kg.
  3. *Power-to-Weight Calculation & Pause Gating Tests (`CyclingPowerMetricsDeviceTest.kt`)*:
  • 300 W with 75.0 kg weight -> 4.0 W/kg.
  • 250 W with 65.0 kg weight -> 3.8 W/kg.
  • Zero power (0 W) -> 0.0 W/kg.
  • Unconfigured weight (0.0 kg) -> null / "-- W/kg".
  • When `isRecordingSuspended()` (paused or auto-paused) -> no new sample published.
  4. *PowerToWeightFormatter Tests (`PowerToWeightFormatterTest.kt`)*:
  • Null value -> "--".
  • 4.0 -> "4.0".
  • 3.849 -> "3.8".
  • Negative / NaN -> "--".
  5. *Sensor Filter Defaults Tests (`SensorFilterDefaultsTest.kt`)*:
  • Verify `POWER_TO_WEIGHT` defaults to 3-second moving average (`FilterType.MOVING_AVERAGE_TIME`, 3.0s).
  • Verify `SensorType.values()` iteration in `SensorFilterDefaultsTest` passes 100%.
  6. *Activity Type Sensor Inclusion Tests (`ActivityTypeTest.kt`)*:
  • Verify `SensorType.POWER_TO_WEIGHT` is in available sensor types for `ActivityType.BIKE_POWER`.
  7. *9-Language Localization Parity Tests (`TranslationParityTest.kt`)*:
  • Verify all new string keys (`power_to_weight`, `power_to_weight_short`, `units_power_to_weight`, `athlete_weight_title`, etc.) exist with matching format specifiers across all 9 locales.
  8. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with zero regressions across all unit tests.

### TST-TRK-012: **Real-Time Normalized Power (NP) and Intensity Factor (IF) Calculation, Algorithm Math & Formatter Test Suite** [Normalized Power (NP) and Intensity Factor (IF) verified across 30s rolling buffer, 4th power weighting, cumulative 4th power average, 4th root extraction, pause gating, unconfigured FTP handling, 2-decimal formatting, filter presets, and 9-language translation parity.]
- **Requirement(s)**: `REQ-TRK-020`
- **Component**: **Real-Time Normalized Power (NP) and Intensity Factor (IF) Calculation, Algorithm Math & Formatter Test Suite**
- **Jira**: ``ATT-1290``
- **Test Description**:
  1. *Mathematical Unit Tests (`NormalizedPowerMathTest.kt`)*:
  • Verify warmup window: queue size < 30 returns null.
  • Verify steady power: 30s of 200W -> NP is exactly 200.0W.
  • Verify stochastic power vs arithmetic mean: 30s at 100W followed by 30s at 300W (average 200W) yields NP > 200W.
  • Verify coasting (0W): coasting samples are accepted and properly lower the 30s rolling mean.
  • Verify reset / empty behavior.
  2. *Intensity Factor Calculation Tests (`NormalizedPowerMathTest.kt`)*:
  • Verify valid FTP: NP = 240.0, FTP = 300 -> IF = 0.80.
  • Verify unconfigured FTP: FTP = 0 or null -> IF returns null.
  • Verify zero power: NP = 0 -> IF = 0.0.
  3. *Pause Gating & Engine Tests (`CyclingPowerMetricsDeviceTest.kt`)*:
  • Verify `isRecordingSuspended() == true` freezes queue size and cumulative sum.
  • Verify resume restores calculation without data loss.
  4. *Formatter Tests (`IntensityFactorFormatterTest.kt`)*:
  • Null value -> "--".
  • NaN / Infinite / Negative -> "--".
  • Valid value 0.854 -> "0.85".
  • Valid value 1.028 -> "1.03".
  • Valid value 0.7 -> "0.70".
  5. *Filter Defaults & Activity Types Tests (`SensorFilterDefaultsTest.kt`, `ActivityTypeTest.kt`)*:
  • Verify `NORMALIZED_POWER` and `INTENSITY_FACTOR` default to `FilterType.INSTANTANEOUS`.
  • Verify inclusion in `ActivityType.BIKE_POWER` sensor array.
  6. *9-Language Translation Parity Tests (`TranslationParityTest.kt`)*:
  • Verify string tokens `normalized_power`, `normalized_power_short`, `intensity_factor`, `intensity_factor_short` across all 9 locales.
  7. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with zero regressions.

### TST-TRK-013: **Real-Time Mechanical Work (kJ) and Training Stress Score (TSS) Telemetry Test Suite** [Real-time Mechanical Work (kJ) and Training Stress Score (TSS) verified across 1Hz integration, Coggan TSS formula, pause gating, unconfigured FTP handling, filter defaults, and 9-language translation parity.]
- **Requirement(s)**: `REQ-TRK-021`
- **Component**: **Real-Time Mechanical Work (kJ) and Training Stress Score (TSS) Telemetry Test Suite**
- **Jira**: ``ATT-1291``
- **Test Description**:
  1. *Mechanical Work Integration Tests (`MechanicalWorkAndTssMathTest.kt`)*:
  • Verify 1Hz integration: 10 seconds at 200W -> 2000 Joules = 2.0 kJ.
  • Verify coasting (0W): adds 0 Joules, work does not advance.
  • Verify negative/invalid power: skipped, work does not corrupt.
  • Verify pause gating: suspended recording halts integration.
  • Verify session reset: resets to 0.0 kJ.
  2. *TSS Calculation Tests (`MechanicalWorkAndTssMathTest.kt`)*:
  • Verify warmup window: $t < 30\text{ s}$ returns null.
  • Verify steady 1 hour at FTP: 3600 seconds at 250W with FTP = 250W -> TSS = 100.0.
  • Verify 30 minutes at FTP: 1800 seconds at 250W with FTP = 250W -> TSS = 50.0.
  • Verify unconfigured FTP: returns null.
  • Verify mid-ride FTP reactivity: TSS recalculates immediately on FTP change.
  • Verify long ride IEEE 754 precision: 24 hours at 400W maintains numerical exactness.
  3. *Engine & Sensor Tests (`CyclingPowerMetricsDeviceTest.kt`)*:
  • Verify `WORK_KJ` and `TSS` sensor registration and emission.
  • Verify pause state gating.
  4. *Filter Presets & Activity Types Tests (`SensorFilterDefaultsTest.kt`, `ActivityTypeTest.kt`)*:
  • Verify `WORK_KJ` and `TSS` default to `FilterType.INSTANTANEOUS`.
  • Verify inclusion in `ActivityType.BIKE_POWER`.
  5. *9-Language Translation Parity Tests (`TranslationParityTest.kt`)*:
  • Verify string tokens `work_kj`, `work_kj_short`, `units_work_kj`, `tss`, `tss_short` across all 9 locales.
  6. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with zero regressions.

### TST-TRK-014: **Streamlined Dual-Type (Location and Health) Foreground Service Verification Suite** [health"`.
2. *TrackerService Foreground Service Type Derivation Tests (`TrackerServiceFGSTypeTest.kt`)*:
• Verify that `determineForegroundServiceType()` returns `FOREGROUND_SERVICE_TYPE_LOCATION` when health permission is not granted.
• Verify that `determineForegroundServiceType()` returns `LOCATION]
- **Requirement(s)**: `REQ-TRK-022`
- **Component**: **Streamlined Dual-Type (Location and Health) Foreground Service Verification Suite**
- **Jira**: ``ATT-3567``
- **Test Description**:
  1. *Manifest Permission and Attribute Audit*:
  • Verify that `FOREGROUND_SERVICE_CONNECTED_DEVICE` does not exist in any `<uses-permission>` tag in `AndroidManifest.xml` or merged manifest.
  • Verify that `TrackerService` and `BANALService` specify `android:foregroundServiceType="location

### TST-TRK-015: **Growing 1..29s Warm-Up Window Verification Suite for NP, IF, and TSS** [Growing 1..29s warm-up rolling window for live Normalized Power, Intensity Factor, and TSS and $t=29\to 30$ boundary stability verified across unit tests and full clean-room suite; live telemetry published from second 1 (Rule 40).]
- **Requirement(s)**: `REQ-TRK-023`
- **Component**: **Growing 1..29s Warm-Up Window Verification Suite for NP, IF, and TSS**
- **Jira**: ``ATT-3778``
- **Test Description**:
  1. *Normalized Power Growing Window & Boundary Stability Math Tests (`NormalizedPowerMathTest.kt`)*:
  • Verify `testWarmupWindow_growing1To29Samples_emitsValidNpAndIfFromFirstSample` emits non-null `NP` ($200.0\text{ W}$) and `IF` ($0.80$) on ticks `1..29`.
  • Verify `test_NP_Transition_Boundary_Stability` asserts `abs(NP(30) - NP(29)) < 1e-6` with zero spike or oscillation at the $t = 29 \to t = 30$ boundary.
  • Verify `count4thPower == 18000L` after 18,000 samples (5 hours) since sample 1 is included.
  2. *Mechanical Work & TSS Growing Window Math Tests (`MechanicalWorkAndTssMathTest.kt`)*:
  • Verify `testTss_growingWarmupWindow_emitsValidTssFromFirstSecond` emits non-null `TSS` (`t / 36.0`) during ticks `1..29` at 250W (`FTP = 250`).
  • Verify `testLongPause_windowFlushed_staleSamplesPrevented` flushes pre-pause window samples and updates NP immediately on tick 1 after resume using the growing `1..29s` post-pause window.
  3. *CyclingPowerMetricsDevice Sensor Emission Tests (`CyclingPowerMetricsDeviceTest.kt`)*:
  • Verify `normalizedPowerSensor`, `intensityFactorSensor`, and `tssSensor` emit non-null values from tick 1 during initial warm-up and after pause/resume.
  4. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with zero regressions.

### TST-TRK-016: **MySensorManager Priority Hierarchy, DeviceType Enum Alignment, LINE_DISTANCE_m & Canonical Rank Verification Suite** [`SPEED_AND_LOCATION_GOOGLE_FUSED` enabled in `SPEED_PRIORITY` after `GPS`; `DeviceType` enum order aligned (`GPS < GOOGLE_FUSED < NETWORK`); `LINE_DISTANCE_m` registered in `LOCATION_PRIORITY` with `getBestSensor` selecting `GOOGLE_FUSED`; canonical `getPriorityRank` helper verified across all sensor types; `DevicesDatabaseManager` insertion sequence verified; 100% clean-room test pass rate.]
- **Requirement(s)**: `REQ-TRK-024`
- **Component**: **`MySensorManager` Priority Hierarchy, `DeviceType` Enum Alignment, `LINE_DISTANCE_m` & Canonical Rank Verification Suite**
- **Jira**: ``ATT-3961`, `ATT-4375``
- **Test Description**:
  1. *`SPEED_PRIORITY` Ordering Invariant Test (`MySensorManagerPriorityTest.kt`)*:
  • Verify `MySensorManager.SPEED_PRIORITY` equals `[RUN_SPEED, BIKE_SPEED, BIKE_SPEED_AND_CADENCE, BIKE_POWER, SPEED_AND_LOCATION_GPS, SPEED_AND_LOCATION_GOOGLE_FUSED, SPEED_AND_LOCATION_NETWORK]`, matching the relative location provider ordering of `ALTITUDE_PRIORITY` and `LOCATION_PRIORITY`.
  2. *Google Fused `ProxySensor` Retention & GC String Test (`MySensorManagerPriorityTest.kt`)*:
  • Verify that registering `SPEED_mps`, `PACE_spm`, `DISTANCE_m`, `DISTANCE_m_LAP`, `ALTITUDE`, `LATITUDE`, and `LONGITUDE` from a `DeviceType.SPEED_AND_LOCATION_GOOGLE_FUSED` device retains non-null `ProxySensor` instances in `MySensorManager`, propagates live values (`6.5 m/s`, `2896.48 m`), and sets `'D'` and `'S'` in `SensorType.SENSORS` (`"-DS----AG--"`).
  3. *Priority Upgrade & Fallback Test (`MySensorManagerPriorityTest.kt`)*:
  • Verify that registering and unregistering `SPEED_AND_LOCATION_NETWORK`, `SPEED_AND_LOCATION_GOOGLE_FUSED`, `SPEED_AND_LOCATION_GPS`, and `BIKE_SPEED` dynamically upgrades and falls back `getBestSensor` and `ProxySensor` values according to `BIKE_SPEED > SPEED_AND_LOCATION_GPS > SPEED_AND_LOCATION_GOOGLE_FUSED > SPEED_AND_LOCATION_NETWORK`.
  4. *`DeviceType` Enum Declaration Order Test (`DeviceTypeEnumOrderTest.kt`)*:
  • Verify that in `DeviceType.java`, `SPEED_AND_LOCATION_GPS.ordinal() < SPEED_AND_LOCATION_GOOGLE_FUSED.ordinal()` and `SPEED_AND_LOCATION_GOOGLE_FUSED.ordinal() < SPEED_AND_LOCATION_NETWORK.ordinal()`.
  5. *`LINE_DISTANCE_m` Priority Mapping & `getBestSensor` Fallback Test (`MySensorManagerPriorityTest.kt`)*:
  • Verify `MySensorManager.getPriorityList(SensorType.LINE_DISTANCE_m)` equals `LOCATION_PRIORITY`.
  • Verify that when both `SPEED_AND_LOCATION_GOOGLE_FUSED` and `SPEED_AND_LOCATION_NETWORK` are registered for `LINE_DISTANCE_m`, `getBestSensor(SensorType.LINE_DISTANCE_m)` selects `SPEED_AND_LOCATION_GOOGLE_FUSED`.
  6. *Canonical Priority Rank Helper Test (`MySensorManagerPriorityTest.kt`)*:
  • Verify `MySensorManager.getPriorityRank(sensorType, deviceType)` returns 0 for `SPEED_AND_LOCATION_GPS`, 1 for `SPEED_AND_LOCATION_GOOGLE_FUSED`, and 2 for `SPEED_AND_LOCATION_NETWORK` across all location/altitude sensor types.
  • Verify fallback ranks `1000 + deviceType.ordinal()` for unmapped devices and `Integer.MAX_VALUE` for null `deviceType`.
  7. *`DevicesDatabaseManager.addLocationDevicesToDb` Insertion Sequence Test (`DevicesDatabaseManagerTest.kt`)*:
  • Verify on fresh database creation that `SPEED_AND_LOCATION_GPS` is inserted first (`_id = 1`), `SPEED_AND_LOCATION_GOOGLE_FUSED` second (`_id = 2`), and `SPEED_AND_LOCATION_NETWORK` third (`_id = 3`).
  8. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with zero regressions.

### TST-TRK-017: **Deterministic AdbGpsInjector Mock Provider Lifecycle (PLAYING Registration & Immediate PAUSED/STOPPED/EOF Removal) Verification Suite** [Deterministic `AdbGpsInjector` mock provider lifecycle verified across startup cleanup, lazy `PLAYING` registration, and immediate removal on `PAUSED`, `STOPPED`, `kill_gps`, and `EOF` in `tools/test_replay_workout.py`.]
- **Requirement(s)**: `REQ-TRK-025`
- **Component**: **Deterministic `AdbGpsInjector` Mock Provider Lifecycle (`PLAYING` Registration & Immediate `PAUSED`/`STOPPED`/`EOF` Removal) Verification Suite**
- **Jira**: ``ATT-3961`, `ATT-3971``
- **Test Description**:
  1. *Startup & `--cleanup` Provider Removal Unit Tests (`TestAdbGpsInjector` in `tools/test_replay_workout.py`)*:
  • Verify `AdbGpsInjector.setup()` removes any lingering `gps`, `network`, and `fused` test providers (`remove-test-provider`) without calling `add-test-provider` while `STOPPED` (`providers_registered == False`).
  2. *Lazy Registration on `PLAYING` & Immediate Removal on `PAUSED` / `STOPPED` / `EOF` Unit Tests (`TestAdbGpsInjector` & `TestReplayEngine` in `tools/test_replay_workout.py`)*:
  • Verify `enable_providers()` / `inject_location()` registers `add-test-provider` lazily when entering `PLAYING` (`providers_registered == True`).
  • Verify `release_providers()` immediately executes `remove-test-provider` for `gps`, `network`, and `fused` and sets `providers_registered = False` when playback transitions to `PAUSED`, `STOPPED`, `kill_gps(True)`, or reaches workout completion (`EOF`).
  3. *Clean-Room Regression Execution*:
  • Execute `python3 -m unittest tools/test_replay_workout.py` and `./gradlew testDebugUnitTest` asserting 100% pass rate.

### TST-TRK-018: **Resumed Workout Sample Table Resolution, Asynchronous BANAL Binding Barrier & Sample Write Error Surfacing Verification Suite** [Resumed workout sample table name synchronization (`applyResumedState`), asynchronous `mBanalConnection.onServiceConnected()` ordering, `"null"` table name rejection, and `SQLiteException` surfacing verified in `TrackerServiceResumeTest` and full clean-room regression suite.]
- **Requirement(s)**: `REQ-TRK-026`
- **Component**: **Resumed Workout Sample Table Resolution, Asynchronous BANAL Binding Barrier & Sample Write Error Surfacing Verification Suite**
- **Jira**: ``ATT-3980``
- **Test Description**:
  1. *`applyResumedState` Table Name & Barrier Synchronization Test (`TrackerServiceResumeTest.kt`)*:
  • Verify `applyResumedState(state)` updates `mBaseFileName`, recomputes `mSamplesTableName = "\"<baseFileName>\""`, and completes `mTableInitializationFuture`.
  2. *Asynchronous Deferred `RESUME_SERVICE_RECREATION` & `RESUME_BY_USER` End-to-End Tests (`TrackerServiceResumeTest.kt`)*:
  • Simulate `onStartCommand()` with `mBanalService == null` followed by `mBanalConnection.onServiceConnected()`; verify `mSamplesTableName` is never `"\"null\""`, `mTableInitializationFuture` remains incomplete until `applyResumedState()` runs, `recreateValuesWhenResuming()` executes before `createNewLap()` and `onSearchingFinished()`, and subsequent `sampleAndWriteToDb()` ticks insert rows into the resumed workout's SQLite sample table.
  3. *Invalid Table Name Guard & SQLite Error Surfacing Tests (`TrackerServiceResumeTest.kt`)*:
  • Verify `WorkoutSamplesDatabaseManager.getTableName()` throws `IllegalArgumentException` for `null`, empty/blank, or `"null"` input.
  • Verify `TrackerService.handleMissingColumns()` rethrows non-column `SQLiteException` errors instead of silently swallowing them.
  4. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with zero regressions.

### TST-TRK-019: **Fault-Tolerant 1 Hz Sampling Loop, Clock Timer Exception Guard & Volatile mBanalService Snapshot Concurrency Verification Suite** [Fault-tolerant `try / catch (Throwable)` guards in `TrackerService.tracker.run()` and `ClockDevice.ClockTimeTask.run()` and `volatile` local-snapshot concurrency safety for `mBanalService` verified in `TrackerServiceResilienceTest`, `ClockDeviceResilienceTest`, and full 3,116-test clean-room regression suite.]
- **Requirement(s)**: `REQ-TRK-027`
- **Component**: **Fault-Tolerant 1 Hz Sampling Loop, Clock Timer Exception Guard & Volatile `mBanalService` Snapshot Concurrency Verification Suite**
- **Jira**: ``ATT-3981``
- **Test Description**:
  1. *1 Hz Sampling Loop Exception Survival Test (`TrackerServiceResilienceTest.kt`)*:
  • Verify that when tick 1 of `TrackerService.tracker` throws an unchecked exception (`RuntimeException`), the exception is caught and logged via `Log.e`, `getSamplingErrorCount()` increments to `1`, and subsequent ticks (`tick 2`, `tick 3`) continue executing on `ScheduledExecutorService.scheduleAtFixedRate`, advancing `timeTotal_s` and updating the notification.
  2. *Concurrent `mBanalService` Disconnection Local Snapshot & Volatile Visibility Test (`TrackerServiceResilienceTest.kt`)*:
  • Verify `mBanalService` field is declared `volatile` via reflection.
  • Simulate `mBanalConnection.onServiceDisconnected()` setting `mBanalService = null` concurrently during an in-flight tick of `tracker.run()` / `sampleAndWriteToDb()`; verify the in-flight tick completes cleanly using its local `banalService` snapshot without throwing `NullPointerException` (`samplingErrorCount == 0`).
  3. *1 Hz Clock Timer (`ClockTimeTask`) Exception Survival Test (`ClockDeviceResilienceTest.kt`)*:
  • Verify that when tick 1 of `ClockDevice.ClockTimeTask` throws an unchecked exception (`RuntimeException` during broadcast or sensor listener), the exception is caught and logged via `Log.e`, `getClockTimerErrorCount()` increments to `1`, the `java.util.Timer` thread remains alive, and subsequent ticks continue advancing `TIME_TOTAL`, `TIME_ACTIVE`, and `TIME_LAP` sensors.
  4. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with zero regressions.

### TST-TRK-020: **ProxySensor Timestamp Delegation, End-to-End MySensorManager + AutoPauseManager Integration, Manual Resume Sync & ToneGenerator Lifecycle Verification Suite** [`ProxySensor` timestamp and `SensorData` delegation (`getDeviceName() == null`), end-to-end `MySensorManager` + `AutoPauseManager` integration across sub-threshold auto-pause, registered-source standstill, hysteresis auto-resume, manual pause lock precedence, manual resume state sync, and `ToneGenerator` reuse/release verified in `AutoPauseManagerTest` and full clean-room regression suite.]
- **Requirement(s)**: `REQ-TRK-028`, `REQ-TRK-018`
- **Component**: **`ProxySensor` Timestamp Delegation, End-to-End `MySensorManager` + `AutoPauseManager` Integration, Manual Resume Sync & `ToneGenerator` Lifecycle Verification Suite**
- **Jira**: ``ATT-3984``
- **Test Description**:
  1. *`ProxySensor` Timestamp, Value, DeviceName & Null-Source Delegation Test (`TST-TRK-020_1`, `AutoPauseManagerTest.kt`)*:
  • Verify `MySensor.newValue(v)` updates `getLastUpdateTimestamp() > 0L` and `ProxySensor.getLastUpdateTimestamp()` / `ProxySensor.getSensorData().getTimestamp()` return the exact timestamp of `mSourceSensor`.
  • Verify `ProxySensor.getSensorData().getDeviceName()` returns `null` even when `mSourceSensor` belongs to a named `MyDevice`, and verify `ProxySensor` with `mSourceSensor == null` returns `null` value and `0L` timestamp without `NullPointerException`.
  2. *Criterion 1 End-to-End Integration: Sub-Threshold Speed via `MySensorManager` & `ProxySensor` Triggers `AUTO_PAUSED` & Halts `TIME_ACTIVE` (`TST-TRK-020_2`, `AutoPauseManagerTest.kt`)*:
  • Register a speed sensor on a test `MyDevice` in `MySensorManager`, feed moving speed (`5.0 m/s`), then feed sub-threshold speed (`0.5 m/s` / `0.0 m/s`), read `getBestSensorData(SensorType.SPEED_mps)` through `ProxySensor`, pass into `AutoPauseManager.evaluateTick`, and verify transition `TRACKING -> PAUSE_DEBOUNCE_PENDING -> AUTO_PAUSED`, `TrainingApplication.isAutoPaused() == true`, and `TrainingApplication.isRecordingSuspended() == true`.
  3. *Registered Speed Source Standstill with Stale/Null Packet Triggers `AUTO_PAUSED` (`TST-TRK-020_3`, `AutoPauseManagerTest.kt`)*:
  • Verify that when `hasSpeedSource == true` and packet age exceeds `5000 ms` (or `currentSpeedMps == null` from `ThresholdSensor` after 8 zeros), `AutoPauseManager` treats effective speed as `0.0 m/s`, increments debounce, and transitions to `AUTO_PAUSED` rather than `SENSOR_LOST`.
  • Verify that while in `AUTO_PAUSED`, a stale speed packet does NOT trigger false auto-resume until a fresh packet arrives.
  4. *Criterion 2 End-to-End Integration: Speed Recovery Above Hysteresis via `ProxySensor` Triggers Auto-Resume (`TST-TRK-020_4`, `AutoPauseManagerTest.kt`)*:
  • From `AUTO_PAUSED`, feed speed inside hysteresis deadband (`3.2 km/h`) through `ProxySensor` and verify session remains `AUTO_PAUSED`; then feed fresh speed at/above `resumeThresholdMps` (`3.6 km/h` = `1.0 m/s`) through `ProxySensor` and verify transition `AUTO_PAUSED -> TRACKING` and `TrainingApplication.isAutoPaused() == false`.
  5. *Criterion 3: Manual Pause Precedence & Manual Resume Synchronization from `AUTO_PAUSED` (`TST-TRK-020_5`, `AutoPauseManagerTest.kt`)*:
  • Verify that when `isManualPauseLocked == true`, speed changes through `ProxySensor` never trigger auto-pause or auto-resume.
  • Verify that when a session in `AUTO_PAUSED` is manually resumed (`onManualResume()` / `TrainingApplication.resumeFromPaused()`), `AutoPauseManager.currentState` resets to `TRACKING` and subsequent stationary ticks cleanly re-trigger `AUTO_PAUSED`.
  6. *`DefaultAutoPauseAudioNotifier` `ToneGenerator` Reuse & `release()` Lifecycle Test (`TST-TRK-020_6`, `AutoPauseManagerTest.kt`)*:
  • Inject a mock `ToneGenerator` provider into `DefaultAutoPauseAudioNotifier`, trigger multiple pause and resume tones, verify the provider is invoked only once (reusing the same `ToneGenerator`), and verify `autoPauseManager.release()` calls `ToneGenerator.release()`.

### TST-TRK-021: **Safe SQLite Column Identifier Escaping for Custom Device Names (' and ") & Rate-Limited Sample Insert Failure Telemetry Verification Suite** [Safe SQLite column identifier escaping (`"..."` with `""` escaping and outer-delimiter unquoting) for device names containing apostrophes and double quotes, plus rate-limited sample insert failure telemetry, verified in `TrackerServiceDeviceNameQuotingTest` and full clean-room regression suite.]
- **Requirement(s)**: `REQ-TRK-029`
- **Component**: **Safe SQLite Column Identifier Escaping for Custom Device Names (`'` and `"`) & Rate-Limited Sample Insert Failure Telemetry Verification Suite**
- **Jira**: ``ATT-3985``
- **Test Description**:
  1. *SQLite Column Identifier Quoting & Unquoting Round-Trip Unit Test (`TST-TRK-021.1`, `TrackerServiceDeviceNameQuotingTest.kt`)*:
  • Verify `TrackerService.quoteSqlColumnIdentifier` and `TrackerService.unquoteSqlColumnIdentifier` round-trip standard device names (`HR (Polar H10)`), apostrophes (`HR (Rainer's HRM)`), double quotes (`POWER (Pro "2" Power)`), combined quotes (`CADENCE (Rainer's "Pro" Sensor)`), legacy single-quoted identifiers (`'HR (Polar H10)'`), and bare sensor columns (`HR`, `SPEED_mps_gps`) without stripping internal apostrophes or quotes.
  2. *End-to-End 1 Hz Sample Recording & Dynamic `ALTER TABLE` with Apostrophes and Double Quotes in Device Names (`TST-TRK-021.2`, `TrackerServiceDeviceNameQuotingTest.kt`)*:
  • Against a real Robolectric SQLite `WorkoutSamplesDatabaseManager` table, record multiple 1 Hz ticks via `sampleAndWriteToDb()` with sensors named `"Rainer's HRM"`, `"Pro \"2\" Power"`, and `"Rainer's \"Pro\" Cadence"`, and add a mid-workout sensor `"Stages's \"Gen 3\" L/R"` on tick 3.
  • Verify zero rows are dropped (`rowCount == 3`, `sampleInsertFailureCount == 0`), `cursor.getColumnNames()` contains exact `<SensorType> (<DeviceName>)` columns, all sensor values are persisted and readable, and existing apostrophe/quote columns are not re-added when a new sensor joins mid-workout.
  3. *Rate-Limited Sample Insert Failure Counter & Logging Test (`TST-TRK-021.3`, `TrackerServiceDeviceNameQuotingTest.kt`)*:
  • Simulate 65 consecutive unrecoverable SQLite sample insert failures and verify `getSampleInsertFailureCount()` increments to `65` while `Log.e` is rate-limited to ticks `1`, `2`, `3`, and `60` (`4` log emissions total).
  4. *Clean-Room Full Suite Regression Execution (`TST-TRK-021.4`)*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with zero regressions.

### TST-TRK-022: **Fault-Isolated Workout Finalization, Dedicated FINISHED=1 Commit, Defensive AccumulatedSensorTypeSet Copy & Zero-Duration Average Speed Verification Suite** [Dedicated `FINISHED = 1` commit transaction, defensive `MySensorManager.getAccumulatedSensorTypeSet()` copy, finite `0.0` average speed guard at `0 s` active time, and fault-isolated map/stream persistence in `finalizeLiveSession()` verified in `TrackerServiceFinalizationTest` and full clean-room regression suite.]
- **Requirement(s)**: `REQ-TRK-030`
- **Component**: **Fault-Isolated Workout Finalization, Dedicated `FINISHED=1` Commit, Defensive `AccumulatedSensorTypeSet` Copy & Zero-Duration Average Speed Verification Suite**
- **Jira**: ``ATT-3986``
- **Test Description**:
  1. *Zero-Duration (`0 s` Active Time) Stop & Finite Average Speed Guard Test (`TST-TRK-022.1`, `TrackerServiceFinalizationTest.kt`)*:
  • Configure `TrackerService` with `mTimeActive_s = 0` (and `mDistanceTotal_m = 0.0` / `25.0`), invoke `averageSpeedCalculateable()`, `getAverageSpeed()`, and `endWorkout()`, and verify `averageSpeedCalculateable() == false`, `getAverageSpeed() == 0.0` (finite, non-`NaN`), `WorkoutSummaries.FINISHED = 1` is persisted, and zero `NaN` or `Infinity` values are written to `WorkoutSummaries`, `WorkoutExtrema`, or `EquipmentAndSportTypeDiscoveryManager`.
  2. *Fault-Isolated `endWorkout()` & `finalizeLiveSession()` Persistence Test (`TST-TRK-022.2`, `TrackerServiceFinalizationTest.kt`)*:
  • Simulate exceptions thrown in `saveAccumulatedSensorTypes()`, `getSportTypeId()`, `guessCommuteAndTrainer()`, `EquipmentAndSportTypeDiscoveryManager`, and `WorkoutClusterEngine`, as well as `mBanalService == null` during `endWorkout()`; verify `WorkoutSummaries.FINISHED = 1` is committed in its own dedicated transaction and the simplified map polyline and elevation/distance streams are still persisted via `updateMapAndStreams`, `setMapPolyline`, and `setElevationStreams`.
  3. *`MySensorManager.getAccumulatedSensorTypeSet()` Defensive Copy & Concurrent Registration Test (`TST-TRK-022.3`, `TrackerServiceFinalizationTest.kt`)*:
  • Verify mutating the `Set` returned by `MySensorManager.getAccumulatedSensorTypeSet()` does not alter internal state, and concurrent `registerSensor()` calls while iterating over `getAccumulatedSensorTypeSet()` complete with zero `ConcurrentModificationException`s.
  4. *Clean-Room Full Suite Regression Execution (`TST-TRK-022.5`)*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with zero regressions.

### TST-TRK-023: **LiveWorkoutSession Stream & Extrema Rehydration on Workout Resume, Sample Count Persistence & finalizeLiveSession() Continuity Verification Suite** [`LiveWorkoutSession` instantiation and rehydration of delta-encoded 20s streams and per-sensor `MIN`/`MAX`/`AVG` (`COUNT` column in `TABLE_EXTREMA_VALUES` with `TIME_ACTIVE_s` fallback) across `RESUME_BY_USER` and `RESUME_SERVICE_RECREATION` verified in `LiveWorkoutSessionTest`, `TrackerServiceResumeTest`, and full clean-room regression suite.]
- **Requirement(s)**: `REQ-TRK-031`
- **Component**: **`LiveWorkoutSession` Stream & Extrema Rehydration on Workout Resume, Sample Count Persistence & `finalizeLiveSession()` Continuity Verification Suite**
- **Jira**: ``ATT-3987``
- **Test Description**:
  1. *Stream Delta-Encoding & Finalization Continuity Unit Test (`TST-TRK-023.1`, `LiveWorkoutSessionTest.kt`)*:
  • Record pre-resume 20s stream points (`t = 20s`, `t = 40s`), decode the encoded `MAP_POLYLINE`, `ALTITUDE_STREAM`, and `DISTANCE_STREAM`, instantiate a resumed `LiveWorkoutSession`, invoke `rehydrateStreams(...)`, and record a post-resume 20s point (`t = 60s`).
  • Verify that concatenating the pre-resume encoded strings with the post-resume `StreamIncrement` decodes to the exact 3-point coordinate, altitude, and distance sequences without any delta jump, and that `getRawLatLngs()`, `getSampledLatLngs()`, `getSampledAltitudes()`, and `getSampledDistances()` contain all 3 points.
  2. *Sensor `RunningStats` Rehydration with Explicit `COUNT` & `TIME_ACTIVE_s` Fallback Test (`TST-TRK-023.2`, `LiveWorkoutSessionTest.kt`)*:
  • Rehydrate `LiveWorkoutSession` with `rehydrateSensorStats(SensorType.HR, min=110.0, max=180.0, avg=150.0, sampleCount=100)`, feed an interior sample (`160.0`), a new minimum (`105.0`), and a new maximum (`185.0`).
  • Verify that on `160.0`, `minChanged == false` and `maxChanged == false`, `min == 110.0`, `max == 180.0`, `count == 101`, and `average == (150.0 * 100 + 160.0) / 101.0`; on `105.0`, `minChanged == true`; on `185.0`, `maxChanged == true`.
  3. *`TrackerService` Resume Rehydration & `finalizeLiveSession()` Integration Test (`TST-TRK-023.3`, `TrackerServiceResumeTest.kt`)*:
  • Configure `WorkoutSummariesDatabaseManager` with pre-resume `EncodedStreams` and `ExtremaRecord`s (including explicit `count` for a mid-workout sensor, `null` count fallback to `mTimeActive_s` for a legacy sensor, and `ExtremaType.START` coordinates), invoke `recreateValuesWhenResuming()`, `sampleAndWriteToDb()`, and `endWorkout()`.
  • Verify `mLiveSession` is non-null and rehydrated, post-resume `sampleAndWriteToDb()` persists `AVG` with incremented `count` without overwriting `ExtremaType.START`, and `endWorkout()` executes `finalizeLiveSession()` to persist the combined polyline and streams via `updateMapAndStreams`.
  4. *DB Version 25 Migration & Clean-Room Full Suite Regression Execution (`TST-TRK-023.4`)*:
  • Verify `WorkoutSummariesDbHelper.DB_VERSION == 25` (`>= 24`) and execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with zero regressions.

### TST-TRK-024: **Thread-Safe Sensor, Device, Filter, Service & Live Session Concurrency Verification Suite** [Thread-safe collections, `volatile` cross-thread fields, atomic `putIfAbsent` device registration, non-null `shutDown()` teardown, lock-free `MySensor` / `MySensorManager` listener dispatch, null-safe `BANALService` accessors, and synchronized `LiveWorkoutSession` verified in `SensorAndDeviceConcurrencyStressTest` and full clean-room regression suite.]
- **Requirement(s)**: `REQ-TRK-032`
- **Component**: **Thread-Safe Sensor, Device, Filter, Service & Live Session Concurrency Verification Suite**
- **Jira**: ``ATT-3988``
- **Test Description**:
  1. *`DeviceManager` Concurrent Device Creation, Defensive Copy & Post-Shutdown Safety Test (`TST-TRK-024.1`, `SensorAndDeviceConcurrencyStressTest.kt`)*:
  • Verify via reflection that `mMyRemoteDevices` and `mMyRemoteDeviceTries` are `ConcurrentHashMap`, `mSearchQueue` is `ConcurrentLinkedQueue`, `mNewlyFoundDevices` is `CopyOnWriteArrayList`, and `cMyRemoteDeviceCurrentlySearchingFor`, `mAntAsyncSearchEngine`, `mBTSearchForNewDevicesEngine` are `volatile`.
  • Verify `getIdsOfNewlyFoundDevices()` returns a defensive copy whose mutation does not affect `mNewlyFoundDevices`.
  • Run multi-threaded stress workers concurrently registering devices via `putIfAbsent`, mutating `mNewlyFoundDevices`, and iterating `getActiveRemoteDevices()`, `getIdsOfActiveDevices()`, `getRemoteDeviceData()`, and `getIdsOfNewlyFoundDevices()`.
  • Verify `shutDown()` clears collections without setting `mMyRemoteDevices` or `mSearchQueue` to `null` and post-shutdown queries do not throw `NullPointerException`.
  2. *`MySensor`, `ProxySensor`, `MyAccumulatorSensor` & Lock-Free `MySensorManager` Stress Test (`TST-TRK-024.2`, `SensorAndDeviceConcurrencyStressTest.kt`)*:
  • Verify `MySensor.mSensorListeners` is `CopyOnWriteArrayList` and `mValue`, `mActivated`, `mLastUpdateTimestamp` are `volatile`.
  • Run multi-threaded stress workers adding/removing `SensorListener`s, calling `MySensor.newValue()`, swapping `ProxySensor.setSourceSensor()`, and calling `MyAccumulatorSensor.increment()` / `accumulate()` / `reset()`.
  • Verify `!Thread.holdsLock(mySensorManager)` inside `SensorListener.newValue()` during `MySensorManager.registerSensor()` and `unregisterSensor()`, and verify concurrent cross-thread `MySensorManager` queries inside the listener complete without deadlock.
  3. *`FilterManager`, `BANALService`, `TrackerService` & `LiveWorkoutSession` Concurrency Test (`TST-TRK-024.3`, `SensorAndDeviceConcurrencyStressTest.kt`)*:
  • Verify `volatile` modifiers on `MyBTLEDevice`, `MyANTDevice`, `MyRemoteDevice`, `MyDevice`, `BANALService`, and `TrackerService` shared fields, and `ConcurrentHashMap` backing in `FilterManager` and `MySensorManager.mInitialValues`.
  • Verify `BANALService` static accessors return safe non-throwing defaults when `cDeviceManager`, `cSensorManager`, and `cFilterManager` are `null`.
  • Stress-test `LiveWorkoutSession` across concurrent threads executing `addSample()`, `recordStreamPoint()`, `addRawLatLng()`, `applyAltitudeCorrection()`, `rehydrateStreams()`, `rehydrateSensorStats()`, and collection getters (`getSampledLatLngs()`, `getSampledAltitudes()`, `getSampledDistances()`, `getRawLatLngs()`, `getSensorStats()`).
  4. *Clean-Room Full Suite Regression Execution (`TST-TRK-024.4`)*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with zero regressions.

### TST-TRK-025: **Non-Blocking TrackerService.onDestroy() Finalization on mDbExecutor, Deterministic mScheduler Shutdown & Async Init Failure Recovery Verification Suite** [Deterministic `mScheduler.shutdownNow()`, non-blocking `onDestroy()` FIFO finalization on `mDbExecutor`, and `TrainingApplication` tracking mode recovery on async DB init abort verified in `TrackerServiceFinalizationTest`, `TrackerServiceResilienceTest`, `TrackerServiceAsyncInitTest`, and full clean-room regression suite.]
- **Requirement(s)**: `REQ-TRK-033`, `REQ-TRK-015`, `REQ-TRK-030`
- **Component**: **Non-Blocking `TrackerService.onDestroy()` Finalization on `mDbExecutor`, Deterministic `mScheduler` Shutdown & Async Init Failure Recovery Verification Suite**
- **Jira**: ``ATT-4006``
- **Test Description**:
  1. *`mScheduler` Shutdown & Non-Blocking `onDestroy()` FIFO Finalization Unit Test (`TST-TRK-025.1`, `TrackerServiceFinalizationTest.kt`)*:
  • Instantiate `TrackerService` with a real single-thread `mDbExecutor`, submit a simulated slow sample-write task (`Thread.sleep(300)` appending `"sample-write-done"` to an event log), and call `service.onDestroy()` on the test thread.
  • Verify `service.onDestroy()` returns in `< 100 ms` without blocking on the `300 ms` DB task.
  • Verify `service.isSchedulerShutdownForTesting() == true` and `service.isDbExecutorShutdownForTesting() == true` immediately after `onDestroy()`.
  • Await `service.getFinalizationFuture().get(2, TimeUnit.SECONDS)` and verify `endWorkout()` executed on `mDbExecutor` strictly after `"sample-write-done"`, `WorkoutSummaries.FINISHED = 1` was persisted, and `mBanalService == null` after completion.
  2. *Interrupted Tracking (`mTrackingInterrupted == true`) Skips `endWorkout()` While Completing Teardown Test (`TST-TRK-025.2`, `TrackerServiceFinalizationTest.kt`, `TrackerServiceResilienceTest.kt`)*:
  • Set `mTrackingInterrupted = true`, call `service.onDestroy()`, await `service.getFinalizationFuture().get(2, TimeUnit.SECONDS)`, and verify `endWorkout()` is NOT invoked, `isSchedulerShutdownForTesting() == true`, `isDbExecutorShutdownForTesting() == true`, and `mBanalService == null`.
  3. *Async `START_NORMAL` Initialization Failure Resets `TrainingApplication` Tracking Mode & Broadcasts `TRACKING_ABORTED_INTENT` Test (`TST-TRK-025.3`, `TrackerServiceAsyncInitTest.kt`)*:
  • Set `TrainingApplication.startTracking()` (`getTrackingMode() == TrackingMode.TRACKING`), configure `WorkoutSummariesDatabaseManager.createNewWorkout(...)` to throw `RuntimeException("DB Failure")`, and invoke `service.onStartCommand(startIntent, 0, 1)`.
  • Verify `mTableInitializationFuture.isCompletedExceptionally() == true`, `service.mTrackingInterrupted == true`, `TrainingApplication.getTrackingMode() == TrackingMode.READY`, `TrainingApplication.isManualPauseLocked() == false`, `TrainingApplication.isAutoPaused() == false`, and `TrackerService.TRACKING_ABORTED_INTENT` was broadcast.
  4. *Clean-Room Full Suite Regression Execution (`TST-TRK-025.4`)*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with zero regressions.

### TST-SEN-020: **Sensor Onboarding, Empty-State Action & Discovery Auto-Pairing Verification Suite** [Start screen "Pair Sensor" onboarding button, actionable empty state in My Sensors with timeout guard, default discovery auto-pairing in DeviceManager with instant unpair safety, active workout auto-pair immunity, database insertion failure resilience, manual override conflict resolution, 9-language localization parity, and absence of '+' prefix in action strings across all 9 locales verified in DevicesDatabaseManagerTest, ControlTrackingScreenTest, DeviceListScreenTest, DeviceManagerDiscoveryTest, SensorOnboardingLocalizationTest, and clean-room full suite.]
- **Requirement(s)**: `REQ-SEN-020`
- **Component**: **Sensor Onboarding, Empty-State Action & Discovery Auto-Pairing Verification Suite**
- **Jira**: ``ATT-4051`, `ATT-4288``
- **Test Description**:
  1. *Database Discovery Auto-Pairing Flag Test (`TST-SEN-020.1`, `DevicesDatabaseManagerTest.kt`)*:
  • Verify `insertNewBluetoothDeviceIntoDB(..., paired = true)` stores `PAIRED = 1` in the database.
  • Verify `insertNewAntDeviceIntoDB(..., paired = true, ...)` stores `PAIRED = 1` in the database.
  2. *DeviceManager Synchronized Discovery and Notification Test (`TST-SEN-020.2`, `DeviceManagerDiscoveryTest.kt`)*:
  • Verify `mBTSearchInterface.onNewDeviceFound` inserts with `paired = true`, calls `pairingChanged(deviceId, true)` strictly after database write confirmation, and sends new device broadcast.
  • Verify `mANTAsyncSearchEngineInterface.onNewDeviceFound` inserts with `paired = true`, calls `pairingChanged(deviceId, true)`, and sends new device broadcast.
  3. *ControlTrackingScreen Onboarding Button Visibility & Navigation Test (`TST-SEN-020.3`, `ControlTrackingScreenTest.kt`)*:
  • When `devices.isEmpty()`, `showResearchButton == false` (`hasPairedRemoteDevices == false`), and `searchingFor == null`, verify the "Pair Sensor" button is rendered and invoking click dispatches `onPairSensor`.
  • When `devices.isNotEmpty()` or `showResearchButton == true`, verify the "Pair Sensor" button is absent.
  4. *DeviceListScreen Empty-State Search Button & Scan Timeout Guard Test (`TST-SEN-020.4`, `DeviceListScreenTest.kt`)*:
  • When `devices.isEmpty()`, `!isSearching`, and `filterCriteria.isEmpty`, verify empty-state hint and "Search for Sensors" button are rendered and clicking dispatches `onScanForSensors`.
  • When `isSearching == true`, verify search action button is hidden.
  • When scan stops without finding devices, verify `isSearching` transitions to `false` and empty-state action button is restored.
  5. *9-Language Localization Parity Test (`TST-SEN-020.5`, `SensorOnboardingLocalizationTest.kt`)*:
  • Verify `onboarding_pair_sensor_action`, `devices_empty_state_search_hint`, and `devices_action_search_sensors` exist and are non-blank across EN, DE, ES, FR, IT, JA, NL, PL, PT.
  6. *Clean-Room Full Suite Regression Execution (`TST-SEN-020.6`)*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with zero regressions.
  7. *Immediate Unpair Reversal Safety Test (`TST-SEN-020.7`, `DeviceListViewModelTest.kt`)*:
  • Verify toggling pair switch on an auto-paired device dispatches `onPairedChanged(deviceId, false)`, setting `PAIRED = 0` in the database.
  8. *Navigation Disposal Scan Reset Test (`TST-SEN-020.8`, `DevicesTabbedViewModelTest.kt`)*:
  • Verify navigation disposal during an active scan invokes `stopSearching()` and resets `isSearching` to `false` without ghost actions.
  9. *Active Workout Auto-Pairing Immunity Test (`TST-SEN-020.9`, `DeviceManagerDiscoveryTest.kt`)*:
  • Verify that when `TrainingApplication.getTrackingMode() == TrackingMode.TRACKING`, discovery inserts the sensor into the database with `paired = true`, but suppresses immediate `pairingChanged(deviceId, true)` to protect active workout recording from telemetry disruption.
  10.• *Database Insertion Failure Resilience Test (`TST-SEN-020.10`, `DeviceManagerDiscoveryTest.kt`)*:
  • Verify that if `insertNewBluetoothDeviceIntoDB` or `insertNewAntDeviceIntoDB` returns `deviceId <= 0` (simulating database failure or constraint error), the device is NOT added to `mNewlyFoundDevices`, `pairingChanged` is NOT called, and no broadcast is sent, preventing phantom/ghost devices.
  11. *Manual Unpair vs. Auto-Pairing Conflict Resolution Test (`TST-SEN-020.11`, `DeviceManagerDiscoveryTest.kt`)*:
  • Verify that if a user manually unpairs an auto-paired device during workout tracking, `paired = 0` is persisted in the database; when tracking ends and `startSearchForPairedDevices()` runs, the explicitly unpaired sensor is NOT queried or re-queued for connection, ensuring manual override takes strict precedence.
  12. *Action Strings Absence of Plus Prefix Across All 9 Locales (`TST-SEN-020.12`, `SensorOnboardingLocalizationTest.kt`)*:
  • In `SensorOnboardingLocalizationTest.kt`, parse `onboarding_pair_sensor_action` and `devices_action_search_sensors` across all 9 locales (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`).
  • Assert that none of the values begin with `'+'`, enforcing Rule 49 and Design Guidelines Section 5.5 to eliminate duplicate `+ +` action iconography.

### TST-TRK-026: **Foreground-Protected Asynchronous Workout Finalization and Stop Lifecycle Isolation Verification Suite** [Foreground priority retention during endWorkout(), BANALService connection preservation until finalization completes, ACTION_STOP idempotency, TrainingApplication dispatch, and consolidated async init abort verified in TrackerServiceFinalizationTest, TrackerServiceAsyncInitTest, and full clean-room suite.]
- **Requirement(s)**: `REQ-TRK-034`, `REQ-TRK-015`, `REQ-TRK-030`, `REQ-TRK-033`
- **Component**: **Foreground-Protected Asynchronous Workout Finalization and Stop Lifecycle Isolation Verification Suite**
- **Jira**: ``ATT-4098``
- **Test Description**:
  1. *Foreground Priority Retention During `endWorkout()` Test (`TST-TRK-026.1`, `TrackerServiceFinalizationTest.kt`)*:
  • Send `ACTION_STOP` intent to `TrackerService.onStartCommand()`.
  • Verify `stopForeground()` and `stopSelf()` are NOT called while `endWorkout()` is executing on `mDbExecutor`.
  • Verify `performStopForeground()` and `performStopSelf()` are called on the main thread after `finalizationFuture` completes.
  2. *`BANALService` Bound Throughout `endWorkout()` Test (`TST-TRK-026.2`, `TrackerServiceFinalizationTest.kt`)*:
  • Verify `mBanalService` remains non-null and `unbindService(mBanalConnection)` is deferred until after `endWorkout()` returns.
  3. *Duplicate `ACTION_STOP` Idempotency Test (`TST-TRK-026.3`, `TrackerServiceFinalizationTest.kt`)*:
  • Deliver duplicate `ACTION_STOP` while stopping is in progress; verify second call returns `START_NOT_STICKY` immediately without submitting duplicate tasks.
  4. *`TrainingApplication.stopTracking()` `ACTION_STOP` Dispatch Test (`TST-TRK-026.4`, `TrackerServiceFinalizationTest.kt`)*:
  • When `TrackerService.isRunning() == true`, verify `stopTracking()` dispatches `ACTION_STOP` to `startService()`.
  5. *Consolidated Async Init Abort Test (`TST-TRK-026.5`, `TrackerServiceAsyncInitTest.kt`)*:
  • Verify async DB init failure triggers `handleTrackingAborted()` on main thread without duplicate calls.
  6. *Clean-Room Full Suite Regression Execution (`TST-TRK-026.6`)*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with zero regressions.

### TST-TRK-027: **Deterministic BANAL State Snapshotting and Bound Lifecycle Preservation Verification Suite** [Main-thread snapshot capture, in-flight tick suppression, final lap and sensor preservation when BANAL destroyed, and onDestroy deferred unbinding verified in TrackerServiceFinalizationTest and clean-room full suite.]
- **Requirement(s)**: `REQ-TRK-035`, `REQ-TRK-034`
- **Component**: **Deterministic BANAL State Snapshotting and Bound Lifecycle Preservation Verification Suite**
- **Jira**: ``ATT-4099``
- **Test Description**:
  1. *Main-Thread `BanalFinalizationSnapshot` Capture Test (`TST-TRK-027.1`, `TrackerServiceFinalizationTest.kt`)*:
  • Verify `BanalFinalizationSnapshot.capture(mBanalService)` captures `lapNr`, `lapTime_s`, `lapDistance_m`, `activeRemoteDeviceIds`, `accumulatedSensorTypes`, `accumulatedGCDataString`, `bSportType`, and `sportTypeId` accurately on the main thread.
  • Verify null `mBanalService` provides safe fallback defaults.
  2. *In-Flight 1 Hz Tick Suppression When Stopping Test (`TST-TRK-027.2`, `TrackerServiceFinalizationTest.kt`)*:
  • Set `mIsStopping = true`; invoke `tracker.run()` and `sampleAndWriteToDb()`.
  • Verify execution immediately returns without evaluating auto-pause, updating notifications, or submitting sample writes to `mDbExecutor`.
  3. *Final Lap & Sensor Retention When BANAL Unbound Prior to `endWorkout()` Test (`TST-TRK-027.3`, `TrackerServiceFinalizationTest.kt`)*:
  • Capture snapshot, set `mBanalService = null` (simulating premature service destruction), and invoke `endWorkout(snapshot)`.
  • Verify final lap with non-zero duration/distance is persisted in `LapsTable`, accumulated sensor types are stored in `WorkoutSummariesDatabaseManager`, and GC string and sport type are written to summary record.
  4. *`onDestroy()` Deferred Service Unbinding Test (`TST-TRK-027.4`, `TrackerServiceFinalizationTest.kt`)*:
  • Invoke `onDestroy()` when `mIsStopping == false`.
  • Verify `unbindService(mBanalConnection)` is NOT called while `endWorkout()` is executing on `mDbExecutor`, and is only dispatched to the main thread after `finalizationTask` completes.
  5. *Clean-Room Full Suite Regression Execution (`TST-TRK-027.5`)*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with zero regressions.

### TST-TRK-028: **Lock-Free ProxySensor Listener Dispatch & Deadlock Prevention Concurrency Suite** [Concurrency stress test verifying absence of A/B deadlocks between MySensorManager and ProxySensor across 8 threads, lock-free callback assertions, and source sensor initial value propagation verified in SensorAndDeviceConcurrencyStressTest and clean-room full suite.]
- **Requirement(s)**: `REQ-SEN-021`, `REQ-TRK-032`
- **Component**: **Lock-Free ProxySensor Listener Dispatch & Deadlock Prevention Concurrency Suite**
- **Jira**: ``ATT-4096``
- **Test Description**:
  1. *Concurrent ProxySensor Listener Registration & MySensorManager Mutex Deadlock Elimination Test (`TST-TRK-028.1`, `SensorAndDeviceConcurrencyStressTest.kt`)*:
  • Spawn 8 concurrent worker threads: 4 threads registering/unregistering sensors on `MySensorManager` and 4 threads continuously adding/removing `SensorListener`s on `ProxySensor`.
  • The `SensorListener.newValue(value)` callback executes re-entrant queries into `MySensorManager` (`getSensors()`, `getAccumulatedSensorTypeSet()`, `getAllSensors()`, `registerSensor()`).
  • Verify all threads complete $\ge 1,000$ operations in $< 5$ seconds with zero monitor deadlocks, thread starvations, or timeouts.
  2. *Lock-Free Listener Callback Assertion Test (`TST-TRK-028.2`, `SensorAndDeviceConcurrencyStressTest.kt`)*:
  • Attach test listener to `ProxySensor`.
  • Trigger `ProxySensor.addSensorListener()`, `ProxySensor.setSourceSensor()`, and `MySensorManager.registerSensor()` / `unregisterSensor()`.
  • Inside `listener.newValue()`, assert `!Thread.holdsLock(proxySensor)` and `!Thread.holdsLock(sensorManager)`.
  3. *Source Sensor Switch & Initial Value Propagation Test (`TST-TRK-028.3`, `SensorAndDeviceConcurrencyStressTest.kt`)*:
  • Register listener on `ProxySensor` bound to `sourceSensorA`.
  • Change source to `sourceSensorB` having a different value.
  • Verify listener immediately receives the initial value from `sourceSensorB` and subsequent values emitted by `sourceSensorB`.
  4. *Clean-Room Full Suite Regression Execution (`TST-TRK-028.4`)*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with zero regressions.

### TST-TRK-029: **In-Memory LiveWorkoutSession Reuse on User Resume and Safe Publication Verification Suite** [In-memory LiveWorkoutSession reuse on RESUME_BY_USER, 1 Hz rawLatLngs preservation, fresh instance construction and publication safety after rehydration verified in TrackerServiceResumeTest and clean-room full suite.]
- **Requirement(s)**: `REQ-TRK-036`, `REQ-TRK-031`
- **Component**: **In-Memory `LiveWorkoutSession` Reuse on User Resume and Safe Publication Verification Suite**
- **Jira**: ``ATT-4095``
- **Test Description**:
  1. *In-Memory Session Reuse & 1 Hz `rawLatLngs` Preservation Test (`TST-TRK-029.1`, `TrackerServiceResumeTest.kt`)*:
  • Initialize `TrackerService` with an active `LiveWorkoutSession` for `workoutId = 100` containing pre-pause 1 Hz `rawLatLngs` points (e.g. 5 raw points).
  • Invoke `applyResumedState()` with `StartMode.RESUME_BY_USER` (simulating user pause and resume while service stays in memory).
  • Verify `mLiveSession` retains the exact same instance (`assertSame`).
  • Verify all 5 original 1 Hz trackpoints in `mLiveSession.getRawLatLngs()` remain intact.
  • Verify database `getEncodedStreams` is NOT queried and `mTableInitializationFuture` completes.
  2. *Process Recreation Fresh Instance Construction & DB Rehydration Test (`TST-TRK-029.2`, `TrackerServiceResumeTest.kt`)*:
  • Initialize `TrackerService` with `mLiveSession = null` (simulating process recreation / `RESUME_SERVICE_RECREATION`).
  • Configure `mockDbManager.getEncodedStreams(workoutId)` with persisted streams.
  • Invoke `applyResumedState()`.
  • Verify a new `LiveWorkoutSession` instance is created and rehydrated with streams from the database.
  • Verify `mTableInitializationFuture` completes and `notifyTrackingStarted(workoutId)` is called.
  3. *Publication Safety & Atomic Assignment on `mDbExecutor` Test (`TST-TRK-029.3`, `TrackerServiceResumeTest.kt`)*:
  • Initialize `TrackerService` with `mLiveSession = null`.
  • Capture `rehydrateTask` submitted to `mDbExecutor`.
  • Verify that before `rehydrateTask` executes, `mLiveSession` remains `null` (or un-rehydrated session is never prematurely published).
  • Verify that while rehydration runs, `isRehydrating()` evaluates to `true`.
  • Verify that once `rehydrateTask` completes, `mLiveSession` is published and populated, and `isRehydrating()` resets to `false`.
  4. *Clean-Room Full Suite Regression Execution (`TST-TRK-029.4`)*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with zero regressions.

### TST-CON-012: **Resilient Bluetooth LE & ANT+ Reconnection, Immutable Telemetry Dispatch, and Queue Integrity Verification Suite** [BLE notification payload byte cloning guarantees immutability under high-frequency telemetry; single deterministic search timeout and exponential backoff eliminate GATT 133/257 disconnect storms; DeviceManager search queue guards against alien device preemption; ANT+ channel release prevents DEAD lock and DEVICE_ALREADY_IN_USE errors.]
- **Requirement(s)**: `REQ-CON-020`
- **Component**: **Resilient Bluetooth LE & ANT+ Reconnection, Immutable Telemetry Dispatch, and Queue Integrity Verification Suite**
- **Jira**: ``ATT-4007``
- **Test Description**:
  1. *Immutable BLE Notification Payload Dispatch Unit Test (`TST-CON-012.1`, `BLEPayloadDispatchTest.kt`)*:
  • Invoke API 33 `onCharacteristicChanged(gatt, ch, value)` with packet 1 (`[0x01, 0x50]`).
  • Mutate the underlying byte array immediately to packet 2 (`[0x02, 0x60]`).
  • Drain the main-thread Looper and verify the receiver observes the exact original cloned byte payload (`[0x01, 0x50]`) without in-place mutation.
  2. *Deterministic Search Timeout Lifecycle & Reconnect Backoff Unit Test (`TST-CON-012.2`, `BLEReconnectRobustnessTest.kt`)*:
  • Verify `startSearching()` cancels any prior pending timeout.
  • Verify successful connection discovery (`notifyStopSearching(true)`) cancels `mSearchTimeoutRunnable`.
  • Simulate unexpected disconnect (`STATE_DISCONNECTED`) and verify exponential backoff progression (1s -> 2s -> 4s -> 8s -> 16s).
  • Verify invoking `shutDown()` sets `mIsShutDown`, removes all handler callbacks, and ignores subsequent disconnect callbacks.
  3. *Search Queue Device Integrity on Alien Events Unit Test (`TST-CON-012.3`, `DeviceManagerSearchQueueTest.kt`)*:
  • Configure `DeviceManager` actively searching for Device A (`id=101`) with Device B (`id=102`) in `mSearchQueue`.
  • Send `SEARCHING_STOPPED_FOR_ONE_INTENT` with alien `deviceID = 999`.
  • Verify the event is discarded: `cMyRemoteDeviceCurrentlySearchingFor` remains Device A and Device B is NOT popped from `mSearchQueue`.
  4. *ANT+ Handle Hygiene & DEAD State Teardown Unit Test (`TST-CON-012.4`, `ANTDeviceLifecycleTest.kt`)*:
  • Verify `startSearching()` calls `releaseAccess()` on any existing `pccReleaseHandle` prior to requesting new access.
  • Trigger `onDeviceStateChangeHandler(DeviceState.DEAD)` and verify `releaseAccess()` is invoked, `pccReleaseHandle` is nulled, and `notifyStopSearching(false)` is dispatched.
  5. *Clean-Room Full Suite Regression Execution (`TST-CON-012.5`)*:
  • Execute `./gradlew testDebugUnitTest` verifying 100% pass rate with zero regressions across all unit and Robolectric tests.

### TST-TRK-030: **AutoPauseManager In-Motion Dropout vs. Standstill Silence Verification Suite** [In-motion signal loss transitions to SENSOR_LOST, standstill silence debounces to AUTO_PAUSED, dynamic unpairing resets lastValidSpeedMps, 2-tick recovery hysteresis, and decoupled manual resume verified.]
- **Requirement(s)**: `REQ-TRK-037`
- **Component**: **AutoPauseManager In-Motion Dropout vs. Standstill Silence Verification Suite**
- **Jira**: ``ATT-4094``
- **Test Description**:
  1. *In-Motion Signal Loss Dropout Test (`TST-TRK-030.1`, `AutoPauseManagerTest.kt`)*:
  • Set valid speed reading > resume threshold (e.g. 8.0 m/s with resume threshold 1.0 m/s).
  • Advance time past stale packet timeout (> 5000 ms).
  • Verify state transitions to `SENSOR_LOST`, audit event is `"SENSOR_LOST_IN_MOTION_DROPOUT"`, `debounceCounter == 0`, and state does NOT transition to `AUTO_PAUSED`.
  2. *Standstill Silence Debounce Test (`TST-TRK-030.2`, `AutoPauseManagerTest.kt`)*:
  • Set valid speed reading $\le$ resume threshold (e.g. 0.4 m/s).
  • Advance time past stale timeout.
  • Verify effective speed is evaluated as 0.0 m/s and state debounces to `AUTO_PAUSED`.
  3. *Dynamic Unpairing Reset Test (`TST-TRK-030.3`, `AutoPauseManagerTest.kt`)*:
  • Evaluate tick with `hasSpeedSource = false`.
  • Verify state transitions to `SENSOR_LOST` and `lastValidSpeedMps` is explicitly reset to `null`.
  4. *Sensor Recovery Hysteresis Test (`TST-TRK-030.4`, `AutoPauseManagerTest.kt`)*:
  • From `SENSOR_LOST`, provide 1st valid reading -> remains `SENSOR_LOST`.
  • Provide 2nd valid reading -> cleanly transitions to `TRACKING` with `"SENSOR_RECOVERED"`.
  5. *Decoupled Manual Resume Lock Guard Test (`TST-TRK-030.5`, `AutoPauseManagerTest.kt`)*:
  • From `AUTO_PAUSED`, call `onManualResume(manualPauseLocked = true)` -> remains `AUTO_PAUSED`.
  • Call `onManualResume(manualPauseLocked = false)` -> transitions to `TRACKING`.
  6. *Clean-Room Regression Suite Execution (`TST-TRK-030.6`)*:
  • Run `./gradlew testDebugUnitTest` verifying 100% pass rate.

### TST-TRK-031: **CyclingPowerMetricsDevice Constructor Seeding, Sensor-Presence Gating (Rule 52), Stationary/Paused W/kg Alignment, and Workout Summary Presentation Defense Verification Suite** [Sensor-presence gating in CyclingPowerMetricsDevice constructor and pause (Rule 52), Rule 51 constructor seeding parity with power sensor, stationary/paused W/kg alignment, transient dropout resilience, WorkoutDataMapper power gating and DTO symmetry (Rule 20), WorkoutDetails presentation gating, < 500 lines modularity invariant, 9-language localization audit, and full clean-room pass specified.]
- **Requirement(s)**: `REQ-TRK-038`, `REQ-UI-106`
- **Component**: **CyclingPowerMetricsDevice Constructor Seeding, Sensor-Presence Gating (Rule 52), Stationary/Paused W/kg Alignment, and Workout Summary Presentation Defense Verification Suite**
- **Jira**: ``ATT-4368``
- **Test Description**:
  1. *Sensor-Presence Gating on Constructor & Suspended State Test (`TST-TRK-031.1`, `CyclingPowerMetricsDeviceTest.kt`)*:
  • Mock `sensorManager.getSensor(SensorType.POWER)` returning `null` (Rule 52).
  • Instantiate `CyclingPowerMetricsDevice` with CP = 250W and W' = 20.0 kJ.
  • Assert `wPrimeBalSensor.sensorData.value == null` immediately upon instantiation.
  • Invoke `calculateMetrics()` with `isSuspended = true`: assert `wPrimeBalSensor.sensorData.value == null` (preventing phantom SQLite extrema).
  2. *Constructor Baseline W' Capacity Seeding with Power Sensor Present Test (`TST-TRK-031.2`, `CyclingPowerMetricsDeviceTest.kt`)*:
  • Mock `sensorManager.getSensor(SensorType.POWER)` returning active power sensor.
  • Instantiate `CyclingPowerMetricsDevice` with CP = 280W (and with FTP fallback 250W) and W' = 20.0 kJ.
  • Assert `wPrimeBalSensor.sensorData.value == 20.0` (Double, delta 0.001) immediately upon instantiation without invoking `calculateMetrics()` or `resetSession()` (Rule 51).
  • Instantiate with W' = 0.0f: assert `wPrimeBalSensor.sensorData.value == null` (Double, delta 0.001).
  3. *Stationary and Paused W/kg Telemetry Emission Test (`TST-TRK-031.3`, `CyclingPowerMetricsDeviceTest.kt`)*:
  • Configure `isSuspended = true` and `athleteWeightKg = 75.0f` and power sensor present.
  • Set `power = 0.0W`: invoke `calculateMetrics()`, assert `powerToWeightSensor.sensorData.value == 0.0` (not starved / not null), preventing `TimedMovingAverageFilter` from expiring to null.
  • Set `power = 150.0W`: invoke `calculateMetrics()`, assert `powerToWeightSensor.sensorData.value == 2.0`.
  • Assert `normalizedPowerSensor`, `workKjSensor`, and `tssSensor` do NOT accumulate or increment.
  • Assert `wPrimeBalSensor` continues exponential reconstitution at 0.0W.
  4. *Transient Power Dropout Resilience Test (`TST-TRK-031.4`, `CyclingPowerMetricsDeviceTest.kt`)*:
  • Set up active tracking with CP = 250W, W' = 20.0 kJ, power = 300W on tick 1.
  • On tick 2, simulate 1-second transient BLE/RF packet dropout (`power = null`).
  • Invoke `calculateMetrics()`: assert `wPrimeBalSensor.sensorData.value` retains last valid balance (NOT wiped out to `null`).
  • Assert `normalizedPowerSensor`, `intensityFactorSensor`, `tssSensor`, and `workKjSensor` retain current session values.
  • Assert mechanical work does not accumulate during the missing sample tick.
  5. *Unconfigured Athlete Weight Fallback Test (`TST-TRK-031.5`, `CyclingPowerMetricsDeviceTest.kt`)*:
  • Configure athlete weight = 0.0f (unconfigured).
  • Invoke `calculateMetrics()` in active and suspended states: assert `powerToWeightSensor.sensorData.value == null`.
  6. *WorkoutDataMapper Power Gating & Historical Legacy Defense Symmetry Test (`TST-TRK-031.6`, `WorkoutDataMappingSymmetryTest.kt`)*:
  • Provide workout cursor and snapshot containing legacy SQLite extrema `rawMinWPrimeBal = 20.0 kJ` but `avgPower = null` and `maxPower = null`.
  • Assert both `fromCursor` and `fromSnapshot` map `minWPrimeBalKj == null`, `minWPrimeBalPct == null`, and `isEmptyTankWarning == false` (Rule 20 & Rule 52).
  • Provide workout with `avgPower = 200.0 W` and `rawMinWPrimeBal = 15.0 kJ`: assert both map `minWPrimeBalKj == 15.0 kJ` and `minWPrimeBalPct == 75.0%`.
  7. *WorkoutDetails Presentation Gating & Modularity Contract Test (`TST-TRK-031.7`, `WorkoutDetailsLayoutTest.kt`)*:
  • Build `WorkoutDetailsData` with `minWPrimeBalKj = null` (non-power workout): assert `AnaerobicBatteryRow` is not added to visible section list.
  • Verify `WorkoutDetails.kt` line count is strictly `< 500` lines.
  8. *9-Language Localization & Specifier Audit (`TST-TRK-031.8`, `TranslationParityTest.kt`)*:
  • Audit telemetry string tokens `power_to_weight`, `power_to_weight_short`, `units_power_to_weight`, `w_prime_battery_pct`, `w_prime_warning_low_battery`, `workout_w_prime_min_kj_label`, `workout_w_prime_min_pct_label` across EN, DE, ES, FR, IT, JA, NL, PL, PT.
  • Confirm zero new string resources required and 100% localization parity.
  9. *Clean-Room Regression Suite Execution (`TST-TRK-031.9`)*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% pass rate with zero regressions across the full test suite.

### TST-FIL-014: **Backend Filter Key Normalization, Runtime 'Best Sensor' Field Registration & Post-Trim Rolling Filter NaN Guard Verification Suite** [Runtime sensor field creation and updating with "Best Sensor" normalizes deviceName and deviceId to null, resolving proxy filters immediately; TimedMovingAverageFilter returns null instead of Double.NaN when window samples expire; specific device selection is preserved; 9-language localization parity confirmed; clean-room test suite passes with zero regressions.]
- **Requirement(s)**: `REQ-FIL-014`, `REQ-FIL-011`, `REQ-UI-167`
- **Component**: **Backend Filter Key Normalization, Runtime 'Best Sensor' Field Registration & Post-Trim Rolling Filter NaN Guard Verification Suite**
- **Jira**: ``ATT-4282``
- **Test Description**:
  1. *Runtime 'Best Sensor' Field Creation Normalization Test (`TST-FIL-014.1`, `EditSensorFieldViewModelTest.kt`)*:
  • Initialize `EditSensorFieldViewModel` in new-field mode (`sensorFieldId = -1L`).
  • Set `selectedDeviceId = -1L` (Best Sensor) and `selectedDeviceName = "Bester Sensor"`.
  • Call `viewModel.saveChanges()`.
  • Verify that `banalServiceRepo.createFilter(capture(filterSlot))` was invoked with `filterSlot.captured.deviceName == null` (Rule 50).
  • Verify that `trackingViewsRepo.insertSensorFieldConfig(...)` was invoked with `newSourceDeviceId == null` and `newSourceDeviceName == null`.
  2. *Runtime 'Best Sensor' Field Update Normalization Test (`TST-FIL-014.2`, `EditSensorFieldViewModelTest.kt`)*:
  • Initialize `EditSensorFieldViewModel` in edit-field mode (`sensorFieldId = 42L`).
  • Set `selectedDeviceId = -1L` and call `viewModel.saveChanges()`.
  • Verify that `banalServiceRepo.createFilter(...)` received `deviceName == null`.
  • Verify that `trackingViewsRepo.updateSensorFieldConfig(...)` was invoked with `newSourceDeviceId == null` and `newSourceDeviceName == null`.
  3. *Specific Paired Device Selection Preservation Test (`TST-FIL-014.3`, `EditSensorFieldViewModelTest.kt`)*:
  • Initialize `EditSensorFieldViewModel` with `selectedDeviceId = 123L` and `selectedDeviceName = "Wahoo KICKR"`.
  • Call `viewModel.saveChanges()`.
  • Verify that `banalServiceRepo.createFilter(...)` was invoked with `deviceName == "Wahoo KICKR"`.
  • Verify that `trackingViewsRepo.insertSensorFieldConfig(...)` was invoked with `newSourceDeviceId == 123L` and `newSourceDeviceName == "Wahoo KICKR"`.
  4. *TimedMovingAverageFilter Post-Trim Expired Samples Empty Guard Test (`TST-FIL-014.4`, `FilterNullSafetyTest.kt`)*:
  • Instantiate `TimedMovingAverageFilter("power_filter", SensorType.POWER, 3)` with 3-second window.
  • Add sample `filter.newValue(250)`.
  • Advance or wait past window threshold (>3000ms, or simulate timestamp older than `System.currentTimeMillis() - 3000`).
  • Call `filter.filteredValue` (or `getFilteredValue()`).
  • Assert `filter.filteredValue == null` and assert `filter.filteredValue != Double.NaN`.
  5. *TimedMovingAverageFilter Fresh Window Moving Average Accuracy Test (`TST-FIL-014.5`, `FilterNullSafetyTest.kt`)*:
  • Instantiate `TimedMovingAverageFilter("power_filter", SensorType.POWER, 10)` with 10-second window.
  • Add samples `filter.newValue(100)` and `filter.newValue(200)`.
  • Assert `filter.filteredValue == 150.0` (Double, delta 0.01).
  6. *9-Language Localization & String Specifier Audit (`TST-FIL-014.6`, `TranslationParityTest.kt`)*:
  • Verify that zero new string resources are required across all 9 canonical app locales (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`).
  • Verify that `R.string.bestSensor` remains 100% defined and uncorrupted across all 9 locales.
  7. *Clean-Room Regression Suite Execution (`TST-FIL-014.7`)*:
  • Execute `./gradlew testDebugUnitTest` verifying 100% pass rate with zero regressions across the full test suite.

### TST-CON-021: **BLE Sensor Search Android 8–11 Compatibility and Defensive Exception Handling Verification Suite** [BLE sensor search Android 8–11 compatibility (BLUETOOTH_ADMIN declaration with maxSdkVersion=30, pre-API 31 permission checking), defensive startScan and stopScan SecurityException/IllegalStateException recovery, 9-language localization audit, and full clean-room pass specified.]
- **Requirement(s)**: `REQ-CON-021`, `REQ-CON-019`, `REQ-UI-106`
- **Component**: **BLE Sensor Search Android 8–11 Compatibility and Defensive Exception Handling Verification Suite**
- **Jira**: ``ATT-3997``
- **Test Description**:
  1. *Pre-API 31 Missing BLUETOOTH_ADMIN Abort Test (`TST-CON-021.1`, `BTSearchForNewDevicesEngineTest.kt`)*:
  • Configure environment for API 30 (`Build.VERSION.SDK_INT = 30`).
  • Mock `ContextCompat.checkSelfPermission` to return `PERMISSION_DENIED` for `BLUETOOTH_ADMIN` and `PERMISSION_GRANTED` for `ACCESS_FINE_LOCATION`.
  • Invoke `engine.startAsyncSearch()`.
  • Assert `engine.isScanning() == false` and verify `mockScanner.startScan(...)` was called 0 times.
  2. *Pre-API 31 Missing ACCESS_FINE_LOCATION Abort Test (`TST-CON-021.2`, `BTSearchForNewDevicesEngineTest.kt`)*:
  • Configure environment for API 30 (`Build.VERSION.SDK_INT = 30`).
  • Mock `ContextCompat.checkSelfPermission` to return `PERMISSION_GRANTED` for `BLUETOOTH_ADMIN` and `PERMISSION_DENIED` for `ACCESS_FINE_LOCATION`.
  • Invoke `engine.startAsyncSearch()`.
  • Assert `engine.isScanning() == false` and verify `mockScanner.startScan(...)` was called 0 times.
  3. *StartScan SecurityException Graceful Recovery Test (`TST-CON-021.3`, `BTSearchForNewDevicesEngineTest.kt`)*:
  • Mock permissions granted for scanning.
  • Mock `mockScanner.startScan(...)` throwing `SecurityException("Need BLUETOOTH_ADMIN permission")`.
  • Invoke `engine.startAsyncSearch()`.
  • Assert method returns cleanly without unhandled exception.
  • Assert `engine.isScanning() == false`, proving state lockup is prevented and subsequent searches are unblocked.
  4. *StartScan IllegalStateException Graceful Recovery Test (`TST-CON-021.4`, `BTSearchForNewDevicesEngineTest.kt`)*:
  • Mock permissions granted for scanning.
  • Mock `mockScanner.startScan(...)` throwing `IllegalStateException("BT adapter is not turned ON")`.
  • Invoke `engine.startAsyncSearch()`.
  • Assert method returns cleanly without unhandled exception.
  • Assert `engine.isScanning() == false`.
  5. *StopScan SecurityException Graceful Recovery Test (`TST-CON-021.5`, `BTSearchForNewDevicesEngineTest.kt`)*:
  • Set `engine` into active scanning state (`scanning = true`).
  • Mock `mockScanner.stopScan(...)` throwing `SecurityException("Permission revoked")`.
  • Invoke `engine.stopAsyncSearch()`.
  • Assert method returns cleanly without unhandled exception.
  • Assert `engine.isScanning() == false` and tracking state is cleanly reset.
  6. *StopScan IllegalStateException Graceful Recovery Test (`TST-CON-021.6`, `BTSearchForNewDevicesEngineTest.kt`)*:
  • Set `engine` into active scanning state (`scanning = true`).
  • Mock `mockScanner.stopScan(...)` throwing `IllegalStateException("BT adapter off")`.
  • Invoke `engine.stopAsyncSearch()`.
  • Assert method returns cleanly without unhandled exception.
  • Assert `engine.isScanning() == false` and tracking state is cleanly reset.
  7. *Manifest Permission Declaration & MaxSdkVersion Audit (`TST-CON-021.7`, `AndroidManifestTest.kt`)*:
  • Inspect `AndroidManifest.xml`.
  • Assert `<uses-permission android:name="android.permission.BLUETOOTH_ADMIN" android:maxSdkVersion="30" />` is declared and active (not commented out).
  8. *9-Language Localization & Specifier Audit (`TST-CON-021.8`, `TranslationParityTest.kt`)*:
  • Verify that zero new user-facing string resources are required for this platform permission and backend exception handling fix.
  • Assert 100% translation parity across all 9 canonical app locales: `values` (en), `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`.
  9. *Clean-Room Regression Suite Execution (`TST-CON-021.9`)*:
  • Execute `./gradlew testDebugUnitTest` verifying 100% pass rate with zero regressions across the full test suite.

### TST-CON-022: **Automated BLE/ANT+ Pedal Power Balance Reference Normalization & Obsolete UI Checkbox Deprecation Verification Suite** [Automated BLE and ANT+ pedal power balance reference normalization verified; Right-pedal telemetry contract (REQ-UI-377.1) guaranteed; obsolete UI checkboxes deprecated; 9-locale parity verified; zero database migrations; 100% clean-room test pass rate.]
- **Requirement(s)**: `REQ-CON-022`, `REQ-UI-377`
- **Component**: **Automated BLE/ANT+ Pedal Power Balance Reference Normalization & Obsolete UI Checkbox Deprecation Verification Suite**
- **Jira**: ``ATT-4554``
- **Test Description**:
  1. *BLE Pedal Power Balance Reference Flag Normalization Unit Tests (`TST-CON-022.1`, `BTLEBikePowerDeviceTest.kt` / `BTLEMeasurementParsingTest.kt`)*:
  • Verify that when `0x2A63` packet has Bit 0 = 1 (Balance Present) and Bit 1 = 0 (Right reference) with raw balance 104 ($52.0\%$), `mPowerBalanceSensor.newValue(52.0)` is emitted.
  • Verify that when `0x2A63` packet has Bit 0 = 1 and Bit 1 = 1 (Left reference) with raw balance 96 ($48.0\%$ Left), the driver inverts the value and emits $52.0\%$ Right (`100.0 - 48.0`).
  • Verify boundary values (0% Left -> 100% Right, 100% Left -> 0% Right).
  2. *ANT+ Pedal Power Balance Indicator Normalization Unit Tests (`TST-CON-022.2`, `ANTBikePowerDeviceTest.kt`)*:
  • Verify that when `rightPedalIndicator == true` and `pedalPowerPercentage == 54`, `mPowerBalanceSensor.newValue(54)` is emitted.
  • Verify that when `rightPedalIndicator == false` (Left reference) and `pedalPowerPercentage == 46`, the driver inverts the value and emits $54$ Right (`100 - 46`).
  • Verify that `pedalPowerPercentage < 0` publishes `null`.
  3. *EditDevice UI & ViewModel Deprecation Contract Tests (`TST-CON-022.3`, `EditDeviceViewModelTest.kt`)*:
  • Verify `EditDeviceDialog` renders `PowerMeterSection` as read-only features without correction checkboxes.
  • Verify `EditDeviceViewModel`, `DeviceData`, `DeviceDataRepository`, and `BikePowerSensorsHelper` operate cleanly without `doublePowerBalanceValues` and `invertPowerBalanceValues`.
  4. *9-Language Localization Parity & Pruning Audit (`TST-CON-022.4`, `TranslationParityTest.kt`)*:
  • Verify that `bike_power__pedal_power_balance_correction`, `bike_power__double_power_balance_values`, and `bike_power__invert_power_balance_values` are completely pruned across all 9 application locales (`values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`) with 100% parity.
  5. *Clean-Room Full Suite Regression Execution (`TST-CON-022.5`)*:
  • Execute `./gradlew testDebugUnitTest` verifying 100% test pass rate across the full test suite.

### TST-TRK-039: **Tracking Subsystem Robustness, Resource Lifecycle & Rate-Limited Error Telemetry Hygiene Verification Suite** [Zero-duration lap speed guard, empty sport list guard, notification resurrection prevention, rate-limited error logging, AltitudeFromPressureDevice shutDown cleanup, BatterySaverController scope cancellation, and full clean-room pass specified.]
- **Requirement(s)**: `REQ-TRK-039`
- **Component**: **Tracking Subsystem Robustness, Resource Lifecycle & Rate-Limited Error Telemetry Hygiene Verification Suite**
- **Jira**: ``ATT-4032``
- **Test Description**:
  1. *Lap Speed Zero-Duration & Sport List Fallback Test (`TST-TRK-039.1`, `TrackingRobustnessTest.kt`)*:
  • Verify `BANALService.newLap` returns `0.0` lap speed when `lapTime_s <= 0` without `NaN` or `Infinity`.
  • Verify `BANALService.getSportTypeId` returns fallback sport ID when list is empty without `IndexOutOfBoundsException`.
  2. *Notification Stop Guard Test (`TST-TRK-039.2`, `TrainingApplicationTrackingTest.kt`)*:
  • Verify `updateTimeAndDistanceToNotification` suppresses notification updates when tracking mode is not active/paused, preventing notification resurrection.
  3. *Rate-Limited Error Logging & Clock Monotonicity Test (`TST-TRK-039.3`, `ClockDeviceResilienceTest.kt`)*:
  • Verify `ClockTimeTask` and `TrackerService` rate-limit error logs to burst threshold then periodic logging on persistent exceptions.
  • Verify clock time ticks anchor to monotonic elapsed time.
  4. *Lifecycle Cleanup & Resource Teardown Test (`TST-TRK-039.4`, `AltitudeFromPressureDeviceTest.kt`, `BatterySaverControllerTest.kt`, `DeviceManagerTeardownTest.kt`)*:
  • Verify `AltitudeFromPressureDevice.shutDown()` does not invoke `registerReceiver`.
  • Verify `BatterySaverController.release()` cancels its coroutine scope and clamps brightness safely.
  • Verify `DeviceManager.createPairedRemoteDevices` safely closes cursors in try-with-resources.
  5. *Clean-Room Full Suite Regression Execution (`TST-TRK-039.5`)*:
  • Execute `./gradlew testDebugUnitTest` and `TranslationParityTest` asserting 100% pass rate.

### TST-TRK-040: **Landscape Cockpit Dual-Pane Layout, Widescreen Sensor Grid & Sub-Pane Overlay Docking Verification Suite** [Verified]
- **Requirement(s)**: `REQ-TRK-040`
- **Component**: `SensorGridLandscapeLayoutTest.kt`, `SensorGridScreen.kt`, `SensorFieldState.kt`, `TrackingTabsScreen.kt`
- **Jira**: `ATT-52`
- **Test Description**:
  1. *Dual-Pane Split Layout Composition Verification (`TST-TRK-040.1`)*:
  • Compose UI test verifying that when `orientation == Configuration.ORIENTATION_LANDSCAPE` and `showMap == true`, `SensorGridScreen` renders a horizontal `Row` containing a 50% left telemetry column and a 50% right map column (`TrackingMapContainer`).
  2. *Widescreen Sensor Grid Verification (`TST-TRK-040.2`)*:
  • Compose UI test verifying that when `orientation == Configuration.ORIENTATION_LANDSCAPE` and `showMap == false`, `SensorGridScreen` allocates the full screen width to sensor cells and renders scalable numerals via `SensorFieldView`.
  3. *Right-Pane Bottom Sheet Docking Invariant (`TST-TRK-040.3`)*:
  • Compose UI test verifying that when `LiveSegmentSheet` or `LiveClimbSheet` is displayed in landscape mode, the bottom sheet container is anchored exclusively within the right pane, and left sensor telemetry fields remain 100% visible and unmasked.
  4. *Display Setting Force Portrait Enforcement (`TST-TRK-040.4`)*:
  • Unit test verifying that when `forcePortrait` is enabled in `SettingsDataStore` / `TrainingApplication`, screen orientation requests lock to portrait and ignore configuration rotation.
  5. *Configuration Mode Gesture & Action Preservation (`TST-TRK-040.5`, Rule 34)*:
  • Gesture contract test verifying that in `ScreenMode.CONFIGURATION`, sensor field editing (`onEditField`), deletion (`onDeleteField`), add row/col, and pick-and-place reordering callbacks remain fully dispatched and interactive in landscape mode.
  6. *Targeted & Full Regression Suite Execution (`TST-TRK-040.6`)*:
  • Execute `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.tracking.*"` and full clean-room suite verifying 100% pass rate.
