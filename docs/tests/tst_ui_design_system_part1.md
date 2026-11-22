# Living Test Specification: UI Components & Design System (Part 1 of 3)

**Document ID**: `DOC-TST-UI-P1`  
**Version**: 1.0.3  
**Status**: Reviewed  
**Approver**: Pending PO Sign-Off  

**Domain**: `ui_design_system_part1`  
**Prefixes**: `UI`, `UNT`  
**Scope**: Jetpack Compose design system, UI components, typography, dialogs, gestures, and layout contracts.  
**Total Tests**: 244

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
- **Verification Evidence**: Verified (`ATT-8330`, SHA `3e20473b`, Gate 4 `129/129` PASS across 30 suites): `EquipmentDiscoveryTest.kt` (`EquipmentDiscoveryTest.testResolveIdentity_UniqueDeviceResolvesLinkedEquipmentAndSport_TST_UNT_003`, `EquipmentDiscoveryTest.testResolveIdentity_NoDeviceUsesSpeedBasedFallbackAndUnlinkedEquipment_TST_UNT_003`)

### TST-UNT-004: MovingAverageFilter [Verified]
- **Requirement(s)**: `REQ-FIL-001`
- **Component**: `MovingAverageFilter`
- **Test Description**: Verify correct averaging of a sequence of numbers (Numbered and Timed variants).
- **Verification Evidence**: Verified (`ATT-8330`, SHA `3e20473b`, Gate 4 `129/129` PASS across 30 suites): `FilterNullSafetyTest.kt` (`FilterNullSafetyTest.numberedMovingAverageFilter_slidingWindowSequence_computesExactMean_TST_UNT_004`)

### TST-UNT-005: ExponentialSmoothing [Verified]
- **Requirement(s)**: `REQ-FIL-002`
- **Component**: `ExponentialSmoothing`
- **Test Description**: Verify the recursive smoothing formula: `y[k+1] = α m[k] + (1-α) y[k]`.
- **Verification Evidence**: Verified (`ATT-8330`, SHA `3e20473b`, Gate 4 `129/129` PASS across 30 suites): `FilterInitializationAndBoundsTest.kt` (`FilterInitializationAndBoundsTest.exponentialSmoothingFilter_firstSampleSeedingAndReseed_eliminatesZeroBias`)

### TST-UNT-006: SportTypeMapping [Verified]
- **Requirement(s)**: `REQ-EXP-006`
- **Component**: `SportTypeMapping`
- **Test Description**: Verify that `SportTypeDatabaseManager` correctly handles a `null` Strava mapping as a persistent state.
- **Verification Evidence**: Verified (`ATT-8330`, SHA `3e20473b`, Gate 4 `129/129` PASS across 30 suites): `SportTypeDatabaseManagerStravaTest.kt` (`SportTypeDatabaseManagerStravaTest.testUpdateSportType_persistsNullStravaMappingAsPersistentState_TST_UNT_006`)

### TST-UNT-007: EditWorkoutViewModel [Verified]
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
- **Verification Evidence**: Verified (`ATT-8330`, SHA `3e20473b`, Gate 4 `129/129` PASS across 30 suites): `WorkoutExtremaBigSixMapperTest.kt` (`WorkoutExtremaBigSixMapperTest.fromCursor_mapsBigSixExtremaMetricsAndHarmonizedAverageSpeed_TST_UNT_009_TST_UI_027`)

### TST-UNT-010: MappablePath [Verified]
- **Requirement(s)**: `REQ-MAP-007`
- **Component**: `MappablePath`
- **Test Description**: Verify that coordinate projection (latLngs) is lazy and only occurs once.
- **Verification Evidence**: Verified (`ATT-8330`, SHA `3e20473b`, Gate 4 `129/129` PASS across 30 suites): `MappablePathMemoizationTest.kt` (`MappablePathMemoizationTest.mappablePathImplementations_preserveReferentialLatLngsIdentityAcrossAccesses_TST_UNT_010`)

### TST-UNT-011: TrackingViewsDb [Verified]
- **Requirement(s)**: `REQ-PRO-005`
- **Component**: `TrackingViewsDb`
- **Test Description**: 1. Simulated fresh install (Wipe data): Verify schema contains ShowElevationProfile. 2. Simulated upgrade (v9->v10): Verify ShowElevationProfile is added.
- **Verification Evidence**: Verified (`ATT-8330`, SHA `3e20473b`, Gate 4 `129/129` PASS across 30 suites): `TrackingViewsRealSqliteMigrationTest.kt` (`TrackingViewsRealSqliteMigrationTest.testTrackingViewsDbHelper_v17ToV18Migration_sportDependentBarsAndLegacyGaugeParity`)

### TST-UNT-012: WorkoutSummariesViewModel [Verified]
- **Requirement(s)**: `REQ-PER-008`
- **Component**: `WorkoutSummariesViewModel`
- **Jira**: ``ATT-457``
- **Test Description**: Verify `getFilteredWorkouts` correctly filters by `sportId` (Sub-Sport) when provided.
- **Verification Evidence**: Verified (`ATT-8330`, SHA `3e20473b`, Gate 4 `129/129` PASS across 30 suites): `WorkoutSummariesViewModelFilterTest.kt` (`WorkoutSummariesViewModelFilterTest.testGetFilteredWorkouts_filtersBySubSportId_TST_UNT_012`)

### TST-UNT-013: WorkoutSummariesViewModel [Verified]
- **Requirement(s)**: `REQ-PER-008`
- **Component**: `WorkoutSummariesViewModel`
- **Jira**: ``ATT-458``
- **Test Description**: Verify `getFilteredWorkouts` correctly filters by `bSportType` (Base Sport) when `sportId` is null.
- **Verification Evidence**: Verified (`ATT-8330`, SHA `3e20473b`, Gate 4 `129/129` PASS across 30 suites): `WorkoutSummariesViewModelFilterTest.kt` (`WorkoutSummariesViewModelFilterTest.testGetFilteredWorkouts_filtersByBSportTypeWhenSportIdNull_TST_UNT_013`)

### TST-UNT-014: WorkoutSummariesDbHelper [Verified]
- **Requirement(s)**: `REQ-DAT-008`
- **Component**: `WorkoutSummariesDbHelper`
- **Jira**: ``ATT-476``
- **Test Description**: Verify that `onUpgrade` does not call `setTransactionSuccessful()` explicitly, which would conflict with `SQLiteOpenHelper`'s internal transaction management.
- **Verification Evidence**: Verified (`ATT-8330`, SHA `3e20473b`, Gate 4 `129/129` PASS across 30 suites): `DatabaseMigrationIndexTest.kt` (`DatabaseMigrationIndexTest.testWorkoutSummariesDb_freshOnCreate_createsAllThreeIndexes`, `DatabaseMigrationIndexTest.testWorkoutSummariesDb_onUpgradeV25ToV26_createsIndexesIdempotently`)

### TST-UNT-015: WorkoutClusterEngine [Verified]
- **Requirement(s)**: `REQ-SET-065`
- **Component**: `WorkoutClusterEngine`
- **Jira**: ``ATT-502``
- **Test Description**: Verify that 6-term altitude-aware cluster similarity calculation correctly applies displacement weights when altitude positions are present, differentiates routes with distinct peaks/valleys, and cleanly falls back to 4-term matching when altitude coordinates are null or disabled.

### TST-UI-075: **Settings Terminology Audit** [Verified]
- **Requirement(s)**: `REQ-UI-115`
- **Component**: **Settings Terminology Audit**
- **Jira**: ``ATT-272``
- **Test Description**: 1. Open Navigation Drawer -> Settings. 2. Verify items "Display" and "Search". 3. Switch to German. 4. Verify items "Display" and "Suche".
- **Verification Evidence**: Verified (`ATT-8330`, SHA `3e20473b`, Gate 4 `129/129` PASS across 30 suites): `UiDesignSystemTerminologyTest.kt` (`UiDesignSystemTerminologyTest.navigationDrawerSettingsTitles_useConciseEnAndDeTerminology_TST_UI_075`)

### TST-UI-001: **UI Design** [Implemented]
- **Requirement(s)**: `REQ-UI-011`
- **Component**: **UI Design**
- **Test Description**: Inspect the workout summary extrema table on various device themes.

### TST-UI-011: **Formatting** [Verified]
- **Requirement(s)**: `REQ-UI-011`
- **Component**: **Formatting**
- **Test Description**: Verify that units in data rows are surrounded by square brackets (e.g., `[km/h]`).
- **Verification Evidence**: Verified (`ATT-8330`, SHA `3e20473b`, Gate 4 `129/129` PASS across 30 suites): `WorkoutExtremaUiTest.kt` (`WorkoutExtremaUiTest.workoutExtrema_verifiesBracketsNotationTypographyHierarchyAlignmentAndAlphas_TST_UI_011_015_019_020_059`)

### TST-UI-012: **Hierarchy** [Verified]
- **Requirement(s)**: `REQ-UI-011`
- **Component**: **Hierarchy**
- **Test Description**: Verify that the Sensor Name and the Numeric Values share the same visual weight, while the unit is micro-scaled.
- **Verification Evidence**: Verified (`ATT-8330`, SHA `3e20473b`, Gate 4 `129/129` PASS across 30 suites): `WorkoutExtremaUiTest.kt` (`WorkoutExtremaUiTest.workoutExtrema_verifiesBracketsNotationTypographyHierarchyAlignmentAndAlphas_TST_UI_011_015_019_020_059`)

### TST-UI-013: **Alignment** [Verified]
- **Requirement(s)**: `REQ-UI-011`
- **Component**: **Alignment**
- **Test Description**: Verify that in all data rows, the icon, sensor label, and numeric values are vertically aligned to their bottom edge.
- **Verification Evidence**: Verified (`ATT-8330`, SHA `3e20473b`, Gate 4 `129/129` PASS across 30 suites): `WorkoutExtremaUiTest.kt` (`WorkoutExtremaUiTest.workoutExtrema_verifiesBracketsNotationTypographyHierarchyAlignmentAndAlphas_TST_UI_011_015_019_020_059`)

### TST-UI-014: **Typography** [Verified]
- **Requirement(s)**: `REQ-UI-011`
- **Component**: **Typography**
- **Test Description**: Verify that units use a micro font size (e.g., 8-9.sp) and a lighter color (reduced alpha) than the sensor name.
- **Verification Evidence**: Verified (`ATT-8330`, SHA `3e20473b`, Gate 4 `129/129` PASS across 30 suites): `WorkoutExtremaUiTest.kt` (`WorkoutExtremaUiTest.workoutExtrema_verifiesBracketsNotationTypographyHierarchyAlignmentAndAlphas_TST_UI_011_015_019_020_059`)

