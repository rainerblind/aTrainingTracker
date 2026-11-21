# Living Requirements: UI Components & Design System (Part 5 of 5)

**Document ID**: `DOC-REQ-UI-P5`  
**Version**: 1.0.25  
**Status**: Reviewed  
**Approver**: Pending PO Sign-Off  

**Domain**: `ui_design_system_part5`  
**Prefixes**: `UI`, `UNT`  
**Scope**: Jetpack Compose design system, UI components, typography, dialogs, gestures, and layout contracts.  
**Total Requirements**: 49

---

### REQ-UI-488: Strava Settings Dialog Close Button Elimination & Sync Card Action Icons [Verified]
- **Tests**: `TST-UI-488`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `StravaSettingsDialog.kt`, `AppModalBottomSheet.kt`
- **Rationale**: In `StravaSettingsDialog.kt`, two visual and interaction defects were identified: (1) redundant top-right `✕` close button in the header alongside sticky bottom `Abbrechen` and `Speichern` action buttons (addressed via `AppBottomSheetContent`'s `showCloseButton = (actions == null)` default introduced in `REQ-UI-473`); and (2) manual sync cards (*Equipment*, *Routes*, *Segments*) were rendered as static text blocks in a bare `Column` without trailing action indicators or progress spinners, making interactive triggers look like read-only status displays.
- **Description**:
1. *Close Button Elimination (`REQ-UI-488.1`)*:
   • In `StravaSettingsDialog.kt`, because `actions` is provided (`AppDialogActions.SaveCancel`), `AppBottomSheetContent` SHALL automatically suppress the top-right `✕` close button, leaving the bottom action row as the sole dismissal/confirmation mechanism.
2. *Interactive Manual Sync Card Feedback (`REQ-UI-488.2`)*:
   • The three manual sync `OutlinedCard`s (*Ausrüstung aktualisieren*, *Routen aktualisieren*, *Segmente aktualisieren*) SHALL format their content using a horizontal `Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp))`.
   • The left child SHALL be a `Column(modifier = Modifier.weight(1f))` holding the title (`MaterialTheme.typography.titleMedium`) and the timestamp / status subtitle (`MaterialTheme.typography.bodySmall`, `onSurfaceVariant`).
   • The right child SHALL be a trailing feedback container rendering `CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary)` when actively syncing (`isEquipmentSyncing`, `isRoutesSyncing`, or `isSegmentsSyncing` is `true`), or `Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)` when idle.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Builds on `REQ-UI-473` (*Modal Bottom Sheet Close Button Elimination*); establishes `REQ-UI-488` (*Strava Settings Dialog Close Button Elimination & Sync Card Action Icons*).
2. *Historical Origin & Commit Trace*: Commit `8058c49e` (ATT-1083 / `REQ-UI-149`) and commit `d92469ec` (ATT-5873).
3. *Root Reason for Existing Formulation*: Manual sync cards initially rendered simple vertical text columns; reactive sync StateFlows only swapped subtitle text without visual icon affordances.
4. *Preservation of Core Invariants*: Manual sync click callbacks, concurrency guards (`if (!isSyncing)`), background sync workers (`StravaEquipmentSyncWorker`, `StravaRoutesSyncWorker`, `StravaSegmentsSyncWorker`), and selective upload preferences remain 100% strictly intact.

**Acceptance Criteria (Given-When-Then)**:
• *Given* `StravaSettingsDialog` open with bottom `Abbrechen` and `Speichern` buttons,
• *When* inspecting the header row,
• *Then* zero `✕` close buttons are rendered.
• *Given* an idle Strava sync card (*Equipment*, *Routes*, or *Segments*),
• *When* rendered in `StravaSettingsDialog`,
• *Then* it displays `Icons.Default.Refresh` as a trailing action icon on the right.
• *Given* an active synchronization operation in progress,
• *When* rendered in `StravaSettingsDialog`,
• *Then* the corresponding card displays a 20.dp `CircularProgressIndicator` instead of the refresh icon.

**Invariants**: Strava OAuth 2.0 authentication, worker scheduling, repository sync algorithms, and 100% test pass rate MUST NOT be broken.

### REQ-UI-489: Route Details Climb Badges, Header Iconography, Section Deduplication & Localized Grade Formatting [Verified]
- **Tests**: `TST-UI-489`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `ElevationProfile.kt`, `RouteSummaryHeader.kt`, `RouteOnMapScreen.kt`, `RouteSegmentsBreakdownSection.kt`, `ClimbDetector.kt`
- **Rationale**: In the Route Details screen (`RouteOnMapScreen.kt`), UI and localization audits identified four presentation defects: (1) excessive legacy top padding (`44.dp`) in `ElevationProfile.kt` creating an unneeded gap and causing summit climb badges to clip under the toolbar boundary; (2) duplicate upward arrow icon (`ic_ascent`) for both elevation gain and climb count in `RouteSummaryHeader.kt`; (3) redundant breakdown section headers (`Anstiege (N)` / `Segmente (M)`) rendered directly beneath active tab filter chips; and (4) hardcoded `Locale.US` decimal points (`0.7 km @ 3.7%`) in climb titles generated by `ClimbDetector.kt` conflicting with German comma conventions (`3,7%`).
- **Description**:
1. *Elevation Profile Top Clearance & Height Calibration (`REQ-UI-489.1`)*:
   • In `ElevationProfile.kt`, `topPadding` SHALL be declared as `if (showZoomControls) 24.dp else 16.dp`, and `totalCanvasHeight` SHALL be declared as `fixedHeight ?: if (showZoomControls) cachedData.adaptiveHeight + 8.dp else cachedData.adaptiveHeight`. This eliminates the legacy 20dp white space gap reserved for decoupled zoom controls while maintaining at least 24dp top clearance for summit climb category badges (`UC`, `Cat 4`, etc.) at 1.0x font scale without vertical boundary clipping during split-pane scrolling.
2. *Route Summary Header Climb Icon Disambiguation (`REQ-UI-489.2`)*:
   • In `RouteSummaryHeader.kt`, the metric item for climb count (`climbs.size`) SHALL bind `iconRes = R.drawable.ic_altitude` (mountain silhouette) instead of `R.drawable.ic_ascent` (upward arrow), cleanly distinguishing total climb count from cumulative elevation gain.
3. *Route Details Section Heading Deduplication (`REQ-UI-489.3`)*:
   • `RouteClimbsBreakdownSection` and `RouteSegmentsBreakdownSection` SHALL declare an optional parameter `showHeader: Boolean = true`.
   • In both breakdown composables, the section heading `Text` component SHALL be wrapped in `if (showHeader)`.
   • In `RouteOnMapScreen.kt`, when rendering the breakdown sections under the dual-tab filter chip row (`climbs.isNotEmpty() && matchedSegments.isNotEmpty()`), `showHeader` SHALL be passed as `!(climbs.isNotEmpty() && matchedSegments.isNotEmpty())`, suppressing the redundant duplicate section heading beneath the active filter chip.
4. *Locale-Aware Climb Title Grade Formatting (`REQ-UI-489.4`)*:
   • In `ClimbDetector.kt` (`buildClimb`), the generated default climb name SHALL format average grade using `String.format(Locale.getDefault(), "%.1f%%", avgGrade)` instead of hardcoded `Locale.US`.
   • In `RouteClimbsBreakdownSection` in `RouteOnMapScreen.kt`, default generated climb titles SHALL dynamically format the average gradient matching the active runtime locale and user preferences.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Amends `REQ-UI-225` (*Decoupled Global Telemetry Zoom Toolbar & Chart Spacing*), `REQ-UI-274` (*Dynamic Elevation Profile Top Clearance & Container Height Stability*), `REQ-UI-302` (*Route Details Segment & Climb Tabs*), `REQ-UI-308` (*Route POI Overlay & Waypoint Filtering*), and `REQ-UI-387` (*Localized Climb Titles & Metric Formatter Parity*); establishes `REQ-UI-489` (*Route Details Climb Badges, Header Iconography, Section Deduplication & Localized Grade Formatting*).
2. *Historical Origin & Commit Trace*: Commit `2b3cb559` (ATT-2386 zoom decoupling), commit `6eaeb507` (ATT-3453 climb badge clearance), commit `0937a091` (ATT-3575 climb title localization), and commit `b24a3e9c` (ATT-2583 / ATT-2763 route tabs).
3. *Root Reason for Existing Formulation*: Legacy 44dp topPadding accommodated internal on-canvas zoom buttons prior to external toolbar extraction; duplicate `ic_ascent` was reused for climb count because no dedicated terrain icon was assigned; unconditional breakdown headers were authored prior to the introduction of dual-tab filter chips; hardcoded `Locale.US` in `ClimbDetector` was left during initial detector prototyping.
4. *Preservation of Core Invariants*: Climb detection algorithms (`REQ-MAP-027`), segment matching, map polyline overlays, scrubbing overlay synchronization, 9-language localization parity across EN, DE, ES, FR, IT, JA, NL, PL, PT, and 100% test pass rate remain strictly preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* the Elevation Profile rendered in detail view (`showZoomControls = true`),
• *When* inspecting `topPadding` and `totalCanvasHeight`,
• *Then* `topPadding` is `24.dp` and `totalCanvasHeight` adds `8.dp` over `cachedData.adaptiveHeight`, eliminating the 20dp empty white gap while keeping summit climb badges completely visible without clipping.
• *Given* the Route Details summary header with climbs present,
• *When* rendered on screen,
• *Then* total elevation gain displays `R.drawable.ic_ascent` (upward arrow) and climb count displays `R.drawable.ic_altitude` (mountain silhouette).
• *Given* a route with both climbs and matched segments (`climbs.isNotEmpty() && matchedSegments.isNotEmpty()`),
• *When* the user views the route details screen,
• *Then* the breakdown section displays filter chips `[Anstiege (N)] [Segmente (M)]` and suppresses the duplicate text heading immediately below the chips.
• *Given* the application running in German locale (`de`),
• *When* an automated climb is detected or displayed,
• *Then* its grade in the title row is formatted with a localized comma (e.g., `3,7%`) rather than an English dot (`3.7%`).

**Invariants**: Climb categorization thresholds (`CAT_4`..`HC`), route point coordinates, split-pane dragging, scrubbing overlay synchronization, 9-language localization parity across EN, DE, ES, FR, IT, JA, NL, PL, PT, and 100% test pass rate MUST NOT be broken.

### REQ-UI-490: Workout Cluster Trend Chart & Dashboard Polish: Label Collision Resolution, Average Background Badge, Time-Range Filtering & Thousands Grouping [Verified]
- **Tests**: `TST-UI-490`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `WorkoutClusterTrendChartCard.kt`, `WorkoutClusterHeatmapScreen.kt`, `DistanceFormatter.java`, `AltitudeFormatter.java`
- **Rationale**: In `WorkoutClusterDetailDashboard` (`WorkoutClusterHeatmapScreen.kt` & `WorkoutClusterTrendChartCard.kt`), UI audits of high-volume clusters (e.g. `Bike2Work`) revealed five readability and layout defects: (1) average label `Ø ...` drawn without a background pill directly over dense trend lines and data markers; (2) bottom Y-axis boundary label colliding vertically with X-axis start date at `x = leftPadding`; (3) redundant card title `📈 Leistungsverlauf` repeating the selected tab button; (4) lack of time-range filtering for multi-year clusters; and (5) lack of localized thousands separators on cumulative statistics totals (`136730 m`, `4226 km`).
- **Description**:
1. *Canvas Text Separation & Left Gutter (`REQ-UI-490.1`)*:
   • In `WorkoutClusterTrendChartCard.kt`, Y-axis boundary labels (`yAxisTopLabel` and `yAxisBottomLabel`) SHALL be positioned in a dedicated left gutter (`leftPadding = 48.dp.toPx()`) rather than overlapping the start date.
   • `xAxisStartLabel` and `xAxisEndLabel` SHALL be drawn exclusively along the bottom margin below the plot canvas.
2. *Average Reference Line Badge (`REQ-UI-490.2`)*:
   • The average label `Ø ...` SHALL be rendered with a rounded background container badge (`surfaceColor` with subtle fill) drawn on top of the trend lines and points, guaranteeing text readability regardless of underlying data marker density.
3. *Header Deduplication & Time-Range Filter Chips (`REQ-UI-490.3`)*:
   • The redundant text header `📈 Leistungsverlauf` inside `WorkoutClusterTrendChartCard` SHALL be replaced with a compact time-range filter chip group (`FilterChip` for `1J` / `3J` / `Alle`, localized across all 9 locales without emojis) docked to the left of the `[Dauer | Tempo]` selector row.
   • When a time range is selected, the chart points SHALL filter relative to the latest workout timestamp (`latestTimeS - spanS`), automatically falling back to all points if fewer than 2 workouts match the selected window.
4. *Cumulative Volume Localized Thousands Grouping (`REQ-UI-490.4`)*:
   • Large integer meter/kilometer totals formatted via `DistanceFormatter.java` and `AltitudeFormatter.java` SHALL use locale-aware grouping formats (`%,.0f`) so cumulative totals render with localized grouping separators (e.g. `136.730 m`, `4.226 km` in German).

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Refines `REQ-SET-078` (*Historical Duration and Speed/Pace Trend Chart for Workout Clusters*), `REQ-UI-225`, and Rule 36 formatters (`DistanceFormatter`, `AltitudeFormatter`).
2. *Historical Origin & Commit Trace*: Commit `feat(ATT-4067)` (Sprint 2026-42.10) and `feat(ATT-4287)`.
3. *Root Reason for Existing Formulation*: Initial trend card placed both `yAxisBottomLabel` and `xAxisStartLabel` at `x = leftPadding`, without anticipating collision or time-range filtering on clusters with hundreds of workouts; formatters used `%.0f` without grouping specifier `,`.
4. *Preservation of Core Invariants*: "Faster-is-higher" coordinate inversion, PR markers, nearest-point touch selection, $N \ge 2$ threshold, and 9-language localization parity without decorative emojis (`ATT-5400`) MUST remain strictly preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* `WorkoutClusterTrendChartCard` rendering a cluster with multiple workouts,
• *When* the average label `Ø ...` is drawn on canvas,
• *Then* it renders on top of a rounded surface background badge, ensuring complete text legibility over dense trend lines and data markers.
• *Given* `WorkoutClusterTrendChartCard` with start date and bottom Y-axis label,
• *When* rendered on canvas,
• *Then* the bottom Y-axis label is rendered in the left gutter and the start date along the bottom margin without visual collision.
• *Given* the toolbar header row of `WorkoutClusterTrendChartCard`,
• *When* viewed in `WorkoutClusterDetailDashboard`,
• *Then* the redundant `📈 Leistungsverlauf` heading is replaced by time-range filter chips (`1J | 3J | Alle`) docked left of the `[Dauer | Tempo]` toggle.
• *Given* the cumulative volume card in the Statistics tab,
• *When* displaying totals $\ge 1.000$,
• *Then* cumulative distance and elevation are formatted with locale-aware thousands grouping (e.g., `4.226 km` and `136.730 m` in German).

**Invariants**: 9-language localization parity across EN, DE, ES, FR, IT, JA, NL, PL, PT; cluster similarity calculations; database schema backward compatibility; 100% test pass rate.

### REQ-UI-491: MapDetailLayout Status Bar Inset Clearance for Collapsed Header Floating Controls [Verified]
- **Tests**: `TST-UI-491`, `TST-UI-500`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `MapDetailLayout.kt`, `MapDetailLayoutCollapsingHeaderContractTest.kt`
- **Rationale**: In full-screen detail views utilizing `MapDetailLayout` with `useStatusBarsPadding == true` (Favorite Routes, Route Details, Segment Details, Climbs), scrolling down collapses the top header offscreen, causing `currentTopPaddingPx` to reach `0`. While `ATrainingTrackerMap` bleeds edge-to-edge behind the status bar, top-aligned floating action buttons (the Share button and `overlay()` composables) translate into the system status bar, overlapping system status bar indicators (clock, 5G, battery).
- **Description**:
1. *Dynamic Floating Controls Top Inset (`REQ-UI-491.1`)*:
   • In `MapDetailLayout.kt`, when `useStatusBarsPadding == true`, dynamic status bar clearance `floatingControlsTopOffsetDp = with(density) { (statusBarHeightPx - currentTopPaddingPx).coerceAtLeast(0).toDp() }` SHALL be computed, where `statusBarHeightPx = WindowInsets.statusBars.getTop(density)`.
   • When `useStatusBarsPadding == false` (e.g. bottom sheets), `statusBarHeightPx` MUST evaluate to `0`, ensuring `floatingControlsTopOffsetDp == 0.dp`.
2. *Floating Controls Container Clearance (`REQ-UI-491.2`)*:
   • In `mapBox`, the slotted `overlay()` content and the floating Share button SHALL be wrapped in a container applying `Modifier.fillMaxSize().padding(top = floatingControlsTopOffsetDp)`.
   • When the header collapses (`currentTopPaddingPx == 0`), floating action controls SHALL remain offset below the status bar (`statusBarHeight + defaultPadding`), while `ATrainingTrackerMap` continues to render edge-to-edge behind the translucent status bar.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Refines `REQ-UI-250` (*Collapsing Upper Metadata Architecture & Map Squashing Prevention via NestedScroll in MapDetailLayout*).
2. *Historical Origin & Commit Trace*: Introduced in `ATT-2150` (commit `e2b80b2d`) for collapsing header behavior in `MapDetailLayout`.
3. *Root Reason for Existing Formulation*: `currentTopPaddingPx` was allowed to drop to `0` to give the map full-screen bleed, but overlooked status bar collision for top-aligned floating action buttons inside `mapBox`.
4. *Preservation of Core Invariants*: Full-screen map bleed under the transparent status bar is 100% preserved; uncollapsed `16.dp` button spacing below header is preserved; bottom sheet layouts (`useStatusBarsPadding == false`) remain unaltered (`floatingControlsTopOffsetDp == 0.dp`).

**Acceptance Criteria (Given-When-Then)**:
• *Given* `MapDetailLayout` with `useStatusBarsPadding == true` and an expanded header,
• *When* the header is fully expanded (`connection.appBarOffset == 0`),
• *Then* `floatingControlsTopOffsetDp` evaluates to `0.dp`, preserving the default `16.dp` spacing below the header bottom edge.
• *Given* `MapDetailLayout` with `useStatusBarsPadding == true`,
• *When* the user scrolls and the header collapses completely (`connection.appBarOffset == -headerHeightPx`),
• *Then* `floatingControlsTopOffsetDp` evaluates to `statusBarHeightDp`, preventing the Share button and overlay action buttons from colliding with Android system status bar icons.
• *Given* `MapDetailLayout` configured as a bottom sheet (`useStatusBarsPadding == false`),
• *When* scrolling or expanding,
• *Then* `floatingControlsTopOffsetDp` remains strictly `0.dp`.

**Invariants**: Edge-to-edge map bleed under status bar is preserved; no changes to public API of `MapDetailLayout`; bottom sheet mode behavior is preserved; 100% test pass rate.

### REQ-UI-492: Equipment & Sensors UI: Strava Gear Deduplication, Date/Frame-Type Localization, Placeholder Harmonization & Sensor Detail Status Dot Alignment [Verified]
- **Tests**: `TST-UI-492`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `EquipmentTabsScreen.kt`, `BikeTypeSelector.kt`, `EditEquipmentDialog.kt`, `EditDeviceDialog.kt`, `DeviceData.kt`, `values*/strings.xml`
- **Rationale**: Live audits of `Räder & Schuhe` and `Sensoren` identified four user-facing defects: (1) redundant repetition of identical Strava gear names in subtitle rows, (2) unlocalized raw ISO usage dates (`YYYY-MM-DD`) and hardcoded English bike frame types ("Road", "Time Trial"), (3) German compounding typo `Rad Typ` and asymmetric dropdown placeholders ("Keine" vs. "Keine verknüpften Sensoren"), and (4) sensor detail dialog showing a green status LED while displaying "100% (nicht verbunden)", uncapitalized internal smartphone manufacturers ("samsung", "google"), and an unstyled bare `TextButton` for equipment linking.
- **Description**:
1. *Strava Gear Name Deduplication (`REQ-UI-492.1`)*:
   • In `EquipmentTabsScreen.kt`, when an equipment item's `stravaName` is non-blank and identical (case-insensitive) to its primary `name`, the card SHALL render an inline `StravaOriginalLogo` badge in the title row and SHALL suppress the duplicate subtitle line.
   • When `stravaName` differs from `name`, the distinct Strava gear name SHALL continue to render in the subtitle row with `StravaOriginalLogo`.
2. *Usage Date & Frame-Type Localization (`REQ-UI-492.2`)*:
   • In `UsageItem`, `firstUsed` and `lastUsed` date strings SHALL be formatted via `DateTimeUtils.formatIsoDate(date)` adhering to the active locale (`FormatStyle.MEDIUM`).
   • Bike frame types (1: MTB, 2: Cross, 3: Road, 4: Time Trial) SHALL be resolved via localized string resources (`equipment_bike_type_mtb`, `cross`, `road`, `tt`) across all 9 locales (e.g. "Rennrad", "Zeitfahrrad" in German) in both `EquipmentTabsScreen.kt` and `BikeTypeSelector.kt`.
3. *Spelling & Dropdown Placeholder Harmonization (`REQ-UI-492.3`)*:
   • In `values-de/strings.xml`, `equipment_bike_type` SHALL be corrected to `Radtyp` and `equipment_select_bike_type` to `Radtyp auswählen`.
   • In `EditEquipmentDialog.kt`, the empty placeholder for linked sensors SHALL be harmonized with linked sport types to display compact "Keine" / "None" (`R.string.equipment_no_sports_linked`).
4. *Sensor Detail Dialog Status Dot, Capitalization & Dropdown Styling (`REQ-UI-492.4`)*:
   • In `EditDeviceDialog.kt`, the technical status LED dot color SHALL be strictly synchronized with `DeviceStatusRow` connection state (`liveData?.isConnected ?: data.isConnected`), displaying `TTColor.ConnectionStatusGreen` when connected and neutral gray (`Color.LightGray`) when disconnected.
   • In `DeviceData.kt:raw2UiDeviceData`, raw Android manufacturer strings (e.g. `Build.BRAND`) SHALL be capitalized (e.g. "Samsung", "Google") via titlecase formatting.
   • In `EditDeviceDialog.kt:EquipmentSection`, the linked equipment selector SHALL be styled as an `OutlinedTextField` with dropdown affordance (`trailingIcon`) matching standard dialog inputs.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement `REQ-UI-492`, refining `REQ-UI-061` (*Universal List Card Contracts*), `REQ-UI-149` (*Modal Bottom Sheet Dialogs*), `REQ-UI-256` (*Equipment Management UI*), and `REQ-UI-283` (*Sensor Management*).
2. *Historical Origin & Commit Trace*: Introduced in `ATT-2126` (`commit fc10ad81`) and `ATT-2382` during equipment card and sensor management implementation.
3. *Root Reason for Existing Formulation*: Strava subtitle was unconditionally rendered to confirm external provenance, overlooking 1:1 name matches; frame types were hardcoded during rapid prototyping; and status LED was bound independently from `DeviceStatusRow`.
4. *Preservation of Core Invariants*: Equipment-to-sensor relationships (`REQ-UI-256`), database schemas (`Equipment.db`, `Devices.db`), swipe-to-delete card gestures (`REQ-UI-061`), 9-locale parity (`REQ-LOC-001`), and 100% test pass rate MUST NOT be compromised.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an equipment item where `stravaName` matches `name` (e.g. "Brooks Green Silence"),
• *When* rendered in `EquipmentTabsScreen`,
• *Then* an inline Strava logo badge is displayed next to the title and no duplicate subtitle line is shown.
• *Given* an equipment item with `firstUsed` or `lastUsed` ISO dates,
• *When* rendered in German locale,
• *Then* dates are formatted as `DD.MM.YYYY` (e.g. `10.01.2019`) rather than raw ISO `YYYY-MM-DD`.
• *Given* a bike with frame type 3 (Road) or 4 (Time Trial) in German locale,
• *When* viewed in `EquipmentTabsScreen` or `BikeTypeSelector`,
• *Then* the frame type displays as "Rennrad" or "Zeitfahrrad" and the selector label reads "Radtyp".
• *Given* a sensor that is disconnected in `EditDeviceDialog`,
• *When* the dialog is displayed,
• *Then* the status indicator dot is gray (`Color.LightGray`), the status text reads `(nicht verbunden)`, and smartphone manufacturers like "samsung" or "google" are capitalized to "Samsung" or "Google".

**Invariants**: Equipment and sensor database schemas remain unchanged; Bluetooth/ANT+ background connectivity pipelines remain unaffected; 9-language localization parity across EN, DE, ES, FR, IT, JA, NL, PL, PT; 100% test pass rate.

### REQ-UI-493: ClusterItem & Filter (Lieblingsstrecken): Hit Count Display Healing, Bidirectional Location Context, Landscape Thumbnail & Filter Sheet Reset Action [Verified]
- **Target Release**: `V5.0.0`
- **Tests**: `TST-UI-493`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `WorkoutClusterComponents.kt`, `WorkoutClustersViewModel.kt`, `WorkoutClusterRepository.kt`, `FilterBottomSheetScaffold.kt`, `strings.xml` (all 9 locales)
- **Rationale**: In `Lieblingsstrecken` cluster management (`WorkoutClusterComponents.kt`, `WorkoutClusterRepository.kt`, and `FilterBottomSheetScaffold.kt`), UI audits revealed four presentation and telemetry defects: (1) cluster cards display `0 Aufzeichnungen` when `RouteClusters.hitCount` is desynchronized from `WorkoutSummaries`; (2) cluster cards only display start locations and fail to resolve destination coordinates (`endLat`, `endLng`); (3) `110.dp × 75.dp` map thumbnails with `newLatLngBounds(..., 40)` leave unused horizontal whitespace beside `~117.dp`-tall metadata on `411.dp` phones while `Google` watermark and Start/End/Apex markers obscure the polyline; and (4) the filter bottom sheet reset button label ("Alle zurücksetzen") wraps onto two lines in the 50% split button row.
- **Description**:
1. *Hit Count Display Healing & Repository Reconciliation (`REQ-UI-493.1`)*:
   • In `WorkoutClusterComponents.kt` (`WorkoutClusterMetadataBlock`), the displayed recordings count SHALL be calculated from `maxOf(cluster.hitCount, stats?.workoutCount ?: 0)` (falling back to `cluster.hitCount`), guaranteeing that cluster cards display the accurate number of recorded workouts even if the cached `hitCount` in SQLite is zero.
   • In `WorkoutClusterRepository.kt` (`loadAllClustersLightweight`), when clusters are loaded and `_clusterStats` is hydrated from `WorkoutSummaries`, any cluster with `hitCount == 0` that has associated workouts in summary stats SHALL have its `hitCount` healed and updated.
2. *Bidirectional Route Endpoint Location Context (`REQ-UI-493.2`)*:
   • `WorkoutClustersViewModel` SHALL expose a general location lookup `getLocationName(lat: Double, lng: Double): String?` resolving recognized locations via `KnownLocationsDatabaseManager`.
   • In `WorkoutClusterComponents.kt` (`WorkoutClusterMetadataBlock`), both start coordinates (`startLat`, `startLng`) and destination coordinates (`endLat`, `endLng`) SHALL be resolved.
   • If start and destination resolve to the same known location, the UI SHALL display `Start & Ziel: [Location]` (`R.string.workout_start_and_destination`).
   • If start and destination resolve to different known locations, the UI SHALL display `[Start] → [Ziel]`.
   • If only one endpoint resolves to a known location, the UI SHALL display `Start: [Start]` (`R.string.cluster_start_location`) or `Ziel: [Ziel]` (`R.string.workout_destination_location`).
3. *Landscape Map Thumbnail, Camera Framing & Equipment Text Constraining (`REQ-UI-493.3`)*:
   • In `WorkoutClusterComponents.kt`, both `ClusterItem` and `UnclusteredWorkoutItem` SHALL render the right-hand route preview map `Surface` at `175.dp × 120.dp` (`Modifier.size(width = 175.dp, height = 120.dp)` with `12.dp` rounded corners on standard `>= 387.dp` screens), paired with a `12.dp` horizontal `Spacer` separating `WorkoutClusterMetadataBlock` / workout metadata from the map thumbnail, while preserving `GoogleMapOptions().liteMode(true)`, `DarkMapStyle.resolveMapProperties`, and `DarkMapAntiFlashOverlay`.
   • On narrow screens (e.g., `360.dp` viewport / `< 387.dp` screen width), the thumbnail width MUST clamp responsively so the left metadata column retains at least `160.dp` width; this width calculation MUST NOT place `BoxWithConstraints` (`SubcomposeLayout`) inside `Row(Modifier.height(IntrinsicSize.Min))` (derive width from `LocalConfiguration.current.screenWidthDp` or measure outside the `IntrinsicSize.Min` `Row`).
   • `CameraUpdateFactory.newLatLngBounds(bounds, ...)` SHALL use a named `16–20 px` padding constant (e.g., `THUMBNAIL_BOUNDS_PADDING_PX` in `16..20`, replacing literal `40` and removing the corresponding `MagicNumber` entry from `app/detekt-baseline.xml`), wrapped in a `try/catch` guard against unlaid-out / zero-size map layout exceptions (`IllegalStateException`), and `rememberCameraPositionState` SHALL optionally seed its initial `CameraPosition` to `bounds.center` when `bounds` is non-null to suppress world-zoom `(0, 0)` flash prior to `onMapLoaded`.
   • In `WorkoutClusterMetadataBlock` and `UnclusteredWorkoutItem`, the linked equipment string SHALL enforce `maxLines = 1` and `overflow = TextOverflow.Ellipsis`.
   • **Localization Parity**: `N/A - no UI strings` (zero `values*/strings*.xml` edits for the `ATT-8748` thumbnail enlargement; `REQ-UI-493.4` 9-locale `filter_reset` parity remains intact).
4. *Single-Line Filter Sheet Reset Button Action (`REQ-UI-493.4`)*:
   • In `FilterBottomSheetScaffold.kt`, the reset action button SHALL use the concise localized resource `R.string.filter_reset` ("Zurücksetzen" / "Reset") with `maxLines = 1` across all 9 supported locales (EN, DE, ES, FR, IT, JA, NL, PL, PT), guaranteeing a uniform single-line height matching the adjacent "OK" button in 50% split button rows.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Refines `REQ-UI-493.3` (`ATT-5846`, commit `c3c957e2`) alongside `REQ-SET-067` (*Auto-Naming & Hit Counting*), `REQ-UI-186` (*Recognized Start Location Badges*), `REQ-UI-244` (*Cluster Map Thumbnails*), and `REQ-UI-149` (*Filter Bottom Sheets*).
2. *Historical Origin & Commit Trace*: Introduced in `ATT-714`, `ATT-1402`, `ATT-2276`, and refined in `c3c957e2` (`ATT-5846`, which replaced `100.dp × 100.dp` thumbnails with `110.dp × 75.dp`).
3. *Root Reason for Existing Formulation*: `110.dp × 75.dp` (`ATT-5846`) preserved the prior `~100.dp` horizontal footprint, leaving `~65.dp` unused horizontal whitespace beside `~117.dp`-tall metadata on `411.dp` phones while `newLatLngBounds(..., 40)` compressed route polylines under the Google watermark and Start/End/Apex markers.
4. *Preservation of Core Invariants*: `GoogleMapOptions().liteMode(true)`, `DarkMapAntiFlashOverlay`, `DarkMapStyle.resolveMapProperties`, TopStart Merge/Delete `DropdownMenu`, `IntrinsicSize.Min` bottom alignment (zero `BoxWithConstraints` inside `IntrinsicSize.Min`), `maxLines = 1` equipment ellipsis, `filter_clear_all` / `filter_reset` 9-locale parity, zero-touch auto-naming counter integrity, and `KnownLocationsScreen.kt` (*Lieblingsorte*, `ATT-8749`) isolation MUST NOT be compromised.

**Acceptance Criteria (Given-When-Then)**:
• *Given* a cluster with `hitCount = 0` but having matching workouts in `WorkoutSummaries`,
• *When* displayed in `ClusterItem` or `WorkoutClusterDetailDashboard`,
• *Then* the recordings badge displays the non-zero count matching `stats.workoutCount`.
• *Given* a cluster where start and end coordinates match the same known location (e.g. "Home"),
• *When* rendered in `WorkoutClusterMetadataBlock`,
• *Then* the location badge reads "Start & Ziel: Home".
• *Given* a cluster where start coordinates resolve to "Home" and destination coordinates resolve to "Work",
• *When* rendered in `WorkoutClusterMetadataBlock`,
• *Then* the location badge reads "Home → Work".
• *Given* `ClusterItem` or `UnclusteredWorkoutItem` rendered on a standard `411.dp` phone viewport,
• *When* the card layout composes,
• *Then* the map preview `Surface` measures `175.dp` width by `120.dp` height with a `12.dp` horizontal spacer, multi-gear equipment strings truncate with an ellipsis on a single line, and `newLatLngBounds` frames the route using the named `16–20 px` padding constant with bounds-center camera seeding and `try/catch` safety.
• *Given* `ClusterItem` or `UnclusteredWorkoutItem` rendered on a narrow `360.dp` phone viewport,
• *When* the card layout composes,
• *Then* the thumbnail width clamps so the left metadata column retains at least `160.dp` width without throwing any `IntrinsicSize.Min` / `SubcomposeLayout` exception.
• *Given* `FilterBottomSheetScaffold` displayed in German locale,
• *When* inspecting the bottom action bar,
• *Then* the reset button displays "Zurücksetzen" on a single line with uniform height matching the "OK" button.

**Invariants**: MapView `liteMode(true)` and dark map anti-flash styling preserved; TopStart Merge/Delete `DropdownMenu` preserved; `IntrinsicSize.Min` bottom alignment preserved without `BoxWithConstraints` inside `Row`; auto-naming counter integrity preserved; 9-language localization parity across EN, DE, ES, FR, IT, JA, NL, PL, PT without emojis (`Localization Parity: N/A - no UI strings` for `ATT-8748`); 100% test pass rate.

### REQ-UI-494: ElevationProfile Y-Axis Grid Clearance & Zero-Altitude Profile Suppression in RouteItem [Verified]
- **Tests**: `TST-UI-494`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `ElevationProfile.kt`, `RouteItem.kt`
- **Rationale**: In `ElevationProfile` and `RouteItem`, UI audits of the Routes screen (`screen_04_routen.png`) identified two presentation defects: (1) intermediate Y-axis elevation grid labels (e.g. `400`) overlap the bottom minimum altitude label (`383 m`) due to omitting the `+ 10f` baseline offset in vertical clearance calculations and using a fixed hardcoded `-110f` X-offset; and (2) flat GPX routes lacking altitude data (e.g. `bike2work`, `↑ 0 m`) render a meaningless synthetic `-10 m` to `10 m` flat green elevation profile in `RouteItem`.
- **Description**:
1. *Y-Axis Intermediate Label Baseline Clearance Guard (`REQ-UI-494.1`)*:
   • In `ElevationProfile.kt`, the vertical clearance evaluation for intermediate Y-axis labels SHALL incorporate the drawn label baseline offset (`labelBaselineY = y + 10f`).
   • An intermediate altitude label SHALL only be rendered if its baseline maintains a clearance of at least `textPaint.textSize * 1.35f` from the bottom canvas boundary (`height`), from the top `maxAltLabel` boundary (`highlightPaint.textSize`), and from the previously rendered intermediate tick (`Math.abs(y - lastY)`).
2. *Right-Aligned Intermediate Elevation Labels (`REQ-UI-494.2`)*:
   • Intermediate Y-axis elevation labels SHALL NOT use a fixed `-110f` X-offset.
   • Intermediate labels SHALL be right-aligned dynamically to `-textPaint.measureText(altText) - 10f`, matching the visual right alignment of `minAltLabel` and `maxAltLabel`.
3. *Zero-Altitude Elevation Profile Suppression in RouteItem (`REQ-UI-494.3`)*:
   • In `RouteItem.kt`, `ElevationProfile` SHALL only be rendered when authentic altitude data is available (`hasElevationData = pathPoints.any { it.altitude != null && !it.altitude.isNaN() && Math.abs(it.altitude) > 1e-3 } || summary.elevationGain > 0.0`).
   • For flat GPX routes or tracks with zero elevation gain and null/zero altitude points, the `ElevationProfile` composable SHALL be suppressed, preventing display of the synthetic minimum vertical span (`-10 m` to `10 m`).

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement `REQ-UI-494`, refining `REQ-UI-197` (*Elevation Profile Layout Architecture*) and `REQ-UI-225` (*Route Item Layout*).
2. *Historical Origin & Commit Trace*: Introduced in `ATT-508` (bounds calculation) and `ATT-2463` (`RouteItem` layout).
3. *Root Reason for Existing Formulation*: The clearance check in `ElevationProfile` checked `height - y` before the `+ 10f` baseline shift was introduced for vertical text centration on grid lines; `RouteItem` assumed all imported GPX routes would contain elevation data.
4. *Preservation of Core Invariants*: `ATT-5853` top padding (`24.dp` / `16.dp`) and climb badge clearance invariants, chart zoom/pan math, full-screen route detail elevation analysis (`MapDetailLayout`), and authentic route elevation visualization MUST NOT be compromised.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an elevation profile with minimum altitude `383 m` and an intermediate tick at `400 m` whose drawn baseline would fall within `textPaint.textSize * 1.35f` of `height`,
• *When* the profile canvas is drawn,
• *Then* the colliding intermediate label `400` is suppressed, leaving `383 m` legible without overlap.
• *Given* intermediate altitude labels rendered on the Y-axis,
• *When* drawn on the canvas,
• *Then* each label is right-aligned to `-textPaint.measureText(altText) - 10f`.
• *Given* a route item with zero elevation gain (`elevationGain == 0.0`) and path points with null or zero altitude,
• *When* the route card is rendered in the routes list,
• *Then* the elevation profile is hidden completely rather than showing a `-10 m` to `10 m` synthetic bar.

**Invariants**: `ATT-5853` top padding and climb badge clearance invariants preserved; full-screen elevation detail inspection in `MapDetailLayout` preserved; authentic elevation profiles render without regression; 100% test pass rate.

### REQ-UI-495: AthleteProfileScreen Filter Pill Layout, Decimal Localization, PMC Card Heights & Time-Range Selector [Verified]
- **Tests**: `TST-UI-495`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `AthleteProfileScreen.kt`, `AthleteMetricsPlotMath.kt`, `AthleteProfileViewModel.kt`
- **Rationale**: In `AthleteProfileScreen.kt`, live UI audits (`screen_02_profil_zonen.png`, `screen_08_profil_pmc.png`) identified 4 presentation, layout, and localization defects: (1) filter pills in Verlauf and Messwert-Historie use uniform `weight(1f)`, truncating longer labels `Gewicht` and `Ruhe-HF` to `Gewi...` and `Ruhe...`; (2) hardcoded `Locale.US` decimal formatting renders dots (e.g. `80.0 kg`, `3.44 W/kg`, `2.4`, `0.8`, `+1.5`, `0.34`) inconsistent with the German active locale and adjacent localized strings (`-0,4/Wo.`); (3) unequal card heights in the PMC stat badge row because TSB contains 3 text lines while CTL/ATL contain 2 lines; and (4) absence of a time-range selector on `PerformanceManagementChartCard`, plotting all 10 historical years and compressing recent training curves into an unreadable sliver.
- **Description**:
1. *Proportional Filter Pill Width Allocation & Non-Truncation (`REQ-UI-495.1`)*:
   • In `AthleteProfileScreen.kt`, `CompactSelectorPill` items in both the *Verlauf* metric row and the *Messwert-Historie* filter row SHALL be weighted proportionally based on label text length (`Modifier.weight(label.length.coerceIn(3, 9).toFloat())`).
   • `CompactSelectorPill` horizontal padding SHALL be bounded to `3.dp` to ensure all 6 pills (`Gewicht`, `Ruhe-HF`, `FTP`, `CP`, `W'`, `W/kg`) fit on a single line on standard mobile screen widths ($\ge 360\,\text{dp}$) without ellipsis (`...`) truncation.
2. *Active Locale Decimal Formatting (`REQ-UI-495.2`)*:
   • In `AthleteMetricsPlotMath.kt`, format functions (`formatWeightValue`, `formatWPrimeValue`, `formatPowerToWeightValue`) SHALL default `locale` to `Locale.getDefault()`.
   • In `AthleteProfileScreen.kt`, value formatting across chart Y-axis ticks, estimate dialogs, and PMC stat badges (`CTL`, `ATL`, `TSB`, `ACWR`) SHALL format decimals according to `Locale.getDefault()`.
   • Canonical input parsing in `parseToCanonicalValue` SHALL preserve comma/dot-agnostic tolerance (`replace(',', '.')`).
3. *Uniform PMC Stat Badge Row Heights (`REQ-UI-495.3`)*:
   • In `AthleteProfileScreen.kt`, enclosing `Row` layouts for PMC stat cards SHALL specify `Modifier.height(IntrinsicSize.Min)` per Design Guideline 5.14.
   • `PmcStatBadge` SHALL apply `Modifier.fillMaxHeight()` to ensure all cards in the row (`CTL | ATL | TSB` in Row 1; `ACWR | Ramp Rate` in Row 2) match the height of the tallest card without vertical asymmetry.
4. *Dedicated PMC Chart Time-Range Selector (`REQ-UI-495.4`)*:
   • `AthleteProfileViewModel` and `AthleteProfileUiState` SHALL provide independent PMC time-range state (`selectedPmcTimeRange: MetricsTimeRange`), defaulting to `MetricsTimeRange.SIX_MONTHS`.
   • `PerformanceManagementChartCard` SHALL render a dedicated time-range selector row (`1M | 3M | 6M | 1J | Alle`) driving `onSelectPmcTimeRange(range)`.
   • Physiological CTL/ATL/TSB EWMA calculation SHALL retain full multi-year history, and time-range filtering via `filterPmcPointsByTimeRange` SHALL be executed strictly post-calculation.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Refines `REQ-UI-391.5` (*Dedicated AthleteProfileScreen UI, Locale Date Formatting & 9-Language Parity*) and `REQ-UI-401.3` (*PMC UI & Charting*).
2. *Historical Origin & Commit Trace*: Introduced in `ATT-3704` (commit `b660ef43`) and `ATT-3859` (commit `e5d9225e`).
3. *Root Reason for Existing Formulation*: Uniform `Modifier.weight(1f)` was a naive placeholder assuming short 2-3 char abbreviations; `Locale.US` was hardcoded during initial prototyping without auditing German decimal separators; PMC was initially launched without a dedicated range selector.
4. *Preservation of Core Invariants*: Metric input parsing preserves comma and dot tolerance (`replace(',', '.')`); EWMA continues calculating over full history prior to range windowing; `IntrinsicSize.Min` protects against `Constraints.Infinity` crash in scroll containers.

**Acceptance Criteria (Given-When-Then)**:
• *Given* the *Verlauf* metric selector and *Messwert-Historie* filter rows on a $\ge 360\,\text{dp}$ screen,
• *When* rendered in German locale (`Gewicht`, `Ruhe-HF`),
• *Then* all 6 filter pills display complete text on a single line without ellipsis (`...`) truncation.
• *Given* an active German locale (`de_DE`),
• *When* weight, W/kg, W', Y-axis ticks, and PMC badges (CTL, ATL, TSB, ACWR) are formatted,
• *Then* numbers display comma decimal separators (e.g. `80,0 kg`, `3,44 W/kg`, `2,4`, `0,8`, `+1,5`, `0,34`).
• *Given* the PMC stat badges in `PerformanceManagementChartCard`,
• *When* rendered in `Row(Modifier.height(IntrinsicSize.Min))` with `PmcStatBadge(Modifier.fillMaxHeight())`,
• *Then* all badges within Row 1 (CTL, ATL, TSB) and Row 2 (ACWR, Ramp Rate) have matching heights.
• *Given* `PerformanceManagementChartCard` displayed with default `6M` time range,
• *When* the user taps `1M`, `3M`, `1J`, or `Alle`,
• *Then* the displayed PMC curve updates to the chosen date window while CTL, ATL, and TSB values retain full-history EWMA mathematical accuracy.

**Invariants**: `Constraints.Infinity` crash prevention per Design Guideline 5.14 via `IntrinsicSize.Min`; complete historical EWMA integrity for CTL/ATL/TSB (slicing post-EWMA); metric input parsing supports both `,` and `.`; 9-language localization parity preserved; 100% test pass rate.

### REQ-UI-496: Navigation Drawer Header Height Compactness & Vertical Clearance Guard [Verified]
- **Tests**: `TST-UI-496`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `AppNavigationDrawer.kt` (`DrawerHeader`)
- **Rationale**: In `AppNavigationDrawer.kt`, visual audits (`audit_02_drawer.png`) revealed that the bottom-most navigation entry `Info & Unterstützung` (`drawer__info_support`) is vertically clipped on standard portrait mobile screens, forcing users to scroll the drawer to read the final support/privacy rows. Meanwhile, `DrawerHeader()` allocated an oversized `92.dp + statusBarHeight`, leaving a wide empty band of plain sky above the app branding. Reducing the net header height by 16dp (from `92.dp` to `76.dp`) trims the unneeded top sky while keeping the bottom landscape motif intact (ATT-5839). However, placing the branding row with `bottom = 6.dp` positioned the app icon and blue title directly on top of the dark forest treeline; elevating the branding row into the upper sky area (`bottom = 24.dp`) ensures optimal text contrast against the light sky while keeping the treeline and meadow clear below (ATT-6216).
- **Description**:
1. *Compact Header Height Bounding (`REQ-UI-496.1`)*:
   • In `AppNavigationDrawer.kt`, `DrawerHeader()` SHALL specify a total height of `76.dp + statusBarHeight` (reduced from `92.dp + statusBarHeight`).
2. *Bottom Landscape Motif Preservation (`REQ-UI-496.2`)*:
   • The background image `R.drawable.menu_header_background` SHALL remain anchored at `Alignment.BottomCenter` with `ContentScale.FillWidth`, preserving 100% of the meadow, trees, hills, and road landscape motif at the bottom edge while trimming only the empty blue sky at the top.
3. *Branding Row Sky Placement & Rule 72 Icon Invariant (`REQ-UI-496.3`)*:
   • The branding row containing the app launcher icon and title SHALL apply `padding(start = 16.dp, end = 16.dp, top = statusBarHeight, bottom = 24.dp)` with `verticalAlignment = Alignment.CenterVertically`, elevating the icon and title into the open sky area above the dark forest treeline.
   • The Cervélo P3C launcher icon slot SHALL preserve its `46.dp` size and `1.81f` adaptive icon safe-zone scale factor per Rule 72 (`REQ-UI-445.4`), remaining single-line alongside the title without clipping.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Refines `REQ-UI-445.4` (*Navigation Drawer Header Recognition & 9-Locale Tier Naming*) and `REQ-UI-159` (*Navigation Drawer*).
2. *Historical Origin & Commit Trace*: Introduced in `ATT-1083` (legacy header), refined in `ATT-5371` / `ATT-5397` / `ATT-5496` (Cervélo P3C launcher branding), and adjusted in `ATT-5839` (commit `d22358e188150d59fccc929b78015a33d5a365de`) and `ATT-6216`.
3. *Root Reason for Existing Formulation*: `ATT-5839` specified `bottom = 6.dp` with naive vertical centering within the 76dp net height band, which unintentionally pushed the branding row down into the dark treeline at the bottom of the 4:3 background image.
4. *Preservation of Core Invariants*: `Alignment.BottomCenter` landscape anchoring, single-line branding layout, `46.dp` icon size, `1.81f` safe-zone scaling, `76.dp + statusBarHeight` overall height bounding (preserving *Info & Unterstützung* clearance), dynamic status bar handling, and 9-language localization parity MUST remain strictly preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* the Navigation Drawer on a standard portrait mobile screen,
• *When* the drawer is opened,
• *Then* `DrawerHeader` renders with net height `76.dp` below `statusBarHeight`, and all navigation groups down to and including `Info & Unterstützung` are fully visible without bottom vertical clipping.
• *Given* `DrawerHeader` with `R.drawable.menu_header_background`,
• *When* the header is drawn,
• *Then* `alignment = Alignment.BottomCenter` anchors the bottom landscape artwork (trees, hills, meadow) at the bottom edge, trimming only top sky.
• *Given* the Navigation Drawer opened in `AppNavigationDrawer`,
• *When* `DrawerHeader` renders the branding row,
• *Then* the launcher icon and title are positioned vertically in the open sky area above the dark forest treeline with `bottom = 24.dp` padding, providing clear text contrast against the light sky background.
• *Given* any supporter tier icon,
• *When* rendered in `DrawerHeader`,
• *Then* the launcher icon retains `46.dp` size and `1.81f` scaling without mid-row distortion.

**Invariants**: `Alignment.BottomCenter` preserved; `46.dp` / `1.81f` icon scaling preserved; edge-to-edge status bar padding preserved; *Info & Unterstützung* vertical clearance preserved; 100% test pass rate.

### REQ-UI-497: EditWorkoutScreen Hint Localization, Punctuation Normalization, Label Capitalization & Strava Row Layout Parity [Verified]
- **Tests**: `TST-UI-497`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `EditWorkoutScreen.kt`, `strings.xml` (all 9 locales)
- **Rationale**: In `EditWorkoutScreen.kt`, visual audits (`audit_04_einheiten.png`) revealed untranslated lowercase placeholders (`description`, `goal`, `method` in `values-de`), a trailing colon on `R.string.Sport` clashing with borderless floating labels, uncapitalized `stravaUpload` ("hochladen zu Strava"), and a Strava row layout bug where the logo placed before the checkbox turns solid grey (`■`) when disabled due to `tint = Color.Gray` and shifts the checkbox out of vertical alignment with sibling options (`Commute`, `Race`).
- **Description**:
1. *Workout Hint Localization & Capitalization (`REQ-UI-497.1`)*:
   • In `res/values-de/strings.xml`, `hint_workout_description`, `hint_workout_goal`, and `hint_workout_method` SHALL be localized to `Beschreibung`, `Ziel`, and `Methode`.
   • Across all 9 supported application locales (EN, DE, ES, FR, IT, JA, NL, PL, PT), initial letters of workout hint strings SHALL be capitalized to match Title/Sentence Case conventions.
2. *Sport Punctuation Normalization & Strava Upload Label Capitalization (`REQ-UI-497.2`)*:
   • `R.string.Sport` SHALL NOT contain trailing colons across any of the 9 locales (e.g. `Sport`, `Deporte`, `スポーツ`, `Desporto`), providing clean container labels in `EditWorkoutScreen`, `ManualClusterScreen`, and `WorkoutClusterHeatmapScreen`.
   • `R.string.stravaUpload` SHALL be capitalized across all 9 locales (e.g. German: `Zu Strava hochladen`, English: `Upload to Strava`).
3. *Strava Row Layout Ordering & Disabled Alpha (`REQ-UI-497.3`)*:
   • In `EditWorkoutScreen.kt`, the Strava row SHALL place `Checkbox` first (left-aligned with `commute` and `race`), followed by Strava logo `Icon` (`size = 18.dp`, `tint = Color.Unspecified`, `Modifier.alpha(if (stravaMappingAvailable) 1f else 0.38f)`), `Spacer(width = 8.dp)`, and label `Text`.
   • When Strava mapping is unavailable (`!stravaMappingAvailable`), the logo SHALL be dimmed via alpha without masking raster pixels into a solid square (`■`).

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Refines `REQ-LOC-001` (*9-Language Localization Parity*), `REQ-UI-122` (*Translation Coverage*), and `REQ-UI-239` (*Workout Metadata & Checkbox Presentation*).
2. *Historical Origin & Commit Trace*: Introduced in `ATT-2005` (workout metadata fields) and updated in `ATT-4801` (Strava upload integration).
3. *Root Reason for Existing Formulation*: Placeholder strings were left untranslated during rapid prototyping; trailing colon on `Sport:` was inherited from legacy pre-Compose layouts; `tint = Color.Gray` was applied generically without accounting for multi-color raster drawables.
4. *Preservation of Core Invariants*: Strava upload state binding (`uploadToStrava`), enablement guard (`stravaMappingAvailable`), and dropdown functionality across all screens consuming `R.string.Sport` MUST remain strictly preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* the Edit Workout screen in German locale (`de`),
• *When* the workout metadata fields render,
• *Then* hint labels display `Beschreibung`, `Ziel`, and `Methode` with proper capitalization.
• *Given* `R.string.Sport` and `R.string.stravaUpload` in any of the 9 locales,
• *When* rendered,
• *Then* `Sport` contains no trailing colon and `stravaUpload` begins with a capital letter.
• *Given* the Strava upload option in `EditWorkoutScreen`,
• *When* rendered,
• *Then* `Checkbox` is the first element in the row (aligned with `Commute` and `Race`), followed by the Strava icon.
• *Given* Strava mapping is unavailable (`!stravaMappingAvailable`),
• *When* the Strava row renders,
• *Then* the logo is dimmed via `0.38f` alpha with the white chevron visible, and zero solid grey squares (`■`) appear.

**Invariants**: Strava upload enablement strictly tied to `stravaMappingAvailable`; 9-language localization parity preserved; 100% test pass rate.

### REQ-UI-498: WorkoutDetailTabbedContent Header Click Decoupling & MapDetailLayout Sheet Chrome Suppression [Verified]
- **Tests**: `TST-UI-498`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `WorkoutDetailTabbedContent.kt`, `MapDetailLayout.kt`, `TrackOnMapScreen.kt`
- **Rationale**: In `WorkoutDetailTabbedContent.kt`, clicking anywhere on the header card surface inadvertently triggered the Edit Workout dialog (`Trainingseinheit überarbeiten`), creating accidental triggers when inspecting details or switching tabs. In addition, `MapDetailLayout` conflated `!useStatusBarsPadding` with bottom-sheet presentation, rendering an orphan bottom-sheet drag handle pill (`—`) and rounded top corners in full-screen tabbed layouts (`audit_05_under_edit.png`):
- **Description**:
1. *WorkoutDetailTabbedContent Header Click Decoupling (`REQ-UI-498.1`)*:
   • In `WorkoutDetailTabbedContent.kt`, `WorkoutHeader` SHALL receive `onClicked = null`, disabling whole-card click navigation and reserving edit actions strictly to the dedicated Edit pencil icon button (`onEditWorkout`).
2. *MapDetailLayout Bottom-Sheet Chrome Decoupling (`REQ-UI-498.2`)*:
   • `MapDetailLayout` SHALL accept `isBottomSheet: Boolean = !useStatusBarsPadding`.
   • The drag handle pill (`MinimumDragHandle()`) and rounded top sheet shape (`BottomSheetDesign.SheetShape`) SHALL be rendered strictly when `isBottomSheet == true`.
   • When `isBottomSheet == false`, the drag handle SHALL be suppressed and the surface container SHALL use flat top corners (`RectangleShape`).
3. *Full-Screen Tabbed Content Chrome Suppression (`REQ-UI-498.3`)*:
   • In `WorkoutDetailTabbedContent.kt` (overview, map/charts, and full tabs) and `TrackOnMapScreen.kt`, `MapDetailLayout` invocations SHALL explicitly pass `isBottomSheet = false`, ensuring no drag handle pill or rounded sheet corners appear beneath tab bars.
4. *Preservation of System Invariants (`REQ-UI-498.4`)*:
   • Modal bottom-sheet callers (`MapScreenWithTrack.kt:763`, `LiveSegmentSheet.kt:49`) SHALL preserve visible drag handles and `BottomSheetDesign.SheetShape` per Rule 74 (`isBottomSheet = true`).
   • `ATT-5851` floating controls status bar offset (`floatingControlsTopOffsetPx`) calculation SHALL remain strictly tied to `useStatusBarsPadding` and unaffected by `isBottomSheet`.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Refines `REQ-UI-408` (*Multi-Tab Workout Details*), `REQ-UI-148` & `Rule 74` (*Bottom Sheet Drag Handle Contract*), and `REQ-UI-491` (*MapDetailLayout Status Bar Inset Clearance*). Targets `WorkoutDetailTabbedContent.kt`, `MapDetailLayout.kt`, and `TrackOnMapScreen.kt`.
2. *Historical Origin & Commit Trace*: `WorkoutHeader` click binding inherited from `WorkoutSummariesWithMapCard` in `ATT-4081`; `MapDetailLayout` drag handle and sheet shape heuristic (`!useStatusBarsPadding`) introduced in `ATT-5485` and refined in `ATT-5851`.
3. *Root Reason for Existing Formulation*: `WorkoutHeader` originally supported whole-card click for list drill-down; `MapDetailLayout` assumed any layout not needing status bar padding was a modal/peek bottom sheet.
4. *Preservation of Core Invariants*: Dedicated edit pencil icon continues opening `EditWorkoutScreen`; modal bottom sheets in `MapScreenWithTrack` retain Rule 74 drag handles; `ATT-5851` floating controls clearance is 100% preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an athlete viewing Workout Details in `WorkoutDetailTabbedContent`,
• *When* tapping anywhere on the `WorkoutHeader` card surface,
• *Then* no Edit Workout dialog opens.
• *Given* an athlete viewing Workout Details,
• *When* tapping the dedicated Edit pencil icon button on `WorkoutHeader`,
• *Then* `EditWorkoutScreen` opens as expected.
• *Given* full-screen tabbed layouts in `WorkoutDetailTabbedContent` or `TrackOnMapScreen`,
• *When* rendered,
• *Then* no drag handle pill (`—`) appears below the tab bar, and the sheet surface uses flat top corners.
• *Given* modal bottom sheets in `MapScreenWithTrack`,
• *When* rendered,
• *Then* the Rule 74 drag handle pill is visible and top corners are rounded per `BottomSheetDesign.SheetShape`.

**Invariants**: Dedicated edit pencil button functional; Rule 74 bottom sheet drag handles preserved; ATT-5851 floating controls clearance intact; 100% test pass rate.

### REQ-UI-499: WorkoutSummary & WorkoutSectionType: Reorderable WorkoutDetails & Photos-First Card Ordering [Verified]
- **Tests**: `TST-UI-499`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `WorkoutSectionType.kt`, `WorkoutDetailTabConfig.kt`, `MyPreferenceManager.kt`, `WorkoutSummary.kt`, `WorkoutMasksAndCardsSection.kt`, `TrackOnMapScreen.kt`
- **Rationale**: In the Workout Summary list card (`WorkoutSummary.kt`), `WorkoutDetails` (*Zeit aktiv / Distanz / Höhenmeter*) was previously hardcoded above the dynamic section loop alongside `WorkoutHeader`. This prevented athletes from placing Photos (`PHOTOS`) or other sections directly below `WorkoutHeader` and above `WorkoutDetails`. The system SHALL unify `WorkoutSectionType` with `WorkoutDetailSectionType`, making `WorkoutDetails` an independent, reorderable section in `WorkoutSectionType` while keeping `WorkoutHeader` as the sole permanent top identity anchor (ATT-6213):
- **Description**:
1. *Section Domain Model Unification (`REQ-UI-499.1`)*:
   • `WorkoutSectionType` SHALL define enum constant `DETAILS(R.string.workout_detail_section_details)`.
   • In `WorkoutDetailTabConfig.kt`, `WorkoutDetailSectionType.toWorkoutSectionTypeOrNull()` SHALL map `WorkoutDetailSectionType.DETAILS` to `WorkoutSectionType.DETAILS`.
   • `WorkoutSectionType.toWorkoutDetailSectionType()` SHALL map `WorkoutSectionType.DETAILS` to `WorkoutDetailSectionType.DETAILS`.
   • `WorkoutSectionType.DEFAULT_ORDER` SHALL contain 12 elements with `DETAILS` at index 0: `[DETAILS, DESCRIPTION, EXTREMA, PHOTOS, MAP, ELEVATION, CHARTS, ZONES, GRADE_DISTRIBUTION, LAPS, EXPORT_STATUS, STRAVA]`.
2. *Backwards-Compatible Preference Migration (`REQ-UI-499.2`)*:
   • In `WorkoutSectionType.fromSerializedString(serialized: String?)`, if the deserialized list does not contain `DETAILS`:
     - If the first element is `PHOTOS` (`parsed.firstOrNull() == WorkoutSectionType.PHOTOS`), `DETAILS` SHALL be inserted at index 1 (preserving `PHOTOS` above `DETAILS`).
     - Otherwise, `DETAILS` SHALL be inserted at index 0 (preserving the classic layout where details precede all secondary sections).
3. *Visibility Preference Persistence (`REQ-UI-499.3`)*:
   • `WorkoutCardSectionPreferences` SHALL include `val showDetails: Boolean = true`.
   • `MyPreferenceManager` SHALL persist `WORKOUT_CARD_SHOW_DETAILS` to DataStore (defaulting to `true`).
4. *Dynamic Section Loop & Divider Execution (`REQ-UI-499.4`)*:
   • In `WorkoutSummary.kt`, `WorkoutHeader` SHALL remain permanently anchored at the top of the card.
   • The hardcoded `WorkoutDetails(...)` invocation above `workoutSectionsOrder.forEach` SHALL be removed.
   • `WorkoutDetails(...)` SHALL be rendered dynamically within `workoutSectionsOrder.forEach` under branch `WorkoutSectionType.DETAILS`, gated by `preferences.showDetails`.
   • Exactly one `HorizontalDivider` SHALL separate `WorkoutHeader` from the first visible section; sections rendering first SHALL NOT produce duplicate consecutive dividers.
5. *Settings Matrix Feature Row & Modularity (`REQ-UI-499.5`)*:
   • `WorkoutMasksAndCardsSection.kt` SHALL map `WorkoutSectionType.DETAILS` to `MatrixFeatureRow` with localized title `R.string.workout_detail_section_details` and checkbox bound to `showDetails`.
   • `WorkoutMasksAndCardsSection.kt` SHALL remain strictly under 400 lines of code.
6. *Exhaustive Caller Handling (`REQ-UI-499.6`)*:
   • In `TrackOnMapScreen.kt`, `WorkoutSectionType.DETAILS` SHALL be handled in `preMapSections` and `postMapSections` `when` blocks to ensure exhaustive enum coverage without runtime exception.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Refines and amends `REQ-UI-255` (*Customizable Section Reordering for Workout Summaries and Details*) and `REQ-UI-210` (*Workout Card Section Visibility Matrix*). Targets `WorkoutSectionType.kt`, `WorkoutDetailTabConfig.kt`, `MyPreferenceManager.kt`, `WorkoutSummary.kt`, `WorkoutMasksAndCardsSection.kt`, and `TrackOnMapScreen.kt`.
2. *Historical Origin & Commit Trace*: Sprint 2026-40.11 (`ATT-1714`, commit `56375eeb7c13`) established `WorkoutHeader` and `WorkoutDetails` as mandatory non-configurable anchors; `ATT-2176` added dynamic reordering for secondary sections but kept `WorkoutDetails` pinned above the loop; `ATT-5474` added `PHOTOS` to `WorkoutSectionType`.
3. *Root Reason for Existing Formulation*: Core summary metrics (`WorkoutDetails`) were originally viewed as inseparable from workout identity and required to be immediately visible at the top of every card.
4. *Preservation of Core Invariants*: `WorkoutHeader` remains permanently and non-configurably anchored at the top of every summary card. `showDetails` defaults to `true` and `DETAILS` defaults to index 0, ensuring 100% visual layout preservation for existing installs while unlocking user reordering (e.g. photos above details).

**Acceptance Criteria (Given-When-Then)**:
• *Given* a workout with attached photos and `workoutSectionsOrder` where `PHOTOS` precedes `DETAILS`,
• *When* `WorkoutSummary` renders the workout card,
• *Then* `WorkoutHeader` appears at the top, followed immediately by `WorkoutPhotoStrip` (`PHOTOS`), and `WorkoutDetails` renders below the photos.
• *Given* the Workout Card configuration in `WorkoutMasksAndCardsSection`,
• *When* the user inspects the reorderable section list,
• *Then* `DETAILS` (`R.string.workout_detail_section_details`) appears with a visibility checkbox (`showDetails`) and drag handle, allowing drag-and-drop reordering.
• *Given* a legacy serialized order string without `DETAILS` where `PHOTOS` was placed at index 0,
• *When* `WorkoutSectionType.fromSerializedString` deserializes the string,
• *Then* `DETAILS` is inserted at index 1, preserving `PHOTOS` at index 0 above `DETAILS`.
• *Given* a legacy serialized order string without `DETAILS` where `DESCRIPTION` or other sections were at index 0,
• *When* `WorkoutSectionType.fromSerializedString` deserializes the string,
• *Then* `DETAILS` is inserted at index 0, maintaining the details-first layout.

**Invariants**: `WorkoutHeader` permanently anchored at top; backward-compatible preference migration; `WorkoutMasksAndCardsSection.kt` < 400 lines; 100% 9-language localization parity; 100% clean-room test suite pass rate.

### REQ-UI-500: MapDetailLayout Unified Top-End Floating Actions Stacking & Adaptive Layers Button Placement [Verified]
- **Tests**: `TST-UI-500`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `MapDetailLayout.kt`, `WorkoutDetailTabbedContent.kt`, `TrackOnMapScreen.kt`, `RouteOnMapScreen.kt`
- **Rationale**: In `MapDetailLayout.kt`, the floating Share FAB was previously placed as an independent `Surface` at `Alignment.TopEnd` with `padding(16.dp)`, while callers with secondary actions (`TrackOnMapScreen.kt` and `RouteOnMapScreen.kt`) independently positioned their Layers buttons via hardcoded `padding(top = 76.dp, end = 16.dp)` inside `overlay`. When ATT-5809 suppressed the floating Share button for workouts (`showFloatingShareButton = workoutData == null`), a 60dp empty gap was left above the Layers button on `TrackOnMapScreen.kt`. The system SHALL unify top-end floating action stacking in `MapDetailLayout`, enabling the Layers button to adaptively dock at `top = 16.dp` when the Share button is absent, and naturally stack at `top = 76.dp` when the Share button is present, without hardcoded offsets in caller screens (ATT-6214):
- **Description**:
1. *MapDetailLayout Unified Top-End Actions Slot (`REQ-UI-500.1`)*:
   • `MapDetailLayout` SHALL accept a parameter `topEndActions: @Composable (ColumnScope.() -> Unit)? = null`.
   • Inside the floating controls `Box(Modifier.fillMaxSize().padding(top = controlsTopOffsetDp))`, all top-end actions SHALL be rendered within a single `Column`:
     ```kotlin
     Column(
         modifier = Modifier
             .align(Alignment.TopEnd)
             .padding(16.dp),
         verticalArrangement = Arrangement.spacedBy(16.dp),
         horizontalAlignment = Alignment.End
     ) {
         if (showFloatingShareButton) {
             // Share button Surface (44.dp)
         }
         topEndActions?.invoke(this)
     }
     ```
2. *WorkoutDetailTabbedContent Top-End Actions Forwarding (`REQ-UI-500.2`)*:
   • `WorkoutDetailTabbedContent` SHALL accept parameter `mapTopEndActions: @Composable (ColumnScope.() -> Unit)? = null` (defaulting to `null`) and forward it directly to `MapDetailLayout.topEndActions` across both single-pane and split-pane configurations.
3. *TrackOnMapScreen Adaptive Layers Placement (`REQ-UI-500.3`)*:
   • In `TrackOnMapScreen.kt` (single-pane and split-pane layouts), the technical tracks Layers button SHALL be migrated from `mapOverlay` to `mapTopEndActions` without caller-side `align(Alignment.TopEnd)` or hardcoded `padding(top = 76.dp, end = 16.dp)`.
   • When viewing a workout map (`showFloatingShareButton == false`), the Layers button SHALL dock at `top = 16.dp` (plus `controlsTopOffsetDp` status bar inset).
4. *RouteOnMapScreen Top-End Action Migration (`REQ-UI-500.4`)*:
   • In `RouteOnMapScreen.kt`, the route overlay Layers button SHALL be passed via `topEndActions` to `MapDetailLayout` without hardcoded `padding(top = 76.dp, end = 16.dp)`.
   • When the route Share button is visible (`showFloatingShareButton == true`), the Layers button SHALL automatically stack 16dp below the 44dp Share button, maintaining `top = 76.dp` layout parity with zero hardcoding.
5. *Preservation of System Invariants (`REQ-UI-500.5`)*:
   • REQ-UI-491 status bar inset clearance (`controlsTopOffsetDp`) SHALL govern the top offset of both the Share button and all `topEndActions`.
   • 44dp touch target sizing, `CircleShape`, 6dp shadow elevation, `TTAlpha.Overlay` surface styling, and dropdown menu functionality MUST remain strictly preserved.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Amends `REQ-UI-308` (Clause 2, *Route Map Layers Menu UI & Ergonomic Parity*) and complements `REQ-UI-491` (*Status Bar Clearance for Floating Map Controls*). Targets `MapDetailLayout.kt`, `WorkoutDetailTabbedContent.kt`, `TrackOnMapScreen.kt`, and `RouteOnMapScreen.kt`.
2. *Historical Origin & Commit Trace*: Ticket `ATT-2763` (`REQ-UI-308`, hardcoded `76.dp` in `RouteOnMapScreen`), `ATT-5809` (suppressed floating Share FAB for workouts), and `ATT-5851` (`REQ-UI-491`, `controlsTopOffsetDp`).
3. *Root Reason for Existing Formulation*: `top = 76.dp` was originally hardcoded in individual caller screens as a static offset (`16.dp top + 44.dp share + 16.dp space = 76.dp`) before `showFloatingShareButton` became dynamic in ATT-5809.
4. *Preservation of Core Invariants*: Status bar inset clearance (`controlsTopOffsetDp`), 44dp button sizes, layer dropdown toggle logic, permanent route polyline anchoring, and 100% test pass rate remain strictly preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* `TrackOnMapScreen` displaying a workout track where `showFloatingShareButton == false`,
• *When* the map canvas is rendered,
• *Then* the technical tracks Layers button docks at `top = 16.dp` (plus `controlsTopOffsetDp`), leaving zero empty gap above it.
• *Given* `RouteOnMapScreen` displaying a route where `showFloatingShareButton == true`,
• *When* the map canvas is rendered,
• *Then* the Share button renders at `top = 16.dp` and the route Layers button stacks directly below it at `top = 76.dp` via `Arrangement.spacedBy(16.dp)`.
• *Given* `MapDetailLayout` with both Share button and `topEndActions`,
• *When* system status bars are present,
• *Then* both Share and `topEndActions` are padded by `controlsTopOffsetDp`, preserving REQ-UI-491 status bar clearance.

**Invariants**: `controlsTopOffsetDp` status bar clearance; 44dp button touch targets; `CircleShape` with `TTAlpha.Overlay`; dropdown menus interactive; 100% test pass rate.

### REQ-UI-501: SupporterScreen & SupporterComponents: Tier Hierarchy & Lower Tier De-Emphasis [Verified]
- **Tests**: `TST-UI-501`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/supporter/SupporterComponents.kt`, `SupporterScreen.kt`, `SupporterTier.kt`, `res/values*/strings_monetization.xml`, `SupporterPlanCardHierarchyTest.kt`
- **Rationale**: In `SupporterScreen.kt` and `SupporterComponents.kt` (`SupporterPlanCard`), lower tiers (Bronze) currently render a filled primary `Button` ("Fördern") when a higher tier (Silver/Gold/Diamond) is active, causing visual competition. Furthermore, the "Empfehlung" badge on Silver is rendered unconditionally, cluttering the view for existing Silver/Gold/Diamond athletes.
- **Description**:
  1. *Effective Tier Parameter & Visual Plan Hierarchy (`REQ-UI-501.1`)*:
     - `SupporterPlanCard` in `SupporterComponents.kt` SHALL accept `effectiveTier: SupporterTier` (passed from `SupporterScreen.kt`). Based on `plan.tier.rank` vs `effectiveTier.rank`, cards SHALL render in one of three mutually exclusive visual states:
       a. *Lower Tier / Downgrade* (`effectiveTier.isPro && effectiveTier != SupporterTier.PIONEER && plan.tier.rank < effectiveTier.rank`): standard `surface` container, thin `0.5.dp` `outlineVariant` border, slightly muted typography, suppressed recommendation badge, and subtle `OutlinedButton` labeled "Wechseln" / "Switch" (`R.string.supporter_plan_button_switch`).
       b. *Current Active Tier* (`plan.tier == effectiveTier`): highlighted `1.5.dp` `primary` border, subtle container tint (`MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)`), suppressed recommendation badge, and disabled `OutlinedButton` labeled "Aktiv" / "Active" (`R.string.supporter_plan_button_active`).
       c. *Higher Tier / Upgrade* (or `effectiveTier in (FREE, PIONEER)`): filled primary `Button` labeled "Fördern" / "Support" (`R.string.supporter_plan_button_support`).
  2. *Conditional Recommendation Badge Suppression (`REQ-UI-501.2`)*:
     - The "Empfehlung" badge (`R.string.supporter_recommended_badge`) and recommendation container highlight on `silver-yearly` (`plan.isRecommended == true`) SHALL be rendered strictly when `effectiveTier.rank < SupporterTier.SILVER.rank` (Free, Pioneer, Bronze).
     - For athletes with `effectiveTier.rank >= SupporterTier.SILVER.rank` (Silver, Gold, Diamond), the badge and recommendation highlight SHALL be suppressed.
  3. *Domain-Partitioned 9-Language Localization Parity (`REQ-UI-501.3`)*:
     - `supporter_plan_button_switch` SHALL be partitioned into domain resource `strings_monetization.xml` across all 9 supported locales (`values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`) with 100% translation parity.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Amends `REQ-SET-086` / `REQ-UI-445.4` (`SupporterPlanCard` presentation). Targets `SupporterComponents.kt`, `SupporterScreen.kt`, and `strings_monetization.xml`.
2. *Historical Origin & Commit Trace*: Ticket `ATT-5371` (`REQ-SET-086.4`), `ATT-5496` (`REQ-UI-445.4`, 44dp tier emblems).
3. *Root Reason for Existing Formulation*: Recommendation highlights were created to guide prospective subscribers. Suppressing them once Silver is attained preserves conversion intent while eliminating noise. Downgrades remain possible via subtle "Wechseln" without competing with upgrades.
4. *Preservation of Core Invariants*: Full-color 44dp emblems remain visible on all cards; existing purchase action handlers and Google Play subscription linking remain unaffected; 100% test pass rate.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an active Silver supporter (`effectiveTier == SupporterTier.SILVER`),
• *When* opening `SupporterScreen`,
• *Then* Bronze cards render subtle `OutlinedButton` ("Wechseln") with surface container, Silver renders disabled `OutlinedButton` ("Aktiv") without "Empfehlung" badge, and Gold/Diamond render primary `Button` ("Fördern").
• *Given* an active Gold or Diamond supporter,
• *When* opening `SupporterScreen`,
• *Then* Silver renders subtle `OutlinedButton` ("Wechseln") without "Empfehlung" badge or primary highlight.
• *Given* a Free or Pioneer athlete,
• *When* opening `SupporterScreen`,
• *Then* Silver renders the "Empfehlung" badge and container highlight, and all non-current plans render primary `Button` ("Fördern").

**Invariants**: Full-color 44dp tier emblems (`REQ-UI-445.4`); single-line buttons without text wrapping (`Rule 38`); 9-locale translation parity; 100% test pass rate.

### REQ-UI-502: SupporterScreen: Neutral Pioneer Early Adopter Recognition Copy [Verified]
- **Target Release**: `V5.0.0`
- **Tests**: `TST-UI-502`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `res/values*/strings.xml`, `SupporterComponents.kt`, `SupporterCopyContractTest.kt`
- **Rationale**: In `SupporterComponents.kt` (`PioneerStatusBanner`), `R.string.pioneer_thank_you_body` specifically referenced "the early ANT+ days" across all 9 locales. Early adopters who used Bluetooth LE, GPS, or internal phone sensors before V5.0.0 were alienated by ANT+-specific phrasing. The copy SHALL express neutral, appreciative gratitude for tracking workouts with an earlier version of the app.
- **Description**:
  1. *Neutral Pioneer Gratitude Copy (`REQ-UI-502.1`)*:
     - `pioneer_thank_you_body` SHALL express appreciation for recording workouts with an earlier version of `aTrainingTracker` and state that unlimited sensors and all future features are permanently unlocked for the athlete.
     - The copy SHALL contain zero references to "ANT+" across all 9 supported locales (`values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`).
  2. *Prohibition of Decorative Inline Emojis (`REQ-UI-502.2`)*:
     - Pursuant to `ATT-5400`, `pioneer_thank_you_body` SHALL NOT contain decorative inline Unicode emojis.
  3. *9-Language Localization Parity (`REQ-UI-502.3`)*:
     - The updated string SHALL maintain 100% localization parity across all 9 supported application locales without format specifiers (`%s`, `%d`).

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Amends `REQ-SET-085.3` / `REQ-UI-445.3` (`PioneerStatusBanner` copy). Targets `res/values*/strings.xml` and `SupporterComponents.kt`.
2. *Historical Origin & Commit Trace*: Ticket `ATT-5371` (commit `31da409a`), which extracted `pioneer_thank_you_body` and added dynamic since-year formatting.
3. *Root Reason for Existing Formulation*: Early copy colloquially assumed early adopters used ANT+ because of the app's historical origin. However, many athletes tracked with BLE/GPS in early versions.
4. *Preservation of Core Invariants*: Lifetime Pro privileges (`isProActive() == true`), unlimited sensor pairing, dynamic `pioneerSinceYear` badge, 36dp vector emblem, and 9-locale parity remain 100% preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an athlete with Pioneer status opening `SupporterScreen`,
• *When* `PioneerStatusBanner` is displayed,
• *Then* `pioneer_thank_you_body` expresses appreciation for recording workouts with an earlier version of `aTrainingTracker` and contains zero references to "ANT+" and zero decorative emojis across all 9 supported locales.
• *Given* the localization parity test suite (`TST-LOC-001` / `TST-UI-502.2`),
• *When* executed,
• *Then* all 9 locales pass with 100% translation parity and zero format specifier errors.

**Invariants**: Lifetime Pro privileges (`isProActive() == true`); dynamic since-year badge formatting; 36dp tier emblem; zero format specifier mismatches; 100% test pass rate.

### REQ-UI-503: DisplaySettingsDialog: Descriptive Subtitles and Capitalized Titles for Top Display Toggles [Verified]
- **Target Release**: `V5.0.0`
- **Tests**: `TST-UI-503`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `DisplaySettingsDialog.kt`, `res/values*/strings.xml`, `DisplaySettingsTest.kt`
- **Rationale**: In `DisplaySettingsDialog.kt`, the top three display option toggles (`forcePortrait`, `keepScreenOn`, and `noUnlocking`) historically lacked explanatory subtitle descriptions, while legacy title strings started with lowercase letters in multiple locales. The system SHALL display clear, localized subtitle descriptions for all four top display toggles and ensure consistent title capitalization across all 9 supported locales.
- **Description**:
  1. *Descriptive Subtitle Wiring (`REQ-UI-503.1`)*:
     - `DisplaySettingsDialog.kt` SHALL supply descriptive subtitles to the top three `DisplayOptionToggle` instances via `forcePortraitSummary`, `prefsKeepScreenOnSummary`, and `prefsNoUnlockingSummary`.
     - Subtitles SHALL explain vertical portrait orientation lock, active tracking wake-lock retention, and lock-screen overlay access.
  2. *Consistent Title Capitalization (`REQ-UI-503.2`)*:
     - `prefsKeepScreenOnTitle` and `prefsNoUnlockingTitle` SHALL begin with a capital letter across all supported locales (e.g., EN: *"Keep screen on"*, *"Keep screen unlocked"*).
  3. *9-Language Localization Parity (`REQ-UI-503.3`)*:
     - All 3 summary strings SHALL be defined across all 9 supported application locales (`values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`) with 100% parity, zero format specifiers, and zero decorative emojis (`ATT-5400`).

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Amends `REQ-UI-149.1` (`DisplaySettingsDialog` presentation). Targets `DisplaySettingsDialog.kt` and `res/values*/strings.xml`.
2. *Historical Origin & Commit Trace*: Ticket `ATT-1016` (commit `3aa0845a`) and `ATT-3549` (commit `f9d7842e`).
3. *Root Reason for Existing Formulation*: Direct initial port of legacy XML preference entries where summaries were omitted and titles had lowercase casing in older Android versions.
4. *Preservation of Core Invariants*: Preference keys (`DEFAULT_DISPLAY_OPTIONS`), SharedPreferences storage, boolean getters in `TrainingApplication`, and Material 3 `DisplayOptionToggle` structure remain 100% preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* the athlete opens `DisplaySettingsDialog`,
• *When* inspecting the top four display option switches,
• *Then* all four toggles (`forcePortrait`, `keepScreenOn`, `noUnlocking`, `volumeKeysSwitchCockpitTabs`) render a capitalized title and a helpful, localized subtitle description.
• *Given* the localization parity test suite (`TST-LOC-001` / `TST-UI-503.2`),
• *When* executed,
• *Then* all 9 locales pass with 100% parity and zero format specifier errors.

**Invariants**: SharedPreferences key string set `DEFAULT_DISPLAY_OPTIONS`; `TrainingApplication` display getters; Material 3 typography and token hierarchy; zero format specifiers; 100% test pass rate.

### REQ-UI-504: SupporterPlanCard: Compact Action Button Content Padding & Title Row Overflow Protection [Verified]
- **Target Release**: `V5.0.0`
- **Tests**: `TST-UI-504`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/supporter/SupporterComponents.kt`, `SupporterPlanCardHierarchyTest.kt`
- **Rationale**: In `SupporterComponents.kt` (`SupporterPlanCard`), `OutlinedButton` and `Button` inherit default Material 3 horizontal padding (`24.dp`), causing `"Wechseln"` (~108dp) to consume excessive horizontal space. On compact viewports, the German compound title *"Bronze-Unterstützer"* wraps before its trailing letter (*"Bronze-Unterstütze"* / *"r"*), producing an awkward single-letter orphan line. The system SHALL apply compact horizontal button padding (`16.dp`) and enforce single-line title truncation with ellipsis.
- **Description**:
  1. *Compact Action Button Padding (`REQ-UI-504.1`)*:
     - In `SupporterComponents.kt` (`SupporterPlanCard`), all action button composables (`OutlinedButton` for `CURRENT_TIER` and `LOWER_TIER`, `Button` for `UPGRADE_TIER`) SHALL declare explicit `contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)` instead of inheriting default Material 3 padding (`24.dp`), reclaiming 16dp of horizontal width for the card title row.
  2. *Single-Line Title Row & Overflow Ellipsis Safeguard (`REQ-UI-504.2`)*:
     - In `SupporterComponents.kt` (`SupporterPlanCard`), the plan tier title `Text` composable SHALL enforce `maxLines = 1` and `overflow = TextOverflow.Ellipsis`, preventing awkward single-letter orphan line wrapping on compound tier names across compact viewports.
  3. *Preservation of Visual Hierarchy & Accessibility (`REQ-UI-504.3`)*:
     - Single-line button text without wrapping (`Rule 38`), 44dp tier emblems (`REQ-UI-445.4`), tier hierarchy state resolution (`REQ-UI-501`), click targets, and 9-language localization parity SHALL remain 100% preserved.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Amends `REQ-UI-501.1` (`SupporterPlanCard` presentation). Targets `SupporterComponents.kt`.
