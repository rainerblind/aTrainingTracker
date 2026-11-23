# Living Test Specification: UI Components & Design System (Part 3 of 3)

**Document ID**: `DOC-TST-UI-P3`  
**Version**: 1.0.41  
**Status**: Reviewed  
**Approver**: Pending PO Sign-Off  

**Domain**: `ui_design_system_part3`  
**Prefixes**: `UI`, `UNT`  
**Scope**: Jetpack Compose design system, UI components, typography, dialogs, gestures, and layout contracts.  
**Total Tests**: 98

---

### TST-UI-444: **Android Adaptive Icon Architecture, Safe-Zone Bounds & Brand Preservation Verification Suite** [Verified]
- **Requirement(s)**: `REQ-UI-444`
- **Component**: `AppIconAdaptiveContractTest.kt`, `AndroidManifest.xml`, `res/mipmap-anydpi-v26/`, `res/drawable/`
- **Jira**: `ATT-1482`
- **Test Description**:
  1. *Adaptive Resource & Manifest Contract (`TST-UI-444.1`)*:
  • Verify `AndroidManifest.xml` specifies `android:icon="@mipmap/ic_launcher"` and `android:roundIcon="@mipmap/ic_launcher_round"`.
  • Verify `res/mipmap-anydpi-v26/ic_launcher.xml` and `ic_launcher_round.xml` exist and correctly reference `@drawable/ic_launcher_background`, `@drawable/ic_launcher_foreground`, and `@drawable/ic_launcher_monochrome`.
  2. *Safe-Zone Geometry & Vector Bounds Audit (`TST-UI-444.2`)*:
  • Parse vector path coordinate extrema in `ic_launcher_foreground.xml` and `ic_launcher_monochrome.xml`, supporting lines (`L`/`l`), arcs (`A`/`a`), and cubic/quadratic Béziers (`C`/`c`/`S`/`s`/`Q`/`q`).
  • Verify all vector elements lie strictly within the inner circular safe zone centered at `(54, 54)` on the `108x108` canvas with $r \le 34.5\text{dp}$ (and $r \ge 33.0\text{dp}$ to prevent excessive margin).
  3. *Authentic Brand Color & Geometry Fidelity Audit (`TST-UI-444.3`)*:
  • Audit color tokens in `ic_launcher_foreground.xml` to verify exact matching of Cervélo P3C frame (`#FF0000`), carbon wheels/saddle (`#000000`), stopwatch ring/stems (`#FFC700`), dial (`#F0F0F0`), pushers (`#000090`, `#FF0000`, `#009000`), and athletes (`#0000FF`).
  • Verify pusher button caps overlap stem necks with zero white gap and round stroke caps on athlete curves.
  4. *In-App Caller Parity & Master Asset Integrity (`TST-UI-444.4`)*:
  • Verify `R.drawable.logo` remains intact and unaffected for existing notification and protocol callers.
  • Verify `app/src/main/res/drawable/logo_512.png` and `images/logo_512.png` match `drawable-xxxhdpi/logo_512.png` checksum (`sha256: f84c6251...`), preserving full-bleed in-app drawer rendering.
  • Verify separate Play Store master asset `images/play_store_icon_512.png` exists with padded safe-zone geometry.
  • Verify `verify_requirement_governance.py` exits with code 0.

### TST-UI-445: **Supporter Badges: Custom Launcher Icons, Cockpit Status Emblem, Navigation Drawer & Share Watermark Verification Suite** [Verified]
- **Requirement(s)**: `REQ-UI-445`
- **Component**: `LauncherIconManagerTest.kt`, `SupporterBadgeContractTest.kt`, `SensorStatusTest.kt`, `AppNavigationDrawerTest.kt`, `SupporterComponentsTest.kt`, `ShareUtilsTest.kt`, `TranslationParityTest.kt`
- **Jira**: `ATT-1406`, `ATT-5372`, `ATT-5397`, `ATT-5496`
- **Test Description**:
  1. *Variant C Adaptive Launcher Icon Safe-Zone & Brand Color Contract Test (`TST-UI-445.1` / `ATT-5496`)*:
  • Parse all 6 foreground vector drawables (`ic_launcher_foreground.xml` and tier variants).
  • Verify maximum radial distance from canvas center `(54, 54)` is $33.0 \le r \le 34.5\text{dp}$ for all path coordinates, and verify cubic Bézier curves (`C`/`c`) are present for smooth cyclist back/hip geometry.
  • Verify exact color token preservation: Cervélo P3C red frame (`#FF0000`), carbon wheels/saddle (`#000000`), golden bezel (`#FFC700`), pushers (`#000090`, `#FF0000`, `#009000`), and blue athletes (`#0000FF`).
  • Verify that all 28 base logo paths are identical between standard and all 5 tier foreground drawables, with Variant C emblems positioned on the left and right on `#FFFFFF` ground flanking the centered text stadium.
  2. *Activity-Alias Manifest & Deferred Automatic Tier Synchronization Test (`TST-UI-445.2`)*:
  • Verify `AndroidManifest.xml` declares 6 `<activity-alias>` entries targeting `MainActivityWithNavigation`, with default alias enabled.
  • Verify `LauncherIconManager` automatically synchronizes with `ProFeatureManager.effectiveTier` via `LauncherIconVariant.fromTier(effectiveTier)` and defers alias changes with `PackageManager.DONT_KILL_APP` during active foreground or tracking sessions (`isTracking == true`), executing component state transitions on `ProcessLifecycleOwner` `onStop`.
  3. *Live Cockpit & Supporter Screen Full-Color Emblem Verification (`TST-UI-445.3` / `ATT-5372`, `ATT-5397`, `ATT-5496`)*:
  • Verify `SensorStatus.kt` renders the right-aligned 24dp emblem symmetrically balancing the 24dp menu button in Portrait and Landscape without opening an icon picker dialog.
  • Verify `SupporterPlanCard` renders the corresponding full-color tier emblem at `44.dp` with `Color.Unspecified`, and `ActiveTierBanner` and `PioneerStatusBanner` render the full-color tier emblem at `36.dp` with `Color.Unspecified`.
  • Verify 24dp vector drawables `ic_supporter_emblem_bronze.xml` (B5 Rosette), `ic_supporter_emblem_silver.xml` (S4-A Single-Rim Coins), and `ic_supporter_emblem_gold.xml` (G6 Gold Bar+Coin) are valid XML vectors with width/height `24dp`.
  4. *Navigation Drawer Header & Share Watermark Contract Test (`TST-UI-445.4`)*:
  • Verify `AppNavigationDrawer.DrawerHeader` preserves the original single-line header layout without a text pill below the title and renders the active custom Cervélo supporter icon at the original full 46dp visual size (`scale = 1.81f`) matching `effectiveTier` directly.
  • Verify `ShareUtils.drawFooter` renders the Cervélo P3C icon, "Tracked with aTrainingTracker", and supporter tier pill.
  5. *9-Locale Localization Parity Test (`TST-LOC-001` / `TST-UI-445.5` / `ATT-5496`)*:
  • Verify 100% parity across all 9 supported locales (`values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`) for `supporter_tier_silver`, `drawer_tier_silver`, `launcher_icon_silver`, `supporter_tier_gold`, `drawer_tier_gold`, `launcher_icon_gold`, and `garage_detector_dialog_message_gold` with zero missing keys or specifier mismatches.

### TST-UI-446: **Universal Stacked Workout Details Marginal Histograms & Widened Strip Verification Suite** [Verified]
- **Requirement(s)**: `REQ-UI-446`
- **Component**: `TelemetryMetricGraphTest.kt`, `TelemetryMetricGraphLandscapeContractTest.kt`, `ElevationProfileTest.kt`, `ElevationProfileGradeColorTest.kt`, `MapDetailLayoutTest.kt`, `TranslationParityTest.kt`
- **Jira**: `ATT-5370`, `ATT-5486`, `ATT-5504`
- **Test Description**:
  1. *Universal Marginal Histogram & Widened Portrait Margin Contract Test (`TST-UI-446.1`)*:
  • Verify `TelemetryMetricGraph` and `ElevationProfile` allocate `endPaddingPx = 52.dp` in portrait mode when aligned with stacked charts, yielding ~44dp bar width within the 52dp margin.
  • Verify universal marginal histogram rendering across all stacked telemetry types: `ElevationProfile`, `SPEED`, `PACE`, `HEART_RATE`, `POWER`, and `W_PRIME_BAL`.
  2. *Series-Color Rendering, Track Continuity & Domain Clamping Contract Test (`TST-UI-446.2`)*:
  • Verify non-zoned charts (`Speed/Pace`, `W'bal`, `Elevation`) render marginal histograms in series color without physiological zone segmentation, specifically asserting that `ElevationProfile` in portrait and landscape `AlignedMarginalZoneDistribution` uses fixed `TTColor.Zone1` green regardless of the initial segment slope gradient.
  • Verify unconditional `alpha = 0.15f` background container tracks across `[dataMin, dataMax]` per Rule 69.
  • Verify strict domain-boundary filtering and clamping (`bin.rangeMax <= dataMin || bin.rangeMin >= dataMax`) preventing out-of-domain bin stacking at `y = 0` per Rule 70.
  • Verify 5/7-zone coloring and threshold alignment are preserved for `HEART_RATE` and `POWER`.
  3. *Landscape 72%/28% Multi-Chart Alignment & Elevation Parity Contract Test (`TST-UI-446.3`)*:
  • Verify all stacked charts in landscape orientation share identical 72%/28% dual-pane horizontal bounds (`chartCanvas` at 0.72f, marginal pane at 0.28f), guaranteeing 1:1 horizontal plot-width and scrubbing cursor alignment per Rule 64.
  • Verify `ElevationProfile(alignWithTelemetryCharts = false)` default preserves clean standalone layout without marginal histogram or extra padding for non-stacked callers (`SensorGridScreen`, `LiveClimbSheet`, `ClimbDetailSheet`, `RouteItem`, `SegmentItem`).
  • Verify `MapDetailLayout` defaults `showElevationHistogram = (workoutData != null)`, suppressing altitude histograms for non-workout callers (`workoutData == null`) while enabling it for Workout Details (`workoutData != null`) (`ATT-5504`).
  • Verify `WorkoutSummary` passes `alignWithTelemetryCharts = false` on list card `ElevationProfile` (`ATT-5504`).
  4. *9-Language Localization & Targeted Test Suite (`TST-UI-446.4`)*:
  • Run `TranslationParityTest` verifying 100% parity across all 9 locales.
  • Execute targeted test suite (`--tests "com.atrainingtracker.trainingtracker.ui.map.*"`, `aftermath.zones.*`, `ThemeColorCentralizationAuditTest`, `build.*`) with 100% pass rate.

### TST-UI-447: **Workout Share Mode Selection & Photo Track Overlay Verification Suite** [Verified]
- **Requirement(s)**: `REQ-UI-447`
- **Component**: `PhotoTrackOverlayComposerTest.kt`, `WorkoutShareBottomSheetContractTest.kt`, `ShareUtilsTest.kt`, `TranslationParityTest.kt`, `WorkoutHeader.kt`, `WorkoutSummary.kt`, `WorkoutDetailTabbedContent.kt`, `MapDetailLayout.kt`
- **Jira**: `ATT-30`, `ATT-5479`, `ATT-5472`, `ATT-5656`, `ATT-5809`
- **Test Description**:
  1. *Equirectangular Track Projection, Portrait Bounds & Silhouette Geometry (`TST-UI-447.1`)*:
  • Verify `PhotoTrackOverlayComposer` scales GPS polyline points via cosine-latitude projection ($\Delta x = \Delta\lambda \cdot \cos(\phi_{\text{mean}})$, $\Delta y = -\Delta\phi$) preserving true aspect ratio.
  • Verify `PhotoTrackOverlayComposer` filters out invalid or Null-Island coordinates (`!isValidGpsCoordinate(lat, lng)`) prior to silhouette and bounding box computation.
  • Verify portrait photo track bounding box is constrained to upper-right quadrant ($\le 22\%$ photo height).
  2. *Metrics Formatting, Typography Sizing & Compact Anchoring (`TST-UI-447.2`)*:
  • Verify bottom gradient scrim renders with dark translucent gradient (alpha $0.0 \rightarrow 0.85$).
  • Verify workout title (~48f), date (~26f), and metrics (~36f) render in enlarged white typography satisfying WCAG AAA contrast ratio ($\ge 7.0:1$).
  • Verify metrics block anchors compactly directly above `footerTop` (`footerTop - 24f * scaleRef`), eliminating portrait vertical dead space.
  3. *Supporter Emblem Only & Footer Text Auto-Scaling Contract (`TST-UI-447.3`)*:
  • Verify `ShareUtils.drawFooter` renders standalone vector emblem (`ic_supporter_emblem_*`) without wide text pills when `effectiveTier.isPro`.
  • Verify footer watermark text auto-scales to prevent collision or truncation across narrow widths and 9 supported locales.
  • Verify tier-matched launcher icon rendering on left.
  4. *Uniform WorkoutHeader Share Action & Rule 73 Stacked Action Buttons Contract (`TST-UI-447.4`, `WorkoutShareBottomSheetContractTest.kt`)*:
  • Verify `WorkoutHeader.kt` defines `onShareWorkout: (() -> Unit)? = null` parameter and renders 32dp `IconButton` with `Icons.Default.Share`, `MaterialTheme.colorScheme.primary` tint, and `contentDescription = stringResource(R.string.share)` in both portrait and landscape adjacent to `onEditWorkout`.
  • Verify `WorkoutHeader.kt` portrait layout expands action button spacer to ~104dp when both edit and share actions are present (`hasEdit && hasShare`) to prevent workout title truncation.
  • Verify `WorkoutSummary.kt` wires `onShareWorkout` callback to launch `WorkoutShareBottomSheet` (Option A routes to `onMapClick()`).
  • Verify `WorkoutDetailTabbedContent.kt` passes persistent `WorkoutHeader` composable into `MapDetailLayout` `header` parameter for Option A snapshot rendering and wires header `onShareWorkout`.
  • Verify `MapDetailLayout.kt` suppresses floating share button when `workoutData != null` in multi-tab details, while preserving it for non-workout callers (`workoutData == null`).
  • Verify `WorkoutShareBottomSheet` renders stacked full-width action buttons per Rule 73 (primary "Teilen" above secondary "Foto auswählen").
  5. *9-Language Localization & Targeted Test Suite (`TST-UI-447.5`)*:
  • Verify 100% parity across all 9 locales via `TranslationParityTest` for all share mode strings.
  • Run targeted test suite (`--tests "com.atrainingtracker.trainingtracker.helpers.*"`, `ui.map.*`, `ui.components.workoutheader.*`, `ui.aftermath.*`, `ThemeColorCentralizationAuditTest`) with 100% pass rate.
  6. *3-Tier Photo Selection Cascade Contract Test (`TST-UI-447.6`, `WorkoutShareBottomSheetContractTest.kt`)*:
  • Verify `WorkoutShareBottomSheet` displays Tier 1 (Attached Photos grid) when `attachedPhotos.isNotEmpty()`.
  • Verify expanding to Tier 2 (Time-Window Photos) queries photos from `WorkoutPhotoManager.queryWorkoutTimePhotos`.
  • Verify fallback to Tier 3 (System Picker `PickVisualMedia`) when both tiers are empty or when user taps "Alle Fotos anzeigen".
  • Verify tapping "Foto auswählen" in preview mode returns to the 3-tier cascade.
  • Verify Rule 73 stacked buttons across all cascade states.

### TST-UI-448: **Workout Photos Strip, Full-Screen Preview Bottom Sheet with ATT-30 Overlay Share, Map Pin Markers, and Null-Island Coordinate Rejection Verification Suite** [Verified]
- **Requirement(s)**: `REQ-UI-448`
- **Component**: `WorkoutDetails.kt`, `WorkoutDetailTabbedContent.kt`, `TrackOnMapScreen.kt`, `ATrainingTrackerMap.kt`, `WorkoutPhotoViewerSheet.kt`, `WorkoutPhotoPreviewBottomSheet.kt`, `WorkoutPhotoStrip.kt`, `WorkoutSummary.kt`, `WorkoutShareBottomSheet.kt`, `WorkoutPhotoManager.kt`, `WorkoutRepository.kt`, `MapBehaviors.kt`
- **Jira**: `ATT-43`, `ATT-5479`, `ATT-5473`, `ATT-5811`
- **Test Description**:
  1. *Workout Photos Strip & Empty State Contract Test (`TST-UI-448.1`, `WorkoutPhotoUiContractTest.kt`)*:
  • Verify horizontal photo strip renders when photos are attached, displaying thumbnail cards with `RoundedCornerShape(8.dp)`.
  • Verify neutral `onSurfaceVariant` styling for camera header icon and "Add Photos" header icon button (no primary electric blue).
  • Verify "Add Photos" action is rendered in the section header row (`Icons.Default.AddPhotoAlternate`) when `showAddButton` is true.
  • Verify `LazyRow` renders only when `photos.isNotEmpty()`, starting with photo thumbnails at index 0 without an oversized empty tile.
  • Verify compact single-line header row renders when `photos.isEmpty()` and `showAddButton` is true.
  • Verify `HorizontalDivider` preceding `WorkoutPhotoStrip` in `WorkoutSummary.kt`.
  • Verify photo picker launcher contract invocation on add button click.
  2. *Workout Photo Viewer Bottom Sheet & ATT-30 Share Handoff (`TST-UI-448.2`, `WorkoutPhotoViewerSheetTest.kt`)*:
  • Verify clicking thumbnail displays `WorkoutPhotoViewerSheet` / `WorkoutPhotoPreviewBottomSheet` via `AppModalBottomSheet`.
  • Verify full image display, formatted capture timestamp, and caption rendering.
  • Verify stacked full-width action buttons per Rule 73 (primary share overlay button above secondary outlined delete button with 8dp spacing).
  • Verify "Delete Photo" action presents confirmation dialog and triggers photo deletion callback.
  • Verify "Share with Track Overlay" CTA launches `WorkoutShareBottomSheet` pre-populated with the photo Uri.
  3. *Map Photo Pin Markers & Strict Coordinate Validation Contract Test (`TST-UI-448.3`, `WorkoutPhotoUiContractTest.kt`, `WorkoutRepositoryMarkerTest.kt`)*:
  • Verify pure coordinate validator `GeoUtils.isValidGpsCoordinate` rejects `(0.0, 0.0)` (`abs(lat) < 1e-5 && abs(lng) < 1e-5`), out-of-range latitudes/longitudes, and non-finite values (`NaN`, `Infinity`).
  • Verify `WorkoutRepository.getWorkoutMarkers()` excludes photo records with `(0.0, 0.0)` or invalid coordinates from producing `LocationMarker` objects.
  • Verify `MapBoundsController` (`MapBehaviors.kt`) excludes invalid coordinates from `LatLngBounds.Builder`, preventing continental zoom level inflation.
  4. *EXIF Null-Island Rejection & Database Self-Healing Contract Test (`TST-UI-448.4`, `WorkoutPhotoManagerTest.kt`)*:
  • Verify `WorkoutPhotoManager.extractExifMetadata` treats `(0.0, 0.0)` as `null`, allowing trackpoint fallback interpolation to run.
  • Verify existing `(0.0, 0.0)` records in `workout_photos` are self-healed upon loading by re-interpolating against `WorkoutSamples.db` trackpoints using `taken_at_ms`.
  5. *9-Language Localization & Format Specifier Parity Test (`TST-UI-448.5`, `TranslationParityTest.kt`)*:
  • Verify 100% parity across all 9 supported locales for all `workout_photos_*` string keys with zero missing entries and zero decorative inline emojis.
  6. *Clean-Room Regression Suite Execution (`TST-UI-448.6`)*:
  • Run targeted package tests (`com.atrainingtracker.trainingtracker.database.WorkoutPhotoManagerTest`, `ui.aftermath.*`, `ui.map.*`, `ThemeColorCentralizationAuditTest`, `build.*`) asserting 100% pass rate.

### TST-UI-449: **Interactive Map Lap Creation & Splitting in Workout Aftermath Verification Suite** [Verified]
- **Requirement(s)**: `REQ-UI-449`
- **Component**: `CreateLapOnMapSheet.kt`, `PathPreviewMap.kt`, `LapEditorMath.kt`, `WorkoutRepository.kt`, `WorkoutSamplesDatabaseManager.java`, `LapsDatabaseManager.java`, `TrackOnMapScreen.kt`, `MapDetailLayout.kt`, `WorkoutLaps.kt`
- **Jira**: `ATT-907`, `ATT-5480`
- **Test Description**:
  1. *Domain Math & Split Preview Contract Test (`TST-UI-449.1`, `LapEditorMathTest.kt`)*:
  • Verify `LapEditorMath.calculateSplitLapsPreview` computes duration, distance, and speed correctly for Lap $k$ and Lap $k+1$.
  • Verify boundary validation rejects splits at $s \le start_k$ or $s > end_k$ and prevents zero-duration/zero-distance phantom laps.
  2. *Database Transactional Splitting Contract Test (`TST-UI-449.2`, `WorkoutSamplesDatabaseManagerLapSplitTest.java` / `WorkoutRepositoryLapSplitTest.kt`)*:
  • Verify `WorkoutSamplesDatabaseManager.splitLapAtSampleRow` atomically increments `LAP_NR` by 1 for all rows with `_id >= splitRowId`.
  • Verify `WorkoutRepository.splitLapAtSample` persists updated lap sequence to `Laps.db` via `replaceCroppedLaps` and reloads `WorkoutData`.
  • Verify rollback on SQLite exception leaves `WorkoutSamples.db` and `Laps.db` unchanged.
  3. *Compose UI, Canonical Blue Track & Split Iconography Contract Test (`TST-UI-449.3`, `CreateLapOnMapSheetTest.kt`, `PathPreviewMapTest.kt`)*:
  • Verify `CreateLapOnMapSheet` renders full GPS track instantiated via `MapTrack` with `type = TrackType.BEST` (canonical blue `TTColor.TrackBest`).
  • Verify `PathPreviewMap` receives `apexIconRes = R.drawable.ic_lap_split_scissors` for the split pin marker rather than the Max Line Distance ruler icon (`R.drawable.ic_distance`).
  • Verify dedicated scissors vector drawable `R.drawable.ic_lap_split_scissors` is rendered for split action buttons across `WorkoutLaps.kt`, `LapSplitVisualizer.kt`, `MapDetailLayout.kt`, and `CreateLapOnMapSheet.kt`.
  • Verify pre-seeding from scrub point index when launched from `MapDetailLayout`.
  • Verify confirmation button invocation dispatches split action with correct sample index.
  4. *9-Language Localization & Parity Test (`TST-UI-449.4`, `TranslationParityTest.kt`)*:
  • Verify 100% parity across all 9 supported locales for new `create_lap_*` string keys with zero missing entries and zero decorative inline emojis.
  5. *Clean-Room Regression Suite (`TST-UI-449.5`)*:
  • Run targeted package tests (`com.atrainingtracker.trainingtracker.ui.aftermath.*`, `com.atrainingtracker.trainingtracker.ui.map.*`, `database.*`, `ThemeColorCentralizationAuditTest`, `build.*`) asserting 100% pass rate.

### TST-UI-450: **Interactive Map Lap Boundary Moving in Workout Aftermath Verification Suite** [Verified]
- **Requirement(s)**: `REQ-UI-450`
- **Component**: `MoveLapBoundaryOnMapSheet.kt`, `PathPreviewMap.kt`, `LapSplitAndMoveCalculator.kt`, `WorkoutRepository.kt`, `WorkoutSamplesDatabaseManager.java`, `LapsDatabaseManager.java`, `LapEditBottomSheet.kt`, `WorkoutLaps.kt`, `TrackOnMapScreen.kt`
- **Jira**: `ATT-908`, `ATT-5483`, `ATT-5480`
- **Test Description**:
  1. *Domain Boundary Clamping & Move Preview Contract Test (`TST-UI-450.1`, `LapBoundaryMoveCalculatorTest.kt`)*:
  • Verify `LapSplitAndMoveCalculator.validateBoundaryMove` clamps and rejects boundaries outside $[startIndex(k) + 1, endIndex(k+1) - 1]$.
  • Verify reactive duration, distance, and speed calculations for forward and backward boundary adjustments.
  • Verify phantom lap prevention: both adjacent laps maintain positive duration and distance.
  2. *Database Range Update & Invariant Contract Test (`TST-UI-450.2`, `WorkoutSamplesDatabaseManagerBoundaryMoveTest.java` / `WorkoutRepositoryBoundaryMoveTest.kt`)*:
  • Verify `WorkoutSamplesDatabaseManager.updateLapNumberForSampleRange` selectively updates `LAP_NR` for sample rows between old and new boundaries.
  • Verify `WorkoutRepository.moveLapBoundary` updates `Laps.db` for Lap $k$ and Lap $k+1$ via `replaceCroppedLaps` while leaving all other laps ($1 \dots k-1$ and $k+2 \dots K$) untouched.
  • Verify rollback on SQLite exception preserves data integrity across tables.
  3. *Compose UI, Canonical Blue Track & Boundary Iconography Contract Test (`TST-UI-450.3`, `MoveLapBoundaryOnMapSheetTest.kt`, `PathPreviewMapTest.kt`)*:
  • Verify `MoveLapBoundaryOnMapSheet` renders track segment instantiated via `MapTrack` with `type = TrackType.BEST` (canonical blue `TTColor.TrackBest`).
  • Verify `PathPreviewMap` receives `apexIconRes = R.drawable.ic_lap_move_boundary` for the boundary pin marker rather than the Max Line Distance ruler icon (`R.drawable.ic_distance`).
  • Verify dedicated shift boundary vector drawable `R.drawable.ic_lap_move_boundary` is rendered for boundary move action buttons across `WorkoutLaps.kt`, `LapEditBottomSheet.kt`, `LapSplitVisualizer.kt`, and `MoveLapBoundaryOnMapSheet.kt`.
  • Verify pre-seeding from existing boundary index.
  • Verify confirmation button dispatches `onBoundaryConfirmed` with selected sample index.
  • Verify seamless entry point invocation from `LapEditBottomSheet` when opened within Workout Details (`TrackOnMapScreen.kt`), dismissing the edit sheet and opening `MoveLapBoundaryOnMapSheet` as a modal overlay (`Rule 47`).
  4. *9-Language Localization & Parity Test (`TST-UI-450.4`, `TranslationParityTest.kt`)*:
  • Verify 100% parity across all 9 supported locales for new `move_lap_boundary_*` string keys with zero missing entries and zero decorative inline emojis.
  5. *Clean-Room Regression Suite (`TST-UI-450.5`)*:
  • Run targeted package tests (`com.atrainingtracker.trainingtracker.domain.laps.*`, `com.atrainingtracker.trainingtracker.ui.aftermath.*`, `com.atrainingtracker.trainingtracker.ui.map.*`, `database.*`, `ThemeColorCentralizationAuditTest`, `build.*`) asserting 100% pass rate.

### TST-UI-451: **Tracking Cockpit SensorStatus Top Bar 16 dp Margin, Symmetrical Centering & 9-Icon Radar Integration Verification Suite** [Verified]
- **Requirement(s)**: `REQ-UI-451`
- **Component**: `SensorStatus.kt`, `SensorSourceDialog.kt`, `SensorType.java`, `SensorStatusTopBarSpacingTest.kt`, `SensorStatusLegibilityTest.kt`, `SensorSourceDialogContractTest.kt`, `TrackingTabsLandscapeLayoutTest.kt`
- **Jira**: `ATT-5422`, `ATT-7544`
- **Test Description**:
  1. *Portrait Edge Margin, Symmetrical Spacer & 9-Icon 360dp Budget Contract Test (`TST-UI-451.1`, `SensorStatusTopBarSpacingTest.kt`)*:
  • Verify outer `Row` has `padding(horizontal = 8.dp, vertical = 4.dp)` in portrait mode.
  • Verify left menu button and right supporter emblem (and their fallback `Spacer`s) have identical `Modifier.size(32.dp)` width.
  • Verify central `Box` has `weight(1f)` with `Alignment.Center` guaranteeing mathematical centering of the sensor icons across `supporterEmblemRes != null` and `supporterEmblemRes == null`.
  • Verify `sensorDefinitions` contains 9 sensor types with `RADAR_THREAT_COUNT` as the 9th item.
  • Verify horizontal padding of 4 dp per icon and 22 dp icon size yields 30 dp per slot and total width of 270 dp, fitting cleanly within the 272 dp budget on 360 dp viewports without clipping.
  2. *Compact Landscape Invariant Contract Test (`TST-UI-451.2`, `TrackingTabsLandscapeLayoutTest.kt`)*:
  • Verify compact landscape mode (`isCompactLandscape = true`) preserves `padding(horizontal = 4.dp, vertical = 2.dp)` and max height 28 dp per `REQ-UI-434.1`, rendering all 9 icons.
  3. *Sensor Inactive Alpha & Contrast Non-Regression Test (`TST-UI-451.3`, `SensorStatusLegibilityTest.kt`)*:
  • Verify `INACTIVE_SENSOR_ALPHA = 0.38f` and WCAG AAA / AA contrast ratio compliance are preserved for active and inactive states.
  4. *Radar Icon Asset & SensorType Mapping Contract Test (`TST-UI-451.4`, `SensorStatusTopBarSpacingTest.kt`)*:
  • Verify `R.drawable.ic_radar` exists in `res/drawable/` as a valid 24dp vector drawable.
  • Verify `SensorType.RADAR_THREAT_COUNT.getIconResId()` returns `R.drawable.ic_radar`.
  • Verify `ic_radar.xml` reorients radar waves 90 degrees counter-clockwise (to the left) via `<group>` rotation (`android:rotation="270"` around `android:pivotX="480"` and `android:pivotY="480"`) (`ATT-7736`).
  5. *Radar Sensor Source Dialog Contract Test (`TST-UI-451.5`, `SensorSourceDialogContractTest.kt`)*:
  • Verify `SensorSourceDialog` displays localized "Bike Radar" title (`R.string.device_type_bike_radar`) for `RADAR_THREAT_COUNT`.
  • Verify scalar `valueWithUnit` is suppressed on device identity rows for `RADAR_THREAT_COUNT`.
  • Verify `BIKE_RADAR` devices are listed and clicks route to `onDeviceClick`.
  6. *9-Language Localization Parity Verification (`TST-UI-451.6`)*:
  • Verify `device_type_bike_radar` and all radar content descriptions across all 9 values folders (`values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`).
  7. *Clean-Room Regression Suite Execution (`TST-UI-451.7`)*:
  • Execute targeted package tests (`com.atrainingtracker.trainingtracker.ui.tracking.controltracking.*`, `com.atrainingtracker.banalservice.sensor.*`) asserting 100% pass rate.

### TST-UI-452: **Dedicated Status Bar Vector Icon, Notification Builder Migration & Monolithic Density Packaging Verification Suite** [Verified]
- **Requirement(s)**: `REQ-UI-452`
- **Component**: `ic_stat_notify_logo.xml`, `TrainingApplication.java`, `TrackerService.java`, `WorkoutDeletionNotificationManager.kt`, `ExportNotificationManager.kt`, `SensorBatteryNotificationManager.kt`, `app/build.gradle`, `DrawableDensityFallbackAuditTest.kt`, `NotificationIconContractTest.kt`
- **Jira**: `ATT-5477`
- **Test Description**:
  1. *Vector Asset & Monochrome Silhouette Contract Test (`TST-UI-452.1`, `NotificationIconContractTest.kt`)*:
  • Verify `ic_stat_notify_logo.xml` exists in `app/src/main/res/drawable/` as a valid XML `<vector>`.
  • Verify `viewportWidth="24"` and `viewportHeight="24"`.
  • Verify there are no density-specific (`drawable-*dpi`) or `nodpi` siblings for `ic_stat_notify_logo`.
  2. *Notification Builder SmallIcon Call Site Verification (`TST-UI-452.2`, `NotificationIconContractTest.kt`)*:
  • Audit all 6 call sites across `TrainingApplication.java`, `TrackerService.java`, `WorkoutDeletionNotificationManager.kt`, `ExportNotificationManager.kt`, and `SensorBatteryNotificationManager.kt`.
  • Assert that zero notification builders invoke `.setSmallIcon(R.drawable.logo)`.
  • Assert that all notification builders invoke `.setSmallIcon(R.drawable.ic_stat_notify_logo)`.
  3. *App Bundle Density Split Configuration Test (`TST-UI-452.3`, `NotificationIconContractTest.kt`)*:
  • Verify `app/build.gradle` contains `bundle { density { enableSplit = false } }`.
  4. *Drawable Density Fallback & Core Coverage Test (`TST-UI-452.4`, `DrawableDensityFallbackAuditTest.kt`)*:
  • Verify `testAllDensityDrawablesHaveRootFallback()` passes without error.
  • Verify `testCoreDrawablesHaveFullHighDensityCoverage()` checks `xhdpi`, `xxhdpi`, and `xxxhdpi` and passes without requiring redundant `drawable-nodpi/` duplicates.
  5. *Clean-Room Regression Suite Execution (`TST-UI-452.5`)*:
  • Execute targeted package tests (`com.atrainingtracker.trainingtracker.resources.*`, `com.atrainingtracker.trainingtracker.notifications.*`, `build.*`) asserting 100% pass rate.

### TST-UI-453: **Aftermath Workout Details: Grade / Slope Distribution Card & 1% Telemetry Histogram Verification Suite** [Verified]
- **Requirement(s)**: `REQ-UI-453`
- **Component**: `GradeZoneDistributionCard.kt`, `ZoneDistributionCalculator.kt`, `ZoneDistributionColumnChart.kt`, `ZoneDistributionStackedBar.kt`, `ZoneDistributionModels.kt`, `WorkoutMasksAndCardsSection.kt`, `WorkoutDetailPreferences.kt`, `WorkoutCardSectionPreferences.kt`, `WorkoutSectionType.kt`, `WorkoutDetailTabConfig.kt`, `GradeZoneDistributionCardTest.kt`, `GradeZoneDistributionCalculatorTest.kt`, `WorkoutDetailPreferencesTest.kt`, `WorkoutMasksAndCardsLayoutTest.kt`, `TrackOnMapScreen.kt`, `WorkoutDetailTabbedContent.kt`, `WorkoutSummary.kt`, `TranslationParityTest.kt`
- **Jira**: `ATT-5487`, `ATT-5563`, `ATT-5654`
- **Test Description**:
  1. *Grade Zone Calculation & Canonical Thresholds Unit Test (`TST-UI-453.1`, `GradeZoneDistributionCalculatorTest.kt`)*:
  • Verify `ZoneDistributionCalculator.calculateGradeDistribution(...)` maps segments into exactly 7 categories matching `getElevationGradeColor` thresholds: `< 2%`, `2–5%`, `5–10%`, `10–15%`, `15–20%`, `20–25%`, `> 25%`.
  • Verify smoothing eliminates GPS quantization noise and prevents artificial `> 25%` spikes on flat segments.
  • Verify delta time clamping ($\Delta t \le 5\text{s}$) and handling of degenerate/stationary points.
  • Verify null return when path points or elevation data are empty or missing.
  2. *Fine-Grained 1% Grade Histogram Calculation Test (`TST-UI-453.2`, `GradeZoneDistributionCalculatorTest.kt`)*:
  • Verify generation of 1% histogram bins spanning `[minGrade, maxGrade]`.
  • Verify bin category indices and colors match `ElevationProfile.kt` color coding.
  • Verify total active time matches the sum of 7 category durations.
  3. *`GradeZoneDistributionCard` UI, Mode Switcher & Category Label Test (`TST-UI-453.3`, `GradeZoneDistributionCardTest.kt`)*:
  • Verify card renders icon `R.drawable.ic_grade`, localized title `aftermath_grade_zones_title` (*Steigungsverteilung* / *Slope Distribution*), and formatted total duration.
  • Verify 3-way toggle renders Balken (`STACKED_BAR`), Säulen (`COLUMNS`), and Histogramm (`HISTOGRAM`).
  • Verify Column Chart and Stacked Bar render mathematical threshold labels `<2%`, `≥2%`, `≥5%`, `≥10%`, `≥15%`, `≥20%`, `≥25%` for slope distribution, while HR/Power strictly retain `Z1`..`Z7`.
  • Verify view switching renders `ZoneDistributionStackedBar`, `ZoneDistributionColumnChart`, and `TelemetryHistogramChart` respectively.
  4. *Independent Section Entry, Reordering & Subordinate Switch Removal Test (`TST-UI-453.4`, `WorkoutDetailPreferencesTest.kt`, `WorkoutMasksAndCardsLayoutTest.kt`, `WorkoutDetailTabsTest.kt`, `WorkoutSummarySectionsTest.kt`)*:
  • Verify `WorkoutSectionType.GRADE_DISTRIBUTION` is present in `WorkoutSectionType.DEFAULT_ORDER` and serialized cleanly.
  • Verify `WorkoutDetailSectionType.GRADE_DISTRIBUTION` is present in `DEFAULT_TABS[2]` (`tab_zones`) following `ZONES`.
  • Verify `WorkoutDetailTabConfig.deserializeTabs` auto-migrates missing `GRADE_DISTRIBUTION` into `tab_zones`.
  • Verify `WorkoutMasksAndCardsSection.kt` maps `GRADE_DISTRIBUTION` in `buildMatrixFeatureRow` to `showGradeDistribution`.
  • Verify `WorkoutMasksAndCardsSection.kt` contains zero subordinate switches under `ZoneDisplayModeSelector` and strictly respects the `< 400` lines constraint (`TST-UI-377.5`).
  • Verify `WorkoutSummary.kt` and `WorkoutDetailTabbedContent.kt` render `GradeZoneDistributionCard` at the decoupled `GRADE_DISTRIBUTION` position.
  5. *9-Language Localization & Parity Test (`TST-UI-453.5`, `TranslationParityTest.kt`)*:
  • Verify 100% parity across all 9 supported locales for `aftermath_grade_zones_title` and `settings_workout_card_grade_distribution` with zero missing entries and zero decorative inline emojis.
  6. *Clean-Room Regression Suite Execution (`TST-UI-453.6`)*:
  • Execute targeted package tests (`com.atrainingtracker.trainingtracker.ui.aftermath.zones.*`, `com.atrainingtracker.trainingtracker.ui.aftermath.*`, `ThemeColorCentralizationAuditTest`, `build.*`) asserting 100% pass rate.