### TST-UI-015: **Notation** [Verified]
- **Requirement(s)**: `REQ-UI-011`
- **Component**: **Notation**
- **Test Description**: Verify that the "Average" header column displays the mathematical symbol (Ø) instead of the word "average".
- **Verification Evidence**: Verified (`ATT-8330`, SHA `3e20473b`, Gate 4 `129/129` PASS across 30 suites): `WorkoutExtremaUiTest.kt` (`WorkoutExtremaUiTest.workoutExtrema_verifiesBracketsNotationTypographyHierarchyAlignmentAndAlphas_TST_UI_011_015_019_020_059`)

### TST-UI-016: **Proportion** [Implemented]
- **Requirement(s)**: `REQ-UI-011`
- **Component**: **Proportion**
- **Test Description**: Verify that the icon height visually matches the height of the sensor name (bodyLarge line height).

### TST-UI-017: **Alignment** [Implemented]
- **Requirement(s)**: `REQ-UI-011`
- **Component**: **Alignment**
- **Test Description**: Verify that the icon, sensor label, and bracketed unit are perfectly aligned to the same bottom baseline in the Compose Preview.

### TST-UI-018: **Baseline Precision** [Implemented]
- **Requirement(s)**: `REQ-UI-011`
- **Component**: **Baseline Precision**
- **Test Description**: Verify that the bottom edge of the icon and the bottom of the text characters are visually perfectly aligned.

### TST-UI-019: **Hierarchy** [Verified]
- **Requirement(s)**: `REQ-UI-011`
- **Component**: **Hierarchy**
- **Test Description**: Verify context-aware bolding: HR/Power (Avg+Max), Speed/Pace/Cadence (Avg), Alt/Temp (Min+Max).
- **Verification Evidence**: Verified (`ATT-8330`, SHA `3e20473b`, Gate 4 `129/129` PASS across 30 suites): `WorkoutExtremaUiTest.kt` (`WorkoutExtremaUiTest.workoutExtrema_verifiesBracketsNotationTypographyHierarchyAlignmentAndAlphas_TST_UI_011_015_019_020_059`)

### TST-UI-020: **Contrast** [Verified]
- **Requirement(s)**: `REQ-UI-011`
- **Component**: **Contrast**
- **Test Description**: Verify that the "-" placeholder is visually lighter (alpha 0.3) than numeric values.
- **Verification Evidence**: Verified (`ATT-8330`, SHA `3e20473b`, Gate 4 `129/129` PASS across 30 suites): `WorkoutExtremaUiTest.kt` (`WorkoutExtremaUiTest.workoutExtrema_verifiesBracketsNotationTypographyHierarchyAlignmentAndAlphas_TST_UI_011_015_019_020_059`)

### TST-UI-021: **Redundancy** [Verified]
- **Requirement(s)**: `REQ-UI-012`
- **Component**: **Redundancy**
- **Test Description**: Verify that Min/Max altitude no longer appear in the Workout Details row, only Ascent and Descent.
- **Verification Evidence**: Verified (`ATT-8330`, SHA `3e20473b`, Gate 4 `129/129` PASS across 30 suites): `WorkoutDetailsAndMetricUiTest.kt` (`WorkoutDetailsAndMetricUiTest.workoutDetailsAndSegmentDetails_verifyElevationAscentOmissionHierarchyAndMetricEmphasis_TST_UI_021_030_031_059_060`)

### TST-UI-026: **Consistency** [Implemented]
- **Requirement(s)**: `REQ-UI-013`
- **Component**: **Consistency**
- **Test Description**: In the Workout Summary card, verify that the altitude labels at the start/end of the chart axes match the Min/Max values shown in the Extrema table.

### TST-UI-027: **Harmonization** [Verified]
- **Requirement(s)**: `REQ-UI-014`
- **Component**: **Harmonization**
- **Test Description**: Start tracking; verify the Speed Average in the extrema table exactly matches the value derived from Total Distance / Active Time (e.g., if distance is 100m and active time is 100s, avg speed must be 1.0 m/s).
- **Verification Evidence**: Verified (`ATT-8330`, SHA `3e20473b`, Gate 4 `129/129` PASS across 30 suites): `WorkoutExtremaBigSixMapperTest.kt` (`WorkoutExtremaBigSixMapperTest.fromCursor_mapsBigSixExtremaMetricsAndHarmonizedAverageSpeed_TST_UNT_009_TST_UI_027`)

### TST-UI-028: **Localization** [Verified]
- **Requirement(s)**: `REQ-UI-018`
- **Component**: **Localization**
- **Test Description**: Inspect Workout Summary in various languages. Verify Ascent/Descent uses relative terminology (e.g., "Höhenmeter", "Elevation") and Min/Max uses absolute terminology (e.g., "Höhe", "Altitude").
- **Verification Evidence**: Verified (`ATT-8330`, SHA `3e20473b`, Gate 4 `129/129` PASS across 30 suites): `UiDesignSystemTerminologyTest.kt` (`UiDesignSystemTerminologyTest.elevationTerminology_distinguishesRelativeAscentFromAbsoluteAltitude_TST_UI_028`)

### TST-UI-029: **Session Isolation** [Verified]
- **Requirement(s)**: `REQ-UI-009`, `REQ-UI-110`
- **Component**: **Session Isolation**
- **Test Description**: 1. Start a workout, move, stop workout. 2. Immediately start a new workout.
- **Verification Evidence**: Verified (`ATT-8330`, SHA `3e20473b`, Gate 4 `129/129` PASS across 30 suites): `LiveWorkoutSessionTest.kt` (`LiveWorkoutSessionTest.testNewSession_startsCleanWithZeroLeakedTrackOrExtremaFromPriorSession_TST_UI_029`)

### TST-UI-030: **Metric Consistency** [Verified]
- **Requirement(s)**: `REQ-UI-020`
- **Component**: **Metric Consistency**
- **Test Description**: Audit Workout and Segment items. Verify \"Gain\" uses `ic_ascent` in both. Verify \"Elevation\" label in Workout is bottom-aligned with values. Verify all icons/text share a consistent baseline.
- **Verification Evidence**: Verified (`ATT-8330`, SHA `3e20473b`, Gate 4 `129/129` PASS across 30 suites): `WorkoutDetailsAndMetricUiTest.kt` (`WorkoutDetailsAndMetricUiTest.workoutDetailsAndSegmentDetails_verifyElevationAscentOmissionHierarchyAndMetricEmphasis_TST_UI_021_030_031_059_060`)

### TST-UI-031: **Layout Hierarchy** [Verified]
- **Requirement(s)**: `REQ-UI-021`
- **Component**: **Layout Hierarchy**
- **Test Description**: Open Workout Summary. Inspect the Elevation section header (left column).
- **Verification Evidence**: Verified (`ATT-8330`, SHA `3e20473b`, Gate 4 `129/129` PASS across 30 suites): `WorkoutDetailsAndMetricUiTest.kt` (`WorkoutDetailsAndMetricUiTest.workoutDetailsAndSegmentDetails_verifyElevationAscentOmissionHierarchyAndMetricEmphasis_TST_UI_021_030_031_059_060`)

### TST-UI-034: **Identity Branding UI** [Verified]
- **Requirement(s)**: `REQ-UI-024`
- **Component**: **Identity Branding UI**
- **Test Description**: 1. Open Route List. 2. Inspect Strava route: verify logo is below name and no \"Source: Strava\" text exists. 3. Inspect Local route: verify \"Source: Local\" text is below name. 4. Verify no branding logo in metrics row.
- **Verification Evidence**: Verified (`ATT-8330`, SHA `3e20473b`, Gate 4 `129/129` PASS across 30 suites): `RouteAndSegmentCardsUiTest.kt` (`RouteAndSegmentCardsUiTest.routeSummaryHeader_rendersStravaVsLocalIdentityBrandingAndCompactMetricsRow_TST_UI_034_TST_UI_035`)

### TST-UI-035: **Compact Metric Row** [Verified]
- **Requirement(s)**: `REQ-UI-025`
- **Component**: **Compact Metric Row**
- **Test Description**: 1. Open Route List. 2. Inspect gap between identity row and metrics row.
- **Verification Evidence**: Verified (`ATT-8330`, SHA `3e20473b`, Gate 4 `129/129` PASS across 30 suites): `RouteAndSegmentCardsUiTest.kt` (`RouteAndSegmentCardsUiTest.routeSummaryHeader_rendersStravaVsLocalIdentityBrandingAndCompactMetricsRow_TST_UI_034_TST_UI_035`)

### TST-UI-036: **Branding Audit** [Verified]
- **Requirement(s)**: `REQ-UI-026`
- **Component**: **Branding Audit**
- **Test Description**: 1. Open Segment List. 2. Verify top logo height (e.g., 24dp). 3. Inspect individual Segment Items (if enabled).
- **Verification Evidence**: Verified (`ATT-8330`, SHA `3e20473b`, Gate 4 `129/129` PASS across 30 suites): `RouteAndSegmentCardsUiTest.kt` (`RouteAndSegmentCardsUiTest.segmentList_rendersEmptyOrPopulatedItemsWithoutPerCardBrandingNoise_TST_UI_036`)

### TST-UI-038: **Detailed Metric Scale** [Verified]
- **Requirement(s)**: `REQ-UI-028`
- **Component**: **Detailed Metric Scale**
- **Test Description**: 1. Open Periods screen. 2. Inspect sub-sport rows and \"Longest Workout\" highlight.
- **Verification Evidence**: Verified (`ATT-8330`, SHA `3e20473b`, Gate 4 `129/129` PASS across 30 suites): `PeriodSummaryCardUiTest.kt` (`PeriodSummaryCardUiTest.periodSummaryCard_rendersSubSportsDateRangeAndTriggersLongestWorkoutNavigation_TST_UI_038_053_074`)

### TST-UI-039: **Pause Movement Isolation** [Verified]
- **Requirement(s)**: `REQ-PRO-004`
- **Component**: **Pause Movement Isolation**
- **Test Description**: 1. Start workout. 2. Move to create track. 3. **Pause**. 4. Move 50m.
- **Verification Evidence**: Verified (`ATT-8330`, SHA `3e20473b`, Gate 4 `129/129` PASS across 30 suites): `BANALServiceRepositoryPauseIsolationTest.kt` (`BANALServiceRepositoryPauseIsolationTest.trackAccumulation_ignoresLocationUpdatesWhilePaused_TST_UI_039`)

