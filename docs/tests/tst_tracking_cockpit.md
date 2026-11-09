# Living Test Specification: Tracking & Live Sensor Cockpit

**Domain**: `tracking_cockpit`  
**Prefixes**: `CON`, `FIL`, `FUSION`, `SEN`, `TRK`, `WEA`  
**Scope**: Live sensor telemetry, Bluetooth/ANT+ connectivity, filtering, GPS/tracking lifecycle.  
**Total Tests**: 53

---

### TST-FIL-002: WorkoutSamplesDb [Verified]
- **Requirement(s)**: `REQ-DAT-003`
- **Component**: `WorkoutSamplesDb`
- **Test Description**: Verify that `calcExtremaValue` returns `null` (not 0.0) when querying a sensor that has only NULL values or does not exist in the table.

### TST-FIL-001: **Null-Safe Filter Verification** [Verified]
- **Requirement(s)**: `REQ-FIL-011`
- **Component**: **Null-Safe Filter Verification**
- **Jira**: ``ATT-275``
- **Test Description**: 1. Execute `AverageFilterTest` (or similar unit test). 2. Pass `null` as a new value to the filter. 3. Call `getFilteredValue`.

### TST-FIL-003: **Atomic Altitude Correction** [Verified]
- **Requirement(s)**: `REQ-FIL-004`
- **Component**: **Atomic Altitude Correction**
- **Jira**: ``ATT-425``
- **Test Description**: 1. Start a workout with barometric altitude at 100m. 2. Record data for 1 minute. 3. Trigger a correction to 200m (e.g. by nearing a known location). 4. Stop workout and inspect summary.

### TST-CON-002: **Barometric Cold-Start Baseline Protection Audit** [Verified]
- **Requirement(s)**: `REQ-CON-011`
- **Component**: **Barometric Cold-Start Baseline Protection Audit**
- **Jira**: ``ATT-508``
- **Test Description**:
  1. Verify `AltitudeFromPressureDevice` does not publish uncalibrated standard atmosphere altitude (using 1013.25 hPa assumption) to session listeners or running statistics before baseline calibration.
  2. Simulate cold start with sea level pressure = 1020.8 hPa (raw altitude = -63.0 m); verify that -63 m is suppressed from session extrema.
  3. Execute unit tests in `AltitudeFromPressureDeviceTest.kt`.

### TST-TRK-002: **Workout Lap Lifecycle, Pause Neutrality & Zero-Duration Phantom Prevention** [Verified]
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

### TST-TRK-003: **Lieblingsorte Start & Destination Workout Auto-Naming Unit & Localization Tests** [Verified]
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

### TST-TRK-004: **Distance-Enriched Auto-Generated Workout Names for Start-Location Sessions Verification** [Verified]
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

