# Living Test Specification: UI Components & Design System (Part 3 of 3)

**Document ID**: `DOC-TST-UI-P3`  
**Version**: 1.0.12  
**Status**: Reviewed  
**Approver**: Pending PO Sign-Off  

**Domain**: `ui_design_system_part3`  
**Prefixes**: `UI`, `UNT`  
**Scope**: Jetpack Compose design system, UI components, typography, dialogs, gestures, and layout contracts.  
**Total Tests**: 40

---

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