### TST-UI-454: **Interactive Aftermath Lap Deletion & Adjacent Lap Merging Verification Suite** [Verified]
- **Requirement(s)**: `REQ-UI-454`
- **Component**: `WorkoutSamplesDatabaseManager.java`, `LapsDatabaseManager.java`, `LapSplitAndMoveCalculator.kt`, `WorkoutRepository.kt`, `LapEditBottomSheet.kt`, `DeleteLapConfirmationDialog.kt`, `TranslationParityTest.kt`
- **Jira**: `ATT-5482`
- **Test Description**:
  1. *Domain Math & Merge Preview Calculation Test (`TST-UI-454.1`, `LapSplitAndMoveCalculatorTest.kt`)*:
  • Verify `calculateMergePreview` accurately sums active duration and distance and recomputes average speed for adjacent laps $k$ and $k+1$.
  • Verify `recalculateLapsAfterMerge` renumbers laps contiguously $1 \dots N-1$, combines names/descriptions cleanly, and preserves unaffected laps ($< k$ and $> k+1$).
  • Verify boundary validation rejects invalid boundary indices ($B < 1$ or $B \ge N$).
  2. *Database Transactional Decrement & Replace Contract Test (`TST-UI-454.2`, `WorkoutSamplesDatabaseManagerLapDeleteTest.java` / `WorkoutRepositoryLapDeleteTest.kt`)*:
  • Verify `WorkoutSamplesDatabaseManager.decrementLapNumbersAbove` atomically executes `UPDATE ... SET LAP_NR = LAP_NR - 1 WHERE LAP_NR > ?`.
  • Verify `WorkoutRepository.deleteLap` updates sample rows, calls `LapsDatabaseManager.replaceCroppedLaps`, refreshes reactive `WorkoutData`, and verifies total session metrics remain identical.
  • Verify rollback on SQLite exception leaves `WorkoutSamples.db` and `Laps.db` unchanged.
  3. *UI & Confirmation Dialog Contract Test (`TST-UI-454.3`, `DeleteLapConfirmationDialogTest.kt` / `LapEditBottomSheetTest.kt`)*:
  • Verify Delete / Merge CTA is displayed in `LapEditBottomSheet` when `totalLaps >= 2` and hidden/disabled when `totalLaps < 2`.
  • Verify `SingleChoiceSegmentedButtonRow` displays direction toggle for interior laps ($1 < k < N$) and fixed direction for boundary laps ($k=1$ and $k=N$).
  • Verify confirm button triggers repository delete action with the selected boundary.
  4. *9-Language Localization & Parity Test (`TST-UI-454.4`, `TranslationParityTest.kt`)*:
  • Verify 100% parity across all 9 supported locales for all `delete_lap_*` string keys with zero missing entries and zero decorative inline emojis.
  5. *Clean-Room Regression Suite Execution (`TST-UI-454.5`)*:
  • Execute targeted package tests (`com.atrainingtracker.trainingtracker.domain.laps.*`, `com.atrainingtracker.trainingtracker.ui.aftermath.*`, `com.atrainingtracker.trainingtracker.database.*`, `ThemeColorCentralizationAuditTest`, `build.*`) asserting 100% pass rate.

### TST-UI-455: **Workout-Time Pre-Filtered Photo Picker with Show All Photos Fallback Verification Suite** [Verified]
- **Requirement(s)**: `REQ-UI-455`
- **Component**: `WorkoutPhotoStrip.kt`, `WorkoutTimePhotoPickerSheet.kt`, `WorkoutPhotoManager.kt`, `AndroidManifest.xml`, `TranslationParityTest.kt`
- **Jira**: `ATT-5475`
- **Test Description**:
  1. *MediaStore Query & Temporal Filtering Unit Test (`TST-UI-455.1`, `WorkoutPhotoManagerTest.kt`)*:
  • Verify `WorkoutPhotoManager.queryWorkoutTimePhotos` queries `MediaStore.Images.Media.EXTERNAL_CONTENT_URI` with temporal bounds `[startTimeMs - 300_000L .. endTimeMs + 300_000L]`.
  • Verify fallback to `DATE_ADDED * 1000L` when `DATE_TAKEN` is null or $\le 0$.
  • Verify sorting by `DATE_TAKEN ASC` / `DATE_ADDED ASC`.
  2. *Runtime Permission & Fallback Decision Contract Test (`TST-UI-455.2`, `WorkoutPhotoPickerContractTest.kt`)*:
  • Verify clicking "Add Photos" when permission is denied or zero photos match immediately invokes `PickMultipleVisualMedia`.
  • Verify manifest declares `READ_MEDIA_IMAGES`, `READ_MEDIA_VISUAL_USER_SELECTED`, and `READ_EXTERNAL_STORAGE` (`maxSdkVersion="32"`).
  3. *`WorkoutTimePhotoPickerSheet` Compose UI & Rule 73 Contract Test (`TST-UI-455.3`, `WorkoutTimePhotoPickerSheetTest.kt`)*:
  • Verify sheet uses `AppModalBottomSheet(scrollable = false)` hosting a 3-column multi-select grid.
  • Verify Rule 73 stacked full-width buttons: Primary "Hinzufügen (<count>)" and Secondary "Alle Bilder anzeigen".
  • Verify thumbnail items render downsampled bitmaps and formatted capture timestamps.
  4. *9-Language Localization & Parity Test (`TST-UI-455.4`, `TranslationParityTest.kt`)*:
  • Verify 100% parity across all 9 supported locales for all new `workout_photos_*` strings with zero missing entries and zero decorative inline emojis.
  5. *Clean-Room Regression Suite Execution (`TST-UI-455.5`)*:
  • Execute targeted package tests (`com.atrainingtracker.trainingtracker.database.WorkoutPhotoManagerTest`, `com.atrainingtracker.trainingtracker.ui.aftermath.*`, `ThemeColorCentralizationAuditTest`, `build.*`) asserting 100% pass rate.

### TST-UI-456: **Drag & Drop Section Reordering and Touch Target Ergonomics Verification Suite** [Verified]
- **Requirement(s)**: `REQ-UI-456`, `REQ-UI-414.3`, `REQ-UI-408.5`
- **Component**: `WorkoutMasksAndCardsSection.kt`, `WorkoutDetailTabsConfigSheet.kt`, `WorkoutDetailTabsConfigComponents.kt`, `WorkoutMasksAndCardsLayoutTest.kt`, `WorkoutSectionReorderContractTest.kt`, `WorkoutDetailTabsTest.kt`, `TranslationParityTest.kt`
- **Jira**: `ATT-5761`
- **Test Description**:
  1. *Drag-and-Drop Reordering Logic & Boundary Swapping Unit Tests (`TST-UI-456.1`, `WorkoutSectionReorderContractTest.kt`)*:
  • Verify list swap calculation on synthetic section lists for drag-up and drag-down deltas.
  • Verify that dragging beyond index bounds (first item upward or last item downward) clamps safely without index corruption.
  2. *Detail Tabs MAP Pinning Invariant Unit Tests (`TST-UI-456.2`, `WorkoutDetailTabsTest.kt`)*:
  • Verify that `WorkoutDetailTabConfig.moveSectionInTab` and drag swapping cannot displace `MAP` from index 0 or move another section above `MAP`.
  3. *Layout Contract & 44dp Touch Target Verification (`TST-UI-456.3`, `WorkoutMasksAndCardsLayoutTest.kt`)*:
  • Verify that 22dp arrow icon buttons are replaced with drag-handle touch area ($\ge 44\text{dp}$).
  • Verify `WorkoutMasksAndCardsSection.kt` line count is strictly $< 400$ lines (`REQ-UI-414.5`).
  4. *9-Language Localization Audit (`TST-UI-456.4`, `TranslationParityTest.kt`)*:
  • Verify 100% parity across EN, DE, ES, FR, IT, JA, NL, PL, PT for `action_drag_to_reorder` and `workout_detail_tabs_map_pinned_desc` with zero missing entries and zero decorative inline emojis.
  5. *Clean-Room Targeted Regression Execution (`TST-UI-456.5`)*:
  • Execute targeted package tests (`com.atrainingtracker.trainingtracker.ui.settings.tuning.categories.*`, `com.atrainingtracker.trainingtracker.ui.aftermath.*`, `ThemeColorCentralizationAuditTest`, `build.*`) asserting 100% pass rate.

### TST-UI-457: **Dynamic Photo Thumbnail Downsampling Resolution & Dimension-Keyed Reloading Verification Suite** [Verified]
- **Requirement(s)**: `REQ-UI-457`, `REQ-UI-414.6`
- **Component**: `WorkoutPhotoStrip.kt`, `PhotoThumbnailSizeTest.kt`
- **Jira**: `ATT-5760`
- **Test Description**:
  1. *Dynamic Decode Max Dimension Calculation Unit Tests (`TST-UI-457.1`, `PhotoThumbnailSizeTest.kt`)*:
  • Verify `calculatePhotoDecodeMaxDim` across `SMALL`, `MEDIUM`, `LARGE` and multiple densities (1.0x, 2.0x, 2.75x, 3.0x).
  • Verify lower bound clamping at 256px and upper bound clamping at 1200px.
  2. *Dimension-Keyed LaunchedEffect Structural Contract Test (`TST-UI-457.2`, `PhotoThumbnailSizeTest.kt`)*:
  • Verify `PhotoThumbnailCard` computes `targetMaxDim` via `calculatePhotoDecodeMaxDim`.
  • Verify `remember` and `LaunchedEffect` are keyed on `targetMaxDim` (or `dimensions`) and pass `maxDim = targetMaxDim` to `decodeSampledBitmap`.
  3. *Clean-Room Targeted Regression Execution (`TST-UI-457.3`)*:
  • Execute targeted package tests (`com.atrainingtracker.trainingtracker.ui.aftermath.photos.*`, `build.*`) asserting 100% pass rate.

### TST-UI-458: **Uniform Action-Oriented Phrasing for Strava Bulk Export Card Verification Suite** [Verified]
- **Requirement(s)**: `REQ-UI-458`, `REQ-UI-293`, `REQ-MIG-048`
- **Component**: `strings.xml` (all 9 locales), `ImportFormatLocalizationTest.kt`, `ImportFormatCardsContractTest.kt`
- **Jira**: `ATT-5679`
- **Test Description**:
  1. *9-Language Action Verb Parity Unit Tests (`TST-UI-458.1`, `ImportFormatLocalizationTest.kt`)*:
  • Verify that `import_strava_archive_title` exists, is non-empty, and contains the required action verb in EN, DE, ES, FR, IT, JA, NL, PL, PT.
  • Verify zero decorative inline emojis in string resources across all 9 locales.
  2. *Layout & Composable Reference Contract Test (`TST-UI-458.2`, `ImportFormatCardsContractTest.kt`)*:
  • Verify `ImportBackupTabsScreen.kt` binds `StravaArchiveImportCard` title with `R.string.import_strava_archive_title`.
  3. *Clean-Room Targeted Regression Execution (`TST-UI-458.3`)*:
  • Execute targeted package tests (`com.atrainingtracker.trainingtracker.migration.*`, `com.atrainingtracker.trainingtracker.localization.*`, `build.*`) asserting 100% pass rate.

### TST-UI-459: **Trademark-Free Physiology & Localization Terminology Parity Verification Suite** [Verified]
- **Requirement(s)**: `REQ-UI-459`
- **Component**: `strings.xml` (all 9 locales), `WorkoutDetails.kt`, `AthleteProfileScreen.kt`, `AdvancedTuningAccordion.kt`, `TipsGuideScreen.kt`, `TranslationParityTest.kt`
- **Jira**: `ATT-5678`
- **Test Description**:
  1. *9-Language Localization & Positional Specifier Audit (`TST-UI-459.1`, `TranslationParityTest.kt`)*:
  • Verify that all 20 updated physiology string resources exist and are non-empty across all 9 locales (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`).
  • Verify positional format specifier preservation (`%1$+.1f`, `%1$.0f`, `%1$.2f`) and zero decorative inline emojis (`ATT-5400`).
  • Verify non-translatable badge strings `normalized_power_short` (`WP`), `intensity_factor_short` (`RI`), and `tss_short` (`Load`) are defined exclusively in `values/strings.xml`.
  2. *Tuning Accordion Subtitle & Model Concatenation Unit Tests (`TST-UI-459.2`, `AdvancedTuningAccordionTest.kt`)*:
  • Verify `tuning_physiology_pmc_short` is loaded as `"Load / Form"` (or localized) and accordion subtitle concatenates active models cleanly.
  3. *Sensor Category & Filter Query Contract Tests (`TST-UI-459.3`, `SensorCategoryAndSelectorContractTest.kt`)*:
  • Verify search filter matching by full name and short abbreviation for `NORMALIZED_POWER` (`"WP"` / `"Weighted Power"`).
  4. *Mathematical & Schema Invariance Unit Tests (`TST-UI-459.4`, `PowerPhysiologyCalculatorTest.kt`)*:
  • Verify calculation functions (`calculateHrTss`, `computePerformanceManagementChart`) and DTO mappings (`resolvePowerTrainingSummary`) produce identical results without schema alterations.
  5. *Clean-Room Targeted Regression Execution (`TST-UI-459.5`)*:
  • Execute targeted test suites (`com.atrainingtracker.trainingtracker.localization.*`, `com.atrainingtracker.trainingtracker.ui.athleteprofile.*`, `com.atrainingtracker.trainingtracker.ui.settings.tuning.*`, `com.atrainingtracker.trainingtracker.ui.help.*`, `com.atrainingtracker.trainingtracker.ui.tracking.editsensorfield.*`) asserting 100% pass rate.

### TST-UI-460: **Brand Compliance & Feature Title Neutralization Verification Suite** [Verified]
- **Requirement(s)**: `REQ-UI-460`
- **Component**: `strings.xml` (all 9 locales), `AppNavigationDrawer.kt`, `SegmentsTabsScreen.kt`, `TipsGuideScreen.kt`, `ImportBackupTabsScreen.kt`
- **Jira**: `ATT-5677`
- **Test Description**:
  1. *9-Language Title Neutralization & Brand Absence Audit (`TST-UI-460.1`, `TranslationParityTest.kt`)*:
  • Verify that `tip_live_segments_title`, `segments`, and `import_fit_description` exist and are non-empty across all 9 locales (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`).
  • Verify that `tip_live_segments_title` and `segments` do NOT contain the "Strava" brand prefix.
  • Verify that `import_fit_description` does NOT name "Garmin", "Wahoo", or "Hammerhead", and contains zero inline emojis (`ATT-5400`).
  2. *Navigation Drawer & Live Segments Tip UI Binding Contracts (`TST-UI-460.2`, `AppNavigationDrawerTest.kt`, `TipsGuideScreenContractTest.kt`)*:
  • Verify `AppNavigationDrawer` binds `R.string.segments` to `R.id.drawer_segments`.
  • Verify `TipsGuideScreen` binds `R.string.tip_live_segments_title` to Tip 4 card.
  3. *FIT Import Card Description Binding Contract (`TST-UI-460.3`, `ImportFormatCardsContractTest.kt`)*:
  • Verify `ImportBackupTabsScreen.kt` binds `R.string.import_fit_description` in `FitFileImportCard`.
  4. *Clean-Room Targeted Regression Execution (`TST-UI-460.4`)*:
  • Execute targeted test suites (`com.atrainingtracker.trainingtracker.localization.*`, `com.atrainingtracker.trainingtracker.ui.help.*`, `com.atrainingtracker.trainingtracker.migration.*`, `com.atrainingtracker.trainingtracker.build.*`) asserting 100% pass rate.

### TST-UI-461: **Trademark-Free Live Climb Tuning Title & Brand Hygiene Verification Suite** [Verified]
- **Requirement(s)**: `REQ-UI-461`
- **Component**: `strings.xml` (all 9 locales), `LiveClimbSheet.kt`, `TrackingViewsDatabaseManager.java`, `SensorGridLandscapeLayoutTest.kt`
- **Jira**: `ATT-5675`
- **Test Description**:
  1. *9-Language Title Neutralization & Brand Absence Audit (`TST-UI-461.1`, `TranslationParityTest.kt`)*:
  • Verify that `tuning_show_live_climbs_title` exists and is non-empty across all 9 locales (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`).
  • Verify that `tuning_show_live_climbs_title` across all 9 locales does NOT contain `ClimbPro` or `(ClimbPro)`.
  • Verify zero inline emojis in localized strings (`ATT-5400`).
  2. *Codebase Trademark & Comment Hygiene Audit (`TST-UI-461.2`)*:
  • Verify that `LiveClimbSheet.kt` KDoc contains no `(ClimbPro)` reference.
  • Verify that `TrackingViewsDatabaseManager.java` Javadoc contains no `ClimbPro` reference.
  • Verify that `SensorGridLandscapeLayoutTest.kt` KDoc contains no `ClimbPro` reference.
  3. *Tuning Preferences Contract & Schema Invariant Audit (`TST-UI-461.3`, `TrackingViewsDatabaseManagerTest.kt`)*:
  • Verify that `KEY_SHOW_LIVE_CLIMBS` ("tuning_show_live_climbs") and SQLite database column `TrackingViewsDbHelper.SHOW_LIVE_CLIMBS` ("show_live_climbs") remain unchanged.
  4. *Clean-Room Targeted Regression Execution (`TST-UI-461.4`)*:
  • Execute targeted test suites (`com.atrainingtracker.trainingtracker.localization.*`, `com.atrainingtracker.trainingtracker.ui.climbs.*`, `com.atrainingtracker.trainingtracker.database.*`, `com.atrainingtracker.trainingtracker.build.*`) asserting 100% pass rate.

### TST-UI-462: **Cluster Naming & Selection Dialog Reversion, Clear Action, and Tap Interaction Verification Suite** [Verified]
- **Requirement(s)**: `REQ-UI-462`
- **Component**: `ImportBackupTabsScreen.kt`, `WorkoutClusterComponents.kt`, `ClusterNamingDialogEscapeContractTest.kt`, `ClusterNamingDialogLabelsVisualContractTest.kt`
- **Jira**: `ATT-5674`, `ATT-5818`
- **Test Description**:
  1. *Clear Action & Create-New UI Binding Contract Tests (`TST-UI-462.1`, `ClusterNamingDialogEscapeContractTest.kt`)*:
  • Verify `WorkoutClusterSelectionDialog` declares optional parameter `onCreateNew: (() -> Unit)? = null`.
  • Verify `WorkoutClusterSelectionDialog` conditionally renders `Icons.Default.Add` and `R.string.cluster_naming__create_new` when `onCreateNew != null`.
  • Verify `ClusterNamingDialog` passes `onCreateNew` callback resetting `selectedCluster = null`.
  • Verify `ClusterNamingDialog` binds `Icons.Default.Clear` and `R.string.clear` when `selectedCluster != null`.
  2. *9-Language Localization & String Resource Audit (`TST-UI-462.2`, `TranslationParityTest.kt`)*:
  • Verify `cluster_naming__create_new`, `clear`, and `select_route` exist and are non-empty across all 9 supported locales (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`) with zero inline emojis (`ATT-5400`).
  3. *Composable Signature & Reflection Integrity Test (`TST-UI-462.3`, `ModalBottomSheetDialogsIntegrityTest.kt`)*:
  • Verify `WorkoutClusterSelectionDialog` composable function remains public and callable with default parameter overloads.
  4. *Clean-Room Targeted Regression Execution (`TST-UI-462.4`)*:
  • Execute targeted test suites (`com.atrainingtracker.trainingtracker.ui.clusters.*`, `com.atrainingtracker.trainingtracker.migration.*`, `com.atrainingtracker.trainingtracker.localization.*`, `com.atrainingtracker.trainingtracker.build.*`) asserting 100% pass rate.
  5. *Read-Only Route Picker Field Tap Handling Contract Test (`TST-UI-462.5`, `ClusterNamingDialogEscapeContractTest.kt`, ATT-5818)*:
  • Verify `ClusterNamingDialog` binds a `MutableInteractionSource` observing `PressInteraction.Release` to trigger `showSelectionDialog = true`.
  • Verify `OutlinedTextField` does not attach outer dead `Modifier.clickable` (Rule 77 / Design Guidelines §5.19).

### TST-UI-463: **Navigation Drawer & Expert Settings Thematic Grouping Verification Suite** [Verified]
- **Requirement(s)**: `REQ-UI-463`
- **Component**: `AppNavigationDrawer.kt`, `AdvancedTuningScreen.kt`, `AdvancedTuningDialog.kt`, `AdvancedTuningAccordion.kt`, `strings.xml` (all 9 locales)
- **Jira**: `ATT-5670`
- **Test Description**:
  1. *Navigation Drawer Group Structure & Ordering Contract Tests (`TST-UI-463.1`, `AppNavigationDrawerTest.kt`)*:
  • Verify `prefsOnlineCommunities` contains `drawer_backup_restore` directly below `drawer_export` (5 items total).
  • Verify `drawer__settings` contains exactly 6 items ending with `drawer_advanced_tuning`.
  • Verify new `drawer__info_support` group exists, is collapsible, and contains `drawer_supporter`, `drawer_tips_guide`, and `drawer_privacy_policy`.
  2. *Expert Settings Thematic Grouping & Accordion Sequence Contract Tests (`TST-UI-463.2`, `AdvancedTuningScreenTest.kt`)*:
  • Verify presence of 3 group subheaders and the new canonical sequence of 9 accordions across Screen and Dialog.
  3. *Modularity & File Line Limit Enforcement (`TST-UI-463.3`, `AdvancedTuningModularityTest.kt`)*:
  • Verify `AdvancedTuningScreen.kt`, `AdvancedTuningDialog.kt`, and `AdvancedTuningAccordion.kt` strictly maintain `< 400` lines.
  4. *9-Language Localization Audit (`TST-UI-463.4`, `TranslationParityTest.kt`)*:
  • Verify `drawer__info_support`, `tuning_group_cockpit_tracking`, `tuning_group_sensors_gps`, and `tuning_group_analysis_models` exist and are non-empty across all 9 locales with zero inline emojis (`ATT-5400`) and proper `&amp;` escaping.
  5. *Clean-Room Targeted Regression Execution (`TST-UI-463.5`)*:
  • Execute targeted test suites (`com.atrainingtracker.trainingtracker.ui.navigation.*`, `com.atrainingtracker.trainingtracker.ui.settings.tuning.*`, `com.atrainingtracker.trainingtracker.localization.TranslationParityTest`, `com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest`, `com.atrainingtracker.trainingtracker.build.*`) asserting 100% pass rate.

### TST-UI-464: **WorkoutDetailTabbedContent Horizontal Tab Swipe & Gesture Arbitration Verification Suite** [Verified]
- **Requirement(s)**: `REQ-UI-464`
- **Component**: `WorkoutDetailTabbedContent.kt`, `WorkoutDetailTabConfig.kt`, `MapDetailLayout.kt`, `WorkoutDetailTabsTest.kt`
- **Jira**: `ATT-5826`
- **Test Description**:
  1. *Edge Gutter Initial Down Lock Contract Tests (`TST-UI-464.1`, `WorkoutDetailTabsTest.kt`)*:
  • Verify `WorkoutDetailTabConfig.requiresHorizontalGestureIsolation` uses default `edgeSwipeGutterDp = 32f`.
  • Verify touches starting within `32.dp` of left or right screen boundary return `false` (pager swipe enabled) regardless of interactive canvas flag.
  • Verify that touch X is evaluated from initial down and locked across subsequent motion.
  2. *Granular Interactive Canvas & Strip Pass-Through Contract Tests (`TST-UI-464.2`, `WorkoutDetailTabsTest.kt`)*:
  • Verify `requiresHorizontalGestureIsolation` returns `false` when `isTouchingInteractiveCanvas = false` even on `tab_map_graphs` where `MAP`, `ELEVATION`, and `CHARTS` are all active.
  • Verify that non-interactive surfaces (`SplitPaneDivider`, `GlobalTelemetryZoomToolbar`, chart headings, Y-axis margin `0..50.dp`, and right histogram margin `52.dp`) do not assert canvas touch.
  3. *Active Map & Chart Plot Scrubbing Isolation Contract Tests (`TST-UI-464.3`, `WorkoutDetailTabsTest.kt`)*:
  • Verify `requiresHorizontalGestureIsolation` returns `true` when touching inside the map viewport or chart plot area (`touchXDp` between `50.dp` and `width - 52.dp`) away from edge gutters.
  4. *Clean-Room Targeted Regression Execution (`TST-UI-464.4`)*:
  • Execute targeted unit test suites (`com.atrainingtracker.trainingtracker.ui.aftermath.*`, `com.atrainingtracker.trainingtracker.ui.map.*`, `com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest`, `com.atrainingtracker.trainingtracker.build.*`) asserting 100% pass rate.

### TST-UI-465: **KnownLocationCard UI Polish Verification Suite** [Verified]
- **Requirement(s)**: `REQ-UI-465`
- **Component**: `KnownLocationsScreen.kt`, `KnownLocationCardLayoutTest.kt`
- **Jira**: `ATT-5825`
- **Test Description**:
  1. *48dp Minimum Touch Target Gap Elimination Contract Tests (`TST-UI-465.1`, `KnownLocationCardLayoutTest.kt`)*:
  • Verify `KnownLocationsScreen.kt` scopes drill-down badges under `CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified)` or equivalent to prevent 48dp vertical padding bloat in `FlowRow`.
  • Verify "Starts" badge maintains distinct styling and clickability (`location_starts_badge_*`).
  2. *Semantic Altitude Icon Contract Tests (`TST-UI-465.2`, `KnownLocationCardLayoutTest.kt`)*:
  • Verify `KnownLocationsScreen.kt` references `Icons.Default.Terrain` for altitude and does not use `ic_ascent`.
  • Verify altitude styling uses `onSurfaceVariant` secondary tint instead of `primary`.
  3. *Compact Landscape Map Thumbnail Contract Tests (`TST-UI-465.3`, `KnownLocationCardLayoutTest.kt`)*:
  • Verify `KnownLocationThumbnailMap` specifies `width = 116.dp` and `height = 80.dp`.
  • Verify `KnownLocationCardLayoutTest` assertions updated from `.size(100.dp)` to `.size(width = 116.dp, height = 80.dp)`.
  4. *Clean-Room Targeted Regression Execution (`TST-UI-465.4`)*:
  • Execute targeted unit test suites (`com.atrainingtracker.trainingtracker.ui.knownlocations.*`, `com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest`, `com.atrainingtracker.trainingtracker.build.*`) asserting 100% pass rate.

### TST-UI-466: **KnownLocationsScreen Multi-Dimensional Filter Verification Suite** [Verified]
- **Requirement(s)**: `REQ-UI-466`
- **Component**: `KnownLocationsViewModel.kt`, `KnownLocationsScreen.kt`, `KnownLocationsFilterBottomSheet.kt`, `ActiveKnownLocationsFilterChipsRow.kt`, `KnownLocationsViewModelTest.kt`
- **Jira**: `ATT-5823`
- **Test Description**:
  1. *Default Starts Threshold & Query Unit Tests (`TST-UI-466.1`, `KnownLocationsViewModelTest.kt`)*:
  • Verify `KnownLocationsViewModel` initializes with `minStarts = 5`.
  • Verify locations with $< 5$ starts are filtered out by default, while locations with $\ge 5$ starts are retained.
  • Verify filtering with `minStarts = null` includes all locations.
  • Verify combining query search with `minStarts` threshold.
  2. *Filter Action Button & Badge Contract Tests (`TST-UI-466.2`, `KnownLocationsScreenTest.kt`)*:
  • Verify `FilterActionButton` is present in `KnownLocationsScreen` top app bar with `known_locations_filter_button` test tag.
  • Verify active filter badge renders when criteria are active.
  3. *Active Filter Chips Row & Dismissal Contract Tests (`TST-UI-466.3`, `KnownLocationsScreenTest.kt`)*:
  • Verify active chip `"≥ 5 Starts"` is displayed when `minStarts = 5`.
  • Verify tapping remove dismisses the threshold and updates `filteredLocations`.
  4. *9-Language Localization Audit (`TST-UI-466.4`, `TranslationParityTest.kt`)*:
  • Verify `filter_known_locations_title`, `filter_min_starts_label`, `filter_min_starts_chip_format`, and `filter_no_matching_locations` exist across all 9 locales with zero format specifier mismatches.
  5. *Targeted Regression Execution (`TST-UI-466.5`)*:
  • Execute targeted unit test suites (`com.atrainingtracker.trainingtracker.ui.knownlocations.*`, `com.atrainingtracker.trainingtracker.localization.TranslationParityTest`, `com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest`, `com.atrainingtracker.trainingtracker.build.*`) asserting 100% pass rate.

### TST-UI-467: **Workout Clusters & Filter Sheets Starts and Hit Count Default Filtering Verification Suite** [Verified]
- **Requirement(s)**: `REQ-UI-467`
- **Component**: `WorkoutClustersViewModel.kt`, `ClusterFilterCriteria.kt`, `ClusterFilterBottomSheet.kt`, `WorkoutFilterBottomSheet.kt`, `WorkoutClustersViewModelTest.kt`, `ClusterFilterBottomSheetTest.kt`, `WorkoutFilterBottomSheetTest.kt`
- **Jira**: `ATT-5822`
- **Test Description**:
  1. *WorkoutClustersViewModel Default Hit Count Session Filter Tests (`TST-UI-467.1`, `WorkoutClustersViewModelTest.kt`)*:
  • Verify `WorkoutClustersViewModel` initializes with `minHitCount = 5`.
  • Verify clearing filter sets `minHitCount = null`.
  • Verify updating other filter criteria preserves or modifies `minHitCount` cleanly.
  2. *ClusterFilterBottomSheet Location Starts Filtering & Toggle Contract Tests (`TST-UI-467.2`, `ClusterFilterBottomSheetTest.kt`)*:
  • Verify locations with $< 5$ starts are hidden by default when unselected.
  • Verify currently selected location with $< 5$ starts is preserved in the list.
  • Verify toggle chip `"Alle anzeigen"` / `"Nur ≥ 5 Starts"` toggles display of all locations.
  3. *WorkoutFilterBottomSheet Location Starts Filtering & Toggle Contract Tests (`TST-UI-467.3`, `WorkoutFilterBottomSheetTest.kt`)*:
  • Verify locations with $< 5$ starts are hidden by default when unselected.
  • Verify currently selected location with $< 5$ starts is preserved.
  • Verify toggle chip correctly expands and collapses infrequent locations.
  4. *9-Language Localization Audit (`TST-UI-467.4`, `TranslationParityTest.kt`)*:
  • Verify `filter_locations_show_all` and `filter_locations_show_frequent` exist across all 9 locales with zero format specifier mismatches and zero emojis (`ATT-5400`).
  5. *Tier 1 Targeted Regression Execution (`TST-UI-467.5`)*:
  • Execute targeted test suites (`com.atrainingtracker.trainingtracker.ui.clusters.*`, `com.atrainingtracker.trainingtracker.ui.aftermath.workoutlist.*`, `com.atrainingtracker.trainingtracker.localization.TranslationParityTest`, `com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest`, `com.atrainingtracker.trainingtracker.build.*`) asserting 100% pass rate.

### TST-UI-468: **Active Filter Chips Row Uniform 'Clear all' Action Verification Suite** [Verified]
- **Requirement(s)**: `REQ-UI-468`
- **Component**: `ActiveFilterChipsRow.kt`, `ActiveSegmentFilterChipsRow.kt`, `ActiveRouteFilterChipsRow.kt`, `ActiveClusterFilterChipsRow.kt`, `ActiveDeviceFilterChipsRow.kt`, `ActiveKnownLocationsFilterChipsRow.kt`, `ActiveFilterChipsRowClearAllContractTest.kt`
- **Jira**: `ATT-5888`
- **Test Description**:
  1. *Structural Contract Test (`TST-UI-468.1`, `ActiveFilterChipsRowClearAllContractTest.kt`)*:
  • Verify that none of the 6 `Active*FilterChipsRow` implementations contain conditional guards `activeFilterCount > 1` or `activeFilterCount >= 2`.
  • Verify that all 6 implementations reference `R.string.filter_clear_all`.
  • Verify that all 6 implementations enforce `if (criteria.isEmpty) return`.
  2. *9-Language Localization Audit (`TST-UI-468.2`, `TranslationParityTest.kt`)*:
  • Verify `filter_clear_all` exists across all 9 supported locales (`values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`) with zero format specifier mismatches.
  3. *Tier 1 Targeted Regression Execution (`TST-UI-468.3`)*:
  • Execute targeted unit test suites (`com.atrainingtracker.trainingtracker.ui.aftermath.workoutlist.*`, `com.atrainingtracker.trainingtracker.ui.segments.segmentlist.*`, `com.atrainingtracker.trainingtracker.ui.routes.*`, `com.atrainingtracker.trainingtracker.ui.clusters.*`, `com.atrainingtracker.trainingtracker.localization.TranslationParityTest`, `com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest`, `com.atrainingtracker.trainingtracker.build.*`) asserting 100% pass rate.

### TST-UI-469: **KnownLocationCard Harmonized Unboxed Layout & Typography Verification Suite** [Verified]
- **Requirement(s)**: `REQ-UI-469`
- **Component**: `KnownLocationsScreen.kt`, `KnownLocationCardLayoutTest.kt`, `KnownLocationCardRoutesBadgeTest.kt`
- **Jira**: `ATT-5887`
- **Test Description**:
  1. *Structural Contract & Layout Tests (`TST-UI-469.1`, `KnownLocationCardLayoutTest.kt`)*:
  • Verify that `KnownLocationCard` uses `Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), verticalAlignment = Alignment.Bottom)`.
  • Verify that the left column takes `Modifier.weight(1f).fillMaxHeight()` and contains a vertical spring `Spacer(Modifier.weight(1f))`.
  • Verify that the starts metric renders with `Icons.AutoMirrored.Filled.DirectionsRun` and bold primary blue typography (`bodyMedium`, `FontWeight.Bold`, `primary`).
  • Verify that the routes cross-link renders with `R.drawable.ic_favorite_route` and primary blue link styling (`bodySmall`, `FontWeight.Medium`, `primary`).
  • Verify the elimination of bordered `Surface` pill badges (`RoundedCornerShape(8.dp)`) in the metadata column.
  • Verify preservation of test tags `location_starts_badge_${item.id}` and `location_routes_badge_${item.id}`.
  2. *Routes Badge Interaction & Filtering Invariant Tests (`TST-UI-469.2`, `KnownLocationCardRoutesBadgeTest.kt`)*:
  • Verify independent click callback invocations for `onShowWorkouts` and `onShowRoutes`.
  • Verify spatial cluster filter criteria construction from location item.
  3. *9-Language Localization Audit (`TST-UI-469.3`, `TranslationParityTest.kt`)*:
  • Verify `known_locations_starts` and `known_locations_routes` plurals exist across all 9 supported locales (`values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`) with zero format specifier mismatches.
  4. *Tier 1 Targeted Regression Execution (`TST-UI-469.4`)*:
  • Execute targeted unit test suites (`com.atrainingtracker.trainingtracker.ui.knownlocations.*`, `com.atrainingtracker.trainingtracker.localization.TranslationParityTest`, `com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest`, `com.atrainingtracker.trainingtracker.build.*`) asserting 100% pass rate.

### TST-UI-470: **Known Locations Map Orientation, FAB & Description Parity Verification Suite** [Verified]
- **Requirement(s)**: `REQ-UI-470`
- **Component**: `KnownLocationZoomMath.kt`, `KnownLocationsScreen.kt`, `EditKnownLocationDialog.kt`, `KnownLocationsViewModel.kt`, `KnownLocationsRepository.kt`, `MapUtils.kt`, `KnownLocationZoomMathTest.kt`, `EditKnownLocationDialogTest.kt`, `KnownLocationsScreenTest.kt`, `KnownLocationCardLayoutTest.kt`
- **Jira**: `ATT-5884, ATT-6211, ATT-6210`
- **Test Description**:
  1. *Zoom Math Clamping & Shift Unit Tests (`TST-UI-470.1`, `KnownLocationZoomMathTest.kt`)*:
  • Verify `MIN_ZOOM = 9.7f`, `DEFAULT_THUMBNAIL_ZOOM = 11.2f`, `MAX_ZOOM = 11.7f`.
  • Verify small radii (50m, 100m, 200m) clamp to `11.7f`.
  • Verify medium radii (300m, 500m) scale proportionally within `[9.7f, 11.7f]`.
  • Verify large radii (1000m+) clamp to `9.7f`.
  2. *FAB & Creation Flow Contract Tests (`TST-UI-470.2`, `KnownLocationsScreenTest.kt`)*:
  • Verify `FloatingActionButton` exists in `KnownLocationsScreen` with test tag `known_locations_add_fab`.
  • Verify tapping FAB triggers new location creation dialog flow.
  3. *Canonical Material Heart Glyph Marker & Thumbnail Alignment Tests (`TST-UI-470.3`, `KnownLocationsScreenTest.kt`, `MapUtils.kt`)*:
  • Verify `createCenteredHeartGlyphMarker` renders canonical Material Favorite path geometry centered with an opaque white halo (`Color.White`, 100% opacity, stroke width >= 2.5dp).
  • Verify `KnownLocationThumbnailMap` in `KnownLocationsScreen` uses `createCenteredHeartGlyphMarker` with concentric `anchor = Offset(0.5f, 0.5f)`.
  • Verify absence of standing pin `R.drawable.my_locations` in thumbnail map.
  • Verify preview mode in `KnownLocationsScreen` continues to use `Icons.Default.Favorite`.
  4. *Home Base Subtitle & Edit Dialog Contract Tests (`TST-UI-470.4`, `EditKnownLocationDialogTest.kt`)*:
  • Verify `EditKnownLocationDialog` displays `R.string.known_locations_home_base_desc` instead of `known_locations_set_home` in the home toggle row.
  5. *9-Language Localization Audit (`TST-UI-470.5`, `TranslationParityTest.kt`)*:
  • Verify `known_locations_home_base_desc`, `known_locations_add_location`, and `known_locations_new_location` exist across all 9 supported locales (`values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`) with zero format specifier mismatches and zero unescaped characters.
  6. *Tier 1 Targeted Regression Execution (`TST-UI-470.6`)*:
  • Execute targeted unit test suites (`com.atrainingtracker.trainingtracker.ui.knownlocations.*`, `com.atrainingtracker.trainingtracker.localization.TranslationParityTest`, `com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest`, `com.atrainingtracker.trainingtracker.build.*`) asserting 100% pass rate.
  7. *Sport Type Starts Breakdown Unit & Contract Tests (`TST-UI-470.7`, `KnownLocationCardLayoutTest.kt`, `KnownLocationsViewModelTest.kt`)*:
  • Verify `KnownLocationsViewModel` partitions workout starts by `BSportType` into `SportStartsCount(bike, run, other)`.
  • Verify `KnownLocationCard` renders sport drawables in canonical order `[Bike, Run, Other]` with `Color.Unspecified`, and test tag `location_starts_badge_${item.id}` remains present.

