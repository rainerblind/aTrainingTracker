# Project Requirements: aTrainingTracker

This document tracks all functional and non-functional requirements of the project. Every code change must be traceable to a requirement defined here.

## 1. Core Connectivity & Hardware Integration

| ID | Description | Rationale | Implementation File(s) | Verification ID | Status |
|:---|:---|:---|:---|:---|:---|
| **REQ-CON-001** | Support multiple ANT+ sensor profiles (HR, Speed, Cadence, Power, Env). | Provide comprehensive data for diverse athletic activities. | `BANALService.java`, `ant_plus/` | `TST-MAN-001` | Verified |
| **REQ-CON-002** | Support Bluetooth LE (BLE) sensor profiles (HR, Speed, Cadence, Power). | Ensure compatibility with modern fitness hardware. | `BANALService.java`, `bluetooth_le/` | `TST-MAN-001` | Verified |
| **REQ-CON-003** | Implement "Best Sensor" selection logic using priority lists. | Maximize data quality by preferring hardware over GPS. | `MySensorManager.java` | `TST-MAN-001` | Verified |
| **REQ-CON-004** | Provide asynchronous background searching for devices. | Allow users to discover sensors without interrupting app use. | `DeviceManager.java` | `TST-MAN-001` | Verified |
| **REQ-CON-005** | All BLE characteristic reads must be null-safe. | Prevent application crashes when communicating with hardware. | `MyBTLEDevice.java` | `TST-STR-001` | Verified |
| **REQ-CON-006** | Support barometric pressure sensors for altitude. | Provide precise elevation data where GPS is insufficient. | `AltitudeFromPressureDevice.java` | `TST-MAN-001` | Verified |
| **REQ-CON-007** | Notify user about low battery levels in sensors. | Prevent data loss during workouts due to sensor failure. | `BatteryStatusHelper.java` | `TST-MAN-001` | Verified |
| **REQ-CON-008** | Detect missing ANT+ services and link to Play Store. | Ensure users can easily set up required hardware drivers. | `BANALService.java` | `TST-MAN-001` | Verified |
| **REQ-CON-009** | Support per-device calibration (wheel circumference). | Ensure distance and speed accuracy across setups. | `EditSimpleBikeDeviceFragment.kt`| `TST-MAN-001` | Verified |

## 2. Sensor Data Processing & Filtering

| ID | Description | Rationale | Implementation File(s) | Verification ID | Status |
|:---|:---|:---|:---|:---|:---|
| **REQ-FIL-001** | Flexible filtering (Moving Avg, Exponential Smoothing). | Allow for smooth visualization of noisy sensor data. | `FilterManager.java` | `TST-UNT-004` | Verified |
| **REQ-FIL-002** | Configurable smoothing factors (α) for Power. | Balance responsiveness vs. stability. | `ExponentialSmoothingFilter.java` | `TST-UNT-005` | Verified |
| **REQ-FIL-003** | Calculate derived metrics (VAM, slope, pace). | Provide advanced metrics for climbing and analysis. | `VerticalSpeedAndSlopeDevice.java` | `TST-UNT-002` | Verified |
| **REQ-FIL-004** | Support "Altitude Correction" via known locations. | Eliminate barometric drift and ensure consistency. | `KnownLocationsDatabaseManager.java` | `TST-MAN-001` | Verified |
| **REQ-FIL-005** | Implement "Proxy Sensors" and "Accumulator Sensors". | Extensible architecture for complex multi-sensor data. | `ProxySensor.java` | `TST-STR-001` | Verified |
| **REQ-FIL-006** | Geographical rolling averages for start altitude. | Filter out momentary GPS or barometric jitter. | `WorkoutSamplesDatabaseManager.java`| `TST-STR-001` | Verified |

## 3. Tracking Logic & Session Management

| ID | Description | Rationale | Implementation File(s) | Verification ID | Status |
|:---|:---|:---|:---|:---|:---|
| **REQ-TRK-001** | Support Start, Stop, Pause with Service persistence. | Ensure data integrity in the background. | `TrackerService.java` | `TST-REG-001` | Verified |
| **REQ-TRK-002** | Implement lap recording with immediate summaries. | Enable performance analysis for workout segments. | `LapsDatabaseManager.java` | `TST-MAN-001` | Verified |
| **REQ-TRK-003** | Use WakeLocks during active tracking. | Prevent CPU sleep and data gaps. | `TrackerService.java` | `TST-REG-001` | Verified |
| **REQ-TRK-004** | Automatically guess Sport Type and Equipment. | Minimize user effort via automated categorization. | `EquipmentDiscoveryManager.kt` | `TST-UNT-003` | Verified |
| **REQ-TRK-005** | Implement session recovery ("Resume from Crash"). | Ensure data integrity against system pressure. | `TrackerService.java` | `TST-STR-001` | Verified |
| **REQ-TRK-006** | Support sport-specific search triggers. | Sensors ready when user changes activity. | `TrainingApplication.java` | `TST-MAN-001` | Verified |
| **REQ-TRK-007** | Deterministic 1Hz sampling rate during recording. | Consistent time-base for analysis and export. | `TrackerService.java` | `TST-REG-001` | Verified |