### TST-UI-041: **Period Peak Markers Audit** [Verified]
- **Requirement(s)**: `REQ-UI-033`, `REQ-UI-035`, `REQ-UI-036`
- **Component**: **Interactive Period Map Peak Markers**
- **Jira**: `SCRUM-130`
- **Test Description**:
  1. Navigate to Period Map for Week/Month summary in `PeriodMapScreen`.
  2. Verify technical markers for Start, End, Maximum Altitude, and Maximum Line Displacement are present and displayed with subordinate alpha matching the period type.
  3. Tap a peak marker: verify the bottom peek sheet opens displaying the corresponding workout details.
  4. Ensure successful compilation and zero visual regressions.
- **Verification Evidence**: Verified (`ATT-8330`, SHA `3e20473b`, Gate 4 `129/129` PASS across 30 suites): `InteractivePeriodMapTest.kt` (`InteractivePeriodMapTest.testTstPer027_4_SpatialTapHitTestingResolvesClosestWorkout`)

### TST-UI-043: **Device Management Modernization** [Verified]
- **Requirement(s)**: `REQ-UI-041`, `REQ-UI-043`
- **Component**: **Device Management Modernization**
- **Test Description**: 1. Navigate to Pairing screen. 2. Verify Tabbed layout. 3. Interact with Pair switches. 4. Click device to open Edit Dialog. 5. Modify a setting (e.g. name).
- **Verification Evidence**: Verified (`ATT-8330`, SHA `3e20473b`, Gate 4 `129/129` PASS across 30 suites): `DeviceManagementUiTest.kt` (`DeviceManagementUiTest.deviceItem_andEditDeviceDialog_verifyIdentityLayoutSwitchAndSave_TST_UI_043_044_050`, `DeviceManagementUiTest.devicesTabbedScreen_rendersTabBarWithActiveDeviceType_TST_UI_043`)

### TST-UI-044: **Mappable Device Items Layout Verification** [Verified]
- **Requirement(s)**: `REQ-UI-042`
- **Component**: **High-Density Device Item Layout**
- **Jira**: `SCRUM-37`
- **Test Description**:
  1. Inspect `DeviceItem` layout in the device pairing screen.
  2. Verify removal of horizontal divider and unified multi-row structure matching Route/Segment cards.
  3. Verify green/red availability LED icon is positioned to the left of the device metadata.
  4. Confirm pairing switch is positioned at the bottom-right corner with 0.8f scale.
- **Verification Evidence**: Verified (`ATT-8330`, SHA `3e20473b`, Gate 4 `129/129` PASS across 30 suites): `DeviceManagementUiTest.kt` (`DeviceManagementUiTest.deviceItem_andEditDeviceDialog_verifyIdentityLayoutSwitchAndSave_TST_UI_043_044_050`)

### TST-UI-045: **Reactive Map Updates** [Implemented]
- **Requirement(s)**: `REQ-MAP-009`
- **Component**: **Reactive Map Updates**
- **Test Description**: Open Route Detail Map. Toggle the \"Active\" switch in the header.

### TST-UI-046: **Stats Summary Block Visual Alignment** [Verified]
- **Requirement(s)**: `REQ-UI-046`
- **Component**: **Stats Summary Block Visual Alignment**
- **Test Description**: Inspect the `StatsSummaryBlock` in the Equipment or SportType details screen.
- **Verification Evidence**: Verified (`ATT-8330`, SHA `3e20473b`, Gate 4 `129/129` PASS across 30 suites): `StatsAndSensorStatusUiTest.kt` (`StatsAndSensorStatusUiTest.statsSummaryBlock_alignsMetricsHorizontallyAndInvokesClick_TST_UI_046`)

### TST-UI-047: **Live Segment Sheet Swipe Limit** [Implemented]
- **Requirement(s)**: `REQ-UI-047`
- **Component**: **Live Segment Sheet Swipe Limit**
- **Test Description**: 1. Start tracking. 2. When a segment is active, swipe up on the bottom sheet.

### TST-UI-048: **Sensor Status Header Visual Audit** [Verified]
- **Requirement(s)**: `REQ-UI-048`
- **Component**: **Sensor Status Header Visual Audit**
- **Test Description**: Inspect the `SensorStatus` row at the top of the tracking screen.
- **Verification Evidence**: Verified (`ATT-8330`, SHA `3e20473b`, Gate 4 `129/129` PASS across 30 suites): `StatsAndSensorStatusUiTest.kt` (`StatsAndSensorStatusUiTest.sensorStatus_rendersIconsOnly_andOpensSensorSourceDialogWithIdentityOnTap_TST_UI_048_049_050`)

### TST-UI-049: **Sensor Source Dialog Verification** [Verified]
- **Requirement(s)**: `REQ-UI-049`
- **Component**: **Sensor Source Dialog Verification**
- **Test Description**: 1. Start a session. 2. Tap any sensor icon (active or inactive) in the header.
- **Verification Evidence**: Verified (`ATT-8330`, SHA `3e20473b`, Gate 4 `129/129` PASS across 30 suites): `StatsAndSensorStatusUiTest.kt` (`StatsAndSensorStatusUiTest.sensorStatus_rendersIconsOnly_andOpensSensorSourceDialogWithIdentityOnTap_TST_UI_048_049_050`)

### TST-UI-051: **Grade Legend Visibility** [Verified]
- **Requirement(s)**: `REQ-MAP-010`
- **Component**: **Grade Legend Visibility**
- **Test Description**: 1. Open any Elevation Profile (e.g., in Aftermath). 2. Tap the info icon.
- **Verification Evidence**: Verified (`ATT-8330`, SHA `3e20473b`, Gate 4 `129/129` PASS across 30 suites): `ElevationProfileOverlayTest.kt` (`ElevationProfileOverlayTest.testElevationProfile_tappingInfoButton_togglesGradeLegendVisibility_TST_UI_051`)

### TST-UI-050: **Sensor Identity Logic** [Verified]
- **Requirement(s)**: `REQ-UI-050`
- **Component**: **Sensor Identity Logic**
- **Test Description**: Open `SensorSourceDialog`, `EditDeviceDialog`, and `DeviceItem`.
- **Verification Evidence**: Verified (`ATT-8330`, SHA `3e20473b`, Gate 4 `129/129` PASS across 30 suites): `DeviceManagementUiTest.kt` (`DeviceManagementUiTest.deviceItem_andEditDeviceDialog_verifyIdentityLayoutSwitchAndSave_TST_UI_043_044_050`); `StatsAndSensorStatusUiTest.kt` (`StatsAndSensorStatusUiTest.sensorStatus_rendersIconsOnly_andOpensSensorSourceDialogWithIdentityOnTap_TST_UI_048_049_050`)

### TST-UI-071: **Branding** [Implemented]
- **Requirement(s)**: `REQ-UI-062`
- **Component**: **Branding**
- **Jira**: ``ATT-237``
- **Test Description**: 1. Open the app to the main tracking screen. 2. Inspect the header area.

### TST-UI-074: **Weekly Period Date Range** [Verified]
- **Requirement(s)**: `REQ-UI-116`, `REQ-UI-521`
- **Component**: **Weekly Period Date Range**
- **Jira**: ``ATT-386``
- **Test Description**: 1. Open the 'Periods' screen. 2. Navigate to the 'Weeks' tab. 3. Inspect the period items.
- **Verification Evidence**: Verified (`ATT-8330`, SHA `3e20473b`, Gate 4 `129/129` PASS across 30 suites): `PeriodSummaryCardUiTest.kt` (`PeriodSummaryCardUiTest.periodSummaryCard_rendersSubSportsDateRangeAndTriggersLongestWorkoutNavigation_TST_UI_038_053_074`)

### TST-UI-072: **Progress Notification Neutrality** [Implemented]
- **Requirement(s)**: `REQ-UI-101`
- **Component**: **Progress Notification Neutrality**
- **Jira**: ``ATT-369``
- **Test Description**: 1. Trigger a Periods sync or Cluster initial load. 2. Inspect the background color of the progress card.

### TST-UI-073: **Bar Graph Synchronization** [Implemented]
- **Requirement(s)**: `REQ-UI-104`
- **Component**: **Bar Graph Synchronization**
- **Jira**: ``ATT-376``
- **Test Description**: 1. Clear application data to trigger a Periods migration. 2. Navigate to the 'Periods' screen. 3. Observe the bar graph as the newest months are aggregated first. 4. Scroll the period list.

### TST-UI-077: **Consistent Spacing Audit** [Verified]
- **Requirement(s)**: `REQ-UI-121`
- **Component**: **Consistent Spacing Audit**
- **Jira**: ``ATT-474``
- **Test Description**: 1. Open Workout List, Route List, and Cluster List. 2. Verify all cards have 12dp horizontal internal padding. 3. Verify vertical spacing between sections (Header, Details, etc.) is uniform (12dp). 4. Verify list items are separated by 8dp.
- **Verification Evidence**: Verified (`ATT-8330`, SHA `3e20473b`, Gate 4 `129/129` PASS across 30 suites): `UnifiedCardSpacingUiTest.kt` (`UnifiedCardSpacingUiTest.workoutRouteAndClusterCards_enforceUniformTwelveDpHorizontalPadding_TST_UI_077`)

### TST-UI-052: **SensorSourceDialog Header Color Decoupling & Semantic Green Token Verification Suite** [Verified]
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

### TST-UI-053: **Longest Workout Navigation** [Verified]
- **Requirement(s)**: `REQ-UI-052`
- **Component**: **Longest Workout Navigation**
- **Test Description**: 1. Open a Period Summary with multiple workouts. 2. Tap the "Longest Workout" section.
- **Verification Evidence**: Verified (`ATT-8330`, SHA `3e20473b`, Gate 4 `129/129` PASS across 30 suites): `PeriodSummaryCardUiTest.kt` (`PeriodSummaryCardUiTest.periodSummaryCard_rendersSubSportsDateRangeAndTriggersLongestWorkoutNavigation_TST_UI_038_053_074`)

### TST-UI-054: **Location Audit Metric** [Implemented]
- **Requirement(s)**: `REQ-UI-053`
- **Component**: **Location Audit Metric**
- **Test Description**: 1. Start a tracking session with a location provider active. 2. Tap the location icon in the `SensorStatus` header.

### TST-UI-055: **Multi-Track Visibility Audit** [Verified]
- **Requirement(s)**: `REQ-MAP-011`, `REQ-MAP-012`
- **Component**: **Multi-Track Visibility Audit**
- **Test Description**: 1. Open the Workout Details screen (including Period Map / Cluster Heatmap peek sheets) for a recorded activity. 2. Tap the "Layers" FAB. 3. Toggle different track types (GPS, Google Fused, ATT Fused; confirming Network is absent).