2. *Historical Origin & Commit Trace*: Ticket `ATT-6286` (commit `7d72425c`), which introduced `PlanCardHierarchyState.LOWER_TIER` with `"Wechseln"` `OutlinedButton`.
3. *Root Reason for Existing Formulation*: Default M3 button padding was retained during initial hierarchy implementation without verifying compound German string width budgets.
4. *Preservation of Core Invariants*: Full-color 44dp emblems, hierarchy states, click handlers, and 100% unit test pass rate remain preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* `SupporterPlanCard` rendered in German for Bronze with an active Silver subscription,
• *When* inspecting the card title and `"Wechseln"` button,
• *Then* the button uses `16.dp` horizontal padding and the title *"Bronze-Unterstützer"* renders without single-letter line wrapping.
• *Given* `SupporterPlanCard` across all hierarchy states (`CURRENT_TIER`, `LOWER_TIER`, `UPGRADE_TIER`),
• *When* rendered on compact displays,
• *Then* all action buttons maintain `PaddingValues(horizontal = 16.dp, vertical = 8.dp)` and card titles truncate with `TextOverflow.Ellipsis` if horizontal space is exhausted.

**Invariants**: 44dp emblem size; single-line button text (`Rule 38`); visual hierarchy states (`REQ-UI-501`); 100% unit test pass rate.

