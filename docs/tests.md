# Project Verification & Testing Plan

This document defines the verification procedures for all project requirements. This project follows a **Test-Driven Development (TDD)** approach, where tests are defined before implementation.

## 1. Automated Unit Tests (SWE.4)

*These tests verify the core logic of individual software units without requiring a physical device.*

| Test ID | Component | Requirement ID(s) | Test Description | Status |
|:---|:---|:---|:---|:---|
| **TST-UNT-001** | `NumericalEncodingUtils` | `REQ-DAT-004` | Verify that encoding and decoding LatLng/Double lists result in zero data loss. | Verified |
| **TST-UNT-002** | `TimeFormatter` | `REQ-FIL-003`, `REQ-UI-002` | Verify Removal of leading zeros and correct localized string generation for s, m:s, and h:m:s formats. | Verified |
| **TST-UNT-003** | `EquipmentDiscovery` | `REQ-TRK-004`, `REQ-SET-003` | Verify sport-type resolution logic based on varied active sensor ID sets and speed. | Verified |
| **TST-UNT-004** | `MovingAverageFilter` | `REQ-FIL-001` | Verify correct averaging of a sequence of numbers (Numbered and Timed variants). | Proposed |
| **TST-UNT-005** | `ExponentialSmoothing` | `REQ-FIL-002` | Verify the recursive smoothing formula: `y[k+1] = α m[k] + (1-α) y[k]`. | Proposed |
| **TST-UNT-006** | `SportTypeMapping` | `REQ-EXP-006` | Verify that `SportTypeDatabaseManager` correctly handles a `null` Strava mapping as a persistent state. | Proposed |
| **TST-UNT-007** | `EditWorkoutViewModel` | `REQ-EXP-006` | Verify that selecting a sport type with a `null` Strava mapping automatically sets `uploadToStrava` to `0`. | Proposed |
| **TST-UNT-008** | `Accumulator` | `REQ-PRO-003` | Verify that hardware deltas received during a pause are discarded, while deltas received while active are accumulated correctly. | Verified |
| **TST-UNT-009** | `WorkoutDataMapper` | `REQ-UI-011` | Verify that the extrema rows only include the "Big 6" performance metrics: HR, Speed/Pace, Cadence, Power, Altitude, and Temperature. | Verified |
| **TST-UNT-010** | `MappablePath` | `REQ-MAP-007` | Verify that coordinate projection (latLngs) is lazy and only occurs once. | Proposed |
| **TST-UNT-011** | `TrackingViewsDb` | `REQ-PRO-005` | 1. Simulated fresh install (Wipe data): Verify schema contains ShowElevationProfile. 2. Simulated upgrade (v9->v10): Verify ShowElevationProfile is added. | Verified |

## 2. Regression & Manual Verification (SWE.5 / SWE.6)

These procedures ensure high-level system integrity and UI consistency.