### TST-UI-056: **Map Preference Persistence** [Verified]
- **Requirement(s)**: `REQ-MAP-013`
- **Component**: **Map Preference Persistence**
- **Test Description**: 1. Toggle technical tracks in Workout Details. 2. Close and reopen the workout.
- **Verification Evidence**: Verified (`ATT-8330`, SHA `3e20473b`, Gate 4 `129/129` PASS across 30 suites): `TrackOnMapAftermathViewModelTest.kt` (`TrackOnMapAftermathViewModelTest.toggleTrackTypeEnabled_persistsSelectionAcrossViewModelRecreation_TST_UI_056`)

### TST-UI-057: **Period FAB Order** [Implemented]
- **Requirement(s)**: `REQ-MAP-014`
- **Component**: **Period FAB Order**
- **Test Description**: 1. Open the Period Map. 2. Verify the vertical order of FABs.

### TST-UI-058: **High-Fidelity Period Tracks** [Implemented]
- **Requirement(s)**: `REQ-MAP-015`
- **Component**: **High-Fidelity Period Tracks**
- **Test Description**: 1. Compare a track in a Period Map overview with the same track in Workout Details.

### TST-UI-059: **Alpha Consistency Audit** [Verified]
- **Requirement(s)**: `REQ-UI-054`
- **Component**: **Alpha Consistency Audit**
- **Test Description**: Static analysis of `WorkoutExtrema.kt` and `MetricItem.kt`.
- **Verification Evidence**: Verified (`ATT-8330`, SHA `3e20473b`, Gate 4 `129/129` PASS across 30 suites): `WorkoutExtremaUiTest.kt` (`WorkoutExtremaUiTest.workoutExtrema_verifiesBracketsNotationTypographyHierarchyAlignmentAndAlphas_TST_UI_011_015_019_020_059`); `WorkoutDetailsAndMetricUiTest.kt` (`WorkoutDetailsAndMetricUiTest.workoutDetailsAndSegmentDetails_verifyElevationAscentOmissionHierarchyAndMetricEmphasis_TST_UI_021_030_031_059_060`)

### TST-UI-060: **Primary Metric Hierarchy** [Verified]
- **Requirement(s)**: `REQ-UI-055`
- **Component**: **Primary Metric Hierarchy**
- **Test Description**: Open Workout Details or Segment Details.
- **Verification Evidence**: Verified (`ATT-8330`, SHA `3e20473b`, Gate 4 `129/129` PASS across 30 suites): `WorkoutDetailsAndMetricUiTest.kt` (`WorkoutDetailsAndMetricUiTest.workoutDetailsAndSegmentDetails_verifyElevationAscentOmissionHierarchyAndMetricEmphasis_TST_UI_021_030_031_059_060`)

### TST-UI-061: **Theme Audit** [Verified]
- **Requirement(s)**: `REQ-UI-056`
- **Component**: **Theme Audit**
- **Test Description**: Project-wide grep for `Color(0xFF...)` and `copy(alpha = ...)`.
- **Verification Evidence**: Verified (`ATT-8330`, SHA `3e20473b`, Gate 4 `129/129` PASS across 30 suites): `ThemeColorCentralizationAuditTest.kt` (`ThemeColorCentralizationAuditTest.verifyZeroHexColorsOutsideColorKt`)

### TST-UI-062: **Protocol-Aware Menu** [Implemented]
- **Requirement(s)**: `REQ-UI-044`
- **Component**: **Protocol-Aware Menu**
- **Test Description**: 1. Open the Bluetooth sensor list. 2. Check the options menu. 3. Open the ANT+ sensor list. 4. Check the options menu.

### TST-UI-063: **Global Icon Clipping** [Implemented]
- **Requirement(s)**: `REQ-UI-057`
- **Component**: **Global Icon Clipping**
- **Test Description**: 1. Open the app in Dark Mode. 2. Inspect sensor and protocol icons.

### TST-UI-066: **Elevation Profile Visibility** [Verified]
- **Requirement(s)**: `REQ-UI-058`
- **Component**: **Elevation Profile Visibility**
- **Test Description**: 1. Open the Recurring Path Heatmap. 2. Inspect the bottom of the screen.
- **Verification Evidence**: Verified (`ATT-8330`, SHA `3e20473b`, Gate 4 `129/129` PASS across 30 suites): `MapDetailLayoutElevationVisibilityUiTest.kt` (`MapDetailLayoutElevationVisibilityUiTest.mapDetailLayout_hidesElevationProfileWhenDisabledAndShowsWhenEnabled_TST_UI_066`)

### TST-UI-067: **Robust Sharing Snapshot** [Implemented]
- **Requirement(s)**: `REQ-UI-059`
- **Component**: **Robust Sharing Snapshot**
- **Test Description**: 1. Open a Workout Cluster Heatmap (where elevation is hidden). 2. Tap the Share button. 3. Verify that a loading indicator appears on the button. 4. Select a sharing target in the system dialog.

### TST-UI-069: **Safe Map Snapshot** [Implemented]
- **Requirement(s)**: `REQ-UI-060`
- **Component**: **Safe Map Snapshot**
- **Test Description**: 1. Trigger a snapshot multiple times rapidly. 2. Attempt a snapshot on a device with low memory.

### TST-UI-070: **Universal Delete-Only Long-Press Context Menu Enforcement** [Verified]
- **Requirement(s)**: `REQ-UI-061`, `REQ-UI-165`, `REQ-UI-166`, `REQ-UI-180`
- **Component**: **Universal Delete-Only Long-Press Context Menu Enforcement**
- **Jira**: ``ATT-238` / `ATT-1523``
- **Test Description**:
  1. Long-press across all deletable list items (Workouts, Routes, Clusters, Equipment, Sport Types, Favorite Locations).
  2. Run automated structural AST and layout audit test (`GlobalDeleteContextMenuAuditTest`) across all list card composables.
  3. Verify that `KnownLocationCard` does not contain "Edit" or "Show on Map" in its context menu, does not render an overflow MoreVert icon in the header, and anchors the context menu strictly to Top-Left.
  4. Run 9-language localization audit for deletion confirmation strings (`@string/delete`, `@string/really_delete_format`).
  5. Execute full unit test suite.