### TST-UI-471: **Cockpit Edit-Mode Typography, Localization Parity & FAB Layout Polish Suite** [Verified]
- **Requirement(s)**: `REQ-UI-471`
- **Component**: `SensorFieldView.kt`, `SensorGridScreen.kt`, `EditSensorFieldDialog.kt`, `TrackingTabsScreen.kt`, `TrackingTabPreviewHeader.kt`, `TrackingTabConfigHeader.kt`, `FilterType.kt`, `TranslationParityTest.kt`
- **Jira**: `ATT-5879`
- **Test Description**:
  1. *Edit-Mode Typography & Single-Line Constraints (`TST-UI-471.1`, `SensorFieldViewTest.kt`)*:
  • Verify `SensorFieldView` renders 8-character time values (`18:29:15`) on a single line (`maxLines = 1`) without multi-line wrapping in edit mode.
  • Verify filter badges with `MOVING_AVERAGE_TIME` render single-line `2 s Ø` with SI unit `s` instead of `sec` and do not wrap into 3 vertical lines.
  2. *9-Language Localization Audit (`TST-UI-471.2`, `TranslationParityTest.kt`)*:
  • Verify zero missing entries across all 9 locales for `cd_move_field` and `cd_delete_field`.
  • Verify German strings: `time_active_short` = "Aktiv", `time_lap_short` = "Runde", `time_lap` = "Rundenzeit", `lap_number_short` = "Runde Nr.", `distance_lap_short` = "Dist. Runde", `config_tracking__showLiveSegments` = "Live-Segmente", `config_tracking__showLapButton` = "Runden-Button", `edit_field` = "Anzeige anpassen", `Done` = "Fertig", `filter_moving_average` = "gleitender Durchschnitt", and `units_seconds` = "s".
  3. *Field Configuration Dialog Polish Contract Tests (`TST-UI-471.3`, `EditSensorFieldDialogTest.kt`)*:
  • Verify `EditSensorFieldDialog` title is sentence-case `Anzeige anpassen`.
  • Verify `Relative Breite` header uses `Modifier.fillMaxWidth()` aligning with left-aligned chips.
  • Verify category breadcrumb supports `maxLines = 2` without ellipsis truncation.
  • Verify smoothing selector button renders leading `Icons.Default.Tune` icon, trailing chevron, and formatted summary.
  4. *Elevation Profile Bottom Clearance for FAB Overlay (`TST-UI-471.4`, `SensorGridScreenTest.kt`)*:
  • Verify `ElevationProfile` on `Karte` tab applies bottom clearance (68dp) in portrait mode when `showBikeBell` or `showLapButton` is enabled, preventing bell FAB overlap on placeholder text.
  5. *Header Sport Context Verification (`TST-UI-471.5`, `TrackingTabHeaderTest.kt`)*:
  • Verify `TrackingTabPreviewHeader` and `TrackingTabConfigHeader` accept `sportName` and render it above the tab title.
  6. *Tier 1 Targeted Unit Test Regression (`TST-UI-471.6`)*:
  • Execute targeted test suites (`com.atrainingtracker.trainingtracker.ui.tracking.*`, `com.atrainingtracker.trainingtracker.localization.TranslationParityTest`, `com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest`, `com.atrainingtracker.trainingtracker.build.*`) asserting 100% pass rate.

### TST-UI-472: **Display & Expert Settings Terminology, Subtitle Localization & Warning Deduplication Suite** [Verified]
- **Requirement(s)**: `REQ-UI-472`
- **Component**: `AdvancedTuningAccordion.kt`, `TuningFormControls.kt`, `AdvancedTuningScreen.kt`, `AdvancedTuningDialog.kt`, `res/values*/strings.xml`, `TranslationParityTest.kt`, `AdvancedTuningAccordionTest.kt`
- **Jira**: `ATT-5875`
- **Test Description**:
  1. *9-Language Localization Audit (`TST-UI-472.1`, `TranslationParityTest.kt`)*:
  • Verify zero missing entries across all 9 supported locales for `tuning_battery_saver_flat_short`, `tuning_battery_saver_steep_short`, `tuning_sensors_alt_short`, `tuning_sensors_speed_short`, `tuning_cluster_end_short`, `tuning_cluster_apex_short`, `tuning_cluster_dist_short`, and `tuning_cluster_alt_short`.
  • Verify `cockpit_theme_description` does not contain `Control Tracking` in any locale and references localized tab name.
  • Verify German `advanced_tuning_warning_title` is `Hinweis`, `tuning_cat_aftermath` is `Nachbereitung & Profil-Analytik`, and `tuning_sensor_search_active` is `Aktive`.
  2. *Accordion Subtitle Formatter Contract Tests (`TST-UI-472.2`, `AdvancedTuningAccordionTest.kt`)*:
  • Verify `formatBatterySaverSubtitle()` renders localized `Flach`/`Steil` when German context is provided and falls back to `Flat`/`Steep` without context.
  • Verify `formatSensorsGpsSubtitle()` renders localized `Höhe`/`Geschw.` when German context is provided and falls back to `Alt`/`Speed` without context.
  • Verify `formatClusterTuningSubtitle()` formats metric tolerances $\le 1000\text{ m}$ as integer meters (`200 m`, `400 m`) and uses localized labels (`Ende:`, `Apex:`, `Dist.:`, `Höhe:`).
  3. *Advisory Banner Deduplication Contract Tests (`TST-UI-472.3`, `AdvancedTuningVisualContractTest.kt`)*:
  • Verify `TuningWarningNotice` continues to bind `R.string.advanced_tuning_warning_title` and `R.string.advanced_tuning_warning_desc`.
  4. *Tier 1 Targeted Unit Test Regression (`TST-UI-472.4`)*:
  • Execute targeted test suites (`com.atrainingtracker.trainingtracker.ui.settings.tuning.*`, `com.atrainingtracker.trainingtracker.localization.TranslationParityTest`) asserting 100% pass rate.

### TST-UI-473: **Modal Bottom Sheet Close Button Default Contract & Activity Type Selection Test Suite** [Verified]
- **Requirement(s)**: `REQ-UI-473`
- **Component**: `AppModalBottomSheet.kt`, `ActivityTypeSelectionDialog.kt`, `AppModalBottomSheetTest.kt`
- **Jira**: `ATT-5873`
- **Test Description**:
  1. *Default Close Button Contract Reflection / Logic Test (`TST-UI-473.1`, `AppModalBottomSheetTest.kt`)*:
  • Verify `AppModalBottomSheet` and `AppBottomSheetContent` function signatures declare parameter `actions` before `showCloseButton`.
  • Verify contract behavior: when `actions == null`, default close button is enabled (`showCloseButton = true`); when `actions != null`, default close button is suppressed (`showCloseButton = false`).
  2. *Activity Type Selection Dialog Simplification Audit (`TST-UI-473.2`)*:
  • Verify `ActivityTypeSelectionDialog` does not configure bottom actions (`actions == null`), presenting only the header close icon alongside item tap selection.
  3. *Cross-Caller Source Compatibility Audit (`TST-UI-473.3`)*:
  • Verify compilation and source compatibility across all existing modal bottom sheet callers (`StravaSettingsDialog`, `DropboxSyncDialog`, `GDriveSyncDialog`, etc.).
  4. *Tier 1 Targeted Unit Test Regression (`TST-UI-473.4`)*:
  • Execute targeted test suite (`com.atrainingtracker.trainingtracker.ui.components.core.*`, `com.atrainingtracker.trainingtracker.ui.settings.trackingtabs.*`, `com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest`, `com.atrainingtracker.trainingtracker.build.*`) asserting 100% pass rate.

### TST-UI-474: **Import & Backup UI Polish, Strava Archive Tuning & Heading Deduplication Test Suite** [Verified]
- **Requirement(s)**: `REQ-UI-474`
- **Component**: `ImportBackupTabsScreen.kt`, `BackupTabContent.kt`, `RestoreTabContent.kt`, `res/values*/strings.xml`, `RestoreCardContractTest.kt`, `TranslationParityTest.kt`
- **Jira**: `ATT-5874`
- **Test Description**:
  1. *9-Language Localization Audit (`TST-UI-474.1`, `TranslationParityTest.kt`)*:
  • Verify zero missing entries across all 9 supported locales for `merge_from_backup_file` and `backup_now`.
  2. *Restore Card Iconography & Deduplication Contract (`TST-UI-474.2`, `RestoreCardContractTest.kt`)*:
  • Verify Dropbox restore button renders `dropbox_logo_blue` with `tint = Color.Unspecified`.
  • Verify Google Drive restore button renders `ic_google_drive` with `tint = Color.Unspecified`.
  • Verify incremental import button renders `Icons.Default.CallMerge` and binds `R.string.merge_from_backup_file`.
  3. *Backup Tab Header & Switch Layout Contract (`TST-UI-474.3`)*:
  • Verify `BackupTabContentLayout` renders `R.string.backup_now` on backup button and header row contains `Switch` for automated backups.
  4. *Strava Archive Pre-Import Tuning Contract (`TST-UI-474.4`)*:
  • Verify `PreImportTuningBottomSheet` supports `showStravaUploadOption` and suppresses Strava upload toggle when false.
  5. *Tier 1 Targeted Unit Test Regression (`TST-UI-474.5`)*:
  • Execute targeted test suite (`com.atrainingtracker.trainingtracker.migration.*`, `com.atrainingtracker.trainingtracker.localization.TranslationParityTest`, `com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest`, `com.atrainingtracker.trainingtracker.build.*`) asserting 100% pass rate.

### TST-UI-475: **Sport Type Card Presentation, Localized Mappings & Locale-Aware Usage Dates Test Suite** [Verified]
- **Requirement(s)**: `REQ-UI-475`
- **Component**: `SportTypesTabsScreen.kt`, `DateTimeUtils.kt`, `DateTimeUtilsTest.kt`, `SportTypesTabsScreenContractTest.kt`, `res/values*/strings.xml`, `arrays.xml`
- **Jira**: `ATT-5872`
- **Test Description**:
  1. *Locale-Aware ISO Date Formatting Test (`TST-UI-475.1`, `DateTimeUtilsTest.kt`)*:
  • Verify `formatIsoDate` correctly formats ISO-8601 `LocalDate` strings into medium localized date format across `de_DE` (`12.08.2018`), `en_US` (`Aug 12, 2018`), and `ja_JP`.
  • Verify graceful fallback handling for null, blank, and malformed date strings without throwing runtime exceptions.
  2. *Sport Type Card Presentation & Conditional GC Mapping Contract (`TST-UI-475.2`, `SportTypesTabsScreenContractTest.kt`)*:
  • Verify empty or null `gcName` suppresses `mapping_format_gc` and renders only `mapping_format_tcx`.
  • Verify non-empty `gcName` renders combined `sport_type_file_mapping_format`.
  3. *Default Sport Type & Export Key Localization Contract (`TST-UI-475.3`, `SportTypesTabsScreenContractTest.kt`)*:
  • Verify locked default sport types (`!item.isEditable`) bind localized display names via `bSportType.stringResId` (`R.string.sport_type_other`).
  • Verify Strava, TCX, and GC export identifiers map to localized UI array strings.
  4. *Usage Date Formatting Integration Contract (`TST-UI-475.4`)*:
  • Verify `UsageItem` receives formatted date strings instead of raw ISO dates.
  5. *Tier 1 Targeted Unit Test Regression (`TST-UI-475.5`)*:
  • Execute targeted test suite (`com.atrainingtracker.trainingtracker.ui.common.*`, `com.atrainingtracker.trainingtracker.ui.settings.sporttypes.*`, `com.atrainingtracker.trainingtracker.localization.TranslationParityTest`, `com.atrainingtracker.trainingtracker.build.*`) asserting 100% pass rate.

### TST-UI-487: **Navigation Drawer Units Disambiguation & Distinct Power Cockpit Iconography Test Suite** [Verified]
- **Requirement(s)**: `REQ-UI-487`
- **Component**: `AppNavigationDrawer.kt`, `UnitsSettingsDialog.kt`, `ActivityTypeSelectionDialog.kt`, `CopyTrackingTabDialog.kt`, `ActivityType.java`, `res/values-de/strings.xml`, `res/values-ja/strings.xml`, `res/drawable/bsport_bike_power.xml`
- **Jira**: `ATT-5871`
- **Test Description**:
  1. *German Navigation Drawer Disambiguation Contract (`TST-UI-487.1`)*:
  • Verify `prefsUnitsTitle` in `values-de` resolves to `"Maßeinheiten"`, eliminating duplicate label collision with `tab_workouts` (`"Einheiten"`).
  • Verify `AppNavigationDrawer.kt` Settings group binds `R.string.prefsUnitsTitle` to `drawer_units`.
  2. *Units Settings Dialog Imperial Localization Parity (`TST-UI-487.2`, `TranslationParityTest.kt`)*:
  • Verify `units_type_imperial` in `values-de` resolves to `"Imperial (Meilen)"`.
  • Verify `units_type_imperial` in `values-ja` resolves to `"ヤードポンド法 (マイル)"`.
  • Verify 100% string translation and placeholder parity across all 9 locales.
  3. *Distinct Bike Power Iconography Contract (`TST-UI-487.3`)*:
  • Verify `ActivityType.BIKE_POWER.logoId` equals `R.drawable.bsport_bike_power`.
  • Verify `ActivityType.BIKE_SPEED_AND_CADENCE.logoId` equals `R.drawable.bsport_bike`.
  • Verify `bsport_bike_power.xml` is present, parses cleanly as a VectorDrawable with 192x192 viewport, contains both cyclist geometry and canonical lightning bolt badge paths.
  4. *Tier 1 Targeted Unit Test Regression (`TST-UI-487.4`)*:
  • Execute targeted test suite (`com.atrainingtracker.trainingtracker.localization.TranslationParityTest`, `com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest`, `com.atrainingtracker.trainingtracker.build.*`) asserting 100% pass rate.

### TST-UI-488: **Strava Settings Dialog Close Button Elimination & Sync Card Action Icons Test Suite** [Verified]
- **Requirement(s)**: `REQ-UI-488`
- **Component**: `StravaSettingsDialog.kt`, `StravaSettingsDialogContractTest.kt`, `AppModalBottomSheet.kt`
- **Jira**: `ATT-5854`
- **Test Description**:
  1. *Close Button Suppression Contract (`TST-UI-488.1`, `StravaSettingsDialogContractTest.kt`)*:
  • Verify `StravaSettingsDialog` invokes `AppBottomSheetContent` with non-null `actions` and without overriding `showCloseButton = true`, guaranteeing automatic `✕` close button suppression.
  2. *Sync Card Layout & Refresh Icon Affordance (`TST-UI-488.2`, `StravaSettingsDialogContractTest.kt`)*:
  • Verify each of the three sync cards (*Equipment*, *Routes*, *Segments*) formats content in a horizontal `Row` containing a `weight(1f)` text column and trailing action slot.
  • Verify trailing slot renders `Icons.Default.Refresh` with `size = 20.dp` and `colorScheme.primary` tint when idle.
  3. *Syncing State Circular Progress Indicator (`TST-UI-488.3`, `StravaSettingsDialogContractTest.kt`)*:
  • Verify when syncing state is true, the trailing action slot renders `CircularProgressIndicator` with `size = 20.dp` and `strokeWidth = 2.dp`.
  4. *Tier 1 Targeted Unit Test Regression (`TST-UI-488.4`)*:
  • Execute targeted test suite (`com.atrainingtracker.trainingtracker.ui.settings.strava.*`, `com.atrainingtracker.trainingtracker.ui.components.*`, `com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest`, `com.atrainingtracker.trainingtracker.build.*`) asserting 100% pass rate.

### TST-UI-489: **Route Details Climb Badges, Header Iconography, Section Deduplication & Localized Grade Formatting Test Suite** [Verified]
- **Requirement(s)**: `REQ-UI-489`
- **Component**: `ElevationProfile.kt`, `RouteSummaryHeader.kt`, `RouteOnMapScreen.kt`, `RouteSegmentsBreakdownSection.kt`, `ClimbDetector.kt`, `ElevationProfileLayoutTest.kt`, `ElevationProfileClimbBadgeClippingTest.kt`, `ClimbDetectorTest.kt`, `RouteDetailsContractTest.kt`
- **Jira**: `ATT-5853`
- **Test Description**:
  1. *Elevation Profile Top Padding & Clearance Calibration Contract (`TST-UI-489.1`, `ElevationProfileLayoutTest.kt`, `ElevationProfileClimbBadgeClippingTest.kt`)*:
  • Verify `ElevationProfile.kt` declares `val topPadding = if (showZoomControls) 24.dp else 16.dp`.
  • Verify `totalCanvasHeight` adds `8.dp` when `showZoomControls == true` and `fixedHeight == null`.
  • Verify summit badge clearance math ensures at least 24dp clearance at 1.0x font scale without container clipping.
  2. *Route Summary Header Iconography Contract (`TST-UI-489.2`, `RouteDetailsContractTest.kt`)*:
  • Verify `RouteSummaryHeader.kt` binds `R.drawable.ic_ascent` for elevation gain and `R.drawable.ic_altitude` for climb count.
  3. *Section Header Deduplication Contract (`TST-UI-489.3`, `RouteDetailsContractTest.kt`)*:
  • Verify `RouteClimbsBreakdownSection` and `RouteSegmentsBreakdownSection` accept `showHeader: Boolean = true`.
  • Verify `RouteOnMapScreen.kt` passes `showHeader = !(climbs.isNotEmpty() && matchedSegments.isNotEmpty())` when dual tabs are active.
  • Verify section title `Text` is omitted when `showHeader == false`.
  4. *Locale-Aware Climb Title Grade Formatting Unit Tests (`TST-UI-489.4`, `ClimbDetectorTest.kt`)*:
  • Verify `ClimbDetector.buildClimb` uses `Locale.getDefault()` for grade percentage formatting.
  • Verify German locale produces localized decimal comma (e.g. `6,0%`) and US locale produces decimal point (e.g. `6.0%`).
  5. *Tier 1 Targeted Unit Test Regression (`TST-UI-489.5`)*:
  • Execute targeted test suite (`com.atrainingtracker.trainingtracker.climbs.*`, `com.atrainingtracker.trainingtracker.ui.map.*`, `com.atrainingtracker.trainingtracker.ui.routes.*`, `com.atrainingtracker.trainingtracker.localization.TranslationParityTest`, `com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest`, `com.atrainingtracker.trainingtracker.build.*`) asserting 100% pass rate.

### TST-UI-490: **Workout Cluster Trend Chart & Dashboard Polish Verification Suite** [Verified]
- **Requirement(s)**: `REQ-UI-490`
- **Component**: `WorkoutClusterTrendChartCard.kt`, `WorkoutClusterHeatmapScreen.kt`, `DistanceFormatter.java`, `AltitudeFormatter.java`, `WorkoutClusterTrendMath.kt`, `WorkoutClusterTrendCardPolishTest.kt`
- **Jira**: `ATT-5852`
- **Test Description**:
  1. *Canvas Text Separation & Left Gutter Contract Tests (`TST-UI-490.1`, `WorkoutClusterTrendCardPolishTest.kt`)*:
  • Verify `WorkoutClusterTrendChartCard.kt` positions Y-axis labels in dedicated left gutter (`leftPadding = 48.dp.toPx()`).
  • Verify `yAxisBottomLabel` and `xAxisStartLabel` do not collide horizontally or vertically.
  2. *Average Badge Legibility Contract Tests (`TST-UI-490.2`, `WorkoutClusterTrendCardPolishTest.kt`)*:
  • Verify `WorkoutClusterTrendChartCard.kt` draws a rounded surface background badge behind `formattedAverage`.
  3. *Time-Range Filtering & Header Deduplication Contract Tests (`TST-UI-490.3`, `WorkoutClusterTrendCardPolishTest.kt`)*:
  • Verify redundant `📈 Leistungsverlauf` heading is removed from the card header row.
  • Verify time-range filter chips (`1J | 3J | Alle`) are bound and filter points relative to latest workout timestamp.
  • Verify fallback to full dataset when $< 2$ points match time-range.
  4. *Thousands Grouping Formatter Unit Tests (`TST-UI-490.4`, `DistanceFormatterTest.java`, `AltitudeFormatterTest.java`)*:
  • Verify `DistanceFormatter.format_with_units(4_226_000)` formats as `"4.226 km"` in German locale and `"4,226 km"` in US locale.
  • Verify `AltitudeFormatter.format_with_units(136_730)` formats as `"136.730 m"` in German locale and `"136,730 m"` in US locale.
  5. *9-Language Localization & Format Audit (`TST-UI-490.5`, `TranslationParityTest.kt`)*:
  • Verify `cluster_trend_range_all`, `cluster_trend_range_1y`, and `cluster_trend_range_3y` exist across all 9 locales with zero inline emojis (`ATT-5400`).
  6. *Tier 1 Targeted Unit Test Regression (`TST-UI-490.6`)*:
  • Execute targeted test suite (`com.atrainingtracker.trainingtracker.ui.clusters.*`, `com.atrainingtracker.banalservice.sensor.formater.*`, `com.atrainingtracker.trainingtracker.localization.TranslationParityTest`, `com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest`, `com.atrainingtracker.trainingtracker.build.*`) asserting 100% pass rate.

### TST-UI-491: **MapDetailLayout Floating Controls Status Bar Clearance Verification Suite** [Verified]
- **Requirement(s)**: `REQ-UI-491`
- **Component**: `MapDetailLayout.kt`, `MapDetailLayoutCollapsingHeaderContractTest.kt`, `MapDetailLayoutTest.kt`
- **Jira**: `ATT-5851`
- **Test Description**:
  1. *Dynamic Floating Controls Clearance Math Contract (`TST-UI-491.1`, `MapDetailLayoutCollapsingHeaderContractTest.kt`)*:
  • Verify `MapDetailLayout.kt` computes `statusBarHeightPx` conditioned on `useStatusBarsPadding`.
  • Verify dynamic `floatingControlsTopOffsetDp` computation `(statusBarHeightPx - currentTopPaddingPx).coerceAtLeast(0).toDp()`.
  • Verify `floatingControlsTopOffsetDp` evaluates to `0.dp` when `useStatusBarsPadding == false`.
  2. *Floating Controls Container Padding Contract (`TST-UI-491.2`, `MapDetailLayoutCollapsingHeaderContractTest.kt`)*:
  • Verify `overlay()` and the floating Share button Surface inside `mapBox` are enclosed in a container applying `Modifier.fillMaxSize().padding(top = floatingControlsTopOffsetDp)`.
  • Verify `ATrainingTrackerMap` remains outside this padding container, preserving edge-to-edge bleed.
  3. *Unit Tests for Clearance Offset Calculations (`TST-UI-491.3`, `MapDetailLayoutCollapsingHeaderContractTest.kt`)*:
  • Test offset calculation when header is fully expanded: `currentTopPaddingPx > statusBarHeightPx` yields `0.dp` additional offset.
  • Test offset calculation when header is fully collapsed: `currentTopPaddingPx == 0` yields `statusBarHeightPx.toDp()`.
  • Test offset calculation when `useStatusBarsPadding == false`: yields `0.dp` regardless of scroll offset.
  4. *Tier 1 Targeted Unit Test Regression (`TST-UI-491.4`)*:
  • Execute targeted test suite (`com.atrainingtracker.trainingtracker.ui.map.*`, `com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest`, `com.atrainingtracker.trainingtracker.build.*`) asserting 100% pass rate.

### TST-UI-492: **Equipment & Sensors UI Polish Verification Suite** [Verified]
- **Requirement(s)**: `REQ-UI-492`
- **Component**: `EquipmentTabsScreen.kt`, `BikeTypeSelector.kt`, `EditEquipmentDialog.kt`, `EditDeviceDialog.kt`, `DeviceData.kt`, `EquipmentSensorsUiPolishContractTest.kt`
- **Jira**: `ATT-5847`
- **Test Description**:
  1. *Strava Gear Name Deduplication Contract (`TST-UI-492.1`, `EquipmentSensorsUiPolishContractTest.kt`)*:
  • Verify `EquipmentTabsScreen.kt` checks equality between `item.stravaName` and `item.name`.
  • Verify inline `StravaOriginalLogo` is rendered in title row when identical.
  • Verify subtitle row is suppressed when identical and shown when different.
  2. *Usage Date & Frame Type Localization Contract (`TST-UI-492.2`, `EquipmentSensorsUiPolishContractTest.kt`)*:
  • Verify `UsageItem` formats dates via `DateTimeUtils.formatIsoDate(date)`.
  • Verify `BikeTypeSelector.kt` and `EquipmentTabsScreen.kt` use string resources `equipment_bike_type_mtb`, `cross`, `road`, `tt`.
  3. *Spelling & Placeholder Harmonization Contract (`TST-UI-492.3`, `EquipmentSensorsUiPolishContractTest.kt`)*:
  • Verify `values-de/strings.xml` defines `Radtyp` and `Radtyp auswählen`.
  • Verify `EditEquipmentDialog.kt` binds `equipment_no_sports_linked` ("Keine" / "None") for sensor placeholder.
  4. *Sensor Detail Status LED, Capitalization & Dropdown Contract (`TST-UI-492.4`, `EquipmentSensorsUiPolishContractTest.kt`)*:
  • Verify `EditDeviceDialog.kt` binds LED dot color to `isConnected` matching `DeviceStatusRow`.
  • Verify `DeviceData.kt:raw2UiDeviceData` capitalizes `manufacturer` (`replaceFirstChar`).
  • Verify `EquipmentSection` renders `OutlinedTextField` with dropdown affordance.
  5. *9-Language Localization Parity Audit (`TST-UI-492.5`, `TranslationParityTest.kt`)*:
  • Verify `equipment_bike_type_mtb`, `equipment_bike_type_cross`, `equipment_bike_type_road`, and `equipment_bike_type_tt` exist with identical keys across all 9 locales.
  6. *Tier 1 Targeted Unit Test Regression (`TST-UI-492.6`)*:
  • Execute targeted test suite (`com.atrainingtracker.trainingtracker.ui.equipment.*`, `com.atrainingtracker.banalservice.ui.devices.*`, `com.atrainingtracker.trainingtracker.localization.TranslationParityTest`, `com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest`, `com.atrainingtracker.trainingtracker.build.*`) asserting 100% pass rate.

### TST-UI-2789: **Cards & Graphs UI Deduplication Verification Suite** [Verified]
- **Requirement(s)**: `REQ-UI-2789`
- **Component**: `TelemetryChartCanvasHelpers.kt`, `ElevationProfile.kt`, `TelemetryMetricGraph.kt`, `PathPreviewMap.kt`, `WorkoutClusterComponents.kt`, `TelemetryChartCanvasHelpersTest.kt`, `ClusterThumbnailPathPreviewDelegationTest.kt`, `PathPreviewMapBoundsTest.kt`, `ClusterItemThumbnailComposeTest.kt`
- **Verification Evidence**: Gate 4 `ATT-9433` @ `2b4277ca` (`277/277` PASS across 61 suites, `4/4` linters, Light/Dark `Rule 8.5` PNGs): `TelemetryChartCanvasHelpersTest` (`7/7`: `computeMarginalBarVerticalBounds_filtersOutOfDomainAndClampsWithoutZeroHeightStacking`, `drawMarginalHistogramBars_rendersUnconditionalTrackAndGatesForegroundOnDuration`, `drawXAxisTicks_separatesGridlinesFromLabelCollisionGating`, `captureChartsMarginalHistogramsAndTicksLightDarkPng_forRule85`), `ClusterThumbnailPathPreviewDelegationTest` (`4/4`: `buildClusterThumbnailPolylines_preservesHeatmapAlphaAndAuthoritativeZIndex`, `pathPreviewMap_compositeBoundsIncludesAdditionalPathsAndPaddingAffectsCamera`, `clusterItem_longClickOnPathPreviewMapThumbnailOpensContextMenu`, `captureClusterThumbnailsViaPathPreviewMapLightDarkPng_forRule85`), `PathPreviewMapBoundsTest` (`8/8`), `ClusterItemThumbnailComposeTest` (`5/5`), `ArchitectureConformanceContractTest` (`11/11`), `ThemeColorCentralizationAuditTest` (`5/5`).
- **Jira**: `ATT-6041`
- **Test Description**:
  1. *Histogram & X-Axis Tick Characterization (`TST-UI-2789.1`, `TelemetryChartCanvasHelpersTest.kt`)*: Verify Rules 69/70 clamping (`rectHeight <= 0f -> continue`), `0.15f` unconditional tracks, `durationSec > 0L` fg gating, `drawRect`/`drawRoundRect`, `1.0f`/`0.85f` alpha, `onBinDrawn`, and `onTickInBounds` vs `onLabelRendered` across time/distance steps.
  2. *`PathPreviewMap` & Cluster Thumbnail Parity (`TST-UI-2789.2`, `PathPreviewMapBoundsTest.kt`, `ClusterItemThumbnailComposeTest.kt`)*: Verify `boundsPaddingPx` in camera seeding + move, `additionalPaths` composite bounds without `actualStart`/`actualEnd` fallback pollution, `onMapLongClick` overlay, stub-polyline inspection fallback, `175×120 dp`/`360 dp` bounds, and untouched interactive `ClusterNamingDialog`.
  3. *`Rule 8.5` PNG Visual Inspection & Targeted Regression (`TST-UI-2789.3`)*: Capture dedicated Light/Dark `docs/engineering/walkthroughs/img/ATT-6041_*.png` via Robolectric (leaving historical PNGs untouched), inspect via `read_file` against the 4-point checklist (+ live `emulator-5554` check in Stage 5/6), and run Tier-1 targeted `ui.map.*`, `ui.clusters.*`, `WorkoutLapsIconContractTest`, `HardcodedStringsAndUnitsContractTest`, `ThemeColorCentralizationAuditTest`, `ArchitectureConformanceContractTest`, and `UiReflectionOnlyTestAuditContractTest`.

### TST-UI-493: **ClusterItem & Filter (Lieblingsstrecken) Presentation Polish Verification Suite** [Verified]
- **Target Release**: `V5.0.0`
- **Requirement(s)**: `REQ-UI-493` (`REQ-UI-493.1`–`REQ-UI-493.4`)
- **Component**: `WorkoutClusterComponents.kt`, `WorkoutClustersViewModel.kt`, `WorkoutClusterRepository.kt`, `FilterBottomSheetScaffold.kt`, `ClusterItemAndFilterPolishTest.kt`, `ClusterItemThumbnailComposeTest.kt`, `WorkoutClusterRepositoryTest.kt`
- **Jira**: `ATT-5846`, `ATT-8748`
- **Test Description**:
  1. *Hit Count Display Healing & Reconciliation Contract Tests (`TST-UI-493.1`, `ClusterItemAndFilterPolishTest.kt`, `WorkoutClusterRepositoryTest.kt`)*:
  • Verify `effectiveWorkoutCount` resolves `maxOf(cluster.hitCount, stats?.workoutCount ?: 0)` so clusters with `hitCount = 0` but `workoutCount = 3` display 3 recordings.
  • Verify `WorkoutClusterRepository.loadAllClustersLightweight()` heals `hitCount` from summary stats when cluster has 0.
  2. *Bidirectional Location Resolution Contract Tests (`TST-UI-493.2`, `ClusterItemAndFilterPolishTest.kt`)*:
  • Verify identical start and destination location displays `"Start & Ziel: [Location]"`.
  • Verify distinct start and destination locations display `"[Start] → [Ziel]"`.
  • Verify single start or destination falls back to `"Start: ..."` or `"Ziel: ..."`.
  3. *Landscape Map Thumbnail, Narrow-Screen Guard, Camera Framing & Equipment Ellipsis Tests (`TST-UI-493.3`, `ClusterItemThumbnailComposeTest.kt`, `ClusterItemAndFilterPolishTest.kt`)*:
  • **Behavioral Compose UI Tests (`createComposeRule()`, ZERO `src/main` reads per `REQ-PRO-049.1`, `ClusterItemThumbnailComposeTest.kt`)**: Render `ClusterItem` and `UnclusteredWorkoutItem` at `411.dp` and `360.dp` widths; assert the preview thumbnail renders at `175.dp × 120.dp` with `12.dp` horizontal spacing on `411.dp`, clamps width cleanly on `360.dp` so metadata retains `>= 160.dp` without throwing `IntrinsicSize.Min` / `SubcomposeLayout` exceptions, preserves `maxLines = 1` / `TextOverflow.Ellipsis` equipment truncation, uses the named `16–20 px` bounds padding constant (`THUMBNAIL_BOUNDS_PADDING_PX`), and safely handles camera bounds framing (`try/catch` + optional bounds-center seeding).
  • **Legacy Baseline Alignment (`ClusterItemAndFilterPolishTest.kt`)**: Update `clusterComponents_rendersLandscapeThumbnailAndConstrainedEquipment` thumbnail size expectation to `175.dp × 120.dp` while preserving `app/source-reading-baseline.txt` (`SourceTextReadingAuditContractTest` ceiling `105`).
  • **Rule 8.5 Light/Dark `@Preview` Visual Evidence**: Capture combined Light & Dark `@Preview` screenshot `docs/engineering/walkthroughs/img/ATT-8748_lieblingsstrecken_preview.png` and inspect via `read_file` against the 4-point checklist.
  4. *Filter Sheet Reset Button Single-Line & 9-Locale Parity Tests (`TST-UI-493.4`, `ClusterItemAndFilterPolishTest.kt`, `TranslationParityTest.kt`)*:
  • Verify `R.string.filter_reset` exists across all 9 locales (EN, DE, ES, FR, IT, JA, NL, PL, PT) with zero inline emojis (`ATT-5400`).
  • Verify `FilterBottomSheetScaffold` binds `filter_reset` with `maxLines = 1`.
  • **Localization Parity (`ATT-8748`)**: `N/A - no UI strings` (zero `values*/strings*.xml` modifications).
  5. *Tier 1 Targeted Unit Test Regression (`TST-UI-493.5`)*:
  • Execute targeted test suite (`ui.clusters.*`, `MapPreviewLiteModeTest`, `DarkMapAntiFlashOverlayTest`, `DarkMapStyleResolutionTest`, `GlobalDeleteContextMenuAuditTest`, `SourceTextReadingAuditContractTest`) asserting 100% pass rate.

### TST-UI-494: **ElevationProfile Y-Axis Grid Clearance & Zero-Altitude Profile Suppression Verification Suite** [Verified]
- **Requirement(s)**: `REQ-UI-494`
- **Component**: `ElevationProfile.kt`, `RouteItem.kt`, `ElevationProfileClearanceContractTest.kt`, `RouteItemZeroElevationContractTest.kt`
- **Jira**: `ATT-5845`
- **Test Description**:
  1. *Y-Axis Intermediate Label Baseline Clearance Guard Contract Tests (`TST-UI-494.1`, `ElevationProfileClearanceContractTest.kt`)*:
  • Verify that when `labelBaselineY = y + 10f` is within `textPaint.textSize * 1.35f` of `height` (e.g. `400` near `383 m`), the intermediate tick label is suppressed.
  • Verify that when clearance from `height`, `highlightPaint.textSize`, and adjacent ticks exceeds `textPaint.textSize * 1.35f`, the tick label is drawn.
  2. *Right-Aligned Intermediate Elevation Labels Contract Tests (`TST-UI-494.2`, `ElevationProfileClearanceContractTest.kt`)*:
  • Verify intermediate altitude text X position is dynamically computed as `-textPaint.measureText(altText) - 10f` rather than hardcoded `-110f`.
  3. *Zero-Altitude Elevation Profile Suppression in RouteItem Contract Tests (`TST-UI-494.3`, `RouteItemZeroElevationContractTest.kt`)*:
  • Verify `RouteItem` evaluates `hasElevationData = pathPoints.any { it.altitude != null && !it.altitude.isNaN() && Math.abs(it.altitude) > 1e-3 } || summary.elevationGain > 0.0`.
  • Verify `ElevationProfile` is hidden when a route has 0 altitude data and 0 elevation gain (e.g. `bike2work`).
  • Verify `ElevationProfile` is rendered when a route has authentic altitude points or positive elevation gain.
  4. *ATT-5853 Invariant & Layout Non-Regression Tests (`TST-UI-494.4`, `ElevationProfileLayoutTest.kt`, `ElevationProfileClimbBadgeClippingTest.kt`)*:
  • Verify `ATT-5853` top padding (`24.dp` / `16.dp`) and climb badge clearance tests pass with 0 regressions.
  5. *Tier 1 Targeted Unit Test Regression (`TST-UI-494.5`)*:
  • Execute targeted test suite (`ui.map.*`, `ui.routes.*`, `ui.theme.ThemeColorCentralizationAuditTest`, `build.*`) asserting 100% pass rate.