| Test ID | Category | Requirement ID(s) | Procedure | Expected Result |
|:---|:---|:---|:---|:---|
| **TST-REG-001** | **Tracking** | `REQ-TRK-001`, `REQ-TRK-003`, `REQ-TRK-007`, `REQ-MAP-003` | Start workout, background app for 10 min, check bearing jitter. | Persistent 1Hz sampling, active notification, filtered map camera. |
| **TST-REG-002** | **Branding** | `REQ-EXT-001`, `REQ-EXT-002`, `REQ-MAP-005` | Navigate to Strava Segments list and Detail map. | Correct orange/white logos visible in headers and context rows. |
| **TST-REG-003** | **Cloud** | `REQ-EXP-001`, `REQ-EXP-002` | Complete workout with Strava enabled. | File export success UI and successful upload to third-party API. |
| **TST-REG-004** | **Pause Logic** | `REQ-PRO-003` | Connect hardware distance sensor. Start workout. Record distance. **Pause**. Continue movement (e.g., spin wheel). **Resume**. Record more distance. Repeat for all relevant protocols (ANT+/BTLE). | Total distance reflects only active movement. No distance increase or 'jump' is recorded during the pause phase. |
| **TST-INT-002** | **UI/Logic** | `REQ-EXP-006` | In Sport Type Editor, select "- No upload -". Then create/edit workout with this type. | Strava checkbox is unchecked and disabled in workout editor. |
| **TST-INT-003** | **Mapping** | `REQ-EXP-007` | In Sport Type Editor, select a new Strava type (e.g., Gravel Ride). Save and reopen. | The new type is persisted and correctly displayed in the editor and summary views. |
| **TST-INT-004** | **UI/Logic** | `REQ-UI-009` | In Tracking Tab Configuration, toggle "Show Elevation Profile". | Elevation profile is displayed below the map on the tracking screen. |
| **TST-INT-005** | **UI Layout** | `REQ-UI-010` | Open the Tab Configuration on a narrow device or split-screen. | Checkboxes wrap into multiple rows instead of overlapping. Labels remain fully visible. |
| **TST-INT-006** | **UI Layout** | `REQ-UI-010` | Audit the checkbox sequence in the tab configuration header. | Checkboxes appear in the exact order: Map, Elevation Profile, Live Segments, Lap Button. |
| **TST-REG-005** | **Map Context** | `REQ-MAP-008` | Open detail map. Verify active item is primary. Verify background items of same sport are muted (0.3). Verify background items of different sport are highly muted (0.1). | Full tiered spatial context is maintained. |
| **TST-REG-007** | **Pause Time Sync** | `REQ-TRK-009`, `REQ-UI-038` | 1. Start workout. 2. Pause after 10s. 3. Wait 20s while paused. 4. Verify Active Time is 10s and Total Time is ~30s in the UI and DB. | Verified |
| **TST-UI-001** | **UI Design** | `REQ-UI-011` | Inspect the workout summary extrema table on various device themes. | The table has distinct headers, clear alignment, and high-quality typography matching the Material 3 standards. | Verified |
| **TST-UI-011** | **Formatting** | `REQ-UI-011` | Verify that units in data rows are surrounded by square brackets (e.g., `[km/h]`). | Verified |
| **TST-UI-012** | **Hierarchy** | `REQ-UI-011` | Verify that the Sensor Name and the Numeric Values share the same visual weight, while the unit is micro-scaled. | Verified |
| **TST-UI-013** | **Alignment** | `REQ-UI-011` | Verify that in all data rows, the icon, sensor label, and numeric values are vertically aligned to their bottom edge. | Verified |
| **TST-UI-014** | **Typography** | `REQ-UI-011` | Verify that units use a micro font size (e.g., 8-9.sp) and a lighter color (reduced alpha) than the sensor name. | Verified |
| **TST-UI-015** | **Notation** | `REQ-UI-011` | Verify that the "Average" header column displays the mathematical symbol (Ø) instead of the word "average". | Verified |
| **TST-UI-016** | **Proportion** | `REQ-UI-011` | Verify that the icon height visually matches the height of the sensor name (bodyLarge line height). | Verified |
| **TST-UI-017** | **Alignment** | `REQ-UI-011` | Verify that the icon, sensor label, and bracketed unit are perfectly aligned to the same bottom baseline in the Compose Preview. | Verified |
| **TST-UI-018** | **Baseline Precision** | `REQ-UI-011` | Verify that the bottom edge of the icon and the bottom of the text characters are visually perfectly aligned. | Verified |
| **TST-UI-019** | **Hierarchy** | `REQ-UI-011` | Verify context-aware bolding: HR/Power (Avg+Max), Speed/Pace/Cadence (Avg), Alt/Temp (Min+Max). | Verified |
| **TST-UI-020** | **Contrast** | `REQ-UI-011` | Verify that the "-" placeholder is visually lighter (alpha 0.3) than numeric values. | Verified |
| **TST-UI-021** | **Redundancy** | `REQ-UI-012` | Verify that Min/Max altitude no longer appear in the Workout Details row, only Ascent and Descent. | Verified |
| **TST-UI-026** | **Consistency** | `REQ-UI-013` | In the Workout Summary card, verify that the altitude labels at the start/end of the chart axes match the Min/Max values shown in the Extrema table. | Verified |
| **TST-UI-027** | **Harmonization** | `REQ-UI-014` | Start tracking; verify the Speed Average in the extrema table exactly matches the value derived from Total Distance / Active Time (e.g., if distance is 100m and active time is 100s, avg speed must be 1.0 m/s). | Verified |
| **TST-UI-028** | **Localization** | `REQ-UI-018` | Inspect Workout Summary in various languages. Verify Ascent/Descent uses relative terminology (e.g., "Höhenmeter", "Elevation") and Min/Max uses absolute terminology (e.g., "Höhe", "Altitude"). | Correct linguistic distinction across all supported languages. |
| **TST-UI-029** | **Session Isolation** | `REQ-UI-009` | 1. Start a workout, move, stop workout. 2. Immediately start a new workout. 3. Verify Map and Elevation Profile are empty at start. | Verified |
| **TST-UI-030** | **Metric Consistency**| `REQ-UI-020` | Audit Workout and Segment items. Verify \"Gain\" uses `ic_ascent` in both. Verify \"Elevation\" label in Workout is bottom-aligned with values. Verify all icons/text share a consistent baseline. | Verified |
| **TST-UI-031** | **Layout Hierarchy** | `REQ-UI-021` | Open Workout Summary. Inspect the Elevation section header (left column). | Verified |
| **TST-UI-034** | **Identity Branding UI** | `REQ-UI-024` | 1. Open Route List. 2. Inspect Strava route: verify logo is below name and no \"Source: Strava\" text exists. 3. Inspect Local route: verify \"Source: Local\" text is below name. 4. Verify no branding logo in metrics row. | Verified |
| **TST-LOG-001** | **Logic** | `REQ-UI-011` | Verify that all rows, including "Pace", follow strict mathematical ordering (numerical minimum in Min column, numerical maximum in Max column). | Verified |
| **TST-LOG-002** | **Logic** | `REQ-UI-011` | Verify that for a running activity, both Speed and Pace rows are generated from the single Speed data source. | Verified |
| **TST-MAN-001** | **General** | All other `REQ` | Ad-hoc functional testing on a physical device. | Feature performs according to the rationale defined in requirements. |