### TST-TRK-005: **GPS Speed Inactivity Watchdog, Progressive Decay & Recovery Verification** [Verified]
- **Requirement(s)**: `REQ-TRK-013`
- **Component**: **GPS Speed Inactivity Watchdog, Progressive Decay & Recovery Verification**
- **Jira**: ``ATT-2616``, ``ATT-4802``
- **Test Description**:
  1. *Continuous Fix Speed Stability Unit Test (`SpeedAndLocationDeviceDecayTest.kt`)*:
  • Initialize `SpeedAndLocationDevice` and emit continuous valid location fixes at 1.0-second intervals.
  • Verify active speed (`mSpeed`) and `mSpeedSensor` remain stable and watchdog does not trigger decay.
  2. *Inactivity Timeout & Progressive Decay Test (`SpeedAndLocationDeviceDecayTest.kt`)*:
  • Emit a location fix with speed 10.0 m/s.
  • Advance virtual time by 10.0 seconds (timeout threshold).
  • Verify decay begins: tick 1 (at 10.0s) halves speed to 5.0 m/s; tick 2 (at 11.0s) halves speed to 2.5 m/s; tick 3 (at 12.0s) halves speed to 1.25 m/s; tick 4 (at 13.0s) halves speed to 0.625 m/s.
  • Verify subsequent ticks continue until speed drops below 0.1 m/s, where it is clamped strictly to 0.0 m/s.
  3. *Multi-Second Cadence (7–8s) Immunity Test (`SpeedAndLocationDeviceDecayTest.kt`)*:
  • Emit valid location fixes arriving at 7.0 to 8.0 second intervals with active displacement ($\Delta d > 0$).
  • Verify that watchdog does not fire premature decay ticks between fixes and speed does not drop toward zero.
  4. *Decoupled Recovery on Fresh Location Fix Test (`SpeedAndLocationDeviceDecayTest.kt`)*:
  • Following speed decay ticks, emit a fresh valid location fix with speed 6.0 m/s.
  • Verify speed recovers directly to 6.0 m/s without averaging with the decayed artifact, pending decay ticks are cancelled, and pace sensor resumes emitting valid numeric pace.
  5. *Pace Sensor Nullification & Zero-Division Safety Test (`SpeedAndLocationDeviceDecayTest.kt`)*:
  • When speed decays to 0.0 m/s, verify `mPaceSensor.getValue()` is strictly `null` (no `Double.POSITIVE_INFINITY` or NaN emitted).
  • Verify cockpit pace display displays fallback dashes (`--:--`).
  6. *Distance Invariant Verification (`SpeedAndLocationDeviceDecayTest.kt`)*:
  • Verify that throughout the inactivity timeout and decay ticks, `mDistanceSensor` and `mLapDistanceSensor` accumulate zero phantom distance.
  7. *Clean Teardown & Lifecycle Verification (`SpeedAndLocationDeviceDecayTest.kt`)*:
  • Trigger `device.shutDown()` during an active decay state.
  • Verify all watchdog callbacks/tasks are cancelled and no memory leaks or post-termination callbacks occur.
  8. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` verifying 100% pass rate with zero regressions.

### TST-TRK-006: **Successive Geodesic Location Delta Fallback for Missing GPS Bearing & Speed Verification** [Verified]
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

### TST-TRK-007: **Permission-Gated Foreground Service Types Verification** [Verified]
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

### TST-TRK-008: **Guaranteed Workout Finalization and Completion Broadcast Test** [Verified]
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

### TST-CON-003: **BLE GATT Lifecycle, Thread-Safe Concurrency & Null-Safety Unit Tests** [Verified]
- **Requirement(s)**: `REQ-CON-012`
- **Component**: **BLE GATT Lifecycle, Thread-Safe Concurrency & Null-Safety Unit Tests**
- **Jira**: ``ATT-1348``
- **Test Description**:
  1. *Null GATT Resilience*: Instantiate `MyBTLEDevice` with null `mBluetoothGatt`; populate `mReadCharacteristicQueue`; invoke `readNextCharacteristic()`; drain main Looper; verify zero NPEs.
  2. *TOCTOU Race Simulation*: Trigger `readNextCharacteristic()`; nullify `mBluetoothGatt` concurrently before the Looper executes the runnable; verify the captured local snapshot prevents crash.
  3. *Disconnection Draining*: Enqueue characteristics; trigger `onConnectionStateChange(..., STATE_DISCONNECTED)`; verify `mReadCharacteristicQueue` is empty and `mBluetoothGatt` is null.
  4. *Delayed Battery Callback Cancellation*: Trigger battery update; verify delayed runnable is scheduled; trigger disconnection; verify delayed runnable is cancelled from `mHandler` and does not run.
  5. *Empty Queue Descriptor Safety*: Invoke empty queue branch when `mBluetoothGatt` is null or service/characteristic is missing; verify zero crashes.

### TST-CON-004: **Barometric Altitude Sensor Initialization & Null-Safe Correction Dispatch Test** [Verified]
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

### TST-CON-005: **BLE GATT Measurement Packet Parsing Resilience & Null-Safe Telemetry Test** [Verified]
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

### TST-CON-006: **Smartphone Battery Level & Remaining Duration Telemetry Unit & Localization Tests** [Verified]
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

### TST-CON-007: **Battery Remaining Duration Monotonic Countdown and Step-Transition Recalibration Verification** [Verified]
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

### TST-CON-008: **Barometric Altimeter Idempotent Calibration, Delta Shift Dispatch, Physical Sanity Limiting & Mountain Climb Preservation Test** [Verified]
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

### TST-CON-009: **Robust Bluetooth LE Sensor Discovery: GATT Queue Deadlock Prevention and State Reset Test** [Verified]
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

### TST-CON-010: **Continuous Sensor Battery State Persistence, Live Battery Display in Sensors List & Post-Workout Alerting Verification** [Verified]
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

### TST-CON-011: **Bluetooth LE Sensor Discovery, LE Transport Selection & Foreground Service Compliance Test** [Verified]
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

### TST-FIL-004: **Stabilized VAM (Vertical Speed) Metric, Location Accuracy Gating & Indoor Drift Suppression Verification** [Verified]
- **Requirement(s)**: `REQ-FIL-012`
- **Component**: **Stabilized VAM (Vertical Speed) Metric, Location Accuracy Gating & Indoor Drift Suppression Verification**
- **Jira**: `ATT-1621`, `ATT-1738`, `ATT-5373`
- **Test Description**:
  1. *Stationary Indoor Drift Suppression Tests (`VerticalSpeedAndSlopeDeviceTest.kt`)*:
  • Simulate stationary athlete (`speed = 0.0 m/s` or `speed = 0.2 m/s`) with altitude oscillating randomly by $\pm 0.5\text{ m}$ around 100m.
  • Verify `mVerticalSpeedSensor` broadcasts `0 m/h` and does not emit phantom climbing rates.
  2. *Location Accuracy Gating Verification (`VerticalSpeedAndSlopeDeviceTest.kt`)*:
  • Simulate athlete with degraded GPS accuracy ($> 20.0\text{ m}$, e.g. 25m or 35m) indoors/basement while altitude fluctuates.
  • Verify `calculateVam` suppresses vertical speed to `0 m/h` whenever GPS accuracy exceeds `VAM_MAX_GPS_ACCURACY_METERS = 20.0`.
  3. *Low-Speed Indoor GPS Wander Suppression Tests (`VerticalSpeedAndSlopeDeviceTest.kt`)*:
  • Simulate indoor wander with phantom horizontal speed between $0.5\text{ m/s}$ and $1.1\text{ m/s}$ ($2.0\dots 4.0\text{ km/h}$) and altitude jitter ($< 150\text{ m/h}$).
  • Verify `calculateVam` suppresses vertical speed to `0 m/h`.
  4. *Standstill Altitude Source Transition Artifact Suppression (`VerticalSpeedAndSlopeDeviceTest.kt`, ATT-5373)*:
  • Simulate exact Workout #455 trace: GPS altitude of 492.0 m during seconds 1–2 at standstill (`speed = 0.0 m/s`), transitioning at second 3 to DEM altitude of 438.0 m (-54 m geoid jump), followed by first 20m movement at seconds 11–16.
  • Verify `mDescentSensor` accumulation remains strictly `0.0 m` at standstill (second 3) and `mSlopeSensor` / `mVerticalSpeedSensor` exhibit no -59% slope or -3.0 m/s jump.
  5. *Barometric Recalibration Filter Shift Synchronization (`VerticalSpeedAndSlopeDeviceTest.kt`, `TimedMovingAverageFilterTest.kt`, ATT-5373)*:
  • Populate `cAltitudeFilter` and `cAltitudeSuperFilter` with steady altitude samples (e.g. 500.0 m).
  • Broadcast `ALTITUDE_CORRECTION_INTENT` with `deltaOffset = +20.0 m` (or `-20.0 m`).
  • Verify `cAltitudeFilter` and `cAltitudeSuperFilter` shift buffered timestamped values by `deltaOffset` so `getFilteredValue()` immediately shifts to 520.0 m (or 480.0 m) without transient decay, false descent, or false ascent.

### TST-TRK-009: **Guaranteed Defensive Location Device Initialization on Tracking Startup and BANAL Connection Verification** [Verified]
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

### TST-FIL-005: **Continuous Rolling Smoothing Telemetry, Session Reset Isolation & Live Pre-Start Metric Verification** [Verified]
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

### TST-TRK-010: **Sport-Specific Auto-Pause Engine, Anti-Chatter Hysteresis, Unit Conversion & Mutex Verification** [Verified]
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

### TST-TRK-011: **Real-Time Power-to-Weight (W/kg) Calculation, Weight Persistence & Unit Parity Test Suite** [Verified]
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

### TST-TRK-012: **Real-Time Normalized Power (NP) and Intensity Factor (IF) Calculation, Algorithm Math & Formatter Test Suite** [Verified]
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

### TST-TRK-013: **Real-Time Mechanical Work (kJ) and Training Stress Score (TSS) Telemetry Test Suite** [Verified]
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

### TST-TRK-014: **Streamlined Dual-Type (Location and Health) Foreground Service Verification Suite** [Verified]
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

### TST-TRK-015: **Growing 1..29s Warm-Up Window Verification Suite for NP, IF, and TSS** [Verified]
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

### TST-TRK-016: **MySensorManager Priority Hierarchy, DeviceType Enum Alignment, LINE_DISTANCE_m, Default Pairing & Canonical Rank Verification Suite** [Verified]
- **Requirement(s)**: `REQ-TRK-024`
- **Component**: **`MySensorManager` Priority Hierarchy, `DeviceType` Enum Alignment, `LINE_DISTANCE_m`, Default Pairing & Canonical Rank Verification Suite**
- **Jira**: ``ATT-3961`, `ATT-4375`, `ATT-5491`, `ATT-5492``
- **Test Description**:
  1. *`SPEED_PRIORITY`, `ALTITUDE_PRIORITY` & `LOCATION_PRIORITY` Ordering Invariant Test (`MySensorManagerPriorityTest.kt`)*:
  • Verify `MySensorManager.SPEED_PRIORITY` equals `[RUN_SPEED, BIKE_SPEED, BIKE_SPEED_AND_CADENCE, BIKE_POWER, SPEED_AND_LOCATION_GOOGLE_FUSED, SPEED_AND_LOCATION_GPS]`, with `SPEED_AND_LOCATION_GOOGLE_FUSED` strictly preceding `SPEED_AND_LOCATION_GPS`.
  • Verify `MySensorManager.ALTITUDE_PRIORITY` and `LOCATION_PRIORITY` order `SPEED_AND_LOCATION_GOOGLE_FUSED` ahead of `SPEED_AND_LOCATION_GPS`.
  2. *Google Fused `ProxySensor` Retention & GC String Test (`MySensorManagerPriorityTest.kt`)*:
  • Verify that registering `SPEED_mps`, `PACE_spm`, `DISTANCE_m`, `DISTANCE_m_LAP`, `ALTITUDE`, `LATITUDE`, and `LONGITUDE` from a `DeviceType.SPEED_AND_LOCATION_GOOGLE_FUSED` device retains non-null `ProxySensor` instances in `MySensorManager`, propagates live values (`6.5 m/s`, `2896.48 m`), and sets `'D'` and `'S'` in `SensorType.SENSORS` (`"-DS----AG--"`).
  3. *Priority Upgrade & Fallback Test (`MySensorManagerPriorityTest.kt`)*:
  • Verify that registering and unregistering `SPEED_AND_LOCATION_GPS`, `SPEED_AND_LOCATION_GOOGLE_FUSED`, and `BIKE_SPEED` dynamically upgrades and falls back `getBestSensor` and `ProxySensor` values according to `BIKE_SPEED > SPEED_AND_LOCATION_GOOGLE_FUSED > SPEED_AND_LOCATION_GPS`.
  4. *`DeviceType` Enum Declaration Order Test (`DeviceTypeEnumOrderTest.kt`)*:
  • Verify that in `DeviceType.java`, `SPEED_AND_LOCATION_GPS.ordinal() < SPEED_AND_LOCATION_GOOGLE_FUSED.ordinal()`.
  5. *`LINE_DISTANCE_m` Priority Mapping & `getBestSensor` Fallback Test (`MySensorManagerPriorityTest.kt`)*:
  • Verify `MySensorManager.getPriorityList(SensorType.LINE_DISTANCE_m)` equals `LOCATION_PRIORITY`.
  • Verify that when both `SPEED_AND_LOCATION_GOOGLE_FUSED` and `SPEED_AND_LOCATION_GPS` are registered for `LINE_DISTANCE_m`, `getBestSensor(SensorType.LINE_DISTANCE_m)` selects `SPEED_AND_LOCATION_GOOGLE_FUSED`.
  6. *Canonical Priority Rank Helper Test (`MySensorManagerPriorityTest.kt`)*:
  • Verify `MySensorManager.getPriorityRank(sensorType, deviceType)` returns 0 for `SPEED_AND_LOCATION_GOOGLE_FUSED` and 1 for `SPEED_AND_LOCATION_GPS` across location sensor types.
  • Verify speed ranks: `BIKE_SPEED (1) -> SPEED_AND_LOCATION_GOOGLE_FUSED (4) -> SPEED_AND_LOCATION_GPS (5)`.
  • Verify altitude ranks: `PRESSURE (0) -> POSITION (1) -> SPEED_AND_LOCATION_GOOGLE_FUSED (2) -> SPEED_AND_LOCATION_GPS (3)`.
  • Verify fallback ranks `1000 + deviceType.ordinal()` for unmapped devices and `Integer.MAX_VALUE` for null `deviceType`.
  7. *`DevicesDatabaseManager.addLocationDevicesToDb` Insertion Sequence & Default Pairing Tests (`DevicesDatabaseManagerOrderingTest.kt`)*:
  • Verify insertion sequence `_id = 1` for `SPEED_AND_LOCATION_GPS` and `_id = 2` for `SPEED_AND_LOCATION_GOOGLE_FUSED`.
  • Verify that when Google Play Services is available, `SPEED_AND_LOCATION_GOOGLE_FUSED` is paired (`PAIRED = 1`) and `SPEED_AND_LOCATION_GPS` is unpaired (`PAIRED = 0`).
  • Verify that when Google Play Services is unavailable (de-Googled fallback), `SPEED_AND_LOCATION_GPS` is paired (`PAIRED = 1`) and `SPEED_AND_LOCATION_GOOGLE_FUSED` is unpaired (`PAIRED = 0`).
  8. *SensorSourceDialog Priority Sorting Test (`SensorSourceDialogPriorityTest.kt`)*:
  • Verify that `activeBackups` and `notConnected` lists in `SensorSourceDialog` sort `SPEED_AND_LOCATION_GOOGLE_FUSED` ahead of `SPEED_AND_LOCATION_GPS`.
  9. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with zero regressions.

### TST-TRK-017: **Deterministic AdbGpsInjector Mock Provider Lifecycle (PLAYING Registration & Immediate PAUSED/STOPPED/EOF Removal) Verification Suite** [Verified]
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

### TST-TRK-018: **Resumed Workout Sample Table Resolution, Asynchronous BANAL Binding Barrier & Sample Write Error Surfacing Verification Suite** [Verified]
- **Requirement(s)**: `REQ-TRK-026`
- **Component**: **Resumed Workout Sample Table Resolution, Asynchronous BANAL Binding Barrier & Sample Write Error Surfacing Verification Suite**
- **Jira**: ``ATT-3980, ATT-5827``
- **Test Description**:
  1. *`applyResumedState` Table Name & Barrier Synchronization Test (`TrackerServiceResumeTest.kt`)*:
  • Verify `applyResumedState(state)` updates `mBaseFileName`, recomputes `mSamplesTableName = "\"<baseFileName>\""`, and completes `mTableInitializationFuture`.
  2. *Asynchronous Deferred `RESUME_SERVICE_RECREATION` & `RESUME_BY_USER` End-to-End Tests (`TrackerServiceResumeTest.kt`)*:
  • Simulate `onStartCommand()` with `mBanalService == null` followed by `mBanalConnection.onServiceConnected()`; verify `mSamplesTableName` is never `"\"null\""`, `mTableInitializationFuture` remains incomplete until `applyResumedState()` runs, `recreateValuesWhenResuming()` executes before `createNewLap()` and `onSearchingFinished()`, and subsequent `sampleAndWriteToDb()` ticks insert rows into the resumed workout's SQLite sample table. Verify that cancelling `mTrackerHandle` via `cancelTrackerHandle()` halts background 1 Hz sampling immediately after `onStartCommand()`, eliminating timing races and ensuring deterministic single-execution assertions (`verify(exactly = 1) { mockSamplesDb.setTransactionSuccessful() }`) without thread leakage (ATT-5827).
  3. *Invalid Table Name Guard & SQLite Error Surfacing Tests (`TrackerServiceResumeTest.kt`)*:
  • Verify `WorkoutSamplesDatabaseManager.getTableName()` throws `IllegalArgumentException` for `null`, empty/blank, or `"null"` input.
  • Verify `TrackerService.handleMissingColumns()` rethrows non-column `SQLiteException` errors instead of silently swallowing them.
  4. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with zero regressions.

### TST-TRK-019: **Fault-Tolerant 1 Hz Sampling Loop, Clock Timer Exception Guard & Volatile mBanalService Snapshot Concurrency Verification Suite** [Verified]
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

### TST-TRK-020: **ProxySensor Timestamp Delegation, End-to-End MySensorManager + AutoPauseManager Integration, Manual Resume Sync & ToneGenerator Lifecycle Verification Suite** [Verified]
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

### TST-TRK-021: **Safe SQLite Column Identifier Escaping for Custom Device Names (' and ") & Rate-Limited Sample Insert Failure Telemetry Verification Suite** [Verified]
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

### TST-TRK-022: **Fault-Isolated Workout Finalization, Dedicated FINISHED=1 Commit, Defensive AccumulatedSensorTypeSet Copy & Zero-Duration Average Speed Verification Suite** [Verified]
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

### TST-TRK-023: **LiveWorkoutSession Stream & Extrema Rehydration on Workout Resume, Sample Count Persistence & finalizeLiveSession() Continuity Verification Suite** [Verified]
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

### TST-TRK-024: **Thread-Safe Sensor, Device, Filter, Service & Live Session Concurrency Verification Suite** [Verified]
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

### TST-TRK-025: **Non-Blocking TrackerService.onDestroy() Finalization on mDbExecutor, Deterministic mScheduler Shutdown & Async Init Failure Recovery Verification Suite** [Verified]
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

### TST-SEN-020: **Sensor Onboarding, Empty-State Action & Discovery Auto-Pairing Verification Suite** [Verified]
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

### TST-TRK-026: **Foreground-Protected Asynchronous Workout Finalization and Stop Lifecycle Isolation Verification Suite** [Verified]
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

### TST-TRK-027: **Deterministic BANAL State Snapshotting and Bound Lifecycle Preservation Verification Suite** [Verified]
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

### TST-TRK-028: **Lock-Free ProxySensor Listener Dispatch & Deadlock Prevention Concurrency Suite** [Verified]
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

### TST-TRK-029: **In-Memory LiveWorkoutSession Reuse on User Resume and Safe Publication Verification Suite** [Verified]
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

### TST-CON-012: **Resilient Bluetooth LE & ANT+ Reconnection, Immutable Telemetry Dispatch, and Queue Integrity Verification Suite** [Verified]
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

### TST-TRK-030: **AutoPauseManager In-Motion Dropout vs. Standstill Silence Verification Suite** [Verified]
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

### TST-TRK-031: **CyclingPowerMetricsDevice Constructor Seeding, Sensor-Presence Gating (Rule 52), Stationary/Paused W/kg Alignment, and Workout Summary Presentation Defense Verification Suite** [Verified]
- **Requirement(s)**: `REQ-TRK-038`, `REQ-UI-106`
- **Component**: **CyclingPowerMetricsDevice Constructor Seeding, Sensor-Presence Gating (Rule 52), Stationary/Paused W/kg Alignment, and Workout Summary Presentation Defense Verification Suite**
- **Jira**: ``ATT-4368``
- **Test Description**:
  1. *Sensor-Presence & Active Telemetry Gating on Constructor & Suspended State Test (`TST-TRK-031.1`, `CyclingPowerMetricsDeviceTest.kt`)*:
  • Mock `sensorManager.getSensor(SensorType.POWER)` returning `null` or an inactive power sensor with `powerSensorData.value == null` (Rule 52).
  • Instantiate `CyclingPowerMetricsDevice` with CP = 250W and W' = 20.0 kJ.
  • Assert `wPrimeBalSensor.sensorData.value == null` immediately upon instantiation (Rule 52).
  • Invoke `calculateMetrics()` with `isSuspended = true` and `isSuspended = false`: assert `wPrimeBalSensor.sensorData.value == null` (preventing phantom SQLite extrema before real power arrives).
  2. *Active Power Sample Telemetry-Activated W'bal Emission Test (`TST-TRK-031.2`, `CyclingPowerMetricsDeviceTest.kt`)*:
  • Mock `sensorManager.getSensor(SensorType.POWER)` returning active power sensor.
  • Instantiate `CyclingPowerMetricsDevice` with CP = 280W (and with FTP fallback 250W) and W' = 20.0 kJ.
  • Assert `wPrimeBalSensor.sensorData.value == null` initially (preventing unearned emissions before power packets).
  • Inject first active power sample (e.g. 250W) and invoke `calculateMetrics()`: assert `wPrimeBalSensor.sensorData.value` emits full/stepped capacity ($\approx 20.0\text{ kJ}$).
  • Instantiate with W' = 0.0f: assert `wPrimeBalSensor.sensorData.value == null`.
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
  10. *TelemetryMetricUtils W'bal Chart Power Gating Test (`TST-TRK-031.10`, `TelemetryMetricUtilsTest.kt`)*:
  • Evaluate `TelemetryMetricUtils.hasWPrimeBalData(path)` with points having `wPrimeBalKj >= 0.0` but `power == null || power <= 0`: assert returns `false`.
  • Evaluate with points having valid `power > 0` and `wPrimeBalKj >= 0.0`: assert returns `true`.

### TST-FIL-014: **Backend Filter Key Normalization, Runtime 'Best Sensor' Field Registration & Post-Trim Rolling Filter NaN Guard Verification Suite** [Verified]
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

### TST-CON-021: **BLE Sensor Search Android 8–11 Compatibility and Defensive Exception Handling Verification Suite** [Verified]
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

### TST-CON-022: **Automated BLE/ANT+ Pedal Power Balance Reference Normalization & Obsolete UI Checkbox Deprecation Verification Suite** [Verified]
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

### TST-TRK-039: **Tracking Subsystem Robustness, Resource Lifecycle & Rate-Limited Error Telemetry Hygiene Verification Suite** [Verified]
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

### TST-TRK-041: **Distance Increment Plausibility Clamping & Multi-Sensor Summary Isolation Test Suite** [Verified]
- **Requirement(s)**: `REQ-TRK-041`
- **Component**: `SpeedAndLocationDeviceTest.kt`, `TrackerServiceTest.java`
- **Jira**: `ATT-4843`
- **Test Description**:
  1. *SpeedAndLocationDevice Displacement Plausibility Clamping (`TST-TRK-041.1`)*:
  • Unit test verifying that when consecutive location fixes arrive with displacement velocity exceeding 70.0 m/s (e.g. 4,245.6 m in 1000 ms), `delta_distance` is clamped to 0.0, and `mDistanceSensor` / `mLapDistanceSensor` values remain unchanged.
  • Unit test verifying that when consecutive location fixes arrive with normal human velocity (e.g. 15.0 m in 1000 ms = 15 m/s), distance accumulators increment by the full displacement.
  2. *TrackerService Canonical Sensor Summary Filtering (`TST-TRK-041.2`)*:
  • Unit test verifying that when `sampleAndWriteToDb()` is called with both canonical `ProxySensor` (`deviceName == null`, distance = 4,370 m) and a secondary device sensor (`deviceName == "Google Fused"`, distance = 13,377 m), `WorkoutSummaries.DISTANCE_TOTAL_m` and `mDistanceTotal_m` equal 4,370 m.
  • Verify that `samplingValues` table row retains both `DISTANCE_m` (4,370 m) and `"DISTANCE_m (Google Fused)"` (13,377 m).
  3. *Targeted & Clean-Room Regression Verification (`TST-TRK-041.3`)*:
  • Execute `./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.devices.*" --tests "com.atrainingtracker.trainingtracker.tracker.*"` verifying 100% pass rate.

