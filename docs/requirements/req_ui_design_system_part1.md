# Living Requirements: UI Components & Design System (Part 1 of 5)

**Document ID**: `DOC-REQ-UI-P1`  
**Version**: 1.0.2  
**Status**: Reviewed  
**Approver**: Pending PO Sign-Off  

**Domain**: `ui_design_system_part1`  
**Prefixes**: `UI`, `UNT`  
**Scope**: Jetpack Compose design system, UI components, typography, dialogs, gestures, and layout contracts.  
**Total Requirements**: 148

---

### REQ-UI-521: Weekly Period Date Range [Verified]
- **Tests**: `TST-UI-074`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `PeriodsRepository.kt`, `PeriodSummaryCard.kt`
- **Rationale**: The system SHALL display the explicit start and end dates (e.g., \"May 11 - May 17\") below the title for weekly period summaries in the Periods screen to provide precise temporal context.

### REQ-UI-002: TimeFormatter Display Formatting [Verified]
- **Tests**: `TST-UNT-002`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `TimeFormatter.java`
- **Rationale**: The system SHALL format duration elapsed time without leading zero hours or zero minutes when unneeded, generating localized string representations for seconds, minutes:seconds, and hours:minutes:seconds formats, and handling null timestamp values safely with placeholder `--:--:--` in `TimeFormatter`.
- **Description**: TimeFormatter Display Formatting.

### REQ-UI-008: Structured Architecture & Elevation Profile Linking [Implemented]
- **Tests**: `TST-STR-001`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit / Review
- **Implementation**: `ElevationProfile.kt`, `WorkoutDataMapper.kt`
- **Rationale**: The system SHALL link `SHOW_ELEVATION_PROFILE` to map visibility (`showMap ? 1 : 0`), preserving sensor filter presets, view size seeding, and user customization sovereignty across workout views.
- **Description**: Structured Architecture & Elevation Profile Linking.

### REQ-UI-009: Session Isolation & Workout Transition UI [Verified]
- **Tests**: `TST-UI-029`, `TST-INT-004`, `TST-STR-003`
- **Priority**: High
- **Type**: Functional
- **Verification Method**: Unit / Integration
- **Implementation**: `TrackerService.java`, `LiveTrackingScreen.kt`
- **Rationale**: The system SHALL strictly isolate consecutive workout tracking sessions, clearing accumulators, active route polylines, and extrema tables upon stopping a workout before immediately starting a subsequent recording.
- **Description**: Session Isolation & Workout Transition UI.

### REQ-UI-010: Navigation Drawer Architecture & Tab Configuration Layout [Implemented]
- **Tests**: `TST-INT-005`, `TST-INT-006`
- **Priority**: High
- **Type**: Functional
- **Verification Method**: Unit / Review
- **Implementation**: `AppNavigationDrawer.kt`, `TabConfigurationActivity.java`
- **Rationale**: The system SHALL structure the navigation drawer into coherent thematic groups while strictly preserving existing routes and actions, and render the tab configuration screen responsively across narrow viewports and split-screen mode without clipping checkboxes or titles.
- **Description**: Navigation Drawer Architecture & Tab Configuration Layout.

### REQ-UI-011: Professional Extrema Table [Implemented]
- **Tests**: `TST-UI-001`, `TST-UI-011`, `TST-UI-012`, `TST-UI-013`, `TST-UI-014`, `TST-UI-015`, `TST-UI-016`, `TST-UI-017`, `TST-UI-018`, `TST-UI-019`, `TST-UI-020`, `TST-LOG-001`, `TST-LOG-002`, `TST-UNT-009`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `WorkoutExtrema.kt`, `WorkoutDataMapper.kt`
- **Rationale**: The workout summary SHALL feature a world-class aesthetic for the min/mean/max table, including optimized core metrics, dual Speed/Pace display for runs, bracketed micro-units, bottom-baseline alignment, mathematical average notation (Ø), context-aware bolding, and strict mathematical ordering.

### REQ-UI-012: Ascent/Descent Priority [Verified]
- **Tests**: `TST-UI-021`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `WorkoutDetails.kt`, `WorkoutDetailsData.kt`
- **Rationale**: The workout details row SHALL display a compact Ascent/Descent view with prioritized visual hierarchy (24dp icons). Redundant Min/Max altitude values SHALL NOT be displayed in this row.

### REQ-UI-013: Harmonized Elevation Extrema [Implemented]
- **Tests**: `TST-UI-026`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `ElevationProfile.kt`, `WorkoutSummary.kt`, `TrackOnMapScreen.kt`
- **Rationale**: Ensure the Elevation Profile chart and the summary table display identical min/max values to prevent visual inconsistency and user confusion.
- **Description**: Harmonized Elevation Extrema.

### REQ-UI-014: Authoritative Average Speed [Verified]
- **Tests**: `TST-UI-027`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `TrackerService.java`
- **Rationale**: Align the Speed sensor's average value in the extrema table with the global workout average calculated from distance and active time (mDistanceTotal_m / mTimeActive_s) in real-time.
- **Description**: Authoritative Average Speed.

### REQ-UI-015: Unified Metric component system [Implemented]
- **Tests**: `TST-STR-006`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `MetricItem.kt`, `MetricBadge.kt`
- **Rationale**: Ensure cross-screen consistency for data presentation.
- **Description**: Unified Metric component system.

### REQ-UI-016: Centralized metric formatting context [Implemented]
- **Tests**: `TST-STR-007`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `MetricFormatterContext.kt`
- **Rationale**: Single source of truth for units and precision.
- **Description**: Centralized metric formatting context.

### REQ-UI-017: Standardized list items and placeholders [Implemented]
- **Tests**: `TST-STR-008`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `MappableListItem.kt`, `EmptyStatePlaceholder.kt`
- **Rationale**: Harmonize the "look and feel" of all list views.
- **Description**: Standardized list items and placeholders.

### REQ-UI-018: Refined Elevation Terminology [Verified]
- **Tests**: `TST-UI-028`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `WorkoutDetails.kt`, `strings.xml`
- **Rationale**: Distinguish between absolute altitude ("Altitude"/"Höhe") and relative gain ("Elevation"/"Höhenmeter") in all languages.
- **Description**: Refined Elevation Terminology.

### REQ-UI-020: Consistent Vertical Metric Representation [Verified]
- **Tests**: `TST-UI-030`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `WorkoutDetails.kt`, `SegmentDetails.kt`
- **Rationale**: Provide a harmonized technical representation of elevation data across all list items using standardized icons and visual lead-ins.
- **Description**: Consistent Vertical Metric Representation.

### REQ-UI-021: Elevation Section Hierarchy [Verified]
- **Tests**: `TST-UI-031`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `WorkoutDetails.kt`
- **Rationale**: In the Workout Summary, the descriptive elevation text SHALL be positioned above the mountain symbol to provide a clear visual hierarchy.

### REQ-UI-024: Optimized Route Identity Layout [Verified]
- **Tests**: `TST-UI-034`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `RouteSummaryHeader.kt`
- **Rationale**: The Route Name shall be granted maximum horizontal priority. Secondary metadata (Source) must be subordinate. For Strava routes, the text label must be replaced by the official logo to eliminate redundancy. Control elements (Switch) must be positioned in the metrics row to prevent layout conflicts.
- **Description**: Optimized Route Identity Layout.

### REQ-UI-025: Decoupled Control Positioning [Verified]
- **Tests**: `TST-UI-035`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `RouteSummaryHeader.kt`
- **Rationale**: The visibility switch shall be positioned as an overlay in the bottom-right of the header area to decouple its technical height from the metrics layout, enabling maximum vertical density.
- **Description**: Decoupled Control Positioning.

### REQ-UI-028: Hierarchical Detailed Metrics [Verified]
- **Tests**: `TST-UI-038`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `PeriodSummaryCard.kt`, `MetricItem.kt`
- **Rationale**: To establish a clear visual hierarchy in Period Summaries, secondary detailed stats (sub-sports and specific highlights) must be rendered using a reduced scale (14dp icons and bodySmall typography) while maintaining legibility.
- **Description**: Hierarchical Detailed Metrics.

### REQ-UI-026: Consistent Branding Scaling [Verified]
- **Tests**: `TST-UI-036`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `SegmentList.kt`, `SegmentDetails.kt`
- **Rationale**: Official third-party logos (e.g., Strava) must be scaled to subordinate heights (10dp-24dp) when used as source metadata or list headers to prevent visual dominance over primary athletic data.
- **Description**: Consistent Branding Scaling.

### REQ-UI-033: Period Peak Markers [Verified]
- **Tests**: `TST-UI-041`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `InteractivePeriodMap.kt`, `PeriodsViewModel.kt`, `PeriodMapScreen.kt`
- **Rationale**: The `PeriodMapScreen` SHALL display optional and selectable markers for the Start, End, Maximum Altitude, and Maximum Line Distance of each workout in the period.
- **Description**: Period Peak Markers.

### REQ-UI-035: Peak Marker Alpha Sync [Verified]
- **Tests**: `TST-UI-041`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `InteractivePeriodMap.kt`, `PeriodMapUtils.kt`
- **Rationale**: Technical markers in the Period Map SHALL use the same Alpha (opacity) as the workout's polyline (defined by the period type) to ensure visual consistency.
- **Description**: Peak Marker Alpha Sync.

### REQ-UI-036: Peak Marker Interactivity [Verified]
- **Tests**: `TST-UI-041`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `InteractivePeriodMap.kt`, `PeriodMapScreen.kt`
- **Rationale**: Tapping a technical marker SHALL select the associated workout and open its details in the BottomSheet.
- **Description**: Peak Marker Interactivity.

### REQ-UI-038: Live Pause Updates [Implemented]
- **Tests**: `TST-REG-007`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `WorkoutDetails.kt`, `TrackerService.java`
- **Rationale**: The user interface SHALL reflect real-time updates to the Total Duration of an active workout session while paused, ensuring accurate visibility into the overall elapsed time.
- **Description**: Live Pause Updates.

### REQ-UI-041: Composable Device Management [Verified]
- **Tests**: `TST-UI-043`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `ListDeviceFragment.kt`, `DevicesTabbedContainerFragment.kt`, `EditGeneralDeviceFragment.kt`
- **Rationale**: The entire device management interface (Tabs, List, and Edit Dialogs) SHALL be implemented using Jetpack Compose to ensure visual consistency and high performance. The interface SHALL include a modern Composable options menu for technical troubleshooting (e.g., ANT+ installation checks).
- **Description**: Composable Device Management.

### REQ-UI-044: Protocol-Aware Troubleshooting [Implemented]
- **Tests**: `TST-UI-062`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `DevicesTabbedScreen.kt`
- **Rationale**: Technical troubleshooting options (e.g., "Check ANT+ Installation") SHALL only be displayed when the relevant communication protocol is active in the current view.
- **Description**: Protocol-Aware Troubleshooting.

### REQ-UI-042: Mappable Device Items [Verified]
- **Tests**: `TST-UI-044`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `DeviceItem.kt`
- **Rationale**: Individual device items SHALL use the `MappableListItem` foundation. The layout SHALL be organized into a multi-row structure: Header Row (54dp/44dp icon next to technical status LED, Name and Value/Status Icon), Manufacturer Row, Technical Status Row (18dp battery icon, percentage, and connection state in brackets), Equipment Row, and Predicted Sport Type Row. Connected devices SHALL be immediately recognizable via a green status LED dot next to the device name. The Predicted Sport Type Row SHALL use technical subordination visuals (→, labelSmall, 0.7f alpha) to dynamically display the mapping between equipment and evaluation results. The pairing switch SHALL be positioned at the absolute bottom-right corner, using visual offsets and a 0.8f scale. Header icons SHALL use protocol-aware scaling (ANT+: 36dp, BLE: 42dp) to ensure visual parity.
- **Description**: Mappable Device Items.

### REQ-UI-043: Composable Edit & Selection Dialogs [Verified]
- **Tests**: `TST-UI-043`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `EditDeviceDialog.kt`, `DevicesTabbedScreen.kt`
- **Rationale**: Device configuration and type selection SHALL be handled via Composable dialogs. The selection interface SHALL include device-specific icons (32dp) and high-readability labels. The edit dialog SHALL feature a cohesive identity block for Sensor Name and Manufacturer. The equipment section SHALL include a reactive Predicted Sport Type Row (using →) to clarify the resulting sport evaluation in real-time.
- **Description**: Composable Edit & Selection Dialogs.

### REQ-UI-046: Unified Stats Summary Block [Verified]
- **Tests**: `TST-UI-046`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `StatsSummaryBlock.kt`
- **Rationale**: The `StatsSummaryBlock` SHALL display metrics using a vertical layout (heading above value) matching the visual identity of Workout, Segment, and Route items.
- **Description**: Unified Stats Summary Block.

### REQ-UI-047: Live Segment Sheet Height Constraint [Implemented]
- **Tests**: `TST-UI-047`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `MapDetailLayout.kt`, `LiveSegmentSheet.kt`
- **Rationale**: The Live Segment bottom sheet SHALL NOT expand to the top of the screen; its maximum height SHALL be limited to display the header and the elevation profile only.
- **Description**: Live Segment Sheet Height Constraint.

### REQ-UI-048: Clean Sensor Status Header [Verified]
- **Tests**: `TST-UI-048`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `SensorStatus.kt`
- **Rationale**: The `SensorStatus` header SHALL display only technical icons for active/available sensors. Icons SHALL use a balanced 22dp scale and SHALL NOT include descriptive text labels below them.

### REQ-UI-049: Sensor Source Dialog [Verified]
- **Tests**: `TST-UI-049`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `SensorStatus.kt`, `BANALServiceRepository.kt`, `SensorSourceDialog.kt`
- **Rationale**: Tapping a sensor icon in the `SensorStatus` header SHALL display a dialog showing current telemetry and the sensor redundancy chain grouped into: Source (active), Active Backups, and Not Connected. Values SHALL include localized units. Tapping a device within this dialog SHALL open the `EditDeviceDialog` for that specific hardware.
- **Description**: Sensor Source Dialog.

### REQ-UI-050: Optimized Sensor Identity Layout [Verified]
- **Tests**: `TST-UI-050`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `EditDeviceDialog.kt`, `SensorSourceDialog.kt`, `DeviceItem.kt`
- **Rationale**: The `EditDeviceDialog` SHALL use a 3-row identification block (including Manufacturer). The `SensorSourceDialog` SHALL use a high-density 2-row block (Icon, Name/LED, Battery/Status) without Manufacturer to maximize clarity during session audits. The technical status LED SHALL be vertically aligned with the battery icon by sharing a common 18dp horizontal center axis.
- **Description**: Optimized Sensor Identity Layout.

### REQ-UI-051: Color-Coded Sensor Source Headers & Semantic Color Token Decoupling [Verified]
- **Tests**: `TST-UI-052`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `SensorSourceDialog.kt`, `Color.kt`
- **Rationale**: The `SensorSourceDialog` SHALL use dedicated semantic color tokens from `TTColor` to color-code section headers and clearly distinguish sensor availability roles without coupling to map route tokens (ATT-4374):
1. *Dedicated Semantic Color Tokens (`Color.kt`)*:
• `TTColor.SensorSourceActive`: Darker green (`Color(0xFF2E7D32)`, Material Green 800) SHALL be used for the primary active "Source Device" ("Quelle" / `R.string.source_device`) section header, achieving $\ge 3.0:1$ WCAG AA bold contrast across Light (`#FFFFFF`), Dark (`#16161A`), and AMOLED (`#000000`) surfaces.
• `TTColor.SensorBackupActive`: Lighter green (`Color(0xFF43A047)`, Material Green 600) SHALL be used for the standby "Active Backups" ("Aktive Backups" / `R.string.source_active_backups`) section header, providing clear visual differentiation from the primary source while achieving $\ge 3.0:1$ WCAG AA bold contrast on Light surfaces ($3.30:1$) and $\ge 5.4:1$ on Dark surfaces.
• "Not Connected Devices" ("Nicht verbunden" / `R.string.source_not_connected`) header SHALL continue to render in neutral grey using `MaterialTheme.colorScheme.onSurfaceVariant`.
2. *Decoupling from Route Domain (`SensorSourceDialog.kt`)*:
• The section headers in `SensorSourceDialog.kt` SHALL NOT reference route visualization tokens `TTColor.RouteSelected` or `TTColor.RouteUnselected`.
• The primary source header SHALL set `color = TTColor.SensorSourceActive`.
• The active backups header SHALL set `color = TTColor.SensorBackupActive`.
3. *Route Domain Immutability*:
• Route visualization tokens `TTColor.RouteSelected` (`Color(0xFF1565C0)`), `TTColor.RouteUnselected` (`Color(0xFF90CAF9)`), and map route renderers SHALL remain completely unmodified, preserving `REQ-UI-306` and `TST-UI-266`.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: `REQ-UI-051` (*Color-Coded Sensor Source Headers*), targeting `SensorSourceDialog.kt`, `Color.kt`, and `TST-UI-052`.
2. *Historical Origin & Commit Trace*: Introduced in Sprint `2026-40.1` (SCRUM-147, commit `aa0184f0`), centralized into `TTColor` in commit `9691da82` (SCRUM-150), and inadvertently turned blue in Sprint `2026-41.4` (ATT-2761, commit `a9364e84`) when map route tokens were transitioned to Royal Blue (`REQ-UI-306`).
3. *Root Reason for Existing Formulation*: In SCRUM-147, `RouteColorSelected` (`0xFF228B22`) and `RouteColorUnselected` (`0xFF90EE90`) were reused opportunistically because they were already defined in `Color.kt` as dark and light green constants. There was no architectural reason to couple sensor availability with map route navigation lines.
4. *Preservation of Core Invariants*: Decoupling these headers from route tokens restores the original green intent of `REQ-UI-051` without impacting route visualization. Map route tokens, polyline renderers, truthful connection status indicators (`REQ-UI-268`), and canonical priority sorting remain 100% intact.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an athlete in the tracking cockpit opening `SensorSourceDialog`,
• *When* the "Quelle" / "Source Device" header is displayed,
• *Then* it SHALL render with `TTColor.SensorSourceActive` (Material Green 800 `0xFF2E7D32`), achieving $\ge 3.0:1$ WCAG AA bold contrast on all theme surfaces.
• *Given* an athlete in the tracking cockpit opening `SensorSourceDialog` with active backup sensors,
• *When* the "Aktive Backups" header is displayed,
• *Then* it SHALL render with `TTColor.SensorBackupActive` (Material Green 600 `0xFF43A047`), visually distinct from the primary source header and achieving $\ge 3.0:1$ WCAG AA bold contrast.
• *Given* static code and contract test analysis,
• *When* inspecting `SensorSourceDialog.kt`,
• *Then* zero references to `TTColor.RouteSelected` or `TTColor.RouteUnselected` SHALL exist.
• *Given* the route visualization subsystem,
• *When* route polylines and route cards render,
• *Then* `TTColor.RouteSelected` and `TTColor.RouteUnselected` SHALL retain their Royal Blue values (`0xFF1565C0` and `0xFF90CAF9`) with zero behavioral or visual changes.

**Invariants**: Route color palette immutability, sensor connection state truthfulness (`REQ-UI-268`), and 100% clean-room test suite pass rate MUST NOT be broken.

### REQ-UI-052: Longest Workout Navigation [Verified]
- **Tests**: `TST-UI-053`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `PeriodSummaryCard.kt`, `WorkoutSummariesListFragment.kt`
- **Rationale**: Tapping the "Longest Workout" highlight in a Period Summary SHALL navigate to the filtered workout list and automatically scroll to the specific workout record.
- **Description**: Longest Workout Navigation.

### REQ-UI-053: Location Accuracy Auditing [Implemented]
- **Tests**: `TST-UI-054`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `SensorStatus.kt`, `SensorType.java`
- **Rationale**: The tracking header SHALL use `SensorType.ACCURACY` as the primary location indicator. Tapping this icon SHALL display the `SensorSourceDialog` focused on technical accuracy (in meters) to provide actionable data quality insights.
- **Description**: Location Accuracy Auditing.

### REQ-UI-054: Semantic Color Palette [Verified]
- **Tests**: `TST-UI-059`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `Color.kt`, `Theme.kt`
- **Rationale**: The system SHALL use semantic color tokens and standardized alpha constants (`TTAlpha`) instead of hardcoded hex values or magic numbers.
- **Description**: Semantic Color Palette.

### REQ-UI-055: Primary Metric Highlighting [Verified]
- **Tests**: `TST-UI-060`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `MetricItem.kt`, `WorkoutDetails.kt`
- **Rationale**: Primary performance data (Total Distance, Active Time) SHALL use the `primary` color to establish a clear visual hierarchy.
- **Description**: Primary Metric Highlighting.

### REQ-UI-056: Project-wide Theme Compliance [Verified]
- **Tests**: `TST-UI-061`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: TBD
- **Rationale**: All legacy UI components and fragments SHALL be migrated to use semantic `TTColor` and `TTAlpha` tokens to eliminate magic literals.
- **Description**: Project-wide Theme Compliance.

### REQ-UI-057: Global Icon Rounding [Implemented]
- **Tests**: `TST-UI-063`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `DeviceItem.kt`, `DevicesTabbedScreen.kt`, `RemoteDevices.kt`, `PairingButtons.kt`, `DeviceTypeSelectionDialog.kt`
- **Rationale**: The system SHALL programmatically apply a subtle 4dp rounded corner clip to all sensor and protocol icons to ensure visual consistency and compatibility with dark mode and non-white backgrounds.
- **Description**: Global Icon Rounding.

### REQ-UI-058: Conditional Elevation Profile [Verified]
- **Tests**: `TST-UI-066`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `MapDetailLayout.kt`
- **Rationale**: The `MapDetailLayout` SHALL support an explicit toggle to hide the elevation profile, allowing analytical screens (like Favorite Tracks) to focus solely on spatial data.

### REQ-UI-059: Robust & Asynchronous Workout Sharing [Implemented]
- **Tests**: `TST-UI-067`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `ShareUtils.kt`, `MapDetailLayout.kt`
- **Rationale**: The sharing system SHALL handle optional UI components (Header, Elevation Profile) gracefully. If a component is hidden or has zero size, it SHALL be omitted from the combined snapshot without causing a crash. Snapshots SHALL be generated asynchronously to prevent UI freezing, and the system SHALL provide visual feedback (e.g. loading indicator) during the process.

### REQ-UI-060: Safe Map Snapshot [Implemented]
- **Tests**: `TST-UI-069`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `ATrainingTrackerMap.kt`, `MapDetailLayout.kt`
- **Rationale**: The map snapshot mechanism SHALL handle null results from the Google Maps API gracefully and provide immediate feedback to the user via a notification (Toast) to prevent fatal crashes and ensure a professional user experience.

### REQ-UI-061: Unified Deletion UI & Universal Delete-Only Long-Press Contract [Verified]
- **Tests**: `TST-UI-070`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `MappableListItem.kt`, `KnownLocationsScreen.kt`, `RouteItem.kt`, `EquipmentTabsScreen.kt`, `SportTypesTabsScreen.kt`, `WorkoutClusterComponents.kt`, `WorkoutSummaryCompact.kt`
- **Rationale**: The system SHALL enforce a standardized, predictable, and safe delete-only long-press context menu contract across all deletable list items in the application (ATT-238, ATT-1523):
1. *Universal Delete-Only Long-Press Contract*:
• All deletable list items across the entire application (Workouts, Routes, Clusters, Equipment, Sport Types, Favorite Locations / Lieblingsorte) SHALL strictly and exclusively present the deletion option in their standardized long-press context menu (`DropdownMenu`).
• The context menu SHALL be anchored consistently at the **Top-Left corner** (`Alignment.TopStart` with `start = 12.dp, top = 8.dp`) of the card, covering the header.
• Non-destructive secondary actions (such as "Edit", "Navigation", "Show on Map") are **strictly prohibited** from appearing in the long-press context menu.
• *Documented Lifecycle Exceptions*: Marking an unfinished workout as finished (`REQ-UI-046` in `WorkoutHeader.kt` / `WorkoutSummaryCompact.kt`) and saving a Strava route as a local route (`REQ-EXT-004` in `RouteItem.kt`) are preserved as documented lifecycle options.
2. *Lieblingsorte Card UI Harmonization (`KnownLocationsScreen.kt`)*:
• `KnownLocationCard` SHALL anchor its `DropdownMenu` strictly to `Alignment.TopStart` (Top-Left corner).
• The `DropdownMenu` SHALL contain strictly and exclusively the "Löschen" / "Delete" action (`R.string.delete`, `Icons.Default.Delete`).
• Secondary actions "Show on Map" (`R.string.known_location_show_map`) and "Edit" (`R.string.Edit`) SHALL be excised from the `KnownLocationCard` context menu.
• The redundant `IconButton(MoreVert)` overflow icon SHALL be excised from the `KnownLocationCard` header, allowing the location title to span the header cleanly and matching all other application cards (`RouteItem`, `EquipmentTabsScreen`, `WorkoutSummaryCompact`).
• Single-tap on `KnownLocationCard` (`onClick = onEdit`) SHALL continue to launch `EditKnownLocationDialog` (which includes embedded mini-map geofence visualization).
3. *Delete Confirmation Dialog Invariant*:
• Tapping "Delete" from any card's context menu MUST present `DeleteConfirmationDialog` using localized confirmation message (`@string/really_delete_format`) before deleting the item from SQLite.
4. *100% Localization Parity Across 9 Locales*:
• String resources `@string/delete` and `@string/really_delete_format` SHALL be verified with 100% parity across all 9 supported application locales (EN, DE, ES, FR, IT, JA, NL, PL, PT).

### REQ-UI-062: App Branding & Header Presentation [Implemented]
- **Tests**: `TST-UI-071`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Inspection / Review
- **Implementation**: `TrackingHeader.kt`, `strings.xml`
- **Rationale**: The system SHALL present consistent app branding and title hierarchy in the main tracking header, prioritizing active sport type and recording duration without visual distortion or branding competition.
- **Description**: App Branding & Header Presentation.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: `REQ-UI-061` (*Unified Deletion UI*), targeting `MappableListItem.kt` and all deletable list card composables (`KnownLocationsScreen.kt`, `RouteItem.kt`, `EquipmentTabsScreen.kt`, `SportTypesTabsScreen.kt`, `WorkoutClusterComponents.kt`, `WorkoutSummaryCompact.kt`), along with `REQ-UI-165`, `REQ-UI-166`, and `REQ-UI-180` in `docs/requirements.md`.
2. *Historical Origin & Commit Trace*: Commit `ceaf4070` (ATT-238 / ATT-1479), commit `48d0bdc8` (ATT-919, 2026-09-24), commit `b4ebca54` (ATT-1382, 2026-09-25), and commit `191ba05d` (ATT-1449 / ATT-1553, 2026-09-28).
3. *Root Reason for Existing Formulation*: `REQ-UI-061` originally established the Top-Left delete menu pattern to prevent accidental deletion and replace disparate swipe/delete buttons, but did not explicitly prohibit secondary actions. `REQ-UI-165`, `REQ-UI-166`, and `REQ-UI-180` predated full UI consolidation and carried legacy specifications for a 3-dots overflow menu containing "Edit" and "Show on Map".
4. *Preservation of Core Invariants*: Primary single-tap card interaction (`onClick = onEdit`) opening `EditKnownLocationDialog` remains 100% intact; central navigation map (`NavRoutes.MAP`) continues to render all favorite locations with peek sheets; SQLite schema V5, single-thread database serialization (`KnownLocationsDB-Thread`), and 9-language localization parity are strictly preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* the athlete viewing the "Lieblingsorte" list (`KnownLocationsScreen`),
• *When* the athlete long-presses any location card,
• *Then* a `DropdownMenu` SHALL appear anchored strictly at the **Top-Left** (`Alignment.TopStart`) corner of the card.
• *And* the menu SHALL contain strictly and exclusively the "Löschen" / "Delete" action (`R.string.delete`, `Icons.Default.Delete`).
• *And* the menu SHALL NOT contain "Show on Map" or "Edit".
• *Given* the athlete viewing the "Lieblingsorte" list,
• *When* inspecting `KnownLocationCard`,
• *Then* the card header SHALL NOT display any `MoreVert` / 3-dots overflow icon button.
• *Given* the athlete viewing the "Lieblingsorte" list,
• *When* the athlete taps any location card,
• *Then* `EditKnownLocationDialog` SHALL launch immediately to edit the location's name, altitude, and geofence radius.
• *Given* the automated test suite,
• *When* static AST / layout analysis runs across all list card components (`RouteItem.kt`, `EquipmentTabsScreen.kt`, `SportTypesTabsScreen.kt`, `WorkoutClusterComponents.kt`, `KnownLocationsScreen.kt`),
• *Then* all long-press context menus SHALL anchor to `TopStart` and contain strictly deletion (or documented lifecycle actions).

**Invariants**: Single-tap card editing, central map favorite locations layer, SQLite schema V5, `KnownLocationsDB-Thread` single-thread concurrency, and 9-language localization parity MUST NOT be broken.

### REQ-UI-101: Neutral Backgrounds [Implemented]
- **Tests**: `TST-MAN-001`, `TST-UI-072`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `Theme.kt`, `PeriodsTabsScreen.kt`, `WorkoutClustersTabsScreen.kt`
- **Rationale**: Maintain a clean, professional aesthetic and emphasize content over branding.
- **Description**: Neutral Backgrounds. The system SHALL use neutral white backgrounds (Light) or Material Surface (Dark) for all content areas, including progress notifications and migration status cards. Branding colors MUST be isolated strictly to navigation elements (e.g., TabRow backgrounds).

### REQ-UI-102: Bold Primary Metrics [Implemented]
- **Tests**: `TST-MAN-001`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `WorkoutSummary.kt`
- **Rationale**: The Workout Summary SHALL display primary metrics in boldface to emphasize key training volume data.

### REQ-UI-103: Customizable Cockpits [Implemented]
- **Tests**: `TST-MAN-001`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `ConfigTrackingTabsActivity.kt`
- **Rationale**: The system SHALL provide deeply customizable tracking cockpits, allowing users to configure their own data visualization dashboards.

### REQ-UI-104: Volume Bar Graph [Implemented]
- **Tests**: `TST-MAN-001`, `TST-UI-073`, `TST-UI-076`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `PeriodsTabsScreen.kt`, `PeriodBarGraph.kt`
- **Rationale**: The system SHALL feature a scrollable bar graph for period volume to enhance historical navigation and trend analysis. The graph SHALL remain synchronized with the current scroll position of the period list and MUST be consistently visible below the primary header tabs. During initial data loading or migration, the graph SHALL prioritize the display of the most recent periods to match the user's focus.

### REQ-UI-105: Edge-to-Edge Layout [Implemented]
- **Tests**: `TST-MAN-001`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `StravaUploadFragment.kt`
- **Rationale**: The system SHALL correctly handle system bar insets to provide a modern, immersive edge-to-edge Android experience.

### REQ-UI-106: Global Localization (Languages) [Verified]
- **Tests**: `TST-MAN-001`, `TST-STR-013`, `TST-STR-014`, `TST-STR-016`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `strings.xml`, `values-xx/`
- **Rationale**: The system SHALL support multiple languages (EN, DE, ES, FR, IT, PT, NL, PL, JA). All user-facing strings MUST be externalized in `strings.xml`.

### REQ-UI-122: Strict Format String Compliance [Verified]
- **Tests**: `TST-STR-016`, `TST-UI-394`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `strings.xml` in all locales
- **Rationale**: All localized string resources with placeholders MUST strictly follow the `java.util.Formatter` syntax. Positional specifiers MUST include both the index and the type suffix (e.g., `%1$s`, `%2$d`). Incomplete specifiers like `%1` are strictly prohibited to prevent runtime crashes.

### REQ-UI-113: String Resource Integrity [Verified]
- **Tests**: `TST-STR-013`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `strings.xml`, `values-*/strings.xml`
- **Rationale**: All string resources that are translated in a specific locale MUST also be present in the default `values/strings.xml` to ensure fallback consistency and prevent build warnings/runtime issues.

### REQ-UI-107: Ubiquitous Unit Support [Implemented]
- **Tests**: `TST-MAN-001`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `MyUnits.java`, `MyHelper.java`, `MetricFormatterContext.kt`
- **Rationale**: The system SHALL support both Metric and Imperial unit systems everywhere in the application. All user-facing interfaces (Tracking, Analysis, Management, Settings) MUST dynamically adapt to the user's unit preference for distance, speed, pace, and altitude.

### REQ-UI-108: Flexible sorting for lists [Implemented]
- **Tests**: `TST-MAN-001`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `WorkoutSummariesViewModel.kt`
- **Rationale**: Allow users to organize and find data easily based on Date, Distance, or Elevation.

### REQ-UI-109: Communication Clarification [Implemented]
- **Tests**: `TST-STR-001`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `project_protocol.md`
- **Rationale**: Agents SHALL ask for clarification if instructions are unclear to protect project quality and precision.

### REQ-UI-110: Optionally display the current track on a map and its elevation profile [Verified]
- **Tests**: `TST-UI-029`, `TST-INT-004`, `TST-STR-003`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `BANALServiceRepository.kt`, `SensorGridScreen.kt`
- **Rationale**: Provide real-time spatial and vertical situational awareness of the active session.
- **Description**: Optionally display the current track on a map and its elevation profile.

### REQ-UI-111: Adaptive layout for tab configuration settings [Implemented]
- **Tests**: `TST-INT-006`, `TST-INT-005`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `TrackingTabConfigHeader.kt`
- **Rationale**: Ensure all configuration toggles remain accessible and legible across different screen sizes using FlowRow, in the prioritized order: Map, Elevation Profile, Live Segments, Lap Button.
- **Description**: Adaptive layout for tab configuration settings.

### REQ-UI-112: Navigation Drawer Terminology [Implemented]
- **Tests**: `TST-NAV-002`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `main_navigation_drawer.xml`, `strings.xml`
- **Rationale**: The system SHALL use professional and standardized terminology for all navigation drawer labels across all supported languages. Specifically, the management hub SHALL be labeled \"Equipment\" (English) / \"Ausrüstung\" (German), the cloud integration section SHALL be labeled \"Synchronization\" (English) / \"Synchronisation\" (German), and the file export item SHALL be labeled \"Export\" (English / German).

### REQ-UI-114: Original Sport Icons [Implemented]
- **Tests**: `TST-INT-007`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `ActivityTypeSelectionDialog.kt`, `SportTypeListFragment.kt`
- **Rationale**: The system SHALL display sport type icons in their original colors to ensure quick identification and preserve visual identity. These icons SHALL NOT be subject to theme-based tinting unless they are explicitly in a \"muted\" or \"disabled\" state (e.g. background layers).

### REQ-UI-115: Concise Settings Terminology [Verified]
- **Tests**: `TST-UI-075`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `strings.xml`, `main_navigation_drawer.xml`
- **Rationale**: The system SHALL use concise and direct terminology for settings-related navigation items by omitting redundant qualifiers such as \"Settings\", \"Configuration\", \"Einstellungen\", or \"Konfiguration\".

### REQ-UI-116: Weekly Period Date Range [Verified]
- **Tests**: `TST-UI-074`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `PeriodsRepository.kt`, `PeriodSummaryCard.kt`
- **Rationale**: The system SHALL display the explicit start and end dates (e.g., \"May 11 - May 17\") below the title for weekly period summaries in the Periods screen to provide precise temporal context.

### REQ-UI-117: Adaptive Header Height [Implemented]
- **Tests**: `TST-BUG-006`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `WorkoutTabsScreen.kt`, `PeriodsTabsScreen.kt`, `WorkoutClustersTabsScreen.kt`, etc.
- **Rationale**: The system's collapsing headers (Workouts, Periods, Clusters, etc.) SHALL dynamically calculate their maximum height based on the device's actual status bar insets and the standard content height (Title + Tabs) to ensure consistent layout across all Android hardware (e.g., Samsung, Pixel).

### REQ-UI-118: Internal Sensor Transparency [Implemented]
- **Tests**: `TST-BUG-007`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `DeviceManager.java`, `AltitudeFromPressureDevice.java`
- **Rationale**: The `SensorSourceDialog` SHALL correctly display all active internal sensor sources (e.g., Pressure Sensor for Altitude) by ensuring they are included in the active device telemetry list and assigned unique negative identifiers.

### REQ-UI-119: Specialized Internal Sensor Icons [Implemented]
- **Tests**: `TST-BUG-010`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `DeviceData.kt`, `DeviceItem.kt`
- **Rationale**: The system SHALL display descriptive technical icons for internal smartphone sensors in the sensor management list (e.g., Altitude icon for the Pressure Sensor) to ensure professional identification and visual clarity.

### REQ-UI-120: Deterministic Period Visualization [Verified]
- **Tests**: `TST-PER-010`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `PeriodsTabsScreen.kt`, `PeriodMapScreen.kt`, `MyPreferenceManager.kt`
- **Rationale**: The system SHALL ALWAYS display heatmaps within the Periods module to provide a consistent analytical experience.

### REQ-UI-121: Consistent List Item Spacing [Verified]
- **Tests**: `TST-UI-077`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `WorkoutSummary.kt`, `RouteItem.kt`, `ClusterItem.kt`, `WorkoutHeader.kt`
- **Rationale**: The system SHALL use a standardized horizontal padding of 12dp and a consistent vertical spacing of 12dp between internal sections (Header, Description, Details, Extrema, Map) for all comprehensive list items (Workouts, Routes, Clusters).

### REQ-UI-123: Declarative Compose Navigation Drawer, High-Density Presentation, Theme-Aware Contrast Tinting & Defensive Resource Resolution [Verified]
- **Tests**: `TST-UI-078`, `TST-UI-135`, `TST-UI-140`, `TST-NAV-007`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `ImportBackupTabsScreen.kt`, `strings.xml`
- **Rationale**: The navigation drawer content SHALL be implemented in Jetpack Compose (`AppNavigationDrawer.kt`) hosted inside the existing Activity `DrawerLayout` via `ComposeView` (ATT-243, ATT-516, ATT-1547, ATT-1592):
1. *High-Density Presentation & Touch Ergonomics*: The drawer items SHALL feature a compact, high-density layout (~40dp item height with standard touch targets) to allow all core items to fit without excessive vertical scrolling.
2. *Reactive Tracking Status*: The dynamic tracking status ("Start Tracking", "Tracking", "Pause") SHALL be driven by reactive Compose state (`State<Int>`) rather than legacy menu mutations.
3. *Theme-Aware Icon Contrast Tinting*: All monochrome navigation drawer leading icons SHALL apply theme-aware contrast tinting matching row contentColor (`colorFilter = ColorFilter.tint(contentColor)`):
• When an item is selected, the icon SHALL be tinted with `colorResource(R.color.color_primary)`.
• When an item is unselected, the icon SHALL be tinted with `colorResource(R.color.color_on_surface)` (resolving to high-contrast white in Dark Mode and dark navy in Light Mode).
• Brand partner logos (`logo_square_strava`, `dropbox_logo_blue`) SHALL retain their authentic brand colors without tinting (`tintIcon = false`).
4. *Crash Prevention*: Legacy XML `NavigationView` inflation SHALL remain eliminated to prevent reflection-based `InflateException` crashes.
5. *Defensive Resource Resolution & Density Fallback Protection*: The drawer icon resolution in `DrawerItemView` SHALL handle `Resources.NotFoundException` and other resource resolution exceptions defensively, falling back gracefully to `null` (rendering the 24dp `Spacer` placeholder) without crashing the drawer or application (ATT-1592). In addition, partner brand assets (`logo_square_strava`, `dropbox_logo_blue`) SHALL provide an unqualified root fallback in `res/drawable/` to guarantee successful density resolution across split APKs and custom device DPI settings.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: `REQ-UI-123` (*Declarative Compose Navigation Drawer & High-Density Presentation*), targeting `AppNavigationDrawer.kt` and `MainActivityWithNavigation.kt`.
2. *Historical Origin & Commit Trace*: Introduced in `ATT-243` / `ATT-516` (commit `69cf7240`) to replace legacy Android View `NavigationView` with a pure Jetpack Compose drawer that eliminated reflection-based `InflateException` crashes and compressed vertical row spacing (40dp item height). Enhanced in `ATT-1547` (commit `4d528cb8`) for theme-aware icon contrast tinting, and in `ATT-1592` for defensive resource resolution.
3. *Root Reason for Existing Formulation*: The original architectural focus was eliminating View inflation crashes, ensuring touch target ergonomics, and managing dynamic reactive tracking state (`startTrackingTitleRes`). The row text color was assigned dynamically (`contentColor`), and the author provided an `if (drawable != null)` branch, expecting `ContextCompat.getDrawable` to return `null` on failure. The possibility of `Resources.NotFoundException` during density resolution was overlooked.
4. *Preservation of Core Invariants*: Compact layout density (~40dp height), item touch targets, selected background highlight (`color_primary` with 0.12 alpha), reactive tracking status state, back navigation hierarchy, theme-aware contrast tinting, and partner brand logos (`logo_square_strava`, `dropbox_logo_blue` retaining authentic brand colors) are strictly preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* the device configured in Dark Mode (or AMOLED dark theme),
• *When* the navigation drawer (`AppNavigationDrawer`) is opened,
• *Then* all monochrome destination icons SHALL render with high-contrast white tinting matching their adjacent text labels.
• *Given* any selected destination in the navigation drawer,
• *When* the drawer row is rendered,
• *Then* both the leading icon and text label SHALL render tinted with `color_primary`.
• *Given* the Online Communities drawer group,
• *When* Strava and Dropbox rows are rendered,
• *Then* their icons SHALL render with their authentic brand colors without monochrome tinting.
• *Given* the device configured in Light Mode,
• *When* the navigation drawer is opened,
• *Then* monochrome destination icons SHALL render with `color_on_surface` (dark navy `#000080`), maintaining high contrast against the white surface.
• *Given* an item in the navigation drawer with an invalid, corrupted, or unresolvable drawable resource ID (or a missing density asset),
• *When* the navigation drawer is composed,
• *Then* `DrawerItemView` SHALL catch the resource resolution exception, log a warning, and render a 24dp spacer without crashing the application.

**Invariants**: Navigation drawer routing, `NavRoutes` resolution, 40dp compact item density, 24dp icon footprint, and 100% 9-language localization parity MUST NOT be broken.

### REQ-UI-124: Kotlin Modernization of Navigation Activity (`MainActivityWithNavigation`) [Verified]
- **Tests**: `TST-NAV-008`, `TST-STB-017`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `MainActivityWithNavigation.kt`, `AndroidManifest.xml`
- **Rationale**: The application's primary navigation host activity SHALL be migrated from Java to idiomatic Kotlin (`MainActivityWithNavigation.kt`). The Kotlin implementation MUST maintain 100% functional and binary interoperability:
1. *Interface Compliance*: The activity MUST implement `BANALService.GetBanalServiceInterface`, `PreferenceFragmentCompat.OnPreferenceStartScreenCallback`, and `StartOrResumeInterface`.
2. *Companion Object & Java Interop*: Constants `SELECTED_FRAGMENT_ID`, `SELECTED_FRAGMENT`, and `EXTRA_RESUME_INTERRUPTED_WORKOUT` MUST be exposed as `@JvmField` in a companion object to ensure zero disruption to Java callers (`TrackerService`, dialogs).
3. *Navigation & Routing Integrity*: The activity MUST retain identical routing in `navigateToDrawerItem(itemId)` across all 21 items, including modal settings dialog dispatch (`ActivityTypeSelectionHelper`, `DisplaySettingsDialogFragment`, `UnitsSettingsDialogFragment`, `ExportSettingsDialogFragment`) and fragment transactions.
4. *Lifecycle & Service Binding*: The activity MUST manage broadcast receivers (`mStartTrackingReceiver`, `mPauseTrackingReceiver`, `mStopTrackingReceiver`, `mTrackingStoppedReceiver`, `mAntDependencyReceiver`) in `onResume()` / `onPause()`, BANALService binding with delayed disconnect, and window insets dispatch to `compose_nav_view`.
5. *Back Gesture Invariant*: Back gesture when the drawer is open MUST dismiss the drawer without closing the activity.
**Invariants**: Manifest declaration, launch mode (`singleTask`), theme (`Theme.aTrainingTracker`), and fragment state restoration contracts MUST remain identical.

### REQ-UI-125: Period Summaries Empty State Presentation [Verified]
- **Tests**: `TST-PER-017`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `PeriodList.kt`, `strings.xml` (all 9 locales)
- **Rationale**: The period summaries screen (`PeriodList.kt` / `PeriodsTabsScreen.kt`) SHALL display an informative empty state presentation whenever no period summary records exist (`periods.isEmpty()`):
1. *Visual Hierarchy & Component Reuse*: The system SHALL present an `EmptyStatePlaceholder` component (standardized with `WorkoutList`, `RouteList`, `SegmentList`, and `WorkoutClustersList`) featuring a calendar/history icon (`Icons.Default.DateRange`), a localized headline message (`no_periods_available`), and a supportive description guiding the user.
2. *Layout Insets & Alignment*: The empty state placeholder SHALL be vertically centered within the content viewport below the collapsible header, properly accounting for top collapsing header offset padding and bottom system navigation bar insets.
3. *Localization Parity*: All user-facing strings SHALL be externalized and translated across all 9 supported application locales (EN, DE, ES, FR, IT, JA, NL, PL, PT).
**Acceptance Criteria**:
• *Given* an empty period list (`periods.isEmpty()`) for any temporal tab (Week, Month, Year) or sport filter,
• *When* the user views the Periods screen,
• *Then* the system SHALL render the `EmptyStatePlaceholder` with the localized message and icon instead of an uninformative blank screen.
• *Given* a period list containing one or more periods,
• *When* the user views the Periods screen,
• *Then* the system SHALL render the period summary items and bar graph normally.
**Invariants**: Period bar graph, temporal tab switching, collapsing header behavior, sport filtering, and period card layout for non-empty lists MUST NOT be altered.

### REQ-UI-126: Elevation Profile Chart Bounds Sanitization, Containment & Plausibility Protection [Verified]
- **Tests**: `TST-UI-079`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `ElevationProfile.kt`
- **Rationale**: The `ElevationProfile` component SHALL sanitize effective vertical bounds (`min` and `max`) against recorded stream points (`pathPointsDownsampled`) such that the rendered vertical scale strictly envelopes all rendered and smoothed points without vertical clipping or cropping.
1. *Strict Containment Invariant*: The effective minimum bound `min` SHALL NEVER be greater than `streamMin` (even if an override is provided), and the effective maximum bound `max` SHALL NEVER be less than `streamMax`. If `minAltitudeOverride > streamMin`, it SHALL be clamped down to `streamMin`. If `maxAltitudeOverride < streamMax`, it SHALL be clamped up to `streamMax`.
2. *Outlier Clamping*: If `minAltitudeOverride < streamMin - outlierToleranceMeters`, it SHALL be clamped to `streamMin`. If `maxAltitudeOverride > streamMax + outlierToleranceMeters`, it SHALL be clamped to `streamMax`.
3. *Aesthetic Minimum Span Preservation*: When expanding a narrow elevation profile to `minSpanMeters` (20.0m) centered around the midpoint, `expandedMin` SHALL NOT exceed `streamMin` and `expandedMax` SHALL NOT be less than `streamMax`.
**Acceptance Criteria**:
• *Given* an elevation profile with points dipping to 360 m and an override of 364 m or 368 m,
• *When* `ElevationProfile` renders the chart,
• *Then* `bounds.min` SHALL be <= 360 m, and NO point on the profile line SHALL extend below the bottom axis or bound.
• *Given* a flat sea-level route (0 m to 3 m) with corrupted overrides (-63 m, +60 m),
• *Then* bounds SHALL clamp to [-8.5 m, 11.5 m] (20 m span) enveloping all points.
**Invariants**: Visual grade legend (`REQ-MAP-010`) and distance step calculation MUST NOT be altered.

### REQ-UI-127: Delete Old Workouts Compose Dialog & Action Trigger [Verified]
- **Tests**: `TST-UI-080`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `WorkoutListActions.kt`, `WorkoutTabsScreen.kt`, `DeleteOldWorkoutsDialog.kt`, `WorkoutSummariesViewModel.kt`, `strings.xml`
- **Rationale**: The `WorkoutTabsScreen` and `WorkoutListActions` components SHALL provide a dedicated action button and dialog for triggering bulk deletion of old workouts:
1. *Action Trigger*: An action button featuring icon `ic_baseline_delete_sweep_24` and localized content description (`@string/deleteOldWorkouts`) SHALL be displayed in `WorkoutListActions` in the workouts header.
2. *Dialog Interface*: Tapping the action button SHALL display `DeleteOldWorkoutsDialog` containing:
• Localized headline: `@string/deleteOldWorkouts` ("Delete old workouts").
• Localized explanatory prompt: `@string/deleteWorkoutsThatAreOlderThanDays` ("delete all workouts that are older than … days").
• Numeric input field (`OutlinedTextField`) defaulting to 365 days (driven by `@string/defaultDaysToKeep`), configured with `KeyboardType.Number`.
• *Input Validation*: The confirm button SHALL be disabled unless the input is a valid non-negative integer ($D \ge 0$).
• *Dismissal & Confirmation*: Tapping dismiss ("Cancel") SHALL close the dialog without action; tapping confirm ("Delete" / "OK") SHALL close the dialog and dispatch `executeDeleteOldWorkouts(daysToKeep)` to the ViewModel.
**Acceptance Criteria**:
• *Given* the user is on the Workouts tabbed screen,
• *When* the user taps the delete sweep action button,
• *Then* the dialog SHALL appear with default value 365.
• *Given* an invalid input (e.g. negative number, empty text, or non-numeric),
• *Then* the confirm button SHALL be disabled.
• *Given* a valid number (e.g. 180) and tapping confirm,
• *Then* `executeDeleteOldWorkouts(180)` SHALL be invoked and the dialog dismissed.
**Invariants**: Header title row height (`LayoutConstants.HEADER_TITLE_ROW_HEIGHT`) and existing actions (compact view toggle, sort menu) MUST remain responsive and unobstructed.

### REQ-UI-128: Bulk Workout Deletion Progress Feedback & Notification [Verified]
- **Tests**: `TST-UI-081`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `WorkoutDeletionProgressDialog.kt`, `WorkoutTabsScreen.kt`, `WorkoutSummariesTabbedFragment.kt`, `WorkoutSummariesViewModel.kt`, `WorkoutRepository.kt`, `WorkoutDeletionHelper.java`
- **Rationale**: While bulk-deleting workouts older than $D$ days, the system SHALL provide continuous, observable progress feedback both within the user interface and via system notification:
1. *In-App Progress Dialog*: The system SHALL present a non-dismissible modal Compose dialog (`WorkoutDeletionProgressDialog`) upon initiating bulk deletion, preventing conflicting user interactions during database mutations and cache resynchronization.
2. *Granular Progress Reporting*: During deletion (Phase 1), the dialog and notification SHALL display the localized deletion title (`@string/deleteOldWorkouts`), the specific workout session currently being deleted (`@string/deleting_workout`), a numeric counter ($X / Y$), and a linear progress bar indicating fractional completion ($X / Y$).
3. *Downstream Finalization Indicator*: During analytical cache resynchronization and cluster integrity verification (Phase 2), the dialog SHALL display a progress indicator (circular spinner) and status message (`@string/please_wait`) until all background database operations conclude.
4. *Foreground / System Notification*: The system SHALL post an ongoing notification (`NotificationCompat.Builder`) with `CATEGORY_PROGRESS` and `setOngoing(true)` to keep the user informed if the application is backgrounded during deletion. The notification SHALL be dismissed immediately upon completion.
5. *Lifecycle & State Safety*: In both normal completion and error scenarios, the system MUST guarantee reset of deletion state to `Idle`, ensuring the user interface is never locked in an unrecoverable modal state.
**Acceptance Criteria**:
• *Given* the user confirms deletion of old workouts in `DeleteOldWorkoutsDialog`,
• *When* deletion is executing,
• *Then* `WorkoutTabsScreen` SHALL display the modal progress dialog showing the current workout name, progress $X / Y$, and a progress bar.
• *Given* deletion of records finishes and cache resync begins,
• *Then* the progress dialog SHALL transition to the finalization phase showing the spinner and wait message.
• *Given* all operations complete,
• *Then* the progress dialog and system notification SHALL automatically dismiss and the list SHALL refresh.
**Invariants**: Deletion order and period/cluster cache invariants (`REQ-DAT-010`, `REQ-DAT-011`) MUST NOT be altered; non-bulk deletion MUST NOT display bulk progress dialog.

### REQ-UI-129: Bulk Workout Deletion Confirmation Dialog [Verified]
- **Tests**: `TST-UI-082`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `WorkoutTabsScreen.kt`, `DeleteOldWorkoutsDialog.kt`, `DeleteConfirmationDialog.kt`, `strings.xml` (all 9 locales)
- **Rationale**: Prior to executing bulk deletion of old workouts, the system SHALL require explicit user confirmation to prevent accidental and irreversible data loss:
1. *Two-Step Deletion Flow*: Upon the user specifying a valid retention threshold ($D \ge 0$ days) in `DeleteOldWorkoutsDialog` and clicking the proceed action, the system SHALL NOT initiate deletion immediately. Instead, the system SHALL display an explicit confirmation dialog (`DeleteConfirmationDialog`).
2. *Confirmation Dialog Content*: The confirmation dialog SHALL present:
• Localized title: `@string/deleteOldWorkouts` ("delete old workouts").
• Localized warning prompt explicitly stating the retention threshold: `@string/really_delete_old_workouts_format` ("Do you really want to delete all workouts older than %1$d days?").
• Confirm action button: `@string/delete` ("delete").
• Dismiss action button: `@string/Cancel` ("Cancel").
3. *Cancellation Safety*: If the user dismisses the confirmation dialog (tapping "Cancel", tapping outside, or pressing the system back gesture), the deletion flow MUST terminate without modifying the database, and zero workouts SHALL be deleted.
4. *Confirmed Execution*: Only when the user explicitly clicks the confirmation button SHALL `onDeleteOldWorkouts(daysToKeep)` be dispatched, triggering the progress notification and deletion process (`REQ-UI-128`, `REQ-DAT-010`).
5. *Localization Parity*: All user-facing strings for the confirmation dialog SHALL be localized across all 9 supported application locales (EN, DE, ES, FR, IT, JA, NL, PL, PT).
**Acceptance Criteria**:
• *Given* the user is on the Workouts tabbed screen and opens `DeleteOldWorkoutsDialog`,
• *When* the user specifies a retention threshold (e.g. 180 days) and taps the proceed button,
• *Then* the retention input dialog SHALL close and the confirmation dialog SHALL appear displaying the specified threshold (180 days).
• *Given* the confirmation dialog is visible,
• *When* the user taps "Cancel",
• *Then* the dialog SHALL dismiss, and zero database deletion operations SHALL be executed.
• *Given* the confirmation dialog is visible,
• *When* the user taps "Delete",
• *Then* `executeDeleteOldWorkouts(180)` SHALL be dispatched and the progress dialog (`REQ-UI-128`) SHALL appear.
**Invariants**: Single workout deletion (`WorkoutDeleteDialog`) MUST remain unaltered; retention threshold validation ($D \ge 0$) from `REQ-UI-127` MUST remain enforced; no background deletion SHALL start before explicit confirmation.

### REQ-UI-130: Sport-Specific Equipment Selection & In-Place Expansion in Edit Workout Dialog [Verified]
- **Tests**: `TST-UI-083`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `EditWorkoutViewModel.kt`, `EditWorkoutScreen.kt`
- **Rationale**: Within the workout editing dialog (`EditWorkoutScreen` / `EditWorkoutViewModel`), the system SHALL strictly partition available equipment options by the active workout's base sport type (`currentBSportType`):
1. *Base Sport Type Partitioning (`BSportType` Firewall)*:
• For `BSportType.BIKE`: Available equipment SHALL be constrained strictly to bicycles (`frameType` $\in 1..4$, `sportType == BIKE`). Running shoes and other incompatible equipment MUST NOT be included.
• For `BSportType.RUN`: Available equipment SHALL be constrained strictly to running shoes (`frameType == 0`, `sportType == RUN`). Bicycles and other incompatible equipment MUST NOT be included.
• For `BSportType.UNKNOWN` / `OTHER`: The equipment dropdown SHALL display only `- none -` (plus any explicitly linked equipment configured for that specific sport).
2. *Three-Case Hierarchy Based on Linked Equipment Count ($N$)*:
• **Case 1 ($N > 1$: Multiple equipment items linked to the sport)**: The dropdown options list SHALL present: `- none -`, the linked equipment items ($L$), and the expansion item (`+ all bikes +` for cycling, `+ all shoes +` for running). Selecting the expansion item SHALL expand the dropdown options list in-place (via `stayOpenOn`) to display `- none -` and all equipment belonging to that base sport ($A$).
• **Case 2 ($N == 1$: Exactly one equipment item linked to the sport)**: The dropdown options list SHALL present: `- none -` and all equipment of that base sport ($A$). The uniquely linked equipment item SHALL be automatically preselected.
• **Case 3 ($N == 0$: No equipment items linked to the sport)**: The dropdown options list SHALL present: `- none -` and all equipment of that base sport ($A$). `- none -` SHALL be preselected (unless the workout already has an assigned equipment item that is valid for this sport).
3. *Cross-Sport Switching Invariants*:
• When the user changes the sport type to a different `BSportType` (e.g. Cycling $\rightarrow$ Running): Any previously selected equipment item SHALL be cleared (since bicycles cannot be assigned to running workouts). If the new sport has $N == 1$ linked equipment, that linked item SHALL be preselected; if $N == 0$, `- none -` SHALL be preselected.
• When the user changes the sport type within the same `BSportType` (e.g. Road Cycling $\rightarrow$ Mountain Biking): If the new sport has $N == 1$ linked equipment, that linked equipment SHALL be preselected; otherwise, if the current equipment is valid for the base sport, it SHALL be retained.
4. *Cross-Sport Backdoor Elimination*:
• The generic token `+ all equipment +` and unfiltered fallback to `equipmentRepository.equipmentList` SHALL be eliminated from `EditWorkoutViewModel`, preventing cross-sport gear pollution.
**Acceptance Criteria**:
• *Given* a cycling workout with 2 linked bikes (Bike A, Bike B) and 1 unlinked bike (Bike C),
• *When* the equipment dropdown is opened,
• *Then* options SHALL be `[- none -, Bike A, Bike B, + all bikes +]`.
• *When* `+ all bikes +` is tapped,
• *Then* options SHALL expand in-place to `[- none -, Bike A, Bike B, Bike C]`, with zero running shoes displayed.
• *Given* a running workout with 1 linked shoe (Shoe 1) and 2 unlinked shoes (Shoe 2, Shoe 3),
• *When* the dialog opens,
• *Then* options SHALL be `[- none -, Shoe 1, Shoe 2, Shoe 3]` and `Shoe 1` SHALL be preselected.
• *Given* a cycling workout with 0 linked bikes,
• *When* the dialog opens for a new/unassigned workout,
• *Then* options SHALL be `[- none -, <all bikes>]` and `- none -` SHALL be preselected.
• *Given* an active workout with Bike A selected,
• *When* the user changes the sport to Running,
• *Then* the selected equipment SHALL NOT remain Bike A, but SHALL switch to the linked shoe ($N == 1$) or `- none -` ($N == 0$).
**Invariants**: `SportTypeEquipmentLinkManager` and `EquipmentDbHelper` data persistence MUST NOT be altered; existing valid equipment assignments on workouts matching their sport MUST NOT be overwritten without user action.

### REQ-UI-131: Compact Strava Segment Efforts Presentation with Expandable Accordion in Workout Summary [Verified]
- **Tests**: `TST-UI-084`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `StravaActivitySection.kt`, `StravaActivityData.kt`, `strings.xml` (all 9 locales)
- **Rationale**: Within the Strava activity results section of the workout summary card (`StravaActivitySection.kt`), the system SHALL provide a smart expandable presentation for segment efforts to prevent summary card bloat while preserving full access to all segment details:
1. *Segment Effort Classification*:
A segment effort is classified as *Highlight* (important) if:
• It represents a Personal Record (`prRank != null`, where `prRank` $\in \{1, 2, 3\}$, parsed directly or from `achievements`), OR
• It represents a Course Record / Overall Rank (`komRank != null`, parsed directly or from `achievements`), OR
• The segment is starred by the user on Strava (`isStarred == true`, `starred_date` present, or locally synchronized in `SegmentsDatabaseManager` by ID or name).
All other segment efforts are classified as *Standard*.
2. *Collapse Eligibility Threshold ($K = 4$)*:
The expandable accordion SHALL be activated for a workout if and only if:
• Total segment efforts count exceeds threshold $K$ ($N > 4$), AND
• Total segment efforts count exceeds the count of highlighted segments ($N > N_{\text{highlight}}$).
If $N \le 4$ OR if all segments are highlighted ($N == N_{\text{highlight}}$), all segment efforts SHALL be displayed directly without expansion controls.
3. *Default Compact View*:
When eligible for collapse, the section SHALL default to collapsed state (`isExpanded == false`), displaying:
• If $N_{\text{highlight}} > 0$: Strictly the highlighted segment efforts (PRs, KOMs, Starred).
• If $N_{\text{highlight}} == 0$: A localized note indicating no starred segments or PRs (`@string/strava_no_highlights`) with zero segment rows.
• Directly below the displayed segments, an expansion action button SHALL be rendered: e.g. `"▼ Show all %1$d segments (+%2$d more)"` (`@string/strava_show_all_segments_format`), indicating total count ($N$) and hidden count ($N - N_{\text{highlight}}$).
4. *In-Place Expansion & Collapse Toggle*:
• Tapping the expansion action SHALL immediately expand the list in-place to display all $N$ segment efforts without navigating away or closing the card.
• While expanded (`isExpanded == true`), the control button SHALL toggle to a collapse action: e.g. `"▲ Show only starred & PRs (%1$d)"` (`@string/strava_show_less_segments_format`), displaying $N_{\text{highlight}}$.
• Tapping the collapse action SHALL collapse the list back to the highlighted segments.
• Expansion state SHALL be preserved during lazy scrolling via `rememberSaveable`.
5. *Localization Parity (9 Locales)*:
All user-facing action labels and formatting strings SHALL be localized across all 9 supported application locales (EN, DE, ES, FR, IT, JA, NL, PL, PT).
**Acceptance Criteria**:
• *Given* a workout with 15 segment efforts where 3 are PRs and 1 is starred (4 highlights, 11 standard),
• *When* the workout summary is rendered,
• *Then* exactly 4 highlighted segment efforts SHALL be visible, followed by an expansion button `"▼ Show all 15 segments (+11 more)"`.
• *When* the user taps the expansion button,
• *Then* all 15 segment efforts SHALL be displayed, and the button label SHALL become `"▲ Show only starred & PRs (4)"`.
• *When* the user taps the collapse button,
• *Then* the list SHALL collapse back to 4 segments.
• *Given* a workout with 7 segment efforts where 0 are PRs or starred,
• *When* the workout summary is rendered in collapsed view,
• *Then* zero segment rows and `@string/strava_no_highlights` SHALL be visible, followed by `"▼ Show all 7 segments (+7 more)"`.
• *Given* a workout with 3 segment efforts,
• *When* the summary is rendered,
• *Then* all 3 segments SHALL be displayed directly with NO expansion button.
• *Given* a workout with 8 segment efforts where all 8 are PRs or starred,
• *When* the summary is rendered,
• *Then* all 8 segments SHALL be displayed directly with NO expansion button.
**Invariants**: Best Efforts section, external Strava activity click launch (`StravaHelper.openActivity`), and Strava branding requirements (`PoweredByStrava`) MUST remain fully intact.

### REQ-UI-132: Multi-Dimensional Workout Filtering, Modal Filter Sheet & Persistent Filter State [Verified]
- **Tests**: `TST-UI-085`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `WorkoutFilterCriteria.kt`, `WorkoutListActions.kt`, `WorkoutTabsScreen.kt`, `WorkoutFilterBottomSheet.kt`, `ActiveFilterChipsRow.kt`, `WorkoutSummariesViewModel.kt`, `MyPreferenceManager.kt`, `strings.xml` (all 9 locales)
- **Rationale**: The system SHALL provide multi-dimensional filtering for workout sessions on the tabbed workout list (`WorkoutTabsScreen`):
1. *Action Trigger & Active Indicator*: The header action row (`WorkoutListActions`) SHALL display a dedicated filter action button with the standard Material funnel icon (`Icons.Default.FilterAlt`) and localized content description. When one or more filter criteria are active (`isFilterActive == true`), the action icon SHALL display a visual badge indicating the active filter count.
2. *Filter Dimension Set*: The system SHALL support filtering across orthogonal dimensions encapsulated in immutable `WorkoutFilterCriteria`:
• *Text Search*: Case-insensitive substring matching against workout title, description, and notes.
• *Date & Time Period*: Year selection, Month, and/or Custom Date Range (`startDateS` to `endDateS`).
• *Sport Sub-Type*: Specific sport ID (`sportTypeId`).
• *Equipment / Gear*: Specific equipment ID (`equipmentId`).
• *Workout Flags & Attributes*: Toggle filters for Commute (`commute == true`), Trainer / Indoor (`trainer == true`), Has GPS Track (`mapPolyline.isNotEmpty()`), and Cluster ID (`clusterId`).
• *Distance & Duration Thresholds*: Minimum distance threshold ($D \ge D_{\text{min}}$ in meters) and/or minimum active duration threshold ($T \ge T_{\text{min}}$ in seconds).
3. *Modal Filter Bottom Sheet (`WorkoutFilterBottomSheet`)*: Tapping the filter action button SHALL present a modal bottom sheet containing organized controls for each filter dimension. The bottom sheet SHALL be properly anchored to the bottom edge without bottom gap artifacts when dragged. The sheet SHALL feature:
• A dedicated top header row with localized title and close dismiss button (`Icons.Default.Close`).
• A dedicated bottom action row providing distinct "Reset all" (clear) and "Apply" / "OK" action buttons with prominent touch targets.
4. *Removable Active Filter Chip Strip*: When one or more filter criteria are active, the system SHALL render a horizontal scrolling chip strip directly above the workout list (below the sport tabs). Each active dimension SHALL render a distinct chip with localized label and a trailing remove icon (`Icons.Default.Close`). Tapping the remove icon SHALL clear that specific filter dimension reactively without requiring the bottom sheet to be opened.
5. *Filter Lifecycle & Main View Reset*: Filter criteria SHALL remain active while navigating within the workouts section (switching tabs, editing workouts, inspecting track on map), but SHALL be automatically cleared when navigating back to the main view (or exiting the workouts section), ensuring subsequent visits start with the full unfiltered workout list.
6. *Reactive List Pipeline & Tab Interoperability*: `WorkoutSummariesViewModel` SHALL combine `allWorkouts`, `sortOrder`, and `filterCriteria` into the reactive `workouts` state flow. The top-level tabs (*All*, *Bike*, *Run*, *Other*) SHALL continue to partition workouts by `BSportType` while strictly respecting all active sub-filters.
7. *Localization Parity (9 Locales)*: All filter category titles, action buttons, chip descriptors, and empty-state filter prompts SHALL be localized across all 9 supported application locales (EN, DE, ES, FR, IT, JA, NL, PL, PT).
**Acceptance Criteria**:
• *Given* the workout list header,
• *Then* the filter action SHALL display the funnel icon (`Icons.Default.FilterAlt`).
• *When* the user opens `WorkoutFilterBottomSheet`,
• *Then* the sheet SHALL anchor flush to the bottom edge without gaps, the top header SHALL display the title and a close button, and a separate bottom row SHALL display "Reset all" and "Apply".
• *Given* active filters applied to the workout list,
• *When* the user navigates back to the main view (e.g. Start Tracking),
• *Then* all active filters SHALL be cleared, and re-entering the workouts section SHALL show all workouts.
**Invariants**: Sorting orders (`WorkoutSortOrder.DATE`, `WORKOUT_DURATION`, `WORKOUT_DISTANCE`, `TOTAL_ELEVATION_GAIN`), compact/detailed view mode toggle, individual and bulk workout deletion workflows, and SQLite database schema MUST NOT be altered or regressed.

### REQ-UI-133: Multi-Dimensional Route Filtering, Shared Modal Filter Sheet & Persistent Filter State [Verified]
- **Tests**: `TST-UI-086`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `RouteFilterCriteria.kt`, `FilterBottomSheetScaffold.kt`, `FilterActionButton.kt`, `RouteTabbedScreen.kt`, `RouteFilterBottomSheet.kt`, `ActiveRouteFilterChipsRow.kt`, `RoutesViewModel.kt`, `MyPreferenceManager.kt`, `strings.xml` (all 9 locales)
- **Rationale**: The system SHALL provide multi-dimensional filtering for routes on the tabbed route list (`RouteTabbedScreen`):
1. *Action Trigger & Active Indicator*: The header action row (`RouteTabbedScreen`) SHALL display a dedicated filter action button positioned consistently at the end of the action row (`Select All` $\rightarrow$ `Import` $\rightarrow$ `Sync` $\rightarrow$ `Sort` $\rightarrow$ `Filter`) featuring the Material funnel icon (`Icons.Default.FilterAlt`) and localized content description. When one or more filter criteria are active (`isFilterActive == true`), the filter action button SHALL display a visual badge indicating the active filter count.
2. *Filter Dimension Set*: The system SHALL support filtering across orthogonal route dimensions encapsulated in immutable `RouteFilterCriteria`:
• *Text Search*: Case-insensitive substring matching against route `name` and `description`.
• *Route Source*: Specific route origin (`RouteSource.STRAVA`, `LOCAL_GPX`, or `WORKOUT`).
• *Map Selection*: Toggle filter for routes currently marked selected / visible on map (`isSelected == true`).
• *Distance Thresholds*: Minimum distance threshold ($D \ge D_{\text{min}}$ in meters: 10 km, 25 km, 50 km, 100 km).
• *Elevation Gain Thresholds*: Minimum elevation gain threshold ($E \ge E_{\text{min}}$ in meters: 100 m, 250 m, 500 m, 1000 m).
3. *Shared Modal Filter Bottom Sheet Architecture*: The system SHALL implement a shared layout shell (`FilterBottomSheetScaffold`) eliminating boilerplate and UI divergence between workout and route filters. The modal bottom sheet SHALL feature:
• A dedicated top header row with localized title and close dismiss button (`Icons.Default.Close`).
• Zero bottom gap artifacts when expanded or dragged, anchoring flush to the device navigation bar.
• A dedicated bottom action bar providing distinct "Reset all" (`OutlinedButton`) and "OK" (`Button`) action buttons with prominent touch targets.
4. *Removable Active Filter Chip Strip*: When one or more filter criteria are active, the system SHALL render a horizontal scrolling chip strip directly below the sport tabs. Each active dimension SHALL render a distinct chip using shared `RemovableFilterChip` with localized label and trailing remove icon (`Icons.Default.Close`). Tapping the remove icon SHALL clear that specific filter dimension reactively without opening the sheet.
5. *Filter Lifecycle & Main View Reset*: Filter criteria SHALL remain active while navigating within the routes section (switching tabs, editing routes, inspecting route on map), but SHALL be automatically cleared when navigating back to the main view (`drawer_start_tracking`) or switching drawer items in `MainActivityWithNavigation`, ensuring subsequent visits start with the full unfiltered route list.
6. *Reactive List Pipeline & Tab Interoperability*: `RoutesViewModel` SHALL combine `routesRepository.allRoutes`, `_sortOrder`, `currentLocation`, and `filterCriteria` into the reactive `routes` state flow. The top-level tabs (*All*, *Bike*, *Run*, *Other*) SHALL continue to partition routes by `BSportType` while strictly respecting all active sub-filters.
7. *Localization Parity (9 Locales)*: All route filter category titles, action buttons, chip descriptors, and empty-state filter prompts SHALL be localized across all 9 supported application locales (EN, DE, ES, FR, IT, JA, NL, PL, PT).
**Acceptance Criteria**:
• *Given* the route list header,
• *Then* the filter action SHALL display the funnel icon (`Icons.Default.FilterAlt`) with badge counter when active.
• *When* the user opens `RouteFilterBottomSheet`,
• *Then* the sheet SHALL anchor flush to the bottom edge without gaps, displaying title, close button, filter controls, and separate bottom "Reset all" and "OK" buttons using `FilterBottomSheetScaffold`.
• *Given* active filters applied to the route list,
• *When* the user navigates back to the main view (e.g. Start Tracking),
• *Then* all active route filters SHALL be cleared, and re-entering the routes section SHALL show all routes.
**Invariants**: Sorting orders (`RouteSortOrder.DISTANCE_TO_USER`, `TOTAL_ELEVATION_GAIN`, `ROUTE_DISTANCE`, `NAME`), sport tabs, GPX import, Strava route sync, route deletion, route editing, and workout filtering (`REQ-UI-132`) MUST NOT be altered or regressed.

### REQ-UI-134: Multi-Dimensional Segment Filtering, Shared Modal Filter Sheet & Persistent Filter State [Verified]
- **Tests**: `TST-UI-087`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `SegmentFilterCriteria.kt`, `SegmentFilterBottomSheet.kt`, `ActiveSegmentFilterChipsRow.kt`, `FilterBottomSheetScaffold.kt`, `FilterActionButton.kt`, `SegmentsTabsScreen.kt`, `SegmentListViewModel.kt`, `MyPreferenceManager.kt`, `strings.xml` (all 9 locales)
- **Rationale**: The system SHALL provide multi-dimensional filtering for Strava-starred segments on the tabbed segment list (`SegmentsTabsScreen`):
1. *Action Trigger & Active Indicator*: The header action row (`SegmentsTabsScreen`) SHALL display a dedicated filter action button positioned at the end of the action row (after the sort button) featuring the Material funnel icon (`Icons.Default.FilterAlt`) and localized content description. When one or more filter criteria are active (`isFilterActive == true`), the filter action button SHALL display a visual badge indicating the active filter count.
2. *Filter Dimension Set*: The system SHALL support filtering across orthogonal segment dimensions encapsulated in immutable `SegmentFilterCriteria`:
• *Text Search*: Case-insensitive substring matching against segment `name` and `city`.
• *Minimum Climb Category* (`minClimbCategory: Int?`): Filter segments at or above a minimum Strava climb category (1 = Cat. 4, 2 = Cat. 3, 3 = Cat. 2, 4 = Cat. 1, 5 = HC). Segments with category 0 (uncategorized) SHALL be excluded when this filter is active.
• *Distance Thresholds*: Minimum distance threshold ($D \ge D_{\text{min}}$ in meters: 5 km, 10 km, 25 km, 50 km).
• *Elevation Gain Thresholds*: Minimum elevation gain threshold ($E \ge E_{\text{min}}$ in meters: 100 m, 250 m, 500 m, 1000 m).
• *Has PR*: Toggle filter for segments on which the user holds a personal record (`prTime_raw > 0`).
3. *Shared Modal Filter Bottom Sheet Architecture*: The system SHALL reuse the existing shared layout shell (`FilterBottomSheetScaffold`) for zero boilerplate and full visual consistency with workout and route filtering. The modal bottom sheet SHALL feature:
• A dedicated top header row with localized title and close dismiss button (`Icons.Default.Close`).
• Zero bottom gap artifacts when expanded or dragged, anchoring flush to the device navigation bar.
• A dedicated bottom action bar providing distinct "Reset all" (`OutlinedButton`) and "OK" (`Button`) action buttons with prominent touch targets.
4. *Removable Active Filter Chip Strip*: When one or more filter criteria are active, the system SHALL render a horizontal scrolling chip strip directly below the sport tabs. Each active dimension SHALL render a distinct chip using shared `RemovableFilterChip` with localized label and trailing remove icon (`Icons.Default.Close`). Tapping the remove icon SHALL clear that specific filter dimension reactively without opening the sheet.
5. *Filter Lifecycle & Persistence*: Filter criteria SHALL persist across tab switches within the segments screen via `DataStore`. The filter criteria are NOT auto-cleared on back navigation (segments do not interact with the main navigation drawer clear logic).
6. *Reactive List Pipeline & Tab Interoperability*: `SegmentListViewModel` SHALL combine `segmentsRepository.allSegmentsWithPath`, `_sortOrder`, `currentLocation`, and `filterCriteria` into the reactive `segmentsWithPath` state flow. The Bike and Run tabs SHALL continue to partition segments by `BSportType` while strictly respecting all active sub-filters.
7. *Localization Parity (9 Locales)*: All segment filter category titles, action buttons, chip descriptors, and empty-state filter prompts SHALL be localized across all 9 supported application locales (EN, DE, ES, FR, IT, JA, NL, PL, PT).
**Acceptance Criteria**:
• *Given* the segment list header,
• *Then* the filter action SHALL display the funnel icon (`Icons.Default.FilterAlt`) with badge counter when active.
• *When* the user opens `SegmentFilterBottomSheet`,
• *Then* the sheet SHALL anchor flush to the bottom edge without gaps, displaying title, close button, filter sections (Query, Climb Category, Distance, Elevation, Has PR), and separate bottom "Reset all" and "OK" buttons using `FilterBottomSheetScaffold`.
• *Given* active filters applied to the segment list,
• *When* the user switches between Bike and Run tabs,
• *Then* the active filters SHALL remain in effect on both tabs.
**Invariants**: Sorting orders (`SegmentSortOrder.DISTANCE_TO_USER`, `CLIMB_CATEGORY`, `TOTAL_ELEVATION_GAIN`, `AVERAGE_GRADE`, `SEGMENT_DISTANCE`, `NAME`), sport tabs (Bike, Run), Strava sync/refresh, and route/workout filtering (`REQ-UI-132`, `REQ-UI-133`) MUST NOT be altered or regressed.

### REQ-UI-135: Multi-Dimensional Favorite Tracks Filtering & Modal Sheet [Verified]
- **Tests**: `TST-UI-088`
- **Priority**: High
- **Type**: Functional
- **Verification Method**: Unit / Review
- **Implementation**: `WorkoutClusterComponents.kt`, `ClusterFilterCriteria.kt`, `FilterModalSheet.kt`
- **Rationale**: The system SHALL provide multi-dimensional filtering for favorite tracks (Lieblingsstrecken / workout clusters) via a shared modal filter sheet supporting text search, linked equipment matching, reference distance thresholds, and persistent filter state across sessions.
- **Description**: Multi-Dimensional Favorite Tracks Filtering & Modal Sheet.

### REQ-UI-136: Favorite Tracks (Lieblingsstrecken) Sorting by Recordings, Distance to User, Length, and Name [Verified]
- **Tests**: `TST-UI-089`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `ClusterSortOrder.kt`, `WorkoutClustersTabsScreen.kt`, `WorkoutClustersViewModel.kt`
- **Rationale**: The system SHALL provide flexible sorting capabilities for Favorite Tracks / Workout Clusters on the tabbed clusters list (`WorkoutClustersTabsScreen`):
1. *Action Trigger & Sort Menu*: The header action row (`WorkoutClustersTabsScreen`) SHALL display a dedicated sort action button (`Icons.Default.Sort`) with localized content description (`@string/sort`), positioned consistently between the tuning settings icon and the filter action button. Tapping the sort button SHALL open a dropdown menu (`DropdownMenu`) presenting all available sort options defined by `ClusterSortOrder`.
2. *Sort Dimensions (`ClusterSortOrder`)*:
• **Recordings / Frequency (`RECORDINGS`)** (Default): Sorts clusters by `hitCount` descending (most frequented favorite tracks first), with secondary tie-breaking by `name.lowercase()` ascending. Labeled via `@string/filter_section_recordings` ("Recordings" / "Aufzeichnungen").
• **Distance to User (`DISTANCE_TO_USER`)**: Sorts clusters by geodetic distance from current user GPS coordinates (`currentLocation`) to cluster start coordinates (`startLat`, `startLng`) ascending (closest first). If GPS location is unavailable or denied, this menu item SHALL be disabled and visually muted (`alpha = 0.38f`), with fallback sorting by `name.lowercase()`. Labeled via `@string/sort_closest` ("Closest" / "Entfernung").
• **Track Length / Reference Distance (`DISTANCE`)**: Sorts clusters by `refDistance` descending (longest tracks first), with secondary tie-breaking by `name.lowercase()` ascending. Labeled via `@string/sort_length` ("Length" / "Länge").
• **Name (`NAME`)**: Sorts clusters alphabetically by `name.lowercase()` ascending. Labeled via `@string/sort_name` ("Name").
3. *Selection State & Interaction*: The currently active sort order SHALL be indicated by a leading checkmark icon (`Icons.Default.Check`) in the dropdown menu. Selecting an option SHALL update the ViewModel's sort order state, re-sort the cluster list reactively, and dismiss the dropdown menu.
4. *Tab Interoperability & Scope*: Sorting SHALL apply reactively to the filtered cluster list across all sport tabs (*All*, *Bike*, *Run*, *Other*). The *Unclustered* tab SHALL remain ordered by workout date.
5. *Localization Parity (9 Locales)*: All sort labels and content descriptions SHALL be localized across all 9 supported application locales (EN, DE, ES, FR, IT, JA, NL, PL, PT).
**Acceptance Criteria**:
• *Given* the favorite tracks header,
• *Then* the sort action button SHALL be visible between the tuning icon and filter icon.
• *When* the sort menu is opened,
• *Then* options `RECORDINGS`, `DISTANCE_TO_USER`, `DISTANCE`, and `NAME` SHALL appear, with a checkmark next to the active selection.
• *Given* GPS location is unavailable,
• *Then* the `DISTANCE_TO_USER` option SHALL be disabled and grayed out.
• *When* the user selects a sort order (e.g. `NAME`),
• *Then* the cluster list SHALL immediately reorder alphabetically.
**Invariants**: Existing filtering (`REQ-UI-135`), cluster tuning, manual cluster creation, deletion, and route/segment sorting (`RouteSortOrder`, `SegmentSortOrder`) MUST NOT be altered or regressed.

### REQ-UI-137: Cluster Algorithm & Lieblingsstrecken Educational Info Modal [Verified]
- **Tests**: `TST-UI-090`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `ClusterInfoDialog.kt`, `WorkoutClustersTabsScreen.kt`, `ClusterTuningScreen.kt`, `strings.xml` (all 9 locales)
- **Rationale**: The system SHALL provide athletes with structured, comprehensive in-app educational documentation explaining the workout clustering algorithm (detection of "Lieblingsstrecken" / Favorite Tracks):
1. *Action Triggers & Entry Points*:
• **Favorite Tracks Header (`WorkoutClustersTabsScreen`)**: The header action row SHALL display a dedicated info action button (`Icons.Outlined.Info`) with localized content description (`@string/cluster_info_title`), positioned in the action row alongside Tuning, Sort, and Filter. Tapping this button SHALL display `ClusterInfoDialog`.
• **Cluster Parameters / Tuning TopAppBar (`ClusterTuningScreen`)**: The top app bar SHALL display an info action button (`IconButton` with `Icons.Outlined.Info`) with localized content description (`@string/cluster_info_title`). Tapping this button SHALL display `ClusterInfoDialog`.
2. *Educational Modal Dialog Architecture (`ClusterInfoDialog`)*:
The modal dialog SHALL be implemented in Jetpack Compose (`AlertDialog`), featuring:
• **Title**: Prominently displays the localized headline `@string/cluster_info_title` ("Workout Clustering / Lieblingsstrecken").
• **Scrollable Content (`Column` with `verticalScroll`)**:
- *Section 1: What are Lieblingsstrecken?* (`@string/cluster_info_what_title`, `@string/cluster_info_what_desc`): Explains automatic route grouping, smart naming using iconic country-specific lakes/courses (e.g. "Lake Tahoe Loop #5", „Chiemsee-Runde #5“), equipment tracking, and multi-session performance comparison across identical paths.
- *Section 2: The 3D Topological Fingerprint* (`@string/cluster_info_fingerprint_title`, `@string/cluster_info_fingerprint_desc`): Details the simple but surprisingly good algorithm based on a 6-dimensional spatial metric: (a) Start point (200m), (b) End point (200m), (c) Apex / Max Line Distance point (furthest geographical deviation from start, 400m), (d) Track Length (relative tolerance, 20%), (e) Min Altitude Position (optional spatial 3D anchor, 400m), (f) Max Altitude Position (optional spatial 3D anchor, 400m).
- *Section 3: Sport Awareness & Learning Centroid* (`@string/cluster_info_sports_title`, `@string/cluster_info_sports_desc`): Explains sport-type matching firewall (e.g. distinguishing Road Bike vs. Running) and dynamic running centroid coordinate adaptation as new workouts join the cluster.
- *Section 4: Tuning & Sensitivity* (`@string/cluster_info_tuning_title`, `@string/cluster_info_tuning_desc`): Explains how to use the Master Sensitivity Slider (from Strict to Relaxed / Locker) and fine-tune individual tolerances for varied terrain.
• **Action Button**: A prominent "Close" button (`TextButton`) with localized label `@string/close` to dismiss the dialog.
3. *Responsive & Theme Integration*:
• Dialog SHALL adapt gracefully to dark and light Material 3 themes.
• Content MUST be vertically scrollable to guarantee full readability on compact screen heights and large font scales without truncation.
4. *Localization Parity (9 Locales)*:
All educational text keys, section titles, descriptions, and action labels SHALL be fully translated and localized across all 9 supported application locales (EN, DE, ES, FR, IT, JA, NL, PL, PT).
**Acceptance Criteria**:
• *Given* the Favorite Tracks screen (`WorkoutClustersTabsScreen`),
• *When* the user taps the info icon in the header,
• *Then* `ClusterInfoDialog` SHALL appear displaying all 4 educational sections.
• *Given* the Cluster Tuning screen (`ClusterTuningScreen`),
• *When* the user taps the info action icon in the top app bar,
• *Then* `ClusterInfoDialog` SHALL appear displaying all 4 educational sections.
• *When* the user taps "Close" or outside the dialog,
• *Then* the dialog SHALL dismiss cleanly without state loss.
**Invariants**: Opening or dismissing the dialog MUST NOT alter cluster tolerance preferences, cluster database records, sorting orders, or filtering criteria. Existing cluster tuning sliders and switches MUST continue to operate without interference.

### REQ-UI-138: Detailed Workout Inspection from Favorite Tracks with Full-Fidelity Tracks, Extrema, and Elevation Profiles [Verified]
- **Tests**: `TST-UI-091`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `WorkoutClustersViewModel.kt`, `WorkoutClustersFragment.kt`, `MapModels.kt`, `TrackOnMapScreen.kt`
- **Rationale**: The system SHALL ensure that navigating from the Favorite Tracks / Workout Clusters view (`WorkoutClustersFragment` / `WorkoutClustersTabsScreen`) to inspect a specific workout session displays the complete, high-fidelity workout track with authentic elevation profiles, sensor extrema markers, and technical track layers:
1. *Workout Track & Telemetry Loading*: When a workout within a cluster or unclustered list is selected for detailed inspection, `WorkoutClustersViewModel` SHALL ensure `_clusterWorkouts` state contains the cluster's workouts so that `selectWorkoutForPeek(workout.id)` successfully resolves the workout, loads its complete track telemetry via `WorkoutClusterRepository.getWorkoutWithTrack(workout.id)`, and updates `_peekedWorkoutDataWithTrack`.
2. *Elevation Profile Fidelity*: The elevation profile (`ElevationProfile`) SHALL render the authentic altitude samples (`encodedAltitudes`) and distances (`encodedDistances`) of the inspected workout. The altitude axes, minimum/maximum bounds, and elevation gain SHALL accurately reflect recorded trackpoint elevation rather than default zero or flatline clamping (`[-10m, +10m]`).
3. *Fallback Track Point Preservation (`WorkoutData.toMapTrack()`)*: When `WorkoutData.toMapTrack()` converts a `WorkoutData` record to a `MapTrack`, it SHALL preserve available `encodedAltitudes` and `encodedDistances` by decoding them into corresponding `PathPoint(altitude, latLng, distance)` elements rather than hardcoding altitude and distance to `0.0`.
4. *Sensor Extrema & Technical Track Layers*: When inspecting a workout from the cluster view, the system SHALL initialize `TrackOnMapAftermathViewModel` with the inspected workout (`loadAftermathData(workout)`), ensuring that all sensor extrema pins (Altitude Min/Max, Speed Max, Heart Rate Max, Power Max), technical track layers (Fused, GPS, Network), segments, and route overlays are fully populated, selectable, and rendered on `TrackOnMapScreen`.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an athlete viewing the workouts belonging to a Favorite Track (Lieblingsstrecke) or unclustered workouts,
• *When* the user taps a workout card to inspect it in the full-screen track map view (`TrackOnMapScreen`),
• *Then* the elevation profile at the bottom of the map SHALL display the workout's true altitude curve, with accurate elevation gain, minimum altitude, and maximum altitude matching the workout summary.
• *Then* the map SHALL display the authoritative extrema pins (Min Altitude, Max Altitude, Max Speed, and Max Heart Rate where recorded).
• *Then* the technical track layer controls (GPS, Fused, Network) SHALL reflect the recorded provider data and be toggleable.
• *Given* any `WorkoutData` instance containing valid `encodedAltitudes` and `encodedDistances`,
• *When* `workout.toMapTrack()` is invoked,
• *Then* the resulting `MapTrack.path` points SHALL contain the decoded altitude and distance values.

**Invariants**: Cluster list display, cluster grouping metrics, cluster sorting (`REQ-UI-136`), cluster filtering (`REQ-UI-135`), standalone workout summary detail screen (`WorkoutTabsScreen`), and live tracking map rendering MUST NOT be altered or regressed.

### REQ-UI-139: Right-Aligned Edge FastScrollbar, Isolated Thumb Touch Target & Continuous Drag Accumulation [Verified]
- **Tests**: `TST-UI-092`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `FastScrollbar.kt`, `WorkoutList.kt`
- **Rationale**: The system SHALL position the fast scrollbar (`FastScrollbar.kt`) flush against the right edge of the viewport, isolate drag gesture detection strictly to the draggable thumb, and maintain continuous drag displacement accumulation:
1. *Flush Edge Alignment*: In `WorkoutList.kt`, `FastScrollbar` SHALL be aligned flush to `Alignment.CenterEnd` with zero end padding (`padding(end = 0.dp)`), eliminating floating inward offsets.
2. *Right-Aligned Track & Ergonomic Thumb*: In `FastScrollbar.kt`, the track (`width = 4.dp`) SHALL be aligned to `Alignment.CenterEnd` flush against the viewport edge. The draggable thumb hit box SHALL be aligned to `Alignment.TopEnd` with an ergonomic touch target (`width = 28.dp`, `height = 48.dp`) containing an 8dp visual pill aligned to `Alignment.CenterEnd`, ensuring effortless grabbing while physically isolating the track from list cards (which end at `screenWidth - 8.dp`).
3. *Isolated Thumb Touch Target & Gesture Scoping*: Drag gesture detection (`detectDragGestures`) SHALL be scoped strictly to the draggable thumb composable, and MUST NOT be declared on the full-height outer container Box. The track area above and below the thumb SHALL NOT capture or consume pointer input, ensuring underlying list item action buttons (e.g. 3-dots export menu, edit button) receive click events without interference.
4. *Continuous Drag Accumulation & Zero-Latency Visual Tracking*: During drag gestures, the scrollbar SHALL maintain an active drag state (`isDragging: Boolean`, `dragProgress: Float`), continuously accumulating fractional displacement deltas without per-frame integer truncation loss ($\text{dragProgress} = (\text{dragProgress} + \text{dragAmount.y} / \text{maxOffsetPx}).\text{coerceIn}(0f, 1f)$). The visual thumb position during dragging SHALL be driven directly by `dragProgress` for zero-lag responsiveness, while dispatching `state.scrollToItem(targetIndex)` across all items ($0 \dots \text{totalItems} - 1$).

**Acceptance Criteria (Given-When-Then)**:
• *Given* a scrollable list of workouts in `WorkoutList`,
• *When* the list is displayed,
• *Then* the fast scrollbar track and thumb SHALL sit flush against the right display edge.
• *Given* any list item card with right-aligned action buttons (such as the 3-dots menu or edit button in `WorkoutHeader`),
• *When* the user taps the button while the scrollbar thumb is at a different vertical position,
• *Then* the button click SHALL be triggered immediately and reliably without being intercepted by `FastScrollbar`.
• *Given* the fast scrollbar thumb,
• *When* the user touches and drags the thumb vertically at normal finger speed (small per-frame deltas),
• *Then* the thumb SHALL follow the finger with zero lag and the `LazyColumn` SHALL scroll smoothly across all items from first to last.

**Invariants**: Dynamic alpha fading (0.4f idle to 1.0f active scroll with 500ms tween), idle progress calculation from `firstVisibleItemIndex` / `firstVisibleItemScrollOffset`, and smooth scrolling via `scrollToItem` MUST NOT be regressed; list item layouts and action button callbacks MUST NOT be altered.

### REQ-UI-140: Universal FastScrollbar Integration Across All Application Collection Views [Verified]
- **Tests**: `TST-UI-093`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
• In all views, touch detection MUST remain strictly confined to the draggable thumb, ensuring all item click actions, checkboxes, switches, menus, and delete handlers remain 100% accessible with zero click interception.
• Fractional drag displacement accumulation and zero-latency visual thumb tracking MUST be preserved across all screens.

**Acceptance Criteria (Given-When-Then)**:
• *Given* any of the 7 collection screens (Periods, Routes, Favorite Tracks, Segments, Equipment, Sport Types, Devices),
• *When* the number of items exceeds the screen viewport,
• *Then* the edge-aligned `FastScrollbar` SHALL be visible on the right display edge.
• *When* the user touches and drags the scrollbar thumb vertically,
• *Then* the list SHALL scroll smoothly across all items from top to bottom.
• *Given* any collection screen where total items fit within the screen viewport,
• *Then* `FastScrollbar` SHALL remain completely hidden without altering list content bounds.
• *Given* any list item card in any collection screen with interactive action buttons (e.g. Route item, Cluster card, Equipment config/stats, Device pair switch),
• *When* the user taps the action button while the scrollbar thumb is at a different vertical position,
• *Then* the action click SHALL be triggered immediately and reliably without being intercepted by `FastScrollbar`.

**Invariants**: Existing list layouts, card layouts, collapsing toolbar connections, FAB placement, filtering, sorting, and database operations across all views MUST NOT be altered or regressed.
- **Rationale**: The system SHALL integrate the fast scrollbar component (`FastScrollbar.kt`) across all primary scrollable collection screens in the application:
1. *Periods (Zeiträume)*: In `PeriodList.kt`, `LazyColumn` SHALL be accompanied by a flush right-aligned `FastScrollbar` mapped to `scrollState`, respecting system navigation bar insets.
2. *Routes (Routen)*: In `RouteList.kt`, `LazyColumn` SHALL be accompanied by a flush right-aligned `FastScrollbar` mapped to `scrollState`, respecting collapsing app bar offset and system navigation insets.
3. *Favorite Tracks (Lieblingsstrecken)*: In `WorkoutClustersList.kt`, both `WorkoutClustersClusteredList` and `UnclusteredWorkoutsList` SHALL be accompanied by a flush right-aligned `FastScrollbar` mapped to `scrollState`, respecting header offset and FAB bottom margin (`80.dp`).
4. *Segments (Segmente)*: In `SegmentList.kt`, `LazyColumn` SHALL be accompanied by a flush right-aligned `FastScrollbar` mapped to `scrollState`, respecting collapsing app bar offset and system navigation insets.
5. *Equipment (Ausrüstung)*: In `EquipmentTabsScreen.kt` (`EquipmentList`), `LazyColumn` SHALL be accompanied by a flush right-aligned `FastScrollbar` mapped to its `LazyListState`, respecting collapsing app bar offset and system navigation insets.
6. *Sport Types (Sportarten)*: In `SportTypesTabsScreen.kt`, `LazyColumn` SHALL be accompanied by a flush right-aligned `FastScrollbar` mapped to its `LazyListState`, respecting collapsing app bar offset and system navigation insets.
7. *Connected Devices (Geräte)*: In `DeviceListScreen.kt`, `LazyColumn` SHALL be accompanied by a flush right-aligned `FastScrollbar` mapped to `scrollState`, respecting search header offset and navigation insets.
8. *Universal Invariants & Behavior*:
• In all views, `FastScrollbar` MUST automatically hide when `totalItems <= visibleItems

### REQ-UI-141: Workout Summary Lap Overview & Performance Highlights [Verified]
- **Tests**: `TST-UI-094`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `WorkoutSummary.kt`, `WorkoutLaps.kt`, `LapData.kt`, `WorkoutData.kt`, `WorkoutDataMapper.kt`, `WorkoutRepository.kt`, `LapsDatabaseManager.java`
- **Rationale**: The system SHALL display a structured overview of all recorded laps within workout summary cards (`WorkoutSummary.kt`) in the aftermath screen (`WorkoutTabsScreen` / `WorkoutList.kt`), featuring lap names, metrics, and split performance badges:
1. *Laps Section Placement & Visibility*: In `WorkoutSummary.kt`, a dedicated `WorkoutLaps` section SHALL be rendered directly after `WorkoutExtrema` and before `StravaActivitySection` / `WorkoutMediaSection`. When a workout contains 0 recorded laps, `WorkoutLaps` SHALL be completely omitted with zero vertical layout overhead.
2. *Split Table Layout & Formatting*: For workouts with $\ge 1$ laps, the section SHALL render a structured table displaying each lap's sequential order and metrics:
• *Lap Name*: Renders the lap's custom `name` if present and non-blank; otherwise falls back to localized default `"Lap X"` (where $X$ is the 1-based sequential lap index). If a custom description is present, a secondary note line SHALL be rendered beneath the lap name.
• *Duration & Distance*: Renders formatted split active duration (`formatters.time.format`) and split distance (`formatters.distance.format_with_units`).
• *Pace / Speed*: Renders sport-dependent pace for running and walking activities (`formatters.pace.format_with_units`), and speed for cycling and other sports (`formatters.speed.format_with_units`).
3. *Performance Highlight Badges (Rabbit 🐇 & Hedgehog 🦔)*:
For workouts containing $\ge 2$ valid laps (`speedAverageMps > 0`):
• *Fastest Lap (Rabbit)*: The lap possessing the highest average speed (`speedAverageMps`) SHALL be highlighted with a Rabbit badge (`🐇`).
• *Slowest Lap (Hedgehog)*: The lap possessing the lowest average speed (`speedAverageMps`) SHALL be highlighted with a Hedgehog badge (`🦔`).
• Workouts with a single lap or identical lap speeds SHALL NOT display fastest/slowest badges.
4. *Expandable List Ergonomics*: When a workout contains $> 3$ laps, `WorkoutLaps` SHALL default to showing the first 3 laps accompanied by an expandable toggle button (`Show all X laps` / `Show fewer`) to conserve vertical space in `WorkoutList`. When $\le 3$ laps, all laps SHALL be displayed directly.
5. *Database Schema & Migration Safety*: In `LapsDatabaseManager.java`, SQLite database `Laps.db` (`LapsDbHelper`) SHALL be upgraded to `DB_VERSION = 2`. The migration in `onUpgrade` MUST execute non-destructive `ALTER TABLE` operations adding nullable columns `name TEXT` and `description TEXT`, and MUST NOT drop or clear existing tables. Batch retrieval method `getLapsForWorkouts(chunkIds)` SHALL be provided to eliminate N+1 queries during list loading.

**Acceptance Criteria (Given-When-Then)**:
• *Given* a workout session with $\ge 1$ laps displayed in `WorkoutSummary`,
• *When* the workout card is rendered,
• *Then* `WorkoutLaps` SHALL display each lap with its name, duration, distance, and sport-appropriate pace/speed.
• *Given* a workout session with $\ge 2$ laps of varying speeds,
• *Then* the fastest lap SHALL be badged with `🐇` (Rabbit) and the slowest lap badged with `🦔` (Hedgehog).
• *Given* a workout session with $> 3$ laps,
• *Then* the table SHALL initially show 3 laps and tapping the expand button SHALL reveal all laps.
• *Given* an existing database at `DB_VERSION = 1` with historical laps,
• *When* the app upgrades to `DB_VERSION = 2`,
• *Then* all existing lap records SHALL be preserved intact with null `name` and `description`.

**Invariants**: Card touch navigation to map (`REQ-SET-071`), single-workout deletion (`REQ-DAT-010`), TCX/GPX export queries, and metric unit localization (`REQ-UI-016`) MUST NOT be altered or regressed.

### REQ-UI-142: Interactive Lap Detail Editing, Quick-Tag Presets, Sequential Navigation, and Segment Map Highlighting [Verified]
- **Tests**: `TST-UI-095`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `LapEditBottomSheet.kt`, `WorkoutLaps.kt`, `WorkoutSummary.kt`, `WorkoutRepository.kt`, `LapSegmentUtils.kt`, `LapsDatabaseManager.java`
- **Rationale**: The system SHALL provide an interactive editing bottom sheet (`LapEditBottomSheet.kt`) invoked by tapping any lap row in `WorkoutLaps.kt` across both Workout Summary (`WorkoutSummary.kt`) and Workout Details (`TrackOnMapScreen.kt` / `WorkoutDetailTabbedContent.kt`), allowing athletes to customize lap names and descriptions, rapidly apply preset tags, navigate sequentially across laps, visually inspect the lap track on an expandable map, and trigger boundary move actions:
1. *Edit Sheet Invocation & Split Summary Header*: In `WorkoutLaps.kt`, tapping any lap row in either Workout Summary (`WorkoutSummary.kt`) or Workout Details (`TrackOnMapScreen.kt` / `WorkoutDetailTabbedContent.kt`) SHALL invoke `LapEditBottomSheet` for the selected lap. In Workout Details, `LapEditBottomSheet` SHALL be hosted as an `AppModalBottomSheet` overlay alongside the tabbed content (preserving the active tab per Rule 47), displaying the interactive lap map segment preview, persisting custom lap name & description edits, and exposing lap split/move boundary actions. The sheet header SHALL render the lap index and formatted metrics: active duration (`formatters.time.format`), distance (`formatters.distance.format_with_units`), and sport-appropriate pace or speed (`formatters.pace.format_with_units` / `formatters.speed.format_with_units`).
2. *Quick-Tag Presets*: The sheet SHALL display a horizontally scrollable row of quick-tag suggestion chips: `Warm-up`, `Interval`, `Recovery`, `Hill Climb`, `Tempo`, `Sprint`, `Cool-down` (fully localized across all 9 supported languages). Tapping any chip SHALL immediately populate the lap name text field with the selected preset tag.
3. *Custom Name & Description Editing*: Single-line `OutlinedTextField` pre-filled with the lap's existing custom `name` if present (or placeholder defaulting to localized `"Lap X"`), and multi-line `OutlinedTextField` pre-filled with existing `description` notes. Tapping `Save` (or navigating sequentially / dismissing after changes) SHALL invoke `WorkoutRepository.updateLapDetails(workoutId, lapNr, name, description)` to persist changes to `Laps.db` and reactively update the in-memory `WorkoutData.laps` cache without requiring a full workout list reload.
4. *Sequential Lap Navigation*: The sheet SHALL provide `< Previous` (disabled when on the first lap) and `Next >` (disabled when on the last lap) navigation buttons. Tapping either navigation control SHALL persist pending modifications for the active lap and transition the sheet state to the adjacent sequential lap.
5. *Expandable Map Visualization & Intelligent Zoom Strategy*:
• *Two-State Sheet Ergonomics*: In the default half-expanded state, the bottom sheet SHALL prioritize text editing controls. Sliding the sheet upward (fully expanded) SHALL reveal the interactive segment map view.
• *Dual Track Overlay*: The entire workout polyline SHALL be rendered in the background with subtle muted neutral styling (`width = 4.dp`, semi-transparent outline color, `zIndex = 1f`), and the selected lap segment SHALL be rendered prominently in the foreground with vibrant primary styling (`width = 8-10.dp`, `zIndex = 2f`), along with distinct start (green) and stop (red) pins (`zIndex = 3f`).
• *Camera Zoom & Bounding Box*: The map camera SHALL automatically calculate and smoothly animate to the `LatLngBounds` enclosing all trackpoints of the complete workout session (`workoutBounds` derived from `allPoints`) with standard padding (`60-80px`), ensuring the entire course is visible for spatial context. The camera framing SHALL remain stable across sequential lap navigation (`< Previous` / `Next >`), while the highlighted foreground segment and start/stop markers update reactively to reflect the active lap. For stationary/indoor workouts or workouts lacking GPS trackpoints, the map view SHALL safely display an informative empty state without runtime exceptions.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: `REQ-UI-142` (amended to explicitly mandate `LapEditBottomSheet` invocation in Workout Details `TrackOnMapScreen` / `WorkoutDetailTabbedContent` alongside `WorkoutSummary`); interacting with `REQ-UI-408` and `REQ-UI-450`.
2. *Historical Origin & Commit Trace*: `c2f96cae` (`ATT-511`), `ea5fa705` (`ATT-3920`), `ATT-908`, `ATT-5483`.
3. *Root Reason for Existing Formulation*: Conceived when `WorkoutSummary.kt` was the primary workout inspection surface; subsequent tabbed detail refactoring (`ATT-3920`) inadvertently omitted sheet integration, leaving `onLapClick` updating only `selectedLapNr` on the hidden Map tab.
4. *Preservation of Core Invariants*: Retains single-page map highlighting contract (`selectedLapNr`) while adding sheet overlay; preserves quantitative metric immutability; enforces Rule 47 modal overlay alongside host tabs.

**Acceptance Criteria (Given-When-Then)**:
• *Given* a workout session with recorded laps rendered in `WorkoutSummary`,
• *When* the athlete taps any lap row in `WorkoutLaps`,
• *Then* `LapEditBottomSheet` SHALL open displaying the lap's metrics and existing details.
• *Given* a workout session with recorded laps rendered in Workout Details (`WorkoutDetailTabbedContent` / `TrackOnMapScreen`),
• *When* the athlete taps any lap row in the "Runden & Segmente" tab,
• *Then* `LapEditBottomSheet` SHALL open as a modal overlay alongside the tabbed content (Rule 47), displaying the lap map segment preview, allowing custom name and description editing, and exposing lap split/move boundary actions.
• *When* the athlete taps the "Interval" quick-tag chip,
• *Then* the lap name field SHALL immediately update to "Interval".
• *When* the athlete edits the name and description and taps "Save",
• *Then* `Laps.db` SHALL be updated and `WorkoutLaps` SHALL immediately reflect the new name and description.
• *When* the athlete taps "Next >",
• *Then* pending edits SHALL be saved and the sheet SHALL update to display Lap 2.
• *When* the athlete expands the bottom sheet upward,
• *Then* the segment map SHALL become visible, displaying the entire workout polyline in the background with the selected lap highlighted in the foreground and camera zoomed to enclose the complete workout route.
• *When* the athlete navigates between laps via `< Previous` or `Next >`,
• *Then* the map camera framing SHALL remain stable over the entire workout route, while the highlighted segment moves along the track to reflect the newly selected lap.
• *Given* an indoor workout with 0 GPS trackpoints,
• *When* the edit sheet is opened and expanded,
• *Then* the edit controls SHALL remain 100% functional and the map section SHALL gracefully indicate that no GPS track is available.

**Invariants**: Lap quantitative metrics (`timeTotalS`, `distanceTotalM`, `speedAverageMps`, `timeStart`) MUST remain immutable; card touch navigation to `TrackOnMapScreen` (`REQ-SET-071`) on non-lap elements MUST NOT be intercepted; overview table layout, collapsible threshold (`> 3` laps), and performance badges (Rabbit/Hedgehog) (`REQ-UI-141`) MUST NOT be regressed.

### REQ-UI-143: Workout Summary Laps Table Headers, Single-Line Formatting & Proportional Layout [Verified]
- **Tests**: `TST-UI-096`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `WorkoutLaps.kt`, `strings.xml` (all 9 locales)
- **Rationale**: The system SHALL enhance the workout summary laps table (`WorkoutLaps.kt`) with dedicated table headers, guaranteed single-line metric formatting, rebalanced column proportions, and vertically centered performance badges:
1. *Dedicated Table Header Row (`LapTableHeader`)*: In `WorkoutLaps.kt`, when laps are displayed, a dedicated header row SHALL be rendered directly above the lap rows. The header row SHALL render 4 column labels styled consistently with `WorkoutExtrema` (`style = MaterialTheme.typography.labelSmall`, `color = MaterialTheme.colorScheme.onSurfaceVariant`):
• *Column 1 (Lap Name)*: Localized label `@string/lap_table_header_lap` ("Runde" / "Lap").
• *Column 2 (Time)*: Localized label `@string/lap_table_header_time` ("Zeit" / "Time").
• *Column 3 (Distance)*: Localized label `@string/lap_table_header_distance` ("Distanz" / "Distance").
• *Column 4 (Pace or Speed with Unit)*: For running/walking activities, localized pace header including bracketed unit: `@string/pace_short` + `" ["` + unit + `"]"` (e.g., `"Tempo [min/km]"` or `"Pace [min/mi]"`). For cycling and other activities, localized speed header including bracketed unit: `@string/speed_short` + `" ["` + unit + `"]"` (e.g., `"Geschw. [km/h]"` or `"Speed [mph]"`).
• *Column 5 (Spacer / Badge column)*: Fixed width (`26.dp`) matching badge alignment in data rows.
2. *Guaranteed Single-Line Metric Cells*: Data cells for Pace/Speed SHALL render the pure formatted numerical value (`formatters.pace.format` or `formatters.speed.format`) without repeating the unit string. Time, Distance, and Pace/Speed cells SHALL enforce `maxLines = 1`, `softWrap = false`, preventing multi-line wrapping artifacts (such as `"46:27 min/"` wrapping `"km"` onto a second line).
3. *Proportional Column Width Layout*: Column layout in both `LapTableHeader` and `LapRow` SHALL adhere to proportional weights:
• Lap Name column: `Modifier.weight(1.6f)` (providing generous space for custom lap names like "1. Pause am Bach" without premature truncation).
• Time column: `Modifier.weight(0.85f)`.
• Distance column: `Modifier.weight(0.85f)`.
• Pace/Speed column: `Modifier.weight(1.1f)`.
• Badge column: `Modifier.width(26.dp)`, right-aligned.
4. *Badge Alignment*: Performance highlight badges (Rabbit 🐇 and Hedgehog 🦔) SHALL be vertically centered (`Alignment.CenterVertically` or `Box(contentAlignment = Alignment.Center)`) within single-line lap rows.
5. *Localization Parity (9 Locales)*: All lap table header string resources SHALL be localized across all 9 supported application locales (EN, DE, ES, FR, IT, JA, NL, PL, PT).

**Acceptance Criteria (Given-When-Then)**:
• *Given* a workout session with $\ge 1$ laps displayed in `WorkoutSummary`,
• *When* `WorkoutLaps` is rendered,
• *Then* a table header row SHALL appear above the lap rows with localized labels: "Lap", "Time", "Distance", and "Pace [unit]" (or "Speed [unit]").
• *Given* a lap with a long custom name (e.g. "1. Pause am Bach"),
• *When* displayed on a standard 360-400dp mobile screen,
• *Then* the lap name SHALL occupy the widened 1.6f column weight without premature truncation.
• *Given* any lap pace value,
• *When* rendered in the pace column,
• *Then* the numerical value SHALL render cleanly on a single line without wrapping unit text.
• *Given* the fastest or slowest lap with a Rabbit (🐇) or Hedgehog (🦔) badge,
• *When* rendered,
• *Then* the badge SHALL be vertically centered relative to the row's metric values.

**Invariants**: Interactive lap row tap opening `LapEditBottomSheet` (`REQ-UI-142`), Rabbit/Hedgehog detection logic (`REQ-UI-141`), expandable threshold (`> 3` laps), and underlying lap database schema (`Laps.db`) MUST NOT be altered or regressed.

### REQ-UI-144: Streamlined & Collapsible Strava Best Efforts Presentation in Workout Summary [Verified]
- **Tests**: `TST-UI-097`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `StravaActivitySection.kt`, `StravaActivityData.kt`, `strings.xml` (all 9 locales)
- **Rationale**: Within the Strava activity results section of the workout summary card (`StravaActivitySection.kt`), the system SHALL provide a streamlined, unit-aware, and collapsible presentation for Strava best efforts (`best_efforts`) to prevent summary card bloat while prioritizing personal achievements and the activity's primary benchmark time:
1. *Effort Classification & Metric Attributes*:
• A best effort is classified as *Highlight* if it represents a Personal Record (`prRank != null`, where `prRank` $\in \{1, 2, 3\}$).
• An effort is classified as *Mile Effort* (`isMileEffort == true`) if its benchmark name contains `"mile"` or `" mi"` (e.g., `"1/2 mile"`, `"1 mile"`, `"2 miles"`, `"10 miles"`).
• Each effort resolves an effective distance in meters (`effectiveDistanceMeters`) parsed directly from JSON `distance` or inferred from standard benchmark names (400m $\rightarrow$ 400m, 1/2 mile $\rightarrow$ 804.67m, 1k $\rightarrow$ 1000m, 1 mile $\rightarrow$ 1609.34m, 2 miles $\rightarrow$ 3218.69m, 5k $\rightarrow$ 5000m, 10k $\rightarrow$ 10000m, 15k $\rightarrow$ 15000m, 10 miles $\rightarrow$ 16093.44m, 20k $\rightarrow$ 20000m, Half-Marathon $\rightarrow$ 21097.5m, Marathon $\rightarrow$ 42195m).
2. *Unit System Filtering in Collapsed View*:
• When the active unit system is Metric (`TrainingApplication.getUnit() == MyUnits.METRIC`), the system SHALL exclude all imperial mile efforts (`isMileEffort == true`) from the collapsed (reduced) view.
• When Imperial units are active (`MyUnits.IMPERIAL`), mile intervals SHALL remain eligible for the collapsed view.
3. *Mandatory Inclusion of the Longest Distance in Reduced View*:
• When the best efforts are reduced in the collapsed view, the system SHALL guarantee the presentation of the time for the **longest** eligible distance (e.g. 10k in a 10k run, or longest metric distance when metric units are active).
• If the activity achieved PRs on eligible distances, the collapsed view SHALL display all eligible PRs plus the longest distance.
• If the longest distance is itself a PR, it SHALL be included without duplication.
• Displayed efforts in the collapsed view SHALL maintain natural ascending distance order.
4. *Collapse Eligibility & Accordion Interaction*:
• The section SHALL be eligible for collapse if and only if `allEfforts.size > reducedEfforts.size`.
• If `allEfforts.size == reducedEfforts.size`, all efforts SHALL be displayed directly without expansion controls.
• *Collapsed View (`isExpanded == false`, Default)*: Renders `reducedEfforts` and an expand action button: `@string/strava_show_all_best_efforts_format` (`"▼ Show all %1$d best efforts (+%2$d more)"` / in DE: `"▼ Alle %1$d Bestzeiten anzeigen (+%2$d weitere)"`).
• *Expanded View (`isExpanded == true`)*: Renders all `activity.bestEfforts` in full (including mile intervals) and a collapse action button: `@string/strava_show_less_best_efforts_format` (`"▲ Show less (%1$d)"` / in DE: `"▲ Weniger anzeigen (%1$d)"`).
• *State Preservation*: Expansion state SHALL be preserved during lazy list scrolling and configuration changes via `rememberSaveable { mutableStateOf(false) }`.
5. *Localization Parity (9 Locales)*:
All user-facing action labels and formatting strings SHALL be localized across all 9 supported application locales (EN, DE, ES, FR, IT, JA, NL, PL, PT).

**Acceptance Criteria (Given-When-Then)**:
• *Given* an athlete with Metric units viewing a 10k run with 7 best efforts (400m, 1/2 mi, 1k, 1 mi, 2 mi, 5k, 10k) where 1k is a PR #2,
• *When* the workout summary is rendered in collapsed view,
• *Then* exactly 2 best effort rows SHALL be displayed (1k and 10k), zero mile rows SHALL be visible, followed by an expand button `"▼ Alle 7 Bestzeiten anzeigen (+5 weitere)"`.
• *When* the athlete taps the expand button,
• *Then* all 7 best efforts SHALL be displayed, followed by `"▲ Weniger anzeigen (2)"`.
• *Given* an athlete with Metric units viewing a 10k run with 7 best efforts and 0 PRs,
• *When* rendered in collapsed view,
• *Then* exactly 1 row SHALL be displayed (10k), followed by `"▼ Alle 7 Bestzeiten anzeigen (+6 weitere)"`.
• *Given* an athlete with Metric units viewing a 5k run where 5k is PR #1,
• *When* rendered in collapsed view,
• *Then* exactly 1 row SHALL be displayed (5k), with zero duplicate 5k rows, followed by `"▼ Alle 6 Bestzeiten anzeigen (+5 weitere)"`.
• *Given* an activity with 1 best effort (or where all efforts equal the reduced list),
• *When* rendered,
• *Then* all efforts SHALL be displayed directly with zero expand/collapse buttons.

**Invariants**: Segment efforts rendering (`REQ-UI-131`), Strava branding requirements (`PoweredByStrava`), external Strava activity click launch (`StravaHelper.openActivity`), and PR badge styling (`RankBadge`) MUST remain fully intact.

### REQ-UI-145: UI Deletion of Old Unfinished Workouts & Active Tracking Deletion Protection [Verified]
- **Tests**: `TST-UI-098`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `WorkoutHeader.kt`, `WorkoutSummary.kt`, `WorkoutSummaryCompact.kt`, `TrainingApplication.java`
- **Rationale**: The system SHALL allow the user to delete old unfinished or crashed workouts (`finished == false` / SQLite column `FINISHED == 0`) via the user interface in both expanded (`WorkoutSummary.kt`) and compact (`WorkoutSummaryCompact.kt`) aftermath list views, while strictly preserving unfinished workouts in SQLite until explicitly deleted, maintaining their visual alpha (`0.5f`), and preventing deletion of any workout currently being actively tracked:
1. *Database Retention Invariant*: Unfinished and crashed workouts SHALL remain preserved in the SQLite database (`WorkoutSummaries.TABLE`) across application restarts and new tracking launches. The system SHALL NOT automatically delete or purge unfinished workouts in the background or upon starting a new session.
2. *Visual Differentiation*: In `WorkoutSummary.kt` and `WorkoutSummaryCompact.kt`, unfinished workouts (`workoutData.headerData.finished == false`) SHALL continue to be rendered with semi-transparent alpha (`contentAlpha = 0.5f`), clearly indicating their incomplete or interrupted status.
3. *Decoupled Header Deletion Ergonomics*: In `WorkoutHeader.kt`, the long-press context menu for workout deletion (`showContextMenu = true`) SHALL be decoupled from export menu enablement (`menuEnabled`). Whenever deletion is permitted (`canDelete == true`), the header surface SHALL attach `combinedClickable` with `onLongClick = { showContextMenu = true }` displaying the "Delete" (`R.string.delete`) context menu action. The 3-dots export menu SHALL remain governed by `menuEnabled = workoutData.headerData.finished` (preventing incomplete workouts from being exported to external formats or Strava).
4. *Active Tracking Safety Guard*: If an unfinished workout is the one currently being tracked by `TrackerService` (`TrainingApplication.isActivelyTracked(workoutId) == true`, where `isTracking() == true && workoutId == activeWorkoutId`), the system SHALL strictly prohibit deletion via the UI in both expanded (`WorkoutSummary`) and compact (`WorkoutSummaryCompact`) views. For non-actively tracked workouts (finished workouts and old unfinished workouts), deletion SHALL remain fully accessible.
5. *Cascading Deletion Execution*: When the user confirms deletion in the confirmation dialog (`WorkoutDeleteDialog`), the deletion SHALL execute via `WorkoutDeletionHelper.deleteWorkout(workoutId)` to cleanly purge the summary row, laps, extrema values, export status, and drop dynamic sample tables (`workout_samples_<baseFileName>`) with zero orphaned tables (`REQ-DAT-010`).

**Acceptance Criteria (Given-When-Then)**:
• *Given* an old unfinished workout in SQLite (`finished == false`) and tracking is idle (`isTracking() == false`),
• *When* displayed in `WorkoutSummary` (expanded view),
• *Then* it SHALL render with `alpha = 0.5f`, and long-pressing the header SHALL open the context menu with "Delete".
• *When* the user taps "Delete" and confirms in `WorkoutDeleteDialog`,
• *Then* the workout and all associated relational records and sample tables SHALL be completely purged from SQLite, and the workout list SHALL refresh.
• *Given* an old unfinished workout displayed in `WorkoutSummaryCompact` (compact view),
• *When* the user long-presses the item,
• *Then* the context menu SHALL display the "Delete" option.
• *Given* an active live tracking session (`isTracking() == true` with active workout ID $W$),
• *When* viewing workout $W$ in `WorkoutSummary` or `WorkoutSummaryCompact`,
• *Then* the UI SHALL NOT allow deletion of workout $W$ while tracking is active.
• *Given* a completed workout (`finished == true`),
• *Then* both the 3-dots export menu and the long-press deletion context menu SHALL remain fully functional.

**Invariants**: Automatic workout data retention (`finished == false` remains in SQLite), cascaded table drop sequencing (`ATT-296`), and single-workout deletion dialog flow MUST remain strictly intact.

### REQ-UI-146: Option to Mark Old Unfinished Workouts as Finished & Period Integration [Verified]
- **Tests**: `TST-UI-099`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `WorkoutHeader.kt`, `WorkoutSummary.kt`, `WorkoutSummaryCompact.kt`, `WorkoutList.kt`, `WorkoutRepository.kt`, `WorkoutSummariesDatabaseManager.java`, `PeriodsRepository.kt`, `strings.xml`
- **Rationale**: The system SHALL allow the user to mark an old unfinished or crashed workout (`finished == false` / SQLite column `FINISHED == 0`) as finished via the user interface in both expanded (`WorkoutSummary.kt` via `WorkoutHeader.kt`) and compact (`WorkoutSummaryCompact.kt`) aftermath list views, updating persistence, visual opacity, export capability, and periods aggregation while strictly preventing actively tracked sessions from being marked finished via the aftermath UI:
1. *Context Menu Action Availability*: For any unfinished workout (`finished == false`) that is not actively being recorded (`!TrainingApplication.isActivelyTracked(workoutId)`), the long-press context menu in `WorkoutHeader.kt` and `WorkoutSummaryCompact.kt` SHALL display the action "Als abgeschlossen markieren" / "Mark as finished" (`@string/mark_as_finished`).
2. *Database & Memory State Transition*: Selecting "Mark as finished" SHALL invoke `WorkoutRepository.markWorkoutFinished(workoutId)` which SHALL atomically update `WorkoutSummaries.FINISHED = 1` in SQLite (`WorkoutSummaries.TABLE`) for the target workout ID and update the in-memory cache (`updateWorkoutInMemory`), emitting the updated `WorkoutData` with `finished = true`.
3. *Reactive Visual & Menu Ergonomics*: Upon marking as finished, the workout card SHALL reactively re-render with full opacity (`alpha = TTAlpha.High` / `1.0f`), the 3-dots export menu SHALL become enabled (`menuEnabled = true`), granting immediate access to TCX/GPX export and Strava upload, and the "Mark as finished" action SHALL be omitted from subsequent context menus.
4. *Historical Period Aggregation*: `WorkoutRepository.markWorkoutFinished` SHALL directly invoke `PeriodsRepository.getInstance(app).onWorkoutFinished(updatedWorkout)`, immediately aggregating the newly finalized workout into its Day period and rolling up metrics (workout count, duration, distance, ascent) to Week, Month, and Year periods.
5. *Active Tracking Safety Guard*: If an unfinished workout is currently being recorded by `TrackerService` (`TrainingApplication.isActivelyTracked(workoutId) == true`), the "Mark as finished" option MUST NOT be visible or accessible in the aftermath context menu.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an old unfinished workout in SQLite (`finished == false`) and tracking is idle (`isTracking() == false`),
• *When* the athlete long-presses the workout in expanded view (`WorkoutSummary`) or compact view (`WorkoutSummaryCompact`),
• *Then* the context menu SHALL display the "Mark as finished" option.
• *When* the athlete selects "Mark as finished",
• *Then* `WorkoutSummaries.FINISHED` SHALL update to 1 in SQLite, the workout card SHALL render with normal opacity (`alpha = 1.0f`), the 3-dots export menu SHALL be enabled, and the workout metrics SHALL appear in the corresponding Period Summary (Day, Week, Month, Year).
• *Given* an active live tracking session (`isTracking() == true` with active workout ID $W$),
• *When* viewing workout $W$ in aftermath list views,
• *Then* the "Mark as finished" option SHALL NOT be accessible.
• *Given* an already finished workout (`finished == true`),
• *Then* the "Mark as finished" option SHALL NOT be present in the context menu.

**Invariants**: Recorded raw samples, trackpoints, laps, sensor extrema, and base file names MUST NOT be modified or corrupted; cascading deletion (`REQ-UI-145`) MUST remain available for all non-actively tracked workouts; single-workout deletion flow MUST remain intact.

### REQ-UI-147: Compact Workout Summary Context Menu Streamlining [Verified]
- **Tests**: `TST-UI-100`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
4. *Preservation of Full Summary Editing*: The expanded workout summary card (`WorkoutSummary.kt` via `WorkoutHeader.kt`) SHALL retain its full workout editing capabilities (`onEditWorkout`) intact.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an athlete viewing workouts in compact view (`WorkoutSummaryCompact`),
• *When* the athlete long-presses an idle finished workout,
• *Then* the context menu SHALL display ONLY the "Delete" option and SHALL NOT display "Edit workout".
• *Given* an idle unfinished workout in compact view,
• *When* long-pressed,
• *Then* the context menu SHALL display "Mark as finished" and "Delete", and SHALL NOT display "Edit workout".
• *Given* an actively tracked workout in compact view,
• *When* long-pressed,
• *Then* NO context menu SHALL be shown (`hasContextMenu == false`).
• *Given* the expanded view (`WorkoutSummary`),
• *When* viewed or edited,
• *Then* workout editing functionality SHALL remain fully functional.

**Invariants**: Workout deletion (`REQ-UI-145`), mark as finished (`REQ-UI-146`), and expanded workout editing MUST NOT be altered.
- **Rationale**: The system SHALL streamline the long-press context menu of the compact workout summary (`WorkoutSummaryCompact.kt`) by eliminating the "Edit workout" action and scoping the compact context menu strictly to lifecycle management actions:
1. *Removal of Edit Action*: `WorkoutSummaryCompact.kt` SHALL NOT render an "Edit workout" (`@string/edit_workout`) item in its dropdown context menu and SHALL NOT require an `onEditWorkout` callback parameter.
2. *Permitted Compact Context Menu Actions*: The compact context menu SHALL contain only:
• "Als abgeschlossen markieren" / "Mark as finished" (`@string/mark_as_finished`) when the workout is unfinished and not actively tracked (`canMarkFinished == true`).
• "Löschen" / "Delete" (`@string/delete`) when the workout is not actively tracked (`canDelete == true`).
3. *Context Menu Trigger Condition*: The long-press trigger for `WorkoutSummaryCompact` SHALL be governed strictly by `hasContextMenu = canDelete

### REQ-UI-148: Standardized Edge-to-Edge Bottom Sheet Insets & Core UI Design Components [Verified]
- **Tests**: `TST-UI-101`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `AppModalBottomSheet.kt`, `AppTableHeader.kt`, `AppTableTypography.kt`, `MinimumDragHandle.kt`, `FastScrollbar.kt`, `BadgeBox.kt`, `RichStatsSheet.kt`, `WorkoutLaps.kt`, `WorkoutExtrema.kt`
- **Rationale**: The system SHALL provide a centralized design system package (`com.atrainingtracker.trainingtracker.ui.components.core`) and harmonize edge-to-edge system bar window insets across all modal bottom sheets and tabular headers:
1. *Universal Modal Bottom Sheet (`AppModalBottomSheet`)*:
• The system SHALL provide a reusable Material 3 `ModalBottomSheet` container (`AppModalBottomSheet.kt`) wrapping standard bottom sheet mechanics.
• The sheet container SHALL automatically apply `navigationBarsPadding()` and `imePadding()` to guarantee that content and bottom action bars do not overlap the Android navigation bar or keyboard on gesture and 3-button navigation devices.
• The sheet SHALL provide a standard drag handle (`MinimumDragHandle`), a structured header row displaying an optional leading icon, localized title (`MaterialTheme.typography.titleLarge`), and optional close/dismiss button (`Icons.Default.Close`).
• The sheet SHALL provide an adaptive scrollable body column and an optional bottom action bar slot separated by a subtle divider.
2. *Edge-to-Edge Bottom Sheet Insets Harmonization*:
• In `RichStatsSheet.kt`, hardcoded bottom padding (`padding(bottom = 32.dp)`) SHALL be replaced with adaptive `navigationBarsPadding()`, preventing clipping on devices with tall 3-button navigation bars and eliminating dead whitespace on gesture navigation devices.
• All modal bottom sheets (`FilterBottomSheetScaffold`, `LapEditBottomSheet`, `RichStatsSheet`, `AppModalBottomSheet`) SHALL uniformly anchor to the bottom navigation bar without bottom gap or clipping artifacts.
3. *Shared Tabular Header Component & Typography (`AppTableHeader`, `AppTableTypography`)*:
• The system SHALL extract a standardized tabular header composable (`AppTableHeader.kt`) and reusable typography style (`AppTableTypography.kt`) unifying `labelSmall` with `PlatformTextStyle(includeFontPadding = false)` and `LineHeightStyle(Alignment.Bottom, Trim.Both)` to ensure consistent bottom baseline alignment across data tables.
• `WorkoutLaps.kt` (`LapTableHeader`) SHALL be refactored to use `AppTableHeader` and `AppTableTypography`, preserving exact column weights (1.6f, 0.85f, 0.85f, 1.1f, 26.dp) and localized strings.
4. *Component Consolidation in `ui.components.core`*:
• Universal UI primitives (`AppModalBottomSheet`, `AppTableHeader`, `AppTableTypography`, `MinimumDragHandle`, `FastScrollbar`, `BadgeBox`) SHALL be housed in `com.atrainingtracker.trainingtracker.ui.components.core` as the foundation for modernizing dialogs (ATT-900).

**Acceptance Criteria (Given-When-Then)**:
• *Given* any device running gesture navigation or 3-button navigation,
• *When* `AppModalBottomSheet` or `RichStatsSheet` is displayed,
• *Then* content and bottom controls SHALL render above the system navigation bar with zero clipping and zero hardcoded bottom gaps.
• *Given* `LapTableHeader` in `WorkoutLaps.kt`,
• *When* rendered,
• *Then* column labels SHALL use `AppTableHeader` / `AppTableTypography` with bottom baseline alignment matching `WorkoutExtrema`.
• *Given* the `ui.components.core` package,
• *Then* it SHALL provide `AppModalBottomSheet`, `AppTableHeader`, `AppTableTypography`, `MinimumDragHandle`, `FastScrollbar`, and `BadgeBox` with clean API contracts.

**Invariants**: Filter bottom sheet scaffold behavior (`REQ-UI-133`, `REQ-UI-134`), status bar boundary limits on map bottom sheets (`REQ-SET-069`), live tracking lap summary HUD dialog (`LapSummaryDialog`), and lap table formatting (`REQ-UI-143`) MUST NOT be altered or regressed.

### REQ-UI-149: Modernized Modal Bottom Sheet Dialogs & Selection Flows [Verified]
- **Tests**: `TST-UI-102`, `TST-SEN-031`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: Modernize mobile ergonomics by transitioning interactive selection, configuration, nudge, and history popups to edge-to-edge modal bottom sheets (AppModalBottomSheet) while keeping transient HUDs and destructive alerts as centered dialogs.
- **Rationale**: The system SHALL modernize interactive configuration, settings, editing, and device/sensor selection dialogs into bottom popups backed by `AppModalBottomSheet`, improving one-handed mobile ergonomics and edge-to-edge system navigation support, while strictly preserving transient tracking HUDs and destructive alerts as centered modal dialogs:
1. *Drawer Settings Modernization*:
• `ExportSettingsDialog.kt` (`ExportSettingsDialogFragment`) SHALL compose `AppModalBottomSheet` displaying file export format toggles (TCX, GPX, Golden Cheetah JSON, CSV) with leading export icon (`Icons.Default.Upload`), title (`@string/prefs_Export`), close button, and confirm action button (`@string/Done`).
• `UnitsSettingsDialog.kt` (`UnitsSettingsDialogFragment`) SHALL compose `AppModalBottomSheet` displaying the global unit system selection (Metric vs. Imperial) with leading unit icon (`Icons.Default.SquareFoot`), title (`@string/prefsUnitsTitle`), close button, dismiss/cancel button, and confirm action button (`@string/Done`).
• `DisplaySettingsDialog.kt` (`DisplaySettingsDialogFragment`) SHALL compose `AppModalBottomSheet` displaying display behavior toggles (force portrait, keep screen on, keep screen unlocked) with leading display icon (`Icons.Default.DisplaySettings`), title (`@string/Display`), close button, and confirm action button (`@string/Done`).
• `ActivityTypeSelectionDialog.kt` (`ActivityTypeSelectionDialogFragment` / `ActivityTypeSelectionHelper`) SHALL compose `AppModalBottomSheet` displaying the scrollable list of activity types with leading edit icon (`Icons.Default.Edit`), un-tinted authentic sport logos (`REQ-UI-114`), localized names, and close/cancel button.
2. *Tracking & Hardware Selection Modernization*:
• `SensorSourceDialog.kt` SHALL compose `AppModalBottomSheet` displaying the multi-tiered sensor telemetry hierarchy (Source Device, Active Backups, Not Connected) with leading sensor icon, localized title, close button, and clickable device rows opening device settings.
• `DeviceTypeSelectionDialog.kt` SHALL compose `AppModalBottomSheet` displaying available protocol hardware types (ANT+ / BLE) with leading protocol/device icon, localized name, and "All Devices" confirm button.
3. *Tracking Grid & Sensor Field Modernization*:
• `EditSensorFieldDialog.kt` SHALL compose `AppModalBottomSheet` displaying sensor type, source device, and text size selection with save and cancel actions.
• `ConfigureFilterDialog.kt` SHALL compose `AppModalBottomSheet` displaying smoothing filter parameters (filter type, smoothing factor alpha) with save and cancel actions.
4. *Sport Types & Equipment Modernization*:
• `EditSportTypeDialog.kt` SHALL compose `AppModalBottomSheet` displaying sport name, speed thresholds, base sport selector, and sensor requirements.
• `EditEquipmentDialog.kt` SHALL compose `AppModalBottomSheet` displaying equipment name, bike type selector, and multi-select sport types/sensors.
• `EditDeviceDialog.kt` SHALL compose `AppModalBottomSheet` displaying device technical status LED, name, protocol, calibration, wheel circumference, and unpairing action.
5. *Workout Clusters & Export Status Modernization*:
• `WorkoutClusterSelectionDialog` and `EditWorkoutClusterDialog` / `EditWorkoutClusterIdentityDialog` SHALL compose `AppModalBottomSheet` for cluster candidate selection and cluster identity editing.
• `ClusterNamingDialog` in `ImportBackupTabsScreen.kt` SHALL compose `AppModalBottomSheet` (bottom popup) replacing the centered `AlertDialog`, integrating route preview with `DarkMapAntiFlashOverlay`, queue count badge in header, editable Workout Name (`OutlinedTextField`), and Rule 73 stacked full-width action buttons (Primary "Speichern" / `@string/save` and Secondary `@string/cluster_naming__leave_unclustered`) (ATT-5560).
• `ClusterInfoDialog.kt` SHALL compose `AppModalBottomSheet` displaying cluster tolerance and tuning parameter guidance.
• `ExportDetailsDialog` in `ExportStatusGroup.kt` SHALL compose `AppModalBottomSheet` displaying per-service upload status and details.
6. *AppModalBottomSheet Architectural Enhancement*:
• `AppModalBottomSheet.kt` SHALL support both vector icons (`icon: ImageVector? = null`) and drawable painter icons (`iconPainter: Painter? = null`) to cleanly accommodate Android resource drawables across all bottom sheet headers.
7. *Preserved Modal Dialog Invariants*:
• `LapSummaryDialog` MUST remain an auto-dismissing (3-second) centered HUD `Dialog` during active tracking.
• Destructive confirmation dialogs (`DeleteConfirmationDialog`, `WorkoutDeleteDialog`, `DeleteOldWorkoutsDialog`), deletion progress dialogs (`WorkoutDeletionProgressDialog`), and system redirects (`GPSDisabledDialog`, `StartOrResumeDialog`) MUST remain centered `AlertDialog`s.
8. *Monetization, Nudge & History Dialog Modernization (`ATT-5399`, `ATT-5470`, `ATT-5468`, `ATT-5661`)*:
• `SwapOrUpgradeDialog.kt` SHALL compose `AppModalBottomSheet` displaying candidate devices for swap, smart recommendation, and upgrade navigation (`NavRoutes.SUPPORTER`) with swap action button, appreciative support development action (*'Entwicklung unterstützen'*, `@string/swap_dialog_action_unlock`), and dismiss action (and stacked full-width layout per Rule 73, ATT-5470, ATT-5661).
• `KeepTwoSensorsBottomSheet.kt` SHALL compose `AppModalBottomSheet` guiding athletes to retain up to 2 active sensors with appreciative base version copy and support development CTA (*'Entwicklung unterstützen'*, `@string/keep_two_sensors_action_unlock`) (ATT-5661).
• `GarageUpgradeNudgeDialog.kt` SHALL compose `AppModalBottomSheet` displaying dynamic gear counts and upgrade navigation to Gold Pro or Diamond Sponsor with dismiss action (and stacked full-width layout per Rule 73, ATT-5468).
• `FirstJubileeCelebrationDialog.kt` SHALL compose `AppModalBottomSheet` displaying milestone achievements, tier-gated engagement actions, and dismiss action.
• `ZoneHistoryDialog.kt` SHALL compose `AppModalBottomSheet` with `scrollable = false` for the outer sheet container displaying the zone snapshot list (`LazyColumn`), and `ZoneSnapshotEditDialog` SHALL compose `AppModalBottomSheet(scrollable = true)` for editing individual snapshot boundaries.

**Acceptance Criteria (Given-When-Then)**:
• *Given* the navigation drawer in `MainActivityWithNavigation`,
• *When* the athlete taps Export, Units, Display Settings, or Tracking Layouts,
• *Then* the respective options SHALL present as an `AppModalBottomSheet` anchored to the screen bottom with `MinimumDragHandle` and `navigationBarsPadding()`.
• *Given* `SensorStatus` in the tracking screen,
• *When* the athlete taps a sensor status tile,
• *Then* `SensorSourceDialog` SHALL present as an `AppModalBottomSheet` with the sensor's icon and telemetry breakdown.
• *Given* `ControlTrackingScreen` during device pairing,
• *When* the athlete selects ANT+ or Bluetooth pairing,
• *Then* `DeviceTypeSelectionDialog` SHALL present as an `AppModalBottomSheet`.
• *Given* tracking grid or equipment/sport/device editing,
• *When* invoked,
• *Then* the corresponding configuration sheet SHALL present as an `AppModalBottomSheet`.
• *Given* an active workout recording where a new lap is triggered,
• *Then* `LapSummaryDialog` SHALL appear as a centered overlay and dismiss after 3 seconds.

**Invariants**: SharedPreferences keys (`SP_UNITS`, `pref_display_options`, export flags) and persistence logic MUST remain unaltered; sport icons MUST preserve authentic non-tinted colors (`REQ-UI-114`); `LapSummaryDialog` 3s HUD behavior MUST remain intact.

### REQ-UI-150: Standardized Bottom Sheet Action Layouts, Semantic Action Verbs & Route Dialog Parity [Verified]
- **Tests**: `TST-UI-103`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `AppModalBottomSheet.kt`, `AppDialogActions.kt`, `EditRouteDialog.kt`, `RoutesFragment.kt`, `GpxImportActivity.kt`, `EditDeviceDialog.kt`, `EditSensorFieldDialog.kt`, `ConfigureFilterDialog.kt`, `UnitsSettingsDialog.kt`, `ExportSettingsDialog.kt`, `DisplaySettingsDialog.kt`, `ImportBackupTabsScreen.kt`
- **Rationale**: The system SHALL standardize bottom action bars, button arrangements, and semantic action verbs across all application bottom popups (`AppModalBottomSheet`), and SHALL eliminate full-screen editing dialog outliers by migrating `EditRouteScreen` to an `AppModalBottomSheet`:
1. *Core Action Bar Architecture (`AppDialogActions`)*:
• The system SHALL provide reusable action composables in `com.atrainingtracker.trainingtracker.ui.components.core.AppDialogActions`:
- `SaveCancel`: Two-button horizontal action bar comprising a secondary `TextButton` ("Abbrechen" / `@string/Cancel`), flexible space separation, and a primary filled `Button` ("Speichern" / `@string/save`) with configurable `enabled` state.
- `Confirm`: Single primary filled `Button` ("OK" / `@android:string/ok`) aligned to the right by default.
- `CancelOnly`: Single secondary `TextButton` ("Abbrechen" / `@string/Cancel`) filling horizontal width (`fillMaxWidth()`).
2. *Semantic Action Verb Enforcement & Transactional Staging*:
• Whenever a dialog persists, updates, or commits data changes to ViewModel, database, or SharedPreferences upon clicking the primary action button, the primary button SHALL display the verb "Speichern" (`@string/save`), and the dialog SHALL stage modifications locally until "Speichern" is tapped. The system MUST NOT display "OK" or "Done" on data-mutating buttons.
Specifically:
- `DisplaySettingsDialog.kt`: SHALL stage display preferences locally and use `AppDialogActions.SaveCancel` with `@string/save` ("Speichern"). Reverting changes when dismissed or "Abbrechen" tapped.
- `ExportSettingsDialog.kt`: SHALL stage export preferences locally and use `AppDialogActions.SaveCancel` with `@string/save` ("Speichern"). Reverting changes when dismissed or "Abbrechen" tapped.
- `EditDeviceDialog.kt`: Primary button SHALL display `@string/save`.
- `EditSensorFieldDialog.kt`: Primary button SHALL display `@string/save`.
- `ConfigureFilterDialog.kt`: Primary button SHALL display `@string/save`.
- `UnitsSettingsDialog.kt`: Primary button SHALL display `@string/save` instead of `@string/Done`.
- `ImportBackupTabsScreen.kt` (Cluster-Parameter): Primary button SHALL display `@string/save`.
- `EditEquipmentDialog.kt`, `EditSportTypeDialog.kt`, and `WorkoutClusterHeatmapScreen.kt`: SHALL continue to display `@string/save`.
• Whenever a dialog provides informational acknowledgment without deferred data staging, the action button SHALL display "OK" (`@android:string/ok` or `@string/OK`) aligned to the right.
Specifically:
- `SensorSourceDialog.kt`, `ExportDetailsDialog`, and `ClusterInfoDialog.kt`: SHALL display right-aligned `@android:string/ok` or `@string/OK`.
3. *Full-Screen Outlier Elimination (`EditRouteDialog`)*:
• `EditRouteScreen.kt` (full-screen `Scaffold` with `TopAppBar`) SHALL be refactored into `EditRouteDialog.kt` presenting as an `AppModalBottomSheet` with route icon, localized title `@string/route_edit`, close dismiss icon, scrollable form body, and standard `AppDialogActions.SaveCancel` bottom action bar.
• Callers in `RoutesFragment.kt` and `GpxImportActivity.kt` SHALL invoke the dialog as a modal bottom sheet, preserving existing `onSave` and `onCancel` flows.
4. *Standard Bottom Sheet Adoption in Import Tuning*:
• In `ImportBackupTabsScreen.kt`, the Cluster-Parameter tuning sheet SHALL compose `AppModalBottomSheet` with `AppDialogActions.SaveCancel`, replacing the raw `ModalBottomSheet` and removing the `OutlinedButton`.
5. *Localization Parity*:
• All action buttons across all dialogs SHALL be fully localized across all 9 supported locales (EN, DE, ES, FR, IT, JA, NL, PL, PT).
6. *System Invariants & Preserved Behavior*:
• Data persistence logic, ViewModel state holding, database transactions, SharedPreferences keys, route summary updating, filter type conversions, and device pairing/calibration MUST NOT be altered or regressed.
• Bottom sheet drag handles (`MinimumDragHandle`), system insets (`navigationBarsPadding()`, `imePadding()`), and scrim dismiss gestures MUST be preserved across all sheets.

**Acceptance Criteria (Given-When-Then)**:
• *Given* any data-storing bottom sheet (Edit Device, Edit Sensor Field, Configure Filter, Units Settings, Edit Equipment, Edit Sport Type, Edit Route, Cluster Parameter, Edit Workout Name, Display Settings, Export Settings),
• *When* rendered,
• *Then* the bottom action bar SHALL display a borderless text button "Abbrechen" on the left and a filled primary button "Speichern" on the right.
• *Given* an informational bottom sheet (Sensor Source, Export Details, Cluster Info),
• *When* rendered,
• *Then* the bottom action bar SHALL display a right-aligned button with text "OK" and SHALL NOT display "Done".
• *Given* a user editing a route in `RoutesFragment` or `GpxImportActivity`,
• *When* edit route is invoked,
• *Then* the route editor SHALL present as a modal bottom sheet (`AppModalBottomSheet`) anchored above the navigation bar instead of a full-screen scaffold.
• *Given* `ImportBackupTabsScreen` when cluster parameter tuning is opened,
• *When* rendered,
• *Then* it SHALL use `AppModalBottomSheet` and standard text button "Abbrechen" + filled button "Speichern" without `OutlinedButton`.

**Invariants**: Database schemas, ViewModel save implementations, route coordinate data, SharedPreferences preferences, authentic un-tinted sport icons, and system window insets MUST NOT be altered.

### REQ-UI-151: Workout Editing Modal Bottom Sheet & Fragment Viewport Layering [Verified]
- **Tests**: `TST-UI-104`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `EditWorkoutScreen.kt`, `EditWorkoutDialog.kt`, `WorkoutSummariesListFragment.kt`, `WorkoutSummariesTabbedFragment.kt`, `PeriodsFragment.kt`, `WorkoutClustersFragment.kt`
- **Rationale**: The system SHALL eliminate the full-screen `Scaffold` outlier in workout editing by refactoring `EditWorkoutScreen` into a standardized modal bottom sheet (`EditWorkoutDialog` / `AppModalBottomSheet`), and SHALL optimize fragment caller viewport composition so background lists and maps remain visible behind the modal scrim:
1. *Modal Bottom Sheet Architecture (`EditWorkoutDialog`)*:
• The workout editor SHALL compose `AppModalBottomSheet` with leading edit icon (`Icons.Default.Edit`), localized title (`@string/edit_workout`), dismiss handling via scrim tap / drag handle / close icon, and window insets (`navigationBarsPadding()`, `imePadding()`).
• The editor SHALL retain all 8 workout input sections within a vertically scrollable body (`verticalScroll`): Workout Name, Route / Cluster assignment, Sport & Equipment spinners (`DropdownSelector`), Commute and Trainer flags, Strava individual upload toggle (when Strava is configured), Description (`minLines = 3`), Goal, and Method.
• The Route/Cluster field SHALL continue to support launching `EditWorkoutClusterDialog` as a nested modal bottom sheet and unassigning routes.
2. *Standard Action Bar Integration (`AppDialogActions`)*:
• The bottom sheet SHALL integrate `AppDialogActions.SaveCancel`:
- Secondary action: borderless `TextButton` ("Abbrechen" / `@string/Cancel`) invoking dismiss without persisting.
- Primary action: filled `Button` ("Speichern" / `@string/save`) invoking `viewModel.saveChanges()` followed by dismiss.
3. *Caller Viewport Layering Optimization*:
• In `WorkoutSummariesListFragment`, `WorkoutSummariesTabbedFragment`, `PeriodsFragment`, and `WorkoutClustersFragment`, the active underlying screen content (`WorkoutList`, `TrackOnMapScreen`, `WorkoutTabsScreen`, `PeriodMapScreen`, `PeriodList`, `WorkoutClustersScreen`) SHALL remain composed and active in the view hierarchy when workout editing is invoked (`selectedWorkoutIdForEdit != null` / `editedWorkoutId != null`), presenting `EditWorkoutDialog` as an overlay to ensure fluid presentation without empty/blank background artifacts.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an athlete tapping "Edit workout" in any workout summary or list,
• *When* the workout editor is displayed,
• *Then* it SHALL present as an `AppModalBottomSheet` anchored above the navigation bar with "Abbrechen" on the left and "Speichern" on the right, and the underlying list or map view SHALL remain visible beneath the translucent scrim.
• *Given* an athlete tapping the Route/Cluster field in the editor,
• *When* tapped,
• *Then* `EditWorkoutClusterDialog` SHALL open as a nested modal bottom sheet without dismissing the workout editor.
• *Given* an athlete tapping "Abbrechen", swiping down, or tapping outside,
• *When* dismissed,
• *Then* changes SHALL be discarded and `viewModel.saveChanges()` SHALL NOT be called.
• *Given* an athlete tapping "Speichern",
• *When* saved,
• *Then* `viewModel.saveChanges()` SHALL be called to commit changes to the database and the sheet SHALL dismiss.

**Invariants**: Database persistence logic in `WorkoutRepository`, `EditWorkoutViewModel` state holding, sport and equipment selection rules (`REQ-UI-130`), cluster creation and matching heuristics, and IME keyboard padding MUST NOT be altered or regressed.

### REQ-UI-152: Dropbox Settings Modal Bottom Sheet & Navigation Integration [Verified]
- **Tests**: `TST-UI-105`, `TST-UI-395`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `DropboxSettingsDialog.kt`, `DropboxSettingsDialogFragment.kt`, `MainActivityWithNavigation.kt`, `ImportBackupTabsScreen.kt`
- **Rationale**: The system SHALL eliminate the full-screen fragment outlier for Dropbox settings by replacing `CloudUploadFragment` with a standardized modal bottom sheet (`DropboxSettingsDialog` / `DropboxSettingsDialogFragment` backed by `AppModalBottomSheet`), integrating with the navigation drawer and preserving all backup and authentication mechanics:
1. *Modal Bottom Sheet Architecture (`DropboxSettingsDialog`)*:
• The dialog SHALL compose `AppModalBottomSheet` with authentic Dropbox logo (`R.drawable.dropbox_logo_blue` via `iconPainter`, `iconTint = Color.Unspecified`), localized title (`@string/Dropbox`), dismiss handling (drag handle, scrim tap, close icon), and window insets (`navigationBarsPadding()`, `imePadding()`).
• *Connection Section*: The dialog SHALL display the current connection state (`@string/dropbox_connected_status` in green vs. `@string/dropbox_disconnected_status` in error color). When disconnected, it SHALL provide a Dropbox-blue button (`@string/dropbox_connect`) initiating OAuth PKCE via `Auth.startOAuth2PKCE(...)`. To ensure reliable OAuth launch when hosted in themed or wrapped Compose hierarchies, the dialog SHALL recursively unwrap `LocalContext.current` via `Context.findActivity(): Activity?` (traversing `ContextWrapper.baseContext`) before invoking `Auth.startOAuth2PKCE` [ATT-4588]. When connected, it SHALL provide a button (`@string/dropbox_disconnect`) revoking credentials via `TrainingApplication.deleteDropboxCredential()` and `TrainingApplication.setUploadToDropbox(false)`.
• *Automated Backups Section*: The dialog SHALL display an interactive switch for automated backups (`@string/automated_backups` with summary `@string/automated_backups_summary`, defaulting to `true`), and a backup interval selector (`@string/backup_interval`, options: Daily, Every 3 days, Weekly, Monthly from `@array/backup_interval_entries`, defaulting to `"1"`), enabled only when automated backups are enabled.
2. *Standard Action Bar Integration (`AppDialogActions`)*:
• The bottom sheet SHALL integrate `AppDialogActions.SaveCancel`:
- Secondary action: borderless `TextButton` ("Abbrechen" / `@string/Cancel`) dismissing the dialog and discarding uncommitted backup preference changes.
- Primary action: filled `Button` ("Speichern" / `@string/save`) committing `automated_backups` and `backup_interval_days` to `SharedPreferences`, immediately invoking `BackupWorker.schedule(context)` to reschedule WorkManager periodic backup tasks, and dismissing the dialog.
3. *DialogFragment Host & Single-Authority Credential Capture (`DropboxSettingsDialogFragment` / `DropboxSettingsDialog`)*:
• The dialog SHALL be hosted by `DropboxSettingsDialogFragment` extending `AppBottomSheetDialogFragment`. To eliminate race conditions across static `AuthActivity.result` consumers, credential capture from `Auth.getDbxCredential()` SHALL be centralized exclusively in `DropboxSettingsDialog` (via `Lifecycle.Event.ON_RESUME` observer) as the single authority, immediately updating Compose state `isConnected` and persisting credentials via `TrainingApplication.storeDropboxCredential()` and `TrainingApplication.setUploadToDropbox(true)` [ATT-4588]. Redundant credential consumption in `DropboxSettingsDialogFragment.onResume()` SHALL be omitted.
4. *Navigation Drawer & Preference Routing*:
• In `MainActivityWithNavigation.kt`, selecting `R.id.drawer_dropbox` SHALL close the navigation drawer and present `DropboxSettingsDialogFragment` without replacing the main screen fragment. Preference screen routing for `"cloudUpload"` in `onPreferenceStartScreen` SHALL launch `DropboxSettingsDialogFragment`.
5. *Reactive Cloud Connection State & Dual-Install Scheme Isolation (`ImportBackupTabsScreen.kt` & `AndroidManifest.xml`)*:
• In `ImportBackupTabsScreen.kt`, `isDropboxConnected` (and `isGoogleDriveConnected`) SHALL reactively refresh upon `Lifecycle.Event.ON_RESUME` (or preference change) so manual backup controls and summaries update immediately upon returning from settings [ATT-4588].
• The Dropbox OAuth redirect scheme (`db-iknmdmr31sf64r0`) registration in `AndroidManifest.xml` SHALL isolate OAuth redirect handling between build variants or document dual-install disambiguation [ATT-4588].

**Acceptance Criteria (Given-When-Then)**:
• *Given* an athlete opening the navigation drawer,
• *When* "Dropbox" is selected,
• *Then* `DropboxSettingsDialog` SHALL present as an `AppModalBottomSheet` anchored to the screen bottom with "Abbrechen" and "Speichern", leaving the background screen visible beneath the scrim.
• *Given* `DropboxSettingsDialog` hosted within a `ContextWrapper` / `ContextThemeWrapper`,
• *When* the athlete taps "Mit Dropbox verknüpfen",
• *Then* `Context.findActivity()` SHALL resolve the underlying `Activity` and launch `Auth.startOAuth2PKCE(...)` without `null` failure [ATT-4588].
• *Given* completion of browser authorization,
• *When* returning to `DropboxSettingsDialog`,
• *Then* `DropboxSettingsDialog` SHALL uniquely capture `Auth.getDbxCredential()` without dual-consumer race conditions and immediately transition `isConnected` to true [ATT-4588].
• *Given* `ImportBackupTabsScreen`,
• *When* returning from `DropboxSettingsDialog` after connecting or disconnecting Dropbox,
• *Then* `isDropboxConnected` SHALL immediately refresh on `ON_RESUME` without requiring screen recreation [ATT-4588].
• *Given* an athlete modifying the backup interval or toggle,
• *When* "Abbrechen" is tapped,
• *Then* changes SHALL be discarded without mutating `SharedPreferences`.
• *Given* an athlete modifying backup preferences and tapping "Speichern",
• *When* saved,
• *Then* preferences SHALL be committed to `SharedPreferences`, `BackupWorker.schedule()` SHALL be called, and the sheet SHALL dismiss.

**Invariants**: Dropbox OAuth PKCE redirect scheme (`db-iknmdmr31sf64r0`), `SP_DROPBOX_CREDENTIAL` and `SP_UPLOAD_TO_DROPBOX` keys, `automated_backups` and `backup_interval_days` keys in `SharedPreferences`, and un-tinted Dropbox logo colors MUST NOT be altered.

### REQ-UI-153: Strava Settings Modal Bottom Sheet & Navigation Integration [Verified]
- **Tests**: `TST-UI-106`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `StravaSettingsDialog.kt`, `StravaSettingsDialogFragment.kt`, `MainActivityWithNavigation.kt`
- **Rationale**: The system SHALL eliminate the full-screen fragment outlier for Strava settings by replacing `StravaUploadFragment` with a standardized modal bottom sheet (`StravaSettingsDialog` / `StravaSettingsDialogFragment` backed by `AppModalBottomSheet`), integrating with the navigation drawer and preserving all connection, synchronization, and selective upload mechanics:
1. *Modal Bottom Sheet Architecture (`StravaSettingsDialog`)*:
• The dialog SHALL compose `AppModalBottomSheet` with authentic Strava logo (`R.drawable.logo_square_strava` via `iconPainter`, `iconTint = Color.Unspecified`), localized title (`@string/Strava`), dismiss handling (drag handle, scrim tap, close icon), and window insets (`navigationBarsPadding()`, `imePadding()`).
• *Connection Section*: The dialog SHALL display the current connection state (`@string/strava_connected_status` in green vs. `@string/strava_disconnected_status` in error color). When connecting, it SHALL display a progress indicator with `@string/please_wait`. When disconnected, it SHALL provide a button initiating OAuth via `StravaHelper.requestAccessToken(context)`. When connected, it SHALL provide a disconnect button (`@string/strava_disconnect`) styled with `errorContainer` / `onErrorContainer`, revoking tokens via `TrainingApplication.deleteStravaToken()`, executing `StravaDeauthorizationThread`, and updating UI state. Upon successful token exchange (`StravaAuthState.Success`), it SHALL trigger initial sync of equipment (`StravaEquipmentSynchronizeThread`), segments (`SegmentsRepository.syncSegmentsAsync`), and routes (`RoutesRepository.syncRoutesFromStravaAsync`), resetting auth state.
• *Manual Synchronization Section (visible when connected)*: The dialog SHALL provide actions to synchronize Strava equipment (`@string/updateStravaEquipment`) and Strava routes (`@string/updateStravaRoutes`), displaying their respective last updated timestamps (`TrainingApplication.getLastUpdateTimeOfStravaEquipment()`, `TrainingApplication.getLastUpdateTimeOfStravaRoutes()`). Tapping triggers background synchronization threads/coroutines.
• *Selective Upload Section (visible when connected)*: The dialog SHALL display interactive toggles for selective telemetry upload (`@string/selectiveUpload`) including GPS (`uploadStravaGPS`), Altitude (`uploadStravaAltitude`), Heart Rate (`uploadStravaHR`), Power (`uploadStravaPower`), and Cadence (`uploadStravaCadence`), all defaulting to `true`.
2. *Standard Action Bar Integration (`AppDialogActions`)*:
• The bottom sheet SHALL integrate `AppDialogActions.SaveCancel`:
- Secondary action: borderless `TextButton` ("Abbrechen" / `@string/Cancel`) dismissing the dialog and discarding uncommitted selective upload preference modifications.
- Primary action: filled `Button` ("Speichern" / `@string/save`) committing staged selective upload preferences to `SharedPreferences` and dismissing the dialog.
3. *DialogFragment Host (`StravaSettingsDialogFragment`)*:
• The dialog SHALL be hosted by `StravaSettingsDialogFragment` extending `DialogFragment` with `STYLE_NORMAL, android.R.style.Theme_Translucent_NoTitleBar`.
4. *Navigation Drawer & Preference Routing*:
• In `MainActivityWithNavigation.kt`, selecting `R.id.drawer_strava` SHALL close the navigation drawer and present `StravaSettingsDialogFragment` without replacing the main screen fragment. Preference screen routing for `TrainingApplication.PREFERENCE_SCREEN_STRAVA` in `onPreferenceStartScreen` SHALL launch `StravaSettingsDialogFragment`.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an athlete opening the navigation drawer,
• *When* "Strava" is selected,
• *Then* `StravaSettingsDialog` SHALL present as an `AppModalBottomSheet` anchored to the screen bottom with "Abbrechen" and "Speichern", leaving the background screen visible beneath the scrim.
• *Given* an athlete tapping "Mit Strava verknüpfen" in the dialog,
• *When* OAuth authorization completes via browser and `StravaOAuthCallbackActivity`,
• *Then* `StravaSettingsDialog` SHALL reflect connected status and trigger background synchronizations.
• *Given* an athlete modifying selective upload toggles,
• *When* "Abbrechen" is tapped,
• *Then* changes SHALL be discarded without mutating `SharedPreferences`.
• *Given* an athlete modifying selective upload toggles and tapping "Speichern",
• *When* saved,
• *Then* preferences SHALL be committed to `SharedPreferences` and the sheet SHALL dismiss.

**Invariants**: Strava OAuth callback URL prefix `strava://rainerblind.github.io`, `SP_STRAVA_TOKEN` and token expiration handling, `uploadStravaGPS`, `uploadStravaAltitude`, `uploadStravaHR`, `uploadStravaPower`, and `uploadStravaCadence` preference keys in `SharedPreferences`, and un-tinted Strava logo colors MUST NOT be altered.

### REQ-UI-154: Search Settings Modal Bottom Sheet & Navigation Integration [Verified]
- **Tests**: `TST-UI-107`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `SearchSettingsDialog.kt`, `SearchSettingsDialogFragment.kt`, `MainActivityWithNavigation.kt`
- **Rationale**: The system SHALL eliminate the full-screen fragment outlier for sensor search settings by replacing `SearchSettingsFragment` with a standardized modal bottom sheet (`SearchSettingsDialog` / `SearchSettingsDialogFragment` backed by `AppModalBottomSheet`), integrating with the navigation drawer and preserving all search behavior, trigger conditions, and retry mechanics:
1. *Modal Bottom Sheet Architecture (`SearchSettingsDialog`)*:
• The dialog SHALL compose `AppModalBottomSheet` with search icon (`R.drawable.ic_search` via `iconPainter`), localized title (`@string/Search_Settings`), dismiss handling (drag handle, scrim tap, close icon), and window insets (`navigationBarsPadding()`, `imePadding()`).
• *Search Tries Section*: The dialog SHALL provide an interactive slider for the number of search attempts (`numberOfSearchTriesInt` / `TrainingApplication.SP_NUMBER_OF_SEARCH_TRIES_INT`) ranging from 1 to 5 (steps = 3, default: 3) with localized title `@string/prefsNumberOfSearchTriesTitle` and a dynamic numeric indicator displaying the staged value.
• *Trigger Section ("Suche starten bei" / `@string/prefStartSearchTitle`)*: The dialog SHALL provide interactive switches for automated search trigger events:
- `startSearchWhenAppStarts` (default: `true`, title: `@string/prefsStartSearchWhenAppStartsTitle`)
- `startSearchWhenResumeFromPaused` (default: `true`, title: `@string/prefsStartSearchWhenResumeFromPausedTitle`)
- `startSearchWhenUserChangesSport` (default: `true`, title: `@string/prefsStartSearchWhenUserChangesSportTitle`)
- `startSearchWhenTrackingStarts` (default: `false`, title: `@string/prefsStartSearchWhenTrackingStartsTitle`)
• *Behavior Section ("Suchverhalten" / `@string/prefs_search_behavior_title`)*: The dialog SHALL provide interactive switches with localized titles and summaries for search filtering behaviors:
- `searchOnlyForSportSpecificDevices` (default: `true`, title: `@string/prefsSearchOnlyForSportSpecificDevicesTitle`, summary: `@string/prefsSearchOnlyForSportSpecificDevicesSummary`)
- `changeSportWhenDeviceGetsLost` (default: `true`, title: `@string/prefsChangeSportWhenDeviceGetsLostTitle`, summary: `@string/prefsChangeSportWhenDeviceGetsLostSummary`)
2. *Standard Action Bar Integration (`AppDialogActions`)*:
• The bottom sheet SHALL integrate `AppDialogActions.SaveCancel`:
- Secondary action: borderless `TextButton` ("Abbrechen" / `@string/Cancel`) dismissing the dialog and discarding uncommitted search preference modifications.
- Primary action: filled `Button` ("Speichern" / `@string/save`) committing staged search preferences to `SharedPreferences` and dismissing the dialog.
3. *DialogFragment Host (`SearchSettingsDialogFragment`)*:
• The dialog SHALL be hosted by `SearchSettingsDialogFragment` extending `DialogFragment` with `STYLE_NORMAL, android.R.style.Theme_Translucent_NoTitleBar`.
4. *Navigation Drawer & Preference Routing*:
• In `MainActivityWithNavigation.kt`, selecting `R.id.drawer_search_settings` SHALL close the navigation drawer and present `SearchSettingsDialogFragment` without replacing the main screen fragment. Preference screen routing for `"search_settings"` in `onPreferenceStartScreen` SHALL launch `SearchSettingsDialogFragment`.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an athlete opening the navigation drawer,
• *When* "Such-Einstellungen" / "Search Settings" is selected,
• *Then* `SearchSettingsDialog` SHALL present as an `AppModalBottomSheet` anchored to the screen bottom with "Abbrechen" and "Speichern", leaving the background screen visible beneath the scrim.
• *Given* an athlete adjusting the search tries slider or modifying trigger/behavior switches,
• *When* "Abbrechen" is tapped,
• *Then* changes SHALL be discarded without mutating `SharedPreferences`.
• *Given* an athlete adjusting search preferences and tapping "Speichern",
• *When* saved,
• *Then* preferences SHALL be committed to `SharedPreferences` and the sheet SHALL dismiss.

**Invariants**: SharedPreferences keys `numberOfSearchTriesInt`, `startSearchWhenAppStarts`, `startSearchWhenResumeFromPaused`, `startSearchWhenUserChangesSport`, `startSearchWhenTrackingStarts`, `searchOnlyForSportSpecificDevices`, and `changeSportWhenDeviceGetsLost`, and `TrainingApplication` getters MUST NOT be altered.

### REQ-UI-155: Legacy Fragment Retirement, Unused Layout Purge & Obsolete String Resource Cleanup [Verified]
- **Tests**: `TST-UI-108`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `CloudUploadFragment.kt`, `SearchSettingsFragment.kt`, `StravaUploadFragment.kt`, `StarredSegmentsFragment.kt`, `MainActivityWithNavigation.kt`, `res/layout/*.xml`, `strings.xml`
- **Rationale**: The system SHALL eliminate technical debt and dead legacy UI artifacts resulting from modernizing settings and screens to Jetpack Compose:
1. *Dead Legacy Fragment Retirement*:
• The system SHALL delete deprecated, superseded fragment classes: `CloudUploadFragment.kt` (replaced by `DropboxSettingsDialog`), `SearchSettingsFragment.kt` (replaced by `SearchSettingsDialog`), and `StravaUploadFragment.kt` (replaced by `StravaSettingsDialog`).
• In `StarredSegmentsFragment.kt`, the system SHALL update `startStravaUploadFragment()` to `startStravaSettingsDialog()` invoking `StravaSettingsDialogFragment.newInstance().show(parentFragmentManager, StravaSettingsDialogFragment.TAG)`, removing the final reference to `StravaUploadFragment`.
• In `MainActivityWithNavigation.kt`, the system SHALL purge dead imports for `CloudUploadFragment`, `SearchSettingsFragment`, and `StravaUploadFragment`.
2. *Obsolete Layout XML Purge*:
• The system SHALL delete all 59 unused legacy View-based layout XML files in `app/src/main/res/layout/` superseded by Jetpack Compose screens and dialogs.
• The system SHALL preserve the 9 actively required layout resources: `check_ant_installation_dialog.xml`, `export_notification__expanded.xml`, `export_notification__group.xml`, `main_activity_with_navigation.xml`, `main_activity_without_navigation.xml`, `pebble_config_list_5.xml`, `preference_category.xml`, `preference_slim.xml`, and `tabbed_config_views.xml`.
3. *Obsolete String Resource Cleanup*:
• The system SHALL clean up dead, unreferenced string resources in `res/values/strings.xml` and translation resource files (`res/values-de/strings.xml`, etc.) associated with deleted layouts, legacy dialogs, and retired fragments.
4. *System Invariants & Non-Regression*:
• All unit tests (`./gradlew testDebugUnitTest`) MUST continue to pass with 0 failures and 0 regressions.
• Reflection contracts in `ModalBottomSheetDialogsIntegrityTest.kt` MUST remain satisfied.
• Runtime execution and hardware deployment (`./gradlew installDebug`) MUST function without layout inflation errors or missing string crashes.

**Acceptance Criteria (Given-When-Then)**:
• *Given* the modernized codebase on `feature/ATT-1018`,
• *When* searching for `CloudUploadFragment`, `SearchSettingsFragment`, or `StravaUploadFragment`,
• *Then* zero occurrences of these obsolete classes SHALL exist in the source code.
• *Given* `StarredSegmentsFragment` when Strava is not connected,
• *When* tapping "Connect to Strava",
• *Then* `StravaSettingsDialogFragment` SHALL open as a modal bottom sheet.
• *Given* `app/src/main/res/layout/`,
• *Then* exactly the 9 actively required layout files SHALL remain present, and all 59 unused layout files SHALL be purged.
• *Given* the test suite,
• *When* running `./gradlew testDebugUnitTest`,
• *Then* all tests SHALL pass with 0 failures and 0 regressions.

### REQ-UI-156: Bottom Popup Dialog Single-Window BottomSheetDialogFragment & Zero-Flicker Decor [Verified]
- **Tests**: `TST-UI-109`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `AppBottomSheetDialogFragment.kt`, `themes.xml`, `AppModalBottomSheet.kt`, `SearchSettingsDialogFragment.kt`, `ExportSettingsDialogFragment.kt`, `UnitsSettingsDialogFragment.kt`, `StravaSettingsDialogFragment.kt`, `DropboxSettingsDialogFragment.kt`, `ActivityTypeSelectionDialogFragment.kt`, `DisplaySettingsDialogFragment.kt`
- **Rationale**: The system SHALL standardize all 7 bottom popup dialog fragments on a single-window architecture by extending Google Material's `BottomSheetDialogFragment`, eliminating double-window nesting, pitch-black status bars, and navigation bar dismiss flickering:
1. *Dedicated Theme Overlay (`ThemeOverlay.aTrainingTracker.BottomSheetDialogFragment`)*:
• In `themes.xml`, the system SHALL define `ThemeOverlay.aTrainingTracker.BottomSheetDialogFragment` inheriting from `ThemeOverlay.MaterialComponents.BottomSheetDialog`.
• The theme overlay SHALL set `android:windowDrawsSystemBarBackgrounds` to `true`.
• The theme overlay SHALL explicitly set `android:navigationBarColor` and `android:statusBarColor` to `@android:color/transparent`.
• The theme overlay SHALL set `android:enforceNavigationBarContrast` and `android:enforceStatusBarContrast` to `false` (targetApi 29).
• The theme overlay SHALL set `android:windowLightNavigationBar` (targetApi 27) and `android:windowLightStatusBar` to `true`.
• The theme overlay SHALL set `android:windowBackground` to `@android:color/transparent` and enable a single window dim scrim.
2. *Base Class (`AppBottomSheetDialogFragment`)*:
• In `com.atrainingtracker.trainingtracker.ui.components.core`, the system SHALL provide an abstract base class `AppBottomSheetDialogFragment` inheriting directly from `com.google.android.material.bottomsheet.BottomSheetDialogFragment`.
• In `onCreate()`, `AppBottomSheetDialogFragment` SHALL apply `setStyle(STYLE_NORMAL, R.style.ThemeOverlay_aTrainingTracker_BottomSheetDialogFragment)`.
• In `onStart()`, `AppBottomSheetDialogFragment` SHALL configure the single `BottomSheetDialog` window: setting layout to `MATCH_PARENT`, setting background drawable to transparent, ensuring `WindowCompat.setDecorFitsSystemWindows(window, false)`, configuring transparent system bars with `FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS`, disabling contrast enforcement (`isNavigationBarContrastEnforced = false`, `isStatusBarContrastEnforced = false`), and synchronizing light system bars.
3. *Direct Compose Content (No Nested ModalBottomSheet)*:
• In `onCreateView()`, the 7 dialog fragments SHALL return a `ComposeView` hosting their respective settings composable wrapped in `ATrainingTrackerTheme`.
• The dialog contents SHALL render the bottom sheet layout (drag handle, header, scrollable body, actions) directly within the single window *without* invoking Compose's `ModalBottomSheet`, eliminating the redundant second `ComponentDialog` window.
4. *Consolidated DialogFragment Hosts*:
• All 7 bottom popup dialog fragments SHALL extend `AppBottomSheetDialogFragment`:
- `SearchSettingsDialogFragment`
- `ExportSettingsDialogFragment`
- `UnitsSettingsDialogFragment`
- `StravaSettingsDialogFragment`
- `DropboxSettingsDialogFragment`
- `ActivityTypeSelectionDialogFragment`
- `DisplaySettingsDialogFragment`
• Companion object contracts (`TAG`, `newInstance()`, and listener interfaces) SHALL remain strictly identical.
5. *Dismiss Transition & System Bar Parity*:
• When a popup is displayed, the top status bar SHALL remain transparent with a single dim scrim over the underlying Activity screen (never solid black).
• When dismissed via back button, back gesture, scrim tap, or action button, `BottomSheetDialogFragment` SHALL slide down smoothly with zero black navigation bar flicker.

**Acceptance Criteria (Given-When-Then)**:
• *Given* any bottom popup dialog,
• *When* the popup is displayed on Android 17 (API 37),
• *Then* the status bar SHALL show the underlying activity dimmed by a single scrim without any solid black block.
• *When* the popup is dismissed via system back button, gesture, scrim tap, or close button,
• *Then* the navigation bar area SHALL remain transparent throughout the transition with zero black flash.
• *Given* the 7 DialogFragment classes,
• *When* inspected via reflection in `ModalBottomSheetDialogsIntegrityTest`,
• *Then* every class SHALL inherit from `AppBottomSheetDialogFragment` and transitively from `com.google.android.material.bottomsheet.BottomSheetDialogFragment`.

**Invariants**: Fragment companion `newInstance()` and `TAG` signatures, `MainActivityWithNavigation` drawer routing, `ActivityTypeSelectionDialogFragment.ActivityTypeSelectionListener`, SharedPreferences persistence, and `AppModalBottomSheet` composable contracts for pure Compose screens (e.g. `EditWorkoutScreen`) MUST NOT be altered or regressed.

### REQ-UI-157: Advanced Interval Filtering, Tab-Aware Sport Selection & Sport-Aware Equipment [Verified]
- **Tests**: `TST-UI-110`, `TST-UI-394`, `TST-UI-508`, `TST-UI-509`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `WorkoutFilterCriteria.kt`, `WorkoutFilterBottomSheet.kt`, `WorkoutTabsScreen.kt`
- **Rationale**: The system SHALL improve workout list filtering by introducing interval-based filtering controls, tab-aware sport type selection, and sport-aware equipment selection across the workout aftermath views:
1. *Tab-Aware Sport Sub-Type Selection*:
• When viewing the "Bike" tab (`BSportType.BIKE`), the sport filter multi-choice selector in `WorkoutFilterBottomSheet` SHALL present only cycling-related sport types (`it.bSportType == BSportType.BIKE`).
• When viewing the "Run" tab (`BSportType.RUN`), the sport filter selector SHALL present only running-related sport types (`it.bSportType == BSportType.RUN`).
• When viewing the "Other" tab (`BSportType.UNKNOWN`), the sport filter selector SHALL present only non-bike, non-run sport types (`it.bSportType != BSportType.BIKE && it.bSportType != BSportType.RUN`).
• When viewing the "All" tab (`activeBSportType == null`), the sport filter selector SHALL present all recorded sport types.
2. *Sport-Aware Equipment Selection*:
• The equipment selector in `WorkoutFilterBottomSheet` SHALL present only equipment associated with workouts matching the active sport context:
- If a specific sport is selected (`localSportId != null`), only equipment associated with that sport ID SHALL be displayed.
- If no specific sport is selected but an active tab sport type is present (`activeBSportType != null`), only equipment associated with that base sport type SHALL be displayed.
- If no sport or tab filter is active (`activeBSportType == null && localSportId == null`), all recorded equipment SHALL be displayed.
- If the currently selected equipment (`localEquipId`) is not part of the newly filtered available equipment list (e.g. when the athlete switches sport or tab), `localEquipId` SHALL automatically be cleared (`null`).
3. *Date Interval Filtering*:
• The filter bottom sheet SHALL provide custom date interval controls with start and end date pickers (`startDateS` and `endDateS`), allowing the athlete to define arbitrary date ranges $[T_{\text{start}}, T_{\text{end}}]$ with day precision.
4. *Distance Interval Filtering*:
• The filter criteria SHALL support both lower bound (`minDistanceMeters: Double?`) and upper bound (`maxDistanceMeters: Double?`), enabling filtering within $[D_{\text{min}}, D_{\text{max}}]$.
• The filter bottom sheet SHALL allow setting lower and upper distance boundaries.
5. *Duration/Time Interval Filtering*:
• The filter criteria SHALL support both lower bound (`minDurationSec: Long?`) and upper bound (`maxDurationSec: Long?`), enabling filtering within $[T_{\text{min}}, T_{\text{max}}]$.
• The filter bottom sheet SHALL allow setting lower and upper duration boundaries.
6. *Serialization & Backward Compatibility*:
• `WorkoutFilterCriteria` JSON serialization (`toJson` / `fromJson`) SHALL preserve all existing keys and serialize `maxDistanceMeters` and `maxDurationSec` with null-safe fallbacks.
7. *Active Filter Count*:
• `activeFilterCount` in `WorkoutFilterCriteria` SHALL correctly reflect date range, distance range, and duration range selections.

**Acceptance Criteria (Given-When-Then)**:
• *Given* the athlete is on the "Bike" tab and opens `WorkoutFilterBottomSheet`,
• *When* inspecting the sport sub-type multi-choice list,
• *Then* only cycling sport types SHALL be listed (zero running or other sports).
• *Given* the athlete selects a sport (or is on the "Bike" tab),
• *When* inspecting the equipment options in `WorkoutFilterBottomSheet`,
• *Then* only equipment used in that sport (or base sport type) SHALL be displayed.
• *Given* an equipment filter is active and the athlete selects a sport type that does not use this equipment,
• *When* the available equipment list updates,
• *Then* the active equipment selection SHALL automatically clear (`null`).
• *Given* the athlete selects a distance interval (e.g. 20 km to 50 km),
• *When* applied,
• *Then* only workouts with $20000 \le \text{distance} \le 50000$ SHALL be displayed.
• *Given* the athlete selects a duration interval (e.g. 30 min to 90 min),
• *When* applied,
• *Then* only workouts with $1800 \le \text{duration} \le 5400$ SHALL be displayed.
• *Given* the athlete selects a date interval,
• *When* applied,
• *Then* only workouts recorded between the start date and end date inclusive SHALL be displayed.

**Invariants**: Sorting orders, compact view toggle, deletion workflows, and tab-level `BSportType` partitioning MUST NOT be altered.

### REQ-UI-158: Retired Equipment Isolation in Pickers, Visual Identification & Equipment Lifecycle Management [Verified]
- **Tests**: `TST-UI-111`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `EditWorkoutViewModel.kt`, `EquipmentTabsScreen.kt`, `EditEquipmentDialog.kt`, `EquipmentDbHelper.java`, `EquipmentViewModel.kt`, `EquipmentAndSportTypeDiscoveryManager.kt`
- **Rationale**: The system SHALL isolate retired equipment from active selection interfaces while maintaining historical workout attribution and providing clear equipment lifecycle controls:
1. *Active Workout Equipment Picker Isolation (`EditWorkoutViewModel.kt`)*:
• When presenting equipment options in `EditWorkoutBottomSheet` (`updateEquipmentNames`), the system SHALL filter out all equipment marked as retired (`isRetired == true`).
• *Current Assignment Invariant*: If the workout currently being edited is already assigned to a retired equipment (`equipmentId == it.id` or `equipmentName == it.name`), the system SHALL retain that specific retired equipment in the options list, ensuring existing historical assignments remain visible, selectable, and not inadvertently cleared or reset.
• Secondary expansion tokens (`showAllBikes()`, `showAllShoes()`) SHALL likewise filter out retired equipment, retaining only active items and any currently assigned retired item.
2. *Tracking & Hardware Sensor Identity Resolution (`EquipmentAndSportTypeDiscoveryManager.kt`)*:
• When inferring workout identity or linked equipment from active hardware sensors (`resolveIdentity`, `getLinkedEquipmentIds`), the system SHALL exclude any equipment marked as retired (`Retired == 1`). A retired equipment MUST NOT be automatically linked or assigned to newly recorded workouts.
3. *Sport Type Equipment Association (`EditSportTypeDialog.kt`, `SportTypeViewModel.kt`)*:
• When associating equipment with sport types in `availableEquipment(bSportType)`, the system SHALL exclude retired equipment so newly created or edited sport types are not linked to obsolete gear.
4. *Equipment Management & Visual Distinction (`EquipmentTabsScreen.kt`, `EquipmentViewModel.kt`)*:
• The equipment list in `EquipmentTabsScreen` SHALL visually distinguish active equipment from retired equipment:
- Retired items SHALL display an explicit localized "Retired" badge/indicator.
- The equipment screen SHALL organize items into distinct "Active" and "Retired" sections (or provide a toggle to show/hide retired items), keeping the active fleet clear and prominent.
5. *Manual Equipment Retirement Lifecycle (`EditEquipmentDialog.kt`, `EquipmentDbHelper.java`)*:
• `EditEquipmentDialog` SHALL provide a toggle/switch for retirement status ("Retired" / "In Ruhestand"), allowing athletes to manually retire active equipment or restore retired equipment.
• `EquipmentDbHelper` SHALL persist retirement changes via `updateEquipment` / `setEquipmentRetired` to SQLite `Equipment.db`.
6. *Historical Workout Display Fidelity (`WorkoutDataMapper.kt`, `WorkoutFilterBottomSheet.kt`)*:
• Display of historical workouts in compact summaries, expanded headers, and extrema tables SHALL continue to resolve equipment name and frame/sport icons by equipment ID regardless of retirement status.
• Workout list filtering in `WorkoutFilterBottomSheet` SHALL continue to dynamically derive available equipment from historical workouts, ensuring past activities on retired gear remain filterable.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an active equipment "Road Bike" and a retired equipment "Old Mountain Bike" in `Equipment.db`,
• *When* editing a workout with no equipment assigned (or an active equipment assigned),
• *Then* the equipment dropdown in `EditWorkoutBottomSheet` SHALL display "Road Bike" and SHALL NOT display "Old Mountain Bike".
• *Given* a historical workout already assigned to "Old Mountain Bike" (which is retired),
• *When* opening `EditWorkoutBottomSheet` for this workout,
• *Then* the equipment selector SHALL display "Old Mountain Bike" as the selected option, and "Old Mountain Bike" SHALL be present in the available options.
• *Given* a sensor linked to a retired bike,
• *When* `EquipmentAndSportTypeDiscoveryManager.resolveIdentity` executes for a new tracking session,
• *Then* the retired bike SHALL NOT be inferred as the workout's `equipmentId`.
• *Given* the Equipment screen (`EquipmentTabsScreen`),
• *When* viewing the list of bikes or shoes,
• *Then* retired items SHALL display a localized "Retired" badge and be grouped in the retired section.
• *Given* the user edits an active bike in `EditEquipmentDialog` and toggles "Retired" to true,
• *When* saving changes,
• *Then* `Equipment.db` SHALL update `Retired = 1`, and the bike SHALL move to the retired section.

**Invariants**: Existing SQLite schema (v2) and data in `Equipment.db` MUST NOT be dropped or corrupted; historical workout summaries and TCX/GPX exports MUST NOT lose equipment names or frame types.

### REQ-UI-159: Single-Activity Architecture & Jetpack Compose Navigation Modernization [Verified]
- **Tests**: `TST-NAV-009`, `TST-UI-387`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `MainActivityWithNavigation.kt`, `AppNavigationDrawer.kt`, `app/build.gradle`
- **Rationale**: The system SHALL implement a pure Jetpack Compose single-activity architecture for `MainActivityWithNavigation` utilizing declarative composition (`setContent`), Material 3 `ModalNavigationDrawer`, and official Jetpack Compose Navigation (`NavHost` / `NavController`):
1. *Layout Modernization & Single-Activity Root*: The system SHALL retire the legacy Android View hierarchy `main_activity_with_navigation.xml` from the primary runtime path in favor of declarative Compose composition (`setContent { ATrainingTrackerApp(...) }`), hosting top-level navigation, screens, and dialogs within a single Activity window decor.
2. *Material 3 Navigation Drawer*: The system SHALL replace the Android View `DrawerLayout` with Material 3's `ModalNavigationDrawer` and `ModalDrawerSheet` with `WindowInsets.statusBars`, maintaining drawer header rendering and reactive tracking status titles.
3. *Official Compose Navigation Host (`NavHost`)*: The system SHALL replace legacy `supportFragmentManager` transaction replacements with Jetpack Compose Navigation (`androidx.navigation:navigation-compose`), mapping all 21 drawer destinations to type-safe routes with `launchSingleTop = true`, `popUpTo(startDestinationId) { saveState = true }`, and `restoreState = true`.
4. *SavedState Integration & Process-Death Resilience*: Composable destination backstack entries SHALL be managed by `NavBackStackEntry.savedStateRegistry`, automatically restoring scroll positions, tab states (`rememberSaveable`), and ViewModel state (`SavedStateHandle`) across Android process termination and configuration changes.
5. *Modal Bottom Sheet & IME Keyboard Unification*: Settings and selection bottom sheets (Strava, Dropbox, Export, Units, Display, Search, Activity Type Selection) SHALL compose natively within the main window decor via `ModalBottomSheet` configured with `WindowInsets.ime.union(WindowInsets.navigationBars)` and `windowSoftInputMode="adjustResize"`, eliminating child `Dialog` window allocations, dark status bar flashes, and soft keyboard occlusion.
6. *Service Binding Lifecycle Invariants & Concurrency Guards*: Binding to `BANALService.GetBanalServiceInterface` MUST remain anchored strictly to the `MainActivityWithNavigation` Activity lifecycle. In `onPause()` and `onDestroy()`, the system SHALL verify `!isChangingConfigurations` before scheduling service disconnect or unbinding, shielding ANT+ and BLE telemetry streams from disconnection thrashing during device rotation.
7. *Compatibility & Interop Contracts*: Companion object constants (`SELECTED_FRAGMENT_ID`, `SELECTED_FRAGMENT`, `EXTRA_RESUME_INTERRUPTED_WORKOUT`), `SelectedFragment` enum, callback interfaces (`GetBanalServiceInterface`, `OnPreferenceStartScreenCallback`, `StartOrResumeInterface`), crash recovery (`REQ-STB-003`), dynamic display settings (`REQ-SET-052`), and Android 14 insets crash immunity (`REQ-STB-007`) MUST remain 100% functional and verified by unit test reflection.

**Acceptance Criteria (Given-When-Then)**:
• *Given* `MainActivityWithNavigation` cold start,
• *When* the activity launches,
• *Then* `setContent` SHALL render `ATrainingTrackerApp` with `NavHost` initialized to `NavRoutes.START_TRACKING`, without inflating `main_activity_with_navigation.xml`.
• *Given* the athlete selects a drawer destination (e.g. Workouts, Sensors, Map),
• *When* tapped,
• *Then* `NavController` SHALL navigate to the target route with `saveState = true`, the drawer SHALL close, and the selected screen SHALL render directly in Compose.
• *Given* an active sensor telemetry stream,
• *When* the user rotates the device (configuration change),
• *Then* `BANALService` SHALL NOT unbind or drop the connection (`isChangingConfigurations == true` guard).
• *Given* a settings sheet with text inputs (e.g. Strava or Search),
• *When* the software keyboard opens,
• *Then* `ModalBottomSheet` SHALL adjust its insets with `imePadding()`, preventing button or input field occlusion.
• *Given* automated unit test suites (`MainActivityWithNavigationInteropTest`, `CoreDependencyAlignmentTest`),
• *When* executed,
• *Then* 100% of reflection and classpath alignment tests SHALL pass with zero regressions.

**Invariants**: Service binding lifecycle, sensor pairing contracts, crash recovery (`REQ-STB-003`), display options (`REQ-SET-052`), and Android 14 insets stability (`REQ-STB-007`) MUST NOT be altered or regressed.

### REQ-UI-160: Reactive Equipment Loading & Lifecycle Synchronization in Jetpack Compose Architecture [Verified]
- **Tests**: `TST-UI-112`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `EquipmentViewModel.kt`, `EquipmentTabsScreen.kt`
- **Rationale**: The system SHALL ensure equipment items (Bikes and Shoes) are asynchronously loaded from SQLite and maintained reactively within `EquipmentViewModel` under the Jetpack Compose single-activity architecture:
1. *Automated Initialization*: Upon construction (`init`), `EquipmentViewModel` SHALL automatically invoke `loadEquipment()` on `ioDispatcher` to query `EquipmentDbHelper`, populating `_bikes` and `_shoes` StateFlows without requiring manual trigger from external UI callers or fragments.
2. *Reactive Strava Synchronization Observation*: `EquipmentViewModel` SHALL observe `EquipmentRepository.isSyncing` via an internal `syncStatusFlow` StateFlow. When an active equipment synchronization job finishes (falling edge: `true -> false`), `EquipmentViewModel` SHALL automatically reload equipment from SQLite, ensuring the UI reflects newly synchronized or updated gear in real-time. The initial `false` state on cold start SHALL NOT trigger redundant queries.
3. *Concurrency & Deduplication Guard*: `EquipmentViewModel.loadEquipment()` SHALL cancel or coalesce active coroutine jobs to prevent overlapping or redundant database queries when successive trigger events occur in rapid succession.
4. *Declarative UI Layer*: `EquipmentTabsScreen` SHALL remain 100% declarative, collecting `viewModel.bikes` and `viewModel.shoes` directly from `EquipmentViewModel` without requiring ad-hoc `LaunchedEffect` hooks or UI-level sync handling.
5. *Backward Interoperability & Invariants*: Legacy `EquipmentFragment` and existing database helpers (`EquipmentDbHelper`, `SportTypeEquipmentLinkManager`, `WorkoutSummariesDatabaseManager`) SHALL continue to function without modification. Database schemas and queries MUST NOT be changed.

**Acceptance Criteria (Given-When-Then)**:
• *Given* `EquipmentViewModel` instantiated with equipment present in SQLite,
• *When* initialization completes on the IO dispatcher,
• *Then* `viewModel.bikes.value` and `viewModel.shoes.value` SHALL contain the loaded equipment items.
• *Given* `EquipmentViewModel` observing `syncStatusFlow`,
• *When* `syncStatusFlow` transitions from `true` to `false` (sync completion),
• *Then* `loadEquipment()` SHALL be invoked to refresh bikes and shoes.
• *Given* `EquipmentViewModel` on cold start with `syncStatusFlow` emitting `false`,
• *When* initialized,
• *Then* no duplicate reload SHALL be triggered.
• *Given* the user navigates to Bikes (`NavRoutes.BIKES`) or Shoes (`NavRoutes.SHOES`) in Compose navigation,
• *When* `EquipmentTabsScreen` is displayed,
• *Then* the stored bikes and shoes SHALL be rendered in their respective tabs instead of empty placeholders.

**Invariants**: Zero changes to SQLite database schemas or DAO contracts; public StateFlow immutability preserved; no business logic in Compose UI components.

### REQ-UI-161: ModalNavigationDrawer Edge-Swipe Restriction & Touch Conflict Resolution [Verified]
- **Tests**: `TST-UI-113`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `ATrainingTrackerApp.kt`
- **Rationale**: The system SHALL restrict swipe-to-open gesture detection on `ModalNavigationDrawer` exclusively to the leftmost screen edge when the drawer is in the closed state, preventing unintended drawer opening during map panning and child content interactions:
1. *Closed-State Gesture Restriction*: While `drawerState.isClosed` (or `!drawerState.isOpen`), `ModalNavigationDrawer` SHALL configure `gesturesEnabled = false`, deactivating Material 3's internal full-screen horizontal draggable modifier.
2. *Edge-Only Opening Interceptor*: When the drawer is closed, touch-based opening SHALL be triggered exclusively by the dedicated edge-swipe interceptor on the root container, requiring touch initiation within the 40dp margin (`down.position.x <= 40.dp`).
3. *Child View Drag Immunity*: Touch and drag gestures initiated outside the 40dp margin (`down.position.x > 40.dp`) SHALL NOT be intercepted or consumed by the navigation drawer. In particular, horizontal map panning gestures on `MapScreen` (and Google Maps views) and touch interactions on details screens SHALL operate unhindered without triggering the navigation drawer.
4. *Open-State Dismissal Parity*: While `drawerState.isOpen`, `ModalNavigationDrawer` SHALL configure `gesturesEnabled = true`, preserving:
- Full-screen swipe-to-close gesture anywhere across the drawer sheet or scrim.
- Tap-to-dismiss on the background scrim layer.
- System/gesture Back navigation (`BackHandler`) to close the drawer.
5. *Direct Controls & Invariants*: The top app bar navigation hamburger icon, `drawerController.selectedItemId` synchronization, and all 21 drawer destinations in `AppNavigationDrawer` MUST continue to operate without regression.

**Acceptance Criteria (Given-When-Then)**:
• *Given* the athlete is on `MapScreen` with the navigation drawer closed,
• *When* the athlete pans the map eastward by swiping right starting from $x > 40\text{dp}$,
• *Then* the map SHALL pan smoothly and the navigation drawer SHALL NOT open.
• *Given* the athlete is on any details screen (e.g. `WorkoutDetailsScreen`, `SegmentDetailsScreen`, `RouteDetailsScreen`) with the drawer closed,
• *When* the athlete swipes right starting from $x > 40\text{dp}$,
• *Then* the navigation drawer SHALL NOT open.
• *Given* the athlete is on any screen with the navigation drawer closed,
• *When* the athlete swipes right starting from the leftmost edge ($x \le 40\text{dp}$),
• *Then* the navigation drawer SHALL open smoothly.
• *Given* the navigation drawer is open,
• *When* the athlete swipes left across the drawer sheet or taps the scrim,
• *Then* the navigation drawer SHALL close smoothly.

**Invariants**: All 21 navigation drawer destinations and routes in `NavRoutes` preserved; 40dp edge touch threshold preserved; back press handling and top bar hamburger icon preserved; no screen-specific gesture hacks.

### REQ-UI-162: Single Source of Truth & Scoped Overlay Back Navigation [Verified]
- **Tests**: `TST-UI-114`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `ATrainingTrackerApp.kt`, `MainActivityWithNavigation.kt`, `AppNavigationDrawer.kt`
- **Rationale**: The system SHALL establish Material 3's `DrawerState` as the single authoritative source of truth for navigation drawer visibility and navigation control, eliminating decoupled boolean state flags, and SHALL isolate overlay back navigation (Navigation Drawer and Settings Bottom Sheets) from screen-level destination back navigation using scoped, conditionally composed `BackHandler`s:
1. *Single Source of Truth*: `NavigationDrawerController` SHALL delegate drawer open and close actions directly to Material 3's `DrawerState` via functional action delegates, removing the decoupled mutable boolean state flag (`isDrawerOpen`) entirely and eliminating split-brain asynchronous synchronization.
2. *Scoped Overlay Back Navigation & Dynamic LIFO Precedence*: Overlay back handlers for `ModalNavigationDrawer` and Settings Bottom Sheets SHALL be conditionally composed (`if (isDrawerVisible) { BackHandler { ... } }` and `if (activeBottomSheet != null) { BackHandler { ... } }`). Conditionally composing `BackHandler` dynamically appends its callback to the head of the `OnBackPressedDispatcher` LIFO evaluation stack whenever the overlay is presented, guaranteeing that system Back exclusively dismisses the active overlay and strictly supersedes `NavHost`'s internal backstack popping and destination screen back handlers across all animated phases (`currentValue != Closed || targetValue != Closed`).
3. *NavController Lifecycle Binding & Defensive Graph Readiness*: In `ATrainingTrackerApp.kt`, `DisposableEffect(navController)` binding `activity.onNavControllerReady(navController)` and initial route synchronization SHALL be composed within the `Scaffold` content lambda after `NavHost`, guaranteeing that `NavHost` initializes and attaches the navigation graph (`setGraph`) before `onNavControllerReady` executes queued drawer navigation. In `MainActivityWithNavigation.kt`, navigation methods (`navigateToDrawerItem`, `navigateToFilteredWorkouts`, `startWorkoutSummaryListFromPeriod`) SHALL verify that `navController` has an initialized graph; if called before `NavHost` sets its graph, `navigateToDrawerItem` SHALL buffer `mPendingDrawerItemId = itemId` without accessing `controller.graph` or throwing `IllegalStateException`.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: `REQ-UI-162` (*Single Source of Truth & Scoped Overlay Back Navigation*), implemented under `ATT-1327` / `ATT-1335`. Targets `ATrainingTrackerApp.kt`, `MainActivityWithNavigation.kt`, and `AppNavigationDrawer.kt`.
2. *Historical Origin & Commit Trace*: Ticket `ATT-1327` / `ATT-1335` bound `MainActivityWithNavigation` navigation callbacks to Compose `NavHostController`. Refined under `ATT-7526` (`Sprint 2026-46.5`).
3. *Root Reason for Existing Formulation*: `DisposableEffect(navController)` was originally placed in the outer `ATrainingTrackerApp` body to bind `navController` on initial composition. Because `Scaffold` defers subcomposition of its content lambda until the measure/layout pass, the outer `DisposableEffect`'s `onRemembered()` fired before `NavHost` invoked `setGraph()`, crashing on early drawer navigation with `IllegalStateException: You must call setGraph() before calling getGraph()`.
4. *Preservation of Core Invariants*: Binding `navController` inside `Scaffold` after `NavHost` maintains the exact binding lifecycle contract while eliminating premature callback dispatch before graph readiness. Scoped `BackHandler` LIFO precedence and 21 drawer destinations remain 100% preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an early drawer navigation request arrives prior to Compose graph initialization (e.g. `mPendingDrawerItemId = itemId`),
• *When* `ATrainingTrackerApp` initial composition occurs,
• *Then* `NavHost` SHALL attach the navigation graph before `activity.onNavControllerReady(navController)` executes, successfully navigating to the pending drawer destination without throwing `IllegalStateException`.
• *Given* `navigateToDrawerItem(itemId)` is invoked while `navController` is present but has no graph attached,
• *When* the method executes,
• *Then* it SHALL safely buffer `mPendingDrawerItemId = itemId` and return `true` without accessing `controller.graph`.

### REQ-UI-163: Cockpit Tab Navigation on Tracking Initiation & Origin-Aware Resume Neutrality [Verified]
- **Tests**: `TST-UI-115`, `TST-UI-373`, `TST-UI-375`, `TST-UI-399`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `TrainingApplication.java`, `BANALServiceRepository.kt`, `TrackingTabsViewModel.kt`, `TrackingTabsScreen.kt`
- **Rationale**: The system SHALL ensure that whenever tracking is initiated (`READY -> TRACKING`) or manually resumed while actively on the Control tab (`PAUSED -> TRACKING` with `wasAutoPaused == false` and `currentPage == 0`), the tracking screen automatically and smoothly navigates the pager from Page 0 ("Control" tab) to Page 1 (first "Cockpit" telemetry tab), while strictly preserving the active tab without scrolling when Auto-Pause ends (`wasAutoPaused == true`) or when resuming a manual pause while viewing any telemetry or map tab (`currentPage >= 1`):
1. *Origin-Aware Resume State Tracking (`TrainingApplication.java` & `BANALServiceRepository.kt`)*:
• In `TrainingApplication.resumeFromPaused()`, the system SHALL capture `wasAuto = mIsAutoPaused.get()`, record it in atomic static state `mWasAutoPaused.set(wasAuto)` prior to clearing pause locks, broadcast `EXTRA_WAS_AUTO_PAUSED` in `TRACKING_STATE_CHANGED`, and expose `TrainingApplication.wasAutoPaused()`.
• In `BANALServiceRepository.kt`, the repository SHALL observe `EXTRA_WAS_AUTO_PAUSED` and expose `val wasAutoPaused: StateFlow<Boolean>`.
• In `pauseTracking()`, `stopTracking()`, `startTracking()`, and abort handlers, `mWasAutoPaused` SHALL be reset to `false`.
2. *Active Page Pager State Synchronization (`TrackingTabsViewModel.kt` & `TrackingTabsScreen.kt`)*:
• `TrackingTabsViewModel` SHALL maintain the current pager page position via `val currentPage: StateFlow<Int>` and public callback `onPageSelected(page: Int)`.
• In `TrackingTabsScreen.kt`, `LaunchedEffect(pagerState.currentPage)` SHALL synchronize `pagerState.currentPage` to `trackingTabsViewModel.onPageSelected(page)`.
3. *Origin-Aware Navigation Decision Logic (`TrackingTabsViewModel.kt`)*:
• In `TrackingTabsViewModel`, upon observing a rising edge to `TrackingMode.TRACKING` (`previousMode != null && previousMode != TRACKING && mode == TRACKING`), the ViewModel SHALL evaluate `shouldNavigate = when (previousMode) { TrackingMode.READY -> true; TrackingMode.PAUSED -> !banalServiceRepository.wasAutoPaused.value && currentPage.value == 0; else -> false }`.
• When `shouldNavigate == true`, `TrackingTabsViewModel` SHALL emit `TabNavigationEvent.NavigateTo(0)` via `Channel<TabNavigationEvent>(Channel.BUFFERED)` (`REQ-UI-412`).
• When `shouldNavigate == false`, navigation SHALL be suppressed with zero events emitted.
4. *Auto-Pause Resume Tab Preservation*:
• Resuming from Auto-Pause (via speed recovery or tapping "Resume" on `AutoPauseHudBanner`) SHALL evaluate `wasAutoPaused == true`, suppressing `NavigateTo(0)` and leaving the athlete uninterrupted on their active tab (telemetry tabs, Map tab, or Page 0).
5. *Telemetry & Map Tab Manual Resume Preservation*:
• Resuming a manually paused session while viewing `currentPage >= 1` (e.g. via smartwatch action or notification action) SHALL evaluate `currentPage != 0`, suppressing `NavigateTo(0)` and preserving the active tab.
6. *Recomposition, Rotation & Gesture Invariants*:
• Cold starts, process recreation, and device rotations while in `TRACKING` mode SHALL emit zero navigation events.
• Pager scroll animations SHALL respect manual swipe interruptions (`CancellationException`) without hard snapping (`REQ-UI-412.3`).

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: `REQ-UI-163` (*Cockpit Tab Navigation on Tracking Initiation*), implemented under `ATT-1340` (`commit e253c312` and `commit 26cf352c`). Targets `TrainingApplication.java`, `BANALServiceRepository.kt`, `TrackingTabsViewModel.kt`, `TrackingTabsScreen.kt`, and `TrackingTabsViewModelTest.kt`.
2. *Historical Origin & Commit Trace*: Ticket `ATT-1340`. Commit `e253c312` originally guarded rising-edge navigation with `previousMode != PAUSED`. Rainer observed that after pausing manually on the Control tab (Page 0), tapping "Resume" left the athlete stranded on Page 0 instead of animating to the metrics screen. 15 minutes later, commit `26cf352c` removed `previousMode != PAUSED` so resuming from `PAUSED` would scroll to Page 1. Refined in `ATT-4097` (`REQ-UI-412`) for channel buffering. Amending under `ATT-4373` (`Sprint 2026-43.2`).
3. *Root Reason for Existing Formulation*: Commit `26cf352c` removed the `PAUSED` guard as an expedient fix to support Control tab manual resume without plumbing pager page state into `TrackingTabsViewModel` or distinguishing Auto-Pause from manual pause. This had the unintended side effect of causing every Auto-Pause resumption (and manual resume from Page >= 1) to yank the athlete away from secondary metrics or Map navigation back to Page 1.
4. *Preservation of Core Invariants*: Initial tracking start (`READY -> TRACKING`) and Control tab manual resume (`PAUSED -> TRACKING` on `Page 0`) continue to navigate smoothly to Page 1. Resuming from Auto-Pause and resuming from Page >= 1 now preserves the active tab. Channel buffering (`REQ-UI-412`), swipe gesture cancellation resilience (`REQ-UI-412.3`), and 100% clean-room test pass rate remain strictly preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an active workout with Auto-Pause enabled and the athlete viewing Page 2+ (e.g. Map tab or secondary metrics),
• *When* Auto-Pause ends via movement acceleration or tapping "Resume" on the HUD banner,
• *Then* the pager SHALL NOT scroll and the athlete SHALL remain on their active tab (`NavigateTo(0)` suppressed).
• *Given* an athlete viewing the Control tab (`Page 0`) in `PAUSED` mode following a manual pause,
• *When* the athlete taps "Resume",
• *Then* the pager SHALL automatically and smoothly animate to Page 1 (first cockpit tab).
• *Given* an athlete viewing Page 2+ in `PAUSED` mode following a manual pause,
• *When* tracking is resumed (e.g. via smartwatch or notification action),
• *Then* the pager SHALL NOT scroll and the athlete SHALL remain on Page 2+ (`NavigateTo(0)` suppressed).
• *Given* an athlete on the Start / Control tab (`Page 0`) in `TrackingMode.READY`,
• *When* the athlete taps "Start Tracking",
• *Then* the pager SHALL automatically and smoothly animate to Page 1.
• *Given* the athlete is actively tracking and rotates the device or recomposes the screen,
• *Then* zero navigation events SHALL be emitted and the active tab SHALL NOT be changed.

**Invariants**: Configuration mode (`ScreenMode.CONFIGURATION`) and Preview mode (`ScreenMode.PREVIEW`) back navigation and tab management remain unaltered; Page 0 Control tab remains accessible via left-swipe or tapping the "Tracking" tab; manual gesture interruptions are protected against fallback hard-snapping (`REQ-UI-412.3`).

### REQ-UI-164: Accessibility Event, NodeInfo & Manager Compatibility & Defective Platform Resilience [Verified]
- **Tests**: `TST-UI-116`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: Eliminate fatal NoSuchFieldError and NoSuchMethodError crashes in Jetpack Compose accessibility dispatch, action class initialization, node info creation, and accessibility manager queries on defective Android 14 platform builds through targeted dependency substitution with defensive try-catch guards.
- **Rationale**: The system SHALL guarantee runtime immunity against `NoSuchMethodError` and `NoSuchFieldError` crashes during accessibility event, node info, action initialization, and manager tool query dispatching on defective Android 14 (API 34) platform builds missing `AccessibilityEvent.setAccessibilityDataSensitive(boolean)`, `AccessibilityNodeInfo.setAccessibilityDataSensitive(boolean)`, `AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_IN_DIRECTION`, or `AccessibilityManager.isRequestFromAccessibilityTool()` (ATT-1347, ATT-2302, ATT-2584, ATT-2767):
1. *Targeted Dependency Substitution & Patched Core Artifact*: The build system SHALL substitute `androidx.core:core:1.15.0` with local patched artifact `androidx.core:core:1.15.0-patched` (via `settings.gradle` local maven repository and `app/build.gradle` `resolutionStrategy.dependencySubstitution`), completely avoiding D8/R8 dex-merging duplicate class conflicts and classpath shadowing ambiguity in `app/src/main/java`.
2. *Defensive LinkageError Call-Site Guards & Proactive Blanket Shield*: In `AccessibilityEventCompat$Api34Impl`, `AccessibilityNodeInfoCompat$Api34Impl`, `AccessibilityManagerCompat$Api34Impl`, and `AccessibilityWindowInfoCompat$Api34Impl`, platform invocations and field accesses SHALL be wrapped in defensive `try ... catch (LinkageError e)` blocks. In `AccessibilityNodeInfoCompat$Api34Impl`, `getActionScrollInDirection()` SHALL catch `LinkageError` and return `null` safely (matching pre-API 34 fallback); all remaining API 34 methods (`getContainerTitle`, `setContainerTitle`, `getBoundsInWindow`, `setBoundsInWindow`, `hasRequestInitialAccessibilityFocus`, `setRequestInitialAccessibilityFocus`, `getMinDurationBetweenContentChangeMillis`, `setMinDurationBetweenContentChangeMillis`, `setQueryFromAppProcessEnabled`) SHALL be defensively shielded with safe fallbacks (`null`, `false`, `0L`, or no-op). Non-linkage fatal JVM errors (`OutOfMemoryError`) SHALL NEVER be caught.
3. *Build Variant Neutrality*: The patched artifact SHALL operate uniformly and deterministically across all build types (Debug, Profile, Release), ensuring that developers, automated UI tests, and release APKs share identical crash immunity with zero dependency on R8 compiler optimization heuristics.
4. *Looper & Dispatch Loop Integrity*: The call-site interception SHALL prevent exceptions from escaping into `AccessibilityNodeInfoCompat$AccessibilityActionCompat.<clinit>`, `AndroidComposeViewAccessibilityDelegateCompat.boundsUpdatesEventLoop$ui`, `createNodeInfo`, or `isRequestFromAccessibilityTool`, completely eliminating main-thread `Looper` infinite dispatch retry loops, UI freezes, or state corruption.
5. *Dual Delivery & Hotfix Parity*: The patched artifact SHALL be integrated into both the active sprint integration branch (`sprint/2026-41.4`) and the release hotfix branch (`hotfix/V4.9.38.4__267`).

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: `REQ-UI-164` (*Accessibility Event & NodeInfo Compatibility & Defective Platform Resilience*), targeting `local-repo/androidx/core/core/1.15.0-patched/core-1.15.0-patched.aar`.
2. *Historical Origin & Commit Trace*: Established under ATT-1347 (`b2a3a5f7`), amended under ATT-2302 (`96365709`), ATT-2584 (`d2ab4240`), and ATT-2767.
3. *Root Reason for Existing Formulation*: Built to absorb `NoSuchMethodError` and `NoSuchFieldError` crashes on defective Android 14 OEM firmware where `Build.VERSION.SDK_INT == 34` but accessibility methods or fields are omitted from `framework.jar`.
4. *Preservation of Core Invariants*: Extending the defensive shield to `AccessibilityNodeInfoCompat$Api34Impl.getActionScrollInDirection` and all remaining API 34 methods preserves 100% of the existing contract: on intact devices, native behavior is unchanged; on defective devices, fatal crashes are safely suppressed with graceful degradation; fatal JVM errors are never swallowed.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an application build running on an Android 14 (API 34) device missing `ACTION_SCROLL_IN_DIRECTION`, `setAccessibilityDataSensitive`, or `isRequestFromAccessibilityTool`,
• *When* `AccessibilityActionCompat` static class initialization, Jetpack Compose UI accessibility loops (`boundsUpdatesEventLoop$ui`), view delegates (`createNodeInfo`), or manager checks invoke platform APIs,
• *Then* `Api34Impl` SHALL intercept missing fields/methods via `try ... catch (LinkageError e)`, log a warning, and return cleanly without throwing `NoSuchFieldError` or `NoSuchMethodError`.
• *Given* any build configuration (Debug, Profile, Release),
• *Then* the crash immunity SHALL be active and identical across all configurations, and `./gradlew assembleRelease` SHALL complete without D8 duplicate class merger exceptions.
• *Given* an intact Android 14/15/16 device with valid platform methods and fields,
• *Then* platform methods SHALL be invoked normally without behavioral divergence.

**Invariants**: Pre-API 34 compatibility preserved; standard Compose accessibility semantics preserved; zero infinite dispatch loops.

### REQ-UI-165: Known Start Locations Management, Interactive Map Geofences & Altitude Lock UI [Verified]
1. *Navigation Drawer Hierarchy & Icon Reallocation (`AppNavigationDrawer.kt`)*:
• In `drawer__maps`, `drawer_start_locations` SHALL be positioned directly below `drawer_my_locations` (*Lieblingsstrecken*).
• `drawer_start_locations` (*Lieblingsorte*) SHALL be assigned `R.drawable.my_locations` (location pin with embedded heart).
• `drawer_my_locations` (*Lieblingsstrecken*) SHALL be assigned `R.drawable.ic_favorite_route` (route trajectory with heart overlay).
• Both destinations SHALL route correctly to `NavRoutes.START_LOCATIONS` and `NavRoutes.MY_LOCATIONS`.
2. *Standard Tabbed Header Surface & Tab Row (`KnownLocationsScreen.kt`)*:
• The screen header SHALL be enclosed in a `Surface` with `MaterialTheme.colorScheme.primaryContainer` and `Modifier.statusBarsPadding()` without a sandwich drawer icon or location counter badge.
• The title row (`LayoutConstants.HEADER_TITLE_ROW_HEIGHT`) SHALL display the screen title using `MaterialTheme.typography.titleLarge` colored `MaterialTheme.colorScheme.onPrimaryContainer`.
• The tab row SHALL compose `PrimaryTabRow` with `containerColor = MaterialTheme.colorScheme.surfaceContainerHighest` and `divider = {}`.
• Two text-only primary tabs without icons SHALL be defined: "Liste" (`R.string.known_locations_tab_list`) and "Karte" (`R.string.known_locations_tab_map`).
3. *Fluid Page Navigation & Swipe Gestures*:
• Screen content SHALL be hosted in a `HorizontalPager(state = pagerState)` with page count 2. On the List tab, user swiping to the left SHALL navigate directly to the Map tab (`userScrollEnabled = pagerState.currentPage == 0`). On the Map tab, pager scroll SHALL be disabled so dragging and gestures exclusively pan and interact with the map.
4. *Multi-Dimension Sorting Menu*:
• An action sort button in the header SHALL open a `DropdownMenu` offering 4 sorting dimensions in exact sequence: (1) Starts / Aufzeichnungen (`RECORDINGS`, `@string/filter_section_recordings`), (2) Closest to Current Location (`DISTANCE_TO_USER`, `@string/sort_closest`), (3) Altitude (`ALTITUDE`, `@string/sort_altitude`), and (4) Name (`NAME`, `@string/sort_name`).
• When GPS user location is unavailable, the `DISTANCE_TO_USER` option SHALL be disabled and dimmed (`alpha = 0.38f`). Reactive sorting SHALL re-evaluate when user location updates.
5. *Custom Theme-Colored Map Markers & Fallback Centering*:
• The Google Map view SHALL render custom `BitmapDescriptor` markers styled with the application's theme colors (primary tinted pin with white heart glyph). Circular geofence overlays SHALL be rendered with radius $200.0\text{m}$.
• When switching to or opening the Map perspective without an explicitly selected location and without active GPS, the camera SHALL automatically center on the start location with the highest visit count: $\text{fallbackTarget} = \operatorname{argmax}_{l \in \text{locations}}(\text{hitCount}(l))$. If no locations exist in SQLite, the camera SHALL center on default coordinates (Munich `48.13715, 11.57612`).
6. *Map Item Deletion Context Menu & Confirmation*:
• Long-pressing a location marker or its geofence area on the map SHALL open an anchored contextual `DropdownMenu` offering a Delete action (`@string/delete`, `Icons.Default.Delete`).
• Selecting Delete from the map context menu (or from the list item context menu) MUST present `DeleteConfirmationDialog` using localized confirmation message (`@string/really_delete_format`) before deleting the location from SQLite.
7. *List Item Card Standardization & Context Menu (`MappableListItem`)*:
• Location cards in the list perspective SHALL compose `MappableListItem` with 16dp rounded corners and 2dp elevation.
• *Universal Delete-Only Context Menu*: Long-pressing a location card SHALL trigger a standardized `DropdownMenu` anchored at Top-Left (`Alignment.TopStart`, covering the header) containing strictly and exclusively the Delete action (`@string/delete`, `Icons.Default.Delete`) under `REQ-UI-061`. Secondary non-destructive actions ("Edit", "Show on Map") and header overflow buttons (`MoreVert`) are strictly forbidden; editing is accessed via single card tap (`onClick = onEdit`).
• Bottom list content padding SHALL incorporate `WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()` to ensure the final item is fully visible and never obscured behind the system navigation bar.
• *Unified Edit Dialog*: Tapping a list item opens `EditKnownLocationDialog` with an embedded map preview. Tapping a location on the map tab opens `EditKnownLocationDialog` without the redundant map preview. The edit title SHALL display "Lieblingsort bearbeiten" / "Edit Favorite Location" consistently.
8. *9-Language Domain Nomenclature Parity*:
• German strings (`values-de/strings.xml`) SHALL be updated to "Lieblingsorte" (`drawer_start_locations`, `known_locations_title`, `known_locations_empty_title`). English strings (`values/strings.xml`) SHALL be defined as "Favorite Locations". Localization parity SHALL be 100% maintained across all 9 supported application locales (EN, DE, ES, FR, IT, JA, NL, PL, PT).
9. *Automatic Legacy Name Healing & Geocoder Auto-Naming*:
• On initialization of `KnownLocationsViewModel` and `KnownLocationsRepository`, the system SHALL trigger `healLegacyNames()` in the background to asynchronously enrich any start locations retaining placeholder or coordinate-fallback names with human-readable geocoded addresses.
• In `KnownLocationsDatabaseManager`, `upsertLocationByGeofence()` and `learnLocation()` SHALL persist newly resolved names when existing records have placeholder names.
• `LocationNameResolver.isPlaceholderName()` SHALL recognize coordinate fallback patterns (e.g. `Startort (lat, lon)`) across all supported locales, while strictly preserving manual user customizations (`source == MANUAL_USER` or user-edited names).

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: `REQ-UI-165` (*Known Start Locations Management, Interactive Map Geofences & Altitude Lock UI*) in `docs/requirements.md`, targeting `KnownLocationsScreen.kt` and `AppNavigationDrawer.kt`.
2. *Historical Origin & Commit Trace*: Introduced in ticket `ATT-919` via commits `48d0bdc8` and `b4ebca54` (dated 2026-09-24).
3. *Root Reason for Existing Formulation*: Baseline functionality focused on SQLite V5 persistence and reverse geocoding auto-naming. This enhancement aligns the tabbed layout, sorting capabilities, gesture navigation, and map marker interaction directly with the core application design system.
4. *Preservation of Core Invariants*: Schema V5 persistence (`REQ-DAT-014`), single-thread SQLite confinement (`KnownLocationsDB-Thread`), automatic altitude write-protection on manual user edits (`is_locked = 1`, `source = MANUAL_USER`), repeat workout start hitCount increments (`REQ-DAT-007`), and navigation route contracts (`NavRoutes.START_LOCATIONS`) remain 100% strictly intact.
5. *Universal Delete-Only Long-Press Contract (ATT-1523)*: Explicitly harmonizes the list perspective with `REQ-UI-061` by eliminating secondary actions ("Edit", "Show on Map") from the card context menu and removing the header overflow MoreVert button, preserving single-tap editing via `EditKnownLocationDialog`.

**Acceptance Criteria (Given-When-Then)**:
• *Given* the athlete opens the navigation drawer,
• *When* viewing `drawer__maps`,
• *Then* `Lieblingsorte` SHALL appear immediately below `Lieblingsstrecken`, displaying `R.drawable.my_locations`, and `Lieblingsstrecken` SHALL display `R.drawable.ic_favorite_route`.
• *Given* `KnownLocationsScreen` is rendered,
• *When* inspecting the top bar and tab row,
• *Then* the header surface SHALL have `primaryContainer` color with `statusBarsPadding()`, and the tab row SHALL be a `PrimaryTabRow` with `surfaceContainerHighest` container color, no divider, and text-only labels without icons.
• *Given* the athlete is on the List tab,
• *When* swiping left,
• *Then* the screen SHALL smoothly animate to the Map tab, and `PrimaryTabRow`'s selected indicator SHALL synchronize to index 1.
• *Given* the athlete is on the Map tab,
• *When* long-pressing a location marker pin or circular geofence,
• *Then* a contextual `DropdownMenu` SHALL appear anchored at the marker with a \"Löschen\" / \"Delete\" option.
• *When* tapping Delete in the context menu,
• *Then* `DeleteConfirmationDialog` SHALL appear requesting confirmation (`really_delete_format`) before deleting the location.
• *Given* the sort dropdown menu in the header,
• *When* opened,
• *Then* options SHALL be listed in order: Starts (`RECORDINGS`), Distance to User (`DISTANCE_TO_USER`), Altitude (`ALTITUDE`), and Name (`NAME` last).
• *Given* start locations with legacy placeholder names (e.g. `Startort (48.137, 11.576)` or `Auto-learned start`),
• *When* navigating to `KnownLocationsScreen`,
• *Then* `healLegacyNames()` SHALL automatically resolve human-readable place names via Geocoder and update the UI reactively.
• *Given* German language device configuration,
• *When* viewing drawer and screen titles,
• *Then* \"Lieblingsorte\" SHALL be displayed across all relevant UI components.

**Invariants**: SQLite schema V5 compatibility (`REQ-DAT-014`), single-thread SQLite concurrency (`KnownLocationsDB-Thread`), automatic altitude lock and source update on manual edit (`is_locked = 1`, `source = MANUAL_USER`), repeat start hitCount increments (`REQ-DAT-007`), and navigation route contracts (`NavRoutes.START_LOCATIONS`) MUST NOT be broken.]
- **Tests**: `TST-UI-070`, `TST-UI-117`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: **REQ-UI-166**
- **Rationale**: The system SHALL provide a dedicated Jetpack Compose management screen and interactive dual-perspective interface for known start locations, enabling athletes to audit, calibrate, and manage spatial reference points and their reference altitudes (ATT-919):
1. *Navigation Drawer Entry Point*: The system SHALL integrate an entry item under the map destinations group (`drawer__maps`) in `AppNavigationDrawer`: `R.id.drawer_start_locations` -> `NavRoutes.START_LOCATIONS` with localized title ("Start Locations" / "Startorte") and pin icon (`Icons.Default.Place` or `R.drawable.ic_menu_locations`), navigating to `KnownLocationsScreen`.
2. *Dual-Perspective Presentation (`KnownLocationsScreen.kt`)*:
• The screen SHALL feature a top `TabRow` toggling between "List" (`@string/known_locations_tab_list`) and "Map" (`@string/known_locations_tab_map`) perspectives.
• *List Perspective*: Renders a scrollable `LazyColumn` of known location cards. Each card SHALL display:
- Location Name (`name` or localized fallback `@string/known_location_unnamed_format` with formatted geodetic coordinate).
- Reference Altitude formatted according to active unit preferences (Metric: meters $h_{\text{m}}$ with "m", Imperial: feet $h_{\text{ft}}$ with "ft" rounded to nearest integer).
- Source Badge reflecting provenance (`INTERNET_DEM`, `MANUAL_USER`, `AUTO_LEARNED`, `GPS_FALLBACK`, `LEGACY_RAW`) with localized label and distinct semantic tint. Manual user edits are clearly identified by the `MANUAL_USER` badge.
- Hit Count Badge displaying total workout starts originating from this location.
- Geodetic Coordinates (Latitude, Longitude formatted to 5 decimal places).
- Standardized delete-only long-press context menu anchored at Top-Left providing strictly the Delete option (`@string/delete`), with editing invoked via single card tap (`REQ-UI-061`).
• *Map Perspective*: Renders an interactive Google Map (`ATrainingTrackerMap` / `GoogleMap`) displaying:
- Marker pins for each known location placed at `(latitude, longitude)`.
- Geofence circular overlays (`CircleOptions`) centered at each location with radius 200.0 meters, semi-transparent fill (`TTAlpha.HeatmapLow`), and subtle stroke (`primary` theme color).
- Dynamic viewport bounds culling: the renderer SHALL evaluate camera bounds (`visibleRegion.latLngBounds`) to prioritize active rendering of markers and circles within the visible viewport.
- Marker click listener: tapping any marker or geofence circle selects the location and displays a bottom peek sheet with location summary and an action to launch the edit dialog.
3. *Modal Edit Dialog (`EditKnownLocationDialog.kt`)*:
• The dialog SHALL compose `AppModalBottomSheet` with localized title `@string/known_location_edit_title` and close dismiss icon.
• *Name Field*: Outlined text field allowing custom naming and editing of the location.
• *Altitude Field*: Localized numeric text field supporting decimal comma/period input. In Metric mode, value is in meters ($0.1\text{m}$ precision); in Imperial mode, value is in feet.
• *"Fetch from Internet (DEM)" Action*: Prominent button allowing on-demand elevation retrieval via `ElevationService.fetchElevation(lat, lon)`. While fetching, an inline progress indicator is displayed. Upon success, the altitude field updates to the DEM elevation and `source` is set to `INTERNET_DEM`.
• *Action Bar*: Integrates `AppDialogActions.SaveCancel` with borderless "Abbrechen" (`@string/Cancel`) and filled "Speichern" (`@string/save`). Saving commits modifications to SQLite via `KnownLocationsRepository`. To eliminate user friction and protect manual calibrations without requiring manual lock management, whenever the user manually saves an altitude, the system SHALL automatically set `source = MANUAL_USER` and `is_locked = 1`. If the user fetches from DEM and saves, `source` is set to `INTERNET_DEM` and `is_locked = 0`.
4. *Reverse-Geocoded Location Naming & User Editing*:
• *Meaningful Auto-Naming on Discovery*: When a new location is discovered (e.g. during workout start in `AltitudeFromPressureDevice`), the system SHALL resolve a human-readable location name via Android `Geocoder` (querying locality, sublocality, thoroughfare, or feature name) on a background thread, falling back to localized coordinate representation (`@string/known_location_unnamed_format`) if offline or geocoding fails.
• *Database Upgrade & Batch Healing Name Enrichment*: During legacy database healing/updates, any location retaining a generic placeholder name (e.g. `'Internet DEM start'`, `'Auto-learned start'`, or empty) SHALL asynchronously be enriched with a geocoded location name.
• *User Sovereignty & Custom Name Protection*: Athletes SHALL be able to edit and customize the location name via `EditKnownLocationDialog`. Custom names entered by the user (`source == MANUAL_USER` or user-edited) SHALL strictly be preserved in SQLite (`KnownLocationsDbHelper.NAME`) and MUST NEVER be overwritten by automatic geocoding or background healing.
5. *Single-Threaded SQLite Dispatcher & Coroutine Concurrency*: `KnownLocationsRepository` SHALL serialize all SQLite read and write transactions through a dedicated single-threaded dispatcher (`newSingleThreadExecutor("KnownLocationsDB-Thread").asCoroutineDispatcher()`), eliminating coroutine thread hopping and preventing deadlocks between Java `synchronized` monitors and Kotlin Coroutines.
6. *Unit Conversion Precision & Invariants*:
• The authoritative database storage for altitude SHALL strictly remain SI meters (`Double`).
• Manual editing in Imperial units SHALL convert feet to meters with explicit 0.1m rounding: $h_{\text{m}} = \text{round}(h_{\text{ft}} \times 0.3048 \times 10.0) / 10.0$.
• Imperial display SHALL round to the nearest integer foot: $h_{\text{ft}} = \text{round}(h_{\text{m}} / 0.3048)$.
• Location deletion: Deleting a location SHALL purge the record from `KnownLocations.TABLE` and refresh the UI reactively.
• Workout start counting invariant: Repeat workout starts within a locked or manually calibrated location's 200m geofence SHALL increment `hitCount` (`existing.hitCount + 1`) while strictly preserving locked altitude and coordinates (`REQ-DAT-007`).
7. *100% Localization Parity*: All user-facing strings across the screen, tabs, badges, actions, dialogs, and units SHALL be defined across all 9 supported application locales (EN, DE, ES, FR, IT, JA, NL, PL, PT).

**Acceptance Criteria (Given-When-Then)**:
• *Given* the athlete opens the navigation drawer,
• *When* "Start Locations" / "Startorte" is tapped,
• *Then* `NavController` SHALL navigate to `NavRoutes.START_LOCATIONS` and render `KnownLocationsScreen`.
• *Given* the List tab on `KnownLocationsScreen`,
• *When* viewing stored locations,
• *Then* each card SHALL display Name, formatted Altit

### REQ-UI-166: Lieblingsorte UI/UX Harmonization & Favorite Locations Management [Verified]
- **Tests**: `TST-UI-070`, `TST-UI-118`
- **Priority**: High
- **Type**: Functional
- **Verification Method**: Unit / Review
- **Implementation**: `KnownLocationsScreen.kt`, `KnownLocationsViewModel.kt`, `MappableListItem.kt`
- **Rationale**: The system SHALL harmonize the Lieblingsorte (Favorite Locations) interface with standard list item cards (`MappableListItem`), dual list/map tabs, 4-dimension sorting (Starts, Distance to User, Altitude, Name), geofence circular overlays, theme-colored custom marker assets, single-tap navigation to editor, and delete-only long-press context menus across all 9 supported locales.
- **Description**: Lieblingsorte UI/UX Harmonization & Favorite Locations Management.

### REQ-UI-167: Intuitive Smoothing Presets, Simplified Filter Configuration & Smart Athletic Defaults [Verified]
- **Tests**: `TST-UI-119`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `ConfigureFilterDialog.kt`, `EditSensorFieldViewModel.kt`, `TrackingViewsDatabaseManager.java`, `strings_filters.xml` (all 9 locales)
- **Rationale**: The system SHALL provide an intuitive, 1-tap quick preset selection interface and smart athletic defaults for sensor telemetry smoothing across tracking cockpit views (ATT-1276):
1. *Smart Athletic Defaults*:
• When a sensor field is created or configured with `SensorType.POWER` in `EditSensorFieldViewModel`, the system SHALL default the filter type to 3-second moving average (`FilterType.MOVING_AVERAGE_TIME`, constant `3.0`, unit `"sec"`), preventing erratic 1-second raw fluctuations during workouts.
• In `TrackingViewsDatabaseManager`, when generating default cockpit views for bike power (`ActivityType.BIKE_POWER`), the system SHALL initialize `SensorType.POWER` rows with `FilterType.MOVING_AVERAGE_TIME` and `filterConstant = 3`.
• For all other sensor types (`HR`, `SPEED_mps`, `CADENCE`, `ALTITUDE`, `TEMPERATURE`), the system SHALL default to `FilterType.INSTANTANEOUS` (constant `1.0`).
2. *Intuitive Quick Presets in ConfigureFilterDialog*:
• In `ConfigureFilterDialog.kt`, the system SHALL present a dedicated 1-tap preset selection group comprising 7 distinct choices:
- Direct / Instantaneous (1s): `FilterType.INSTANTANEOUS`, constant `1.0` (`@string/filter_preset_direct`).
- 3 Seconds (Power): `FilterType.MOVING_AVERAGE_TIME`, constant `3.0`, unit `"sec"` (`@string/filter_preset_3s`).
- 10 Seconds (Pacing): `FilterType.MOVING_AVERAGE_TIME`, constant `10.0`, unit `"sec"` (`@string/filter_preset_10s`).
- 30 Seconds (Endurance): `FilterType.MOVING_AVERAGE_TIME`, constant `30.0`, unit `"sec"` (`@string/filter_preset_30s`).
- Session Average (Ø): `FilterType.AVERAGE`, constant `1.0` (`@string/filter_preset_avg`).
- Session Max: `FilterType.MAX_VALUE`, constant `1.0` (`@string/filter_preset_max`).
- Custom / Expert: Granular manual configuration (`@string/filter_preset_custom`).
• The active preset SHALL be highlighted dynamically based on the current filter configuration.
• Selecting a preset SHALL immediately update the staged filter type, constant, and unit, and reflect in the explanatory preview surface without requiring manual entry.
3. *Explanatory Guidance Surface*:
• The dialog SHALL display a Material 3 container presenting a human-readable summary (`getSummary`) and detailed athletic explanation (`getDetails`), keeping the user informed of the active filter's behavior.
4. *Expandable Custom / Expert Controls*:
• When Custom / Expert is selected, the dialog SHALL display granular inputs: `FilterType` dropdown, numeric input for time/samples/alpha, and unit selector (`sec`, `min`, `samples`), preserving full mathematical control.
5. *Preserved Modal Bottom Sheet & Action Contracts*:
• The dialog SHALL compose `AppModalBottomSheet` with `FilterAlt` icon and `statusBarsPadding()` / `navigationBarsPadding()`.
• The action bar SHALL compose `AppDialogActions.SaveCancel` with "Abbrechen" (`@string/Cancel`) and "Speichern" (`@string/save`), staging modifications until saved.
6. *9-Language Localization Parity*:
• All preset names, section headers, and labels SHALL be fully localized across all 9 supported application locales (EN, DE, ES, FR, IT, JA, NL, PL, PT).

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: `REQ-UI-149` (item 3: ConfigureFilterDialog) and `REQ-UI-150` (item 2: ConfigureFilterDialog Save verb).
2. *Historical Origin & Commit Trace*: Introduced in `ATT-900` / `ATT-1029` (commits `69e4f1a2` / `e971cf62`) and standardized in `ATT-1034` (commit `f849622d`).
3. *Root Reason for Existing Formulation*: Baseline migration from XML DialogFragment to Compose AppModalBottomSheet and standardizing button verbs from "OK" to "Speichern". The internal form body was ported as a direct representation of legacy dropdowns without domain-specific athletic presets.
4. *Preservation of Core Invariants*: AppModalBottomSheet contract (`REQ-UI-149`), SaveCancel semantics with "Speichern" (`REQ-UI-150`), uncommitted change discarding on dismiss, underlying BANALService filter algorithms, and SQLite schema compatibility (`ROWS_TABLE.FILTER_TYPE`, `FILTER_CONSTANT`) remain 100% strictly intact.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an athlete editing a cockpit sensor field,
• *When* selecting `SensorType.POWER` from the sensor dropdown,
• *Then* the filter SHALL automatically initialize to 3-second moving average (`FilterType.MOVING_AVERAGE_TIME`, 3.0s).
• *Given* the athlete opens `ConfigureFilterDialog`,
• *When* tapping any quick preset chip (e.g. 10s Pacing),
• *Then* the filter configuration SHALL immediately update, the chip SHALL be visually highlighted, and the explanatory card SHALL update without opening technical dropdowns.
• *Given* `ConfigureFilterDialog`,
• *When* the athlete taps "Manuell / Experte" (or has a custom filter active),
• *Then* the dialog SHALL display the `FilterType` dropdown, numeric input, and unit spinner, allowing arbitrary customization.
• *Given* uncommitted changes in `ConfigureFilterDialog`,
• *When* tapping "Abbrechen" or dismissing the sheet,
• *Then* modifications SHALL be discarded.
• *When* tapping "Speichern",
• *Then* modifications SHALL be saved to the ViewModel and persisted upon confirming the sensor field dialog.
• *Given* any of the 9 supported locales (EN, DE, ES, FR, IT, JA, NL, PL, PT),
• *Then* all preset chips and section headers SHALL render in the active language with zero untranslated fallbacks.

**Invariants**: AppModalBottomSheet contract (`REQ-UI-149`), SaveCancel button semantics (`REQ-UI-150`), BANALService filter mathematical integrity, and SQLite schema compatibility MUST NOT be broken.

### REQ-UI-168: Workout Cockpit Independent Theme Selector (System vs Always Dark AMOLED) [Verified]
- **Tests**: `TST-UI-120`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: Immer Dunkel ]` reflecting the persisted mode.
• *Given* an athlete changes the mode to `Immer Dunkel` and taps \"Abbrechen\",
• *When* the dialog dismisses,
• *Then* the change SHALL be discarded without mutating SharedPreferences.
• *Given* an athlete changes the mode to `Immer Dunkel` and taps \"Speichern\",
• *When* saved,
• *Then* `setCockpitThemeMode(CockpitThemeMode.ALWAYS_DARK)` SHALL be executed and persisted.
• *Given* an active workout on `TrackingTabsScreen` with `ALWAYS_DARK` configured while device OS is in Light Mode,
• *When* viewing Pages 1..N (telemetry tiles, sensor data, map),
• *Then* the cockpit SHALL render in dark theme (`DarkColorScheme` / AMOLED black).
• *When* viewing Page 0 (Control Tracking),
• *Then* the screen SHALL render in light theme matching the system.
• *Given* any of the 9 supported locales (EN, DE, ES, FR, IT, JA, NL, PL, PT),
• *Then* all cockpit theme string resources SHALL render in the active language.

**Invariants**: `AppModalBottomSheet` contract (`REQ-UI-149`), `SaveCancel` button semantics (`REQ-UI-150`), existing display toggles (`REQ-SET-052`), and ambient theme behavior on non-cockpit screens MUST NOT be broken.
- **Rationale**: The system SHALL provide an independent theme selector specifically for the workout tracking cockpit within Display Settings, enabling athletes to force AMOLED Pure Black mode during workouts while isolating non-cockpit screens (ATT-1267):
1. *Dedicated Setting in DisplaySettingsDialog*:
• In `DisplaySettingsDialog.kt`, the system SHALL display a dedicated section titled `Cockpit-Design (während des Trainings)` (`@string/cockpit_theme_title`) with an explanatory subtitle `@string/cockpit_theme_description` clarifying that the setting applies exclusively to active workout data and sensor displays, while menus and \"Control Tracking\" retain the standard theme.
• The selector SHALL provide a Material 3 `SingleChoiceSegmentedButtonRow` offering 2 mutually exclusive modes: `Systemstandard` (`@string/cockpit_theme_system`, `CockpitThemeMode.SYSTEM`, default) following `isSystemInDarkTheme()`, and `Immer Dunkel` (`@string/cockpit_theme_always_dark`, `CockpitThemeMode.ALWAYS_DARK`) rendering the cockpit in high-contrast AMOLED Pure Black (`#000000`).
• The selected mode SHALL be staged in dialog state upon selection. Tapping \"Abbrechen\" SHALL discard changes without mutating preferences; tapping \"Speichern\" SHALL commit the selection to persistent storage and dismiss the dialog.
2. *Persistent Storage & Lifecycle*:
• In `TrainingApplication`, the system SHALL persist the selected mode under SharedPreferences key `KEY_COCKPIT_THEME_MODE = "cockpit_theme_mode"` (default `"system"`), exposing `getCockpitThemeMode(): CockpitThemeMode` and `setCockpitThemeMode(mode: CockpitThemeMode)`.
3. *Cockpit Scope Boundary & Theme Isolation*:
• In `TrackingTabsScreen.kt`, the system SHALL observe `cockpitThemeMode` and determine the effective cockpit theme: `isCockpitDark = if (mode == CockpitThemeMode.ALWAYS_DARK) true else isSystemInDarkTheme()`.
• *Workout Cockpit (Pages 1..N)*: The telemetry pages (`TrackingTabGridContent`), sensor tiles, dynamic gauges, live map, elevation profile, live segments, and tab preview/config headers SHALL be enclosed in `ATrainingTrackerTheme(darkTheme = isCockpitDark)`.
• *Control Tracking (Page 0)*: `ControlTrackingScreen` (device pairing, protocol selection, sport picker, start/pause/stop) SHALL remain enclosed in the default ambient app theme and SHALL NOT be inverted to dark mode when `isSystemInDarkTheme() == false`.
• *Global App Shell*: All other activities, fragments, and Compose destinations (Navigation Drawer, History, Periods, Settings Dialogs) SHALL remain completely unaffected by the cockpit theme setting.
4. *100% Localization Parity Across 9 Locales*:
• All user-facing strings (`cockpit_theme_title`, `cockpit_theme_description`, `cockpit_theme_system`, `cockpit_theme_always_dark`) SHALL be translated across all 9 supported application locales (EN, DE, ES, FR, IT, JA, NL, PL, PT) with zero untranslated fallbacks and strict positional format compliance (`REQ-UI-106`, `REQ-UI-122`).

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: `REQ-UI-101` (*Neutral Backgrounds*) and `REQ-SET-052` (*Display Settings Dialog & State Parity*).
2. *Historical Origin & Commit Trace*: `REQ-UI-101` established in early architecture for neutral surfaces; `REQ-SET-052` introduced in `ATT-261` and standardized in `ATT-1016` (`DisplaySettingsDialog`).
3. *Root Reason for Existing Formulation*: App previously delegated theme strictly to `isSystemInDarkTheme()`. Adding a specialized cockpit override enables OLED battery savings and sunlight glare reduction during training without disturbing general app navigation.
4. *Preservation of Core Invariants*: `DisplaySettingsDialog` preserves `AppModalBottomSheet` (`REQ-UI-149`), SaveCancel semantics with \"Speichern\" (`REQ-UI-150`), existing display toggles (`forcePortrait`, `keepScreenOn`, `noUnlocking`), and default ambient styling on non-cockpit screens.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an athlete opening Navigation Drawer -> Settings -> Display,
• *When* `DisplaySettingsDialog` appears,
• *Then* a dedicated section \"Cockpit-Design (während des Trainings)\" SHALL be displayed with the 2-way segmented button row `[ Systemstandard

### REQ-UI-169: AMOLED Pure Black Cockpit Theme (#000000) [Verified]
- **Tests**: `TST-UI-121`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `Theme.kt`, `Color.kt`, `TrackingTabsScreen.kt`
- **Rationale**: The system SHALL provide a dedicated AMOLED Pure Black (`#000000`) color scheme for the workout tracking cockpit whenever dark mode is active (ATT-1263):
1. *Color Scheme Definition (`AmoledDarkColorScheme`)*:
• In `Theme.kt`, `AmoledDarkColorScheme` SHALL derive from `DarkColorScheme` overriding `background = Color(0xFF000000)`, `surface = Color(0xFF000000)`, `surfaceVariant = Color(0xFF000000)`, `surfaceDim = Color(0xFF000000)`, `surfaceContainer = Color(0xFF000000)`, `outlineVariant = Color(0xFF2C2C2E)`, `outline = Color(0xFF38383A)`, `onSurface = Color(0xFFFFFFFF)`, `onBackground = Color(0xFFFFFFFF)`, and `onSurfaceVariant = Color(0xFFC4C6D0)`.
2. *Theme Composable Parameterization*:
• `ATrainingTrackerTheme` SHALL accept `amoled: Boolean = false`. When `darkTheme == true` and `amoled == true`, the composable SHALL apply `AmoledDarkColorScheme`.
3. *Cockpit Scope Integration*:
• In `TrackingTabsScreen.kt`, cockpit pages (Pages 1..N: `TrackingTabGridContent`) and `LapButton` SHALL be wrapped with `ATrainingTrackerTheme(darkTheme = isCockpitDark, amoled = isCockpitDark)`.
• `ControlTrackingScreen` (Page 0) and the outer application shell SHALL remain in standard ambient theme without AMOLED override.
4. *Zone Color & Indicator Integrity*:
• Sensor field cards (`SensorFieldView`) with active athletic zones SHALL blend the zone tint (`fieldState.zoneColor.copy(alpha = 0.12f)`) over `#000000` with the 6dp vertical indicator strip clearly visible.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: `REQ-UI-101` (*Neutral Backgrounds*) and `REQ-UI-168` (*Cockpit Theme Selector*).
2. *Historical Origin & Commit Trace*: `REQ-UI-101` established neutral surfaces; `REQ-UI-168` established the independent cockpit theme selector.
3. *Root Reason for Existing Formulation*: Standard charcoal grey (`#1B1B1F`) keeps OLED pixels illuminated. Pure black (`#000000`) shuts off OLED pixels completely for maximum contrast and battery conservation.
4. *Preservation of Core Invariants*: App shell and Control Tracking remain untouched in standard theme; athletic zone colors and 6dp strips remain prominent.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an active workout on `TrackingTabsScreen` with dark cockpit mode active (`isCockpitDark == true`),
• *When* viewing Pages 1..N (telemetry tiles, sensor grid, map, elevation profile, live segments),
• *Then* `MaterialTheme.colorScheme.background` and `surface` SHALL evaluate to `Color(0xFF000000)` (pure pitch black).
• *When* inspecting sensor field cards without zone colors,
• *Then* container color SHALL be `Color(0xFF000000)` with `Color(0xFF2C2C2E)` borders.
• *When* inspecting telemetry text and metric labels,
• *Then* primary text SHALL be `Color(0xFFFFFFFF)` (pure white).
• *When* viewing Page 0 (Control Tracking) or navigating to drawer/settings,
• *Then* screens SHALL NOT use `AmoledDarkColorScheme`.

**Invariants**: Non-cockpit ambient theme behavior, zone color visibility, and `REQ-UI-168` selection contracts MUST NOT be broken.

### REQ-UI-170: Comprehensive Cockpit Dark Theme (Top Bar, Tab Row, and Navigation Bar) [Verified]
- **Tests**: `TST-UI-122`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `TrackingTabsScreen.kt`, `Theme.kt`, `Color.kt`
- **Rationale**: The system SHALL extend the workout cockpit dark theme across the entire `TrackingTabsScreen` layout, unifying top bar, tab bar, content, and navigation bar under AMOLED pure black on telemetry views while isolating Page 0 (ATT-1413):
1. *Dynamic Cockpit Theme State Resolution*:
• The system SHALL resolve cockpit theme states via pure function `resolveEffectiveCockpitThemeState(screenMode, currentPage, cockpitThemeMode, isSystemDark)`: on telemetry tabs (Pages 1..N in TRACKING mode, or all pages in CONFIGURATION/PREVIEW mode), `darkTheme = isCockpitDark` and `amoled = isCockpitDark`; on Control Tracking (Page 0 in TRACKING mode), `darkTheme = isSystemDark` and `amoled = false`.
2. *Root Theme Enclosure*:
• In `TrackingTabsScreen.kt`, the root `Surface` and all child containers SHALL be enclosed within `ATrainingTrackerTheme(darkTheme = themeState.darkTheme, amoled = themeState.amoled)`.
3. *AMOLED Header & Tab Row Harmonization*:
• In `AmoledDarkColorScheme`, `primaryContainer` SHALL be `Color(0xFF000000)`, `onPrimaryContainer` SHALL be `Color(0xFFFFFFFF)`, and `surfaceContainerHighest` SHALL be `Color(0xFF000000)`.
• The top header `Surface(color = MaterialTheme.colorScheme.primaryContainer)` and `PrimaryScrollableTabRow(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest)` SHALL render in true pitch black (`#000000`).
4. *System Bars & Navigation Inset Integration*:
• In `Theme.kt`, `WindowCompat.getInsetsController` SHALL update `isAppearanceLightStatusBars = !darkTheme` and `isAppearanceLightNavigationBars = !darkTheme` with `statusBarColor = colorScheme.surface.toArgb()` and `navigationBarColor = colorScheme.surface.toArgb()`, guarded against detached/finishing activities.
• In `TrackingTabsScreen.kt`, a `DisposableEffect` SHALL restore ambient system bar contrast (`!isSystemDark`) upon screen exit.
5. *Contrast Compliance*:
• All text and indicators in `PrimaryScrollableTabRow` and `SensorStatus` SHALL meet WCAG 2.1 AA standards.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: `REQ-UI-168` (*Cockpit Theme Selector*) and `REQ-UI-169` (*AMOLED Pure Black Cockpit Theme*).
2. *Historical Origin & Commit Trace*: `ATT-1267` introduced cockpit theme selection; `ATT-1263` introduced `AmoledDarkColorScheme`. Both originally themed only inner content inside the pager.
3. *Root Reason for Existing Formulation*: Initial cautious scoping avoided the top header and navigation padding to prevent unintended bleed into navigation. However, on device, this caused bright light-blue top bars and white bottom bars during night rides (`cockpit_dark_mode_partial_top_bottom_light.png`).
4. *Preservation of Core Invariants*: Page 0 strictly adheres to the user mandate remaining in ambient theme when device is in light mode; non-cockpit destinations remain in ambient theme.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an active workout on `TrackingTabsScreen` with `isCockpitDark == true` on a device in Light Mode,
• *When* viewing telemetry Pages 1..N,
• *Then* the top bar, tab row, telemetry cards, and bottom navigation padding SHALL render in pure pitch black (`#000000`) with high-contrast text and dark system bars.
• *When* swiping or navigating to Page 0 (Control Tracking),
• *Then* the entire screen (top bar, tab row, and Page 0 controls) SHALL render in the ambient system theme (Light Mode).
• *When* navigating away from `TrackingTabsScreen`,
• *Then* system bars SHALL cleanly revert to ambient theme without state leakage.

**Invariants**: Zero broken non-cockpit themes; WCAG 2.1 AA text contrast compliance; Page 0 ambient theme isolation.

### REQ-UI-171: High-Contrast Cockpit Typography and Subtle Tile Grid for Sunlight Legibility [Verified]
- **Tests**: `TST-UI-123`, `TST-UI-166`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `SensorFieldView.kt`, `Theme.kt`, `Color.kt`
- **Rationale**: The system SHALL optimize typography weights, text color hierarchies, and tile divider borders within the workout tracking cockpit for instant legibility in direct sunlight and with sports sunglasses (ATT-1264):
1. *SemiBold Primary Sensor Metrics*:
• In `SensorFieldView.kt`, the primary numeric reading `fieldState.value` SHALL render with semibold font weight (`FontWeight.SemiBold`) across all sensor view sizes (`XSMALL`, `SMALL`, `NORMAL`, `LARGE`, `XLARGE`, `HUGE`, `XHUGE`).
• In `AmoledDarkColorScheme`, `onSurface` SHALL be `Color(0xFFFFFFFF)` (pure white), ensuring maximum contrast against `#000000` (21:1 ratio).
2. *Muted Crisp Labels and Unit Annotations*:
• In `AmoledDarkColorScheme`, `onSurfaceVariant` SHALL be `Color(0xFF9E9E9E)`.
• In `SensorFieldView.kt`, metric labels (`fieldState.label`), unit annotations (`fieldState.units`), and filter descriptions (`fieldState.filterDescription`) SHALL render with `MaterialTheme.colorScheme.onSurfaceVariant` (`#9E9E9E` in AMOLED dark mode, 7.6:1 contrast ratio), establishing clear hierarchy and eliminating visual clutter.
3. *Subtle Tile Divider Grid*:
• In `AmoledDarkColorScheme`, `outlineVariant` SHALL be `Color(0xFF262626)`.
• Sensor field cards in `SensorFieldView.kt` SHALL render tile demarcations using `BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)`, cleanly separating fields without harsh contrast or distraction.
4. *Preserved App Isolation*:
• Standard app dark mode (`DarkColorScheme`) SHALL retain `outlineVariant = Color(0xFF44474F)` and `onSurfaceVariant = Color(0xFFC4C6D0)`, strictly isolating non-cockpit destinations.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: `REQ-UI-169` (*AMOLED Pure Black Cockpit Theme*) and `REQ-UI-101` (*Neutral Backgrounds*).
2. *Historical Origin & Commit Trace*: `ATT-1263` introduced `AmoledDarkColorScheme` (#000000) and initialized `AmoledOutlineVariant` to `#2C2C2E` and `onSurfaceVariant` to `#C4C6D0`. In `SensorFieldView.kt`, text colors were using `onSurface` for values, units, and labels, while font weights used default typography (`Normal`).
3. *Root Reason for Existing Formulation*: During the initial pure-black pass (`ATT-1263`), the priority was turning off OLED background pixels (`#000000`). Typography and subtle tile demarcation were left at default token mappings. However, in outdoor sunlight and when wearing tinted cycling sunglasses, normal-weight numerals and white unit/label text produce visual clutter with low contrast distinction between metric values and metadata, while borders at `#2C2C2E` can be overly noticeable or insufficiently subtle compared to `#262626`.
4. *Preservation of Core Invariants*: Primary readings remain true white `#FFFFFF` (`onSurface`); standard app dark mode (`DarkColorScheme`) remains completely unchanged for non-cockpit views; contrast ratios exceed WCAG AAA (7.6:1 for `#9E9E9E`, 21:1 for `#FFFFFF`); athletic zone color overlays remain fully visible.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an active workout on `TrackingTabsScreen` in AMOLED dark mode,
• *When* inspecting sensor field values (`fieldState.value`),
• *Then* the text SHALL render with `FontWeight.SemiBold` and color `Color(0xFFFFFFFF)`.
• *When* inspecting metric labels (`fieldState.label`), units (`fieldState.units`), and filter descriptions (`fieldState.filterDescription`),
• *Then* the text SHALL render with color `Color(0xFF9E9E9E)` (`onSurfaceVariant`).
• *When* inspecting sensor field tile borders,
• *Then* borders SHALL render with `BorderStroke(1.dp, Color(0xFF262626))` (`outlineVariant`).
• *When* inspecting non-cockpit views in standard dark mode,
• *Then* `onSurfaceVariant` SHALL remain `Color(0xFFC4C6D0)` and `outlineVariant` SHALL remain `Color(0xFF44474F)`.

**Invariants**: WCAG 2.1 AAA text contrast compliance; non-cockpit theme isolation; active zone color visibility.

### REQ-UI-172: Configurable Zone Visualization Options and Theme-Aware Live Preview [Verified]
- **Tests**: `TST-UI-124`, `TST-UI-393`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `SettingsDataStore.kt`, `SettingsDataStoreJavaHelper.kt`, `ZonesSettingsActivity.kt`, `SensorFieldState.kt`, `SensorFieldView.kt`, `TrackingViewModel.kt`
- **Rationale**: The system SHALL provide configurable zone visualization options per athletic zone profile (`HR_RUN`, `HR_BIKE`, `PWR_BIKE`, and `HRV (DFA a1)`) within `ZonesSettingsActivity` and reflect the chosen visual cues dynamically across workout cockpit sensor tiles (ATT-1265). ATT-5200 updates string resource `zone_display_background` across all 9 application locales to "Tinted background" (*Eingefärbter Hintergrund*) to resolve user confusion with Dark/AMOLED themes. ATT-7524 establishes symmetrical defaults (background, left bar, and right bar enabled, text color disabled) and wires dedicated DataStore persistence for `HRV (DFA a1)`:
1. *Per-Profile Visualization Options (`ZoneDisplayOptions`)*:
• The system SHALL define four independent, multi-selectable visual presentation cues for each `ZoneType` and the dedicated `HRV (DFA a1)` profile:
- *Tinted background* (`showBackground`: Boolean, localized via `R.string.zone_display_background`): Renders the card container with tinted zone color at 12% opacity (`zoneColor.copy(alpha = 0.12f)`). When disabled, the card container remains neutral `MaterialTheme.colorScheme.surface` (pure `#000000` in AMOLED dark mode). Across all 9 locales, the string resource SHALL convey active zone color tinting rather than daylight theme ("Tinted background" / "Eingefärbter Hintergrund").
- *Left bar* (`showLeftBar`: Boolean): Renders a 6dp vertical indicator strip on the left edge of the tile colored with the active `zoneColor`.
- *Right bar* (`showRightBar`: Boolean): Renders a 6dp vertical indicator strip on the right edge of the tile colored with the active `zoneColor`.
- *Text color* (`showTextColor`: Boolean): Renders the primary numeric metric reading (`fieldState.value`) using the active `zoneColor`. When disabled, the reading renders in standard `MaterialTheme.colorScheme.onSurface`.
• The default configuration for all profiles SHALL be: `showBackground = true`, `showLeftBar = true`, `showRightBar = true`, `showTextColor = false`, `showGauge = false`. In DataStore, an unset `rightBarKey` SHALL fall back to `true`, while explicitly saved user preferences (`false`) SHALL be strictly preserved.
2. *Persistent Preferences Storage (`SettingsDataStore.kt`)*:
• The system SHALL store the four visual toggles independently for each `ZoneType` (`HR_RUN`, `HR_BIKE`, `PWR_BIKE`) and dedicated `HRV (DFA a1)` preferences (`hrv_dfa_a1_zone_show_background`, `hrv_dfa_a1_zone_show_left_bar`, `hrv_dfa_a1_zone_show_right_bar`, `hrv_dfa_a1_zone_show_text_color`, `hrv_dfa_a1_zone_show_gauge`) in DataStore preferences.
• Modifying a toggle SHALL immediately persist the updated setting to DataStore on an IO dispatcher and update the cached `SettingsSnapshot`.
3. *Settings UI Integration & Theme-Aware Live Preview (`ZonesSettingsActivity.kt`)*:
• In `ZonesSettingsActivity.kt`, each profile tab (`HR_RUN`, `HR_BIKE`, `PWR_BIKE`, and `HRV (DFA a1)`) SHALL display a dedicated section titled "Zonendarstellung" / "Zone Visualization" positioned below the threshold/band cards.
• The section SHALL present four interactive toggle controls corresponding to the four display options.
• For the `HRV (DFA a1)` tab, toggle changes SHALL be bound reactively to `SettingsDataStore` (`saveHrvZoneDisplayOptions`), eliminating ephemeral state loss.
• Directly below the toggle controls, the section SHALL render an interactive live preview tile (`SensorFieldView`) strictly inheriting `MaterialTheme.colorScheme` from the active application/system theme (respecting dark mode or light mode), with an interactive zone/band selector row.
4. *Cockpit Telemetry Rendering (`SensorFieldView.kt`)*:
• In `SensorFieldView.kt`, when `fieldState.zoneColor != Color.Transparent`: container background evaluates to tinted color or neutral surface, left bar renders if enabled, right bar renders if enabled, and numeric text adopts zoneColor if enabled.
5. *Preserved Invariants & Guardrails*:
• Established zone color palette (`#7FFF00`, `#008000`, `#FFA500`, `#FF0000`, `#9400D3`) MUST NOT be altered.
• Deselecting all four toggles gracefully renders neutral unaccented telemetry without errors or layout clipping.
• Non-zone sensor fields remain completely unaffected.
• Zero disk I/O on 1Hz sensor tick updates (options resolved in-memory on `SensorFieldState`).
• 100% localization parity maintained across all 9 supported locales.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Amends `REQ-UI-172` (*Configurable Zone Visualization Options and Theme-Aware Live Preview*, `ATT-1265`, `ATT-5200`, `ATT-7524`). Targets `ZoneDisplayOptions.kt`, `SettingsDataStore.kt`, `ZonesSettingsActivity.kt`, and `docs/requirements/req_ui_design_system.md`.
2. *Historical Origin & Commit Trace*: `ATT-1265` (commit `a48c8a38`, Sprint 2026-40.5) established `ZoneDisplayOptions` with `showRightBar = false` by default. `ATT-5200` clarified "Tinted background" localization. `ATT-7317` introduced the 4th `HRV (DFA a1)` tab in `ZonesSettingsActivity` with ephemeral display options.
3. *Root Reason for Existing Formulation*: `showRightBar` was originally `false` to avoid potential visual crowding before symmetrical card layouts were standardized; `HRV (DFA a1)` lacked persistent DataStore keys upon initial tab introduction.
4. *Preservation of Core Invariants*: Symmetrical edge bars frame active tiles cleanly on both compact and wide screens; unset `rightBarKey` defaults to `true` while explicitly saved user `false` preferences in DataStore are strictly preserved; 9-language translation parity is 100% maintained.

**Acceptance Criteria (Given-When-Then)**:
• *Given* a fresh installation or uncustomized `ZoneDisplayOptions` in `SettingsDataStore`,
• *When* reading `ZoneDisplayOptions` for `HR_RUN`, `HR_BIKE`, `PWR_BIKE`, or `HRV (DFA a1)`,
• *Then* `showBackground == true`, `showLeftBar == true`, `showRightBar == true`, and `showTextColor == false`.
• *Given* an athlete in `ZonesSettingsActivity` on the `HRV (DFA a1)` tab,
• *When* toggling any display option (e.g. disabling Right bar or enabling Text color),
• *Then* the preference SHALL immediately persist to `SettingsDataStore` and be reflected upon tab switches and activity recreations.
• *Given* an existing user who previously explicitly saved `showRightBar = false`,
• *When* reading from `SettingsDataStore`,
• *Then* the stored `false` preference SHALL be respected without overwrite.
• *Given* an athlete in `ZonesSettingsActivity` on the Run HR tab,
• *When* viewing the "Zonendarstellung" section,
• *Then* four toggle controls (Tinted background, Left bar, Right bar, Text color) and a live preview tile SHALL be rendered below the zone threshold cards.
• *Given* an athlete viewing the "Zonendarstellung" section in any supported language,
• *When* inspecting the first toggle,
• *Then* it SHALL display "Tinted background" (EN), "Eingefärbter Hintergrund" (DE), "Fondo tintado" (ES), "Arrière-plan teinté" (FR), "Sfondo tinto" (IT), "色付き背景" (JA), "Getinte achtergrond" (NL), "Zabarwione tło" (PL), or "Fundo matizado" (PT).
• *When* the athlete toggles "Right bar" ON and "Tinted background" OFF,
• *Then* the live preview tile SHALL immediately update to show a right vertical strip on neutral surface with no background tint, and the preferences SHALL be persisted to DataStore.
• *Given* the host device is in AMOLED Dark Mode,
• *When* viewing the live preview tile in `ZonesSettingsActivity`,
• *Then* the preview tile card background SHALL render in pure black (`#000000`) when background tint is toggled OFF.
• *Given* an active workout with Run HR zone configured with "Text color" ON and "Tinted background" OFF,
• *When* the heart rate enters Zone 4 (Threshold),
• *Then* the heart rate metric value SHALL render in red (`#FF0000`) on a neutral dark surface with no background tint.
• *Given* all four zone visualization toggles are switched OFF,
• *When* viewing live tracking or the preview tile,
• *Then* the tile SHALL render standard neutral telemetry without accent bars or tint.

**Invariants**: Zone color hex palette (`REQ-UI-101`), AMOLED cockpit theme tokens (`REQ-UI-168`..`171`), zone threshold calculation formulas, and non-zone sensor field rendering MUST NOT be altered.

### REQ-UI-173: Legible Inactive Sensor Status Indicators in Dark Mode [Verified]
- **Tests**: `TST-UI-125`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `SensorStatus.kt`
- **Rationale**: The system SHALL ensure that inactive and disconnected sensor icons in the top `SensorStatus` header bar remain clearly legible and discernible across all application themes (Light, Dark, and AMOLED Pure Black) while maintaining clear visual subordination to active connected sensors (ATT-1437):
1. *Content Color & Opacity Decoupling*:
• In `SensorStatus.kt`, all sensor icons SHALL evaluate `tint = MaterialTheme.colorScheme.onSurface` (pure white `#FFFFFF` in dark mode, near-black `#1A1C1E` in light mode).
• The system SHALL eliminate the compounded low-luminance `outline` color tint for inactive sensors.
• Inactive sensors (`isAvailable == false`) SHALL render with a defined disabled opacity of `0.38f` (`alpha = 0.38f`), aligning with Material Design 3 disabled content standards.
• Active sensors (`isAvailable == true`) SHALL render with full opacity (`alpha = 1.0f`).
2. *Contrast & WCAG 2.1 Compliance*:
• Inactive sensor icons SHALL achieve a minimum contrast ratio of $\ge 3:1$ against the underlying header surface in AMOLED Pure Black (`#000000`), standard Dark (`#1B1B1F`), and Light (`#FFFFFF` / `#D3E3FD`) modes, complying with WCAG 2.1 Non-text Contrast (SC 1.4.11).
• Active sensor icons SHALL maintain a minimum contrast ratio of $\ge 7:1$ (exceeding WCAG AAA).
3. *Preserved Invariants & Interactivity*:
• Tapping an inactive sensor icon SHALL continue to open the `SensorSourceDialog` (REQ-UI-049) to allow sensor inspection, pairing, or diagnostics.
• Sensor icon ordering (`TIME_ACTIVE`, `ACCURACY`, `ALTITUDE`, `DISTANCE_m`, `SPEED_mps`, `CADENCE`, `HR`, `POWER`) and size (22dp with 6dp horizontal padding) MUST NOT be altered.
• The hamburger navigation menu button and top bar layout in `TrackingTabsScreen.kt` MUST NOT be affected.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: `REQ-UI-048` (*Clean Sensor Status Header*) and `REQ-UI-170` (*Comprehensive Cockpit Dark Theme*).
2. *Historical Origin & Commit Trace*: `REQ-UI-048` established the icon-only header bar; `REQ-UI-170` (ATT-1413) themed the cockpit top header surface to pure black `#000000` in AMOLED dark mode.
3. *Root Reason for Existing Formulation*: Originally, inactive sensors used `tint = outline` with `.alpha(0.2f)`. On light backgrounds, this produced a soft ghost icon. However, when the top header was darkened to `#000000` in ATT-1413, applying 20% alpha to `AmoledOutline` (`#38383A`) caused the rendered pixel values to drop to `#0B0B0B` (1.02:1 contrast), rendering missing sensors completely invisible to the human eye.
4. *Preservation of Core Invariants*: Decoupling tint to `onSurface` and using standard `0.38f` alpha preserves the visual distinction between active and inactive sensors (6.25x contrast ratio differential) while restoring full visibility and interactivity in dark mode without altering layout or dialog behavior.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an active workout on `TrackingTabsScreen` in AMOLED dark mode (`isCockpitDark == true`),
• *When* viewing the top `SensorStatus` header bar with one or more disconnected/inactive sensors,
• *Then* inactive sensor icons SHALL be clearly visible against the pure black `#000000` background with contrast ratio $\ge 3:1$ and opacity `0.38f`.
• *When* viewing active connected sensors,
• *Then* active sensor icons SHALL render in full white `#FFFFFF` with opacity `1.0f`.
• *When* the athlete taps an inactive sensor icon,
• *Then* the `SensorSourceDialog` SHALL open displaying current sensor status and available hardware.
• *Given* the device is in Light Mode,
• *When* viewing the top `SensorStatus` bar,
• *Then* inactive sensor icons SHALL render legibly with `0.38f` opacity on the light header surface without disappearing.

**Invariants**: Sensor icon size (22dp), horizontal spacing (6dp), sensor order sequence, and `SensorSourceDialog` navigation MUST NOT be altered.

### REQ-UI-174: AMOLED Battery Saver Mode with Multi-Factor Display Dimming and Event-Based Wakeup [Verified]
- **Tests**: `TST-UI-126`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `DisplaySettingsDialog.kt`, `BatterySaverController.kt`, `TrackingTabsScreen.kt`, `TrainingApplication.java`, `strings_display.xml`
- **Rationale**: The system SHALL provide an intelligent AMOLED Battery Saver Mode that dynamically dims display brightness during endurance tracking based on topography and athletic intensity zones, while waking the display to full illumination on critical events (ATT-1268):
1. *Dedicated "Einsparungen" Display Settings Category*:
• In `DisplaySettingsDialog.kt`, the system SHALL group energy-saving options into a distinct section titled "Einsparungen" (`@string/settings_category_savings`), demarcated by a horizontal divider positioned above the Cockpit Dark Mode selector.
• The section SHALL provide a dedicated toggle: *Akkusparer / Display-Dimming* (`@string/prefs_battery_saver_title`, `@string/prefs_battery_saver_summary`), operating independently of the cockpit theme.
• Tapping "Speichern" SHALL persist the setting in `TrainingApplication` / SharedPreferences (`KEY_BATTERY_SAVER = "battery_saver"`, default `false`).
2. *Multi-Factor Adaptive Brightness State Machine*:
• During active tracking with battery saver enabled, the system SHALL evaluate the derived slope sensor (`SensorType.SLOPE` in %), active Heart Rate zone ($Z_{HR}$), and active Cycling Power zone ($Z_{PWR}$):
- *Full Dimming* (Default $0.15f$ / 15% brightness): Triggered when $\text{Slope} < 2.0\%$ **AND** $Z_{HR} \le \text{Zone 2}$ **AND** $Z_{PWR} \le \text{Zone 2}$ (for cycling). Maximizes OLED battery conservation during flat cruising, descent, or recovery.
- *Medium Dimming* (Default $0.50f$ / 50% brightness): Triggered when $2.0\% \le \text{Slope} \le 5.0\%$ **OR** $Z_{HR} == \text{Zone 3}$ **OR** $Z_{PWR} == \text{Zone 3}$. Provides comfortable visibility during tempo efforts while saving 30–50% display power.
- *Full Illumination* (1.0f / 100% brightness): Triggered when $\text{Slope} > 5.0\%$ **OR** $Z_{HR} \ge \text{Zone 4}$ **OR** $Z_{PWR} \ge \text{Zone 4}$. Ensures maximum peripheral visibility during hard climbs, threshold work, and intervals.
3. *Graceful Sensor Fallback & Damping Hysteresis*:
• If Power is absent (or sport is running/hiking), the system SHALL evaluate Slope and $Z_{HR}$.
• If Heart Rate is absent, the system SHALL evaluate Slope and $Z_{PWR}$.
• If neither Heart Rate nor Power is available, the system SHALL fall back to pure Slope thresholds ($<2\%$, $2-5\%$, $>5\%$).
• The system SHALL apply 3-second moving average smoothing to cycling power telemetry and a $3-5\text{s}$ damping hysteresis before executing downward brightness transitions to prevent rapid visual oscillations.
4. *Event-Based Wakeup Triggers*:
• The system SHALL immediately restore 100% full brightness (`WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE`) for $15\text{ seconds}$ upon:
- User touch interaction or tab swipe on `TrackingTabsScreen`.
- Contactless hand wave detected via hardware Proximity Sensor (`Sensor.TYPE_PROXIMITY`).
- Live Strava Segment status transition to *Approaching*, *Start*, or *Finish* (`LiveSegmentsRepository`).
- Manual lap split trigger (`[+] Runde`).
- Auto-pause trigger or acceleration restart from standstill.
• Overlapping wakeup events SHALL reset the 15-second countdown timer.
5. *Window-Scoped Brightness Safety Invariant*:
• Screen brightness overrides SHALL be applied strictly to the active `Activity` window attributes (`window.attributes.screenBrightness`), cleanly releasing upon backgrounding or screen exit.
• Minimum brightness SHALL enforce a safety floor of $\ge 0.05f$ to prevent the screen from turning completely off.
• Detailed fine-tuning of dimming percentages and slope thresholds SHALL be decoupled into the Advanced Tuning Preferences screen (ATT-1304).
6. *100% Localization Parity Across 9 Locales*:
• All newly introduced string resources SHALL be defined across all 9 supported locales (EN, DE, ES, FR, IT, JA, NL, PL, PT) with strict positional format specifiers.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an athlete opening Display Settings,
• *Then* an "Einsparungen" section SHALL appear separated by a divider above Cockpit Dark Mode, containing the Akkusparer switch.
• *Given* active tracking with Battery Saver enabled, slope $< 2\%$, and HR in Zone 1,
• *When* no interaction or workout event occurs,
• *Then* window brightness SHALL dim down to 15%.
• *Given* the display is in dimmed state,
• *When* the athlete waves their hand over the proximity sensor or touches the screen,
• *Then* brightness SHALL immediately return to 100% for 15 seconds.
• *Given* the athlete approaches a steep climb with slope $> 5\%$,
• *Then* brightness SHALL automatically elevate to 100% without requiring user interaction.
• *Given* an athlete running without a power meter,
• *Then* the state machine SHALL evaluate Slope and Heart Rate zones seamlessly without crash or delay.

**Invariants**: Screen brightness safety floor $\ge 0.05f$; non-cockpit window isolation; existing display options (`forcePortrait`, `keepScreenOn`, `noUnlocking`) preserved; 9-language translation parity.

### REQ-UI-175: Tracking Tabs Configuration Transition and Hierarchical Navigation [Verified]
- **Tests**: `TST-UI-127`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `MainActivityWithNavigation.kt`, `ATrainingTrackerApp.kt`, `TrackingTabsScreen.kt`, `TrackingTabPreviewHeader.kt`
- **Rationale**: The system SHALL ensure that selecting a sport type for tracking tab configuration reliably transitions `TrackingTabsScreen` to `ScreenMode.CONFIGURATION` for that sport type, and provides seamless hierarchical back navigation (ATT-1456):
1. *Activity-Scoped Reactive State Mutation*:
• In `MainActivityWithNavigation.kt`, `onActivityTypeSelected(activityType: ActivityType)` SHALL retrieve the Activity-scoped `TrackingTabsViewModel` via `ViewModelProvider` and directly invoke `trackingTabsViewModel.setExplicitActivityType(activityType)` and `trackingTabsViewModel.setScreenMode(ScreenMode.CONFIGURATION)`.
• The unobserved mutable property `pendingActivityType` SHALL be eliminated from `MainActivityWithNavigation.kt`.
2. *SingleTop Recomposition & Lifecycle Resilience*:
• In `ATrainingTrackerApp.kt`, `composable(NavRoutes.START_TRACKING)` SHALL scope `TrackingTabsViewModel` to the host Activity (`viewModelStoreOwner = activity`).
• The fragile `LaunchedEffect(activity.pendingActivityType)` bridge SHALL be eliminated, ensuring immediate reactive recomposition into `ScreenMode.CONFIGURATION` even when the athlete is already positioned on `NavRoutes.START_TRACKING`.
3. *Hierarchical Back Navigation & Preview Exit*:
• In `TrackingTabsScreen.kt`, when in `ScreenMode.CONFIGURATION`, pressing the system back button or back gesture SHALL transition the screen to `ScreenMode.PREVIEW` via `trackingTabsViewModel.handleBackPressToPreview()`.
• When in `ScreenMode.PREVIEW`, pressing the system back button or back gesture SHALL return the screen to `ScreenMode.TRACKING` via `trackingTabsViewModel.exitConfiguration()`, clearing the explicit activity type and preventing accidental application exit.
• In `TrackingTabPreviewHeader.kt`, the header SHALL provide an explicit Done/Exit action button (`Icons.Default.Check`) that invokes `trackingTabsViewModel.exitConfiguration()`.
4. *Preserved Invariants*:
• Normal tracking mode (`ScreenMode.TRACKING`), active telemetry, GPS tracking, and Page 0 Control tab MUST NOT be altered.
• Tab editing actions (adding, deleting, renaming tabs, and metric toggles) in `TrackingViewsRepository` MUST NOT be affected.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: `REQ-SET-050` (*Navigation Drawer Structure*) and `REQ-UI-103` (*Customizable Cockpits*).
2. *Historical Origin & Commit Trace*: `REQ-SET-050` organized the drawer; `REQ-UI-103` established tab configuration originally hosted in `ConfigTrackingTabsActivity` before being embedded directly into `TrackingTabsScreen`.
3. *Root Reason for Existing Formulation*: When configuration was embedded, an unobserved Activity property `pendingActivityType` was introduced with a Compose `LaunchedEffect`. However, navigating to `START_TRACKING` with `launchSingleTop = true` when already on that route suppresses recomposition, causing `LaunchedEffect` to never execute.
4. *Preservation of Core Invariants*: Scoping `TrackingTabsViewModel` to the Activity and mutating its `StateFlow` directly guarantees instant reactive recomposition, preserves all tab customization workflows, and completes the back navigation hierarchy without side effects.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an athlete on `NavRoutes.START_TRACKING` (or any other route) opening the navigation drawer,
• *When* the athlete taps "Tracking Tabs" (`drawer_tracking_layouts`) and selects an `ActivityType` (e.g., Radeln),
• *Then* `TrackingTabsScreen` SHALL immediately transition to `ScreenMode.CONFIGURATION` displaying `TrackingTabConfigHeader` for the chosen sport type.
• *Given* the athlete in `ScreenMode.CONFIGURATION`,
• *When* the athlete presses the system Back button/gesture or taps the Check button in the header,
• *Then* the screen SHALL transition to `ScreenMode.PREVIEW`.
• *Given* the athlete in `ScreenMode.PREVIEW`,
• *When* the athlete presses the system Back button/gesture or taps the Done button in `TrackingTabPreviewHeader`,
• *Then* the screen SHALL return to `ScreenMode.TRACKING` with standard telemetry and Page 0 control tabs.

**Invariants**: Active tracking telemetry, Page 0 Control Tracking, `TrackingViewsRepository` database operations, and cockpit theming MUST NOT be altered.

### REQ-UI-176: Synchronous Cockpit Tab Header Color Transitions [Verified]
- **Tests**: `TST-UI-128`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `TrackingTabsScreen.kt`, `Theme.kt`
- **Rationale**: The system SHALL eliminate the tab header typography color animation delay during theme transitions between Page 0 (Control Tracking) and Pages 1..N (Telemetry Tabs), ensuring tab title text colors update instantaneously in sync with the tab container background (ATT-1446):
1. *Direct Synchronous Text Color Binding*:
• In `TrackingTabsScreen.kt`, tab header title composables within `PrimaryScrollableTabRow` SHALL pass an explicit `color` parameter directly to `Text`: `color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant`.
• Explicit `color` evaluation SHALL bypass Material 3 `TabTransition`'s animated `LocalContentColor` interpolation, achieving 0ms instantaneous color synchronization on the exact recomposition frame.
2. *Tab Ripple & Specifier Coherence*:
• Each `Tab` in `PrimaryScrollableTabRow` SHALL explicitly supply `selectedContentColor = MaterialTheme.colorScheme.primary` and `unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant`, ensuring ripple indications and child icon/text defaults reflect the active theme.
3. *Contrast & Legibility Invariants*:
• In AMOLED Dark Mode (`#000000` background), selected tab text SHALL evaluate to `DarkPrimary` (`#A6C8FF`, contrast ratio $\ge 12:1$) and unselected tab text SHALL evaluate to `AmoledOnSurfaceVariant` (`#9E9E9E`, contrast ratio $\ge 7:1$), complying with WCAG 2.1 AAA.
• In Light Mode (`#C8E3FF` background), selected tab text SHALL evaluate to `LightPrimary` (`#1464F4`, contrast ratio $\ge 4.5:1$) and unselected tab text SHALL evaluate to `LightOnSurfaceVariant` (`#44474F`, contrast ratio $\ge 5:1$), complying with WCAG 2.1 AA.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: `REQ-UI-170` (*Comprehensive Cockpit Dark Theme*).
2. *Historical Origin & Commit Trace*: `ATT-1413` enclosed `TrackingTabsScreen` in dynamic theme state, flipping container background to `#000000` on Pages 1..N.
3. *Root Reason for Existing Formulation*: Standard Compose Material 3 `Tab` delegates to `TabTransition`, which animates `LocalContentColor` using `updateTransition(selected).animateColor(...)` (~250ms). When container color flips to `#000000` in 0ms, tab text slowly interpolates, causing visible lag and muddy contrast.
4. *Preservation of Core Invariants*: Passing explicit `color` to `Text` completely bypasses `TabTransition`'s animated `LocalContentColor`, eliminating the 250ms lag while maintaining full WCAG 2.1 AA contrast in both themes and preserving Page 0 ambient theme isolation.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an athlete on `TrackingTabsScreen` with device in Light Mode and cockpit configured for Always Dark,
• *When* the athlete navigates from Page 0 to Page 1,
• *Then* tab header text colors SHALL update synchronously in 0ms with the tab container background without intermediate muddy colors or animation lag.
• *When* the athlete navigates from Page 1 back to Page 0,
• *Then* tab header text colors SHALL update synchronously in 0ms with the light tab container background.
• *When* inspecting tab title contrast in AMOLED Dark Mode,
• *Then* contrast against `#000000` SHALL be $\ge 7:1$ for unselected tabs and $\ge 12:1$ for selected tabs.

**Invariants**: WCAG 2.1 AA text contrast compliance; Page 0 ambient theme isolation; Pager smooth scrolling and tab selection logic MUST NOT be altered.

### REQ-UI-177: Theme-Aware Post-Workout & Period Share Snapshot Canvas and Footer [Verified]
- **Tests**: `TST-UI-129`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `ShareUtils.kt`, `MapDetailLayout.kt`, `PeriodMapScreen.kt`
- **Rationale**: The system SHALL adapt the composite image canvas background and branding footer colors to the active theme (Dark Mode vs. Light Mode) when generating shared workout and period snapshots (ATT-1472):
1. *Decoupled Theme Color Token Resolution (`ShareThemeColors` / `ShareThemeResolver`)*:
• In `ShareUtils.kt`, the system SHALL resolve composite colors via `ShareThemeResolver.resolveColors(isDark: Boolean)` returning `ShareThemeColors`:
- *Light Mode* (`isDark == false`): `canvasBackground = Color.WHITE`, `footerBackground = Color.parseColor("#F5F5F5")`, and `footerTextColor = Color.DKGRAY` (`#444444`).
- *Dark Mode* (`isDark == true`): `canvasBackground = Color.BLACK` (`#000000`), `footerBackground = Color.parseColor("#1E1E1E")`, and `footerTextColor = Color.parseColor("#E0E0E0")`.
2. *Context-Aware Night Mode Detection & Call-Site Compatibility*:
• The system SHALL provide `ShareThemeResolver.isNightMode(context: Context): Boolean` evaluating `(context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES`.
• `combineAndShare` and `combineWorkoutAndShare` SHALL expose `isDark: Boolean = ShareThemeResolver.isNightMode(context)`, preserving 100% binary and source compatibility for existing callers (`MapDetailLayout.kt`, `PeriodMapScreen.kt`) while enabling explicit programmatic overrides.
3. *Contrast & WCAG 2.1 AAA Compliance*:
• In Dark Mode, footer text (`#E0E0E0`) on footer background (`#1E1E1E`) SHALL achieve a contrast ratio $\ge 7:1$ (measured 13.5:1), fully satisfying WCAG 2.1 Level AAA.
• In Light Mode, footer text (`Color.DKGRAY`) on footer background (`#F5F5F5`) SHALL maintain contrast ratio $\ge 7:1$ (measured 9.0:1), satisfying WCAG 2.1 Level AAA.
• Canvas background in Dark Mode SHALL be `Color.BLACK` (`#000000`), seamlessly matching AMOLED dark map tiles and dark card containers without white border artifacts.
4. *Sharing Pipeline & Geometry Invariants*:
• The system SHALL preserve footer height (125px), logo dimension ($80\times 80$), horizontal margin (24f), and text size (42f).
• PNG compression (100% quality), cache file directory (`context.cacheDir/images`), `FileProvider` URI generation, and intent dispatch on `Dispatchers.Main` MUST NOT be altered.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: `REQ-UI-059` (*Robust & Asynchronous Workout Sharing*) and `REQ-UI-101` (*Neutral Backgrounds*).
2. *Historical Origin & Commit Trace*: Introduced in early app versions when `ShareUtils.kt` was created to stitch map and header snapshots with a clean branding footer.
3. *Root Reason for Existing Formulation*: At the time `ShareUtils.kt` was authored, aTrainingTracker operated strictly in Light Mode; hardcoded `Color.WHITE` canvas clear color and `#F5F5F5` footer background were natural defaults. Following the introduction of Dark Mode and AMOLED Pure Black under Epic `ATT-1157`, these hardcoded light constants produce visual seams and a jarring bright white footer bar below dark workout graphics.
4. *Preservation of Core Invariants*: Historical Light Mode styling (`Color.WHITE`, `#F5F5F5`, `Color.DKGRAY`) is 100% preserved; binary and source compatibility for all callers is guaranteed via default argument `isDark`; FileProvider security, caching logic, and branding geometry remain unchanged.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an active app session operating in Light Mode,
• *When* the user shares a workout summary (`combineWorkoutAndShare`) or period summary (`combineAndShare`),
• *Then* the composite canvas background SHALL clear to `Color.WHITE`, the footer background SHALL be `#F5F5F5`, and the footer text SHALL be `Color.DKGRAY`.
• *Given* an active app session operating in Dark Mode (or AMOLED mode),
• *When* the user shares a workout summary or period summary,
• *Then* the composite canvas background SHALL clear to `Color.BLACK`, the footer background SHALL be `#1E1E1E`, and the footer text SHALL be `#E0E0E0`.
• *Given* any caller invoking `combineWorkoutAndShare(context, hBmp, mapBitmap, eBmp)` without specifying `isDark`,
• *Then* the function SHALL automatically evaluate `ShareThemeResolver.isNightMode(context)` and render the corresponding theme colors without error.

**Invariants**: Light Mode appearance, PNG 100% compression, FileProvider URI creation, background dispatcher offloading (`Dispatchers.Default`, `Dispatchers.IO`), and footer layout dimensions MUST NOT be altered.

### REQ-UI-178: Direct Workout-to-Cluster Navigation and Hierarchical Backstack Integrity [Verified]
- **Tests**: `TST-UI-130`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `NavRoutes.kt`, `MainActivityWithNavigation.kt`, `ATrainingTrackerApp.kt`, `WorkoutClustersScreen.kt`
- **Rationale**: The system SHALL support direct navigation from workout cards (`WorkoutSummariesTabbedScreen.kt`, `WorkoutSummariesListFragment.kt`) and period map inspection sheets (`PeriodMapScreen.kt`) to the targeted workout cluster detail view (`WorkoutClusterHeatmapScreen`), preserving backstack hierarchy, active filter criteria, and list scroll positions (ATT-1448):
1. *Parameterized Route Resolution (`NavRoutes.kt`)*:
• In `NavRoutes.kt`, the system SHALL define `const val LOCATIONS_PATTERN = "locations?clusterId={clusterId}"` and `const val ARG_CLUSTER_ID = "clusterId"`.
• The system SHALL provide helper `fun locations(clusterId: Long? = null): String`, returning `"locations?$ARG_CLUSTER_ID=$clusterId"` when `clusterId != null && clusterId > 0`, and `LOCATIONS` (`"locations"`) otherwise.
• `NavRoutes.toDrawerItemId(route: String?)` SHALL parse the base route segment via `route?.substringBefore("?")?.substringBefore("/")`, ensuring route query parameters do not disrupt navigation drawer synchronization.
2. *Navigation Graph Argument & Backstack Wiring (`ATrainingTrackerApp.kt`)*:
• In `ATrainingTrackerApp.kt`, the destination for `NavRoutes.LOCATIONS_PATTERN` SHALL register `navArgument(NavRoutes.ARG_CLUSTER_ID) { type = NavType.LongType; defaultValue = -1L }`.
• The composable destination SHALL extract `clusterIdArg = backStackEntry.arguments?.getLong(NavRoutes.ARG_CLUSTER_ID)?.takeIf { it > 0 }` and pass `initialClusterId = clusterIdArg` to `WorkoutClustersScreen`.
• The composable destination SHALL supply `onBackToNav = { clustersViewModel.selectCluster(null); navController.popBackStack() }` to `WorkoutClustersScreen`, ensuring that dismissing or exiting the heatmap pops back to the caller screen and clears cluster selection.
3. *Non-Destructive Navigation Dispatch (`MainActivityWithNavigation.kt`)*:
• In `MainActivityWithNavigation.kt`, the observer for `WorkoutNavigationEvents.navigateToClusterLiveData` SHALL pre-select the cluster on the Activity-scoped `WorkoutClustersViewModel` via `selectClusterById(clusterId)`.
• The observer SHALL navigate directly via `navController?.navigate(NavRoutes.locations(clusterId))` without invoking `navigateToDrawerItem(R.id.drawer_my_locations)`, strictly preserving the navigation backstack and preventing `popUpTo(startDestination)`.
• The observer SHALL NOT invoke `MyPreferenceManager.clearWorkoutFilterCriteria()`, preserving active workout list filters and scroll state.
• `WorkoutNavigationEvents.resetCluster()` SHALL be called immediately after dispatch.
4. *Direct Navigation Heatmap Lifecycle (`WorkoutClustersScreen.kt`)*:
• When `initialClusterId > 0`, `WorkoutClustersScreen` SHALL immediately display `WorkoutClusterHeatmapScreen` for the specified cluster.
• Tapping the top app bar back arrow or pressing the system Back button/gesture in `WorkoutClusterHeatmapScreen` SHALL invoke `onBackToNav`, cleanly popping the backstack to the originating workout screen.
5. *Preserved Drawer & Cluster Invariants*:
• Navigating to "Lieblingsstrecken" via the navigation drawer (`R.id.drawer_my_locations` / `NavRoutes.LOCATIONS`) SHALL continue to open the generic 5-tab cluster list (`WorkoutClustersTabsScreen`) with `clusterId = -1L`.
• Cluster creation, deletion, manual editing, tuning, and member workout listings MUST NOT be altered.
• Workout list scroll position, sort order, and active filter criteria MUST remain intact upon returning from cluster detail view.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: `REQ-SET-058` (*Cluster Visibility & Direct Cluster Navigation in Workout Summary*).
2. *Historical Origin & Commit Trace*: `REQ-SET-058` (ATT-1150 / `WorkoutNavigationEvents`) introduced direct cluster navigation via `triggerCluster(clusterId)` from workout cards and map popups.
3. *Root Reason for Existing Formulation*: During the Compose navigation consolidation in `MainActivityWithNavigation.kt`, `navigateToClusterLiveData` was wired to `navigateToDrawerItem(R.id.drawer_my_locations)` without passing `clusterId` or wiring `onBackToNav`. `navigateToDrawerItem` was designed for top-level drawer transitions, which automatically pops the backstack to the start destination (`popUpTo(startDestination) { saveState = true }`) and wipes workout list filter criteria.
4. *Preservation of Core Invariants*: Top-level drawer navigation to `NavRoutes.LOCATIONS` remains 100% intact; direct navigation pushes `NavRoutes.locations(clusterId)` onto the backstack without popping; `WorkoutClusterHeatmapScreen` displays the targeted cluster; Back returns seamlessly to the originating workout view without losing list filters or scroll state.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an athlete viewing the workout list (`WorkoutSummariesTabbedScreen`) with active filters or specific scroll position,
• *When* the athlete taps the cluster badge on a workout card associated with a cluster (e.g., `clusterId = 42`),
• *Then* the app SHALL navigate directly to `WorkoutClusterHeatmapScreen` displaying cluster 42's heatmap, route trajectory, and aggregate metrics.
• *When* the athlete taps the back arrow in `WorkoutClusterHeatmapScreen` or executes the system Back gesture,
• *Then* the backstack SHALL pop directly to `WorkoutSummariesTabbedScreen`, restoring the exact workout list scroll position and preserving all active filter criteria.
• *Given* an athlete inspecting a workout on the period map (`PeriodMapScreen`),
• *When* the athlete taps the cluster chip in the workout peek bottom sheet,
• *Then* the app SHALL navigate directly to `WorkoutClusterHeatmapScreen` for that cluster, and pressing Back SHALL return directly to `PeriodMapScreen`.
• *Given* an athlete on any screen opening the navigation drawer,
• *When* the athlete taps "Lieblingsstrecken" (`drawer_my_locations`),
• *Then* the app SHALL open `WorkoutClustersTabsScreen` with the 5-tab cluster overview (`clusterId = -1L`).

**Invariants**: Navigation drawer tab selection behavior, workout list filters, cluster database schema, and cluster heatmap rendering MUST NOT be altered.

### REQ-UI-179: Customizable Geofence Radius with Live Map Preview in Lieblingsorte Edit Dialog [Verified]
- **Tests**: `TST-UI-131`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `EditKnownLocationDialog.kt`, `KnownLocationsScreen.kt`, `KnownLocationsUnitConversions.kt`, `KnownLocationsViewModel.kt`, `KnownLocationsRepository.kt`, `KnownLocationsDatabaseManager.java`, `strings.xml` (all 9 locales)
- **Rationale**: The system SHALL enable athletes to customize the geofence detection radius for known start locations (*Lieblingsorte*) directly within the editing interface (`EditKnownLocationDialog`), with dynamic real-time map preview feedback and complete persistence integration (ATT-1473):
1. *Discrete Slider Control (`EditKnownLocationDialog.kt`)*:
• The dialog SHALL present a dedicated horizontal `Slider` positioned directly below the reference altitude input field.
• *Range & Discrete Granularity*: The slider SHALL span from $50\text{m}$ to $1,000\text{m}$ (`valueRange = 50f..1000f`) with 25-meter discrete steps (`steps = 37`), evaluated via `(round(it / 25f) * 25f).coerceIn(50f, 1000f)`, preventing floating-point artifacts.
• *Formatted Visual Readout*: Directly above the slider, the dialog SHALL display a localized label showing the active radius: `%1$s: %2$s` (`@string/known_location_radius_format`), where parameter 1 is "Radius" / "Erfassungsradius" (`@string/known_location_radius_label`) and parameter 2 is formatted via `KnownLocationsUnitConversions.formatRadius(radiusMeters, isMetric)` (Metric: `"{radius} m"`, Imperial: `"{radius} m ({feet} ft)"`).
2. *Real-Time Dynamic Mini-Map Preview (`LocationMiniMap`)*:
• The circular geofence overlay rendered on the embedded `LocationMiniMap` SHALL reactively bind to `radius = radiusMeters.toDouble()`.
• As the athlete drags the slider, the circular overlay SHALL expand and contract smoothly in real-time, providing immediate visual spatial feedback.
3. *Persistence & Architecture Serialization*:
• Confirming the dialog (`onConfirm`) SHALL pass `radiusMeters.roundToInt()`.
• `KnownLocationsViewModel.updateLocation` and `KnownLocationsRepository.updateLocation` SHALL propagate `radius: Int` to `KnownLocationsDatabaseManager.updateLocation(id, name, altitude, radius, source, isLocked)`, writing to the existing `KnownLocationsDbHelper.RADIUS` column in SQLite.
• All repository write operations SHALL remain serialized on `dbDispatcher` (`KnownLocationsDB-Thread`), ensuring zero deadlocks and immediate emission through `locationsFlow`.
• Backward compatibility: Existing 4-argument overloads for `updateLocation` without radius SHALL be preserved for legacy/background callers.
4. *100% Localization Parity Across 9 Locales*:
• All newly introduced string resources (`known_location_radius_label`, `known_location_radius_format`) SHALL be defined across all 9 supported application locales (EN, DE, ES, FR, IT, JA, NL, PL, PT) with 0 missing translations.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: `REQ-UI-165` (*Known Start Locations Management*) and `REQ-UI-166` (*Lieblingsorte UI/UX Harmonization*).
2. *Historical Origin & Commit Trace*: Introduced in `ATT-919` / `ATT-1382` where `KnownLocations.RADIUS` was created with a default value of 200m.
3. *Root Reason for Existing Formulation*: While the database column and map rendering natively supported radius, the edit dialog previously omitted a slider control and the update method dropped radius upon saving.
4. *Preservation of Core Invariants*: Automatic write-protection on manual altitude edit (`is_locked = 1`, `source = MANUAL_USER`), single-thread SQLite serialization (`KnownLocationsDB-Thread`), and 9-language parity remain strictly intact.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an athlete opening `EditKnownLocationDialog` for any known location,
• *When* the dialog renders,
• *Then* a radius slider initialized to the location's current radius (or clamped within 50m–1000m) SHALL be displayed below the altitude field, accompanied by a localized readout label.
• *Given* the edit dialog displayed with embedded map preview (`showMap = true`),
• *When* the athlete drags the radius slider,
• *Then* the circular geofence overlay on the map SHALL resize dynamically in real time without lag.
• *Given* the athlete selects a new radius and taps "Speichern",
• *When* the save executes,
• *Then* the updated radius SHALL be persisted to SQLite and reflected reactively in `locationsFlow` and the list/map perspectives.
• *Given* an app running in Imperial units,
• *When* viewing the radius readout,
• *Then* the readout SHALL display both meters and converted feet (e.g. `200 m (656 ft)`).

**Invariants**: SQLite schema V5 integrity, single-thread SQLite concurrency (`KnownLocationsDB-Thread`), automatic altitude lock on manual user edits (`is_locked = 1`, `source = MANUAL_USER`), repeat start hitCount increments (`REQ-DAT-007`), and 9-language localization parity MUST NOT be broken.

### REQ-UI-180: Display Favorite Locations (Lieblingsorte) on Central Navigation Map and Streamline Management View [Verified]
- **Tests**: `TST-UI-132`, `TST-UI-070`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `MapContentScope.kt`, `MapScreenWithTrack.kt`, `MapFragmentWithTrackViewModel.kt`, `KnownLocationsScreen.kt`, `KnownLocationsViewModel.kt`, `NavRoutes.kt`, `ATrainingTrackerApp.kt`
- **Rationale**: The system SHALL display an athlete's favorite start locations (*Lieblingsorte* / known locations) directly on the central navigation map (`NavRoutes.MAP` / `MapScreenWithTrack.kt`) alongside routes, segments, and live tracks, and streamline the dedicated favorite locations management screen (`KnownLocationsScreen.kt`) into a clean single-perspective list (ATT-1449):
1. *Central Map Presentation & Rendering Layer (`MapContentScope.kt`, `MapScreenWithTrack.kt`, `MapFragmentWithTrackViewModel.kt`)*:
• *DSL Extension*: `MapContentScope` SHALL provide `knownLocations(locations: List<KnownLocationItem>, onLocationClick: (Long) -> Unit)`.
• *Marker & Overlay Rendering*: Favorite locations SHALL render with custom theme-colored heart-pin markers (`createHeartPinMarker(context, primaryColor, Color.White)`) and semi-transparent circular geofence overlays with radius matching `location.radius.toDouble()`.
• *Reactive Data Flow*: `MapFragmentWithTrackViewModel` SHALL collect `KnownLocationsRepository.getInstance(application).locationsFlow` and expose `knownLocations: List<KnownLocationItem>` in `MapFragmentUIState`.
2. *Interactive Central Map Bottom Peek Sheet*:
• In `MapScreenWithTrack.kt`, tapping a favorite location pin SHALL set `selectedLocationId` and expand `BottomSheetScaffold` to peek height ($140\text{dp} + \text{navBarHeight}$).
• The peek card SHALL display the location's name, reference altitude, elevation source badge, starts count badge, and an edit button.
• Tapping the edit button SHALL launch `EditKnownLocationDialog` to edit the location's name, altitude, and radius, delegating updates to `viewModel.updateKnownLocation`.
• Tapping the map canvas or pressing the system Back button SHALL collapse the sheet and clear `selectedLocationId`.
3. *Streamlined Management View (`KnownLocationsScreen.kt`, `KnownLocationsViewModel.kt`)*:
• The system SHALL eliminate the redundant "Karte" tab, `PrimaryTabRow`, and `HorizontalPager` from `KnownLocationsScreen.kt`, aligning it with `RoutesScreen` and `StarredSegmentsScreen` as a high-performance single-perspective list.
• Unused GoogleMap composables, map viewport bounds culling, and camera state managers SHALL be removed from `KnownLocationsScreen.kt` and `KnownLocationsViewModel.kt`.
• In `KnownLocationsScreen`, viewing locations on the central navigation map is accessed via the map navigation drawer destination or central map view; list item cards strictly adhere to the universal delete-only long-press contract (`REQ-UI-061`).
4. *Targeted Map Navigation (`NavRoutes.kt`, `ATrainingTrackerApp.kt`)*:
• In `NavRoutes.kt`, the system SHALL define `const val MAP_PATTERN = "map?locationId={locationId}"`, `const val ARG_LOCATION_ID = "locationId"`, and `fun map(locationId: Long? = null): String`.
• In `ATrainingTrackerApp.kt`, `NavRoutes.MAP_PATTERN` SHALL extract `locationId` and pass `targetLocationId` to `MapScreenWithTrack`.
• When `targetLocationId` is supplied, `MapScreenWithTrack` SHALL animate the camera to the target location coordinates and expand its bottom peek card.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: `REQ-UI-165` (*Known Start Locations Management*) and `REQ-UI-166` (*Lieblingsorte UI/UX Harmonization*).
2. *Historical Origin & Commit Trace*: Introduced in `ATT-919` / `ATT-1382` where `KnownLocationsScreen` was given a secondary tabbed GoogleMap for spatial inspection.
3. *Root Reason for Existing Formulation*: At that time, `ATrainingTrackerMap` and `MapContentScope` were not yet consolidated into a multi-layer engine capable of rendering known locations alongside routes and segments.
4. *Preservation of Core Invariants*: Full spatial inspection capability is preserved and upgraded by moving to the central navigation map; memory consumption is reduced; thread-safe persistence on `KnownLocationsDB-Thread` is preserved; existing routes, segments, tracks, and 9-language translations remain intact.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an athlete navigating to the central map (`NavRoutes.MAP`),
• *When* the map renders,
• *Then* all favorite start locations SHALL be visible with heart-pin markers and geofence circles alongside routes and segments.
• *When* the athlete taps a favorite location pin,
• *Then* a bottom peek card SHALL appear displaying name, altitude, start count, and an edit button.
• *Given* an athlete navigating to "Lieblingsorte" (`NavRoutes.START_LOCATIONS`),
• *When* the screen renders,
• *Then* it SHALL display a streamlined list without tabs or redundant embedded map.
• *When* navigating to the central map,
• *Then* all favorite start locations SHALL be visible with heart-pin markers and geofence circles alongside routes and segments.
• *When* tapping a favorite location pin,
• *Then* a bottom peek card SHALL appear displaying name, altitude, start count, and an edit button.

**Invariants**: Routes, segments, live track rendering, zoom focus behaviors, `KnownLocationsDB-Thread` serialization, universal delete-only long-press contract (`REQ-UI-061`), and 9-language localization parity MUST NOT be broken.

### REQ-UI-181: Ultra-Large Cockpit Typography Extensions (XXHuge and XXXHuge) [Verified]
- **Tests**: `TST-UI-133`, `TST-UI-166`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `SensorFieldView.kt`, `TrackingViewsRepository.kt`, `EditSensorFieldViewModel.kt`, `strings.xml` (all 9 locales)
- **Rationale**: The system SHALL extend the cockpit sensor field sizing scale with two ultra-large typography variants (`XXHUGE` and `XXXHUGE`) to provide athletes with glanceable, maximum-contrast numeric readouts during high-vibration sports (e.g. road cycling, downhill mountain biking) or on dedicated 1–2 metric cockpit focus tabs (ATT-1431):
1. *Enum Extension & Display Mapping (`SensorFieldView.kt`)*:
• `enum class ViewSize` SHALL define two additional size steps: `XXHUGE` and `XXXHUGE`, establishing the complete progression: `XSMALL`, `SMALL`, `NORMAL`, `LARGE`, `XLARGE`, `HUGE`, `XHUGE`, `XXHUGE`, `XXXHUGE`.
• `ViewSize.getDisplayName(context: Context)` SHALL map `XXHUGE` to `@string/view_size_xxhuge` and `XXXHUGE` to `@string/view_size_xxxhuge`.
2. *Proportional Typography Scaling (`SensorFieldView.kt`)*:
• *Primary Metric Values (`getSensorValueTextStyle`)*: `XXHUGE` SHALL resolve to `typography.displayLarge.copy(fontSize = 140.sp, fontWeight = FontWeight.SemiBold)`. `XXXHUGE` SHALL resolve to `typography.displayLarge.copy(fontSize = 180.sp, fontWeight = FontWeight.SemiBold)`. Semibold weight SHALL strictly be enforced across all 9 sizes.
• *Unit Annotations (`getSensorUnitTextStyle`)*: `XXHUGE` SHALL resolve to `typography.headlineLarge.copy(fontSize = 56.sp)` and `XXXHUGE` SHALL resolve to `typography.headlineLarge.copy(fontSize = 64.sp)`. Unit styles SHALL NOT be bold.
• *Labels & Filter Descriptions*: In `SensorFieldView`, `labelStyle` SHALL evaluate to `MaterialTheme.typography.headlineMedium.copy(fontSize = 36.sp)` for `XXHUGE` and `MaterialTheme.typography.headlineLarge.copy(fontSize = 40.sp)` for `XXXHUGE`. `filterStyle` SHALL evaluate to `MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp)` for `XXHUGE` and `MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp)` for `XXXHUGE`.
3. *Configuration UI & Selection (`EditSensorFieldViewModel.kt`, `EditSensorFieldDialog.kt`)*:
• In `EditSensorFieldViewModel.kt`, `availableViewSizes` SHALL expose all `ViewSize` entries dynamically.
• `EditSensorFieldDialog.kt` SHALL populate the "Text Size" dropdown spinner with all 9 localized display names.
4. *Database Persistence & Fallback Resilience (`TrackingViewsRepository.kt`)*:
• SQLite storage in `TrackingViewsDatabaseManager.ROWS_TABLE` SHALL persist `ViewSize.name()` as `TEXT` without requiring schema migration.
• Deserialization in `TrackingViewsRepository.kt` SHALL parse `ViewSize.valueOf(sizeString)` with fallback to `ViewSize.NORMAL` for corrupted or legacy records.
5. *100% Localization Parity Across 9 Locales*:
• String resources `view_size_xxhuge` and `view_size_xxxhuge` SHALL be provided across all 9 supported application locales (EN, DE, ES, FR, IT, JA, NL, PL, PT) with 0 missing translations.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: `REQ-UI-171` (*High-Contrast Cockpit Typography and Subtle Tile Grid*) and `REQ-UI-103` (*Customizable Cockpits*).
2. *Historical Origin & Commit Trace*: Introduced in `ATT-1264` where font sizes were capped at `XHUGE` (100.sp).
3. *Root Reason for Existing Formulation*: Cockpit tabs historically featured 4–8 fields per tab; 100.sp was the maximum size that fit within 2x2 and 3x2 grids without overlapping headers. With athletes increasingly configuring single-field or double-field focus tabs for cycling/MTB, larger sizes are needed.
4. *Preservation of Core Invariants*: All 7 existing sizes retain immutable font sizes; `FontWeight.SemiBold` remains mandatory for all primary values; unit styles remain non-bold; SQLite persistence schema is unaltered; 9-language localization parity maintained.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an athlete configuring a cockpit sensor field in `EditSensorFieldDialog`,
• *When* expanding the "Text Size" dropdown spinner,
• *Then* "XXHuge" and "XXXHuge" (or localized equivalents) SHALL be visible and selectable options.
• *Given* a sensor field configured with `ViewSize.XXHUGE`,
• *When* the field renders on `SensorGridScreen`,
• *Then* the primary metric value SHALL render with `fontSize = 140.sp` and `FontWeight.SemiBold`, and the unit annotation SHALL render with `fontSize = 56.sp`.
• *Given* a sensor field configured with `ViewSize.XXXHUGE`,
• *When* the field renders on `SensorGridScreen`,
• *Then* the primary metric value SHALL render with `fontSize = 180.sp` and `FontWeight.SemiBold`, and the unit annotation SHALL render with `fontSize = 64.sp`.
• *Given* an existing database containing sensor fields with legacy sizes (`XSMALL` through `XHUGE`),
• *When* loading tracking views,
• *Then* existing sizes SHALL render with identical font sizes and weights without regression.

**Invariants**: Visual hierarchy, `FontWeight.SemiBold` value enforcement, non-bold unit styling, SQLite schema stability, and 9-language localization parity MUST NOT be broken.

### REQ-UI-182: Unified Edit Action Symbols & Design Token Tinting [Verified]
- **Tests**: `TST-UI-134`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `WorkoutHeader.kt`, `WorkoutClusterHeatmapScreen.kt`, `TrackingTabPreviewHeader.kt`, `MapScreenWithTrack.kt`, `EditKnownLocationDialog.kt`, `ActivityTypeSelectionDialog.kt`, `device_list_context_menu.xml`, `AppNavigationDrawer.kt`
- **Rationale**: The system SHALL standardize all general edit action buttons, dialog headers, and menus across the entire application to use a single canonical symbol and theme-aware design token color tinting:
1. *Unified Symbol Standard*:
• All Jetpack Compose interactive edit action buttons and dialog headers SHALL use the canonical Material 3 diagonal pencil symbol (`Icons.Default.Edit`).
• XML menu resources and navigation drawer configs SHALL reference a dedicated vector asset (`@drawable/ic_edit`) matching the Material 3 pencil symbol, eliminating `@android:drawable/ic_menu_edit` and `ic_table_edit` from edit operations.
• `WorkoutClusterHeatmapScreen.kt` SHALL retain `Icons.Default.EditLocationAlt` strictly for specialized geographic coordinate boundary adjustment, while general cluster identity renaming SHALL use `Icons.Default.Edit`.
2. *100% Color & Design Token Tinting Uniformity*:
• All interactive edit action buttons across the application (including `WorkoutHeader.kt`, `WorkoutClusterHeatmapScreen.kt`, `TrackingTabPreviewHeader.kt`, `MapScreenWithTrack.kt`) SHALL be tinted with `MaterialTheme.colorScheme.primary`. Hardcoded colors or non-primary tints (`secondary`, `onSurfaceVariant`) SHALL NOT be used for interactive edit action buttons.
• Modal bottom sheet dialog headers (`EditKnownLocationDialog.kt`, `ActivityTypeSelectionDialog.kt`, `EditWorkoutScreen.kt`, `EditRouteScreen.kt`, `EditSensorFieldDialog.kt`) SHALL standardize on `icon = Icons.Default.Edit` with default `iconTint = MaterialTheme.colorScheme.primary`.
3. *Accessibility & Geometry Preservation*:
• All interactive edit buttons SHALL maintain a minimum touch target bounding box of $48\times 48\text{dp}$ with a $24\times 24\text{dp}$ or $32\times 32\text{dp}$ visual footprint.
• All call sites SHALL preserve localized accessibility content descriptions (`@string/edit_workout`, `@string/Edit`, `@string/edit_workout_name`, `@string/known_location_edit`).
4. *Legacy Asset Removal & Build Enforcement*:
• `R.drawable.ic_table_edit` (`ic_table_edit.xml`) SHALL be permanently deleted from `app/src/main/res/drawable/` to remove the deprecated asset from the bundle. `AppNavigationDrawer.kt` SHALL transition to `R.drawable.ic_edit`. Automated verification tests failing on detection of `ic_table_edit` or `@android:drawable/ic_menu_edit` SHALL enforce build hygiene.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: `REQ-SET-070` / `REQ-SET-071` (`WorkoutHeader`), `REQ-UI-149` (`ActivityTypeSelectionDialog`), `REQ-UI-151` (`EditWorkoutDialog`).
2. *Historical Origin & Commit Trace*: `ATT-506` (`16ac39f3`), `ATT-850` (`2bf79cdf`), `ATT-900` (`be9d84d7`), `ATT-1043` (`4452a85e`).
3. *Root Reason for Existing Formulation*: `R.drawable.ic_table_edit` was used as a convenient drawable placeholder before an app-wide Compose Material 3 vector icon standard was established.
4. *Preservation of Core Invariants*: Upgrading to `Icons.Default.Edit` with `MaterialTheme.colorScheme.primary` tint preserves button positioning, touch targets, click handlers, and dialog contracts without regression.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an athlete viewing a workout summary in list or map peek views,
• *When* the header action row is composed,
• *Then* the edit button SHALL render `Icons.Default.Edit` tinted with `MaterialTheme.colorScheme.primary`.
• *Given* an athlete viewing the cluster heatmap screen or favorite location peek sheet on the map,
• *When* edit action buttons are rendered,
• *Then* the general edit buttons SHALL render `Icons.Default.Edit` tinted with `MaterialTheme.colorScheme.primary`.
• *Given* an athlete opening `EditKnownLocationDialog` or `ActivityTypeSelectionDialog`,
• *When* the modal bottom sheet appears,
• *Then* the header leading icon SHALL display `Icons.Default.Edit` tinted with `MaterialTheme.colorScheme.primary`.
• *Given* an athlete previewing tracking tabs in `TrackingTabPreviewHeader`,
• *When* the edit button is rendered,
• *Then* it SHALL display `Icons.Default.Edit` tinted with `MaterialTheme.colorScheme.primary`.
• *Given* the device list context menu or navigation drawer,
• *When* the menu items are inflated/rendered,
• *Then* the edit action SHALL reference `@drawable/ic_edit`.
• *Given* the project source repository,
• *Then* `app/src/main/res/drawable/ic_table_edit.xml` SHALL NOT exist.

**Invariants**: Business logic, ViewModel states, click handlers, database persistence, and dialog dismiss mechanics MUST NOT be altered.

### REQ-UI-183: Lieblingsorte: Live Cockpit Location & Barometer Calibration Feedback [Verified]
- **Tests**: `TST-UI-136`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `ControlTrackingScreen.kt`, `TrackingTabsScreen.kt`, `TrackingTabsViewModel.kt`, `LocationCalibrationBadge.kt`, `strings.xml` (all 9 locales)
- **Rationale**: The system SHALL provide visible, reactive feedback on the control tracking setup screen confirming when the athlete is located within the geofence of a known start location (Lieblingsort) and that the barometric altimeter is calibrated to reference altitude (ATT-1399):
1. *Decoupled Architecture & Contextual Placement*:
• The feedback component SHALL be strictly decoupled from the background tracking and barometric calibration engine (`AltitudeFromPressureDevice`, `TrackerService`).
• The feedback badge is placed directly at the top of `ControlTrackingScreen.kt` (the setup/ready screen) and excluded from the persistent cockpit header (`TrackingTabsScreen.kt`), ensuring secondary data grid, map, and elevation tabs remain uncluttered.
2. *Reactive State Pipeline (`TrackingTabsViewModel.kt`)*:
• `TrackingTabsViewModel` SHALL combine `banalServiceRepository.currentLocation` (`StateFlow<LatLng?>`) and `knownLocationsRepository.locationsFlow` (`StateFlow<List<KnownLocationItem>>`).
• When `currentLocation` is within the geofence radius ($r$) of an existing known location ($d < r$), the system SHALL emit `LocationCalibrationStatus(locationId, locationName, referenceAltitude, isCalibrated, source)`.
• When outside any known location geofence, `locationCalibrationStatus` SHALL emit `null`.
3. *Cockpit Status Presentation & Smooth Transitions (`LocationCalibrationBadge.kt`, `ControlTrackingScreen.kt`)*:
• In `ControlTrackingScreen.kt`, at the top of the main column, the system SHALL render `LocationCalibrationBadge(status = locationCalibrationStatus)`.
• The badge SHALL feature `AnimatedVisibility` (fade and expand/shrink) to transition smoothly as GPS fix arrives or location changes.
• The badge SHALL render a theme-aware surface chip containing a location pin icon and formatted text: `"%1$s (%2$d m %3$s)"` / `"%1$s (%2$d m kalibriert)"` (`@string/location_calibrated_format`), dynamically reflecting unit preferences (Metric meters vs. Imperial feet).
4. *100% Localization Parity Across 9 Locales*:
• String resources `location_calibrated_format` and `calibrated` SHALL be defined across all 9 supported application locales (EN, DE, ES, FR, IT, JA, NL, PL, PT).

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement only (REQ-UI-183). No existing requirements modified.
2. *Historical Origin & Commit Trace*: Ticket `ATT-1399` under Epic `ATT-1396` (*Lieblingsorte: Visible Value, Auto-Naming & Tracking Integration*).
3. *Root Reason for Existing Formulation*: Baseline altimeter calibration operates silently in the background in `AltitudeFromPressureDevice.java` without cockpit visibility.
4. *Preservation of Core Invariants*: Background calibration calculations (`REQ-CON-011`, `REQ-CON-013`), SQLite schema V5 (`REQ-DAT-014`), start count recording (`REQ-DAT-015`), and single-thread SQLite confinement (`KnownLocationsDB-Thread`) remain 100% strictly intact.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an athlete on the Control Tracking screen with GPS coordinates within 200m of known location "Zuhause" (reference altitude 520m),
• *When* viewing `ControlTrackingScreen`,
• *Then* `LocationCalibrationBadge` SHALL be visible displaying 📍 "Zuhause (520 m kalibriert)".
• *Given* the athlete moving away from the known location beyond its geofence radius,
• *When* `currentLocation` updates,
• *Then* `locationCalibrationStatus` SHALL emit `null` and `LocationCalibrationBadge` SHALL animate out smoothly.
• *Given* the application configured for Imperial units,
• *When* viewing the badge for a location at 520.0 meters,
• *Then* the altitude readout SHALL display in feet.

**Invariants**: Background calibration calculations, SQLite schema V5, start count recording, and single-thread SQLite confinement MUST NOT be altered.

### REQ-UI-184: Lieblingsorte: Display Start & Destination Locations in Workout Details and Map [Verified]
- **Tests**: `TST-UI-137`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `WorkoutHeader.kt`, `WorkoutHeaderData.kt`, `WorkoutData.kt`, `WorkoutDataMapper.kt`, `WorkoutRepository.kt`, `PathPreviewMap.kt`, `strings.xml` (all 9 locales)
- **Rationale**: The system SHALL resolve, encapsulate, and visually present recognized favorite start locations (*Lieblingsorte*) for workout start and destination endpoints within workout summary headers and route map markers (ATT-1400):
1. *Domain & Presentation Metadata Extension (`WorkoutHeaderData.kt`, `WorkoutData.kt`)*:
• `WorkoutData` and `WorkoutHeaderData` SHALL provide nullable properties: `startLocationName: String? = null` and `endLocationName: String? = null`.
• `WorkoutData.headerData` SHALL map `startLocationName` and `endLocationName` from `WorkoutData` directly into `WorkoutHeaderData`.
2. *Dynamic Endpoint Geofence Resolution (`WorkoutDataMapper.kt`)*:
• In `WorkoutDataMapper.kt`, the system SHALL resolve `startLocationName` and `endLocationName` from `startLatLng` and `endLatLng` using `KnownLocationsDatabaseManager.getMyLocation(latLng)` and `WorkoutAutoNamingHelper.getDisplayName(context, location)`.
• The resolution SHALL be retroactive across all existing and past workouts without requiring SQLite database schema migrations.
• If an endpoint coordinate is outside all known geofences, or if coordinates are null, the corresponding location name SHALL evaluate to `null`.
3. *Workout Header Presentation (`WorkoutHeader.kt`)*:
• In `WorkoutHeader.kt`, when either `startLocationName` or `endLocationName` is non-null, the header SHALL render a dedicated location indicator row below the date/time row.
• *Loop / Round-Trip Case*: When `startLocationName != null && startLocationName == endLocationName`, the header SHALL display: `📍 Start & Ziel: {startLocationName}` (`@string/workout_start_and_destination`).
• *Point-to-Point Case*: When both are present and distinct, the header SHALL display: `📍 Start: {startLocationName}   🏁 Ziel: {endLocationName}` (`@string/workout_start_location`, `@string/workout_destination_location`).
• *Single Endpoint Case*: When only one endpoint is recognized, the header SHALL display the recognized endpoint (`📍 Start: {startLocationName}` or `🏁 Ziel: {endLocationName}`).
• Text truncation: Each location name SHALL enforce `maxLines = 1` and `overflow = TextOverflow.Ellipsis`.
4. *Route Map Pin Annotations (`WorkoutRepository.kt`, `PathPreviewMap.kt`)*:
• In `WorkoutRepository.getWorkoutMarkers()`, start and finish `LocationMarker` titles SHALL incorporate the recognized favorite location name when present (e.g. `"${application.getString(R.string.Start)}: $startLocationName"`).
• In `PathPreviewMap.kt`, start and end markers SHALL receive and render `startTitle` and `endTitle` in their marker states.
5. *100% Localization Parity Across 9 Locales*:
• String resources `workout_start_location`, `workout_destination_location`, and `workout_start_and_destination` SHALL be defined across all 9 supported application locales (EN, DE, ES, FR, IT, JA, NL, PL, PT) with 0 missing translations.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement only (REQ-UI-184). No existing requirements modified.
2. *Historical Origin & Commit Trace*: Ticket `ATT-1400` under Epic `ATT-1396` (*Lieblingsorte: Visible Value, Auto-Naming & Tracking Integration*).
3. *Root Reason for Existing Formulation*: Historical workout headers and map pins only displayed time/date and generic "Start"/"Stop" markers because favorite locations were originally designed solely for altitude calibration.
4. *Preservation of Core Invariants*: SQLite schemas (`WorkoutSummaries.db`, `StartLocation2Altitude.db`) remain 100% unchanged; `WorkoutHeader` click and long-press handlers are preserved; background tracking services remain untouched; 9-language localization parity maintained.

**Acceptance Criteria (Given-When-Then)**:
• *Given* a workout recorded starting at known location "Zuhause" and ending at "Büro",
• *When* the athlete views the workout summary card or header,
• *Then* the header SHALL display "Start: Zuhause" and "Ziel: Büro".
• *Given* a workout recorded starting and ending at "Zuhause",
• *When* viewing the workout header,
• *Then* the header SHALL display "Start & Ziel: Zuhause".
• *Given* the athlete opening `TrackOnMapScreen` or inspecting map preview pins,
• *When* inspecting the start marker,
• *Then* the marker title SHALL display "Start: Zuhause".
• *Given* a workout starting in an unrecognized location,
• *When* viewing the header and map,
• *Then* no empty location chips SHALL be rendered and map markers SHALL fall back cleanly to default "Start" and "Stop".

**Invariants**: SQLite schema stability, WorkoutHeader click/long-press behavior, map gesture interactivity, and 9-language localization parity MUST NOT be broken.

### REQ-UI-185: Lieblingsorte: Drill-Down Filter to View Workouts by Starting Location [Verified]
- **Tests**: `TST-UI-138`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
• The chip label SHALL format as `"📍 ${criteria.startLocationName ?: stringResource(R.string.filter_start_location)}"`.
• Dismissing the chip SHALL invoke `onRemoveStartLocation: () -> Unit`, clearing the spatial filter dimensions while preserving all other active criteria.
3. *Lieblingsorte Card Drill-Down Affordance (`KnownLocationsScreen.kt`)*:
• `KnownLocationsScreen` and `KnownLocationCard` SHALL expose navigation callback `onShowWorkouts: (KnownLocationItem) -> Unit`.
• In `KnownLocationCard`, the starts count indicator SHALL be rendered as an interactive, accessible touch target with minimum touch bounding box of $48\times 48\text{dp}$. Tapping the starts badge SHALL trigger `onShowWorkouts(item)`.
• Invariant: Single-tap on the card body (`onClick = onEdit`) SHALL continue to launch `EditKnownLocationDialog`, and long-press (`onLongClick`) SHALL strictly preserve the universal delete-only context menu (`REQ-UI-061`).
4. *App Navigation Wiring (`ATrainingTrackerApp.kt`)*:
• In `ATrainingTrackerApp.kt`, within `NavRoutes.START_LOCATIONS`, `onShowWorkouts` SHALL construct `WorkoutFilterCriteria` with the location's name, coordinates, and radius, update `WorkoutSummariesViewModel.setFilterCriteria(criteria)`, and navigate to `NavRoutes.WORKOUTS`.
5. *100% Localization Parity Across 9 Locales*:
• String resources `filter_start_location` and `known_locations_view_workouts` SHALL be defined across all 9 supported application locales (EN, DE, ES, FR, IT, JA, NL, PL, PT) with 0 missing translations.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement only (REQ-UI-185). No existing requirements modified.
2. *Historical Origin & Commit Trace*: Ticket `ATT-1401` under Epic `ATT-1396` (*Lieblingsorte: Visible Value, Auto-Naming & Tracking Integration*).
3. *Root Reason for Existing Formulation*: Historical favorite location cards displayed hit counts without navigational drill-down, and `WorkoutFilterCriteria` lacked a spatial coordinate predicate.
4. *Preservation of Core Invariants*: SQLite schemas remain 100% unchanged; card editing (`onEdit`) and universal deletion (`REQ-UI-061`) contracts remain intact; existing filter criteria dimensions and serialization remain backward-compatible; 9-language localization parity maintained.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an athlete on `KnownLocationsScreen` viewing location "Zuhause",
• *When* tapping the starts badge on the card,
• *Then* the app SHALL navigate to `NavRoutes.WORKOUTS` displaying only workouts that started within the geofence radius of "Zuhause".
• *Given* workouts starting within and outside the "Zuhause" geofence,
• *When* the "Zuhause" filter is active,
• *Then* only workouts starting within the geofence radius SHALL be visible.
• *Given* the workout list filtered by starting location "Zuhause",
• *When* viewing the top of the workout list,
• *Then* an active chip "📍 Zuhause" SHALL be displayed in `ActiveFilterChipsRow`, and tapping its remove icon SHALL clear the start location filter.
• *Given* an athlete on `KnownLocationsScreen`,
• *When* tapping the card body outside the starts badge, `EditKnownLocationDialog` SHALL launch; when long-pressing, the delete-only context menu SHALL appear.

**Invariants**: SQLite schema stability, card edit/delete interactions, existing filter criteria dimensions, and 9-language localization parity MUST NOT be broken.
- **Rationale**: The system SHALL provide spatial geofence filtering in `WorkoutFilterCriteria` and an interactive drill-down navigation flow from `KnownLocationsScreen` to the filtered workout list (ATT-1401):
1. *Spatial Filter Criteria Extension (`WorkoutFilterCriteria.kt`)*:
• `WorkoutFilterCriteria` SHALL define nullable properties: `startLocationName: String? = null`, `startLocationLat: Double? = null`, `startLocationLng: Double? = null`, and `startLocationRadiusM: Double? = null`.
• *Predicate Evaluation (`matches`)*: When `startLocationLat != null && startLocationLng != null`, the system SHALL evaluate the workout's start coordinate (`workout.startLatLng`). If `workout.startLatLng == null` or its geodetic distance to `(startLocationLat, startLocationLng)` exceeds `startLocationRadiusM ?: 200.0`, `matches` SHALL return `false`.
• Geodetic distance SHALL be computed via `WorkoutClusterEngine.distanceBetween(p1, p2)`, ensuring robust execution across Android and JVM test runtimes.
• `activeFilterCount` SHALL increment by 1 when the spatial start location filter is active.
• `toJson()` and `fromJson()` SHALL serialize and deserialize the spatial fields losslessly for DataStore preference persistence.
2. *Active Filter Chip Presentation (`ActiveFilterChipsRow.kt`)*:
• `ActiveFilterChipsRow` SHALL display an active filter chip when start location filtering is active (`criteria.startLocationName != null

### REQ-UI-186: Lieblingsorte & Lieblingsstrecken Bridge: Starting Location Association, Invariant & UI Integration [Verified]
- **Tests**: `TST-UI-139`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `WorkoutClusterEngine.kt`, `ClusterFilterCriteria.kt`, `KnownLocationsViewModel.kt`, `KnownLocationsScreen.kt`, `WorkoutClustersScreen.kt`, `strings.xml` (all 9 locales)
- **Rationale**: The system SHALL establish an architectural, algorithmic, and visual bridge between Known Start Locations (Lieblingsorte) and Route Clusters (Lieblingsstrecken) (ATT-1402):
1. *Mandatory Starting Location Invariant & Automated Seeding (`WorkoutClusterEngine.kt`)*:
• Every `WorkoutCluster` (Lieblingsstrecke) SHALL originate at a recognized `KnownLocation` (Lieblingsort).
• In `WorkoutClusterEngine.learnFromWorkout`, when learning or creating a new cluster, the system SHALL check if a `KnownLocation` exists within the geofence radius ($d \le 200\text{m}$) of `start` via `KnownLocationsDatabaseManager.getInstance(appContext).getMyLocation(start)`.
• If no location exists within 200m, the system SHALL automatically discover and persist a new `KnownLocation` at `start` with a reverse-geocoded name, calibrated altitude, default geofence radius (200m), and provenance `ElevationSource.AUTO_LEARNED` via `KnownLocationsDatabaseManager.getInstance(appContext).recordWorkoutStart(start, altitude)`.
2. *Cluster Spatial Filter Criteria Extension (`ClusterFilterCriteria.kt`)*:
• `ClusterFilterCriteria` SHALL define nullable properties: `startLocationName: String? = null`, `startLocationLat: Double? = null`, `startLocationLng: Double? = null`, and `startLocationRadiusM: Double? = null`.
• *Predicate Evaluation (`matches`)*: When `startLocationLat != null && startLocationLng != null`, the system SHALL evaluate the cluster's start coordinate `(cluster.startLat, cluster.startLng)`. If geodetic distance between `(cluster.startLat, cluster.startLng)` and `(startLocationLat, startLocationLng)` exceeds `startLocationRadiusM ?: 200.0`, `matches` SHALL return `false`. Geodetic distance SHALL be computed via `WorkoutClusterEngine.distanceBetween(p1, p2)`.
• `activeFilterCount` SHALL increment by 1 when `startLocationLat != null && startLocationLng != null`.
• `toJson()` and `fromJson()` SHALL serialize and deserialize `startLocationName`, `startLocationLat`, `startLocationLng`, and `startLocationRadiusM` losslessly for DataStore preference persistence.
3. *Lieblingsorte Linked Routes Integration (`KnownLocationsViewModel.kt`, `KnownLocationsScreen.kt`)*:
• `KnownLocationsUiState` SHALL provide `clustersByLocationId: Map<Long, List<WorkoutCluster>> = emptyMap()`.
• `KnownLocationsViewModel` SHALL observe `WorkoutClusterRepository.allClusters` and group route clusters whose starting coordinates fall within each location's geofence radius ($d \le \text{radius}$).
• In `KnownLocationCard`, when a location has linked route clusters, it SHALL render a dedicated route cluster section with interactive chips for each cluster.
• Tapping a route cluster chip SHALL invoke `onSelectCluster: (Long) -> Unit`, navigating directly to that cluster in `WorkoutClustersScreen`.
4. *Lieblingsstrecken Screen Starting Location Badge (`WorkoutClustersScreen.kt`)*:
• Each route cluster card in `WorkoutClustersScreen` SHALL resolve and render a starting location badge: `📍 Start: {startLocationName}` using dynamic resolution from `KnownLocationsDatabaseManager`.
5. *100% Localization Parity Across 9 Locales*:
• String resources `cluster_start_location`, `known_location_routes_header`, and `filter_by_start_location` SHALL be defined across all 9 supported application locales (EN, DE, ES, FR, IT, JA, NL, PL, PT) with 0 missing translations.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement only (REQ-UI-186). No existing requirements modified.
2. *Historical Origin & Commit Trace*: Ticket `ATT-1402` under Epic `ATT-1396` (*Lieblingsorte: Visible Value, Auto-Naming & Tracking Integration*).
3. *Root Reason for Existing Formulation*: Route clusters and start locations operated in separate data silos; linking them bridges the athlete's mental model without requiring database migrations.
4. *Preservation of Core Invariants*: SQLite schemas (`WorkoutClusters.db`, `StartLocation2Altitude.db`) remain 100% unchanged; existing cluster math and centroid updates remain intact; universal delete context menu (`REQ-UI-061`) preserved; 9-language localization parity maintained.

**Acceptance Criteria (Given-When-Then)**:
• *Given* a new route cluster learned from a workout starting at an unmapped trailhead,
• *When* `WorkoutClusterEngine.learnFromWorkout` creates the cluster,
• *Then* a new `KnownLocation` SHALL be automatically persisted in `StartLocation2Altitude.db` with geocoded name and calibrated altitude.
• *Given* an athlete on `KnownLocationsScreen` viewing location "Zuhause" which has 3 route clusters starting within its 200m geofence,
• *When* the card is composed,
• *Then* 3 route cluster chips SHALL be displayed on the card, and tapping one SHALL navigate directly to that route cluster.
• *Given* an athlete browsing `WorkoutClustersScreen`,
• *When* inspecting a cluster card starting at "Zuhause",
• *Then* a badge `📍 Start: Zuhause` SHALL be displayed.
• *Given* the cluster list filtered by starting location "Zuhause",
• *When* evaluating clusters against the filter,
• *Then* only clusters whose starting coordinates fall within the "Zuhause" geofence radius SHALL be displayed.

**Invariants**: SQLite schema stability, cluster clustering math, card edit/delete interactions, and 9-language localization parity MUST NOT be broken.

### REQ-UI-187: Selection of Lieblingsorte and Lieblingsstrecken in Workout and Cluster Filter Dialogs [Verified]
- **Tests**: `TST-UI-141`, `TST-UI-508`, `TST-UI-509`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `WorkoutFilterCriteria.kt`, `WorkoutFilterBottomSheet.kt`, `ClusterFilterBottomSheet.kt`, `ActiveFilterChipsRow.kt`, `ActiveClusterFilterChipsRow.kt`, `WorkoutTabsScreen.kt`, `WorkoutClustersTabsScreen.kt`
- **Rationale**: The system SHALL provide interactive selection of favorite start locations (*Lieblingsorte*) and route clusters (*Lieblingsstrecken*) across workout and cluster filter dialogs (ATT-1593):
1. *Workout Filter Domain Extension (`WorkoutFilterCriteria.kt`)*:
• `WorkoutFilterCriteria` SHALL define nullable properties: `clusterId: Long? = null` and `clusterName: String? = null`.
• *Predicate Evaluation (`matches`)*: When `clusterId != null`, the system SHALL evaluate `workout.clusterId`. If `workout.clusterId != clusterId`, `matches` SHALL return `false`.
• `activeFilterCount` SHALL increment by 1 when `clusterId != null`.
• `toJson()` and `fromJson()` SHALL serialize and deserialize `clusterId` and `clusterName` losslessly for DataStore preference persistence.
2. *Workout Filter Bottom Sheet Enhancement (`WorkoutFilterBottomSheet.kt`)*:
• `WorkoutFilterBottomSheet` SHALL accept `knownLocations: List<KnownLocationItem> = emptyList()` and `availableClusters: List<WorkoutCluster> = emptyList()`.
• When `knownLocations` is non-empty, the sheet SHALL render a "Lieblingsorte" (*Favorite Locations*) section with single-selectable `FilterChip` items for each location.
• When `availableClusters` is non-empty, the sheet SHALL render a "Lieblingsstrecken" (*Favorite Tracks*) section with single-selectable `FilterChip` items for each cluster.
• Tapping an already selected chip SHALL deselect it.
• `onApply` SHALL preserve and propagate selected `startLocation*` and `cluster*` criteria.
• `onClearAll` SHALL reset both location and cluster selections.
3. *Lieblingsstrecken Filter Bottom Sheet Enhancement (`ClusterFilterBottomSheet.kt`)*:
• `ClusterFilterBottomSheet` SHALL accept `knownLocations: List<KnownLocationItem> = emptyList()`.
• When `knownLocations` is non-empty, the sheet SHALL render a "Lieblingsorte" (*Favorite Locations*) section with single-selectable `FilterChip` items.
• Selecting a location chip SHALL bind its coordinates (`startLocationLat`, `startLocationLng`), radius (`startLocationRadiusM`), and display name (`startLocationName`) to `ClusterFilterCriteria`.
• `onApply` SHALL propagate the spatial start location criteria, and `onClearAll` SHALL reset them.
4. *Active Filter Chips Row Presentation*:
• In `ActiveFilterChipsRow.kt` (workouts), when `clusterId != null` or `!clusterName.isNullOrBlank()`, the system SHALL display an active chip: `"🗺️ ${criteria.clusterName ?: stringResource(R.string.my_locations)}"`. Tapping remove SHALL invoke `onRemoveCluster: () -> Unit`.
• In `ActiveClusterFilterChipsRow.kt` (clusters), when `startLocationLat != null && startLocationLng != null` or `!startLocationName.isNullOrBlank()`, the system SHALL display an active chip: `"📍 ${criteria.startLocationName ?: stringResource(R.string.filter_start_location)}"`. Tapping remove SHALL invoke `onRemoveStartLocation: () -> Unit`.
5. *Screen & ViewModel Data Hoisting*:
• `WorkoutSummariesViewModel` SHALL collect `KnownLocationsRepository.locationsFlow` and `WorkoutClusterRepository.allClusters`, passing them to `WorkoutSummariesTabbedScreen` -> `WorkoutTabsScreen` -> `WorkoutFilterBottomSheet`.
• `WorkoutClustersViewModel` SHALL collect `KnownLocationsRepository.locationsFlow`, passing them to `WorkoutClustersTabsScreen` -> `ClusterFilterBottomSheet`.
6. *100% 9-Language Localization Parity*:
• Reused string resources `known_locations_title`, `my_locations`, and `filter_start_location` SHALL be verified across all 9 supported locales (EN, DE, ES, FR, IT, JA, NL, PL, PT) with 0 missing entries.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement (REQ-UI-187), complementing `REQ-UI-185` (*Lieblingsorte: Drill-Down Filter to View Workouts by Starting Location*) and `REQ-UI-186` (*Lieblingsorte & Lieblingsstrecken Bridge*).
2. *Historical Origin & Commit Trace*: Ticket `ATT-1401` (commit `6bfbcfc7`) and `ATT-1402` (commit `91acde33`) under Epic `ATT-1396`.
3. *Root Reason for Existing Formulation*: Earlier tickets focused on establishing the spatial distance calculation engine and 1-tap navigation from list cards. Integrating interactive chips into the modal bottom sheets was deferred to maintain small, focused deliverables.
4. *Preservation of Core Invariants*: 1-tap card drill-down navigation remains 100% functional; DataStore JSON serialization defaults to null for backward compatibility; geodetic distance calculations via `WorkoutClusterEngine.distanceBetween` remain unchanged; 9-language localization parity strictly maintained.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an athlete opening `WorkoutFilterBottomSheet` in the Workouts tab with available known locations,
• *When* selecting a location chip (e.g. "Zuhause") and applying the filter,
• *Then* the workout list SHALL filter to workouts starting within the geofence radius of "Zuhause", and `ActiveFilterChipsRow` SHALL display `"📍 Zuhause"`.
• *Given* an athlete opening `WorkoutFilterBottomSheet` with available route clusters,
• *When* selecting a cluster chip (e.g. "Isarrunde") and applying the filter,
• *Then* the workout list SHALL filter strictly to workouts matching `clusterId`, and `ActiveFilterChipsRow` SHALL display `"🗺️ Isarrunde"`.
• *Given* an athlete opening `ClusterFilterBottomSheet` in the Lieblingsstrecken tab,
• *When* selecting a location chip (e.g. "Büro") and applying the filter,
• *Then* the cluster list SHALL filter to route clusters starting within the geofence radius of "Büro", and `ActiveClusterFilterChipsRow` SHALL display `"📍 Büro"`.
• *Given* active location or cluster filter chips in either filter sheet,
• *When* the athlete taps the chip again or taps "Alle zurücksetzen",
• *Then* the corresponding filter criteria SHALL be cleared.
• *Given* an active `"🗺️ Isarrunde"` chip in the workout list or `"📍 Büro"` chip in the cluster list,
• *When* the athlete taps the remove 'X' icon on the chip,
• *Then* that specific filter dimension SHALL be cleared while preserving all other active criteria.

**Invariants**: Zero regression in existing filter dimensions (query, date, sport, equipment, distance, duration), JSON preference backward compatibility, single-thread SQLite concurrency (`KnownLocationsDB-Thread`), and 9-language localization parity MUST NOT be broken.

### REQ-UI-188: Lieblingsorte: Compact Interactive Routes Badge on KnownLocationCard [Verified]
- **Tests**: `TST-UI-142`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `KnownLocationsScreen.kt`, `ATrainingTrackerApp.kt`, `strings.xml` (all 9 locales)
- **Rationale**: The system SHALL provide a compact interactive routes badge on `KnownLocationCard` summarizing linked route clusters (*Lieblingsstrecken*) departing from that favorite location, replacing the multi-line individual cluster chips list (ATT-1594):
1. *Compact Routes Badge Visual Presentation (`KnownLocationCard` in `KnownLocationsScreen.kt`)*:
• When a favorite location has one or more linked route clusters (`linkedClusters.isNotEmpty()`), the card SHALL render a single compact interactive `Surface` badge alongside the Starts-Badge.
• The badge SHALL be styled consistently with the Starts-Badge (`RoundedCornerShape(12.dp)`, `defaultMinSize(minHeight = 48.dp)` for accessible touch ergonomics).
• The badge content SHALL include:
- Route icon (`Icons.Default.Route`),
- Pluralized count string using `@plurals/known_locations_routes` (e.g. `"1 Strecke"`, `"3 Strecken"`),
- Trailing navigation chevron (`Icons.AutoMirrored.Filled.ArrowForward`).
• *Metrics & Badges Layout*: The altitude metric, Starts-Badge, and Routes-Badge SHALL be arranged in a horizontal `FlowRow` or `Row` ensuring clean wrapping without clipping on narrow screens.
• *Zero Routes State*: When `linkedClusters.isEmpty()`, the routes badge SHALL be omitted.
2. *Interactive Drill-Down Navigation (`ATrainingTrackerApp.kt`)*:
• `KnownLocationCard` and `KnownLocationsScreen` SHALL expose navigation callback `onShowRoutes: (KnownLocationItem) -> Unit`.
• Tapping the Routes-Badge SHALL invoke `onShowRoutes(item)`.
• In `ATrainingTrackerApp.kt`, `onShowRoutes` SHALL construct `ClusterFilterCriteria` preset with the location's name (`startLocationName`), coordinates (`startLocationLat`, `startLocationLng`), and radius (`startLocationRadiusM`), update `WorkoutClustersViewModel.setFilterCriteria(criteria)`, and navigate to `NavRoutes.LOCATIONS`.
3. *100% 9-Language Localization Parity*:
• Plural resource `known_locations_routes` and accessible content description string `known_locations_view_routes` SHALL be defined across all 9 supported application locales (EN, DE, ES, FR, IT, JA, NL, PL, PT) with 0 missing translations.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement (REQ-UI-188), refining and replacing the `FlowRow` chip presentation aspect of `REQ-UI-186` (`ATT-1402`).
2. *Historical Origin & Commit Trace*: Ticket `ATT-1402` (commit `91acde33`) under Epic `ATT-1396`.
3. *Root Reason for Existing Formulation*: In `ATT-1402`, individual chips were rendered in a `FlowRow` to prove the algorithmic bridge between locations and clusters. As routes proliferated, this caused visual bloat on location cards.
4. *Preservation of Core Invariants*: The algorithmic association via `clustersByLocationId` remains unchanged; card body edit (`onEdit`) and universal delete context menu (`REQ-UI-061`) remain intact; Starts-Badge (`onShowWorkouts`) remains intact; 9-language localization parity strictly maintained.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an athlete on `KnownLocationsScreen` viewing a location with 3 linked route clusters,
• *When* the card is composed,
• *Then* instead of multiple individual route chips, a single badge `"3 Strecken >"` SHALL be displayed alongside the Starts-Badge.
• *Given* a location card displaying the routes badge,
• *When* the athlete taps the routes badge,
• *Then* the app SHALL navigate to `NavRoutes.LOCATIONS` displaying only route clusters starting within that location's geofence.
• *Given* a favorite location with 0 linked route clusters,
• *When* the card is composed,
• *Then* the routes badge SHALL NOT be rendered.
• *Given* a location card,
• *When* tapping the card body outside the starts or routes badges, `EditKnownLocationDialog` SHALL launch; when long-pressing, the universal delete-only menu SHALL appear.

**Invariants**: Universal delete-only context menu (`REQ-UI-061`), Starts-Badge drill-down navigation, card single-tap editing, single-thread SQLite database access, and 9-language localization parity MUST NOT be broken.

### REQ-UI-189: UI/Sheets: Standardized Boundaries, Contours, Drop Shadow Elevation & Drag Affordances [Verified]
- **Tests**: `TST-UI-143`, `TST-UI-172`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `BottomSheetDesign.kt`, `MinimumDragHandle.kt`, `SensorGridScreen.kt`, `MapDetailLayout.kt`, `LIveSegmentSheet.kt`, `AppModalBottomSheet.kt`, `FilterBottomSheetScaffold.kt`, `MapScreenWithTrack.kt`, `PeriodMapScreen.kt`, `WorkoutClusterHeatmapScreen.kt`
- **Rationale**: The system SHALL standardize visual boundaries, contours, elevation drop shadows, and interactive drag affordances across all bottom sheet popups and persistent scaffold sheets without introducing contrasting background colors or multi-color bars (ATT-1588):
1. *Design Tokens & Core Bottom Sheet Architecture (`BottomSheetDesign.kt`)*:
• The system SHALL provide a centralized design tokens object `BottomSheetDesign` in `com.atrainingtracker.trainingtracker.ui.components.core` defining:
- Standardized corner radius: `SheetCornerRadius = 20.dp`,
- Standardized top curvature shape: `SheetShape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)`,
- Standardized drop shadow elevation: `SheetShadowElevation = 8.dp`,
- Standardized tonal elevation: `SheetTonalElevation = 2.dp`,
- Standardized contour border: `BorderWidth = 1.dp`, `BorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)`,
- Extension modifier `Modifier.sheetContour(borderColor, shape, borderWidth)` applying border stroke and top corner clipping.
2. *Live Segment & Scaffold Bottom Sheets (`SensorGridScreen.kt`, `LIveSegmentSheet.kt`, `MapDetailLayout.kt`)*:
• In `SensorGridScreen.kt`, `BottomSheetScaffold` SHALL specify `sheetShape = BottomSheetDesign.SheetShape`, `sheetShadowElevation = BottomSheetDesign.SheetShadowElevation` (8.dp), and `sheetTonalElevation = BottomSheetDesign.SheetTonalElevation` (2.dp).
• The Live Segment sheet content container SHALL apply `sheetContour` to cleanly delineate its top edge from the sensor grid and map canvas.
• In `MapDetailLayout.kt` and `LIveSegmentSheet.kt`, header surfaces SHALL NOT override the top curvature with `RectangleShape` when hosted within a bottom sheet (`!useStatusBarsPadding`).
3. *Modal Bottom Sheet Harmonization (`AppModalBottomSheet.kt`, `FilterBottomSheetScaffold.kt`)*:
• All modal bottom sheets (`AppModalBottomSheet`, `AppBottomSheetContent`, `FilterBottomSheetScaffold`) SHALL use `BottomSheetDesign.SheetShape`, `BottomSheetDesign.SheetTonalElevation`, and apply the 1.dp `outlineVariant` top contour stroke.
4. *Prominent Drag Handle Affordance (`MinimumDragHandle.kt`)*:
• `MinimumDragHandle` SHALL provide a standardized, prominent drag handle pill of dimension $36\text{dp} \times 4\text{dp}$ with `MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)` and padded bounds ($12\text{dp}$ top, $6\text{dp}$ bottom) ensuring unambiguous interactivity affordance.
5. *Persistent Map Bottom Sheets (`MapScreenWithTrack.kt`, `PeriodMapScreen.kt`, `WorkoutClusterHeatmapScreen.kt`)*:
• Persistent map scaffolds SHALL uniformly specify `sheetShape = BottomSheetDesign.SheetShape` and `sheetShadowElevation = 8.dp`.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement (REQ-UI-189), complementing `REQ-UI-148` (*Standardized Edge-to-Edge Bottom Sheet Insets & Core UI Design Components*).
2. *Historical Origin & Commit Trace*: Ticket `ATT-939` (commit `9b7405e3`) under Epic `ATT-355` (*Good and consistent UI*).
3. *Root Reason for Existing Formulation*: Previous work focused strictly on system window insets, navigation bar padding, and basic keyboard handling. Visual boundary delineation, top border strokes, and elevation drop shadows were omitted, causing sheets in dark mode to blend into the underlying canvas.
4. *Preservation of Core Invariants*: Window insets handling (`navigationBarsPadding()`, `imePadding()`) remains 100% intact; zero arbitrary background color shifts (user-mandated constraint); map underlay drawing behavior in `SensorGridScreen` preserved; single-thread SQLite concurrency unaffected.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an active live segment in `SensorGridScreen`,
• *When* viewing the cockpit in Light or Dark mode,
• *Then* the bottom sheet SHALL be delimited by 20dp top rounded corners, a subtle 1dp `outlineVariant` contour stroke, an 8dp drop shadow, and a prominent drag handle pill without arbitrary background color shifts.
• *Given* any modal bottom sheet (`AppModalBottomSheet`, `FilterBottomSheetScaffold`),
• *When* presented against the application background,
• *Then* the sheet SHALL exhibit 20dp top rounded curvature and a 1dp `outlineVariant` contour border.
• *Given* persistent map bottom sheets (`MapScreenWithTrack`, `PeriodMapScreen`, `WorkoutClusterHeatmapScreen`),
• *When* the bottom sheet peeks or expands,
• *Then* it SHALL cast an 8dp drop shadow elevation with 20dp top corner curvature.

**Invariants**: Edge-to-edge system insets (`REQ-UI-148`), map underlay interaction, and zero arbitrary color shifts MUST NOT be broken.

### REQ-UI-190: UI/Theme: Harmonious Dark Mode Tonal Progression, Surface Container Elevation & Contrast Alignment [Verified]
- **Tests**: `TST-UI-144`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `Color.kt`, `Theme.kt`, `MappableListItem.kt`, `LapButton.kt`, `values-night/color.xml`
- **Rationale**: The system SHALL establish a cohesive dark theme palette hierarchy, surface container elevation progression, and contrast compliance (ATT-1585):
1. *Slate-Blue Tonal Progression (`Color.kt`, `Theme.kt`)*:
• The dark theme SHALL provide a coordinated slate-blue palette progression connecting status bars, screen headers, and tab rows:
- Top status bar and screen header container: `primaryContainer = DarkPrimaryContainer` (`#162032`),
- Header title and actions: `onPrimaryContainer = DarkOnPrimaryContainer` (`#D4E3FF`),
- Tab row container: `surfaceContainerHighest = surfaceContainerHighestDark` (`#1B2436`),
- Tab active indicator: `primary = DarkPrimary` (`#A6C8FF`).
2. *Surface Container Elevation Hierarchy (`Color.kt`, `Theme.kt`)*:
• `DarkColorScheme` SHALL define distinct, graduated surface container elevation tokens:
- Canvas background: `background = DarkBackground` (`#121214`),
- Base surface: `surface = DarkSurface` (`#16161A`),
- Lowest container: `surfaceContainerLowest = surfaceContainerLowestDark` (`#0E0E10`),
- Low container (elevated cards): `surfaceContainerLow = surfaceContainerLowDark` (`#1C2028`),
- Standard container: `surfaceContainer = surfaceContainerDark` (`#20242E`),
- High container: `surfaceContainerHigh = surfaceContainerHighDark` (`#282F3D`),
- Outline border: `outlineVariant = DarkOutlineVariant` (`#2B3342`).
3. *Card Boundary Definition (`MappableListItem.kt`)*:
• `MappableListItem` SHALL apply a subtle hairline border stroke `BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))` to `ElevatedCard`, ensuring clean visual demarcation of workout cards, period summaries, and location cards against the canvas in both dark and light modes.
4. *Theme-Aware Lap Button & Resource Contrast (`LapButton.kt`, `res/values-night/color.xml`)*:
• `LapButton` active state SHALL use `MaterialTheme.colorScheme.primary` and `onPrimary`.
• In `res/values-night/color.xml`, disabled colors SHALL be defined as: `lap_button_disabled_background` = `#22252C`, `lap_button_disabled_text` = `#5A6270`, `color_primary` = `#A6C8FF`, and `color_on_primary` = `#003060`.
• Primary and text contrast against surfaces SHALL meet WCAG AA (>= 4.5:1).

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement (REQ-UI-190), complementing `REQ-UI-171` (*Pure Black AMOLED Dark Mode Theme*) and building upon Epic `ATT-1157` (*Optimize dark mode*).
2. *Historical Origin & Commit Trace*: Commit `2e631940` (Ticket `SCRUM-134`): "ui: centralize color theming and normalize component backgrounds".
3. *Root Reason for Existing Formulation*: In `SCRUM-134`, all surface tokens were flattened to `#1B1B1F` and `surfaceTint` set to `Color.Transparent` to eliminate unwanted global Material 3 tonal elevation overlays that caused purple/blue tinting artifacts across sensor grid tiles and dialogs. However, this left Dark Mode without subtle tonal progression or card elevation.
4. *Preservation of Core Invariants*: AMOLED pure black cockpit theme (`AmoledDarkColorScheme`, `REQ-UI-171`) remains 100% untouched (`#000000` pitch black); Light theme colors remain unchanged; sensor grid metric tiles and zone colors unaffected; WCAG AA/AAA contrast standards preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* the app is running in Dark Mode,
• *When* viewing screens with a header and tab bar (`TrackingTabsScreen`, `WorkoutTabsScreen`, `PeriodsTabsScreen`),
• *Then* the header and tab row SHALL display a coordinated slate-blue tonal progression (`#162032` -> `#1B2436`), eliminating the navy/charcoal horizontal mismatch.
• *Given* the Workouts list (`Einheiten`), Periods, or Locations list in Dark Mode,
• *When* viewing cards,
• *Then* each card SHALL render with elevated surface color `#1C2028` and subtle outline `#2B3342`, distinct from background `#121214`.
• *Given* the Runden cockpit tab when tracking is stopped or paused in Dark Mode,
• *When* the "+ Runde" button is displayed,
• *Then* the button background SHALL render as a muted dark container (`#22252C` with text `#5A6270`) without glaring light-grey background.
• *Given* primary actions and selected items in Dark Mode,
• *When* evaluating text and icon contrast against dark surfaces,
• *Then* the contrast ratio SHALL meet or exceed WCAG AA standards (>= 4.5:1 for normal text, >= 3:1 for large text/icons).

**Invariants**: AMOLED pure black cockpit theme (`REQ-UI-171`), Light theme palette, and sensor telemetry clarity MUST NOT be broken.

### REQ-UI-191: Standardized List Item Heading Click Interaction Semantics & Dedicated Edit Affordance [Verified]
- **Tests**: `TST-UI-145`, `TST-UI-387`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `RouteSummaryHeader.kt`, `RouteItem.kt`, `RouteList.kt`, `RouteTabbedScreen.kt`, `RoutesScreen.kt`
- **Rationale**: The system SHALL standardize card heading and body click interaction semantics across all application list views according to domain entity classification, while providing dedicated, theme-standardized edit affordances (ATT-1541):
1. *Domain Interaction Taxonomy*:
• *Spatial / Analytical Entities (Workouts, Routes, Workout Clusters, Segments)*: Tapping the card header, title, description, metrics, or media preview SHALL navigate directly to the primary detailed inspection view (`TrackOnMapScreen` for Workouts per `REQ-SET-071`, Route Details for Routes, `WorkoutClusterHeatmapScreen` for Clusters, and `SegmentOnMapScreen` for Segments). Navigating to edit mode via title or card body tap is strictly prohibited.
• *Configuration Entities (Equipment, Sport Types, Favorite Locations)*: Configuration entities without a secondary map or telemetry inspection view SHALL preserve direct-to-editor single-tap navigation (`onEdit` / `EditKnownLocationDialog` per `REQ-UI-061`, `REQ-UI-165`, `REQ-UI-166`, `REQ-UI-180`, `EquipmentTabsScreen`, `SportTypesTabsScreen`).
2. *Dedicated Route Header Edit Button*:
• `RouteSummaryHeader.kt` SHALL incorporate a dedicated edit action button (`IconButton`) placed in the header identity row.
• The edit button SHALL display `Icons.Default.Edit`, tinted with `MaterialTheme.colorScheme.primary` per `REQ-UI-182`.
• The edit button SHALL strictly comply with Material 3 accessibility guidelines with a minimum touch target bounding box of at least $48\times 48\text{dp}$ (`Modifier.size(48.dp)`), housing a $24\times 24\text{dp}$ icon.
• The edit button SHALL use localized accessibility content description `@string/route_edit`.
• Tapping this edit button SHALL invoke `onEditClick` (launching `EditRouteScreen`).
3. *Route Item Heading Click Harmonization*:
• In `RouteItem.kt`, tapping `RouteSummaryHeader` SHALL invoke `onMapClick(summary.id)` to open route details, eliminating the previous split navigation where tapping the header edited the route while tapping the map inspected it.
• The long-press context menu of `RouteItem.kt` SHALL remain strictly compliant with `REQ-UI-061` (delete-only, plus the documented `REQ-EXT-004` Strava duplication lifecycle action; no secondary non-destructive actions).

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: `REQ-SET-071` (Workouts click-to-map standard), `REQ-UI-061` (Universal delete-only context menu), `REQ-UI-165` / `REQ-UI-166` / `REQ-UI-180` (Favorite locations configuration single-tap), and `REQ-UI-182` (Unified edit symbol and primary tint).
2. *Historical Origin & Commit Trace*: `ATT-850` (`2bf79cdf`, Workouts inspection standard), `ATT-1449` (`191ba05d`, Locations single-perspective), `ATT-1523` (`191ba05d`, Delete-only context menu), `ATT-1536` (Edit button styling).
3. *Root Reason for Inconsistency*: In `RouteItem.kt`, clicking `RouteSummaryHeader` originally defaulted to edit navigation because a dedicated edit button had not been designed into the header, creating cognitive friction against the established `REQ-SET-071` inspection-first pattern.
4. *Preservation of Core Invariants*: Full touch target compliance ($\ge 48\text{dp}$), universal delete menu integrity (`REQ-UI-061`), configuration entity single-tap preservation, and 9-language localization parity (`@string/route_edit`) are strictly maintained.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an athlete viewing the routes list in `RouteTabbedScreen` or `RoutesScreen`,
• *When* the athlete taps the route header, map preview, or elevation profile,
• *Then* the system SHALL navigate to the route inspection/details view (`selectedRouteIdForDetails = id`).
• *Given* an athlete viewing a route card in `RouteItem`,
• *When* inspecting the header row,
• *Then* a dedicated edit button with `Icons.Default.Edit` tinted with `MaterialTheme.colorScheme.primary` and a minimum $48\times 48\text{dp}$ touch target SHALL be visible.
• *When* the athlete taps the dedicated edit button in the header,
• *Then* the system SHALL open `EditRouteScreen` (`selectedRouteIdForEdit = id`).
• *Given* an athlete interacting with configuration list items (Lieblingsorte, Equipment, Sport Types),
• *When* the card body is tapped,
• *Then* the configuration/edit dialog SHALL open directly, preserving the configuration entity pattern.

**Invariants**: Long-press context menus MUST NOT include non-destructive edit items (`REQ-UI-061`); route selection toggle (`onToggleSelection`) and Strava duplication (`onDuplicateAsLocal`) MUST remain functional; 9-language localization parity MUST be preserved.

### REQ-UI-192: Interactive Elevation Profile Zoom, Pan & Centered Scaling Navigation [Verified]
- **Tests**: `TST-UI-146`, `TST-UI-169`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `ElevationProfile.kt`
- **Rationale**: The system SHALL support horizontal zoom and pan interactions on the elevation profile chart (`ElevationProfile.kt`), enabling athletes to inspect specific climbs, gradient pitches, and interval segments in detail while preserving synchronized scrubbing and vertical height constraints (ATT-527):
1. *Horizontal Multi-Touch Pinch Zoom & Centered Scaling*:
• The system SHALL support horizontal pinch-to-zoom gestures scaling the visible distance window $W_{\text{vis}} = \frac{D_{\text{total}}}{Z}$ between $1.0x$ (full workout distance) and $10.0x$, anchored around the gesture centroid.
• Zooming SHALL scale purely along the horizontal distance axis; vertical altitude bounds and adaptive height (`calculateElevationBounds`, `calculateElevationProfileHeight`) SHALL remain invariant.
2. *Pan Navigation & Boundary Clamping*:
• When zoomed in ($Z > 1.0x$), horizontal drag navigation SHALL pan the visible distance window $D_{\text{start}}$ across the route.
• $D_{\text{start}}$ SHALL be strictly clamped within $[0.0, (D_{\text{total}} - W_{\text{vis}}).coerceAtLeast(0.0)]$, preventing empty space panning beyond route start or termination.
3. *Instant Reset Affordances*:
• Double-tapping the graph surface SHALL immediately reset the profile to the full $1.0x$ view ($D_{\text{start}} = 0.0$, $Z = 1.0x$).
• When zoomed in ($Z > 1.0x$), the profile SHALL display an interactive overlay badge indicating the current zoom factor (e.g. `2.5x`) and providing an explicit tap-to-reset action restoring $1.0x$.
4. *Synchronized Coordinate Scrubbing within Zoomed Window*:
• Scrubbing coordinates on the visible curve SHALL translate the canvas position to absolute workout distance ($d = D_{\text{start}} + u \cdot W_{\text{vis}}$) and dispatch to `onDistanceSelected(d)`.
• The vertical dashed guide line, coordinate marker, and distance/altitude label SHALL render accurately at the zoomed screen position and hide cleanly when $d$ lies outside $[D_{\text{start}}, D_{\text{start}} + W_{\text{vis}}]$.
5. *Adaptive X-Axis Distance Ticks*:
• Grid ticks and distance labels along the X-axis SHALL automatically adapt their step interval based on $W_{\text{vis}}$ rather than $D_{\text{total}}$, providing meaningful intermediate milestones across zoomed sections.
6. *Accessible Controls & Short Track Safeguards*:
• The overlay SHALL provide accessible zoom-in (`+`) and zoom-out (`-`) controls for one-handed navigation.
• For short routes ($D_{\text{total}} \le 10\text{m}$), zoom interactions SHALL be gracefully disabled.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: `REQ-UI-013` (Harmonized Elevation Extrema) and `ATT-508` / `REQ-UI-126` (Adaptive elevation profile bounds & vertical sanitization).
2. *Historical Origin & Commit Trace*: `ATT-508` (`calculateElevationBounds` outlier clamping and minimum 20m span) and `ElevationProfile.kt` initial Compose implementation.
3. *Root Reason for Existing Formulation*: `ElevationProfile` originally hardcoded a static $0 .. \text{totalDist}$ mapping without visible window state or transform gesture processing.
4. *Preservation of Core Invariants*: Vertical bounds sanitization (`calculateElevationBounds`), grade percentage calculations (`TTColor.Zone1 .. Zone5`), adaptive canvas height (`calculateElevationProfileHeight`), and absolute distance metric dispatch (`onDistanceSelected`) MUST remain strictly intact.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an elevation profile displayed with route path points,
• *When* the athlete performs a horizontal pinch-to-zoom gesture (or taps the zoom-in button),
• *Then* the visible distance window SHALL scale accordingly (up to $10.0x$), expanding climbs and local terrain features horizontally.
• *Given* the elevation profile is in a zoomed-in state ($Z > 1.0x$),
• *When* the athlete drags horizontally,
• *Then* the profile SHALL pan across the distance axis, strictly clamped within $[0 .. D_{\text{total}}]$.
• *Given* the elevation profile is zoomed in,
• *When* the athlete double-taps the graph surface or taps the overlay reset button,
• *Then* the view SHALL immediately reset to the full $1.0x$ extent ($0 .. D_{\text{total}}$).
• *Given* the elevation profile is zoomed in,
• *When* the athlete scrubs along the visible curve,
• *Then* `onDistanceSelected` and the coordinate marker SHALL accurately reflect the absolute workout distance and elevation of the inspected point.

**Invariants**: Vertical elevation bounds, grade coloring thresholds, adaptive profile height, and absolute distance contract MUST NOT be altered.

### REQ-UI-193: Workout Filter: Categorized Equipment Sections (Bikes & Shoes) with Sport-Tab Awareness [Verified]
- **Tests**: `TST-UI-147`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `WorkoutFilterBottomSheet.kt`
- **Rationale**: The system SHALL partition and present equipment filter options in `WorkoutFilterBottomSheet` into distinct, sport-categorized sections with tab-context sensitivity (ATT-1641):
1. *Sport Category Partitioning*:
• Equipment items derived from `allWorkouts` SHALL be partitioned by sport type into `availableBikes` (`bSportType == BSportType.BIKE`), `availableShoes` (`bSportType == BSportType.RUN`), and `availableOtherEquipment` (for unclassified or other sports where `bSportType != BSportType.BIKE && bSportType != BSportType.RUN`).
• If a specific sub-sport filter is active (`localSportId != null`), only equipment associated with that sub-sport ID SHALL be included within the respective category.
2. *Tab-Context Sensitive Section Rendering*:
• In the Bike tab (`activeBSportType == BSportType.BIKE`): The bottom sheet SHALL render only the **Fahrräder** / **Bikes** section (`R.string.equipment_type_bike`) if `availableBikes.isNotEmpty()`. Running shoes and other gear SHALL NOT be displayed.
• In the Run tab (`activeBSportType == BSportType.RUN`): The bottom sheet SHALL render only the **Schuhe** / **Shoes** section (`R.string.equipment_type_shoe`) if `availableShoes.isNotEmpty()`. Bikes and other gear SHALL NOT be displayed.
• In the Other tab (`activeBSportType == BSportType.UNKNOWN`): The bottom sheet SHALL render only the **Sonstige Ausrüstung** / **Equipment** section (`R.string.filter_section_equipment`) if `availableOtherEquipment.isNotEmpty()`.
• In the All Workouts tab (`activeBSportType == null`): The bottom sheet SHALL render each non-empty category as a distinct, clearly titled section (**Fahrräder**, **Schuhe**, and **Sonstige Ausrüstung**) rather than mixing all gear into a single monolithic group.
3. *Single Selection State & Invariant Maintenance*:
• `localEquipId: Long?` SHALL remain mutually exclusive across all equipment sections. Selecting any chip SHALL toggle that ID and immediately deselect any equipment from other categories.
• A `LaunchedEffect` monitoring all available equipment (`availableBikes + availableShoes + availableOtherEquipment`) SHALL automatically clear `localEquipId` (`null`) whenever the currently selected equipment ID is no longer present in the active filtered set.
4. *100% 9-Language Localization Parity*:
• Section headers SHALL utilize localized string resources `equipment_type_bike`, `equipment_type_shoe`, and `filter_section_equipment` across all 9 supported locales (EN, DE, ES, FR, IT, JA, NL, PL, PT) with 0 missing entries.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement (REQ-UI-193), refining and extending the equipment presentation dimension of `REQ-UI-157` (*Tab-Aware Sport Selection & Sport-Aware Equipment*).
2. *Historical Origin & Commit Trace*: Ticket `ATT-882` (Sprint `2026-38`).
3. *Root Reason for Existing Formulation*: `REQ-UI-157` partitioned equipment by tab context but grouped all gear under a single monolithic "Ausrüstung" section, mixing bikes and running shoes in the overview tab.
4. *Preservation of Core Invariants*: Incompatible equipment auto-clearing preserved; `WorkoutFilterCriteria` data class, JSON serialization, and database schemas remain 100% unchanged; single-selection mutual exclusivity preserved; 9-language localization parity maintained.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an athlete opens `WorkoutFilterBottomSheet` from the Bike tab (`BSportType.BIKE`),
• *When* viewing equipment filters,
• *Then* only the "Fahrräder" section SHALL be displayed, and no running shoes SHALL be visible.
• *Given* an athlete opens `WorkoutFilterBottomSheet` from the Run tab (`BSportType.RUN`),
• *When* viewing equipment filters,
• *Then* only the "Schuhe" section SHALL be displayed, and no bikes SHALL be visible.
• *Given* an athlete opens `WorkoutFilterBottomSheet` from the "All Workouts" tab (`activeBSportType == null`),
• *When* viewing equipment filters,
• *Then* bikes and shoes SHALL be rendered in separate, clearly titled sections ("Fahrräder" and "Schuhe").
• *Given* an active equipment selection (`localEquipId != null`),
• *When* the athlete switches to a sport tab or selects a sport sub-type where that equipment is not available,
• *Then* `localEquipId` SHALL automatically be cleared (`null`).

**Invariants**: `WorkoutFilterCriteria` data class, JSON preference backward compatibility, and database schemas MUST NOT be altered.

### REQ-UI-194: Filter Dialogs: Clean Chip Formatting (Emoji Removal) and Prioritized Section Ordering [Verified]
- **Tests**: `TST-UI-148`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `WorkoutFilterBottomSheet.kt`, `ClusterFilterBottomSheet.kt`, `ActiveFilterChipsRow.kt`, `ActiveClusterFilterChipsRow.kt`
- **Rationale**: The system SHALL eliminate emoji prefixes from filter chips and reorder filter sections to prioritize frequently adjusted criteria over extensive location and route lists (ATT-1642):
1. *Clean Chip Label Formatting (Emoji Removal)*:
• In `WorkoutFilterBottomSheet.kt`, chip labels for Favorite Locations (*Lieblingsorte*) SHALL render `loc.name` without any emoji prefix (removing `📍 `).
• In `WorkoutFilterBottomSheet.kt`, chip labels for Favorite Tracks (*Lieblingsstrecken*) SHALL render `cluster.name` without any emoji prefix (removing `🗺️ `).
• In `ClusterFilterBottomSheet.kt`, chip labels for Known Locations (*Lieblingsorte*) SHALL render `loc.name` without any emoji prefix (removing `📍 `).
• In `ActiveFilterChipsRow.kt`, the active chip for starting location SHALL format as `criteria.startLocationName ?: stringResource(R.string.filter_start_location)` without any emoji prefix (removing `📍 `).
• In `ActiveFilterChipsRow.kt`, the active chip for route cluster SHALL format as `criteria.clusterName ?: stringResource(R.string.my_locations)` without any emoji prefix (removing `🗺️ `).
• In `ActiveClusterFilterChipsRow.kt`, the active chip for starting location SHALL format as `criteria.startLocationName ?: stringResource(R.string.filter_start_location)` without any emoji prefix (removing `📍 `).
2. *Prioritized Filter Section Ordering*:
• In `WorkoutFilterBottomSheet.kt`, filter sections SHALL follow a prioritized layout order: (1) Search Text, (2) Date Interval, (3) Sport Sub-Type Selection, (4) Categorized Equipment Selection, (5) Workout Attributes, (6) Distance Interval, (7) Duration Interval, (8) Favorite Locations (*Lieblingsorte*), (9) Favorite Tracks (*Lieblingsstrecken*). Favorite Locations and Favorite Tracks SHALL appear as the final sections at the bottom of the bottom sheet.
• In `ClusterFilterBottomSheet.kt`, filter sections SHALL follow a prioritized layout order: (1) Equipment Selection, (2) Minimum Reference Distance Thresholds, (3) Minimum Recordings / Hit Count Thresholds, (4) Known Locations (*Lieblingsorte*). Known Locations SHALL appear as the final section at the bottom of the bottom sheet.
3. *100% 9-Language Localization Parity*:
• All filter section titles and chip descriptors SHALL utilize existing localized string resources across all 9 supported application locales (EN, DE, ES, FR, IT, JA, NL, PL, PT) with 0 missing entries.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: `REQ-UI-185` (*Lieblingsorte: Drill-Down Filter to View Workouts by Starting Location*) and `REQ-UI-187` (*Selection of Lieblingsorte and Lieblingsstrecken in Workout and Cluster Filter Dialogs*).
2. *Historical Origin & Commit Trace*: Commits `6bfbcfc7` (ATT-1401) and `91acde33` (ATT-1402, ATT-1593) under Epic `ATT-1396`.
3. *Root Reason for Existing Formulation*: Emojis (📍, 🗺️) were originally added as visual indicators for spatial geofence and route cluster filters, and sections were appended as features were introduced.
4. *Preservation of Core Invariants*: Full filter functionality, spatial geofence calculations (`WorkoutClusterEngine.distanceBetween`), cluster matching, single-tap removal in active filter rows, and 9-language localization parity remain 100% preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an athlete opens `WorkoutFilterBottomSheet`,
• *When* inspecting the Favorite Locations and Favorite Tracks sections,
• *Then* chip labels SHALL NOT display emojis (neither 📍 nor 🗺️), and both sections SHALL be situated at the bottom of the dialog below Distance and Duration intervals.
• *Given* an athlete opens `ClusterFilterBottomSheet`,
• *When* inspecting the Known Locations section,
• *Then* chip labels SHALL NOT display emoji 📍, and the section SHALL be situated at the bottom of the dialog below Equipment, Distance Thresholds, and Hit Count Thresholds.
• *Given* an active location or cluster filter applied to workouts or clusters,
• *When* viewing `ActiveFilterChipsRow` or `ActiveClusterFilterChipsRow`,
• *Then* active chip labels SHALL NOT display emojis (neither 📍 nor 🗺️).

**Invariants**: Filter criteria evaluation, DataStore serialization, and 9-language localization parity MUST NOT be broken.

### REQ-UI-195: Lieblingsorte: Dedicated Row and Compact Sizing for Starts and Strecken Badges on KnownLocationCard [Verified]
- **Tests**: `TST-UI-149`, `TST-UI-171`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `KnownLocationsScreen.kt`
- **Rationale**: The system SHALL structure the layout of `KnownLocationCard` to isolate the altitude metric and position interactive badges on a dedicated row with refined compact dimensions (ATT-1643):
1. *Vertical Layout Progression (`KnownLocationCard` in `KnownLocationsScreen.kt`)*:
• The card's root content column SHALL present elements in a strict top-to-bottom sequence:
- (1) Location name header (`Text`, `titleMedium`, `FontWeight.Bold`),
- (2) Dedicated altitude metric row (`ic_ascent` icon, formatted elevation text),
- (3) Dedicated interactive badges row (housing Starts-Badge and, when available, Strecken-Badge).
• Decoupling: The altitude metric SHALL NOT share a row or `FlowRow` with interactive badges.
2. *Refined Compact Badge Styling*:
• Both Starts-Badge and Strecken-Badge (`Surface`) SHALL feature compact proportions:
- Corner shape: `RoundedCornerShape(8.dp)`,
- Internal content padding: `horizontal = 8.dp, vertical = 4.dp`,
- Typography: `MaterialTheme.typography.labelLarge` with `FontWeight.Bold`,
- Leading icons (`Place`, `ic_favorite_route`): scaled to `14.dp`,
- Trailing chevron (`Icons.AutoMirrored.Filled.ArrowForward`): scaled to `12.dp`,
- Minimum visual height constrained to ~28–32dp by omitting explicit 48dp container bloating while preserving accessible tap semantics.
3. *Preservation of Test Tags & Navigation*:
• `location_starts_badge_${item.id}` and `location_routes_badge_${item.id}` SHALL be preserved on the respective `Surface` components.
• `onShowWorkouts` and `onShowRoutes` click handlers SHALL remain fully wired and accessible.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: `REQ-UI-185` (*Lieblingsorte: Drill-Down Filter to View Workouts by Starting Location*) and `REQ-UI-188` (*Lieblingsorte: Compact Interactive Routes Badge on KnownLocationCard*).
2. *Historical Origin & Commit Trace*: Commits `6bfbcfc7` (ATT-1401) and `7868078b` (ATT-1594).
3. *Root Reason for Existing Formulation*: `REQ-UI-188` grouped altitude, starts, and routes in an adaptive `FlowRow` with `defaultMinSize(minHeight = 48.dp)` to save space, but high start counts caused erratic wrapping.
4. *Preservation of Core Invariants*: Full drill-down navigation to workouts and routes remains intact; universal delete context menu (`REQ-UI-061`) remains intact; 9-language localization parity strictly preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an athlete viewing favorite locations in `KnownLocationsScreen`,
• *When* inspecting any `KnownLocationCard`,
• *Then* the altitude metric SHALL render on its own line below the location name, and interactive badges SHALL render on a separate dedicated line below the altitude metric.
• *Given* a location card with linked routes,
• *When* viewing the Starts-Badge and Strecken-Badge,
• *Then* both badges SHALL display compact styling with `RoundedCornerShape(8.dp)`, 14dp leading icons, and 12dp trailing chevrons, without stretching to 48dp visual height.
• *Given* an athlete tapping the compact Starts-Badge or Strecken-Badge,
• *Then* the app SHALL navigate to the filtered workouts or routes list respectively.

**Invariants**: Card single-tap edit, long-press delete context menu (`REQ-UI-061`), navigation callbacks, and 9-language localization parity MUST NOT be broken.

### REQ-UI-196: LiveSegment & Bottom Sheets: Refined Subtle Drag Handle, Harmonized Popup Spacing & Unified Surface Background [Verified]
- **Tests**: `TST-UI-150`, `TST-UI-167`, `TST-UI-172`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `BottomSheetDesign.kt`, `MinimumDragHandle.kt`, `MapDetailLayout.kt`, `LIveSegmentSheet.kt`, `SensorGridScreen.kt`
- **Rationale**: The system SHALL refine the visual weight, dimensions, color tokens, and container padding of `MinimumDragHandle` across the LiveSegment popup and all bottom sheet scaffolds, and unify container surface styling to eliminate contrast seams (ATT-1644, ATT-1735):
1. *Refined Design Tokens (`BottomSheetDesign.kt`)*:
• `DragHandleWidth`: Standardized at `32.dp` (reduced from `36.dp`).
• `DragHandleHeight`: Standardized at `3.dp` (reduced from `4.dp`).
2. *Subtle Drag Handle Affordance (`MinimumDragHandle.kt`)*:
• The drag handle pill container color SHALL use `MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)` in place of heavy `onSurfaceVariant(0.5f)`, providing a gentle, muted grey that harmonizes with both Light and Dark themes.
• Container padding SHALL be standardized to `top = 8.dp, bottom = 4.dp` (reduced from `12.dp` / `6.dp`).
• `MinimumDragHandle` SHALL support configurable parameters with defaults: `width`, `height`, `color`, `topPadding`, `bottomPadding`.
3. *Harmonized Spacing in LiveSegment Popup*:
• Vertical spacing from the sheet's top rounded contour to the pill SHALL be `8.dp`.
• Combined vertical distance from the pill to the segment title row (`SegmentHeader`) SHALL be `8.dp` (`4.dp` handle bottom + `4.dp` header top).
4. *Unified Container Surface Background (`SensorGridScreen.kt`, `MapDetailLayout.kt`, `LIveSegmentSheet.kt`)*:
• In `SensorGridScreen.kt`, `BottomSheetScaffold` SHALL explicitly define `sheetContainerColor = MaterialTheme.colorScheme.surface`, matching inner sheet content and eliminating visual color contrast seams.
• The sheet container `Box` SHALL apply `.background(MaterialTheme.colorScheme.surface, shape = BottomSheetDesign.SheetShape)`.
• In `MapDetailLayout.kt`, when operating in bottom sheet mode (`!useStatusBarsPadding`), the root `Column` SHALL apply `.background(MaterialTheme.colorScheme.surface)`, ensuring `MinimumDragHandle`, the slotted header, and the elevation profile share the identical unified surface.
• In `LIveSegmentSheet.kt`, the header and metrics column SHALL seamlessly share `MaterialTheme.colorScheme.surface`, eliminating stark white patches against darker sheet containers.
5. *Preservation of Invariants*:
• Edge-to-edge window insets (`REQ-UI-148`) and top contour styling (`REQ-UI-189`) remain 100% intact; touch responsiveness and drag interactions are preserved.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: `REQ-UI-189` (*UI/Sheets: Standardized Boundaries, Contours, Drop Shadow Elevation & Drag Affordances*), targeting `BottomSheetDesign.kt`, `MinimumDragHandle.kt`, `MapDetailLayout.kt`, `LIveSegmentSheet.kt`, and `SensorGridScreen.kt`.
2. *Historical Origin & Commit Trace*: Commit `69c84e1b` (ATT-1588) and commit `e7ae455c` (ATT-1644).
3. *Root Reason for Existing Formulation*: `REQ-UI-189` introduced a prominent 36dp x 4dp handle with `onSurfaceVariant(0.5f)` to ensure discoverability. `ATT-1644` refined the drag handle dimensions (32dp x 3dp) and harmonized vertical spacing, but left the container color uncoordinated between `BottomSheetScaffold` (defaulting to `surfaceContainerLow`) and nested `Surface` components (using `surface`), resulting in a stark white header inside a darker container.
4. *Preservation of Core Invariants*: Full touch target accessibility, edge-to-edge window insets (`REQ-UI-148`), sheet contour border (`REQ-UI-189`), and full-screen detail view styling remain intact.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an active live segment popup in `SensorGridScreen` or any bottom sheet using `MinimumDragHandle`,
• *When* displayed in Light or Dark mode,
• *Then* the drag handle pill SHALL render with refined dimensions ($32\text{dp} \times 3\text{dp}$), subtle `outlineVariant.copy(alpha = 0.6f)` color, and balanced padding ($8\text{dp}$ top, $4\text{dp}$ bottom) without commanding visual dominance over segment metrics.
• *Given* `BottomSheetDesign.DragHandleWidth` and `BottomSheetDesign.DragHandleHeight`,
• *When* inspected, `DragHandleWidth` SHALL equal `32.dp` and `DragHandleHeight` SHALL equal `3.dp`.
• *Given* an active live segment popup displayed in `SensorGridScreen` (`LIveSegmentSheet`),
• *When* rendered in Light, Dark, or AMOLED theme,
• *Then* the bottom sheet scaffold container, drag handle zone, segment header, live details, and elevation profile SHALL share a single unified `MaterialTheme.colorScheme.surface` background without stark contrasting color patches or visible background seams.

**Invariants**: Sheet expansion physics, edge-to-edge system insets (`REQ-UI-148`), and touch interaction MUST NOT be regressed.

### REQ-UI-197: Elevation Profile Contextual Zoom Controls Visibility & Non-Overlapping Multi-Metric Scrubbing Badge Architecture [Verified]
- **Tests**: `TST-UI-151`, `TST-UI-167`, `TST-UI-179`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `ElevationProfile.kt`, `MapDetailLayout.kt`, `LIveSegmentSheet.kt`
- **Rationale**: The system SHALL restrict interactive zoom and pan controls on the elevation profile chart strictly to detailed inspection views, suppress them in compact list item previews, and enforce a non-overlapping vertical layout between the overlay control buttons and the multi-metric telemetry badge (ATT-1647, ATT-1736, ATT-1737):
1. *Contextual Zoom Controls Parameterization (`ElevationProfile.kt`, `MapDetailLayout.kt`, `LIveSegmentSheet.kt`)*:
• `ElevationProfile` composable overloads SHALL accept `showZoomControls: Boolean = false`.
• When `showZoomControls == false`: (a) The zoom controls row (`+`, `-`, Pan/Scrub mode toggle, and zoom scale reset badge) and the legend info icon button SHALL NOT be rendered; (b) The `Canvas` gesture handler `Modifier.pointerInput(...)` SHALL NOT be attached, enabling smooth list scrolling and instantaneous parent card click handling (`clickable { onMapClick() }`); (c) Top padding of the chart canvas SHALL be reduced to compact `16.dp` without visual overhead.
• When `showZoomControls == true`: Full interactive zooming, panning, reset badge, and legend info button are rendered. Detailed inspection layouts (`MapDetailLayout.kt`) SHALL accept `showZoomControls: Boolean = true` defaulting to `true`, forwarding the parameter directly to `ElevationProfile`. Full-screen detail inspection screens (`TrackOnMapScreen`, `RouteOnMapScreen`, `SegmentOnMapScreen`, `WorkoutClusterHeatmapScreen`) SHALL rely on the default `true`, while ambient/compact bottom sheet popups (`LIveSegmentSheet.kt`) SHALL explicitly pass `showZoomControls = false`, completely suppressing zoom controls and legend button in the LiveSegment popup during tracking cockpit view (ATT-1736).
2. *Three-Layer Non-Overlapping Vertical Layout Architecture (`ElevationProfile.kt`)*:
• In detailed inspection views where `showZoomControls == true`:
- Layer 1 (Controls & Legend): The controls row height SHALL be constrained to `24.dp` at `top = 2.dp` (occupying vertical interval `2.dp .. 26.dp`); The legend info button is placed at `Alignment.TopEnd` at `top = 2.dp`.
- Layer 2 (Multi-Metric Telemetry Badge): When scrubbing (`currentDistance != null`), `ScrubbingTelemetryBadge` SHALL be positioned at `top = 28.dp` (occupying vertical interval `28.dp .. 70.dp`), guaranteeing at least `2.dp` clear vertical separation below the controls row across all scrubbing positions and eliminating overlap with zoom buttons unconditionally (ATT-1737).
- Layer 3 (Canvas Chart Plotting): The `Canvas` top padding SHALL be set to `72.dp` (with total canvas height = `cachedData.adaptiveHeight + 48.dp` to strictly preserve the exact drawable plotting height: `adaptiveHeight - 48.dp`); The elevation curve begins plotting from `y = 0` (which begins at `72.dp` in Box space), guaranteeing a minimum `2.dp` clear separation below the telemetry badge across all horizontal coordinates.
• In compact previews or ambient popups where `showZoomControls == false`: Top padding remains compact at `16.dp`, total canvas height equals `cachedData.adaptiveHeight`, and single-line scrubber text baseline is anchored at `y = -4.dp.toPx()`.
3. *Preservation of Core Invariants*:
• Viewport zooming calculations, centroid scaling, pan clamping, adaptive distance ticks, and unit formatting remain 100% intact per `REQ-UI-192`. Existing call sites in list previews (`WorkoutSummary.kt`, `RouteItem.kt`, `SegmentItem.kt`) and ambient live tracking (`SensorGridScreen.kt`) automatically inherit `showZoomControls = false` cleanly.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: `REQ-UI-192` (*Interactive Elevation Profile Zoom, Pan & Centered Scaling Navigation*), targeting `ElevationProfile.kt` and `MapDetailLayout.kt`.
2. *Historical Origin & Commit Trace*: Commit `69286d4e` (Ticket `ATT-527`, Sprint `2026-40.4`), commit for `ATT-1647` (Sprint `2026-40.5`), `ATT-1736` (Sprint `2026-40.6`), and `ATT-1737` (Sprint `2026-40.6`).
3. *Root Reason for Existing Formulation*: In `ATT-1647`, `topPadding = 44.dp` separated controls from the old single-line text label. In `ATT-1391`, `ScrubbingTelemetryBadge` (~42dp tall) was introduced with hardcoded `top = 2.dp`, placing it directly over the zoom buttons.
4. *Refinement Reason (ATT-1737)*: During on-device review of Sprint `2026-40.5`, the user demonstrated via screenshot that scrubbing on the left side caused `ScrubbingTelemetryBadge` to collide with and occlude the zoom controls. Adjusting the vertical geometry to 3 disjoint layers (Controls at 2..26dp, Badge at 28..70dp, Canvas at 72dp) completely resolves the visual collision.
5. *Preservation of Core Invariants*: Viewport mathematics (`ElevationProfileZoomMath`), synchronized coordinate scrubbing, exact chart plotting height (`cachedData.adaptiveHeight - 48.dp`), unit formatting, and full-screen detail view interactivity are fully preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an athlete viewing workout history, route list, or segment list,
• *When* inspecting the elevation profile preview,
• *Then* zero zoom controls (`+`, `-`, Pan mode, reset badge) or legend buttons SHALL be displayed, and tapping the profile opens the detailed map view.
• *Given* an athlete viewing a workout, route, or segment detail sheet in `MapDetailLayout`,
• *When* inspecting the elevation profile,
• *Then* zoom controls and the legend button SHALL be visible and fully interactive.
• *Given* an athlete scrubbing on the left edge or any position of the elevation profile in `MapDetailLayout`,
• *When* `ScrubbingTelemetryBadge` is displayed,
• *Then* `ScrubbingTelemetryBadge` SHALL render completely below the zoom control buttons (`top = 28.dp`) with a minimum 2dp vertical clearance, ensuring the `+`, `-`, Pan mode toggle, and zoom reset badge remain 100% visible and accessible without any visual collision.
• *Given* an athlete viewing an active live segment popup during tracking in `SensorGridScreen` (`LIveSegmentSheet.kt`),
• *When* the popup renders the elevation profile,
• *Then* zoom controls (`+`, `-`, Pan mode, reset badge) and legend info button SHALL be completely suppressed (`showZoomControls = false`), preserving an uncluttered cockpit view.

**Invariants**: Elevation profile zoom math, scrubbing accuracy, unit conversion, full-screen inspection interactivity, and 9-language localization parity MUST NOT be broken.

### REQ-UI-198: Cockpit Sensor Field Domain-Specific Default Filter Presets Architecture [Verified]
- **Tests**: `TST-UI-152`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `SensorFilterDefaults.kt`, `EditSensorFieldViewModel.kt`, `TrackingViewsDatabaseManager.java`
- **Rationale**: The system SHALL provide domain-optimized default filter presets for sensor metrics upon field creation, sensor type switching, and initial tracking view database seeding, eliminating raw telemetry jitter while preserving accumulator integrity (ATT-1624, ATT-3310):
1. *Centralized Sensor Filter Defaults Registry (`SensorFilterDefaults.kt`)*:
• The system SHALL define `DefaultFilterConfig(val filterType: FilterType, val filterConstant: Double, val unit: String = "sec")`.
• `SensorFilterDefaults.getDefaultFilterConfig(sensorType: SensorType?)` SHALL evaluate:
- `SensorType.POWER`: `FilterType.MOVING_AVERAGE_TIME`, constant `3.0`, unit `"sec"`.
- `SensorType.SPEED_mps`: `FilterType.MOVING_AVERAGE_TIME`, constant `2.0`, unit `"sec"` (ATT-3310).
- `SensorType.CADENCE`: `FilterType.MOVING_AVERAGE_TIME`, constant `2.0`, unit `"sec"` (ATT-3310).
- `SensorType.PACE_spm`: `FilterType.MOVING_AVERAGE_TIME`, constant `5.0`, unit `"sec"`.
- `SensorType.VERTICAL_SPEED`: `FilterType.MOVING_AVERAGE_TIME`, constant `15.0`, unit `"sec"`.
- `SensorType.SLOPE`: `FilterType.MOVING_AVERAGE_TIME`, constant `5.0`, unit `"sec"`.
- All accumulator and discrete counter metrics (`DISTANCE_m`, `TIME_ACTIVE`, `CALORIES`, `LAP_NR`, `DISTANCE_m_LAP`, `TIME_LAP`, `TIME_TOTAL`, `PHONE_BATTERY`, `BATTERY_REMAINING_TIME`): strictly `FilterType.INSTANTANEOUS`, constant `1.0`.
- Other continuous metrics (`HR`, `ALTITUDE`, `TEMPERATURE`): strictly `FilterType.INSTANTANEOUS`, constant `1.0`.
- Null or unknown sensor types: `FilterType.INSTANTANEOUS`, constant `1.0`.
• `SensorFilterDefaults` SHALL expose `@JvmStatic` static accessor methods (`getDefaultFilterConfig`, `getDefaultFilterType`, `getDefaultFilterConstant`, `getDefaultUnit`) and a Kotlin extension method `SensorType.getDefaultFilterConfig()` for seamless cross-language interoperability.
2. *Sensor Field Editor Dynamic State Synchronization (`EditSensorFieldViewModel.kt`)*:
• In `EditSensorFieldViewModel.init`, fallback field initialization SHALL resolve `SensorFilterDefaults.getDefaultFilterConfig(defaultSensor)`.
• In `onSensorTypeChanged(newSensorType)`, the system SHALL update `selectedFilterType`, `filterConstant`, `movingAverageUnit`, and `filterSummary` dynamically using `newSensorType.getDefaultFilterConfig()`, and synchronize `isCustomFilterExpanded = (resolveFilterPreset(...) == FilterPreset.CUSTOM)`.
• In `onFilterConfigDismissed()`, when reverting unsaved modifications for a newly selected sensor type, the system SHALL restore the domain-specific defaults from `SensorFilterDefaults`.
3. *Default Cockpit View Database Seeding (`TrackingViewsDatabaseManager.java`)*:
• When generating default tracking view rows in `TrackingViewsDatabaseManager.addDefaultTab(...)`, the system SHALL query `SensorFilterDefaults.getDefaultFilterConfig(rowData.sensorType)` to assign `FILTER_TYPE` and `FILTER_CONSTANT`, ensuring pre-configured tabs (e.g. Speed and Cadence fields) inherit 2.0s smoothed presets upon clean database creation.
4. *User Sovereignty & Customization Invariants*:
• Existing saved views in `TrackingViewsDatabaseManager.ROWS_TABLE` SHALL remain completely immutable across upgrades (zero automated migration).
• The user's ability to manually override, customize, or disable filters in `ConfigureFilterDialog` across all supported `FilterType` options SHALL NOT be restricted.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: `REQ-UI-167` (*Intuitive Smoothing Presets, Simplified Filter Configuration & Smart Athletic Defaults*), targeting `EditSensorFieldViewModel.kt` and `TrackingViewsDatabaseManager.java`.
2. *Historical Origin & Commit Trace*: Commit `2b3d819e` (Ticket `ATT-1276`, Sprint `2026-40.4`); amended in `ATT-1624` (Sprint `2026-40.10`); amended in `ATT-3310` (Sprint `2026-42.1`).
3. *Root Reason for Existing Formulation*: `ATT-1276` added a hardcoded 3s moving average default exclusively for `SensorType.POWER` to fix volatile pedal stroke dead-spots, and `ATT-1624` added presets for Pace, VAM, and Slope. Speed and cadence were left raw, leading to display chatter under 1Hz GPS quantization noise and crank rotation timing. Configuring a 2-second moving average default stabilizes readouts without introducing perceptible lag.
4. *Preservation of Core Invariants*: Full customization freedom in `ConfigureFilterDialog`, immutability of existing saved views, BANALService filtering mathematics, and `HR`/accumulator `INSTANTANEOUS` protection remain 100% intact.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an athlete editing or adding a sensor field in `EditSensorFieldDialog`,
• *When* selecting `SensorType.SPEED_mps` in the sensor dropdown,
• *Then* `selectedFilterType` SHALL automatically initialize to `FilterType.MOVING_AVERAGE_TIME` with `filterConstant = 2.0` (2s moving average).
• *Given* an athlete selecting `SensorType.CADENCE`,
• *When* selected,
• *Then* `selectedFilterType` SHALL automatically initialize to `FilterType.MOVING_AVERAGE_TIME` with `filterConstant = 2.0`.
• *Given* an athlete selecting `SensorType.PACE_spm` in the sensor dropdown,
• *Then* `selectedFilterType` SHALL automatically initialize to `FilterType.MOVING_AVERAGE_TIME` with `filterConstant = 5.0` (5s moving average).
• *Given* an athlete selecting `SensorType.VERTICAL_SPEED` (VAM),
• *When* the sensor is selected,
• *Then* `selectedFilterType` SHALL automatically initialize to `FilterType.MOVING_AVERAGE_TIME` with `filterConstant = 15.0`.
• *Given* an athlete selecting `SensorType.SLOPE`,
• *When* selected,
• *Then* `selectedFilterType` SHALL initialize to `FilterType.MOVING_AVERAGE_TIME` with `filterConstant = 5.0`.
• *Given* an athlete selecting `SensorType.POWER`,
• *When* selected,
• *Then* `selectedFilterType` SHALL initialize to `FilterType.MOVING_AVERAGE_TIME` with `filterConstant = 3.0`.
• *Given* an athlete selecting an accumulator sensor (`DISTANCE_m`, `TIME_ACTIVE`, `CALORIES`, `LAP_NR`) or `SensorType.HR`,
• *When* selected,
• *Then* `selectedFilterType` SHALL strictly initialize to `FilterType.INSTANTANEOUS` with `filterConstant = 1.0`.
• *Given* default tracking views created in `TrackingViewsDatabaseManager`,
• *When* views are seeded during clean installation,
• *Then* `SPEED_mps` and `CADENCE` rows SHALL be persisted with `FILTER_TYPE = MOVING_AVERAGE_TIME` and `FILTER_CONSTANT = 2.0`.

**Invariants**: User customization freedom in `ConfigureFilterDialog`, immutability of existing user views, and zero database schema migration MUST NOT be broken.

### REQ-UI-199: Lieblingsorte: Cockpit Calibration Badge Synchronized Altimeter Dispatch & Standby Architecture [Verified]
- **Tests**: `TST-UI-153`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `AltitudeFromPressureDevice.java`, `DeviceManager.java`, `BANALService.java`, `BANALServiceRepository.kt`, `TrackingTabsViewModel.kt`, `LocationCalibrationBadge.kt`
- **Rationale**: The system SHALL synchronize the cockpit location calibration badge (`LocationCalibrationBadge.kt`) with the actual calibration state of the barometric altimeter (`AltitudeFromPressureDevice.java`), dispatching reference elevation on geofence entry and distinguishing uncalibrated standby from active calibration (ATT-1617):
1. *Direct Altimeter Calibration Dispatch & Pending Application*:
• `AltitudeFromPressureDevice` SHALL provide a synchronized method `calibrate(referenceAltitude: Double): Boolean`.
• When called:
- If raw pressure altitude `mLastRawAltitude` is available (`!Double.isNaN(mLastRawAltitude)`): `mAltitudeCorrection` SHALL be set to `referenceAltitude - mLastRawAltitude` (or current altitude), `mPressureSensorInitialized` set to `true`, `mIsCalibrated` set to `true`, `mPendingReferenceAltitude` reset to `Double.NaN`, `mAltitudeSensor.newValue(...)` published immediately, and `ALTITUDE_CORRECTION_INTENT` broadcast.
- If raw pressure is not yet available (`Double.isNaN(mLastRawAltitude)` during sensor warmup): `mPendingReferenceAltitude` SHALL cache `referenceAltitude`. As soon as the first pressure measurement arrives in `handlePressureMeasurement(pressureHpa)`, the pending calibration SHALL be executed automatically.
• `DeviceManager`, `BANALService` (via `BANALServiceComm`), and `BANALServiceRepository` SHALL expose `calibrateAltimeter(referenceAltitude: Double): Boolean` and `isAltimeterCalibrated: StateFlow<Boolean>`.
2. *Closed-Loop State Verification in Tracking Cockpit*:
• In `TrackingTabsViewModel.kt`, whenever `currentLocation` matches an existing known location geofence (`closestItem != null`), the ViewModel SHALL trigger `banalServiceRepository.calibrateAltimeter(closestItem.altitude)`.
• `LocationCalibrationStatus` SHALL evaluate `isCalibrated` directly from `banalServiceRepository.isAltimeterCalibrated`, eliminating false claims of completed calibration.
3. *Two-State Visual Feedback (Standby vs. Calibrated)*:
• In `LocationCalibrationBadge.kt`:
- When `status.isCalibrated == true`: The badge SHALL render `R.string.location_calibrated_format`: `"%1$s (%2$s calibrated)"` / `"%1$s (%2$s kalibriert)"`.
- When `status.isCalibrated == false`: The badge SHALL render `R.string.location_detected_format`: `"%1$s detected"` / `"%1$s erkannt"` (standby mode), preventing false claims of completed calibration.
4. *100% 9-Language Localization Parity*:
• The string resource `location_detected_format` SHALL be defined across all 9 application locales: English (values), German (values-de), Spanish (values-es), French (values-fr), Italian (values-it), Japanese (values-ja), Dutch (values-nl), Polish (values-pl), Portuguese (values-pt).
5. *Preservation of Core Invariants*:
• `LocationCalibrationBadge` placement on `ControlTrackingScreen.kt`, animated transitions, metric/imperial unit formatting, single-thread SQLite concurrency in `KnownLocationsDatabaseManager`, and `ALTITUDE_CORRECTION_INTENT` broadcast contracts MUST remain strictly intact.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: `REQ-UI-183` (*Lieblingsorte: Live Cockpit Location & Barometer Calibration Feedback*), targeting `ControlTrackingScreen.kt`, `TrackingTabsViewModel.kt`, `LocationCalibrationBadge.kt`, and `AltitudeFromPressureDevice.java`.
2. *Historical Origin & Commit Trace*: Ticket `ATT-1399` (`feat(cockpit): add live favorite location feedback badge and calibration status (ATT-1399, ATT-1572)`).
3. *Root Reason for Existing Formulation*: Originally, `LocationCalibrationStatus` assumed that matching a geofence implied calibration was active, hardcoding `isCalibrated = true` without verifying or dispatching actual calibration to `AltitudeFromPressureDevice`.
4. *Refinement Reason*: Synchronize the UI badge with the actual barometric sensor state; dispatch calibration to `AltitudeFromPressureDevice` on geofence match; indicate standby/detection (e.g. "Zu Hause erkannt") during sensor warmup or uncalibrated state, and only claim "kalibriert" once `AltitudeFromPressureDevice` has actively applied the ground-truth offset.
5. *Preservation of Core Invariants*: Preservation of `LocationCalibrationBadge` placement on `ControlTrackingScreen.kt`, 9-language localization, imperial/metric conversions, smooth `AnimatedVisibility`, single-thread SQLite confinement, and `ALTITUDE_CORRECTION_INTENT` broadcast semantics.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an athlete opens the app at a known favorite location ("Zu Hause", 436 m) while the pressure sensor is warming up or uncalibrated (`isAltimeterCalibrated == false`),
• *When* viewing the Control Tracking screen,
• *Then* `LocationCalibrationBadge` SHALL display `Zu Hause erkannt` (standby mode) and SHALL NOT claim that calibration has completed.
• *Given* the athlete at a known favorite location ("Zu Hause", 436 m) and the barometric altimeter applies the reference altitude offset,
• *When* the sensor is calibrated (`isAltimeterCalibrated == true`),
• *Then* `LocationCalibrationBadge` SHALL display `Zu Hause (436 m kalibriert)` and the barometric altitude sensor (`Luftdruck`) in telemetry dialogs SHALL display `436 m` (± sensor noise).
• *Given* the athlete moves away from the favorite location beyond its geofence radius,
• *When* `currentLocation` updates,
• *Then* `locationCalibrationStatus` SHALL emit `null` and `LocationCalibrationBadge` SHALL animate out smoothly while the altimeter retains its calibrated barometric baseline.

**Invariants**: Background calibration calculations, SQLite schema V5, start count recording, and single-thread SQLite confinement MUST NOT be broken.

### REQ-UI-200: Cockpit Sensor Grid Pick & Place Reordering & Swapping Architecture [Verified]
- **Tests**: `TST-UI-154`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `TrackingViewsDatabaseManager.java`, `TrackingViewsRepository.kt`, `TrackingViewModel.kt`, `SensorGridScreen.kt`, `SensorFieldView.kt`, `strings.xml` (all 9 locales)
- **Rationale**: The system SHALL provide an accessible, tactile \"Pick & Place\" (Tap-to-Move / Tap-to-Swap) reordering mode within cockpit layout configuration (`ScreenMode.CONFIGURATION`), enabling athletes to freely reorganize sensor tiles without continuous drag gesture conflicts (ATT-1629):
1. *Selection State (Pick)*:
• In `ScreenMode.CONFIGURATION`, long-pressing any sensor tile or tapping its dedicated reorder button SHALL select the tile and transition the cockpit into Move Mode (`selectedFieldForMove != null`).
• The selected tile SHALL display prominent visual feedback (`BorderStroke(2.dp, MaterialTheme.colorScheme.primary)`) and trigger tactile haptic confirmation (`HapticFeedbackType.LongPress`).
2. *Floating Guidance Banner*:
• While a tile is selected, a floating guidance banner SHALL be displayed at the top of the grid containing instruction text `R.string.move_tile_banner_instruction` and a cancel button `R.string.move_tile_cancel`.
3. *Tile Swapping (Place)*:
• Tapping another existing sensor tile while in Move Mode SHALL atomically exchange (`swap`) the two tiles' `(rowNr, colNr)` coordinates in the database (`TrackingViewsDatabaseManager.swapSensorFields`), update the UI, and exit Move Mode.
4. *Move to Adder Slot & Layout Normalization*:
• Tapping an existing `RowAdder` or `ColAdder` (`+` button) while in Move Mode SHALL reposition the selected tile to that new slot (`TrackingViewsDatabaseManager.moveSensorField`), compact the origin row, eliminate empty rows, normalize grid coordinates, and exit Move Mode without opening the new-sensor creation dialog.
5. *Cancellation*:
• Tapping the selected tile a second time, tapping the banner cancel button, pressing Back, or exiting configuration mode SHALL cleanly exit Move Mode with zero layout changes.
6. *100% 9-Language Localization Parity*:
• String resources `move_tile_banner_instruction` and `move_tile_cancel` SHALL be defined across all 9 application locales (EN, DE, ES, FR, IT, JA, NL, PL, PT).

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement only (REQ-UI-200), extending and complementing `REQ-UI-103` (*Customizable Cockpits*).
2. *Historical Origin & Commit Trace*: Ticket `ATT-1629`, Sprint `2026-40.5`.
3. *Root Reason for Existing Formulation*: Previously, reordering required deleting and re-creating fields from scratch due to pointer-capture lockouts when attempting continuous drag-and-drop within a scrollable column.
4. *Preservation of Core Invariants*: Full touch target accessibility, standard tracking mode interaction (`ScreenMode.TRACKING`), SQLite single-thread confinement, and zero schema migration are preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an athlete viewing the cockpit in configuration mode (`ScreenMode.CONFIGURATION`),
• *When* long-pressing a sensor tile or tapping its move icon,
• *Then* the tile SHALL highlight with a primary accent border, and the guidance banner SHALL appear at the top.
• *Given* an active Move Mode with tile A selected,
• *When* tapping another sensor tile B,
• *Then* tile A and tile B SHALL cleanly swap positions in the grid layout and SQLite database, and Move Mode SHALL exit.
• *Given* an active Move Mode with tile A selected,
• *When* tapping a `RowAdder` or `ColAdder`,
• *Then* tile A SHALL move to the designated row/column slot, the origin row SHALL compact, and Move Mode SHALL exit.
• *Given* an active Move Mode,
• *When* tapping the cancel button or tapping the selected tile again,
• *Then* Move Mode SHALL exit without modifying any tile positions.

**Invariants**: Normal active tracking mode (`ScreenMode.TRACKING`), sensor telemetry, SQLite database schema, and single-thread SQLite confinement MUST NOT be broken.

### REQ-UI-201: Aftermath: Synchronized Multi-Metric Scrubbing on Elevation Profile with Configurable X-Axis Domain Architecture [Verified]
- **Tests**: `TST-UI-155`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `ElevationProfile.kt`, `MapDetailLayout.kt`, `MapModels.kt`, `WorkoutRepository.kt`, `TuningPreferencesDataStore.kt`, `AdvancedTuningDialog.kt`
- **Rationale**: The system SHALL enhance the Aftermath inspection layout and elevation profile with synchronized multi-metric scrubbing, telemetry-enriched trackpoints, and a configurable X-axis domain (ATT-1391):
1. *Enriched PathPoint Telemetry Model (`MapModels.kt`)*:
• `PathPoint` SHALL encapsulate distance, latLng, altitude, and optional telemetry fields: `val timeSec: Long = 0L`, `val hr: Int? = null`, `val power: Int? = null`, `val speedMps: Double? = null`, `val slope: Double? = null`. Default values SHALL ensure 100% backward compatibility.
2. *Full-Fidelity Telemetry Extraction (`WorkoutRepository.kt`)*:
• `WorkoutRepository.getWorkoutTrackPoints(workoutId, trackType)` SHALL query the workout samples database table and resolve column indices for `TIME_ACTIVE`, `TIME_TOTAL`, `HR`, `POWER`, `SPEED_mps`, and `SLOPE`. Extracted sensor values SHALL populate each `PathPoint`, maintaining thread-safety on `Dispatchers.IO`.
3. *Configurable Profile X-Axis Domain Preference (`TuningPreferencesDataStore.kt` & `AdvancedTuningDialog.kt`)*:
• The system SHALL define `enum class ProfileXAxisDomain { DISTANCE, TIME }`. `TuningConfig` and `TuningPreferencesDataStore` SHALL manage `profileXAxisDomain` with default `ProfileXAxisDomain.DISTANCE`.
• `AdvancedTuningDialog.kt` SHALL expose an Aftermath Tuning section allowing athletes to switch between *Distance* and *Elapsed Time*. Factory reset SHALL restore the domain preference to `DISTANCE`.
4. *Adaptive Time Ticks & Viewport Scaling (`ElevationProfileZoomMath.kt`)*:
• When `xAxisDomain == ProfileXAxisDomain.TIME`, horizontal layout and gestures scale along the time domain $0 .. T_{\text{total}}$ seconds.
• `ElevationProfileZoomMath.calculateAdaptiveTimeStep(visibleTimeSec: Double): Long` SHALL calculate readable tick intervals (e.g. 30s, 60s, 120s, 300s, 600s, 1800s, 3600s) formatted as `m:ss` or `h:mm:ss`.
5. *Synchronized Multi-Metric Scrubbing & Floating Overlay Badge (`ElevationProfile.kt`)*:
• While scrubbing, `ElevationProfile` SHALL locate the nearest `PathPoint` along the active horizontal axis and invoke `onPointSelected: ((PathPoint?) -> Unit)?`.
• `ElevationProfile` SHALL render a floating Compose telemetry card (`ScrubbingTelemetryBadge`): Distance & Elapsed Time; Altitude & Slope; Heart Rate (`bpm`); Power (`W`); Speed/Pace formatted per sport type. Missing metrics SHALL be cleanly omitted.
6. *100% 9-Language Localization Parity*:
• String resources `tuning_cat_aftermath`, `tuning_profile_x_axis_title`, `tuning_profile_x_axis_desc`, `tuning_profile_x_axis_distance`, and `tuning_profile_x_axis_time` SHALL be defined across all 9 application locales.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement only (REQ-UI-201), extending and complementing `REQ-UI-192` (*Interactive Elevation Profile Zoom, Pan & Centered Scaling Navigation*) and `REQ-UI-197` (*Elevation Profile Contextual Zoom Controls Visibility & Non-Overlapping Scrubber Text Architecture*).
2. *Historical Origin & Commit Trace*: Ticket `ATT-1391`, Sprint `2026-40.5`.
3. *Root Reason for Existing Formulation*: `ElevationProfile` previously scrubbed strictly by distance and rendered a minimal text string without multi-sensor telemetry correlation or time-based plotting.
4. *Preservation of Core Invariants*: Full zoom/pan mathematics, vertical bounds sanitization, existing `onDistanceSelected: (Double?) -> Unit` contract, preview card zoom suppression, and SQLite single-thread confinement remain 100% intact.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an athlete configures the Profile X-Axis setting to Elapsed Time in `AdvancedTuningDialog`,
• *When* inspecting the workout elevation profile in Aftermath,
• *Then* the chart and scrubber SHALL render along elapsed time with adaptive `m:ss` / `h:mm:ss` ticks.
• *Given* a workout recorded with Heart Rate, Power, and Speed sensors,
• *When* scrubbing along the elevation profile in `MapDetailLayout`,
• *Then* the floating telemetry badge SHALL display instantaneous Distance, Time, Altitude, Slope, HR (`bpm`), Power (`W`), and Speed/Pace synchronized to the scrubbed trackpoint.
• *Given* a workout without Power meter telemetry (`power == null`),
• *When* scrubbing along the elevation profile,
• *Then* the Power metric SHALL be omitted cleanly from the badge without displaying null or placeholder text.

**Invariants**: Existing `onDistanceSelected` caller contracts, `calculateElevationBounds` vertical sanitization, list preview zoom suppression, and single-thread SQLite confinement MUST NOT be broken.

### REQ-UI-202: Aftermath: Heart Rate 5-Zone Distribution (Time-in-Zones) Vertical Column Chart Architecture [Verified]
- **Tests**: `TST-UI-156`, `TST-UI-185`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `ZoneDistributionModels.kt`, `ZoneDistributionCalculator.kt`, `HeartRateZoneDistributionCard.kt`, `ZoneDistributionColumnChart.kt`, `WorkoutRepository.kt`, `TrackOnMapAftermathViewModel.kt`, `MapDetailLayout.kt`, `TrackOnMapScreen.kt`
- **Rationale**: The system SHALL provide a compact, visually intuitive 5-zone Heart Rate distribution vertical column chart (histogram) within the workout details / aftermath inspection view (`TrackOnMapScreen.kt` / `MapDetailLayout.kt`) (ATT-1389, ATT-1739, ATT-1811):
1. *Domain Model (`ZoneDistributionModels.kt`)*:
• The system SHALL define `ZoneTimeEntry(val zoneIndex: Int, val zoneLabelResId: Int, val durationSec: Long, val percentage: Float, val color: Color)`.
• The system SHALL define `ZoneDistributionData(val totalActiveTimeSec: Long, val entries: List<ZoneTimeEntry>)` where `entries` contains exactly 5 elements representing Zones 1 through 5.
• The system SHALL define `HeartRateZoneThresholds(val z1Max: Int, val z2Max: Int, val z3Max: Int, val z4Max: Int)`.
2. *Pure Mathematical Engine (`ZoneDistributionCalculator.kt`)*:
• `ZoneDistributionCalculator.calculateHeartRateDistribution(samples, thresholds)` SHALL assign each valid HR sample to Zones 1..5: $\text{HR} \le Z1_{\max} \implies \text{Z1}$, $Z1_{\max} < \text{HR} \le Z2_{\max} \implies \text{Z2}$, $Z2_{\max} < \text{HR} \le Z3_{\max} \implies \text{Z3}$, $Z3_{\max} < \text{HR} \le Z4_{\max} \implies \text{Z4}$, $\text{HR} > Z4_{\max} \implies \text{Z5}$.
• Active duration spent in each zone SHALL be accumulated from consecutive sample timestamps $\Delta t = (t_{i+1} - t_i)$ clamped to $[0\text{s}, 5\text{s}]$ to eliminate runaway accumulation during pauses.
• If valid samples have identical or non-advancing timestamps (degenerate timestamps where `first == last`), the calculator SHALL defensively fall back to unit duration ($1\text{s}$ per sample) rather than collapsing to a $1\text{s}$ terminal duration.
• If total active HR duration is 0 or no valid HR samples exist, the calculator SHALL return `null`.
3. *Full-Fidelity SQLite Telemetry Extraction (`WorkoutRepository.kt`)*:
• `WorkoutRepository.getHeartRateZoneDistribution(workoutId, bSportType)` SHALL execute on `Dispatchers.IO`.
• The repository SHALL query athlete HR zone thresholds from `SettingsDataStore`: if `bSportType == BSportType.BIKE` $\implies$ `ZoneType.HR_BIKE`, otherwise $\implies$ `ZoneType.HR_RUN`.
• The repository SHALL query the workout samples table for `TIME_ACTIVE`, `TIME_TOTAL`, `time` (`WorkoutSamplesDbHelper.TIME`), and `HR`. If `TIME_ACTIVE` and `TIME_TOTAL` are null, the repository SHALL fall back to deriving relative elapsed seconds from the `time` column ($t - t_0$), or sequential 1s intervals. If the `HR` column does not exist or the table is empty, the repository SHALL cleanly return `null`.
4. *ViewModel State Flow (`TrackOnMapAftermathViewModel.kt`)*:
• `AftermathMapUIState` SHALL include `val hrZoneDistribution: ZoneDistributionData? = null`.
• In `loadAftermathData(workoutData)`, the ViewModel SHALL asynchronously query `getHeartRateZoneDistribution` and emit to `_uiState`.
5. *Visual UI Component (`HeartRateZoneDistributionCard.kt` & `ZoneDistributionColumnChart.kt`)*:
• The system SHALL render a 5-column vertical histogram with the 5 physiological heart rate zones (Zone 1 through Zone 5) on the X-axis and duration spent in each zone on the Y-axis.
• The column height for each zone SHALL be proportional to its duration relative to the maximum zone duration: $\text{heightFraction}_i = t_i / \max_j(t_j)$ (with a subtle minimum height of 4.dp for non-zero zones for visual visibility, and 0.dp for zero duration zones).
• Each vertical column SHALL be filled with its corresponding zone color `TTColor.Zone1` through `TTColor.Zone5`, with rounded top corners.
• Above/within each column, the system SHALL display formatted duration (`m:ss` or `h:mm:ss`) and percentage (`X%`).
• Below each column, the system SHALL display the zone label (`Z1`..`Z5`).
• The card SHALL display a compact title row with a heart icon (`R.drawable.ic_heart_rate`), localized text `R.string.aftermath_hr_zones_title`, and total active time.
6. *Slotted MapDetailLayout Integration (`MapDetailLayout.kt`, `TrackOnMapScreen.kt`)*:
• `MapDetailLayout` SHALL provide an optional `analyticsContent: @Composable ColumnScope.() -> Unit = {}` slot directly integrated with the layout.
• If `hrZoneDistribution == null`, the component SHALL render nothing and consume 0 vertical space.
• For stationary / indoor workouts (`showMap == false`, `showElevationProfile == false`), `analyticsContent` SHALL remain fully visible.
7. *100% 9-Language Localization Parity*:
• `aftermath_hr_zones_title` SHALL be defined across all 9 supported locales (en, de, es, fr, it, ja, nl, pl, pt).

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: REQ-UI-202 (*Aftermath: Heart Rate 5-Zone Distribution Bar Architecture*).
2. *Historical Origin & Commit Trace*: Ticket `ATT-1389`, Sprint `2026-40.5`. Refined in ticket `ATT-1739`, Sprint `2026-40.6`, and `ATT-1811`, Sprint `2026-40.7`.
3. *Root Reason for Existing Formulation*: Originally formulated as a single horizontal stacked bar to minimize vertical card height. During Sprint Review 2026-40.5, user review rejected the stacked bar in favor of discrete vertical columns: 'The zones (Zone 1, Zone 2, .., Zone 5) must be on the x-Axis; the time in the corresponding Zone must be on the y-Axis.' Previous extraction relied strictly on `TIME_ACTIVE` which is null in historical workouts.
4. *Preservation of Core Invariants*: Domain models, calculation math, database queries on `Dispatchers.IO`, ViewModel state flow, and 9-language localization parity remain 100% intact. Timestamp fallback to `time` column and unit duration degeneracy immunity preserve modern behavior while restoring historical workout fidelity.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an outdoor or indoor workout recorded with a Heart Rate sensor,
• *When* the athlete views the workout in Aftermath (`TrackOnMapScreen`),
• *Then* the 5-zone Heart Rate distribution card SHALL render below the elevation profile / summary stats as a 5-column vertical histogram with Zones 1-5 on the X-axis and duration on the Y-axis.
• *Given* an indoor workout (`showMap == false`, `showElevationProfile == false`),
• *When* opened in Aftermath,
• *Then* the Heart Rate zone distribution card SHALL render cleanly below the stats sheet without layout clipping.
• *Given* a workout recorded without a Heart Rate monitor (`HR` column absent or null),
• *When* viewed in Aftermath,
• *Then* the Heart Rate zone distribution card SHALL be hidden cleanly with zero space consumption.
• *Given* a historical workout where `TIME_ACTIVE` and `TIME_TOTAL` are null,
• *When* viewed in Aftermath,
• *Then* heart rate zone distribution SHALL derive elapsed seconds from the `time` column and correctly populate multi-minute zone durations.

**Invariants**: SQLite single-thread confinement, zero degradation of elevation profile or GPS map rendering, and 9-language localization parity MUST NOT be broken.

### REQ-UI-203: Aftermath: Cycling Power 5-Zone Distribution (Time-in-Zones) Vertical Column Chart Architecture [Verified]
- **Tests**: `TST-UI-157`, `TST-UI-185`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `ZoneDistributionModels.kt`, `ZoneDistributionCalculator.kt`, `PowerZoneDistributionCard.kt`, `ZoneDistributionColumnChart.kt`, `WorkoutRepository.kt`, `TrackOnMapAftermathViewModel.kt`, `MapDetailLayout.kt`, `TrackOnMapScreen.kt`
- **Rationale**: The system SHALL provide a compact, visually intuitive 5-zone Cycling Power distribution vertical column chart (histogram) within the workout details / aftermath inspection view (`TrackOnMapScreen.kt` / `MapDetailLayout.kt`) for sessions recorded with power meter telemetry (ATT-1390, ATT-1739, ATT-1811):
1. *Domain Model (`ZoneDistributionModels.kt`)*:
• The system SHALL define `PowerZoneThresholds(val z1Max: Int, val z2Max: Int, val z3Max: Int, val z4Max: Int)`.
• The system SHALL reuse `ZoneTimeEntry` and `ZoneDistributionData` representing Zones 1 through 5.
2. *Pure Mathematical Engine (`ZoneDistributionCalculator.kt`)*:
• `ZoneDistributionCalculator.calculatePowerDistribution(samples, thresholds)` SHALL assign each valid power sample ($P > 0$) to Zones 1..5: $P \le Z1_{\max} \implies \text{Z1}$, $Z1_{\max} < P \le Z2_{\max} \implies \text{Z2}$, $Z2_{\max} < P \le Z3_{\max} \implies \text{Z3}$, $Z3_{\max} < P \le Z4_{\max} \implies \text{Z4}$, $P > Z4_{\max} \implies \text{Z5}$.
• Active duration spent in each zone SHALL be accumulated from consecutive sample timestamps $\Delta t = (t_{i+1} - t_i)$ clamped to $[0\text{s}, 5\text{s}]$ to eliminate runaway accumulation during pauses.
• If valid samples have identical or non-advancing timestamps (degenerate timestamps where `first == last`), the calculator SHALL defensively fall back to unit duration ($1\text{s}$ per sample) rather than collapsing to a $1\text{s}$ terminal duration.
• If total active power duration is 0 or no valid power samples exist, the calculator SHALL return `null`.
3. *Full-Fidelity SQLite Telemetry Extraction (`WorkoutRepository.kt`)*:
• `WorkoutRepository.getPowerZoneDistribution(workoutId)` SHALL execute on `Dispatchers.IO`.
• The repository SHALL query athlete power zone thresholds from `SettingsDataStore`: keys `pwr_bike_zone1_max` through `pwr_bike_zone4_max` via `SettingsDataStoreJavaHelper.getZoneMax(application, ZoneType.PWR_BIKE, 1..4)`.
• The repository SHALL query the workout samples table for `TIME_ACTIVE`, `TIME_TOTAL`, `time` (`WorkoutSamplesDbHelper.TIME`), and `POWER`. If `TIME_ACTIVE` and `TIME_TOTAL` are null, the repository SHALL fall back to deriving relative elapsed seconds from the `time` column ($t - t_0$), or sequential 1s intervals. If the `POWER` column does not exist or the table contains 0 power samples, the repository SHALL cleanly return `null`.
4. *ViewModel State Flow (`TrackOnMapAftermathViewModel.kt`)*:
• `AftermathMapUIState` SHALL include `val powerZoneDistribution: ZoneDistributionData? = null`.
• In `loadAftermathData(workoutData)`, the ViewModel SHALL asynchronously query `getPowerZoneDistribution` and emit to `_uiState`.
5. *Visual UI Component (`PowerZoneDistributionCard.kt` & `ZoneDistributionColumnChart.kt`)*:
• The system SHALL render a 5-column vertical histogram with the 5 physiological cycling power zones (Zone 1 through Zone 5) on the X-axis and duration spent in each zone on the Y-axis.
• The column height for each zone SHALL be proportional to its duration relative to the maximum zone duration: $\text{heightFraction}_i = t_i / \max_j(t_j)$ (with a subtle minimum height of 4.dp for non-zero zones for visual visibility, and 0.dp for zero duration zones).
• Each vertical column SHALL be filled with its corresponding zone color `TTColor.Zone1` through `TTColor.Zone5`, with rounded top corners.
• Above/within each column, the system SHALL display formatted duration (`m:ss` or `h:mm:ss`) and percentage (`X%`).
• Below each column, the system SHALL display the zone label (`Z1`..`Z5`).
• The card SHALL display a compact title row with a power icon (`R.drawable.ic_power`), localized text `R.string.aftermath_power_zones_title`, and total active time.
6. *Slotted MapDetailLayout Integration (`MapDetailLayout.kt`, `TrackOnMapScreen.kt`)*:
• In `TrackOnMapScreen.kt`, `powerZoneDistribution` SHALL be passed to `analyticsContent`.
• If both `hrZoneDistribution` and `powerZoneDistribution` are present, both cards SHALL render sequentially with standard spacing (`8.dp`).
• If `powerZoneDistribution == null`, the component SHALL render nothing and consume 0 vertical space.
7. *100% 9-Language Localization Parity*:
• `aftermath_power_zones_title` SHALL be defined across all 9 supported locales (en, de, es, fr, it, ja, nl, pl, pt).

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: REQ-UI-203 (*Aftermath: Cycling Power 5-Zone Distribution Bar Architecture*).
2. *Historical Origin & Commit Trace*: Ticket `ATT-1390`, Sprint `2026-40.5`. Refined in ticket `ATT-1739`, Sprint `2026-40.6`, and `ATT-1811`, Sprint `2026-40.7`.
3. *Root Reason for Existing Formulation*: Originally formulated as a single horizontal stacked bar to match ATT-1389. During Sprint Review 2026-40.5, user review mandated discrete vertical columns (X-axis = Zones 1..5, Y-axis = time in zone). Previous extraction relied strictly on `TIME_ACTIVE` which is null in historical workouts.
4. *Preservation of Core Invariants*: Domain models, power zone calculation logic, database queries on `Dispatchers.IO`, ViewModel state flow, and 9-language localization parity remain 100% intact. Timestamp fallback to `time` column and unit duration degeneracy immunity preserve modern behavior while restoring historical workout fidelity.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an outdoor or indoor workout recorded with a Power meter,
• *When* the athlete views the workout in Aftermath (`TrackOnMapScreen`),
• *Then* the 5-zone Power distribution card SHALL render in `analyticsContent` as a 5-column vertical histogram with Zones 1-5 on the X-axis and duration on the Y-axis.
• *Given* a workout recorded without a Power meter (`POWER` column absent or zero samples),
• *When* viewed in Aftermath,
• *Then* the Power zone distribution card SHALL be hidden cleanly with zero space consumption.
• *Given* a workout recorded with both Heart Rate and Power sensors,
• *When* viewed in Aftermath,
• *Then* both Heart Rate and Power zone distribution cards SHALL render sequentially in `analyticsContent`.
• *Given* a historical workout where `TIME_ACTIVE` and `TIME_TOTAL` are null,
• *When* viewed in Aftermath,
• *Then* power zone distribution SHALL derive elapsed seconds from the `time` column and correctly populate multi-minute zone durations.

**Invariants**: SQLite single-thread confinement, zero degradation of elevation profile or GPS map rendering, and 9-language localization parity MUST NOT be broken.

### REQ-UI-204: Aftermath: High-Aesthetic Lap & Interval Split Visualizer Architecture [Verified]
- **Tests**: `TST-UI-158`, `TST-UI-182`, `TST-UI-183`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `TrackOnMapScreen.kt`, `WorkoutLaps.kt`, `LapSplitVisualizer.kt`, `LapSplitModels.kt`, `LapSplitCalculator.kt`, `strings.xml` (all 9 locales)
- **Rationale**: The system SHALL provide a high-aesthetic, professional Lap and Interval Split Visualizer within the workout aftermath inspection view (`TrackOnMapScreen.kt` / `MapDetailLayout.kt`) and workout laps view (`WorkoutLaps.kt`), replacing legacy emoji-laden bar visualizers with modern Material 3 typography and aligned split comparisons (ATT-1742):
1. *Domain Models & Calculation Engine (`LapSplitModels.kt`, `LapSplitCalculator.kt`)*:
• The pure domain models (`LapSplitItem`, `LapSplitChartData`) and mathematical engine (`LapSplitCalculator`) SHALL be preserved.
• `LapSplitItem` SHALL support clean formatting: lap number, display name, distance, duration, formatted pace/speed, relative ratio $[0.25, 1.0]$, fastest indicator, and slowest indicator.
• Emojis (`🐇`, `🦔`) and garish neon zone colors SHALL NOT be used.
2. *Visual Composable (`LapSplitVisualizer.kt`)*:
• The system SHALL render a sleek, modern split comparison card (`LapSplitVisualizerCard`).
• The card header SHALL display an icon (`R.drawable.ic_lap_laps`), localized title `R.string.aftermath_laps_splits_title`, and average pace/speed.
• Each split row SHALL feature clean columnar alignment: Lap badge (`L1`, `L2`), Distance & Duration, Formatted Pace/Speed, and a subtle proportional bar.
• The fastest split SHALL be denoted by an elegant, understated "Best" pill badge (`R.string.split_badge_best`).
• Split bars SHALL use subtle, harmonious theme tokens (`MaterialTheme.colorScheme.primary.copy(alpha = 0.65f)`, `tertiary` for best split) rather than saturated multi-color rainbow bars.
• Tapping a split SHALL trigger interactive selection (`selectedLapNr`) with subtle container accenting.
3. *Aftermath & Map Integration (`TrackOnMapScreen.kt`)*:
• In `TrackOnMapScreen.kt`, the visualizer card SHALL be rendered in `analyticsContent` when `splitChartData != null` ($\ge 2$ laps).
• When a split is selected (`selectedLapNr != null`), the corresponding segment of the route SHALL be highlighted on the map (`lapSegment`), and start/end pins SHALL be rendered on the track.
4. *Workout Laps Integration (`WorkoutLaps.kt`)*:
• In `WorkoutLaps.kt`, the visualizer SHALL be rendered above the detailed laps table when `splitChartData != null`.
5. *100% 9-Language Localization Parity*:
• String resources `aftermath_laps_splits_title`, `split_badge_best`, `laps_header` SHALL be fully localized across all 9 supported locales (en, de, es, fr, it, ja, nl, pl, pt).

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: REQ-UI-204 (*Aftermath: Compact Lap & Interval Split Chart Architecture*).
2. *Historical Origin & Commit Trace*: Ticket `ATT-1392` (Sprint `2026-40.5`, commit `1c8ef184`) originally introduced visual split bars. Reverted in `ATT-1741` (Sprint `2026-40.6`, commit `81cf2958`) following user review rejection ('looks really bad... create one ticket to revert this and one ticket to make this much more better'). Redesigned in `ATT-1742` (Sprint `2026-40.7`).
3. *Root Reason for Existing Formulation*: Reverted to remove childish emojis (`🐇`, `🦔`) and garish rainbow bars from production code while preserving mathematical engines.
4. *Preservation of Core Invariants*: Underlying SQLite lap storage, manual lap editing in `WorkoutLaps`, map detail layouts, and single-thread SQLite confinement are 100% intact.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an activity with $\ge 2$ laps viewed in Aftermath (`TrackOnMapScreen`),
• *When* the screen renders,
• *Then* `LapSplitVisualizerCard` SHALL render in `analyticsContent` with clean typography, aligned columns, subtle split bars, and an elegant 'Best' badge on the fastest split, without animal emojis or garish rainbow colors.
• *Given* an athlete tapping a split in `LapSplitVisualizerCard`,
• *When* selected,
• *Then* the corresponding route segment SHALL be highlighted on the map track polyline with start and end pins.
• *Given* an activity with fewer than 2 laps,
• *When* viewed in Aftermath or WorkoutLaps,
• *Then* the visualizer card SHALL be omitted cleanly with zero vertical whitespace.

**Invariants**: SQLite single-thread confinement, zero degradation of elevation profile or GPS map rendering, and tabular lap editing MUST NOT be broken.

---

## Change History

| Version | Date | Author | Ticket | Summary of Changes |
| :--- | :--- | :--- | :--- | :--- |
| `1.0.0` | 2026-11-18 | AI Agent (`ATT-8206`) | `ATT-8206` | Split oversized shard into compliant sub-shard (<= 4,000 lines, `REQ-PRO-093.4`, `REQ-PRO-127.3`). |
| `1.0.1` | 2026-11-19 | Lane A Flash (`ATT-8324`, `--as agent1`) | `ATT-8324` | Promoted `REQ-UI-120` (`TST-PER-010`) and `REQ-UI-125` (`TST-PER-017`) to `[Verified]` in Stage 5 following Robolectric Compose UI, `PeriodsViewModel`, and 9-locale `TranslationParityTest` verification (`Rule 7.5`). |
| `1.0.2` | 2026-11-20 | Lane A Flash (`ATT-8330`, `--as agent1`) | `ATT-8330` | Promoted 32 unblocked UI Design System requirements to `[Verified]` following Gate 4 (`3e20473b`, `129/129` PASS, `Rule 7.5`); retained multi-TST blocked `REQ-UI-011` and Bucket C requirements at `[Implemented]`. |