### REQ-UI-505: WorkoutClusterSplitSheet Embedded Color-Coded Map Preview & List Context Menu Split Exclusion [Verified]
- **Target Release**: `V5.0.0`
- **Tests**: `TST-UI-505`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/clusters/WorkoutClusterSplitSheet.kt`, `app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/PathPreviewMap.kt`, `app/src/main/java/com/atrainingtracker/trainingtracker/ui/clusters/WorkoutClustersViewModel.kt`, `app/src/main/java/com/atrainingtracker/trainingtracker/ui/clusters/WorkoutClusterComponents.kt`, `app/src/main/java/com/atrainingtracker/trainingtracker/ui/clusters/WorkoutClustersList.kt`, `app/src/main/java/com/atrainingtracker/trainingtracker/ui/clusters/WorkoutClustersTabsScreen.kt`, `app/src/main/java/com/atrainingtracker/trainingtracker/ui/clusters/WorkoutClustersScreen.kt`, `app/src/main/java/com/atrainingtracker/trainingtracker/ui/clusters/WorkoutClusterHeatmapScreen.kt`
- **Rationale**: When opening `WorkoutClusterSplitSheet` (`AppModalBottomSheet`) from `WorkoutClusterHeatmapScreen`, the background heatmap is largely obscured by the modal sheet and darkened scrim; therefore `WorkoutClusterSplitSheet` SHALL embed an interactive, color-coded `PathPreviewMap` directly above the corridor sensitivity slider (`ATT-6318`). Furthermore (`ATT-8464`), exposing *"Split Favorite Route…"* (`R.string.cluster_split_action`) inside the `WorkoutClustersScreen` list-level long-click context menu alongside *"Merge"* and *"Delete"* is semantically mismatched; route splitting SHALL be scoped exclusively to the cluster detail/heatmap view (`WorkoutClusterHeatmapScreen.kt`, `REQ-SET-096.1`).
- **Description**:
  1. *Embedded Color-Coded Map Preview (`REQ-UI-505.1`)*:
     - In `WorkoutClusterSplitSheet.kt`, directly above the corridor sensitivity slider (`cluster_split_corridor_threshold`), the sheet SHALL render an embedded map preview (`PathPreviewMap`) hosted within a rounded surface (`RoundedCornerShape(12.dp)` with height `200.dp`).
     - The embedded map SHALL render all detected corridor variants (`splitVariantPaths`) in their corresponding group colors (`getVariantColor(index)`) matching the numbered variant badges (1, 2, 3...) in the sheet.
     - The embedded map SHALL automatically fit camera bounds to the cluster's bounding box (`cluster.bounds`) or encompass all detected variant polylines with appropriate map padding (`20.dp`).
  2. *Live Sensitivity Modulation & Dynamic Map Synchronization (`REQ-UI-505.2`)*:
     - Adjusting the corridor sensitivity slider SHALL dynamically recalculate variant groupings and update the embedded map preview in real time, visually reflecting the redistribution of member workout polylines across primary and secondary corridors.
  3. *Multi-Path Support in PathPreviewMap (`REQ-UI-505.3`)*:
     - `PathPreviewMap.kt` SHALL support rendering multiple polyline paths via `paths: List<MappablePath> = emptyList()` alongside the existing single `path: MappablePath? = null` parameter, computing unified bounding box coordinates across all supplied paths while preserving `liteMode(true)` touch isolation and backward compatibility for all single-path callers.
  4. *List-Level Context Menu Split Exclusion (`REQ-UI-505.4` — Amended by `ATT-8464`)*:
     - In `WorkoutClusterComponents.kt` (`ClusterItem`), the long-click `DropdownMenu` SHALL NOT expose `R.string.cluster_split_action` or `Icons.Default.CallSplit`, and `ClusterItem` SHALL NOT declare `onSplitRequest`, retaining exclusively Merge (`onMergeRequest`, `R.string.cluster_menu_merge`) and Delete (`onDeleteRequest`, `R.string.delete`).
     - `WorkoutClustersList.kt`, `WorkoutClustersTabsScreen.kt`, and `WorkoutClustersScreen.kt` SHALL NOT forward `onSplitRequest`, maintain `clusterToSplit` state, or instantiate `WorkoutClusterSplitSheet`.
  5. *Preservation of Invariants & Scope Boundaries (`REQ-UI-505.5`)*:
     - Invariants: `WorkoutClusterHeatmapScreen.kt` (`CallSplit` header button for `hitCount >= 2`, `REQ-SET-096.1`), `WorkoutClusterSplitSheet`, and `WorkoutClustersViewModel.openSplitPreview()` SHALL remain fully functional; `R.string.cluster_split_action` SHALL remain present in all 9 locales; `WorkoutClusterEngine.kt` clustering heuristics SHALL NOT be modified; single-path `PathPreviewMap` callers (`CreateLapOnMapSheet`, `MoveLapBoundaryOnMapSheet`, `CropWorkoutScreen`, `WorkoutSummary`) SHALL remain 100% backward-compatible.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Amends `REQ-UI-505` (`REQ-UI-505.4`, introduced in `ATT-6318`) targeting `WorkoutClusterComponents.kt`, `WorkoutClustersList.kt`, `WorkoutClustersTabsScreen.kt`, and `WorkoutClustersScreen.kt`.
2. *Historical Origin & Commit Trace*: Split action + `WorkoutClusterSplitSheet` were introduced in `ATT-5900` (`43a25302`, `REQ-SET-096.1`) in `WorkoutClusterHeatmapScreen`; `ATT-6318` (`ea954e51`) embedded `PathPreviewMap` in `WorkoutClusterSplitSheet` and added the list-level long-click entry (`REQ-UI-505.4`).
3. *Root Reason for Existing Formulation*: `ATT-6318` added list-level split access so users could split directly from the cluster list without opening the detail heatmap, and embedded `PathPreviewMap` because the list screen lacked a background map.
4. *Preservation of Core Invariants*: Removing list-level split entry (`REQ-UI-505.4`) restores clean list menu semantics (Merge & Delete only) while preserving 100% of `REQ-UI-505.1–505.3` (embedded map preview in `WorkoutClusterSplitSheet`), `REQ-SET-096.1–096.6` (detail `CallSplit` entry point in `WorkoutClusterHeatmapScreen`), `WorkoutClustersViewModel.openSplitPreview()`, and 9-language parity for `R.string.cluster_split_action`.

**Acceptance Criteria (Given-When-Then)**:
• *Given* `WorkoutClusterSplitSheet` open for a route cluster with detected sub-corridors,
• *When* the athlete views the top section of the bottom sheet,
• *Then* an embedded 200dp `PathPreviewMap` is rendered directly above the sensitivity slider, displaying all detected variant polylines color-coded to match the variant badges.
• *Given* the corridor sensitivity slider inside `WorkoutClusterSplitSheet`,
• *When* the athlete drags the slider between 250m and 1500m,
• *Then* `splitVariantPaths` are recalculated asynchronously and the embedded map polylines update their groupings and colors dynamically without UI stutter.
• *Given* any cluster card (including `hitCount >= 2`) in `WorkoutClustersScreen`,
• *When* the athlete long-presses the card to open the context menu,
• *Then* only *"Merge with other favorite route…"* (`cluster_menu_merge`) and *"Delete"* (`delete`) are displayed; *"Split Favorite Route…"* (`cluster_split_action`) is absent.
• *Given* a cluster with `hitCount >= 2` open in `WorkoutClusterHeatmapScreen`,
• *When* the athlete views the detail header and taps the `CallSplit` icon (`R.string.cluster_split_action`),
• *Then* `WorkoutClusterSplitSheet` opens via `viewModel.openSplitPreview()` with the embedded color-coded map preview intact.

**Invariants**: `WorkoutClusterHeatmapScreen` `CallSplit` (`REQ-SET-096.1`) and `WorkoutClusterSplitSheet` preserved; `R.string.cluster_split_action` preserved across all 9 locales; `WorkoutClusterEngine.kt` unmodified; `liteMode(true)` touch isolation preserved; single-path `PathPreviewMap` callers backward-compatible; 100% unit test pass rate.

### REQ-UI-506: Decoupled Compose UI State Models (WorkoutLaps, WorkoutSummaryCard, WorkoutExtrema) [Verified]
- **Target Release**: `V5.0.0`
- **Tests**: `TST-UI-506`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/components/workoutlaps/WorkoutLaps.kt`, `app/src/main/java/com/atrainingtracker/trainingtracker/ui/components/workoutlaps/LapRowUiState.kt`, `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/workoutlist/WorkoutSummaryCompact.kt`, `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/workoutlist/WorkoutCardUiState.kt`, `app/src/main/java/com/atrainingtracker/trainingtracker/ui/components/workoutextrema/WorkoutExtrema.kt`, `app/src/main/java/com/atrainingtracker/trainingtracker/ui/components/workoutextrema/ExtremaUiState.kt`
- **Rationale**: Several core Jetpack Compose composables (`WorkoutLaps.kt`, `WorkoutSummaryCompact.kt`, `WorkoutExtrema.kt`) directly invoke metric formatters and perform math/aggregations inside rendering loops during composition. To eliminate redundant formatting on recomposition, enable pure JVM unit testing without Robolectric harnesses, and simplify `@Preview` authoring, the system SHALL decouple these composables via dedicated, immutable UI state models and pure transformation functions.
- **Description**:
  1. *LapRowUiState & WorkoutLaps Decoupling (`REQ-UI-506.1`)*:
     - The system SHALL introduce an immutable `LapRowUiState` data class encapsulating pre-formatted display strings (`displayName`, `description`, `timeFormatted`, `distanceFormatted`, `speedPaceFormatted`, `badgeEmoji`, `badgeDescription`) and an optional click callback.
     - `WorkoutLapsHelper` SHALL expose a pure function `buildLapRowUiStates(...)` calculating fastest (`🐇`) and slowest (`🦔`) badges and formatting time, distance, and speed/pace strings outside composition.
     - `WorkoutLaps.kt` SHALL render rows from `List<LapRowUiState>` without performing metric formatting or min/max calculations inside the composable row loop.
  2. *WorkoutCardUiState & WorkoutSummaryCompact Decoupling (`REQ-UI-506.2`)*:
     - The system SHALL introduce an immutable `WorkoutCardUiState` data class encapsulating pre-formatted card metadata (`workoutName`, `sportIconResId`, `sportEquipmentText`, `formattedDateTime`, `activeTimeFormatted`, `distanceFormatted`, `speedPaceFormatted`, `ascentFormatted`, `isFinished`, `contentAlpha`).
     - A pure mapper function `WorkoutCardUiState.from(...)` SHALL transform `WorkoutData` and formatters into `WorkoutCardUiState`.
     - `WorkoutSummaryCompact.kt` SHALL support rendering directly from `WorkoutCardUiState`.
  3. *ExtremaUiState & WorkoutExtrema Decoupling (`REQ-UI-506.3`)*:
     - The system SHALL introduce immutable `ExtremaRowUiState` and `ExtremaUiState` representing pre-computed extrema display strings (`label`, `min`, `avg`, `max`, `unit`, `iconResId`, bold flags), decoupling null-coalescing and fallback `"-"` logic from `WorkoutExtrema.kt`.
     - A pure transformation function `ExtremaUiState.from(ExtremaData)` SHALL produce the decoupled state.
  4. *Composable Signature & Backward Compatibility (`REQ-UI-506.4`)*:
     - `WorkoutLaps`, `WorkoutSummaryCompact`, and `WorkoutExtrema` SHALL maintain backward-compatible overloads accepting existing domain models so all existing screen callers compile and operate without disruption.
  5. *Tooling Previews & Pure JVM Testability (`REQ-UI-506.5`)*:
     - Target composables SHALL provide Jetpack Compose `@Preview` definitions instantiated with static UI state objects without requiring mocked `LocalMetricFormatter` instances. Pure JVM unit tests SHALL assert 100% of the UI state mapping logic.

**Acceptance Criteria (Given-When-Then)**:
• *Given* a list of `LapData` with distinct average speeds,
• *When* transformed via `WorkoutLapsHelper.buildLapRowUiStates(...)`,
• *Then* the fastest lap receives `"🐇"`, the slowest receives `"🦔"`, and all pace, speed, time, and distance strings match canonical formatter outputs.
• *Given* a `WorkoutData` instance,
• *When* transformed via `WorkoutCardUiState.from(...)`,
• *Then* active time, total distance, speed/pace, and elevation ascent are pre-formatted, and sport icon and equipment text are resolved without calling formatters inside `@Composable`.
• *Given* an `ExtremaData` instance containing null min or max values,
• *When* converted via `ExtremaUiState.from(...)`,
• *Then* missing values resolve to `"-"` in the state model rather than within the composable layout.

**Invariants**: 100% visual and functional parity; zero regression across all 9 supported languages; zero breaking changes to existing callers; all contract tests green.

### REQ-UI-507: Repository-Only UI Database Access Layer (Phase A: UI & ViewModel Cleanup) [Verified]
- **Target Release**: `V5.0.0`
- **Tests**: `TST-UI-507`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `app/src/main/java/com/atrainingtracker/trainingtracker/MainActivityWithNavigation.kt`, `app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/importbackup/ImportBackupTabsScreen.kt`, `app/src/main/java/com/atrainingtracker/trainingtracker/ui/equipment/EquipmentViewModel.kt`, `app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/controltracking/ControlTrackingViewModel.kt`, `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/WorkoutRepository.kt`, `app/src/main/java/com/atrainingtracker/trainingtracker/devices/DeviceDataRepository.kt`, `app/src/main/java/com/atrainingtracker/trainingtracker/sporttypes/SportTypesRepository.kt`, `app/src/main/java/com/atrainingtracker/trainingtracker/trackingviews/TrackingViewsRepository.kt`
- **Rationale**: Direct instantiation of SQLite database manager singletons (`*DatabaseManager.getInstance()`) inside UI screens, Composables, and ViewModels violates Clean Architecture (SWE.2) and couples presentation layers to raw database operations. The system SHALL eliminate all direct database manager references from `MainActivityWithNavigation.kt`, `ImportBackupTabsScreen.kt`, `EquipmentViewModel.kt`, and `ControlTrackingViewModel.kt` by routing all database operations through domain repositories (`WorkoutRepository`, `DeviceDataRepository`, `SportTypesRepository`, `TrackingViewsRepository`).
- **Description**:
  1. *MainActivityWithNavigation Database Decoupling (`REQ-UI-507.1`)*:
     - In `MainActivityWithNavigation.kt`, all direct references to `DevicesDatabaseManager`, `TrackingViewsDatabaseManager`, and `WorkoutSummariesDatabaseManager` SHALL be replaced with calls to `DeviceDataRepository`, `TrackingViewsRepository`, and `WorkoutRepository`.
     - Low-battery alerts, filter generation, and unfinished workout detection/recovery flows SHALL execute via repository delegates on `Dispatchers.IO`.
  2. *ImportBackupTabsScreen Database Decoupling (`REQ-UI-507.2`)*:
     - In `ImportBackupTabsScreen.kt`, all direct calls to `SportTypeDatabaseManager` for resolving sport names, UI names, and default sport types SHALL route through `SportTypesRepository` delegates.
  3. *EquipmentViewModel Repository Encapsulation (`REQ-UI-507.3`)*:
     - `EquipmentViewModel.kt` SHALL consume `SportTypesRepository`, `DeviceDataRepository`, and `WorkoutRepository` directly.
     - Direct construction of `SportTypeDatabaseManager`, `DevicesDatabaseManager`, and `WorkoutSummariesDatabaseManager` SHALL be removed, while preserving secondary constructor overloads for backward compatibility with existing unit test harnesses.
  4. *ControlTrackingViewModel Database Decoupling (`REQ-UI-507.4`)*:
     - In `ControlTrackingViewModel.kt`, cold-start sensor state seeding and unfinished workout verification SHALL route through `DeviceDataRepository` and `WorkoutRepository` instead of instantiating `DevicesDatabaseManager` and `WorkoutSummariesDatabaseManager`.
  5. *Domain Repository Query Delegates & Invariant Preservation (`REQ-UI-507.5`)*:
     - `WorkoutRepository`, `DeviceDataRepository`, `SportTypesRepository`, and `TrackingViewsRepository` SHALL expose the necessary query and mutation delegates.
     - SQLite schema stability, thread dispatching (`Dispatchers.IO`), battery alert thresholds, and sensor tracking lifecycles SHALL remain 100% preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* `MainActivityWithNavigation`, `ImportBackupTabsScreen`, `EquipmentViewModel`, and `ControlTrackingViewModel`,
• *When* auditing source code and AST references,
• *Then* zero occurrences of `*DatabaseManager.getInstance()` exist across all four target presentation files.
• *Given* an unfinished workout in the database,
• *When* `MainActivityWithNavigation` resumes or `ControlTrackingViewModel` initializes,
• *Then* unfinished workout status is detected via `WorkoutRepository` and the recovery prompt operates identically to the legacy flow.
• *Given* paired sensors with low battery,
• *When* `MainActivityWithNavigation` checks device states,
• *Then* battery warnings are evaluated via `DeviceDataRepository` and notifications trigger without regression.

**Invariants**: Phase A scope bounded strictly to UI & ViewModels; zero modifications to Phase B (`ATT-6426`) or Phase C (`ATT-6427`) components; 100% unit test pass rate.

### REQ-UI-508: Filter Chips & Sheets Imperial Unit Parity & Consistent Rounding [Verified]
- **Target Release**: `V5.0.0`
- **Tests**: `TST-UI-508`, `TST-UI-509`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/common/filters/FilterFormattingUtils.kt`, `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/workoutlist/ActiveFilterChipsRow.kt`, `app/src/main/java/com/atrainingtracker/trainingtracker/ui/clusters/ActiveClusterFilterChipsRow.kt`, `app/src/main/java/com/atrainingtracker/trainingtracker/ui/routes/ActiveRouteFilterChipsRow.kt`, `app/src/main/java/com/atrainingtracker/trainingtracker/ui/clusters/ClusterFilterBottomSheet.kt`, `app/src/main/java/com/atrainingtracker/trainingtracker/ui/routes/RouteFilterBottomSheet.kt`, `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/workoutlist/WorkoutFilterBottomSheet.kt`
- **Rationale**: Filter chips across Workouts, Clusters, and Routes hardcode metric units (`km`, `m`) and unconditional division by `1000.0`, resulting in inaccurate labels (e.g., 10 mi displays as "16.09344 km") and floating-point precision artifacts when Imperial units (`TrainingApplication.getUnit() == MyUnits.IMPERIAL`) are configured. Furthermore, Cluster and Route filter bottom sheets hardcode metric presets and labels. The system SHALL introduce centralized filter unit formatting utilities (`FilterFormattingUtils.kt`), provide imperial unit conversion and consistent rounding (max 1 decimal for distance without trailing `.0`; integer for elevation) across all three active filter chip rows, and support imperial presets and text inputs in `ClusterFilterBottomSheet` and `RouteFilterBottomSheet` with 9-language localization parity in `strings_filters.xml` (ATT-6168, refining `REQ-UI-157`, `REQ-UI-187`, and `REQ-UI-425`):
1. *Centralized Filter Formatting Utilities (`REQ-UI-508.1`)*:
• `FilterFormattingUtils.kt` SHALL provide `formatFilterDistanceChip(...)` and `formatFilterElevationChip(...)` to format interval, min-only, and max-only filter chips using unit-aware string resources (`km`/`mi` and `m`/`ft`).
• Distance input strings SHALL be formatted to at most 1 decimal place, stripping redundant `.0` for integers (e.g. `10`, `12.5`). Elevation input strings SHALL be formatted as rounded integers.
• Reusable preset lists (`getDistancePresets(isImperial)` and `getElevationPresets(isImperial)`) SHALL provide standard athletic interval thresholds for both metric and imperial units.
2. *Active Filter Chips Imperial Unit Parity (`REQ-UI-508.2`)*:
• In `ActiveFilterChipsRow.kt`, `ActiveClusterFilterChipsRow.kt`, and `ActiveRouteFilterChipsRow.kt`, distance and elevation filter chip labels SHALL evaluate `TrainingApplication.getUnit() == MyUnits.IMPERIAL`.
• When imperial is active: distance meters SHALL be converted to miles (`meters / 1609.344`) and formatted with `"mi"`; elevation gain meters SHALL be converted to feet (`meters * 3.28084`) and formatted with `"ft"`.
• When metric is active: distance meters SHALL be converted to kilometers (`meters / 1000.0`) and formatted with `"km"`; elevation gain meters SHALL be formatted with `"m"`.
3. *Cluster & Route Bottom Sheet Imperial Presets and Inputs (`REQ-UI-508.3`)*:
• In `ClusterFilterBottomSheet.kt` and `RouteFilterBottomSheet.kt`, distance and elevation filter chip presets and text field labels SHALL dynamically reflect metric or imperial units based on `TrainingApplication.getUnit()`.
• User-entered imperial distance (`mi`) and elevation (`ft`) values SHALL be converted to raw meters when updating `ClusterFilterCriteria` and `RouteFilterCriteria` drafts (`meters = miles * 1609.344`, `meters = feet / 3.28084`), preserving raw SI meter storage in domain models.
4. *Domain-Partitioned 9-Language Localization Parity (`REQ-UI-508.4`)*:
• Imperial filter format strings (intervals, min-only, max-only, and unit labels) SHALL be defined in `strings_filters.xml` across all 9 canonical locales (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`) per Rule 9 (`REQ-PRO-041.3`).
5. *Preservation of Domain Invariants (`REQ-UI-508.5`)*:
• `WorkoutFilterCriteria`, `ClusterFilterCriteria`, and `RouteFilterCriteria` data classes SHALL continue storing distance and elevation thresholds strictly in raw SI meters (`minDistanceMeters`, `maxDistanceMeters`, `minElevationGainMeters`, `maxElevationGainMeters`).
• Database queries, repository filtering pipelines, and sorting algorithms SHALL remain 100% untouched.