### TST-TRK-042: **1-Hz Live-Tracking Memory, Decoupling & Recomposition Optimization Test Suite** [Verified]
- **Requirement(s)**: `REQ-TRK-042`
- **Component**: `BANALServiceRepositoryMemoryTest.kt`, `LiveTrackDecimationTest.kt`, `TrackingViewModelMapStateTest.kt`, `TrackingElevationProfileResolutionTest.kt`
- **Jira**: `ATT-4863`, `ATT-4923`
- **Test Description**:
  1. *Amortized O(1) Snapshot Append & Deduplication (`TST-TRK-042.1`)*:
  • Unit test in `BANALServiceRepositoryMemoryTest` asserting `SnapshotChunkedList` provides $O(1)$ amortized appends and bounded allocation with 15,000 synthetic points without copying prior list elements.
  • Unit test verifying stationary ticks suppress duplicate `PathPoint` allocations and `DeviceTelemetry` structural equality.
  2. *Incremental Chunked Decimation & Polyline Stability (`TST-TRK-042.2`)*:
  • Unit test in `LiveTrackDecimationTest` asserting `IncrementalTrackDecimator` simplifies historical chunks once, permanently freezes them, preserves 200-point unsimplified tail, and maintains strict historical vertex stability across 1-Hz ticks.
  3. *Sensor Field Combine Decoupling & Elevation Gating (`TST-TRK-042.3`)*:
  • Unit test in `TrackingViewModelMapStateTest` / `TrackingElevationProfileResolutionTest` verifying `loadSensorFieldStates()` combines sensor data independently of `currentPathPoints`, emitting at most once per sensor tick.
  4. *Clean-Room Package Regression (`TST-TRK-042.4`)*:
  • Execute targeted tests across `BANALServiceRepository`, `MapLayers`, and `TrackingViewModel` suites verifying 100% pass rate.