### TST-UI-078: **Cluster Naming Dialog Localization Audit** [Verified]
- **Requirement(s)**: `REQ-UI-123`, `REQ-UI-522`
- **Component**: **Cluster Naming Dialog Localization Audit**
- **Jira**: `ATT-565`, `ATT-5664`, `ATT-5662`
- **Test Description**:
  1. Inspect `ClusterNamingDialog` strings across all 9 locales: verify `cluster_naming__assignment_label`, `cluster_naming__new_name_prompt`, and `cluster_naming__new_route_name_label` explicitly reference existing and new favorite routes (*Lieblingsstrecken*), and verify `cluster_naming__found_recurring` provides neutral unassigned route copy with `%1$s` placeholder.
  2. Run `TranslationParityTest` and `ClusterNamingDialogLabelsVisualContractTest` to confirm 100% string presence, copy correctness, and parity across `values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, and `values-pt/`.
  3. Verify `verify_requirement_governance.py` passes cleanly.

### TST-UI-076: **Period UI Layout Integrity** [Implemented]
- **Requirement(s)**: `REQ-UI-104`
- **Component**: **Period UI Layout Integrity**
- **Jira**: ``ATT-444``
- **Test Description**: 1. Open the 'Periods' screen. 2. Inspect the visibility of the Bar Graph. 3. Scroll the period list downwards.

### TST-UI-079: **Elevation Profile Chart Bounds Sanitization & Strict Containment Verification** [Verified]
- **Requirement(s)**: `REQ-UI-126`
- **Component**: **Elevation Profile Chart Bounds Sanitization & Strict Containment Verification**
- **Jira**: ``ATT-508``
- **Test Description**:
  1. Test `ElevationProfile` with flat coastal elevation points (~0 m to 3 m) and corrupted override bounds (`minAltitudeOverride = -63.0`, `maxAltitudeOverride = 60.0`); verify bounds clamp to stream envelope with 20m aesthetic span.
  2. Test `ElevationProfile` with workout points dipping to 360 m and override `minAltitudeOverride = 368.0` (or 364.0); verify `bounds.min` is clamped to <= 360 m, guaranteeing that NO point on the profile curve renders below the chart bottom bound.
  3. Test with valid mountainous route; verify that full dynamic elevation range is preserved.
  4. Run unit tests in `ElevationProfileBoundsTest.kt`.

### TST-UI-080: **Delete Old Workouts Compose Dialog & Action Trigger Verification** [Verified]
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
- **Verification Evidence**: Verified (`ATT-8330`, SHA `3e20473b`, Gate 4 `129/129` PASS across 30 suites): `DeleteOldWorkoutsUiTest.kt` (`DeleteOldWorkoutsUiTest.workoutListActions_opensDeleteOldWorkoutsDialogWithDefault365AndConfirmsInput_TST_UI_080`)

### TST-UI-081: **Bulk Workout Deletion Progress Feedback & Notification Verification** [Verified]
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

### TST-UI-082: **Bulk Workout Deletion Two-Step Confirmation Verification** [Verified]
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

### TST-UI-083: **Sport-Specific Equipment Selection & In-Place Expansion Verification** [Verified]
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

### TST-UI-084: **Compact Strava Segment Efforts Presentation & Expandable Accordion Verification** [Verified]
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

### TST-UI-085: **Multi-Dimensional Workout Filtering, Modal Sheet & Persistence Verification** [Verified]
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

### TST-UI-086: **Multi-Dimensional Route Filtering, Shared Modal Sheet & Persistence Verification** [Verified]
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

### TST-UI-087: **Multi-Dimensional Segment Filtering, Shared Modal Sheet & Persistence Verification** [Verified]
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

### TST-UI-088: **Multi-Dimensional Favorite Tracks Filtering, Shared Modal Sheet & Persistence Verification** [Verified]
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

### TST-UI-089: **Favorite Tracks (Lieblingsstrecken) Sorting Verification** [Verified]
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

### TST-UI-090: **Cluster Algorithm Info Presentation & Action Trigger Verification** [Verified]
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

### TST-UI-091: **Detailed Workout Inspection from Favorite Tracks Verification** [Verified]
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

### TST-UI-092: **FastScrollbar Edge Alignment, Continuous Drag Accumulation & Action Non-Interference Verification** [Verified]
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

### TST-UI-093: **Universal FastScrollbar Integration & Action Non-Interference Verification Across Collection Views** [Verified]
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

### TST-UI-094: **Workout Summary Lap Overview, Rabbit/Hedgehog Highlights & Database Migration Verification** [Verified]
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

### TST-UI-095: **Interactive Lap Editing, Quick-Tag Presets, Sequential Navigation, and Full Workout Map Framing Verification** [Verified]
- **Requirement(s)**: `REQ-UI-142`
- **Component**: **Interactive Lap Editing, Quick-Tag Presets, Sequential Navigation, and Full Workout Map Framing Verification**
- **Jira**: `ATT-511`, `ATT-897`, `ATT-925`, `ATT-947`, `ATT-5483`
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
  4. *Workout Details Integration & Modal Overlay Contract Test (`WorkoutDetailTabbedLapsTest.kt` / `TrackOnMapScreenTest.kt`)*:
  • Verify tapping any lap row in `WorkoutDetailTabbedContent` on the "Runden & Segmente" tab invokes `onLapClick(lap)`.
  • Verify `TrackOnMapScreen` captures `activeEditingLap` and mounts `LapEditBottomSheet` as an `AppModalBottomSheet` overlay alongside the tabbed container (`Rule 47`).
  • Verify saving lap edits persists name and description to `WorkoutRepository.updateLapDetails`.
  • Verify tapping "Move Boundary on Map" dismisses `LapEditBottomSheet` and opens `MoveLapBoundaryOnMapSheet`.
  5. *Regression Verification*:
  • Execute full clean-room unit test suite (`./gradlew testDebugUnitTest`) to ensure zero regressions across all modules.

### TST-UI-096: **Workout Summary Laps Table Header, Pace Single-Line & Column Width Verification** [Verified]
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

### TST-UI-097: **Strava Best Efforts Collapsible Display, Metric Unit Filtering & Longest Distance Verification** [Verified]
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

### TST-UI-098: **UI Deletion of Old Unfinished Workouts & Active Workout Protection Verification** [Verified]
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

### TST-UI-099: **Mark Old Unfinished Workout Finished & Active Workout Protection Verification** [Verified]
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

### TST-UI-100: **Compact Workout Summary Context Menu Streamlining Verification** [Verified]
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

### TST-UI-101: **Edge-to-Edge Bottom Sheet Insets & Shared Core Components Verification** [Verified]
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

### TST-UI-102: **Modernized Modal Bottom Sheet Dialogs & Selection Flows Verification** [Verified]
- **Requirement(s)**: `REQ-UI-149`
- **Component**: **Modernized Modal Bottom Sheet Dialogs & Selection Flows Verification**
- **Jira**: `ATT-900`, `ATT-1029`, `ATT-1031`, `ATT-1032`, `ATT-5399`, `ATT-5661`
- **Test Description**:
  1. *Unit & Contract Verification (`ModalBottomSheetDialogsIntegrityTest.kt`)*:
  • Verify `AppModalBottomSheet` supports both vector icon (`icon: ImageVector?`) and painter icon (`iconPainter: Painter?`) parameters in its composable signature.
  • Verify `ExportSettingsDialog`, `UnitsSettingsDialog`, `DisplaySettingsDialog`, and `ActivityTypeSelectionDialog` render as bottom popups utilizing `AppModalBottomSheet`.
  • Verify `SensorSourceDialog` and `DeviceTypeSelectionDialog` render as bottom popups utilizing `AppModalBottomSheet`.
  • Verify `EditSensorFieldDialog` and `ConfigureFilterDialog` render as bottom popups utilizing `AppModalBottomSheet`.
  • Verify `EditSportTypeDialog`, `EditEquipmentDialog`, and `EditDeviceDialog` render as bottom popups utilizing `AppModalBottomSheet`.
  • Verify `WorkoutClusterSelectionDialog`, `EditWorkoutClusterDialog`, `ClusterInfoDialog`, and `ClusterNamingDialog` render as bottom popups utilizing `AppModalBottomSheet`.
  • Verify `ClusterNamingDialog` composes `AppModalBottomSheet` with Rule 73 stacked full-width action buttons, displays editable Workout Name, and pre-fills stripped cluster name (ATT-5560).
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

### TST-UI-103: **Standardized Bottom Sheet Action Layouts, Semantic Action Verbs & Route Dialog Verification** [Verified]
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

### TST-UI-104: **Workout Editing Modal Bottom Sheet & Viewport Layering Verification** [Verified]
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

### TST-UI-105: **Dropbox Settings Modal Bottom Sheet & Navigation Integration Verification** [Verified]
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

### TST-UI-106: **Strava Settings Modal Bottom Sheet & Navigation Integration Verification** [Verified]
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

### TST-UI-107: **Search Settings Modal Bottom Sheet & Navigation Integration Verification** [Verified]
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

### TST-UI-108: **Legacy Fragment Retirement, Unused Layout Purge & Resource Cleanup Verification** [Verified]
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
- **Verification Evidence**: Verified (`ATT-8330`, SHA `3e20473b`, Gate 4 `129/129` PASS across 30 suites): `DeadCodeEliminationContractTest.kt` (`DeadCodeEliminationContractTest.testLegacyFragmentsAndObsoleteLayoutsAreEliminated_TST_UI_108`)

### TST-UI-109: **Bottom Popup Single-Window BottomSheetDialogFragment, Decor & Zero-Flicker Dismiss Verification** [Verified]
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

### TST-UI-110: **Advanced Interval, Tab-Aware Sport & Sport-Aware Equipment Workout Filtering Verification** [Verified]
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

### TST-UI-111: **Retired Equipment Isolation in Pickers, Visual Identification & Equipment Lifecycle Management Test** [Verified]
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

### TST-UI-112: **Equipment ViewModel Automated Initialization & Reactive Sync Test** [Verified]
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

### TST-UI-113: **ModalNavigationDrawer Edge-Swipe Restriction & Map Panning Test** [Verified]
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

### TST-UI-114: **ModalNavigationDrawer Back Navigation Isolation & State Synchronization Test** [Verified]
- **Requirement(s)**: `REQ-UI-162`
- **Component**: **ModalNavigationDrawer Back Navigation Isolation & State Synchronization Test**
- **Jira**: ``ATT-1335`, `ATT-7526``
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
  8. *Unit Test Navigation Graph Readiness & Early Drawer Navigation Queuing (`MainActivityWithNavigationTest.kt` / `MainActivityWithNavigationInteropTest.kt`)*:
  • Verify that invoking `navigateToDrawerItem(itemId)` when `navController` is null or has no graph attached buffers `mPendingDrawerItemId = itemId` without throwing `IllegalStateException`.
  • Verify that when `onNavControllerReady(controller)` is called with an attached graph, queued pending navigation is executed and cleared.
  • Verify that `navigateToFilteredWorkouts` and `startWorkoutSummaryListFromPeriod` safely fall back to queued drawer navigation when the graph is uninitialized.

### TST-UI-115: **Cockpit Tab Navigation on Tracking Initiation & Origin-Aware Resume Neutrality Verification** [Verified]
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

### TST-UI-116: **Accessibility Event, NodeInfo & Manager Api34 Compatibility & LinkageError Resilience Verification** [Verified]
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

### TST-UI-216: **Reactive Bi-Directional Equipment-to-Sensor Synchronization and Cache Invalidation Test** [Verified]
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

### TST-UI-117: **Known Start Locations Management, Interactive Map Geofences & Altitude Lock Verification** [Verified]
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

### TST-UI-118: **Lieblingsorte UI/UX Harmonization, Standard Tabbed Layout, Sorting Options, Map Deletion & Legacy Name Healing Verification** [Verified]
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

### TST-UI-119: **Intuitive Smoothing Presets, Smart Defaults & Filter Dialog Verification** [Verified]
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

### TST-UI-120: **Workout Cockpit Theme Selection & Isolation Verification** [Verified]
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

### TST-UI-121: **AMOLED Pure Black Cockpit Theme Verification** [Verified]
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

### TST-UI-122: **Comprehensive Cockpit Dark Theme Verification** [Verified]
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

### TST-UI-123: **High-Contrast Typography & Cockpit Tile Grid Verification** [Verified]
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

### TST-UI-124: **Configurable Zone Visualization & Theme-Aware Preview Verification** [Verified]
- **Requirement(s)**: `REQ-UI-172`
- **Component**: **Configurable Zone Visualization & Theme-Aware Preview Verification**
- **Jira**: `ATT-1265`, `ATT-5200`, `ATT-7524`
- **Test Description**:
  1. *Data Model Unit Tests (`ZoneDisplayOptionsTest.kt`)*:
  • Verify default constructor sets `showBackground = true`, `showLeftBar = true`, `showRightBar = true`, `showTextColor = false`, `showGauge = false` for symmetrical Cockpit framing.
  • Verify copy mutations produce expected combinations.
  • Verify equality and hash code parity.
  2. *DataStore Preferences Persistence Unit Tests (`SettingsDataStoreZoneDisplayTest.kt`, `SettingsDataStoreZoneAtomicTest.kt`)*:
  • Verify reading uninitialized DataStore preferences returns default `ZoneDisplayOptions` (with `showRightBar = true`) for `HR_RUN`, `HR_BIKE`, `PWR_BIKE`, and `HRV (DFA a1)`.
  • Verify an explicit user setting of `showRightBar = false` is preserved upon reading DataStore.
  • Verify saving custom options for `HR_RUN` persists correctly and updates `getZoneDisplayOptionsFlow(ZoneType.HR_RUN)` without contaminating `HR_BIKE`, `PWR_BIKE`, or HRV.
  • Verify dedicated `hrvZoneDisplayOptionsFlow` and `saveHrvZoneDisplayOptions` persist independently and update `SettingsSnapshot.hrvDisplayOptions`.
  • Verify independent persistence across all zone profiles with distinct toggle configurations.
  • Verify `SettingsDataStoreJavaHelper.getZoneDisplayOptions` returns the persisted values synchronously with safe fallback and non-blocking caching off main thread.
  3. *Cockpit Sensor Tile Rendering Logic Unit Tests (`SensorFieldZoneRenderingTest.kt`)*:
  • Container color resolution: `showBackground = true` returns 12% alpha zone color; `showBackground = false` returns surface; `zoneColor == Transparent` returns surface.
  • Edge bar visibility: verify left/right bar visibility predicates respect `showLeftBar`, `showRightBar`, and `zoneColor != Transparent`.
  • Text color resolution: `showTextColor = true` returns `zoneColor`; `showTextColor = false` returns `onSurface`; `zoneColor == Transparent` returns `onSurface`.
  • Empty selection guard: all false results in standard neutral telemetry.
  • Theme-aware preview reactivity: verify container resolves to pure black/dark surface in dark theme and light surface in light theme when background tint is off.
  4. *9-Language Localization Audit (`ZoneDisplayLocalizationTest.kt`)*:
  • Verify all newly added string keys (`zone_display_title`, `zone_display_background`, `zone_display_left_bar`, `zone_display_right_bar`, `zone_display_text_color`, `zone_display_preview_title`) exist across all 9 application locales.
  • Verify `zone_display_background` translates accurately to "Tinted background" (EN), "Eingefärbter Hintergrund" (DE), and matching translations across ES, FR, IT, JA, NL, PL, PT.
  5. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` across all modules.