## 4. Advanced Metrics & User Profiles

| ID | Description | Rationale | Implementation File(s) | Verification ID | Status |
|:---|:---|:---|:---|:---|:---|
| **REQ-SET-001** | HR and Power training zones (5-zone model). | Allow athletes to train within specific intensities. | `ZonesSettingsActivity.kt` | `TST-MAN-001` | Verified |
| **REQ-SET-002** | Equipment management (Bikes/Shoes) with odometer. | Help users monitor gear wear and tear. | `EquipmentRepository.kt` | `TST-MAN-001` | Verified |
| **REQ-SET-003** | Configurable speed thresholds for classification. | Tailor automation to user's specific pace profile. | `TrainingApplication.java` | `TST-UNT-003` | Verified |
| **REQ-SET-004** | "Fancy Name" schemes using location and detours. | Provide descriptive workout names automatically. | `WorkoutSummariesDatabaseManager.java`| `TST-STR-001` | Verified |
| **REQ-SET-005** | Support dual-unit systems (Metric and Imperial). | Accommodate global user base. | `MyUnits.java`, `MyHelper.java` | `TST-MAN-001` | Verified |

## 5. Data Storage & Post-Processing

| ID | Description | Rationale | Implementation File(s) | Verification ID | Status |
|:---|:---|:---|:---|:---|:---|
| **REQ-DAT-001** | Store per-second sensor samples and extrema. | Granular data and peak performance insights. | `WorkoutSamplesDatabaseManager.java`| `TST-STR-001` | Verified |
| **REQ-DAT-002** | Support automatic deletion of old workouts. | Manage device storage automatically. | `WorkoutDeletionHelper.java` | `TST-MAN-001` | Verified |
| **REQ-DAT-003** | Provide period-based statistics (W/M/Y). | Enable long-term training volume analysis. | `PeriodsViewModel.kt` | `TST-MAN-001` | Verified |
| **REQ-DAT-004** | Encoded polylines and streams for storage/map. | Optimize database size and UI performance. | `NumericalEncodingUtils.kt` | `TST-UNT-001` | Verified |
| **REQ-DAT-005** | Dynamic database schema evolution. | Support future hardware without migrations. | `TrackerService.java` | `TST-STR-001` | Verified |

## 6. Map & Geographical Visualization

| ID | Description | Rationale | Implementation File(s) | Verification ID | Status |
|:---|:---|:---|:---|:---|:---|
| **REQ-MAP-001** | Interactive workout tracks with "Roughness". | Smooth map interaction for long activities. | `ATrainingTrackerMap.kt` | `TST-REG-001` | Verified |
| **REQ-MAP-002** | Training heatmaps (Cyan -> Indigo gradient). | Visualize training density professionally. | `ATrainingTrackerMap.kt` | `TST-MAN-001` | Verified |
| **REQ-MAP-003** | Low-pass filtered map camera bearing. | Reduce rotation jitter from noisy GPS. | `ATrainingTrackerMap.kt` | `TST-REG-001` | Verified |
| **REQ-MAP-004** | Zoom depending on speed. | Wider context at speed, detail when slow. | `ATrainingTrackerMap.kt` | `TST-REG-001` | Verified |
| **REQ-MAP-005** | Strava Segments and Routes as map overlays. | Integrate community data directly on map. | `MapFragmentWithTrack.kt` | `TST-REG-002` | Verified |

## 7. Live Segment Tracking

| ID | Description | Rationale | Implementation File(s) | Verification ID | Status |
|:---|:---|:---|:---|:---|:---|
| **REQ-LIV-001** | Detect segments using "Gates" and cross-products. | Reliable entry/exit detection regardless of jitter. | `LiveSegmentsRepository.kt` | `TST-MAN-001` | Verified |
| **REQ-LIV-002** | Real-time progress on starred Strava segments. | Motivate users during live efforts. | `LiveSegmentsRepository.kt` | `TST-MAN-001` | Verified |
| **REQ-LIV-003** | Remaining distance/time vs PR. | Immediate feedback on performance. | `LiveSegmentsRepository.kt` | `TST-MAN-001` | Verified |
| **REQ-LIV-004** | Filter segments by bearing alignment (45°). | Eliminate false alerts on parallel roads. | `LiveSegmentsRepository.kt` | `TST-MAN-001` | Verified |

## 8. Export & Cloud Integration

