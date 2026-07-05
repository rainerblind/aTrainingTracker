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
| **TST-UI-048** | **Sensor Status Header Visual Audit** | `REQ-UI-048` | Inspect the `SensorStatus` row at the top of the tracking screen. | The row SHALL contain only sensor icons. No text labels (e.g., "HR", "Speed") SHALL be visible below the icons. Icons SHALL use appropriate technical scaling for high-density display. | Verified |
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

## 4. Release Verification Workflow

Whenever a file is modified, the following workflow is triggered:

1.  **Change Detection**: The agent identifies which files were touched.
2.  **Requirement Mapping**: The agent cross-references the files with `docs/requirements.md` (Implementation Column) to identify affected features.
3.  **Test Selection**: The agent selects the relevant **Verification ID** from the requirements table.
4.  **Co-Execution**: The user and agent perform the verification. The agent updates the `Status` column in `docs/requirements.md` upon success.