### TST-UI-125: **Sensor Status Inactive Indicator Legibility Verification** [Verified]
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

### TST-UI-126: **AMOLED Battery Saver Multi-Factor Dimming & Wakeup Verification** [Verified]
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

### TST-UI-127: **Tracking Tabs Configuration Transition and Hierarchical Navigation Verification** [Verified]
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

### TST-UI-128: **Cockpit Tab Header Synchronous Color Transition Verification** [Verified]
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

### TST-UI-129: **Theme-Aware Share Snapshot Color Harmonization Verification** [Verified]
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

### TST-UI-130: **Direct Workout-to-Cluster Navigation and Backstack Integrity Verification** [Verified]
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

### TST-UI-131: **Customizable Geofence Radius with Live Map Preview Verification** [Verified]
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

### TST-UI-132: **Favorite Locations on Central Map & Streamlined Management Verification** [Verified]
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

### TST-UI-133: **Ultra-Large ViewSize Typography & Configuration Verification** [Verified]
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

### TST-UI-134: **Unified Edit Action Symbols & Design Token Tinting Verification** [Verified]
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

### TST-UI-135: **Navigation Drawer Icon Theme-Aware Contrast Tinting Verification** [Verified]
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

### TST-UI-136: **Live Cockpit Location & Barometer Calibration Feedback Verification** [Verified]
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

### TST-UI-137: **Lieblingsorte: Display Start & Destination Locations in Workout Details and Map Verification** [Verified]
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

### TST-UI-138: **Lieblingsorte: Drill-Down Filter to View Workouts by Starting Location Verification** [Verified]
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

### TST-UI-139: **Lieblingsorte & Lieblingsstrecken Bridge Verification** [Verified]
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

### TST-UI-140: **Navigation Drawer Icon Defensive Resource Resolution & Crash Prevention Verification** [Verified]
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

### TST-UI-141: **Workout & Cluster Filter Dialogs: Lieblingsorte and Lieblingsstrecken Selection Verification** [Verified]
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

### TST-UI-142: **Lieblingsorte Compact Routes Badge & Drill-Down Verification** [Verified]
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

### TST-UI-143: **Bottom Sheet Boundaries, Curvature, Drop Shadow & Affordance Verification** [Verified]
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

### TST-UI-144: **Dark Theme Tonal Progression, Surface Elevation, Lap Button & Contrast Verification** [Verified]
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

### TST-UI-145: **List Item Heading Click Standardization & Dedicated Route Edit Button Verification** [Verified]
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

### TST-UI-146: **Elevation Profile Zoom, Pan, Reset & Synchronized Scrubbing Verification** [Horizontal pinch zoom expands visible distance window up to 10x; horizontal drag smoothly pans visible window within [0..totalDist]; double-tap and reset button restore 1.0x extent; scrubbing accurately reflects absolute distance; adaptive distance ticks format cleanly; 100% test pass rate.] [Verified]
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

### TST-UI-147: **Workout Filter Categorized Equipment Partitioning & Tab-Awareness Verification** [Verified]
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

### TST-UI-148: **Filter Dialogs Clean Chip Formatting (Emoji Removal) & Prioritized Section Ordering Verification** [Verified]
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

### TST-UI-149: **KnownLocationCard Dedicated Badges Row & Compact Sizing Verification** [Verified]
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

### TST-UI-150: **MinimumDragHandle Dimensions, Unified Popup Surface and Customization Unit Test** [Verified]
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

### TST-UI-151: **Elevation Profile Contextual Zoom Controls Visibility and Non-Overlapping Multi-Metric Badge Layout Verification** [Verified]
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

### TST-UI-152: **Cockpit Sensor Field Domain-Specific Default Filter Presets Verification** [Verified]
- **Requirement(s)**: `REQ-UI-198`
- **Component**: **Cockpit Sensor Field Domain-Specific Default Filter Presets Verification**
- **Jira**: ``ATT-1624`, `ATT-3310``
- **Test Description**:
  1. *Centralized Defaults Registry Unit Verification (`SensorFilterDefaultsTest.kt`)*:
  • Verify `getDefaultFilterConfig` for `SensorType.POWER` returns `MOVING_AVERAGE_TIME`, `3.0`, `"sec"`.\u003cbr\u003e• Verify `SensorType.SPEED_mps` returns `MOVING_AVERAGE_TIME`, `2.0`, `"sec"` (ATT-3310).\u003cbr\u003e• Verify `SensorType.CADENCE` returns `MOVING_AVERAGE_TIME`, `2.0`, `"sec"` (ATT-3310).\u003cbr\u003e• Verify `SensorType.PACE_spm` returns `MOVING_AVERAGE_TIME`, `5.0`, `"sec"`.\u003cbr\u003e• Verify `SensorType.VERTICAL_SPEED` returns `MOVING_AVERAGE_TIME`, `15.0`, `"sec"`.\u003cbr\u003e• Verify `SensorType.SLOPE` returns `MOVING_AVERAGE_TIME`, `5.0`, `"sec"`.\u003cbr\u003e• Verify all accumulator sensors (`DISTANCE_m`, `TIME_ACTIVE`, `CALORIES`, `LAP_NR`, `DISTANCE_m_LAP`, `TIME_LAP`, `TIME_TOTAL`, `PHONE_BATTERY`, `BATTERY_REMAINING_TIME`) return `INSTANTANEOUS`, `1.0`, `"sec"`.\u003cbr\u003e• Verify continuous physiological and environmental metrics (`HR`, `ALTITUDE`, `TEMPERATURE`) return `INSTANTANEOUS`, `1.0`.\u003cbr\u003e• Verify exhaustive coverage: loop across all entries of `SensorType.values()`, asserting non-null and valid configuration.\u003cbr\u003e• Verify `@JvmStatic` helper methods (`getDefaultFilterType`, `getDefaultFilterConstant`, `getDefaultUnit`).\u003cbr\u003e2. *ViewModel Dynamic Defaults Unit Verification (`EditSensorFieldViewModelTest.kt`)*:\u003cbr\u003e• Initialize `EditSensorFieldViewModel` and call `onSensorTypeChanged(SensorType.SPEED_mps)`: verify `selectedFilterType == FilterType.MOVING_AVERAGE_TIME` and `filterConstant == 2.0`.\u003cbr\u003e• Call `onSensorTypeChanged(SensorType.CADENCE)`: verify `selectedFilterType == FilterType.MOVING_AVERAGE_TIME` and `filterConstant == 2.0`.\u003cbr\u003e• Call `onSensorTypeChanged(SensorType.PACE_spm)`: verify `selectedFilterType == FilterType.MOVING_AVERAGE_TIME` and `filterConstant == 5.0`.\u003cbr\u003e• Call `onSensorTypeChanged(SensorType.VERTICAL_SPEED)`: verify `selectedFilterType == FilterType.MOVING_AVERAGE_TIME` and `filterConstant == 15.0`.\u003cbr\u003e• Call `onSensorTypeChanged(SensorType.SLOPE)`: verify `selectedFilterType == FilterType.MOVING_AVERAGE_TIME` and `filterConstant == 5.0`.\u003cbr\u003e• Call `onSensorTypeChanged(SensorType.POWER)`: verify `selectedFilterType == FilterType.MOVING_AVERAGE_TIME` and `filterConstant == 3.0`.\u003cbr\u003e• Call `onSensorTypeChanged(SensorType.DISTANCE_m)`: verify `selectedFilterType == FilterType.INSTANTANEOUS` and `filterConstant == 1.0`.\u003cbr\u003e• Verify `onFilterConfigDismissed()` resets to domain-specific defaults when sensor type was changed.\u003cbr\u003e3. *Clean-Room Full Suite Regression Execution*:\u003cbr\u003e• Execute `./gradlew testDebugUnitTest` across all modules to verify 100% test pass rate with 0 regressions.

### TST-UI-153: **Lieblingsorte Cockpit Location Calibration Badge & Altimeter Synchronization Verification** [Verified]
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

### TST-UI-154: **Cockpit Sensor Grid Pick & Place Reordering & Swapping Verification** [Verified]
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

### TST-UI-155: **Aftermath Synchronized Multi-Metric Scrubbing & X-Axis Domain Verification** [Verified]
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

### TST-UI-156: **Aftermath Heart Rate 5-Zone Distribution Vertical Column Chart Verification** [Verified]
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

### TST-UI-157: **Aftermath Cycling Power 5-Zone Distribution Vertical Column Chart Verification** [Verified]
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

### TST-UI-158: **Aftermath High-Aesthetic Lap & Interval Split Visualizer Verification** [Verified]
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

### TST-UI-159: **Aftermath Enhanced Shareable Workout Snapshot Verification** [Verified]
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

### TST-UI-160: **Aftermath Continuous Telemetry Metric Graphs & Synchronized Scrubbing Verification** [Verified]
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

### TST-UI-161: **KnownLocationCard Subtle Ghost Badges & Low-Contrast Styling Verification** [Verified]
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

### TST-UI-162: **Filter Dialogs 'Start at' Section Heading & 9-Language Localization Verification** [Verified]
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

### TST-UI-163: **WorkoutFilterBottomSheet Favorite Tracks Section Removal Verification** [Verified]
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

### TST-UI-164: **Aftermath Detailed Workout Card Configurable Sections Verification** [Verified]
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

### TST-UI-165: **Aftermath Edit Workout Dialog Configurable Fields Verification** [Verified]
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

### TST-UI-166: **Cockpit Typography (Font Family & Boldness) Configuration & Rendering Verification** [Verified]
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

### TST-UI-167: **Aftermath Resilient Map Preview Visibility & Scrollable Telemetry Layout Verification** [Verified]
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

### TST-UI-168: **Aftermath Telemetry Metric Graph Ordering & Visual Flow Verification** [Verified]
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

### TST-UI-169: **Aftermath Global Synchronized Horizontal Zoom & Telemetry Alignment Verification** [Verified]
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

### TST-UI-170: **Settings/Aftermath Relocation of Aftermath Customization Toggles to Advanced Settings Verification** [Verified]
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

### TST-UI-171: **Lieblingsorte Map Preview Thumbnail & Standardized Typography on KnownLocationCard Verification** [Verified]
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

### TST-UI-172: **Popups, Bottom Sheets & Dialogs Surface Background Standardization Verification** [Verified]
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

### TST-UI-173: **Aftermath Pace Telemetry Decoding, Stopped Speed Clamping & Y-Axis Label Formatting Verification** [Verified]
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