### TST-TRK-043: **Sport-Specific Auto-Pause Resume Debounce & Symmetric Single-Threshold Test Suite** [Verified]
- **Requirement(s)**: `REQ-TRK-043`
- **Component**: `AutoPauseManagerTest.kt`, `AutoPauseViewModelTest.kt`, `TranslationParityTest.kt`
- **Jira**: `ATT-4839`
- **Test Description**:
  1. *Sport-Specific Multi-Tick Resume Debounce (`TST-TRK-043.1`)*:
  • Unit test in `AutoPauseManagerTest` verifying that in `AUTO_PAUSED` state, moving at speed $\ge$ threshold requires strictly $N$ consecutive 1-Hz ticks (Bike=2, Run=5, Other=10) before transitioning to `TRACKING` and emitting `SoundCue.AUTO_RESUME`.
  2. *GPS Jitter Spike Rejection & Counter Reset (`TST-TRK-043.2`)*:
  • Unit test verifying that a transient speed spike $\ge$ threshold lasting fewer than $N$ ticks followed by a stationary tick ($< v_{\text{threshold}}$) or stale packet ($> 5000\text{ ms}$) resets `resumeDebounceCounter` to 0 and maintains `AUTO_PAUSED` state without emitting audio tones.
  3. *Single-Threshold Evaluation without Hysteresis Offset (`TST-TRK-043.3`)*:
  • Unit test verifying that speed $\ge v_{\text{threshold}}$ (without $+0.5$ offset) counts toward resume debounce, and speeds $< v_{\text{threshold}}$ maintain pause.
  4. *ViewModel & DataStore Preferences Persistence (`TST-TRK-043.4`)*:
  • Unit test in `AutoPauseViewModelTest` asserting that updating `autoPauseBikeResumeDebounceSec`, `autoPauseRunResumeDebounceSec`, and `autoPauseOtherResumeDebounceSec` immediately updates `tuningConfig` StateFlow, and `resetAutoPausePreferences(sportType)` resets resume debounce values to defaults.
  5. *9-Language Localization Parity (`TST-TRK-043.5`)*:
  • Test in `TranslationParityTest` verifying all resume detection time title, description, and default strings exist in all 9 translation sets.


### TST-TRK-044: **Optional Automatic Stationary Auto-Pause at Workout Start Test Suite** [Verified]
- **Requirement(s)**: `REQ-TRK-044`
- **Component**: `AutoPauseManagerTest.kt`, `TrackerServiceTest.kt`, `AutoPauseViewModelTest.kt`, `TranslationParityTest.kt`
- **Jira**: `ATT-4840`, `ATT-4925`
- **Test Description**:
  1. *Domain Startup Predicate Evaluation (`TST-TRK-044.1`)*:
  • Unit test in `AutoPauseManagerTest` verifying `shouldStartInAutoPause` returns `true` when `hasSpeedSource` is `true`, `startInAutoPauseEnabled` is `true`, sport auto-pause is enabled, and speed is null, 0.0 m/s, or below the sport pause threshold.
  • Verify it returns `false` when `hasSpeedSource` is `false`, `startInAutoPauseEnabled` is `false`, sport auto-pause is disabled, or speed is $\ge$ threshold.
  2. *Workout Start Transition Integration (`TST-TRK-044.2`)*:
  • Unit test in `TrackerServiceTest` / `TrackerService` asserting that when starting a new workout (`START_NORMAL`) with stationary speed, `hasSpeedSource == true`, and `startInAutoPauseEnabled == true`, state initializes directly to `AUTO_PAUSED` and `TrackingMode.PAUSED` (`isAutoPaused = true`).
  • Unit test verifying that when starting a new workout with `hasSpeedSource == false` (e.g. indoor HR-only), state initializes to `TRACKING` even if `startInAutoPauseEnabled == true`.
  3. *Mid-Workout Speed Source Removal from AUTO_PAUSED (`TST-TRK-044.3`)*:
  • Unit test in `AutoPauseManagerTest` verifying that when in `AUTO_PAUSED` and `hasSpeedSource` becomes `false`, `evaluateTick` transitions to `SENSOR_LOST` with `triggerResume = true`.
  4. *ViewModel & DataStore Toggle Mutation (`TST-TRK-044.4`)*:
  • Unit test in `AutoPauseViewModelTest` asserting that `updateStartInAutoPauseEnabled(true/false)` dispatches to DataStore and reactively updates `tuningConfig` StateFlow.
  5. *9-Language Localization Parity (`TST-TRK-044.5`)*:
  • Test in `TranslationParityTest` verifying `tuning_auto_pause_start_in_auto_pause_title` and `tuning_auto_pause_start_in_auto_pause_desc` exist in all 9 supported locales.


### TST-TRK-045: **Non-Recording Status Resolution, UI Banner, and Grid Dimming Test Suite** [Verified]
- **Requirement(s)**: `REQ-TRK-045`
- **Component**: `SensorGridStatusTest.kt`, `SensorGridScreen.kt`, `TrackingTabsScreen.kt`, `TranslationParityTest.kt`, `ThemeColorCentralizationAuditTest.kt`
- **Jira**: `ATT-4856`, `ATT-5283`
- **Test Description**:
  1. *Status Resolution Logic (`TST-TRK-045.1`)*:
  • Unit test in `SensorGridStatusTest` verifying `resolveCockpitStatus`:
    - Returns `CockpitStatus.NOT_STARTED` when `screenMode == ScreenMode.TRACKING` and `trackingMode == TrackingMode.READY`.
    - Returns `CockpitStatus.PAUSED` when `screenMode == ScreenMode.TRACKING` and `trackingMode == TrackingMode.PAUSED` with `isAutoPaused == false`.
    - Returns `CockpitStatus.AUTO_PAUSED` when `screenMode == ScreenMode.TRACKING` and `trackingMode == TrackingMode.PAUSED` with `isAutoPaused == true`.
    - Returns `null` when `trackingMode == TrackingMode.TRACKING` or `screenMode != ScreenMode.TRACKING`.
  2. *Sensor Grid Dimming Alpha Resolution (`TST-TRK-045.2`)*:
  • Unit test in `SensorGridStatusTest` verifying `resolveGridAlpha`:
    - Returns `0.6f` when `screenMode == ScreenMode.TRACKING` and `trackingMode != TrackingMode.TRACKING` (`READY`, `PAUSED`).
    - Returns `1.0f` when `screenMode == ScreenMode.TRACKING` and `trackingMode == TrackingMode.TRACKING`.
    - Returns `1.0f` when `screenMode == ScreenMode.CONFIGURATION` or `ScreenMode.PREVIEW` regardless of `trackingMode`.
  3. *9-Language Localization Parity (`TST-TRK-045.3`)*:
  • Test in `TranslationParityTest` verifying `cockpit_status_not_started` and `cockpit_status_paused` exist in all 9 supported locales (`values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`).
  4. *Theme Token Centralization & Stationary Overlay Audit (`TST-TRK-045.4`)*:
  • Verification via `ThemeColorCentralizationAuditTest` ensuring `SensorGridScreen.kt` and `TrackingTabsScreen.kt` use centralized blue theme tokens (`primaryContainer` / `onPrimaryContainer` / `primary`) without hardcoded hex colors.
  • Verification that `NonRecordingStatusBanner` is hosted in `TrackingTabsScreen.kt` as a stationary overlay outside `HorizontalPager` for `currentPage > 0` and hidden on `currentPage == 0`.


### TST-TRK-046: **Per-Sensor-Field Display Elements Configuration Test Suite** [Verified]
- **Requirement(s)**: `REQ-TRK-046`
- **Component**: `TrackingViewsDatabaseManagerTest.kt`, `TrackingViewsRepositoryTest.kt`, `EditSensorFieldViewModelTest.kt`, `SensorFieldView.kt`, `TranslationParityTest.kt`, `ThemeColorCentralizationAuditTest.kt`
- **Jira**: `ATT-4841`, `ATT-4927`
- **Test Description**:
  1. *Database Schema v18 Migration & Legacy Settings Carryover (`TST-TRK-046.1`)*:
  • Unit test in `TrackingViewsDatabaseManagerTest` verifying:
    - Upgrading to schema v18 evaluates `VIEWS_TABLE.ActivityType` to determine `sportType`.
    - HR and Power rows inherit `ShowBar` from legacy `ZoneDisplayOptions.showGauge` (e.g. `false` -> `0`, `true` -> `1`) for supported sports (`HR` in `RUN`/`BIKE`, `POWER` in `BIKE`).
    - Bike power balance and W' battery bars receive `ShowBar = 1` for `BIKE`, and `0` for other sports.
    - Fields with `ShowBar == 0` enforce `ShowValue = 1`.
    - Fixes stale mock setup in `TrackingViewsDatabaseManagerTest`.
    - `DatabaseSchemaRegistry.isVersionSupported("TrackingViews.db", 18)` returns `true`.
  2. *Repository Model & Flow Projection (`TST-TRK-046.2`)*:
  • Unit test in `TrackingViewsRepositoryTest` verifying:
    - `fetchSensorFieldConfigs` and `fetchSingleSensorFieldConfig` populate `showValue` and `showBar` correctly.
    - `updateSensorFieldConfig` and `insertSensorFieldConfig` persist `showValue` and `showBar` to the database.
  3. *Sport-Dependent Bar Support in Edit Dialog (`TST-TRK-046.3`)*:
  • Unit test in `EditSensorFieldViewModelTest` verifying:
    - Canonical `isBarSupported(sensorType, sportType)` returns `true` for HR in Run/Bike and Power/Balance/W'Bal in Bike; returns `false` for all other combinations.
    - When `isBarSupported == false`, Value/Bar switches are hidden and state enforces `showValue = true, showBar = false`.
    - When `isBarSupported == true`, mutual exclusion constraint (at least one active) is enforced.
  4. *Empty-Tile Fallback in Cockpit Sensor Field (`TST-TRK-046.4`)*:
  • Unit test in `SensorFieldZoneDisplayTest` / `SensorFieldView` verifying:
    - When a field has `showValue = false, showBar = true`, but no active bar can be drawn (`zoneGaugeState == null` or unsupported sport), the numeric value row renders as a fallback so the card is never blank.
  5. *9-Language Localization Parity (`TST-TRK-046.5`)*:
  • Verification in `TranslationParityTest` for `sensor_field_display_elements_title`, `sensor_field_show_value`, `sensor_field_show_bar` across all 9 locales.
  6. *Clean-Room Full Suite Regression Execution (`TST-TRK-046.6`)*:
  • Execute targeted unit tests and verify 100% pass rate with zero regressions.