| **TST-STP-001** | **Localization** | `REQ-STP-001` | Review generated markdown files in `docs/store_presence/`. Verify that Title, Short Description ("The serious athlete's tracking cockpit with ANT+, BLE, Strava & live segments."), and Full Description are correctly translated and adhere to Google Play character limits (Title: 30, Short: 80). | Verified |

| **TST-UI-035** | **Compact Metric Row** | `REQ-UI-025` | 1. Open Route List. 2. Inspect gap between identity row and metrics row. | Verified |
| **TST-UI-036** | **Branding Audit** | `REQ-UI-026` | 1. Open Segment List. 2. Verify top logo height (e.g., 24dp). 3. Inspect individual Segment Items (if enabled). | Verified |
| **TST-UI-038** | **Detailed Metric Scale** | `REQ-UI-028` | 1. Open Periods screen. 2. Inspect sub-sport rows and \"Longest Workout\" highlight. Verify icons are 14dp and text is bodySmall. | Verified |
| **TST-UI-039** | **Pause Movement Isolation** | `REQ-PRO-004` | 1. Start workout. 2. Move to create track. 3. **Pause**. 4. Move 50m. 5. Observe map. | Verified |
| **TST-UI-041** | **Period Peak Markers** | `REQ-UI-033`, `REQ-UI-035`, `REQ-UI-036`, `REQ-UI-037` | 1. Open a \"Yearly\" summary. 2. Enter map. 3. Verify Max Alt and Max Dist markers are visible with reduced alpha and size. 4. Tap a marker and verify workout \"Peek\" opens. | Verified |
| **TST-UI-043** | **Device Management Modernization** | `REQ-UI-041`, `REQ-UI-043` | 1. Navigate to Pairing screen. 2. Verify Tabbed layout. 3. Interact with Pair switches. 4. Click device to open Edit Dialog. 5. Modify a setting (e.g. name). | Verified |
| **TST-UI-045** | **Reactive Map Updates** | `REQ-MAP-009` | Open Route Detail Map. Toggle the \"Active\" switch in the header. | The route polyline on the map SHALL immediately change its color and width without requiring any camera movement (zoom/pan). | Verified |
| **TST-UI-046** | **Stats Summary Block Visual Alignment** | `REQ-UI-046` | Inspect the `StatsSummaryBlock` in the Equipment or SportType details screen. | The block SHALL use the unified `MetricItem` component. Headings (Workouts, Distance, Time, Ascent) SHALL be positioned above their respective values. Visual hierarchy (icons, typography) SHALL be consistent with Workout and Segment list items. | Verified |
| **TST-UI-047** | **Live Segment Sheet Swipe Limit** | `REQ-UI-047` | 1. Start tracking. 2. When a segment is active, swipe up on the bottom sheet. | The sheet SHALL stop expanding once the header and elevation profile are fully visible. It SHALL NOT cover the entire screen or reach the status bar. | Verified |
| **TST-UI-048** | **Sensor Status Header Visual Audit** | `REQ-UI-048` | Inspect the `SensorStatus` row at the top of the tracking screen. | The row SHALL contain only sensor icons. No text labels (e.g., "HR", "Speed") SHALL be visible below the icons. Icons SHALL use a 22dp scale to remain legible without dominating the cockpit. | Verified |
| **TST-UI-049** | **Sensor Source Dialog Verification** | `REQ-UI-049` | 1. Start a session. 2. Tap any sensor icon (active or inactive) in the header. | A dialog SHALL appear. For active sensors, it shows real-time telemetry. For inactive sensors, it identifies the paired source device(s) or internal fallbacks. | Verified |
| **TST-UI-051** | **Grade Legend Visibility** | `REQ-MAP-010` | 1. Open any Elevation Profile (e.g., in Aftermath). 2. Tap the info icon. | The grade color legend SHALL appear. Tap again to hide. | Verified |
| **TST-UI-050** | **Sensor Identity Logic** | `REQ-UI-050` | Open `SensorSourceDialog`, `EditDeviceDialog`, and `DeviceItem`. | Both SHALL feature identical Row 1/2 blocks. `EditDeviceDialog` SHALL include Row 3 (Manufacturer). The LED dot and the Battery icon SHALL be perfectly aligned on their vertical centers. | Verified |