### TST-UI-495: **AthleteProfileScreen Filter Pill Layout, Decimal Localization, PMC Card Heights & Time-Range Selector Suite** [Verified]
- **Requirement(s)**: `REQ-UI-495`
- **Component**: `AthleteProfileScreen.kt`, `AthleteMetricsPlotMath.kt`, `AthleteProfileViewModel.kt`
- **Jira**: `ATT-5844`
- **Test Description**:
  1. *Proportional Pill Width Allocation Contract Test (`TST-UI-495.1`)*:
  • Verify that label length proportional weighting (`coerceIn(3, 9)`) allocates sufficient width for `Gewicht` and `Ruhe-HF` alongside short abbreviations (`FTP`, `CP`, `W'`) without ellipsis truncation.
  2. *Active Locale Decimal Formatting Contract Test (`TST-UI-495.2`)*:
  • Verify `formatWeightValue`, `formatWPrimeValue`, and `formatPowerToWeightValue` format with commas under `Locale.GERMANY` (`80,0`, `20,0`, `3,44`) and dots under `Locale.US` (`80.0`, `20.0`, `3.44`).
  • Verify decimal points in PMC stat badge format strings conform to `Locale.getDefault()`.
  3. *PMC Card Heights & Intrinsic Layout Contract Test (`TST-UI-495.3`)*:
  • Verify `PmcStatBadge` applies `.fillMaxHeight()` and enclosing `Row` layouts use `Modifier.height(IntrinsicSize.Min)` per Design Guideline 5.14.
  4. *PMC Time-Range Filtering & EWMA Preservation Test (`TST-UI-495.4`)*:
  • Verify `PerformanceManagementChartCard` defaults to `MetricsTimeRange.SIX_MONTHS`.
  • Verify selecting a range slices plotted points correctly without modifying historical EWMA calculations.
  5. *9-Language Localization & Specifier Audit (`TST-UI-495.5`)*:
  • Verify 100% parity across all 9 locales (EN, DE, ES, FR, IT, JA, NL, PL, PT) for all athlete profile strings via `TranslationParityTest`.
  6. *Tier 1 Targeted Unit Test Regression (`TST-UI-495.6`)*:
  • Execute targeted test suite (`ui.athleteprofile.*`, `ui.theme.ThemeColorCentralizationAuditTest`, `build.*`) asserting 100% pass rate.

### TST-UI-496: **Navigation Drawer Header Height Compactness & Vertical Clearance Verification Suite** [Verified]
- **Requirement(s)**: `REQ-UI-496`
- **Component**: `AppNavigationDrawer.kt`, `AppNavigationDrawerContractTest.kt`
- **Jira**: `ATT-5839`, `ATT-6216`
- **Test Description**:
  1. *Drawer Header Height & Alignment Contract Test (`TST-UI-496.1`)*:
  • Verify `AppNavigationDrawer.kt` source declares `.height(76.dp + statusBarHeight)` in `DrawerHeader()`.
  • Verify `menu_header_background` image is anchored with `Alignment.BottomCenter` and `ContentScale.FillWidth`.
  2. *Rule 72 Branding Row & Icon Invariant Contract Test (`TST-UI-496.2`)*:
  • Verify `AppNavigationDrawer.kt` branding row specifies `padding(start = 16.dp, end = 16.dp, top = statusBarHeight, bottom = 24.dp)`.
  • Verify launcher icon slot retains `Modifier.size(46.dp)` and `scaleX = 1.81f; scaleY = 1.81f`.
  • Verify single-line title layout without extra subtitle/pill rows below `"a Training Tracker"`.
  3. *Tier 1 Targeted Unit Test Regression (`TST-UI-496.3`)*:
  • Execute targeted test suite (`ui.navigation.*`, `ui.theme.ThemeColorCentralizationAuditTest`, `build.*`) asserting 100% pass rate.

### TST-UI-497: **EditWorkoutScreen Hint Localization, Punctuation Normalization, Label Capitalization & Strava Row Layout Verification Suite** [Verified]
- **Requirement(s)**: `REQ-UI-497`
- **Component**: `EditWorkoutScreen.kt`, `res/values*/strings.xml`
- **Jira**: `ATT-5838`
- **Test Description**:
  1. *9-Locale Hint, Sport & Strava Upload String Parity Contract Test (`TST-UI-497.1`)*:
  • Verify `hint_workout_description`, `hint_workout_goal`, `hint_workout_method` are localized in German as `Beschreibung`, `Ziel`, `Methode` and capitalized across all 9 locales.
  • Verify `Sport` contains zero trailing colons across all 9 locales.
  • Verify `stravaUpload` is capitalized across all 9 locales.
  2. *Strava Row Component Ordering & Non-Destructive Alpha Contract Test (`TST-UI-497.2`)*:
  • Verify in `EditWorkoutScreen.kt` source that `Checkbox` precedes `Icon` in the Strava row.
  • Verify Strava logo uses `tint = Color.Unspecified` and applies `Modifier.alpha` for disabled dimming instead of `Color.Gray`.
  3. *9-Language Localization & Translation Parity Suite (`TST-UI-497.3`)*:
  • Execute `TranslationParityTest` verifying 100% parity across all 9 supported locales with 0 missing keys.
  4. *Tier 1 Targeted Unit Test Regression (`TST-UI-497.4`)*:
  • Execute targeted test suite (`ui.aftermath.editworkout.*`, `ui.theme.ThemeColorCentralizationAuditTest`, `build.*`) asserting 100% pass rate.

### TST-UI-498: **WorkoutDetailTabbedContent Header Click Decoupling & MapDetailLayout Sheet Chrome Suppression Verification Suite** [Verified]
- **Requirement(s)**: `REQ-UI-498`
- **Component**: `WorkoutDetailTabbedContent.kt`, `MapDetailLayout.kt`, `TrackOnMapScreen.kt`
- **Jira**: `ATT-5835`
- **Test Description**:
  1. *WorkoutDetailTabbedContent Header Click Decoupling Contract Test (`TST-UI-498.1`)*:
  • Verify that `WorkoutDetailTabbedContent.kt` binds `onClicked = null` on `WorkoutHeader`, reserving edit execution strictly to the dedicated `onEditWorkout` pencil icon button.
  2. *MapDetailLayout isBottomSheet Parameter & Drag Handle Gating Contract Test (`TST-UI-498.2`)*:
  • Verify that `MapDetailLayout` supports `isBottomSheet: Boolean` parameter with default `!useStatusBarsPadding`.
  • Verify that `MinimumDragHandle()` and `BottomSheetDesign.SheetShape` are rendered strictly when `isBottomSheet == true`.
  • Verify that when `isBottomSheet == false`, the drag handle is suppressed and `shape = RectangleShape` is used.
  3. *Full-Screen Tabbed Callers isBottomSheet Parity Contract Test (`TST-UI-498.3`)*:
  • Verify that all `MapDetailLayout` invocations in `WorkoutDetailTabbedContent.kt` and `TrackOnMapScreen.kt` explicitly pass `isBottomSheet = false`.
  • Verify modal bottom sheets in `MapScreenWithTrack.kt` and `LiveSegmentSheet.kt` maintain `isBottomSheet = true` (or default to true when `useStatusBarsPadding == false`).
  4. *Tier 1 Targeted Unit Test Regression (`TST-UI-498.4`)*:
  • Execute targeted test suite (`ui.aftermath.*`, `ui.theme.ThemeColorCentralizationAuditTest`, `build.*`) asserting 100% pass rate.

### TST-UI-499: **WorkoutSummary & WorkoutSectionType: Reorderable WorkoutDetails & Photos-First Ordering Verification Suite** [Verified]
- **Requirement(s)**: `REQ-UI-499`, `REQ-UI-255`
- **Component**: `WorkoutSectionType.kt`, `WorkoutDetailTabConfig.kt`, `MyPreferenceManager.kt`, `WorkoutSummary.kt`, `WorkoutMasksAndCardsSection.kt`, `TrackOnMapScreen.kt`
- **Jira**: `ATT-6213`
- **Test Description**:
  1. *Domain Model & Enum Parity Contract Test (`WorkoutSectionOrderPersistenceTest.kt`)*:
  • Verify `WorkoutSectionType` contains `DETAILS(R.string.workout_detail_section_details)`.
  • Verify `WorkoutSectionType.DEFAULT_ORDER` contains 12 elements with `DETAILS` at index 0.
  • Verify bidirectional mapping between `WorkoutSectionType.DETAILS` and `WorkoutDetailSectionType.DETAILS`.
  2. *Backwards-Compatible Preference Migration Tests (`WorkoutSectionOrderPersistenceTest.kt`)*:
  • Verify `fromSerializedString` without `DETAILS` inserts `DETAILS` at index 1 when `PHOTOS` is at index 0.
  • Verify `fromSerializedString` without `DETAILS` inserts `DETAILS` at index 0 when `PHOTOS` is not at index 0.
  • Verify round-trip serialization and deserialization with `DETAILS`.
  3. *Summary Rendering & Dynamic Ordering Structural Contract (`WorkoutSummaryDynamicOrderContractTest.kt` & `WorkoutSummarySectionsTest.kt`)*:
  • Verify `WorkoutSummary.kt` renders `WorkoutDetails` within the dynamic section loop under `WorkoutSectionType.DETAILS` gated by `showDetails`.
  • Verify `WorkoutHeader` remains the sole anchor preceding the dynamic loop.
  • Verify `WorkoutCardSectionPreferences` contains `showDetails` (defaulting to `true`).
  4. *Settings UI Modularity & Line Limit Audit*:
  • Verify `WorkoutMasksAndCardsSection.kt` maps `WorkoutSectionType.DETAILS` to `MatrixFeatureRow` with `R.string.workout_detail_section_details`.
  • Verify `WorkoutMasksAndCardsSection.kt` total line count remains strictly `< 400` lines.
  5. *9-Language Localization Parity Audit (`TranslationParityTest`)*:
  • Verify `R.string.workout_detail_section_details` exists across all 9 locales (EN, DE, ES, FR, IT, JA, NL, PL, PT) with 100% translation parity and zero formatting mismatches.
  6. *Tier 1 Targeted Unit Test Regression Suite*:
  • Command: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.aftermath.*" --tests "com.atrainingtracker.trainingtracker.settings.*" --tests "com.atrainingtracker.trainingtracker.build.*"`

### TST-UI-500: **MapDetailLayout Unified Top-End Floating Actions Stacking & Adaptive Layers Placement Verification Suite** [Verified]
- **Requirement(s)**: `REQ-UI-500`, `REQ-UI-308`, `REQ-UI-491`
- **Component**: `MapDetailLayout.kt`, `WorkoutDetailTabbedContent.kt`, `TrackOnMapScreen.kt`, `RouteOnMapScreen.kt`, `MapDetailLayoutTest.kt`
- **Jira**: `ATT-6214`
- **Test Description**:
  1. *MapDetailLayout Top-End Actions Column Stacking Contract Test (`TST-UI-500.1`)*:
  • Verify `MapDetailLayout.kt` declares `topEndActions: @Composable (ColumnScope.() -> Unit)? = null`.
  • Verify `MapDetailLayout.kt` encapsulates `showFloatingShareButton` and `topEndActions` inside a single `Column` with `Alignment.TopEnd`, `padding(16.dp)`, and `verticalArrangement = Arrangement.spacedBy(16.dp)`.
  2. *WorkoutDetailTabbedContent mapTopEndActions Delegation Contract Test (`TST-UI-500.2`)*:
  • Verify `WorkoutDetailTabbedContent.kt` declares `mapTopEndActions: @Composable (ColumnScope.() -> Unit)? = null` and forwards it to `MapDetailLayout` across both single-pane and split-pane branches.
  3. *TrackOnMapScreen & RouteOnMapScreen Zero Hardcoded 76dp Offset Audit (`TST-UI-500.3`)*:
  • Verify in `TrackOnMapScreen.kt` source that the Layers button uses `mapTopEndActions` and contains zero occurrences of `padding(top = 76.dp)`.
  • Verify in `RouteOnMapScreen.kt` source that the Layers button uses `topEndActions` and contains zero occurrences of `padding(top = 76.dp)`.
  4. *Tier 1 Targeted Unit Test Regression Suite (`TST-UI-500.4`)*:
  • Execute targeted test suite (`ui.map.*`, `ui.aftermath.*`, `ui.routes.*`, `ui.theme.ThemeColorCentralizationAuditTest`, `build.*`) asserting 100% pass rate.

### TST-UI-501: **SupporterScreen & SupporterComponents: Tier Hierarchy & Lower Tier De-Emphasis Verification Suite** [Verified]
- **Requirement(s)**: `REQ-UI-501`
- **Component**: `SupporterComponents.kt`, `SupporterScreen.kt`, `SupporterPlanCardHierarchyTest.kt`, `TranslationParityTest.kt`
- **Jira**: `ATT-6286`
- **Test Description**:
  1. *SupporterPlanCard Visual Hierarchy & State Resolution Contract Test (`TST-UI-501.1`)*:
  • Verify `SupporterPlanCard` button type (`Button` vs. `OutlinedButton`), label string resource (`supporter_plan_button_switch` vs. `supporter_plan_button_active` vs. `supporter_plan_button_support`), and container/border styling across all tier permutations.
  2. *SupporterScreen Effective Tier Propagation Contract Test (`TST-UI-501.2`)*:
  • Verify in `SupporterScreen.kt` that `effectiveTier` is passed down to every `SupporterPlanCard`.
  3. *9-Language Localization & Translation Parity Suite (`TST-UI-501.3`)*:
  • Execute `TranslationParityTest` asserting that `supporter_plan_button_switch` exists in `strings_monetization.xml` across all 9 locales with 100% parity.
  4. *Tier 1 Targeted Unit Test Regression (`TST-UI-501.4`)*:
  • Execute targeted test suite (`ui.settings.supporter.*`, `ui.branding.*`, `ui.theme.ThemeColorCentralizationAuditTest`, `build.*`) asserting 100% pass rate.

### TST-UI-502: **SupporterScreen PioneerStatusBanner Neutral Copy Verification Suite** [Verified]
- **Target Release**: `V5.0.0`
- **Requirement(s)**: `REQ-UI-502`
- **Component**: `SupporterComponents.kt`, `res/values*/strings.xml`, `SupporterCopyContractTest.kt`, `TranslationParityTest.kt`
- **Jira**: `ATT-6290`
- **Test Description**:
  1. *Pioneer Copy Contract & ANT+ Elimination Test (`TST-UI-502.1`)*:
  • Verify in `SupporterCopyContractTest.kt` across all 9 locales (`values`, `de`, `es`, `fr`, `it`, `ja`, `nl`, `pl`, `pt`) that `pioneer_thank_you_body` exists, has non-blank content, contains zero references to "ANT+" (case-insensitive), contains zero decorative emojis (`ATT-5400`), and contains zero format specifiers.
  • Verify exact appreciative wording match in German (`values-de`) and English (`values`).
  2. *9-Language Localization & Translation Parity Suite (`TST-UI-502.2`)*:
  • Execute `TranslationParityTest` asserting that `pioneer_thank_you_body` exists in all 9 supported locales with 100% parity.
  3. *Tier 1 Targeted Unit Test Regression (`TST-UI-502.3`)*:
  • Execute targeted test suite (`ui.settings.supporter.*`, `ui.branding.*`, `ui.theme.ThemeColorCentralizationAuditTest`, `build.*`) asserting 100% pass rate.

### TST-UI-503: **DisplaySettingsDialog Display Option Subtitles Verification Suite** [Verified]
- **Target Release**: `V5.0.0`
- **Requirement(s)**: `REQ-UI-503`
- **Component**: `DisplaySettingsDialog.kt`, `res/values*/strings.xml`, `DisplaySettingsTest.kt`, `TranslationParityTest.kt`
- **Jira**: `ATT-6282`
- **Test Description**:
  1. *Display Option Subtitle Resource Contract Test (`TST-UI-503.1`)*:
  • Verify in `DisplaySettingsTest.kt` that `forcePortraitSummary`, `prefsKeepScreenOnSummary`, and `prefsNoUnlockingSummary` exist across all 9 locales, are non-blank, contain zero format specifiers, and contain zero decorative emojis (`ATT-5400`).
  • Verify that `prefsKeepScreenOnTitle` and `prefsNoUnlockingTitle` start with a capitalized letter across all applicable locales.
  2. *9-Language Localization Parity Suite (`TST-UI-503.2`)*:
  • Execute `TranslationParityTest` asserting that all 3 new string resources exist across all 9 supported locales with 100% parity.
  3. *Tier 1 Targeted Unit Test Regression (`TST-UI-503.3`)*:
  • Execute targeted test suite (`ui.settings.display.*`, `ui.branding.*`, `ui.theme.ThemeColorCentralizationAuditTest`, `build.*`) asserting 100% pass rate.

### TST-UI-504: **SupporterPlanCard Compact Button Padding & Title Truncation Verification Suite** [Verified]
- **Target Release**: `V5.0.0`
- **Requirement(s)**: `REQ-UI-504`
- **Component**: `SupporterComponents.kt`, `SupporterPlanCardHierarchyTest.kt`
- **Jira**: `ATT-6323`
- **Test Description**:
  1. *Button Content Padding & Single-Line Title Contract Test (`TST-UI-504.1`)*:
  • Verify in `SupporterPlanCardHierarchyTest.kt` that `SupporterComponents.kt` defines `contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)` across `OutlinedButton` and `Button` instances in `SupporterPlanCard`.
  • Verify that the card tier title `Text` composable declares `maxLines = 1` and `overflow = TextOverflow.Ellipsis`.
  2. *Tier Hierarchy & Action Button Integrity Test (`TST-UI-504.2`)*:
  • Execute existing hierarchy tests verifying `PlanCardHierarchyState` resolution, recommendation badge suppression, and button label mappings across all supported tiers.
  3. *Tier 1 Targeted Unit Test Regression (`TST-UI-504.3`)*:
  • Execute targeted test suite (`ui.settings.supporter.*`, `ui.branding.*`, `ui.theme.ThemeColorCentralizationAuditTest`, `build.*`) asserting 100% pass rate.

### TST-UI-505: **WorkoutClusterSplitSheet Embedded Map Preview & Split Action Scope Verification Suite** [Verified]
- **Target Release**: `V5.0.0`
- **Requirement(s)**: `REQ-UI-505`
- **Component**: `WorkoutClusterSplitSheet.kt`, `PathPreviewMap.kt`, `WorkoutClustersViewModel.kt`, `WorkoutClusterComponents.kt`, `WorkoutClustersList.kt`, `WorkoutClustersTabsScreen.kt`, `WorkoutClustersScreen.kt`, `WorkoutClusterHeatmapScreen.kt`, `WorkoutClusterSplitMapPreviewTest.kt`, `WorkoutClusterSplitDialogContractTest.kt`
- **Jira**: `ATT-6318, ATT-8464`
- **Test Description**:
  1. *Embedded Map Composable Layout & Placement Contract (`TST-UI-505.1`)*:
  • Verify via contract test in `WorkoutClusterSplitMapPreviewTest.kt` that `WorkoutClusterSplitSheet.kt` embeds `PathPreviewMap` directly above the sensitivity slider column with `paths = splitVariantPaths` and a bounded 200dp height.
  2. *PathPreviewMap Multi-Path Rendering & Backward Compatibility (`TST-UI-505.2`)*:
  • Verify in `PathPreviewMapContractTest.kt` that `PathPreviewMap` accepts `paths: List<MappablePath> = emptyList()` with default parameter hygiene, renders polyline loops, and accurately computes composite `LatLngBounds` across multiple paths.
  3. *List-Level Split Action Exclusion & Detail View Preservation (`TST-UI-505.3`)*:
  • Verify in `WorkoutClusterSplitMapPreviewTest.kt` that `WorkoutClusterComponents.kt` (`ClusterItem`), `WorkoutClustersList.kt`, `WorkoutClustersTabsScreen.kt`, and `WorkoutClustersScreen.kt` do NOT declare or reference `onSplitRequest`, `clusterToSplit`, `cluster_split_action`, `Icons.Default.CallSplit`, or `WorkoutClusterSplitSheet(`, while `ClusterItem` retains `cluster_menu_merge` and `delete`.
  • Verify in `WorkoutClusterSplitMapPreviewTest.kt` and `WorkoutClusterSplitDialogContractTest.kt` that `WorkoutClusterHeatmapScreen.kt` retains the `CallSplit` action (`cluster.hitCount >= 2`, `R.string.cluster_split_action`), `WorkoutClusterSplitSheet` / `WorkoutClustersViewModel.openSplitPreview()` remain intact, and `R.string.cluster_split_action` maintains 9-language parity.
  4. *Tier 1 Targeted Unit Test Regression (`TST-UI-505.4`)*:
  • Execute targeted test suite (`ui.clusters.*`, `ui.GlobalDeleteContextMenuAuditTest`, `ui.map.*`, `ui.theme.ThemeColorCentralizationAuditTest`, `ui.components.core.*`, `build.*`) asserting 100% pass rate.

### TST-UI-506: **Decoupled Compose UI State Models & Mapping Verification Suite** [Verified]
- **Target Release**: `V5.0.0`
- **Requirement(s)**: `REQ-UI-506`
- **Component**: `WorkoutLaps.kt`, `LapRowUiState.kt`, `WorkoutSummaryCompact.kt`, `WorkoutCardUiState.kt`, `WorkoutExtrema.kt`, `ExtremaUiState.kt`, `WorkoutUiStateMappingTest.kt`
- **Jira**: `ATT-936`
- **Test Description**:
  1. *LapRowUiState Mapping & Badge Determination Contract Test (`TST-UI-506.1`)*:
  • Verify in `WorkoutUiStateMappingTest.kt` that `buildLapRowUiStates(...)` accurately computes fastest/slowest lap badges, formatted time, distance, and speed/pace for RUN and non-RUN sports.
  2. *WorkoutCardUiState Transformation Contract Test (`TST-UI-506.2`)*:
  • Verify in `WorkoutUiStateMappingTest.kt` that `WorkoutCardUiState.from(...)` formats active time, total distance, speed/pace, and elevation ascent according to athlete unit settings and maps sport type and equipment properly.
  3. *ExtremaUiState Transformation Contract Test (`TST-UI-506.3`)*:
  • Verify that `ExtremaUiState.from(...)` maps extrema rows, correctly applies fallback `"-"` strings, respects `isMinRelevant`, and propagates bolding flags.
  4. *Composable Signature & Backward Compatibility Test (`TST-UI-506.4`)*:
  • Verify that existing composable callers compile and render without breaking changes.
  5. *Tier 1 Targeted Unit Test Regression (`TST-UI-506.5`)*:
  • Execute targeted test suite (`ui.components.workoutlaps.*`, `ui.components.workoutextrema.*`, `ui.aftermath.workoutlist.*`, `ui.theme.ThemeColorCentralizationAuditTest`, `ui.components.core.*`, `build.*`) asserting 100% pass rate.

### TST-UI-507: **Repository-Only UI Database Access Layer Verification Suite** [Verified]
- **Target Release**: `V5.0.0`
- **Requirement(s)**: `REQ-UI-507`
- **Component**: `MainActivityWithNavigation.kt`, `ImportBackupTabsScreen.kt`, `EquipmentViewModel.kt`, `ControlTrackingViewModel.kt`, `RepositoryUiCleanlinessContractTest.kt`
- **Jira**: `ATT-432`
- **Test Description**:
  1. *AST DatabaseManager Prohibition Contract Test (`TST-UI-507.1`)*:
  • Verify via contract test in `RepositoryUiCleanlinessContractTest.kt` that `MainActivityWithNavigation.kt`, `ImportBackupTabsScreen.kt`, `EquipmentViewModel.kt`, and `ControlTrackingViewModel.kt` contain zero occurrences of `*DatabaseManager` imports or invocations.
  2. *EquipmentViewModel Repository Delegation Test (`TST-UI-507.2`)*:
  • Verify in `EquipmentViewModelLifecycleTest.kt` / `EquipmentViewModelTest.kt` that `EquipmentViewModel` correctly loads equipment, sport types, and sensor counts via repository delegates.
  3. *ControlTrackingViewModel Unfinished Workout & Sensor Seeding Test (`TST-UI-507.3`)*:
  • Verify in `ControlTrackingViewModelTest.kt` that `ControlTrackingViewModel` correctly verifies unfinished workouts and seeds sensor state via `WorkoutRepository` and `DeviceDataRepository`.
  4. *Tier 1 Targeted Unit Test Regression (`TST-UI-507.4`)*:
  • Execute targeted test suite (`ui.equipment.*`, `ui.tracking.controltracking.*`, `ui.settings.importbackup.*`, `ui.theme.ThemeColorCentralizationAuditTest`, `ui.components.core.*`, `build.*`) asserting 100% pass rate.

