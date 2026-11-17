# Living Test Specification: Engineering Process, Stability & Architecture

**Domain**: `process_stability`  
**Prefixes**: `ARC`, `BLD`, `BUG`, `INT`, `LIV`, `LOG`, `MAN`, `NFR`, `PRO`, `REG`, `SEC`, `STB`, `STR`, `TOOL`, `TST`  
**Scope**: ASPICE governance, infinite context, crash stability, build architecture, security, and developer tools.  
**Total Tests**: 217

---

### TST-REG-001: **Tracking** [Implemented]
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Integration
- **Requirement(s)**: `REQ-TRK-001`, `REQ-TRK-003`, `REQ-TRK-007`, `REQ-MAP-003`
- **Component**: **Tracking**
- **Test Description**: Start workout, background app for 10 min, check bearing jitter.

### TST-REG-002: **Branding** [Implemented]
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Integration
- **Requirement(s)**: `REQ-EXT-001`, `REQ-EXT-002`, `REQ-EXT-003`, `REQ-MAP-005`
- **Component**: **Branding**
- **Test Description**: Navigate to Strava Segments list and Detail map.

### TST-REG-003: **Cloud** [Implemented]
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Integration
- **Requirement(s)**: `REQ-EXP-001`, `REQ-EXP-002`
- **Component**: **Cloud**
- **Test Description**: Complete workout with Strava enabled.

### TST-REG-004: **Pause Logic** [Implemented]
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Integration
- **Requirement(s)**: `REQ-PRO-003`
- **Component**: **Pause Logic**
- **Test Description**: Connect hardware distance sensor. Start workout. Record distance. **Pause**. Continue movement (e.g., spin wheel). **Resume**. Record more distance. Repeat for all relevant protocols (ANT+/BTLE).

### TST-INT-002: **UI/Logic** [Implemented]
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Integration
- **Requirement(s)**: `REQ-EXP-006`
- **Component**: **UI/Logic**
- **Test Description**: In Sport Type Editor, select "- No upload -". Then create/edit workout with this type.

### TST-INT-003: **Mapping** [Implemented]
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Integration
- **Requirement(s)**: `REQ-EXP-007`
- **Component**: **Mapping**
- **Test Description**: In Sport Type Editor, select a new Strava type (e.g., Gravel Ride). Save and reopen.

### TST-INT-004: **UI/Logic** [Implemented]
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Integration
- **Requirement(s)**: `REQ-UI-110`
- **Component**: **UI/Logic**
- **Test Description**: In Tracking Tab Configuration, toggle "Show Elevation Profile".

### TST-INT-005: **UI Layout** [Implemented]
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Integration
- **Requirement(s)**: `REQ-UI-111`
- **Component**: **UI Layout**
- **Test Description**: Open the Tab Configuration on a narrow device or split-screen.

### TST-INT-006: **UI Layout** [Implemented]
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Integration
- **Requirement(s)**: `REQ-UI-111`
- **Component**: **UI Layout**
- **Test Description**: Audit the checkbox sequence in the tab configuration header.

### TST-REG-005: **Map Context** [Implemented]
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Integration
- **Requirement(s)**: `REQ-MAP-008`
- **Component**: **Map Context**
- **Test Description**: Open detail map. Verify active item is primary. Verify background items of same sport are muted (0.3). Verify background items of different sport are highly muted (0.1).

### TST-INT-007: **Original Sport Icons Visual Identity Verification** [Implemented]
- **Priority**: High
- **Type**: Functional
- **Verification Method**: Manual / Review
- **Requirement(s)**: `REQ-UI-114`
- **Component**: **ActivityTypeSelectionDialog, SportTypeListFragment**
- **Test Description**:
  1. Open sport type selection dialog and sport type list screens.
  2. Verify that sport type icons render in their authentic original colors and are not subject to theme-based tinting in active states.
  3. Verify muted/disabled state icons render with appropriate transparency.

### TST-INT-008: **Settings Cleanup Verification** [Implemented]
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Integration
- **Requirement(s)**: `REQ-SET-055`
- **Component**: **Settings Cleanup Verification**
- **Jira**: ``ATT-268``
- **Test Description**: 1. Gradle Build: Ensure the project compiles successfully after reorganization and deletions. 2. Navigation Test: Verify all settings items in the drawer (Units, Display, Search, Export, Strava, Dropbox, Tracking Tabs) still function correctly.

### TST-INT-009: **INT-SWE-01 Presentation Layer UI to ViewModel MVI Verification** [Verified]
- **Priority**: High
- **Type**: Functional
- **Verification Method**: Integration
- **Requirement(s)**: `REQ-PRO-110`
- **Component**: `ui/tracking/controltracking/ControlTrackingButton.kt`, `ui/tracking/controltracking/ControlTrackingViewModel.kt`, `ui/tracking/tracking/TrackingViewModel.kt`, `ui/tracking/trackingtabs/TrackingTabsViewModel.kt`
- **Jira**: `ATT-8155`
- **Test Description**:
  1. Initialize real `ControlTrackingViewModel`, `TrackingTabsViewModel`, and `TrackingViewModel` in Robolectric Compose test harness (`createComposeRule()`).
  2. Compose `ControlTrackingButton` and Compose observers bound to `TrackingViewModel.uiState` / `screenMode` and `TrackingTabsViewModel.screenMode` / coachmark StateFlows.
  3. Dispatch UI actions (`onStartTracking()`, `onPauseTracking()`, `onResumeTracking()`, `onStopTracking()`, `updateScreenMode()`, `setScreenMode()`), invoke `waitForIdle()`, and assert Compose UI nodes recompose with `assertTextEquals()` and broadcast dispatch.

### TST-INT-010: **INT-SWE-02 ViewModel to Repository Data Layer Verification** [Verified]
- **Priority**: High
- **Type**: Functional
- **Verification Method**: Integration
- **Requirement(s)**: `REQ-PRO-110`
- **Component**: `ui/aftermath/WorkoutRepository.kt`, `ui/aftermath/workoutlist/WorkoutSummariesViewModel.kt`, `repositories/BANALServiceRepository.kt`, `database/WorkoutSummariesDatabaseManager.java`
- **Jira**: `ATT-8155`
- **Test Description**:
  1. Instantiate `WorkoutRepository` and `WorkoutSummariesViewModel` with real Robolectric SQLite `WorkoutSummariesDatabaseManager` (verified via `RealSqliteTestHelper`).
  2. Collect reactive `Flow` emissions via `getWorkoutById()`, `WorkoutSummariesViewModel.allWorkouts`, and `BANALServiceRepository.allFilteredSensorData`.
  3. Insert and update real SQLite workout summary rows; assert reactive emissions deliver updated `WorkoutData` records without main-thread blocking.

### TST-INT-011: **INT-SWE-03 Inter-Service Ingestion Binder IPC Verification** [Verified]
- **Priority**: High
- **Type**: Functional
- **Verification Method**: Integration
- **Requirement(s)**: `REQ-PRO-110`
- **Component**: `tracker/TrackerService.java`, `banalservice/BANALService.java`, `banalservice/sensor/MySensorManager.java`, `database/WorkoutSamplesDatabaseManager.java`, `database/WorkoutSummariesDatabaseManager.java`
- **Jira**: `ATT-8155`
- **Test Description**:
  1. Wire `TrackerService` to real `BANALService.BANALServiceComm` local binder backed by real `MySensorManager`.
  2. Execute 1-Hz batch poll (`sampleAndWriteToDb()`) polling `getBestSensorData()` and `getAllSensorData()`, verifying `LiveWorkoutSession` stats and real SQLite sample/summary row writes.
  3. Verify sensor priority arbitration (`BIKE_SPEED` > `SPEED_AND_LOCATION_GPS`) and verify null-safety on `getBanalConnectionForTesting().onServiceDisconnected()` and `sampleAndWriteToDb(null)`.

### TST-INT-012: **INT-SWE-04 Hardware HAL Byte Stream Ingestion & Sensor Replay Verification** [Verified]
- **Priority**: High
- **Type**: Functional
- **Verification Method**: Integration
- **Requirement(s)**: `REQ-PRO-110`
- **Component**: `banalservice/devices/bluetooth_le/BTLEHeartRateDevice.java`, `banalservice/devices/bluetooth_le/BTLEBikePowerDevice.java`, `banalservice/devices/bluetooth_le/BTLERadarDevice.java`, `banalservice/devices/radar/RadarStateRepository.kt`, `banalservice/devices/SpeedAndLocationDevice_GPS.java`, `banalservice/sensor/MySensorManager.java`
- **Jira**: `ATT-8155`
- **Test Description**:
  1. Inject raw GATT characteristic byte arrays for Heart Rate (`0x2A37`), Cycling Power (`0x2A63`), and Radar (`6a4e3204` V1 & V2 `parseV2ThreatPacket`), plus `Location` fixes via `SpeedAndLocationDevice_GPS.onNewLocation()`.
  2. Assert binary parsing into `MySensorManager` sensors, `RrBeatEvent` callbacks, and `RadarStateRepository` targets.
  3. Assert real `BTLERadarDevice` watchdog timeout (`WATCHDOG_TIMEOUT_MS` = 3,500 ms via `getWatchdogRunnableForTesting()`) transitions `RadarStateRepository` to `DISCONNECTED_ERROR`.

### TST-INT-013: **INT-SWE-05 Domain Calculators & Algorithms Integration Verification** [Verified]
- **Priority**: High
- **Type**: Functional
- **Verification Method**: Integration
- **Requirement(s)**: `REQ-PRO-111`
- **Component**: `tracker/autopause/AutoPauseManager.kt`, `physiology/PowerPhysiologyCalculator.kt`, `physiology/PowerHrDynamicsCalculator.kt`, `database/WorkoutClusterEngine.kt`, `climbs/ClimbDetector.kt`
- **Jira**: `ATT-8156`
- **Test Description**:
  1. Execute real `AutoPauseManager.evaluateTick()`, `PowerPhysiologyCalculator.calculateNormalizedPower()`, `PowerHrDynamicsCalculator.calculate()`, `WorkoutClusterEngine.suggestCluster()`, and `ClimbDetector.detectClimbs()` using synthetic multi-sensor time-series and elevation profiles.
  2. Assert deterministic NP/TSS, cardiac drift, cluster apex, climb detection, and auto-pause debounce transitions without primitive constant stubs.

### TST-INT-014: **INT-SWE-06 Persistence SQLite Foundation Integration Verification** [Verified]
- **Priority**: High
- **Type**: Functional
- **Verification Method**: Integration
- **Requirement(s)**: `REQ-PRO-111`
- **Component**: `database/WorkoutSamplesDatabaseManager.java`, `database/WorkoutSummariesDatabaseManager.java`, `database/KnownLocationsDatabaseManager.java`, `database/LapsDatabaseManager.java`, `repositories/KnownLocationsRepository.kt`
- **Jira**: `ATT-8156`
- **Test Description**:
  1. Exercise real Robolectric SQLite databases (`WorkoutSamplesDatabaseManager`, `WorkoutSummariesDatabaseManager`, `KnownLocationsDatabaseManager`, `LapsDatabaseManager`) and `KnownLocationsRepository.recordWorkoutStart()`.
  2. Assert WAL concurrency, dynamic `createNewTable` sample persistence, and non-destructive `onUpgrade` schema parity (`v1 -> v6`) via `RealSqliteTestHelper`.

### TST-INT-015: **INT-SWE-07 Repository/ExportManager to File Exporters Integration Verification** [Verified]
- **Priority**: High
- **Type**: Functional
- **Verification Method**: Integration
- **Requirement(s)**: `REQ-PRO-111`
- **Component**: `exporter/ExportManager.java`, `exporter/writer/FitFileWriter.kt`, `exporter/writer/GPXFileWriter.java`, `exporter/writer/TCXFileWriter.kt`, `exporter/db/ExportStatusDatabaseManager.java`
- **Jira**: `ATT-8156`
- **Test Description**:
  1. Seed real SQLite workout/sample/lap records and invoke `FitFileWriter`, `GPXFileWriter`, and `TCXFileWriter` via `ExportManager` into temporary storage.
  2. Assert valid FIT/GPX/TCX byte/XML output, zero leftover `.tmp` artifacts after write, and authentic `ExportStatusDatabaseManager` status persistence.

### TST-INT-016: **INT-SWE-08 Cloud APIs & Health Connect Integration Verification** [Verified]
- **Priority**: High
- **Type**: Functional
- **Verification Method**: Integration
- **Requirement(s)**: `REQ-PRO-111`
- **Component**: `exporter/uploader/StravaUploader.kt`, `exporter/uploader/DropboxUploader.java`, `cloud/healthconnect/HealthConnectManager.kt`, `cloud/healthconnect/HealthConnectHrvImporter.kt`, `database/hrv/HrvReadinessRepository.kt`
- **Jira**: `ATT-8156`
- **Test Description**:
  1. Wire real `StravaUploader` to an authentic `OkHttpClient` `Interceptor` transport double and real `ExportStatusRepository` / `StravaUploadDbHelper`, verifying bearer-token multipart upload + polling + activity PUT update, HTTP 503 retry classification, and `DropboxUploader` missing-credential guard.
  2. Wire real `HealthConnectManager` and `HealthConnectHrvImporter` to an authentic `HealthConnectClient` test double and real `ExportStatusDatabaseManager` / `HrvReadinessRepository`, verifying idempotent workout sync and nocturnal HRV ingestion.

### TST-PRO-001: **Jira Attachment Download** [Verified]
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Requirement(s)**: `REQ-PRO-008`
- **Component**: **Jira Attachment Download**
- **Jira**: ``ATT-273``
- **Test Description**: 1. Identify a Jira ticket with attachments. 2. Run `jira_util.py download-all KEY`.

### TST-PRO-002: **Jira Type Visibility** [Verified]
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Requirement(s)**: `REQ-PRO-009`
- **Component**: **Jira Type Visibility**
- **Jira**: ``ATT-274``
- **Test Description**: 1. Run `jira_util.py list`. 2. Run `jira_util.py show KEY`.

### TST-PRO-003: **Plan Sub-Task Verification** [Implemented]
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Requirement(s)**: `REQ-PRO-010`
- **Component**: **Plan Sub-Task Verification**
- **Jira**: ``ATT-327``
- **Test Description**: 1. Identify a task requires a plan. 2. Verify that the agent creates a Jira sub-task with summary `[Plan] SCRUM-XXX: Summary` and description matching the plan.

### TST-REG-007: **Pause Time Sync** [Implemented]
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Integration
- **Requirement(s)**: `REQ-TRK-009`, `REQ-UI-038`
- **Component**: **Pause Time Sync**
- **Test Description**: 1. Start workout. 2. Pause after 10s. 3. Wait 20s while paused.

### TST-REG-008: **Dropbox Auth** [Implemented]
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Integration
- **Requirement(s)**: `REQ-EXP-002`
- **Component**: **Dropbox Auth**
- **Jira**: ``ATT-257``
- **Test Description**: 1. Navigate to 'Dropbox' in Ecosystem. 2. Tap 'Connect to Dropbox'. 3. Complete OAuth flow in browser. 4. Return to app.

### TST-LOG-001: **Logic** [Implemented]
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Requirement(s)**: `REQ-UI-011`
- **Component**: **Logic**
- **Test Description**: Verify that all rows, including "Pace", follow strict mathematical ordering (numerical minimum in Min column, numerical maximum in Max column).

### TST-LOG-002: **Logic** [Implemented]
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Requirement(s)**: `REQ-UI-011`
- **Component**: **Logic**
- **Test Description**: Verify that for a running activity, both Speed and Pace rows are generated from the single Speed data source.

### TST-MAN-001: **General** [Implemented]
- **Priority**: Low
- **Type**: Functional
- **Verification Method**: On-Device Manual
- **Requirement(s)**: `REQ-DAT-002`, `REQ-DAT-003`, `REQ-EXP-003`, `REQ-MAP-002`, `REQ-LIV-001`, `REQ-LIV-002`, `REQ-LIV-003`, `REQ-LIV-004`, `REQ-PRO-001`, `REQ-PRI-002`, `REQ-CON-008`, `REQ-CON-009`, `REQ-TRK-004`, `REQ-TRK-006`, `REQ-WEA-001`, `REQ-UI-101`, `REQ-UI-102`, `REQ-UI-103`, `REQ-UI-104`, `REQ-UI-105`, `REQ-UI-106`, `REQ-UI-107`, `REQ-UI-108`
- **Component**: **General**
- **Test Description**: Ad-hoc functional testing on a physical device.

### TST-PRO-004: **Decoupled Interaction Queue** [Implemented]
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Requirement(s)**: `REQ-MIG-014`
- **Component**: **Decoupled Interaction Queue**
- **Jira**: ``ATT-333``
- **Test Description**: 1. Start a bulk TCX import that triggers multiple cluster naming candidates. 2. Verify that while the ClusterNamingDialog is shown, the background progress indicator remains visible. 3. Complete the first naming task.

### TST-BUG-006: **Adaptive Header Framing** [Implemented]
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Requirement(s)**: `REQ-UI-117`
- **Component**: **Adaptive Header Framing**
- **Jira**: ``ATT-397``
- **Test Description**: 1. Open the app on a device with a large status bar (e.g. Pixel 10). 2. Navigate to Workouts, Periods, and My Locations.

### TST-BUG-007: **Internal Altitude Source Visibility** [Implemented]
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Requirement(s)**: `REQ-UI-118`
- **Component**: **Internal Altitude Source Visibility**
- **Jira**: ``ATT-410``
- **Test Description**: 1. Open tracking screen on device with pressure sensor. 2. Tap Altitude icon.

### TST-BUG-008: **Self-Healing Pressure Sensor Registration** [Implemented]
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Requirement(s)**: `REQ-DAT-006`
- **Component**: **Self-Healing Pressure Sensor Registration**
- **Jira**: ``ATT-414``
- **Test Description**: 1. Use Database Inspector to delete the 'Altitude from Pressure' entry from the `Devices` table. 2. Restart the app on a device with a pressure sensor. 3. Navigate to 'My Sensors'.

### TST-BUG-009: **Internal Sensor Pairing Control** [Implemented]
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Requirement(s)**: `REQ-CON-010`
- **Component**: **Internal Sensor Pairing Control**
- **Jira**: ``ATT-416``
- **Test Description**: 1. Navigate to 'My Sensors'. 2. Deactivate the 'Altitude from Pressure' sensor using the toggle switch. 3. Verify that the sensor status LED turns grey and real-time altitude updates from this source stop. 4. Reactivate and verify resumption.

### TST-BUG-011: **Eager Pressure Sensor Visibility** [Implemented]
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Requirement(s)**: `REQ-DAT-006`
- **Component**: **Eager Pressure Sensor Visibility**
- **Jira**: ``ATT-420``
- **Test Description**: 1. Use Database Inspector to delete the 'Altitude from Pressure' entry from the `Devices` table. 2. Restart the app on a device with a pressure sensor. 3. Navigate to 'My Sensors'.

### TST-BUG-010: **Internal Sensor Iconography** [Implemented]
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Requirement(s)**: `REQ-UI-119`
- **Component**: **Internal Sensor Iconography**
- **Test Description**: 1. Open 'My Sensors'. 2. Inspect the 'Altitude from Pressure' entry.

### TST-STR-001: **Stability** [Implemented]
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit
- **Requirement(s)**: `REQ-CON-005`, `REQ-TRK-005`, `REQ-DAT-005`, `REQ-EXP-005`, `REQ-UI-109`
- **Component**: **Stability**
- **Test Description**: Static analysis and architectural audit.

### TST-STR-002: **Thread Safety** [Implemented]
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit
- **Requirement(s)**: `REQ-PRO-002`
- **Component**: **Thread Safety**
- **Test Description**: Static analysis of `synchronized` blocks to ensure no nested locks or I/O within locks.

### TST-STR-003: **Data Integrity** [Implemented]
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit
- **Requirement(s)**: `REQ-UI-110`
- **Component**: **Data Integrity**
- **Test Description**:
  1. Static analysis of `onUpgrade` (v9->v10).
  2. Static analysis of `onCreate` and `addDefaultTab`.

### TST-STR-004: **Consistency** [Implemented]
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit
- **Requirement(s)**: `REQ-TRK-008`
- **Component**: **Consistency**
- **Test Description**: Static analysis of `TrackerService.java`.

### TST-STR-005: **Map DSL** [Implemented]
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit
- **Requirement(s)**: `REQ-MAP-006`
- **Component**: **Map DSL**
- **Test Description**: Static audit of `ATrainingTrackerMap` callers.

### TST-STR-006: **Metric Consistency** [Implemented]
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit
- **Requirement(s)**: `REQ-UI-015`
- **Component**: **Metric Consistency**
- **Test Description**: Visual audit of Workout, Route, and Segment screens.

### TST-STR-007: **Formatter Sync** [Implemented]
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit
- **Requirement(s)**: `REQ-UI-016`
- **Component**: **Formatter Sync**
- **Test Description**: Static audit of `LocalMetricFormatter` usages.

### TST-STR-008: **List Foundation** [Implemented]
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit
- **Requirement(s)**: `REQ-UI-017`
- **Component**: **List Foundation**
- **Test Description**: Audit of `MappableListItem` usages.

### TST-STR-011: **Thread-Safe Sensor Management** [Implemented]
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit
- **Requirement(s)**: `REQ-PRO-006`
- **Component**: **Thread-Safe Sensor Management**
- **Test Description**: Static audit of `MySensorManager` and `MyDevice`.

### TST-STR-012: **Sub-Task Automation Verification** [Implemented]
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit
- **Requirement(s)**: `REQ-PRO-007`
- **Component**: **Sub-Task Automation Verification**
- **Test Description**: 1. Define a requirement and a test case in the docs. 2. The agent should be able to create a sub-task in Jira linked to the main ticket with the test ID and summary. 3. Verify the sub-task is created with the correct parent.

### TST-STR-013: **String Resource Integrity Check** [Verified]
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit
- **Requirement(s)**: `REQ-UI-113`
- **Component**: **String Resource Integrity Check**
- **Jira**: ``ATT-251``
- **Test Description**: 1. Audit all `values-*/strings.xml` files. 2. Compare the set of keys in each file with the keys in `values/strings.xml`. 3. Identify keys present in translations but missing in default. 4. Verify that after implementation, no such discrepancies exist.

### TST-STR-015: **Documentation Audit** [Implemented]
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit
- **Requirement(s)**: `REQ-PRO-011`
- **Component**: **Documentation Audit**
- **Jira**: ``ATT-486``
- **Test Description**: 1. Perform a static audit of the modified source files (e.g., `WorkoutRepository.kt`). 2. Verify that every class has a header describing its purpose. 3. Verify that every method has a header describing what it does and how.

### TST-STR-014: **Missing Translation Audit** [Verified]
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit
- **Requirement(s)**: `REQ-UI-106`
- **Component**: **Missing Translation Audit**
- **Jira**: ``ATT-484``
- **Test Description**: 1. Run a script to identify all translatable keys in default `values/` that are missing in any supported locale (DE, ES, FR, IT, JA, NL, PL, PT). 2. Verify that after implementation, zero translatable keys from the default locale are missing in any supported sub-locale. 3. Verify that specialized resource files (e.g., `strings_devices.xml`, `strings_filters.xml`) are also consistent across locales.

### TST-STR-016: **Global Format String Audit** [Verified]
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit
- **Requirement(s)**: `REQ-UI-122`
- **Component**: **Global Format String Audit**
- **Jira**: ``ATT-523``
- **Test Description**: 1. Execute a comprehensive search across all `strings.xml` files in all locales (DE, ES, FR, IT, JA, NL, PL, PT) for positional format specifiers that are incomplete (e.g., `%1` without `$s`, `$d`, or `$f`). 2. Correct any identified issues. 3. Verify that the final resource set contains only syntactically valid `java.util.Formatter` specifiers.

### TST-BUG-001: **Cluster Selection Race Condition** [Implemented]
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Requirement(s)**: `REQ-PER-002`
- **Component**: **Cluster Selection Race Condition**
- **Jira**: ``ATT-364``
- **Test Description**: 1. Open Workout Cluster list. 2. Tap Cluster A. 3. Immediately go back and tap Cluster B. 4. Wait for loading to complete.

### TST-BUG-002: **Aggregation Gap Stability** [Implemented]
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Requirement(s)**: `REQ-PER-001`
- **Component**: **Aggregation Gap Stability**
- **Jira**: ``ATT-372``
- **Test Description**: 1. Import training history with temporal gaps (e.g. skip a week/month). 2. Trigger a full hierarchical sync.

### TST-STR-017: **Protocol Integrity Audit** [Implemented]
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit
- **Requirement(s)**: `REQ-PRO-012`
- **Component**: **Protocol Integrity Audit**
- **Jira**: ``ATT-523``
- **Test Description**: Verify that the 'Format String Hardening' section is correctly incorporated into 'project_protocol.md'.

### TST-PRO-005: **System Invariant & Impact Protocol Verification** [Verified]
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Requirement(s)**: `REQ-PRO-013`
- **Component**: **System Invariant & Impact Protocol Verification**
- **Jira**: ``ATT-523``
- **Test Description**: Verify that 'project_protocol.md' mandates System Invariants, pre-implementation `find_usages` requirement cross-checks, and adversarial self-reviews.

### TST-PRO-006: **Dual-Gate Review Protocol Verification** [Implemented]
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Requirement(s)**: `REQ-PRO-014`
- **Component**: **Dual-Gate Review Protocol Verification**
- **Jira**: ``ATT-523``
- **Test Description**: Verify that 'project_protocol.md' mandates the unified 5-stage sub-task lifecycle using the automated dual-agent review workflow, with autonomous transitions upon passing gate audits and human approval reserved exclusively for parent tickets.

### TST-PRO-007: **Agent Git Branch, Commit & Merge Protocol Verification** [Implemented]
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Requirement(s)**: `REQ-PRO-015`
- **Component**: **Agent Git Branch, Commit & Merge Protocol Verification**
- **Jira**: ``ATT-422``
- **Test Description**:
  1. Verify `docs/project_protocol.md` and `.cursorrules` explicitly mandate agent-driven creation of feature/bugfix branches off `develop` at ticket start.
  2. Verify protocol defines rules for agent-driven Conventional Commits with asterisk bullet points and ticket references.
  3. Verify protocol mandates merging the ticket branch into `develop` using non-fast-forward merge (`git merge --no-ff <branch> -m "Merge branch '<branch>' into develop"`) upon completion and human release approval.
  4. Validate repository state demonstrates successful branch creation (`feature/ATT-422`), commit adherence, and merge flow.

### TST-PRO-008: **Automated 5-Stage ASPICE Workflow & Independent Agent 2 Audit Verification** [Implemented]
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Requirement(s)**: `REQ-PRO-014`
- **Component**: **Automated 5-Stage ASPICE Workflow & Independent Agent 2 Audit Verification**
- **Jira**: ``ATT-670` / `ATT-685``
- **Test Description**:
  1. Verify `docs/project_protocol.md` defines the unified 5-stage ASPICE lifecycle (`Analysis`, `Test Spec`, `Implementation Plan`, `Implementation`, `Test`, `Erledigt`) across all issue types (Bug, Improvement, Feature).
  2. Verify protocol mandates automated creation of lifecycle subtasks (`[Analysis]`, `[Test-Spec]`, `[Impl-Plan]`, `[Implementation]`, `[Test]`) by Jira Automation upon entering each stage.
  3. Verify protocol mandates that Agent 2 *always* conducts an automated independent gate audit whenever a sub-task is moved to `In Überprüfung`, posting the audit report as a Jira comment and transitioning to `Freigabe (Human)`.
  4. Verify protocol enforces deliverable placement in the sub-task's Description (Agent 1) and audit report placement in Comments (Agent 2).
  5. Verify protocol enforces the strict Human Decision Gate for `Erledigt` on parent tickets and automated parent transition upon sub-task completion.
  6. Verify `tools/jira_util.py` `show` command displays subtasks.
  7. Verify `.cursorrules` reflects the unified 5-stage workflow and automated Agent 2 audits.
  8. Verify `tools/jira_util.py` displays `Lösungsversion` on `show` and `list`, supports `versions` and `set-fixversion`, and warns if missing.
  9. Verify protocol and `.cursorrules` mandate `Lösungsversion` (*Fix Version/s*) and its audit in Gate 5 prior to closure.

### TST-PRO-009: **Inviolable ASPICE Human Decision Gate & Anti-Premature Trigger Protocol Verification** [Implemented]
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Requirement(s)**: `REQ-PRO-016`
- **Component**: **Inviolable ASPICE Human Decision Gate & Anti-Premature Trigger Protocol Verification**
- **Jira**: ``ATT-728` / `ATT-941``
- **Test Description**:
  1. Verify `docs/project_protocol.md` and `.cursorrules` mandate `RequestFeedback: false` on all artifact tool calls.
  2. Verify protocol explicitly states that synthetic IDE auto-approval messages hold zero authority and grant NO permission to modify code or Jira tickets.
  3. Verify protocol and rules mandate `python3 tools/jira_util.py check-gate <Subtask>` before modifying any production code in Stage 4.
  4. Verify `tools/jira_util.py` includes the `check-gate` command that returns exit code 0 when status is `Erledigt` and exit code 1 otherwise.
  5. Verify `.agents/rules/aspice_governance.md` exists and enforces these rules in Antigravity.

### TST-PRO-010: **Sub-Task Self-Sufficiency, Transition Sequencing, Discovery-First, and Git Finalization Verification** [Implemented]
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Requirement(s)**: `REQ-PRO-017`
- **Component**: **Sub-Task Self-Sufficiency, Transition Sequencing, Discovery-First, and Git Finalization Verification**
- **Jira**: ``ATT-945` / `ATT-1000``
- **Test Description**:
  1. Verify `docs/project_protocol.md` explicitly mandates sub-task self-sufficiency (non-empty Description containing full deliverable, concrete audit comments).
  2. Verify protocol mandates strict tool execution sequencing (`update-desc` and `comment` BEFORE state transitions).
  3. Verify protocol mandates discovery-first sub-task handling via `tools/jira_util.py show <Parent>` to eliminate duplicate sub-task creation.
  4. Verify protocol mandates post-completion git finalization (`git checkout develop && git merge --no-ff`) and integration commenting on the parent ticket.
  5. Validate that ATT-945 deliverables adhere to the hardened protocol with full documentation in `docs/engineering/Sprint_Review_and_Retro_ATT-945.md`.

### TST-PRO-011: **Autonomous Multi-Model Independent Review Agent & Automated Gate Audits Verification** [Implemented]
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Requirement(s)**: `REQ-PRO-018`
- **Component**: **Autonomous Multi-Model Independent Review Agent & Automated Gate Audits Verification**
- **Jira**: ``ATT-1122` / `ATT-1194``
- **Test Description**:
  1. Verify `tools/review_agent.py` exists and implements out-of-process gate auditing using Python 3 standard library (`urllib.request`, `json`, `os`, `sys`, `subprocess`) with zero external pip dependencies.
  2. Verify provider credential loading from `.env.gemini` (`GEMINI_API_KEY`) and `.env.claude` (`ANTHROPIC_API_KEY`).
  3. Verify automated gate stage detection based on sub-task summary prefixes (`[Analysis]`, `[Test-Spec]`, `[Impl-Plan]`, `[Implementation]`, `[Test]`).
  4. Verify automated context assembly: sub-task description, parent ticket details, living documentation (`requirements.md`, `tests.md`), and active git diffs.
  5. Verify audit report generation adheres to Gate 1-5 checklist criteria from `docs/project_protocol.md` with explicit recommendation (`RECOMMEND PASS`, `CHALLENGED`, `RECOMMEND REVISION`) and Jira Wiki formatting.
  6. Verify automated comment posting to Jira prefixed with `[Automated comment by AI Agent (External Auditor)]` and transition to `Freigabe (Human)`.
  7. Verify strict human decision gate guard: attempting to transition to `Erledigt` is blocked.
  8. Execute automated unit/functional test of `tools/review_agent.py` verifying mock and live execution across providers.

### TST-PRO-012: **Multi-Account Jira Authentication, Atomic Resolution & Terminal Error Handling Verification** [Verified]
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Requirement(s)**: `REQ-PRO-019`
- **Component**: **Multi-Account Jira Authentication, Atomic Resolution & Terminal Error Handling Verification**
- **Jira**: ``ATT-1123` / `ATT-1200` / `ATT-6146` / `ATT-6147``
- **Test Description**:
  1. Unit Test Credential Configuration Parsing (`.env.jira`): Verify `get_config()` and `resolve_account(role)` parse role-specific keys (`JIRA_AGENT1_USER`, `JIRA_AGENT1_TOKEN`, `JIRA_AGENT2_USER`, `JIRA_AGENT2_TOKEN`, `JIRA_COORDINATOR_USER`, `JIRA_COORDINATOR_TOKEN`) alongside base `JIRA_USER`, `JIRA_TOKEN`.
  2. Unit Test Role Credential Isolation & Elimination of Default Fallback (ATT-6146): Verify that when both role user and token are set, role credentials are used; verify that when role credentials are missing or partial for automated roles (`agent1`, `agent2`, `coordinator`), `resolve_account` aborts with exit code 1 and writes credential isolation error to `stderr`, refusing fallback to `(JIRA_USER, JIRA_TOKEN)` under ASPICE SUP.1.
  3. Unit Test Terminal Error on Missing Credentials: Mock configuration with no credentials defined; verify `get_headers()` or API request halts immediately with exit code 1 and writes diagnostic error to `stderr` without making unauthenticated requests.
  4. Unit Test Basic Auth Header Generation: Verify `get_headers(role)` produces base64 Basic Auth headers matching the resolved credentials.
  5. Backward Compatibility Invariant: Verify that single-account setups run without warnings or errors.
  6. Standard Library Invariant: Verify all unit tests and tooling run with zero pip dependencies on Python 3 standard library.
  7. Unit Test Per-Role Credential Partitioning & Gitignore Wildcard (ATT-6147): Verify that `get_config(role="agent1")` loads `.env.jira.agent1` and isolates credentials; verify that invoking with `--as agent2` without `agent2` credentials exits with code 1; verify `.gitignore` contains `.env.jira*`; verify `tools/hoechstmass/.env.jira.example` contains zero human credentials.

### TST-PRO-013: **Role Locking, Precedence, Anti-Spoofing & Actor Comment Attribution Verification** [Verified]
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Requirement(s)**: `REQ-PRO-020`
- **Component**: **Role Locking, Precedence, Anti-Spoofing & Actor Comment Attribution Verification**
- **Jira**: ``ATT-1123` / `ATT-1200` / `ATT-6146``
- **Test Description**:
  1. Unit Test Precedence Resolution: Verify role selection follows `CLI Flag (--as <role>)` > `Environment Variable (JIRA_ACTOR)` > `Script Default`.
  2. Unit Test Role Hardwiring in `review_agent.py`: Verify that `review_agent.py` hardwires role to `agent2` across all API calls and comments, rejecting or ignoring any attempt to run review audits as `agent1` or `coordinator`.
  3. Unit Test Invalid & Human Role Rejection (ATT-6146): Verify `jira_util.py --as invalid_role` exits with error code 1 and descriptive usage message; verify `--as human`, `--as=human`, and `JIRA_ACTOR=human` abort with exit code 1 and a fatal governance error message.
  4. Unit Test Comment Prefix Attribution: Verify `add_comment` formats opening line with role-specific attribution prefix: `[Automated comment by AI Agent 1 (Implementer)]` for `agent1`, `[Automated comment by AI Agent 2 (Auditor)]` for `agent2`, and `[Automated comment by AI Coordinator]` for `coordinator`.
  5. Unit Test Human Gate Invariance: Verify that attempting `transition_issue(key, "erledigt")` or `transition_issue(key, "freigabe erteilt")` is blocked with exit code 1 specifically for transitions targeting Erledigt / Done, while non-Erledigt transitions (`in_progress`, `in_review`, `freigabe`) succeed.
  6. Automated Protocol Inspection: Programmatic test verifies `docs/project_protocol.md` and `.cursorrules` explicitly define the Coordinator role, specify Coordinator boundaries (cannot override Auditor verdicts, cannot skip gates, cannot close tickets to `Erledigt`).

### TST-PRO-014: **Jira Credential & Token Secret Masking Verification** [Verified]
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Requirement(s)**: `REQ-PRO-021`
- **Component**: **Jira Credential & Token Secret Masking Verification**
- **Jira**: ``ATT-1123` / `ATT-1200``
- **Test Description**:
  1. Unit Test Output Sanitization: Execute mock CLI invocations with valid, fallback, and invalid configurations; capture `sys.stdout` and `sys.stderr`; assert that raw token strings and Basic Auth base64 payload strings never appear in the output.
  2. Unit Test Advisory Notice Scrubbing: Verify fallback notices printed to `stderr` reference only role names or usernames and contain zero token substrings.
  3. Unit Test Exception Masking: Simulate HTTP errors (e.g. 401 Unauthorized); verify error outputs printed to console do not echo request authorization headers or secret tokens.

### TST-PRO-015: **Requirement Archaeology & Chesterton's Fence Governance Verification** [Verified]
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Requirement(s)**: `REQ-PRO-022`
- **Component**: **Requirement Archaeology & Chesterton's Fence Governance Verification**
- **Jira**: ``ATT-1141` / `ATT-1205``
- **Test Description**:
  1. *Unit Test Template & Diff Parser (`tools/verify_requirement_governance.py`)*:
  • *Scenario 1 (Valid Full Archaeology)*: Given a deliverable proposing edits to an existing requirement with all 4 mandatory fields populated (`Original Requirement ID & Target`, `Historical Origin & Commit Trace`, `Root Reason for Existing Formulation`, `Preservation of Core Invariants`), verify the tool exits with code 0.
  • *Scenario 2 (Missing or Empty Mandatory Field)*: Given a deliverable modifying an existing requirement where one or more fields are omitted or blank, verify the tool exits with code 1 and outputs structured schema error diagnostics to stderr.
  • *Scenario 3 (Net-New Requirement Invariance)*: Given git diff containing only added requirement lines with a novel ID (absent from base), verify the tool identifies it as net-new and exits with code 0 without requiring archaeology.
  • *Scenario 4 (Diff Detection against Base)*: Given `git diff develop..HEAD docs/requirements.md`, verify the script accurately parses modified requirement lines versus added/deleted lines using git hunk analysis.
  2. *Gate 2 Auditor Model Verification (`tools/review_agent.py`)*:
  • Verify `GATE_DEFINITIONS["Gate 2"]["checklist"]` includes explicit item: "Requirement Archaeology (Chesterton's Fence): If existing requirements are modified in `docs/requirements.md`, verify presence and validity of the 4-field archaeology section."
  • Verify review prompt instructs the model to issue `CHALLENGED` or `RECOMMEND REVISION` when archaeology is omitted on modified requirements.
  3. *Living Protocol & Rules Inspection*:
  • Verify `docs/project_protocol.md` and `.cursorrules` explicitly document the Chesterton's Fence principle, the 4-field section schema, and the requirement archaeology hurdle.
  4. *Standard Library Footprint Invariant*:
  • Verify `tools/verify_requirement_governance.py` imports only Python 3 standard library modules (`os`, `sys`, `re`, `subprocess`, `unittest`) with zero external pip dependencies.

### TST-PRO-016: **Android Test Fixtures & Jira Metadata Auto-Inheritance Verification** [Verified]
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Requirement(s)**: `REQ-PRO-023`
- **Component**: **Android Test Fixtures & Jira Metadata Auto-Inheritance Verification**
- **Jira**: ``ATT-1125` / `ATT-1210``
- **Test Description**:
  1. *Unit Test `MockCursorFactory` Fidelity (`MockCursorFactoryTest.kt`)*:
  • *Empty Cursor*: Verify cursor initialized with empty row list has `count == 0`, `moveToFirst() == false`, and `isAfterLast == true`.
  • *Column Resolution*: Verify `getColumnIndex` and `getColumnIndexOrThrow` return correct 0-based indices for defined columns and -1 / `IllegalArgumentException` for unknown columns.
  • *Row Traversal*: Given a 3-row dataset, verify sequential `moveToNext()` traverses row 0, 1, 2 accurately, and returns false when exhausting rows.
  • *Data Type Extraction*: Verify `getInt`, `getLong`, `getDouble`, `getFloat`, `getString`, and `getBlob` return expected casted values for heterogeneous cell values.
  • *Nullability Assertions*: Verify `isNull` returns true for null cell values and false for non-null values.
  2. *Integration / Regression Verification in Existing Tests*:
  • Refactor a sample database test (e.g. in `EquipmentDbHelperRetiredTest.kt`) to utilize `MockCursorFactory.create(...)`; verify test passes cleanly with identical assertions and dramatically reduced boilerplate.
  3. *CLI Unit Tests for Jira Metadata & Sprint Auto-Inheritance (`tools/test_jira_accounts.py`)*:
  • Verify `create_subtask` and `create_issue` accept and process `--add-to-sprint` and `--fixversion` flags.
  • Mock Jira Agile board sprint query; verify sub-task creation with `--add-to-sprint` attaches newly created issue key to active sprint.
  4. *Living Protocol & Sandbox Documentation Inspection*:
  • Verify `docs/project_protocol.md` and `.cursorrules` explicitly document filesystem boundaries for Gradle Daemon caches (`~/.gradle`, `~/.android`) and when to execute with elevated permissions (`BypassSandbox: true`).

### TST-PRO-017: **Modular Skill-Based Agile Architecture & Directory Parity Verification** [Implemented]
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Requirement(s)**: `REQ-PRO-024`
- **Component**: **Modular Skill-Based Agile Architecture & Directory Parity Verification**
- **Jira**: ``ATT-1508` / `ATT-1513``
- **Test Description**:
  1. *Directory & SKILL.md Parity Verification*:
  • Programmatically inspect `.agents/skills/` and verify that all 9 skill directories exist: `stage1-analysis`, `stage2-req-test-spec`, `stage3-impl-plan`, `stage4-implementation`, `stage5-verification`, `jira-workflow`, `ui-designer`, `brainstormer`, `sprint-planner`.
  • Verify each skill directory contains a `SKILL.md` file with valid YAML frontmatter matching regex `^---\ntitle: .+\ndescription: .+\n---` or `^---\nname: .+\ndescription: .+\n---`.
  2. *Deliverable Template Verification*:
  • Verify existence and completeness of `templates/analysis_template.md` in `stage1-analysis`.
  • Verify existence and completeness of `templates/test_spec_template.md` in `stage2-req-test-spec`.
  • Verify existence and completeness of `templates/plan_template.md` in `stage3-impl-plan`.
  • Verify existence and completeness of `templates/walkthrough_template.md` in `stage5-verification`.
  • Verify existence and completeness of `templates/backlog_ticket_template.md` in `brainstormer`.
  3. *Jira Subtask Transition & Parent Final Review Invariant Verification*:
  • Verify `tools/jira_util.py` allows subtasks to transition directly to `Erledigt` via transition `freigabe` (or ID `4`).
  • Verify `tools/jira_util.py` transitions parent tickets in `Test` to `Final Review (Human)` and automatically assigns them to `human`.
  • Verify `tools/jira_util.py` strictly blocks transitioning parent tickets to `Erledigt` for AI agent roles, exiting with error code 1.
  4. *Sprint Lifecycle & Skill Integration*:
  • Verify `sprint-planner` defines Phase 1 Sprint-Start interactive screening and Phase 3 Sprint-End joint review procedures.
  5. *Living Protocol & Rules Integrity*:
  • Verify `docs/project_protocol.md` and `.cursorrules` document the modular skill layout, referencing `.agents/skills/` for execution while preserving core invariants and Human Decision Gates.

### TST-PRO-018: **Parallel Unit Test Forking, Worker Recycling, and Performance Benchmarking** [Verified]
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
2. *Defensive Memory Allocation Capping Verification (`TST-PRO-018.2`)*: Inspect command line arguments of `GradleWorkerMain` processes via `ps -ef` or `/proc/<pid>/cmdline`. Verify presence of `-Xms256m` and `-Xmx768m`. Confirm aggregate heap across workers is capped $\le 3.1\text{ GB}$.
3. *Shared Localization DOM Cache Thread-Safety & Immutability Verification (`TST-PRO-018.3`)*: Execute `LocalizationTestCacheTest.kt` verifying all 9 locales parse correctly, returned maps are unmodifiable (`UnsupportedOperationException` on mutation), 16 concurrent threads access the cache without data races, and disk is read at most once per locale per JVM.
4. *Worker Recycling & MockK Heap Stability Verification (`TST-PRO-018.4`)*: Monitor worker PIDs during a full run of all 436 test classes. Verify worker processes exit and new workers are spawned every 80 test classes (`forkEvery = 80`). Verify absence of escalating GC pauses.
5. *Full Clean-Room Regression & Performance Benchmark Execution (`TST-PRO-018.5`)*: Execute `./gradlew clean testDebugUnitTest`. Measure total execution duration. Verify 100% pass rate across all 436 test classes and >2,130 tests, with execution time reduced by $\ge 40\%$ compared to baseline.]
- **Requirement(s)**: `REQ-PRO-025`
- **Component**: **Parallel Unit Test Forking, Worker Recycling, and Performance Benchmarking**
- **Jira**: ``ATT-2770``
- **Test Description**: 1. *Parallel Worker Forking & Multi-Core Utilization Verification (`TST-PRO-018.1`)*: Trigger `./gradlew testDebugUnitTest`. Inspect OS processes via `ps aux

### TST-PRO-019: **Document Triad Architecture, Governance Streamlining, and Invariant Verification** [Implemented]
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Requirement(s)**: `REQ-PRO-026`
- **Component**: **Document Triad Architecture, Governance Streamlining, and Invariant Verification**
- **Jira**: ``ATT-3323``
- **Test Description**:
  1. *ASPICE Governance 4-Tier Structure Audit*:
  • Verify `.agents/rules/aspice_governance.md` defines exactly 16 consolidated rules structured into 4 descending importance tiers.
  • Verify Tier 1 (Rules 1–6) contains: Rule 1 (*Obey the Rules & Mandatory Triad Refresh*), Rule 2 (*Autonomous Full-Sprint Execution*), Rule 3 (*Human Decision Gates & Terminal States*), Rule 4 (*Gate 3 Pre-Check Before Production Code*), Rule 5 (*Strict Separation of Ticket Creation vs. Implementation*), Rule 6 (*Autonomous In-Sprint Escalation Protocol*).
  • Verify Tier 2 (Rules 7–11) contains: Rule 7 (*Strict Sprint Scope & Backlog Rank Authority*), Rule 8 (*Zero Code Changes During Sprint Review*), Rule 9 (*Lean Defect Recording with Immediate Evidence Capture*), Rule 10 (*Mandatory Device Deployment Before Review*), Rule 11 (*Retrospective & Governance Documentation Before Merge*).
  • Verify Tier 3 (Rules 12–14) contains: Rule 12 (*Standardized Sub-Task Naming Scheme*), Rule 13 (*Clean Ticket Summaries*), Rule 14 (*Fix Version Governance*).
  • Verify Tier 4 (Rules 15–16) contains: Rule 15 (*Formal GitFlow Hotfix & Dual-Branch Protocol*), Rule 16 (*Human Prerequisites & Deferred Field-Testing*).
  • Verify consolidation of legacy duplicate pairs: 17+25 -> Rule 9; 7+19 -> Rule 14; 26+27 -> Rule 15; 22+28 -> Rule 16.
  • Verify absence of obsolete rules (legacy Rule 3 and legacy Rule 24).
  • Verify presence and completeness of the Historical Mapping Cross-Reference Table for Rules 1–36.
  2. *UI Design Guidelines Consolidation Audit (`docs/design_guidelines.md`)*:
  • Verify inclusion of Section 5 (*Visual Consistency Baseline / Rule 23*).
  • Verify inclusion of Section 3.1 (*Explanatory Scale Direction in Settings Sliders / Rule 31*).
  • Verify inclusion of Section 2.1 (*Coordinate Space Normalization for Sub-Entity Inspection Charts / Rule 32*).
  • Verify inclusion of Section 6.1 (*Unified Viewport Anchor Authority for Map Tracking / Rule 33*).
  • Verify inclusion of Section 4 (*Jetpack Compose @Preview tooling, 9-language localization parity, and positional format specifiers*).
  3. *Technical Coding Standards Architecture Audit (`docs/coding_standards.md`)*:
  • Verify existence and completeness of `docs/coding_standards.md`.
  • Verify Section 1.1 (*Rule 36: Single Source of Truth & DRY — Shared Code Over Duplication*, mandatory reuse of canonical formatters and unit math, prohibition of inline manual formatting).
  • Verify Section 2.1 (*Rule 20: Database & DTO Mapping Symmetry*).
  • Verify Section 3.1 (*Rule 21: Specific Direct Platform Intents Over Generic App Settings*).
  • Verify Section 3.2 (*Rule 29: Defensive Service Lifecycle Finalization & FGS Permission Gating*).
  • Verify Section 4.1 (*Rule 30: Developer Tool Host Pre-Flight Diagnostics*).
  • Verify Section 1.2 (*Rule 34: Chesterton's Fence for Empty Callback Lambdas & Gesture Contract Tests*).
  • Verify Section 3.3 (*Rule 35: Clean Hardware Teardown and Orphan Defense on Persistence Deletion*).
  • Verify Section 5 (*Test Framework Invariants: Prohibition of returnDefaultValues, Stage 4 unit tests vs Stage 5 clean-room regression*).
  4. *Document Triad Cross-Reference Audit*:
  • Verify `docs/project_protocol.md` and `.agents/skills/` cross-reference the Document Triad without broken links.
  5. *Clean-Room Governance & Regression Execution*:
  • Execute `python3 tools/verify_requirement_governance.py` asserting exit code 0.
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with zero regressions.

### TST-STB-001: **Resilient WorkManager Initialization & Startup Protection Verification** [Verified]
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit
- **Requirement(s)**: `REQ-STB-001`
- **Component**: **Resilient WorkManager Initialization & Startup Protection Verification**
- **Jira**: ``ATT-621``
- **Test Description**:
  1. Verify `AndroidManifest.xml` configures `androidx.startup.InitializationProvider` with `<meta-data android:name="androidx.work.WorkManagerInitializer" tools:node="remove" />`.
  2. Verify `TrainingApplication.onCreate()` encapsulates `WorkManager.initialize(...)` within a `try ... catch (Throwable)` block that logs warnings on failure.
  3. Verify `ExportManager.java` and `BackupWorker.kt` guard `WorkManager.getInstance()` invocations against `IllegalStateException` / `Throwable`.
  4. Verify unit test confirms safe error handling when WorkManager is uninitialized.
  5. Run full test suite (`./gradlew testDebugUnitTest`) to ensure zero regression.

### TST-STB-002: **TrackerService Foreground Service Resilience Verification** [Verified]
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit
4. Create and run unit tests verifying safe handling of `null` intent restarts without background location permission and graceful recovery when `startForeground` throws `SecurityException`.
5. Run full test suite (`./gradlew testDebugUnitTest`) to ensure zero regression.]
- **Requirement(s)**: `REQ-STB-002`
- **Component**: **TrackerService Foreground Service Resilience Verification**
- **Jira**: ``ATT-622``
- **Test Description**:
  1. Verify `TrackerService.onStartCommand` returns `Service.START_STICKY`.
  2. Verify that when `intent == null` and `hasBackgroundLocationPermission()` is false, `startForeground` is not called with location type, `stopSelf()` is invoked, and workout state is preserved.
  3. Verify that all `startForeground` calls are wrapped in `try ... catch (SecurityException

### TST-STB-003: **Interrupted Workout Resumption & Recovery Verification** [Verified]
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit
- **Requirement(s)**: `REQ-STB-003`
- **Component**: **Interrupted Workout Resumption & Recovery Verification**
- **Jira**: ``ATT-635``
- **Test Description**:
  1. Verify `TrackerService.showTrackingInterruptedNotification()` attaches `EXTRA_RESUME_INTERRUPTED_WORKOUT = true` and `SELECTED_FRAGMENT = START_OR_TRACKING` to its `PendingIntent`.
  2. Verify `MainActivityWithNavigation.handleIntent()` detects the resumption extra, sets `cResumeFromCrash = true`, cancels the notification, and resumes tracking via `REQUEST_START_TRACKING`.
  3. Verify `WorkoutSummariesDatabaseManager.hasUnfinishedWorkout()` accurately identifies unfinished workouts (`FINISHED == 0`).
  4. Verify app launch prompts `StartOrResumeDialog` when an unfinished workout exists.
  5. Execute unit tests verifying Intent extras, database detection, and resume flag handling.
  6. Perform on-device validation on Pixel 10 (Android 16): start tracking, kill process via `run-as`, tap interrupted notification, confirm workout resumes in foreground without data loss.
  7. Run full test suite (`./gradlew testDebugUnitTest`) to ensure zero regression.

### TST-STB-004: **Location Provider Availability & Exception Shielding Verification** [Verified]
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit
3. Verify that when GPS or Network provider is missing or throws `IllegalArgumentException: provider does not exist`, the device calls `LocationUnavailable()` and does not crash.
4. Verify `DeviceManager` checks provider availability and isolates device initialization failures.
5. Execute unit tests in `SpeedAndLocationDeviceResilienceTest.kt` verifying missing provider and exception handling.
6. Run full regression test suite (`./gradlew testDebugUnitTest`) to ensure zero regression.]
- **Requirement(s)**: `REQ-STB-004`
- **Component**: **Location Provider Availability & Exception Shielding Verification**
- **Jira**: ``ATT-623``
- **Test Description**:
  1. Verify `SpeedAndLocationDevice_GPS` checks `mLocationManager.getProvider(LocationManager.GPS_PROVIDER) != null` before requesting location updates.
  2. Verify all `LocationManager.requestLocationUpdates()` calls in `SpeedAndLocationDevice_GPS` and `SpeedAndLocationDevice_Network` are wrapped in `try ... catch (IllegalArgumentException

### TST-STB-005: **Equipment Database Boundary Validation & Error Log Shielding Verification** [Verified]
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit
- **Requirement(s)**: `REQ-STB-005`
- **Component**: **Equipment Database Boundary Validation & Error Log Shielding Verification**
- **Jira**: ``ATT-644``
- **Test Description**:
  1. Verify `EquipmentDbHelper.getEquipmentNameFromId()` returns `null` immediately for non-positive IDs (`0`, `-1`, `-100`) without querying SQLite or calling `Log.e`.
  2. Verify `EquipmentDbHelper.getStravaIdFromId()` returns `null` immediately for non-positive IDs without querying SQLite or calling `Log.e`.
  3. Verify `EquipmentDbHelper.getDeviceIdsForEquipment()` returns an empty list for non-positive IDs without database queries.
  4. Verify valid positive IDs (`> 0`) resolve equipment names and Strava IDs accurately.
  5. Verify non-existent positive IDs (`> 0`) continue to log diagnostic `ERROR` messages.
  6. Verify `WorkoutDataMapper`, `EquipmentDataProvider`, `StravaUploader`, `EquipmentAndSportTypeDiscoveryManager`, and `SportTypeViewModel` short-circuit lookups when `equipmentId <= 0`.
  7. Execute unit tests in `EquipmentDbHelperResilienceTest.kt`.
  8. Run full regression test suite (`./gradlew testDebugUnitTest`) to ensure zero regression.

### TST-STB-006: **Navigation Drawer Header Background Resource Availability & Crash Immunity Verification** [Verified]
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit
- **Requirement(s)**: `REQ-STB-006`
- **Component**: **Navigation Drawer Header Background Resource Availability & Crash Immunity Verification**
- **Jira**: ``ATT-1036`, `ATT-1086``
- **Test Description**:
  1. *Resource File Existence Verification*:
  • Verify that `app/src/main/res/drawable-nodpi/menu_header_background.jpg` exists in the codebase.
  • Verify that the resource can be resolved across all screen densities without falling into a missing-resource state.
  2. *Defensive Fallback & Unit Test Verification (`AppNavigationDrawerTest.kt`)*:
  • Verify that `DrawerHeader()` encapsulates background painter resolution in safe defensive error handling.
  • Verify via automated unit test that when resource resolution throws `Resources$NotFoundException`, `DrawerHeader()` does NOT propagate the fatal exception and renders fallback UI cleanly.
  3. *Clean-Room Regression Verification*:
  • Run full test suite (`./gradlew testDebugUnitTest`) to ensure zero regressions across all modules.
  4. *On-Device / Density Verification*:
  • Deploy debug APK to connected device (`./gradlew installDebug`).
  • Verify navigation drawer opens cleanly with landscape header background.
  • Adjust system display size / text zoom setting and verify drawer continues to display without crashing.

### TST-STB-007: **AndroidX Core 1.15.0 Alignment & Insets Crash Immunity Verification** [Verified]
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit
- **Requirement(s)**: `REQ-STB-007`
- **Component**: **AndroidX Core 1.15.0 Alignment & Insets Crash Immunity Verification**
- **Jira**: ``ATT-1037`, `ATT-1092``
- **Test Description**:
  1. *Dependency Graph Verification*:
  • Run `./gradlew :app:dependencyInsight --configuration debugRuntimeClasspath --dependency androidx.core:core` and verify that the resolved version across all configurations is strictly `1.15.0`.
  • Run `./gradlew :app:dependencyInsight --configuration debugRuntimeClasspath --dependency androidx.core:core-ktx` and verify that the resolved version is strictly `1.15.0`.
  2. *Automated Unit & Compatibility Verification*:
  • Create automated unit test `CoreDependencyAlignmentTest.kt` verifying that `WindowInsetsCompat` does not invoke missing platform methods on API 34 configurations.
  3. *Clean-Room Regression Verification*:
  • Run full test suite (`./gradlew testDebugUnitTest`) to ensure 100% of existing unit tests pass without regressions under `androidx.core:1.15.0`.
  4. *On-Device / Compose Verification*:
  • Deploy debug APK to connected device (`./gradlew installDebug`).
  • Verify application startup, Compose navigation drawer, and bottom popups render properly without insets crashes.

### TST-STB-008: **Resilient Permission-Gated Location Provider Access & SecurityException Immunity Verification** [Verified]
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit
- **Requirement(s)**: `REQ-STB-008`
- **Component**: **Resilient Permission-Gated Location Provider Access & SecurityException Immunity Verification**
- **Jira**: ``ATT-1243``
- **Test Description**:
  1. Verify `MainActivityWithNavigation.onCreate()` evaluates `ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED` prior to querying `LocationManager.getProvider(LocationManager.GPS_PROVIDER)`.
  2. Verify that when `ACCESS_FINE_LOCATION` is missing or revoked on startup, `LocationManager.getProvider()` is bypassed and `GPSDisabledDialog` is suppressed.
  3. Verify all calls to `LocationManager.getProvider()` and `isProviderEnabled()` are encapsulated in localized exception handling catching `SecurityException` and `IllegalArgumentException`.
  4. Verify that when `LocationManager.getProvider()` throws `SecurityException` or returns `null`, the exception is logged cleanly without crashing the application process.
  5. Verify that in `SpeedAndLocationDevice_GPS` and `SpeedAndLocationDevice_Network`, `getProvider()` is guarded inside existing try-catch blocks and calls `LocationUnavailable()` safely on failure.
  6. Create and run automated unit tests in `LocationPermissionSafetyTest.kt` verifying unpermissioned cold starts, missing providers, and permission grant handling.
  7. Run full clean-room regression test suite (`./gradlew testDebugUnitTest`) to ensure zero regressions across all modules.

### TST-STB-009: **Asynchronous Workout Initialization, Barrier Synchronization & ANR Immunity Verification** [Verified]
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit
- **Requirement(s)**: `REQ-STB-009`
- **Component**: **Asynchronous Workout Initialization, Barrier Synchronization & ANR Immunity Verification**
- **Jira**: ``ATT-1245` / `ATT-1260``
- **Test Description**:
  1. *Main-Thread Execution Latency Verification*:
  • Verify that `TrackerService.onStartCommand(START_NORMAL)` returns immediately (`Service.START_STICKY`) on the calling thread (< 10ms) without blocking on SQLite schema DDL (`CREATE TABLE`, `DROP TABLE`) or disk page reads.
  2. *Asynchronous Table Commitment & CompletableFuture Verification*:
  • Verify that `mTableInitializationFuture` completes successfully upon table creation on `mDbExecutor`.
  • Verify that sensor samples dispatched to `mDbExecutor` await `mTableInitializationFuture.get(5, TimeUnit.SECONDS)` and persist without `SQLiteException: no such table`.
  3. *Zero Sample Loss / Buffer Verification*:
  • Verify that early sensor ticks arriving while table creation is queued on `mDbExecutor` are buffered in the executor queue and persisted once the table future completes.
  4. *Broadcast Deferral Verification*:
  • Verify that `notifyTrackingStarted(workoutId)` broadcast is emitted strictly after table creation completes, preventing downstream UI observers from querying uninitialized tables.
  5. *Defensive Error Recovery & Graceful Teardown*:
  • Verify that if table creation throws `SQLException`, `mTableInitializationFuture` completes exceptionally, `mTrackerHandle` is cancelled, `showTrackingInterruptedNotification()` is shown, and `performStopSelf()` is called without process crash.
  6. *Clean-Room Regression Suite*:
  • Execute `./gradlew testDebugUnitTest` across all modules with zero regressions.

### TST-STB-010: **Compose List Map Lite-Mode Enforcement & RenderThread Deadlock Immunity Verification** [Verified]
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit
- **Requirement(s)**: `REQ-STB-010`
- **Component**: **Compose List Map Lite-Mode Enforcement & RenderThread Deadlock Immunity Verification**
- **Jira**: ``ATT-1246` / `ATT-1272``
- **Test Description**:
  1. *Lite Mode Configuration Verification*:
  • Verify that `PathPreviewMap.kt` configures `googleMapOptionsFactory = { GoogleMapOptions().liteMode(true) }`.
  • Verify that `WorkoutClusterComponents.kt` (`ClusterCard` and `ClusterWorkoutCard`) configures `googleMapOptionsFactory = { GoogleMapOptions().liteMode(true) }`.
  2. *Visual & Functional Integrity Verification*:
  • Verify that `PathPreviewMap` in Lite Mode correctly renders polyline coordinates, Start marker, End marker, and Apex marker.
  • Verify that `CameraUpdateFactory.newLatLngBounds` correctly executes bounds clamping without exceptions in Lite Mode.
  • Verify that `onMapClick` callbacks continue to trigger detail navigation when tapped.
  3. *Interactive Map Preservation Verification*:
  • Verify that full-screen interactive maps (`ATrainingTrackerMap.kt`, `LapEditBottomSheet.kt`, `ManualClusterScreen.kt`) maintain interactive mode (`liteMode = false`) with user gestures fully functional.
  4. *Clean-Room Regression Suite*:
  • Execute `./gradlew testDebugUnitTest` across all modules with zero regressions.
  5. *Physical Device Verification*:
  • Deploy debug build onto connected device `66020DLCR002FL`.
  • Rapidly scroll through route cluster list and workout list containing embedded preview maps.
  • Perform backgrounding and task switching during scroll; inspect Logcat to verify 0 ANRs, 0 native deadlocks, and clean UI thread responsiveness.

### TST-STB-011: **DevicesTabbedViewModel SavedStateHandle Fallbacks & Crash Immunity Verification** [Verified]
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit
- **Requirement(s)**: `REQ-STB-011`
- **Component**: **DevicesTabbedViewModel SavedStateHandle Fallbacks & Crash Immunity Verification**
- **Jira**: ``ATT-1308``
- **Test Description**:
  1. *Empty SavedStateHandle Instantiation Verification*:
  • Instantiate `DevicesTabbedViewModel` with an empty `SavedStateHandle()`.
  • Verify `protocol` defaults cleanly to `Protocol.ALL` without throwing `NullPointerException`.
  • Verify `uiState.value` resolves to `UiState.DisplayingTabs(DeviceType.ALL)` without stalling on `UiState.AwaitingDeviceTypeSelection`.
  • Verify `SavedStateHandle` persists the default values.
  2. *Malformed / Unknown Value Fallback Verification*:
  • Instantiate with invalid/corrupted strings (e.g. `"UNKNOWN_PROTO"`, `"UNKNOWN_DEV"`).
  • Verify safe fallback to `Protocol.ALL` and `DeviceType.ALL` without exceptions.
  3. *Explicit Parameter Preservation Verification*:
  • Instantiate with valid parameters (`Protocol.ANT_PLUS`, `DeviceType.HEART_RATE`).
  • Verify explicit values are accurately resolved and preserved in state.
  4. *Dynamic Filter Update Verification*:
  • Invoke `updateFilters(Protocol.BLUETOOTH_LE, DeviceType.POWER)`.
  • Verify new values are reflected in `protocol`, `uiState`, and `SavedStateHandle`.
  5. *Clean-Room Regression Suite*:
  • Execute `./gradlew testDebugUnitTest` across all modules with zero regressions.

### TST-STR-018: **Global Translation Parity, Array Integrity & Formatter Audit** [Verified]
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit
- **Requirement(s)**: `REQ-LOC-001`
- **Component**: **Global Translation Parity, Array Integrity & Formatter Audit**
- **Jira**: ``ATT-525``
- **Test Description**:
  1. Verify all 9 supported locales possess 100% of translatable string keys (0 missing keys, 0 orphaned keys).
  2. Verify all localized format strings adhere strictly to `REQ-UI-122` positional specifier syntax (`%1$s`), with 100% parity in specifier count, indices, and conversion types against default English resources.
  3. Verify all localized string-arrays have identical item counts to base `values/arrays.xml`.
  4. Verify `filter_max` is defined in `strings_filters.xml` across all locales and removed from `arrays.xml` and `strings.xml`.
  5. Verify `devices_sensors_title` is defined in `strings_devices.xml` in Spanish.
  6. Verify `backup_interval_values` has `translatable="false"` and obsolete `Basic_Sport_Types_values` is removed.
  7. Execute automated tests in `TranslationParityTest.kt`.
  8. Run full regression test suite (`./gradlew testDebugUnitTest`) to ensure zero regression.

### TST-STB-012: **Forensic Process Kill Diagnosis, Dynamic Rationale Titles, Empathetic Escalation & Direct Battery Exemption Guidance Verification** [Verified]
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit
- **Requirement(s)**: `REQ-STB-012`
- **Component**: **Forensic Process Kill Diagnosis, Dynamic Rationale Titles, Empathetic Escalation & Direct Battery Exemption Guidance Verification**
- **Jira**: ``ATT-2079``
- **Test Description**:
  1. *Forensic Classification & Idempotence Test (`ProcessExitReasonHelperTest.kt`)*:
  • Verify `ProcessExitReasonHelper` maps `REASON_EXCESSIVE_RESOURCE_USAGE` to `BATTERY_KILL`.
  • Verify repeated calls / screen rotations DO NOT increment `battery_kill_count` without a new timestamp.
  • Verify new exit timestamp increments `battery_kill_count` by 1.
  • Verify `battery_kill_count` resets to 0 when `isIgnoringBatteryOptimizations` becomes true.
  • Verify `openBatteryOptimizationSettings` prioritizes direct `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` with `package:` URI per Rule 21.
  • Verify API < 30 safe fallback without reflection errors.
  2. *Dialog Dynamic Titles & Empathetic Copy Test (`StartOrResumeDialogContractTest.kt`)*:
  • Verify dialog title dynamically adapts to specific cause (`unfinished_workout_title_battery`, `unfinished_workout_title_memory`, `unfinished_workout_title_permission`, or generic).
  • Verify body copy uses constructive, empathetic athletic-partnership phrasing free of sarcasm.
  • Verify Stage 1 appends Strava acknowledgment when `uploadToStrava == true`.
  • Verify "Disable Battery Optimization" action is provided on `BATTERY_KILL`.
  • Verify `chooseResume()` and `chooseStart()` callbacks remain wired.
  3. *9-Language Localization Audit*:
  • Verify all dynamic titles, de-escalated escalation copies, Strava text, and button labels exist across all 9 locales (EN, DE, ES, FR, IT, JA, NL, PL, PT).
  4. *Clean-Room Full Suite Regression Execution*:
  • Run `./gradlew testDebugUnitTest` verifying 100% pass rate.

### TST-STB-013: **Resilient Single-Pass Workout History Streaming & CursorWindow Crash Protection Verification** [Verified]
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit
- **Requirement(s)**: `REQ-STB-013`
- **Component**: **Resilient Single-Pass Workout History Streaming & CursorWindow Crash Protection Verification**
- **Jira**: ``ATT-2309``
- **Test Description**:
  1. *Single-Pass Sequential Streaming Unit Tests (`WorkoutRepositoryStreamTest.kt`)*:
  • Verify that `loadAllWorkouts()` executes in a single forward pass without calling `c.moveToPosition(...)` or seeking backward across rows.
  • Verify that chunks of 50 are extracted into `RawCursorSnapshot` and vectorized metadata queries (`extrema`, `stravaData`, `clusterNames`, `laps`) execute in batch.
  • Verify that all loaded workouts are accurately mapped with their metadata and export statuses.
  2. *CursorWindow Boundary & Synthetic 2,000 Workouts Stress Test (`WorkoutRepositoryStressTest.kt`)*:
  • Simulate an extensive dataset of 2,000 synthetic workouts backed by a MockCursor.
  • Verify that all 2,000 workouts stream completely to completion into `allWorkouts` without throwing `IllegalStateException`.
  • Verify that progressive UI updates are emitted at expected thresholds (first 10, each batch of 50, and completion).
  3. *Defensive Row Exception Shielding Verification*:
  • Simulate a cursor row throwing `IllegalStateException` or `CursorWindowAllocationException` during row extraction.
  • Verify that the repository catches the exception, logs a warning, skips the faulty row, and completes streaming the remaining rows without crashing the process.
  4. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` verifying 100% pass rate with zero regressions.

### TST-STB-014: **TrainingApplication Foreground Service Startup Delegation & Exception Shielding Verification** [Verified]
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit
- **Requirement(s)**: `REQ-STB-014`
- **Component**: **TrainingApplication Foreground Service Startup Delegation & Exception Shielding Verification**
- **Jira**: ``ATT-2661``
- **Test Description**:
  1. *Foreground Service Startup Delegation Unit Test (`TrainingApplicationTrackingTest.kt`)*:
  • Verify that `startTracking()` delegates to `ContextCompat.startForegroundService()` instead of `startService()` when targeting `TrackerService`.
  • Verify tracking mode transitions to `TrackingMode.TRACKING`.
  2. *Background Launch Restriction Shielding Unit Test (`TrainingApplicationTrackingTest.kt`)*:
  • Simulate `ContextCompat.startForegroundService()` throwing `IllegalStateException("Not allowed to start service ...: app is in background")`.
  • Verify the exception is caught cleanly without terminating the application process.
  • Verify tracking mode resets to `TrackingMode.READY`.
  • Verify `TrackerService.showTrackingInterruptedNotification()` is displayed to provide user recovery.
  3. *SecurityException Permission Shielding Unit Test (`TrainingApplicationTrackingTest.kt`)*:
  • Simulate `ContextCompat.startForegroundService()` throwing `SecurityException("Missing FGS permission")`.
  • Verify the exception is caught cleanly and logged, preventing crash.
  4. *Clean-Room Full Suite Regression Execution*:
  • Run `./gradlew testDebugUnitTest` verifying 100% pass rate with zero regressions.

### TST-STB-015: **Simulated Process Exit Reason Injection & Native PyQt6 Desktop Kill Simulator Verification** [Verified]
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit
- **Requirement(s)**: `REQ-STB-015`
- **Component**: **Simulated Process Exit Reason Injection & Native PyQt6 Desktop Kill Simulator Verification**
- **Jira**: ``ATT-3377``
- **Test Description**:
  1. *Debug Injection & Idempotence Unit Tests (`ProcessExitReasonHelperTest.kt`)*:
  • In debug mode: Inject `pref_debug_simulated_kill_reason = "LOW_MEMORY"` into SharedPreferences.
  • Invoke `resolveKillReason(context)`; verify diagnosis reason is `KillReason.LOW_MEMORY`.
  • Verify `pref_debug_simulated_kill_reason` is immediately removed from SharedPreferences.
  • Verify second invocation without injection evaluates standard OS exit reason or generic.
  • Repeat for `"PERMISSION_REVOKED"`, `"BATTERY_KILL"`, and `"GENERIC"`.
  2. *Release Build Isolation Test (`ProcessExitReasonHelperTest.kt`)*:
  • Simulate release build (`isDebug = false`); verify `pref_debug_simulated_kill_reason` is ignored even if present in SharedPreferences.
  3. *Tool Injection CLI & Controller Contract Tests (`tools/test_simulate_kill.py`)*:
  • Verify `inject_simulated_kill_reason()` formats valid XML and executes `adb shell run-as`.
  • Verify `kill_lmk_simulation()` invokes injection before termination.
  • Verify `--kill lmk` CLI argument triggers injection.
  4. *PyQt6 GUI Instantiation & Fallback Contract Tests (`tools/test_simulate_kill.py`)*:
  • Verify `KillSimulatorWindow` instantiates with obsidian theme styling, status cards, and action buttons when PyQt6 is available.
  • Verify graceful fallback to terminal menu when `HAS_PYQT6 == False`.
  5. *Clean-Room Full Suite Regression Execution*:
  • Run `./gradlew testDebugUnitTest` verifying 100% pass rate with zero regressions.

### TST-TOOL-001: **External ADB GPX Replay Tool Verification & Simulation Engine Unit Tests** [Verified]
- **Priority**: Medium
- **Type**: Non-Functional
- **Verification Method**: Unit
- **Requirement(s)**: `REQ-TOOL-001`
- **Component**: **External ADB GPX Replay Tool Verification & Simulation Engine Unit Tests**
- **Jira**: ``ATT-2467``
- **Test Description**:
  1. *Fake GPS Engine Unit Tests (`tools/test_fake_gps.py`)*:
  • Verify geodesic distance calculation (Haversine) matches reference distances within 0.1% error.
  • Verify forward travel bearing calculation correctly calculates headings across all 4 cardinal quadrants.
  • Verify forward travel bearing calculation derives heading strictly from coordinate difference between previous and current emitted location fixes.
  • Verify polyline interpolation advances distance monotonically with elapsed time and speed.
  • Verify speed multipliers ($1\times, 2\times, 5\times, 10\times$) scale advancement distance proportionally.
  • Verify deliberate off-route deviation offsets coordinates $\ge 60\text{ m}$ perpendicular to forward bearing.
  • Verify seeking / scrubbing accurately snaps simulation state to target route progress ratio and updates forward bearing from coordinate deltas.
  • Verify GPX parser extracts trackpoints and handles missing elevation/timestamps gracefully.
  2. *ADB Multi-Provider Lifecycle & Process Guard Verification*:
  • Verify multi-provider synchronization: test provider commands (`add-test-provider`, `set-test-provider-enabled`, `set-test-provider-location`, `remove-test-provider`) target all 3 providers (`gps`, `network`, `fused`) to prevent OS location oscillation with live Wi-Fi fixes.
  • Verify single-instance process lock: identifies and terminates previous running background instances (`pgrep -f fake_gps.py`) before starting playback.
  • Verify exit handlers ensure `remove-test-provider` across all registered providers on termination (`SIGINT`, `SIGTERM`, `atexit`).
  3. *Live Device Verification on Google Pixel 10*:
  • Verify `python3 tools/fake_gps.py --sample munich` injects mock locations across `gps`, `network`, and `fused` into attached Pixel 10 and drives aTrainingTracker live tracking/navigation.
  4. *Full Clean-Room Regression Suite*:
  • Execute `./gradlew testDebugUnitTest` and `python3 tools/test_fake_gps.py` asserting 100% test pass rate with 0 regressions.

### TST-TOOL-002: **Synchronized TCX Workout Replay Tool with PyQt6 GUI & Telemetry Verification** [Verified]
- **Priority**: Medium
- **Type**: Non-Functional
- **Verification Method**: Unit
- **Requirement(s)**: `REQ-TOOL-002`
- **Component**: **Synchronized TCX Workout Replay Tool with PyQt6 GUI & Telemetry Verification**
- **Jira**: ``ATT-2969``
- **Test Description**:
  1. *TCX Parsing Unit Tests (`tools/test_replay_workout.py`)*:
  • Verify standard Garmin/Strava `.tcx` XML is parsed extracting latitude, longitude, altitude, distance, HR, cadence, and power.
  • Verify default handling for optional or missing fields.
  2. *Playback Engine & Timeline Scrubbing Tests*:
  • Verify transport transitions (`PLAYING`, `PAUSED`, `STOPPED`).
  • Verify timeline scrubbing snaps telemetry accurately.
  • Verify speed multiplier scaling ($1\times, 2\times, 5\times, 10\times$).
  3. *BLE Payload Construction & BlueZ GATT Server Contract Tests*:
  • Verify `build_power_payload(watts, cadence)` produces valid 8-byte GATT payload for UUID `0x2A63`.
  • Verify `build_hr_payload(bpm)` produces valid 2-byte GATT payload for UUID `0x2A37`.
  • Verify BlueZ GATT Application, Cycling Power Service (`0x1818`), Heart Rate Service (`0x180D`), and dual LE Advertisements (`1818` / `180d`) registration structure.
  4. *Android 14+ Mock Location Command Syntax Tests*:
  • Verify ADB location injection command strictly conforms to `cmd location providers set-test-provider-location <p> --location <lat,lon> --accuracy 2.5 --time <ms>` without illegal `--altitude`, `--speed`, or `--bearing` flags.
  5. *Rule 30 Pre-Flight Self-Diagnostics Tests*:
  • Verify detection of Bluetooth daemon status, adapter power, LE advertising manager, ADB device presence, and shell mock location permission.
  6. *Clean-Room Full Suite Regression Execution*:
  • Execute `python3 -m unittest tools/test_replay_workout.py`.
  • Execute `./gradlew testDebugUnitTest` verifying 100% test pass rate with zero regressions.

### TST-TOOL-003: **TCX Replay BLE Battery Service Simulation, GATT Notification & GUI Control Verification** [Verified]
- **Priority**: Medium
- **Type**: Non-Functional
- **Verification Method**: Unit
- **Requirement(s)**: `REQ-TOOL-003`
- **Component**: **TCX Replay BLE Battery Service Simulation, GATT Notification & GUI Control Verification**
- **Jira**: ``ATT-3178``
- **Test Description**:
  1. *Battery Payload Generation & Clamping Tests (`tools/test_replay_workout.py`)*:
  • Verify `build_battery_payload(100)` produces `[100]`.
  • Verify `build_battery_payload(0)` produces `[0]`.
  • Verify `build_battery_payload(15)` produces `[15]`.
  • Verify values < 0 are clamped to 0 and values > 100 are clamped to 100.
  2. *Battery Service & Characteristic D-Bus Contract Tests (`tools/test_replay_workout.py`)*:
  • Verify `BATTERY_SERVICE_UUID` equals `'0000180f-0000-1000-8000-00805f9b34fb'`.
  • Verify `BATTERY_LEVEL_CHARACTERISTIC_UUID` equals `'00002a19-0000-1000-8000-00805f9b34fb'`.
  • Verify `BatteryLevelCharacteristic` read/notify flags and value updates.
  3. *BleReplayServer Dual Battery State Tests (`tools/test_replay_workout.py`)*:
  • Verify default initial battery levels are 100% for both HR and Power.
  • Verify constructor accepts `hr_battery` and `power_battery` parameters.
  • Verify `set_hr_battery(val)` and `set_power_battery(val)` update internal state and characteristics.
  4. *PyQt6 Battery Widget & Interaction Tests (`tools/test_replay_workout.py`)*:
  • Verify sliders, status labels, and preset buttons exist in `ReplayGuiWindow`.
  • Verify adjusting sliders updates BLE server battery levels and GUI labels.
  • Verify clicking preset buttons (100%, 50%, 15%, 5%) sets correct slider and BLE server values.
  5. *CLI Argument Parsing Tests (`tools/test_replay_workout.py`)*:
  • Verify `--hr-battery` and `--power-battery` CLI arguments.
  6. *Clean-Room Full Suite Regression Execution*:
  • Execute `python3 -m unittest tools/test_replay_workout.py`.
  • Execute `./gradlew testDebugUnitTest` verifying 100% test pass rate with zero regressions.

### TST-TOOL-004: **TCX Replay Per-Device Kill, Disconnection, Reconnection & GUI/CLI Control Verification** [Verified]
- **Priority**: Medium
- **Type**: Non-Functional
- **Verification Method**: Unit
- **Requirement(s)**: `REQ-TOOL-004`
- **Component**: **TCX Replay Per-Device Kill, Disconnection, Reconnection & GUI/CLI Control Verification**
- **Jira**: ``ATT-3179``
- **Test Description**:
  1. *BleReplayServer Kill & Revive Lifecycle Unit Tests (`tools/test_replay_workout.py`)*:
  • Verify `kill_hr()` sets `is_hr_killed = True`, unregisters advertisement, and suppresses HR notifications.
  • Verify `revive_hr()` sets `is_hr_killed = False`, re-registers advertisement, and resumes HR notifications.
  • Verify `kill_power()` sets `is_power_killed = True`, unregisters advertisement, and suppresses CP notifications.
  • Verify `revive_power()` sets `is_power_killed = False`, re-registers advertisement, and resumes CP notifications.
  • Verify telemetry values catch up to current TCX timestamp upon revival.
  2. *AdbGpsInjector Kill & Revive Unit Tests (`tools/test_replay_workout.py`)*:
  • Verify `kill()` sets `is_killed = True` and suppresses coordinate enqueuing in `inject_location()`.
  • Verify `revive()` sets `is_killed = False` and resumes coordinate injection.
  3. *PyQt6 GUI Connectivity Controls Contract Tests (`tools/test_replay_workout.py`)*:
  • Verify toggle buttons exist for HR, Power, and GPS in `ReplayGuiWindow`.
  • Verify clicking toggle buttons triggers kill/revive methods and updates status badges.
  4. *CLI Argument Parsing & Automated Trigger Tests (`tools/test_replay_workout.py`)*:
  • Verify `--kill-hr-at`, `--revive-hr-at`, `--kill-power-at`, `--revive-power-at`, `--kill-gps-at`, `--revive-gps-at` parsing.
  • Verify automated schedule triggers execute at target timestamps.
  5. *Clean-Room Full Suite Regression Execution*:
  • Execute `python3 -m unittest tools/test_replay_workout.py`.
  • Execute `./gradlew testDebugUnitTest` verifying 100% test pass rate with zero regressions.

### TST-LIV-001: **Active and Approaching Live Segments Numeric Distance Sorting Verification** [Verified]
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Requirement(s)**: `REQ-LIV-005`
- **Component**: **Active and Approaching Live Segments Numeric Distance Sorting Verification**
- **Jira**: ``ATT-3324``
- **Test Description**:
  1. *Active On-Segment Crossing Unit Boundary Sorting Tests (`LiveSegmentSortingTest.kt`)*:
  • Given Segment A with `remainingDistance_raw = 995.0` (`remainingDistance = "995 m"`) and Segment B with `remainingDistance_raw = 2400.0` (`remainingDistance = "2.40 km"`), both in `LiveSegmentStatus.ON_SEGMENT`.
  • Verify that sorting places Segment A first.
  • Verify that sorting is identical regardless of whether imperial or metric distance formatting is configured.
  2. *Approaching Segment Crossing Unit Boundary Sorting Tests (`LiveSegmentSortingTest.kt`)*:
  • Given Segment A with `distanceToStart_raw = 950.0` (`distanceToStart = "950 m"`) and Segment B with `distanceToStart_raw = 1200.0` (`distanceToStart = "1.20 km"`), both in `LiveSegmentStatus.APPROACHING`.
  • Verify that sorting places Segment A first.
  3. *Fallback Offset & Status Weight Priority Preservation Tests (`LiveSegmentSortingTest.kt`)*:
  • Verify that `LiveSegmentStatus.ON_SEGMENT_CLOSE_TO_FINISH` (weight 4) outranks `ON_SEGMENT` (weight 3).
  • Verify that finished segments tie-break by `segmentOffset_raw`.
  4. *Localization Parity Audit (`TranslationParityTest.kt`)*:
  • Verify 100% parity across all 9 supported locales (0 new UI strings introduced).
  5. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with zero regressions.

### TST-ARC-001: **Safe Enum Deserialization, Observability & Architecture Guard Verification Suite** [Verified]
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Review
- **Requirement(s)**: `REQ-ARC-001`
- **Component**: **Safe Enum Deserialization, Observability & Architecture Guard Verification Suite**
- **Jira**: ``ATT-3562``
- **Test Description**:
  1. *Central Utility Unit Tests (`EnumUtilsTest.kt`)*:
  • Verify valid enum names match corresponding constants.
  • Verify case-sensitive exact match vs whitespace trimming.
  • Verify null input returns fallback or null without exception.
  • Verify empty string and whitespace-only string returns fallback or null.
  • Verify unrecognized enum constant string returns fallback or null.
  • Verify Kotlin reified extension functions `safeEnumValueOf` and `safeEnumValueOfOrNull`.
  2. *Tier A (Hardware & Lifecycle) Validation Tests (`DeviceManagerTest.kt` / `TrackerServiceTest.kt`)*:
  • Verify invalid `Protocol` extra does not trigger BLE/ANT search and logs error.
  • Verify invalid `DeviceType` extra does not trigger BLE/ANT search and logs error.
  • Verify invalid `StartType` in `TrackerService` does not crash service lifecycle.
  3. *Tier B (Database Multi-Record Queries) Deserialization Tests (`WorkoutSummariesDatabaseTest.kt`)*:
  • Verify corrupted or null `SensorType` or `ExtremaType` rows are skipped without throwing NPE or IAE.
  • Verify valid extrema rows in the same cursor are successfully parsed and returned.
  4. *Tier C (Preferences & Configuration) Deserialization Tests (`PeriodSummariesDatabaseTest.kt`, `EquipmentDbTest.kt`)*:
  • Verify null or invalid `BSportType` / `PeriodType` falls back to default values with warning log.
  5. *Static Architectural Audit Guard (`SafeEnumDeserializationAuditTest.kt`)*:
  • Run source code AST / regex scan over all Java and Kotlin source files in `app/src/main`.
  • Assert that no unshielded `valueOf(cursor.getString...)`, `valueOf(intent.getStringExtra...)`, `valueOf(arguments.getString...)`, or `valueOf(sharedPreferences.getString...)` calls exist.
  6. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% pass rate.

### TST-STB-016: **Lifecycle Safety, Guarded Service Launches & Idempotent Receiver Teardown Verification Suite** [Verified]
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit
- **Requirement(s)**: `REQ-STB-016`
- **Component**: **Lifecycle Safety, Guarded Service Launches & Idempotent Receiver Teardown Verification Suite**
- **Jira**: ``ATT-3563``
- **Test Description**:
  1. *Central Utility Unit Tests (`LifecycleUtilsTest.kt`)*:
  • Verify `safeStartService` returns `ComponentName` on successful service start.
  • Verify `safeStartService` returns `null` and logs `Log.w` when `Context.startService` throws `IllegalStateException` (`BackgroundServiceStartNotAllowedException`), `ForegroundServiceStartNotAllowedException`, or `SecurityException`.
  • Verify `safeStartService` returns `null` when context or intent is null.
  • Verify `safeUnregisterReceiver` returns `true` on successful receiver unregistration.
  • Verify `safeUnregisterReceiver` returns `false` and logs `Log.w` when `Context.unregisterReceiver` throws `IllegalArgumentException: Receiver not registered` or `IllegalStateException`.
  • Verify `safeUnregisterReceiver` returns `false` when context or receiver is null.
  • Verify Kotlin extension functions `Context?.safeStartService(...)` and `Context?.safeUnregisterReceiver(...)`.
  • Verify logging resilience against unmocked `android.util.Log` in local JVM test runners.
  2. *Static Architecture Audit Guard (`LifecycleServiceReceiverSafetyAuditTest.kt`)*:
  • Run source code scan over all Java and Kotlin source files in `app/src/main` (excluding `LifecycleUtils.kt`).
  • Assert zero unshielded `unregisterReceiver(` calls.
  • Assert zero unshielded `startService(` calls.
  3. *CLI Linter Verification (`tools/lint_lifecycle_safety.py`)*:
  • Execute python linter script asserting zero lifecycle violations.
  4. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with zero regressions across all 2900+ tests.

### TST-TOOL-005: **BLE Cycling Power Simulator Pedal Power Balance Verification Suite** [Verified]
- **Priority**: Medium
- **Type**: Non-Functional
- **Verification Method**: Unit
- **Requirement(s)**: `REQ-TOOL-005`
- **Component**: **BLE Cycling Power Simulator Pedal Power Balance Verification Suite**
- **Jira**: ``ATT-3678``
- **Test Description**:
  1. *Cycling Power Feature Bitmask Unit Tests (`TestBlePowerSimulator` in `tools/test_ble_power_simulator.py`)*:
  • Verify `CyclingPowerFeatureCharacteristic.value` includes bit 0 (`0x01`, Pedal Power Balance Supported) and bit 3 (`0x08`, Crank Revolution Data Supported), yielding `0x09`.
  • Verify `ReadValue()` returns byte array `[0x09, 0x00, 0x00, 0x00]`.
  2. *Measurement Payload Encoding & Offset Tests (`TestBlePowerSimulator` in `tools/test_ble_power_simulator.py`)*:
  • Verify default payload: `pedal_balance = 50.0` produces `flags = 0x0021` (bits 0 and 5 set), total payload length 9 bytes, byte 4 raw balance = 100 ($50.0\%$), followed by 4 crank revolution data bytes.
  • Verify asymmetry tiers: `pedal_balance = 54.0` (Mild Asymmetry Amber) encodes raw balance 108; `pedal_balance = 40.0` (Severe Asymmetry Red) encodes raw balance 80; `pedal_balance = 48.0` (Balanced Green) encodes raw balance 96.
  • Verify clamping: `pedal_balance = -5.0` clamps to raw balance 0 ($0.0\%$); `pedal_balance = 105.0` clamps to raw balance 200 ($100.0\%$).
  • Verify balance disabled (`pedal_balance = None`): flags = `0x0020` (bit 0 cleared), payload length 8 bytes, no balance byte present.
  3. *Interactive CLI Command Parser Tests (`TestBlePowerSimulator` in `tools/test_ble_power_simulator.py`)*:
  • Verify parsing `b 50`, `b50`, `b 48`, `b 54`, `b 60` sets `pedal_balance` accordingly.
  • Verify parsing `b off` sets `pedal_balance = None`.
  • Verify invalid command rejection (`b abc`, `b -10`, `b 150`) leaves state unchanged without throwing exceptions.
  4. *Clean-Room Full Suite Regression Execution*:
  • Execute `python3 -m unittest tools/test_ble_power_simulator.py` and `./gradlew testDebugUnitTest` asserting 100% test pass rate.

### TST-TOOL-006: **Variable-Interval TCX Interpolation, Elapsed-Time Playback Stepping & Workout-Relative ADB GPS Timestamp Verification Suite** [Verified]
- **Priority**: Medium
- **Type**: Non-Functional
- **Verification Method**: Unit
- **Requirement(s)**: `REQ-TOOL-006`
- **Component**: **Variable-Interval TCX Interpolation, Elapsed-Time Playback Stepping & Workout-Relative ADB GPS Timestamp Verification Suite**
- **Jira**: ``ATT-3927``
- **Test Description**:
  1. *Variable-Interval TCX Parsing, Coordinate Interpolation & Fallback Speed Unit Tests (`TestTcxParser` in `tools/test_replay_workout.py`)*:
  • Verify `TcxParser.parse_file()` retains intermediate `<Trackpoint>` elements lacking `<Position>` when valid `<Position>` fixes exist in the file, preserving $1\text{ Hz}$ `hr_bpm`, `cadence_rpm`, and `power_watts` telemetry.
  • Verify `lat`, `lon`, and `ele` are linearly interpolated by `time_sec` across interior `<Position>` gaps.
  • Verify `speed_mps = step_d / dt` is calculated automatically when `<Speed>` extensions are absent from `<Trackpoint>` elements, and verify that duplicate timestamps (`dt < 0.001`) safely set `speed_mps = 0.0` without `ZeroDivisionError`, `NaN`, or `Inf`.
  2. *Elapsed-Time Playback Stepping Unit Tests (`TestReplayEngine` in `tools/test_replay_workout.py`)*:
  • Verify `ReplayEngine.step(dt_sec=1.0)` advances `elapsed_time_sec += 1.0 * speed_multiplier` and selects the corresponding `current_index` by `time_sec` rather than incrementing array index blindly across variable-interval trackpoints.
  • Verify `ReplayEngine.step()` cleanly clamps `current_index` to `len(points) - 1` and transitions to `PAUSED` at the final trackpoint without index-out-of-bounds errors.
  • Verify `seek_ratio()`, `seek_time()`, and `stop()` synchronize `elapsed_time_sec` with `points[current_index].time_sec`.
  3. *Replay Session Epoch Workout-Relative ADB GPS Timestamp Unit Tests (`TestAdbGpsInjector` in `tools/test_replay_workout.py`)*:
  • Verify `AdbGpsInjector.inject_location(..., time_sec=t)` computes `timestamp_ms = base_epoch_ms + int(round(t * 1000.0))` and passes `--time <timestamp_ms>` to `cmd location providers set-test-provider-location`.
  • Verify consecutive forward fixes separated by $\Delta t_{\text{workout}} = 5.0\text{ s}$ produce $\Delta\text{timestamp\_ms} = 5000\text{ ms}$ regardless of replay speed multiplier ($1\times, 2\times, 5\times, 10\times$), and verify `reset_epoch(time_sec)` resets `base_epoch_ms` to maintain the exact relative `time_sec` offset from the new position.
  4. *Clean-Room Full Suite Regression Execution*:
  • Execute `python3 -m unittest tools/test_replay_workout.py` and `./gradlew testDebugUnitTest` asserting 100% test pass rate with zero regressions.

### TST-TOOL-007: **Simulated Ground Speed Injection & Defensive AOSP CLI Fallback Verification Suite** [Verified]
- **Priority**: Medium
- **Type**: Non-Functional
- **Verification Method**: Unit
- **Requirement(s)**: `REQ-TOOL-007`
- **Component**: **Simulated Ground Speed Injection & Defensive AOSP CLI Fallback Verification Suite**
- **Jira**: ``ATT-6684``
- **Test Description**:
  1. *Unit Test: Command Formatting with Speed (`test_build_inject_cmd_with_speed` in `tools/test_replay_workout.py`)*:
  • Verify `AdbGpsInjector.build_inject_cmd()` formats `--speed {speed_mps:.2f}` when `speed_mps >= 0.0`.
  2. *Unit Test: Command Formatting Backward Compatibility (`test_build_inject_cmd_without_speed` in `tools/test_replay_workout.py`)*:
  • Verify `AdbGpsInjector.build_inject_cmd()` preserves standard Android 14+ command syntax without `--speed` when `speed_mps is None` or `speed_mps < 0.0`.
  3. *Unit Test: Asynchronous Queue Tuple Handling (`test_async_queue_injection_with_speed` in `tools/test_replay_workout.py`)*:
  • Verify `AdbGpsInjector.inject_location()` queues 4-tuple `(lat, lon, timestamp_ms, speed_mps)` and `_worker_loop` accepts 2-tuples, 3-tuples, and 4-tuples without error.
  4. *Unit Test: Defensive Auto-Probing Fallback on Unsupported Option (`test_speed_fallback_on_unsupported_option` in `tools/test_replay_workout.py`)*:
  • Mock ADB subprocess returning `java.lang.IllegalArgumentException: Unknown option: --speed` with exit code 255.
  • Verify `_supports_speed_flag` transitions to `False`, warning notice is logged once, and retry injection proceeds without `--speed`.
  5. *Localization Parity Audit*:
  • Verify developer tooling changes introduce zero missing strings or format specifier mismatches (no Android UI strings affected).
  6. *Clean-Room Full Suite Regression Execution*:
  • Execute `python3 -m unittest tools/test_replay_workout.py` asserting 100% test pass rate across all 39+ tests.

### TST-BLD-001: **R8 Release Build Minification, ProGuard Keep Rules & DEX Obfuscation Verification Suite** [Verified]
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit
- **Requirement(s)**: `REQ-BLD-001`
- **Component**: **R8 Release Build Minification, ProGuard Keep Rules & DEX Obfuscation Verification Suite**
- **Jira**: ``ATT-3770``
- **Test Description**:
  1. *Release Build Assembly & Mapping Generation Test (`TST-BLD-001.1`)*:
  • Execute `./gradlew :app:assembleRelease` and verify the build completes successfully with zero compilation or R8 shrinking errors.
  • Verify that `app/build/outputs/mapping/release/mapping.txt` is generated with non-zero size and is bundled into the release AAB/APK.
  2. *DEX Obfuscation Ratio Threshold Verification (`TST-BLD-001.2`)*:
  • Inspect classes in release DEX output; verify the percentage of obfuscated classes is $\ge 25\%$ (target $> 50\%$), satisfying Google Play Console Android vitals threshold.
  3. *ProGuard Rules Contract & Source Verification (`TST-BLD-001.3`, `ProGuardRulesContractTest.kt`)*:
  • Verify `app/proguard-rules.pro` does NOT contain blanket `-keep class androidx.compose.**`.
  • Verify `app/proguard-rules.pro` contains explicit keep rules for domain enums (`com.atrainingtracker.**`), Garmin FIT SDK (`com.garmin.fit.**`), ANT+ Plugin (`com.dsi.ant.**`), PebbleKit (`com.getpebble.android.kit.**`), Dropbox SDK (`com.dropbox.core.**`), and Kotlinx Serialization descriptors.
  4. *Domain Enum SQLite & DataStore Reflection Safety Test (`TST-BLD-001.4`, `ProGuardRulesContractTest.kt`)*:
  • Verify that all domain enums (`SensorType`, `BSportType`, `DeviceType`, `MyUnits`, `ZoneType`, `FilterType`, `FileFormat`, `ExportStatus`, `EquipmentType`) resolve their names via `Enum.valueOf` without throwing `IllegalArgumentException`.
  5. *Clean-Room Regression Suite Execution (`TST-BLD-001.5`)*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with zero regressions.
  6. *9-Language Localization Parity (`TST-BLD-001.6`)*:
  • Verify `TranslationParityTest` passes cleanly across all 9 languages.

### TST-BLD-002: **Release CI Workflow Automated Test Gating, Manifest Hygiene & Lint Baseline Verification Suite** [Verified]
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit
- **Requirement(s)**: `REQ-BLD-002`
- **Component**: **Release CI Workflow Automated Test Gating, Manifest Hygiene & Lint Baseline Verification Suite**
- **Jira**: ``ATT-4025``
- **Test Description**:
  1. *CI Workflow Release Test Gating Contract Test (`TST-BLD-002.1`, `ReleaseWorkflowContractTest.kt`)*:
  • Verify that `.github/workflows/android-release.yml` invokes `./gradlew :app:testDebugUnitTest` prior to `:app:assembleRelease` and `:app:bundleRelease`.
  • Verify that release artifact assembly and release asset upload are sequenced strictly after unit tests.
  2. *Manifest MissingClass Hygiene Verification (`TST-BLD-002.2`)*:
  • Verify `app/src/main/AndroidManifest.xml` does not declare non-existent component classes, specifically confirming the excision of orphaned `StravaSegmentsIntentService` to eliminate `MissingClass` lint errors.
  3. *Build Configuration Lint Suppressions Documentation Audit (`TST-BLD-002.3`)*:
  • Verify `app/build.gradle` retains `checkReleaseBuilds = true` to run `lintVitalRelease`.
  • Verify `app/build.gradle` contains clear technical rationale comments for active suppressions: `DuplicatePlatformClasses`, `MissingTranslation`, and `Range`.
  4. *Lint Baseline Synchronization & Burn-Down Verification (`TST-BLD-002.4`)*:
  • Verify `app/lint-baseline.xml` is synchronized with current AGP release variant and contains no false-positive missing class errors.
  5. *Clean-Room Full Suite Regression Execution & Translation Parity (`TST-BLD-002.5`)*:
  • Execute `./gradlew testDebugUnitTest` and `TranslationParityTest` asserting 100% test pass rate with zero regressions across all 9 supported locales.

### TST-SEC-038: **Zero-Leak Logging, PII & Credential Sanitization, Build-Scoped Debug Flags, and Proactive Token Invalidation Verification Suite** [Verified]
- **Priority**: Critical
- **Type**: Non-Functional
- **Verification Method**: Review
- **Requirement(s)**: `REQ-SEC-041`, `REQ-EXT-009`
- **Component**: **Zero-Leak Logging, PII & Credential Sanitization, Build-Scoped Debug Flags, and Proactive Token Invalidation Verification Suite**
- **Jira**: ``ATT-3983``
- **Test Description**:
  1. *OAuth Credential Sanitization Test (`TST-SEC-038.1`, `LogcatPrivacySecurityTest.kt`)*:
  • Invoke `StravaHelper.storeJSONData` and token refresh with mock JSON containing `access_token` and `refresh_token`.
  • Verify that zero tokens or full JSON response bodies appear in log output.
  2. *Location & GPS Polyline PII Sanitization Test (`TST-SEC-038.2`, `LogcatPrivacySecurityTest.kt`)*:
  • Update workout in `WorkoutRepository` with dummy `mapPolyline` and coordinate data.
  • Verify that zero polyline strings or GPS points appear in log output.
  3. *HTTP Payload Sanitization Test (`TST-SEC-038.3`, `LogcatPrivacySecurityTest.kt`)*:
  • Mock `StravaUploader` and `StravaEquipmentSynchronizeThread` responses.
  • Verify raw response payloads are not emitted into logs.
  4. *Source Tree Static Lint Guard Test (`TST-SEC-038.4`, `LogcatPrivacySecurityTest.kt`)*:
  • Scan all Java and Kotlin source files in `app/src/main/`.
  • Assert zero un-commented occurrences of `DEBUG = true` or `DEBUG=true`.
  • Run AST/regex pattern verification ensuring no `Log.*` calls reference `mapPolyline`, `accessToken`, or `refreshToken`.
  5. *Mandatory Programmatic Credential Invalidation on Upgrade Test (`TST-SEC-038.5`, `LogcatPrivacySecurityTest.kt`)*:
  • Simulate an existing user with active Strava credentials upgrading to V4.9.40.
  • Verify `TrainingApplication` / `StravaHelper` executes remote deauthorization, purges stored credentials, and marks the migration key.
  6. *Clean-Room Full Suite Regression Execution (`TST-SEC-038.6`)*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with zero regressions.

### TST-SEC-039: **BuildConfig Secret Reflection Audit, Keystore Exclusion & Defaults Configuration Verification Suite** [Verified]
- **Priority**: Critical
- **Type**: Non-Functional
- **Verification Method**: Review
- **Requirement(s)**: `REQ-SEC-042`
- **Component**: **BuildConfig Secret Reflection Audit, Keystore Exclusion & Defaults Configuration Verification Suite**
- **Jira**: ``ATT-4004``
- **Test Description**:
  1. *Required BuildConfig Fields Reflection & Compilation Integrity Test (`TST-SEC-039.1`, `BuildConfigSecretsContractTest.kt`)*:
  • Inspect `BuildConfig::class.java.declaredFields` via reflection.
  • Assert required fields exist: `MAPS_API_KEY`, `STRAVA_CLIENT_ID`, `STRAVA_CLIENT_SECRET`, `DROPBOX_APP_KEY`.
  • Assert each required field has a non-null, non-blank string value.
  • Assert project compilation (`./gradlew compileDebugSources compileReleaseSources`) succeeds cleanly with zero missing symbol errors after pruning dead keys.
  2. *Forbidden Dead Secrets Exclusion Test (`TST-SEC-039.2`, `BuildConfigSecretsContractTest.kt`)*:
  • Inspect all field names in `BuildConfig::class.java.declaredFields`.
  • Assert that no field matches `RUNKEEPER_.*`, `TRAININGPEAKS_.*`, or `DROPBOX_APP_SECRET`.
  3. *Keystore and Signing Credential Exclusion Test (`TST-SEC-039.3`, `BuildConfigSecretsContractTest.kt`)*:
  • Inspect all field names in `BuildConfig::class.java.declaredFields`.
  • Assert that no field matches `(?i).*key(alias

### TST-STB-017: **MainActivity Launch Intent Lifecycle Sanitization & Safe Resumption Guards Verification Suite** [Verified]
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit
- **Requirement(s)**: `REQ-STB-017`, `REQ-STB-003`, `REQ-UI-124`
- **Component**: **MainActivity Launch Intent Lifecycle Sanitization & Safe Resumption Guards Verification Suite**
- **Jira**: ``ATT-4001``
- **Test Description**:
  1. *Cold Start Intent Processing Unit Tests (`MainActivityIntentLifecycleTest.kt`)*:
  • Verify that when `savedInstanceState == null`, `handleIntent` processes `EXTRA_RESUME_INTERRUPTED_WORKOUT`, removes `EXTRA_RESUME_INTERRUPTED_WORKOUT`, `SELECTED_FRAGMENT`, and `EXTRA_DRAWER_ITEM_ID`, and calls `chooseResume()`.
  • Verify that when `savedInstanceState == null`, `handleIntent` processes `EXTRA_DRAWER_ITEM_ID`, updates `mSelectedFragmentId`, and removes `EXTRA_DRAWER_ITEM_ID`.
  • Verify that when `savedInstanceState == null`, `handleIntent` processes `SELECTED_FRAGMENT`, updates `mSelectedFragmentId`, and removes `SELECTED_FRAGMENT`.
  • Verify that when `savedInstanceState == null`, `handleIntent` processes `EXTRA_FILTER_CRITICAL_BATTERY` and removes `EXTRA_FILTER_CRITICAL_BATTERY`.
  2. *Activity Recreation Isolation Unit Tests (`MainActivityIntentLifecycleTest.kt`)*:
  • Simulate activity recreation with non-null `savedInstanceState` containing a restored fragment ID (e.g. `R.id.drawer_workouts`) while the activity intent contains `EXTRA_RESUME_INTERRUPTED_WORKOUT = true` or `EXTRA_DRAWER_ITEM_ID = R.id.drawer_start_tracking`.
  • Verify that `handleIntent()` is NOT invoked, the restored fragment ID is preserved, and `chooseResume()` is NOT called.
  3. *Runtime New Intent Handling Unit Tests (`MainActivityIntentLifecycleTest.kt`)*:
  • Simulate `onNewIntent` delivery while activity is alive.
  • Verify `setIntent(intent)` is updated and new extras are handled and consumed.
  4. *Defensive `chooseResume()` Guard Unit Tests (`MainActivityIntentLifecycleTest.kt`)*:
  • Verify that if `TrainingApplication.isTracking()` is true, `chooseResume()` returns immediately without dispatching `REQUEST_START_TRACKING`.
  • Verify that if `WorkoutSummariesDatabaseManager.hasUnfinishedWorkout()` is false, `chooseResume()` returns immediately without dispatching `REQUEST_START_TRACKING`.
  • Verify that if not tracking and an unfinished workout exists, `chooseResume()` sets `resumeFromCrash = true` and broadcasts `REQUEST_START_TRACKING`.
  5. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` verifying 100% pass rate across the full test suite.

### TST-STB-018: **WorkoutRepository Concurrency, Conflated Dirty-Flag Reloads, Stale Snapshot Defense & Exception Shielding Verification Suite** [Verified]
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit
- **Requirement(s)**: `REQ-STB-018`, `REQ-STB-013`
- **Component**: **WorkoutRepository Concurrency, Conflated Dirty-Flag Reloads, Stale Snapshot Defense & Exception Shielding Verification Suite**
- **Jira**: `ATT-4002`, `ATT-5565`
- **Test Description**:
  1. *Conflated Per-Workout Reload & Lifecycle Callback Unit Tests (`TST-STB-018.1`, `WorkoutRepositoryConcurrencyTest.kt`)*:
  • Verify concurrent reload requests during an active reload mark the workout dirty and trigger a sequential re-query.
  • Verify transitions from `finished = false` to `true` trigger `PeriodsRepository.onWorkoutFinished` and `WorkoutClusterEngine.onWorkoutFinished`.
  2. *Concurrent Delete-During-Stream Stale Overwrite Defense Unit Tests (`TST-STB-018.2`, `WorkoutRepositoryConcurrencyTest.kt`)*:
  • Verify workouts deleted via `deleteWorkout(id)` during progressive history streaming are not resurrected by subsequent progressive or terminal emissions.
  3. *Skipped Row Recovery & Guaranteed Terminal Batch Emission Unit Tests (`TST-STB-018.3`, `WorkoutRepositoryConcurrencyTest.kt`)*:
  • Verify exceptions during row snapshot extraction or mapping are logged and skipped without crashing the loader.
  • Verify unconditional terminal emission publishes all valid loaded workouts including the final partial batch.
  4. *Atomic Loading Guard Concurrency Protection Unit Tests (`TST-STB-018.4`, `WorkoutRepositoryConcurrencyTest.kt`)*:
  • Verify concurrent invocations of `loadAllWorkouts()` return immediately without spawning parallel cursor traversals.
  5. *Pure Functional CAS Transformation Contract Tests (`TST-STB-018.5`, `WorkoutRepositoryConcurrencyTest.kt`)*:
  • Verify `updateWorkoutInMemory` and `saveWorkout` execute zero coroutines, database queries, or external mutations inside `_allWorkouts.update`.
  • Verify un-cached workouts trigger background fetch outside CAS lambda.
  6. *Defensive Deletion Reporting & Rule 74 Completion Ordering Unit Tests (`TST-STB-018.6`, `WorkoutRepositoryConcurrencyTest.kt`)*:
  • Verify `deleteWorkout` posts `Pair(id, false)` when deletion returns false or throws an exception.
  • Verify `_deletionProgress` unconditionally resets to `DeletionProgress.Idle` prior to posting `deleteFinishedEvent`, preventing race conditions for completion event observers.
  7. *9-Language Localization Parity (`TST-STB-018.7`, `TranslationParityTest.kt`)*:
  • Verify 100% translation parity and matching string specifiers across all 9 supported locales.
  8. *Clean-Room Regression Suite Execution (`TST-STB-018.8`)*:
  • Run targeted unit tests and clean-room test suite verifying 100% pass rate.

### TST-STB-019: **MainActivity Service Binding Safety, Lifecycle Scoping, In-Ride Battery State Retention & Off-Main-Thread DB Verification Suite** [Verified]
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit
- **Requirement(s)**: `REQ-STB-019`, `REQ-UI-356`
- **Component**: **MainActivity Service Binding Safety, Lifecycle Scoping, In-Ride Battery State Retention & Off-Main-Thread DB Verification Suite**
- **Jira**: ``ATT-4020``
- **Test Description**:
  1. *Service Connection & Runnable Cleanup Unit Tests (`TST-STB-019.1`, `MainActivityLifecycleTest.kt`)*:
  • Verify that `disconnectFromBANALService()` is invoked during `onDestroy()` even when `isChangingConfigurations == true`.
  • Verify that `mHandler.removeCallbacks(mDisconnectFromBANALServiceRunnable)` is called in `onDestroy()`, leaving zero pending runnables.
  2. *Structured Coroutine Scoping Contract Tests (`TST-STB-019.2`, `MainActivityLifecycleTest.kt`)*:
  • Verify battery checks and in-ride monitoring jobs are bound to `lifecycleScope`, cancelling cleanly when the Activity lifecycle is destroyed.
  3. *In-Ride Battery State Retention & Screen-Off Tracking Tests (`TST-STB-019.3`, `MainActivityLifecycleTest.kt`)*:
  • Verify alerted sensor IDs (`mInRideAlertedSensorIds`, `mInRideCriticalAlertedSensorIds`) survive Activity configuration changes / recreation so subsequent telemetry does not re-fire duplicate snackbars.
  • Verify battery monitoring does not stop on `onPause()` while `TrainingApplication.isTracking()` is true.
  4. *Off-Main-Thread Database Access Unit Tests (`TST-STB-019.4`, `MainActivityLifecycleTest.kt`)*:
  • Verify `onServiceConnected` creates filters asynchronously without blocking the main thread.
  • Verify `checkUnfinishedWorkout()` and dialog actions execute `hasUnfinishedWorkout()` and `discardOrFinishUnfinishedWorkout()` off the main thread on `Dispatchers.IO`.
  • Verify `checkGpsEnabledIfPermitted()` does not block the main thread with synchronous database queries.
  5. *Google Play Services Dialog Lifecycle Tests (`TST-STB-019.5`, `MainActivityLifecycleTest.kt`)*:
  • Verify that when the Play Services error dialog is displayed, it is safely dismissed on `onDestroy()` to prevent `WindowLeaked`.
  6. *9-Language Localization & Specifier Parity Audit (`TST-STB-019.6`, `TranslationParityTest.kt`)*:
  • Verify 100% translation parity and matching format specifiers across all 9 supported locales.
  7. *Clean-Room Full Suite Regression Execution (`TST-STB-019.7`)*:
  • Execute targeted tests and `./gradlew testDebugUnitTest` verifying 100% pass rate across the full test suite.

### TST-TST-001: **Deterministic JVM Test State Isolation, Production Catch Excision & Exporter Test Coverage Verification Suite** [Verified]
- **Priority**: Medium
- **Type**: Non-Functional
- **Verification Method**: Unit
- **Requirement(s)**: `REQ-TST-001`
- **Component**: **Deterministic JVM Test State Isolation, Production Catch Excision & Exporter Test Coverage Verification Suite**
- **Jira**: ``ATT-4024``
- **Test Description**:
  1. *Locale and TimeZone Lifecycle Restoration Tests (`TST-TST-001.1`)*:
  • Verify that `IntensityFactorFormatterTest`, `BatteryRemainingTimeFormatterTest`, `BatteryDeviceTest`, `AdvancedTuningAccordionTest`, and `PeriodsAggregationTest` capture and restore original `Locale.getDefault()` and `TimeZone.getDefault()` in `@After` blocks, preventing JVM state pollution across subsequent tests.
  2. *Battery Test Strict Assertions (`TST-TST-001.2`)*:
  • Verify `BatteryRemainingTimeFormatterTest` and `BatteryDeviceTest` use strict `assertEquals("Charging", ...)` and deterministic expected value assertions, eliminating bare `assert` and tautological `

### TST-BLD-003: **Manifest Component Security, GPX Intent Filter Hardening & Permission Invariants Verification Suite** [Verified]
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit
- **Requirement(s)**: `REQ-BLD-003`
- **Component**: **Manifest Component Security, GPX Intent Filter Hardening & Permission Invariants Verification Suite**
- **Jira**: ``ATT-4027``
- **Test Description**:
  1. *Manifest Security & Encapsulation Contract Test (`TST-BLD-003.1`, `ManifestSecurityContractTest.kt`)*:
  • Verify `ConfigPebbleViewsActivity` has `android:exported="false"`.
  • Verify `GpxImportActivity` does not specify `android:host="*"` or `android:scheme="file"`.
  • Verify `ACCESS_BACKGROUND_LOCATION` is declared in `<uses-permission>`.
  • Verify `application` declares `android:largeHeap="true"`.
  2. *GPX Intent Filter MIME & Path Specification Verification (`TST-BLD-003.2`, `ManifestSecurityContractTest.kt`)*:
  • Verify `GpxImportActivity` intent filters support `content://` scheme with `application/gpx+xml`, `application/xml`, and `text/xml`.
  • Verify `application/octet-stream` is scoped with `.gpx` path extension patterns.
  3. *Manifest Background Location Rationale Comment Audit (`TST-BLD-003.3`)*:
  • Verify `AndroidManifest.xml` does not contain misleading "Not necessary" comments regarding `ACCESS_BACKGROUND_LOCATION` and correctly documents Android 14+ FGS `START_STICKY` recovery contract (`REQ-TRK-015`).
  4. *Clean-Room Full Suite Regression Execution & Translation Parity (`TST-BLD-003.4`)*:
  • Execute `./gradlew testDebugUnitTest` and `TranslationParityTest` asserting 100% pass rate with zero regressions across all 9 supported locales.

### TST-TST-002: **Fragile UI Source-Grep Contract Test Modernization & Architectural Guard Preservation Verification Suite** [Verified]
- **Priority**: Medium
- **Type**: Non-Functional
- **Verification Method**: Unit
- **Requirement(s)**: `REQ-TST-002`
- **Component**: **Fragile UI Source-Grep Contract Test Modernization & Architectural Guard Preservation Verification Suite**
- **Jira**: ``ATT-4028``
- **Test Description**:
  1. *Return Navigation HUD Contract Modernization Tests (`TST-TST-002.1`, `ReturnNavigationHudContractTest.kt`)*:
  • Verify that source-reading string assertions (`content.contains("RoundedCornerShape(12.dp)")`, `"TTColor.RouteSelected"`, emoji checks) are excised.
  • Verify that `ReturnNavigationState` model assertions thoroughly validate formatting, unit conversions, and metric visibility across all states.
  2. *Route Tabbed Clean Layout Reflection & Decoupling Tests (`TST-TST-002.2`, `RouteTabbedCleanLayoutTest.kt`)*:
  • Verify that file-grep substring assertions are excised from `RouteTabbedScreen`, `RouteList`, and `RouteItem` test methods.
  • Verify that dead class excision and decoupled properties are asserted via programmatic reflection (`Class.forName`, `declaredFields`, `declaredMethods`) and file existence checks.
  3. *Turn Direction Icon XML DOM & Geometric Milestone Tests (`TST-TST-002.3`, `TurnDirectionIconContractTest.kt`)*:
  • Verify that raw path string greps (`"V20"`, `"h-2"`, `"M6,20"`, `"V20z"`) are excised.
  • Verify that baseline grounding (y=20), stem width (2dp), margin clearance, and reflection symmetry are verified via XML DOM parsing and `evaluateAbsoluteMilestones` mathematical vertex analysis.
  4. *Architectural Governance Guard Invariance Audit (`TST-TST-002.4`)*:
  • Verify that structural policy guards (`TryWithResourcesCursorAuditTest`, `ManifestSecurityContractTest`, `TranslationParityTest`) pass with 100% success.
  5. *9-Language Localization Audit (`TST-TST-002.5`, `TranslationParityTest.kt`)*:
  • Verify 100% translation parity across all 9 locales via `TranslationParityTest` (0 new user-facing strings).
  6. *Clean-Room Full Suite Regression Execution (`TST-TST-002.6`)*:
  • Execute `./gradlew testDebugUnitTest` verifying 100% pass rate across the full test suite.

### TST-STR-019: **Locale Hygiene, Translation Completeness & ExtraTranslation De-duplication Verification Suite** [Verified]
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit
- **Requirement(s)**: `REQ-LOC-002`, `REQ-LOC-001`
- **Component**: **Locale Hygiene, Translation Completeness & ExtraTranslation De-duplication Verification Suite**
- **Jira**: ``ATT-4029``
- **Test Description**:
  1. *Translation Completeness Audit (`TST-STR-019.1`)*:
  • Verify `values-de/strings.xml` contains German translations for `pebble_build_in`, `tss`, and `pedal_power_balance`.
  • Verify `values-nl/strings.xml` contains Dutch translations for `zone_2_label`, `zone_3_label`, and `zone_5_label`.
  • Verify `values-pl/strings.xml` contains Polish translations for `exporting_to_file*` (4 keys).
  • Verify `values-{es,fr,it,ja,nl,pl,pt}/strings.xml` contain localized translations for `backup_restore`.
  2. *ExtraTranslation Non-Translatable Redundancy Audit (`TST-STR-019.2`, `TranslationParityTest.kt`)*:
  • Execute `testNoExtraTranslationsInLocales` asserting zero keys marked `translatable="false"` in base `values/strings.xml` exist in any `values-<locale>/strings.xml` file.
  3. *Redundant Array Excision Audit (`TST-STR-019.3`)*:
  • Verify redundant string arrays in `values-de/arrays.xml` and `values-pl/arrays.xml` referencing `@string/...` are removed, and array resolution functions identically from base.
  4. *9-Language Localization Parity Test (`TST-STR-019.4`, `TranslationParityTest.kt`)*:
  • Verify all 9 locales pass 100% string key parity and format specifier parity under `TranslationParityTest.kt`.
  5. *Clean-Room Full Suite Regression Execution (`TST-STR-019.5`)*:
  • Execute `./gradlew testDebugUnitTest` verifying 100% pass rate across the full project test suite.

### TST-ARC-002: **Exception Handling Hygiene, Fatal Error Escalation & Diagnostic Observability Verification Suite** [Verified]
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Review
- **Requirement(s)**: `REQ-ARC-002`
- **Component**: **Exception Handling Hygiene, Fatal Error Escalation & Diagnostic Observability Verification Suite**
- **Jira**: ``ATT-4030``
- **Test Description**:
  1. *Silent/Empty Catch Elimination & Warning Logging Unit Tests (`TST-ARC-002.1`, `ExceptionHandlingHygieneTest.kt`)*:
  • Verify `StravaUploader.parseTimeToEpochSeconds` handles invalid or corrupted date strings gracefully, returns `null` without throwing unhandled exceptions, and does not silently swallow errors.
  • Verify `WorkoutClusterDatabaseManager.deserializePreviewPaths` returns `emptyList()` without crashing when given malformed JSON.
  • Verify `FitFileWriter` handles invalid distance/timestamp values safely without silent swallows.
  2. *Fatal JVM Error Escalation & Non-Throwable Exception Scoping Tests (`TST-ARC-002.2`, `ExceptionHandlingHygieneTest.kt`)*:
  • Verify that `catch (Throwable)` is eradicated in audited operational paths in `PeriodsRepository`, `FitFileWriter`, and `MigrationEngine`.
  • Verify operational error catches use `catch (Exception)`, allowing `OutOfMemoryError` and coroutine cancellation (`CancellationException`) to propagate naturally.
  3. *Thread Interrupt Preservation Tests (`TST-ARC-002.3`, `ExceptionHandlingHygieneTest.kt`)*:
  • Verify that when `InterruptedException` is caught in `StravaUploader` backoff sleep, thread interrupt status is restored via `Thread.currentThread().interrupt()`.
  4. *Standard Android Logging & Safe Null Handling Tests (`TST-ARC-002.4`, `ExceptionHandlingHygieneTest.kt`)*:
  • Verify `MyBanalDebugHelper` logs via `Log.e(TAG, ...)` instead of `e.printStackTrace()`.
  • Verify `ForkNavigationRepository` eliminates unsafe force unwraps (`!!`) on `lastSearchPos` and handles null safely during rapid concurrency.
  5. *Structural Policy Guard Audit (`TST-ARC-002.5`, `ExceptionHandlingHygieneTest.kt`)*:
  • Verify source code AST/regex scan over audited production classes (`FitFileWriter`, `PeriodsRepository`, `StravaUploader`, `MyBanalDebugHelper`, `ForkNavigationRepository`, `WorkoutClusterDatabaseManager`, `MigrationEngine`) asserts zero `e.printStackTrace()` occurrences and zero `catch (Throwable)` in operational logic.
  6. *Clean-Room Full Suite Regression Execution & Translation Parity (`TST-ARC-002.6`)*:
  • Execute `./gradlew testDebugUnitTest` and `TranslationParityTest` asserting 100% pass rate with zero regressions across the codebase.

### TST-ARC-003: **Dead Code Elimination, Volatile Visibility & Lock-Screen API Modernization Verification Suite** [Verified]
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Review
- **Requirement(s)**: `REQ-ARC-003`
- **Component**: **Dead Code Elimination, Volatile Visibility & Lock-Screen API Modernization Verification Suite**
- **Jira**: ``ATT-4031``
- **Test Description**:
  1. *Dead Legacy Class Elimination Verification (`TST-ARC-003.1`, `DeadCodeEliminationContractTest.kt`)*:
  • Verify via programmatic reflection (`Class.forName` throwing `ClassNotFoundException`) and file existence that `SelectSportTypeDialog`, `MultiSelectionSpinner`, `DeviceIdAndNameArrayAdapter`, and `InstallANTShitDialog` are completely excised from the project.
  2. *TrainingApplication Dead Method Excision & Volatile State Verification (`TST-ARC-003.2`, `DeadCodeEliminationContractTest.kt`)*:
  • Verify via reflection that `TrainingApplication` no longer declares methods `todo()`, `setIsSegmentListUpdating()`, or `isSegmentListUpdating()`, nor field `mSegmentListUpdating`.
  • Verify via reflection that field `TrainingApplication.cResumeFromCrash` has `Modifier.isVolatile(field.modifiers) == true`.
  3. *MainActivityWithNavigation Dead Method Excision & Lock Screen API Verification (`TST-ARC-003.3`, `DeadCodeEliminationContractTest.kt`)*:
  • Verify via reflection that `MainActivityWithNavigation` no longer declares `checkBatteryOptimizations()` or `showInstallANTShitDialog()`.
  • Verify `NoUnlocking` window configuration branches on `Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1` calling `setShowWhenLocked(true/false)`.
  4. *AntStartupCheckContractTest Boundary Invariant Audit (`TST-ARC-003.4`, `AntStartupCheckContractTest.kt`)*:
  • Verify `AntStartupCheckContractTest` executes successfully with updated boundary string (`observeNavigationEvents`), maintaining 100% cold-start assertions.
  5. *Top-Level Compose Preview Private Visibility Audit (`TST-ARC-003.5`, `DeadCodeEliminationContractTest.kt`)*:
  • Verify that all top-level `@Preview` composables across UI screens are declared with `private` visibility.
  6. *Clean-Room Full Suite Regression Execution & Translation Parity (`TST-ARC-003.6`)*:
  • Execute `./gradlew testDebugUnitTest` and `TranslationParityTest` asserting 100% pass rate with zero regressions.

### TST-TST-003: **Deterministic Full-Suite Unit Test Scaffolding & Teardown Hygiene Verification Suite** [Verified]
- **Priority**: Medium
- **Type**: Non-Functional
- **Verification Method**: Unit
- **Requirement(s)**: `REQ-TST-003`, `REQ-TST-001`, `REQ-BLD-002`
- **Component**: **Deterministic Full-Suite Unit Test Scaffolding & Teardown Hygiene Verification Suite**
- **Jira**: ``ATT-4669``
- **Test Description**:
  1. *Contract Alignment Test (`TST-TST-003.1`, `HardcodedStringsAndUnitsContractTest.kt`)*:
  • Verify that obsolete `TrainingApplication todo method is marked as Deprecated` test is excised and `HardcodedStringsAndUnitsContractTest` passes 100%.
  2. *Static Mock Scaffolding Test (`TST-TST-003.2`, `AltitudeAwareClusterMatchingTest.kt`)*:
  • Verify `testLearnFromWorkout_UpdatesAltitudeExtremaCentroidsInDatabase` mocks `KnownLocationsDatabaseManager` and completes without unmocked Android Location exceptions.
  3. *DataStore & FilesDir Injection Test (`TST-TST-003.3`, `DevicesTabbedViewModelTest.kt`)*:
  • Verify `DevicesTabbedViewModel` instantiations across `DevicesTabbedViewModelTest` pass `mockSettingsDataStore` and stub `mockApplication.filesDir`, executing without NPE.
  4. *Dispatcher Injection & Synchronization Test (`TST-TST-003.4`, `BackupRestoreViewModelLocalRestoreTest.kt`)*:
  • Verify `BackupRestoreViewModel` accepts `ioDispatcher` via `@JvmOverloads` and `testRestoreFromLocalBackup_whenRestoreThrowsException_emitsError` passes `testDispatcher`, synchronizing deterministically via `advanceUntilIdle()`.
  5. *Coroutine Teardown Lifecycle Hygiene Test (`TST-TST-003.5`, `TrackingTabsViewModelInteropTest.kt`)*:
  • Verify all test-created `TrackingTabsViewModel` instances have `viewModelScope.cancel()` invoked in `@After`, preventing `UncaughtExceptionsBeforeTest` cross-test leakage.
  6. *Clean-Room Full Suite Regression Execution (`TST-TST-003.6`)*:
  • Execute `./gradlew testDebugUnitTest` verifying 100% pass rate across the full 3,840+ unit test suite.

### TST-ARC-004: **Android Framework Reflection Constructor Compatibility & Lifecycle ViewModel Factory Hygiene Verification Suite** [Verified]
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Review
- **Requirement(s)**: `REQ-ARC-004`, `REQ-ARC-003`
- **Component**: **Android Framework Reflection Constructor Compatibility & Lifecycle ViewModel Factory Hygiene Verification Suite**
- **Jira**: ``ATT-4667``
- **Test Description**:
  1. *WorkoutClustersViewModel Reflection Constructor Test (`TST-ARC-004.1`, `AndroidViewModelReflectionContractTest.kt`)*:
  • Verify via Java reflection (`WorkoutClustersViewModel::class.java.getConstructor(Application::class.java)`) that `WorkoutClustersViewModel` exposes a public 1-arg `(Application)` constructor.
  • Verify that instantiating `WorkoutClustersViewModel` via `getConstructor(Application::class.java).newInstance(mockApplication)` succeeds without `NoSuchMethodException`.
  2. *Comprehensive AndroidViewModel Reflection Contract Test (`TST-ARC-004.2`, `AndroidViewModelReflectionContractTest.kt`)*:
  • Verify via automated reflection contract test that all `AndroidViewModel` subclasses instantiated via default framework factory (`DeviceListViewModel`, `EquipmentViewModel`, `KnownLocationsViewModel`, `MapFragmentWithTrackViewModel`, `RoutesViewModel`, `TrackOnMapAftermathViewModel`, `BackupRestoreViewModel`, `WorkoutClustersViewModel`, `ZonesSettingsViewModel`, `PeriodsViewModel`, `SportTypeViewModel`, `AthleteProfileViewModel`, `EditDeviceViewModel`, `WorkoutSummariesViewModel`) expose an accessible `(Application.class)` constructor.
  3. *Coding Standards Parity Audit (`TST-ARC-004.3`)*:
  • Verify `docs/coding_standards.md` §3.5 codifies the mandatory `@JvmOverloads constructor` rule for reflective framework classes.
  4. *Clean-Room Full Suite Regression Execution (`TST-ARC-004.4`)*:
  • Execute `./gradlew testDebugUnitTest` verifying 100% pass rate across the full unit test suite without regressions.

### TST-ARC-005: **Java Core Services Architecture Decision Record & Migration Strategy Verification Suite** [Verified]
- **Priority**: Medium
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Requirement(s)**: `REQ-ARC-005`
- **Component**: **Java Core Services Architecture Decision Record & Migration Strategy Verification Suite**
- **Jira**: `ATT-7566`, `ATT-8115`
- **Test Description**:
  1. *ADR Existence & Structural Integrity Contract Test (`TST-ARC-005.1`, `JavaCoreMigrationStrategyContractTest.kt`)*:
  • Verify that `docs/architecture/adr_001_java_core_services_migration.md` exists and contains required sections: Context, Java Inventory, Decision Matrix, Phased Roadmap (Tiers 0–6), Quality Safeguards, and Child Tickets.
  2. *Class Inventory & Decision Coverage Test (`TST-ARC-005.2`, `JavaCoreMigrationStrategyContractTest.kt`)*:
  • Verify that ADR-001 accounts for all top Java anchors (`TrackerService`, `BANALService`, `WorkoutSummariesDatabaseManager`, `TrainingApplication`, `DevicesDatabaseManager`, `DeviceManager`, `BTLERadarDevice`, `FitFileWriter`) with unambiguous Migrate/Keep/Retire determinations, and verify `<= 149` Java classes in main production sources.
  3. *Child Ticket Sizing & Traceability Audit (`TST-ARC-005.3`, `JavaCoreMigrationStrategyContractTest.kt`)*:
  • Verify that `REGISTERED_CHILD_TICKETS` contains exactly the 3 authorized migration child tickets (`ATT-7903`, `ATT-7905`, `ATT-7906`) sized between 1 and 3 SP and mapped to Epic `ATT-431`.
  4. *Targeted & Full Suite Clean-Room Regression Execution (`TST-ARC-005.4`)*:
  • Execute `./gradlew testDebugUnitTest --tests com.atrainingtracker.architecture.JavaCoreMigrationStrategyContractTest` verifying 100% pass rate.

### TST-ARC-006: **Java Core Migration: Pure Signal Filters Kotlin Migration & ADR-001 Contract Alignment Verification Suite** [Verified]
- **Target Release**: `V5.0.0`
- **Priority**: Medium
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Requirement(s)**: `REQ-ARC-006`
- **Component**: **Pure Signal Filters Kotlin Migration Verification Suite**
- **Jira**: `ATT-7905`
- **Test Description**:
  1. *Filter Behavioral & Numerical Equivalence Test Suite (`TST-ARC-006.1`)*:
  • Execute targeted filter unit tests in `com.atrainingtracker.banalservice.filters.*` (`FilterInitializationAndBoundsTest`, `FilterManagerAccumulatorResetTest`, `FilterManagerRebindTest`, `FilterNullSafetyTest`, `FilterResetAndPreStartTest`, `SensorFilterDefaultsTest`) verifying 100% pass rate and identical floating-point output.
  2. *Thread Synchronization & Concurrency Safety Verification (`TST-ARC-006.2`)*:
  • Verify that all converted filter classes annotate mutating methods (`newValue`, `reset`, `getFilteredValue`, `createFilter`, `resetAllFilters`) with `@Synchronized` to preserve monitor safety across high-frequency ingestion threads.
  3. *Rule 50 Post-Trim Emptiness & NaN Elimination Verification (`TST-ARC-006.3`)*:
  • Verify that `TimedMovingAverageFilterTest` confirms `getFilteredValue()` returns `null` (not `NaN`) when all timestamped values are trimmed.
  4. *Java Core Migration Strategy Contract Test Verification (`TST-ARC-006.4`)*:
  • Execute `./gradlew testDebugUnitTest --tests com.atrainingtracker.architecture.JavaCoreMigrationStrategyContractTest` verifying that ADR-001 reflects the bounded migration scope and the `<= 149` Java file count tolerance passes.
  5. *Filter Subsystem Kover Coverage Verification (`TST-ARC-006.5`)*:
  • Execute `./gradlew koverVerifyDebug` confirming the filter subsystem achieves $\ge 70\%$ Line Coverage and $\ge 50\%$ Branch Coverage.

### TST-ARC-007: **Java Core Migration: Leaf Sensor Formatters Kotlin Migration & Interoperability Verification Suite** [Verified]
- **Target Release**: `V5.0.0`
- **Priority**: Medium
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Requirement(s)**: `REQ-ARC-007`
- **Component**: **Leaf Sensor Formatters Kotlin Migration Verification Suite**
- **Jira**: `ATT-7903`
- **Test Description**:
  1. *Formatter Behavioral & String Equivalence Test Suite (`TST-ARC-007.1`)*:
  • Execute targeted formatter unit tests in `com.atrainingtracker.banalservice.sensor.formater.*` covering all 14 concrete formatter classes across valid sensor readings, verifying 100% identical string representations and unit suffix outputs.
  2. *Null, NaN, Infinity & Boundary Robustness Verification (`TST-ARC-007.2`)*:
  • Verify that passing `null`, `Double.NaN`, `Double.POSITIVE_INFINITY`, `Double.NEGATIVE_INFINITY`, and boundary zero/negative values to each formatter returns the specified fallback string (`"--"`, `"--.--"`, `"0"`) and never throws `NullPointerException` or `NumberFormatException`.
  3. *Java Caller Compatibility & Secondary Signatures Verification (`TST-ARC-007.3`)*:
  • Verify that Java callers (`SensorType.java`, `BANALService.java`, UI presenters) compile and resolve `format(value)`, `format_with_units(value)`, and `format_3(value)` without source or binary incompatibilities.
  4. *Java Core Migration Strategy Contract Test Verification (`TST-ARC-007.4`)*:
  • Execute `./gradlew testDebugUnitTest --tests com.atrainingtracker.architecture.JavaCoreMigrationStrategyContractTest` verifying that Java file count decreases to 123 and satisfies `<= 149`.
  5. *Formatter Subsystem Kover Coverage Verification (`TST-ARC-007.5`)*:
  • Confirm that `com.atrainingtracker.banalservice.sensor.formater.*` achieves $\ge 70\%$ Line Coverage and $\ge 50\%$ Branch Coverage.

### TST-ARC-008: **Java Core Migration: TrackerService & BANALService Characterisation Test Suite** [Verified]
- **Target Release**: `V5.0.0`
- **Priority**: Medium
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Requirement(s)**: `REQ-ARC-008`
- **Component**: **Core Services Characterisation Test Suite**
- **Jira**: `ATT-7902`
- **Test Description**:
  1. *TrackerService State Machine & 1-Hz Tick Dispatching Test Suite (`TST-ARC-008.1`)*:
  • Execute automated characterisation tests verifying `TrackerService` state transitions across `TrackingMode` (`IDLE`, `READY`, `TRACKING`, `PAUSED`, `STOPPED`) and `AutoPauseState`, 1-Hz database tick sample dispatching, and post-stop export gating parity (`ATT-7918`).
  2. *TrackerService Crash/Resume Recovery Verification (`TST-ARC-008.2`)*:
  • Verify that simulated process kill and crash recovery reconstructs the active session state, workout ID, and sample continuity without data loss.
  3. *BANALService Multi-Sensor Arbitration & Dispatching Test Suite (`TST-ARC-008.3`)*:
  • Execute automated characterisation tests verifying `BANALService` ANT+/BLE arbitration, `ProxySensor` listener notifications, filter query integration, and timeout failover.
  4. *Zero Production Code Change Governance Audit (`TST-ARC-008.4`)*:
  • Verify via `git status` that `app/src/main/` contains zero modifications.

### TST-ARC-009: **Java Core Migration: Modernize SQLite Database Managers via Repository Interfaces Test Suite** [Verified]
- **Target Release**: `V5.0.0`
- **Priority**: Medium
- **Type**: Non-Functional
- **Verification Method**: Unit / Integration
- **Requirement(s)**: `REQ-ARC-009`
- **Component**: **Repository & Database Manager Modernization Verification Suite**
- **Jira**: `ATT-7909`
- **Test Description**:
  1. *ViewModel Repository Encapsulation Contract Verification (`TST-ARC-009.1`)*:
  • Execute `RepositoryCleanlinessContractTest` to verify that `DevicesTabbedViewModel` and `SportTypeViewModel` contain zero direct `DatabaseManager.getInstance` invocations and that `TrackingViewsRepository` exposes no database manager types in public signatures.
  2. *DeviceDataRepository & DevicesTabbedViewModel Modernization Suite (`TST-ARC-009.2`)*:
  • Execute `DevicesTabbedViewModelTest` to verify that device name and type resolution route through `DeviceDataRepository` off the main thread.
  3. *WorkoutRepository & SportTypeViewModel Statistics Suite (`TST-ARC-009.3`)*:
  • Execute `SportTypeViewModelRetiredTest` / `SportTypeViewModelTest` to verify that sport type stats queries route through `WorkoutRepository.getSportTypeStats()` and `WorkoutRepository.getSportTypeStatsForPeriod()` on `Dispatchers.IO`.
  4. *Null-Hardened Cursor & Resource Safety Verification (`TST-ARC-009.4`)*:
  • Execute unit tests verifying that null or empty cursors in `WorkoutRepository.loadAllWorkouts()` and `PeriodsRepository.startMigration()` return safely via `?.use {}` without `NullPointerException`.
  • Run `TryWithResourcesCursorAuditTest` to verify all cursor acquisition paths adhere to resource cleanup rules.
  5. *Merged Features Regression Verification (`TST-ARC-009.5`)*:
  • Verify that `dismissPolarSyncPending()`, `setPolarSyncPending()`, `clearPolarSyncPending()`, and crash-recovery accumulator preservation methods compile and function without regression.

### TST-ARC-010: **Java Core Migration: Streaming File Writers (BaseFileWriter, TCXFileWriter, FitFileWriter) Kotlin Migration Verification Suite** [Verified]
- **Target Release**: `V5.0.0`
- **Priority**: Medium
- **Type**: Non-Functional
- **Verification Method**: Unit / Integration
- **Requirement(s)**: `REQ-ARC-010`
- **Component**: **Streaming File Writers Kotlin Migration Verification Suite**
- **Jira**: `ATT-7906`
- **Test Description**:
  1. *Binary FIT Protocol Serialization & Golden Master Parity (`TST-ARC-010.1`)*:
  • Execute `FitFileWriterTest`, `FitFileWriterRadarTest`, and `FitFileWriterHrvTest` verifying binary FIT Protocol V2.0 message encoding, `FileEncoder.use {}` scoping, developer data ID headers, and $O(1)$ memory sample streaming without SQLite imports.
  2. *FIT Lossless Round-Trip & Developer Field Fidelity (`TST-ARC-010.2`)*:
  • Execute `FitLosslessRoundTripTest` verifying lossless round-trip through `FitWorkoutParser` for pedal dynamics (`ATT-8196`), swimming pool length (`ATT-7999`), session metadata developer fields (`ATT-7747`), radar threat alerts (`ATT-5096`), and RPE/Feel (`ATT-6879`).
  3. *TCX XML Schema & Structured DTO Serialization (`TST-ARC-010.3`)*:
  • Execute `TCXFileWriterLapTest`, `TCXFileWriterWorkoutNameTest`, and `TCXRoundTripTest` verifying Garmin TCX v2 sequence ordering, XML character escaping, and `TcxSerializer` structured DTO generation.
  4. *Java Subclass Interoperability Verification (`TST-ARC-010.4`)*:
  • Execute `GPXFileWriterTest`, `GCFileWriterTest`, and `CSVFileWriterTest` verifying that legacy Java writers inheriting from `open class BaseFileWriter` compile and function without behavioral regressions.
  5. *Architectural Contract & Exception Handling Governance (`TST-ARC-010.5`)*:
  • Execute `RepositoryEngineCleanlinessContractTest` verifying zero SQLite imports in `BaseFileWriter`, `TCXFileWriter`, and `FitFileWriter`.
  • Execute `ExceptionHandlingHygieneTest` verifying `FitFileWriter.kt` contains zero unlogged empty catch blocks and zero `catch (Throwable)`.
  • Execute `JavaCoreMigrationStrategyContractTest` verifying Java class count decreases while remaining `<= 149`.

### TST-PRO-020: **Agile Agentic ASPICE (AAA) Governance, Constructive Resistance, Hypothesis Verification, Stage 5 Resource Gate & Ceremony 2 Completeness Sweep Verification** [Implemented]
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Requirement(s)**: `REQ-PRO-027`
- **Component**: **Agile Agentic ASPICE (AAA) Governance, Constructive Resistance, Hypothesis Verification, Stage 5 Resource Gate & Ceremony 2 Completeness Sweep Verification**
- **Jira**: ``ATT-3534``
- **Test Description**:
  1. *Project Protocol & Governance Preamble Audit*:
  • Verify `docs/project_protocol.md` and `.agents/rules/aspice_governance.md` define Agile Agentic ASPICE (AAA) and the 3 Automotive SPICE pillars (Tripwires, Infinite Context, Constructive Resistance & Documented Deviations).
  2. *Stage 1 Hypothesis Verification Audit*:
  • Verify `.agents/skills/stage1-analysis/SKILL.md` and `templates/analysis_template.md` mandate independent hypothesis verification of ticket-provided problem domains and root causes against the active sprint branch.
  3. *Stage 5 AAPT2 Fast Resource Gate Audit*:
  • Verify `.agents/skills/stage5-verification/SKILL.md` mandates `./gradlew processDebugResources` in Stage 5 Tier 1 verification.
  4. *Ceremony 2 Completeness Sweep Audit*:
  • Verify `.agents/skills/sprint-planner/SKILL.md` Ceremony 2 Step 3 mandates `git show` commit review and codebase completeness sweep (`grep`/`rg`) across `app/src/main` for refactoring/cleanup tickets.

### TST-PRO-021: **Living Documentation Sharding, Multi-Line Markdown Section Formatting & Dual-Format Tooling Verification Suite** [Verified]
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Requirement(s)**: `REQ-PRO-028`
- **Component**: **Living Documentation Sharding, Multi-Line Markdown Section Formatting & Dual-Format Tooling Verification Suite**
- **Jira**: ``ATT-4594``, ``ATT-6140``
- **Test Description**:
  1. *Domain Shard Structure & Master Index Verification (`TST-PRO-021.1`)*:
  • Verify existence of 9 domain files in `docs/requirements/` and `docs/tests/` (including `req_store_presence.md` and `tst_store_presence.md`), and verify master index tables link to all shards.
  2. *Bitwise ID & Entity Migration Parity Test (`TST-PRO-021.2`)*:
  • Verify `tools/shard_living_docs.py` preserves 100% of all 762 `REQ-` and 764 `TST-` IDs, status tags, and sub-clauses.
  3. *Dual-Format Governance Verification (`TST-PRO-021.3`)*:
  • Verify `tools/verify_requirement_governance.py` and `tools/jira_util.py` detect requirements, diffs, archaeology sections, and update status from `[Planned]` to `[Verified]` in domain-sharded multi-line files.
  4. *Clean-Room Governance & Full Suite Regression Execution (`TST-PRO-021.4`)*:
  • Run `python3 tools/verify_requirement_governance.py` asserting exit code 0; execute `./gradlew testDebugUnitTest` verifying 100% test pass rate.
  5. *Prose Shard Count Preamble Verification (`TST-PRO-021.5`)*:
  • Verify `verify_master_table_parity()` in `tools/verify_requirement_governance.py` validates that prose stated shard counts match filesystem shard counts with exit code 0 on parity and exit code 1 on mismatch.

### TST-PRO-022: **AAA Story Point Estimation, CLI Operations, Complexity Split Guardrail & Sprint Capacity Tracking Verification** [Verified]
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Requirement(s)**: `REQ-PRO-029`
- **Component**: **AAA Story Point Estimation, CLI Operations, Complexity Split Guardrail & Sprint Capacity Tracking Verification**
- **Jira**: ``ATT-5203``
- **Test Description**:
  1. *Jira CLI Story Point Unit & Integration Test (`TST-PRO-022.1`, `tools/test_jira_story_points.py`)*:
  • Test `set-points` payload generation for integer and float points (`float(points)`), string parsing, and clearing (`None`).
  • Test `create-issue` argument parsing for `--points=N` and `--story-points=N`.
  • Test `list` sprint points aggregation (handling issues with null points, zero points, and valid points, and verifying total calculation and unestimated count).
  • Test `show` display formatting with and without story points.
  2. *Live Jira REST API Integration Verification (`TST-PRO-022.2`)*:
  • Execute `python3 tools/jira_util.py set-points ATT-5203 2` and verify `python3 tools/jira_util.py show ATT-5203` displays `*Story Points*: 2.0 SP` (or `2 SP`).
  • Verify `python3 tools/jira_util.py list` lists all active sprint tickets with `[X SP]` tags and the total sprint summary line.
  3. *Skill & Presentation Governance Audit (`TST-PRO-022.3`)*:
  • Verify `.agents/skills/sprint-planner/SKILL.md` documents Ceremony 1 estimation, `\ge 5 SP` split guardrail, target capacity envelope (~24–30 SP), and Ceremony 2 retro points tracking.
  • Verify `.agents/skills/brainstormer/SKILL.md` documents `--points` flag and `\ge 5 SP` decomposition.
  • Verify `docs/presentations/agile_agentic_aspice_beamer.tex` references the 1/2/3/5+ Story Point Complexity Guardrail and human review capacity model.
  4. *Clean-Room Governance Verification (`TST-PRO-022.4`)*:
  • Execute `python3 tools/verify_requirement_governance.py` asserting exit code 0 and zero governance violations.

### TST-PRO-033: **Online User Guide Content, Exclusion & Asset Contract** [Verified]
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Requirement(s)**: `REQ-PRO-033`
- **Component**: **Online User Guide Content, Exclusion & Asset Contract**
- **Jira**: ``ATT-2275``, ``ATT-5418``
- **Test Description**:
  1. *Jekyll Configuration & Exclusion Hygiene (`TST-PRO-033.1`)*:
  • Verify `docs/_config.yml` exists and explicitly excludes internal developer paths (`ci-secrets.md`, `project_protocol.md`, `coding_standards.md`, `design_guidelines.md`, `engineering/`, `requirements/`, `tests/`).
  2. *Landing Page & Bilingual Chapter Completeness (`TST-PRO-033.2`)*:
  • Verify `docs/index.md` exists and links to both German and English guides.
  • Verify all 18 chapter files exist across `docs/guide/de/` and `docs/guide/en/` with non-empty content.
  3. *Image Asset Resolution & Existence (`TST-PRO-033.3`)*:
  • Verify all markdown image references (`../img/...` or `img/...`) resolve to existing, non-empty PNG/JPG image assets under `docs/guide/img/`.
  4. *Anchor Parity & Deep Linking Contract (`TST-PRO-033.4`)*:
  • Verify all `<a id="...">` anchor identifiers match 1:1 between corresponding German and English chapter files.
  5. *Layout Structure & HTML/Markdown Asset Resolution (`TST-PRO-033.5`, ATT-5418)*:
  • Verify `OnlineUserGuideContractTest.kt` parses both Markdown `![]()` and HTML `<img src="...">` / `<a href="...">` syntax, asserting all referenced screenshot assets exist and are non-empty.
  • Verify 100% anchor parity across all 9 chapters between DE and EN.

### TST-PRO-034: **ASPICE SWE.1/2/4/6 Audit Hardening Verification Suite** [Verified]
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Requirement(s)**: `REQ-PRO-034`
- **Component**: **ASPICE Audit Hardening Verification Suite**
- **Jira**: ``ATT-5431``
- **Test Description**:
  1. *Shard Status & Master Table Counter Parity Verification (`TST-PRO-034.1`)*:
  • Verify that all completed items in `docs/requirements/` and `docs/tests/` carry `[Verified]`, backlog items carry `[Planned]`, and tables in `docs/requirements.md` and `docs/tests.md` have 100% mathematical parity.
  2. *ASPICE SWE.2 Architecture Completeness & Dynamic Behavioral Models (`TST-PRO-034.2`)*:
  • Verify that `docs/architecture.md` covers all layers, maps all 9 domain shards and requirement prefix families, includes 3 valid Mermaid diagrams (Tracking lifecycle, Sensor failover, Post-workout pipeline), and specifies threading/migration invariants.
  3. *Automated Stage-5 Master Counter Recalculation Verification (`TST-PRO-034.3`)*:
  • Verify that `auto_verify_living_docs_on_stage5` in `tools/jira_util.py` correctly updates master tables on simulated/actual Stage 5 submissions and `verify_requirement_governance.py` validates counter consistency.
  4. *Universal Image Inspection, Code Grounding & Continuous SWE.2 Skill Governance (`TST-PRO-034.4`)*:
  • Verify that `aspice_governance.md`, `stage1-analysis`, `stage3-impl-plan`, `stage4-implementation`, and `stage5-verification` codify universal `read_file` image inspection, documentation code-grounding, and SWE.2 architecture updates.
  5. *Clean-Room Governance & Regression Execution (`TST-PRO-034.5`)*:
  • Execute `python3 tools/verify_requirement_governance.py` (exit 0) and targeted build tests.

### TST-PRO-035: **Standalone Agile Agentic ASPICE (AAA) Core Framework, Swiss Cheese Reliability & Starter Templates Verification Suite** [Verified]
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Requirement(s)**: `REQ-PRO-035`
- **Component**: **Höchstmaß Core Framework & Starter Templates**
- **Jira**: `ATT-3922`, `ATT-5817`, `ATT-6092`
- **Test Description**:
  1. *README, Project Protocol, Epigraph, Etymology, 2-Column 5-Layer SVG & Entry Barrier Verification (`TST-PRO-035.1`, `HoechstmassCoreFrameworkContractTest.kt`)*:
  • Verify `tools/hoechstmass/README.md` and `tools/hoechstmass/docs/project_protocol.md` exist and contain `# Höchstmaß — The AAA Coding Framework` title, inside-out AAA structure (ASPICE -> Agentic -> Agile), the ChatGPT epigraph quote, human/AI boundary attribution, multi-model review (Claude, Gemini, ChatGPT), Höchstmaß etymology callout (`/ˈhøːçstˌmaːs/`), 2-column 5-layer Swiss Cheese SVG asset (`tools/hoechstmass/assets/swiss_cheese_layers.svg`) with `L1`–`L5` barrier plates and `✕` markers, 5-layer decorrelation formula ($P = p_1 \cdot p_2 \cdot p_3 \cdot p_4 \cdot p_5$), and Chapter 5 Entry Barrier.
  2. *Generalized Document Triad & Isolated In-Place Review Workflow Contract Verification (`TST-PRO-035.2`, `HoechstmassCoreFrameworkContractTest.kt`)*:
  • Verify existence and content of generalized `.agents/rules/aspice_governance.md` (8 Core Rules), `docs/coding_standards.md` (§1.1–§1.5 invariants), `docs/design_guidelines.md` (component heuristics), `tools/review_tool.py`, protocol `.reviews/` and `Emacs` workflow specification, and `.reviews/` isolation via `.git/info/exclude`.
  3. *10 Generalized Skills & Deliverable Templates Verification (`TST-PRO-035.3`, `HoechstmassCoreFrameworkContractTest.kt`)*:
  • Verify existence of all 10 project-agnostic `SKILL.md` files under `tools/hoechstmass/.agents/skills/` and all 6 markdown templates under `tools/hoechstmass/templates/`.
  4. *Clean-Room Governance & Regression Execution (`TST-PRO-035.4`)*:
  • Execute `python3 tools/verify_requirement_governance.py` (exit 0) and targeted unit tests `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.build.*"`.

### TST-PRO-036: **Parameterized Issue Tracker Automation & Requirement Governance Tooling Verification Suite** [Verified]
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Requirement(s)**: `REQ-PRO-036`
- **Component**: **Höchstmaß Parameterized Tooling**
- **Jira**: `ATT-5667`
- **Test Description**:
  1. *Declarative Configuration Schema & Template Audit (`TST-PRO-036.1`, `HoechstmassToolingContractTest.kt`)*:
  • Verify existence and non-emptiness of `tools/hoechstmass/tools/hoechstmass_config.json` and `.env.jira.example`.
  • Assert `hoechstmass_config.json` declares `project_key`, `board_id`, `transitions`, `roles`, and `paths`.
  2. *Parameterized Tooling Source & Decoupling Contract (`TST-PRO-036.2`, `HoechstmassToolingContractTest.kt`)*:
  • Verify existence and non-emptiness of `tools/hoechstmass/tools/jira_util.py` and `tools/hoechstmass/tools/verify_requirement_governance.py`.
  • Assert neither tool contains hardcoded `ATT-` or proprietary developer account IDs.
  3. *Python Automated Tooling Unit Test Suite (`TST-PRO-036.3`, `test_hoechstmass_tools.py`)*:
  • Execute `python3 tools/hoechstmass/tools/test_hoechstmass_tools.py` verifying config loading, role resolution, secret masking, and governance validation pass 100%.
  4. *Clean-Room Governance & Regression Execution (`TST-PRO-036.4`)*:
  • Execute `python3 tools/verify_requirement_governance.py` (exit 0) and targeted unit tests `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.build.*"`.

### TST-PRO-037: **Real-World Reference Showcase & Developer Community Launch Posts Verification Suite** [Verified]
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Requirement(s)**: `REQ-PRO-037`
- **Component**: **Höchstmaß Showcase & Community Documentation**
- **Jira**: `ATT-5668`
- **Test Description**:
  1. *Real-World Reference Showcase & Case Studies Audit (`TST-PRO-037.1`, `HoechstmassShowcaseAndCommunityContractTest.kt`)*:
  • Verify existence of `coding_standards.md`, `design_guidelines.md`, and `CASE_STUDIES.md` under `tools/hoechstmass/examples/android-jetpack-compose/`.
  • Assert `CASE_STUDIES.md` details the 3 adversarial reviews (Mader-Heck Metabolic Model, DFA alpha-1 HRV, Radar bytecode audit).
  2. *Developer Community Launch Posts Integrity Audit (`TST-PRO-037.2`, `HoechstmassShowcaseAndCommunityContractTest.kt`)*:
  • Verify existence of all 5 community articles in `tools/hoechstmass/docs/community/` (`r_softwareengineering.md`, `r_androiddev.md`, `r_google_antigravity.md`, `r_vibecoding.md`, `hackernews_show_hn.md`).
  • Assert all 5 articles contain the ChatGPT epigraph, dual attribution reasons (provenance and cross-model Gemini + ChatGPT UI review), and Höchst·maß etymology box.
  3. *Clean-Room Governance & Regression Execution (`TST-PRO-037.3`)*:
  • Execute `python3 tools/verify_requirement_governance.py` (exit 0) and targeted unit tests `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.build.*"`.

### TST-PRO-038: **Automated Quantitative Sprint Metrics (PA 4.1), Statistical Process Control (PA 4.2) & Ceremony 2 Governance Verification Suite** [Verified]
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Requirement(s)**: `REQ-PRO-038`
- **Component**: **Jira Process Tooling, Sprint Retrospective Governance & Höchstmaß Framework**
- **Jira**: `ATT-5765`, `ATT-5764`
- **Test Description**:
  1. *Sprint Metrics Computation & Parsing (`TST-PRO-038.1`, `test_sprint_metrics.py`)*:
  • Verify parsing of subtask comments with `[ROLE: agent2]` and computation of Gate 1–5 FTPR, Swiss Cheese escape rates ($p_1 \dots p_5$), and Human Acceptance Rate.
  2. *Time-Series Persistence & Idempotent Upsert (`TST-PRO-038.2`, `test_sprint_metrics.py`)*:
  • Verify `--record` writes structured JSON lines to `docs/engineering/metrics/process_metrics.jsonl` and updates existing records idempotently.
  3. *Statistical Process Control (SPC) Outlier Detection (`TST-PRO-038.3`, `test_sprint_metrics.py`)*:
  • Verify computation of rolling mean $\mu$ and standard deviation $\sigma$, and assertion of warnings for metric variations exceeding $\mu \pm 2\sigma$.
  4. *Governance Whitelist & Directory Parity Contract (`TST-PRO-038.4`, `verify_requirement_governance.py`)*:
  • Assert `docs/engineering/metrics/` is whitelisted in `ALLOWED_ENGINEERING_ENTRIES` and passes `verify_requirement_governance.py`.
  5. *Ceremony 2 Scorecard & Retro Template Verification (`TST-PRO-038.5`)*:
  • Verify that `.agents/skills/sprint-planner/SKILL.md` (and hoechstmass mirror) mandates `sprint-metrics --record --post-to-retro` in Step 4.
  • Verify that `templates/sprint_retro_template.md` and `tools/hoechstmass/templates/sprint_retro_template.md` contain the Quantitative Sprint Scorecard section and `PA 4.2 -> PA 5.2` corrective action trigger.
  6. *ASPICE Level 1–5 Capability Matrix & Contract Verification (`TST-PRO-038.6`)*:
  • Verify `docs/project_protocol.md`, `tools/hoechstmass/docs/project_protocol.md`, and `tools/hoechstmass/README.md` codify `PA 1.1` through `PA 5.2`.
  • Assert `HoechstmassCoreFrameworkContractTest.kt` verifies the ASPICE Level 1–5 matrix and retro template parity.

### TST-PRO-039: **Jira Data Center & Enterprise On-Premise Tooling Verification Suite** [Verified]
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Requirement(s)**: `REQ-PRO-039`
- **Component**: **Jira Process Tooling, Höchstmaß Framework & Deployment Adapters**
- **Jira**: `ATT-5830`
- **Test Description**:
  1. *Deployment Mode Configuration & Environment Parsing (`TST-PRO-039.1`, `test_jira_datacenter.py`)*:
  • Verify config parser correctly detects `deployment_type` from `hoechstmass_config.json` and overrides with `JIRA_DEPLOYMENT`.
  • Assert default deployment falls back to `cloud`.
  2. *Bearer PAT & Corporate CA Bundle Verification (`TST-PRO-039.2`, `test_jira_datacenter.py`)*:
  • Mock HTTP calls under Data Center mode and assert `Authorization: Bearer <token>` header.
  • Assert SSL context configuration passes `JIRA_CA_BUNDLE` path to urllib3 / requests or creates custom SSLContext.
  3. *Assignee Payload Username vs AccountId Dispatch (`TST-PRO-039.3`, `test_jira_datacenter.py`)*:
  • Assert `assign_issue` sends `{"name": "agent1"}` in Data Center mode and `{"accountId": "..."}` in Cloud mode.
  • Assert `get_issue` parses `fields.assignee.name` in Data Center mode.
  4. *Epic Link Custom Field vs Parent Payload Dispatch (`TST-PRO-039.4`, `test_jira_datacenter.py`)*:
  • Assert creating/linking top-level issues in Data Center mode populates `customfield_10014` (or configured custom field) and omits `fields.parent`.
  • Assert creating subtasks in Data Center mode populates `fields.parent` without `customfield_10014`.
  5. *JQL Search REST API v2 Routing (`TST-PRO-039.5`, `test_jira_datacenter.py`)*:
  • Assert `search_issues` dispatches to `/rest/api/2/search` in Data Center mode and `/rest/api/3/search/jql` in Cloud mode.
  6. *Tooling Mirror Synchronization & Regression Suite (`TST-PRO-039.6`)*:
  • Verify identical logic and hashes for shared functions between `tools/jira_util.py` and `tools/hoechstmass/tools/jira_util.py`.
  • Execute `python3 tools/verify_requirement_governance.py` (exit 0) and Python unit tests.

### TST-PRO-040: **Epic Lifecycle Management, 'list-epics' / 'close-epic' CLI Commands, and Taxonomy Governance Verification Suite** [Verified]
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Requirement(s)**: `REQ-PRO-040`
- **Component**: `tools/jira_util.py`, `tools/hoechstmass/tools/jira_util.py`, `.agents/skills/{brainstormer,sprint-planner,jira-workflow}/SKILL.md`
- **Jira**: `ATT-5911`
- **Test Description**:
  1. *List Epics Command & Child Status Aggregation (`TST-PRO-040.1`, `test_jira_epics.py`)*:
  • Mock JQL search returning Epics and child tickets across Cloud (`parent = ...`) and Data Center (`cf[10014] = ...`).
  • Verify `list-epics` computes open and done child ticket counts accurately and formats output.
  2. *Close Epic Triple Guardrail Enforcement (`TST-PRO-040.2`, `test_jira_epics.py`)*:
  • Role Guard: Verify non-coordinator/non-human roles (`--as agent1`, `--as agent2`) abort with exit code 1.
  • Issue Type Guard: Verify passing a standard Story/Bug/Task aborts with exit code 1 and Human Gate notice.
  • Completeness Guard: Verify passing an Epic with open child tickets aborts with exit code 1 listing the open tickets.
  • Success: Verify passing an Epic with 0 open child tickets under `--as coordinator` successfully executes transition to `Erledigt`.
  3. *Tooling Mirror Synchronization & Hoechstmass Parity (`TST-PRO-040.3`, `test_hoechstmass_tools.py`)*:
  • Verify `tools/hoechstmass/tools/jira_util.py` implements identical `list-epics` and `close-epic` subcommands with identical guardrails.
  4. *Skill Documentation & Canonical Epic Taxonomy Audit (`TST-PRO-040.4`)*:
  • Verify `.agents/skills/brainstormer/SKILL.md`, `.agents/skills/sprint-planner/SKILL.md`, and `.agents/skills/jira-workflow/SKILL.md` (and their hoechstmass mirrors) define the Canonical Epic Taxonomy and human consultation requirement.
  5. *Clean-Room Governance & Verification Execution (`TST-PRO-040.5`)*:
  • Execute `python3 tools/verify_requirement_governance.py` (exit 0) and `python3 -m unittest tools/test_jira_epics.py`.

### TST-PRO-041: **Parallel Sprint Execution Protocol for Disjoint Epics, Flock Serialization & Partitioned Localization Verification Suite** [Verified]
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Requirement(s)**: `REQ-PRO-041`
- **Component**: `.agents/rules/aspice_governance.md`, `.agents/skills/{jira-workflow,stage4-implementation,stage5-verification,sprint-planner}/SKILL.md`, `tools/run_serialized.sh`, `tools/parallel_sprint_lock.py`, `tools/jira_util.py`, `tools/verify_requirement_governance.py`, `tools/test_verify_requirement_governance.py`, `tools/test_flock_serialization.py`, `app/src/test/java/com/atrainingtracker/trainingtracker/localization/TranslationParityTest.kt`
- **Jira**: `ATT-5914`, `ATT-6743`, `ATT-7025`
- **Test Description**:
  1. *Subagent Worktree Isolation & Disjoint Epic Guardrail (`TST-PRO-041.1`)*:
  • Verify `Workspace: "share"` subagent pairs execute in independent worktrees without branch or working-tree collisions across up to 3 concurrent lanes (`concurrency = 3`).
  • Verify governance enforces strict disjoint Parent Epic constraint (rejecting parallel execution within the same Epic).
  2. *POSIX Flock Build, Device & Deterministic Merge Mutual Exclusion (`TST-PRO-041.2`, `test_flock_serialization.py`)*:
  • Simulate concurrent processes competing for `/tmp/att_build_merge.lock`.
  • Verify mutual exclusion: exactly one process holds the lock at a time for heavy Gradle tests, `installDebug` / `adb` commands, and Stage 5 squash merges. Subsequent processes queue deterministically, and lock releases reliably upon completion or failure.
  • Verify multi-worktree `--merge-ticket` execution (`test_merge_ticket_multi_worktree_success`, `test_merge_ticket_concurrent_worktrees_serialization`): changes rebase cleanly onto local `sprint/<SPRINT_NAME>`, living doc indexes synchronize, the main worktree fast-forwards to the new commit, and feature branches are cleanly deleted without checkout collisions.
  3. *Dynamic Multi-File String Discovery & Parity Test Suite (`TST-PRO-041.3`, `TranslationParityTest.kt`)*:
  • Verify `TranslationParityTest.kt` dynamically scans all `strings*.xml` across `res/values*/`.
  • Assert partitioned domain string files (`strings_<domain>.xml`) maintain 100% key and placeholder parity across all 9 supported locales.
  4. *Rebase-then-Recalculate Living Docs Governance (`TST-PRO-041.4`)*:
  • Verify `recalculate_master_index_tables()` under merge lock after rebase maintains 100% parity across living doc tables with zero Git merge conflicts.
  5. *Deterministic Python Governance Tripwires Verification Suite (`TST-PRO-041.5`, `test_verify_requirement_governance.py`)*:
  • Verify Composable Island Check: newly added non-preview, non-private `@Composable` declarations without production call sites in `app/src/main/` trigger exit code 1.
  • Verify Repository Encapsulation Guard: non-private methods or properties in `*Repository.kt` or `*Repository.java` exposing `*DatabaseManager`, `*DbHelper`, `SQLiteDatabase`, or `Cursor` trigger exit code 1.
  • Verify Architecture & System Spec Grounding & SYSREQ Guard: non-existent backticked source files or `SYSREQ-*` set mismatches trigger exit code 1.
  6. *Clean-Room Governance & Verification Execution (`TST-PRO-041.6`)*:
  • Execute `python3 tools/verify_requirement_governance.py` (exit 0) and `python3 tools/test_verify_requirement_governance.py`.

### TST-PRO-042: **CI Configuration Integrity & Stale Travis Artifacts Contract Test Suite** [Verified]
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Requirement(s)**: `REQ-PRO-042`
- **Component**: `ReleaseCiAndLintConfigContractTest.kt`, `app/build.gradle`
- **Jira**: `ATT-6177`
- **Test Description**:
  1. *Repository Root Stale CI File Absence Test (`TST-PRO-042.1`, `ReleaseCiAndLintConfigContractTest.kt`)*:
  • Search repository root for `.travis.yml`.
  • Assert that `File(".travis.yml")` does not exist in any candidate repository root directory.
  2. *Build Script Travis Comment Hygiene Test (`TST-PRO-042.2`, `ReleaseCiAndLintConfigContractTest.kt`)*:
  • Read text content of `app/build.gradle`.
  • Assert that `"travis"` (case-insensitive) is not contained anywhere in the file.
  3. *Authoritative GitHub Actions Workflow Integrity Test (`TST-PRO-042.3`, `ReleaseCiAndLintConfigContractTest.kt`)*:
  • Verify that existing tests validating `android-release.yml` continue to execute and pass.
  4. *Clean-Room Targeted Test Suite Execution (`TST-PRO-042.4`)*:
  • Execute targeted unit test suite `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.build.*"` asserting 100% pass rate.

### TST-PRO-043: **SWE.2 Architecture Conformance & Code-Grounding Contract Test Suite** [Verified]
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Requirement(s)**: `REQ-PRO-043`
- **Component**: `docs/architecture.md`, `docs/architecture/interfaces.md`, `AspiceSweAuditContractTest.kt`
- **Jira**: `ATT-6179, ATT-6727`
- **Test Description**:
  1. *Authentic Component Presence Test (`TST-PRO-043.1`, `AspiceSweAuditContractTest.kt`)*:
  • Verify `docs/architecture.md` contains authentic class names: `WorkoutClusterEngine`, `GPXFileWriter`, `TCXFileWriter`, `WorkoutSamplesDatabaseManager`, `MilestoneAutoNamingManager`, `FirstJubileeActionHelper`, `SettingsDataStore`, `EquipmentDbHelper`, `EquipmentRepository`, `ProFeatureManager`, `ATrainingTrackerTheme`, `TelemetryMetricGraph`, and `PlayStoreListingContractTest`.
  2. *Drifted Component Blacklist Absence Test (`TST-PRO-043.2`, `AspiceSweAuditContractTest.kt`)*:
  • Verify `docs/architecture.md` and `docs/architecture/interfaces.md` do not contain any of the 14 drifted placeholders: `RouteLearningEngine`, `GpxExporter`, `TcxExporter`, `WorkoutSamplesDb`, `MilestoneDetector`, `InAppReviewManager`, `UserPreferences`, `EquipmentDatabaseManager`, `PrivacyZoneManager`, `BillingManager`, `TTTheme`, `CustomLineChart`, `ElevationPoint`, `StoreMetadataContractTest`.
  3. *Tracking State Machine & Dispatcher Conformance Test (`TST-PRO-043.3`, `AspiceSweAuditContractTest.kt`)*:
  • Verify `docs/architecture.md` specifies `TrackingMode` states (`IDLE`, `READY`, `TRACKING`, `PAUSED`) and retains required threading tokens (`dbDispatcher`, `Dispatchers.Main`, `Dispatchers.IO`).
  4. *Dynamic Component Existence & Architecture Conformance Contract (`TST-PRO-043.4`, `AspiceSweAuditContractTest.kt`)*:
  • Extract all backticked software component symbols from `docs/architecture.md` Section 2 allocation table and `docs/architecture/interfaces.md`, filter against a strict framework SDK allow-list, and assert that every project component symbol exists in `app/src/main/` or `tools/`.
  5. *Clean-Room Targeted Test Suite Execution (`TST-PRO-043.5`)*:
  • Execute targeted unit test suite `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.build.AspiceSweAuditContractTest"` asserting 100% pass rate.

### TST-PRO-044: **ASPICE SWE.2 Software Interface Specification Contract Test Suite** [Verified]
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Requirement(s)**: `REQ-PRO-044`
- **Component**: `docs/architecture/interfaces.md`, `docs/architecture.md`, `AspiceSweAuditContractTest.kt`
- **Jira**: `ATT-6180, ATT-6732`
- **Test Description**:
  1. *Interface File & Boundary Completeness Test (`TST-PRO-044.1`, `AspiceSweAuditContractTest.kt`)*:
  • Verify existence of `docs/architecture/interfaces.md`.
  • Assert that all 8 interface families (`IF-SWE-01` through `IF-SWE-08`) are defined.
  2. *Mandatory Interface Attribute Completeness Test (`TST-PRO-044.2`, `AspiceSweAuditContractTest.kt`)*:
  • For every interface `IF-SWE-01`..`IF-SWE-08`, assert the presence of Provider, Consumer, Operations/Payload, Threading/Concurrency, and Error Handling fields.
  3. *Architecture Document Integration Test (`TST-PRO-044.3`, `AspiceSweAuditContractTest.kt`)*:
  • Verify `docs/architecture.md` Section 5 contains the interface summary matrix referencing `IF-SWE-01` through `IF-SWE-08` and authentic components (`TrackOnMapAftermathViewModel`, `REST (OkHttp) & HealthConnectClient`).
  4. *Dynamic Method & Type Grounding Conformance Test (`TST-PRO-044.4`, `AspiceSweAuditContractTest.kt`)*:
  • Extract documented `fun <name>(` declarations and backticked component/DTO types from `docs/architecture/interfaces.md`.
  • Assert that all extracted method names and non-framework types resolve to real source declarations in `app/src/main/`.
  5. *Clean-Room Targeted Test Suite Execution (`TST-PRO-044.5`)*:
  • Execute targeted unit test suite `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.build.AspiceSweAuditContractTest"` asserting 100% pass rate.


### TST-PRO-045: **ASPICE SWE.5 Software Integration Verification Strategy Contract Test Suite** [Verified]
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Requirement(s)**: `REQ-PRO-045`
- **Component**: `docs/architecture/integration_strategy.md`, `docs/architecture.md`, `AspiceSweAuditContractTest.kt`
- **Jira**: `ATT-6183, ATT-6736`
- **Test Description**:
  1. *Integration Strategy Structural Completeness Test (`TST-PRO-045.1`, `AspiceSweAuditContractTest.kt`)*:
  • Verify existence of `docs/architecture/integration_strategy.md`.
  • Assert that core sections ("Integration Sequence", "Verification Levels", "Entry and Exit Criteria", "Regression Selection") are present.
  2. *Interface Integration Seam Grounding Test (`TST-PRO-045.2`, `AspiceSweAuditContractTest.kt`)*:
  • Verify `docs/architecture/integration_strategy.md` explicitly references all 8 interfaces `IF-SWE-01` through `IF-SWE-08`.
  3. *Architecture Document Section 6 Conformance Test (`TST-PRO-045.3`, `AspiceSweAuditContractTest.kt`)*:
  • Verify `docs/architecture.md` contains Section 6 referencing `integration_strategy.md`.
  4. *Clean-Room Targeted Test Suite Execution (`TST-PRO-045.4`)*:
  • Execute targeted unit test suite `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.build.AspiceSweAuditContractTest"` asserting 100% pass rate.



### TST-PRO-046: **Android Lint CI Quality Gate & Baseline Burn-down Contract Test Suite** [Verified]
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Requirement(s)**: `REQ-PRO-046`
- **Component**: `.github/workflows/android-branch-build.yml`, `app/build.gradle`, `app/lint-baseline.xml`, `ReleaseCiAndLintConfigContractTest.kt`
- **Jira**: `ATT-6173, ATT-6728`
- **Test Description**:
  1. *CI Workflow Lint Step & Artifact Upload Conformance Test (`TST-PRO-046.1`, `ReleaseCiAndLintConfigContractTest.kt`)*:
  • Read `.github/workflows/android-branch-build.yml`.
  • Assert that the workflow contains `./gradlew :app:lintDebug` (or `lintDebug`).
  • Assert that the workflow uploads lint report artifacts via `actions/upload-artifact`.
  2. *Build Script Lint Configuration Conformance Test (`TST-PRO-046.2`, `ReleaseCiAndLintConfigContractTest.kt`)*:
  • Read `app/build.gradle`.
  • Assert that the `lint` block specifies `abortOnError = true`, `htmlReport = true`, and `xmlReport = true`.
  3. *Lint Baseline Upper Bound & Issue Count Test (`TST-PRO-046.3`, `ReleaseCiAndLintConfigContractTest.kt`)*:
  • Parse `app/lint-baseline.xml`.
  • Count `<issue` XML elements and assert the total count is in `0..239` (ATT-6728).
  4. *Compose Lint Rules Enforcement Test (`TST-PRO-046.5`, `ReleaseCiAndLintConfigContractTest.kt`)*:
  • Read `app/build.gradle`.
  • Assert that `NonObservableLocale` and `LocalContextGetResourceValueCall` are not downgraded to warning (ATT-6728).
  5. *Clean-Room Targeted Test Suite Execution (`TST-PRO-046.4`)*:
  • Execute targeted unit test suite `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.build.ReleaseCiAndLintConfigContractTest"` asserting 100% pass rate.
  6. *Android Lint Execution Cleanliness Test (`TST-PRO-046.6`)*:
  • Execute `./gradlew :app:lintDebug` and assert 0 errors.



### TST-PRO-047: **Detekt Static Analysis & Complexity Governance Contract Test Suite** [Verified]
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Requirement(s)**: `REQ-PRO-047`
- **Component**: `build.gradle`, `app/build.gradle`, `config/detekt/detekt.yml`, `app/detekt-baseline.xml`, `.github/workflows/android-branch-build.yml`, `ReleaseCiAndLintConfigContractTest.kt`
- **Jira**: `ATT-6175`, `ATT-6729`
- **Test Description**:
  1. *Build Script Detekt Plugin, Configuration & Baseline Ceiling Conformance Test (`TST-PRO-047.1`, `ReleaseCiAndLintConfigContractTest.kt`)*:
  • Read `build.gradle`, `app/build.gradle`, and `config/detekt/detekt.yml`.
  • Assert that the detekt plugin is applied and configured with `config/detekt/detekt.yml` and `app/detekt-baseline.xml`.
  • Assert that `config/detekt/detekt.yml` contains Jetpack Compose exemptions (`ignoreAnnotated: ['Composable']`, `ignoreAnnotated: ['Preview']`).
  • Parse `app/detekt-baseline.xml` and assert total `<ID>` issue count is strictly `<= 9550`.
  2. *Detekt Rules Configuration Conformance Test (`TST-PRO-047.2`, `ReleaseCiAndLintConfigContractTest.kt`)*:
  • Read `config/detekt/detekt.yml`.
  • Assert that complexity rules (`LargeClass`, `LongMethod`, and `CyclomaticComplexMethod`) are present with active thresholds.
  3. *CI Workflow Detekt Step & Artifact Upload Conformance Test (`TST-PRO-047.3`, `ReleaseCiAndLintConfigContractTest.kt`)*:
  • Read `.github/workflows/android-branch-build.yml`.
  • Assert that the workflow contains `./gradlew detekt` and an artifact upload step for detekt reports.
  4. *Clean-Room Targeted Test Suite Execution (`TST-PRO-047.4`)*:
  • Execute targeted unit test suite `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.build.ReleaseCiAndLintConfigContractTest"` and `./gradlew :app:detekt` asserting 100% pass rate.



### TST-PRO-048: **Kover Code Coverage Measurement & CI Threshold Gating Contract Test Suite** [Verified]
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Requirement(s)**: `REQ-PRO-048`
- **Component**: `build.gradle`, `app/build.gradle`, `.github/workflows/android-branch-build.yml`, `ReleaseCiAndLintConfigContractTest.kt`
- **Jira**: `ATT-6171`, `ATT-6730`
- **Test Description**:
  1. *Build Script Kover Plugin & Configuration Conformance Test (`TST-PRO-048.1`, `ReleaseCiAndLintConfigContractTest.kt`)*:
  • Read `build.gradle` and `app/build.gradle`.
  • Assert that the Kover plugin (0.9.11) is applied and configured with XML/HTML reporting, generated class exclusions (`*.R`, `*Manifest*`), and dual verification threshold rules (`CoverageUnit.LINE` and `CoverageUnit.BRANCH`).
  2. *CI Workflow Kover Step & Artifact Upload Conformance Test (`TST-PRO-048.2`, `ReleaseCiAndLintConfigContractTest.kt`)*:
  • Read `.github/workflows/android-branch-build.yml`.
  • Assert that the workflow contains Kover reporting/verify task execution and artifact upload for coverage reports.
  3. *Kover Report Generation & Verification Execution Test (`TST-PRO-048.3`, `app/build.gradle`)*:
  • Execute `./gradlew :app:koverXmlReportDebug :app:koverHtmlReportDebug :app:koverVerifyDebug`.
  • Assert that XML and HTML reports are produced under `app/build/reports/kover/` and verification threshold rules succeed against calibrated baseline bounds (LINE >= 35%, BRANCH >= 30%).
  4. *Clean-Room Targeted Test Suite Execution (`TST-PRO-048.4`)*:
  • Execute targeted unit test suite `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.build.ReleaseCiAndLintConfigContractTest"` asserting 100% pass rate.



### TST-PRO-049: **Source-Text Reading Test Audit Suite** [Verified]
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Requirement(s)**: `REQ-PRO-049`
- **Component**: `app/src/test/java/com/atrainingtracker/trainingtracker/build/SourceTextReadingAuditContractTest.kt`, `app/source-reading-baseline.txt`, `docs/coding_standards.md` §5.4
- **Jira**: `ATT-6186`, `ATT-6731`, `ATT-6569`, `ATT-6568`, `ATT-6567`, `ATT-6566`, `ATT-6565`, `ATT-6564`
- **Test Description**:
  1. *Audit Scanner Execution & Baseline Conformance Test (`TST-PRO-049.1`, `SourceTextReadingAuditContractTest.kt`)*:
  • Recursively scan all `.java` and `.kt` test files in `app/src/test/java/` using expanded reader detection (`readText`, `readLines`, `readString`, `useLines`, `bufferedReader`) targeting `app/src/main/`.
  • Assert that zero tests outside the approved allow-list (`LEGITIMATE_GOVERNANCE_GUARDS` + baseline) read `app/src/main/` production source files.
  2. *Governance Guard Exception Validation Test (`TST-PRO-049.2`, `SourceTextReadingAuditContractTest.kt`)*:
  • Assert that exactly 22 approved architectural governance guards are registered in `LEGITIMATE_GOVERNANCE_GUARDS`.
  • Assert that each guard ends with `Test.kt` or `Cache.kt` and exists on disk in `app/src/test/java`.
  3. *Legacy Baseline Ratchet & Stale-Entry Detection Test (`TST-PRO-049.3`, `SourceTextReadingAuditContractTest.kt`)*:
  • Assert that `(baseline - detectedReaders).isEmpty()`, guaranteeing zero stale entries remain in the baseline when legacy tests are phased out.
  • Assert that `baseline.size <= 94`, enforcing a monotonic non-increasing ceiling on legacy debt (ratcheted down from 139 by removing 45 tests across `ui.map` in ATT-6564).
  4. *Ratcheting Tripwire Violation Detection Test (`TST-PRO-049.4`, `SourceTextReadingAuditContractTest.kt`)*:
  • Validate that a synthetic unallow-listed reader triggers an assertion failure.
  5. *Clean-Room Targeted Test Suite Execution (`TST-PRO-049.5`)*:
  • Execute targeted unit test suite `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.build.SourceTextReadingAuditContractTest"` asserting 100% pass rate.


### TST-PRO-050: **Automated Architecture Conformance Test Suite** [Verified]
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Requirement(s)**: `REQ-PRO-050`
- **Component**: `app/src/test/java/com/atrainingtracker/trainingtracker/build/ArchitectureConformanceContractTest.kt`, `app/src/test/java/com/atrainingtracker/trainingtracker/build/SourceTextReadingAuditContractTest.kt`, `docs/architecture.md`
- **Jira**: `ATT-6182`, `ATT-6734`
- **Test Description**:
  1. *UI Layer DatabaseManager Isolation & Downward Ratchet Test (`TST-PRO-050.1`, `ArchitectureConformanceContractTest.kt`)*:
  • Recursively scan all `.java` and `.kt` source files in both `trainingtracker/ui/` and `banalservice/ui/` after stripping block and line comments.
  • Assert that zero classes outside the 23-caller ratcheted baseline import or reference `*DatabaseManager` or `*DbHelper`.
  • Assert that `(baseline - detectedCallers).isEmpty()` and `detectedCallers.size <= 23`.
  2. *Domain & Scientific Calculation Layer Purity Test (`TST-PRO-050.2`, `ArchitectureConformanceContractTest.kt`)*:
  • Recursively scan all files in `domain/` and `physiology/` calculation packages.
  • Assert that zero classes reference Android UI frameworks or raw SQLite `*DatabaseManager` / `*DbHelper` classes.
  3. *Ratcheting Tripwire Violation Detection Test (`TST-PRO-050.3`, `ArchitectureConformanceContractTest.kt`)*:
  • Validate that synthetic unallow-listed UI database references, domain UI imports, or upward database Compose imports trigger assertion failures.
  4. *Clean-Room Targeted Test Suite Execution (`TST-PRO-050.4`)*:
  • Execute targeted unit test suite `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.build.ArchitectureConformanceContractTest"` asserting 100% pass rate and confirm `ArchitectureConformanceContractTest` is registered in `LEGITIMATE_GOVERNANCE_GUARDS`.
  5. *Database Layer Upward Import Prohibition Test (`TST-PRO-050.5`, `ArchitectureConformanceContractTest.kt`)*:
  • Recursively scan all `.java` and `.kt` files in `database/` packages (`trainingtracker/database`, `banalservice/database`, `database`).
  • Assert that zero persistence classes import `androidx.compose.*` or presentation UI screens/composables.


### TST-PRO-051: **Android Instrumented Test Infrastructure & Emulator CI Contract Test Suite** [Verified]
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Requirement(s)**: `REQ-PRO-051`
- **Component**: `app/build.gradle`, `.github/workflows/android-branch-build.yml`, `app/src/androidTest/java/com/atrainingtracker/trainingtracker/AppSmokeAndroidTest.kt`, `ReleaseCiAndLintConfigContractTest.kt`
- **Jira**: `ATT-6184, ATT-6737`
- **Test Description**:
  1. *Build Script Instrumentation Runner & Dependency Conformance Test (`TST-PRO-051.1`, `ReleaseCiAndLintConfigContractTest.kt`)*:
  • Read `app/build.gradle`.
  • Assert that `testInstrumentationRunner` is configured as `"androidx.test.runner.AndroidJUnitRunner"`.
  • Assert that `androidTestImplementation` dependencies for `androidx.test.ext:junit`, `androidx.test.espresso:espresso-core`, `androidx.test:runner`, and `androidx.test:rules` are present.
  2. *CI Workflow Pre-Build Step, API 34 Target & Artifact Upload Conformance Test (`TST-PRO-051.2`, `ReleaseCiAndLintConfigContractTest.kt`)*:
  • Read `.github/workflows/android-branch-build.yml`.
  • Assert that the workflow contains a pre-build step executing `:app:assembleDebug :app:assembleDebugAndroidTest` prior to launching the emulator runner.
  • Assert that the workflow configures `reactivecircus/android-emulator-runner@v2` targeting `api-level: 34`, `target: google_apis`, and `arch: x86_64`.
  • Assert that the workflow executes `connectedDebugAndroidTest` and uploads instrumented test results via `actions/upload-artifact`.
  3. *AndroidTest Compilation & Smoke Test Lifecycle Verification (`TST-PRO-051.3`)*:
  • Verify existence of `app/src/androidTest/java/com/atrainingtracker/trainingtracker/AppSmokeAndroidTest.kt`.
  • Verify that `AppSmokeAndroidTest.kt` references `MainActivityWithNavigation`, `GrantPermissionRule`, and synthetic tracking start/stop with `WorkoutDeletionHelper`.
  • Execute `./gradlew compileDebugAndroidTestKotlin :app:assembleDebug :app:assembleDebugAndroidTest` and assert successful compilation without errors.
  4. *Clean-Room Targeted Test Suite Execution (`TST-PRO-051.4`)*:
  • Execute targeted unit test suite `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.build.ReleaseCiAndLintConfigContractTest"` asserting 100% pass rate.


### TST-PRO-052: **Architectural Resource and Timing Budgets Contract Test Suite** [Verified]
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Requirement(s)**: `REQ-PRO-052`, `REQ-NFR-003`, `REQ-NFR-004`, `REQ-NFR-005`, `REQ-NFR-006`, `REQ-NFR-007`, `REQ-NFR-008`, `REQ-NFR-009`, `REQ-NFR-010`, `REQ-NFR-011`, `REQ-NFR-012`, `REQ-NFR-013`, `REQ-NFR-014`
- **Component**: `docs/architecture.md`, `app/src/test/java/com/atrainingtracker/trainingtracker/build/ArchitectureConformanceContractTest.kt`
- **Jira**: `ATT-6181`, `ATT-6735`
- **Test Description**:
  1. *Architecture Section 4.9 Structural & Sectional Conformance Test (`TST-PRO-052.1`, `ArchitectureConformanceContractTest.kt`)*:
  • Read `docs/architecture.md`.
  • Assert that Section 4.9 ("Resource and Timing Budgets") is present.
  • Assert that all 4 critical subsystem domains (`TrackerService`, `BANALService`, `WorkoutSamplesDatabaseManager`, and Cockpit UI/Map) are documented.
  2. *Quantitative Threshold Verification Test (`TST-PRO-052.2`, `ArchitectureConformanceContractTest.kt`)*:
  • Assert that Section 4.9 specifies numeric bounds for CPU, heap memory, latency, battery drain, and disk growth.
  3. *WakeLock Attribution & Threading Conformance Test (`TST-PRO-052.3`, `ArchitectureConformanceContractTest.kt`)*:
  • Assert that `PARTIAL_WAKE_LOCK` (`aTrainingTracker:TrackingLock`) is attributed to `TrackerService` under Tracking Core domain.
  • Assert that `BANALService` has zero WakeLock attribution.
  • Assert that 1-Hz database write threading references `TrackerService`'s `mScheduler` / `mDbExecutor`.
  4. *Non-Functional Requirement Link Conformance Test (`TST-PRO-052.4`, `ArchitectureConformanceContractTest.kt`)*:
  • Assert that every budget row in Section 4.9 links to its concrete `REQ-NFR-003` through `REQ-NFR-014` identifier.
  5. *Clean-Room Targeted Test Suite Execution (`TST-PRO-052.5`)*:
  • Execute targeted unit test suite `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.build.ArchitectureConformanceContractTest"` asserting 100% pass rate.


### TST-PRO-053: **System Context Specification and Code-Grounding Contract Test Suite** [Verified]
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Requirement(s)**: `REQ-PRO-053`
- **Component**: `docs/system/system_context.md`, `app/src/test/java/com/atrainingtracker/trainingtracker/build/SystemContextContractTest.kt`
- **Jira**: `ATT-6158`, `ATT-6738`
- **Test Description**:
  1. *System Context Structural & Diagram Verification (`TST-PRO-053.1`, `SystemContextContractTest.kt`)*:
  • Read `docs/system/system_context.md`.
  • Assert that the document exists and contains a valid Mermaid diagram (`flowchart` or `graph`).
  • Assert that all 5 interface domains (Sensors, Platform, Location/Maps, Cloud/Health, Decommissioned) are documented.
  2. *External Elements & Source Grounding Verification (`TST-PRO-053.2`, `SystemContextContractTest.kt`)*:
  • Assert that BLE GATT profiles defined in `docs/system/system_context.md` match UUIDs in `BluetoothConstants.java` (including `0x180A`, Varia V1, Light Control).
  • Assert that hardware sensors match `Sensor.TYPE_PRESSURE` and `Sensor.TYPE_PROXIMITY`.
  • Assert that FGS execution types match `location|health` in `AndroidManifest.xml` and `TrackerService.java`.
  • Assert that targetSdk 37 is labeled as Android 17.
  • Assert that cloud SDK dependencies match entries in `app/build.gradle` (Dropbox, PebbleKit, Health Connect, Open-Meteo, Firebase BOM, Google Play Auth/Review).
  • Assert that Runkeeper and TrainingPeaks are explicitly tagged as decommissioned.
  3. *Dynamic Filesystem & Symbol Grounding Verification (`TST-PRO-053.3`, `SystemContextContractTest.kt`)*:
  • Dynamically extract all backticked `.kt`, `.java`, `.xml` filenames and verify 100% exist on disk in the repository.
  • Dynamically extract all `Sensor.TYPE_*` and `FOREGROUND_SERVICE_TYPE_*` tokens and verify they match authentic production code.
  4. *Clean-Room Targeted Test Suite Execution (`TST-PRO-053.4`)*:
  • Execute targeted unit test suite `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.build.SystemContextContractTest"` asserting 100% pass rate.


### TST-PRO-054: **System Requirements for Live Tracking Contract Test Suite** [Verified]
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Requirement(s)**: `REQ-PRO-054`
- **Component**: `docs/system/sysreq_live_tracking.md`, `app/src/test/java/com/atrainingtracker/trainingtracker/build/SystemLiveTrackingRequirementsContractTest.kt`
- **Jira**: `ATT-6160`, `ATT-6739`
- **Test Description**:
  1. *Specification Structure & SYSREQ Coverage Verification (`TST-PRO-054.1`, `SystemLiveTrackingRequirementsContractTest.kt`)*:
  • Read `docs/system/sysreq_live_tracking.md`.
  • Assert that the document exists and contains all 6 requirements (`SYSREQ-TRK-001` through `SYSREQ-TRK-006`).
  • Assert that each requirement contains non-empty sections for Rationale, Priority, Verification Method, and Traced Software Components.
  2. *Code Grounding & Timing Constants Verification (`TST-PRO-054.2`, `SystemLiveTrackingRequirementsContractTest.kt`)*:
  • Assert that BLE GATT profiles defined in `SYSREQ-TRK-001` match UUIDs in `BluetoothConstants.java`.
  • Assert that reconnection delays in `SYSREQ-TRK-002` match `MyBTLEDevice.INITIAL_RECONNECT_DELAY_MS` (1,000 ms) and `MAX_RECONNECT_DELAY_MS` (16,000 ms).
  • Assert that radar starvation watchdog matches `BTLERadarDevice.WATCHDOG_TIMEOUT_MS` (3,500 ms).
  • Assert that 1-Hz technical sampling tick matches `TrackerService` 1,000 ms period.
  • Assert dynamic `File.exists()` on disk for all backticked `.kt` and `.java` source and test files cited in `docs/system/sysreq_live_tracking.md`.
  3. *Clean-Room Targeted Test Suite Execution (`TST-PRO-054.3`)*:
  • Execute targeted unit test suite `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.build.SystemLiveTrackingRequirementsContractTest"` asserting 100% pass rate.


### TST-PRO-055: **System Requirements for Data Exchange Contract Test Suite** [Verified]
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Requirement(s)**: `REQ-PRO-055`
- **Component**: `docs/system/sysreq_data_exchange.md`, `docs/system_requirements.md`, `app/src/test/java/com/atrainingtracker/trainingtracker/build/SystemDataExchangeRequirementsContractTest.kt`, `app/src/test/java/com/atrainingtracker/trainingtracker/build/SystemRequirementsSpecificationContractTest.kt`
- **Jira**: `ATT-6161`, `ATT-6740`
- **Test Description**:
  1. *Specification Structure & SYSREQ Coverage Verification (`TST-PRO-055.1`, `SystemDataExchangeRequirementsContractTest.kt` & `SystemRequirementsSpecificationContractTest.kt`)*:
  • Read `docs/system/sysreq_data_exchange.md` and `docs/system_requirements.md`.
  • Assert that `sysreq_data_exchange.md` exists and contains all 6 requirements (`SYSREQ-DEX-001` through `SYSREQ-DEX-006`).
  • Assert that each requirement contains non-empty sections for Rationale, Priority, Verification Method, and Traced Software Components.
  • Assert that `docs/system_requirements.md` defines sequential section headings 1 through 4.
  2. *Code Grounding, Filename Verification & Data Exchange Verification (`TST-PRO-055.2`, `SystemDataExchangeRequirementsContractTest.kt`)*:
  • Assert that export formats in `SYSREQ-DEX-001` match `FileFormat` enum entries (`FIT`, `TCX`, `GPX`, `CSV`).
  • Assert that backup archive constants in `SYSREQ-DEX-005` match `BackupManager.BACKUP_FILENAME` (`"aTrainingTracker_backup.attbackup"`).
  • Assert that credential exclusions in `SYSREQ-DEX-006` match `auth_credentials_prefs.xml` and Android Auto Backup XMLs `full_backup_content.xml` and `data_extraction_rules.xml`.
  • Assert that Runkeeper and TrainingPeaks are explicitly demarcated as decommissioned.
  • Assert dynamic `File.exists()` on disk for all cited source, resource, and test files (`FitFileWriterTest.kt`, `TCXRoundTripTest.kt`, `LegacyImportEngine.kt`, `WorkoutDataMapper.kt`, `StravaUploader.kt`, `GoogleDriveUploader.kt`, `StravaHelper.kt`, `StravaUploadDbHelper.java`, `DatabaseConnectionManager.kt`, `DatabaseSchemaRegistry.kt`, `WorkoutSummariesDatabaseManager.java`, `full_backup_content.xml`, `data_extraction_rules.xml`).
  3. *Clean-Room Targeted Test Suite Execution (`TST-PRO-055.3`)*:
  • Execute targeted unit test suite `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.build.SystemDataExchangeRequirementsContractTest" --tests "com.atrainingtracker.trainingtracker.build.SystemRequirementsSpecificationContractTest"` asserting 100% pass rate.


### TST-PRO-056: **System Architectural Design Contract Test Suite** [Verified]
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Requirement(s)**: `REQ-PRO-056`
- **Component**: `docs/system/system_architecture.md`, `app/src/test/java/com/atrainingtracker/trainingtracker/build/SystemArchitectureDesignContractTest.kt`
- **Jira**: `ATT-6164`, `ATT-6741`
- **Test Description**:
  1. *System Elements & Interfaces Coverage Verification (`TST-PRO-056.1`, `SystemArchitectureDesignContractTest.kt`)*:
  • Read `docs/system/system_architecture.md`.
  • Assert that the document exists and defines all 5 system elements (`SYS-EL-01` through `SYS-EL-05`) and 6 system interfaces (`SYS-IF-01` through `SYS-IF-06`).
  • Assert protocol, payload data schema, and timing characteristics are documented for each interface.
  2. *Dynamic Behavior & Mermaid Syntax Verification (`TST-PRO-056.2`, `SystemArchitectureDesignContractTest.kt`)*:
  • Assert that `docs/system/system_architecture.md` contains valid Mermaid `sequenceDiagram` fences for live 1-Hz telemetry acquisition and post-workout cloud export/backup.
  • Assert that Diagram 4.2 accurately models workout stop from `ControlTrackingScreen` / notification action delegating session finalization and export triggering to `TrackerService` (`mDbExecutor`).
  3. *100% Bidirectional System Requirements Allocation Verification (`TST-PRO-056.3`, `SystemArchitectureDesignContractTest.kt`)*:
  • Assert that the System Requirements Allocation Table allocates 100% of defined `SYSREQ-TRK-001`..`006` and `SYSREQ-DEX-001`..`006` requirements to system elements.
  • Assert exact bidirectional set equality (`declaredSysreqs == allocatedSysreqs`) ensuring zero orphan or undeclared `SYSREQ-*` IDs exist.
  4. *Clean-Room Targeted Test Suite Execution (`TST-PRO-056.4`)*:
  • Execute targeted unit test suite `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.build.SystemArchitectureDesignContractTest"` asserting 100% pass rate.
  5. *Dynamic Production Code Grounding Reflection Verification (`TST-PRO-056.5`, `SystemArchitectureDesignContractTest.kt`)*:
  • Verify existence of referenced production classes via dynamic reflection (`Class.forName`): `RadarEdgeOverlayKt`, `RadarAlertManager`, `RadarAudioSynthesizer`, `NavigationAudioAlertManager`, `BikeBellAudioSynthesizer`, `SpeedAndLocationDevice_GoogleFused`.
  • Verify existence of referenced XML resource files via `File.exists()`: `app/src/main/res/xml/full_backup_content.xml`.
  • Verify referenced application process ID matches `com.atrainingtracker`.

### TST-PRO-057: **Configuration Management Repository Grounding & cm_plan.md Parity Contract Test Suite** [Verified]
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Requirement(s)**: `REQ-PRO-057`
- **Component**: `docs/management/cm_plan.md`, `ReleaseCiAndLintConfigContractTest.kt`
- **Jira**: `ATT-6733`
- **Test Description**:
  1. *Production Branch 'master' Grounding & 'main' Absence Test (`TST-PRO-057.1`, `ReleaseCiAndLintConfigContractTest.kt`)*:
  • Read `docs/management/cm_plan.md`.
  • Assert that the production release branch is documented as `master` (matching `origin/master`).
  • Assert that zero references to `main` as a branch name exist in `cm_plan.md`.
  2. *Sprint Milestone & Release Tagging Nomenclature Test (`TST-PRO-057.2`, `ReleaseCiAndLintConfigContractTest.kt`)*:
  • Assert that `docs/management/cm_plan.md` documents `sprint/<SPRINT_NAME>` milestone tags and both `V<versionName>__<versionCode>` and `V<versionName>` release tag formats.
  3. *Controlled Configuration Items Inventory Completeness Test (`TST-PRO-057.3`, `ReleaseCiAndLintConfigContractTest.kt`)*:
  • Assert that the CI inventory table lists `docs/architecture/*.md`, `docs/management/*.md`, `config/detekt/`, `tools/`, and `.agents/`.
  4. *Formal Release Contents Specification Test (`TST-PRO-057.4`, `ReleaseCiAndLintConfigContractTest.kt`)*:
  • Assert that `docs/management/cm_plan.md` includes a dedicated Release Contents subsection specifying signed APK, Play Store AAB, `docs/store_presence/` localized notes, and Jira FixVersion baseline traceability.
  5. *Clean-Room Targeted Test Suite Execution (`TST-PRO-057.5`)*:
  • Execute targeted unit test suite `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.build.ReleaseCiAndLintConfigContractTest"` asserting 100% pass rate.

### TST-TST-004: **Android Emulator Lifecycle Management, Physical-Phone Guardrail, and Two-Tier Verification Suite** [Verified]
- **Priority**: Medium
- **Type**: Non-Functional
- **Verification Method**: Unit
- **Requirement(s)**: `REQ-TST-004`
- **Component**: `tools/agent_emulator.py`, `tools/fake_gps.py`, `tools/simulate_kill.py`, `tools/replay_workout.py`, `tools/test_agent_emulator.py`
- **Jira**: `ATT-6506`
- **Test Description**:
  1. *ADB Device Table Parsing & Filtering Tests (`TST-TST-004.1`, tools/test_agent_emulator.py)*:
  • Assert parsing of `adb devices -l` output correctly partitions devices into emulators (`emulator-*`) and physical USB devices.
  • Assert multi-device resolution prioritizes `emulator-5554` when present.
  2. *Physical-Phone Protection & Agent Mode Guardrail Tests (`TST-TST-004.2`, tools/test_agent_emulator.py)*:
  • Assert `resolve_target_device` aborts with `RuntimeError` or `SystemExit` if targeting a physical USB serial when `--agent` or `ATT_AGENT_MODE=1` is set.
  • Assert explicit `--serial <SERIAL>` overrides default behavior when valid.
  3. *Emulator Lifecycle & Configuration Command Generation Tests (`TST-TST-004.3`, tools/test_agent_emulator.py)*:
  • Assert `ensure` constructs proper CLI arguments for `~/Android/Sdk/emulator/emulator -avd <AVD_NAME>`.
  • Assert boot verification queries `getprop sys.boot_completed`.
  • Assert window/transition/animator scale zeroing and keyguard dismiss commands.
  • Assert `configure-app` generates correct `pm grant` and `appops set` commands for `com.atrainingtracker.debug`.
  4. *Database Seeding & Extraction Pipeline Tests (`TST-TST-004.4`, tools/test_agent_emulator.py)*:
  • Assert `seed-db` and `sync-from-physical` construct valid `run-as com.atrainingtracker.debug` SQLite extraction and push commands.
  5. *Governance & Skills Two-Tier Policy Parity Tests (`TST-TST-004.5`, tools/test_agent_emulator.py)*:
  • Assert `.agents/rules/aspice_governance.md`, `stage5-verification/SKILL.md`, `sprint-planner/SKILL.md`, `ui-designer/SKILL.md`, and `docs/project_protocol.md` mandate `emulator-5554` for Stages 1–5 and physical device for Ceremony 2.
  6. *Python Standard Library Compliance Test (`TST-TST-004.6`, tools/test_agent_emulator.py)*:
  • Assert `tools/agent_emulator.py` imports only standard library modules complying with `TST-PRO-005.6`.

### TST-PRO-058: **Stage 6 Cold Carbon Final Review (Agent), 3-Bounce Healing Loop & Gate 6 Metrics Verification Suite** [Implemented]
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Requirement(s)**: `REQ-PRO-058`
- **Component**: `tools/jira_util.py`, `tools/sprint_metrics.py`, `tools/hoechstmass/tools/jira_util.py`, `tools/hoechstmass/tools/sprint_metrics.py`, `.agents/skills/stage6-final-review/SKILL.md`, `tools/test_jira_epics.py`, `tools/test_sprint_metrics.py`, `HoechstmassCoreFrameworkContractTest.kt`
- **Jira**: `ATT-7101`
- **Test Description**:
  1. *Parent Stage Order & Stage 5 -> Final Review (Agent) Resolution Tests (`TST-PRO-058.1`, tools/test_jira_epics.py)*:
  • Assert `PARENT_STAGE_ORDER` contains `"final review (agent)"` immediately after `"test"` and immediately before `"final review (human)"`.
  • Assert `STAGE_TO_SKILL_DIR["final review (agent)"] == "stage6-final-review"`.
  • Assert `resolve_desired_parent_stage_for_subtask("[Test] Verification", True)` returns `"final review (agent)"`.
  • Assert `transition_issue` disambiguates `"final review (agent)"` (assigning to `agent2`) from `"final review (human)"` (assigning to `human`).
  2. *Composite `final-review-verdict` PASS, 3-Bounce Healing & Escalation Tests (`TST-PRO-058.2`, tools/test_jira_epics.py)*:
  • Assert `final_review_verdict(key, "PASS", ...)` posts `[FINAL REVIEW (AGENT) — PASS]`, transitions to `Final Review (Human)`, and assigns to `human`.
  • Assert `final_review_verdict(key, "BOUNCE", ...)` increments bounce counter `1/3..3/3`, transitions back to `Implementation` (or `Analysis` / `Zu erledigen`), and assigns to `agent1`.
  • Assert that a 4th bounce (`bounce_count > 3`) posts `[FINAL REVIEW (AGENT) — ESCALATED AFTER 3 BOUNCES]`, transitions to `Final Review (Human)`, and assigns to `human`.
  • Assert parity with `tools/hoechstmass/tools/jira_util.py`.
  3. *Stage 6 Skill & Governance Contract Tests (`TST-PRO-058.3`, HoechstmassCoreFrameworkContractTest.kt)*:
  • Assert `.agents/skills/stage6-final-review/SKILL.md` and `tools/hoechstmass/.agents/skills/stage6-final-review/SKILL.md` exist, declare `name: stage6-final-review`, and document the 4-part Cold Audit Checklist, Executive Review Summary, and 3-bounce healing loop.
  4. *Gate 6 Quantitative Metrics & Swiss Cheese $p_6$ Tests (`TST-PRO-058.4`, tools/test_sprint_metrics.py)*:
  • Assert `parse_final_review_agent_verdicts_from_comments` extracts `PASS` and `BOUNCE` verdicts from `[FINAL REVIEW (AGENT) — ...]` parent ticket comments.
  • Assert `parse_sprint_gate_metrics` computes `gate_6` (`Final Review (Agent)`) FTPR, bounce count, and `p6_final_review_agent_escape` in `swiss_cheese`.

### TST-PRO-059: **Sprint Gate Record Export & Repository Verification** [Verified]
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Requirement(s)**: `REQ-PRO-059`
- **Component**: `tools/jira_util.py`, `tools/verify_requirement_governance.py`, `tools/hoechstmass/tools/jira_util.py`, `tools/hoechstmass/tools/verify_requirement_governance.py`, `tools/test_jira_accounts.py`, `tools/test_verify_requirement_governance.py`, `tools/hoechstmass/tools/test_hoechstmass_tools.py`
- **Jira**: `ATT-6149`
- **Test Description**:
  1. *`export-gates` CLI Command & Markdown Generation (`TST-PRO-059.1`, tools/test_jira_accounts.py & tools/hoechstmass/tools/test_hoechstmass_tools.py)*:
  • Execute `export_gates` with mock Jira response; assert generated markdown contains Sprint ID, summary table, and detailed verdict log with role, actor, timestamp, and verdict.
  2. *Post-Sprint Gate Verification Check (`TST-PRO-059.2`, tools/test_verify_requirement_governance.py)*:
  • Run `verify_gate_records` against valid vs missing/empty sprint files; assert return status and diagnostic error messages.
  3. *Canonical Directory Registration (`TST-PRO-059.3`, tools/test_verify_requirement_governance.py)*:
  • Assert `"gates"` exists in `ALLOWED_ENGINEERING_ENTRIES` and `hoechstmass_config.json`.

### TST-PRO-060: **Sprint Metrics Persistence, Story Point Governance & Query Optimization** [Verified]
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Requirement(s)**: `REQ-PRO-060`
- **Component**: `tools/sprint_metrics.py`, `tools/jira_util.py`, `tools/verify_requirement_governance.py`, `tools/hoechstmass/tools/jira_util.py`, `tools/hoechstmass/tools/verify_requirement_governance.py`, `tools/test_sprint_metrics.py`, `tools/test_verify_requirement_governance.py`, `tools/hoechstmass/tools/test_hoechstmass_tools.py`
- **Jira**: `ATT-6150`
- **Test Description**:
  1. *Story Point Extraction & Schema Persistence (`TST-PRO-060.1`, tools/test_sprint_metrics.py)*:
  • Verify `parse_sprint_gate_metrics` populates `total_story_points` and `completed_story_points` from custom fields.
  2. *Governance Verification Check (`TST-PRO-060.2`, tools/test_verify_requirement_governance.py)*:
  • Run `verify_sprint_metrics` against valid, missing, empty, and zero-story-point files; assert pass/fail responses.
  3. *Counterpart Parity & Indexing Optimization (`TST-PRO-060.3`, tools/hoechstmass/tools/test_hoechstmass_tools.py)*:
  • Assert `verify_sprint_metrics` parity and verify `collect_sprint_metrics_for_id` uses pre-indexed comments without extra network requests.

### TST-PRO-061: **Sprint-Level QA Process-Compliance Audit Suite** [Verified]
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Requirement(s)**: `REQ-PRO-061`
- **Component**: `tools/jira_util.py`, `tools/verify_requirement_governance.py`, `tools/hoechstmass/tools/jira_util.py`, `tools/hoechstmass/tools/verify_requirement_governance.py`, `tools/test_jira_accounts.py`, `tools/test_verify_requirement_governance.py`, `tools/hoechstmass/tools/test_hoechstmass_tools.py`
- **Jira**: `ATT-6154`
- **Test Description**:
  1. *`qa-audit` CLI Command & Report Generation (`TST-PRO-061.1`, tools/test_jira_accounts.py & tools/hoechstmass/tools/test_hoechstmass_tools.py)*:
  • Execute `qa_audit` with mock Jira response; verify sampled tickets, 5-point checklist evaluation, and markdown formatting.
  2. *Post-Sprint QA Audit Verification (`TST-PRO-061.2`, tools/test_verify_requirement_governance.py)*:
  • Run `verify_qa_audits` against valid vs missing, empty, and $<2$ sample files; assert pass/fail status and diagnostics.
  3. *Counterpart Parity & Tooling Integration (`TST-PRO-061.3`, tools/hoechstmass/tools/test_hoechstmass_tools.py)*:
  • Assert `qa_audit` and `verify_qa_audits` in Höchstmaß mirror match root tooling behavior.
  4. *Canonical Directory Registration (`TST-PRO-061.4`, tools/test_verify_requirement_governance.py)*:
  • Assert `"qa_audits"` exists in `ALLOWED_ENGINEERING_ENTRIES` and `hoechstmass_config.json`.

### TST-PRO-062: **Independent QA & Cross-Vendor Review Evidence Suite** [Verified]
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Requirement(s)**: `REQ-PRO-062`
- **Component**: `tools/jira_util.py`, `tools/verify_requirement_governance.py`, `tools/hoechstmass/tools/jira_util.py`, `tools/hoechstmass/tools/verify_requirement_governance.py`, `tools/test_jira_accounts.py`, `tools/test_verify_requirement_governance.py`, `tools/hoechstmass/tools/test_hoechstmass_tools.py`
- **Jira**: `ATT-6155`, `ATT-8142`
- **Test Description**:
  1. *`record-review` CLI Command & Report Generation (`TST-PRO-062.1`, tools/test_jira_accounts.py & tools/hoechstmass/tools/test_hoechstmass_tools.py)*:
  • Execute `record-review` for Human Ceremony 2 review and Cross-Vendor AI review; verify structured markdown output and metadata serialization.
  • Test that `record-review` with `--verdict PASS` or `FAIL` without `--findings` fails with usage error.
  • Test that `record-review` with `--verdict SKIPPED` or `NOT PERFORMED` generates truthful record without default success text and includes Rule 1.3 deviation header.
  • Test invalid verdict string is rejected.
  2. *Independent Review Governance Verification (`TST-PRO-062.2`, tools/test_verify_requirement_governance.py)*:
  • Run `verify_independent_reviews` against valid vs missing and malformed review records, including `PASS`, `FAIL`, `SKIPPED`, `NOT PERFORMED`, and `PARTIAL`; assert pass/fail status and schema diagnostics.
  3. *Review File Correction & Evidence Integrity (`TST-PRO-062.3`, docs/engineering/reviews/sprint_2026-46.6.md)*:
  • Verify `docs/engineering/reviews/sprint_2026-46.6.md` contains verdict `SKIPPED` and correction note referencing Finding K2.
  4. *Counterpart Parity & Tooling Integration (`TST-PRO-062.4`, tools/hoechstmass/tools/test_hoechstmass_tools.py)*:
  • Assert `record-review` and `verify_independent_reviews` in Höchstmaß mirror match root tooling behavior.
  5. *Canonical Directory Registration (`TST-PRO-062.5`, tools/test_verify_requirement_governance.py)*:
  • Assert `"reviews"` exists in `ALLOWED_ENGINEERING_ENTRIES` and `hoechstmass_config.json`.

### TST-PRO-063: **Problem Severity Classification & Defect Trend Metrics Suite** [Verified]
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Requirement(s)**: `REQ-PRO-063`
- **Component**: `tools/jira_util.py`, `tools/sprint_metrics.py`, `tools/verify_requirement_governance.py`, `tools/hoechstmass/tools/jira_util.py`, `tools/hoechstmass/tools/sprint_metrics.py`, `tools/hoechstmass/tools/verify_requirement_governance.py`, `tools/test_jira_accounts.py`, `tools/test_sprint_metrics.py`, `tools/hoechstmass/tools/test_sprint_metrics.py`, `tools/hoechstmass/tools/test_hoechstmass_tools.py`
- **Jira**: `ATT-6156`
- **Test Description**:
  1. *Bug Creation Severity Mandate (`TST-PRO-063.1`, tools/test_jira_accounts.py & tools/hoechstmass/tools/test_hoechstmass_tools.py)*:
  • Test creating a Bug without severity fails with exit code 1; test creating a Bug with `--severity=critical` attaches `severity-critical` label and succeeds.
  • Test non-bug issue types (Features, Improvements) can be created without severity.
  2. *Defect Trend Metrics Extraction (`TST-PRO-063.2`, tools/test_sprint_metrics.py & tools/hoechstmass/tools/test_sprint_metrics.py)*:
  • Process sprint issues containing bugs with severity labels; verify `parse_sprint_gate_metrics()` aggregates bug counts by severity, open vs closed, and calculates defect escape/resolution rates.
  3. *Metrics Markdown Reporting & Schema Validation (`TST-PRO-063.3`, tools/test_sprint_metrics.py & tools/test_verify_requirement_governance.py)*:
  • Verify `format_metrics_markdown()` includes the "Problem Trends & Defect Severity" section; verify `verify_sprint_metrics()` accepts `problem_trends` schema fields.
  4. *Counterpart Parity & Tooling Integration (`TST-PRO-063.4`, tools/hoechstmass/tools/test_hoechstmass_tools.py & tools/hoechstmass/tools/test_sprint_metrics.py)*:
  • Assert Höchstmaß mirror tools maintain 100% functional parity for bug severity enforcement and metrics extraction.

### TST-PRO-064: **Change-Request Impact Analysis Governance Verification Suite** [Verified]
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Requirement(s)**: `REQ-PRO-064`
- **Component**: `tools/jira_util.py`, `tools/verify_requirement_governance.py`, `tools/hoechstmass/tools/jira_util.py`, `tools/hoechstmass/tools/verify_requirement_governance.py`, `tools/test_jira_accounts.py`, `tools/test_verify_requirement_governance.py`, `tools/hoechstmass/tools/test_hoechstmass_tools.py`
- **Jira**: `ATT-6157`
- **Test Description**:
  1. *Impact Analysis Section Validation (`TST-PRO-064.1`, tools/test_verify_requirement_governance.py & tools/hoechstmass/tools/test_hoechstmass_tools.py)*:
  • Test `validate_impact_analysis_section()` against valid markdown containing all 4 dimensions (Requirements, Architecture, Tests, Risks) and assert True.
  • Test invalid markdown (missing section, missing dimension, empty or placeholder text) and assert False / failure diagnostics.
  2. *Governance CLI `--check-impact` Flag (`TST-PRO-064.2`, tools/test_verify_requirement_governance.py)*:
  • Test `verify_stage1_impact_analysis(analysis_file)` and CLI invocation `--check-impact [FILE]` verifying exit code 0 on valid deliverables and exit code 1 with actionable errors on invalid files.
  3. *Gate 1 Impact Analysis Verification (`TST-PRO-064.3`, tools/test_jira_accounts.py & tools/hoechstmass/tools/test_hoechstmass_tools.py)*:
  • Test `gate-verdict` on `[Analysis]` subtask with missing/incomplete impact analysis fails with error; test valid deliverable passes Gate 1.
  • Test non-analysis subtasks (Gates 2..5) proceed normally without triggering Stage 1 impact analysis check.
  4. *Counterpart Parity & Template Verification (`TST-PRO-064.4`, tools/hoechstmass/tools/test_hoechstmass_tools.py)*:
  • Assert both `.agents/skills/stage1-analysis/templates/analysis_template.md` and `tools/hoechstmass/templates/analysis_template.md` contain the standardized Change Impact Analysis section.
  • Assert Höchstmaß mirror tools maintain 100% functional parity with root tooling.

### TST-PRO-065: **Living Documentation Sharding Code-Span Pipe Splitting & Master Index Safety Guard Verification Suite** [Verified]
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Requirement(s)**: `REQ-PRO-065`
- **Component**: `tools/shard_living_docs.py`, `tools/test_shard_living_docs.py`
- **Jira**: `ATT-6124`
- **Test Description**:
  1. *Code-Span Pipe Splitting & Pre-Fix Reproduction (`TST-PRO-065.1`, tools/test_shard_living_docs.py)*:
  • Test `split_markdown_table_row()` against single backticks (`...`), double backticks (``...``), escaped pipes (`\|`), and code snippets containing `||` (e.g. `REQ-EXP-021` snippet).
  • Assert reproduction: demonstrate that naive `row.split("|")` splits into 9 parts corrupting `Tests`, whereas `split_markdown_table_row()` preserves exact 7 columns with intact `Tests = TST-EXP-018`.
  2. *6-Column and 7-Column Requirement Parsing (`TST-PRO-065.2`, tools/test_shard_living_docs.py)*:
  • Parse synthetic requirements table with both 6-column and 7-column rows, verifying Title, Description, Rationale, Implementation, Tests, and Status are correctly populated without index shifts.
  3. *Master Index Overwrite Safety Guard (`TST-PRO-065.3`, tools/test_shard_living_docs.py)*:
  • Invoke `shard_living_docs.py` against a directory where `docs/requirements.md` is a Master Index; verify process exits with code 1 and existing shards are preserved.
  • Test `--force` bypass allowing overwrite when explicitly requested.
  4. *Clean Execution & Parity Verification (`TST-PRO-065.4`, tools/test_shard_living_docs.py)*:
  • Verify `--verify-only` succeeds on current repository living documentation shards.

### TST-PRO-066: **Living Documentation Unique Requirement & Test Identifier Governance Verification Suite** [Verified]
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Requirement(s)**: `REQ-PRO-066`
- **Component**: `tools/verify_requirement_governance.py`, `tools/test_verify_requirement_governance.py`
- **Jira**: `ATT-6120`, `ATT-6123`
- **Test Description**:
  1. *Duplicate Heading ID Detection & Detailed Reporting (`TST-PRO-066.1`, tools/test_verify_requirement_governance.py)*:
  • Create synthetic requirements and test shards in a temporary directory containing duplicated IDs.
  • Verify `verify_unique_ids()` detects each duplicated ID and formats error diagnostics reporting the exact identifier, filename, and line numbers of all occurrences.
  2. *Legacy Duplicate Baseline Tolerance & New Duplicate Rejection (`TST-PRO-066.2`, tools/test_verify_requirement_governance.py)*:
  • Verify that duplicate IDs present in `legacy_allowlist` produce warnings but return `success = True` in standard verification mode.
  • Verify that adding any single new duplicate outside the allowlist causes `verify_unique_ids()` to return `success = False` and triggers exit code 1.
  3. *Strict Uniqueness Enforcement Option (`TST-PRO-066.3`, tools/test_verify_requirement_governance.py)*:
  • Verify that when `fail_on_duplicates=True` (or CLI `--enforce-unique`), duplicates trigger `success = False` and exit code 1.
  4. *Clean Repository Governance Parity (`TST-PRO-066.4`, tools/test_verify_requirement_governance.py)*:
  • Verify that running `verify_unique_ids()` and `verify_unique_ids(fail_on_duplicates=True)` against the authentic repository passes cleanly with 0 duplicates and zero warnings.

### TST-PRO-067: **JUnit Test Results Archival & Evidence Integrity Contract Test Suite** [Verified]
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Requirement(s)**: `REQ-PRO-067`
- **Component**: `.github/workflows/android-branch-build.yml`, `.github/workflows/android-release.yml`, `docs/engineering/test_results`, `app/src/test/java/com/atrainingtracker/trainingtracker/build/ReleaseCiAndLintConfigContractTest.kt`, `tools/verify_requirement_governance.py`, `tools/test_verify_requirement_governance.py`
- **Jira**: `ATT-6137`
- **Test Description**:
  1. *CI Branch Build Workflow Archival Test (`TST-PRO-067.1`, ReleaseCiAndLintConfigContractTest.kt)*:
  • Inspect `.github/workflows/android-branch-build.yml`.
  • Assert presence of an artifact upload step configured with `if: always()` and step name indicating unit test report archival.
  • Assert artifact paths include `app/build/test-results/testDebugUnitTest/` and `app/build/reports/tests/testDebugUnitTest/`.
  2. *Release Workflow Test Results Packaging & Attachment Test (`TST-PRO-067.2`, ReleaseCiAndLintConfigContractTest.kt)*:
  • Inspect `.github/workflows/android-release.yml`.
  • Assert presence of step packaging `junit-test-results-<tag>.zip` (or `.tar.gz`) from unit test report directories.
  • Assert that release assets attached via `softprops/action-gh-release@v2` include the versioned test results archive pattern.
  • Assert workflow artifact upload for release test results.
  3. *Test Results Symlink Retirement & Evidence Directory Contract Test (`TST-PRO-067.3`, ReleaseCiAndLintConfigContractTest.kt & tools/verify_requirement_governance.py)*:
  • Assert `docs/engineering/test_results` symlink to transient build output is retired.
  • Assert `docs/engineering/test_evidence/` is established as canonical directory in living docs and governance scripts.
  4. *Governance Tooling Integration (`TST-PRO-067.4`, tools/test_verify_requirement_governance.py)*:
  • Assert `verify_engineering_directory_structure()` in `tools/verify_requirement_governance.py` validates canonical `test_evidence/` layout and rejects obsolete symlink.
  5. *Workflow YAML Syntax Validation (`TST-PRO-067.5`, tools/test_verify_requirement_governance.py & ReleaseCiAndLintConfigContractTest.kt)*:
  • Parse `.github/workflows/android-branch-build.yml` and `.github/workflows/android-release.yml` with YAML parser and assert zero syntax errors.

### TST-PRO-068: **Developer Sensor Simulator Self-Test Harness Contract Test Suite** [Verified]
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Requirement(s)**: `REQ-PRO-068`
- **Component**: `docs/system/sys_integration_tests.md`, `tools/run_simulator_self_tests.py`, `tools/test_simulator_self_tests.py`, `app/src/test/java/com/atrainingtracker/trainingtracker/build/SystemIntegrationTestContractTest.kt`
- **Jira**: `ATT-6166`, `ATT-8157`
- **Test Description**:
  1. *System Integration Boundary & Gap Disclosure (`TST-PRO-068.1`, SystemIntegrationTestContractTest.kt)*:
  • Assert `docs/system/sys_integration_tests.md` exists, contains required metadata header, and explicitly discloses that Python simulators do not execute the Android APK or physical hardware.
  • Assert documentation covers external system interfaces (`SYS-IF-01` BLE, `SYS-IF-02` ANT+, `SYS-IF-03` GNSS, `SYS-IF-05` Cloud) and honest allocation across `INT-SWE-01..08`, `AppSmokeAndroidTest.kt`, and manual `SYS.5`.
  2. *System Requirements Bidirectional Traceability (`TST-PRO-068.2`, SystemIntegrationTestContractTest.kt)*:
  • Assert interface specifications trace to system requirements (`SYSREQ-TRK-*`, `SYSREQ-DEX-*`) and system elements (`SYS-EL-01`..`05`).
  3. *Simulator Self-Test Runner Execution (`TST-PRO-068.3`, tools/test_simulator_self_tests.py)*:
  • Execute `tools/run_simulator_self_tests.py` against real simulator modules (`ble_hr_simulator.py`, `ble_power_simulator.py`, `ble_radar_simulator.py`) and verify JSON report schema via `--output`, explicit simulator-only metadata, and exit code 0.
  4. *Simulator Self-Test Unit Suite (`TST-PRO-068.4`, tools/test_simulator_self_tests.py)*:
  • Verify `unittest` suite for simulator handlers, targeted filtering, CLI `--output` arguments, and pure Python 3 stdlib compliance passes cleanly.

### TST-PRO-069: **System Verification Protocol & Hardware Reference Specification Contract Test Suite** [Verified]
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Requirement(s)**: `REQ-PRO-069`
- **Component**: `docs/system/sys_verification_protocol.md`, `app/src/test/java/com/atrainingtracker/trainingtracker/build/SystemVerificationProtocolContractTest.kt`, `docs/architecture.md`
- **Jira**: `ATT-6167`
- **Test Description**:
  1. *Protocol Structural Integrity & Metadata Header (`TST-PRO-069.1`, SystemVerificationProtocolContractTest.kt)*:
  • Assert `docs/system/sys_verification_protocol.md` exists and contains required metadata header referencing ASPICE SYS.5, REQ-PRO-069, and TST-PRO-069.
  • Assert protocol contains Mermaid workflow and system verification topology diagrams.
  2. *Hardware Reference & Environmental Conditions Matrices (`TST-PRO-069.2`, SystemVerificationProtocolContractTest.kt)*:
  • Assert protocol specifies hardware matrix with Google Pixel reference family, BLE HRM (Polar H10/Garmin HRM-Pro), Cycling Power (Favero Assioma/Stages/Wahoo), Garmin Varia Radar (RTL515), and Barometric Altimeter / GNSS.
  • Assert protocol specifies 5 environmental test matrices (Urban, Rural, Climb, Indoor, Longevity).
  3. *System Verification Cases & SYSREQ Traceability (`TST-PRO-069.3`, SystemVerificationProtocolContractTest.kt)*:
  • Assert protocol specifies formal test cases (`SYS-VRF-001` through `SYS-VRF-008`).
  • Assert bidirectional coverage of all system requirements (`SYSREQ-TRK-001`..`006` and `SYSREQ-DEX-001`..`006`) with zero dangling IDs.
  4. *Objective Pass/Fail Criteria & Release Record Template (`TST-PRO-069.4`, SystemVerificationProtocolContractTest.kt)*:
  • Assert protocol defines pass/fail thresholds for FIT/TCX integrity, Strava/Dropbox sync, RF reconnection, sample loss $\le 1\%$, and battery drain $\le 8\%$/hr.
  • Assert protocol includes release sign-off record template with signature fields for release governance.

### TST-PRO-070: **Living Documentation Bidirectional Reference Integrity Test Suite** [Verified]
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Requirement(s)**: `REQ-PRO-070`
- **Component**: `tools/verify_requirement_governance.py`, `tools/test_verify_requirement_governance.py`
- **Jira**: `ATT-6121`
- **Test Description**:
  1. *Dangling TST Detection in Requirements (`TST-PRO-070.1`, tools/test_verify_requirement_governance.py)*:
  • Assert that `find_dangling_references()` detects unresolvable `TST-*` IDs cited in `- **Tests**:` fields of REQs, reporting source file, line number, and target ID.
  2. *Dangling REQ Detection in Tests (`TST-PRO-070.2`, tools/test_verify_requirement_governance.py)*:
  • Assert that `find_dangling_references()` detects unresolvable `REQ-*` IDs cited in `- **Requirement(s)**:` fields of TSTs, reporting source file, line number, and target ID.
  3. *Legacy Allowlist Tolerance & New Reference Rejection (`TST-PRO-070.3`, tools/test_verify_requirement_governance.py)*:
  • Assert that `verify_dangling_references()` emits warnings and returns success (`True`) for known allowlisted legacy dangling links.
  • Assert that any net-new dangling link outside the allowlist fails verification (`False`) with error diagnostics.
  4. *Strict Enforcement Mode (`TST-PRO-070.4`, tools/test_verify_requirement_governance.py)*:
  • Assert that passing `--enforce-links` / `--fail-on-dangling` (or `fail_on_dangling=True`) returns failure (`False`) when any dangling reference is present.
  5. *Clean Repository Resolution (`TST-PRO-070.5`, tools/test_verify_requirement_governance.py)*:
  • Assert that verification passes cleanly when all cited IDs resolve to valid headings.

### TST-PRO-071: **Living Documentation Bidirectional REQ-TST Traceability & Code Coverage Test Suite** [Verified]
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Requirement(s)**: `REQ-PRO-071`
- **Component**: `tools/verify_requirement_governance.py`, `tools/test_verify_requirement_governance.py`
- **Jira**: `ATT-6122`
- **Test Description**:
  1. *Category 1 Calculation (REQs without TST) (`TST-PRO-071.1`, tools/test_verify_requirement_governance.py)*:
  • Assert that requirements with missing, empty, or 'None' `Tests` fields are correctly categorized and quantified.
  2. *Category 2 Calculation (TSTs without Test Code) (`TST-PRO-071.2`, tools/test_verify_requirement_governance.py)*:
  • Assert that TST headings lacking corresponding test code tags in test directories are detected and quantified.
  3. *Category 3 Calculation (Code Tags without Spec) (`TST-PRO-071.3`, tools/test_verify_requirement_governance.py)*:
  • Assert that code tags referencing test IDs not defined in living doc headings are detected and reported.
  4. *Category 4 Calculation (TSTs not Cited Back by REQs) (`TST-PRO-071.4`, tools/test_verify_requirement_governance.py)*:
  • Assert that TST specifications whose cited REQs do not include them in the `Tests` field are identified.
  5. *Report Output Formatting (`TST-PRO-071.5`, tools/test_verify_requirement_governance.py)*:
  • Assert that Markdown and JSON coverage reports format counts, totals, and percentages accurately.
  6. *Threshold Gate Ratcheting (`TST-PRO-071.6`, tools/test_verify_requirement_governance.py)*:
  • Assert that verifier fails with exit code 1 when coverage metrics fall below configured minimum thresholds, and passes when thresholds are met.

### TST-NFR-001: **Deterministic EKF Timing Verification** [Verified]
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit
- **Requirement(s)**: `REQ-NFR-001`
- **Component**: `UnicycleOdometryEkf.kt`, `KinematicAltitudeSlopeEkf.kt`, `SpeedAndLocationDevice_ATTFused.kt`, `docs/architecture.md` §4.9
- **Jira**: `ATT-6139`, `ATT-8158`
- **Test Description**:
  1. *Scope*: Measured JVM EKF Timing & Delta-T Benchmark (`TST-NFR-001.1`, `MeasuredNfrBenchmarkTest.kt`).
  2. *Preconditions*: JIT warmup completed; high-frequency synthetic IMU hardware timestamps with simulated thread arrival jitter.
  3. *Action*: Run `UnicycleOdometryEkf` / `KinematicAltitudeSlopeEkf` motion predict/update loops measuring elapsed step latency (`System.nanoTime`) and hardware-timestamp Delta-T deviation, emitting JSON metrics to `app/build/reports/nfr/nfr_measurements.json`.
  4. *Expected Result*: Delta-T jitter <= 2.0 ms and measured EKF cycle p95 latency <= 2.0 ms.

### TST-NFR-002: **Stationary Pinning Drift Prevention Verification** [Verified]
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit
- **Requirement(s)**: `REQ-NFR-002`
- **Component**: `SpeedAndLocationDevice.java`, `SpeedAndLocationDevice_ATTFused.kt`, `docs/architecture.md` §4.9
- **Jira**: `ATT-6139`, `ATT-8158`
- **Test Description**:
  1. *Scope*: Measured Stationary Replay Simulation (`TST-NFR-002.1`, `MeasuredNfrBenchmarkTest.kt`).
  2. *Preconditions*: Robolectric replay with stationary GNSS jitter within deadband + IMU rest state on `SpeedAndLocationDevice`.
  3. *Action*: Replay 30-minute (1,800-tick) stationary window followed by movement resumption, measuring horizontal drift (m/min), unpin latency (s), and replay runtime, emitting JSON metrics to `app/build/reports/nfr/nfr_measurements.json`.
  4. *Expected Result*: Accumulated horizontal drift < 0.5 m/min; coordinate unpinning <= 1.0 s upon movement resumption.

### TST-NFR-003: **Periodic Tick Latency Budget Verification** [Verified]
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Integration
- **Requirement(s)**: `REQ-NFR-003`
- **Component**: `TrackerService.java`, `docs/architecture.md` §4.9
- **Jira**: `ATT-6139`, `ATT-6181`, `ATT-8158`
- **Test Description**:
  1. *Scope*: Measured `TrackerService` 1-Hz Tick Latency Integration Benchmark (`TST-NFR-003.1`, `MeasuredNfrBenchmarkTest.kt`).
  2. *Preconditions*: Active `TrackerService` Robolectric recording session after JIT warmup.
  3. *Action*: Measure >= 200 consecutive 1-Hz tick cycles via `System.nanoTime`, compute p95/p99/max execution duration, and emit JSON metrics to `app/build/reports/nfr/nfr_measurements.json`.
  4. *Expected Result*: Tick p99 execution duration < 50 ms on 1000 ms cycle (>= 950 ms headroom).

### TST-NFR-004: **CPU Consumption Ceiling Verification** [Planned]
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: On-Device Manual
- **Requirement(s)**: `REQ-NFR-004`
- **Component**: `TrackerService.java`, `docs/architecture.md` §4.9
- **Jira**: `ATT-6139`, `ATT-6181`, `ATT-8158`
- **Test Description**:
  1. *Scope*: Attested On-Device Manual Protocol — `simpleperf` / `dumpsys cpuinfo` (`TST-NFR-004.1`).
  2. *Preconditions*: Physical Pixel reference device; active 30-minute background tracking session with screen off, GNSS 1 Hz, and 2 BLE sensors connected.
  3. *Action*: Execute `adb shell dumpsys cpuinfo` / Android Studio CPU Profiler capture across the 30-minute session and record attestation (device model, Android build, date, operator, measured % single-core CPU, PASS/FAIL). Never falsely satisfied by JVM unit tests.
  4. *Expected Result*: Attested average single-core CPU consumption < 5.0%.

### TST-NFR-005: **Dedicated Heap Footprint Ceiling Verification** [Verified]
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Integration
- **Requirement(s)**: `REQ-NFR-005`
- **Component**: `TrackerService.java`, `LiveWorkoutSession.java`, `docs/architecture.md` §4.9
- **Jira**: `ATT-6139`, `ATT-6181`, `ATT-8158`
- **Test Description**:
  1. *Scope*: Measured Endurance Heap Footprint Benchmark (`TST-NFR-005.1`, `MeasuredNfrBenchmarkTest.kt`).
  2. *Preconditions*: Warmed JVM/Robolectric `TrackerService` / `LiveWorkoutSession` session with post-GC baseline heap recorded.
  3. *Action*: Replay 3,600+ 1-Hz multi-sensor telemetry ticks, invoke GC, measure retained heap delta (`MB`) and total heap (`MB`), and emit JSON metrics to `app/build/reports/nfr/nfr_measurements.json`.
  4. *Expected Result*: Dedicated retained heap delta < 40 MB; total heap RAM <= 150 MB.

### TST-NFR-006: **Tracking Battery Drain Ceiling Verification** [Planned]
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: On-Device Manual
- **Requirement(s)**: `REQ-NFR-006`
- **Component**: `TrackerService.java`, `docs/architecture.md` §4.9
- **Jira**: `ATT-6139`, `ATT-6181`, `ATT-8158`
- **Test Description**:
  1. *Scope*: Attested On-Device Manual Protocol — Battery Historian / `dumpsys batterystats` (`TST-NFR-006.1`).
  2. *Preconditions*: Physical Pixel reference device unplugged at >= 90% SoC; active GNSS + paired BLE/ANT+ sensors.
  3. *Action*: Reset `adb shell dumpsys batterystats --reset`, run 60-minute screen-off tracking and 60-minute screen-on cockpit map tracking (50% brightness), dump `batterystats`, and record attested discharge rates (%/h), device, build, date, operator, and verdict.
  4. *Expected Result*: Attested battery drain <= 5.0%/h screen off; <= 12.0%/h screen on with cockpit map.

### TST-NFR-007: **Sensor Dispatch Latency Budget Verification** [Verified]
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Integration
- **Requirement(s)**: `REQ-NFR-007`
- **Component**: `BANALService.java`, `ProxySensor.java`, `docs/architecture.md` §4.9
- **Jira**: `ATT-6139`, `ATT-6181`, `ATT-8158`
- **Test Description**:
  1. *Scope*: Measured Sensor Dispatch Latency Benchmark (`TST-NFR-007.1`, `MeasuredNfrBenchmarkTest.kt`).
  2. *Preconditions*: Warmed Robolectric `BANALService` / `ProxySensor` pipeline with active subscribers.
  3. *Action*: Dispatch >= 1,000 synthetic sensor events, measure timestamp delta (`System.nanoTime`) to subscriber delivery, and emit p99/max latency (ms) to `app/build/reports/nfr/nfr_measurements.json`.
  4. *Expected Result*: Measured p99 dispatch latency < 25 ms with zero dropped packets.

### TST-NFR-008: **Sensor Connection Failover Timeout Verification** [Verified]
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit
- **Requirement(s)**: `REQ-NFR-008`
- **Component**: `BTLERadarDevice.java`, `ANTBikeRadarDevice.kt`, `MyBTLEDevice.java`, `BANALService.java`, `ProxySensor.java`, `docs/architecture.md` §4.9
- **Jira**: `ATT-6139`, `ATT-6181`, `ATT-8158`
- **Test Description**:
  1. *Scope*: Measured Disconnect/Watchdog Failover Benchmark (`TST-NFR-008.1`, `MeasuredNfrBenchmarkTest.kt`).
  2. *Preconditions*: Active radar/BLE/proxy sensor state with `WATCHDOG_TIMEOUT_MS = 3500L` and secondary source or reconnect handler.
  3. *Action*: Simulate abrupt packet starvation, advance/observe watchdog & failover dispatch timing, measure elapsed failover/reconnect initiation latency (`ms`), and emit JSON metrics to `app/build/reports/nfr/nfr_measurements.json`.
  4. *Expected Result*: Measured failover/reconnect initiation <= 3.5 s (`3500 ms`) observing §4.8 priority hierarchy.

### TST-NFR-009: **Tracking WakeLock Bounding Verification** [Verified]
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Integration
- **Requirement(s)**: `REQ-NFR-009`
- **Component**: `TrackerService.java`, `docs/architecture.md` §4.9
- **Jira**: `ATT-6139`, `ATT-6735`, `ATT-8158`
- **Test Description**:
  1. *Scope*: Measured `ShadowPowerManager` WakeLock Lifecycle Test (`TST-NFR-009.1`, `MeasuredNfrBenchmarkTest.kt`).
  2. *Preconditions*: Robolectric `TrackerService` lifecycle driving `onCreate` -> `onStartCommand(START_NORMAL)` -> `PAUSED` -> `TRACKING` -> `ACTION_STOP` -> `onDestroy`.
  3. *Action*: Assert `ShadowPowerManager` WakeLock `isHeld()` for `aTrainingTracker:TrackingLock` across every transition, measure release latency (`ms`), and emit JSON metrics to `app/build/reports/nfr/nfr_measurements.json`.
  4. *Expected Result*: WakeLock held throughout active tracking and pause monitoring; released immediately (`0` leaked WakeLocks) upon `ACTION_STOP` / `onDestroy` finalization.

### TST-NFR-010: **Database Sample Write Latency Budget Verification** [Verified]
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Integration
- **Requirement(s)**: `REQ-NFR-010`
- **Component**: `TrackerService.java`, `WorkoutSamplesDatabaseManager.java`, `docs/architecture.md` §4.9
- **Jira**: `ATT-6139`, `ATT-6735`, `ATT-8158`
- **Test Description**:
  1. *Scope*: Measured SQLite WAL Write Latency Benchmark (`TST-NFR-010.1`, `MeasuredNfrBenchmarkTest.kt`).
  2. *Preconditions*: Real Robolectric SQLite database in WAL mode after warmup inserts on a background executor (`mDbExecutor` semantics).
  3. *Action*: Measure per-row `SQLiteDatabase.insertOrThrow` latency over >= 1,000 consecutive 1-Hz workout sample rows, verify non-main-thread execution, and emit p95/p99/max ms to `app/build/reports/nfr/nfr_measurements.json`.
  4. *Expected Result*: Measured p99 write latency < 30 ms per sample with zero operations on the Android main thread.

### TST-NFR-011: **Telemetry Storage Growth Rate Ceiling Verification** [Verified]
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Integration
- **Requirement(s)**: `REQ-NFR-011`
- **Component**: `WorkoutSamplesDatabaseManager.java`, `docs/architecture.md` §4.9
- **Jira**: `ATT-6139`, `ATT-6181`, `ATT-8158`
- **Test Description**:
  1. *Scope*: Measured SQLite Storage Progression Integration Test (`TST-NFR-011.1`, `MeasuredNfrBenchmarkTest.kt`).
  2. *Preconditions*: Real Robolectric SQLite workout samples table with WAL checkpoint baseline size measured.
  3. *Action*: Insert 3,600 consecutive 1-Hz multi-sensor rows (1-hour workout), run `PRAGMA wal_checkpoint(TRUNCATE)`, measure `.db` file byte delta (`KB/h`), and emit JSON metrics to `app/build/reports/nfr/nfr_measurements.json`.
  4. *Expected Result*: Measured storage growth <= 360 KB/h (~100 bytes/sample).

### TST-NFR-012: **Cockpit Frame Render Budget Verification** [Planned]
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: On-Device Manual
- **Requirement(s)**: `REQ-NFR-012`
- **Component**: `SensorGridScreen.kt`, `docs/architecture.md` §4.9
- **Jira**: `ATT-6139`, `ATT-6181`, `ATT-8158`
- **Test Description**:
  1. *Scope*: Attested On-Device Manual Protocol — AndroidX Macrobenchmark `FrameTimingMetric` / `dumpsys gfxinfo` (`TST-NFR-012.1`).
  2. *Preconditions*: Physical Pixel device rendering `SensorGridScreen` live 1-Hz telemetry cards for 60 seconds.
  3. *Action*: Collect `adb shell dumpsys gfxinfo com.atrainingtracker.trainingtracker framestats` (or Macrobenchmark `FrameTimingMetric`) and record attested p95 frame time (ms), device, build, date, operator, and verdict.
  4. *Expected Result*: Attested 95th-percentile frame render duration < 16.6 ms (sustaining >= 60 FPS).

### TST-NFR-013: **Map Polyline Update Latency Budget Verification** [Verified]
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit
- **Requirement(s)**: `REQ-NFR-013`
- **Component**: `MapLayers.kt`, `ATrainingTrackerMap.kt`, `docs/architecture.md` §4.9
- **Jira**: `ATT-6139`, `ATT-6181`, `ATT-8158`
- **Test Description**:
  1. *Scope*: Measured Map Polyline Decimation Benchmark (`TST-NFR-013.1`, `MeasuredNfrBenchmarkTest.kt`).
  2. *Preconditions*: Warmed JVM benchmark with a 10,000-point synthetic GPS `LatLng` track.
  3. *Action*: Execute `IncrementalTrackDecimator` / `decimateLiveTrack` (`MapLayers.kt`) across incremental coordinate appends, measure elapsed `System.nanoTime` (ms), and emit p95/max ms to `app/build/reports/nfr/nfr_measurements.json`.
  4. *Expected Result*: Measured polyline geometry update latency < 30 ms.

### TST-NFR-014: **Cockpit Telemetry Emission Rate Throttling Verification** [Verified]
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit
- **Requirement(s)**: `REQ-NFR-014`
- **Component**: `BANALServiceRepository.kt`, `TrackingTabsViewModel.kt`, `docs/architecture.md` §4.9
- **Jira**: `ATT-6139`, `ATT-6181`, `ATT-8158`
- **Test Description**:
  1. *Scope*: Measured Coroutine `TestScope` 1-Hz Emission Throttling Test (`TST-NFR-014.1`, `MeasuredNfrBenchmarkTest.kt`).
  2. *Preconditions*: High-frequency (> 4 Hz) mock sensor updates feeding `BANALServiceRepository` / `TrackingTabsViewModel`.
  3. *Action*: Advance virtual time over 5.0 seconds (`5,000 ms`), count distinct `allFilteredSensorData` / `StateFlow` periodic emissions, verify rate <= 1.0 Hz, and emit measured emission count & interval to `app/build/reports/nfr/nfr_measurements.json`.
  4. *Expected Result*: <= 5 distinct periodic UI emissions over 5.0 seconds (throttled to <= 1.0 Hz).

### TST-NFR-015: **Cold Start Startup Latency Budget Verification** [Planned]
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: On-Device Manual
- **Requirement(s)**: `REQ-NFR-015`
- **Component**: `MainActivityWithNavigation.kt`, `docs/architecture.md` §4.9
- **Jira**: `ATT-6139`, `ATT-8158`
- **Test Description**:
  1. *Scope*: Attested On-Device Manual Protocol — Cold Start `am start -W` / Macrobenchmark `StartupMode.COLD` (`TST-NFR-015.1`).
  2. *Preconditions*: Physical Pixel device with app force-stopped before each of >= 5 cold launches.
  3. *Action*: Measure TTID (`TotalTime`) and TTFD (`reportFullyDrawn`) via `adb shell am start -W` / Macrobenchmark and record attested median/p95 ms, device, build, date, operator, and verdict.
  4. *Expected Result*: Attested TTID <= 1200 ms; TTFD <= 1500 ms.

### TST-NFR-016: **GNSS Horizontal Trajectory Accuracy Verification** [Planned]
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: On-Device Manual
- **Requirement(s)**: `REQ-NFR-016`
- **Component**: `MySensorManager.java`, `SpeedAndLocationDevice_ATTFused.kt`, `docs/architecture.md` §4.8
- **Jira**: `ATT-6139`, `ATT-8158`
- **Test Description**:
  1. *Scope*: Attested On-Device Manual Protocol — Open-Sky Field Track vs Surveyed Reference (`TST-NFR-016.1`).
  2. *Preconditions*: Open-sky field course with surveyed RTK/ground-truth polyline on physical Pixel device.
  3. *Action*: Record workout track, export GPX, compute orthogonal cross-track error against reference polyline, and record attested p95 error (m), RMSE (m), device, build, date, operator, and verdict.
  4. *Expected Result*: Attested 95th-percentile horizontal error <= 5.0 m; cross-track RMSE <= 3.0 m.

### TST-NFR-017: **Production Crash-Free Session Stability Verification** [Planned]
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: On-Device Manual
- **Requirement(s)**: `REQ-NFR-017`
- **Component**: `TrainingApplication.java`, `docs/architecture.md`
- **Jira**: `ATT-6139`, `ATT-8158`
- **Test Description**:
  1. *Scope*: Attested On-Device Manual Protocol — Play Console Android Vitals Crash Rate (`TST-NFR-017.1`).
  2. *Preconditions*: Production/staging release telemetry accumulated in Google Play Console Android Vitals over rolling 30-day window.
  3. *Action*: Export rolling 30-day crash-free session rate from Play Console / Crashlytics and record attested percentage, release version, date, operator, and verdict.
  4. *Expected Result*: Attested production crash-free session rate >= 99.5% (staging >= 99.0%).

### TST-NFR-018: **Application Not Responding (ANR) Rate Ceiling Verification** [Planned]
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: On-Device Manual
- **Requirement(s)**: `REQ-NFR-018`
- **Component**: `TrackerService.java`, `WorkoutSamplesDatabaseManager.java`, `docs/architecture.md` §4.9
- **Jira**: `ATT-6139`, `ATT-8158`
- **Test Description**:
  1. *Scope*: Attested On-Device Manual Protocol — Play Console Android Vitals ANR Rate & StrictMode (`TST-NFR-018.1`).
  2. *Preconditions*: Production release monitored in Google Play Console Android Vitals plus on-device StrictMode session audit.
  3. *Action*: Export rolling 30-day user-perceived ANR rate from Play Console, inspect StrictMode/Perfetto logs, and record attested ANR rate (%), release version, date, operator, and verdict.
  4. *Expected Result*: Attested production ANR rate <= 0.05% (threshold 0.47%); zero main-thread blocking I/O violations.

### TST-PRO-072: **Release Manager System Verification Evidence Integration & Release Gating Test Suite** [Verified]
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Requirement(s)**: `REQ-PRO-072`
- **Component**: `.agents/skills/release-manager/SKILL.md`, `docs/releases/README.md`, `docs/releases/TEMPLATE.md`, `app/src/test/java/com/atrainingtracker/trainingtracker/build/ReleaseManagerSystemEvidenceContractTest.kt`, `docs/architecture.md`
- **Jira**: `ATT-6169`, `ATT-8157`
- **Test Description**:
  1. *Skill System Evidence Gate Specification Contract Test (`TST-PRO-072.1`, ReleaseManagerSystemEvidenceContractTest.kt)*:
  • Assert that `.agents/skills/release-manager/SKILL.md` exists, mandates unit/`INT-SWE-01..08` + `SYS.5` (`sys_verification_record.md`) + `verification_summary.md` release gates, and does NOT mandate `system_integration_results.json` or invalid `--output-json` as a blocking SYS.4 gate.
  2. *Release Tag Blocking Logic Contract Test (`TST-PRO-072.2`, ReleaseManagerSystemEvidenceContractTest.kt)*:
  • Assert that the release checklist and step-by-step procedures explicitly block tag creation and Play Store distribution if `sys_verification_record.md` or `verification_summary.md` is missing, failing, or has unapproved deviations.
  3. *Release Template & Directory Structure Contract Test (`TST-PRO-072.3`, ReleaseManagerSystemEvidenceContractTest.kt)*:
  • Assert that `docs/releases/` `README.md` and `TEMPLATE.md` specify `docs/releases/<tag>/` layout, `SYS.5` sign-off criteria, and non-gating simulator self-test status.
  4. *Contract Test Suite Execution & Governance Parity (`TST-PRO-072.4`, ReleaseManagerSystemEvidenceContractTest.kt & tools/verify_requirement_governance.py)*:
  • Assert `ReleaseManagerSystemEvidenceContractTest.kt` passes cleanly under Gradle and `python3 tools/verify_requirement_governance.py` confirms 100% living documentation parity.

### TST-PRO-073: **Evidence-Based Requirement Status Model and Master Index Parity Test Suite** [Verified]
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Requirement(s)**: `REQ-PRO-073`
- **Scope**: Contract Tests & Tooling Unit Tests
- **Component**: `tools/verify_requirement_governance.py`, `tools/test_verify_requirement_governance.py`, `app/src/test/java/com/atrainingtracker/trainingtracker/build/AspiceSweAuditContractTest.kt`
- **Jira**: `ATT-6126`
- **Test Description**:
  1. *Headings Standardization (`TST-PRO-073.1`, AspiceSweAuditContractTest.kt)*:
     • Assert that all living requirement and test headings end with standard status tokens: `[Verified]`, `[Implemented]`, `[Planned]`, or `[Open]`.
  2. *4-Column Master Table Parity (`TST-PRO-073.2`, AspiceSweAuditContractTest.kt)*:
     • Assert that master tables in `docs/requirements.md` and `docs/tests.md` have 4 numeric columns (Total, Verified, Implemented, Planned/Open) matching shard counts with 100% mathematical parity.
  3. *Verifier 4-Column Sync & Parity (`TST-PRO-073.3`, tools/test_verify_requirement_governance.py)*:
     • Assert that `verify_master_table_parity()` and `recalculate_master_index_tables()` correctly calculate and output 4-column master table records.
  4. *Evidence Status Verification Rule (`TST-PRO-073.4`, tools/test_verify_requirement_governance.py)*:
     • Assert that requirements claiming `[Verified]` without an existing TST ID fail verification, while requirements citing existing TST IDs pass cleanly.

### TST-PRO-074: **Release Verification Summary Generator Test Suite** [Verified]
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Requirement(s)**: `REQ-PRO-074`
- **Component**: `tools/generate_release_verification_summary.py`, `tools/test_generate_release_verification_summary.py`, `.agents/skills/release-manager/SKILL.md`, `docs/releases/README.md`
- **Jira**: `ATT-6138`, `ATT-8157`
- **Test Description**:
  1. *CLI Argument Parsing & Default Output Path Test (`TST-PRO-074.1`, test_generate_release_verification_summary.py)*:
  • Verify CLI accepts `--tag`, `--commit`, `--out`, and coverage threshold flags, correctly defaulting output to `docs/releases/<tag>/verification_summary.md`.
  2. *Unit Test Report Parsing & Archival Link Test (`TST-PRO-074.2`, test_generate_release_verification_summary.py)*:
  • Verify parsing of JUnit XML reports and versioned archive reference `build-evidence/junit-test-results-<tag>.zip`.
  3. *Coverage JSON Integration & Threshold Verification Test (`TST-PRO-074.3`, test_generate_release_verification_summary.py)*:
  • Verify extraction of bidirectional REQ-TST and TST-Code metrics from `--coverage-json` output and threshold breach detection.
  4. *SYS.5 Gate & Non-Blocking Legacy Simulator Summary Test (`TST-PRO-074.4`, test_generate_release_verification_summary.py)*:
  • Verify parsing of `sys_verification_record.md` flags missing/unsigned SYS.5 protocols under deviations requiring human approval, while missing `system_integration_results.json` raises zero blocking deviations and legacy files render honestly as non-blocking simulator/informational telemetry.
  5. *End-to-End Markdown Summary Generation & Governance Parity Test (`TST-PRO-074.5`, test_generate_release_verification_summary.py & tools/verify_requirement_governance.py)*:
  • Verify end-to-end generation of `verification_summary.md` and confirm 100% living documentation parity.

### TST-PRO-075: **Requirement Tests Field Format & Traceability Governance Verifier Test Suite** [Verified]
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Requirement(s)**: `REQ-PRO-075`
- **Component**: `tools/verify_requirement_governance.py`, `tools/test_verify_requirement_governance.py`
- **Jira**: `ATT-6125`
- **Test Description**:
  1. *Conforming Syntax Acceptance Test (`TST-PRO-075.1`, test_verify_requirement_governance.py)*:
  • Assert that single and multiple backticked test IDs (e.g. `` `TST-CON-001` ``, `` `TST-CON-001`, `TST-CON-002` ``) and valid placeholders (`None`, `Planned`, `Review`, `Manual inspection`) pass validation with 0 errors.
  2. *Corrupted Sentence Fragment Rejection Test (`TST-PRO-075.2`, test_verify_requirement_governance.py)*:
  • Assert that unclosed backticks, dangling boolean expressions (e.g. `code == 201 check...`), and trailing pipe metadata (e.g. `| **Tickets**: ATT-...`) are detected as violations.
  3. *Misplaced Source File Rejection Test (`TST-PRO-075.3`, test_verify_requirement_governance.py)*:
  • Assert that Kotlin/Java/XML filenames in `Tests:` fields are detected as violations.
  4. *Clean-Room Shard Audit & Governance Parity (`TST-PRO-075.4`, verify_requirement_governance.py)*:
  • Assert `tools/verify_requirement_governance.py` passes with exit code 0 across all 9 shards and master index tables match with 100% parity.

### TST-PRO-076: **System-to-Software Requirement Traceability & Governance Verification Suite** [Verified]
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Requirement(s)**: `REQ-PRO-076`
- **Scope**: Tooling Unit Tests, Contract Tests & Governance Integration
- **Component**: `tools/verify_requirement_governance.py`, `tools/test_verify_requirement_governance.py`, `docs/system/sysreq_live_tracking.md`, `docs/system/sysreq_data_exchange.md`, `docs/system/system_software_traceability.md`, `docs/project_governance.md`
- **Jira**: `ATT-6163`
- **Test Description**:
  1. *SYSREQ Citation Integrity Unit Test (`TST-PRO-076.1`, test_verify_requirement_governance.py)*:
  • Verify that `verify_system_software_traceability()` passes when all cited `SYSREQ-*` IDs exist in `docs/system/sysreq_*.md`, and fails with descriptive errors when an ungrounded or dangling `SYSREQ-XXX` is cited.
  2. *SYSREQ Coverage by Software Requirements Test (`TST-PRO-076.2`, test_verify_requirement_governance.py)*:
  • Verify that 100% of declared system requirements (`SYSREQ-TRK-001`..`006`, `SYSREQ-DEX-001`..`006`) are refined by software requirements, and flag missing coverage if any system requirement is unrefined.
  3. *Domain & Prefix Tailoring Governance Rule Test (`TST-PRO-076.3`, test_verify_requirement_governance.py)*:
  • Verify that software requirements belonging to tailored prefix families (`UI`, `UNT`, `PRO`, etc.) are recognized as software-derived (`[SWE-Derived]`) with valid architectural rationale.
  4. *CLI Integration & Living Documentation Parity Test (`TST-PRO-076.4`, tools/verify_requirement_governance.py & AspiceSweAuditContractTest.kt)*:
  • Verify end-to-end execution of `python3 tools/verify_requirement_governance.py --verify-sys-trace` and assert 100% master index parity across all living documentation shards.

### TST-PRO-077: **Requirement Attributes Governance Verification Suite** [Verified]
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Requirement(s)**: `REQ-PRO-077`
- **Scope**: Tooling Unit Tests, Contract Tests & Governance Integration
- **Component**: `tools/verify_requirement_governance.py`, `tools/test_verify_requirement_governance.py`, `.agents/skills/stage2-req-test-spec/SKILL.md`, `templates/test_spec_template.md`, `docs/requirements.md`
- **Jira**: `ATT-6136`
- **Test Description**:
  1. *Requirement Attribute Presence & Domain Validation Unit Test (`TST-PRO-077.1`, test_verify_requirement_governance.py)*:
  • Verify that `verify_requirement_attributes()` passes when all three attributes (Priority, Type, Verification Method) are present and valid, and fails when any attribute is missing, empty, or has an invalid value.
  2. *Git Diff Enforcement & Legacy Tolerance Unit Test (`TST-PRO-077.2`, test_verify_requirement_governance.py)*:
  • Verify that diff-based checking enforces all three attributes on newly added or modified requirements while emitting warnings for untouched legacy requirements.
  3. *Strict CLI Enforcement Flag Test (`TST-PRO-077.3`, test_verify_requirement_governance.py)*:
  • Verify that `--enforce-attributes` / `--fail-on-missing-attributes` fails with exit code 1 if any requirement in the repository lacks valid attributes.
  4. *Template & Living Documentation Parity Audit (`TST-PRO-077.4`, tools/verify_requirement_governance.py)*:
  • Verify that Stage 2 skill template, deliverable template, and `docs/requirements.md` contain standardized attribute fields and living documentation master tables maintain 100% parity.

### TST-PRO-079: **Aftermath Reviews Test Evidence Backfill & Traceability Verification Suite** [Verified]
- **Requirement(s)**: `REQ-PRO-079`
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Component**: `docs/requirements/req_aftermath_reviews.md`, `docs/tests/tst_aftermath_reviews.md`, `ZoneDistributionCardTest.kt`, `tools/verify_requirement_governance.py`
- **Jira**: `ATT-6128`
- **Test Description**:
  1. *Coverage & Existence Matrix Verification*: Run `python3 tools/verify_requirement_governance.py --coverage` asserting 0 gaps in Category 1 (100% REQs with TST), 0 gaps in Category 2 for TST-REV (100% TSTs with code), 0 gaps in Category 3 for TST-REV, and 0 gaps in Category 4 for TST-REV (100% bidirectional citation).
  2. *Dangling Link & Evidence Audit*: Run `python3 tools/verify_requirement_governance.py --check-links` and `--check-evidence` asserting zero dangling references and zero ungrounded Verified statuses in `aftermath_reviews`.
  3. *Attribute Compliance Check*: Run `python3 tools/verify_requirement_governance.py --check-attributes` asserting all 12 `REQ-REV-*` requirements define valid Priority, Type, and Verification Method attributes.
  4. *Targeted Unit Test Verification*: Execute `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.aftermath.zones.ZoneDistributionCardTest"` asserting 100% pass rate.
















### TST-PRO-078: **Tracking Cockpit Verification & Bidirectional Traceability Verification Suite** [Verified]
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Requirement(s)**: `REQ-PRO-078`
- **Scope**: Tooling Unit Tests, Contract Tests & Governance Integration
- **Component**: `tools/verify_requirement_governance.py`, `tools/test_verify_requirement_governance.py`, `docs/requirements/req_tracking_cockpit.md`, `docs/tests/tst_tracking_cockpit.md`
- **Jira**: `ATT-6127`
- **Test Description**:
  1. *Tracking Cockpit Domain Coverage Test (`TST-PRO-078.1`, test_verify_requirement_governance.py)*:
  • Verify that `calculate_traceability_coverage()` across `req_tracking_cockpit.md` and `tst_tracking_cockpit.md` yields $\ge 95\%$ Category 1 coverage and $\ge 95\%$ Category 4 bidirectional coverage with zero ungrounded [Verified] claims.
  2. *Zero Dangling Reference Verification Test (`TST-PRO-078.2`, verify_requirement_governance.py)*:
  • Verify that `find_dangling_references()` detects zero dangling references in `req_tracking_cockpit.md` and `tst_tracking_cockpit.md` after removing `TST-FUSION-001`..`003` and `REQ-TRK-035` from legacy allowlists.
  3. *Living Documentation Master Table Parity Test (`TST-PRO-078.3`, AspiceSweAuditContractTest.kt)*:
  • Verify 100% mathematical parity across all 4 columns of `docs/requirements.md` and `docs/tests.md` via `verify_master_table_parity()` and `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.build.AspiceSweAuditContractTest"`.


### TST-PRO-080: **Aftermath Periods Test Evidence Backfill & Traceability Verification Suite** [Verified]
- **Requirement(s)**: `REQ-PRO-080`
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Component**: `docs/requirements/req_aftermath_periods.md`, `docs/tests/tst_aftermath_periods.md`, `PeriodBarGraphTest.kt`, `WorkoutShareBottomSheetContractTest.kt`, `SportingJourneyRepositoryTest.kt`, `SportingJourneyViewModelTest.kt`, `SportingJourneyCalculatorTest.kt`, `tools/verify_requirement_governance.py`
- **Jira**: `ATT-6129`
- **Test Description**:
  1. *Coverage & Existence Matrix Verification*: Run `python3 tools/verify_requirement_governance.py --coverage` asserting 0 gaps in Category 1 for aftermath_periods (100% REQs with TST), 0 gaps in Category 4 for aftermath_periods, and 0 Category 3 tags for SPT.
  2. *Dangling Link & Evidence Audit*: Run `python3 tools/verify_requirement_governance.py --check-links` and `--check-evidence` asserting zero dangling references and zero ungrounded Verified statuses in `aftermath_periods`.
  3. *Attribute Compliance Check*: Run `python3 tools/verify_requirement_governance.py --check-attributes` asserting all 41 requirements in `req_aftermath_periods.md` define valid Priority, Type, and Verification Method attributes.
  4. *Targeted Unit Test Verification*: Execute `./gradlew testDebugUnitTest --tests "*PeriodBarGraph*" --tests "*SportingJourney*"` asserting 100% pass rate.

### TST-PRO-081: **Maps and Navigation Test Evidence Backfill & Bidirectional Traceability Verification Suite** [Verified]
- **Requirement(s)**: `REQ-PRO-081`
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Scope**: Governance Verification, Coverage Analysis & Unit Tests
- **Component**: `docs/requirements/req_maps_navigation.md`, `docs/tests/tst_maps_navigation.md`, `WaypointBadgeMarkerTest.kt`, `tools/verify_requirement_governance.py`
- **Jira**: `ATT-6131`
- **Test Description**:
  1. *Coverage & Existence Matrix Verification*: Run `python3 tools/verify_requirement_governance.py --coverage` asserting 0 gaps in Category 1 for maps_navigation (100% REQs with TST), resolution of `TST-MAP-038` in Category 3, and 100% bidirectional citation for all MAP and NAV test specifications in Category 4.
  2. *Dangling Link & Evidence Audit*: Run `python3 tools/verify_requirement_governance.py --check-links` and `--check-evidence` asserting zero dangling references in `maps_navigation` (pruning `REQ-MAP-037` from legacy allowlist) and zero ungrounded Verified statuses.
  3. *Attribute Compliance Check*: Run `python3 tools/verify_requirement_governance.py --check-attributes` asserting all 75 requirements in `req_maps_navigation.md` define valid Priority, Type, and Verification Method attributes.
  4. *Targeted Unit Test Verification*: Execute `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.map.WaypointBadgeMarkerTest"` asserting 100% pass rate.

### TST-PRO-082: **Settings and Profiles Test Evidence Backfill & Traceability Verification Suite** [Verified]
- **Requirement(s)**: `REQ-PRO-082`
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Component**: `docs/requirements/req_settings_profiles.md`, `docs/tests/tst_settings_profiles.md`, `WorkoutClusterCounterTest.kt`, `WorkoutClusterStatsTest.kt`, `tools/verify_requirement_governance.py`
- **Jira**: `ATT-6133`
- **Test Description**:
  1. *Coverage & Existence Matrix Verification*: Run `python3 tools/verify_requirement_governance.py --coverage` asserting 0 gaps in Category 1 for settings_profiles (100% REQs with TST), 0 gaps in Category 4 for settings_profiles, and 0 Category 3 tags for SET.
  2. *Dangling Link & Evidence Audit*: Run `python3 tools/verify_requirement_governance.py --check-links` and `--check-evidence` asserting zero dangling references and zero ungrounded Verified statuses in `settings_profiles`.
  3. *Attribute Compliance Check*: Run `python3 tools/verify_requirement_governance.py --check-attributes` asserting all 102 requirements in `req_settings_profiles.md` define valid Priority, Type, and Verification Method attributes.
  4. *Targeted Unit Test Verification*: Execute `./gradlew testDebugUnitTest --tests "*Cluster*" --tests "*Setting*"` asserting 100% pass rate.

### TST-PRO-083: **Data Migration and Export Test Evidence Backfill & Bidirectional Traceability Verification Suite** [Verified]
- **Requirement(s)**: `REQ-PRO-083`
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Scope**: Governance Verification, Coverage Analysis & Unit Tests
- **Component**: `docs/requirements/req_data_migration_export.md`, `docs/tests/tst_data_migration_export.md`, `WorkoutDeletionHelperTest.kt`, `tools/verify_requirement_governance.py`
- **Jira**: `ATT-6134`
- **Test Description**:
  1. *Coverage & Existence Matrix Verification*: Run `python3 tools/verify_requirement_governance.py --coverage` asserting 0 gaps in Category 1 for data_migration_export (100% REQs with TST), resolution of `REQ-DAT-010` ungrounded claim, and 100% bidirectional citation for all DAT, EXP, EXT, and MIG test specifications in Category 4.
  2. *Dangling Link & Evidence Audit*: Run `python3 tools/verify_requirement_governance.py --check-links` and `--check-evidence` asserting zero dangling references in `data_migration_export` and zero ungrounded Verified statuses (pruning `REQ-DAT-010` from `LEGACY_UNGROUNDED_VERIFIED_REQS`).
  3. *Attribute Compliance Check*: Run `python3 tools/verify_requirement_governance.py --check-attributes` asserting all 130 requirements in `req_data_migration_export.md` define valid Priority, Type, and Verification Method attributes.
  4. *Targeted Unit Test Verification*: Execute `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.database.WorkoutDeletionHelperTest"` asserting 100% pass rate.

### TST-PRO-084: **Store Presence Test Evidence Backfill & Traceability Verification Suite** [Verified]
- **Requirement(s)**: `REQ-PRO-084`
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Component**: `docs/requirements/req_store_presence.md`, `docs/tests/tst_store_presence.md`, `PlayStoreListingContractTest.kt`, `tools/verify_requirement_governance.py`
- **Jira**: `ATT-6135`
- **Test Description**:
  1. *Coverage & Existence Matrix Verification*: Run `python3 tools/verify_requirement_governance.py --coverage` asserting 0 gaps in Category 1 for store_presence (100% REQs with TST), 0 gaps in Category 2 for store_presence (10/10 TSTs tagged in code), and 0 gaps in Category 4 for store_presence.
  2. *Dangling Link & Evidence Audit*: Run `python3 tools/verify_requirement_governance.py --check-links` and `--check-evidence` asserting zero dangling references and zero ungrounded Verified statuses in `store_presence`.
  3. *Attribute Compliance Check*: Run `python3 tools/verify_requirement_governance.py --check-attributes` asserting all 10 requirements in `req_store_presence.md` define valid Priority, Type, and Verification Method attributes.
  4. *Targeted Unit Test Verification*: Execute `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.build.*"` asserting 100% pass rate.
### TST-PRO-085: **UI Design System Test Evidence Backfill & Bidirectional Traceability Verification Suite** [Verified]
- **Requirement(s)**: `REQ-PRO-085`
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Scope**: Governance Verification, Coverage Analysis & Unit Tests
- **Component**: `docs/requirements/req_ui_design_system.md`, `docs/tests/tst_ui_design_system.md`, `tools/verify_requirement_governance.py`
- **Jira**: `ATT-6132`
- **Test Description**:
  1. *Coverage & Existence Matrix Verification*: Run `python3 tools/verify_requirement_governance.py --coverage` asserting 0 gaps in Category 1 for ui_design_system (100% REQs with TST), 0 ungrounded claims, and 100% bidirectional citation for all UI and UNT test specifications in Category 4.
  2. *Dangling Link & Evidence Audit*: Run `python3 tools/verify_requirement_governance.py --check-links`, `--check-evidence`, and `--enforce-unique` asserting zero dangling references and zero ungrounded Verified statuses in `ui_design_system` (pruning all UI items from legacy allowlists).
  3. *Attribute Compliance Check*: Run `python3 tools/verify_requirement_governance.py --check-attributes` asserting all 452 requirements in `req_ui_design_system.md` define valid Priority, Type, and Verification Method attributes.
  4. *Targeted Unit Test Verification*: Execute `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest" --tests "com.atrainingtracker.banalservice.sensor.formater.TimeFormatterTest"` asserting 100% pass rate.

### TST-PRO-086: **Process Stability Test Evidence Backfill & Traceability Verification Suite** [Verified]
- **Requirement(s)**: `REQ-PRO-086`
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Component**: `docs/requirements/req_process_stability.md`, `docs/tests/tst_process_stability.md`, `tools/verify_requirement_governance.py`, `tools/test_verify_requirement_governance.py`
- **Jira**: `ATT-6130`
- **Test Description**:
  1. *Coverage & Existence Matrix Verification*: Run `python3 tools/verify_requirement_governance.py --coverage` asserting 0 gaps in Category 1 for process_stability (100% REQs with TST), zero dangling references originating from this domain, and >= 95% Category 4 bidirectional parity.
  2. *Dangling Link & Evidence Audit*: Run `python3 tools/verify_requirement_governance.py --check-links` and `--check-evidence` asserting zero dangling references and zero ungrounded Verified statuses in `process_stability`.
  3. *Attribute Compliance Check*: Run `python3 tools/verify_requirement_governance.py --check-attributes` asserting all requirements in `req_process_stability.md` and test specifications in `tst_process_stability.md` define valid Priority, Type, and Verification Method attributes.
  4. *Targeted Unit Test Verification*: Execute `python3 -m unittest tools/test_verify_requirement_governance.py` asserting 100% pass rate across all governance verifier unit tests.

### TST-PRO-087: **Machine-Verified Bidirectional Traceability & Ratchet Baseline Verification Suite** [Verified]
- **Verifies**: `REQ-PRO-087`
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Component**: `tools/verify_requirement_governance.py`, `tools/traceability_baseline.json`, `tools/test_verify_requirement_governance.py`
- **Jira**: `ATT-7561`
- **Test Description**:
  1. *Bidirectional Orphan Verification*: Run `python3 tools/verify_requirement_governance.py` asserting all declared REQs have verifying TSTs and all TSTs declare valid REQs, or are recorded in `tools/traceability_baseline.json`.
  2. *Baseline Ratchet Monotonicity Test*: Execute unit tests in `tools/test_verify_requirement_governance.py` simulating addition of new unlinked REQs and baseline growth, verifying immediate CI failure.
  3. *Syntax Flexibility & Shorthand Normalization*: Verify parser supports `Verifies:` and legacy `Requirement(s):` syntax, and confirm normalized links for `TST-MIG-001`, `TST-UI-310`, and the 6 historical unreferenced requirements.
  4. *Targeted Unit Test Verification*: Execute `python3 -m unittest tools/test_verify_requirement_governance.py` asserting 100% pass rate.

---

### TST-PRO-088: **System Verification Protocol Sign-Off & Release Gating Verification Suite** [Planned]
- **Verifies**: `REQ-PRO-088`
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Component**: `docs/releases/V5.0.0/sys_verification_record.md`, `tools/run_system_integration_tests.py`, `.github/workflows/android-release.yml`, `tools/test_generate_release_verification_summary.py`, `tools/test_system_integration_tests.py`
- **Jira**: `ATT-7560`, `ATT-8141`
- **Test Description**:
  1. *SYS.5 Verification Record & Release Gating Contract*: Verify that `docs/releases/V5.0.0/sys_verification_record.md` exists, defines all 8 `SYS-VRF-001`..`008` cases, validates absence of synthetic placeholder serials (`*-REF-*`), and enforces human PO attestation prior to release qualification.
  2. *System Integration Runner & Wire Protocol Alignment*: Execute `tools/run_system_integration_tests.py` verifying all 6 cases (`SYS-INT-001`..`006`) pass cleanly including `SYS-INT-003`.
  3. *Release Verification Summary Gating*: Run `tools/generate_release_verification_summary.py --tag V5.0.0 --fail-on-deviations` asserting that unbacked or placeholder verification records correctly yield `BLOCKED` and missing human attestation prevents release candidate authorization.
  4. *Targeted Regression Test Execution*: Run unit tests in `tools/test_generate_release_verification_summary.py` and `tools/test_system_integration_tests.py` asserting 100% pass rate.


---

### TST-PRO-089: **Kover Verification Gate Execution and Multi-Package Rule Audit** [Verified]
- **Verifies**: `REQ-PRO-089`
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Component**: `app/build.gradle`, `WorkoutSectionOrderPersistenceTest.kt`, `WorkoutSectionReorderContractTest.kt`, `docs/tests.md`
- **Jira**: `ATT-7563`
- **Test Description**:
  1. *Test Suite Hygiene Verification*: Execute `./gradlew testDebugUnitTest` and assert all unit tests pass (100% pass rate).
  2. *Kover Global Rule Verification*: Execute `./gradlew koverVerifyDebug` and assert line coverage $\ge 40\%$ and branch coverage $\ge 35\%$.
  3. *Kover Subsystem Rule Verification*: Verify bounded rules for `tracker`, `filter`, and `devices`/`sensor` packages pass in `koverVerifyDebug`.
  4. *Negative Mutation Verification*: Artificially elevate bounds (e.g., line 99%) and assert build failure, verifying gate enforcement.
  5. *Documentation Parity*: Confirm `docs/tests.md` and `docs/coding_standards.md` reflect ratcheted figures.

---

### TST-PRO-090: **Unified CI & Release Quality Gates Contract Verification Suite** [Verified]
- **Verifies**: `REQ-PRO-090`
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit
- **Component**: `app/src/test/java/com/atrainingtracker/trainingtracker/build/ReleaseCiAndLintConfigContractTest.kt`, `.github/workflows/android-release.yml`, `.github/workflows/android-branch-build.yml`
- **Jira**: `ATT-7564`
- **Test Description**:
  1. *Release Workflow Gate Presence & Ordering Contract*: Execute `ReleaseCiAndLintConfigContractTest.testReleaseWorkflow_runsLintDetektKoverAndGovernanceBeforeAssembly` verifying that `android-release.yml` executes `lintDebug`, `detekt`, `koverVerifyDebug`, and `verify_requirement_governance.py` prior to `assembleRelease :app:bundleRelease`.
  2. *Release Workflow Report Artifact Archiving Contract*: Execute `ReleaseCiAndLintConfigContractTest.testReleaseWorkflow_archivesQualityGateReports` verifying that `android-release.yml` uploads artifacts for lint, detekt, and kover reports with `if: always()`.
  3. *Branch Build Governance Step Contract*: Execute `ReleaseCiAndLintConfigContractTest.testBranchBuildWorkflow_runsGovernanceVerification` verifying that `android-branch-build.yml` executes `python3 tools/verify_requirement_governance.py`.
  4. *Governance Script Validation*: Run `python3 tools/verify_requirement_governance.py` asserting exit code 0 and zero governance violations across all shards.

---

### TST-PRO-091: **Independent ASPICE SUP.1 QA Auditing, Representative Sampling & Corrective Action Verification Suite** [Verified]
- **Verifies**: `REQ-PRO-091`
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Component**: `tools/jira_util.py`, `tools/verify_requirement_governance.py`, `tools/hoechstmass/`, `tools/test_jira_accounts.py`, `tools/test_verify_requirement_governance.py`, `tools/hoechstmass/tools/test_hoechstmass_tools.py`, `docs/engineering/qa_audits/`
- **Jira**: `ATT-7558`, `ATT-8152`, `ATT-8153`
- **Evidence**: Verified by `test_tst_pro_091_7_living_doc_shard_resolution`, `test_tst_pro_091_8_issue_description_ingestion`, `test_tst_pro_091_9_account_id_role_separation`, `test_tst_pro_091_10_seeded_random_sampling`, `test_tst_pro_091_11_dedicated_corrective_action_creation_and_deduplication`, and `test_tst_pro_091_13_ceremony2_retrospective_qa_tracking_protocol` in `tools/test_jira_accounts.py` (79/79 tests passed), `test_scenario_76_tst_pro_091_12_verifier_rejects_self_referential_corrective_actions` in `tools/test_verify_requirement_governance.py` (82/82 tests passed), and full Python test suite pass (181/181 passed).
- **Test Description**:
  1. *QA Auditor Role Independence & Coordinator Rejection*: Test in `test_jira_accounts.py` that `qa_audit` executes under `qa` / `agent2` and rejects `role="coordinator"` or `role="agent1"`.
  2. *Representative Sample Size Dynamic Scaling*: Test in `test_jira_accounts.py` that `qa_audit` samples $\ge 10\%$ when population exceeds 20 tickets (e.g. 25 tickets $\rightarrow \ge 3$ sampled).
  3. *Corrective Action Link Validation in Verifier*: Test in `test_verify_requirement_governance.py` that `verify_qa_audits` rejects `NON-COMPLIANT` reports lacking Jira issue keys in Section 4 and accepts reports with linked corrective actions.
  4. *Auditor Independence Verification in Verifier*: Test in `test_verify_requirement_governance.py` that `verify_qa_audits` rejects reports with `Auditor Role: coordinator`.
  5. *Counterpart Mirror Parity*: Verify parity in Höchstmaß mirror tooling (`test_hoechstmass_tools.py`).
  6. *Governance Script Validation*: Run `python3 tools/verify_requirement_governance.py` verifying exit code 0.
  7. *Living Document Shard Resolution (`TST-PRO-091.7`, tools/test_jira_accounts.py)*: Verify `qa_audit` resolves requirement references across all living requirement shards (`docs/requirements/req_*.md`) and passes compliant tickets.
  8. *Issue Description Ingestion (`TST-PRO-091.8`, tools/test_jira_accounts.py)*: Verify `fetch_all_sprint_issues` requests and parses the `description` field for requirement references.
  9. *AccountId Role Separation (`TST-PRO-091.9`, tools/test_jira_accounts.py)*: Verify `qa_audit` checks comment author `accountId` against `ROLE_ACCOUNT_IDS`, correctly evaluating independent reviews.
  10. *Seeded Random Sampling (`TST-PRO-091.10`, tools/test_jira_accounts.py)*: Verify `qa_audit` uses seeded pseudo-random sampling and records the random seed and sampled keys in the markdown header.
  11. *Dedicated Corrective Action Ticket Creation & Deduplication (`TST-PRO-091.11`, tools/test_jira_accounts.py)*: Verify `qa_audit` creates or links distinct dedicated corrective action tickets in raw Backlog (`add_to_sprint=False`, `rank_top=True`), deduplicated by non-conformance category, ensuring `ca_key != audited_key` in Section 4.
  12. *Self-Reference Rejection in Verifier (`TST-PRO-091.12`, tools/test_verify_requirement_governance.py)*: Verify `verify_qa_audits` rejects reports where corrective action links match the audited ticket key (`ca_key == audited_key`) for active/current sprints while grandfathering legacy historical audits prior to `2026-11-16_02_Spät`.
  13. *Ceremony 2 Retrospective QA Tracking & Multi-Sprint PO Escalation (`TST-PRO-091.13`, .agents/skills/sprint-planner/SKILL.md)*: Verify Ceremony 2 instructions require a dedicated 'QA Findings & Corrective Actions' section in the Retrospective and explicit PO escalation when an unclosed corrective action remains open for > 2 sprints.



### TST-PRO-092: **Process Metrics Credibility, Bug Severity Rubric & Downstream Escape Verification Suite** [Verified]
- **Verifies**: `REQ-PRO-092`
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Component**: `tools/sprint_metrics.py`, `tools/jira_util.py`, `tools/hoechstmass/`, `tools/test_sprint_metrics.py`, `tools/test_jira_accounts.py`, `tools/hoechstmass/tools/test_sprint_metrics.py`, `docs/project_governance.md`
- **Jira**: `ATT-7559`
- **Test Description**:
  1. *Jira API Issue Field Query & Severity Extraction*: Test that `collect_sprint_metrics_for_id` requests `labels` and `description` and that `parse_sprint_gate_metrics` correctly detects `severity-critical` and `severity-major` labels without defaulting to Minor.
  2. *Atlassian Document Format (ADF) Comment Parsing*: Test that `parse_verdicts_from_comments` and `parse_final_review_agent_verdicts_from_comments` correctly extract text and parse PASS / REVISION / BOUNCE from ADF dict structures without throwing `AttributeError` or `TypeError`.
  3. *Segregation of Unverified Gate Subtasks*: Test that gate subtasks in `Erledigt` status without verdict comments increment `unverified` and do NOT increment `passed_first_time` or inflate FTPR.
  4. *Downstream Defect Escape Accounting*: Test that Stage 6 bounces are incorporated into upstream gate escape metrics, yielding non-zero escape probabilities for gates with downstream defects.
  5. *Plausibility Warning Thresholds*: Test that `parse_sprint_gate_metrics` generates a plausibility warning when FTPR is 100% across $\ge 20$ gate subtasks without rejections.
  6. *Bug Severity Rubric Enforcement*: Test in `test_jira_accounts.py` that `create_issue` enforces valid severities and flags crash/ANR origin bugs when submitted with sub-Major severity without justification.


---

### TST-PRO-093: **Governance Rules Normative Structure and Shard Size Capping Enforcement Test** [Verified]
- **Verifies**: `REQ-PRO-093`
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Component**: `tools/shard_living_docs.py`, `tools/test_shard_living_docs.py`, `.agents/rules/aspice_governance.md`, `docs/engineering/rule_history_archive.md`
- **Jira**: `ATT-7565`
- **Test Description**:
  1. *Shard Size Verification Test (`tools/test_shard_living_docs.py`)*: Assert `check_shard_sizes()` flags temporary shards exceeding `max_lines` and passes for shards within limits.
  2. *Oversized Shard Splitting Test (`tools/test_shard_living_docs.py`)*: Verify `split_oversized_shard()` partitions an oversized shard into sub-shards with zero ID loss or format corruption.
  3. *Governance Contract Preservation*: Run `test_verify_requirement_governance.py`, `test_agent_emulator.py`, and `AspiceSweAuditContractTest.kt`, asserting all tests pass without regression.
  4. *Living Documentation Parity*: Execute `python3 tools/verify_requirement_governance.py` confirming 100% master table parity and zero dangling references.


---

### TST-PRO-094: **Legacy Requirement Attribute Backfill and Strict Verification Gate Suite** [Verified]
- **Verifies**: `REQ-PRO-094`
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Component**: `tools/verify_requirement_governance.py`, `tools/test_verify_requirement_governance.py`, `docs/requirements/`
- **Jira**: `ATT-7562`
- **Test Description**:
  1. *Missing Attribute Discovery*: Assert `find_missing_requirement_attributes()` returns 0 defective requirements.
  2. *Strict Verifier Execution*: Run `verify_requirement_attributes(enforce_attributes=True)` and assert pass with message `PASS: 100% of living requirements define valid Priority, Type, and Verification Method attributes.`
  3. *Negative Mutation Verification*: In `tools/test_verify_requirement_governance.py`, assert that any requirement without attributes fails verification.
  4. *Governance Parity Verification*: Execute `python3 tools/verify_requirement_governance.py` and confirm 100% parity with zero warnings.


---

### TST-PRO-095: **Weekly Dual-Vendor Code Review Governance and Automation Verification** [Verified]
- **Verifies**: `REQ-PRO-095`
- **Priority**: Medium
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Component**: `tools/jira_util.py`, `tools/test_jira_accounts.py`, `tools/verify_requirement_governance.py`, `tools/test_verify_requirement_governance.py`, `docs/engineering/code_reviews/`
- **Jira**: `ATT-8213`
- **Test Description**:
  1. *Canonical Directory Registration Test (`tools/test_verify_requirement_governance.py`)*: Assert `code_reviews` is recognized in `ALLOWED_ENGINEERING_ENTRIES` and `verify_canonical_engineering_entries()` passes with `docs/engineering/code_reviews/` present.
  2. *Subsystem Coverage Matrix Parsing & Slice Selection Test (`tools/test_jira_accounts.py`, [Verified] ATT-8426)*: Assert helper function `get_next_review_subsystem()` correctly parses `coverage_matrix.md` with extended package mapping (`repositories/`, `cloud/`, `onlinecommunities/`, `segments/`, `climbs/`) and selects unreviewed or oldest audited subsystem slices.
  3. *Weekly Review Ticket Creation & Deduplication Test (`tools/test_jira_accounts.py`, [Verified] ATT-8426)*: Mock Jira API and verify `ensure_weekly_review_ticket()` creates a ticket with generic "current top model" specification and `rank_top=True` under `ATT-232` when overdue and skips creation when an active review ticket already exists.
  4. *7-Day Cadence & Decoupled Release Gate Test (`tools/test_jira_accounts.py`, [Verified] ATT-8426)*: In `test_is_weekly_review_overdue`, verify that `is_weekly_review_overdue()` returns `False` when the latest review is $\le 7$ days old even when other subsystems remain `UNREVIEWED`, preventing release gating deadlock.
  5. *Governance Documentation, Model Mandate & Claude Non-Simulation Protocol Review (`[Verified] ATT-8426`)*: Verify generic model formulation and explicit Claude non-simulation handoff protocol across `docs/coding_standards.md`, `release_plan.md`, `coverage_matrix.md`, and `template_dual_vendor_code_review.md`.
  6. *Living Documentation Master Parity*: Run `python3 tools/verify_requirement_governance.py` confirming 100% parity across `docs/requirements.md` and `docs/tests.md`.


---

### TST-PRO-096: **ASPICE 4.0 SUP.1 QA Governance, Audit Evidence Store, and Automation Verification** [Verified]
- **Verifies**: `REQ-PRO-096`
- **Priority**: Medium
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Component**: `docs/management/qa_plan.md`, `tools/jira_util.py`, `tools/test_jira_accounts.py`, `tools/verify_requirement_governance.py`, `tools/test_verify_requirement_governance.py`, `docs/engineering/aspice_audits/`
- **Jira**: `ATT-8212`
- **Test Description**:
  1. *Canonical Directory Registration Test (`tools/test_verify_requirement_governance.py`)*: Assert `aspice_audits` is recognized in `ALLOWED_ENGINEERING_ENTRIES` and `verify_canonical_engineering_entries()` passes with `docs/engineering/aspice_audits/` present.
  2. *ASPICE Audit Currency & Overdue Check Test (`tools/test_jira_accounts.py`)*: Assert `is_weekly_aspice_audit_overdue()` correctly evaluates elapsed days against records in `docs/engineering/aspice_audits/`.
  3. *Weekly ASPICE Audit Ticket Creation & Deduplication Test (`tools/test_jira_accounts.py`)*: Mock Jira API and verify `ensure_weekly_aspice_audit_ticket()` creates a ticket with `rank_top=True` under `ATT-232` when overdue and skips creation when an active ticket already exists.
  4. *SUP.1 QA Plan Governance & Release Gate Review*: Verify alignment across `docs/management/qa_plan.md`, `cm_plan.md`, `sprint-planner/SKILL.md`, and `release-manager/SKILL.md`.
  5. *Living Documentation Parity*: Run `python3 tools/verify_requirement_governance.py` confirming 100% parity across `docs/requirements.md` and `docs/tests.md`.


---

### TST-PRO-097: **Capacity-Bound Bin-Packing, V5.0.0 Release Plan, and FixVersion Governance Verification** [Verified]
- **Verifies**: `REQ-PRO-097`
- **Priority**: Medium
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Component**: `docs/management/release_plan.md`, `tools/jira_util.py`, `tools/test_jira_accounts.py`, `sprint-planner/SKILL.md`, `release-manager/SKILL.md`, `.agents/rules/aspice_governance.md`
- **Jira**: `ATT-8162`
- **Test Description**:
  1. *Multi-Lane Capacity Bin-Packing Test (`tools/test_jira_accounts.py`)*: Assert `select_greedy_bin_pack_tickets()` packs candidate tickets in strict `ORDER BY rank ASC` across disjoint Epics, respects `max_sp_per_lane` / capacity bounds, and preserves excess tickets of saturated Epics for subsequent sprints.
  2. *FixVersion Warning Suppression Test (`tools/test_jira_accounts.py`)*: Assert `show_issue()` does not emit FixVersion missing warnings when tickets are in non-terminal statuses (`Zu erledigen`, `In Bearbeitung`, `In Überprüfung`), but alerts when status is `Erledigt` and `fixVersions` is empty.
  3. *Release Plan Structure, Field Test Path & Pseudo-Sprint Review (`[Verified] ATT-8426`)*: Inspect `docs/management/release_plan.md` confirming full coverage of `V5.0.0` milestone epics, 12h continuous cadence, synchronization with `docs/releases/V5.0.0/sys_verification_record.md` (Section 3), and reference to pseudo-sprint `Bereit für PO Systemtest` (ID 454).
  4. *Governance & Lifecycle Parity Review*: Verify consistency across `.agents/rules/aspice_governance.md` (Rule 8.3 & Rule 85), `sprint-planner/SKILL.md`, and `release-manager/SKILL.md`.
  5. *Living Documentation Parity*: Execute `python3 tools/verify_requirement_governance.py` confirming 100% parity across `docs/requirements.md` and `docs/tests.md`.


---

### TST-PRO-098: **Human-Attested Field Evidence Extractor, Release Guarding and Governance Verification Suite** [Verified]
- **Verifies**: `REQ-PRO-098`
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Component**: `tools/extract_pixel_field_evidence.py`, `tools/generate_release_verification_summary.py`, `tools/test_generate_release_verification_summary.py`, `docs/releases/TEMPLATE.md`, `docs/releases/V5.0.0/sys_verification_record.md`, `.agents/rules/aspice_governance.md`
- **Jira**: `ATT-8141`
- **Test Description**:
  1. *Placeholder Serial & Synthetic Data Rejection Test*: In `tools/test_generate_release_verification_summary.py`, verify that `collect_sys5_summary()` detects placeholder serials (`P8-REF-2024-001`, `P6-REF-2022-044`, `*-REF-*`) and treats them as blocking deviations.
  2. *Human PO Attestation Gating Test*: In `tools/test_generate_release_verification_summary.py`, assert that missing human PO attestation (missing signature, date, or build SHA) reports `BLOCKED` under `generate_release_verification_summary.py`.
  3. *Pixel Telemetry Extractor & Gap Coach Test (`TST-PRO-098.3`, [Verified] ATT-8427)*: In `tools/test_extract_pixel_field_evidence.py`, verify that `tools/extract_pixel_field_evidence.py` parses authentic `WorkoutSummaries` and `ExtremumValues` schema, extracts genuine sensor signals, reports `None` for unmeasured metrics, correctly parses TCX XML files without synthetic values, and outputs prioritized gap recommendations for unexercised sensor combinations.
  4. *V5.0.0 Protocol & Template Schema Audit*: Verify `docs/releases/TEMPLATE.md` and `docs/releases/V5.0.0/sys_verification_record.md` contain the Human PO Attestation block and zero synthetic assertions.
  5. *Agent Governance Rule Verification*: Assert that `.agents/rules/aspice_governance.md` mandates `human-execution` labeling and forbids autonomous agents from fabricating physical test evidence.
  6. *Living Documentation Parity*: Execute `python3 tools/verify_requirement_governance.py` confirming 100% parity across `docs/requirements.md` and `docs/tests.md`.


---

### TST-PRO-099: **Test Result-Driven Verification Status & Evidence Gating Suite** [Verified]
- **Requirement(s)**: `REQ-PRO-099`
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Component**: `tools/verify_requirement_governance.py`, `tools/jira_util.py`, `tools/hoechstmass/tools/jira_util.py`, `tools/test_verify_requirement_governance.py`, `tools/test_jira_accounts.py`, `.agents/skills/stage5-verification/SKILL.md`
- **Jira**: `ATT-8143`
- **Test Description**:
  1. *JUnit XML Result Ingestion & TST Mapping Test (`tools/test_verify_requirement_governance.py`)*: Assert parser extracts testcases from JUnit XML files (`TEST-*.xml`), maps method names and code tags to `TST-XXX` IDs, and aggregates pass/fail/error counts.
  2. *Failing Test Result Evidence Gate Test (`tools/test_verify_requirement_governance.py`)*: Assert `verify_verified_test_evidence()` rejects `[Verified]` status with exit code 1 when a mapped test has `<failure>` or `<error>` in JUnit XML.
  3. *Unbacked TST Rejection & Ratchet Baseline Test (`tools/test_verify_requirement_governance.py`)*: Assert net-new `[Verified]` TST without passing test evidence fails, while legacy baseline items within `LEGACY_UNBACKED_VERIFIED_TSTS` pass with warning.
  4. *Stage 5 Evidence-Gated Promotion Test (`tools/test_jira_accounts.py`)*: Assert `auto_verify_living_docs_on_stage5()` checks test results before flipping status, promoting only items with passing evidence and keeping unbacked/failing items as `[Implemented]`.
  5. *Clean Worktree Default Verification Safety Test*: Assert default `verify_requirement_governance.py` passes cleanly in fresh worktree without requiring pre-existing build artifacts.
  6. *Living Documentation Parity*: Execute `python3 tools/verify_requirement_governance.py` confirming 100% parity across `docs/requirements.md` and `docs/tests.md`.

---

### TST-PRO-100: **Versioned Test and Coverage Evidence Contract & Gating Suite** [Verified]
- **Requirement(s)**: `REQ-PRO-100`
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Component**: `tools/generate_test_evidence.py`, `tools/test_generate_test_evidence.py`, `tools/jira_util.py`, `tools/verify_requirement_governance.py`, `tools/test_verify_requirement_governance.py`, `app/src/test/java/com/atrainingtracker/trainingtracker/build/ReleaseCiAndLintConfigContractTest.kt`, `docs/management/cm_plan.md`, `.agents/rules/aspice_governance.md`
- **Jira**: `ATT-8144`
- **Test Description**:
  1. *Test Evidence Generator Unit Suite (`TST-PRO-100.1`, tools/test_generate_test_evidence.py)*:
  • Assert parser extracts JUnit XML test results and calculates total, passed, failed, error, and skipped counts.
  • Assert parser correlates test cases to `TST-XXX` IDs and produces structured pass/fail mapping.
  • Assert parser parses Kover XML report and extracts global and per-package line and branch coverage percentages.
  • Assert generator produces valid, readable `docs/engineering/test_evidence/<target>.md` and `.json` files.
  2. *Governance Gating & Directory Structure Test (`TST-PRO-100.2`, tools/test_verify_requirement_governance.py)*:
  • Assert `verify_engineering_directory_structure()` allows `docs/engineering/test_evidence/` and rejects `docs/engineering/test_results` symlink.
  • Assert `verify_requirement_governance.py --check-test-evidence` validates presence, format, and non-empty status of sprint and release evidence files.
  3. *Release CI Contract & CM Plan Verification (`TST-PRO-100.3`, ReleaseCiAndLintConfigContractTest.kt)*:
  • Assert `ReleaseCiAndLintConfigContractTest.kt` verifies retirement of `docs/engineering/test_results` symlink and presence of `docs/engineering/test_evidence/`.
  • Assert `docs/management/cm_plan.md` registers `docs/engineering/test_evidence/` under controlled Configuration Items.
  4. *Living Documentation Parity*:
  • Execute `python3 tools/verify_requirement_governance.py` confirming 100% parity across `docs/requirements.md` and `docs/tests.md`.

---

### TST-PRO-101: **Unbacked Status Triage, Test-Tag Reconciliation & Ratchet Reduction Suite (ATT-8145)** [Verified]
- **Requirement(s)**: `REQ-PRO-101`
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Component**: `tools/verify_requirement_governance.py`, `tools/hoechstmass/tools/verify_requirement_governance.py`, `tools/test_verify_requirement_governance.py`, `tools/hoechstmass/tools/test_hoechstmass_tools.py`, `app/src/test/java/com/atrainingtracker/trainingtracker/build/AspiceSweAuditContractTest.kt`, `app/src/test/java/com/atrainingtracker/trainingtracker/build/HoechstmassToolingContractTest.kt`, `docs/engineering/test_evidence/ATT-8145_unbacked_triage.csv`
- **Jira**: `ATT-8145`
- **Test Description**:
  1. *Deterministic Unbacked Triage & CSV Ledger Test (`TST-PRO-101.1`, tools/test_verify_requirement_governance.py)*:
  • Verify `--triage-unbacked` classifies unbacked `[Verified]` TSTs across all shards into `unit-test-backed`, `UI/manual-only`, `obsolete/duplicate`, and `missing test code`, flags stale already-backed `LEGACY_UNBACKED_VERIFIED_TSTS` entries, and writes `docs/engineering/test_evidence/ATT-8145_unbacked_triage.csv` cleanly under `ALLOWED_ENGINEERING_ENTRIES`.
  2. *Test-Tag Reconciliation & Evidence Verification Test (`TST-PRO-101.2`, tools/test_verify_requirement_governance.py & targeted Gradle unit tests)*:
  • Verify `extract_test_code_tags()` detects newly added `TST-*` tags across `*Test.kt`, `*Test.java`, and `tools/test_*.py` suites for `unit-test-backed` specs, and targeted Gradle / Python unit tests pass.
  3. *Truthful Downgrade, Orphan & Heading Suffix Compliance Test (`TST-PRO-101.3`, AspiceSweAuditContractTest.kt & verify_requirement_governance.py)*:
  • Verify downgraded `UI/manual-only` (incl. `TST-MIG-006`), `obsolete/duplicate`, `missing test code`, `14` `[Verified]` orphans, and unbacked REQs use standard `[Verified|Implemented|Planned|Open]` suffixes (`testLivingDocShards_allHeadingsStandardized`), and `--sync-indexes` maintains 100% master table parity (`testMasterTableParity_matchesDomainShards`).
  4. *Ratchet Reduction & Scenario 60/61 Fixture Test (`TST-PRO-101.4`, tools/test_verify_requirement_governance.py)*:
  • Verify `LEGACY_UNBACKED_VERIFIED_TSTS` ratchet reduction passes `--check-verified-evidence` with exit code `0`, and `test_scenario_60` / `test_scenario_61` validate ratchet enforcement and clean-worktree safety using parameterized `baseline_allowlist` / reconciled fixtures.
  5. *Höchstmaß Mirror Parity Contract Test (`TST-PRO-101.5`, HoechstmassToolingContractTest.kt & tools/hoechstmass/tools/test_hoechstmass_tools.py)*:
  • Verify `HoechstmassToolingContractTest` and `python3 -m unittest discover` in `tools/hoechstmass/tools/` pass with zero hardcoded `ATT` project/user IDs.

---

### TST-PRO-102: **Boundary-Safe SYSREQ Traceability & Bidirectional Coverage Verification Suite (ATT-8146)** [Verified]
- **Requirement(s)**: `REQ-PRO-102`
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Component**: `tools/verify_requirement_governance.py`, `tools/test_verify_requirement_governance.py`
- **Jira**: `ATT-8146`
- **Test Description**:
  1. *SYSREQ Boundary False-Positive Rejection Test (`TST-PRO-102.1`, tools/test_verify_requirement_governance.py)*:
  • Verify a document containing only `SYSREQ-DEX-001` or `SYSREQ-TRK-001` does NOT extract `REQ-DEX-001` / `REQ-TRK-001` into `sys_to_swe` or `_collect_test_file_index()`, while valid standalone `REQ-*` references still match cleanly.
  2. *Derived Prefix Exclusion Test (`TST-PRO-102.2`, tools/test_verify_requirement_governance.py)*:
  • Verify `REQ-PRO-*` (and other `SWE_DERIVED_PREFIXES`) mentioning `SYSREQ-*` are excluded from `sys_to_swe` and `swe_refinement_total_count`.
  3. *Bidirectional Coverage & Live CLI Invariant Verification Test (`TST-PRO-102.3`, [Verified] ATT-8437, tools/test_verify_requirement_governance.py)*:
  • Verify `report` exposes accurate `swe_refinement_total_count`, `swe_refinement_covered_count`, `swe_refinement_coverage_pct`, and `swe_to_sys_map`; verify live `--verify-sys-trace --report-sys-coverage` prints `12/12` (`100.0%`) forward and reverse `System-Refinement` coverage with exit code `0`, and `test_scenario_68` asserts structural invariants: exact forward parity `covered_count == sysreq_count == 12`, lower-bound floors `swe_refinement_total_count >= 362`, `swe_refinement_covered_count >= 346`, `swe_refinement_coverage_pct >= 95.0`, and zero `REQ-DEX-*` / `REQ-PRO-*` pollution.

---

### TST-PRO-103: **Complete Bidirectional SYS-to-SWE Traceability & System-Refinement Classification Suite (ATT-8147)** [Verified]
- **Requirement(s)**: `REQ-PRO-103`
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Component**: `tools/verify_requirement_governance.py`, `tools/test_verify_requirement_governance.py`, `app/src/test/java/com/atrainingtracker/trainingtracker/build/SystemLiveTrackingRequirementsContractTest.kt`, `app/src/test/java/com/atrainingtracker/trainingtracker/build/SystemDataExchangeRequirementsContractTest.kt`, `app/src/test/java/com/atrainingtracker/trainingtracker/build/SystemRequirementsSpecificationContractTest.kt`, `app/src/test/java/com/atrainingtracker/trainingtracker/build/SystemVerificationProtocolContractTest.kt`, `app/src/test/java/com/atrainingtracker/trainingtracker/build/AspiceSweAuditContractTest.kt`
- **Jira**: `ATT-8147`
- **Test Description**:
  1. *System Requirement(s) Attribute & Derived (SW-only) Parser Test (`TST-PRO-103.1`, tools/test_verify_requirement_governance.py)*:
  • Verify `verify_system_software_traceability()` parses `- **System Requirement(s)**:` for both `SYSREQ-*` links and `Derived (SW-only)` rationales, classifies `RAD`/`CLM` prefixes cleanly, and rejects unclassified `SYSTEM_REFINED_PREFIXES` REQs under `--check-reverse-sys-trace` / `fail_on_unclassified_swe=True`.
  2. *Live Repository 100% Bidirectional Classification & Invariant Test (`TST-PRO-103.2`, [Verified] ATT-8437, tools/test_verify_requirement_governance.py)*:
  • Verify `--check-reverse-sys-trace` and `--verify-sys-trace --report-sys-coverage` pass on the live repository with `12/12` (`100.0%`) forward coverage, and `test_scenario_70` asserts structural invariants: `reverse_classified_count == swe_refinement_total_count`, `swe_refinement_covered_count + swe_derived_sw_only_count == swe_refinement_total_count`, `unclassified_swe_refinement_reqs == []`, `swe_refinement_total_count >= 362`, and `reverse_classified_pct == 100.0`.
  3. *System Contract & Fixture Compatibility Test (`TST-PRO-103.3`, System*ContractTest.kt & tools/test_verify_requirement_governance.py)*:
  • Verify `SystemLiveTrackingRequirementsContractTest`, `SystemDataExchangeRequirementsContractTest`, `SystemRequirementsSpecificationContractTest`, `SystemVerificationProtocolContractTest`, `AspiceSweAuditContractTest`, and synthetic fixtures (`test_scenario_40..43`, `test_scenario_66..68`) all pass with exit code `0`.

---

### TST-PRO-104: **Code-to-Doc Orphan Guard, Shard Header Count Sync & REQ Description Governance Suite (ATT-8148)** [Verified]
- **Requirement(s)**: `REQ-PRO-104`
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Component**: `tools/verify_requirement_governance.py`, `tools/test_verify_requirement_governance.py`
- **Jira**: `ATT-8148`
- **Test Description**:
  1. *Code-to-Shard Orphan Detection & Typo Resolution Test (`TST-PRO-104.1`, tools/test_verify_requirement_governance.py)*:
  • Verify `verify_code_traceability_orphans()` scans `app/src/main`, `app/src/test`, `app/src/androidTest`, and `tools/`, flags undeclared `REQ-*` and `TST-*` tokens, passes cleanly with 0 orphans on the live repo after restoring missing specs and fixing `REQ-UI-314` and `REQ-MIG-053` typos, and validates synthetic orphan injection failure.
  2. *Living Doc Shard Header Synchronization & Audit Test (`TST-PRO-104.2`, tools/test_verify_requirement_governance.py)*:
  • Verify `recalculate_master_index_tables()` parses and writes exact header counts across all 18 shards in `docs/requirements/` and `docs/tests/`, and verify governance check rejects any shard with divergent `**Total Requirements**` or `**Total Tests**` headers.
  3. *Description Field Ratchet & Field Coverage Reporting Test (`TST-PRO-104.3`, tools/test_verify_requirement_governance.py)*:
  • Verify `--check-req-description` enforces `- **Description**:` on non-baselined requirements (including `REQ-PRO-104`), rejects new REQs lacking description, and verify `--report-req-field-coverage` outputs accurate percentages for Description, Acceptance Criteria, and Allocation fields.
  4. *Restored Requirement Governance & Evidence Cross-Check Test (`TST-PRO-104.4`, tools/test_verify_requirement_governance.py)*:
  • Verify all restored requirements in `SYSTEM_REFINED_PREFIXES` define valid `- **System Requirement(s)**:`, no restored test is marked `[Verified]` without test code evidence, and `--check-verified-evidence` and `--check-reverse-sys-trace` exit `0`.

---

### TST-PRO-105: **Explicit PO Approval & Changelog Provenance for Agent-Originated Change Requests Suite (ATT-8166)** [Verified]
- **Requirement(s)**: `REQ-PRO-105`
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Component**: `tools/jira_util.py`, `tools/test_jira_accounts.py`
- **Jira**: `ATT-8166`
- **Test Description**:
  1. *Agent Backlog Placement & Direct Sprint Block Test (`TST-PRO-105.1`, tools/test_jira_accounts.py)*:
  • Verify `create_issue` creates issues in raw Backlog without sprint assignment and rejects `--add-to-sprint` when invoked under agent roles (`agent1`, `agent2`, `coordinator`).
  2. *Backlog-to-PO-Queue Two-Way Guard Test (`TST-PRO-105.2`, tools/test_jira_accounts.py)*:
  • Verify moving issues from raw Backlog into `Vom PO Freigegeben` (ID 447) or an active sprint is hard-rejected for all agent roles, while Coordinator `pack-sprint` (`447 -> active_sprint`) and `return-unstarted` (`active_sprint -> 447`) continue to function.
  3. *PO Changelog Provenance & QA Audit Dimension 6 Test (`TST-PRO-105.3`, tools/test_jira_accounts.py)*:
  • Verify `verify_po_approval_changelog()` parses Jira changelog history, passes issues moved to ID 447 or created by human PO `accountId`, rejects unapproved agent-originated issues, and asserts `qa_audit()` integrates Dimension 6 (`po_approval_pass`) while exempting ceremony tickets.
  4. *Historical Exception Disposition & Living Doc Parity Test (`TST-PRO-105.4`, Jira / tools/test_jira_accounts.py)*:
  • Verify `ATT-7902` disposition record confirms tests-only PO authorization, and verify all living doc shards pass governance checks with Description field ratchet compliance.

---

### TST-PRO-106: **Atomic REQ and TST ID Reservation Across Parallel Sprint Lanes Suite (ATT-8149)** [Verified]
- **Requirement(s)**: `REQ-PRO-106`
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Component**: `tools/jira_util.py`, `tools/test_jira_accounts.py`
- **Jira**: `ATT-8149`
- **Test Description**:
  1. *Atomic Cross-Worktree ID Reservation Test (`TST-PRO-106.1`, tools/test_jira_accounts.py)*:
  • Verify `reserve_id()` locks `/tmp/att_id_reservation.lock`, persists reservations to `$(git rev-parse --git-common-dir)/att_id_reservations.json`, guarantees cross-worktree visibility across simulated parallel worktrees, and prevents duplicate allocations under concurrent threads/processes.
  2. *Monotonic Next-ID Calculation with Active Reservation Awareness Test (`TST-PRO-106.2`, tools/test_jira_accounts.py)*:
  • Verify next ID selection considers both living documentation files and active ledger reservations, correctly producing `max(committed, reserved) + 1` for single and multi-count allocations.
  3. *Gate 2 Reservation Provenance Verification Test (`TST-PRO-106.3`, tools/test_jira_accounts.py)*:
  • Verify `gate-verdict` on Stage 2 subtasks passes when all new REQ/TST IDs match the parent ticket reservation and rejects with a governance error when an unreserved ID is detected.
  4. *Reservation Hygiene, Expiration & Code Orphan Parity Test (`TST-PRO-106.4`, tools/test_jira_accounts.py)*:
  • Verify reservation release on ticket lifecycle completion, ledger cleanup, and verify `verify_requirement_governance.py --check-code-orphans` reports zero false positives.

---

### TST-PRO-107: **Separated Gate Review Authorship, Evidence Checklists & Zero-Revision Alerts Suite (ATT-8150)** [Verified]
- **Requirement(s)**: `REQ-PRO-107`
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Component**: `tools/jira_util.py`, `tools/sprint_metrics.py`, `tools/test_jira_accounts.py`
- **Jira**: `ATT-8150`
- **Test Description**:
  1. *Separated Authorship Enforcement Test (`TST-PRO-107.1`, tools/test_jira_accounts.py)*:
  • Verify `submit_stage` detects if the caller actor matches the Gate N reviewer on the same parent ticket, rejects self-review handoffs with an error, and permits valid alternating author submissions.
  2. *Evidence-Backed Gate 1–5 Checklist Validation Test (`TST-PRO-107.2`, tools/test_jira_accounts.py)*:
  • Verify `gate_verdict` accepts verdicts with structured checklists and verifiable evidence anchors (`file:line`, commit SHA, test execution hashes), rejects unstructured or anchorless text, and flags rapid reviews.
  3. *Multi-Sprint 100% FTPR Rubber-Stamping Alert Test (`TST-PRO-107.3`, tools/test_jira_accounts.py)*:
  • Verify `sprint_metrics` inspects historical sprint metrics, triggers a Rubber-Stamping Warning when consecutive sprints exhibit 100.0% FTPR, and verifies integration into retro comments.
  4. *Rule & Living Documentation Parity Test (`TST-PRO-107.4`, tools/test_jira_accounts.py)*:
  • Verify updated rules in `aspice_governance.md` and `jira-workflow/SKILL.md` conform to separated pair reviews and all living doc shards pass governance checks with zero code orphans.

---

### TST-PRO-108: **Technical Role Separation for Agent Credentials & Git Commit Identity Suite (ATT-8151)** [Verified]
- **Requirement(s)**: `REQ-PRO-108`
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Component**: `tools/jira_util.py`, `tools/parallel_sprint_lock.py`, `tools/test_jira_accounts.py`
- **Jira**: `ATT-8151`
- **Test Description**:
  1. *Subagent Identity & Role Binding Enforcement Test (`TST-PRO-108.1`, tools/test_jira_accounts.py)*:
  • Verify that when `JIRA_ACTOR` is set in the environment (e.g. `agent1`), passing a mismatched `--as` flag (e.g. `--as agent2`) raises a SystemExit role mismatch error. Verify that matching `--as agent1` succeeds, and verify that when `JIRA_ACTOR` is unset, `--as coordinator` and manual role invocations continue to succeed without rejection.
  2. *Dedicated QA Role Credential Mapping & Account Independence Test (`TST-PRO-108.2`, tools/test_jira_accounts.py)*:
  • Verify that `resolve_account("qa")` resolves `JIRA_QA_USER` and `JIRA_QA_TOKEN`, verify that `get_config(role="qa")` isolates QA credentials, and verify validation rejects configurations where `agent2` and `qa` share identical account IDs.
  3. *Producing Agent Identity & Reviewer Git Commit Trailers Test (`TST-PRO-108.3`, tools/test_jira_accounts.py)*:
  • Verify that `_merge_ticket_impl` formats squash commit messages with standard git trailers (`Agent-Role:`, `Agent-Model:`, `Reviewed-by:`), correctly reading from environment or arguments and passing automated trailer validation.
  4. *Single-OS User Residual Risk Review Test (`TST-PRO-108.4`, docs/management/risk_register.md)*:
  • Verify that `docs/management/risk_register.md` contains an approved entry documenting the residual risk and defense-in-depth controls for co-located subagent execution under a single OS account.

---

### TST-PRO-109: **Stage Transition Precondition Enforcement, Impl-Plan Gate Validation & Bypass Prevention Suite (ATT-8154)** [Verified]
- **Requirement(s)**: `REQ-PRO-109`
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Component**: `tools/jira_util.py`, `tools/test_jira_accounts.py`
- **Jira**: `ATT-8154`
- **Test Description**:
  1. *Predecessor Stage Precondition Enforcement in `submit-stage` Test (`TST-PRO-109.1`, tools/test_jira_accounts.py)*:
  • Verify that calling `submit_stage` with stage 4 on a parent ticket in `Analysis` raises SystemExit with non-zero exit code. Verify that Stage 1 allows transition from `zu erledigen` to `analysis`, and Stages 2..5 succeed when the parent is in the expected stage.
  2. *Stage 6 Gate 5 Prerequisite Enforcement in `final-review-verdict` Test (`TST-PRO-109.2`, tools/test_jira_accounts.py)*:
  • Verify that `final_review_verdict` raises SystemExit with non-zero exit code when called on a parent ticket whose status is not `Final Review (Agent)`. Verify that `--bounce-target` healing flow back to `Implementation` or `Analysis` continues to succeed.
  3. *Strict Subtask & Impl-Plan Summary Validation in `check-gate` Test (`TST-PRO-109.3`, tools/test_jira_accounts.py)*:
  • Verify that `check_gate` raises SystemExit with exit code 1 when passed a non-subtask issue key or a subtask with summary `[Analysis]` even if its status is `Erledigt`. Verify that it passes (exit code 0) only when the issue is a sub-task, its summary matches `[Impl-Plan]`, and its status is `Erledigt`.
  4. *Structured Pros/Cons & Recommendation Validation Test (`TST-PRO-109.4`, tools/test_jira_accounts.py)*:
  • Verify that `final_review_verdict` rejects plain keyword-stuffed unstructured text on `PASS`, and successfully accepts structured reports matching `stage6-final-review/SKILL.md` sections with bullets or structured options tables.
  5. *Sanctioned Coordinator Workflow Invariants Test (`TST-PRO-109.5`, tools/test_jira_accounts.py)*:
  • Verify that Coordinator operations (`pack_sprint`, `return_unstarted`, `carryover_wip`, `archive_po_acceptance`, `defer_to_systemtest_queue`), `ATT-8153` QA non-conformance ticket generation, and `ATT-8426` decoupled 7-day review currency checks remain 100% functional.

---

### TST-PRO-110: **Integration Verification Test Strategy & Evidence Integrity Suite (ATT-8155)** [Verified]
- **Requirement(s)**: `REQ-PRO-110`
- **Priority**: High
- **Type**: Functional
- **Verification Method**: Integration / Review
- **Component**: `app/src/test/java/com/atrainingtracker/trainingtracker/build/AspiceSweAuditContractTest.kt`, `tools/generate_test_evidence.py`
- **Jira**: `ATT-8155`
- **Test Description**:
  1. *Architecture Integration Sequence Parity Test (`TST-PRO-110.1`, build/AspiceSweAuditContractTest.kt)*:
  • Verify that `AspiceSweAuditContractTest` asserts 100% bidirectional traceability between `INT-SWE-01..04`, `IF-SWE-01..04`, and executable integration test classes.
  2. *Robolectric Level 1 Execution & Fast Feedback Test (`TST-PRO-110.2`, integration/*.kt)*:
  • Verify that all four integration test suites execute under `./gradlew testDebugUnitTest` within fast turnaround bounds (<= 30s) without requiring emulator or physical hardware.
  3. *Machine-Readable Test Evidence & JUnit XML Archival Test (`TST-PRO-110.3`, tools/generate_test_evidence.py)*:
  • Verify that `tools/generate_test_evidence.py` and `tools/jira_util.py export-test-evidence` parse JUnit XML results for `INT-SWE-01..04` and archive versioned evidence under `docs/engineering/test_evidence/`.
  4. *Living Documentation & Shard Parity Test (`TST-PRO-110.4`, tools/verify_requirement_governance.py)*:
  • Verify that all living documentation shards pass governance checks with zero dangling references and zero code orphans.

---

### TST-PRO-111: **INT-SWE-05..08 Integration Verification & Strategy Parity Suite (ATT-8156)** [Verified]
- **Requirement(s)**: `REQ-PRO-111`
- **Priority**: High
- **Type**: Functional
- **Verification Method**: Integration / Review
- **Component**: `app/src/test/java/com/atrainingtracker/trainingtracker/build/AspiceSweAuditContractTest.kt`, `docs/architecture/integration_strategy.md`
- **Jira**: `ATT-8156`
- **Test Description**:
  1. *Integration Test Suite & Spec Lock (`TST-PRO-111.1`, build/AspiceSweAuditContractTest.kt)*:
  • Verify `DomainCalculatorsIntegrationTest.kt`, `PersistenceSqliteIntegrationTest.kt`, `FileExportersIntegrationTest.kt`, and `CloudHealthConnectIntegrationTest.kt` exist on disk, use `RobolectricTestRunner` (`sdk = [34]`), and cite `TST-INT-013..016`.
  2. *Integration Strategy Factual Sync (`TST-PRO-111.2`, docs/architecture/integration_strategy.md)*:
  • Verify `integration_strategy.md` reflects authentic `sprint_455.md` / `ATT-8500` metrics (`6,540` tests, `1,119` suites, `29m 21s` Tier-2 / `2,635.73s` sum), Level 1 `INT-SWE-01..08` scope, and `emulator-5554` policy.
  3. *Machine-Readable Evidence & Governance (`TST-PRO-111.3`, tools/verify_requirement_governance.py)*:
  • Verify JUnit XML captures `TST-INT-013..016` and `TST-PRO-111`, and living documentation shards pass governance checks with zero dangling references or code orphans.

---

### TST-PRO-112: **Simulator Self-Test Scoping & Release Gate Honesty Test Suite (ATT-8157)** [Verified]
- **Requirement(s)**: `REQ-PRO-112`
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Component**: `app/src/test/java/com/atrainingtracker/trainingtracker/build/SystemIntegrationTestContractTest.kt`, `app/src/test/java/com/atrainingtracker/trainingtracker/build/ReleaseManagerSystemEvidenceContractTest.kt`, `tools/test_generate_release_verification_summary.py`, `tools/verify_requirement_governance.py`
- **Jira**: `ATT-8157`
- **Test Description**:
  1. *Simulator Self-Test Rename & Purity Test (`TST-PRO-112.1`, tools/test_simulator_self_tests.py & SystemIntegrationTestContractTest.kt)*:
  • Verify `tools/run_simulator_self_tests.py` (`SimulatorSelfTestRunner`) tests `ble_hr_simulator.py`, `ble_power_simulator.py`, and `ble_radar_simulator.py` without fake Android/constant-arithmetic claims, `--output` JSON export succeeds, legacy `run_system_integration_tests.py` is removed, and `SystemIntegrationTestContractTest.kt` passes.
  2. *Non-Blocking Release Gate Removal Test (`TST-PRO-112.2`, tools/test_generate_release_verification_summary.py & ReleaseManagerSystemEvidenceContractTest.kt)*:
  • Verify `collect_sys4_summary()` in `tools/generate_release_verification_summary.py` raises zero deviations when `system_integration_results.json` is absent, does not falsely emit `[ PASS ]` SYS.4 app integration status, and `ReleaseManagerSystemEvidenceContractTest.kt` asserts `SKILL.md` / `docs/releases/` / `docs/architecture.md` gate strictly on unit/`INT-SWE-01..08`, `SYS.5`, and `verification_summary.md`.
  3. *Honest Documentation & V5.0.0 Reclassification Audit (`TST-PRO-112.3`, tools/verify_requirement_governance.py)*:
  • Verify `docs/architecture.md` (§6.2, §6.4), `docs/system/sys_integration_tests.md`, `docs/releases/V5.0.0/` (`verification_summary.md`, `system_integration_results.json`), and living docs explicitly state simulator-only scope, `INT-SWE-01..08` + `AppSmokeAndroidTest.kt` + manual `SYS.5` boundaries, and pass `verify_requirement_governance.py` with exit 0.

---

### TST-PRO-113: **Measured NFR Verification, Manual Protocol & Evidence Ingestion Suite (ATT-8158)** [Verified]
- **Requirement(s)**: `REQ-PRO-113`
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Integration / Review
- **Component**: `app/src/test/java/com/atrainingtracker/trainingtracker/build/MeasuredNfrBenchmarkTest.kt`, `app/src/test/java/com/atrainingtracker/trainingtracker/build/NonFunctionalRequirementsContractTest.kt`, `docs/engineering/test_evidence/nfr_manual_protocol_template.md`, `tools/generate_test_evidence.py`, `tools/test_generate_test_evidence.py`, `tools/verify_requirement_governance.py`
- **Jira**: `ATT-8158`
- **Test Description**:
  1. *Measured JVM/Robolectric NFR Benchmark Execution (`TST-PRO-113.1`, MeasuredNfrBenchmarkTest.kt)*:
  • Verify that the 11 measurable NFR benchmarks (`TST-NFR-001, 002, 003, 005, 007, 008, 009, 010, 011, 013, 014`) run warmup + measured passes, assert documented budgets, and emit structured JSON metrics to `app/build/reports/nfr/nfr_measurements.json`.
  2. *Manual Protocol Classification & Anti-False-Green Guard (`TST-PRO-113.2`, NonFunctionalRequirementsContractTest.kt)*:
  • Verify that `NonFunctionalRequirementsContractTest.kt` checks authentic benchmark/evidence artifacts and enforces `On-Device Manual` + non-`[Verified]` status for unattested hardware NFRs (`004, 006, 012, 015, 016, 017, 018`) instead of raw doc-string presence.
  3. *Test Evidence Ingestion & Governance Parity (`TST-PRO-113.3`, tools/test_generate_test_evidence.py & verify_requirement_governance.py)*:
  • Verify that `tools/generate_test_evidence.py` ingests `app/build/reports/nfr/nfr_measurements.json` and manual protocol status into `docs/engineering/test_evidence/<target>.{md,json}`, and `verify_requirement_governance.py` passes with exit 0.