### Requirement Archaeology & Chesterton's Fence Audit:
1. *Original Requirement ID & Target*: Net-new requirement `REQ-UI-508`, refining `REQ-UI-157` (*Workout Filter*), `REQ-UI-187` (*Cluster Filter*), and `REQ-UI-425` (*Elevation Gain Filter*). Targets `ActiveFilterChipsRow.kt`, `ActiveClusterFilterChipsRow.kt`, `ActiveRouteFilterChipsRow.kt`, `ClusterFilterBottomSheet.kt`, `RouteFilterBottomSheet.kt`, and `FilterFormattingUtils.kt`.
2. *Historical Origin & Commit Trace*: Sprint 2026-40.4 (`ATT-1276`, commit `69e4f1a2`), where filter chips were initially introduced with metric-only strings.
3. *Root Reason for Existing Formulation*: Metric was the original baseline; imperial units were added incrementally to `WorkoutFilterBottomSheet` but chips and secondary filter sheets retained legacy hardcoded `/ 1000.0` metric formatting.
4. *Preservation of Core Invariants*: Raw SI meter storage in domain criteria models (`minDistanceMeters`, `maxDistanceMeters`, etc.), database query stability, and 100% backward compatibility for metric users are strictly preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an athlete with Imperial units selected (`MyUnits.IMPERIAL`),
• *When* an active distance filter of 16,093.44 meters (10 miles) is displayed on `ActiveFilterChipsRow`, `ActiveClusterFilterChipsRow`, or `ActiveRouteFilterChipsRow`,
• *Then* the chip label displays "10 mi" (or localized equivalent) without floating-point precision artifacts, instead of "16.09344 km".
• *Given* an athlete with Imperial units selected,
• *When* an active elevation filter of 304.8 meters (1,000 feet) is displayed,
• *Then* the chip label displays "1000 ft" (or localized equivalent) instead of "304 m".
• *Given* `ClusterFilterBottomSheet` or `RouteFilterBottomSheet` opened under Imperial units,
• *When* inspecting the distance and elevation filter sections,
• *Then* presets display athletic mile and feet thresholds and text fields display "(mi)" and "(ft)" labels.
• *Given* an athlete entering "5.5" miles in `ClusterFilterBottomSheet` or `RouteFilterBottomSheet`,
• *When* applying the filter,
• *Then* the criteria stores the converted meter equivalent (~8,851.39 m) in `minDistanceMeters`.

**Invariants**: Raw SI meter storage in filter criteria; 100% 9-language translation parity in `strings_filters.xml`; metric formatting parity preserved for metric users; 100% clean-room test pass rate.

### REQ-UI-509: Shared Filter UI Components, Draft State & Functional Chip Transformers [Verified]
- **Target Release**: `V5.0.0`
- **Tests**: `TST-UI-509`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/common/filters/FilterSearchField.kt`, `app/src/main/java/com/atrainingtracker/trainingtracker/ui/common/filters/NumericIntervalFilterSection.kt`, `app/src/main/java/com/atrainingtracker/trainingtracker/ui/common/filters/StartLocationFilterSection.kt`, `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/workoutlist/WorkoutFilterBottomSheet.kt`, `app/src/main/java/com/atrainingtracker/trainingtracker/ui/clusters/ClusterFilterBottomSheet.kt`, `app/src/main/java/com/atrainingtracker/trainingtracker/ui/routes/RouteFilterBottomSheet.kt`, `app/src/main/java/com/atrainingtracker/trainingtracker/ui/segments/segmentlist/SegmentFilterBottomSheet.kt`, `app/src/main/java/com/atrainingtracker/trainingtracker/ui/knownlocations/KnownLocationsFilterBottomSheet.kt`, `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/workoutlist/ActiveFilterChipsRow.kt`, `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/workoutlist/WorkoutTabsScreen.kt`
- **Rationale**: Across 5 filter bottom sheets and 5 active filter chip rows, search input (`OutlinedTextField`), distance/elevation numeric intervals (~75 lines each), and "Start at" favorite locations (~65 lines) are duplicated. Furthermore, `WorkoutFilterBottomSheet` maintains 27 loose `mutableStateOf` variables, and `ActiveFilterChipsRow` exposes 19 individual callbacks. The system SHALL extract reusable filter primitives (`FilterSearchField`, `NumericIntervalFilterSection`, `StartLocationFilterSection`), migrate sheets to an immutable `draft` state with intermediate text buffers, and migrate `ActiveFilterChipsRow` to functional criteria transformers (`onUpdateCriteria`):
1. *Reusable Filter Input Primitives (`REQ-UI-509.1`)*:
• `FilterSearchField.kt` in `ui/common/filters/` SHALL provide a standardized `OutlinedTextField` parameterized by `query`, `onQueryChange`, and `label`, with leading search icon and single-tap clear icon (`Icons.Default.Close`) when non-blank.
• `NumericIntervalFilterSection.kt` in `ui/common/filters/` SHALL provide a reusable numeric interval filtering component rendering section title, athletic preset chips (`FlowRow`), and min/max inputs with unit indicator, preserving raw metric thresholds and calling back with updated min/max bounds and text buffers.
• `StartLocationFilterSection.kt` in `ui/common/filters/` SHALL provide a reusable favorite location selector rendering `KnownLocationItem` chips, filtering infrequent locations (`hitCount < 5`) by default, and providing a toggle chip to show all locations.
2. *Filter Bottom Sheet Immutable Draft State Architecture (`REQ-UI-509.2`)*:
• All 5 filter bottom sheets (`WorkoutFilterBottomSheet`, `ClusterFilterBottomSheet`, `RouteFilterBottomSheet`, `SegmentFilterBottomSheet`, `KnownLocationsFilterBottomSheet`) SHALL replace loose per-field `mutableStateOf` variables with an immutable `draft` criteria state (`var draft by remember(criteria) { mutableStateOf(criteria) }`).
• Decoupled text buffers (`minDistanceText`, `maxDistanceText`, etc.) SHALL be maintained alongside the `draft` state to preserve intermediate text typing (e.g. `"1."`, `""`) without premature numeric parsing collapse or re-key reset during recomposition.
• `onClearAll` SHALL reset `draft` to a fresh default instance and clear text buffers; `onApply` SHALL emit the finalized criteria.
3. *Functional Transformer Architecture for Active Filter Chips (`REQ-UI-509.3`)*:
• `ActiveFilterChipsRow.kt` SHALL consume a functional transformer callback `onUpdateCriteria: ((WorkoutFilterCriteria) -> WorkoutFilterCriteria) -> Unit` instead of 19 individual removal lambdas, delegating chip removals directly to `WorkoutSummariesViewModel.updateFilterCriteria`.
• Dead unused alias callbacks (`onRemoveMinDistance`, `onRemoveMinDuration`, `onRemoveMinElevation`) SHALL be eliminated across `ActiveFilterChipsRow`, `ActiveClusterFilterChipsRow`, and `ActiveRouteFilterChipsRow`.
4. *Preservation of Filter Contracts & Interaction Invariants (`REQ-UI-509.4`)*:
• `ActiveFilterChipsRowClearAllContractTest.kt` invariants (`if (criteria.isEmpty) return`, `onClick = onClearAll`, and `Modifier.padding(start = 4.dp)` on the clear chip) SHALL remain 100% intact and verified across all chip rows.
• Visual layout, padding, typography, animations, and Imperial unit parity (`REQ-UI-508`) across all 5 filter bottom sheets and chip rows SHALL remain visually indistinguishable.

### Requirement Archaeology & Chesterton's Fence Audit:
1. *Original Requirement ID & Target*: Net-new requirement `REQ-UI-509`, refining `REQ-UI-157` (*Workout Filter*), `REQ-UI-187` (*Cluster Filter*), `REQ-UI-425` (*Elevation Filter*), `REQ-UI-466` (*Known Locations Filter*), `REQ-UI-468` (*Clear All Contract*), and `REQ-UI-508` (*Imperial Unit Parity*). Targets `FilterSearchField.kt`, `NumericIntervalFilterSection.kt`, `StartLocationFilterSection.kt`, bottom sheets, and chip rows.
2. *Historical Origin & Commit Trace*: Sprints 2026-40.4 (`ATT-1276`), 2026-42.2 (`ATT-1593`), 2026-45.12 (`ATT-5888`), and 2026-46.1 (`ATT-6168`).
3. *Root Reason for Existing Formulation*: Sheets and chip rows were built iteratively per list screen prior to ViewModel transformer unification; state and callbacks grew organically without shared input primitives.
4. *Preservation of Core Invariants*: Zero UI layout or interaction regression, intermediate text buffer typing preserved, `ActiveFilterChipsRowClearAllContractTest` preserved, 100% unit test pass rate.

**Acceptance Criteria (Given-When-Then)**:
• *Given* `FilterSearchField` rendered with query `"tempo"`,
• *When* the clear icon is tapped,
• *Then* `query` becomes empty and the field displays hint text.
• *Given* an athlete typing `"1."` into a distance interval text field,
• *When* recomposition occurs,
• *Then* the field preserves `"1."` without collapsing to `1` or clearing.
• *Given* `StartLocationFilterSection` with infrequent locations (`hitCount < 5`),
• *When* the athlete toggles "Show all",
• *Then* the full location list is displayed.
• *Given* `ActiveFilterChipsRow` with an active query filter,
• *When* the query chip remove icon is clicked,
• *Then* `onUpdateCriteria` receives `{ it.copy(query = "") }`.

**Invariants**: Decoupled text buffers preserved for in-progress typing; `ActiveFilterChipsRowClearAllContractTest` 100% compliant; Imperial unit parity (`REQ-UI-508`) preserved; 100% clean-room test pass rate.

### REQ-UI-510: Package-Wide Presentation & UI Layer Repository Encapsulation (Phase A Hardening) [Verified]
- **Target Release**: `V5.0.0`
- **Tests**: `TST-UI-510`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/clusters/WorkoutClustersViewModel.kt`, `app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/trackingtabs/TrackingTabsViewModel.kt`, `app/src/main/java/com/atrainingtracker/trainingtracker/ui/athleteprofile/AthleteProfileViewModel.kt`, `app/src/main/java/com/atrainingtracker/trainingtracker/ui/components/strava/StravaActivitySection.kt`, `app/src/main/java/com/atrainingtracker/trainingtracker/ui/components/export/ExportStatusDataProvider.kt`, `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/WorkoutDataMapper.kt`, `app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapInitialLocationResolver.kt`, `app/src/main/java/com/atrainingtracker/trainingtracker/migration/ImportBackupTabsScreen.kt`, `app/src/main/java/com/atrainingtracker/trainingtracker/activities/MainActivityWithNavigation.kt`, `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/editworkout/EditWorkoutViewModel.kt`
- **Rationale**: Phase A (`ATT-432`) eliminated direct database manager instantiations in four initial presentation entry points, but residual call sites remained across `ui/` and `activities/`. To enforce clean architecture (SWE.2) and prevent database coupling leaks in the presentation layer, all remaining UI and activity call sites SHALL interact exclusively with domain repositories, and `RepositoryUiCleanlinessContractTest` SHALL be expanded into a recursive package-wide architectural guard.
- **Description**:
  1. *Presentation Layer DatabaseManager Sweep (`REQ-UI-510.1`)*:
     - `WorkoutClustersViewModel.kt`, `TrackingTabsViewModel.kt`, `AthleteProfileViewModel.kt`, `StravaActivitySection.kt`, `ExportStatusDataProvider.kt`, `WorkoutDataMapper.kt`, `MapInitialLocationResolver.kt`, `ImportBackupTabsScreen.kt`, `MainActivityWithNavigation.kt`, and `EditWorkoutViewModel.kt` SHALL route all database queries and mutations through domain repositories (`KnownLocationsRepository`, `WorkoutRepository`, `SegmentsRepository`, `ExportStatusRepository`, `EquipmentRepository`, `DeviceDataRepository`, `SportTypesRepository`), completely eliminating direct calls to `*DatabaseManager.getInstance(...)` and `*DbHelper.getInstance(...)`.
  2. *Domain Repository Presentation Delegation (`REQ-UI-510.2`)*:
     - Domain repositories SHALL expose all necessary query and mutation delegates:
       - `KnownLocationsRepository`: `upsertLocationByGeofence`, `updateLocation`, `getMyLocation`
       - `WorkoutRepository`: `getWorkoutTrainingLoadRecords`, `getCardioPowerDynamicsFitnessTrendSamples`
       - `SegmentsRepository`: `allSegmentSummaries`
       - `ExportStatusRepository`: `getExportRows`
       - `EquipmentRepository`: `equipmentItems`
       - `DeviceDataRepository`: `getActiveDeviceIdsForWorkout`
       - `SportTypesRepository`: default sports and sport resolution
  3. *Package-Wide AST Presentation Cleanliness Contract (`REQ-UI-510.3`)*:
     - `RepositoryUiCleanlinessContractTest` SHALL recursively inspect all source files under `app/src/main/java/com/atrainingtracker/trainingtracker/ui/` and `app/src/main/java/com/atrainingtracker/trainingtracker/activities/` (excluding `*Repository.kt` and `MapFragmentWithTrackViewModel.kt` reserved for `ATT-6692`).
     - Zero occurrences of `\b\w*(DatabaseManager|DbHelper)\.getInstance\(` SHALL exist across all scanned presentation classes.

**Acceptance Criteria (Given-When-Then)**:
• *Given* presentation layer files across `ui/` and `activities/`,
• *When* audited by `RepositoryUiCleanlinessContractTest`,
• *Then* zero direct `*DatabaseManager.getInstance` or `*DbHelper.getInstance` invocations exist.
• *Given* athletes viewing workout clusters, athlete profiles, Strava sections, tracking tabs, or export statuses,
• *When* loading presentation data,
• *Then* all metrics, locations, and device records load with 100% data fidelity via repository delegates on `Dispatchers.IO`.

**Invariants**: Zero SQLite schema alterations; all repository I/O dispatched off main thread; 100% test pass rate across UI and ViewModel tests.

### REQ-UI-511: EquipmentViewModel & Repositories Domain DTO Decoupling and Constructor Modernization [Verified]
- **Target Release**: `V5.0.0`
- **Tests**: `TST-UI-511`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `EquipmentViewModel.kt`, `EquipmentRepository.kt`, `DeviceDataRepository.kt`, `SportTypesRepository.kt`, `WorkoutRepository.kt`
- **Rationale**: Repositories must not leak inner `*DatabaseManager` DTOs (`NameAndBatteryPercentage`, `SimpleSensorInfo`, `SimpleSportTypeInfo`, `Stats`) into the UI layer. `EquipmentViewModel` must not bypass domain repositories or retain deprecated constructor parameters and legacy delegation shims (`docs/coding_standards.md` §1.12, §2.5, §5.9).
- **Description**:
  1. *Domain DTO Return Types (`REQ-UI-511.1`)*:
     - `DeviceDataRepository`, `SportTypesRepository`, and `WorkoutRepository` SHALL replace leaky `*DatabaseManager` inner DTOs with strongly typed domain models (`CriticalBatteryDevice`, `SimpleSensorInfo`, `SimpleSportTypeInfo`, `WorkoutStatsSummary`).
  2. *EquipmentRepository Encapsulation (`REQ-UI-511.2`)*:
     - `EquipmentRepository` SHALL encapsulate equipment CRUD and sport-to-equipment associations, eliminating direct UI dependencies on `EquipmentDbHelper` and `SportTypeEquipmentLinkManager`.
  3. *EquipmentViewModel Constructor Modernization (`REQ-UI-511.3`)*:
     - `EquipmentViewModel` SHALL accept domain repositories directly (`EquipmentRepository`, `SportTypesRepository`, `DeviceDataRepository`, `WorkoutRepository`).
     - The 3 `@Deprecated` database manager parameters (`dbSportHelper`, `dbDevicesHelper`, `dbSummariesManager`) and all 7 `resolve*()` delegation fallbacks SHALL be retired.
  4. *Clean Architecture Contract (`REQ-UI-511.4`)*:
     - `RepositoryUiCleanlinessContractTest` SHALL verify zero leaky DatabaseManager DTOs or manager imports in `EquipmentViewModel` and UI callers.

**Acceptance Criteria (Given-When-Then)**:
• *Given* domain repositories `DeviceDataRepository`, `SportTypesRepository`, and `WorkoutRepository`,
• *When* queried for battery levels, sensors, sport types, or equipment stats,
• *Then* they return pure domain models with zero leaky `*DatabaseManager` inner DTOs.
• *Given* `EquipmentViewModel`,
• *When* constructed and observed,
• *Then* it operates exclusively via domain repositories without deprecated database manager parameters or resolve shims.

**Invariants**: Zero functional or numerical regression across equipment screens; thread safety on `Dispatchers.IO` preserved; 100% test pass rate across equipment test harnesses.

### REQ-UI-512: Mounting RadarSettingsSection as TuningAccordionSection in AdvancedTuningScreen and AdvancedTuningDialog [Verified]
- **Target Release**: `V5.0.0`
- **Tests**: `TST-UI-512`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `AdvancedTuningAccordion.kt`, `AdvancedTuningScreen.kt`, `AdvancedTuningDialog.kt`, `RadarSettingsSection.kt`, `strings_radar.xml`
- **Rationale**: `RadarSettingsSection.kt` (907 LOC) provides comprehensive settings and an interactive simulator for cycling radar, but currently has zero production callers. The system SHALL mount `RadarSettingsSection` as a collapsible `TuningAccordionSection` under Block 2 ("Sensoren & GPS") in both `AdvancedTuningScreen.kt` and `AdvancedTuningDialog.kt`, backed by a dynamic summary subtitle in `AdvancedTuningAccordion.kt` and 100% 9-language localization parity.
- **Description**:
  1. *TuningSection Enum Extension (`REQ-UI-512.1`)*:
     - `TuningSection` in `AdvancedTuningAccordion.kt` SHALL include `CYCLING_RADAR`, positioned in athletic sequence within Block 2 ("Sensoren & GPS") following `SENSORS_GPS`.
  2. *Dynamic Subtitle Formatter (`REQ-UI-512.2`)*:
     - `TuningSubtitleFormatter` SHALL provide `formatCyclingRadarSubtitle(...)` reflecting:
       - Active scenario and running status (`radar_simulator_running`) when `RadarTrafficSimulator.isSimulating` is true.
       - Concise summary of screen edge side, audio alert mode, and tail light default mode when idle.
  3. *Accordion Mounting & Collapsed Default (`REQ-UI-512.3`)*:
     - Both `AdvancedTuningScreen.kt` and `AdvancedTuningDialog.kt` SHALL mount `RadarSettingsSection` wrapped in `TuningAccordionSection` using icon `Icons.Default.Sensors`, title `R.string.tuning_cat_cycling_radar`, and the dynamic subtitle, defaulting to initially collapsed (`expandedSections = emptySet()`) per Design Guidelines §3.
  4. *Visual Alignment & Padding Hygiene (`REQ-UI-512.4`)*:
     - Inner `ElevatedCard` containers in `RadarSettingsSection.kt` SHALL use `Modifier.fillMaxWidth().padding(vertical = 4.dp)` without horizontal padding, eliminating double margins inside `TuningAccordionSection`.
  5. *Modularity Constraint (`REQ-UI-512.5`)*:
     - All source files in `com.atrainingtracker.trainingtracker.ui.settings.tuning.*` SHALL strictly maintain $< 400$ lines of code (`AdvancedTuningModularityTest`).
  6. *9-Language Parity (`REQ-UI-512.6`)*:
     - `tuning_cat_cycling_radar` SHALL be localized across all 9 supported locales (`values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`).

**Acceptance Criteria (Given-When-Then)**:
• *Given* default advanced tuning configuration,
• *When* viewing Expert Settings,
• *Then* `CYCLING_RADAR` is listed under Block 2 in collapsed state displaying an idle summary subtitle.
• *Given* `RadarTrafficSimulator` active,
• *When* collapsed,
• *Then* the header subtitle displays active scenario and running indicator.
• *Given* an expanded `CYCLING_RADAR` section,
• *When* rendered,
• *Then* cards align flush with sibling accordion bodies without double horizontal inset.

**Invariants**: Zero schema migrations; settings persist immediately to `TuningPreferencesDataStore`; strict $< 400$ LOC limit across `ui/settings/tuning/`.