| **TST-FUSION-001** | **Dynamic Accuracy** | `REQ-FIL-007`, `REQ-FIL-008` | Audit live track during sharp turns and rapid acceleration. Verify no "overshoot" or "lag" in position relative to visual map markers. | Backlog |
| **TST-FUSION-002** | **Dead Reckoning** | `REQ-FIL-009` | 1. Enter tunnel (GPS loss). 2. Continue moving. 3. Verify map track continues along road based on speed sensor. | Backlog |
| **TST-FUSION-003** | **Auto-Calibration Audit** | `REQ-FIL-010` | Compare EKF Wheel Scale Factor against a manually measured circumference after a 10km ride. | Backlog |
| **TST-FUSION-004** | **Stationary Audit** | `REQ-NFR-002` | Stop physically for 2 minutes. Verify location dot remains perfectly fixed with zero "webbing" artifacts on the map. | Backlog |

## 3. Structural & Compliance Checks

| Test ID | Focus | Requirement ID(s) | Method |
|:---|:---|:---|:---|
| **TST-STR-001** | **Stability** | `REQ-CON-005`, `REQ-TRK-005`, `REQ-DAT-005`, `REQ-UI-008` | Static analysis and architectural audit. |
| **TST-STR-002** | **Thread Safety** | `REQ-PRO-002` | Static analysis of `synchronized` blocks to ensure no nested locks or I/O within locks. |
| **TST-STR-003** | **Data Integrity** | `REQ-UI-009` | 1. Static analysis of `onUpgrade` (v9->v10).<br>2. Static analysis of `onCreate` and `addDefaultTab`. | 1. `onUpgrade` must set `SHOW_ELEVATION_PROFILE = SHOW_MAP`.<br>2. `addDefaultTab` must initialize `SHOW_ELEVATION_PROFILE` using the value provided for `showMap`. | Verified |
| **TST-STR-004** | **Consistency** | `REQ-TRK-008` | Static analysis of `TrackerService.java`. | `IMPORTANT_SENSOR_TYPES` must strictly contain only the "Big 6" + Temperature metrics. | Verified |
| **TST-STR-005** | **Map DSL** | `REQ-MAP-006` | Static audit of `ATrainingTrackerMap` callers. | All data layers added via DSL block, not direct parameters. | Verified |
| **TST-STR-006** | **Metric Consistency**| `REQ-UI-015` | Visual audit of Workout, Route, and Segment screens. | All use `MetricItem` for data rows. | Verified |
| **TST-STR-007** | **Formatter Sync** | `REQ-UI-016` | Static audit of `LocalMetricFormatter` usages. | No direct `new DistanceFormatter()` etc. in UI layer. | Verified |
| **TST-STR-008** | **List Foundation** | `REQ-UI-017` | Audit of `MappableListItem` usages. | All list cards share same foundation and padding. | Verified |
| **TST-UI-052** | **Source Header Color Audit** | `REQ-UI-051` | Open `SensorSourceDialog` for a sensor with active and standby sources. | Header text colors SHALL match: Source=Dark Green, Backup=Light Green, Not connected=Grey. | Verified |
| **TST-UI-053** | **Longest Workout Navigation** | `REQ-UI-052` | 1. Open a Period Summary with multiple workouts. 2. Tap the "Longest Workout" section. | The app SHALL navigate to the list and automatically scroll to the targeted workout. | Verified |
| **TST-UI-054** | **Location Audit Metric** | `REQ-UI-053` | 1. Start a tracking session with a location provider active. 2. Tap the location icon in the `SensorStatus` header. | The `SensorSourceDialog` SHALL show "Accuracy" and its current value (e.g. "5 m") instead of "Longitude". | Verified |
| **TST-UI-055** | **Multi-Track Visibility Audit** | `REQ-MAP-011`, `REQ-MAP-012` | 1. Open the Workout Details screen for a recorded activity. 2. Tap the "Layers" FAB. 3. Toggle different track types (GPS, Fused, Network). | The map SHALL update polylines in real-time to show/hide the selected tracks. The selection menu includes a color legend. | Verified |
| **TST-UI-056** | **Map Preference Persistence** | `REQ-MAP-013` | 1. Toggle technical tracks in Workout Details. 2. Close and reopen the workout. | The previously selected track layers SHALL remain visible. | Verified |
| **TST-UI-057** | **Period FAB Order** | `REQ-MAP-014` | 1. Open the Period Map. 2. Verify the vertical order of FABs. | The Share button SHALL be the topmost item in the FAB stack. | Verified |
| **TST-UI-058** | **High-Fidelity Period Tracks** | `REQ-MAP-015` | 1. Compare a track in a Period Map overview with the same track in Workout Details. | Both tracks SHALL show identical fine-granular detail. | Verified |
| **TST-UI-059** | **Alpha Consistency Audit** | `REQ-UI-054` | Static analysis of `WorkoutExtrema.kt` and `MetricItem.kt`. | No hardcoded `copy(alpha = ...)` literals; all use `TTAlpha` constants. | Verified |
| **TST-UI-060** | **Primary Metric Hierarchy** | `REQ-UI-055` | Open Workout Details or Segment Details. | The primary value (e.g. Distance) SHALL be rendered in the `primary` theme color. | **Deactivated** |
| **TST-UI-061** | **Theme Audit** | `REQ-UI-056` | Project-wide grep for `Color(0xFF...)` and `copy(alpha = ...)`. | All UI-related colors and alphas MUST use semantic tokens. | Proposed |
| **TST-EXT-001** | **Strava Custom Tabs** | `REQ-EXT-004` | 1. Click "Connect to Strava". 2. Verify that a Custom Tab opens. | The authorization page SHALL open in a Chrome Custom Tab. | Proposed |
| **TST-EXT-002** | **Strava OAuth Flow** | `REQ-EXT-005` | 1. Complete Strava authorization. 2. Observe the loading state. | The app SHALL show a non-blocking loading indicator and correctly store the resulting token. | Proposed |
| **TST-UI-062** | **Protocol-Aware Menu** | `REQ-UI-044` | 1. Open the Bluetooth sensor list. 2. Check the options menu. 3. Open the ANT+ sensor list. 4. Check the options menu. | The "Check ANT+ Installation" option SHALL NOT be visible in the Bluetooth view, but SHALL be visible in the ANT+ view. | Verified |
| **TST-UI-063** | **Global Icon Clipping** | `REQ-UI-057` | 1. Open the app in Dark Mode. 2. Inspect sensor and protocol icons. | All icons SHALL appear with subtle 4dp rounded corners, and square white corner artifacts (on PNG assets) SHALL be masked. | Verified |
| **TST-UI-066** | **Elevation Profile Visibility** | `REQ-UI-058` | 1. Open the Frequent Path Heatmap. 2. Inspect the bottom of the screen. | The elevation profile SHALL NOT be visible. | Proposed |
| **TST-SET-001** | **Route Learning Engine** | `REQ-SET-006`, `007`, `008` | 1. Track a route. 2. Name it \"Park Loop\" and set sport to \"Running\". 3. Track the same route again. | 1. The second workout SHALL automatically be named \"Park Loop #2\". 2. The sport SHALL automatically be set to \"Running\". 3. Changing the name to \"Morning Loop\" SHALL update future suggestions to \"Morning Loop\". | Verified |
| **TST-SET-002** | **Frequent Paths Heatmap** | `REQ-SET-009` | 1. Navigate to \"Frequent Paths\" drawer. 2. Select a learned route. | 1. A map SHALL open showing a heatmap of all associated workouts. 2. Start, Stop, and Apex markers SHALL be visible as clear pins. | Verified |
| **TST-SET-003** | **Cluster Tuning** | `REQ-SET-010`, `011` | 1. Navigate to \"Frequent Paths\". 2. Open Tuning menu. 3. Adjust the master slider. 4. Optionally toggle detailed controls. 5. Tap \"Recalculate\". | 1. The master slider SHALL adjust all three tolerances proportionally. 2. The Route Cluster database SHALL be wiped. 3. A background migration SHALL rebuild the clusters with the new parameters. | Verified |
| **TST-SET-004** | **Route Cluster Item Map** | `REQ-SET-012` | 1. Navigate to \"Frequent Paths\". 2. Inspect list items. | 1. Each item SHALL display a small map. 2. Start (Green pin with start icon), End (Red pin with stop icon), and Apex (Blue pin with distance icon) SHALL be visible on the map. | Verified |
| **TST-SET-005** | **Name-Aware Clustering** | `REQ-SET-013` | 1. Identify two workouts with same name but high spatial drift. 2. Trigger recalculation. | The system SHALL group them into the same cluster by applying the name-match bonus to the similarity score. | Verified |
| **TST-SET-006** | **Auto-Dismiss Tuning** | `REQ-SET-014` | 1. Open Tuning screen. 2. Tap \"Recalculate All Clusters\". 3. Wait for completion. | The UI SHALL automatically navigate back to the Frequent Paths list view upon completion. | Verified |
| **TST-SET-007** | **Unique Cluster Naming** | `REQ-SET-015` | 1. Create a cluster named \"Home\". 2. Create a spatially distinct route and also name it \"Home\". | The second cluster SHALL be automatically named \"Home var 2\". | Verified |
| **TST-SET-008** | **Edit Cluster Identity** | `REQ-SET-016` | 1. Select a route cluster. 2. Tap Edit Icon in TopAppBar. 3. Enter new name and select a different sport. 4. Save. | 1. The name and sport SHALL be updated in the database. 2. The UI SHALL immediately reflect the new name and sport icon in the list and details view. | Verified |
| **TST-SET-009** | **Manual Workout Reassignment** | `REQ-SET-017` | 1. Open a Route Heatmap. 2. Tap a specific workout polyline. 3. Select a different cluster from the candidate list. 4. Save. | 1. The workout SHALL be moved to the target cluster. 2. Centroids for both clusters SHALL be recalculated. 3. The UI SHALL refresh to show the workout in its new home. | Verified |
| **TST-SET-010** | **Edit Workout Suggestions** | `REQ-SET-018` | 1. Edit a workout. 2. Tap the \"My Locations\" icon in the Name field. 3. Select a suggested cluster. | 1. The workout name SHALL update to the cluster's name. 2. The sport SHALL update to the cluster's sport. 3. Saving SHALL persist the cluster association. | Verified |
| **TST-SET-011** | **Manual Cluster Creation** | `REQ-SET-019` | 1. Navigate to \"Frequent Paths\". 2. Tap \"+\" FAB. 3. Fill in name and distance. 4. Tap map to set 3 points. 5. Save. | 1. A new cluster SHALL be created in the database with 0 hit count. 2. The cluster SHALL be visible in the list. | Verified |
| **TST-SET-012** | **Workout Peek & Move** | `REQ-SET-020` | 1. Open a Route Heatmap. 2. Tap a specific workout track. 3. Observe the Bottom Sheet peek. 4. Tap the \"Move\" FAB in the peek. | 1. The workout details SHALL be visible in the sheet. 2. The \"Move Workout\" dialog SHALL appear upon tapping the FAB. | Verified |
| **TST-SET-013** | **Manual Fingerprint Edit** | `REQ-SET-021` | 1. Open a Route Heatmap. 2. Tap a signature marker (Start/End/Apex). 3. Select \"Edit Position\". 4. Tap map to set new position. 5. Save. | 1. The cluster centroid SHALL be updated in the database. 2. Future matching SHALL use the new coordinates. | Verified |
| **TST-SET-014** | **Workout Markers in Cluster** | `REQ-SET-022` | 1. Open a Route Heatmap. 2. Observe the distribution of markers for all members. 3. Tap a specific workout track OR any marker. 4. Inspect markers in the peek. | 1. Markers for ALL member workouts SHALL be visible with subtle transparency. 2. Tapping a marker SHALL correctly trigger the detailed peek for that workout. | Verified |
| **TST-SET-015** | **Imperial Units in Clusters** | `REQ-SET-023` | 1. Switch to Imperial. 2. Add manual cluster (miles). 3. View tuning details (mile). | 1. Manual distance input SHALL accept miles and store as meters. 2. Tuning tolerances SHALL be displayed in miles with high precision. | Verified |
| **TST-SET-016** | **Cluster Equipment Determination** | `REQ-SET-024` | 1. Open a Route Cluster. 2. Verify the associated equipment list (→). | 1. The list of equipment linked to the cluster's primary sport SHALL be visible. | Verified |

## 4. Release Verification Workflow

Whenever a file is modified, the following workflow is triggered:

1.  **Change Detection**: The agent identifies which files were touched.
2.  **Requirement Mapping**: The agent cross-references the files with `docs/requirements.md` (Implementation Column) to identify affected features.
3.  **Test Selection**: The agent selects the relevant **Verification ID** from the requirements table.
4.  **Co-Execution**: The user and agent perform the verification. The agent updates the `Status` column in `docs/requirements.md` upon success.