### TST-UI-508: **Filter Chips & Sheets Imperial Unit Parity & Rounding Verification Suite** [Verified]
- **Target Release**: `V5.0.0`
- **Requirement(s)**: `REQ-UI-508`, `REQ-UI-157`, `REQ-UI-187`, `REQ-UI-425`
- **Component**: `FilterFormattingUtils.kt`, `FilterFormattingUtilsTest.kt`, `ActiveFilterChipsRow.kt`, `ActiveClusterFilterChipsRow.kt`, `ActiveRouteFilterChipsRow.kt`, `ClusterFilterBottomSheet.kt`, `RouteFilterBottomSheet.kt`
- **Jira**: `ATT-6168`
- **Test Description**:
  1. *Filter Distance & Elevation Formatting Unit Test (`TST-UI-508.1`, `FilterFormattingUtilsTest.kt`)*:
  • Verify `formatFilterDistanceChip` formats interval, min-only, and max-only correctly for metric (`km`) and imperial (`mi`), asserting at most 1 decimal place without trailing `.0` (e.g. "10 km", "12.5 mi").
  • Verify `formatFilterElevationChip` formats elevation correctly for metric (`m`) and imperial (`ft`), asserting rounded integer values (e.g. "100 m", "500 ft").
  2. *Active Filter Chips Row Imperial Unit Presentation Contract Test (`TST-UI-508.2`, `ActiveFilterChipsRowTest.kt`)*:
  • Verify `ActiveFilterChipsRow`, `ActiveClusterFilterChipsRow`, and `ActiveRouteFilterChipsRow` display imperial distance (`mi`) and elevation (`ft`) chips when `TrainingApplication.getUnit() == MyUnits.IMPERIAL`.
  3. *Cluster & Route Bottom Sheet Preset & Input Conversion Test (`TST-UI-508.3`, `FilterFormattingUtilsTest.kt` / `ClusterFilterBottomSheetTest.kt`)*:
  • Verify distance and elevation presets return appropriate athletic thresholds for metric vs imperial.
  • Verify two-way input conversion between display strings (mi/ft) and domain criteria meters.
  4. *9-Language Localization & Translation Parity Suite (`TST-UI-508.4`, `TranslationParityTest.kt`)*:
  • Verify all newly added imperial filter format strings exist across all 9 canonical locales (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`) with matching format specifiers.
  5. *Tier 1 Targeted Unit Test Regression (`TST-UI-508.5`)*:
  • Execute targeted UI filter test suite (`ui.common.filters.*`, `ui.aftermath.workoutlist.*`, `ui.clusters.*`, `ui.routes.*`, `ui.theme.ThemeColorCentralizationAuditTest`, `ui.components.core.*`) asserting 100% pass rate.

### TST-UI-509: **Shared Filter UI Components, Draft State & Functional Chip Transformers Verification Suite** [Verified]
- **Target Release**: `V5.0.0`
- **Requirement(s)**: `REQ-UI-509`, `REQ-UI-157`, `REQ-UI-187`, `REQ-UI-425`, `REQ-UI-468`, `REQ-UI-508`
- **Component**: `FilterSearchField.kt`, `NumericIntervalFilterSection.kt`, `StartLocationFilterSection.kt`, `WorkoutFilterBottomSheet.kt`, `ClusterFilterBottomSheet.kt`, `RouteFilterBottomSheet.kt`, `SegmentFilterBottomSheet.kt`, `KnownLocationsFilterBottomSheet.kt`, `ActiveFilterChipsRow.kt`
- **Jira**: `ATT-6043`
- **Test Description**:
  1. *Shared Filter Components Unit & Composable Contract Tests (`TST-UI-509.1`, `FilterSearchFieldTest.kt`, `NumericIntervalFilterSectionTest.kt`, `StartLocationFilterSectionTest.kt`)*:
  • Verify `FilterSearchField` handles query emission and clears text on close icon click.
  • Verify `NumericIntervalFilterSection` emits updated bounds on preset selection and manual text changes without losing partial text ("1.").
  • Verify `StartLocationFilterSection` filters infrequent locations (`hitCount < 5`) and toggles "Show all".
  2. *Filter Bottom Sheets Draft State & Reset Contract Tests (`TST-UI-509.2`, `WorkoutFilterBottomSheetTest.kt`, `ClusterFilterBottomSheetTest.kt`, `RouteFilterBottomSheetTest.kt`)*:
  • Verify `draft` criteria state captures modifications and applies them on OK click.
  • Verify `onClearAll` resets draft state to default criteria and clears text buffers.
  3. *Active Filter Chips Functional Transformer & Clear-All Contract Test (`TST-UI-509.3`, `ActiveFilterChipsRowTest.kt`, `ActiveFilterChipsRowClearAllContractTest.kt`)*:
  • Verify dismissing individual filter chips applies the expected copy transform via `onUpdateCriteria`.
  • Verify `ActiveFilterChipsRowClearAllContractTest` passes with zero regressions on clear-all semantics.
  4. *Tier 1 Targeted Unit Test Regression Suite (`TST-UI-509.4`)*:
  • Execute targeted UI filter test suite (`ui.common.filters.*`, `ui.aftermath.workoutlist.*`, `ui.clusters.*`, `ui.routes.*`, `ui.segments.*`, `ui.knownlocations.*`, `ui.theme.ThemeColorCentralizationAuditTest`, `ui.components.core.*`, `build.*`) asserting 100% pass rate.

### TST-UI-510: **Package-Wide UI & Presentation Repository Cleanliness Verification Suite** [Verified]
- **Target Release**: `V5.0.0`
- **Requirement(s)**: `REQ-UI-510`
- **Component**: `WorkoutClustersViewModel.kt`, `TrackingTabsViewModel.kt`, `AthleteProfileViewModel.kt`, `StravaActivitySection.kt`, `ExportStatusDataProvider.kt`, `WorkoutDataMapper.kt`, `MapInitialLocationResolver.kt`, `ImportBackupTabsScreen.kt`, `MainActivityWithNavigation.kt`, `EditWorkoutViewModel.kt`, `RepositoryUiCleanlinessContractTest.kt`
- **Jira**: `ATT-6697`
- **Test Description**:
  1. *Package-Wide UI AST Cleanliness Contract Test (`TST-UI-510.1`, `RepositoryUiCleanlinessContractTest.kt`)*:
  • Recursively scan all Kotlin/Java source files in `app/src/main/java/.../ui/` and `app/src/main/java/.../activities/` (excluding `*Repository.kt` and `MapFragmentWithTrackViewModel.kt` reserved for `ATT-6692`).
  • Assert zero occurrences of `\b\w*(DatabaseManager|DbHelper)\.getInstance\(`.
  2. *Targeted Presentation Cleanliness Assertions (`TST-UI-510.2`, `RepositoryUiCleanlinessContractTest.kt`)*:
  • Verify explicit decoupling across all 8 target classes: `WorkoutClustersViewModel`, `TrackingTabsViewModel`, `AthleteProfileViewModel`, `StravaActivitySection`, `ExportStatusDataProvider`, `WorkoutDataMapper`, `MapInitialLocationResolver`, `ImportBackupTabsScreen`, `MainActivityWithNavigation`, and `EditWorkoutViewModel`.
  3. *ViewModel & Presentation Unit Regression Test (`TST-UI-510.3`)*:
  • Run unit test suites for modified ViewModels and presentation helpers (`WorkoutClustersViewModelTest`, `AthleteProfileViewModelTest`, etc.).
  • Assert 100% functional and numeric parity via domain repositories.
  4. *Tier 1 Targeted Unit Test Regression (`TST-UI-510.4`)*:
  • Execute targeted test suite (`--tests "com.atrainingtracker.trainingtracker.ui.RepositoryUiCleanlinessContractTest" --tests "com.atrainingtracker.trainingtracker.ui.clusters.*" --tests "com.atrainingtracker.trainingtracker.ui.athleteprofile.*" --tests "com.atrainingtracker.trainingtracker.ui.tracking.*" --tests "com.atrainingtracker.trainingtracker.activities.*"`).
  • Assert 100% pass rate.

### TST-UI-511: **EquipmentViewModel & Repositories Domain Decoupling Verification Suite** [Verified]
- **Target Release**: `V5.0.0`
- **Requirement(s)**: `REQ-UI-511`
- **Component**: `EquipmentViewModel.kt`, `EquipmentRepository.kt`, `DeviceDataRepository.kt`, `SportTypesRepository.kt`, `WorkoutRepository.kt`, `RepositoryUiCleanlinessContractTest.kt`
- **Jira**: `ATT-6696`
- **Test Description**:
  1. *Domain DTO Decoupling Contract Test (`TST-UI-511.1`, `RepositoryUiCleanlinessContractTest.kt`)*:
  • Verify public APIs of `DeviceDataRepository`, `SportTypesRepository`, and `WorkoutRepository` return pure domain DTOs with zero `*DatabaseManager` types.
  • Verify `EquipmentViewModel.kt` imports zero `*DatabaseManager` or `*DbHelper` classes.
  2. *EquipmentRepository Encapsulation Test (`TST-UI-511.2`, `EquipmentRepositoryTest.kt` / `EquipmentDbLinkContractTest.kt`)*:
  • Verify equipment CRUD operations and sport-type associations execute cleanly via `EquipmentRepository`.
  3. *EquipmentViewModel Modernization & Lifecycle Regression Test (`TST-UI-511.3`, `EquipmentViewModelLifecycleTest.kt`, `EquipmentViewModelMatrixTest.kt`, `EquipmentViewModelRetiredTest.kt`)*:
  • Verify `EquipmentViewModel` initializes cleanly with repository injection, updating StateFlows for equipment, sport types, and sensor counts with 100% data fidelity.
  4. *Tier 1 Targeted Unit Test Regression (`TST-UI-511.4`)*:
  • Execute targeted test suite (`--tests "com.atrainingtracker.trainingtracker.ui.equipment.*" --tests "com.atrainingtracker.trainingtracker.repositories.*" --tests "com.atrainingtracker.banalservice.ui.devices.devicedata.*" --tests "com.atrainingtracker.trainingtracker.ui.RepositoryUiCleanlinessContractTest"`).
  • Assert 100% pass rate.

### TST-UI-512: **Radar Accordion Section, Dynamic Subtitle & Modularity Verification Suite** [Verified]
- **Target Release**: `V5.0.0`
- **Requirement(s)**: `REQ-UI-512`
- **Component**: `AdvancedTuningAccordion.kt`, `AdvancedTuningScreen.kt`, `AdvancedTuningDialog.kt`, `RadarSettingsSection.kt`, `strings_radar.xml`
- **Jira**: `ATT-6702`
- **Test Description**:
  1. *Radar Accordion Section & Dynamic Subtitle Test (`TST-UI-512.1`, `RadarAccordionSectionTest.kt`)*:
  • Verify `TuningSection.CYCLING_RADAR` enum presence, ordering in Block 2, and non-null title string resource.
  • Verify `TuningSubtitleFormatter.formatCyclingRadarSubtitle` produces expected summaries for idle states (edge, alert mode, tail light) and running simulation states.
  • Verify `RadarSettingsSection` renders stably with `TuningPreferencesDataStore`.
  2. *Tuning Package File Size Modularity Test (`TST-UI-512.2`, `AdvancedTuningModularityTest.kt`)*:
  • Verify all `.kt` files in `ui/settings/tuning/` remain strictly $< 400$ lines.
  3. *9-Language Localization Parity Audit (`TST-UI-512.3`)*:
  • Assert 100% presence of `tuning_cat_cycling_radar` across all 9 language folders (`values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`).
  4. *Tier 1 Targeted Unit Test Regression Suite (`TST-UI-512.4`)*:
  • Execute targeted test suite (`./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.settings.tuning.*"`).
  • Assert 100% pass rate.

### TST-UI-513: **Fitness & Physiology Hub, Training Zones Settings & Drawer Navigation Verification Suite** [Verified]
- **Target Release**: `V5.0.0`
- **Requirement(s)**: `REQ-UI-513`
- **Component**: `AppNavigationDrawer.kt`, `NavRoutes.kt`, `AthleteProfileScreen.kt`, `PeriodsTabsScreen.kt`, `ZoneSettingsScreen.kt`, `NavigationDrawerStateTest.kt`, `AppNavigationDrawerTest.kt`, `AutoPauseNavigationTest.kt`
- **Jira**: `ATT-6720`, `ATT-7535`
- **Test Description**:
  1. *Navigation Drawer Structure & Route Resolution Test (`TST-UI-513.1`, `NavigationDrawerStateTest.kt` / `AppNavigationDrawerTest.kt` / `AutoPauseNavigationTest.kt`)*:
  • Verify Training drawer group has exactly 4 items (`drawer_start_tracking`, `drawer_workouts`, `drawer_periods`, `drawer_athlete_profile`).
  • Verify `drawer_morning_hrv` is absent from the drawer group items.
  • Verify Settings drawer group contains exactly 7 items in order: `drawer_training_zones`, `drawer_sport_types`, `drawer_tracking_layouts`, `drawer_display_settings`, `drawer_auto_pause`, `drawer_units`, `drawer_advanced_tuning`.
  • Verify `isDrawerItemSelected` separates `drawer_training_zones` and `drawer_athlete_profile`.
  2. *Periods Screen Clean Decoupling Test (`TST-UI-513.2`, `PeriodsViewModelTest.kt`)*:
  • Verify `PeriodsViewModel` operates cleanly without `HrvReadinessRepository` dependencies and `PeriodsTabsScreen` contains zero HRV trend composables.
  3. *Fitness & Physiology Tab Structure & HRV Hub Test (`TST-UI-513.3`, `AthleteProfileContractAndLocalizationTest.kt` / `FitnessPhysiologyTest.kt`)*:
  • Verify tab ordering starts with Body & Thresholds (`R.string.fitness_tab_body`), followed by conditional PMC (`R.string.fitness_tab_pmc`), HRV & Readiness (`R.string.fitness_tab_hrv`), and conditional Cardio-Power (`R.string.fitness_tab_cardio_power`).
  • Verify all 4 boolean permutations of `resolveFitnessHubTabs(enablePmcModel, enableCardioPowerDynamics)`.
  • Verify PMC tab is suppressed when `enablePmcModel = false`.
  • Verify Morning HRV measurement CTA and empty state when 0 readings exist.
  • Verify `+` Add Entry action is visible on default landing tab (`initialTab = 0`).
  4. *9-Language Localization Parity Audit (`TST-UI-513.4`)*:
  • Assert 100% presence and parity of all drawer, tab, and empty state strings across all 9 language folders (`values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`).
  5. *Tier 1 Targeted Unit Test Regression Suite (`TST-UI-513.5`)*:
  • Execute targeted test suite (`./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.navigation.*" --tests "com.atrainingtracker.trainingtracker.ui.athleteprofile.*" --tests "com.atrainingtracker.trainingtracker.ui.aftermath.periodlist.*"`).
  • Assert 100% pass rate.

### TST-UI-514: **Dedicated ANT+ & Bluetooth LE Radar Vector Badge Verification Suite** [Verified]
- **Target Release**: `V5.0.0`
- **Requirement(s)**: `REQ-UI-514`
- **Component**: `app/src/main/res/drawable/bike_radar.xml`, `app/src/main/res/drawable/bt_bike_radar.xml`, `UIHelper.java`, `DeviceData.kt`, `VectorAssetResolutionContractTest.kt`
- **Jira**: `ATT-6699`, `ATT-7318`, `ATT-7518`
- **Test Description**:
  1. *Vector XML Well-Formedness & Rule 37 Geometry Contract Test (`TST-UI-514.1`, `VectorAssetResolutionContractTest.kt`)*:
  • Verify `bike_radar.xml` (96x96) and `bt_bike_radar.xml` (144x144) exist in `app/src/main/res/drawable/`, parse cleanly as XML `<vector>` documents with `48dp x 48dp` dimensions, and have valid positive viewport bounds.
  • Verify each `<path>` has $\le 45$ `L` commands and at least one analytical curve command (`A`, `C`, or `Q`), adhering strictly to Rule 37.
  2. *Color Tokens, Badge Geometry & Uppercase Typography Contract Test (`TST-UI-514.2`, `VectorAssetResolutionContractTest.kt`)*:
  • Verify `bike_radar.xml` has `#000000` background badge ($r=10$) and white foreground glyphs.
  • Verify `bt_bike_radar.xml` has full-bleed `#0A3D91` background badge ($0..144$, $r=22.0$) without legacy `#07377F` or 10-unit inset margins.
  • Verify both drawables use `evenOdd` fillType for bottom `RDR` typography counter punch-outs at expected Y-coordinates ($y \in [75.5, 90.5]$ for ANT+, $y \in [113.25, 135.75]$ for BLE).
  • Verify `bt_bike_radar.xml` centers cyclist with `translateX="18.18"` and embeds canonical radar mini-icon `mini_icon_radar` in the top-left quadrant.
  3. *DeviceType.BIKE_RADAR Icon Resolution Contract Test (`TST-UI-514.3`, `VectorAssetResolutionContractTest.kt`)*:
  • Assert `UIHelper.getIconId(DeviceType.BIKE_RADAR, Protocol.ANT_PLUS) == R.drawable.bike_radar`.
  • Assert `UIHelper.getIconId(DeviceType.BIKE_RADAR, Protocol.BLUETOOTH_LE) == R.drawable.bt_bike_radar`.
  • Assert `DeviceData.getIconId(DeviceType.BIKE_RADAR, Protocol.ANT_PLUS) == R.drawable.bike_radar`.
  • Assert `DeviceData.getIconId(DeviceType.BIKE_RADAR, Protocol.BLUETOOTH_LE) == R.drawable.bt_bike_radar`.
  4. *Tier 1 Targeted Unit Test Regression Suite (`TST-UI-514.4`)*:
  • Execute targeted test suite (`./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.resources.VectorAssetResolutionContractTest"`).
  • Assert 100% pass rate.
  5. *Radar Vector Asset Geometric Bounding Box & Collision-Free Contract Test (`TST-UI-514.5`, `VectorAssetResolutionContractTest.kt`)*:
  • Verify `bt_bike_radar.xml` cyclist group contains exactly 18 standard cyclist paths with zero colliding saddle arcs, and `mini_icon_radar` maintains clean separation ($\ge 7.0$ px) from the cyclist silhouette.
  • Verify `bt_bike_radar.xml` `mini_icon_radar` path data specifies a single non-degenerate circular source dot ($\Delta x = 2r$ chord or equivalent single circle geometry, zero double-circle snowman artifact) and concentric rearward wave arcs with uniform radial thickness and zero pinched crescents.
  • Verify `bike_radar.xml` emission arcs maintain $\ge 3.0$ units clearance from the rear wheel ($y_{\text{wheel}} \ge 39.0$), cyclist back, and card border, with all arc endpoints staying strictly above the rear wheel ($y \le 35.0$).

### TST-UI-515: **Concise Display Settings Option Subtitles & Length Bound Verification Suite** [Verified]
- **Target Release**: `V5.0.0`
- **Requirement(s)**: `REQ-UI-515`
- **Component**: `DisplaySettingsDialog.kt`, `DisplaySettingsTest.kt`, `values*/strings.xml`
- **Jira**: `ATT-6681`
- **Test Description**:
  1. *Toggle Subtitles Character Bound Test (`TST-UI-515.1`, `DisplaySettingsTest.kt`)*:
  • Verify `forcePortraitSummary`, `prefsKeepScreenOnSummary`, and `prefsNoUnlockingSummary` exist and are $\le 55$ characters across all 9 supported locales (`values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`).
  2. *Cockpit Theme Description Character Bound Test (`TST-UI-515.2`, `DisplaySettingsTest.kt`)*:
  • Verify `cockpit_theme_description` exists and is $\le 75$ characters across all 9 supported locales.
  3. *Exact Copy Match Test for German & English (`TST-UI-515.3`, `DisplaySettingsTest.kt`)*:
  • Verify exact match for German (`de`) and English (default):
    - German:
      - `forcePortraitSummary`: `"Anzeige dauerhaft im Hochformat fixieren"`
      - `prefsKeepScreenOnSummary`: `"Kein Standby während einer Aufzeichnung"`
      - `prefsNoUnlockingSummary`: `"Cockpit direkt über dem Sperrbildschirm anzeigen"`
    - English:
      - `forcePortraitSummary`: `"Lock display to portrait mode"`
      - `prefsKeepScreenOnSummary`: `"Prevent standby during active tracking"`
      - `prefsNoUnlockingSummary`: `"Show cockpit directly above the lock screen"`
  4. *Hygiene & Quality Constraints (`TST-UI-515.4`, `DisplaySettingsTest.kt`)*:
  • Verify zero format specifiers (`%s`, `%d`) and zero emojis across all 9 locales.
  5. *Tier 1 Targeted Unit Test Regression Suite (`TST-UI-515.5`)*:
  • Execute `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.settings.display.DisplaySettingsTest"`.
  • Assert 100% pass rate.

### TST-UI-516: **Pioneer Status Banner Voluntary Support Transition Copy Verification Suite** [Verified]
- **Target Release**: `V5.0.0`
- **Requirement(s)**: `REQ-UI-516`
- **Component**: `SupporterComponents.kt`, `SupporterCopyContractTest.kt`, `values*/strings.xml`
- **Jira**: `ATT-6680`
- **Test Description**:
  1. *9-Language Presence & Non-Blank Audit (`TST-UI-516.1`, `SupporterCopyContractTest.kt`)*:
  • Verify `pioneer_thank_you_body` exists and is non-blank across all 9 locales (`values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`).
  2. *Hygiene Constraints Audit (`TST-UI-516.2`, `SupporterCopyContractTest.kt`)*:
  • Verify `pioneer_thank_you_body` contains zero format arguments (`%s`, `%d`), zero decorative emojis (`ATT-5400`), and zero references to "ANT+" (case-insensitive) across all 9 locales.
  3. *Exact Copy Match Test for German & English (`TST-UI-516.3`, `SupporterCopyContractTest.kt`)*:
  • Verify exact match for German (`de`) and English (default):
    - German:
      `"Danke, dass du bereits mit einer früheren Version von aTrainingTracker deine Trainings aufgezeichnet hast! Unbegrenzte Sensoren und alle zukünftigen Funktionen sind für dich dauerhaft freigeschaltet. Über eine freiwillige finanzielle Unterstützung für die Weiterentwicklung würden wir uns trotzdem sehr freuen."`
    - English:
      `"Thank you for recording your workouts with an earlier version of aTrainingTracker! Unlimited sensors and all future features are permanently unlocked for you. Nevertheless, we would still be very happy about voluntary financial support for ongoing development."`
  4. *Tier 1 Targeted Unit Test Regression Suite (`TST-UI-516.4`)*:
  • Execute `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.settings.supporter.SupporterCopyContractTest"`.
  • Assert 100% pass rate.

### TST-UI-517: **Root Hamburger vs Pushed Back Arrow Contract and Training/Maps Header Leading Action Verification Suite** [Verified]
- **Target Release**: `V5.0.0`
- **Requirement(s)**: `REQ-UI-517`
- **Component**: `NavRoutes.kt`, `ATrainingTrackerApp.kt`, `MainActivityWithNavigation.kt`, `WorkoutTabsScreen.kt`, `PeriodsTabsScreen.kt`, `SegmentsTabsScreen.kt`, `RouteTabbedScreen.kt`, `WorkoutClustersTabsScreen.kt`, `KnownLocationsScreen.kt`
- **Jira**: `ATT-7313`
- **Test Description**:
  1. *Route Contract & Pushed Argument Verification (`TST-UI-517.1`, `SingleActivityNavigationTest.kt`)*:
     - Verify `NavRoutes.workouts(pushed = false)` returns `"workouts"`.
     - Verify `NavRoutes.workouts(pushed = true)` returns `"workouts?pushed=true"`.
     - Verify `NavRoutes.locations(pushed = false)` returns `"locations"`.
     - Verify `NavRoutes.locations(clusterId = 5L, pushed = true)` returns `"locations?clusterId=5&pushed=true"`.
     - Verify `ARG_PUSHED` parsing matches `defaultValue = false`.
  2. *MainActivityWithNavigation Push Contract Verification (`TST-UI-517.2`, `MainActivityWithNavigationInteropTest.kt`)*:
     - Verify `startWorkoutSummaryListFromPeriod` and `navigateToFilteredWorkouts` push `WORKOUTS` with `pushed = true` onto `navController`.
  3. *ATrainingTrackerApp Navigation & Interceptor State Verification (`TST-UI-517.3`, `ATrainingTrackerAppNavigationTest.kt`)*:
     - Verify drawer edge interceptor is disabled when destination argument `pushed == true`.
     - Verify system Back pops backstack and cleans filter criteria on pushed destinations.
  4. *9-Language Localization Parity Verification (`TST-UI-517.4`, `TranslationParityTest.kt`)*:
     - Verify `R.string.navigation_drawer_open` and `R.string.back` exist across all 9 locales (en, de, es, fr, it, ja, nl, pl, pt) with matching semantics.
  5. *Theme & Visual Token Verification (`TST-UI-517.5`, `ThemeColorCentralizationAuditTest.kt`)*:
     - Verify `onPrimaryContainer` tint is strictly used on leading icons.
  6. *Tier 1 Targeted Unit Test Regression Suite (`TST-UI-517.6`)*:
     - Execute `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.navigation.*"`.
     - Assert 100% pass rate.

### TST-UI-518: **Context-Aware Hamburger Menu vs Back Arrow Contract Across Secondary Drawer Groups Verification Suite** [Verified]
- **Target Release**: `V5.0.0`
- **Requirement(s)**: `REQ-UI-518`
- **Component**: `NavRoutes.kt`, `ATrainingTrackerApp.kt`, `DevicesTabbedScreen.kt`, `EquipmentTabsScreen.kt`, `ImportBackupTabsScreen.kt`, `SportTypesTabsScreen.kt`, `ZonesSettingsActivity.kt`, `AdvancedTuningScreen.kt`, `SupporterScreen.kt`, `TipsGuideScreen.kt`
- **Jira**: `ATT-7312`
- **Test Description**:
  1. *Route Contract & Pushed Argument Verification (`TST-UI-518.1`, `SingleActivityNavigationTest.kt`, `NavRoutesZoneTest.kt`, `AdvancedTuningNavigationTest.kt`)*:
     - Verify `NavRoutes.trainingZones()` preserves backwards-compatible defaults while supporting `pushed: Boolean = false`.
     - Verify `NavRoutes.supporter()` supports `pushed: Boolean = false`.
     - Verify `NavRoutes.advancedSettings(pushed: Boolean = false)` builds correct route strings.
     - Verify `toDrawerItemId()` and `fromDrawerItemId()` mappings for all secondary destinations.
  2. *Secondary Tabbed Screens Leading Icon Verification (`TST-UI-518.2`)*:
     - Verify `DevicesTabbedScreen`, `EquipmentTabsScreen`, `ImportBackupTabsScreen`, and `SportTypesTabsScreen` declare `onMenuClick` and `onBackClick` / `onNavigateBack` parameters.
     - Verify leading `IconButton` renders `Icons.Default.Menu` when `onMenuClick` is non-null and `Icons.AutoMirrored.Filled.ArrowBack` when `onBackClick` is non-null.
     - Verify 56 dp title alignment offset (`start = 4.dp` container padding + 48 dp IconButton + 4 dp text padding).
  3. *Settings & Support Context-Aware TopAppBar Verification (`TST-UI-518.3`, `AdvancedTuningScreenTest.kt`, `TipsGuideScreenContractTest.kt`)*:
     - Verify `ZoneSettingsScreen` renders `Icons.Default.Menu` when `onOpenDrawer` is provided and `Icons.AutoMirrored.Filled.ArrowBack` calling `onFinish` when `onOpenDrawer == null`.
     - Verify `AdvancedTuningScreen` renders `Icons.Default.Menu` when `onOpenDrawer` is non-null and `Icons.AutoMirrored.Filled.ArrowBack` when `onOpenDrawer == null`.
     - Verify `SupporterScreen` renders `Icons.Default.Menu` when `onOpenDrawer` is non-null and `Icons.AutoMirrored.Filled.ArrowBack` when `onOpenDrawer == null`.
     - Verify `TipsGuideScreen` renders `Icons.Default.Menu` when `onOpenDrawer` is non-null and `Icons.AutoMirrored.Filled.ArrowBack` when `onOpenDrawer == null`.
  4. *ATrainingTrackerApp Integration & Interceptor Verification (`TST-UI-518.4`, `ATrainingTrackerAppNavigationTest.kt`)*:
     - Verify drawer edge interceptor is suppressed when secondary screens are pushed.
     - Verify wiring passes `onOpenDrawer` for drawer-root destinations and back callbacks for pushed destinations.
  5. *9-Language Localization Parity Verification (`TST-UI-518.5`, `TranslationParityTest.kt`)*:
     - Verify `R.string.navigation_drawer_open`, `R.string.back`, and `R.string.cancel` exist across all 9 locales with 100% parity.
  6. *Targeted Unit Test Regression Suite (`TST-UI-518.6`)*:
     - Execute `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.navigation.*" --tests "com.atrainingtracker.trainingtracker.zones.*" --tests "com.atrainingtracker.trainingtracker.ui.settings.tuning.*" --tests "com.atrainingtracker.trainingtracker.ui.help.*"`.
     - Assert 100% pass rate.

### TST-UI-524: **Official Google Drive & Health Connect Brand Vector Contract Verification Suite** [Verified]
- **Target Release**: `V5.0.0`
- **Requirement(s)**: `REQ-UI-524`
- **Component**: `app/src/main/res/drawable/ic_google_drive.xml`, `app/src/main/res/drawable/ic_health_connect.xml`, `BrandIconContractTest.kt`
- **Jira**: `ATT-7534`
- **Test Description**:
  1. *Google Drive Vector Geometry & Color Verification (`TST-UI-524.1`, `BrandIconContractTest.kt`)*:
     - Verify `ic_google_drive.xml` exists in `app/src/main/res/drawable/`, parses as valid `<vector>` XML, contains 6 distinct `<path>` elements with official 2020+ Google Drive hex color codes (`#0066DA`, `#00AC47`, `#EA4335`, `#00832D`, `#2684FC`, `#FFBA00`), and defines a square 87.3x87.3 viewport.
  2. *Health Connect Vector Geometry & Color Verification (`TST-UI-524.2`, `BrandIconContractTest.kt`)*:
     - Verify `ic_health_connect.xml` exists in `app/src/main/res/drawable/`, parses as valid `<vector>` XML, defines `viewportWidth="88"` and `viewportHeight="88"` (or official dimensions), contains the 2 interlocking loops with `fillType="evenOdd"` and the circular node, and uses official brand colors (`#003144` and `#4285F4`).
  3. *Tier 1 Resource Compilation & Navigation Suite (`TST-UI-524.3`)*:
     - Execute `./gradlew processDebugResources` and `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.navigation.*"`.
     - Assert 100% pass rate.

### TST-UI-525: **Configurable Morning HRV Check Launch Button on Start Tracking Screen Verification Suite (ATT-8469)** [Verified]
- **Target Release**: `V5.0.0`
- **Requirement(s)**: `REQ-UI-525`
- **Component**: `TuningPreferencesDataStore.kt`, `MorningCheckSection.kt`, `DevicesDatabaseManager.java`, `DeviceDataRepository.kt`, `HrvReadinessDatabaseManager.kt`, `HrvReadinessRepository.kt`, `ControlTrackingViewModel.kt`, `TrackingTabsScreen.kt`, `ControlTrackingScreen.kt`, `MorningCheckCutoffScreenshotTest.kt`
- **Jira**: `ATT-7532`, `ATT-7740`, `ATT-8469`
- **Test Description**:
  1. *Tuning Preferences DataStore & Config Verification (`TST-UI-525.1`, `TuningConfigTest.kt`)*:
     - Verify default values: `showMorningCheckOnStartScreen = true`, `DEFAULT_MORNING_CHECK_CUTOFF_HOUR = 8`, `MIN_MORNING_CHECK_CUTOFF_HOUR = 6`, `MAX_MORNING_CHECK_CUTOFF_HOUR = 11` (`ATT-7740`).
     - Verify mutation and `.coerceIn(6, 11)` clamping on out-of-range and persisted legacy `> 11` values (`4 -> 6`, `12/23/25 -> 11`) (`ATT-7740`).
  2. *Paired HRM Sensor SQLite Query Verification (`TST-UI-525.2`, `DevicesDatabaseManagerTest.kt`)*:
     - Verify `hasPairedHeartRateDevices()` returns false when table is empty or contains only non-HRM paired devices (speed, cadence, radar).
     - Verify `hasPairedHeartRateDevices()` returns true when at least one `DeviceType.HRM` device with `PAIRED > 0` is present.
  3. *Today's Morning Check SQLite Query Verification (`TST-UI-525.3`, `HrvReadinessDatabaseManagerTest.kt`)*:
     - Verify `hasCompletedMorningCheck(dateIso)` returns false when no entries exist for `dateIso`.
     - Verify `hasCompletedMorningCheck(dateIso)` returns true once an entry with `dateIso` is inserted.
  4. *ControlTrackingViewModel Reactive Visibility Rules Verification (`TST-UI-525.4`, `ControlTrackingViewModelTest.kt`)*:
     - Verify `isMorningHrvCheckVisible` evaluates to true when mode is `READY`, hour < cutoff, today's check is not completed, and EITHER `(showMorningCheckOnStartScreen == true && hasPairedHeartRateDevices == true)` OR `enableMorningSleepAndWellness == true` (even with zero paired HRM devices and `showMorningCheckOnStartScreen == false`) (`ATT-8469`).
     - Verify `isMorningHrvCheckVisible` evaluates to false when: mode is `RECORDING` or `PAUSED`; both `showMorningCheckOnStartScreen` and `enableMorningSleepAndWellness` are false; `enableMorningSleepAndWellness` is false and no paired HRM; hour >= cutoff; or check (HRV reading or standalone wellness) is already completed today.
  5. *9-Language Localization Parity Verification (`TST-UI-525.5`, `TranslationParityTest.kt`)*:
     - Verify all 5 new string resources exist across all 9 locales with 100% parity, including `hrv_morning_check_start_button` ("Morgendlicher Check") and morning check tuning strings.
  6. *Morning Check Cutoff Slider Visual Compose Screenshot (`TST-UI-525.6`, `MorningCheckCutoffScreenshotTest.kt`, Rule 8.5)*:
     - Render `MorningCheckSection` in Light & Dark themes verifying slider range `06:00..11:00` (`steps = 4`) and default `08:00`.
     - Verify slider remains visible and interactive when `enableMorningSleepAndWellness == true` even if `showMorningCheckOnStartScreen == false` (`ATT-8469`).
  7. *Targeted Unit Test Regression Suite (`TST-UI-525.7`)*:
     - Execute `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.settings.TuningConfigTest" --tests "com.atrainingtracker.trainingtracker.ui.tracking.controltracking.ControlTrackingViewModelTest" --tests "com.atrainingtracker.trainingtracker.ui.settings.tuning.AdvancedTuningAccordionTest" --tests "com.atrainingtracker.trainingtracker.ui.settings.tuning.MorningCheckCutoffScreenshotTest"`.
     - Assert 100% pass rate.

### TST-UI-526: **Global Physiological Typography DFA α1 Verification Suite** [Verified]
- **Target Release**: `V5.0.0`
- **Requirement(s)**: `REQ-UI-526`
- **Component**: `strings_hrv.xml` (all 9 locales), `ZonesSettingsActivity.kt`, `SensorType.java`, `SensorTypeHrvTest.kt`, `HrvDfaA1SettingsContractTest.kt`, `TranslationParityTest.kt`
- **Jira**: `ATT-7521`
- **Test Description**:
  1. *SensorType HRV_DFA_A1 Typography Verification (`TST-UI-526.1`, `SensorTypeHrvTest.kt`)*:
     - Verify `SensorType.HRV_DFA_A1` retains `fullNameId = R.string.hrv_dfa_a1` and `shortNameId = R.string.hrv_dfa_a1_short`.
     - Assert that resolved string contents for default locale contain `'DFA α1'` and `'α1'`.
  2. *Zone Settings Badge Contract Verification (`TST-UI-526.2`, `HrvDfaA1SettingsContractTest.kt`)*:
     - Verify `ZonesSettingsActivity.kt` source code contains standard Greek `"α1"` in range badge templates (`"α1 > "`, `" ≤ α1 ≤ "`, `"α1 < "`) and contains zero occurrences of subscript `"α₁"`.
  3. *UI Test Fixture Typography Alignment (`TST-UI-526.3`, `HrvInfoBottomSheetTest.kt`, `SensorFieldZoneDisplayTest.kt`, `TrackingViewModelHrvTest.kt`)*:
     - Update mock sensor field labels and test expectations from `"DFA a1"` to `"DFA α1"` across affected UI test fixtures.
  4. *9-Language Localization Parity Verification (`TST-UI-526.4`, `TranslationParityTest.kt`)*:
     - Verify 100% translation parity across all 9 locales (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`) with zero missing keys and matching `%1$.2f` format specifiers.
  5. *Targeted Unit Test Regression Suite (`TST-UI-526.5`)*:
     - Execute `./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.sensor.*" --tests "com.atrainingtracker.trainingtracker.zones.*" --tests "com.atrainingtracker.trainingtracker.ui.tracking.*" --tests "com.atrainingtracker.trainingtracker.localization.*"`.
     - Assert 100% pass rate.

### TST-UI-527: **Dedicated HRV Tab, Wire Migration & Telemetry-Gated Zone Comparison Verification Suite** [Verified]
- **Target Release**: `V5.0.0`
- **Requirement(s)**: `REQ-UI-527`
- **Component**: `WorkoutDetailTabConfig.kt`, `WorkoutSectionType.kt`, `WorkoutDetailTabbedContent.kt`, `TrackOnMapScreen.kt`, `HrvThresholdSummaryCard.kt`, `WorkoutDetailTabsTest.kt`, `HrvThresholdSummaryCardTest.kt`, `TranslationParityTest.kt`
- **Jira**: `ATT-7756`
- **Test Description**:
  1. *Default 5-Tab Archetype, Enum Sizing & Mapping Test (`TST-UI-527.1`, `WorkoutDetailTabsTest.kt`)*:
     - Verify `WorkoutDetailSectionType.entries.size == 14` (including `HRV`).
     - Verify `WorkoutSectionType.entries.size == 14` (including `HRV`).
     - Verify bidirectional mapping between `WorkoutDetailSectionType.HRV` and `WorkoutSectionType.HRV`.
     - Verify `WorkoutDetailTabConfig.DEFAULT_TABS` contains 5 tabs in canonical order: `tab_overview`, `tab_map_graphs`, `tab_zones`, `tab_hrv`, and `tab_laps_segments`.
     - Verify `tab_hrv` has preset `WorkoutDetailTabPreset.HRV` and sections `[WorkoutDetailSectionType.HRV]`.
  2. *Serialization & Auto-Migration of HRV Tab Test (`TST-UI-527.2`, `WorkoutDetailTabsTest.kt`)*:
     - Verify round-trip serialization/deserialization of tabs containing `tab_hrv`.
     - Verify that deserializing a legacy wire string (e.g. from v2 format lacking `HRV`) automatically injects `tab_hrv` directly after `tab_zones`.
  3. *Dynamic Availability & Empty Tab Auto-Hiding Test (`TST-UI-527.3`, `WorkoutDetailTabsTest.kt`)*:
     - Verify `WorkoutDetailSectionAvailability.isSectionActive(WorkoutDetailSectionType.HRV)` reflects `hasHrv`.
     - Verify `filterVisibleWorkoutDetailTabs` retains `tab_hrv` when `hasHrv == true` and filters out `tab_hrv` when `hasHrv == false`.
  4. *HRV Cards Mounting Under HRV Section Test (`TST-UI-527.4`, `HrvThresholdSummaryCardTest.kt`)*:
     - Verify `HrvThresholdSummaryCard`, `HrvAlpha1ScatterCard`, and `HrvDurabilityCard` are mounted under `WorkoutDetailSectionType.HRV` in `WorkoutDetailTabbedContent.kt`.
     - Verify `WorkoutDetailSectionType.ZONES` renders only `HeartRateZoneDistributionCard`, `PowerZoneDistributionCard`, and `GradeZoneDistributionCard`.
  5. *Telemetry-Gated Zone Comparison Section Test (`TST-UI-527.5`, `HrvThresholdSummaryCardTest.kt`)*:
     - Verify that when `hasPower == false`, the Coggan Power comparison block, FTP label, power delta chips, power consensus badge, and "Leistungszonen aktualisieren" button are omitted from `HrvThresholdSummaryCard`.
     - Verify that when `hasPower == true && configuredFtp != null`, the power comparison block and update button are rendered.
     - Verify that when neither power nor HR comparison applies, the entire `hrv_zone_comparison_section` is omitted.
  6. *9-Language Localization Parity Audit (`TST-UI-527.6`, `TranslationParityTest.kt`)*:
     - Verify `workout_detail_tab_hrv` and `settings_workout_card_hrv` exist across all 9 localized `strings.xml` files (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`).
  7. *Targeted Unit Test Regression Suite (`TST-UI-527.7`)*:
     - Execute `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.aftermath.*" --tests "com.atrainingtracker.trainingtracker.localization.*"`.
     - Assert 100% pass rate.

### TST-UI-528: **Multi-Dimensional Sensor List Sorting (Alphabetical, First Seen, Last Usage, Battery Level) Verification Suite** [Verified]
- **Target Release**: `V5.0.0`
- **Requirement(s)**: `REQ-UI-528` (`REQ-UI-528.1`..`REQ-UI-528.5`)
- **Component**: `DeviceSortOrder.kt`, `DeviceListViewModel.kt`, `DevicesTabbedViewModel.kt`, `DevicesTabbedScreen.kt`, `strings_devices.xml` (all 9 locales)
- **Jira**: `ATT-7663`
- **Test Description**:
  1. *DeviceSortOrder Model & Comparator Unit Tests (`TST-UI-528.1`, `DeviceSortOrderTest.kt`)*:
     - Verify `ALPHABETICAL` sorts case-insensitively ascending by name with ID tie-breaker.
     - Verify `FIRST_SEEN` sorts by `id` descending.
     - Verify `LAST_USAGE` sorts connected devices first, then descending by parsed `lastSeen` timestamp, with unknown/null placed at bottom.
     - Verify `BATTERY_LEVEL` sorts known percentages ascending (0%..100%), followed by unknown (-1).
  2. *Scan Recency Pinning Across All Sort Orders (`TST-UI-528.2`, `DeviceListViewModelPriorityTest.kt`)*:
     - Verify that across all 4 sort orders, `isNewlyDiscovered == true` sensors remain pinned at the very top of the list in reverse discovery order.
  3. *DevicesTabbedViewModel Sort Order State & SavedStateHandle (`TST-UI-528.3`, `DevicesTabbedViewModelTest.kt`)*:
     - Verify default sort order is `ALPHABETICAL`.
     - Verify updating sort order updates state flow and saves to `SavedStateHandle`.
  4. *Top App Bar Sort Button Visibility Contract Tests (`TST-UI-528.4`, `DevicesTabbedScreenContractTest.kt`)*:
     - Verify Sort button is present on sensor tabs (0, 1, 2) and hidden on Equipment tabs (3, 4).
  5. *9-Language Localization Parity Verification (`TST-UI-528.5`, `TranslationParityTest.kt`)*:
     - Verify all 4 sort labels exist across all 9 locales (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`) with zero missing entries.
  6. *Targeted Unit Test Regression Suite (`TST-UI-528.6`)*:
     - Execute `./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.ui.devices.*" --tests "com.atrainingtracker.trainingtracker.build.*"`.

### TST-UI-529: **Borg CR10 RPE 7-Zone Palette, Slider Stop-Dot & Header Readout Verification Suite** [Verified]
- **Target Release**: `V5.0.0`
- **Requirement(s)**: `REQ-UI-529` (`REQ-UI-529.1`..`REQ-UI-529.5`)
- **Component**: `Color.kt`, `WorkoutRpeFeelSection.kt`, `WorkoutRpeFeelTest.kt`
- **Jira**: `ATT-7743`
- **Test Description**:
  1. *Borg CR10 7-Zone Physiological Color Mapping Unit Tests (`TST-UI-529.1`, `WorkoutRpeFeelTest.kt`)*:
     - Verify `TTColor.rpeColor(1)` and `TTColor.rpeColor(2)` return `TTColor.Zone1`.
     - Verify `TTColor.rpeColor(3)` and `TTColor.rpeColor(4)` return `TTColor.Zone2`.
     - Verify `TTColor.rpeColor(5)` and `TTColor.rpeColor(6)` return `TTColor.Zone3`.
     - Verify `TTColor.rpeColor(7)` returns `TTColor.Zone4`.
     - Verify `TTColor.rpeColor(8)` returns `TTColor.Zone5`.
     - Verify `TTColor.rpeColor(9)` returns `TTColor.Zone6`.
     - Verify `TTColor.rpeColor(10)` returns `TTColor.Zone7`.
     - Verify boundary clamping: `TTColor.rpeColor(0)` returns `TTColor.Zone1`, `TTColor.rpeColor(11)` returns `TTColor.Zone7`.
  2. *Dark Theme Contrast & Accessibility Verification (`TST-UI-529.2`, `WorkoutRpeFeelTest.kt`)*:
     - Verify WCAG 2.1 relative luminance calculation between `TTColor.Zone7` and `DarkBackground` / `DarkSurface`.
     - Verify RPE 10 text color resolves to `MaterialTheme.colorScheme.onSurface`.
     - Verify RPE 10 badge and thumb receive `outlineVariant` border.
  3. *Redundant Button Row Absence Contract Test (`TST-UI-529.3`, `WorkoutRpeFeelTest.kt`)*:
     - Verify `RpeFeelEditorControls` does NOT render the 10 numbered score boxes beneath the slider.
  4. *Slider Track Stop-Dot Uniformity Contract Test (`TST-UI-529.4`, `WorkoutRpeFeelTest.kt`)*:
     - Verify `Slider` passes `drawStopIndicator = null` inside `track = { SliderDefaults.Track(...) }` so inactive tick 10 is not colored with active track color.
  5. *Decoupled Header & Full-Width Readout Structure Contract Test (`TST-UI-529.5`, `WorkoutRpeFeelTest.kt`)*:
     - Verify title row hosts `workout_rpe_title` and Clear button (`RPE_CLEAR`).
     - Verify dynamic anchor readout renders on a dedicated full-width line positioned directly above the slider.
  6. *Targeted Unit Test Regression Suite (`TST-UI-529.6`)*:
     - Execute `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutRpeFeelTest" --tests "com.atrainingtracker.trainingtracker.build.ThemeColorCentralizationAuditTest"`.
     - Assert 100% pass rate.

### TST-UI-530: **Centralized Telemetry Color Palette, Metric Defaults & Preference Verification Suite** [Verified]
- **Target Release**: `V5.0.0`
- **Requirement(s)**: `REQ-UI-530` (`REQ-UI-530.1`..`REQ-UI-530.4`)
- **Component**: `Color.kt`, `TelemetryGraphColorPreferences.kt`, `TelemetryMetricGraph.kt`, `TelemetryGraphColorContrastTest.kt`, `TelemetryGraphColorPreferencesTest.kt`
- **Jira**: `ATT-7759`
- **Test Description**:
  1. *TelemetryGraphColor WCAG Contrast & Palette Unit Tests (`TST-UI-530.1`, `TelemetryGraphColorContrastTest.kt`)*:
     - Verify all 8 colors (`CRIMSON`, `VIOLET`, `EMERALD`, `AMBER`, `TEAL`, `MAGENTA`, `CYAN`, `CORAL`) achieve $\ge 3.0:1$ non-text contrast against Light (`#FFFFFF`), Dark (`#16161A`), and AMOLED (`#000000`) surfaces.
     - Verify `EMERALD` Light variant (`#059669`) achieves $\ge 3.0:1$ contrast against `#FFFFFF`, fixing the `2.53:1` contrast deficiency of raw `WPrimeBalGreen`.
     - Verify `resolveColor` returns `colorScheme.primary` when `THEME_DEFAULT` is selected.
  2. *Canonical Metric & Sensor Default Mappings Unit Tests (`TST-UI-530.2`, `TelemetryGraphColorPreferencesTest.kt`)*:
     - Verify default color mappings for all `TelemetryMetricType` values: HR=CRIMSON, POWER=VIOLET, W_PRIME_BAL=EMERALD, HRV_DFA_A1=AMBER, HRV_RMSSD=TEAL, SPEED/PACE=THEME_DEFAULT.
     - Verify default mappings for `SensorType` families (Heart Rate, Power, Speed, Cadence, Altitude, Grade, etc.).
  3. *Preference Persistence & Safe Deserialization Unit Tests (`TST-UI-530.3`, `TelemetryGraphColorPreferencesTest.kt`)*:
     - Verify `setGraphColor` persists the selected color and `getGraphColor` returns it.
     - Verify `resetGraphColor` restores the metric's default color.
     - Verify `resetAllGraphColors` resets all metric overrides to defaults.
     - Verify corrupt, unknown, or lowercase enum strings in `SharedPreferences` safely fall back to canonical defaults without throwing exceptions (`REQ-ARC-001`).
  4. *TelemetryMetricGraph Accent Color Resolution & Contrast Verification (`TST-UI-530.4`, `TelemetryMetricGraphColorTest.kt`)*:
     - Verify accent color resolution in `TelemetryMetricGraph` across Light, Dark, and AMOLED theme modes.
     - Verify reactive resolution when preferences are modified.
  5. *Architecture, Centralization & Governance Regression Suite (`TST-UI-530.5`)*:
     - Execute `ThemeColorCentralizationAuditTest`, `AspiceSweAuditContractTest`, `SourceTextReadingAuditContractTest`, `SafeEnumDeserializationAuditTest`, and `verify_requirement_governance.py`.
     - Assert 100% pass rate with zero `Color(0x...)` literals outside `Color.kt`.

### TST-UI-2781: Conditional Section Dividers Verification in SensorSourceDialog [Verified]
- **Target Release**: `V5.0.0`
- **Requirement(s)**: `REQ-UI-2781` (`REQ-UI-2781.1`, `REQ-UI-2781.2`)
- **Component**: `SensorSourceDialog.kt`, `SensorSourceDialogContractTest.kt`
- **Jira**: `ATT-8107`
- **Test Description**:
  1. *Disconnected Sensor Top-Divider Absence Contract & Unit Test (`TST-UI-2781.1`, `SensorSourceDialogContractTest.kt`)*:
     - Verify that when `sourceDevice == null` and `activeBackups` is empty, no `HorizontalDivider` is emitted preceding the `notConnected` section.
     - Verify that the first visual element rendered below the dialog title is the `notConnected` section header.
  2. *Altitude Calibration & Section Permutation Divider Separation Contract Test (`TST-UI-2781.2`, `SensorSourceDialogContractTest.kt`)*:
     - Verify that when Altitude Calibration is present alongside `notConnected` devices (with no active source), exactly one divider separates the calibration card from `notConnected`, eliminating stacked/duplicate dividers.
     - Verify that when `sourceDevice != null` and `activeBackups.isNotEmpty()`, a divider separates `sourceDevice` from `activeBackups`.
     - Verify that when no content follows Altitude Calibration, no trailing divider is emitted.
  3. *Targeted Regression Suite & Requirement Governance (`TST-UI-2781.3`)*:
     - Execute `./gradlew testDebugUnitTest --tests "*SensorSourceDialog*" --tests "*SensorStatus*" --max-workers=2`.
     - Execute `python3 tools/verify_requirement_governance.py --check-code-orphans --check-req-description`.
     - Assert 100% pass rate.

### TST-UI-2782: EditSensorFieldDialog Logical Section Ordering & Guard Verification [Verified]
- **Target Release**: `V5.0.0`
- **Requirement(s)**: `REQ-UI-2782` (`REQ-UI-2782.1`, `REQ-UI-2782.2`, `REQ-UI-2782.3`)
- **Component**: `EditSensorFieldDialog.kt`, `EditSensorFieldDialogScreenshotTest.kt`, `EditSensorFieldDialogOrderTest.kt`
- **Jira**: `ATT-8462`
- **Test Description**:
  1. *3-Group Vertical Ordering Compose UI Test (`TST-UI-2782.1`, `EditSensorFieldDialogOrderTest.kt`)*:
     - Render `EditSensorFieldDialog` via Robolectric `createComposeRule()` with multi-device source list, `filteringPossible = true`, `isMultiFieldRow = true`, `isGraphSupported = true`, `showGraph = true`, and a metric with guidance (`HRV_DFA_A1` / `POWER_TO_WEIGHT` / `INTENSITY_FACTOR`).
     - Assert strict top-to-bottom `boundsInRoot().top` ordering across all 3 logical groups: `Sensortyp < Quelle < Filter < Textgröße < Relative Breite < Anzeigeelemente < Zeitfenster < Graph-Farbe < Vorschau < Metrik-Hinweis`.
  2. *Conditional Guards & Callback Preservation Tests (`TST-UI-2782.2`, `EditSensorFieldDialogOrderTest.kt`)*:
     - Verify omission when `filteringPossible == false` (Filter button absent), `isMultiFieldRow == false` (Relative Breite absent), single `-1L` `deviceList` (Quelle absent), and `showGraph == false` / `isGraphSupported == false` (Graph options absent while `Textgröße` / `Relative Breite` remain above `Anzeigeelemente`).
     - Verify `POWER_TO_WEIGHT`, `INTENSITY_FACTOR`/`TSS`, and `isHrvMetric` guidance blocks render below Visual Representation.
     - Verify interactive callbacks (`onConfigureFilterClicked`, `onColWeightChanged`, `onShowGraphChanged`, `saveChanges`) remain wired and functional.
  3. *Rule 8.5 Light & Dark Mode Visual Screenshot Inspection (`TST-UI-2782.3`, `EditSensorFieldDialogScreenshotTest.kt`)*:
     - Capture Light and Dark mode screenshots of the reordered `EditSensorFieldDialog` (`ATT-8462_dialog_light.png`, `ATT-8462_dialog_dark.png`) and inspect via `read_file` for spacing, alignment, typography wrapping, contrast, and touch ergonomics.
  4. *Targeted Regression & Governance Suite (`TST-UI-2782.4`)*:
     - Execute `./gradlew :app:testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.tracking.editsensorfield.*" --tests "com.atrainingtracker.trainingtracker.ui.tracking.TrackingTabsPolishContractTest" --tests "com.atrainingtracker.trainingtracker.ui.components.core.ModalBottomSheetDialogsIntegrityTest"` and `python3 tools/verify_requirement_governance.py`.
     - Assert 100% pass rate.

### TST-UI-2783: ZoneDisplayOptions Removal from Expert Settings & Tile/Tab Config Verification [Verified]
- **Target Release**: `V5.0.0`
- **Requirement(s)**: `REQ-UI-2783` (`REQ-UI-2783.1`, `REQ-UI-2783.2`, `REQ-UI-2783.3`)
- **Component**: `ZoneDisplayOptionsControls.kt`, `EditSensorFieldDialog.kt`, `EditSensorFieldViewModel.kt`, `SensorGridScreen.kt`, `ZonesSettingsActivity.kt`, `EditSensorFieldZoneDisplayTest.kt`, `TrackingTabConfigZoneDisplayTest.kt`
- **Jira**: `ATT-8498`
- **Test Description**:
  1. *Expert Settings Removal & Contract Guards (`TST-UI-2783.1`)*:
     - Verify `AdvancedTuningScreen`, `AdvancedTuningDialog`, and `CockpitTypographySection` no longer render `zone_display_title` / `zone_display_apply_all` (`AdvancedTuningScreenTest` / Compose test).
     - Verify `CockpitPreviewTileContractTest`, `AdvancedTuningModularityTest`, `TuningDesignGuidelinesContractTest`, `HrvDfaA1SettingsContractTest` (aligned with `REQ-TRK-087` / `TST-TRK-087` for `ZonesSettingsActivity` 4-toggle parity), and `SourceTextReadingAuditContractTest` (zero `src/main` file reads in new/modified tests) pass 100%.
  2. *Tile Long-Press `EditSensorFieldDialog` & ViewModel Verification (`TST-UI-2783.2`, `EditSensorFieldZoneDisplayTest.kt`)*:
     - Verify profile mapping matches `TrackingViewModel.resolveDisplayOptions`: `(HR, RUN) -> HR_RUN`, `(HR, BIKE) -> HR_BIKE`, `(POWER, BIKE) -> PWR_BIKE`, `(HRV_DFA_A1, *) -> HRV_DFA_A1`, and non-zone / unmapped metrics (`SPEED`, `HR` in `UNKNOWN`, `POWER` in `RUN`) hide the Zone Display section.
     - Verify `zone_display_gauge` is shown strictly for `HRV_DFA_A1`, `zone_display_apply_all` preserves `HRV.showGauge`, **Cancel** discards changes, and **Save** (`saveChanges()`) persists to `SettingsDataStore` without altering zone thresholds.
  3. *Tracking Tabs Config `SensorGridScreen` Verification (`TST-UI-2783.3`, `TrackingTabConfigZoneDisplayTest.kt`)*:
     - Verify `SensorGridScreen` in `ScreenMode.CONFIGURATION` renders the Zone Display section with `SingleChoiceSegmentedButtonRow` pre-selected from `ActivityType`, `ZoneDisplayOptionsToggleList`, and `zone_display_apply_all`, persisting toggles immediately to `SettingsDataStore`.
  4. *9-Locale Parity & Rule 8.5 Light/Dark Visual Verification (`TST-UI-2783.4`)*:
     - Verify `ZoneDisplayLocalizationTest` and `TranslationParityTest` pass across all 9 locales (`values`..`values-pt`).
     - Capture combined Light & Dark Compose screenshot `docs/engineering/walkthroughs/img/ATT-8498_zone_display_tile_and_tabs.png` and inspect via `read_file`.

### TST-UI-2784: RadarTrafficSummaryCard Canonical Speed Unit Verification [Verified]
- **Target Release**: `V5.0.0`
- **Requirement(s)**: `REQ-UI-2784` (`REQ-UI-2784.1`, `REQ-UI-2784.2`)
- **Component**: `RadarTrafficSummaryCard.kt`, `MetricFormatterContext.kt`, `SpeedFormatter.kt`, `RadarTrafficSummaryCardTest.kt`, `SpeedFormatterTest.kt`
- **Jira**: `ATT-8440`
- **Test Description**:
  1. *Metric & Imperial Speed Formatting Compose UI Test (`TST-UI-2784.1`, `RadarTrafficSummaryCardTest.kt`)*:
     - Render `RadarTrafficSummaryCard` via Robolectric `createComposeRule()` with `totalVehicles = 12`, `maxApproachSpeedMps = 10.0`, `maxVehicleSpeedMps = 20.0`.
     - Under `TrainingApplication.setUnitForTesting(MyUnits.METRIC)`, assert `km/h` values (`36.0 km/h` / `36,0 km/h` and `72.0 km/h` / `72,0 km/h`) are displayed.
     - Under `TrainingApplication.setUnitForTesting(MyUnits.IMPERIAL)`, assert `mile/h` values (`22.4 mile/h` / `22,4 mile/h` and `44.7 mile/h` / `44,7 mile/h`) are displayed and zero `km/h` speed strings remain.
     - Reset `TrainingApplication.setUnitForTesting(null)` in `@After`.
  2. *Null / Zero-Vehicle Guard & Out-of-Scope Field Preservation (`TST-UI-2784.2`, `RadarTrafficSummaryCardTest.kt`)*:
     - Verify `RadarTrafficSummaryCard(null)` and `RadarTrafficSummaryCard(RadarTrafficSummary(totalVehicles = 0))` render zero nodes.
     - Verify non-speed metrics (`vehiclesPerKm`, `vehiclesPerHour`, `minPassingDistanceM`) remain unaffected.
  3. *Rule 8.5 Light & Dark Visual Verification (`TST-UI-2784.3`, `RadarTrafficSummaryCardTest.kt`)*:
     - Capture Light and Dark screenshots of `RadarTrafficSummaryCard` under `MyUnits.IMPERIAL` (`ATrainingTrackerTheme`) and inspect via `read_file`.
  4. *Targeted Regression Suite (`TST-UI-2784.4`)*:
     - Execute `RadarTrafficSummaryCardTest`, `RadarTrafficSummaryTest`, `RadarTrafficSummaryFormatterTest`, and `SpeedFormatterTest` asserting 100% pass rate.

### TST-UI-2785: Enlarged Lieblingsorte Card Preview Map (175x120 dp), Zoom Calibration & Narrow Viewport Verification [Verified]
- **Target Release**: `V5.0.0`
- **Requirement(s)**: `REQ-UI-2785` (`REQ-UI-2785.1`, `REQ-UI-2785.2`, `REQ-UI-2785.3`, `REQ-UI-2785.4`)
- **Component**: `KnownLocationsScreen.kt`, `KnownLocationZoomMath.kt`, `KnownLocationCardLayoutTest.kt`, `KnownLocationZoomMathTest.kt`, `KnownLocationCardComposeUiTest.kt`
- **Jira**: `ATT-8749`
- **Test Description**:
  1. *Zoom Calibration Unit Tests (`TST-UI-2785.1`, `KnownLocationZoomMathTest.kt`)*:
     - Verify `DEFAULT_THUMBNAIL_HEIGHT_DP == 120.0` and `calculateThumbnailZoom` produces calibrated `+0.585` zoom levels at `lat = 48.137` (`500m -> 11.7f`, `1000m -> ~11.10f`, `1500m -> ~10.51f`, `2000m -> ~10.10f`, `>= 2700m -> 9.7f`) while preserving `MIN_ZOOM = 9.7f` and `MAX_ZOOM = 11.7f`.
  2. *Legacy Layout Baseline Update (`TST-UI-2785.2`, `KnownLocationCardLayoutTest.kt`)*:
     - Update `KnownLocationCardLayoutTest.kt:297` in-place to assert `.size(width = 175.dp, height = 120.dp)` without adding any new `src/main` reads (`app/source-reading-baseline.txt:75` count `105` preserved).
  3. *Behavioral Compose UI & `360.dp` Overflow Tests (`TST-UI-2785.3`, `KnownLocationCardComposeUiTest.kt`)*:
     - Render `KnownLocationCard` via Robolectric `createComposeRule()` with `0` `src/main` file reads (`REQ-PRO-049.1`) and explicit `TST-UI-2785` KDoc tag (`--check-verified-evidence`).
     - Assert `location_map_preview_<id>` bounds measure `175.dp × 120.dp`, verify `360.dp` viewport width with 3 multi-digit `SportStartsItem` counts (`130%` font scale) and long altitude text renders without clipping/overlap (`Altitude` single-line ellipsis + `SportStartsItem` wrapping/spacing guard), and verify `Rule 8.5` Light/Dark PNG (`docs/engineering/walkthroughs/img/ATT-8749_lieblingsorte_preview.png`) plus `emulator-5554` inspection.
  4. *Localization & Targeted Regression (`TST-UI-2785.4`)*:
     - `Localization Parity: N/A - no UI strings` (`values*/strings*.xml` untouched); execute `KnownLocationZoomMathTest`, `KnownLocationCardLayoutTest`, and `KnownLocationCardComposeUiTest` asserting 100% pass rate.

### TST-UI-2786: Workout Detail Header Action Uniformity, Global-vs-Unit Grouping & Universal Cluster Swap Verification [Verified]
- **Target Release**: `V5.0.0`
- **Requirement(s)**: `REQ-UI-2786` (`REQ-UI-2786.1`, `REQ-UI-2786.2`, `REQ-UI-2786.3`, `REQ-UI-2786.4`)
- **Component**: `WorkoutHeader.kt`, `WorkoutDetailTabbedContent.kt`, `TrackOnMapScreen.kt`, `TrackOnMapAftermathViewModel.kt`, `WorkoutClusterRepository.kt`, `WorkoutClustersScreen.kt`, `WorkoutClusterHeatmapScreen.kt`, `WorkoutDetailHeaderActionsComposeTest.kt`, `TrackOnMapClusterActionViewModelTest.kt`
- **Jira**: `ATT-8097`
- **Test Description**:
  1. *Robolectric Compose UI Action Uniformity, Ordering, Grouping & `actionSpacerWidth` Tests (`TST-UI-2786.1`, `WorkoutDetailHeaderActionsComposeTest.kt`)*:
     - Render `WorkoutHeader` and `WorkoutDetailTabbedContent` via Robolectric `createComposeRule()` with `0` `src/main` file reads (`REQ-PRO-049.1`) and explicit `TST-UI-2786` KDoc tag.
     - Assert left-to-right `boundsInRoot().left` ordering `[Tune] < [⇄] < [Edit] < [Share]` (with `Tune` in its global tonal pill + `VerticalDivider` before `[⇄][Edit][Share]`), `32.dp × 32.dp` icon-button bounds, `MaterialTheme.colorScheme.primary` tint on all 4 action icons (and `error` tint retained on `WarningAmber`), non-overlapping title bounds on `360.dp` and `411.dp` viewports (`actionSpacerWidth`), and `TooltipBox`/`PlainTooltip` semantics distinguishing global display layout from single-workout actions.
     - Verify `WorkoutSummary` / `snapshotHeader` defaults (`onOpenTabsConfig = null`, `onChangeCluster = null`) omit `Tune` and `⇄`.
  2. *Universal `⇄` Entry Points & Non-GPS/Indoor Cluster Fallback Tests (`TST-UI-2786.2`, `TrackOnMapClusterActionViewModelTest.kt`)*:
     - Verify `TrackOnMapScreen` renders `⇄` uniformly across all 5 callers (`WorkoutSummariesTabbedScreen`, `WorkoutSummariesListFragment`, `PeriodMapScreen`, `WorkoutClustersScreen`, `WorkoutClusterHeatmapScreen`) via built-in `TrackOnMapAftermathViewModel` / `WorkoutClusterRepository` orchestration when `onChangeCluster` is not overridden.
     - Verify for indoor/non-GPS workouts (`startLatLng == null || endLatLng == null`): candidate list falls back to `WorkoutClusterRepository.getAllClustersSync()`, selecting a cluster invokes `assignClusterToWorkout` (`forceIdentity = true`, bypassing `moveWorkoutToCluster` early return), `onCreateNew` is hidden (`null`), and `onUnassign` stays enabled when `clusterId > 0`.
  3. *9-Locale Tooltip Parity & Architectural Invariants (`TST-UI-2786.3`, `TranslationParityTest.kt`, `ArchitectureConformanceContractTest.kt`)*:
     - Verify `values*/strings_workout_header.xml` defines all 4 tooltip/accessibility keys across all 9 locales (EN, DE, ES, FR, IT, JA, NL, PL, PT) via `TranslationParityTest`.
     - Verify `WorkoutRepository.kt` remains untouched (`<= 5,117` LoC), `ArchitectureConformanceContractTest` (`IF-SWE`), `SourceTextReadingAuditContractTest`, `WorkoutDetailHeaderClickContractTest`, `WorkoutHeaderLandscapeTest`, and `WorkoutClusterSelectionDialogTest` pass 100%.
  4. *Rule 8.5 Light/Dark `@Preview` & On-Device Verification (`TST-UI-2786.4`)*:
     - Capture Light/Dark `@Preview` screenshot `docs/engineering/walkthroughs/img/ATT-8097_header_actions_preview.png`, inspect via `read_file` against the 4-point checklist (`Rule 8.5`), and verify on `emulator-5554`.

### TST-UI-2787: **Workout Details v3 Persistence, Summary Toggle Decoupling, Header Action Reservation & Tab-Driven Settings Verification Suite** [Verified]
- **Requirement(s)**: `REQ-UI-2787` (`REQ-UI-2787.1`..`REQ-UI-2787.4`)
- **Component**: `WorkoutDetailTabConfig.kt`, `WorkoutDetailTabbedContent.kt`, `WorkoutHeader.kt`, `WorkoutMasksAndCardsSection.kt`, `AdvancedTuningAccordion.kt`, `WorkoutDetailTabsTest.kt`, `WorkoutDetailHeaderActionsComposeTest.kt`, `AdvancedTuningAccordionTest.kt`, `TranslationParityTest.kt`
- **Jira**: `ATT-8079` | **Target Release**: `V5.0.0`
- **Test Description**:
  1. *`v3|` Serialization & Persistent Section/Tab Removal (`TST-UI-2787.1`, `WorkoutDetailTabsTest.kt`)*:
     - Verify `serializeTabs` emits `"v3|"` prefix, unchecking `RPE_AND_FEEL`, `GRADE_DISTRIBUTION`, or `PHOTOS`, or deleting `tab_hrv`, persists cleanly across `serializeTabs` $\rightarrow$ `deserializeTabs`, and all legacy `"v1|"` / `"v2|"` auto-migration tests (`TST-UI-371.2b`, `TST-UI-453.4b`, `TST-UI-453.4c`, `TST-UI-527.2`) pass unchanged.
  2. *Summary Toggle Decoupling Behavioral Compose UI Test (`TST-UI-2787.2`, `WorkoutDetailSummaryDecouplingTest.kt`)*:
     - Using Robolectric `createComposeRule()` (`0` `src/main` reads per `SourceTextReadingAuditContractTest` & `UiReflectionOnlyTestAuditContractTest`), render `WorkoutDetailTabbedContent` (`DETAILS`) with `showCogganPowerInWorkoutSummary = false`, `showAerobicEfficiencyInWorkoutSummary = false`, `showCriticalPowerInWorkoutSummary = false` (`enable* = true`) verifying Coggan NP/IF/TSS, Aerobic Efficiency, and W'bal remain visible in Workout Details while `WorkoutSummary` hides them when those flags are `false`.
  3. *Dynamic `WorkoutHeader` Custom `actions()` Width Reservation (`TST-UI-2787.3`, `WorkoutDetailHeaderActionsComposeTest.kt`)*:
     - Using `createComposeRule()` at `360dp`, render `WorkoutHeader` with a 2-line portrait title and custom `actions = { ... }` content verifying `titleBounds.right <= actionsBounds.left` without overlap while preserving existing `ATT-8097` Frame-0 bounds assertions and `Rule 8.5` PNG check.
  4. *Tab-Driven Details Zone Selector, Subtitle, `< 400` LoC & 9-Locale Parity (`TST-UI-2787.4`, `WorkoutMasksAndCardsLayoutTest.kt`, `AdvancedTuningAccordionTest.kt`, `TranslationParityTest.kt`)*:
     - Using `createComposeRule()`, verify `WorkoutMasksAndCardsSection` shows/hides `ZoneDisplayModeSelector` based on `detailTabs` (`ZONES` / `GRADE_DISTRIBUTION`) regardless of `workoutDetailPrefs.showZoneAnalysis`, verify `WorkoutMasksAndCardsSection.kt` `< 400` LoC, verify `TuningSubtitleFormatter.formatWorkoutMatrixSubtitle` (`detailTabs` overload + preserved 3-arg compatibility), and verify `tuning_summary_matrix_tabs_format` across all 9 locales via `TranslationParityTest`.

### TST-UI-250: **Sensor Pairing Device Type Selection: Omission of 'Alle' Action and Targeted Hardware Profile Discovery Verification** [Verified]
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

### TST-UI-251: **Sensors View Multi-Sensor Inventory Preservation During Pairing Verification** [Verified]
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

---

### TST-UI-252: **Sensor Field Style Dynamic Grid Spacing Resolution Verification** [Implemented]
- **Requirement(s)**: `REQ-UI-292`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Component**: `SensorFieldStyle.kt`
- **Jira**: `ATT-2624`
- **Test Description**:
  1. *Dynamic Inter-Tile Grid Spacing Resolution Test (`TST-UI-252.1`)*:
  • Verify that `SensorFieldStyle` computes `0.dp` spacing for `CLASSIC_SEAMLESS` preset and expands spacing dynamically for rounded corners or thick borders to prevent visual card clipping.

---

### TST-UI-253: **Format-Specific Import Tab Architecture and Multi-Document Recovery Verification** [Verified]
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

### TST-UI-254: **Workout File Picker Selectability and Format-Extension Validation Verification** [Verified]
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

### TST-UI-2788: **Cockpit Sparkline Canvas Height, Compact X-Axis Leading & Flexible Value+Graph Layout Suite** (`ATT-8995`) [Verified]
- **Requirement(s)**: `REQ-UI-2788` (`REQ-UI-2788.1`..`REQ-UI-2788.3`)
- **Component**: `SensorSparklineView.kt`, `SensorFieldView.kt`, `EditSensorFieldDialog.kt`, `SensorSparklineViewTest.kt`, `SensorFieldViewGraphOnlyLayoutTest.kt`, `SensorFieldViewSparklineLayoutComposeTest.kt`, `SensorFieldViewDynamicZoneScreenshotTest.kt`, `EditSensorFieldDialogScreenshotTest.kt`
- **Jira**: `ATT-8995` | **Priority**: Medium | **Type**: Functional | **Verification Method**: Unit, Integration
- **Test Description**:
  1. *Height Resolver & Monotonicity Unit Tests (`TST-UI-2788.1`)*: Verify `resolveSparklineHeight` returns `46/52/60/68.dp` for `isGraphOnly = false` and strictly taller monotonic `48/56/68/84.dp` for `isGraphOnly = true` across `XSMALL..XLARGE` (`SensorSparklineViewTest`, `SensorFieldViewGraphOnlyLayoutTest`).
  2. *Behavioral Compose Layout & Exact Canvas Height Verification (`TST-UI-2788.2`)*: Using `androidx.compose.ui.test.junit4.v2.createComposeRule()` under Robolectric (`SensorFieldViewSparklineLayoutComposeTest`), verify (a) `NORMAL` Value+Graph inside `Row(Modifier.height(IntrinsicSize.Min))` yields `sensor_sparkline_canvas` `getUnclippedBoundsInRoot().height >= 34.dp` (`>= 28.dp` net curve amplitude after `6.dp` padding) with `-T`, `-T/2`, and `Now` labels visible and unclipped, (b) multi-column `IntrinsicSize.Min` row beside a taller `XLARGE` sibling expands `sensor_sparkline_canvas` beyond its `NORMAL` baseline, and (c) externally constrained `132.dp`/`116.dp` cards preserve visible primary Value text and X-axis labels without clipping.
  3. *Rule 8.5 Visual PNG Screenshot & Regression Contracts (`TST-UI-2788.3`)*: Capture and visually inspect Light & Dark/AMOLED PNG (`docs/engineering/walkthroughs/img/ATT-8995_sparkline_value_graph_layout.png`) covering stacked `Test RR`-style `NORMAL` Value+Graph tiles, multi-column surplus expansion, compact `SMALL`, and Graph-only comparison; verify `SensorFieldViewDynamicZoneScreenshotTest`, `EditSensorFieldDialogScreenshotTest`, `ArchitectureConformanceContractTest`, `DeadCodeEliminationContractTest`, `SourceTextReadingAuditContractTest`, and `UiReflectionOnlyTestAuditContractTest` PASS (`Localization Parity: N/A - no new or modified UI strings`).
- **Verified Evidence (`a5cbbd1b`, `ATT-9382` PASS, `6/6`)**: `SensorSparklineViewTest#testResolveSparklineHeight`, `SensorFieldViewGraphOnlyLayoutTest#testResolveSparklineHeight_graphOnlyMode_enforcesStrictlyPositiveHeights`, `SensorFieldViewSparklineLayoutComposeTest#testNormalValueAndGraphInIntrinsicMinRow_canvasHeightAtLeast34dpAndLabelsVisible_tstUi2788_2`, `SensorFieldViewSparklineLayoutComposeTest#testMultiColumnRowWithTallerSibling_valueAndGraphSparklineExpandsIntoSurplusHeight_tstUi2788_2`, `SensorFieldViewSparklineLayoutComposeTest#testFixedHeightCompactCards_preserveUnclippedValueAndXAxisLabels_tstUi2788_2`, `SensorFieldViewSparklineLayoutComposeTest#testCaptureSparklineValueGraphComparisonScreenshot_tstUi2788_3`.

---

### TST-UI-2790: **Sealed UI State Models for `ImportBackupTabsScreen` & `MapScreenWithTrack` Verification Suite** (`ATT-6040`) [Verified]
- **Requirement(s)**: `REQ-UI-2790` (`REQ-UI-2790.1`–`REQ-UI-2790.3`)
- **Component**: `ImportBackupTabsScreen.kt`, `MapScreenWithTrack.kt`, `ArchitectureConformanceContractTest.kt`, `ImportBackupTabsUiContractTest.kt`, `ImportBackupTabsSealedStateComposeTest.kt`, `MapPeekSelectionSaverAndUiComposeTest.kt`
- **Jira**: `ATT-6040` | **Priority**: Medium | **Type**: Functional | **Verification Method**: Unit, Integration
- **Test Description**:
  1. *`ImportBackupTabsScreen` Sealed Dialog & Pre-Import Compose Behavioral Tests (`TST-UI-2790.1`, `ImportBackupTabsSealedStateComposeTest.kt`)*:
     - Using Robolectric `createComposeRule()`, verify all `4` `PendingRestoreSource` paths (Local/Auto/Dropbox/GoogleDrive restore confirmation -> Cancel vs Confirm ViewModel dispatch), all `3` `CloudInfoDialog` paths (Dropbox disconnected, Google Drive disconnected, No-cloud upload notice), and `PreImportTuningBottomSheet` rendering (`showStravaUploadOption = false` on `PendingImportSource.StravaArchive` vs `true` on `DropboxBulk`/`LegacyFiles`/`FitFiles`/`Directory`) plus dead `pendingSingleLegacyUri` absence.
  2. *`MapPeekSelection.Saver` Round-Trip & `MapScreenWithTrack` Mutual Exclusion Tests (`TST-UI-2790.2`, `MapPeekSelectionSaverAndUiComposeTest.kt`)*:
     - Unit-test `MapPeekSelection.Saver` `save`/`restore` round-trip for `null` and all `5` variants (`Segment`, `Route`, `Cluster`, `Location`, `Climb`) plus `StateRestorationTester` rotation survival in Compose.
     - Verify via `createComposeRule()` that selecting `Segment`/`Route`/`Cluster`/`Location` vs `Climb` enforces mutual exclusion, renders matching `sheetPeekHeight` + `sheetContent` (`Segment` -> `SegmentOnMapScreen`, `Route` -> `RouteOnMapScreen`, `Cluster` -> `ClusterOnMapSheet`, `Location` -> `KnownLocationOnMapSheet` with `SheetValue.Expanded` blocked), hides `BottomSheetScaffold` on `Climb` while showing `ClimbDetailSheet`, and resets `measuredHeaderHeight` per selection.
  3. *Targeted Regression, LoC Ratchet & Hygiene Checks (`TST-UI-2790.3`)*:
     - Execute Tier-1 targeted suites (`ImportBackupTabsUiContractTest`, `BackupCloudProviderUnificationTest`, `RestoreCardContractTest`, `ImportFormatCardsContractTest`, `ImportFormatFilePickerContractTest`, `ImportBackupMapResilienceTest`, `AppDialogActionsIntegrityTest`, `MapScreenWithTrackTest`, `FavoriteLocationSheetContractTest`, `GeneralMapLayersContractTest`, `MapDetailLayoutNavigationBarsPaddingTest`, `HardcodedStringsAndUnitsContractTest`, `ArchitectureConformanceContractTest`, `DeadCodeEliminationContractTest`, `UiReflectionOnlyTestAuditContractTest`), confirming `ImportBackupTabsScreen.kt <= 1,500` LoC removed from `MAX_MONOLITH_FILE_LOC_BASELINE`, `0` reflection-only UI stubs, `0` `src/main` reads in new tests, and `Localization Parity: N/A - 0 string changes`.
- **Verified Evidence (`cd809943`, `ATT-9485` PASS, `886/886` across 172 suites)**: `ImportBackupTabsSealedStateComposeTest#testPendingRestoreSource_autoDropboxAndGoogleDriveRestoreDialogs_cancelAndConfirmDispatch_tstUi2790_1`, `ImportBackupTabsSealedStateComposeTest#testCloudInfoDialog_disconnectedAndNoCloudPaths_showUnifiedAlertAndNeverOpenRestoreConfirm_tstUi2790_1`, `ImportBackupTabsSealedStateComposeTest#testPendingImportSource_dropboxBulkConfirmAndStravaStravaOptionSuppression_tstUi2790_1`, `ImportBackupTabsSealedStateComposeTest#testPendingImportSource_stravaArchiveHidesStravaUploadOptionWhileOtherVariantsShowIt_tstUi2790_1`, `MapPeekSelectionSaverAndUiComposeTest#testMapPeekSelectionSaver_roundTripsNullAndAllFiveVariants_tstUi2790_2`, `MapPeekSelectionSaverAndUiComposeTest#testMapPeekSelection_stateRestorationTester_survivesConfigurationChange_tstUi2790_2`, `MapPeekSelectionSaverAndUiComposeTest#testMapScreenWithTrack_targetLocationRendersKnownLocationSheetAndZeroOverlap_tstUi2790_2`, `ArchitectureConformanceContractTest#testMonolithLocRatchet_noNewOrGrowingGodClasses`.

---

### TST-UI-2791: **PMC Chart Box-to-Curve Mapping, Zero-Baseline CTL Fill, Rounded Y-Axis Ticks & Unclipped Badge Suite (`ATT-9357`)** [Verified]
- **Requirement(s)**: `REQ-UI-2791` (`REQ-UI-2791.1`–`REQ-UI-2791.6`)
- **Component**: `PerformanceManagementChartCard.kt`, `AthleteProfileScreen.kt`, `AthleteMetricsPlotMath.kt`, `AthleteMetricsDtoAndMathTest.kt`, `PerformanceManagementChartCardTest.kt`, `AthleteProfileContractAndLocalizationTest.kt`, `ArchitectureConformanceContractTest.kt`, `UiReflectionOnlyTestAuditContractTest.kt`
- **Jira**: `ATT-9357` | **Priority**: Medium | **Type**: Functional | **Verification Method**: Unit, Integration
- **Test Description**:
  1. *Pure Rounded Y-Axis Tick & `zeroY` Clamping Unit Tests (`TST-UI-2791.1`, `AthleteMetricsDtoAndMathTest#computePmcYAxisScale produces clean rounded ticks including single explicit zero across ranges (TST-UI-2791_1)`, `AthleteMetricsDtoAndMathTest#computePmcYAxisScale synchronizes yMin and yMax with tick bounds for narrow ranges (TST-UI-2791_1)`, `AthleteMetricsDtoAndMathTest#computePmcZeroBaselineY clamps CTL area baseline to zeroY or plot bounds (TST-UI-2791_1)`)*:
     - Verify `AthleteMetricsPlotMath.computePmcYAxisScale` produces clean rounded step multiples (`5, 10, 15, 20, 25, 50, ...`), strictly ascending unique ticks, unconditional `yMin == ticks.first()` / `yMax == ticks.last()` synchronization across narrow ranges (`[0, 2.86]`, `[0, 5]`, `[-4, 0]`, `[21, 24]`), and explicit `0` inclusion with zero duplicate zero lines across Pixel 10 `[-21, +25]`, flat all-zero `[0, 0]`, all-positive `[10, 85]`, negative-dominant `[-45, 5]`, wide elite `[-60, 140]`, and all-negative `[-40, -10]`.
     - Verify `AthleteMetricsPlotMath.computePmcZeroBaselineY` clamps strictly into `[topPad, topPad + plotH]`, yielding interior `zeroY` when `yMin < 0 < yMax`, `topPad + plotH` when `yMin >= 0`, and `topPad` when `yMax <= 0`.
  2. *Behavioral Compose UI, Uniform Border, Legend, Truncation & Locale Tests (`TST-UI-2791.2`, `PerformanceManagementChartCardTest#testGerman360dpViewport_optimalerAufbauUnclippedSwatchesLegendAndUniformRowHeights_tstUi2791_2`, `PerformanceManagementChartCardTest#testAcwrFormatting_respectsCurrentLocaleOverrideIndependentOfJvmDefault_tstUi2791_2`, `PerformanceManagementChartCardTest#testRow1KpiCards_uniformColoredBordersWithoutTopAccentBarInLightTheme_att9558`, `PerformanceManagementChartCardTest#testRow1KpiCards_uniformColoredBordersWithoutTopAccentBarInDarkAmoledTheme_att9558`)*:
     - Using Robolectric `v2.createComposeRule()` under German (`de-DE`) `360 dp` mobile width with `TsbReadinessZone.OPTIMAL_OVERLOAD` (`Optimaler Aufbau`), verify `Optimaler Aufbau` and all 3 Row 1 curve headers (`Fitness (42d)`, `Ermüdung (7d)`, `Form (Balance)`) render with `isLineEllipsized == false` & `didOverflowHeight == false`, thick top-only `3.dp` bars (`pmc_swatch_*`) are absent while uniform all-around colored borders (`1.5.dp`) match each curve color in Light & Dark AMOLED themes and remain distinct from neutral Row 2 borders (`ATT-9558`), 3-curve `FlowRow` legend displays cleanly, Row 1 cards share equal aligned heights (`IntrinsicSize.Min`), `ACWR` & X-axis dates format via `currentLocale` (`"3,26"` in `de-DE` vs `"3.26"` in `en-US` independent of JVM `Locale.getDefault()`), and empty state renders `athlete_profile_pmc_empty`.
  3. *`Rule 8.5` Light & Dark/AMOLED PNG Visual Screenshot & Live Emulator Verification (`TST-UI-2791.3`, `PerformanceManagementChartCardTest#testCapturePmcChartLightAndDarkComparisonPng_tstUi2791_3`, `emulator-5554`)*:
     - Capture Light and Dark/AMOLED PNG screenshot (`/tmp/att9357_verify/ATT-9357_pmc_chart_light_dark.png`, before/after `ATT-9558` in `docs/engineering/test_evidence/tickets/ATT-9558/`) reproducing the Pixel 10 zero-CTL / negative-TSB / DE `Optimaler Aufbau` scenario and inspect via `read_file` against the 4-point Visual Self-Inspection Checklist (zero blue CTL shading below `0`, clean rounded Y-axis `30, 20, 10, 0, -10, -20` with single emphasized `0`, uniform all-around `1.5.dp` colored borders on Row 1 without top-only bar, neutral `1.dp` Row 2 borders, unclipped `Optimaler Aufbau`); verify live on `emulator-5554` (`com.atrainingtracker.debug`) in Stage 5/6.
  4. *Updated Contract, Monolith Ratchet & Governance Checks (`TST-UI-2791.4`, `AthleteProfileContractAndLocalizationTest`, `ArchitectureConformanceContractTest`, `UiReflectionOnlyTestAuditContractTest`)*:
     - Verify updated `AthleteProfileContractAndLocalizationTest` (`8/8`) covers `PerformanceManagementChartCard.kt` alongside `AthleteProfileScreen.kt`, `ArchitectureConformanceContractTest` (`11/11`) confirms `AthleteProfileScreen.kt` (`1,596` LoC) cleanly inside `(1500, 1975]` LoC and `PerformanceManagementChartCard.kt` (`611` LoC `<= 1,500`), `UiReflectionOnlyTestAuditContractTest` (`4/4`) passes, and `verify_requirement_governance.py --check-living-doc-layout` exits `0` (`Localization Parity: N/A - 0 new strings`).
- **Verified Evidence (`4fe0f4af`, `ATT-9512` PASS, `36/36` Bounce 1 + `55/55` Stage 4 PASS)**: `AthleteMetricsDtoAndMathTest#computePmcYAxisScale produces clean rounded ticks including single explicit zero across ranges (TST-UI-2791_1)`, `AthleteMetricsDtoAndMathTest#computePmcYAxisScale synchronizes yMin and yMax with tick bounds for narrow ranges (TST-UI-2791_1)`, `AthleteMetricsDtoAndMathTest#computePmcZeroBaselineY clamps CTL area baseline to zeroY or plot bounds (TST-UI-2791_1)`, `PerformanceManagementChartCardTest#testGerman360dpViewport_optimalerAufbauUnclippedSwatchesLegendAndUniformRowHeights_tstUi2791_2`, `PerformanceManagementChartCardTest#testAcwrFormatting_respectsCurrentLocaleOverrideIndependentOfJvmDefault_tstUi2791_2`, `PerformanceManagementChartCardTest#testCapturePmcChartLightAndDarkComparisonPng_tstUi2791_3`, `AthleteProfileContractAndLocalizationTest#AthleteProfileScreen adheres to REQ-UI-495 layout, localization, and PMC height contracts (TST-UI-495)`, `ArchitectureConformanceContractTest#testMonolithLocRatchet_noNewOrGrowingGodClasses`, `UiReflectionOnlyTestAuditContractTest`.

---

### TST-UI-2792: **PMC ACWR Building-Base Card UX, 9-Locale Parity & Visual Verification Suite (`ATT-9360`)** [Verified]
- **Requirement(s)**: `REQ-UI-2792` (`REQ-UI-2792.1`–`REQ-UI-2792.2`)
- **Component**: `PerformanceManagementChartCard.kt`, `PerformanceManagementChartCardTest.kt`, `AthleteProfileContractAndLocalizationTest.kt`, `TranslationParityTest.kt`, `ArchitectureConformanceContractTest.kt`, `UiReflectionOnlyTestAuditContractTest.kt`
- **Jira**: `ATT-9360` | **Priority**: Medium | **Type**: Functional | **Verification Method**: Unit, Integration
- **Test Description**:
  1. *Behavioral Compose UI Building-Base & Empty-State Tests (`TST-UI-2792.1`, `PerformanceManagementChartCardTest#testAcwrBuildingBaseHint_shownNeutrallyWhenLowCtlAndOmittedWhenEmpty_tstUi2792_1`, `PerformanceManagementChartCardTest#testAcwrBuildingBaseHint_omittedWhenPmcHistoryCompletelyEmpty_tstUi2792_1`)*: Using Robolectric `v2.createComposeRule()` at `360 dp`, verify (a) `currentAcwr == null` with non-empty `pmcSummary.dailyPoints` or `pmcPlotPoints` renders `"—"` and unclipped localized `"Aufbauphase"` (DE) / `"Building base"` (EN) in neutral styling with zero `"Risikobereich"`/`"Danger Zone"` text; (b) completely empty history (`dailyPoints.isEmpty() && pmcPlotPoints.isEmpty()`) renders `"—"` with `badgeText == null` (no building-base hint); (c) `currentAcwr != null` (`CTL >= 15.0`) preserves `currentLocale` `"%.2f"` formatting.
  2. *9-Locale Parity & Architecture Contract Checks (`TST-UI-2792.2`, `TranslationParityTest`, `AthleteProfileContractAndLocalizationTest`, `ArchitectureConformanceContractTest`, `UiReflectionOnlyTestAuditContractTest`)*: Verify `athlete_profile_pmc_acwr_building_base` exists and is non-blank across all 9 locales (`EN, DE, ES, FR, IT, JA, NL, PL, PT` — 18 `athlete_profile_pmc_*` keys total), `PowerPhysiologyCalculator.kt <= 1,500` LoC, `PerformanceManagementChartCard.kt <= 1,500` LoC, and `AthleteProfileScreen.kt` stays untouched (`1,596/1,975` LoC).
  3. *`Rule 8.5` Light/Dark PNG & Live Emulator Verification (`TST-UI-2792.3`, `PerformanceManagementChartCardTest#testCapturePmcBuildingBaseLightAndDarkComparisonPng_tstUi2792_3`, `emulator-5554`)*: Capture Light and Dark/AMOLED PNG (`/tmp/att9360_verify/ATT-9360_pmc_building_base_light_dark.png`) for the `CTL = 7.0 < 15.0` cold-start state and visually inspect via `read_file` confirming `"—"` + neutral `"Aufbauphase"`, zero red alarm, and intact Row 1/Row 2 layout; verify live on `emulator-5554` in Stage 5/6.
- **Verified Evidence (`51ce6830`, `ATT-9534` PASS, `67/67` Tier-1 PASS)**: `PerformanceManagementChartCardTest#testAcwrBuildingBaseHint_shownNeutrallyWhenLowCtlAndOmittedWhenEmpty_tstUi2792_1`, `PerformanceManagementChartCardTest#testAcwrBuildingBaseHint_omittedWhenPmcHistoryCompletelyEmpty_tstUi2792_1`, `PerformanceManagementChartCardTest#testCapturePmcBuildingBaseLightAndDarkComparisonPng_tstUi2792_3`, `AthleteProfileContractAndLocalizationTest`, `TranslationParityTest`, `ArchitectureConformanceContractTest#testMonolithLocRatchet_noNewOrGrowingGodClasses`, `UiReflectionOnlyTestAuditContractTest`.

### TST-UI-2793: **ElevationProfile Canvas Hoisting, MapState Decoupling & Shared Base OkHttpClient Verification Suite (`ATT-6037`)** [Verified]
- **Requirement(s)**: `REQ-UI-2793` (`REQ-UI-2793.1`–`REQ-UI-2793.3`)
- **Component**: `ElevationProfile.kt`, `ElevationProfileDrawHelpers.kt`, `MapScreenWithTrack.kt`, `MapContentScope.kt`, `ATrainingTrackerMap.kt`, `TrackingViewModel.kt`, `SensorGridScreen.kt`, `TrackingElevationProfileSection.kt`, `TrackingTabGridContent.kt`, `AppHttpClient.kt`, `ElevationProfilePerformanceComposeTest.kt`, `SensorGridMapDecouplingComposeTest.kt`, `TrackingViewModelMapStateTest.kt`, `AppHttpClientPoolSharingTest.kt`
- **Jira**: `ATT-6037` | **Priority**: Medium | **Type**: Non-Functional | **Verification Method**: Unit, Integration
- **Test Description**:
  1. *ElevationProfile Canvas Hoisting & MapScreenWithTrack Flow Forwarding (`TST-UI-2793.1`, `ElevationProfilePerformanceComposeTest`)*:
     - Verify `precomputeClimbDrawData` extracts peak summit altitude/distance and start/end spans across populated and null-altitude climbs (`testPrecomputeClimbDrawData_extractsExactSummitAndSpanBounds_tstUi2793_1`).
     - Verify `buildClimbBadgePaintCache` caches all 6 `ClimbCategory` Paint triples scaled to `Density` (`testBuildClimbBadgePaintCache_populatesAllCategoriesWithScaledDensity_tstUi2793_1`).
     - Verify `drawElevationSegmentsCoalesced` coalesces 600 segments across 3 contiguous slope-color bands into 3 fill draw calls reusing a single `Path` in Distance, zoomed viewport, and Time domains (`testDrawElevationSegmentsCoalesced_mergesContiguousColorRunsReusingSinglePath_tstUi2793_1`).
     - Render `ElevationProfile` with climbs, zoom controls, scrubbing badge, and legend toggle via Robolectric `v2.createComposeRule()` (`testElevationProfile_rendersWithClimbsZoomControlsAndLiveScrubbingRedraws_tstUi2793_1`), and capture Light/Dark comparison PNG (`testCaptureElevationProfileLightAndDarkComparisonPng_tstUi2793_1`).
     - Render `MapScreenWithTrack` via Robolectric `v2.createComposeRule()` and assert 10 GPS `currentLocation` emissions trigger 0 host recompositions (`testMapScreenWithTrack_forwardsCurrentLocationFlowWithoutParentRecomposition_tstUi2793_1`), and verify non-`LocalInspectionMode` `MapContentScopeImpl.Render(currentZoom, contentRevision)` recomposes dynamically on monotonic revision increments at constant zoom & scope identity while skipping when unchanged (`testMapContentScopeImpl_renderUpdatesOnDynamicLayerTogglesAndTrackGrowthAtConstantZoom_tstUi2793_1`).
  2. *True `mapState` Decoupling in `TrackingViewModel` & `SensorGridScreen` (`TST-UI-2793.2`, `TrackingViewModelMapStateTest`, `SensorGridMapDecouplingComposeTest`, `TrackingMapIsolationContractTest`)*:
     - Verify `TrackingViewModel.observeMapState()` updates `mapState` immediately on speed/bearing/track ticks while `uiState` remains referentially identical (`assertSame`) with zero copy-back (`testObserveMapState_reactsImmediatelyToCurrentSpeedAndCurrentBearingWithoutCurrentTrackEmission`).
     - Render `SensorGridScreen` via Robolectric `v2.createComposeRule()` and verify 10 GPS `mapStateFlow` + `currentLocationFlow` updates trigger 0 parent/sensor-tile recompositions (`testSensorGridScreen_mapStateFlowAndLocationTicksDoNotRecomposeParentHost_tstUi2793_2`), and verify `TrackingElevationProfileSection` transitions falsifiably from `R.string.elevation_profile_empty_state` displayed -> absent when `mapStateFlow` emits an active navigated route (with `state.mapRoutes` empty) -> displayed again when cleared (`testSensorGridScreen_elevationProfileConsumesActiveNavigatedRouteFromMapStateFlow_tstUi2793_2`).
  3. *Shared Base `AppHttpClient`, Custom Timeouts & Test Injections (`TST-UI-2793.3`, `AppHttpClientPoolSharingTest`, `ElevationServiceTest`)*:
     - Verify `AppHttpClient.shared` singleton identity and `AppHttpClient.newBuilder()` `ConnectionPool`/`Dispatcher` sharing (`testSharedBaseClient_returnsSingletonAndDerivedClientsSharePoolAndDispatcher_tstUi2793_3`).
     - Verify `StravaHelper` (15/15/20 s), `ElevationService` (5/5/5 s), `GoogleDriveClient` (30/60/60 s), `StravaUploader`, and `StravaAuthRepository` share the base pool/dispatcher and preserve all custom timeouts and constructor/setter test injections (`testProductionClients_shareBasePoolPreserveCustomTimeoutsAndTestInjections_tstUi2793_3`).
  4. *Emulator Visual Verification (`Rule 8.5`) & Architecture Contracts*:
     - Deploy `com.atrainingtracker.debug` onto `emulator-5554`, verify clean `logcat -b crash -d`, capture live screenshots of `SensorGridScreen` (`Karte` tab) and `MapScreenWithTrack` plus Light/Dark `ElevationProfile` PNG (`docs/engineering/test_evidence/tickets/ATT-6037/screenshots/`), inspect all 3 PNGs via `read_file`, and confirm `ArchitectureConformanceContractTest`, `InterfaceSignatureDriftContractTest`, and `UiReflectionOnlyTestAuditContractTest` pass (`Localization Parity: N/A - 0 new or modified UI strings`).
- **Verified Evidence (`ATT-6037`, `docs/engineering/test_evidence/tickets/ATT-6037/`)**: `ElevationProfilePerformanceComposeTest#testPrecomputeClimbDrawData_extractsExactSummitAndSpanBounds_tstUi2793_1`, `ElevationProfilePerformanceComposeTest#testBuildClimbBadgePaintCache_populatesAllCategoriesWithScaledDensity_tstUi2793_1`, `ElevationProfilePerformanceComposeTest#testDrawElevationSegmentsCoalesced_mergesContiguousColorRunsReusingSinglePath_tstUi2793_1`, `ElevationProfilePerformanceComposeTest#testElevationProfile_rendersWithClimbsZoomControlsAndLiveScrubbingRedraws_tstUi2793_1`, `ElevationProfilePerformanceComposeTest#testCaptureElevationProfileLightAndDarkComparisonPng_tstUi2793_1`, `ElevationProfilePerformanceComposeTest#testMapScreenWithTrack_forwardsCurrentLocationFlowWithoutParentRecomposition_tstUi2793_1`, `SensorGridMapDecouplingComposeTest#testSensorGridScreen_mapStateFlowAndLocationTicksDoNotRecomposeParentHost_tstUi2793_2`, `SensorGridMapDecouplingComposeTest#testSensorGridScreen_elevationProfileConsumesActiveNavigatedRouteFromMapStateFlow_tstUi2793_2`, `TrackingViewModelMapStateTest#testObserveMapState_reactsImmediatelyToCurrentSpeedAndCurrentBearingWithoutCurrentTrackEmission`, `AppHttpClientPoolSharingTest#testSharedBaseClient_returnsSingletonAndDerivedClientsSharePoolAndDispatcher_tstUi2793_3`, `AppHttpClientPoolSharingTest#testProductionClients_shareBasePoolPreserveCustomTimeoutsAndTestInjections_tstUi2793_3`.

---

### TST-UI-2794: **MapScreenWithTrack Compact Sheet-Backed Climb Peek Verification Suite (`ATT-9553`)** [Verified]
- **Requirement(s)**: `REQ-UI-2794` (`REQ-UI-2794.1`–`REQ-UI-2794.3`)
- **Component**: `MapScreenWithTrack.kt`, `BottomSheetDesign.kt`, `ClimbOnMapScreen.kt`, `ClimbDetailSheet.kt`, `MapPeekSelectionSaverAndUiComposeTest.kt`, `BottomSheetVisualContractTest.kt`, `ClimbDetailSheetContractTest.kt`
- **Jira**: `ATT-9553` | **Priority**: Medium | **Type**: Functional | **Verification Method**: Unit, Integration
- **Test Description**:
  1. *Sheet-Backed Climb Peek & Dynamic `measuredHeaderHeight` Behavioral Compose Tests (`TST-UI-2794.1`, `MapPeekSelectionSaverAndUiComposeTest`)*:
     - Verify `MapPeekSelection.Climb.isSheetBacked == true`, `BottomSheetDesign.PeekHeightClimb == 192.dp`, and `MapPeekSelection.Saver` + `StateRestorationTester` survival across all 5 variants (`testMapPeekSelectionSaver_roundTripsNullAndAllFiveVariants_tstUi2790_2`, `testMapPeekSelection_stateRestorationTester_survivesConfigurationChange_tstUi2790_2`).
     - Render `MapScreenWithTrackContent` with `MapPeekSelection.Climb(505L)` via Robolectric `v2.createComposeRule()` (`testMapScreenWithTrack_selectedClimbPeeksCompactHeaderWithMeasuredHeight_tstUi2794_1`), asserting `SheetValue.PartiallyExpanded` (NOT fullscreen `Expanded`), `measuredHeaderHeight in 100.dp..280.dp`, Climb title sitting in the lower half of the viewport (`top > 500.dp`), category chip visible, and zero modal `EntityDetailSheetScaffold` close button (`R.string.Cancel`).
  2. *On-Demand Sheet Expansion, Swipe-to-Hide & System Back Dismissal (`TST-UI-2794.2`, `MapPeekSelectionSaverAndUiComposeTest`)*:
     - Verify expanding the peeked Climb sheet transitions to `SheetValue.Expanded`, moves the header to the top (`top < 120.dp`), reveals `R.string.graph_heading_elevation` and floating `R.string.share`, and hiding the sheet clears `MapPeekSelection.Climb` to `null` and unmounts the Climb header (`testMapScreenWithTrack_selectedClimbExpandsToDetailsAndDismissClearsSelection_tstUi2794_2`).
     - Verify pressing system Back clears `MapPeekSelection.Climb` to `null` and unmounts the Climb header (`testMapScreenWithTrack_backPressDismissesSelectedClimbPeek_tstUi2794_2`).
  3. *`Rule 8.5` Light/Dark PNG, Live `emulator-5554` Target Verification & Contract Parity (`TST-UI-2794.3`)*:
     - Capture and visually inspect Light & Dark/AMOLED PNG (`testCaptureMapClimbPeekLightAndDarkScreenshot_tstUi2794_3`) plus live `emulator-5554` peek & expanded screenshots (`docs/engineering/test_evidence/tickets/ATT-9553/screenshots/`) via `read_file` with clean `logcat -b crash -d`.
     - Verify `BottomSheetVisualContractTest`, `ClimbDetailSheetContractTest`, `MapScreenWithTrackTest`, `FavoriteLocationSheetContractTest`, `GeneralMapLayersContractTest`, `ElevationProfilePerformanceComposeTest`, `ArchitectureConformanceContractTest`, `UiReflectionOnlyTestAuditContractTest`, and `InterfaceSignatureDriftContractTest` PASS (`Localization Parity: N/A - 0 new or modified UI strings`).
- **Verified Evidence (`ATT-9553`, `docs/engineering/test_evidence/tickets/ATT-9553/`)**: `MapPeekSelectionSaverAndUiComposeTest#testMapPeekSelectionSaver_roundTripsNullAndAllFiveVariants_tstUi2790_2`, `MapPeekSelectionSaverAndUiComposeTest#testMapPeekSelection_stateRestorationTester_survivesConfigurationChange_tstUi2790_2`, `MapPeekSelectionSaverAndUiComposeTest#testMapScreenWithTrack_selectedClimbPeeksCompactHeaderWithMeasuredHeight_tstUi2794_1`, `MapPeekSelectionSaverAndUiComposeTest#testMapScreenWithTrack_selectedClimbExpandsToDetailsAndDismissClearsSelection_tstUi2794_2`, `MapPeekSelectionSaverAndUiComposeTest#testMapScreenWithTrack_backPressDismissesSelectedClimbPeek_tstUi2794_2`, `MapPeekSelectionSaverAndUiComposeTest#testCaptureMapClimbPeekLightAndDarkScreenshot_tstUi2794_3`, `BottomSheetVisualContractTest#testScreens_consumeStandardizedPeekHeightTokens`, `BottomSheetVisualContractTest#testDynamicHeaderMeasurement_wiringAndForwarding`, `ClimbDetailSheetContractTest#testClimbDetailSheet_structuralTokensAndContracts`.

---

### TST-UI-2795: **Workout Details Tab Section Order Verification Suite for ELEVATION and CHARTS (`ATT-9508`)** [Verified]
- **Requirement(s)**: `REQ-UI-2795` (`REQ-UI-2795.1`–`REQ-UI-2795.3`)
- **Component**: `MapDetailLayout.kt`, `WorkoutDetailTabbedContent.kt`, `WorkoutDetailSectionOrderUiTest.kt`, `WorkoutDetailTabbedContentTest.kt`, `WorkoutDetailTabsTest.kt`, `TrackOnMapLayersUiTest.kt`, `MapDetailLayoutElevationVisibilityUiTest.kt`, `TelemetryChartStackVisibilityTest.kt`
- **Jira**: `ATT-9508` | **Priority**: Medium | **Type**: Functional | **Verification Method**: Unit, Integration
- **Test Description**:
  1. *`MODE_A_CARD_SCROLL` Custom Ordering Behavioral Compose Tests (`TST-UI-2795.1`, `WorkoutDetailSectionOrderUiTest`)*:
     - Render `WorkoutDetailTabbedContent` in `MODE_A_CARD_SCROLL` with `[DETAILS, ELEVATION, ZONES, CHARTS]` (`modeA_rendersDetailsElevationZonesChartsInConfiguredOrder_tstUi2795_1`) and assert strictly `detailsTop < elevationTop < zonesTop < chartsTop` via `getUnclippedBoundsInRoot()`.
     - Render `WorkoutDetailTabbedContent` in `MODE_A_CARD_SCROLL` with `[CHARTS, ELEVATION]` (`modeA_rendersChartsBeforeElevationWhenConfigured_tstUi2795_1`) and assert strictly `chartsTop < elevationTop`.
  2. *`MODE_B_SPLIT_MAP` Pinning & Lower-Pane Ordering Compose Test (`TST-UI-2795.2`, `WorkoutDetailSectionOrderUiTest`)*:
     - Render `WorkoutDetailTabbedContent` in `MODE_B_SPLIT_MAP` with `[DETAILS, CHARTS, MAP, ZONES, ELEVATION]` (`modeB_splitMapKeepsMapPinnedAndOrdersDetailsChartsZonesElevation_tstUi2795_2`), asserting `MAP` stays pinned in the top split pane (`detailsTop > 250.dp`) while the lower scrollable pane renders strictly `detailsTop < chartsTop < zonesTop < elevationTop`.
  3. *Synchronized Scrubbing, Visibility Toggles & `HorizontalPager` Gesture Isolation (`TST-UI-2795.3`, `WorkoutDetailSectionOrderUiTest`)*:
     - Verify in a 2-tab `HorizontalPager` with `[DETAILS, ELEVATION, CHARTS]` (`customOrderedSections_preserveScrubbingVisibilityTogglesAndPagerIsolation_tstUi2795_3`) that touching `DETAILS` leaves pager swiping enabled, scrubbing inside `ELEVATION` locks pager swiping and displays `scrubbing_telemetry_badge`, pointer release restores pager swiping, and `charts_visibility_button` toggles `SPEED_PACE` cleanly without disturbing neighboring sections.
  4. *`Rule 8.5` Light/Dark PNG, Live `emulator-5554` Verification & Contract Parity (`TST-UI-2795.4`)*:
     - Capture and visually inspect Light & Dark/AMOLED PNG (`captureCustomOrderedWorkoutDetailsLightAndDarkScreenshot_tstUi2795_4`) plus live `emulator-5554` Workout Details verification screenshots (`docs/engineering/test_evidence/tickets/ATT-9508/screenshots/`) via `read_file` with clean `logcat -b crash -d`.
     - Verify existing `WorkoutDetailTabbedContentTest`, `WorkoutDetailTabsTest`, `WorkoutDetailSummaryDecouplingTest`, `TrackOnMapLayersUiTest`, `MapDetailLayoutElevationVisibilityUiTest`, `MapDetailLayoutOffscreenHeaderUiTest`, `TelemetryChartStackVisibilityTest`, `ElevationProfileOverlayTest`, `MapDetailLayoutNavigationBarsPaddingTest`, `MapDetailLayoutDynamicViewportContractTest`, `MapDetailLayoutScrubSynchronizationTest`, `ArchitectureConformanceContractTest`, and `InterfaceSignatureDriftContractTest` PASS (`Localization Parity: N/A - 0 new or modified UI strings`).
- **Verified Evidence (`ATT-9508`, `docs/engineering/test_evidence/tickets/ATT-9508/`)**: `WorkoutDetailSectionOrderUiTest#modeA_rendersDetailsElevationZonesChartsInConfiguredOrder_tstUi2795_1`, `WorkoutDetailSectionOrderUiTest#modeA_rendersChartsBeforeElevationWhenConfigured_tstUi2795_1`, `WorkoutDetailSectionOrderUiTest#modeB_splitMapKeepsMapPinnedAndOrdersDetailsChartsZonesElevation_tstUi2795_2`, `WorkoutDetailSectionOrderUiTest#customOrderedSections_preserveScrubbingVisibilityTogglesAndPagerIsolation_tstUi2795_3`, `WorkoutDetailSectionOrderUiTest#captureCustomOrderedWorkoutDetailsLightAndDarkScreenshot_tstUi2795_4`.

---

### TST-UI-2796: **Synchronized Activity & Carriage Mode Bar Verification Suite (`ATT-9172`)** [Verified]
- **Requirement(s)**: `REQ-UI-2796` (`REQ-UI-2796.1`–`REQ-UI-2796.3`)
- **Component**: `ActivityCarriageSyncBar.kt`, `MapDetailLayout.kt`, `ActivityCarriageTimelineCard.kt`, `WorkoutDetailTabConfig.kt`, `WorkoutDetailTabbedContent.kt`, `TrackOnMapScreen.kt`, `values*/strings_workout_details.xml`, `ActivityCarriageSyncBarUiTest.kt`, `WorkoutDetailTabsTest.kt`, `AdvancedTuningAccordionTest.kt`, `TranslationParityTest.kt`
- **Jira**: `ATT-9172` | **Priority**: Medium | **Type**: Functional | **Verification Method**: Unit, Integration
- **Test Description**:
  1. *Domain Projection & Active-Mode Lookup Pure-JVM Tests (`TST-UI-2796.1`, `ActivityCarriageSyncBarUiTest`)*:
     - Verify `ActivityCarriageBarMath.projectSegmentsToDomain` across `DISTANCE` (preserving zero-distance `STATIONARY` stop coordinate) and `TIME` domains, `resolveModeAtDomainPosition`, and `resolveCursorDomainPosition` across GPS Distance, GPS Time, and Trackless Time (`activityCarriageBarMath_projectsDistanceAndTimeDomainsAndResolvesActiveMode_tstUi2796_1`).
  2. *`v3|` -> `v4|` Tab Migration, Removal Persistence & Gesture Isolation (`TST-UI-2796.2`, `ActivityCarriageSyncBarUiTest`, `WorkoutDetailTabsTest`, `AdvancedTuningAccordionTest`)*:
     - Verify legacy `v3|` payloads auto-migrate `ACTIVITY_CARRIAGE_BAR` directly after `MAP` in `tab_map_graphs` without re-injecting deleted tabs, `v4|` persists user removal, `WorkoutSectionType` stays 14 entries (`toWorkoutSectionTypeOrNull() == null`), and `requiresHorizontalGestureIsolation` locks only when active and touched (`tabConfig_v3MigratesActivityCarriageBarAfterMapAndV4PreservesRemoval_tstUi2796_2`).
  3. *`Rule 64` Plot Alignment, Bidirectional Scrubbing & Zoom Coupling Compose Tests (`TST-UI-2796.3`, `ActivityCarriageSyncBarUiTest`)*:
     - Render `WorkoutDetailTabbedContent` in `MODE_B_SPLIT_MAP` (`[MAP, ACTIVITY_CARRIAGE_BAR, ELEVATION, CHARTS]`) and verify `ACTIVITY_CARRIAGE_BAR` renders below the map and strictly above `ELEVATION`/`CHARTS` with identical horizontal bounds (`Rule 64`), tapping the bar updates `activity_carriage_scrub_readout` (`WALKING_TRANSITION`) AND `scrubbing_telemetry_badge`, scrubbing `ELEVATION` updates the bar readout (`BIKE_JERSEY_BACK`), and zooming rescales cleanly (`karteUndDiagramme_rendersBarBelowMapAlignedWithElevationAndBidirectionalScrubbing_tstUi2796_3`).
  4. *Graceful Omission When Telemetry Absent & Coexistence Without `ELEVATION` (`TST-UI-2796.4`, `ActivityCarriageSyncBarUiTest`)*:
     - Verify `ACTIVITY_CARRIAGE_BAR` is omitted cleanly when `activityCarriageTimeline == null` (`karteUndDiagramme_omitsBarWhenTelemetryAbsentAndSupportsBarWithoutElevation_tstUi2796_4`) and renders directly below `MAP` above `CHARTS` when `ELEVATION` is disabled (`karteUndDiagramme_supportsActivityBarInsteadOfElevation_tstUi2796_4`).
  5. *`Rule 8.5` Light/Dark PNG, 9-Locale Parity & Contract Suites (`TST-UI-2796.5`)*:
     - Capture and inspect Light & Dark/AMOLED PNG (`captureActivityCarriageSyncBarLightAndDarkScreenshot_tstUi2796_5`, `docs/engineering/test_evidence/tickets/ATT-9172/screenshots/att9172_activity_carriage_sync_bar_light_dark.png`) via `read_file`, and verify `TranslationParityTest` (9/9 locales), `ArchitectureConformanceContractTest`, and `InterfaceSignatureDriftContractTest` PASS.
- **Verified Evidence (`ATT-9172`, `docs/engineering/test_evidence/tickets/ATT-9172/`)**: `ActivityCarriageSyncBarUiTest#activityCarriageBarMath_projectsDistanceAndTimeDomainsAndResolvesActiveMode_tstUi2796_1`, `ActivityCarriageSyncBarUiTest#tabConfig_v3MigratesActivityCarriageBarAfterMapAndV4PreservesRemoval_tstUi2796_2`, `ActivityCarriageSyncBarUiTest#karteUndDiagramme_rendersBarBelowMapAlignedWithElevationAndBidirectionalScrubbing_tstUi2796_3`, `ActivityCarriageSyncBarUiTest#karteUndDiagramme_omitsBarWhenTelemetryAbsentAndSupportsBarWithoutElevation_tstUi2796_4`, `ActivityCarriageSyncBarUiTest#karteUndDiagramme_supportsActivityBarInsteadOfElevation_tstUi2796_4`, `ActivityCarriageSyncBarUiTest#captureActivityCarriageSyncBarLightAndDarkScreenshot_tstUi2796_5`.

---

### TST-UI-2797: **TelemetryMetricGraph Dotted Grey Average Reference Line & `Ø` Y-Axis Label Verification Suite (`ATT-9170`)** [Verified]
- **Requirement(s)**: `REQ-UI-2797` (`REQ-UI-2797.1`–`REQ-UI-2797.3`)
- **Component**: `TelemetryMetricUtils.kt`, `TelemetryChartCanvasHelpers.kt`, `TelemetryMetricGraph.kt`, `TelemetryMetricGraphAverageLineTest.kt`, `TelemetryChartCanvasHelpersTest.kt`, `TelemetryMetricUtilsTest.kt`, `TelemetryMetricGraphTest.kt`, `TelemetryMetricGraphHrvUiTest.kt`, `TelemetryMetricGraphZoneTest.kt`, `ArchitectureConformanceContractTest.kt`
- **Jira**: `ATT-9170` | **Priority**: Medium | **Type**: Functional | **Verification Method**: Unit, Integration
- **Test Description**:
  1. *Metric-Accurate Averaging & Off-Scale Guard Pure-JVM Tests (`TST-UI-2797.1`, `TelemetryMetricGraphAverageLineTest`)*:
     - Verify `computeWorkoutAverage` for `PACE` derives average pace from mean positive finite speed (`unitMeters / mean(speedMps) / 60.0`) in both `METRIC` and `IMPERIAL` units rather than arithmetic mean of per-point pace values, and matches `SPEED` average mathematically (`computeWorkoutAverage_paceUsesMeanSpeedNotArithmeticMeanOfPointPaces`).
     - Verify all supported metrics (`HEART_RATE`, `POWER`, `CADENCE`, `SPEED`, `SLOPE`, `W_PRIME_BAL`, `HRV_DFA_A1`, `HRV_RMSSD`) including HRV artifact exclusion (`> 5.0%`) with all-artifact fallback and empty/non-finite `null` handling (`computeWorkoutAverage_coversAllMetricsAndFiltersHrvArtifactsAndInvalidSamples`).
     - Verify `resolveVisibleAverageValue` retains in-range anchors and cleanly hides off-scale/non-finite averages (`resolveVisibleAverageValue_hidesOffScaleAndNonFiniteValuesCleanly`).
  2. *`Ø` Formatting, Gutter Auto-Fit & Strict Collision Priority Tests (`TST-UI-2797.2`, `TelemetryMetricGraphAverageLineTest`)*:
     - Verify locale-aware `formatAverageYAxisLabel` across metrics (`Ø155`, `Ø8:15`, `Ø0,83`/`Ø0.83`, `Ø235`, `Ø1%`, `Ø0%`, `Ø-3%`, `Ø42`, `Ø4,2`) (`formatAverageYAxisLabel_matchesMetricStandardFormatsAndLocales`).
     - Verify `resolveTelemetryYAxisLabels` gives `Ø` strict top priority over colliding min/max extrema, `DFA α1` thresholds, and simulated `ATT-9164` HR zone boundaries (`Ø155` vs `157`) with zero bounding-box overlaps (`resolveTelemetryYAxisLabels_enforcesAverageStrictTopPriorityOverExtremaThresholdsAndZones`).
     - Verify `computeFittedGutterTextSize` and `computeRightAlignedGutterX` keep both compact and wide labels inside `[gutterLeftPx, gutterRightPx]` without clipping into the plot area (`gutterAutoFitAndRightAlignment_preventOverflowIntoPlotArea`).
  3. *Narrow-Canvas Guard, Dotted Pixel Rhythm & `Rule 8.5` Light/Dark AMOLED Visual Verification (`TST-UI-2797.3`, `TelemetryMetricGraphAverageLineTest`)*:
     - Verify `drawAverageReferenceLine` paints zero pixels on `plotWidth <= 0f` and paints distinct alternating round dots & clear gaps across valid plot widths (`drawAverageReferenceLine_guardsNarrowCanvasAndDrawsDistinctDottedPattern`).
     - Capture and visually inspect Light & Dark/AMOLED multi-chart PNG (`renderTelemetryAverageLineGallery_lightAndDarkAmoled_andSaveVisualEvidencePng`, `docs/engineering/test_evidence/tickets/ATT-9170/screenshots/att9170_telemetry_average_line_light_dark.png`) via `read_file` covering Heart Rate (with zones), Pace (with slow walk dip), Slope (dashed `0%` vs dotted `Ø1%`), `DFA α1` collision (`Ø0.74` suppressing `0.75` while preserving `0.20`, `0.50`, `1.40`), and `2.5x` zoomed Heart Rate constant anchor.
     - Verify existing `TelemetryChartCanvasHelpersTest`, `TelemetryMetricUtilsTest`, `TelemetryMetricGraphTest`, `TelemetryMetricGraphHrvUiTest`, `TelemetryMetricGraphZoneTest`, and `ArchitectureConformanceContractTest` PASS (`70/70` total, `0` new warnings, `ElevationProfile.kt` untouched).
- **Verified Evidence (`ATT-9170`, `docs/engineering/test_evidence/tickets/ATT-9170/`)**: `TelemetryMetricGraphAverageLineTest#computeWorkoutAverage_paceUsesMeanSpeedNotArithmeticMeanOfPointPaces`, `TelemetryMetricGraphAverageLineTest#computeWorkoutAverage_coversAllMetricsAndFiltersHrvArtifactsAndInvalidSamples`, `TelemetryMetricGraphAverageLineTest#formatAverageYAxisLabel_matchesMetricStandardFormatsAndLocales`, `TelemetryMetricGraphAverageLineTest#resolveVisibleAverageValue_hidesOffScaleAndNonFiniteValuesCleanly`, `TelemetryMetricGraphAverageLineTest#resolveTelemetryYAxisLabels_enforcesAverageStrictTopPriorityOverExtremaThresholdsAndZones`, `TelemetryMetricGraphAverageLineTest#gutterAutoFitAndRightAlignment_preventOverflowIntoPlotArea`, `TelemetryMetricGraphAverageLineTest#drawAverageReferenceLine_guardsNarrowCanvasAndDrawsDistinctDottedPattern`, `TelemetryMetricGraphAverageLineTest#renderTelemetryAverageLineGallery_lightAndDarkAmoled_andSaveVisualEvidencePng`.

---

### TST-UI-2798: **Zone-Colored Heart Rate & Power Graphs, Multi-Source Overlay Distinction & Zone-Boundary / Nice-Tick Y-Axis Verification Suite (`ATT-9164`)** [Verified]
- **Requirement(s)**: `REQ-UI-2798` (`REQ-UI-2798.1`–`REQ-UI-2798.3`)
- **Component**: `Color.kt`, `TelemetryZoneMath.kt`, `TelemetryOverlayComponents.kt`, `TelemetryMetricGraph.kt`, `TelemetryMetricGraphZoneColorUiTest.kt`, `TelemetryMetricGraphZoneTest.kt`, `TelemetryZoneMathTest.kt`, `TelemetryMetricGraphAverageLineTest.kt`, `TelemetryMetricGraphColorTest.kt`, `TelemetryMetricGraphHrvUiTest.kt`, `TelemetryMetricGraphMultiSourceUiTest.kt`, `ArchitectureConformanceContractTest.kt`
- **Jira**: `ATT-9164` | **Priority**: Medium | **Type**: Functional | **Verification Method**: Unit, Integration
- **Test Description**:
  1. *WCAG Contrast, Hard-Stepped Gradient Stops & Point/Scrub Zone Coloring Pure-JVM Tests (`TST-UI-2798.1`, `TelemetryMetricGraphZoneColorUiTest`)*:
     - Verify `TTColor.zoneStrokeColor(1..7, isDarkTheme)` satisfies WCAG 2.1 AA (`>= 3.0:1`) contrast across `LightColorScheme`, `DarkColorScheme`, and `AmoledDarkColorScheme` (`zoneStrokeColor_meetsWcagContrastAcrossLightDarkAndAmoledForAll7Zones`).
     - Verify `buildZoneBandsVerticalGradientStops` produces monotonically non-decreasing `[0f, 1f]` top-to-bottom hard-stepped stops across 5 HR zones, 7 Power zones (including Dark AMOLED `Zone7StrokeDark`), single-zone rides, and invalid bounds (`buildZoneBandsVerticalGradientStops_producesMonotonicHardSteppedStopsForHrAndPower`).
     - Verify `resolveMetricZoneStrokeColor` resolves single-point/scrub colors for HR/Power/DFA α1 and returns `null` when unconfigured or non-zoned (`resolveMetricZoneStrokeColor_resolvesZoneColorsAndNullFallbackWhenUnzoned`).
  2. *Zone-Orthogonal Multi-Source Overlay Palette Tests (`TST-UI-2798.2`, `TelemetryMetricGraphZoneColorUiTest`)*:
     - Verify `overlayStrokeColor(..., avoidZoneColors = true)` resolves theme-adapted colors strictly disjoint from `Z1`..`Z7` stroke tokens in Light and Dark (`overlayStrokeColor_avoidsZoneColorsWhenPrimaryCurveIsZoneColored`).
  3. *Unzoned Nice Round Ticks, Pixel Verification & `Rule 8.5` Light/Dark AMOLED Gallery (`TST-UI-2798.3`, `TelemetryMetricGraphZoneColorUiTest`)*:
     - Verify `calculateNiceIntermediateTicks` dynamically picks clean `10`/`20` bpm and `50`/`100`/`200` W intermediate steps for unzoned HR and Power (`calculateNiceIntermediateTicks_selectsDynamicRoundStepsForUnzonedHrAndPower`).
     - Render and inspect Light & Dark/AMOLED 4-panel PNG (`renderZoneColoredGraphsGallery_lightAndDarkAmoled_verifiesPixelsAndSavesPng`, `docs/engineering/test_evidence/tickets/ATT-9164/screenshots/att9164_zone_colored_graphs_light_dark.png`) asserting `Z1..Z5` HR stroke pixels, `Z1..Z7` Power stroke pixels (including `Zone7StrokeDark` on AMOLED), multi-source HR primary zone + contrasting blue overlay pixels, unzoned `CRIMSON` accent fallback + round ticks, and `Ø > zone > extrema` collision suppression (`66/66` PASS across 8 targeted suites, `0` new warnings).
- **Verified Evidence (`ATT-9164`, `docs/engineering/test_evidence/tickets/ATT-9164/`)**: `TelemetryMetricGraphZoneColorUiTest#zoneStrokeColor_meetsWcagContrastAcrossLightDarkAndAmoledForAll7Zones`, `TelemetryMetricGraphZoneColorUiTest#buildZoneBandsVerticalGradientStops_producesMonotonicHardSteppedStopsForHrAndPower`, `TelemetryMetricGraphZoneColorUiTest#resolveMetricZoneStrokeColor_resolvesZoneColorsAndNullFallbackWhenUnzoned`, `TelemetryMetricGraphZoneColorUiTest#calculateNiceIntermediateTicks_selectsDynamicRoundStepsForUnzonedHrAndPower`, `TelemetryMetricGraphZoneColorUiTest#overlayStrokeColor_avoidsZoneColorsWhenPrimaryCurveIsZoneColored`, `TelemetryMetricGraphZoneColorUiTest#renderZoneColoredGraphsGallery_lightAndDarkAmoled_verifiesPixelsAndSavesPng`.
### TST-UI-2799: **PeriodsScreen Compose Navigation, EditDeviceDialog Draft/Locale/Rotation & BottomSheet Insets Verification Suite (`ATT-8125`)** [Verified]
- **Target Release**: `V5.0.0`
- **Requirement(s)**: `REQ-UI-2799` (`REQ-UI-2799.1`–`REQ-UI-2799.3`)
- **Component**: `PeriodsScreen.kt`, `EditDeviceDialog.kt`, `EditDeviceViewModel.kt`, `DevicesTabbedScreen.kt`, `ATrainingTrackerApp.kt`, `FilterBottomSheetScaffold.kt`, `PeriodsScreenComposeNavigationTest.kt`, `EditDeviceDialogDraftLocaleRotationTest.kt`, `BottomSheetInsetsComposeTest.kt`, `EditDeviceViewModelTest.kt`, `EditDeviceWheelCircumferenceTest.kt`
- **Jira**: `ATT-8125` | **Priority**: Medium | **Type**: Functional | **Verification Method**: Unit, Integration
- **Test Description**:
  1. *Pure Compose Day-Click Navigation in `PeriodsScreen` (`TST-UI-2799.1`, `PeriodsScreenComposeNavigationTest`)*:
     - Verify `buildDayPeriodSummary` produces exact `PeriodType.DAY` `[dayStartS, dayEndS]` bounds and sport stats (`testBuildDayPeriodSummary_producesExactDayBoundsAndStats_tstUi2799_1`).
     - Render `PeriodsScreen` inside a real Compose `NavHost` via Robolectric `v2.createComposeRule()`, tap an active day cell & selected-day banner, verify pure Compose routing to `NavRoutes.workouts(pushed = true)` with exact day `WorkoutFilterCriteria` + `scrollToWorkoutId`, verify clean back pop to `PeriodsScreen`, and assert zero `Fragment` references in `PeriodsScreenKt` (`testPeriodsScreen_dayClickRoutesStrictlyViaComposeNavHostAndBackReturnsCleanly_tstUi2799_1`).
  2. *`de_DE` Comma/Dot Decimal Input, Rotation Survival & Cancel Zero-Write Persistence (`TST-UI-2799.2`, `EditDeviceDialogDraftLocaleRotationTest`, `EditDeviceViewModelTest`, `EditDeviceWheelCircumferenceTest`, `DeviceDataRepositoryEquipmentSyncTest`)*:
     - Verify `parseLocaleDecimal` accepts comma and dot decimals while rejecting invalid/non-finite strings (`testParseLocaleDecimal_acceptsCommaAndDotAndRejectsInvalid_tstUi2799_2`).
     - Render `RunCalibrationFactorContent` under `Locale.GERMANY`, assert `"1,0000"` initial formatting, `"1,085"` comma entry, `StateRestorationTester` survival across rotation, `"1.125"` dot entry, and comma calculation inside `CorrectCalibrationDialog` (`testRunCalibrationFactor_germanLocaleAcceptsCommaAndDotAndSurvivesRestoration_tstUi2799_2`).
     - Verify `EditDeviceViewModel` preserves unsaved name, calibration, ATT Odometry Fusion, ATT Altitude Fusion, TPMS baseline reset, and Radar light mode edits across simulated `loadInitialDeviceData` rotation calls, persists zero writes on `discardDraft()` (`Cancel`), and commits all staged edits cleanly on `saveChanges()` (`testViewModel_rotationPreservesDirtyDraftsAndCancelPersistsZeroWrites_tstUi2799_2`, `testEditDeviceViewModel_stagesRadarTailLightInDraftAndDiscardsOnCancel`, `testEditDeviceViewModel_asyncHistoryLoadStaleClearAndDraftBaselineSync_tstSen033_2`, `testEditDeviceViewModel_loadInitialDeviceData_alwaysFetchesFreshSnapshot`).
  3. *Single Non-Doubled Bottom-Sheet Insets & Filter IME Padding (`TST-UI-2799.3`, `BottomSheetInsetsComposeTest`, `BottomSheetVisualContractTest`, `ModalBottomSheetDialogsIntegrityTest`)*:
     - Dispatch synthetic `WindowInsetsCompat` (`navigationBars` + `ime = 300px`) to `FilterBottomSheetBody` and verify filter text inputs lift upward by `~100dp` (1x IME) above the keyboard (`testFilterBottomSheetBody_shiftsInputUpwardBySingleImeInset_tstUi2799_3`).
     - Dispatch synthetic `WindowInsetsCompat` to `AppBottomSheetContent` and verify bottom inset delta is strictly 1x (`~100dp`, zero doubling) (`testAppBottomSheetContent_appliesBottomNavigationAndImeInsetsStrictlyOnce_tstUi2799_3`).
  4. *`Rule 8.5` Light/Dark PNG, Live `emulator-5554` Verification & Contract Suites (`TST-UI-2799.4`)*:
     - Capture and visually inspect Light/Dark PNG (`testCaptureLightAndDarkVisualEvidencePng_Rule8_5_tstUi2799_4`) and live `emulator-5554` `Units` Settings `ModalBottomSheet` screenshot (`docs/engineering/test_evidence/tickets/ATT-8125/screenshots/`) via `read_file` confirming clean single bottom inset and `0` crashes in `logcat -b crash -d`.
     - Verify `ArchitectureConformanceContractTest`, `AndroidViewModelReflectionContractTest`, `UiReflectionOnlyTestAuditContractTest`, `BottomSheetVisualContractTest`, `ModalBottomSheetDialogsIntegrityTest`, and `ATrainingTrackerAppNavigationTest` PASS (`98/98` tests across 17 suites; `Localization Parity: N/A - 0 new or modified UI strings`).
- **Verified Evidence (`ATT-8125`, `docs/engineering/test_evidence/tickets/ATT-8125/`)**: `PeriodsScreenComposeNavigationTest#testBuildDayPeriodSummary_producesExactDayBoundsAndStats_tstUi2799_1`, `PeriodsScreenComposeNavigationTest#testPeriodsScreen_dayClickRoutesStrictlyViaComposeNavHostAndBackReturnsCleanly_tstUi2799_1`, `EditDeviceDialogDraftLocaleRotationTest#testParseLocaleDecimal_acceptsCommaAndDotAndRejectsInvalid_tstUi2799_2`, `EditDeviceDialogDraftLocaleRotationTest#testRunCalibrationFactor_germanLocaleAcceptsCommaAndDotAndSurvivesRestoration_tstUi2799_2`, `EditDeviceDialogDraftLocaleRotationTest#testViewModel_rotationPreservesDirtyDraftsAndCancelPersistsZeroWrites_tstUi2799_2`, `EditDeviceDialogDraftLocaleRotationTest#testCaptureLightAndDarkVisualEvidencePng_Rule8_5_tstUi2799_4`, `BottomSheetInsetsComposeTest#testFilterBottomSheetBody_shiftsInputUpwardBySingleImeInset_tstUi2799_3`, `BottomSheetInsetsComposeTest#testAppBottomSheetContent_appliesBottomNavigationAndImeInsetsStrictlyOnce_tstUi2799_3`, `EditDeviceViewModelTest#testEditDeviceViewModel_stagesRadarTailLightInDraftAndDiscardsOnCancel`, `EditDeviceWheelCircumferenceTest#testEditDeviceViewModel_asyncHistoryLoadStaleClearAndDraftBaselineSync_tstSen033_2`.

---

### TST-UI-2800: **Live Sensor & Cockpit Hotpath Verification Suite (`ATT-8126`)** [Verified]
- **Target Release**: `V5.0.0`
- **Requirement(s)**: `REQ-UI-2800` (`REQ-UI-2800.1`–`REQ-UI-2800.4`)
- **Component**: `BANALServiceRepository.kt`, `FilteredSensorData.kt`, `TrackingViewModel.kt`, `SensorFieldState.kt`, `WorkoutRepository.kt`, `EquipmentRepository.kt`, `SportTypesRepository.kt`, `RrArtifactFilter.kt`, `ProxySensor.java`, `FilterData.kt`, `FilterManager.kt`, `MyFilter.kt`, `DeviceManager.java`, `TrackingViewsDatabaseManager.java`, `EditSensorFieldViewModel.kt`, `TrackingTabGridContent.kt`, `TrackingTabsScreen.kt`, `BANALServiceRepositoryHotpathTest.kt`, `RrArtifactFilterPerformanceEquivalenceTest.kt`, `ProxySensorAndFilterManagerIdKeyTest.kt`, `CockpitRecompositionBoundsComposeTest.kt`
- **Jira**: `ATT-8126` | **Priority**: High | **Type**: Non-Functional | **Verification Method**: Unit, Integration
- **Test Description**:
  1. *Repository Dispatchers, Conflation + Sparkline/W'bal Liveness & Concurrency (`TST-UI-2800.1`, `BANALServiceRepositoryHotpathTest`, `TrackingViewModelGridTest`)*:
     - Verify `BANALServiceRepository`, `WorkoutRepository`, `EquipmentRepository`, and `SportTypesRepository` scopes do not dispatch on `Dispatchers.Main`.
     - Verify NaN-safe structural `FilteredSensorData.equals`/`hashCode` (`Objects.equals`, `mFilterConstant.toBits()`, ignoring `mTimestamp`) conflates identical 1-Hz snapshots in `_allFilteredSensorData`, while decoupled `_sparklineTick` and `_wPrimeTelemetryHeartbeat` keep steady-value `showGraph` sparklines advancing and `W_PRIME_BAL` freshness alive (and still mark STALE when `W_PRIME_BAL` stops emitting).
     - Verify `bindToBANALService()` false/throw reset and unbind-before-connect + `onServiceConnected`/`onServiceDisconnected` under `connectionLock`, plus `itpmsLock` (`loadAndApplyItpmsBaseline` atomic check+apply, `persistConvergedCircumferences` snapshot) and `trackLock` (`applyRetroactiveAltitudeCorrection`).
  2. *`RrArtifactFilter` `O(N)` Precomputation, 92-Element Causal Window & Thread-Safety (`TST-UI-2800.2`, `RrArtifactFilterPerformanceEquivalenceTest`, `RrArtifactFilterTest`)*:
     - Verify 300-beat RR windows (covering `filterLiveCausal`'s 92-element `[i - 86, i + 6)` window) across normal, ectopic, missed, short, and gap beats yield bit-exact `FilterResult` outputs, thread-safe concurrent evaluation on one shared instance (method-local scratch buffers), and bounded latency (`< 250 ms` for 50 runs).
  3. *`ProxySensor`/`FilterManager` Single Initial Push & End-to-End `deviceId` Rename Survival (`TST-UI-2800.3`, `ProxySensorAndFilterManagerIdKeyTest`, `FilterManagerRebindTest`)*:
     - Verify `ProxySensor.addSensorListener` delivers a single non-null initial value when bound (never leading `null`) and handles concurrent `setSourceSensor` swap via post-register re-check without holding `mSourceLock`.
     - Verify `FilterManager.rebindFilter` ingests the bound sensor's current value strictly once into moving-average / smoothing filters.
     - Verify `FilterData(deviceId > 0L)` keeps stable `hashKey`, resolves directly via `DeviceManager.getMyDeviceById` without name fallback when non-null, syncs `MyFilter.mDeviceName`, and keeps both `TrackingViewModel` tiles and `BANALServiceRepository` sparklines live across device renames while preserving global (`null`) and legacy name-only fallbacks.
  4. *Cockpit 1-Hz Recomposition Bounds & Contracts (`TST-UI-2800.4`, `CockpitRecompositionBoundsComposeTest`, `SensorGridMapDecouplingComposeTest`, `ArchitectureConformanceContractTest`)*:
     - Verify `gridActions` referential stability (`===`) across 1-Hz `uiState` updates in `TrackingTabGridContent`.
     - Verify `TrackingTabsScreen` skips `allFilteredSensorData` collection in `DisplayBrightnessMode.SYSTEM`/`CUSTOM`, invokes `batterySaverController.updateTelemetry` only in `AUTO`, triggers `0` root recompositions on 1-Hz sensor/GPS ticks, and preserves `beyondViewportPageCount == 0`.
     - Verify monolith LoC ceilings (`DeviceManager.java <= 1,708`, `WorkoutRepository.kt <= 5,117`, `TrackingViewsDatabaseManager.java <= 2,631`), `0` new compiler warnings, and live `emulator-5554` cockpit check with clean `logcat -b crash -d`.

---

### TST-UI-2801: **Legacy UI Decommissioning Part 1 & Pure Compose Dialogs Verification Suite (`ATT-8129`)** [Verified]

- **Requirement(s)**: `REQ-UI-2801` (`REQ-UI-2801.1`–`REQ-UI-2801.3`)
- **Jira**: `ATT-8129` (Epic `ATT-8120`) | **Target Release**: `V5.0.0`
- **Verification Scope**:
  1. *Physical & Classpath Excision (`TST-UI-2801.1`, `LegacyUiDecommissioningComposeTest`, `ModalBottomSheetDialogsIntegrityTest`, `DropboxSettingsDialogTest`, `AdvancedTuningModularityTest`, `WorkoutSummariesTabbedScreenTest`)*:
     - Verify all 27 excised source files and `StartOrResumeDialog` `DialogFragment` wrapper do not exist on disk or in classpath (`ClassNotFoundException`).
     - Verify `MainActivityWithNavigation` declares zero `mFragment`/`mDrawerLayout` fields and no longer implements `StartOrResumeInterface` (`MainActivityWithNavigationInteropTest`).
  2. *Pure Compose Dialog Behavior & Visual Verification (`TST-UI-2801.2`, `LegacyUiDecommissioningComposeTest`, `StartOrResumeDialogContractTest`, `StartOrResumeDialogLayoutTest`, `MainActivityLifecycleTest`, `MainActivityIntentLifecycleTest`, `PeriodsNavigationFilterTest`)*:
     - Verify `StartOrResumeAlertDialog` & `StartOrResumeDialogContent` render all 4 `KillReason` diagnoses, dispatch `onStartNew`/`onResume`/`onDisableBatteryOptimization`, and pass 9-locale localization parity.
     - Verify `GPSDisabledAlertDialog` renders localized strings (`gps_disabled_on_device`, `gps_goto_settings`, `Cancel`) and dispatches `onOpenLocationSettings`/`onDismiss`.
     - Verify pure Compose `EditDeviceDialog` opens from state without `FragmentManager` or `EditDeviceFragmentFactory`.
     - Capture Light/Dark Compose PNG and live `emulator-5554` screenshot, inspect via `read_file` (`Rule 8.5`), and verify clean `logcat -b crash -d`.
  3. *Governance & Architectural Contracts (`TST-UI-2801.3`, `DeadCodeEliminationContractTest`, `SourceTextReadingAuditContractTest`, `SystemLiveTrackingRequirementsContractTest`, `ArchitectureConformanceContractTest`, `UiReflectionOnlyTestAuditContractTest`)*:
     - Verify `source-reading-baseline.txt` ratcheted cleanly (`105 -> 104`), `SYSREQ-TRK-013` code grounding intact, `0` new compiler warnings, and `verify_requirement_governance.py` exit `0`.

### TST-UI-2802: **Legacy UI & Package Decommissioning Part 2 Verification Suite (`ATT-8130`)** [Verified]
- **Requirement(s)**: `REQ-UI-2802`
- **Component**: `LegacyPackageDecommissioningTest.kt`, `BTLERadarDeviceTest.kt`, `HardwareHalIngestionIntegrationTest.kt`, `DatabaseSchemaRegistryParityContractTest.kt`, `DeadCodeEliminationContractTest.kt`, `ManifestAndBuildConfigContractTest.kt`, `SystemContextContractTest.kt`, `SystemArchitectureDesignContractTest.kt`, `ProGuardRulesContractTest.kt`, `AdrCompletenessContractTest.kt`, `ArchitectureConformanceContractTest.kt`, `AdvancedTuningModularityTest.kt`, `HrvDfaA1SettingsContractTest.kt`, `ZonesSettingsPreviewPillsContractTest.kt`, `ZonesSettingsHeaderContractTest.kt`
- **Jira**: `ATT-8130`
- **Test Description**:
  1. *Classpath & Manifest Excision (`TST-UI-2802.1`, `LegacyPackageDecommissioningTest`, `DeadCodeEliminationContractTest`, `ManifestAndBuildConfigContractTest`, `ProGuardRulesContractTest`, `DatabaseSchemaRegistryParityContractTest`)*:
     - Verify all 23 excised classes (`AdvancedTuningDialogKt`, `ClusterTuningScreenKt`, `ZonesSettingsActivity`, `TrackOnMapHelper`, `Event`, `DynamicalPowerHrEngineKt`, `ui.components.FastScrollbarKt`, `BottomSheetUtilsKt`, `au.com.bytecode.opencsv.*`, `smartwatch.pebble.*`, `ConfigViewsActivityClassic`, `ConfigViewsFragment`, `ConfigViewFragment`), `SettingsBottomSheetType.ADVANCED_TUNING`, `HrvTestPhase.PRE_FLIGHT`, `MyHelper.formatRank`, `PebbleView.db`, `pebblekit:4.0.1`, and 3 orphaned XML layouts (`res/layout/` `9 -> 6`) are absent.
  2. *Behavioral Replacement Parity (`TST-UI-2802.2`, `LegacyPackageDecommissioningTest`, `BTLERadarDeviceTest`, `HardwareHalIngestionIntegrationTest`, `ZonesSettings*`, `HrvDfaA1*`, `AdvancedTuning*`)*:
     - Verify `Rfc4180CsvWriter` exact RFC-4180 quoting, `""` escaping, embedded newlines, and `null` field parity.
     - Verify shared `ZoneLivePreviewTileCard` Compose rendering, modular `ui/settings/zones/` screens, `AdvancedTuningScreen` with `SwimmingSection`, and live `emulator-5554` navigation + `Rule 8.5` PNG inspection.
     - Verify `BTLERadarDevice` accepts authentic Garmin Varia V1 (`0x*2`) and RearVue 820 V2 (`2 + 9*N`, 2-byte heartbeat, 5/6-byte status) frames while rejecting synthetic `0x00` and `0x01` frames.
  3. *Architecture & System Code-Grounding (`TST-UI-2802.3`, `SystemContextContractTest`, `SystemArchitectureDesignContractTest`, `AdrCompletenessContractTest`, `ArchitectureConformanceContractTest`, `SourceTextReadingAuditContractTest`)*:
     - Verify `ZonesSettingsActivity.kt` removed from monolith allowlist, PebbleKit moved to Domain 5 (`PERMANENTLY DECOMMISSIONED`) in `system_context.md` and removed from `SYS-EL-04`/`SYS-IF-04`, `ADR-006` and `docs/architecture.md` code-grounded, `0` new compiler warnings, and `verify_requirement_governance.py` exit `0`.

---

## Change History

| Version | Date | Author | Ticket | Summary of Changes |
| :--- | :--- | :--- | :--- | :--- |
| `1.0.0` | 2026-11-18 | AI Agent (`ATT-8206`) | `ATT-8206` | Split oversized shard into compliant sub-shard (<= 4,000 lines, `REQ-PRO-093.4`, `REQ-PRO-127.3`). |
| `1.0.2` | 2026-11-18 | AI Agent (`ATT-8712`) | `ATT-8712` | Aligned `TST-UI-2783.1` (`HrvDfaA1SettingsContractTest` <-> `REQ-TRK-087`) cleanly post-split (`ATT-8206`). |
| `1.0.3` | 2026-11-18 | AI Agent (`ATT-8749`) | `ATT-8749` | Added `TST-UI-2785` (`[Planned]`) for `175x120 dp` preview map, `120.0 dp` zoom calibration, and `360dp` behavioral Compose UI verification. |
| `1.0.4` | 2026-11-18 | AI Agent (`ATT-8749`) | `ATT-8749` | Promoted `TST-UI-2785` to `[Verified]` after `89/89` unit/UI tests and `Rule 8.5` PNG inspection. |
| `1.0.6` | 2026-11-18 | AI Agent (`ATT-8748`) | `ATT-8748` | Updated `TST-UI-493` to `[Verified]` (`ClusterItemThumbnailComposeTest.kt`, `ClusterItemAndFilterPolishTest.kt`, `151/151` Tier 1 XML tests, `Rule 8.5` PNG). |
| `1.0.7` | 2026-11-18 | AI Agent (`ATT-8097`) | `ATT-8097` | Added `TST-UI-2786` (`[Planned]`) for Workout Detail header action uniformity, `[Tune] \| [⇄][Edit][Share]` grouping, universal `⇄` / non-GPS fallback, 9-locale tooltips, and `Rule 8.5` Light/Dark preview. |
| `1.0.8` | 2026-11-18 | AI Agent (`ATT-8097`) | `ATT-8097` | Promoted `TST-UI-2786` to `[Implemented]` (`WorkoutDetailHeaderActionsComposeTest` + `TrackOnMapClusterActionViewModelTest` + `90/90` targeted tests PASS, `Rule 8.5` PNG). |
| `1.0.9` | 2026-11-18 | AI Agent (`ATT-8097`) | `ATT-8097` | Promoted `TST-UI-2786` to `[Verified]` after Stage 5 verification (`90/90` XML tests PASS, `has_verified_test_evidence` `True`, `Rule 8.5` PNG). |
| `1.0.10` | 2026-11-18 | AI Agent (`ATT-8079`) | `ATT-8079` | Added `TST-UI-2787` (`[Planned]`) verifying `REQ-UI-2787` `v3|` persistence, summary toggle decoupling, dynamic header action reservation, and tab-driven settings/subtitle. |
| `1.0.11` | 2026-11-18 | AI Agent (`ATT-8079`) | `ATT-8079` | Promoted `TST-UI-2787` to `[Implemented]` in Stage 4 (`WorkoutDetailTabsTest`, `WorkoutDetailSummaryDecouplingTest`, `WorkoutDetailHeaderActionsComposeTest`, `WorkoutMasksAndCardsBehaviorTest`, `AdvancedTuningAccordionTest`). |
| `1.0.12` | 2026-11-18 | AI Agent (`ATT-8079`) | `ATT-8079` | Promoted `TST-UI-2787` to `[Verified]` in Stage 5 (`94/94` Tier 1 JUnit XMLs PASS, `Rule 8.5` PNG inspected). |
| `1.0.13` | 2026-11-20 | Lane A Flash (`ATT-8330`, `--as agent1`) | `ATT-8330` | Rebalanced `TST-UI-250..254` from `tst_ui_design_system_part1.md` to satisfy `<= 4,000` line shard limit (`REQ-PRO-093.3`). |
| `1.0.14` | 2026-11-20 | Lane A Author (`ATT-8995`, `--as agent1`) | `ATT-8995` | Added `TST-UI-2788` (`[Planned]`, `.1`–`.3`, strictly symmetric `REQ-UI-2788`) covering `resolveSparklineHeight` monotonicity, `v2.createComposeRule()` exact `sensor_sparkline_canvas` height & fixed-card safety, and `Rule 8.5` Light/Dark visual PNG (`N/A - no new or modified UI strings`). |
| `1.0.15` | 2026-11-20 | Lane A Author (`ATT-8995`, `--as agent1`) | `ATT-8995` | Promoted `TST-UI-2788` to `[Implemented]` (`SensorSparklineViewTest`, `SensorFieldViewGraphOnlyLayoutTest`, `SensorFieldViewSparklineLayoutComposeTest` `4/4`, `Rule 8.5` PNG `ATT-8995_sparkline_value_graph_layout.png`, `6/6` verbatim `TestClass#testMethod` citations). |
| `1.0.16` | 2026-11-20 | Lane A Author (`ATT-8995`, `--as agent1`) | `ATT-8995` | Promoted `TST-UI-2788` to `[Verified]` in Stage 5 reusing Gate 4 (`ATT-9382` @ `a5cbbd1b`) `57/57 PASS` across 11 suites + `:app:assembleDebug` + `0` new Kotlin/deprecation warnings (`Rule 7.5`, `0` `app/` diff since `a5cbbd1b`, `6/6` verbatim `TestClass#testMethod` citations, `Rule 8.5` Light/Dark PNG certified, `4/4` safety linters PASS, `--check-verified-evidence` `exit 0`). |
| `1.0.17` | 2026-11-21 | Lane 2 Author (`ATT-6040`, `--as agent1`) | `ATT-6040` | Added `TST-UI-2790` `[Planned]` covering sealed dialog/pre-import Compose UI behavior, `MapPeekSelection.Saver` round-trip & `StateRestorationTester` rotation survival, sheet/modal mutual exclusion, and monolith LoC ratchet. |
| `1.0.18` | 2026-11-21 | Lane 2 Author (`ATT-6040`, `--as agent1`) | `ATT-6040` | Promoted `TST-UI-2790` to `[Implemented]` following Stage 4 implementation and Tier-1 unit/Compose verification. |
| `1.0.19` | 2026-11-21 | Lane 2 Author (`ATT-6040`, `--as agent1`) | `ATT-6040` | Promoted `TST-UI-2790` to `[Verified]` in Stage 5 (`Rule 7.5` Doc-Only Fast Path reusing Gate 4 `ATT-9485` @ `cd809943`: `886/886 PASS` across 172 suites, `8/8` verbatim `TestClass#testMethod` citations, `--check-verified-evidence` `exit 0`). |
| `1.0.20` | 2026-11-21 | Single-Lane Author (`ATT-9357`, `--as agent1`) | `ATT-9357` | Added `TST-UI-2791` (`[Planned]`, `.1`–`.4`, strictly symmetric `REQ-UI-2791`) covering pure rounded Y-axis scale & `zeroY` unit tests, Robolectric `createComposeRule()` DE `Optimaler Aufbau` / `currentLocale` / swatch & legend tests, `Rule 8.5` Light/Dark PNG + `emulator-5554` verification, and monolith/contract checks (`0` new strings). |
| `1.0.21` | 2026-11-21 | Single-Lane Author (`ATT-9357`, `--as agent1`) | `ATT-9357` | Promoted `TST-UI-2791` to `[Implemented]` with exact test method citations (`55/55 PASS` across 7 Tier-1 suites, `0` new compiler warnings, `3/3` Python safety linters PASS, `Rule 8.5` Light/Dark PNG `/tmp/att9357_verify/ATT-9357_pmc_chart_light_dark.png` inspected). |
| `1.0.22` | 2026-11-21 | Single-Lane Author (`ATT-9357`, `--as agent1`) | `ATT-9357` | Promoted `TST-UI-2791` to `[Verified]` in Stage 5 (`Rule 7.5` Doc-Only Fast Path reusing Gate 4 `ATT-9506` @ `04271fff`: `55/55` Stage 4 + `35/35` Gate 4 PASS, `0` `app/` drift, `3/3` safety linters PASS, `Rule 8.5` PNG certified, `--check-verified-evidence` `exit 0`, `docs/engineering/walkthroughs/ATT-9357_walkthrough.md`). |
| `1.0.23` | 2026-11-21 | Single-Lane Author (`ATT-9357`, `--as agent1`) | `ATT-9357` | Re-promoted `TST-UI-2791` to `[Verified]` in Stage 5 Bounce 1 (`Rule 7.5` Doc-Only Fast Path reusing Gate 4 Bounce 1 `ATT-9512` @ `4fe0f4af`: `36/36 PASS` incl. narrow-range Y-axis bound sync test, `0` `app/` drift, `3/3` safety linters PASS, `Rule 8.5` PNG re-certified, `--check-verified-evidence` `exit 0`). |
| `1.0.24` | 2026-11-21 | Single-Lane Author (`ATT-9360`, `--as agent1`) | `ATT-9360` | Added `TST-UI-2792` (`[Planned]`, `.1`–`.3`, strictly symmetric `REQ-UI-2792`) covering Robolectric `v2.createComposeRule()` ACWR building-base & empty-state UI tests, 9-locale `TranslationParityTest` / `AthleteProfileContractAndLocalizationTest` parity, and `Rule 8.5` Light/Dark PNG + `emulator-5554` verification. |
| `1.0.25` | 2026-11-21 | Single-Lane Author (`ATT-9360`, `--as agent1`) | `ATT-9360` | Promoted `TST-UI-2792` to `[Implemented]` with exact verbatim `PerformanceManagementChartCardTest` method citations and `Rule 8.5` PNG path. |
| `1.0.26` | 2026-11-21 | Single-Lane Author (`ATT-9360`, `--as agent1`) | `ATT-9360` | Promoted `TST-UI-2792` to `[Verified]` in Stage 5 (`Rule 7.5` Doc-Only Fast Path reusing Gate 4 `ATT-9534` @ `51ce6830`: `67/67 PASS` incl. `TranslationParityTest` `11/11`, `0` `app/` drift, `Rule 8.5` PNG certified, `--check-verified-evidence` `exit 0`, `docs/engineering/walkthroughs/ATT-9360_walkthrough.md`). |
| `1.0.27` | 2026-11-21 | Lane 3 Author (`ATT-9558`, `--as agent1`) | `ATT-9558` | Updated `TST-UI-2791.2`/`.3` for `ATT-9558`: removed top-only `3.dp` bar assertions and added Light & Dark AMOLED uniform colored border pixel-symmetry tests (`testRow1KpiCards_uniformColoredBordersWithoutTopAccentBarInLightTheme_att9558`, `testRow1KpiCards_uniformColoredBordersWithoutTopAccentBarInDarkAmoledTheme_att9558`). |
| `1.0.28` | 2026-11-21 | Lane 1 Author (`ATT-6037`, `--as agent1`) | `ATT-6037` | Added and verified `TST-UI-2793` (`[Verified]`, `.1`–`.3`) covering `ElevationProfile` Canvas hoisting/coalescing + Light/Dark PNG, `MapScreenWithTrack` `StateFlow` forwarding, `TrackingViewModel`/`SensorGridScreen` true `mapState` decoupling, shared `AppHttpClient` pool/timeout/injection tests, and `emulator-5554` visual checks. |
| `1.0.29` | 2026-11-22 | Lane 2 Author (`ATT-9553`, `--as agent1`) | `ATT-9553` | Added and verified `TST-UI-2794` (`[Verified]`, `.1`–`.3`) covering Robolectric `v2.createComposeRule()` Climb peek/expand/hide/BackHandler tests, Light/Dark PNG + live `emulator-5554` peek/expand/dismiss checks, and `BottomSheetVisualContractTest`/`ClimbDetailSheetContractTest` parity (`76/76` targeted tests PASS). |
| `1.0.30` | 2026-11-22 | Lane 2 Author (`ATT-9508`, `--as agent1`) | `ATT-9508` | Added and verified `TST-UI-2795` (`[Verified]`, `.1`–`.4`) covering Robolectric `v2.createComposeRule()` section ordering in `MODE_A_CARD_SCROLL` and `MODE_B_SPLIT_MAP`, synchronized scrubbing, chart visibility toggles, `HorizontalPager` gesture isolation & pointer-up unlock, Light/Dark PNG, and live `emulator-5554` verification (`82/82` targeted tests PASS across 14 suites). |
| `1.0.31` | 2026-11-22 | Lane 2 Author (`ATT-9172`, `--as agent1`) | `ATT-9172` | Added and verified `TST-UI-2796` (`[Verified]`, `.1`–`.5`) covering `ActivityCarriageBarMath` `DISTANCE`/`TIME` projection, `v3|` -> `v4|` migration & removal persistence, `Rule 64` plot alignment, bidirectional scrubbing & zoom coupling, absence omission & `ELEVATION` coexistence, 9-locale `TranslationParityTest`, and Light/Dark `Rule 8.5` PNG (`78/78` targeted tests PASS across 8 suites). |
| `1.0.32` | 2026-11-22 | Lane 2 Author (`ATT-9170`, `--as agent1`) | `ATT-9170` | Added and verified `TST-UI-2797` (`[Verified]`, `.1`–`.3`) covering speed-derived `PACE` & multi-metric averaging, off-scale hiding, `Ø` gutter auto-fit & priority collision suppression, narrow-canvas guard, dotted pixel rhythm, and Light/Dark AMOLED `Rule 8.5` PNG (`70/70` targeted tests PASS across 7 suites, `0` new warnings). |
| `1.0.33` | 2026-11-22 | Lane 2 Author (`ATT-9164`, `--as agent1`) | `ATT-9164` | Added and verified `TST-UI-2798` (`[Verified]`, `.1`–`.3`) covering WCAG `Z1..Z7` stroke contrast, hard-stepped vertical gradient stops, single-point/scrub zone colors, zone-orthogonal multi-source overlay palette, dynamic unzoned round ticks, and Light/Dark AMOLED `Rule 8.5` PNG (`66/66` targeted tests PASS across 8 suites, `0` new warnings). |
| `1.0.32` | 2026-11-22 | Lane 3 Author (`ATT-8125`, `--as agent1`) | `ATT-8125` | Added and verified `TST-UI-2799` (`[Verified]`, `.1`–`.4`) covering pure Compose `PeriodsScreen` day-click NavHost routing, `de_DE` comma/dot calibration input + `StateRestorationTester` + Save-only/Cancel-zero-write draft tests for Fused/TPMS/Radar, single 1x `WindowInsetsCompat` bottom-sheet tests for `FilterBottomSheetBody` & `AppBottomSheetContent`, and Light/Dark + live `emulator-5554` PNG verification (`98/98` PASS across 17 suites). |
| `1.0.35` | 2026-11-22 | Lane 3 Author (`ATT-8126`, `--as agent1`) | `ATT-8126` | Added `TST-UI-2800` (`[Planned]`, `.1`–`.4`) covering background repository scopes, `FilteredSensorData` `StateFlow` conflation & unbind-before-connect leak tests, `RrArtifactFilter` `O(N)` equivalence/performance tests, single-push `ProxySensor`/`FilterManager` & `deviceId` rename survival tests, and cockpit 1-Hz recomposition bounds (`TrackingTabGridContent`, `TrackingTabsScreen`). |
| `1.0.36` | 2026-11-22 | Lane 3 Author (`ATT-8126`, `--as agent1`) | `ATT-8126` | Gate 3 (`ATT-9635`) revision on `TST-UI-2800`: added steady-value sparkline/`W_PRIME_BAL` liveness + NaN equality tests, 92-element causal window + multi-thread safety checks, end-to-end `deviceId` rename survival (`TrackingViewModel`/sparklines), full `itpmsLock`/`trackLock`/`connectionLock` + `ProxySensor` swap coverage, and `DisplayBrightnessMode.SYSTEM`/`CUSTOM`/`AUTO` code-grounding. |
| `1.0.37` | 2026-11-22 | Lane 3 Author (`ATT-8126`, `--as agent1`) | `ATT-8126` | Promoted `TST-UI-2800` to `[Verified]` (`128/128` PASS across 20 suites incl. `BANALServiceRepositoryHotpathTest` 6/6, `RrArtifactFilterPerformanceEquivalenceTest` 3/3, `ProxySensorAndFilterManagerIdKeyTest` 5/5, `CockpitRecompositionBoundsComposeTest` 2/2, `ArchitectureConformanceContractTest` 11/11, `UiReflectionOnlyTestAuditContractTest` 4/4; `0` new warnings; live `emulator-5554` PNGs & `0` crashes; SHA-256 bundle under `docs/engineering/test_evidence/tickets/ATT-8126/`). |
| `1.0.40` | 2026-11-22 | Lane 1 Author (`ATT-8129`, `--as agent1`) | `ATT-8129` | Added and verified `TST-UI-2801` (`[Verified]`, `.1`–`.3`) covering physical & classpath excision of 27 dead Fragment/SettingsDialogFragment/hybrid-bridge files + `StartOrResumeDialog` `DialogFragment` wrapper, `MainActivityWithNavigation` cleanup, pure Compose `EditDeviceDialog`/`StartOrResumeAlertDialog`/`GPSDisabledAlertDialog` behavioral Compose UI & Light/Dark + `emulator-5554` verification, and governance contract tests (`131/131` PASS across 22 suites, `source-reading-baseline` ratcheted `105 -> 104`, `0` crashes in `logcat -b crash -d`, SHA-256 evidence bundle in `docs/engineering/test_evidence/tickets/ATT-8129/`). |
| `1.0.41` | 2026-11-22 | Lane 1 Author (`ATT-8130`, `--as agent1`) | `ATT-8130` | Added and verified `TST-UI-2802` (`[Verified]`, `.1`–`.3`, `521/521` PASS across all targeted suites incl. `LegacyPackageDecommissioningTest`, `BTLERadarDeviceTest`, `HardwareHalIngestionIntegrationTest`, `DatabaseSchemaRegistryParityContractTest`, `DeadCodeEliminationContractTest`, `ManifestAndBuildConfigContractTest`, `SystemContextContractTest`, `SystemArchitectureDesignContractTest`, `ProGuardRulesContractTest`, `AdrCompletenessContractTest`, `ArchitectureConformanceContractTest`, `SourceTextReadingAuditContractTest`; 4 live `emulator-5554` PNGs verified via `read_file`; `0` crashes; SHA-256 evidence bundle in `docs/engineering/test_evidence/tickets/ATT-8130/`). |