### TST-TRK-047: **Manual Altitude Calibration with Known Location Persistence & Drift Correction Test Suite** [Verified]
- **Requirement(s)**: `REQ-TRK-047`
- **Component**: `SensorSourceDialogAltitudeCalibrationTest.kt`, `KnownLocationsDatabaseManagerTest.kt`, `TrackingTabsViewModelLocationTest.kt`, `TrackerServiceAltitudeTest.kt`, `LiveWorkoutSessionTest.kt`, `TranslationParityTest.kt`
- **Jira**: `ATT-4845`, `ATT-5072`
- **Test Description**:
  1. *Header Dialog Manual Calibration & State Binding (`TST-TRK-047.1`)*:
  • Unit test in `SensorSourceDialogTest` verifying:
    - `SensorSourceDialog` renders the calibration section when `sensorType == SensorType.ALTITUDE`.
    - Reference altitude input is pre-filled with the current altitude reading formatted via canonical `AltitudeFormatter`.
    - Confirming calibration invokes `onCalibrate(altitudeMeters, locationName, saveAsKnownLocation)` and dispatches calibration to altimeter.
  2. *Known Location Geofence Upsert with Manual Provenance (`TST-TRK-047.2`)*:
  • Unit test in `KnownLocationsDatabaseManagerTest` verifying:
    - `upsertLocationByGeofence` stores `(latLng, altitude, name)` with `ElevationSource.MANUAL_USER` and `isLocked = true`.
    - Subsequent queries via `getMyLocation(latLng)` return the stored ground-truth altitude and name.
  3. *Case A: Retroactive Uniform Altitude Shift on Uncalibrated Workouts (`TST-TRK-047.3`)*:
  • Unit test in `TrackerServiceAltitudeTest` and `LiveWorkoutSessionTest` verifying:
    - When a workout starts with $t_0 = \text{none}$ and calibration offset $\Delta h = +35.0\text{ m}$ is applied at $t_1$, all existing samples in `samplesTable`, `sampledAltitudes`, `RunningStats` (min/max/sum), and `_currentPathPoints` are shifted by $+35.0\text{ m}$.
    - $t_0$ is updated to $t_1$.
  4. *Case B: Piecewise-Linear Drift Interpolation Between Calibrations (`TST-TRK-047.4`)*:
  • Unit test in `TrackerServiceAltitudeTest` and `LiveWorkoutSessionTest` verifying:
    - When previously calibrated at $t_0$ and recalibrated at $t_1 > t_0$ with drift $\Delta h = -18.0\text{ m}$, samples in $t \in [t_0, t_1]$ receive correction $\Delta h \cdot \frac{t - t_0}{t_1 - t_0}$.
    - Sample at $t_0$ has zero change, sample at $t_1$ has $-18.0\text{ m}$ change, and intermediate samples scale strictly monotonically.
    - Running ascent and descent metrics are recomputed without false vertical spikes.
  5. *9-Language Localization Parity (`TST-TRK-047.5`)*:
  • Verification in `TranslationParityTest` for `altitude_calibration_title`, `altitude_calibration_reference_label`, `altitude_calibration_location_name`, `altitude_calibration_save_location`, `altitude_calibration_action`, and `altitude_calibrated_success` across all 9 locales.
  6. *Save as Known Location Unchecked by Default & Conditional Name Input (`TST-TRK-047.6`)*:
  • Unit test in `SensorSourceDialogAltitudeCalibrationTest` verifying that when `matchedLocation == null`, the "Save as Known Location" checkbox defaults to unchecked (`false`), and the location name input field is hidden until explicitly checked (ATT-5072).
  7. *Matched Known Location Recognition, Pre-fill & Elevation Update (`TST-TRK-047.7`)*:
  • Unit test in `SensorSourceDialogAltitudeCalibrationTest` and `TrackingTabsViewModelTest` verifying that when `matchedLocation != null`, the dialog displays the matched location name, hides the redundant save checkbox, pre-fills reference altitude from the stored location, and calibration updates the existing `KnownLocations.db` record with `ElevationSource.MANUAL_USER` (ATT-5072).


### TST-SEN-025: **Position-Based Elevation Sensor & Dedicated Spatial DEM Cache Test Suite** [Verified]
- **Requirement(s)**: `REQ-SEN-025`
- **Component**: `ElevationCacheDatabaseManagerTest.kt`, `AltitudeFromPositionDeviceTest.kt`, `WorkoutRepositoryTelemetryTest.kt`, `ElevationSmoothingMathTest.kt`, `DeviceTypeTest.kt`, `SensorSourceDialogPriorityTest.kt`, `TranslationParityTest.kt`
- **Jira**: `ATT-4859`, `ATT-5199`, `ATT-5312`
- **Test Description**:
  1. *Spatial Elevation Cache CRUD & Nearest-Neighbor Interpolation (`TST-SEN-025.1`)*:
  • Unit test in `ElevationCacheDatabaseManagerTest` verifying:
    - Inserting quantized coordinates `(latE5, lonE5, altitude, source)` persists rows to `elevation_cache`.
    - Querying exact quantized coordinates returns cached elevation.
    - Querying coordinates within spatial tolerance ($\Delta\text{E5} \le 2$) resolves nearest neighbor.
    - Querying distant coordinates (> tolerance) returns null (cache miss).
  2. *Background Route/Segment Elevation Seeding & Source Priority (`TST-SEN-025.2`)*:
  • Unit test in `ElevationCacheDatabaseManagerTest` verifying:
    - Seeding from mock `RoutesDatabaseManager` inserts non-null trackpoint altitudes into `ElevationCache.db` with `source = ROUTE_SEED`.
    - Seeding from `SEGMENT_SEED` uses `CONFLICT_IGNORE` and NEVER overwrites existing `ROUTE_SEED` or `DEM_CACHE` records.
    - Seeding from `ROUTE_SEED` updates existing `SEGMENT_SEED` records but NEVER overwrites authoritative `DEM_CACHE` records.
    - Inserting authoritative `DEM_CACHE` entries replaces lower-priority seeded points.
  3. *Device Lifecycle, Pairing Toggle, Async DEM Query & Online Upgrade (`TST-SEN-025.3`)*:
  • Unit test in `AltitudeFromPositionDeviceTest` and `DeviceManagerTest` verifying:
    - `AltitudeFromPositionDevice` initializes with `DeviceType.ALTITUDE_FROM_POSITION` under `Protocol.SMARTPHONE`.
    - Pairing toggle in `DevicesDatabaseManager` dynamically starts and shuts down the device.
    - Incoming `(latitude, longitude)` updates on a cache miss trigger async DEM fetch via `ElevationService` and cache with `source = DEM_CACHE`.
    - Incoming updates matching a seeded non-DEM entry (`ROUTE_SEED` / `SEGMENT_SEED`) emit the cached altitude immediately for offline continuity AND dispatch an async DEM query to upgrade the cache to `DEM_CACHE`.
    - Concurrent location updates while an async DEM query is in flight do not queue redundant queries (`AtomicBoolean mIsDemQueryInFlight`).
  4. *Zero Contamination of Known Locations (*Lieblingsorte*) (`TST-SEN-025.4`)*:
  • Architectural isolation test verifying:
    - Continuous position tracking and cache insertion in `ElevationCache.db` execute with zero calls or writes to `KnownLocationsDatabaseManager`.
  5. *9-Language Localization Parity (`TST-SEN-025.5`)*:
  • Verification in `TranslationParityTest` for `device_type_altitude_from_position`, `device_type_short_altitude_from_position`, `devices_altitude_from_position`, and `devices_altitude_from_position_description` across all 9 locales.
  6. *2D Route/Segment Zero Elevation Rejection & Self-Healing Purge (`TST-SEN-025.6`)*:
  • Unit test in `ElevationCacheDatabaseManagerTest` verifying:
    - `seedFromRoutes()` and `seedFromSegments()` reject dummy 0.0m points (`abs(elevation) < 1e-4`) and skip routes where all elevations are 0.0.
    - `purgeCorruptZeroSeeds()` deletes existing 0.0m `ROUTE_SEED` and `SEGMENT_SEED` entries on startup while preserving legitimate points and `DEM_CACHE` records.
  7. *Neighbor Seed Cell DEM Upgrade (`TST-SEN-025.7`)*:
  • Unit test in `AltitudeFromPositionDeviceTest` verifying:
    - Matching a non-authoritative neighbor seed cell triggers async DEM query and, on success, upgrades both the current coordinates and the neighbor seed cell to `DEM_CACHE`.
  8. *Telemetry Null-Altitude Parity & Outlier Smoothing (`TST-SEN-025.8`)*:
  • Unit tests in `WorkoutRepositoryTelemetryTest` and `ElevationSmoothingMathTest` verifying:
    - `WorkoutRepository.loadWorkoutDetailTelemetry` returns `null` for unrecorded altitude samples.
    - `ElevationSmoothingMath.smoothAltitudes` interpolates isolated 0.0m dropouts when median altitude $> 50.0$ m, and preserves valid 0.0m elevations when median altitude $\le 50.0$ m.