### TST-UI-174: **Aftermath Telemetry Graphs X-Axis Milestone Decimation, Collision Clearance & Boundary Labeling Verification** [Verified]
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

### TST-UI-175: **Bottom Sheet Peek Baselines, Design Token Centralization, Dynamic Self-Measurement & System Navigation Inset Verification** [Verified]
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
  2. *Dynamic Self-Measurement, Drag Affordance & Screen Forwarding Contract Tests (`BottomSheetVisualContractTest.kt`)*:
  • Verify `MapDetailLayout.kt` defines `onHeaderHeightMeasured: ((Dp) -> Unit)? = null` and binds `Modifier.onGloballyPositioned` around `MinimumDragHandle() + Surface(header)`.
  • Verify `SegmentOnMapScreen.kt` accepts and forwards `onHeaderHeightMeasured` to `MapDetailLayout`.
  • Verify `RouteOnMapScreen.kt` accepts and forwards `onHeaderHeightMeasured` to `MapDetailLayout`.
  • Verify `MapScreenWithTrack.kt` tracks `measuredSegmentHeaderHeight` and `measuredRouteHeaderHeight`, dynamically evaluating `sheetPeekHeight` with defensive fallback to `BottomSheetDesign` constants.
  • Verify that `SensorGridScreen.kt` references `BottomSheetDesign.PeekHeightLiveSegment`.
  • Verify that `WorkoutClusterHeatmapScreen.kt` and `PeriodMapScreen.kt` reference `BottomSheetDesign.PeekHeightWorkout`.
  • Verify that `WorkoutClusterHeatmapScreen.kt` binds `sheetDragHandle` with `MinimumDragHandle()` when `peekedWorkoutDataWithTrack != null && !isEditingFingerprint` and does not set `sheetDragHandle = null` unconditionally (ATT-5564).
  3. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` across all modules verifying 100% pass rate with 0 regressions.

### TST-UI-176: **Advanced Settings Modular Collapsible Subsection Accordion Architecture & Live Subtitle Verification** [Verified]
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
  5. *Advisory Notice Modernization Contract & Localization Tests (`ATT-5819`, `AdvancedTuningVisualContractTest.kt`, `TranslationParityTest.kt`)*:
  • Verify `TuningWarningNotice` in `TuningFormControls.kt` binds `R.string.advanced_tuning_warning_title` and `R.string.advanced_tuning_warning_desc`.
  • Verify `TuningWarningNotice` renders an advisory container (`surfaceVariant` or `secondaryContainer` styling with `Icons.Default.Tune` or `Icons.Default.Info`), eliminating alarming error-red styling (`errorContainer` and `Icons.Default.Warning`).
  • Verify `advanced_tuning_warning_desc` across all 9 locales (EN, DE, ES, FR, IT, JA, NL, PL, PT) conveys constructive customization guidance for layouts, models, and sensors with factory reset reassurance.

### TST-UI-177: **MapDetailLayout Interactive Draggable Splitter & Viewport Resizing Verification** [Verified]
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

### TST-UI-178: **KnownLocationCard Map Thumbnail Calibrated Zoom & Framing Verification** [Verified]
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

### TST-UI-179: **MapDetailLayout Persistent Sticky Global Zoom Toolbar & Decoupled ElevationProfile Verification** [Verified]
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

### TST-UI-180: **MapDetailLayout Directional Gesture Disambiguation & Vertical Scroll Verification** [Verified]
- **Requirement(s)**: `REQ-UI-226`, `REQ-UI-206`, `REQ-UI-213`
- **Component**: **MapDetailLayout Directional Gesture Disambiguation & Vertical Scroll Verification**
- **Jira**: ``ATT-1872``
- **Test Description**:
  1. *ChartGestureDisambiguator Pure Logic Unit Tests (`ChartGestureDisambiguatorTest.kt`)*:
  • Verify dominant vertical swipes ($

### TST-UI-181: **Cockpit HUD Curated Font Palette Expansion & Defensive Resolution Verification** [Verified]
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

### TST-UI-182: **Aftermath Lap Split Visualizer Refinements & Alignment Verification** [Verified]
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

### TST-UI-183: **Configurable Lap Section Display Mode Verification** [Verified]
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

### TST-UI-184: **Background Training Zone Bands & Right-Hand Zone Axis Verification** [Verified]
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

### TST-UI-185: **Fine-Grained Telemetry Frequency Histogram Verification** [Verified]
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

### TST-UI-186: **Aftermath/Graphs: TelemetryMetricGraph Pan Mode Gesture & Lockstep Synchronization Verification** [Verified]
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

### TST-UI-187: **Advanced Settings Initial Collapsed Accordion State Verification** [Verified]
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

### TST-UI-188: **Lap Display Mode Visualizer Default & Advanced Settings Dialog State Synchronization Verification** [Verified]
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

### TST-UI-189: **Zone Card Header Row Layout, Bottom Toggle Placement & Streamlined Readout Verification** [Verified]
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

### TST-UI-190: **Pan Mode Gesture Sensitivity & Cross-Domain Continuous Panning Verification** [Verified]
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

### TST-UI-191: **Lap Display Mode Binary Choice (Table vs. Visualizer) & Legacy Migration Verification** [Verified]
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

### TST-UI-192: **Independent X-Axis Domain Settings (Elevation vs. Telemetry) & Cross-Domain Scrubbing Verification** [Verified]
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

### TST-UI-193: **UI Design System & Mode Selection Heuristics Verification** [Verified]
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

### TST-UI-194: **Aftermath/Telemetry: Trackless Continuous Telemetry Graph Ingestion, Time-Domain Scrubbing, and Map Collapse Verification** [Verified]
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

### TST-UI-195: **WorkoutSummaries: Support Marking Workouts as "Race" (Wettkampf) Verification Across DB, Header, Edit UI, and Filters** [Verified]
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

### TST-UI-196: **Segment, Route, and Live Segment Popup Navigation Bar Inset & Upward Travel Calibration Verification** [Verified]
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

### TST-UI-197: **Lieblingsorte/Map: Favorite Location Bottom Sheet Non-Expandable Contract & Drag Handle Removal Verification** [Verified]
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

### TST-UI-198: **Unconditional Edit Workout Form Presentation & Removal of Edit Dialog Field Toggles Verification** [Verified]
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

### TST-UI-199: **Matrix Table Presentation for Configurable Aftermath Sections (Workout Summary List vs. Workout Details) Verification** [Verified]
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

### TST-UI-200: **Persistent Floating Scrubbing Telemetry Badge Pinned Above Scrollable Graphs in MapDetailLayout Verification** [Verified]
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

### TST-UI-201: **Aftermath/Scrubbing: Render Training Zone Badge Suffix in Active Zone Color in Scrubbing Telemetry Badge Verification** [Verified]
- **Requirement(s)**: `REQ-UI-242`
- **Component**: **Aftermath/Scrubbing: Render Training Zone Badge Suffix in Active Zone Color in Scrubbing Telemetry Badge Verification**
- **Jira**: ``ATT-2015``
- **Test Description**:
  1. *Structural & Zone Styling Contract Tests (`ScrubbingBadgeZoneColorContractTest.kt`)*:
  • Verify `ElevationProfile.kt` utilizes `buildAnnotatedString` for HR and Power telemetry badges.
  • Verify base metric value (e.g. `${point.hr} bpm`, `${point.power} W`) is styled with `MaterialTheme.colorScheme.onSurface`.
  • Verify active zone suffix (` • Z$hrZone`, ` • Z$powerZone`) is styled with `TelemetryZoneMath.ZONE_COLORS[zone - 1]` and `FontWeight.Bold` across HR zones 1..5 and all 7 Coggan power zones 1..7 (`TelemetryZoneMath.ZONE_COLORS.size`).
  • Verify fallback branch renders plain base metric value in `MaterialTheme.colorScheme.onSurface` without delimiter or zone suffix when zone is null.
  2. *Existing Invariant Tests*:
  • Verify `ElevationProfileLayoutTest.kt` passes 100%.
  • Verify `TelemetryMetricGraphZoneTest.kt` passes 100%.
  • Verify `MapDetailLayoutScrubbingBadgeContractTest.kt` passes 100%.
  3. *9-Language Localization Audit (`TranslationParityTest.kt`)*:
  • Verify 100% parity across all 9 localized `strings.xml` files.
  4. *Clean-Room Full Suite Regression Execution*:
  • Run `./gradlew testDebugUnitTest` verifying 100% pass rate.

### TST-UI-202: **Aftermath/Graphs: Configurable Minimum Pace Ceiling in Expert Settings with Athletic Default 3:00 min/km Verification** [Verified]
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

### TST-UI-203: **Lieblingsorte/UI: Harmonize Map Preview Thumbnail Dimensions to 100dp on KnownLocationCard Verification** [Verified]
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

### TST-UI-204: **Aftermath/Details: Upper Metadata Slot Architecture & Reactive Detail Section Rendering in MapDetailLayout Verification** [Verified]
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

### TST-UI-205: **Aftermath/Scrubbing: Align Scrubbing Telemetry Badge with Zoom Controls and Offset to Top-Right in MapDetailLayout Verification** [Verified]
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

### TST-UI-206: **Aftermath/Summary: Click-to-Open Navigation and Gesture Disambiguation for Telemetry Graphs and Zone Distribution Cards in WorkoutSummary Verification** [Verified]
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

### TST-UI-207: **Aftermath/Details: Strava Activity Data Upper Metadata Slotting & Structural Parity in Detailed Workout View Verification** [Verified]
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

### TST-UI-208: **Aftermath/Zoom: Reactive Zoom Controls Suppression in MapDetailLayout When No Zoomable Graphs Are Rendered Verification** [Verified]
- **Requirement(s)**: `REQ-UI-249`
- **Component**: **Aftermath/Zoom: Reactive Zoom Controls Suppression in MapDetailLayout When No Zoomable Graphs Are Rendered Verification**
- **Jira**: ``ATT-2138``
- **Test Description**:
  1. *Structural Contract Test (`MapDetailLayoutZoomContractTest.kt`)*:
  • Verify `MapDetailLayout.kt` specifies `hasZoomToolbar = showZoomControls && (showElevationProfile

### TST-UI-209: **Aftermath/Layout: Collapsing Upper Metadata Architecture & Map Squashing Prevention via NestedScroll Verification** [Verified]
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

### TST-UI-210: **Aftermath/UI: Integrated Minimalist Marginal Zone Bars & Histogram Right of Telemetry Graph in Portrait Mode Verification** [Verified]
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

### TST-UI-211: **Aftermath/Details: Lap Split Visualizer Upper Metadata Slotting & Structural Parity in Detailed Workout View Verification** [Verified]
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

### TST-UI-212: **Tracking Views Null-Safe Deserialization & DB Seeding Verification** [Verified]
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

### TST-UI-213: **Aftermath/Details: Non-Blocking Nested Scroll Dispatch, Sub-Pixel Precision & Optimized High-Density Metadata Verification** [Verified]
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

### TST-UI-214: **Aftermath: User-Customizable Section Reordering, Unified Order Persistence & Modular Section Architecture Verification** [Verified]
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

### TST-UI-215: **Equipment Management: Fleet-Wide Equipment-to-Sensor Mapping Matrix with Checkboxes Verification** [Verified]
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

### TST-UI-519: **Compact Equipment Card Layout & Harmonized Inter-Card Spacing Verification** [Verified]
- **Requirement(s)**: `REQ-UI-523`
- **Component**: **Compact Equipment Card Layout & Harmonized Inter-Card Spacing Verification**
- **Jira**: ``ATT-2059``
- **Test Description**:
  1. *Layout Metrics & Spacing Contract Test (`TST-UI-519.1`, `EquipmentCardSpacingContractTest.kt`)*:
  • Verify `EquipmentList` configures `verticalArrangement` with compact spacing (6.dp–8.dp).
  • Verify `MappableListItem` inside `EquipmentItem` has `vertical = 0.dp` margin to prevent double-gap compounding.
  • Verify minimum card touch target height satisfies WCAG accessibility (>= 48.dp).
  2. *Adaptive Compact Presentation Test (`TST-UI-519.2`, `EquipmentCompactCardTest.kt`)*:
  • Verify 0-workout items (`totalWorkouts == 0`) resolve compact typography (`titleMedium`) and vertical padding (`8.dp`).
  • Verify active items with workouts (`totalWorkouts > 0`) retain standard typography (`titleLarge`), 12.dp padding, and stats summary block.
  • Verify retired items (`isRetired == true`) apply `0.75f` alpha to `MappableListItem`.
  3. *Interactive Invariants Test (`TST-UI-519.3`)*:
  • Verify single-tap triggers `onConfigClick`.
  • Verify long-press triggers delete dropdown menu anchored at `TopStart` per `REQ-UI-061`.
  4. *Clean-Room Regression Execution (`TST-UI-519.4`)*:
  • Run `./gradlew testDebugUnitTest` verifying 100% pass rate.

### TST-UI-217: **Cockpit Sensor Field Visual Styling Architecture, Expert Settings Selection & Reactive Grid Layout Verification** [Verified]
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

### TST-UI-218: **Conditional Research Button Visibility Based on Real Paired Remote Devices Verification** [Verified]
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

### TST-UI-219: **Centralized Zone Threshold Resolution & O(log N) Scrubbing Point Search Verification** [Verified]
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

### TST-UI-220: **Canonical Horizontal Domain Evaluation & Total Span Engine (ProfileDomainMath) Verification** [Verified]
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

### TST-UI-221: **Modular Component Architecture for Advanced Tuning Dialog Verification** [Verified]
- **Requirement(s)**: `REQ-UI-262`
- **Component**: **Modular Component Architecture for Advanced Tuning Dialog Verification**
- **Jira**: `ATT-2033`, `ATT-7536`, `ATT-7539`
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
  5. *Standardized Form Controls & Segmented Button Contracts (`TST-UI-221.5`, `TuningFormControlsTest.kt`)*:
  • Verify `TuningFormControls.kt` exports `TuningSegmentedButtonRow` and `TuningDropdownRow` composables.
  • Verify `TuningSegmentedButtonRow` specifies `icon = {}`, `contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)`, `maxLines = 1`, `softWrap = false`, `overflow = TextOverflow.Ellipsis`, and `MaterialTheme.typography.labelSmall`.
  • Verify `RadarSettingsSection.kt` eliminates nested `ElevatedCard` containers and raw `Switch(` calls, standardizing on `TuningToggleItem`, `SubordinateTuningToggleItem`, `TuningSliderItem`, and `TuningSegmentedButtonRow` separated by `HorizontalDivider`s.
  6. *Design Guidelines Architecture Contract Enforcement (`TST-UI-221.6`, `TuningDesignGuidelinesContractTest.kt`)*:
  • Verify automated AST/text scanning of all category composables under `ui/settings/tuning/categories/`.
  • Assert zero raw `FilterChip(` calls for binary exclusive and small discrete selectors across category files (allowing only `SupporterDebugSection.kt` developer tier overrides).
  • Assert zero unscaled raw `Switch(` calls in `categories/`.
  • Assert zero nested `ElevatedCard(` containers in `categories/`.

### TST-UI-222: **Equipment Sensor Matrix Table Layout, Column Continuity, and Information Density Verification** [Verified]
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

### TST-UI-223: **Workout Cards & Details Tuning Dialog Layout, Typography Wrapping, and Reordering Ergonomics Verification** [Verified]
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

### TST-UI-224: **Workout List Filtering by Origin Source Attribute Verification** [Verified]
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

### TST-UI-225: **Immediate Collapsing Header Quick Return Expansion Verification** [Verified]
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

### TST-UI-226: **Routes & Segments Elevation Profile Below Map & Interactive Zooming Verification** [Verified]
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

### TST-UI-227: **SensorSourceDialog Truthful Connection Status, Parameterization & Priority Sorting Verification** [Verified]
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

### TST-UI-228: **ClusterInfoDialog Preserved Bullet Point Line Breaks & 9-Language Localization Audit Verification** [Verified]
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

### TST-UI-229: **LiveSegmentSheet Clamped Bottom Sheet Height & MapDetailLayout Viewport Constraints Verification** [Verified]
- **Requirement(s)**: `REQ-UI-270`
- **Component**: **LiveSegmentSheet Clamped Bottom Sheet Height & MapDetailLayout Viewport Constraints Verification**
- **Jira**: ``ATT-2231``
- **Test Description**:
  1. *Architectural Contract Verification (`LiveSegmentSheetContractTest.kt`)*:
  • Verify `LiveSegmentSheet.kt` passes `showMap = false`, `showZoomControls = false`, and applies `Modifier.fillMaxWidth().wrapContentHeight()`.
  • Verify `MapDetailLayout.kt` applies `wrapContentHeight()` on the resizable viewport Box when `!showMap && !hasScrollableContent`.
  • Verify `MapDetailLayout.kt` conditionally attaches `Modifier.nestedScroll(connection)` only when `showMap

### TST-UI-230: **Sport-Specific Equipment Sensor Matrix Partitioning and Incompatible Sensor Restriction Verification** [Verified]
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

### TST-UI-231: **Settings/Tuning: Simplified Preposition-Free Column Headers (List & Details) in Workout Cards & Details Matrix Verification** [Verified]
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

### TST-UI-232: **Elevation Profile Adaptive X-Axis Tick Spacing and Dynamic Label Collision Prevention Verification** [Verified]
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

### TST-UI-233: **Routes & Segments: Dynamically Maximized Map with Bottom-Anchored Intrinsic Elevation Profile Verification** [Verified]
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

### TST-UI-234: **Route Climbs Visual Prominence Across Elevation Profile, Map Polyline, Route Cards & Dedicated Breakdown Verification** [Verified]
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

### TST-UI-235: **Unified WYSIWYG Tracking Tab Configuration with Spatial Overlays and Per-Tab Popup Toggles Verification** [Verified]
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

### TST-UI-236: **Cockpit Tile Distinct Presets, Granular Corner Radius, Border Thickness, and Theme-Aware Border Contrast Verification** [Verified]
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

### TST-UI-237: **Settings Live Preview Cockpit Tile Styling & Comprehensive Section Naming Verification** [Verified]
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

### TST-UI-238: **Pairing Triggers Relocation to Sensor Management & Control Tracking Layout Optimization Verification** [Verified]
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

### TST-UI-239: **Branded Route Selection Button on Control Tracking Screen & Cockpit Sensor Grid Decoupling Verification** [Verified]
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

### TST-UI-240: **Route Selector Streamlining: Pinned Heimweg Card and Sort Tab Bar Removal Contract and Unit Verification** [Verified]
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

### TST-UI-241: **Configurable Route Selector Proximity Radius and Recency Sorting Verification** [Verified]
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

### TST-UI-242: **Mid-Ride Route Selector Return Navigation Entry Point & Take Me Home Design Consistency Verification** [Verified]
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

### TST-UI-243: **Equipment Sensor Matrix and Configuration Dialog: Sport-Specific Sensor Precedence Ordering Verification** [Verified]
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

### TST-UI-244: **Sensors View Sport-Specific Equipment Matrix Tabs, Visual Link Indicators & Equipment Tabs Consolidation Verification** [Verified]
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

### TST-UI-245: **Workout Section 9-Item Default Sequence and Visibility Matrix Verification** [Verified]
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

### TST-UI-246: **ResearchButton Jetpack Compose Vector Resilience & Universal Resource Fallback Verification** [Verified]
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

### TST-UI-247: **Navigation Cue Auto-Dismiss Duration, Countdown Distance & Solid Opaque Overlays Verification** [Verified]
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

### TST-UI-248: **Equipment Sensor Matrix Bike and Shoe Icon Tint Unification Verification** [Verified]
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

### TST-UI-249: **Context-Aware Route Selection: Pre-Tracking Start Proximity and In-Ride Corridor Auto-Detection Verification** [Verified]
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

---

## Change History

| Version | Date | Author | Ticket | Summary of Changes |
| :--- | :--- | :--- | :--- | :--- |
| `1.0.0` | 2026-11-18 | AI Agent (`ATT-8206`) | `ATT-8206` | Split oversized shard into compliant sub-shard (<= 4,000 lines, `REQ-PRO-093.4`, `REQ-PRO-127.3`). |
| `1.0.1` | 2026-11-18 | AI Agent (`ATT-8080`) | `ATT-8080` | Updated and verified (`[Verified]`) `TST-UI-055` for `ATT Fused` replacement of `Network` and bottom-sheet `Layers` wiring. |
| `1.0.2` | 2026-11-20 | Lane A Flash (`ATT-8330`, `--as agent1`) | `ATT-8330` | Promoted 47 verified UI Design System TSTs (`TST-UNT-003..014`, `TST-UI-011..108`) to `[Verified]` using Gate 4 `129/129` PASS evidence (SHA `3e20473b`, `Rule 7.5`); preserved `TST-UI-055` (`[Verified]`) and retained 18 Bucket C TSTs at `[Implemented]`; rebalanced `TST-UI-250..254` to `part3` to comply with `<= 4,000` line shard limit (`REQ-PRO-093.3`). |