| ID | Description | Rationale | Implementation File(s) | Verification ID | Status |
|:---|:---|:---|:---|:---|:---|
| **REQ-EXP-001** | Export to GPX, TCX, CSV, GC JSON. | Interoperability with professional analysis tools. | `writer/` | `TST-REG-003` | Verified |
| **REQ-EXP-002** | Automated uploads to Strava and Dropbox. | Streamline cloud synchronization. | `ExportManager.java` | `TST-REG-003` | Verified |
| **REQ-EXP-003** | Selective Upload (exclude specific data). | Provide users control over data privacy. | `StravaUploader.kt` | `TST-MAN-001` | Verified |
| **REQ-EXP-006** | Explicit "No upload" option for Strava mapping. | Allow users to opt-out of Strava sync for specific custom sport types via a dedicated "- No upload -" mapping option. | `SportTypeDatabaseManager.java`, `EditWorkoutViewModel.kt` | `TST-UNT-006`, `TST-UNT-007`, `TST-INT-002` | Verified |
| **REQ-EXP-007** | Support latest Strava sport types. | Ensure compatibility with modern Strava activity classification (e.g., Trail Run, Gravel Ride) for better social integration. | `arrays.xml`, `SportTypeDatabaseManager.java` | `TST-INT-003` | Verified |
| **REQ-EXP-004** | Automated email export with attachments. | Simple, reliable backup and sharing. | `ExportManager.java` | `TST-MAN-001` | Verified |
| **REQ-EXP-005** | Exponential backoff for Strava uploads. | Robust sync even with API issues. | `StravaUploader.kt` | `TST-STR-001` | Verified |

## 9. External API Compliance

| ID | Description | Rationale | Implementation File(s) | Verification ID | Status |
|:---|:---|:---|:---|:---|:---|
| **REQ-EXT-001** | "Powered by Strava" branding on all Strava data. | Comply with mandatory API guidelines. | `PoweredByStrava.kt` | `TST-REG-002` | Verified |
| **REQ-EXT-002** | Official Strava "Connect" authentication assets. | Adhere to partner brand requirements. | `StravaUploadFragment.kt` | `TST-REG-002` | Verified |
| **REQ-EXT-003** | Display logo contextually within lists/headers. | Maintain compliance without UI clutter. | `SegmentList.kt` | `TST-REG-002` | Verified |

## 10. User Interface & Quality Standards

| ID | Description | Rationale | Implementation File(s) | Verification ID | Status |
|:---|:---|:---|:---|:---|:---|
| **REQ-UI-001** | Clean white background (Material Surface). | Ensure professional visual consistency. | `Theme.kt` | `TST-MAN-001` | Verified |
| **REQ-UI-002** | Primary metrics in **boldface**. | Emphasize key training volume data. | `WorkoutSummary.kt` | `TST-MAN-001` | Verified |
| **REQ-UI-003** | Deeply customizable tracking cockpits. | Users build their perfect dashboard. | `ConfigTrackingTabsActivity.kt` | `TST-MAN-001` | Verified |
| **REQ-UI-004** | Scrollable bar graph for period volume. | Enhance historical navigation and trends. | `PeriodsTabsScreen.kt` | `TST-MAN-001` | Verified |
| **REQ-UI-005** | Handling of system bar insets (edge-to-edge). | Modern, immersive Android experience. | `StravaUploadFragment.kt` | `TST-MAN-001` | Verified |
| **REQ-UI-006** | Support multiple languages (EN, DE, ES, FR, IT, PT, NL, PL, JA). | Reach international audience of athletes. | `strings.xml`, `values-xx/` | `TST-MAN-001` | Verified |
| **REQ-UI-007** | Flexible sorting for lists (Date/Dist/Elev). | Allow users to organize and find data easily. | `WorkoutSummariesViewModel.kt` | `TST-MAN-001` | Verified |
| **REQ-UI-008** | Agents ask for clarification if instructions unclear. | Protect project quality and precision. | `project_protocol.md` | `TST-STR-001` | Verified |

## 11. Wearable Integration

| ID | Description | Rationale | Implementation File(s) | Verification ID | Status |
|:---|:---|:---|:---|:---|:---|
| **REQ-WEA-001** | Support Pebble smartwatches for live metrics. | Allow users to view data on their wrist. | `PebbleService.java` | `TST-MAN-001` | **Deactivated** |## 7. Process & Engineering Quality

| ID | Description | Rationale | Implementation File(s) | Verification ID | Status |
|:---|:---|:---|:---|:---|:---|
| **REQ-PRO-001** | Integrate Agile Workflow with Jira Cloud. | Ensure bidirectional traceability between tasks and code. | `project_protocol.md`, `SCRUM-103` | `TST-MAN-001` | Verified |
| **REQ-PRO-002** | Minimize lock contention and eliminate ANRs. | Ensure the app remains responsive during heavy I/O or sensor load. | `TrackerService.java`, `ExportManager.java` | `TST-STR-002` | Verified |

## 12. Privacy & Permissions

| ID | Description | Rationale | Implementation File(s) | Verification ID | Status |
|:---|:---|:---|:---|:---|:---|
| **REQ-PRI-001** | Transparent location/background permission info. | Comply with Android standards and trust. | `MainActivityWithNavigation.java`| `TST-MAN-001` | Verified |
| **REQ-PRI-002** | Accessible Privacy Policy linked to source. | Inform users about data handling. | `privacy.md` | `TST-MAN-001` | Verified |