### TST-TRK-048: **Stationary GPS Jitter Suppression, Doppler Zero Preservation & Anchor Pinning Test Suite** [Verified]
- **Requirement(s)**: `REQ-TRK-048`
- **Component**: `SpeedAndLocationDeviceStationaryJitterTest.kt`, `SpeedAndLocationDeviceResilienceTest.kt`, `BANALServiceRepositoryTest.kt`
- **Jira**: `ATT-4850`
- **Test Description**:
  1. *Stationary GNSS Jitter Zero Speed & Zero Distance Increment (`TST-TRK-048.1`)*:
  • Unit test in `SpeedAndLocationDeviceStationaryJitterTest` feeding consecutive 1-Hz synthetic GPS fixes with `hasSpeed() == true`, `getSpeed() == 0.0f`, `accuracy = 5.0m`, and coordinates jittering randomly within $1.5\text{--}3.0\text{ m}$.
  • Verifies that `SPEED_mps` remains clamped to 0.0, `DISTANCE_m` does not increment, `BEARING` remains stable, and published coordinates match the stationary anchor.
  2. *Stationary Anchor Unpinning on True Movement (`TST-TRK-048.2`)*:
  • Unit test in `SpeedAndLocationDeviceStationaryJitterTest` transitioning from stationary jitter to true forward motion with displacement exceeding $\max(3.0\text{ m},\, 0.6 \cdot \text{accuracy})$.
  • Verifies that the anchor unpins, speed transitions smoothly, distance accumulation resumes, and bearing updates along the direction of motion.
  3. *Provider Without Doppler Velocity Fallback (`TST-TRK-048.3`)*:
  • Unit test in `SpeedAndLocationDeviceStationaryJitterTest` feeding fixes with `hasSpeed() == false`.
  • Verifies that jitter within the deadband produces 0.0 speed and zero distance increment, while sustained movement beyond the deadband accurately computes geometric speed `delta_distance / dt` and accumulates distance.
  4. *Spider-Map Breadcrumb Suppression in Repository (`TST-TRK-048.4`)*:
  • Unit test in `BANALServiceRepositoryTest` verifying that stationary jitter fixes do not generate or append spiderweb line segments to `_currentTrack`.
  5. *Existing Invariant Parity - Speed Decay Watchdog & Teleportation Spike Gating (`TST-TRK-048.5`)*:
  • Regression verification confirming that sensor inactivity continues to trigger watchdog speed decay (`REQ-TRK-013`) and multipath teleportation spikes ($> 70\text{ m/s}$) are rejected (`REQ-TRK-041.1`).


### TST-TRK-049: **Post-Workout State Cleansing, Breadcrumb Purge & Idle Service Dormancy Test Suite** [Verified]
- **Requirement(s)**: `REQ-TRK-049`
- **Component**: `BANALServiceRepositoryTest.kt`, `BANALServiceBindingLifecycleTest.kt`, `MyAccumulatorSensorTrackingGateTest.kt`, `AccumulatorResetNotificationTest.kt`, `FilterManagerAccumulatorResetTest.kt`, `TrackingViewModelSensorNullSafetyTest.kt`
- **Jira**: `ATT-4926`, `ATT-5080`, `ATT-5191`
- **Test Description**:
  1. *Breadcrumb & Elevation Path Cleansing on Session End (`TST-TRK-049.1`)*:
  • Unit test in `BANALServiceRepositoryTest` verifying that invoking `clearBreadcrumbs()`, receiving `TrackingMode.READY`, and completing a workout empties `_currentTrack` and `_currentPathPoints`.
  2. *Idle Service Disconnect & Unbind Lifecycle (`TST-TRK-049.2`)*:
  • Unit test verifying `MainActivityWithNavigation` and `disconnectFromBANALService()` unbind logic: confirms `bindReferenceCount` drops to 0, `unbindService` is called, and `BANALService` stops without leaking timers.
  3. *Idle Accumulator Gating (`TST-TRK-049.3`)*:
  • Unit test in `MyAccumulatorSensorTrackingGateTest` verifying that in `TrackingMode.READY`, ticks to `MyAccumulatorSensor.increment()` with `mRespectPause = true` do not increment value when `!TrainingApplication.isTracking()`.
  4. *Reset Notification & InstantaneousFilter Reset (`TST-TRK-049.4`)*:
  • Unit test verifying `MyAccumulatorSensor.reset()` notifies listeners with `mZeroValue`, and `InstantaneousFilter.reset()` clears cached values.
  5. *Accumulator Reset Baseline Preservation & Null String Safety (`TST-TRK-049.5`)*:
  • Unit test in `FilterManagerAccumulatorResetTest` verifying that calling `FilterManager.resetAllFilters()` with filters attached directly to `MyAccumulatorSensor` or wrapped via `ProxySensor` causes them to retain/re-seed `getZeroValue()` (e.g. `0` -> formatted `0:00:00`, `1` -> formatted `1`), even when `FilterManager` executes before `MyDevice.reset()`, while non-accumulator filters reset to `null` (`--`).
  • Unit test in `TrackingViewModelSensorNullSafetyTest` verifying that `DefaultStringFormatter.format(null)` returns `"--"`, and `TrackingViewModel.applySensorData()` handles null or empty sensor string values without throwing `NullPointerException`.


### TST-TRK-050: **Indirect Tire Pressure Monitoring (iTPMS) Gated Estimator, CUSUM Detector & Baseline Verification** [Verified]
- **Requirement(s)**: `REQ-TRK-050`
- **Component**: `IndirectTpmsEstimatorTest.kt`, `DevicesDatabaseManagerMigrationTest.kt`, `EditDeviceViewModelTest.kt`, `TranslationParityTest.kt`
- **Jira**: `ATT-4880`
- **Test Description**:
  1. *Gated Circumference Estimator & Straight-Line/3D-Slope Filtering (`TST-TRK-050.1`)*:
  • Unit test feeding synthetic GPS fixes and wheel sensor revolution events:
    - Verifies samples with $|\Delta\text{bearing}/\Delta t| \ge 3.0^\circ/\text{s}$ are rejected from circumference accumulation.
    - Verifies samples with $\text{accuracy} > 6.0\text{ m}$ or $\text{speed} < 4.0\text{ m/s}$ are rejected.
    - Verifies 3D slope correction $\sqrt{\Delta s_{2D}^2 + \Delta h^2}$ matches analytical geometry on 8% and 12% grades.
  2. *Ride-Start Baseline Calibration & Multi-Day Low Pressure Reminder (`TST-TRK-050.2`)*:
  • Unit test verifying:
    - Ride-start baseline $C_{\text{start}}$ locks only after $2.0\text{ km}$ of gated samples have accumulated.
    - When $C_{\text{baseline}} = 2130\text{ mm}$ and $C_{\text{start}} \le 2115\text{ mm}$ ($-0.7\%$), low tire pressure reminder event is emitted.
    - When $C_{\text{start}} = 2125\text{ mm}$ ($-0.2\%$), no reminder is emitted.
  3. *CUSUM Slow Puncture In-Ride Detection & Cornering Immunity (`TST-TRK-050.3`)*:
  • Unit test feeding a gradual tire deflation profile:
    - After $C_{\text{start}}$ locks at $2128\text{ mm}$, rolling window $C_{\text{short}}(t)$ drops to $2105\text{ mm}$ ($-1.08\%$) over $2.5\text{ km}$.
    - Verifies one-sided CUSUM detector triggers slow puncture alert.
    - Verifies a sharp hairpin cornering sequence ($180^\circ$ turn at $8^\circ/\text{s}$) does not corrupt the rolling estimator or trigger a false alarm.
  4. *`Devices.db` v8 Migration, Schema Registry Parity & Baseline Reset Action (`TST-TRK-050.4`)*:
  • Contract unit test verifying:
    - Upgrading `Devices.db` from v7 to v8 executes `ALTER TABLE devices ADD COLUMN baselineCircumference REAL DEFAULT 0.0`.
    - `onCreate()` and `onUpgrade()` yield identical schemas.
    - `DatabaseSchemaRegistry` reflects `DevicesDbHelper.DB_VERSION = 8`.
    - `EditDeviceViewModel.resetTirePressureBaseline(deviceId)` saves current circumference to `baselineCircumference`.
  5. *9-Language Localization Parity Audit (`TST-TRK-050.5`)*:
  • Automated assertion in `TranslationParityTest` verifying all iTPMS strings (`devices_reset_tire_pressure_baseline`, `itpms_slow_puncture_alert_title`, `itpms_slow_puncture_alert_message`, `itpms_low_pressure_reminder_title`, `itpms_low_pressure_reminder_message`) exist across all 9 locales with valid positional specifiers (`%1$s`).


### TST-SEN-026: **Specialized Altitude Devices Naming Parity, Idempotent SQLite Migration & Educational Details Verification** [Verified]
- **Requirement(s)**: `REQ-SEN-026`
- **Component**: `AltitudeFromPressureDeviceTest.kt`, `DevicesDatabaseManagerTest.java`, `EditDeviceDialogTest.kt`, `SpecializedAltitudeDevicesLocalizationTest.kt`, `TranslationParityTest.kt`
- **Jira**: `ATT-5073`, `ATT-5198`
- **Test Description**:
  1. *String Resource Naming Parity Across 9 Locales (`TST-SEN-026.1`)*:
  • Verification that `devices_altitude_from_pressure` resolves to "Höhe aus Luftdruck" (DE), "Altitude from Pressure" (EN), "Altitud por presión" (ES), "Altitude par pression" (FR), "Altitudine da pressione" (IT), "気圧からの高度" (JA), "Hoogte uit luchtdruk" (NL), "Wysokość z ciśnienia" (PL), and "Altitude por pressão" (PT).
  2. *Idempotent SQLite Name Migration (`TST-SEN-026.2`)*:
  • Unit test in `DevicesDatabaseManagerTest` verifying that opening an existing `Devices.db` database containing `ALTITUDE_FROM_PRESSURE` with any legacy default name updates the row to `devices_altitude_from_pressure`, whereas custom user-defined names are strictly preserved without modification.
  3. *Educational Operation Card in EditDeviceDialog (`TST-SEN-026.3`)*:
  • Compose UI test or layout test in `EditDeviceDialogTest` verifying that viewing details for `ALTITUDE_FROM_PRESSURE` or `ALTITUDE_FROM_POSITION` renders the educational info card (`OutlinedCard`) with structured "How it works" and "Pros & Cons" sections, while other sensor types omit this section.
  4. *9-Language Translation Parity for Educational Strings (`TST-SEN-026.4`)*:
  • Verification in `TranslationParityTest` and `SpecializedAltitudeDevicesLocalizationTest` for all altitude explanation string keys (`device_altitude_explanation_title`, `device_altitude_how_it_works_header`, `device_altitude_pros_cons_header`, `device_altitude_pressure_how_it_works`, `device_altitude_pressure_pros_cons`, `device_altitude_position_how_it_works`, `device_altitude_position_pros_cons`) across all 9 supported locales, specifically asserting that `device_altitude_position_pros_cons` explicitly mentions active internet connection requirements for uncached areas.
  5. *Clean-Room Regression Verification (`TST-SEN-026.5`)*:
  • Full regression pass over sensor, device management, and dialog test suites confirming zero regressions in device pairing, sensor data delivery, and calibration workflows.