### REQ-UI-513: Unified Fitness & Physiology Analytical Hub, Training Drawer Streamlining, and Dedicated Settings Training Zones [Verified]
- **Target Release**: `V5.0.0`
- **Tests**: `TST-UI-513`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `AppNavigationDrawer.kt`, `NavRoutes.kt`, `AthleteProfileScreen.kt`, `PeriodsTabsScreen.kt`, `PeriodsViewModel.kt`, `ZoneSettingsScreen.kt`, `strings.xml` (all 9 locales)
- **Rationale**: Fragmented athlete analytics across Periods, Morning HRV, and Athlete Profile degrade scannability and confuse static settings with time-series trends. The system SHALL streamline the *Training* drawer group into 4 clean destinations, remove `HrvReadinessTrendCard` from `PeriodsTabsScreen`, consolidate longitudinal fitness and recovery analytics into a 4-tab **Fitness & Physiology** hub with a shared `MetricsTimeRange` X-axis, and restore **Training Zones** as a dedicated top-level destination inside the *Settings* drawer group.
- **Description**:
  1. *Training Drawer Streamlining (`REQ-UI-513.1`)*:
     - The *Training* drawer group (`drawer__training`) in `AppNavigationDrawer.kt` SHALL contain exactly 4 items in sequence:
       1. Start Tracking (`R.id.drawer_start_tracking`)
       2. Workouts (`R.id.drawer_workouts`)
       3. Periods (`R.id.drawer_periods`, strictly calendar volume summaries)
       4. Fitness & Physiology (`R.id.drawer_athlete_profile`, title `R.string.drawer_fitness_physiology` / `drawer_athlete_profile`)
     - Standalone `drawer_morning_hrv` SHALL be retired from top-level drawer items.
  2. *Settings Drawer Items Ordering (`REQ-UI-513.2`)*:
     - The *Settings* drawer group (`drawer__settings`) in `AppNavigationDrawer.kt` SHALL contain exactly 7 items in the following sequence:
       1. Trainings Zonen (`R.id.drawer_training_zones`, icon `R.drawable.ic_speed`, title `R.string.prefs_training_zones`)
       2. Sportarten (`R.id.drawer_sport_types`, icon `R.drawable.ic_sports_combined`, title `R.string.sport_types`)
       3. Tracking Tabs (`R.id.drawer_tracking_layouts`, icon `R.drawable.ic_table_edit`, title `R.string.prefsConfigureDisplaysTitle`)
       4. Display (`R.id.drawer_display_settings`, icon `R.drawable.ic_display_settings`, title `R.string.Display`)
       5. Auto-Pause (`R.id.drawer_auto_pause`, icon `R.drawable.ic_pause_circle`, title `R.string.auto_pause_title`)
       6. Maßeinheiten (`R.id.drawer_units`, icon `R.drawable.ic_square_foot`, title `R.string.prefsUnitsTitle`)
       7. Experten-Einstellungen (`R.id.drawer_advanced_tuning`, icon `R.drawable.ic_tune`, title `R.string.advanced_tuning_title`)
     - `isDrawerItemSelected` SHALL recognize `R.id.drawer_training_zones` independently from `R.id.drawer_athlete_profile`, preserving deep-link backward compatibility.
  3. *Clean Periods Screen Decoupling (`REQ-UI-513.3`)*:
     - `PeriodsTabsScreen.kt` and `PeriodsViewModel.kt` SHALL strictly display calendar volume aggregations, removing `HrvReadinessTrendCard` and unnecessary `HrvReadinessRepository` loading overhead.
  4. *Unified Fitness & Physiology Analytical Hub (`REQ-UI-513.4`)*:
     - `AthleteProfileScreen.kt` (Fitness & Physiology) SHALL host tabs in `PrimaryScrollableTabRow` + `HorizontalPager` with standard fixed header `LayoutConstants.HEADER_TITLE_ROW_HEIGHT` and synchronized `MetricsTimeRange` (`1M | 3M | 6M | 1Y | All`) calendar X-axis:
       - **Tab 1: Body & Thresholds (`R.string.fitness_tab_body`)**: Primary baseline physiological inputs and thresholds (Weight, Resting HR, FTP, CP, W', W/kg) with `+` Add Entry action visible in top header bar, `AthleteMetricTimeSeriesChart`, and filterable History Log. Default landing tab (`initialTab = 0`).
       - **Tab 2: Fitness & Fatigue (PMC) (`R.string.fitness_tab_pmc`)**: Conditionally displayed when `enablePmcModel` is true. `CTL / ATL / TSB / ACWR` + Ramp Rate KPI summary tiles and `PerformanceManagementChartCard`. When `enablePmcModel` is false, the tab header and page SHALL be omitted entirely from the hub (no empty placeholder card).
       - **Tab 3: HRV & Readiness (`R.string.fitness_tab_hrv`)**: Primary "Measure Morning HRV" CTA button launching `MorningHrvCheckScreen`, empty-state onboarding card when 0 readings exist in `HrvReadiness.db`, `HrvReadinessTrendCard` (60d posture-isolated SWC corridor + 7d rolling trend + optional Training-Load overlay), and chronological history log of recorded morning HRV entries with delete support.
       - **Tab 4: Cardio-Power (`R.string.fitness_tab_cardio_power`)**: Longitudinal `CardioPowerTrendsCard` (`Fresh HR@200W`, Time Constant `τ`, Slope `1/K`), conditionally displayed when `enableCardioPowerDynamics` is true.
     - Tab resolution SHALL be executed via pure function `resolveFitnessHubTabs(enablePmcModel: Boolean, enableCardioPowerDynamics: Boolean): List<Int>`.
     - On dynamic shrinkage of active tabs, `pagerState.currentPage` MUST be safely clamped to `0..tabTitleResIds.lastIndex`.
  5. *Dedicated Settings Training Zones Hub (`REQ-UI-513.5`)*:
     - `NavRoutes.TRAINING_ZONES` SHALL route directly to `ZoneSettingsScreen` in `ZonesSettingsActivity.kt` hosting the 3 zone configuration tabs (`HR Run`, `HR Bike`, `Power Bike`), Zone Calculator dialog, and Zone History dialog.
  6. *9-Language Localization Parity (`REQ-UI-513.6`)*:
     - All new and updated string resources (`drawer_fitness_physiology`, tab titles, empty states, button CTAs) SHALL be localized with 100% parity across `values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`.

**Acceptance Criteria (Given-When-Then)**:
• *Given* the navigation drawer,
• *When* expanded,
• *Then* the Training group contains exactly 4 items (`Start Tracking`, `Workouts`, `Periods`, `Fitness & Physiology`) and Settings contains `Training Zones`.
• *Given* the Periods screen,
• *When* opened,
• *Then* it displays only calendar volume bars with zero HRV trend card overhead.
• *Given* the Fitness & Physiology hub,
• *When* navigating between tabs with default settings (`enablePmcModel = true`, `enableCardioPowerDynamics = true`),
• *Then* Tab 1 renders Body & Thresholds (with `+` Add Entry visible), Tab 2 renders PMC, Tab 3 renders HRV readiness with Morning HRV CTA & log, and Tab 4 renders Cardio-Power.
• *Given* `enablePmcModel = false` in Expert Settings,
• *When* opening Fitness & Physiology,
• *Then* the PMC tab is completely omitted from the tab row, starting on Body & Thresholds followed by HRV readiness (and Cardio-Power if enabled).
• *Given* selecting Training Zones from Settings,
• *When* opened,
• *Then* it loads `ZoneSettingsScreen` with the 3 zone tabs, calculator, and history dialogs.

**Archaeology (Chesterton's Fence)**:
1. *Original Requirement ID & Target*: Amends `REQ-UI-397.1` & `REQ-UI-397.2` (*Consolidated Equipment & Profile/Zones Destinations*, Sprint 2026-42.7, ATT-3851), `REQ-UI-405.1` (*Training Drawer Order*, Sprint 2026-42.7, ATT-3928), and `REQ-PER-033.5` (*HRV Trend in Periods*, Sprint 2026-46.1, ATT-3557).
2. *Historical Origin & Commit Trace*: Commit `b660ef43` (ATT-3704), commit `8471b409` (ATT-3851), and Sprint 2026-46.1 (ATT-3557).
3. *Root Reason for Existing Formulation*: Zones were initially grouped with athlete profile as an interim consolidation step; HRV trend card was introduced into Periods to surface early longitudinal recovery data before a dedicated multi-tab physiology hub was architected.
4. *Preservation of Core Invariants*: All zone calculation and editing capabilities preserved; all historical athlete metric entries and morning HRV records remain 100% accessible; backward-compatible route mappings (`NavRoutes.ATHLETE_PROFILE`, `NavRoutes.TRAINING_ZONES`) maintained.
5. *Tab Hierarchy & Inactive PMC Suppression Archaeology (ATT-7531, Sprint 2026-46.5)*:
   - *Original Requirement ID & Target*: Refines `REQ-UI-513.4` (*Unified Fitness & Physiology Analytical Hub*, Sprint 2026-46.3, ATT-6720 / commit `83e5f529`).
   - *Historical Origin & Commit Trace*: Sprint 2026-46.3 commit `83e5f529` (`ATT-6720`).
   - *Root Reason for Existing Formulation*: PMC was placed first as an umbrella fitness chart, and an inactive placeholder card was displayed to inform the user that PMC was disabled in Expert Settings.
   - *Preservation of Core Invariants*: Opening the hub lands on active, universally applicable baseline parameters (*Körper & Schwellen*) with immediate `+` entry capability; all underlying PMC, HRV, and Cardio-Power calculation engines, charts, and data stores remain 100% intact; hiding inactive tabs ensures consistency with `enableCardioPowerDynamics` behavior; 9-language localization parity is preserved.

**Invariants**: DataStore zone persistence preserved; SQLite database tables (`HrvReadiness.db`, `AthleteMetricsDb`) unchanged; navigation deep-links backward-compatible; WCAG AA contrast maintained.

### REQ-UI-514: Dedicated ANT+ (bike_radar) and Bluetooth LE (bt_bike_radar) Vector Sensor Badges for DeviceType.BIKE_RADAR [Verified]
- **Target Release**: `V5.0.0`
- **Tests**: `TST-UI-514`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `app/src/main/res/drawable/bike_radar.xml`, `app/src/main/res/drawable/bt_bike_radar.xml`, `UIHelper.java`, `DeviceData.kt`, `VectorAssetResolutionContractTest.kt`
- **Rationale**: When `DeviceType.BIKE_RADAR` devices are discovered or paired over ANT+ or Bluetooth LE, they currently fall back to generic protocol icons (`ant_logo` or `logo_protocol_bluetooth`). The system SHALL provide dedicated, mathematically constructed vector drawables (`bike_radar.xml` and `bt_bike_radar.xml`) following the established design language of the sensor badge family (REQ-UI-375, REQ-UI-442), wire them into `UIHelper` and `DeviceData`, and enforce Rule 37 geometric constraints.
- **Description**:
  1. *ANT+ Radar Badge Geometry & Typography (`REQ-UI-514.1` — Rule 37)*:
     - `app/src/main/res/drawable/bike_radar.xml` SHALL define a `48dp x 48dp` vector drawable with `96x96` viewport.
     - Background badge SHALL be `#000000` rounded rectangle with corner radius $r=10.0$ (`M 10,0 L 86,0 A 10,10 0 0,1 96,10 ...`).
     - Foreground SHALL render standard white silhouette cyclist matching `bike_pwr.xml`.
     - Radar emission motif SHALL render concentric analytical arcs radiating backward behind the cyclist's saddle/seatpost with origin $(36.0, 26.0)$, radii $[8.0, 14.0, 20.0]$, spanning from $+50^\circ$ up (angle $230^\circ$) to $-25^\circ$ down (angle $155^\circ$), maintaining $\ge 3.0$ units visual separation from the rear wheel ($y_{\text{wheel}} \ge 39.0$), cyclist back/saddle, and card border, with all arc endpoints staying cleanly above the rear wheel ($y \le 35.0$).
     - Bottom abbreviation label SHALL render bold uppercase `RDR` centered at $y \in [75.5, 90.5]$ with `android:fillType="evenOdd"` for punch-out transparency on glyph `D` and `R`.
  2. *Bluetooth LE Radar Badge Geometry & Typography (`REQ-UI-514.2` — Rule 37)*:
     - `app/src/main/res/drawable/bt_bike_radar.xml` SHALL define a `48dp x 48dp` vector drawable with `144x144` viewport.
     - Background badge SHALL be full-bleed `#0A3D91` rounded rectangle ($r=22.0$) spanning `0..144` (`M 22,0 L 122,0 A 22,22 0 0,1 144,22 ...`).
     - Cyclist motif SHALL be horizontally centered using `<group android:translateX="18.18">` matching canonical `bt_bike_pwr.xml`, containing the unmodified clean cyclist silhouette (18 paths) with zero saddle emission overlays.
     - Top-left quadrant ($x \approx 12..44, y \approx 17..36$) SHALL embed canonical radar wave mini-icon group `mini_icon_radar` with zero geometric collisions against the cyclist silhouette or viewport boundaries.
     - The radar mini-icon SHALL comprise a single circular source dot (constructed with true diameter chord $\Delta x = 2r$ or equivalent single-center circle geometry, precluding double-circle / 'snowman' rendering) and concentric radar wave arcs radiating rearward (left-facing) toward oncoming traffic, maintaining uniform radial band thickness and zero pinched crescent artifacts.
     - Bottom abbreviation label SHALL render bold uppercase `RDR` centered at $y \in [113.25, 135.75]$ with `android:fillType="evenOdd"`.
  3. *Protocol Icon Resolution (`REQ-UI-514.3`)*:
     - `UIHelper.getIconId(DeviceType.BIKE_RADAR, Protocol.ANT_PLUS)` and `DeviceData.getIconId(DeviceType.BIKE_RADAR, Protocol.ANT_PLUS)` SHALL return `R.drawable.bike_radar`.
     - `UIHelper.getIconId(DeviceType.BIKE_RADAR, Protocol.BLUETOOTH_LE)` and `DeviceData.getIconId(DeviceType.BIKE_RADAR, Protocol.BLUETOOTH_LE)` SHALL return `R.drawable.bt_bike_radar`.
  4. *Rule 37 Vector Quality & Contract Test Coverage (`REQ-UI-514.4`)*:
     - All `<path>` elements in both radar drawables MUST contain $\le 45$ `L` commands and use analytical curve commands (`A`, `C`, or `Q`).
     - `VectorAssetResolutionContractTest.kt` SHALL include `bike_radar` and `bt_bike_radar` in DOM validity, color token, Rule 37 complexity, geometric bounding-box non-overlap, and resolution tests.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Amends `REQ-UI-375` (*Mathematical VectorDrawable Migration*, `ATT-2859`) and `REQ-UI-442` (*Full-Bleed Badge Geometry & Uppercase Labels for Bluetooth LE Sensor Drawables*, `ATT-4956`, `ATT-5201`). Targets `app/src/main/res/drawable/bike_radar.xml`, `app/src/main/res/drawable/bt_bike_radar.xml`, `UIHelper.java`, `DeviceData.kt`, and `VectorAssetResolutionContractTest.kt`.
2. *Historical Origin & Commit Trace*: `ATT-2859` (`15c74685`, Sprint 2026-42.7) established 96x96 ANT+ sensor vector badges with black rounded rectangle and 3-letter uppercase labels. `ATT-4956` (`be03a5bc14`, Sprint 2026-45.1) and `ATT-5201` (`43de6fd0`, Sprint 2026-45.5) established 144x144 full-bleed BLE badges with official `#0A3D91` color, centered cyclist (`translateX=18.18`), and top-left mini-icons. `ATT-7518` (Sprint 2026-46.5) corrected `mini_icon_radar` geometry, eliminating the double-circle dot artifact and pinched crescents to render concentric rearward radar wave bands.
3. *Root Reason for Existing Formulation*: When `DeviceType.BIKE_RADAR` was introduced in `ATT-5083`, dedicated vector drawables were deferred, falling back to generic protocol logos. This creates visual inconsistency in sensor pairing and equipment screens where all other 7 sensor types have dedicated protocol badges.
4. *Preservation of Core Invariants*: Full-bleed badge geometry `0..144` ($r=22.0$), official `#0A3D91` Bluetooth badge token, `#000000` ANT+ badge token, bold uppercase typography with `evenOdd`, Rule 37 geometric constraints ($\le 45$ `L` commands, analytical curves), and 100% backward compatibility for all existing `DeviceType` icon mappings are strictly preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* `DeviceType.BIKE_RADAR` connected via `Protocol.ANT_PLUS`,
• *When* querying `UIHelper.getIconId` or `DeviceData.getIconId`,
• *Then* `R.drawable.bike_radar` is returned.
• *Given* `DeviceType.BIKE_RADAR` connected via `Protocol.BLUETOOTH_LE`,
• *When* querying `UIHelper.getIconId` or `DeviceData.getIconId`,
• *Then* `R.drawable.bt_bike_radar` is returned.
• *Given* `bike_radar.xml` and `bt_bike_radar.xml`,
• *When* evaluated by `VectorAssetResolutionContractTest`,
• *Then* both files parse as valid `<vector>` DOMs, satisfy Rule 37 ($\le 45$ `L` commands per path, analytical curves), conform to color tokens (`#000000` / `#0A3D91`), and render bold `RDR` labels with `evenOdd`.

**Invariants**: Pure UI asset and mapping addition; zero changes to sensor drivers, database schemas, or tracking engine; 100% test pass rate across vector contract tests.

### REQ-UI-515: Concise Display Settings Toggle Subtitles and Cockpit Theme Description [Verified]
- **Target Release**: `V5.0.0`
- **Tests**: `TST-UI-515`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `DisplaySettingsDialog.kt`, `values*/strings.xml` (all 9 locales), `DisplaySettingsTest.kt`
- **Rationale**: In `ATT-6282`, descriptive subtitles were added to the display options toggles and cockpit theme in `DisplaySettingsDialog`. On standard mobile displays, multi-line wrapping (up to 3 lines per option) creates visual clutter, introduces orphaned words, and pushes lower controls off-screen. The system SHALL condense `forcePortraitSummary`, `prefsKeepScreenOnSummary`, `prefsNoUnlockingSummary`, and `cockpit_theme_description` across all 9 locales into crisp, single-line descriptions ($\le 55$ characters for toggles, $\le 75$ characters for cockpit theme).
- **Description**:
  1. *Concise Toggle Subtitle Length Bound (`REQ-UI-515.1`)*:
     - `forcePortraitSummary`, `prefsKeepScreenOnSummary`, and `prefsNoUnlockingSummary` SHALL NOT exceed 55 characters in any of the 9 supported locales (`en`, `de`, `es`, `fr`, `it`, `ja`, `nl`, `pl`, `pt`).
  2. *Cockpit Theme Description Length Bound (`REQ-UI-515.2`)*:
     - `cockpit_theme_description` SHALL NOT exceed 75 characters in any of the 9 supported locales, eliminating 3-line overflow and orphaned words.
  3. *9-Language Semantic Parity (`REQ-UI-515.3`)*:
     - All 9 localized variants SHALL preserve the functional meaning of each toggle without formatting specifiers (`%s`, `%d`), XML-escaped newline entities (`&#10;`), or decorative emojis (`ATT-5400`).
  4. *Contract Test Validation (`REQ-UI-515.4`)*:
     - `DisplaySettingsTest.kt` SHALL validate that toggle subtitles do not exceed 55 characters and cockpit theme description does not exceed 75 characters across all 9 locales, while matching the canonical German and English copy.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Amends `REQ-UI-503` (*Display Settings Toggle Subtitles & Capitalization Parity*, `ATT-6282`). Targets `app/src/main/res/values*/strings.xml`, `DisplaySettingsDialog.kt`, and `DisplaySettingsTest.kt`.
2. *Historical Origin & Commit Trace*: `ATT-6282` (`6753b825`, Sprint 2026-45.6) introduced explanatory subtitles to the display toggles.
3. *Root Reason for Existing Formulation*: Subtitles were drafted as full explanatory sentences to maximize clarity on fresh installations, but the resulting 2–3 line height caused excessive vertical elongation on standard mobile viewport widths ($\le 412\text{dp}$).
4. *Preservation of Core Invariants*: Full functional clarity is maintained; preference keys (`forcePortrait`, `keepScreenOn`, `noUnlocking`), zero format specifiers, zero emojis, and sentence capitalization invariants are strictly preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* `DisplaySettingsDialog` rendered on a standard phone viewport (1080px / 360-412dp),
• *When* inspecting the subtitles for `forcePortrait`, `keepScreenOn`, and `noUnlocking`,
• *Then* each subtitle renders on a single line without wrapping or orphaned words, and its length is $\le 55$ characters across all 9 locales.
• *Given* the `cockpit_theme_description` text,
• *When* rendered across all 9 locales,
• *Then* its length is $\le 75$ characters and fits within 2 compact lines without orphaned 3rd-line words.
• *Given* `DisplaySettingsTest.kt`,
• *When* executed,
• *Then* length bound assertions and exact copy matches for German and English pass with 100%.

**Invariants**: Zero changes to preference keys, DataStore persistence, or dialog state logic; 9-language localization parity with zero emojis or format arguments.

### REQ-UI-516: Pioneer Status Banner Voluntary Support Transition Copy Parity [Verified]
- **Target Release**: `V5.0.0`
- **Tests**: `TST-UI-516`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `SupporterComponents.kt`, `values*/strings.xml` (all 9 locales), `SupporterCopyContractTest.kt`
- **Rationale**: In `ATT-6290` (`REQ-UI-505`), `pioneer_thank_you_body` on `PioneerStatusBanner` (`SupporterScreen.kt`) was updated to confirm permanent feature and sensor unlock for Pioneer athletes. However, displaying the Supporter subscription plan cards (`Bronze`, `Silver`, `Gold`) directly beneath this banner without explanation causes user confusion as to why plans are shown when their app is already unlocked. The system SHALL append a respectful transition sentence inviting voluntary financial support for ongoing development to `pioneer_thank_you_body` across all 9 supported locales.
- **Description**:
  1. *Voluntary Support Transition Sentence (`REQ-UI-516.1`)*:
     - `pioneer_thank_you_body` SHALL append a polite, non-coercive transition sentence clarifying that voluntary financial contributions for ongoing development remain welcome despite all features being unlocked.
  2. *9-Language Localization Parity (`REQ-UI-516.2`)*:
     - The updated copy SHALL be localized across all 9 supported language directories (`values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`).
  3. *Hygiene & Quality Standards (`REQ-UI-516.3`)*:
     - Pursuant to `ATT-5400`, `pioneer_thank_you_body` SHALL NOT contain decorative inline Unicode emojis.
     - The copy SHALL NOT contain formatting specifiers (`%s`, `%d`) or references to "ANT+".
  4. *Contract Test Validation (`REQ-UI-516.4`)*:
     - `SupporterCopyContractTest.kt` SHALL validate that `pioneer_thank_you_body` exists in all 9 locales, contains zero emojis/specifiers/"ANT+", and matches the exact German and English canonical copy.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Amends `REQ-UI-505` (*Supporter Screen Pioneer Thank You Banner Copy Parity*, `ATT-6290`). Targets `app/src/main/res/values*/strings.xml` and `SupporterCopyContractTest.kt`.
2. *Historical Origin & Commit Trace*: `ATT-6290` (`69e96e70`, Sprint 2026-45.6) neutralized legacy "ANT+" references.
3. *Root Reason for Existing Formulation*: The prior update focused purely on removing protocol bias and thanking users, leaving the visual juxtaposition with paid supporter tiers below unexplained.
4. *Preservation of Core Invariants*: Unconditional Pioneer feature and sensor unlock is 100% preserved; support remains strictly voluntary; zero inline emojis (`ATT-5400`) and zero format specifiers.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an athlete with Pioneer status viewing `SupporterScreen`,
• *When* `PioneerStatusBanner` is rendered above the Supporter plan cards,
• *Then* `pioneer_thank_you_body` includes the transition sentence inviting voluntary support, contains zero emojis, and has zero format specifiers across all 9 locales.
• *Given* `SupporterCopyContractTest.kt`,
• *When* executed,
• *Then* exact copy assertions for German and English pass with 100%.

**Invariants**: Zero changes to Pioneer entitlement detection or subscription billing logic; 9-language localization parity with zero emojis.

### REQ-UI-517: Standardize Root-Drawer Hamburger Menu vs Pushed Back Arrow Contract and Training and Maps Screen Headers [Verified]
- **Target Release**: `V5.0.0`
- **Tests**: `TST-UI-517`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `NavRoutes.kt`, `ATrainingTrackerApp.kt`, `MainActivityWithNavigation.kt`, `WorkoutTabsScreen.kt`, `PeriodsTabsScreen.kt`, `SegmentsTabsScreen.kt`, `RouteTabbedScreen.kt`, `WorkoutClustersTabsScreen.kt`, `KnownLocationsScreen.kt`
- **Rationale**: In `ATrainingTrackerApp.kt`, drawer swipe-to-open is intentionally restricted to a narrow 40 dp left-edge interceptor (`REQ-UI-161`). However, only 6 of 18 drawer destinations render a Hamburger Menu icon (`Icons.Default.Menu`), leaving primary top-level views (`Workouts`, `Periods`, `Segments`, `Routes`, `Lieblingsorte`) without any visible navigation button. Furthermore, opening filtered Workouts from `Periods` or `Equipment` calls `navigateToDrawerItem`, resetting the backstack to `START_TRACKING` and preventing system Back from returning to the caller. The system SHALL distinguish Drawer-root destinations from pushed contextual destinations and establish a consistent leading navigation contract across all Training and Maps views.
- **Description**:
  1. *Deterministic Route Contract (`REQ-UI-517.1`)*:
     - `NavRoutes.WORKOUTS` and `NavRoutes.LOCATIONS` SHALL support an optional query parameter `pushed: Boolean = false` (`ARG_PUSHED = "pushed"`).
     - Helper builders `NavRoutes.workouts(pushed: Boolean = false)` and `NavRoutes.locations(clusterId: Long? = null, pushed: Boolean = false)` SHALL construct type-safe route strings.
  2. *Pushed Destination Backstack & Interceptor Contract (`REQ-UI-517.2`)*:
     - When a destination is pushed (`isPushed == true`), the root 40 dp left-edge drawer interceptor in `ATrainingTrackerApp.kt` SHALL be deactivated.
     - Pushed screens SHALL display `Icons.AutoMirrored.Filled.ArrowBack` with `R.string.back` contentDescription. Tapping the back button or pressing system Back SHALL pop back to the origin caller and clear contextual filter criteria (`WorkoutFilterCriteria`, `ClusterFilterCriteria`).
     - `MainActivityWithNavigation.startWorkoutSummaryListFromPeriod()` and `navigateToFilteredWorkouts()` SHALL push `NavRoutes.workouts(pushed = true)` onto `navController`.
  3. *Root Destination Drawer Contract (`REQ-UI-517.3`)*:
     - When opened as a top-level drawer destination (`isPushed == false`), screens SHALL display `Icons.Default.Menu` with `R.string.navigation_drawer_open` contentDescription, and tapping it SHALL open `AppNavigationDrawer` via `drawerController.openDrawer()`.
     - The 40 dp left-edge drawer interceptor SHALL remain active when `isPushed == false`.
  4. *Collapsible Header Leading-Icon Alignment (`REQ-UI-517.4`)*:
     - Headers in `WorkoutTabsScreen`, `PeriodsTabsScreen`, `SegmentsTabsScreen`, `RouteTabbedScreen`, `WorkoutClustersTabsScreen`, and `KnownLocationsScreen` SHALL standardize leading `IconButton` placement (48 dp target, `start = 4.dp`), tint (`MaterialTheme.colorScheme.onPrimaryContainer`), and `titleLarge` text spacing, ensuring zero horizontal or vertical title jump when navigating between drawer destinations.
  5. *Sub-View Drill-Down Integrity (`REQ-UI-517.5`)*:
     - In-screen detail views (`TrackOnMapScreen`, `EditWorkoutScreen`, `PeriodMapScreen`, `RouteOnMapScreen`, `WorkoutClusterHeatmapScreen`) SHALL retain `Icons.AutoMirrored.Filled.ArrowBack` and return to their parent list when tapped.

**Acceptance Criteria (Given-When-Then)**:
• *Given* the athlete selects Workouts, Periods, Segments, Routes, or Lieblingsorte from `AppNavigationDrawer`,
• *When* the destination screen renders at its top level,
• *Then* the header SHALL display the Hamburger Menu button (`Icons.Default.Menu`) and tapping it SHALL open `AppNavigationDrawer`.
• *Given* the athlete opens Workouts from Periods, Start Locations, Equipment, or Sport Types (or opens Lieblingsorte from Start Locations),
• *When* the destination renders,
• *Then* the header SHALL display `Icons.AutoMirrored.Filled.ArrowBack` and tapping it or pressing system Back SHALL pop back to the caller screen and clear contextual filter criteria.
• *Given* the athlete switches between any of the top-level Training and Maps tabbed views,
• *When* observing the header title and leading icon,
• *Then* there SHALL be zero horizontal or vertical title shift.

**Invariants**: All 21 navigation drawer destinations in `NavRoutes` preserved; `REQ-UI-161` 40 dp threshold preserved for root screens; `REQ-UI-162` LIFO back handling hierarchy preserved.

### REQ-UI-518: Standardize Context-Aware Hamburger Menu vs Back Arrow Contract Across Secondary Drawer Groups (Sensors, Equipment, Backup, Sport Types, Zones, Settings, Support) [Verified]
- **Target Release**: `V5.0.0`
- **Tests**: `TST-UI-518`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `NavRoutes.kt`, `ATrainingTrackerApp.kt`, `DevicesTabbedScreen.kt`, `EquipmentTabsScreen.kt`, `ImportBackupTabsScreen.kt`, `SportTypesTabsScreen.kt`, `ZonesSettingsActivity.kt`, `AdvancedTuningScreen.kt`, `SupporterScreen.kt`, `TipsGuideScreen.kt`
- **Rationale**: Completing Part 1 (`ATT-7313`), this requirement standardizes the context-aware Hamburger Menu vs Back Arrow navigation contract across all remaining secondary drawer destinations (`My Stuff`, `Online Communities`, `Settings`, `Info & Support`). Previously, four tabbed screens lacked a leading navigation button entirely, three top-level screens unconditionally displayed Back Arrow even when opened from the drawer, and Training Zones unconditionally displayed Hamburger Menu even when pushed above My Sensors via onboarding pairing banners.
- **Description**:
  1. *Tabbed Secondary Headers Contract (`REQ-UI-518.1`)*:
     - `DevicesTabbedScreen.kt`, `EquipmentTabsScreen.kt` (for `EQUIPMENT`, `BIKES`, and `SHOES`), `ImportBackupTabsScreen.kt`, and `SportTypesTabsScreen.kt` SHALL add a leading navigation `IconButton` to the `primaryContainer` header row.
     - When opened as a root destination from `AppNavigationDrawer`, the button SHALL render `Icons.Default.Menu` with `R.string.navigation_drawer_open` accessibility description and open the drawer on click.
     - When pushed above another destination, the button SHALL render `Icons.AutoMirrored.Filled.ArrowBack` with `R.string.back` accessibility description and navigate back on click.
     - The header row SHALL maintain `padding(start = 4.dp, end = 16.dp)`, `IconButton` size 48 dp, tint `MaterialTheme.colorScheme.onPrimaryContainer`, and 4 dp start padding on the title text, enforcing a consistent 56 dp start offset without visual jumps across tab switches.
  2. *Training Zones Context-Aware Navigation (`REQ-UI-518.2`)*:
     - In `ZonesSettingsActivity.kt` / `ATrainingTrackerApp.kt`, `ZoneSettingsScreen` SHALL display `Icons.Default.Menu` with localized `R.string.navigation_drawer_open` description when opened directly from `drawer_training_zones`.
     - When pushed above `My Sensors` via pairing onboarding banners (`onNavigateToZones` with `promptReason != null` or `pushed == true`), `ZoneSettingsScreen` SHALL display `Icons.AutoMirrored.Filled.ArrowBack` with `R.string.back` description, and tapping it SHALL pop back to the origin caller.
  3. *Settings & Support TopAppBar Navigation Contract (`REQ-UI-518.3`)*:
     - `AdvancedTuningScreen.kt`, `SupporterScreen.kt`, and `TipsGuideScreen.kt` SHALL accept an optional `onOpenDrawer: (() -> Unit)? = null` callback alongside `onNavigateBack`.
     - When opened as root drawer destinations from `AppNavigationDrawer`, they SHALL render `Icons.Default.Menu` (`R.string.navigation_drawer_open`).
     - When pushed above another view, they SHALL render `Icons.AutoMirrored.Filled.ArrowBack` (`R.string.back` / `R.string.cancel`).
     - `AdvancedTuningScreen`'s bottom `Save / Cancel` bar SHALL remain fully functional in both drawer-root and pushed modes.
  4. *Pushed Destination Interceptor Suppression (`REQ-UI-518.4`)*:
     - When any of the secondary screens are pushed (`isCurrentScreenPushed == true`), the root 40 dp left-edge drawer interceptor in `ATrainingTrackerApp.kt` SHALL be deactivated to prevent gesture conflicts with child views and system predictive back.

**Acceptance Criteria (Given-When-Then)**:
• *Given* the athlete opens `My Sensors`, `Equipment`, `Import & Backup`, `Sport Types`, `Training Zones`, `Advanced Tuning`, `Supporter`, or `Tips & Guide` from `AppNavigationDrawer`,
• *When* the destination screen renders at its top level,
• *Then* the header SHALL display `Icons.Default.Menu` and tapping it SHALL open `AppNavigationDrawer`.
• *Given* the athlete navigates to `Training Zones` from `My Sensors` via a sensor pairing banner, or navigates to `Supporter` from `My Sensors` / `Equipment`, or navigates to `Advanced Tuning` from `Display Settings` / `WorkoutClustersScreen`,
• *When* the target screen renders above the origin caller,
• *Then* the header SHALL display `Icons.AutoMirrored.Filled.ArrowBack` and tapping it SHALL pop back to the origin caller.
• *Given* all newly added Hamburger and Back `IconButton` controls across `:app` and `:banalservice`,
• *When* inspected in any of the 9 supported locales,
• *Then* every button SHALL provide localized `contentDescription` (`navigation_drawer_open` or `back` / `cancel`) and align to the 56 dp title baseline.

**Invariants**: All 21 navigation drawer destinations in `NavRoutes` preserved; `REQ-UI-161` 40 dp threshold preserved for root screens; `REQ-UI-162` LIFO back handling hierarchy preserved; zero regressions on existing deep links.

### REQ-UI-524: Official Google Drive and Health Connect Brand Vector Fidelity [Verified]
- **Target Release**: `V5.0.0`
- **Tests**: `TST-UI-524`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `app/src/main/res/drawable/ic_google_drive.xml`, `app/src/main/res/drawable/ic_health_connect.xml`
- **Rationale**: Defective third-party brand icons in the navigation drawer and backup/health settings undermine visual professionalism. Replacing `ic_google_drive.xml` with the official 2020+ 6-segment vector and `ic_health_connect.xml` with the official interlocking dual-loop vector restores authentic brand visual fidelity while ensuring high-contrast rendering across Light and Dark themes.
- **Description**:
  1. *Official Google Drive Brand Vector (`REQ-UI-524.1`)*:
     - `app/src/main/res/drawable/ic_google_drive.xml` SHALL render the official 2020+ Google Drive triangle vector consisting of 6 distinct color paths (`#0066DA`, `#00AC47`, `#EA4335`, `#00832D`, `#2684FC`, `#FFBA00`), centered in a square 87.3x87.3 viewport (`translateY="4.65"`), eliminating broken polygonal gaps and unaligned segments.
  2. *Official Android Health Connect Brand Vector (`REQ-UI-524.2`)*:
     - `app/src/main/res/drawable/ic_health_connect.xml` SHALL render the official Google Health Connect dual interlocking loop brand geometry: left loop in Dark Teal (`#003144`), right loop in Google Blue (`#4285F4`), and upper-right circular node in Google Blue (`#4285F4`).
     - In both Light and Dark themes, the icon SHALL maintain clear contrast and legibility without disappearing on dark backgrounds.
  3. *Un-tinted Brand Icon Integration (`REQ-UI-524.3`)*:
     - Both icons SHALL retain `tintIcon = false` in `AppNavigationDrawer.kt` and `Color.Unspecified` in Compose screens, displaying authentic multi-color brand palettes.

**Acceptance Criteria (Given-When-Then)**:
• *Given* `res/drawable/ic_google_drive.xml` rendered in the Navigation Drawer and Import/Backup screens,
• *When* displayed,
• *Then* it renders the official 6-segment Google Drive vector with a horizontal blue base and continuous segment boundaries.
• *Given* `res/drawable/ic_health_connect.xml` rendered in the Navigation Drawer and Health Connect dialog,
• *When* displayed in either Light or Dark mode,
• *Then* it renders the official interlocking dual-loop heart with upper-right blue dot with high contrast.

**Invariants**: Resource IDs `R.drawable.ic_google_drive` and `R.drawable.ic_health_connect` remain stable; zero changes to OAuth or Health Connect API logic.

### REQ-UI-525: Configurable Morning HRV Check Launch Button on Start Tracking Screen (ATT-8469) [Verified]
- **Target Release**: `V5.0.0`
- **Tickets**: `ATT-7532`, `ATT-7740`, `ATT-8469`
- **Tests**: `TST-UI-525`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `TuningPreferencesDataStore.kt`, `DevicesDatabaseManager.java`, `DeviceDataRepository.kt`, `HrvReadinessDatabaseManager.kt`, `HrvReadinessRepository.kt`, `ControlTrackingViewModel.kt`, `TrackingTabsScreen.kt`, `ControlTrackingScreen.kt`, `MorningCheckSection.kt`, `AdvancedTuningScreen.kt`
- **Rationale**: Following `REQ-UI-513`, athletes performing waking morning HRV check-ins require an immediate 1-tap shortcut on the Start screen (`ControlTrackingScreen`) without navigating nested menus. To prevent cockpit clutter during active tracking and avoid irrelevant prompts for athletes without heart rate sensors, after today's reading has been taken, or past morning hours, the launcher must be strictly conditional and user-configurable.
- **Description**:
  1. *Tuning Preferences Configuration (`REQ-UI-525.1`)*:
     - `TuningPreferencesDataStore.kt` and `TuningConfig.kt` SHALL provide `showMorningCheckOnStartScreen: Boolean` (default `true`) and `morningCheckCutoffHour: Int` (`DEFAULT_MORNING_CHECK_CUTOFF_HOUR = 8` [08:00 AM], `MIN_MORNING_CHECK_CUTOFF_HOUR = 6`, `MAX_MORNING_CHECK_CUTOFF_HOUR = 11`, valid range `6..11`; persisted values `> 11` SHALL safely clamp to `11` via `.coerceIn(6, 11)`) (`ATT-7740`).
     - These preferences SHALL be surfaced under the Morning Checks & Wellness accordion section (`MorningCheckSection.kt`, `TuningSection.MORNING_CHECK`) in `AdvancedTuningScreen.kt` and `AdvancedTuningDialog.kt` with slider range `6f..11f` (`steps = 4`), localized titles/summaries, 2-digit zero-padded hours (`%02d:00`), and Stage 4 Compose PNG screenshot verification (`Rule 8.5`). The cutoff hour slider (`morningCheckCutoffHour`) SHALL be visible whenever `showMorningCheckOnStartScreen == true || enableMorningSleepAndWellness == true` (`ATT-8469`).
  2. *Paired Heart Rate Sensor Check (`REQ-UI-525.2`)*:
     - `DevicesDatabaseManager.java` SHALL provide `hasPairedHeartRateDevices(): Boolean` which queries SQLite `DEVICES` table for `PAIRED > 0 AND DEVICE_TYPE = 'HRM'`.
     - `DeviceDataRepository.kt` SHALL expose `hasPairedHeartRateDevices(): Boolean` forwarding this query.
  3. *Today's Morning Check Completion Check (`REQ-UI-525.3`)*:
     - `HrvReadinessDatabaseManager.kt` SHALL provide `hasCompletedMorningCheck(dateIso: String): Boolean` which queries SQLite `hrv_readiness.db` for the existence of any entry matching `dateIso`.
     - `HrvReadinessRepository.kt` SHALL expose `hasCompletedMorningCheck(dateIso: String): Boolean`.
  4. *Reactive Visibility & Navigation Contract on Start Tracking Screen (`REQ-UI-525.4`)*:
     - `ControlTrackingViewModel.kt` SHALL expose `isMorningHrvCheckVisible: StateFlow<Boolean>` which evaluates to `true` iff ALL of the following conditions hold:
       1. Tracking state is `TrackingMode.READY` (not `RECORDING`, `PAUSED`, or `STOPPED`).
       2. Start-screen morning check is active: either `(showMorningCheckOnStartScreen == true && hasPairedHeartRateDevices == true)` OR pure morning sleep & wellness is enabled (`enableMorningSleepAndWellness == true`) (`ATT-8469`).
       3. Local system time is before cutoff hour (`LocalTime.now().hour < morningCheckCutoffHour`).
       4. No morning HRV check entry exists for today's local date (`!hasCompletedMorningCheck(LocalDate.now().toString())`).
     - In `ControlTrackingScreen.kt` / `TrackingTabsScreen.kt`, when `isMorningHrvCheckVisible == true`, `bottomContent` SHALL render a prominent "Morgendlicher Check" CTA button styled with `RoundedCornerShape(8.dp)`, `Icons.Default.Favorite` (or Heart icon), and localized text `R.string.hrv_morning_check_start_button`.
     - Clicking the CTA button SHALL trigger `onNavigateToMorningHrv` navigating to `NavRoutes.MORNING_HRV`.
     - Once a morning check entry (either chest-strap HRV reading or standalone sleep & wellness entry via `saveStandaloneWellness()`) is saved for today and the athlete navigates back to the Start screen, the button SHALL immediately auto-hide for the remainder of the calendar day.
  5. *9-Language Localization Parity (`REQ-UI-525.5`)*:
     - All newly introduced string keys (`hrv_morning_check_start_button`, `pref_show_morning_check_title`, `pref_show_morning_check_summary`, `pref_morning_check_cutoff_title`, `pref_morning_check_cutoff_summary`) SHALL be localized across all 9 supported Android resource directories (`values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`).

**Acceptance Criteria (Given-When-Then)**:
• *Given* an athlete with at least one paired HRM sensor opens `ControlTrackingScreen` in `TrackingMode.READY` before cutoff hour (default 08:00 AM) on a day with no morning check recorded,
• *When* the screen renders,
• *Then* the "Morgendlicher Check" CTA button is visible in `bottomContent` and tapping it navigates to `NavRoutes.MORNING_HRV`.
• *Given* an athlete with `enableMorningSleepAndWellness == true` and zero paired HRM sensors opens `ControlTrackingScreen` in `TrackingMode.READY` before cutoff hour on a day with no morning check recorded,
• *When* the screen renders,
• *Then* the "Morgendlicher Check" CTA button is visible in `bottomContent` and tapping it navigates to `NavRoutes.MORNING_HRV`.
• *Given* the athlete completes and saves today's morning check (HRV check or standalone wellness via `saveStandaloneWellness()`) or the local time passes cutoff hour or both `showMorningCheckOnStartScreen` and `enableMorningSleepAndWellness` are disabled,
• *When* `ControlTrackingScreen` renders or resumes,
• *Then* the CTA button is hidden.
• *Given* the athlete opens `MorningCheckSection`,
• *When* `enableMorningSleepAndWellness == true` and `showMorningCheckOnStartScreen == false`,
• *Then* the cutoff hour slider (`morningCheckCutoffHour`) is visible and interactive.
• *Given* the athlete starts a workout (`TrackingMode.RECORDING`),
• *When* the cockpit renders,
• *Then* the CTA button is hidden.
• *Given* any of the 9 supported locales,
• *When* viewing the button or tuning settings,
• *Then* all titles and summaries are localized with 100% parity and no placeholder text.

#### Requirement Archaeology & Chesterton's Fence Audit
1. *Original Requirement ID & Target*: `REQ-UI-525` (`REQ-UI-525.1`, `REQ-UI-525.4`), targeting `TuningPreferencesDataStore.kt`, `TuningConfig.kt`, `MorningCheckSection.kt`, `ControlTrackingViewModel.kt`.
2. *Historical Origin & Commit Trace*: Introduced in `ATT-7532` (commit `647ef674d06`), extended to `23:00` in `ATT-7740` (`6267d2c0`), relocated to `MorningCheckSection.kt` in `ATT-8117` (`f628f0a6`), amended in `ATT-7740` (Sprint `2026-47.1`), and refined in `ATT-8469` (Sprint `2026-11-16_03_Nacht`).
3. *Root Reason for Existing Formulation*: Originally, morning check was exclusively linked to chest-strap HRV measurements, gating button visibility strictly on `showMorningCheckOnStartScreen && hasPairedHeartRateDevices`, and hiding the cutoff slider if `showMorningCheckOnStartScreen` was disabled.
4. *Preservation of Core Invariants*: Invariants `TrackingMode == READY`, cutoff hour ceiling `6..11` (default `8`), daily single-completion auto-hide, zero DB schema migration, and strict architectural layering (`RepositoryUiCleanlinessContractTest` zero `*DatabaseManager` in `ui/`) remain strictly preserved.

**Invariants**: Zero database migrations on `devices.db` or `hrv_readiness.db`; zero modifications to existing morning check scoring, baseline corridors, or workout tracking lifecycle; zero background alarms or wake locks.

### REQ-UI-526: Global Physiological Typography Standardization for DFA α1 and α1 [Verified]
- **Target Release**: `V5.0.0`
- **Tickets**: `ATT-7521`
- **Tests**: `TST-UI-526`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `strings_hrv.xml` (all 9 locales), `SensorType.java`, `ZonesSettingsActivity.kt`
- **Rationale**: Detrended Fluctuation Analysis alpha-1 nomenclature was historically inconsistent across the application due to early ASCII representations (`DFA a1` / `a1`) in `strings_hrv.xml`, subscript Greek notations (`α₁`) in `ZonesSettingsActivity.kt` range badges, and standard Greek notations (`DFA α1`, `Δα1`) in newer durability and ramp test components. Standardizing globally on the authentic Greek letter α (`DFA α1` and `α1`) ensures professional, consistent physiological typography across all user-facing screens and all 9 supported locales while strictly maintaining internal enum and preference key stability.
- **Description**:
  1. *9-Locale String Resource Typography Parity (`REQ-UI-526.1`)*:
     - All user-facing string definitions in `app/src/main/res/values*/strings_hrv.xml` referencing Detrended Fluctuation Analysis alpha-1 SHALL use the authentic Greek letter α (`DFA α1` and `α1`) across all 9 supported locales (`values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`).
     - This SHALL include `hrv_dfa_a1` (`DFA α1`), `hrv_dfa_a1_short` (`α1`), `tab_hrv_dfa_a1` (`HRV (DFA α1)` / `VFC (DFA α1)`), `hrv_test_live_a1` (`Live α1: %1$.2f` / localized counterparts), and all related threshold, tuning, and guided test descriptions.
  2. *Zone Settings Dynamic Range Badge Alignment (`REQ-UI-526.2`)*:
     - In `ZonesSettingsActivity.kt`, dynamic zone range badge strings SHALL use standard `α1` (`"α1 > %.2f"`, `"%.2f ≤ α1 ≤ %.2f"`, `"α1 < %.2f"`) instead of subscript `α₁`, aligning with app-wide typography.
  3. *Sensor Type Presentation Integrity (`REQ-UI-526.3`)*:
     - `SensorType.HRV_DFA_A1` SHALL resolve its user-facing full label and short title from `R.string.hrv_dfa_a1` (`DFA α1`) and `R.string.hrv_dfa_a1_short` (`α1`).
  4. *Internal Symbol, Preference Key, and Test Tag Invariance (`REQ-UI-526.4`)*:
     - Enum names (`SensorType.HRV_DFA_A1`), resource identifiers (`R.string.hrv_dfa_a1`, `R.string.hrv_dfa_a1_short`, `R.string.tab_hrv_dfa_a1`), DataStore preference keys (`tuning_dfa_a1_threshold_t1`, `tuning_dfa_a1_threshold_t2`, `hrv_dfa_a1_zone_*`), and UI test tags (`hrv_test_live_a1`, `hrv_alpha1_scatter_card`) SHALL remain strictly unchanged to preserve backward compatibility, schema stability, and test harness invariants.
  5. *Localization Parity & Specifier Safety (`REQ-UI-526.5`)*:
     - 100% key and format specifier parity SHALL be preserved across all 9 localized resource files with zero missing keys and zero AAPT2 formatting errors.

**Acceptance Criteria (Given-When-Then)**:
• *Given* the application running under any of the 9 supported locales,
• *When* the user views any screen referencing Detrended Fluctuation Analysis alpha-1 (sensor telemetry fields, Training Zones tab title, range badges, workout details cards/charts, guided ramp test, or advanced tuning),
• *Then* the metric is consistently displayed using Greek letter α (`DFA α1` or `α1`) with zero occurrences of ASCII `DFA a1` or subscript `α₁`.
• *Given* `SensorType.HRV_DFA_A1`,
• *When* querying `fullNameId` and `shortNameId`,
• *Then* they resolve to `R.string.hrv_dfa_a1` and `R.string.hrv_dfa_a1_short` which contain `DFA α1` and `α1` respectively.
• *Given* `ZonesSettingsActivity.kt` range badges,
• *When* rendered for Aerobic, Transition, and Anaerobic zones,
• *Then* badge strings display `"α1 > %.2f"`, `"%.2f ≤ α1 ≤ %.2f"`, and `"α1 < %.2f"`.
• *Given* DataStore keys and SQLite schema,
• *When* reading or writing preferences,
• *Then* all keys and database columns remain 100% invariant.

**Invariants**: Resource IDs, DataStore preference keys, enum constants, and SQLite tables remain unchanged; zero changes to mathematical DSP calculation or threshold invariant $T_1 \ge T_2 + 0.05f$.

### REQ-UI-527: Dedicated HRV Tab in Workout Details, Wire Migration & Telemetry-Gated Zone Comparison [Verified]
- **Target Release**: `V5.0.0`
- **Tickets**: `ATT-7756`
- **Tests**: `TST-UI-527`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `WorkoutDetailTabConfig.kt`, `WorkoutSectionType.kt`, `WorkoutDetailTabbedContent.kt`, `TrackOnMapScreen.kt`, `HrvThresholdSummaryCard.kt`, `strings.xml` (all 9 locales)
- **Rationale**: Post-workout HRV analytics (`HrvAlpha1ScatterCard`, `HrvThresholdSummaryCard`, `HrvDurabilityCard`) were previously colocated within the Training Zones section (`WorkoutDetailSectionType.ZONES`), cluttering the standard HR and Power distribution cards. Furthermore, `HrvThresholdSummaryCard` rendered Coggan Power headers, FTP comparison deltas, consensus status rows, and zone update buttons whenever `configuredFtp > 0` even when the workout contained no power telemetry. The system SHALL extract a dedicated `HRV` section (`WorkoutDetailSectionType.HRV`, `WorkoutSectionType.HRV`, and `WorkoutDetailTabPreset.HRV`), define a canonical 5-tab default layout, auto-migrate legacy persisted tab configurations, dynamically auto-hide the HRV tab when no HRV data exists, and gate Power and HR zone comparison blocks strictly on actual telemetry availability (ATT-7756, Parent Epic ATT-3539, status: Planned, refining `REQ-UI-408`, `REQ-PER-036.5`, and `REQ-PER-037.4`):
- **Description**:
  1. *Dedicated HRV Section & Canonical 5-Tab Default Layout (`REQ-UI-527.1`)*:
     - `WorkoutDetailSectionType` and `WorkoutSectionType` SHALL define `HRV(R.string.settings_workout_card_hrv)`.
     - `WorkoutDetailTabPreset` SHALL define `HRV(R.string.workout_detail_tab_hrv)`.
     - `WorkoutDetailTabConfig.DEFAULT_TABS` SHALL define 5 default tabs in canonical order:
       1. `tab_overview` (`OVERVIEW`: `[RPE_AND_FEEL, DETAILS, PHOTOS, DESCRIPTION, EXTREMA, EXPORT_STATUS]`)
       2. `tab_map_graphs` (`MAP_AND_GRAPHS`: `[MAP, ELEVATION, CHARTS]`)
       3. `tab_zones` (`ZONES`: `[ZONES, GRADE_DISTRIBUTION]`)
       4. `tab_hrv` (`HRV`: `[HRV]`)
       5. `tab_laps_segments` (`LAPS_AND_SEGMENTS`: `[LAPS, STRAVA]`)
  2. *Automatic Wire Migration & Backward Compatibility (`REQ-UI-527.2`)*:
     - In `WorkoutDetailTabConfig.deserializeTabs`, if `WorkoutDetailSectionType.HRV` is not present in ANY tab of a valid non-empty tab list, the system SHALL automatically insert a new tab `tab_hrv` (`preset = WorkoutDetailTabPreset.HRV`, `sections = listOf(WorkoutDetailSectionType.HRV)`) directly after `tab_zones` (or before `tab_laps_segments`), guaranteeing existing user configurations seamlessly surface the new HRV tab without data loss.
  3. *Dynamic Availability & Empty Tab Auto-Hiding (`REQ-UI-527.3`)*:
     - `WorkoutDetailSectionAvailability` SHALL expose `val hasHrv: Boolean = false`, mapped to `WorkoutDetailSectionType.HRV -> hasHrv` in `isSectionActive`.
     - `TrackOnMapScreen.kt` SHALL compute `hasHrvData = TelemetryMetricUtils.hasHrvData(activeScrubPath)` and pass it into `WorkoutDetailSectionAvailability.fromWorkoutState`.
     - For workouts lacking HRV telemetry (`hasHrvData == false`), `filterVisibleWorkoutDetailTabs` SHALL automatically hide `tab_hrv` with zero layout shifts.
  4. *HRV Card Migration from ZONES to HRV (`REQ-UI-527.4`)*:
     - In `WorkoutDetailTabbedContent.kt`, `HrvAlpha1ScatterCard`, `HrvThresholdSummaryCard`, and `HrvDurabilityCard` SHALL be rendered under `WorkoutDetailSectionType.HRV` preserving their relative order (`1. HrvAlpha1ScatterCard` -> `2. HrvThresholdSummaryCard` -> `3. HrvDurabilityCard`).
     - Those three HRV cards SHALL be completely removed from `WorkoutDetailSectionType.ZONES`.
  5. *Telemetry-Gated Power & HR Zone Comparison in HrvThresholdSummaryCard (`REQ-UI-527.5`)*:
     - In `HrvThresholdSummaryCard.kt`, power comparison (`showPowerComparison = hasPower && configuredFtp != null && configuredFtp > 0`) SHALL gate the Coggan Power header, configured FTP text, power delta chips (`hrv_power_delta_t1`, `hrv_power_delta_t2`), power consensus badge (`hrv_consensus_badge`), and "Leistungszonen aktualisieren" button (`hrv_btn_update_power_zones`).
     - Heart rate comparison (`showHrComparison = hasHr && configuredHrMax != null && configuredHrMax > 0`) SHALL gate the HR header, HR delta chips, HR consensus badge, and "HF-Zonen aktualisieren" button.
     - The entire `hrv_zone_comparison_section` SHALL be rendered only when `showPowerComparison || showHrComparison`. If neither applies (e.g. workout has no power data and no HR max is configured), the comparison section and its divider SHALL be completely omitted.
  6. *9-Language Localization Parity (`REQ-UI-527.6`)*:
     - String resources `workout_detail_tab_hrv` and `settings_workout_card_hrv` SHALL exist across all 9 supported application locales (`values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`).

**Acceptance Criteria (Given-When-Then)**:
• *Given* a workout with valid HRV telemetry (`hasHrvData == true`) and default tab preferences,
• *When* the user views Workout Details,
• *Then* a dedicated HRV tab appears at tab index 3 (between Zones and Laps & Segments) containing `HrvAlpha1ScatterCard`, `HrvThresholdSummaryCard`, and `HrvDurabilityCard`, and the Zones tab contains only HR/Power/Grade distribution cards.
• *Given* a workout recorded without HRV telemetry (`hasHrvData == false`),
• *When* the user views Workout Details,
• *Then* `filterVisibleWorkoutDetailTabs` suppresses the HRV tab entirely.
• *Given* a legacy serialized tabs string omitting `HRV`,
• *When* `deserializeTabs` parses the payload,
• *Then* `tab_hrv` is automatically inserted directly following `tab_zones`.
• *Given* a workout with HRV and HR data but no power meter (`hasPower == false`),
• *When* viewing `HrvThresholdSummaryCard`,
• *Then* the Power zone comparison header, FTP label, power deltas, power consensus badge, and power zone update button are omitted while the HR zone comparison remains visible.
• *Given* a workout with HRV but neither power nor configured HR max,
• *When* viewing `HrvThresholdSummaryCard`,
• *Then* the entire `hrv_zone_comparison_section` is omitted.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: `REQ-UI-408.1` (Default Tab Archetype), `REQ-PER-036.5` (Threshold Card Host), `REQ-PER-037.4` (Zone Comparison Section), amended by `ATT-7756`.
2. *Historical Origin & Commit Trace*: `ATT-3920` (Sprint 2026-44.1, 4-tab model), `ATT-3554` (Sprint 2026-46.4, HRV cards placed under `ZONES`), `ATT-3555` (Sprint 2026-46.5, zone comparison section).
3. *Root Reason for Existing Formulation*: When the multi-tab layout was created in `ATT-3920`, HRV analytics did not yet exist. In `ATT-3554`, the 3 HRV cards were temporarily colocated in `tab_zones` because an HRV tab did not exist. In `ATT-3555`, zone comparison assumed power was available if `configuredFtp > 0`.
4. *Preservation of Core Invariants*: Wire backward compatibility in `deserializeTabs` via auto-migration; map-at-index-0 invariant; empty tab auto-hiding; 100% 9-language localization parity.

**Invariants**: Map remains pinned at index 0 in any tab where present; zero layout shifts on non-HRV workouts; existing user tab configurations migrate without data loss; 100% 9-language localization parity.

### REQ-UI-528: Multi-Dimensional Sensor List Sorting (Alphabetical, First Seen, Last Usage, Battery Level) [Verified]
- **Target Release**: `V5.0.0`
- **Tickets**: `ATT-7663`
- **Tests**: `TST-UI-528`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `DeviceSortOrder.kt`, `DeviceListViewModel.kt`, `DevicesTabbedViewModel.kt`, `DevicesTabbedScreen.kt`, `strings_devices.xml` (all 9 locales)
- **Rationale**: Athletes managing multiple wireless sensors (ANT+ and Bluetooth LE) need quick, intuitive ways to locate newly paired sensors, review recently active sensors, or check battery levels before workouts. Providing a top app bar Sort dropdown with checkmark indicators matching Routes and Known Locations patterns enhances navigation without disrupting existing scan discovery pinning or alphabetical stability (ATT-7663, Parent Epic ATT-5910, extending `REQ-UI-334`).
- **Description**:
  1. *DeviceSortOrder Domain Enumeration (`REQ-UI-528.1`)*:
     - The system SHALL define `DeviceSortOrder` enum in `com.atrainingtracker.banalservice.ui.devices.devicelist` with values: `ALPHABETICAL` (Default), `FIRST_SEEN`, `LAST_USAGE`, and `BATTERY_LEVEL`, each mapped to a localized string resource ID (`labelResId`).
  2. *Multi-Tier Deterministic Sorting Pipeline (`REQ-UI-528.2`)*:
     - `DeviceListViewModel.getFilteredDevices` SHALL accept a `sortOrder: DeviceSortOrder` parameter (defaulting to `DeviceSortOrder.ALPHABETICAL`).
     - *Primary Scan Recency Tier*: All newly discovered sensors (`isNewlyDiscovered == true`) SHALL remain pinned at the very top of the list in reverse discovery order (`newlyFoundDevicesIds.indexOf(it.id)` descending), strictly preserving `REQ-UI-334`.
     - *Secondary User-Selected Tier*:
       - `ALPHABETICAL`: Case-insensitive `deviceName` ascending (`A–Z`), tie-broken by `id` ascending.
       - `FIRST_SEEN`: Database row ID (`id`) descending (newest added/discovered first).
       - `LAST_USAGE`: Currently connected devices (`isConnected == true`) first, followed by disconnected devices ordered by parsed `lastSeen` timestamp descending (most recently active first, treating null or unparseable timestamps as 0L), tie-broken by `deviceName` (`A–Z`) and `id` ascending.
       - `BATTERY_LEVEL`: Known battery percentage (`batteryPercentage >= 0`) ascending (lowest battery first), followed by unknown battery (`batteryPercentage < 0`), tie-broken by `deviceName` (`A–Z`) and `id` ascending.
  3. *Top App Bar Sort Action Button & Dropdown Menu (`REQ-UI-528.3`)*:
     - In `DevicesTabbedScreen.kt`, the top app bar SHALL display a Sort `IconButton` with `Icons.AutoMirrored.Filled.Sort` right next to `FilterActionButton` when the active tab is a sensor list tab (`pagerState.currentPage in 0..2`).
     - Tapping the button SHALL open a `DropdownMenu` listing all 4 sort options, displaying a `Check` leading icon next to the currently active sort order.
     - The Sort button SHALL be hidden on Equipment Matrix tabs (`pagerState.currentPage in 3..4`).
  4. *ViewModel State & Process Death Persistence (`REQ-UI-528.4`)*:
     - `DevicesTabbedViewModel` SHALL expose `sortOrder: StateFlow<DeviceSortOrder>` initialized from `SavedStateHandle[KEY_SORT_ORDER]`, surviving configuration changes and process restoration.
  5. *100% 9-Language Localization Parity (`REQ-UI-528.5`)*:
     - All 4 sort order labels (`devices_sort_alphabetical`, `devices_sort_first_seen`, `devices_sort_last_usage`, `devices_sort_battery_level`) and menu title/description SHALL be localized across all 9 supported application locales (EN, DE, ES, FR, IT, JA, NL, PL, PT) with zero missing entries and zero empty strings.

**Acceptance Criteria (Given-When-Then)**:
• *Given* the athlete opens "My Sensors" (`DevicesTabbedScreen`),
• *When* viewing any sensor tab (Available, Paired, All Known),
• *Then* a Sort icon button is displayed next to the Filter button, and clicking it opens a dropdown menu with a checkmark on the active sort order (defaulting to Alphabetical).
• *Given* an active sort order selected (e.g. `BATTERY_LEVEL`, `FIRST_SEEN`, `LAST_USAGE`),
• *When* the sensor list renders,
• *Then* sensors are sorted deterministically according to the selected criteria, while any newly discovered sensors (`isNewlyDiscovered == true`) remain pinned at index 0..N at the top.
• *Given* the athlete switches to Equipment tabs (Bikes or Shoes),
• *When* viewing tab 3 or 4,
• *Then* the Sort icon button is not displayed in the top bar.

**Invariants**: Zero changes to `devices.db` SQLite schema; `isNewlyDiscovered` top-of-list recency pinning (`REQ-UI-334`) preserved; stable alphabetical default with zero connection jumping preserved; 100% 9-language localization parity.

### REQ-UI-529: Borg CR10 RPE 7-Zone Physiological Palette, Discrete Slider Stop-Dot Uniformity & Dedicated Non-Occluding Header [Verified]
- **Tests**: `TST-UI-529`
- **Priority**: High
- **Type**: Functional
- **Verification Method**: Unit, On-Device Manual
- **Implementation**: `Color.kt`, `WorkoutRpeFeelSection.kt`
- **Rationale**: In `WorkoutRpeFeelSection.kt`, the initial Borg CR10 slider prototype from ATT-7515 mapped RPE 1–10 to only 5 zones, leaving Zone 6 (#8B5A2B) and Zone 7 (#000000) unused and inverting Zone 1 and Zone 2. Furthermore, the redundant row of 10 number boxes beneath the slider wasted vertical space, the Compose Material 3 slider track stop indicator erroneously painted tick 10 in the active track color when dragging at lower values, and sharing a single row between section title, dynamic value, and Clear button caused line wrapping and finger occlusion during right-handed interaction. Mapping RPE 1–10 to all 7 physiological zones with OLED dark theme high-contrast protection, removing the redundant number boxes, ensuring uniform track stop-dot rendering, and elevating the dynamic RPE descriptor to a dedicated full-width line establishes ergonomic, accessible, and sports-scientifically accurate workout perception editing (ATT-7743, Parent Epic ATT-68, refining `REQ-WKO-037.1` and `REQ-WKO-038`).
- **Description**:
  1. *Borg CR10 7-Zone Physiological Color Mapping (`REQ-UI-529.1`)*:
     - `TTColor.rpeColor(rpe: Int): Color` in `Color.kt` SHALL map all 10 Borg CR10 exertion scores strictly to `TTColor.Zone1..Zone7`:
       - `1, 2` $\rightarrow$ `TTColor.Zone1` (Chartreuse `#7FFF00`, Active Recovery)
       - `3, 4` $\rightarrow$ `TTColor.Zone2` (Green `#008000`, Endurance)
       - `5, 6` $\rightarrow$ `TTColor.Zone3` (Orange `#FFA500`, Tempo / Sweet Spot)
       - `7` $\rightarrow$ `TTColor.Zone4` (Red `#FF0000`, Threshold)
       - `8` $\rightarrow$ `TTColor.Zone5` (Dark Violet `#9400D3`, VO2max)
       - `9` $\rightarrow$ `TTColor.Zone6` (Warm Earthy Brown `#8B5A2B`, Anaerobic Capacity)
       - `10` $\rightarrow$ `TTColor.Zone7` (Pure Black `#000000`, Neuromuscular / Max)
       - Values out of range ($< 1$) SHALL default to `TTColor.Zone1`; values $> 10$ SHALL default to `TTColor.Zone7`.
  2. *Dark Theme High-Contrast Accessibility for RPE 10 / Zone 7 (`REQ-UI-529.2`)*:
     - When `rpe == 10` (or `TTColor.rpeColor(rpe) == TTColor.Zone7`), the dynamic RPE score and Borg descriptor text in `RpeFeelEditorControls` SHALL be rendered using `MaterialTheme.colorScheme.onSurface` (achieving $\ge 12:1$ contrast against dark surfaces and $\ge 14:1$ on light surfaces) rather than invisible pure black text.
     - In `WorkoutRpeFeelSection` badge row, when `rpe == 10`, the badge surface border and inner indicator dot SHALL render an outline border (`0.5.dp` to `1.dp`, `MaterialTheme.colorScheme.outlineVariant`) guaranteeing visibility against OLED black backgrounds.
     - In `RpeFeelEditorControls` slider, when `rpe == 10`, the slider thumb SHALL render a subtle border (`1.dp`, `MaterialTheme.colorScheme.outline`) ensuring distinct visual edge separation against dark backgrounds.
  3. *Removal of Redundant 1–10 Number Boxes (`REQ-UI-529.3`)*:
     - `RpeFeelEditorControls` in `WorkoutRpeFeelSection.kt` SHALL omit the secondary 10-box button row beneath the slider, relying solely on the discrete Material 3 `Slider` (1f..10f, `steps = 8`) for RPE selection, thereby freeing substantial vertical screen space in `EditWorkoutScreen`.
  4. *Slider Track Stop-Dot Uniformity (`REQ-UI-529.4`)*:
     - In `RpeFeelEditorControls`, the Material 3 `Slider` SHALL supply `track = { sliderState -> SliderDefaults.Track(sliderState = sliderState, colors = colors, drawStopIndicator = null) }` (or equivalent), preventing `SliderDefaults.Track` from hardcoding the active track color at the rightmost inactive stop tick (position 10) when the slider thumb is set to values $< 10$.
  5. *Dedicated Non-Wrapping, Non-Occluding Dynamic RPE Header (`REQ-UI-529.5`)*:
     - `RpeFeelEditorControls` SHALL decouple the section title and dynamic RPE readout:
       - *Title Row*: Standard `Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween)` containing `workout_rpe_title` ("Belastungsempfinden (RPE 1–10)") on the start and the Clear action (`Icons.Default.Clear`, `WorkoutRpeFeelTestTags.RPE_CLEAR`) on the end when `rpe != null`.
       - *Dynamic Readout Line*: Dedicated full-width line positioned immediately above the `Slider`. When `rpe != null`, it SHALL display the full formatted anchor descriptor (`stringResource(getRpeAnchorStringRes(rpe))`) in bold typography, ensuring zero line-wrapping across all 9 localized string sets (including German "6 — Schwer bis sehr schwer") and zero occlusion by the athlete's dragging finger.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an athlete selecting RPE ratings 1 through 10 in `EditWorkoutScreen`,
• *When* the rating is evaluated in `TTColor.rpeColor`,
• *Then* scores 1–2 map to Zone1, 3–4 to Zone2, 5–6 to Zone3, 7 to Zone4, 8 to Zone5, 9 to Zone6, and 10 to Zone7.
• *Given* the app running in Dark Theme with RPE set to 10 (`Maximal`),
• *When* viewing the editor readout or workout badge,
• *Then* the text is rendered with `onSurface` and the badge/thumb features an `outlineVariant` border with $\ge 3.0:1$ non-text contrast against the dark background.
• *Given* `EditWorkoutScreen` opened with the RPE editor active,
• *When* inspecting the component hierarchy,
• *Then* the slider provides discrete steps 1..10, the redundant 10-box button row is absent, and setting RPE to 7 renders inactive ticks 8, 9, and 10 in identical inactive tick color without a red stop dot.
• *Given* RPE set to 6 on a 360dp portrait device in German locale,
• *When* the dynamic readout renders,
• *Then* "6 — Schwer bis sehr schwer" renders on a single line without wrapping and remains visible above the slider during dragging.

**Invariants**: `rpe` (1..10) nullable database persistence (`DB_VERSION = 30`) preserved; Foster sRPE calculation preserved; Feel (1..5) M3 cards and sentiment icons preserved; 100% 9-language localization parity preserved.

### REQ-UI-530: Centralized Light/Dark Telemetry Color Palette, Metric Defaults & Global Graph Color Persistence [Verified]
- **Tests**: `TST-UI-530`
- **Priority**: High
- **Type**: Functional
- **Verification Method**: Unit, Review
- **Implementation**: `Color.kt`, `TelemetryGraphColorPreferences.kt`, `TelemetryMetricGraph.kt`
- **Rationale**: Currently, `TelemetryMetricGraph.kt` hardcodes raw `TTColor.Zone1..5` and `TTColor.WPrimeBalGreen` tokens that lack Light/Dark/AMOLED adaptation (e.g., `Zone1` fails contrast on light surfaces, `Zone5` is dim on dark/AMOLED backgrounds, and `WPrimeBalGreen` has only 2.53:1 contrast on white). Furthermore, users have no global preference mechanism to configure metric graph colors consistently across Workout Detail and Cockpit sparklines. Establishing a centralized 8-color Light/Dark palette with WCAG 2.1 compliance ($\ge 3.0:1$), canonical metric defaults, a downgrade-safe SharedPreferences store, and reactive graph integration ensures accessibility, aesthetic uniformity, and user personalization (ATT-7759, Parent Epic ATT-5908, refining `REQ-UI-206`).
- **Description**:
  1. *Centralized Fixed Telemetry Graph Color Palette (`REQ-UI-530.1`)*:
     - `ui/theme/Color.kt` SHALL define `TelemetryGraphColor` featuring 8 Light/Dark-aware color tokens (`CRIMSON`, `VIOLET`, `EMERALD`, `AMBER`, `TEAL`, `MAGENTA`, `CYAN`, `CORAL`) plus `THEME_DEFAULT` (`MaterialTheme.colorScheme.primary`).
     - Every palette token SHALL satisfy WCAG 2.1 non-text contrast ($\ge 3.0:1$) against Light (`#FFFFFF`), Dark (`#16161A`), and AMOLED (`#000000`) surfaces.
     - `TelemetryGraphColor.resolveColor(colorScheme: ColorScheme, isDarkTheme: Boolean): Color` SHALL resolve `colorScheme.primary` for `THEME_DEFAULT`, and the respective high-contrast light or dark hex variant for the 8 palette tokens.
  2. *Canonical Metric Default Color Assignments (`REQ-UI-530.2`)*:
     - Default telemetry graph colors SHALL be assigned canonically:
       - Heart Rate $\rightarrow$ `TelemetryGraphColor.CRIMSON`
       - Power $\rightarrow$ `TelemetryGraphColor.VIOLET`
       - W'bal $\rightarrow$ `TelemetryGraphColor.EMERALD` (Light `#059669`, Dark `#10B981`)
       - HRV DFA $\alpha_1$ $\rightarrow$ `TelemetryGraphColor.AMBER`
       - HRV rMSSD $\rightarrow$ `TelemetryGraphColor.TEAL`
       - Speed, Pace, and cockpit-only sensor types (Cadence, Altitude, Grade, Temperature) $\rightarrow$ `TelemetryGraphColor.THEME_DEFAULT`
  3. *Global Preference Store & Safe Deserialization (`REQ-UI-530.3`)*:
     - A global preference store (`TelemetryGraphColorPreferences`) in `com.atrainingtracker.trainingtracker.settings` SHALL persist user-selected graph colors per metric family using `SharedPreferences`.
     - The preference store SHALL provide `getGraphColor(metricType: TelemetryMetricType): TelemetryGraphColor`, `setGraphColor(metricType: TelemetryMetricType, color: TelemetryGraphColor)`, `resetGraphColor(metricType: TelemetryMetricType)`, and `resetAllGraphColors()`.
     - Sensor-based overloads SHALL support mapping `SensorType` families to their corresponding preference keys.
     - Deserialization of persisted enum names SHALL use safe deserialization (`safeEnumValueOf` or safe lookup) falling back gracefully to canonical metric defaults upon invalid or unknown tokens (`REQ-ARC-001`).
  4. *TelemetryMetricGraph Integration & Reactive Theme Luminance Adaptation (`REQ-UI-530.4`)*:
     - `TelemetryMetricGraph.kt` SHALL resolve `accentColor` via `TelemetryGraphColorPreferences` using the active surface luminance (`TTColor.relativeLuminance(colorScheme.surface) < 0.5f`) to ensure proper high-contrast rendering under `CockpitThemeMode.ALWAYS_DARK` and AMOLED surfaces.
     - Graph geometry (paddings `50.dp`/`25.dp`/`24.dp`), zone background bands, scrubbing markers, and histogram calculations SHALL remain preserved and unregressed.

**Acceptance Criteria (Given-When-Then)**:
• *Given* a fresh installation with no custom graph color overrides,
• *When* the athlete views Workout Detail charts (`TelemetryMetricGraph`),
• *Then* Heart Rate, Power, W'bal, HRV DFA α1, and HRV rMSSD render in Crimson, Violet, Emerald, Amber, and Teal respectively, while Speed and Pace render in `MaterialTheme.colorScheme.primary`.
• *Given* any color in the fixed 8-color telemetry palette,
• *When* rendered in Light, Dark, or AMOLED mode,
• *Then* the resolved color achieves $\ge 3.0:1$ non-text contrast against the underlying surface and satisfies `ThemeColorCentralizationAuditTest`.
• *Given* the global graph color preference for Heart Rate is updated to Cyan,
• *When* the athlete opens a workout in Workout Detail,
• *Then* the Heart Rate `TelemetryMetricGraph` renders using the Cyan palette token.
• *Given* an invalid or corrupt color string persisted in SharedPreferences,
• *When* `TelemetryGraphColorPreferences` reads the preference,
• *Then* it safely recovers and returns the canonical metric default without crashing.

**Invariants**: `TTColor.Zone1..7` and `TTColor.WPrimeBalGreen` tokens unaltered; zero `Color(0x...)` outside `Color.kt`; graph layout dimensions (`50.dp`/`25.dp`/`24.dp`) preserved; 100% 9-language localization parity.

### REQ-UI-2781: Conditional Section Dividers in SensorSourceDialog [Verified]
- **Tests**: `TST-UI-2781`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `SensorSourceDialog.kt`
- **Rationale**: In `SensorSourceDialog`, horizontal dividers (`HorizontalDivider`) separate distinct operational tiers (Altitude Calibration, Source Device, Active Backups, and Not Connected). Dividers SHALL only be rendered between two rendered content sections. When a section is not preceded by any rendered section (such as when `sourceDevice == null` and `activeBackups.isEmpty()`), no leading divider SHALL be rendered at the top of the dialog body. When a section has no subsequent section rendered, no trailing divider SHALL be emitted.
- **Description**: Conditional Section Dividers in `SensorSourceDialog`.

1. *Sequential Section Guarding (`REQ-UI-2781.1`)*:
• In `SensorSourceDialog.kt`, section rendering SHALL maintain a sequential preceding-section guard (`hasPrecedingSection`).
• The Altitude Calibration section (`sensorType == SensorType.ALTITUDE && onCalibrateAltitude != null`) SHALL NOT emit a trailing `HorizontalDivider` unconditionally.
• The Source Device section (`sourceDevice != null`), Active Backups section (`activeBackups.isNotEmpty()`), and Not Connected section (`notConnected.isNotEmpty()`) SHALL emit a `HorizontalDivider` preceding their header if and only if a prior section has rendered content.
• If no preceding section was rendered (e.g. for disconnected sensors where `sourceDevice == null` and `activeBackups.isEmpty()`), the Not Connected section SHALL NOT render a leading `HorizontalDivider`.

2. *Duplicate Divider Elimination (`REQ-UI-2781.2`)*:
• Under no permutation of sensor availability or calibration settings SHALL two `HorizontalDivider` elements be rendered adjacent to each other.
• If only the Altitude Calibration card is rendered without any subsequent device blocks, no trailing `HorizontalDivider` SHALL be displayed above the dialog confirmation actions.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement `REQ-UI-2781`, extending `REQ-UI-049` (*Sensor Source Dialog*) and `REQ-UI-268` (*Canonical Priority Sorting in SensorSourceDialog*), targeting `SensorSourceDialog.kt`.
2. *Historical Origin & Commit Trace*: Introduced in Sprint `2026-40.1` (`aa0184f0`), augmented in `ATT-4845`/`ATT-5072` (altitude calibration) and `ATT-7544` (bike radar).
3. *Root Reason for Existing Formulation*: Dividers were added incrementally inside section blocks on the assumption that preceding sections were present. When a category had only disconnected devices, the divider became an orphaned top-level line.
4. *Preservation of Core Invariants*: Preserves all sensor category sections, semantic header colors (`REQ-UI-051`), canonical sorting order (`REQ-UI-268.5`), and device click navigation without regression.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an athlete opening `SensorSourceDialog` for a sensor with only disconnected devices (`sourceDevice == null` and `activeBackups.isEmpty()`),
• *When* the dialog content renders,
• *Then* zero `HorizontalDivider` composables SHALL precede the "Nicht verbunden" (`R.string.source_not_connected`) section header.
• *Given* an athlete opening `SensorSourceDialog` for `SensorType.ALTITUDE` with calibration active and no connected source devices,
• *When* both the calibration card and disconnected devices render,
• *Then* exactly one `HorizontalDivider` SHALL separate the calibration card from the "Nicht verbunden" section, with zero stacked dividers.
• *Given* an athlete opening `SensorSourceDialog` with an active `sourceDevice` and active backups,
• *When* the dialog content renders,
• *Then* exactly one `HorizontalDivider` SHALL separate `sourceDevice` from `activeBackups`.

**Invariants**: Layout spacing, semantic color tokens (`TTColor.SensorSourceActive`, `TTColor.SensorBackupActive`), device click actions, and canonical priority sorting (`REQ-UI-268.5`) MUST NOT be altered.

### REQ-UI-2782: EditSensorFieldDialog Logical Section Ordering [Verified]
- **Target Release**: `V5.0.0`
- **Tests**: `TST-UI-2782`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `EditSensorFieldDialog.kt`
- **Rationale**: In `EditSensorFieldDialog.kt`, incremental feature additions interleaved data-source, tile-geometry, and visual-presentation controls so that enabling the live Sparkline graph pushed `Relative Breite` and `Filter / Glättung` below the fold, separating `Quelle` from `Filter / Glättung` and `Textgröße` from `Relative Breite` (`ATT-8462`). Regrouping the dialog body into three cohesive vertical tiers improves scannability and keeps high-frequency data and geometry controls above the visual graph editor.
- **Description**: Logical 3-Group Vertical Ordering in `EditSensorFieldDialog`.

1. *Group 1 — Messdaten (Data & Signal) (`REQ-UI-2782.1`)*:
• `EditSensorFieldDialog.kt` SHALL render Group 1 in strict top-to-bottom order: `SensorTypeSelectorSection` -> `Spinner` (*Quelle*, omitted when `deviceList.size == 1 && deviceList[0].first == -1L`) -> `OutlinedButton` (*Filter / Glättung*, rendered strictly when `uiState.selectedSensorType?.filteringPossible == true`).

2. *Group 2 — Kachel-Geometrie (Tile Geometry) (`REQ-UI-2782.2`)*:
• Immediately below Group 1, `EditSensorFieldDialog.kt` SHALL render Group 2 in strict top-to-bottom order: `Spinner` (*Textgröße*) -> *Relative Breite* (`1x`, `2x`, `3x` `FilterChip` row, rendered strictly when `uiState.isMultiFieldRow == true`).

3. *Group 3 — Visuelle Darstellung & Metrik-Hinweise (`REQ-UI-2782.3`)*:
• Immediately below Group 2, `EditSensorFieldDialog.kt` SHALL render Group 3 in strict top-to-bottom order: *Anzeigeelemente* (`FilterChip`s *Wert* / *Balken* / *Graph*, gated by `uiState.isBarSupported || uiState.isGraphSupported`) -> expanded Graph options (`uiState.isGraphSupported && uiState.showGraph`: Sparkline Time Horizon `Slider`, Graph Color Editor chips/swatches/`SingleChoiceSegmentedButtonRow`, and Live `SensorSparklineView` preview) -> metric-specific guidance blocks (`POWER_TO_WEIGHT` weight button + `AthleteWeightDialog`, `INTENSITY_FACTOR`/`TSS` FTP `Surface`, and `isHrvMetric` info button + `HrvInfoBottomSheet`).
• All existing `EditSensorFieldViewModel` callbacks, conditional visibility guards, `ConfigureFilterDialog` overlay wiring, `AppModalBottomSheet(scrollable = true)` scroll safety, and 9-locale string parity MUST remain preserved without adding new string keys or altering ViewModel/DB state.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement `REQ-UI-2782`, complementing `REQ-UI-471.3` (*Field Configuration Dialog Polish*), `REQ-UI-437.3` (*Relative Width Cardinality Gating*), `REQ-UI-395` (*Categorized Sensor Picker*), `REQ-TRK-059.3/.11` (*Display Elements, Slider & Sparkline Preview*), and `REQ-TRK-081.3` (*Graph Color Editor*), targeting `EditSensorFieldDialog.kt`.
2. *Historical Origin & Commit Trace*: Built incrementally across `ATT-3852` (`SensorTypeSelectorSection`), `ATT-4861`/`ATT-5192` (`Relative Breite`), `ATT-1294`/`ATT-6690`/`ATT-7314` (Display Elements, Slider, Preview), and `ATT-7760` (`65d2c2d5`, Graph Color Editor).
3. *Root Reason for Existing Formulation*: Subsequent controls were appended sequentially to the bottom of the `Column` without regrouping earlier geometry and filter controls.
4. *Preservation of Core Invariants*: Preserves all conditional guards (`deviceList` single-best check, `filteringPossible`, `uiState.isMultiFieldRow`, `isBarSupported || isGraphSupported`, `isGraphSupported && showGraph`, metric-type checks), `REQ-TRK-081.3` color-editor placement below the time-horizon slider, `REQ-UI-471.3` title/icon/alignment polish, `AppModalBottomSheet` scroll safety, and 9-language localization parity.

**Acceptance Criteria (Given-When-Then)**:
• *Given* `EditSensorFieldDialog` with all three groups active (`deviceList` size > 1, `filteringPossible = true`, `isMultiFieldRow = true`, `isGraphSupported = true`, `showGraph = true`, and metric guidance applicable),
• *When* the dialog body renders,
• *Then* vertical `boundsInRoot().top` positions strictly satisfy `Sensortyp < Quelle < Filter < Textgröße < Relative Breite < Anzeigeelemente < Zeitfenster < Graph-Farbe < Vorschau < Metrik-Hinweis`.
• *Given* a field where `filteringPossible == false` or `isMultiFieldRow == false` or `deviceList` contains only `-1L` or `showGraph == false`,
• *When* the dialog renders,
• *Then* each disabled section is omitted while all remaining active sections preserve the strict Group 1 -> Group 2 -> Group 3 vertical order.
• *Given* `EditSensorFieldDialog` rendered in Light or Dark mode,
• *When* any control in Group 1, Group 2, or Group 3 is interacted with,
• *Then* the corresponding `EditSensorFieldViewModel` callback fires and the sheet renders cleanly without clipping, overlapping, or nested scrolling.

**Invariants**: Zero `EditSensorFieldViewModel` / DB schema / repository signature changes; all conditional guards and callbacks preserved; `AppModalBottomSheet` scroll safety preserved; 9-locale parity unchanged.

### REQ-UI-2783: ZoneDisplayOptions Relocation to Sensor Tile Long-Press & Tracking Tabs Config [Verified]
- **Target Release**: `V5.0.0`
- **Tests**: `TST-UI-2783`, `TST-TRK-087`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `ZoneDisplayOptionsControls.kt`, `SettingsDataStore.kt`, `EditSensorFieldDialog.kt`, `EditSensorFieldViewModel.kt`, `SensorGridScreen.kt`, `TrackingTabGridContent.kt`, `ZonesSettingsActivity.kt`, `AdvancedTuningScreen.kt`, `AdvancedTuningDialog.kt`, `CockpitTypographySection.kt`
- **Rationale**: Per Ceremony 2 PO Rejection (`2026-11-17T15:59`), configuring `ZoneDisplayOptions` (`showBackground`, `showLeftBar`, `showRightBar`, `showTextColor`, `showGauge`) inside Expert Settings (`AdvancedTuningScreen` / `AdvancedTuningDialog` / `CockpitTypographySection`) was rejected as the wrong UX surface (`ATT-8498`). Relocating these controls directly to (1) long-pressing a sensor tile (`EditSensorFieldDialog`) and (2) tracking tabs configuration (`ScreenMode.CONFIGURATION` in `TrackingTabsScreen` / `SensorGridScreen`) places zone visualization where athletes edit tiles and cockpit layouts, while preserving shared `ZoneDisplayOptionsControls` reuse with `ZonesSettingsActivity`.
- **Description**: Removal from Expert Settings and Dual Placement in Sensor Tile Editor & Tracking Tabs Configuration.

1. *Removal from Expert Settings (`REQ-UI-2783.1`)*:
• `AdvancedTuningScreen.kt`, `AdvancedTuningDialog.kt`, and `CockpitTypographySection.kt` SHALL NOT contain `ZoneDisplayOptions` controls, `ZoneDisplayOptionsDraft` state/persistence/reset wiring, or zone-color/gauge overrides in `PreviewCockpitTile`.
• `CockpitZoneDisplaySubsection.kt` and `AdvancedTuningZoneDisplayTest.kt` SHALL be deleted (eliminating the `file.readLines()` `src/main` violation under `REQ-PRO-049.1`), and `AdvancedTuningSectionsScreenshotTest.kt` SHALL remove `testCaptureCockpitZoneDisplayScreenshot_LightAndDark`. `CockpitTypographySection.kt` MUST restore its pre-`81af2506` inline `ExposedDropdownMenuBox` selectors (`< 400` LOC per `REQ-UI-277.3`) and satisfy `CockpitPreviewTileContractTest`, `AdvancedTuningModularityTest`, and `TuningDesignGuidelinesContractTest`.

2. *Sensor Tile Long-Press Placement (`EditSensorFieldDialog` / `EditSensorFieldViewModel`, `REQ-UI-2783.2`)*:
• When editing or adding a sensor tile in `EditSensorFieldDialog` (opened on tile long-press in `TRACKING` or tap in `CONFIGURATION`), `(selectedSensorType, sportType)` SHALL map 1:1 with `TrackingViewModel.resolveDisplayOptions` (`(HR, RUN) -> HR_RUN`, `(HR, BIKE) -> HR_BIKE`, `(POWER, BIKE) -> PWR_BIKE`, `(HRV_DFA_A1, *) -> HRV_DFA_A1`).
• For those 4 zone-capable pairs, Group 3 of `EditSensorFieldDialog` SHALL render `ZoneDisplayOptionsToggleList` (`showGaugeToggle = target == HRV_DFA_A1`) + `zone_display_apply_all` without nested `verticalScroll` (`AppModalBottomSheet` scroll safety), hydrating from `SettingsDataStore`, persisting ONLY on **Save** (`saveChanges()`), and discarding on **Cancel**. Non-zone sensors (and `HR`/`POWER` in unmapped sports) MUST omit the Zone Display section.

3. *Tracking Tabs Configuration Placement & Shared Invariants (`REQ-UI-2783.3`)*:
• Within `ScreenMode.CONFIGURATION` (`TrackingTabsScreen` / `SensorGridScreen`), athletes SHALL have access to profile-scoped `ZoneDisplayOptions` controls (`SingleChoiceSegmentedButtonRow` across `HR_RUN`, `HR_BIKE`, `PWR_BIKE`, `HRV_DFA_A1` pre-selected from `ActivityType`, `ZoneDisplayOptionsToggleList`, and `zone_display_apply_all`), persisting immediately to `SettingsDataStore` (matching `SpatialCockpitToggleCard` semantics).
• Shared `ZoneDisplayOptionsControls` in `ui/settings/` SHALL serve `ZonesSettingsActivity`, `EditSensorFieldDialog`, and `SensorGridScreen` cleanly while preserving `ZonesSettingsActivity` 4-toggle `ZoneDisplayOptionsToggleList(showGaugeToggle = false)` parity (`REQ-TRK-046.4`, `REQ-TRK-087` — removing the duplicate `zone_display_gauge` row and setting `previewFieldState.showBar = true` in `HrvDfaA1SettingsContent`), zone thresholds, and 100% 9-locale parity (`values`..`values-pt`) across existing `zone_display_*` (`zone_display_title`, `zone_display_background`, `zone_display_left_bar`, `zone_display_right_bar`, `zone_display_text_color`, `zone_display_gauge`, `zone_display_apply_all`) and profile labels (`tab_hr_run`, `tab_hr_bike`, `tab_pwr_bike`, `tab_hrv_dfa_a1`) with zero new untranslated string keys.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: `REQ-UI-2783` (originally `ATT-8498` Expert Settings integration), refining `REQ-UI-172` (*Zone Display Options*), `REQ-UI-262.5`, `REQ-UI-277.3`, `REQ-UI-376`, `REQ-TRK-061.4`, and `REQ-PRO-049.1`.
2. *Historical Origin & Commit Trace*: `ATT-1390` (`e3966ebf`) introduced `ZoneDisplayOptions` in `ZonesSettingsActivity.kt`; `ATT-8498` (`81af2506`) placed them in `CockpitTypographySection.kt`, which failed Ceremony 2 PO Review (`2026-11-17T15:59`) and introduced a `src/main` file-read violation in `AdvancedTuningZoneDisplayTest.kt`.
3. *Root Reason for Existing Formulation*: `81af2506` co-located zone visualization with Cockpit typography/border tuning inside Expert Settings; the PO clarified athletes expect zone display controls directly on long-pressed sensor tiles and in tracking tabs configuration.
4. *Preservation of Core Invariants*: `CockpitTypographySection.kt < 400` LOC (`REQ-UI-277.3`), `CockpitPreviewTileContractTest`, `AdvancedTuningModularityTest`, `TuningDesignGuidelinesContractTest`, `HrvDfaA1SettingsContractTest`, zero `src/main` test reads (`REQ-PRO-049.1`), zone threshold integrity in `SettingsDataStore`, and 9-locale parity remain preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* Expert Settings (`AdvancedTuningScreen` / `AdvancedTuningDialog` -> **Cockpit & Kacheln**), *When* expanded, *Then* zero `ZoneDisplayOptions` controls or zone preview overrides appear and `CockpitTypographySection.kt < 400` LOC.
• *Given* a zone-capable sensor tile (`HR` in Run/Bike, `POWER` in Bike, or `HRV_DFA_A1`) opened via long-press (`EditSensorFieldDialog`), *When* toggles or `zone_display_apply_all` are modified and **Cancel** is tapped, changes discard; *When* **Save** is tapped, changes persist in `SettingsDataStore` without altering thresholds; *Given* a non-zone tile (e.g. `SPEED`), the Zone Display section is hidden.
• *Given* `TrackingTabsScreen` in `ScreenMode.CONFIGURATION`, *When* an athlete switches `ZoneProfileTarget` via `SingleChoiceSegmentedButtonRow` and toggles `ZoneDisplayOptions` or taps `zone_display_apply_all` (preserving `HRV.showGauge`), *Then* `SettingsDataStore` updates immediately and `ZonesSettingsActivity` / `TrackingViewModel` reflect the update.
• *Given* all 9 locale `strings.xml` files and Light/Dark themes (`Rule 8.5`), *When* contract, localization, unit/UI, and screenshot tests execute, *Then* `TranslationParityTest`, `ZoneDisplayLocalizationTest`, `SourceTextReadingAuditContractTest`, and Light/Dark PNG verification pass with zero `src/main` reads.

**Invariants**: Zero `ZoneDisplayOptions` UI/state in `AdvancedTuning*`/`CockpitTypographySection`; `CockpitTypographySection < 400` LOC; `ZonesSettingsActivity` & `HrvDfaA1SettingsContractTest` preserved; zero `src/main` reads in new/modified tests (`REQ-PRO-049.1`); zone thresholds untouched; 9-locale parity maintained.

### REQ-UI-2784: RadarTrafficSummaryCard Canonical Speed Unit Formatting (`Rule 36`) [Verified]
- **Target Release**: `V5.0.0`
- **Tests**: `TST-UI-2784`
- **System Requirement(s)**: `SYSREQ-TRK-006`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `RadarTrafficSummaryCard.kt`, `MetricFormatterContext.kt`, `SpeedFormatter.kt`, `WorkoutDetailTabbedContent.kt`
- **Rationale**: `RadarTrafficSummaryCard.kt` (`L131`, `L138`) hardcoded `String.format(locale, "%.1f km/h", ... * 3.6)` for `summary.maxApproachSpeedMps` and `summary.maxVehicleSpeedMps`, showing `km/h` to Imperial (`MyUnits.IMPERIAL`) athletes in violation of `Rule 36` (Single Source of Truth). Delegating speed formatting to `LocalMetricFormatter.current.speed.format_with_units(...)` aligns `RadarTrafficSummaryCard` with sibling aftermath cards (`TrackOnMapScreen.kt`, `WorkoutSummaryCompact.kt`) (`ATT-8440`, refining `REQ-RAD-005`):
- **Description**:
  1. *Canonical Speed Formatting (`REQ-UI-2784.1`)*:
     - `RadarTrafficSummaryCard.kt` SHALL format `summary.maxApproachSpeedMps` and `summary.maxVehicleSpeedMps` using `LocalMetricFormatter.current.speed.format_with_units(...)` (`SpeedFormatter.kt` via `MetricFormatterContext.kt`), emitting `km/h` (`* 3.6`) under `MyUnits.METRIC` and `mile/h` (`* 2.23693629` via `MyHelper.mps2userUnit`) under `MyUnits.IMPERIAL`.
  2. *Scope & Guard Preservation (`REQ-UI-2784.2`)*:
     - `RadarTrafficSummaryCard` SHALL preserve its early-return guard `if (summary == null || summary.totalVehicles <= 0) return`, `locale` formatting on `vehiclesPerKm` / `vehiclesPerHour` (`L110`, `L117`), `minPassingDistanceM` (`L157`), and `WorkoutDetailTabbedContent.kt:932` integration; `RadarTrafficSummaryFormatter.kt` (`REQ-RAD-007`) remains untouched.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new `REQ-UI-2784` refining `REQ-RAD-005` (`ATT-5088`) and enforcing `Rule 36`; targets `RadarTrafficSummaryCard.kt:131,138`.
2. *Historical Origin & Commit Trace*: Commit `624bea5e` (`ATT-5088`) introduced `RadarTrafficSummaryCard.kt`; commit `6a694bb1` added `LocalConfiguration.current.locales[0]`.
3. *Root Reason for Existing Formulation*: Initial implementation hardcoded `* 3.6` and `"km/h"` prior to `LocalMetricFormatter.current.speed` alignment.
4. *Preservation of Core Invariants*: Preserves null/zero-vehicle early return (`L56`), non-speed density/clearance formatting (`L110`, `L117`, `L157`), `Locale.getDefault()` in `SpeedFormatter`, and `RadarTrafficSummaryFormatter.kt` untouched.

**Acceptance Criteria (Given-When-Then)**:
• *Given* `RadarTrafficSummary` with `totalVehicles > 0`, `maxApproachSpeedMps = 10.0`, `maxVehicleSpeedMps = 20.0`,
  - *When* `TrainingApplication.getUnit()` is `MyUnits.METRIC`, *Then* `RadarTrafficSummaryCard` displays `36.0 km/h` (or `36,0 km/h`) and `72.0 km/h` (or `72,0 km/h`).
  - *When* `TrainingApplication.getUnit()` is `MyUnits.IMPERIAL`, *Then* `RadarTrafficSummaryCard` displays `22.4 mile/h` (or `22,4 mile/h`) and `44.7 mile/h` (or `44,7 mile/h`).
• *Given* `summary == null` or `summary.totalVehicles <= 0`, *When* `RadarTrafficSummaryCard` composes, *Then* nothing is rendered.

**Invariants**: Zero SQLite/DTO changes; `TrainingApplication.setUnitForTesting(null)` cleanup in tests; Light/Dark theme readability preserved; `ANT-Android-SDKs` untouched.

### REQ-UI-2785: Enlarged Lieblingsorte Card Preview Map (175x120 dp), Zoom Calibration & 360dp Overflow Protection [Verified]
- **Target Release**: `V5.0.0`
- **Tests**: `TST-UI-2785`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit, On-Device Manual
- **Implementation**: `KnownLocationsScreen.kt`, `KnownLocationZoomMath.kt`
- **Rationale**: In `KnownLocationsScreen.kt`, `KnownLocationThumbnailMap` rendered at `116.dp × 80.dp` (`L673`), leaving excess whitespace beside the metadata column while cramping the centered heart marker, geofence circle, and Google watermark. Enlarging the preview to `175.dp × 120.dp` and calibrating `KnownLocationZoomMath.calculateThumbnailZoom` to `120.0 dp` improves neighborhood & geofence legibility while protecting the `109.dp` metadata column on `360.dp` viewports (`ATT-8749`).
- **Description**:
  1. *Enlarged Preview Map & Spacer (`REQ-UI-2785.1`)*:
     - `KnownLocationThumbnailMap` (`KnownLocationsScreen.kt`) SHALL render at `Modifier.size(width = 175.dp, height = 120.dp)` with KDoc updated accordingly, and the `Spacer` separating the left metadata `Column` from `KnownLocationThumbnailMap` SHALL be reduced from `16.dp` to `12.dp` (preserving `RoundedCornerShape(12.dp)`, `liteMode(true)`, `location_map_preview_${item.id}` testTag, `createCenteredHeartGlyphMarker`, `LocalInspectionMode` canvas `size.minDimension * 0.35f`, `IntrinsicSize.Min` bottom alignment, and click/long-press callbacks).
  2. *Calibrated Zoom Math (`REQ-UI-2785.2`)*:
     - `KnownLocationZoomMath` SHALL define `const val DEFAULT_THUMBNAIL_HEIGHT_DP: Double = 120.0` and expose `thumbnailHeightDp: Double = DEFAULT_THUMBNAIL_HEIGHT_DP` on `calculateThumbnailZoom(radiusMeters, latitude, thumbnailHeightDp)` (`+log2(1.5) ≈ +0.585` zoom levels over `80.0 dp`, keeping `MIN_ZOOM = 9.7f` and `MAX_ZOOM = 11.7f` without introducing a Detekt `MagicNumber`).
  3. *Narrow `360.dp` Column Overflow Protection (`REQ-UI-2785.3`)*:
     - Inside the `296.dp` card inner width on `360.dp` screens (`109.dp` left `weight(1f)` metadata `Column` beside `175.dp` map + `12.dp` `Spacer`), Altitude `Text` MUST set `maxLines = 1` and `overflow = TextOverflow.Ellipsis`, and the `SportStartsItem` row MUST guard against horizontal crowding/clipping (via wrapping `FlowRow` or compact spacing) even with 3 multi-digit sport counts at `130%` font scale.
  4. *Localization & Test Hygiene (`REQ-UI-2785.4`)*:
     - `Localization Parity: N/A - no UI strings` (`values*/strings*.xml` untouched); new behavioral `createComposeRule()` UI tests MUST execute with `0` new `src/main` file reads (`REQ-PRO-049.1`).

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Refines `REQ-UI-217`, `REQ-UI-224`, `REQ-UI-244`, `REQ-UI-465.3`, `REQ-UI-469.4`, and `REQ-UI-470.1` on `KnownLocationsScreen.kt` and `KnownLocationZoomMath.kt`.
2. *Historical Origin & Commit Trace*: `b24a3c20` (`ATT-1813`, `80dp`), `d7b00bf8` (`ATT-1878`, `80.0`), `e29c614b` (`ATT-2051`, `100dp`), `4c095473` (`ATT-5825`, `116x80 dp`), and `ce73d561` (`ATT-5884`, `9.7f..11.7f`).
3. *Root Reason for Existing Formulation*: `116×80 dp` minimized card height when badges wrapped; `80.0` in `KnownLocationZoomMath` matched the `80 dp` map height prior to enlargement.
4. *Preservation of Core Invariants*: `RoundedCornerShape(12.dp)`, `liteMode(true)`, `createCenteredHeartGlyphMarker` (`anchor = Offset(0.5f, 0.5f)`), `LocalInspectionMode` canvas, `IntrinsicSize.Min` alignment, `MIN_ZOOM = 9.7f`/`MAX_ZOOM = 11.7f`, test tags, and 1-tap drill-down/edit/long-press callbacks remain intact.

**Acceptance Criteria (Given-When-Then)**:
• *Given* `KnownLocationCard` rendered at `360.dp` viewport width with 3 multi-digit `SportStartsItem` counts and long altitude text at 130% font scale, *When* composed in Light/Dark theme, *Then* `location_map_preview_<id>` measures `175.dp × 120.dp` and left-column metadata renders without horizontal clipping or overlap.
• *Given* `KnownLocationZoomMath.calculateThumbnailZoom(radiusMeters, 48.137)`, *When* called with default `DEFAULT_THUMBNAIL_HEIGHT_DP = 120.0`, *Then* `500m` clamps to `11.7f`, `1000m` evaluates `~11.10f`, `1500m` evaluates `~10.51f`, and `2000m` evaluates `~10.10f` within `[9.7f, 11.7f]`.

**Invariants**: `WorkoutClusterComponents.kt` (`110×75 dp`), `EditKnownLocationDialog.kt`, `MIN_ZOOM`/`MAX_ZOOM`, and geofence marker geometry remain unchanged; zero `strings*.xml` edits; zero new `src/main` test reads (`REQ-PRO-049.1`).

### REQ-UI-2786: Workout Detail Header Action Icon Uniformity, Global-vs-Unit Grouping & Universal Cluster Swap Visibility [Verified]
- **Target Release**: `V5.0.0`
- **Tests**: `TST-UI-2786`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit, On-Device Manual
- **Implementation**: `WorkoutHeader.kt`, `WorkoutDetailTabbedContent.kt`, `TrackOnMapScreen.kt`, `TrackOnMapAftermathViewModel.kt`, `WorkoutClusterRepository.kt`, `WorkoutClustersScreen.kt`, `WorkoutClusterHeatmapScreen.kt`, `values*/strings_workout_header.xml`
- **Rationale**: On-device Sprint Review of `ATT-7754` revealed three Workout Detail top-right header defects (`ATT-8097`, refining `REQ-UI-408.5`, `REQ-UI-447`, and `REQ-SET-059.4`): (1) `SwapHoriz` (`⇄`) in `WorkoutClustersScreen.kt:186-193` rendered black (`LocalContentColor.current`) at `48.dp` instead of `MaterialTheme.colorScheme.primary` at `32.dp`; (2) `[⇄][Tune][Edit][Share]` placed global tab-layout config (`Tune`) inside per-workout actions without visual or tooltip distinction; and (3) omitting `headerActions` in `WorkoutSummariesTabbedScreen`, `WorkoutSummariesListFragment`, and `PeriodMapScreen` hid `⇄` outside Lieblingsstrecken, while indoor/non-GPS workouts lacked a `startLatLng == null` fallback.
- **Description**:
  1. *Uniform Action Icon Tint & Sizing (`REQ-UI-2786.1`)*:
     - All Workout Detail header action icons (`Tune`, `SwapHoriz`, `Edit`, `Share`) SHALL render with `tint = MaterialTheme.colorScheme.primary` and `Modifier.size(32.dp)` inside `WorkoutHeader.kt` (retaining `MaterialTheme.colorScheme.error` at `32.dp` for diagnostic `WarningAmber`).
  2. *Global-vs-Per-Workout Ordering, Visual Grouping, Tooltips & `actionSpacerWidth` (`REQ-UI-2786.2`)*:
     - `WorkoutHeader` SHALL expose optional `onOpenTabsConfig: (() -> Unit)? = null` and `onChangeCluster: (() -> Unit)? = null` slots rendered left-to-right as `[Tune]` (Global/Persistent Display Configuration) before `[⇄] [Edit] [Share]` (Per-Workout Actions, with `WarningAmber` if present).
     - `[Tune]` SHALL be visually distinguished from the per-workout action group via a subtle tonal pill container (`MaterialTheme.colorScheme.primary.copy(alpha = TTAlpha.Faint)`, `RoundedCornerShape(8.dp)`) and a `16.dp` `VerticalDivider` (`outlineVariant`) before `[⇄] [Edit] [Share]`, keeping compact `360–411 dp` width without horizontal text labels.
     - Each action button SHALL wrap `TooltipBox` + `PlainTooltip` with localized 9-language strings (`values*/strings_workout_header.xml`: `workout_header_tabs_config_tooltip`, `workout_header_change_cluster_tooltip`, `workout_header_edit_tooltip`, `workout_header_share_tooltip`) clarifying global display layout vs. single-workout scope, alongside descriptive TalkBack `contentDescription`s.
     - `WorkoutHeader` `actionSpacerWidth` MUST sum every rendered header action (`onOpenTabsConfig` including pill/divider clearance, `onChangeCluster`, `WarningAmber`, `menuEnabled`, `onEditWorkout`, `onShareWorkout`) so long titles never collide with the action strip on `360–411 dp` screens.
  3. *Universal `⇄` Cluster-Change Visibility & Non-GPS Fallback (`REQ-UI-2786.3`)*:
     - `TrackOnMapScreen.kt` SHALL centrally wire `onChangeCluster` into `WorkoutDetailTabbedContent` -> `WorkoutHeader` across all 5 callers (`WorkoutSummariesTabbedScreen`, `WorkoutSummariesListFragment`, `PeriodMapScreen`, `WorkoutClustersScreen`, `WorkoutClusterHeatmapScreen`), defaulting to a built-in `WorkoutClusterSelectionDialog` backed by `TrackOnMapAftermathViewModel` / `WorkoutClusterRepository` when no caller override is supplied (and migrating `WorkoutClustersScreen` & `WorkoutClusterHeatmapScreen` from ad-hoc `headerActions` to `onChangeCluster`).
     - For non-GPS/indoor workouts (`startLatLng == null || endLatLng == null || maxDisplacementLatLng == null`), candidate scoring SHALL fall back to all clusters from `WorkoutClusterRepository`, selecting a cluster SHALL route via `assignClusterToWorkout` (`forceIdentity = true`, avoiding `moveWorkoutToCluster`'s early return), `onCreateNew` MUST be hidden (`null`), and `onUnassign` SHALL remain available whenever `clusterId > 0`.
  4. *Architectural & Test Invariants (`REQ-UI-2786.4`)*:
     - `WorkoutRepository.kt` MUST remain untouched (`<= 5,117` LoC God-class safety); new cluster selection/assignment orchestration belongs in `WorkoutClusterRepository.kt` / `TrackOnMapAftermathViewModel.kt`, keeping `ArchitectureConformanceContractTest` (`IF-SWE`) clean (zero `*DatabaseManager`/`*DbHelper` in UI).
     - `WorkoutSummary` list cards (`menuEnabled = true`, `onOpenTabsConfig = null`, `onChangeCluster = null`), `snapshotHeader` (`MapDetailLayout`), `WorkoutDetailHeaderClickContractTest` (`onClicked = null`), `WorkoutHeaderLandscapeTest` (`REQ-UI-433.2`), and `WorkoutClusterSelectionDialog`'s public signature remain preserved.
     - New Robolectric `createComposeRule()` UI tests MUST read `0` `src/main` files (`REQ-PRO-049.1`), and Stage 5 SHALL capture and inspect Light/Dark `@Preview` PNG `docs/engineering/walkthroughs/img/ATT-8097_header_actions_preview.png` (`Rule 8.5`).

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Refines `REQ-UI-408.5` (`WorkoutDetailTabbedContent.kt`), `REQ-UI-447` (`WorkoutHeader.kt`), and `REQ-SET-059.4` (`WorkoutClustersScreen.kt` / `WorkoutClusterHeatmapScreen.kt`) via net-new `REQ-UI-2786`.
2. *Historical Origin & Commit Trace*: `ea5fa705` (`ATT-3920`, `Tune` in `WorkoutDetailTabbedContent`), `ab9bebee` (`ATT-5809`, `Share` & `actionSpacerWidth` in `WorkoutHeader`), and `949078cd` (`ATT-7754`, `onUnassign` & peek refresh).
3. *Root Reason for Existing Formulation*: `headerActions` was used as an ad-hoc slot for cluster screens while `WorkoutHeader` decoupled `Edit`/`Share`, unintentionally omitting `⇄` on `Trainingsliste` and `Perioden`, rendering `Tune` after `headerActions`, and leaving `WorkoutClustersScreen`'s icon untinted.
4. *Preservation of Core Invariants*: Preserves `WorkoutSummary` overflow menu, clean `snapshotHeader`, `WorkoutDetailHeaderClickContractTest`, `WorkoutHeaderLandscapeTest` (`REQ-UI-433.2`), `WorkoutClusterSelectionDialog` binary compatibility, and `WorkoutRepository.kt` untouched (`5,117` LoC).

**Acceptance Criteria (Given-When-Then)**:
• *Given* `TrackOnMapScreen` opened from any of the 5 entry points (`WorkoutSummariesTabbedScreen`, `WorkoutSummariesListFragment`, `PeriodMapScreen`, `WorkoutClustersScreen`, `WorkoutClusterHeatmapScreen`), *When* `WorkoutHeader` renders in Light or Dark theme, *Then* all action icons (`Tune`, `⇄`, `Edit`, `Share`) use `MaterialTheme.colorScheme.primary` at `32.dp`, ordered `[Tune]` (tonal pill + `VerticalDivider`) before `[⇄] [Edit] [Share]`, with `actionSpacerWidth` preventing title overlap at `360–411 dp`.
• *Given* an indoor/non-GPS workout (`startLatLng == null`), *When* the athlete taps `⇄`, *Then* `WorkoutClusterSelectionDialog` lists available clusters with `onCreateNew` hidden (`null`) and selecting a cluster invokes `assignClusterToWorkout` cleanly without early return.
• *Given* all 9 locale `strings_workout_header.xml` files, *When* `TranslationParityTest` and `SourceTextReadingAuditContractTest` run, *Then* 100% 9-locale parity and `0` new `src/main` test reads pass.

**Invariants**: `WorkoutRepository.kt` untouched (`<= 5,117` LoC); `IF-SWE` / `ArchitectureConformanceContractTest` clean; `WorkoutSummary` & `snapshotHeader` free of `Tune`/`⇄`; `WarningAmber` retains `error` tint; `REQ-UI-433.2` landscape single-row layout preserved; 9-locale parity; `0` new `src/main` test reads.

### REQ-UI-2787: Persist Unchecked Workout Details Sections (v3 Schema), Decouple Summary Toggles, Reserve Custom Header Actions & Tab-Driven Settings [Verified]
- **Target Release**: `V5.0.0`
- **Tests**: `TST-UI-2787`
- **Priority**: High
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `WorkoutDetailTabConfig.kt`, `WorkoutDetailTabbedContent.kt`, `WorkoutHeader.kt`, `WorkoutMasksAndCardsSection.kt`, `AdvancedTuningAccordion.kt`, `AdvancedTuningScreen.kt`, `AdvancedTuningDialog.kt`
- **Localization Parity**: `tuning_summary_matrix_tabs_format` across all 9 locales (`values*/strings_workout_details.xml`)
- **Rationale**: In `WorkoutDetailTabConfig.deserializeTabs` (`ATT-5654`, `ATT-7320`, `ATT-7756`), `GRADE_DISTRIBUTION`, `RPE_AND_FEEL`, and `HRV` auto-migrations run unconditionally even on `"v2|"` payloads, reverting user removals; `WorkoutDetailTabbedContent` forwards summary-card `show*InWorkoutSummary` flags into full Workout Details (`ATT-4369`); `WorkoutHeader.actionSpacerWidth` omits custom `actions()` width; and `AdvancedTuningAccordion` / `WorkoutMasksAndCardsSection` read ignored `WorkoutDetailPreferences` booleans instead of `detailTabs` (`ATT-4043`, `REQ-UI-414`).
- **Description**: The system SHALL persist removed Workout Details sections/tabs, isolate summary toggles to summary list cards, dynamically reserve header action width, and drive settings subtitles/selectors from configured detail tabs:
  1. *`v3|` Schema Persistence & Pre-`v3` Legacy Migration (`REQ-UI-2787.1`)*: `WorkoutDetailTabConfig` SHALL bump `SCHEMA_PREFIX` from `"v2|"` to `"v3|"` and gate legacy auto-migrations (`GRADE_DISTRIBUTION`, `RPE_AND_FEEL`, `HRV`) strictly on `!raw.startsWith("v3|")` (alongside `isV1` for `PHOTOS`) so unchecked sections (`RPE_AND_FEEL`, `GRADE_DISTRIBUTION`, `PHOTOS`) and deleted `tab_hrv` remain absent after `serializeTabs` $\rightarrow$ `deserializeTabs`, while all `"v1|"` and `"v2|"` upgrade migrations remain intact (`WorkoutDetailTabsTest`).
  2. *Workout Details Decoupling from Summary Card Toggles (`REQ-UI-2787.2`)*: `WorkoutDetailTabbedContent` (`RenderWorkoutDetailCardSections`) SHALL pass `true` for `showCogganPowerInWorkoutSummary`, `showAerobicEfficiencyInWorkoutSummary`, and `showCriticalPowerInWorkoutSummary` into `WorkoutDetails(...)` (matching `CardioPowerDynamicsCard` decoupling), keeping Coggan NP/IF/TSS, Aerobic Efficiency/Decoupling, and Critical Power/W'bal visible in Workout Details whenever their `enable*` toggles are active, while `WorkoutSummary.kt` retains `show*InWorkoutSummary` gating for compact list cards.
  3. *Dynamic `WorkoutHeader` Custom `actions()` Width Reservation (`REQ-UI-2787.3`)*: `WorkoutHeader` SHALL preserve Frame-0 `actionSpacerWidth` (`ATT-8097`) AND dynamically measure `actionButtonsContent()` width via `Modifier.onSizeChanged` (`maxOf` reservation) so custom `actions()` content and scaled icons never collide with 2-line portrait titles.
  4. *Tab-Driven Settings Subtitle, Zone Selector Gating & `< 400` LoC Ceiling (`REQ-UI-2787.4`)*: `WorkoutMasksAndCardsSection` SHALL gate Details `ZoneDisplayModeSelector` on `detailTabs.any { tab -> tab.sections.any { it == WorkoutDetailSectionType.ZONES || it == WorkoutDetailSectionType.GRADE_DISTRIBUTION } }` rather than stale `workoutDetailPrefs.showZoneAnalysis`, while staying strictly `< 400` lines (`REQ-UI-414.5`, extracting a helper composable if needed). `TuningSubtitleFormatter.formatWorkoutMatrixSubtitle` SHALL retain its 3-arg `(cardPrefs, detailPrefs, context)` signature for `AdvancedTuningAccordionTest` / `AdvancedTuningAftermathContractTest` compatibility and support `detailTabs` (`AdvancedTuningScreen`, `AdvancedTuningDialog`) using 9-locale `tuning_summary_matrix_tabs_format`.

**Acceptance Criteria (Given-When-Then)**:
• *Given* default `v3` Workout Detail tabs, *When* the athlete unchecks `RPE_AND_FEEL` or `GRADE_DISTRIBUTION` or deletes `tab_hrv` in `WorkoutDetailTabsConfigSheet` and reopens Workout Details, *Then* the unchecked sections and deleted tab remain absent after `serializeTabs` $\rightarrow$ `deserializeTabs`, while `"v1|"` / `"v2|"` legacy migrations continue adding them once.
• *Given* `showCogganPowerInWorkoutSummary = false`, `showAerobicEfficiencyInWorkoutSummary = false`, and `showCriticalPowerInWorkoutSummary = false` with `enable* = true`, *When* viewing a workout with power/HR telemetry in Workout Details (`DETAILS`), *Then* Coggan, Aerobic Efficiency, and W'bal metrics remain visible in Workout Details while remaining hidden on compact `WorkoutSummary` list cards.
• *Given* a 2-line portrait title in `WorkoutHeader` with `onOpenTabsConfig` and/or custom `actions()` content, and modified `detailTabs` in Expert Settings, *When* `WorkoutHeader`, `WorkoutMasksAndCardsSection`, and `AdvancedTuningAccordion` render, *Then* the title never overlaps the action buttons, the accordion subtitle reflects active configured `detailTabs`, `ZoneDisplayModeSelector` tracks `ZONES`/`GRADE_DISTRIBUTION` tab presence, and `WorkoutMasksAndCardsSection.kt` stays `< 400` LoC.

**Invariants**: Legacy `"v1|"` / `"v2|"` migration tests pass; `WorkoutSummary` list card summary toggles remain functional; `WorkoutHeader` Frame-0 bounds (`ATT-8097`) preserved; `WorkoutMasksAndCardsSection.kt < 400` LoC; `formatWorkoutMatrixSubtitle` 3-arg contract preserved; `WorkoutRepository.kt` untouched; 9-locale parity; `0` new `src/main` reads in tests; Compose UI tests use `createComposeRule()`.

### REQ-UI-2788: Adequate Cockpit Sparkline Canvas Height, Compact X-Axis Leading & Flexible Value+Graph Layout (`ATT-8995`) [Verified]
- **Target Release**: `V5.0.0`
- **Tests**: `TST-UI-2788`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit, Integration
- **Implementation**: `SensorSparklineView.kt`, `SensorFieldView.kt`, `EditSensorFieldDialog.kt`
- **Localization Parity**: `N/A - no new or modified UI strings` (`0` edits to `res/values*/strings*.xml`)
- **Rationale**: After `ATT-6695` (`ef243477`) added the X-axis time label row (`-T`, `-T/2`, `Now`) below `Canvas(Modifier.weight(1f))`, `resolveSparklineHeight(..., isGraphOnly = false)` (`26/34/44/52.dp`) combined with inherited `16.sp` M3 `labelSmall` leading and `2 * 3.dp = 6.dp` Canvas vertical padding squeezed `NORMAL` Value+Graph curves into `~12.dp` net drawing height, while rigid `.height(...)` in `SensorFieldView.kt:505-507` wasted surplus tile height (`ATT-8995`).
- **Description**:
  1. *Sparkline Minimum Heights & Compact X-Axis Leading (`REQ-UI-2788.1`)*: `resolveSparklineHeight(viewSize, isGraphOnly)` in `SensorSparklineView.kt` SHALL return `46.dp` (`<= SMALL`), `52.dp` (`NORMAL`), `60.dp` (`LARGE`), and `68.dp` (`>= XLARGE`) when `isGraphOnly == false` (guaranteeing `>= 28.dp` net Canvas curve amplitude at `NORMAL` and `>= 34.dp` measured Canvas height), while keeping `isGraphOnly == true` strictly monotonically taller (`48.dp`, `56.dp`, `68.dp`, `84.dp`) so toggling off Value never shrinks the sparkline. The X-axis label `Text`s (`SensorSparklineView.kt:362-379`) SHALL apply `lineHeight = 12.sp` paired with `PlatformTextStyle(includeFontPadding = false)` to reclaim dead font leading without clipping `10.sp` labels, and the sparkline `Canvas` SHALL expose a stable `testTag("sensor_sparkline_canvas")` for exact measured-height verification.
  2. *Flexible Value+Graph Vertical Expansion & Fixed-Card Safety (`REQ-UI-2788.2`)*: In `SensorFieldView.kt:504-508`, Value+Graph mode (`showValue == true && showGraph == true`) SHALL apply `Modifier.heightIn(min = resolveSparklineHeight(fieldState.viewSize, isGraphOnly = false)).weight(1f).padding(horizontal = 4.dp)` instead of rigid `.height(...)`, expanding cleanly in `IntrinsicSize.Min` rows (`SensorGridScreen.kt:749,1122`), claiming surplus vertical space alongside taller neighbors in multi-column rows, and shrinking gracefully inside externally height-constrained cards (`116.dp`/`132.dp`) without clipping primary Value text or X-axis labels.
  3. *WYSIWYG Preview Parity & Invariants (`REQ-UI-2788.3`)*: `EditSensorFieldDialog.kt:526` SHALL size its sparkline preview container to `52.dp` (matching `ViewSize.NORMAL` Value+Graph). `0` edits to `strings*.xml`, `docs/tests/tst_tracking_cockpit.md`, or protected monoliths; `0` Compose snapshot-state mutations (`Rule 66`).
- **Requirement Archaeology & Chesterton's Fence Audit**:
  1. *Original Requirement ID & Target*: Refines `REQ-TRK-059.5`, `REQ-TRK-059.9`, `REQ-TRK-059.10`, and `REQ-UI-438` via net-new `REQ-UI-2788`.
  2. *Historical Origin & Commit Trace*: `ATT-1294` created `SensorSparklineView`; `ATT-6695` (`ef243477`) added `-T / -T/2 / Now` X-axis labels; `ATT-6689` (`82e21ca5`) added `heightIn(min = ...).weight(1f)` for Graph-only `IntrinsicSize.Min` collapse immunity.
  3. *Root Reason for Existing Formulation*: `ATT-6695` underestimated inherited `16.sp` M3 `labelSmall` leading + `6.dp` Canvas padding and kept rigid `.height(...)` for Value+Graph.
  4. *Preservation of Core Invariants*: Preserves `0px` collapse immunity in `IntrinsicSize.Min` rows (`REQ-TRK-059.10`), X-axis `-T / -T/2 / Now` legibility & 9-locale parity, Dynamic Zone gradient & DFA alpha 1 inverted polarity (`REQ-TRK-082`, `ATT-8436`), placeholder mode (`REQ-TRK-059.11`), and `SensorGridScreen` 50% map-split scroll cap (`REQ-UI-358`).
- **Acceptance Criteria (Given-When-Then)**:
  • *Given* a `ViewSize.NORMAL` `SensorFieldView` tile with `showValue = true` and `showGraph = true` inside a `Row(Modifier.height(IntrinsicSize.Min))`, *When* composed and measured via `createComposeRule()`, *Then* the `sensor_sparkline_canvas` node measures `>= 34.dp` (`>= 28.dp` net curve amplitude after `6.dp` padding) and all `-T`, `-T/2`, and `Now` X-axis labels remain fully visible without clipping.
  • *Given* a multi-column `Row(Modifier.height(IntrinsicSize.Min))` containing a `NORMAL` Value+Graph tile beside a taller `XLARGE` neighbor, *When* composed, *Then* the Value+Graph sparkline expands vertically beyond its `52.dp` minimum to claim surplus tile height.
  • *Given* externally height-constrained `132.dp` (`NORMAL`) and `116.dp` (`SMALL`) tiles (Light & Dark/AMOLED), *When* composed, *Then* primary Value text and X-axis labels remain unclipped and readable (`Rule 8.5` PNG verified).

---

### REQ-UI-2790: Type-Safe Sealed UI State Models for `ImportBackupTabsScreen` & `MapScreenWithTrack` (`ATT-6040`) [Verified]
- **Target Release**: `V5.0.0`
- **Tests**: `TST-UI-2790`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit, Integration
- **Implementation**: `ImportBackupTabsScreen.kt`, `MapScreenWithTrack.kt`, `ArchitectureConformanceContractTest.kt`
- **Localization Parity**: `N/A - no new or modified UI strings` (`0` edits to `res/values*/strings*.xml`)
- **Rationale**: `ImportBackupTabsScreen.kt` (`1,513` LoC) manages mutually exclusive restore, cloud-info, and pre-import tuning popups via `18` scattered `remember` booleans/nullables (`4` identical restore `AlertDialog`s, `3` cloud info `AlertDialog`s, `5` active + `1` dead `pendingSingleLegacyUri` `PreImportTuningBottomSheet` blocks), while `MapScreenWithTrack.kt` (`865` LoC) clears `5` mutually exclusive `rememberSaveable` IDs manually across `8` sites with a latent priority mismatch between `sheetPeekHeight` and `sheetContent`.
- **Description**:
  1. *Sealed Dialog & Pre-Import States in `ImportBackupTabsScreen.kt` (`REQ-UI-2790.1`)*:
     - `ImportBackupTabsScreen` SHALL model the `4` destructive full-restore confirmation `AlertDialog`s via a `sealed interface PendingRestoreSource` (`LocalFile(uri)`, `AutomaticLocalBackup`, `Dropbox`, `GoogleDrive`) rendered by one parameterized `AlertDialog`.
     - `ImportBackupTabsScreen` SHALL model the `3` cloud informational `AlertDialog`s via a `sealed interface CloudInfoDialog` (`DropboxDisconnected`, `GoogleDriveDisconnected`, `NoCloudConnected`) rendered by one parameterized `AlertDialog`, updating `isDropboxConnected` and `isGoogleDriveConnected` `remember` keys to observe that state (`REQ-MIG-036.13`).
     - `ImportBackupTabsScreen` SHALL model the `5` active `PreImportTuningBottomSheet` triggers via a `sealed interface PendingImportSource` (`DropboxBulk(format)`, `LegacyFiles(uris, format)`, `FitFiles(uris)`, `StravaArchive(uri)`, `Directory(uri, format)`), preserving `showStravaUploadOption = false` on `StravaArchive`, single-file `importLegacyFile` vs multi-file `importWorkoutFiles` dispatch on `LegacyFiles`, and pre-picker SAF launcher format holders (`pendingSingleLegacyFormat`, `pendingDirectoryFormat`) for async `rememberLauncherForActivityResult` callbacks (`REQ-UI-293`, `REQ-UI-362`).
     - Dead `pendingSingleLegacyUri` state and its unreachable `PreImportTuningBottomSheet` branch SHALL be removed.
     - Once `ImportBackupTabsScreen.kt` shrinks `<= 1,500` LoC, `ArchitectureConformanceContractTest.MAX_MONOLITH_FILE_LOC_BASELINE` MUST remove `ImportBackupTabsScreen.kt` (`REQ-PRO-129.2`).
  2. *Sealed `MapPeekSelection` with `Saver` & Unified Header Measurement in `MapScreenWithTrack.kt` (`REQ-UI-2790.2`)*:
     - `MapScreenWithTrack` SHALL replace the `5` mutually exclusive `rememberSaveable` selection IDs (`selectedSegmentId`, `selectedRouteId`, `selectedClusterId`, `selectedClimbId`, `selectedLocationId`) and `8` manual multi-null reset blocks with a single `sealed interface MapPeekSelection` (`Segment(id)`, `Route(id)`, `Cluster(id)`, `Location(id)`, `Climb(id)`) stored via `rememberSaveable(stateSaver = MapPeekSelection.Saver)`.
     - `MapPeekSelection.Saver` SHALL losslessly round-trip `null` and all `5` variants across configuration changes/process death while `LaunchedEffect(targetLocationId)` continues selecting `MapPeekSelection.Location(targetLocationId)` when `targetLocationId != null && targetLocationId > 0`.
     - `MapScreenWithTrack` SHALL distinguish sheet-backed selections (`Segment`, `Route`, `Cluster`, `Location`) from `Climb` so `Climb` keeps `BottomSheetScaffold` hidden (`0.dp` peek) and renders modal `ClimbDetailSheet`, while sheet-backed selections dispatch `sheetPeekHeight` and `sheetContent` from a single exhaustive `when (peekSelection)` without priority mismatch.
     - `MapScreenWithTrack` SHALL replace `3` separate `measured*HeaderHeight` states with a single `measuredHeaderHeight` keyed via `remember(peekSelection)` (`REQ-UI-221`) while preserving `editingLocation`/`previewRadius` coexistence with `Location`, location-peek expansion lock (`confirmValueChange` rejecting `SheetValue.Expanded` & `sheetSwipeEnabled = false`, `REQ-UI-238`), and `BackHandler`.
  3. *Preserved Signatures & Invariants (`REQ-UI-2790.3`)*:
     - Public signatures of `ImportBackupTabsScreen`, `PreImportTuningBottomSheet`, and `MapScreenWithTrack` MUST remain unchanged; `showMappingDialog` (`MappingRequired` flow) and `activeInteraction` (`ClusterNamingDialog`) remain intact; `0` `strings*.xml` edits and `0` visual/behavioral regressions.

**Acceptance Criteria (Given-When-Then)**:
• *Given* `ImportBackupTabsScreen`, *When* local/auto/Dropbox/GoogleDrive restore, disconnected/no-cloud notice, or FIT/TCX/GPX/folder/Dropbox/Strava pre-import tuning is triggered, *Then* exactly one `AlertDialog` or `PreImportTuningBottomSheet` renders from `PendingRestoreSource`/`CloudInfoDialog`/`PendingImportSource` with `showStravaUploadOption = false` for Strava, `pendingSingleLegacyUri` absent, `isDropboxConnected`/`isGoogleDriveConnected` keys refreshing, and `ImportBackupTabsScreen.kt <= 1,500` LoC.
• *Given* `MapScreenWithTrack` with `Segment`, `Route`, `Cluster`, `Location`, or `Climb` layers, *When* entities are tapped sequentially, `BackHandler`/`onMapClick` fires, or configuration changes occur, *Then* `MapPeekSelection.Saver` preserves the active variant, `sheetPeekHeight` and `sheetContent` always match, `Climb` shows modal `ClimbDetailSheet` without peek overlap, `Location` blocks `SheetValue.Expanded`, and `measuredHeaderHeight` resets cleanly per selection.
• *Given* behavioral Compose (`createComposeRule()`) and `MapPeekSelection.Saver` unit tests plus existing migration/map/build contract suites, *When* executed, *Then* 100% pass with `0` reflection-only UI stubs (`REQ-PRO-132`) and `0` `src/main` test reads (`REQ-PRO-049.1`).

**Invariants**: Public composable signatures preserved; `REQ-MIG-036.13`, `REQ-UI-221`, `REQ-UI-238`, `REQ-UI-293`, `REQ-UI-322`, `REQ-UI-362`, `REQ-MAP-059` contracts intact; `ImportBackupTabsScreen.kt` ratcheted out of `MAX_MONOLITH_FILE_LOC_BASELINE`; `0` `strings*.xml` edits; `0` Compose snapshot writes during composition (`Rule 66`).

---

### REQ-UI-2791: PMC Chart Box-to-Curve Visual Mapping, Zero-Baseline CTL Area Fill, Rounded Y-Axis Ticks & Unclipped Readiness Badges (`ATT-9357`) [Verified]
- **Target Release**: `V5.0.0`
- **Tests**: `TST-UI-2791`
- **System Requirement(s)**: Derived (SW-only — Compose UI presentation refinement of `REQ-UI-401.3` / `REQ-UI-495`)
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit, Integration
- **Implementation**: `PerformanceManagementChartCard.kt`, `AthleteProfileScreen.kt`, `AthleteMetricsPlotMath.kt`
- **Localization Parity**: `N/A - 0 new strings (reuses existing ATT-5678 trademark-free 9-locale athlete_profile_pmc_* strings)`
- **Rationale**: Physical Pixel 10 inspection (`ATT-9357`, `pixel10_screen.png`) of **Fitness & Physiologie -> Fitness & Ermüdung** revealed that `Form (Balance)`'s border used `PmcOptimalBlue` instead of its amber curve color `PmcTsbAmber` while Row 2 non-plotted KPIs (`ACWR`, `7T-Steigerung`) collided with `PmcAtlMagenta`/`PmcCtlBlue`, `Optimaler Aufbau` clipped to `Optimaler Auf...`, the blue CTL area fill shaded negative territory down to `-26`, Y-axis ticks rendered raw `30 / 2 / -26` with an adjacent duplicate zero line, and `ACWR` used `Locale.getDefault()` instead of `currentLocale`. Additionally, `AthleteProfileScreen.kt` sat at its exact `1,975 / 1,975` LoC monolith ceiling (`ArchitectureConformanceContractTest.kt:125`).
- **Description**:
  1. *Unambiguous Box-to-Curve Mapping, Curve Swatches & Legend (`REQ-UI-2791.1`)*:
     - Row 1 plotted KPI cards in `PerformanceManagementChartCard` (`Fitness (42d)` -> `TTColor.PmcCtlBlue`, `Ermüdung (7d)` -> `TTColor.PmcAtlMagenta`, `Form (Balance)` -> `TTColor.PmcTsbAmber`) SHALL anchor their card borders and visible curve swatch indicators strictly to their exact chart curve colors, while styling `Form (Balance)`'s semantic readiness zone text (`TsbReadinessZone`: `PmcFreshGreen`, `onSurfaceVariant`, `PmcOptimalBlue`, `error`) independently on its status line so readiness semantics MUST NEVER hijack the curve identity border or swatch.
     - Row 2 non-plotted derived KPIs (`ACWR`, `7T-Steigerung`) SHALL use neutral card borders (`MaterialTheme.colorScheme.outlineVariant`) to prevent color collision with `PmcCtlBlue` and `PmcAtlMagenta`, preserving semantic status coloring strictly on their status text/badge.
     - `PerformanceManagementChartCard` SHALL render a compact horizontal curve legend pairing each curve swatch (`PmcCtlBlue`, `PmcAtlMagenta`, `PmcTsbAmber`) with the existing `ATT-5678` trademark-free 9-locale labels (`athlete_profile_pmc_ctl_label`, `athlete_profile_pmc_atl_label`, `athlete_profile_pmc_tsb_label`).
  2. *Unclipped Multi-Line Readiness Badge & Uniform Row Heights (`REQ-UI-2791.2`)*:
     - `PmcStatBadge` SHALL allow its readiness/status `badgeText` to wrap cleanly up to `2` lines (`maxLines = 2`, `softWrap = true`, `overflow = TextOverflow.Ellipsis`) so multi-word localized readiness labels (specifically DE `Optimaler Aufbau`, ES `Sobrecarga óptima`, FR `Surcharge optimale`, IT `Sovraccarico ottimale`) render without ellipsis clipping on 3-column mobile viewports (`360–412 dp`), while maintaining uniform aligned card heights via `Modifier.height(IntrinsicSize.Min)` and `Modifier.fillMaxHeight()` (`REQ-UI-495`).
  3. *CTL Area Fill Clamped to Zero Baseline `zeroY` (`REQ-UI-2791.3`)*:
     - `PerformanceManagementChartCanvas` SHALL compute the clamped zero baseline `zeroY = (topPad + plotH * (1f - ((0.0 - yMin) / yRange).toFloat())).coerceIn(topPad, topPad + plotH)` (`topPad + plotH` only when `yMin >= 0`) and SHALL anchor both the CTL `fillPath` closing edge and its vertical gradient `endY = zeroY`, producing zero pale-blue CTL shading below `0` whenever `CTL = 0` and `yMin < 0`.
  4. *Clean Rounded Y-Axis Bounds & Explicit `0` Tick (`REQ-UI-2791.4`)*:
     - `AthleteMetricsPlotMath` SHALL provide a pure deterministic helper (`computePmcYAxisScale`) returning rounded Y-axis bounds and clean human-readable tick increments (`5, 10, 15, 20, 25, 50, ...` scaled by powers of 10) that explicitly include `0` whenever data spans or touches zero, rendering `0` once as an emphasized labeled gridline and eliminating arbitrary ticks (`30 / 2 / -26`) and adjacent duplicate zero lines.
  5. *`currentLocale` Decimal Formatting Parity for `ACWR` (`REQ-UI-2791.5`)*:
     - `PerformanceManagementChartCard` SHALL format `pmcSummary.currentAcwr` using `currentLocale` (`LocalConfiguration.current.locales[0]`, `"%.2f"`) matching CTL/ATL/TSB formatting (`0` `Locale.US` or `Locale.getDefault()` drift in `PerformanceManagementChartCard`).
  6. *Modular Extraction & Monolith Ceiling Compliance (`REQ-UI-2791.6`)*:
     - `PerformanceManagementChartCard`, `PmcStatBadge`, and `PerformanceManagementChartCanvas` SHALL be extracted into sibling `PerformanceManagementChartCard.kt` (`<= 1,500` LoC), keeping `AthleteProfileScreen.kt` cleanly within `(1500, 1975]` LoC (`ArchitectureConformanceContractTest.kt:125, :517`) with Banister/Coggan `TSB_t = CTL_{t-1} - ATL_{t-1}` (`PowerPhysiologyCalculator.kt`) and `ATT-9360` (`ACWR` `CTL >= 15` gate / Tier-4 `sRPE`) untouched and `0` new string keys.
- **Acceptance Criteria (Given-When-Then)**:
  • *Given* **Fitness & Physiologie -> Fitness & Ermüdung** with populated PMC data (including `TsbReadinessZone.OPTIMAL_OVERLOAD`), *When* `PerformanceManagementChartCard` renders, *Then* Row 1 cards (`Fitness (42d)`, `Ermüdung (7d)`, `Form (Balance)`) display borders and curve swatches matching `PmcCtlBlue`, `PmcAtlMagenta`, and `PmcTsbAmber` alongside a compact curve legend, while `ACWR` and `7T-Steigerung` use neutral borders with semantic status text only.
  • *Given* a German (`de-DE`) mobile viewport (`360 dp`) with `Form (Balance)` in `OPTIMAL_OVERLOAD` (`Optimaler Aufbau`), *When* rendered, *Then* `Optimaler Aufbau` displays completely without ellipsis truncation (`hasVisualOverflow == false`), `ACWR` formats with a comma decimal separator via `currentLocale`, and all Row 1 cards maintain equal aligned heights.
  • *Given* a PMC series containing `CTL = 0` days alongside negative `TSB` values (e.g. `-21` to `+25`), *When* `PerformanceManagementChartCanvas` draws the chart, *Then* Y-axis ticks use clean rounded multiples explicitly including a single labeled `0` line (no duplicate zero line) and the CTL blue area fill closes at `zeroY` leaving negative territory unshaded.
  • *Given* Light and Dark/AMOLED rendered Compose screenshots (`Rule 8.5`), *When* inspected via `read_file`, *Then* all curves, swatches, legend, Y-axis ticks, and localized badges render cleanly with zero clipping, overlap, or theme contrast regressions.
- **Requirement Archaeology & Chesterton's Fence Audit**:
  1. *Original Requirement ID & Target*: Refines `REQ-UI-401` (`REQ-UI-401.3`/`.4`) and `REQ-UI-495` (`TST-UI-364`, `TST-UI-495`) targeting `AthleteProfileScreen.kt`, `PerformanceManagementChartCard.kt`, and `AthleteMetricsPlotMath.kt`.
  2. *Historical Origin & Commit Trace*: `ATT-3859` introduced PMC UI; `ATT-5678` replaced proprietary Peaksware trademarks across all 9 locales; `ATT-4927` (`REQ-UI-495`) added `IntrinsicSize.Min` uniform heights & dedicated `selectedPmcTimeRange`.
  3. *Root Reason for Existing Formulation*: Original `PmcStatBadge` shared one `accentColor` for both border and status text and anchored CTL fill to `topPad + plotH`, assuming positive series without sparse zero-CTL / negative-TSB onset periods.
  4. *Preservation of Core Invariants*: Preserves Banister/Coggan `TSB_t = CTL_{t-1} - ATL_{t-1}` (`PowerPhysiologyCalculator.kt` untouched), `ATT-9360` out-of-scope isolation, `ATT-5678` trademark-free 9-locale strings (`0` new keys), `IntrinsicSize.Min` + `fillMaxHeight()` uniform heights, `CompactSelectorPill` time-range selector, `AthleteProfileScreen.kt` within `(1500, 1975]` LoC, and `HrvRampTestScreenTest` `AthleteProfileScreenKt` symbol.

---

### REQ-UI-2792: PMC ACWR Low-CTL Building-Base Card Presentation & 9-Locale Trademark-Free Parity (`ATT-9360`) [Verified]
- **Target Release**: `V5.0.0`
- **Tests**: `TST-UI-2792`
- **System Requirement(s)**: Derived (SW-only — Compose UI & 9-locale string refinement of `REQ-UI-401.1`/`.3`/`.4` & `REQ-UI-2791`)
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit, Integration
- **Implementation**: `PerformanceManagementChartCard.kt`, `values*/strings.xml`
- **Localization Parity**: `9/9 locales (EN, DE, ES, FR, IT, JA, NL, PL, PT) — athlete_profile_pmc_acwr_building_base`
- **Rationale**: When `PowerPhysiologyCalculator` gates `ACWR` below `MIN_CTL_FOR_ACWR = 15.0` (`REQ-WKO-039.1`), athletes with active workout history must see a calm, informative neutral hint (*Building base* / *Aufbauphase*) alongside `'—'` rather than a blank sub-label or false red *Risikobereich* alarm (`ATT-9360`).
- **Description**:
  1. *Neutral Building-Base ACWR Presentation (`REQ-UI-2792.1`)*: In `PerformanceManagementChartCard.kt`, when `pmcSummary.currentAcwr == null && (pmcSummary.dailyPoints.isNotEmpty() || pmcPlotPoints.isNotEmpty())`, the Row 2 ACWR `PmcStatBadge` SHALL remain visible, display `"—"` as `valueText`, display `stringResource(R.string.athlete_profile_pmc_acwr_building_base)` as `badgeText`, and style neutrally with `MaterialTheme.colorScheme.onSurfaceVariant` text and neutral `MaterialTheme.colorScheme.outlineVariant` border (MUST NEVER display red `error` or *Risikobereich* styling). When both `pmcSummary.dailyPoints` and `pmcPlotPoints` are empty, `valueText` SHALL be `"—"` and `badgeText` SHALL be `null`. When `pmcSummary.currentAcwr != null` (`CTL >= 15.0`), `currentLocale` `"%.2f"` formatting and semantic `AcwrZone` text coloring (`REQ-UI-2791.1`/`.5`) SHALL remain intact.
  2. *9-Locale Trademark-Free String Parity (`REQ-UI-2792.2`)*: `athlete_profile_pmc_acwr_building_base` SHALL be defined and non-blank across all 9 supported locales in `app/src/main/res/values*/strings.xml` (`EN`: `Building base`, `DE`: `Aufbauphase`, `ES`: `Creando base`, `FR`: `Construction de base`, `IT`: `Costruzione base`, `JA`: `ベース構築中`, `NL`: `Basis opbouwen`, `PL`: `Budowanie bazy`, `PT`: `Construção de base`), with 100% parity verified by `TranslationParityTest` and `AthleteProfileContractAndLocalizationTest` (18 `athlete_profile_pmc_*` keys).
- **Acceptance Criteria (Given-When-Then)**:
  • *Given* `pmcSummary.currentAcwr == null` with populated workout history (`pmcSummary.dailyPoints.isNotEmpty() || pmcPlotPoints.isNotEmpty()`, `CTL < 15.0`), *When* `PerformanceManagementChartCard` renders, *Then* the ACWR card displays `"—"` and localized `athlete_profile_pmc_acwr_building_base` (DE: `Aufbauphase`, EN: `Building base`) in neutral styling without red warning text or truncation.
  • *Given* completely empty PMC history (`dailyPoints.isEmpty() && pmcPlotPoints.isEmpty()`), *When* `PerformanceManagementChartCard` renders, *Then* the ACWR card displays `"—"` with no building-base hint (`badgeText == null`).
  • *Given* all 9 supported locale `strings.xml` files, *When* `TranslationParityTest` and `AthleteProfileContractAndLocalizationTest` run, *Then* `athlete_profile_pmc_acwr_building_base` passes with 100% 9-locale parity (`EN, DE, ES, FR, IT, JA, NL, PL, PT`).
- **Requirement Archaeology & Chesterton's Fence Audit**:
  1. *Original Requirement ID & Target*: Refines `REQ-UI-401` (`REQ-UI-401.1`/`.3`/`.4`) and `REQ-UI-2791` targeting `PerformanceManagementChartCard.kt` and `values*/strings.xml`.
  2. *Historical Origin & Commit Trace*: `ATT-3859` (`dc62bb3c`) introduced PMC ACWR card & 17 `athlete_profile_pmc_*` strings; `ATT-5678` made PMC labels trademark-free; `ATT-9357` extracted `PerformanceManagementChartCard.kt` with neutral Row 2 borders and `currentLocale` formatting.
  3. *Root Reason for Existing Formulation*: Previously `currentAcwrZone == null` unconditionally mapped to `acwrZoneText = null` because `acwr` was only `null` when `ctl <= 0.01` (empty history).
  4. *Preservation of Core Invariants*: Preserves `ATT-9357` Row 1 curve swatches/legend, Row 2 neutral `outlineVariant` borders, `IntrinsicSize.Min` uniform row heights, `currentLocale` formatting, `AthleteProfileScreen.kt` (`1,596/1,975` LoC untouched), and `ATT-5678` trademark-free 9-locale strings.

---

## Change History

| Version | Date | Author | Ticket | Summary of Changes |
| :--- | :--- | :--- | :--- | :--- |
| `1.0.0` | 2026-11-18 | AI Agent (`ATT-8206`) | `ATT-8206` | Split oversized shard into compliant sub-shard (<= 4,000 lines, `REQ-PRO-093.4`, `REQ-PRO-127.3`). |
| `1.0.2` | 2026-11-18 | AI Agent (`ATT-8712`) | `ATT-8712` | Aligned `REQ-UI-2783.3` with `REQ-TRK-046.4` and `REQ-TRK-087` cleanly post-split (`ATT-8206`). |
| `1.0.3` | 2026-11-18 | AI Agent (`ATT-8749`) | `ATT-8749` | Added `REQ-UI-2785` (`[Planned]`) for enlarged `175x120 dp` Lieblingsorte preview map, `120.0 dp` zoom calibration, and `360dp` overflow protection. |
| `1.0.4` | 2026-11-18 | AI Agent (`ATT-8749`) | `ATT-8749` | Promoted `REQ-UI-2785` to `[Verified]` after Stage 4/5 verification (`89/89` tests, `Rule 8.5` PNG audit). |
| `1.0.6` | 2026-11-18 | AI Agent (`ATT-8748`) | `ATT-8748` | Refined `REQ-UI-493` to `[Verified]` (`175.dp x 120.dp` thumbnail, `12.dp` spacing, `resolveClusterThumbnailWidthDp`, `THUMBNAIL_BOUNDS_PADDING_PX = 18`, `151/151` PASS, `Rule 8.5` PNG). |
| `1.0.7` | 2026-11-18 | AI Agent (`ATT-8097`) | `ATT-8097` | Added `REQ-UI-2786` (`[Planned]`) unifying Workout Detail header action icons, global-vs-unit grouping, tooltips, universal `⇄` visibility, and non-GPS fallback. |
| `1.0.8` | 2026-11-18 | AI Agent (`ATT-8097`) | `ATT-8097` | Promoted `REQ-UI-2786` to `[Implemented]` after Stage 4 implementation (`90/90` targeted tests PASS, `Rule 8.5` PNG inspected). |
| `1.0.9` | 2026-11-18 | AI Agent (`ATT-8097`) | `ATT-8097` | Promoted `REQ-UI-2786` to `[Verified]` after Stage 5 verification (`90/90` XML tests PASS, governance `exit 0`, `Rule 8.5` PNG). |
| `1.0.10` | 2026-11-18 | AI Agent (`ATT-8079`) | `ATT-8079` | Added `REQ-UI-2787` (`[Planned]`) for `v3|` tab persistence, Workout Details summary-toggle decoupling, dynamic header action reservation, and tab-driven settings/subtitle. |
| `1.0.11` | 2026-11-18 | AI Agent (`ATT-8079`) | `ATT-8079` | Promoted `REQ-UI-2787` to `[Implemented]` in Stage 4 (`WorkoutDetailTabConfig`, `WorkoutDetailTabbedContent`, `WorkoutHeader`, `WorkoutMasksAndCardsSection`, `AdvancedTuning*`, 9-locale `strings_workout_details.xml`). |
| `1.0.12` | 2026-11-18 | AI Agent (`ATT-8079`) | `ATT-8079` | Promoted `REQ-UI-2787` to `[Verified]` in Stage 5 (`94/94` Tier 1 JUnit XMLs PASS, `Rule 8.5` PNG inspected). |
| `1.0.13` | 2026-11-20 | Lane A Author (`ATT-8995`, `--as agent1`) | `ATT-8995` | Added `REQ-UI-2788` (`[Planned]`, `.1`–`.3`) for adequate Value+Graph sparkline Canvas height (`46/52/60/68.dp`), monotonic Graph-only heights (`48/56/68/84.dp`), compact `12.sp` X-axis leading (`includeFontPadding = false`), flexible `heightIn(min).weight(1f)` in `SensorFieldView`, and `52.dp` preview parity (`N/A - no new or modified UI strings`). |
| `1.0.14` | 2026-11-20 | Lane A Author (`ATT-8995`, `--as agent1`) | `ATT-8995` | Promoted `REQ-UI-2788` to `[Implemented]` (`SensorSparklineView.kt`, `SensorFieldView.kt`, `EditSensorFieldDialog.kt`, `SensorFieldViewSparklineLayoutComposeTest.kt`, Light/Dark `Rule 8.5` PNG `ATT-8995_sparkline_value_graph_layout.png`; `0` `strings*.xml` or monolith edits). |
| `1.0.15` | 2026-11-20 | Lane A Author (`ATT-8995`, `--as agent1`) | `ATT-8995` | Promoted `REQ-UI-2788` to `[Verified]` in Stage 5 reusing Gate 4 (`ATT-9382` @ `a5cbbd1b`) `57/57 PASS` across 11 suites + `:app:assembleDebug` + `0` new Kotlin/deprecation warnings (`Rule 7.5`, `0` `app/` diff since `a5cbbd1b`), `Rule 8.5` Light/Dark PNG certified, `4/4` safety linters PASS, `--check-verified-evidence` `exit 0`, and `docs/engineering/walkthroughs/ATT-8995_walkthrough.md`. |
| `1.0.16` | 2026-11-21 | Lane 2 Author (`ATT-6040`, `--as agent1`) | `ATT-6040` | Added `REQ-UI-2790` `[Planned]` for type-safe sealed UI state models (`PendingRestoreSource`, `CloudInfoDialog`, `PendingImportSource`, `MapPeekSelection` + `Saver`, dead `pendingSingleLegacyUri` removal, `<= 1,500` LoC ratchet). |
| `1.0.17` | 2026-11-21 | Lane 2 Author (`ATT-6040`, `--as agent1`) | `ATT-6040` | Promoted `REQ-UI-2790` to `[Implemented]` (`ImportBackupTabsScreen.kt` `1,400` LoC, `MapScreenWithTrack.kt` `886` LoC, `ArchitectureConformanceContractTest.kt` baseline ratcheted to 15, `ImportBackupTabsSealedStateComposeTest.kt`, `MapPeekSelectionSaverAndUiComposeTest.kt`). |
| `1.0.18` | 2026-11-21 | Lane 2 Author (`ATT-6040`, `--as agent1`) | `ATT-6040` | Promoted `REQ-UI-2790` to `[Verified]` in Stage 5 (`Rule 7.5` Doc-Only Fast Path reusing Gate 4 `ATT-9485` @ `cd809943`: `886/886 PASS` across 172 suites, `0` new warnings, `--check-verified-evidence` `exit 0`). |
| `1.0.19` | 2026-11-21 | Single-Lane Author (`ATT-9357`, `--as agent1`) | `ATT-9357` | Added `REQ-UI-2791` (`[Planned]`, `.1`–`.6`, refining `REQ-UI-401.3`/`REQ-UI-495`) for PMC box-to-curve swatches/legend & neutral Row 2 borders, 2-line unclipped readiness badges, `zeroY`-clamped CTL area fill, pure rounded Y-axis ticks with explicit `0`, `currentLocale` `ACWR` formatting, and modular `PerformanceManagementChartCard.kt` extraction (`0` new strings). |
| `1.0.20` | 2026-11-21 | Single-Lane Author (`ATT-9357`, `--as agent1`) | `ATT-9357` | Promoted `REQ-UI-2791` to `[Implemented]` (`PerformanceManagementChartCard.kt` `613` LoC, `AthleteProfileScreen.kt` `1,596/1,975` LoC, `AthleteMetricsPlotMath.kt` `365` LoC; `55/55 PASS` across 7 Tier-1 suites + `3/3` Python safety linters, `0` new warnings, `Rule 8.5` Light/Dark PNG inspected). |
| `1.0.21` | 2026-11-21 | Single-Lane Author (`ATT-9357`, `--as agent1`) | `ATT-9357` | Promoted `REQ-UI-2791` to `[Verified]` in Stage 5 (`Rule 7.5` Doc-Only Fast Path reusing Gate 4 `ATT-9506` @ `04271fff`: `55/55` Stage 4 + `35/35` Gate 4 PASS, `0` `app/` drift, `0` new warnings, `3/3` safety linters PASS, `Rule 8.5` Light/Dark PNG certified, `--check-verified-evidence` `exit 0`). |
| `1.0.22` | 2026-11-21 | Single-Lane Author (`ATT-9357`, `--as agent1`) | `ATT-9357` | Re-promoted `REQ-UI-2791` to `[Verified]` in Stage 5 Bounce 1 (`Rule 7.5` Doc-Only Fast Path reusing Gate 4 Bounce 1 `ATT-9512` @ `4fe0f4af`: narrow-range `yMin == ticks.first()` / `yMax == ticks.last()` sync, `currentLocale` X-axis `formatShortDate`, TalkBack decorative swatch cleanup, `36/36 PASS`, `0` `app/` drift, `Rule 8.5` PNG re-certified, `--check-verified-evidence` `exit 0`). |
| `1.0.23` | 2026-11-21 | Single-Lane Author (`ATT-9360`, `--as agent1`) | `ATT-9360` | Added `REQ-UI-2792` (`[Planned]`, `.1`–`.2`, refining `REQ-UI-401.1`/`.3`/`.4` & `REQ-UI-2791`) for neutral ACWR building-base hint (`—` + `athlete_profile_pmc_acwr_building_base` when `currentAcwr == null` with non-empty `dailyPoints`/`pmcPlotPoints`, `badgeText = null` when both empty) and 9-locale trademark-free parity (`EN, DE, ES, FR, IT, JA, NL, PL, PT`). |
| `1.0.24` | 2026-11-21 | Single-Lane Author (`ATT-9360`, `--as agent1`) | `ATT-9360` | Promoted `REQ-UI-2792` to `[Implemented]` (`PerformanceManagementChartCard.kt` neutral building-base badge + 9-locale `values*/strings.xml` parity). |
| `1.0.25` | 2026-11-21 | Single-Lane Author (`ATT-9360`, `--as agent1`) | `ATT-9360` | Promoted `REQ-UI-2792` to `[Verified]` in Stage 5 (`Rule 7.5` Doc-Only Fast Path reusing Gate 4 `ATT-9534` @ `51ce6830`: `67/67 PASS` incl. `TranslationParityTest` `11/11`, `0` `app/` drift, `Rule 8.5` Light/Dark PNG certified, `--check-verified-evidence` `exit 0`). |
