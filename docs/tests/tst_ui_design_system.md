# Living Test Specification: UI Components & Design System

**Domain**: `ui_design_system`  
**Prefixes**: `UI`, `UNT`  
**Scope**: Jetpack Compose design system, UI components, typography, dialogs, gestures, and layout contracts.  
**Total Tests**: 389

---

### TST-UNT-001: NumericalEncodingUtils [Verified]
- **Requirement(s)**: `REQ-DAT-004`
- **Component**: `NumericalEncodingUtils`
- **Test Description**: Verify that encoding and decoding LatLng/Double lists result in zero data loss.

### TST-UNT-002: TimeFormatter [Verified]
- **Requirement(s)**: `REQ-FIL-003`, `REQ-UI-002`
- **Component**: `TimeFormatter`
- **Test Description**: Verify Removal of leading zeros and correct localized string generation for s, m:s, and h:m:s formats.

### TST-UNT-003: EquipmentDiscovery [Verified]
- **Requirement(s)**: `REQ-TRK-004`, `REQ-SET-003`
- **Component**: `EquipmentDiscovery`
- **Test Description**: Verify sport-type resolution logic based on varied active sensor ID sets and speed.

### TST-UNT-004: MovingAverageFilter [Proposed]
- **Requirement(s)**: `REQ-FIL-001`
- **Component**: `MovingAverageFilter`
- **Test Description**: Verify correct averaging of a sequence of numbers (Numbered and Timed variants).

### TST-UNT-005: ExponentialSmoothing [Proposed]
- **Requirement(s)**: `REQ-FIL-002`
- **Component**: `ExponentialSmoothing`
- **Test Description**: Verify the recursive smoothing formula: `y[k+1] = α m[k] + (1-α) y[k]`.

### TST-UNT-006: SportTypeMapping [Proposed]
- **Requirement(s)**: `REQ-EXP-006`
- **Component**: `SportTypeMapping`
- **Test Description**: Verify that `SportTypeDatabaseManager` correctly handles a `null` Strava mapping as a persistent state.

### TST-UNT-007: EditWorkoutViewModel [Proposed]
- **Requirement(s)**: `REQ-EXP-006`
- **Component**: `EditWorkoutViewModel`
- **Test Description**: Verify that selecting a sport type with a `null` Strava mapping automatically sets `uploadToStrava` to `0`.

### TST-UNT-008: Accumulator [Verified]
- **Requirement(s)**: `REQ-PRO-003`
- **Component**: `Accumulator`
- **Test Description**: Verify that hardware deltas received during a pause are discarded, while deltas received while active are accumulated correctly.

### TST-UNT-009: WorkoutDataMapper [Verified]
- **Requirement(s)**: `REQ-UI-011`
- **Component**: `WorkoutDataMapper`
- **Test Description**: Verify that the extrema rows only include the "Big 6" performance metrics: HR, Speed/Pace, Cadence, Power, Altitude, and Temperature.

### TST-UNT-010: MappablePath [Proposed]
- **Requirement(s)**: `REQ-MAP-007`
- **Component**: `MappablePath`
- **Test Description**: Verify that coordinate projection (latLngs) is lazy and only occurs once.

### TST-UNT-011: TrackingViewsDb [Verified]
- **Requirement(s)**: `REQ-PRO-005`
- **Component**: `TrackingViewsDb`
- **Test Description**: 1. Simulated fresh install (Wipe data): Verify schema contains ShowElevationProfile. 2. Simulated upgrade (v9->v10): Verify ShowElevationProfile is added.

### TST-UNT-012: WorkoutSummariesViewModel [Proposed]
- **Requirement(s)**: `REQ-PER-008`
- **Component**: `WorkoutSummariesViewModel`
- **Jira**: ``ATT-457``
- **Test Description**: Verify `getFilteredWorkouts` correctly filters by `sportId` (Sub-Sport) when provided.

### TST-UNT-013: WorkoutSummariesViewModel [Proposed]
- **Requirement(s)**: `REQ-PER-008`
- **Component**: `WorkoutSummariesViewModel`
- **Jira**: ``ATT-458``
- **Test Description**: Verify `getFilteredWorkouts` correctly filters by `bSportType` (Base Sport) when `sportId` is null.

### TST-UNT-014: WorkoutSummariesDbHelper [Verified]
- **Requirement(s)**: `REQ-DAT-008`
- **Component**: `WorkoutSummariesDbHelper`
- **Jira**: ``ATT-476``
- **Test Description**: Verify that `onUpgrade` does not call `setTransactionSuccessful()` explicitly, which would conflict with `SQLiteOpenHelper`'s internal transaction management.

### TST-UNT-015: WorkoutClusterEngine [Proposed]
- **Requirement(s)**: `REQ-SET-065`
- **Component**: `WorkoutClusterEngine`
- **Jira**: ``ATT-502``
- **Test Description**: Verify that 6-term altitude-aware cluster similarity calculation correctly applies displacement weights when altitude positions are present, differentiates routes with distinct peaks/valleys, and cleanly falls back to 4-term matching when altitude coordinates are null or disabled.

### TST-UI-075: **Settings Terminology Audit** [Labels are concise and free of redundant qualifiers ("Settings"/"Einstellungen").]
- **Requirement(s)**: `REQ-UI-115`
- **Component**: **Settings Terminology Audit**
- **Jira**: ``ATT-272``
- **Test Description**: 1. Open Navigation Drawer -> Settings. 2. Verify items "Display" and "Search". 3. Switch to German. 4. Verify items "Display" and "Suche".

### TST-UI-001: **UI Design** [The table has distinct headers, clear alignment, and high-quality typography matching the Material 3 standards.]
- **Requirement(s)**: `REQ-UI-011`
- **Component**: **UI Design**
- **Test Description**: Inspect the workout summary extrema table on various device themes.

### TST-UI-011: **Formatting** [-]
- **Requirement(s)**: `REQ-UI-011`
- **Component**: **Formatting**
- **Test Description**: Verify that units in data rows are surrounded by square brackets (e.g., `[km/h]`).

### TST-UI-012: **Hierarchy** [-]
- **Requirement(s)**: `REQ-UI-011`
- **Component**: **Hierarchy**
- **Test Description**: Verify that the Sensor Name and the Numeric Values share the same visual weight, while the unit is micro-scaled.

### TST-UI-013: **Alignment** [-]
- **Requirement(s)**: `REQ-UI-011`
- **Component**: **Alignment**
- **Test Description**: Verify that in all data rows, the icon, sensor label, and numeric values are vertically aligned to their bottom edge.

### TST-UI-014: **Typography** [-]
- **Requirement(s)**: `REQ-UI-011`
- **Component**: **Typography**
- **Test Description**: Verify that units use a micro font size (e.g., 8-9.sp) and a lighter color (reduced alpha) than the sensor name.

### TST-UI-015: **Notation** [-]
- **Requirement(s)**: `REQ-UI-011`
- **Component**: **Notation**
- **Test Description**: Verify that the "Average" header column displays the mathematical symbol (Ø) instead of the word "average".

### TST-UI-016: **Proportion** [-]
- **Requirement(s)**: `REQ-UI-011`
- **Component**: **Proportion**
- **Test Description**: Verify that the icon height visually matches the height of the sensor name (bodyLarge line height).

### TST-UI-017: **Alignment** [-]
- **Requirement(s)**: `REQ-UI-011`
- **Component**: **Alignment**
- **Test Description**: Verify that the icon, sensor label, and bracketed unit are perfectly aligned to the same bottom baseline in the Compose Preview.

### TST-UI-018: **Baseline Precision** [-]
- **Requirement(s)**: `REQ-UI-011`
- **Component**: **Baseline Precision**
- **Test Description**: Verify that the bottom edge of the icon and the bottom of the text characters are visually perfectly aligned.

### TST-UI-019: **Hierarchy** [-]
- **Requirement(s)**: `REQ-UI-011`
- **Component**: **Hierarchy**
- **Test Description**: Verify context-aware bolding: HR/Power (Avg+Max), Speed/Pace/Cadence (Avg), Alt/Temp (Min+Max).

### TST-UI-020: **Contrast** [-]
- **Requirement(s)**: `REQ-UI-011`
- **Component**: **Contrast**
- **Test Description**: Verify that the "-" placeholder is visually lighter (alpha 0.3) than numeric values.

### TST-UI-021: **Redundancy** [-]
- **Requirement(s)**: `REQ-UI-012`
- **Component**: **Redundancy**
- **Test Description**: Verify that Min/Max altitude no longer appear in the Workout Details row, only Ascent and Descent.

### TST-UI-026: **Consistency** [-]
- **Requirement(s)**: `REQ-UI-013`
- **Component**: **Consistency**
- **Test Description**: In the Workout Summary card, verify that the altitude labels at the start/end of the chart axes match the Min/Max values shown in the Extrema table.

### TST-UI-027: **Harmonization** [-]
- **Requirement(s)**: `REQ-UI-014`
- **Component**: **Harmonization**
- **Test Description**: Start tracking; verify the Speed Average in the extrema table exactly matches the value derived from Total Distance / Active Time (e.g., if distance is 100m and active time is 100s, avg speed must be 1.0 m/s).

### TST-UI-028: **Localization** [Correct linguistic distinction across all supported languages.]
- **Requirement(s)**: `REQ-UI-018`
- **Component**: **Localization**
- **Test Description**: Inspect Workout Summary in various languages. Verify Ascent/Descent uses relative terminology (e.g., "Höhenmeter", "Elevation") and Min/Max uses absolute terminology (e.g., "Höhe", "Altitude").

### TST-UI-029: **Session Isolation** [Verify Map and Elevation Profile are empty at start.]
- **Requirement(s)**: `REQ-UI-009`
- **Component**: **Session Isolation**
- **Test Description**: 1. Start a workout, move, stop workout. 2. Immediately start a new workout.

### TST-UI-030: **Metric Consistency** [-]
- **Requirement(s)**: `REQ-UI-020`
- **Component**: **Metric Consistency**
- **Test Description**: Audit Workout and Segment items. Verify \"Gain\" uses `ic_ascent` in both. Verify \"Elevation\" label in Workout is bottom-aligned with values. Verify all icons/text share a consistent baseline.

### TST-UI-031: **Layout Hierarchy** [-]
- **Requirement(s)**: `REQ-UI-021`
- **Component**: **Layout Hierarchy**
- **Test Description**: Open Workout Summary. Inspect the Elevation section header (left column).

### TST-UI-034: **Identity Branding UI** [-]
- **Requirement(s)**: `REQ-UI-024`
- **Component**: **Identity Branding UI**
- **Test Description**: 1. Open Route List. 2. Inspect Strava route: verify logo is below name and no \"Source: Strava\" text exists. 3. Inspect Local route: verify \"Source: Local\" text is below name. 4. Verify no branding logo in metrics row.

### TST-UI-035: **Compact Metric Row** [-]
- **Requirement(s)**: `REQ-UI-025`
- **Component**: **Compact Metric Row**
- **Test Description**: 1. Open Route List. 2. Inspect gap between identity row and metrics row.

### TST-UI-036: **Branding Audit** [-]
- **Requirement(s)**: `REQ-UI-026`
- **Component**: **Branding Audit**
- **Test Description**: 1. Open Segment List. 2. Verify top logo height (e.g., 24dp). 3. Inspect individual Segment Items (if enabled).

### TST-UI-038: **Detailed Metric Scale** [Verify icons are 14dp and text is bodySmall.]
- **Requirement(s)**: `REQ-UI-028`
- **Component**: **Detailed Metric Scale**
- **Test Description**: 1. Open Periods screen. 2. Inspect sub-sport rows and \"Longest Workout\" highlight.

### TST-UI-039: **Pause Movement Isolation** [Observe map. No movement recorded during pause.]
- **Requirement(s)**: `REQ-PRO-004`
- **Component**: **Pause Movement Isolation**
- **Test Description**: 1. Start workout. 2. Move to create track. 3. **Pause**. 4. Move 50m.

### TST-UI-043: **Device Management Modernization** [-]
- **Requirement(s)**: `REQ-UI-041`, `REQ-UI-043`
- **Component**: **Device Management Modernization**
- **Test Description**: 1. Navigate to Pairing screen. 2. Verify Tabbed layout. 3. Interact with Pair switches. 4. Click device to open Edit Dialog. 5. Modify a setting (e.g. name).

### TST-UI-045: **Reactive Map Updates** [The route polyline on the map SHALL immediately change its color and width without requiring any camera movement (zoom/pan).]
- **Requirement(s)**: `REQ-MAP-009`
- **Component**: **Reactive Map Updates**
- **Test Description**: Open Route Detail Map. Toggle the \"Active\" switch in the header.

### TST-UI-046: **Stats Summary Block Visual Alignment** [The block SHALL use the unified `MetricItem` component. Headings (Workouts, Distance, Time, Ascent) SHALL be positioned above their respective values. Visual hierarchy (icons, typography) SHALL be consistent with Workout and Segment list items.]
- **Requirement(s)**: `REQ-UI-046`
- **Component**: **Stats Summary Block Visual Alignment**
- **Test Description**: Inspect the `StatsSummaryBlock` in the Equipment or SportType details screen.

### TST-UI-047: **Live Segment Sheet Swipe Limit** [The sheet SHALL stop expanding once the header and elevation profile are fully visible. It SHALL NOT cover the entire screen or reach the status bar.]
- **Requirement(s)**: `REQ-UI-047`
- **Component**: **Live Segment Sheet Swipe Limit**
- **Test Description**: 1. Start tracking. 2. When a segment is active, swipe up on the bottom sheet.

### TST-UI-048: **Sensor Status Header Visual Audit** [The row SHALL contain only sensor icons. No text labels (e.g., "HR", "Speed") SHALL be visible below the icons. Icons SHALL use a 22dp scale to remain legible without dominating the cockpit.]
- **Requirement(s)**: `REQ-UI-048`
- **Component**: **Sensor Status Header Visual Audit**
- **Test Description**: Inspect the `SensorStatus` row at the top of the tracking screen.

### TST-UI-049: **Sensor Source Dialog Verification** [A dialog SHALL appear. For active sensors, it shows real-time telemetry. For inactive sensors, it identifies the paired source device(s) or internal fallbacks.]
- **Requirement(s)**: `REQ-UI-049`
- **Component**: **Sensor Source Dialog Verification**
- **Test Description**: 1. Start a session. 2. Tap any sensor icon (active or inactive) in the header.

### TST-UI-051: **Grade Legend Visibility** [The grade color legend SHALL appear. Tap again to hide.]
- **Requirement(s)**: `REQ-MAP-010`
- **Component**: **Grade Legend Visibility**
- **Test Description**: 1. Open any Elevation Profile (e.g., in Aftermath). 2. Tap the info icon.

### TST-UI-050: **Sensor Identity Logic** [Both SHALL feature identical Row 1/2 blocks. `EditDeviceDialog` SHALL include Row 3 (Manufacturer). The LED dot and the Battery icon SHALL be perfectly aligned on their vertical centers.]
- **Requirement(s)**: `REQ-UI-050`
- **Component**: **Sensor Identity Logic**
- **Test Description**: Open `SensorSourceDialog`, `EditDeviceDialog`, and `DeviceItem`.

### TST-UI-071: **Branding** [Hamburger menu is visible on the very left. Tapping it opens the navigation drawer.]
- **Requirement(s)**: `REQ-UI-062`
- **Component**: **Branding**
- **Jira**: ``ATT-237``
- **Test Description**: 1. Open the app to the main tracking screen. 2. Inspect the header area.

### TST-UI-074: **Weekly Period Date Range** [Each week item SHALL display its start and end dates (e.g., \"May 11 - May 17\") below the primary week label.]
- **Requirement(s)**: `REQ-UI-116`
- **Component**: **Weekly Period Date Range**
- **Jira**: ``ATT-386``
- **Test Description**: 1. Open the 'Periods' screen. 2. Navigate to the 'Weeks' tab. 3. Inspect the period items.

### TST-UI-072: **Progress Notification Neutrality** [The card background SHALL be the neutral *surface* color. Branding colors (e.g. secondaryContainer) MUST NOT be used for the card background.]
- **Requirement(s)**: `REQ-UI-101`
- **Component**: **Progress Notification Neutrality**
- **Jira**: ``ATT-369``
- **Test Description**: 1. Trigger a Periods sync or Cluster initial load. 2. Inspect the background color of the progress card.

### TST-UI-073: **Bar Graph Synchronization** [1. During migration, the graph SHALL display the most recent months (right side of the graph) by default. 2. As the list grows into the past, the "Current" selection in the graph SHALL remain synchronized with the top-most visible period in the list.]
- **Requirement(s)**: `REQ-UI-104`
- **Component**: **Bar Graph Synchronization**
- **Jira**: ``ATT-376``
- **Test Description**: 1. Clear application data to trigger a Periods migration. 2. Navigate to the 'Periods' screen. 3. Observe the bar graph as the newest months are aggregated first. 4. Scroll the period list.

### TST-UI-077: **Consistent Spacing Audit** [Perfect horizontal alignment and uniform vertical rhythm across all list items.]
- **Requirement(s)**: `REQ-UI-121`
- **Component**: **Consistent Spacing Audit**
- **Jira**: ``ATT-474``
- **Test Description**: 1. Open Workout List, Route List, and Cluster List. 2. Verify all cards have 12dp horizontal internal padding. 3. Verify vertical spacing between sections (Header, Details, etc.) is uniform (12dp). 4. Verify list items are separated by 8dp.

### TST-UI-052: **SensorSourceDialog Header Color Decoupling & Semantic Green Token Verification Suite** [Section headers in `SensorSourceDialog` are decoupled from route blue tokens and render in dedicated dark green (`TTColor.SensorSourceActive`) and light green (`TTColor.SensorBackupActive`); route tokens remain untouched; all contract and unit tests pass with 100% clean-room success.]
- **Requirement(s)**: `REQ-UI-051`
- **Component**: **SensorSourceDialog Header Color Decoupling & Semantic Green Token Verification Suite**
- **Jira**: ``ATT-4374``
- **Test Description**:
  1. *SensorSourceDialog Color Token Decoupling Contract Test (`SensorSourceDialogContractTest.kt`, `TST-UI-052.1`)*:
  • Verify AST/source inspection of `SensorSourceDialog.kt` contains ZERO references to `TTColor.RouteSelected` or `TTColor.RouteUnselected`.
  • Verify 'Source Device' ("Quelle" / `R.string.source_device`) header explicitly references `TTColor.SensorSourceActive`.
  • Verify 'Active Backups' ("Aktive Backups" / `R.string.source_active_backups`) header explicitly references `TTColor.SensorBackupActive`.
  • Verify 'Not Connected' ("Nicht verbunden" / `R.string.source_not_connected`) header references `MaterialTheme.colorScheme.onSurfaceVariant`.
  2. *TTColor Semantic Green Token Values & WCAG Contrast Contract Test (`SensorSourceColorContractTest.kt`, `TST-UI-052.2`)*:
  • Assert `TTColor.SensorSourceActive` equals `Color(0xFF2E7D32)` (Material Green 800).
  • Assert `TTColor.SensorBackupActive` equals `Color(0xFF43A047)` (Material Green 600).
  • Evaluate WCAG 2.1 contrast ratio for `TTColor.SensorSourceActive` against Light surface (`#FFFFFF` $\ge 4.5:1$, actual $5.13:1$) and Dark surface (`#16161A` $\ge 3.0:1$ for bold text, actual $3.52:1$).
  • Evaluate WCAG 2.1 contrast ratio for `TTColor.SensorBackupActive` against Light surface (`#FFFFFF` $\ge 3.0:1$ for bold text, actual $3.30:1$) and Dark surface (`#16161A` $\ge 4.5:1$, actual $5.46:1$).
  3. *Route Domain Immutability Contract Test (`RouteColorPaletteContractTest.kt`, `TST-UI-052.3`)*:
  • Assert `TTColor.RouteSelected` strictly equals `Color(0xFF1565C0)` (Royal Blue 800) and `TTColor.RouteUnselected` strictly equals `Color(0xFF90CAF9)` (Soft Steel Blue 200).
  4. *9-Language Localization Audit (`TranslationParityTest.kt`, `TST-UI-052.4`)*:
  • Verify `source_device`, `source_active_backups`, and `source_not_connected` string presence and parity across all 9 supported locales (EN, DE, ES, FR, IT, JA, NL, PL, PT).
  5. *Clean-Room Full Suite Regression Execution (`TST-UI-052.5`)*:
  • Execute `./gradlew testDebugUnitTest` verifying 100% pass rate with zero regressions.
  
  **Given-When-Then Acceptance Criteria**:
  • *Given* `SensorSourceDialog.kt` rendered in the tracking cockpit,
  • *When* inspecting the "Quelle" ("Source Device") section header,
  • *Then* it SHALL render with `TTColor.SensorSourceActive` (`Color(0xFF2E7D32)`), maintaining $\ge 3.0:1$ WCAG AA bold contrast across Light, Dark, and AMOLED themes.
  • *Given* active backup sensors present,
  • *When* inspecting the "Aktive Backups" section header,
  • *Then* it SHALL render with `TTColor.SensorBackupActive` (`Color(0xFF43A047)`), clearly differentiated from the primary source header and achieving $\ge 3.0:1$ contrast.
  • *Given* disconnected sensors present,
  • *When* inspecting the "Nicht verbunden" section header,
  • *Then* it SHALL render in neutral grey with `MaterialTheme.colorScheme.onSurfaceVariant`.
  • *Given* static code inspection,
  • *When* searching `SensorSourceDialog.kt` for `RouteSelected` or `RouteUnselected`,
  • *Then* zero occurrences SHALL be found.
  • *Given* the route visualization subsystem,
  • *When* route colors are queried,
  • *Then* `RouteSelected` and `RouteUnselected` SHALL remain strictly Royal Blue without regression.

### TST-UI-053: **Longest Workout Navigation** [The app SHALL navigate to the list and automatically scroll to the targeted workout.]
- **Requirement(s)**: `REQ-UI-052`
- **Component**: **Longest Workout Navigation**
- **Test Description**: 1. Open a Period Summary with multiple workouts. 2. Tap the "Longest Workout" section.

### TST-UI-054: **Location Audit Metric** [The `SensorSourceDialog` SHALL show "Accuracy" and its current value (e.g. "5 m") instead of "Longitude".]
- **Requirement(s)**: `REQ-UI-053`
- **Component**: **Location Audit Metric**
- **Test Description**: 1. Start a tracking session with a location provider active. 2. Tap the location icon in the `SensorStatus` header.

### TST-UI-055: **Multi-Track Visibility Audit** [The map SHALL update polylines in real-time to show/hide the selected tracks. The selection menu includes a color legend.]
- **Requirement(s)**: `REQ-MAP-011`, `REQ-MAP-012`
- **Component**: **Multi-Track Visibility Audit**
- **Test Description**: 1. Open the Workout Details screen for a recorded activity. 2. Tap the "Layers" FAB. 3. Toggle different track types (GPS, Fused, Network).

### TST-UI-056: **Map Preference Persistence** [The previously selected track layers SHALL remain visible.]
- **Requirement(s)**: `REQ-MAP-013`
- **Component**: **Map Preference Persistence**
- **Test Description**: 1. Toggle technical tracks in Workout Details. 2. Close and reopen the workout.

### TST-UI-057: **Period FAB Order** [The Share button SHALL be the topmost item in the FAB stack.]
- **Requirement(s)**: `REQ-MAP-014`
- **Component**: **Period FAB Order**
- **Test Description**: 1. Open the Period Map. 2. Verify the vertical order of FABs.

### TST-UI-058: **High-Fidelity Period Tracks** [Both tracks SHALL show identical fine-granular detail.]
- **Requirement(s)**: `REQ-MAP-015`
- **Component**: **High-Fidelity Period Tracks**
- **Test Description**: 1. Compare a track in a Period Map overview with the same track in Workout Details.

### TST-UI-059: **Alpha Consistency Audit** [No hardcoded `copy(alpha = ...)` literals; all use `TTAlpha` constants.]
- **Requirement(s)**: `REQ-UI-054`
- **Component**: **Alpha Consistency Audit**
- **Test Description**: Static analysis of `WorkoutExtrema.kt` and `MetricItem.kt`.

### TST-UI-060: **Primary Metric Hierarchy** [The primary value (e.g. Distance) SHALL be rendered in the `primary` theme color.]
- **Requirement(s)**: `REQ-UI-055`
- **Component**: **Primary Metric Hierarchy**
- **Test Description**: Open Workout Details or Segment Details.

### TST-UI-061: **Theme Audit** [All UI-related colors and alphas MUST use semantic tokens.]
- **Requirement(s)**: `REQ-UI-056`
- **Component**: **Theme Audit**
- **Test Description**: Project-wide grep for `Color(0xFF...)` and `copy(alpha = ...)`.

### TST-UI-062: **Protocol-Aware Menu** [The "Check ANT+ Installation" option SHALL NOT be visible in the Bluetooth view, but SHALL be visible in the ANT+ view.]
- **Requirement(s)**: `REQ-UI-044`
- **Component**: **Protocol-Aware Menu**
- **Test Description**: 1. Open the Bluetooth sensor list. 2. Check the options menu. 3. Open the ANT+ sensor list. 4. Check the options menu.

### TST-UI-063: **Global Icon Clipping** [All icons SHALL appear with subtle 4dp rounded corners, and square white corner artifacts (on PNG assets) SHALL be masked.]
- **Requirement(s)**: `REQ-UI-057`
- **Component**: **Global Icon Clipping**
- **Test Description**: 1. Open the app in Dark Mode. 2. Inspect sensor and protocol icons.

### TST-UI-066: **Elevation Profile Visibility** [The elevation profile SHALL NOT be visible.]
- **Requirement(s)**: `REQ-UI-058`
- **Component**: **Elevation Profile Visibility**
- **Test Description**: 1. Open the Recurring Path Heatmap. 2. Inspect the bottom of the screen.

### TST-UI-067: **Robust Sharing Snapshot** [1. The app SHALL NOT crash. 2. A combined image containing the Header and Map (but no elevation profile) SHALL be generated. 3. The UI MUST remain responsive during generation.]
- **Requirement(s)**: `REQ-UI-059`
- **Component**: **Robust Sharing Snapshot**
- **Test Description**: 1. Open a Workout Cluster Heatmap (where elevation is hidden). 2. Tap the Share button. 3. Verify that a loading indicator appears on the button. 4. Select a sharing target in the system dialog.

### TST-UI-069: **Safe Map Snapshot** [1. App does not crash. 2. A Toast message ("Unable to generate share image") appears if the snapshot fails.]
- **Requirement(s)**: `REQ-UI-060`
- **Component**: **Safe Map Snapshot**
- **Test Description**: 1. Trigger a snapshot multiple times rapidly. 2. Attempt a snapshot on a device with low memory.

### TST-UI-070: **Universal Delete-Only Long-Press Context Menu Enforcement** [The deletion `DropdownMenu` is anchored consistently at the Top-Left corner (`Alignment.TopStart`, covering the header) across all deletable list items. The menu strictly and exclusively contains the Delete option (or documented lifecycle exceptions), forbidding Edit or Show on Map. All automated tests pass with 100% success rate.]
- **Requirement(s)**: `REQ-UI-061`, `REQ-UI-165`, `REQ-UI-166`, `REQ-UI-180`
- **Component**: **Universal Delete-Only Long-Press Context Menu Enforcement**
- **Jira**: ``ATT-238` / `ATT-1523``
- **Test Description**:
  1. Long-press across all deletable list items (Workouts, Routes, Clusters, Equipment, Sport Types, Favorite Locations).
  2. Run automated structural AST and layout audit test (`GlobalDeleteContextMenuAuditTest`) across all list card composables.
  3. Verify that `KnownLocationCard` does not contain "Edit" or "Show on Map" in its context menu, does not render an overflow MoreVert icon in the header, and anchors the context menu strictly to Top-Left.
  4. Run 9-language localization audit for deletion confirmation strings (`@string/delete`, `@string/really_delete_format`).
  5. Execute full unit test suite.

### TST-UI-078: **Cluster Naming Dialog Localization Audit** [All titles, labels, prompts, dropdown options, and buttons in `ClusterNamingDialog` are rendered in the active locale. Zero hardcoded English strings appear.]
- **Requirement(s)**: `REQ-UI-123`
- **Component**: **Cluster Naming Dialog Localization Audit**
- **Jira**: ``ATT-565``
- **Test Description**: 1. Set app locale to non-English (e.g., German/DE). 2. Trigger a TCX import to launch `ClusterNamingDialog`. 3. Inspect all text elements in the dialog.

### TST-UI-076: **Period UI Layout Integrity** [1. The Bar Graph SHALL be immediately visible below the tabs. 2. The Bar Graph SHALL move up and down in synchronization with the list as the header collapses/expands.]
- **Requirement(s)**: `REQ-UI-104`
- **Component**: **Period UI Layout Integrity**
- **Jira**: ``ATT-444``
- **Test Description**: 1. Open the 'Periods' screen. 2. Inspect the visibility of the Bar Graph. 3. Scroll the period list downwards.

### TST-UI-079: **Elevation Profile Chart Bounds Sanitization & Strict Containment Verification** [Unplausible override bounds are sanitized to match stream envelope; chart bounds strictly envelope all rendered points without clipping; mountainous profiles retain full dynamic range.]
- **Requirement(s)**: `REQ-UI-126`
- **Component**: **Elevation Profile Chart Bounds Sanitization & Strict Containment Verification**
- **Jira**: ``ATT-508``
- **Test Description**:
  1. Test `ElevationProfile` with flat coastal elevation points (~0 m to 3 m) and corrupted override bounds (`minAltitudeOverride = -63.0`, `maxAltitudeOverride = 60.0`); verify bounds clamp to stream envelope with 20m aesthetic span.
  2. Test `ElevationProfile` with workout points dipping to 360 m and override `minAltitudeOverride = 368.0` (or 364.0); verify `bounds.min` is clamped to <= 360 m, guaranteeing that NO point on the profile curve renders below the chart bottom bound.
  3. Test with valid mountainous route; verify that full dynamic elevation range is preserved.
  4. Run unit tests in `ElevationProfileBoundsTest.kt`.

### TST-UI-080: **Delete Old Workouts Compose Dialog & Action Trigger Verification** [Action trigger and Compose dialog operate seamlessly with input validation and default 365 days retention.]
- **Requirement(s)**: `REQ-UI-127`
- **Component**: **Delete Old Workouts Compose Dialog & Action Trigger Verification**
- **Jira**: ``ATT-296``
- **Test Description**:
  1. Verify `defaultDaysToKeep` string resource evaluates to `"365"`.
  2. Verify `WorkoutListActions` renders the delete sweep action button (`ic_baseline_delete_sweep_24`) with localized content description.
  3. Verify clicking the action button triggers `DeleteOldWorkoutsDialog`.
  4. Verify dialog pre-fills input field with `"365"`.
  5. Verify entering empty string, non-numeric characters, or negative numbers disables the confirm button.
  6. Verify entering valid integer (e.g. 180) enables the confirm button.
  7. Verify confirming dispatches `executeDeleteOldWorkouts(180)` and closes dialog.
  8. Verify dismissing closes dialog without triggering deletion.
  9. Execute automated tests.

### TST-UI-081: **Bulk Workout Deletion Progress Feedback & Notification Verification** [In-app modal dialog and system notification provide continuous, accurate progress updates during deletion and analytical resynchronization, automatically dismissing upon completion.]
- **Requirement(s)**: `REQ-UI-128`
- **Component**: **Bulk Workout Deletion Progress Feedback & Notification Verification**
- **Jira**: ``ATT-705``
- **Test Description**:
  1. Trigger bulk deletion with $N$ old workouts.
  2. Verify `WorkoutTabsScreen` renders modal `WorkoutDeletionProgressDialog`.
  3. Verify Phase 1 (Deletion): Dialog displays localized title (`@string/deleteOldWorkouts`), formatted session name (`@string/deleting_workout`), numeric counter ($X / Y$), and `LinearProgressIndicator` tracking fractional completion.
  4. Verify dialog is modal and non-dismissible (`dismissOnBackPress = false`, `dismissOnClickOutside = false`).
  5. Verify Phase 2 (Resynchronization): Dialog transitions to indeterminate `CircularProgressIndicator` and status message (`@string/please_wait`) during period cache and cluster resync.
  6. Verify ongoing system notification (`NotificationCompat.Builder`) is posted on channel `NOTIFICATION_CHANNEL__EXPORT` with `CATEGORY_PROGRESS` and `setOngoing(true)` tracking progress $X / Y$, and is cancelled when operation concludes.
  7. Verify upon operation completion or error, `deletionProgress` resets to `Idle`, the dialog dismisses automatically, and the notification is cleared.
  8. Execute automated unit tests.

### TST-UI-082: **Bulk Workout Deletion Two-Step Confirmation Verification** [Bulk workout deletion requires explicit, two-step user confirmation; cancellation cleanly aborts without mutations, while confirmation accurately triggers deletion with the specified retention parameter.]
- **Requirement(s)**: `REQ-UI-129`
- **Component**: **Bulk Workout Deletion Two-Step Confirmation Verification**
- **Jira**: ``ATT-704``
- **Test Description**:
  1. Open `DeleteOldWorkoutsDialog` from the workouts header action.
  2. Input a valid retention threshold (e.g. 180 days).
  3. Verify clicking the proceed action in `DeleteOldWorkoutsDialog` dismisses the input dialog and displays the confirmation dialog (`DeleteConfirmationDialog`) with title `@string/deleteOldWorkouts` and formatted warning message (`@string/really_delete_old_workouts_format`) specifying 180 days.
  4. Verify that tapping "Cancel", tapping outside, or executing the system back gesture dismisses the confirmation dialog without invoking deletion or altering the database.
  5. Verify that while the confirmation dialog is displayed, no database write or deletion progress notification is triggered.
  6. Verify that tapping "Delete" (`@string/delete`) in the confirmation dialog confirms the operation, dismisses the confirmation dialog, dispatches `executeDeleteOldWorkouts(180)`, and initiates deletion with progress feedback (`REQ-UI-128`).
  7. Verify string formatting and localization parity across all 9 supported application locales.
  8. Execute automated unit tests.

### TST-UI-083: **Sport-Specific Equipment Selection & In-Place Expansion Verification** [Equipment dropdown strictly enforces base sport partitioning; 3-case hierarchy ($N > 1$, $N == 1$, $N == 0$) and in-place expansion operate as specified; cross-sport gear pollution is completely eliminated.]
- **Requirement(s)**: `REQ-UI-130`
- **Component**: **Sport-Specific Equipment Selection & In-Place Expansion Verification**
- **Jira**: ``ATT-715``
- **Test Description**:
  1. Configure mock `EquipmentRepository` with bicycles (`BSportType.BIKE`) and shoes (`BSportType.RUN`).
  2. Case 1 ($N > 1$): Configure cycling sport with 2 linked bikes. Verify dropdown presents `[- none -, <linked bike 1>, <linked bike 2>, "+ all bikes +"]` and zero shoes.
  3. In-Place Expansion: Select `"+ all bikes +"`; verify list expands in-place to `[- none -, <all bikes...>]` with `stayOpenOn` preventing premature dropdown closure.
  4. Case 2 ($N == 1$): Configure running sport with 1 linked shoe. Verify dropdown presents `[- none -, <all shoes...>]` and the uniquely linked shoe is automatically preselected.
  5. Case 3 ($N == 0$): Configure cycling sport with 0 linked bikes. Verify dropdown presents `[- none -, <all bikes...>]` and `- none -` is preselected.
  6. Cross-Sport Switching (Different Base Sport): Switch from Cycling (with bike selected) to Running. Verify selected equipment clears / switches to linked shoe ($N == 1$) or `- none -` ($N == 0$) and options refresh strictly to shoes.
  7. Cross-Sport Switching (Same Base Sport): Switch between cycling sports; verify valid selected bike is retained.
  8. Other / Unknown Sport (`BSportType.UNKNOWN`): Verify dropdown presents only `[- none -]` (plus explicit links).
  9. Execute automated unit tests in `EditWorkoutEquipmentTest.kt`.

### TST-UI-084: **Compact Strava Segment Efforts Presentation & Expandable Accordion Verification** [Segment efforts in workout summary automatically collapse to highlights when $> 4$ segments with non-highlights exist; in-place accordion expands and collapses smoothly; card bloat is prevented.]
- **Requirement(s)**: `REQ-UI-131`
- **Component**: **Compact Strava Segment Efforts Presentation & Expandable Accordion Verification**
- **Jira**: ``ATT-716``
- **Test Description**:
  1. Unit Test Strava Parsing: Verify `StravaActivityParser` accurately parses `isStarred`, `starred_date`, `prRank`, and `komRank` from Strava activity JSON payloads (including nested `segment` object, flat fields, and `achievements` array).
  2. Case A (Small Segment Count $\le 4$): Provide activity with $\le 4$ segments (e.g. 3 segments). Verify all 3 segments are rendered directly and NO expand/collapse button is displayed.
  3. Case B (All Highlights $N == N_{\text{highlight}}$): Provide activity with $> 4$ segments (e.g. 6 segments) where all 6 have PR or Starred status. Verify all 6 are rendered directly and NO expand/collapse button is displayed.
  4. Case C (Collapse Eligible $N > 4$ and $N > N_{\text{highlight}}$): Provide activity with 10 segments where 3 are PRs and 1 is starred (4 highlights, 6 standard). Verify by default only the 4 highlighted segments are displayed, followed by `"▼ Show all 10 segments (+6 more)"` and in-place expansion toggles to all 10.
  5. Case D (Zero Highlights $N > 4, N_{\text{highlight}} == 0$): Provide activity with 7 segments where 0 are PRs or starred. Verify in collapsed view zero segment rows and `@string/strava_no_highlights` are displayed, followed by `"▼ Show all 7 segments (+7 more)"`, and tapping expansion reveals all 7.
  6. In-Place Collapse: Trigger click on collapse button. Verify list collapses back to the highlighted state.
  7. Starred Segment Matching: Verify starred segments are matched by Strava ID or segment name against `SegmentsDatabaseManager`.
  8. Localization Parity: Verify action button labels and status notes are correctly formatted across all 9 supported application locales (EN, DE, ES, FR, IT, JA, NL, PL, PT).
  9. Invariants: Verify Best Efforts, external Strava activity click launch, and Strava branding are fully preserved.
  10. Automated Unit & UI Tests: Execute automated test suite.

### TST-UI-085: **Multi-Dimensional Workout Filtering, Modal Sheet & Persistence Verification** [Multi-dimensional workout filtering filters sessions accurately across all criteria; funnel icon and active badge render cleanly; bottom sheet layout is spacious and anchored flush; criteria reset when returning to main view; all existing list behaviors and invariants remain intact.]
- **Requirement(s)**: `REQ-UI-132`
- **Component**: **Multi-Dimensional Workout Filtering, Modal Sheet & Persistence Verification**
- **Jira**: ``ATT-128`, `ATT-742``
- **Test Description**:
  1. Unit Test Predicate Evaluation: Verify `WorkoutFilterCriteria.matches()` across all dimensions: text query, date range, year, specific `sportTypeId`, specific `equipmentId`, commute flag, trainer flag, GPS track flag, and distance/duration thresholds.
  2. Action Trigger & Funnel Icon: Verify `WorkoutListActions` renders the Material funnel icon (`Icons.Default.FilterAlt`) with localized content description and badge counter when active.
  3. Modal Filter Bottom Sheet Layout: Verify tapping the filter icon opens `WorkoutFilterBottomSheet` displaying a dedicated top header with title and dismiss button (`Icons.Default.Close`), proper bottom anchoring with zero gap artifacts, and a dedicated bottom action row for "Reset all" and "Apply".
  4. Active Filter Chips: Verify applying filters displays horizontal scrolling chip strip above the workout list with localized labels and trailing `✕` icons. Verify tapping `✕` removes that specific filter dimension reactively.
  5. Clear All / Reset: Verify tapping "Reset all" in the sheet or removing all chips resets criteria to default, hides chips and badge, and restores full list.
  6. Main View Navigation Reset: Verify navigating away from the workouts section back to the main view clears active filter criteria, so subsequent visits start with the full unfiltered list.
  7. Invariants: Verify sorting orders (`DATE`, `DURATION`, `DISTANCE`, `ELEVATION_GAIN`), view mode toggle (Compact/Detailed), sport tabs (All, Bike, Run, Other), and deletion workflows remain fully functional without regression.
  8. Localization Parity: Verify all filter category labels, action buttons, chip descriptors, and empty-state filter prompts are localized across all 9 supported locales (EN, DE, ES, FR, IT, JA, NL, PL, PT).
  9. Automated Unit Tests: Execute automated test suite.

### TST-UI-086: **Multi-Dimensional Route Filtering, Shared Modal Sheet & Persistence Verification** [Multi-dimensional route filtering accurately filters routes across all criteria; shared bottom sheet layout is spacious, gap-free, and code is cleanly shared with workout filtering; criteria reset when returning to main view; all existing route behaviors and invariants remain intact.]
- **Requirement(s)**: `REQ-UI-133`
- **Component**: **Multi-Dimensional Route Filtering, Shared Modal Sheet & Persistence Verification**
- **Jira**: ``ATT-736``
- **Test Description**:
  1. Unit Test Predicate Evaluation: Verify `RouteFilterCriteria.matches()` across all dimensions: text query (name/description substring), route origin (`RouteSource`), map selection (`isSelected`), distance thresholds, and elevation gain thresholds.
  2. Action Trigger & Funnel Icon: Verify `RouteTabbedScreen` renders the Material funnel icon (`Icons.Default.FilterAlt`) at the end of the action row (`Select All` $\rightarrow$ `Import` $\rightarrow$ `Sync` $\rightarrow$ `Sort` $\rightarrow$ `Filter`) with badge counter when filters are active.
  3. Shared Modal Bottom Sheet Layout: Verify tapping the filter icon opens `RouteFilterBottomSheet` built on shared `FilterBottomSheetScaffold`, displaying a dedicated top header with title and dismiss button (`Icons.Default.Close`), zero bottom gap artifacts, filter sections (Query, Source, Selected, Distance, Elevation), and a dedicated bottom action row for "Reset all" and "OK".
  4. Active Filter Chips: Verify applying route filters displays horizontal scrolling chip strip above the route list with localized labels and trailing `✕` icons using shared `RemovableFilterChip`. Verify tapping `✕` removes that specific filter dimension reactively.
  5. Clear All / Reset: Verify tapping "Reset all" in the sheet or removing all chips resets criteria to default, hides chips and badge, and restores the full route list.
  6. Main View Navigation Reset: Verify navigating away from the routes section back to the main view (`drawer_start_tracking`) or switching drawers clears active route filter criteria, so subsequent visits start with the full unfiltered list.
  7. Code Sharing Invariant: Verify workout filtering (`REQ-UI-132` / `TST-UI-085`) continues to function without visual or functional regressions after adopting the shared `FilterBottomSheetScaffold` and `RemovableFilterChip`.
  8. Route Invariants: Verify sorting orders (`DISTANCE_TO_USER`, `TOTAL_ELEVATION_GAIN`, `ROUTE_DISTANCE`, `NAME`), sport tabs (All, Bike, Run, Unknown), GPX import, Strava sync, and route editing remain fully operational.
  9. Localization Parity: Verify all route filter category labels, action buttons, chip descriptors, and empty-state filter prompts are localized across all 9 supported locales (EN, DE, ES, FR, IT, JA, NL, PL, PT).
  10. Automated Unit Tests: Execute automated test suite (`RouteFilterCriteriaTest`, `RoutesViewModelFilterTest`, `TranslationParityTest`).

### TST-UI-087: **Multi-Dimensional Segment Filtering, Shared Modal Sheet & Persistence Verification** [Multi-dimensional segment filtering accurately filters starred segments across all criteria; shared bottom sheet layout is spacious, gap-free, and code is cleanly shared with workout and route filtering; criteria persist across tab switches; all existing segment behaviors and invariants remain intact.]
- **Requirement(s)**: `REQ-UI-134`
- **Component**: **Multi-Dimensional Segment Filtering, Shared Modal Sheet & Persistence Verification**
- **Jira**: ``ATT-735``
- **Test Description**:
  1. Unit Test Predicate Evaluation: Verify `SegmentFilterCriteria.matches()` across all dimensions: text query (name/city substring), minimum climb category (including category-0 exclusion), distance thresholds, elevation gain thresholds, and `hasPR` (`prTime_raw > 0`).
  2. Action Trigger & Funnel Icon: Verify `SegmentsTabsScreen` renders the Material funnel icon (`Icons.Default.FilterAlt`) at the end of the action row (after sort button) with badge counter when filters are active.
  3. Shared Modal Bottom Sheet Layout: Verify tapping the filter icon opens `SegmentFilterBottomSheet` built on shared `FilterBottomSheetScaffold`, displaying a dedicated top header with title and dismiss button (`Icons.Default.Close`), zero bottom gap artifacts, filter sections (Query, Climb Category, Distance, Elevation, Has PR), and a dedicated bottom action row for "Reset all" and "OK".
  4. Active Filter Chips: Verify applying segment filters displays horizontal scrolling chip strip below the sport tabs with localized labels and trailing `✕` icons using shared `RemovableFilterChip`. Verify tapping `✕` removes that specific filter dimension reactively.
  5. Clear All / Reset: Verify tapping "Reset all" in the sheet or removing all chips resets criteria to default, hides chips and badge, and restores the full segment list.
  6. Climb Category Filter Semantics: Verify that when `minClimbCategory >= 1`, segments with `climbCategory_raw == 0` (uncategorized) are excluded from results.
  7. Tab Persistence: Verify that active filter criteria persist when switching between Bike and Run tabs within the segment screen.
  8. Code Sharing Invariant: Verify workout filtering (`REQ-UI-132` / `TST-UI-085`) and route filtering (`REQ-UI-133` / `TST-UI-086`) continue to function without visual or functional regressions after segment filter integration.
  9. Segment Invariants: Verify sorting orders (`DISTANCE_TO_USER`, `CLIMB_CATEGORY`, `TOTAL_ELEVATION_GAIN`, `AVERAGE_GRADE`, `SEGMENT_DISTANCE`, `NAME`), sport tabs (Bike, Run), and Strava segment sync remain fully operational.
  10. Localization Parity: Verify all segment filter category labels, action buttons, chip descriptors, and empty-state filter prompts are localized across all 9 supported locales (EN, DE, ES, FR, IT, JA, NL, PL, PT).
  11. Automated Unit Tests: Execute automated test suite (`SegmentFilterCriteriaTest`, `SegmentListViewModelFilterTest`, `TranslationParityTest`).

### TST-UI-088: **Multi-Dimensional Favorite Tracks Filtering, Shared Modal Sheet & Persistence Verification** [Multi-dimensional favorite track filtering accurately filters clusters across all criteria; shared bottom sheet layout is spacious and gap-free; criteria reset when returning to main view; all existing cluster behaviors and invariants remain intact.]
- **Requirement(s)**: `REQ-UI-135`
- **Component**: **Multi-Dimensional Favorite Tracks Filtering, Shared Modal Sheet & Persistence Verification**
- **Jira**: ``ATT-737``
- **Test Description**:
  1. Unit Test Predicate Evaluation: Verify `ClusterFilterCriteria.matches()` across all dimensions: text query (cluster name substring), equipment name matching (cluster linked equipment contains selected equipment), minimum reference distance thresholds ($10\,\text{km}, 25\,\text{km}, 50\,\text{km}, 100\,\text{km}$), and minimum recording count thresholds ($\ge 3, 5, 10, 25$).
  2. Action Trigger & Funnel Icon: Verify `WorkoutClustersTabsScreen` renders the Material funnel icon (`Icons.Default.FilterAlt`) in the header action row beside the settings tuning icon with badge counter reflecting active filter dimensions count.
  3. Shared Modal Bottom Sheet Layout: Verify tapping the filter icon opens `ClusterFilterBottomSheet` built on shared `FilterBottomSheetScaffold`, displaying a dedicated top header with title and dismiss button (`Icons.Default.Close`), zero bottom gap artifacts, filter sections (Query, Equipment, Distance, Recordings), and a dedicated bottom action row for "Reset all" and "OK".
  4. Active Filter Chips: Verify applying cluster filters displays horizontal scrolling chip strip below the sport tabs with localized labels and trailing `✕` icons using shared `RemovableFilterChip`. Verify tapping `✕` removes that specific filter dimension reactively.
  5. Clear All / Reset: Verify tapping "Reset all" in the sheet or removing all chips resets criteria to default, hides chips and badge, and restores the full cluster list.
  6. Main View Navigation Reset: Verify navigating away from Favorite Tracks (`drawer_my_locations`) back to Start Tracking or another drawer item in `MainActivityWithNavigation` clears active cluster filter criteria, so subsequent visits start with the full unfiltered list.
  7. Tab Interoperability: Verify active filters remain applied across all sport tabs (All, Bike, Run, Other, Unclustered).
  8. Code Sharing Invariant: Verify workout (`REQ-UI-132`), route (`REQ-UI-133`), and segment (`REQ-UI-134`) filtering continue to function without regressions after cluster filter integration.
  9. Cluster Invariants: Verify cluster recalculation engine, tuning sliders, manual cluster addition, cluster deletion, and heatmap views remain fully operational.
  10. Localization Parity: Verify all cluster filter category labels, action buttons, chip descriptors, and empty-state filter prompts are localized across all 9 supported locales (EN, DE, ES, FR, IT, JA, NL, PL, PT).
  11. Automated Unit Tests: Execute automated test suite (`ClusterFilterCriteriaTest`).

### TST-UI-089: **Favorite Tracks (Lieblingsstrecken) Sorting Verification** [Favorite tracks sort cleanly and reactively across all dimensions; location availability is handled gracefully with muted state; sort dropdown UI adheres to Routes and Segments design language.]
- **Requirement(s)**: `REQ-UI-136`
- **Component**: **Favorite Tracks (Lieblingsstrecken) Sorting Verification**
- **Jira**: ``ATT-761``
- **Test Description**:
  1. Unit Test Sort Order Logic: Verify `ClusterSortOrder` enum items and sorting logic (`RECORDINGS`, `DISTANCE_TO_USER`, `DISTANCE`, `NAME`):
  • `RECORDINGS`: Sorts clusters by `hitCount` descending, with secondary tie-breaking by `name.lowercase()` ascending.
  • `DISTANCE_TO_USER`: Geodetic distance from user coordinates to cluster `startLat`/`startLng` ascending. When location is null, falls back to `name.lowercase()`.
  • `DISTANCE`: Sorts clusters by `refDistance` descending, with secondary tie-breaking by `name.lowercase()` ascending.
  • `NAME`: Sorts clusters alphabetically by `name.lowercase()` ascending.
  2. Action Trigger & Sort Button: Verify `WorkoutClustersTabsScreen` renders the sort action button (`Icons.Default.Sort`) with localized content description (`@string/sort`), positioned between tuning icon and filter action button.
  3. Dropdown Menu Layout & Checkmark: Verify tapping the sort button opens `DropdownMenu` showing all `ClusterSortOrder` entries with localized labels. Verify leading checkmark (`Icons.Default.Check`) is displayed strictly next to the active sort order.
  4. GPS Location Availability Handling: Verify that when user location is null/unavailable, `DISTANCE_TO_USER` menu item is disabled (`enabled = false`) and styled with disabled alpha (`0.38f`).
  5. Reactive List Ordering: Verify selecting a sort order updates `viewModel.sortOrder`, immediately re-sorts the displayed cluster list, and dismisses the dropdown menu.
  6. Tab Scope: Verify sorting applies across all sport tabs (*All*, *Bike*, *Run*, *Other*), while the *Unclustered* tab retains native workout date ordering.
  7. Invariants: Verify existing filtering (`REQ-UI-135`), cluster tuning, manual cluster creation, and route/segment sorting remain intact without regression.
  8. Localization Parity: Verify all sort order labels are localized across all 9 supported locales (EN, DE, ES, FR, IT, JA, NL, PL, PT).
  9. Automated Unit Tests: Execute automated unit test suite (`ClusterSortOrderTest`).

### TST-UI-090: **Cluster Algorithm Info Presentation & Action Trigger Verification** [Educational info modal displays clearly with complete 3D topological algorithm explanations; accessible from both Favorite Tracks header and Cluster Parameter screen; dismisses smoothly; zero effect on cluster calculations or settings state.]
- **Requirement(s)**: `REQ-UI-137`
- **Component**: **Cluster Algorithm Info Presentation & Action Trigger Verification**
- **Jira**: ``ATT-501`, `ATT-792`, `ATT-795`, `ATT-796``
- **Test Description**:
  1. Component Unit & Compose Render Test (`ClusterInfoDialogTest.kt`):
  • Verify `ClusterInfoDialog` renders title `@string/cluster_info_title`.
  • Verify all 4 educational sections (What are clusters, 3D Fingerprint, Sport Type Awareness, Tuning Guidance) render with non-empty localized strings.
  • Verify dismiss button `@string/close` invokes the `onDismiss` callback.
  2. Action Trigger in `WorkoutClustersTabsScreen`: Verify header action row displays info action button with localized content description `@string/cluster_info_title`, and clicking the button opens `ClusterInfoDialog`.
  3. Action Trigger in `ClusterTuningScreen`: Verify `TopAppBar` displays info action button with localized content description `@string/cluster_info_title`, and clicking the button opens `ClusterInfoDialog`.
  4. Localization Parity: Verify all newly introduced string keys exist across all 9 supported application locales (EN, DE, ES, FR, IT, JA, NL, PL, PT) via automated resource validation.
  5. Invariants & State Isolation: Verify opening and closing `ClusterInfoDialog` does not mutate cluster tolerance preferences (`ClusterTolerances`), database entries, or sort/filter state.

### TST-UI-091: **Detailed Workout Inspection from Favorite Tracks Verification** [Inspecting any workout from Lieblingsstrecken displays true altitude profile, correct elevation bounds, sensor extrema pins, and available technical track layers; `toMapTrack()` decodes elevations accurately.]
- **Requirement(s)**: `REQ-UI-138`
- **Component**: **Detailed Workout Inspection from Favorite Tracks Verification**
- **Jira**: ``ATT-818`, `ATT-821``
- **Test Description**:
  1. Unit Tests for `MapModels.toMapTrack()` (`MapModelsTest.kt`):
  • Verify that `WorkoutData.toMapTrack()` correctly decodes `encodedAltitudes` and `encodedDistances` into `PathPoint(altitude, latLng, distance)`.
  • Verify that empty or missing altitude/distance strings handle gracefully with default fallback without throwing exceptions.
  2. Unit Tests for `WorkoutClustersViewModel` (`WorkoutClustersViewModelTest.kt`):
  • Verify `selectCluster(cluster)` updates `_clusterWorkouts` state with the retrieved workout list.
  • Verify `selectWorkoutForPeek(workoutId)` successfully finds the workout in `_clusterWorkouts.value`, triggers `workoutClusterRepository.getWorkoutWithTrack(workoutId)`, and updates `_peekedWorkoutDataWithTrack`.
  3. View Model & Integration Verification:
  • Verify that inspecting a workout in `WorkoutClustersFragment` triggers `trackOnMapViewModel.loadAftermathData(inspectedWorkout)`.
  • Verify that `TrackOnMapScreen` displays valid elevation bounds ($min < max$) matching the workout's altitude extrema, and renders extrema markers (`MIN_ALTITUDE`, `MAX_ALTITUDE`, etc.).
  4. Regression Verification:
  • Execute full clean-room unit test suite (`./gradlew testDebugUnitTest`) to verify zero regressions.

### TST-UI-092: **FastScrollbar Edge Alignment, Continuous Drag Accumulation & Action Non-Interference Verification** [FastScrollbar sits flush at the right edge; thumb dragging smoothly scrolls the list even with small finger movements; taps on card action buttons are never intercepted by the scrollbar overlay.]
- **Requirement(s)**: `REQ-UI-139`
- **Component**: **FastScrollbar Edge Alignment, Continuous Drag Accumulation & Action Non-Interference Verification**
- **Jira**: ``ATT-861`, `ATT-873`, `ATT-878``
- **Test Description**:
  1. *Component Unit & Math Verification (`FastScrollbarTest.kt`)*:
  • Verify continuous drag accumulator advances `dragProgress` and calculates valid target indices under fractional per-frame drag deltas ($\Delta_y \approx 2\dots 10\text{px}$), preventing integer truncation loss.
  • Verify boundary index clamping at 0 and $\text{totalItems}-1$.
  • Verify visual thumb offset matches `effectiveProgress` during dragging (`isDragging = true`).
  2. *Integration Verification in Workout List*:
  • In `WorkoutList`, verify `FastScrollbar` sits flush to the screen edge with zero end padding.
  • Verify tapping the 3-dots export menu button on any `WorkoutSummary` card opens the export menu cleanly.
  • Verify tapping the edit button on any `WorkoutSummary` card opens the editor cleanly.
  • Verify dragging the 28dp thumb at normal human finger speed smoothly scrolls the list from top to bottom.
  3. *Regression Verification*:
  • Execute full clean-room unit test suite (`./gradlew testDebugUnitTest`) to verify zero regressions.

### TST-UI-093: **Universal FastScrollbar Integration & Action Non-Interference Verification Across Collection Views** [All 7 collection views render the edge-aligned FastScrollbar when content overflows; dragging thumb scrolls each list smoothly; short lists hide the scrollbar automatically; all card actions and buttons remain 100% accessible; 0 test regressions.]
- **Requirement(s)**: `REQ-UI-140`
- **Component**: **Universal FastScrollbar Integration & Action Non-Interference Verification Across Collection Views**
- **Jira**: ``ATT-866`, `ATT-885`, `ATT-888``
- **Test Description**:
  1. *View Integration & Scroll Verification*:
  • In Periods (`PeriodList.kt`), verify `FastScrollbar` renders flush at the right edge when list exceeds viewport and scrolls smoothly upon dragging.
  • In Routes (`RouteList.kt`), verify `FastScrollbar` renders flush at the right edge and respects collapsing app bar insets.
  • In Favorite Tracks (`WorkoutClustersList.kt`), verify `FastScrollbar` renders in both clustered and unclustered lists and respects the 80dp FAB bottom padding.
  • In Segments (`SegmentList.kt`), verify `FastScrollbar` renders flush at the right edge and scrolls smoothly.
  • In Equipment (`EquipmentTabsScreen.kt`), verify `FastScrollbar` renders flush at the right edge and scrolls equipment items.
  • In Sport Types (`SportTypesTabsScreen.kt`), verify `FastScrollbar` renders flush at the right edge and scrolls sport types.
  • In Connected Devices (`DeviceListScreen.kt`), verify `FastScrollbar` renders flush at the right edge and scrolls devices.
  2. *Auto-Hiding Verification*:
  • For views with empty data or fewer items than viewport capacity, verify `FastScrollbar` remains hidden and does not alter layout padding.
  3. *Card Action Non-Interference Verification*:
  • Verify tapping interactive controls on cards across all 7 views (Route item toggle/delete, cluster card click, segment selection, equipment menu/stats, sport type edit/stats, device pair switch) functions immediately with zero touch interception.
  4. *Hardware & Regression Verification*:
  • Execute full clean-room unit test suite (`./gradlew testDebugUnitTest`): BUILD SUCCESSFUL (0 regressions).
  • Execute physical hardware verification on Google Pixel 10 (`66020DLCR002FL`): FastScrollbar thumb drags and list scrolls smoothly across Workouts, Periods, Routes, Sport Types, Equipment, and Clusters; auto-hiding confirmed on short lists.

### TST-UI-094: **Workout Summary Lap Overview, Rabbit/Hedgehog Highlights & Database Migration Verification** [Laps table migrates safely from v1 to v2 preserving historical records; batch loading eliminates N+1 queries; WorkoutLaps renders clean split table with rabbit (🐇) on fastest lap and hedgehog (🦔) on slowest lap; collapsible toggle works cleanly for >3 laps; 0 regressions.]
- **Requirement(s)**: `REQ-UI-141`
- **Component**: **Workout Summary Lap Overview, Rabbit/Hedgehog Highlights & Database Migration Verification**
- **Jira**: ``ATT-510`, `ATT-891`, `ATT-894`, `ATT-895``
- **Test Description**:
  1. *Database Migration & Data Preservation Unit Test (`LapsDatabaseManagerTest.kt`)*:
  • Initialize `Laps.db` at `DB_VERSION = 1` with sample laps for multiple workouts.
  • Trigger migration to `DB_VERSION = 2`: verify `onUpgrade` executes `ALTER TABLE` adding `name` and `description` without dropping the table.
  • Verify pre-existing lap records remain intact with correct durations, distances, and speeds, with `name = null` and `description = null`.
  • Insert a lap with custom `name = "Warmup"` and `description = "Easy tempo"`, and verify retrieval.
  • Test `getLaps(workoutId)` returns sorted laps for that workout.
  • Test `getLapsForWorkouts(workoutIds)` vectorized batch retrieval returns grouped map matching single queries without N+1 query overhead.
  2. *Data Layer & Mapper Unit Test (`WorkoutDataMapperLapsTest.kt`)*:
  • Verify `WorkoutDataMapper.fromCursor(cursor)` and `fromCursor(cursor, batchMetadata)` accurately populate `WorkoutData.laps`.
  3. *Compose UI Component Unit Test (`WorkoutLapsTest.kt`)*:
  • Render `WorkoutLaps` with 1 lap: verify lap renders with default title "Lap 1", metrics formatted, and no rabbit/hedgehog badges.
  • Render `WorkoutLaps` with 3 laps of differing speeds: verify fastest lap displays rabbit (🐇) badge and slowest displays hedgehog (🦔) badge.
  • Render `WorkoutLaps` with custom lap name: verify custom name is displayed.
  • Render `WorkoutLaps` with lap description: verify description is rendered as secondary text.
  • Render `WorkoutLaps` with 5 laps: verify only first 3 are rendered initially along with "Show all 5 laps" button; verify clicking expand renders all 5 laps and changes toggle to "Show fewer".
  • Verify empty laps list renders nothing.
  4. *Regression Verification*:
  • Execute full clean-room unit test suite (`./gradlew testDebugUnitTest`) to ensure zero regressions across all modules.

### TST-UI-095: **Interactive Lap Editing, Quick-Tag Presets, Sequential Navigation, and Full Workout Map Framing Verification** [Lap edit bottom sheet allows one-tap tag assignment, custom text entry, sequential lap navigation, and highlighted segment map visualization with stable camera bounds framing the full workout course; repository updates database and cache reactively; 0 regressions.]
- **Requirement(s)**: `REQ-UI-142`
- **Component**: **Interactive Lap Editing, Quick-Tag Presets, Sequential Navigation, and Full Workout Map Framing Verification**
- **Jira**: ``ATT-511`, `ATT-897`, `ATT-925`, `ATT-947``
- **Test Description**:
  1. *Geometry & Slicing Unit Test (`LapSegmentUtilsTest.kt`)*:
  • Given a polyline with 100 `LatLng` points and 100 corresponding cumulative distances ($0 \dots 1000\text{m}$).
  • Slicing for Lap 1 ($0 \dots 400\text{m}$): verify extracted sub-track contains points within $[0, 400\text{m}]$ and boundary anchor points.
  • Slicing for Lap 2 ($400 \dots 1000\text{m}$): verify extracted sub-track contains points within $[400, 1000\text{m}]$.
  • Verify `LatLngBounds` calculation correctly encloses the lap segment coordinates.
  • Verify `LatLngBounds` calculation correctly encloses the full workout coordinates (`allPoints`), calculating valid bounds spanning the entire route ($minLat \le maxLat$, $minLng \le maxLng$).
  • Verify empty polyline or 0-distance workouts gracefully return empty track and null bounds without throwing exceptions.
  2. *Compose UI & BottomSheet Unit Test (`LapEditBottomSheetTest.kt`)*:
  • Render `LapEditBottomSheet` with a lap: verify header displays formatted duration, distance, and speed.
  • Verify preset quick-tag chips (`Warm-up`, `Interval`, `Recovery`, etc.) are displayed.
  • Tap "Interval" chip: verify custom name field immediately updates to "Interval".
  • Enter custom description: verify description field captures text.
  • Sequential navigation:
  - For first lap (Lap 1 of 3): verify `< Previous` button is disabled, `Next >` button is enabled.
  - Tap `Next >`: verify callback or state transition advances to Lap 2 with pending changes saved.
  - For last lap (Lap 3 of 3): verify `Next >` button is disabled, `< Previous` button is enabled.
  • Map integration & Full Course Framing:
  - Verify map camera automatically calculates and animates to `workoutBounds` enclosing the entire workout session (`allPoints`), providing global spatial context.
  - Verify map polyline layering: full workout polyline rendered in muted neutral styling (`zIndex = 1f`), active lap segment rendered in prominent primary styling (`zIndex = 2f`), start and end markers pinned at lap boundaries (`zIndex = 3f`).
  - Verify sequential navigation updates the highlighted lap segment and markers while map camera framing remains stable over the entire course.
  - For indoor/zero-GPS session, verify map renders `NoGpsTrackCard` fallback placeholder without error.
  3. *Data Layer & In-Memory Update Unit Test (`WorkoutRepositoryLapUpdateTest.kt`)*:
  • Invoke `WorkoutRepository.updateLapDetails(workoutId, lapNr, name, description)`:
  - Verify `LapsDatabaseManager.updateLapDetails` is invoked with correct arguments.
  - Verify in-memory `WorkoutData.laps` is updated with new name and description.
  - Verify `WorkoutData` emitted by repository reflects updated lap details immediately without full list reload.
  4. *Regression Verification*:
  • Execute full clean-room unit test suite (`./gradlew testDebugUnitTest`) to ensure zero regressions across all modules.

### TST-UI-096: **Workout Summary Laps Table Header, Pace Single-Line & Column Width Verification** [Laps table in `WorkoutSummary` displays distinct headers styled with `labelSmall`, pace values never wrap across multiple lines due to column unit placement, lap names have expanded 1.6f width, badges are vertically centered, and 100% translation parity is preserved across all 9 locales with 0 regressions.]
- **Requirement(s)**: `REQ-UI-143`
- **Component**: **Workout Summary Laps Table Header, Pace Single-Line & Column Width Verification**
- **Jira**: ``ATT-920`, `ATT-931``
- **Test Description**:
  1. *Compose UI Component Unit Test (`WorkoutLapsTest.kt`)*:
  • *Header Row Presence & Localization*: Render `WorkoutLaps` with 1+ laps. Verify header row renders labels for Lap (`@string/lap_table_header_lap`), Time (`@string/lap_table_header_time`), Distance (`@string/lap_table_header_distance`), and sport-appropriate Pace/Speed with unit (e.g., `"Tempo [min/km]"` or `"Geschw. [km/h]"`).
  • *Numerical Formatting (No Cell Units)*: Verify pace/speed cell renders numerical string (`formatters.pace.format` / `formatters.speed.format`) without repeating unit text.
  • *Single-Line Constraint*: Verify time, distance, and pace texts specify `maxLines = 1` and `softWrap = false`.
  • *Proportional Widths*: Verify column weights (1.6f for name, 0.85f for time, 0.85f for distance, 1.1f for pace/speed, 26dp for badge) in both `LapTableHeader` and `LapRow`.
  • *Badge Centering*: Verify Rabbit (🐇) and Hedgehog (🦔) badges are positioned in the dedicated badge column with vertical centering.
  • *Empty Laps Guard*: Verify workouts with 0 laps render no header and no lap rows.
  2. *Localization Parity Test*: Verify string keys `lap_table_header_lap`, `lap_table_header_time`, `lap_table_header_distance`, and `pace_short` / `speed_short` exist and are translated across all 9 supported locales (values, values-de, values-es, values-fr, values-it, values-ja, values-nl, values-pl, values-pt).
  3. *Regression Verification*: Execute full clean-room unit test suite (`./gradlew testDebugUnitTest`) to ensure zero regressions across all modules.

### TST-UI-097: **Strava Best Efforts Collapsible Display, Metric Unit Filtering & Longest Distance Verification** [Collapsed best efforts view excludes miles when metric units are active; guarantees visibility of the longest distance time; displays PRs without duplication; expands in-place to all efforts; maintains 100% localization parity across all 9 locales with zero regressions.]
- **Requirement(s)**: `REQ-UI-144`
- **Component**: **Strava Best Efforts Collapsible Display, Metric Unit Filtering & Longest Distance Verification**
- **Jira**: ``ATT-883`, `ATT-974``
- **Test Description**:
  1. *Unit System Filtering Unit Tests (`StravaActivitySectionTest.kt`)*:
  • Under `MyUnits.METRIC`, verify that mile intervals (`"1/2 mile"`, `"1 mile"`, `"2 miles"`, `"10 miles"`) are excluded from the collapsed view.
  • Under `MyUnits.IMPERIAL`, verify that mile intervals remain eligible for the collapsed view.
  2. *Longest Distance & PR Aggregation Unit Tests (`StravaActivitySectionTest.kt`)*:
  • For an activity with 0 PRs, verify that the longest distance (e.g. 10k in a 10k run) is displayed in the collapsed view.
  • For an activity with an intermediate PR (e.g. 1k PR in a 10k run), verify that both 1k (PR) and 10k (longest) are displayed.
  • For an activity where the longest distance is a PR (e.g. 5k PR in a 5k run), verify that 5k is displayed exactly once without duplicates.
  • Verify that items in the collapsed view maintain natural ascending distance order.
  3. *Accordion State & Expansion Unit Tests (`StravaActivitySectionTest.kt`)*:
  • Verify that when `allEfforts.size > reducedEfforts.size`, accordion is collapsible with accurate hidden count ($N - M$).
  • Verify that when expanded (`isExpanded = true`), all $N$ best efforts (including miles) are returned.
  • Verify that when `allEfforts.size == reducedEfforts.size`, section is non-collapsible.
  4. *Localization Parity Test (`TranslationParityTest.kt`)*:
  • Verify `strava_show_all_best_efforts_format`, `strava_show_less_best_efforts_format`, and `strava_show_less_best_efforts` exist and pass format specifier validation across all 9 supported locales (values, values-de, values-es, values-fr, values-it, values-ja, values-nl, values-pl, values-pt).
  5. *Clean-Room Regression Verification*:
  • Execute full repository test suite (`./gradlew testDebugUnitTest`): BUILD SUCCESSFUL with 0 regressions.

### TST-UI-098: **UI Deletion of Old Unfinished Workouts & Active Workout Protection Verification** [Old unfinished workouts allow UI deletion via long-press in both full and compact views; live actively tracked workouts are strictly protected from deletion; unfinished workouts retain alpha = 0.5f; 100% test pass with 0 regressions.]
- **Requirement(s)**: `REQ-UI-145`
- **Component**: **UI Deletion of Old Unfinished Workouts & Active Workout Protection Verification**
- **Jira**: ``ATT-917`, `ATT-983``
- **Test Description**:
  1. *Active Tracking State Unit Tests (`TrainingApplicationTrackedTest.kt`)*:
  • When `cTrackingMode == TrackingMode.READY`, verify `TrainingApplication.isActivelyTracked(workoutId)` returns `false` for any workout ID.
  • When `cTrackingMode == TrackingMode.TRACKING` and `activeWorkoutId == 100L`, verify `isActivelyTracked(100L)` returns `true` and `isActivelyTracked(101L)` returns `false`.
  • When tracking stops (`stopTracking()`), verify `isActivelyTracked` resets to `false`.
  2. *WorkoutHeader & Summary Deletion Ergonomics Unit Tests (`WorkoutDeletionErgonomicsTest.kt`)*:
  • Render `WorkoutHeader` with `canDelete = true` (old unfinished or finished workout): verify `combinedClickable` attaches long-click action and triggers `onDeleteRequest()`.
  • Render `WorkoutHeader` with `canDelete = false` (actively tracked workout): verify long-click action is disabled / does not trigger `onDeleteRequest()`.
  • Verify 3-dots export menu is enabled only when `menuEnabled == true` (`finished == true`), preventing export of unfinished sessions.
  3. *WorkoutSummaryCompact Deletion Guard Unit Tests*:
  • For an old unfinished workout (`finished == false` and not actively tracked), verify "Delete" option is accessible in the context menu.
  • For an actively tracked workout (`isActivelyTracked == true`), verify "Delete" is disabled or hidden.
  4. *Clean-Room Regression Verification*:
  • Execute full clean-room unit test suite (`./gradlew testDebugUnitTest`): BUILD SUCCESSFUL (0 regressions).

### TST-UI-099: **Mark Old Unfinished Workout Finished & Active Workout Protection Verification** [Unfinished workouts allow marking as finished via context menu; active sessions are strictly protected; marking finished updates alpha to 1.0f, enables export, triggers periods rollup, and translates across all 9 locales; 0 regressions.]
- **Requirement(s)**: `REQ-UI-146`
- **Component**: **Mark Old Unfinished Workout Finished & Active Workout Protection Verification**
- **Jira**: ``ATT-987`, `ATT-989``
- **Test Description**:
  1. *Unit & State Transition Tests (`WorkoutMarkFinishedTest.kt`)*:
  • Given an unfinished workout (`finished == false`): verify `canMarkFinished == true` when idle (`isTracking() == false`).
  • Verify visual alpha is `0.5f` and export `menuEnabled` is `false`.
  • Trigger `markWorkoutFinished(workoutId)`: verify `finished` transitions to `true`.
  • In finished state: verify `contentAlpha == TTAlpha.High` (1.0f), export `menuEnabled == true`, and "Mark as finished" option is omitted.
  2. *Active Tracking Safety Guard Tests*:
  • When `isTracking() == true` and `workoutId == activeWorkoutId`: verify `canMarkFinished == false`, strictly preventing active workout from being marked finished via aftermath UI.
  • When `isTracking() == true` and `workoutId != activeWorkoutId`: verify `canMarkFinished == true` for an old unfinished session.
  3. *Period Integration & Notification Tests*:
  • Verify marking finished triggers `PeriodsRepository.onWorkoutFinished`, updating Day, Week, Month, and Year periods.
  4. *Localization Parity Test*:
  • Verify string key `mark_as_finished` exists and is translated across all 9 supported application locales (values, values-de, values-es, values-fr, values-it, values-ja, values-nl, values-pl, values-pt).
  5. *Clean-Room Regression Verification*:
  • Execute full clean-room unit test suite (`./gradlew testDebugUnitTest`): BUILD SUCCESSFUL (0 regressions).

### TST-UI-100: **Compact Workout Summary Context Menu Streamlining Verification** [Compact view context menu excludes "Edit workout" across all workout states, restricts items to lifecycle actions, disables context menu when actively tracked, and preserves expanded view editing with zero regressions.]
- **Requirement(s)**: `REQ-UI-147`
- **Component**: **Compact Workout Summary Context Menu Streamlining Verification**
- **Jira**: ``ATT-993`, `ATT-995``
- **Test Description**:
  1. *Unit & State Tests (`WorkoutSummaryCompactMenuTest.kt`)*:
  • *Idle Completed Workout*: Verify `canDelete == true`, `canMarkFinished == false`, `hasContextMenu == true`. Verify context menu contains only "Delete" and omits "Edit workout".
  • *Idle Unfinished Workout*: Verify `canDelete == true`, `canMarkFinished == true`, `hasContextMenu == true`. Verify context menu contains "Mark as finished" and "Delete", and omits "Edit workout".
  • *Actively Tracked Workout*: When `isActivelyTracked == true`, verify `canDelete == false`, `canMarkFinished == false`, and `hasContextMenu == false` (disabling long-press).
  2. *Full Summary Editing Invariant*: Verify `WorkoutSummary` / `WorkoutHeader` retains `onEditWorkout` parameter and continues to support edit interaction in expanded view.
  3. *Regression Verification*: Execute full clean-room unit test suite (`./gradlew testDebugUnitTest`): BUILD SUCCESSFUL with 0 regressions.

### TST-UI-101: **Edge-to-Edge Bottom Sheet Insets & Shared Core Components Verification** [`AppModalBottomSheet`, `AppTableHeader`, `AppTableTypography`, `MinimumDragHandle`, `FastScrollbar`, and `BadgeBox` provide clean, reusable core primitives; bottom sheets handle navigation bars reactively; `LapTableHeader` and `WorkoutExtrema` render tabular metrics with bottom-baseline alignment and uniform 26dp badge width; zero unit test regressions.]
- **Requirement(s)**: `REQ-UI-148`
- **Component**: **Edge-to-Edge Bottom Sheet Insets & Shared Core Components Verification**
- **Jira**: ``ATT-939`, `ATT-1025``
- **Test Description**:
  1. *Unit & Layout Verification (`CoreComponentsIntegrityTest.kt`, `AppModalBottomSheetTest.kt`, `AppTableHeaderTest.kt`, `FastScrollbarTest.kt`)*:
  • Verify `CoreComponentsIntegrityTest` confirms presence, public access, and contracts for all 6 core components (`AppModalBottomSheet`, `AppTableHeader`, `AppTableTypography`, `MinimumDragHandle`, `FastScrollbar`, `BadgeBox`).
  • Verify `AppModalBottomSheet` composable renders title, close button, drag handle, and content slot with `navigationBarsPadding()` and `imePadding()`.
  • Verify `AppTableHeader` and `AppTableTypography` render column headers with `includeFontPadding = false` and `LineHeightStyle(Alignment.Bottom, Trim.Both)`.
  • Verify `LapTableHeader` in `WorkoutLaps.kt` uses `AppTableHeader`, `BadgeSpacer`, and `BadgeBox` with column weights (1.6f, 0.85f, 0.85f, 1.1f, 26dp) and renders localized titles cleanly.
  • Verify `FastScrollbarTest` confirms 11 unit test scenarios for scroll calculations and progress accumulation.
  2. *Edge-to-Edge Insets Verification*:
  • In `RichStatsSheet.kt`, verify `navigationBarsPadding()` replaces hardcoded `padding(bottom = 32.dp)`.
  • On devices with 3-button navigation bars and gesture navigation bars, verify sheets anchor cleanly to the navigation bar without clipping or excessive bottom gap.
  3. *Regression Verification*:
  • Execute full clean-room unit test suite (`./gradlew testDebugUnitTest --no-daemon`) to ensure zero regressions across all modules.

### TST-UI-102: **Modernized Modal Bottom Sheet Dialogs & Selection Flows Verification** [All interactive configuration and selection popups render as bottom sheets anchored above the navigation bar; transient tracking HUD and destructive alerts remain centered dialogs; SharedPreferences state and callbacks are 100% preserved; zero regressions.]
- **Requirement(s)**: `REQ-UI-149`
- **Component**: **Modernized Modal Bottom Sheet Dialogs & Selection Flows Verification**
- **Jira**: ``ATT-900`, `ATT-1029`, `ATT-1031`, `ATT-1032``
- **Test Description**:
  1. *Unit & Contract Verification (`ModalBottomSheetDialogsIntegrityTest.kt`)*:
  • Verify `AppModalBottomSheet` supports both vector icon (`icon: ImageVector?`) and painter icon (`iconPainter: Painter?`) parameters in its composable signature.
  • Verify `ExportSettingsDialog`, `UnitsSettingsDialog`, `DisplaySettingsDialog`, and `ActivityTypeSelectionDialog` render as bottom popups utilizing `AppModalBottomSheet`.
  • Verify `SensorSourceDialog` and `DeviceTypeSelectionDialog` render as bottom popups utilizing `AppModalBottomSheet`.
  • Verify `EditSensorFieldDialog` and `ConfigureFilterDialog` render as bottom popups utilizing `AppModalBottomSheet`.
  • Verify `EditSportTypeDialog`, `EditEquipmentDialog`, and `EditDeviceDialog` render as bottom popups utilizing `AppModalBottomSheet`.
  • Verify `WorkoutClusterSelectionDialog`, `EditWorkoutClusterDialog`, and `ClusterInfoDialog` render as bottom popups utilizing `AppModalBottomSheet`.
  • Verify `ExportDetailsDialog` renders as a bottom popup utilizing `AppModalBottomSheet`.
  • Verify that `ExportSettingsDialog` preserves toggles for TCX, GPX, Golden Cheetah JSON, and CSV with `TrainingApplication` getters/setters.
  • Verify that `UnitsSettingsDialog` persists Metric vs Imperial selection to `SharedPreferences` under `SP_UNITS`.
  • Verify that `DisplaySettingsDialog` preserves `forcePortrait`, `keepScreenOn`, and `noUnlocking` toggles and synchronizes with `TrainingApplication.setDisplayOptions`.
  • Verify that `ActivityTypeSelectionDialog` displays sport type icons with un-tinted authentic colors (`REQ-UI-114`) and triggers `onTypeSelected`.
  • Verify that `SensorSourceDialog` displays active source, backups, and available devices, and triggers `onDeviceClick`.
  • Verify that `DeviceTypeSelectionDialog` lists protocol devices and handles `onSelected`.
  2. *Centered Dialog Invariant Verification*:
  • Verify `LapSummaryDialog` retains its centered 3-second auto-dismissing HUD presentation during active tracking.
  • Verify destructive confirmation dialogs (`DeleteConfirmationDialog`, `WorkoutDeleteDialog`, `DeleteOldWorkoutsDialog`), deletion progress dialogs (`WorkoutDeletionProgressDialog`), and system redirects (`GPSDisabledDialog`, `StartOrResumeDialog`) retain centered `AlertDialog` presentation.
  3. *Regression Verification*:
  • Execute full clean-room unit test suite (`./gradlew testDebugUnitTest --no-daemon`) to ensure zero regressions across all modules.

### TST-UI-103: **Standardized Bottom Sheet Action Layouts, Semantic Action Verbs & Route Dialog Verification** [Bottom popups provide uniform action bar layouts and semantic verb distinctions (Save vs. OK); route editing is integrated as an `AppModalBottomSheet`; cluster tuning in import conforms to design system; 100% test pass with zero regressions.]
- **Requirement(s)**: `REQ-UI-150`
- **Component**: **Standardized Bottom Sheet Action Layouts, Semantic Action Verbs & Route Dialog Verification**
- **Jira**: ``ATT-1034`, `ATT-1039``
- **Test Description**:
  1. *Unit & Layout Contract Verification (`DialogActionsIntegrityTest.kt` / `ModalBottomSheetDialogsIntegrityTest.kt`)*:
  • Verify `AppDialogActions.SaveCancel`, `AppDialogActions.Confirm`, and `AppDialogActions.CancelOnly` provide uniform structure, paddings, and button semantics in `ui.components.core`.
  • Verify data-storing sheets (`EditDeviceDialog`, `EditSensorFieldDialog`, `ConfigureFilterDialog`, `UnitsSettingsDialog`, `EditEquipmentDialog`, `EditSportTypeDialog`, `EditRouteDialog`, `ClusterTuningContent` / `ImportBackupTabsScreen`) bind primary action text to `R.string.save` ("Speichern").
  • Verify immediate-setting sheets (`ExportSettingsDialog`, `DisplaySettingsDialog`) bind primary action text to `android.R.string.ok` ("OK") and eliminate `R.string.Done`.
  • Verify informational sheets (`SensorSourceDialog`, `ExportDetailsDialog`, `ClusterInfoDialog`) bind action button to `android.R.string.ok` or `R.string.OK`.
  • Verify `EditRouteDialog` composes `AppModalBottomSheet` with `AppDialogActions.SaveCancel`, replacing full-screen `Scaffold` while preserving `RouteSummary` editing.
  • Verify `ImportBackupTabsScreen` Cluster-Parameter tuning sheet composes `AppModalBottomSheet` and `AppDialogActions.SaveCancel`, eliminating `OutlinedButton`.
  2. *Navigation & Callback Invariant Verification*:
  • Verify `EditRouteDialog` invokes `onSave` with updated route details and `onCancel` on dismiss.
  • Verify `UnitsSettingsDialog` persists selected unit to `SP_UNITS` on save and discards uncommitted selection on cancel.
  • Verify `EditDeviceDialog` calls `viewModel.saveChanges()` on save and discards on cancel.
  3. *Clean-Room Regression Verification*:
  • Execute full clean-room unit test suite (`./gradlew testDebugUnitTest --no-daemon`) to ensure zero regressions across all modules.

### TST-UI-104: **Workout Editing Modal Bottom Sheet & Viewport Layering Verification** [`EditWorkoutScreen` conforms to `AppModalBottomSheet` and `AppDialogActions.SaveCancel`; callers maintain active background layers beneath scrim; 100% tests pass.]
- **Requirement(s)**: `REQ-UI-151`
- **Component**: **Workout Editing Modal Bottom Sheet & Viewport Layering Verification**
- **Jira**: ``ATT-1043`, `ATT-1046``
- **Test Description**:
  1. *Unit & Reflection Contract Verification (`ModalBottomSheetDialogsIntegrityTest.kt`)*:
  • Verify `EditWorkoutDialog` (and `EditWorkoutScreen`) exists, is public, and composes `AppModalBottomSheet` with `AppDialogActions.SaveCancel`.
  • Verify primary action is bound to `@string/save` ("Speichern") and secondary action is bound to `@string/Cancel` ("Abbrechen").
  2. *ViewModel & Mutation Contract Verification*:
  • Verify `viewModel.saveChanges()` commits `workoutData` to `WorkoutRepository`.
  • Verify dismissing without tapping "Speichern" discards in-memory modifications without invoking `saveChanges()`.
  • Verify `EditWorkoutClusterDialog` integration retains cluster assignment, unassignment, and creation.
  3. *Caller Layering Integrity*:
  • Verify callers in `WorkoutSummariesListFragment`, `WorkoutSummariesTabbedFragment`, `PeriodsFragment`, and `WorkoutClustersFragment` preserve underlying screen composition while `selectedWorkoutIdForEdit != null` / `editedWorkoutId != null`.
  4. *Clean-Room Regression Verification*:
  • Execute full clean-room unit test suite (`./gradlew testDebugUnitTest --no-daemon`) ensuring zero regressions.

### TST-UI-105: **Dropbox Settings Modal Bottom Sheet & Navigation Integration Verification** [Dropbox settings conforms to `AppModalBottomSheet` and `AppDialogActions.SaveCancel`; drawer opens modal bottom sheet; credential capture and backup scheduling preserved; reactive connection state refresh on Import/Backup screen verified; 100% tests pass.]
- **Requirement(s)**: `REQ-UI-152`
- **Component**: **Dropbox Settings Modal Bottom Sheet & Navigation Integration Verification**
- **Jira**: ``ATT-1051`, `ATT-1053`, `ATT-4588``
- **Test Description**:
  1. *Unit & Reflection Contract Verification (`ModalBottomSheetDialogsIntegrityTest.kt`)*:
  • Verify `DropboxSettingsDialogKt` exists, is public, and exposes `@Composable fun DropboxSettingsDialog(onDismiss: () -> Unit)`.
  • Verify `DropboxSettingsDialogFragment` extends `androidx.fragment.app.DialogFragment` and provides `newInstance()`.
  2. *Authentication & Connection Logic Verification*:
  • Verify disconnected state renders `@string/dropbox_connect` and triggers `Auth.startOAuth2PKCE(...)` using recursive `Context.findActivity()` unwrapping to resolve `Activity` from `ContextThemeWrapper` [ATT-4588].
  • Verify connected state renders `@string/dropbox_connected_status` in green and provides `@string/dropbox_disconnect` calling `TrainingApplication.deleteDropboxCredential()` and `TrainingApplication.setUploadToDropbox(false)`.
  • Verify credential capture via `Auth.getDbxCredential()` is centralized in `DropboxSettingsDialog` `ON_RESUME` as single authority, immediately updating `isConnected`, with redundant consumption removed from `DropboxSettingsDialogFragment` [ATT-4588].
  • Verify `ImportBackupTabsScreen` refreshes `isDropboxConnected` and `isGoogleDriveConnected` on `Lifecycle.Event.ON_RESUME` [ATT-4588].
  • Verify redirect intent filter handling across co-installed package variants [ATT-4588].
  3. *Backup Configuration & Action Bar Verification*:
  • Verify automated backups toggle binds to `automated_backups` with summary `@string/automated_backups_summary`.
  • Verify backup interval selector binds to `backup_interval_days` (values 1, 3, 7, 30) from `@array/backup_interval_entries`.
  • Verify action bar uses `AppDialogActions.SaveCancel`: dismissing or tapping "Abbrechen" discards modifications; tapping "Speichern" commits preferences to `SharedPreferences` and triggers `BackupWorker.schedule(context)`.
  4. *Navigation Drawer Verification*:
  • In `MainActivityWithNavigation.kt`, verify `R.id.drawer_dropbox` and `onPreferenceStartScreen("cloudUpload")` launch `DropboxSettingsDialogFragment` without replacing the main screen fragment.
  5. *Clean-Room Regression Verification*:
  • Execute full clean-room unit test suite (`./gradlew testDebugUnitTest --no-daemon`) ensuring zero regressions.

### TST-UI-106: **Strava Settings Modal Bottom Sheet & Navigation Integration Verification** [Strava settings conforms to `AppModalBottomSheet` and `AppDialogActions.SaveCancel`; drawer opens modal bottom sheet directly; token revocation, sync triggers, and selective upload options preserved; 100% tests pass.]
- **Requirement(s)**: `REQ-UI-153`
- **Component**: **Strava Settings Modal Bottom Sheet & Navigation Integration Verification**
- **Jira**: ``ATT-1048`, `ATT-1058``
- **Test Description**:
  1. *Unit & Reflection Contract Verification (`ModalBottomSheetDialogsIntegrityTest.kt`)*:
  • Verify `StravaSettingsDialogKt` exists, is public, and exposes `@Composable fun StravaSettingsDialog(onDismiss: () -> Unit)`.
  • Verify `StravaSettingsDialogFragment` extends `androidx.fragment.app.DialogFragment` and provides `newInstance()`.
  2. *Authentication & Connection Logic Verification*:
  • Verify disconnected state renders `@string/strava_disconnected_status` and provides button initiating `StravaHelper.requestAccessToken(...)`.
  • Verify connected state renders `@string/strava_connected_status` in green and provides `@string/strava_disconnect` calling `TrainingApplication.deleteStravaToken()` and `StravaDeauthorizationThread`.
  • Verify loading state renders circular progress indicator with `@string/please_wait`.
  3. *Sync Actions & Selective Upload Verification*:
  • Verify manual sync actions render for equipment and routes, displaying formatted summaries from `TrainingApplication.getLastUpdateTimeOfStravaEquipment()` and `TrainingApplication.getLastUpdateTimeOfStravaRoutes()`.
  • Verify selective upload toggles bind to `uploadStravaGPS`, `uploadStravaAltitude`, `uploadStravaHR`, `uploadStravaPower`, `uploadStravaCadence`, defaulting to `true`.
  • Verify action bar uses `AppDialogActions.SaveCancel`: dismissing or tapping "Abbrechen" discards toggle modifications; tapping "Speichern" commits preferences to `SharedPreferences`.
  4. *Navigation Drawer & Preference Verification*:
  • In `MainActivityWithNavigation.kt`, verify `R.id.drawer_strava` and `onPreferenceStartScreen(TrainingApplication.PREFERENCE_SCREEN_STRAVA)` launch `StravaSettingsDialogFragment` without replacing the active fragment.
  5. *Clean-Room Regression Verification*:
  • Execute full clean-room unit test suite (`./gradlew testDebugUnitTest --no-daemon`) ensuring zero regressions.

### TST-UI-107: **Search Settings Modal Bottom Sheet & Navigation Integration Verification** [Search settings conforms to `AppModalBottomSheet` and `AppDialogActions.SaveCancel`; drawer opens modal bottom sheet directly; search triggers, behavior options, and retry slider preserved; 100% tests pass.]
- **Requirement(s)**: `REQ-UI-154`
- **Component**: **Search Settings Modal Bottom Sheet & Navigation Integration Verification**
- **Jira**: ``ATT-1044`, `ATT-1063``
- **Test Description**:
  1. *Unit & Reflection Contract Verification (`ModalBottomSheetDialogsIntegrityTest.kt`)*:
  • Verify `SearchSettingsDialogKt` exists, is public, and exposes `@Composable fun SearchSettingsDialog(onDismiss: () -> Unit)` with single dismiss parameter.
  • Verify `SearchSettingsDialogFragment` extends `androidx.fragment.app.DialogFragment` and provides `newInstance()`.
  2. *Search Preferences & Staging Verification*:
  • Verify search tries slider renders with min 1, max 5, steps 3, bound to `numberOfSearchTriesInt` with numeric text label.
  • Verify trigger event switches bind to `startSearchWhenAppStarts` (default true), `startSearchWhenResumeFromPaused` (default true), `startSearchWhenUserChangesSport` (default true), and `startSearchWhenTrackingStarts` (default false).
  • Verify behavior switches bind to `searchOnlyForSportSpecificDevices` (default true) and `changeSportWhenDeviceGetsLost` (default true) with title and summary.
  • Verify action bar uses `AppDialogActions.SaveCancel`: dismissing or tapping "Abbrechen" discards modifications; tapping "Speichern" commits all 7 preferences to `SharedPreferences`.
  3. *Navigation Drawer & Preference Verification*:
  • In `MainActivityWithNavigation.kt`, verify `R.id.drawer_search_settings` and `onPreferenceStartScreen("search_settings")` launch `SearchSettingsDialogFragment` without replacing the active fragment.
  4. *Clean-Room Regression Verification*:
  • Execute full clean-room unit test suite (`./gradlew testDebugUnitTest --no-daemon`) ensuring zero regressions.

### TST-UI-108: **Legacy Fragment Retirement, Unused Layout Purge & Resource Cleanup Verification** [Obsolete fragments, 59 dead layout XML files, and unreferenced strings purged; active layouts preserved; 100% clean-room test pass with zero regressions.]
- **Requirement(s)**: `REQ-UI-155`
- **Component**: **Legacy Fragment Retirement, Unused Layout Purge & Resource Cleanup Verification**
- **Jira**: ``ATT-1018`, `ATT-1068``
- **Test Description**:
  1. *Static Analysis & Source Code Verification*:
  • Verify `CloudUploadFragment.kt`, `SearchSettingsFragment.kt`, and `StravaUploadFragment.kt` are deleted from the codebase.
  • Verify `MainActivityWithNavigation.kt` contains zero unused imports for the deleted fragments.
  • Verify `StarredSegmentsFragment.kt` launches `StravaSettingsDialogFragment.newInstance()` and contains zero references to `StravaUploadFragment`.
  2. *Layout Integrity & Resource Audit*:
  • Verify all 59 obsolete View-based layout files are deleted from `app/src/main/res/layout/`.
  • Verify the 9 actively required layout files (`check_ant_installation_dialog.xml`, `export_notification__expanded.xml`, `export_notification__group.xml`, `main_activity_with_navigation.xml`, `main_activity_without_navigation.xml`, `pebble_config_list_5.xml`, `preference_category.xml`, `preference_slim.xml`, `tabbed_config_views.xml`) remain present and uncorrupted.
  • Verify unreferenced string resources in `res/values/strings.xml` and translation files associated with deleted layouts are cleanly purged without breaking active string resolution.
  3. *Automated Unit & Reflection Tests*:
  • Run `./gradlew testDebugUnitTest --no-daemon` to confirm 100% clean-room test pass with 0 regressions across all test suites.
  • Run `ModalBottomSheetDialogsIntegrityTest` to ensure all modernized bottom sheet and dialog contracts remain intact.
  4. *Device Deployment & Runtime Sanity*:
  • Deploy debug APK to Google Pixel 10 (`./gradlew installDebug`) and verify clean launch without `InflateException` or missing resource crashes.

### TST-UI-109: **Bottom Popup Single-Window BottomSheetDialogFragment, Decor & Zero-Flicker Dismiss Verification** [All 7 bottom popup dialog fragments inherit from `AppBottomSheetDialogFragment` (extending `BottomSheetDialogFragment`); theme overlay correctly configures single-window edge-to-edge transparent system bars; status bar remains transparent with single scrim; dismissal is completely free of black flickering; 100% of unit tests pass with zero regressions.]
- **Requirement(s)**: `REQ-UI-156`
- **Component**: **Bottom Popup Single-Window BottomSheetDialogFragment, Decor & Zero-Flicker Dismiss Verification**
- **Jira**: ``ATT-1072`, `ATT-1080``
- **Test Description**:
  1. *Theme Overlay & Resource Verification (`themes.xml`)*:
  • Verify `ThemeOverlay.aTrainingTracker.BottomSheetDialogFragment` is declared in `res/values/themes.xml` inheriting from `ThemeOverlay.MaterialComponents.BottomSheetDialog`.
  • Verify `android:windowDrawsSystemBarBackgrounds` is set to `true`.
  • Verify `android:navigationBarColor` and `android:statusBarColor` are `@android:color/transparent`.
  • Verify `android:enforceNavigationBarContrast` and `android:enforceStatusBarContrast` are `false`.
  • Verify `android:windowLightNavigationBar` and `android:windowLightStatusBar` are `true`.
  • Verify `android:windowBackground` is `@android:color/transparent`.
  2. *Base Class & Inheritance Reflection Verification (`ModalBottomSheetDialogsIntegrityTest.kt`)*:
  • Verify `com.atrainingtracker.trainingtracker.ui.components.core.AppBottomSheetDialogFragment` exists, is abstract, and inherits directly from `com.google.android.material.bottomsheet.BottomSheetDialogFragment`.
  • Verify all 7 dialog fragments (`SearchSettingsDialogFragment`, `ExportSettingsDialogFragment`, `UnitsSettingsDialogFragment`, `StravaSettingsDialogFragment`, `DropboxSettingsDialogFragment`, `ActivityTypeSelectionDialogFragment`, `DisplaySettingsDialogFragment`) inherit directly from `AppBottomSheetDialogFragment`.
  • Verify that `newInstance()` and `TAG` contracts remain present and public on all 7 companion objects.
  3. *Single-Window Architecture Verification*:
  • Verify that the Compose layout inside the 7 dialogs renders content directly within the single `BottomSheetDialog` window without spawning a nested Compose `ModalBottomSheet`.
  4. *Automated Unit Test Suite*:
  • Run `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.components.core.ModalBottomSheetDialogsIntegrityTest"`.
  • Run `./gradlew testDebugUnitTest` to confirm 100% clean-room test pass with 0 regressions across all modules.
  5. *Physical Device Verification (Pixel 10, Android 17 / API 37)*:
  • Deploy debug APK to Google Pixel 10 (`./gradlew installDebug`).
  • Open each bottom popup from navigation drawer (Display, Units, Export, Search, Strava, Dropbox) and tracking screen (Activity Type).
  • Verify top status bar displays the underlying activity dimmed by a single scrim (never solid black).
  • Dismiss each popup via system back button, navigation gesture, scrim tap, and close button: verify 100% transparent navigation bar with zero black flicker.

### TST-UI-110: **Advanced Interval, Tab-Aware Sport & Sport-Aware Equipment Workout Filtering Verification** [Workouts are accurately filtered by date interval, distance interval, and duration interval; sport type selector displays sports contextual to the active tab; equipment selector displays equipment contextual to the active sport/tab; JSON serialization is backward-compatible; 100% unit tests pass with zero regressions.]
- **Requirement(s)**: `REQ-UI-157`
- **Component**: **Advanced Interval, Tab-Aware Sport & Sport-Aware Equipment Workout Filtering Verification**
- **Jira**: ``ATT-882`, `ATT-1103`, `ATT-1108``
- **Test Description**:
  1. *Filter Criteria Interval Evaluation Unit Tests (`WorkoutFilterCriteriaTest.kt`)*:
  • Distance interval filtering: verify workout with distance below `minDistanceMeters` or above `maxDistanceMeters` is rejected; workouts within $[D_{\text{min}}, D_{\text{max}}]$ match.
  • Duration interval filtering: verify workout with duration below `minDurationSec` or above `maxDurationSec` is rejected; workouts within $[T_{\text{min}}, T_{\text{max}}]$ match.
  • Unbounded intervals: verify only `minDistanceMeters` set behaves as $\ge D_{\text{min}}$, only `maxDistanceMeters` set behaves as $\le D_{\text{max}}$, etc.
  • Serialization test: verify JSON round-trip preserves all fields including `maxDistanceMeters` and `maxDurationSec`, and legacy JSON without max fields parses with null bounds.
  • Active filter count calculation test: verify distance interval, duration interval, and date interval count towards active filters.
  2. *Tab-Aware Sport Selection Logic Verification*:
  • Verify `availableSports` filtering logic when `activeBSportType` is `BSportType.BIKE`, `BSportType.RUN`, `BSportType.UNKNOWN` (Other), or `null` (All).
  3. *Sport-Aware Equipment Selection Logic Verification*:
  • Verify `availableEquipment` filtering logic when filtering by `activeBSportType` (Bike, Run, Other, All) and `localSportId`.
  • Verify automatic clearing (`localEquipId = null`) when active equipment does not belong to the selected sport/tab.
  4. *Filter Bottom Sheet Component Verification*:
  • Verify interval input controls for distance, duration, date pickers, and reactive chip state.
  5. *Clean-Room Regression Verification*:
  • Execute full clean-room unit test suite (`./gradlew testDebugUnitTest`) to ensure zero regressions across all modules.

### TST-UI-111: **Retired Equipment Isolation in Pickers, Visual Identification & Equipment Lifecycle Management Test** [Retired equipment is hidden from workout pickers unless assigned to the current workout; retired equipment is excluded from auto-inference; equipment manager displays visual retired indicators and grouping; manual retirement toggle functions properly; historical workouts preserve equipment name and icon with zero regressions.]
- **Requirement(s)**: `REQ-UI-158`
- **Component**: **Retired Equipment Isolation in Pickers, Visual Identification & Equipment Lifecycle Management Test**
- **Jira**: ``ATT-1118``
- **Test Description**:
  1. *Unit Test `EquipmentData` and `EquipmentDbHelper`*:
  • Verify `getEquipmentItems()` maps `isRetired` from the `Retired` column.
  • Verify `updateEquipment` and `setEquipmentRetired` persist changes to `Retired`.
  2. *Unit Test `EditWorkoutViewModel`*:
  • Verify active equipment list filters out retired items for workouts without equipment or with active equipment.
  • Verify when workout has an existing retired equipment assigned, that specific retired equipment is retained in `_equipmentNames` alongside active items.
  • Verify expansion functions (`showAllBikes`, `showAllShoes`) exclude retired equipment except when assigned.
  3. *Unit Test `EquipmentAndSportTypeDiscoveryManager`*:
  • Verify `getLinkedEquipmentIds` and `resolveIdentity` exclude equipment marked `isRetired == true`.
  4. *Unit Test `SportTypeViewModel`*:
  • Verify `availableEquipment(bSportType)` returns only active equipment.
  5. *Unit Test `EquipmentViewModel`*:
  • Verify `loadEquipment` populates `EquipmentItem.isRetired`.
  • Verify updating equipment persists `isRetired` to database.
  6. *UI Component Verification*:
  • Verify `EditEquipmentDialog` contains retirement toggle switch and emits updated item.
  • Verify `EquipmentTabsScreen` renders retired badge and handles sectioning/collapsible state.
  7. *Clean-Room Regression Verification*:
  • Execute full clean-room unit test suite (`./gradlew testDebugUnitTest`) to ensure zero regressions across all modules.

### TST-UI-112: **Equipment ViewModel Automated Initialization & Reactive Sync Test** [EquipmentViewModel self-initializes immediately, reacts to Strava sync completion, deduplicates concurrent load requests, and displays items in Compose navigation with 0 test failures.]
- **Requirement(s)**: `REQ-UI-160`
- **Component**: **Equipment ViewModel Automated Initialization & Reactive Sync Test**
- **Jira**: ``ATT-1309``
- **Test Description**:
  1. *Unit Test Automated Initialization*:
  • In `EquipmentViewModelLifecycleTest`, verify that instantiating `EquipmentViewModel` triggers `loadEquipment()` and populates `bikes` and `shoes` StateFlows asynchronously without requiring manual invocation.
  2. *Unit Test Reactive Falling-Edge Sync*:
  • Verify that transitioning `syncStatusFlow` from `false` to `true` and back to `false` triggers a database reload.
  • Verify that the initial `false` emission on `syncStatusFlow` does NOT cause duplicate queries on cold start.
  3. *Unit Test Coroutine Job Deduplication*:
  • Verify that rapid successive calls to `loadEquipment()` cancel/coalesce pending jobs and preserve StateFlow consistency.
  4. *Clean-Room Regression Verification*:
  • Execute `./gradlew testDebugUnitTest` and verify 0 regressions across all modules.

### TST-UI-113: **ModalNavigationDrawer Edge-Swipe Restriction & Map Panning Test** [Full-screen drag gestures outside the 40dp margin never open the drawer; map panning operates unhindered; edge-swiping, scrim tap, back press, and hamburger icon open/close the drawer reliably with 0 test failures.]
- **Requirement(s)**: `REQ-UI-161`
- **Component**: **ModalNavigationDrawer Edge-Swipe Restriction & Map Panning Test**
- **Jira**: ``ATT-1310``
- **Test Description**:
  1. *Unit Test Navigation Drawer Gesture State Mapping (`SingleActivityNavigationTest.kt`)*:
  • Verify that when `drawerState` is in `Closed` value, `gesturesEnabled` evaluates to `false`.
  • Verify that when `drawerState` is in `Open` value, `gesturesEnabled` evaluates to `true`.
  • Verify that the edge threshold invariant for swipe-to-open remains bounded to 40dp (`40.dp`).
  2. *Clean-Room Full Suite Regression*:
  • Execute `./gradlew testDebugUnitTest` and verify 0 failures / 0 regressions across all modules.
  3. *Physical Device Verification*:
  • On `MapScreen`, pan map in all directions from mid-screen ($x > 40\text{dp}$); verify map pans freely and drawer never opens.
  • On `WorkoutDetailsScreen`, swipe right from mid-screen ($x > 40\text{dp}$); verify drawer remains closed.
  • Swipe right from extreme left edge ($x \le 40\text{dp}$); verify drawer opens smoothly.
  • While open, swipe left or tap scrim; verify drawer closes smoothly.

### TST-UI-114: **ModalNavigationDrawer Back Navigation Isolation & State Synchronization Test** [Pressing Back with drawer open exclusively dismisses the drawer without altering the current screen; pressing Back with drawer closed navigates to main tracking control; dynamic LIFO composition guarantees NavHost preemption; rapid double-back presses and cancellation exceptions are handled without route jumps; 400ms timeout prevents UI deadlocks; 0 unit test regressions.]
- **Requirement(s)**: `REQ-UI-162`
- **Component**: **ModalNavigationDrawer Back Navigation Isolation & State Synchronization Test**
- **Jira**: ``ATT-1335``
- **Test Description**:
  1. *Unit Test Navigation Drawer Controller Single Source of Truth (`SingleActivityNavigationTest.kt`)*:
  • Verify that `NavigationDrawerController` properly delegates `openDrawer()`, `closeDrawer()`, and `isDrawerOpen` when bound to an authoritative state provider.
  • Verify backward-compatible fallback for `isDrawerOpen` before binding (clean default without NPE).
  2. *Unit Test Dynamic LIFO Evaluation Precedence (`SingleActivityNavigationTest.kt`)*:
  • In `OnBackPressedDispatcher`, register a simulated `NavHost` destination back callback (`isEnabled = true`).
  • Verify that with drawer closed (no drawer callback attached), `onBackPressed()` triggers `NavHost` destination popping.
  • Simulate drawer opening: attach conditional drawer `BackHandler` callback.
  • Verify that invoking `onBackPressed()` triggers drawer closure exclusively; `NavHost` callback invocation count remains unchanged.
  • Simulate drawer settled closed: remove drawer `BackHandler` callback. Verify invoking `onBackPressed()` again triggers `NavHost` destination popping.
  3. *Unit Test Comprehensive Visibility Predicate (`SingleActivityNavigationTest.kt`)*:
  • Verify `isDrawerVisible` under all animation phases (`currentValue`, `targetValue`, and `isAnimationRunning`), confirming the handler remains mounted during opening and closing transitions.
  4. *Rapid Double-Back Press & CancellationException Resilience Test (`SingleActivityNavigationTest.kt`)*:
  • Verify that while the drawer is transitioning (`targetValue == Open` or animation in flight), `isDrawerVisible` remains `true`, keeping Layer 3 disabled and preventing double-press fall-through to screen navigation.
  • Verify that calling `close()` during an active opening animation handles `CancellationException` gracefully without crashing or dropping frames.
  5. *Deadlock Safety Timeout & Starvation Simulation Test (`SingleActivityNavigationTest.kt`)*:
  • Simulate animation delay exceeding the 400ms safety threshold; verify timeout fallback executes `snapTo(DrawerValue.Closed)`, immediately unblocking Layer 3 screen navigation and preventing UI deadlocks (INV-UI-04).
  6. *Clean-Room Full Suite Regression*:
  • Execute `./gradlew testDebugUnitTest` and verify 0 failures across all 630+ unit tests.
  7. *Physical Device Verification*:
  • On `Workouts` (or any non-tracking screen), open navigation drawer (via edge-swipe or hamburger).
  • Press Android Back button/gesture: verify drawer dismisses smoothly and the screen remains on `Workouts` (does NOT navigate to tracking).
  • Press Back again with drawer closed: verify app navigates to main tracking control screen.
  • While drawer is opening, perform rapid double-back press: verify drawer closes cleanly without unexpected screen route changes.

### TST-UI-115: **Cockpit Tab Navigation on Tracking Initiation & Origin-Aware Resume Neutrality Verification** [Tapping Start Tracking or resuming a manual pause from Page 0 automatically and smoothly scrolls the pager to Cockpit Page 1; ending Auto-Pause and resuming manual pause from Page >= 1 strictly preserves the active tab without scrolling; cold starts and rotations preserve the active tab; 0 unit test regressions.]
- **Requirement(s)**: `REQ-UI-163`
- **Component**: **Cockpit Tab Navigation on Tracking Initiation & Origin-Aware Resume Neutrality Verification**
- **Jira**: ``ATT-1340`, `ATT-4373``
- **Test Description**:
  1. *Unit Test Rising-Edge Start Navigation (`TrackingTabsViewModelTest.kt`)*:
  • *Given* `trackingMode` in `TrackingMode.READY` and `TrackingTabsViewModel` initialized,
  • *When* `trackingMode` transitions to `TrackingMode.TRACKING`,
  • *Then* verify that `navigationEvent` emits `TabNavigationEvent.NavigateTo(0)`.
  2. *Unit Test Auto-Pause Resume Neutrality (`TrackingTabsViewModelTest.kt`)*:
  • *Given* an active tracking session entering Auto-Pause (`TrackingMode.PAUSED` with `wasAutoPaused == true`),
  • *When* tracking resumes (`TrackingMode.TRACKING`),
  • *Then* verify that `navigationEvent` emits zero navigation events, preserving the current tab regardless of whether `currentPage` is 0 or >= 1.
  3. *Unit Test Manual Resume from Control Tab (`Page 0`) (`TrackingTabsViewModelTest.kt`)*:
  • *Given* an active tracking session in manual pause (`wasAutoPaused == false`) with `currentPage == 0`,
  • *When* tracking resumes (`TrackingMode.TRACKING`),
  • *Then* verify that `navigationEvent` emits `TabNavigationEvent.NavigateTo(0)`, smoothly animating to Page 1.
  4. *Unit Test Manual Resume from Telemetry/Map Tab (`Page >= 1`) (`TrackingTabsViewModelTest.kt`)*:
  • *Given* an active tracking session in manual pause (`wasAutoPaused == false`) with `currentPage == 2` (or >= 1),
  • *When* tracking resumes (`TrackingMode.TRACKING`),
  • *Then* verify that `navigationEvent` emits zero navigation events, strictly preserving the active telemetry/map tab.
  5. *Unit Test Cold Start & Recreation Neutrality (`TrackingTabsViewModelTest.kt`)*:
  • *Given* `TrackingTabsViewModel` initialized while `trackingMode` is already `TRACKING`,
  • *When* collectors subscribe,
  • *Then* verify that zero navigation events are emitted.
  6. *Unit Test Multiple Start/Stop Cycles (`TrackingTabsViewModelTest.kt`)*:
  • *Given* multiple successive start/stop tracking cycles (`READY -> TRACKING -> READY -> TRACKING`),
  • *When* each start transition occurs,
  • *Then* verify that `TabNavigationEvent.NavigateTo(0)` is emitted on every start without suppression.
  7. *Unit Test Pager Offset & State Synchronization (`TrackingTabsViewModelTest.kt`)*:
  • *Given* `ScreenMode.TRACKING`,
  • *When* receiving `TabNavigationEvent.NavigateTo(0)`,
  • *Then* verify target page calculates to `1` (`0 + offset`) and pins `lastKnownPage` to `1`.
  8. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` and verify 100% test pass rate with 0 failures across all unit tests.
  9. *Physical Device Verification*:
  • On physical device, start tracking from Page 0 and verify smooth animation to Page 1 cockpit tab; verify resuming manual pause from Page 0 navigates to Page 1; verify ending Auto-Pause while on Map or secondary tab preserves the active tab with zero scroll.

### TST-UI-116: **Accessibility Event, NodeInfo & Manager Api34 Compatibility & LinkageError Resilience Verification** [Missing Android 14 (API 34) accessibility platform methods and fields are safely absorbed via LinkageError try-catch guards without throwing NoSuchFieldError/NoSuchMethodError or swallowing OutOfMemoryError; native execution preserved on intact devices; 0 unit test regressions.]
- **Requirement(s)**: `REQ-UI-164`
- **Component**: **Accessibility Event, NodeInfo & Manager Api34 Compatibility & LinkageError Resilience Verification**
- **Jira**: ``ATT-1347`, `ATT-2302`, `ATT-2584`, `ATT-2767``
- **Test Description**:
  1. *Unit Test `AccessibilityNodeInfoCompat$Api34Impl` Missing Field & Blanket Shield Resilience (`AccessibilityNodeInfoCompatResilienceTest.kt`)*:
  • Verify that when `AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_IN_DIRECTION` throws `NoSuchFieldError`, `Api34Impl.getActionScrollInDirection()` catches `LinkageError`, logs a warning to logcat, and returns `null` safely.
  • Verify that when `ACTION_SCROLL_IN_DIRECTION` is intact, it returns the native action object cleanly.
  • Verify that when remaining API 34 methods (`getContainerTitle`, `setContainerTitle`, `getBoundsInWindow`, `setBoundsInWindow`, `hasRequestInitialAccessibilityFocus`, `setRequestInitialAccessibilityFocus`, `getMinDurationBetweenContentChanges`, `setMinDurationBetweenContentChanges`, `setQueryFromAppProcessEnabled`) encounter missing platform methods, they catch `LinkageError` and return safe defaults without crashing.
  • Verify that non-linkage fatal JVM errors (`OutOfMemoryError`) are NOT caught and propagate up.
  2. *Unit Test `AccessibilityManagerCompat$Api34Impl` Missing Method Resilience (`AccessibilityManagerCompatResilienceTest.kt`)*:
  • Verify that when `AccessibilityManager.isRequestFromAccessibilityTool()` throws `NoSuchMethodError`, `Api34Impl.isRequestFromAccessibilityTool(manager)` catches `LinkageError`, logs a warning to logcat, and returns `false` safely.
  • Verify that when `AccessibilityManager.isRequestFromAccessibilityTool()` is intact, it returns the native boolean result cleanly.
  • Verify that non-linkage fatal JVM errors (`OutOfMemoryError`) are NOT caught and propagate up.
  3. *Unit Test `AccessibilityWindowInfoCompat$Api34Impl` Missing Method Resilience (`AccessibilityManagerCompatResilienceTest.kt`)*:
  • Verify that when `AccessibilityWindowInfo.getTransitionTimeMillis()` throws `NoSuchMethodError`, `Api34Impl.getTransitionTimeMillis(window)` catches `LinkageError`, logs a warning, and returns `0L`.
  • Verify that when `AccessibilityWindowInfo.getLocales()` throws `NoSuchMethodError`, `Api34Impl.getLocales(window)` catches `LinkageError`, logs a warning, and returns `null`.
  4. *Unit Test `AccessibilityNodeInfoCompat$Api34Impl` Data Sensitivity Resilience (`AccessibilityNodeInfoCompatResilienceTest.kt`)*:
  • Verify that when `AccessibilityNodeInfo.setAccessibilityDataSensitive(boolean)` or `isAccessibilityDataSensitive()` throws `NoSuchMethodError`, `Api34Impl` catches `LinkageError` and logs a warning without crashing.
  5. *Targeted Dependency Substitution & Build Parity Verification*:
  • Verify that `settings.gradle` and `app/build.gradle` resolve `core-1.15.0-patched.aar` cleanly.
  6. *Clean-Room Regression Suite Execution*:
  • Execute `./gradlew testDebugUnitTest` and verify 0 failures across all modules.

### TST-UI-216: **Reactive Bi-Directional Equipment-to-Sensor Synchronization and Cache Invalidation Test** [Real-time bi-directional synchronization between equipment mapping mutations and sensor repositories via reactive SharedFlow without stale UI caching; 100% test pass rate.]
- **Requirement(s)**: `REQ-UI-257`
- **Component**: **Reactive Bi-Directional Equipment-to-Sensor Synchronization and Cache Invalidation Test**
- **Jira**: ``ATT-2307``
- **Test Description**:
  1. *DeviceDataRepository Reactive Refresh Test (`testEquipmentLinksChanged_notifiesDeviceDataRepository_refreshesAffectedDevice`)*:
  • Mock `DeviceDataRepository` with initial cached device.
  • Emit affected device ID to `EquipmentRepository.equipmentLinksChanged`.
  • Verify `refreshDeviceFromDb(deviceId)` is executed and `allDevices` emits updated `DeviceUiData` with refreshed `linkedEquipment`.
  2. *EquipmentDbHelper Mutation Dispatch Test (`testEquipmentDbHelper_linkMutations_emitEquipmentLinksChanged`)*:
  • Verify that executing `addDeviceLink`, `removeDeviceLink`, and `setEquipmentLinks` dispatches `notifyEquipmentLinksChanged` with the target device ID.
  3. *EditDeviceViewModel Fresh Snapshot Test (`testEditDeviceViewModel_loadInitialDeviceData_alwaysFetchesFreshSnapshot`)*:
  • Initialize `EditDeviceViewModel` with device ID; update repository snapshot; invoke `loadInitialDeviceData(deviceId)`.
  • Verify `deviceSnapshot` updates to the fresh repository data without stale caching.
  4. *EquipmentViewModel Bi-Directional Refresh Test (`testEquipmentViewModel_reloadsEquipment_onExternalLinksChanged`)*:
  • Initialize `EquipmentViewModel`; emit event to `EquipmentRepository.equipmentLinksChanged`.
  • Verify `loadEquipment()` is invoked reactively, refreshing `_bikes` and `_shoes`.
  5. *Clean-Room Regression Suite*:
  • Execute `./gradlew testDebugUnitTest` to guarantee 0 regressions across all test suites.

### TST-UI-117: **Known Start Locations Management, Interactive Map Geofences & Altitude Lock Verification** [Known start locations management interface provides complete user sovereignty over elevation reference points; human-readable place names are resolved via Geocoder and editable in UI; dual-perspective tabs render cards and 200m geofences; manual edits automatically lock altitude and update source to MANUAL_USER without UI toggle friction; DEM fetch retrieves online elevation; hitCount increments on repeat starts while locked altitude remains immutable; single-thread SQLite dispatcher prevents coroutine deadlocks; 100% 9-language localization parity; 0 regressions.]
- **Requirement(s)**: `REQ-UI-165`
- **Component**: **Known Start Locations Management, Interactive Map Geofences & Altitude Lock Verification**
- **Jira**: ``ATT-919`, `ATT-1379``
- **Test Description**:
  1. *Drawer Navigation & Route Integration Verification*:
  • Verify `AppNavigationDrawer` declares `R.id.drawer_start_locations` under `drawer__maps` routing to `NavRoutes.START_LOCATIONS`.
  • Verify tapping the drawer item navigates to `KnownLocationsScreen` and closes the drawer.
  2. *ViewModel & Repository Concurrency Unit Tests (`KnownLocationsViewModelTest.kt`)*:
  • Verify `KnownLocationsRepository` executes all SQLite operations on the dedicated single-thread dispatcher (`KnownLocationsDB-Thread`).
  • Test `updateLocation(locationId, name, altitude, source)`: verify manual edits to name and altitude persist atomically to SQLite with `source = MANUAL_USER` and `is_locked = 1`.
  • Test `deleteLocation(locationId)`: verify record is removed from SQLite and StateFlow refreshes cleanly.
  3. *Location Reverse Geocoding & Auto-Naming Test (`LocationNameResolverTest.kt`)*:
  • Mock `Geocoder` with coordinates (48.137, 11.576): verify resolver returns human-readable place name (e.g. "München").
  • Test offline/error fallback: verify resolver returns localized coordinate format (`@string/known_location_unnamed_format`).
  • Test custom name preservation: verify existing non-placeholder names are never overwritten by automated resolution.
  4. *Unit Conversion & Precision Round-Trip Unit Tests*:
  • Metric input: verify entering `"525,5"` or `"525.5"` in Metric mode parses to `525.5` meters with 0.1m precision.
  • Imperial conversion: verify entering `1000` feet in Imperial mode converts to $1000 \times 0.3048 = 304.8$ meters, and re-displaying in Imperial rounds to $\text{round}(304.8 / 0.3048) = 1000$ feet.
  • Fractional Imperial conversion: verify $h_{\text{m}} = \text{round}(h_{\text{ft}} \times 0.3048 \times 10.0) / 10.0$ preserves mathematical monotonicity and stability.
  5. *Compose UI Dual-Perspective & Card Interaction Tests (`KnownLocationsScreenTest.kt`)*:
  • Render `KnownLocationsScreen` in List mode: verify cards display Location Name, Altitude with unit, Source badge (`INTERNET_DEM`, `MANUAL_USER`, `AUTO_LEARNED`, `GPS_FALLBACK`, `LEGACY_RAW`), Hit Count badge, coordinates formatted to 5 decimals, without redundant lock switches.
  • Overflow action menu: verify tapping Edit triggers `EditKnownLocationDialog`; verify tapping Delete triggers confirmation dialog.
  • Map Tab: verify Google Map renders markers and 200m circular geofence overlays around each location.
  • Viewport culling: verify only markers within `visibleRegion.latLngBounds` are rendered when location dataset is large.
  6. *Edit Dialog & DEM Fetching Tests (`EditKnownLocationDialogTest.kt`)*:
  • Render `EditKnownLocationDialog`: verify pre-filled name and altitude text fields (no lock switch rendered).
  • Tap "Fetch from Internet (DEM)": mock `ElevationService.fetchElevation` returning 520.0m; verify progress indicator renders while loading, altitude text updates to `"520.0"`, and source badge updates to `INTERNET_DEM`.
  • Manual edit and save: edit name to "Home Base" and altitude to 530.0m, tap "Speichern"; verify save callback receives `name = "Home Base"`, `altitude = 530.0`, `isLocked = true`, and `source = MANUAL_USER`.
  • Tap "Abbrechen": verify dialog dismisses and zero database mutations occur.
  7. *Hit Count Counting Invariant on Locked Location (`KnownLocationsDatabaseManagerTest.kt`)*:
  • Insert a location with `altitude = 520.0`, `is_locked = 1`, and `hitCount = 5`.
  • Invoke `learnLocation(pos, 480.0, ExtremaType.START)`: verify `altitude` remains strictly 520.0m, coordinates remain unchanged, and `hitCount` increments to 6.
  8. *Localization Parity Audit*:
  • Verify all new string resources exist across all 9 supported application locales (EN, DE, ES, FR, IT, JA, NL, PL, PT) with 0 missing translations.
  9. *Clean-Room Regression Suite*:
  • Execute full clean-room unit test suite (`./gradlew testDebugUnitTest`) to ensure zero regressions across all modules.

### TST-UI-118: **Lieblingsorte UI/UX Harmonization, Standard Tabbed Layout, Sorting Options, Map Deletion & Legacy Name Healing Verification** [Navigation drawer positions Lieblingsorte below Lieblingsstrecken with distinct icons; fallback map camera centers on highest hitCount start location; screen header uses primaryContainer Surface with PrimaryTabRow, HorizontalPager, 4-way sorting, map delete context menu, and navigation bar insets; automatic legacy name healing resolves geocoded addresses for placeholder starts; custom theme-colored heart markers render on map; 100% 9-language localization parity; zero regressions.]
- **Requirement(s)**: `REQ-UI-166`
- **Component**: **Lieblingsorte UI/UX Harmonization, Standard Tabbed Layout, Sorting Options, Map Deletion & Legacy Name Healing Verification**
- **Jira**: ``ATT-1382`, `ATT-1387``
- **Test Description**:
  1. *Drawer Navigation Hierarchy & Icon Mapping Test (`AppNavigationDrawerTest.kt`)*:
  • Verify that under `drawer__maps`, `drawer_start_locations` appears directly after `drawer_my_locations`.
  • Verify that `drawer_start_locations` is configured with `R.drawable.my_locations` (heart pin).
  • Verify that `drawer_my_locations` is configured with `R.drawable.ic_favorite_route` (route trajectory with heart).
  • Verify route resolution mapping to `NavRoutes.START_LOCATIONS` and `NavRoutes.MY_LOCATIONS`.
  2. *Fallback Camera Target Resolution Unit Test (`KnownLocationsViewModelTest.kt`)*:
  • Populate repository flow with multiple locations having varying visit counts (`hitCount = [2, 18, 5]`).
  • Query fallback map target coordinate resolver: verify returned target coordinate matches location with highest hit count (`18`).
  • Query resolver when location list is empty: verify returns null or default Munich coordinates.
  3. *Standard Header & PrimaryTabRow Composable Verification (`KnownLocationsScreenTest.kt`)*:
  • Verify header container renders `Surface` with `primaryContainer` color and `statusBarsPadding()`, without hamburger icon or location counter badge.
  • Verify tab row utilizes `PrimaryTabRow` with `containerColor = MaterialTheme.colorScheme.surfaceContainerHighest` and `divider = {}`.
  • Verify two text-only tabs without icons: "Liste" (`@string/known_locations_tab_list`) and "Karte" (`@string/known_locations_tab_map`).
  4. *Horizontal Pager Swipe & Tab Sync Verification (`KnownLocationsScreenTest.kt`)*:
  • Verify content is hosted in `HorizontalPager` with `pageCount = 2`.
  • Verify swiping between pages synchronizes `pagerState.currentPage` and tab selection.
  5. *Multi-Dimension Sorting Menu Verification (`KnownLocationsViewModelTest.kt` / `KnownLocationsScreenTest.kt`)*:
  • Verify header sort menu presents 4 dimensions in exact sequence: Starts (`RECORDINGS`), Distance to User (`DISTANCE_TO_USER`), Altitude (`ALTITUDE`), and Name (`NAME`).
  • Verify Distance option is disabled when GPS location is unavailable.
  6. *Map Item Deletion Context Menu & Confirmation Dialog Verification (`KnownLocationsScreenTest.kt`)*:
  • Verify long-pressing a map marker pin or geofence circle opens a contextual dropdown menu offering Delete.
  • Verify tapping Delete displays `DeleteConfirmationDialog` before executing deletion.
  7. *List Item Card Standardization & Insets Verification (`KnownLocationsScreenTest.kt`)*:
  • Verify each location item is wrapped in `MappableListItem` (`ElevatedCard`, 16dp rounded corners, 2dp elevation) using modern icon badge layout.
  • Verify bottom list content padding accounts for system navigation bars (`WindowInsets.navigationBars`).
  8. *Automatic Legacy Name Healing & Geocoder Auto-Naming (`KnownLocationsRepositoryTest.kt` / `LocationNameResolverTest.kt`)*:
  • Verify `healLegacyNames()` is triggered asynchronously on screen/viewmodel init.
  • Verify coordinate fallback names (e.g. `Startort (lat, lon)`) and placeholder names are enriched via Geocoder.
  • Verify manual user edits (`isLocked = true` / `source = MANUAL_USER`) are strictly preserved.
  9. *Custom Theme-Colored Marker Asset Generation Test (`MapUtilsTest.kt` / `KnownLocationsScreenTest.kt`)*:
  • Invoke marker generator with theme primary color: verify non-null `BitmapDescriptor` is generated.
  • Verify default red Google Maps markers are not utilized.
  10. *9-Language Nomenclature Parity Audit (`KnownLocationsScreenTest.kt`)*:
  • Verify German string `drawer_start_locations`, `known_locations_title`, and `known_locations_empty_title` equal "Lieblingsorte".
  • Verify English strings equal "Favorite Locations".
  • Verify 100% presence and non-blank values across all 9 supported application locales (EN, DE, ES, FR, IT, JA, NL, PL, PT).
  11. *Clean-Room Regression Suite*:
  • Execute `./gradlew testDebugUnitTest` to ensure zero regressions across all modules.

### TST-UI-119: **Intuitive Smoothing Presets, Smart Defaults & Filter Dialog Verification** [Sensor field configuration defaults cycling power to 3-second moving average; ConfigureFilterDialog provides 1-tap quick preset selection with live preview, custom mode expansion, and SaveCancel semantics; 100% 9-language localization parity; zero regressions.]
- **Requirement(s)**: `REQ-UI-167`
- **Component**: **Intuitive Smoothing Presets, Smart Defaults & Filter Dialog Verification**
- **Jira**: ``ATT-1276``
- **Test Description**:
  1. *Smart Defaults Unit Verification (`EditSensorFieldViewModelTest.kt`)*:
  • Verify that when initializing or changing sensor type to `SensorType.POWER`, `selectedFilterType` defaults to `FilterType.MOVING_AVERAGE_TIME`, `filterConstant` defaults to `3.0`, and `movingAverageUnit` defaults to `"sec"`.
  • Verify that for other sensor types (`HR`, `SPEED_mps`, etc.), `selectedFilterType` defaults to `FilterType.INSTANTANEOUS` and `filterConstant` to `1.0`.
  • Verify that `TrackingViewsDatabaseManager` default start rows for `ActivityType.BIKE_POWER` initialize `SensorType.POWER` with `FilterType.MOVING_AVERAGE_TIME` and `filterConstant = 3`.
  2. *Preset Resolution & State Mapping Verification (`EditSensorFieldViewModelTest.kt`)*:
  • Verify `resolvePreset` maps `INSTANTANEOUS` to `DIRECT`.
  • Verify `resolvePreset` maps `MOVING_AVERAGE_TIME` (3s, 10s, 30s) to `SMOOTH_3S`, `SMOOTH_10S`, and `SMOOTH_30S`.
  • Verify `resolvePreset` maps `AVERAGE` to `SESSION_AVG` and `MAX_VALUE` to `SESSION_MAX`.
  • Verify `resolvePreset` maps non-preset values (e.g. `EXPONENTIAL_SMOOTHING`, `samples`, 15s) to `CUSTOM`.
  3. *Preset Selection State Transition (`EditSensorFieldViewModelTest.kt`)*:
  • Verify `onPresetSelected` updates `selectedFilterType`, `filterConstant`, `movingAverageUnit`, and `filterSummary` immediately.
  4. *Composable Contract & Reflection Verification (`ModalBottomSheetDialogsIntegrityTest.kt`)*:
  • Verify `ConfigureFilterDialog` exists and retains its public composable contract.
  5. *9-Language Localization & Positional Specifier Verification*:
  • Verify all new string resources exist across all 9 supported locales (EN, DE, ES, FR, IT, JA, NL, PL, PT) with 0 missing translations and strict positional format specifiers.
  6. *Clean-Room Regression Suite*:
  • Execute `./gradlew testDebugUnitTest` to ensure zero regressions across all modules.

### TST-UI-120: **Workout Cockpit Theme Selection & Isolation Verification** [Independent AMOLED dark mode selector in DisplaySettingsDialog allows forcing AMOLED pure black mode on active workout telemetry views while isolating control tracking and general app navigation; 100% 9-language translation parity; zero regressions.]
- **Requirement(s)**: `REQ-UI-168`
- **Component**: **Workout Cockpit Theme Selection & Isolation Verification**
- **Jira**: ``ATT-1267``
- **Test Description**:
  1. *Pure Logic & Theme Resolution Verification (`CockpitThemeModeTest.kt`)*:
  • When `mode == CockpitThemeMode.SYSTEM` and `isSystemDark == false`, assert `resolveEffectiveCockpitDarkTheme(mode, isSystemDark) == false`.
  • When `mode == CockpitThemeMode.SYSTEM` and `isSystemDark == true`, assert `resolveEffectiveCockpitDarkTheme(mode, isSystemDark) == true`.
  • When `mode == CockpitThemeMode.ALWAYS_DARK` and `isSystemDark == false`, assert `resolveEffectiveCockpitDarkTheme(mode, isSystemDark) == true`.
  • When `mode == CockpitThemeMode.ALWAYS_DARK` and `isSystemDark == true`, assert `resolveEffectiveCockpitDarkTheme(mode, isSystemDark) == true`.
  • Verify `CockpitThemeMode.fromId("always_dark") == CockpitThemeMode.ALWAYS_DARK`, `CockpitThemeMode.fromId("system") == CockpitThemeMode.SYSTEM`, and unknown/null string falls back to `CockpitThemeMode.SYSTEM`.
  2. *Persistence & Defaults Verification (`TrainingApplicationTest.kt` or `DisplaySettingsTest.kt`)*:
  • Ensure default value of `getCockpitThemeMode()` is `CockpitThemeMode.SYSTEM` when SharedPreferences key is unset.
  • Call `setCockpitThemeMode(CockpitThemeMode.ALWAYS_DARK)`. Verify `getCockpitThemeMode() == CockpitThemeMode.ALWAYS_DARK` and underlying SharedPreferences stores `"always_dark"`.
  • Call `setCockpitThemeMode(CockpitThemeMode.SYSTEM)`. Verify `getCockpitThemeMode() == CockpitThemeMode.SYSTEM` and underlying SharedPreferences stores `"system"`.
  3. *DisplaySettingsDialog UI Staging & Action Semantics (`DisplaySettingsDialogTest.kt`)*:
  • When dialog is displayed, verify the segmented button row reflects the persisted `CockpitThemeMode`.
  • When the user selects `ALWAYS_DARK` and taps "Abbrechen", verify SharedPreferences is NOT modified and dialog dismisses cleanly.
  • When the user selects `ALWAYS_DARK` and taps "Speichern", verify `setCockpitThemeMode(CockpitThemeMode.ALWAYS_DARK)` is executed, `onSettingsChanged` callback is invoked, and dialog dismisses cleanly.
  • Verify existing toggles (`forcePortrait`, `keepScreenOn`, `noUnlocking`) continue to be staged and persisted in parallel with `cockpitThemeMode` without cross-field interference.
  4. *Cockpit Scope Isolation Verification*:
  • Verify `TrackingTabsScreen` binds Page 0 (`ControlTrackingScreen`) using the default ambient theme and Pages 1..N (`TrackingTabGridContent`) using `isCockpitDark`.
  • Verify that toggling `CockpitThemeMode.ALWAYS_DARK` does not mutate `isSystemInDarkTheme()` or alter the theme of `AppNavigationDrawer`, `WorkoutSummariesTabbedScreen`, or `PeriodsScreen`.
  5. *9-Language Localization & Positional Specifier Verification (`TranslationParityTest.kt`)*:
  • Verify string resources exist in all 9 locales: `cockpit_theme_title`, `cockpit_theme_description`, `cockpit_theme_system`, `cockpit_theme_always_dark`.
  • Execute `TranslationParityTest` and assert 0 missing strings.
  6. *Clean-Room Regression Suite*:
  • Execute `./gradlew testDebugUnitTest` and confirm 100% pass rate across all unit tests with 0 failures and 0 regressions.

### TST-UI-121: **AMOLED Pure Black Cockpit Theme Verification** [AMOLED pure black (#000000) theme turns off OLED pixels across tracking views while maintaining crisp tile outlines (#2C2C2E), pure white metrics (#FFFFFF), and athletic zone color contrast; Page 0 and global app shell remain isolated in standard Material 3; zero regressions.]
- **Requirement(s)**: `REQ-UI-169`
- **Component**: **AMOLED Pure Black Cockpit Theme Verification**
- **Jira**: ``ATT-1263``
- **Test Description**:
  1. *Color Scheme Token Unit Tests (`AmoledThemeTest.kt`)*:
  • Verify `AmoledDarkColorScheme.background` equals `Color(0xFF000000)`.
  • Verify `AmoledDarkColorScheme.surface` equals `Color(0xFF000000)`.
  • Verify `AmoledDarkColorScheme.onSurface` equals `Color(0xFFFFFFFF)`.
  • Verify `AmoledDarkColorScheme.outlineVariant` equals `Color(0xFF2C2C2E)`.
  • Verify `AmoledDarkColorScheme.surfaceContainer` equals `Color(0xFF000000)`.
  2. *Standard vs AMOLED Parity Verification*:
  • Verify standard `DarkColorScheme.background` retains `#1B1B1F` (ensuring non-cockpit app dark mode is unchanged).
  • Verify standard `DarkColorScheme.surface` retains `#1B1B1F`.
  3. *Theme Injection Contract*:
  • Verify `ATrainingTrackerTheme` injects `AmoledDarkColorScheme` when `darkTheme == true` and `amoled == true`.
  • Verify `TrackingTabsScreen` applies `amoled = isCockpitDark` to Pages 1..N and LapButton while leaving Page 0 in standard ambient theme.
  4. *Clean-Room Regression Suite*:
  • Execute `./gradlew testDebugUnitTest` to ensure zero broken invariants across existing tests.

### TST-UI-122: **Comprehensive Cockpit Dark Theme Verification** [Top bar, tab row, telemetry cards, and navigation bar area render in unified AMOLED pure black (#000000) on telemetry views without light-blue borders or glare; Page 0 and non-cockpit destinations cleanly isolate in ambient theme; zero regressions.]
- **Requirement(s)**: `REQ-UI-170`
- **Component**: **Comprehensive Cockpit Dark Theme Verification**
- **Jira**: ``ATT-1413``
- **Test Description**:
  1. *State Resolution Unit Tests (`TrackingThemeResolutionTest.kt`)*:
  • Verify Page 0 in TRACKING mode with `ALWAYS_DARK` and `isSystemDark = false` resolves `darkTheme == false` and `amoled == false` (telemetry-only user policy).
  • Verify Page 1 in TRACKING mode with `ALWAYS_DARK` and `isSystemDark = false` resolves `darkTheme == true` and `amoled == true`.
  • Verify Page 0 in TRACKING mode with `ALWAYS_DARK` and `isSystemDark = true` resolves `darkTheme == true` and `amoled == false`.
  • Verify Page 1 in TRACKING mode with `SYSTEM` and `isSystemDark = false` resolves `darkTheme == false` and `amoled == false`.
  • Verify Page 1 in TRACKING mode with `SYSTEM` and `isSystemDark = true` resolves `darkTheme == true` and `amoled == true`.
  • Verify Page 0 in CONFIGURATION / PREVIEW mode with `ALWAYS_DARK` resolves `darkTheme == true` and `amoled == true`.
  • Verify rapid page index switches (0 -> 1 -> 0 -> 2) compute deterministic state without drift.
  2. *Color Scheme Token Unit Tests (`AmoledThemeTest.kt`)*:
  • Verify `AmoledDarkColorScheme.primaryContainer` equals `Color(0xFF000000)`.
  • Verify `AmoledDarkColorScheme.onPrimaryContainer` equals `Color(0xFFFFFFFF)`.
  • Verify `AmoledDarkColorScheme.surfaceContainerHighest` equals `Color(0xFF000000)`.
  • Verify standard `DarkColorScheme.primaryContainer` remains `BabyBlueEyeInverse` (`Color(0xFF001A41)`).
  3. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` across all modules.

### TST-UI-123: **High-Contrast Typography & Cockpit Tile Grid Verification** [Sensor field values render with semibold typography in pure white (#FFFFFF); metric labels and unit annotations render in high-contrast muted grey (#9E9E9E); subtle divider borders (#262626) demarcate tiles; standard dark mode remains strictly isolated; zero regressions.]
- **Requirement(s)**: `REQ-UI-171`
- **Component**: **High-Contrast Typography & Cockpit Tile Grid Verification**
- **Jira**: ``ATT-1264``
- **Test Description**:
  1. *Color Scheme Token Parity Unit Tests (`AmoledThemeTest.kt`)*:
  • Verify `AmoledDarkColorScheme.outlineVariant` equals `Color(0xFF262626)`.
  • Verify `AmoledDarkColorScheme.onSurfaceVariant` equals `Color(0xFF9E9E9E)`.
  • Verify `AmoledDarkColorScheme.onSurface` equals `Color(0xFFFFFFFF)`.
  • Verify `DarkColorScheme.outlineVariant` equals `DarkOutlineVariant` (`Color(0xFF44474F)`).
  • Verify `DarkColorScheme.onSurfaceVariant` equals `DarkOnSurfaceVariant` (`Color(0xFFC4C6D0)`).
  2. *Sensor Field Typography Unit Tests (`SensorFieldTypographyTest.kt`)*:
  • Verify that sensor metric values across all 7 `ViewSize` configurations resolve with `FontWeight.SemiBold`.
  • Verify that label and unit colors map to `colorScheme.onSurfaceVariant`.
  3. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` across all modules.

### TST-UI-124: **Configurable Zone Visualization & Theme-Aware Preview Verification** [Athletes can independently configure 4 zone visualization options per zone profile (`HR_RUN`, `HR_BIKE`, `PWR_BIKE`); live preview in `ZonesSettingsActivity` dynamically reflects toggle selections and respects the active dark/light mode theme; cockpit sensor tiles render custom combinations of background tint, left bar, right bar, and text color while preserving established zone colors; 100% 9-language localization parity; zero regressions.]
- **Requirement(s)**: `REQ-UI-172`
- **Component**: **Configurable Zone Visualization & Theme-Aware Preview Verification**
- **Jira**: ``ATT-1265``
- **Test Description**:
  1. *Data Model Unit Tests (`ZoneDisplayOptionsTest.kt`)*:
  • Verify default constructor sets `showBackground = true`, `showLeftBar = true`, `showRightBar = false`, `showTextColor = false` for backward compatibility.
  • Verify copy mutations produce expected combinations.
  • Verify equality and hash code parity.
  2. *DataStore Preferences Persistence Unit Tests (`SettingsDataStoreZoneDisplayTest.kt`)*:
  • Verify reading uninitialized DataStore preferences returns default `ZoneDisplayOptions` for `HR_RUN`, `HR_BIKE`, and `PWR_BIKE`.
  • Verify saving custom options for `HR_RUN` persists correctly and updates `getZoneDisplayOptionsFlow(ZoneType.HR_RUN)` without contaminating `HR_BIKE` or `PWR_BIKE`.
  • Verify independent persistence across all 3 zone types with distinct toggle configurations.
  • Verify `SettingsDataStoreJavaHelper.getZoneDisplayOptions` returns the persisted values synchronously with safe fallback and non-blocking caching off main thread.
  3. *Cockpit Sensor Tile Rendering Logic Unit Tests (`SensorFieldZoneRenderingTest.kt`)*:
  • Container color resolution: `showBackground = true` returns 12% alpha zone color; `showBackground = false` returns surface; `zoneColor == Transparent` returns surface.
  • Edge bar visibility: verify left/right bar visibility predicates respect `showLeftBar`, `showRightBar`, and `zoneColor != Transparent`.
  • Text color resolution: `showTextColor = true` returns `zoneColor`; `showTextColor = false` returns `onSurface`; `zoneColor == Transparent` returns `onSurface`.
  • Empty selection guard: all false results in standard neutral telemetry.
  • Theme-aware preview reactivity: verify container resolves to pure black/dark surface in dark theme and light surface in light theme when background tint is off.
  4. *9-Language Localization Audit (`ZoneDisplayLocalizationTest.kt`)*:
  • Verify all newly added string keys (`zone_display_title`, `zone_display_background`, `zone_display_left_bar`, `zone_display_right_bar`, `zone_display_text_color`, `zone_display_preview_title`) exist across all 9 application locales.
  5. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` across all modules.

### TST-UI-125: **Sensor Status Inactive Indicator Legibility Verification** [Inactive sensor icons in the top status bar remain clearly visible and legible across AMOLED Pure Black, standard Dark, and Light themes; active sensors remain full brightness; tapping inactive icons opens SensorSourceDialog; 100% test pass rate with zero regressions.]
- **Requirement(s)**: `REQ-UI-173`
- **Component**: **Sensor Status Inactive Indicator Legibility Verification**
- **Jira**: ``ATT-1437``
- **Test Description**:
  1. *Alpha and Tint State Resolution Unit Tests (`SensorStatusLegibilityTest.kt`)*:
  • Verify active sensors resolve `tint = onSurface` and `alpha = 1.0f`.
  • Verify inactive sensors resolve `tint = onSurface` and `alpha = 0.38f`.
  • Verify no compounded `outline` color dimming is applied to inactive icons.
  2. *Contrast Ratio Calculation Verification*:
  • Verify inactive icon effective color on `#000000` achieves contrast ratio $\ge 3:1$ (WCAG 2.1 AA Non-text Contrast SC 1.4.11).
  • Verify active icon effective color on `#000000` achieves contrast ratio $\ge 7:1$ (WCAG AAA).
  • Verify contrast differential between active and inactive icons is $\ge 4:1$.
  3. *Interactivity & Dialog Triggering Verification*:
  • Verify clicking an inactive sensor icon invokes `selectedSensor = type` and renders `SensorSourceDialog`.
  • Verify clicking an active sensor icon continues to open `SensorSourceDialog`.
  4. *Visual Preview & Dark Mode Integration*:
  • Verify `PreviewSensorStatusRowDark` renders inactive sensors distinctly visible against dark surface.
  • Verify `PreviewSensorStatusRow` renders inactive sensors distinctly visible against light surface.
  5. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` across all modules.

### TST-UI-126: **AMOLED Battery Saver Multi-Factor Dimming & Wakeup Verification** [AMOLED Battery Saver mode dynamically adapts display brightness based on terrain slope, heart rate, and power zones; immediate event-based wakeup restores 100% brightness for 15s on touch, wave, lap, segment, or movement; window-scoped brightness enforcement respects safety floor >= 0.05f; DisplaySettingsDialog stages "Einsparungen" category; 100% 9-language localization parity; zero regressions.]
- **Requirement(s)**: `REQ-UI-174`
- **Component**: **AMOLED Battery Saver Multi-Factor Dimming & Wakeup Verification**
- **Jira**: ``ATT-1268``
- **Test Description**:
  1. *Pure State Machine Unit Verification (`BatterySaverStateMachineTest.kt`)*:
  • Full Dimming Matrix: slope < 2%, HR <= Z2, Power <= Z2 -> FULL_DIM (0.15f).
  • Medium Dimming Matrix: slope 2–5%, or HR == Z3, or Power == Z3 -> MEDIUM_DIM (0.50f).
  • No Dimming Matrix: slope > 5%, or HR >= Z4, or Power >= Z4 -> NO_DIM (1.0f).
  2. *Sensor Fallback & Edge Case Matrix (`BatterySaverStateMachineTest.kt`)*:
  • Running / Missing Power: evaluate Slope & HR.
  • Missing HR: evaluate Slope & Power.
  • Zero peripheral sensors: pure Slope thresholds.
  3. *Wakeup Controller & Timer Lifecycle (`BatterySaverControllerTest.kt`)*:
  • Interrupt execution: touch, proximity wave, live segments, lap split, auto-pause/restart immediately snap brightness to 100% (BRIGHTNESS_OVERRIDE_NONE).
  • Timer reset: overlapping events reset 15s timer; timeout smoothly restores calculated dimming level.
  4. *Settings Dialog & Persistence (`DisplaySettingsDialogTest.kt`)*:
  • Verify "Einsparungen" category and divider above Cockpit Dark Mode.
  • Verify Akkusparer switch staging, cancel dismissal, and commit to TrainingApplication / SharedPreferences.
  5. *Localization Parity & Regression Suite*:
  • 100% string coverage across 9 locales (EN, DE, ES, FR, IT, JA, NL, PL, PT).
  • Full `testDebugUnitTest` suite passes cleanly with zero regressions.

### TST-UI-127: **Tracking Tabs Configuration Transition and Hierarchical Navigation Verification** [Tracking tabs configuration reliably opens from navigation drawer regardless of active screen; hierarchical back navigation transitions CONFIGURATION -> PREVIEW -> TRACKING without exiting app; zero test regressions.]
- **Requirement(s)**: `REQ-UI-175`
- **Component**: **Tracking Tabs Configuration Transition and Hierarchical Navigation Verification**
- **Jira**: ``ATT-1456`, `ATT-1459``
- **Test Description**:
  1. *Activity-Scoped ViewModel & Sport Selection Transition Unit Tests (`TrackingTabsViewModelInteropTest.kt`)*:
  • Verify that when `onActivityTypeSelected(activityType)` is invoked on `MainActivityWithNavigation`, the Activity-scoped `TrackingTabsViewModel` receives `setExplicitActivityType(activityType)` and transitions `screenMode` to `ScreenMode.CONFIGURATION`.
  • Verify that `activityType` StateFlow emits the selected `ActivityType` when `setExplicitActivityType` is set.
  • Verify that `trackingViews` flow loads tracking views specific to the configured sport type.
  2. *Hierarchical Back Navigation Flow Unit Tests*:
  • In `ScreenMode.CONFIGURATION`, calling `handleBackPressToPreview()` transitions `screenMode` to `ScreenMode.PREVIEW`.
  • In `ScreenMode.PREVIEW`, calling `exitConfiguration()` transitions `screenMode` to `ScreenMode.TRACKING` and resets `explicitActivityType` to `null`.
  3. *Preview Header Exit Action Verification*:
  • Verify `TrackingTabPreviewHeader` renders exit/done button and clicking it executes `onExitConfig()`.
  4. *On-Device Pixel 10 End-to-End Verification*:
  • Open navigation drawer -> Tap "Tracking Tabs" (`drawer_tracking_layouts`) -> Select sport "Radeln" -> Verify header renders `TrackingTabConfigHeader` with tab name input, add/delete buttons, and metric checkboxes.
  • Tap Check icon -> Verify screen transitions to `ScreenMode.PREVIEW`.
  • Press system Back or tap Done -> Verify screen transitions to `ScreenMode.TRACKING` without exiting the application.
  5. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` across all modules to verify 100% test pass rate with 0 regressions.

### TST-UI-128: **Cockpit Tab Header Synchronous Color Transition Verification** [Tab header text colors switch instantaneously in sync with tab bar background during Page 0 <-> Page 1+ navigation; contrast ratios comply with WCAG 2.1 AA/AAA; zero test regressions.]
- **Requirement(s)**: `REQ-UI-176`
- **Component**: **Cockpit Tab Header Synchronous Color Transition Verification**
- **Jira**: ``ATT-1446`, `ATT-1464`, `ATT-1466`, `ATT-1467``
- **Test Description**:
  1. *Color Resolution & Contrast Unit Tests (`TabHeaderColorResolutionTest.kt`)*:
  • Verify that in AMOLED Dark Mode (`AmoledDarkColorScheme`), selected tab color resolves to `DarkPrimary` (`#A6C8FF`, contrast against `#000000` $\ge 12:1$) and unselected tab color resolves to `AmoledOnSurfaceVariant` (`#9E9E9E`, contrast against `#000000` $\ge 7:1$).
  • Verify that in Light Mode (`LightColorScheme`), selected tab color resolves to `LightPrimary` (`#1464F4`, contrast against `#C8E3FF` $\ge 4.5:1$) and unselected tab color resolves to `LightOnSurfaceVariant` (`#44474F`, contrast against `#C8E3FF` $\ge 5:1$).
  2. *Static Composable Call Site Audit*:
  • Verify that in `TrackingTabsScreen.kt`, all tab `Text` composables explicitly specify `color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant`, bypassing `TabTransition`'s animated `LocalContentColor`.
  • Verify that `Tab` explicitly specifies `selectedContentColor` and `unselectedContentColor`.
  3. *On-Device Pixel 10 Verification (Light Device Mode + Always Dark Cockpit)*:
  • Navigate between Page 0 and Page 1+.
  • Verify tab header typography switches color synchronously in 0ms with the tab bar background container without any visible 250ms animation delay, color morphing, or intermediate muddy colors.
  4. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` across all modules to verify 100% test pass rate with 0 regressions.

### TST-UI-129: **Theme-Aware Share Snapshot Color Harmonization Verification** [Post-workout and period share snapshots harmonize perfectly with active Light and Dark themes; WCAG 2.1 AAA contrast compliance; zero white seams or bright footers in Dark Mode; zero regressions.]
- **Requirement(s)**: `REQ-UI-177`
- **Component**: **Theme-Aware Share Snapshot Color Harmonization Verification**
- **Jira**: ``ATT-1472`, `ATT-1475`, `ATT-1476`, `ATT-1477`, `ATT-1478`, `ATT-1480``
- **Test Description**:
  1. *Share Theme Color Token Unit Tests (`ShareThemeResolverTest.kt`)*:
  • Verify `ShareThemeResolver.resolveColors(isDark = false)` returns `Color.WHITE` canvas, `#F5F5F5` footer background, and `Color.DKGRAY` footer text.
  • Verify `ShareThemeResolver.resolveColors(isDark = true)` returns `Color.BLACK` canvas, `#1E1E1E` footer background, and `#E0E0E0` footer text.
  • Verify `ShareThemeResolver.Light` and `Dark` constants retain immutable definitions.
  2. *WCAG 2.1 AAA Contrast Ratio Calculations*:
  • Verify Light Mode footer text (`Color.DKGRAY`, #444444) on background (`#F5F5F5`) achieves contrast ratio $\ge 7:1$ (measured ~9.0:1).
  • Verify Dark Mode footer text (`#E0E0E0`) on background (`#1E1E1E`) achieves contrast ratio $\ge 7:1$ (measured ~13.5:1).
  • Verify Dark Mode canvas background evaluates to pure `#000000` for seamless integration with dark map and AMOLED UI components.
  3. *Context Configuration Interop Unit Tests*:
  • Verify `ShareThemeResolver.isNightMode(context)` returns `true` when `Configuration.UI_MODE_NIGHT_MASK` is `UI_MODE_NIGHT_YES`, and `false` when `UI_MODE_NIGHT_NO`.
  • Verify `combineWorkoutAndShare` and `combineAndShare` default parameter `isDark` resolves dynamically from context without runtime error.
  4. *On-Device Pixel 10 End-to-End Verification*:
  • Switch device to Dark Mode. Open workout details map (`MapDetailLayout`). Tap Share button. Verify captured preview snapshot has pure black canvas and dark footer `#1E1E1E` with crisp light text `#E0E0E0` and zero bright white seams.
  • Switch device to Light Mode. Open workout details map. Tap Share button. Verify captured preview snapshot retains traditional `#FFFFFF` canvas, `#F5F5F5` footer, and dark gray typography.
  5. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` across all modules to verify 100% test pass rate with 0 regressions.

### TST-UI-130: **Direct Workout-to-Cluster Navigation and Backstack Integrity Verification** [Direct navigation from workout summaries and period map opens the targeted cluster detail/heatmap immediately; pressing Back pops directly to the caller screen; workout filters and scroll state are preserved; drawer navigation opens the cluster tabs list; zero test regressions.]
- **Requirement(s)**: `REQ-UI-178`
- **Component**: **Direct Workout-to-Cluster Navigation and Backstack Integrity Verification**
- **Jira**: ``ATT-1448`, `ATT-1481`, `ATT-1483``
- **Test Description**:
  1. *Parameterized Route and Drawer ID Unit Tests (`NavRoutesClusterTest.kt`)*:
  • Verify `NavRoutes.locations(null)` returns `"locations"`.
  • Verify `NavRoutes.locations(-1L)` returns `"locations"`.
  • Verify `NavRoutes.locations(42L)` returns `"locations?clusterId=42"`.
  • Verify `NavRoutes.toDrawerItemId("locations")` returns `R.id.drawer_my_locations`.
  • Verify `NavRoutes.toDrawerItemId("locations?clusterId=42")` returns `R.id.drawer_my_locations`.
  • Verify `NavRoutes.toDrawerItemId("locations?clusterId=100&other=true")` returns `R.id.drawer_my_locations`.
  2. *Navigation Backstack & Event Dispatch Unit Tests*:
  • Verify `WorkoutNavigationEvents.triggerCluster(clusterId)` emits `clusterId` on `navigateToClusterLiveData`.
  • Verify `WorkoutNavigationEvents.resetCluster()` resets `navigateToClusterLiveData` to `null`.
  • Verify `MainActivityWithNavigation` observer invokes `navController.navigate("locations?clusterId=42")` without invoking `popUpTo(startDestination)` or `clearWorkoutFilterCriteria()`.
  3. *Cluster Direct Navigation Lifecycle & Back Handling Unit Tests*:
  • In `WorkoutClustersScreen`, verify that when `initialClusterId` is provided (`> 0`), `viewModel.selectClusterById(initialClusterId)` is called.
  • Verify that when `onBackToNav` is invoked, `clustersViewModel.selectCluster(null)` is executed and backstack is popped.
  4. *On-Device Pixel 10 End-to-End Verification*:
  • Apply a filter on the workout list (e.g. sport type or date range) and scroll down.
  • Tap a cluster chip on a workout card.
  • Verify `WorkoutClusterHeatmapScreen` opens immediately displaying the targeted cluster route and metrics (no 5-tab overview).
  • Tap top bar back arrow or use system Back gesture. Verify app returns directly to the workout list.
  • Verify the active filter and scroll position are completely preserved.
  • Open drawer -> tap "Lieblingsstrecken". Verify the 5-tab cluster overview opens normally.
  5. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` across all modules to verify 100% test pass rate with 0 regressions.

### TST-UI-131: **Customizable Geofence Radius with Live Map Preview Verification** [Athletes can customize geofence radius (50m–1000m, step=25m) in edit dialog with live map preview; custom radius persists to SQLite; imperial/metric unit formatting; 100% 9-language localization parity; zero test regressions.]
- **Requirement(s)**: `REQ-UI-179`
- **Component**: **Customizable Geofence Radius with Live Map Preview Verification**
- **Jira**: ``ATT-1473`, `ATT-1493`, `ATT-1494`, `ATT-1495`, `ATT-1496`, `ATT-1497``
- **Test Description**:
  1. *Repository Persistence & Concurrency Unit Tests (`KnownLocationsRepositoryTest.kt`)*:
  • Test `updateLocation` with custom radius: verify `mockDbManager.updateLocation(id, name, altitude, radius, source, isLocked)` is invoked with `radius = 500` and `locationsFlow` updates reactively with `item.radius == 500`.
  • Verify backward-compatible 4-argument overload executes cleanly without altering radius.
  2. *ViewModel Delegation & Dialog State Unit Tests (`KnownLocationsViewModelTest.kt`)*:
  • Verify `viewModel.updateLocation(id, name, altitude, radius, source)` propagates all parameters to repository and resets `selectedLocationForEdit` to dismiss the dialog.
  3. *Unit Conversion & Formatting Tests (`EditKnownLocationDialogTest.kt`)*:
  • Metric mode: verify `formatRadius(200, isMetric = true)` returns `"200 m"`.
  • Imperial mode: verify `formatRadius(200, isMetric = false)` returns `"200 m (656 ft)"`.
  • Edge boundaries: verify `50m` returns `"50 m"` / `"50 m (164 ft)"`, and `1000m` returns `"1000 m"` / `"1000 m (3281 ft)"`.
  4. *Dialog Slider Logic & Dynamic Map Binding Unit Tests (`EditKnownLocationDialogTest.kt`)*:
  • Verify radius slider snaps to 25m steps (`(round(v / 25f) * 25f).roundToInt()`) and clamps strictly within $[50, 1000]$.
  • Verify `onConfirm` callback receives updated `radiusMeters`.
  5. *Database Manager ContentValues Unit Tests (`KnownLocationsDatabaseManagerTest.kt`)*:
  • Verify `updateLocation` with radius puts `KnownLocationsDbHelper.RADIUS` into `ContentValues` and updates SQLite row.
  6. *9-Language Localization Parity (`TranslationParityTest.kt`)*:
  • Verify `known_location_radius_label` and `known_location_radius_format` exist and validate across all 9 supported locales (EN, DE, ES, FR, IT, JA, NL, PL, PT) with 0 missing translations.
  7. *On-Device Pixel 10 End-to-End Verification*:
  • Open Lieblingsorte -> edit a location with map preview.
  • Drag radius slider from 200m to 500m; verify circular map overlay expands dynamically in real time.
  • Tap "Speichern"; verify location card and map perspective reflect 500m radius.
  • Re-open edit dialog; verify slider initializes at 500m.
  8. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` across all modules to verify 100% test pass rate with 0 regressions.

### TST-UI-132: **Favorite Locations on Central Map & Streamlined Management Verification** [Favorite locations render seamlessly on central navigation map with interactive bottom peek card; "Show on Map" in streamlined list navigates directly to central map; redundant map tab removed; 100% test pass rate with zero regressions.]
- **Requirement(s)**: `REQ-UI-180`
- **Component**: **Favorite Locations on Central Map & Streamlined Management Verification**
- **Jira**: ``ATT-1449`, `ATT-1498`, `ATT-1499``
- **Test Description**:
  1. *Central Map ViewModel State & Repository Delegation Unit Tests (`MapFragmentWithTrackViewModelTest.kt`)*:
  • Verify `viewModel.uiState` combines `knownLocationsRepository.locationsFlow` and exposes `knownLocations: List<KnownLocationItem>` in `MapFragmentUIState`.
  • Verify `viewModel.updateKnownLocation(id, name, altitude, radius, source)` delegates to `knownLocationsRepository.updateLocation`.
  2. *Navigation Route & Drawer Mapping Unit Tests (`NavRoutesTest.kt`)*:
  • Verify `NavRoutes.map()` returns `"map"`.
  • Verify `NavRoutes.map(42L)` returns `"map?locationId=42"`.
  • Verify `NavRoutes.toDrawerItemId("map?locationId=42")` returns `R.id.drawer_map`.
  3. *Streamlined Management ViewModel Unit Tests (`KnownLocationsViewModelTest.kt`)*:
  • Verify `KnownLocationsViewModel` retains 100% of list filtering, sorting, and editing capabilities without viewport bounds culling or map tab state.
  4. *Central Map Compose UI & Peek Sheet Unit Tests (`MapScreenWithTrackTest.kt`)*:
  • Verify clicking a favorite location pin sets `selectedLocationId`, expands `BottomSheetScaffold` to peek height ($140\text{dp} + \text{navBarHeight}$), and renders location name, altitude, start count, and edit button.
  • Verify supplying `targetLocationId` animates camera focus to target coordinates and expands its peek card.
  • Verify tapping map background or pressing Back clears `selectedLocationId` and collapses the sheet.
  5. *Streamlined Management Screen Unit Tests (`KnownLocationsScreenTest.kt`)*:
  • Verify `KnownLocationsScreen` renders single-perspective list without "Karte" tab or `HorizontalPager`.
  • Verify selecting "Show on Map" (`@string/known_location_show_map`) in the item overflow menu invokes `onShowOnMap(location.id)`.
  6. *On-Device Pixel 10 End-to-End Verification*:
  • Navigate to "Lieblingsorte" via drawer: verify single-perspective list.
  • Select "Show on Map" on a location: verify navigation to central map centered on location with peek card expanded.
  • Tap "Bearbeiten" on peek card: verify `EditKnownLocationDialog` opens and saves cleanly.
  • Open central map directly: verify all favorite locations appear with heart-pin markers and geofence circles alongside routes and segments.
  7. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` across all modules to verify 100% test pass rate with 0 regressions.

### TST-UI-133: **Ultra-Large ViewSize Typography & Configuration Verification** [Athletes can select XXHuge and XXXHuge text sizes in sensor field configuration; primary values render at 140.sp and 180.sp with SemiBold weight; unit annotations scale proportionally; 100% 9-language localization parity; zero test regressions.]
- **Requirement(s)**: `REQ-UI-181`
- **Component**: **Ultra-Large ViewSize Typography & Configuration Verification**
- **Jira**: ``ATT-1431`, `ATT-1505`, `ATT-1506``
- **Test Description**:
  1. *Font Weight Invariant Unit Tests (`SensorFieldTypographyTest.kt`)*:
  • Verify all 9 sizes in `ViewSize.values()` enforce `FontWeight.SemiBold` for primary sensor metric values (`getSensorValueTextStyle`).
  • Verify all 9 sizes in `ViewSize.values()` do not enforce `FontWeight.Bold` for sensor unit annotations (`getSensorUnitTextStyle`).
  2. *Explicit Font Size Unit Tests (`SensorFieldTypographyTest.kt`)*:
  • Verify `XXHUGE` evaluates to `fontSize = 140.sp` for value and `56.sp` for unit.
  • Verify `XXXHUGE` evaluates to `fontSize = 180.sp` for value and `64.sp` for unit.
  • Verify existing sizes (`XSMALL` at 20/10sp, `XLARGE` at 50/32sp, `HUGE` at 76/40sp, `XHUGE` at 100/48sp) remain unaltered.
  3. *Database Persistence & Deserialization Unit Tests*:
  • Verify `ViewSize.valueOf("XXHUGE")` and `ViewSize.valueOf("XXXHUGE")` resolve correctly.
  • Verify `TrackingViewsRepository` deserializes invalid or null size strings to `ViewSize.NORMAL` as fallback.
  4. *Configuration UI & ViewModel Unit Tests (`EditSensorFieldViewModelTest.kt`)*:
  • Verify `availableViewSizes` contains all 9 sizes including `XXHUGE` and `XXXHUGE`.
  • Verify `onViewSizeChanged(ViewSize.XXHUGE)` and `onViewSizeChanged(ViewSize.XXXHUGE)` update `uiState.selectedViewSize` correctly.
  5. *9-Language Localization Parity (`TranslationParityTest.kt`)*:
  • Verify `view_size_xxhuge` and `view_size_xxxhuge` exist and validate across all 9 supported locales (EN, DE, ES, FR, IT, JA, NL, PL, PT) with 0 missing translations.
  6. *On-Device Pixel 10 End-to-End Verification*:
  • Open tracking cockpit -> enter edit mode -> edit a single-field sensor tab.
  • Select "XXHuge" in the text size dropdown; verify instant layout rendering with 140.sp numeral.
  • Select "XXXHuge"; verify giant 180.sp numeral readout fills the display canvas cleanly.
  • Verify no unexpected text truncation or unhandled line wrapping occurs.
  7. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` across all modules to verify 100% test pass rate with 0 regressions.

### TST-UI-134: **Unified Edit Action Symbols & Design Token Tinting Verification** [All interactive edit buttons, dialog headers, and menus use the canonical Material 3 pencil symbol (`Icons.Default.Edit` / `@drawable/ic_edit`); 100% color tint uniformity with `MaterialTheme.colorScheme.primary`; 100% localization parity; zero test regressions.]
- **Requirement(s)**: `REQ-UI-182`
- **Component**: **Unified Edit Action Symbols & Design Token Tinting Verification**
- **Jira**: ``ATT-1503`, `ATT-1518`, `ATT-1519``
- **Test Description**:
  1. *Source Code & Resource Vector Verification (`EditIconConsistencyTest.kt`)*:
  • Verify XML menu resource `device_list_context_menu.xml` contains zero references to `@android:drawable/ic_menu_edit` and references `@drawable/ic_edit`.
  • Verify `ic_edit.xml` exists and defines a valid vector asset with standard Material 3 edit pencil geometry.
  • Verify Compose source files (`WorkoutHeader.kt`, `WorkoutClusterHeatmapScreen.kt`, `TrackingTabPreviewHeader.kt`, `EditKnownLocationDialog.kt`, `ActivityTypeSelectionDialog.kt`) reference `Icons.Default.Edit` for general edit actions and zero references to `ic_table_edit`.
  2. *Interactive Button Color & Tint Token Verification (`EditIconConsistencyTest.kt`)*:
  • Verify `WorkoutHeader.kt`, `WorkoutClusterHeatmapScreen.kt`, `TrackingTabPreviewHeader.kt`, and `MapScreenWithTrack.kt` apply `MaterialTheme.colorScheme.primary` tinting to edit action buttons.
  3. *9-Language Localization & Content Description Parity (`TranslationParityTest.java`)*:
  • Verify all localized edit content descriptions (`edit_workout`, `Edit`, `edit_workout_name`, `known_location_edit`) exist across all 9 supported locales (EN, DE, ES, FR, IT, JA, NL, PL, PT) with 0 missing strings.
  4. *Visual Compose Preview & Tinting Verification (`@Preview` inspection)*:
  • Inspect composable previews for `WorkoutHeader`, `TrackingTabPreviewHeader`, and `EditKnownLocationDialog` in Light and AMOLED Dark modes; verify $24\text{dp}$ visual scale within touch targets and dynamic `primary` token tinting without clipping.
  5. *CI & Resource Lint Guard (`EditIconConsistencyTest.kt`)*:
  • Scan all menu XML resources for `@android:drawable/ic_menu_edit` and verify 0 occurrences across the project.
  6. *On-Device Pixel 10 End-to-End Verification*:
  • Workout Summary: verify unified pencil icon renders on the right in primary tint.
  • Cluster Heatmap: verify pencil icon for rename and location pin icon for boundary tuning.
  • Tracking Cockpit: verify edit button in tracking tabs preview uses primary tint.
  • Modal Bottom Sheets: verify header icons across edit dialogs render unified pencil icon in primary tint.
  • Device Management: verify device context menu renders unified edit icon.
  7. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` across all modules to verify 100% test pass rate with 0 regressions.

### TST-UI-135: **Navigation Drawer Icon Theme-Aware Contrast Tinting Verification** [Navigation drawer destination icons dynamically tint to high-contrast white in Dark Mode and dark navy in Light Mode matching text labels; selected items tint with primary color; partner brand logos (Strava, Dropbox) retain authentic colors; 100% test pass rate with zero regressions.]
- **Requirement(s)**: `REQ-UI-123`
- **Component**: **Navigation Drawer Icon Theme-Aware Contrast Tinting Verification**
- **Jira**: ``ATT-1547``
- **Test Description**:
  1. *Drawer Item Model & Brand Exemption (`AppNavigationDrawerTest.kt`)*:
  • Instantiate `createDrawerGroups(R.string.tab_start)`.
  • Verify that all monochrome item configs have `tintIcon == true`.
  • Verify that `R.id.drawer_strava` and `R.id.drawer_dropbox` have `tintIcon == false`.
  2. *DrawerItemView ColorFilter Integration (`AppNavigationDrawerTest.kt`)*:
  • Verify that in `AppNavigationDrawer.kt`, the leading `Image` composable defines `colorFilter = if (item.tintIcon) ColorFilter.tint(contentColor) else null`.
  3. *Theme Token & Contrast Ratio Audit (`AppNavigationDrawerTest.kt`)*:
  • Verify `color_on_surface` in `values-night/color.xml` is `#ffffff`.
  • Verify `color_on_surface` in `values/color.xml` is `#000080`.
  • Calculate contrast ratios against dark surface `#121212` and light surface `#ffffff`, asserting WCAG AA compliance (> 4.5:1).
  4. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` across all modules.

### TST-UI-136: **Live Cockpit Location & Barometer Calibration Feedback Verification** [Live cockpit location & calibration feedback badge renders recognized start location and calibrated altitude at the top of the ControlTrackingScreen; clean setup screen without clobbering tracking data tabs; 100% 9-language localization parity; zero regressions.]
- **Requirement(s)**: `REQ-UI-183`
- **Component**: **Live Cockpit Location & Barometer Calibration Feedback Verification**
- **Jira**: ``ATT-1399``
- **Test Description**:
  1. *ViewModel Reactive Matching Unit Tests (`TrackingTabsViewModelLocationTest.kt`)*:
  • Within geofence ($d < r$): verify `locationCalibrationStatus` emits non-null `LocationCalibrationStatus` with matching name, altitude, and `isCalibrated == true`.
  • Outside geofence ($d \ge r$): verify `locationCalibrationStatus` emits `null`.
  • Dynamic movement: verify transitioning from inside to outside geofence updates status to null.
  • Multiple locations: verify closest matching location is selected.
  2. *ControlTrackingScreen UI Integration & Decoupled Architecture*:
  • Verify `LocationCalibrationBadge` is placed at the top of `ControlTrackingScreen` (pre-workout setup/ready tab) rather than global header, keeping secondary data tabs clean without needing a display toggle.
  3. *Composable UI Badge Unit Tests (`LocationCalibrationBadgeTest.kt`)*:
  • Status non-null: renders location name, pin icon, and formatted altitude.
  • Status null: renders nothing (or hidden).
  • Unit conversions: verifies Metric meters vs Imperial feet display.
  4. *9-Language Localization Parity (`TranslationParityTest.kt`)*:
  • Verify string resources (`location_calibrated_format`, `calibrated`) across all 9 supported locales (EN, DE, ES, FR, IT, JA, NL, PL, PT) with 0 missing translations.
  5. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` across all modules to verify 100% test pass rate with 0 regressions.

### TST-UI-137: **Lieblingsorte: Display Start & Destination Locations in Workout Details and Map Verification** [Workout summary cards, headers, and route map markers display recognized favorite start and destination locations; round-trip workouts cleanly combine into a single indicator; zero database migrations needed; 100% 9-language localization parity; zero regressions.]
- **Requirement(s)**: `REQ-UI-184`
- **Component**: **Lieblingsorte: Display Start & Destination Locations in Workout Details and Map Verification**
- **Jira**: ``ATT-1400``
- **Test Description**:
  1. *Data Model & Mapping Unit Tests (`WorkoutDataMapperLocationTest.kt`)*:
  • Test dynamic resolution of `startLocationName` and `endLocationName` via mocked `KnownLocationsDatabaseManager` and `WorkoutAutoNamingHelper.getDisplayName` (using `KnownLocationItem(name = ...)`).
  • Verify that when `startLatLng` is within a known location geofence, `startLocationName` is populated.
  • Verify that when `endLatLng` is within a known location geofence, `endLocationName` is populated.
  • Verify that when coordinates are outside known geofences or null, location names resolve to null.
  • Verify that `WorkoutData.headerData` maps both location properties directly to `WorkoutHeaderData`.
  2. *WorkoutHeader Composable Rendering Unit Tests (`WorkoutHeaderLocationTest.kt`)*:
  • Round-trip scenario (`startLocationName == endLocationName`): verify header renders formatted text with `@string/workout_start_and_destination` (`"Start & Ziel: Zuhause"`).
  • Point-to-point scenario (`startLocationName != endLocationName`): verify header renders both `@string/workout_start_location` and `@string/workout_destination_location` (`"Start: Zuhause   Ziel: Büro"`).
  • Single endpoint scenario: verify header renders only the recognized endpoint (start or destination).
  • Neither endpoint recognized (`null, null`): verify location indicator row is omitted.
  • Text truncation: verify `TextOverflow.Ellipsis` and `maxLines = 1` are applied.
  3. *Route Map Pin Marker Annotations Unit Tests (`WorkoutRepositoryMarkerTest.kt`)*:
  • In `WorkoutRepository.getWorkoutMarkers()`, verify start marker title includes start location name when present (e.g. `"Start: Zuhause"`).
  • Verify finish marker title includes end location name when present (e.g. `"Stop: Büro"`).
  • Verify markers default cleanly to standard `"Start"` and `"Stop"` when locations are null.
  4. *9-Language Localization & Specifier Audit (`TranslationParityTest.kt`)*:
  • Verify string resources `workout_start_location`, `workout_destination_location`, and `workout_start_and_destination` exist across all 9 supported locales (EN, DE, ES, FR, IT, JA, NL, PL, PT) with 0 missing translations and matching `%s` format specifiers.
  5. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` across all modules to verify 100% test pass rate with 0 regressions.

### TST-UI-138: **Lieblingsorte: Drill-Down Filter to View Workouts by Starting Location Verification** [Interactive starts badge on Lieblingsort cards enables 1-tap navigation to filtered workout list; spatial start location geofence filter accurately filters workouts; active chip provides visual feedback and dismissal; zero regressions.]
- **Requirement(s)**: `REQ-UI-185`
- **Component**: **Lieblingsorte: Drill-Down Filter to View Workouts by Starting Location Verification**
- **Jira**: ``ATT-1401``
- **Test Description**:
  1. *Spatial Filter Criteria Unit Tests (`WorkoutFilterCriteriaSpatialTest.kt`)*:
  • Verify workout matching within favorite start location geofence radius ($d \le r$).
  • Verify workout exclusion outside favorite start location geofence radius ($d > r$).
  • Verify workout with null `startLatLng` evaluates to false.
  • Verify custom radius handling and default 200m fallback.
  • Verify `activeFilterCount` includes spatial filter dimension.
  • Verify lossless JSON serialization/deserialization across DataStore persistence.
  2. *Active Filter Chips Row Unit Tests (`ActiveFilterChipsRowLocationTest.kt`)*:
  • Verify `ActiveFilterChipsRow` displays `"📍 ${name}"` chip when spatial filter is active.
  • Verify tapping remove invokes `onRemoveStartLocation` callback.
  3. *Lieblingsorte Card Drill-Down Affordance Tests (`KnownLocationsScreenDrillDownTest.kt`)*:
  • Verify tapping starts count badge invokes `onShowWorkouts(item)`.
  • Verify tapping card body outside starts badge continues to invoke `onEdit(item)`.
  • Verify long-pressing card body strictly preserves universal delete-only context menu.
  4. *9-Language Localization Audit (`TranslationParityTest.java`)*:
  • Verify string resources `filter_start_location` and `known_locations_view_workouts` across all 9 supported locales (EN, DE, ES, FR, IT, JA, NL, PL, PT) with 0 missing translations.
  5. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` across all modules to verify 100% test pass rate with 0 regressions.

### TST-UI-139: **Lieblingsorte & Lieblingsstrecken Bridge Verification** [Automated seeding ensures every route cluster originates at a recognized favorite location; favorite location cards show linked route clusters; cluster cards show departure location badge; spatial filter enables filtering clusters by starting location; 100% 9-language localization parity; zero regressions.]
- **Requirement(s)**: `REQ-UI-186`
- **Component**: **Lieblingsorte & Lieblingsstrecken Bridge Verification**
- **Jira**: ``ATT-1402``
- **Test Description**:
  1. *Automated Location Seeding & Invariant Tests (`WorkoutClusterEngineLocationSeedTest.kt`)*:
  • Learn workout at new coordinate without existing location -> verify `recordWorkoutStart` is invoked with correct coordinate and altitude.
  • Learn workout at existing location coordinate -> verify no duplicate location is created, and existing location is preserved.
  2. *Cluster Spatial Filter Criteria Tests (`ClusterFilterCriteriaSpatialTest.kt`)*:
  • Verify cluster starting within favorite start location geofence radius ($d \le r$) matches.
  • Verify cluster starting outside geofence radius ($d > r$) does not match.
  • Verify custom radius handling and default 200m fallback.
  • Verify `activeFilterCount` includes spatial filter dimension.
  • Verify lossless JSON serialization/deserialization across DataStore persistence.
  3. *Lieblingsorte Linked Routes Association Tests (`KnownLocationsViewModelRoutesTest.kt`)*:
  • Verify `clustersByLocationId` maps matching clusters starting within location geofence.
  • Verify clusters starting outside geofence are not associated.
  • Verify empty list when a location has 0 starting clusters.
  4. *9-Language Localization Audit (`TranslationParityTest.java`)*:
  • Verify string resources `cluster_start_location`, `known_location_routes_header`, and `filter_by_start_location` across all 9 supported locales (EN, DE, ES, FR, IT, JA, NL, PL, PT) with 0 missing translations.
  5. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` across all modules to verify 100% test pass rate with 0 regressions.

### TST-UI-140: **Navigation Drawer Icon Defensive Resource Resolution & Crash Prevention Verification** [Navigation drawer handles missing or density-unresolvable drawables gracefully without crashing; partner brand icons have unqualified root fallbacks; all drawer items pass integrity verification; zero test regressions.]
- **Requirement(s)**: `REQ-UI-123`
- **Component**: **Navigation Drawer Icon Defensive Resource Resolution & Crash Prevention Verification**
- **Jira**: ``ATT-1592``
- **Test Description**:
  1. *Defensive try-catch Resolution Unit Test (`AppNavigationDrawerTest.kt`)*:
  • Verify that when `DrawerItemView` is composed with a non-existent or invalid resource ID (e.g. `0x7f089999`), the composable does not throw `Resources.NotFoundException` or any other runtime exception.
  • Verify that upon failure, a 24dp `Spacer` placeholder is composed instead of crashing.
  2. *Root Fallback Asset Verification (`AppNavigationDrawerTest.kt`)*:
  • Verify `res/drawable/logo_square_strava.png` exists in the unqualified `drawable/` folder as a density fallback.
  • Verify `res/drawable/dropbox_logo_blue.png` exists in the unqualified `drawable/` folder as a density fallback.
  3. *Drawer Groups Integrity Verification (`AppNavigationDrawerTest.kt`)*:
  • Iterate over all drawer items produced by `createDrawerGroups()` and verify every declared `iconRes` and `titleRes` is non-zero and resolvable in the test context.
  4. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` across all modules to verify 100% test pass rate with 0 regressions.

### TST-UI-141: **Workout & Cluster Filter Dialogs: Lieblingsorte and Lieblingsstrecken Selection Verification** [Both filter dialogs allow interactive selection of Lieblingsorte and Lieblingsstrecken; active filter chips rows show immediate visual feedback and enable 1-tap removal; 100% localization parity; zero regressions.]
- **Requirement(s)**: `REQ-UI-187`
- **Component**: **Workout & Cluster Filter Dialogs: Lieblingsorte and Lieblingsstrecken Selection Verification**
- **Jira**: ``ATT-1593``
- **Test Description**:
  1. *Workout Filter Criteria Domain & Persistence Tests (`WorkoutFilterCriteriaClusterTest.kt`)*:
  • Verify `matches` returns true when `workout.clusterId == criteria.clusterId`.
  • Verify `matches` returns false when `workout.clusterId != criteria.clusterId`.
  • Verify `activeFilterCount` increments by 1 when `clusterId != null`.
  • Verify round-trip JSON serialization and deserialization retains `clusterId` and `clusterName`.
  • Verify backward compatibility with JSON strings lacking `clusterId`.
  2. *Workout Filter Bottom Sheet Component Tests (`WorkoutFilterBottomSheetLocationClusterTest.kt`)*:
  • Verify "Lieblingsorte" section renders `FilterChip` items for available `KnownLocationItem`s.
  • Verify "Lieblingsstrecken" section renders `FilterChip` items for available `WorkoutCluster`s.
  • Verify clicking chips toggles selection on/off.
  • Verify `onApply` propagates both location and cluster filter criteria.
  • Verify `onClearAll` resets both location and cluster selections.
  3. *Cluster Filter Bottom Sheet Component Tests (`ClusterFilterBottomSheetLocationTest.kt`)*:
  • Verify "Lieblingsorte" section renders `FilterChip` items for available `KnownLocationItem`s.
  • Verify selecting location sets coordinates and radius in `ClusterFilterCriteria`.
  • Verify `onClearAll` resets location selection.
  4. *Active Filter Chips Rows Integration Tests (`ActiveFilterChipsRowClusterTest.kt`, `ActiveClusterFilterChipsRowLocationTest.kt`)*:
  • Verify `ActiveFilterChipsRow` renders `"🗺️ ${clusterName}"` and dismisses on click.
  • Verify `ActiveClusterFilterChipsRow` renders `"📍 ${startLocationName}"` and dismisses on click.
  5. *9-Language Localization Audit (`TranslationParityTest.java`)*:
  • Verify string tokens `known_locations_title`, `my_locations`, and `filter_start_location` across all 9 locales (EN, DE, ES, FR, IT, JA, NL, PL, PT) with 0 missing translations.
  6. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` across all modules to verify 100% test pass rate with 0 regressions.

### TST-UI-142: **Lieblingsorte Compact Routes Badge & Drill-Down Verification** [Compact routes badge renders on cards with linked clusters and navigates with preset spatial filter; omitted on zero clusters; 100% localization parity; zero regressions.]
- **Requirement(s)**: `REQ-UI-188`
- **Component**: **Lieblingsorte Compact Routes Badge & Drill-Down Verification**
- **Jira**: ``ATT-1594``
- **Test Description**:
  1. *Routes Badge Component Tests (`KnownLocationCardRoutesBadgeTest.kt`)*:
  • Verify routes badge is displayed with correct pluralized count when `linkedClusters.isNotEmpty()`.
  • Verify routes badge is omitted when `linkedClusters.isEmpty()`.
  • Verify tapping routes badge invokes `onShowRoutes(item)`.
  • Verify tapping card body outside badges continues to invoke `onEdit(item)`.
  • Verify long-pressing card body strictly preserves universal delete-only context menu.
  2. *App Navigation & Spatial Filter Wiring Tests (`ATrainingTrackerAppRoutesDrillDownTest.kt`)*:
  • Verify `onShowRoutes` callback constructs `ClusterFilterCriteria` with matching location coordinates, radius, and name.
  3. *9-Language Localization Audit (`TranslationParityTest.kt`)*:
  • Verify plural resource `known_locations_routes` and string `known_locations_view_routes` across all 9 supported locales (EN, DE, ES, FR, IT, JA, NL, PL, PT) with 0 missing translations.
  4. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` across all modules to verify 100% test pass rate with 0 regressions.

### TST-UI-143: **Bottom Sheet Boundaries, Curvature, Drop Shadow & Affordance Verification** [Bottom sheets have distinct 20dp rounded top corners, 1dp outlineVariant contour border, 8dp shadow elevation, and prominent drag handle without color shifts; 100% test pass rate.]
- **Requirement(s)**: `REQ-UI-189`
- **Component**: **Bottom Sheet Boundaries, Curvature, Drop Shadow & Affordance Verification**
- **Jira**: ``ATT-1588``
- **Test Description**:
  1. *Design Tokens & Modifier Unit Tests (`BottomSheetDesignTest.kt`)*:
  • Verify `BottomSheetDesign` constants (`SheetCornerRadius = 20.dp`, `SheetShadowElevation = 8.dp`, `SheetTonalElevation = 2.dp`, `BorderWidth = 1.dp`).
  • Verify `SheetShape` topStart and topEnd corner radii equal 20dp, and bottomStart/bottomEnd equal 0dp.
  2. *Scaffold & Modal Bottom Sheet Visual Contract Tests (`BottomSheetVisualContractTest.kt`)*:
  • Verify `SensorGridScreen` `BottomSheetScaffold` uses `sheetShape = SheetShape`, `sheetShadowElevation = 8.dp`, `sheetTonalElevation = 2.dp`, and wraps content with `sheetContour`.
  • Verify `MapDetailLayout` when `useStatusBarsPadding == false` does not force `shape = RectangleShape`.
  • Verify `AppModalBottomSheet` and `FilterBottomSheetScaffold` use 20dp top curvature and 1dp border contour stroke.
  3. *MinimumDragHandle Pill Affordance Tests (`MinimumDragHandleTest.kt`)*:
  • Verify drag handle dimensions (36dp x 4dp) and padded bounds (12dp top, 6dp bottom).
  4. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` across all modules to verify 100% test pass rate with 0 regressions.

### TST-UI-144: **Dark Theme Tonal Progression, Surface Elevation, Lap Button & Contrast Verification** [Dark theme displays coordinated slate-blue progression between headers and tabs, elevated cards have distinct surface color and subtle outline, disabled lap button has muted dark background, and XML colors meet WCAG contrast; 100% test pass rate.]
- **Requirement(s)**: `REQ-UI-190`
- **Component**: **Dark Theme Tonal Progression, Surface Elevation, Lap Button & Contrast Verification**
- **Jira**: ``ATT-1585``
- **Test Description**:
  1. *Dark Color Scheme Tonal Hierarchy Unit Tests (`DarkThemeTonalHierarchyTest.kt`)*:
  • Verify `primaryContainer` equals `DarkPrimaryContainer` (`#162032`) and `surfaceContainerHighest` equals `surfaceContainerHighestDark` (`#1B2436`).
  • Verify graduated surface container values: `surfaceContainerLowest` < `surfaceContainerLow` < `surfaceContainer` < `surfaceContainerHigh`.
  • Verify `background` is `#121214` and distinct from elevated card container `surfaceContainerLow` (`#1C2028`).
  2. *Card Border & Visual Separation Tests (`DarkThemeVisualContractTest.kt`)*:
  • Verify `MappableListItem` applies `outlineVariant` border stroke to `ElevatedCard`.
  • Verify `outlineVariant` in `DarkColorScheme` is non-null and equals `DarkOutlineVariant` (`#2B3342`).
  3. *Theme-Aware Lap Button & XML Color Verification (`LapButtonDarkThemeTest.kt`)*:
  • Verify `res/values-night/color.xml` defines `lap_button_disabled_background` as `#22252C` and `lap_button_disabled_text` as `#5A6270`.
  • Verify `color_primary` is `#A6C8FF` and `color_on_primary` is `#003060`.
  • Verify WCAG AA contrast ratio (>= 4.5:1) for primary action text and icons.
  4. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` across all modules to verify 100% test pass rate with 0 regressions.

### TST-UI-145: **List Item Heading Click Standardization & Dedicated Route Edit Button Verification** [Route header tap standardizes on route inspection/details navigation; dedicated 48dp Material 3 edit button opens route editor; context menu strictly preserves REQ-UI-061 delete-only invariant; 100% localization parity; zero regressions.]
- **Requirement(s)**: `REQ-UI-191`
- **Component**: **List Item Heading Click Standardization & Dedicated Route Edit Button Verification**
- **Jira**: ``ATT-1541``
- **Test Description**:
  1. *Route Item Heading Click Navigation Unit Tests (`RouteItemClickStandardizationTest.kt`)*:
  • Verify that tapping `RouteSummaryHeader` invokes `onMapClick(summary.id)` to navigate to route inspection/details.
  • Verify that tapping map preview or elevation profile continues to invoke `onMapClick(summary.id)`.
  • Verify that tapping the card body invokes `onMapClick(summary.id)`.
  2. *Dedicated Route Header Edit Button Contract Tests (`RouteItemClickStandardizationTest.kt`)*:
  • Verify that `RouteSummaryHeader` renders a dedicated edit icon button (`Icons.Default.Edit`) with `MaterialTheme.colorScheme.primary` tint.
  • Verify that the edit button has a minimum touch target bounding box of $48\times 48\text{dp}$.
  • Verify that tapping the edit button invokes `onEditClick` (opening `EditRouteScreen`).
  • Verify localized accessibility content description `@string/route_edit`.
  3. *Context Menu Invariant Tests (`RouteItemClickStandardizationTest.kt`)*:
  • Verify that long-pressing `RouteItem` opens the top-left anchored context menu containing strictly deletion (`@string/delete`) and Strava save-as-local when applicable, with zero non-destructive edit entries per `REQ-UI-061`.
  4. *9-Language Localization Audit (`TranslationParityTest.kt`)*:
  • Verify `@string/route_edit` exists across all 9 supported application locales (EN, DE, ES, FR, IT, JA, NL, PL, PT) with 0 missing translations.
  5. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` across all modules to verify 100% test pass rate with 0 regressions.

### TST-UI-146: **Elevation Profile Zoom, Pan, Reset & Synchronized Scrubbing Verification** [Horizontal pinch zoom expands visible distance window up to 10x; horizontal drag smoothly pans visible window within [0..totalDist]; double-tap and reset button restore 1.0x extent; scrubbing accurately reflects absolute distance; adaptive distance ticks format cleanly; 100% test pass rate.]
- **Requirement(s)**: `REQ-UI-192`
- **Component**: **Elevation Profile Zoom, Pan, Reset & Synchronized Scrubbing Verification**
- **Jira**: ``ATT-527``
- **Test Description**:
  1. *Mathematical Window & Clamping Unit Tests (`ElevationProfileZoomMathTest.kt`)*:
  • Default scale: Verify $Z = 1.0$, $D_{\text{start}} = 0.0$, $W_{\text{vis}} = D_{\text{total}}$.
  • Centroid zoom calculation: Verify zoom multiplier updates $W_{\text{vis}}$ while keeping centroid distance stationary under finger.
  • Clamping boundaries: Verify $Z$ is clamped to $[1.0, 10.0]$ and $D_{\text{start}}$ is clamped strictly to $[0.0, D_{\text{total}} - W_{\text{vis}}]$.
  • Short track guard: Verify routes $\le 10\text{m}$ remain fixed at $1.0x$.
  2. *Gesture & Interaction Contract Tests*:
  • Double-tap reset: Verify double-tap restores $Z = 1.0x$ and $D_{\text{start}} = 0.0$.
  • Overlay controls: Verify zoom-in (+), zoom-out (-), and reset button trigger expected state changes.
  • Synchronized scrubbing: Verify canvas touch coordinate maps to exact absolute workout distance $d = D_{\text{start}} + u \cdot W_{\text{vis}}$ in zoomed state.
  • Marker clipping: Verify coordinate marker and label hide or pin cleanly when selected distance lies outside $[D_{\text{start}}, D_{\text{end}}]$.
  3. *Adaptive Ticks & Label Tests*:
  • Verify distance tick intervals dynamically adapt based on visible distance range $W_{\text{vis}}$ for both Metric and Imperial units.
  4. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` across all modules to verify 100% test pass rate with 0 regressions.

### TST-UI-147: **Workout Filter Categorized Equipment Partitioning & Tab-Awareness Verification** [Workout filter equipment is partitioned cleanly into Bikes and Shoes with sport-tab context awareness, mutual exclusion, and automatic stale selection clearing; 100% test pass rate.]
- **Requirement(s)**: `REQ-UI-193`
- **Component**: **Workout Filter Categorized Equipment Partitioning & Tab-Awareness Verification**
- **Jira**: ``ATT-1641``
- **Test Description**:
  1. *Equipment Category Partitioning Unit Tests (`WorkoutFilterEquipmentPartitioningTest.kt`)*:
  • Verify bike workouts partition into `availableBikes`.
  • Verify run workouts partition into `availableShoes`.
  • Verify other sports partition into `availableOtherEquipment`.
  2. *Tab-Aware Filtering Verification*:
  • When `activeBSportType == BSportType.BIKE`, verify only bikes are available and shoes/other are empty.
  • When `activeBSportType == BSportType.RUN`, verify only shoes are available and bikes/other are empty.
  • When `activeBSportType == null`, verify both bikes and shoes are available.
  • When sub-sport filter is active (`localSportId != null`), verify only equipment belonging to that specific sub-sport is returned within its category.
  3. *State Reset Invariant Verification*:
  • Verify that selecting equipment sets `localEquipId`.
  • Verify that when switching tabs (e.g. Bike to Run), stale equipment is automatically cleared (`localEquipId = null`).
  4. *9-Language Localization Audit*:
  • Verify `equipment_type_bike`, `equipment_type_shoe`, and `filter_section_equipment` across EN, DE, ES, FR, IT, JA, NL, PL, PT with matching format specifiers.
  5. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` across all modules to verify 100% test pass rate with 0 regressions.

### TST-UI-148: **Filter Dialogs Clean Chip Formatting (Emoji Removal) & Prioritized Section Ordering Verification** [Filter dialog chip labels and active filter chip strips render cleanly without emojis (📍, 🗺️); dialog sections are reordered with favorite locations and tracks placed at the bottom; 100% test pass rate with zero regressions.]
- **Requirement(s)**: `REQ-UI-194`
- **Component**: **Filter Dialogs Clean Chip Formatting (Emoji Removal) & Prioritized Section Ordering Verification**
- **Jira**: ``ATT-1642``
- **Test Description**:
  1. *Clean Chip Formatting Unit Tests (`ActiveFilterChipsRowCleanFormattingTest.kt`, `ActiveFilterChipsRowLocationTest.kt`, `ActiveFilterChipsRowClusterTest.kt`, `ActiveClusterFilterChipsRowLocationTest.kt`)*:
  • Verify that active start location chip label formats as `criteria.startLocationName` or fallback string without `📍 ` prefix.
  • Verify that active route cluster chip label formats as `criteria.clusterName` or fallback string without `🗺️ ` prefix.
  • Verify in `WorkoutFilterBottomSheet` and `ClusterFilterBottomSheet` that chip labels for locations and clusters contain no emojis.
  2. *Section Ordering Verification*:
  • Verify in `WorkoutFilterBottomSheet` that `knownLocations` (Lieblingsorte) and `availableClusters` (Lieblingsstrecken) are positioned at the bottom after `distance` and `duration` sections.
  • Verify in `ClusterFilterBottomSheet` that `knownLocations` (Lieblingsorte) is positioned at the bottom after equipment, distance thresholds, and hit-count thresholds.
  3. *Localization Parity Audit (`TranslationParityTest.kt`)*:
  • Verify all section headers and chip fallback strings across all 9 supported locales (EN, DE, ES, FR, IT, JA, NL, PL, PT) with 0 missing translations.
  4. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` across all modules to verify 100% test pass rate with 0 regressions.

### TST-UI-149: **KnownLocationCard Dedicated Badges Row & Compact Sizing Verification** [KnownLocationCard renders altitude on its own row and places Starts and Strecken badges on a dedicated row with refined compact sizing; 100% test pass rate with zero regressions.]
- **Requirement(s)**: `REQ-UI-195`
- **Component**: **KnownLocationCard Dedicated Badges Row & Compact Sizing Verification**
- **Jira**: ``ATT-1643``
- **Test Description**:
  1. *Layout & Badge Contract Tests (`KnownLocationCardLayoutTest.kt`)*:
  • Verify that altitude metric is separated from interactive badges row.
  • Verify that Starts-Badge and Routes-Badge reside in a dedicated badges container.
  • Verify compact badge shape (`RoundedCornerShape(8.dp)`), icon dimensions (`14.dp`, `12.dp`), and padding.
  • Verify preservation of `location_starts_badge_${item.id}` and `location_routes_badge_${item.id}` test tags.
  2. *Navigation Drill-Down Preservation Tests (`KnownLocationsScreenDrillDownTest.kt`)*:
  • Verify `onShowWorkouts` and `onShowRoutes` callback dispatch.
  3. *Context Menu Invariant Tests (`GlobalDeleteContextMenuAuditTest.kt`)*:
  • Verify `REQ-UI-061` compliance (no overflow button, delete-only menu).
  4. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` across all modules to verify 100% test pass rate with 0 regressions.

### TST-UI-150: **MinimumDragHandle Dimensions, Unified Popup Surface and Customization Unit Test** [Drag handle dimensions conform to 32dp x 3dp, color defaults to outlineVariant(0.6f), padding is balanced (8dp top, 4dp bottom), bottom sheet hierarchy uniformly applies MaterialTheme.colorScheme.surface, and core components integrity is maintained; 100% test pass rate.]
- **Requirement(s)**: `REQ-UI-196`
- **Component**: **MinimumDragHandle Dimensions, Unified Popup Surface and Customization Unit Test**
- **Jira**: ``ATT-1644`, `ATT-1735``
- **Test Description**:
  1. *Design Tokens Unit Verification (`MinimumDragHandleTest.kt`)*:
  • Verify that `BottomSheetDesign.DragHandleWidth` equals `32.dp`.
  • Verify that `BottomSheetDesign.DragHandleHeight` equals `3.dp`.
  2. *Composable Function Reflection & Contract Tests (`MinimumDragHandleTest.kt`)*:
  • Verify that `MinimumDragHandle` exists, is public, and supports default parameters for width, height, color, and padding.
  3. *Unified Background Surface Contract Verification (`LiveSegmentSheetLayoutTest.kt`)*:
  • Verify that `SensorGridScreen.kt`, `LIveSegmentSheet.kt`, and `MapDetailLayout.kt` consistently apply `MaterialTheme.colorScheme.surface` for bottom sheet containers.
  4. *Core Components Architecture Integrity (`CoreComponentsIntegrityTest.kt`)*:
  • Verify `CoreComponentsIntegrityTest` confirms presence and contract for `MinimumDragHandle`.
  5. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` across all modules to verify 100% test pass rate with 0 regressions.

### TST-UI-151: **Elevation Profile Contextual Zoom Controls Visibility and Non-Overlapping Multi-Metric Badge Layout Verification** [Zoom controls suppressed in list previews and LiveSegment popup, and enabled in MapDetailLayout detail views; 3-layer vertical layout guarantees zero collision between controls row, scrubbing badge, and canvas curve; 100% test pass rate.]
- **Requirement(s)**: `REQ-UI-197`
- **Component**: **Elevation Profile Contextual Zoom Controls Visibility and Non-Overlapping Multi-Metric Badge Layout Verification**
- **Jira**: ``ATT-1647`, `ATT-1736`, `ATT-1737``
- **Test Description**:
  1. *Contextual Zoom Controls Parameterization Tests (`ElevationProfileLayoutTest.kt`, `LiveSegmentSheetLayoutTest.kt`)*:
  • Verify `showZoomControls` defaults to `false` in both overloads of `ElevationProfile`.
  • Verify list preview callers (`WorkoutSummary.kt`, `RouteItem.kt`, `SegmentItem.kt`) do not enable zoom controls.
  • Verify `MapDetailLayout.kt` defines default parameter `showZoomControls: Boolean = true` and forwards it to `ElevationProfile`.
  • Verify `LiveSegmentSheet.kt` explicitly passes `showZoomControls = false` to `MapDetailLayout` to suppress zoom controls in the LiveSegment popup during tracking cockpit view (`LiveSegmentSheetLayoutTest.kt`).
  2. *Three-Layer Non-Overlapping Vertical Layout Architecture Verification (`ElevationProfileLayoutTest.kt`)*:
  • Verify source contract that controls row height is constrained to `24.dp` at `top = 2.dp` (Layer 1: interval `2.dp .. 26.dp`).
  • Verify `ScrubbingTelemetryBadge` modifier is anchored at `Alignment.TopCenter` with `padding(top = 28.dp)` (Layer 2: interval `28.dp .. 70.dp`), guaranteeing $\ge 2\text{dp}$ clearance below controls row (26.dp).
  • Verify `Canvas` top padding is `72.dp` when `showZoomControls == true` and `16.dp` when `false` (Layer 3: starts at `72.dp`), with total canvas height = `cachedData.adaptiveHeight + 48.dp` preserving exact plotting height.
  • Verify `testVerticalLayoutGeometry_guaranteesNonOverlappingBounds()` confirms $\ge 2.0\text{dp}$ clearance between all adjacent layers.
  3. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` across all modules to verify 100% test pass rate with 0 regressions.

### TST-UI-152: **Cockpit Sensor Field Domain-Specific Default Filter Presets Verification** [Centralized filter defaults registry defines domain-optimized presets for Speed (2s), Cadence (2s), Pace (5s), VAM (15s), Slope (5s), and Power (3s); accumulators and HR strictly protected with Instantaneous (1s); EditSensorFieldViewModel dynamically adopts defaults; 100% test pass rate.]
- **Requirement(s)**: `REQ-UI-198`
- **Component**: **Cockpit Sensor Field Domain-Specific Default Filter Presets Verification**
- **Jira**: ``ATT-1624`, `ATT-3310``
- **Test Description**:
  1. *Centralized Defaults Registry Unit Verification (`SensorFilterDefaultsTest.kt`)*:
  • Verify `getDefaultFilterConfig` for `SensorType.POWER` returns `MOVING_AVERAGE_TIME`, `3.0`, `"sec"`.\u003cbr\u003e• Verify `SensorType.SPEED_mps` returns `MOVING_AVERAGE_TIME`, `2.0`, `"sec"` (ATT-3310).\u003cbr\u003e• Verify `SensorType.CADENCE` returns `MOVING_AVERAGE_TIME`, `2.0`, `"sec"` (ATT-3310).\u003cbr\u003e• Verify `SensorType.PACE_spm` returns `MOVING_AVERAGE_TIME`, `5.0`, `"sec"`.\u003cbr\u003e• Verify `SensorType.VERTICAL_SPEED` returns `MOVING_AVERAGE_TIME`, `15.0`, `"sec"`.\u003cbr\u003e• Verify `SensorType.SLOPE` returns `MOVING_AVERAGE_TIME`, `5.0`, `"sec"`.\u003cbr\u003e• Verify all accumulator sensors (`DISTANCE_m`, `TIME_ACTIVE`, `CALORIES`, `LAP_NR`, `DISTANCE_m_LAP`, `TIME_LAP`, `TIME_TOTAL`, `PHONE_BATTERY`, `BATTERY_REMAINING_TIME`) return `INSTANTANEOUS`, `1.0`, `"sec"`.\u003cbr\u003e• Verify continuous physiological and environmental metrics (`HR`, `ALTITUDE`, `TEMPERATURE`) return `INSTANTANEOUS`, `1.0`.\u003cbr\u003e• Verify exhaustive coverage: loop across all entries of `SensorType.values()`, asserting non-null and valid configuration.\u003cbr\u003e• Verify `@JvmStatic` helper methods (`getDefaultFilterType`, `getDefaultFilterConstant`, `getDefaultUnit`).\u003cbr\u003e2. *ViewModel Dynamic Defaults Unit Verification (`EditSensorFieldViewModelTest.kt`)*:\u003cbr\u003e• Initialize `EditSensorFieldViewModel` and call `onSensorTypeChanged(SensorType.SPEED_mps)`: verify `selectedFilterType == FilterType.MOVING_AVERAGE_TIME` and `filterConstant == 2.0`.\u003cbr\u003e• Call `onSensorTypeChanged(SensorType.CADENCE)`: verify `selectedFilterType == FilterType.MOVING_AVERAGE_TIME` and `filterConstant == 2.0`.\u003cbr\u003e• Call `onSensorTypeChanged(SensorType.PACE_spm)`: verify `selectedFilterType == FilterType.MOVING_AVERAGE_TIME` and `filterConstant == 5.0`.\u003cbr\u003e• Call `onSensorTypeChanged(SensorType.VERTICAL_SPEED)`: verify `selectedFilterType == FilterType.MOVING_AVERAGE_TIME` and `filterConstant == 15.0`.\u003cbr\u003e• Call `onSensorTypeChanged(SensorType.SLOPE)`: verify `selectedFilterType == FilterType.MOVING_AVERAGE_TIME` and `filterConstant == 5.0`.\u003cbr\u003e• Call `onSensorTypeChanged(SensorType.POWER)`: verify `selectedFilterType == FilterType.MOVING_AVERAGE_TIME` and `filterConstant == 3.0`.\u003cbr\u003e• Call `onSensorTypeChanged(SensorType.DISTANCE_m)`: verify `selectedFilterType == FilterType.INSTANTANEOUS` and `filterConstant == 1.0`.\u003cbr\u003e• Verify `onFilterConfigDismissed()` resets to domain-specific defaults when sensor type was changed.\u003cbr\u003e3. *Clean-Room Full Suite Regression Execution*:\u003cbr\u003e• Execute `./gradlew testDebugUnitTest` across all modules to verify 100% test pass rate with 0 regressions.

### TST-UI-153: **Lieblingsorte Cockpit Location Calibration Badge & Altimeter Synchronization Verification** [Altimeter calibration dispatched on geofence entry, pending warmup handled, badge accurately differentiates standby (detected) from calibrated, 9-language localization parity maintained; 100% test pass rate.]
- **Requirement(s)**: `REQ-UI-199`
- **Component**: **Lieblingsorte Cockpit Location Calibration Badge & Altimeter Synchronization Verification**
- **Jira**: ``ATT-1617``
- **Test Description**:
  1. *AltitudeFromPressureDevice Calibration Unit Tests (`AltitudeFromPressureDeviceTest.kt`)*:
  • `testCalibrate_whenRawAltitudeAvailable_setsCorrectionAndMarksCalibrated`: Verify `calibrate(436.0)` sets `mAltitudeCorrection = 436.0 - raw`, marks `isCalibrated() == true`, updates `mAltitudeSensor`, and broadcasts `ALTITUDE_CORRECTION_INTENT`.
  • `testCalibrate_whenRawAltitudeNaN_cachesPendingReferenceAltitude`: Verify `calibrate(436.0)` caches `436.0` when `mLastRawAltitude` is NaN, returns false, and applies immediately when `handlePressureMeasurement(pressure)` arrives.
  • `testCalibrate_multipleInvocationsWithSameAltitude_idempotent`: Verify repeated calls with same altitude do not produce duplicate redundant broadcasts.
  2. *BANALService & Repository State Flow Unit Tests (`BANALServiceRepositoryTest.kt`)*:
  • Verify `banalServiceRepository.isAltimeterCalibrated` reflects binder state.
  • Verify `banalServiceRepository.calibrateAltimeter(...)` delegates to binder.
  3. *TrackingTabsViewModel Calibration Dispatch & State Flow Unit Tests (`TrackingTabsViewModelLocationTest.kt`)*:
  • `testInsideGeofence_whenAltimeterNotCalibrated_emitsStatusWithIsCalibratedFalse`: When inside geofence but `isAltimeterCalibrated == false`, verify `locationCalibrationStatus` emits `isCalibrated = false`.
  • `testInsideGeofence_dispatchesCalibrateAltimeterWithLocationAltitude`: When inside geofence, verify `calibrateAltimeter(altitude)` is invoked on `banalServiceRepository`.
  • `testInsideGeofence_whenAltimeterCalibrated_emitsStatusWithIsCalibratedTrue`: When `isAltimeterCalibrated == true`, verify `locationCalibrationStatus` emits `isCalibrated = true`.
  4. *LocationCalibrationBadge Composable Unit Tests (`LocationCalibrationBadgeTest.kt`)*:
  • `testBadge_whenCalibrated_rendersCalibratedFormat`: Verify rendered text matches `location_calibrated_format` (e.g. "Zu Hause (436 m kalibriert)").
  • `testBadge_whenNotCalibrated_rendersDetectedFormat`: Verify rendered text matches `location_detected_format` (e.g. "Zu Hause erkannt").
  5. *9-Language Localization Unit Test (`LocationCalibrationLocalizationTest.kt`)*:
  • Verify all 9 locales define `location_detected_format` with single string placeholder `%1$s` (or `%s`).
  6. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` across all modules.

### TST-UI-154: **Cockpit Sensor Grid Pick & Place Reordering & Swapping Verification** [Sensor tiles swapped and moved atomically in database; grid coordinates normalized without gaps; Move Mode guided by banner and primary highlight; 9-language localization parity maintained; 100% test pass rate.]
- **Requirement(s)**: `REQ-UI-200`
- **Component**: **Cockpit Sensor Grid Pick & Place Reordering & Swapping Verification**
- **Jira**: ``ATT-1629``
- **Test Description**:
  1. *Database Tile Swapping & Coordinate Invariants (`TrackingViewsDatabaseManagerTest.kt`)*:
  • `testSwapSensorFields_swapsCoordinatesAtomically`: Swap tile A (row 1, col 1) and tile B (row 2, col 2); verify (row, col) coordinates are exchanged in `ROWS_TABLE`.
  • `testSwapSensorFields_sameTile_isNoOp`: Swap tile A with tile A; verify no changes.
  2. *Database Tile Moving & Grid Normalization (`TrackingViewsDatabaseManagerTest.kt`)*:
  • `testMoveSensorField_toNewRow_createsContiguousRowAndCompactsOrigin`: Move tile from multi-column row to new row (`col = -1`); verify origin row column count decreases, new row created, row numbers remain contiguous 1..R.
  • `testMoveSensorField_soleFieldInRow_removesEmptyRow`: Move the only field in row 2 to row 4; verify row 2 is removed, remaining rows shift down, and coordinates remain contiguous 1..N.
  • `testMoveSensorField_toExistingRowCol_shiftsTargetColumns`: Move tile to existing row before column C; verify target row columns shift right and tile placed at C.
  3. *ViewModel Move Mode State & Dispatch (`TrackingViewModelGridTest.kt`)*:
  • `testSelectFieldForMove_setsSelectedState`: Verify selecting tile sets `selectedFieldForMove`.
  • `testSwapFields_dispatchesDatabaseSwapAndClearsSelection`: Verify tapping another tile triggers swap on repository and clears `selectedFieldForMove`.
  • `testMoveField_dispatchesDatabaseMoveAndClearsSelection`: Verify tapping adder triggers move on repository and clears `selectedFieldForMove`.
  • `testCancelMove_clearsSelectedStateWithoutDatabaseChanges`: Verify canceling clears `selectedFieldForMove`.
  4. *Compose Grid UI Interaction & Accessibility (`SensorGridScreenReorderTest.kt`)*:
  • Verify guidance banner displays when `selectedFieldForMove != null` with cancel button.
  • Verify selected tile applies primary accent border.
  • Verify tapping another tile routes to swap action.
  • Verify tapping adder routes to move action.
  5. *9-Language Localization Unit Test (`SensorGridLocalizationTest.kt`)*:
  • Verify `move_tile_banner_instruction` and `move_tile_cancel` exist across all 9 locales (values, values-de, values-es, values-fr, values-it, values-ja, values-nl, values-pl, values-pt).
  6. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` across all modules.

### TST-UI-155: **Aftermath Synchronized Multi-Metric Scrubbing & X-Axis Domain Verification** [Enriched PathPoint telemetry extracted from sample db, configurable ProfileXAxisDomain managed in DataStore, adaptive time ticks generated, multi-metric scrubbing overlay renders and falls back gracefully, 9-language localization parity maintained; 100% test pass rate.]
- **Requirement(s)**: `REQ-UI-201`
- **Component**: **Aftermath Synchronized Multi-Metric Scrubbing & X-Axis Domain Verification**
- **Jira**: ``ATT-1391``
- **Test Description**:
  1. *PathPoint Model & Telemetry Extraction Unit Tests (`PathPointTelemetryTest.kt`)*:
  • Verify `PathPoint` default arguments preserve backward compatibility.
  • Verify `WorkoutRepository.getWorkoutTrackPoints` extracts HR, Power, Speed, Slope, and Time from sample table cursor.
  2. *ProfileXAxisDomain Preference & DataStore Unit Tests (`ProfileXAxisDomainTest.kt`)*:
  • Verify default domain is `ProfileXAxisDomain.DISTANCE`.
  • Verify saving `TIME` persists and emits via `tuningConfigFlow`.
  • Verify factory reset restores `DISTANCE`.
  3. *ElevationProfileZoomMath Adaptive Time Step Tests (`ElevationProfileZoomMathTimeTest.kt`)*:
  • Verify `calculateAdaptiveTimeStep` produces expected intervals for short (<5m), medium (30m-2h), and long (4h+) sessions.
  4. *ElevationProfile Multi-Metric Scrubbing Unit Tests (`ElevationProfileScrubbingTest.kt`)*:
  • Verify nearest `PathPoint` resolution during scrubbing.
  • Verify graceful metric omission when telemetry is null.
  5. *9-Language Localization Unit Test (`AftermathTuningLocalizationTest.kt`)*:
  • Verify string resources (`tuning_cat_aftermath`, `tuning_profile_x_axis_title`, `tuning_profile_x_axis_desc`, `tuning_profile_x_axis_distance`, `tuning_profile_x_axis_time`) exist across all 9 locales with zero format specifier mismatches.
  6. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` across all modules to verify 100% test pass rate with 0 regressions.

### TST-UI-156: **Aftermath Heart Rate 5-Zone Distribution Vertical Column Chart Verification** [HR samples mapped to 5 zones based on athlete thresholds; duration accumulated and clamped; degenerate identical timestamps fall back to unit duration; missing TIME_ACTIVE falls back to time column; 5-column vertical histogram renders accurately with X-axis zones and Y-axis duration; missing HR handled cleanly; 9-language parity maintained; 100% test pass rate.]
- **Requirement(s)**: `REQ-UI-202`
- **Component**: **Aftermath Heart Rate 5-Zone Distribution Vertical Column Chart Verification**
- **Jira**: ``ATT-1389`, `ATT-1739`, `ATT-1811``
- **Test Description**:
  1. *ZoneDistributionCalculator Unit Tests (`ZoneDistributionCalculatorTest.kt`)*:
  • `testCalculateHeartRateDistribution_thresholdClassification`: Verify samples below, at, and above thresholds are assigned accurately to Zones 1..5.
  • `testCalculateHeartRateDistribution_durationAccumulationAndClamping`: Verify delta time accumulation and clamping to 5s max during tracking pauses.
  • `testCalculateHeartRateDistribution_zeroDurationOrNullSamples_returnsNull`: Verify null return when no HR samples or zero duration.
  • `testCalculateHeartRateDistribution_percentageSum`: Verify sum of percentages equals 100% (or 0% for unused zones) and zero-duration zones have 0% width.
  • `testCalculateHeartRateDistribution_degenerateIdenticalTimestamps_fallsBackToUnitDuration`: Verify that samples with identical timestamps fall back to 1s unit duration rather than collapsing to 1s total.
  2. *WorkoutRepository HR Extraction Unit Tests (`WorkoutRepositoryZoneTest.kt`)*:
  • Verify sport type threshold selection (Bike vs Run/other).
  • Verify query with missing HR column returns null safely.
  • Verify query with valid HR samples returns populated `ZoneDistributionData`.
  • Verify fallback to `time` column when `TIME_ACTIVE` and `TIME_TOTAL` are null.
  3. *ViewModel State Flow Unit Tests (`TrackOnMapAftermathViewModelZoneTest.kt`)*:
  • Verify `loadAftermathData` populates `hrZoneDistribution` in UI state.
  4. *Vertical Column Chart UI Unit Tests (`ZoneDistributionChartTest.kt`)*:
  • Verify column relative height calculations scale to max zone duration with minimum visibility height.
  • Verify 5 columns correspond to Zones 1..5 on X-axis.
  5. *9-Language Localization Parity Unit Test (`HeartRateZoneLocalizationTest.kt`)*:
  • Verify `aftermath_hr_zones_title` is defined across all 9 locales (en, de, es, fr, it, ja, nl, pl, pt).
  6. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` across all modules to verify 100% test pass rate with 0 regressions.

### TST-UI-157: **Aftermath Cycling Power 5-Zone Distribution Vertical Column Chart Verification** [Power samples mapped to 5 zones based on athlete thresholds; duration accumulated and clamped; degenerate identical timestamps fall back to unit duration; missing TIME_ACTIVE falls back to time column; 5-column vertical histogram renders accurately with X-axis zones and Y-axis duration; missing power handled cleanly; 9-language parity maintained; 100% test pass rate.]
- **Requirement(s)**: `REQ-UI-203`
- **Component**: **Aftermath Cycling Power 5-Zone Distribution Vertical Column Chart Verification**
- **Jira**: ``ATT-1390`, `ATT-1739`, `ATT-1811``
- **Test Description**:
  1. *ZoneDistributionCalculator Power Classification Unit Tests (`PowerZoneDistributionCalculatorTest.kt`)*:
  • `testCalculatePowerDistribution_thresholdClassification`: Verify samples below, at, and above thresholds are assigned accurately to Zones 1..5.
  • `testCalculatePowerDistribution_durationAccumulationAndClamping`: Verify delta time accumulation and clamping to 5s max during tracking pauses.
  • `testCalculatePowerDistribution_zeroDurationOrNullSamples_returnsNull`: Verify null return when no power samples or zero duration.
  • `testCalculatePowerDistribution_percentageSum`: Verify sum of percentages equals 100% (or 0% for unused zones) and zero-duration zones have 0% width.
  • `testCalculatePowerDistribution_degenerateIdenticalTimestamps_fallsBackToUnitDuration`: Verify that samples with identical timestamps fall back to 1s unit duration rather than collapsing to 1s total.
  2. *WorkoutRepository Power Extraction Unit Tests (`WorkoutRepositoryZoneTest.kt`)*:
  • Verify bike power threshold retrieval from DataStore.
  • Verify query with missing POWER column returns null safely.
  • Verify query with valid power samples returns populated `ZoneDistributionData`.
  • Verify fallback to `time` column when `TIME_ACTIVE` and `TIME_TOTAL` are null.
  3. *ViewModel State Flow Unit Tests (`TrackOnMapAftermathViewModelZoneTest.kt`)*:
  • Verify `loadAftermathData` populates `powerZoneDistribution` in UI state.
  4. *Vertical Column Chart UI Unit Tests (`ZoneDistributionChartTest.kt`)*:
  • Verify column relative height calculations scale to max zone duration with minimum visibility height.
  • Verify 5 columns correspond to Zones 1..5 on X-axis.
  5. *9-Language Localization Parity Unit Test (`PowerZoneLocalizationTest.kt`)*:
  • Verify `aftermath_power_zones_title` is defined across all 9 locales (en, de, es, fr, it, ja, nl, pl, pt).
  6. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` across all modules to verify 100% test pass rate with 0 regressions.

### TST-UI-158: **Aftermath High-Aesthetic Lap & Interval Split Visualizer Verification** [High-aesthetic Lap & Interval Split Visualizer renders with clean typography, aligned columns, and subtle split bars; fastest split features elegant 'Best' badge; map polyline highlights on selection; 9-language localization parity maintained; 100% test pass rate.]
- **Requirement(s)**: `REQ-UI-204`
- **Component**: **Aftermath High-Aesthetic Lap & Interval Split Visualizer Verification**
- **Jira**: ``ATT-1392`, `ATT-1741`, `ATT-1742``
- **Test Description**:
  1. *Domain Model & Calculator Tests (`LapSplitCalculatorTest.kt`)*:
  • Verify calculation logic, relative ratios $[0.25, 1.0]$, fastest/slowest lap detection, and formatted pace/speed strings for running vs cycling.
  2. *Visual Composable Unit Tests (`LapSplitVisualizerTest.kt`)*:
  • Verify columnar alignment across lap items.
  • Verify absence of animal emojis (`🐇`, `🦔`) and presence of refined 'Best' badge on fastest split.
  • Verify subtle proportional split bar scaling.
  • Verify split item click dispatch for map polyline selection.
  3. *Aftermath & Map Integration Tests*:
  • Verify `LapSplitVisualizerCard` renders in `analyticsContent` of `TrackOnMapScreen` when $\ge 2$ laps exist, and collapses when $< 2$ laps exist.
  • Verify tapping a split highlights the lap segment on the map polyline.
  4. *9-Language Localization Audit (`LapSplitLocalizationTest.kt`)*:
  • Verify `aftermath_laps_splits_title`, `split_badge_best`, `laps_header` across all 9 locales (EN, DE, ES, FR, IT, JA, NL, PL, PT).
  5. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` across all modules to verify 100% test pass rate with 0 regressions.

### TST-UI-159: **Aftermath Enhanced Shareable Workout Snapshot Verification** [Snapshot layout calculated accurately; analytics layer integrated cleanly below elevation profile; absent analytics collapses without phantom space; 100% test pass rate.]
- **Requirement(s)**: `REQ-UI-205`
- **Component**: **Aftermath Enhanced Shareable Workout Snapshot Verification**
- **Jira**: ``ATT-1393``
- **Test Description**:
  1. *WorkoutSnapshotLayoutCalculator Total Height Tests (`WorkoutSnapshotLayoutCalculatorTest.kt`)*:
  • Verify total height calculation accurately sums header, map, elevation, analytics, and branding footer.
  2. *WorkoutSnapshotLayoutCalculator Analytics Omission Tests (`WorkoutSnapshotLayoutCalculatorTest.kt`)*:
  • Verify height calculation with null/zero analytics collapses gracefully without phantom padding.
  3. *WorkoutSnapshotLayoutCalculator Scaling Tests (`WorkoutSnapshotLayoutCalculatorTest.kt`)*:
  • Verify horizontal proportional scaling factor calculation and defensive handling of zero/negative dimensions.
  4. *WorkoutSnapshotLayoutCalculator Section Offsets Tests (`WorkoutSnapshotLayoutCalculatorTest.kt`)*:
  • Verify sequential section vertical offsets stack monotonically without overlap.
  5. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` across all modules to verify 100% test pass rate with 0 regressions.

### TST-UI-160: **Aftermath Continuous Telemetry Metric Graphs & Synchronized Scrubbing Verification** [Metric curves render with dynamic scaling and synchronized cursor; speed vs pace selected by sport; conditional presence checks omit empty graphs cleanly; 9-language translation parity maintained; 100% test pass rate.]
- **Requirement(s)**: `REQ-UI-206`
- **Component**: **Aftermath Continuous Telemetry Metric Graphs & Synchronized Scrubbing Verification**
- **Jira**: ``ATT-1740``
- **Test Description**:
  1. *TelemetryMetricGraph Dynamic Scaling & Bounds Tests (`TelemetryMetricGraphTest.kt`)*:
  • Verify dynamic scaling of metric values to canvas coordinates (min/max scaling, zero-range guard, outlier clamping).
  2. *TelemetryMetricGraph Sport Type Selection Tests (`TelemetryMetricGraphTest.kt`)*:
  • Verify running activities select Pace formatting (`min/km` or `min/mi`) while cycling/other activities select Speed (`km/h` or `mph`).
  3. *TelemetryMetricGraph Conditional Rendering Presence Tests (`TelemetryMetricGraphTest.kt`)*:
  • Verify presence detection: `hasHeartRateData(path)` requires `it.hr != null && it.hr > 0`, `hasSpeedData(path)` requires `it.speedMps != null && it.speedMps > 0.0`, `hasPowerData(path)` requires `it.power != null && it.power > 0`.
  4. *TelemetryMetricGraph Cursor Alignment & Scrubbing Tests (`TelemetryMetricGraphTest.kt`)*:
  • Verify touch coordinate to distance/time mapping and instantaneous value interpolation at `currentDistance`.
  5. *Layout Invariants & Horizontal Alignment Tests (`TelemetryMetricGraphTest.kt`)*:
  • Verify padding invariants (`start = 50.dp, end = 25.dp, bottom = 24.dp`) guaranteeing pixel-perfect alignment.
  6. *9-Language Localization Parity Unit Test (`TelemetryMetricLocalizationTest.kt`)*:
  • Verify all 5 graph heading strings (`graph_heading_elevation`, `graph_heading_heart_rate`, `graph_heading_speed`, `graph_heading_pace`, `graph_heading_power`) are fully defined across all 9 localized resource files (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`).
  7. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` across all modules verifying 0 test failures.

### TST-UI-161: **KnownLocationCard Subtle Ghost Badges & Low-Contrast Styling Verification** [KnownLocationCard renders subtle ghost badges with low-contrast onSurfaceVariant typography and refined icon scale, eliminating visual dominance while preserving 100% drill-down navigation and test tag contracts.]
- **Requirement(s)**: `REQ-UI-207`
- **Component**: **KnownLocationCard Subtle Ghost Badges & Low-Contrast Styling Verification**
- **Jira**: ``ATT-1733``
- **Test Description**:
  1. *Badge Color & Styling Contract Tests (`KnownLocationCardLayoutTest.kt`)*:
  • Verify container color uses `surfaceVariant.copy(alpha = 0.35f)`.
  • Verify `contentColor` uses `onSurfaceVariant`.
  • Verify typography uses `labelMedium` with `FontWeight.Medium`.
  • Verify icon scaling (`13.dp` leading icon, `11.dp` trailing chevron with `TTAlpha.Medium`).
  • Verify absence of saturated `primaryContainer.copy(alpha = 0.5f)` on badges.
  2. *Layout Invariants & Navigation Drill-Down Preservation Tests*:
  • Verify altitude metric precedes the dedicated badges row.
  • Verify preservation of `location_starts_badge_${item.id}` and `location_routes_badge_${item.id}` test tags.
  • Verify `onShowWorkouts` and `onShowRoutes` click dispatch.
  3. *9-Language Localization Audit*:
  • Verify plurals `known_locations_starts` and `known_locations_routes` across all 9 supported locales (EN, DE, ES, FR, IT, JA, NL, PL, PT) with matching format specifiers.
  4. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` across all modules verifying 100% test pass rate with 0 regressions.

### TST-UI-162: **Filter Dialogs 'Start at' Section Heading & 9-Language Localization Verification** [WorkoutFilterBottomSheet and ClusterFilterBottomSheet display contextual 'Start at' / 'Startet bei' section headings with 100% 9-language localization parity while KnownLocationsScreen top app bar remains intact.]
- **Requirement(s)**: `REQ-UI-208`
- **Component**: **Filter Dialogs 'Start at' Section Heading & 9-Language Localization Verification**
- **Jira**: ``ATT-1732``
- **Test Description**:
  1. *Structural Contract Tests (`FilterSectionHeadingLayoutTest.kt`)*:
  • Verify `WorkoutFilterBottomSheet.kt` references `filter_section_start_at` for location filter heading and does NOT reference `known_locations_title`.
  • Verify `ClusterFilterBottomSheet.kt` references `filter_section_start_at` for location filter heading and does NOT reference `known_locations_title`.
  • Verify `KnownLocationsScreen.kt` retains `known_locations_title` for screen top app bar.
  2. *9-Language Localization Audit (`FilterSectionHeadingLocalizationTest.kt`)*:
  • Verify `filter_section_start_at` is defined across all 9 localized resource files (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`).
  • Verify exact localized translations: EN ('Start at'), DE ('Startet bei'), ES ('Comienza en'), FR ('Départ à'), IT ('Partenza da'), JA ('開始地点'), NL ('Start bij'), PL ('Start w'), PT ('Início em').
  3. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` across all modules verifying 100% test pass rate with 0 regressions.

### TST-UI-163: **WorkoutFilterBottomSheet Favorite Tracks Section Removal Verification** [WorkoutFilterBottomSheet eliminates Favorite Tracks (Lieblingsstrecken) filter section and chip cloud, reducing visual bloat and sheet load latency while preserving external route drill-down filters and start location filtering; 100% test pass rate with zero regressions.]
- **Requirement(s)**: `REQ-UI-209`
- **Component**: **WorkoutFilterBottomSheet Favorite Tracks Section Removal Verification**
- **Jira**: ``ATT-1731``
- **Test Description**:
  1. *Structural Contract Unit Tests (`WorkoutFilterBottomSheetClusterRemovalTest.kt`)*:
  • Verify `WorkoutFilterBottomSheet.kt` does not contain Section 9 (`// 9. Favorite Tracks`).
  • Verify `WorkoutFilterBottomSheet.kt` does not declare `localClusterId` or `localClusterName` state variables.
  • Verify `WorkoutFilterBottomSheet.kt` does not declare `availableClusters` parameter.
  • Verify `WorkoutTabsScreen.kt` does not pass `availableClusters` to `WorkoutFilterBottomSheet` composable call.
  2. *Drill-Down Filter Preservation & Regression Tests*:
  • Verify `WorkoutFilterCriteriaClusterTest` passes verifying cluster matching and JSON serialization.
  • Verify `ActiveFilterChipsRowClusterTest` passes verifying active cluster chips row rendering and removal.
  3. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` across all modules verifying 100% test pass rate with 0 regressions.

### TST-UI-164: **Aftermath Detailed Workout Card Configurable Sections Verification** [WorkoutSummary cards support athlete-configurable section visibility for map, elevation, extrema, laps, strava, description, telemetry charts, and zone distribution with DataStore persistence, decoupled media rendering, and 100% 9-language translation parity.]
- **Requirement(s)**: `REQ-UI-210`
- **Component**: **Aftermath Detailed Workout Card Configurable Sections Verification**
- **Jira**: ``ATT-1714``
- **Test Description**:
  1. *Preference DataStore & Serialization Unit Tests (`WorkoutCardSectionPreferencesTest.kt`)*:
  • Verify default values: description, extrema, laps, strava, map, elevation are true; telemetry charts and zone analysis are false.
  • Verify that toggling individual preferences persists accurately to DataStore and emits updated state via `workoutCardPreferencesFlow`.
  • Verify lossless JSON serialization/deserialization of `WorkoutCardSectionPreferences` snapshots.
  2. *WorkoutSummary Visual Contract Unit Tests (`WorkoutSummarySectionsTest.kt`)*:
  • Verify default preferences retain standard existing sections.
  • Verify disabling description, extrema, laps, and strava omits the respective composables.
  • Verify decoupled map-only, elevation-only, both, and neither media rendering modes.
  • Verify mandatory card anchors `WorkoutHeader` and `WorkoutDetails` are always present even when all 8 toggles are false.
  3. *9-Language Localization Audit (`WorkoutCardSettingsLocalizationTest.kt`)*:
  • Verify all section toggle string keys exist across all 9 localized resource files (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`).
  4. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` across all modules verifying 100% test pass rate with 0 regressions.

### TST-UI-165: **Aftermath Edit Workout Dialog Configurable Fields Verification** [Edit Workout dialog supports athlete-configurable metadata field visibility with DataStore persistence, mandatory core field invariants, hidden field data preservation, and 100% 9-language translation parity.]
- **Requirement(s)**: `REQ-UI-211`
- **Component**: **Aftermath Edit Workout Dialog Configurable Fields Verification**
- **Jira**: ``ATT-1713``
- **Test Description**:
  1. *Preference DataStore & Serialization Unit Tests (`EditWorkoutFieldPreferencesTest.kt`)*:
  • Verify default values: all 6 field toggles (cluster, commute/trainer, strava, description, goal, method) are true.
  • Verify that modifying field preferences persists accurately to DataStore and emits updated state via `editWorkoutFieldPreferencesFlow`.\u003cbr\u003e• Verify lossless snapshot serialization/deserialization.
  2. *EditWorkoutScreen Visual Contract Unit Tests (`EditWorkoutFieldsLayoutTest.kt`)*:
  • Verify that disabling individual field toggles omits the corresponding composables from the form.
  • Verify that mandatory core fields (Workout Name, Sport, Equipment) remain visible and functional under all preference combinations.
  3. *Data Preservation Contract Unit Tests (`EditWorkoutDataPreservationTest.kt`)*:
  • Verify that editing a workout where Goal and Method are hidden does NOT clear or corrupt existing Goal and Method database values upon saving.
  4. *9-Language Localization Audit (`EditWorkoutSettingsLocalizationTest.kt`)*:
  • Verify all setting category and field toggle keys exist across all 9 localized resource files (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`).
  5. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` across all modules verifying 100% test pass rate with 0 regressions.

### TST-UI-166: **Cockpit Typography (Font Family & Boldness) Configuration & Rendering Verification** [Live cockpit typography and boldness are configurable in Advanced Settings with DataStore persistence, live preview, clean scaling across all 9 ViewSizes, 9-language localization parity, and zero regressions.]
- **Requirement(s)**: `REQ-UI-212`, `REQ-UI-171`, `REQ-UI-181`
- **Component**: **Cockpit Typography (Font Family & Boldness) Configuration & Rendering Verification**
- **Jira**: ``ATT-1751``
- **Test Description**:
  1. *Preference DataStore & Serialization Unit Tests (`TuningPreferencesCockpitTypographyTest.kt`)*:
  • Verify default values: `CockpitFontFamily.SYSTEM_DEFAULT` and `CockpitFontWeight.SEMI_BOLD`.
  • Verify that saving each font family and weight persists to DataStore and emits updated state via `tuningConfigFlow`.
  • Verify factory reset restores defaults (`SYSTEM_DEFAULT`, `SEMI_BOLD`).
  • Verify defensive parsing against unknown or corrupted string tokens.
  2. *Typography Resolution Unit Tests (`CockpitTypographyResolutionTest.kt`)*:
  • Verify resolution for all 5 font families returns non-null `FontFamily`.
  • Verify fallback behavior on JVM unit test runner does not throw exceptions.
  • Verify `CockpitFontWeight.asFontWeight()` correctly maps to Compose `FontWeight`.
  3. *SensorFieldView Visual & Scaling Contract Tests (`SensorFieldViewTypographyTest.kt`)*:
  • Verify `getSensorValueTextStyle` applies `fontFamily` and `fontWeight` correctly across all 9 `ViewSize` steps.
  • Verify `getSensorUnitTextStyle` inherits configured `fontFamily`.
  • Verify no text clipping or invalid line heights across all combinations.
  4. *9-Language Localization Audit (`CockpitTypographyLocalizationTest.kt`)*:
  • Verify section title `tuning_cat_cockpit_typography`, font family display names, and weight labels across all 9 localized resource files (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`).
  5. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` across all modules verifying 100% test pass rate with 0 regressions.

### TST-UI-167: **Aftermath Resilient Map Preview Visibility & Scrollable Telemetry Layout Verification** [Map preview remains visible with guaranteed viewport height (>= 240 dp), charts and analytics cards are smoothly scrollable, route/segment/sheet views maintain zero regressions, and multi-metric scrubbing operates synchronously; 100% test pass rate.]
- **Requirement(s)**: `REQ-UI-213`, `REQ-UI-206`, `REQ-UI-196`, `REQ-UI-197`
- **Component**: **Aftermath Resilient Map Preview Visibility & Scrollable Telemetry Layout Verification**
- **Jira**: ``ATT-1812``
- **Test Description**:
  1. *MapDetailLayout Visual & Layout Contract Tests (`MapDetailLayoutTest.kt`)*:
  • Verify nullable `analyticsContent` parameter with default null.
  • Verify dynamic overflow detection checking `hasTelemetryGraphs` (HR, speed, power) and `analyticsContent`.
  • Verify Map Box applies `heightIn(min = 240.dp)` when scrollable content is present, preventing 0dp collapse.
  • Verify lower section wraps inside `Modifier.verticalScroll` with flexible weight (`weight(1.2f)`) when scrollable content is present.
  • Verify unscrollable `wrapContentHeight` when scrollable content is absent or when `showMap == false`.
  2. *Multi-Screen Regression & Invariant Tests*:
  • Verify `ElevationProfileLayoutTest` passes verifying `showZoomControls` forwarding.
  • Verify `LiveSegmentSheetLayoutTest` passes verifying background surface contracts and zoom control suppression in live cockpit bottom sheet.
  • Verify `BottomSheetVisualContractTest` passes verifying sheet shape contract.
  3. *Synchronized Scrubbing & Snapshot Sharing Contract Tests (`TelemetryMetricGraphTest.kt`)*:
  • Verify synchronized `selectedDistance` wiring across all graphs and map.
  • Verify `combineWorkoutAndShare` integration and graphics layer recording.
  4. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` across all modules verifying 100% test pass rate with 0 regressions.

### TST-UI-168: **Aftermath Telemetry Metric Graph Ordering & Visual Flow Verification** [Continuous telemetry graphs render in Speed/Pace -> Heart Rate -> Power sequence across inspection screens and summary cards, clustering Heart Rate continuous curve directly adjacent to the 5-zone distribution card with zero regressions.]
- **Requirement(s)**: `REQ-UI-214`, `REQ-UI-206`
- **Component**: **Aftermath Telemetry Metric Graph Ordering & Visual Flow Verification**
- **Jira**: ``ATT-1813``
- **Test Description**:
  1. *MapDetailLayout Graph Ordering Contract Tests (`MapDetailLayoutTest.kt`)*:
  • Verify that in `MapDetailLayout.kt`, the Speed/Pace graph section (`TelemetryMetricType.SPEED`/`PACE` and heading) precedes the Heart Rate graph section (`TelemetryMetricType.HEART_RATE` and heading).
  2. *WorkoutSummary Graph Ordering Contract Tests (`TelemetryMetricGraphTest.kt`)*:
  • Verify that in `WorkoutSummary.kt`, the Speed/Pace graph section precedes the Heart Rate graph section.
  3. *Synchronized Scrubbing & Conditional Rendering Regression Tests (`TelemetryMetricGraphTest.kt`)*:
  • Verify dynamic bounds, sport type selection, conditional presence checks, and synchronized cursor handling.
  4. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` across all modules verifying 100% test pass rate with 0 regressions.

### TST-UI-169: **Aftermath Global Synchronized Horizontal Zoom & Telemetry Alignment Verification** [Horizontal zoom and panning operate synchronously across Elevation Profile, Speed/Pace, Heart Rate, and Power graphs, maintaining identical visible windows and pixel-perfect tick alignment with zero regressions.]
- **Requirement(s)**: `REQ-UI-215`, `REQ-UI-192`, `REQ-UI-206`
- **Component**: **Aftermath Global Synchronized Horizontal Zoom & Telemetry Alignment Verification**
- **Jira**: ``ATT-1814``
- **Test Description**:
  1. *ElevationProfile Zoom State Hoisting Unit Tests (`ElevationProfileLayoutTest.kt`)*:
  • Verify `ElevationProfile` accepts `zoomScale`, `startDist`, and `onZoomChanged`.
  • Verify internal fallback behavior when `onZoomChanged == null`.
  2. *TelemetryMetricGraph Zoom & Scrubbing Unit Tests (`TelemetryMetricGraphTest.kt`)*:
  • Verify `TelemetryMetricGraph` accepts `zoomScale` and `startDist`.
  • Verify curve point projection and clipping under 2.0x, 5.0x zoom.
  • Verify `canvasXToDistance` mapping under zoomed `startDist`.
  • Verify cursor line positioning and out-of-bounds suppression when `currentDistance < startDist` or `currentDistance > startDist + visibleSpan`.
  3. *MapDetailLayout Global Zoom Wiring Contract Tests (`MapDetailLayoutTest.kt`)*:
  • Verify `MapDetailLayout` declares hoisted zoom state and forwards `zoomScale` and `startDist` to `ElevationProfile` and all `TelemetryMetricGraph`s.
  4. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` across all modules verifying 100% test pass rate with 0 regressions.

### TST-UI-170: **Settings/Aftermath Relocation of Aftermath Customization Toggles to Advanced Settings Verification** [Workout card sections and Edit Workout field toggles are cleanly relocated to Advanced Settings Category 4 (Aftermath & Profil-Analytik), restoring clarity to standard Display Settings while preserving DataStore persistence, factory reset, downstream reactivity, and 9-language localization parity.]
- **Requirement(s)**: `REQ-UI-216`, `REQ-UI-210`, `REQ-UI-211`
- **Component**: **Settings/Aftermath Relocation of Aftermath Customization Toggles to Advanced Settings Verification**
- **Jira**: ``ATT-1815``
- **Test Description**:
  1. *DisplaySettingsDialog Cleanup Contract Tests (`DisplaySettingsCleanupTest.kt`)*:
  • Verify that `DisplaySettingsDialog.kt` does NOT reference `settings_workout_card_title` or `settings_edit_workout_title`.
  • Verify that `DisplaySettingsDialog.kt` does NOT import or instantiate `WorkoutCardSectionPreferences` or `EditWorkoutFieldPreferences`.
  • Verify that `DisplaySettingsDialog.kt` retains core display options, theme mode, brightness modes, and the Advanced Settings button.
  2. *AdvancedTuningDialog Category 4 Contract Tests (`AdvancedTuningAftermathContractTest.kt`)*:
  • Verify that `AdvancedTuningDialog.kt` imports and references `WorkoutCardSectionPreferences` and `EditWorkoutFieldPreferences`.
  • Verify that `AdvancedTuningDialog.kt` references `settings_workout_card_title` and all 8 card section string keys.
  • Verify that `AdvancedTuningDialog.kt` references `settings_edit_workout_title` and all 6 edit workout field string keys.
  • Verify that `onSave` persists both preference models and `resetToDefaults` restores both default instances.
  3. *Aftermath Tuning State & Reset Integration Tests (`AftermathTuningSettingsTest.kt`)*:
  • Verify saving modified preferences persists to DataStore and emits updated state via flows.
  • Verify factory reset restores default instances.
  4. *9-Language Localization Audit*:
  • Execute `WorkoutCardSettingsLocalizationTest.kt`, `EditWorkoutSettingsLocalizationTest.kt`, and `AftermathTuningLocalizationTest.kt` verifying all 9 locales (EN, DE, ES, FR, IT, JA, NL, PL, PT).
  5. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` across all modules verifying 100% test pass rate with 0 regressions.

### TST-UI-171: **Lieblingsorte Map Preview Thumbnail & Standardized Typography on KnownLocationCard Verification** [KnownLocationCard title is standardized to titleLarge Bold, map preview thumbnail renders on the right side in Google Maps lite mode with onShowOnMap click trigger and inspection fallback, while card edit, delete context menu, and badge drill-downs remain 100% operational.]
- **Requirement(s)**: `REQ-UI-217`, `REQ-UI-195`, `REQ-UI-207`
- **Component**: **Lieblingsorte Map Preview Thumbnail & Standardized Typography on KnownLocationCard Verification**
- **Jira**: ``ATT-1816``
- **Test Description**:
  1. *Title Typography Contract Tests (`KnownLocationCardLayoutTest.kt`)*:
  • Verify that `item.name` in `KnownLocationCard` utilizes `MaterialTheme.typography.titleLarge` with `FontWeight.Bold`.
  • Verify that `titleMedium` is no longer used for the location title.
  2. *Map Preview Thumbnail Layout & Lite Mode Contract Tests (`KnownLocationCardLayoutTest.kt`)*:
  • Verify that `KnownLocationCard` contains the map preview thumbnail with test tag `location_map_preview_${item.id}`.
  • Verify that the map preview is placed inside an 80dp square `Surface` with `RoundedCornerShape(12.dp)`.
  • Verify that the thumbnail uses `GoogleMapOptions().liteMode(true)`.
  • Verify that the map thumbnail wires `onShowOnMap` to its click handler.
  • Verify that `LocalInspectionMode.current` provides a safe schematic rendering fallback.
  3. *Screen Drill-Down & Context Menu Regression Tests (`KnownLocationsScreenTest.kt`, `KnownLocationsScreenDrillDownTest.kt`)*:
  • Verify that single-tap on the card body still triggers `onEdit`.
  • Verify that long-press on the card body still triggers the universal Delete context menu (`REQ-UI-061`).
  • Verify that tapping the Starts badge triggers `onShowWorkouts`.
  • Verify that tapping the Routes badge triggers `onShowRoutes`.
  4. *9-Language Localization Audit*:
  • Verify 100% string resource integrity across all 9 locales (EN, DE, ES, FR, IT, JA, NL, PL, PT).
  5. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` across all modules verifying 100% test pass rate with 0 regressions.

### TST-UI-172: **Popups, Bottom Sheets & Dialogs Surface Background Standardization Verification** [Bottom sheet scaffolds, modal sheets, settings sheets, live segment popups, and confirmation dialogs cleanly adhere to standardized surface container color tokens with zero tonal elevation, eliminating artificial grey overlays and contrast splits across all supported themes.]
- **Requirement(s)**: `REQ-UI-218`, `REQ-UI-189`, `REQ-UI-196`
- **Component**: **Popups, Bottom Sheets & Dialogs Surface Background Standardization Verification**
- **Jira**: ``ATT-1817``
- **Test Description**:
  1. *Design Tokens Unit Verification (`BottomSheetDesignTest.kt`)*:
  • Verify that `BottomSheetDesign.SheetTonalElevation == 0.dp`.
  • Verify that `SheetCornerRadius` (20.dp), `SheetShadowElevation` (8.dp), `BorderWidth` (1.dp), `DragHandleWidth` (32.dp), and `DragHandleHeight` (3.dp) remain preserved.
  2. *Modal Bottom Sheet Visual Contract Tests (`BottomSheetVisualContractTest.kt`)*:
  • Verify that `AppModalBottomSheet.kt` sets `containerColor = MaterialTheme.colorScheme.surface` and `tonalElevation = BottomSheetDesign.SheetTonalElevation`.
  • Verify that `AppBottomSheetContent` sets `color = MaterialTheme.colorScheme.surface` and `tonalElevation = BottomSheetDesign.SheetTonalElevation`.
  • Verify that `FilterBottomSheetScaffold.kt` sets `containerColor = MaterialTheme.colorScheme.surface` and `tonalElevation = BottomSheetDesign.SheetTonalElevation`.
  3. *Persistent Bottom Sheet Scaffolds Contract Tests (`BottomSheetVisualContractTest.kt`)*:
  • Verify that `MapScreenWithTrack.kt`, `SensorGridScreen.kt`, `PeriodMapScreen.kt`, and `WorkoutClusterHeatmapScreen.kt` specify `sheetContainerColor = MaterialTheme.colorScheme.surface`, `sheetTonalElevation = BottomSheetDesign.SheetTonalElevation`, `sheetShape = BottomSheetDesign.SheetShape`, and `sheetShadowElevation = BottomSheetDesign.SheetShadowElevation`.
  4. *MapDetailLayout & LiveSegment Uniform Surface Contract Tests (`BottomSheetVisualContractTest.kt`)*:
  • Verify that `MapDetailLayout.kt` applies `.background(MaterialTheme.colorScheme.surface)` when `!useStatusBarsPadding`.
  • Verify that slotted header `Surface`, elevation profile `Surface`, and analytics `Surface` bind `color = MaterialTheme.colorScheme.surface`.
  • Verify that `LIveSegmentSheet.kt` header applies `MaterialTheme.colorScheme.surface`.
  5. *Confirmation Dialogs Container Standardization Contract Tests (`BottomSheetVisualContractTest.kt`)*:
  • Verify that `StravaSettingsDialog.kt`, `WorkoutClusterHeatmapScreen.kt`, `WorkoutClustersScreen.kt`, `DevicesTabbedScreen.kt`, and `ImportBackupTabsScreen.kt` set `containerColor = MaterialTheme.colorScheme.surface` and `tonalElevation = 0.dp`.
  6. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` across all modules verifying 100% test pass rate with 0 regressions.

### TST-UI-173: **Aftermath Pace Telemetry Decoding, Stopped Speed Clamping & Y-Axis Label Formatting Verification** [Elevation profile scrubbing badge passes seconds per meter ($1.0/speed$) and clamps stopped speeds to '-- min/km'; TelemetryMetricUtils formats pace in Metric/Imperial correctly; Y-axis pace labels render in athletic mm:ss format; pause points do not distort the vertical dynamic range; 100% test pass rate.]
- **Requirement(s)**: `REQ-UI-219`, `REQ-UI-206`
- **Component**: **Aftermath Pace Telemetry Decoding, Stopped Speed Clamping & Y-Axis Label Formatting Verification**
- **Jira**: ``ATT-1818``
- **Test Description**:
  1. *Scrubbing Pace Decoding & Stopped Clamping Unit Test (`ElevationProfileScrubbingTest.kt`)*:
  • Verify that for running sports (`bSportType == BSportType.RUN`), speed 4.167 m/s (15 km/h) formats to `"4:00 min/km"` (Metric) and `"6:26 min/mile"` (Imperial), eliminating the inverted 69:27 min/km defect.
  • Verify that speeds $< 0.55\text{ m/s}$ (or null) format to `"-- min/km"` (or `"-- min/mile"`).
  • Verify that cycling and hiking sports format speed via `SpeedFormatter` in `km/h` or `mph`.
  2. *TelemetryMetricUtils Pace Formatting & Bounding Unit Tests (`TelemetryMetricGraphTest.kt`)*:
  • Verify that `formatValue` for `PACE` converts minutes/unit to $s/m$ and formats as `"4:00 min/km"` in Metric and `"8:30 min/mile"` in Imperial.
  • Verify that `formatPaceMinutes` converts decimal minutes (4.0, 4.5, 6.25, 12.0) to athletic `mm:ss` strings (`"4:00"`, `"4:30"`, `"6:15"`, `"12:00"`).
  • Verify that `extractMetricValue` returns `null` for speeds $< 0.55\text{ m/s}$ and coerces running pace within `[1.5, 20.0]`.
  3. *TelemetryMetricGraph Visual Contract & Y-Axis Labels Test (`TelemetryMetricGraphTest.kt`)*:
  • Verify `TelemetryMetricGraph.kt` renders Y-axis labels via `formatPaceMinutes` for `metricType == PACE` and eliminates raw decimal floats.
  4. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` across all modules to verify 100% pass rate with 0 regressions.

### TST-UI-174: **Aftermath Telemetry Graphs X-Axis Milestone Decimation, Collision Clearance & Boundary Labeling Verification** [Adaptive 2 km step progression active for 10-20km workouts; milestone labels omit redundant unit suffixes; boundary end label anchored with units; intermediate milestones cleanly decimated without collision; 100% test pass rate.]
- **Requirement(s)**: `REQ-UI-220`, `REQ-UI-206`
- **Component**: **Aftermath Telemetry Graphs X-Axis Milestone Decimation, Collision Clearance & Boundary Labeling Verification**
- **Jira**: ``ATT-1819``
- **Test Description**:
  1. *Adaptive 2 km Step Progression Unit Test (`ElevationProfileZoomMathTest.kt`)*:
  • Verify that `calculateAdaptiveDistanceStep` produces `2_000f` for visible distances in `(10_000, 20_000]` meters (e.g. 17.82 km, 12.0 km) in Metric mode.
  • Verify preserved steps at 8 km (1,000m), 25 km (5,000m), and Imperial equivalents.
  2. *Milestone Label Formatting Unit Tests (`TelemetryMetricGraphTest.kt`)*:
  • Verify that intermediate milestone labels format cleanly without repeating unit suffix (`"2"`, `"4"`, `"10"`, NOT `"2 km"`, `"4 km"`).
  • Verify Imperial decimal formatting.
  3. *Boundary Labels Parity Tests (`TelemetryMetricGraphTest.kt`)*:
  • Verify that `TelemetryMetricGraph` formats and anchors `endLabel` with units (`"17,82 km"` or `"11.07 mi"`) at the right boundary.
  • Verify that when zoomed ($Z > 1.01$), `startLabel` is rendered at the start boundary.
  4. *Milestone Decimation & Collision Prevention Tests (`TelemetryMetricGraphTest.kt`)*:
  • Verify that intermediate labels maintain minimum 12 dp clearance and are decimated when colliding.
  • Verify margin suppression within 40 dp of start padding and within `endLabelWidth + 16 dp` of end.
  5. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` across all modules to verify 100% pass rate with 0 regressions.

### TST-UI-175: **Bottom Sheet Peek Baselines, Design Token Centralization, Dynamic Self-Measurement & System Navigation Inset Verification** [Bottom sheet peek heights across workout, route, segment, favorite location, and live tracking sheets consume centralized BottomSheetDesign tokens and dynamic header self-measuring feedback, ensuring full visibility of header information above system navigation bars without clipping or empty container overshoots; 100% test pass rate.]
- **Requirement(s)**: `REQ-UI-221`
- **Component**: **Bottom Sheet Peek Baselines, Design Token Centralization, Dynamic Self-Measurement & System Navigation Inset Verification**
- **Jira**: ``ATT-1645``
- **Test Description**:
  1. *Design Token Constant Assertions (`BottomSheetDesignTest.kt`)*:
  • Verify that `BottomSheetDesign.PeekHeightWorkout == 140.dp`.
  • Verify that `BottomSheetDesign.PeekHeightRoute == 112.dp`.
  • Verify that `BottomSheetDesign.PeekHeightRouteWithDescription == 152.dp`.
  • Verify that `BottomSheetDesign.PeekHeightSegment == 192.dp`.
  • Verify that `BottomSheetDesign.PeekHeightKnownLocation == 108.dp`.
  • Verify that `BottomSheetDesign.PeekHeightLiveSegment == 126.dp`.
  2. *Dynamic Self-Measurement & Screen Forwarding Contract Tests (`BottomSheetVisualContractTest.kt`)*:
  • Verify `MapDetailLayout.kt` defines `onHeaderHeightMeasured: ((Dp) -> Unit)? = null` and binds `Modifier.onGloballyPositioned` around `MinimumDragHandle() + Surface(header)`.
  • Verify `SegmentOnMapScreen.kt` accepts and forwards `onHeaderHeightMeasured` to `MapDetailLayout`.
  • Verify `RouteOnMapScreen.kt` accepts and forwards `onHeaderHeightMeasured` to `MapDetailLayout`.
  • Verify `MapScreenWithTrack.kt` tracks `measuredSegmentHeaderHeight` and `measuredRouteHeaderHeight`, dynamically evaluating `sheetPeekHeight` with defensive fallback to `BottomSheetDesign` constants.
  • Verify that `SensorGridScreen.kt` references `BottomSheetDesign.PeekHeightLiveSegment`.
  • Verify that `WorkoutClusterHeatmapScreen.kt` and `PeriodMapScreen.kt` reference `BottomSheetDesign.PeekHeightWorkout`.
  3. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` across all modules verifying 100% pass rate with 0 regressions.

### TST-UI-176: **Advanced Settings Modular Collapsible Subsection Accordion Architecture & Live Subtitle Verification** [Advanced Settings cleanly structured into 5 collapsible Material 3 accordion sections with dynamic live-updating subtitle summaries, decoupled subsection composables, zero tonal elevation, 100% 9-language localization parity, and full test suite pass.]
- **Requirement(s)**: `REQ-UI-222`
- **Component**: **Advanced Settings Modular Collapsible Subsection Accordion Architecture & Live Subtitle Verification**
- **Jira**: ``ATT-1821``
- **Test Description**:
  1. *Live Active-Value Subtitle Formatter Unit Tests (`AdvancedTuningAccordionTest.kt`)*:
  • Verify Cockpit subtitle generation across font family and weight combinations.
  • Verify Battery Saver subtitle formatting percentages and slope thresholds.
  • Verify Sensors & GPS subtitle formatting meters, seconds, and speed.
  • Verify Aftermath subtitle reflecting Distance and Time domains.
  • Verify Workout Masks & Cards active count formatter (`X/8 Cards, Y/6 Fields`).
  • Verify factory reset restores default subtitle values for all 5 sections.
  2. *Accordion Architecture & Visual Contract Tests (`AdvancedTuningVisualContractTest.kt`)*:
  • Verify `AdvancedTuningDialog.kt` contains `TuningAccordionSection` invocations for all 5 semantic categories.
  • Verify zero remaining flat category headers with plain dividers.
  • Verify `TuningAccordionSection` applies `tonalElevation = 0.dp` and Material 3 surfaceVariant container styling.
  • Verify presence of decoupled subsection composables (`CockpitTypographySection`, `AmoledBatterySaverSection`, `SensorsGpsFilterSection`, `AftermathAnalysisSection`, `WorkoutMasksAndCardsSection`).
  3. *9-Language Localization Audit*:
  • Verify string resources `tuning_cat_sensors_gps`, `tuning_cat_workout_masks_cards`, and `tuning_summary_masks_cards_format` are defined identically across all 9 resource directories (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`).
  4. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` across all modules verifying 100% pass rate with 0 regressions.

### TST-UI-177: **MapDetailLayout Interactive Draggable Splitter & Viewport Resizing Verification** [Interactive draggable splitter allows continuous resizing of map and telemetry viewports in MapDetailLayout; double-tap resets to 50/50; boundaries safely clamped to >=120dp map and >=160dp graphs; routes/segments unaffected; 100% test pass rate.]
- **Requirement(s)**: `REQ-UI-223`
- **Component**: **MapDetailLayout Interactive Draggable Splitter & Viewport Resizing Verification**
- **Jira**: ``ATT-1890``
- **Test Description**:
  1. *SplitPaneMath Pure Unit Tests (`SplitPaneMathTest.kt`)*:
  • Verify calculation of available height subtracting divider touch height.
  • Verify calculation of minimum and maximum fraction bounds for various total heights.
  • Verify updateFraction clamping behavior across positive, negative, and extreme delta values.
  • Verify default split fraction equals 0.50f.
  2. *Visual & Structural Contract Tests (`SplitPaneDividerVisualContractTest.kt`)*:
  • Verify `SplitPaneDivider.kt` defines centered pill grip (32dp x 4dp) and 24dp touch target height.
  • Verify `SplitPaneDivider.kt` handles vertical dragging and double-tap gestures.
  • Verify `MapDetailLayout.kt` manages rememberSaveable splitFraction and conditionally renders `SplitPaneDivider`.
  • Verify Map Box applies `heightIn(min = 120.dp)` and lower container applies vertical scroll.
  • Verify routes and segments without scrollable content omit the splitter and retain full weight.
  3. *9-Language Localization Audit*:
  • Verify string resource `map_splitter_content_description` is defined across all 9 resource directories (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`).
  4. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` across all modules verifying 100% pass rate with 0 regressions.

### TST-UI-178: **KnownLocationCard Map Thumbnail Calibrated Zoom & Framing Verification** [KnownLocationCard map preview thumbnail camera zoom dynamically calibrated via KnownLocationZoomMath; geofence circle completely contained without clipping; >=1.4km neighborhood context visible; 100% test pass rate.]
- **Requirement(s)**: `REQ-UI-224`
- **Component**: **KnownLocationCard Map Thumbnail Calibrated Zoom & Framing Verification**
- **Jira**: ``ATT-1878``
- **Test Description**:
  1. *Pure Unit Tests (`KnownLocationZoomMathTest.kt`)*:
  • Verify `DEFAULT_THUMBNAIL_ZOOM == 12.0f`.
  • Verify `calculateThumbnailZoom` returns `12.5f` for typical radii: 50m, 100m, 200m.
  • Verify `calculateThumbnailZoom` returns proportionally wider zoom for 300m (~12.2f), 500m (~11.5f), and 1000m (~10.5f).
  • Verify clamping bounds: zoom never exceeds `12.5f` and never drops below `10.5f`.
  • Verify latitude variations and edge cases.
  2. *Visual & Structural Contract Tests (`KnownLocationCardLayoutTest.kt`)*:
  • Verify `KnownLocationsScreen.kt` references `KnownLocationZoomMath.calculateThumbnailZoom`.
  • Verify `KnownLocationsScreen.kt` eliminates hardcoded `14.5f` zoom literal.
  • Verify camera initialization and `LaunchedEffect` movement consume dynamic `targetZoom`.
  • Verify preservation of `liteMode(true)`, 80dp size, `RoundedCornerShape(12.dp)`, `onShowOnMap`, and `LocalInspectionMode.current`.
  3. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` across all modules verifying 100% pass rate with 0 regressions.

### TST-UI-179: **MapDetailLayout Persistent Sticky Global Zoom Toolbar & Decoupled ElevationProfile Verification** [Persistent sticky global zoom toolbar sits between map and telemetry graphs in MapDetailLayout, remaining stationary during scrolling; zoom controls decoupled from ElevationProfile, recovering vertical plotting area; 100% 9-language localization parity and zero regressions.]
- **Requirement(s)**: `REQ-UI-225`, `REQ-UI-215`, `REQ-UI-197`
- **Component**: **MapDetailLayout Persistent Sticky Global Zoom Toolbar & Decoupled ElevationProfile Verification**
- **Jira**: ``ATT-1876``
- **Test Description**:
  1. *GlobalTelemetryZoomToolbar Pure Composable & Logic Unit Tests (`GlobalTelemetryZoomToolbarTest.kt`)*:
  • Verify Zoom In button invokes onZoomChanged with 1.5x target zoom anchored at centroid.
  • Verify Zoom Out button invokes onZoomChanged with /1.5x target zoom anchored at centroid.
  • Verify Zoom In button is disabled when `zoomScale >= 10.0f - 0.01f`.
  • Verify Zoom Out button is disabled when `zoomScale <= 1.01f`.
  • Verify Reset pill is hidden at 1.0x and visible when `zoomScale > 1.01f`.
  • Verify Reset pill displays formatted scale (`"%.1fx"`) and tapping restores `zoomScale = 1.0f, startDist = 0.0`.
  • Verify Pan/Scrub mode toggle button toggles `isPanMode` state.
  • Verify toolbar height constant `TOOLBAR_HEIGHT == 36.dp`.
  2. *MapDetailLayout Visual & Structural Contract Tests (`MapDetailLayoutTest.kt`)*:
  • Verify `MapDetailLayout.kt` conditionally renders `GlobalTelemetryZoomToolbar` when `showZoomControls && activeScrubPath != null && activeScrubPath.isNotEmpty()`.
  • Verify `GlobalTelemetryZoomToolbar` is placed outside `Modifier.verticalScroll(...)` directly between `SplitPaneDivider` and `lowerColumn`.
  • Verify `availableHeightPx` accounts for `TOOLBAR_HEIGHT` when toolbar is active.
  • Verify `MapDetailLayout` manages hoisted `isPanMode` state and passes it to both `GlobalTelemetryZoomToolbar` and `ElevationProfile`.
  3. *ElevationProfile Decoupling & Padding Contract Tests (`ElevationProfileLayoutTest.kt`)*:
  • Verify `ElevationProfile.kt` does NOT contain embedded zoom buttons row (`Icons.Default.Add` / `Icons.Default.Remove`).
  • Verify `ElevationProfile` accepts `isPanMode` parameter.
  • Verify reduced canvas top padding when `showZoomControls == true` (`44.dp`), reclaiming vertical screen space.
  • Verify `ScrubbingTelemetryBadge` remains functional and cleanly positioned.
  4. *9-Language Localization Audit (`ZoomToolbarLocalizationTest.kt`)*:
  • Verify string resources `zoom_in`, `zoom_out`, `zoom_reset`, `zoom_pan_mode`, `zoom_scrub_mode` are defined identically across all 9 resource directories (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`).
  5. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` across all modules verifying 100% pass rate with 0 regressions.

### TST-UI-180: **MapDetailLayout Directional Gesture Disambiguation & Vertical Scroll Verification** [\Delta y]
- **Requirement(s)**: `REQ-UI-226`, `REQ-UI-206`, `REQ-UI-213`
- **Component**: **MapDetailLayout Directional Gesture Disambiguation & Vertical Scroll Verification**
- **Jira**: ``ATT-1872``
- **Test Description**:
  1. *ChartGestureDisambiguator Pure Logic Unit Tests (`ChartGestureDisambiguatorTest.kt`)*:
  • Verify dominant vertical swipes ($

### TST-UI-181: **Cockpit HUD Curated Font Palette Expansion & Defensive Resolution Verification** [Curated Cockpit typography palette expanded to 13 fonts; athletic condensed and HUD fonts selectable and render in live preview; 100% 9-language localization parity; safe offline fallbacks; 100% test pass rate.]
- **Requirement(s)**: `REQ-UI-227`, `REQ-UI-212`
- **Component**: **Cockpit HUD Curated Font Palette Expansion & Defensive Resolution Verification**
- **Jira**: ``ATT-1871``
- **Test Description**:
  1. *Pure Logic & Fallback Tests (`CockpitTypographyResolutionTest.kt`)*:
  • Verify all 13 `CockpitFontFamily` enum values resolve non-null `FontFamily`.
  • Verify fallback font family mappings for all 13 enum values return valid Compose fallbacks (`SansSerif`, `Monospace`, `Cursive`, `Default`).
  • Verify all 13 enum values return positive resource IDs for `getDisplayNameRes()`.
  • Verify `resolveConfig` produces complete config with resolved family and weight.
  2. *Preferences Serialization & Backward Compatibility Tests (`TuningPreferencesCockpitTypographyTest.kt`)*:
  • Verify all 13 `CockpitFontFamily` values serialize and deserialize cleanly.
  • Verify defensive deserialization falls back to `SYSTEM_DEFAULT` on invalid strings.
  3. *Font Expansion & Localization Parity Contract Tests (`CockpitFontExpansionTest.kt`)*:
  • Verify all 8 XML font descriptors exist in `res/font/` and declare valid authority (`com.google.android.gms.fonts`), package (`com.google.android.gms`), query, and certs.
  • Verify `preloaded_fonts.xml` includes all 8 new font resources.
  • Verify all 8 new string resources exist across all 9 localized resource directories (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`).
  • Verify `ExposedDropdownMenu` in `AdvancedTuningDialog.kt` applies `heightIn(max = 360.dp)`.
  4. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` across all modules verifying 100% pass rate with 0 regressions.

### TST-UI-182: **Aftermath Lap Split Visualizer Refinements & Alignment Verification** [Lap Split Visualizer shows 3-lap preview with expand toggle; custom and descriptive lap names display in lap badge; pace column features explicit units; fastest split row features rabbit icon left of right-aligned pace; 100% test pass rate.]
- **Requirement(s)**: `REQ-UI-228`, `REQ-UI-204`
- **Component**: **Aftermath Lap Split Visualizer Refinements & Alignment Verification**
- **Jira**: ``ATT-1869``
- **Test Description**:
  1. *Visual Composable & Contract Tests (`LapSplitVisualizerTest.kt`)*:
  • Verify `LapSplitVisualizer` defaults to displaying 3 laps when $> 3$ laps exist.
  • Verify toggle button expands to full count and collapses back to 3 laps.
  • Verify `split.displayName` is rendered in the lap badge.
  • Verify rabbit icon (`🐇`) is present on the fastest split row and absent on non-fastest rows.
  • Verify textual 'Best' badge is removed.
  • Verify hedgehog emoji is NOT present.
  • Verify pace values are right-aligned.
  2. *Formatting Engine & Callers Tests (`LapSplitCalculatorTest.kt`)*:
  • Verify `calculateSplitData` produces `formattedPaceOrSpeed` with units when formatters with units are provided.
  • Verify default formatters append `/km` and `km/h`.
  3. *Localization Parity Audit (`LapSplitRefinementsLocalizationTest.kt`)*:
  • Verify `show_all_laps` and `show_fewer_laps` exist across all 9 localized resource files (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`).
  • Verify `show_all_laps` contains positional argument `%1$d`.
  4. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` across all modules verifying 100% pass rate with 0 regressions.

### TST-UI-183: **Configurable Lap Section Display Mode Verification** [Lap presentation mode is fully configurable in Advanced Settings (Table, Visualizer, Both); conditional rendering in WorkoutLaps respects saved mode; lap editing works in all modes; 100% 9-language localization parity; 100% test pass rate.]
- **Requirement(s)**: `REQ-UI-229`, `REQ-UI-210`, `REQ-UI-204`
- **Component**: **Configurable Lap Section Display Mode Verification**
- **Jira**: ``ATT-1870``
- **Test Description**:
  1. *DataStore & Preferences Unit Tests (`LapDisplayModePreferencesTest.kt`)*:
  • Verify `LapDisplayMode` enum values (`TABLE_ONLY`, `VISUALIZER_ONLY`, `BOTH`).
  • Verify default value is `BOTH`.
  • Verify serialization and deserialization of all 3 values in `MyPreferenceManager`.
  • Verify defensive fallback to `BOTH` upon null, empty, or invalid stored string.
  • Verify factory reset restores `BOTH`.
  2. *Composable Conditional Rendering Tests (`WorkoutLapsDisplayModeTest.kt`)*:
  • Verify `WorkoutLaps` with `lapDisplayMode == BOTH` renders both `LapSplitVisualizer` and `LapTableHeader` + rows.
  • Verify `WorkoutLaps` with `lapDisplayMode == TABLE_ONLY` renders `LapTableHeader` + rows and does NOT render `LapSplitVisualizer`.
  • Verify `WorkoutLaps` with `lapDisplayMode == VISUALIZER_ONLY` renders `LapSplitVisualizer` and does NOT render `LapTableHeader` or rows.
  • Verify `onLapClick` is invoked when clicking on split item in `VISUALIZER_ONLY` mode.
  3. *Settings UI Contract & 9-Language Localization Tests (`LapDisplayModeSettingsTest.kt`)*:
  • Verify `WorkoutMasksAndCardsSection` displays 3-way `FilterChip` selector when `showLaps == true`.
  • Verify selector is omitted when `showLaps == false`.
  • Verify string resources (`settings_lap_display_mode_title`, `settings_lap_display_mode_table`, `settings_lap_display_mode_visualizer`, `settings_lap_display_mode_both`) exist across all 9 localized resource directories (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`).
  4. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` across all modules verifying 100% pass rate with 0 regressions.

### TST-UI-184: **Background Training Zone Bands & Right-Hand Zone Axis Verification** [HR and Power graphs render 5 subtle horizontal zone bands (10% alpha) and right-hand Z1-Z5 axis; Speed/Pace remain plain; scrubbing tooltip indicates active zone; 100% padding alignment with ElevationProfile; 100% test pass rate.]
- **Requirement(s)**: `REQ-UI-230`, `REQ-UI-206`
- **Component**: **Background Training Zone Bands & Right-Hand Zone Axis Verification**
- **Jira**: ``ATT-1839``
- **Test Description**:
  1. *Pure Zone Geometry & Math Engine Unit Tests (`TelemetryZoneMathTest.kt`)*:
  • Verify 5-zone range partition for HR and Power.
  • Verify clamping when data range spans only subset of zones (e.g. $[130, 160]$ with $z1Max=120$).
  • Verify suppression of zone label when band vertical height is $< 12\text{ dp}$.
  • Verify correct zone index mapping ($1..5$) for given sample values.
  2. *Composable Background Bands & Axis Rendering Tests (`TelemetryMetricGraphZoneTest.kt`)*:
  • Verify `TelemetryMetricGraph` with `HEART_RATE` renders 5 colored background bands with 10% alpha.
  • Verify `TelemetryMetricGraph` with `POWER` renders 5 colored background bands with 10% alpha.
  • Verify right-hand margin displays `Z1`–`Z5` labels at center of visible bands.
  • Verify horizontal dashed boundary lines at `z1Max`..`z4Max`.
  • Verify `SPEED` and `PACE` graphs do NOT render zone bands or right-hand zone axes.
  3. *Scrubbing Readout & Invariant Contract Tests*:
  • Verify scrubbing cursor tooltip displays zone tag (`• Z4`).
  • Verify horizontal padding invariant: `startPaddingPx = 50.dp` and `endPaddingPx = 25.dp` match `ElevationProfile.kt` exactly.
  4. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` across all modules verifying 100% pass rate with 0 regressions.

### TST-UI-185: **Fine-Grained Telemetry Frequency Histogram Verification** [Fine-grained frequency histograms accurately computed for HR and Power; individual bars shaded with matching zone colors; toggle between 5-zone and histogram operates cleanly; interactive readout updates on touch; 100% 9-language parity; 100% test pass rate.]
- **Requirement(s)**: `REQ-UI-231`, `REQ-UI-202`, `REQ-UI-203`
- **Component**: **Fine-Grained Telemetry Frequency Histogram Verification**
- **Jira**: ``ATT-1845``
- **Test Description**:
  1. *Pure Histogram Binning Engine Unit Tests (`TelemetryHistogramCalculatorTest.kt`)*:
  • Verify continuous bin generation for HR (2 bpm width) and Power (10 W width).
  • Verify boundary clamping to active sample range $[v_{min}, v_{max}]$.
  • Verify duration accumulation ($\Delta t \le 5\text{s}$) matching total active time.
  • Verify zone color assignment per bin matching athlete thresholds.
  • Verify degenerate input handling (empty samples, zero duration, single sample).
  2. *Composable Card Toggle & Histogram Rendering Tests (`TelemetryHistogramCardTest.kt`)*:
  • Verify `HeartRateZoneDistributionCard` and `PowerZoneDistributionCard` render view mode toggle.
  • Verify switching toggle switches between `ZoneDistributionColumnChart` and `TelemetryHistogramChart`.
  • Verify touching a histogram bar highlights the bar and updates readout text.
  3. *9-Language Localization Parity Tests (`TelemetryHistogramLocalizationTest.kt`)*:
  • Verify `zone_mode_5_zones` and `zone_mode_histogram` exist across all 9 localized resource directories (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`).
  4. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` across all modules verifying 100% pass rate with 0 regressions.

### TST-UI-186: **Aftermath/Graphs: TelemetryMetricGraph Pan Mode Gesture & Lockstep Synchronization Verification** [Horizontal dragging across Speed/Pace, Heart Rate, and Power graphs in Pan Mode pans visible window via ElevationProfileZoomMath.applyPan in lockstep across all charts; Scrub Mode preserved; vertical scrolling untouched; 100% test pass rate.]
- **Requirement(s)**: `REQ-UI-232`
- **Component**: **Aftermath/Graphs: TelemetryMetricGraph Pan Mode Gesture & Lockstep Synchronization Verification**
- **Jira**: ``ATT-1956``
- **Test Description**:
  1. *Structural and Gesture Contract Tests (`TelemetryMetricGraphGestureTest.kt`)*:
  • Verify `TelemetryMetricGraph` declares `isPanMode: Boolean = false` and `onZoomChanged: ((Float, Double) -> Unit)? = null`.
  • Verify `pointerInput` includes `isPanMode` in parameter keys.
  • Verify `pointerInput` tracks `prevX` and calculates `dragDeltaX`.
  • Verify `isPanMode && totalSpan > 10.0 && onZoomChanged != null` invokes `ElevationProfileZoomMath.applyPan`.
  • Verify horizontal dragging in Pan Mode invokes `onZoomChanged(zoomScale, panStart)`.
  • Verify drag release in Pan Mode does not clear scrub selection.
  2. *MapDetailLayout Lockstep Wiring Contract Tests (`MapDetailLayoutTest.kt`)*:
  • Verify `MapDetailLayout.kt` forwards `isPanMode = isPanMode` to `ElevationProfile` and all 3 `TelemetryMetricGraph` instances (count >= 4).
  • Verify `MapDetailLayout.kt` forwards `onZoomChanged` to `ElevationProfile` and all 3 `TelemetryMetricGraph` instances (count >= 4).
  3. *Pure Logic Unit Tests (`ElevationProfileZoomMathTest.kt`)*:
  • Verify `applyPan` clamps startDist within `[0.0, totalDist - visibleDist]`.
  • Verify panning in both positive and negative directions across various zoom scales.
  4. *Clean-Room Full Suite Regression Execution*:
  • Run `./gradlew testDebugUnitTest` across all modules verifying 100% pass rate.

### TST-UI-187: **Advanced Settings Initial Collapsed Accordion State Verification** [All 5 accordion subsections in AdvancedTuningDialog are initially collapsed by default on dialog launch, displaying category icons, titles, and live subtitles without vertical scrolling; manual expansion and rotation retention fully operational; 100% test pass rate.]
- **Requirement(s)**: `REQ-UI-222`
- **Component**: **Advanced Settings Initial Collapsed Accordion State Verification**
- **Jira**: ``ATT-1957``
- **Test Description**:
  1. *Visual & Structural Contract Tests (`AdvancedTuningVisualContractTest.kt`)*:
  • Verify `AdvancedTuningDialog.kt` initializes `expandedSections` with `emptySet()` via `rememberSaveable`.
  • Verify `AdvancedTuningDialog.kt` does NOT initialize `expandedSections` with `setOf(TuningSection.COCKPIT_TYPOGRAPHY.name)`.
  • Verify all 5 `TuningSection` enum values are referenced and managed.
  2. *Interactive Expand/Collapse & Configuration State Retention*:
  • Verify tapping any collapsed section header expands it via `AnimatedVisibility` while leaving other sections untouched.
  • Verify multi-section selection survives configuration changes (rotation).
  3. *Clean-Room Full Suite Regression Execution*:
  • Run `./gradlew testDebugUnitTest` across all modules verifying 100% pass rate.

### TST-UI-188: **Lap Display Mode Visualizer Default & Advanced Settings Dialog State Synchronization Verification** [LapDisplayMode defaults to VISUALIZER_ONLY out-of-the-box for fresh installs and resets; AdvancedTuningDialog robustly initializes and displays persisted DataStore lapDisplayMode upon reopen without state desynchronization; 100% test pass rate.]
- **Requirement(s)**: `REQ-UI-229`
- **Component**: **Lap Display Mode Visualizer Default & Advanced Settings Dialog State Synchronization Verification**
- **Jira**: ``ATT-1958``
- **Test Description**:
  1. *DataStore Defaults & Fallback Unit Tests (`LapDisplayModePreferencesTest.kt`)*:
  • Assert `WorkoutCardSectionPreferences()` initializes `lapDisplayMode` to `LapDisplayMode.VISUALIZER_ONLY`.
  • Assert DataStore flow deserialization falls back to `LapDisplayMode.VISUALIZER_ONLY` upon null or corrupt values.
  • Assert factory reset restores `LapDisplayMode.VISUALIZER_ONLY`.
  2. *Advanced Tuning Settings Unit Tests (`LapDisplayModeSettingsTest.kt`)*:
  • Assert default preferences and reset preferences produce `LapDisplayMode.VISUALIZER_ONLY`.
  3. *Dialog State Synchronization & Flow Gating Contract Tests (`AdvancedTuningVisualContractTest.kt`)*:
  • Verify `AdvancedTuningDialog.kt` collects `workoutCardPreferencesFlow` and `editWorkoutFieldPreferencesFlow` with `initial = null`.
  • Verify `workoutCardPrefs` is initialized on non-null emission, preventing the initial fallback dummy object from race-condition locking out real DataStore values.
  4. *Clean-Room Full Suite Regression Execution*:
  • Run `./gradlew testDebugUnitTest` across all modules verifying 100% pass rate.

### TST-UI-189: **Zone Card Header Row Layout, Bottom Toggle Placement & Streamlined Readout Verification** [Header row in Heart Rate and Power zone cards never wraps duration across all 9 languages; mode toggle renders centered below chart; default histogram readout omits active bins metric and preserves range and bin delta; 100% test pass rate.]
- **Requirement(s)**: `REQ-UI-231`
- **Component**: **Zone Card Header Row Layout, Bottom Toggle Placement & Streamlined Readout Verification**
- **Jira**: ``ATT-1959``
- **Test Description**:
  1. *Card Header and Toggle Placement Contract Tests (`TelemetryHistogramCardTest.kt`)*:
  • Verify `HeartRateZoneDistributionCard.kt` header Row contains icon, title, flexible spacer, and total active duration without mode toggle collision.
  • Verify `HeartRateZoneDistributionCard.kt` renders mode toggle below the chart, centered horizontally.
  • Verify `PowerZoneDistributionCard.kt` header Row contains icon, title, flexible spacer, and total active duration without mode toggle collision.
  • Verify `PowerZoneDistributionCard.kt` renders mode toggle below the chart, centered horizontally.
  2. *Streamlined Readout Line Contract Tests (`TelemetryHistogramCardTest.kt`)*:
  • Verify `TelemetryHistogramChart.kt` default resting readout does NOT contain `"active bins"` text.
  • Verify default resting readout displays `${histogram.dataMin}–${histogram.dataMax} $unit` and `Δ ${histogram.binWidth} $unit`.
  • Verify scrubbing active bin displays range, duration with percentage, and zone index.
  3. *Localization Parity Audit (`TelemetryHistogramLocalizationTest.kt`)*:
  • Verify `zone_mode_5_zones` and `zone_mode_histogram` remain intact across all 9 locales.
  4. *Clean-Room Full Suite Regression Execution*:
  • Run `./gradlew testDebugUnitTest` across all modules verifying 100% pass rate.

### TST-UI-190: **Pan Mode Gesture Sensitivity & Cross-Domain Continuous Panning Verification** [PointerInput keys in TelemetryMetricGraph and ElevationProfile exclude running startDist and zoomScale; dynamic parameters captured via rememberUpdatedState; viewport offset managed as normalized fraction eliminating cross-domain collisions; lockstep panning across Elevation and Telemetry verified; 100% test pass rate.]
- **Requirement(s)**: `REQ-UI-232`
- **Component**: **Pan Mode Gesture Sensitivity & Cross-Domain Continuous Panning Verification**
- **Jira**: ``ATT-1987``
- **Test Description**:
  1. *PointerInput Keys Decoupling Contract Tests (`TelemetryMetricGraphGestureTest.kt`)*:
  • Verify `TelemetryMetricGraph.kt` does NOT declare `startDist` or `zoomScale` in `pointerInput` keys.
  • Verify `TelemetryMetricGraph.kt` observes only `(totalSpan, isTimeDomain, isPanMode)`.
  • Verify `TelemetryMetricGraph.kt` references dynamic parameters via `rememberUpdatedState`.
  • Verify `ElevationProfile.kt` does NOT declare `currentStartDist` or `currentZoomScale` in `pointerInput` keys.
  • Verify `ElevationProfile.kt` observes only `(totalSpan, isTimeDomain, isPanMode)`.
  2. *Local Running Distance Accumulation Contract Tests (`TelemetryMetricGraphGestureTest.kt`)*:
  • Verify continuous drag loop updates local start distance across sequential drag events without resetting to initial down position.
  3. *Cross-Domain Fraction Mapping & Lockstep Unit Tests (`MapDetailLayoutCrossDomainPanTest.kt`)*:
  • Verify fraction-to-domain calculation and clamping across Distance ($S = 25,000\text{m}$) and Time ($S = 3,600\text{s}$) domains without clamping overflow or division-by-zero crashes.
  • Verify simulated drag on ElevationProfile updates viewport start fraction and updates Telemetry offset in lockstep.
  • Verify simulated drag on TelemetryMetricGraph updates viewport start fraction and updates Elevation offset in lockstep.
  4. *Elevation Profile Gesture Continuity Contract Tests (`ElevationProfileGestureContractTest.kt`)*:
  • Verify `ElevationProfile.kt` uses `rememberUpdatedState(onZoomChanged)` directly without function object allocation.
  • Verify `prevCentroid` reset upon drag engagement and absence of premature `isVerticalScrolling` gesture loop breaks.
  5. *Clean-Room Full Suite Regression Execution*:
  • Run `./gradlew testDebugUnitTest` across all modules verifying 100% pass rate.

### TST-UI-191: **Lap Display Mode Binary Choice (Table vs. Visualizer) & Legacy Migration Verification** [LapDisplayMode is streamlined to binary choice (Table vs. Visualizer); legacy 'BOTH' deserializes safely to Visualizer Only; Advanced Settings renders SingleChoiceSegmentedButtonRow with two SegmentedButtons; WorkoutLaps defaults to Visualizer Only; 100% test pass rate.]
- **Requirement(s)**: `REQ-UI-229`
- **Component**: **Lap Display Mode Binary Choice (Table vs. Visualizer) & Legacy Migration Verification**
- **Jira**: ``ATT-1988``
- **Test Description**:
  1. *Domain Model & DataStore Deserialization Tests (`LapDisplayModePreferencesTest.kt`)*:
  • Assert `LapDisplayMode` enum has exactly 2 values (`TABLE_ONLY`, `VISUALIZER_ONLY`).
  • Assert default is `VISUALIZER_ONLY`.
  • Assert legacy `"BOTH"` string deserializes defensively to `VISUALIZER_ONLY`.
  • Assert unknown/empty strings deserialize to `VISUALIZER_ONLY`.
  2. *Settings Dialog UI Contract Tests (`LapDisplayModeSettingsTest.kt`)*:
  • Verify `AdvancedTuningDialog.kt` displays Material 3 `SingleChoiceSegmentedButtonRow` containing exactly 2 `SegmentedButton` items (`TABLE_ONLY` and `VISUALIZER_ONLY`) under Rundendarstellung.
  • Verify `FilterChip` for lap display mode is eliminated.
  • Verify localization parity across all 9 locales for remaining tokens.
  3. *Composable Conditional Rendering Tests (`WorkoutLapsDisplayModeTest.kt`)*:
  • Verify `WorkoutLaps` defaults to `VISUALIZER_ONLY`.
  • Verify `VISUALIZER_ONLY` shows visualizer and hides table.
  • Verify `TABLE_ONLY` shows table and hides visualizer.
  4. *Clean-Room Full Suite Regression Execution*:
  • Run `./gradlew testDebugUnitTest` verifying 100% pass rate.

### TST-UI-192: **Independent X-Axis Domain Settings (Elevation vs. Telemetry) & Cross-Domain Scrubbing Verification** [Elevation profile defaults to Distance, telemetry graphs default to Time; both can be independently configured in Expert Settings; cross-domain scrubbing and cursor rendering operate synchronously; 100% test pass rate.]
- **Requirement(s)**: `REQ-UI-233`
- **Component**: **Independent X-Axis Domain Settings (Elevation vs. Telemetry) & Cross-Domain Scrubbing Verification**
- **Jira**: ``ATT-1986``
- **Test Description**:
  1. *DataStore Preferences Decoupling & Fallback Tests (`ProfileXAxisDomainTest.kt`)*:
  • Verify `TuningConfig` initializes `elevationXAxisDomain = DISTANCE` and `telemetryXAxisDomain = TIME` by default.
  • Verify independent serialization and deserialization of `KEY_ELEVATION_X_AXIS_DOMAIN` and `KEY_TELEMETRY_X_AXIS_DOMAIN`.
  • Verify legacy migration: when only `KEY_PROFILE_X_AXIS_DOMAIN` is present, `elevationXAxisDomain` adopts it and `telemetryXAxisDomain` defaults to `TIME`.
  • Verify factory reset restores `elevationXAxisDomain = DISTANCE` and `telemetryXAxisDomain = TIME`.
  2. *Expert Settings UI & Subtitle Tests (`AdvancedTuningVisualContractTest.kt`, `AftermathTuningLocalizationTest.kt`)*:
  • Verify `AdvancedTuningDialog.kt` renders two independent FilterChip selector rows in the Aftermath section.
  • Verify `TuningSubtitleFormatter.formatAftermathSubtitle` formats both domains.
  • Verify 100% 9-language localization parity for new strings (`tuning_telemetry_x_axis_title`, `tuning_telemetry_x_axis_desc`).
  3. *Scrubbing & Range Check Validation (`TelemetryMetricGraphRangeTest.kt`)*:
  • Verify `TelemetryMetricGraph` validates cursor range against temporal coordinates (`cursorDistSpan in 0.0..totalSpan`), eliminating the magnitude mismatch bug.
  4. *Clean-Room Full Suite Regression Execution*:
  • Run `./gradlew testDebugUnitTest` verifying 100% pass rate.

### TST-UI-193: **UI Design System & Mode Selection Heuristics Verification** [Centralized UI design guidelines are documented and verified; binary mode switchers adhere to SingleChoiceSegmentedButtonRow heuristics; requirement traceability is 100% established.]
- **Requirement(s)**: `REQ-UI-234`
- **Component**: **UI Design System & Mode Selection Heuristics Verification**
- **Jira**: ``ATT-1989``
- **Test Description**:
  1. *Design Guidelines Document Audit*:
  • Verify existence and completeness of `docs/design_guidelines.md` covering binary mode selection, categorical filters, coordinate space integrity, and touch gesture key stability.
  2. *Binary Mode Component Selection Contract*:
  • Verify that binary display mode toggles (e.g. 5 Zones vs. Histogram in `HeartRateZoneDistributionCard.kt`, Table vs. Visualizer in `AdvancedTuningDialog.kt`) use `SingleChoiceSegmentedButtonRow` with `SegmentedButton` rather than `FilterChip`.
  3. *Requirement Traceability*:
  • Verify cross-reference link between `REQ-UI-234` in `docs/requirements.md` and `docs/design_guidelines.md`.

### TST-UI-194: **Aftermath/Telemetry: Trackless Continuous Telemetry Graph Ingestion, Time-Domain Scrubbing, and Map Collapse Verification** [Continuous Heart Rate curve renders along Time domain for trackless workouts; Google Map is collapsed; touch scrubbing smoothly traverses duration with instantaneous metric/zone readouts; full-screen content scrolls without clipping; 100% test pass rate.]
- **Requirement(s)**: `REQ-UI-235`
- **Component**: **Aftermath/Telemetry: Trackless Continuous Telemetry Graph Ingestion, Time-Domain Scrubbing, and Map Collapse Verification**
- **Jira**: ``ATT-2006``
- **Test Description**:
  1. *WorkoutRepository Telemetry Extraction Unit Tests (`WorkoutRepositoryTelemetryTest.kt`)*:
  • Verify extraction of valid sensor samples when latitude and longitude are null.
  • Verify `timeSec`, `hr`, and `power` are extracted while `distance` is 0.0 and `latLng` is `(0.0, 0.0)`.
  • Verify uniform stride decimation when sample count exceeds 800, preserving first and last points.
  2. *ViewModel Telemetry Ingestion Tests (`TrackOnMapViewModelTelemetryTest.kt`)*:
  • Verify `AftermathMapUIState` exposes `telemetryPath: List<PathPoint>`.
  • Verify `telemetryPath` is populated when `fullTracks` is empty.
  3. *Visual & Structural Contract Tests (`TracklessAftermathVisualContractTest.kt`)*:
  • Verify `TrackOnMapScreen.kt` evaluates `hasGpsTrack` and sets `showMap = false` when no GPS points exist.
  • Verify `MapDetailLayout.kt` suppresses `ATrainingTrackerMap` and applies `fillMaxSize().verticalScroll(...)` when `showMap == false`.
  • Verify `hasTelemetryGraphs` evaluates to true when `activeScrubPath` contains HR data without GPS.
  • Verify scrubbing readout in header row renders instantaneous metric and zone.
  4. *Time-Domain Scrubbing Tests (`TelemetryMetricGraphTracklessScrubbingTest.kt`)*:
  • Verify dragging dispatches `nearest.timeSec` directly without falling back to 0.0.
  • Verify cursor and marker dot evaluate directly against time span.
  5. *Clean-Room Full Suite Regression Execution*:
  • Run `./gradlew testDebugUnitTest` verifying 100% pass rate.

### TST-UI-195: **WorkoutSummaries: Support Marking Workouts as "Race" (Wettkampf) Verification Across DB, Header, Edit UI, and Filters** [Workouts can be marked as Race; DB upgrades to version 23 idempotently; Material 3 header badge renders with vector trophy icon and zero raw emojis; edit dialog and filter bottom sheet operate seamlessly; Strava syncs native race workout type; 100% test pass rate.]
- **Requirement(s)**: `REQ-UI-236`
- **Component**: **WorkoutSummaries: Support Marking Workouts as "Race" (Wettkampf) Verification Across DB, Header, Edit UI, and Filters**
- **Jira**: ``ATT-2005``
- **Test Description**:
  1. *Database Migration & Persistence Unit Tests (`WorkoutSummariesDatabaseRaceMigrationTest.kt`)*:
  • Verify SQLite schema migration from `DB_VERSION = 22` to `23` adds column `race` (int, default 0) idempotently.
  • Verify existing records default to `race = 0` without data loss.
  • Verify `updateWorkoutData` persists `race = 1` and `race = 0`.
  2. *Domain Model & Mapper Tests (`WorkoutDataRaceMappingTest.kt`)*:
  • Verify `WorkoutDataMapper` extracts `race` column from cursor correctly.
  • Verify `WorkoutData.headerData` forwards `race` to `WorkoutHeaderData`.
  • Verify `WorkoutRepository.saveWorkout` preserves `race` in in-memory state update.
  3. *Visual Contract Tests (`WorkoutHeaderRaceBadgeVisualContractTest.kt`)*:
  • Verify `WorkoutHeader.kt` Row A renders Material 3 `Surface` badge with `tertiaryContainer` tone and vector icon `Icons.Default.EmojiEvents` when `data.race == true`.
  • Verify strictly zero raw unicode emojis.
  4. *Filter Criteria Tests (`WorkoutFilterCriteriaRaceTest.kt`)*:
  • Verify `WorkoutFilterCriteria.matches` filters by `isRace = true`, `isRace = false`, and `null`.
  • Verify JSON serialization and deserialization of `isRace`.
  5. *Strava Uploader Integration Tests (`StravaUploaderRaceWorkoutTypeTest.kt`)*:
  • Verify `workout_type` is set to `"11"` for cycling race and `"1"` for running race.
  6. *Localization Parity Audit (`TranslationParityTest.kt`)*:
  • Verify 100% parity across all 9 application locales for new string keys.
  7. *Clean-Room Regression Suite*:
  • Run `./gradlew testDebugUnitTest` verifying 100% pass rate.

### TST-UI-196: **Segment, Route, and Live Segment Popup Navigation Bar Inset & Upward Travel Calibration Verification** [Segment, route, and live segment popups incorporate navigation bar padding so that the elevation profile and all controls remain 100% visible and interactive above the Android 3-button navigation bar; 100% test pass rate.]
- **Requirement(s)**: `REQ-UI-237`
- **Component**: **Segment, Route, and Live Segment Popup Navigation Bar Inset & Upward Travel Calibration Verification**
- **Jira**: ``ATT-2008``
- **Test Description**:
  1. *MapDetailLayout Structural Contract Tests (`MapDetailLayoutNavigationBarsPaddingTest.kt`)*:
  • Verify `MapDetailLayout.kt` applies `Modifier.navigationBarsPadding()` to the elevation profile `Surface` when `analyticsContent == null`.
  • Verify `analyticsContent` retains its `navigationBarsPadding()`.
  • Verify fallback `Spacer(modifier = Modifier.navigationBarsPadding())` when both `showElevationProfile` is false and `analyticsContent == null` in a bottom sheet context (`!useStatusBarsPadding`).
  2. *Bottom Sheet Inset & Contour Visual Contract Tests (`BottomSheetVisualContractTest.kt`)*:
  • Verify that `MapScreenWithTrack.kt` sizes sheet to `maxSheetHeight` and allows full upward travel.
  • Verify that `SensorGridScreen.kt` wraps `LiveSegmentSheet` cleanly, and sheet expansion naturally increases with the navigation bar padding.
  3. *Clean-Room Full Suite Regression Execution*:
  • Run `./gradlew testDebugUnitTest` verifying 100% pass rate.

### TST-UI-197: **Lieblingsorte/Map: Favorite Location Bottom Sheet Non-Expandable Contract & Drag Handle Removal Verification** [Favorite location bottom sheet is locked to peek height; swipe gestures are disabled when a location is active; upward expansion is rejected via confirmValueChange; misleading drag handle is removed; 100% test pass rate.]
- **Requirement(s)**: `REQ-UI-238`
- **Component**: **Lieblingsorte/Map: Favorite Location Bottom Sheet Non-Expandable Contract & Drag Handle Removal Verification**
- **Jira**: ``ATT-2042``
- **Test Description**:
  1. *Structural & Visual Contract Tests (`FavoriteLocationSheetContractTest.kt`)*:
  • Verify `MapScreenWithTrack.kt` sets `sheetSwipeEnabled = selectedLocationId == null` on `BottomSheetScaffold`.
  • Verify `rememberStandardBottomSheetState` supplies `confirmValueChange` rejecting `SheetValue.Expanded` when `selectedLocationId != null`.
  • Verify `KnownLocationOnMapSheet` composable does not call `MinimumDragHandle()`.
  • Verify `KnownLocationOnMapSheet` root column applies `padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 12.dp)`.
  • Verify `MapScreenWithTrack.kt` consumes `BottomSheetDesign.PeekHeightKnownLocation` for peek height.
  2. *Clean-Room Full Suite Regression Execution*:
  • Run `./gradlew testDebugUnitTest` verifying 100% pass rate.

### TST-UI-198: **Unconditional Edit Workout Form Presentation & Removal of Edit Dialog Field Toggles Verification** [All edit workout metadata fields are rendered unconditionally; field toggles and state are removed from Advanced Settings; Section 5 subtitle formats active cards correctly; 100% test pass rate.]
- **Requirement(s)**: `REQ-UI-239`
- **Component**: **Unconditional Edit Workout Form Presentation & Removal of Edit Dialog Field Toggles Verification**
- **Jira**: ``ATT-2029``
- **Test Description**:
  1. *Structural Contract Verification (`AdvancedTuningAftermathContractTest.kt`)*:
  • Verify `AdvancedTuningDialog.kt` contains `WorkoutCardSectionPreferences` and all 8 card section toggles.
  • Verify `AdvancedTuningDialog.kt` does NOT reference `EditWorkoutFieldPreferences`.
  • Verify `AdvancedTuningDialog.kt` does NOT reference `settings_edit_workout_title` or any of the 7 edit field toggles.
  • Verify `AdvancedTuningDialog.kt` does NOT invoke `setEditWorkoutFieldPreferences`.
  2. *Unconditional Form Layout Contract Test (`EditWorkoutFieldsLayoutTest.kt`)*:
  • Verify all metadata fields in `EditWorkoutScreen.kt` are permanently visible without conditional toggle dependencies.
  3. *Accordion Subtitle Formatting Test (`AdvancedTuningAccordionTest.kt`)*:
  • Verify `TuningSubtitleFormatter.formatWorkoutMasksSubtitle` formats strings correctly with single argument (`%1$d/8 Cards`).
  4. *9-Language Localization Audit (`TranslationParityTest.kt`)*:
  • Verify `tuning_summary_masks_cards_format` is defined across all 9 localized `strings.xml` files with matching format specifiers (`%1$d/8`).
  5. *Clean-Room Full Suite Regression Execution*:
  • Run `./gradlew testDebugUnitTest` verifying 100% pass rate.

### TST-UI-199: **Matrix Table Presentation for Configurable Aftermath Sections (Workout Summary List vs. Workout Details) Verification** [Independent aftermath section customization operates via 2-column matrix table in Advanced Settings; list view preserves 60/120 FPS scrolling performance; details view renders full analytics by default; 100% test pass rate.]
- **Requirement(s)**: `REQ-UI-240`
- **Component**: **Matrix Table Presentation for Configurable Aftermath Sections (Workout Summary List vs. Workout Details) Verification**
- **Jira**: ``ATT-2030``
- **Test Description**:
  1. *Preferences Model & DataStore Fallback Tests (`WorkoutDetailPreferencesTest.kt`)*:
  • Verify `WorkoutDetailPreferences()` defaults all 8 flags to `true`.
  • Verify `WorkoutCardSectionPreferences()` defaults `showElevationProfile` to `false`.
  • Verify fallback rules when `workout_detail_show_*` keys are missing from DataStore.
  • Verify DataStore serialization and deserialization.
  2. *Matrix Table UI Structural Contract Tests (`AdvancedTuningMatrixContractTest.kt`)*:
  • Verify `AdvancedTuningDialog.kt` Section 5 renders 2-column matrix table with headers `tuning_matrix_col_list` and `tuning_matrix_col_details`.
  • Verify dual state management for `workoutCardPrefs` and `workoutDetailPrefs`.
  • Verify interactive elements satisfy 48x48dp touch bounds.
  3. *Accordion Subtitle Formatting Tests (`AdvancedTuningAccordionTest.kt`)*:
  • Verify `formatWorkoutMatrixSubtitle` formats dual counts (`%1$d/8 Liste, %2$d/8 Details`).
  4. *Detail Screen Preferences Tests (`TrackOnMapScreenPreferencesTest.kt`)*:
  • Verify `TrackOnMapScreen` / `MapDetailLayout` respects `WorkoutDetailPreferences` for map, elevation, charts, zones, laps, extrema, and description.
  5. *9-Language Localization Audit (`TranslationParityTest.kt`)*:
  • Verify 100% parity across all 9 localized `strings.xml` files for matrix headers and subtitle formats.
  6. *Clean-Room Full Suite Regression Execution*:
  • Run `./gradlew testDebugUnitTest` verifying 100% pass rate.

### TST-UI-200: **Persistent Floating Scrubbing Telemetry Badge Pinned Above Scrollable Graphs in MapDetailLayout Verification** [Scrubbing telemetry badge is hoisted into MapDetailLayout and pinned above scrollable graphs; remains visible across scroll positions and when elevation profile is disabled; 100% test pass rate.]
- **Requirement(s)**: `REQ-UI-241`
- **Component**: **Persistent Floating Scrubbing Telemetry Badge Pinned Above Scrollable Graphs in MapDetailLayout Verification**
- **Jira**: ``ATT-2016``
- **Test Description**:
  1. *Structural & Parameter Contract Tests (`MapDetailLayoutScrubbingBadgeContractTest.kt`)*:
  • Verify `ElevationProfile.kt` declares parameter `showScrubbingBadge: Boolean = true` across both overloads.
  • Verify `ElevationProfile.kt` gates `ScrubbingTelemetryBadge` on `showScrubbingBadge`.
  • Verify `MapDetailLayout.kt` passes `showScrubbingBadge = false` to `ElevationProfile`.
  • Verify `MapDetailLayout.kt` encapsulates `lowerColumn` inside a `Box` and hosts `ScrubbingTelemetryBadge` pinned at `Alignment.TopCenter`.
  • Verify `MapDetailLayout.kt` resolves `activeScrubPoint` and `activeScrubAltitude` with trackless time-domain fallback.
  2. *Existing Layout & Invariant Tests*:
  • Verify `ElevationProfileLayoutTest.kt` passes 100% without modification.
  • Verify `TelemetryMetricGraphZoneTest.kt` passes 100%.
  • Verify `MapDetailLayoutTest.kt` passes 100%.
  3. *9-Language Localization Audit (`TranslationParityTest.kt`)*:
  • Verify 100% parity across all 9 localized `strings.xml` files for graph titles, metric labels, and zoom controls.
  4. *Clean-Room Full Suite Regression Execution*:
  • Run `./gradlew testDebugUnitTest` verifying 100% pass rate.

### TST-UI-201: **Aftermath/Scrubbing: Render Training Zone Badge Suffix in Active Zone Color in Scrubbing Telemetry Badge Verification** [Training zone suffix in ScrubbingTelemetryBadge is rendered directly in active zone color while base value remains neutral onSurface; graceful fallback without dangling delimiters; 100% test pass rate.]
- **Requirement(s)**: `REQ-UI-242`
- **Component**: **Aftermath/Scrubbing: Render Training Zone Badge Suffix in Active Zone Color in Scrubbing Telemetry Badge Verification**
- **Jira**: ``ATT-2015``
- **Test Description**:
  1. *Structural & Zone Styling Contract Tests (`ScrubbingBadgeZoneColorContractTest.kt`)*:
  • Verify `ElevationProfile.kt` utilizes `buildAnnotatedString` for HR and Power telemetry badges.
  • Verify base metric value (e.g. `${point.hr} bpm`, `${point.power} W`) is styled with `MaterialTheme.colorScheme.onSurface`.
  • Verify active zone suffix (` • Z$hrZone`, ` • Z$powerZone`) is styled with `TelemetryZoneMath.ZONE_COLORS[zone - 1]` and `FontWeight.Bold`.
  • Verify fallback branch renders plain base metric value in `MaterialTheme.colorScheme.onSurface` without delimiter or zone suffix when zone is null.
  2. *Existing Invariant Tests*:
  • Verify `ElevationProfileLayoutTest.kt` passes 100%.
  • Verify `TelemetryMetricGraphZoneTest.kt` passes 100%.
  • Verify `MapDetailLayoutScrubbingBadgeContractTest.kt` passes 100%.
  3. *9-Language Localization Audit (`TranslationParityTest.kt`)*:
  • Verify 100% parity across all 9 localized `strings.xml` files.
  4. *Clean-Room Full Suite Regression Execution*:
  • Run `./gradlew testDebugUnitTest` verifying 100% pass rate.

### TST-UI-202: **Aftermath/Graphs: Configurable Minimum Pace Ceiling in Expert Settings with Athletic Default 3:00 min/km Verification** [Configurable minimum pace ceiling operates in Expert Settings with default 3:00 min/km; eliminates GPS noise spikes and hardcoded 1:30 min/km ceiling from pace telemetry graphs; 100% test pass rate.]
- **Requirement(s)**: `REQ-UI-243`
- **Component**: **Aftermath/Graphs: Configurable Minimum Pace Ceiling in Expert Settings with Athletic Default 3:00 min/km Verification**
- **Jira**: ``ATT-2014``
- **Test Description**:
  1. *Preferences Model & DataStore Tests (`TuningPreferencesDataStorePaceCeilingTest.kt`)*:
  • Verify `TuningConfig()` defaults `paceCeilingMinKm` to `3.0f` (3:00 min/km).
  • Verify custom pace ceiling values within `[2.0f, 6.0f]` persist and re-read correctly.
  • Verify out-of-range values are clamped to `[2.0f, 6.0f]`.
  • Verify factory reset restores `paceCeilingMinKm` to `3.0f`.
  2. *Expert Settings UI & Architecture Contract Tests (`AdvancedTuningPaceCeilingContractTest.kt`)*:
  • Verify `AdvancedTuningDialog.kt` Section 4 hosts `TuningSliderItem` for `paceCeilingMinKm`.
  • Verify slider range is `2.0f..6.0f` with `steps = 15`.
  • Verify `MapDetailLayout.kt` forwards `tuningConfig.paceCeilingMinKm` to `TelemetryMetricGraph`.
  3. *Pace Telemetry Graph Clamping & Y-Axis Bounds Tests (`TelemetryMetricGraphPaceCeilingTest.kt`)*:
  • Verify `extractMetricValue` clamps pace points faster than `effectiveCeiling` to the ceiling (e.g. 3.0 min/km) and eliminates hardcoded 1.5 clamping.
  • Verify `calculateBounds` enforces `bounds.first` is at least `effectiveCeiling`, preventing top Y-axis label from rendering 1:30 min/km.
  • Verify Imperial unit conversion scales the ceiling by `BANALService.METER_PER_MILE / 1000.0`.
  4. *9-Language Localization Audit (`TranslationParityTest.kt`)*:
  • Verify 100% parity across all 9 localized `strings.xml` files for `tuning_pace_ceiling_title` and `tuning_pace_ceiling_desc`.
  5. *Clean-Room Full Suite Regression Execution*:
  • Run `./gradlew testDebugUnitTest` verifying 100% pass rate.

### TST-UI-203: **Lieblingsorte/UI: Harmonize Map Preview Thumbnail Dimensions to 100dp on KnownLocationCard Verification** [Map preview thumbnail dimensions on KnownLocationCard are harmonized to 100dp x 100dp; visual parity with route cluster cards is achieved; all gestures, edit dialog, delete context menu, and navigation drill-downs operate cleanly; 100% test pass rate.]
- **Requirement(s)**: `REQ-UI-244`
- **Component**: **Lieblingsorte/UI: Harmonize Map Preview Thumbnail Dimensions to 100dp on KnownLocationCard Verification**
- **Jira**: ``ATT-2051``
- **Test Description**:
  1. *Structural Contract Test (`KnownLocationCardLayoutTest.kt`)*:
  • Verify `KnownLocationsScreen.kt` declares `Modifier.size(100.dp)` for `KnownLocationThumbnailMap`.
  • Verify 12dp rounded corners (`RoundedCornerShape(12.dp)`).
  • Verify `location_map_preview_${item.id}` test tag.
  • Verify offline preview fallback (`LocalInspectionMode.current`).
  2. *Component & ViewModel Regression Tests*:
  • Run `KnownLocationsScreenTest.kt`, `KnownLocationZoomMathTest.kt`, and `KnownLocationsViewModelTest.kt`.
  3. *9-Language Localization Audit (`TranslationParityTest.kt`)*:
  • Verify 100% parity across all 9 localized `strings.xml` files.
  4. *Clean-Room Full Suite Regression Execution*:
  • Run `./gradlew testDebugUnitTest` verifying 100% pass rate.

### TST-UI-204: **Aftermath/Details: Upper Metadata Slot Architecture & Reactive Detail Section Rendering in MapDetailLayout Verification** [MapDetailLayout upper metadata slot renders WorkoutDescription and WorkoutExtrema above the map; visual order parity with WorkoutSummary.kt is achieved; preferences reactively toggle visibility; backward compatibility for other callers preserved; 100% test pass rate.]
- **Requirement(s)**: `REQ-UI-245`
- **Component**: **Aftermath/Details: Upper Metadata Slot Architecture & Reactive Detail Section Rendering in MapDetailLayout Verification**
- **Jira**: ``ATT-2112``
- **Test Description**:
  1. *Structural Contract Test (`MapDetailLayoutMetadataSlotContractTest.kt`)*:
  • Verify `MapDetailLayout.kt` declares optional `metadataContent: (@Composable ColumnScope.() -> Unit)? = null` parameter.
  • Verify `metadataContent` is rendered immediately beneath `header()` and above `BoxWithConstraints` (the map container).
  • Verify `metadataContent` is omitted from `lowerColumn`.
  2. *Architecture & Routing Contract Test (`TrackOnMapScreenMetadataRoutingContractTest.kt`)*:
  • Verify `TrackOnMapScreen.kt` routes `WorkoutDescription` and `WorkoutExtrema` into `metadataContent`.
  • Verify `WorkoutDescription` is gated by `activeDetailPrefs.showDescription`.
  • Verify `WorkoutExtrema` is gated by `activeDetailPrefs.showExtrema`.
  • Verify `analyticsContent` does not duplicate description or extrema.
  3. *9-Language Localization Audit (`TranslationParityTest.kt`)*:
  • Verify 100% parity across all 9 localized `strings.xml` files.
  4. *Clean-Room Full Suite Regression Execution*:
  • Run `./gradlew testDebugUnitTest` verifying 100% pass rate.

### TST-UI-205: **Aftermath/Scrubbing: Align Scrubbing Telemetry Badge with Zoom Controls and Offset to Top-Right in MapDetailLayout Verification** [ScrubbingTelemetryBadge top edge aligns with zoom controls and offsets to top-right in MapDetailLayout; unifies viewport container; zero graph occlusion; 100% test pass rate.]
- **Requirement(s)**: `REQ-UI-246`
- **Component**: **Aftermath/Scrubbing: Align Scrubbing Telemetry Badge with Zoom Controls and Offset to Top-Right in MapDetailLayout Verification**
- **Jira**: ``ATT-2113``
- **Test Description**:
  1. *Structural Contract Test (`MapDetailLayoutScrubbingBadgeContractTest.kt`)*:
  • Verify `MapDetailLayout.kt` specifies `Alignment.TopEnd` and `padding(top = 2.dp, end = 8.dp)` for `ScrubbingTelemetryBadge`.
  • Verify shared viewport `Box` encapsulates `GlobalTelemetryZoomToolbar` and `lowerColumn` before `scrubbingOverlay()`.
  2. *9-Language Localization Audit (`TranslationParityTest.kt`)*:
  • Verify 100% parity across all 9 localized `strings.xml` files.
  3. *Clean-Room Full Suite Regression Execution*:
  • Run `./gradlew testDebugUnitTest` verifying 100% pass rate.

### TST-UI-206: **Aftermath/Summary: Click-to-Open Navigation and Gesture Disambiguation for Telemetry Graphs and Zone Distribution Cards in WorkoutSummary Verification** [Telemetry graphs and zone cards in WorkoutSummary trigger onMapClick() on tap; pointer input is disabled in list preview mode ensuring fluid vertical scrolling; full interactive scrubbing in MapDetailLayout is preserved; 100% test pass rate.]
- **Requirement(s)**: `REQ-UI-247`
- **Component**: **Aftermath/Summary: Click-to-Open Navigation and Gesture Disambiguation for Telemetry Graphs and Zone Distribution Cards in WorkoutSummary Verification**
- **Jira**: ``ATT-2129``
- **Test Description**:
  1. *Structural Contract Test (`TelemetryMetricGraphGesturesContractTest.kt`)*:
  • Verify `TelemetryMetricGraph.kt` declares optional `enableGestures: Boolean = true` parameter.
  • Verify `Canvas` pointerInput is conditionally attached only when `enableGestures == true`.
  2. *Architecture & Navigation Contract Test (`WorkoutSummaryClickContractTest.kt`)*:
  • Verify `WorkoutSummary.kt` applies `mapClickModifier` to Speed/Pace, Heart Rate, and Power graph containers.
  • Verify `WorkoutSummary.kt` passes `enableGestures = false` to all `TelemetryMetricGraph` instances.
  • Verify `WorkoutSummary.kt` attaches `mapClickModifier` to `HeartRateZoneDistributionCard` and `PowerZoneDistributionCard`.
  3. *9-Language Localization Audit (`TranslationParityTest.kt`)*:
  • Verify 100% parity across all 9 localized `strings.xml` files.
  4. *Clean-Room Full Suite Regression Execution*:
  • Run `./gradlew testDebugUnitTest` verifying 100% pass rate.

### TST-UI-207: **Aftermath/Details: Strava Activity Data Upper Metadata Slotting & Structural Parity in Detailed Workout View Verification** [StravaActivitySection renders in metadataContent above the map in TrackOnMapScreen, matching the canonical order of WorkoutSummary.kt; preference gating strictly enforced; analyticsContent does not duplicate Strava; 100% test pass rate.]
- **Requirement(s)**: `REQ-UI-248`
- **Component**: **Aftermath/Details: Strava Activity Data Upper Metadata Slotting & Structural Parity in Detailed Workout View Verification**
- **Jira**: ``ATT-2137``
- **Test Description**:
  1. *Architecture & Routing Contract Test (`TrackOnMapScreenMetadataRoutingContractTest.kt`)*:
  • Verify `TrackOnMapScreen.kt` routes `StravaActivitySection` into `metadataContent` immediately following `WorkoutExtrema`.
  • Verify `StravaActivitySection` in `metadataContent` is guarded by `activeDetailPrefs.showStrava && !workoutData.stravaActivityData.isNullOrBlank()`.
  • Verify `analyticsContent` does not duplicate `StravaActivitySection`.
  2. *9-Language Localization Audit (`TranslationParityTest.kt`)*:
  • Verify 100% parity across all 9 localized `strings.xml` files.
  3. *Clean-Room Full Suite Regression Execution*:
  • Run `./gradlew testDebugUnitTest` verifying 100% pass rate.

### TST-UI-208: **Aftermath/Zoom: Reactive Zoom Controls Suppression in MapDetailLayout When No Zoomable Graphs Are Rendered Verification** []
- **Requirement(s)**: `REQ-UI-249`
- **Component**: **Aftermath/Zoom: Reactive Zoom Controls Suppression in MapDetailLayout When No Zoomable Graphs Are Rendered Verification**
- **Jira**: ``ATT-2138``
- **Test Description**:
  1. *Structural Contract Test (`MapDetailLayoutZoomContractTest.kt`)*:
  • Verify `MapDetailLayout.kt` specifies `hasZoomToolbar = showZoomControls && (showElevationProfile

### TST-UI-209: **Aftermath/Layout: Collapsing Upper Metadata Architecture & Map Squashing Prevention via NestedScroll Verification** [Upper metadata collapses smoothly on scroll, freeing full screen height for map and telemetry graphs; minimum map height guaranteed in base state; 100% test pass rate.]
- **Requirement(s)**: `REQ-UI-250`
- **Component**: **Aftermath/Layout: Collapsing Upper Metadata Architecture & Map Squashing Prevention via NestedScroll Verification**
- **Jira**: ``ATT-2150``
- **Test Description**:
  1. *Structural & Architecture Contract Test (`MapDetailLayoutCollapsingHeaderContractTest.kt`)*:
  • Verify `MapDetailLayout.kt` integrates `CollapsingAppBarNestedScrollConnection`.
  • Verify root container attaches `Modifier.nestedScroll(connection)`.
  • Verify upper metadata container applies `Modifier.offset { IntOffset(0, connection.appBarOffset) }`.
  • Verify viewport container dynamically adjusts top padding based on `connection.appBarOffset`.
  2. *Clean-Room Full Suite Regression Execution*:
  • Run `./gradlew testDebugUnitTest` verifying 100% pass rate.

### TST-UI-210: **Aftermath/UI: Integrated Minimalist Marginal Zone Bars & Histogram Right of Telemetry Graph in Portrait Mode Verification** [Minimalist marginal bars rendered right of HR and Power graphs without text clutter; vertically aligned with zone thresholds; synchronous mode switching with zone cards; 100% test pass rate.]
- **Requirement(s)**: `REQ-UI-251`
- **Component**: **Aftermath/UI: Integrated Minimalist Marginal Zone Bars & Histogram Right of Telemetry Graph in Portrait Mode Verification**
- **Jira**: ``ATT-2147``
- **Test Description**:
  1. *Structural & Architectural Contract Test (`TelemetryMetricGraphMarginalBarsContractTest.kt`)*:
  • Verify `TelemetryMetricGraph.kt` accepts optional `zoneDistribution: ZoneDistributionData? = null` and `zoneDisplayMode: ZoneCardDisplayMode = ZoneCardDisplayMode.FIVE_ZONES`.
  • Verify `HeartRateZoneDistributionCard.kt` and `PowerZoneDistributionCard.kt` accept hoisted `displayMode` and `onDisplayModeChange`.
  • Verify drawing phase renders marginal strip rects vertically aligned with `valueToY` boundaries.
  2. *Clean-Room Full Suite Regression Execution*:
  • Run `./gradlew testDebugUnitTest` verifying 100% pass rate.

### TST-UI-211: **Aftermath/Details: Lap Split Visualizer Upper Metadata Slotting & Structural Parity in Detailed Workout View Verification** [LapSplitVisualizerCard renders in metadataContent above the map between Extrema and Strava in TrackOnMapScreen, matching the canonical order of WorkoutSummary.kt; preference gating strictly enforced; analyticsContent does not duplicate Laps; 100% test pass rate.]
- **Requirement(s)**: `REQ-UI-252`
- **Component**: **Aftermath/Details: Lap Split Visualizer Upper Metadata Slotting & Structural Parity in Detailed Workout View Verification**
- **Jira**: ``ATT-2170``
- **Test Description**:
  1. *Architecture & Routing Contract Test (`TrackOnMapScreenLapRoutingContractTest.kt`)*:
  • Verify `TrackOnMapScreen.kt` routes `LapSplitVisualizerCard` into `metadataContent` immediately following `WorkoutExtrema` and prior to `StravaActivitySection`.
  • Verify `LapSplitVisualizerCard` in `metadataContent` is guarded by `activeDetailPrefs.showLaps && splitChartData != null`.
  • Verify `analyticsContent` does not duplicate `LapSplitVisualizerCard`.
  2. *Clean-Room Full Suite Regression Execution*:
  • Run `./gradlew testDebugUnitTest` verifying 100% pass rate.

### TST-UI-212: **Tracking Views Null-Safe Deserialization & DB Seeding Verification** [Null and corrupt enum cursor values resolve cleanly to defaults with zero NullPointerExceptions; addDefaultTab seeds VIEW_SIZE on fresh installs; DB upgrade 11 repairs null values; 100% test pass rate.]
- **Requirement(s)**: `REQ-UI-253`
- **Component**: **Tracking Views Null-Safe Deserialization & DB Seeding Verification**
- **Jira**: ``ATT-2179``
- **Test Description**:
  1. *Repository Null & Invalid Enum Deserialization Test (`TrackingViewsRepositoryTest.kt`)*:
  • Verify `fetchSensorFieldConfigs` and `fetchSingleSensorFieldConfig` handle null `VIEW_SIZE`, `SENSOR_TYPE`, and `FILTER_TYPE` cursor values without throwing `NullPointerException`.
  • Verify graceful resolution to defaults: `ViewSize.NORMAL`, `SensorType.TIME_ACTIVE`, `FilterType.INSTANTANEOUS`.
  • Verify invalid string values fall back gracefully without throwing `IllegalArgumentException`.
  2. *Database Seeding & Migration Test (`TrackingViewsDatabaseManagerTest.kt`)*:
  • Verify `addDefaultTab(...)` populates `VIEW_SIZE` in `ContentValues`.
  • Verify `onUpgrade` for `oldVersion < 11` executes repair SQL setting `VIEW_SIZE = 'NORMAL'` where null.
  • Verify `getAllFilterData` handles null/invalid enum values safely.
  3. *Clean-Room Full Suite Regression Execution*:
  • Run `./gradlew testDebugUnitTest` verifying 100% pass rate.

### TST-UI-213: **Aftermath/Details: Non-Blocking Nested Scroll Dispatch, Sub-Pixel Precision & Optimized High-Density Metadata Verification** [Nested scroll dispatches smoothly in both directions without gesture trapping or premature header expansion; float precision prevents jitter; high-density segments and laps render fluidly; 100% test pass rate.]
- **Requirement(s)**: `REQ-UI-254`
- **Component**: **Aftermath/Details: Non-Blocking Nested Scroll Dispatch, Sub-Pixel Precision & Optimized High-Density Metadata Verification**
- **Jira**: ``ATT-2178``
- **Test Description**:
  1. *Nested Scroll Non-Blocking Contract Test (`CollapsingAppBarNestedScrollConnectionTest.kt`)*:
  • Verify `CollapsingAppBarNestedScrollConnection` preserves sub-pixel float precision during scroll delta accumulation without integer truncation jitter.
  • Verify downward scroll deltas (`available.y > 0`) are not eagerly consumed in `onPreScroll` when the child view is scrolled away from top, preventing lower column trapping.
  • Verify upward scroll deltas smoothly collapse `appBarOffset` to `-appBarMaxHeight`.
  2. *MapDetailLayout Scroll & Viewport Contract Test (`MapDetailLayoutScrollContractTest.kt`)*:
  • Verify viewport offset and padding math does not cause recomposition thrashing.
  • Verify `maxMetadataHeightDp` calculation bounds upper metadata to preserve `SplitPaneMath.MIN_MAP_HEIGHT` (120dp).
  3. *High-Density Performance Contract Test (`StravaActivitySectionPerformanceTest.kt`)*:
  • Verify `StravaActivitySection` handles workouts with 40+ segment efforts (Einstein HM volume) without UI lockup, redundant object allocations, or crashes.
  4. *9-Language Localization Audit*:
  • Verify 100% parity across all 9 locales (EN, DE, ES, FR, IT, JA, NL, PL, PT).
  5. *Clean-Room Full Suite Regression Execution*:
  • Run `./gradlew testDebugUnitTest` verifying 100% pass rate.

### TST-UI-214: **Aftermath: User-Customizable Section Reordering, Unified Order Persistence & Modular Section Architecture Verification** [User-customizable section reordering persists across app sessions and dynamically drives section sequences in WorkoutSummary.kt and TrackOnMapScreen.kt while preserving fixed top identity anchors and 120dp minimum map height safety; 100% test pass rate.]
- **Requirement(s)**: `REQ-UI-255`
- **Component**: **Aftermath: User-Customizable Section Reordering, Unified Order Persistence & Modular Section Architecture Verification**
- **Jira**: ``ATT-2176``
- **Test Description**:
  1. *Domain & Persistence Contract Test (`WorkoutSectionOrderPersistenceTest.kt`)*:
  • Verify `WorkoutSectionType` contains all 8 canonical sections: `DESCRIPTION`, `EXTREMA`, `LAPS`, `STRAVA`, `MAP`, `ELEVATION`, `CHARTS`, `ZONES`.
  • Verify default sequence matches `[DESCRIPTION, EXTREMA, LAPS, STRAVA, MAP, ELEVATION, CHARTS, ZONES]`.
  • Verify `MyPreferenceManager` serialization, deserialization, and missing value self-healing.
  2. *Reordering Logic & UI Contract Test (`WorkoutSectionReorderContractTest.kt`)*:
  • Verify `AdvancedTuningDialog.kt` provides accessible Move Up / Move Down controls.
  • Verify boundary constraints: Move Up disabled at index 0, Move Down disabled at last index.
  • Verify swapping adjacent items produces the expected new sequence.
  3. *WorkoutSummary Dynamic Ordering Test (`WorkoutSummaryDynamicOrderContractTest.kt`)*:
  • Verify `WorkoutSummary.kt` iterates through the active custom order, maintaining `WorkoutHeader` and `WorkoutDetails` fixed at the top.
  4. *TrackOnMapScreen Dynamic Relative Slotting Test (`TrackOnMapScreenSectionSlottingContractTest.kt`)*:
  • Verify sections preceding `MAP` slot into `metadataContent` and sections following `MAP` slot into `lowerColumn` / `analyticsContent`.
  5. *9-Language Localization Audit*:
  • Verify 100% parity across all 9 locales (EN, DE, ES, FR, IT, JA, NL, PL, PT).
  6. *Clean-Room Full Suite Regression Execution*:
  • Run `./gradlew testDebugUnitTest` verifying 100% pass rate.

### TST-UI-215: **Equipment Management: Fleet-Wide Equipment-to-Sensor Mapping Matrix with Checkboxes Verification** [Centralized, interactive equipment-to-sensor mapping matrix with checkboxes accurately maps and toggles links in SQLite; 3 tabs integrated in EquipmentTabsScreen; sticky first column functions smoothly; 100% test pass rate.]
- **Requirement(s)**: `REQ-UI-256`
- **Component**: **Equipment Management: Fleet-Wide Equipment-to-Sensor Mapping Matrix with Checkboxes Verification**
- **Jira**: ``ATT-2126``
- **Test Description**:
  1. *Sensor Aggregation & Database Link Contract Test (`EquipmentDbLinkContractTest.kt`)*:
  • Verify `EquipmentDbHelper.addDeviceLink`, `removeDeviceLink`, and `setDeviceLink` accurately modify rows in SQLite `LINKS` table idempotently.
  • Verify `DevicesDatabaseManager.getAllRemoteSensors()` returns all paired ANT+ and BLE devices while excluding phone-internal sensors.
  2. *ViewModel Reactive Matrix State Test (`EquipmentViewModelMatrixTest.kt`)*:
  • Verify `EquipmentViewModel.allRemoteSensors` initialization and `setSensorLink` reactivity.
  • Verify toggling a link updates `linkedDeviceIds` on targeted equipment and immediately reloads `bikes` and `shoes`.
  3. *Matrix Composable Structural Contract Test (`EquipmentSensorMatrixContractTest.kt`)*:
  • Verify `EquipmentTabsScreen.kt` declares 3 tabs and suppresses FAB on page 2.
  • Verify `EquipmentSensorMatrixScreen` renders sticky first column and sections for Bikes and Shoes.
  4. *9-Language Localization Audit*:
  • Verify 100% parity across all 9 locales (EN, DE, ES, FR, IT, JA, NL, PL, PT) for new string keys.
  5. *Clean-Room Full Suite Regression Execution*:
  • Run `./gradlew testDebugUnitTest` verifying 100% pass rate.

### TST-UI-216: **Compact Equipment Card Layout & Harmonized Inter-Card Spacing Verification** [Equipment list items exhibit clean 6dp–8dp inter-card spacing without 20dp compounding gaps; inactive and 0-workout items present compact card layout while preserving 48dp minimum touch target and delete contract; 100% test pass rate.]
- **Requirement(s)**: `REQ-UI-257`
- **Component**: **Compact Equipment Card Layout & Harmonized Inter-Card Spacing Verification**
- **Jira**: ``ATT-2059``
- **Test Description**:
  1. *Layout Metrics & Spacing Contract Test (`EquipmentCardSpacingContractTest.kt`)*:
  • Verify `EquipmentList` configures `verticalArrangement` with compact spacing (6.dp–8.dp).
  • Verify `MappableListItem` inside `EquipmentItem` has `vertical = 0.dp` margin to prevent double-gap compounding.
  • Verify minimum card touch target height satisfies WCAG accessibility (>= 48.dp).
  2. *Adaptive Compact Presentation Test (`EquipmentCompactCardTest.kt`)*:
  • Verify 0-workout items (`totalWorkouts == 0`) resolve compact typography (`titleMedium`) and vertical padding (`8.dp`).
  • Verify active items with workouts (`totalWorkouts > 0`) retain standard typography (`titleLarge`), 12.dp padding, and stats summary block.
  • Verify retired items (`isRetired == true`) apply `0.75f` alpha to `MappableListItem`.
  3. *Interactive Invariants Test*:
  • Verify single-tap triggers `onConfigClick`.
  • Verify long-press triggers delete dropdown menu anchored at `TopStart` per `REQ-UI-061`.
  4. *Clean-Room Regression Execution*:
  • Run `./gradlew testDebugUnitTest` verifying 100% pass rate.

### TST-UI-217: **Cockpit Sensor Field Visual Styling Architecture, Expert Settings Selection & Reactive Grid Layout Verification** [Default production parameters remain 100% faithful to baseline (CLASSIC_SEAMLESS); 4 distinct variants cleanly specified, selectable in Expert Settings, persisted in DataStore, and reactively reflected in live cockpit; 100% test pass rate.]
- **Requirement(s)**: `REQ-UI-258`
- **Component**: **Cockpit Sensor Field Visual Styling Architecture, Expert Settings Selection & Reactive Grid Layout Verification**
- **Jira**: ``ATT-2058``
- **Test Description**:
  1. *Production Default Baseline Invariance Test (`SensorFieldStyleContractTest.kt`)*:
  • Verify default `TuningPreferencesDefaults.SENSOR_FIELD_VARIANT` evaluates to `SensorFieldVariant.CLASSIC_SEAMLESS`.
  • Verify default `SensorFieldStyle.forVariant(CLASSIC_SEAMLESS)` resolves `RectangleShape`, `0.dp` spacing, and `0.dp` elevation.
  2. *Variant Specification & Geometry Test (`SensorFieldStyleContractTest.kt`)*:
  • Verify `CLASSIC_SEAMLESS` resolves `RectangleShape`, `0.dp` spacing, `0.dp` elevation.
  • Verify `OUTLINED_TILES` resolves `RoundedCornerShape(6.dp)`, `4.dp` spacing, `0.dp` elevation.
  • Verify `ELEVATED_CARDS` resolves `RoundedCornerShape(8.dp)`, `6.dp` spacing, `2.dp` elevation.
  • Verify `SOFT_CAPSULES` resolves `RoundedCornerShape(12.dp)`, `8.dp` spacing, `1.dp` elevation.
  3. *DataStore Persistence & Atomic Reset Test (`TuningPreferencesDataStoreTest.kt`)*:
  • Verify `KEY_SENSOR_FIELD_VARIANT` persists and retrieves selected variant.
  • Verify `resetToDefaults()` atomically resets variant to `CLASSIC_SEAMLESS`.
  4. *Cockpit Tuning Section & Subtitle Formatter Test (`TuningSubtitleFormatterTest.kt`)*:
  • Verify `formatCockpitSubtitle` includes localized variant name.
  5. *Zone Bar Clipping & Selection Interaction Contract Test*:
  • Verify 6dp indicator strip clips cleanly to rounded card boundaries.
  • Verify configuration mode (`ScreenMode.CONFIGURATION`) selection border (`2.dp primary`) renders accurately on all variants.
  6. *9-Language Localization Audit*:
  • Verify all 5 variant titles and settings descriptions exist across all 9 locales (EN, DE, ES, FR, IT, JA, NL, PL, PT).
  7. *Clean-Room Regression Suite*:
  • Run `./gradlew testDebugUnitTest` verifying 100% pass rate.

### TST-UI-218: **Conditional Research Button Visibility Based on Real Paired Remote Devices Verification** [ResearchButton is conditionally rendered only when real paired remote devices exist in SQLite; cold-start instantaneous seeding prevents layout flicker; trademark logos unchanged; 100% unit test pass rate.]
- **Requirement(s)**: `REQ-UI-259`
- **Component**: **Conditional Research Button Visibility Based on Real Paired Remote Devices Verification**
- **Jira**: ``ATT-2057``
- **Test Description**:
  1. *ViewModel Reactive Paired Remote State Tests (`ControlTrackingViewModelTest.kt`)*:
  • Verify `hasPairedRemoteDevices` emits `false` when list contains only `Protocol.SMARTPHONE` devices (e.g. GPS, Battery).
  • Verify `hasPairedRemoteDevices` emits `true` when a paired `Protocol.BLUETOOTH_LE` or `Protocol.ANT_PLUS` device is present with `isPaired == true`.
  • Verify `hasPairedRemoteDevices` emits `false` when a Bluetooth LE or ANT+ device exists with `isPaired == false`.
  2. *Database Direct Fast Query Test (`DevicesDatabaseManagerTest.kt`)*:
  • Verify `DevicesDatabaseManager.hasPairedRemoteDevices()` executes optimized `LIMIT 1` query filtering `paired > 0 AND (protocol = 'ANT_PLUS' OR protocol = 'BLUETOOTH_LE')`.
  • Verify cursor closing and accurate boolean result on 0 vs 1 rows.
  3. *UI Conditional Composition Contract Test (`ControlTrackingScreenTest.kt` / Previews)*:
  • Verify `ControlTrackingScreen` renders `ResearchButton` when `showResearchButton == true` and hides it when `showResearchButton == false`.
  • Verify central information area (`SearchArea` and `RemoteDevices`) preserves `Alignment.TopCenter` with zero horizontal layout shift.
  4. *Brand Compliance & 9-Language Localization Audit*:
  • Verify official ANT+ and Bluetooth trademark assets remain completely unmodified.
  • Verify `R.string.research` ("Suchen") exists in all 9 supported locales (EN, DE, ES, FR, IT, JA, NL, PL, PT).
  5. *Clean-Room Full Suite Regression Execution*:
  • Run `./gradlew testDebugUnitTest` verifying 100% pass rate.

### TST-UI-219: **Centralized Zone Threshold Resolution & O(log N) Scrubbing Point Search Verification** [Centralized zone resolution eliminates duplicate boilerplate; binary search resolves nearest scrubbing point in O(log N) comparisons with exact numerical parity to linear search; zero layout regressions; 100% unit test pass rate.]
- **Requirement(s)**: `REQ-UI-260`
- **Component**: **Centralized Zone Threshold Resolution & O(log N) Scrubbing Point Search Verification**
- **Jira**: ``ATT-2031``
- **Test Description**:
  1. *Threshold Resolution Factory Unit Tests (`TelemetryZoneMathTest.kt`)*:
  • Verify `TelemetryZoneMath.loadHeartRateThresholds` correctly queries `HR_BIKE` for `BSportType.BIKE` and `HR_RUN` for other sport types.
  • Verify returning `null` if any zone threshold is $\le 0$.
  • Verify `TelemetryZoneMath.loadPowerThresholds` queries `PWR_BIKE` and returns `PowerZoneThresholds` or `null` if invalid.
  2. *Scrubbing Point Lookup Binary Search Unit Tests (`TelemetryMetricUtilsTest.kt`)*:
  • Verify `TelemetryMetricUtils.findNearestPoint` on distance domain against standard `minByOrNull` across varying track lengths.
  • Verify time domain lookup (`isTimeDomain = true`).
  • Edge cases: Empty list returns `null`, single-element list returns that element, exact matches return matching point, points before start return first point, points after end return last point, intermediate values choose nearest neighbor.
  3. *Component Call-Site DRY Verification*:
  • Verify `ElevationProfile.kt`, `TelemetryMetricGraph.kt`, and `MapDetailLayout.kt` invoke `TelemetryZoneMath` and `TelemetryMetricUtils.findNearestPoint` without redundant boilerplate queries or linear scans.
  4. *Clean-Room Full Suite Regression Execution*:
  • Run `./gradlew testDebugUnitTest` verifying 100% pass rate.

### TST-UI-220: **Canonical Horizontal Domain Evaluation & Total Span Engine (ProfileDomainMath) Verification** [ProfileDomainMath provides unified single source of truth for horizontal domain and span calculations; corrupt zero-timestamp tracks gracefully fall back to distance; stationary sessions resolve to time; 100% unit test pass rate.]
- **Requirement(s)**: `REQ-UI-261`
- **Component**: **Canonical Horizontal Domain Evaluation & Total Span Engine (ProfileDomainMath) Verification**
- **Jira**: ``ATT-2032``
- **Test Description**:
  1. *Pure Mathematical Unit Tests (`ProfileDomainMathTest.kt`)*:
  • Verify `isTracklessWorkout` returns `true` for `(0.0, 100L)`, `false` for `(100.0, 100L)`, `false` for `(0.0, 0L)`, `false` for null/empty lists.
  • Verify `isEffectiveTimeDomain` returns `true` for trackless sessions regardless of configured domain.
  • Verify `isEffectiveTimeDomain` returns `true` when `domain == TIME` and `timeSec > 0`.
  • Verify `isEffectiveTimeDomain` falls back to `false` when `domain == TIME` but `timeSec <= 0` (corrupt track safety).
  • Verify `calculateTotalSpan` accurately resolves seconds vs meters across combinations.
  2. *Component Integration & DRY Verification*:
  • Verify `ElevationProfile.kt`, `TelemetryMetricGraph.kt`, and `MapDetailLayout.kt` consume `ProfileDomainMath` exclusively.
  • Verify `TelemetryMetricGraph.kt` eliminates `minByOrNull` cursor scan.
  3. *9-Language Localization Audit (`TranslationParityTest.kt`)*:
  • Verify 100% parity across all 9 localized `strings.xml` files.
  4. *Clean-Room Full Suite Regression Execution*:
  • Run `./gradlew testDebugUnitTest` verifying 100% pass rate.

### TST-UI-221: **Modular Component Architecture for Advanced Tuning Dialog Verification** [Monolithic AdvancedTuningDialog.kt successfully decomposed into focused category files under categories/ and TuningFormControls.kt; all files strictly under 400 lines; 100% behavioral and test parity; 100% unit test pass rate.]
- **Requirement(s)**: `REQ-UI-262`
- **Component**: **Modular Component Architecture for Advanced Tuning Dialog Verification**
- **Jira**: ``ATT-2033``
- **Test Description**:
  1. *Structural File Size & Modularity Tests (`AdvancedTuningModularityTest.kt`)*:
  • Verify each extracted category file exists under `categories/`: `CockpitTypographySection.kt`, `AmoledBatterySaverSection.kt`, `SensorsGpsFilterSection.kt`, `AftermathAnalysisSection.kt`, `WorkoutMasksAndCardsSection.kt`.
  • Verify shared controls exist in `TuningFormControls.kt`.
  • Verify line count of every file in `ui.settings.tuning` is strictly $< 400$ lines (AC-1).
  2. *Visual & Functional Contract Parity Tests*:
  • Execute `AdvancedTuningVisualContractTest.kt`, `AdvancedTuningAccordionTest.kt`, `AdvancedTuningAftermathContractTest.kt`, `AdvancedTuningPaceCeilingContractTest.kt`, `WorkoutSectionReorderContractTest.kt`, `CockpitFontExpansionTest.kt`, `LapDisplayModeSettingsTest.kt`.
  • Verify all accordion expansions, section reordering controls, pace ceiling slider (15 steps), single-choice segmented buttons, and DataStore flow collection remain 100% operational.
  3. *9-Language Localization Audit (`TranslationParityTest.kt`)*:
  • Verify 100% parity across all 9 localized `strings.xml` files.
  4. *Clean-Room Full Suite Regression Execution*:
  • Run `./gradlew testDebugUnitTest` verifying 100% pass rate.

### TST-UI-222: **Equipment Sensor Matrix Table Layout, Column Continuity, and Information Density Verification** [Top-left header displays Equipment title; continuous 1dp divider line between equipment column and sensor columns through section headers; sticky column expanded to 184dp with 2-line text wrapping; 100% unit test pass rate.]
- **Requirement(s)**: `REQ-UI-263`
- **Component**: **Equipment Sensor Matrix Table Layout, Column Continuity, and Information Density Verification**
- **Jira**: ``ATT-2306``
- **Test Description**:
  1. *Layout & Contract Tests (`EquipmentSensorMatrixScreenTest.kt`)*:
  • Verify `STICKY_COLUMN_WIDTH` is set to `184.dp`.
  • Verify top-left header cell uses `R.string.Equipment` ("Ausrüstung" / "Equipment").
  • Verify equipment name text wraps with `maxLines = 2`.
  • Verify continuous vertical divider across header, section headers, and equipment rows.
  2. *9-Language Localization Audit (`TranslationParityTest.kt`)*:
  • Verify 100% parity across all 9 localized `strings.xml` files for `Equipment`, `equipment_tab_sensor_matrix`, `equipment_type_bike`, `equipment_type_shoe`, and `equipment_retired`.
  3. *Clean-Room Full Suite Regression Execution*:
  • Run `./gradlew testDebugUnitTest` verifying 100% pass rate.

### TST-UI-223: **Workout Cards & Details Tuning Dialog Layout, Typography Wrapping, and Reordering Ergonomics Verification** [Reorder controls formatted as compact 32dp vertical stack; checkbox columns set to 50dp; section titles gain 60dp horizontal width, preventing mid-word line breaking in German; LapDisplayMode cleanly decoupled; 100% unit test pass rate.]
- **Requirement(s)**: `REQ-UI-264`
- **Component**: **Workout Cards & Details Tuning Dialog Layout, Typography Wrapping, and Reordering Ergonomics Verification**
- **Jira**: ``ATT-2305``
- **Test Description**:
  1. *Layout Metrics & Typography Contract Tests (`WorkoutMasksAndCardsLayoutTest.kt`)*:
  • Verify reorder controls container width is `32.dp`.
  • Verify checkbox column widths in header and content rows are `50.dp`.
  • Verify header spacer width matches reorder controls width (`32.dp`).
  • Verify lap display mode segmented button is decoupled from reorderable items and positioned as a footer card.
  2. *Existing Contract Preservation Tests*:
  • Execute `WorkoutSectionReorderContractTest.kt` verifying swap logic and icon contracts.
  • Execute `AdvancedTuningModularityTest.kt` verifying file existence and $< 400$ lines constraint.
  • Execute `LapDisplayModeSettingsTest.kt` verifying SegmentedButton preference bindings.
  3. *9-Language Localization Audit (`TranslationParityTest.kt`)*:
  • Verify 100% parity across all 9 localized `strings.xml` files for tuning matrix strings.
  4. *Clean-Room Full Suite Regression Execution*:
  • Run `./gradlew testDebugUnitTest` verifying 100% pass rate.

### TST-UI-224: **Workout List Filtering by Origin Source Attribute Verification** [WorkoutFilterCriteria filters workouts by origin source with accurate predicate matching, active count derivation, and JSON persistence; WorkoutSummariesViewModel reactively emits filtered list; 100% 9-language parity; 100% test pass rate.]
- **Requirement(s)**: `REQ-UI-265`
- **Component**: **Workout List Filtering by Origin Source Attribute Verification**
- **Jira**: ``ATT-2303``
- **Test Description**:
  1. *Domain Model & Predicate Tests (`WorkoutFilterCriteriaTest.kt`)*:
  • Verify predicate matching for all `WorkoutSource` values (TRACKED, TCX, GPX, FIT).
  • Verify non-matching workouts rejection.
  • Verify null source filter matches all workouts.
  • Verify `activeFilterCount` derivation when `source != null`.
  • Verify JSON serialization and deserialization round-trip for each source.
  • Verify backward compatibility when `"source"` is absent in JSON.
  2. *ViewModel Reactive Filtering Tests (`WorkoutSummariesViewModelFilterTest.kt`)*:
  • Verify setting criteria with source restricts emitted `workouts` flow strictly to matching workouts.
  • Verify clearing criteria restores all workouts.
  • Verify multi-criteria conjunction (source + sport, source + year).
  3. *9-Language Localization Audit (`TranslationParityTest.kt`)*:
  • Verify `filter_section_origin_source` exists across all 9 localized `strings.xml` files.
  • Verify `workout_source_tracked`, `workout_source_tcx`, `workout_source_gpx`, and `workout_source_fit` exist across all 9 localized `strings.xml` files.
  4. *Clean-Room Full Suite Regression Execution*:
  • Run `./gradlew testDebugUnitTest` verifying 100% pass rate.

### TST-UI-225: **Immediate Collapsing Header Quick Return Expansion Verification** [CollapsingAppBarNestedScrollConnection immediately consumes downward scroll deltas in onPreScroll, restoring smooth Quick Return header expansion with sub-pixel precision and fluid child handoff; 100% unit test pass rate.]
- **Requirement(s)**: `REQ-UI-266`
- **Component**: **Immediate Collapsing Header Quick Return Expansion Verification**
- **Jira**: ``ATT-2304``
- **Test Description**:
  1. *Unit Tests (`CollapsingAppBarNestedScrollConnectionTest.kt`)*:
  • Verify downward scroll deltas (`available.y > 0`) are consumed in `onPreScroll` when partially or fully collapsed (`rawOffset < 0`), immediately increasing `appBarOffset` towards `0`.
  • Verify downward scroll delta exceeding remaining offset clamps cleanly at `0` and defers excess delta to child containers.
  • Verify downward scroll returns `Offset.Zero` when already fully expanded (`rawOffset == 0`).
  • Verify upward scroll deltas (`available.y < 0`) continue to collapse header towards `-appBarMaxHeight` in `onPreScroll`, and defer excess to child.
  • Verify floating-point sub-pixel delta accumulation in `rawOffset` preserves sub-pixel movements without integer truncation jitter.
  2. *Clean-Room Regression Suite*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% pass rate.

### TST-UI-226: **Routes & Segments Elevation Profile Below Map & Interactive Zooming Verification** [MapDetailLayout activates two-pane resizable split layout for routes and segments with elevation profile positioned strictly below map, interactive GlobalTelemetryZoomToolbar (+, -, reset, pan), draggable SplitPaneDivider, and synchronized map marker scrubbing; 100% test pass rate.]
- **Requirement(s)**: `REQ-UI-267`
- **Component**: **Routes & Segments Elevation Profile Below Map & Interactive Zooming Verification**
- **Jira**: ``ATT-2311``
- **Test Description**:
  1. *Layout & Contract Tests (`MapDetailLayoutTest.kt`)*:
  • Verify `hasLowerSection` computes presence of elevation profile on routes and segments when `activeScrubPath` is non-empty.
  • Verify two-pane split-pane layout is activated when `showMap && hasLowerSection`, placing map in upper viewport and lower container with `ElevationProfile` below `SplitPaneDivider`.
  2. *Interactive Zooming Contract Tests (`MapDetailLayoutZoomContractTest.kt`)*:
  • Verify `GlobalTelemetryZoomToolbar` is rendered in lower viewport when `hasZoomToolbar` is true (routes and segments).
  • Verify toolbar height allocation and dynamic available height calculations in `SplitPaneMath`.
  3. *Standalone Map & Bottom Sheet Invariants (`LiveSegmentSheetLayoutTest.kt`)*:
  • Verify standalone map screens without lower content fill 100% of viewport.
  • Verify bottom sheets (`showMap == false`, e.g. `LiveSegmentSheet`) preserve compact `wrapContentHeight()`.
  4. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` verifying 100% pass rate.

### TST-UI-227: **SensorSourceDialog Truthful Connection Status, Parameterization & Priority Sorting Verification** [Actively connected primary and backup sensors in SensorSourceDialog render (verfügbar) / (available) next to battery level, disconnected sensors render (nicht verbunden), active backups and disconnected devices are sorted strictly by MySensorManager canonical priority order (Google Fused before Network), and DeviceStatusRow maintains full backward compatibility; 100% clean-room test pass rate.]
- **Requirement(s)**: `REQ-UI-268`
- **Component**: **SensorSourceDialog Truthful Connection Status, Parameterization & Priority Sorting Verification**
- **Jira**: ``ATT-2194`, `ATT-4375``
- **Test Description**:
  1. *DeviceStatusRow Unit Tests (`DeviceStatusRowTest.kt`)*:
  • Verify `DeviceStatusRow` renders `R.string.devices_available` when `isConnected = true`, regardless of underlying `device.isConnected` field.
  • Verify `DeviceStatusRow` renders `R.string.devices_not_connected` (or relative timestamp) when `isConnected = false`.
  • Verify `DeviceStatusRow` defaults `isConnected` to `device.isConnected` when parameter is omitted, preserving 100% backward compatibility.
  2. *SensorSourceDialog Architectural Contract Test (`SensorSourceDialogContractTest.kt`)*:
  • Verify `SensorSourceDialog.kt` passes `isConnected = isConnected` from `DeviceIdentityBlock` to `DeviceStatusRow`.
  3. *SensorSourceDialog Active Backups Priority Sorting Test (`SensorSourceDialogPriorityTest.kt`)*:
  • Given multiple active backup sensors transmitting telemetry for `sensorType` (e.g. `SPEED_AND_LOCATION_GOOGLE_FUSED` and `SPEED_AND_LOCATION_NETWORK`), verify `activeBackups` is sorted by `MySensorManager.getPriorityRank(sensorType, dev.deviceType)` (placing Google Fused before Network), then by device name, then by device ID.
  4. *SensorSourceDialog Disconnected Devices Priority Sorting Test (`SensorSourceDialogPriorityTest.kt`)*:
  • Given disconnected paired location devices (`SPEED_AND_LOCATION_GOOGLE_FUSED` and `SPEED_AND_LOCATION_NETWORK`), verify `notConnected` is sorted by `MySensorManager.getPriorityRank(sensorType, it.deviceType)` (placing Google Fused before Network), then by device name, then by device ID.
  5. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with zero regressions.

### TST-UI-228: **ClusterInfoDialog Preserved Bullet Point Line Breaks & 9-Language Localization Audit Verification** [cluster_info_fingerprint_desc contains zero &#10; entities and uses literal \n escapes across all 9 locales, ensuring each of the 4 topological fingerprint criteria starts on its own line; 100% test pass rate.]
- **Requirement(s)**: `REQ-UI-269`
- **Component**: **ClusterInfoDialog Preserved Bullet Point Line Breaks & 9-Language Localization Audit Verification**
- **Jira**: ``ATT-2195``
- **Test Description**:
  1. *9-Language Resource Existence (`ClusterInfoDialogContractTest.kt`)*:
  • Verify `cluster_info_fingerprint_desc` exists and is non-empty across all 9 language packs (EN, DE, ES, FR, IT, JA, NL, PL, PT).
  2. *XML Entity Elimination Audit (`ClusterInfoDialogContractTest.kt`)*:
  • Verify `cluster_info_fingerprint_desc` contains zero instances of `&#10;` across all 9 locales.
  3. *Bullet Point Line Break Preservation (`ClusterInfoDialogContractTest.kt`)*:
  • Verify `cluster_info_fingerprint_desc` contains at least 4 `\n` line breaks separating each bullet criterion across all 9 locales.
  4. *Clean-Room Full Suite Regression*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% pass rate.

### TST-UI-229: **LiveSegmentSheet Clamped Bottom Sheet Height & MapDetailLayout Viewport Constraints Verification** []
- **Requirement(s)**: `REQ-UI-270`
- **Component**: **LiveSegmentSheet Clamped Bottom Sheet Height & MapDetailLayout Viewport Constraints Verification**
- **Jira**: ``ATT-2231``
- **Test Description**:
  1. *Architectural Contract Verification (`LiveSegmentSheetContractTest.kt`)*:
  • Verify `LiveSegmentSheet.kt` passes `showMap = false`, `showZoomControls = false`, and applies `Modifier.fillMaxWidth().wrapContentHeight()`.
  • Verify `MapDetailLayout.kt` applies `wrapContentHeight()` on the resizable viewport Box when `!showMap && !hasScrollableContent`.
  • Verify `MapDetailLayout.kt` conditionally attaches `Modifier.nestedScroll(connection)` only when `showMap

### TST-UI-230: **Sport-Specific Equipment Sensor Matrix Partitioning and Incompatible Sensor Restriction Verification** [Equipment sensor matrix cleanly partitioned into sport-specific tables for bikes and shoes with independent scrolling; incompatible sensor linkages restricted; 100% test pass rate.]
- **Requirement(s)**: `REQ-UI-256`
- **Component**: **Sport-Specific Equipment Sensor Matrix Partitioning and Incompatible Sensor Restriction Verification**
- **Jira**: ``ATT-2382``
- **Test Description**:
  1. *Sport-Specific Sensor Compatibility Unit Test (`EquipmentSportSensorCompatibilityTest.kt`)*:
  • Verify `DevicesDatabaseManager.getSensorsForSportType(BSportType.BIKE)` returns bike sensors (Power, Speed, Cadence, Radar, Shifting) and shared sensors (HRM, Environment), while strictly excluding running-exclusive sensors (`RUN_SPEED`).
  • Verify `DevicesDatabaseManager.getSensorsForSportType(BSportType.RUN)` returns run sensors (`RUN_SPEED`) and shared sensors (HRM, Environment), while strictly excluding cycling-exclusive sensors (Power, Speed, Cadence, Radar, Shifting).
  2. *ViewModel Sport-Partitioned Sensor Flow Test (`EquipmentViewModelMatrixTest.kt`)*:
  • Verify `bikeSensors` and `shoeSensors` reactive flows correctly filter remote sensors into their respective sport categories.
  • Verify unlinking/linking updates `EquipmentDbHelper` transactional table.
  3. *Single-Item Dialog Incompatible Sensor Restriction Test (`EditEquipmentDialogContractTest.kt`)*:
  • Verify `EditEquipmentDialog` for bike only exposes bike and shared sensors.
  • Verify `EditEquipmentDialog` for shoe only exposes run and shared sensors.
  4. *9-Language Localization Audit*:
  • Verify 100% parity across all 9 supported locales (EN, DE, ES, FR, IT, JA, NL, PL, PT) for any new empty-state or category string resources.
  5. *Clean-Room Regression Suite*:
  • Execute `./gradlew testDebugUnitTest` to guarantee 0 regressions across all test suites.

### TST-UI-231: **Settings/Tuning: Simplified Preposition-Free Column Headers (List & Details) in Workout Cards & Details Matrix Verification** [Simplified preposition-free column headers ("Liste" / "Details", "List" / "Details", "Lista" / "Szczegóły", etc.) align cleanly over 50dp checkbox columns without 2-line wrapping; defensive single-line typography enforced; 100% test pass rate.]
- **Requirement(s)**: `REQ-UI-271`
- **Component**: **Settings/Tuning: Simplified Preposition-Free Column Headers (List & Details) in Workout Cards & Details Matrix Verification**
- **Jira**: ``ATT-2383``
- **Test Description**:
  1. *Defensive UI Contract Tests (`WorkoutMasksAndCardsLayoutTest.kt`)*:
  • Verify `WorkoutMasksAndCardsSection.kt` renders `tuning_matrix_col_list` and `tuning_matrix_col_details` with `maxLines = 1`, `overflow = TextOverflow.Ellipsis`, and `textAlign = TextAlign.Center`.
  2. *String Simplification & Localization Parity (`TranslationParityTest.kt`)*:
  • Verify `tuning_matrix_col_list` and `tuning_matrix_col_details` exist across all 9 `strings.xml` files with concise singular noun translations.
  3. *Full Clean-Room Test Suite*:
  • Run `./gradlew testDebugUnitTest` verifying 100% pass rate.

### TST-UI-232: **Elevation Profile Adaptive X-Axis Tick Spacing and Dynamic Label Collision Prevention Verification** [Distance and time tick labels on elevation profile render without overlap across all card previews and detail screens; minimum 24dp clearance enforced; 100% test pass rate.]
- **Requirement(s)**: `REQ-UI-272`
- **Component**: **Elevation Profile Adaptive X-Axis Tick Spacing and Dynamic Label Collision Prevention Verification**
- **Jira**: ``ATT-2385``
- **Test Description**:
  1. *Step Calibration Unit Tests (`ElevationProfileZoomMathTest.kt`)*:
  • Verify `calculateAdaptiveDistanceStep(1470.0)` returns 250 m instead of 100 m.
  • Verify step thresholds across short, mid, and long tracks in Metric and Imperial.
  2. *Clearance Invariant Evaluation Tests (`ElevationProfileZoomMathTest.kt`)*:
  • Verify collision helper `shouldRenderTickLabel` rejects overlapping adjacent labels (< 24dp gap) and accepts valid labels (>= 24dp gap).
  • Verify collision helper rejects ticks colliding with `endLabel` boundary.
  3. *Architectural Contract Tests (`ElevationProfileContractTest.kt`)*:
  • Verify `ElevationProfile.kt` maintains `lastDrawnRightX` tracking in both distance and time rendering loops.
  4. *Full Clean-Room Test Suite*:
  • Run `./gradlew testDebugUnitTest` verifying 100% pass rate.

### TST-UI-233: **Routes & Segments: Dynamically Maximized Map with Bottom-Anchored Intrinsic Elevation Profile Verification** [In Route and Segment details, upper map dynamically expands with weight(1f) to occupy all available vertical space above elevation profile; lower elevation profile sizes intrinsically to content and sits flush with Navigation Bar with zero dead space; TrackOnMapScreen split-pane preserved; 100% test pass rate.]
- **Requirement(s)**: `REQ-UI-273`
- **Component**: **Routes & Segments: Dynamically Maximized Map with Bottom-Anchored Intrinsic Elevation Profile Verification**
- **Jira**: ``ATT-2386``
- **Test Description**:
  1. *Architectural Contract Tests (`MapDetailLayoutDynamicViewportContractTest.kt`)*:
  • Verify `MapDetailLayout.kt` checks `hasScrollableContent` within the `showMap && hasLowerSection` block.
  • Verify that when `hasScrollableContent == false`, `mapBox` receives `Modifier.weight(1f)`.
  • Verify that when `hasScrollableContent == false`, the lower container applies `Modifier.wrapContentHeight()`.
  • Verify that `SplitPaneDivider` is rendered only when `hasScrollableContent == true`.
  • Verify that `lowerColumn` in `!hasScrollableContent` retains `navigationBarsPadding()` anchoring to the Navigation Bar.
  2. *Existing Contract Suite Verification*:
  • Run `MapDetailLayoutCollapsingHeaderContractTest` and `SplitPaneDividerVisualContractTest` ensuring 100% pass rate.
  3. *Full Clean-Room Test Suite*:
  • Run `./gradlew testDebugUnitTest` verifying 100% pass rate.

### TST-UI-234: **Route Climbs Visual Prominence Across Elevation Profile, Map Polyline, Route Cards & Dedicated Breakdown Verification** [Route climbs prominently visible on elevation profile with colored ridge strokes and summit category badges, on route map with climb start markers, in header metrics, and interactive breakdown; backward-compatible fallback detection; 100% 9-language parity; 100% test pass rate.]
- **Requirement(s)**: `REQ-UI-274`
- **Component**: **Route Climbs Visual Prominence Across Elevation Profile, Map Polyline, Route Cards & Dedicated Breakdown Verification**
- **Jira**: ``ATT-2388``
- **Test Description**:
  1. *Model & Repository Climb Propagation Unit Tests (`RouteClimbsRepositoryTest.kt`)*:
  • Verify `RouteWithPath` and `MapRoute` hold `climbs: List<Climb>`.
  • Verify `RouteWithPath.toMapRoute()` propagates `climbs`.
  • Verify `RoutesRepository` enriches routes loaded via `allRoutes` from `ClimbsDatabaseManager`.
  • Verify fallback on-the-fly detection via `ClimbDetector.detectClimbs` for routes with zero cached climbs.
  2. *UI & Elevation Profile Contract Tests (`RouteClimbsUiContractTest.kt`)*:
  • Verify `ElevationProfile.kt` accepts `climbs: List<Climb>` and renders ridge accent stroke (3.5 dp) in category color and summit category badge pills in distance domain.
  • Verify `RouteSummaryHeader.kt` renders climb count and category metrics when climbs are present.
  • Verify `RouteOnMapScreen.kt` integrates climb start markers at `climb.startLatLng` and dedicated climb breakdown section.
  3. *9-Language Localization Audit (`RouteClimbsLocalizationTest.kt`)*:
  • Verify all new climb string resources exist and are non-empty across all 9 locales (EN, DE, ES, FR, IT, JA, NL, PL, PT).
  • Verify zero missing keys, zero AAPT2 entity issues, and matching formatting tokens.
  4. *Clean-Room Full Suite Regression*:
  • Run `./gradlew testDebugUnitTest` asserting 100% pass rate.

### TST-UI-235: **Unified WYSIWYG Tracking Tab Configuration with Spatial Overlays and Per-Tab Popup Toggles Verification** [WYSIWYG spatial cockpit configuration in ScreenMode.CONFIGURATION; per-tab gating of navigation prompts and live climbs; smooth SQLite migration with default 1; 100% 9-language translation parity; 100% test pass rate.]
- **Requirement(s)**: `REQ-UI-275`
- **Component**: **Unified WYSIWYG Tracking Tab Configuration with Spatial Overlays and Per-Tab Popup Toggles Verification**
- **Jira**: ``ATT-2360``
- **Test Description**:
  1. *Database Schema & Migration Unit Tests (`TrackingViewsDatabaseManagerTest.kt`)*:
  • Verify database upgrade from v11 to v12 adds `SHOW_LIVE_CLIMBS` and `SHOW_NAVIGATION_HINTS` with default value 1.
  • Verify `updateShowLiveClimbs` and `updateShowNavigationHints` persist changes in SQLite.
  2. *Repository & State Propagation Tests (`TrackingViewsRepositoryTest.kt`)*:
  • Verify `TrackingViewInfo` extracts `showLiveClimbs` and `showNavigationHints` from cursor.
  • Verify repository update methods update database and trigger flow refresh.
  • Verify backward-compatible default values (`true`) when constructing `TrackingViewInfo`.
  3. *UI & Contract Tests (`TrackingTabWysiwygContractTest.kt`)*:
  • Verify `TrackingTabConfigHeader.kt` does not contain checkbox rows.
  • Verify `SensorGridScreen.kt` renders spatial WYSIWYG toggles in `ScreenMode.CONFIGURATION`.
  • Verify runtime gating conditions for `TurnPromptBanner` and `LiveClimbSheet`.
  4. *9-Language Localization Audit (`TrackingTabLocalizationTest.kt`)*:
  • Verify all new string keys exist and are non-empty across all 9 locales (EN, DE, ES, FR, IT, JA, NL, PL, PT).
  5. *Clean-Room Full Suite Regression*:
  • Run `./gradlew testDebugUnitTest` asserting 100% pass rate.

### TST-UI-236: **Cockpit Tile Distinct Presets, Granular Corner Radius, Border Thickness, and Theme-Aware Border Contrast Verification** [Four distinct presets; athlete-adjustable corner radius (0-20dp), border thickness (0-4dp), and theme-aware contrast sliders in Cockpit Typography settings; live cockpit updates; 100% 9-language translation parity; 100% test pass rate.]
- **Requirement(s)**: `REQ-UI-276`
- **Component**: **Cockpit Tile Distinct Presets, Granular Corner Radius, Border Thickness, and Theme-Aware Border Contrast Verification**
- **Jira**: ``ATT-2456``
- **Test Description**:
  1. *Preset & Style Contract Tests (`SensorFieldStyleContractTest.kt`)*:
  • Verify `SensorFieldStyle.forVariant` yields distinctly differentiated baseline parameters across all 4 variants.
  • Verify `SensorFieldStyle.resolveBorder` returns `null` when `thickness <= 0.dp`.
  • Verify `SensorFieldStyle.resolveBorder` interpolates correctly to `Color.White` in Dark Mode and `Color.Black` in Light Mode when `contrast == 1.0f`.
  • Verify `SensorFieldStyle.resolveBorder` interpolates correctly to subtle tones when `contrast == 0.0f`.
  2. *DataStore Persistence & Clamping Tests (`TuningPreferencesDataStoreSensorFieldTest.kt`)*:
  • Verify persistence of `KEY_SENSOR_FIELD_CORNER_RADIUS`, `KEY_SENSOR_FIELD_BORDER_THICKNESS`, `KEY_SENSOR_FIELD_BORDER_CONTRAST`.
  • Verify boundary clamping: corner radius clamped to `0.0f .. 20.0f`, border thickness clamped to `0.0f .. 4.0f`, border contrast clamped to `0.0f .. 1.0f`.
  • Verify `resetToDefaults()` clears all 3 keys and restores default configuration.
  3. *UI Contract & Modularity Tests (`AdvancedTuningModularityTest.kt`)*:
  • Verify line count of `AdvancedTuningDialog.kt` and `CockpitTypographySection.kt` remains strictly under 400 lines.
  • Verify presence of Corner Radius, Border Thickness, and Border Contrast slider items in `CockpitTypographySection.kt`.
  4. *9-Language Localization Audit (`TranslationParityTest.kt`)*:
  • Verify all new string resources exist and are non-empty across all 9 application locales.
  5. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate.

### TST-UI-237: **Settings Live Preview Cockpit Tile Styling & Comprehensive Section Naming Verification** [Settings live preview renders sample metrics as individual cockpit tiles reflecting corner radius, elevation, border stroke, and spacing; section header renamed across all 9 locales; modularity preserved; 100% test pass rate.]
- **Requirement(s)**: `REQ-UI-277`
- **Component**: **Settings Live Preview Cockpit Tile Styling & Comprehensive Section Naming Verification**
- **Jira**: ``ATT-2457``
- **Test Description**:
  1. *UI & Live Preview Contract Tests (`CockpitPreviewTileContractTest.kt`)*:
  • Verify `CockpitTypographySection.kt` resolves tile shape, elevation, and border stroke using `SensorFieldStyle`.
  • Verify preview tiles utilize discrete Surface/Card containers with shape, border, and elevation.
  • Verify preview tiles are arranged horizontally with inter-tile spacing derived from `style.gridSpacing`.
  2. *Modularity & File Length Contract Tests (`AdvancedTuningModularityTest.kt`)*:
  • Verify `CockpitTypographySection.kt` line count is strictly < 400 lines.
  • Verify `AdvancedTuningDialog.kt` line count is strictly < 400 lines.
  3. *9-Language Localization Audit (`CockpitTypographyLocalizationTest.kt` & `TranslationParityTest.kt`)*:
  • Verify `tuning_cat_cockpit_typography` exists, is non-empty, and contains language-specific tile terminology across EN, DE, ES, FR, IT, JA, NL, PL, and PT.
  4. *Full Clean-Room Regression*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with 0 regressions.

### TST-UI-238: **Pairing Triggers Relocation to Sensor Management & Control Tracking Layout Optimization Verification** [Pairing triggers removed from Control Tracking screen; FAB added to "Meine Sensoren" tabs; bottom sheet protocol and device type selection; Start button vertically centered with bottom slot; 100% 9-language translation parity; 100% test pass rate.]
- **Requirement(s)**: `REQ-UI-278`
- **Component**: **Pairing Triggers Relocation to Sensor Management & Control Tracking Layout Optimization Verification**
- **Jira**: ``ATT-2189``
- **Test Description**:
  1. *Control Tracking UI & Layout Contract Tests (`ControlTrackingLayoutContractTest.kt`)*:
  • Verify `ControlTrackingScreen.kt` does not reference or render `PairingButtons`.
  • Verify `ControlTrackingScreen.kt` centers `ControlTrackingButton` and `SportTypeSelector` via equal-weight spacers.
  • Verify `ControlTrackingScreen.kt` exposes `bottomContent` slot parameter.
  • Verify `ControlTrackingButton` location gating (`REQ-PRI-004`) and warning badge remain intact.
  2. *Sensor Management Pairing UI Contract Tests (`DevicesTabbedScreenContractTest.kt`)*:
  • Verify `DevicesTabbedScreen.kt` renders pairing FAB with `Icons.Default.Add` and content description `@string/devices_pair_sensor`.
  • Verify pairing FAB click triggers protocol selection modal bottom sheet (`PairingProtocolBottomSheet`).
  • Verify selecting protocol displays `DeviceTypeSelectionDialog`.
  • Verify choosing device type triggers `tabViewModel.updateFilters(protocol, deviceType)` and starts search.
  3. *9-Language Localization Parity Audit (`PairingLocalizationTest.kt` & `TranslationParityTest.kt`)*:
  • Verify all newly added pairing string keys exist across all 9 supported locales with non-empty translations.
  4. *Full Clean-Room Regression Suite*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with 0 regressions.

### TST-UI-239: **Branded Route Selection Button on Control Tracking Screen & Cockpit Sensor Grid Decoupling Verification** [Route selection button removed from all cockpit sensor grid tabs; branded route selection card with green border/icon accent rendered in bottom slot of Control Tracking screen; active/inactive route state display and cancellation operational; in-ride auto-detection banner preserved; 100% 9-language translation parity; 100% test pass rate.]
- **Requirement(s)**: `REQ-UI-279`
- **Component**: **Branded Route Selection Button on Control Tracking Screen & Cockpit Sensor Grid Decoupling Verification**
- **Jira**: ``ATT-2458``
- **Test Description**:
  1. *UI & Contract Tests (`ControlTrackingRouteSelectionContractTest.kt`)*:
  • Verify `SensorGridScreen.kt` does NOT reference or render `RouteActionChipRow`.
  • Verify `ControlTrackingScreen.kt` provides `RouteSelectionButton` in `bottomContent`.
  • Verify `RouteSelectionButton.kt` implements design tokens: `RoundedCornerShape(12.dp)`, `BorderStroke(1.dp, TTColor.RouteSelected.copy(alpha = 0.35f))`, branded icon container (36.dp, 8.dp shape, `TTColor.RouteSelected.copy(alpha = 0.12f)`), and `R.drawable.ic_route` icon.
  • Verify inactive state displays "Select Route" (`@string/route_action_select`), descriptive subtitle, and trailing forward chevron (`Icons.AutoMirrored.Default.ArrowForwardIos`).
  • Verify active state displays "✓ <Route Name>" with distance + elevation metrics, and close/stop button (`Icons.Default.Close`) invoking route cancellation.
  • Verify `SensorGridScreen.kt` retains `AutoDetectedRouteBanner` for candidate detection.
  2. *Sensor Grid Integration Tests (`SensorGridScreenRouteIntegrationTest.kt`)*:
  • Verify that `SensorGridScreen` does NOT render `RouteActionChipRow`.
  • Verify that `AutoDetectedRouteBanner` continues to render when an auto-detected candidate route is present.
  3. *9-Language Localization Parity Audit (`RouteSelectionLocalizationTest.kt` & `TranslationParityTest.kt`)*:
  • Verify all newly added string keys exist across all 9 supported locales (EN, DE, ES, FR, IT, JA, NL, PL, PT) with non-empty translations and identical format specifiers.
  4. *Full Clean-Room Regression Suite*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with 0 regressions.

### TST-UI-240: **Route Selector Streamlining: Pinned Heimweg Card and Sort Tab Bar Removal Contract and Unit Verification** [Route selector bottom sheet streamlined by removing filter tabs and pinned Heimweg card; direct proximity-recency ranking via RouteProximityRanker operational; active route banner and auto-detection intact; 100% test pass rate.]
- **Requirement(s)**: `REQ-UI-280`
- **Component**: **Route Selector Streamlining: Pinned Heimweg Card and Sort Tab Bar Removal Contract and Unit Verification**
- **Jira**: ``ATT-2459``
- **Test Description**:
  1. *Route Selector Contract & Structural Decoupling Tests (`RouteSelectorSheetTest.kt`)*:
  • Verify `RouteSelectorSheet.kt` does NOT reference `TabRow` or `showFilterTabs`.
  • Verify `RouteSelectorSheet.kt` does NOT contain `onTakeMeHome` or `ic_nav_home` or `take_me_home_title`.
  • Verify `RouteSelectorModalBottomSheet` does NOT accept `onTakeMeHome`.
  • Verify public composables preserved: `RouteSelectorModalBottomSheet`, `RouteSelectorContent`, `ActiveRouteBanner`, `AutoDetectedRouteBanner`, `RouteCard`.
  2. *RouteSelectorViewModel Proximity-Recency Pipeline Tests (`RouteSelectorViewModelTest.kt`)*:
  • Verify initial state defaults directly to `RouteProximityRanker` ranking without filter tabs.
  • Verify location updates trigger proximity re-ranking.
  • Verify route selection, clear/stop, candidate activation, and candidate dismissal.
  • Verify absence of filter tabs and `showFilterTabs` in `RouteSelectorUiState`.
  3. *9-Language Localization Parity Audit (`TranslationParityTest.kt`)*:
  • Verify all route selector strings maintain 100% translation parity across all 9 supported application locales (EN, DE, ES, FR, IT, JA, NL, PL, PT).
  4. *Full Clean-Room Regression Suite*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with 0 regressions.

### TST-UI-241: **Configurable Route Selector Proximity Radius and Recency Sorting Verification** [Dedicated Navigation category in Advanced Settings; configurable radius (default 1 km) with factory reset support; strict straight-line start point filtering; recency sorting; dimmed button visual feedback when no routes qualify; 100% localization parity; 100% test pass rate.]
- **Requirement(s)**: `REQ-UI-281`
- **Component**: **Configurable Route Selector Proximity Radius and Recency Sorting Verification**
- **Jira**: ``ATT-2460``
- **Test Description**:
  1. *DataStore Radius Persistence & Reset Tests (`TuningPreferencesDataStoreTest.kt`)*:
  • Verify default `routeSelectionRadiusKm` is `1.0f`.
  • Verify saving custom radius `2.5f` persists in DataStore.
  • Verify boundary clamping: `0.1f` clamps to `0.5f`, `15.0f` clamps to `10.0f`.
  • Verify `resetToDefaults()` restores radius to `1.0f`.
  2. *RouteProximityRanker Radius Filtering & Recency Sorting Tests (`RouteProximityRankerTest.kt`)*:
  • Verify routes further than radius to start point are excluded.
  • Verify qualifying routes within radius sort by `syncedAt` descending (most recently ridden first).
  • Verify null location returns empty list.
  3. *RouteSelectorViewModel Dynamic Radius Pipeline Tests (`RouteSelectorViewModelTest.kt`)*:
  • Verify `uiState` reactively updates candidate routes upon radius preference changes.
  • Verify `uiState` reactively updates candidate routes upon location updates.
  4. *RouteSelectionButton Dimmed State Contract Tests (`RouteSelectionButtonTest.kt`)*:
  • Verify `isDimmed = true` applies reduced alpha (`alpha = 0.45f`).
  • Verify `isDimmed = false` applies full alpha (`1.0f`).
  • Verify `TrackingTabsScreen.kt` passes `isDimmed = activeRoute == null && routes.isEmpty()`.
  5. *9-Language Localization Parity Audit (`TranslationParityTest.kt`)*:
  • Verify `tuning_cat_navigation`, `tuning_route_selection_radius_title`, `tuning_route_selection_radius_desc` across EN, DE, ES, FR, IT, JA, NL, PL, PT.
  6. *Full Clean-Room Regression Suite*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% pass rate.

### TST-UI-242: **Mid-Ride Route Selector Return Navigation Entry Point & Take Me Home Design Consistency Verification** [Mid-ride Take Me Home card presented in route selector during active sessions; ReturnNavigationHud styled with 12.dp shape, subtle green accents, and no raw emojis; 100% 9-language localization parity; 100% test pass rate.]
- **Requirement(s)**: `REQ-UI-282`
- **Component**: **Mid-Ride Route Selector Return Navigation Entry Point & Take Me Home Design Consistency Verification**
- **Jira**: ``ATT-2462``
- **Test Description**:
  1. *RouteSelectorSheet Mid-Ride Conditional Contract (`RouteSelectorMidRideContractTest.kt`)*:
  • Verify `RouteSelectorSheet.kt` exposes parameters `isMidRide: Boolean` and `onTakeMeHome: (() -> Unit)?`.
  • Verify `RouteSelectorContent` contains `MidRideHeimwegCard` conditional on `isMidRide && onTakeMeHome != null`.
  • Verify `MidRideHeimwegCard` uses `RoundedCornerShape(12.dp)`, `TTColor.RouteSelected`, and `Icons.Default.Home`.
  • Verify `TrackingTabsScreen.kt` passes `isMidRide` and `onTakeMeHome` to `RouteSelectorModalBottomSheet`.
  2. *ReturnNavigationHud Design System Contract (`ReturnNavigationHudContractTest.kt`)*:
  • Verify `ReturnNavigationHud.kt` does NOT contain emoji `🏠`.
  • Verify `ReturnNavigationHud.kt` uses `RoundedCornerShape(12.dp)`.
  • Verify `ReturnNavigationHud.kt` incorporates `TTColor.RouteSelected` or green domain accent.
  • Verify `ReturnNavigationHud.kt` uses `Icons.Default.Home`.
  3. *9-Language Localization Parity Audit (`TranslationParityTest.kt`)*:
  • Verify `nav_take_me_home`, `nav_home_destination_desc`, `nav_stop_return` across EN, DE, ES, FR, IT, JA, NL, PL, PT.
  4. *Full Clean-Room Regression Suite*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate.

### TST-UI-243: **Equipment Sensor Matrix and Configuration Dialog: Sport-Specific Sensor Precedence Ordering Verification** [Equipment sensor matrix columns and configuration dialog sensor lists ordered with sport-specific sensors first, followed by shared/generic sensors, with standardized case-insensitive alphabetical tie-breaking; 100% test pass rate.]
- **Requirement(s)**: `REQ-UI-283`
- **Component**: **Equipment Sensor Matrix and Configuration Dialog: Sport-Specific Sensor Precedence Ordering Verification**
- **Jira**: ``ATT-2464``
- **Test Description**:
  1. *Pure Unit Ordering Verification (`EquipmentSensorOrderingTest.kt`)*:
  • Verify bike sensor precedence: `BIKE_POWER` < `BIKE_SPEED_AND_CADENCE` < `BIKE_SPEED` < `BIKE_CADENCE` < `HRM` < `ENVIRONMENT`.
  • Verify shoe sensor precedence: `RUN_SPEED` < `HRM` < `ENVIRONMENT`.
  • Verify secondary case-insensitive alphabetical sorting on name.
  • Verify tertiary ascending tie-breaking on ID.
  • Verify defensive null handling for sensors or deviceTypes.
  2. *ViewModel Reactive Ordering Tests (`EquipmentViewModelMatrixTest.kt`)*:
  • Verify `_bikeSensors` and `bikeSensorsList` emit sport-specific sensors first, then shared sensors.
  • Verify `_shoeSensors` and `runSensorsList` emit run-specific sensors first, then shared sensors.
  3. *Database Manager Sport Query Ordering (`DevicesDatabaseManagerOrderingTest.kt`)*:
  • Verify `getSensorsForSportType(BSportType.BIKE)` and `getSensorsForSportType(BSportType.RUN)` return prioritized sensor lists.
  4. *Contract & UI Consistency Verification (`EquipmentSensorMatrixContractTest.kt`)*:
  • Verify `EditEquipmentDialog.kt` and `EquipmentSensorMatrixScreen.kt` apply `EquipmentSensorOrdering`.
  5. *Full Clean-Room Regression Suite*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with 0 regressions.

### TST-UI-244: **Sensors View Sport-Specific Equipment Matrix Tabs, Visual Link Indicators & Equipment Tabs Consolidation Verification** [Relocate equipment sensor matrices into Sensors view as two dedicated tabs at the very right with leading link indicators on matrix tabs, host isolated sport-specific matrix tables for Bikes and Shoes, streamline EquipmentTabsScreen to 2 tabs, and enforce 9-language localization parity; 100% test pass rate.]
- **Requirement(s)**: `REQ-UI-284`
- **Component**: **Sensors View Sport-Specific Equipment Matrix Tabs, Visual Link Indicators & Equipment Tabs Consolidation Verification**
- **Jira**: ``ATT-2465` / `ATT-3314``
- **Test Description**:
  1. *Sensors View 5-Tab Architecture & Link Indicators (`DevicesTabbedScreenContractTest.kt`)*:
  • Verify `DevicesTabbedScreen.kt` defines 5 tabs (`devices_tab_available`, `devices_tab_paired`, `devices_tab_known`, `devices_tab_bikes`, `devices_tab_shoes`).
  • Verify `PrimaryScrollableTabRow` is used.
  • Verify Tab 3 renders Bikes matrix and Tab 4 renders Shoes matrix.
  • Verify Tab 3 and Tab 4 render `Icons.Default.Link` with 16.dp size and 6.dp spacing in `text` slot, while Tabs 0–2 remain text-only.
  • Verify `DevicesTabbedScreen` accepts/resolves `EquipmentViewModel`.
  2. *EquipmentTabsScreen 2-Tab Contract (`EquipmentSensorMatrixContractTest.kt`)*:
  • Verify `EquipmentTabsScreen.kt` defines exactly 2 tabs (`equipment_type_bike`, `equipment_type_shoe`).
  • Verify `equipment_tab_sensor_matrix` is NOT referenced in `EquipmentTabsScreen`.
  • Verify FAB is not suppressed by a 3rd tab check.
  3. *Single-Sport Matrix Verification (`EquipmentSportSensorMatrixTest.kt`)*:
  • Verify single-sport matrix displays sticky column (`184.dp`) and independent horizontal scroll state without redundant section header.
  • Verify empty states when equipment or sensors are empty.
  4. *9-Language Localization Audit (`TranslationParityTest.kt`)*:
  • Verify `devices_tab_bikes` and `devices_tab_shoes` across all 9 locales (EN, DE, ES, FR, IT, JA, NL, PL, PT).
  5. *Full Clean-Room Regression Suite*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with 0 regressions.

### TST-UI-245: **Workout Section 9-Item Default Sequence and Visibility Matrix Verification** [Integrate EXPORT_STATUS into WorkoutSectionType, define 9-section DEFAULT_ORDER, and unify clean-installation default visibility matrices across workout list cards and detail views; 100% test pass rate.]
- **Requirement(s)**: `REQ-UI-285`
- **Component**: **Workout Section 9-Item Default Sequence and Visibility Matrix Verification**
- **Jira**: ``ATT-2384``
- **Test Description**:
  1. *Domain Model & Ordering Verification (`WorkoutSectionOrderPersistenceTest.kt`)*:
  • Verify `DEFAULT_ORDER.size == 9` and matches the exact sequence: `DESCRIPTION`, `EXTREMA`, `MAP`, `ELEVATION`, `CHARTS`, `ZONES`, `LAPS`, `EXPORT_STATUS`, `STRAVA`.
  • Verify `fromSerializedString` with legacy 8-section string safely appends `EXPORT_STATUS` at the end.
  • Verify roundtrip serialization of all 9 sections.
  2. *Default Visibility Matrix Tests (`WorkoutPreferencesDefaultMatrixTest.kt`)*:
  • Verify `WorkoutCardSectionPreferences` defaults: `showElevationProfile == false`, `showTelemetryCharts == false`, `showLaps == false`; `showDescription == true`, `showExtrema == true`, `showMapPreview == true`, `showZoneAnalysis == true`, `showExportStatus == true`, `showStrava == true`.
  • Verify `WorkoutDetailPreferences` defaults: all 9 section toggles are `true`.
  3. *UI Presentation & Settings Contract (`WorkoutSectionReorderContractTest.kt`)*:
  • Verify `WorkoutMasksAndCardsSection.kt` maps `WorkoutSectionType.EXPORT_STATUS` to `MatrixFeatureRow` with `R.string.export_status`.
  • Verify `WorkoutSummary.kt` handles `WorkoutSectionType.EXPORT_STATUS` in the dynamic order loop.
  • Verify `TrackOnMapScreen.kt` handles `WorkoutSectionType.EXPORT_STATUS` in `analyticsContent`.
  4. *Full Clean-Room Regression Suite*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with 0 regressions.

### TST-UI-246: **ResearchButton Jetpack Compose Vector Resilience & Universal Resource Fallback Verification** [`ResearchButton` renders with complete immunity to `Resources$NotFoundException` across all states; `R.drawable.research_icon` resolves reliably; 9-language translation parity verified; 100% test pass rate.]
- **Requirement(s)**: `REQ-UI-286`
- **Component**: **ResearchButton Jetpack Compose Vector Resilience & Universal Resource Fallback Verification**
- **Jira**: ``ATT-2619``
- **Test Description**:
  1. *Unit Test `ResearchButton` Composable Rendering (`ResearchButtonTest.kt`)*:
  • Verify `ResearchButton` renders with `isEnabled = true` using `Icons.Default.Refresh` without throwing `Resources$NotFoundException`.
  • Verify `ResearchButton` renders with `isEnabled = false` using `Icons.Default.Refresh` without throwing `Resources$NotFoundException`.
  • Verify accessibility `contentDescription` is populated with `@string/research`.
  2. *Unit Test `research_icon` Vector Drawable Resolution (`ResearchIconDrawableTest.kt`)*:
  • Verify `ContextCompat.getDrawable(context, R.drawable.research_icon)` resolves non-null without throwing `Resources$NotFoundException`.
  3. *9-Language Localization Audit*:
  • Verify string `@string/research` exists across all 9 application locales (EN, DE, ES, FR, IT, JA, NL, PL, PT) with zero untranslated fallbacks.
  4. *Clean-Room Regression Suite Execution*:
  • Execute `./gradlew testDebugUnitTest` and verify 0 regressions across all modules.

### TST-UI-247: **Navigation Cue Auto-Dismiss Duration, Countdown Distance & Solid Opaque Overlays Verification** [Navigation cue overlays render 100% opaque, dismiss duration rendered as discrete integer slider from 1s to 10s with steps = 8 and legacy 0 fallback to 4s, countdown distance persisted in DataStore; 100% 9-language parity; 100% test pass rate.]
- **Requirement(s)**: `REQ-UI-287`
- **Component**: **Navigation Cue Auto-Dismiss Duration, Countdown Distance & Solid Opaque Overlays Verification**
- **Jira**: ``ATT-2632`, `ATT-3321`, `ATT-3322`, `ATT-3325`, `ATT-3450``
- **Test Description**:
  1. *Unit Test DataStore Persistence & Reset (`TuningPreferencesDataStoreDismissDurationTest.kt`, `TuningConfigTest.kt`)*:
  • Verify defaults: `navigationCueTransparency == 1.0f`, `navigationCueDismissDurationSec == 4`, `turnCueCountdownDistanceMeters == 100.0f` (ATT-3450).
  • Verify bounds & options: `MIN = 1`, `MAX = 10`, `STEP = 1`, `NAVIGATION_CUE_DISMISS_OPTIONS == (1..10).toList()` (ATT-3450).
  • Verify persistence: arbitrary values in `1..10` persisted; legacy `0` falls back to `4`; negative values fall back to `4`; values `> 10` clamped to `10`.
  • Verify clamping: `updateTurnCueCountdownDistanceMeters(10.0f)` clamps to 30.0f, `updateTurnCueCountdownDistanceMeters(500.0f)` clamps to 300.0f.
  • Verify reset: `resetToDefaults()` reverts to `1.0f`, `4`, and `100.0f`.
  2. *Unit & Layout Tests for Navigation Cue Overlays & Settings UI (`TurnPromptBannerTest.kt`, `AdvancedTuningVisualContractTest.kt`, `AdvancedTuningAccordionTest.kt`)*:
  • Verify `TurnPromptBanner`, `ReturnNavigationHud`, and `ForkDecisionCard` render with solid 100% opacity without transparency modifier.
  • Verify `NavigationSection.kt` renders `TuningSliderItem` for dismiss duration (1s..10s, steps = 8, 1s increments, default 4s) with zero `FilterChip` items (ATT-3450, `[TST-UI-247.5]`).
  • Verify `TuningSubtitleFormatter.formatNavigationSubtitle` formats `"${sec}s, ${radius} km"` (e.g. `"4s, 1.0 km"` or `"10s, 1.0 km"`).
  • Verify dynamic countdown threshold in `TurnByTurnNavigationEngine`: approaching state triggers at configured `turnCueCountdownDistanceMeters` (e.g. 200m vs 50m).
  3. *9-Language Localization Audit (`TranslationParityTest.kt`)*:
  • Verify absence of obsolete `tuning_nav_cue_transparency_title`, `tuning_nav_cue_transparency_desc`, and `tuning_nav_cue_dismiss_persistent` across all 9 locales (EN, DE, ES, FR, IT, JA, NL, PL, PT).
  4. *Clean-Room Regression Suite Execution*:
  • Execute `./gradlew testDebugUnitTest` and verify 0 regressions across all modules.

### TST-UI-248: **Equipment Sensor Matrix Bike and Shoe Icon Tint Unification Verification** [Unify active equipment icon tinting across bikes and shoes to MaterialTheme.colorScheme.primary in MatrixEquipmentRow while preserving MaterialTheme.colorScheme.outline for retired items; 100% test pass rate.]
- **Requirement(s)**: `REQ-UI-288`
- **Component**: **Equipment Sensor Matrix Bike and Shoe Icon Tint Unification Verification**
- **Jira**: ``ATT-2630``
- **Test Description**:
  1. *Unit & Contract Tint Verification (`EquipmentSensorMatrixScreenTest.kt`)*:
  • Verify `MatrixEquipmentRow` leading icon tint evaluates to `MaterialTheme.colorScheme.primary` for both bikes and shoes when active.
  • Verify `MatrixEquipmentRow` leading icon tint evaluates to `MaterialTheme.colorScheme.outline` when retired (`item.isRetired == true`).
  • Verify `MaterialTheme.colorScheme.secondary` is NOT used for active equipment icon tinting.
  2. *Single-Sport Matrix Integration Verification (`EquipmentSensorMatrixContractTest.kt`)*:
  • Verify sport tabs integration in `DevicesTabbedScreen` remains unbroken.
  • Verify 2-tab structure in `EquipmentTabsScreen` remains unbroken.
  3. *9-Language Localization Parity Audit (`TranslationParityTest.kt`)*:
  • Verify string resources `Equipment`, `equipment_type_bike`, `equipment_type_shoe`, `devices_tab_bikes`, `devices_tab_shoes` across all 9 locales (EN, DE, ES, FR, IT, JA, NL, PL, PT).
  4. *Full Clean-Room Regression Suite*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with 0 regressions.

### TST-UI-249: **Context-Aware Route Selection: Pre-Tracking Start Proximity and In-Ride Corridor Auto-Detection Verification** [Route candidate selection and UI feedback dynamically adapt to workout tracking state; in-ride corridor matching directly feeds route list; dimmed button displays context-aware hints; 100% 9-language translation parity; 100% test pass rate.]
- **Requirement(s)**: `REQ-UI-289`
- **Component**: **Context-Aware Route Selection: Pre-Tracking Start Proximity and In-Ride Corridor Auto-Detection Verification**
- **Jira**: ``ATT-2627``
- **Test Description**:
  1. *Lifecycle State Switching Unit Tests (`RouteSelectorViewModelTest.kt`)*:
  • Verify when `isTrackingActive == false`, candidate routes are evaluated via `RouteProximityRanker.filterAndRankRoutes` (start point proximity within radius).
  • Verify when `isTrackingActive == true`, candidate routes are evaluated via `RouteAutoDetector.evaluateMatchingRoutes` (corridor segment matching).
  • Verify context empty hint evaluates to `R.string.route_no_routes_nearby` when pre-tracking and `R.string.route_no_matching_route_detected` when in-ride.
  2. *Multi-Candidate Route Corridor Evaluation Unit Tests (`RouteAutoDetectorTest.kt`)*:
  • Verify `evaluateMatchingRoutes` returns all routes matching current position and heading along path segments.
  • Verify active navigated route and cooldown dismissed routes are excluded.
  • Verify candidate sorting order by proximity.
  3. *UI & Presentation Contract Tests (`RouteSelectorSheetTest.kt`, `RouteSelectionButtonContractTest.kt`)*:
  • Verify `AutoDetectedRouteBanner` is excised from `RouteSelectorSheet.kt`.
  • Verify `RouteSelectionButton` displays context-aware subtitle when empty and dimmed (`alpha = 0.45f`).
  • Verify `RouteSelectorSheet` renders `MidRideHeimwegCard` when `isMidRide == true`.
  4. *9-Language Localization Audit (`TranslationParityTest.kt`)*:
  • Verify string resources `route_no_routes_nearby` and `route_no_matching_route_detected` across EN, DE, ES, FR, IT, JA, NL, PL, PT.
  5. *Full Clean-Room Regression Suite*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with 0 regressions.

### TST-UI-250: **Sensor Pairing Device Type Selection: Omission of 'Alle' Action and Targeted Hardware Profile Discovery Verification** [Omit "Alle" button from DeviceTypeSelectionDialog during sensor pairing flows, enforcing targeted wireless discovery for specific hardware sensor profiles; 100% unit test pass rate.]
- **Requirement(s)**: `REQ-UI-290`
- **Component**: **Sensor Pairing Device Type Selection: Omission of 'Alle' Action and Targeted Hardware Profile Discovery Verification**
- **Jira**: ``ATT-2625``
- **Test Description**:
  1. *Dialog Parameter and Actions Slot Unit & Contract Tests (`DeviceTypeSelectionDialogTest.kt`)*:
  • Verify `DeviceTypeSelectionDialog` defaults parameter `showAllOption` to `false`.
  • Verify that when `showAllOption == false`, the dialog renders without the "Alle" (`R.string.device_type_all`) action button and the bottom sheet `actions` slot is null.
  • Verify that when `showAllOption == true`, the dialog renders the "Alle" button in the bottom sheet actions slot.
  2. *Hardware Profile Discovery and Selection Unit Tests (`DeviceTypeSelectionDialogTest.kt`)*:
  • Verify dialog items for `ProtocolType.ANT_PLUS` display all ANT+ remote device types (`DeviceType.getRemoteDeviceTypes(ANT_PLUS)`).
  • Verify dialog items for `ProtocolType.BLE` display all BLE remote device types (`DeviceType.getRemoteDeviceTypes(BLE)`).
  • Verify tapping a device profile invokes `onSelected(type)` with the concrete `DeviceType`.
  • Verify dismissing the dialog invokes `onDismiss()`.
  3. *Integration Sites Verification (`DevicesTabbedScreenTest.kt`, `ControlTrackingScreenTest.kt`)*:
  • Verify `DevicesTabbedScreen` invokes `DeviceTypeSelectionDialog` during pairing FAB flow with `showAllOption = false` (or defaulted).
  • Verify `ControlTrackingScreen` invokes `DeviceTypeSelectionDialog` during pairing button flow with `showAllOption = false` (or defaulted).
  4. *Full Clean-Room Regression Suite*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with 0 regressions.

### TST-UI-251: **Sensors View Multi-Sensor Inventory Preservation During Pairing Verification** [Preserve complete multi-sensor inventory across all Sensors screen tabs during pairing discovery, decoupling hardware scanning target from UI tab filters; 100% unit test pass rate.]
- **Requirement(s)**: `REQ-UI-291`
- **Component**: **Sensors View Multi-Sensor Inventory Preservation During Pairing Verification**
- **Jira**: ``ATT-2626``
- **Test Description**:
  1. *ViewModel Decoupling & State Unit Tests (`DevicesTabbedViewModelTest.kt`)*:
  • Verify `startPairingScan(protocol, deviceType)` dispatches `startSearchingForNewDevices` with targeted protocol and device type.
  • Verify `startPairingScan` preserves `_uiState` as `UiState.DisplayingTabs(DeviceType.ALL)` and `protocol` as `Protocol.ALL`.
  • Verify `scanningProtocol` and `scanningDeviceType` emit targeted pairing parameters.
  • Verify `stopSearching()` resets `scanningProtocol` and `scanningDeviceType` to null.
  2. *Screen Tab Filter Spec Contract Verification (`DevicesTabbedScreenContractTest.kt`)*:
  • Verify `DevicesTabbedScreen` configures tabs with unrestrictive filter specs during pairing.
  • Verify `SearchingHeader` in `DeviceListScreen` receives active scanning target.
  3. *9-Language Localization Parity Audit (`TranslationParityTest.kt`)*:
  • Verify `devices_searchingForDevice` format string has matching 2 format specifiers (`%1$s`, `%2$s`) across all 9 supported locales (EN, DE, ES, FR, IT, JA, NL, PL, PT).
  4. *Full Clean-Room Regression Suite*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with 0 regressions.

### TST-UI-253: **Format-Specific Import Tab Architecture and Multi-Document Recovery Verification** [Import tab partitioned into FIT, TCX, and GPX cards with complete action triad parity and multi-document local picking; format-scoped cloud recovery; 100% 9-language localization parity; 100% unit test pass rate.]
- **Requirement(s)**: `REQ-UI-293`
- **Component**: **Format-Specific Import Tab Architecture and Multi-Document Recovery Verification**
- **Jira**: ``ATT-2623` / `ATT-3315``
- **Test Description**:
  1. *9-Language Localization Parity Tests (`ImportFormatLocalizationTest.kt`)*:
  • Verify all format titles, descriptions, button labels, and diagnostic messages exist, are non-empty, and contain no malformed format tokens across all 9 supported locales: `values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`.
  2. *ViewModel Format Scoping Unit Tests (`BackupRestoreViewModelFormatScopingTest.kt`)*:
  • Verify `bulkRecoverLegacyData` passes `"fit"`, `"tcx"`, and `"gpx"` correctly to `LegacyImportEngine.bulkRecoverFromDropbox`.
  • Verify `bulkRecoverGoogleDriveData` passes `"fit"`, `"tcx"`, and `"gpx"` correctly to `LegacyImportEngine.bulkRecoverFromGoogleDrive`.
  • Verify `importWorkoutFiles` processes multiple URIs sequentially with progress callbacks, duplicate skipping, error recording, and cluster reconciliation.
  3. *UI Contract & Architecture Tests (`ImportFormatCardsContractTest.kt`)*:
  • Verify `ImportTabContent` renders three distinct format blocks (FIT, TCX, GPX).
  • Verify each block contains the uniform action triad (Local Multi-Doc, Dropbox, Google Drive).
  • Verify disconnected cloud styling and dialog dispatch.
  4. *Full Clean-Room Regression Suite*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with 0 regressions.

### TST-UI-254: **Workout File Picker Selectability and Format-Extension Validation Verification** [System document pickers ensure workout file selectability by including application/octet-stream; defensive extension validation prevents format mismatches; 100% 9-language localization parity; 100% unit test pass rate.]
- **Requirement(s)**: `REQ-UI-294`
- **Component**: **Workout File Picker Selectability and Format-Extension Validation Verification**
- **Jira**: ``ATT-2622`, `ATT-2740``
- **Test Description**:
  1. *SAF MIME Launcher Contract Tests (`ImportFormatFilePickerContractTest.kt`)*:
  • Verify `onLocalImportClick("fit")` launches `pickFitFilesLauncher` with `FIT_MIME_TYPES` (`application/vnd.ant.fit`, `application/fit`, `application/octet-stream`).
  • Verify `onLocalImportClick("tcx")` launches `pickLegacyFileLauncher` with `TCX_MIME_TYPES` (`application/vnd.garmin.tcx+xml`, `application/xml`, `text/xml`, `application/octet-stream`).
  • Verify `onLocalImportClick("gpx")` launches `pickLegacyFileLauncher` with `GPX_MIME_TYPES` (`application/gpx+xml`, `application/xml`, `text/xml`, `application/octet-stream`).
  2. *Defensive Extension Validation Unit Tests (`ImportFormatExtensionValidatorTest.kt`)*:
  • Verify `.fit` files pass for FIT and fail for TCX/GPX.
  • Verify `.tcx` files pass for TCX and fail for FIT/GPX.
  • Verify `.gpx` files pass for GPX and fail for FIT/TCX.
  • Verify unsupported extensions (`.pdf`, `.png`, `.txt`) fail.
  • Verify files without extension pass through to content parser.
  3. *9-Language Localization Parity Audit (`ImportFormatFilePickerLocalizationTest.kt`)*:
  • Verify `invalid_workout_file_format` exists, is non-empty, and has exactly 1 string specifier `%1$s` across `values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`.
  4. *Full Clean-Room Regression Suite*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with 0 regressions.

### TST-UI-255: **Unified Scrollable Container Architecture, Full-Width Spatial Toggles Layout, and Viewport Slotting Verification** [Unified scrollable configuration container eliminates viewport clipping; full-width single-column toggle layout eliminates text truncation across all locales; floating LapButton suppressed in edit mode; 100% unit test pass rate.]
- **Requirement(s)**: `REQ-UI-295`
- **Component**: **Unified Scrollable Container Architecture, Full-Width Spatial Toggles Layout, and Viewport Slotting Verification**
- **Jira**: ``ATT-2620`, `ATT-2744``
- **Test Description**:
  1. *SensorGridScreen Container Architecture Contract Tests (`TrackingTabWysiwygContractTest.kt`)*:
  • Verify `SensorGridScreen.kt` implements a unified `verticalScroll(rememberScrollState())` container for `ScreenMode.CONFIGURATION`.
  • Verify the sensor grid `Column` does not declare nested `verticalScroll()` when in `CONFIGURATION` mode.
  • Verify `ATrainingTrackerMap` and `ElevationProfile` are conditionally guarded by `screenMode != ScreenMode.CONFIGURATION`.
  • Verify `BottomSheetScaffold` sets `sheetPeekHeight = 0.dp`, disables swipe, and sets empty `sheetContent` in `CONFIGURATION` mode.
  • Verify bottom container includes `navigationBarsPadding()` to prevent occlusion by the system navigation bar.
  2. *Full-Width Spatial Toggles & Elimination of 2-Column Rows / Grey Dock (`TrackingTabWysiwygContractTest.kt`)*:
  • Verify `SensorGridScreen.kt` declares all 5 lower toggles (Map, Elevation, Live Segments, Live Climbs, Lap Button) as full-width cards within a unified `Column`.
  • Verify zero 2-column side-by-side `Row` constructs for spatial toggles in `CONFIGURATION` mode.
  • Verify absence of arbitrary nested grey dock `Surface` with tonal elevation 2 dp.
  • Verify text and status badge pill layout guarantees full label readability without ellipsis truncation.
  3. *TrackingTabsScreen Floating Lap Button Isolation Contract Test (`TrackingTabWysiwygContractTest.kt`)*:
  • Verify `TrackingTabsScreen.kt` suppresses floating `LapButton` when `screenMode == ScreenMode.CONFIGURATION`.
  4. *Spatial Toggle Interaction & Runtime Gating Contract Tests (`TrackingTabWysiwygContractTest.kt`)*:
  • Verify all 6 spatial toggle cards remain declared and bound to their respective actions and runtime gates.
  5. *Full Clean-Room Regression Suite*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with 0 regressions.

### TST-UI-256: **Contextual ANT+ Service Guidance and Startup Dialog Elimination Verification** [Cold-start ANT+ dialog completely eliminated; contextual Compose card and bottom sheet guide missing services; 100% 9-language localization parity; 100% test pass rate.]
- **Requirement(s)**: `REQ-UI-296`
- **Component**: **Contextual ANT+ Service Guidance and Startup Dialog Elimination Verification**
- **Jira**: ``ATT-2513``
- **Test Description**:
  1. *Cold-Start Elimination Contract Tests (`AntStartupCheckContractTest.kt`)*:
  • Verify `MainActivityWithNavigation.kt` does not call `checkANTInstallation()` or `showInstallANTShitDialog()` in `onCreate()`.
  • Verify no dialogs are displayed on fresh launch.
  2. *Contextual Guidance UI & Sheet Contract Tests (`AntServicesStatusContractTest.kt`)*:
  • Verify `AntServicesStatusCard` renders in `DevicesTabbedScreen` when `protocol == Protocol.ANT_PLUS` and `BANALService.areAllANTServicesInstalled` is false.
  • Verify `AntServicesStatusSheet` renders live status for ANT Plugin Service, ANT Radio Service, and ANT USB Service.
  • Verify Google Play intent URLs use `market://details?id=...` with fallback.
  • Verify `PairingProtocolBottomSheet` presents ANT guidance when ANT+ is selected and services are missing.
  3. *9-Language Localization Parity Audit (`AntStatusLocalizationTest.kt`)*:
  • Verify all new ANT status string resources exist, are non-empty, and contain zero formatting errors across `values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`.
  4. *Full Clean-Room Regression Suite*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with 0 regressions.

### TST-UI-257: **Distance-Weighted Elevation Profile Gaussian Smoothing & Continuous Slope Grade Verification** [Distance-weighted Gaussian kernel smoothing with default sigma 21m, dynamic cutoff, DataStore preference clamping, and dynamic profile reactivity; 100% test pass rate.]
- **Requirement(s)**: `REQ-UI-297`
- **Component**: **Distance-Weighted Elevation Profile Gaussian Smoothing & Continuous Slope Grade Verification**
- **Jira**: ``ATT-2512`, `ATT-2746``
- **Test Description**:
  1. *Pure Kernel Mathematical Unit Tests (`ElevationSmoothingMathTest.kt`)*:
  • Verify default spatial bandwidth $\sigma = 21.0\,\text{m}$ and default cutoff $3\sigma = 63.0\,\text{m}$.
  • Verify dynamic cutoff scaling ($3.0 * \sigma$) when custom $\sigma$ is supplied.
  • Verify distance-weighted Gaussian smoothing on synthetic quantized step data (0.2m–0.4m steps over 20m plateaus) eliminates flat plateaus and yields smooth, monotonic slope transitions.
  • Verify exact boundary weight renormalization: start boundary ($d=0$) and end boundary ($d=D_{total}$) altitudes match terrain endpoints within 0.1% without artificial sag or peak distortion.
  • Verify true geographic features (crests, saddles, summits) are preserved within $\pm 0.5\text{ m}$ of true elevation.
  • Verify degenerate input handling: empty list, single point, identical coordinates, non-positive distances return cleanly without NaN or exceptions.
  2. *Complexity & Performance Benchmark Unit Test (`ElevationSmoothingMathTest.kt`)*:
  • Verify that smoothing 10,000 dense points ($N = 10,000$) executes in $< 15\text{ ms}$ on JVM.
  3. *DataStore Clamping & Preference Persistence Unit Tests (`TuningPreferencesDataStoreTest.kt`)*:
  • Verify default value of `elevationSmoothingSigmaMeters` is `21.0f`.
  • Verify safety clamping: values $< 10.0\text{f}$ clamp to `10.0f`, values $> 50.0\text{f}$ clamp to `50.0f`.
  • Verify `updateElevationSmoothingSigmaMeters` persists to DataStore and emits via `tuningConfigFlow`.
  • Verify `resetToDefaults()` restores `21.0f`.
  4. *UI Contract & Dynamic Reactivity Tests (`ElevationProfileSmoothingContractTest.kt`, `AftermathAnalysisSectionContractTest.kt`)*:
  • Verify `ElevationProfile.kt` accepts `smoothingSigma` parameter and delegates to `ElevationSmoothingMath.smoothAltitudes(..., sigma = ...)`.
  • Verify `ElevationProfile.kt` reactively observes `tuningConfigFlow` from `TuningPreferencesDataStore`.
  • Verify `AftermathAnalysisSection.kt` renders `TuningSliderItem` with range 10..50 and 40 steps.
  • Verify `AdvancedTuningDialog.kt` binds `elevationSmoothingSigmaMeters` to state, save action, and factory reset.
  5. *9-Language Localization Parity Audit (`AftermathAnalysisLocalizationTest.kt`)*:
  • Verify `tuning_elevation_smoothing_sigma_title` and `tuning_elevation_smoothing_sigma_desc` exist and are non-empty across all 9 locales.
  6. *Full Clean-Room Regression Suite*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with 0 regressions.

### TST-UI-258: **Route Map Climb Polyline Highlighting by Climb Category Classification Verification** [Route map climb span polyline highlighting color-coded by climb category classification; unclassified/flat sections retain standard route coloring; 100% test pass rate.]
- **Requirement(s)**: `REQ-UI-298`
- **Component**: **Route Map Climb Polyline Highlighting by Climb Category Classification Verification**
- **Jira**: ``ATT-2509``
- **Test Description**:
  1. *MapContentScope Climb Highlight Contract Tests (`ClimbPolylineContractTest.kt`)*:
  • Verify `MapContentScope.climbs()` creates polyline highlights for all classified climbs (`CAT_4`, `CAT_3`, `CAT_2`, `CAT_1`, `HC`).
  • Verify `ClimbCategory.UNCATEGORIZED` climbs are omitted from colored highlights, retaining standard route polyline coloring.
  • Verify climb polyline coordinates match `climb.pathPoints.map { it.latLng }` with fallback to `listOf(startLatLng, endLatLng)`.
  • Verify polyline colors match `getClimbCategoryColors(climb.category).first`.
  • Verify polyline `zIndex` is 25f (between route polyline and markers).
  2. *RouteOnMapScreen Architectural Integration Contract Tests (`RouteOnMapScreenClimbContractTest.kt`)*:
  • Verify `RouteOnMapScreen.kt` invokes `climbs(climbs)` within `mapContent`.
  3. *Full Clean-Room Regression Suite*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with 0 regressions.

### TST-UI-259: **Elevation Profile Slope Gradient Preservation and X-Axis Climb Span Highlight Verification** [Elevation profile curve strictly preserves physical slope grade coloration; horizontal X-axis displays categorized climb span bars; 100% unit test pass rate.]
- **Requirement(s)**: `REQ-UI-299`
- **Component**: **Elevation Profile Slope Gradient Preservation and X-Axis Climb Span Highlight Verification**
- **Jira**: ``ATT-2510``
- **Test Description**:
  1. *Elevation Profile Climb Span Contract Tests (`ElevationProfileClimbSpanContractTest.kt`)*:
  • Verify `ElevationProfile.kt` does NOT render accented ridge strokes over the elevation curve (no `drawLine` over `(px1, py1)` to `(px2, py2)` with climb category color).
  • Verify `ElevationProfile.kt` renders horizontal X-axis span bars for recognized cycling climbs along baseline (`y = height`).
  • Verify start and end distance bounds match `climb.pathPoints.first().distance` and `climb.pathPoints.last().distance` mapped via `distanceToCanvasX`.
  • Verify climb span bar uses `getClimbCategoryColors(climb.category).first` with `strokeWidth = 4.dp.toPx()` and `StrokeCap.Round`.
  • Verify `ClimbCategory.UNCATEGORIZED` climbs are excluded from X-axis span highlights.
  • Verify summit badges/pins remain intact above peak coordinates.
  2. *Full Clean-Room Regression Suite*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with 0 regressions.

### TST-UI-260: **Dedicated Route Climb Detail View with Focused Map and Zoomed Elevation Profile Verification** [Dedicated climb detail sheet with focused map, isolated elevation profile, and metrics HUD on climb item tap; 100% 9-language localization parity; 100% unit test pass rate.]
- **Requirement(s)**: `REQ-UI-300`
- **Component**: **Dedicated Route Climb Detail View with Focused Map and Zoomed Elevation Profile Verification**
- **Jira**: ``ATT-2511``
- **Test Description**:
  1. *Climb Breakdown Item Click & Callback Contract Tests (`RouteClimbsBreakdownContractTest.kt`)*:
  • Verify `RouteClimbsBreakdownSection` provides `onClimbClick: ((Climb) -> Unit)?`.
  • Verify tapping a climb card dispatches `onClimbClick` with the corresponding `Climb` instance.
  • Verify `RouteOnMapScreen.kt` declares `selectedClimbForDetail` state and renders `ClimbDetailSheet` when non-null.
  2. *ClimbDetailSheet Structural & Viewport Contract Tests (`ClimbDetailSheetContractTest.kt`)*:
  • Verify `ClimbDetailSheet` contains header with climb title and `ClimbCategoryChip`.
  • Verify metrics row displays distance, elevation gain, avg grade, max grade, and start offset formatted via `LocalMetricFormatter`.
  • Verify `ATrainingTrackerMap` renders within sheet using `MapZoomFocus.EXPLICIT_BOUNDS` mapped from `climb.pathPoints`.
  • Verify isolated elevation profile canvas renders strictly between 0 and climb distance with slope gradient coloration.
  • Verify dismissal callback resets state.
  3. *9-Language Localization Parity Audit (`ClimbDetailLocalizationTest.kt`)*:
  • Verify all climb detail string resources (`routes_climb_max_grade`, etc.) exist and are non-empty across `values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`.
  4. *Full Clean-Room Regression Suite*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with 0 regressions.

### TST-UI-261: **Control Tracking Screen Sensor Header Layout Isolation & Collision Elimination Verification** [Header layout in ControlTrackingScreen restructured into non-overlapping containers; ResearchButton and RemoteDevices arranged in structured Row; 100% unit test pass rate.]
- **Requirement(s)**: `REQ-UI-301`
- **Component**: **Control Tracking Screen Sensor Header Layout Isolation & Collision Elimination Verification**
- **Jira**: ``ATT-2479``
- **Test Description**:
  1. *ControlTrackingScreen Header Architecture Contract Tests (`ControlTrackingSensorHeaderContractTest.kt`)*:
  • Verify `ControlTrackingScreen.kt` implements a non-overlapping `Row` containing `ResearchButton` and `RemoteDevices`.
  • Verify `SearchArea` is rendered in its own dedicated slot above the `Row`.
  • Verify `RemoteDevices` receives `Modifier.weight(1f)` when devices are present.
  • Verify `ResearchButton` centers horizontally when `devices.isEmpty()` and `showResearchButton == true`.
  • Verify `ResearchButton` is omitted when `showResearchButton == false`.
  2. *RemoteDevices Component Parameterization & Spacing Contract Tests (`RemoteDevicesContractTest.kt`)*:
  • Verify `RemoteDevices` accepts `modifier: Modifier = Modifier` and forwards it to the root layout.
  • Verify `LazyRow` uses `Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)` ensuring distinct tile separation.
  3. *Full Clean-Room Regression Suite*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with 0 regressions.

### TST-UI-262: **Route Starred Segments Spatial Overlap Matching, Chronological Route Breakdown & Map Synergy Verification** [(D_{end} - D_{start}) - \text{segment.distance}]
- **Requirement(s)**: `REQ-UI-302`
- **Component**: **Route Starred Segments Spatial Overlap Matching, Chronological Route Breakdown & Map Synergy Verification**
- **Jira**: ``ATT-2583``
- **Test Description**:
  1. *Spatial Overlap Matching Unit Tests (`RouteSegmentMatcherTest.kt`)*:
  • Verify sport type filtering: matching sport types (or `UNKNOWN`) pass, conflicting sport types rejected.
  • Verify corridor proximity: segments whose start and end coordinates fall within 25 m corridor are accepted; segments exceeding 25 m tolerance are rejected.
  • Verify directional bearing alignment: forward traversal ($\Delta \theta \le 45^\circ$, dot product $> 0$) passes; reverse direction ($\Delta \theta > 90^\circ$, negative dot product) or crossing perpendicular corridors rejected.
  • Verify progression and length consistency: $D_{end} > D_{start}$ strictly enforced, and route span distance approximates segment distance within tolerance ($

### TST-UI-263: **Route Matched Segment Detail Bottom Sheet, Focused Map, Isolated Elevation Profile & Interaction Parity Verification** [Dedicated matched segment detail bottom sheet with focused map, isolated elevation profile, and metrics HUD on segment card tap; 100% 9-language localization parity; 100% unit test pass rate.]
- **Requirement(s)**: `REQ-UI-303`
- **Component**: **Route Matched Segment Detail Bottom Sheet, Focused Map, Isolated Elevation Profile & Interaction Parity Verification**
- **Jira**: ``ATT-2774``
- **Test Description**:
  1. *Segment Tap Interaction & Bottom Sheet State Contract Test (`RouteSegmentsBreakdownContractTest.kt`)*:
  • Verify `RouteSegmentsBreakdownSection` provides `onSegmentClick: ((MatchedRouteSegment) -> Unit)?`.
  • Verify tapping a segment card dispatches `onSegmentClick` with the corresponding `MatchedRouteSegment` instance.
  • Verify `RouteOnMapScreen.kt` declares `selectedSegmentForDetail` mutable state and renders `SegmentDetailSheet` when non-null.
  2. *SegmentDetailSheet Structural & Viewport Contract Tests (`SegmentDetailSheetContractTest.kt`)*:
  • Verify `SegmentDetailSheet` contains header with segment title, route counter badge, `ClimbCategoryChip` (if categorized), and athlete PR badge.
  • Verify metrics card displays start distance along route (`routes_segment_start_at`), city, and full `SegmentDetails` component (distance, Strava branding, avg/max grade, elevation gain, min/max altitude).
  • Verify `ATrainingTrackerMap` renders within sheet using `MapZoomFocus.EXPLICIT_BOUNDS` mapped from `matchedSegment.segment.path` with start and stop markers.
  • Verify isolated elevation profile renders strictly for the segment path with altitude annotations.
  • Verify dismissal callback resets `selectedSegmentForDetail` to null without disturbing route state.
  3. *9-Language Localization Parity Audit (`RouteSegmentsLocalizationTest.kt`)*:
  • Verify all route segment string resources (`routes_segment_counter`, `routes_segment_start_at`, `routes_segment_pr`, etc.) exist and are non-empty across `values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`.
  4. *Full Clean-Room Regression Suite*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with 0 regressions.

### TST-UI-264: **Control Tracking Screen Remote Device Tile Bounded Width, Multiline Wrapping & Centering Contract Verification** [Remote device tiles bounded to 72 dp maximum width; device names wrap onto 2 centered lines with ellipsis truncation; 100% unit test pass rate.]
- **Requirement(s)**: `REQ-UI-304`
- **Component**: **Control Tracking Screen Remote Device Tile Bounded Width, Multiline Wrapping & Centering Contract Verification**
- **Jira**: ``ATT-2772``
- **Test Description**:
  1. *Bounded Maximum Tile Width Contract Test (`RemoteDevicesContractTest.kt`)*:
  • Verify `RemoteDeviceItem` declares `Modifier.widthIn(max = 72.dp)` on its root `Column` container.
  2. *Multiline Text Wrapping & Ellipsis Contract Test (`RemoteDevicesContractTest.kt`)*:
  • Verify the `Text` composable rendering `device.name` configures `maxLines = 2` and `overflow = TextOverflow.Ellipsis`.
  3. *Symmetrical Text Centering Contract Test (`RemoteDevicesContractTest.kt`)*:
  • Verify the `Text` composable rendering `device.name` configures `textAlign = TextAlign.Center`.
  4. *Full Clean-Room Regression Suite*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with 0 regressions.

### TST-UI-265: **Inverted Route-Segment Association Engine, Segment Details Routes Breakdown & Cross-Navigation Verification** [Inverted route-segment spatial matching engine identifies containing routes; SegmentRoutesSection renders cards with metrics HUD; tapping route navigates to RouteOnMapScreen; 100% 9-language localization parity; 100% unit test pass rate.]
- **Requirement(s)**: `REQ-UI-305`
- **Component**: **Inverted Route-Segment Association Engine, Segment Details Routes Breakdown & Cross-Navigation Verification**
- **Jira**: ``ATT-2585``
- **Test Description**:
  1. *Inverted Spatial Matching Unit Tests (`RouteSegmentMatcherTest.kt`)*:
  • Verify candidate routes traversing the segment in forward direction are correctly matched with valid start/end distance offsets.
  • Verify candidate routes traversing in reverse direction or exceeding corridor tolerance are rejected.
  • Verify empty candidate routes or single-point segment paths return empty list cleanly.
  2. *SegmentRoutesSection Structural & Interaction Contract Tests (`SegmentRoutesSectionContractTest.kt`)*:
  • Verify `SegmentRoutesSection` renders `ElevatedCard` for each matching route displaying sport icon, route name, distance, elevation gain, and start offset.
  • Verify tapping a route card dispatches `onRouteClick` with the route's summary ID.
  • Verify `SegmentOnMapScreen.kt` integrates `SegmentRoutesSection` via `analyticsContent` in `MapDetailLayout`.
  • Verify `StarredSegmentsScreen.kt` renders `RouteOnMapScreen` when a route is clicked, with `BackHandler` returning to the segment.
  3. *9-Language Localization Parity Audit (`SegmentRoutesLocalizationTest.kt`)*:
  • Verify `segment_routes_containing_title` exists, is non-empty, and contains valid format specifiers across all 9 supported locales: `values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`.
  4. *Full Clean-Room Regression Suite*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with 0 regressions.

### TST-UI-266: **Royal Blue Route Color Tokens, Multi-Tier Contrast Hierarchy, and Living Style Guide Synchronization Verification** [Route visualization transitioned to Royal Blue palette; high contrast against UCI climb categories and terrain; living style guide synchronized; 100% test pass rate.]
- **Requirement(s)**: `REQ-UI-306`
- **Component**: **Royal Blue Route Color Tokens, Multi-Tier Contrast Hierarchy, and Living Style Guide Synchronization Verification**
- **Jira**: ``ATT-2761``
- **Test Description**:
  1. *Route Color Tokens Audit (`Color.kt`)*:
  • Assert exact ARGB hex values for `RouteSelected` (`Color(0xFF1565C0)`), `RouteUnselected` (`Color(0xFF90CAF9)`), `RouteActiveNavigation` (`Color(0xFF1E88E5)`), `RouteActiveNavigationOverlay` (`Color(0xFF0D47A1)`).
  2. *Multi-Tier Contrast & Climb Category Differentiation (`RouteColorPaletteContractTest.kt`)*:
  • Verify RGB Euclidean / WCAG contrast between `RouteSelected` and all 6 climb categories (`HC`, `CAT_1`..`CAT_4`, `UC`), asserting Cat 4 green $\Delta C > 0.35$.
  3. *Active Navigation Polyline Hierarchy (`MapRouteActiveNavigationTest.kt`)*:
  • Verify `MapRoute` color resolution, width, and z-index hierarchy with new Royal Blue tokens.
  4. *Living Style Guide Synchronization (`docs/design_guidelines.md`)*:
  • Verify Sections 5.4 and 5.7 codify Royal Blue with zero stale green route references.
  5. *Full Clean-Room Regression Suite*:
  • Execute `./gradlew clean testDebugUnitTest` asserting 100% test pass rate across all test classes.

### TST-UI-267: **Route Map Pin Elimination, Full-Spectrum UC Climb Polyline Highlighting, and Elevation Profile Baseline Parity Verification** [Elimination of climb pin markers from route map; full-spectrum UC climb polyline highlighting in neutral grey; elevation profile X-axis baseline span indicator bars for all climbs including UC; 100% unit test pass rate.]
- **Requirement(s)**: `REQ-UI-307`
- **Component**: **Route Map Pin Elimination, Full-Spectrum UC Climb Polyline Highlighting, and Elevation Profile Baseline Parity Verification**
- **Jira**: ``ATT-2748``
- **Test Description**:
  1. *Route Map Marker Contract Audit (`RouteOnMapScreenContractTest.kt`)*:
  • Verify `allMarkers` contains strictly Start and End markers when route path is non-empty; assert 0 `ic_ascent` markers are supplied to `markers()` on `RouteOnMapScreen`.
  2. *Full-Spectrum Climb Polyline Highlighting Contract Tests (`ClimbPolylineContractTest.kt`)*:
  • Verify all 6 categories (including `UNCATEGORIZED` -> `Color(0xFF757575)`) generate `ClimbHighlightData` at `zIndex = 25f`.
  3. *Elevation Profile Baseline UC Span Parity (`ElevationProfileClimbSpanContractTest.kt`)*:
  • Verify `ElevationProfile.kt` renders horizontal baseline span indicator bars for all recognized climbs, including `ClimbCategory.UNCATEGORIZED`.
  4. *Living Documentation Synchronization*:
  • Verify `docs/requirements.md` contains `REQ-UI-307` and `docs/tests.md` contains `TST-UI-267`.
  5. *Full Clean-Room Regression Suite*:
  • Execute `./gradlew clean testDebugUnitTest` asserting 100% test pass rate across all test classes.

### TST-UI-268: **Route Map Layers Menu, Overlay Layer Visibility, Item-Level Breakdown Toggles, and Permanent Route Line Parity Verification** [Route map Layers menu toggles climbs, segments, and waypoints; primary route polyline permanently anchored; item-level visibility toggles in breakdown sections; 100% 9-language localization parity; 100% unit test pass rate.]
- **Requirement(s)**: `REQ-UI-308`
- **Component**: **Route Map Layers Menu, Overlay Layer Visibility, Item-Level Breakdown Toggles, and Permanent Route Line Parity Verification**
- **Jira**: ``ATT-2763``
- **Test Description**:
  1. *Route Map Layers Menu & Overlay Filtering Contract Tests (`RouteOnMapScreenLayersContractTest.kt`)*:
  • Verify `RouteOverlayLayer` declares `CLIMBS`, `SEGMENTS`, `WAYPOINTS`.
  • Verify default state enables all layers.
  • Verify disabling `CLIMBS` filters out all climb highlight polylines.
  • Verify disabling `SEGMENTS` filters out all matched segment polylines.
  • Verify disabling `WAYPOINTS` yields empty waypoints on route while preserving path points.
  • Verify selected route line is permanently anchored and unaffected by layer toggles.
  • Verify Start and End markers remain visible at all times.
  2. *Breakdown Section Visibility Toggle Contract Tests (`RouteBreakdownVisibilityToggleContractTest.kt`)*:
  • Verify `RouteClimbsBreakdownSection` accepts `hiddenClimbIds: Set<Long>` and `onToggleClimbVisibility` callback.
  • Verify `RouteSegmentsBreakdownSection` accepts `hiddenSegmentIds: Set<Long>` and `onToggleSegmentVisibility` callback.
  • Verify toggle icon button interactions trigger respective callbacks.
  3. *9-Language Localization Parity Audit (`RouteLayersLocalizationTest.kt`)*:
  • Verify all route layer string resources (`route_layers`, `route_layer_climbs`, `route_layer_segments`, `route_layer_waypoints`, `route_item_hide`, `route_item_show`) exist and are non-empty across `values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/` with matching format specifiers.
  4. *Full Clean-Room Regression Suite*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with 0 regressions.

### TST-UI-269: **ANT+ Status Sheet Untinted Branding, BLE Hint Elimination, and Localization Parity Verification** [Official ANT+ logo renders untinted in AntServicesStatusSheet title bar; BLE hint card removed; string resource cleanly eliminated across all 9 locales; 100% unit test pass rate.]
- **Requirement(s)**: `REQ-UI-309`
- **Component**: **ANT+ Status Sheet Untinted Branding, BLE Hint Elimination, and Localization Parity Verification**
- **Jira**: ``ATT-2749``
- **Test Description**:
  1. *AntServicesStatusSheet Architectural & Contract Tests (`AntServicesStatusContractTest.kt`)*:
  • Verify `AntServicesStatusSheet.kt` passes `iconTint = Color.Unspecified` to `AppModalBottomSheet`.
  • Verify `AntServicesStatusSheet.kt` does not contain `ant_status_ble_alternative_note`.
  • Verify `AntServicesStatusSheet.kt` does not reference `logo_protocol_bluetooth`.
  2. *9-Language Localization Audit & Cleanup Verification (`AntStatusLocalizationTest.kt`)*:
  • Verify `ant_status_ble_alternative_note` is absent across all 9 `strings.xml` files (`values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`).
  • Verify all remaining active ANT status strings maintain 100% parity across all 9 locales.
  3. *Full Clean-Room Regression Suite*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with 0 regressions.

### TST-UI-270: **Sport-Discipline Filtering in Quick Route Selector and Route List Sport Iconography Verification** [Route selector filters candidate routes matching active sport discipline with fallback to untagged routes; route cards display sport icons; 100% unit test pass rate.]
- **Requirement(s)**: `REQ-UI-310`
- **Component**: **Sport-Discipline Filtering in Quick Route Selector and Route List Sport Iconography Verification**
- **Jira**: ``ATT-2668``
- **Test Description**:
  1. *RouteProximityRanker Unit Tests (`RouteProximityRankerTest.kt`)*:
  • Verify `filterAndRankRoutes` with `activeSport = BSportType.BIKE` returns bike and untagged routes, excluding run routes.
  • Verify `filterAndRankRoutes` with `activeSport = BSportType.RUN` returns run and untagged routes, excluding bike routes.
  • Verify `filterAndRankRoutes` with `activeSport = BSportType.UNKNOWN` or null returns all routes within proximity.
  2. *RouteSelectorViewModel Reactive Sport Filtering Unit Tests (`RouteSelectorViewModelTest.kt`)*:
  • Verify `setActiveSport(BSportType.BIKE)` updates `uiState.routes` to include only bike and untagged routes.
  • Verify `setActiveSport(BSportType.RUN)` updates `uiState.routes` to include only run and untagged routes.
  • Verify switching back to `UNKNOWN` restores all nearby candidate routes.
  • Verify in-ride auto-detection candidates match the active sport profile.
  3. *RouteCard Sport Iconography Contract Tests (`RouteSelectorSheetContractTest.kt`)*:
  • Verify `RouteCard` renders `Icon` displaying `route.summary.bSportType.iconResId` preceding route title.
  • Verify icon sizing and semantic tinting.
  4. *Full Clean-Room Regression Suite*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with 0 regressions.

### TST-UI-271: **Removal of In-Ride Auto-Detected Route Banner and Sensor Grid Decoupling Verification** [Auto-detected route banner eliminated from SensorGridScreen; obsolete composable excised from RouteSelectorSheet; obsolete strings removed across all 9 locales; 100% unit test pass rate.]
- **Requirement(s)**: `REQ-UI-311`
- **Component**: **Removal of In-Ride Auto-Detected Route Banner and Sensor Grid Decoupling Verification**
- **Jira**: ``ATT-2771``
- **Test Description**:
  1. *SensorGridScreen Route Integration Contract Test (`SensorGridScreenRouteIntegrationTest.kt`)*:
  • Verify `SensorGridScreen.kt` does NOT contain `AutoDetectedRouteBanner(`.
  • Verify `SensorGridScreen.kt` retains primary sensor grid column and scrolling.
  2. *ControlTracking Route Selection Contract Test (`ControlTrackingRouteSelectionContractTest.kt`)*:
  • Verify `SensorGridScreen.kt` does NOT host `AutoDetectedRouteBanner(`.
  3. *RouteSelectorSheet Component Contract Test (`RouteSelectorSheetTest.kt`)*:
  • Verify `RouteSelectorSheet.kt` does NOT define `fun AutoDetectedRouteBanner(`.
  • Verify `RouteSelectorModalBottomSheet`, `RouteSelectorContent`, `ActiveRouteBanner`, and `RouteCard` remain exposed.
  4. *9-Language Localization Audit (`TranslationParityTest.kt`)*:
  • Verify `route_auto_detect_title`, `route_auto_detect_prompt`, `route_auto_detect_activate`, and `route_auto_detect_dismiss` are removed across all 9 `strings.xml` files (`values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`).
  • Verify all remaining active string resources pass with 100% parity.
  5. *Clean-Room Full Suite Regression*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% pass rate with 0 regressions.

### TST-UI-272: **Root Fallback Drawables Density Audit, Drawer Header Resilience and Guarded Compose Loading Contract Verification** [100% of density-specific drawables have matching root fallback assets in `res/drawable/`; safe painter loader handles invalid/missing IDs gracefully; SportTypeSelector and AppNavigationDrawer are protected against Resources$NotFoundException; logo_512 exists across density buckets; 100% test pass rate.]
- **Requirement(s)**: `REQ-UI-312`
- **Component**: **Root Fallback Drawables Density Audit, Drawer Header Resilience and Guarded Compose Loading Contract Verification**
- **Jira**: ``ATT-2858`, `ATT-3164``
- **Test Description**:
  1. *Automated Density Fallback Audit Unit Test (`DrawableDensityFallbackAuditTest.kt`)*:
  • Scan all directories in `app/src/main/res` matching `drawable-*`.
  • Collect all unique image filenames (`.png`, `.jpg`, `.webp`, `.xml`).
  • Assert that every collected filename exists in root `app/src/main/res/drawable/`.
  2. *Safe Painter Resource Contract Test (`SafePainterResourceContractTest.kt`)*:
  • Verify `safePainterResource` loads valid drawables correctly.
  • Verify `safePainterResource` with non-existent or invalid resource ID returns provided fallback `ImageVector` without throwing `Resources$NotFoundException`.
  3. *SportTypeSelector Defensive Loading Contract Test (`SportTypeSelectorContractTest.kt`)*:
  • Assert that `SportItem` resolves all sport type drawables (`bsport_run`, `bsport_bike`, `bsport_other`) defensively with vector fallbacks.
  • Verify all `BSportType` drawables exist in root `app/src/main/res/drawable/`.
  4. *AppNavigationDrawer Defensive Loading & Density Bucket Audit Contract Test (`AppNavigationDrawerAssetResilienceTest.kt`)*:
  • Verify `DrawerHeader` invokes `safePainterResource` for `logo_512` with `Icons.Default.DirectionsRun` fallback.
  • Assert `painterResource(id = R.drawable.logo_512)` does NOT occur in `AppNavigationDrawer.kt`.
  • Verify `logo_512.png` exists in `res/drawable/`, `res/drawable-mdpi/`, `res/drawable-hdpi/`, `res/drawable-xhdpi/`, `res/drawable-xxhdpi/`, `res/drawable-xxxhdpi/`, and `res/drawable-nodpi/`.
  5. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with 0 regressions.

### TST-UI-273: **Control Tracking Screen Sensor Header Centering & Accessibility Parity Verification** [Symmetrical balancing anchor ensures mathematical screen centering for single/dual sensor tiles; accessibility semantics cleared; zero collision for multi-device setups; 100% unit test pass rate.]
- **Requirement(s)**: `REQ-UI-313`
- **Component**: **Control Tracking Screen Sensor Header Centering & Accessibility Parity Verification**
- **Jira**: ``ATT-2791``
- **Test Description**:
  1. *Sensor Header Contract Tests (`ControlTrackingSensorHeaderContractTest.kt`)*:
  • Verify symmetrical balancing anchor exists when `devices.isNotEmpty()` and `showResearchButton`.
  • Verify trailing anchor declares `Modifier.clearAndSetSemantics { }` and `Modifier.alpha(0f)`.
  • Verify `RemoteDevices` receives `Modifier.weight(1f)` between balanced leading and trailing components.
  • Verify `SearchArea` remains in its dedicated slot above sensor rows.
  • Verify `ResearchButton` centers horizontally when `devices.isEmpty()`.
  • Verify multi-device Compose preview covers 6 devices without layout collision.
  2. *Clean-Room Regression Suite*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with 0 regressions.

### TST-UI-274: **Navigation Drawer Decluttering and Advanced Tuning Sensor Search Section Contract Verification** [Navigation drawer decluttered to 21 items without search settings; Advanced Tuning hosts dedicated SensorSearchTuningSection with retry slider and 6 switches; all tuning files strictly under 400 lines; 100% test pass rate.]
- **Requirement(s)**: `REQ-UI-314`
- **Component**: **Navigation Drawer Decluttering and Advanced Tuning Sensor Search Section Contract Verification**
- **Jira**: ``ATT-2780``
- **Test Description**:
  1. *Navigation Drawer State Unit Tests (`NavigationDrawerStateTest.kt`)*:
  • Verify navigation drawer contains exactly 21 items across 5 categories.
  • Verify `drawer__settings` group contains 5 items (Units, Display, Tracking Layouts, Backup & Restore, Privacy Policy).
  • Verify `R.id.drawer_search_settings` is absent from all drawer categories.
  2. *Single Activity Navigation Unit Tests (`SingleActivityNavigationTest.kt`)*:
  • Verify `NavRoutes.fromDrawerItemId(R.id.drawer_search_settings)` is null.
  3. *Advanced Tuning Modularity & Structure Contract Tests (`AdvancedTuningModularityTest.kt`, `AdvancedTuningVisualContractTest.kt`)*:
  • Verify `categories/SensorSearchTuningSection.kt` exists and defines `@Composable fun SensorSearchTuningSection(...)`.
  • Verify all files in `ui.settings.tuning` remain strictly under 400 lines.
  • Verify `TuningSection.SENSOR_SEARCH` exists and is initially collapsed.
  • Verify `TuningSubtitleFormatter.formatSensorSearchSubtitle` generates correct summary.
  4. *9-Language Localization Audit (`TranslationParityTest.kt`)*:
  • Verify `tuning_cat_sensor_search` and related subtitle strings exist and have valid format tokens across all 9 supported application locales.
  5. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with 0 regressions.

### TST-UI-275: **Harmonized Visual & UX Architecture for Climb, Segment, and Route Detail Bottom Sheets via MapDetailLayout Verification** [Segment, Route, and Climb modal detail sheets directly reuse SegmentOnMapScreen, RouteOnMapScreen, and MapDetailLayout; obsolete card columns eliminated; 100% 9-language parity; 100% unit test pass rate.]
- **Requirement(s)**: `REQ-UI-315`
- **Component**: **Harmonized Visual & UX Architecture for Climb, Segment, and Route Detail Bottom Sheets via MapDetailLayout Verification**
- **Jira**: ``ATT-2860``
- **Test Description**:
  1. *SegmentDetailSheet Map Popup Unification Contract Test (`SegmentDetailSheetContractTest.kt`)*:
  • Verify `SegmentDetailSheet` directly hosts `SegmentOnMapScreen` within a non-scrolling modal bottom sheet container.
  • Verify that header renders `SegmentHeader` and `SegmentDetails` in parity with map popup.
  • Verify obsolete 3-card composables (`SegmentDetailMetricsCard`, `SegmentDetailMapCard`, `SegmentDetailElevationProfileCard`) are eliminated.
  2. *RouteDetailSheet Route Map Screen Unification Contract Test (`RouteDetailSheetContractTest.kt`)*:
  • Verify `RouteDetailSheet` directly hosts `RouteOnMapScreen` within a modal container.
  • Verify obsolete 3-card composables (`RouteDetailMetricsCard`, `RouteDetailMapCard`, `RouteDetailElevationProfileCard`) are eliminated.
  3. *ClimbDetailSheet Layout Alignment Contract Test (`ClimbDetailSheetContractTest.kt`)*:
  • Verify `ClimbDetailSheet` aligns with `MapDetailLayout` and structured climb header.
  4. *9-Language Localization Audit (`TranslationParityTest.kt`)*:
  • Verify string resources across all detail sheets maintain 100% parity across EN, DE, ES, FR, IT, JA, NL, PL, PT.
  5. *Full Clean-Room Regression Suite*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with 0 regressions.

### TST-UI-276: **Dedicated Route Detail Bottom Sheet from Segment View Verification** [Dedicated RouteDetailSheet modal bottom sheet overlay from containing routes in SegmentOnMapScreen; renders route metrics, bounded map, and elevation profile without navigating away from segment view; 100% test pass rate.]
- **Requirement(s)**: `REQ-UI-316`
- **Component**: **Dedicated Route Detail Bottom Sheet from Segment View Verification**
- **Jira**: ``ATT-2861``
- **Test Description**:
  1. *RouteDetailSheet Structural & Contract Tests (`RouteDetailSheetContractTest.kt`)*:
  • Verify `RouteDetailSheet` exists and accepts `RouteWithPath`, `onDismiss`, and `modifier`.
  • Verify `RouteDetailSheet` declares `AppModalBottomSheet`.
  • Verify Header contains route title, sport icon, and dedicated close button.
  • Verify Metrics card declares `RoundedCornerShape(16.dp)` and `surfaceContainer` styling.
  • Verify Map card declares `MapZoomFocus.EXPLICIT_BOUNDS` and embeds route polyline in `TTColor.RouteSelected`.
  • Verify Elevation profile card is rendered.
  2. *StarredSegmentsScreen Overlay Navigation Verification*:
  • Verify tapping a containing route in `SegmentRoutesSection` sets `inspectedRouteId` without unmounting `SegmentOnMapScreen`.
  • Verify `inspectedRouteId` renders `RouteDetailSheet` modal overlay.
  • Verify dismissing sheet clears `inspectedRouteId` and preserves segment map screen state.
  3. *9-Language Localization Audit (`TranslationParityTest.kt`)*:
  • Verify any newly introduced string keys exist across all 9 application locales (EN, DE, ES, FR, IT, JA, NL, PL, PT) with matching format specifiers.
  4. *Full Clean-Room Regression Suite*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with 0 regressions.

### TST-UI-277: **Geodesic Polyline Orthogonal Projection & Interval Matching Verification** [Geodesic orthogonal projection onto polyline line segments matches segments between sparse vertices and on out-and-back routes; reverse/perpendicular tracks rejected; 100% test pass rate.]
- **Requirement(s)**: `REQ-UI-317`
- **Component**: **Geodesic Polyline Orthogonal Projection & Interval Matching Verification**
- **Jira**: ``ATT-2862``
- **Test Description**:
  1. *Orthogonal Projection & Sparse Route Unit Tests (`RouteSegmentMatcherTest.kt`)*:
  • Verify segment starting/ending midway between sparse route vertices (e.g. 50 m spacing, where vertex distance > 25 m but cross-track distance < 10 m) is successfully matched.
  • Verify accurate `distanceAlongRouteMeters` and route span consistency derived from interpolated projection.
  2. *Out-and-Back & Circuit Route Traversal Tests*:
  • Verify an out-and-back route that traverses a segment in forward direction on outbound leg and reverse direction on return leg matches the forward interval without negative progression or span blowout.
  3. *Directional Bearing & Rejection Invariants*:
  • Verify reverse traversal segment is rejected.
  • Verify perpendicular crossing segment is rejected.
  • Verify diverging finish and horseshoe midpoint are rejected.
  • Verify conflicting sport types are rejected.
  4. *Full Clean-Room Regression Suite*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with 0 regressions.

### TST-UI-278: **Route Map Segment Polyline Suppression & Background Path Layer Filtering Contract Verification** [Segment layer toggle unselection suppresses all segment polylines (matched and background) from the map canvas; individual hidden segments filtered from background paths; 100% unit test pass rate.]
- **Requirement(s)**: `REQ-UI-318`
- **Component**: **Route Map Segment Polyline Suppression & Background Path Layer Filtering Contract Verification**
- **Jira**: ``ATT-2863``
- **Test Description**:
  1. *RouteOnMapScreen Segment Layer Filtering Contract Tests (`RouteOverlayLayersContractTest.kt`)*:
  • Verify that disabling `RouteOverlayLayer.SEGMENTS` filters out all `MapSegment` paths from `backgroundPaths` rendered via `contextualPaths`.
  • Verify that `hiddenSegmentIds` filters out matching `MapSegment` paths from `backgroundPaths`.
  • Verify that `matchedSegmentIds` prevents duplication of segments between `segments(...)` and `contextualPaths(...)`.
  • Verify that `hasSegments` includes `backgroundPaths.any { it is MapSegment }`.
  • Verify selected route line and Start/End markers remain permanently anchored.
  2. *Full Clean-Room Regression Suite*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with 0 regressions.

### TST-UI-279: **Multi-Tier Route Map Polyline Layering: Solid Climb Overlay and Dashed Segment Overlay Synergy Verification** [Multi-tier polyline layering establishes climbs as solid polylines above both route layers and segments as dashed polylines above climbs with transparent gaps; 100% test pass rate.]
- **Requirement(s)**: `REQ-UI-319`
- **Component**: **Multi-Tier Route Map Polyline Layering: Solid Climb Overlay and Dashed Segment Overlay Synergy Verification**
- **Jira**: ``ATT-2864``
- **Test Description**:
  1. *MapVisualization Z-Index Hierarchy Contract (`RouteClimbSegmentLayeringContractTest.kt`)*:
  • Assert `ROUTE_BASE_Z_INDEX` (20.0f) < `ROUTE_OVERLAY_Z_INDEX` (22.0f).
  • Assert `ROUTE_OVERLAY_Z_INDEX` (22.0f) < `CLIMB_Z_INDEX` (28.0f).
  • Assert `ROUTE_ACTIVE_OVERLAY_Z_INDEX` (26.0f) < `CLIMB_Z_INDEX` (28.0f).
  • Assert `CLIMB_Z_INDEX` (28.0f) < `SEGMENT_Z_INDEX` (30.0f).
  • Assert `SEGMENT_DASH_LENGTH` is 20f and `SEGMENT_GAP_LENGTH` is 15f.
  • Assert `CLIMB_WIDTH` is 10f.
  2. *MapSegment Pattern & isDashed Contract (`RouteClimbSegmentLayeringContractTest.kt`)*:
  • Assert `MapSegment.isDashed` defaults to `false`.
  • Assert when `isDashed == false`, `pattern` is `null` (solid polyline).
  • Assert when `isDashed == true`, `pattern` contains `Dash(20f)` and `Gap(15f)`.
  3. *ClimbHighlightData Z-Index Contract (`RouteClimbSegmentLayeringContractTest.kt`)*:
  • Assert `MapContentScope.ClimbHighlightData` default `zIndex` equals `MapVisualization.CLIMB_Z_INDEX` (28f).
  • Assert `MapContentScope.climbs(...)` renders solid polylines with `JointType.ROUND`.
  4. *MappablePathLayer Transparent Gap Base Omission (`RouteClimbSegmentLayeringContractTest.kt`)*:
  • Assert `MappablePathLayer` sets `hasSolidBase = false` when `path is MapSegment && path.isDashed`.
  • Assert `XRayPolyline` skips solid base rendering when `hasSolidBase == false`.
  5. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with 0 regressions.

### TST-UI-280: **Preservation of Authentic Sport Type Icon Colors in Quick Route Selector Verification** [Sport type icons render in their authentic multi-color vector asset colors with tint = Color.Unspecified; active route chip and layout invariants preserved; 100% test pass rate.]
- **Requirement(s)**: `REQ-UI-320`
- **Component**: **Preservation of Authentic Sport Type Icon Colors in Quick Route Selector Verification**
- **Jira**: ``ATT-2865``
- **Test Description**:
  1. *RouteCard Sport Icon Color & Structural Contract Tests (`RouteSelectorSheetTest.kt`)*:
  • Verify `RouteCard` renders sport icon via `route.summary.bSportType.iconResId`.
  • Verify `RouteCard` specifies `tint = Color.Unspecified`, preserving multi-color vector asset colors without monochrome theme tinting.
  • Verify `RouteCard` does NOT contain `tint = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant`.
  • Verify `RouteCard` provides localized accessibility description via `stringResource(id = route.summary.bSportType.stringResId)`.
  • Verify `RouteCard` sizes sport icon at `24.dp`.
  • Verify active routes continue to display dedicated `"ACTIVE"` chip (`labelSmall`, `onPrimary` text over `primary` background).
  2. *Full Clean-Room Regression Suite*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with 0 regressions.

### TST-UI-281: **Fork Decision Prompt Spatial Overlay & Tab Navigation Hints Gating Verification** [Fork decision prompt floats as top-level spatial overlay at Alignment.TopCenter without displacing underlying sensor tiles; conditionally gated strictly behind state.showNavigationHints; 100% test pass rate.]
- **Requirement(s)**: `REQ-UI-321`
- **Component**: **Fork Decision Prompt Spatial Overlay & Tab Navigation Hints Gating Verification**
- **Jira**: ``ATT-2874``
- **Test Description**:
  1. *Tab Navigation Hints Gating Contract Tests (`TrackingTabWysiwygContractTest.kt`)*:
  • Verify `ForkDecisionCard` in `SensorGridScreen.kt` is conditionally gated by `state.showNavigationHints`.
  2. *Spatial Overlay Container Contract Tests (`SensorGridScreenRouteIntegrationTest.kt`)*:
  • Verify `ForkDecisionCard` is anchored at `Alignment.TopCenter` within a non-displacing overlay container.
  • Verify `ForkDecisionCard` is NOT an in-flow child of the scrollable sensor grid `Column`.
  • Verify `ForkDecisionCard` consumes `tuningConfig.navigationCueTransparency` per Design Guidelines Section 5.7.
  3. *Full Clean-Room Regression Suite*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with 0 regressions.

### TST-UI-282: **General Map Layer Visibility Controls & Preference Persistence Verification** [General map Layers menu toggles routes, segments, favorite locations, and live track; user layer visibility preferences persisted across sessions in SharedPreferences; 100% 9-language localization parity; 100% unit test pass rate.]
- **Requirement(s)**: `REQ-UI-322`
- **Component**: **General Map Layer Visibility Controls & Preference Persistence Verification**
- **Jira**: ``ATT-2931``
- **Test Description**:
  1. *GeneralMapLayer Enum & Default State Contract (`GeneralMapLayersContractTest.kt`)*:
  • Verify `GeneralMapLayer` enum declares `ROUTES`, `SEGMENTS`, `KNOWN_LOCATIONS`, `TRACK`.
  • Verify default state enables all layers.
  2. *MapFragmentWithTrackViewModel Layer Toggling & Persistence Contract (`GeneralMapLayersContractTest.kt`)*:
  • Verify initial state loads from `SharedPreferences` or defaults to all layers.
  • Verify `toggleLayer(layer)` removes layer if present, adds if absent.
  • Verify toggling updates `_enabledLayers` and persists serialized set to `SharedPreferences`.
  3. *MapScreenWithTrack UI Structure & Conditional Layer Delegation (`GeneralMapLayersContractTest.kt`)*:
  • Verify `MapScreenWithTrack.kt` includes `Icons.Default.Layers` button.
  • Verify floating surface at `Alignment.TopEnd` with `statusBarsPadding().padding(top = 16.dp, end = 16.dp)`.
  • Verify dropdown menu includes checkboxes and swatches for all 4 layers.
  • Verify conditional rendering of `knownLocations()`, `segments()`, `routes()`, and `markers()` + `liveTrack()`.
  4. *9-Language Localization Parity Audit (`GeneralMapLayersLocalizationTest.kt`)*:
  • Verify string resources `map_layers`, `map_layer_routes`, `map_layer_segments`, `map_layer_locations`, `map_layer_track` exist and are non-empty across all 9 supported locales (`values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`).
  5. *Full Clean-Room Regression Suite*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with 0 regressions.

### TST-UI-283: **Material 3 Compose ANT Missing Adapter & Dependency Alerts Verification** [Missing adapter and missing dependency alerts modernized to Material 3 Compose with official untinted ANT+ logo; direct shortcut to AntServicesStatusSheet; German typo corrected; 100% 9-language translation parity; 100% unit test pass rate.]
- **Requirement(s)**: `REQ-UI-323`
- **Component**: **Material 3 Compose ANT Missing Adapter & Dependency Alerts Verification**
- **Jira**: ``ATT-2939``
- **Test Description**:
  1. *AntMissingAdapterDialog Contract Tests (`AntAlertDialogContractTest.kt`)*:
  • Verify renders Material 3 AlertDialog with untinted ANT+ logo (`R.drawable.ant_logo`).
  • Verify title matches `R.string.ant_missing_adapter_title`.
  • Verify message matches `R.string.ant_missing_adapter_message`.
  • Verify confirm button matches `R.string.ant_dialog_view_status` and invokes `onViewStatus()`.
  • Verify dismiss button matches `R.string.ant_dialog_dismiss` and invokes `onDismiss()`.
  2. *AntMissingDependencyDialog Contract Tests (`AntAlertDialogContractTest.kt`)*:
  • Verify renders Material 3 AlertDialog with untinted ANT+ logo (`R.drawable.ant_logo`).
  • Verify title matches `R.string.ant_missing_dependency_title`.
  • Verify message contains formatted dependency name.
  • Verify confirm button matches `R.string.go_to_store` and invokes `onGoToStore()`.
  • Verify dismiss button matches `R.string.cancel` and invokes `onDismiss()`.
  3. *MainActivityWithNavigation Elimination of Raw Dialogs (`AntAlertDialogContractTest.kt`)*:
  • Verify zero occurrences of `AlertDialog.Builder` within `showANTAdapterMissingDialog` and `showSpecificInstallANTDialog`.
  • Verify state dispatching to Compose `antDialogState` and `showAntStatusSheet`.
  4. *9-Language Localization Parity Audit (`AntDialogLocalizationTest.kt`)*:
  • Verify `ant_dialog_view_status` and `ant_dialog_dismiss` exist, are non-empty, and resolve cleanly across `values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`.
  • Verify `values-de/strings.xml` contains "installiert" and zero occurrences of "intalliert".
  5. *Full Clean-Room Regression Suite*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with 0 regressions.

### TST-UI-284: **Turn-by-Turn Navigation Prompts Spatial Overlay & Visual Harmonization Verification** [TurnPromptBanner floats as non-displacing top-level overlay in SensorGridScreen; gated strictly by state.showNavigationHints; card shapes, colors, borders, and padding conform to Design Guidelines; 100% unit test pass rate.]
- **Requirement(s)**: `REQ-UI-324`
- **Component**: **Turn-by-Turn Navigation Prompts Spatial Overlay & Visual Harmonization Verification**
- **Jira**: ``ATT-2941``
- **Test Description**:
  1. *SensorGridScreen Top-Level Spatial Overlay & Tab Gating Contract Tests (`SensorGridScreenRouteIntegrationTest.kt`)*:
  • Verify `TurnPromptBanner` is NOT rendered inside the scrollable in-flow sensor grid `Column`.
  • Verify `TurnPromptBanner` is hosted within the top-level spatial overlay `Column` at `Alignment.TopCenter`.
  • Verify `TurnPromptBanner` is conditionally gated by `state.showNavigationHints`.
  • Verify `TurnPromptBanner` binds `overlayAlpha` to `tuningConfig.navigationCueTransparency`.
  • Verify `TurnPromptBanner` binds `dismissDurationSec` to `tuningConfig.navigationCueDismissDurationSec`.
  2. *TurnPromptBanner Visual Styling & Design Guideline Conformance Tests (`SensorGridScreenRouteIntegrationTest.kt`)*:
  • Verify `TurnCueCard` and `OffRouteCard` apply `RoundedCornerShape(16.dp)`.
  • Verify `TurnCueCard` applies Royal Blue navigation border stroke `BorderStroke(1.dp, TTColor.RouteActiveNavigation.copy(alpha = 0.5f))`.
  • Verify `TurnCueCard` applies `surfaceContainer` (approaching) and `primaryContainer` (`isTurnNow`).
  • Verify inner content padding uses `padding(horizontal = 16.dp, vertical = 12.dp)`.
  3. *Full Clean-Room Regression Suite*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with 0 regressions.

### TST-UI-285: **Single Active Route Card Presentation and Direct In-List Cancellation Verification** [Active route appears exactly once in RouteSelectorSheet without upper duplicate banner; direct 1-tap route cancellation operational on active RouteCard; 100% unit test pass rate.]
- **Requirement(s)**: `REQ-UI-325`
- **Component**: **Single Active Route Card Presentation and Direct In-List Cancellation Verification**
- **Jira**: ``ATT-2943``
- **Test Description**:
  1. *RouteSelectorContent Upper Banner Exclusion Contract Tests (`RouteSelectorSheetTest.kt`)*:
  • Verify `RouteSelectorContent` does NOT compose `ActiveRouteBanner` in its layout body.
  • Verify `ActiveRouteBanner` composable definition remains exposed in `RouteSelectorSheet.kt` for backward compatibility.
  2. *RouteCard In-List Cancellation & Interaction Tests (`RouteSelectorSheetTest.kt`)*:
  • Verify `RouteCard` renders `Icons.Default.Close` button with `@string/route_action_clear` when `isActive == true` and `onClearRoute != null`.
  • Verify clicking close button triggers `onClearRoute`.
  • Verify `RouteCard` preserves `secondaryContainer` and `"ACTIVE"` badge styling when active.
  3. *Full Clean-Room Regression Suite*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with 0 regressions.

### TST-UI-286: **Tracking Map Recomposition Boundary and State Decoupling Verification** [TrackingMapContainer acts as Compose recomposition barrier; map DSL lambda memoized; TrackingViewModel reuses mapState across telemetry ticks; 100% unit test pass rate.]
- **Requirement(s)**: `REQ-UI-326`
- **Component**: **Tracking Map Recomposition Boundary and State Decoupling Verification**
- **Jira**: ``ATT-2944``
- **Test Description**:
  1. *Sub-State Model & Compose Stability Tests (`TrackingMapIsolationContractTest.kt`)*:
  • Verify `TrackingMapState` is annotated with `@Immutable`.
  • Verify `TrackingMapState` encapsulates all 11 map-specific rendering properties.
  • Verify `TrackingScreenState` includes `val mapState: TrackingMapState` with backward-compatible defaults.
  2. *TrackingMapContainer Recomposition Barrier Contract Tests (`TrackingMapIsolationContractTest.kt`)*:
  • Verify `SensorGridScreen.kt` defines `fun TrackingMapContainer(`.
  • Verify `TrackingMapContainer` memoizes the `MapContentScope` lambda with `remember(`.
  • Verify `SensorGridScreen` delegates map rendering to `TrackingMapContainer` and no longer calls `ATrainingTrackerMap` directly with an un-remembered trailing lambda.
  3. *Telemetry Decoupling Unit Tests (`TrackingViewModelGridTest.kt`)*:
  • Verify that when `allFilteredSensorData` updates (e.g. Heart Rate changes), `uiState.value.mapState` maintains identity/equality with previous state without re-allocating new route or segment collections.
  4. *Full Clean-Room Regression Suite*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with 0 regressions.

### TST-UI-287: **Tracking Tab Live Climb and Segment Cockpit BottomSheet Suppression & State Synchronization Verification** []
- **Requirement(s)**: `REQ-UI-327`
- **Component**: **Tracking Tab Live Climb and Segment Cockpit BottomSheet Suppression & State Synchronization Verification**
- **Jira**: ``ATT-2945``
- **Test Description**:
  1. *Sub-State & Active-Tab Gating Contract Tests (`SensorGridScreenClimbSuppressionTest.kt`)*:
  • Verify `SensorGridScreen` accepts parameter `isTabActive: Boolean = true`..
  • Verify `shouldShowBottomSheet` incorporates `isTabActive`, `(showLiveSegments

### TST-UI-288: **Live Climb Cockpit Sheet Visual Design Alignment & Layout Verification** [LiveClimbSheet layout, spacing, typography, and telemetry HUD match LiveSegmentSheet design system; 100% unit test pass rate.]
- **Requirement(s)**: `REQ-UI-328`
- **Component**: **Live Climb Cockpit Sheet Visual Design Alignment & Layout Verification**
- **Jira**: ``ATT-2947``
- **Test Description**:
  1. *Top Header Structure & Icon Tests (`LiveClimbSheetLayoutTest.kt`)*:
  • Verify 32dp climb icon (`Icons.Default.Terrain`).
  • Verify climb title rendered in `MaterialTheme.typography.titleLarge` with bold weight.
  • Verify `ClimbCategoryChip` badge is right-aligned in the top row.
  2. *Subtitle Status & Route Counter Verification (`LiveClimbSheetLayoutTest.kt`)*:
  • Verify `ClimbStatusBadge` rendered in the subtitle row.
  • Verify route climb counter text formatted with `labelLarge` bold primary color.
  3. *Standardized Divider & Telemetry HUD Layout Tests (`LiveClimbSheetLayoutTest.kt`)*:
  • Verify `HorizontalDivider` with 0.5dp thickness and 16dp horizontal padding.
  • Verify remaining distance in `titleLarge` bold and remaining elevation in `bodyLarge` bold.
  • Verify instantaneous gradient in `headlineMedium` bold monospace font.
  4. *Elevation Profile Canvas Verification (`LiveClimbSheetLayoutTest.kt`)*:
  • Verify `ClimbProfileCanvas` rendering.
  5. *9-Language Localization Audit*:
  • Verify climb strings and tokens exist across EN, DE, ES, FR, IT, JA, NL, PL, PT.
  6. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` verifying 100% test pass rate with zero regressions.

### TST-UI-289: **StartOrResumeDialog Material 3 Compose Migration & Kill Diagnosis Verification** [StartOrResumeDialog modernizes to Material 3 Compose; contextual icons, typography, and button hierarchy match design system; 100% unit test pass rate.]
- **Requirement(s)**: `REQ-UI-329`
- **Component**: **StartOrResumeDialog Material 3 Compose Migration & Kill Diagnosis Verification**
- **Jira**: ``ATT-2948``
- **Test Description**:
  1. *Structural & Inheritance Contract Tests (`StartOrResumeDialogContractTest.kt`)*:
  • Verify `StartOrResumeDialog` inherits `DialogFragment`.
  • Verify `StartOrResumeDialog.TAG` equals class name.
  • Verify `attachInterface` enforces `StartOrResumeInterface`.
  2. *Composable Layout & Contextual State Tests (`StartOrResumeDialogLayoutTest.kt`)*:
  • Verify contextual icons and titles for `BATTERY_KILL` (`BatteryAlert`), `LOW_MEMORY` (`Memory`), `PERMISSION_REVOKED` (`Security`), and `GENERIC_UNFINISHED` (`DirectionsRun`).
  • Verify diagnostic callout container formatting, progressive battery escalation copy (stages 1, 2, 3), and Strava active note.
  • Verify button hierarchy: Resume as filled `Button`, Start new as `OutlinedButton`, and Disable Battery Optimization button visibility when `shouldShowBatteryButton == true`.
  • Verify button callbacks trigger `chooseStart()`, `chooseResume()`, and `openBatteryOptimizationSettings()`.
  3. *9-Language Localization Audit (`StartOrResumeLocalizationTest.kt`)*:
  • Verify all recovery dialog string resources exist across EN, DE, ES, FR, IT, JA, NL, PL, PT.
  4. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` verifying 100% test pass rate with zero regressions.

### TST-UI-290: **In-Ride Fork Decision Card Directional Grouping, Styling & Interaction Verification** [Candidate routes group cleanly under directional headers; crisp white surface and subtle border styling applied; hint text removed; 100% unit test pass rate.]
- **Requirement(s)**: `REQ-UI-330`
- **Component**: **In-Ride Fork Decision Card Directional Grouping, Styling & Interaction Verification**
- **Jira**: ``ATT-2962``
- **Test Description**:
  1. *Directional Grouping Unit Tests (`ForkDecisionCardTest.kt`)*:
  • Verify candidate branches group by `ForkDirection` cleanly..
  2. *Composable Layout & Styling Contract Tests (`ForkDecisionCardTest.kt`)*:
  • Verify surface and border styling tokens conform to M3 guidelines..
  • Verify hint text `fork_select_hint` is omitted from candidate rows..
  3. *9-Language Localization Audit (`ForkDecisionCardTest.kt`)*:
  • Verify all fork alert string resources exist across EN, DE, ES, FR, IT, JA, NL, PL, PT..
  4. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` verifying 100% test pass rate with zero regressions.

### TST-UI-291: **Live Segment Elevation Profile Position Marker & Scrub Distance Verification** [LiveSegmentSheet maps status to externalScrubDistance; MapDetailLayout synchronizes position marker; approaching status renders clean profile; 100% unit test pass rate.]
- **Requirement(s)**: `REQ-UI-331`
- **Component**: **Live Segment Elevation Profile Position Marker & Scrub Distance Verification**
- **Jira**: ``ATT-2967``
- **Test Description**:
  1. *Scrub Distance Calculation Contract Tests (`LiveSegmentSheetContractTest.kt`)*:
  • Verify `LiveSegmentSheet` evaluates `externalScrubDistance` across all `LiveSegmentStatus` states (`ON_SEGMENT`, `ON_SEGMENT_CLOSE_TO_FINISH`, `FINISHED`, `APPROACHING`, `FAR_FAR_AWAY`)..
  • Verify `LiveSegmentSheet` supplies `externalScrubDistance = externalScrubDistance` to `MapDetailLayout`..
  2. *Scrub Distance Synchronization in `MapDetailLayout.kt`*:
  • Verify `MapDetailLayout.kt` synchronizes `selectedDistance = externalScrubDistance` without null gating..
  3. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` verifying 100% test pass rate with zero regressions.

### TST-UI-292: **Workout Details Elevation Profile Display & Telemetry Ingestion Verification for Trackless Workouts** [Altitude telemetry samples extracted correctly for trackless sessions; showElevationProfile enables elevation profile chart in time domain when altitude exists; remains suppressed for zero-altitude sessions; 100% unit test pass rate.]
- **Requirement(s)**: `REQ-UI-332`
- **Component**: **Workout Details Elevation Profile Display & Telemetry Ingestion Verification for Trackless Workouts**
- **Jira**: ``ATT-3046``
- **Test Description**:
  1. *WorkoutRepository Telemetry Altitude Ingestion Tests (`WorkoutRepositoryTelemetryTest.kt`)*:
  • Verify `getWorkoutTelemetryPoints` extracts `SensorType.ALTITUDE` samples and sets `PathPoint.altitude` accurately.
  • Verify samples with only altitude (no HR/power/speed) are retained in `telemetryPath`.
  2. *TrackOnMapScreen Decoupled Visibility Contract Tests (`TracklessAftermathVisualContractTest.kt`, `TrackOnMapScreenDetailPreferencesContractTest.kt`)*:
  • Verify `TrackOnMapScreen.kt` evaluates `hasTracklessAltitude` and decouples `showElevationProfile` from `hasGpsTrack`.
  • Verify `showElevationProfile` evaluates to true for trackless workouts with altitude data.
  • Verify `showElevationProfile` remains conditioned on `activeDetailPrefs.showElevationProfile` and `isElevationPostMap`.
  3. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` verifying 100% test pass rate with zero regressions.

### TST-UI-293: **Entity Detail Sheet Scaffold & Climb Popup Harmonization Verification** [EntityDetailSheetScaffold shared across Climb, Segment, and Route popups; ClimbDetailSheet harmonized with MapDetailLayout, ClimbHeader, and ClimbDetails; 100% unit test pass rate.]
- **Requirement(s)**: `REQ-UI-333`
- **Component**: **Entity Detail Sheet Scaffold & Climb Popup Harmonization Verification**
- **Jira**: ``ATT-3053``
- **Test Description**:
  1. *EntityDetailSheetScaffold Architectural Contract Tests (`EntityDetailSheetScaffoldContractTest.kt`)*:
  • Verify `EntityDetailSheetScaffold` exists, is public, and configures `ModalBottomSheet` with `rememberModalBottomSheetState(skipPartiallyExpanded = true)`, `BottomSheetDesign.SheetShape`, `BottomSheetDesign.SheetTonalElevation`, and `dragHandle = null`.
  • Verify top-right dismiss close button (`IconButton` on `CircleShape` Surface with `Icons.Default.Close`).
  • Verify `SegmentDetailSheet.kt`, `RouteDetailSheet.kt`, and `ClimbDetailSheet.kt` all delegate to `EntityDetailSheetScaffold`.
  2. *ClimbDetailSheet & ClimbOnMapScreen Visual & Layout Contract Tests (`ClimbDetailSheetContractTest.kt`)*:
  • Verify `ClimbDetailSheet` delegates to `EntityDetailSheetScaffold` and `ClimbOnMapScreen`.
  • Verify `ClimbOnMapScreen` uses `MapDetailLayout` with `MapZoomFocus.EXPLICIT_BOUNDS` and `calculateClimbBounds`.
  • Verify `ClimbHeader` renders sport icon, climb name, category chip, and route counter badge.
  • Verify `ClimbDetails` renders distance, average grade, maximum grade, elevation gain, and min/max elevation with standard icons (`ic_distance`, `ic_grade`, `ic_ascent`, `ic_altitude`).
  • Verify `calculateClimbBounds` correctly computes bounds for multi-point and single-point climbs.
  3. *9-Language Localization Audit*:
  • Verify existing string resources (`climb_title`, `climb_route_counter`, `routes_climb_start_at`, `climb_remaining_dist`, `climb_remaining_elevation`, `routes_climb_avg_grade`, `routes_climb_max_grade`, `climb_max_grade_label`) exist across EN, DE, ES, FR, IT, JA, NL, PL, PT.
  4. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` verifying 100% test pass rate with zero regressions.

### TST-UI-294: **Newly Discovered Sensor Prioritization, Visual Highlight Badge & Discovery Feedback Notification Verification** [Newly discovered sensors prioritized at index 0 in scan list; rendered with NEW badge chip and highlight border; snackbar feedback, haptic vibration, and auto-scroll to top operational; 100% 9-language translation parity; 100% unit test pass rate.]
- **Requirement(s)**: `REQ-UI-334`
- **Component**: **Newly Discovered Sensor Prioritization, Visual Highlight Badge & Discovery Feedback Notification Verification**
- **Jira**: ``ATT-3099``
- **Test Description**:
  1. *GetMergedDevicesUseCase Newly Discovered Flagging Unit Tests (`GetMergedDevicesUseCaseTest.kt`)*:
  • Verify `DeviceUiData` mapped for device IDs in `newlyFoundDevicesIds` sets `isNewlyDiscovered = true`.
  • Verify devices not in `newlyFoundDevicesIds` set `isNewlyDiscovered = false`.
  2. *DeviceListViewModel Scan Priority Sorting Unit Tests (`DeviceListViewModelPriorityTest.kt`)*:
  • Verify newly discovered devices sort at index 0 and 1 ahead of connected and idle devices.
  • Verify multiple newly discovered devices sort in recency order (most recently discovered first).
  3. *DeviceItem Visual Badge & Border Contract Tests (`DeviceItemVisualHighlightTest.kt`)*:
  • Verify `DeviceItem` renders "NEW" badge chip (`devices_newly_discovered_badge`) when `isNewlyDiscovered == true`.
  • Verify `DeviceItem` applies `BorderStroke(1.5.dp, primary.copy(alpha = 0.6f))` when `isNewlyDiscovered == true`.
  4. *Discovery Feedback & Notification Event Flow Unit Tests (`DevicesTabbedViewModelDiscoveryTest.kt`)*:
  • Verify `DevicesTabbedViewModel` emits sensor name to `newDeviceDiscoveredEvent` when a new device is found.
  • Verify `DevicesTabbedScreen` observes event, shows snackbar (`devices_new_sensor_discovered`), triggers haptic vibration, and auto-scrolls to index 0.
  5. *9-Language Localization Audit (`DeviceDiscoveryLocalizationTest.kt`)*:
  • Verify string keys `devices_newly_discovered_badge` and `devices_new_sensor_discovered` exist and are non-empty across EN, DE, ES, FR, IT, JA, NL, PL, PT with valid `%1$s` specifiers.
  6. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with zero regressions.

### TST-UI-295: **Dynamic Map-Aware Navigation Hints Overlay Anchoring over Embedded Map View Verification** [Navigation hints overlay anchored over the top of the embedded map view when present, leaving sensor grid unobstructed; anchored at top of tab when map is absent; zero displacement invariant preserved; 100% unit test pass rate.]
- **Requirement(s)**: `REQ-UI-335`
- **Component**: **Dynamic Map-Aware Navigation Hints Overlay Anchoring over Embedded Map View Verification**
- **Jira**: ``ATT-3100``
- **Test Description**:
  1. *Map-Present Layout Contract Tests (`SensorGridScreenRouteIntegrationTest.kt`)*:
  • Verify that when `state.mapState.showMap == true`, the navigation hints overlay is hosted inside the weighted map `Box` with `Alignment.TopCenter`, directly beneath the sensor grid.
  • Verify that the topmost sensor grid rows remain unobstructed and outside the navigation overlay container.
  2. *Map-Absent Layout Contract Tests (`SensorGridScreenRouteIntegrationTest.kt`)*:
  • Verify that when `state.mapState.showMap == false`, the navigation hints overlay is hosted at `Alignment.TopCenter` of the root tracking `Box`.
  3. *Component Encapsulation & Mutual Exclusion Contract Tests (`SensorGridScreenRouteIntegrationTest.kt`)*:
  • Verify `TurnPromptBanner`, `ReturnNavigationHud`, and `ForkDecisionCard` are unified in `NavigationHintsOverlay` and mutually exclusive between map and root branches.
  4. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with zero regressions.

### TST-UI-296: **Cockpit Sensor Field In-Ride Direct Editing via Long-Press Gesture Verification** [Long-clicking sensor field tile in live tracking triggers sensor field configuration dialog; single-tap remains safe no-op; configuration mode move/edit gestures intact; 100% unit test pass rate.]
- **Requirement(s)**: `REQ-UI-336`
- **Component**: **Cockpit Sensor Field In-Ride Direct Editing via Long-Press Gesture Verification**
- **Jira**: ``ATT-3101``
- **Test Description**:
  1. *SensorGridScreen Tracking Mode Wiring Contract Test (`SensorGridScreenContractTest.kt`)*:
  • Verify that in `SensorGridScreen.kt`, during tracking mode, `onEdit` on `SensorFieldView` delegates to `gridActions.onEditField(fieldState)`.
  • Verify that `onStartMove` and `onDelete` remain no-ops in tracking and preview modes.
  2. *SensorFieldView Gesture Contract Test (`SensorFieldViewGestureTest.kt`)*:
  • Verify that in `ScreenMode.TRACKING`, single tap does NOT invoke `onEdit` or `onStartMove`.
  • Verify that in `ScreenMode.TRACKING`, long-press invokes `onEdit()`.
  • Verify that in `ScreenMode.CONFIGURATION`, single tap invokes `onEdit()`, and long-press invokes `onStartMove()`.
  3. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` verifying 100% test pass rate with zero regressions.

### TST-UI-297: **In-Ride Fork Decision Card Zero Tonal Elevation & Clean Surface Verification** [Outer container and direction groups strictly enforce tonalElevation = 0.dp; zero surfaceVariant backgrounds; uniform overlayAlpha; dividers preserved; 100% test pass rate.]
- **Requirement(s)**: `REQ-UI-337`
- **Component**: **In-Ride Fork Decision Card Zero Tonal Elevation & Clean Surface Verification**
- **Jira**: ``ATT-3104``
- **Test Description**:
  1. *ForkDecisionCard Tonal Elevation & Surface Token Contract Tests (`ForkDecisionCardTest.kt`)*:
  • Verify `ForkDecisionCard.kt` specifies `tonalElevation = 0.dp` for the outer container and `tonalElevation = 0.dp` for `ForkDirectionGroup`.
  • Verify `ForkDecisionCard.kt` contains zero references to `surfaceVariant` for container backgrounds.
  • Verify `ForkDecisionCard.kt` uses `MaterialTheme.colorScheme.surface` with `overlayAlpha` for outer and inner surfaces.
  2. *ForkDirectionGroup Visual Hierarchy & Dividers Contract Tests (`ForkDecisionCardTest.kt`)*:
  • Verify `ForkDirectionGroup` preserves `VerticalDivider` and `HorizontalDivider` with `outlineVariant`.
  • Verify direction column and `ForkRouteRow` do not declare independent grey surface backgrounds.
  3. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with zero regressions.

### TST-UI-298: **Climb Detail Elevation Profile Local Distance Normalization & Scrubbing Synchronization Verification** [Climb elevation profile X-axis normalized to 0.0m..climbDistanceMeters spanning 100% chart width; scrubbing synchronized with map pin; header route offset preserved; 100% test pass rate.]
- **Requirement(s)**: `REQ-UI-338`
- **Component**: **Climb Detail Elevation Profile Local Distance Normalization & Scrubbing Synchronization Verification**
- **Jira**: ``ATT-3105``
- **Test Description**:
  1. *Climb Path Normalization Logic Unit Tests (`ClimbNormalizationTest.kt`)*:
  • Verify `climb.normalizedPathPoints` on climb with route-relative distances (e.g. 27380.0m to 28102.0m) produces points starting at 0.0m and ending at 722.0m.
  • Verify all intermediate point distances are correctly shifted by subtracting `startDist`.
  • Verify latitude, longitude, altitude, and timestamps on each `PathPoint` remain identical.
  • Verify empty `pathPoints` returns empty list.
  • Verify single-point `pathPoints` returns point with distance 0.0.
  • Verify already normalized `pathPoints` (starting at 0.0) returns list unchanged.
  2. *ClimbOnMapScreen Architecture & Integration Contract Tests (`ClimbDetailSheetContractTest.kt`)*:
  • Verify `ClimbOnMapScreen.kt` passes `climb.normalizedPathPoints` (or remembered normalized path) to `MapDetailLayout` as `activeScrubPath`.
  • Verify `ClimbHeader.kt` preserves display of route offset ("Start at 27.4 km") via `climb.pathPoints.firstOrNull()?.distance`.
  3. *Scrubbing Point Lookup & Synchronization Tests (`ClimbNormalizationTest.kt`)*:
  • Verify `TelemetryMetricUtils.findNearestPoint` on normalized path with local distances (e.g. 361.0m) resolves to the midpoint coordinates and altitude.
  4. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with zero regressions.

### TST-UI-299: **Dynamic Cockpit Layout Weighting, Sensor Grid Scrollability & Elevation Profile Vertical Expansion Verification** [Sensor grid constrained with weight(1f, fill = false); elevation profile given weight(1f) and fillParentHeight = true when map is disabled; list previews unchanged; 100% test pass rate.]
- **Requirement(s)**: `REQ-UI-339`
- **Component**: **Dynamic Cockpit Layout Weighting, Sensor Grid Scrollability & Elevation Profile Vertical Expansion Verification**
- **Jira**: ``ATT-3107``
- **Test Description**:
  1. *SensorGridScreen Dynamic Weighting Structural Contract Tests (`SensorGridScreenLayoutTest.kt`)*:
  • Verify sensor grid `Column` specifies `Modifier.weight(1f, fill = false)`.
  • Verify `TrackingMapContainer` retains `Modifier.weight(1f)` when `showMap == true`.
  • Verify `ElevationProfile` container `Surface` applies `Modifier.weight(1f)` when `!state.mapState.showMap` and `state.showElevationProfile == true`.
  • Verify `ElevationProfile` receives `fillParentHeight = !state.mapState.showMap` and `modifier = if (!state.mapState.showMap) Modifier.fillMaxSize() else Modifier.fillMaxWidth()`.
  • Verify rendering is guarded by `state.pathPoints.size >= 2`.
  2. *ElevationProfile Decoupled Canvas Height Contract Tests (`ElevationProfileLayoutTest.kt`)*:
  • Verify `ElevationProfile` composables declare `fillParentHeight: Boolean = false` parameter default.
  • Verify that when `fillParentHeight == false`, `baseCanvasModifier` uses `Modifier.fillMaxWidth().height(totalCanvasHeight)`.
  • Verify that when `fillParentHeight == true`, `baseCanvasModifier` uses `Modifier.fillMaxWidth().defaultMinSize(minHeight = totalCanvasHeight).fillMaxHeight()`.
  3. *List Preview Callers Non-Regression Verification (`ElevationProfileLayoutTest.kt`)*:
  • Verify all list preview callers (`WorkoutSummary.kt`, `RouteItem.kt`, `SegmentItem.kt`) do not pass `fillParentHeight = true` and continue relying on default wrapped height.
  4. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with zero regressions.

### TST-UI-300: **LiveSegmentSheet Finish State Type-Safe Metric Resolution & NumberFormatException Elimination Verification** [LiveSegmentSheet resolves externalScrubDistance via distance_raw without string parsing; zero NumberFormatException on segment finish; 100% unit test pass rate.]
- **Requirement(s)**: `REQ-UI-340`
- **Component**: **LiveSegmentSheet Finish State Type-Safe Metric Resolution & NumberFormatException Elimination Verification**
- **Jira**: ``ATT-3108``
- **Test Description**:
  1. *LiveSegmentSheet Contract Tests (`LiveSegmentSheetContractTest.kt`)*:
  • Verify `LIveSegmentSheet.kt` maps `LiveSegmentStatus.FINISHED` to `liveSegment.staticData.summary.distance_raw.coerceAtLeast(liveSegment.liveData.distanceOnSegment_raw)` without calling `toDouble()`.
  2. *LiveSegmentSheet Finish Status Non-Crashing Unit Tests (`LiveSegmentSheetLayoutTest.kt`)*:
  • Instantiate `LiveSegment` with `LiveSegmentStatus.FINISHED`, `summary.distance = "766 m"`, and `distance_raw = 766.0`.
  • Verify evaluating `externalScrubDistance` does not throw `NumberFormatException` and equals `766.0`.
  • Verify handling when `summary.distance` has localized floating-point formatting (e.g. `"1,2 km"`).
  3. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with zero regressions.

### TST-UI-301: **Live Climb Cockpit Sheet Visual Design Optimization & Multi-State Compose Previews Verification** [LiveClimbSheet includes multi-state Compose @Previews, 68dp canvas height with ground baseline, and preserves all typography tokens; 100% unit test pass rate.]
- **Requirement(s)**: `REQ-UI-341`
- **Component**: **Live Climb Cockpit Sheet Visual Design Optimization & Multi-State Compose Previews Verification**
- **Jira**: ``ATT-3103``
- **Test Description**:
  1. *Preview Composable Contract Tests (`LiveClimbSheetLayoutTest.kt`)*:
  • Verify `LiveClimbSheet.kt` contains `@Preview` composables for `APPROACHING`, `ON_CLIMB`, and `FINISHED` states.
  • Verify Dark AMOLED preview declares `UI_MODE_NIGHT_YES` or AMOLED dark styling.
  2. *Graphical Canvas Contract Tests (`LiveClimbSheetLayoutTest.kt`)*:
  • Verify `ClimbProfileCanvas` renders the baseline ground line using `outlineVariant`.
  • Verify canvas height is at least `68.dp`.
  3. *Existing Token & Layout Invariants Preservation (`LiveClimbSheetLayoutTest.kt`)*:
  • Verify 32dp Terrain icon, bold titleLarge climb title, labelLarge route counter, headlineMedium monospace grade, bodyLarge remaining elevation, and 9-language localization parity remain 100% compliant.
  4. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with zero regressions.

### TST-UI-302: **Compose Text Input Stylus Handwriting Interception & Resilience Verification** [ComposeHandwritingResilience safely disables isStylusHandwritingSupported when SelectGesture is missing, neutralizing NoClassDefFoundError; 100% unit test pass rate.]
- **Requirement(s)**: `REQ-UI-342`
- **Component**: **Compose Text Input Stylus Handwriting Interception & Resilience Verification**
- **Jira**: ``ATT-3163``
- **Test Description**:
  1. *Safe Probe & Flag Mutation Unit Tests (`ComposeHandwritingResilienceTest.kt`)*:
  • Verify `ComposeHandwritingResilience.disableComposeStylusHandwriting()` updates `StylusHandwriting_androidKt.isStylusHandwritingSupported` to `false`.
  • Verify that when `isStylusHandwritingSupported` is false, Compose reports `isStylusHandwritingSupported() == false`.
  2. *Idempotent & Fault-Tolerant Initialization Tests (`ComposeHandwritingResilienceTest.kt`)*:
  • Verify `ComposeHandwritingResilience.install()` executes idempotently without throwing unhandled exceptions.
  3. *Localization Parity Audit (`TranslationParityTest.kt`)*:
  • Verify 100% parity across all 9 supported locales (0 new UI strings introduced).
  4. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with zero regressions.

### TST-UI-303: **Tracking Tab Live Segment & Climb BottomSheet Default Fully Expanded Presentation Contract Verification** []
- **Requirement(s)**: `REQ-UI-343`
- **Component**: **Tracking Tab Live Segment & Climb BottomSheet Default Fully Expanded Presentation Contract Verification**
- **Jira**: ``ATT-3176`, `ATT-3319``
- **Test Description**:
  1. *Initial BottomSheet State Evaluation Contract Tests (`SensorGridScreenClimbSuppressionTest.kt`)*:
  • Verify `SensorGridScreen.kt` initializes `initialSheetValue` to `SheetValue.Expanded` when `shouldShowBottomSheet == true` (replacing `PartiallyExpanded`).
  • Verify `scaffoldState` initializes `bottomSheetState` with `initialValue = initialSheetValue`.
  2. *Entity-Aware Reactive Transition Contract Tests (`SensorGridScreenClimbSuppressionTest.kt`)*:
  • Verify `activeEntityKey` derives unique keys for active segment (`stravaId`) and active climb (`id`, `routeIndex`, `name`).
  • Verify `LaunchedEffect(shouldShowBottomSheet, activeEntityKey)` invokes `scaffoldState.bottomSheetState.expand()` unconditionally when `shouldShowBottomSheet` is `true`, even if previously in `PartiallyExpanded`.
  • Verify `LaunchedEffect` continues to invoke `scaffoldState.bottomSheetState.hide()` when `shouldShowBottomSheet` is `false`.
  3. *Preserved Dynamic Drag & Inactive Tab Suppression Contract Tests (`SensorGridScreenClimbSuppressionTest.kt`)*:
  • Verify `sheetSwipeEnabled` incorporates `isTabActive`, `screenMode == ScreenMode.TRACKING`, and `showLiveSegments

### TST-UI-304: **Default Tracking Views Feature Configuration Matrix Initial Database Seeding Contract Verification** [Default tracking views on clean database creation enforce the targeted toggle matrix for Standard, Map, and Laps tabs across all activity types; addDefaultTab propagates explicit parameters; 100% unit test pass rate.]
- **Requirement(s)**: `REQ-UI-344`
- **Component**: **Default Tracking Views Feature Configuration Matrix Initial Database Seeding Contract Verification**
- **Jira**: ``ATT-3177``
- **Test Description**:
  1. *Clean Database Creation Seeding Unit Tests (`TrackingViewsDatabaseManagerTest.kt`)*:
  • Verify that when `TrackingViewsDbHelper.onCreate()` executes, the generated default tabs for each `ActivityType` match the canonical toggle matrix:
  - Standard Tab (`layoutNr = 1`): `showNavigationHints = 1`, `showLiveSegments = 1`, `showLiveClimbs = 1`, `showMap = 0`, `showElevationProfile = 0`, `showLapButton = 0`.
  - Map Tab (`layoutNr = 2`): `showNavigationHints = 1`, `showMap = 1`, `showElevationProfile = 1`, `showLiveSegments = 0`, `showLiveClimbs = 0`, `showLapButton = 0`.
  - Laps Tab (`layoutNr = 3`): `showLapButton = 1`, `showNavigationHints = 0`, `showLiveSegments = 0`, `showLiveClimbs = 0`, `showMap = 0`, `showElevationProfile = 0`.
  2. *API Parameter Passing Contract Tests (`TrackingViewsDatabaseManagerTest.kt`)*:
  • Verify `TrackingViewsDbHelper.addDefaultTab(...)` populates `SHOW_LIVE_CLIMBS` and `SHOW_NAVIGATION_HINTS` with the explicit boolean arguments provided rather than hardcoding `1`.
  3. *Living Design Guidelines Verification (`docs/design_guidelines.md`)*:
  • Verify Section 5.9 codifies the default tracking views configuration matrix.
  4. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with zero regressions.

### TST-UI-305: **Workout Recovery Dialog Layout Visibility, Start Interception Guard & Sticky Tracking State Verification** [Recovery dialog window parameters set to MATCH_PARENT/WRAP_CONTENT; tracking start intercepted when unfinalized session exists; SQLite predicates match and update NULL/0 finished states; sticky service syncs UI tracking mode; 100% unit test pass rate.]
- **Requirement(s)**: `REQ-UI-345`
- **Component**: **Workout Recovery Dialog Layout Visibility, Start Interception Guard & Sticky Tracking State Verification**
- **Jira**: ``ATT-3165``
- **Test Description**:
  1. *Dialog Window Bounds Contract Verification (`StartOrResumeDialogLayoutTest.kt`)*:
  • Verify `StartOrResumeDialog.onStart()` configures `dialog?.window?.setLayout(MATCH_PARENT, WRAP_CONTENT)`.
  2. *Start Tracking Interception & Unfinished Session Guard Unit Tests (`ControlTrackingViewModelTest.kt`)*:
  • Verify `ControlTrackingViewModel.onStartTracking()` checks `hasUnfinishedWorkout()` and `isTracking()`.
  • When unfinalized workout exists and not tracking, verify `ShowRecoveryDialog` event is emitted and `REQUEST_START_TRACKING` broadcast is NOT dispatched.
  • When no unfinalized workout exists or tracking is already active, verify `REQUEST_START_TRACKING` broadcast is dispatched.
  3. *Database Query & Finalization Predicate Tests (`WorkoutSummariesDatabaseManagerTest.kt`)*:
  • Verify `hasUnfinishedWorkout()` evaluates to `true` for records with `finished IS NULL` and `finished = 0`.
  • Verify `hasUnfinishedWorkout()` is not masked by subsequent finished workouts.
  • Verify `discardOrFinishUnfinishedWorkout()` updates records with `finished IS NULL` and `finished = 0` to `finished = 1`.
  • Verify `TrackerService.createNewWorkout()` inserts `finished = 0`.
  4. *Sticky Service Tracking State Sync Contract Tests (`BANALServiceRepositoryTest.kt`)*:
  • Verify `TrainingApplication.setTracking()` invokes `notifyTrackingStateChanged()`.
  • Verify `BANALServiceRepository` initializes `_trackingMode` with current application tracking mode.
  5. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with zero regressions.

### TST-UI-306: **Tracking Screen Sole Elevation Profile Canonical Adaptive Height & Layout Weight Decoupling Verification** [Surface unweighted without fillParentHeight; profile uses canonical adaptive height (70dp-200dp); sensor grid weight(1f, fill=false) preserved; 100% unit test pass rate.]
- **Requirement(s)**: `REQ-UI-346`
- **Component**: **Tracking Screen Sole Elevation Profile Canonical Adaptive Height & Layout Weight Decoupling Verification**
- **Jira**: ``ATT-3173``
- **Test Description**:
  1. *Elevation Profile Surface Constraint Contract Verification (`SensorGridScreenLayoutTest.kt`)*:
  • Verify `SensorGridScreen.kt` elevation profile `Surface` does NOT apply `Modifier.weight(1f)`.
  • Verify `Surface` declares `Modifier.fillMaxWidth()` and `padding(top = 8.dp)`.
  2. *Elevation Profile Parameter & Modifier Contract Verification (`SensorGridScreenLayoutTest.kt`)*:
  • Verify `ElevationProfile` does NOT pass `fillParentHeight = !state.mapState.showMap`.
  • Verify `ElevationProfile` receives `modifier = Modifier.fillMaxWidth()` (not `Modifier.fillMaxSize()`).
  3. *Sensor Grid Weight & Scrollability Preservation Tests (`SensorGridScreenLayoutTest.kt`)*:
  • Verify sensor grid `Column` retains `Modifier.weight(1f, fill = false)`.
  • Verify elevation profile rendering is guarded by `state.showElevationProfile && state.pathPoints.size >= 2`.
  4. *Adaptive Height Calculation Invariant Verification (`ElevationProfileLayoutTest.kt`)*:
  • Verify `calculateElevationProfileHeight(range)` produces values in $[70\text{dp}, 200\text{dp}]$.
  • Verify default `fillParentHeight = false` in `ElevationProfile` attaches `.height(totalCanvasHeight)`.
  5. *Localization Parity Audit (`TranslationParityTest.kt`)*:
  • Verify 100% parity across all 9 supported locales (0 new UI strings introduced).
  6. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with zero regressions.

### TST-UI-307: **RoutesScreen and RouteDetailSheet Route Preview Polyline Styling & Selection State Decoupling Verification** [Route preview reflects actual selection and active navigation state; toggling selection dynamically updates polyline appearance; 100% unit test pass rate.]
- **Requirement(s)**: `REQ-UI-347`
- **Component**: **RoutesScreen and RouteDetailSheet Route Preview Polyline Styling & Selection State Decoupling Verification**
- **Jira**: ``ATT-3172``
- **Test Description**:
  1. *RoutesScreen Dynamic Navigation State Contract Verification (`RoutesScreenContractTest.kt`)*:
  • Verify `RoutesScreen.kt` passes `isActiveNavigation = (selectedRoute.summary.id == activeNavigatedRouteId)` (does not hardcode `isActiveNavigation = true`).
  • Verify `RoutesViewModel` exposes `activeNavigatedRouteId: StateFlow<Long?>`.
  2. *RouteDetailSheet Decoupling Contract Verification (`RouteDetailSheetContractTest.kt`)*:
  • Verify `RouteDetailSheet.kt` does NOT hardcode `isActiveNavigation = true`.
  • Verify `RouteDetailSheet` defaults `isActiveNavigation = false` and propagates `routeWithPath.toMapRoute(isActiveNavigation)`.
  3. *Three-Tier Polyline Visual Resolution Unit Tests (`MapRouteActiveNavigationTest.kt`)*:
  • Verify that when `isActiveNavigation = false` and `isSelected = false`, `color` is `RouteUnselected`, `width` is `6f`, and pattern is dashed.
  • Verify that when `isActiveNavigation = false` and `isSelected = true`, `color` is `RouteSelected`, `width` is `10f`, and pattern is dashed.
  • Verify that when `isActiveNavigation = true`, `color` is `RouteActiveNavigation`, `width` is `26f`, and pattern is solid.
  4. *Localization Parity Audit (`TranslationParityTest.kt`)*:
  • Verify 100% parity across all 9 supported locales (0 new UI strings introduced).
  5. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with zero regressions.

### TST-UI-308: **Unified Top-of-Tab Navigation Hints Overlay Anchoring Contract & Reversion Verification** [Navigation hints overlay anchored at top of root tracking container across all tabs; map container decoupled from navigation overlay; TurnPromptBanner, ReturnNavigationHud, and ForkDecisionCard encapsulation preserved; 100% unit test pass rate.]
- **Requirement(s)**: `REQ-UI-348`
- **Component**: **Unified Top-of-Tab Navigation Hints Overlay Anchoring Contract & Reversion Verification**
- **Jira**: ``ATT-3170``
- **Test Description**:
  1. *Root Container Overlay Placement Contract Tests (`SensorGridScreenRouteIntegrationTest.kt`)*:
  • Verify `SensorGridScreen.kt` renders `NavigationHintsOverlay` anchored at `Alignment.TopCenter` of the root tracking `Box` container.
  • Verify root `NavigationHintsOverlay` is conditioned solely on `state.showNavigationHints` (independent of `state.mapState.showMap`).
  2. *Map Container Decoupling Contract Tests (`SensorGridScreenRouteIntegrationTest.kt`)*:
  • Verify the map container `Box` (`if (state.mapState.showMap)`) does NOT contain `NavigationHintsOverlay`.
  3. *Navigation Hints Encapsulation Verification (`SensorGridScreenRouteIntegrationTest.kt`)*:
  • Verify `NavigationHintsOverlay` encapsulates `TurnPromptBanner`, `ReturnNavigationHud`, and `ForkDecisionCard`.
  4. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with zero regressions.

### TST-UI-309: **Active Sensor Deletion Disconnection Lifecycle and Defensive Telemetry Boundary Filtering Verification** [Deleted/orphaned sensors never appear as ghost tiles or "New Sensor"; BLE/ANT+ connection terminated on delete; missing device edit dialog automatically dismisses; 100% unit test pass rate.]
- **Requirement(s)**: `REQ-UI-349`
- **Component**: **Active Sensor Deletion Disconnection Lifecycle and Defensive Telemetry Boundary Filtering Verification**
- **Jira**: ``ATT-3169``
- **Test Description**:
  1. *ViewModel Defensive Filtering Unit Tests (`ControlTrackingViewModelTest.kt`)*:
  • Verify `remoteDevices` drops active remote IDs absent from `allDevicesFromDb`.
  • Verify zero `DeviceType.DUMMY` or `"New Sensor"` items emitted when orphaned IDs are passed.
  • Verify legitimate devices in `allDevicesFromDb` continue to map correctly to `RemoteDeviceUIData`.
  2. *Repository Deletion Broadcast Unit Tests (`DeviceDataRepositoryTest.kt`)*:
  • Verify `deleteDevice(deviceId)` broadcasts `BANALService.PAIRING_CHANGED` with `paired = false` and matching `deviceId`.
  • Verify `devicesDatabaseManager.deleteDevice(deviceId)` is invoked.
  3. *Dialog Missing Snapshot Auto-Dismiss Tests (`EditDeviceDialogTest.kt` / `EditDeviceViewModelTest.kt`)*:
  • Verify `EditDeviceDialog` calls `onDismiss()` when snapshot resolves to `null`.
  4. *DeviceManager Null-Safety Verification (`DeviceManagerTest.kt`)*:
  • Verify `removeAndStopRemoteDevice` executes safely without throwing when device is not in `mMyRemoteDevices`.
  5. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with zero regressions.

### TST-UI-310: WorkoutSummariesTabbedScreenTest, WorkoutSummariesListFragmentTest [Verified]
- **Requirement(s)**: Verify that `SnackbarHost` in `WorkoutSummariesTabbedScreen` (and `WorkoutSummariesListFragment`) applies `Modifier.navigationBarsPadding()` to ensure snackbars and action buttons render above system navigation bars with zero occlusion.
- **Component**: `WorkoutSummariesTabbedScreenTest`, `WorkoutSummariesListFragmentTest`
- **Jira**: `**Post-Workout Low Battery Alert Snackbar Navigation Bar Inset Padding Verification.**`
- **Test Description**: `REQ-UI-350`

### TST-UI-311: **Multi-Dimensional Sensor Filtering by Protocol, Category, and Battery Level with Deep-Link Navigation Verification** [Multi-dimensional sensor filtering by protocol, category, and battery level; deep-link from low battery notification navigates reliably on cold and hot launches to pre-filtered critical battery sensors; 100% unit test pass rate.]
- **Requirement(s)**: `REQ-UI-351`
- **Component**: **Multi-Dimensional Sensor Filtering by Protocol, Category, and Battery Level with Deep-Link Navigation Verification**
- **Jira**: ``ATT-3166``
- **Test Description**:
  1. *Filter Criteria Model Unit Tests (`DeviceFilterCriteriaTest.kt`)*:
  • Verify `matches` filters by protocol (`ANT_PLUS`, `BLUETOOTH_LE`).
  • Verify `matches` filters by category set (`Set<DeviceType>`).
  • Verify `matches` filters by battery level: `CRITICAL_ONLY` matches $0..20\%$, rejects $> 20\%$ and unknown ($-1$); `NORMAL` matches $> 20\%$.
  • Verify `activeFilterCount`, `isEmpty`, `isNotEmpty`.
  2. *ViewModel Reactive Filtering Unit Tests (`DeviceListViewModelFilterTest.kt`)*:
  • Verify `getFilteredDevices` dynamically filters `mergedDevices` across `CONNECTED`, `PAIRED`, and `ALL_KNOWN` tabs when criteria change.
  3. *UI & Filter Bottom Sheet Contract Tests (`DevicesTabbedScreenFilterTest.kt`)*:
  • Verify `FilterActionButton` renders in header and displays active badge count.
  • Verify `ActiveDeviceFilterChipsRow` displays removable chips and clears criteria.
  • Verify `DeviceFilterBottomSheet` renders protocol, category, and battery sections.
  4. *Deep-Link & Navigation Race Resolution Tests (`NotificationDeepLinkTest.kt`)*:
  • Verify `SensorBatteryNotificationManager` sets `EXTRA_FILTER_CRITICAL_BATTERY = true`.
  • Verify `MainActivityWithNavigation` queues `mPendingDrawerItemId` when `navController == null` and executes navigation to `drawer_my_sensors` upon `onNavControllerReady`.
  • Verify `BatteryFilter.CRITICAL_ONLY` is applied when launched with extra.
  5. *Localization Parity Audit (`TranslationParityTest.kt`)*:
  • Verify 100% parity across all 9 supported application locales (EN, DE, ES, FR, IT, JA, NL, PL, PT).
  6. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with zero regressions.

### TST-UI-312: **LiveClimbSheet Design System Harmonization, ElevationProfile Gradient Shading, Adaptive Height Scaling & Degenerate Geometry Verification** [LiveClimbSheet reuses ElevationProfile with gradient shading and adaptive height scaling based on climb elevation gain; chips and badges delegate to MetricBadge; degenerate geometries guarded; 100% unit test pass rate.]
- **Requirement(s)**: `REQ-UI-352`
- **Component**: **LiveClimbSheet Design System Harmonization, ElevationProfile Gradient Shading, Adaptive Height Scaling & Degenerate Geometry Verification**
- **Jira**: ``ATT-3264`, `ATT-3316``
- **Test Description**:
  1. *ElevationProfile Gradient Shading & Degenerate Geometry Unit Tests (`ElevationProfileClimbShadingTest.kt`)*:
  • Verify `getClimbGradeColor` maps grade percentages to canonical color bands: $<3\%$ Green, $3-6\%$ Light Green, $6-9\%$ Amber, $9-12\%$ Orange, $12-20\%$ Red, $>20\%$ Dark.
  • Verify `calculateElevationBounds` enforces `range.coerceAtLeast(1.0)` on flat altitudes.
  • Verify `ElevationProfile` handles `pathPoints.isEmpty()` and `pathPoints.size == 1` gracefully without throwing `NoSuchElementException`.
  • Verify `fixedHeight` overrides `totalCanvasHeight` when supplied.
  2. *MetricBadge Shape Parameterization Unit Tests (`MetricBadgeShapeTest.kt`)*:
  • Verify default `shape` evaluates to `RoundedCornerShape(4.dp)`.
  • Verify custom shapes (e.g. `RoundedCornerShape(6.dp)` and `RoundedCornerShape(12.dp)`) compile and apply cleanly.
  3. *LiveClimbSheet Layout & Contract Tests (`LiveClimbSheetLayoutTest.kt`, `LiveClimbAdaptiveHeightTest.kt`)*:
  • Verify `LiveClimbSheet` invokes `ElevationProfile` with `climbGradientShading = true` and without hardcoded `fixedHeight` override.
  • Verify `ElevationProfile` calculates height adaptively from $70\text{dp}$ to $200\text{dp}$ based on climb elevation gain via `calculateElevationProfileHeight(range)`.
  • Verify `ClimbProfileCanvas` is completely excised from `LiveClimbSheet.kt`.
  • Verify `ClimbCategoryChip` and `ClimbStatusBadge` delegate to `MetricBadge`.
  • Verify multi-state Compose `@Preview` composables are present and render.
  4. *Localization Parity Audit (`TranslationParityTest.kt`)*:
  • Verify 100% translation parity across EN, DE, ES, FR, IT, JA, NL, PL, PT.
  5. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with zero regressions.

### TST-UI-313: **Persistent Missing ANT+ Adapter Dialog Suppression, DataStore Persistence, and Advanced Settings Warning Reset Verification** [Persistent suppression preference in DataStore, broadcast guard in MainActivityWithNavigation, Advanced Settings toggle in sensor search section, 100% localization parity, and 100% unit test pass rate.]
- **Requirement(s)**: `REQ-UI-353`
- **Component**: **Persistent Missing ANT+ Adapter Dialog Suppression, DataStore Persistence, and Advanced Settings Warning Reset Verification**
- **Jira**: ``ATT-3220`, `ATT-3454``
- **Test Description**:
  1. *DataStore Preference Serialization & Default Reset Unit Tests (`TuningPreferencesDataStoreTest.kt`)*:
  • Verify `TuningConfig` initializes with `suppressAntMissingAdapterDialog = false` and `showAntMissingAdapterWarnings = true`.
  • Verify `setSuppressAntMissingAdapterDialog(true)` persists and emits `true` via `suppressAntMissingAdapterDialogFlow`.
  • Verify `setSuppressAntMissingAdapterDialog(false)` persists and emits `false`.
  • Verify `resetToDefaults()` restores `suppressAntMissingAdapterDialog = false`.
  2. *Broadcast Guard Unit Tests (`AntMissingAdapterDialogSuppressionTest.kt`)*:
  • Verify `showANTAdapterMissingDialog()` transitions `antDialogState` to `AntDialogState.MissingAdapter` when unsuppressed.
  • Verify `showANTAdapterMissingDialog()` discards the broadcast and `antDialogState` remains `null` when suppressed.
  • Verify `antDialogState != null` prevents duplicate emissions.
  3. *Advanced Settings Toggle Contract Tests (`AntMissingAdapterDialogSuppressionTest.kt`)*:
  • Verify `SensorSearchTuningSection` renders `TuningToggleItem` with `checked = showAntMissingAdapterWarnings` under search behavior (`prefs_search_behavior_title`) (ATT-3454).
  • Verify `SensorsGpsFilterSection` no longer declares `showAntMissingAdapterWarnings` or renders the toggle item (ATT-3454).
  • Verify toggling invokes `onShowAntMissingAdapterWarningsChange`.
  • Verify `AdvancedTuningDialog` and `AdvancedTuningScreen` load `showAntMissingAdapterWarnings` from `tuningConfigFlow` and write back inverted value to `suppressAntMissingAdapterDialog` on save.
  • Verify all files in `ui.settings.tuning` remain strictly < 400 lines of code (`AdvancedTuningModularityTest.kt`).
  4. *Localization Parity Audit (`TranslationParityTest.kt`)*:
  • Verify `tuning_show_ant_missing_adapter_warnings_title` and `tuning_show_ant_missing_adapter_warnings_desc` exist and are non-empty across all 9 supported application locales (EN, DE, ES, FR, IT, JA, NL, PL, PT).
  5. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with zero regressions.

### TST-UI-314: **Whole Integer Cycling Power Formatting, IntegerFormatter Mathematical Rounding, and Cadence Consolidation Verification** [Filtered cycling power formats as whole rounded integer; IntegerFormatter and CadenceFormatter perform mathematical half-up rounding; null and invalid inputs display dashes; 100% unit test pass rate.]
- **Requirement(s)**: `REQ-UI-354`
- **Component**: **Whole Integer Cycling Power Formatting, IntegerFormatter Mathematical Rounding, and Cadence Consolidation Verification**
- **Jira**: ``ATT-3229``
- **Test Description**:
  1. *IntegerFormatter Mathematical Rounding Unit Tests (`IntegerFormatterTest.kt`)*:
  • Verify whole numbers (`250.0`, `150L`, `85`) format as `"250"`, `"150"`, `"85"`.
  • Verify half-up mathematical rounding: `245.2` -> `"245"`, `245.49` -> `"245"`, `245.5` -> `"246"`, `245.8` -> `"246"`.
  • Verify Cadence fraction rounding: `89.1` -> `"89"`, `89.8` -> `"90"`.
  • Verify Heart Rate fraction rounding: `154.2` -> `"154"`, `154.7` -> `"155"`.
  • Verify zero handling: `0`, `0.0`, `-0.0` -> `"0"`.
  • Verify edge cases: `null` -> `"--"`, `Double.NaN` -> `"--"`, `Double.POSITIVE_INFINITY` -> `"--"`, `Double.NEGATIVE_INFINITY` -> `"--"`.
  2. *CadenceFormatter Rounding Unit Tests (`CadenceFormatterTest.kt`)*:
  • Verify delegation to `IntegerFormatter` with identical rounding and null handling.
  3. *SensorType Formatter Contract Unit Tests (`SensorTypeFormatterTest.kt`)*:
  • Verify `SensorType.POWER.getMyFormatter()` is an instance of `IntegerFormatter`.
  • Verify `SensorType.POWER.getMyFormatter().format(250.37)` returns `"250"`.
  • Verify `SensorType.CADENCE.getMyFormatter().format(89.8)` returns `"90"`.
  • Verify `SensorType.HR.getMyFormatter().format(154.6)` returns `"155"`.
  4. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with zero regressions.

### TST-UI-315: **Active Route Full Elevation Profile with Map-Matched Position Marker, Categorized Climbs & Per-Tab Rolling Window Verification** [Live tracking elevation profile switches dynamically to active route, rendering complete route profile or rolling window with 15% athlete centering, map-matched marker distance, and categorized climbs; 100% unit test pass rate.]
- **Requirement(s)**: `REQ-UI-355`
- **Component**: **Active Route Full Elevation Profile with Map-Matched Position Marker, Categorized Climbs & Per-Tab Rolling Window Verification**
- **Jira**: ``ATT-3199`, `ATT-3317``
- **Test Description**:
  1. *Route Elevation Profile Selection & Fallback Unit Tests (`TrackingElevationProfileResolutionTest.kt`)*:
  • Verify that when an active navigated route with altitude data is present, the resolved path points match the full route path (0 km to total distance) and climbs match the route climbs.
  • Verify that when no route is active (`isActiveNavigation == false` across all routes), resolved path points match `state.pathPoints` and climbs are empty.
  • Verify that when an active route has flat 0.0 altitude data across all points, resolved path points fall back to `state.pathPoints` and climbs are empty.
  • Verify that when an active route has fewer than 2 path points, resolved path points fall back to `state.pathPoints`.
  2. *Orthogonal Map-Matched Progress Projection Tests (`TrackingElevationProfileResolutionTest.kt`)*:
  • Verify that when `currentLocation` is at the route start coordinate, the resolved marker distance is approximately $0.0\text{m}$.
  • Verify that when `currentLocation` is midway along the route, the resolved marker distance matches the along-track distance of the projected segment.
  • Verify that when `currentLocation == null`, the resolved marker distance defaults to $0.0\text{m}$.
  • Verify that for free tracking (fallback mode), the marker distance is `state.pathPoints.lastOrNull()?.distance`.
  3. *Per-Tab Rolling Window & Lookahead Slicing Unit Tests (`TrackingElevationProfileResolutionTest.kt`)*:
  • Verify that when `scope == ElevationProfileScope.ROLLING_WINDOW` and lookahead is 2,000m, $D_{\text{behind}} = \frac{3}{17} \times 2000 \approx 352.9\text{m}$, centering the athlete marker at 15% from the left edge.
  • Verify rolling window extraction and interpolation across presets: 500m, 1,000m, 2,000m, 5,000m, 10,000m.
  • Verify start boundary clamping: when $alongTrackDist < D_{\text{behind}}$, window start is clamped to 0.0m.
  • Verify end boundary clamping: when $alongTrackDist + D_{\text{forward}} > D_{\text{route}}$, window end is clamped to $D_{\text{route}}$.
  • Verify categorized climbs intersecting the rolling window are retained, and non-intersecting climbs are excluded.
  4. *Database Schema Migration & Tab Persistence Unit Tests (`TrackingViewsDatabaseManagerTest.kt`)*:
  • Verify upgrade to DB version 13 adds `ElevationProfileScope` (default 0) and `ElevationProfileLookahead` (default 2000).
  • Verify per-tab persistence and independent retrieval across multiple tabs.
  5. *UI Contract, Toggle & Localization Audit Tests (`SensorGridScreenElevationProfileTest.kt`, `TranslationParityTest.kt`)*:
  • Verify `ElevationProfile` renders with non-zero start distance in rolling window mode.
  • Verify scope switch and lookahead chips in configuration mode trigger callbacks.
  • Verify 100% localization parity across EN, DE, ES, FR, IT, JA, NL, PL, PT.
  6. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with zero regressions.

### TST-UI-316: **Workout Start & In-Ride Low Sensor Battery Alert Snackbars with Navigation Bar Inset Compliance & Semantic Severity Color Tiering Verification** [Non-blocking low battery snackbars displayed on workout start and in-ride live tracking with semantic severity color tiering (amber warning, red critical), instantaneous critical preemption, navigation bar insets, and lap button clearance; 100% unit test pass rate.]
- **Requirement(s)**: `REQ-UI-356`
- **Component**: **Workout Start & In-Ride Low Sensor Battery Alert Snackbars with Navigation Bar Inset Compliance & Semantic Severity Color Tiering Verification**
- **Jira**: ``ATT-3313`, `ATT-3451``
- **Test Description**:
  1. *Workout Start & In-Ride Severity Classification (`SensorBatteryAlertTest.kt`)*:
  • Verify that when starting a workout or in-ride, sensors with battery $\le 10\%$ are assigned `BatteryAlertSeverity.CRITICAL`.
  • Verify that sensors with battery in $11\dots 20\%$ are assigned `BatteryAlertSeverity.WARNING`.
  • Verify that when all sensors have battery $> 20\%$, zero alerts are dispatched.
  2. *In-Ride Battery Drop & Critical Escalation (`SensorBatteryAlertTest.kt`)*:
  • Verify that when an active session sensor drops to $\le 20\%$ during an active ride, `WorkoutNavigationEvents.lowBatteryAlert` is triggered with `WARNING`.
  • Verify that subsequent reports at $18\%$ or $15\%$ for the same sensor are suppressed by session deduplication.
  • Verify that when the sensor subsequently drops to $\le 10\%$, an escalated `CRITICAL` alert is triggered.
  • Verify duplicate reports at $\le 10\%$ are suppressed.
  3. *Snackbar Preemption & Typed Visuals (`SensorBatteryAlertTest.kt`)*:
  • Verify that when a `CRITICAL` alert arrives while a snackbar is active, `currentSnackbarData?.dismiss()` is executed immediately.
  • Verify `BatterySnackbarVisuals` carries `severity` and satisfies `SnackbarVisuals` interface.
  4. *Semantic Warning Colors & WCAG Contrast (`SensorBatteryAlertColorsTest.kt`)*:
  • Verify `LightWarningContainer`, `LightOnWarningContainer`, `LightWarningAction`, `DarkWarningContainer`, `DarkOnWarningContainer`, and `DarkWarningAction`.
  • Assert WCAG contrast ratios $\ge 4.5:1$ (body/action) and $\ge 7:1$ (container text) across both Light and Dark modes.
  5. *Tracking & Aftermath Screen SnackbarHost Dynamic Styling Contract Tests (`TrackingTabsScreenLayoutTest.kt`)*:
  • Verify `TrackingTabsScreen.kt` and `WorkoutSummariesTabbedScreen.kt` dynamically resolve container and content colors via `getBatterySnackbarColors(visuals.severity)` rather than static `primaryContainer`.
  • Verify fallback to standard Material 3 colors for non-battery snackbars.
  • Verify `SnackbarHost` applies `Modifier.navigationBarsPadding()` and `LapButton` clearance.
  • Verify `WorkoutNavigationEvents.consumeLowBatteryAlert()` is called upon alert handling.
  6. *Localization Parity Audit (`TranslationParityTest.kt`)*:
  • Verify 100% parity across EN, DE, ES, FR, IT, JA, NL, PL, PT for all sensor battery alert strings.
  7. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with zero regressions.

### TST-UI-317: **Dedicated Full-Screen Advanced Settings Screen Architecture, Zero-Dismissal Scrolling, and Lifecycle Category Reorganization Verification** [Advanced Settings migrated to dedicated full-screen Compose destination with zero scroll dismissals, drawer and dialogs route cleanly, categories reorganized into 3-phase athletic lifecycle, modularity < 400 lines preserved; 100% unit test pass rate.]
- **Requirement(s)**: `REQ-UI-357`
- **Component**: **Dedicated Full-Screen Advanced Settings Screen Architecture, Zero-Dismissal Scrolling, and Lifecycle Category Reorganization Verification**
- **Jira**: ``ATT-3344``
- **Test Description**:
  1. *Dedicated Full-Screen Screen Architecture Contract Tests (`AdvancedTuningScreenTest.kt`)*:
  • Verify `AdvancedTuningScreen` is hosted within a Material 3 `Scaffold`.
  • Verify `TopAppBar` renders back navigation arrow calling `onNavigateBack()`, localized title `R.string.advanced_tuning_title`, and reset action button.
  • Verify sticky bottom bar hosts Save (`R.string.save`) and Cancel buttons (`AppDialogActions.SaveCancel`).
  • Verify tapping Back or invoking system back pops backstack without saving uncommitted changes.
  • Verify tapping Save atomically persists modifications and pops backstack.
  2. *Zero-Dismissal Full-Height Scroll Verification (`AdvancedTuningScreenTest.kt`)*:
  • Verify content uses full-height vertical scroll (`Modifier.fillMaxSize().verticalScroll(rememberScrollState())`).
  • Verify `AdvancedTuningScreen` is NOT hosted inside `ModalBottomSheet` or `BottomSheetScaffold`, guaranteeing zero accidental drag dismissals.
  3. *Navigation & Route Contract Tests (`AdvancedTuningNavigationTest.kt`)*:
  • Verify `NavRoutes.ADVANCED_SETTINGS == "advanced_settings"`.
  • Verify `NavRoutes.fromDrawerItemId(R.id.drawer_advanced_tuning) == NavRoutes.ADVANCED_SETTINGS`.
  • Verify `NavRoutes.toDrawerItemId(NavRoutes.ADVANCED_SETTINGS) == R.id.drawer_advanced_tuning`.
  • Verify `NavRoutes.toBottomSheetType(R.id.drawer_advanced_tuning) == null`.
  • Verify `ATrainingTrackerApp.kt` registers `composable(NavRoutes.ADVANCED_SETTINGS)` in `NavHost`.
  • Verify `DisplaySettingsDialog.kt` tuning link navigates to `NavRoutes.ADVANCED_SETTINGS`.
  4. *Lifecycle Category Reorganization Contract Tests (`AdvancedTuningAccordionTest.kt`)*:
  • Verify `TuningSection` enum sequence: `NAVIGATION`, `COCKPIT_TYPOGRAPHY`, `BATTERY_SAVER`, `SENSORS_GPS`, `SENSOR_SEARCH`, `WORKOUT_MASKS_CARDS`, `AFTERMATH_ANALYSIS`.
  • Verify `AdvancedTuningAccordion` renders sections in this exact canonical sequence.
  5. *Preservation of Modularity & Invariants (`AdvancedTuningModularityTest.kt`)*:
  • Verify every file in `ui.settings.tuning` remains strictly < 400 lines of code.
  • Verify all 25+ parameter DataStore keys, default values, and clamping bounds remain identical.
  6. *9-Language Localization Audit (`TranslationParityTest.kt`)*:
  • Verify all tuning string resources exist across EN, DE, ES, FR, IT, JA, NL, PL, PT.
  7. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with zero regressions.

### TST-UI-318: **Dedicated Workout Route Clustering Section in Advanced Settings, Single Source of Truth, and Modularity Preservation Verification** [Route clustering configuration consolidated in Advanced Settings accordion; single source of truth shared composable eliminates code duplication; atomic factory reset supported; modularity < 400 lines preserved; 100% unit test pass rate.]
- **Requirement(s)**: `REQ-SET-075`
- **Component**: **Dedicated Workout Route Clustering Section in Advanced Settings, Single Source of Truth, and Modularity Preservation Verification**
- **Jira**: ``ATT-3355``
- **Test Description**:
  1. *Route Clustering Section & Controls Verification (`ClusterTuningSectionTest.kt`)*:
  • Verify that `ClusterTuningSection` renders master sensitivity slider and controls overall tolerances.
  • Verify details switch reveals individual sliders: Endpoint Tolerance (10..500m), Apex Tolerance (10..500m), Distance Tolerance (0.01..0.20), Altitude Position Tolerance (10..500m).
  • Verify switches: "Sportart berücksichtigen" (`useSportTypeForClustering`) and "Höhen-Extrema berücksichtigen" (`useAltitudePosForClustering`).
  • Verify unit formatting adapts to Metric (m, %) and Imperial (mi, %).
  2. *Lifecycle Category Ordering & Subtitle Formatter (`AdvancedTuningAccordionTest.kt`)*:
  • Verify `ROUTE_CLUSTERING` is positioned in Phase C between `WORKOUT_MASKS_CARDS` and `AFTERMATH_ANALYSIS`.
  • Verify `TuningSubtitleFormatter.formatClusterTuningSubtitle(...)` summarizes active tolerances concisely.
  3. *DataStore Persistence & Factory Reset (`TuningPreferencesDataStoreTest.kt`)*:
  • Verify `TuningConfig` initializes with canonical defaults (200m, 400m, 0.20, 400m, true, true).
  • Verify modifications persist to DataStore and SharedPreferences.
  • Verify "Auf Standardwerte zurücksetzen" restores all 6 values to factory defaults.
  4. *Single Source of Truth & DRY Call-Site Contracts (`ClusterTuningContractTest.kt`)*:
  • Verify `ClusterTuningScreen.kt` and `ImportBackupTabsScreen.kt` delegate to `ClusterTuningContent.kt` with zero UI code duplication.
  • Verify contextual recalculation button remains functional in `ClusterTuningScreen.kt`.
  5. *Modularity & File Size Limit Enforcement (`AdvancedTuningModularityTest.kt`)*:
  • Verify every file in `ui/settings/tuning/` strictly obeys `< 400` lines of code.
  6. *9-Language Localization Audit (`TranslationParityTest.kt`)*:
  • Verify 100% translation coverage for `tuning_cat_route_clustering` and associated tokens across all 9 supported locales.
  7. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with zero regressions.

### TST-UI-319: **Dynamic Cockpit Viewport Allocation, Asymmetric Map Weight Expansion & Bounded Sensor Grid Containment Verification** [Tracking map expands flexibly with weight(1f, fill = true) to consume 100% of remaining vertical space above elevation profile, eliminating all dead white space; sensor grid wraps content and is capped at 50% screen height with scrolling; map-disabled fallback preserved; 100% unit test pass rate.]
- **Requirement(s)**: `REQ-UI-358`
- **Component**: **Dynamic Cockpit Viewport Allocation, Asymmetric Map Weight Expansion & Bounded Sensor Grid Containment Verification**
- **Jira**: ``ATT-3343``
- **Test Description**:
  1. *Compose Container & AST Layout Structure Tests (`SensorGridScreenLayoutTest.kt`)*:
  • Verify `SensorGridScreen.kt` declares `BoxWithConstraints` wrapping the cockpit layout.
  • Verify that when `state.mapState.showMap == true`, the sensor grid Column applies `heightIn(max = ... * 0.5f)` and omits `weight(...)`.
  • Verify that when `state.mapState.showMap == false`, the sensor grid Column declares `weight(1f, fill = false)`.
  • Verify that the map Box declares `Modifier.fillMaxWidth().weight(1f, fill = true)`.
  2. *Geometry & Layout Constraint Unit Verification (`SensorGridScreenLayoutTest.kt`)*:
  • Verify available viewport height calculation and allocation under simulated constraints.
  • Verify 100% consumption of remaining vertical space between sensor grid and elevation profile with zero dead white space.
  • Verify sensor grid scrolling containment when row count is large (e.g. 5+ rows) with map enabled.
  • Verify adaptive elevation profile height preservation ($70\text{dp} \dots 200\text{dp}$) and map expansion when profile is disabled.
  3. *9-Language Localization Audit (`TranslationParityTest.kt`)*:
  • Verify 100% parity across EN, DE, ES, FR, IT, JA, NL, PL, PT.
  4. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with zero regressions.

### TST-UI-320: **Live Segment and Climb Bottom Sheet Gesture Re-Expansion, Peek Tap-to-Expand, and Inactive Touch Neutrality Verification** [Upward bottom swipe gesture and peek tap smoothly re-expand dismissed LiveSegment/Climb bottom sheets to full height; touch neutrality preserved when no entity is active; 100% unit test pass rate.]
- **Requirement(s)**: `REQ-UI-359`
- **Component**: **Live Segment and Climb Bottom Sheet Gesture Re-Expansion, Peek Tap-to-Expand, and Inactive Touch Neutrality Verification**
- **Jira**: ``ATT-3341``
- **Test Description**:
  1. *Architectural & AST Layout Structure Tests (`SensorGridBottomSheetGestureTest.kt`)*:
  • Verify `SensorGridScreen.kt` instantiates `rememberCoroutineScope` for sheet expansion control.
  • Verify bottom-edge gesture target is conditionally composed when `shouldShowBottomSheet == true` and `scaffoldState.bottomSheetState.currentValue != SheetValue.Expanded`.
  • Verify upward drag gesture (`detectVerticalDragGestures`) triggers `scaffoldState.bottomSheetState.expand()`.
  • Verify tap-to-expand handler on peek area triggers `scaffoldState.bottomSheetState.expand()`.
  2. *Touch Neutrality & Invariant Verification (`SensorGridBottomSheetGestureTest.kt`)*:
  • Verify gesture interceptor is completely suppressed / not composed when `shouldShowBottomSheet == false`.
  • Verify `initialSheetValue` evaluates to `SheetValue.Expanded` when `shouldShowBottomSheet == true`.
  • Verify `LaunchedEffect(shouldShowBottomSheet, activeEntityKey)` preserves unconditional initial expansion.
  3. *9-Language Localization Audit (`TranslationParityTest.kt`)*:
  • Verify 100% localization parity across EN, DE, ES, FR, IT, JA, NL, PL, PT (0 new strings introduced).
  4. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with zero regressions.

### TST-UI-321: **Favorite Routes Cluster Tuning Elimination, Advanced Settings Recalculate Action & Material 3 Confirmation Guard Verification** [Cluster tuning removed from Favorite Routes header; dedicated recalculate action added to Advanced Settings with Material 3 blocking confirmation AlertDialog; decoupled coroutine execution with progress indication; modularity < 400 lines preserved; 100% unit test pass rate.]
- **Requirement(s)**: `REQ-SET-075`
- **Component**: **Favorite Routes Cluster Tuning Elimination, Advanced Settings Recalculate Action & Material 3 Confirmation Guard Verification**
- **Jira**: ``ATT-3452``
- **Test Description**:
  1. *Favorite Routes Header & Screen Cleanup Contract Tests (`ClusterTuningContractTest.kt`)*:
  • Verify that `WorkoutClustersTabsScreen.kt` does NOT declare `onTuneClick` parameter or render a tuning settings icon in the top app bar.
  • Verify that `WorkoutClustersScreen.kt` does NOT declare `isTuning` state or compose `ClusterTuningScreen`.
  2. *Advanced Settings Recalculate Action & Material 3 Confirmation Guard (`ClusterTuningContractTest.kt`)*:
  • Verify that `ClusterTuningSection.kt` renders a dedicated action button referencing `@string/cluster_tuning_recalculate_button`.
  • Verify that tapping the button triggers a modal `AlertDialog` referencing warning message `@string/cluster_tuning_warning`.
  • Verify that the dialog specifies `containerColor = MaterialTheme.colorScheme.surface` and `tonalElevation = 0.dp`.
  • Verify that the confirm button is styled with `MaterialTheme.colorScheme.error` and dismiss button references `@string/cancel`.
  3. *Lifecycle Decoupling, Concurrency & Double-Tap Prevention (`ClusterTuningContractTest.kt`)*:
  • Verify that `ClusterTuningSection.kt` collects `migrationStatus` from `WorkoutClusterRepository`.
  • Verify that when `migrationStatus != null`, the recalculate button is disabled and displays `CircularProgressIndicator` alongside `@string/cluster_tuning_recalculating`.
  • Verify that tolerance sliders and switches in `ClusterTuningContent` are disabled when `migrationStatus != null`.
  4. *Modularity & File Size Limit Enforcement (`AdvancedTuningModularityTest.kt`)*:
  • Verify that all source files in `ui.settings.tuning` remain strictly `< 400` lines of code.
  5. *9-Language Localization Audit (`TranslationParityTest.kt`)*:
  • Verify 100% translation coverage for `cluster_tuning_recalculate_button`, `cluster_tuning_warning`, `cluster_tuning_recalculating`, and `cancel` across EN, DE, ES, FR, IT, JA, NL, PL, PT.
  6. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with zero regressions.

### TST-UI-322: **Prevent Clipping of Climb Category Marker Badges in Elevation Profile Canvas Verification** [Dynamic font-scale aware clearance prevents climb category badge clipping on elevation profile canvas; container height stability preserved; two-layer boundary protection ceiling guarantees >= 2dp margin; 100% unit test pass rate.]
- **Requirement(s)**: `REQ-UI-274`
- **Component**: **Prevent Clipping of Climb Category Marker Badges in Elevation Profile Canvas Verification**
- **Jira**: ``ATT-3453``
- **Test Description**:
  1. *Dynamic Font-Scale Aware Top Clearance Verification (`ElevationProfileClimbBadgeClippingTest.kt`)*:
  • Verify that when `climbs.isNotEmpty()` and `!isTimeDomain`, `climbBadgeTopPadding` evaluates dynamically to `(requiredClimbTopClearance - baseTopPadding).coerceAtLeast(0.dp)`.
  • Verify that when `climbs.isEmpty()`, `climbBadgeTopPadding` evaluates to `0.dp`, preserving compact list preview geometry.
  • Verify that when `showZoomControls == true` (detail view), `baseTopPadding = 44.dp` satisfies clearance, yielding `climbBadgeTopPadding == 0.dp`.
  2. *Container Height Stability & Proportional Rescaling (`ElevationProfileClimbBadgeClippingTest.kt`)*:
  • Verify that `totalCanvasHeight` is unchanged (`fixedHeight ?: if (showZoomControls) cachedData.adaptiveHeight + 28.dp else cachedData.adaptiveHeight`), preventing card jumping.
  • Verify that when `fixedHeight != null` (e.g. 120 dp), container height remains constant while chart plotting area rescales without container overflow.
  3. *Accessibility Font Scaling & Boundary Ceiling Safeguards (`ElevationProfileClimbBadgeClippingTest.kt`)*:
  • Verify simulated font scales 1.0x (24 dp), 1.5x (29 dp), and 2.0x (34 dp) expand clearance dynamically.
  • Verify `minAllowedPillTop = -effectiveTopPadding.toPx() + 2.dp.toPx()` prevents negative box coordinates ($y_{\text{box}} \ge 2\text{dp}$) even at peak elevation ($Y = 0$).
  • Verify horizontal safety margins (`minMarginPx = 2.dp.toPx()`) at route start and finish.
  4. *Preservation of Layout and Climb Contract Invariants (`ElevationProfileLayoutTest.kt`, `ElevationProfileClimbSpanContractTest.kt`)*:
  • Verify literal preservation of `val topPadding = if (showZoomControls) 44.dp else 16.dp` and `canvas.nativeCanvas.drawLine(summitX, summitY, summitX, pillBottom, stemPaint)`.
  5. *9-Language Localization Audit (`TranslationParityTest.kt`)*:
  • Verify 100% parity across EN, DE, ES, FR, IT, JA, NL, PL, PT (0 new strings introduced).
  6. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with zero regressions.

### TST-UI-323: **Turn-by-Turn Navigation Slight Turn Vector Icon Harmonization and Directional Symbol Compose Preview Gallery Verification** [Canonical Material vector geometries for slight right and slight left turn navigation icons with baseline y=20 grounding, symmetric mirroring, and interactive multi-theme Compose preview gallery; 100% unit test pass rate.]
- **Requirement(s)**: `REQ-UI-360`
- **Component**: **Turn-by-Turn Navigation Slight Turn Vector Icon Harmonization and Directional Symbol Compose Preview Gallery Verification**
- **Jira**: ``ATT-3455``
- **Test Description**:
  1. *Vector XML Structure, Viewport & Baseline Grounding Contract Tests (`TurnDirectionIconContractTest.kt`)*:
  • Verify that all 8 `TurnDirection` enum entries reference valid XML vector drawables in `res/drawable/`.
  • Verify that all 8 vector drawables declare `android:viewportWidth="24"` and `android:viewportHeight="24"`.
  • Verify that `ic_turn_slight_right.xml` contains canonical path starting at baseline $y=20$ and ending with closing `z`.
  • Verify that `ic_turn_slight_left.xml` contains canonical path starting at baseline $y=20$ and ending with closing `z`.
  2. *Symmetrical Path Mirroring Unit Tests (`TurnDirectionIconContractTest.kt`)*:
  • Verify that the X coordinates and delta vectors in `ic_turn_slight_left.xml` are the exact mathematical horizontal reflection ($x' = 24 - x$) of `ic_turn_slight_right.xml`.
  3. *Compose Preview Gallery Contract Tests (`TurnDirectionIconContractTest.kt`)*:
  • Verify that `TurnPromptBanner.kt` declares `@Preview` composables `TurnCueIconsGalleryLightPreview` and `TurnCueIconsGalleryDarkPreview`.
  • Verify that `TurnCueIconsGallery` renders all 8 directions with non-null icons and string resources.
  4. *9-Language Localization Audit (`TranslationParityTest.kt`)*:
  • Verify 100% parity across EN, DE, ES, FR, IT, JA, NL, PL, PT for all 8 `turn_cue_*` strings.
  5. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with zero regressions.

### TST-UI-324: **Turn-by-Turn Navigation Sharp Turn Vector Icon Harmonization and Directional Contract Verification** [Canonical Material vector geometries for sharp right and sharp left turn navigation icons with acute ~135° deflection bends, diagonal downward-pointing arrowheads, baseline y=20 grounding, and symmetric reflection mirroring; 100% unit test pass rate.]
- **Requirement(s)**: `REQ-UI-361`
- **Component**: **Turn-by-Turn Navigation Sharp Turn Vector Icon Harmonization and Directional Contract Verification**
- **Jira**: ``ATT-3493`, `ATT-3573``
- **Test Description**:
  1. *Vector XML Structure, Viewport & Safety Header Contract Tests (`TurnDirectionIconContractTest.kt`)*:
  • Verify that `ic_turn_sharp_right.xml` and `ic_turn_sharp_left.xml` exist and declare valid 24x24dp viewport (`viewportWidth="24"`, `viewportHeight="24"`).
  • Verify that both vector drawables declare safety header comment `<!-- DO NOT MODIFY PATH DATA WITHOUT UPDATING TST-UI-324 -->`.
  • Verify that both vector drawables declare `android:fillColor="#FF1976D2"`.
  2. *Baseline Grounding & Stem Width Geometry Tests (`TurnDirectionIconContractTest.kt`)*:
  • Verify that `ic_turn_sharp_right.xml` starts at baseline $y=20$ (`M6,20h2`) and closes at $y=20$ (`V20z`).
  • Verify that `ic_turn_sharp_left.xml` starts at baseline $y=20$ (`M18,20h-2`) and closes at $y=20$ (`V20z`).
  • Verify that baseline stem width is exactly 2dp (`h2` for right, `h-2` for left).
  3. *Symmetrical Path Mirroring Unit Tests (`TurnDirectionIconContractTest.kt`)*:
  • Verify that milestone coordinates and vectors in `ic_turn_sharp_left.xml` are the exact mathematical horizontal reflection ($x_R + x_L = 24.0, y_R = y_L$) of `ic_turn_sharp_right.xml` across all 22 milestone vertices.
  4. *9-Language Localization Audit (`TranslationParityTest.kt`)*:
  • Verify 100% parity across EN, DE, ES, FR, IT, JA, NL, PL, PT for `turn_cue_sharp_right` and `turn_cue_sharp_left`.
  5. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with zero regressions.

### TST-UI-325: **Removal of Google Drive Format Scan Option from Workout Import Cards Verification** [Format import tab cards render exclusively Local SAF and Dropbox scan action buttons; obsolete Google Drive scan buttons and dialogs removed; full cloud database backup and SAF picking preserved; 100% test pass rate.]
- **Requirement(s)**: `REQ-UI-362`
- **Component**: **Removal of Google Drive Format Scan Option from Workout Import Cards Verification**
- **Jira**: ``ATT-3489``
- **Test Description**:
  1. *Import Format Cards Architectural & Contract Tests (`ImportFormatCardsContractTest.kt`)*:
  • Verify that `FormatImportCard` composable is defined with parameters for local import and Dropbox scan, and without parameters for Google Drive.
  • Verify that `ImportTabContent` renders three distinct format blocks (FIT, TCX, GPX).
  • Verify that each format block contains only Local File and Dropbox Scan action buttons.
  • Verify that `R.string.import_fit_gdrive_button`, `R.string.import_tcx_gdrive_button`, and `R.string.import_gpx_gdrive_button` are absent from `ImportBackupTabsScreen.kt`.
  • Verify that `pendingGoogleDriveFormat` is absent from `ImportBackupTabsScreen.kt`.
  2. *9-Language Localization Audit (`ImportFormatLocalizationTest.kt`)*:
  • Verify all remaining format import titles, descriptions, and button labels exist and are non-empty across all 9 supported locales: `values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`.
  • Verify that `import_fit_gdrive_button`, `import_tcx_gdrive_button`, and `import_gpx_gdrive_button` are cleanly removed across all 9 locale resource files without dangling references.
  3. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with zero regressions.

### TST-UI-326: **Recursive Directory Workout File Discovery and Bulk Import via Storage Access Framework Verification** [Recursive directory traversal and bulk import via Android SAF OpenDocumentTree() with automated duplicate skipping, depth limit bounding, cycle detection, and 9-language localization parity; 100% test pass rate.]
- **Requirement(s)**: `REQ-UI-363`
- **Component**: **Recursive Directory Workout File Discovery and Bulk Import via Storage Access Framework Verification**
- **Jira**: ``ATT-3491``
- **Test Description**:
  1. *Recursive Traversal Engine Tests (`SafDirectoryScannerTest.kt`)*:
  • Verify that `SafDirectoryScanner` discovers matching files (`.fit`, `.tcx`, `.gpx`) across nested directory hierarchies.
  • Verify that scanner filters files case-insensitively and ignores incompatible extensions.
  • Verify depth limit bounding ceases traversal at `maxDepth = 6`.
  • Verify cycle detection prevents infinite loops across duplicate or cyclical directory links.
  • Verify empty directories return empty list without crashing.
  • Verify coroutine cancellation stops traversal promptly.
  2. *Import Format Cards Architectural & Contract Tests (`ImportFormatCardsContractTest.kt`)*:
  • Verify that `FormatImportCard` defines `folderButtonText` and `onFolderClick` parameters.
  • Verify that `ImportTabContent` defines `onFolderImportClick` and configures all 3 cards (FIT, TCX, GPX) with folder scan handlers.
  • Verify that each format card displays all 3 action buttons: Local File Import, Folder Scan, and Dropbox Scan.
  3. *9-Language Localization Audit (`ImportFormatLocalizationTest.kt`)*:
  • Verify all newly added folder scan and status string resources exist, are non-empty, and match parameter format specifiers across all 9 supported locales: `values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`.
  4. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with zero regressions.

### TST-UI-327: **Centralized Metric Distance and Altitude Formatting Enforcement Across Navigation and Telemetry Cards Verification** [Centralized metric distance and altitude formatting enforcement across navigation and telemetry cards (Rule 36), ensuring strict Imperial unit parity and headless JVM formatter null safety; 100% test pass rate.]
- **Requirement(s)**: `REQ-UI-364`
- **Component**: **Centralized Metric Distance and Altitude Formatting Enforcement Across Navigation and Telemetry Cards Verification**
- **Jira**: ``ATT-3361``
- **Test Description**:
  1. *Unit & Contract Tests for Navigation & HUD Metric Formatters (`MetricFormatterContractTest.kt`)*:
  • Verify `ForkDecisionCard` distance & elevation formatting via `DistanceFormatter` and `AltitudeFormatter`.
  • Verify `ReturnNavigationState` formats `formattedRemainingDistance` and `formattedRemainingClimb` using `DistanceFormatter` and `AltitudeFormatter`.
  • Verify `RouteSelectionButton` and `RouteSelectorSheet` format distance and elevation using canonical formatters.
  • Verify `TurnPromptBanner` renders `distanceRemaining` formatted with units.
  • Verify `LapSplitVisualizer` formats split distance via `DistanceFormatter`.
  • Verify `LiveClimbSheet` formats remaining elevation and distance via canonical formatters without raw `" m"` literals.
  2. *Imperial & Metric Unit Parity Verification (`MetricFormatterContractTest.kt`)*:
  • Verify that when `MyUnits.IMPERIAL` is active, distances format with feet (`ft`) and miles (`mile`), and elevations format with feet (`ft`).
  • Verify zero occurrences of raw `"km"` or `" m"` in Imperial mode.
  • Verify that when `MyUnits.METRIC` is active, distances and elevations format with canonical `m` and `km`.
  3. *Formatter JVM Robustness & Null-Safety Tests (`DistanceFormatterTest.kt`, `AltitudeFormatterTest.kt`)*:
  • Verify that `DistanceFormatter.format_with_units()` and `AltitudeFormatter.format_with_units()` execute safely in headless JVM environments without throwing `NullPointerException` when `ApplicationContext` or `SharedPreferences` is null.
  4. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with zero regressions.

### TST-UI-328: **Centralized Speed Unit Conversion and Formatter Harmonization via MyHelper Verification** [Centralized speed conversions via MyHelper (Rule 36), zero inline magic constants in TelemetryMetricUtils, locale-resilient numeric parsing in EditSportTypeDialog, and 100% unit test pass rate.]
- **Requirement(s)**: `REQ-UI-365`
- **Component**: **Centralized Speed Unit Conversion and Formatter Harmonization via MyHelper Verification**
- **Jira**: ``ATT-3363``
- **Test Description**:
  1. *Unit & Precision Tests for MyHelper Conversions (`MyHelperSpeedConversionTest.kt`)*:
  • Verify bidirectional conversion `mps2userUnit(speed, unit)` and `UserUnit2mps(speed, unit)` across `MyUnits.METRIC` (factor 3.6) and `MyUnits.IMPERIAL` (factor 2.23693629).
  • Verify mathematical identity: `UserUnit2mps(mps2userUnit(speed, unit), unit) == speed` within $10^{-6}$ tolerance across wide speed range (0 to 100 m/s).
  • Verify zero/negative speed boundary safety.
  2. *TelemetryMetricUtils Unit & Formatting Tests (`TelemetryMetricUtilsTest.kt`)*:
  • Verify `extractMetricValue` for `SPEED` under both `MyUnits.METRIC` and `MyUnits.IMPERIAL` matches `MyHelper.mps2userUnit(speed, unit)`.
  • Verify `formatValue` for `SPEED` under both `MyUnits.METRIC` and `MyUnits.IMPERIAL` converts user unit values via `MyHelper.UserUnit2mps(value, unit)` prior to passing to `SpeedFormatter`.
  • Verify zero inline occurrences of magic constants `3.6` or `2.236936` in `TelemetryMetricUtils.kt`.
  3. *EditSportTypeDialog Formatting & Input Parsing Tests (`EditSportTypeDialogTest.kt`)*:
  • Verify `formatSpeed` outputs consistent two-decimal-place numbers formatted without ad-hoc string character replacement (`.replace(",", ".")`).
  • Verify decimal input parsing handles both comma (`,`) and dot (`.`) inputs seamlessly, correctly converting user inputs into valid m/s threshold bounds.
  4. *SpeedFormatter DRY Integration Tests (`SpeedFormatterTest.kt`)*:
  • Verify `SpeedFormatter.format_with_units()` delegates conversion to `MyHelper.mps2userUnit()`, matching canonical speeds across metric and imperial settings.
  5. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with zero regressions.

### TST-UI-329: **Harmonized Live Segment and Live Climb Tracking Bottom Sheet Architecture & Container Verification** [Harmonized live segment and live climb tracking bottom sheet architecture and container verification (Rule 36), ensuring zero container duplication, correct file casing, and 100% unit test pass rate.]
- **Requirement(s)**: `REQ-UI-366`
- **Component**: **Harmonized Live Segment and Live Climb Tracking Bottom Sheet Architecture & Container Verification**
- **Jira**: ``ATT-3364``
- **Test Description**:
  1. *Architectural Contract Tests (`LiveSegmentSheetContractTest.kt`)*:
  • Verify file path resolution to `LiveSegmentSheet.kt` under standard PascalCase.
  • Verify `wrapContentHeight` and map suppression parameters in `MapDetailLayout` integration.
  • Verify `SensorGridScreen.kt` mounts `LiveSegmentSheet` inside a content-wrapped container.
  • Verify `externalScrubDistance` dynamic evaluation (`REQ-UI-331`, `REQ-UI-340`).
  2. *Composable Layout Reflection Tests (`LiveSegmentSheetLayoutTest.kt`, `LiveClimbSheetLayoutTest.kt`)*:
  • Verify `LiveSegmentSheetKt` and `LiveClimbSheetKt` public visibility and composable signatures.
  • Verify design tokens for drag handle, badges, and shared elevation profile embedding.
  3. *Unified Container Component Tests (`LiveTrackingSheetContainerTest.kt`)*:
  • Verify that `LiveTrackingSheetContainer` applies `.fillMaxWidth().wrapContentHeight().sheetContour().background(...)` and triggers click-to-expand in `PartiallyExpanded` state.
  4. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with zero regressions.

### TST-UI-330: **First-Time Stop Tracking Swipe and Tab Coachmark Banner on Cockpit Screen Verification** [First-time stop tracking swipe and tab coachmark banner on cockpit screen verified for visibility predicates, synchronous optimistic dismissal (`isDismissPending`), scroll-settled gating (`settledPage > 0 && !isScrollInProgress`), ViewModel configuration-change survival, DataStore persistence & factory reset, zero-jump `Box(Modifier.weight(1f))` overlay (`heightIn(max = 120.dp)`, `maxLines = 2` for 4-inch small-screen telemetry clearance), and 9-language localization parity; 100% unit test pass rate.]
- **Requirement(s)**: `REQ-UI-367`
- **Component**: **First-Time Stop Tracking Swipe and Tab Coachmark Banner on Cockpit Screen Verification**
- **Jira**: ``ATT-3387`, `ATT-4456``
- **Test Description**:
  1. *Coachmark Visibility, Optimistic Dismissal & Auto-Acknowledgement Predicate Unit Tests (`StopTrackingCoachmarkTest.kt`)*:
  • Verify `shouldShowStopTrackingCoachmark` returns `true` when `hasSeenHint == false`, `isDismissPending == false`, `trackingMode` is `TRACKING` or `PAUSED`, `currentPage > 0`, and `screenMode == ScreenMode.TRACKING`.
  • Verify `shouldShowStopTrackingCoachmark` returns `false` when `trackingMode == TrackingMode.READY`, when `currentPage == 0`, when `screenMode == ScreenMode.CONFIGURATION`, when `hasSeenHint == true`, or immediately when `isDismissPending == true` (simulating synchronous optimistic dismissal while `DataStore` write is delayed/hanging or across interrupted `READY -> TRACKING -> READY -> TRACKING` transitions).
  • Verify `shouldAutoAcknowledgeStopTrackingCoachmark` returns `false` when `hasRenderedCoachmarkInSession == false`, `isDismissPending == true`, `currentPage != 0`, `settledPage != 0`, or `isScrollInProgress == true` (preventing premature dismissal during scroll animations from `Page 0` to `Page 1`), and returns `true` only when `hasRenderedCoachmarkInSession == true`, `currentPage == 0`, `settledPage == 0`, `!isScrollInProgress`, `hasSeenHint == false`, `!isDismissPending`, and tracking is active/paused.
  • Verify in-flight animation states (`currentPage = 1, settledPage = 0, isScrollInProgress = true` and `currentPage = 0, settledPage = 0, isScrollInProgress = true`) strictly return `false`.
  2. *ViewModel Configuration-Change Survival & DataStore Persistence Contract Tests (`StopTrackingCoachmarkTest.kt`)*:
  • Verify `TrackingTabsViewModel.hasRenderedCoachmarkInSession` and `isCoachmarkDismissPending` survive configuration changes across UI recreations and reset appropriately.
  • Verify `TuningPreferencesDataStore` defines `KEY_HAS_SEEN_STOP_TRACKING_HINT = booleanPreferencesKey("tuning_has_seen_stop_tracking_hint")` in `ALL_KEYS`, exposes `hasSeenStopTrackingHintFlow` and `setHasSeenStopTrackingHint(boolean)`, and automatically resets the key upon `resetToDefaults()`.
  3. *Layout Stability, Height Constraint, Settled Gating & 4-Inch Small-Screen Telemetry Clearance Contract Verification (`StopTrackingCoachmarkTest.kt`)*:
  • Verify `TrackingTabsScreen.kt` gates `markCoachmarkRenderedInSession()` on `showStopTrackingCoachmark && pagerState.settledPage > 0 && !pagerState.isScrollInProgress`.
  • Verify `TrackingTabsScreen.kt` hosts `HorizontalPager` and `StopTrackingCoachmarkBanner` inside a constant-weight `Box(modifier = Modifier.weight(1f).fillMaxWidth())` with `Alignment.TopCenter` so appearing/disappearing never causes `HorizontalPager` to jump or resize.
  • Verify `StopTrackingCoachmarkBanner.kt` enforces `heightIn(max = 120.dp)`, `maxLines = 2`, `TextOverflow.Ellipsis`, weighted text layout (`Modifier.weight(1f)`) for 4-inch small-screen telemetry clearance, 48dp minimum touch targets (`defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)`), and zero tonal elevation (`tonalElevation = 0.dp`).
  4. *9-Language Localization Parity Audit (`StopTrackingCoachmarkTest.kt`, `TranslationParityTest.kt`)*:
  • Verify `coachmark_stop_tracking_hint`, `coachmark_stop_tracking_show_tab`, and `coachmark_stop_tracking_dismiss` exist and are non-empty across all 9 supported locales (`values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`).
  5. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with zero regressions.

### TST-UI-331: **Interactive Start/End Workout Cropping with Destructive Sample Pruning and Full Metric Recalculation Verification** [Interactive start/end workout cropping with destructive sample pruning, synchronized map/elevation previews, universal EPSILON = 1e-6 cumulative clamping, deterministic lap note preservation, pre-flight storage safety, export replacement, and 9-language localization parity; 100% unit test pass rate.]
- **Requirement(s)**: `REQ-UI-368`
- **Component**: **Interactive Start/End Workout Cropping with Destructive Sample Pruning and Full Metric Recalculation Verification**
- **Jira**: ``ATT-3274``
- **Test Description**:
  1. *Pure Crop Math & Telemetry Aggregation Unit Tests (`CropWorkoutMathTest.kt`)*:
  • Verify that cumulative monotonic columns (`DISTANCE_m`, `TIME_TOTAL`, `TIME_ACTIVE`, `ASCENT`, `DESCENT`, `CALORIES`) are zero-anchored at `startRowId` and clamped with `EPSILON = 1e-6` for subsequent rows, eliminating floating-point subtraction drift.
  • Verify row-by-row `LINE_DISTANCE_m` Haversine recalculation relative to the new start coordinate, and zero-anchoring when GPS coordinates are null (indoor sessions).
  • Verify strict re-derivation of non-linear extrema (`MIN`, `MAX`, `AVG`) from retained sample rows, discarding pruned points.
  • Verify input sanitization (`Double.isFinite(v)`), ensuring `NaN` and `Infinity` values are filtered out from sums and averages.
  • Verify fallback to `0.0` extrema when a sensor has zero valid readings in the cropped interval.
  • Verify duration guard $\ge 1\text{ s}$ and safe speed division (`activeTimeSec >= 1.0`).
  2. *Lap Recalculation & Metadata Preservation Unit Tests (`CropLapsRecalculationTest.kt`)*:
  • Verify deterministic lap mapping where retained lap $k$ strictly preserves user-authored `NAME` and `DESCRIPTION` from its original parent lap without truncation or heuristic merging.
  • Verify boundary laps 1 and $K$ have their duration, distance, and average speed recalculated strictly from their retained samples.
  • Verify sequential re-indexing `1..K` in lockstep across laps and sample rows.
  • Verify verification assertion $\sum_{k=1}^K \text{DISTANCE\_TOTAL\_m}_k == \text{workout.DISTANCE\_TOTAL\_m}$ within `1e-6`.
  3. *Database Transaction & Rollback Recovery Tests (`WorkoutRepositoryCropTest.kt`)*:
  • Verify SQLite rollback journal guarantees that any exception during sample pruning, lap re-indexing, or summary updates rolls back all modifications cleanly without data corruption.
  • Verify post-op checksum validation (`remainingRows == expectedRetainedRowCount && deletedRows == expectedDeletedRowCount`).
  • Verify pre-flight storage check (`StatFs` $\ge 10\text{ MB}$) rejects crop when storage is insufficient.
  • Verify pre-crop ephemeral JSON snapshot creation and cleanup inside `try ... finally`.
  • Verify synchronous export cache file deletion pre-commit and post-commit `WorkManager` replacement via `ExistingWorkPolicy.REPLACE`.
  • Verify that if `WorkManager` enqueue fails, `ExportStatus` transitions to `ERROR` and SQLite changes remain committed.
  4. *ViewModel & UI Pre-Condition Contract Tests (`CropWorkoutScreenContractTest.kt`)*:
  • Verify `isCropValid` predicate: button is disabled when `(endIndex - startIndex < minIndexStep)` or `croppedDuration < 1.0s` or `isCroppingInProgress == true`.
  • Verify `BackHandler(enabled = isCroppingInProgress)` blocks back navigation during execution.
  • Verify irreversible destructive confirmation `AlertDialog` presentation and text warning.
  • Verify "Force Re-Export" button visibility in workout details when `ExportStatus == ERROR`.
  5. *9-Language Localization Audit (`CropWorkoutLocalizationTest.kt`)*:
  • Verify all crop UI strings, button labels, dialog titles, warnings, and error messages exist and are non-empty across all 9 supported locales: `values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`.
  6. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with zero regressions.

### TST-UI-332: **Prohibition of Static BottomSheetScaffold Bottom Inset Padding on Cockpit ElevationProfile Surface Verification (Post-Review Revert Fence)** [Enforces post-review revert fence on `SensorGridScreen.kt` prohibiting static `BottomSheetScaffold` bottom padding on `ElevationProfile` `Surface`; 100% unit test pass rate.]
- **Requirement(s)**: `REQ-UI-369`
- **Component**: **Prohibition of Static BottomSheetScaffold Bottom Inset Padding on Cockpit ElevationProfile Surface Verification (Post-Review Revert Fence)**
- **Jira**: ``ATT-3311``
- **Test Description**:
  1. *ElevationProfile Surface No-Bottom-Padding Fence Verification (`SensorGridScreenLayoutTest.kt`)*:
  • Verify `testElevationProfileSurface_doesNotApplyStaticScaffoldBottomPadding`: `SensorGridScreen.kt` MUST NOT contain `bottom = paddingValues.calculateBottomPadding()`, preventing the $\approx 174\text{dp}$ permanent blank whitespace below the `ElevationProfile` when the Live Segment / Live Climb sheet is swiped down to `SheetValue.Hidden`.
  2. *Sensor Grid Scrollability & Asymmetric Map Weight Preservation Tests (`SensorGridScreenLayoutTest.kt`)*:
  • Verify sensor grid retains `.weight(1f, fill = false)` when map is hidden and `.heightIn(max = availableHeight * 0.5f)` when map is visible (`REQ-UI-358`).

### TST-UI-333: **Decoupled Training Zone Architecture & 7-Zone Coggan Cycling Power Model Verification** [Decoupled training zone architecture retaining 5 zones for Heart Rate while expanding Cycling Power to Coggan 7-zone model across DataStore dynamic migration, telemetry math, real-time HUD tiles, aftermath column charts, and 9-language localization parity; 100% unit test pass rate.]
- **Requirement(s)**: `REQ-UI-370`
- **Component**: **Decoupled Training Zone Architecture & 7-Zone Coggan Cycling Power Model Verification**
- **Jira**: ``ATT-3184``
- **Test Description**:
  1. *DataStore Dynamic Proportional Migration & Persistence Unit Tests (`SettingsDataStorePowerZonesTest.kt`)*:
  • Verify `SettingsDataStore` dynamically derives missing `PWR_BIKE_ZONE5_MAX` and `PWR_BIKE_ZONE6_MAX` proportionally from existing `ZONE4_MAX` within an atomic transaction.
  • Verify canonical fallback defaults (150W, 200W, 250W, 300W, 350W, 450W) when `ZONE4_MAX` is missing.
  • Verify strict monotonic ascending order validation ($0 < z1 < z2 < z3 < z4 < z5 < z6$).
  2. *Edge-Case Athletic Baseline & Bounds Unit Tests (`SettingsDataStorePowerZonesTest.kt`, `TelemetryZoneMathTest.kt`)*:
  • Verify proportional calculation and monotonic validity across extreme athletic profiles: ultra-low FTP (60W) and ultra-high FTP (500W).
  • Verify negative and zero power clamping ($P \le 0$).
  3. *TelemetryZoneMath 7-Zone Band Calculation Unit Tests (`TelemetryZoneMathTest.kt`)*:
  • Verify `calculatePowerZoneBands` returns exactly 7 non-overlapping, clamped bands with `Zone1..Zone7` colors and labels `Z1..Z7`.
  • Verify `determinePowerZone` maps power values across all 7 intervals correctly.
  • Verify `loadPowerThresholds` loads 6 boundaries.
  4. *PowerZoneDistributionCalculator 7-Zone Unit Tests (`PowerZoneDistributionCalculatorTest.kt`)*:
  • Verify `calculatePowerDistribution` computes cumulative durations and percentages across 7 zones.
  • Verify `calculatePowerHistogram` maps frequency bins across 7 zones with matching colors.
  5. *Tracking Cockpit HUD & Battery Saver Unit Tests (`SensorFieldZoneDisplayTest.kt`, `BatterySaverModelTest.kt`)*:
  • Verify `TrackingViewModel` resolves `TTColor.Zone6` (Dark Chestnut Brown) and `TTColor.Zone7` (Black).
  • Verify `BatterySaverModel.calculateZoneIndex` returns zones `1..7` for cycling power.
  6. *9-Language Localization Audit (`PowerZoneLocalizationTest.kt`)*:
  • Verify `zone_6_label` and `zone_7_label` exist and are non-empty across all 9 supported locales: `values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`.
  7. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with zero regressions.

### TST-UI-334: **Elevation Profile 7-Zone Gradient Shading and Legend Verification** [Elevation profile 7-zone gradient shading and harmonized legend verified for all 7 zone intervals, exact boundaries, descent mapping, contract delegation, accessibility borders, and climb shading non-regression; 100% unit test pass rate.]
- **Requirement(s)**: `REQ-UI-371`
- **Component**: **Elevation Profile 7-Zone Gradient Shading and Legend Verification**
- **Jira**: ``ATT-3391``
- **Test Description**:
  1. *Elevation Slope Gradient Resolution Unit Tests (`ElevationProfileGradeColorTest.kt`)*:
  • Verify `getElevationGradeColor` evaluates all 7 zone color intervals: Zone 1 (< 2%), Zone 2 (2 - 5%), Zone 3 (5 - 10%), Zone 4 (10 - 15%), Zone 5 (15 - 20%), Zone 6 (20 - 25%), Zone 7 (>= 25%).
  • Verify exact boundary transitions ($[x_{n-1}, x_n)$): 2.0% -> Zone 2, 5.0% -> Zone 3, 10.0% -> Zone 4, 15.0% -> Zone 5, 20.0% -> Zone 6, 25.0% -> Zone 7.
  • Verify descent and negative slope handling: -15.0%, -5.0%, -0.1%, 0.0% all map to Zone 1 (`TTColor.Zone1`).
  • Verify extreme values: -50.0% -> Zone 1, 100.0% -> Zone 7.
  2. *ElevationProfile Contract & Legend Verification (`ElevationProfileGradeColorTest.kt`)*:
  • Verify `ElevationProfile.kt` delegates to `getElevationGradeColor(grade)` when `climbGradientShading == false`.
  • Verify `GradeLegend` contains all 7 labels (`< 2%`, `2 - 5%`, `5 - 10%`, `10 - 15%`, `15 - 20%`, `20 - 25%`, `> 25%`).
  • Verify `GradeLegendItem` renders border outline for dark swatches.
  3. *Preservation of Climb Shading Invariants (`ElevationProfileClimbShadingTest.kt`)*:
  • Verify that `ElevationProfileClimbShadingTest.kt` passes with 100% assertions satisfied for `REQ-UI-352`.
  4. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with zero regressions.

### TST-UI-335: **Horizontal Stacked Distribution Bar Component & 3-Way Zone Display Mode Switching Verification** [Horizontal stacked distribution bar component, 3-way display mode switching, zero-duration handling, uniform contour and swatch borders, default preference selector in Advanced Settings, reactive wiring to workout summaries with Tier 1/2 state precedence, and histogram absence fallback verified across 5-zone HR and 7-zone Power; 100% unit test pass rate.]
- **Requirement(s)**: `REQ-UI-372`
- **Component**: **Horizontal Stacked Distribution Bar Component & 3-Way Zone Display Mode Switching Verification**
- **Jira**: ``ATT-3390`, `ATT-3676``
- **Test Description**:
  1. *Stacked Bar Layout & Math Unit Tests (`ZoneDistributionStackedBarTest.kt`)*:
  • Verify active entries generate proportional segment weights (`entry.percentage`) directly without overflow, and zero-duration entries are cleanly omitted from the bar.
  • Verify empty or all-zero distributions render a neutral placeholder bar without throwing exceptions.
  • Verify 5-zone HR dataset and 7-zone Power dataset rendering parity.
  • Verify outer bar and legend swatches receive uniform accessibility border outlines.
  • Verify dimmed display of zero-duration zones in legend with `0:00 (0%)`.
  2. *ZoneCardDisplayMode Enum & Legacy Compatibility Unit Tests (`ZoneCardDisplayModeTest.kt`, `TelemetryHistogramCardTest.kt`)*:
  • Verify `ZoneCardDisplayMode.values()` contains `STACKED_BAR`, `COLUMNS`, `HISTOGRAM`, and `FIVE_ZONES`.
  • Verify `FIVE_ZONES` acts as a backward-compatible alias for `COLUMNS`.
  3. *WorkoutCardSectionPreferences Persistence Unit Tests (`WorkoutCardSectionPreferencesTest.kt`)*:
  • Verify `zoneCardDisplayMode` defaults to `COLUMNS` and copies cleanly.
  • Verify DataStore round-trip across all modes and legacy `FIVE_ZONES` fallback.
  4. *Contract Regression Verification (`TelemetryMetricGraphMarginalBarsContractTest.kt`)*:
  • Verify 100% assertions satisfied for telemetry graph marginal strip contract.
  5. *9-Language Localization Audit (`ZoneDistributionStackedBarLocalizationTest.kt`)*:
  • Verify `zone_mode_stacked_bar`, `zone_mode_columns`, `zone_mode_histogram`, and `settings_zone_card_display_mode_title` exist and are non-empty across all 9 supported locales: `values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`.
  6. *Histogram Fallback Guard Unit Tests (`ZoneCardDisplayModeFallbackTest.kt`)*:
  • Verify that when `mode == HISTOGRAM` and `distribution.histogram == null`, effective mode gracefully resolves to `COLUMNS`.
  • Verify 2-button segmented row highlights Columns when fallback is active.
  7. *Workout Summary State Precedence Tests (`WorkoutSummaryZoneDisplayModeTest.kt`)*:
  • Verify cards initialize to `preferences.zoneCardDisplayMode`.
  • Verify interactive card tap establishes manual override.
  • Verify global preference change updates cards without manual override, while preserving manual overrides.
  8. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with zero regressions.

### TST-UI-336: **Automatic Training Zone Boundary Calculation from FTP and Heart Rate Baselines Verification** [Automatic training zone calculation from FTP and Heart Rate baselines verified for Coggan 7-zone rounding, Karvonen invariants, two-layer monotonicity with strict +1 increments, single-transaction ACID atomicity, UI mutation locking, locale-aware parsing, overwrite dialog safety, and 9-language format token parity; 100% unit test pass rate.]
- **Requirement(s)**: `REQ-UI-373`
- **Component**: **Automatic Training Zone Boundary Calculation from FTP and Heart Rate Baselines Verification**
- **Jira**: ``ATT-3188``
- **Test Description**:
  1. *Pure Mathematical Model, Coggan Rounding & Zone 7 Ceiling Unit Tests (`ZoneCalculationMathTest.kt`)*:
  • Verify Coggan 7-zone calculation for FTP = 200W, 250W, 300W matches exact half-up rounded cutoffs.
  • Verify Zone 7 physical ceiling is capped at 2500W with lower bound $Z_6 + 1\text{ W}$.
  • Verify baseline FTP boundary clamping: FTP < 50 throws `IllegalArgumentException`, FTP > 600 throws `IllegalArgumentException`.
  • Verify calculation when $Z_6 \ge 2500\text{ W}$ throws `IllegalArgumentException`.
  • Verify strict monotonicity: $Z_n \ge Z_{n-1} + 1$ for all valid FTP inputs in $[50, 600]$.
  2. *Karvonen & % HRmax Mathematical Models & Invariant Guards (`ZoneCalculationMathTest.kt`)*:
  • Verify % HRmax model for $HR_{\max} = 190\text{ bpm}$ produces monotonic 5-zone cutoffs.
  • Verify Karvonen model for $HR_{\max} = 190, HR_{\text{rest}} = 50$ produces correct HRR cutoffs.
  • Verify Primary Karvonen Invariant: $HR_{\max} \le HR_{\text{rest}}$ throws `IllegalArgumentException`.
  • Verify Karvonen delta constraint: $HR_{\max} - HR_{\text{rest}} < 20$ throws `IllegalArgumentException`.
  • Verify $HR_{\max} \notin [80, 240]$ and $HR_{\text{rest}} \notin [30, 120]$ throw `IllegalArgumentException`.
  3. *Two-Layer Monotonicity Validation & Step Increment (`ZoneValidatorTest.kt`)*:
  • Verify `validatePowerZoneStack` passes strictly monotonic ascending stacks ($Z_n \ge Z_{n-1} + 1$) and throws `IllegalArgumentException` on inversions ($Z_i \ge Z_{i+1}$) or equal adjacent values ($Z_i == Z_{i+1}$) or values $> 2500\text{W}$ or $\le 0$.
  • Verify `validateHeartRateZoneStack` passes valid monotonic 4-cutoff stacks and throws `IllegalArgumentException` on inversions, equal values, or values $> 240\text{ bpm}$.
  4. *Single-Transaction ACID Atomicity, UI Lock & DataStore Baselines (`SettingsDataStoreBaselinesTest.kt`)*:
  • Verify saving calculated power zones persists FTP, timestamp, `pwr_bike_zones_customized = false`, and all 6 cutoffs in a single transaction.
  • Verify manual cutoff mutation flips `pwr_bike_zones_customized` to `true`.
  • Verify `SettingsDataStoreJavaHelper.getFtp(context)` retrieves stored FTP or null when unconfigured.
  • Verify non-destructive migration: existing legacy cutoffs retain custom flag `true` and null FTP.
  5. *Locale-Aware Number Parsing (`ZoneNumberParserTest.kt`)*:
  • Verify parsing integer strings with standard ASCII digits (`"250"` -> `250`), German/European comma decimal separators (`"250,5"` -> `250`), grouping separators and spaces (`"1 200"`, `"1.200"`), and invalid non-numeric strings returning null.
  6. *Destructive Overwrite, Error Copy & Dialog Contract Verification (`ZoneCalculatorDialogContractTest.kt`)*:
  • Verify dialog initial state populates existing stored FTP or default 200W.
  • Verify `shouldShowOverwriteWarning` returns `true` if and only if `isCustomized == true`.
  • Verify cancelling overwrite preserves existing cutoffs without DataStore writes.
  • Verify confirming overwrite applies calculated boundaries and resets `isCustomized` to `false`.
  • Verify that Karvonen delta < 20 bpm surfaces explicit localized error `zone_calc_error_karvonen_delta`.
  • Verify UI mutation lock (`isSaving == true`) disables Apply button during persistence.
  7. *9-Language Localization & Format Specifier Token Audit (`ZoneCalculatorLocalizationTest.kt`)*:
  • Verify presence of all zone calculation string keys across all 9 application locales: EN, DE, ES, FR, IT, JA, NL, PL, PT.
  • Verify Format Specifier Token Audit: for every string containing format tokens (`%d`, `%s`, `%1$d`), assert that every translation contains exactly the identical count, argument types, and indexing order as base `res/values/strings.xml`.
  8. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with zero regressions.

### TST-UI-337: **Contextual Zone Settings Navigation Upon Physiological Sensor Pairing Verification** [Contextual zone settings navigation upon physiological sensor pairing verified across configuration guards, migration heuristics, sensor discrimination, parameterized routing, guidance banner UI, auto-pairing discovery confirmation prompt dialogs, and 9-language token parity; 100% unit test pass rate.]
- **Requirement(s)**: `REQ-UI-374`
- **Component**: **Contextual Zone Settings Navigation Upon Physiological Sensor Pairing Verification**
- **Jira**: ``ATT-3189`, `ATT-4452``
- **Test Description**:
  1. *Configuration Guard & Migration Heuristics Unit Tests (`SettingsDataStoreConfiguredTest.kt`)*:
  • Verify fresh installation defaults `isHrZonesConfigured` and `isPwrZonesConfigured` to `false`.
  • Verify migration fallback heuristic: if `is_hr_zones_configured` key is null, returns `true` when `hr_max != null` or `hr_run_zones_customized == true` or `hr_bike_zones_customized == true`.
  • Verify migration fallback heuristic: if `is_pwr_zones_configured` key is null, returns `true` when `cycling_ftp != null` or `pwr_bike_zones_customized == true`.
  • Verify explicit mutators: `setHrZonesConfigured(true)` and `setPwrZonesConfigured(true)` update DataStore preferences.
  • Verify auto-calculation: `saveCalculatedPowerZones` sets `is_pwr_zones_configured = true`; `saveCalculatedHeartRateZones` sets `is_hr_zones_configured = true`.
  2. *Pairing Detection & Sensor Discrimination Unit Tests (`DeviceListViewModelPairingTest.kt`)*:
  • Verify pairing `DeviceType.HRM` with `isHrZonesConfigured == false` emits `ZoneOnboardingEvent(initialTab = 0, promptReason = "hr_paired")`.
  • Verify pairing `DeviceType.BIKE_POWER` with `isPwrZonesConfigured == false` emits `ZoneOnboardingEvent(initialTab = 2, promptReason = "pwr_paired")`.
  • Verify pairing `DeviceType.HRM` with `isHrZonesConfigured == true` emits zero events (silent completion).
  • Verify pairing `DeviceType.BIKE_POWER` with `isPwrZonesConfigured == true` emits zero events (silent completion).
  • Verify pairing non-physiological sensors (`BIKE_SPEED`, `BIKE_CADENCE`, `RUN_SPEED`, `ENVIRONMENT`, `SPEED_AND_LOCATION_GPS`) emits zero events.
  • Verify unpairing sensor (`isPaired: true -> false`) emits zero events.
  3. *Navigation Route & Deep-Link Parameter Formatting (`NavRoutesZoneTest.kt`)*:
  • Verify `NavRoutes.trainingZones(initialTab = 0, promptReason = "hr_paired")` formats to `"training_zones?initialTab=0&promptReason=hr_paired"`.
  • Verify `NavRoutes.trainingZones(initialTab = 2, promptReason = "pwr_paired")` formats to `"training_zones?initialTab=2&promptReason=pwr_paired"`.
  • Verify default call `NavRoutes.trainingZones()` produces `"training_zones"`.
  • Verify `NavRoutes.toDrawerItemId` matches `R.id.drawer_training_zones` even with query parameters.
  4. *Contextual Guidance Banner Component & Dismissal Contract (`ZoneOnboardingBannerContractTest.kt`)*:
  • Verify banner displays localized `zone_onboarding_hr_banner` for `promptReason == "hr_paired"`.
  • Verify banner displays localized `zone_onboarding_pwr_banner` for `promptReason == "pwr_paired"`.
  • Verify tapping dismiss button executes callback and marks zones configured.
  5. *9-Language Localization & Format Token Parity Audit (`ZoneOnboardingLocalizationTest.kt`)*:
  • Verify presence and non-emptiness of `zone_onboarding_hr_banner`, `zone_onboarding_pwr_banner`, and `zone_onboarding_dismiss` across all 9 application locales: `values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`.
  • Verify exact format token parity across all 9 translations.
  6. *Auto-Pairing Discovery Confirmation Dialog Verification (`DevicesTabbedViewModelTest.kt`, `DevicesTabbedScreenTest.kt`)*:
  • Verify receiving `BANALService.NEW_DEVICE_FOUND_INTENT` for unconfigured `DeviceType.HRM` sets `zonePromptState` to `ZoneOnboardingPrompt(DeviceType.HRM, ...)`.
  • Verify receiving `BANALService.NEW_DEVICE_FOUND_INTENT` for unconfigured `DeviceType.BIKE_POWER` sets `zonePromptState` to `ZoneOnboardingPrompt(DeviceType.BIKE_POWER, ...)`.
  • Verify receiving `BANALService.NEW_DEVICE_FOUND_INTENT` for configured HRM/Power or non-physiological sensors leaves `zonePromptState` null.
  • Verify calling `dismissZonePrompt()` resets `zonePromptState` to null.
  • Verify `DevicesTabbedScreen` renders `AlertDialog` when `zonePromptState` is non-null with localized strings.
  • Verify dialog "Configure Zones" action calls `onNavigateToZones` and dismisses prompt.
  • Verify dialog "Not Now" action dismisses prompt without navigating.
  7. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with zero regressions.

### TST-UI-339: **Real-Time Visual Training Zone Gauge Verification & Precision Test Suite** [Real-time visual training zone gauge verified across 5-zone and 7-zone math, dynamic ceilings, DataStore persistence, cockpit integration, and 9-language translation parity.]
- **Requirement(s)**: `REQ-UI-376`
- **Component**: **Real-Time Visual Training Zone Gauge Verification & Precision Test Suite**
- **Jira**: ``ATT-1293``
- **Test Description**:
  1. *Mathematical Unit Tests (`ZoneGaugeMathTest.kt`)*:
  • Verify 5-zone HR intra-zone needle calculation and boundary clamping.
  • Verify 7-zone Power intra-zone needle calculation and boundary clamping.
  • Verify open-ended top zone dynamic ceiling and physical 2500W cap.
  • Verify below-baseline clamping to 0.0.
  • Verify null/NaN/infinite inputs return null needle position.
  2. *DataStore Preference Persistence Tests (`SettingsDataStoreZoneGaugeTest.kt`)*:
  • Verify default `showGauge == false`.
  • Verify updating and saving `showGauge = true` persists and emits on Flow across all profiles.
  3. *Cockpit UI & SensorFieldView Integration Tests (`SensorFieldZoneGaugeTest.kt`)*:
  • Verify `SensorFieldState` carries `showGauge`.
  • Verify `SensorFieldView` renders `ZoneGaugeBar` when enabled for `HR` and `POWER`.
  • Verify `SensorFieldView` does not render gauge for non-zone sensors.
  • Verify needle is hidden on null/stale telemetry.
  4. *9-Language Translation Parity Tests (`TranslationParityTest.kt`)*:
  • Verify `zone_display_gauge` across all 9 application locales.
  5. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with zero regressions.

### TST-UI-340: **Real-Time Pedal Power Balance Verification & Precision Test Suite** [52".
• Verify null/non-finite returns "-- \]
- **Requirement(s)**: `REQ-UI-377`
- **Component**: **Real-Time Pedal Power Balance Verification & Precision Test Suite**
- **Jira**: ``ATT-1295``
- **Test Description**:
  1. *Mathematical Unit Tests (`PedalPowerBalanceMathTest.kt`)*:
  • Verify perfect balance: $P_R = 50 \rightarrow P_L = 50$, deviation 0%, tier `OPTIMAL`, $x = 0.50$.
  • Verify Left-dominant optimal: $P_R = 48 \rightarrow P_L = 52$, tier `OPTIMAL`, $x = 0.48$.
  • Verify Right-dominant optimal: $P_R = 52 \rightarrow P_L = 48$, tier `OPTIMAL`, $x = 0.52$.
  • Verify mild asymmetry: $P_R = 46 \rightarrow P_L = 54$, tier `MILD`, $x = 0.46$.
  • Verify severe asymmetry: $P_R = 40 \rightarrow P_L = 60$, tier `SEVERE`, $x = 0.40$.
  • Verify boundary clamping: $P_R < 0 \rightarrow 0\%$, $P_R > 100 \rightarrow 100\%$.
  • Verify null/NaN/infinity inputs return stale state.
  2. *Formatter Tests (`PedalPowerBalanceFormatterTest.kt`)*:
  • Verify valid input formats as "48 \

### TST-UI-341: **Compact Action Buttons & Header Height Containment Verification Suite** [Compact icon buttons and header height containment verified across contract tests, 9-language localization audit, and full clean-room suite.]
- **Requirement(s)**: `REQ-UI-378`
- **Component**: **Compact Action Buttons & Header Height Containment Verification Suite**
- **Jira**: ``ATT-3675``
- **Test Description**:
  1. *Header Source Contract & Architectural Test (`ZonesSettingsHeaderContractTest.kt`)*:
  • Verify `ZonesSettingsActivity.kt` header row replaces `TextButton` controls with `IconButton` using `Icons.Default.Calculate` and `Icons.Default.RestartAlt`.
  • Verify title `Text` specifies `maxLines = 1` and `overflow = TextOverflow.Ellipsis`.
  • Verify left title container uses `Modifier.weight(1f, fill = false)`.
  2. *9-Language ContentDescription Localization Parity (`ZoneCalculatorLocalizationTest.kt`)*:
  • Verify string tokens `zone_calc_button` and `reset_to_defaults` maintain 100% translation parity across all 9 locales (EN, DE, ES, FR, IT, JA, NL, PL, PT).
  3. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% pass rate across the full unit test suite.

### TST-UI-342: **Live Segment State-Aware Metrics, Past-Finish Rollout & PR Delta Verification Suite** [State-aware live segment metrics, post-finish rollout distance, and PR delta feedback verified across repository unit tests, composable layout contract tests, 9-language localization audit, and full clean-room suite.]
- **Requirement(s)**: `REQ-UI-379`
- **Component**: **Live Segment State-Aware Metrics, Past-Finish Rollout & PR Delta Verification Suite**
- **Jira**: ``ATT-3578``
- **Test Description**:
  1. *Repository Domain Model & PR Delta Unit Tests (`LiveSegmentsRepositoryTest.kt`)*:
  • Verify that crossing finish line forward sets `segmentStatus == LiveSegmentStatus.FINISHED`.
  • Verify that `distancePastFinish_raw` equals `distanceToEnd` and `distancePastFinish` is formatted (e.g. `"210 m"`).
  • Verify that `segmentOffset` is not overloaded with `distanceToEnd` (`segmentOffset == "--"`, `segmentOffset_raw == 0.0`).
  • Verify PR delta calculations: faster than PR produces negative delta (`"-0:10"`), slower produces positive delta (`"+0:05"`), missing PR (`prTime_raw == 0`) produces null delta.
  2. *UI State-Aware Rendering Contract & AST Tests (`LiveSegmentSheetLayoutTest.kt`)*:
  • Verify `SegmentLiveDetails` evaluates state-based visibility branches.
  • Verify that in `APPROACHING` state, lateral offset row is omitted.
  • Verify that in `FINISHED` state, `segment_status_past_finish` is used, and both `segment_status_remaining` and `segment_status_offset` are omitted.
  • Verify that in `FINISHED` state, `segment_status_pr_delta` is rendered and colored in `TTColor.Zone2` when $\le 0\text{L}$.
  3. *9-Language Localization Parity & Japanese String Fix (`SegmentLiveDetailsLocalizationTest.kt`)*:
  • Verify `segment_status_past_finish` and `segment_status_pr_delta` exist across all 9 locales: `values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/` with matching `%1$s` specifiers.
  • Verify `segment_status_offset` in Japanese (`values-ja/strings.xml`) uses `コースずれ` rather than `タイム差`.
  4. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with zero regressions across all 2,964+ unit tests.

### TST-UI-343: **Complete High-Density Asset Coverage & Defensive Compose Resource Resolution Verification Suite** [Complete high-density asset coverage across xxhdpi and xxxhdpi, systematic Compose migration to safePainterResource across all 11 components, and complete density-split crash immunity verified across contract tests and full clean-room suite; 100% test pass rate.]
- **Requirement(s)**: `REQ-UI-380`
- **Component**: **Complete High-Density Asset Coverage & Defensive Compose Resource Resolution Verification Suite**
- **Jira**: ``ATT-3529``
- **Test Description**:
  1. *High-Density Resource Coverage Audit (`DrawableDensityFallbackAuditTest.kt`)*:
  • Verify that `ant_logo.png`, `logo_protocol_bluetooth.png`, `dropbox_logo_blue.png`, and `logo.png` exist with correct dimensions and non-zero size in `drawable-xxhdpi/` and `drawable-xxxhdpi/`.
  • Verify that zero density-specific drawables lack a root fallback in `app/src/main/res/drawable/`.
  2. *Compose Defensive SafePainter Contract Tests (`ComposeSafePainterContractTest.kt`)*:
  • Verify that all 11 critical Compose UI components (`PairingButtons.kt`, `PairingProtocolBottomSheet.kt`, `DeviceTypeSelectionDialog.kt`, `DeviceItem.kt`, `SensorSourceDialog.kt`, `SensorStatus.kt`, `SearchArea.kt`, `DropboxConnectionHeader.kt`, `DropboxSettingsDialog.kt`, `ActivityTypeSelectionDialog.kt`, `ControlTrackingButton.kt`) invoke `safePainterResource` instead of raw `painterResource`.
  • Verify that semantic fallback `ImageVector` instances are provided.
  3. *Defensive Exception Handling Contract Tests (`SafePainterResourceContractTest.kt`)*:
  • Verify that `safePainterResource` catches `Resources.NotFoundException` and delegates to fallback.
  4. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with zero regressions across all unit tests.

### TST-UI-344: **Compose Theme Color Centralization Audit & Climb Gradient Parity Verification Suite** [Centralized Jetpack Compose color architecture, zero hardcoded Color(0x) hex literals outside Color.kt, and unified climb gradient resolver verified across contract and unit tests; 100% test pass rate.]
- **Requirement(s)**: `REQ-UI-381`
- **Component**: **Compose Theme Color Centralization Audit & Climb Gradient Parity Verification Suite**
- **Jira**: ``ATT-3574``
- **Test Description**:
  1. *Zero Hex Colors Outside Color.kt Architecture Contract Tests (`ThemeColorCentralizationAuditTest.kt`)*:
  • Recursively scan all `.kt` files in `app/src/main/java/` and assert exactly 0 occurrences of `Color(0x` outside `Color.kt`.
  2. *Climb Gradient Helper Resolution Tests (`ThemeColorCentralizationAuditTest.kt`)*:
  • Verify that `TTColor.getClimbGradeColor(gradePercent)` resolves correct colors for all gradient thresholds: flat (< 3.0%), mild (< 6.0%), medium (< 9.0%), steep (< 12.0%), severe (< 15.0%), and extreme (>= 15.0%).
  3. *Climb Sheets Gradient Resolution Delegation Audit (`ThemeColorCentralizationAuditTest.kt`)*:
  • Verify that both `LiveClimbSheet.kt` and `ClimbDetailSheet.kt` call `TTColor.getClimbGradeColor` and contain zero duplicate inline grade thresholds.
  4. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with zero regressions across all unit tests.

### TST-UI-345: **Canonical Metric/Speed Formatting Contract Audit and Dead Slider Purge Verification Suite** [Canonical metric and speed formatting across all remaining helper and tuning components, zero hardcoded conversion multipliers or string templates, and complete removal of dead TuningSlider verified; 100% test pass rate.]
- **Requirement(s)**: `REQ-UI-382`
- **Component**: **Canonical Metric/Speed Formatting Contract Audit and Dead Slider Purge Verification Suite**
- **Jira**: ``ATT-3575``
- **Test Description**:
  1. *Metric Formatter Contract Audit (`MetricFormatterContractTest.kt`)*:
  • Verify that `ClimbDetector.kt`, `AdvancedTuningAccordion.kt`, and `NavigationSection.kt` contain zero occurrences of `"%.1f km"`, `"%.2f km"`, or `"Anstieg"`, and explicitly reference `DistanceFormatter`.
  2. *Speed Conversion Contract Audit (`SpeedConversionContractTest.kt`)*:
  • Verify that `LapSplitCalculator.kt` and `SensorsGpsFilterSection.kt` contain zero occurrences of `* 3.6` or `"%.1f km/h"`, and explicitly reference `SpeedFormatter` and `PaceFormatter`.
  3. *Dead Slider Purge Audit (`ClusterTuningContractTest.kt`)*:
  • Verify that `ClusterTuningContent.kt` does NOT declare `fun TuningSlider(`. Assert complete absence of deprecated legacy slider composable.
  4. *Climb & Split Functional Regression Verification (`ClimbDetectorTest.kt`, `LapSplitCalculatorTest.kt`)*:
  • Verify climb detection name formatting with localized climb title and `DistanceFormatter`. Verify lap split pace/speed formatting.
  5. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with zero regressions across all unit tests.

### TST-UI-346: **Unified Internal Tracking Sheet Layout & Shared LiveTrackingEntitySheet Verification Suite** [Unified internal tracking sheet layout, shared LiveTrackingEntitySheet composable, and complete elimination of direct HorizontalDivider calls in LiveSegmentSheet and LiveClimbSheet verified across contract tests and full clean-room suite; 100% test pass rate.]
- **Requirement(s)**: `REQ-UI-383`
- **Component**: **Unified Internal Tracking Sheet Layout & Shared LiveTrackingEntitySheet Verification Suite**
- **Jira**: ``ATT-3576``
- **Test Description**:
  1. *Shared Composable Structural Contract Audit (`LiveTrackingSheetContainerTest.kt`)*:
  • Verify that `LiveTrackingEntitySheet` composable exists and is public via reflection.
  • Verify that `LiveTrackingEntitySheet.kt` applies `fillMaxWidth().wrapContentHeight()`, unbroken `MaterialTheme.colorScheme.surface`, and canonical `HorizontalDivider` (thickness 0.5.dp, horizontal padding 16.dp).
  2. *Live Segment Sheet Contract Audit (`LiveSegmentSheetContractTest.kt`)*:
  • Verify that `LiveSegmentSheet.kt` invokes `LiveTrackingEntitySheet(`.
  • Verify that `LiveSegmentSheet.kt` contains zero direct calls to `HorizontalDivider(`.
  3. *Live Climb Sheet Layout Contract Audit (`LiveClimbSheetLayoutTest.kt`)*:
  • Verify that `LiveClimbSheet.kt` invokes `LiveTrackingEntitySheet(`.
  • Verify that `LiveClimbSheet.kt` contains zero direct calls to `HorizontalDivider(`.
  • Verify that `ClimbHeader` and `ClimbLiveDetails` composable declarations exist.
  • Verify that all visual tokens (Terrain icon 32dp, `climb_route_counter`, `climb_grade`, `climb_remaining_dist`, etc.) remain intact.
  4. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with zero regressions across all unit tests.

### TST-UI-347: **Compact Zone-Tinted Bordered Preview Pills Verification Suite** [Compact zone-tinted bordered selector pills in Zone Settings preview verified across source contract tests, 130% font scale width budget & WCAG contrast math, and full clean-room suite; zero line wrapping on Z1..Z7.]
- **Requirement(s)**: `REQ-UI-384`
- **Component**: **Compact Zone-Tinted Bordered Preview Pills Verification Suite**
- **Jira**: ``ATT-3776``
- **Test Description**:
  1. *Source Contract Audit (`ZonesSettingsPreviewPillsContractTest.kt`)*:
  • Verify `ZonesSettingsActivity.kt` defines `ZonePreviewPill` and invokes it in the preview zone selector loop.
  • Verify `ZonesSettingsActivity.kt` contains zero `FilterChip(` invocations in the preview zone selector.
  • Verify `ZonePreviewPill` applies `RoundedCornerShape(8.dp)`, zone-tinted background (`0.28f` selected, `0.12f` unselected), zone-colored `BorderStroke` (`2.dp` selected, `1.dp` unselected), and single-line `Text` (`maxLines = 1`, `softWrap = false`).
  2. *Large Font Scale & Width Budget Math Verification (`ZonesSettingsPreviewPillsContractTest.kt`)*:
  • Verify that across a 360dp narrow viewport with 7 pills and 130% Large font scale, the available horizontal text width per pill ($\ge 32\text{ dp}$) strictly exceeds the measured width of `"Z7"` at `12sp * 1.3` ($\approx 20\text{ dp}$), guaranteeing zero line wrapping.
  • Verify WCAG AA contrast ($\ge 4.5:1$) of `onSurface` text over `0.12f` and `0.28f` alpha zone tints.
  3. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with zero regressions.

### TST-UI-348: **Streamlined Single-Action ZonesSettingsActivity Header Verification Suite** [Streamlined single-action header in `ZonesSettingsActivity` verified across source contract tests and full clean-room suite; status badge, reset action, last-updated row, and athlete weight row cleanly removed.]
- **Requirement(s)**: `REQ-UI-385`
- **Component**: **Streamlined Single-Action ZonesSettingsActivity Header Verification Suite**
- **Jira**: ``ATT-3777``
- **Test Description**:
  1. *Source Contract Audit (`ZonesSettingsHeaderContractTest.kt`)*:
  • Extract the header block between `// 1. THE HEADER` and `PrimaryScrollableTabRow(` in `ZonesSettingsActivity.kt`.
  • Assert `headerBlock` contains `IconButton`, `Icons.Default.Calculate`, and `R.string.zone_calc_button`.
  • Assert `headerBlock` omits `TextButton`, `SuggestionChip`, `Icons.Default.RestartAlt`, `R.string.reset_to_defaults`, `zone_calc_status_customized`, `zone_calc_status_calculated`, `zone_calc_last_updated`, `zone_calc_calibration_recommended`, `athlete_weight_title`, and `athlete_weight_edit`.
  • Assert `ZonesSettingsActivity.kt` omits `showAthleteWeightDialog` and `AthleteWeightDialog(`.
  2. *Bounded Title Container Verification (`ZonesSettingsHeaderContractTest.kt`)*:
  • Verify `headerBlock` applies `maxLines = 1`, `overflow = TextOverflow.Ellipsis`, and `weight(1f, fill = false)` on the title container.
  3. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with zero regressions.

### TST-UI-349: **Single-Line Non-Wrapping Zone Card Display Mode Segmented Button Verification Suite** [Single-line non-wrapping `ZoneCardDisplayMode` `SegmentedButton` layout and concise 9-locale chart labels (`<= 11` chars) verified across contract tests, 9-locale audit, and full clean-room suite (Rule 38).]
- **Requirement(s)**: `REQ-UI-386`
- **Component**: **Single-Line Non-Wrapping Zone Card Display Mode Segmented Button Verification Suite**
- **Jira**: ``ATT-3771``
- **Test Description**:
  1. *Source Contract Audit (`WorkoutSummaryZoneDisplayModeTest.kt`, `ZoneDistributionStackedBarLocalizationTest.kt`)*:
  • Verify that `WorkoutMasksAndCardsSection.kt`, `HeartRateZoneDistributionCard.kt`, and `PowerZoneDistributionCard.kt` configure the 3-way `ZoneCardDisplayMode` `SegmentedButton`s with `icon = {}`, `PaddingValues(horizontal = 6.dp, vertical = 0.dp)`, `MaterialTheme.typography.labelSmall`, `maxLines = 1`, `softWrap = false`, and `overflow = TextOverflow.Ellipsis`.
  • Verify that `HeartRateZoneDistributionCard.kt` and `PowerZoneDistributionCard.kt` apply `Modifier.fillMaxWidth().height(28.dp)` on `SingleChoiceSegmentedButtonRow`.
  2. *9-Locale Character Budget & Width Math Audit (`ZoneDistributionStackedBarLocalizationTest.kt`)*:
  • Parse `zone_mode_stacked_bar`, `zone_mode_columns`, and `zone_mode_histogram` across all 9 locales (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`).
  • Assert every string is non-empty and `value.length <= 11`, guaranteeing zero line wrapping and zero truncation up to 150% system font scale on a 360dp viewport.
  3. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with zero regressions.

### TST-UI-350: **Compact Legend-Free Stacked Bar Display Mode Verification Suite** [Compact legend-free Stacked Bar display mode verified across source contract tests, math tests, and full clean-room suite; detailed per-zone breakdown list omitted in Stacked Bar mode while preserved in Columns mode.]
- **Requirement(s)**: `REQ-UI-387`
- **Component**: **Compact Legend-Free Stacked Bar Display Mode Verification Suite**
- **Jira**: ``ATT-3772``
- **Test Description**:
  1. *Source & Default Parameter Contract Audit (`ZoneDistributionStackedBarTest.kt`)*:
  • Verify that `ZoneDistributionStackedBar.kt` declares `showLegend: Boolean = false` as the default parameter value on `ZoneDistributionStackedBar`.
  • Verify that `HeartRateZoneDistributionCard.kt` and `PowerZoneDistributionCard.kt` invoke `ZoneDistributionStackedBar` with `showLegend = false`.
  • Verify that `ZoneDistributionStackedBarMath` active entry filtering, 5-zone HR / 7-zone Power parity, and `RoundedCornerShape(6.dp)` / `0.5.dp` border invariants remain 100% intact.
  2. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with zero regressions.

### TST-UI-351: **Decoupled Summary/Details Zone Card Display Mode & Switcher-Free Summary Verification Suite** [Decoupled Summary (`STACKED_BAR`, `showModeSelector = false`) and Details (`HISTOGRAM`, `showModeSelector = true`) Zone Card Display Modes and legacy `"FIVE_ZONES"` / downgrade migration verified across preference tests, contract tests, and full clean-room suite.]
- **Requirement(s)**: `REQ-UI-388`
- **Component**: **Decoupled Summary/Details Zone Card Display Mode & Switcher-Free Summary Verification Suite**
- **Jira**: ``ATT-3773``
- **Test Description**:
  1. *Preference Defaults, Legacy Migration & Downgrade Safety Unit Tests (`WorkoutCardSectionPreferencesTest.kt`, `WorkoutSummaryZoneDisplayModeTest.kt`)*:
  • Verify `WorkoutCardSectionPreferences().zoneCardDisplayMode == ZoneCardDisplayMode.STACKED_BAR` and `WorkoutDetailPreferences().zoneCardDisplayMode == ZoneCardDisplayMode.HISTOGRAM`.
  • Verify `parseZoneCardDisplayMode(rawMode, defaultMode)` maps `null` to `defaultMode`, legacy `"FIVE_ZONES"` to `ZoneCardDisplayMode.COLUMNS` for both Summary and Details, valid enum strings (`"STACKED_BAR"`, `"COLUMNS"`, `"HISTOGRAM"`) to their enum values, and unknown/downgrade strings (`"UNKNOWN_FUTURE_MODE"`) to `defaultMode`.
  • Verify `ZoneCardDisplayMode.HISTOGRAM.resolveWithHistogramFallback(hasHistogram = false) == ZoneCardDisplayMode.COLUMNS`.
  2. *Source & Component Wiring Contract Audit (`WorkoutSummaryZoneDisplayModeTest.kt`, `AdvancedTuningModularityTest.kt`)*:
  • Verify `HeartRateZoneDistributionCard.kt` and `PowerZoneDistributionCard.kt` declare `showModeSelector: Boolean = true` and gate the segmented button row on `if (showModeSelector && distribution.entries.isNotEmpty())`.
  • Verify `WorkoutSummary.kt` passes `showModeSelector = false` to `HeartRateZoneDistributionCard` and `PowerZoneDistributionCard`.
  • Verify `TrackOnMapScreen.kt` resolves `effectiveHrZoneDisplayMode = hrZoneDisplayMode ?: activeDetailPrefs.zoneCardDisplayMode` and `effectivePowerZoneDisplayMode = powerZoneDisplayMode ?: activeDetailPrefs.zoneCardDisplayMode`.
  • Verify `WorkoutMasksAndCardsSection.kt` mutates both `workoutCardPrefs.copy(zoneCardDisplayMode = mode)` and `workoutDetailPrefs.copy(zoneCardDisplayMode = mode)` and stays `< 400` lines.
  3. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with zero regressions.

### TST-UI-352: **Unified 8-Icon Turn Cue Arrowhead Geometry, Baseline Grounding & Reflection Symmetry Verification Suite** [Unified filled-triangle arrowhead geometry ($W=8\text{dp}, D=4\text{dp}, o=3\text{dp}$), $y=20$ baseline grounding, and `ic_turn_left` vertical centering & 3-pair reflection symmetry verified across contract tests and full clean-room suite.]
- **Requirement(s)**: `REQ-UI-389`
- **Component**: **Unified 8-Icon Turn Cue Arrowhead Geometry, Baseline Grounding & Reflection Symmetry Verification Suite**
- **Jira**: ``ATT-3774``
- **Test Description**:
  1. *Unified Arrowhead Geometry & Baseline Grounding Contract Tests (`TurnDirectionIconContractTest.kt`)*:
  • Verify that all 8 `ic_turn_*.xml` files exist, have `24dp x 24dp` (`24x24`) viewport, contain a single `<path>` with `fillColor="#FF1976D2"` and `TST-UI-352` safety header and exactly one `M` command, and ground their vertical base stem at $y = 20.0\text{ dp}$ (`V20z` and `2.0dp` stem cut `h2` or `h-2`).
  • Verify that all 4 cardinal icons (`ic_turn_left.xml`, `ic_turn_right.xml`, `ic_turn_straight.xml`, `ic_turn_u_turn.xml`) use the unified filled triangle arrowhead with $3.0\text{ dp}$ wing overhangs (`v3`/`v-3` or `h3`/`h-3`) and $(4.0\text{ dp}, 4.0\text{ dp})$ tip legs ($W = 8.0\text{ dp}, D = 4.0\text{ dp}$).
  • Verify that all 4 diagonal icons (`ic_turn_slight_left.xml`, `ic_turn_slight_right.xml`, `ic_turn_sharp_left.xml`, `ic_turn_sharp_right.xml`) use the unified filled triangle arrowhead with $(2.12\text{ dp}, 2.12\text{ dp})$ perpendicular wing overhangs ($o = 3.0\text{ dp}$) and $5.66\text{ dp}$ axis-aligned outer wing legs ($W = 8.0\text{ dp}, D = 4.0\text{ dp}$).
  2. *Turn Left / Turn Right Vertical Centering & 3-Pair Reflection Symmetry Tests (`TurnDirectionIconContractTest.kt`)*:
  • Verify that `ic_turn_left.xml` and `ic_turn_right.xml` center their arrowhead tips at $y = 10.0$ (`(5.0, 10.0)` and `(19.0, 10.0)`) with symmetrical upper ($y = 6.0$) and lower ($y = 14.0$) wing vertices on the $y \in [9, 11]$ shaft.
  • Verify exact horizontal reflection symmetry ($x_L + x_R = 24.0 \pm 0.01, y_L = y_R \pm 0.01$) across all milestone vertices for all 3 directional pairs: `LEFT`/`RIGHT`, `SLIGHT_LEFT`/`SLIGHT_RIGHT`, and `SHARP_LEFT`/`SHARP_RIGHT`.
  3. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with zero regressions.

### TST-UI-353: **Linear ZoneGaugeBar Scaling, 10% Boundary Clipping, Under-Segment Badge & Primary-Blue Needle Verification Suite** [Proportional segment weights, 10% boundary clipping, strictly linear needle velocity, centered under-segment active zone badge, and Primary Blue needle verified across math tests, contract tests, and full clean-room suite.]
- **Requirement(s)**: `REQ-UI-390`
- **Component**: **Linear ZoneGaugeBar Scaling, 10% Boundary Clipping, Under-Segment Badge & Primary-Blue Needle Verification Suite**
- **Jira**: ``ATT-3708``
- **Test Description**:
  1. *Proportional Segment Weights & 10% Boundary Clipping Math Tests (`ZoneGaugeMathTest.kt`)*:
  • Verify that for standard Coggan 7-zone power cutoffs (`[55%, 75%, 90%, 105%, 120%, 150%]` of FTP), `ZoneGaugeMath.calculateSegmentWeights(cutoffs, isPower = true)` produces weights `[10/115f, 20/115f, 15/115f, 15/115f, 15/115f, 30/115f, 10/115f]` summing to `1.0f`.
  • Verify that across both 5-zone HR and 7-zone Power cutoffs, `calculateNeedleFraction(C_i, cutoffs)` equals the exact cumulative sum of `segmentWeights.take(i).sum()` within `1e-4f`, and needle velocity $\Delta\text{needleFraction}/\Delta\text{value}$ is strictly constant across $[X_{\min}, X_{\max}]$.
  • Verify clamping to `0.0f` for $\text{value} \le X_{\min}$ and `1.0f` for $\text{value} \ge X_{\max}$.
  2. *Source & State Contract Tests (`SensorFieldZoneGaugeTest.kt`)*:
  • Verify `ZoneGaugeState` stores `segmentWeights` and defaults safely when constructed directly.
  • Verify `ZoneGaugeBar.kt` uses an outer `Column(modifier = modifier.fillMaxWidth())`, weights each track segment and under-segment badge slot by `state.segmentWeights`, centers `"Z${state.activeZoneIndex}"` in `MaterialTheme.colorScheme.onSurface`, and renders a `4.dp` needle in `MaterialTheme.colorScheme.primary` instead of `Color.White`.
  3. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with zero regressions.

### TST-UI-354: **Athlete Profile SQLite History, Unidirectional DataStore Projection, Derived W/kg Time-Series Math, Navigation & 9-Language Parity Verification Suite** [Dedicated Athlete Profile screen, timestamped SQLite history, unidirectional DataStore projection state machine, derived W/kg time-series math, and 9-locale parity verified across unit tests, contract tests, and full clean-room suite.]
- **Requirement(s)**: `REQ-UI-391`
- **Component**: **Athlete Profile SQLite History, Unidirectional DataStore Projection, Derived W/kg Time-Series Math, Navigation & 9-Language Parity Verification Suite**
- **Jira**: ``ATT-3704``
- **Test Description**:
  1. *DTO Mapping Symmetry & Derived W/kg Time-Series Math Tests (`AthleteMetricsDtoAndMathTest.kt`)*:
  • Verify `AthleteMetricEntry.fromCursor` and `AthleteMetricEntry.fromCursorBatch` produce identical objects on mock cursors without closing the cursor (Rule 20).
  • Verify `AthleteMetricsPlotMath.computeDerivedPowerToWeightSeries` computes `F(t) / W(t)` at each distinct measurement timestamp and formats `W/kg` to 2 decimal places (`%.2f`).
  • Verify `AthleteMetricsPlotMath.filterAndConvertSeries` filters points by `1M`, `3M`, `6M`, `1Y`, `ALL` and converts `WEIGHT` via `SettingsDataStore.LBS_PER_KG` when `isImperial = true`.
  • Verify `AthleteMetricType` bounds validation for `WEIGHT_KG` (`30..300`), `HR_REST_BPM` (`30..120`), and `FTP_WATTS` (`50..600`).
  2. *Repository Seeding & Unidirectional Projection State-Machine Tests (`AthleteMetricsRepositorySyncTest.kt`)*:
  • Verify `ensureSeededFromDataStore()` seeds initial `WEIGHT_KG`, `HR_REST_BPM`, and `FTP_WATTS` once and is idempotent.
  • Verify adding/editing a newer entry updates `SettingsDataStore`, adding a backdated entry preserves the newer value in `SettingsDataStore`, deleting the latest entry reverts `SettingsDataStore` to the next most recent remaining entry, and deleting the final entry resets `SettingsDataStore` (`0.0f` / `null`).
  3. *Navigation, Source Contract & 9-Locale Parity Audit (`AthleteProfileContractAndLocalizationTest.kt`)*:
  • Verify `NavRoutes.fromDrawerItemId(R.id.drawer_athlete_profile) == NavRoutes.ATHLETE_PROFILE` and `createDrawerGroups` includes `R.id.drawer_athlete_profile` in `R.string.drawer__my_stuff`.
  • Verify `ic_athlete_profile.xml` exists in `res/drawable/` with curved SVG primitives (Rule 37).
  • Verify all new `athlete_profile_*` and `drawer_athlete_profile` strings exist across all 9 locales with matching positional format specifiers.
  4. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with zero regressions.

### TST-UI-355: **Warm Earthy Brown Zone 6 Color Token, XML/Compose Parity & Zone 7 Luminance Contrast Verification Suite** [Warm Earthy Brown (`#8B5A2B`) Zone 6 color token, 1:1 XML/Compose parity, and $\ge 3.0:1$ contrast against Zone 7 Black verified across unit tests, contract tests, and full clean-room suite.]
- **Requirement(s)**: `REQ-UI-392`
- **Component**: **Warm Earthy Brown Zone 6 Color Token, XML/Compose Parity & Zone 7 Luminance Contrast Verification Suite**
- **Jira**: ``ATT-3696``
- **Test Description**:
  1. *Token Hex Value, XML Parity & Contrast Ratio Unit Tests (`SensorFieldZoneDisplayTest.kt`, `SensorFieldZoneGaugeTest.kt`)*:
  • Verify `TTColor.Zone6 == Color(0xFF8B5A2B)` and `TTColor.Zone7 == Color(0xFF000000)`.
  • Parse `app/src/main/res/values/color.xml` and verify `<color name="zone_6">#8B5A2B</color>`.
  • Verify WCAG 2.1 relative luminance contrast ratio $(L_6 + 0.05) / (L_7 + 0.05) \ge 3.0$ ($3.44:1$) between `TTColor.Zone6` and `TTColor.Zone7`.
  2. *9-Locale Zone Label Parity Audit (`PowerZoneLocalizationTest.kt`)*:
  • Verify `zone_6_label` and `zone_7_label` exist and are non-empty across all 9 locales.
  3. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with zero regressions.

### TST-UI-356: **Control Tracking Dead Sensor Pairing Removal & Active Sensor Flow Preservation Verification Suite** [Dead sensor pairing code cleanly removed from `PairingButtons.kt`, `ControlTrackingScreen.kt`, `ControlTrackingViewModel.kt`, `TrackingTabsScreen.kt`, and `MainActivityWithNavigation.kt`, with active sensor discovery and My Sensors pairing preserved.]
- **Requirement(s)**: `REQ-UI-393`
- **Component**: **Control Tracking Dead Sensor Pairing Removal & Active Sensor Flow Preservation Verification Suite**
- **Jira**: ``ATT-3692``
- **Test Description**:
  1. *Architectural Contract Tests (`ControlTrackingDeadPairingRemovalTest.kt`)*:
  • Verify `PairingButtons.kt` does not exist on disk.
  • Verify `ControlTrackingScreen.kt` omits `isAntSupported`, `isBluetoothSupported`, `onPairingClicked`, `selectingProtocol`, `onDeviceTypeSelected`, `onCancelDeviceTypeSelection`, and `DeviceTypeSelectionDialog`.
  • Verify `ControlTrackingViewModel.kt` omits `ToPairing`, `selectingProtocol`, `_selectingProtocol`, `isAntProperlyInstalled`, `isBluetoothSupported`, `onPairingClicked`, `onDeviceTypeSelected`, and `onCancelDeviceTypeSelection`, while retaining `hasPairedRemoteDevices`, `remoteDevices`, `onSearchClicked`, `ToEditDevice`, and `ShowRecoveryDialog`.
  • Verify `TrackingTabsScreen.kt` omits `selectingProtocol`, `ToPairing`, `isAntSupported`, `isBluetoothSupported`, `onPairingClicked`, `onDeviceTypeSelected`, and `onCancelDeviceTypeSelection`.
  • Verify `MainActivityWithNavigation.kt` omits `fun startPairing(`.
  • Verify `DeviceTypeSelectionDialog.kt` still exists and is referenced by `DevicesTabbedScreen.kt`.
  2. *Safe Painter Contract Verification (`ComposeSafePainterContractTest.kt`)*:
  • Verify `ComposeSafePainterContractTest` passes across all 10 remaining active Compose components.
  3. *9-Language Localization Audit (`ControlTrackingDeadPairingRemovalTest.kt`)*:
  • Verify `devices_pair_protocol_title` remains present and non-empty across all 9 locales for `PairingProtocolBottomSheet.kt`.
  4. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with zero regressions.

### TST-UI-357: **ElevationProfile Empty State Rendering, Canonical Height & 9-Language Parity Verification Suite** [`ElevationProfile` empty state rendering (`70.dp` canonical height), unconditional `SensorGridScreen` `showEmptyState = true` wiring, and 9-locale parity verified across contract tests and full clean-room suite.]
- **Requirement(s)**: `REQ-UI-394`
- **Component**: **ElevationProfile Empty State Rendering, Canonical Height & 9-Language Parity Verification Suite**
- **Jira**: ``ATT-3779``
- **Test Description**:
  1. *Source & Canonical Height Contract Tests (`ElevationProfileEmptyStateContractTest.kt`)*:
  • Verify `calculateElevationProfileHeight(0.0) == 70.dp` (`docs/design_guidelines.md` Section 2.3).
  • Verify `ElevationProfile.kt` declares `showEmptyState: Boolean = false` on both `ElevationProfile` overloads, invokes `ElevationProfileEmptyState` when `pathPoints.size < 2 && showEmptyState`, and sizes `ElevationProfileEmptyState` via `fixedHeight ?: calculateElevationProfileHeight(0.0)`.
  • Verify `ElevationProfile.kt` includes `Icons.Default.Terrain`, `R.string.elevation_profile_empty_state`, and Light/Dark `@Preview`s (`ElevationProfileEmptyStateLightPreview`, `ElevationProfileEmptyStateDarkPreview`).
  • Verify `SensorGridScreen.kt` gates the elevation profile `Surface` on `if (state.showElevationProfile)` (without `&& resolvedProfile.pathPoints.size >= 2`) and passes `showEmptyState = true` to `ElevationProfile(...)`.
  2. *9-Language Localization Audit (`ElevationProfileEmptyStateContractTest.kt`)*:
  • Parse all 9 `values*/strings.xml` files (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`) and assert that `elevation_profile_empty_state` exists and is non-blank in all 9 locales.
  3. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with zero regressions.

### TST-UI-338: **Mathematical VectorDrawable Migration from Xfig/SVG/XCF Masters & Legacy Raster Purge Verification Suite** [All 16 sport and sensor icons migrated to mathematical XML VectorDrawables from original Xfig/SVG/XCF sources and analytical primitives; 106 legacy raster PNGs purged; 100% test pass rate.]
- **Requirement(s)**: `REQ-UI-375`
- **Component**: **Mathematical VectorDrawable Migration from Xfig/SVG/XCF Masters & Legacy Raster Purge Verification Suite**
- **Jira**: ``ATT-2859``
- **Test Description**:
  1. *DOM & Rule 37 Geometric Contract Tests (`VectorAssetResolutionContractTest.kt`)*:
  • Verify all 16 `.xml` vector drawables exist in `app/src/main/res/drawable/`, parse cleanly as XML `<vector>` documents, and include provenance headers (`REQ-UI-375`, `TST-UI-338`).
  • Verify every `<vector>` contains curved geometric primitives (`A`, `a`, `C`, `c`, `Q`, `q`) and either stroked Xfig primitives (`android:strokeWidth`) or compound `evenOdd` paths (`android:fillType="evenOdd"`).
  • Verify multi-color fidelity in `bsport_bike.xml`, `bsport_run.xml`, `bsport_other.xml`, ANT+ badges, and BLE badges.
  • Verify zero `.png` files remain for the 16 migrated icons, `bsport_*_gray.png`, or `btle_hr.png` across all `drawable*` directories.
  2. *UI & Density Fallback Tests (`SportTypeSelectorContractTest.kt`, `DrawableDensityFallbackAuditTest.kt`)*:
  • Verify `SportTypeSelector.kt` preserves `tint = Color.Unspecified` with `REQ-UI-375` documentation.
  • Verify `DrawableDensityFallbackAuditTest` passes 100%.
  3. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with zero regressions.

### TST-UI-358: **Categorized & Searchable SensorType Selector, 7-Category Exhaustive Partition & 9-Language Parity Verification Suite** [All 43 `SensorType` metrics partitioned into 7 `SensorCategory` groups, categorized accordion & instant search selector wired in `EditSensorFieldDialog.kt` without nested scroll containers, and 9-locale parity verified.]
- **Requirement(s)**: `REQ-UI-395`
- **Component**: **Categorized & Searchable SensorType Selector, 7-Category Exhaustive Partition & 9-Language Parity Verification Suite**
- **Jira**: ``ATT-3852``
- **Test Description**:
  1. *Exhaustive 43-Metric `SensorCategory` Partition & Search Filter Unit Tests (`SensorCategoryAndSelectorContractTest.kt`)*:
  • Verify `SensorCategory.values().size == 7` and every category returns a non-zero `titleResId`.
  • Verify all 43 `SensorType.values()` return a non-null `category` matching the exact 7-category partition (`TIME_AND_LAPS` = 5, `SPEED_PACE_AND_DISTANCE` = 4, `CADENCE_AND_RUNNING` = 2, `HEART_RATE_AND_ENERGY` = 2, `POWER_AND_PEDALING` = 13, `ELEVATION_AND_NAVIGATION` = 9, `SYSTEM_ENVIRONMENT_AND_DIAGNOSTICS` = 8).
  • Verify `SensorTypeSearchFilter.filterByQuery` matches case-insensitively across localized full name, short name, unit string, and enum name, and returns all items when the query is blank.
  2. *Source Contract, Bottom Sheet Scroll Safety & 9-Language Localization Audit (`SensorCategoryAndSelectorContractTest.kt`)*:
  • Verify `EditSensorFieldDialog.kt` defines and invokes `SensorTypeSelectorSection`, references `SensorCategory`, `R.string.sensor_type_search_hint`, and `R.string.sensor_type_no_matches`, and contains zero `verticalScroll(` or `LazyColumn(` calls (`docs/design_guidelines.md` Section 5.8).
  • Parse all 9 `values*/strings.xml` files and verify all 9 new string keys (`sensor_category_*`, `sensor_type_search_hint`, `sensor_type_no_matches`) exist and are non-blank across all 9 locales.
  3. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with zero regressions.

### TST-UI-359: **AthleteProfileScreen System Navigation Bar Bottom Inset Clearance Verification Suite** [System navigation bar bottom inset clearance (`bottom = 16.dp + navBarBottomPadding`) on `AthleteProfileScreen` `LazyColumn` verified across contract tests, 9-locale audit, and full clean-room suite (Rule 41).]
- **Requirement(s)**: `REQ-UI-396`
- **Component**: **AthleteProfileScreen System Navigation Bar Bottom Inset Clearance Verification Suite**
- **Jira**: ``ATT-3850``
- **Test Description**:
  1. *Source Contract Audit (`AthleteProfileContractAndLocalizationTest.kt`)*:
  • Verify `AthleteProfileScreen.kt` imports `WindowInsets`, `asPaddingValues`, and `navigationBars` from `androidx.compose.foundation.layout`.
  • Verify `AthleteProfileContent` computes `WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()` and configures the `LazyColumn` `contentPadding` with `start = 16.dp`, `top = 16.dp`, `end = 16.dp`, and `bottom = 16.dp + navBarBottomPadding` (Rule 41).
  • Verify `AthleteProfileScreen.kt` retains `.statusBarsPadding()` on the top header row.
  2. *9-Language Localization Parity Audit (`AthleteProfileContractAndLocalizationTest.kt`)*:
  • Verify all 22 `athlete_profile_*` and `drawer_athlete_profile` strings remain present with matching positional format specifiers across all 9 locales.
  3. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with zero regressions.

### TST-UI-360: **Navigation Drawer 5-Group Restructuring, Consolidated Equipment & Profile/Zones Tabs, and Collapsible Secondary Sections Verification Suite** [Restructured 5-group Navigation Drawer, consolidated Equipment and Profile & Zones tabbed destinations, and collapsible Online Communities & Settings sections verified across unit tests, contract tests, 9-locale audit, and full clean-room suite.]
- **Requirement(s)**: `REQ-UI-397`
- **Component**: **Navigation Drawer 5-Group Restructuring, Consolidated Equipment & Profile/Zones Tabs, and Collapsible Secondary Sections Verification Suite**
- **Jira**: ``ATT-3851``
- **Test Description**:
  1. *Drawer Structure, Collapsible Groups & Consolidated Selection Tests (`NavigationDrawerStateTest.kt`)*:
  • Verify `createDrawerGroups(R.string.tab_start)` returns 5 groups (`drawer__training` = 3, `drawer__maps` = 5, `drawer__my_stuff` = 4 in Athlete->Gear order `[drawer_athlete_profile, drawer_my_sensors, drawer_equipment, drawer_sport_types]`, `prefsOnlineCommunities` = 4, `drawer__settings` = 6 including `drawer_advanced_tuning`), with `drawer__expert_settings` removed.
  • Verify `collapsible == false` on the top 3 groups (`12` default visible items) and `collapsible == true` on `prefsOnlineCommunities` and `drawer__settings`.
  • Verify `isDrawerGroupInitiallyExpanded` returns `false` by default for collapsible groups and `true` when `selectedItemId` belongs to the collapsible group.
  • Verify `isDrawerItemSelected` matches `drawer_athlete_profile` for both `drawer_athlete_profile` and `drawer_training_zones`, and `drawer_equipment` for `drawer_equipment`, `drawer_bikes`, and `drawer_shoes`.
  2. *Route & Unified Profile/Zones Contract & 9-Locale Parity Tests (`SingleActivityNavigationTest.kt`, `AthleteProfileContractAndLocalizationTest.kt`)*:
  • Verify `R.id.drawer_equipment` <-> `NavRoutes.EQUIPMENT` mapping alongside backward-compatible route mappings.
  • Verify `AthleteProfileScreen.kt` hosts `PrimaryScrollableTabRow` and `HorizontalPager` with `athlete_profile_tab_profile`, `tab_hr_run`, `tab_hr_bike`, `tab_pwr_bike`, and `ZoneProfileTabContent`.
  • Verify `drawer_athlete_profile`, `athlete_profile_title`, and `athlete_profile_tab_profile` exist across all 9 locales.
  3. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with zero regressions.

### TST-UI-361: **ZoneDistributionStackedBar Per-Segment Percentage Formatting, Width Threshold & Source Contract Verification Suite** [Space-aware per-segment percentage labels below `ZoneDistributionStackedBar` verified across math threshold unit tests, source contract assertions, 9-locale parity tests, and full clean-room regression suite.]
- **Requirement(s)**: `REQ-UI-398`
- **Component**: **ZoneDistributionStackedBar Per-Segment Percentage Formatting, Width Threshold & Source Contract Verification Suite**
- **Jira**: ``ATT-3853``
- **Test Description**:
  1. *Pure Percentage Formatting & Width-Threshold Unit Tests (`ZoneDistributionStackedBarTest.kt`)*:
  • Verify `ZoneDistributionStackedBarMath.formatSegmentPercentage` formats `0f`, `5.2f`, `24.4f`, `52.6f`, and `100f` as `"0%"`, `"5%"`, `"24%"`, `"53%"`, and `"100%"`.
  • Verify `ZoneDistributionStackedBarMath.minRequiredSegmentWidthDp` and `canFitPercentageLabel` return `true` when `segmentWidthDp >= labelText.length * charWidthDp + horizontalPaddingDp` (e.g., `"24%"` at `24dp`..`60dp`) and `false` when `segmentWidthDp` is too narrow (e.g., `"5%"` at `8dp`, `"24%"` at `15dp`, or empty label).
  2. *Component Source Contract & 9-Language Localization Parity Audit (`ZoneDistributionStackedBarTest.kt`, `ZoneDistributionStackedBarLocalizationTest.kt`)*:
  • Verify `ZoneDistributionStackedBar.kt` uses `BoxWithConstraints(modifier = Modifier.weight(entry.percentage)` in the percentage row beneath the bar, `ZoneDistributionStackedBarMath.formatSegmentPercentage(entry.percentage)`, `ZoneDistributionStackedBarMath.canFitPercentageLabel`, `MaterialTheme.typography.labelSmall`, `maxLines = 1`, `softWrap = false`, and `didOverflowWidth` overflow guard.
  • Verify `ZoneDistributionStackedBarLocalizationTest` passes across all 9 supported application locales.
  3. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with zero regressions.

### TST-UI-362: **Critical Power (CP), W', and Skiba 1-Hz W'bal Athlete Profile, Live Cockpit, Aftermath & 9-Language Parity Verification Suite** [Critical Power (CP), W', and Skiba 1-Hz W'bal verified across physiology math unit tests, live device emission tests, Athlete Profile repository sync tests, 9-locale audit, and full clean-room regression suite.]
- **Requirement(s)**: `REQ-UI-399`
- **Component**: **Critical Power (CP), W', and Skiba 1-Hz W'bal Athlete Profile, Live Cockpit, Aftermath & 9-Language Parity Verification Suite**
- **Jira**: ``ATT-3862``
- **Test Description**:
  1. *Skiba 1-Hz $W'_{\text{bal}}$ & 2-Point CP/W' Solver Math Tests (`PowerPhysiologyCalculatorTest.kt`, `AthleteMetricsDtoAndMathTest.kt`)*:
  • Verify `PowerPhysiologyCalculator.estimateCpAndWPrime` accurately solves $W_i = P_i \cdot t_i = CP \cdot t_i + W'$ for valid 2-point efforts and returns `null` for degenerate inputs ($t_2 \le t_1$, $P_1 \le P_2$, or non-positive $CP$/$W'$).
  • Verify Skiba's 1-Hz depletion ($P > CP$), clamping at $0\text{ J}$ (never negative), exponential reconstitution ($P \le CP$ with $\tau_{W'} = 546 e^{-0.01(CP - P)} + 316$), and summary extrema (`minWPrimeBalKj`, `minWPrimeBalPct`, `isEmptyTankWarning` when $< 10\%$).
  2. *Athlete Profile Sync & Live Cockpit `CyclingPowerMetricsDevice` Tests (`AthleteMetricsRepositorySyncTest.kt`, `CyclingPowerMetricsDeviceTest.kt`, `SensorCategoryAndSelectorContractTest.kt`)*:
  • Verify `AthleteMetricType.CP_WATTS` (`50..1000`) and `AthleteMetricType.W_PRIME_KJ` (`5..60`) validation and unidirectional `SettingsDataStore` synchronization.
  • Verify `CyclingPowerMetricsDevice` emits `W_PRIME_BAL` in kJ on every 1-Hz tick, depletes above $CP$, recharges below $CP$ and during pauses, and resets to $W'$ on `resetSession()`.
  • Verify `SensorCategoryAndSelectorContractTest` passes with `44` total sensors (`14` in `POWER_AND_PEDALING`).
  3. *9-Language Localization & Source Contract Audit (`AthleteProfileContractAndLocalizationTest.kt`)*:
  • Verify all new string keys exist and are non-blank with matching positional specifiers across all 9 locales (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`).
  4. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with zero regressions.

### TST-UI-363: **Efficiency Factor (EF) & Aerobic Decoupling (Pw:Hr / Pa:Hr) Calculation, DTO Symmetry & 9-Language Parity Verification Suite** [Efficiency Factor (EF) and Aerobic Decoupling (Pw:Hr / Pa:Hr) verified across physiology math unit tests, Rule 20 DTO mapping symmetry tests, 9-locale audit, and full clean-room regression suite.]
- **Requirement(s)**: `REQ-UI-400`
- **Component**: **Efficiency Factor (EF) & Aerobic Decoupling (Pw:Hr / Pa:Hr) Calculation, DTO Symmetry & 9-Language Parity Verification Suite**
- **Jira**: ``ATT-3861``
- **Test Description**:
  1. *Pure Domain Efficiency Factor & Aerobic Decoupling Math Tests (`PowerPhysiologyCalculatorTest.kt`)*:
  • Verify `calculateNormalizedPower` computes 30s rolling 4th-power average (with growing $1..29\text{ s}$ warm-up window) and handles empty/null/zero inputs safely.
  • Verify `calculateEfficiencyFactorPower` ($\text{NP} / \text{Avg\_HR}$ in $\text{W/bpm}$) and `calculateEfficiencyFactorSpeed` ($(\text{Speed}_{\text{m/s}} \times 60.0) / \text{Avg\_HR}$ in $\text{m/min/bpm}$).
  • Verify `calculateAerobicDecouplingFromEf` and `computeAerobicEfficiencyAndDecoupling` split paired active samples into $H_1$ and $H_2$, compute $\text{Decoupling }\% = ((\text{EF}_1 - \text{EF}_2) / \text{EF}_1) \times 100.0$, and classify $< 5.0\%$ as coupled (`isHighDrift = false`) and $\ge 5.0\%$ as high cardiac drift (`isHighDrift = true`) across cycling (`Pw:Hr`) and running (`Pa:Hr`).
  2. *Rule 20 DTO & Snapshot Mapping Symmetry Tests (`WorkoutDataMappingSymmetryTest.kt`)*:
  • Verify `WorkoutDataMapper.fromCursor` (single & batch) and `WorkoutDataMapper.fromSnapshot` produce identical `efficiencyFactor`, `aerobicDecouplingPct`, `isPowerBasedEfficiency`, and `isAerobicDecouplingHighDrift` values on `WorkoutData` and `WorkoutDetailsData`.
  3. *9-Language Localization Parity Audit (`AthleteProfileContractAndLocalizationTest.kt`)*:
  • Verify all new EF and Aerobic Decoupling string resources exist and are non-blank with matching positional specifiers across all 9 locales (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`).
  4. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with zero regressions.

### TST-UI-364: **Performance Management Chart (PMC) Impulse-Response Math, Multi-Sport hrTSS Fallback & 9-Language Parity Verification Suite** [Performance Management Chart (PMC) for Fitness (CTL), Fatigue (ATL), Form / Race-Readiness (TSB), Workload Ratio (ACWR), and 7d Ramp Rate with multi-sport hrTSS fallback verified across unit tests, UI contract tests, 9-locale audit, and full clean-room regression suite.]
- **Requirement(s)**: `REQ-UI-401`
- **Component**: **Performance Management Chart (PMC) Impulse-Response Math, Multi-Sport hrTSS Fallback & 9-Language Parity Verification Suite**
- **Jira**: ``ATT-3859``
- **Test Description**:
  1. *Pure Domain PMC & Multi-Sport $\text{hrTSS}$ Unit Tests (`PowerPhysiologyCalculatorTest.kt`)*:
  • Verify `calculateHrTss` returns `100.0` for `3600 s` at threshold HR ($HR_r = 0.85$) and scales non-linearly via Banister's $\text{TRIMP}$ ($HR_r \cdot e^{1.92 \cdot HR_r}$).
  • Verify `resolveWorkoutTrainingLoad` follows the 3-tier hierarchy: stored `powerTss` -> power-derived `TSS` (`NP` / `avgPower` + `activeTimeSeconds` + `ftpWatts`) -> `hrTSS` (`avgHrBpm` + `activeTimeSeconds` + `hrRestBpm` / `hrMaxBpm`).
  • Verify `computePerformanceManagementChart` aggregates daily training stress $TSS_t$ across calendar days, fills rest days with $0.0$, and computes $\text{CTL}_t = \text{CTL}_{t-1} + (TSS_t - \text{CTL}_{t-1}) / 42.0$, $\text{ATL}_t = \text{ATL}_{t-1} + (TSS_t - \text{ATL}_{t-1}) / 7.0$, $\text{TSB}_t = \text{CTL}_{t-1} - \text{ATL}_{t-1}$, $\text{ACWR}_t = \text{ATL}_t / \text{CTL}_t$, $\text{RampRate}_t = \text{CTL}_t - \text{CTL}_{t-7}$, `TsbReadinessZone`, `AcwrZone`, and `isAggressiveRampUp` ($> 8.0\text{ CTL/wk}$).
  2. *UI Contract & 9-Language Localization Parity Audit (`AthleteProfileContractAndLocalizationTest.kt`)*:
  • Verify `AthleteProfileScreen.kt` defines and invokes `PerformanceManagementChartCard` and `PerformanceManagementChartCanvas`.
  • Verify all 17 `athlete_profile_pmc_*` string keys exist and are non-blank with matching positional format specifiers across all 9 locales (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`).
  3. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with zero regressions.

### TST-UI-365: **Power-Based Training Metrics (NP, IF, TSS, Work kJ, W/kg, VI) Symmetric DTO Mapping, Aftermath UI Contract & 9-Language Parity Verification Suite** [Power-based training metrics (`NP`, `IF`, `TSS`, `Work kJ`, `W/kg`, `VI`) in Workout Summaries and Workout Details verified across Rule 20 DTO mapping symmetry tests, UI contract tests, 9-locale audit, and full clean-room regression suite.]
- **Requirement(s)**: `REQ-UI-402`
- **Component**: **Power-Based Training Metrics (NP, IF, TSS, Work kJ, W/kg, VI) Symmetric DTO Mapping, Aftermath UI Contract & 9-Language Parity Verification Suite**
- **Jira**: ``ATT-3858``
- **Test Description**:
  1. *Rule 20 DTO & Snapshot Mapping Symmetry Tests (`WorkoutDataMappingSymmetryTest.kt`)*:
  • Verify `WorkoutDataMapper.fromCursor` (single & batch) and `WorkoutDataMapper.fromSnapshot` produce identical `normalizedPowerWatts`, `intensityFactor`, `tss`, `workKj`, `avgPowerToWeight`, `maxPowerToWeight`, and `variabilityIndex` values on both `WorkoutData` and `WorkoutDetailsData`.
  • Verify `resolvePowerTrainingSummary` prioritizes `ExtremaType.END` over `ExtremaType.AVG` for cumulative session metrics (`NORMALIZED_POWER`, `INTENSITY_FACTOR`, `TSS`, `WORK_KJ`), computes `variabilityIndex = NP / Avg_Power`, and provides on-the-fly fallback derivation from `avgPowerWatts`, `activeTimeSec`, `effectiveFtpWatts`, and `effectiveWeightKg`.
  2. *Aftermath UI Contract & 9-Language Localization Parity Audit (`AthleteProfileContractAndLocalizationTest.kt`)*:
  • Verify `WorkoutDetails.kt` renders the Power Training Metrics summary section using `workout_power_metrics_*` string resources.
  • Verify all 6 `workout_power_metrics_*` string keys exist and are non-blank with matching positional format specifiers across all 9 locales (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`).
  3. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with zero regressions.

### TST-UI-366: **Physiology & Training Models Advanced Tuning Category, Consumer UI Gating, < 400 Line Modularity & 9-Language Parity Verification Suite** ['Physiology & Training Models' Advanced Settings toggles, progressive UI disclosure across WorkoutDetails, AthleteProfileScreen, Telemetry Charts, and EditSensorFieldDialog, < 400 line modularity, and 9-locale parity verified.]
- **Requirement(s)**: `REQ-UI-403`
- **Component**: **Physiology & Training Models Advanced Tuning Category, Consumer UI Gating, < 400 Line Modularity & 9-Language Parity Verification Suite**
- **Jira**: ``ATT-3929``
- **Test Description**:
  1. *TuningConfig Defaults, Accordion Enum & Modularity Tests (`AdvancedTuningAccordionTest.kt`, `AdvancedTuningModularityTest.kt`, `AdvancedTuningScreenTest.kt`)*:
  • Verify `TuningConfig()` defaults `enableCogganPowerMetrics = true`, `enablePmcModel = false`, `enableAerobicEfficiencyModel = false`, and `enableCriticalPowerModel = false`.
  • Verify `TuningSection.entries` includes `PHYSIOLOGY_MODELS`, `PhysiologyModelsSection.kt` exists in `ui/settings/tuning/categories/` and is wired into `AdvancedTuningScreen.kt` and `AdvancedTuningDialog.kt`, and every `.kt` file under `ui/settings/tuning/` has strictly `< 400` lines.
  2. *Consumer UI Gating & Sensor Filter Contract Tests (`PhysiologyModelsVisibilityContractTest.kt`)*:
  • Verify `WorkoutDetails.kt` gates `NP`, `IF`, `TSS`, `VI` on `enableCogganPowerMetrics` while keeping `workKj` and `avgPowerToWeight` (`W/kg`) unconditionally visible when power/work data exists; gates `EF` and `Pw:Hr` / `Pa:Hr` on `enableAerobicEfficiencyModel`; and gates `minWPrimeBalKj` and `isEmptyTankWarning` on `enableCriticalPowerModel`.
  • Verify `AthleteProfileScreen.kt` gates `CP` and `W'` summary cards, `PlotMetricType.CP` / `PlotMetricType.W_PRIME` plot selector chips, and `CP_WATTS` / `W_PRIME_KJ` history filter chips on `enableCriticalPowerModel`, and gates `PerformanceManagementChartCard` on `enablePmcModel`.
  • Verify `TrackOnMapScreen.kt`, `MapDetailLayout.kt`, and `WorkoutSummary.kt` gate `TelemetryMetricType.W_PRIME_BAL` telemetry graphs on `enableCriticalPowerModel`.
  • Verify `EditSensorFieldViewModel.kt` filters `NORMALIZED_POWER`, `INTENSITY_FACTOR`, and `TSS` when `!enableCogganPowerMetrics` and `W_PRIME_BAL` when `!enableCriticalPowerModel`.
  3. *9-Language Localization Parity Audit (`PhysiologyModelsVisibilityContractTest.kt`)*:
  • Verify all new `tuning_cat_physiology_*` and `tuning_physiology_*` string keys exist and are non-blank with matching positional format specifiers across all 9 locales (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`).
  4. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with zero regressions.

### TST-UI-367: **WorkoutDetails Section 5.10 AltitudeRow + MetricItem Grouped Row Layout & 9-Language Parity Verification Suite** [`WorkoutDetails.kt` power and physiology rows refactored to the canonical `AltitudeRow` + `MetricItem(layout = MetricLayout.VERTICAL, modifier = Modifier.weight(1f))` pattern (`docs/design_guidelines.md` Section 5.10), eliminating all `FlowRow` pill badges and raw inline text rows.]
- **Requirement(s)**: `REQ-UI-404`
- **Component**: **WorkoutDetails Section 5.10 AltitudeRow + MetricItem Grouped Row Layout & 9-Language Parity Verification Suite**
- **Jira**: ``ATT-3930``
- **Test Description**:
  1. *Section 5.10 Grouped Row Structure & Anti-Pattern Removal Contract Tests (`PhysiologyModelsVisibilityContractTest.kt`, `AthleteProfileContractAndLocalizationTest.kt`)*:
  • Verify `WorkoutDetails.kt` contains zero `FlowRow` and zero `surfaceContainerHighest` pill badge usages (`docs/design_guidelines.md` Section 5.10).
  • Verify `WorkoutDetails.kt` defines and invokes `WorkoutDomainRow`, `PowerTrainingMetricsSection`, `AerobicEfficiencyRow`, and `AnaerobicBatteryRow` using `24.dp` domain icons (`R.drawable.ic_power`, `R.drawable.ic_cardio_load`, `R.drawable.ic_battery_full`), `Spacer(modifier = Modifier.width(20.dp))`, and equal-weight `MetricItem(layout = MetricLayout.VERTICAL, modifier = Modifier.weight(1f))` columns.
  • Verify `PowerTrainingMetricsSection` gates `NP`, `IF`, `TSS`, and `VI` on `enableCogganPowerMetrics` while keeping `workKj` and `avgPowerToWeight` (`W/kg`) unconditionally visible in their own `MetricItem` row.
  • Verify `AerobicEfficiencyRow` is gated on `enableAerobicEfficiencyModel && data.efficiencyFactor != null` and renders 2 equal-weight `MetricItem` columns (`EF` and `Pw:Hr` / `Pa:Hr` with Coupled/High Drift `secondaryValue`).
  • Verify `AnaerobicBatteryRow` is gated on `enableCriticalPowerModel && data.minWPrimeBalKj != null && data.minWPrimeBalPct != null` and renders 2 equal-weight `MetricItem` columns (`Min W'bal` in `kJ` and `Min W'bal %` with Empty Tank `secondaryValue`).
  2. *9-Language Localization Parity Audit (`AthleteProfileContractAndLocalizationTest.kt`)*:
  • Parse all 9 `values*/strings.xml` files (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`) and verify that all new domain/metric label and value format keys exist and are non-blank with matching positional format specifiers across all 9 locales.
  3. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with zero regressions.

### TST-UI-368: **Profile & Zones in Training Drawer Section and Collapsible My Stuff Verification Suite** ['Profile & Zones' moved to the bottom of the 'Training' drawer section (`4` items) and 'My Stuff' (`3` items) configured with `collapsible = true` (`9` default visible destination items) verified across unit tests and full clean-room regression suite.]
- **Requirement(s)**: `REQ-UI-405`
- **Component**: **Profile & Zones in Training Drawer Section and Collapsible My Stuff Verification Suite**
- **Jira**: ``ATT-3928``
- **Test Description**:
  1. *Drawer Structure, Training Group Ordering & Collapsible My Stuff Tests (`NavigationDrawerStateTest.kt`, `AthleteProfileContractAndLocalizationTest.kt`)*:
  • Query `createDrawerGroups(R.string.tab_start)` by `titleRes` (`groups.first { it.titleRes == ... }`) rather than brittle index.
  • Verify `R.string.drawer__training` has `collapsible == false` and places `R.id.drawer_athlete_profile` (`R.drawable.ic_athlete_profile`, `R.string.drawer_athlete_profile`) as its 4th destination item (`[drawer_start_tracking, drawer_workouts, drawer_periods, drawer_athlete_profile]`).
  • Verify `R.string.drawer__my_stuff` has `collapsible == true` and contains strictly 3 destination items: `[R.id.drawer_my_sensors, R.id.drawer_equipment, R.id.drawer_sport_types]`.
  • Verify non-collapsible groups (`Training` = 4 destination items, `Maps` = 5 destination items) yield `9` default visible `DrawerItemConfig` rows (`groups.filter { !it.collapsible }.sumOf { it.items.size } == 9`, separate from the `5` section header rows) and collapsible groups (`My Stuff` = 3, `Online Communities` = 4, `Settings` = 6) yield `13` hidden destination items (`9 + 13 = 22` total unique items across `5` groups).
  • Verify `isDrawerGroupInitiallyExpanded(myStuffGroup, selectedItemId)` returns `false` when `R.id.drawer_start_tracking` is selected and iterates exhaustively over `myStuffGroup.items` (plus consolidated equipment aliases `R.id.drawer_bikes` and `R.id.drawer_shoes`) asserting `true` for every item.
  2. *9-Language Localization Parity Audit (`AthleteProfileContractAndLocalizationTest.kt`)*:
  • Verify `drawer_athlete_profile`, `drawer__training`, and `drawer__my_stuff` exist across all 9 supported application locales.
  3. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with zero regressions.

### TST-UI-369: **Transparent Inner Dial Fill (#00000000) on bsport_other.xml Stopwatch Vector Icon Verification Suite** [Transparent inner dial (`#00000000`) on `bsport_other.xml` stopwatch icon verified in `VectorAssetResolutionContractTest` and full clean-room regression suite.]
- **Requirement(s)**: `REQ-UI-406`
- **Component**: **Transparent Inner Dial Fill (`#00000000`) on `bsport_other.xml` Stopwatch Vector Icon Verification Suite**
- **Jira**: ``ATT-3926``
- **Test Description**:
  1. *DOM & Palette Contract Tests (`VectorAssetResolutionContractTest.kt`)*:
  • Verify `bsport_other.xml` parses as a valid `<vector>` DOM document where every `<path>` element — including `path[0]` (the main stopwatch dial circle `M 31.63,110.42 A 63.87,63.87 ...`) — declares `android:fillColor="#00000000"`.
  • Verify `bsport_other.xml` does not contain `#F0F0F0`.
  • Verify `bsport_other.xml` preserves `#FFC700` (gold ring and stems), `#000090` (blue left button cap), `#FF0000` (red center button cap), and `#009000` (green right button cap) strokes.
  2. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with zero regressions.

### TST-UI-370: **Smart Auto-Collapse & Inline Expansion of Export Status Unit and Contract Verification Suite** [Smart auto-collapse (`isExpanded = false`), 1-line succeeded summary header, automatic surfacing of attention groups (`waiting`, `running`, `failed`), and inline `AnimatedVisibility` expansion verified in `ExportStatusCollapseTest`.]
- **Requirement(s)**: `REQ-UI-407`
- **Component**: **Smart Auto-Collapse & Inline Expansion of Export Status Unit and Contract Verification Suite**
- **Jira**: ``ATT-3938``
- **Test Description**:
  1. *Pure Domain Classification & Summary Formatting Unit Tests (`ExportStatusCollapseTest.kt`)*:
  • Verify `isFullySucceeded` and `needsAttentionOrInProgress` across all combinations of `hasContent`, `waitingLine`, `runningLine`, `succeededLine`, and `failedLine`.
  • Verify `formatCollapsedSucceededSummary` returns `"✅ File, Dropbox, Communities"` when all three succeeded, `"✅ File, Dropbox"` when `Communities` has a failure/ongoing job, and `null` when no group is fully succeeded.
  • Verify `filterAttentionGroups` returns `emptyList()` when all groups succeeded, and returns only groups with `waitingLine`, `runningLine`, or `failedLine` when present.
  2. *UI Source Contract & Bottom Sheet Integrity Tests (`ExportStatusCollapseTest.kt`, `ModalBottomSheetDialogsIntegrityTest.kt`)*:
  • Verify `ExportStatusGroup.kt` uses `AnimatedVisibility`, `Icons.Default.ExpandMore`, `Icons.Default.ExpandLess`, `formatCollapsedSucceededSummary`, and `filterAttentionGroups`, and preserves `ExportDetailsDialog` (`ModalBottomSheetDialogsIntegrityTest`).
  3. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with zero regressions.

### TST-UI-371: **Individually Configurable Workout Detail Tabs, Conflict-Free Layout Modes, Smart Gesture Isolation, Auto-Hiding Empty Tabs & 9-Language Parity Verification Suite** [Individually configurable Workout Detail tabs, 3 conflict-free layout modes, smart gesture isolation, auto-hiding empty tabs, tab configuration bottom sheet, dual entry points, and 9-locale parity verified in `WorkoutDetailTabsTest`.]
- **Requirement(s)**: `REQ-UI-408`
- **Component**: **Individually Configurable Workout Detail Tabs, Conflict-Free Layout Modes, Smart Gesture Isolation, Auto-Hiding Empty Tabs & 9-Language Parity Verification Suite**
- **Jira**: ``ATT-3920``
- **Test Description**:
  1. *Domain Model, Default 4-Tab Archetype & Serialization Unit Tests (`WorkoutDetailTabsTest.kt`)*:
  • Verify `WorkoutDetailSectionType.entries.size == 10`, `WorkoutSectionType.entries.size == 9`, and `WorkoutDetailTabConfig.DEFAULT_TABS` defines the 4 default tabs (`OVERVIEW`: `[DETAILS, DESCRIPTION, EXTREMA, EXPORT_STATUS]`, `MAP_AND_GRAPHS`: `[MAP, ELEVATION, CHARTS]`, `ZONES`: `[ZONES]`, `LAPS_AND_SEGMENTS`: `[LAPS, STRAVA]`).
  • Verify `serializeTabs` and `deserializeTabs` round-trip default and custom tabs, escape delimiters, ignore unknown tokens, deduplicate sections, and fall back to `DEFAULT_TABS` on null/blank/invalid inputs.
  2. *Layout Mode, Map Pinning, Gesture Isolation, Auto-Hiding & CRUD Unit Tests (`WorkoutDetailTabsTest.kt`)*:
  • Verify `normalizedSections` pins `MAP` at index `0` whenever `MAP` is present and `layoutMode` resolves to `MODE_A_CARD_SCROLL` (`MAP` absent), `MODE_B_SPLIT_MAP` (`MAP` + other sections), or `MODE_C_FULL_MAP` (`MAP` only).
  • Verify `requiresHorizontalGestureIsolation` returns `true` for tabs containing active `MAP`, `ELEVATION`, or `CHARTS` and `false` for card-only tabs.
  • Verify `filterVisibleWorkoutDetailTabs` hides tabs with zero available sections for the current workout while keeping at least 1 tab.
  • Verify `addCustomTab`, `renameTab`, `moveTab`, `deleteTab` (min 1 tab), `toggleSectionInTab`, and `moveSectionInTab` (`MAP` pinned at index `0`).
  3. *UI Integration, Tuning Modularity (`< 400` lines) & 9-Language Parity Audit (`WorkoutDetailTabsTest.kt`)*:
  • Verify `TrackOnMapScreen.kt` routes to `WorkoutDetailTabbedContent` and `WorkoutDetailTabsConfigSheet` while preserving legacy `MapDetailLayout` fallback contracts, `MyPreferenceManager.kt` exposes `workoutDetailTabsOrderFlow`, and every `.kt` file under `ui/settings/tuning/` has `< 400` lines.
  • Verify all new `workout_detail_tab_*`, `workout_detail_section_*`, and `workout_detail_tabs_config_*` string resources exist and are non-blank across all 9 locales (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`).
  4. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with zero regressions.

### TST-UI-372: **Default BIKE_POWER Cockpit Tabs (Standard, Power, Map, Laps), W_PRIME_BAL Catalog Inclusion, Live Cockpit Gating & 9-Language Parity Verification Suite** [`BIKE_POWER` 4-tab default cockpit layouts (`Standard`, `Power`, `Map`, `Laps`), `W_PRIME_BAL` sensor catalog inclusion, memoized live cockpit gating (`enableCogganPowerMetrics`, `enableCriticalPowerModel`), empty-row zero-height collapse, all-gated empty grid & null boot fallback (`TST-UI-372.5`), custom-layout coordinate preservation, and 9-locale parity verified in `BikePowerCockpitDefaultsAndGatingTest` and `TrackingViewsDatabaseManagerTest`.]
- **Requirement(s)**: `REQ-UI-409`
- **Component**: **Default `BIKE_POWER` Cockpit Tabs (`Standard`, `Power`, `Map`, `Laps`), `W_PRIME_BAL` Catalog Inclusion, Live Cockpit Gating & 9-Language Parity Verification Suite**
- **Jira**: ``ATT-3921`, `ATT-4447``
- **Test Description**:
  1. *`ActivityType.BIKE_POWER` Sensor Catalog Unit Test (`BikePowerCockpitDefaultsAndGatingTest.kt`)*:
  • Verify `ActivityType.getSensorTypeArray(ActivityType.BIKE_POWER, null)` contains `POWER`, `POWER_TO_WEIGHT`, `NORMALIZED_POWER`, `INTENSITY_FACTOR`, `WORK_KJ`, `TSS`, `W_PRIME_BAL`, `PEDAL_POWER_BALANCE`, and `TORQUE`.
  2. *`TrackingViewsDbHelper` 4-Tab `BIKE_POWER` Default Layouts & Toggle Matrix Tests (`BikePowerCockpitDefaultsAndGatingTest.kt`, `TrackingViewsDatabaseManagerTest.kt`)*:
  • Verify `onCreate` seeds 3 tabs (`layoutNr` 1, 2, 3) for `GENERIC_HR`, `RUN_SPEED_AND_CADENCE`, and `BIKE_SPEED_AND_CADENCE`, and seeds 4 tabs (`layoutNr` 1 = Standard, `layoutNr` 2 = Power, `layoutNr` 3 = Map, `layoutNr` 4 = Laps) for `BIKE_POWER`.
  • Verify `createViewMap` for `BIKE_POWER` produces the 6-row Standard layout (`TIME_ACTIVE`/`TIME_OF_DAY`, `SPEED_mps`/`DISTANCE_m`, `HR`/`CADENCE`, `POWER` `HUGE`, `POWER_TO_WEIGHT`/`NORMALIZED_POWER`, `ALTITUDE`/`ASCENT`).
  • Verify `getDefaultPowerRowDataList()` produces the 6-row Power tab layout (`TIME_ACTIVE`/`HR`/`CADENCE`, `POWER` `HUGE`, `POWER_TO_WEIGHT`/`NORMALIZED_POWER` `LARGE`, `INTENSITY_FACTOR`/`TSS`/`WORK_KJ` `NORMAL`, `W_PRIME_BAL` `LARGE`, `PEDAL_POWER_BALANCE` `SMALL` spanning full width without `TORQUE`).
  • Verify `getDefaultMapRowDataList(ActivityType.BIKE_POWER)` and `getDefaultLapRowDataList(ActivityType.BIKE_POWER)` include `POWER` and `POWER_TO_WEIGHT`.
  3. *Live-Cockpit Gating, Empty-Row Zero-Height Collapse & Custom-Layout Coordinate Preservation Tests (`BikePowerCockpitDefaultsAndGatingTest.kt`)*:
  • Verify `isSensorTypeVisibleForTuning` and `filterVisibleFieldsForTuning` across `TuningConfig` states (`enableCogganPowerMetrics`, `enableCriticalPowerModel`) and `ScreenMode`s (`TRACKING`, `PREVIEW`, `CONFIGURATION`), including the Empty-Row scenario (verifying that when all fields in a row are gated off, that `rowNr` is completely removed from `visibleFields.groupBy { it.rowNr }` in the `Column` layout so row height collapses to `0`) and a custom user layout simulation verifying that `rowNr`/`colNr` coordinates of remaining fields are never mutated and `ScreenMode.CONFIGURATION` returns all fields unfiltered.
  4. *9-Language Localization Parity Audit (`BikePowerCockpitDefaultsAndGatingTest.kt`)*:
  • Parse all 9 `values*/strings.xml` files and verify `tab_power` exists and is non-blank across all 9 locales.
  5. *All-Gated Empty Grid & Null `TuningConfig` Initial-Boot Fallback Tests (`TST-UI-372.5`, `BikePowerCockpitDefaultsAndGatingTest.kt`)*:
  • Verify that when all fields in a tab are gated off (`visibleFields.isEmpty()`), `filterVisibleFieldsForTuning` and `fieldsByRow` evaluate to `emptyList()` and `emptyMap()` gracefully without throwing `IndexOutOfBoundsException` or `IllegalArgumentException`, and `SensorGridScreen.kt` maintains a safe minimum container height (`defaultMinSize(minHeight = 48.dp)`).
  • Verify that passing `tuningConfig = null` to `isSensorTypeVisibleForTuning` and `filterVisibleFieldsForTuning` safely defaults to `TuningConfig()` during initial app boot.
  6. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with zero regressions.

### TST-UI-373: **Timeout-Resilient Cockpit Tab Navigation Collector, Lifecycle-Safe Dialog Dispatch & Context.findActivity() Verification Suite** [Timeout-resilient cockpit tab navigation (`withTimeoutOrNull` + `first`), `repeatOnLifecycle(STARTED)` collection, `canShowDialogFragments()` guard, and `Context.findActivity()` unwrapping verified in `TrackingTabsNavigationTimeoutTest`.]
- **Requirement(s)**: `REQ-UI-410`, `REQ-UI-163`
- **Component**: **Timeout-Resilient Cockpit Tab Navigation Collector, Lifecycle-Safe Dialog Dispatch & `Context.findActivity()` Verification Suite**
- **Jira**: ``ATT-4000``
- **Test Description**:
  1. *Post-Timeout Navigation Collector Survival Unit Test (`TST-UI-373.1`, `TrackingTabsNavigationTimeoutTest.kt`)*:
  • Collect `SharedFlow<TabNavigationEvent>` using `navigateToPagerTarget` in `ScreenMode.TRACKING` with `pageCount = 1`, emit `TabNavigationEvent.NavigateTo(2)` (`target = 3`), advance virtual time past the 2000 ms timeout, then update `pageCount = 4` and emit `TabNavigationEvent.NavigateTo(0)` (`target = 1`).
  • Verify that the first event times out cleanly (`returned == false`, `lastKnownPage == 0`, collector coroutine remains active) and the second event navigates the pager to page `1` (`lastKnownPage == 1`, `scrolledPages == listOf(1)`).
  2. *Delayed Page-Count Readiness & Scroll Fallback Unit Test (`TST-UI-373.2`, `TrackingTabsNavigationTimeoutTest.kt`)*:
  • Verify `navigateToPagerTarget` suspends while `pageCount <= target`, resumes immediately when `pageCountFlow` emits `count > target` within the 2000 ms window, updates `lastKnownPage = target`, and falls back to `scrollToPage(target)` if `animateScrollToPage(target)` throws an exception (while propagating outer `CancellationException`).
  3. *`Context.findActivity()`, `canShowDialogFragments()` & Source Contract Test (`TST-UI-373.3`, `TrackingTabsNavigationTimeoutTest.kt`)*:
  • Verify `Context.findActivity()` unwraps nested `ContextWrapper` instances to return `AppCompatActivity` and returns `null` for non-Activity `Context`s.
  • Verify `canShowDialogFragments()` returns `false` when `activity == null`, `isFinishing`, `isDestroyed`, or `supportFragmentManager.isStateSaved == true`, and `true` when active and state is not saved.
  • Verify `TrackingTabsScreen.kt` uses `withTimeoutOrNull`, `repeatOnLifecycle(Lifecycle.State.STARTED)`, `findActivity()`, and `canShowDialogFragments()`, and contains zero `LocalContext.current as androidx.appcompat.app.AppCompatActivity` or `CancellationException("PageCountReady")` patterns.
  4. *9-Language Localization & Clean-Room Full Suite Regression (`TST-UI-373.4`, `TST-UI-373.5`)*:
  • Execute `TranslationParityTest` and `./gradlew testDebugUnitTest` asserting 100% test pass rate with zero regressions.

### TST-UI-374: **Cockpit Sensor Grid Configurable Section Dividers, Automated Row Shift & 9-Language Localization Verification Suite** [SectionDividersTable schema v14, CRUD operations, row shifting on row insertion, grid normalization integrity, repository flow emissions, glanceable horizontal divider rendering, and 9-language localization parity verified in TrackingViewsDatabaseManagerTest, TrackingViewsRepositoryTest, SensorGridLocalizationTest, and full clean-room suite.]
- **Requirement(s)**: `REQ-UI-411`
- **Component**: **Cockpit Sensor Grid Configurable Section Dividers, Automated Row Shift & 9-Language Localization Verification Suite**
- **Jira**: ``ATT-4055``
- **Test Description**:
  1. *SQLite Schema Creation & Upgrade Migration Test (`TST-UI-374.1`, `TrackingViewsDatabaseManagerTest.kt`)*:
  • Verify that on fresh database creation (`onCreate`) and upgrade from v13 to v14 (`onUpgrade`), `SectionDividersTable` exists with columns `_id`, `ViewId`, `RowNr`, `Title` and unique constraint on `(ViewId, RowNr)`.
  2. *Section Divider CRUD, Row Shifting on Insert & Grid Normalization Test (`TST-UI-374.2`, `TrackingViewsDatabaseManagerTest.kt`)*:
  • Insert section dividers at rows 2 and 4 for a test tab; verify `getSectionDividers` retrieves them accurately.
  • Insert a new row at row 2 (`colNr = -1`) via `insertSensorFieldAt`; verify that dividers previously at row 2 and 4 shift to rows 3 and 5.
  • Trigger `normalizeGrid`; verify divider row numbers remain synchronized with compacted row indices, and deleting a tab removes its dividers.
  3. *TrackingViewsRepository & TrackingViewModel Flow Emission Test (`TST-UI-374.3`, `TrackingViewsRepositoryTest.kt`)*:
  • Verify `getSectionDividersFlow` emits updated divider lists whenever `configUpdateTrigger` fires.
  • Verify `TrackingViewModel` maps database dividers into `TrackingScreenState.sectionDividers`.
  4. *SensorGridScreen Visual Divider Rendering Test (`TST-UI-374.4`, `SensorGridScreenLayoutTest.kt`)*:
  • In `ScreenMode.TRACKING`, verify an unlabeled divider renders as a clean horizontal line with minimal vertical overhead ($\le 20\text{ dp}$).
  • In `ScreenMode.TRACKING`, verify a labeled divider renders uppercase mini-title with flanking divider rules.
  5. *9-Language Localization Parity & Non-Blank String Test (`TST-UI-374.5`, `SensorGridLocalizationTest.kt`)*:
  • Verify all section divider control, dialog, and preset chip strings exist and are non-blank across EN, DE, ES, FR, IT, JA, NL, PL, PT.
  6. *Clean-Room Full Suite Regression Execution (`TST-UI-374.6`)*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with zero regressions.

### TST-UI-375: **Buffered One-Off Navigation Channels across STOPPED Lifecycle and Gesture Cancellation Safety Verification Suite** [Channel background buffering across STOPPED lifecycle, ControlTrackingViewModel buffering, exactly-once consumption, user swipe gesture cancellation resilience, non-cancellation fallback, and full regression pass verified in TrackingTabsNavigationTimeoutTest, ControlTrackingViewModelTest, and full clean-room suite.]
- **Requirement(s)**: `REQ-UI-412`, `REQ-UI-163`, `REQ-UI-410`
- **Component**: **Buffered One-Off Navigation Channels across STOPPED Lifecycle and Gesture Cancellation Safety Verification Suite**
- **Jira**: ``ATT-4097``
- **Test Description**:
  1. *Channel Background Buffering across `STOPPED` Lifecycle Test (`TST-UI-375.1`, `TrackingTabsNavigationTimeoutTest.kt`)*:
  • Emit navigation event (`TabNavigationEvent.NavigateTo(0)` or `TabNavigationEvent.EditDevice(42L)`) in `TrackingTabsViewModel` while no collector is active (simulating an Activity in `STOPPED` where `repeatOnLifecycle(STARTED)` cancels collection).
  • Start a collector simulating resumption to `Lifecycle.State.STARTED`.
  • Verify that the buffered event is received and processed immediately upon subscription.
  2. *ControlTrackingViewModel Background Buffering Test (`TST-UI-375.2`, `ControlTrackingViewModelTest.kt`)*:
  • Emit `ControlNavigation.ToEditDevice` and `ControlNavigation.ShowRecoveryDialog` in `ControlTrackingViewModel` with zero subscribers.
  • Start collecting `viewModel.navigationEvent`.
  • Verify each event is received in FIFO order without loss.
  3. *Exactly-Once Consumption & Re-Subscription Cleanliness Test (`TST-UI-375.3`, `TrackingTabsNavigationTimeoutTest.kt`)*:
  • After the first collector consumes the event, cancel collection (simulating screen stop/recreation).
  • Start a second collector.
  • Verify that zero replay events are received (unlike `SharedFlow(replay = 1)`).
  4. *User Gesture Cancellation Resilience Test (`TST-UI-375.4`, `TrackingTabsNavigationTimeoutTest.kt`)*:
  • In `navigateToPagerTarget`, simulate `animateScrollToPage(target)` throwing `kotlinx.coroutines.CancellationException` (e.g. user manual swipe interrupt).
  • Verify `navigateToPagerTarget` returns cleanly without throwing an unhandled exception.
  • Verify that fallback `scrollToPage(target)` is NOT invoked, preserving the user's manual swipe position.
  5. *Non-Cancellation Exception Fallback Scroll Test (`TST-UI-375.5`, `TrackingTabsNavigationTimeoutTest.kt`)*:
  • In `navigateToPagerTarget`, simulate `animateScrollToPage(target)` throwing a standard `IllegalStateException` or `RuntimeException`.
  • Verify that fallback `scrollToPage(target)` IS executed.
  6. *Clean-Room Full Suite Regression Execution (`TST-UI-375.6`)*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with zero regressions.

### TST-UI-376: **Cockpit Sensor Field Insertion Atomicity, Tab Isolation, Divider Synchronization, and Rollback Verification Suite** [Cockpit sensor field insertion atomicity, multi-tab isolation, divider shift synchronization, contiguous grid normalization with gapped healing, parameter and out-of-bounds boundary validation, and rollback safety verified in real SQLite integration and unit tests.]
- **Requirement(s)**: `REQ-UI-413`
- **Component**: **Cockpit Sensor Field Insertion Atomicity, Tab Isolation, Divider Synchronization, and Rollback Verification Suite**
- **Jira**: ``ATT-4010``
- **Test Description**:
  1. *Multi-Tab Real SQLite Isolation Test (`TST-UI-376.1`, `TrackingViewsRealSqliteMigrationTest.kt`)*:
  • Initialize real SQLite database with Tab 1 (`viewId = 1`) and Tab 2 (`viewId = 2`), each populated with rows in `ROWS_TABLE` and records in `SECTION_DIVIDERS_TABLE`.
  • Invoke `insertSensorFieldAt(tabViewId = 1, rowNr = 1, colNr = -1, ...)` (insert new row in Tab 1).
  • Verify rows and dividers in Tab 1 shift down by 1.
  • Verify all rows and dividers in Tab 2 retain exact original `ROW_NR` and `COL_NR` values.
  2. *Transaction Rollback Resilience Test (`TST-UI-376.2`, `TrackingViewsRealSqliteMigrationTest.kt`)*:
  • Simulate failure or constraint error mid-operation during `insertSensorFieldAt`.
  • Verify the entire transaction rolls back completely and zero rows or dividers remain shifted or orphaned.
  3. *Contiguous Coordinate Normalization & Gapped Grid Healing Test (`TST-UI-376.3`, `TrackingViewsDatabaseManagerTest.kt`, `TrackingViewsRealSqliteMigrationTest.kt`)*:
  • Inject a fragmented/gapped database state (e.g. rows 1, 3, 5) with section dividers.
  • Invoke `insertSensorFieldAt` for new column and new row.
  • Verify `normalizeGrid(db, tabViewId)` is invoked on the active transaction database connection, asserting rows compact contiguously to 1, 2, 3 and section dividers re-map accurately.
  4. *Parameter, Boundary & Out-of-Bounds Row Validation Test (`TST-UI-376.4`, `TrackingViewsDatabaseManagerTest.kt`)*:
  • Invoke `insertSensorFieldAt` with non-positive values (`tabViewId <= 0` or `rowNr < 1`); verify method returns immediately without initiating database transactions or mutations.
  • Invoke `insertSensorFieldAt` with out-of-bounds row (`rowNr = 999`); verify field is inserted and compacted cleanly to `currentMaxRow + 1` by `normalizeGrid`.
  5. *9-Language Localization & Specifier Audit (`TST-UI-376.5`)*:
  • Audit `res/values*/strings.xml` confirming zero string modifications.
  6. *Clean-Room Regression Suite Execution (`TST-UI-376.6`)*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with zero regressions.

### TST-UI-377: **Workout Cards & Details Settings Streamlining, Collapsible Section Order, Authoritative Tab Activation, and 9-Language Parity Verification Suite** [Authoritative tab data availability activation, single-column checkbox table, collapsible summary reorder list by default, domain-scoped zone display mode selectors, < 400 lines modularity, 9-language parity, and full clean-room suite verified.]
- **Requirement(s)**: `REQ-UI-414`
- **Component**: **Workout Cards & Details Settings Streamlining, Collapsible Section Order, Authoritative Tab Activation, and 9-Language Parity Verification Suite**
- **Jira**: ``ATT-4043``
- **Test Description**:
  1. *Authoritative Tab Data Availability & Section Activation Test (`TST-UI-377.1`, `WorkoutDetailTabsTest.kt`)*:
  • Initialize `WorkoutDetailSectionAvailability` with `hasMap = true` and `hasElevation = true`, alongside `WorkoutDetailPreferences` with `showMap = false` and `showElevationProfile = false`.
  • Evaluate `availability.isSectionActive(WorkoutDetailSectionType.MAP, detailPrefs)` and `availability.isSectionActive(WorkoutDetailSectionType.ELEVATION, detailPrefs)`.
  • Assert both return `true`, confirming section activation is governed by data availability and tab configuration, rather than legacy boolean flags.
  2. *Single-Column Checkbox & Layout Structure Contract Test (`TST-UI-377.2`, `WorkoutMasksAndCardsLayoutTest.kt`)*:
  • Verify `WorkoutMasksAndCardsSection.kt` table header references `tuning_matrix_col_list` (50dp width) and does NOT contain `tuning_matrix_col_details`.
  • Verify feature rows contain a single checkbox for `feature.listChecked` and no second checkbox for details.
  3. *Collapsible Summary Reorder List Contract Test (`TST-UI-377.3`, `WorkoutMasksAndCardsLayoutTest.kt`)*:
  • Verify collapsible toggle state initialized to `false` (collapsed by default).
  • Verify references to `Icons.Default.KeyboardArrowDown` and `Icons.Default.KeyboardArrowUp` for expand/collapse visual feedback.
  • Verify reference to `R.string.workout_card_reorder_toggle` format string.
  4. *Scoped Zone Display Mode & Detail Tabs Action Contract Test (`TST-UI-377.4`, `WorkoutMasksAndCardsLayoutTest.kt`)*:
  • Verify `ZoneDisplayModeSelector` for Summary is placed in the Trainingsliste block (`workoutCardPrefs.zoneCardDisplayMode`).
  • Verify `ZoneDisplayModeSelector` for Details is placed in the Trainings-Details block (`workoutDetailPrefs.zoneCardDisplayMode`) alongside `WorkoutDetailTabsConfigSheet` launch button.
  5. *Modularity & 400-Line Limit Test (`TST-UI-377.5`, `WorkoutMasksAndCardsLayoutTest.kt`)*:
  • Count total lines of `WorkoutMasksAndCardsSection.kt`.
  • Assert line count is strictly < 400 lines.
  6. *9-Language Localization Parity Audit (`TST-UI-377.6`, `TranslationParityTest.kt`)*:
  • Inspect all 9 supported locale directories (`values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`).
  • Verify `workout_card_reorder_toggle` and `settings_workout_details_title` exist in all 9 files with `%1$d` specifier match and no entity issues.
  7. *Clean-Room Full Suite Regression Execution (`TST-UI-377.7`)*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with zero regressions.

### TST-UI-378: **WorkoutDetails Single-Line Power Row Layout, Domain Row Dividers, Priority Resolution, and 9-Language Parity Verification Suite** [Single-line power row priority logic without physics fallback, domain row horizontal dividers, Section 5.10 compliance, < 500 lines modularity, 9-language parity, and full test suite verified.]
- **Requirement(s)**: `REQ-UI-415`
- **Component**: **WorkoutDetails Single-Line Power Row Layout, Domain Row Dividers, Priority Resolution, and 9-Language Parity Verification Suite**
- **Jira**: ``ATT-4042``
- **Test Description**:
  1. *Power Training Metrics Single-Line Presentation Unit Test (`TST-UI-378.1`, `WorkoutDetailsLayoutTest.kt`)*:
  • Test full Coggan scenario with `NP`, `IF`, `TSS`: verify resolution returns strictly 3 items: `[NORMALIZED_POWER, INTENSITY_FACTOR, TSS]`.
  • Test missing FTP scenario with `NP` (null `IF`/`TSS`): verify resolution returns strictly 1 item: `[NORMALIZED_POWER]` without basic physics fallback.
  • Test Coggan disabled scenario (`enableCogganPowerMetrics = false` or `NP = null`): verify resolution returns strictly empty list.
  • Test all-null scenario: verify resolution returns empty list.
  2. *WorkoutDetails Layout & Divider Source Contract Test (`TST-UI-378.2`, `WorkoutDetailsLayoutTest.kt`)*:
  • Parse `WorkoutDetails.kt` and assert `PowerTrainingMetricsSection` contains exactly one `Row(modifier = Modifier.fillMaxWidth())` call within `WorkoutDomainRow`.
  • Assert `WorkoutDetails` contains `HorizontalDivider` calls conditionally placed before each optional domain row (`AltitudeRow`, `PowerTrainingMetricsSection`, `AerobicEfficiencyRow`, `AnaerobicBatteryRow`).
  • Assert outer column uses `Arrangement.spacedBy(12.dp)`.
  • Assert `WorkoutDetails.kt` file length is strictly < 500 lines.
  3. *Section 5.10 & Physiology Visibility Contract Test (`TST-UI-378.3`, `PhysiologyModelsVisibilityContractTest.kt`)*:
  • Verify `WorkoutDetails.kt` contains zero `FlowRow` and zero `surfaceContainerHighest` calls.
  • Verify `WorkoutDomainRow`, `ic_power`, `ic_cardio_load`, and `ic_battery_full` remain properly referenced.
  4. *9-Language Localization Parity Audit (`TST-UI-378.4`, `AthleteProfileContractAndLocalizationTest.kt`)*:
  • Verify all 9 locales define power metric string resources and value format specifiers with 100% parity.
  5. *Clean-Room Full Suite Regression Execution (`TST-UI-378.5`)*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% pass rate with zero regressions.

### TST-UI-379: **W' Balance Battery Gauge, Mathematical Normalization, Layout Invariants, and 9-Language Parity Verification Suite** [Mathematical normalization, warning thresholds, bounds clamping, stale transitions, mid-session mutation, device reset fallback to FTP, fixed 14.dp layout stability, anti-clipping padding, 9-language parity, and full clean-room pass verified.]
- **Requirement(s)**: `REQ-UI-416`
- **Component**: **W' Balance Battery Gauge, Mathematical Normalization, Layout Invariants, and 9-Language Parity Verification Suite**
- **Jira**: ``ATT-1296``
- **Test Description**:
  1. *Mathematical Normalization, Clamping & Thresholds Test (`TST-UI-379.1`, `WPrimeBatteryMathTest.kt`)*:
  • Verify percentage and fraction scaling across Normal, Low, Critical, and Depleted levels.
  • Verify boundary clamping to $[0.0f, 1.0f]$ and $[0, 100]$.
  • Verify stale transitions (>3000ms) and mid-session baseline mutation resilience with clamping to $[0.0, W'_{\text{new}}]$.
  2. *Device Hardening & Fallback Test (`TST-UI-379.2`, `CyclingPowerMetricsDeviceTest.kt`)*:
  • Verify `resetSession()` restores full $W'$ capacity when only $FTP$ is configured using `resolveEffectiveCpWatts()`.
  • Verify continuous 1-Hz depletion at $P > CP$ and exponential reconstitution at $P = 0\text{ W}$ during pause.
  • Verify null safety and fallback to `SettingsDataStore.DEFAULT_CYCLING_FTP` (`200` W).
  3. *Battery Gauge Layout & Stability Contract Test (`TST-UI-379.3`, `WPrimeBatteryGaugeBarLayoutTest.kt`)*:
  • Verify fixed 14.dp height slot across active, stale, and empty states.
  • Verify 3.dp end-cap boundary padding prevents clipping at 0% and 100% capacity.
  • Verify layout stability under system font scaling (1.0x, 1.5x, 2.0x) with `TextOverflow.Clip`.
  • Verify continuous animation updates with `tween(durationMillis = 300, easing = FastOutSlowInEasing)`.
  • Verify warning border color activation when `isWarning == true`.
  • Verify progressive disclosure gating collapses row to 0 when `enableCriticalPowerModel == false`.
  4. *9-Language Localization & Format Audit (`TST-UI-379.4`, `WPrimeBatteryLocalizationTest.kt`)*:
  • Verify 100% parity across all 9 application locales for `w_prime_battery_pct` (`%1$d%%`) and `w_prime_warning_low_battery` (`W\' Battery Low (<20%)`).
  5. *Clean-Room Regression Suite Execution (`TST-UI-379.5`)*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% pass rate.

### TST-UI-380: **Distance Interval Filtering (Min/Max Range and Preset Intervals) Verification Suite across Routes and Favorite Routes** [Distance interval filtering accurately filters routes and favorite tracks across preset chips and custom min/max decimal bounds; active filter count reflects 1 dimension for distance; JSON persistence is backward compatible; chips format cleanly; 9-language parity and full clean-room pass verified.]
- **Requirement(s)**: `REQ-UI-417`
- **Component**: **Distance Interval Filtering (Min/Max Range and Preset Intervals) Verification Suite across Routes and Favorite Routes**
- **Jira**: ``ATT-4291``
- **Test Description**:
  1. *RouteFilterCriteria Distance Interval & Count Test (`TST-UI-380.1`, `RouteFilterCriteriaTest.kt`)*:
  • Test bounded interval `[25 km, 50 km]` (`min = 25000.0, max = 50000.0`): assert 25 km, 45 km, 50 km match; 5 km, 10 km, 80 km fail.
  • Test upper bound only `< 10 km` (`min = null, max = 10000.0`): assert 5 km, 10 km match; 25 km, 45 km, 50 km, 80 km fail.
  • Test lower bound only `> 50 km` (`min = 50000.0, max = null`): assert 50 km, 80 km match; 5 km, 10 km, 25 km, 45 km fail.
  • Test `activeFilterCount`: assert `minOnly` gives 1, `maxOnly` gives 1, and `bothBounds` gives 1 (strictly 1 dimension for distance).
  2. *RouteFilterCriteria JSON Serialization Roundtrip & Backward Compatibility Test (`TST-UI-380.2`, `RouteFilterCriteriaTest.kt`)*:
  • Call `toJson()` on a two-bound instance, deserialize with `RouteFilterCriteria.fromJson()`, and assert properties match.
  • Deserialize legacy JSON lacking `maxDistanceMeters`: assert `maxDistanceMeters = null` and `minDistanceMeters` is preserved without errors.
  3. *ClusterFilterCriteria Distance Interval & Count Test (`TST-UI-380.3`, `ClusterFilterCriteriaTest.kt`)*:
  • Test bounded interval `[10 km, 35 km]` (`min = 10000.0, max = 35000.0`) on cluster `refDistance`: assert 12.5 km, 25 km, 35 km match; 8 km, 50 km, 75 km fail.
  • Test upper bound only `< 10 km`: assert 8 km matches; 12.5 km, 25 km fail.
  • Test lower bound only `> 50 km`: assert 50 km, 75 km match; 35 km fails.
  • Test `activeFilterCount`: assert setting min, max, or both adds strictly 1 to count.
  4. *ClusterFilterCriteria JSON Serialization Roundtrip & Backward Compatibility Test (`TST-UI-380.4`, `ClusterFilterCriteriaTest.kt`)*:
  • Execute JSON roundtrip with `maxDistanceMeters` and verify legacy JSON without `maxDistanceMeters` parses safely with null.
  5. *Active Filter Chips Row Distance Formatting & Dismissal Contract Test (`TST-UI-380.5`, `RouteFilterCriteriaTest.kt` / `ClusterFilterCriteriaTest.kt`)*:
  • Verify interval formatting produces `"$min - $max km"` (e.g. `"25 - 50 km"`), `"> $min km"` (e.g. `"> 25 km"`), and `"< $max km"` (e.g. `"< 50 km"`).
  • Verify decimal trimming: `12500.0` formats as `"12.5"`, whereas `25000.0` formats as `"25"`.
  • Verify dismissal contract: `criteria.copy(minDistanceMeters = null, maxDistanceMeters = null)` clears both distance bounds.
  6. *9-Language Localization & Specifier Audit (`TST-UI-380.6`, `TranslationParityTest.kt`)*:
  • Verify string resources (`filter_section_distance`, `filter_min_label`, `filter_max_label`, `filter_distance_interval_format`, `filter_min_distance_format`, `filter_max_distance_format`, `sort_elevation_gain`) exist across all 9 application locales (`values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`) with 100% format specifier parity.
  7. *Clean-Room Regression Suite Execution (`TST-UI-380.7`)*:
  • Run `./gradlew testDebugUnitTest` verifying 100% pass rate.

### TST-UI-381: **Double Percent Symbol Removal & 9-Locale Percent Integrity Verification Suite** [Single percent symbol (%) parity verified across all 5 non-formatted keys in all 9 locales; w_prime_warning_low_battery confirmed free of %%; repository-wide non-formatted strings confirmed free of %%; clean-room test suite passes with zero regressions.]
- **Requirement(s)**: `REQ-UI-418`
- **Component**: **Double Percent Symbol Removal & 9-Locale Percent Integrity Verification Suite**
- **Jira**: ``ATT-4292``
- **Test Description**:
  1. *W' Battery Warning Low Battery Double Percent Removal Test (`TST-UI-381.1`, `WPrimeBatteryLocalizationTest.kt`)*:
  • For each of the 9 locales (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`), parse `w_prime_warning_low_battery`.
  • Assert `warning.contains("%")` is true.
  • Assert `warning.contains("%%")` is false.
  2. *Repository-Wide Non-Formatted String Percent Integrity Contract Test (`TST-UI-381.2`, `LocalizationPercentIntegrityTest.kt`)*:
  • Scan all XML string resources across all 9 locales using `LocalizationTestCache`.
  • For every `<string>` entry containing `%%`, verify that it contains a valid format specifier token (`%s`, `%d`, `%[0-9]+\$`, `%f`, etc.).
  • Assert that zero non-formatted strings contain `%%`.
  3. *5-Key 9-Locale Single Percent Parity Contract Test (`TST-UI-381.3`, `LocalizationPercentIntegrityTest.kt`)*:
  • For each of the 5 keys (`workout_decoupling_coupled`, `workout_decoupling_drift`, `workout_w_prime_min_pct_label`, `workout_w_prime_empty_tank`, `w_prime_warning_low_battery`) across all 9 locales:
  • Assert that the string exists, is not blank, contains `%`, and does not contain `%%`.
  • Verify exact expected text across all 45 localized entries.
  4. *Clean-Room Regression Suite Execution (`TST-UI-381.4`)*:
  • Execute `./gradlew testDebugUnitTest` verifying 100% pass rate.

### TST-UI-382: **Power Histogram Rare Sprint Spike Folding, Zone Boundary Snapping, and Consistent Symmetrical Header Range Toggle Verification Suite** [Statistical tail cutoff calculation with Zone 4–6 upper boundary snapping and 50 W fallback, bin width alignment, eligibility gating, overflow bin aggregation, return bin aggregation, symmetrical 2-segment header range toggle layout contracts, independent Y-axis scaling, and 9-language localization parity specified and verified across test suite.]
- **Requirement(s)**: `REQ-UI-419`, `REQ-UI-372`, `REQ-UI-388`, `REQ-UI-420`
- **Component**: **Power Histogram Rare Sprint Spike Folding, Zone Boundary Snapping, and Consistent Symmetrical Header Range Toggle Verification Suite**
- **Jira**: ``ATT-4370``
- **Test Description**:
  1. *Zone Boundary Snapping, Upper Boundary Alignment & Fallback Test (`TST-UI-382.1`, `TelemetryHistogramCalculatorTest.kt`)*:
  • Test candidate $\le z4Max$: verify `tailCutoff == thresholds.z4Max`.
  • Test candidate between $z4Max$ and $z5Max$: verify `tailCutoff == thresholds.z5Max`.
  • Test candidate between $z5Max$ and $z6Max$: verify `tailCutoff == thresholds.z6Max`.
  • Test candidate $> z6Max$: verify fallback to 50 W multiple rounding.
  • Test bin width alignment: verify `tailCutoff % binWidth == 0`.
  2. *Tail Folding Gating & Symmetric Dataset Non-Folding Test (`TST-UI-382.2`, `TelemetryHistogramCalculatorTest.kt`)*:
  • Test dataset where `dataMax - tailCutoff < 100 W`: verify `tailCutoff == null` and `sprintTail == null`.
  • Test dataset where `dataMax < tailCutoff * 1.25`: verify `tailCutoff == null` and `sprintTail == null`.
  • Test Heart Rate histogram calculation: verify `tailCutoff` is permanently null.
  3. *Main View Overflow Bin & Sprint Tail Return Bin Aggregation Test (`TST-UI-382.3`, `TelemetryHistogramCalculatorTest.kt`)*:
  • Verify Main View contains regular bins up to `tailCutoff` plus 1 overflow bin with `isOverflow = true`, `rangeMin = tailCutoff`, `rangeMax = dataMax`, and duration matching sum of samples $> \text{tailCutoff}$.
  • Verify Sprint Tail dataset contains return bin at index 0 with `isReturn = true`, `rangeMin = dataMin`, `rangeMax = tailCutoff`, and duration matching sum of samples $\le \text{tailCutoff}$.
  • Verify mathematical conservation of total duration and percentages sum to 100% in both Main and Sprint Tail datasets.
  4. *Model Backward Compatibility & Default Arguments Contract Test (`TST-UI-382.4`, `TelemetryHistogramCardTest.kt`)*:
  • Verify existing constructors of `TelemetryHistogramBin` and `TelemetryHistogramData` without new parameters compile and default `isOverflow = false`, `isReturn = false`, `tailCutoff = null`, `sprintTail = null`.
  5. *Symmetrical 2-Segment Header Range Toggle Layout & Affordance Contract Test (`TST-UI-382.5`, `TelemetryHistogramCardTest.kt`)*:
  • Verify independent Y-scaling math in Sprint Tail: `maxDuration` excludes return bin and scales to maximum sprint bin.
  • Verify Main View header layout contract: Segment 1 (`0–X W`) highlighted with `primaryContainer` / `onPrimaryContainer` (non-clickable); Segment 2 (`> X W`) clickable with `surfaceVariant` / `onSurfaceVariant` and forward arrow.
  • Verify Sprint Tail View header layout contract: Segment 1 (`0–X W`) clickable with `surfaceVariant` / `onSurfaceVariant` and back arrow; Segment 2 (`X–Y W`) highlighted with `primaryContainer` / `onPrimaryContainer` (non-clickable).
  • Verify resting readout string formats across both views and selected bin inspection across regular bins.
  6. *9-Language Localization & Format Specifier Parity Test (`TST-UI-382.6`, `TelemetryHistogramLocalizationTest.kt`)*:
  • Verify presence and non-blank values for `power_histogram_overflow_label`, `power_histogram_return_label`, `power_histogram_overflow_desc`, `power_histogram_return_desc` across all 9 locales with exact format token parity (`%1$d`, `%2$s`).
  7. *Clean-Room Full Suite Regression Execution (`TST-UI-382.7`)*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with zero regressions.

### TST-UI-383: **7 Cycling Power Zones in Power Histogram, TelemetryMetricGraph Threshold Lines, and Dark Mode Chart Rendering Verification Suite** [Power histogram correctly maps Zone 6 and Zone 7 wattage bins without clamping to Zone 5; overflow bar reflects peak sprint tail zone; TelemetryMetricGraph displays all 6 power threshold lines; dark mode outlines and text colors ensure full contrast; heart rate remains 5 zones; full clean-room test suite passes with zero regressions.]
- **Requirement(s)**: `REQ-UI-420`, `REQ-UI-419`, `REQ-UI-372`, `REQ-UI-392`
- **Component**: **7 Cycling Power Zones in Power Histogram, TelemetryMetricGraph Threshold Lines, and Dark Mode Chart Rendering Verification Suite**
- **Jira**: ``ATT-4294``
- **Test Description**:
  1. *7-Zone Power Classification & Histogram Binning Unit Test (`TST-UI-383.1`, `TelemetryHistogramCalculatorTest.kt`)*:
  • Verify samples with wattage in Zone 6 (`z5Max < pwr <= z6Max`) produce bins with `zoneIndex = 6` and `color = TTColor.Zone6`.
  • Verify samples with wattage in Zone 7 (`pwr > z6Max`) produce bins with `zoneIndex = 7` and `color = TTColor.Zone7`.
  • Verify `overflowBin` in `calculatePowerHistogram` adopts the peak zone index (6 or 7) and color from `tailBins`.
  • Verify `calculateHeartRateHistogram` continues to classify HR samples strictly into zones 1..5 (`zoneIndex in 1..5`, `color in Zone1..Zone5`).
  • Verify mathematical conservation of total active duration and percentage sum ($100.0\% \pm 0.1\%$).
  2. *6-Threshold Power Dashed Lines Unit Test (`TST-UI-383.2`, `TelemetryZoneMathTest.kt`)*:
  • Verify `calculatePowerThresholdDashes` produces all 6 dashed lines (`z1Max` through `z6Max`) when data range spans below `z1Max` and above `z6Max`.
  • Verify threshold values outside `[dataMin, dataMax]` are filtered out.
  • Verify heart rate 4-threshold dashes (`calculateThresholdDashes`) remain strictly 4 lines.
  3. *Dark Mode Contrast, Readability & Model KDoc Parity Contract Test (`TST-UI-383.3`, `TelemetryHistogramCardTest.kt`)*:
  • Contrast verification: verify `TTColor.Zone7` (`#000000`) paired with `outlineVariant` border guarantees $\ge 3.0:1$ non-text contrast against dark theme surface (`#121212` / `#1E1E1E`).
  • Header readout color resolution: verify Zone 7 selected bin text color resolves to `onSurface` in dark mode.
  • Verify KDoc documentation parity in `ZoneDistributionModels.kt` documenting 1..7 zones for power.
  4. *9-Language Localization & String Audit (`TST-UI-383.4`, `TranslationParityTest.kt`)*:
  • Verify zero new string resources required across all 9 canonical app locales (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`).
  • Verify existing string resources maintain 100% translation parity and format specifier compatibility.
  5. *Clean-Room Regression Suite Execution (`TST-UI-383.5`)*:
  • Execute `./gradlew testDebugUnitTest` verifying 100% pass rate with zero regressions across the full test suite.

### TST-UI-384: **Electronic Bike Bell with Button, Double-Tap Gesture, and Progressive Loudness Escalation Verification Suite** [AudioTrack configured with USAGE_MEDIA and static replay stop() cycle; BikeBellButton enabled across READY, TRACKING, and PAUSED modes with gesture isolation; double-tap active across non-configuration modes; 100% test pass rate with zero regressions.]
- **Requirement(s)**: `REQ-UI-421`, `REQ-UI-344`, `REQ-UI-295`, `REQ-UI-409`
- **Component**: **Electronic Bike Bell with Button, Double-Tap Gesture, and Progressive Loudness Escalation Verification Suite**
- **Jira**: ``ATT-4371``
- **Test Description**:
  1. *TrackingViewsDbHelper SQLite v15 Transactional & Idempotent Migration Unit Test (`TST-UI-384.1`, `TrackingViewsRealSqliteMigrationTest.kt`)*:
  • Test database upgrade from v14 to v15: verify `SHOW_BIKE_BELL` column added.
  • Test existing cycling tabs (`BIKE_SPEED_AND_CADENCE`, `BIKE_POWER`) have `SHOW_BIKE_BELL = 1`.
  • Test non-cycling tabs retain `SHOW_BIKE_BELL = 0`.
  • Test idempotency: re-running migration does not throw duplicate column exception.
  • Test `DatabaseSchemaRegistry.kt` asserts version 15 for `"FlexibleGenericView.db"` and `"TrackingViews.db"`.
  2. *TrackingViewsRepository & DTO Mapping Symmetry Unit Test (`TST-UI-384.2`, `TrackingViewsRepositoryTest.kt`)*:
  • Test `TrackingViewInfo` reads and maps `showBikeBell` correctly from single-row and batch queries.
  • Test `updateShowBikeBell(tabViewId, show)` persists toggle state accurately.
  3. *BikeBellAudioSynthesizer USAGE_MEDIA Stream, Static Replay & Concurrency Test (`TST-UI-384.3`, `BikeBellAudioSynthesizerTest.kt`)*:
  • Verify 16-bit PCM mono buffer synthesis generated at $44.1\text{ kHz}$ with two distinct strikes separated by $\sim 110\text{ ms}$ and exponential decay.
  • Verify `AudioAttributes.USAGE_MEDIA` and `AudioAttributes.CONTENT_TYPE_SONIFICATION` audio attributes configuration.
  • Verify static replay contract: `mockTrack.stop()` is invoked prior to `mockTrack.playbackHeadPosition = 0` on every ring and roll start, preventing `ERROR_INVALID_OPERATION`.
  • Verify thread safety: concurrent calls to `ring()`, `startRoll()`, `stopRoll()`, and `close()` from multiple threads execute without exceptions or native crashes.
  4. *Progressive Loudness Escalation Logic Test (`TST-UI-384.4`, `TrackingTabsViewModelTest.kt`)*:
  • Test initial ring: verify volume is 0.70f.
  • Test repeat ring within 5 seconds ($t + 2\text{s}$): verify volume escalates to 1.0f.
  • Test silence $> 5\text{ seconds}$ ($t + 6\text{s}$): verify volume resets to 0.70f.
  • Test `onCleared()` releases synthesizer resources cleanly.
  5. *Cockpit Multi-Mode Availability & Pointer Gesture Isolation Contract Test (`TST-UI-384.5`, `TrackingTabsScreenTest.kt` / `BikeBellButtonTest.kt`)*:
  • Verify multi-mode availability: `BikeBellButton` is enabled in `TrackingMode.READY`, `TrackingMode.TRACKING`, and `TrackingMode.PAUSED` (enforcing Rule 53).
  • Verify `TrackingMode.IDLE`: `BikeBellButton` is disabled.
  • Verify gesture isolation: tap triggers, haptic pulse, and shake animation are handled directly by `ElevatedButton(onClick)` without nested or attached `Modifier.pointerInput(detectTapGestures)`.
  • Verify both buttons visible: `LapButton` and `BikeBellButton` render side-by-side in horizontal row.
  • Verify single button: `BikeBellButton` renders centered when `showBikeBell = true` and `showLapButton = false`.
  • Verify `ScreenMode.CONFIGURATION`: both `LapButton` and `BikeBellButton` are suppressed (preserving `REQ-UI-295`).
  • Verify `SnackbarHost` bottom padding adjusts dynamically (64.dp when button visible, 8.dp when hidden).
  6. *SensorGridScreen Multi-Mode Double-Tap Disambiguation & Pager Non-Interference Contract Test (`TST-UI-384.6`, `SensorGridScreenTest.kt`)*:
  • Verify double-tap on sensor grid dispatches bell ring when `screenMode != ScreenMode.CONFIGURATION && state.showBikeBell` (active in both `TRACKING` and `PREVIEW` modes).
  • Verify double-tap is disabled when `screenMode == ScreenMode.CONFIGURATION` or `showBikeBell == false`.
  • Verify horizontal swipe gestures are not consumed and propagate freely to `HorizontalPager`.
  • Verify single tap propagates to `BatterySaverController` for screen wakefulness.
  7. *9-Language Localization & String Specifier Parity Test (`TST-UI-384.7`, `TranslationParityTest.kt`)*:
  • Verify presence of `config_tracking__showBikeBell` and `config_tracking__showBikeBell_subtitle` across all 9 canonical locales (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`).
  • Verify zero missing keys and literal `\n` linebreaks.
  8. *Clean-Room Regression Suite Execution (`TST-UI-384.8`)*:
  • Run `./gradlew testDebugUnitTest` verifying 100% pass rate with zero regressions.

### TST-UI-385: **Relocate 'Sport Types' Drawer Item to Settings and Rename 'Equipment' Item to 'Bikes & Shoes' Verification Suite** [Relocate Sport Types drawer destination to Settings Hub, restrict Equipment hub strictly to physical hardware (My Sensors, Bikes & Shoes), rename Equipment title to Bikes & Shoes across all 9 locales with XML entity escaping, verify drawer state machine auto-expansion, and pass clean-room test suite.]
- **Requirement(s)**: `REQ-UI-422`, `REQ-UI-405`, `REQ-LOC-001`
- **Component**: **Relocate 'Sport Types' Drawer Item to Settings and Rename 'Equipment' Item to 'Bikes & Shoes' Verification Suite**
- **Jira**: ``ATT-4289``
- **Test Description**:
  1. *Drawer Structure & Group Item Count Integrity Unit Test (`TST-UI-385.1`, `NavigationDrawerStateTest.kt`)*:
  • Verify `myStuffGroup.items.map { it.id }` equals `listOf(R.id.drawer_my_sensors, R.id.drawer_equipment)` (strictly 2 items).
  • Verify `settingsGroup.items.map { it.id }` equals `listOf(R.id.drawer_units, R.id.drawer_display_settings, R.id.drawer_tracking_layouts, R.id.drawer_sport_types, R.id.drawer_backup_restore, R.id.drawer_privacy_policy, R.id.drawer_advanced_tuning)` (strictly 7 items with `drawer_sport_types` at index 3).
  • Verify total unique items count across all 5 groups remains strictly 22.
  • Verify default visible items count across non-collapsible groups (`Training` + `Maps`) remains strictly 9.
  2. *Drawer Auto-Expansion & Alias Selection State Unit Test (`TST-UI-385.2`, `NavigationDrawerStateTest.kt`)*:
  • Verify selecting `R.id.drawer_sport_types` auto-expands `settingsGroup` (`isDrawerGroupInitiallyExpanded == true`).
  • Verify selecting `R.id.drawer_sport_types` leaves `myStuffGroup` collapsed (`isDrawerGroupInitiallyExpanded == false`).
  • Verify selecting items in `myStuffGroup` (`drawer_my_sensors`, `drawer_equipment`, `drawer_bikes`, `drawer_shoes`) auto-expands `myStuffGroup`.
  • Verify `isDrawerItemSelected(R.id.drawer_equipment, ...)` alias matching for `drawer_equipment`, `drawer_bikes`, and `drawer_shoes`.
  3. *9-Language Localization & XML Entity Escaping Audit Test (`TST-UI-385.3`, `TranslationParityTest.kt`)*:
  • Verify `R.string.equipment_management_title` is defined across all 9 canonical locales (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`) with exact expected translations and proper XML entity escaping (`&amp;`).
  • Verify zero missing keys and 100% translation parity.
  4. *Screen Header Parity Contract Test (`TST-UI-385.4`, `EquipmentTabsScreenContractTest.kt`)*:
  • Verify `EquipmentTabsScreen.kt` references `R.string.equipment_management_title` for its top header, guaranteeing 1:1 parity with the drawer destination label.
  5. *Clean-Room Regression Suite Execution (`TST-UI-385.5`)*:
  • Execute `./gradlew testDebugUnitTest` verifying 100% pass rate with zero regressions across the full test suite.

### TST-UI-386: **Cockpit Section Divider Prominence, Centered Title Layout, and Unambiguous Split Icon Verification Suite** [Unlabeled divider thickness (2.5.dp) and outline contrast, titled divider horizontal centering with symmetric weight(1f) flanking rules, configuration mode HorizontalSplit icon and HorizontalRule exclusion, 9-language localization audit, and full clean-room pass specified.]
- **Requirement(s)**: `REQ-UI-411`
- **Component**: **Cockpit Section Divider Prominence, Centered Title Layout, and Unambiguous Split Icon Verification Suite**
- **Jira**: ``ATT-4286``
- **Test Description**:
  1. *Unlabeled Cockpit Section Divider Prominence & Contrast Test (`TST-UI-386.1`, `SensorGridScreenLayoutTest.kt`)*:
  • Verify `CockpitSectionDivider` for unlabeled divider (`title.isNullOrEmpty()`) specifies `thickness = 2.5.dp` (thickness $\ge 2.0\text{ dp}$).
  • Verify stroke color uses `MaterialTheme.colorScheme.outline` (providing prominent, glanceable contrast against dark and light surface containers).
  • Verify vertical padding remains compact (`padding(vertical = 4.dp)`), guaranteeing total height overhead $\le 10\text{ dp}$.
  2. *Labeled Cockpit Section Divider Centered Title & Symmetric Weights Test (`TST-UI-386.2`, `SensorGridScreenLayoutTest.kt`)*:
  • Verify `CockpitSectionDivider` for titled divider specifies symmetric `Modifier.weight(1f)` on both the leading and trailing `HorizontalDivider` instances.
  • Verify absence of asymmetric flanking weights (`weight(0.12f)` and `weight(0.88f)`).
  • Verify flanking divider lines specify `thickness = 2.dp` with `MaterialTheme.colorScheme.outline`.
  • Verify title retains `maxLines = 1`, `overflow = TextOverflow.Ellipsis`, and row height remains constrained (`heightIn(max = 20.dp)`).
  3. *Configuration Mode Divider Insertion Icon & Minus Affordance Removal Test (`TST-UI-386.3`, `SensorGridScreenLayoutTest.kt`)*:
  • In `SensorGridScreen.kt`, verify inter-row divider action evaluates to `Icons.Default.HorizontalSplit` when `existingDivider == null`.
  • Verify `Icons.Default.HorizontalRule` is strictly absent from divider insertion controls and `SensorGridScreen.kt`.
  • Verify existing divider retains `Icons.Default.Title` with `primary` tint when `existingDivider != null`.
  • Verify content description references `R.string.cockpit_section_divider_add` when `existingDivider == null` and `R.string.cockpit_section_divider_edit` when `existingDivider != null`.
  4. *9-Language Localization Parity & Non-Blank Audit (`TST-UI-386.4`, `SensorGridLocalizationTest.kt`)*:
  • Verify all section divider accessibility descriptions, dialog labels, and preset chips exist and are non-blank across EN, DE, ES, FR, IT, JA, NL, PL, PT.
  • Confirm zero new strings are required and zero format specifiers are mutated.
  5. *Clean-Room Regression Suite Execution (`TST-UI-386.5`)*:
  • Execute `./gradlew testDebugUnitTest` verifying 100% pass rate with zero regressions across the full test suite.

### TST-UI-387: **RouteTabbedScreen Modal Overlay Composition Retention & Tab Scroll Stability Verification Suite** [RouteTabbedScreen composition retention during route editing, modal overlay composition alongside RouteTabbedScreen, BackHandler dismissal contract, 9-language localization audit, and full clean-room pass specified.]
- **Requirement(s)**: `REQ-UI-423`, `REQ-UI-191`, `REQ-UI-159`
- **Component**: **RouteTabbedScreen Modal Overlay Composition Retention & Tab Scroll Stability Verification Suite**
- **Jira**: ``ATT-4285``
- **Test Description**:
  1. *Composition Retention & Overlay Structural Contract Test (`TST-UI-387.1`, `RoutesScreenContractTest.kt`)*:
  • Verify `RouteTabbedScreen` is rendered unconditionally in the non-details branch (`selectedRouteIdForDetails == null`).
  • Verify `EditRouteScreen` is composed *inside* the same branch alongside `RouteTabbedScreen` conditioned on `if (selectedRouteIdForEdit != null)`.
  • Verify `else if (selectedRouteIdForEdit != null)` does NOT exist as a mutually exclusive sibling branch to `RouteTabbedScreen`.
  2. *BackHandler & State Dismissal Contract Test (`TST-UI-387.2`, `RoutesScreenContractTest.kt`)*:
  • Verify `EditRouteScreen` receives `onSave` updating route via `viewModel.updateRoute(it)` and clearing `selectedRouteIdForEdit = null`.
  • Verify `EditRouteScreen` receives `onCancel` clearing `selectedRouteIdForEdit = null`.
  • Verify `BackHandler` is active while editing to reset `selectedRouteIdForEdit = null`.
  3. *9-Language Localization & String Resource Audit (`TST-UI-387.3`)*:
  • Verify zero new user-facing strings are introduced, and all existing route editing string tokens (`route_edit`, `name`, `route_sport_type`, `save`, `cancel`) remain intact across all 9 canonical locales (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`).
  4. *Clean-Room Regression Suite Execution (`TST-UI-387.4`)*:
  • Execute `./gradlew testDebugUnitTest` verifying 100% pass rate with zero regressions across the full test suite.

### TST-UI-388: **Physiology Models 'Show in Workout Summary' Gating, Rule 59 Subordinate Switch Scale & Hierarchy, Lighter Checked Track Color, Coggan-Only PowerMetricSlot, Modularity & 9-Language Parity Verification Suite** [Granular 'Show in Workout Summary' gating for each physiology model, Rule 59 subordinate switch downscaling (0.6f, 24dp height, lighter primary alpha checked track color), PowerMetricSlot without basic power fallback, TuningPreferencesDataStore persistence, < 400 and < 500 line modularity invariants, 9-language dead string removal and parity, and clean-room full test pass specified.]
- **Requirement(s)**: `REQ-UI-424`, `REQ-UI-403`, `REQ-UI-415`
- **Component**: **Physiology Models 'Show in Workout Summary' Gating, Rule 59 Subordinate Switch Scale & Hierarchy, Lighter Checked Track Color, Coggan-Only PowerMetricSlot, Modularity & 9-Language Parity Verification Suite**
- **Jira**: ``ATT-4369`, `ATT-4432`, `ATT-4464`, `ATT-4513``
- **Test Description**:
  1. *PowerMetricSlot Resolution without Basic Power Fallback Unit Test (`TST-UI-388.1`, `PowerMetricSlotResolutionTest.kt`)*:
  • *Given* workout telemetry with normalizedPower, intensityFactor, tss, workKj, and avgPowerToWeight,
  • *When* calling `resolvePowerMetricSlots` with `enableCogganPowerMetrics = true` and `showCogganPower = true`,
  • *Then* exactly 3 Coggan slots `[NORMALIZED_POWER, INTENSITY_FACTOR, TSS]` are returned.
  • *When* calling with `enableCogganPowerMetrics = true`, `showCogganPower = false`,
  • *Then* an empty list `[]` is returned (zero basic power fallback).
  • *When* calling with `enableCogganPowerMetrics = false`,
  • *Then* an empty list `[]` is returned.
  • *When* calling with `enableCogganPowerMetrics = true`, `showCogganPower = true`, but null FTP metrics (`intensityFactor == null`, `tss == null`),
  • *Then* strictly 1 slot `[NORMALIZED_POWER]` is returned, without filling from basic power.
  • *When* calling with null `normalizedPowerWatts`,
  • *Then* an empty list `[]` is returned.
  2. *WorkoutDetails Presentation Gating & Divider Integrity Contract Test (`TST-UI-388.2`, `WorkoutDetailsLayoutTest.kt`)*:
  • *Given* `WorkoutDetails` composable,
  • *When* all summary toggles (`showCogganPowerInWorkoutSummary`, `showAerobicEfficiencyInWorkoutSummary`, `showCriticalPowerInWorkoutSummary`) are `false`,
  • *Then* verify `PowerTrainingMetricsSection`, `AerobicEfficiencyRow`, and `AnaerobicBatteryRow` are omitted, and zero dividers are rendered after Time/Distance (or exactly one divider if Altitude is present).
  • *When* only `showCogganPowerInWorkoutSummary = true` and NP is present, verify `PowerTrainingMetricsSection` is rendered with exactly one preceding divider.
  • *When* only `showAerobicEfficiencyInWorkoutSummary = true`, verify `AerobicEfficiencyRow` is rendered with exactly one preceding divider.
  • *When* only `showCriticalPowerInWorkoutSummary = true`, verify `AnaerobicBatteryRow` is rendered with exactly one preceding divider.
  • Verify `WorkoutDetails.kt` line count is strictly `< 500` lines.
  3. *TuningConfig & DataStore Persistence Contract Test (`TST-UI-388.3`, `PhysiologyModelsVisibilityContractTest.kt`, `TuningFormControlsTest.kt`)*:
  • *Given* `TuningConfig()` defaults,
  • *Then* verify: exactly 4 models exist; `showCogganPowerInWorkoutSummary = true`, `showAerobicEfficiencyInWorkoutSummary = true`, `showCriticalPowerInWorkoutSummary = true`.
  • Verify `TuningPreferencesDefaults` contains zero basic power constants (`DEFAULT_ENABLE_BASIC_POWER_METRICS` and `DEFAULT_SHOW_BASIC_POWER_IN_WORKOUT_SUMMARY` removed).
  • Verify `TuningPreferencesDataStore.ALL_KEYS` contains exactly 3 summary preference keys.
  • Verify `PhysiologyModelsSection.kt` renders subordinate "Show in Workout Summary" toggles via `SubordinateTuningToggleItem` (Rule 59) when master switches are enabled.
  • Verify `SubordinateTuningToggleItem` downscales `Switch` to `Modifier.scale(0.6f)`, enforces compact `Modifier.height(24.dp)`, and applies lighter subordinate checked track color `MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)` and thumb `MaterialTheme.colorScheme.primary` via `SwitchDefaults.colors()` (`TuningFormControlsTest.kt`, `PhysiologyModelsVisibilityContractTest.kt`, `ATT-4513`).
  • Verify every `.kt` file under `ui/settings/tuning/` (`PhysiologyModelsSection.kt`, `AdvancedTuningScreen.kt`, `AdvancedTuningDialog.kt`, `TuningFormControls.kt`) has strictly `< 400` lines (`AdvancedTuningModularityTest.kt`).
  4. *Live Cockpit & Background Persistence Invariant Contract Test (`TST-UI-388.4`, `PhysiologyModelsVisibilityContractTest.kt`)*:
  • *Given* `EditSensorFieldViewModel.shouldIncludeSensorType` and `SensorGridScreen` field visibility,
  • *When* evaluated,
  • *Then* verify they continue to evaluate master model toggles (`enableCogganPowerMetrics`, `enableCriticalPowerModel`), completely independent of summary toggles.
  • Verify baseline physics metrics (`SensorType.WORK_KJ`, `SensorType.POWER_TO_WEIGHT`) remain fully selectable in live cockpit tiles.
  • Verify background recording in `TrackerService` and SQLite storage remains untouched.
  5. *9-Language Localization Clean-Up & Parity Audit (`TST-UI-388.5`, `PhysiologyModelsVisibilityContractTest.kt`)*:
  • *Given* all 9 canonical locales (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`),
  • *Then* verify string keys `tuning_physiology_basic_power_title` and `tuning_physiology_basic_power_desc` are completely removed across all 9 locales.
  • Verify remaining summary toggle strings exist and are non-blank with matching format specifiers.
  6. *Clean-Room Full Suite Regression Execution (`TST-UI-388.6`)*:
  • *When* executing `./gradlew testDebugUnitTest`,
  • *Then* assert 100% pass rate with zero regressions across the complete test suite.

### TST-UI-389: **Elevation Gain Interval Filtering (Min/Max Range and Preset Intervals) Verification Suite across Routes, Favorite Routes, and Workouts** [Elevation gain interval filtering accurately filters routes, favorite tracks, and workouts across preset chips and custom min/max bounds; active filter count reflects strictly 1 dimension for elevation gain; JSON persistence is backward compatible; chips format cleanly; 9-language parity and full clean-room pass verified.]
- **Requirement(s)**: `REQ-UI-425`
- **Component**: **Elevation Gain Interval Filtering (Min/Max Range and Preset Intervals) Verification Suite across Routes, Favorite Routes, and Workouts**
- **Jira**: ``ATT-4367``
- **Test Description**:
  1. *RouteFilterCriteria Elevation Gain Interval & Active Count Test (`TST-UI-389.1`, `RouteFilterCriteriaTest.kt`)*:
  • Test bounded interval `[250 m, 500 m]` (`min = 250.0, max = 500.0`): assert 250 m, 400 m, 500 m match; 50 m, 100 m, 1200 m fail.
  • Test upper bound only `< 100 m` (`min = null, max = 100.0`): assert 50 m, 100 m match; 250 m, 400 m, 500 m, 1200 m fail.
  • Test lower bound only `> 500 m` (`min = 500.0, max = null`): assert 500 m, 1200 m match; 50 m, 100 m, 250 m, 400 m fail.
  • Test `activeFilterCount`: assert `minOnly` gives 1, `maxOnly` gives 1, and `bothBounds` gives 1 (strictly 1 dimension for elevation gain).
  2. *RouteFilterCriteria JSON Serialization Roundtrip & Backward Compatibility Test (`TST-UI-389.2`, `RouteFilterCriteriaTest.kt`)*:
  • Call `toJson()` on a two-bound instance, deserialize with `RouteFilterCriteria.fromJson()`, and assert properties match.
  • Deserialize legacy JSON lacking `maxElevationGainMeters`: assert `maxElevationGainMeters = null` and `minElevationGainMeters` is preserved without errors.
  3. *ClusterFilterCriteria Elevation Gain Interval & Active Count Test (`TST-UI-389.3`, `ClusterFilterCriteriaTest.kt`)*:
  • Test bounded interval `[100 m, 250 m]` (`min = 100.0, max = 250.0`) on cluster resolved ascent: assert 150 m, 250 m match; 50 m, 350 m, 500 m, 800 m fail.
  • Test upper bound only `< 100 m`: assert 50 m matches; 150 m, 250 m fail.
  • Test lower bound only `> 500 m`: assert 500 m, 800 m match; 350 m fails.
  • Test `activeFilterCount`: assert setting min, max, or both contributes strictly 1 to count.
  4. *ClusterFilterCriteria JSON Serialization Roundtrip & Backward Compatibility Test (`TST-UI-389.4`, `ClusterFilterCriteriaTest.kt`)*:
  • Execute JSON roundtrip with `minElevationGainMeters` and `maxElevationGainMeters` and verify legacy JSON without elevation keys parses safely with null.
  5. *WorkoutFilterCriteria Elevation Gain Interval & Active Count Test (`TST-UI-389.5`, `WorkoutFilterCriteriaTest.kt`)*:
  • Test bounded interval `[100 m, 250 m]` (`min = 100.0, max = 250.0`) on workout `ascentMeters`: assert 100L, 200L, 250L match; 40L, 600L, 1200L fail.
  • Test upper bound only `< 100 m`: assert 40L, 100L match; 200L, 250L, 600L fail.
  • Test lower bound only `> 500 m`: assert 600L, 1200L match; 250L fails.
  • Test `activeFilterCount`: assert setting min, max, or both contributes strictly 1 to count.
  6. *WorkoutFilterCriteria JSON Serialization Roundtrip & Backward Compatibility Test (`TST-UI-389.6`, `WorkoutFilterCriteriaTest.kt`)*:
  • Execute JSON roundtrip with `minElevationGainMeters` and `maxElevationGainMeters` and verify legacy JSON without elevation keys parses safely with null.
  7. *Active Filter Chips Row Elevation Formatting & Dismissal Contract Test (`TST-UI-389.7`, `RouteFilterCriteriaTest.kt` / `ActiveFilterChipsRowTest.kt`)*:
  • Verify interval formatting produces `"$min - $max m"` (e.g. `"250 - 500 m"`), `"> $min m"` (e.g. `"> 500 m"`), and `"< $max m"` (e.g. `"< 100 m"`).
  • Verify dismissal contract: `criteria.copy(minElevationGainMeters = null, maxElevationGainMeters = null)` clears both elevation bounds.
  8. *9-Language Localization & Format Specifier Parity Test (`TST-UI-389.8`, `TranslationParityTest.kt`)*:
  • Verify string resources `filter_max_elevation_format` and `filter_elevation_interval_format` exist across all 9 application locales (`values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`) with 100% format specifier parity (`%1$s`, `%2$s`).
  9. *Clean-Room Regression Suite Execution (`TST-UI-389.9`)*:
  • Run `./gradlew testDebugUnitTest` verifying 100% pass rate.

### TST-UI-390: **BikeBellButton MutableInteractionSource Press Monitoring, Long-Press Continuous Ring Disambiguation & Looping Roll Synthesis Verification Suite** [Contention-free long-press continuous ring via MutableInteractionSource press monitoring on ElevatedButton; single-tap disambiguation suppressing accidental ring on release; continuous shake animation loop; AudioTrack.setLoopPoints indefinite roll looping in BikeBellAudioSynthesizer; Rules 53 and 58 zero gesture contention; 100% test pass rate with zero regressions.]
- **Requirement(s)**: `REQ-UI-421`, `REQ-UI-421.2`, `REQ-UI-421.4`
- **Component**: **BikeBellButton MutableInteractionSource Press Monitoring, Long-Press Continuous Ring Disambiguation & Looping Roll Synthesis Verification Suite**
- **Jira**: ``ATT-4431``
- **Test Description**:
  1. *BikeBellButton InteractionSource Press-and-Hold & Continuous Shake Animation Unit Test (`TST-UI-390.1`, `BikeBellButtonContractTest.kt`)*:
  • Verify `BikeBellButton` instantiates `remember { MutableInteractionSource() }` and passes it to `ElevatedButton`.
  • Verify `LaunchedEffect` collects `PressInteraction.Press` and launches long-press delay timer via `LocalViewConfiguration.current.longPressTimeoutMillis` (~400 ms).
  • Verify long-press timer triggers `onContinuousRingStart()`, haptic pulse, and continuous looping shake animation while held.
  2. *BikeBellButton Release, Cancellation & Unmount Teardown Unit Test (`TST-UI-390.2`, `BikeBellButtonContractTest.kt`)*:
  • Verify `PressInteraction.Release` cancels timer, invokes `onContinuousRingStop()`, and resets rotation to 0.
  • Verify `PressInteraction.Cancel` cancels timer, invokes `onContinuousRingStop()`, and resets rotation to 0.
  • Verify `DisposableEffect` invokes `onContinuousRingStop()` if button is unmounted during active roll.
  3. *Single-Tap Disambiguation & Spurious Chime Suppression Unit Test (`TST-UI-390.3`, `BikeBellButtonContractTest.kt`)*:
  • Verify short tap (< 400 ms) invokes `onSingleRing()` via `ElevatedButton(onClick)` with one-shot shake animation.
  • Verify long-press release resets `wasLongPressHandled` and suppresses `onSingleRing()`, preventing duplicate strike on finger release.
  4. *Cockpit Zero Gesture Contention & Multi-Mode Gating Contract Test (`TST-UI-390.4`, `BikeBellButtonContractTest.kt`)*:
  • Verify `BikeBellButton.kt` contains zero `Modifier.pointerInput` or `detectTapGestures` on `ElevatedButton` (enforcing Rules 53 and 58).
  • Verify `isAvailable` evaluates `READY`, `TRACKING`, and `PAUSED` modes.
  5. *BikeBellAudioSynthesizer Looping Roll setLoopPoints Unit Test (`TST-UI-390.5`, `BikeBellAudioSynthesizerTest.kt`)*:
  • Verify `startRoll()` invokes `mockTrack.setLoopPoints(0, totalFrames, -1)` with `totalFrames = rollPcm.size / 2` (110,250 samples).
  • Verify `startRoll()` resets position, sets volume, and starts playback.
  • Verify `stopRoll()` halts track and resets playback head position.
  6. *BikeBellAudioSynthesizer Concurrency Safety & Lifecycle Teardown Test (`TST-UI-390.6`, `BikeBellAudioSynthesizerTest.kt`)*:
  • Verify 50 concurrent interleaved calls across `ring()`, `startRoll()`, `stopRoll()`, and `close()` execute thread-safely under `synchronized(lock)`.
  • Verify `close()` releases native tracks safely and subsequent calls safe no-op.
  7. *9-Language Localization & String Specifier Audit (`TST-UI-390.7`, `TranslationParityTest.kt`)*:
  • Verify `config_tracking__showBikeBell` and `config_tracking__showBikeBell_subtitle` exist with 100% parity across EN, DE, ES, FR, IT, JA, NL, PL, PT.
  8. *Clean-Room Full Regression Suite Execution (`TST-UI-390.8`)*:
  • Execute `./gradlew testDebugUnitTest` verifying 100% pass rate with zero regressions across the full project test suite.

### TST-UI-391: **Realistic Default Cycling FTP (270 W), Coggan 7-Zone and Karvonen Heart Rate Zone Baselines Verification Suite** [Default cycling FTP is 270 W, default Coggan power cutoffs are [149, 203, 243, 284, 324, 405] W, default Karvonen HR cutoffs are [132, 145, 158, 171] bpm for run and [127, 140, 153, 166] bpm for bike across DataStore, UI calculator dialogs, and tracking ViewModels.]
- **Requirement(s)**: `REQ-UI-426`, `REQ-UI-370`, `REQ-UI-373`
- **Component**: **Realistic Default Cycling FTP (270 W), Coggan 7-Zone and Karvonen Heart Rate Zone Baselines Verification Suite**
- **Jira**: ``ATT-4521, ATT-4543``
- **Test Description**:
  1. *SettingsDataStore Default Constants & Snapshot Fallback Unit Tests (`TST-UI-391.1`, `SettingsDataStoreBaselinesTest.kt`)*:
  • Verify `SettingsDataStore.DEFAULT_CYCLING_FTP == 270`.
  • Verify `SettingsDataStore.DEFAULT_HR_MAX == 185` and `DEFAULT_HR_REST == 55`.
  • Verify `SettingsSnapshot.DEFAULT.pwrBikeZoneMax == listOf(149, 203, 243, 284, 324, 405)`.
  • Verify `SettingsSnapshot.DEFAULT.hrRunZoneMax == listOf(132, 145, 158, 171)`.
  • Verify `SettingsSnapshot.DEFAULT.hrBikeZoneMax == listOf(127, 140, 153, 166)`.
  2. *SettingsDataStore getDefaultValue() and resetToDefaults() Unit Tests (`TST-UI-391.2`, `SettingsDataStoreBaselinesTest.kt`)*:
  • Verify `getDefaultValue(ZoneType.PWR_BIKE, zone)` returns 149, 203, 243, 284, 324, 405 for Zones 1..6.
  • Verify `getDefaultValue(ZoneType.HR_RUN, zone)` returns 132, 145, 158, 171 for Zones 1..4.
  • Verify `getDefaultValue(ZoneType.HR_BIKE, zone)` returns 127, 140, 153, 166 for Zones 1..4.
  • Verify `resetToDefaults()` writes these exact canonical baselines to DataStore.
  3. *ZoneCalculatorDialog Default State & Contract Tests (`TST-UI-391.3`, `ZoneCalculatorDialogContractTest.kt`)*:
  • Verify `ZoneCalculatorUiState` defaults `ftpInput` to `"270"`, `hrMaxInput` to `"185"`, and `hrRestInput` to `"55"`.
  • Verify `initialState_populatesStoredFtpOrFallback270W` defaults to 270 W when stored FTP is absent and computes Coggan cutoffs [149, 203, 243, 284, 324, 405].
  • Verify initial Karvonen calculation defaults compute `HR_RUN` cutoffs [132, 145, 158, 171] and `HR_BIKE` cutoffs [127, 140, 153, 166].
  4. *TrackingViewModel Runtime Fallback Cutoffs Contract Test (`TST-UI-391.4`, `TrackingViewModelGridTest.kt`)*:
  • Verify `zoneCutoffsMap` initial fallbacks match canonical cutoffs for PWR_BIKE, HR_RUN, and HR_BIKE.
  5. *Existing BatterySaver & NonBlocking Contract Test Updates (`TST-UI-391.5`, `BatterySaverModelPureTest.kt`, `SettingsDataStoreJavaHelperNonBlockingTest.kt`)*:
  • Update assertions asserting on default zone cutoffs to match 270 W and 185/55 bpm baselines.
  6. *Clean-Room Regression Suite Execution (`TST-UI-391.6`)*:
  • Run `./gradlew testDebugUnitTest` verifying 100% pass rate across the full project test suite.

### TST-UI-392: **Aftermath Loader Concurrency, Process-Death Detail Restoration, Elevation Gesture State Integrity and Lifecycle Safety Verification Suite** [Aftermath and peek loader coroutine cancellation verified; process-death detail restoration triggered reactively; elevation scrub gestures access live telemetry points; crop double-tap and CancellationException rethrow verified; bulk delete cache resync protected under NonCancellable; 100% test pass rate.]
- **Requirement(s)**: `REQ-UI-427`, `REQ-MAP-032`, `REQ-DAT-011`, `REQ-UI-368`
- **Component**: **Aftermath Loader Concurrency, Process-Death Detail Restoration, Elevation Gesture State Integrity and Lifecycle Safety Verification Suite**
- **Jira**: ``ATT-4018``
- **Test Description**:
  1. *Aftermath & Peek Loader Coroutine Cancellation Unit Tests (`TST-UI-392.1`, `TrackOnMapAftermathViewModelTest.kt`, `WorkoutClustersViewModelTest.kt`, `PeriodsViewModelTest.kt`)*:
  • Verify that dispatching `loadAftermathData` for a second workout cancels the previous `Job` before completing.
  • Verify that calling `selectWorkoutForPeek` consecutively cancels the previous peek coroutine job in both ViewModels.
  2. *Process-Death Restoration Reactive Trigger Contract Tests (`TST-UI-392.2`, `WorkoutSummariesTabbedScreenTest.kt`)*:
  • Verify that initializing `selectedWorkoutIdForDetails` without `onMapClick` triggers `loadAftermathData` via `LaunchedEffect`.
  3. *Elevation Profile Gesture Updated State Unit/Contract Tests (`TST-UI-392.3`, `ElevationProfileGestureTest.kt`)*:
  • Verify that `ElevationProfile` pointerInput evaluates `rememberUpdatedState(pathPoints)` and retrieves updated telemetry during ongoing scrub gestures.
  4. *Crop Double-Tap Guard & CancellationException Rethrow Unit Tests (`TST-UI-392.4`, `CropWorkoutViewModelTest.kt`, `WorkoutRepositoryCropTest.kt`)*:
  • In `CropWorkoutViewModelTest`, verify that `executeCrop` immediately updates `_uiState` with `isCropping = true` synchronously, rejecting concurrent calls.
  • In `WorkoutRepositoryCropTest`, verify that `cropWorkout` rethrows `CancellationException` rather than returning `Result.failure`.
  5. *Bulk Deletion NonCancellable Resync Unit Tests (`TST-UI-392.5`, `WorkoutRepositoryDeleteTest.kt`)*:
  • In `WorkoutRepositoryDeleteTest`, verify that cancelling the caller's coroutine during `deleteOldWorkouts` does NOT abort `resyncAllPeriods` and `refreshClusters`.
  6. *EditWorkoutViewModel Lifecycle Scoping Contract Tests (`TST-UI-392.6`, `EditWorkoutLifecycleTest.kt`)*:
  • Verify that exiting edit mode cleans up or properly scopes `EditWorkoutViewModel` without unbounded memory accumulation in the host store.
  7. *Localization Parity Audit (`TST-UI-392.7`, `TranslationParityTest.kt`)*:
  • Verify 100% translation parity across all 9 supported locales (0 new UI strings introduced).
  8. *Clean-Room Regression Suite Execution (`TST-UI-392.8`)*:
  • Run targeted unit tests and full clean-room suite verifying 100% test pass rate.

### TST-UI-393: **Atomic Single-Transaction DataStore Zone Persistence, SavedStateHandle Draft Preservation, Deduplicated Profile Loading & UI Decoupling Verification Suite** [Zone persistence is atomic and race-free, drafts are preserved across rotation via SavedStateHandle, loading is deduplicated, dead manifest entry removed, contract tests pass, and 100% clean-room unit test pass rate is maintained.]
- **Requirement(s)**: `REQ-UI-428`, `REQ-UI-172`, `REQ-UI-397`
- **Component**: **Atomic Single-Transaction DataStore Zone Persistence, SavedStateHandle Draft Preservation, Deduplicated Profile Loading & UI Decoupling Verification Suite**
- **Jira**: ``ATT-4021``
- **Test Description**:
  1. *Atomic Single-Transaction Persistence Unit Tests (`TST-UI-393.1`, `SettingsDataStoreZoneAtomicTest.kt`)*:
  • Verify `SettingsDataStore.saveZoneProfile` executes a single atomic `dataStore.edit {}` block writing all zone thresholds (Z1..Z4 or Z1..Z6) and `ZoneDisplayOptions` together.
  • Verify rapid consecutive calls update DataStore serially without race conditions or partial writes.
  2. *ViewModel / SavedStateHandle Draft Preservation Unit Tests (`TST-UI-393.2`, `ZoneSettingsViewModelTest.kt`)*:
  • Simulate entering non-monotonic drafts (e.g. Z2 < Z1); verify draft is held in `SavedStateHandle` without persisting invalid state to DataStore.
  • Simulate ViewModel recreation with restored `SavedStateHandle`; verify uncommitted draft is restored cleanly without data loss.
  • Verify committing a valid monotonic draft triggers atomic save.
  3. *Consolidated Zone Loading & Migration Unit Tests (`TST-UI-393.3`, `ZoneProfileLoaderTest.kt` / `SettingsDataStoreZoneAtomicTest.kt`)*:
  • Verify single consolidated loader reads `ZoneProfileState` for `HR_RUN`, `HR_BIKE`, and `PWR_BIKE` with migration applied.
  • Verify zero duplicate loading logic across screen tabs.
  4. *Contract Test & Architectural Decoupling Audit (`TST-UI-393.4`, `ZonesSettingsHeaderContractTest.kt`, `ZonesSettingsPreviewPillsContractTest.kt`, `AthleteProfileContractAndLocalizationTest.kt`)*:
  • Verify all architectural contract tests in `ZonesSettingsHeaderContractTest`, `ZonesSettingsPreviewPillsContractTest`, and `AthleteProfileContractAndLocalizationTest` pass without modification.
  • Verify `AndroidManifest.xml` does not declare unstarted `ZonesSettingsActivity`.
  • Verify safe window insets controller execution without direct `(context as Activity)` cast.
  5. *Localization Parity Audit (`TST-UI-393.5`, `TranslationParityTest.kt`)*:
  • Verify 100% translation parity across all 9 supported locales (0 new UI strings introduced).
  6. *Clean-Room Full Suite Regression Execution (`TST-UI-393.6`)*:
  • Execute `./gradlew testDebugUnitTest` verifying 100% test pass rate across the full project test suite.

### TST-UI-394: **Global User Interface Localization Parity, Unit Adaptability, and Accessibility Semantics Verification Suite** [All extracted string resources maintain 100% 9-language parity; CropWorkoutScreen and WorkoutFilterBottomSheet adapt to metric/imperial units; TalkBack semantics provided for buttons and charts; 100% clean-room test pass rate.]
- **Requirement(s)**: `REQ-UI-429`, `REQ-LOC-001`, `REQ-UI-122`, `REQ-UI-157`
- **Component**: **Global User Interface Localization Parity, Unit Adaptability, and Accessibility Semantics Verification Suite**
- **Jira**: ``ATT-4026``
- **Test Description**:
  1. *9-Language Localization & Format Specifier Parity Test (`TST-UI-394.1`, `TranslationParityTest.kt`)*:
  • Verify all extracted string resources exist across all 9 locales (EN, DE, ES, FR, IT, JA, NL, PL, PT) with identical positional argument tokens (`%1$s`, `%1$d`).
  2. *Crop Workout Screen Unit Adaptability Unit Tests (`TST-UI-394.2`, `CropWorkoutScreenTest.kt`)*:
  • Verify distance and speed values and unit labels display in miles and mph when `MyUnits.IMPERIAL` is active, and km and km/h when `MyUnits.METRIC` is active.
  3. *Workout Filter BottomSheet Imperial Conversion Unit Tests (`TST-UI-394.3`, `WorkoutFilterBottomSheetTest.kt`)*:
  • Verify distance chips display miles and convert to meters accurately using imperial multiplier when `MyUnits.IMPERIAL` is active.
  • Verify elevation chips display feet and convert to meters accordingly.
  4. *TalkBack Accessibility & Content Description Semantics Tests (`TST-UI-394.4`, `UiAccessibilityContractTest.kt`)*:
  • Verify interactive `IconButton` components provide non-null localized `contentDescription`s.
  • Verify canvas chart components expose semantic descriptions for TalkBack.
  5. *Clean-Room Full Suite Regression Execution (`TST-UI-394.5`)*:
  • Execute `./gradlew testDebugUnitTest` verifying 100% pass rate across the full test suite.

### TST-UI-395: **Multi-Provider Cloud Settings Layout Unification, Symmetric Configuration & Localization Parity Verification Suite** [Disconnected prompt and connected switches verified for both cloud providers; backup interval and last backup available symmetrically; Compose safe painter contracts preserved; 100% localization parity; 100% clean-room test pass rate.]
- **Requirement(s)**: `REQ-UI-430`, `REQ-UI-152`, `REQ-DAT-020.6`, `REQ-LOC-001`
- **Component**: **Multi-Provider Cloud Settings Layout Unification, Symmetric Configuration & Localization Parity Verification Suite**
- **Jira**: ``ATT-4666``
- **Test Description**:
  1. *Dropbox Settings Dialog State & Granular Switches Test (`TST-UI-395.1`, `DropboxSettingsDialogTest.kt`)*:
  • Verify disconnected state hides switches and shows `dropbox_connect_prompt`.
  • Verify connected state displays account email, upload workouts switch, upload backup switch, conditional backup interval selector, Wi-Fi only switch, and last sync/backup status.
  • Verify saving commits `uploadWorkoutsToDropbox`, `uploadBackupToDropbox`, `uploadToDropboxOnlyOnWifi`, and `backup_interval_days`, and reschedules `BackupWorker`.
  2. *Google Drive Settings Dialog Backup Interval & Parity Test (`TST-UI-395.2`, `GoogleDriveSettingsDialogTest.kt`)*:
  • Verify connected state displays backup interval selector when `uploadBackupToGoogleDrive` is true.
  • Verify saving commits `backup_interval_days` and reschedules `BackupWorker`.
  • Verify last backup timestamp is displayed alongside last sync.
  3. *Shared Cloud Component Architectural Contract Test (`TST-UI-395.3`, `ComposeSafePainterContractTest.kt`, `CloudSettingsComponentContractTest.kt`)*:
  • Verify `DropboxConnectionHeader` and `GoogleDriveConnectionHeader` delegate cleanly to `CloudConnectionHeader`.
  • Verify `ComposeSafePainterContractTest` passes with zero regressions on `DropboxSettingsDialog.kt` and `DropboxConnectionHeader.kt`.
  4. *9-Language Localization & Format Specifier Parity Test (`TST-UI-395.4`, `TranslationParityTest.kt`)*:
  • Verify `dropbox_connect_prompt`, `upload_workouts_to_dropbox_summary`, `upload_backup_to_dropbox_summary`, and `last_backup_never` exist across all 9 locales (EN, DE, ES, FR, IT, JA, NL, PL, PT) with identical tokens.
  5. *Clean-Room Full Suite Regression Execution (`TST-UI-395.5`)*:
  • Execute `./gradlew testDebugUnitTest` verifying 100% test pass rate across the full project test suite.

### TST-UI-396: **Landscape Compact Aligned Marginal Zone Distribution Bars Verification Suite** [Side-by-side 72/28 Row layout in landscape mode; 1:1 vertical Y-axis alignment with graph zone thresholds; granular text legibility for zone, percentage, duration; synchronous FIVE_ZONES and HISTOGRAM display mode switching; portrait 25dp inline strip regression preservation; 100% clean-room test pass rate.]
- **Requirement(s)**: `REQ-UI-431`, `REQ-UI-251`, `REQ-UI-206`
- **Component**: **Landscape Compact Aligned Marginal Zone Distribution Bars Verification Suite**
- **Jira**: ``ATT-2145``
- **Test Description**:
  1. *Landscape Proportional Dual-Pane Layout Composition Contract Test (`TST-UI-396.1`, `TelemetryMetricGraphLandscapeContractTest.kt`)*:
  • Verify that in landscape orientation (`Configuration.ORIENTATION_LANDSCAPE`), `TelemetryMetricGraph` branches into a side-by-side `Row` layout.
  • Verify the chart canvas is allocated proportional weight ~72% (`0.72f`) and the marginal distribution pane is allocated ~28% (`0.28f`).
  2. *Vertical Y-Axis Coordinate Alignment & Normalization Math Test (`TST-UI-396.2`, `TelemetryMetricGraphLandscapeContractTest.kt`)*:
  • Verify the top and bottom vertical boundaries of each horizontal zone bar match the exact Y-normalization coordinates of the telemetry graph zone thresholds.
  • Verify that top and bottom graph plot paddings are symmetrically respected by the marginal pane.
  3. *Granular Metrics Legibility & Formatting Contract Test (`TST-UI-396.3`, `TelemetryMetricGraphLandscapeContractTest.kt`)*:
  • Verify zone bars display legible text containing Zone Label (`Z1`–`Z5`), Percentage share, and formatted Duration (`TimeFormatter`).
  • Verify text contrast and bounds clipping prevention logic.
  4. *Synchronous Display Mode Switching Test (`TST-UI-396.4`, `TelemetryMetricGraphLandscapeContractTest.kt`)*:
  • Verify synchronous rendering transitions between 5-Zone horizontal bars (`FIVE_ZONES`, `COLUMNS`, `STACKED_BAR`) and frequency distribution bars (`HISTOGRAM`).
  5. *Portrait Invariant & Scrubber Alignment Regression Test (`TST-UI-396.5`, `TelemetryMetricGraphMarginalBarsContractTest.kt`)*:
  • Verify that portrait mode retains the 25dp inline canvas strip per `REQ-UI-251` with 100% backward compatibility.
  • Verify that `startPadding = 50.dp` is preserved across both orientations to guarantee multi-chart scrubber alignment.
  6. *Clean-Room Full Suite Regression Execution (`TST-UI-396.6`)*:
  • Execute `./gradlew testDebugUnitTest` verifying 100% test pass rate across the full project test suite.

### TST-UI-397: **In-App Feature Discovery & Tips Guide, Navigation Drawer Integration & 9-Language Parity Verification Suite** [In-app feature discovery destination 'tips_guide' hosted in single-activity architecture, drawer integration under collapsible Settings group (23 total destinations, 9 non-collapsible default visible rows, advanced tuning terminal position preserved), 7 curated tips with deep-link navigation actions, zero raster asset bloat, 100% 9-language localization parity across all 32 string resources, and clean-room unit test pass rate.]
- **Requirement(s)**: `REQ-UI-432`
- **Component**: **In-App Feature Discovery & Tips Guide Verification Suite**
- **Jira**: ``ATT-2276``
- **Test Description**:
  1. *NavRoutes Route and Drawer ID Bidirectional Mapping Unit Test (`TST-UI-397.1`, `SingleActivityNavigationTest.kt`)*:
  • Verify `NavRoutes.TIPS_GUIDE == "tips_guide"`.
  • Verify `NavRoutes.fromDrawerItemId(R.id.drawer_tips_guide) == NavRoutes.TIPS_GUIDE`.
  • Verify `NavRoutes.toDrawerItemId(NavRoutes.TIPS_GUIDE) == R.id.drawer_tips_guide`.
  • Verify `NavRoutes.toSettingsBottomSheetType(R.id.drawer_tips_guide) == null`.
  2. *Navigation Drawer Group Structure & 23-Destination Item Integrity Unit Test (`TST-UI-397.2`, `NavigationDrawerStateTest.kt`)*:
  • Verify `createDrawerGroups` maintains exactly 5 category hubs.
  • Verify `settingsGroup.items` contains `R.id.drawer_tips_guide` positioned before `drawer_privacy_policy` and `drawer_advanced_tuning`.
  • Verify total destination count is 23 across all 5 groups, and distinct ID count is 23.
  • Verify default visible count across non-collapsible groups strictly remains 9.
  • Verify `drawer_tips_guide` icon is `R.drawable.ic_tips_and_updates` and title is `R.string.drawer_tips_guide`.
  3. *TipsGuideScreen Model, Expandable State & Deep-Link Dispatch Unit/Contract Test (`TST-UI-397.3`, `TipsGuideScreenContractTest.kt`)*:
  • Verify data model defines exactly 7 curated tips with respective title, subtitle, description, icon, and route targets.
  • Verify all 7 deep-link action callbacks (`onNavigateToLocations`, `onNavigateToSensors`, `onNavigateToDisplays`, `onNavigateToSegments`, `onNavigateToEquipment`, `onNavigateToZones`, `onNavigateToBackup`) dispatch cleanly when triggered.
  4. *9-Language Localization & Format Specifier Parity Test (`TST-UI-397.4`, `TranslationParityTest.kt`)*:
  • Verify all 32 string resources are defined across all 9 supported locales (`values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`).
  • Verify 100% translation coverage, matching positional format specifiers, and literal `\n` linebreaks.
  5. *Clean-Room Full Suite Regression Execution (`TST-UI-397.5`)*:
  • Execute `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.navigation.*" --tests "com.atrainingtracker.trainingtracker.localization.*"` and verify 100% pass rate.