### TST-TRK-051: **Dynamic SQLite Schema Discovery via PRAGMA table_info & Decoupled Summary Telemetry Test Suite** [Verified]
- **Requirement(s)**: `REQ-TRK-051`
- **Component**: `TrackerServicePragmaSchemaTest.kt`, `TrackerServiceDecoupledSummaryTest.kt`, `TrackOnMapAftermathViewModelFallbackTest.kt`
- **Jira**: `ATT-4968`
- **Test Description**:
  1. *PRAGMA table_info Column Discovery & Staleness Immunity Test (`TST-TRK-051.1`)*:
  • Unit test in `TrackerServicePragmaSchemaTest.kt` verifying that querying table column names via `PRAGMA table_info` correctly identifies all added columns and returns a case-insensitive set containing all dynamically added columns after `ALTER TABLE ... ADD COLUMN` without relying on `query(..., "1")`.
  2. *Decoupled Summary Update on Sample Failure Test (`TST-TRK-051.2`)*:
  • Unit test in `TrackerServiceTest.java` verifying that when a sample insertion fails, `updateWorkoutSummary()` is still invoked with updated duration and distance.
  3. *FastTrack Map Polyline Fallback Retention Test (`TST-TRK-051.3`)*:
  • Unit test in `TrackOnMapAftermathViewModelTest.kt` verifying that when Phase 4 loads sample tracks with `< 2` points, `uiState.tracks` retains the non-empty `fastTrack` from summary `mapPolyline`.
  4. *Clean-Room Targeted Test Suite Execution (`TST-TRK-051.4`)*:
  • Clean-room execution of unit tests ensuring zero regressions (`REQ-PRO-001`).

### TST-CON-023: **Paired BLE Random Static Address Resolution, Scan Assistance & Configurable Search Timeout Test Suite** [Verified]
- **Requirement(s)**: `REQ-CON-023`
- **Component**: `MyBTLEDeviceTest.java`, `SensorSearchPreferencesTest.kt`, `SensorSearchTuningSectionTest.kt`, `SearchSettingsDialogTest.kt`, `TranslationParityTest.kt`
- **Jira**: `ATT-5489`
- **Test Description**:
  1. *Random Static MAC Classification Unit Tests (`TST-CON-023.1`, `MyBTLEDeviceTest.java`)*:
  • Verify that `MyBTLEDevice.isRandomStaticAddress(String)` returns `true` for addresses with bits 47..46 = 11b (e.g. `C6:CC:E3:F5:F0:37` -> 0xC6 = 1100_0110b, `E8:4D:74:07:F4:DD` -> 0xE8 = 1110_1000b, `FE:00:11:22:33:44`, `C0:00:00:00:00:00`).
  • Verify that `isRandomStaticAddress(String)` returns `false` for public addresses (e.g. `00:1A:7D:DA:71:13`, `10:20:30:40:50:60`, `44:55:66:77:88:99`), invalid addresses, or null.
  2. *Scan Assistance Lifecycle & Resource Cleanup Tests (`TST-CON-023.2`, `MyBTLEDeviceTest.java`)*:
  • Verify that `startSearching()` initiates filtered `BluetoothLeScanner.startScan(...)` when `BLUETOOTH_SCAN` permission is granted.
  • Verify that `stopScan()` is unconditionally invoked upon successful connection (`STATE_CONNECTED`), search timeout (`mSearchTimeoutRunnable`), explicit disconnect (`disconnectFromGatt()`), and device teardown (`shutDown()`).
  3. *Configurable Search Timeout Persistence & Plumbing Tests (`TST-CON-023.3`, `SensorSearchPreferencesTest.kt`, `TrainingApplicationTest.java`)*:
  • Verify `SensorSearchPreferences` loads and persists `btleSearchPeriodSec` in `SharedPreferences` with default `5` seconds and boundary clamping $[3, 15]$.
  • Verify `TrainingApplication.getBtleSearchPeriodMs()` returns `btleSearchPeriodSec * 1000L`.
  • Verify `SensorSearchPreferences.reset()` resets `btleSearchPeriodSec` back to `5`.
  4. *Expert Settings & Search Settings Dialog UI Tests (`TST-CON-023.4`, `SensorSearchTuningSectionTest.kt`, `SearchSettingsDialogTest.kt`)*:
  • Verify that `SensorSearchTuningSection` and `SearchSettingsDialog` render the discrete Slider with range $3\text{--}15$, steps 11, and format `"%d s"`.
  5. *9-Language Localization Parity Verification (`TST-CON-023.5`, `TranslationParityTest.kt`)*:
  • Verification in `TranslationParityTest` that `prefs_btle_search_period_title` and `prefs_btle_search_period_summary` exist across all 9 supported locales (`values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`) with 100% parity.
  6. *Targeted Suite Regression Execution (`TST-CON-023.6`)*:
  • Execute targeted unit tests verifying 100% test pass rate across modified packages without regressions.

### TST-TRK-052: **Zero-Allocation 1-Hz ClockTimeTask Time-of-Day Formatting Test Suite** [Verified]
- **Requirement(s)**: `REQ-TRK-052`
- **Component**: `ClockDevice.java`, `ClockDeviceResilienceTest.kt`
- **Jira**: `ATT-5481`
- **Test Description**:
  1. *Time-of-Day Formatting Parity Test (`TST-TRK-052.1`, `ClockDeviceResilienceTest.kt`)*:
  • Verify that `ClockTimeTask.run()` updates `mTimeOfDaySensor` with a formatted time string identical to `SimpleDateFormat.getTimeInstance().format(...)`.
  2. *Zero-Allocation Contract & Source Code Audit Test (`TST-TRK-052.2`, `ClockDeviceResilienceTest.kt`)*:
  • Verify that `ClockDevice.java` contains `private final Date mReusableDate` and `ClockTimeTask.run()` uses `mReusableDate.setTime(...)` without calling `Calendar.getInstance()`.
  3. *Clean-Room Regression Suite Execution (`TST-TRK-052.3`)*:
  • Run targeted package tests (`com.atrainingtracker.banalservice.*`, `com.atrainingtracker.trainingtracker.tracker.*`, `build.*`) asserting 100% pass rate.

### TST-SEN-027: **Obsolete Network Location Excision & Devices.db Version 10 Migration Test Suite** [Verified]
- **Requirement(s)**: `REQ-SEN-027`
- **Component**: `DeviceType.java`, `SpeedAndLocationDevice_Network.java` (deleted), `DevicesDatabaseManager.java`, `MySensorManager.java`, `DatabaseSchemaRegistry.kt`, `DeviceData.kt`
- **Jira**: `ATT-5492`
- **Test Description**:
  1. *DeviceType Enum Declaration & Excision Contract Test (`TST-SEN-027.1`)*:
  • Verify `SpeedAndLocationDevice_Network.java` does not exist on disk.
  • Verify `DeviceType.values()` does not contain `SPEED_AND_LOCATION_NETWORK`.
  • Verify `DeviceTypeEnumOrderTest.kt` asserts `SPEED_AND_LOCATION_GPS.ordinal < SPEED_AND_LOCATION_GOOGLE_FUSED.ordinal`.
  2. *Devices Database Version 10 Migration & Cleanup Test (`TST-SEN-027.2`)*:
  • Verify in `DevicesDatabaseManagerTest.kt` that upgrading a v9 database containing a `SPEED_AND_LOCATION_NETWORK` entry removes that row completely upon migration to v10.
  • Verify fresh install (`addLocationDevicesToDb`) inserts only `SPEED_AND_LOCATION_GPS` and `SPEED_AND_LOCATION_GOOGLE_FUSED`.
  • Verify `DatabaseSchemaRegistry` asserts `DevicesDbHelper.DB_VERSION == 10`.
  3. *Priority Hierarchy Array Length & Rank Test (`TST-SEN-027.3`)*:
  • Verify `MySensorManager.SPEED_PRIORITY` has length 6, `ALTITUDE_PRIORITY` has length 4, and `LOCATION_PRIORITY` has length 2.
  • Verify `getPriorityRank` returns ranks 0 for `GOOGLE_FUSED` and 1 for `GPS` across location sensors.
  4. *9-Language Clean Removal Audit Test (`TST-SEN-027.4`)*:
  • Verify `TranslationParityTest` passes without `devices_location_Network` in `strings_devices.xml` across all 9 locales.
  5. *Clean-Room Targeted Test Suite Execution (`TST-SEN-027.5`)*:
  • Execute targeted unit tests verifying 100% pass rate.

