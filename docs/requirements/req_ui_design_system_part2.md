# Living Requirements: UI Components & Design System (Part 2 of 5)

**Document ID**: `DOC-REQ-UI-P2`  
**Version**: 1.0.0  
**Status**: Reviewed  
**Approver**: Pending PO Sign-Off  

**Domain**: `ui_design_system_part2`  
**Prefixes**: `UI`, `UNT`  
**Scope**: Jetpack Compose design system, UI components, typography, dialogs, gestures, and layout contracts.  
**Total Requirements**: 95

---

### REQ-UI-205: Aftermath: Enhanced Shareable Workout Snapshot with Zone Analytics Architecture [Verified]
- **Tests**: `TST-UI-159`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `MapDetailLayout.kt`, `ShareUtils.kt`, `WorkoutSnapshotLayoutCalculator.kt`
- **Rationale**: The system SHALL enhance the post-workout image sharing pipeline to capture and integrate the visual analytics slot (`analyticsContent` in `MapDetailLayout.kt`) into the generated workout snapshot image (ATT-1393):
1. *Compose GraphicsLayer Recording (`MapDetailLayout.kt`)*:
• The system SHALL define `val analyticsLayer = rememberGraphicsLayer()` within `MapDetailLayout`.
• The `analyticsContent` slotted container SHALL be wrapped with `Modifier.drawWithContent` recording content into `analyticsLayer`.
• In `onSnapshotReady`, if `analyticsLayer.size.width > 0 && analyticsLayer.size.height > 0`, the system SHALL extract an Android Bitmap on `Dispatchers.Default`; otherwise passing `null`.
2. *Snapshot Assembly & Canvas Pipeline (`ShareUtils.kt`)*:
• `combineWorkoutAndShare` SHALL accept an optional `analytics: Bitmap? = null` parameter, preserving 100% backward compatibility for 4-argument callers (`RouteOnMapScreen.kt`, `SegmentOnMapScreen.kt`).
• If `analytics` is present, the system SHALL convert it to a software bitmap (`ensureSoftwareBitmap`).
• The total canvas height SHALL include the analytics bitmap height: $\text{totalHeight} = H_{\text{header}} + H_{\text{map}} + H_{\text{elevation}} + H_{\text{analytics}} + H_{\text{footer}}$.
• If the analytics bitmap width differs from `totalWidth` ($W_{\text{map}}$), it SHALL be scaled proportionally to match `totalWidth`.
• The analytics bitmap SHALL be drawn at vertical offset $Y = H_{\text{header}} + H_{\text{map}} + H_{\text{elevation}}$ immediately preceding the branding footer.
3. *Pure Mathematical Layout Engine (`WorkoutSnapshotLayoutCalculator.kt`)*:
• The system SHALL provide pure functions for snapshot layout computation: `calculateTotalHeight`, `calculateScaleFactor`, and `calculateSectionOffsets`.
4. *Graceful Fallback & Invariant Protection*:
• For sessions without zone analytics or interval splits (`analytics == null` or 0 height), the snapshot SHALL collapse cleanly without blank space, identical to prior layout behavior.
• All bitmap conversions, matrix scaling, and compression SHALL execute off the main thread on `Dispatchers.Default` and `Dispatchers.IO`.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement only (REQ-UI-205), completing Epic `ATT-111` (*Compact Post-Workout Visual Analytics & Graphs*).
2. *Historical Origin & Commit Trace*: Ticket `ATT-1393`, Sprint `2026-40.5`.
3. *Root Reason for Existing Formulation*: `combineWorkoutAndShare` originally stitched only Header, Map, and Elevation profile (`ATT-1472`), because metabolic zone bars and split charts were introduced later in Epic ATT-111.
4. *Preservation of Core Invariants*: Existing 4-argument callers remain 100% unbroken; branding footer, application logo, and FileProvider system share intent remain unchanged; zero memory leaks or main-thread rendering freezes.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an athlete completes a workout with recorded Heart Rate zones, Power zones, and lap splits displayed in `TrackOnMapScreen`,
• *When* the athlete taps the Share FAB button in `MapDetailLayout`,
• *Then* the generated snapshot image SHALL contain the Header, Map course, Elevation profile, and Analytics cards sequentially stacked, terminating with the application branding footer.
• *Given* an activity with no heart rate, power, or multiple laps (`analyticsContent` is empty or null),
• *When* the athlete shares the workout,
• *Then* the generated snapshot SHALL cleanly omit the analytics section with zero vertical blank space.
• *Given* an athlete sharing a planned route (`RouteOnMapScreen`) or segment (`SegmentOnMapScreen`),
• *When* invoking `combineWorkoutAndShare`,
• *Then* the call SHALL succeed with default `analytics = null` and generate the existing standard route/segment summary image.

**Invariants**: Existing 4-argument caller contracts, background thread offloading, and FileProvider security constraints MUST NOT be broken.

### REQ-UI-206: Aftermath: Continuous Telemetry Metric Graphs (Heart Rate, Speed/Pace, Power) with Section Headings & Synchronized Multi-Chart Scrubbing Architecture [Verified]
- **Tests**: `TST-UI-160`, `TST-UI-167`, `TST-UI-168`, `TST-UI-169`, `TST-UI-173`, `TST-UI-174`, `TST-UI-180`, `TST-UI-184`, `TST-UI-396`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `TelemetryMetricGraph.kt`, `ElevationProfile.kt`, `MapDetailLayout.kt`, `TrackOnMapScreen.kt`, `strings.xml` (all 9 locales)
- **Rationale**: The system SHALL provide dedicated continuous time-series and distance-series metric graphs with distinct section headings and synchronized scrubbing within the Aftermath inspection layout (`MapDetailLayout.kt` / `TrackOnMapScreen.kt`) (ATT-1740):
1. *Continuous Telemetry Graph Composable (`TelemetryMetricGraph.kt`)*:
• The system SHALL provide a lightweight, high-performance continuous line chart composable (`TelemetryMetricGraph.kt`) to render scalar telemetry curves matching the active horizontal axis domain (`ProfileXAxisDomain.DISTANCE` or `ProfileXAxisDomain.TIME`).
• Horizontal layout geometry SHALL match `ElevationProfile` padding invariants: `start = 50.dp, end = 25.dp, bottom = 24.dp`, guaranteeing pixel-perfect vertical alignment of the plot areas and X-axis ticks across all stacked charts.
• Y-axis minimum and maximum value labels SHALL be rendered on the left baseline-aligned to the chart area bounds.
• Adaptive X-axis ticks (distance milestones or formatted elapsed timestamps) SHALL be rendered along the bottom axis adapting via `ElevationProfileZoomMath`.
• When scrubbing (`currentDistance != null`), the component SHALL render a vertical dashed cursor line at `canvasX` and a circular highlight marker dot on the curve at `(canvasX, canvasY)`.
• Touch and horizontal drag gestures on the chart surface SHALL translate touch coordinates to distance/time and invoke `onDistanceSelected(distance)`.
2. *Dedicated Metric Visualizations & Conditional Rendering*:
• **Heart Rate Graph**: Rendered if and only if `path.any { it.hr != null && it.hr > 0 }`. Y-axis bounded to minimum and maximum HR in `bpm`. Highlight accent line and subtle area fill using Red / Coral (`TTColor.Zone4`).
• **Speed / Pace Graph**: Rendered if and only if `path.any { it.speedMps != null && it.speedMps > 0.0 }`. For running sports (`bSportType == BSportType.RUN`), displayed as Pace in `min/km` or `min/mi`; for other sports displayed as Speed in `km/h` or `mph`. Highlight accent line and subtle area fill using Primary Blue (`MaterialTheme.colorScheme.primary`).
• **Cycling Power Graph**: Rendered if and only if `path.any { it.power != null && it.power > 0 }`. Y-axis bounded to 0..max Power in `Watts`. Highlight accent line and subtle area fill using Purple (`TTColor.Zone5`).
• Sessions lacking a specific sensor metric SHALL omit the corresponding graph cleanly with zero vertical whitespace consumption.
3. *Distinct Localized Section Headings*:
• In detailed inspection views where `showZoomControls == true`, each displayed graph SHALL feature a distinct section header title:
- Elevation: `R.string.graph_heading_elevation` ("Elevation Profile" / "Höhenprofil")
- Heart Rate: `R.string.graph_heading_heart_rate` ("Heart Rate" / "Herzfrequenz")
- Speed: `R.string.graph_heading_speed` ("Speed" / "Geschwindigkeit")
- Pace: `R.string.graph_heading_pace` ("Pace" / "Tempo")
- Power: `R.string.graph_heading_power` ("Power" / "Leistung")
4. *Synchronized Multi-Chart Scrubbing Architecture*:
• In `MapDetailLayout.kt`, all displayed continuous graphs (`ElevationProfile`, Heart Rate, Speed/Pace, Power) and the interactive map SHALL share `selectedDistance: Double?`.
• Scrubbing coordinates on any graph or the map SHALL synchronously update `selectedDistance`, positioning the vertical cursor line and instantaneous value across all visible graphs simultaneously.
5. *100% 9-Language Localization Parity*:
• String resources `graph_heading_elevation`, `graph_heading_heart_rate`, `graph_heading_speed`, `graph_heading_pace`, and `graph_heading_power` SHALL be defined across all 9 supported locales (EN, DE, ES, FR, IT, JA, NL, PL, PT) with zero missing entries.
6. *Preservation of Core Invariants*:
• List previews (`WorkoutSummary.kt`, `RouteItem.kt`, `SegmentItem.kt`) continue calling `ElevationProfile` with `showZoomControls = false` without instantiating telemetry graphs.
• Ambient tracking popups (`LIveSegmentSheet.kt`) retain compact elevation profile without extra telemetry graphs.
• Snapshot sharing (`combineWorkoutAndShare`) records all graphs within `elevationLayer` seamlessly.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement only (REQ-UI-206), extending Epic `ATT-111` (*Compact Post-Workout Visual Analytics & Graphs*), `REQ-UI-197`, and `REQ-UI-201`.
2. *Historical Origin & Commit Trace*: Ticket `ATT-1391` (Sprint `2026-40.5`) introduced multi-metric scrubbing on `ElevationProfile`. User sprint review requested full continuous curves with headings for Heart Rate, Speed, and Power (ATT-1740).
3. *Root Reason for Existing Formulation*: Previously, only the elevation profile existed as a continuous curve, while sensor telemetry was confined to instantaneous badge text.
4. *Preservation of Core Invariants*: Elevation zoom math, single-thread SQLite query confinement, 9-language localization parity, and list preview performance remain 100% intact.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an athlete viewing a completed workout in Aftermath (`TrackOnMapScreen`),
• *When* the workout contains recorded Heart Rate, Speed, and Power data,
• *Then* the screen SHALL display distinct continuous graphs for Elevation Profile, Heart Rate, Speed/Pace, and Power, each headed by its localized section title.
• *Given* a workout recorded with GPS and Heart Rate but without a Power meter (`power == null`),
• *When* viewed in Aftermath,
• *Then* Elevation, Heart Rate, and Speed/Pace graphs SHALL render, while the Power graph SHALL be completely omitted.
• *Given* an athlete scrubbing horizontally on any graph or the map,
• *When* the touch position updates,
• *Then* the vertical cursor line and scrubbed metric values SHALL update synchronously across all displayed graphs.
• *Given* list item previews (`WorkoutSummary`) or ambient cockpit popups (`LiveSegmentSheet`),
• *When* rendered,
• *Then* additional telemetry graphs SHALL NOT be rendered, preserving compact performance.

**Invariants**: SQLite single-thread confinement, zero regression in ElevationProfile zoom math, and 9-language localization parity MUST NOT be broken.

### REQ-UI-207: Lieblingsorte: Subtle Low-Contrast Ghost Badges for Starts and Strecken on KnownLocationCard [Verified]
- **Tests**: `TST-UI-161`, `TST-UI-171`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `KnownLocationsScreen.kt`
- **Rationale**: The system SHALL refine the visual styling of Starts and Strecken badges on `KnownLocationCard` to be subtle, understated, and low-contrast, eliminating aggressive visual dominance in location lists while preserving interactive drill-down navigation (ATT-1733):
1. *Subtle Ghost Container Styling (`KnownLocationsScreen.kt`)*:
• Both Starts-Badge and Strecken-Badge (`Surface`) SHALL utilize understated ghost container styling.
• The container background color SHALL use `MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)`, replacing the saturated `primaryContainer.copy(alpha = 0.5f)` pill background.
• The container SHALL incorporate a subtle boundary stroke `BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))` to gently delimit the touch target without commanding visual focus.
• Corner radius SHALL remain `RoundedCornerShape(8.dp)` with compact internal content padding `horizontal = 8.dp, vertical = 4.dp`.
2. *Understated Content & Typography Hierarchy (`KnownLocationsScreen.kt`)*:
• Container `contentColor`, text color, and leading icon tint SHALL use `MaterialTheme.colorScheme.onSurfaceVariant`, ensuring the altitude metric (`ic_ascent` + elevation text) remains the sole primary-accented metric.
• Typography SHALL use `MaterialTheme.typography.labelMedium` with `FontWeight.Medium` (eliminating heavy `labelLarge` bold).
• Leading icons (`Icons.Default.Place`, `ic_favorite_route`) SHALL be scaled to `13.dp` and tinted with `MaterialTheme.colorScheme.onSurfaceVariant`.
• The trailing drill-down chevron (`Icons.AutoMirrored.Filled.ArrowForward`) SHALL be scaled to `11.dp` and tinted with `MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = TTAlpha.Medium)`.
3. *Preservation of Layout Hierarchy & Invariants*:
• The dedicated row placement (`FlowRow`) beneath the altitude metric (`REQ-UI-195`) SHALL remain strictly intact.
• Interactive clickability (`onShowWorkouts`, `onShowRoutes`) SHALL remain 100% operational.
• Test tags `location_starts_badge_${item.id}` and `location_routes_badge_${item.id}` SHALL be preserved on the respective `Surface` components.
• The universal top-left long-press delete context menu (`REQ-UI-061`) SHALL remain intact.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: `REQ-UI-195` (item 2: *Refined Compact Badge Styling*), targeting `KnownLocationCard` in `KnownLocationsScreen.kt`.
2. *Historical Origin & Commit Trace*: Introduced in sprint `2026-40.5` (`ATT-1643`) via commit `073df267`.
3. *Root Reason for Existing Formulation*: `ATT-1643` resolved layout wrapping by decoupling badges into a dedicated row, but carried forward legacy saturated primary container colors from `ATT-1401` and `ATT-1594`. In a multi-card list, this preserved color scheme created unintended visual dominance.
4. *Preservation of Core Invariants*: Dedicated row progression, touch target accessibility, drill-down click navigation (`onShowWorkouts`, `onShowRoutes`), test tags (`location_starts_badge_${item.id}`, `location_routes_badge_${item.id}`), universal delete context menu (`REQ-UI-061`), and 9-language localization parity are 100% strictly preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an athlete viewing the "Lieblingsorte" management list (`KnownLocationsScreen`),
• *When* inspecting `KnownLocationCard`,
• *Then* the Starts-Badge and Strecken-Badge SHALL render with a soft ghost background `surfaceVariant.copy(alpha = 0.35f)` and subtle boundary stroke `outlineVariant.copy(alpha = 0.25f)`, and SHALL NOT display the saturated `primaryContainer` blue pill.
• *Given* `KnownLocationCard` rendered in Light or Dark theme,
• *When* viewing badge text and icons,
• *Then* the text and leading icons SHALL be rendered in `onSurfaceVariant` with `labelMedium` (12sp) `FontWeight.Medium`, and the trailing chevron SHALL be scaled to 11dp with `TTAlpha.Medium`.
• *Given* an athlete viewing `KnownLocationCard`,
• *When* tapping the Starts badge or Strecken badge,
• *Then* the app SHALL navigate to the filtered workouts or routes list respectively.

**Invariants**: Dedicated badges row (`REQ-UI-195`), single-tap card edit, long-press delete context menu (`REQ-UI-061`), navigation callbacks, and 9-language localization parity MUST NOT be broken.

### REQ-UI-208: Filter Dialogs: Contextual 'Start at' Section Heading for Location Filters [Verified]
- **Tests**: `TST-UI-162`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `WorkoutFilterBottomSheet.kt`, `ClusterFilterBottomSheet.kt`, `res/values*/strings.xml`
- **Rationale**: The system SHALL rename the section heading for location filtering in `WorkoutFilterBottomSheet.kt` and `ClusterFilterBottomSheet.kt` from `known_locations_title` (*"Lieblingsorte"* / *"Favorite Locations"*) to a dedicated localized string resource `filter_section_start_at` (*"Start at"* / *"Startet bei"*) (ATT-1732):
1. *Dedicated Filter Heading String Resource (`filter_section_start_at`)*:
• The system SHALL define `filter_section_start_at` across all 9 supported locales: English (`"Start at"`), German (`"Startet bei"`), Spanish (`"Comienza en"`), French (`"Départ à"`), Italian (`"Partenza da"`), Japanese (`"開始地点"`), Dutch (`"Start bij"`), Polish (`"Start w"`), Portuguese (`"Início em"`).
2. *Filter Dialog UI Binding*:
• In `WorkoutFilterBottomSheet.kt`, Section 8 heading SHALL reference `stringResource(R.string.filter_section_start_at)`.
• In `ClusterFilterBottomSheet.kt`, Section 4/5 heading SHALL reference `stringResource(R.string.filter_section_start_at)`.
3. *Preservation of Invariants & Scope Boundaries*:
• In `KnownLocationsScreen.kt`, the top app bar title SHALL strictly preserve `stringResource(R.string.known_locations_title)`.
• In `WorkoutFilterBottomSheet.kt`, Section 9 (*Lieblingsstrecken*) SHALL remain intact for companion ticket `ATT-1731`.
• Chip selection semantics, spatial geofence filtering, active filter chips row display, and dialog dismissal flows SHALL remain 100% operational.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: `REQ-UI-187` and `REQ-UI-194`, targeting filter bottom sheets.
2. *Historical Origin & Commit Trace*: Commits `91acde33` (`ATT-1402`) and `c57e911a` (`ATT-1642`).
3. *Root Reason for Existing Formulation*: Location filtering originally reused entity name `known_locations_title`. User feedback demonstrated an action verb-phrase (`"Startet bei"` / `"Start at"`) provides superior clarity in filter dialogs.
4. *Preservation of Core Invariants*: Spatial filtering, chip toggle, active chips row, test tags, and 9-language localization parity are 100% strictly preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an athlete opening `WorkoutFilterBottomSheet` in German locale,
• *When* the athlete inspects the location filter section,
• *Then* the section title SHALL render as `"Startet bei"`, and SHALL NOT render `"Lieblingsorte"`.
• *Given* an athlete opening `ClusterFilterBottomSheet` in English locale,
• *When* the athlete inspects the location filter section,
• *Then* the section title SHALL render as `"Start at"`, and SHALL NOT render `"Favorite Locations"`.
• *Given* an athlete opening `KnownLocationsScreen`,
• *When* viewing the top app bar,
• *Then* the screen title SHALL remain `"Lieblingsorte"` (DE) / `"Favorite Locations"` (EN).

**Invariants**: Spatial geofence calculations, active filter chips row, dialog dismissal, and 9-language localization parity MUST NOT be broken.

### REQ-UI-209: Workout Filter: Elimination of Favorite Tracks (Lieblingsstrecken) Selection Section [Verified]
- **Tests**: `TST-UI-163`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `WorkoutFilterBottomSheet.kt`, `WorkoutTabsScreen.kt`
- **Rationale**: The system SHALL eliminate the Favorite Tracks / Route Clusters (*Lieblingsstrecken*) filter section and its chip picker from `WorkoutFilterBottomSheet.kt`, removing high latency and visual bloat while preserving external route drill-down filtering and start location (*"Start at"*) filtering (ATT-1731):
1. *Section Removal (`WorkoutFilterBottomSheet.kt`)*:
• Section 9 (*Favorite Tracks / Route Clusters*) SHALL be completely removed from `WorkoutFilterBottomSheet.kt`.
• Local state `localClusterId` and `localClusterName` SHALL be removed.
• Parameter `availableClusters` SHALL be removed from `WorkoutFilterBottomSheet.kt`.
• Section 8 (*"Start at"* / *"Startet bei"*, `REQ-UI-208`) SHALL become the final section of `WorkoutFilterBottomSheet.kt`.
2. *Caller Streamlining (`WorkoutTabsScreen.kt`)*:
• `WorkoutTabsScreen.kt` SHALL omit passing `availableClusters` to `WorkoutFilterBottomSheet`.
3. *Drill-Down Filter Invariant Preservation*:
• In `onApplyCriteria()` in `WorkoutFilterBottomSheet.kt`, active `criteria.clusterId` and `criteria.clusterName` SHALL be preserved on `criteria.copy(...)`.
• `WorkoutFilterCriteria.matches()` and `ActiveFilterChipsRow.kt` cluster chip presentation SHALL remain 100% operational for drill-down navigation from `KnownLocationCard` and `WorkoutHeader`.
• All other filter dimensions (query, date interval, sport sub-type, equipment, attributes, distance interval, duration interval, start location) SHALL remain 100% operational.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: `REQ-UI-187` and `REQ-UI-194`, targeting `WorkoutFilterBottomSheet.kt`.
2. *Historical Origin & Commit Trace*: Commits `91acde33` (`ATT-1402`) and `c57e911a` (`ATT-1642`).
3. *Root Reason for Existing Formulation*: Originally provided complete multi-dimensional filtering, but real-world usage revealed slow availability and excessive options.
4. *Preservation of Core Invariants*: Drill-down from route items into filtered workouts remains fully functional. Start location filtering, active filter chips row, and all other criteria remain 100% intact.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an athlete opening `WorkoutFilterBottomSheet`,
• *When* scrolling to the bottom of the bottom sheet,
• *Then* Section 8 (*"Start at"* / *"Startet bei"*) SHALL be the final filter section, and NO Favorite Tracks (*Lieblingsstrecken*) section or cluster chips SHALL be displayed.
• *Given* an athlete navigated to `WorkoutTabsScreen` via drill-down on a route cluster (`criteria.clusterId != null`),
• *When* opening `WorkoutFilterBottomSheet` and applying a date or sport filter,
• *Then* the active cluster filter SHALL NOT be cleared.
• *Given* an active cluster filter applied to workouts,
• *When* viewing `ActiveFilterChipsRow`,
• *Then* the cluster chip SHALL render cleanly without emojis and tapping `(X)` SHALL clear the cluster filter.

**Invariants**: Drill-down route filtering, active filter chips row, spatial geofence start location filtering, and dialog dismissal MUST NOT be broken.

### REQ-UI-210: Aftermath: Configurable Sections in Detailed Workout Cards (Charts, Zones, Map, Elevation, Extrema) [Verified]
- **Tests**: `TST-UI-164`, `TST-UI-170`, `TST-UI-183`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `WorkoutSummary.kt`, `MyPreferenceManager.kt`, `WorkoutSummariesViewModel.kt`, `DisplaySettingsDialog.kt`
- **Rationale**: The system SHALL provide athlete-configurable section visibility for detailed workout journal cards in Aftermath (`WorkoutSummary.kt`), enabling athletes to tailor card vertical height and visual analytics density directly from display settings (ATT-1714):
1. *Configurable Preferences Model (`WorkoutCardSectionPreferences`)*:
• The system SHALL define an immutable data structure `WorkoutCardSectionPreferences` containing 8 boolean toggles: `showDescription`, `showExtrema`, `showLaps`, `showStrava`, `showMapPreview`, `showElevationProfile`, `showTelemetryCharts` (default false), and `showZoneAnalysis` (default false).
• The system SHALL persist these preferences in `MyPreferenceManager` (DataStore) with atomic reading/updating and expose `workoutCardPreferencesFlow: Flow<WorkoutCardSectionPreferences>`.
2. *Decoupled & Conditional Section Rendering (`WorkoutSummary.kt`)*:
• `WorkoutDescription`, `WorkoutExtrema`, `WorkoutLaps`, and `StravaActivitySection` SHALL be conditionally rendered based on their respective preference toggles.
• The Media section SHALL decouple map preview and elevation profile: if both are enabled, render combined layout; if only map is enabled, render map independently; if only elevation is enabled, render elevation profile independently; if neither, omit media section completely.
• When `showTelemetryCharts` is true, the card SHALL lazily load `PathPoint` track telemetry on `Dispatchers.IO` and render `TelemetryMetricGraph` curves for HR, Speed/Pace, and Power where sensor data exists.
• When `showZoneAnalysis` is true, the card SHALL lazily load `ZoneDistributionData` on `Dispatchers.IO` and render `HeartRateZoneDistributionCard` and `PowerZoneDistributionCard` where zone data exists.
3. *Settings UI Integration (`DisplaySettingsDialog.kt`)*:
• `DisplaySettingsDialog.kt` SHALL provide a structured configuration category titled *"Trainingsliste (Detail-Karten)"* / *"Workout List (Detailed Cards)"* with intuitive switches for all 8 configurable sections.
4. *100% 9-Language Localization Parity*:
• All section toggle titles and descriptions SHALL be defined across all 9 supported locales (EN, DE, ES, FR, IT, JA, NL, PL, PT).
5. *Preservation of Core Invariants*:
• `WorkoutHeader` and `WorkoutDetails` SHALL remain mandatory anchors on every card and are NOT removable.
• All card touch interactions and lazy loading 60/120fps scrolling performance are strictly preserved.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement (`REQ-UI-210`), extending Epic `ATT-355` (*Good and consistent UI*), `REQ-UI-085`, `REQ-UI-206`, and `REQ-UI-207` for `WorkoutSummary.kt` and `DisplaySettingsDialog.kt`.
2. *Historical Origin & Commit Trace*: `WorkoutSummary.kt` was created in ATT-850 and enhanced in ATT-510, ATT-1391, ATT-1739, and ATT-1740.
3. *Root Reason for Existing Formulation*: Sections were rendered unconditionally whenever data was present. User feedback requested granular toggles to customize card size and directly view telemetry curves and zone distributions in the list.
4. *Preservation of Core Invariants*: Mandatory header/details anchors, single-tap navigation, long-press context menu, and lazy loading performance are strictly preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an athlete in `DisplaySettingsDialog`,
• *When* disabling Map Preview and saving,
• *Then* workout cards in `WorkoutList` SHALL omit the map preview while retaining other enabled sections.
• *Given* an athlete enables Telemetry Charts or Zone Analysis in settings,
• *When* viewing a workout card with recorded telemetry or zones,
• *Then* the card SHALL render continuous curves and 5-zone column charts respectively.
• *Given* all 8 configurable sections are toggled off,
• *When* viewing workout cards,
• *Then* the card SHALL still render `WorkoutHeader` and `WorkoutDetails` cleanly.

**Invariants**: Mandatory card anchors `WorkoutHeader` and `WorkoutDetails` always rendered; all card touch interactions and background IO loading 60/120fps scrolling performance MUST NOT be broken.

### REQ-UI-211: Aftermath: Configurable Fields in Edit Workout Dialog (Route, Commute/Trainer, Strava, Description, Goal, Method) [Verified]
- **Tests**: `TST-UI-165`, `TST-UI-170`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `EditWorkoutScreen.kt`, `EditWorkoutViewModel.kt`, `MyPreferenceManager.kt`, `DisplaySettingsDialog.kt`
- **Rationale**: The system SHALL provide athlete-configurable metadata field visibility for the "Edit Workout" dialog in Aftermath (`EditWorkoutScreen.kt`), allowing athletes to streamline the form layout according to their personal training and journaling workflow (ATT-1713):
1. *Configurable Preferences Model (`EditWorkoutFieldPreferences`)*:
• The system SHALL define an immutable data structure `EditWorkoutFieldPreferences` containing 6 boolean toggles: `showCluster` (default true), `showCommuteTrainer` (default true), `showStravaUpload` (default true), `showDescription` (default true), `showGoal` (default true), and `showMethod` (default true).
• The system SHALL persist these preferences in `MyPreferenceManager` (DataStore) with atomic reading/updating and expose `editWorkoutFieldPreferencesFlow: Flow<EditWorkoutFieldPreferences>` and `suspend fun setEditWorkoutFieldPreferences(prefs: EditWorkoutFieldPreferences)`.
2. *Conditional Form Layout (`EditWorkoutScreen.kt`)*:
• In `EditWorkoutScreen.kt`, optional fields SHALL be conditionally rendered according to active `EditWorkoutFieldPreferences`: Route/Cluster selection (if `showCluster`), Commute and Trainer checkboxes (if `showCommuteTrainer`), Strava upload checkbox (if `showStravaUpload` and community enabled), Description field (if `showDescription`), Goal field (if `showGoal`), and Method field (if `showMethod`).
3. *Data Preservation Invariant (`EditWorkoutViewModel.kt`)*:
• When saving changes (`saveChanges()`) while certain fields are hidden via preferences, existing values of hidden fields (e.g. `goal`, `method`, `commute`, `trainer`, `clusterId`) SHALL remain completely intact in the saved `WorkoutData` entity without data loss or corruption.
4. *Settings UI Integration (`DisplaySettingsDialog.kt`)*:
• `DisplaySettingsDialog.kt` SHALL incorporate a dedicated configuration category titled *"Training bearbeiten"* / *"Edit Workout"* with 6 toggle switches for each configurable field.
5. *100% 9-Language Localization Parity*:
• All setting category and toggle titles SHALL be defined across all 9 supported locales (EN, DE, ES, FR, IT, JA, NL, PL, PT).
6. *Preservation of Core Invariants*:
• Mandatory Core Fields: Workout Name (`hint_workout_name`), Sport Type (`Sport`), and Equipment (`Equipment`) SHALL remain permanently rendered and non-configurable on every invocation.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement (`REQ-UI-211`), extending Epic `ATT-355` (*Good and consistent UI*) and companion to `REQ-UI-210` (`ATT-1714`).
2. *Historical Origin & Commit Trace*: `EditWorkoutScreen.kt` was created in ATT-140 and enhanced in ATT-318.
3. *Root Reason for Existing Formulation*: All fields were rendered unconditionally. User feedback requested field customization to avoid excessive vertical scrolling and eliminate irrelevant fields.
4. *Preservation of Core Invariants*: Mandatory core fields, existing database values, cluster assignment logic, and dropdown selectors remain strictly intact.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an athlete in `DisplaySettingsDialog`,
• *When* disabling Goal and Method and saving,
• *Then* `EditWorkoutDialog` SHALL omit Goal and Method text fields from the form.
• *Given* an existing workout with non-empty Goal and Method values,
• *When* the workout is edited with Goal and Method hidden and the user updates Workout Name,
• *Then* saving SHALL preserve the original Goal and Method values in the database.
• *Given* any combination of field preferences,
• *When* opening `EditWorkoutDialog`,
• *Then* Workout Name, Sport Type dropdown, and Equipment dropdown SHALL remain fully visible and operational.

**Invariants**: Mandatory core fields (Name, Sport, Equipment) must never be hidden; saving with hidden fields must never erase existing database values; 9-language localization parity MUST NOT be broken.

### REQ-UI-212: Configurable Cockpit Typography (Font Family & Boldness) in Advanced Settings [Verified]
- **Tests**: `TST-UI-166`, `TST-UI-181`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `CockpitTypography.kt`, `TuningPreferencesDataStore.kt`, `AdvancedTuningDialog.kt`, `SensorFieldView.kt`, `SensorGridScreen.kt`
- **Rationale**: The system SHALL provide athlete-configurable typography options for telemetry values and unit labels across the live tracking cockpit in Advanced Settings (`AdvancedTuningDialog.kt`), enabling athletes to customize cockpit aesthetics, glanceability, and visual readability under varying lighting, movement, and eyesight conditions (ATT-1751):
1. *Cockpit Typography Domain Models (`CockpitTypography.kt`)*:
• The system SHALL define `CockpitFontWeight`: `NORMAL` (`FontWeight.Normal`), `SEMI_BOLD` (`FontWeight.SemiBold`, default), and `BOLD` (`FontWeight.Bold`).
• The system SHALL define `CockpitFontFamily` with curated choices: `SYSTEM_DEFAULT` (default system font / `FontFamily.Default`), `SEVEN_SEGMENT` (classic digital retro LCD font `Orbitron`), `MODERN_ATHLETIC` (condensed athletic sans-serif `Roboto Condensed`), `MONOSPACE` (fixed-width tabular digits `FontFamily.Monospace` / `Roboto Mono`), and `PLAYFUL` (casual font `Comic Neue`).
• `CockpitTypography.kt` SHALL provide defensive fallback resolution to standard Compose families (`Default`, `Monospace`, `SansSerif`, `Cursive`) to guarantee 100% test safety on JVM unit test runners and offline reliability.
2. *Preferences DataStore Management (`TuningPreferencesDataStore.kt`)*:
• The system SHALL persist cockpit typography in `TuningConfig` under DataStore keys `KEY_COCKPIT_FONT_FAMILY` and `KEY_COCKPIT_FONT_WEIGHT`.
• Default values SHALL strictly be `SYSTEM_DEFAULT` and `SEMI_BOLD`, ensuring 100% backward compatibility for existing users.
• Factory reset in `TuningPreferencesDataStore` SHALL restore `SYSTEM_DEFAULT` and `SEMI_BOLD`.
3. *Advanced Settings UI Integration (`AdvancedTuningDialog.kt`)*:
• `AdvancedTuningDialog.kt` SHALL provide a dedicated configuration section titled *"Cockpit-Typografie"* / *"Cockpit Typography"* (`@string/tuning_cat_cockpit_typography`).
• The section SHALL feature a font family selector (`SYSTEM_DEFAULT`, `SEVEN_SEGMENT`, `MODERN_ATHLETIC`, `MONOSPACE`, `PLAYFUL`) with localized display names.
• The section SHALL feature a 3-way segmented button or toggle for boldness (`Normal`, `Semi-Bold`, `Bold`).
• The section SHALL provide an interactive live preview card displaying sample sensor metrics (e.g. "148 bpm", "28.5 km/h", "1:24:35") reflecting current font and boldness selections immediately.
4. *Cockpit HUD Rendering Integration (`SensorFieldView.kt`)*:
• `SensorFieldView.kt` and `SensorGridScreen.kt` SHALL consume configured typography via `LocalCockpitTypography` CompositionLocal, keeping `SensorFieldState` and database schemas decoupled.
• `getSensorValueTextStyle` and `getSensorUnitTextStyle` SHALL parameterize font family and weight across all 9 `ViewSize` steps (`XSMALL` through `XXXHUGE`) without vertical clipping, baseline shifts, or unit label collision.
5. *100% 9-Language Localization Parity*:
• All section titles, font family names, and boldness labels SHALL be defined across all 9 supported locales (EN, DE, ES, FR, IT, JA, NL, PL, PT).

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement (`REQ-UI-212`), extending Epic `ATT-355` (*Good and consistent UI*) and building upon `REQ-UI-171` (*High-Contrast Cockpit Typography and Subtle Tile Grid*) and `REQ-UI-181` (*Ultra-Large Cockpit Typography Extensions*).
2. *Historical Origin & Commit Trace*: Tickets `ATT-1264` and `ATT-1431`.
3. *Root Reason for Existing Formulation*: `REQ-UI-171` hardcoded `FontWeight.SemiBold` and default system font to guarantee high contrast and sunlight legibility on AMOLED displays. Providing user-selectable font families and boldness empowers athletes to tailor the HUD to their specific eyesight and mounting preferences.
4. *Preservation of Core Invariants*: Default configuration remains strictly `SEMI_BOLD` and `SYSTEM_DEFAULT` (100% visual parity for existing users); the 9-step `ViewSize` scale (`XSMALL` .. `XXXHUGE`) and proportional unit scaling remain immutable; AMOLED high-contrast color tokens are preserved; zero mutation to `SensorFieldState` or SQLite schemas.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an athlete in `AdvancedTuningDialog`,
• *When* selecting '7-Segment Digital' (`SEVEN_SEGMENT`) and 'Bold',
• *Then* the live preview card and the Cockpit HUD SHALL render sensor metric values in 7-segment digital LCD digits with bold weight.
• *Given* an athlete selecting 'Monospace' or 'Modern Athletic',
• *When* viewing rapidly changing metrics during a workout,
• *Then* digits SHALL render cleanly without horizontal jitter or layout shifts.
• *Given* an athlete selecting 'Playful',
• *When* viewing the Cockpit HUD,
• *Then* sensor metrics SHALL render in the casual/playful typeface across all sensor fields.
• *Given* any combination of font family and boldness,
• *When* displayed across all 9 tile sizes (`XSMALL` through `XXXHUGE`),
• *Then* numbers and unit labels SHALL fit cleanly within their tiles without clipping, truncation, or baseline overflow.
• *Given* a fresh install or existing update,
• *When* opening the Cockpit HUD without altering typography settings,
• *Then* metrics SHALL default to the system default font with Semi-Bold weight.

**Invariants**: Default settings (`SYSTEM_DEFAULT`, `SEMI_BOLD`) 100% preserved; 9 `ViewSize` scale preserved; zero changes to SQLite schemas or `SensorFieldState`; 9-language localization parity MUST NOT be broken.

### REQ-UI-213: Aftermath: Resilient Map Preview Visibility & Scrollable Telemetry Layout Architecture [Verified]
- **Tests**: `TST-UI-167`, `TST-UI-180`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Rationale**: The system SHALL ensure the map preview remains continuously visible and interactive with a guaranteed viewport height while providing seamless vertical scroll access to all telemetry graphs and analytics cards in Aftermath (`MapDetailLayout.kt` / `TrackOnMapScreen.kt`) (ATT-1812):
1. *Adaptive Layout Detection (`MapDetailLayout.kt`)*:
• The layout SHALL detect whether overflow-inducing content is present: `val hasTelemetryGraphs = showZoomControls && activeScrubPath != null && (TelemetryMetricUtils.hasHeartRateData(activeScrubPath)

### REQ-UI-214: Aftermath: Telemetry Metric Graph Ordering (Speed/Pace Preceding Heart Rate) for Clustered Cardiovascular Exertion Flow [Verified]
- **Tests**: `TST-UI-168`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `MapDetailLayout.kt`, `WorkoutSummary.kt`
- **Rationale**: The system SHALL order continuous telemetry metric graphs such that the Speed/Pace graph precedes the Heart Rate graph, clustering the continuous Heart Rate curve directly adjacent to the 5-zone distribution card (ATT-1813):
1. *Layout Ordering in MapDetailLayout.kt*: In detailed inspection view (`MapDetailLayout.kt` / `TrackOnMapScreen.kt`), under `showZoomControls`, continuous telemetry graphs SHALL be arranged strictly in the order: (1) Speed / Pace Graph if `hasSpeedData(path)`, (2) Heart Rate Graph if `hasHeartRateData(path)`, (3) Cycling Power Graph if `hasPowerData(path)`. This sequence SHALL precede `analyticsContent`.
2. *Layout Ordering in WorkoutSummary.kt*: In workout summary cards (`WorkoutSummary.kt`), under `showTelemetryCharts`, continuous telemetry graphs SHALL be arranged strictly in the order: (1) Speed / Pace Graph if `hasSpeedData(telemetryPoints)`, (2) Heart Rate Graph if `hasHeartRateData(telemetryPoints)`, (3) Cycling Power Graph if `hasPowerData(telemetryPoints)`. This sequence SHALL precede `showZoneAnalysis` cards.
3. *Clustered Visual Flow*: For workouts recording Heart Rate and Speed/Pace without a cycling power meter, the continuous Heart Rate graph SHALL sit directly adjacent to the Heart Rate Zone Distribution card without Speed/Pace intervening, providing a cohesive cardiovascular visual rhythm.
4. *Preservation of Core Invariants*: Conditional metric presence checks, zero extra whitespace for absent metrics, synchronized scrubbing via `selectedDistance`, sport-adaptive Pace/Speed units, and 9-language localization parity remain 100% preserved.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement (`REQ-UI-214`), refining `REQ-UI-206` under Epic `ATT-111` (*Compact Post-Workout Visual Analytics & Graphs*).
2. *Historical Origin & Commit Trace*: `ATT-1740` introduced `TelemetryMetricGraph.kt` and `REQ-UI-206` with arbitrary HR -> Speed -> Power ordering.
3. *Root Reason for Existing Formulation*: Metric curves were initially arranged matching raw data structure ordering rather than user cognitive flow.
4. *Preservation of Core Invariants*: Synchronized multi-metric scrubbing, dynamic scaling, sport-adaptive units, and localization parity are 100% strictly preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an athlete viewing a completed workout with Heart Rate and GPS speed in Aftermath (`TrackOnMapScreen`),
• *When* inspecting the scrollable graphs section,
• *Then* the Speed/Pace graph SHALL appear above the Heart Rate graph, and the Heart Rate graph SHALL appear directly above the Heart Rate Zone Distribution card.
• *Given* an athlete viewing a workout card in `WorkoutSummary` with telemetry charts enabled,
• *When* the card renders,
• *Then* the Speed/Pace graph SHALL precede the Heart Rate graph.
• *Given* a workout without Heart Rate data,
• *When* rendered,
• *Then* Speed/Pace SHALL render cleanly and Heart Rate SHALL be omitted with zero extraneous whitespace.

**Invariants**: Synchronized scrubbing across graphs and map, running pace vs speed selection, and 9-language localization parity MUST NOT be broken.

### REQ-UI-215: Aftermath: Global Synchronized Horizontal Zoom & Panning Architecture Across Stacked Telemetry Graphs [Verified]
- **Tests**: `TST-UI-169`, `TST-UI-179`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `MapDetailLayout.kt`, `ElevationProfile.kt`, `TelemetryMetricGraph.kt`
- **Rationale**: The system SHALL synchronize horizontal zoom and panning globally across all stacked metric graphs (Elevation Profile, Speed/Pace, Heart Rate, and Power) on the detailed workout inspection screen (`MapDetailLayout.kt` / `TrackOnMapScreen.kt`) (ATT-1814):
1. *Global Horizontal Zoom State Hoisting (MapDetailLayout.kt)*: The inspection screen SHALL maintain hoisted horizontal zoom state: `profileZoomScale: Float` (default 1.0f) and `profileStartDist: Double` (default 0.0), reset upon path changes. This state SHALL be forwarded to `ElevationProfile` and to all displayed `TelemetryMetricGraph` instances.
2. *Bidirectional Zoom & Panning Synchronization (ElevationProfile.kt)*: `ElevationProfile` SHALL declare optional parameters `zoomScale: Float = 1.0f`, `startDist: Double = 0.0`, and callback `onZoomChanged: ((zoomScale: Float, startDist: Double) -> Unit)? = null`. When `onZoomChanged != null`, button taps (+, -, Reset), pinch-to-zoom gestures, and pan drag gestures SHALL update hoisted state via `onZoomChanged`. When unprovided, `ElevationProfile` SHALL fall back to internal state, preserving 100% backward compatibility for all unhoisted callers.
3. *Synchronized Window Rendering & Interaction (TelemetryMetricGraph.kt)*: `TelemetryMetricGraph` SHALL accept `zoomScale: Float = 1.0f` and `startDist: Double = 0.0`. Point coordinates SHALL map using `ElevationProfileZoomMath.distanceToCanvasX(xSpan, startDist, visibleSpan, chartWidthPx)`. X-axis ticks along the bottom axis SHALL reflect `[startDist .. startDist + visibleSpan]`. Touch scrubbing gestures SHALL translate touch coordinates to distance using `ElevationProfileZoomMath.canvasXToDistance`. The scrubbing cursor line and marker dot SHALL map via `distanceToCanvasX` and SHALL be omitted when outside the visible window.
4. *Preservation of Core Invariants*: All stacked graphs continue sharing identical horizontal padding (`start = 50.dp, end = 25.dp`), guaranteeing pixel-perfect tick and curve alignment across stacked charts.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement (`REQ-UI-215`), extending `REQ-UI-192` (*Elevation Profile: Zoom, Panning, and Elevation Bounds Math*) and `REQ-UI-206` under Epic `ATT-111` (*Compact Post-Workout Visual Analytics & Graphs*).
2. *Historical Origin & Commit Trace*: `ATT-1740` introduced multi-metric telemetry graphs, which previously operated without horizontal zoom coordination.
3. *Root Reason for Existing Formulation*: Zoom state was initially encapsulated inside `ElevationProfile` before continuous metric graphs existed.
4. *Preservation of Core Invariants*: Backward compatibility for unhoisted `ElevationProfile` callers, pixel-perfect horizontal padding alignment, and synchronized scrubbing are 100% strictly preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an athlete viewing a workout with Elevation, Speed, and Heart Rate graphs in Aftermath (`TrackOnMapScreen`),
• *When* the athlete zooms in (e.g. 2.0x) on the Elevation Profile,
• *Then* Elevation Profile, Speed/Pace graph, and Heart Rate graph SHALL synchronously zoom into the exact same distance/time range with aligned tick milestones.
• *Given* zoomed graphs,
• *When* the athlete pans or taps Reset,
• *Then* all displayed graphs SHALL pan in lockstep or return to 1.0x full view simultaneously.
• *Given* zoomed graphs,
• *When* scrubbing across any graph,
• *Then* the vertical cursor line SHALL appear at the exact same horizontal coordinate across all visible graphs.

**Invariants**: Shared 50.dp start / 25.dp end padding geometry, single-finger multi-chart scrubbing synchronization, and unhoisted ElevationProfile compatibility MUST NOT be broken.

### REQ-UI-216: Settings/Aftermath: Relocation of Workout Card Sections and Edit Workout Fields Customization Toggles to Advanced Settings [Verified]
- **Tests**: `TST-UI-170`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `DisplaySettingsDialog.kt`, `AdvancedTuningDialog.kt`
- **Rationale**: The system SHALL relocate the Aftermath customization toggle sections from the standard Display Settings bottom sheet (`DisplaySettingsDialog.kt`) to Advanced Settings (`AdvancedTuningDialog.kt`) under Category 4 (*"Aftermath & Profil-Analytik"*), eliminating visual clutter in standard display preferences while centralizing all post-workout inspection customization (ATT-1815):
1. *Display Settings De-cluttering (`DisplaySettingsDialog.kt`)*:
• The system SHALL remove Section *"Trainingsliste (Detail-Karten)"* / *"Workout List (Detailed Cards)"* (`settings_workout_card_title`) and its 8 switches from `DisplaySettingsDialog.kt`.
• The system SHALL remove Section *"Training bearbeiten"* / *"Edit Workout"* (`settings_edit_workout_title`) and its 6 switches from `DisplaySettingsDialog.kt`.
• `DisplaySettingsDialog.kt` SHALL remove state variables, LaunchedEffects, and persistence logic for `WorkoutCardSectionPreferences` and `EditWorkoutFieldPreferences`.
• `DisplaySettingsDialog.kt` SHALL retain the "Advanced Settings" navigation button (`onNavigateToTuning`) providing direct access to `AdvancedTuningDialog`.
2. *Advanced Tuning Integration (`AdvancedTuningDialog.kt`)*:
• In `AdvancedTuningDialog.kt`, under Category 4 (*"Aftermath & Profil-Analytik"* / `tuning_cat_aftermath`), positioned directly below the Profile X-Axis Domain selector, the system SHALL render both customization sections:
- Workout List Card Sections (`settings_workout_card_title`) with 8 toggles for Description, Extrema, Laps, Strava, Map Preview, Elevation Profile, Telemetry Charts, and Zone Analysis.
- Edit Workout Fields (`settings_edit_workout_title`) with 6 toggles for Description, Route/Cluster, Commute & Trainer, Strava Upload, Goal, and Method.
• Toggles SHALL be rendered using consistent Material 3 styling.
3. *DataStore Lifecycle & Reset to Factory Defaults*:
• `AdvancedTuningDialog.kt` SHALL collect `workoutCardPreferencesFlow` and `editWorkoutFieldPreferencesFlow` from `MyPreferenceManager` and initialize mutable dialog state.
• Upon Save (`onSave`), `AdvancedTuningDialog.kt` SHALL persist both models via `setWorkoutCardPreferences` and `setEditWorkoutFieldPreferences`.
• Upon Factory Reset (`resetToDefaults`), `AdvancedTuningDialog.kt` SHALL restore `WorkoutCardSectionPreferences()` and `EditWorkoutFieldPreferences()` default instances in DataStore and local state.
4. *100% 9-Language Localization Parity*:
• All referenced string resources SHALL maintain 100% complete definitions across all 9 supported locales (EN, DE, ES, FR, IT, JA, NL, PL, PT).
5. *Preservation of Core Invariants*:
• DataStore persistence keys (`workout_card_show_*`, `edit_workout_show_*`) remain strictly unchanged.
• Reactive consumers (`WorkoutSummary.kt`, `EditWorkoutScreen.kt`) continue functioning with zero modifications.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement (`REQ-UI-216`), refining UI integration of `REQ-UI-210` (Section 3) and `REQ-UI-211` (Section 4) under Epic `ATT-355` (*Good and consistent UI*).
2. *Historical Origin & Commit Trace*: Sprint 2026-40.6 commits `e35a1bb2` (`ATT-1714`) and `4d8961ca` (`ATT-1713`).
3. *Root Reason for Existing Formulation*: Toggles were initially placed in `DisplaySettingsDialog.kt` as an expedient solution, but user testing revealed this bloated standard display settings with 14 domain-specific Aftermath toggles.
4. *Preservation of Core Invariants*: All DataStore keys, default values, downstream reactive rendering behaviors, and 9-language strings are 100% strictly preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an athlete opening standard Display Settings (`DisplaySettingsDialog`),
• *When* inspecting the sheet,
• *Then* the sheet SHALL display core display options and the Advanced Settings button, and SHALL NOT display any Workout Card or Edit Workout section toggles.
• *Given* an athlete opening Advanced Settings (`AdvancedTuningDialog`),
• *When* scrolling to Category 4 (*Aftermath & Profil-Analytik*),
• *Then* the athlete SHALL see the Profile X-Axis Domain selector, followed by the Workout List Card Sections (8 toggles) and Edit Workout Fields (6 toggles).
• *Given* an athlete modifying Aftermath toggles in `AdvancedTuningDialog` and saving,
• *When* viewing workout cards or opening Edit Workout,
• *Then* the configured section and field visibilities SHALL immediately reflect the saved preferences.
• *Given* modified Aftermath preferences,
• *When* tapping "Reset to Factory Defaults",
• *Then* both preference models SHALL revert to their default configurations.

**Invariants**: DataStore keys, default values, downstream consumer reactivity, and 9-language localization parity MUST NOT be broken.

### REQ-UI-217: Lieblingsorte: Map Preview Thumbnail and Standardized Title Typography on KnownLocationCard [Verified]
- **Tests**: `TST-UI-171`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `KnownLocationsScreen.kt`
- **Rationale**: The system SHALL refine the layout of `KnownLocationCard` in `KnownLocationsScreen.kt` by standardizing the title typography and integrating a spatial map preview thumbnail on the right side of the card (ATT-1816):
1. *Standardized Title Typography (`KnownLocationsScreen.kt`)*:
• The card's location name (`item.name`) SHALL use `MaterialTheme.typography.titleLarge` with `FontWeight.Bold` and `TextOverflow.Ellipsis`, standardizing heading proportions with `WorkoutClusterIdentityRow` and other card headers across the app.
2. *Two-Column Content Layout Progression*:
• Below the location title, the card content SHALL be arranged in a horizontal `Row` (`modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically`):
- Left Column (`modifier = Modifier.weight(1f)`): houses the Altitude Metric Row (`ic_ascent` + elevation text) followed by the Dedicated Badges Row (`FlowRow` housing Starts-Badge and, when available, Strecken-Badge per `REQ-UI-195` and `REQ-UI-207`).
- Right Column: houses the Map Preview Thumbnail (`KnownLocationThumbnailMap`).
3. *Spatial Map Preview Thumbnail (`KnownLocationThumbnailMap`)*:
• The map thumbnail SHALL be rendered within a `Surface` with dimensions `Modifier.size(80.dp)`, corner radius `RoundedCornerShape(12.dp)`, background `MaterialTheme.colorScheme.surfaceContainerHigh`, and test tag `location_map_preview_${item.id}`.
• The map SHALL run in Google Maps lite mode (`GoogleMapOptions().liteMode(true)`) with all gestures and controls disabled (`zoomControlsEnabled = false, scrollGesturesEnabled = false, zoomGesturesEnabled = false, tiltGesturesEnabled = false, rotationGesturesEnabled = false, myLocationButtonEnabled = false, compassEnabled = false, mapToolbarEnabled = false`).
• The camera SHALL be centered at `item.latLng` at zoom level `14.5f`.
• The map SHALL render a `Marker` positioned at `item.latLng` using `createHeartPinMarker` with the primary accent color (`MaterialTheme.colorScheme.primary`) and white heart fill.
• The map SHALL render a `Circle` centered at `item.latLng` with radius `item.radius.toDouble()`, fill color `primary.copy(alpha = 0.2f)`, and stroke color `primary.copy(alpha = 0.6f)` with width `2f`.
• The map SHALL apply dark-mode styling via `DarkMapStyle.resolveMapProperties(isDark, context)` and include `DarkMapAntiFlashOverlay(isMapLoaded = isMapLoaded, isDark = isDark)`.
• Tapping the thumbnail (`onMapClick`) SHALL trigger `onShowOnMap()`, navigating the athlete directly to the full map centered on this location.
• Offline & Testing Safety: Under `LocalInspectionMode.current`, the component SHALL render a schematic Canvas representation with a circular geofence boundary and pin icon, preventing Google Play Services failures in unit tests and Compose Previews.
4. *Preservation of Core Invariants*:
• Card single-tap edit (`onEdit`), long-press delete context menu (`REQ-UI-061`), starts drill-down (`onShowWorkouts`), and routes drill-down (`onShowRoutes`) SHALL remain 100% operational.
• Zero regression in 9-language localization parity (`REQ-UI-106`).

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: `REQ-UI-195` (item 1: *Location name header Text, titleMedium, FontWeight.Bold*), targeting `KnownLocationCard` in `KnownLocationsScreen.kt`.
2. *Historical Origin & Commit Trace*: Introduced in sprint `2026-40.5` (`ATT-1643`) via commit `073df267`.
3. *Root Reason for Existing Formulation*: `REQ-UI-195` decoupled the altitude metric from badges and used `titleMedium` for the heading before a map thumbnail was envisioned. User testing and design review revealed that `titleMedium` was too small relative to cluster and workout list cards, and athletes lacked spatial orientation without an inline map.
4. *Preservation of Core Invariants*: Full drill-down navigation to workouts and routes remains intact; universal delete context menu (`REQ-UI-061`) remains intact; altitude metric preceding badges row is preserved on the left column.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an athlete viewing the "Lieblingsorte" screen (`KnownLocationsScreen`),
• *When* inspecting any `KnownLocationCard`,
• *Then* the location name SHALL render in `MaterialTheme.typography.titleLarge` with `FontWeight.Bold`.
• *Given* an athlete viewing `KnownLocationCard`,
• *When* inspecting the right side of the card,
• *Then* a square 80dp rounded map thumbnail (`location_map_preview_${item.id}`) SHALL be displayed, rendering a heart pin marker and geofence circle centered at the location coordinates.
• *Given* an athlete viewing `KnownLocationCard`,
• *When* tapping the map preview thumbnail,
• *Then* the app SHALL navigate to the map view centered on the location (`onShowOnMap`).
• *Given* an athlete tapping the card body (outside the map thumbnail and badges),
• *When* tapped,
• *Then* the Edit Location dialog SHALL open (`onEdit`).
• *Given* an athlete long-pressing the card,
• *When* long-pressed,
• *Then* the universal Delete context menu SHALL appear (`REQ-UI-061`).

**Invariants**: Single-tap card edit, long-press delete context menu (`REQ-UI-061`), drill-down navigation callbacks, and 9-language localization parity MUST NOT be broken.

### REQ-UI-218: UI/Theme: Standardized Surface Background Tokens and Zero Tonal Elevation across Popups, Bottom Sheets, and Dialogs [Verified]
- **Tests**: `TST-UI-172`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `BottomSheetDesign.kt`, `AppModalBottomSheet.kt`, `FilterBottomSheetScaffold.kt`, `MapScreenWithTrack.kt`, `SensorGridScreen.kt`, `PeriodMapScreen.kt`, `WorkoutClusterHeatmapScreen.kt`, `MapDetailLayout.kt`, `LIveSegmentSheet.kt`
- **Rationale**: The system SHALL standardize all bottom sheet scaffolds, modal bottom sheets, settings sheets, live segment popups, and confirmation dialogs to use clean, consistent `MaterialTheme.colorScheme.surface` tokens with zero tonal elevation (`SheetTonalElevation = 0.dp`), eliminating artificial grey overlays (`#EDEDED`) and maintaining seamless surfaces across the application (ATT-1817):
1. *Centralized Design Token Refinement (`BottomSheetDesign.kt`)*:
• `SheetTonalElevation` SHALL be standardized at `0.dp` (reduced from `2.dp`) to eliminate Material 3's automatic 6.94% grey tint overlay over white surfaces in Light mode.
• All physical boundary tokens (`SheetCornerRadius = 20.dp`, `SheetShape`, `SheetShadowElevation = 8.dp`, `BorderWidth = 1.dp`, `DragHandleWidth = 32.dp`, `DragHandleHeight = 3.dp`) SHALL remain strictly preserved.
2. *Modal Bottom Sheets (`AppModalBottomSheet.kt`, `FilterBottomSheetScaffold.kt`)*:
• In `AppModalBottomSheet.kt`, `ModalBottomSheet` SHALL explicitly declare `containerColor = MaterialTheme.colorScheme.surface` and `tonalElevation = BottomSheetDesign.SheetTonalElevation` (`0.dp`).
• In `AppModalBottomSheet.kt`, `AppBottomSheetContent` SHALL declare `color = MaterialTheme.colorScheme.surface` and `tonalElevation = BottomSheetDesign.SheetTonalElevation` (`0.dp`).
• In `FilterBottomSheetScaffold.kt`, `ModalBottomSheet` SHALL explicitly declare `containerColor = MaterialTheme.colorScheme.surface` and `tonalElevation = BottomSheetDesign.SheetTonalElevation` (`0.dp`).
3. *Persistent Bottom Sheet Scaffolds (`MapScreenWithTrack.kt`, `SensorGridScreen.kt`, `PeriodMapScreen.kt`, `WorkoutClusterHeatmapScreen.kt`)*:
• `BottomSheetScaffold` instances in `MapScreenWithTrack.kt`, `SensorGridScreen.kt`, `PeriodMapScreen.kt`, and `WorkoutClusterHeatmapScreen.kt` SHALL uniformly declare `sheetContainerColor = MaterialTheme.colorScheme.surface`, `sheetTonalElevation = BottomSheetDesign.SheetTonalElevation` (`0.dp`), `sheetShape = BottomSheetDesign.SheetShape`, and `sheetShadowElevation = BottomSheetDesign.SheetShadowElevation` (`8.dp`).
4. *LiveSegment & Map Detail Layout Seamlessness (`MapDetailLayout.kt`, `LIveSegmentSheet.kt`)*:
• In `MapDetailLayout.kt`, when operating in bottom sheet mode (`!useStatusBarsPadding`), the root `Column` SHALL apply `.background(MaterialTheme.colorScheme.surface)`.
• The slotted header `Surface`, the elevation profile `Surface`, and the analytics `Surface` SHALL uniformly bind `color = MaterialTheme.colorScheme.surface` with zero tonal elevation, eliminating visual seams.
• In `LIveSegmentSheet.kt`, header and metrics columns SHALL seamlessly share `MaterialTheme.colorScheme.surface`.
5. *Confirmation Dialogs Standardization (`StravaSettingsDialog.kt`, `WorkoutClusterHeatmapScreen.kt`, `WorkoutClustersScreen.kt`, `DevicesTabbedScreen.kt`, `ImportBackupTabsScreen.kt`)*:
• Confirmation `AlertDialog` instances SHALL explicitly declare `containerColor = MaterialTheme.colorScheme.surface` and `tonalElevation = 0.dp`, matching `DeleteConfirmationDialog.kt` and `DeleteOldWorkoutsDialog.kt`.
6. *Preservation of Core Invariants*:
• Top curvature (20.dp), boundary contour stroke (1.dp outlineVariant at 60% opacity), drop shadow (8.dp), and drag handle pill (32dp x 3dp) SHALL remain 100% preserved.
• System bars edge-to-edge insets (`REQ-UI-148`) SHALL NOT be altered.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: `REQ-UI-189` (boundary tokens) and `REQ-UI-196` (drag handle & popup surface), targeting `BottomSheetDesign.kt`, `SensorGridScreen.kt`, `MapDetailLayout.kt`.
2. *Historical Origin & Commit Trace*: Commit `69c84e1b` (ATT-1588) introduced `SheetTonalElevation = 2.dp`. Commits `e7ae455c` (ATT-1644) and `54f90713` (ATT-1735) harmonized drag handle dimensions.
3. *Root Reason for Existing Formulation*: `SheetTonalElevation = 2.dp` caused M3 to apply a 6.94% grey tint overlay (`#EDEDED`) to white surfaces in Light mode. Because `LiveSegmentSheet`'s header used `Modifier.background()` while the elevation container used `Surface()`, the 2.dp tonal elevation produced an unintended contrast split.
4. *Preservation of Core Invariants*: Setting `SheetTonalElevation = 0.dp` and explicitly specifying `containerColor = surface` eliminates the contrast split while preserving all physical boundary delineations, edge-to-edge insets, and gesture responsiveness.

**Acceptance Criteria (Given-When-Then)**:
• *Given* `BottomSheetDesign` design tokens object,
• *When* `SheetTonalElevation` is evaluated,
• *Then* it SHALL equal `0.dp`.
• *Given* an athlete opening any modal bottom sheet (`AppModalBottomSheet`, `FilterBottomSheetScaffold`),
• *When* viewed in Light mode,
• *Then* the sheet container SHALL render with clean `MaterialTheme.colorScheme.surface` background with 0.dp tonal elevation.
• *Given* an athlete viewing map bottom sheets (`MapScreenWithTrack`, `PeriodMapScreen`, `WorkoutClusterHeatmapScreen`) or cockpit live segments (`SensorGridScreen`),
• *When* inspecting the sheet container,
• *Then* the scaffold SHALL explicitly bind `sheetContainerColor = MaterialTheme.colorScheme.surface` and `sheetTonalElevation = BottomSheetDesign.SheetTonalElevation` (`0.dp`).
• *Given* an active live segment in `SensorGridScreen` or route/segment sheet in `MapScreenWithTrack`,
• *When* viewing the boundary between the header card and the elevation profile container,
• *Then* both regions SHALL render with identical, continuous `MaterialTheme.colorScheme.surface` fill without contrasting grey patches.
• *Given* an athlete triggering a confirmation dialog (`StravaSettingsDialog`, `WorkoutClusterHeatmapScreen`, etc.),
• *When* the `AlertDialog` is displayed,
• *Then* its container SHALL use `MaterialTheme.colorScheme.surface` with `0.dp` tonal elevation.

**Invariants**: Top curvature (20.dp), boundary contour stroke (1.dp outlineVariant at 60% opacity), drop shadow (8.dp), and drag handle pill (32dp x 3dp) MUST NOT be broken; system bars edge-to-edge insets MUST NOT be broken.

### REQ-UI-219: Aftermath/Graphs: Robust Running Pace Decoding, Stopped-Speed Clamping, and mm:ss Y-Axis Pace Formatting [Verified]
- **Tests**: `TST-UI-173`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
• Non-running sports SHALL continue formatting speed via `speedFormatter.format_with_units(point.speedMps)` in `km/h` or `mph`.
2. *TelemetryMetricUtils Value Formatting (`TelemetryMetricUtils.formatValue`)*:
• For `TelemetryMetricType.PACE`, `formatValue` SHALL convert `value` (in minutes per unit) to seconds per meter ($s/m$): `spm = (value * 60.0) / metersPerUnit` (where `metersPerUnit` is 1000.0 for Metric and `BANALService.METER_PER_MILE` for Imperial) and pass $spm$ into `paceFormatter.format_with_units(spm)`. When `value == null`, it SHALL return `"--"`.
3. *Athletic Pace Y-Axis Formatting (`formatPaceMinutes` / `mm:ss`)*:
• `TelemetryMetricUtils` SHALL provide a public helper `formatPaceMinutes(paceMinutes: Double): String` returning `String.format(Locale.US, "%d:%02d", min, sec)`.
• In `TelemetryMetricGraph.kt`, when `metricType == TelemetryMetricType.PACE`, Y-axis maximum label (top / fastest, corresponding to `dataMin`) and minimum label (bottom / slowest, corresponding to `dataMax`) SHALL be rendered via `formatPaceMinutes(...)`. Raw decimal float formatting (`String.format(Locale.US, "%.1f", ...)`) SHALL NOT be used for Pace.
4. *Dynamic Range Bounding & Stopped Filtering (`TelemetryMetricGraph.kt`)*:
• In `extractMetricValue` for `PACE`, speeds below $0.55 \text{ m/s}$ SHALL return `null` (treated as stopped/paused), and pace values SHALL be bounded within realistic athletic limits: coerced to `[1.5, 20.0]`.
• In `dataMin` and `dataMax` calculation for `PACE`, `dataMin` and `dataMax` SHALL be derived from active running points with a minimum spread of at least 1.0 min/km: `dataMin = (min * 0.95).coerceAtLeast(1.5)`, `dataMax = (max * 1.05).coerceAtMost(20.0)`, `dataMax = max(dataMax, dataMin + 1.0)`.
5. *Preservation of Core Invariants*:
• Pace inversion logic in `TelemetryMetricGraph` (faster pace near top, slower pace near bottom) remains strictly preserved.
• Synchronized multi-chart scrubbing via `selectedDistance` and global horizontal zoom alignment remain 100% intact.
• Non-running sports (cycling, hiking) remain unaffected and continue using `SpeedFormatter`.
• 100% 9-language localization parity is maintained.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement (`REQ-UI-219`), extending and refining `REQ-UI-206` under Epic `ATT-111` (*Compact Post-Workout Visual Analytics & Graphs*).
2. *Historical Origin & Commit Trace*: Commit `061a4b35` (`ATT-1740`) introduced `TelemetryMetricGraph.kt` and initial pace conversion; ticket `ATT-1391` introduced multi-metric scrubbing.
3. *Root Reason for Existing Formulation*: `ElevationProfile.kt` passed speed (m/s) to `PaceFormatter` (which expects s/m), producing 69:27 min/km for a 15 km/h runner. Decimal labels (1.0, 33.0) and unconstrained pause samples distorted the chart.
4. *Preservation of Core Invariants*: `PaceFormatter` s/m contract, metric and imperial unit parity, synchronized scrubbing, and localization parity are 100% strictly preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an athlete inspecting a running workout in `ElevationProfile`,
• *When* scrubbing at a point with speed 4.167 m/s (15 km/h),
• *Then* the scrubbing badge SHALL display `"4:00 min/km"` (Metric) or `"6:26 min/mile"` (Imperial), and SHALL NOT display `"69:27 min/km"`.
• *Given* an athlete scrubbing at a stopped point ($speedMps < 0.55\text{ m/s}$ or null),
• *When* rendered,
• *Then* the pace text SHALL render as `"-- min/km"` (or `"-- min/mile"`).
• *Given* `TelemetryMetricGraph` rendering a running workout (`metricType == PACE`),
• *When* inspecting Y-axis labels,
• *Then* top and bottom labels SHALL be formatted in athletic `mm:ss` format (e.g. `"4:00"`, `"6:00"`), and SHALL NOT display raw decimal numbers (`"1.0"`, `"33.0"`).
• *Given* a running workout with pause/stop points,
• *When* rendered in `TelemetryMetricGraph`,
• *Then* pause points SHALL NOT expand `dataMax` to 33.0, and the running curve SHALL utilize the vertical chart height effectively.

**Invariants**: Inverted pace plotting (faster higher), synchronized multi-chart scrubbing, SpeedFormatter usage for cycling/hiking, and 9-language localization parity MUST NOT be broken.
- **Rationale**: The system SHALL enforce robust running pace decoding, stopped-speed clamping, and athletic mm:ss Y-axis label formatting across all Aftermath telemetry graphs and scrubbing cards (`ElevationProfile.kt`, `TelemetryMetricGraph.kt`) (ATT-1818):
1. *ElevationProfile Scrubbing Pace Decoding & Stopped Clamping*:
• In `ElevationProfile.kt`, when `bSportType == BSportType.RUN`, the multi-metric scrubbing badge SHALL convert speed to seconds per meter ($s/m = 1.0 / point.speedMps$) and pass $s/m$ into `paceFormatter.format_with_units(1.0 / point.speedMps)`.
• When `point.speedMps == null

### REQ-UI-220: Aftermath/Graphs: Adaptive X-Axis Milestone Decimation, Collision Prevention, and Standardized Boundary Labeling [Verified]
- **Tests**: `TST-UI-174`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `TelemetryMetricGraph.kt`, `ElevationProfileZoomMath.kt`
- **Rationale**: The system SHALL enforce adaptive X-axis milestone decimation, collision prevention, and boundary label parity across all Aftermath continuous telemetry graphs (`TelemetryMetricGraph.kt`, `ElevationProfileZoomMath.kt`) (ATT-1819):
1. *Adaptive 2 km Step Progression (`ElevationProfileZoomMath.kt`)*:
• In `ElevationProfileZoomMath.calculateAdaptiveDistanceStep`, when `unit == MyUnits.METRIC`, the system SHALL provide an intermediate step for workouts with visible distance between 10 km and 20 km: `visibleDist > 10_000 -> 2_000f`. Workouts in `(10_000, 20_000]` meters SHALL generate 2,000m tick intervals rather than 1,000m intervals, reducing tick count to ~5–9 milestones.
2. *Standardized Milestone Label Formatting (`TelemetryMetricGraph.kt`)*:
• In `TelemetryMetricGraph.kt`, intermediate milestone labels SHALL NOT repeat `" km"` or `" mi"` on every tick: (a) Metric: when `visibleSpan < 1500`, format as `"${currentDist.toInt()}m"`; when decimal (`currentDist % 1000.0 != 0.0`), format as `"%.1f"`; else format as `"${(currentDist / 1000.0).toInt()}"` (e.g. `"2"`, `"4"`, `"6"`); (b) Imperial: when decimal, format as `"%.1f"`; else format as `"${miles.toInt()}"` (e.g. `"2"`, `"4"`, `"6"`).
3. *Start and End Boundary Labels Parity (`TelemetryMetricGraph.kt`)*:
• `TelemetryMetricGraph` SHALL instantiate `val distanceFormatter = remember(unit) { DistanceFormatter() }`.
• An explicit `endLabel` with units SHALL be rendered at `startPaddingPx + chartWidthPx - endLabelWidth`: `distanceFormatter.format_with_units(startDist + visibleSpan)` (e.g. `"17,82 km"`) in Distance domain, or `ElevationProfileZoomMath.formatTimeTick((startDist + visibleSpan).toLong())` in Time domain.
• When zoomed ($Z > 1.01$), an explicit `startLabel` with units SHALL be rendered at `startPaddingPx`: `distanceFormatter.format_with_units(startDist)` or `formatTimeTick(startDist.toLong())`.
4. *Dynamic Minimum Pixel Clearance & Decimation Algorithm (`TelemetryMetricGraph.kt`)*:
• Intermediate milestone labels SHALL NOT render within 40 dp of the start padding (`tickX <= startPaddingPx + 40.dp.toPx()`) or within `endLabelWidth + 16.dp.toPx()` of the right chart edge (`startPaddingPx + chartWidthPx - tickX <= endLabelWidth + 16.dp.toPx()`).
• The renderer SHALL track `lastDrawnRightX`. An intermediate label SHALL only render if its left edge maintains at least 12 dp clearance: `labelLeft >= lastDrawnRightX + 12.dp.toPx()`, updating `lastDrawnRightX = labelLeft + labelWidth`. Colliding milestone labels SHALL be decimated (skipped) while underlying vertical grid lines continue to be rendered.
5. *Center-Aligned Milestone Positioning (`TelemetryMetricGraph.kt`)*:
• Milestone labels SHALL be centered under the tick mark: `nativeCanvas.drawText(label, tickX - (labelWidth / 2f), size.height - 4.dp.toPx(), axisTextPaint)`.
6. *Preservation of Core Invariants*:
• Horizontal padding geometry (50 dp start, 25 dp end), synchronized scrubbing cursor positioning, touch scrubbing coordinate mapping (`canvasXToDistance`), and 9-language localization parity remain 100% strictly preserved.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement (`REQ-UI-220`), extending and refining `REQ-UI-206` under Epic `ATT-111` (*Compact Post-Workout Visual Analytics & Graphs*).
2. *Historical Origin & Commit Trace*: Commit `061a4b35` (`ATT-1740`) introduced `TelemetryMetricGraph.kt` with an unconstrained X-axis loop; `ATT-1814` synchronized horizontal zoom.
3. *Root Reason for Existing Formulation*: Secondary continuous graphs lacked collision avoidance, drew redundant `" km"` units on every tick, and omitted boundary labels, causing solid text collisions on workouts $\ge 10\text{ km}$.
4. *Preservation of Core Invariants*: Shared horizontal paddings, synchronized scrubbing, and invertibility of `ElevationProfileZoomMath` remain 100% strictly preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* a workout with total distance 17.82 km,
• *When* `calculateAdaptiveDistanceStep` is evaluated in Metric mode,
• *Then* it SHALL return `2_000f` (2 km tick step).
• *Given* `TelemetryMetricGraph` rendering continuous telemetry curves for a 17.82 km workout,
• *When* inspecting intermediate milestone labels along the bottom axis,
• *Then* labels SHALL render as `"2"`, `"4"`, `"6"`, etc., without repeating `" km"`, and an explicit end label (`"17,82 km"`) SHALL be anchored at the right chart boundary.
• *Given* any combination of workout length, zoom level, or screen width,
• *When* X-axis milestone labels are rendered,
• *Then* zero labels SHALL collide, overlap, or obscure boundary labels.

**Invariants**: Shared 50.dp start / 25.dp end padding geometry, synchronized touch scrubbing, and full unit test regression suite pass MUST NOT be broken.

### REQ-UI-221: UI/Sheets: Standardized Bottom Sheet Peek Height Baselines and Information Footprint Framing [Verified]
- **Tests**: `TST-UI-175`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `BottomSheetDesign.kt`, `MapDetailLayout.kt`, `MapScreenWithTrack.kt`, `SegmentOnMapScreen.kt`, `RouteOnMapScreen.kt`, `SensorGridScreen.kt`, `WorkoutClusterHeatmapScreen.kt`, `PeriodMapScreen.kt`
- **Rationale**: The system SHALL standardize, centralize, and calibrate all persistent bottom sheet and map peek baselines across the application, ensuring essential header content is fully visible above system navigation bars without vertical clipping or awkward container overshoots (ATT-1645):
1. *Centralized Design Token Definition (`BottomSheetDesign.kt`)*:
• `BottomSheetDesign` SHALL define canonical, standardized Dp baseline tokens:
- `val PeekHeightWorkout: Dp = 140.dp`: Calibrated baseline for single workout detail peeks in analytical map screens (`WorkoutClusterHeatmapScreen`, `PeriodMapScreen`), cleanly framing `WorkoutHeader` (Sport Icon, Title, Cluster button, Sport Type/Equipment, Date/Time).
- `val PeekHeightRoute: Dp = 112.dp`: Calibrated baseline for route detail peeks without description (`MapScreenWithTrack.kt` / `RouteOnMapScreen.kt`), cleanly framing `RouteSummaryHeader` (Sport Icon, Route Name, Source, Distance, Ascent, and Visibility Switch).
- `val PeekHeightRouteWithDescription: Dp = 152.dp`: Calibrated baseline for route detail peeks with an active description string, ensuring complete visibility of description text above navigation bars.
- `val PeekHeightSegment: Dp = 192.dp`: Calibrated baseline for segment detail peeks (`MapScreenWithTrack.kt` / `SegmentOnMapScreen.kt`), cleanly framing `SegmentHeader` and the full 3-row `SegmentDetails` card (including the third altitude gain and extrema row) above navigation bars.
- `val PeekHeightKnownLocation: Dp = 108.dp`: Calibrated baseline for favorite start location (*Lieblingsorte*) peeks (`MapScreenWithTrack.kt` / `KnownLocationOnMapSheet`), cleanly framing Name, Edit button, Altitude, and Starts count.
- `val PeekHeightLiveSegment: Dp = 126.dp`: Calibrated baseline for active live segment tracking peek (`SensorGridScreen.kt`), cleanly framing live progress, status, and delta metrics without prematurely revealing the underlying elevation profile graph.
2. *System Navigation Bar Inset Factoring*:
• Every persistent sheet peek height SHALL strictly incorporate the bottom system navigation bar insets: `sheetPeekHeight = BottomSheetDesign.PeekHeight* + navBarHeight` where `navBarHeight = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()`. When collapsed or unselected, peek height SHALL be `0.dp`.
• For routes, `MapScreenWithTrack.kt` SHALL dynamically evaluate `routeSummary?.description.isNullOrEmpty()` to select `PeekHeightRoute` or `PeekHeightRouteWithDescription`.
3. *Screen-Level Token Adoption & Drag Affordance*:
• `MapScreenWithTrack.kt` SHALL consume `PeekHeightSegment`, `PeekHeightRoute`, `PeekHeightRouteWithDescription`, and `PeekHeightKnownLocation`.
• `SensorGridScreen.kt` SHALL consume `PeekHeightLiveSegment`.
• `WorkoutClusterHeatmapScreen.kt` and `PeriodMapScreen.kt` SHALL consume `PeekHeightWorkout`.
• In `WorkoutClusterHeatmapScreen.kt`, `BottomSheetScaffold` SHALL display a visual drag handle via `sheetDragHandle = if (peekedWorkoutDataWithTrack != null && !isEditingFingerprint) { { MinimumDragHandle() } } else null`, providing a subtle drag affordance (`MinimumDragHandle`, `REQ-UI-196`) when a workout peek is active, and suppressing it when no workout is peeked or while editing the cluster fingerprint (ATT-5564).
• Zero hardcoded raw peek height literals SHALL remain in screen composables.
4. *Dynamic Self-Measuring Header Peek Architecture (`MapDetailLayout.kt` / `MapScreenWithTrack.kt`)*:
• In `MapDetailLayout.kt`, when `useStatusBarsPadding == false`, the header container (`MinimumDragHandle() + Surface(header)`) SHALL measure its rendered layout height via `Modifier.onGloballyPositioned` and convert to Dp via `LocalDensity.current`.
• The measured height SHALL be reported via callback `onHeaderHeightMeasured: ((Dp) -> Unit)? = null`, forwarded through `SegmentOnMapScreen.kt` and `RouteOnMapScreen.kt`.
• In `MapScreenWithTrack.kt`, `sheetPeekHeight` SHALL dynamically evaluate `(measuredHeight ?: fallbackBaseline) + navBarHeight`, ensuring that all header elements (including the third altitude row of `SegmentDetails` and multiline route descriptions) sit completely above the system navigation bar regardless of device font scaling or localized title wrapping.
5. *Preservation of Core Invariants*:
• Maximum expanded height constraints (`maxSheetHeight = maxHeight - statusBarHeight`) and status bar boundary protections (`REQ-SET-069`) remain strictly preserved.
• Minimum drag handle pill dimensions (`MinimumDragHandle`, 32dp x 3dp, 15dp total vertical footprint) remain strictly preserved.
• Defensive fallback to `BottomSheetDesign` constants is preserved when dynamic measurement is pending or unavailable.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Amends `REQ-UI-221` (*Standardized Bottom Sheet Peek Height Baselines*) targeting `WorkoutClusterHeatmapScreen.kt` and `BottomSheetVisualContractTest.kt`, referencing `REQ-UI-196` (*Subtle Drag Handle Affordance & Unified Sheet Styling*).
2. *Historical Origin & Commit Trace*: Sprint `2026-40.8` (Commit `9c3dd09e`, ATT-1645), Sprint `2026-40.11` (Dynamic Self-Measuring Peeks), and Sprint Review `2026-45.7` (ATT-5485 / ATT-5564).
3. *Root Reason for Existing Formulation*: Static baseline tokens failed under system font scaling; `sheetDragHandle = null` was originally set to keep the top of `TrackOnMapScreen` flush, overlooking that athletes need a visual drag affordance to discover expandable workout details.
4. *Preservation of Core Invariants*: Status bar boundary constraints (`REQ-SET-069`), navigation bar padding additions, fallback tokens, `MinimumDragHandle` design dimensions, and fingerprint editing state transitions remain 100% strictly preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an athlete peeking a workout in `WorkoutClusterHeatmapScreen` or `PeriodMapScreen`,
• *When* the bottom sheet is in its initial collapsed peek state,
• *Then* the entire `WorkoutHeader`—including sport icon, workout title, cluster badge, sport name, equipment, and start date/time—SHALL be 100% visible above the system navigation bar without vertical clipping.
• *Given* an athlete peeking a workout on `WorkoutClusterHeatmapScreen`,
• *When* the workout bottom sheet peeks or expands,
• *Then* a visual drag handle (`MinimumDragHandle()`) SHALL be displayed at the top of the sheet.
• *Given* an athlete on `WorkoutClusterHeatmapScreen` without a peeked workout or while in fingerprint editing mode (`isEditingFingerprint == true`),
• *When* the bottom sheet is hidden,
• *Then* `sheetDragHandle` SHALL be `null`.
• *Given* an athlete selecting a route with description on `MapScreenWithTrack`,
• *When* the route bottom sheet peeks,
• *Then* the description text row SHALL be 100% visible above the navigation bar.
• *Given* an athlete selecting a segment on `MapScreenWithTrack`,
• *When* the segment bottom sheet peeks,
• *Then* the 3-row `SegmentDetails` card (including the third altitude gain and extrema row) SHALL be fully visible above the navigation bar regardless of font scaling.
• *Given* an active live segment in `SensorGridScreen`,
• *When* the live segment bottom sheet peeks,
• *Then* live progress metrics SHALL sit cleanly framed and the underlying elevation profile graph SHALL remain hidden below the fold.
• *Given* static code inspection across `MapScreenWithTrack.kt`, `SensorGridScreen.kt`, `WorkoutClusterHeatmapScreen.kt`, and `PeriodMapScreen.kt`,
• *When* evaluating sheet peek height assignments,
• *Then* all screens SHALL reference `BottomSheetDesign.PeekHeight*` tokens with dynamic self-measurement where applicable.

**Invariants**: Status bar boundary constraints (`REQ-SET-069`), MinimumDragHandle dimensions, and system navigation bar padding additions MUST NOT be broken.

### REQ-UI-222: Settings/UI: Modular, Collapsible Subsection Accordion Architecture and Advisory Notice for Advanced Settings [Verified]
- **Tests**: `TST-UI-176`, `TST-UI-187`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `AdvancedTuningDialog.kt`
- **Rationale**: The system SHALL structure the Advanced Settings dialog (`AdvancedTuningDialog.kt`) into a modular, collapsible subsection architecture featuring Material 3 accordion cards with live active-value summary subtitles and animated expand/collapse mechanics (ATT-1821, ATT-1957):
1. *Canonical Subsection Grouping*:
• The 25+ parameters of `AdvancedTuningDialog.kt` SHALL be organized into 5 distinct, semantic accordion subsections:
- Section 1 (`CockpitTypographySection` / `tuning_cat_cockpit_typography`): Cockpit Typography (Font family selector, Boldness segmented chips, and live 3-metric preview HUD card).
- Section 2 (`AmoledBatterySaverSection` / `tuning_cat_battery_saver`): AMOLED Battery Saver & Brightness (Full dimming brightness, Medium dimming brightness, Flat slope threshold, Steep slope threshold, Wake-up duration, Downward damping delay sliders).
- Section 3 (`SensorsGpsFilterSection` / `tuning_cat_sensors_gps`): Sensors, GPS & Filtering (GPS horizontal accuracy threshold, Altitude smoothing window, Slope minimum speed sliders).
- Section 4 (`AftermathAnalysisSection` / `tuning_cat_aftermath`): Aftermath & Profile Analytics (Profile X-Axis domain selector: Distance vs Time).
- Section 5 (`WorkoutMasksAndCardsSection` / `tuning_cat_workout_masks_cards`): Workout Cards & Field Masks (8 Workout List card section toggles, 6 Edit Workout field toggles).
2. *Accordion Container Specifications (`TuningAccordionSection`)*:
• Each subsection SHALL be encapsulated within a Material 3 Card container (`RoundedCornerShape(12.dp)`, `containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)`, `border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))`, zero tonal elevation `0.dp` per `REQ-UI-218`).
• The Header Row SHALL be clickable across its entire bounds to toggle expansion state.
• The Header Row SHALL render:
- Category leading icon (`CockpitTypography`: `TextFields`; `AmoledBatterySaver`: `BrightnessMedium`; `SensorsGpsFilter`: `LocationOn`; `AftermathAnalysis`: `ShowChart`; `WorkoutMasksAndCards`: `ViewList`).
- Title in `MaterialTheme.typography.titleMedium` (`FontWeight.SemiBold`).
- Live active-value summary subtitle in `MaterialTheme.typography.bodySmall` (`color = MaterialTheme.colorScheme.onSurfaceVariant`).
- Trailing expandable chevron icon (`Icons.Default.ExpandMore`) with smooth animated rotation (`animateFloatAsState` from 0° collapsed to 180° expanded).
• Subsection contents SHALL expand and collapse via `AnimatedVisibility(visible = isExpanded, enter = expandVertically(), exit = shrinkVertically())`.
• Upon initial display of `AdvancedTuningDialog`, all 5 accordion subsections SHALL be initially collapsed (`expandedSections = emptySet<String>()`), presenting a compact, scannable overview of all 5 category headers and live summary subtitles on first launch without requiring vertical scrolling past the Cockpit Typography section (ATT-1957).
3. *Live Active-Value Subtitle Updating*:
• Each subsection header subtitle SHALL reactively update upon any parameter modification without requiring dialog save:
- Cockpit: font family display name and boldness display name.
- Battery Saver: full dim %, medium dim %, flat slope %, steep slope %.
- Sensors & GPS: GPS threshold (m), altitude window (s), slope minimum speed (m/s).
- Aftermath: Profile X-axis domain (Distance or Time).
- Workout Cards & Masks: active card section count out of 8, active edit field count out of 6.
4. *Extensible & Decoupled Architecture*:
• Individual subsection composables SHALL be declared as separate, focused composable functions (`CockpitTypographySection`, `AmoledBatterySaverSection`, `SensorsGpsFilterSection`, `AftermathAnalysisSection`, `WorkoutMasksAndCardsSection`) with typed parameters or state callbacks, decoupled from the outer bottom sheet layout.
5. *Preservation of Core Invariants*:
• Top advisory notice (`TuningWarningNotice`) SHALL remain visible at the top of the dialog and screen.
• Factory reset action (`R.string.reset_to_defaults`) SHALL atomically restore all 25+ parameters to defaults and immediately update all 5 subtitle summaries.
• `AppBottomSheetContent` header title, icon (`Icons.Default.Tune`), and Save/Cancel footer actions remain 100% strictly preserved.
• DataStore persistence keys, default values, and downstream reactive consumers remain 100% unchanged.
• 100% 9-language localization parity across all 9 supported locales.
6. *Friendly Advisory Notice Formulation & Component Modernization (`REQ-UI-222.6`, `ATT-5819`)*:
• In `TuningFormControls.kt`, `TuningWarningNotice` SHALL be presented as a constructive advisory notice utilizing an informative container (`surfaceVariant` or `secondaryContainer` styling with `Icons.Default.Tune` or `Icons.Default.Info`), eliminating alarmist error-red styling (`errorContainer` and `Icons.Default.Warning`).
• The advisory description (`R.string.advanced_tuning_warning_desc`) SHALL matter-of-factly explain that advanced parameters for layouts, models, and sensors are configured here, and that factory defaults can be restored at any time via the reset action.
• 100% 9-language translation parity SHALL be maintained across EN, DE, ES, FR, IT, JA, NL, PL, PT with zero inline emojis and valid entity formatting.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: `REQ-UI-222`, refining and restructuring the UI presentation layer of `REQ-SET-073`, `REQ-UI-201`, `REQ-UI-212`, and `REQ-UI-216` under Epic `ATT-355` (*Good and consistent UI*). Amends `TuningWarningNotice` under `ATT-5819`.
2. *Historical Origin & Commit Trace*: Sprint 2026-40.8 (Commit `e2303aa7`, ATT-1821 / ATT-1871), Sprint 2026-40.9 (`ATT-1957`), and Sprint 2026-45.12 (`ATT-5819`).
3. *Root Reason for Existing Formulation*: Defensive error-red warning styling and alarming copy focusing strictly on tracking heuristics and sensor corruption were established when Expert Settings only housed experimental GPS and battery dimming options. With the addition of workout cards, cockpit typography, and training models, the alarmist tone became counter-productive and intimidating.
4. *Preservation of Core Invariants*: Top advisory notice remains permanently visible at the top of Expert Settings; factory reset reassurance and reset button functionality strictly preserved; 100% 9-language parity maintained; DataStore schemas and accordion mechanics strictly unchanged.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an athlete opening Advanced Settings (`AdvancedTuningDialog`),
• *When* the dialog renders initially,
• *Then* all 5 accordion cards SHALL be in collapsed state, displaying their category icons, titles, and live active-value summary subtitles without expanded control bodies.
• *Given* any accordion section in collapsed state,
• *When* the athlete taps the card header,
• *Then* the section SHALL smoothly expand via `AnimatedVisibility`, revealing its dedicated controls.
• *Given* any setting adjusted within an expanded section,
• *When* the parameter value changes,
• *Then* the corresponding section header subtitle SHALL immediately update to reflect the new active value.
• *Given* settings modified across multiple subsections,
• *When* tapping \"Reset to Factory Defaults\",
• *Then* all parameters SHALL revert to factory defaults atomically, and all section subtitle summaries SHALL immediately update to show default configurations.

**Invariants**: Parameter ranges, DataStore keys, warning card, factory reset, zero tonal elevation, and 9-language localization parity MUST NOT be broken.

### REQ-UI-223: Aftermath/Map: Interactive Draggable Splitter and Dynamic Viewport Resizing in MapDetailLayout [Verified]
- **Tests**: `TST-UI-177`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `SplitPaneDivider.kt`, `MapDetailLayout.kt`
- **Rationale**: The system SHALL provide an interactive draggable splitter bar to dynamically resize map and telemetry viewports on detailed inspection screens (`MapDetailLayout.kt` / `TrackOnMapScreen.kt`) (ATT-1890):
1. *Reusable SplitPaneDivider Component (`SplitPaneDivider.kt`)*:
• The system SHALL implement a reusable composable `SplitPaneDivider(onDelta: (Float) -> Unit, onReset: () -> Unit, modifier: Modifier = Modifier)` in `com.atrainingtracker.trainingtracker.ui.components.core`.
• The divider SHALL provide a 24 dp vertical touch target (`DIVIDER_TOUCH_HEIGHT = 24.dp`) with a centered pill grip affordance (`GRIP_WIDTH = 32.dp`, `GRIP_HEIGHT = 4.dp`, `RoundedCornerShape(2.dp)`).
• The divider SHALL handle vertical drag gestures via `draggable(orientation = Orientation.Vertical)` reporting $\Delta y$ pixel deltas.
• The divider SHALL handle double-tap gestures via `pointerInput { detectTapGestures(onDoubleTap = { onReset() }) }` resetting to the default balanced ratio.
• The divider SHALL provide an accessibility semantics content description (`map_splitter_content_description`) with 100% 9-language localization parity.
2. *Mathematical Clamping & Boundary Safety (`SplitPaneMath`)*:
• The system SHALL encapsulate split calculations in pure helper `SplitPaneMath`: (a) `DEFAULT_SPLIT_FRACTION = 0.50f`; (b) `MIN_MAP_HEIGHT = 120.dp`; (c) `MIN_LOWER_HEIGHT = 160.dp`; (d) `updateFraction` clamping `splitFraction` strictly within `[minFraction, maxFraction]`, preventing either viewport from collapsing or producing graphical glitches.
3. *MapDetailLayout Dynamic Viewport Integration*:
• In `MapDetailLayout.kt`, when `showMap == true` and `hasScrollableContent == true`, the layout SHALL manage `var splitFraction by rememberSaveable { mutableFloatStateOf(SplitPaneMath.DEFAULT_SPLIT_FRACTION) }`.
• The Map Box SHALL receive `Modifier.weight(splitFraction).heightIn(min = SplitPaneMath.MIN_MAP_HEIGHT)`.
• `SplitPaneDivider` SHALL be placed directly between the Map Box and the lower scroll container.
• The lower container SHALL receive `Modifier.weight(1f - splitFraction).verticalScroll(rememberScrollState())`.
• Double-tapping the divider SHALL reset `splitFraction` to `0.50f`.
4. *Preservation of Core Invariants*:
• When `hasScrollableContent == false` (Routes & Segments), Map Box SHALL retain full `Modifier.weight(1f)` and lower section SHALL wrap its content (`wrapContentHeight()`), with zero divider displayed.
• When `showMap == false` (LiveSegmentSheet), root Column retains compact `wrapContentHeight()`.
• Map pan/zoom/compass gestures, multi-chart synchronized scrubbing, and snapshot sharing integrity remain 100% strictly preserved.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement (`REQ-UI-223`), refining and extending `REQ-UI-213` under Epic `ATT-111` (*Compact Post-Workout Visual Analytics & Graphs*).
2. *Historical Origin & Commit Trace*: Sprint 2026-40.7 (`ATT-1812`), commit `4e6224aa`.
3. *Root Reason for Existing Formulation*: `REQ-UI-213` applied a static 240 dp floor and static weights (`1f` vs `1.2f`) to prevent map collapse when continuous graphs were introduced without a splitter.
4. *Preservation of Core Invariants*: Minimum height clamping (`minMapHeight = 120.dp`, `minLowerHeight = 160.dp`) strictly protects against 0dp collapse while granting athletes full interactive control to expand either the map or graphs.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an athlete viewing a completed workout on `MapDetailLayout`,
• *When* dragging the horizontal splitter bar downwards,
• *Then* the map viewport SHALL smoothly expand and the lower graph container SHALL shrink in real time.
• *Given* an athlete dragging the splitter bar upwards towards the top,
• *When* reaching the upper limit,
• *Then* the map SHALL clamp gracefully at 120 dp, giving maximum screen height to the scrollable graphs below.
• *Given* an altered map viewport ratio,
• *When* the athlete double-taps the splitter bar,
• *Then* the layout SHALL smoothly reset to the default balanced 50/50 split.
• *Given* non-detailed screens where `hasScrollableContent == false` (e.g. routes or segments),
• *When* rendered,
• *Then* the splitter bar SHALL NOT be displayed and the map SHALL retain full available height.

**Invariants**: Google Maps gesture isolation, synchronized multi-chart scrubbing cursor parity, snapshot sharing integrity, and 9-language localization parity MUST NOT be broken.

### REQ-UI-224: Lieblingsorte: Calibrated Zoom Level and Dynamic Geofence Framing on KnownLocationCard Map Thumbnail [Verified]
- **Tests**: `TST-UI-178`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `KnownLocationZoomMath.kt`, `KnownLocationsScreen.kt`
- **Rationale**: The system SHALL calibrate the camera zoom level on `KnownLocationCard` map preview thumbnails (`KnownLocationThumbnailMap` in `KnownLocationsScreen.kt`), ensuring the circular geofence boundary is fully contained without edge clipping and rich regional neighborhood context is displayed (ATT-1878):
1. *Mathematical Zoom Calibration (`KnownLocationZoomMath.kt`)*:
• The system SHALL encapsulate thumbnail zoom calculations in a pure helper object `KnownLocationZoomMath` in `com.atrainingtracker.trainingtracker.ui.knownlocations`:
- `DEFAULT_THUMBNAIL_ZOOM = 12.0f`: Standard baseline zoom level for 80dp thumbnail previews.
- `MIN_ZOOM = 10.5f`: Zoom-out lower bound protecting large geofences (up to 1000m) from over-zooming.
- `MAX_ZOOM = 12.5f`: Zoom-in upper bound guaranteeing at least 1.45 km of surrounding neighborhood/district context even for small geofences (50m–200m).
- `TARGET_GEOFENCE_DIAMETER_RATIO = 0.35f`: Target framing ratio where the circular geofence diameter occupies approximately 35% of the 80dp container width.
- `fun calculateThumbnailZoom(radiusMeters: Int, latitude: Double = 0.0): Float`: Computes Web Mercator resolution based on latitude and radius, scaling zoom so that the geofence occupies the target ratio, clamped strictly within `[MIN_ZOOM, MAX_ZOOM]`.
2. *Thumbnail Camera Integration (`KnownLocationsScreen.kt`)*:
• In `KnownLocationThumbnailMap`, the camera zoom SHALL be dynamically hoisted and calculated:
`val targetZoom = remember(item.radius, item.latLng.latitude) { KnownLocationZoomMath.calculateThumbnailZoom(item.radius, item.latLng.latitude) }`
• The camera position state and map movement SHALL consume `targetZoom`:
`CameraPosition.fromLatLngZoom(item.latLng, targetZoom)` and `cameraPositionState.move(CameraUpdateFactory.newLatLngZoom(item.latLng, targetZoom))`.
• The hardcoded literal `14.5f` SHALL be completely eliminated from `KnownLocationsScreen.kt`.
3. *Preservation of Core Invariants*:
• Google Maps lite mode (`liteMode(true)`), gesture isolation (all controls and gestures disabled), 80dp square bounds, 12dp rounded corners, dark/light theme styling, anti-flash overlay, heart pin marker, offline preview fallback (`LocalInspectionMode.current`), and card click navigation (`onShowOnMap()`, `onEdit()`, long-press delete) remain 100% strictly preserved.
• Zero impact on 9-language localization parity (`REQ-UI-106`).

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement (`REQ-UI-224`), refining and calibrating `REQ-UI-217` under Epic `ATT-355` (*Good and consistent UI*).
2. *Historical Origin & Commit Trace*: Sprint 2026-40.7 (`ATT-1816`), commit `3c16260a`.
3. *Root Reason for Existing Formulation*: `REQ-UI-217` hardcoded `14.5f` as an uncalibrated default borrowed from full-screen maps, causing geofence boundary clipping on 200m–300m radii and microscopic ~360m viewport spans.
4. *Preservation of Core Invariants*: Lite mode, offline preview fallback, heart pin marker, and universal card interactions remain 100% strictly preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an athlete viewing saved favorite locations on `KnownLocationsScreen`,
• *When* viewing the 80dp map preview thumbnail on any `KnownLocationCard` with a typical geofence (radius 50m to 200m),
• *Then* the camera zoom level SHALL be calibrated to `12.5f`, displaying at least 1.4 km of surrounding neighborhood terrain without clipping the geofence boundary.
• *Given* a location with a large geofence (e.g. radius 500m or 1000m),
• *When* the map thumbnail renders,
• *Then* the camera zoom level SHALL dynamically adjust down to `11.5f`–`10.5f`, ensuring the entire geofence circle remains cleanly contained within the 80dp container.
• *Given* `LocalInspectionMode.current` active in Compose Previews or unit tests,
• *When* `KnownLocationThumbnailMap` renders,
• *Then* the offline schematic canvas circle and pin SHALL continue to render without calling Google Play Services.

**Invariants**: Lite mode, gesture isolation, offline preview fallback, heart pin marker, and universal card interactions MUST NOT be broken.

### REQ-UI-225: Aftermath/Graphs: Persistent Sticky Global Zoom Toolbar Between Map and Telemetry Graphs [Verified]
- **Tests**: `TST-UI-179`, `TST-UI-398`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `GlobalTelemetryZoomToolbar.kt`, `MapDetailLayout.kt`, `ElevationProfile.kt`
- **Rationale**: The system SHALL decouple horizontal zoom controls from child graph components and provide a dedicated, persistent sticky zoom toolbar positioned directly between the map viewport and the scrollable telemetry graph container on detailed workout inspection screens (`MapDetailLayout.kt` / `TrackOnMapScreen.kt`) (ATT-1876):
1. *Reusable GlobalTelemetryZoomToolbar Component (`GlobalTelemetryZoomToolbar.kt`)*:
• The system SHALL implement a reusable composable `GlobalTelemetryZoomToolbar` in `com.atrainingtracker.trainingtracker.ui.components.core` accepting `zoomScale: Float`, `startDist: Double`, `totalSpan: Double`, `onZoomChanged: (zoomScale: Float, startDist: Double) -> Unit`, `isPanMode: Boolean = false`, and `onPanModeToggle: (() -> Unit)? = null`.
• The toolbar SHALL render as a compact horizontal strip with standardized height `36.dp` (`TOOLBAR_HEIGHT = 36.dp`) and surface container styling (`MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)` or clean surface with outline divider).
• The toolbar SHALL provide accessible zoom controls:
- Zoom Out (`-`): Scales down by factor `1.5f` anchored at center via `ElevationProfileZoomMath.applyZoomAtCentroid`; disabled when `zoomScale <= 1.01f`.
- Zoom In (`+`): Scales up by factor `1.5f` anchored at center via `ElevationProfileZoomMath.applyZoomAtCentroid`; disabled when `zoomScale >= 10.0f - 0.01f`.
- Pan/Scrub Toggle Button: Toggles `isPanMode` between continuous route scrubbing and horizontal pan navigation.
- Current Zoom & Reset Pill: Displayed when `zoomScale > 1.01f`, rendering current magnification (e.g. `2.5x`) and reset icon (`Icons.Default.RestartAlt`); single-tap restores full `1.0x` extent (`zoomScale = 1.0f, startDist = 0.0`).
• All toolbar controls SHALL include accessible content descriptions with 100% 9-language localization parity (`zoom_in`, `zoom_out`, `zoom_reset`, `zoom_pan_mode`, `zoom_scrub_mode`).
2. *MapDetailLayout Persistent Sticky Placement*:
• In `MapDetailLayout.kt`, when `showZoomControls == true` and `activeScrubPath != null && activeScrubPath.isNotEmpty()`, `GlobalTelemetryZoomToolbar` SHALL be placed directly below `SplitPaneDivider` and above `lowerColumn(...)`.
• Because the toolbar resides outside `Modifier.verticalScroll(...)`, it SHALL remain stationary and pinned directly below the map/divider at all times, never scrolling offscreen when inspecting lower telemetry graphs or lap splits.
• Dynamic available height calculations (`SplitPaneMath.calculateAvailableHeight`) SHALL account for `TOOLBAR_HEIGHT` alongside `DIVIDER_TOUCH_HEIGHT` when the toolbar is active, ensuring exact viewport fraction calculations.
3. *Decoupling & Vertical Space Recovery in ElevationProfile.kt*:
• The embedded zoom controls row in `ElevationProfile.kt` (lines 718–812) SHALL be completely removed, eliminating redundant duplicate buttons.
• `ElevationProfile` SHALL accept `isPanMode: Boolean = false`.
• Canvas top padding in `ElevationProfile` SHALL be reduced from `72.dp` to `44.dp` when `showZoomControls == true`, reclaiming vertical screen real estate for profile curve visualization while preserving collision clearance for `ScrubbingTelemetryBadge` (`top = 4.dp`).
4. *Preservation of Core Invariants*:
• Direct canvas pinch-to-zoom and pan gestures on `ElevationProfile` and `TelemetryMetricGraph` remain 100% functional per `REQ-UI-192` and `REQ-UI-215`.
• Synchronized scrubbing cursor parity across all stacked graphs, Google Maps gestures, snapshot sharing, and 9-language localization parity MUST NOT be broken.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement (`REQ-UI-225`), refining and decoupling `REQ-UI-192`, `REQ-UI-197`, and `REQ-UI-215` under Epic `ATT-111` (*Compact Post-Workout Visual Analytics & Graphs*).
2. *Historical Origin & Commit Trace*: Sprint 2026-40.4 (`ATT-527`), Sprint 2026-40.5 (`ATT-1647`, `ATT-1736`, `ATT-1737`), and Sprint 2026-40.6 (`ATT-1814`).
3. *Root Reason for Existing Formulation*: Zoom controls were initially embedded within `ElevationProfile` prior to the introduction of multi-metric telemetry graphs, causing controls to scroll out of view when scrolling down.
4. *Preservation of Core Invariants*: Viewport scaling math (`ElevationProfileZoomMath`), synchronized scrubbing, unhoisted `ElevationProfile` backward compatibility, and 9-language localization parity are 100% strictly preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an athlete viewing a workout in `MapDetailLayout`,
• *When* scrolling down the lower section to inspect Speed/Pace, Heart Rate, Power, or lap splits,
• *Then* `GlobalTelemetryZoomToolbar` SHALL remain stationary and pinned directly below the map and divider, never scrolling offscreen.
• *Given* the 'Höhenprofil' section header in the scrollable content,
• *When* rendered,
• *Then* it SHALL no longer contain the embedded zoom buttons, which reside exclusively in the global toolbar.
• *Given* an athlete tapping Zoom In, Zoom Out, or Reset on `GlobalTelemetryZoomToolbar`,
• *When* triggered,
• *Then* all visible stacked graphs (Elevation Profile, Speed/Pace, Heart Rate, Power) SHALL zoom, pan, or reset in lockstep synchronously.
• *Given* an athlete tapping the Pan/Scrub mode toggle on `GlobalTelemetryZoomToolbar`,
• *When* switched to Pan mode,
• *Then* dragging on the elevation profile SHALL pan the visible window instead of moving the scrubbing cursor.

**Invariants**: Google Maps gesture isolation, synchronized multi-chart scrubbing cursor parity, snapshot sharing integrity, and 9-language localization parity MUST NOT be broken.

### REQ-UI-226: Aftermath/Map: Directional Gesture Disambiguation and Smooth Vertical Scrolling in MapDetailLayout [Verified]
- **Tests**: `TST-UI-180`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: \Delta y
- **Rationale**: The system SHALL eliminate gesture trapping across all stacked charts in `MapDetailLayout.kt` (`ElevationProfile.kt` and `TelemetryMetricGraph.kt`), allowing effortless vertical scrolling through analytics while preserving precision horizontal scrubbing and pinch-to-zoom (ATT-1872):
1. *Directional Drag Disambiguation Formulation (`ChartGestureDisambiguator` / pointerInput)*:
• In `ElevationProfile.kt` and `TelemetryMetricGraph.kt`, single-pointer drag gestures SHALL be classified dynamically against the system touch slop (`viewConfiguration.touchSlop`):
- Dominant Vertical Gesture ($

### REQ-UI-227: Cockpit/Typography: Expanded Curated Athletic Sports and Aerospace HUD Font Selection [Verified]
- **Tests**: `TST-UI-181`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `CockpitTypography.kt`, `AdvancedTuningDialog.kt`, `res/font/`
- **Rationale**: The system SHALL expand the curated Cockpit HUD typography palette in Advanced Settings from 5 to 13 options, adding high-performance condensed sports typefaces and aerospace HUD fonts while ensuring defensive offline resolution, safe DataStore persistence, and 9-language localization parity (ATT-1871):
1. *Curated Athletic & HUD Font Additions*:
• In `CockpitTypography.kt`, `CockpitFontFamily` enum SHALL be expanded with 8 new typeface constants:
- Athletic Condensed / High Impact: `BEBAS_NEUE` (`R.font.bebas_neue`), `TEKO` (`R.font.teko`), `BARLOW_CONDENSED` (`R.font.barlow_condensed`), `OSWALD` (`R.font.oswald`).
- Technical / Aerospace HUD: `CHAKRA_PETCH` (`R.font.chakra_petch`), `OXANIUM` (`R.font.oxanium`), `RAJDHANI` (`R.font.rajdhani`).
- Modern Geometric: `MONTSERRAT` (`R.font.montserrat`).
2. *Google Fonts Downloadable Font Descriptors*:
• The system SHALL define XML font descriptors in `app/src/main/res/font/`: `bebas_neue.xml`, `teko.xml`, `barlow_condensed.xml`, `oswald.xml`, `chakra_petch.xml`, `oxanium.xml`, `rajdhani.xml`, `montserrat.xml` using `app:fontProviderAuthority="com.google.android.gms.fonts"`, `app:fontProviderPackage="com.google.android.gms"`, `app:fontProviderCerts="@array/com_google_android_gms_fonts_certs"`.
• `app/src/main/res/values/preloaded_fonts.xml` SHALL declare all 8 new font resources in `@array/preloaded_fonts`.
3. *Defensive Resolution & Safe Offline Fallback*:
• `CockpitTypography.resolveFontFamily` SHALL map each new enum constant to `FontFamily(Font(R.font.<name>))` through its concurrent resolution cache.
• If font loading throws an exception (offline device, missing Play Services, JVM unit test), the resolver SHALL catch `Throwable` and fall back to `fallbackFontFamily(family)`.
• `CockpitTypography.fallbackFontFamily` SHALL map all 8 new font families to standard Compose `FontFamily.SansSerif`.
4. *Dropdown Container Scroll Resilience*:
• In `AdvancedTuningDialog.kt`, `ExposedDropdownMenu` SHALL apply `Modifier.heightIn(max = 360.dp)` to ensure the 13-item list scrolls cleanly on compact and landscape displays without clipping or blocking dismiss gestures.
5. *9-Language Localization Parity*:
• Display name strings (`tuning_font_bebas_neue`, `tuning_font_teko`, `tuning_font_barlow_condensed`, `tuning_font_oswald`, `tuning_font_chakra_petch`, `tuning_font_oxanium`, `tuning_font_rajdhani`, `tuning_font_montserrat`) SHALL be defined across all 9 supported language resource directories (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`).
6. *Preservation of Core Invariants*:
• Weight variations (`NORMAL`, `SEMI_BOLD`, `BOLD`) remain universally applicable across all 13 fonts.
• `TuningPreferencesDataStore` serialization and deserialization remain 100% backwards-compatible with `SYSTEM_DEFAULT` fallback on invalid strings.
• Live preview HUD card in `AdvancedTuningDialog.kt` renders selected font and weight reactively.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement (`REQ-UI-227`), extending `REQ-UI-212` under Epic `ATT-355` (*Good and consistent UI*).
2. *Historical Origin & Commit Trace*: Sprint 2026-40.7 (`ATT-1751`).
3. *Root Reason for Existing Formulation*: ATT-1751 established the typography architecture with an initial 5-font palette; expanding to 13 athletic and technical fonts fulfills athlete requirements for high-contrast, condensed HUD readability under vibration and sunlight.
4. *Preservation of Core Invariants*: DataStore backward compatibility, fallback font resolution, zero database schema mutations, and 9-language localization parity are 100% preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an athlete opening the Cockpit Typography selector in Advanced Settings,
• *When* expanding the Font Family dropdown,
• *Then* all 13 fonts (including Bebas Neue, Teko, Barlow Condensed, Oswald, Chakra Petch, Oxanium, Rajdhani, Montserrat) SHALL be selectable with localized titles.
• *Given* any of the 8 new fonts selected,
• *When* viewing the Live Preview card or active tracking cockpit,
• *Then* numeric values and unit labels SHALL render cleanly using the selected typeface and weight without baseline truncation.
• *Given* an offline environment or JVM unit test,
• *When* font resolution occurs,
• *Then* `CockpitTypography` SHALL fall back safely to `FontFamily.SansSerif` without throwing an unhandled exception.

**Invariants**: DataStore serialization backward compatibility, weight variations orthogonality, live preview reactivity, and 9-language localization parity MUST NOT be broken.

### REQ-UI-228: Aftermath/Splits: Lap Split Visualizer Refinements: 3-Lap Truncation, Custom Names, Explicit Pace Units, and Fastest Lap Rabbit Icon [Verified]
- **Tests**: `TST-UI-182`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `LapSplitVisualizer.kt`, `TrackOnMapScreen.kt`, `WorkoutLaps.kt`
- **Rationale**: The system SHALL refine the Lap & Interval Split Visualizer (`LapSplitVisualizer.kt`), ensuring compact vertical presentation, descriptive lap naming, explicit metric unit formatting, and right-aligned pace columns across workout aftermath inspection views (`TrackOnMapScreen.kt` and `WorkoutLaps.kt`) (ATT-1869):
1. *3-Lap Collapsible Truncation*:
• When `splitData.splits.size > 3`, `LapSplitVisualizer` SHALL initially display only the first 3 laps (`splitData.splits.take(3)`).
• It SHALL render an expandable `TextButton` toggling an internal `rememberSaveable { mutableStateOf(false) }` state.
• When collapsed, the button text SHALL display `R.string.show_all_laps` formatted with total lap count (`splitData.splits.size`).
• When expanded, the full split list SHALL be displayed and the button text SHALL display `R.string.show_fewer_laps`.
• When `splitData.splits.size <= 3`, all laps SHALL be displayed and the toggle button SHALL be omitted.
2. *Actual & Custom Lap Names*:
• In each split row, the lap badge SHALL display `split.displayName` instead of hardcoded synthetic `L{index}`.
• Custom names (e.g., "Warm-up", "Intervall 1") or fallback default ("Lap 1") SHALL be rendered with `style = MaterialTheme.typography.labelSmall`, `fontWeight = FontWeight.Bold`, `maxLines = 1`, `overflow = TextOverflow.Ellipsis`, and constrained by `Modifier.widthIn(max = 120.dp)` to prevent horizontal row overflow.
3. *Explicit Pace & Speed Units*:
• In `TrackOnMapScreen.kt` and `WorkoutLaps.kt`, `LapSplitCalculator.calculateSplitData` invocations SHALL pass `formatters.pace.format_with_units(it)` and `formatters.speed.format_with_units(it)`.
• Each split row SHALL render explicit speed/pace units formatted with the athlete's active unit system (e.g. `min/km`, `min/mi`, `km/h`, `mile/h`).
4. *Fastest Lap Rabbit Icon & Columnar Right-Alignment*:
• The textual 'Best' pill badge SHALL be removed.
• In each split row, the pace/speed column SHALL be right-anchored.
• On the fastest split row (`split.isFastest == true`), a subtle rabbit icon (`🐇`) SHALL be rendered immediately to the left of the formatted pace/speed text, separated by `4.dp` spacing.
• The formatted pace/speed text SHALL remain right-aligned across all split rows, ensuring identical right margin alignment regardless of whether the rabbit icon is present.
5. *Preservation of Core Invariants*:
• Interactive lap selection (`selectedLapNr`) and route highlighting on the map track polyline (`TrackOnMapScreen.kt`) remain 100% functional.
• Single-thread SQLite confinement and `LapData` models remain unmodified.
• Proportional tonal split bars and intensity color scaling remain intact.
• 100% 9-language localization parity is preserved.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement (`REQ-UI-228`), refining and extending `REQ-UI-204` under Epic `ATT-111` (*Compact Post-Workout Visual Analytics & Graphs*).
2. *Historical Origin & Commit Trace*: Sprint 2026-40.7 (`ATT-1742`).
3. *Root Reason for Existing Formulation*: ATT-1742 replaced rainbow bars with tonal bars and removed emojis, replacing them with a textual "Best" badge. Testing on Pixel 10 revealed unbounded scrolling for multi-lap workouts, loss of custom lap names, missing pace units, and pace column misalignment.
4. *Preservation of Core Invariants*: Proportional tonal split bars, map polyline synchronization, 9-language localization parity, and non-crashing calculation engines remain 100% preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* a workout session with more than 3 recorded laps viewed in `LapSplitVisualizer` or `LapSplitVisualizerCard`,
• *When* the screen renders initially,
• *Then* exactly 3 split rows SHALL be visible, followed by an expansion button displaying the total count (`Alle {count} Runden anzeigen` / `Show all {count} laps`).
• *When* the athlete taps the expansion button,
• *Then* all split rows SHALL be displayed and the button text SHALL switch to `Weniger anzeigen` / `Show fewer`.
• *Given* recorded laps with custom names or default names,
• *When* `LapSplitVisualizer` renders,
• *Then* each row badge SHALL display the actual `displayName` instead of synthetic `L1..Lx` tokens.
• *Given* a workout session viewed in Aftermath or WorkoutLaps,
• *When* split rows render,
• *Then* the pace/speed column SHALL display explicit units (e.g. `min/km` or `km/h`).
• *Given* the fastest split in a workout,
• *When* the fastest split row is rendered,
• *Then* a rabbit icon (`🐇`) SHALL appear immediately to the left of the pace/speed text, and the numeric pace/speed text SHALL align rightwards at the exact same edge coordinate as all other non-fastest split rows.

**Invariants**: SQLite single-thread confinement, zero degradation of elevation profile or GPS map rendering, and tabular lap editing MUST NOT be broken.

### REQ-UI-229: Aftermath/Settings: Configurable Lap Section Display Mode (Table vs. Split Visualizer) in Advanced Settings [Verified]
- **Tests**: `TST-UI-183`, `TST-UI-188`, `TST-UI-191`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `LapDisplayMode.kt`, `MyPreferenceManager.kt`, `AdvancedTuningDialog.kt`, `WorkoutLaps.kt`, `WorkoutSummary.kt`
- **Rationale**: The system SHALL provide a configurable preference in Advanced Settings allowing athletes to select their preferred lap presentation mode in the Workout Summary card (`WorkoutSummary.kt` / `WorkoutLaps.kt`), preventing redundant stacked representations while maintaining tabular lap editing across all modes (ATT-1870, ATT-1958, ATT-1988):
1. *Lap Display Mode Domain Model (`LapDisplayMode.kt`)*:
• The system SHALL define `enum class LapDisplayMode` containing exactly two binary options: `TABLE_ONLY` and `VISUALIZER_ONLY`. The redundant stacked option `BOTH` SHALL be eliminated.
2. *Preference Storage & DataStore Persistence (`MyPreferenceManager.kt`)*:
• `WorkoutCardSectionPreferences` SHALL include `val lapDisplayMode: LapDisplayMode = LapDisplayMode.VISUALIZER_ONLY`.
• It SHALL be persisted to DataStore key `workout_card_lap_display_mode`.
• Deserialization SHALL fall back defensively to `LapDisplayMode.VISUALIZER_ONLY` upon null, empty, legacy persisted `"BOTH"`, or unrecognized enum values, guaranteeing zero crashes or migration failures for existing users.
• Factory reset (`resetToDefaults()`) SHALL restore `LapDisplayMode.VISUALIZER_ONLY`.
3. *Advanced Settings UI Selector & State Synchronization (`AdvancedTuningDialog.kt`)*:
• In `WorkoutMasksAndCardsSection` (`TuningSection.WORKOUT_MASKS_CARDS`), when `workoutCardPrefs.showLaps` is enabled (`true`), the dialog SHALL render a selector row with label `R.string.settings_lap_display_mode_title` and a centered Material 3 `SingleChoiceSegmentedButtonRow` containing exactly two `SegmentedButton` options per `REQ-UI-234` and `docs/design_guidelines.md`:
- `Table` (`TABLE_ONLY` / `R.string.settings_lap_display_mode_table`) at index 0
- `Visualizer` (`VISUALIZER_ONLY` / `R.string.settings_lap_display_mode_visualizer`) at index 1
• Using `SegmentedButtonDefaults.itemShape(index = index, count = 2)`. The use of `FilterChip` or radio buttons for binary exclusive choices is prohibited.
• When `workoutCardPrefs.showLaps` is disabled (`false`), the selector row SHALL be omitted from the UI.
• *State Synchronization*: The dialog SHALL collect `workoutCardPreferencesFlow` with `initial = null` and initialize `workoutCardPrefs` only once upon the first non-null emission from DataStore, ensuring persisted preferences are never dropped or superseded by an uninitialized fallback dummy object.
4. *Conditional Rendering in WorkoutLaps (`WorkoutLaps.kt`)*:
• `WorkoutLaps` SHALL accept `lapDisplayMode: LapDisplayMode = LapDisplayMode.VISUALIZER_ONLY`.
• When `lapDisplayMode` is `VISUALIZER_ONLY`, `LapSplitVisualizer` SHALL be rendered and classic table components (`LapTableHeader`, `LapRow` items, expandable toggle) SHALL NOT be rendered.
• When `lapDisplayMode` is `TABLE_ONLY`, classic table components SHALL be rendered and `LapSplitVisualizer` SHALL NOT be rendered.
• Interactive lap editing: Tapping a split in `LapSplitVisualizer` or a row in `LapRow` SHALL dispatch `onLapClick` with the target `LapData`, opening `LapEditBottomSheet` across both modes.
5. *9-Language Localization Parity*:
• All display mode labels SHALL be localized across English, German, Spanish, French, Italian, Japanese, Dutch, Polish, and Portuguese.
6. *Preservation of Core Invariants*:
• Single-thread SQLite confinement and `lapsTable` remain untouched.
• Default for fresh installs and resets is `VISUALIZER_ONLY`, avoiding redundant stacked split representations while allowing users to opt into `TABLE_ONLY`.
• Map polyline highlighting on `TrackOnMapScreen.kt` remains completely decoupled.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement (`REQ-UI-229`), refining and extending `REQ-UI-210`, `REQ-UI-216`, and `REQ-UI-204` under Epic `ATT-111` (*Compact Post-Workout Visual Analytics & Graphs*). Refined in ATT-1958 and ATT-1988.
2. *Historical Origin & Commit Trace*: Sprint 2026-40.8 (`ATT-1870`, Commit `7f747b02`), Sprint 2026-40.9 (`ATT-1958`, Commit `2730177f`), and Sprint 2026-40.10 (`ATT-1988`).
3. *Root Reason for Existing Formulation*: In ATT-1870, `BOTH` was initially chosen as a transitional option so users would not lose the table when visualizer was introduced. In ATT-1958, default was changed to `VISUALIZER_ONLY`. User testing on physical hardware in Sprint 2026-40.9 confirmed that athletes strictly prefer either the modern visualizer or the classic table, and having a third stacked option creates redundant visual clutter and cramped settings chips. In ATT-1988, physical device testing rejected `FilterChip` for binary exclusive options, standardizing on Material 3 `SingleChoiceSegmentedButtonRow` per `REQ-UI-234`.
4. *Preservation of Core Invariants*: Binary choice (`VISUALIZER_ONLY` default vs. `TABLE_ONLY`), defensive mapping of legacy `"BOTH"` to `VISUALIZER_ONLY`, interactive lap editing via `LapEditBottomSheet` in both modes, zero database schema mutations, and 9-language localization parity are 100% strictly preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an athlete with a fresh install or performing factory reset in Advanced Settings,
• *When* inspecting `lapDisplayMode`,
• *Then* the preference SHALL default to `LapDisplayMode.VISUALIZER_ONLY`.
• *Given* an athlete selecting `Visualizer` in Advanced Settings,
• *When* inspecting any workout with recorded laps in the Workout Summary card,
• *Then* only `LapSplitVisualizer` SHALL be rendered, and the classic table (`LapTableHeader` and rows) SHALL be hidden.
• *Given* an athlete selecting `Table` in Advanced Settings,
• *When* inspecting any workout with recorded laps in the Workout Summary card,
• *Then* only the classic table SHALL be rendered, and `LapSplitVisualizer` SHALL be hidden.
• *Given* an athlete with legacy `"BOTH"` persisted in DataStore,
• *When* preferences are loaded,
• *Then* `MyPreferenceManager` SHALL defensively map `"BOTH"` to `LapDisplayMode.VISUALIZER_ONLY` without errors.
• *Given* either active display mode (`Table` or `Visualizer`),
• *When* tapping a lap split or table row,
• *Then* `LapEditBottomSheet` SHALL open for that lap.

**Invariants**: SQLite single-thread confinement, `TrackOnMapScreen` map route highlighting, and 9-language localization parity MUST NOT be broken.

### REQ-UI-230: Aftermath/Graphs: Background Training Zone Bands & Right-Hand Zone Axis for Heart Rate and Power Graphs [Verified]
- **Tests**: `TST-UI-184`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `TelemetryMetricGraph.kt`, `TelemetryZoneMath.kt`, `ElevationProfile.kt`
- **Rationale**: The system SHALL render subtle horizontal training zone background bands and a right-hand secondary zone axis (`Z1`–`Z5`) in `TelemetryMetricGraph.kt` for Heart Rate and Cycling Power telemetry curves, providing athletes with instantaneous zone awareness while strictly preserving curve clarity and horizontal axis alignment with `ElevationProfile.kt` (ATT-1839):
1. *Zone Bands Geometry & Pure Math Engine (`TelemetryZoneMath.kt`)*:
• The system SHALL calculate horizontal band geometry for Zones 1 through 5:
- Zone 1: $[dataMin, \min(dataMax, z1Max)]$
- Zone 2: $[\max(dataMin, z1Max), \min(dataMax, z2Max)]$
- Zone 3: $[\max(dataMin, z2Max), \min(dataMax, z3Max)]$
- Zone 4: $[\max(dataMin, z3Max), \min(dataMax, z4Max)]$
- Zone 5: $[\max(dataMin, z4Max), dataMax]$
• When a zone band has non-empty height ($minVal < maxVal$), the system SHALL render a solid filled rectangle across the full chart plot width ($startPaddingPx \dots startPaddingPx + chartWidthPx$) using `TTColor.Zone1`..`TTColor.Zone5` at calibrated subtle alpha ($0.10f$).
• When $z_kMax \in (dataMin, dataMax)$ for $k \in \{1, 2, 3, 4\}$, the system SHALL draw a subtle horizontal dashed threshold boundary line across the plot area using `outlineVariant.copy(alpha = 0.35f)`.
2. *Right-Hand Secondary Zone Axis (`Z1`–`Z5`)*:
• Along the right 25.dp margin ($startPaddingPx + chartWidthPx \dots size.width$), the system SHALL render centered zone labels `Z1`, `Z2`, `Z3`, `Z4`, `Z5`.
• A zone label SHALL only be rendered IF AND ONLY IF the band vertical pixel height is $\ge 12\text{ dp}$. If band height is $< 12\text{ dp}$, the label MUST be suppressed to eliminate visual overlap.
• Label text SHALL be styled using `MaterialTheme.typography.labelSmall` in muted tone (`onSurfaceVariant` or `ZONE_COLORS[i-1]`).
3. *Scrubbing Readout Zone Indicator*: • During synchronized scrubbing, when an instantaneous HR or Power sample is highlighted on the curve, the readout badge / tooltip SHALL indicate the active zone index (e.g. `165 bpm • Z4`, `280 W • Z3`).
4. *Decoupled Fallback & Speed/Pace Isolation*:
• Speed and Pace metric types SHALL NOT render zone bands or right-hand zone axes, retaining their clean, plain canvas.
• In `TelemetryMetricGraph`, `hrZoneThresholds` and `powerZoneThresholds` SHALL be accepted as optional parameters. If null, the composable SHALL defensively resolve athlete thresholds from `SettingsDataStoreJavaHelper.getZoneMax` via `LocalContext.current` with safe `runCatching` fallback. If thresholds are missing, 0, or corrupt, zone bands MUST be omitted without throwing unhandled exceptions.
5. *Preservation of Core Invariants*:
• Horizontal plot paddings (`startPaddingPx = 50.dp`, `endPaddingPx = 25.dp`) MUST NOT be changed, guaranteeing pixel-perfect vertical alignment with `ElevationProfile.kt` and across all stacked charts during multi-chart scrubbing.
• Single-finger vertical scroll gesture propagation (`REQ-UI-226`) and two-finger pinch zoom remain intact.
• The primary curve stroke color (Red for HR, Dark Violet for Power) remains solid to ensure immediate visual distinction from the gradient-colored elevation profile.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement (`REQ-UI-230`), extending and refining `REQ-UI-206` under Epic `ATT-111` (*Compact Post-Workout Visual Analytics & Graphs*).
2. *Historical Origin & Commit Trace*: Sprint 2026-40.5 (`ATT-1740`) and Sprint 2026-40.8 (`ATT-1872`).
3. *Root Reason for Existing Formulation*: `TelemetryMetricGraph` previously rendered curves against a plain monochromatic background, giving athletes no visual correlation to their personalized training zones during timeline inspection.
4. *Preservation of Core Invariants*: Horizontal padding parity (50.dp / 25.dp), speed/pace clean canvas isolation, non-clashing solid curve strokes, and vertical scroll gesture disambiguation are 100% preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an athlete viewing the Heart Rate or Power graph in Aftermath detailed view,
• *When* the graph renders,
• *Then* 5 horizontal colored bands (`TTColor.Zone1`..`Zone5` with subtle 10% alpha) SHALL be visible in the background corresponding to the athlete's configured zone thresholds.
• *Given* the Heart Rate or Power graph,
• *When* inspecting the vertical axes,
• *Then* the left axis SHALL display numeric metrics (BPM or Watts) and the right axis SHALL display zone identifiers (`Z1`–`Z5`) aligned with their bands.
• *Given* a workout where heart rate spans only a narrow range (e.g. Zone 2 only),
• *Then* only bands intersecting the data range SHALL be rendered, and labels for bands narrower than 12 dp SHALL be suppressed.
• *Given* an athlete scrubbing across the HR or Power graph,
• *When* holding the cursor at any point,
• *Then* the readout tooltip SHALL indicate the active zone (e.g. `172 bpm • Z4`).
• *Given* a Speed or Pace graph,
• *When* rendered,
• *Then* no zone bands or right-hand zone axes SHALL be displayed.

**Invariants**: Horizontal padding parity with ElevationProfile (50.dp start / 25.dp end), vertical scroll disambiguation (`REQ-UI-226`), and solid primary curve strokes MUST NOT be broken.

### REQ-UI-231: Aftermath/Zones: Fine-Grained Telemetry Frequency Histogram (BPM & Wattage Bins) with Zone Color Shading [Verified]
- **Tests**: `TST-UI-185`, `TST-UI-189`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `ZoneDistributionModels.kt`, `ZoneDistributionCalculator.kt`, `TelemetryHistogramChart.kt`, `HeartRateZoneDistributionCard.kt`, `PowerZoneDistributionCard.kt`
- **Rationale**: The system SHALL provide a fine-grained telemetry frequency histogram for Heart Rate and Cycling Power in `HeartRateZoneDistributionCard.kt` and `PowerZoneDistributionCard.kt`, allowing athletes to inspect detailed distribution density across narrow metric bins shaded with their active training zone colors (ATT-1845, ATT-1959):
1. *Histogram Binning Engine & Data Models (`TelemetryHistogramModels.kt`, `ZoneDistributionModels.kt`, `ZoneDistributionCalculator.kt`)*:
• The system SHALL define `data class TelemetryHistogramBin(val binIndex: Int, val rangeMin: Int, val rangeMax: Int, val durationSec: Long, val percentage: Float, val zoneIndex: Int, val color: Color)`.
• The system SHALL define `data class TelemetryHistogramData(val binWidth: Int, val dataMin: Int, val dataMax: Int, val totalActiveTimeSec: Long, val bins: List<TelemetryHistogramBin>)`.
• `ZoneDistributionData` SHALL include `val histogram: TelemetryHistogramData? = null`.
• The calculation engine SHALL compute continuous bins across $[v_{min}, v_{max}]$ using curated default bin widths ($\Delta = 2\text{ bpm}$ for Heart Rate, $\Delta = 10\text{ W}$ for Power).
• Each bin duration SHALL accumulate active sample durations ($\Delta t \le 5\text{s}$).
• Every histogram bar SHALL be shaded using `TTColor.Zone1`..`TTColor.Zone5` corresponding to the bin midpoint evaluated against active athlete thresholds (`HeartRateZoneThresholds` / `PowerZoneThresholds`).
2. *View Mode Switching & Header Layout in Zone Cards (`HeartRateZoneDistributionCard.kt`, `PowerZoneDistributionCard.kt`)*:
• *Header Row Layout*: The card header row SHALL contain only the leading icon, localized card title (`aftermath_hr_zones_title` / `aftermath_power_zones_title`), and total active duration (`ZoneDistributionChartMath.formatZoneDuration(distribution.totalActiveTimeSec)`), providing maximum horizontal breathing space and preventing vertical text wrapping across all supported screen densities and languages.
• *Bottom Mode Toggle*: When `distribution.histogram != null`, the card SHALL render a dedicated mode toggle row directly below the chart (`ZoneDistributionColumnChart` or `TelemetryHistogramChart`), centered horizontally, utilizing `SingleChoiceSegmentedButtonRow` with localized options `zone_mode_5_zones` and `zone_mode_histogram`.
• Mode state SHALL be maintained locally via `rememberSaveable { mutableStateOf(ZoneCardDisplayMode.FIVE_ZONES) }`.
3. *Histogram Composable & Interactive Scrubbing (`TelemetryHistogramChart.kt`)*:
• The system SHALL implement `TelemetryHistogramChart` rendering discrete vertical bars with 1 dp spacing.
• Each bar height SHALL scale proportionally to the maximum bin duration in the workout.
• *Streamlined Readout*: In the default unscrubbed state, the readout bar SHALL display only the data range (`${histogram.dataMin}–${histogram.dataMax} $unit`) left-anchored and bin resolution (`Δ ${histogram.binWidth} $unit`) right-anchored. The redundant hardcoded `"active bins"` label SHALL be removed.
• Touching, tapping, or dragging across bars SHALL highlight the selected bin and update a compact readout label: `${rangeMin}–${rangeMax} bpm • ${formattedDuration} (${percentage}%) • Z${zone}`.
• Releasing touch or tapping outside SHALL reset the readout.
4. *9-Language Localization Parity*:
• All toggle tokens (`zone_mode_5_zones`, `zone_mode_histogram`) SHALL be localized across English, German, Spanish, French, Italian, Japanese, Dutch, Polish, and Portuguese.
5. *Preservation of Core Invariants*:
• The default 5-zone column view remains 100% backward compatible and unchanged.
• Single-thread SQLite confinement and `samplesTable` remain untouched.
• Zero database migrations or mutations.
• Binning runs asynchronously off the main thread (`Dispatchers.IO`) in `WorkoutRepository`.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement (`REQ-UI-231`), extending `REQ-UI-202` and `REQ-UI-203` under Epic `ATT-111` (*Compact Post-Workout Visual Analytics & Graphs*). Refined in ATT-1959.
2. *Historical Origin & Commit Trace*: Sprint 2026-40.8 (Commit `bf624382`, ATT-1845) and Sprint 2026-40.9 (`ATT-1959`).
3. *Root Reason for Existing Formulation*: In ATT-1845, the segmented button toggle was placed in the header row adjacent to the title to keep the card compact. However, testing on physical devices revealed that on standard viewports, placing the title, toggle, and total duration on a single line causes catastrophic text wrapping of the duration string in languages with longer words (e.g. German "Herzfrequenz-Zonen"). Furthermore, the default histogram readout contained a non-localized English label ("active bins"). Placing the toggle below the chart provides ample room for both the header elements and the toggle labels, while removing the active bins label streamlines the readout.
4. *Preservation of Core Invariants*: Full backward compatibility with default 5-zone mode, theme contrast parity, asynchronous execution, 9-language localization parity, and zero schema mutations are 100% preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an athlete viewing Heart Rate Zones or Power Zones in Aftermath or Workout Summary on any device width,
• *When* the card renders,
• *Then* the header row SHALL display the icon, localized title, and total duration on a single un-wrapped line without collisions.
• *Given* a workout with telemetry frequency histogram data,
• *When* the card renders,
• *Then* a centered view mode toggle (`5 Zones` / `Histogram`) SHALL be displayed directly below the chart.
• *Given* an athlete inspecting `TelemetryHistogramChart` in resting state,
• *When* no bar is touched or scrubbed,
• *Then* the readout line SHALL display only the range and bin width delta (`Δ`), omitting any `"active bins"` label.
• *Given* an athlete touching or scrubbing any histogram bar,
• *When* a bar is highlighted,
• *Then* the readout line SHALL display the bin range, duration, percentage, and zone name.
• *Given* an athlete switching between `5 Zones` and `Histogram`,
• *Then* the corresponding chart SHALL be displayed immediately.

**Invariants**: Asynchronous computation off the main thread, SQLite single-thread confinement, and 9-language localization parity MUST NOT be broken.

### REQ-UI-232: Aftermath/Graphs: Synchronized Horizontal Window Panning Across Stacked Telemetry Metric Graphs in Pan Mode [Verified]
- **Tests**: `TST-UI-186`, `TST-UI-190`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: \Delta y
- **Rationale**: The system SHALL support smooth, continuous horizontal viewport window panning across all stacked telemetry metric graphs (`TelemetryMetricGraph.kt` for Speed/Pace, Heart Rate, and Power) and `ElevationProfile.kt` when the global zoom toolbar is toggled to Pan Mode (`isPanMode == true`), ensuring seamless multi-chart navigation in lockstep without mid-gesture coroutine cancellations, cross-domain clamping collisions, or sluggish travel distance (ATT-1956, ATT-1987):
1. *TelemetryMetricGraph Pan Mode Signature & State Ingestion*:
• `TelemetryMetricGraph` SHALL accept optional parameters `isPanMode: Boolean = false` and `onZoomChanged: ((Float, Double) -> Unit)? = null`.
• Default arguments SHALL preserve 100% binary and source backward compatibility for all un-instrumented callers (e.g. `WorkoutSummary.kt`).
2. *Directional Pan Gesture Execution & Cancellation-Free Pointer Observation*:
• In `TelemetryMetricGraph.kt` and `ElevationProfile.kt`, the canvas `pointerInput` SHALL observe ONLY stable configuration parameters `(totalSpan, isTimeDomain, isPanMode)`. Mutable running parameters (`startDist`, `currentStartDist`, `zoomScale`, `currentZoomScale`) and callbacks (`onZoomChanged`, `updateZoom`) SHALL NOT be declared as `pointerInput` keys, preventing mid-gesture coroutine cancellation.
• Dynamic parameters SHALL be safely referenced using `rememberUpdatedState`.
• Inside `awaitEachGesture`, a local running start variable (`localStartDist`) SHALL be initialized at `down` and updated continuously per drag event across the entire swipe at 60/120fps.
• When a dominant horizontal drag is detected (`isDragging == true`):
- If `isPanMode == true`, `totalSpan > 10.0`, and `onZoomChanged != null`: the handler SHALL consume the pointer event (`pointer.consume()`), calculate pointer horizontal delta `dragDeltaX = pointer.position.x - prevX`, invoke `ElevationProfileZoomMath.applyPan(currentStartDist = localStartDist, visibleDist = visibleSpan, panDeltaX = dragDeltaX, canvasWidth = chartWidthPx, totalDist = totalSpan)`, update `localStartDist = panStart`, invoke `onZoomChanged(zoomScale, panStart)`, and SHALL NOT trigger scrubbing distance updates (`onDistanceSelected`).
- If `isPanMode == false`: the handler SHALL preserve continuous route scrubbing: calculating `selectedVal` via `ElevationProfileZoomMath.canvasXToDistance` and invoking `onDistanceSelected(selectedVal)`.
- In all cases, `prevX` SHALL update to `pointer.position.x`.
3. *Gesture Release & Tap Isolation in Pan Mode*:
• Upon drag completion (`isDragging == true`): if `isPanMode == false`, the handler SHALL clear the scrubbing cursor (`onDistanceSelected(null)`); if `isPanMode == true`, it SHALL NOT clear or modify scrub selection.
• Upon stationary tap release without drag (`!isVerticalScrolling && !isDragging`): if `isPanMode == false`, tap selection SHALL update `onDistanceSelected`; if `isPanMode == true`, tap inspection SHALL be suppressed to prevent accidental scrub selection during pan navigation.
4. *ElevationProfile Callback Lifecycle & Gesture Continuity (`ElevationProfile.kt`)*:
• `ElevationProfile.kt` SHALL reference `onZoomChanged` directly via `rememberUpdatedState(onZoomChanged)` rather than creating function reference allocations (`::updateZoom`) on each recomposition.
• `prevCentroid` SHALL be reset to `pointer.position` immediately upon horizontal drag activation (`isDragging == true`), eliminating touch slop jump accumulation.
• In `ElevationProfile.kt`, `isVerticalScrolling` SHALL NOT break the gesture loop prematurely (`if (pointer.isConsumed) break` removed), preserving touch tracking continuity until all pointers are unpressed (`pressed.isEmpty()`).
5. *MapDetailLayout Normalized Viewport Start Progress Fraction & Multi-Graph Lockstep Synchronization (`MapDetailLayout.kt`)*:
• In `MapDetailLayout.kt`, when `showZoomControls == true` and `activeScrubPath != null && activeScrubPath.isNotEmpty()`, the horizontal viewport offset SHALL be stored and managed strictly as a dimensionless fraction `var viewportStartFraction by remember(activeScrubPath) { mutableDoubleStateOf(0.0) }` alongside `profileZoomScale: Float`.
• The fraction range SHALL be clamped strictly to $[0.0, \max(0.0, 1.0 - 1.0 / Z)]$.
• Two-way mapping functions SHALL translate between fraction and local domain units: fraction to domain start value $(F \times S).\text{coerceIn}(0.0, \max(0.0, S - S/Z))$, and domain start value to fraction $(\text{startVal} / S).\text{coerceIn}(0.0, \max(0.0, 1.0 - 1.0/Z))$, with zero-span protection when $S \le 0.0$.
• Panning any chart (`ElevationProfile` or `TelemetryMetricGraph`) SHALL update `viewportStartFraction`, smoothly navigating all visible charts and the map route marker in exact lockstep without cross-domain clamping collisions between meters and seconds.
6. *Preservation of Core Invariants*:
• Vertical scrolling propagation: single-finger vertical swipes ($

### REQ-UI-233: Aftermath/Settings: Independent X-Axis Domain Settings for Elevation Profile (Distance) vs. Telemetry Graphs (Time) [Verified]
- **Tests**: `TST-UI-192`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
• The Aftermath accordion subtitle (`TuningSubtitleFormatter.formatAftermathSubtitle`) SHALL summarize both configured domains (e.g. "Höhenprofil: Distanz, Telemetrie: Zeit").
• Display strings SHALL be localized across all 9 application locales.
3. *Independent Domain Distribution in MapDetailLayout (`MapDetailLayout.kt`)*:
• `ElevationProfile` SHALL be supplied with `xAxisDomain = tuningConfig.elevationXAxisDomain`.
• Stacked `TelemetryMetricGraph` instances (Speed/Pace, Heart Rate, Power) SHALL be supplied with `xAxisDomain = tuningConfig.telemetryXAxisDomain`.
4. *Synchronized Cross-Domain Scrubbing & Cursor Rendering (`MapDetailLayout.kt`, `TelemetryMetricGraph.kt`, `ElevationProfile.kt`)*:
• `selectedDistance` across all charts and the map marker SHALL be maintained in route meters.
• When scrubbing either chart, touch coordinates SHALL resolve to the nearest `PathPoint` and dispatch its route distance.
• When rendering the scrubbing cursor in `TelemetryMetricGraph.kt`, the component SHALL convert `currentDistance` to temporal coordinates when in Time domain and evaluate `cursorDistSpan in 0.0..totalSpan` and `cursorDistSpan in startDist..(startDist + visibleSpan)`.
5. *Preservation of Core Invariants*:
• Directional gesture disambiguation (`REQ-UI-226`) and vertical scroll freedom remain 100% intact.
• Plot area padding parity (`start = 50.dp, end = 25.dp`) remains strictly aligned.
• 9-language localization parity across all supported application locales.
• Zero database schema mutations.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement (`REQ-UI-233`), extending and refining `REQ-UI-201` (*Synchronized Multi-Metric Scrubbing on Elevation Profile with Configurable X-Axis Domain*) and `REQ-UI-206` (*Continuous Telemetry Metric Graphs*) under Epic `ATT-111` (*Compact Post-Workout Visual Analytics & Graphs*).
2. *Historical Origin & Commit Trace*: Sprint 2026-40.5 (`ATT-1391`, Commit `e7a6a273`) and Sprint 2026-40.6 (`ATT-1740`, Commit `061a4b35`).
3. *Root Reason for Existing Formulation*: When telemetry curves were created in ATT-1740, they reused `profileXAxisDomain` from ATT-1391 for simplicity. However, athletic mental models dictate that terrain is inherently spatial (Distance), while metabolic and exertion metrics are inherently temporal (Time). Coupling them into a single setting forced an unwanted compromise.
4. *Preservation of Core Invariants*: Full zoom/pan mathematics, cross-chart scrubbing synchronization, padding parity (50dp/25dp), 9-language localization parity, and zero SQLite schema mutations are 100% strictly preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an athlete with a fresh install or performing factory reset in Advanced Settings,
• *When* inspecting Aftermath tuning preferences,
• *Then* `elevationXAxisDomain` SHALL default to `DISTANCE` and `telemetryXAxisDomain` SHALL default to `TIME`.
• *Given* the default configuration,
• *When* viewing workout charts in `MapDetailLayout`,
• *Then* `ElevationProfile` SHALL render with Distance on the X-axis, and all Telemetry Graphs (Speed/Pace, Heart Rate, Power) SHALL render with Time on the X-axis.
• *Given* an athlete in Advanced Settings (Aftermath section),
• *When* changing either domain setting independently,
• *Then* both settings SHALL persist independently in DataStore and update the respective charts immediately.
• *Given* `ElevationProfile` on Distance and `TelemetryMetricGraph` on Time,
• *When* scrubbing either chart,
• *Then* the cursor line and highlight dots SHALL position synchronously at the matching temporal/spatial sample coordinate without visual lag or jumping.

**Invariants**: Directional gesture disambiguation (`REQ-UI-226`), horizontal padding parity (50dp/25dp), and 9-language localization parity MUST NOT be broken.
- **Implementation**: Time]`.
- **Telemetry Graphs X-Axis**: Title `R.string.tuning_telemetry_x_axis_title`, description `R.string.tuning_telemetry_x_axis_desc`, and FilterChips `[Time (Default)
- **Rationale**: The system SHALL provide independent horizontal X-axis domain configuration for the elevation profile and continuous telemetry metric graphs in Advanced Settings (`TuningPreferencesDataStore.kt` & `AdvancedTuningDialog.kt`), defaulting Elevation to Distance and Telemetry to Time, while preserving synchronized cross-domain scrubbing and zoom lockstep across `MapDetailLayout.kt` (ATT-1986):
1. *DataStore Preferences Decoupling (`TuningPreferencesDataStore.kt`)*:
• The system SHALL decouple `profileXAxisDomain` into two distinct persisted preferences:
- `val elevationXAxisDomain: ProfileXAxisDomain` with factory default `ProfileXAxisDomain.DISTANCE`. Persisted to key `tuning_elevation_x_axis_domain`.
- `val telemetryXAxisDomain: ProfileXAxisDomain` with factory default `ProfileXAxisDomain.TIME`. Persisted to key `tuning_telemetry_x_axis_domain`.
• *Backward Compatibility & Migration*: If `tuning_elevation_x_axis_domain` is uninitialized, the system SHALL check legacy `tuning_profile_x_axis_domain`, falling back to `DISTANCE`. If `tuning_telemetry_x_axis_domain` is uninitialized, it SHALL default to `TIME`. `TuningConfig` SHALL provide a deprecated `profileXAxisDomain` accessor returning `elevationXAxisDomain` to preserve binary and source compatibility.
• Factory reset (`resetToDefaults()`) SHALL restore `elevationXAxisDomain = DISTANCE` and `telemetryXAxisDomain = TIME`.
2. *Expert Settings UI Selector Controls (`AdvancedTuningDialog.kt`, `AdvancedTuningAccordion.kt`)*:
• In `AftermathAnalysisSection`, the dialog SHALL render two independent selector rows:
- **Elevation Profile X-Axis**: Title `R.string.tuning_profile_x_axis_title`, description `R.string.tuning_profile_x_axis_desc`, and FilterChips `[Distance (Default)

### REQ-UI-234: UI & Interaction Design System: Mutually Exclusive Binary Mode Selection & Component Heuristics [Verified]
- **Tests**: `TST-UI-193`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: Establish UI design system heuristics linking to docs/design_guidelines.md, mandating SingleChoiceSegmentedButtonRow for binary mode selection and enforcing cross-domain coordinate separation.
- **Rationale**: The user interface SHALL adhere to the centralized design principles and component selection heuristics defined in `docs/design_guidelines.md` (ATT-1988, ATT-1989):
1. *Binary Mutually Exclusive Mode Selections*: Any user selection between exactly two mutually exclusive display, visualization, or calculation modes (e.g. Table vs. Visualizer, 5 Zones vs. Histogram, Pace vs. Speed) SHALL utilize Material 3 `SingleChoiceSegmentedButtonRow` with `SegmentedButton`. The use of `FilterChip` or radio buttons for binary exclusive modes is prohibited.
2. *Categorical & Multi-Option Tags*: Three or more non-exclusive category options or filters SHALL utilize `FilterChip` / `SuggestionChip`.
3. *Cross-Domain Chart Viewport Separation*: Multi-chart composite viewports mixing spatial (distance) and temporal (time) coordinates SHALL maintain independent domain offsets or normalized fractional coordinates ($0.0 \dots 1.0$), prohibiting single shared raw metric scalars for pan/zoom positions.
4. *Touch & Gesture Key Stability*: Gestures in charts and interactive canvases SHALL decouple continuously mutable coordinates from `Modifier.pointerInput` keys, observing stable parameters and capturing dynamic states via `rememberUpdatedState`.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement (`REQ-UI-234`), establishing binding design system heuristics linking to `docs/design_guidelines.md` under Epic `ATT-111` (*Compact Post-Workout Visual Analytics & Graphs*).
2. *Historical Origin & Commit Trace*: Sprint 2026-40.10 Review (`ATT-1988`, `ATT-1989`).
3. *Root Reason for Formulation*: Review on physical Pixel 10 hardware revealed that using `FilterChip` for binary exclusive choices (Table vs. Visualizer in ATT-1988) looks unanchored and inconsistent with card-level mode toggles (5 Zones vs. Histogram). Standardizing binary toggles on `SingleChoiceSegmentedButtonRow` and formalizing chart coordinate separation guarantees UI cohesion across the application.
4. *Preservation of Core Invariants*: Existing multi-option filter chips, card layouts, and 9-language localization parity are 100% strictly preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an interface presenting a binary choice between two mutually exclusive display modes,
• *When* rendered,
• *Then* the UI SHALL use Material 3 `SingleChoiceSegmentedButtonRow` with `SegmentedButton` and SHALL NOT use `FilterChip`.
• *Given* composite multi-chart viewports mixing distance and time domains,
• *When* panned or zoomed,
• *Then* viewport start coordinates SHALL NOT be shared as raw metric scalars across different coordinate spaces.

**Invariants**: Material 3 theming, touch slop disambiguation (`REQ-UI-226`), and 9-language localization parity MUST NOT be broken.

### REQ-UI-235: Aftermath/Telemetry: Continuous Sensor Telemetry Graph Ingestion, Time-Domain Scrubbing, and Map Collapse for Trackless Workouts [Verified]
- **Tests**: `TST-UI-194`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
2. *ViewModel Telemetry Ingestion (`TrackOnMapAftermathViewModel`)*:
• In `AftermathMapUIState`, the system SHALL expose `val telemetryPath: List<PathPoint> = emptyList()`.
• In `loadAftermathData()`, when `fullTracks` is empty (no GPS tracks available), the ViewModel SHALL query `workoutRepository.getWorkoutTelemetryPoints(workoutId)` and populate `_uiState.value.copy(telemetryPath = telemetryPoints)`.
3. *TrackOnMapScreen Scrubber & Layout Wiring (`TrackOnMapScreen.kt`)*:
• The screen SHALL evaluate `val hasGpsTrack = tracks.any { it.path.isNotEmpty() && it.latLngs.any { p -> p.latitude != 0.0
- **Rationale**: The system SHALL extract, ingest, and render continuous sensor telemetry (Heart Rate and Cycling Power) along the Time domain across workout duration for trackless workouts (sessions without GPS coordinates), support interactive time-domain scrubbing, and automatically collapse the Google Map viewport (ATT-2006):
1. *Decoupled Telemetry Extraction (`WorkoutRepository`)*:
• The system SHALL provide `suspend fun getWorkoutTelemetryPoints(workoutId: Long): List<PathPoint>` in `WorkoutRepository.kt`.
• When querying `WorkoutSamples.db`, the method SHALL extract recorded samples having valid sensor data (Heart Rate `SensorType.HR` or Power `SensorType.POWER`), capturing `timeSec`, `hr`, `power`, and `speedMps`, with `distance = 0.0` and `latLng = LatLng(0.0, 0.0)`.
• When sample count exceeds 800, the method SHALL apply uniform stride decimation preserving initial and terminal samples (`index == 0

### REQ-UI-236: WorkoutSummaries: Support Marking Workouts as "Race" (Wettkampf) Across DB, Header Badge, Edit Dialog, and Filters [Verified]
- **Tests**: `TST-UI-195`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `WorkoutSummariesDatabaseManager.java`, `WorkoutData.kt`, `WorkoutHeader.kt`, `EditWorkoutScreen.kt`, `WorkoutFilterCriteria.kt`, `StravaUploader.kt`
- **Rationale**: The system SHALL support marking workouts as a "Race" (Wettkampf) across the persistence layer, domain models, header presentation, edit workflow, list filtering, preferences, and cloud uploaders (ATT-2005):
1. *Database Schema & Migration (`WorkoutSummariesDatabaseManager.java`)*:
• The system SHALL define `public static final String RACE = "race";`.
• The database version SHALL be incremented from `DB_VERSION = 22` to `DB_VERSION = 23`.
• `CREATE_TABLE_WORKOUT_SUMMARIES` SHALL include `+ WorkoutSummaries.RACE + " int DEFAULT 0,"`.
• In `onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion)`: when `oldVersion < 23`, the system SHALL call `addColumnIfNotExists(db, WorkoutSummaries.TABLE, WorkoutSummaries.RACE, "int", "0")`.
• In `updateWorkoutData(WorkoutData workoutData)`: the system SHALL persist `values.put(WorkoutSummaries.RACE, workoutData.getRace() ? 1 : 0);`.
2. *Domain Models & Data Layer Mapping (`WorkoutData.kt`, `WorkoutHeaderData.kt`, `WorkoutDataMapper.kt`, `WorkoutRepository.kt`)*:
• `WorkoutData.kt` SHALL include `val race: Boolean = false`.
• `WorkoutHeaderData.kt` SHALL include `val race: Boolean = false`.
• `WorkoutData.headerData` SHALL forward `race = race`.
• `WorkoutDataMapper.kt` SHALL read `WorkoutSummaries.RACE` from Cursor (using `getColumnIndex` safely) and map `cursor.getInt(...) == 1` to `race`.
• `WorkoutRepository.kt` in `saveWorkout` SHALL preserve `race = userEditedWorkout.race` during in-memory state copy so UI state flows reflect modifications immediately.
3. *Workout Header Badge (`WorkoutHeader.kt`)*:
• In Row A (Sport specific info row), when `data.race == true`, the system SHALL render a Material 3 `Surface` chip: `RoundedCornerShape(4.dp)`, container tone `tertiaryContainer`, content tone `onTertiaryContainer`, vector icon `Icons.Default.EmojiEvents` (14.dp), and localized text `stringResource(R.string.race_badge)`, styled with `MaterialTheme.typography.labelSmall`, bold.
• The badge SHALL strictly contain zero raw unicode emojis (no `🏁` emoji characters).
4. *Edit Workout UI & Preferences (`EditWorkoutViewModel.kt`, `EditWorkoutScreen.kt`, `MyPreferenceManager.kt`, `AdvancedTuningDialog.kt`)*:
• `EditWorkoutViewModel.kt` SHALL provide `fun updateIsRace(isChecked: Boolean)`, updating `_workoutData.value = _workoutData.value?.copy(race = isChecked)`.
• `EditWorkoutScreen.kt` SHALL render a checkbox/switch for Race when `fieldPreferences.showRace == true`.
• `MyPreferenceManager.kt` (`EditWorkoutFieldPreferences`) SHALL include `val showRace: Boolean = true`, persisted with key `KEY_SHOW_RACE = "show_race"`.
• `AdvancedTuningDialog.kt` under "Workout Masks" SHALL provide a toggle for the Race field visibility.
5. *Workout List Filtering (`WorkoutFilterCriteria.kt`, `WorkoutFilterBottomSheet.kt`)*:
• `WorkoutFilterCriteria.kt` SHALL include `val isRace: Boolean? = null`.
• `matches(workout: WorkoutData)` predicate SHALL evaluate `if (isRace != null && workout.race != isRace) return false`.
• JSON serialization and deserialization (`toJson`, `fromJson`) SHALL include `isRace`.
• `WorkoutFilterBottomSheet.kt` SHALL render an interactive filter chip for Race events.
6. *External Platform Integration (`StravaUploader.kt`)*:
• When uploading an activity to Strava where `race == true`, `StravaUploader.kt` SHALL append multipart form parameter `workout_type`: `11` for `BSportType.BIKE` (Strava Race Ride) and `1` for `BSportType.RUN` (Strava Race Run).
7. *9-Language Localization Parity*:
• All newly introduced string keys (`race`, `race_badge`, `pref_show_race`, `filter_race_only`) SHALL be translated across all 9 application locales.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement (`REQ-UI-236`) under Epic `ATT-68` (*Improve WorkoutSummaries*).
2. *Historical Origin & Commit Trace*: Sprint 2026-40.11 (`ATT-2005`).
3. *Root Reason for Existing Formulation*: Workouts lacked a dedicated competitive event classification across database, summary header, filter sheet, and external upload.
4. *Preservation of Core Invariants*: Idempotent schema migration without data loss, IO thread safety, zero raw emojis, and 9-language localization parity are 100% preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an existing database without the race column,
• *When* upgrading to DB version 23,
• *Then* the SQLite database SHALL add column race with default 0 without data loss.
• *Given* an athlete editing a completed workout in `EditWorkoutScreen`,
• *When* toggling 'Race' and saving,
• *Then* the workout header SHALL immediately display the Race badge.
• *Given* a workout marked as a race,
• *When* rendered in `WorkoutHeader`,
• *Then* the header SHALL render a distinct Race tag (with Material vector icon, zero raw emojis) in Row A.
• *Given* an athlete filtering the workout list,
• *When* selecting the 'Race' filter criterion,
• *Then* only workouts where `race == true` SHALL be displayed.
• *Given* a race workout uploaded to Strava,
• *Then* `workout_type` SHALL be set to `11` for cycling and `1` for running.

**Invariants**: Backward compatible idempotent database upgrade, zero raw emojis, and 9-language localization parity MUST NOT be broken.

### REQ-UI-237: UI/Sheets: System Navigation Bar Inset Clearance & Upward Travel Calibration for Segment, Route, and Live Segment Popups [Verified]
- **Tests**: `TST-UI-196`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `MapDetailLayout.kt`, `LiveSegmentSheet.kt`, `MapScreenWithTrack.kt`, `SensorGridScreen.kt`
- **Rationale**: The system SHALL ensure that bottom sheet popups displaying segments and routes on the map (`MapScreenWithTrack.kt`) and live segment popups during tracking (`SensorGridScreen.kt` / `LiveSegmentSheet.kt`) can be dragged and expanded upward with sufficient travel, and incorporate system navigation bar window insets (`WindowInsets.navigationBars`) such that the entire elevation profile and all interactive controls remain 100% visible and accessible above the Android 3-button navigation bar (ATT-2008):
1. *Navigation Bar Inset Clearance in MapDetailLayout (`MapDetailLayout.kt`)*:
• In `MapDetailLayout.kt`, when `showElevationProfile` is true and `analyticsContent == null`, the `Surface` wrapping `ElevationProfile` (and telemetry graphs) SHALL apply `Modifier.navigationBarsPadding()`.
• When `analyticsContent != null`, `analyticsContent` SHALL retain its `Modifier.navigationBarsPadding()`, and the elevation profile `Surface` SHALL NOT apply navigation bar padding, preventing double-padding.
• When both `showElevationProfile` is false and `analyticsContent == null` in a bottom sheet context (`!useStatusBarsPadding`), `lowerColumn` SHALL defensively provide `Spacer(modifier = Modifier.navigationBarsPadding())`.
2. *Upward Travel & Expansion Height Clearance in LiveSegmentSheet (`SensorGridScreen.kt`)*:
• Because `ElevationProfile`'s `Surface` applies `navigationBarsPadding()`, the total measured content height of `LiveSegmentSheet` naturally increases by `WindowInsets.navigationBars.calculateBottomPadding()`.
• Material 3 `BottomSheetScaffold` SHALL allow the sheet to drag and expand upward by the additional navigation bar height, placing the entire elevation profile and all its controls 100% above the 3-button navigation bar.
3. *Map Detail Popup Alignment in MapScreenWithTrack (`MapScreenWithTrack.kt`)*:
• When expanded, `SegmentOnMapScreen` and `RouteOnMapScreen` within `MapScreenWithTrack.kt` SHALL position the elevation profile above the system navigation bar, ensuring X-axis labels and touch controls remain fully visible and interactive.
4. *Preservation of Core Invariants*:
• Peek baselines (`BottomSheetDesign.PeekHeightSegment`, `PeekHeightRoute`, `PeekHeightLiveSegment`, `PeekHeightKnownLocation`) established in `ATT-1645` remain 100% preserved.
• Edge-to-edge drawing under navigation bars using `MaterialTheme.colorScheme.surface` remains intact.
• Full-screen map views where `useStatusBarsPadding == true` (e.g. `StarredSegmentsScreen.kt`, `RoutesScreen.kt`) continue to render cleanly.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement (`REQ-UI-237`), extending and refining `REQ-UI-047` (*Live Segment Sheet Height Constraint*), `REQ-UI-148` (*Edge-to-Edge System Insets*), `REQ-UI-189` (*Standardized Boundaries & Contours*), `REQ-UI-196` (*Harmonized Spacing & Unified Surface Background*), and `REQ-UI-205` (*Slotted Analytics Content in MapDetailLayout*).
2. *Historical Origin & Commit Trace*: Commit `69286d4e` (`ATT-1393`) introduced `analyticsContent` with `.navigationBarsPadding()`. Commit `f17dc40d` (`ATT-1645`) calibrated dynamic peek baselines.
3. *Root Reason for Existing Formulation*: When `analyticsContent` was added to `MapDetailLayout`, `.navigationBarsPadding()` was placed directly on `analyticsContent`. However, for Segments, Routes, and Live Segments, `analyticsContent` is null, leaving `ElevationProfile` as the terminal component with zero navigation bar padding.
4. *Preservation of Core Invariants*: 100% preservation of edge-to-edge drawing under the navigation bar, existing peek baselines, and full test suite pass rate.

**Acceptance Criteria (Given-When-Then)**:
• *Given* a segment, route, or live segment popup displayed on the map or tracking cockpit,
• *When* the user drags or expands the popup upward to its fully expanded state,
• *Then* the sheet SHALL expand upward sufficiently to reveal the entire elevation profile completely above the Android system navigation bar.
• *Given* an expanded segment, route, or live segment popup on a device configured with 3-button navigation,
• *When* inspecting the bottom of the sheet,
• *Then* zero content, chart canvas, or axis labels SHALL be occluded behind the navigation bar buttons.
• *Given* a segment or route popup in its collapsed/peeked state,
• *When* inspecting the sheet header,
• *Then* the header SHALL remain fully visible above the system navigation bar with `peekHeight = base + navBarHeight`.

**Invariants**: Dynamic self-measuring peek baselines (`ATT-1645`), edge-to-edge surface background, and zero regression in full-screen maps MUST NOT be broken.

### REQ-UI-238: Lieblingsorte/Map: Lock Favorite Location Bottom Sheet to Peek Height & Remove Misleading Drag Handle [Verified]
- **Tests**: `TST-UI-197`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `MapScreenWithTrack.kt`
- **Rationale**: The system SHALL lock the favorite location bottom sheet to its designated peek height, disable upward dragging/swiping gestures when a location is active, reject full-screen expansion, remove the misleading drag handle, and preserve all dismiss and edit interactions (ATT-2042):
1. *Gesture & Swipe Lockout on `BottomSheetScaffold` (`MapScreenWithTrack.kt`)*:
• In `MapScreenWithTrack.kt`, `BottomSheetScaffold` SHALL configure `sheetSwipeEnabled = selectedLocationId == null`.
• When `selectedLocationId != null` (favorite location pin selected), dragging and swiping the sheet up or down SHALL be completely disabled.
• When `selectedLocationId == null` (e.g. segment or route selected), standard swipe gestures SHALL remain enabled.
2. *Programmatic & State Expansion Rejection (`rememberStandardBottomSheetState`)*:
• In `MapScreenWithTrack.kt`, `scaffoldState` SHALL configure `rememberStandardBottomSheetState` with `confirmValueChange`: rejecting transition to `SheetValue.Expanded` whenever `selectedLocationId != null`.
• Any programmatic or fling attempt to expand the sheet to `SheetValue.Expanded` while a favorite location is active SHALL be rejected, keeping the sheet anchored strictly at `BottomSheetDesign.PeekHeightKnownLocation + navBarHeight`.
3. *Drag Affordance Excised & Polished Layout (`KnownLocationOnMapSheet`)*:
• In `KnownLocationOnMapSheet` (in `MapScreenWithTrack.kt`), the misleading `MinimumDragHandle()` component SHALL be removed.
• The root container `Column` SHALL apply clean padding: `padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 12.dp)`.
4. *Interaction & Invariant Parity*:
• Tapping the map canvas outside the sheet or pressing the system back button (`BackHandler`) SHALL continue to collapse and dismiss the card cleanly (`selectedLocationId = null`).
• Tapping the edit icon button SHALL continue to launch `EditKnownLocationDialog` with live geofence adjustment.
• Full-screen upward expansion (`SheetValue.Expanded`) for `SegmentOnMapScreen` and `RouteOnMapScreen` SHALL remain 100% functional with dynamic self-measuring peek heights (`REQ-UI-221`, `REQ-UI-237`).
• 100% 9-language localization parity across all supported application locales.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement (`REQ-UI-238`), refining `REQ-UI-180` (*Display Favorite Locations on Central Navigation Map*) and `REQ-UI-221` (*Standardized Bottom Sheet Peek Height Baselines*) under Epic `ATT-1396` (*Lieblingsorte: Visible Value, Auto-Naming & Tracking Integration*).
2. *Historical Origin & Commit Trace*: Sprint 2026-40.4 (`ATT-1449`, commit `191ba05d`) and Sprint 2026-40.8 (`ATT-1645`, commit `9c3dd09e`).
3. *Root Reason for Existing Formulation*: `KnownLocationOnMapSheet` inherited `MinimumDragHandle()` from the initial bottom sheet template when the central map was unified. The absence of scrollable content in favorite locations makes upward expansion superfluous and visually undesirable.
4. *Preservation of Core Invariants*: Route and segment expansion (`REQ-UI-221`, `REQ-UI-237`), map pin heart markers, geofence radius rendering, dismissal back handlers, edit dialog launches (`REQ-UI-061`), and 9-language localization parity are 100% strictly preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an athlete tapping a favorite location pin on the central navigation map (`MapScreenWithTrack.kt`),
• *When* the bottom info card appears,
• *Then* no drag handle (`MinimumDragHandle`) SHALL be rendered above the title row.
• *Given* the favorite location info card is displayed at peek height,
• *When* the athlete attempts to drag or swipe the card upwards,
• *Then* the sheet SHALL remain locked at peek height and SHALL NOT expand into an empty full-screen view (`confirmValueChange` rejects `SheetValue.Expanded`).
• *Given* `selectedLocationId != null`,
• *When* `BottomSheetScaffold` evaluates `sheetSwipeEnabled`,
• *Then* `sheetSwipeEnabled` SHALL evaluate to `false`.
• *Given* the favorite location info card is displayed,
• *When* the athlete taps the map canvas or presses the system back button,
• *Then* the sheet SHALL collapse and `selectedLocationId` SHALL be reset to `null`.
• *When* the athlete taps the edit icon button,
• *Then* `EditKnownLocationDialog` SHALL open with live radius adjustment.
• *Given* a route or segment selected on the map (`selectedLocationId == null`),
• *When* the user drags the sheet upwards,
• *Then* the sheet SHALL expand smoothly to `SheetValue.Expanded` displaying all charts, telemetry, and leaderboards.

**Invariants**: Dynamic self-measuring peek baselines (`ATT-1645`), route and segment full-screen expansion, back press dismissal, and 9-language localization parity MUST NOT be broken.

### REQ-UI-239: Unconditional Edit Workout Form Presentation & Removal of Edit Dialog Field Toggles from Advanced Settings [Verified]
- **Tests**: `TST-UI-198`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `AdvancedTuningDialog.kt`, `AdvancedTuningAccordion.kt`, `EditWorkoutScreen.kt`, `EditWorkoutViewModel.kt`, `res/values*/strings.xml`
- **Rationale**: The system SHALL present all available workout metadata fields unconditionally inside the Edit Workout dialog (`EditWorkoutScreen.kt`) and remove redundant field toggles from Advanced Settings (`AdvancedTuningDialog.kt`) (ATT-2029):
1. *Clean Advanced Settings (`AdvancedTuningDialog.kt`)*:
• The system SHALL remove Sub-block 2 (*"Training bearbeiten"* / `settings_edit_workout_title`) and its 7 toggle switches (`settings_edit_workout_description`, `settings_edit_workout_cluster`, `settings_edit_workout_commute_trainer`, `settings_edit_workout_race`, `settings_edit_workout_strava`, `settings_edit_workout_goal`, `settings_edit_workout_method`) from `AdvancedTuningDialog.kt`.
• Section 5 of Advanced Settings SHALL focus exclusively on Workout List Card customizations (`WorkoutCardSectionPreferences`).
2. *De-cluttered State & Factory Reset Lifecycle*:
• `AdvancedTuningDialog.kt` SHALL remove local mutable state `editWorkoutPrefs`, DataStore persistence invocation `preferenceManager.setEditWorkoutFieldPreferences()`, and factory reset logic for `EditWorkoutFieldPreferences`.
3. *Unconditional Form Presentation (`EditWorkoutScreen.kt`)*:
• In `EditWorkoutScreen.kt`, all standard workout metadata fields SHALL be rendered unconditionally: Core Anchors (Workout Name, Sport Type dropdown, Equipment dropdown) and Metadata Attributes (Route / Cluster selector, Commute checkbox, Trainer checkbox, Race checkbox, Description multiline field, Goal field, and Method field).
• The Strava upload checkbox SHALL remain conditionally rendered solely based on whether the Strava community connection is active (`TrainingApplication.uploadToCommunity(FileFormat.STRAVA)`).
• `EditWorkoutViewModel` SHALL be decoupled from `EditWorkoutFieldPreferences`.
4. *Active Cards Subtitle Formatting & 100% 9-Language Parity*:
• `TuningSubtitleFormatter.formatWorkoutMasksSubtitle` SHALL accept only `cardPrefs: WorkoutCardSectionPreferences, context: Context` and format the subtitle based on the count of active cards.
• String resource `tuning_summary_masks_cards_format` SHALL be standardized to `"%1$d/8 Cards"` / `"%1$d/8 Karten"` across all 9 supported language directories (`values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`).
5. *Invariants*:
• Saving workouts from `EditWorkoutScreen` MUST preserve all entered data without corruption.
• The 8 Workout List Card section customization toggles (`WorkoutCardSectionPreferences`) in Advanced Settings MUST remain 100% operational.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement (`REQ-UI-239`), superseding `REQ-UI-211` (*Configurable Fields in Edit Workout Dialog*) and refining `REQ-UI-216` (*Relocation of Aftermath Customization Toggles to Advanced Settings*), targeting `EditWorkoutScreen.kt`, `AdvancedTuningDialog.kt`, and `AdvancedTuningAccordion.kt`.
2. *Historical Origin & Commit Trace*: Commit `4d8961ca` (`ATT-1713`), commit `2965ceb1` (`ATT-1815`), and commit `30b42d79` (`ATT-2005`).
3. *Root Reason for Existing Formulation*: The author originally assumed athletes wanted to customize and shrink the Edit Workout form to avoid vertical scrolling. However, athlete feedback demonstrated that hiding fields in an editing dialog creates confusion when users seek to edit notes, goals, or clusters. A data editing modal should naturally expose all editable properties.
4. *Preservation of Core Invariants*: Core anchor fields (Name, Sport, Equipment) and optional fields remain rendered; Strava upload remains conditionally rendered based on active community connection; Workout List Cards toggles remain 100% functional.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an athlete in Settings -> Expert / Advanced Settings,
• *When* scrolling to Section 5 ("Workout-Karten & Eingabemasken" / `tuning_cat_workout_masks_cards`),
• *Then* the section SHALL only render Workout List Card customization switches and SHALL NOT display the "Training bearbeiten" header or any edit dialog field toggles.
• *Given* an athlete tapping the Edit icon on any workout in the summaries list or map detail view,
• *When* the Edit Workout bottom sheet opens,
• *Then* all standard attributes (Name, Route Cluster, Sport, Equipment, Commute, Trainer, Race, Description, Goal, Method) SHALL be visible and editable unconditionally.
• *Given* an athlete configuring workout card toggles in Advanced Settings,
• *When* 8 of 8 card sections are enabled,
• *Then* the Section 5 accordion header subtitle SHALL display "8/8 Cards" (or localized equivalent in the active locale).
• *Given* modified settings in Advanced Settings,
• *When* the athlete taps "Reset to Factory Defaults",
• *Then* all preferences reset without references to or errors from `EditWorkoutFieldPreferences`.

**Invariants**: Core fields permanently visible, saving with unedited fields preserves database values, workout list card section toggles preserved, 9-language localization parity MUST NOT be broken.

### REQ-UI-240: Settings/Aftermath: Matrix Table Presentation for Configurable Aftermath Sections (Workout Summary List vs. Workout Details) [Verified]
- **Tests**: `TST-UI-199`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `AdvancedTuningDialog.kt`, `AdvancedTuningAccordion.kt`, `MyPreferenceManager.kt`, `TrackOnMapScreen.kt`, `res/values*/strings.xml`
- **Rationale**: The system SHALL provide independent section configuration for the Workout Summary List (`WorkoutSummary.kt`) and the Workout Details inspection screen (`TrackOnMapScreen.kt` / `MapDetailLayout.kt`), presented as a 2-Column Matrix Table in Advanced Settings (`AdvancedTuningDialog.kt`) (ATT-2030):
1. *Separated Preferences Architecture (`MyPreferenceManager.kt`)*:
• The system SHALL maintain two decoupled preference data models: `WorkoutListCardPreferences` (aliased to `WorkoutCardSectionPreferences` for backward compatibility) and `WorkoutDetailPreferences`.
• In `WorkoutListCardPreferences`, `showElevationProfile`, `showTelemetryCharts`, and `showZoneAnalysis` SHALL default to `false` to safeguard 60/120 FPS list scrolling; core summary fields (`showDescription`, `showExtrema`, `showLaps`, `showStrava`, `showMapPreview`) SHALL default to `true`.
• In `WorkoutDetailPreferences`, all 8 attributes (`showDescription`, `showExtrema`, `showLaps`, `showStrava`, `showMap`, `showElevationProfile`, `showTelemetryCharts`, `showZoneAnalysis`) SHALL default to `true`.
• `MyPreferenceManager` SHALL expose `workoutCardPreferencesFlow` and `workoutDetailPreferencesFlow`, along with corresponding atomic setters `setWorkoutCardPreferences` and `setWorkoutDetailPreferences`.
2. *DataStore Migration & Fallback Rules*:
• Existing DataStore keys for `workout_card_show_*` SHALL be preserved without mutation.
• For `WorkoutDetailPreferences`, if `workout_detail_show_*` keys are unset, `showMap`, `showElevationProfile`, `showTelemetryCharts`, and `showZoneAnalysis` SHALL default to `true`; `showDescription`, `showExtrema`, `showLaps`, and `showStrava` SHALL inherit from their respective `workout_card_show_*` values if explicitly configured, or default to `true`.
3. *2-Column Matrix Table UI (`AdvancedTuningDialog.kt`)*:
• In Section 5 (*"Trainingsliste & Details"* / `tuning_cat_workout_masks_cards`), the system SHALL render a 2-Column Matrix Table with header row (`tuning_matrix_feature`, `tuning_matrix_col_list`, `tuning_matrix_col_details`) and 8 feature rows.
• All checkboxes SHALL meet Material 3 touch target standards (`minimumInteractiveComponentSize()` / 48x48dp minimum bounds).
• Feature column labels SHALL support flexible weighting (`weight(1f)`) and text wrap for localization.
4. *Accordion Subtitle Formatting & 100% 9-Language Parity*:
• `TuningSubtitleFormatter` SHALL format the Section 5 accordion subtitle via `R.string.tuning_summary_matrix_format` reflecting dual active counts (`"%1$d/8 Liste, %2$d/8 Details"` / `"%1$d/8 List, %2$d/8 Details"`).
• All new string resources SHALL be defined across all 9 supported locales.
5. *Preservation of Core Invariants*:
• List card rendering in `WorkoutSummary.kt` continues consuming list preferences without regression.
• Detail screen (`TrackOnMapScreen.kt` / `MapDetailLayout.kt`) gates map, elevation, charts, zones, laps, extrema, and description according to `WorkoutDetailPreferences`.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement (`REQ-UI-240`), extending `REQ-UI-210` (Workout Card Sections) and `REQ-UI-216` (Advanced Settings Integration) under Epic `ATT-111` (*Aftermath: Compact Post-Workout Visual Analytics & Graphs*).
2. *Historical Origin & Commit Trace*: Sprint 2026-40.6 (`ATT-1714`), Sprint 2026-40.7 (`ATT-1815`), and Sprint 2026-40.12 (`ATT-2029`).
3. *Root Reason for Existing Formulation*: Aftermath section toggles previously governed only the scrolling list cards, confusing athletes who expected to customize both list cards and the full-screen details view, or forcing heavy graphs onto list cards to see them anywhere.
4. *Preservation of Core Invariants*: Existing DataStore keys (`workout_card_show_*`) remain functional; 60/120 FPS list scrolling performance is preserved; 9-language localization parity is strictly maintained.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an athlete navigating to Settings -> Expert / Advanced Settings,
• *When* opening Section 5 (*Trainingsliste & Details*),
• *Then* the section SHALL display a 2-column matrix table with "In Liste" and "In Details" columns and 8 feature rows.
• *Given* a fresh installation or factory reset,
• *When* inspecting the matrix table,
• *Then* the List column SHALL enable Description, Extrema, Laps, Strava, and Map while leaving Elevation, Telemetry, and Zones OFF; and the Details column SHALL enable all 8 features.
• *Given* an athlete modifying checkboxes in either column,
• *When* saving settings and returning to Workout Summary List and Workout Details,
• *Then* each view SHALL reflect its configured preferences independently.
• *Given* modified settings in Advanced Settings,
• *When* tapping "Reset to Factory Defaults",
• *Then* both models SHALL revert to their respective defaults atomically in DataStore and UI state.
• *Given* any of the 9 supported locales,
• *When* viewing the matrix table and accordion subtitle,
• *Then* all text SHALL be 100% localized, text SHALL not be clipped, and touch targets SHALL satisfy 48x48dp minimum bounds.

**Invariants**: Existing DataStore keys preserved, 60/120 FPS list scrolling protected, independent view customization, 9-language localization parity MUST NOT be broken.

### REQ-UI-241: Aftermath/Scrubbing: Persistent Floating Scrubbing Telemetry Badge Pinned Above Scrollable Graphs in MapDetailLayout [Verified]
- **Tests**: `TST-UI-200`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `MapDetailLayout.kt`, `ElevationProfile.kt`
- **Rationale**: The system SHALL hoist `ScrubbingTelemetryBadge` from within the internal scrollable `ElevationProfile.kt` component into the persistent lower viewport container of `MapDetailLayout.kt`, ensuring that instantaneous telemetry readouts remain pinned, elevated, and fully visible at the top of the graph pane during active scrubbing regardless of vertical scroll offset (ATT-2016):
1. *Container Viewport Overlay Placement (`MapDetailLayout.kt`)*:
• In `MapDetailLayout.kt`, the lower content section (whether `showMap && hasScrollableContent` or `!showMap && hasScrollableContent`) SHALL be wrapped in a viewport `Box` positioned immediately below the sticky `GlobalTelemetryZoomToolbar` / `SplitPaneDivider`.
• The vertically scrollable `lowerColumn` (`Modifier.fillMaxSize().verticalScroll(...)`) SHALL reside inside this `Box`.
• An overlay `ScrubbingTelemetryBadge` SHALL be anchored at `Alignment.TopCenter` with `padding(top = 4.dp)` within this `Box`.
• The overlay badge SHALL render IF AND ONLY IF `showZoomControls == true`, `selectedDistance != null`, and `activeScrubPoint != null`.
• When `selectedDistance == null`, the badge SHALL dismiss cleanly without layout jitter.
2. *Universal Cross-Graph Scrubbing Ingestion*:
• When touching, scrubbing, or holding any visible graph (`ElevationProfile`, Speed/Pace `TelemetryMetricGraph`, Heart Rate `TelemetryMetricGraph`, or Cycling Power `TelemetryMetricGraph`), `selectedDistance` updates SHALL resolve the active `PathPoint` and interpolated altitude `activeScrubAltitude`.
• The pinned badge SHALL reactively display instantaneous Distance, Time, Altitude, Grade/Slope, Heart Rate (with zone), Power (with zone), and Speed/Pace.
3. *Elevation-Independent & Trackless Telemetry Support*:
• When `showElevationProfile == false` (e.g. toggled off via `WorkoutDetailPreferences`), scrubbing across the remaining telemetry graphs (Speed, HR, Power) SHALL still display the pinned `ScrubbingTelemetryBadge`.
• For trackless workouts (`(path.lastOrNull()?.distance ?: 0.0) == 0.0`), `selectedDistance` represents elapsed seconds; the badge SHALL format elapsed time on the primary tracking row and display HR/Power telemetry along the time axis.
4. *ElevationProfile Decoupling & Backward Compatibility (`ElevationProfile.kt`)*:
• `ElevationProfile` SHALL accept `showScrubbingBadge: Boolean = true` in both composable overloads.
• In `MapDetailLayout.kt`, `ElevationProfile` SHALL be supplied with `showScrubbingBadge = false` to eliminate redundant duplicate badges when `ElevationProfile` is at the top of the scroll container.
• Standalone invocations of `ElevationProfile` (where `showScrubbingBadge == true`) SHALL retain their internal badge rendering, preserving 100% backward compatibility and test invariants.
5. *Layering & Touch Transparency*:
• The pinned `ScrubbingTelemetryBadge` overlay SHALL NOT consume or intercept horizontal drag gestures meant for graph scrubbing or vertical scroll gestures meant for `lowerColumn`.
6. *Preservation of Core Invariants*:
• Directional gesture disambiguation (`REQ-UI-226`), single-finger vertical scroll freedom, synchronized cross-domain scrubbing (`REQ-UI-233`), and 9-language localization parity MUST NOT be broken.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement (`REQ-UI-241`), extending and refining `REQ-UI-201`, `REQ-UI-206`, and `REQ-UI-225` under Epic `ATT-111` (*Compact Post-Workout Visual Analytics & Graphs*).
2. *Historical Origin & Commit Trace*: Sprint 2026-40.5 (Commit `e7a6a273`, `ATT-1391`), Sprint 2026-40.6 (Commit `061a4b35`, `ATT-1740`), Sprint 2026-40.8 (Commit `d71b4028`, `ATT-1876`).
3. *Root Reason for Existing Formulation*: `ScrubbingTelemetryBadge` was originally embedded in `ElevationProfile` because it was the only chart component. Decoupling zoom buttons in `REQ-UI-225` proved the value of hoisting sticky controls to `MapDetailLayout.kt`. Hoisting the scrubbing badge to `MapDetailLayout.kt` is the logical continuation of this decoupled viewport architecture.
4. *Preservation of Core Invariants*: ElevationProfile curve drawing geometry and top padding (44.dp / 16.dp), `ElevationProfileLayoutTest.kt` compatibility, multi-chart lockstep zooming, panning, and synchronized cursor tracking, directional vertical scroll disambiguation (`REQ-UI-226`), and 9-language localization parity are 100% preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an athlete viewing post-workout graphs in `MapDetailLayout`,
• *When* the athlete scrolls the graph container so `ElevationProfile` is scrolled off-screen,
• *Then* the scrubbing telemetry badge SHALL remain anchored and visible at the top of the graph viewport whenever scrubbing is active (`selectedDistance != null`).
• *Given* the athlete touches or scrubs the Speed/Pace, Heart Rate, or Power graph,
• *When* dragging the scrub pointer horizontally,
• *Then* the pinned telemetry badge SHALL update in real time with instantaneous Distance, Time, Altitude, Slope, and metric values.
• *Given* the pinned badge overlay,
• *When* dragging or scrubbing over the chart area,
• *Then* the badge SHALL NOT occlude the map divider or global zoom controls and SHALL NOT swallow chart drag or vertical scroll events.
• *Given* a workout where `showElevationProfile == false`,
• *When* scrubbing across any active telemetry graph (Speed/Pace, Heart Rate, Power),
• *Then* the pinned telemetry badge SHALL render and display telemetry data.
• *Given* the athlete releases their finger from the graph,
• *When* `selectedDistance` becomes null,
• *Then* the pinned badge SHALL be dismissed cleanly without layout jitter.

**Invariants**: Directional gesture disambiguation (`REQ-UI-226`), horizontal padding parity (50dp/25dp), lockstep multi-chart synchrony, and 9-language localization parity MUST NOT be broken.

### REQ-UI-242: Aftermath/Scrubbing: Render Training Zone Badge Suffix in Active Zone Color in Scrubbing Telemetry Badge [Verified]
- **Tests**: `TST-UI-201`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `ElevationProfile.kt`
- **Rationale**: The system SHALL render instantaneous training zone suffixes in their active zone color within `ScrubbingTelemetryBadge` (`ElevationProfile.kt`) across HR zones 1..5 and all 7 Coggan power zones 1..7 (`TelemetryZoneMath.ZONE_COLORS.size`) (ATT-2015, amended by ATT-5070):
1. *Separated Metric Value & Dynamic Zone Suffix*:
• In `ScrubbingTelemetryBadge` (`ElevationProfile.kt`), when Heart Rate (`hasHr`) or Cycling Power (`hasPower`) is displayed:
- The base numeric value and unit (e.g. `${point.hr} bpm`, `${point.power} W`) SHALL be rendered in high-contrast neutral text color (`MaterialTheme.colorScheme.onSurface`).
- When an active zone (`hrZone in 1..5`, `powerZone in 1..7` / `1..TelemetryZoneMath.ZONE_COLORS.size`) is resolved from thresholds via `TelemetryZoneMath`, the zone suffix (` • Z$hrZone`, ` • Z$powerZone`) SHALL be rendered directly in that zone's color using `TelemetryZoneMath.ZONE_COLORS[zone - 1]`: Zone 1: `TTColor.Zone1` .. Zone 7: `TTColor.Zone7`.
- The zone suffix text SHALL be styled with `FontWeight.Bold` for optimal readability.
2. *Inline Annotated String Representation*:
• The base metric and zone suffix SHALL be combined within an `AnnotatedString` (`buildAnnotatedString`), ensuring single-line inline layout without baseline jitter, multi-line wrapping, or unexpected layout reflows during continuous scrubbing.
3. *Neutral Graceful Fallback*:
• If zone thresholds are not configured or zone resolution evaluates to `null` (or outside `1..5` for HR or `1..7` for Power), the composable SHALL render only the base metric value (e.g. `145 bpm`, `220 W`) in `MaterialTheme.colorScheme.onSurface` without trailing delimiters (`•`) or dangling characters.
4. *Preservation of Core Invariants*:
• Speed/Pace metric styling (`MaterialTheme.colorScheme.primary`) SHALL remain unchanged.
• `ScrubbingTelemetryBadge` public signature, parameters (`hrZoneThresholds: HeartRateZoneThresholds? = null`, `powerZoneThresholds: PowerZoneThresholds? = null`), and placement invariants SHALL remain 100% backward compatible.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement (`REQ-UI-242`), refining `REQ-UI-201`, `REQ-UI-206`, `REQ-UI-230`, and `REQ-UI-241` under Epic `ATT-111` (*Compact Post-Workout Visual Analytics & Graphs*). Amends `ElevationProfile.kt` (`ScrubbingTelemetryBadge`).
2. *Historical Origin & Commit Trace*: Sprint 2026-40.5 (Commit `e7a6a273`, `ATT-1391`), Sprint 2026-40.7 (Commit `d71b4028`, `ATT-1876`), and Sprint 2026-40.12 (Commit `0064fba1`, `ATT-2016`). Refined in Sprint 2026-45.1 (`ATT-5070`).
3. *Root Reason for Existing Formulation*: In `ATT-2015`, when heart rate and power metrics were added to `ScrubbingTelemetryBadge`, the 5-zone range `in 1..5` was copied from Heart Rate to Power, inadvertently suppressing zone suffixes for Coggan power zones 6 and 7 (`Z6` and `Z7`).
4. *Preservation of Core Invariants*: Neutral base text styling, dynamic zone coloring via `TelemetryZoneMath.ZONE_COLORS`, single-line layout, and 9-language localization parity are 100% strictly preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an athlete viewing a workout with heart rate or power telemetry and configured zone thresholds,
• *When* scrubbing across points in HR Zones 1..5 or Power Zones 1..7,
• *Then* the `• Z<n>` suffix SHALL be rendered in the corresponding `TTColor.Zone<n>` color (`TelemetryZoneMath.ZONE_COLORS[n - 1]`) in bold.
• *Given* any scrubbed telemetry point with HR or Power,
• *When* rendered in `ScrubbingTelemetryBadge`,
• *Then* the numeric value and unit SHALL be rendered in `MaterialTheme.colorScheme.onSurface`.
• *Given* a telemetry point where zone thresholds are null or zone calculation yields null,
• *When* rendered in `ScrubbingTelemetryBadge`,
• *Then* only the base value SHALL be shown in `MaterialTheme.colorScheme.onSurface` without trailing delimiters or miscolored text.

**Invariants**: Single-line badge rendering, zero baseline jitter, WCAG contrast accessibility, and 9-language localization parity MUST NOT be broken.

### REQ-UI-243: Aftermath/Graphs: Configurable Minimum Pace Ceiling in Expert Settings with Athletic Default (3:00 min/km) [Verified]
- **Tests**: `TST-UI-202`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `TuningPreferencesDataStore.kt`, `AdvancedTuningDialog.kt`, `TelemetryMetricGraph.kt`, `MapDetailLayout.kt`, `values*/strings.xml`
- **Rationale**: The system SHALL provide a configurable minimum pace ceiling (fastest pace boundary) in Expert Settings and enforce it across Aftermath running pace telemetry graphs (`TelemetryMetricGraph.kt`, `TuningPreferencesDataStore.kt`) (ATT-2014):
1. *Preferences Persistence & Model (`TuningPreferencesDataStore.kt`)*:
• The system SHALL declare `DEFAULT_PACE_CEILING_MIN_KM = 3.0f` (3:00 min/km), `MIN_PACE_CEILING_MIN_KM = 2.0f` (2:00 min/km), and `MAX_PACE_CEILING_MIN_KM = 6.0f` (6:00 min/km) in `TuningPreferencesDefaults`.
• `TuningConfig` SHALL include `val paceCeilingMinKm: Float = TuningPreferencesDefaults.DEFAULT_PACE_CEILING_MIN_KM`.
• `TuningPreferencesDataStore` SHALL persist this preference with key `KEY_PACE_CEILING_MIN_KM = floatPreferencesKey("tuning_pace_ceiling_min_km")`, clamping values within `[2.0f, 6.0f]`.
• `resetToDefaults()` SHALL restore `paceCeilingMinKm` to `DEFAULT_PACE_CEILING_MIN_KM` (3.0f).
2. *Expert Settings UI (`AdvancedTuningDialog.kt`)*:
• In Section 4 (*"Aftermath & Analyse"* / `tuning_cat_aftermath`), the system SHALL render a `TuningSliderItem` with title `tuning_pace_ceiling_title`, description `tuning_pace_ceiling_desc`, `valueRange = 2.0f..6.0f`, and `steps = 15` (15-second intervals).
• The slider value and default text SHALL format with active units (`min/km` in Metric, `min/mi` in Imperial).
3. *Telemetry Pace Graph Clamping & Y-Axis Bounding (`TelemetryMetricGraph.kt`)*:
• `TelemetryMetricGraph` SHALL declare parameter `paceCeilingMinKm: Float = TuningPreferencesDefaults.DEFAULT_PACE_CEILING_MIN_KM`.
• In `extractMetricValue` for `PACE`, points faster than `effectiveCeiling` (where `effectiveCeiling = if (unit == MyUnits.METRIC) paceCeilingMinKm.toDouble() else paceCeilingMinKm.toDouble() * (BANALService.METER_PER_MILE / 1000.0)`) SHALL be clamped to `effectiveCeiling`: `(secPerUnit / 60.0).coerceIn(effectiveCeiling, 20.0)`.
• In `calculateBounds` for `PACE`, `paceMin = (min * 0.95).coerceAtLeast(effectiveCeiling)`. The top Y-axis label SHALL NEVER exceed `effectiveCeiling`, completely eliminating the 1:30 min/km artifact.
4. *Layout Integration (`MapDetailLayout.kt`)*:
• `MapDetailLayout` SHALL collect `tuningConfig` and forward `paceCeilingMinKm = tuningConfig.paceCeilingMinKm` to `TelemetryMetricGraph`.
5. *Preservation of Core Invariants*:
• Inverted pace plotting (faster higher), `formatPaceMinutes` mm:ss formatting, pause filtering (<0.55 m/s), non-running speed handling, and 9-language localization parity MUST NOT be broken.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement (`REQ-UI-243`), refining and parameterizing `REQ-UI-219` (*Aftermath/Graphs: Robust Running Pace Decoding, Stopped-Speed Clamping, and mm:ss Y-Axis Pace Formatting*) and `REQ-UI-206` under Epic `ATT-111` (*Compact Post-Workout Visual Analytics & Graphs*).
2. *Historical Origin & Commit Trace*: Commit `7a8c4d21` (`ATT-1818`, Sprint 2026-40.7) introduced `REQ-UI-219` with a hardcoded `1.5` min/km pace bound.
3. *Root Reason for Existing Formulation*: In `ATT-1818`, the author clamped pace to `[1.5, 20.0]` (1:30 min/km) to prevent division-by-zero on extreme GPS spikes. However, 1:30 min/km is an athletically impossible running speed that squashes the readable dynamic range of typical distance running curves (4:30 – 6:30 min/km) and displays an unsightly `1:30` label on the top Y-axis.
4. *Preservation of Core Invariants*: Inverted pace axis plotting (faster pace higher, slower pace lower), `mm:ss` label formatting (`formatPaceMinutes`), stopped/pause filtering (<0.55 m/s), metric vs. imperial unit conversion, synchronized multi-chart scrubbing (`selectedDistance`), and 9-language translation parity are 100% preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an athlete in Settings -> Expert Settings,
• *When* expanding Section 4 (Aftermath & Profile Analytics),
• *Then* a slider for "Minimum Pace Ceiling" ("Schnellste Tempo-Grenze") SHALL be visible and interactive.
• *Given* a fresh install or unconfigured state,
• *When* querying the preference,
• *Then* the default pace ceiling SHALL be exactly 3:00 min/km (3.0 decimal minutes).
• *Given* a running workout with GPS noise spikes faster than the configured ceiling (e.g. 1:15 or 1:30 min/km),
• *When* rendering the Tempo graph in Aftermath,
• *Then* the top Y-axis label SHALL NOT exceed the configured ceiling (e.g. 3:00 min/km), and 1:30 min/km SHALL never be shown.
• *Given* Imperial units are enabled (`MyUnits.IMPERIAL`),
• *When* viewing and setting the preference,
• *Then* the value SHALL be formatted and clamped in min/mile consistently (~4:50 min/mi for 3:00 min/km).
• *Given* any of the 9 supported locales,
• *When* browsing Expert Settings,
• *Then* the preference title, summary, and default text SHALL be 100% localized.

**Invariants**: Inverted pace plotting, mm:ss label formatting, paused speed filtering, and 9-language localization parity MUST NOT be broken.

### REQ-UI-244: Lieblingsorte/UI: Harmonize Map Preview Thumbnail Dimensions to 100dp on KnownLocationCard [Verified]
- **Tests**: `TST-UI-203`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `KnownLocationsScreen.kt`, `KnownLocationCardLayoutTest.kt`
- **Rationale**: The system SHALL harmonize the map preview thumbnail dimensions on `KnownLocationCard` to 100dp x 100dp (`ATT-2051`):
1. *Thumbnail Sizing Harmonization (`KnownLocationsScreen.kt`)*:
• `KnownLocationThumbnailMap` SHALL render its root `Surface` container with dimensions `Modifier.size(100.dp)` (increasing from 80dp to 100dp).
• Corner radius SHALL remain `RoundedCornerShape(12.dp)`.
• Test tag `location_map_preview_${item.id}` SHALL be preserved.
• KDoc SHALL be updated to document the 100dp square dimension.
2. *Visual Parity & Invariant Preservation*:
• Google Maps lite mode (`liteMode(true)`), gesture isolation, dark/light theme styling, anti-flash overlay, heart pin marker, circular geofence boundary, and offline `LocalInspectionMode.current` fallback MUST remain intact.
• Left-column layout (`Modifier.weight(1f)`) containing the title, altitude row, and `FlowRow` badge container (Starts and Routes) MUST adapt gracefully to the 100dp thumbnail without text clipping or layout overflow.
• Card interactions (single-tap edit, long-press delete context menu `REQ-UI-061`, starts drill-down `onShowWorkouts`, routes drill-down `onShowRoutes`, and map thumbnail tap `onShowOnMap`) MUST remain 100% operational.
3. *Structural Contract Test Parity (`KnownLocationCardLayoutTest.kt`)*:
• `KnownLocationCardLayoutTest.kt` SHALL be updated to assert `.size(100.dp)` instead of `.size(80.dp)`.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement (`REQ-UI-244`), refining `REQ-UI-217` (*Thumbnail Map Preview on KnownLocationCard*) under Epic `ATT-355` (*Good and consistent UI*).
2. *Historical Origin & Commit Trace*: Sprint 2026-40.7 (`ATT-1813`, commit `b24a3c20`) introduced `REQ-UI-217` with `Modifier.size(80.dp)`. Route cluster cards (`WorkoutClusterComponents.kt`) render at `Modifier.size(100.dp)` per `REQ-UI-218`.
3. *Root Reason for Existing Formulation*: In `ATT-1813`, 80dp was selected without comparison to route cluster cards. The 20dp discrepancy created an uneven visual rhythm across peer spatial list cards.
4. *Preservation of Core Invariants*: Zoom bounds `[10.5f, 12.5f]`, 12dp rounded corners, lite mode, offline inspection fallback, edit/delete interactions, and 9-language localization parity are 100% strictly preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an athlete browsing "Lieblingsorte" (`KnownLocationsScreen`),
• *When* viewing any favorite location card,
• *Then* the map preview thumbnail SHALL render with dimensions of exactly 100dp x 100dp with 12dp rounded corners.
• *Given* favorite location cards and route cluster cards,
• *When* inspected across screens,
• *Then* both embedded map preview thumbnails SHALL share identical 100dp square dimensions.
• *Given* the visual contract test suite (`KnownLocationCardLayoutTest.kt`),
• *When* executed,
• *Then* the test SHALL assert `.size(100.dp)` and pass 100%.

**Invariants**: Zoom math invariants (`[10.5f, 12.5f]`), single-tap edit, long-press delete (`REQ-UI-061`), starts/routes drill-downs, and 9-language localization parity MUST NOT be broken.

### REQ-UI-245: Aftermath/Details: Upper Metadata Slot Architecture & Reactive Detail Section Rendering in MapDetailLayout [Verified]
- **Tests**: `TST-UI-204`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `MapDetailLayout.kt`, `TrackOnMapScreen.kt`
- **Rationale**: The system SHALL provide an upper metadata slot positioned immediately below the header and above the map in `MapDetailLayout.kt`, routing workout description and extrema cards for immediate visibility matching the section sequence of `WorkoutSummary.kt` (ATT-2112 Revision 2):
1. *Upper Metadata Slot in `MapDetailLayout.kt`*:
• `MapDetailLayout` SHALL declare an optional slotted composable parameter `metadataContent: (@Composable ColumnScope.() -> Unit)? = null`.
• When `metadataContent` is non-null, it SHALL be rendered in a `Surface` container immediately beneath `header()` in the top Column and above `BoxWithConstraints` (the map container).
• `metadataContent` SHALL NOT be rendered in `lowerColumn`, preventing duplicate card rendering.
2. *Component Routing in `TrackOnMapScreen.kt`*:
• `TrackOnMapScreen` SHALL route `WorkoutDescription` (gated by `activeDetailPrefs.showDescription`) and `WorkoutExtrema` (gated by `activeDetailPrefs.showExtrema`) into the `metadataContent` slot of `MapDetailLayout`.
• `TrackOnMapScreen` SHALL retain `HeartRateZoneDistributionCard`, `PowerZoneDistributionCard`, `LapSplitVisualizerCard`, and `StravaActivitySection` in `analyticsContent` (below telemetry charts).
3. *Content Guard & Empty-State Resilience*:
• `WorkoutDescription` SHALL only render when at least one field (`description`, `goal`, or `method`) is non-blank.
• `WorkoutExtrema` SHALL only render when `workoutData.extremaData.dataRows.isNotEmpty()`.
4. *Visual Order Parity Invariant*:
• In Detailed Workout (`TrackOnMapScreen`), the section sequence SHALL be: Header -> Description -> Extrema -> Map -> Telemetry Graphs -> Analytics Cards, matching `WorkoutSummary.kt`.
5. *Preservation of Core Invariants*:
• Non-aftermath `MapDetailLayout` callers without `metadataContent` (`RouteOnMapScreen`, `SegmentOnMapScreen`) remain 100% backward compatible.
• Scrubbing gestures, zoom toolbar operations, and map gestures MUST NOT be intercepted or degraded.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement (`REQ-UI-245`), refining `REQ-UI-240` (*Settings/Aftermath: Matrix Table Presentation for Configurable Aftermath Sections*) and `REQ-UI-241` (*Persistent Floating Scrubbing Telemetry Badge*) under Epic `ATT-111` (*Compact Post-Workout Visual Analytics & Graphs*).
2. *Historical Origin & Commit Trace*: Sprint 2026-40.12 (`ATT-2030`, commit `e1dc8d1e`) decoupled detail screen preferences from list card preferences. In `ATT-2112` iteration 1, metadata was placed in `lowerColumn`.
3. *Root Reason for Existing Formulation*: In iteration 1, `metadataContent` was placed in `lowerColumn` below the map. The human reviewer rejected this in Sprint Review because `WorkoutSummary.kt` places Description and Extrema above the map.
4. *Preservation of Core Invariants*: Backward compatibility for callers with default `metadataContent = null`, gesture transparency, zoom and scrub synchronization, and 9-language translation parity are 100% strictly preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an athlete opens a workout with notes or extrema in the Detailed Workout view,
• *When* the screen renders,
• *Then* `WorkoutDescription` and `WorkoutExtrema` SHALL appear above the map, immediately following `WorkoutHeader`.
• *Given* an athlete disables "Beschreibung & Notizen" or "Extremwerte" in Advanced Settings Section 5 ("In Details" column),
• *When* viewing the detailed workout,
• *Then* the disabled section SHALL be completely omitted from the layout.
• *Given* route inspection (`RouteOnMapScreen`) or segment inspection (`SegmentOnMapScreen`),
• *When* invoking `MapDetailLayout` with default `metadataContent = null`,
• *Then* the layout SHALL render cleanly with zero blank spacing or layout regression.

**Invariants**: Callers with default `metadataContent = null` remain backward compatible; scrubbing gestures, zoom toolbar operations, and map gestures MUST NOT be intercepted or degraded; 100% clean-room test suite pass rate.

### REQ-UI-246: Aftermath/Scrubbing: Align Scrubbing Telemetry Badge with Zoom Controls and Offset to Top-Right in MapDetailLayout [Verified]
- **Tests**: `TST-UI-205`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `MapDetailLayout.kt`
- **Rationale**: The system SHALL co-locate `ScrubbingTelemetryBadge` alongside the zoom controls in `MapDetailLayout.kt`, aligning its top edge with the zoom buttons and offsetting it to the top-right (`ATT-2113`):
1. *Unified Lower Viewport Container (`MapDetailLayout.kt`)*:
• The lower viewport `Box` (governed by `Modifier.weight(1f - splitFraction)` when `showMap && hasScrollableContent`, or `Modifier.weight(1f)` when `!showMap && hasScrollableContent`) SHALL encapsulate both `GlobalTelemetryZoomToolbar` (if `hasZoomToolbar`) and the scrollable `lowerColumn` within an internal `Column(modifier = Modifier.fillMaxSize())`.
• `lowerColumn` SHALL occupy the remaining vertical height with `Modifier.weight(1f).fillMaxWidth().verticalScroll(...)`.
2. *Top-Right Scrubbing Badge Placement (`scrubbingOverlay`)*:
• The persistent overlay `scrubbingOverlay` SHALL anchor `ScrubbingTelemetryBadge` to `Alignment.TopEnd` within the shared viewport `Box`.
• The badge SHALL apply `padding(top = 2.dp, end = 8.dp)`, ensuring its top boundary aligns ($y = 2\text{dp}$) with the top edge of the 28dp zoom controls ($y = 4\text{dp}$) centered in the 36dp toolbar.
3. *Scrubbing Visibility & Touch Transparency*:
• The badge SHALL render IF AND ONLY IF `showZoomControls == true`, `selectedDistance != null`, and `activeScrubPoint != null`.
• The badge overlay SHALL NOT consume or intercept drag gestures on the graphs or tap gestures on the zoom toolbar buttons.
4. *Preservation of Core Invariants*:
• Multi-chart lockstep zooming, panning, and synchronized cursor tracking (`REQ-UI-225`, `REQ-UI-233`) remain intact.
• Dynamic zone colors in active zone badge suffixes (`REQ-UI-242`) remain intact.
• Trackless workout scrubbing along the time axis remains intact.
• 9-language translation parity remains intact.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement (`REQ-UI-246`), refining `REQ-UI-241` (*Persistent Floating Scrubbing Telemetry Badge Pinned Above Scrollable Graphs in MapDetailLayout*) under Epic `ATT-111` (*Compact Post-Workout Visual Analytics & Graphs*).
2. *Historical Origin & Commit Trace*: Sprint 2026-40.12 (Commit `0064fba1`, `ATT-2016`) originally hoisted `ScrubbingTelemetryBadge` to `MapDetailLayout.kt` with `Alignment.TopCenter` and `padding(top = 4.dp)` inside a box below `GlobalTelemetryZoomToolbar`.
3. *Root Reason for Existing Formulation*: In `ATT-2016`, the author centered the badge because chart components historically centered overlays. However, this forced the badge 36dp down into the chart viewport and occluded upper graph curves, leaving the right side of the zoom toolbar completely vacant.
4. *Preservation of Core Invariants*: Floating overlay persistence, universal scrubbing across elevation/speed/HR/power, trackless workout support, active zone coloring (`REQ-UI-242`), and 9-language localization parity are 100% strictly preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an athlete scrubbing any post-workout graph in the detailed inspection view,
• *When* `ScrubbingTelemetryBadge` renders,
• *Then* its top edge SHALL align with the top edge of the zoom controls in `GlobalTelemetryZoomToolbar` ($y \approx 2\text{--}4\text{dp}$), rather than appearing 36dp below them.
• *Given* an athlete scrubbing any post-workout graph,
• *When* the badge appears,
• *Then* it SHALL be positioned on the right side of the screen (`Alignment.TopEnd`), leaving the left-aligned zoom controls (`-`, `+`, Pan/Touch, Reset) unobstructed.
• *Given* the scrubbed graphs in `lowerColumn`,
• *When* inspecting the top curve of the elevation profile or pace graph,
• *Then* the upper graph area SHALL NOT be occluded by a centered badge.

**Invariants**: Single-finger vertical scroll on `lowerColumn` MUST NOT be intercepted; scrubbing gestures and zoom controls MUST NOT experience event conflict; 100% clean-room test suite pass rate.

### REQ-UI-247: Aftermath/Summary: Click-to-Open Navigation and Gesture Disambiguation for Telemetry Graphs and Zone Distribution Cards in WorkoutSummary [Verified]
- **Tests**: `TST-UI-206`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `WorkoutSummary.kt`, `TelemetryMetricGraph.kt`
- **Rationale**: The system SHALL provide seamless click-to-open navigation (`onMapClick()`) across all telemetry graph sections and zone distribution cards in `WorkoutSummary.kt`, while ensuring gesture transparency and smooth vertical scrolling (ATT-2129):
1. *`TelemetryMetricGraph` Gesture Gating (`TelemetryMetricGraph.kt`)*:
• `TelemetryMetricGraph` SHALL declare optional parameter `enableGestures: Boolean = true`.
• When `enableGestures == true`, the underlying `Canvas` SHALL attach `.pointerInput(...)` for horizontal scrubbing and pan gestures.
• When `enableGestures == false`, the `Canvas` SHALL omit `.pointerInput(...)`, rendering as a passive graphical canvas that propagates pointer touch events upwards to parent click modifiers without intercepting tap events.
2. *Telemetry Section Click-to-Open Navigation (`WorkoutSummary.kt`)*:
• In `WorkoutSummary.kt`, each telemetry metric preview block (Speed/Pace, Heart Rate, Cycling Power) consisting of its header `Text` and `TelemetryMetricGraph` SHALL be wrapped in a container possessing `mapClickModifier` (`Modifier.clickable { if (workoutData.headerData.finished) onMapClick() }`).
• Each `TelemetryMetricGraph` in `WorkoutSummary.kt` SHALL be invoked with `enableGestures = false`.
• A tap anywhere on the telemetry title, graph canvas, or surrounding section bounds SHALL reliably invoke `onMapClick()`.
3. *Zone Distribution Card Click-to-Open Navigation (`WorkoutSummary.kt`)*:
• In `WorkoutSummary.kt`, `HeartRateZoneDistributionCard` and `PowerZoneDistributionCard` SHALL receive `mapClickModifier` chained to their layout modifier (`.then(mapClickModifier)`).
• Tapping on the zone card header, column chart, or card surface SHALL invoke `onMapClick()`.
• Taps on the segmented button mode switcher (if present) SHALL be consumed by the inner button row without triggering navigation.
4. *Preservation of Core Invariants*:
• Full interactive scrubbing and zooming in `MapDetailLayout.kt` (detailed workout view) SHALL remain 100% operational with default `enableGestures = true`.
• Vertical scrolling of the workout summary list (`WorkoutSummariesTabbedScreen`) SHALL remain completely fluid without touch slop conflicts.
• 9-language translation parity across all headings and labels SHALL remain 100% intact.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement (`REQ-UI-247`), refining `REQ-UI-205` (*Slotted Analytics Architecture*) and `REQ-UI-206` (*Telemetry Metric Graphs*) under Epic `ATT-111` (*Compact Post-Workout Visual Analytics & Graphs*).
2. *Historical Origin & Commit Trace*: Sprint 2026-40.5 (Commit `e7a6a273`, `ATT-1391`) introduced `TelemetryMetricGraph` and summary list previews.
3. *Root Reason for Existing Formulation*: In `ATT-1391`, telemetry graphs were added to `WorkoutSummary.kt` as visual previews. The author omitted `mapClickModifier` and did not gate `pointerInput` on the canvas.
4. *Preservation of Core Invariants*: Interactive scrubbing in `MapDetailLayout`, multi-chart cursor synchronization, 5-zone histogram toggling, and 9-language localization parity are 100% strictly preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an athlete viewing a workout summary list card with telemetry graphs enabled,
• *When* the athlete taps anywhere on the Speed/Pace, Heart Rate, or Power graph or its title,
• *Then* `onMapClick()` SHALL be triggered and the detailed workout view SHALL open.
• *Given* an athlete viewing a workout summary list card with zone distribution cards enabled,
• *When* the athlete taps on the Heart Rate or Power zone card,
• *Then* `onMapClick()` SHALL be triggered and the detailed workout view SHALL open.
• *Given* an athlete in the detailed workout view (`MapDetailLayout`),
• *When* dragging across the telemetry graphs,
• *Then* interactive scrubbing and the floating telemetry badge SHALL function with zero degradation.
• *Given* the workout summary list with telemetry graphs visible,
• *When* the athlete performs vertical fling or scroll gestures over the graphs,
• *Then* the list SHALL scroll smoothly without gesture stalls or unintended navigation.

**Invariants**: Interactive scrubbing in MapDetailLayout remains intact; fluid 60fps vertical list scrolling; 100% clean-room test suite pass rate.

### REQ-UI-248: Aftermath/Details: Strava Activity Data Upper Metadata Slotting & Structural Parity in Detailed Workout View [Verified]
- **Tests**: `TST-UI-207`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `TrackOnMapScreen.kt`
- **Rationale**: The system SHALL ensure that Strava activity results (PR badges, best efforts, segment achievements) in the detailed workout view (`TrackOnMapScreen.kt` / `MapDetailLayout.kt`) maintain structural order parity with the workout list summary (`WorkoutSummary.kt`) (ATT-2137):
1. *Upper Metadata Slotting (`metadataContent`)*:
• `TrackOnMapScreen.kt` SHALL render `StravaActivitySection` within the `metadataContent` slot rather than `analyticsContent`.
• `StravaActivitySection` SHALL be rendered immediately after `WorkoutExtrema` and prior to the map viewport container (`BoxWithConstraints`).
2. *Preference & Data Guards*:
• `StravaActivitySection` SHALL only be rendered when `activeDetailPrefs.showStrava == true` AND `!workoutData.stravaActivityData.isNullOrBlank()`.
3. *Analytics Slot Cleanup*:
• `StravaActivitySection` SHALL NOT be rendered in `analyticsContent`, eliminating redundant duplicate rendering below telemetry graphs.
4. *Preserved Invariants*:
• Custom `metadataContent` callers (when `metadataContent != null`) retain complete control over upper slot rendering.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement (`REQ-UI-248`), refining `REQ-UI-240` (*Settings/Aftermath: Matrix Table Presentation for Configurable Aftermath Sections*) and `REQ-UI-245` (*Upper Metadata Slot Architecture & Reactive Detail Section Rendering in MapDetailLayout*) under Epic `ATT-111` (*Compact Post-Workout Visual Analytics & Graphs*).
2. *Historical Origin & Commit Trace*: Sprint 2026-40.12 (`ATT-2030`) decoupled detail screen preferences from list card preferences. In `ATT-2112`, description and extrema were moved above the map, but Strava was left at the bottom of `analyticsContent`.
3. *Root Reason for Existing Formulation*: Strava activity results were buried under elevation, telemetry graphs, and zone cards in `lowerColumn`. Positioning it in `metadataContent` restores structural parity with `WorkoutSummary.kt` where Strava precedes the map.
4. *Preservation of Core Invariants*: Backward compatibility for callers with default `metadataContent = null`, preference gating, and 9-language translation parity are 100% strictly preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an athlete opening a workout containing valid Strava activity JSON with `showStrava` enabled in workout detail preferences,
• *When* the detailed workout view renders,
• *Then* `StravaActivitySection` SHALL appear above the map, immediately following `WorkoutExtrema`.
• *Given* an athlete disabling Strava in Advanced Settings Section 5 ("In Details" column),
• *When* viewing the detailed workout,
• *Then* `StravaActivitySection` SHALL be completely omitted from the layout.
• *Given* the analytics content slot in `lowerColumn`,
• *When* scrolling through graphs and zone cards,
• *Then* `StravaActivitySection` SHALL NOT be duplicated below the analytics stack.

**Invariants**: Custom `metadataContent` callers preserved; preference gating strictly enforced; 100% clean-room test suite pass rate.

### REQ-UI-249: Aftermath/Zoom: Reactive Zoom Controls Suppression in MapDetailLayout When No Zoomable Graphs Are Rendered [Verified]
- **Tests**: `TST-UI-208`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
• If both `showElevationProfile` and `hasTelemetryGraphs` are false, `hasZoomToolbar` SHALL evaluate to `false`.
2. *Vertical Space Recovery*:
• In `BoxWithConstraints` within `MapDetailLayout.kt`, `toolbarHeightPx` SHALL evaluate to `0f` when `!hasZoomToolbar`.
• `SplitPaneMath.calculateAvailableHeight` SHALL exclude toolbar height when `hasZoomToolbar == false`, allocating the full vertical height to the map and remaining scrollable content.
3. *Overlay Invariants*:
• When `hasZoomToolbar == false`, `scrubbingOverlay` SHALL NOT render, preventing phantom scrubbing telemetry badges.
4. *Preserved Invariants*:
• Standalone elevation profiles (`showElevationProfile == true`) retain full zoom and pan functionality.
• Telemetry-only inspections (`showElevationProfile == false && hasTelemetryGraphs == true`) retain full zoom and pan functionality across Speed, HR, and Power graphs.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement (`REQ-UI-249`), refining `REQ-UI-225` (*Persistent Sticky Global Zoom Toolbar Between Map and Telemetry Graphs*) under Epic `ATT-111` (*Compact Post-Workout Visual Analytics & Graphs*).
2. *Historical Origin & Commit Trace*: Sprint 2026-40.6 (`ATT-1876`) introduced `GlobalTelemetryZoomToolbar` assuming the elevation profile was always visible. Sprint 2026-40.12 (`ATT-2030`) enabled independent disabling of elevation and telemetry charts.
3. *Root Reason for Existing Formulation*: The guard only checked `showZoomControls && !activeScrubPath.isNullOrEmpty()`, ignoring whether any chart was rendered.
4. *Preservation of Core Invariants*: Full zooming/scrubbing when graphs are active, math integrity in `SplitPaneMath`, and 9-language translation parity are 100% preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an athlete viewing a workout where both elevation profile and telemetry graphs are disabled (or sensor data absent),
• *When* the detailed workout view renders,
• *Then* `GlobalTelemetryZoomToolbar` SHALL NOT be rendered.
• *Given* an athlete viewing a workout with elevation profile enabled or at least one telemetry chart rendered,
• *When* `showZoomControls == true`,
• *Then* `GlobalTelemetryZoomToolbar` SHALL render above the lower column and remain fully operable.

**Invariants**: Active chart zoom/pan preserved; 100% clean-room test suite pass rate.
- **Rationale**: The system SHALL suppress `GlobalTelemetryZoomToolbar` in `MapDetailLayout.kt` whenever neither an elevation profile nor any telemetry metric graphs are rendered, eliminating non-functional UI clutter and returning vertical space to the viewport (ATT-2138):
1. *Toolbar Activation Guard (`hasZoomToolbar`)*:
• In `MapDetailLayout.kt`, `hasZoomToolbar` SHALL evaluate to `true` IF AND ONLY IF: `showZoomControls == true`, `(showElevationProfile

### REQ-UI-250: Aftermath/Layout: Collapsing Upper Metadata Architecture & Map Squashing Prevention via NestedScroll in MapDetailLayout [Verified]
- **Tests**: `TST-UI-209`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `MapDetailLayout.kt`
- **Rationale**: The system SHALL decouple upper workout metadata (header, description, extrema, Strava) from static viewport allocation in `MapDetailLayout.kt` by integrating a collapsible nested-scroll connection, preventing map squashing and enabling full-screen expansion for map and telemetry analytics (ATT-2150):
1. *Collapsing AppBar NestedScroll Connection (`MapDetailLayout.kt`)*:
• The root container of `MapDetailLayout` SHALL attach a `CollapsingAppBarNestedScrollConnection` via `Modifier.nestedScroll(connection)`.
• The upper section containing `header()` and optional `metadataContent` SHALL translate vertically using `Modifier.offset { IntOffset(0, connection.appBarOffset) }`.
• On upward scroll gestures within `lowerColumn` (or upper metadata), `connection.appBarOffset` SHALL decrement from `0` to `-appBarMaxHeight`, collapsing the header and metadata off the top of the viewport.
• The viewport container (`BoxWithConstraints`) SHALL dynamically expand into the released vertical space, granting the map and telemetry graphs up to the full screen height.
2. *Downward Scroll Restoration*:
• On downward scroll gestures, `connection.appBarOffset` SHALL increment smoothly back towards `0`, sliding the header and metadata back into view.
3. *Guaranteed Minimum Map Height in Base State*:
• In the uncollapsed base state (`connection.appBarOffset == 0`), the maximum initial height of the upper metadata container SHALL be bounded to $\text{totalScreenHeight} - (\text{MIN\_MAP\_HEIGHT} + \text{DIVIDER\_TOUCH\_HEIGHT} + \text{toolbarHeight} + \text{MIN\_LOWER\_HEIGHT})$ with an internal scroll state attached if contents exceed this threshold, ensuring that the map viewport is NEVER squashed below `SplitPaneMath.MIN_MAP_HEIGHT` (120dp) regardless of metadata volume.
4. *Preserved Invariants*:
• Custom callers with `metadataContent == null` (Routes, Segments, Heatmaps, LiveSegmentSheet) retain their existing layout.
• Bottom sheet presentation (`!useStatusBarsPadding`) with sheet drag handle and `onHeaderHeightMeasured` callback remains intact.
• 9-language translation parity maintained.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement (`REQ-UI-250`), refining `REQ-UI-245` (*Upper Metadata Slot Architecture & Reactive Detail Section Rendering in MapDetailLayout*) and `REQ-UI-223` (*Interactive Drag-Handle Splitter for Vertical Proportional Resizing*) under Epic `ATT-111` (*Compact Post-Workout Visual Analytics & Graphs*).
2. *Historical Origin & Commit Trace*: Sprint 2026-40.13 (`ATT-2112`, `ATT-2137`) relocated description, extrema, and Strava above the map in a static column.
3. *Root Reason for Existing Formulation*: Static column placement squeezed the map on devices with extensive notes or extrema. Converting to nested scroll allows smooth collapsing on scroll and full-screen map expansion.
4. *Preservation of Core Invariants*: Bottom sheet mode, minimum map height safety, and 9-language localization parity are 100% strictly preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* the detailed workout view (`MapDetailLayout`) with active upper metadata (description, extrema, Strava),
• *When* the athlete scrolls upward on the lower analytics content,
• *Then* the upper metadata section SHALL smoothly slide upward out of the viewport, granting full screen height to the map and graphs.
• *Given* a collapsed upper metadata section,
• *When* the athlete scrolls downward,
• *Then* the header and metadata SHALL smoothly slide back into view.
• *Given* an arbitrarily large metadata block (long description, 10 extrema, Strava PRs),
• *When* the view is displayed in the uncollapsed base state,
• *Then* the map SHALL NEVER be squashed below `SplitPaneMath.MIN_MAP_HEIGHT` (120dp).

**Invariants**: Custom callers preserved; minimum map height guaranteed; 100% clean-room test suite pass rate.

### REQ-UI-251: Aftermath/UI: Integrated Minimalist Marginal Zone Bars & Histogram Right of Telemetry Graph in Portrait Mode [Verified]
- **Tests**: `TST-UI-210`, `TST-UI-396`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `TelemetryMetricGraph.kt`, `HeartRateZoneDistributionCard.kt`, `PowerZoneDistributionCard.kt`, `MapDetailLayout.kt`
- **Rationale**: The system SHALL render an ultra-slim marginal distribution strip (width ~24–32 dp) adjacent to the right margin of `TelemetryMetricGraph.kt` in portrait orientation for Heart Rate and Power metrics, vertically aligned with the curve's Y-axis and synchronized with zone card mode toggles (ATT-2147):
1. *Marginal Strip Presentation (`TelemetryMetricGraph.kt`)*:
• `TelemetryMetricGraph` SHALL accept optional parameters `zoneDistribution: ZoneDistributionData? = null` and `zoneDisplayMode: ZoneCardDisplayMode = ZoneCardDisplayMode.FIVE_ZONES`.
• When `zoneDistribution != null`, a compact marginal strip of width ~28 dp SHALL be rendered within the right boundary region of the graph canvas.
• The strip SHALL be purely visual, omitting text labels, percentages, and duration units to eliminate visual clutter.
2. *5-Zones Mode Alignment*:
• When `zoneDisplayMode == ZoneCardDisplayMode.FIVE_ZONES`, the marginal strip SHALL render up to 5 horizontal bars corresponding to Zones 1..5.
• The top and bottom Y coordinates of each zone bar SHALL align with the threshold boundary lines on the telemetry graph's Y axis (`valueToY(band.maxVal)` and `valueToY(band.minVal)`).
• Each bar SHALL grow horizontally from left to right with a width proportional to its share of total active duration (`(percentage / 100f) * stripWidthPx`), styled in its respective zone color.
3. *Histogram Mode Alignment*:
• When `zoneDisplayMode == ZoneCardDisplayMode.HISTOGRAM`, the strip SHALL render a vertical sequence of fine-grained frequency bars corresponding to `zoneDistribution.histogram.bins`.
• Each bin bar SHALL span vertically between `valueToY(bin.rangeMax)` and `valueToY(bin.rangeMin)` and grow horizontally proportional to its frequency share, styled in the bin's zone color.
4. *Synchronous Mode Switching*:
• In `HeartRateZoneDistributionCard.kt` and `PowerZoneDistributionCard.kt`, `displayMode: ZoneCardDisplayMode` and `onDisplayModeChange: ((ZoneCardDisplayMode) -> Unit)? = null` SHALL be hoisted parameters.
• Switching modes in the zone card SHALL immediately update the hoisted state, synchronously toggling the marginal strip on the graph between 5-zones and histogram representation.
5. *Preserved Invariants*:
• Primary graph curve, multi-chart scrubbing alignment, horizontal axes with `ElevationProfile` (`startPadding = 50.dp`), and 9-language localization parity SHALL remain 100% intact.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement (`REQ-UI-251`), refining `REQ-UI-206` (*Telemetry Metric Graphs*) and `REQ-UI-230` (*Horizontal Background Training Zone Bands*) under Epic `ATT-111` (*Compact Post-Workout Visual Analytics & Graphs*).
2. *Historical Origin & Commit Trace*: Commit `e7a6a273` (`ATT-1391`) introduced `TelemetryMetricGraph`. Commit `53b708cf` (`ATT-1839`) added zone bands with right-side Z1–Z5 text labels.
3. *Root Reason for Existing Formulation*: `endPadding = 25.dp` was reserved for textual zone badges ("Z1".."Z5"). Replacing or augmenting this with weighted marginal bars delivers immediate visual density validation without sacrificing layout symmetry.
4. *Preservation of Core Invariants*: Horizontal graph axes and multi-chart scrubbing alignment with `ElevationProfile` (`startPadding = 50.dp`, total end allocation) must remain strictly invariant.

**Acceptance Criteria (Given-When-Then)**:
• *Given* a telemetry graph for HR or Power in portrait mode with zone distribution available,
• *When* rendered,
• *Then* a minimalist right-side marginal strip (ca. 24–32 dp) is displayed without text clutter.
• *Given* the Y-axis scaling of the graph,
• *When* rendering zone bars or histogram buckets,
• *Then* the vertical extents align with the exact threshold boundaries of the curve.
• *Given* the mode toggle in the zone card,
• *When* toggling between 5-Zones and Histogram,
• *Then* the marginal strip updates synchronously.

**Invariants**: Custom callers preserved; axis alignment preserved; 100% clean-room test suite pass rate.

### REQ-UI-252: Aftermath/Details: Lap Split Visualizer Upper Metadata Slotting & Structural Parity in Detailed Workout View [Verified]
- **Tests**: `TST-UI-211`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `TrackOnMapScreen.kt`
- **Rationale**: The system SHALL ensure that the lap split summary (`LapSplitVisualizerCard`) in the detailed workout view (`TrackOnMapScreen.kt` / `MapDetailLayout.kt`) maintains 1:1 structural order parity with the workout list summary (`WorkoutSummary.kt`) (ATT-2170):
1. *Upper Metadata Slotting (`metadataContent`)*:
• `TrackOnMapScreen.kt` SHALL render `LapSplitVisualizerCard` within the `metadataContent` slot above the map viewport container (`BoxWithConstraints`).
• `LapSplitVisualizerCard` SHALL be placed immediately following `WorkoutExtrema` and prior to `StravaActivitySection`.
2. *Preference & Data Guards*:
• `LapSplitVisualizerCard` SHALL only be rendered when `activeDetailPrefs.showLaps == true` AND `splitChartData != null`.
3. *Analytics Slot Cleanup*:
• `LapSplitVisualizerCard` SHALL NOT be rendered inside `analyticsContent`, eliminating duplicate rendering below telemetry graphs.
4. *Canonical Section Order Parity*:
• In both `WorkoutSummary.kt` and `TrackOnMapScreen.kt`, the canonical section order SHALL be:
1. Header (`WorkoutHeader`)
2. Description & Notes (`WorkoutDescription`)
3. Main Details
4. Extrema (`WorkoutExtrema`)
5. Laps (`LapSplitVisualizerCard`)
6. Strava (`StravaActivitySection`)
7. Map (`PathPreviewMap` / `MapDetailLayout` viewport)
8. Telemetry Graphs (`TelemetryMetricGraph` - Speed, HR, Power)
9. Zone Distribution Cards (`HeartRateZoneDistributionCard`, `PowerZoneDistributionCard`)
5. *Preserved Invariants*:
• Interactive lap tapping SHALL continue to update `selectedLapNr` and highlight the corresponding segment on the map below via `LapSegmentUtils.sliceLapSegment`.
• Collapsing behavior via `CollapsingAppBarNestedScrollConnection` (`REQ-UI-250`) SHALL smoothly slide the lap card off-screen during upward scrolling.
• Custom `metadataContent` callers (when `metadataContent != null`) retain complete control over slot rendering.
• 9-language translation parity MUST NOT be broken.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement (`REQ-UI-252`), refining `REQ-UI-245` (*Upper Metadata Slot Architecture*), `REQ-UI-248` (*Strava Activity Upper Slotting*), and `REQ-UI-250` (*Collapsing Upper Metadata Architecture*) under Epic `ATT-111`.
2. *Historical Origin & Commit Trace*: Sprint 2026-40.12 (`ATT-2030`) decoupled detail screen preferences. In `ATT-2112`, description and extrema were moved above the map. In `ATT-2137`, Strava was moved above the map. Laps remained at the bottom of `analyticsContent`.
3. *Root Reason for Existing Formulation*: `LapSplitVisualizerCard` was placed in `analyticsContent` because early aftermath iterations had only one dynamic slot below the map. Now that `metadataContent` exists and is collapsible, placing laps between Extrema and Strava restores complete canonical parity with `WorkoutSummary.kt`.
4. *Preservation of Core Invariants*: Backward compatibility for callers with default `metadataContent = null`, preference gating, interactive lap highlighting on the map, and 9-language translation parity are 100% strictly preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* a workout with recorded laps and `showLaps` enabled in detail preferences,
• *When* viewing the detailed workout screen (`MapDetailLayout`),
• *Then* `LapSplitVisualizerCard` SHALL render in `metadataContent` above the map, positioned immediately after `WorkoutExtrema` and before `StravaActivitySection`.
• *Given* the detailed workout screen,
• *When* scrolling through `analyticsContent` below the map and telemetry graphs,
• *Then* `LapSplitVisualizerCard` SHALL NOT be rendered in `analyticsContent`.
• *Given* a visible `LapSplitVisualizerCard` in `metadataContent` above the map,
• *When* the athlete taps a lap bar,
• *Then* `selectedLapNr` updates and the corresponding lap segment is highlighted on the map.

**Invariants**: Interactive map lap highlighting preserved; collapsible header integration preserved; 100% clean-room test suite pass rate.

### REQ-UI-253: Tracking Views Null-Safe Enum Deserialization, Fresh-Install Seeding & Database Repair Migration [Verified]
- **Tests**: `TST-UI-212`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `TrackingViewsRepository.kt`, `TrackingViewsDatabaseManager.java`
- **Rationale**: The system SHALL ensure fault-tolerant deserialization of tracking view configurations and robust database initialization/migration (ATT-2179):
1. *Defensive Enum Deserialization (`TrackingViewsRepository.kt`)*:
• When reading sensor field configurations from SQLite (`ROWS_TABLE`), the system SHALL guard against null, empty, or invalid enum values.
• `ViewSize` SHALL default to `ViewSize.NORMAL` if the column value is null, empty, or unparseable.
• `SensorType` SHALL default to `SensorType.TIME_ACTIVE` if the column value is null or unparseable.
• `FilterType` SHALL default to `FilterType.INSTANTANEOUS` if the column value is null or unparseable.
• The deserialization logic MUST catch `NullPointerException` and `IllegalArgumentException` (e.g., via explicit null check and try/catch), ensuring `Enum.valueOf(null)` is never executed and application startup never crashes on fresh installs or corrupted rows.
2. *Default Cockpit View Seeding (`TrackingViewsDatabaseManager.java`)*:
• In `addDefaultTab(...)`, the system SHALL persist `values.put(VIEW_SIZE, rowData.viewSize.name())` for each seeded sensor field row into `ROWS_TABLE`, guaranteeing that fresh installations populate rows with non-null `VIEW_SIZE` values matching the template.
3. *Database Upgrade & Repair Migration (Version 11)*:
• `TrackingViewsDbHelper.DB_VERSION` SHALL be incremented from `10` to `11`.
• In `onUpgrade` for `oldVersion < 11`, the system SHALL execute `UPDATE LayoutRowsTable SET ViewSize = 'NORMAL' WHERE ViewSize IS NULL;`, repairing any pre-existing databases created on previous versions where `VIEW_SIZE` was null.
4. *Defensive Filter Parsing in `getAllFilterData`*:
• In `TrackingViewsDatabaseManager.getAllFilterData`, `SensorType` and `FilterType` SHALL be parsed defensively with null checks and fallback to `SensorType.TIME_ACTIVE` and `FilterType.INSTANTANEOUS`.
5. *Preserved Invariants*:
• Existing valid user cockpit customizations, font sizing hierarchies (`REQ-UI-181`), pick & place reordering (`REQ-UI-200`), and SQLite schema structure MUST NOT be broken.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: `REQ-UI-181` (*Ultra-Large Cockpit Typography Extensions*) Clause 4, and `REQ-PRO-005` (*Database Schema Integrity*).
2. *Historical Origin & Commit Trace*: `REQ-UI-181` was introduced in `ATT-1431` (Sprint 2026-40.3). `REQ-PRO-005` is an architectural invariant.
3. *Root Reason for Existing Formulation*: `REQ-UI-181` explicitly specified fallback to `ViewSize.NORMAL`. However, the implementation caught only `IllegalArgumentException`, while in Java/Kotlin `Enum.valueOf(null)` throws `NullPointerException`. Concurrently, `addDefaultTab` omitted `VIEW_SIZE` during `onCreate` seeding.
4. *Preservation of Core Invariants*: Full typography scales, tile coordinate systems, filter configs, and SQLite table schema remain 100% strictly intact.

**Acceptance Criteria (Given-When-Then)**:
• *Given* a SQLite cursor row where `VIEW_SIZE`, `SENSOR_TYPE`, and/or `FILTER_TYPE` are `null`,
• *When* `TrackingViewsRepository.fetchSensorFieldConfigs` or `fetchSingleSensorFieldConfig` deserializes the row,
• *Then* it SHALL NOT throw `NullPointerException`, and SHALL resolve defaults: `ViewSize.NORMAL`, `SensorType.TIME_ACTIVE`, and `FilterType.INSTANTANEOUS`.
• *Given* a SQLite cursor row where `VIEW_SIZE = "INVALID_SIZE"`, `SENSOR_TYPE = "UNKNOWN"`, and `FILTER_TYPE = "GARBAGE"`,
• *When* deserializing the row,
• *Then* it SHALL NOT throw `IllegalArgumentException`, and SHALL resolve defaults: `ViewSize.NORMAL`, `SensorType.TIME_ACTIVE`, and `FilterType.INSTANTANEOUS`.
• *Given* a clean application install triggering `TrackingViewsDbHelper.onCreate`,
• *When* default tabs and sensor field rows are inserted via `addDefaultTab`,
• *Then* each inserted row in `ROWS_TABLE` SHALL have `VIEW_SIZE` populated with `rowData.viewSize.name()`, matching the template definition.
• *Given* a database upgrading from version 10 (or lower) containing rows with `VIEW_SIZE IS NULL`,
• *When* `TrackingViewsDbHelper.onUpgrade` executes with `newVersion = 11`,
• *Then* an `UPDATE` query SHALL execute setting `VIEW_SIZE = 'NORMAL'` for all rows where `VIEW_SIZE IS NULL`.

**Invariants**: Custom layouts preserved; schema stable; 100% clean-room test suite pass rate.

### REQ-UI-254: Aftermath/Details: Non-Blocking Nested Scroll Dispatch, Sub-Pixel Precision & Optimized High-Density Metadata Rendering in MapDetailLayout [Verified]
- **Tests**: `TST-UI-213`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `MapDetailLayout.kt`, `CollapsingAppBarNestedScrollConnection.kt`, `StravaActivitySection.kt`, `LapSplitVisualizer.kt`
- **Rationale**: The system SHALL eliminate gesture trapping, stutter, and layout lockup in the Detailed Workout view (`MapDetailLayout.kt` / `TrackOnMapScreen.kt`) when displaying high-density metadata (e.g. numerous Strava segments or extensive lap splits) (ATT-2178):
1. *Non-Blocking Nested Scroll Dispatch (`CollapsingAppBarNestedScrollConnection.kt`)*:
• The nested scroll connection SHALL NOT eagerly consume downward scroll deltas (`available.y > 0`) in `onPreScroll` when the child scroll container can scroll backward. It SHALL defer downward expansion to `onPostScroll` (or when the child is at its top boundary `scrollState.value == 0`), allowing lower analytics graphs and internal metadata lists to scroll up/down naturally without prematurely dropping the collapsing header.
• For upward scroll gestures (`available.y < 0`), the connection SHALL consume deltas in `onPreScroll` to smoothly collapse `appBarOffset` from `0` to `-appBarMaxHeight`.
• The connection SHALL maintain high-precision floating-point delta accumulation without integer truncation (`available.y.toInt()`), preventing micro-stutter and frame jitter.
2. *Decoupled Viewport Offset & Composition Optimization (`MapDetailLayout.kt`)*:
• Viewport vertical translation SHALL be decoupled from full-tree layout remeasurement and recomposition loops. The viewport container SHALL apply vertical offset in the layout phase rather than triggering repeated layout passes and `onGloballyPositioned` state writes during scroll gestures.
• In the uncollapsed base state (`appBarOffset == 0`), upper metadata maximum height SHALL remain bounded to ensure the map viewport is never squashed below `SplitPaneMath.MIN_MAP_HEIGHT` (120dp) (`REQ-UI-250`), while allowing smooth, uninhibited vertical scrolling of all enclosed content.
3. *High-Density Performance Optimization (`StravaActivitySection.kt`, `LapSplitVisualizer.kt`)*:
• When rendering a large volume of Strava segment efforts (e.g. 30+ items) or many lap splits, item rows SHALL avoid expensive redundant allocations during scroll/collapse frames.
• Segment effort rows and lap split rows SHALL be efficiently structured to ensure 60/120 fps fluid scrolling.
4. *Preserved Invariants*:
• Full-screen map expansion upon complete collapse (`appBarOffset == -appBarMaxHeight`) per `REQ-UI-250`.
• Canonical section order parity (Header, Description, Extrema, Laps, Strava, Map, Graphs, Zones) per `REQ-UI-252`.
• Interactive draggable splitter (`SplitPaneDivider.kt`, `SplitPaneMath.MIN_MAP_HEIGHT` = 120dp) per `REQ-UI-223`.
• Persistent sticky global zoom toolbar per `REQ-UI-225`.
• Directional chart gesture disambiguation per `REQ-UI-226`.
• 100% 9-language localization parity (`REQ-LOC-001`).

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement (`REQ-UI-254`), refining `REQ-UI-250` (*Collapsing Upper Metadata Architecture & Map Squashing Prevention*) and `REQ-UI-252` (*Lap Split Visualizer Upper Metadata Slotting*) under Epic `ATT-111` (*Compact Post-Workout Visual Analytics & Graphs*).
2. *Historical Origin & Commit Trace*: Sprint 2026-40.13 (`ATT-2150`, commit `e98b598b`).
3. *Root Reason for Existing Formulation*: `REQ-UI-250` introduced `CollapsingAppBarNestedScrollConnection` with blanket `onPreScroll` consumption of both upward and downward deltas, which eagerly intercepted downward swipes from lower analytics and upward swipes within metadata lists, trapping scroll gestures.
4. *Preservation of Core Invariants*: Minimum map height protection (120dp), collapsible header behavior, splitter ratio control, and 9-language translation parity are 100% strictly preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* a workout with many Strava segments and/or many laps (such as Einstein HM 28.09.2014),
• *When* the athlete scrolls within the detailed workout screen (both upper metadata and lower analytics),
• *Then* scrolling SHALL be fluid, responsive, and reach all content without gesture trapping, stutter, or layout lockup.
• *Given* the collapsible upper metadata architecture (`REQ-UI-250`),
• *When* scrolling upward from any point in the view,
• *Then* the upper metadata SHALL collapse cleanly out of view and the map/graphs expand smoothly without stutter.
• *Given* lower analytics content scrolled down,
• *When* the athlete scrolls downward to inspect preceding charts,
• *Then* the lower analytics column SHALL scroll smoothly without prematurely expanding the upper app bar until reaching the top.

**Invariants**: Custom callers preserved; minimum map height (120dp) guaranteed; 100% clean-room test suite pass rate.

### REQ-UI-255: Aftermath: User-Customizable Section Reordering, Unified Order Persistence & Modular Section Architecture for Workout Summaries and Details [Verified]
- **Tests**: `TST-UI-214`, `TST-UI-499`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `WorkoutSectionType.kt`, `MyPreferenceManager.kt`, `AdvancedTuningDialog.kt`, `WorkoutSummary.kt`, `TrackOnMapScreen.kt`
- **Rationale**: The system SHALL allow athletes to customize the vertical display sequence of workout sections and ensure consistent, modular rendering across both list cards (`WorkoutSummary.kt`) and detailed workout screens (`TrackOnMapScreen.kt` / `MapDetailLayout.kt`) (ATT-2176):
1. *Section Domain Model (`WorkoutSectionType`)*:
• The system SHALL define an enum `WorkoutSectionType` with values: `DESCRIPTION`, `EXTREMA`, `LAPS`, `STRAVA`, `MAP`, `ELEVATION`, `CHARTS`, `ZONES`.
• The canonical default sequence SHALL be `[DESCRIPTION, EXTREMA, LAPS, STRAVA, MAP, ELEVATION, CHARTS, ZONES]`.
2. *Display Context Separation (`WorkoutDisplayContext`)*:
• The system SHALL define `WorkoutDisplayContext` with `LIST_CARD` (passive touch, click opens details) and `FULL_DETAIL` (interactive scrubbing, zoom toolbar, lap map highlights).
3. *Unified Order Persistence (`MyPreferenceManager.kt`)*:
• `MyPreferenceManager` SHALL persist the active section sequence in user preferences DataStore under `WORKOUT_SECTIONS_ORDER`.
• If uninitialized or invalid, it SHALL fall back safely to the canonical default order.
• If any enum values are omitted from stored preferences (e.g. newly introduced sections), the system SHALL append the missing values to ensure no sections are dropped.
4. *Advanced Settings Reordering UI (`AdvancedTuningDialog.kt`)*:
• The section matrix in `AdvancedTuningDialog.kt` SHALL render rows according to the active `workoutSectionsOrder`.
• Each row SHALL feature accessible reorder controls (`Move Up` / `Move Down` buttons) to dynamically adjust its position in the sequence, updating DataStore reactively.
• The `Move Up` action SHALL be disabled for the first row, and `Move Down` SHALL be disabled for the last row.
5. *Dynamic Order Execution in WorkoutSummary (`WorkoutSummary.kt`)*:
• `WorkoutSummary.kt` SHALL maintain `WorkoutHeader` and `WorkoutDetails` permanently at the top as the fixed identity anchor.
• All subsequent enabled sections SHALL be rendered in the exact order specified by `workoutSectionsOrder`.
6. *Dynamic Relative Slotting in MapDetailLayout (`TrackOnMapScreen.kt`)*:
• In `TrackOnMapScreen.kt`, enabled sections appearing prior to `MAP` in `workoutSectionsOrder` SHALL be slotted above the map viewport into `metadataContent`.
• Enabled sections appearing after `MAP` in `workoutSectionsOrder` SHALL be slotted below the map into `MapDetailLayout` / `analyticsContent`.
7. *Preserved Invariants*:
• Fixed anchor identity: `WorkoutHeader` and primary details (`Distance`, `Time`, `Speed/Pace`) remain permanently at the top.
• Minimum map height protection: `SplitPaneMath.MIN_MAP_HEIGHT` (120dp) remains strictly guaranteed (`REQ-UI-250`).
• Independent section visibility preferences (`REQ-UI-229`, `REQ-UI-240`) remain fully functional.
• 100% 9-language localization parity (`REQ-LOC-001`).

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement (`REQ-UI-255`), extending `REQ-UI-229` (*Workout Card Section Visibility Matrix*), `REQ-UI-240` (*Detail Screen Section Visibility Matrix*), and `REQ-UI-252` (*Lap Split Visualizer Slotting*) under Epic `ATT-111` (*Compact Post-Workout Visual Analytics & Graphs*).
2. *Historical Origin & Commit Trace*: Sprint 2026-40.11 (`ATT-1714`), Sprint 2026-40.12 (`ATT-2030`), Sprint 2026-40.13 (`ATT-2112`, `ATT-2150`, `ATT-2170`).
3. *Root Reason for Existing Formulation*: Previous iterations introduced independent visibility toggles but hardcoded the vertical layout sequence. Athletes prioritize different aspects of their post-workout data.
4. *Preservation of Core Invariants*: Top identity anchors, collapsible header mechanics, 120dp map safety, and 9-language translation parity are 100% strictly preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* the Advanced Settings dialog opened in the Workout Cards section,
• *When* the athlete taps Move Up or Move Down on a section row,
• *Then* the row moves to the new position and the updated sequence is immediately saved to DataStore.
• *Given* a custom section sequence where CHARTS and ZONES precede MAP and EXTREMA,
• *When* viewing workout cards in the list,
• *Then* the sections render in the exact custom order: Header -> Details -> Charts -> Zones -> Map -> Extrema.
• *When* viewing the detailed workout screen (TrackOnMapScreen),
• *Then* Charts and Zones render in metadataContent above the map, while Extrema renders below the map.
• *Given* an existing app installation with uninitialized section order,
• *When* the app starts,
• *Then* the default sequence is applied and all existing visibility preferences remain preserved.

**Invariants**: Top identity anchors preserved; minimum map height (120dp) guaranteed; 100% clean-room test suite pass rate.

### REQ-UI-256: Equipment Management: Fleet-Wide Equipment-to-Sensor Mapping Matrix with Checkboxes for Bikes and Shoes [Verified]
- **Tests**: `TST-UI-230`, `TST-UI-215`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `EquipmentTabsScreen.kt`, `EquipmentSensorMatrixScreen.kt`, `EquipmentViewModel.kt`, `EquipmentDbHelper.java`, `DevicesDatabaseManager.java`, `EditEquipmentDialog.kt`
- **Rationale**: The system SHALL provide a centralized, interactive equipment-to-sensor mapping matrix with checkboxes in `EquipmentTabsScreen.kt`, partitioned into sport-specific tables to eliminate incompatible sensor clutter (ATT-2126, ATT-2382):
1. *Tab Integration (`EquipmentTabsScreen.kt`)*:
• `EquipmentTabsScreen.kt` SHALL provide a 3-tab navigation structure: "Bikes" (`@string/equipment_type_bike`), "Shoes" (`@string/equipment_type_shoe`), and "Sensor Matrix" (`@string/equipment_tab_sensor_matrix`).
• The floating action button (FAB) for adding new equipment SHALL only be rendered on the "Bikes" (page 0) and "Shoes" (page 1) tabs, and SHALL be suppressed on the "Sensor Matrix" (page 2) tab.
2. *Fleet-Wide Remote Sensor Aggregation & Domain Typing (`DevicesDatabaseManager.java` & `EquipmentViewModel.kt`)*:
• `DevicesDatabaseManager.SimpleSensorInfo` SHALL encapsulate `id`, `name`, and `@Nullable DeviceType deviceType`.
• `DevicesDatabaseManager.getAllRemoteSensors()` SHALL query `DEVICE_TYPE` from `Devices.db` and populate `deviceType` for all paired external sensors.
• `DevicesDatabaseManager.getSensorsForSportType(BSportType sportType)` SHALL return sport-compatible sensors including universally shared sensors (`HRM` and `ENVIRONMENT`) for both `BSportType.BIKE` and `BSportType.RUN`.
• `EquipmentViewModel` SHALL expose `allRemoteSensors` as a reactive `StateFlow<List<SimpleSensorInfo>>`.
3. *Sport-Specific Matrix Architecture (`EquipmentSensorMatrixScreen.kt`)*:
• The matrix SHALL partition sensors and equipment into two distinct, independent tables:
- **Table 1: Bikes (`bikes`)**: Columns SHALL display strictly bike-compatible sensors (`BIKE_SPEED`, `BIKE_CADENCE`, `BIKE_SPEED_AND_CADENCE`, `BIKE_POWER`, `FITNESS_EQUIPMENT`, `RADAR`, `SHIFTING`) and shared sensors (`HRM`, `ENVIRONMENT`). Running-exclusive sensors (`RUN_SPEED` / Footpod) MUST NOT appear in the bike table. The table SHALL feature a dedicated sticky header and an independent horizontal scroll state (`bikeScrollState`).
- **Table 2: Shoes (`shoes`)**: Columns SHALL display strictly run-compatible sensors (`RUN_SPEED` / Footpod, Running Dynamics) and shared sensors (`HRM`, `ENVIRONMENT`). Cycling-exclusive sensors (Power Meters, Bike Cadence/Speed) MUST NOT appear in the shoe table. The table SHALL feature a dedicated sticky header and an independent horizontal scroll state (`shoeScrollState`).
• Horizontal scrolling in Table 1 SHALL NOT scroll or desynchronize Table 2, and vice-versa.
• Sticky First Column: The equipment name and icon column SHALL remain sticky on the left during horizontal scrolling across sensor columns in each table.
4. *1-Tap Checkbox Persistence (`EquipmentDbHelper.java` & `EquipmentViewModel.kt`)*:
• Each grid intersection cell SHALL display an interactive Material 3 `Checkbox`.
• The checkbox SHALL be checked if and only if `equipmentItem.linkedDeviceIds.contains(sensor.id)`.
• Tapping a checkbox SHALL trigger `EquipmentViewModel.setSensorLink(equipmentId, sensorId, !isCurrentlyLinked)`, updating SQLite table `LINKS` immediately within transactional helper `EquipmentDbHelper.setDeviceLink`, followed by a reactive reload of `bikes` and `shoes`.
• Changes made in the matrix SHALL immediately synchronize with the single-item configuration bottom sheet (`EditEquipmentDialog`) and sensor settings (`EditDeviceDialog`).
5. *Single-Item Dialog Linkage Restriction (`EditEquipmentDialog.kt` & `EditDeviceDialog.kt`)*:
• In `EditEquipmentDialog`, bikes SHALL only be offered bike-compatible and shared sensors; shoes SHALL only be offered run-compatible and shared sensors.
• In `EditDeviceDialog`, sensors SHALL only offer compatible equipment types matching `deviceType.sportType` (cycling sensors to bikes only, running sensors to shoes only, shared sensors to both).
6. *Empty States & Edge Case Resilience*:
• If zero remote sensors are paired across the fleet, an informative placeholder (`@string/equipment_matrix_no_sensors`) SHALL be displayed.
• If zero equipment items exist, an informative empty state SHALL be displayed.
• If a sport category has equipment but zero compatible sensors, an inline informational state SHALL indicate no compatible sensors are available.
7. *9-Language Localization Parity (`REQ-LOC-001`)*:
• All newly introduced string resources SHALL be translated with 100% parity across EN, DE, ES, FR, IT, JA, NL, PL, PT.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: `REQ-UI-256` (*Equipment Management: Fleet-Wide Equipment-to-Sensor Mapping Matrix with Checkboxes for Bikes and Shoes*), targeting `EquipmentTabsScreen.kt`, `EquipmentSensorMatrixScreen.kt`, `EquipmentViewModel.kt`, and `DevicesDatabaseManager.java`.
2. *Historical Origin & Commit Trace*: Commit `fc10ad81` (Sprint `2026-40.16`), ticket `ATT-2126`.
3. *Root Reason for Existing Formulation*: In ATT-2126, a monolithic table was created to establish the central matrix concept. However, practical usage revealed that displaying power meters on shoes and footpods on bikes created visual noise and nonsensical mapping options.
4. *Preservation of Core Invariants*: 1-tap Checkbox persistence, bidirectional sync with single-item dialogs, sticky columns/headers, fast scrolling, and 9-language localization parity MUST be preserved. Bike sensors and shoe sensors are cleanly partitioned into two sport-specific tables, with shared sensors (HRM, Temperature) appearing in both.

**Acceptance Criteria (Given-When-Then)**:
• *Given* configured bikes and shoes, and paired remote sensors (e.g. Stages Power, Wahoo Speed, Stryd Footpod, Garmin HRM),
• *When* the athlete navigates to the "Sensor Matrix" tab in `EquipmentTabsScreen`,
• *Then* two separate tables SHALL be displayed: Table 1 (Fahrräder) containing Stages Power, Wahoo Speed, and Garmin HRM; and Table 2 (Schuhe) containing Stryd Footpod and Garmin HRM.
• *Given* Table 1 (Bikes) and Table 2 (Shoes),
• *When* the athlete scrolls Table 1 horizontally across bike sensors,
• *Then* Table 2 SHALL NOT scroll and SHALL maintain its own scroll position independently.
• *Given* a bike sensor row in Table 1,
• *When* the athlete taps an unlinked checkbox,
• *Then* the checkbox immediately toggles to checked, SQLite table `LINKS` is updated, and opening `EditEquipmentDialog` for that bike shows the sensor selected.
• *Given* `EditEquipmentDialog` opened for a running shoe,
• *When* viewing available sensors,
• *Then* only running-compatible and shared sensors SHALL appear, and cycling-exclusive power meters/cadence sensors SHALL NOT be selectable.
• *Given* zero paired remote sensors,
• *When* viewing the "Sensor Matrix" tab,
• *Then* an informative empty state placeholder is displayed.

**Invariants**: SQLite schema stable; bidirectional sync guaranteed; 100% clean-room test suite pass rate.

### REQ-UI-257: Reactive Bi-Directional Equipment-to-Sensor Synchronization Between EquipmentDbHelper, DeviceDataRepository, and EquipmentViewModel [Verified]
- **Tests**: `TST-UI-216`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `EquipmentTabsScreen.kt`, `EquipmentItem.kt`
- **Rationale**: The system SHALL guarantee real-time, lifecycle-safe, bi-directional synchronization between equipment mapping mutations and sensor repositories (ATT-2307):
1. *Reactive Event Stream (`EquipmentRepository.kt`)*:
• `EquipmentRepository` SHALL expose a reactive event stream `equipmentLinksChanged: SharedFlow<Long?>` configured with buffer capacity and `BufferOverflow.DROP_OLDEST`.
• The repository SHALL provide `@JvmStatic fun notifyEquipmentLinksChanged(affectedDeviceId: Long?)` callable from both Kotlin and Java.
2. *Database Mutation Dispatch (`EquipmentDbHelper.java`)*:
• `EquipmentDbHelper` SHALL invoke `EquipmentRepository.notifyEquipmentLinksChanged(deviceId)` on every link mutation: `addDeviceLink` (deviceId), `removeDeviceLink` (deviceId), `setEquipmentLinks` ((long) antDeviceId), `updateEquipment` (null), and `deleteEquipment` (null).
3. *Reactive Invalidation & Cache Refresh (`DeviceDataRepository.kt`)*:
• `DeviceDataRepository` SHALL collect `EquipmentRepository.equipmentLinksChanged` on `repositoryScope`.
• When a specific `affectedDeviceId` is received, it SHALL invoke `refreshDeviceFromDb(affectedDeviceId)`. When `null` is received, it SHALL invoke `loadAllDevices()`.
• The updated device state SHALL be emitted via `allDevices` StateFlow, propagating immediately to `GetMergedDevicesUseCase` and `DeviceListViewModel`.
4. *Bi-Directional Equipment Reactivity (`EquipmentViewModel.kt`)*:
• `EquipmentViewModel` SHALL collect `EquipmentRepository.equipmentLinksChanged` on `viewModelScope` and invoke `loadEquipment()` whenever external modifications occur (such as saves from `EditDeviceDialog`).
5. *Fresh Dialog State Initialization (`EditDeviceViewModel.kt`)*:
• `EditDeviceViewModel.loadInitialDeviceData(deviceId)` SHALL retrieve the fresh snapshot from `DeviceDataRepository.getDeviceSnapshotById(deviceId)` unconditionally, eliminating stale early-exit retention.
6. *Circular Update Immunity & Convergence*:
• Invalidation handlers (`refreshDeviceFromDb`, `loadEquipment`) SHALL be strictly read-only and SHALL NEVER execute database writes, guaranteeing zero recursive loops.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement (`REQ-UI-257`), extending and refining `REQ-UI-256` (*Fleet-Wide Equipment-to-Sensor Mapping Matrix with Checkboxes for Bikes and Shoes*) under Epic `ATT-355` (*Good and consistent UI*).
2. *Historical Origin & Commit Trace*: Sprint 2026-40.15 (`ATT-2307`).
3. *Root Reason for Existing Formulation*: Equipment mapping changes made in the matrix wrote directly to SQLite via `EquipmentDbHelper` without notifying `DeviceDataRepository`, causing Sensor Settings to render stale equipment mappings until app restart.
4. *Preservation of Core Invariants*: SQLite schemas (`EQUIPMENT`, `LINKS`, `DEVICES`), transactional integrity, thread safety, and 100% test suite pass rate are strictly preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an athlete modifies a sensor link in `EquipmentSensorMatrixScreen` or `EquipmentTabsScreen`,
• *When* `EquipmentDbHelper.setDeviceLink` updates the SQLite database,
• *Then* `EquipmentRepository.equipmentLinksChanged` SHALL emit the device ID, `DeviceDataRepository` SHALL refresh the device, and `DeviceListScreen` SHALL render the updated `linkedEquipment` immediately.
• *Given* modified equipment links in SQLite,
• *When* the athlete opens `EditDeviceDialog`,
• *Then* `_deviceSnapshot` SHALL reflect the latest `linkedEquipment` stored in SQLite without requiring process restart.
• *Given* an athlete edits linked equipment in `EditDeviceDialog` and taps Save,
• *When* `DeviceDataRepository.updateDevice` saves the links via `EquipmentDbHelper.setEquipmentLinks`,
• *Then* `EquipmentViewModel` SHALL reactively reload, updating `_bikes` and `_shoes` in real time.
• *Given* ViewModel recreation or destruction,
• *When* `viewModelScope` is cancelled,
• *Then* all coroutine collectors SHALL be cleanly terminated, ensuring zero memory leaks.

**Invariants**: SQLite schema stable; cycle immunity guaranteed via read-only invalidation handlers; 100% clean-room test suite pass rate.

### REQ-UI-522: Localized Cluster Naming Interface [Implemented]
- **Tests**: `TST-UI-078`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `ImportBackupTabsScreen.kt`, `WorkoutClusterComponents.kt`, `strings.xml`
- **Rationale**: The cluster candidate naming dialog (`ClusterNamingDialog`) SHALL render all user-facing text labels (titles, route assignment prompts, task counters, field labels, and button actions) using localized string resources. Zero hardcoded string literals SHALL remain in the composable layout. Furthermore, to eliminate visual ambiguity against the separate workout name input field (`cluster_naming__workout_name_label`), the route cluster assignment and creation sections SHALL explicitly indicate that they select an *existing* favorite route (*Lieblingsstrecke*) or create a *new* favorite route (*Lieblingsstrecke*) across all 9 supported locales (`cluster_naming__assignment_label`, `cluster_naming__new_name_prompt`, `cluster_naming__new_route_name_label`) (ATT-5664). Additionally, when an imported workout cannot be automatically matched, the subtitle copy (`cluster_naming__found_recurring`) SHALL neutrally describe the track as an unassigned route rather than prematurely assuming a new favorite route (*Lieblingsstrecke*), preserving the single string date placeholder (`%1$s`) across all 9 supported locales (ATT-5662). **Invariants**: Formatted placeholders (date strings, task numbers) MUST follow valid Java Formatter syntax across all 9 supported locales; resource keys MUST remain strictly unchanged.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: `REQ-UI-522` (formerly `REQ-UI-123`, renumbered in ATT-6123 to resolve identifier collision, *Localized Cluster Naming Interface*), targeting `ImportBackupTabsScreen.kt`, `WorkoutClusterComponents.kt`, and `strings.xml`.
2. *Historical Origin & Commit Trace*: Introduced in `ATT-494` / `ATT-565` for initial localization of the cluster naming popup, extended in `ATT-5560` (bottom sheet conversion & inline workout naming), updated in `ATT-5664` (favorite route creation labels), and refined in `ATT-5662` (unassigned route subtitle neutrality).
3. *Root Reason for Existing Formulation*: Generic labels were adequate when the dialog only asked for a cluster name, and early design assumed every unassigned candidate was a new recurring route. Neutral unassigned route copy (*"Nicht automatisch zuweisbare Strecke vom %1$s."* / *"Unassigned route from %1$s."*) avoids confusing the user before a cluster assignment is made.
4. *Preservation of Core Invariants*: String resource key names remain 100% backward-compatible; single `%1$s` argument structure preserved; 100% translation parity across all 9 locales; no decorative emojis (`ATT-5400`); no logic mutations.

**Acceptance Criteria (Given-When-Then)**:
• *Given* `ClusterNamingDialog` is presented during workout import or manual cluster assignment,
• *When* inspecting the route cluster section,
• *Then* the upper selector is labeled with explicit selection copy (`cluster_naming__assignment_label`: DE *"Bestehende Lieblingsstrecke auswählen:"* / EN *"Select existing favorite route:"*), the creation prompt specifies favorite route creation (`cluster_naming__new_name_prompt`: DE *"Oder neue Lieblingsstrecke anlegen:"* / EN *"Or create a new favorite route:"*), and the text field label specifies favorite route naming (`cluster_naming__new_route_name_label`: DE *"Name der neuen Lieblingsstrecke"* / EN *"New Favorite Route Name"*).
• *Given* an imported workout that cannot be automatically matched to an existing route cluster,
• *When* `ClusterNamingDialog` opens,
• *Then* the subtitle displays neutral unassigned route copy (`cluster_naming__found_recurring`: DE *"Nicht automatisch zuweisbare Strecke vom %1$s."* / EN *"Unassigned route from %1$s."*) with the date properly injected.
• *Given* all 9 locale `strings.xml` files,
• *When* `TranslationParityTest` executes,
• *Then* all 9 languages maintain 100% string presence and specifier parity.

### REQ-UI-523: Compact Equipment Card Layout & Harmonized Inter-Card Spacing [Verified]
- **Tests**: `TST-UI-519`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `EquipmentTabsScreen.kt`, `EquipmentItem.kt`
- **Rationale**: The system SHALL optimize information density and layout rhythm on the Equipment management screen (`EquipmentTabsScreen.kt` across the "Bikes" and "Shoes" tabs):
1. *Harmonized Inter-Card Spacing*: The vertical visual distance between consecutive equipment cards in `LazyColumn` SHALL be reduced to a unified gap between 6.dp and 8.dp (by configuring `verticalArrangement = Arrangement.spacedBy(6.dp)` or `8.dp` and eliminating excessive vertical margins on `MappableListItem` with `padding(horizontal = 4.dp, vertical = 0.dp)`).
2. *Adaptive Compact Presentation for Inactive Equipment*: Equipment items without recorded workouts (`item.statsData.totalWorkouts == 0`) and/or retired items (`item.isRetired == true`) SHALL render in a streamlined, compact presentation:
• The item title SHALL scale proportionally to `MaterialTheme.typography.titleMedium` (compared to `titleLarge` for active equipment with workout statistics).
• Internal vertical padding of the card container SHALL be reduced to `8.dp` (compared to `12.dp` for active equipment with rich statistics).
• Vertical spacing between metadata rows (Strava, linked sport types, linked sensors) SHALL be standardized to `4.dp`.
3. *Visual Subordination of Retired Gear*: Retired equipment cards SHALL render with subtle visual subordination via `MappableListItem(alpha = 0.75f)` while preserving the localized "Ruhestand" / "Retired" badge.
4. *Touch-Target & Accessibility Invariants*: The entire card SHALL maintain a minimum vertical touch target height of at least 48.dp, single-tap SHALL invoke `onConfigClick` to launch `EditEquipmentDialog`, and long-press SHALL anchor the delete-only context menu at Top-Left (`REQ-UI-061`).

**Acceptance Criteria (Given-When-Then)**:
• *Given* the equipment list for Bikes or Shoes containing multiple items,
• *When* the list renders on screen,
• *Then* the visual spacing between cards SHALL be between 6.dp and 8.dp, and zero cards SHALL exhibit 20.dp gaps.
• *Given* an equipment item with 0 recorded workouts,
• *When* rendered,
• *Then* the card title SHALL use `titleMedium`, inner vertical padding SHALL be `8.dp`, and stats summary block SHALL be omitted.
• *Given* a retired equipment item,
• *When* rendered,
• *Then* the card container SHALL render with `0.75f` alpha.
• *Given* any equipment card,
• *When* tapped,
• *Then* `EditEquipmentDialog` SHALL open, and when long-pressed, the Top-Left delete context menu SHALL appear.

**Invariants**: SQLite persistence (`EquipmentDbHelper`), active gear stats blocks (`StatsSummaryBlock`), `REQ-UI-061` Top-Left delete contract, and 9-language localization parity MUST NOT be altered.

### REQ-UI-258: Cockpit Sensor Field Visual Styling Architecture, Expert Settings Selection & Reactive Grid Layout [Verified]
- **Tests**: `TST-UI-217`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `SensorFieldStyle.kt`, `SensorFieldView.kt`, `SensorGridScreen.kt`, `TuningPreferencesDataStore.kt`, `AdvancedTuningDialog.kt`
- **Rationale**: The system SHALL provide a structured styling and spacing architecture for cockpit telemetry tiles in `SensorFieldView.kt` and `SensorGridScreen.kt`, integrated into user preferences (`TuningPreferencesDataStore.kt`) and Expert Settings (`AdvancedTuningDialog.kt`):
1. *Four Standardized Visual Variants & Naming Overhaul*: The system SHALL encapsulate 4 distinct design specifications in `SensorFieldVariant` with appealing, localized user-facing titles across all 9 languages:
• `CLASSIC_SEAMLESS` (`variant_0`): `RectangleShape`, `gridSpacing = 0.dp`, `elevation = 0.dp`, `1.dp outlineVariant` border. Localized title: *"Klassisch nahtlos"* / *"Classic Seamless Grid"*.
• `OUTLINED_TILES` (`variant_1`): `RoundedCornerShape(6.dp)`, `gridSpacing = 4.dp`, `elevation = 0.dp`, `1.dp outlineVariant` border. Localized title: *"Moderne Sportkacheln"* / *"Modern Outlined Tiles"*.
• `ELEVATED_CARDS` (`variant_2`): `RoundedCornerShape(8.dp)`, `gridSpacing = 6.dp`, `elevation = 2.dp`, borderless. Localized title: *"Erhabene Sportkarten"* / *"Elevated Sports Cards"*.
• `SOFT_CAPSULES` (`variant_3`): `RoundedCornerShape(12.dp)`, `gridSpacing = 8.dp`, `elevation = 1.dp`, `1.dp outlineVariant` border, cleanly clipped 6dp vertical zone indicator bar. Localized title: *"Soft-Akzent Kapseln"* / *"Soft Accent Capsules"*.
2. *DataStore Preference Persistence*: `TuningPreferencesDataStore` SHALL persist the selected variant using key `KEY_SENSOR_FIELD_VARIANT` (`tuning_sensor_field_variant`). Default value SHALL be `SensorFieldVariant.CLASSIC_SEAMLESS`. Atomic factory reset (`resetToDefaults()`) SHALL reset `sensorFieldVariant` to `CLASSIC_SEAMLESS`.
3. *Expert Settings Integration (`AdvancedTuningDialog.kt`)*: In `AdvancedTuningDialog`, Section 1 ("Cockpit & Typografie") SHALL provide a user-facing dropdown/selector (`CockpitTypographySection.kt`) allowing the athlete to select from all 4 variants. `TuningSubtitleFormatter.formatCockpitSubtitle` SHALL append the active tile variant to the section header subtitle.
4. *Reactive Live Cockpit Updating (`SensorGridScreen.kt`)*: `SensorGridScreen` SHALL reactively observe `tuningConfig.sensorFieldVariant`. When invoked with default styling parameters, `SensorGridScreen` SHALL dynamically resolve `shape`, `gridSpacing`, and `elevation` from `SensorFieldStyle.forVariant(tuningConfig.sensorFieldVariant)`. The live tracking cockpit across all tracking tabs SHALL immediately adopt the newly selected styling upon update in `AdvancedTuningDialog` without requiring an app restart.
5. *Preservation of Production Invariants*: Runtime defaults SHALL remain strictly on `CLASSIC_SEAMLESS` (0.dp spacing, RectangleShape, 0.dp elevation), ensuring 100% backward compatibility for all athletes; touch target sizes (>= 48.dp), click-to-edit, long-press pick-and-place reordering (`REQ-UI-200`), LiveSegment priority, and SQLite database schemas MUST NOT be altered.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: `REQ-UI-171` (*High-Contrast Cockpit Typography and Subtle Tile Grid*) and `REQ-UI-200` (*Cockpit Sensor Grid Pick & Place*).
2. *Historical Origin & Commit Trace*: `ATT-1264` (commit `6bfb0e35`), `ATT-1629` (commit `3b692bc1`), and `ATT-2058` (commit `ecf08c35`).
3. *Root Reason for Existing Formulation*: Baseline `RectangleShape` with 0dp spacing was used because early rounded-corner experiments lacked synchronized grid spacing, causing awkward diamond-shaped intersection voids at tile corners. Originally conceived as preview-only in Cycle 1, and now expanded to user-configurable settings based on PO review approval.
4. *Preservation of Core Invariants*: Default production values are strictly preserved (`CLASSIC_SEAMLESS`); AMOLED high contrast and semi-bold typography (`REQ-UI-171`) remain intact; pick-and-place selection (`REQ-UI-200`) remains functional across all variants.

**Acceptance Criteria (Given-When-Then)**:
• *Given* the standard live tracking cockpit in production with default preferences,
• *When* rendered without variant overrides,
• *Then* `SensorFieldView` SHALL render with `RectangleShape`, `0.dp` grid spacing, and `1.dp outlineVariant` border, ensuring 100% visual parity with the baseline.
• *Given* the athlete in `AdvancedTuningDialog` (Cockpit & Typografie),
• *When* selecting a variant (e.g. `OUTLINED_TILES` or `ELEVATED_CARDS`),
• *Then* the preference SHALL be persisted to `TuningPreferencesDataStore` and the live cockpit in `SensorGridScreen` SHALL immediately re-render with the corresponding shape, spacing, and elevation.
• *Given* any variant with `RoundedCornerShape`,
• *When* active athletic zones are present,
• *Then* the 6dp vertical indicator strip and 12% background tint SHALL clip cleanly to the tile's rounded corners.
• *Given* any variant in configuration mode (`ScreenMode.CONFIGURATION`),
• *When* long-pressing a tile,
• *Then* the 2dp primary accent border (`REQ-UI-200`) SHALL render accurately.

**Invariants**: Zero breaking changes to production defaults; SQLite database immutability; 100% localization parity.

### REQ-UI-259: Conditional Research Button Visibility Based on Real Paired Remote Devices [Verified]
- **Tests**: `TST-UI-218`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
3. *UI Conditional Composition (`ControlTrackingScreen.kt` & `TrackingTabsScreen.kt`)*: `ControlTrackingScreen` SHALL accept `showResearchButton: Boolean = true` (default `true` for backward compatibility in isolated preview environments). In `TrackingTabsScreen.kt`, the parameter SHALL be bound to `showResearchButton = hasPairedRemoteDevices`. Inside `ControlTrackingScreen`, the `ResearchButton` container `Box(modifier = Modifier.align(Alignment.TopStart))` SHALL be composed conditionally via `if (showResearchButton)`.
4. *Layout Stability*: The central information area (`SearchArea` and `RemoteDevices`) SHALL preserve its `Alignment.TopCenter` anchor within the `fillMaxWidth()` container, guaranteeing zero layout shift or misalignment when the research button is hidden.
5. *Brand Compliance Invariant (AC-3)*: Official trademark assets for ANT+ (`R.drawable.ant_logo`) and Bluetooth (`R.drawable.logo_protocol_bluetooth`) SHALL NOT be altered, distorted, or modified under any circumstances.
6. *100% Localization Parity Across 9 Locales*: `R.string.research` ("Suchen") SHALL maintain 100% localization parity across all 9 supported locales (EN, DE, ES, FR, IT, JA, NL, PL, PT).

**Acceptance Criteria (Given-When-Then)**:
• *Given* the sensor database contains zero paired remote devices (or only internal `Protocol.SMARTPHONE` entries such as GPS, Network, Fused Location, Battery, Pressure),
• *When* the athlete views `ControlTrackingScreen`,
• *Then* the "Suchen" (`ResearchButton`) action button SHALL NOT be rendered.
• *Given* at least one real Bluetooth LE or ANT+ sensor device is registered and paired (`isPaired == true`) in the database,
• *When* the athlete views `ControlTrackingScreen`,
• *Then* the "Suchen" (`ResearchButton`) action button SHALL be rendered at the top-left, enabling device search and reconnection.
• *Given* an athlete on `ControlTrackingScreen` with zero paired remote devices,
• *When* the athlete pairs a new Bluetooth LE or ANT+ sensor,
• *Then* `hasPairedRemoteDevices` SHALL emit `true` and the "Suchen" button SHALL dynamically appear without requiring an application restart.
• *Given* the display of Bluetooth and ANT+ logos across the application,
• *Then* all official trademark logos SHALL remain completely unmodified and preserved in authentic visual form.

**Invariants**: Central layout alignment MUST NOT be broken; test suite 100% pass rate MUST be preserved; trademark logos MUST remain untouched; parent ticket Human Decision Gate remains inviolable.
- **Rationale**: The system SHALL conditionally render the "Suchen" (`ResearchButton`) action button on the workout control screen (`ControlTrackingScreen.kt`) if and only if at least one real, paired remote sensor device exists in the database (ATT-2057):
1. *Database Precondition Query (`DevicesDatabaseManager.java`)*: `DevicesDatabaseManager` SHALL provide a fast synchronous query `hasPairedRemoteDevices(): boolean` checking `SELECT _id FROM Devices WHERE paired > 0 AND (protocol = 'ANT_PLUS' OR protocol = 'BLUETOOTH_LE') LIMIT 1`.
2. *Reactive State Pipeline (`ControlTrackingViewModel.kt`)*: `ControlTrackingViewModel` SHALL expose `val hasPairedRemoteDevices: StateFlow<Boolean>` derived by mapping `devicesRepository.allDevices` (`devices.any { it.isPaired && (it.protocol == Protocol.ANT_PLUS

### REQ-UI-260: Centralized Zone Threshold Resolution (DRY) and O(log N) Scrubbing Point Search [Verified]
- **Tests**: `TST-UI-219`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `TelemetryZoneMath.kt`, `TelemetryMetricUtils.kt`, `MapDetailLayout.kt`, `ElevationProfile.kt`, `TelemetryMetricGraph.kt`
- **Rationale**: The system SHALL eliminate duplicated zone threshold loading logic across post-workout telemetry visual analytics and optimize the interactive timeline scrubbing point lookup from $O(N)$ linear scanning to $O(\log N)$ binary search (ATT-2031):
1. *Centralized Zone Threshold Resolution (`TelemetryZoneMath.kt`)*: `TelemetryZoneMath` SHALL serve as the single source of truth for loading athlete training zone thresholds from user preferences via standard factory functions:
• `loadHeartRateThresholds(context: Context, bSportType: BSportType): HeartRateZoneThresholds?`
• `loadPowerThresholds(context: Context): PowerZoneThresholds?`
The methods SHALL encapsulate `SettingsDataStoreJavaHelper.getZoneMax` queries (inspecting zones 1..4), mapping `bSportType == BSportType.BIKE` to `HR_BIKE` vs `HR_RUN`, and returning `null` if any zone threshold $\le 0$.
2. *DRY Call-Site Consolidation & Memoization*: Duplicated 15-line boilerplate zone queries across `ElevationProfile.kt`, `TelemetryMetricGraph.kt`, and `MapDetailLayout.kt` SHALL be eliminated and replaced with unified calls to `TelemetryZoneMath`. In `MapDetailLayout.kt`, zone thresholds SHALL be resolved and memoized at the top level via `remember(bSportType, context)` and `remember(context)`, passing resolved threshold models directly into dependent child composables (`ElevationProfile` and `TelemetryMetricGraph`).
3. *Optimized O(log N) Scrubbing Point Search (`TelemetryMetricUtils.kt`)*: Point selection along the workout scrubbing timeline SHALL utilize an allocation-free binary search algorithm `findNearestPoint(points: List<PathPoint>, targetValue: Double, isTimeDomain: Boolean = false): PathPoint?`. Exploiting the monotonic ordering of recorded `PathPoint` lists (sorted by `distance` or `timeSec`), the search SHALL identify the closest trackpoint in $O(\log N)$ comparisons with exact numerical parity to linear nearest-neighbor resolution, avoiding allocation of temporary closures or full list traversals on the UI thread during drag gestures.
4. *Scrubbing Point Search Replacement*: Ad-hoc linear scans `pathPoints.minByOrNull { abs(it.distance - selectedDistance) }` and `it.timeSec` in `MapDetailLayout.kt` and `TelemetryMetricGraph.kt` SHALL be replaced with `TelemetryMetricUtils.findNearestPoint`.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an athlete inspecting a workout in `MapDetailLayout`, `ElevationProfile`, or `TelemetryMetricGraph`,
• *When* HR or Power zone thresholds are configured in settings,
• *Then* `TelemetryZoneMath.loadHeartRateThresholds` and `loadPowerThresholds` SHALL load the exact thresholds corresponding to the workout's `BSportType` with 100% numerical parity.
• *Given* unconfigured or invalid ($\le 0$) zone settings,
• *Then* the threshold factory functions SHALL return `null` safely without runtime exceptions or partial zone initialization.
• *Given* a recorded `PathPoint` track containing $N \ge 1$ points monotonically sorted by distance and time,
• *When* scrubbing interactively to any distance or timestamp (including exact match, intermediate positions, boundary values, or out-of-range targets),
• *Then* `TelemetryMetricUtils.findNearestPoint` SHALL return the identical nearest `PathPoint` in $O(\log N)$ comparisons that `minByOrNull` would have selected.
• *Given* an empty `PathPoint` list,
• *When* `findNearestPoint` is invoked,
• *Then* it SHALL return `null` safely.

**Invariants**: Telemetry data models, visual graph rendering styles, gesture detection contracts, and unit test pass rates MUST NOT regress.

### REQ-UI-261: Canonical Horizontal Domain Evaluation & Total Span Engine (ProfileDomainMath) [Verified]
- **Tests**: `TST-UI-220`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `ProfileDomainMath.kt`, `ElevationProfile.kt`, `TelemetryMetricGraph.kt`, `MapDetailLayout.kt`
- **Rationale**: The system SHALL unify horizontal domain resolution (Distance vs. Time) and horizontal span calculation across all Aftermath telemetry visual analytics components via a single mathematical utility `ProfileDomainMath` (ATT-2032):
1. *Canonical Domain Evaluation Engine (`ProfileDomainMath.kt`)*: `ProfileDomainMath` SHALL provide pure deterministic methods to evaluate domain states and spans:
• `isTracklessWorkout(points: List<PathPoint>?): Boolean` and `isTracklessWorkout(totalDistance: Double, totalTimeSec: Long): Boolean`: SHALL return `true` if and only if `totalDistance <= 0.0 && totalTimeSec > 0L`.
• `isEffectiveTimeDomain(domain: ProfileXAxisDomain, points: List<PathPoint>?): Boolean` and `isEffectiveTimeDomain(domain: ProfileXAxisDomain, totalDistance: Double, totalTimeSec: Long): Boolean`: SHALL return `true` if the workout is trackless, or if `domain == ProfileXAxisDomain.TIME` and `totalTimeSec > 0L`. If `totalTimeSec <= 0L`, it SHALL safely return `false` (falling back to distance domain) to prevent division-by-zero or collapsed zero-span rendering.
• `calculateTotalSpan(domain: ProfileXAxisDomain, points: List<PathPoint>?): Double` and `calculateTotalSpan(domain: ProfileXAxisDomain, totalDistance: Double, totalTimeSec: Long): Double`: SHALL return `totalTimeSec.toDouble().coerceAtLeast(0.0)` if `isEffectiveTimeDomain(...)` is true, otherwise `totalDistance.coerceAtLeast(0.0)`.
2. *Unified Component Consumption*: Ad-hoc domain checks and diverging total span calculations in `ElevationProfile.kt`, `TelemetryMetricGraph.kt`, and `MapDetailLayout.kt` SHALL be eliminated and replaced with unified calls to `ProfileDomainMath`.
3. *MapDetailLayout Viewport Coordination*: In `MapDetailLayout.kt`, `totalSpan` SHALL dynamically evaluate `elevationTotalSpan` when `showElevationProfile == true` and `telemetryTotalSpan` when `showElevationProfile == false`, ensuring 100% viewport and zoom synchronization across all stacked charts and global zoom toolbars.
4. *Scrubbing Point Lookup Optimization*: Remaining linear scans in `TelemetryMetricGraph.kt` (`minByOrNull { abs(it.distance - currentDistance) }`) SHALL be replaced with `TelemetryMetricUtils.findNearestPoint`.

**Acceptance Criteria (Given-When-Then)**:
• *Given* any telemetry chart composable (`ElevationProfile`, `TelemetryMetricGraph`, `MapDetailLayout`),
• *When* evaluating the active horizontal domain,
• *Then* `ProfileDomainMath.isEffectiveTimeDomain` SHALL be used universally.
• *Given* a GPS track where `xAxisDomain == ProfileXAxisDomain.TIME` but `timeSec == 0L` across all points,
• *When* rendering charts,
• *Then* `isEffectiveTimeDomain` SHALL safely evaluate to `false` (Distance domain) and `calculateTotalSpan` SHALL return total distance, preventing collapsed zero-width canvases.
• *Given* a stationary/indoor workout without GPS coordinates (`distance <= 0.0` and `timeSec > 0L`),
• *When* viewed in Aftermath,
• *Then* all components SHALL consistently resolve to time-domain representation.
• *Given* an empty `PathPoint` list or null input,
• *When* calling `ProfileDomainMath` methods,
• *Then* `isTracklessWorkout` and `isEffectiveTimeDomain` SHALL return `false`, and `calculateTotalSpan` SHALL return `0.0`.

**Invariants**: Existing `ProfileXAxisDomain` enum definitions, `ElevationProfileZoomMath` coordinate scaling, and 100% unit test pass rate MUST NOT be broken.

### REQ-UI-262: Modular Component Architecture for Advanced Tuning Dialog (AdvancedTuningDialog) [Verified]
- **Tests**: `TST-UI-221`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `AdvancedTuningDialog.kt`, `CockpitTypographySection.kt`, `AmoledBatterySaverSection.kt`, `SensorsGpsFilterSection.kt`, `AftermathAnalysisSection.kt`, `WorkoutMasksAndCardsSection.kt`, `TuningFormControls.kt`, `RadarSettingsSection.kt`, `NavigationSection.kt`, `TuningDesignGuidelinesContractTest.kt`
- **Rationale**: The system SHALL decompose the monolithic `AdvancedTuningDialog.kt` into focused, dedicated category component files under package `com.atrainingtracker.trainingtracker.ui.settings.tuning.categories` and shared form controls under `com.atrainingtracker.trainingtracker.ui.settings.tuning` (ATT-2033, ATT-7536, ATT-7539):
1. *Dedicated Category Component Files (`categories/`)*:
• `CockpitTypographySection.kt`: Font family selection, numeric font weight, tabular numerals, 360dp menu constraints.
• `AmoledBatterySaverSection.kt`: Display timeout, dimming factor, dynamic battery saver toggles.
• `SensorsGpsFilterSection.kt`: GPS filter accuracy, slope calculations, elevation window.
• `AftermathAnalysisSection.kt`: Independent X-axis domain selectors for elevation vs. telemetry, pace ceiling slider.
• `WorkoutMasksAndCardsSection.kt`: 8 card visibility toggles, section reordering list controls, lap display mode segmented button.
• `NavigationSection.kt`: GPS and turn guidance toggles, route drawing preferences, standardized climb categorization formula segmented selector.
2. *Decoupled Form Controls (`TuningFormControls.kt`)*: Reusable controls (`TuningSliderItem`, `TuningToggleItem`, `SubordinateTuningToggleItem`, `TuningSegmentedButtonRow`, `TuningDropdownRow`) SHALL be maintained in `TuningFormControls.kt` for shared consumption across category sections.
3. *File Size Constraint*: No single source file in the tuning package SHALL exceed 400 lines of code.
4. *Behavioral & State Hoisting Invariants*: Category section composables SHALL be stateless, accepting immutable configuration models and targeted write callbacks. Dialog container `AdvancedTuningDialog.kt` SHALL manage dialog scaffolding, accordion expansion state, DataStore flow collection, and reset operations with 100% behavioral parity.
5. *Standardized Category Layout, Card-Free Structure & Design Guidelines Contract (`REQ-UI-262.5` — ATT-7536, ATT-7539)*: Category composables inside the accordion (such as `RadarSettingsSection.kt`, `NavigationSection.kt`, `AftermathAnalysisSection.kt`, `CockpitTypographySection.kt`) SHALL NOT nest `ElevatedCard` containers inside the accordion body; logical groups SHALL be demarcated using `HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))` and section subheaders. All toggles SHALL use `TuningToggleItem` (0.8f scale) or `SubordinateTuningToggleItem` (0.65f scale), all sliders SHALL use `TuningSliderItem`, and all binary and 2–4 option segmented selectors SHALL use `TuningSegmentedButtonRow` (`icon = {}`, `labelSmall`, `maxLines = 1`, `softWrap = false`). Raw `FilterChip` calls SHALL NOT be used for mutually exclusive binary or small discrete mode choices in `ui/settings/tuning/categories/` (allowlisted strictly for developer tier overrides in `SupporterDebugSection.kt`). Automated enforcement SHALL be verified by `TuningDesignGuidelinesContractTest`.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Amends `REQ-UI-262` (*Modular Component Architecture for Advanced Tuning Dialog*, `ATT-2033`). Targets `TuningFormControls.kt`, `RadarSettingsSection.kt`, `NavigationSection.kt`, `AftermathAnalysisSection.kt`, `CockpitTypographySection.kt`, `docs/design_guidelines.md`.
2. *Historical Origin & Commit Trace*: Sprint 2026-39.2 (`ATT-2033`, commit `096fd565`) extracted category files and created `TuningFormControls.kt`. Sprint 2026-46.5 (`ATT-7536`) added `TuningSegmentedButtonRow` and `TuningDropdownRow`, purged nested `ElevatedCard`s and raw `Switch(` blocks from `RadarSettingsSection.kt`, and codified design guidelines. Sprint 2026-46.5 (`ATT-7539`) standardized climb formula, aftermath X-axis, and font weight segmented rows, and created `TuningDesignGuidelinesContractTest`.
3. *Root Reason for Existing Formulation*: Form controls were initially extracted for sliders and toggles; segmented rows and dropdowns were written ad-hoc in new categories like `RadarSettingsSection.kt`, resulting in nested card anti-patterns and unstandardized button padding.
4. *Preservation of Core Invariants*: DataStore preference keys, reactive flow bindings, default values, stateless composable architecture, and 9-language localization parity are 100% preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* the tuning settings package,
• *When* reviewed,
• *Then* no single UI file SHALL exceed 400 lines of code, and each category section SHALL reside in its own dedicated file under `categories/`.
• *Given* the modularized dialog,
• *When* adjusting sliders, toggles, or resetting to factory defaults,
• *Then* all preferences SHALL continue to load, persist, and reset with 100% behavioral parity.
• *Given* existing UI and visual contract tests for Advanced Settings,
• *When* executed,
• *Then* all unit and visual contract tests SHALL pass cleanly without regressions.

**Invariants**: DataStore schema, public dialog API signatures, Material 3 styling tokens, and 100% test pass rate MUST NOT be broken.

### REQ-UI-263: Equipment Sensor Matrix Table Layout, Column Continuity, and Information Density Optimization [Verified]
- **Tests**: `TST-UI-222`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `EquipmentSensorMatrixScreen.kt`
- **Rationale**: The system SHALL optimize table layout, column alignment, vertical divider continuity, and information density in `EquipmentSensorMatrixScreen.kt` (ATT-2306):
1. *Top-Left Column Header Normalization*: The top-left corner cell of the matrix table SHALL display the localized column header `@string/Equipment` ("Ausrüstung" / "Equipment") rather than the redundant tab title `equipment_tab_sensor_matrix`.
2. *Continuous Vertical Grid Divider*: The 1dp vertical divider (`outlineVariant.copy(alpha = 0.6f)`) between the sticky equipment column and the horizontally scrollable sensor columns SHALL extend continuously without interruption across all table row types, including the sticky header row, section header rows (`MatrixSectionHeader`), and equipment item rows (`MatrixEquipmentRow`).
3. *Section Header Column Alignment*: `MatrixSectionHeader` SHALL partition its layout into a sticky leading section cell of width `STICKY_COLUMN_WIDTH` (rendering the section icon, section title, and count), followed by the 1dp continuous vertical divider, followed by a matching section surface container spanning the sensor columns.
4. *Expanded Sticky Column Width & Two-Line Typography*: `STICKY_COLUMN_WIDTH` SHALL be set to `184.dp` (expanded from 156.dp). Equipment name `Text` in `MatrixEquipmentRow` SHALL support up to 2 lines (`maxLines = 2`, `overflow = TextOverflow.Ellipsis`, line height = 18sp), ensuring equipment titles up to 25–30 characters render without premature single-line clipping.
5. *Sensor Column Visual Alignment*: Sensor column cells in the sticky top header and content rows SHALL render with subtle vertical separation guides (`outlineVariant.copy(alpha = 0.2f)`), providing clear visual tracking from sensor names down to checkboxes.
6. *Preservation of Functional & Accessibility Invariants*: 1-tap Material 3 Checkbox persistence via `EquipmentViewModel.setSensorLink` and `EquipmentDbHelper.setDeviceLink`, bi-directional reactive sync (`REQ-UI-257`), sticky scrolling behavior, empty state rendering, and 9-language localization parity MUST NOT be altered.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an athlete viewing the "Sensor-Matrix" tab in `EquipmentTabsScreen`,
• *When* inspecting the top-left table cell,
• *Then* it SHALL display "Ausrüstung" (DE) / "Equipment" (EN) matching `R.string.Equipment`, and SHALL NOT duplicate the tab name "Sensor-Matrix".
• *Given* the matrix table rendering with Bikes and Shoes sections,
• *When* scanning vertically down the divider line between equipment names and sensor columns,
• *Then* the 1dp vertical divider SHALL continue seamlessly through section headers ("Räder (N)", "Schuhe (N)") without visual gaps.
• *Given* an equipment item with a long name (e.g. "Brooks Green Silence 2", "Specialized S-Works Tarmac SL8"),
• *When* rendered in the sticky left column,
• *Then* the column width of 184dp and 2-line text wrapping SHALL display the full name or significantly reduce truncation compared to 156dp single-line clipping.
• *Given* multiple paired remote sensors in the matrix,
• *When* viewing column headers and checkboxes,
• *Then* subtle vertical guides SHALL visually demarcate sensor columns, aligning header text and checkboxes.
• *Given* any checkbox in the matrix,
• *When* tapped,
• *Then* `onToggleLink` SHALL be invoked, updating SQLite via `EquipmentDbHelper.setDeviceLink` with reactive cross-screen sync.

**Invariants**: SQLite schemas, reactive synchronization, 9-language localization, and 100% unit test pass rate MUST NOT be broken.

### REQ-UI-264: Workout Cards & Details Tuning Dialog Layout, Typography Wrapping, and Reordering Ergonomics Optimization [Verified]
- **Tests**: `TST-UI-223`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
6. *File Size Constraint*: `WorkoutMasksAndCardsSection.kt` SHALL remain strictly under 400 lines of code (`REQ-UI-262`).
7. *Preservation of Functional Invariants*: Preference bindings, reordering logic, DataStore persistence, and 9-language localization parity MUST NOT be broken.

**Acceptance Criteria (Given-When-Then)**:
• *Given* the 'Trainingsliste & Details' card in Advanced Tuning Settings,
• *When* viewing section titles in German (e.g. "Runden-Übersicht", "Telemetrie-Diagramme"),
• *Then* titles SHALL NOT break or wrap mid-word across multiple lines.
• *Given* any row in the section table,
• *When* inspecting the reorder buttons,
• *Then* they SHALL be vertically arranged within a compact 32dp container with Up and Down icons and active bounds checks.
• *Given* the matrix table rendering with Laps enabled,
• *When* inspecting the list rows and reordering items,
• *Then* all 8 table rows SHALL maintain uniform height and rhythm, and `LapDisplayMode` SHALL render cleanly below the table.
• *Given* the table headers,
• *When* viewed,
• *Then* 'In Liste' and 'In Details' titles SHALL align directly above their respective 50dp checkbox columns.

**Invariants**: DataStore schema, reordering logic, and 100% unit test pass rate MUST NOT be broken.
- **Rationale**: The system SHALL optimize layout, text wrapping, and reordering ergonomics in `WorkoutMasksAndCardsSection.kt` (ATT-2305):
1. *Compact Vertical Reorder Controls*: The reorder controls for moving sections up and down SHALL be formatted as a compact vertical stack of width `32.dp`, containing two `IconButton`s of size `22.dp` with icons of size `18.dp`, reducing horizontal consumption from 64dp to 32dp.
2. *Standardized Checkbox Column Widths*: The checkbox columns for "In Liste" and "In Details" in both the header and rows SHALL have width `50.dp` (preserving standard Material 3 48dp touch targets), reducing horizontal consumption from 64dp to 50dp each.
3. *Horizontal Title Space Expansion*: The horizontal space available for section title `Text` (`weight(1f)`) SHALL increase by at least 50dp (from ~112dp to ~172dp in standard mobile dialogs), enabling German titles ("Runden-Übersicht", "Telemetrie-Diagramme", "Strava-Aktivitätsdaten") to display on a single line and preventing mid-word hyphenation.
4. *Column Header Vertical Alignment*: The header row SHALL utilize a leading `Spacer(width = 32.dp)` and header label containers of width `50.dp` for `tuning_matrix_col_list` and `tuning_matrix_col_details`, centered directly over the checkboxes.
5. *Decoupled Lap Display Mode Sub-Control*: When Laps are enabled in either list or details (`workoutCardPrefs.showLaps

### REQ-UI-265: Workout List Filtering by Origin Source Attribute (Tracked, TCX, GPX, FIT) [Verified]
- **Tests**: `TST-UI-224`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `WorkoutFilterCriteria.kt`, `WorkoutFilterBottomSheet.kt`, `ActiveFilterChipsRow.kt`, `WorkoutTabsScreen.kt`, `WorkoutSummariesViewModel.kt`, `strings.xml` (all 9 locales)
- **Rationale**: The system SHALL provide origin source filtering for workout sessions across the domain model, modal filter bottom sheet, active filter strip, and reactive ViewModel pipeline (ATT-2303):
1. *Origin Source Filter Domain Model (`WorkoutFilterCriteria.kt`)*: `WorkoutFilterCriteria` SHALL define nullable property: `val source: WorkoutSource? = null`. When `source != null`, `matches(workout)` SHALL evaluate `workout.source == source`. `activeFilterCount` SHALL increment by 1 when `source != null`. `toJson()` and `fromJson()` SHALL serialize and deserialize `source` via its enum name, falling back safely to `null` if absent.
2. *Origin Source Filter Section (`WorkoutFilterBottomSheet.kt`)*: `WorkoutFilterBottomSheet` SHALL render an "Origin Source" section (`filter_section_origin_source`) positioned directly after Workout Attributes and before Distance Intervals, respecting the prioritized layout order of `REQ-UI-194`. The section SHALL render 4 single-selectable `FilterChip` items corresponding to `WorkoutSource.TRACKED` (`workout_source_tracked`), `WorkoutSource.TCX` (`workout_source_tcx`), `WorkoutSource.GPX` (`workout_source_gpx`), and `WorkoutSource.FIT` (`workout_source_fit`). Tapping an unselected chip SHALL set the source filter to that option; tapping the selected chip SHALL deselect it (`source = null`). `onApply` SHALL propagate `source` and `onClearAll` SHALL reset `source = null`.
3. *Active Filter Chip Presentation (`ActiveFilterChipsRow.kt`)*: When `criteria.source != null`, `ActiveFilterChipsRow` SHALL render an active removable filter chip labeled with the localized name of the active origin source. Tapping the remove ("X") icon SHALL invoke `onRemoveSource: () -> Unit`, clearing the source filter dimension while preserving other active criteria.
4. *Screen & ViewModel Reactive Wiring (`WorkoutTabsScreen.kt`, `WorkoutSummariesViewModel.kt`)*: `WorkoutTabsScreen` SHALL supply `onRemoveSource = { onUpdateFilterCriteria { it.copy(source = null) } }` to `ActiveFilterChipsRow`. `WorkoutSummariesViewModel.workouts` reactive combining pipeline SHALL evaluate `criteria.matches(it)` over all workouts, updating the emitted list instantly without database re-queries.
5. *100% 9-Language Localization Parity*: String resource `filter_section_origin_source` SHALL be defined across all 9 supported application locales (EN, DE, ES, FR, IT, JA, NL, PL, PT). Existing string resources `workout_source_tracked`, `workout_source_tcx`, `workout_source_gpx`, and `workout_source_fit` SHALL be verified across all 9 locales.
6. *Preservation of Functional Invariants*: Sport tabs, search queries, date/time ranges, equipment selections, distance/duration intervals, favorite locations, route clusters, sort orders, and DataStore serialization backward compatibility MUST NOT be altered.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an athlete opening `WorkoutFilterBottomSheet` in the Workouts journal,
• *When* viewing filter sections,
• *Then* an "Origin Source" section SHALL be visible with chips for Tracked, TCX, GPX, and FIT.
• *Given* a workout list containing both live tracked sessions and imported FIT/TCX/GPX workouts,
• *When* the athlete selects the "FIT" source chip and applies filters,
• *Then* only workouts with `source == WorkoutSource.FIT` SHALL be displayed in the workout list.
• *Given* an active source filter (e.g. "FIT"),
• *When* inspecting `ActiveFilterChipsRow`,
• *Then* an active removable chip "FIT" SHALL be displayed, and tapping its remove icon SHALL clear the source filter while preserving other active filters.
• *Given* an active source filter along with other filters (e.g. Sport = Bike, Year = 2024),
• *When* tapping "Alle zurücksetzen" (Clear All) in the filter sheet or the strip,
• *Then* all filters including `source` SHALL be reset to null, restoring the full unfiltered list.

**Invariants**: Sorting orders, sport tab partitioning, DataStore schema backward compatibility, and 100% unit test pass rate MUST NOT be broken.

### REQ-UI-266: Aftermath/Details: Immediate Collapsing Header Quick Return Expansion upon Downward Scrolling [Verified]
- **Tests**: `TST-UI-225`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `CollapsingAppBarNestedScrollConnection.kt`, `MapDetailLayout.kt`
- **Rationale**: The system SHALL restore immediate Quick Return downward header expansion in `CollapsingAppBarNestedScrollConnection.kt` and `MapDetailLayout.kt` (ATT-2304):
1. *Immediate Downward Expansion in `onPreScroll`*: When scrolling downward (`available.y > 0f`) and the header is currently in a collapsed or partially collapsed state (`rawOffset < 0f`), `CollapsingAppBarNestedScrollConnection.onPreScroll` SHALL consume available delta up to `0f`, immediately translating `appBarOffset` towards `0`. The delta consumed SHALL be `(rawOffset + available.y).coerceIn(-appBarMaxHeight.toFloat(), 0f) - rawOffset`. Once `rawOffset == 0f` (header fully expanded), `onPreScroll` SHALL return `Offset.Zero` for downward gestures, allowing all subsequent downward delta to pass uninhibited to child scroll containers.
2. *Smooth Upward Collapse Preservation*: For upward gestures (`available.y < 0f`), when `rawOffset > -appBarMaxHeight`, `onPreScroll` SHALL continue to consume deltas to smoothly collapse `rawOffset` towards `-appBarMaxHeight.toFloat()`. Once `rawOffset == -appBarMaxHeight.toFloat()`, `onPreScroll` SHALL return `Offset.Zero`, allowing child scroll containers to scroll their contents upward without locking.
3. *Sub-Pixel Precision Invariant*: The connection SHALL maintain high-precision floating-point delta accumulation in `rawOffset: Float`, eliminating integer truncation micro-stutters (`available.y.toInt()`), with `appBarOffset` exposed as `rawOffset.roundToInt()`.
4. *Child Scroll Coordination*: Child scroll containers (e.g. `lowerColumn` in `MapDetailLayout`, `LazyColumn` in `WorkoutTabsScreen`, `WorkoutSummariesListFragment`, `SegmentsTabsScreen`, etc.) SHALL receive full unconsumed scroll deltas once the header reaches its respective boundary (0 when expanding downward, `-appBarMaxHeight` when collapsing upward).
5. *Configurable Quick Return Mode*: `CollapsingAppBarNestedScrollConnection` SHALL provide a `quickReturn: Boolean = true` parameter (defaulting to `true`), allowing callers full control while restoring Quick Return globally by default.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Refines and supersedes Clause 1 of `REQ-UI-254` (*Aftermath/Details: Non-Blocking Nested Scroll Dispatch, Sub-Pixel Precision & Optimized High-Density Metadata Rendering in MapDetailLayout*).
2. *Historical Origin & Commit Trace*: Sprint `2026-40.14` (`ATT-2178`, commit `91fb65c1` and `083d1e04`).
3. *Root Reason for Existing Formulation*: Clause 1 in `REQ-UI-254` deferred downward scroll consumption to `onPostScroll` based on a concern that downward gestures in lower analytics charts would squash the view. Physical testing revealed this broke the Quick Return gesture expected by users.
4. *Preservation of Core Invariants*: Sub-pixel floating precision (`rawOffset`), bounded height translation `[-appBarMaxHeight, 0]`, minimum map height protection (`SplitPaneMath.MIN_MAP_HEIGHT` = 120dp), and smooth 60/120 fps rendering are 100% strictly preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* a detailed workout view where the header is collapsed upward,
• *When* the user scrolls downward,
• *Then* the header SHALL expand and scroll back down smoothly into view in `onPreScroll`.
• *Given* the header expanding downward,
• *When* `appBarOffset` reaches `0` (fully expanded),
• *Then* all remaining and subsequent downward scroll delta SHALL pass to child scroll containers without gesture trapping.
• *Given* child scroll containers with extensive segments/laps,
• *When* scrolling within the view,
• *Then* scrolling SHALL remain fluid and responsive without gesture trapping or frame jitter.

**Invariants**: Custom callers preserved; minimum map height (120dp) guaranteed; 100% clean-room test suite pass rate.

### REQ-UI-267: Routes & Segments: Position Elevation Profile Below Map with Interactive Zooming, Panning, and Resizable Split-Pane Viewport in MapDetailLayout [Verified]
- **Tests**: `TST-UI-226`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Rationale**: The system SHALL position the elevation profile strictly below the map in Route and Segment detail views (`RouteOnMapScreen` and `SegmentOnMapScreen`), activate a two-pane resizable split layout with an interactive draggable splitter, and enable interactive zooming and panning via `GlobalTelemetryZoomToolbar` (ATT-2311):
1. *Lower Section Presence Formulation (`MapDetailLayout.kt`)*: The system SHALL define `val hasLowerSection = (showElevationProfile

### REQ-UI-268: Truthful Live Sensor Connection Status and Canonical Priority Sorting in `SensorSourceDialog` [Verified]
- **Tests**: `TST-UI-227`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `DeviceStatusRow.kt`, `SensorSourceDialog.kt`
- **Rationale**: The system SHALL truthfully reflect active sensor connection status in the tracking cockpit's `SensorSourceDialog` by parameterizing `DeviceStatusRow` and propagating live telemetry connection state from `DeviceIdentityBlock`, and SHALL deterministically sort active backup sensors and disconnected devices according to `MySensorManager` canonical priority hierarchy (ATT-2194, ATT-4375):
1. *Parameterized Connection Override in DeviceStatusRow (`DeviceStatusRow.kt`)*: `DeviceStatusRow` SHALL declare `isConnected: Boolean = device.isConnected` as an optional parameter. When computing `stateText`, the composable SHALL evaluate `isConnected`: (a) When `isConnected == true`, `stateText` SHALL evaluate to `stringResource(R.string.devices_available)` ("verfügbar" / "available"); (b) When `isConnected == false`, `stateText` SHALL evaluate to relative time if `device.lastSeen` is present, or `stringResource(R.string.devices_not_connected)` ("nicht verbunden" / "not connected").
2. *Live Telemetry Propagation in SensorSourceDialog (`SensorSourceDialog.kt`)*: In `DeviceIdentityBlock`, `DeviceStatusRow` SHALL be invoked with `isConnected = isConnected`. For primary active sources (`sourceDevice`) and active backups (`activeBackups`), `isConnected` SHALL evaluate to `true`, rendering `R.string.devices_available` next to the battery percentage. For disconnected devices (`notConnected`), `isConnected` SHALL evaluate to `false`, rendering `R.string.devices_not_connected` or relative last seen.
3. *Strict Preservation of Backward Compatibility*: All existing call sites of `DeviceStatusRow` omitting `isConnected` (`DeviceItem.kt`, `EditDeviceDialog.kt`) SHALL continue to resolve `device.isConnected` without behavioral alteration.
4. *Layout and Alignment Invariants*: The 2-row high-density layout (Icon, Name/LED, Battery/Status), 18dp horizontal center axis alignment between status LED and battery icon, and 100% clean-room test suite pass rate MUST NOT be broken.
5. *Canonical Priority Sorting in `SensorSourceDialog.kt` (`REQ-UI-268.5`)*:
• In `SensorSourceDialog.kt`, `activeBackups` SHALL be sorted using `compareBy<DeviceTelemetry> { telemetry -> val dev = allDevices.find { it.id == telemetry.deviceId }; MySensorManager.getPriorityRank(sensorType, dev?.deviceType) }.thenBy { telemetry -> allDevices.find { it.id == telemetry.deviceId }?.deviceName ?: "" }.thenBy { it.deviceId }`.
• In `SensorSourceDialog.kt`, `notConnected` SHALL be sorted using `compareBy<DeviceUiData> { MySensorManager.getPriorityRank(sensorType, it.deviceType) }.thenBy { it.deviceName }.thenBy { it.id }`.
• This guarantees that active backups and disconnected devices are displayed deterministically in strict accordance with `MySensorManager`'s fallback hierarchy (e.g. Google Fused above Network) rather than arbitrary hash map or SQLite row ID order.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Refines and supersedes the status row coupling in `REQ-UI-050` (*Optimized Sensor Identity Layout*), aligns with `REQ-UI-049` (*Sensor Source Dialog*), and is extended by `ATT-4375` targeting `SensorSourceDialog.kt`.
2. *Historical Origin & Commit Trace*: `REQ-UI-050` (Sprint `2026-40.1`), `REQ-UI-268` via `ATT-2194` (Sprint `2026-41.2`), refined in `ATT-4375` (Sprint `2026-43.2`).
3. *Root Reason for Existing Formulation*: In `ATT-2194`, `SensorSourceDialog` was updated to truthfully reflect connection status, but `activeBackups` retained the iteration order of `allTelemetry` (a `ConcurrentHashMap` from `DeviceManager`), producing non-deterministic UI order. Meanwhile, `notConnected` retained database query order, which listed Network above Google Fused due to legacy insertion order. Adding deterministic priority sorting ensures the dialog presents options in the exact operational fallback sequence used by `MySensorManager`.
4. *Preservation of Core Invariants*: Visual presentation (LED 18dp horizontal axis alignment, 2-row density, battery text), truthful `(verfügbar)` / `(nicht verbunden)` connection states, and backward-compatible `DeviceStatusRow` behavior are 100% preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an athlete in the tracking cockpit with an actively connected primary sensor (e.g. smartphone barometer streaming altitude, or HR chest strap streaming heart rate),
• *When* tapping the corresponding sensor metric in `SensorStatus` to open `SensorSourceDialog`,
• *Then* the primary device under \"Quelle\" SHALL display a green connection LED and its status row SHALL display the battery percentage followed by `(verfügbar)` (or localized equivalent, e.g. `(available)`) and SHALL NOT display `(nicht verbunden)`.
• *Given* secondary sensors actively streaming telemetry for the inspected metric,
• *When* viewing the \"Aktive Backups\" section in `SensorSourceDialog`,
• *Then* every active backup sensor SHALL display a green connection LED and its status row SHALL display `(verfügbar)`, and the backup items SHALL be sorted deterministically by canonical priority rank (e.g. Google Fused before Network).
• *Given* paired sensors that are not currently connected or delivering telemetry,
• *When* viewing the \"Nicht verbunden\" section in `SensorSourceDialog`,
• *Then* their status row SHALL display `(nicht verbunden)` or the relative last-seen timestamp, and the devices SHALL be sorted deterministically by canonical priority rank (e.g. Google Fused before Network).
• *Given* paired device lists in `DeviceItem.kt` or `EditDeviceDialog.kt` where `isConnected` is not explicitly passed,
• *When* `DeviceStatusRow` is rendered,
• *Then* it SHALL default cleanly to `device.isConnected`.

**Invariants**: Layout alignment, battery percentage, backward compatibility with device list call sites, and 100% test pass rate MUST NOT be broken.

### REQ-UI-269: Preserved Bullet Point Line Breaks in ClusterInfoDialog and 9-Language Localization Parity [Verified]
- **Tests**: `TST-UI-228`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `strings.xml` (all 9 locales), `ClusterInfoDialog.kt`
- **Rationale**: The system SHALL ensure that the 4 spatial criteria in the workout clustering educational bottom sheet (`ClusterInfoDialog.kt`) render on distinct lines separated by clean line breaks across all 9 application locales (ATT-2195):
1. *Literal Newline Encoding in String Resources*: In string resource `cluster_info_fingerprint_desc`, all paragraph and bullet point separations SHALL be encoded using literal `\n` escape sequences instead of the XML character entity `&#10;`, preventing AAPT2 from collapsing line breaks into single spaces.
2. *9-Language Localization Parity*: String resource `cluster_info_fingerprint_desc` SHALL be updated across all 9 supported application locales (`values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`). Each translation SHALL contain zero instances of `&#10;` and SHALL contain literal `\n` line breaks preceding each bullet point (`•`).
3. *Textual and Parameter Invariant Preservation*: All mathematical tolerance values (Start/Finish: 200 m, Apex: 400 m, Total Distance: 20%, Altitude Position: 400 m) and existing localized phrasing across all 9 languages MUST remain completely preserved.
4. *Dialog Layout & Visual Invariants*: `ClusterInfoDialog.kt` composable hierarchy, section dividers, typography styles, title icon, close confirmation action (`AppDialogActions.Confirm`), and 100% clean-room test suite pass rate MUST NOT be altered.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Refines `REQ-UI-137` (*Cluster Algorithm & Lieblingsstrecken Educational Info Modal*).
2. *Historical Origin & Commit Trace*: Sprint `2026-40.4` (`ATT-1422` / `ATT-1449`, commit `f7136067`).
3. *Root Reason for Existing Formulation*: In `REQ-UI-137`, `cluster_info_fingerprint_desc` was defined to explain the 6-dimensional spatial metric using bullet points. During translation authoring, `&#10;` was used under the assumption that XML entities would be preserved. AAPT2 normalizes unquoted whitespace, causing the bullet points to run together into a continuous block.
4. *Preservation of Core Invariants*: All spatial tolerance values (200 m, 400 m, 20%), educational concepts, and localized translations across all 9 languages are 100% preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an athlete opening the workout clustering information dialog (`ClusterInfoDialog`),\u003cbr\u003e• *When* viewing the \"Topologischer 3D-Fingerabdruck\" (or \"3D Topological Fingerprint\") section,\u003cbr\u003e• *Then* the introductory sentence SHALL be followed by a line break, and each of the 4 bullet points (Start & Finish, Apex, Total Distance, Min & Max Altitude) SHALL begin on its own separate line.\u003cbr\u003e• *Given* any of the 9 supported application locales (EN, DE, ES, FR, IT, JA, NL, PL, PT),\u003cbr\u003e• *When* `cluster_info_fingerprint_desc` is compiled and rendered,\u003cbr\u003e• *Then* the string SHALL contain zero `&#10;` entities and at least 4 `\n` line breaks separating each bullet criterion.\u003cbr\u003e• *Given* `ClusterInfoDialog.kt`,\u003cbr\u003e• *When* rendered,\u003cbr\u003e• *Then* it SHALL preserve `AppModalBottomSheet` layout, info icon, and `AppDialogActions.Confirm` button.\u003cbr\u003e\u003cbr\u003e**Invariants**: Algorithm parameters, tolerance values, translation content, and 100% clean-room test suite pass rate MUST NOT be broken.

### REQ-UI-270: Clamped Maximum LiveSegment Bottom Sheet Expansion to Elevation Profile Content Height [Verified]
- **Tests**: `TST-UI-229`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
3. *Modifier Parameterization (`LiveSegmentSheet.kt`)*: `LiveSegmentSheet` composable SHALL accept an optional `modifier: Modifier = Modifier`, forwarding `modifier.fillMaxWidth().wrapContentHeight()` to `MapDetailLayout`.
4. *Cockpit Visibility & Clamped Bottom Sheet Expansion (`SensorGridScreen.kt`)*: In `SensorGridScreen.kt`, when `LiveSegmentSheet` is rendered inside `BottomSheetScaffold`, the bottom sheet's measured height SHALL equal the natural content height of the segment header, live stats, and elevation profile canvas (~280–320dp). At `SheetValue.Expanded`, the bottom sheet offset SHALL equal `scaffoldHeight - sheetHeight`. The bottom sheet SHALL NOT expand to the top of the viewport (`offset = 0`), preserving complete visibility and touch interaction for primary cockpit telemetry (heart rate, cadence, power, speed). Dragging between `SheetValue.PartiallyExpanded` (`PeekHeightLiveSegment + navBarHeight`) and `SheetValue.Expanded` (elevation profile revealed) SHALL remain fully supported.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Refines `REQ-UI-047` (*Live Segment Sheet Height Constraint*) and `REQ-UI-213` (*Resilient Map Preview Visibility & Scrollable Telemetry Layout Architecture*).
2. *Historical Origin & Commit Trace*: `REQ-UI-047` was authored early to prevent the live segment popup from covering cockpit telemetry. Refactorings in `REQ-UI-223` (Split pane), `REQ-UI-245` (Upper metadata), and `REQ-UI-246` (Persistent sticky lower viewport) introduced `Modifier.fillMaxSize()` on the child viewport container Box, inadvertently overriding wrap-content behavior.
3. *Root Reason for Existing Formulation*: Full-screen detail inspection screens (`RouteOnMapScreen`, `TrackOnMapScreen`, `SegmentOnMapScreen`) require the map and scrollable telemetry to fill the remaining screen space (`fillMaxSize()`). Line 580 was hardcoded to `fillMaxSize()` without checking `showMap
- **Rationale**: The system SHALL restrict the maximum expansion of the `LiveSegmentSheet` bottom sheet during active tracking strictly to the combined height of the segment header, live stats, and elevation profile, ensuring primary cockpit telemetry remains unobstructed across all sheet states (ATT-2231):
1. *Adaptive Viewport Height Constraints (`MapDetailLayout.kt`)*: In `MapDetailLayout.kt`, when `showMap == false` and `hasScrollableContent == false` (ambient bottom sheet mode for `LiveSegmentSheet`), the resizable viewport container `Box` SHALL apply `Modifier.fillMaxWidth().wrapContentHeight()` instead of `Modifier.fillMaxSize()`. When `showMap == true` or `hasScrollableContent == true` (full-screen detail inspection views), the resizable viewport container `Box` SHALL continue to apply `Modifier.fillMaxSize()`.
2. *Conditional NestedScroll Attachment (`MapDetailLayout.kt`)*: The root `BoxWithConstraints` SHALL attach `Modifier.nestedScroll(connection)` if and only if `showMap

### REQ-UI-271: Settings/Tuning: Simplified Preposition-Free Column Headers (List & Details) in Workout Cards & Details Matrix [Verified]
- **Tests**: `TST-UI-231`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `WorkoutMasksAndCardsSection.kt`, `strings.xml` (all 9 locales)
- **Rationale**: The system SHALL eliminate redundant prepositional phrasing from the column headers of the 2-column feature matrix table in the Workout Cards & Details section (`WorkoutMasksAndCardsSection.kt`) of Advanced Settings across all 9 supported application locales (ATT-2383):
1. *Preposition-Free Singular Noun Headers*: The string resources `tuning_matrix_col_list` and `tuning_matrix_col_details` SHALL be defined as concise singular nouns across all 9 locales: `values` (EN: "List" / "Details"), `values-de` (DE: "Liste" / "Details"), `values-es` (ES: "Lista" / "Detalles"), `values-fr` (FR: "Liste" / "Détails"), `values-it` (IT: "Elenco" / "Dettagli"), `values-ja` (JA: "一覧" / "詳細"), `values-nl` (NL: "Lijst" / "Details"), `values-pl` (PL: "Lista" / "Szczegóły"), `values-pt` (PT: "Lista" / "Detalhes").
2. *Defensive Header Typography & Truncation Protection (`WorkoutMasksAndCardsSection.kt`)*: Header text composables for both column titles SHALL enforce `maxLines = 1`, `overflow = TextOverflow.Ellipsis`, and `textAlign = TextAlign.Center`, preventing multi-line wrapping and baseline misalignment within the standard 50.dp column width bound.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Refines Clause 4 of `REQ-UI-264` (*Workout Cards & Details Tuning Dialog Layout, Typography Wrapping, and Reordering Ergonomics Optimization*) and Clause 3 of `REQ-UI-240` (*Settings/Aftermath: Matrix Table Presentation for Configurable Aftermath Sections*).
2. *Historical Origin & Commit Trace*: Sprint 2026-40.12 (`ATT-2030`, commit `02f3d414`) introduced prepositional titles ("In Liste" / "In Details"); Sprint 2026-40.14 (`ATT-2305`, commit `6817b037`) narrowed column containers to 50dp without updating string lengths.
3. *Root Reason for Existing Formulation*: Prepositions were originally chosen when columns were 64dp–76dp wide. At 50dp width, multi-word titles with prepositions collide horizontally and wrap onto 2 lines. In a tabular context directly above checkboxes, prepositions are redundant and concise nouns convey complete semantic meaning without wrapping.
4. *Preservation of Core Invariants*: 50dp column width, Material 3 48x48dp touch targets, DataStore preferences (`WorkoutCardPreferences`, `WorkoutDetailPreferences`), reordering logic, subtitle formatting, and 100% 9-language translation parity are strictly preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* the 'Trainingsliste & Details' section in Advanced Settings,
• *When* viewed in any of the 9 supported locales,
• *Then* the column headers for List and Details SHALL display concise singular nouns without prepositions.
• *Given* the 50dp column width containers,
• *When* rendering header text,
• *Then* header titles SHALL render on a single centered line without line breaking or baseline distortion.
• *Given* `TranslationParityTest`,
• *When* executed,
• *Then* all 9 localized `strings.xml` files SHALL maintain 100% parity with zero missing keys and zero format mismatches.

**Invariants**: DataStore persistence keys, 48dp minimum touch targets, 9-language localization parity, and parent ticket Human Decision Gate MUST NOT be compromised.

### REQ-UI-272: Elevation Profile Adaptive X-Axis Tick Spacing and Dynamic Label Collision Prevention [Verified]
- **Tests**: `TST-UI-232`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `ElevationProfile.kt`, `ElevationProfileZoomMath.kt`
- **Rationale**: The system SHALL eliminate overlapping distance and time tick labels on the X-axis of the elevation profile (`ElevationProfile.kt`) across all card previews (`RouteItem`, `SegmentItem`, `WorkoutSummary`) and detailed inspection views (`MapDetailLayout`, `TrackOnMapScreen`, `SensorGridScreen`) (ATT-2385):
1. *Adaptive Step Interval Calibration (`ElevationProfileZoomMath.kt`)*: `ElevationProfileZoomMath.calculateAdaptiveDistanceStep` SHALL provide balanced intermediate step increments for short and mid-range tracks. For metric units: `> 50_000` $\implies$ 10,000 m; `> 20_000` $\implies$ 5,000 m; `> 10_000` $\implies$ 2,000 m; `> 5_000` $\implies$ 1,000 m; `> 2_000` $\implies$ 500 m; `> 800` $\implies$ 250 m; `> 300` $\implies$ 100 m; `> 150` $\implies$ 50 m; else $\implies$ 25 m. For imperial units: `> 30` $\implies$ 5 mi; `> 10` $\implies$ 2 mi; `> 3` $\implies$ 1 mi; `> 1` $\implies$ 0.5 mi; `> 0.5` $\implies$ 0.25 mi; `> 0.2` $\implies$ 0.1 mi; else $\implies$ 0.05 mi. The step calculation MAY accept an optional canvas width parameter.
2. *Dynamic Clearance Invariant & Adjacent Label Collision Guard (`ElevationProfile.kt`)*: In both distance and time domain X-axis rendering loops, the system SHALL track the rightmost horizontal pixel coordinate of the previously rendered label (`lastDrawnRightX`). An intermediate candidate tick at canvas coordinate `x` with label text width $w_{\text{label}}$ SHALL be rendered if and only if: (a) Start clearance: $x - \frac{w_{\text{label}}}{2} \ge \text{startClearanceThreshold}$ (where $\text{startClearanceThreshold} = \text{startLabelWidth} + \text{minSpacingPx}$ when `startLabel` is drawn, or $\text{minSpacingPx}$ otherwise); (b) Preceding label clearance: $x - \frac{w_{\text{label}}}{2} \ge \text{lastDrawnRightX} + \text{minSpacingPx}$; (c) Terminal clearance: $x + \frac{w_{\text{label}}}{2} \le \text{width} - \text{endLabelWidth} - \text{minSpacingPx}$, where $\text{minSpacingPx} \ge 24.\text{dp.toPx()}$.
3. *Synchronized Tick Mark Suppression*: When an intermediate candidate tick label is suppressed due to insufficient clearance, the vertical tick mark line (`drawLine`) at position `x` SHALL likewise be suppressed, ensuring tick marks and labels remain 100% synchronized.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Refines Clause 5 of `REQ-UI-192` (*Interactive Elevation Profile Zoom, Pan & Centered Scaling Navigation*) and `REQ-UI-201` (*Synchronized Multi-Metric Scrubbing on Elevation Profile with Configurable X-Axis Domain Architecture*).
2. *Historical Origin & Commit Trace*: `ATT-527` (Sprint 2026-40.4) introduced initial `calculateAdaptiveDistanceStep`; `ATT-1391` (Sprint 2026-40.5) added time-domain ticks; `ATT-1819` / `REQ-UI-220` (Sprint 2026-40.7) introduced `shouldRenderMilestoneLabel` for telemetry charts.
3. *Root Reason for Existing Formulation*: `ElevationProfile.kt` originally used a static `x > 60f` threshold and coarse step thresholds (jumping from 500m straight down to 100m at 1,500m), without dynamic label width collision checking.
4. *Preservation of Core Invariants*: Elevation bounds sanitization (`REQ-UI-126`), horizontal zooming/panning (`REQ-UI-192`), contextual zoom control suppression in preview cards (`REQ-UI-197`), and scrubbing accuracy remain strictly preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an elevation profile for a segment or route of distance 1.47 km rendered on a standard preview card,
• *When* the X-axis is drawn,
• *Then* intermediate ticks SHALL render with a step of 250 m or 500 m, every label SHALL maintain at least 24 dp clearance from adjacent labels, and zero labels SHALL collide or overlap with each other or with the 1.47 km terminal label.
• *Given* any track distance from 50 m to 200 km rendered on any canvas width,
• *When* the elevation profile X-axis is rendered,
• *Then* all rendered distance or time labels SHALL satisfy the clearance invariants with zero overlapping text.
• *Given* `ElevationProfileZoomMathTest`,
• *When* executed,
• *Then* adaptive distance step tests and collision guard unit tests SHALL pass with 100% success rate.

**Invariants**: Grade color mapping, vertical profile height, multi-touch pinch zoom, and 100% clean-room test pass rate MUST NOT be broken.

### REQ-UI-273: Routes & Segments: Dynamically Maximized Map with Bottom-Anchored Intrinsic Elevation Profile in MapDetailLayout [Verified]
- **Tests**: `TST-UI-233`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `MapDetailLayout.kt`, `RouteOnMapScreen.kt`, `SegmentOnMapScreen.kt`
- **Rationale**: The system SHALL decouple non-scrollable single-profile viewports (`hasScrollableContent == false`) from the proportional 50/50 split-pane in `MapDetailLayout.kt` (ATT-2386):
1. *Differentiated Viewport Allocation (`MapDetailLayout.kt`)*: When `showMap == true` and `hasLowerSection == true`: (a) *Scrollable Multi-Section Viewports (`hasScrollableContent == true`)*: For workout aftermath inspection (`TrackOnMapScreen`), the system SHALL continue to provide the interactive resizable split-pane with `SplitPaneDivider`, `splitFraction` (default 50/50), and vertical scrolling across telemetry charts and analytics per `REQ-UI-223` and `REQ-UI-267`; (b) *Non-Scrollable Single-Profile Viewports (`hasScrollableContent == false`)*: For route and segment detail screens (`RouteOnMapScreen` and `SegmentOnMapScreen`), the system SHALL eliminate the proportional split fraction and `SplitPaneDivider`.
2. *Dynamically Maximized Map Viewport*: In non-scrollable viewports (`!hasScrollableContent`), the map container (`mapBox`) SHALL receive `Modifier.weight(1f).heightIn(min = SplitPaneMath.MIN_MAP_HEIGHT).fillMaxWidth()`, expanding dynamically into all remaining vertical space above the elevation profile.
3. *Bottom-Anchored Intrinsic Elevation Profile*: In non-scrollable viewports (`!hasScrollableContent`), the lower elevation profile container SHALL apply `Modifier.fillMaxWidth().wrapContentHeight()`, hosting `GlobalTelemetryZoomToolbar` (height 40 dp), elevation header and profile canvas (~100–228 dp), and `scrubbingOverlay`, with its bottom edge sitting flush with the top of the Navigation Bar via `navigationBarsPadding()`.
4. *Preservation of Specialized Viewports & Invariants*: Standalone map views without lower content (`WorkoutClusterHeatmapScreen`) retain full-screen map (`mapBox(Modifier.fillMaxSize())`); compact bottom sheets (`LiveSegmentSheet`, `showMap == false`) retain `wrapContentHeight()`; full interactivity of `GlobalTelemetryZoomToolbar` (+, -, 1.0x, pan toggle), 2-finger pinch zoom, coordinate scrubbing, and map marker synchronization are 100% strictly preserved; minimum map height protection (`SplitPaneMath.MIN_MAP_HEIGHT` = 120 dp) is strictly enforced.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Refines `REQ-UI-267` (*Routes & Segments: Position Elevation Profile Below Map with Interactive Zooming, Panning, and Resizable Split-Pane Viewport in MapDetailLayout*) and restores the intent of Clause 4 in `REQ-UI-223` (*Aftermath/Map: Interactive Draggable Splitter and Dynamic Viewport Resizing in MapDetailLayout*).
2. *Historical Origin & Commit Trace*: `ATT-1890` (Sprint `2026-40.7`, commit `4e6224aa`) introduced `REQ-UI-223` with Clause 4 stating: *"When `hasScrollableContent == false` (Routes & Segments), Map Box SHALL retain full `Modifier.weight(1f)` and lower section SHALL wrap its content (`wrapContentHeight()`), with zero divider displayed."* `ATT-2311` (Sprint `2026-40.15`, commit `f129a0de`) introduced `REQ-UI-267` to move elevation profile below the map in routes/segments by switching condition to `showMap && hasLowerSection`, unintentionally subjecting routes and segments to the 50/50 proportional split.
3. *Root Reason for Existing Formulation*: `ATT-2311` sought to position the elevation profile below the map rather than floating over it. Reusing the existing split layout block was straightforward, but enforced a 50% split budget designed for multi-chart workout aftermath rather than an intrinsic-height single profile.
4. *Preservation of Core Invariants*: `TrackOnMapScreen` retains full 2-pane interactive resizing with `SplitPaneDivider`; route/segment zoom toolbar and scrubbing remain 100% functional; minimum map height (120 dp) is strictly preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an athlete viewing a route in `RouteOnMapScreen` or a segment in `SegmentOnMapScreen`,
• *When* the screen renders,
• *Then* the upper map SHALL receive `Modifier.weight(1f)` and expand dynamically to fill all vertical space above the elevation profile, with zero empty whitespace between the elevation profile and the Navigation Bar.
• *Given* any elevation profile with range between 0 m and 1000+ m in `RouteOnMapScreen` or `SegmentOnMapScreen`,
• *When* rendered,
• *Then* the lower section SHALL size intrinsically to its content (`wrapContentHeight()`), with its bottom edge sitting flush against the top of the Navigation Bar (`navigationBarsPadding()`).
• *Given* an athlete viewing a completed workout with telemetry charts in `TrackOnMapScreen`,
• *When* inspecting the screen,
• *Then* the interactive `SplitPaneDivider` SHALL remain active with proportional resizing (`splitFraction`) and vertical scrolling across all charts.
• *Given* `GlobalTelemetryZoomToolbar` and `ElevationProfile` in `RouteOnMapScreen` or `SegmentOnMapScreen`,
• *When* zooming in/out, toggling pan mode, or scrubbing distance,
• *Then* the curve SHALL magnify smoothly, and scrubbing SHALL accurately position the map marker.

**Invariants**: Standalone full-screen maps, LiveSegment sheet compact height, TrackOnMapScreen split-pane, and 100% clean-room test pass rate MUST NOT be broken.

### REQ-UI-274: Route Climbs Visual Prominence Across Elevation Profile, Map Polyline, Route Cards & Dedicated Breakdown [Verified]
- **Tests**: `TST-UI-234`, `TST-UI-322`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `RoutesDatabaseManager.kt`, `MapModels.kt`, `RoutesRepository.kt`, `ElevationProfile.kt`, `RouteSummaryHeader.kt`, `RouteOnMapScreen.kt`, `strings.xml` (all 9 locales)
- **Rationale**: The system SHALL surface and accentuate recognized climbs on routes across data models, route summary cards, the elevation profile canvas, the route map, and dedicated route details breakdown (ATT-2388, ATT-3453):
1. *Data Model & Extension Enrichment*: `RouteWithPath` (`RoutesDatabaseManager.kt`) and `MapRoute` (`MapModels.kt`) SHALL include `val climbs: List<Climb> = emptyList()`. `RouteWithPath.toMapRoute(isActiveNavigation: Boolean = false)` SHALL propagate `climbs = this.climbs`.
2. *Repository Enrichment & Backward-Compatible Fallback Detection*: In `RoutesRepository`, routes loaded via `allRoutes` / `refreshRoutes()` or queried by ID SHALL be enriched with their associated `climbs: List<Climb>` loaded via `ClimbsDatabaseManager.getClimbsForRoute(routeId)`. If `ClimbsDatabaseManager.getClimbsForRoute(routeId)` returns an empty list and `route.path.size >= 2`, `RoutesRepository` SHALL perform on-the-fly detection via `ClimbDetector.detectClimbs(path, routeId = routeId)` to ensure pre-existing routes display climbs seamlessly.
3. *Route Summary Header Climb Prominence (`RouteSummaryHeader.kt`)*: `RouteSummaryHeader` SHALL accept `climbs: List<Climb> = emptyList()`. When `climbs.isNotEmpty()`, the header metrics row SHALL display a climb summary metric item indicating total climbs on the route.
4. *Elevation Profile Climb Ridge Highlighting, Category Badges & Dynamic Vertical Clearance (`ElevationProfile.kt`)*: `ElevationProfile` and `MapDetailLayout` SHALL accept `climbs: List<Climb> = emptyList()`. In distance domain (`!isTimeDomain`), for each climb in `climbs`: (a) *Ridge Accent Stroke*: The system SHALL render an accented stroke along the climb's ridge (width 3.5 dp) using the climb category's distinct color; (b) *Summit Category Badge & Vertical Top Clearance*: The system SHALL draw a category badge pill (e.g. "Cat 4", "Cat 3", "Cat 2", "Cat 1", "HC", "UC") directly above each climb peak on the elevation profile canvas. When climbs are present (`!isTimeDomain && climbs.isNotEmpty()`), the system SHALL dynamically allocate adequate vertical top clearance based on active font scale, badge height, pin stem length, and safety margin, ensuring the badge pill remains fully visible with at least 2 dp container ceiling clearance ($y_{\text{box}} \ge 2\text{dp}$) and horizontal safety margins. The Composable outer container height SHALL remain stable to prevent layout shifts or card jumping, with the elevation polyline scaling proportionally within the available canvas area. In constrained or fixed-height viewports, if available height is insufficient to grant full clearance, the system SHALL guarantee a minimum viable chart height ($H_{\text{chart, min}} = 32\text{dp}$) while enforcing top ceiling clamping to prevent container clipping.
5. *Map Polyline Markers & Interactive Climb Breakdown (`RouteOnMapScreen.kt`)*: When `route != null && route.climbs.isNotEmpty()`: (a) *Map Climb Start Markers*: The system SHALL render prominent climb start markers at `climb.startLatLng` on the route map; (b) *Climb Breakdown Section*: The system SHALL provide a dedicated climb section in route inspection displaying each climb's category chip, sequential index, start kilometer along the route, length, average grade, and vertical gain.
6. *9-Language Localization Parity*: All newly introduced user-facing string resources SHALL be translated and maintained with 100% parity across all 9 application locales (EN, DE, ES, FR, IT, JA, NL, PL, PT).
7. *Preservation of System Invariants*: Mathematical climb detection parameters in `ClimbDetector`, live Cockpit ClimbPro sheet (`LiveClimbSheet.kt`), zoom toolbar ergonomics, scrubbing synchronization, and 100% clean-room test suite pass rate MUST NOT be compromised.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Refines and extends `REQ-MAP-027` (*Persistent Climbs Database & Live ClimbPro Cockpit Sheet*) and `REQ-UI-267` / `REQ-UI-273` (*Routes & Segments Elevation Profile and Map Detail Layout*). Amended by ATT-3453 (Clause 4, Dynamic Top Clearance & Boundary Protection).
2. *Historical Origin & Commit Trace*: Sprint `2026-40.14` (`ATT-1281`, commit `d5c90d81`) introduced `REQ-MAP-027`, establishing `ClimbDetector`, `ClimbsDatabaseManager`, and `LiveClimbSheet.kt`. Sprint `2026-41.1` (`ATT-2388`, commit `7e8a912f`) added summit category badges. Sprint `2026-41.1` (`ATT-2386`, commit `b24a10d8`) introduced `topPadding = if (showZoomControls) 44.dp else 16.dp`. Sprint `2026-41.3` (`ATT-2510`, commit `e1a42f7c`) preserved summit badges while adding X-axis span bars. Sprint `2026-42.2` (`ATT-3453`) eliminates badge clipping in compact previews.
3. *Root Reason for Existing Formulation*: `topPadding = 16.dp` was calibrated in `REQ-UI-225` for compact list preview cards when no zoom toolbar was present. In `MapDetailLayout` (`showZoomControls = true`), 44 dp absorbed summit badges. In live tracking (`SensorGridScreen.kt`), `showZoomControls = false` left only 16 dp, cutting off summit badges peaking near the maximum profile altitude ($Y = 0$).
4. *Preservation of Core Invariants*: Slope gradient coloring (`REQ-UI-299`), X-axis span bars (`REQ-UI-299`, `REQ-UI-307`), exact layout contract strings in `ElevationProfileLayoutTest.kt`, pin stem line contracts in `ElevationProfileClimbSpanContractTest.kt`, container outer stability, and 100% clean-room test suite pass rate are strictly preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an athlete viewing a route with recognized climbs in `RouteOnMapScreen` or `SensorGridScreen`,
• *When* the screen renders in distance domain,
• *Then* the elevation profile SHALL display category badge pills above summits with at least 2 dp vertical clearance below the container top edge ($y_{\text{box}} \ge 2\text{dp}$), with zero horizontal or vertical clipping.
• *Given* an elevation profile with climbs rendered under accessibility font scaling (1.5x to 2.0x),
• *When* the profile renders,
• *Then* vertical top clearance SHALL dynamically expand to accommodate enlarged badge dimensions without container overflow.
• *Given* an elevation profile in compact mode (`showZoomControls == false`),
• *When* climbs are present,
• *Then* the outer container height SHALL remain stable, and the elevation curve SHALL scale proportionally without layout shifts or card jumping.
• *Given* a constrained container height where full badge clearance cannot be allocated,
• *When* rendered,
• *Then* the system SHALL reserve at least 32 dp of chart plotting height and clamp the badge ceiling to prevent container boundary overflow.
• *Given* all 9 supported application locales (EN, DE, ES, FR, IT, JA, NL, PL, PT),
• *When* rendering climb summary strings and breakdown items,
• *Then* all strings SHALL be localized with 100% translation parity.

**Invariants**: Climb detection mathematics (500m min distance, 3% min grade, 20m min gain), live tracking Cockpit sheet, elevation profile zoom math, layout contract tests, and 100% clean-room test pass rate MUST NOT be broken.

### REQ-UI-275: Unified WYSIWYG Tracking Tab Configuration with Spatial Overlays and Per-Tab Popup Toggles [Verified]
- **Tests**: `TST-UI-235`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `TrackingViewsDatabaseManager.java`, `TrackingViewsRepository.kt`, `TrackingViewModel.kt`, `SensorGridScreen.kt`, `TrackingTabConfigHeader.kt`, `strings.xml` (all 9 locales)
- **Rationale**: The system SHALL transform Configuration Mode (`ScreenMode.CONFIGURATION`) in `SensorGridScreen.kt` into a spatial WYSIWYG cockpit configuration interface and provide per-tab gating for Turn-by-Turn Navigation Prompts and Live Climbs (ATT-2360):
1. *Database Schema & Migration (`TrackingViewsDatabaseManager.java`)*: The system SHALL increment database version to `DB_VERSION = 12`. The `VIEWS_TABLE` schema SHALL include `SHOW_LIVE_CLIMBS` (`ShowLiveClimbs int DEFAULT 1`) and `SHOW_NAVIGATION_HINTS` (`ShowNavigationHints int DEFAULT 1`). In `onUpgrade` for `oldVersion < 12`, each column addition SHALL be wrapped in a defensive try-catch block and execute `UPDATE ViewsTable SET ShowLiveClimbs = 1 WHERE ShowLiveClimbs IS NULL` and `UPDATE ViewsTable SET ShowNavigationHints = 1 WHERE ShowNavigationHints IS NULL` to ensure zero feature regression for existing user tabs.
2. *Domain Model & Repository Parity (`TrackingViewsRepository.kt`)*: `TrackingViewInfo` SHALL declare `val showLiveClimbs: Boolean = true` and `val showNavigationHints: Boolean = true` with default parameters for backward compatibility. `TrackingViewsRepository` SHALL expose `updateShowLiveClimbs(tabViewId: Long, showLiveClimbs: Boolean)` and `updateShowNavigationHints(tabViewId: Long, showNavigationHints: Boolean)` triggering `configUpdateTrigger`.
3. *ViewModel State Propagation (`TrackingViewModel.kt`)*: `TrackingScreenState` SHALL include `val showLiveClimbs: Boolean = true` and `val showNavigationHints: Boolean = true`, populated from `viewInfo?.showLiveClimbs ?: true` and `viewInfo?.showNavigationHints ?: true`.
4. *Streamlined Tab Header (`TrackingTabConfigHeader.kt`)*: The system SHALL remove generic checkboxes (`Show Map`, `Show Elevation Profile`, `Show Live Segments`, `Show Lap Button`) from `TrackingTabConfigHeader`, preserving tab name editing, relative tab addition (`Add Before` / `Add After`), tab deletion, and Done button, maximizing vertical screen real estate for the cockpit editor.
5. *Spatial WYSIWYG Cockpit Editor (`SensorGridScreen.kt`)*: In `ScreenMode.CONFIGURATION`: (a) *Top HUD Section*: The system SHALL render an interactive toggle card representing the Turn-by-Turn Navigation Prompts banner (`showNavigationHints`); (b) *Center Section*: Below the interactive sensor grid, the system SHALL render interactive spatial toggle blocks for Embedded Map (`showMap`) and Elevation Profile (`showElevationProfile`); (c) *Bottom Dock*: The system SHALL render interactive dock cards for Strava Live Segments (`showLiveSegments`), Live Climbs (`showLiveClimbs`), and Lap Button (`showLapButton`). Tapping any toggle card SHALL immediately persist the new state and provide immediate visual state feedback (active highlighted container vs. muted outlined container with state badge).
6. *Runtime Cockpit Gating (`SensorGridScreen.kt`)*: In `ScreenMode.TRACKING`: (a) `TurnPromptBanner` SHALL render IF AND ONLY IF `state.showNavigationHints && tuningConfig.turnPromptsEnabled`; (b) `LiveClimbSheet` SHALL render IF AND ONLY IF `state.showLiveClimbs && tuningConfig.showLiveClimbs && activeLiveClimb != null`.
7. *100% 9-Language Localization Parity*: All newly introduced UI toggle labels and accessibility descriptions SHALL be localized across all 9 application locales (EN, DE, ES, FR, IT, JA, NL, PL, PT).

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Refines and extends `REQ-PRO-005` (*Database Schema Integrity*) and `REQ-UI-200` (*Sensor Grid Configuration & Drag-and-Drop / Pick & Place*).
2. *Historical Origin & Commit Trace*: Sprint 2026-40.4 (`ATT-1281` introduced Live Climbs); Sprint 2026-40.6 (`ATT-1450` introduced Turn-by-Turn Navigation); `TrackingTabConfigHeader.kt` previously managed tab booleans via top checkboxes.
3. *Root Reason for Existing Formulation*: Checkboxes were an MVP implementation in the tab header. Athletes could not disable navigation prompts or live climbs per tab, and the header crowded vertical space.
4. *Preservation of Core Invariants*: Full backward compatibility for existing user databases (defaulting newly added flags to true/1), sensor tile movement and editing, and 100% clean-room test suite pass rate are strictly preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an athlete in configuration mode (`ScreenMode.CONFIGURATION`) for a tracking tab,
• *When* the screen renders,
• *Then* the header SHALL NOT display checkboxes, and the cockpit editor SHALL display spatial toggle cards for Navigation Prompts at top, Map and Elevation Profile below the sensor grid, and Live Segments, Live Climbs, and Lap Button at bottom.
• *Given* an athlete tapping any spatial toggle card in configuration mode,
• *When* tapped,
• *Then* the corresponding flag SHALL immediately update in SQLite, and the toggle card SHALL visually reflect active vs. disabled state.
• *Given* an existing user database upgraded to `DB_VERSION = 12`,
• *When* upgraded,
• *Then* all existing tracking tabs SHALL default `ShowLiveClimbs` and `ShowNavigationHints` to 1 (true) with zero database corruption or feature loss.
• *Given* a tracking tab with `showNavigationHints == false` or `showLiveClimbs == false`,
• *When* active tracking is running,
• *Then* navigation prompts or live climbs SHALL NOT be displayed on that tab even if globally enabled in settings.

**Invariants**: Existing database integrity, sensor grid editing ergonomics, and 100% test pass rate MUST NOT be broken.

### REQ-UI-276: Cockpit Tile Distinct Presets, Granular Corner Radius, Border Thickness, and Theme-Aware Border Contrast [Verified]
- **Tests**: `TST-UI-236`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `SensorFieldStyle.kt`, `SensorFieldView.kt`, `SensorGridScreen.kt`, `CockpitTypographySection.kt`, `TuningPreferencesDataStore.kt`
- **Rationale**: The system SHALL provide distinctly differentiated cockpit tile styling presets and granular, athlete-adjustable sliders for corner radius, border thickness, and theme-aware border contrast in `CockpitTypographySection.kt` and `SensorGridScreen.kt` (ATT-2456):
1. *Distinct Baseline Presets (`SensorFieldVariant` & `SensorFieldStyle`)*: The 4 styling variants SHALL serve as baseline presets with distinctly noticeable visual signatures at glance distance:
• `CLASSIC_SEAMLESS`: `cornerRadius = 0.dp` (`RectangleShape`), `gridSpacing = 0.dp`, `defaultElevation = 0.dp`, `borderThickness = 1.0.dp`, `borderContrast = 0.0f`.
• `OUTLINED_TILES`: `cornerRadius = 8.dp` (`RoundedCornerShape(8.dp)`), `gridSpacing = 4.dp`, `defaultElevation = 0.dp`, `borderThickness = 2.0.dp`, `borderContrast = 0.5f`.
• `ELEVATED_CARDS`: `cornerRadius = 10.dp` (`RoundedCornerShape(10.dp)`), `gridSpacing = 6.dp`, `defaultElevation = 3.dp`, `borderThickness = 0.0.dp`, `borderContrast = 0.0f`.
• `SOFT_CAPSULES`: `cornerRadius = 16.dp` (`RoundedCornerShape(16.dp)`), `gridSpacing = 8.dp`, `defaultElevation = 1.dp`, `borderThickness = 1.5.dp`, `borderContrast = 0.7f`.
2. *Granular Preference Sliders in Cockpit Typography (`CockpitTypographySection.kt`)*: Below the variant dropdown, the section SHALL render 3 dedicated `TuningSliderItem` controls:
• Corner Radius: Range `0.0f .. 20.0f` with 19 steps (1 dp increments).
• Border Thickness: Range `0.0f .. 4.0f` with 7 steps (0.5 dp increments: 0.0, 0.5, 1.0, 1.5, 2.0, 2.5, 3.0, 3.5, 4.0 dp).
• Border Contrast / Luminance: Range `0.0f .. 1.0f` with 9 steps (10% increments: 0% .. 100%).
3. *Dynamic Preset Synchronization*: When the athlete selects a preset from the `SensorFieldVariant` dropdown, the 3 sliders SHALL automatically snap to that preset's baseline parameters. Adjusting any slider independently SHALL update the corresponding parameter without locking other sliders.
4. *Theme-Aware Border Color Resolution (`SensorFieldStyle.kt`)*: Border stroke SHALL be resolved theme-aware:
• When `borderThickness <= 0.0.dp`: `null` (borderless).
• When `borderThickness > 0.0.dp`: `BorderStroke(borderThickness, resolvedColor)`.
• In Dark / AMOLED Cockpit Mode: `resolvedColor = lerp(Color(0xFF383838), Color.White, borderContrast.coerceIn(0f, 1f))`.
• In Light Mode: `resolvedColor = lerp(Color(0xFFD6D6D6), Color.Black, borderContrast.coerceIn(0f, 1f))`.
• When in configuration mode and `isSelectedForMove` is true, the primary accent border (`BorderStroke(2.dp, MaterialTheme.colorScheme.primary)`) SHALL take precedence over the custom border.
5. *DataStore Preference Persistence & Atomic Factory Reset (`TuningPreferencesDataStore.kt`)*: `KEY_SENSOR_FIELD_CORNER_RADIUS`: Float (default `0.0f`), `KEY_SENSOR_FIELD_BORDER_THICKNESS`: Float (default `1.0f`), `KEY_SENSOR_FIELD_BORDER_CONTRAST`: Float (default `0.0f`). `resetToDefaults()` SHALL atomically clear all 3 keys along with `KEY_SENSOR_FIELD_VARIANT`.
6. *Live Cockpit Ingestion (`SensorGridScreen.kt`)*: `SensorGridScreen` SHALL reactively observe `cornerRadius`, `borderThickness`, and `borderContrast` from `tuningConfigFlow`. The resolved shape and border SHALL be supplied to each `SensorFieldView`, updating the live tracking cockpit across all tabs instantly upon saving or adjusting preferences.
7. *100% 9-Language Localization Parity*: String resources for titles, helper texts, and default descriptions SHALL be defined with identical keys across all 9 application locales.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Target & Historical Trace*: Refines and extends `REQ-UI-171` (*High-Contrast Cockpit Typography*), `REQ-UI-200` (*Cockpit Sensor Grid Drag & Drop*), and `REQ-UI-258` (*Cockpit Sensor Field Visual Styling Architecture*).
2. *Historical Origin & Commit Trace*: Sprint `2026-41.1`, Ticket `ATT-2456`, target release `V4.9.39`, Epic `ATT-355` (*Good and consistent UI*).
3. *Root Reason for Existing Formulation*: ATT-2058 presets were too subtle to distinguish at arm's length outdoors, and borders could not be adjusted or contrasted.
4. *Preservation of Core Invariants*: Production defaults (`CLASSIC_SEAMLESS`, 0 dp radius, 1.0 dp border, 0% contrast), 2 dp primary accent selection border in configuration mode, and < 400 lines file size bounds remain strictly preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an athlete selecting a variant preset in `AdvancedTuningDialog`,
• *When* selected,
• *Then* the corner radius, border thickness, and border contrast sliders snap to that preset's baseline parameters.
• *Given* an athlete adjusting the corner radius, border thickness, or border contrast sliders,
• *When* adjusted and saved,
• *Then* the live cockpit in `SensorGridScreen` immediately updates its tile geometry and border stroke.
• *Given* dark vs. light theme,
• *When* border contrast is set to 100%,
• *Then* the border renders pure white in dark mode and pure black in light mode.
• *Given* factory reset,
• *When* triggered,
• *Then* all tile styling parameters revert atomically to `CLASSIC_SEAMLESS` defaults.

### REQ-UI-277: Settings Live Preview Cockpit Tile Styling & Comprehensive Section Naming [Verified]
- **Tests**: `TST-UI-237`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `CockpitTypographySection.kt`, `strings.xml` (all 9 locales)
- **Rationale**: The system SHALL render individual cockpit tile borders, corner radius, elevation, and spacing inside the settings live preview card in `CockpitTypographySection.kt`, and SHALL rename the settings category header to reflect both typography and tile styling across all 9 supported locales (ATT-2457):
1. *WYSIWYG Live Preview Tile Rendering (`CockpitTypographySection.kt`)*:
• The live preview container SHALL render sample metrics ("148 bpm", "28.5 km/h", "1:24:35") as discrete mini cockpit tiles rather than unstructured text on a flat background card.
• Each mini preview tile SHALL faithfully apply: corner radius and shape resolved via `SensorFieldStyle.resolveShape(sensorFieldCornerRadius.dp)`; elevation and shadow resolved via `style.elevation`; border stroke resolved via `SensorFieldStyle.resolveBorder(sensorFieldBorderThickness.dp, sensorFieldBorderContrast, isDarkTheme)`; container background `MaterialTheme.colorScheme.surface`; inter-tile grid spacing reflecting `style.gridSpacing` (0 dp for `CLASSIC_SEAMLESS`, 4 dp for `OUTLINED_TILES`, 6 dp for `ELEVATED_CARDS`, 8 dp for `SOFT_CAPSULES`).
• Sample metrics SHALL continue to reflect `cockpitFontFamily` and `cockpitFontWeight` accurately.
2. *Comprehensive Category Header Renaming*:
• String resource `tuning_cat_cockpit_typography` SHALL be updated across all 9 application locales to encompass both typography and tile styling: EN: `Cockpit, Typography & Tiles`, DE: `Cockpit, Typografie & Kacheln`, ES: `Cockpit, tipografía y cuadrícula`, FR: `Cockpit, typographie et tuiles`, IT: `Cockpit, tipografia e riquadri`, JA: `コックピット、フォント＆タイル`, NL: `Cockpit, typografie & tegels`, PL: `Kokpit, typografia i kafelki`, PT: `Cockpit, tipografia e blocos`.
• The resource ID `tuning_cat_cockpit_typography` SHALL be preserved to guarantee zero breaking changes to existing tests and XML references.
3. *Architectural Modularity Invariant*:
• `CockpitTypographySection.kt` and `AdvancedTuningDialog.kt` SHALL remain strictly under 400 lines of code.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Target & Historical Trace*: Refines `REQ-UI-212` (*Cockpit Typography*), `REQ-UI-222` (*Accordion Architecture for Advanced Settings*), and `REQ-UI-276` (*Cockpit Tile Presets & Sliders*).
2. *Historical Origin & Commit Trace*: Sprint `2026-41.1`, Ticket `ATT-2457`, target release `V4.9.39`, Epic `ATT-355` (*Good and consistent UI*).
3. *Root Reason for Existing Formulation*: In ATT-2058 and ATT-2456, the live preview card in settings displayed text on a bare card without borders, elevation, or inter-tile spacing, preventing athletes from previewing tile styling choices. Furthermore, the section title only referenced typography, omitting tile styling.
4. *Preservation of Core Invariants*: String key `tuning_cat_cockpit_typography` preserved for contract safety; font family and weight preview fidelity strictly maintained; file size boundaries (< 400 lines) enforced.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an athlete in `AdvancedTuningDialog` viewing the *Cockpit, Typografie & Kacheln* section,
• *When* selecting `OUTLINED_TILES` preset,
• *Then* the live preview tiles SHALL render with 8 dp rounded corners, 4 dp spacing, and a 2 dp outline stroke.
• *Given* an athlete adjusting the Border Thickness or Contrast sliders,
• *When* moving the slider,
• *Then* the live preview tile border stroke and contrast SHALL visibly adjust in real-time.
• *Given* an athlete selecting `CLASSIC_SEAMLESS`,
• *When* observing preview tiles,
• *Then* tiles SHALL render with 0 dp corner radius and 0 dp grid spacing.
• *Given* an athlete viewing the dialog in any of the 9 locales,
• *When* viewing the section header,
• *Then* the header SHALL display the comprehensive title reflecting both typography and tiles.

**Invariants**: Key `tuning_cat_cockpit_typography` preserved; typography preview preserved; file modularity (< 400 lines) maintained.

### REQ-UI-278: Pairing Triggers Relocation to Sensor Management & Control Tracking Layout Optimization [Verified]
- **Tests**: `TST-UI-238`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `ControlTrackingScreen.kt`, `DevicesTabbedScreen.kt`, `PairingButtons.kt`, `strings.xml` (all 9 locales)
- **Rationale**: The system SHALL relocate sensor pairing triggers from the tracking control screen to the sensor management screen ("Meine Sensoren") and optimize the layout geometry of `ControlTrackingScreen.kt` (ATT-2189):
1. *Pairing Triggers Removal from Control Tracking Screen (`ControlTrackingScreen.kt`)*:
• `ControlTrackingScreen.kt` SHALL remove `PairingButtons` and `DeviceTypeSelectionDialog` from its composition.
• The primary action button `ControlTrackingButton` (Start / Pause / Stop) and `SportTypeSelector` SHALL remain vertically centered between the upper Information Area (`SearchingArea`, `RemoteDevices`, `LocationCalibrationStatus`) and the bottom boundary via equal-weight spacers (`Modifier.weight(1f)`).
• `ControlTrackingScreen.kt` SHALL expose a bottom container slot `bottomContent: @Composable () -> Unit = {}` positioned at the bottom of the screen column, reserving the vacated vertical space (~72 dp) to host the Route Selection button/card (`ATT-2458`).
2. *Dedicated Pairing Entry Point in Sensor Management ("Meine Sensoren", `DevicesTabbedScreen.kt`)*:
• `DevicesTabbedScreen.kt` SHALL provide dedicated user-facing entry points to initiate pairing for both ANT+ and Bluetooth LE sensors across all tabs.
• The screen SHALL render a Material 3 `FloatingActionButton` anchored to `Alignment.BottomEnd` with `navigationBarsPadding()` and `padding(16.dp)`, styled with `Icons.Default.Add` and content description `@string/devices_pair_sensor`.
• In addition, an action trigger for pairing SHALL be accessible from the top app bar header.
3. *Streamlined Protocol & Device Type Selection*:
• Tapping the pairing action SHALL present a Material 3 bottom sheet (`PairingProtocolBottomSheet`) allowing selection between `Protocol.ANT_PLUS` and `Protocol.BLUETOOTH_LE`.
• Protocol options SHALL display protocol icons, localized names, and reflect system hardware availability (`isAntSupported` via `BANALService.areAllANTServicesInstalled`, `isBluetoothSupported` via `BANALService.isProtocolSupported`).
• Upon selecting a protocol, the system SHALL present `DeviceTypeSelectionDialog` for that protocol.
• Upon choosing a device type, the system SHALL execute `tabViewModel.updateFilters(protocol, deviceType)`, animate the pager to Tab 0 (`DeviceFilterType.CONNECTED` / Available), and trigger active scanning via `tabViewModel.startSearching()`.
4. *9-Language Localization Parity*:
• All newly introduced string keys (`devices_pair_sensor`, `devices_pair_protocol_title`) SHALL be localized across all 9 supported application locales (EN, DE, ES, FR, IT, JA, NL, PL, PT) with zero AAPT2 formatting flaws.
5. *Preserved Invariants*:
• `ControlTrackingButton` strict precise location gating (`REQ-PRI-004`), permission warning badges, and Material 3 rationale sheet (`REQ-PRI-003`) MUST remain strictly intact.
• `MainActivityWithNavigation.startPairing` and `DevicesTabbedViewModel.updateFilters` contracts (`REQ-STB-011`) MUST be preserved.
• Remote device click opening `EditDeviceDialog` (`ControlNavigation.ToEditDevice`) MUST remain intact.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Target & Historical Trace*: Refines `REQ-UI-057` (*Global Icon Rounding*), `REQ-UI-149` (*Modernized Modal Bottom Sheet Dialogs & Selection Flows*), and `REQ-STB-011` (*Dynamic Pairing Filter Configuration*).
2. *Historical Origin & Commit Trace*: Sprint `2026-41.1`, Ticket `ATT-2189`, target release `V4.9.39`, Epic `ATT-355` (*Good and consistent UI*).
3. *Root Reason for Existing Formulation*: Pairing buttons previously occupied the bottom of the active control HUD permanently despite pairing being a one-time configuration task. Relocating pairing to "Meine Sensoren" frees bottom space for Route Selection (ATT-2458) while making sensor management self-contained.
4. *Preservation of Core Invariants*: Center Start button layout geometry, precise location gating (`REQ-PRI-004`), and `updateFilters` ViewModel contracts remain strictly preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an athlete on the Control Tracking screen,
• *When* the screen renders,
• *Then* pairing buttons SHALL NOT be visible at the bottom of the screen, and the Start Tracking button and Sport picker SHALL remain centered between the Information Area and the bottom slot.
• *Given* an athlete in "Meine Sensoren" (`DevicesTabbedScreen`),
• *When* viewing any tab,
• *Then* a prominent Floating Action Button with `Icons.Default.Add` and content description "Sensor koppeln" / "Pair Sensor" SHALL be rendered at the bottom-right of the screen.
• *Given* an athlete tapping the pairing FAB in `DevicesTabbedScreen`,
• *When* tapped,
• *Then* a bottom sheet SHALL display ANT+ and Bluetooth LE protocol options with their availability state.
• *When* the athlete chooses a protocol and device type,
• *Then* the screen SHALL update filters via `tabViewModel.updateFilters`, animate to the Available tab, and start scanning.
• *Given* any of the 9 supported locales,
• *Then* all new pairing strings SHALL be localized with 100% parity.

**Invariants**: Location gating (`REQ-PRI-004`), M3 bottom sheets (`REQ-UI-149`), and 100% test pass rate MUST NOT be broken.

### REQ-UI-279: Branded Route Selection Entry Point on Control Tracking Screen & Cockpit Sensor Grid Decoupling [Verified]
- **Tests**: `TST-UI-239`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `ControlTrackingScreen.kt`, `RouteSelectionButton.kt`, `SensorGridScreen.kt`, `TrackingTabsScreen.kt`
- **Rationale**: The system SHALL relocate the Quick Route Selector entry point from the Cockpit Sensor Grid tabs to exclusively the Control Tracking screen and provide an app-consistent branded presentation with route-semantic green domain accents (ATT-2458):
1. *Removal from Cockpit Sensor Grid Tabs (`SensorGridScreen.kt`)*:
• The system SHALL remove `RouteActionChipRow` from `SensorGridScreen.kt`, ensuring that no sensor grid tab renders the floating route action chip during live tracking.
• Automated Route Detection (`RouteAutoDetector`, `AutoDetectedRouteBanner`) SHALL remain fully active and undisturbed within `SensorGridScreen.kt` to present in-ride route join prompts over live tracking.
2. *Exclusive Entry Point on Control Tracking Screen (`ControlTrackingScreen.kt`)*:
• The route selection entry point SHALL be rendered exclusively at the bottom of `ControlTrackingScreen.kt`, occupying the dedicated container slot (`bottomContent`) established in `REQ-UI-278`.
• The entry point SHALL NOT be visible or duplicated on any other cockpit tracking tab.
3. *App-Consistent Branding & Green Domain Semantic Accents (`RouteSelectionButton.kt`)*:
• The entry point SHALL be styled as an integrated app element (`RouteSelectionButton`) adhering to Rule 23 design tokens (`docs/design_guidelines.md` Section 5):
- Shape: `RoundedCornerShape(12.dp)`.
- Border Accent: Subtle green border stroke `BorderStroke(1.dp, TTColor.RouteSelected.copy(alpha = 0.35f))` semantically connecting to map route geometry.
- Branded Icon Container: 36.dp box with 8.dp rounded corners, `TTColor.RouteSelected.copy(alpha = 0.12f)` background, housing `R.drawable.ic_route` tinted with `TTColor.RouteSelected`.
- Inactive State: Displays "Select Route" (`@string/route_action_select`), descriptive subtitle, and trailing forward chevron (`Icons.AutoMirrored.Default.ArrowForwardIos`).
- Active State: Displays "✓ <Route Name>" with distance + elevation metrics (or dynamic remaining navigation metrics when navigating), and provides a dedicated stop/clear action button (`Icons.Default.Close`) invoking route cancellation.
- Color Scheme: Seamlessly renders across Light, Dark, and AMOLED themes utilizing `MaterialTheme.colorScheme`.
4. *Full Functional Parity (`TrackingTabsScreen.kt`)*:
• Tapping the button SHALL present `RouteSelectorModalBottomSheet` for selecting, changing, or searching routes.
• Route selection and cancellation (`setActiveNavigatedRoute`, `stopRoute`) SHALL immediately reflect on the button.
• Continuous location forwarding to `RouteSelectorViewModel` SHALL be maintained.
5. *100% 9-Language Localization Parity*:
• All user-facing strings SHALL maintain 100% translation parity across all 9 supported application locales (EN, DE, ES, FR, IT, JA, NL, PL, PT).

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Target & Historical Trace*: Refines and amends `REQ-MAP-024` Clause 1 (*Cockpit 1-Tap Entry Point*) and leverages `REQ-UI-278` (*Control Tracking Layout Optimization & Bottom Slot*).
2. *Historical Origin & Commit Trace*: Sprint `2026-41.1`, Ticket `ATT-2458`, target release `V4.9.39`, Epic `ATT-66` (*Improve Routes*).
3. *Root Reason for Existing Formulation*: ATT-1835 placed `RouteActionChipRow` inside `SensorGridScreen` as a quick expedient because `ControlTrackingScreen` was overloaded with pairing buttons. Following ATT-2189's relocation of pairing buttons, vertical space became available at the bottom of `ControlTrackingScreen`. Athletes requested removing the repetitive chip from every sensor grid tab and providing a beautifully branded, green-accented entry point on the setup screen.
4. *Preservation of Core Invariants*: In-ride auto-detection banner (`AutoDetectedRouteBanner`), `RouteSelectorModalBottomSheet` functionality, `RoutesRepository` contracts, and 100% full-suite test pass rate remain strictly preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an athlete swiping through Cockpit Sensor Grid tabs (page 1..N),
• *When* viewing the sensor fields,
• *Then* `RouteActionChipRow` SHALL NOT be rendered on any sensor tab.
• *Given* an athlete on the Control Tracking screen (page 0),
• *When* the screen renders,
• *Then* `RouteSelectionButton` SHALL be rendered in the bottom slot with a 12.dp rounded shape, subtle green border accent, and branded route icon container.
• *Given* an active route being followed,
• *When* observing `RouteSelectionButton`,
• *Then* it SHALL display "✓ <Route Name>" and a close/stop button allowing 1-tap route cancellation.
• *Given* an athlete riding along an unselected saved route,
• *When* auto-detection triggers,
• *Then* `AutoDetectedRouteBanner` SHALL display normally in `SensorGridScreen.kt`.

**Invariants**: In-ride auto-detection banner in `SensorGridScreen`, route repository state, and 100% test pass rate MUST NOT be broken.

### REQ-UI-280: Streamlined Route Selector: Removal of Sort Tabs and Pinned Heimweg Card in Favor of Proximity-Recency Ranking [Verified]
- **Tests**: `TST-UI-240`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `RouteSelectorSheet.kt`, `RouteSelectorViewModel.kt`, `TrackingTabsScreen.kt`
- **Rationale**: The system SHALL streamline `RouteSelectorSheet.kt` and `RouteSelectorViewModel.kt` into a clean, focused, proximity-recency route list without filter tabs and without the pinned Heimweg card (ATT-2459):
1. *Removal of Heimweg Card (`RouteSelectorSheet.kt`)*:
• The system SHALL remove the pinned "Heimweg" / "Take Me Home" card (`Card(onClick = onTakeMeHome, ...)`) from `RouteSelectorSheet.kt`.
• The `onTakeMeHome` parameter SHALL be removed from `RouteSelectorModalBottomSheet` and `RouteSelectorContent`.
• In `TrackingTabsScreen.kt`, the `RouteSelectorModalBottomSheet` call site SHALL no longer supply `onTakeMeHome`.
2. *Removal of Sort/Filter Tab Bar (`RouteSelectorSheet.kt` & `RouteSelectorViewModel.kt`)*:
• The system SHALL remove the `TabRow` filter tabs (*In der Nähe / Zuletzt gefahren / Länge*) and the conditional `uiState.showFilterTabs` guard from `RouteSelectorSheet.kt`.
• In `RouteSelectorViewModel.kt`, route ranking SHALL unconditionally default to `RouteProximityRanker.rankRoutes(...)` using the athlete's current location, bearing, and recency tie-breaking.
• The `RouteFilterTab` enum, `selectedTab`, and `showFilterTabs` properties SHALL be eliminated from `RouteSelectorUiState` and `RouteSelectorViewModel`.
3. *Preservation of In-Ride Detection & Active Route Controls*:
• `ActiveRouteBanner` (allowing 1-tap route cancellation via `onStopRoute`) SHALL remain prominently rendered when a route is currently active.
• `AutoDetectedRouteBanner` (allowing activation or dismissal of dynamically detected route candidates) SHALL remain fully functional within `RouteSelectorSheet.kt`.
4. *Clean Empty State*:
• When no saved routes are available or no routes exist, the sheet SHALL render a centered empty state placeholder informing the athlete (`@string/route_empty_title` and `@string/route_empty_desc`).
5. *100% 9-Language Localization Parity*:
• All remaining user-facing strings in the sheet SHALL maintain 100% parity across all 9 supported application locales (EN, DE, ES, FR, IT, JA, NL, PL, PT).

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Amends `REQ-MAP-024` Clause 3 (*Adaptive Route Selector Sheet UI*) and `REQ-MAP-029` Clause 5 (*"Take Me Home" Entry Points*).
2. *Historical Origin & Commit Trace*: Tickets `ATT-1835` (Sprint `2026-40.14`) and `ATT-1953` (Sprint `2026-40.15`).
3. *Root Reason for Existing Formulation*: ATT-1835 introduced filter tabs because initial testing featured flat route lists without proximity filtering; ATT-1953 placed the Heimweg card at the top of the route selector as an easily accessible launch pad before dedicated return navigation triggers existed.
4. *Preservation of Core Invariants*: In-ride automated route detection (`RouteAutoDetector`, `AutoDetectedRouteBanner`) remains fully active; active route cancellation (`ActiveRouteBanner`, `onStopRoute`) remains fully functional; `RouteProximityRanker` continues to evaluate proximity, heading, sport matching, and recency; 100% full-suite test pass rate is strictly preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an athlete opening the Route Selector bottom sheet from `ControlTrackingScreen`,
• *When* the sheet appears,
• *Then* the "Heimweg" / "Take Me Home" card SHALL NOT be present anywhere in the sheet.
• *Given* an athlete with 10 saved routes opening the Route Selector,
• *When* viewing the route list,
• *Then* no `TabRow` or sort tabs (*In der Nähe / Zuletzt gefahren / Länge*) SHALL be displayed, and routes SHALL be ranked directly by the 5-tier proximity-recency engine (`RouteProximityRanker`).
• *Given* an active navigated route,
• *When* opening `RouteSelectorSheet`,
• *Then* `ActiveRouteBanner` SHALL be displayed at the top with route name, metrics, and a "Clear / Abwählen" action that resets active navigation upon tap.
• *Given* an auto-detected candidate route evaluated by `RouteAutoDetector`,
• *When* opening `RouteSelectorSheet`,
• *Then* `AutoDetectedRouteBanner` SHALL display the candidate prompt with activate and dismiss actions.
• *Given* a user with zero saved routes,
• *When* opening `RouteSelectorSheet`,
• *Then* the empty state title and description SHALL render centered in the sheet without layout overflows.

**Invariants**: In-ride auto-detection banner in `SensorGridScreen`, route repository state, and 100% test pass rate MUST NOT be broken.

### REQ-UI-281: Configurable Route Selector Proximity Radius and Recency Sorting [Verified]
- **Tests**: `TST-UI-241`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `AdvancedTuningAccordion.kt`, `AdvancedTuningDialog.kt`, `NavigationSection.kt`, `TuningPreferencesDataStore.kt`, `RouteProximityRanker.kt`, `RouteSelectorViewModel.kt`, `RouteSelectionButton.kt`, `TrackingTabsScreen.kt`
- **Rationale**: The system SHALL limit the pre-ride route selector strictly to candidate routes within a configurable radius from the athlete's current location, sort qualifying routes by recency ("Zuletzt gefahren"), introduce a dedicated "Navigation" category in Advanced Settings, and dim the route selection button when no routes qualify (ATT-2460):
1. *Dedicated "Navigation" Section in Advanced Settings (`AdvancedTuningAccordion.kt` & `AdvancedTuningDialog.kt`)*:
• The system SHALL introduce `TuningSection.NAVIGATION` as a dedicated collapsible category.
• The section SHALL display icon `Icons.Default.Navigation`, category title `@string/tuning_cat_navigation`, and subtitle summary formatted via `TuningSubtitleFormatter.formatNavigationSubtitle(routeSelectionRadiusKm)`.
2. *Configurable "Routenauswahl-Radius" Setting (`NavigationSection.kt` & `TuningPreferencesDataStore.kt`)*:
• The section SHALL provide a `TuningSliderItem` for "Routenauswahl-Radius" (`routeSelectionRadiusKm`).
• Default value SHALL be `1.0 km` (`TuningPreferencesDefaults.DEFAULT_ROUTE_SELECTION_RADIUS_KM = 1.0f`), range `0.5 km .. 10.0 km` (0.5 km steps).
• The setting SHALL be persisted in `TuningPreferencesDataStore` under `KEY_ROUTE_SELECTION_RADIUS_KM` and restored to 1.0 km on factory reset (`resetToDefaults()`).
3. *Strict Proximity Filtering Criteria (`RouteProximityRanker.kt`)*:
• The system SHALL filter candidate routes strictly by straight-line distance from the athlete's current GPS position to the route start point (`distance <= radiusMeters`, where `radiusMeters = routeSelectionRadiusKm * 1000f`).
• Routes with distance > radiusMeters, missing paths, or when GPS location is null SHALL be excluded.
4. *Recency Sorting ("Zuletzt gefahren")*:
• Qualifying routes within the radius SHALL be sorted strictly by `syncedAt` descending (most recently ridden first), with deterministic secondary tie-breaking by distance to start point ascending, then name.
5. *Dimmed Button Visual Feedback (`RouteSelectionButton.kt` & `TrackingTabsScreen.kt`)*:
• When no routes qualify within the radius (or no routes exist) and no route is active, `RouteSelectionButton` SHALL render with reduced alpha (`alpha = 0.45f`). When an active route is selected or routes enter the radius, the button SHALL render at full opacity (`alpha = 1.0f`). The dimmed button SHALL remain clickable.
6. *100% 9-Language Localization Parity*:
• All new strings (`tuning_cat_navigation`, `tuning_route_selection_radius_title`, `tuning_route_selection_radius_desc`) SHALL maintain 100% parity across all 9 application locales (EN, DE, ES, FR, IT, JA, NL, PL, PT).

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Target & Historical Trace*: Amends `REQ-MAP-024` Clause 2 (*Multi-Stage Route Ranking & Tie-Breaking Engine*), expands `REQ-SET-073` (*Advanced Settings & Tuning System*), and refines `REQ-UI-279` (*Cockpit Route Action Button Visual States*).
2. *Historical Origin & Commit Trace*: Sprint `2026-40.14` (`ATT-1835`), Sprint `2026-40.15` (`ATT-1957`), and Sprint `2026-41.1` (`ATT-2458`, `ATT-2459`).
3. *Root Reason for Existing Formulation*: In ATT-1835, all routes were retained because the selector acted as a global browser; out-of-radius routes were appended at the end. In ATT-1957, only 5 tuning sections were created prior to navigation tuning. In ATT-2458, RouteSelectionButton was created with static opacity.
4. *Preservation of Core Invariants*: In-ride automated route detection (`RouteAutoDetector`, `AutoDetectedRouteBanner`), active route cancellation, all 5 existing tuning categories, and 100% full-suite test pass rate remain strictly preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an athlete opening Advanced Settings,
• *When* observing categories,
• *Then* the "Navigation" section SHALL be present with "Routenauswahl-Radius" defaulting to "1.0 km".
• *Given* an adjusted radius of 2.5 km,
• *When* saved and restored,
• *Then* 2.5 km SHALL persist across restarts, and revert to 1.0 km upon factory reset.
• *Given* an athlete at location `(48.137, 11.576)` with route A at 500m and route B at 1500m and radius 1.0 km,
• *When* opening the route selector,
• *Then* route A SHALL appear and route B SHALL be strictly excluded.
• *Given* multiple routes within radius,
• *When* viewing the list,
• *Then* routes SHALL be sorted by `syncedAt` descending (most recently ridden first).
• *Given* no routes within radius and no active route,
• *When* viewing `ControlTrackingScreen`,
• *Then* `RouteSelectionButton` SHALL be dimmed (`alpha = 0.45f`).

**Invariants**: In-ride route detection, active route cancellation, and 100% test pass rate MUST NOT be broken.

### REQ-UI-282: Mid-Ride Route Selector Return Navigation Entry Point & App-Consistent Take Me Home UI Styling [Verified]
- **Tests**: `TST-UI-242`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
• `TrackingTabsScreen.kt` SHALL supply `isMidRide = isMidRide` and `onTakeMeHome = { returnNavRepo.startTakeMeHome(); showRouteSelectorSheet = false }` to `RouteSelectorModalBottomSheet`.
4. *100% 9-Language Localization Parity*:
• All strings (`nav_take_me_home`, `nav_home_destination_desc`, `nav_stop_return`) SHALL be defined across all 9 supported application locales (EN, DE, ES, FR, IT, JA, NL, PL, PT).

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Target & Historical Trace*: Amends `REQ-MAP-029` Clause 5 (*"Take Me Home" Entry Points & Visual HUD Presentation*) and interfaces with `REQ-UI-280` (*Streamlined Route Selector*).
2. *Historical Origin & Commit Trace*: Ticket `ATT-1953`, Sprint `2026-40.16` (commit `0994f794`), and Ticket `ATT-2459`, Sprint `2026-41.1` (commit `cf4aafd8`).
3. *Root Reason for Existing Formulation*: ATT-1953 originally placed the Heimweg card unconditionally in the route selector. ATT-2459 removed it because it was confusing at workout start. The user decision in ATT-2462 refines this: Heimweg belongs in the Route Selector only mid-ride, where athletes naturally look for a way back.
4. *Preservation of Core Invariants*: Unconfigured workout start retains the clean proximity list without Heimweg card; active route banners, candidate auto-detection, and cancellation remain completely untouched; 100% full-suite test pass rate is strictly maintained.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an athlete on `ControlTrackingScreen` before starting a workout (`TrackingMode.IDLE` or `READY`),
• *When* opening the Route Selector sheet,
• *Then* `MidRideHeimwegCard` SHALL NOT be visible.
• *Given* an athlete during an active or paused tracking session (`TrackingMode.TRACKING` or `PAUSED`),
• *When* opening the Route Selector sheet,
• *Then* `MidRideHeimwegCard` SHALL be displayed above the route list with a 12.dp shape, green border accent, and Home icon.
• *When* the athlete taps `MidRideHeimwegCard`,
• *Then* `onTakeMeHome` SHALL be invoked, starting return navigation, and the sheet SHALL dismiss.
• *Given* `ReturnNavigationHud`,
• *When* rendering return navigation metrics,
• *Then* no emoji `🏠` SHALL appear in the title, and the card SHALL render with a 12.dp rounded corner shape and subtle green border stroke.
• *Given* all 9 supported locales,
• *When* translation parity test runs,
• *Then* zero missing entries and zero format specifier mismatches SHALL occur.

**Invariants**: Clean pre-ride list contract of ATT-2459, active route banners, auto-detection, and 100% test pass rate MUST NOT be broken.
- **Rationale**: The system SHALL conditionally present a dedicated "Take Me Home" (Heimweg) entry card in the Route Selector bottom sheet when opened during an active tracking session (mid-ride), harmonize all return navigation UI components with Rule 23 design tokens, eliminate raw emojis, and enforce 100% 9-language localization parity (ATT-2462):
1. *Mid-Ride Heimweg Entry Point in Route Selector (`RouteSelectorSheet.kt`)*:
• `RouteSelectorModalBottomSheet` and `RouteSelectorContent` SHALL support conditional rendering of a dedicated "Take Me Home" entry card when opened mid-ride via parameter `isMidRide: Boolean = false` and callback `onTakeMeHome: (() -> Unit)? = null`.
• When `isMidRide == true` and `onTakeMeHome != null`, `RouteSelectorContent` SHALL render `MidRideHeimwegCard` above the candidate route list.
• `MidRideHeimwegCard` SHALL follow Rule 23 design tokens: `RoundedCornerShape(12.dp)`, subtle green border accent `BorderStroke(1.dp, TTColor.RouteSelected.copy(alpha = 0.35f))`, container background `MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)`, branded 36.dp icon box with 8.dp radius housing `Icons.Default.Home` tinted `TTColor.RouteSelected`, title `@string/nav_take_me_home`, and subtitle `@string/nav_home_destination_desc`.
• Tapping the card SHALL invoke `onTakeMeHome()` and dismiss the sheet.
• When `isMidRide == false`, `MidRideHeimwegCard` SHALL NOT be rendered, preserving the clean pre-ride route list contract (`REQ-UI-280`).
2. *Harmonized ReturnNavigationHud (`ReturnNavigationHud.kt`)*:
• Raw emoji `🏠` SHALL be completely excised from the title text and anywhere in `ReturnNavigationHud.kt`.
• Container shape SHALL be `RoundedCornerShape(12.dp)` with `BorderStroke(1.dp, TTColor.RouteSelected.copy(alpha = 0.35f))`.
• Background SHALL use `MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)` with full dynamic Light, Dark, and AMOLED theme compliance.
• Destination icon SHALL use `Icons.Default.Home` (tinted `TTColor.RouteSelected` or `MaterialTheme.colorScheme.primary`) when `isHomeDestination == true`.
• Typography SHALL use standard `MaterialTheme.typography.titleSmall` and `bodyMedium` with `FontWeight.SemiBold`.
• Dismiss button SHALL use `Icons.Default.Close` with localized description `@string/nav_stop_return`.
3. *Cockpit Integration Site (`TrackingTabsScreen.kt`)*:
• `TrackingTabsScreen.kt` SHALL evaluate mid-ride tracking state: `isMidRide = trackingMode == TrackingMode.TRACKING

### REQ-UI-283: Equipment Sensor Matrix and Configuration Dialog: Sport-Specific Sensor Precedence Ordering [Verified]
- **Tests**: `TST-UI-243`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `EquipmentSensorOrdering.kt`, `EquipmentSensorMatrixScreen.kt`, `EditEquipmentDialog.kt`, `EquipmentViewModel.kt`, `DevicesDatabaseManager.java`
- **Rationale**: The system SHALL order sensor columns in the Equipment Sensor Matrix (`EquipmentSensorMatrixScreen.kt`) and sensor selection items in the Equipment Configuration Dialog (`EditEquipmentDialog.kt`) by sport-specific telemetry domain priority first, followed by shared/generic sensors, with standardized case-insensitive alphabetical tie-breaking (ATT-2464):
1. *Cycling Sensor Ordering (`BSportType.BIKE`)*:
• Primary tier (Cycling-specific sensors): `BIKE_POWER` (Priority 10), `BIKE_SPEED_AND_CADENCE` (Priority 20), `BIKE_SPEED` (Priority 30), `BIKE_CADENCE` (Priority 40), other bike sensors (Priority 50).
• Secondary tier (Shared / Generic sensors): `HRM` (Priority 100), `ENVIRONMENT` (Priority 110), other shared sensors (Priority 120).
• Tertiary tier (Unknown / other sensors): Priority 200.
• Tie-breaking: Case-insensitive alphabetical by `name` (`String.CASE_INSENSITIVE_ORDER`), then by sensor `id` ascending.
2. *Running Sensor Ordering (`BSportType.RUN`)*:
• Primary tier (Running-specific sensors): `RUN_SPEED` (Footpod / Run speed & distance, Priority 10), other run sensors (Priority 20).
• Secondary tier (Shared / Generic sensors): `HRM` (Priority 100), `ENVIRONMENT` (Priority 110), other shared sensors (Priority 120).
• Tertiary tier (Unknown / other sensors): Priority 200.
• Tie-breaking: Case-insensitive alphabetical by `name` (`String.CASE_INSENSITIVE_ORDER`), then by sensor `id` ascending.
3. *Centralized Comparator Utility (`EquipmentSensorOrdering`)*:
• The system SHALL centralize sensor comparator logic in `EquipmentSensorOrdering` ensuring identical ordering across ViewModel, database queries, and UI components.
• The comparator SHALL safely handle `null` sensor references or sensors with `deviceType == null` by placing them in the lowest priority tier (Rank 200).
4. *Database Query Integration (`DevicesDatabaseManager.java`)*:
• `DevicesDatabaseManager.getSensorsForSportType(BSportType sportType)` SHALL return sensor lists deterministically sorted using `EquipmentSensorOrdering` for the requested sport.
5. *ViewModel Reactive State (`EquipmentViewModel.kt`)*:
• `EquipmentViewModel._bikeSensors` and `bikeSensorsList` SHALL emit bike sensors sorted by `EquipmentSensorOrdering` for `BSportType.BIKE`.
• `EquipmentViewModel._shoeSensors` and `runSensorsList` SHALL emit shoe sensors sorted by `EquipmentSensorOrdering` for `BSportType.RUN`.
6. *UI Presentation Consistency (`EquipmentSensorMatrixScreen.kt` & `EditEquipmentDialog.kt`)*:
• `EquipmentSensorMatrixScreen.kt` Table 1 (Bikes) and Table 2 (Shoes) column headers SHALL be displayed in prioritized order. The fallback path using raw `sensors` SHALL also apply `EquipmentSensorOrdering`.
• In `EditEquipmentDialog.kt`, `compatibleSensors` displayed in `MultiSelectSensorSpinner` SHALL be sorted using `EquipmentSensorOrdering` based on whether the equipment is a bike (`item.frameType > 0`) or shoe.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Target & Historical Trace*: Refines and amends `REQ-UI-256` (*Equipment Management: Fleet-Wide Equipment-to-Sensor Mapping Matrix with Checkboxes for Bikes and Shoes*).
2. *Historical Origin & Commit Trace*: Tickets `ATT-2126` and `ATT-2382` (Sprint `2026-40.16`).
3. *Root Reason for Existing Formulation*: ATT-2382 partitioned the matrix into separate tables and filtered sensors by sport compatibility, but retained SQLite's default `NAME COLLATE NOCASE ASC` order without sport telemetry domain priority.
4. *Preservation of Core Invariants*: 1-tap checkbox persistence, bidirectional synchronization (`REQ-UI-257`), independent horizontal scroll states for Bikes and Shoes tables, and 100% clean-room test pass rate remain strictly preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* paired sensors including "Garmin HRM-Dual" (HRM), "Stages Power" (BIKE_POWER), "Wahoo SPEED" (BIKE_SPEED), and "Garmin Tempe" (ENVIRONMENT),
• *When* viewing the Bikes table in `EquipmentSensorMatrixScreen`,
• *Then* columns SHALL be ordered: "Stages Power", "Wahoo SPEED", "Garmin HRM-Dual", "Garmin Tempe".
• *Given* paired sensors including "Garmin HRM-Dual" (HRM), "Stryd Footpod" (RUN_SPEED), and "Garmin Tempe" (ENVIRONMENT),
• *When* viewing the Shoes table in `EquipmentSensorMatrixScreen`,
• *Then* columns SHALL be ordered: "Stryd Footpod", "Garmin HRM-Dual", "Garmin Tempe".
• *Given* multiple bike power meters (e.g. "Stages L", "4iiii Precision"),
• *When* ordering bike sensors,
• *Then* power meters SHALL appear before speed/cadence/shared sensors, sorted alphabetically: "4iiii Precision" before "Stages L".
• *Given* opening `EditEquipmentDialog` for a bike or shoe,
• *When* expanding the sensor selection spinner,
• *Then* available sensors SHALL appear in the same prioritized order.

**Invariants**: 1-tap checkbox persistence, bidirectional synchronization (`REQ-UI-257`), independent horizontal scroll states, and 100% test pass rate MUST NOT be broken.

### REQ-UI-284: Sensors View Sport-Specific Equipment Matrix Tabs, Visual Link Indicators & Equipment Tabs Consolidation [Verified]
- **Tests**: `TST-UI-244`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `DevicesTabbedScreen.kt`, `EquipmentTabsScreen.kt`, `EquipmentSensorMatrixScreen.kt`, `strings_devices.xml` (all 9 locales)
- **Rationale**: The system SHALL relocate equipment-to-sensor mapping matrices into the Sensors view (`DevicesTabbedScreen.kt`) as two dedicated tabs at the very right, host isolated sport-specific matrix tables for Bikes and Shoes, display visual link indicators on matrix tabs, support horizontal scrolling without tab label clipping, streamline `EquipmentTabsScreen.kt` to two tabs, and enforce 100% 9-language localization parity (ATT-2465, ATT-3314):
1. *Sensors View 5-Tab Architecture (`DevicesTabbedScreen.kt`)*:
• `DevicesTabbedScreen.kt` SHALL provide a 5-tab structure: Tab 0 Available (`@string/devices_tab_available`), Tab 1 Paired (`@string/devices_tab_paired`), Tab 2 All Known (`@string/devices_tab_known`), Tab 3 Bikes Matrix (`@string/devices_tab_bikes`, "Räder" / "Bikes"), and Tab 4 Shoes Matrix (`@string/devices_tab_shoes`, "Schuhe" / "Shoes").
• Tab 3 (Bikes Matrix) and Tab 4 (Shoes Matrix) SHALL incorporate a visual link/chain symbol (`Icons.Default.Link`) as a leading icon next to the localized title text inside the `Tab` composable's `text` slot (composed via horizontal `Row` containing a 16.dp `Icons.Default.Link`, 6.dp `Spacer`, and `Text(title)`), visually indicating that these tabs configure sensor-to-equipment assignment matrices rather than static inventory lists, while preserving standard 48.dp tab row height and alignment across all screen densities (`LocalContentColor.current` reactive tinting) (ATT-3314).
• The tab row SHALL use `PrimaryScrollableTabRow`, ensuring smooth horizontal scrollability without label clipping, truncation, or awkward line wrapping across screen sizes and locales.
• `DevicesTabbedScreen` SHALL accept or resolve `EquipmentViewModel: EquipmentViewModel = viewModel()`, observing reactive state flows `bikes`, `shoes`, `bikeSensors`, and `shoeSensors`.
2. *Sport-Specific Equipment-Sensor Matrix Tabs (`DevicesTabbedScreen.kt` & `EquipmentSensorMatrixScreen.kt`)*:
• Tab 3 (Bikes) SHALL display strictly the Bikes Equipment-to-Sensor Matrix table: sticky left column (`STICKY_COLUMN_WIDTH = 184.dp`) rendering bike icon, name, and retirement status; horizontally scrollable columns rendering bike-compatible and shared sensors (`BSportType.BIKE`) ordered by domain precedence (`REQ-UI-283`); independent horizontal scroll state (`bikesHorizontalScrollState`); 1-tap Material 3 Checkbox persistence via `EquipmentViewModel.setSensorLink`; redundant in-tab section header is omitted.
• Tab 4 (Shoes) SHALL display strictly the Shoes Equipment-to-Sensor Matrix table: sticky left column (`STICKY_COLUMN_WIDTH = 184.dp`) rendering shoe icon, name, and retirement status; horizontally scrollable columns rendering run-compatible and shared sensors (`BSportType.RUN`) ordered by domain precedence (`REQ-UI-283`); independent horizontal scroll state (`shoesHorizontalScrollState`); 1-tap Material 3 Checkbox persistence via `EquipmentViewModel.setSensorLink`; redundant in-tab section header is omitted.
• Both matrix tabs SHALL participate in the collapsing top app bar nested scroll connection (`connection`).
3. *Equipment View Streamlining (`EquipmentTabsScreen.kt`)*:
• `EquipmentTabsScreen.kt` SHALL revert to a clean 2-tab structure: Tab 0 Bikes (`@string/equipment_type_bike`) and Tab 1 Shoes (`@string/equipment_type_shoe`).
• The redundant 3rd tab ("Sensor-Matrix") SHALL be removed.
• The floating action button (FAB) for adding new equipment SHALL be rendered for both tabs without conditional suppression.
4. *100% 9-Language Localization Parity (`REQ-LOC-001`)*:
• String resources `devices_tab_bikes` and `devices_tab_shoes` SHALL be defined across all 9 supported application locales (`values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`).

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Target & Historical Trace*: Refines and amends `REQ-UI-256` (*Equipment Management: Fleet-Wide Equipment-to-Sensor Mapping Matrix with Checkboxes for Bikes and Shoes*).
2. *Historical Origin & Commit Trace*: Tickets `ATT-2126` (commit `fc10ad81`), `ATT-2382` (Sprint `2026-40.16`), `ATT-2465` (Sprint `2026-41.2`), and `ATT-3314` (Sprint `2026-42.1`).
3. *Root Reason for Existing Formulation*: ATT-2465 moved the matrix into `DevicesTabbedScreen` as tabs 3 and 4 with text-only titles. Sprint 2026-41.10 user feedback (ATT-3166) identified that text-only labels "Räder" and "Schuhe" cause cognitive ambiguity with equipment inventory tabs; adding `Icons.Default.Link` clarifies their assignment matrix role.
4. *Preservation of Core Invariants*: 1-tap checkbox persistence, bidirectional synchronization (`REQ-UI-257`), sticky columns (`REQ-UI-263`), sport-specific sensor precedence (`REQ-UI-283`), uniform 48.dp tab height, independent horizontal scroll states, and 100% clean-room test pass rate remain strictly preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an athlete opening the Sensors view (`DevicesTabbedScreen`),
• *When* inspecting the tab bar,
• *Then* 5 tabs SHALL be displayed in order: "Verbunden", "Gekoppelt", "Bekannt", "Räder", "Schuhe", with Tab 3 ("Räder") and Tab 4 ("Schuhe") displaying leading `Icons.Default.Link` icons.
• *Given* the athlete navigates to Tab 3 ("Räder") in `DevicesTabbedScreen`,
• *When* the tab content renders,
• *Then* strictly the Bikes matrix table SHALL be displayed with sticky bike name column and horizontally scrollable bike/shared sensors.
• *Given* the athlete navigates to Tab 4 ("Schuhe") in `DevicesTabbedScreen`,
• *When* the tab content renders,
• *Then* strictly the Shoes matrix table SHALL be displayed with sticky shoe name column and horizontally scrollable run/shared sensors.
• *Given* the athlete scrolls sensor columns horizontally in Tab 3 ("Räder"),
• *When* switching to Tab 4 ("Schuhe"),
• *Then* the horizontal scroll state of the Shoes table SHALL remain completely independent.
• *Given* an unlinked checkbox in either matrix tab,
• *When* tapped,
• *Then* the checkbox immediately toggles to checked, updating SQLite table `LINKS`, and reactively synchronizing with `EditEquipmentDialog`.
• *Given* the athlete opens the Equipment view (`EquipmentTabsScreen`),
• *When* inspecting the screen,
• *Then* only 2 tabs SHALL be present ("Räder" and "Schuhe"), and the FAB for adding equipment SHALL be active on both tabs.
• *Given* all 5 tabs in `DevicesTabbedScreen`,
• *When* measuring layout metrics,
• *Then* all tabs SHALL maintain uniform 48.dp height and aligned text baselines.
• *Given* all 9 supported locales,
• *When* running `TranslationParityTest`,
• *Then* zero missing entries and zero format specifier mismatches SHALL occur.

**Invariants**: 1-tap checkbox persistence, bidirectional synchronization, independent horizontal scroll states, 48.dp tab row ergonomics, and 100% test pass rate MUST NOT be broken.

### REQ-UI-285: Harmonized 9-Section Architecture and Default Visibility Matrix for Workout Summaries and Details [Verified]
- **Tests**: `TST-UI-245`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `WorkoutSectionType.kt`, `MyPreferenceManager.kt`, `WorkoutMasksAndCardsSection.kt`, `WorkoutSummary.kt`, `TrackOnMapScreen.kt`
- **Rationale**: The system SHALL integrate `EXPORT_STATUS` into `WorkoutSectionType`, define an ergonomically optimized default sequence (`DEFAULT_ORDER`), and unify clean-installation default visibility matrices across workout list cards and detail views (ATT-2384):
1. *9-Section Domain Model & DEFAULT_ORDER (`WorkoutSectionType.kt`)*:
• `WorkoutSectionType` SHALL define enum constant `EXPORT_STATUS(R.string.export_status)`.
• `WorkoutSectionType.DEFAULT_ORDER` SHALL be defined in exact sequence: `[DESCRIPTION, EXTREMA, MAP, ELEVATION, CHARTS, ZONES, LAPS, EXPORT_STATUS, STRAVA]` (9 sections).
• `fromSerializedString(serialized: String?)` SHALL parse all 9 section types and automatically append any missing enum values to guarantee self-healing backwards compatibility for existing installations.
2. *Harmonized Default Visibility Matrix (`MyPreferenceManager.kt`)*:
• `WorkoutCardSectionPreferences` (Journal List Cards) defaults SHALL be: `showDescription = true`, `showExtrema = true`, `showMapPreview = true`, `showElevationProfile = false`, `showTelemetryCharts = false`, `showZoneAnalysis = true`, `showLaps = false`, `showExportStatus = true`, `showStrava = true`, `lapDisplayMode = LapDisplayMode.VISUALIZER_ONLY`.
• `WorkoutDetailPreferences` (Workout Details Screen) defaults SHALL be `true` across all 9 sections (`showDescription`, `showExtrema`, `showMap`, `showElevationProfile`, `showTelemetryCharts`, `showZoneAnalysis`, `showLaps`, `showExportStatus`, `showStrava`).
• `dataStore` keys `WORKOUT_CARD_SHOW_EXPORT_STATUS` and `WORKOUT_DETAIL_SHOW_EXPORT_STATUS` SHALL be persisted and default to `true`.
3. *Settings Matrix Feature Row (`WorkoutMasksAndCardsSection.kt`)*:
• `WorkoutMasksAndCardsSection.kt` SHALL render a `MatrixFeatureRow` for `WorkoutSectionType.EXPORT_STATUS` with 48dp touch targets, localized title `R.string.export_status`, alternating row background, and independent List/Detail checkbox toggles.
4. *Modular Section Execution in Presentation Layer*:
• In `WorkoutSummary.kt`, `ExportStatus` SHALL be executed within `workoutSectionsOrder.forEach` when `type == WorkoutSectionType.EXPORT_STATUS && preferences.showExportStatus`. The fixed hardcoded call to `ExportStatus(...)` after the loop SHALL be removed.
• In `TrackOnMapScreen.kt`, `ExportStatus` SHALL be rendered when `type == WorkoutSectionType.EXPORT_STATUS && activeDetailPrefs.showExportStatus` in post-map analytics content before `STRAVA`.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Target & Historical Trace*: Refines and amends `REQ-UI-255` (*Customizable Workout Journal Card & Detail View Section Visibility and Reordering*).
2. *Historical Origin & Commit Trace*: Sprint 2026-40.13 (`ATT-2176`), refined in `ATT-2383` (Sprint 2026-41.1).
3. *Root Reason for Existing Formulation*: `REQ-UI-255` introduced the initial 8-section reordering engine. Export status was previously treated as an auxiliary footer outside the reorderable list, and card defaults enabled laps while disabling zones.
4. *Preservation of Core Invariants*: 0 dp empty state handling for notes/description and inactive export statuses, self-healing deserialization, reorder controls in tuning dialog, and 100% full-suite test pass rate remain strictly preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* a clean installation or call to `WorkoutSectionType.DEFAULT_ORDER`,
• *When* inspecting the sequence,
• *Then* it SHALL contain exactly 9 sections in order: `DESCRIPTION`, `EXTREMA`, `MAP`, `ELEVATION`, `CHARTS`, `ZONES`, `LAPS`, `EXPORT_STATUS`, `STRAVA`.
• *Given* a legacy serialized string without `EXPORT_STATUS`,
• *When* parsed via `WorkoutSectionType.fromSerializedString`,
• *Then* `EXPORT_STATUS` and all missing sections SHALL be appended at the end without errors.
• *Given* initial `WorkoutCardSectionPreferences`,
• *When* checking default values,
• *Then* `showElevationProfile`, `showTelemetryCharts`, and `showLaps` SHALL be `false`, while `showDescription`, `showExtrema`, `showMapPreview`, `showZoneAnalysis`, `showExportStatus`, and `showStrava` SHALL be `true`.
• *Given* initial `WorkoutDetailPreferences`,
• *When* checking default values,
• *Then* all 9 section toggles SHALL be `true`.
• *Given* a custom order where `EXPORT_STATUS` is moved before `MAP`,
• *When* rendering `WorkoutSummary`,
• *Then* `ExportStatus` SHALL render before the map preview, and NOT as a fixed footer.
• *Given* `TrackOnMapScreen`,
• *When* `activeDetailPrefs.showExportStatus` is `true`,
• *Then* `ExportStatus` SHALL render dynamically in `analyticsContent` according to `sectionsOrder`.
• *Given* `WorkoutMasksAndCardsSection`,
• *When* rendered,
• *Then* 9 feature rows SHALL appear with functioning List and Detail checkbox toggles and reorder buttons.

**Invariants**: Self-healing deserialization, drag/reorder controls in tuning dialog, 0dp empty state handling, and 100% test pass rate MUST NOT be broken.

### REQ-UI-286: ResearchButton Jetpack Compose Vector Resilience & Universal Resource Fallback [Verified]
- **Tests**: `TST-UI-246`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `ResearchButton.kt`, `res/drawable/research_icon.xml`, `strings.xml`
- **Rationale**: The system SHALL guarantee runtime immunity against `Resources$NotFoundException` when rendering `ResearchButton` and ensure universal fallback resolution for research icon assets across all device configurations and screen densities (ATT-2619):
1. *Jetpack Compose Vector Modernization (`ResearchButton.kt`)*:
• `ResearchButton.kt` SHALL render its icon using `Icons.Default.Refresh` (`ImageVector`) directly within Compose code, eliminating runtime Android resource lookups via `Resources.getValue()` / `painterResource()` and preventing `Resources$NotFoundException` crashes on non-standard device densities or split-APK configurations.
• `Icon` SHALL supply `contentDescription = stringResource(id = R.string.research)` to provide accessible screen reader labels.
2. *State & Visual Parity*:
• `ResearchButton` SHALL preserve 48dp dimension (`Modifier.size(48.dp)`).
• The icon tint SHALL evaluate to `MaterialTheme.colorScheme.primary` when `isEnabled == true`, and `Color.Gray.copy(alpha = 0.4f)` when `isEnabled == false`.
• Click interactions SHALL remain bound to `Modifier.clickable(enabled = isEnabled, onClick = onClick)`.
• Text label SHALL display `stringResource(id = R.string.research)` with `MaterialTheme.typography.labelMedium`.
3. *Universal Resource Fallback (`res/drawable/research_icon.xml`)*:
• The system SHALL provide a vector drawable `app/src/main/res/drawable/research_icon.xml` in the root `drawable/` directory.
• This ensures that non-Compose Android system callers (e.g. `TrainingApplication.java` notification action builders referencing `R.drawable.research_icon`) resolve the drawable safely without throwing `Resources$NotFoundException` across all device form factors and screen densities.
4. *9-Language Localization Parity*:
• String resource `research` SHALL be verified with 100% parity across all 9 supported application locales (EN, DE, ES, FR, IT, JA, NL, PL, PT).

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement (`REQ-UI-286`), targeting `ResearchButton.kt` and `res/drawable/research_icon.xml`.
2. *Historical Origin & Commit Trace*: Hotfix release `V4.9.38.3` addressing production crash `ATT-2619` (`Issue 355c60b8489f67ef630dc85e12708945`).
3. *Root Reason for Existing Formulation*: `ResearchButton.kt` previously loaded `R.drawable.research_icon` through `painterResource()`. Because `research_icon.png` was only present in density-specific folders without a default `res/drawable/` fallback, runtime resolution threw `Resources$NotFoundException` on certain device density configurations.
4. *Preservation of Core Invariants*: 48dp sizing, primary/disabled tinting, click handling, and 9-language translation parity are 100% preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an athlete on the tracking control screen (`ControlTrackingScreen`),
• *When* `ResearchButton` composable is composed under any display density or split APK configuration,
• *Then* `ResearchButton` SHALL render `Icons.Default.Refresh` without throwing `Resources$NotFoundException`.
• *Given* `ResearchButton` with `isEnabled == true`,
• *When* rendered,
• *Then* the icon tint SHALL evaluate to `MaterialTheme.colorScheme.primary` and the button SHALL respond to tap gestures.
• *Given* `ResearchButton` with `isEnabled == false`,
• *When* rendered,
• *Then* the icon tint SHALL evaluate to `Color.Gray.copy(alpha = 0.4f)` and tap gestures SHALL be disabled.
• *Given* any Android caller resolving `R.drawable.research_icon` via `context.getDrawable(R.drawable.research_icon)`,
• *Then* the resource SHALL resolve to a non-null Drawable without throwing `Resources$NotFoundException`.

**Invariants**: Tracking control layout, sensor pairing triggers, and 9-language localization parity MUST NOT be broken.

### REQ-UI-287: Navigation Cue Auto-Dismiss Duration, Countdown Distance & Expert Settings Configuration (Solid Opaque Overlays) [Verified]
- **Tests**: `TST-UI-247`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `TuningPreferencesDataStore.kt`, `NavigationSection.kt`, `AdvancedTuningDialog.kt`, `AdvancedTuningAccordion.kt`, `TurnPromptBanner.kt`, `SensorGridScreen.kt`, `ForkDecisionCard.kt`, `ReturnNavigationHud.kt`
- **Rationale**: The system SHALL render all in-ride navigation cue banners, return navigation HUDs, and fork decision cards with permanently solid (100% opaque) container surfaces, remove the navigation cue transparency slider from Advanced Settings, and maintain configurable auto-dismiss duration and turn cue countdown distance in Expert Settings (ATT-2632, ATT-3321, ATT-3322, ATT-3325, ATT-3450, amending `REQ-UI-287`, `REQ-UI-321`, `REQ-UI-324`, and `docs/design_guidelines.md` § 1.4 & § 5.7):
1. *Preference DataStore Persistence (`TuningPreferencesDataStore.kt`)*:
• The system SHALL deprecate `KEY_NAVIGATION_CUE_TRANSPARENCY` and ensure `TuningConfig.navigationCueTransparency` defaults to `1.0f` (100% solid opacity).
• The system SHALL define `KEY_NAVIGATION_CUE_DISMISS_DURATION_SEC` (Int) with default `4` seconds, supporting all integer values from `1s` to `10s` with 1s steps (ATT-3450). Legacy user profiles with `0` stored SHALL automatically fall back to `DEFAULT_NAVIGATION_CUE_DISMISS_DURATION_SEC` (`4` seconds). Out-of-bounds values SHALL be clamped to `[1, 10]`.
• The system SHALL define `KEY_TURN_CUE_COUNTDOWN_DISTANCE` (Float) with default `100.0f` (100 m), clamped to `[30.0f, 300.0f]`, with dedicated mutator `updateTurnCueCountdownDistanceMeters(distanceMeters: Float)`.
• `TuningConfig` SHALL expose `navigationCueDismissDurationSec: Int`, `turnCueCountdownDistanceMeters: Float`, and deprecated `navigationCueTransparency: Float = 1.0f`.
• Atomic factory reset (`resetToDefaults()`) SHALL restore default values `4`s, `100.0f`m, and `1.0f` opacity.
2. *Expert Settings UI (`NavigationSection.kt`, `AdvancedTuningDialog.kt`)*:
• The obsolete "Navigation Cue Transparency" (*Transparenz der Navigationshinweise*) slider SHALL be completely removed from `NavigationSection.kt` and `AdvancedTuningDialog.kt`.
• `NavigationSection.kt` SHALL expose a `TuningSliderItem` for "Navigation Cue Dismiss Duration" (*Anzeigedauer der Navigationshinweise*) spanning `1s` to `10s` with 1s steps (default 4s), formatted uniformly as `"${sec}s"`, with NO `FilterChip` items rendered, eliminating multi-line wrapping per `docs/design_guidelines.md` § 1.4 (ATT-3450).
• `NavigationSection.kt` SHALL expose a `TuningSliderItem` for "Navigation Cue Distance" (*Distanz für Abbiegehinweise*) spanning `30 m` to `300 m` with 26 steps (10 m increments, default 100 m, snapping to the nearest 10 m).
• `TuningSubtitleFormatter.formatNavigationSubtitle()` SHALL format an informative live summary subtitle incorporating dismiss duration and route proximity radius without a transparency percentage token (e.g. `"4s, 1.0 km"` or `"10s, 1.0 km"`).
3. *Runtime HUD Application (`TurnPromptBanner.kt`, `SensorGridScreen.kt`, `TurnByTurnNavigationEngine.kt`)*:
• `TurnPromptBanner.kt` container surfaces SHALL render 100% solid (opaque `surfaceContainer` when approaching, `primaryContainer` when `isTurnNow`, and `errorContainer` when off-route), accompanied by a subtle Royal Blue border accent (`BorderStroke(1.dp, TTColor.RouteActiveNavigation.copy(alpha = 0.5f))`) per `docs/design_guidelines.md` Section 5.4 & 5.7.
• When approaching a cue or transitioning to `isTurnNow`, the banner SHALL initiate an auto-dismiss countdown timer and smoothly animate out when `dismissDurationSec` ($1 \dots 10\text{ s}$) elapses, guaranteeing turn banners never permanently occlude the cockpit and map viewport (ATT-3325, ATT-3450).
• Changing the upcoming cue or transitioning between `APPROACHING` and `TURN_NOW` SHALL reset the auto-dismiss timer and restore banner visibility.
• In `TurnByTurnNavigationEngine.kt`, `countdownThreshold` evaluates dynamically to `tuningConfig.turnCueCountdownDistanceMeters.toDouble()`, triggering `isApproaching = true` when distance to upcoming cue $\le \text{countdownThreshold}$.
4. *Visual Consistency & Extended Overlays*:
• `ForkDecisionCard.kt` and `ReturnNavigationHud.kt` SHALL render with solid 100% opaque Material 3 container surfaces to guarantee crisp legibility under all outdoor ambient lighting conditions without background map bleed-through.
5. *100% 9-Language Localization Parity*:
• Obsolete transparency and persistent strings SHALL be excised, and all active preference titles, descriptions, and duration labels SHALL be localized across all 9 supported locales (EN, DE, ES, FR, IT, JA, NL, PL, PT).

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: `REQ-UI-287` (*Navigation Cue Overlay Transparency, Dismiss Duration & Expert Settings Configuration*), amending Clauses 1, 2, 3, 4, and `REQ-UI-321` Clause 3, `REQ-UI-324` Clause 3.
2. *Historical Origin & Commit Trace*: Introduced in Sprint 2026-41.2 (`ATT-2632`, commit `8e6bcfc5`), and integrated into top-level spatial overlays in Sprint 2026-41.3 (`ATT-2874`, commit `00687108` / `REQ-UI-321`; `ATT-2938`, commit `479c3d9a` / `REQ-MAP-039`; `ATT-2941`, commit `68b48574` / `REQ-UI-324`). Amended in Sprint 2026-42.1 (`ATT-3321`, `ATT-3322`, `ATT-3325`) and Sprint 2026-42.2 (`ATT-3450`).
3. *Root Reason for Existing Formulation*: In ATT-2632, semi-transparency and indefinite persistent banners were introduced. In ATT-3325, persistent banners were replaced with 10s using FilterChips. In ATT-3450, FilterChips were replaced with TuningSliderItem spanning 1s to 10s to eliminate multi-line wrapping and conform to `docs/design_guidelines.md` § 1.4.
4. *Preservation of Core Invariants*: Default 4s dismiss duration, legacy profile auto-fallback to 4s, 100% solid overlays, turn countdown distance tuning (`[30m .. 300m]`, default 100m), non-displacing top-level spatial overlay architecture, strict `state.showNavigationHints` per-tab gating, audio cues, and 100% full-suite unit test pass rate remain strictly preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an active route navigation session on `SensorGridScreen`,
• *When* `TurnPromptBanner`, `ReturnNavigationHud`, or `ForkDecisionCard` appears,
• *Then* the card container renders 100% opaque, with zero map polyline or underlying text bleeding through the card background.
• *Given* an athlete navigating to `NavigationSection` in Expert Settings,
• *When* inspecting the settings list,
• *Then* the transparency slider is absent, and the dismiss duration control is a `TuningSliderItem` spanning 1s to 10s with 1s steps (default 4s), with zero `FilterChip` items rendered.
• *Given* dragging the slider to any integer between 1s and 10s (e.g. `7s`),
• *Then* `7` SHALL be persisted to DataStore, the subtitle SHALL read `"7s, 1.0 km"`, and banners SHALL display for 7 seconds.
• *Given* a legacy installation with `0` stored in DataStore,
• *When* DataStore initializes,
• *Then* `navigationCueDismissDurationSec` SHALL evaluate to `4` (default).
• *Given* adjusting cue distance slider to 200 m,
• *Then* the display label SHALL read `200 m` and `200.0f` SHALL be persisted to DataStore, causing turn prompts to trigger at 200 m.
• *Given* an active navigation session with `dismissDurationSec = 4`,
• *When* approaching a turn cue,
• *Then* `TurnPromptBanner` SHALL appear and auto-dismiss after 4 seconds, reappearing upon `isTurnNow`.

**Invariants**: Turn cue detection math, offline operation, DataStore single-source-of-truth, and 100% full-suite test pass rate MUST NOT be broken.

### REQ-UI-288: Equipment Sensor Matrix Bike and Shoe Icon Tint Unification [Verified]
- **Tests**: `TST-UI-248`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `EquipmentSensorMatrixScreen.kt`
- **Rationale**: The system SHALL unify active equipment icon tinting across all equipment categories in the equipment sensor matrix to MaterialTheme.colorScheme.primary while preserving MaterialTheme.colorScheme.outline for retired equipment (ATT-2630):
1. *Icon Tint Unification (`EquipmentSensorMatrixScreen.kt`)*:
• In `MatrixEquipmentRow`, the leading icon tint SHALL evaluate to `MaterialTheme.colorScheme.outline` when `item.isRetired == true`.
• When `item.isRetired == false`, the leading icon tint SHALL evaluate to `MaterialTheme.colorScheme.primary` for both bikes (`isBike == true`) and shoes (`isBike == false`).
• `MaterialTheme.colorScheme.secondary` SHALL NOT be used for active equipment icon tinting in `MatrixEquipmentRow`.
2. *Asset & Header Parity*:
• The vector assets `ic_equipment_bike` and `ic_equipment_shoe` SHALL continue to distinguish bike and shoe categories.
• `MatrixSectionHeader` SHALL preserve `MaterialTheme.colorScheme.primary` tinting for both bike and shoe headers.
3. *Material 3 Theme Consistency*:
• Icon sizing (`22.dp`), row height (`56.dp`), sticky column width (`184.dp`), and subtle divider colors SHALL remain strictly preserved.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Target & Historical Trace*: Refines Clause 4 of `REQ-UI-263` (*Equipment Sensor Matrix Table Layout, Column Continuity, and Information Density Optimization*) and integrates with `REQ-UI-284` (*Sensors View Sport-Specific Equipment Matrix Tabs & Equipment Tabs Consolidation*), targeting `EquipmentSensorMatrixScreen.kt`.
2. *Historical Origin & Commit Trace*: Ticket `ATT-2630`, Sprint `2026-41.2`, target release `V4.9.39`, Epic `ATT-355` (*[Epic] Good and consistent UI*).
3. *Root Reason for Existing Formulation*: When the matrix was originally a single combined table in `ATT-2126`, `secondary` was applied to shoes to visually contrast against bikes. In isolated tabs (`REQ-UI-284`) and adjacent to `MatrixSectionHeader` (which uses `primary` for both), this resulted in jarring, inconsistent tinting.
4. *Preservation of Core Invariants*: Vector drawables remain distinct (`ic_equipment_bike` for bikes, `ic_equipment_shoe` for shoes), retired items preserve `outline` tinting, 1-tap checkbox persistence and bidirectional synchronization (`REQ-UI-257`) remain 100% intact, and 100% full-suite test pass rate MUST NOT be broken.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an active (non-retired) bike item rendered in `MatrixEquipmentRow`,
• *When* the row composable resolves its icon tint,
• *Then* the tint SHALL evaluate to `MaterialTheme.colorScheme.primary`.
• *Given* an active (non-retired) shoe item rendered in `MatrixEquipmentRow`,
• *When* the row composable resolves its icon tint,
• *Then* the tint SHALL evaluate to `MaterialTheme.colorScheme.primary` (matching active bikes).
• *Given* a retired equipment item (either bike or shoe, `item.isRetired == true`),
• *When* the row composable resolves its icon tint,
• *Then* the tint SHALL evaluate to `MaterialTheme.colorScheme.outline`.
• *Given* the athlete switching between Tab 3 ("Räder") and Tab 4 ("Schuhe") in `DevicesTabbedScreen`,
• *When* observing the section header and equipment rows,
• *Then* both views SHALL present uniform `primary` chromatic accenting for active items.

**Invariants**: Vector drawables, retired outline styling, 1-tap checkbox persistence (`REQ-UI-257`), and 100% full-suite test pass rate MUST NOT be broken.

### REQ-UI-289: Context-Aware Route Selection: Start Proximity & In-Ride Corridor Auto-Detection [Verified]
- **Tests**: `TST-UI-249`
- **Priority**: High
- **Type**: Functional
- **Verification Method**: Unit / Review
- **Implementation**: `RouteSelectorViewModel.kt`, `RouteAutoDetector.kt`, `RouteSelectorSheet.kt`
- **Rationale**: The system SHALL dynamically switch candidate route evaluation logic in `RouteSelectorViewModel` based on tracking state: when `isTrackingActive == false`, candidate routes SHALL be ranked by start point proximity (`RouteProximityRanker`); when `isTrackingActive == true`, candidates SHALL be evaluated by in-ride path corridor matching (`RouteAutoDetector.evaluateMatchingRoutes`), displaying context-aware empty state subtitles in `RouteSelectionButton` and mounting `MidRideHeimwegCard` in `RouteSelectorSheet`.
- **Description**: Context-Aware Route Selection: Start Proximity & In-Ride Corridor Auto-Detection.

### REQ-UI-290: Sensor Pairing Device Type Selection: Omission of 'Alle' Action and Targeted Hardware Profile Discovery [Verified]
- **Tests**: `TST-UI-250`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `DeviceTypeSelectionDialog.kt`, `DevicesTabbedScreen.kt`, `ControlTrackingScreen.kt`
- **Rationale**: The system SHALL omit the "Alle" ("All") button from the device type selection bottom sheet (`DeviceTypeSelectionDialog`) during sensor pairing flows, enforcing targeted wireless discovery for specific hardware sensor profiles (ATT-2625):
1. *Exclusion of 'Alle' Button from Pairing Flows (`DeviceTypeSelectionDialog.kt`)*:
• `DeviceTypeSelectionDialog` SHALL expose parameter `showAllOption: Boolean = false`.
• When `showAllOption == false` (the default for pairing flows), `AppModalBottomSheet`'s `actions` slot SHALL be `null`, omitting the bottom action bar and divider completely.
• When `showAllOption == false`, `DeviceType.ALL` SHALL NOT be presented to the user.
2. *Presentation of Specific Hardware Profiles*:
• The dialog SHALL exclusively present the supported physical hardware device profiles for the selected protocol (`DeviceType.getRemoteDeviceTypes(protocol)`), such as Heart Rate, Power Meter, Speed/Cadence, etc.
• Each item SHALL display its official icon (`getIconId(type, protocol)`) and localized display name (`UIHelper.getNameId(type)`).
3. *Unbroken Pairing Initiation & Interaction*:
• Tapping a specific device type item SHALL invoke `onSelected(type)` with the concrete `DeviceType`.
• Tapping outside, swiping down, or tapping the top-right close icon SHALL invoke `onDismiss()`.
4. *Integration Sites*:
• In `DevicesTabbedScreen.kt` (Sensors view pairing FAB flow), `DeviceTypeSelectionDialog` SHALL invoke without `showAllOption` (defaulting to `false`).
• In `ControlTrackingScreen.kt` (Control screen pairing button flow), `DeviceTypeSelectionDialog` SHALL invoke without `showAllOption` (defaulting to `false`).

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Refines `REQ-UI-042` (*Sensor Selection Flow and Navigation Overlay*) and `REQ-UI-278` (*Sensors Screen Pairing FAB & Protocol Selection*).
2. *Historical Origin & Commit Trace*: Commit `d4b2e864b7` (SCRUM-37) placed "Alle" as an unfiltered view reset button when entering the sensors list. Commit `43b35eb6` (ATT-2189) reused the dialog for pairing.
3. *Root Reason for Existing Formulation*: The dialog previously retained the legacy filter action bar. In a pairing initiation flow, "Alle" is semantically invalid because discovery scans must target a specific hardware profile.
4. *Preservation of Core Invariants*: Profile discovery, icon rendering, close dismiss, and full unit test pass rate remain strictly preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an athlete invoking sensor pairing from the '+' FAB in Sensors view or from ControlTrackingScreen,
• *When* selecting ANT+ or Bluetooth LE protocol,
• *Then* `DeviceTypeSelectionDialog` SHALL open displaying strictly specific sensor hardware types (`DeviceType.getRemoteDeviceTypes(protocol)`).
• *Then* no "Alle" button and no bottom action bar SHALL be rendered in the sheet.
• *When* tapping a specific device profile (e.g. Heart Rate),
• *Then* `onSelected(DeviceType.HEART_RATE)` SHALL be dispatched and discovery scan initiated for that profile.

**Invariants**: Supported device profiles, dialog dismissal, protocol routing, and 100% full-suite unit test pass rate MUST NOT be broken.

### REQ-UI-291: Sensors View Device Inventory Preservation During Active Pairing Scanning [Verified]
- **Tests**: `TST-UI-251`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `DevicesTabbedScreen.kt`, `DevicesTabbedViewModel.kt`, `DeviceListScreen.kt`
- **Rationale**: The system SHALL preserve the complete multi-sensor inventory across all Sensors screen tabs ("Verbunden", "Gekoppelt", "Bekannt", "Fahrräder", "Schuhe") during sensor pairing discovery, decouple the hardware wireless discovery target from the UI tab filter specifications, and communicate the active scanning target in the Available tab header (ATT-2626):
1. *Unrestrictive Tab Filter Specifications (`DevicesTabbedScreen.kt` & `DevicesTabbedViewModel.kt`)*:
• During active sensor pairing discovery, the Sensors screen tabs (`DeviceFilterSpec`s for `CONNECTED`, `PAIRED`, `ALL_KNOWN`) SHALL retain `deviceType = DeviceType.ALL` and `protocol = Protocol.ALL`, ensuring all existing, connected, paired, and known sensors across all device types and protocols remain visible.
• Initiating a pairing scan SHALL NOT mutate `UiState.DisplayingTabs` to a restrictive `deviceType`.
• The screen header title SHALL remain "Sensoren" (`R.string.devices_all_sensors`), preserving unified screen context.
2. *Decoupled Hardware Pairing Scan Dispatch (`DevicesTabbedViewModel.kt`)*:
• `DevicesTabbedViewModel` SHALL provide a decoupled pairing scan initiation method `startPairingScan(protocol: Protocol, deviceType: DeviceType)`.
• When a pairing scan is initiated, `DevicesTabbedViewModel` SHALL dispatch targeted background wireless discovery via `banalServiceRepository.startSearchingForNewDevices(protocol, deviceType)`.
• `DevicesTabbedViewModel` SHALL maintain and expose the active scanning target (`scanningProtocol: StateFlow<Protocol?>` and `scanningDeviceType: StateFlow<DeviceType?>`).
• When scanning is stopped (`stopSearching()`), the active scanning target SHALL reset to `null`.
3. *Context-Aware Searching Header Presentation (`DeviceListScreen.kt`)*:
• In the "Verbunden" (Available) tab, when `isSearchingForNewDevices == true`:
- `SearchingHeader` SHALL display the targeted protocol name and device type name currently being scanned for (`devices_searchingForDevice`, e.g. "Suche nach Bluetooth LE Herzfrequenz Sensoren" / "Searching for Bluetooth LE Heart Rate devices").
- If no specific pairing target is active (e.g. general search), it SHALL fall back gracefully to the tab filter spec names.
• The device list under `SearchingHeader` SHALL render all discovered and available devices without restrictive type filtering.
4. *Preserved Invariants & Localization*:
• Zero new string resources required (reuses existing 100% localized `devices_searchingForDevice` format string across all 9 locales: EN, DE, ES, FR, IT, JA, NL, PL, PT).
• SavedStateHandle crash resilience (`REQ-STB-011`) and process death recovery MUST remain fully preserved.
• Low-level GATT/ANT+ device discovery (`NEW_DEVICE_FOUND_INTENT`) ingestion into `DeviceDataRepository` MUST remain unbroken.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Refines `REQ-STB-011` (*Dynamic Pairing Filter Configuration in DevicesTabbedViewModel*) and `REQ-UI-278` (*Pairing Triggers Relocation to Sensor Management*).
2. *Historical Origin & Commit Trace*: Commit `d4b2e864b7` (SCRUM-37) coupled UI tab filtering with scanner starting. Commit `761d72c1` (ATT-2189) reused `updateFilters` during pairing FAB initiation.
3. *Root Reason for Existing Formulation*: `updateFilters` was designed when device tabs were opened for a single dedicated device type. In the unified Sensors screen, however, an athlete expects to see their entire sensor inventory without having existing sensors hidden during pairing.
4. *Preservation of Core Invariants*: Profile-targeted scanning (`startSearchingForNewDevices(protocol, deviceType)`), device discovery broadcast processing, and full clean-room unit test pass rate remain strictly preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an athlete in "Meine Sensoren" (`DevicesTabbedScreen`) with paired sensors (e.g. ANT+ HRM, BLE Power Meter),
• *When* tapping '+' FAB and selecting Bluetooth LE and Heart Rate,
• *Then* `banalServiceRepository.startSearchingForNewDevices` SHALL be invoked for `(Protocol.BLUETOOTH_LE, DeviceType.HEARTRATE)`.
• *Then* the tabs ("Verbunden", "Gekoppelt", "Bekannt") SHALL continue to display all device types (`DeviceType.ALL`) and all protocols (`Protocol.ALL`).
• *Then* the existing ANT+ HRM and BLE Power Meter SHALL remain visible in the "Gekoppelt" and "Bekannt" tabs.
• *Given* an active pairing scan for Bluetooth LE Heart Rate,
• *When* viewing the "Verbunden" (Available) tab,
• *Then* `SearchingHeader` SHALL display "Searching for Bluetooth LE Heart Rate devices" / "Suche nach Bluetooth LE Herzfrequenz Sensoren".
• *Then* newly discovered devices SHALL appear in the list under the header.
• *Given* active sensor discovery,
• *When* the screen is disposed or search stopped,
• *Then* `stopSearching()` SHALL reset `scanningProtocol` and `scanningDeviceType` to null and stop background scanning.

**Invariants**: Targeted background scanning, device discovery broadcast processing, and 100% full-suite unit test pass rate MUST NOT be broken.

---

### REQ-UI-292: Sensor Field Style Dynamic Grid Spacing Resolution [Implemented]
- **Tests**: `TST-UI-252`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Target**: `SensorFieldStyle.kt`
- **Implementation**: `SensorFieldStyle.kt`
- **Rationale**: Resolves dynamic inter-tile grid spacing based on corner radius, border thickness, and baseline to prevent card collisions (ATT-2624).
- **Description**: In `SensorFieldStyle.kt`, `resolveTileGridSpacing()` SHALL compute dynamic inter-tile grid spacing: guaranteeing `0.dp` for `CLASSIC_SEAMLESS` baseline (0 radius, <= 1.0.dp border), and proportionally expanding whitespace when borders are thick or corners are rounded to eliminate tile collisions.
- **Acceptance Criteria (Given-When-Then)**:
  • *Given* the `CLASSIC_SEAMLESS` preset with 0 radius and <= 1.0.dp border,
  • *When* `resolveTileGridSpacing()` is evaluated,
  • *Then* it returns `0.dp` spacing.
  • *Given* rounded corners or thick borders,
  • *When* `resolveTileGridSpacing()` is evaluated,
  • *Then* whitespace is proportionally expanded to eliminate visual clipping.

---

### REQ-UI-293: Format-Specific Import Tab Architecture with Uniform Multi-Document Source Triad (FIT, TCX, GPX) [Verified]
- **Tests**: `TST-UI-253`, `TST-UI-458`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `ImportBackupTabsScreen.kt`, `strings.xml` (all 9 locales)
- **Rationale**: The system SHALL structure the Import tab in `ImportBackupTabsScreen.kt` into three dedicated, format-specific cards (FIT, TCX, GPX) providing complete source parity via a uniform action triad with multi-document selection (ATT-2623, ATT-3315):
1. *Format-Based Card Partitioning (`ImportBackupTabsScreen.kt`)*:
• `ImportTabContent` SHALL render three distinct, cohesive cards for the supported file formats:
- FIT Card: Garmin, Wahoo, Hammerhead, and smartwatch binary activity files (`.fit`).
- TCX Card: Training Center XML activity files (`.tcx`).
- GPX Card: GPS Exchange Format tracks and activities (`.gpx`).
• The legacy generic "Workout Import" / "Legacy Recovery" card grouping TCX and GPX together SHALL be eliminated.
2. *Uniform Multi-Document Action Triad per Format*:
• Each of the three format cards SHALL provide an identical, intuitive action triad composed of three `OutlinedButton` options:
- Button 1 (Local File Picker):
- For FIT, TCX, and GPX: launches multi-document picker (`ActivityResultContracts.OpenMultipleDocuments()`), supporting single or batch file ingestion (`.fit`, `.tcx`, `.gpx`) from local storage or cloud storage providers mounted in Android Storage Access Framework (SAF), including Google Drive.
- Button 2 (Dropbox Scan):
- Displays clear format context (e.g. `import_fit_dropbox_button`, `import_tcx_dropbox_button`, `import_gpx_dropbox_button`).
- When Dropbox is connected (`isDropboxConnected == true`), opens pre-import cluster tuning sheet (`PreImportTuningBottomSheet`), confirming which dispatches `viewModel.bulkRecoverLegacyData(context, format)` with format `"fit"`, `"tcx"`, or `"gpx"`.
- When Dropbox is disconnected (`isDropboxConnected == false`), renders in deactivated styling, and clicking presents the Dropbox connection prompt dialog (`showDropboxDisconnectedDialog`).
- Button 3 (Google Drive Scan):
- Displays clear format context (e.g. `import_fit_gdrive_button`, `import_tcx_gdrive_button`, `import_gpx_gdrive_button`).
- When Google Drive is connected (`isGoogleDriveConnected == true`), opens pre-import cluster tuning sheet (`PreImportTuningBottomSheet`), confirming which dispatches `viewModel.bulkRecoverGoogleDriveData(context, format)` with format `"fit"`, `"tcx"`, or `"gpx"`.
- When Google Drive is disconnected (`isGoogleDriveConnected == false`), renders in deactivated styling, and clicking presents the Google Drive connection prompt dialog (`showGoogleDriveDisconnectedDialog`).
3. *Unified Batch Local Execution (`BackupRestoreViewModel.kt`)*:
• `BackupRestoreViewModel` SHALL provide `importWorkoutFiles(context, uris, format)` to ingest batches of selected URIs with progress feedback, duplicate skipping, error reporting, and post-import cluster reconciliation.
• Selecting local files SHALL present `PreImportTuningBottomSheet` before dispatching batch import.
4. *100% 9-Language Localization Parity*:
• All new format headers, descriptions, button labels, and diagnostic messages SHALL maintain 100% parity across all 9 application locales (EN, DE, ES, FR, IT, JA, NL, PL, PT).
5. *Preservation of Core Invariants*:
• Pre-import cluster parameter tuning sheet (`REQ-MIG-012`, `REQ-UI-150`) remains mandatory before scanning or local batch importing commences.
• Interactive post-import workout navigation (`REQ-MIG-032`) via `StateOverlaySection` remains unchanged.
• Unlinked cloud dialog alerts (`REQ-MIG-023`, `REQ-MIG-034`) remain strictly enforced.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement (`REQ-UI-293`) in Sprint 2026-41.1 (`ATT-2623`), amended in Sprint 2026-42.1 (`ATT-3315`). Extends `REQ-MIG-011`, `REQ-DAT-019`, `REQ-MIG-034`, and `REQ-MIG-035`.
2. *Historical Origin & Commit Trace*: Sprint 2026-41.1 (`ATT-2623`, commit `d5a9d217`), amended under `ATT-3315` in Sprint `2026-42.1`.
3. *Root Reason for Existing Formulation*: Evolutionary accretion: TCX and GPX were historically restricted to single-file `OpenDocument()` picker, preventing users from batch-importing externally copied archives via Android SAF.
4. *Preservation of Core Invariants*: Cloud authentication guards, cluster tuning sheet presentation, post-import navigation, and database deduplication remain 100% preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an athlete navigating to the Import tab,
• *When* the screen renders,
• *Then* `ImportTabContent` SHALL display three format cards: FIT, TCX, and GPX.
• *Given* any format card (FIT, TCX, GPX),
• *When* tapping the local file button,
• *Then* `OpenMultipleDocuments()` is launched, allowing selection of multiple files from local storage or cloud document providers (such as Google Drive).
• *Given* multiple files selected for import,
• *When* confirming cluster parameters in `PreImportTuningBottomSheet`,
• *Then* `viewModel.importWorkoutFiles(context, uris, format)` executes batch import with progress reporting and post-import reconciliation.
• *Given* an athlete tapping "Google Drive scannen (TCX)",
• *When* confirming cluster parameters in `PreImportTuningBottomSheet`,
• *Then* `viewModel.bulkRecoverGoogleDriveData(context, "tcx")` SHALL be dispatched.

**Invariants**: Pre-import cluster parameter tuning, cloud authentication checks, interactive post-import navigation, and multi-document picking MUST NOT be altered or regressed.

### REQ-UI-294: Workout File Picker MIME Filtering, Selectability, and Defensive Format-Extension Validation (FIT, TCX, GPX) [Verified]
- **Tests**: `TST-UI-254`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `ImportBackupTabsScreen.kt`, `strings.xml` (all 9 locales)
- **Rationale**: The system SHALL configure Storage Access Framework (SAF) document picker MIME arrays to guarantee file selectability and enforce defensive extension validation on selected files in `ImportBackupTabsScreen.kt` (ATT-2622, ATT-2740):
1. *Guaranteed Selectability MIME Arrays (`ImportBackupTabsScreen.kt`)*:
• `FIT_MIME_TYPES` SHALL contain `arrayOf("application/vnd.ant.fit", "application/fit", "application/octet-stream")`.
• `TCX_MIME_TYPES` SHALL contain `arrayOf("application/vnd.garmin.tcx+xml", "application/xml", "text/xml", "application/octet-stream")`.
• `GPX_MIME_TYPES` SHALL contain `arrayOf("application/gpx+xml", "application/xml", "text/xml", "application/octet-stream")`.
2. *Defensive Extension & Format Validation*:
• Upon URI selection, the application SHALL inspect the file's display name (`OpenableColumns.DISPLAY_NAME`) via `ImportFileValidator.resolveDisplayName`.
• If the display name contains an explicit file extension, it SHALL match the requested format (case-insensitive: `.fit` for FIT, `.tcx` for TCX, `.gpx` for GPX).
• If the selected file's extension conflicts with the requested format (e.g. user selects a `.pdf` or `.png`), the file SHALL be rejected before stream ingestion, displaying a clear localized error status (`invalid_workout_file_format`).
• If the display name does not specify an extension (e.g. content provider opaque ID), the file SHALL proceed to content/header validation in `LegacyImportEngine` without premature rejection.
3. *100% 9-Language Localization Parity*:
• String resource `invalid_workout_file_format` ("Ungültiges Dateiformat. Bitte wählen Sie eine %1$s-Datei aus." / "Invalid file format. Please select a %1$s file.") SHALL maintain 100% parity across all 9 application locales (EN, DE, ES, FR, IT, JA, NL, PL, PT).
4. *Preserved Invariants*:
• Multi-document selection for FIT (`OpenMultipleDocuments`) and single-document selection for TCX/GPX (`OpenDocument`) remain preserved.
• Full restore archive picker (`.attbackup`) and incremental DB import pickers remain untouched.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Refines Clause 1 of `REQ-UI-294` (*Workout File Picker MIME Filtering and Defensive Format-Extension Validation (FIT, TCX, GPX)*), targeting `FIT_MIME_TYPES`, `TCX_MIME_TYPES`, and `GPX_MIME_TYPES`.
2. *Historical Origin & Commit Trace*: Ticket `ATT-2622`, Sprint `2026-41.3`, commit `c75b8e97`.
3. *Root Reason for Existing Formulation*: In ATT-2622, `application/octet-stream` was included as a fallback because Android lacks native MIME mappings for `.fit` and `.tcx`. Removing it in ATT-2740 caused total breakage on physical devices where workout files were disabled. Product Management Option A explicitly retains `application/octet-stream` to ensure files are clickable.
4. *Preservation of Core Invariants*: Defensive post-selection file extension validation via `ImportFileValidator` (Clause 2), localized error toast `invalid_workout_file_format` across all 9 locales (Clause 3), multi-document selection for FIT, single-document selection for TCX/GPX, and full regression test pass rate remain strictly preserved.

**Acceptance Criteria (Given-When-Then) - Option A**:
• *Given* an athlete tapping "Lokale Datei wählen" on the FIT, TCX, or GPX card,
• *When* the system document picker opens and a valid workout file is chosen,
• *Then* the file is selectable, clickable, and the import workflow is launched.
• *Given* an athlete selecting an incompatible file (e.g. .pdf, .jpg, or wrong format),
• *When* the selection callback returns,
• *Then* the file SHALL be rejected by `ImportFileValidator` and a localized warning toast displayed.
• *Given* all 9 application locales,
• *When* running translation parity tests,
• *Then* `invalid_workout_file_format` SHALL exist and contain `%1$s` in all 9 `strings.xml` files.

**Invariants**: File parsing engines, duplicate detection, multi-document selection for FIT, and backward compatibility MUST NOT be broken.

### REQ-UI-295: Unified Scrollable Container Architecture, Full-Width Spatial Toggles Layout, and Viewport Slotting for Tracking Tab Configuration Mode (`ScreenMode.CONFIGURATION`) [Verified]
- **Tests**: `TST-UI-255`, `TST-UI-384`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `SensorGridScreen.kt`, `TrackingTabsScreen.kt`
- **Rationale**: The system SHALL isolate and configure `ScreenMode.CONFIGURATION` in `SensorGridScreen.kt` using a single unified vertically scrollable container that seamlessly slots all spatial configuration controls without viewport clipping, text truncation, or live tracking overlay collision (ATT-2620, ATT-2744):
1. *Unified Scrollable Configuration Container*:
• In `ScreenMode.CONFIGURATION`, the entire configuration body SHALL be enclosed in a single `Column` with `Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding()`.
• The sensor grid `Column` in `CONFIGURATION` mode SHALL NOT apply an inner nested `verticalScroll`, allowing all sensor field tiles, `RowAdder` buttons, and `ColAdder` buttons to layout naturally within the parent scrollable container.
2. *Full-Width Spatial Toggle Architecture & Text Truncation Elimination*:
• The Turn-by-Turn Navigation Hints toggle card (`showNavigationHints`) SHALL render at the top of the scrollable container with `Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)`.
• The 5 spatial toggles below the sensor grid (Map `showMap`, Elevation Profile `showElevationProfile`, Live Segments `showLiveSegments`, Live Climbs `showLiveClimbs`, and Lap Button `showLapButton`) SHALL render as a single vertical sequence of full-width `SpatialCockpitToggleCard`s enclosed in a `Column` with `Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)` and `verticalArrangement = Arrangement.spacedBy(6.dp)`.
• Side-by-side 2-column `Row` constructs for spatial toggles SHALL be completely eliminated, providing ~320 dp of usable horizontal text clearance and ensuring that all titles and status badge pills ("Aktiv" / "Ausgeblendet") display completely without ellipsis truncation across all 9 supported application locales.
• The arbitrary nested grey dock card (`Surface` with tonal elevation 2 dp enclosing Live Segments, Live Climbs, and Lap Button) SHALL be eliminated, harmonizing all spatial feature toggles into a unified visual style.
3. *Exclusion of Live Map and Telemetry Overlays in Configuration Mode*:
• Interactive `ATrainingTrackerMap` and `ElevationProfile` SHALL ONLY render when `screenMode != ScreenMode.CONFIGURATION`.
• In `ScreenMode.CONFIGURATION`, embedded Map and Elevation Profile SHALL be represented exclusively by their respective `SpatialCockpitToggleCard`s.
4. *Bottom Sheet Scaffold and Floating Lap Button Isolation*:
• In `ScreenMode.CONFIGURATION`, `BottomSheetScaffold` SHALL set `sheetPeekHeight = 0.dp`, `sheetSwipeEnabled = false`, and render empty `sheetContent`, preventing `LiveClimbSheet` or `LiveSegmentSheet` from occluding the configuration canvas.
• In `TrackingTabsScreen.kt`, the floating `LapButton` (`+ Runde`) at `Alignment.BottomCenter` SHALL be suppressed whenever `screenMode == ScreenMode.CONFIGURATION` (`shouldShowLapButton = currentViewInfo?.showLapButton == true && screenMode != ScreenMode.CONFIGURATION`), preventing it from floating over and occluding lower configuration toggles.
5. *Preserved Invariants*:
• `ScreenMode.TRACKING` and `ScreenMode.PREVIEW` layouts SHALL remain 100% preserved with an unscrollable outer column, independently scrollable sensor grid, and expanded weighted map (`Modifier.weight(1f)`).
• Runtime gating (`state.showMap`, `state.showElevationProfile`, etc.) and SQLite tab configuration persistence MUST remain 100% operational.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Refines `REQ-UI-295` (*Unified Scrollable Container Architecture and Viewport Insets Slotting for Tracking Tab Configuration Mode (`ScreenMode.CONFIGURATION`)*) and `REQ-UI-275` Clause 5 (*Spatial WYSIWYG Cockpit Editor*).
2. *Historical Origin & Commit Trace*: Sprint 2026-41.1 commit `1607a7ec` (`ATT-2360`) and Sprint 2026-41.3 commit `e5033b5c` (`ATT-2620`).
3. *Root Reason for Existing Formulation*: Live Segments, Live Climbs, and Lap Button were conceptualized as bottom-dock items, prompting the author to group them into an elevated grey `Surface` container and pair toggles into 2-column rows to save vertical space. However, on portrait smartphone viewports (e.g. Pixel 10), 2-column rows restrict title clearance to ~39 dp against an 85 dp status pill, causing severe ellipsis truncation ("K...", "H...", "Live Se...", "Live-An..."), while the grey container created arbitrary visual hierarchy, and the floating `LapButton` was not suppressed in edit mode.
4. *Preservation of Core Invariants*: Unified scrollable container (`verticalScroll(rememberScrollState()).navigationBarsPadding()`), live map/elevation exclusion in config mode, runtime toggle gating, and 100% full-suite test pass rate remain strictly preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an athlete in `ScreenMode.CONFIGURATION`,
• *When* inspecting the spatial toggle cards below the sensor grid,
• *Then* Map, Elevation Profile, Live Segments, Live Climbs, and Lap Button SHALL render as full-width cards in a single column without 2-column side-by-side rows and without an arbitrary nested grey dock card.
• *Given* an athlete viewing tracking tab configuration on a portrait mobile screen across any of the 9 supported languages,
• *When* the toggle cards render,
• *Then* the full labels ("Karte", "Höhenprofil", "Live-Segmente", "Live-Anstiege", "Runden-Knopf", etc.) SHALL be fully readable without ellipsis truncation.
• *Given* an athlete in `ScreenMode.CONFIGURATION` on a tab with the lap button enabled,
• *When* scrolling through configuration controls,
• *Then* the floating `LapButton` (`+ Runde`) SHALL NOT be displayed.
• *Given* an athlete in `ScreenMode.TRACKING`,
• *When* tracking is active,
• *Then* the floating `LapButton` SHALL display at `Alignment.BottomCenter` whenever enabled for that tab.

**Invariants**: TRACKING and PREVIEW mode stability, SQLite persistence, and 100% unit test pass rate MUST NOT be broken.

### REQ-UI-296: Modernized Contextual ANT+ System Service Guidance & Cold-Start Dialog Elimination [Verified]
- **Tests**: `TST-UI-256`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `MainActivityWithNavigation.kt`, `DevicesTabbedScreen.kt`, `PairingProtocolBottomSheet.kt`, `AntServicesStatusSheet.kt`, `strings.xml`
- **Rationale**: The system SHALL eliminate automatic ANT+ service checks and dialog popups on application cold start, moving all ANT+ hardware readiness guidance and service installation triggers into contextual Material 3 Compose cards and bottom sheets (`AntServicesStatusSheet.kt`, `AntServicesStatusCard.kt`) (ATT-2513):
1. *Elimination of Startup Check (`MainActivityWithNavigation.kt`)*:
• `MainActivityWithNavigation.onCreate()` SHALL NOT invoke `checkANTInstallation()` or present any installation dialogs on app startup.
• The app SHALL launch directly into the athlete's primary workflow (GPS tracking, route selection, Bluetooth LE sensors) without modal interruption.
2. *Contextual Guidance in Sensor Management (`DevicesTabbedScreen.kt`)*:
• When the athlete views the ANT+ sensors tab (`protocol == Protocol.ANT_PLUS`), the screen SHALL check ANT+ service readiness via `BANALService.areAllANTServicesInstalled(context)`.
• If one or more services are missing, the screen SHALL render an inline non-blocking `AntServicesStatusCard` explaining the hardware requirement (external USB-OTG adapter + ANT+ USB stick) and offering a one-tap trigger to open the detailed diagnostic sheet.
• Tapping the "ANT+ Installation prüfen" item in the top overflow menu SHALL open `AntServicesStatusSheet`.
3. *Contextual Pairing Integration (`PairingProtocolBottomSheet.kt`)*:
• When an athlete taps the ANT+ protocol option in `PairingProtocolBottomSheet`, if `!BANALService.areAllANTServicesInstalled(context)`, the system SHALL present `AntServicesStatusSheet` to clarify hardware and driver dependencies before initiating scanning.
4. *Modernized Service Status & Google Play Installation Flow (`AntServicesStatusSheet.kt`)*:
• `AntServicesStatusSheet` SHALL be built as a Material 3 `AppModalBottomSheet` displaying:
a. Clear educational guidance noting that modern smartphones require an external USB-OTG adapter and ANT+ USB stick for ANT+ communication.
b. Service readiness list showing real-time installation status: ANT+ Plugins Service (`com.dsi.ant.plugins.antplus`), ANT Radio Service (`com.dsi.ant.service.socket`), and ANT USB Service (`com.dsi.ant.usbservice`, conditionally visible if `BANALService.hasUsbHostFeature(context)`).
c. Individual action buttons per missing service that launch Google Play Store (`market://details?id=...` with fallback to `https://play.google.com/store/apps/details?id=...`).
d. Reassuring note explaining that standard Bluetooth LE sensors require zero additional hardware or services.
e. Dismiss / Done button.
5. *100% 9-Language Localization Parity*:
• All newly introduced strings (`ant_status_sheet_title`, `ant_status_card_desc`, `ant_status_usb_dongle_note`, `ant_status_ble_alternative_note`, `ant_service_installed`, `ant_service_not_installed`, `ant_service_install_button`) SHALL maintain 100% parity across EN, DE, ES, FR, IT, JA, NL, PL, PT.
6. *Preserved Invariants*:
• Existing ANT+ dongle pairing and data decoding in `BANALService` MUST remain 100% operational.
• Google Play store intent launches MUST strictly require direct athlete interaction.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Replaces legacy `InstallANTShitDialog.java` (commit `05.01.17`) and refines `REQ-UI-278` (*Pairing Triggers Relocation*).
2. *Historical Origin & Commit Trace*: Sprint 2017 legacy code authored for phones with built-in ANT+ chips.
3. *Root Reason for Existing Formulation*: Onboarding in 2017 expected built-in ANT+ radios. On modern phones without ANT+ radios, popping up driver install prompts on first app launch confuses athletes who just want GPS or BLE.
4. *Preservation of Core Invariants*: Full ANT+ sensor communication via USB dongles, diagnostic checks, and Google Play installation flows remain 100% intact.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an athlete installing the app cleanly on a device without ANT+ services,
• *When* the app launches for the first time,
• *Then* `MainActivityWithNavigation` SHALL NOT show any ANT+ dialog or modal prompt.
• *Given* an athlete navigating to Sensors > ANT+ tab on a device without ANT+ services,
• *When* the tab renders,
• *Then* `AntServicesStatusCard` SHALL be visible, explaining the USB-OTG requirement.
• *Given* an athlete tapping "ANT+ Installation prüfen" or tapping the action on `AntServicesStatusCard`,
• *When* tapped,
• *Then* `AntServicesStatusSheet` SHALL open, displaying individual service status and installation buttons.
• *Given* an athlete in `PairingProtocolBottomSheet` tapping ANT+ when services are missing,
• *When* tapped,
• *Then* `AntServicesStatusSheet` SHALL open, preventing silent scan failure.
• *Given* all 9 supported application locales,
• *When* running translation parity tests,
• *Then* all new ANT status strings SHALL exist and be non-empty across all 9 `strings.xml` files.

**Invariants**: BANALService ANT+ pipelines, Google Play policy compliance, zero startup popups, and 100% test pass rate MUST NOT be broken.

### REQ-UI-297: Distance-Weighted Elevation Profile Gaussian Smoothing & Continuous Slope Grade Evaluation [Verified]
- **Tests**: `TST-UI-257`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `ElevationSmoothingMath.kt`, `ElevationProfile.kt`, `TuningPreferencesDataStore.kt`, `AftermathAnalysisSection.kt`
- **Rationale**: The system SHALL smooth elevation profile coordinate streams and evaluate terrain slope gradient using a physically-grounded, distance-weighted Gaussian kernel filter with exact boundary renormalization, eliminating discrete sensor quantization staircase artifacts (ATT-2512, ATT-2746):
1. *Distance-Weighted Gaussian Kernel Engine (`ElevationSmoothingMath.kt`)*:
• The system SHALL encapsulate elevation profile smoothing in a dedicated pure mathematical engine `ElevationSmoothingMath`.
• For each point $i$ with monotonic cumulative distance $d_i$ and raw altitude $a_i$, neighboring points $j$ within a spatial cutoff radius $3\sigma = 63.0\text{ m}$ ($\sigma = 21.0\text{ m}$ default) SHALL be weighted by spatial distance: $w_{ij} = \exp\left(-\frac{(d_j - d_i)^2}{2\sigma^2}\right)$.
• Parameter `cutoff` in `smoothAltitudes` SHALL default dynamically to `3.0 * sigma`.
• The smoothed altitude $\hat{a}_i$ SHALL be strictly normalized by the sum of actual neighboring weights: $\hat{a}_i = \frac{\sum_{j} w_{ij} a_j}{\sum_{j} w_{ij}}$.
• Boundary conditions at track start ($d \approx 0$) and track end ($d \approx D_{total}$) SHALL apply dynamic renormalization over the truncated spatial window without zero-padding or artificial boundary sagging/peaking.
2. *Linear-Time Bounded Complexity ($O(N)$)*:
• Smoothing over monotonically ordered distance points SHALL utilize a sliding two-pointer window maintaining $[d_i - 3\sigma, d_i + 3\sigma]$, achieving strictly linear $O(N)$ computational complexity and sub-2ms latency on dense tracks ($N > 10,000$).
• Array operations SHALL execute via primitive `DoubleArray` with zero object allocations in the inner convolution loop.
3. *Continuous Slope Grade Evaluation (`ElevationProfile.kt`)*:
• Local terrain gradient between adjacent points SHALL evaluate from smoothed altitudes: $\text{grade}_i = \frac{\hat{a}_{i+1} - \hat{a}_i}{d_{i+1} - d_i} \times 100$.
• Continuous grade evaluation SHALL eliminate alternating 0% flat plateaus and 15%+ artificial step jumps on quantized sensor streams (e.g. 0.2m–0.4m steps), rendering natural, smooth grade zone coloration (`Zone1`..`Zone5`).
4. *Configurable Spatial Bandwidth in Tuning Preferences (`TuningPreferencesDataStore.kt`, `AdvancedTuningDialog.kt`)*:
• The system SHALL declare `elevationSmoothingSigmaMeters` in `TuningPreferencesDataStore` with default `21.0f` and safety bounds $[10.0\text{f}, 50.0\text{f}]$.
• `AftermathAnalysisSection` inside `AdvancedTuningDialog` SHALL render an adjustable slider (`TuningSliderItem`) allowing the athlete to customize $\sigma$ between 10m and 50m in 1m step increments.
• Tapping "Reset to Factory Defaults" SHALL restore `elevationSmoothingSigmaMeters` to `21.0f`.
• All new string resources (`tuning_elevation_smoothing_sigma_title`, `tuning_elevation_smoothing_sigma_desc`) SHALL maintain 100% parity across all 9 supported locales.
5. *Dynamic Profile Reactivity (`ElevationProfile.kt`)*:
• `ElevationProfile` SHALL accept an optional parameter `smoothingSigma: Double? = null`. When null, it reactively observes `tuningConfig.elevationSmoothingSigmaMeters.toDouble()` and dynamically re-evaluates smoothed profile geometry whenever the preference changes.
6. *Preservation of Invariants*:
• True topographic summits, passes, and valleys SHALL be preserved without over-smoothing.
• Workout summary ascent/descent calculations, database persistence (`Segments.db`, `WorkoutSummaries.db`), Strava sync, and telemetry graphs MUST NOT be modified.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Refines `REQ-UI-297` (*Distance-Weighted Elevation Profile Gaussian Smoothing & Continuous Slope Grade Evaluation*).
2. *Historical Origin & Commit Trace*: Ticket `ATT-2512`, commit `7630148d`, sprint `2026-41.3`, refined in `ATT-2746`, sprint `2026-41.4`, Epic `ATT-2582` (*[Epic] Segments: Live Tracking, Exploration & Route Integration*).
3. *Root Reason for Existing Formulation*: Commit `7630148d` established $\sigma = 35.0\,\text{m}$ ($3\sigma = 105.0\,\text{m}$) to eliminate 0.2m–0.4m sensor quantization plateaus and derivative zebra artifacts. While mathematically robust, $\sigma = 35.0\,\text{m}$ acts slightly too aggressively on punchy climbs and short terrain features, damping authentic terrain inflections.
4. *Preservation of Core Invariants*: Reducing default bandwidth to $\sigma = 21.0\,\text{m}$ ($3\sigma = 63.0\,\text{m}$) continues to span multiple GPS/barometer points (1 Hz, 2–8 m spacing on climbs), reliably preventing quantization staircasing while sharpening short-pitch gradient response. Exposing $\sigma$ as an adjustable parameter (10.0m–50.0m) empowers the athlete to tune the filter to their terrain preference. System invariants, zero-allocation inner loops, and continuous grade evaluation are 100% preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an elevation stream with discrete vertical quantization (0.2m–0.4m steps),
• *When* `ElevationProfile` renders with default settings ($\sigma = 21.0\,\text{m}$),
• *Then* the elevation curve SHALL be smooth and continuous, sharpening short-pitch gradient response compared to $\sigma = 35.0\,\text{m}$ without staircasing.
• *Given* the athlete in `AdvancedTuningDialog` under Aftermath & Analysis,
• *When* adjusting the elevation smoothing slider,
• *Then* `elevationSmoothingSigmaMeters` SHALL update in preferences within $[10.0\text{m}, 50.0\text{m}]$, and `ElevationProfile` SHALL dynamically re-render using the updated $\sigma$.
• *Given* points at track boundaries ($d=0$ and $d=D_{total}$),
• *When* smoothing is applied,
• *Then* boundary altitudes SHALL renormalize cleanly without sagging or peaking.
• *Given* all 9 supported application locales,
• *When* running localization parity tests,
• *Then* all new tuning string resources SHALL exist and be non-empty.

**Invariants**: SQLite schemas, raw stream data, total ascent metrics, and 100% unit test pass rate MUST NOT be broken.

---

## Change History

| Version | Date | Author | Ticket | Summary of Changes |
| :--- | :--- | :--- | :--- | :--- |
| `1.0.0` | 2026-11-18 | AI Agent (`ATT-8206`) | `ATT-8206` | Split oversized shard into compliant sub-shard (<= 4,000 lines, `REQ-PRO-093.4`, `REQ-PRO-127.3`). |