### TST-TRK-053: **Persisted Deduplicated Workout Diagnostic Errors & Actionable Report Dialog Test Suite** [Verified]
- **Requirement(s)**: `REQ-TRK-053`
- **Component**: `TrackerService.java`, `WorkoutSummariesDatabaseManager.java`, `WorkoutDataMapper.kt`, `WorkoutRepository.kt`, `WorkoutDetailTabbedContent.kt`, `WorkoutDiagnosticBottomSheet.kt`
- **Jira**: `ATT-5886`
- **Test Description**:
  1. *WorkoutSummaries Database Schema Version 28 Migration & Helper Contract Test (`TST-TRK-053.1`, `WorkoutSummariesRealSqliteMigrationTest.kt`, `WorkoutSummariesDatabaseManagerTest.kt`)*:
  • Verify that `WorkoutSummariesDbHelper.DB_VERSION` equals `28`.
  • Verify that migrating an existing SQLite database from version 27 to 28 adds nullable `RECORDING_ERRORS` text column without data loss.
  • Verify that `clearRecordingErrors(workoutId)` sets `RECORDING_ERRORS` to `NULL`.
  • Verify that `DatabaseSchemaRegistry` validates schema version 28.
  2. *TrackerService Exception Collector & 1-Hz Deduplication Unit Tests (`TST-TRK-053.2`, `WorkoutDiagnosticCollectorTest.kt`)*:
  • Verify that 100 repeated sample insert exceptions produce a single deduplicated entry with `count = 100` and updated last-occurrence timestamp.
  • Verify that distinct exceptions across `INIT`, `SAMPLE_WRITE`, and `FINALIZE` stages are preserved independently.
  • Verify that payload serialization enforces the 8 KB size cap and does not throw on null/empty inputs.
  3. *WorkoutData & WorkoutDataMapper Symmetry Contract Test (`TST-TRK-053.3`, `WorkoutDataMapperTest.kt`)*:
  • Verify `Rule 20` mapping symmetry: `fromCursor(Cursor)` and `readCursorSnapshot(Cursor)` -> `fromSnapshot(...)` populate identical `recordingErrors` strings for identical cursor rows.
  4. *Diagnostic Dialog Plain-Language & Action Flow UI Tests (`TST-TRK-053.4`, `WorkoutDiagnosticBottomSheetTest.kt`)*:
  • Verify that `recordingErrors == null` displays zero warning banners or icons.
  • Verify that non-null `recordingErrors` containing `SQLiteFullException` renders `R.string.workout_diagnostic_explanation_storage_full`.
  • Verify that non-null `recordingErrors` containing `SAMPLE_WRITE` renders `R.string.workout_diagnostic_explanation_sample_write`.
  • Verify that non-null `recordingErrors` containing only `FINALIZE` renders `R.string.workout_diagnostic_explanation_finalize`.
  • Verify that tapping the email action creates a `mailto:rainer.blind@gmail.com` intent with app version, device model, and error details.
  • Verify that confirming "Warnung löschen" invokes `repository.clearRecordingErrors(...)`.
  5. *9-Language Localization Parity Verification (`TST-TRK-053.5`, `TranslationParityTest.kt`)*:
  • Verify in `TranslationParityTest` that all 15 diagnostic string resources exist with 100% parity across all 9 locales without decorative emojis (`ATT-5400`) or missing placeholders.
  6. *Targeted & Full Suite Regression Execution (`TST-TRK-053.6`)*:
  • Execute targeted package tests (`com.atrainingtracker.trainingtracker.database.*`, `com.atrainingtracker.trainingtracker.tracker.*`, `com.atrainingtracker.trainingtracker.ui.aftermath.*`) and full test suite asserting 100% pass rate.

### TST-TRK-054: **Hardware Volume Keys Cockpit Tab Cycling with Cyclic Wrap-Around Test Suite** [Verified]
- **Requirement(s)**: `REQ-TRK-054`
- **Component**: `TrackingTabsPageResolverTest.kt`, `DisplaySettingsTest.kt`, `MainActivityWithNavigation.kt`, `TrackingTabsScreen.kt`, `TranslationParityTest.kt`
- **Jira**: `ATT-5905`
- **Test Description**:
  1. *Cyclic Wrap-Around Page Index Calculation Unit Tests (`TST-TRK-054.1`, `TrackingTabsPageResolverTest.kt`)*:
  • Verify `resolveNextPage(currentPage, pageCount, forward = true)` advances sequentially from 0 to `pageCount - 1` and wraps around from `pageCount - 1` to 0.
  • Verify `resolveNextPage(currentPage, pageCount, forward = false)` decrements sequentially and wraps around from 0 to `pageCount - 1`.
  • Verify boundary behavior for `pageCount = 1` (returns 0) and `pageCount <= 0` (returns 0) without exceptions.
  2. *Display Settings & Persistence Unit Tests (`TST-TRK-054.2`, `DisplaySettingsTest.kt`)*:
  • Verify `TrainingApplication.DEFAULT_DISPLAY_OPTIONS` contains `"volumeKeysSwitchCockpitTabs"`.
  • Verify `TrainingApplication.volumeKeysSwitchCockpitTabs()` returns `true` by default and reflects mutations via `setDisplayOptions`.
  3. *Window Key Interception & Lifecycle Guard Unit/Source Tests (`TST-TRK-054.3`)*:
  • Verify `MainActivityWithNavigation` provides a volume key interceptor hook consuming both `ACTION_DOWN` and `ACTION_UP` for `KEYCODE_VOLUME_UP` and `KEYCODE_VOLUME_DOWN`.
  • Verify `TrackingTabsScreen` registers the interceptor only when `screenMode == ScreenMode.TRACKING` and setting is enabled, releasing on dispose.
  4. *9-Language Localization Parity Verification (`TST-TRK-054.4`, `TranslationParityTest.kt`)*:
  • Verify `TranslationParityTest` passes asserting `prefs_volume_keys_switch_cockpit_tabs_title` and `prefs_volume_keys_switch_cockpit_tabs_summary` exist with 100% parity across all 9 locales.
  5. *Targeted Regression Suite Execution (`TST-TRK-054.5`)*:
  • Execute targeted package tests (`com.atrainingtracker.trainingtracker.ui.tracking.trackingtabs.*`, `com.atrainingtracker.trainingtracker.ui.settings.display.*`) asserting 100% pass rate.

### TST-TRK-055: **Persistent Sensor Proxy Binding & Filter Re-Connection Verification Test Suite** [Verified]
- **Requirement(s)**: `REQ-TRK-055`
- **Component**: `FilterManagerRebindTest.kt`, `MySensorManagerProxyTest.kt`, `BANALServiceLifecycleTest.kt`
- **Jira**: `ATT-6257`
- **Test Description**:
  1. *Persistent ProxySensor Retention & Reconnection Unit Test (`TST-TRK-055.1`, `MySensorManagerProxyTest.kt`)*:
  • Register a source sensor on `MySensorManager` and verify a `ProxySensor` is created.
  • Unregister the source sensor and verify `proxy.getSourceSensor() == null`, but the `ProxySensor` instance in `getSensor(sensorType)` is retained.
  • Register a new source sensor and verify the identical `ProxySensor` instance wraps the new sensor with listeners preserved.
  2. *FilterManager Dynamic Re-Binding Unit Test (`TST-TRK-055.2`, `FilterManagerRebindTest.kt`)*:
  • Create a filter in `FilterManager` bound to a sensor.
  • Replace or update the backing sensor instance and dispatch `createFilter()` or `SENSORS_CHANGED`.
  • Verify the filter unregisters from the old sensor, registers on the new sensor, and delivers new telemetry samples.
  3. *Named Peripheral Device Re-Creation Re-Binding Test (`TST-TRK-055.3`, `FilterManagerRebindTest.kt`)*:
  • Create a device-specific filter for `deviceName != null`.
  • Simulate device recreation with a new `MySensor` instance and verify filter re-binds on `SENSORS_CHANGED`.
  4. *BANALService Static Lifecycle Cleanup & Restart Test (`TST-TRK-055.4`, `BANALServiceLifecycleTest.kt`)*:
  • Verify `BANALService.onDestroy()` resets static manager references and `DeviceManager.shutDown()` clears device instances.
  5. *Targeted Regression Suite Execution (`TST-TRK-055.5`)*:
  • Execute targeted package tests (`com.atrainingtracker.banalservice.filters.*`, `com.atrainingtracker.banalservice.sensor.*`, `com.atrainingtracker.banalservice.devices.*`) asserting 100% pass rate.

### TST-TRK-056: **Google Fused Doppler Velocity Plausibility & Stationary Pinning Prevention Test Suite** [Verified]
- **Requirement(s)**: `REQ-TRK-056`
- **Component**: `SpeedAndLocationDevice.java`, `SpeedAndLocationDeviceStationaryJitterTest.kt`
- **Jira**: `ATT-6256`
- **Test Description**:
  1. *Continuous Movement with Zero-Speed Provider Unit Test (`TST-TRK-056.1`, `SpeedAndLocationDeviceStationaryJitterTest.kt`)*:
  • Deliver continuous 1-Hz location fixes advancing 8 m/s (accuracy = 3.0 m, escapeRadius = 4.5 m) with `hasSpeed() == true` and `getSpeed() == 0.0f`.
  • Verify speed never toggles to 0.0 m/s across successive fixes ($t=1, 2, 3\dots$), distance accumulates steadily (~8 m per fix), and no stationary anchor is pinned.
  2. *Damped Doppler Speed Fallback Unit Test (`TST-TRK-056.2`, `SpeedAndLocationDeviceStationaryJitterTest.kt`)*:
  • Deliver continuous 1-Hz location fixes advancing 8 m/s with `hasSpeed() == true` and `getSpeed() == 0.15f` (damped Google Fused sub-case).
  • Verify geometric fallback activates, reporting ~8 m/s instead of 0.15 m/s, and distance accumulates steadily.
  3. *Genuine Standstill Pinning & Unpinning Multi-Cycle Test (`TST-TRK-056.3`, `SpeedAndLocationDeviceStationaryJitterTest.kt`)*:
  • Deliver jitter fixes within 1.5 m accuracy deadband with Doppler zero speed; verify anchor pins immediately with `speed = 0.0 m/s` and zero distance creep (`REQ-TRK-048`).
  • Advance position past escape radius; verify anchor unpins smoothly and resumes movement without re-pinning on subsequent zero/damped fixes.
  4. *Teleportation Spike Guard Continuity Test (`TST-TRK-056.4`, `SpeedAndLocationDeviceStationaryJitterTest.kt`)*:
  • Verify sudden large position jumps (> 70 m/s = 252 km/h) are clamped with `delta_distance = 0.0` preserving `REQ-TRK-041.1`.
  5. *Clean-Room Targeted Test Suite Execution (`TST-TRK-056.5`)*:
  • Execute targeted package tests (`com.atrainingtracker.banalservice.devices.*`) asserting 100% pass rate.





