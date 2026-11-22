# Living Test Specification: Settings, Profiles & Localization

**Document ID**: `DOC-TST-SET`  
**Version**: 1.0.40  
**Status**: Reviewed  
**Approver**: Pending PO Sign-Off  

**Domain**: `settings_profiles`  
**Prefixes**: `LOC`, `MON`, `PRI`, `SET`  
**Scope**: Athlete settings, bike/sensor profiles, privacy zones, data stores, monetization, and 9-locale parity.  
**Total Tests**: 115

---

### TST-SET-031: **Unified Spatial Markers** [Verified]
- **Requirement(s)**: `REQ-SET-041`
- **Component**: **Unified Spatial Markers**
- **Test Description**: 1. Open Workout Details, Period Map, and Route Heatmap. 2. Inspect Start/End markers.
- **Verification Evidence**: Verified (`ATT-8328`, SHA `2a149eca`, Gate 4 `119/119` PASS across 22 suites): `UnifiedSpatialMarkersTest.kt` (`UnifiedSpatialMarkersTest.createSensorMarker_rendersUnifiedStartEndAndApexMarkers_TST_SET_031`)

### TST-SET-001: **Workout Learning Engine** [Verified]
- **Requirement(s)**: `REQ-SET-006`, `REQ-SET-007`, `REQ-SET-008`
- **Component**: **Workout Learning Engine**
- **Test Description**: 1. Track a route. 2. Name it \"Park Loop\" and set sport to \"Running\". 3. Track the same route again.
- **Verification Evidence**: Verified (`ATT-8328`, SHA `2a149eca`, Gate 4 `119/119` PASS across 22 suites): `WorkoutClusterEngineLearningTest.kt` (`WorkoutClusterEngineLearningTest.learnFromWorkout_learnsFingerprintRefinesCentroidAndPreservesCustomName_TST_SET_001`)

### TST-SET-002: **Favorite Tracks Heatmap** [Verified]
- **Requirement(s)**: `REQ-SET-009`
- **Component**: **Favorite Tracks Heatmap**
- **Test Description**: 1. Navigate to \"Workout Clusters\" drawer. 2. Select a recurring path.
- **Verification Evidence**: Verified (`ATT-8328`, SHA `2a149eca`, Gate 4 `119/119` PASS across 22 suites): `WorkoutClustersViewModelTest.kt` (`WorkoutClustersViewModelTest.selectCluster_whenClusterSelected_updatesClusterWorkoutsState`)

### TST-SET-003: **Cluster Tuning** [Verified]
- **Requirement(s)**: `REQ-SET-010`, `REQ-SET-011`
- **Component**: **Cluster Tuning** (`ClusterTuningContent.kt`, `ClusterTuningSection.kt`, `WorkoutClusterRepository.kt`)
- **Test Description**: 1. Open Route Clustering tuning controls (`ClusterTuningContent` / Advanced Settings `ClusterTuningSection` per `REQ-SET-075`). 2. Adjust the master sensitivity slider. 3. Optionally toggle detailed controls and adjust individual tolerances / score threshold. 4. Tap \"Recalculate All Clusters\" and confirm recalculation.
- **Verification Evidence**: Verified (`ATT-8328`, SHA `2a149eca`, Gate 4 `119/119` PASS across 22 suites): `ClusterTuningUiBehaviorTest.kt` (`ClusterTuningUiBehaviorTest.clusterTuningSection_masterSliderDetailsToggleAndRecalculateConfirm_TST_SET_003`)

### TST-SET-004: **Workout Cluster Item Map** [Verified]
- **Requirement(s)**: `REQ-SET-012`
- **Component**: **Workout Cluster Item Map**
- **Test Description**: 1. Navigate to \"Workout Clusters\". 2. Inspect list items.
- **Verification Evidence**: Verified (`ATT-8328`, SHA `2a149eca`, Gate 4 `119/119` PASS across 22 suites): `ClusterItemThumbnailComposeTest.kt` (`ClusterItemThumbnailComposeTest.clusterItem_renders175x120ThumbnailWith12dpGapAndHandlesClicksOn411dp`)

### TST-SET-005: **Name-Aware Clustering** [Verified]
- **Requirement(s)**: `REQ-SET-013`
- **Component**: **Name-Aware Clustering**
- **Test Description**: 1. Identify two workouts with same name but high spatial drift. 2. Trigger recalculation.
- **Verification Evidence**: Verified (`ATT-8328`, SHA `2a149eca`, Gate 4 `119/119` PASS across 22 suites): `WorkoutClusterEngineLearningTest.kt` (`WorkoutClusterEngineLearningTest.calculateSimilarity_appliesHalfScoreBonusForMatchingNormalizedName_TST_SET_005`)

### TST-SET-006: **Auto-Dismiss Tuning** [Implemented]
- **Requirement(s)**: `REQ-SET-014`
- **Component**: **Auto-Dismiss Tuning**
- **Test Description**: 1. Open Tuning screen. 2. Tap \"Recalculate All Clusters\". 3. Wait for completion. *(Superseded by `REQ-SET-075` / `TST-SET-077` under `ATT-3452`: header tuning removed from Favorite Routes and centralized in Advanced Settings without auto-dismissal; retained `[Implemented]`.)*

### TST-SET-007: **Unique Cluster Naming** [Verified]
- **Requirement(s)**: `REQ-SET-015`
- **Component**: **Unique Cluster Naming**
- **Test Description**: 1. Create a cluster named \"Home\". 2. Create a spatially distinct route and also name it \"Home\".
- **Verification Evidence**: Verified (`ATT-8328`, SHA `2a149eca`, Gate 4 `119/119` PASS across 22 suites): `WorkoutClusterEngineLearningTest.kt` (`WorkoutClusterEngineLearningTest.learnFromWorkout_appendsVariantCounterForDistinctRoutesWithSameName_TST_SET_007`)

### TST-SET-008: **Edit Cluster Identity** [Verified]
- **Requirement(s)**: `REQ-SET-016`
- **Component**: **Edit Cluster Identity**
- **Test Description**: 1. Select a workout cluster. 2. Tap Edit Icon in TopAppBar. 3. Enter new name and select a different sport. 4. Save.
- **Verification Evidence**: Verified (`ATT-8328`, SHA `2a149eca`, Gate 4 `119/119` PASS across 22 suites): `WorkoutClusterDialogsUiTest.kt` (`WorkoutClusterDialogsUiTest.editWorkoutClusterIdentityDialog_renamesChangesSportAndTogglesCounter_TST_SET_008`)

### TST-SET-009: **Manual Workout Reassignment** [Verified]
- **Requirement(s)**: `REQ-SET-017`
- **Component**: **Manual Workout Reassignment**
- **Test Description**: 1. Open a Workout Heatmap. 2. Tap a specific workout polyline. 3. Select a different cluster from the candidate list. 4. Save.
- **Verification Evidence**: Verified (`ATT-8328`, SHA `2a149eca`, Gate 4 `119/119` PASS across 22 suites): `WorkoutClusterDialogsUiTest.kt` (`WorkoutClusterDialogsUiTest.workoutClusterSelectionDialog_displaysSyncedHitCountAndSelectsCandidate_TST_SET_009_TST_SET_044`)

### TST-SET-010: **Edit Workout Suggestions** [Verified]
- **Requirement(s)**: `REQ-SET-018`
- **Component**: **Edit Workout Suggestions**
- **Test Description**: 1. Edit a workout. 2. Tap the \"My Locations\" icon in the Name field. 3. Select a suggested cluster.
- **Verification Evidence**: Verified (`ATT-8328`, SHA `2a149eca`, Gate 4 `119/119` PASS across 22 suites): `WorkoutClusterDialogsUiTest.kt` (`WorkoutClusterDialogsUiTest.editWorkoutClusterDialog_displaysCandidatesAndInvokesOnSelectCluster_TST_SET_010`)

### TST-SET-011: **Manual Cluster Creation** [Implemented]
- **Requirement(s)**: `REQ-SET-019`
- **Component**: **Manual Cluster Creation**
- **Test Description**: 1. Navigate to \"Workout Clusters\". 2. Tap \"+\" FAB. 3. Fill in name and distance. 4. Tap map to set 3 points. 5. Save.
- **Verification Status Note**: *(Bucket C — retained `[Implemented]` pending instrumented `GoogleMap(onMapClick)` 3-point map tap verification in `ManualClusterScreen`.)*

### TST-SET-012: **Workout Peek & Move** [Verified]
- **Requirement(s)**: `REQ-SET-020`
- **Component**: **Workout Peek & Move**
- **Test Description**: 1. Open a Workout Heatmap. 2. Tap a specific workout track. 3. Observe the Bottom Sheet peek. 4. Tap the \"Move\" FAB in the peek.
- **Verification Evidence**: Verified (`ATT-8328`, SHA `2a149eca`, Gate 4 `119/119` PASS across 22 suites): `WorkoutClusterDialogsUiTest.kt` (`WorkoutClusterDialogsUiTest.workoutHeader_onChangeClusterButton_triggersClusterMoveDialog_TST_SET_012`)

### TST-SET-013: **Manual Fingerprint Edit** [Implemented]
- **Requirement(s)**: `REQ-SET-021`
- **Component**: **Manual Fingerprint Edit**
- **Test Description**: 1. Open a Workout Heatmap. 2. Tap a signature marker (Start/End/Apex). 3. Select \"Edit Position\". 4. Tap map to set new position. 5. Save.
- **Verification Status Note**: *(Bucket C — retained `[Implemented]` pending instrumented `GoogleMap` `Marker` drag verification in `WorkoutClusterHeatmapScreen`.)*

### TST-SET-014: **Workout Markers in Cluster** [Verified]
- **Requirement(s)**: `REQ-SET-022`
- **Component**: **Workout Markers in Cluster**
- **Test Description**: 1. Open a Workout Heatmap. 2. Observe the distribution of markers for all members. 3. Tap a specific workout track OR any marker. 4. Inspect markers in the peek.
- **Verification Evidence**: Verified (`ATT-8328`, SHA `2a149eca`, Gate 4 `119/119` PASS across 22 suites): `WorkoutClustersViewModelTest.kt` (`WorkoutClustersViewModelTest.selectCluster_whenClusterSelected_updatesClusterWorkoutsState`, `WorkoutClustersViewModelTest.selectWorkoutForPeek_afterSelectCluster_resolvesWorkoutAndLoadsTrackAndMarkers`)

### TST-SET-015: **Imperial Units in Clusters** [Verified]
- **Requirement(s)**: `REQ-SET-023`
- **Component**: **Imperial Units in Clusters**
- **Test Description**: 1. Switch to Imperial. 2. Add manual cluster (miles). 3. View tuning details (mile).
- **Verification Evidence**: Verified (`ATT-8328`, SHA `2a149eca`, Gate 4 `119/119` PASS across 22 suites): `ClusterImperialAndLocaleUiTest.kt` (`ClusterImperialAndLocaleUiTest.clusterTuningContent_andSubtitleFormatter_renderImperialMiles_TST_SET_015`)

### TST-SET-016: **Cluster Equipment Determination** [Verified]
- **Requirement(s)**: `REQ-SET-024`
- **Component**: **Cluster Equipment Determination**
- **Test Description**: 1. Open a Workout Cluster. 2. Verify the associated equipment list (→).
- **Verification Evidence**: Verified (`ATT-8328`, SHA `2a149eca`, Gate 4 `119/119` PASS across 22 suites): `WorkoutClustersTabsUiTest.kt` (`WorkoutClustersTabsUiTest.workoutClusterDetails_displaysEquipmentArrowNotation_andTriggersHitCountClick_TST_SET_016_TST_SET_041`)

### TST-SET-017: **Workout Cluster Collapsing Header** [Verified]
- **Requirement(s)**: `REQ-SET-025`
- **Component**: **Workout Cluster Collapsing Header**
- **Test Description**: 1. Navigate to \"Workout Clusters\". 2. Scroll the list downwards. 3. Observe the TopAppBar behavior.
- **Verification Evidence**: Verified (`ATT-8328`, SHA `2a149eca`, Gate 4 `119/119` PASS across 22 suites): `CollapsingAppBarNestedScrollConnectionTest.kt` (`CollapsingAppBarNestedScrollConnectionTest.testUpwardScroll_consumesInPreScroll_andCollapses`, `CollapsingAppBarNestedScrollConnectionTest.testDownwardScroll_consumesInPreScroll_andExpandsImmediately`); `WorkoutClustersTabsUiTest.kt` (`WorkoutClustersTabsUiTest.workoutClustersTabsScreen_rendersTabsFabFiltersAndUnclusteredNavigation_TST_SET_017_018_020_029_040`)

### TST-SET-018: **Workout Cluster Tabbed Layout** [Verified]
- **Requirement(s)**: `REQ-SET-026`
- **Component**: **Workout Cluster Tabbed Layout**
- **Test Description**: 1. Navigate to \"Workout Clusters\". 2. Observe the tabs at the top. 3. Swipe between tabs (All, Bike, Run, Other).
- **Verification Evidence**: Verified (`ATT-8328`, SHA `2a149eca`, Gate 4 `119/119` PASS across 22 suites): `WorkoutClustersTabsUiTest.kt` (`WorkoutClustersTabsUiTest.workoutClustersTabsScreen_rendersTabsFabFiltersAndUnclusteredNavigation_TST_SET_017_018_020_029_040`)

### TST-SET-019: **Workout Cluster Scroll Persistence** [Verified]
- **Requirement(s)**: `REQ-SET-027`
- **Component**: **Workout Cluster Scroll Persistence**
- **Test Description**: 1. Scroll down the \"All\" tab. 2. Select a cluster to open its heatmap. 3. Navigate back.
- **Verification Evidence**: Verified (`ATT-8328`, SHA `2a149eca`, Gate 4 `119/119` PASS across 22 suites): `WorkoutClustersTabsUiTest.kt` (`WorkoutClustersTabsUiTest.workoutClustersTabsScreen_preservesHoistedScrollStateAcrossDetailToggle_TST_SET_019`)

### TST-SET-020: **Unified FAB Layout** [Verified]
- **Requirement(s)**: `REQ-SET-028`
- **Component**: **Unified FAB Layout**
- **Test Description**: 1. Navigate to \"Workout Clusters\". 2. Observe FAB position and styling. 3. Compare with \"Sport Types\" and \"Equipment\" FABs.
- **Verification Evidence**: Verified (`ATT-8328`, SHA `2a149eca`, Gate 4 `119/119` PASS across 22 suites): `WorkoutClustersTabsUiTest.kt` (`WorkoutClustersTabsUiTest.workoutClustersTabsScreen_rendersTabsFabFiltersAndUnclusteredNavigation_TST_SET_017_018_020_029_040`)

### TST-SET-021: **Identity Arbitration** [Verified]
- **Requirement(s)**: `REQ-SET-029`, `REQ-SET-030`
- **Component**: **Identity Arbitration**
- **Test Description**: 1. Link a specific sensor to a bike. 2. Record a session with that sensor on a route matched to a cluster with a different majority sport.

### TST-SET-022: **Route-to-Cluster Matching** [Verified]
- **Requirement(s)**: `REQ-SET-031`
- **Component**: **Route-to-Cluster Matching**
- **Test Description**: 1. Import a GPX Route. 2. Navigate to \"Workout Clusters\".
- **Verification Evidence**: Verified (`ATT-8328`, SHA `2a149eca`, Gate 4 `119/119` PASS across 22 suites): `WorkoutClusterEngineRouteSyncTest.kt` (`WorkoutClusterEngineRouteSyncTest.learnFromRoute_andSyncRouteNameChange_createAndRenameLinkedCluster_TST_SET_022`)

### TST-SET-023: **Reference Route in Heatmap** [Verified]
- **Requirement(s)**: `REQ-SET-032`
- **Component**: **Reference Route in Heatmap**
- **Test Description**: 1. Open a Workout Cluster that is linked to an explicit Route. 2. Observe the heatmap visualization.
- **Verification Evidence**: Verified (`ATT-8328`, SHA `2a149eca`, Gate 4 `119/119` PASS across 22 suites): `WorkoutClustersViewModelTest.kt` (`WorkoutClustersViewModelTest.selectCluster_withLinkedRoute_populatesLinkedRouteStateFlow_TST_SET_023`)

### TST-SET-024: **Workout Cluster Deletion** [Verified]
- **Requirement(s)**: `REQ-SET-033`
- **Component**: **Workout Cluster Deletion**
- **Test Description**: 1. Open a Workout Cluster detail view. 2. Long-click the header. 3. Select \"Delete\". 4. Confirm deletion.
- **Verification Evidence**: Verified (`ATT-8328`, SHA `2a149eca`, Gate 4 `119/119` PASS across 22 suites): `WorkoutClusterDeleteTest.kt` (`WorkoutClusterDeleteTest.deleteCluster_clearsWorkoutAndRouteLinksWhilePreservingWorkouts_TST_SET_024`, `WorkoutClusterDeleteTest.summaryHeader_longClickOpensDeleteMenuAndInvokesCallback_TST_SET_024`)

### TST-SET-025: **Stable Cluster Naming** [Verified]
- **Requirement(s)**: `REQ-SET-034`
- **Component**: **Stable Cluster Naming**
- **Test Description**: 1. Identify a cluster named \"Park Loop\". 2. Track a workout matching this cluster.
- **Verification Evidence**: Verified (`ATT-8328`, SHA `2a149eca`, Gate 4 `119/119` PASS across 22 suites): `WorkoutClusterEngineLearningTest.kt` (`WorkoutClusterEngineLearningTest.assignClusterToWorkout_preservesBaseClusterNameWithoutCounterSuffix_TST_SET_025`)

### TST-SET-026: **Preview Heatmap** [Verified]
- **Requirement(s)**: `REQ-SET-035`
- **Component**: **Preview Heatmap**
- **Test Description**: 1. Open the Favorite Tracks list. 2. Observe the mini-map in a list item.
- **Verification Evidence**: Verified (`ATT-8328`, SHA `2a149eca`, Gate 4 `119/119` PASS across 22 suites): `WorkoutClusterEngineLearningTest.kt` (`WorkoutClusterEngineLearningTest.assignClusterToWorkout_capsPreviewPathsAtFiveTracks_TST_SET_026`); `ClusterItemThumbnailComposeTest.kt` (`ClusterItemThumbnailComposeTest.clusterItem_renders175x120ThumbnailWith12dpGapAndHandlesClicksOn411dp`)

### TST-SET-027: **Persistent DB Connection** [Verified]
- **Requirement(s)**: `REQ-SET-036`
- **Component**: **Persistent DB Connection** (`WorkoutClusterDatabaseManager.kt`)
- **Test Description**: 1. Exercise `WorkoutClusterDatabaseManager.getDatabase()` across sequential reads and writes (`insertCluster`, `getAllClusters`, `updateCluster`) and verify `isOpen` remains `true` without premature closure, and auto-reopens cleanly if closed externally. 2. (Optional IDE check: open Android Studio Database Inspector while app runs.)
- **Verification Evidence**: Verified (`ATT-8328`, SHA `2a149eca`, Gate 4 `119/119` PASS across 22 suites): `WorkoutClusterDatabaseConnectionTest.kt` (`WorkoutClusterDatabaseConnectionTest.getDatabase_remainsOpenAcrossSequentialOperationsAndReopensIfClosed_TST_SET_027`)

### TST-SET-028: **Hit Count Synchronization** [Verified]
- **Requirement(s)**: `REQ-SET-037`
- **Component**: **Hit Count Synchronization**
- **Test Description**: 1. Identify a cluster with hit count > 0. 2. Use Database Inspector to manually clear `clusterId` for its workouts. 3. Open Favorite Tracks list.
- **Verification Evidence**: Verified (`ATT-8328`, SHA `2a149eca`, Gate 4 `119/119` PASS across 22 suites): `EditWorkoutClusterTest.kt` (`EditWorkoutClusterTest.testAssignClusterToWorkout_usesAuthoritativeCountFromWorkoutSummaries`, `EditWorkoutClusterTest.testGetClusterScores_reconcilesStaleHitCountAgainstWorkoutSummaries`)

### TST-SET-029: **Unclustered Tab Verification** [Verified]
- **Requirement(s)**: `REQ-SET-040`
- **Component**: **Unclustered Tab Verification**
- **Test Description**: 1. Navigate to Favorite Tracks. 2. Open 'Offen' tab. 3. Tap a workout.
- **Verification Evidence**: Verified (`ATT-8328`, SHA `2a149eca`, Gate 4 `119/119` PASS across 22 suites): `WorkoutClustersTabsUiTest.kt` (`WorkoutClustersTabsUiTest.workoutClustersTabsScreen_rendersTabsFabFiltersAndUnclusteredNavigation_TST_SET_017_018_020_029_040`)

### TST-SET-030: **Locale-Aware Distance Input** [Verified]
- **Requirement(s)**: `REQ-SET-039`
- **Component**: **Locale-Aware Distance Input**
- **Test Description**: 1. Set device locale to German. 2. Open 'Add Workout Cluster' flow. 3. Enter '5,5' in the distance field.
- **Verification Evidence**: Verified (`ATT-8328`, SHA `2a149eca`, Gate 4 `119/119` PASS across 22 suites): `ClusterImperialAndLocaleUiTest.kt` (`ClusterImperialAndLocaleUiTest.manualClusterScreen_acceptsGermanCommaDecimalDistanceInput_TST_SET_030`)

### TST-SET-032: **Markers & Framing in Previews** [Verified]
- **Requirement(s)**: `REQ-SET-042`
- **Component**: **Markers & Framing in Previews**
- **Jira**: ``ATT-390``
- **Test Description**: 1. Open Workout History, Routes, and My Locations lists. 2. Observe the mini-maps.
- **Verification Evidence**: Verified (`ATT-8328`, SHA `2a149eca`, Gate 4 `119/119` PASS across 22 suites): `PathPreviewMapBoundsTest.kt` (`PathPreviewMapBoundsTest.pathPreviewMap_rendersSafelyInsideIntrinsicSizeMinWithoutSubcomposeCrash`, `PathPreviewMapBoundsTest.resolvePreviewBounds_prioritizesTargetThenPathThenCompositePoints`)

### TST-SET-033: **Fingerprint Parity** [Verified]
- **Requirement(s)**: `REQ-SET-043`
- **Component**: **Fingerprint Parity**
- **Test Description**: 1. Import a Route. 2. Record a workout on that exact route. 3. Navigate to Favorite Tracks.
- **Verification Evidence**: Verified (`ATT-8328`, SHA `2a149eca`, Gate 4 `119/119` PASS across 22 suites): `WorkoutClusterEngineLearningTest.kt` (`WorkoutClusterEngineLearningTest.learnFromRoute_andWorkoutOnSamePolyline_produceIdenticalFingerprint_TST_SET_033`)

### TST-SET-034: **Sport-Aware Clustering** [Verified]
- **Requirement(s)**: `REQ-SET-010`, `REQ-SET-043`
- **Component**: **Sport-Aware Clustering**
- **Jira**: ``ATT-399``
- **Test Description**: 1. Navigate to My Locations -> Tuning. 2. Toggle 'Take Sport Type into account' to ON. 3. Recalculate clusters. 4. Verify that Runs and Rides on the same path remain in separate clusters. 5. Toggle to OFF and recalculate. 6. Verify they merge into one cluster.
- **Verification Evidence**: Verified (`ATT-8328`, SHA `2a149eca`, Gate 4 `119/119` PASS across 22 suites): `WorkoutClusterEngineLearningTest.kt` (`WorkoutClusterEngineLearningTest.suggestCluster_respectsSportAwareClusteringToggle_TST_SET_034`)

### TST-SET-035: **Sport Mismatch with UNKNOWN** [Verified]
- **Requirement(s)**: `REQ-SET-043`
- **Component**: **Sport Mismatch with UNKNOWN**
- **Jira**: ``ATT-427``
- **Test Description**: 1. Ensure Tuning toggle is ON. 2. Record a 'Running' workout on a path that currently has an 'UNKNOWN' cluster.
- **Verification Evidence**: Verified (`ATT-8328`, SHA `2a149eca`, Gate 4 `119/119` PASS across 22 suites): `WorkoutClusterEngineLearningTest.kt` (`WorkoutClusterEngineLearningTest.calculateSimilarity_appliesTwoPointPenaltyForRunVsUnknownCluster_TST_SET_035`)

### TST-SET-040: **Unclustered Workout Navigation** [Verified]
- **Requirement(s)**: `REQ-SET-056`
- **Component**: **Unclustered Workout Navigation**
- **Jira**: ``ATT-278``
- **Test Description**: 1. Open 'Unclustered' tab in My Locations. 2. Verify layout parity with clusters (mini-map exists). 3. Tap a workout. 4. Verify navigation to map. 5. Tap 'Move to Cluster' icon.
- **Verification Evidence**: Verified (`ATT-8328`, SHA `2a149eca`, Gate 4 `119/119` PASS across 22 suites): `WorkoutClustersTabsUiTest.kt` (`WorkoutClustersTabsUiTest.workoutClustersTabsScreen_rendersTabsFabFiltersAndUnclusteredNavigation_TST_SET_017_018_020_029_040`)

### TST-SET-041: **Cluster Member Listing** [Verified]
- **Requirement(s)**: `REQ-SET-057`
- **Component**: **Cluster Member Listing**
- **Jira**: ``ATT-280``
- **Test Description**: 1. Open 'Clusters' tab. 2. Click on the \"X recordings\" text for a cluster. 3. Verify navigation to a list containing only those recordings.
- **Verification Evidence**: Verified (`ATT-8328`, SHA `2a149eca`, Gate 4 `119/119` PASS across 22 suites): `WorkoutClustersTabsUiTest.kt` (`WorkoutClustersTabsUiTest.workoutClusterDetails_displaysEquipmentArrowNotation_andTriggersHitCountClick_TST_SET_016_TST_SET_041`)

### TST-SET-042: **Workout Session Naming Isolation** [Verified]
- **Requirement(s)**: `REQ-SET-034`, `REQ-SET-044`
- **Component**: **Workout Session Naming Isolation**
- **Jira**: ``ATT-317``
- **Test Description**: 1. Track a workout matching cluster 'Park Loop'. 2. Open 'Edit Workout' screen. 3. Change name to 'Morning Run'. 4. Save.
- **Verification Evidence**: Verified (`ATT-8328`, SHA `2a149eca`, Gate 4 `119/119` PASS across 22 suites): `WorkoutRepositorySaveClusteringTest.kt` (`WorkoutRepositorySaveClusteringTest.testSaveWorkout_whenClustered_updatesCentroidsWithoutReassigningCluster`)

### TST-SET-043: **Cluster Visibility & Re-clustering** [Verified]
- **Requirement(s)**: `REQ-SET-058`, `REQ-SET-059`
- **Component**: **Cluster Visibility & Re-clustering**
- **Jira**: ``ATT-395``
- **Test Description**: 1. Open the Workout History list. 2. Verify each item displays its Cluster Name with the leading route icon at the very left below the workout name. 3. Edit a workout and manually re-assign it to a different cluster. 4. Save and verify the update in the list.

### TST-SET-044: **Selection Dialog Hit Count Accuracy** [Verified]
- **Requirement(s)**: `REQ-SET-038`
- **Component**: **Selection Dialog Hit Count Accuracy**
- **Jira**: ``ATT-429``
- **Test Description**: 1. Identify a cluster with 5 associated workouts. 2. Open a workout for manual re-clustering or trigger a TCX import interaction. 3. Open the "Select Existing Route" dialog.
- **Verification Evidence**: Verified (`ATT-8328`, SHA `2a149eca`, Gate 4 `119/119` PASS across 22 suites): `WorkoutClusterDialogsUiTest.kt` (`WorkoutClusterDialogsUiTest.workoutClusterSelectionDialog_displaysSyncedHitCountAndSelectsCandidate_TST_SET_009_TST_SET_044`)

### TST-SET-045: **Selectable Cluster Markers** [Verified]
- **Requirement(s)**: `REQ-SET-060`
- **Component**: **Selectable Cluster Markers**
- **Jira**: ``ATT-463``
- **Test Description**: 1. Open a Workout Cluster heatmap. 2. Tap the Marker Options button. 3. Uncheck "Start Point". 4. Verify that all green pins (signature and member distribution) disappear. 5. Re-check and Verify reappearance. 6. Re-check persistence after app restart.
- **Verification Evidence**: Verified (`ATT-8328`, SHA `2a149eca`, Gate 4 `119/119` PASS across 22 suites): `WorkoutClustersSelectableMarkersTest.kt` (`WorkoutClustersSelectableMarkersTest.setClusterMarkerTypeEnabled_persistsAcrossInstancesAndIsolatesPeriodPrefs_TST_SET_045`)

### TST-SET-046: **Reactive Editor Suggestions** [Verified]
- **Requirement(s)**: `REQ-SET-018`
- **Component**: **Reactive Editor Suggestions**
- **Jira**: ``ATT-447``
- **Test Description**: 1. Edit a 'Running' workout on a 'Cycling' path. 2. Observe high similarity score (> 5.0) for 'Cycling' cluster in suggestions. 3. Change workout sport to 'Cycling'. 4. Verify score drops below 1.0 (penalty removed).
- **Verification Evidence**: Verified (`ATT-8328`, SHA `2a149eca`, Gate 4 `119/119` PASS across 22 suites): `EditWorkoutViewModelTest.kt` (`EditWorkoutViewModelTest.testUpdateSportName_reactivelyRefreshesClusterSuggestionsAndDropsScore_TST_SET_046`)

### TST-SET-047: **Idempotent Route Cluster Creation** [Verified]
- **Requirement(s)**: `REQ-SET-061`
- **Component**: **Idempotent Route Cluster Creation**
- **Jira**: ``ATT-543``
- **Test Description**: 1. Import multiple TCX workout files that follow the same route name (e.g. "Route A"). 2. Verify cluster assignment.
- **Verification Evidence**: Verified (`ATT-8328`, SHA `2a149eca`, Gate 4 `119/119` PASS across 22 suites): `WorkoutClusterEngineLearningTest.kt` (`WorkoutClusterEngineLearningTest.learnFromWorkout_reusesExistingNormalizedNameWithoutCreatingDuplicate_TST_SET_047`)

### TST-SET-048: **Automatic Zero-Workout Cluster Purging** [Verified]
- **Requirement(s)**: `REQ-SET-062`
- **Component**: **Automatic Zero-Workout Cluster Purging**
- **Jira**: ``ATT-544``
- **Test Description**: 1. Trigger a TCX import or cluster refresh. 2. Inspect the cluster list and database for orphan clusters with 0 assigned workouts and no linked Route. 3. Import an explicit Route with 0 workouts.

### TST-SET-049: **Route Cluster Apex Arithmetic Mean & Live Tracking Synchronization** [Verified]
- **Requirement(s)**: `REQ-SET-063`
- **Component**: **Route Cluster Apex Arithmetic Mean & Live Tracking Synchronization**
- **Jira**: ``ATT-1138``
- **Test Description**: 1. Assign or learn workouts into a Workout Cluster. 2. Verify that the cluster's `maxDispLat` and `maxDispLng` are the exact arithmetic mean of the member workouts' maximum displacement coordinates. 3. Delete or move a member workout and verify the moving average reverses accurately. 4. Select/inspect the cluster in `WorkoutClustersViewModel` and verify that the cluster's apex in SQLite is NOT mutated or overwritten. 5. Verify `WorkoutClusterEngine.onWorkoutFinished` propagates `maxDisplacementLatLng` instead of `startLatLng`. 6. Verify `SpeedAndLocationDevice` clears `mStartLocation` upon reset.

### TST-SET-050: **Explicit Cluster Selection (No Cluster, New Cluster) in Edit Workout** [Verified]
- **Requirement(s)**: `REQ-SET-059`
- **Component**: **Explicit Cluster Selection (No Cluster, New Cluster) in Edit Workout**
- **Jira**: ``ATT-666``
- **Test Description**: 1. Open the Workout History list and tap a workout to open `EditWorkoutScreen`. 2. Verify an explicit Cluster Assignment field is rendered directly below the Workout Name input, displaying the route icon (`my_locations`), label ("Route Assignment" / "Selected Route"), and current cluster name (or "Unclustered" / "Leave Unclustered"). 3. Tap the Cluster Assignment field to launch the assignment dialog (harmonized with `RouteClusterNamingDialog`). 4. Select "Leave Unclustered" / "Kein Cluster" and save the workout: verify that the workout is unassigned (`clusterId = -1L`), its previous cluster hit count is decremented, and the workout header displays "Unclustered". 5. Re-open editor, tap the Cluster Assignment field, select "Create New..." / enter a new route name, and save: verify a new cluster is generated from the workout's spatial fingerprint and assigned to the workout. 6. Re-open editor, tap the Cluster Assignment field, select an existing cluster candidate: verify the workout is reassigned, hit counts are updated atomically, and the new cluster name is displayed.

### TST-SET-051: **Speed-Based Multi-Sport Cluster Discovery & Identity Resolution** [Verified]
- **Requirement(s)**: `REQ-SET-064`
- **Component**: **Speed-Based Multi-Sport Cluster Discovery & Identity Resolution**
- **Jira**: ``ATT-773``
- **Test Description**: 1. Create/seed a Workout Cluster with `BSportType.BIKE` for a known route geometry. 2. Simulate/record a workout following the identical route geometry with average speed 3.0 m/s without paired cadence/power sensors and without user pre-selection (speed suggests both `RUN` and `BIKE`). 3. Verify that `suggestCluster` identifies the existing bike cluster with 0.0 sport penalty and links the workout to it, adopting `BSportType.BIKE`. 4. Repeat with explicit user pre-selection of `BSportType.RUN`: verify the bike cluster receives +5.0 penalty and is rejected. 5. Repeat with high-confidence cadence sensor active: verify candidate sports are strictly `{BIKE}`.

### TST-SET-052: **Altitude Extrema-Aware Cluster Matching, Migration & Fingerprint Relocation** [Verified]
2. 3D altitude-aware scoring distinguishes routes with distinct vertical profiles.
3. Flat/sensorless routes degrade gracefully.
4. Interactive map markers display and relocate altitude extrema anchors reliably.]
- **Requirement(s)**: `REQ-SET-065`
- **Component**: **Altitude Extrema-Aware Cluster Matching, Migration & Fingerprint Relocation**
- **Jira**: ``ATT-502``
- **Test Description**:
  1. Seed existing database with clusters and verify schema migration from v9 to v10 with automated backfill of member workout altitude centroids.
  2. Evaluate similarity of workout against existing cluster with matching Start, End, Apex, and Distance, but differing Min/Max altitude positions (> 400m apart): verify score >= 1.0 (rejected match).
  3. Evaluate workout with matching Min/Max altitude positions: verify score < 1.0 (matching cluster).
  4. Verify fallback to 4-term matching with zero penalty when altitude is null or preference disabled.
  5. Open Cluster Heatmap: verify Min and Max Altitude markers are rendered and toggleable in options menu.
  6. Enter fingerprint editing: drag Min and Max Altitude markers, save, and verify persisted coordinates.

### TST-SET-053: **Workout Cluster Auto-Naming Counter Ergonomics Verification** [Verified]
- **Requirement(s)**: `REQ-SET-066`, `REQ-SET-004`
- **Component**: **Workout Cluster Auto-Naming Counter Ergonomics Verification**
- **Jira**: ``ATT-785``
- **Test Description**:
  1. Unit test `WorkoutClusterEngine.formatClusterWorkoutName`:
  • Test with `hitCount = 0` (or `1`): verify returned string is exactly `clusterName` without `"#1"` or any suffix.
  • Test with `hitCount = 2`: verify returned string is `clusterName + " #2"`.
  • Test with `hitCount = 5`: verify returned string is `clusterName + " #5"`.
  2. Initial Cluster Assignment:
  • Assign a workout to an empty cluster (`hitCount = 0`): verify workout name in database is set to `cluster.name` (NOT `cluster.name + " #1"`).
  3. Subsequent Cluster Assignment:
  • Assign a second workout to the same cluster: verify second workout name is set to `cluster.name + " #2"`.
  4. Live Tracking Auto-Naming (`TrackerService`):
  • Finish live tracking with high-confidence sensors matching a cluster with 0 hits: verify autoName is `suggestion.getName()`.
  • Finish live tracking matching a cluster with 1 hit: verify autoName is `suggestion.getName() + " #2"`.
  5. Workout Editor Selection (`EditWorkoutViewModel`):
  • Apply a cluster with `hitCount = 0`: verify proposed workout name in UI is `cluster.name`.
  • Apply a cluster with `hitCount = 1`: verify proposed workout name in UI is `cluster.name + " #2"`.
  6. Normalization & Deduplication Invariants:
  • Verify `stripHitCount` and `normalizeName` return identical base outputs for names with and without `"#1"`.

### TST-SET-054: **Optional Route Cluster Counter Verification** [Verified]
- **Requirement(s)**: `REQ-SET-067`
- **Component**: **Optional Route Cluster Counter Verification**
- **Jira**: ``ATT-714``
- **Test Description**:
  1. Database Schema & Migration Verification:
  • Seed database with v10 schema and clusters lacking `has_counter`.
  • Trigger migration to v11: verify column `has_counter` is added with default value 1.
  • Query all clusters: verify `hasCounter == true` for existing entries.
  2. Unit test `WorkoutClusterEngine.formatClusterWorkoutName`:
  • Test with `hasCounter = true, hitCount = 1`: verify returned name is `clusterName`.
  • Test with `hasCounter = true, hitCount = 3`: verify returned name is `clusterName + " #3"`.
  • Test with `hasCounter = false, hitCount = 1`: verify returned name is `clusterName` (no suffix).
  • Test with `hasCounter = false, hitCount = 3`: verify returned name is `clusterName` (no suffix).
  • Test with `hasCounter = false, hitCount = 10`: verify returned name is `clusterName` (no suffix).
  3. Cluster Identity Modification:
  • Open `EditWorkoutClusterIdentityDialog` for a cluster: toggle "Activity Counter" to off (`false`) and save.
  • Verify database persists `has_counter = 0`.
  • Assign subsequent workouts to this cluster: verify workout names do not receive `#<count>`.
  4. Live Tracking & Editor Verification:
  • Live tracking completion with cluster suggestion having `hasCounter = false`: verify suggested workout name has no suffix.
  • Edit workout applying cluster with `hasCounter = false`: verify proposed workout name has no suffix.
  5. Invariant Verification:
  • Verify cluster hitCount in database continues to increment regardless of `hasCounter` setting.

### TST-SET-055: **Workout Cluster Performance & Volume Statistics Verification** [Verified]
- **Requirement(s)**: `REQ-SET-068`
- **Component**: **Workout Cluster Performance & Volume Statistics Verification**
- **Jira**: ``ATT-221``
- **Test Description**:
  1. Database Aggregation Unit Tests (`WorkoutClusterStatsTest.kt`):
  • Zero Workouts: Query cluster with 0 sessions; verify `workoutCount = 0`, `bestDurationSec = null`, and safe default values without crashes.
  • Single Workout: Verify Best Time equals Average Time equals workout duration; Total Distance equals workout distance; Total Ascent equals workout ascent.
  • Multiple Workouts: Seed cluster with sessions of durations [2400s, 2100s, 2700s], distances [10000m, 10200m, 9800m], ascents [150m, 200m, 180m]; verify Best Time is 2100s, Average Time is 2400s, Total Distance is 30000m, Total Ascent is 530m.
  • Unfinished Workout Filter: Include unfinished workout (`finished = 0`) with shorter duration; verify it is excluded from Best Time calculation.
  2. Recency & Relative Time Formatting:
  • Test `lastHitRelativeFormatted` for sessions logged today ("Today"), yesterday ("Yesterday"), 4 days ago ("4 days ago"), and 3 weeks ago ("3 weeks ago").
  3. Presentation, Layout & Sport-Adaptive Verification:
  • Running Sport: Verify velocity formats as Pace (e.g., `min/km`).
  • Cycling Sport: Verify velocity formats as Speed (e.g., `km/h`).
  • Detail Screen Split Pane (`WorkoutClusterHeatmapScreen`): Verify `MapDetailLayout.header` renders only the compact 2-line header, and `MapDetailLayout.analyticsContent` renders the 3-tier dashboard below `SplitPaneDivider`.
  • Bottom Tabs: Verify Tab 1 renders 3-tier performance & volume metrics, and Tab 2 renders `WorkoutClusterTrendChartCard` when valid workout count $N \ge 2$.
  • List Card (`ClusterItem`): Verify Best Time, Total Distance, and Last Hit render cleanly without clipping the 100x100 preview map.
  4. Localization & Parity Check:
  • Verify 100% translation coverage for all new stat labels across all 9 supported languages (EN, DE, ES, FR, IT, JA, NL, PL, PT).
  5. Invariant & Regression Check:
  • Full regression suite (`./gradlew testDebugUnitTest`) passes with 0 failures; spatial clustering and sorting remain 100% intact.

### TST-SET-056: **Multi-Sport Candidate Clustering & Cost Minimization in Workout Editor & Tracker** [Verified]
2. `EditWorkoutViewModel` evaluates clusters against candidate sports with 0.0 penalty and selects/ranks the lowest geometric cost cluster first.
3. Suggested sport type dropdown in `EditWorkoutScreen` lists all speed-matching sports.
4. `TrackerService` assigns the lowest-cost cluster on live tracking completion.
5. Dedicated hardware sensors and user pre-selection retain strict precedence.]
- **Requirement(s)**: `REQ-SET-064`
- **Component**: **Multi-Sport Candidate Clustering & Cost Minimization in Workout Editor & Tracker**
- **Jira**: ``ATT-820``
- **Test Description**:
  1. *Unit Test `EditWorkoutViewModel.fetchClusterSuggestions` with multi-sport candidate evaluation*:
  • Create a workout with `bSportType = BSportType.UNKNOWN`, avg speed ~10 km/h (2.78 m/s). Speed matches both `RUN` (Running) and `BIKE` (custom sport `Einkaufen`, 7–15 km/h).
  • Provide two candidate clusters: Cluster A (`BIKE`, geometric score 0.25) and Cluster B (`RUN`, geometric score 0.40).
  • Verify that `fetchClusterSuggestions` applies 0.0 sport penalty to both clusters and ranks Cluster A first (cost 0.25 < 0.40).
  • Verify that when `bSportType` was initially guessed as `RUN`, candidate sports are derived from average speed so that Cluster A (`BIKE`) is NOT artificially penalized with +5.0 mismatch penalty.
  2. *Unit Test `EditWorkoutViewModel.updateSuggestedSportTypeNames` with overlapping sports*:
  • For a workout with avg speed matching both Running and `Einkaufen` where `bSportType == UNKNOWN`, verify suggested sport types dropdown includes all candidate sports across all matching base sports (`Running` and `Einkaufen`).
  3. *Unit Test `SportTypeDatabaseManager` speed query with `UNKNOWN`*:
  • Query speed at ~7 km/h (1.94 m/s) with `BSportType.UNKNOWN`. Verify both `Running` and `Einkaufen` are returned without low-speed walking fallback blocking candidate sports.
  4. *Unit Test Live Tracking Multi-Sport Resolution (`TrackerService.java` & `WorkoutClusterEngine.suggestCluster`)*:
  • End a live tracking session with speed ~10 km/h and no sensors/pre-selection.
  • Verify that cluster similarity cost is evaluated across all candidate sports before locking sport identity, and the lowest cost cluster is selected.
  5. *Invariant & Precedence Verification*:
  • Explicit user pre-selection (`RUN`): verify `BIKE` cluster receives +5.0 penalty and is rejected.
  • High-confidence cadence sensor: candidate sports are strictly `{BIKE}`.

### TST-SET-057: **Direct Navigation from Workout to Linked Workout Cluster Verification** [Verified]
2. Tapping the cluster button immediately routes to the linked cluster heatmap without triggering edit mode.
3. Unclustered workouts display neutral non-interactive text.
4. Back navigation returns reliably to the originating workout view.]
- **Requirement(s)**: `REQ-SET-058`
- **Component**: **Direct Navigation from Workout to Linked Workout Cluster Verification**
- **Jira**: ``ATT-503``
- **Test Description**:
  1. *Compose Unit Test for `WorkoutHeader`*:
  • Render `WorkoutHeader` with `clusterId = 42`, `clusterName = "Morning Run"`, and `onClusterClick` callback.
  • Verify an interactive `Button` is displayed with subtle primary tint colors, containing the `my_locations` icon and `"Morning Run"`.
  • Click the cluster button: verify `onClusterClick` receives `42L`.
  • Click outside the button on the header surface: verify `onClicked` (edit workout) is invoked without triggering `onClusterClick`.
  • Render `WorkoutHeader` with `clusterId = -1L, clusterName = null`: verify the neutral, non-clickable "Unclustered" text is displayed and clicks do not trigger `onClusterClick`.
  2. *Unit Test for Navigation Flow (`WorkoutNavigationEvents` & `WorkoutClustersViewModel`)*:
  • Trigger `WorkoutNavigationEvents.triggerCluster(42L)`: verify event is emitted on `navigateToClusterLiveData`.
  • Test `WorkoutClustersViewModel.selectClusterById(42L)`: verify target cluster is selected and its map state/workouts are loaded.
  3. *Integration & Manual Test Procedure*:
  • Open Workout History (`WorkoutSummariesTabbedFragment`).
  • Locate a workout associated with a cluster: tap the cluster button: verify direct navigation to `WorkoutClustersFragment` displaying `WorkoutClusterHeatmapScreen` for that cluster.
  • Tap top app bar back arrow or system Back button: verify navigation returns directly to Workout History.
  • Open a workout in `TrackOnMapScreen`: tap the cluster button: verify direct navigation to the cluster heatmap.

### TST-SET-058: **Workout, Route & Segment Bottom Scaffold Status Bar Boundary & Peek Verification** [Implemented]
2. Background maps render edge-to-edge under the status bar.
3. Workout popup displays both header and track map.
4. Initial popups display compact drag handles and unclipped header information.
5. All unit tests pass.]
- **Requirement(s)**: `REQ-SET-069`
- **Component**: **Workout, Route & Segment Bottom Scaffold Status Bar Boundary & Peek Verification**
- **Jira**: ``ATT-832``
- **Test Description**:
  1. *Unit / Architectural Verification*:
  • Verify that `PeriodMapScreen.kt`, `MapFragmentWithTrack.kt`, and `WorkoutClusterHeatmapScreen.kt` wrap `BottomSheetScaffold` in `BoxWithConstraints` and constrain `sheetContent` height to `maxHeight - statusBarHeight`.
  • Verify that the background map continues to draw edge-to-edge behind/underneath the system status bar.
  • Verify that `TrackOnMapScreen.kt` passes `Modifier.fillMaxWidth()` to `WorkoutHeader` so the workout popup map container is not squashed.
  • Verify that `MinimumDragHandle` retains its standard height (~24 dp) and minimal padding.
  2. *Initial Popup (Peek) Verification*:
  • Open a period map (`PeriodMapScreen`) and tap a workout: verify the initial workout popup peeks cleanly at the bottom with all key header details and map visible.
  • Open `MapFragmentWithTrack` and tap a segment or route: verify the initial peek is compact and unobtrusive.
  • Open a cluster heatmap (`WorkoutClusterHeatmapScreen`) and tap a workout: verify the initial workout peek displays properly with map visible.
  3. *Full Expansion Verification*:
  • Drag the bottom sheet in `PeriodMapScreen` all the way to the top (`SheetValue.Expanded`): verify the sheet top boundary stops cleanly below the system status bar (clock, battery, Wi-Fi).
  • Drag the bottom sheet in `MapFragmentWithTrack` all the way to the top: verify the top boundary stops below the status bar.
  • Drag the bottom sheet in `WorkoutClusterHeatmapScreen` all the way to the top: verify the top boundary stops below the status bar.
  4. *Regression Verification*:
  • Run full clean-room unit test suite (`./gradlew testDebugUnitTest`) to ensure zero regressions across all modules.
- **Verification Status Note**: *(Bucket C — retained `[Implemented]` pending real-window inset / `SheetValue.Expanded` drag verification against Android system status bar icons.)*

### TST-SET-059: **Workout Popup Direct Edit & Header Button Layout Verification** [Verified]
2. Tapping the edit button or header opens `EditWorkoutScreen` without crashing or losing map state.
3. Returning from editor updates workout details cleanly.
4. All unit tests pass.]
- **Requirement(s)**: `REQ-SET-070`
- **Component**: **Workout Popup Direct Edit & Header Button Layout Verification**
- **Jira**: ``ATT-506``
- **Test Description**:
  1. *Unit & Layout Verification*:
  • Verify `WorkoutHeader` renders `ic_table_edit` on the very right when `onEditWorkout` is provided.
  • Verify `ClusterSummaryHeader` renders `EditLocationAlt` on the left and `ic_table_edit` on the very right.
  • Verify clicking either the edit icon button or the header surface invokes `onEditWorkout(workoutId)`.
  2. *Period Map Popup Navigation Verification*:
  • Open a period map in `PeriodsFragment`.
  • Tap a workout to display the peek popup.
  • Tap the edit button on the very right: verify `EditWorkoutScreen` opens with the workout's details.
  • Change a detail (e.g. name or sport) and press back: verify view returns to `PeriodMapScreen` with the updated detail reflected.
  3. *Cluster Heatmap Popup Navigation Verification*:
  • Open a favorite track in `WorkoutClustersFragment`.
  • Tap a member workout in the heatmap to peek its details.
  • Verify the header displays the cluster transfer icon (`SwapHoriz`) on the left and the edit icon (`ic_table_edit`) on the very right.
  • Tap the edit button: verify `EditWorkoutScreen` opens.
  • Press back: verify view returns to `WorkoutClusterHeatmapScreen`.
  4. *Regression Suite*:
  • Execute `./gradlew testDebugUnitTest` to ensure zero regressions across all modules.

### TST-SET-060: **Workout Card Click-to-Map Navigation & Dedicated Header Edit Action Verification** [Verified]
2. Tapping the dedicated edit button in the header (before the 3-dots) reliably opens `EditWorkoutScreen`.
3. Tapping the edit button within `TrackOnMapScreen` immediately opens `EditWorkoutScreen`, returning to `TrackOnMapScreen` upon dismissal.
4. Cluster button, export status badge, and export menu function independently without navigation conflicts.
5. All unit tests pass.]
- **Requirement(s)**: `REQ-SET-071`
- **Component**: **Workout Card Click-to-Map Navigation & Dedicated Header Edit Action Verification**
- **Jira**: ``ATT-850``
- **Test Description**:
  1. *Unit & Interaction Verification (`WorkoutSummaryTest.kt` / `WorkoutHeaderTest.kt`)*:
  • Verify that clicking `WorkoutHeader` surface invokes `onClicked` (routed to `onMapClick`).
  • Verify that clicking `WorkoutDescription`, `WorkoutDetails`, and `WorkoutExtrema` invokes `onMapClick`.
  • Verify that `WorkoutHeader` renders `ic_table_edit` before the `more_vert` export button (after `actions()` and before `more_vert`).
  • Verify that clicking `ic_table_edit` invokes `onEditWorkout`.
  • Verify that clicking the cluster button invokes `onClusterClick` and does not invoke `onMapClick` or `onEditWorkout`.
  • Verify that tapping the 3-dots export button opens the dropdown and does not invoke `onMapClick`.
  2. *Integration / Manual Workflow in Workout History & Map View*:
  • Open Workout History (`WorkoutSummariesTabbedFragment` / `WorkoutSummariesListFragment`).
  • Tap the header, description, metrics, or extrema of a workout card: verify immediate navigation to `TrackOnMapScreen`.
  • In `TrackOnMapScreen`, tap the edit button (`ic_table_edit`): verify immediate opening of `EditWorkoutScreen` without requiring back navigation.
  • Save or dismiss `EditWorkoutScreen`: verify return directly to `TrackOnMapScreen`.
  • Press back from `TrackOnMapScreen`: verify return to the workout list at the same scroll position.
  • Tap the edit button (`ic_table_edit`) in the workout list header (situated before the 3-dots button): verify immediate opening of `EditWorkoutScreen`.
  • Save or dismiss the editor: verify return to the workout list with updated details reflected.
  • Tap the cluster button on a clustered workout: verify direct navigation to the cluster heatmap.
  • Tap the export status badge: verify the export details dialog opens.
  3. *Regression Suite*:
  • Execute `./gradlew testDebugUnitTest` to ensure zero regressions across all modules.

### TST-SET-061: **Edit Workout New Cluster Creation & Name Synchronization Verification** [Verified]
2. Creating a new cluster updates database and UI with the new cluster identity (`forceIdentity = true`).
3. Non-customized workout names automatically adopt the new cluster name; manually typed custom workout names are safely preserved.]
- **Requirement(s)**: `REQ-SET-072`
- **Component**: **Edit Workout New Cluster Creation & Name Synchronization Verification**
- **Jira**: ``ATT-956``
- **Test Description**:
  1. *Unit Test `EditWorkoutClusterDialog` Pre-fill*:
  • Open dialog with `initialWorkoutName = "Hausrunde #3"`: verify text field initializes with `"Hausrunde"` (`stripHitCount`).
  • Open dialog with unnumbered name `initialWorkoutName = "Hausrunde"`: verify text field initializes with `"Hausrunde"`.
  2. *Unit Test `WorkoutClusterEngine.createNewClusterFromWorkout` Database Persistence*:
  • Create new cluster from workout with existing `WORKOUT_NAME = "Hausrunde #3"` and custom cluster name `"Neue Runde"`.
  • Verify `assignClusterToWorkout` updates `WorkoutSummaries.WORKOUT_NAME` in SQLite to `"Neue Runde"` (with `forceIdentity = true`).
  • Verify `CLUSTER_ID` is set to the new cluster ID.
  • Verify old cluster hit count is decremented and new cluster hit count is 1.
  3. *Unit Test `EditWorkoutViewModel.createNewCluster` UI State & Smart Name Preservation*:
  • Unmodified name case: User did not edit workout name field. Call `createNewCluster("Neue Runde")`. Verify `_workoutData` immediately reflects `clusterName = "Neue Runde"` and `workoutName = "Neue Runde"`.
  • User-typed custom name case: User typed `"Mein Spezieller Lauf"` (`updateWorkoutName("Mein Spezieller Lauf")`). Call `createNewCluster("Neue Runde")`. Verify `_workoutData` reflects `clusterName = "Neue Runde"` while preserving `workoutName = "Mein Spezieller Lauf"`.
  • Repository reload resistance: Simulate background emission from `repository.workout` after reload. Verify user-typed name is not overwritten by the database value.
  4. *Integration / Manual Verification*:
  • Open `EditWorkoutScreen` for workout matched to `"Hausrunde #3"`.
  • Tap Route field to open `EditWorkoutClusterDialog`. Verify new route text field pre-fills `"Hausrunde"`.
  • Enter `"Neue Abendrunde"`, tap OK.
  • Verify Edit Workout screen immediately displays `"Neue Abendrunde"` in both Workout Name and Selected Route fields.
- **Verification Evidence**: Verified (`ATT-8328`, SHA `2a149eca`, Gate 4 `119/119` PASS across 22 suites): `EditWorkoutNewClusterSyncTest.kt` (`EditWorkoutNewClusterSyncTest.editWorkoutClusterDialog_prefillsStrippedHitCountOnCreateNew_TST_SET_061`, `EditWorkoutNewClusterSyncTest.createNewClusterFromWorkout_updatesSqliteNameAndDecrementsOldCluster_TST_SET_061`)

### TST-SET-062: **Advanced Tuning Preferences Clamping, Persistence, and Reset Verification** [Verified]
2. Atomic factory reset restores all defaults.
3. Active services adopt parameters reactively.
4. All unit and contract tests pass.]
- **Requirement(s)**: `REQ-SET-073`
- **Component**: **Advanced Tuning Preferences Clamping, Persistence, and Reset Verification**
- **Jira**: ``ATT-1304``
- **Test Description**:
  1. *Unit Tests (`TuningPreferencesDataStoreTest.kt`)*:
  • Default values: Verify initial flow emissions match factory defaults.
  • Clamping verification: Pass values outside bounds (e.g., brightness -0.1f and 1.5f, min speed 0.0f, accuracy 5m); verify values are clamped safely to specified min/max bounds.
  • Consistency invariants: Verify full dimming factor cannot exceed medium dimming factor, and flat slope cutoff cannot exceed steep slope cutoff.
  • Reset-to-defaults verification: Mutate multiple preferences, invoke `resetToDefaults()`, and verify all values revert to factory defaults.
  2. *Component & Dynamic Reactive Tests (`BatterySaverDynamicTuningTest.kt`)*:
  • Initialize `BatterySaverController` / `BatterySaverStateMachine`.
  • Update battery saver tuning parameters dynamically.
  • Verify state machine uses updated thresholds and dimming factors immediately without controller re-instantiation.
  3. *Navigation & UI Verification*:
  • Verify drawer item `R.id.drawer_advanced_tuning` triggers `SettingsBottomSheetType.ADVANCED_TUNING`.
  • Verify link in `DisplaySettingsDialog` triggers `SettingsBottomSheetType.ADVANCED_TUNING`.
  • Verify Reset to Defaults button resets UI state and triggers toast/feedback.
  4. *Regression Verification*:
  • Full test suite execution `./gradlew testDebugUnitTest`.

### TST-SET-063: **Expert Settings Navigation Drawer Grouping, Localization and 50m GPS Default Verification** [Verified]
2. General settings no longer includes advanced tuning.
3. Default GPS accuracy tolerance defaults to 50.0m across data structures and resets.
4. 100% 9-language translation parity.
5. All unit tests pass.]
- **Requirement(s)**: `REQ-SET-074`
- **Component**: **Expert Settings Navigation Drawer Grouping, Localization and 50m GPS Default Verification**
- **Jira**: ``ATT-1646``
- **Test Description**:
  1. *Unit Tests (`AppNavigationDrawerTest.kt`)*:
  • Drawer group partitioning: Verify that `groups.last()` has `titleRes == R.string.drawer__expert_settings` and contains `R.id.drawer_advanced_tuning`.
  • General settings isolation: Verify `drawer__settings` group no longer contains `R.id.drawer_advanced_tuning`.
  2. *Defaults Verification (`TuningConfigTest.kt`)*:
  • Assert `TuningPreferencesDefaults.GPS_ACCURACY_THRESHOLD_M == 50.0f`.
  • Assert `TuningConfig().gpsAccuracyThresholdMeters == 50.0f`.
  3. *Localization Audit*:
  • Verify string resources `drawer__expert_settings`, `advanced_tuning_title`, and `advanced_tuning_open` exist in all 9 supported locales (values, values-de, values-es, values-fr, values-it, values-ja, values-nl, values-pl, values-pt).
  4. *Regression Verification*:
  • Full test suite execution `./gradlew testDebugUnitTest`.

### TST-SET-064: **Favorite Route Icon Disambiguation for Clusters in Workout Header and Editor** [Verified]
- **Requirement(s)**: `REQ-SET-058`, `REQ-SET-059`
- **Component**: **Favorite Route Icon Disambiguation for Clusters in Workout Header and Editor**
- **Jira**: ``ATT-3277``
- **Test Description**:
  1. *Visual & AST Contract Unit Test (`WorkoutClusterIconVisualContractTest.kt`)*:
  • Verify `WorkoutHeader.kt` renders `R.drawable.ic_favorite_route` for both clustered (`clusterId > 0`) and unclustered cluster rows.
  • Verify `WorkoutHeader.kt` preserves `R.drawable.my_locations` for start and destination known location rows (`startLoc`, `endLoc`).
  • Verify `EditWorkoutScreen.kt` renders `R.drawable.ic_favorite_route` as the leading icon of the selected route input field.
  • Verify vector drawable `ic_favorite_route.xml` exists and contains valid path data.
  2. *Regression Suite*:
  • Execute clean-room unit test suite `./gradlew testDebugUnitTest` to ensure zero regressions across all modules.

### TST-SET-076: **Red Cluster Recalculate Button and Direct Advanced Settings Deep-Link from Favorite Routes Verification** [Verified]
- **Requirement(s)**: `REQ-SET-076`
- **Component**: **Red Cluster Recalculate Button and Direct Advanced Settings Deep-Link from Favorite Routes Verification**
- **Jira**: ``ATT-3492``
- **Test Description**:
  1. *Recalculate Button Styling Contract Tests (`ClusterTuningContractTest.kt`)*:
  • Verify `ClusterTuningSection.kt` styles the recalculate button with `ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError, disabledContainerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.6f), disabledContentColor = MaterialTheme.colorScheme.onError)`.
  • Verify `CircularProgressIndicator` uses `color = MaterialTheme.colorScheme.onError` ensuring WCAG 2.1 contrast.
  2. *9-Language Localization Audit (`ClusterInfoDialogTest.kt`)*:
  • Verify string resource `cluster_info_open_advanced_tuning` exists and is non-blank across EN, DE, ES, FR, IT, JA, NL, PL, PT.
  • Verify updated `cluster_info_tuning_desc` mentions Advanced Settings across all 9 locales.
  3. *Deep-Link Action & Navigation Contract Tests (`ClusterInfoDialogContractTest.kt`)*:
  • Verify `ClusterInfoDialog.kt` requires parameter `onNavigateToAdvancedSettings: () -> Unit`.
  • Verify Section 4 renders a button referencing `R.string.cluster_info_open_advanced_tuning` with icon `Icons.Default.Tune`.
  • Verify tapping button triggers `onDismissRequest()` strictly before `onNavigateToAdvancedSettings()`.
  • Verify zero Activity casts inside `ClusterInfoDialog.kt`.
  • Verify `WorkoutClustersTabsScreen.kt` and `WorkoutClustersScreen.kt` forward `onNavigateToAdvancedSettings`.
  4. *Modularity Limit Audit (`AdvancedTuningModularityTest.kt`)*:
  • Verify all files in `ui.settings.tuning` remain strictly `< 400` lines.
  5. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with zero regressions.

### TST-SET-077: **Standardized Route Clustering Form Controls & Configurable Score Threshold Verification** [Verified]
- **Requirement(s)**: `REQ-SET-075`, `REQ-SET-077`
- **Component**: **Standardized Route Clustering Form Controls via TuningFormControls Verification**
- **Jira**: `ATT-3365`, `ATT-5559`
- **Test Description**:
  1. *Unit & Parameter Tests (`TuningFormControlsTest.kt`)*:
  • Verify `TuningSliderItem` accepts optional `enabled: Boolean` and `onValueChangeFinished: (() -> Unit)?`.
  • Verify `TuningToggleItem` accepts optional `enabled: Boolean`.
  • Verify binary compatibility with default argument callers across all 15+ existing call sites.
  • Negative Interaction / Callback Gating: Verify that when `enabled = false`, `Slider` and `Switch` enter disabled mode, preventing `onValueChange` and `onValueChangeFinished` from mutating state.
  • Disabled Alpha Verification: Verify `TTAlpha.Disabled` is applied to title, value text, and toggle labels when `enabled = false`.
  2. *Architectural Contract & Consumer Tests (`ClusterTuningContractTest.kt`)*:
  • Verify `ClusterTuningContent.kt` delegates all toggles to `TuningToggleItem` and sliders to `TuningSliderItem`.
  • Verify legacy `TuningSlider` composable is annotated with `@Deprecated` containing fully-qualified `ReplaceWith(..., imports = ["com.atrainingtracker.trainingtracker.ui.settings.tuning.TuningSliderItem"])`.
  • Verify `ClusterTuningSection.kt` and `ClusterTuningScreen.kt` propagate `enabled` state consistently down to all form controls.
  • Verify altitude tolerance slider gating logic: asserts `enabled = enabled && useAltitudePosForClustering` across all state combinations.
  3. *Visual & Existing Tuning Section Regression Tests (`AdvancedTuningVisualContractTest.kt`)*:
  • Verify all existing callers in `NavigationSection`, `SensorsGpsFilterSection`, `CockpitTypographySection`, etc., maintain intact rendering and behavior without modification.
  4. *Cluster Score Threshold Persistence, Evaluation & UI Coupling Tests (`WorkoutClusterEngineTest.kt`, `TuningPreferencesDataStoreTest.kt`, `ClusterTuningContractTest.kt`, `TranslationParityTest.kt`)*:
  • Verify `TuningPreferencesDefaults.CLUSTER_SCORE_THRESHOLD` defaults to `1.2f` with range `0.5f..2.5f` and step `0.1f`.
  • Verify `TuningPreferencesDataStore` persists `cluster_score_threshold` to DataStore and `TrainingApplication.setClusterScoreThreshold` synchronously on save.
  • Verify `TrainingApplication.getClusterScoreThreshold()` returns default `1.2f` when unset and reads persisted float.
  • Verify `WorkoutClusterEngine.suggestCluster` accepts candidate clusters when similarity score `< scoreThreshold` (e.g. score `1.15` matches when threshold is `1.2`, rejected when threshold is `1.0`).
  • Verify defensive fallback `runCatching { TrainingApplication.getClusterScoreThreshold().toDouble() }.getOrDefault(1.2)` executes safely under un-stubbed static mocks.
  • Verify `ClusterTuningContent.kt` incorporates score threshold into derived master sensitivity average and renders a dedicated `TuningSliderItem`.
  • Verify 100% 9-language translation parity for `cluster_tuning_score_threshold_label` and `cluster_tuning_score_threshold_helper` across EN, DE, ES, FR, IT, JA, NL, PL, PT.
  5. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with zero regressions.

### TST-PRI-002: **Contextual Just-in-Time Permission Flow, Zero-Friction Cold Start, Background Location Escalation & Battery Optimization Verification** [Verified]
- **Requirement(s)**: `REQ-PRI-003`, `REQ-PRI-001`
- **Component**: **Contextual Just-in-Time Permission Flow, Zero-Friction Cold Start, Background Location Escalation & Battery Optimization Verification**
- **Jira**: ``ATT-2075``
- **Test Description**:
  1. *Cold-Start Freedom Test (`MainActivityStartupPermissionContractTest.kt`)*:
  • Verify `MainActivityWithNavigation.onCreate()` does not invoke blocking `AlertDialog.show()` for permissions or battery optimizations on cold start.
  • Verify GPS provider access remains shielded per `REQ-STB-008`.
  2. *Progressive JIT State Machine & Escalation Tests (`ControlTrackingPermissionTest.kt`)*:
  • Verify tapping Start when foreground location is missing triggers foreground rationale sheet.
  • Verify granting foreground location on API 29+ transitions to background location rationale sheet when `ACCESS_BACKGROUND_LOCATION` is missing.
  • Verify when location permissions are granted but battery optimization is active, system transitions to battery optimization rationale sheet.
  • Verify `ControlTrackingButton` displays amber warning badge when location permissions are missing.
  • Verify `ON_RESUME` re-evaluates all permission states reactively.
  3. *Fail-Safe OEM Intent Contract Test (`ControlTrackingPermissionTest.kt` / `PermissionRationaleSheetContractTest.kt`)*:
  • Verify battery optimization intent launch handles `ActivityNotFoundException` and `SecurityException` with fallback to settings without crashing.
  4. *9-Language Localization Audit*:
  • Verify background location and battery optimization titles and descriptions exist across all 9 locales (EN, DE, ES, FR, IT, JA, NL, PL, PT).
  5. *Clean-Room Full Suite Regression Execution*:
  • Run `./gradlew testDebugUnitTest` asserting 100% pass rate.

### TST-PRI-003: **Precise Location Gating and Dynamic Sensor Device Instantiation Verification** [Verified]
- **Requirement(s)**: `REQ-PRI-004`
- **Component**: **Precise Location Gating and Dynamic Sensor Device Instantiation Verification**
- **Jira**: ``ATT-2357``
- **Test Description**:
  1. *Precise Location Gating Unit Tests (`ControlTrackingPermissionTest.kt`)*:
  • Verify that when `ACCESS_COARSE_LOCATION` is granted but `ACCESS_FINE_LOCATION` is denied, `checkHasLocation()` returns `false`.
  • Verify that tapping Start in coarse-only state transitions to `RationaleStep.FOREGROUND` with precise location explanation.
  • Verify that `ControlTrackingButton` displays permission warning badge in coarse-only state.
  • Verify that granting `ACCESS_FINE_LOCATION` satisfies the location check (`checkHasLocation() == true`) and clears the warning badge.
  2. *Dynamic Device Instantiation Unit Tests (`DeviceManagerLocationInitTest.kt`)*:
  • Verify that `DeviceManager.checkOrInitializeLocationDevices()` creates `SpeedAndLocationDevice_GPS` when `ACCESS_FINE_LOCATION` is granted during an active service session.
  • Verify that `checkOrInitializeLocationDevices()` is idempotent and does not recreate or duplicate an already active device.
  • Verify `BANALService.checkOrInitializeLocationDevices()` delegates properly.
  3. *9-Language Localization Audit (`PermissionLocalizationTest.kt`)*:
  • Verify `permission_rationale_precise_location_explanation` exists and is non-empty across all 9 locales (EN, DE, ES, FR, IT, JA, NL, PL, PT).
  4. *Clean-Room Full Suite Regression Execution*:
  • Run `./gradlew testDebugUnitTest` asserting 100% pass rate.

### TST-PRI-004: **Proactive Background Energy Warning Banner, Start Badge Integration & Cascade Decoupling Verification** [Verified]
- **Requirement(s)**: `REQ-PRI-005`
- **Component**: **Proactive Background Energy Warning Banner, Start Badge Integration & Cascade Decoupling Verification**
- **Jira**: ``ATT-2621``
- **Test Description**:
  1. *Proactive Warning Banner Contract Tests (`BatteryOptimizationWarningBannerTest.kt`)*:
  • Verify `BatteryOptimizationWarningBanner` renders when `isIgnoringBatteryOptimizations == false` and `isBannerDismissed == false`.
  • Verify tapping banner action button dispatches `launchBatteryOptimizationIntent`.
  • Verify tapping dismiss button sets `isBannerDismissed = true` and collapses banner.
  • Verify banner does not render when `isIgnoringBatteryOptimizations == true`.
  2. *ControlTrackingScreen State & Start Badge Unit Tests (`ControlTrackingScreenBatteryTest.kt`)*:
  • Verify `hasPermissionWarning` evaluates to true when location is granted but battery optimization is missing.
  • Verify `proceedAfterPermissions` advances to `RationaleStep.BATTERY_OPTIMIZATION` when background location is skipped.
  • Verify `ON_RESUME` re-evaluates `checkIsIgnoringBatteryOptimizations` and triggers `onStart()` if `pendingStartAfterBatteryExemption == true`.
  3. *9-Language Localization Parity Audit (`BatteryOptimizationBannerLocalizationTest.kt`)*:
  • Verify `battery_optimization_warning_banner_title`, `battery_optimization_warning_banner_desc`, and `battery_optimization_action_fix` exist, are non-empty, and contain zero unescaped format tokens across `values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`.
  4. *Full Clean-Room Regression Suite*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with 0 regressions.

### TST-SET-078: **Workout Cluster Historical Effort Trend Chart, Dual-Metric Inversion, Outlier-Resilient Y-Scaling, X/Y Axis Scale Labels, and 9-Language Parity Verification Suite** [Verified]
- **Requirement(s)**: `REQ-SET-078`
- **Component**: **Workout Cluster Historical Effort Trend Chart, Dual-Metric Inversion, Outlier-Resilient Y-Scaling, X/Y Axis Scale Labels, and 9-Language Parity Verification Suite**
- **Jira**: ``ATT-4067`, `ATT-4287``
- **Test Description**:
  1. *Chronological Sorting & Cleansing Test (`TST-SET-078.1`, `WorkoutClusterTrendMathTest.kt`)*:
  • Provide member workouts in arbitrary order with positive and non-positive durations.
  • Verify output points are sorted ascending by `startTimeS`, non-positive durations and velocities are filtered out, and normalized coordinates lie within $[0, 1]$.
  2. *Faster-Is-Higher Coordinate Inversion Test (`TST-SET-078.2`, `WorkoutClusterTrendMathTest.kt`)*:
  • In Duration mode, verify faster time produces higher normalized $Y$ (`normY(fast) > normY(slow)`).
  • In Pace mode, verify faster pace produces higher normalized $Y$.
  • In Speed mode, verify higher speed produces higher normalized $Y$.
  3. *Boundary & Edge Case Resilience Test (`TST-SET-078.3`, `WorkoutClusterTrendMathTest.kt`)*:
  • Test with $N = 0$, $N = 1$, and identical durations (min == max).
  • Verify safe handling without division-by-zero, NaN, or exceptions; identical values clamp safely to $Y = 0.5f$.
  4. *PR Determination & Direct Touch Selection Test (`TST-SET-078.4`, `WorkoutClusterTrendMathTest.kt`)*:
  • Verify PR index corresponds to minimum duration effort.
  • Verify nearest-point touch detection identifies points within radius $R$ and returns null beyond $R$.
  • Verify point touch hit triggers `onSelectWorkout(workoutId)` directly without requiring or rendering a bottom callout card.
  5. *9-Language Localization & Format Audit (`TST-SET-078.5`, `WorkoutClusterTrendLocalizationTest.kt`)*:
  • Verify all 6 trend keys (`cluster_trend_title`, `cluster_trend_duration`, `cluster_trend_speed`, `cluster_trend_pace`, `cluster_trend_pr`, `cluster_trend_avg`) exist across all 9 application locales.
  6. *Clean-Room Regression Suite Execution (`TST-SET-078.6`)*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% pass rate with zero regressions.
  7. *Tukey IQR Outlier Fencing & Resilient Scaling Test (`TST-SET-078.7`, `WorkoutClusterTrendMathTest.kt`)*:
  • Provide cluster workout series ($N \ge 4$) containing an extreme duration outlier (e.g. 115 min vs normal 32–38 min).
  • Assert that IQR is positive and scale domain clamps to $[Q_1 - 1.5 \times \text{IQR}, Q_3 + 1.5 \times \text{IQR}]$.
  • Assert that normal workouts span the full vertical dynamic range ($[0f, 1f]$) and the outlier is clamped to $\text{normY} = 0f$ with `isClampedOutlier = true`.
  • Verify safe fallback to min/max when $N < 4$ or $\text{IQR} == 0$.
  8. *X-Axis and Y-Axis Scale Bounds Calculation & Formatting Test (`TST-SET-078.8`, `WorkoutClusterTrendMathTest.kt`)*:
  • Verify `yAxisTopLabel` matches the formatted value of the fastest scale boundary across Duration, Speed, and Pace modes.
  • Verify `yAxisBottomLabel` matches the formatted value of the slowest scale boundary across all modes.
  • Verify `xAxisStartLabel` and `xAxisEndLabel` match formatted short dates of earliest and latest member workouts.
  9. *Density-Adaptive Point Sizing & Outlier Representation Contract Test (`TST-SET-078.9`, `WorkoutClusterTrendLayoutAndVisibilityTest.kt`)*:
  • Verify that when $N > 20$, standard non-PR point radius evaluates to $2\text{ dp}$ (vs. standard $4\text{ dp}$ for $N \le 20$).
  • Verify PR point retains gold $6\text{ dp}$ and selected point retains $5\text{ dp}$ + $9\text{ dp}$ ring.
  • Verify clamped outlier points are rendered with distinct visual cue.
  • Verify removal of redundant selected-workout bottom callout card banner from `WorkoutClusterTrendChartCard`.
  • Verify `WorkoutClusterTrendChartCard` is hosted within Tab 2 (*Leistungsverlauf*) of `MapDetailLayout.analyticsContent`.
  10. *9-Language Localization Parity for Outlier String (`TST-SET-078.10`, `WorkoutClusterTrendLocalizationTest.kt` / `TranslationParityTest.kt`)*:
  • Verify string key `cluster_trend_outlier` exists and is non-empty across all 9 application locales (`values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`).
  11. *Clean-Room Regression Suite Execution (`TST-SET-078.11`)*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% pass rate with zero regressions.

### TST-PRI-005: **Decoupled Multi-Permission Cascade, Contextual Bluetooth & Notification Gating, Sensor Discovery Permission Handling, and Legacy Code Retirement Verification Suite** [Verified]
- **Requirement(s)**: `REQ-PRI-006`
- **Component**: **Decoupled Multi-Permission Cascade, Contextual Bluetooth & Notification Gating, Sensor Discovery Permission Handling, and Legacy Code Retirement Verification Suite**
- **Jira**: ``ATT-3998`, `ATT-4449``
- **Test Description**:
  1. *Decoupled Multi-Permission Cascade Unit Tests (`TST-PRI-005.1`, `ControlTrackingPermissionTest.kt`)*:
  • Location missing (`checkHasLocation() == false`): verify transition to `RationaleStep.FOREGROUND`.
  • Location granted, notification missing on API 33+ (`checkHasLocation() == true`, `checkHasNotification() == false`): verify transition to `RationaleStep.NOTIFICATION` (or request `POST_NOTIFICATIONS`).
  • Location granted, paired BLE sensors, Bluetooth connect missing on API 31+ (`checkHasLocation() == true`, `hasPairedBluetoothSensors == true`, `checkHasBluetoothConnect() == false`): verify transition to `RationaleStep.BLUETOOTH` (or request `BLUETOOTH_CONNECT`).
  • Optional dismissal ("Not Now"): verify dismissing notification or Bluetooth rationale advances gracefully to background location / battery optimization / tracking start without infinite loops.
  • All granted: verify `onStart()` is invoked cleanly.
  2. *Sensor Discovery Just-in-Time Permission Gating Tests (`TST-PRI-005.2`, `ControlTrackingScreenSensorSearchTest.kt` / `DevicesTabbedPermissionContractTest.kt`)*:
  • On Android 12+, calling sensor search in `ControlTrackingScreen` when `BLUETOOTH_SCAN` is missing triggers permission request and withholds search broadcast until granted.
  • On Android 12+, opening `DevicesTabbedScreen` or initiating BLE pairing scan when `BLUETOOTH_SCAN` is missing requests permission; if denied, displays `bluetooth_permission_scan_required` with `grant_permission` action rather than running a silent dead search.
  3. *Legacy Permission Method Retirement & Contract Safety Tests (`TST-PRI-005.3`, `MainActivityStartupPermissionContractTest.kt`)*:
  • Verify `MainActivityWithNavigation` no longer declares `getPermissions()` or `showBackgroundLocationDialog()`.
  • Verify `onCreate` does not invoke blocking `AlertDialog` on cold start (`REQ-PRI-003`).
  • Verify GPS provider access remains shielded per `REQ-STB-008`.
  4. *9-Language Localization & Format Specifier Audit (`TST-PRI-005.4`, `PermissionLocalizationParityTest.kt`)*:
  • Verify string resources `bluetooth_permission_scan_required`, `grant_permission`, `permission_rationale_notification_subtitle`, and `permission_rationale_bluetooth_subtitle` exist with 100% translation parity and zero missing entries across EN, DE, ES, FR, IT, JA, NL, PL, PT.
  5. *Clean-Room Full Regression Suite Execution (`TST-PRI-005.5`)*:
  • Execute `./gradlew testDebugUnitTest` verifying 100% pass rate with zero regressions across the full test suite.

### TST-SET-079: **Non-Blocking DataStore Architecture, Reactive In-Memory StateFlow Caching, Pure Parameter Passing & Hot Path Decoupling Verification Suite** [Verified]
- **Requirement(s)**: `REQ-SET-079`
- **Component**: **Non-Blocking DataStore Architecture, Reactive In-Memory StateFlow Caching, Pure Parameter Passing & Hot Path Decoupling Verification Suite**
- **Jira**: ``ATT-3999``
- **Test Description**:
  1. *In-Memory Reactive Caching & Non-Blocking Synchronous Query Contract Unit Tests (`TST-SET-079.1`, `SettingsDataStoreJavaHelperNonBlockingTest.kt`)*:
  • Verify `TuningPreferencesDataStore.getInstance(context).tuningConfigStateFlow.value` updates immediately when preferences change.
  • Verify `SettingsDataStoreJavaHelper.getZoneMax`, `getTuningConfig`, `getFtp`, `getAthleteWeightKg`, `getCpWatts`, `getWPrimeKj`, and `getAutoPauseConfig` return expected values synchronously without parking caller threads.
  • Verify `setAthleteWeightKg` persists asynchronously without blocking caller thread.
  • Verify that mutating settings through `TuningPreferencesDataStore` or `SettingsDataStore` propagates immediately to the in-memory cache without application restart.
  2. *Pure BatterySaverModel Zone Index Calculation Unit Tests (`TST-SET-079.2`, `BatterySaverModelPureTest.kt`)*:
  • Verify `calculateZoneIndex(value, cutoffs)` returns zone 1..5 for HR cutoffs and 1..7 for Power cutoffs across edge cases (below Z1, boundary equality, intermediate zones, above ceiling, negative/zero values).
  • Verify backward-compatible overload `calculateZoneIndex(context, zoneType, value)` delegates to non-blocking helper.
  3. *Hot Path UI & Sensor Thread Decoupling Architecture Contract Tests (`TST-SET-079.3`, `DataStoreHotPathContractTest.kt`)*:
  • Verify `TrackingViewModel` computes `calculateZoneColor` and `calculateZoneGaugeState` using in-memory state without calling `runBlocking`.
  • Verify `SpeedAndLocationDevice` and `VerticalSpeedAndSlopeDevice` access thresholds via non-blocking helper without thread parking.
  • Verify `TelemetryZoneMath.loadHeartRateThresholds` and `loadPowerThresholds` execute non-blocking memory reads.
  • Verify `mockkStatic(SettingsDataStoreJavaHelper::class)` continues to pass in `TelemetryZoneMathTest.kt`.
  • Verify `SettingsDataStore.kt` contains zero `runBlocking` calls and `getSummary` is removed.
  • Verify `LocationNameResolver.resolveLocationNameBlocking` has `@WorkerThread` annotation.
  4. *Clean-Room Full Suite Regression Execution (`TST-SET-079.4`)*:
  • Verify `./gradlew testDebugUnitTest` runs with 100% pass rate across the full project test suite.

### TST-PRI-006: **Abortable First-Install Permission Onboarding Flow, DataStore Persistence, Upgrade Auto-Skip & 9-Language Localization Verification Suite** [Verified]
- **Requirement(s)**: `REQ-PRI-007`
- **Component**: **Abortable First-Install Permission Onboarding Flow, DataStore Persistence, Upgrade Auto-Skip & 9-Language Localization Verification Suite**
- **Jira**: ``ATT-4455``
- **Test Description**:
  1. *DataStore Preference Flow & Persistence Unit Tests (`TST-PRI-006.1`, `TuningPreferencesDataStoreOnboardingTest.kt`)*:
  • Verify default value of `hasCompletedPermissionOnboardingFlow` is `false` (`DEFAULT_HAS_COMPLETED_PERMISSION_ONBOARDING`).
  • Verify calling `setHasCompletedPermissionOnboarding(true)` persists `true` and updates `hasCompletedPermissionOnboardingFlow`.
  • Verify calling `setHasCompletedPermissionOnboarding(false)` resets flag to `false`.
  2. *Host App Launch & Upgrade Auto-Skip Integration Tests (`TST-PRI-006.2`, `PermissionOnboardingLaunchContractTest.kt`)*:
  • Test clean-install scenario: when `hasCompletedPermissionOnboardingFlow` is `false` and permissions are missing, verify `PermissionOnboardingSheet` is scheduled for display.
  • Test upgraded-install scenario: when `hasCompletedPermissionOnboardingFlow` is `false` but all permissions (`ACCESS_FINE_LOCATION`, Bluetooth on API 31+, Notifications on API 33+, Battery optimization) are already granted, verify `checkAreAllPermissionsGranted` returns `true`, `setHasCompletedPermissionOnboarding(true)` is invoked immediately, and onboarding UI is suppressed.
  • Test returning user scenario: when `hasCompletedPermissionOnboardingFlow` is `true`, verify onboarding UI is bypassed on cold start (`REQ-PRI-003`).
  3. *PermissionOnboardingSheet Navigation, Step Sequencing & Abortability Unit Tests (`TST-PRI-006.3`, `PermissionOnboardingSheetContractTest.kt`)*:
  • Step Progression: verify Step 1 (Location) advances to Step 2 (Bluetooth on API 31+), Step 3 (Notifications on API 33+), and Step 4 (Battery Optimization).
  • Step Skipping: verify clicking "Skip this step" advances to the subsequent step without launching permission requests.
  • Total Abortability: verify clicking "Skip Setup" or dismissing the sheet at any step invokes `onFinish` / `onDismiss` with `true`, marking onboarding complete.
  • UI Constraints: verify `PermissionOnboardingSheet.kt` file length is strictly `< 400` lines.
  4. *Preservation of Just-in-Time Fallback Invariants (`TST-PRI-006.4`, `ControlTrackingPermissionTest.kt` / `DevicesTabbedPermissionContractTest.kt`)*:
  • Verify that an athlete who skipped onboarding steps still receives contextual JIT permission prompts when tapping Start on `ControlTrackingScreen` or scanning sensors on `DevicesTabbedScreen` (`REQ-PRI-006`).
  5. *9-Language Localization & Positional Specifier Parity Audit (`TST-PRI-006.5`, `TranslationParityTest.kt`)*:
  • Verify all 15 onboarding string keys exist across all 9 canonical locales (`values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`).
  • Verify `permission_onboarding_step_format` contains matching indexed specifiers (`%1$d`, `%2$d`).
  • Verify zero literal `"+ "` prefixes.
  6. *Clean-Room Full Suite Regression Execution (`TST-PRI-006.6`)*:
  • Execute `./gradlew testDebugUnitTest` verifying 100% pass rate across the full project test suite.

---

### TST-SET-080: **Dedicated Auto-Pause Settings Cockpit & Navigation Drawer Entry Verification Suite** [Verified]
- **Requirement(s)**: `REQ-SET-080`
- **Component**: **Dedicated Auto-Pause Settings Cockpit & Navigation Drawer Entry Verification Suite**
- **Jira**: `ATT-4836`, `ATT-4924`, `ATT-5820`
- **Test Description**:
  1. *Navigation Drawer & Route Resolution Contract Test (`TST-SET-080.1`, `AutoPauseNavigationTest.kt`)*:
  • Verify `NavRoutes.toRoute(R.id.drawer_auto_pause)` returns `NavRoutes.AUTO_PAUSE`.
  • Verify `NavRoutes.toDrawerItemId(NavRoutes.AUTO_PAUSE)` returns `R.id.drawer_auto_pause`.
  • Verify `AppNavigationDrawer.kt` includes `DrawerItemConfig(R.id.drawer_auto_pause, R.drawable.ic_pause_circle, R.string.auto_pause_title)` in the Settings group directly below `drawer_sport_types`.
  2. *AutoPauseViewModel State & Persistence Contract Test (`TST-SET-080.2`, `AutoPauseViewModelTest.kt`)*:
  • Verify `AutoPauseViewModel` exposes reactive `tuningConfig: StateFlow<TuningConfig>`.
  • Verify updating bike, run, and other sport toggles and sliders persists values to `TuningPreferencesDataStore`.
  • Verify resetting preferences for a sport restores default thresholds and debounces.
  3. *Single Canonical Configuration Surface Retirement Contract Test (`TST-SET-080.3`, `AutoPauseRetirementContractTest.kt`)*:
  • Verify `SensorsGpsFilterSection.kt` no longer binds `autoPause*` parameters.
  • Verify `AdvancedTuningScreen.kt` and `AdvancedTuningDialog.kt` no longer render auto-pause toggles or sliders.
  4. *Fixed Header & Global Card UI Contract Test (`TST-SET-080.4`, `AutoPauseTabsScreenContractTest.kt`)*:
  • Verify `AutoPauseTabsScreen.kt` source contract: zero references to `CollapsingAppBarNestedScrollConnection` or `appBarOffset`.
  • Verify single global `startInAutoPauseEnabled` card placed above the tab row.
  • Verify main switch uses `tuning_auto_pause_enable_title`.
  5. *9-Language Localization Parity (`TST-SET-080.5`, `TranslationParityTest.kt`)*:
  • Verify `tuning_auto_pause_enable_title`, updated `tuning_auto_pause_speed_threshold_title`, `tuning_auto_pause_speed_threshold_desc`, and `tuning_auto_pause_start_in_auto_pause_desc` exist across all 9 canonical locales (`values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`).
  6. *Clean-Room Regression Execution (`TST-SET-080.6`)*:
  • Execute targeted unit tests and AAPT2 resources with 100% success.
  7. *Collapsible Calibration Accordion & Subtitle Format Contract Test (`TST-SET-080.7`, `AutoPauseTabsScreenContractTest.kt`, `TuningSubtitleFormatterTest.kt`)*:
  • Verify `AutoPauseTabsScreen.kt` source contract: encapsulates `TuningSliderItem` inside `TuningAccordionSection`.
  • Verify default collapsed state (`isExpanded = false` via `rememberSaveable`).
  • Verify `TuningSubtitleFormatter.formatAutoPauseSubtitle` formats active threshold and debounce delays with units.
  • Verify localization parity across all 9 languages for accordion title and subtitle strings.


---

### TST-SET-081: **Preservation of Unclustered Status on Workout Edits & Explicit Cluster Management Verification Suite** [Verified]
- **Requirement(s)**: `REQ-SET-081`
- **Component**: **WorkoutRepository / EditWorkoutClustering / WorkoutClusterEngine**
- **Jira**: ``ATT-4862``
- **Test Description**:
  1. *Unclustered Workout Name Edit Test (`TST-SET-081.1`, `EditWorkoutClusteringTest.kt`)*:
  • Given an unclustered workout (`clusterId == -1L`, "Offen"),
  • When the workout name is updated to a custom non-empty string and `saveWorkout()` is called,
  • Verify `clusterEngine.learnFromWorkout()` is NOT called, no cluster is created in `WorkoutClusters.db`, and `clusterEngine.assignClusterToWorkout()` is NOT called.
  • Verify the saved workout in memory and SQLite preserves `clusterId == -1L`.
  2. *Unclustered Selection ("Offen") Persistence Test (`TST-SET-081.2`, `EditWorkoutClusteringTest.kt`)*:
  • Given a previously clustered workout (`clusterId > 0`),
  • When the user unassigns the cluster via `unassignCluster()` (selecting "Offen") and subsequently edits the workout name and saves,
  • Verify the workout strictly remains unclustered (`clusterId == -1L`) and `saveWorkout()` does not reassign or create any cluster.
  3. *Clustered Workout Centroid Update Test (`TST-SET-081.3`, `EditWorkoutClusteringTest.kt`)*:
  • Given a clustered workout (`clusterId == 10L`),
  • When saving a name or sport edit via `saveWorkout()`,
  • Verify `clusterEngine.learnFromWorkout(..., clusterIdOverride = 10L)` is invoked to update the cluster's centroids and sport.
  • Verify `assignClusterToWorkout()` is NOT called to override cluster assignment.
  4. *Clean-Room Regression Execution (`TST-SET-081.4`)*:
  • Execute targeted unit tests in `EditWorkoutClusteringTest`, `EditWorkoutClusterTest`, and `ThemeColorCentralizationAuditTest` with 100% pass rate.

### TST-SET-082: **Timestamped Zone Boundary Snapshots & Historical Workout Resolution Verification Suite** [Verified]
- **Requirement(s)**: `REQ-SET-082`, `REQ-SET-001`
- **Component**: **AthleteMetricsDatabaseManager / AthleteMetricsRepository / WorkoutRepository / TelemetryMetricGraph / ZonesSettingsViewModel**
- **Jira**: `ATT-4844`, `ATT-5399`
- **Test Description**:
  1. *SQLite Schema Migration, Parity & Registry Contract Test (`TST-SET-082.1`, `AthleteMetricsRealSqliteMigrationTest.kt`)*:
  • Verify `AthleteMetricsDbHelper` upgrades from `DB_VERSION = 1` to `2` by creating the `zone_history` table and `idx_zone_history_type_time` index.
  • Verify `onCreate` vs incremental `onUpgrade` produce 100% identical schemas (Rule 64).
  • Verify `DatabaseSchemaRegistry.getMaxSupportedVersion("AthleteMetrics.db")` matches `AthleteMetricsDbHelper.DB_VERSION` (Rule 62).
  2. *Zone Snapshot CRUD, Monotonicity & Symmetric DTO Mapping Test (`TST-SET-082.2`, `AthleteMetricsZoneSnapshotTest.kt`)*:
  • Verify insertion, retrieval of effective snapshot at given timestamp, update, and deletion of snapshots for `HR_RUN`, `HR_BIKE`, and `PWR_BIKE`.
  • Verify validation rejects non-monotonic cutoffs ($Z_{i+1} \le Z_i$) and invalid boundary counts (must be 4 for HR, 6 for Power).
  • Verify single-row `fromCursor` and batch `fromCursorBatch` symmetric mapping (Rule 20).
  3. *Unidirectional SettingsDataStore Projection Test (`TST-SET-082.3`, `AthleteMetricsRepositorySyncTest.kt`)*:
  • Verify that inserting a new latest snapshot automatically updates `SettingsDataStore` zone preference keys.
  • Verify that deleting the latest snapshot rolls back `SettingsDataStore` to the next most recent historical snapshot.
  4. *Historical Workout Zone Distribution & Telemetry Graph Resolution Test (`TST-SET-082.4`, `WorkoutRepositoryHistoricalZoneTest.kt`)*:
  • Given historical workouts with different start times, verify `getHeartRateZoneDistribution` and `getPowerZoneDistribution` resolve against the snapshot valid at workout start time rather than current preferences.
  • Verify `TelemetryMetricGraph` and `MapDetailLayout` receive effective historical thresholds.
  5. *9-Language Localization & Format Specifier Parity Test (`TST-SET-082.5`, `TranslationParityTest.kt`)*:
  • Verify all zone history UI strings are present in all 9 supported locales (`values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`) with matching positional specifiers (`%1$s`).

### TST-SET-083: **Two-Device Free Tier Quota Across All Sensor Protocols Verification Suite** [Verified]
- **Requirement(s)**: `REQ-SET-083`
- **Component**: `DevicesDatabaseManager.java`, `DeviceListViewModel.kt`, `ProFeatureManager.kt`
- **Test Description**:
1. Verify fresh install `addLocationDevicesToDb` sets `SPEED_AND_LOCATION_GPS` (paired=true), `SPEED_AND_LOCATION_GOOGLE_FUSED` (paired=false), `SPEED_AND_LOCATION_NETWORK` (paired=false), and `ALTITUDE_FROM_PRESSURE` (paired=true).
2. Verify `DeviceListViewModel` counts total paired devices across all protocols and permits pairing up to 2 devices in Free tier.
3. Assert pairing attempt for a 3rd device is blocked and intercepted in Free tier.

### TST-SET-084: **One-Tap Swap-or-Upgrade Dialog Verification Suite** [Verified]
- **Requirement(s)**: `REQ-SET-084`
- **Component**: `SensorQuotaReconciliationManager.kt`, `DeviceListViewModel.kt`, `ControlTrackingViewModel.kt`, `DeviceManager.java`, `ATrainingTrackerApp.kt`, `MainActivityWithNavigation.kt`, `SwapOrUpgradeDialog.kt`, `SwapOrUpgradeDialogContractTest.kt`, `KeepTwoSensorsBottomSheet.kt`, `DeviceDataRepository.kt`
- **Jira**: `ATT-51`, `ATT-5399`, `ATT-5398`, `ATT-5469`, `ATT-5470`, `ATT-5490`, `ATT-5661`, `ATT-5657`
- **Test Description**:
1. Trigger pairing of a 3rd device in Free tier; verify `showSwapOrUpgradeDialog` state is set to true.
2. Verify dialog displays candidate devices and preselects recommendation.
3. Execute "Swap & Pair" action; assert atomic database update (unpair existing, pair new) and final paired count $\le 2$.
4. Verify "Support Development" (*"Entwicklung unterstützen"*) button triggers navigation to `NavRoutes.SUPPORTER`.
5. *Subscription Downgrade Global Reconciliation Test*: Given `!isProActive()` and $> 2$ paired sensors without active tracking, verify reconciliation event is emitted globally on app start, tracking screen entry, and pre-tracking search/start with 2 sensible defaults pre-selected.
6. *Non-Destructive Batch Pruning Test*: Confirming selection or dismissing applies selection, setting unselected sensors `isPaired = false` in database while preserving custom names, calibration, and equipment links. Total paired devices $\le 2$.
7. *Multi-Sensor Swap Hardening Test*: When executing swap while $> 2$ sensors are paired, excess devices are pruned so resulting paired count is strictly $\le 2$.
8. *9-Language Localization Parity Test*: Verify all reconciliation bottom sheet UI strings exist across all 9 locales with matching format specifiers.
9. *Pre-Tracking Quota Interception Test*: In `ControlTrackingViewModel`, clicking start tracking or search for sensors with $> 2$ paired devices on Free tier triggers reconciliation and blocks tracking start.
10. *BANALService Free Quota Hardware Connection Clamping Test*: In `DeviceManager.startSearchForPairedDevices()`, verify remote device connections are clamped to at most 2 devices when `!isProActive()`.
11. *Rule 73 Stacked Button Layout Contract (`TST-SET-084.8`, `SwapOrUpgradeDialogContractTest.kt`)*: Verify `SwapOrUpgradeDialog` renders primary 'Tauschen & Koppeln' `Button` and secondary 'Entwicklung unterstützen' `FilledTonalButton` stacked full-width with 8dp spacing in sheet content, and a single full-width 'Abbrechen' `TextButton` in `actions`, eliminating broken word wrapping on German and localized mobile screens.
12. *Google Fused Preselection & Active Remote Sensor Prioritization Test (`TST-SET-084.2`, `SensorQuotaReconciliationManagerTest.kt`, `DeviceListViewModelQuotaTest.kt`)*: Verify that when downgrading to Free tier with both Google Fused and GPS paired plus remote sensors, `determineDefaultKeepDeviceIds` preselects Google Fused and the most recently active remote sensor (connected first, latest `lastSeen` timestamp second). When only GPS or only Google Fused is paired, that single location sensor is preserved. When no remote sensor is paired, verify fallback to `ALTITUDE_FROM_PRESSURE`. Verify `determineRecommendedUnpairDeviceId` recommends unpairing `ALTITUDE_FROM_PRESSURE` first, then `SPEED_AND_LOCATION_GPS` if both location sensors are paired.
13. *Appreciative Dialog Copy & 9-Locale Localization Verification (`TST-SET-084.13`, `TranslationParityTest.kt`)*: Verify `SwapOrUpgradeDialog` and `KeepTwoSensorsBottomSheet` strings display appreciative base version copy and "Support Development" CTA across all 9 locales with single `%1$s` placeholder preserved in `swap_dialog_message` and zero decorative emojis.
14. *Never Recommend Google Fused or Sole Location Sensor for Unpairing Test (`TST-SET-084.14`, `SensorQuotaReconciliationManagerTest.kt`, `DeviceListViewModelQuotaTest.kt`)*: Verify that when Google Fused (or sole Location sensor) is paired alongside one or more non-location sensors (e.g. cadence, HRM, power), `determineRecommendedUnpairDeviceId` recommends the non-location sensor (disconnected first, oldest `lastSeen` second) and NEVER returns the ID of Google Fused or the sole Location sensor. In `SwapOrUpgradeDialog`, verify fallback selection likewise prioritizes non-location devices.

### TST-SET-085: **Lifetime Pioneer Early Adopter Recognition Verification Suite** [Verified]
- **Requirement(s)**: `REQ-SET-085`
- **Component**: `WorkoutSummariesDatabaseManager.java`, `SettingsDataStore.kt`, `ProFeatureManager.kt`, `SupporterScreen.kt`, `AppNavigationDrawer.kt`
- **Test Description**:
1. Seed a workout before `2026-11-30 23:59:59`; verify `hasWorkoutBefore()` returns true and `isPioneerEarlyAdopter` is persisted as true.
2. Seed a workout after `2026-11-30 23:59:59` with no prior workouts; verify `isPioneerEarlyAdopter` is false.
3. Verify Pioneer status grants `isProActive() == true` and permits pairing $> 2$ devices.
4. Verify Pioneer badge appears in UI and voluntary supporter upgrade is accessible.
5. Seed historical workouts across multiple years (e.g. 2018); verify `getEarliestWorkoutYear()` returns 2018; verify `PioneerStatusBanner` and `DrawerHeader` format dynamic string `"★ Pioneer (Seit 2018)"` / `"★ Pioneer (Since 2018)"`. When no workouts exist, verify fallback to `"★ Pioneer"`.

### TST-SET-086: **Five-Tier Google Play Supporter Subscriptions Verification Suite** [Verified]
- **Requirement(s)**: `REQ-SET-086`
- **Component**: `SupporterScreen.kt`, `SupporterComponents.kt`, `BillingClientLifecycle.kt`, `ProFeatureManager.kt`, `AppNavigationDrawer.kt`
- **Jira**: `ATT-51`, `ATT-5371`, `ATT-5659`
- **Test Description**:
1. Verify 5 base plan IDs (`bronze-monthly`, `bronze-yearly`, `silver-yearly`, `gold-yearly`, `diamond-yearly`) mapped to correct `SupporterTier` enums.
2. Verify `silver-yearly` is visually highlighted as Recommended with the badge placed vertically above the tier title.
3. Verify `SupporterPlanCard` action buttons enforce `maxLines = 1, softWrap = false` to guarantee single-line rendering without text wrapping (`Rule 38`).
4. Verify drawer header reflects active supporter tier and drawer contains Supporter destination.
5. Verify `HardwareVotingSection` and hardware acquisition promises are completely removed.
6. Verify `supporter_screen_subtitle` renders `100%` (single percent sign) across all locales and zero hardcoded user-facing strings in supporter UI components.
7. *Appreciative Wording & Buzzword Elimination Audit*: Verify that `supporter_screen_subtitle`, `supporter_active_tier_thank_you`, and `supporter_core_invariant_body` invite users to support development without defensive marketing phrases (*"15+ Jahre"*, *"Indie"*, *"unabhängiges Fitnesstracking"*) across all 9 locales (ATT-5659).

### TST-SET-087: **Debug Supporter Tier Override & Pro Feature Manager Governance Verification Suite** [Verified]
- **Requirement(s)**: `REQ-SET-087`
- **Component**: `ProFeatureManager.kt`, `DeveloperMenuScreen.kt`, `AppNavigationDrawer.kt`, `AdvancedTuningScreen.kt`, `SettingsDataStore.kt`
- **Test Description**:
1. In debug build, set `supporterTierOverride` to `BRONZE`, `SILVER`, `GOLD`, `DIAMOND`; verify `ProFeatureManager.effectiveTier` and `isProActive()` immediately reflect override.
2. Verify setting override to `NONE` falls back to Pioneer or Subscription status.
3. *Developer Menu Navigation & Release Isolation (`TST-SET-087.3`)*: Verify `DeveloperMenuScreen` is accessible in the navigation drawer via `R.id.drawer_developer_menu` in debug builds (`BuildConfig.DEBUG == true`), and that the drawer item, route, and debug overrides are completely absent in release builds (`BuildConfig.DEBUG == false`).
4. *Clean AdvancedTuningScreen (`TST-SET-087.4`)*: Verify `AdvancedTuningScreen` contains zero developer or supporter tier override controls, having relocated them cleanly to `DeveloperMenuScreen`.

### TST-SET-088: **Supporter N+1 Garage Detector & Upgrade Prompts Verification Suite** [Verified]
- **Requirement(s)**: `REQ-SET-088`
- **Component**: `GarageLeagueDetector.kt`, `GarageDetectorDialog.kt`, `GarageUpgradeNudgeDialog.kt`, `GarageUpgradeNudgeDialogContractTest.kt`, `TrainingApplication.java`, `StravaHelper.kt`, `StravaEquipmentSynchronizeThread.java`, `StravaDataPurgeManager.kt`, `DeviceListViewModel.kt`, `EquipmentViewModel.kt`, `SettingsDataStore.kt`, `SupporterDebugSection.kt`
- **Jira**: `ATT-5299`, `ATT-5400`, `ATT-5399`, `ATT-5468`, `ATT-5570`
- **Test Description**:
1. Verify `GarageLeagueDetector.evaluateLeague()` resolves to `DIAMOND` when bikes $\ge 4$, sensors $\ge 8$, or power meters $\ge 2$, taking precedence over Gold criteria.
2. Verify `GarageLeagueDetector.evaluateLeague()` resolves to `GOLD` when bikes $\ge 3$, shoes $\ge 4$, sensors $\ge 5$, or power meters $\ge 1$.
3. Verify counts below thresholds resolve to `NONE`.
4. Assert tier monotonicity: when effective tier is already `GOLD`, Gold League prompt is suppressed; when effective tier is `DIAMOND`, both Gold and Diamond prompts are suppressed.
5. Verify non-spam persistence: when `hasShownGoldGarageNudge` or `hasShownDiamondGarageNudge` is true, subsequent nudges for that league are suppressed.
6. Verify debug preview triggers in `SupporterDebugSection` render respective dialogs and reset button clears DataStore flags.
7. Verify 9-language localization audit (`ATT-5400`): all 4 `garage_detector_dialog_*` string resources across all 9 locales contain zero decorative inline Unicode emojis (`🚴‍♂️`, `💎`, `✨`, `😉`) while strictly preserving positional format specifiers (`%1$d`..`%5$s`).
8. *Rule 73 Stacked Button Layout Contract (`TST-SET-088.7`, `GarageUpgradeNudgeDialogContractTest.kt`)*: Verify `GarageUpgradeNudgeDialog` (and `GarageDetectorDialog`) renders primary 'Upgrade' `Button` stacked full-width in sheet content, and a single full-width 'Später / Weiter zum Training' `TextButton` in `actions`, eliminating broken 2–3 line button word wrapping on German and localized mobile screens.
9. *Strava Subscriber League Qualification & Ingestion (`TST-SET-088.1`, `ATT-5570`)*: Verify `GarageLeagueDetector.evaluateLeague()` resolves to `GOLD` when athlete is a Strava subscriber even with 0 hardware; resolves to `DIAMOND` when athlete has Gold hardware AND is a Strava subscriber; verify `StravaHelper.storeJSONData` and `StravaEquipmentSynchronizeThread.fillDbFromJsonObject` ingest `summit`/`premium`; verify `StravaDataPurgeManager` and `TrainingApplication.deleteStravaToken` reset subscriber status.
10. *Strava-Specific Dialog Copy & 9-Language Parity (`TST-SET-088.5`, `ATT-5570`)*: Verify `garage_detector_dialog_message_gold_strava` and `garage_detector_dialog_message_diamond_strava` are used when `isStravaSubscriber == true`, contain 0 emojis, retain all positional specifiers, and maintain 100% parity across all 9 locales.

### TST-LOC-001: **Monetization & Supporter 9-Language Localization Parity Verification Suite** [Verified]
- **Requirement(s)**: `REQ-SET-083`, `REQ-SET-084`, `REQ-SET-085`, `REQ-SET-086`, `REQ-SET-087`, `REQ-SET-088`, `REQ-UI-445`, `REQ-UI-446`, `REQ-UI-455`, `REQ-UI-456`
- **Component**: `res/values*/strings.xml`, `TranslationParityTest.kt`
- **Test Description**:
1. Run `TranslationParityTest` verifying all 9 locales (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`) have identical key sets and parameter specifiers for all new monetization strings.

### TST-SET-089: **Workout Cluster Merge & Similarity-Ranked Target Selection Verification Suite** [Verified]
- **Requirement(s)**: `REQ-SET-089`
- **Component**: `WorkoutClusterComponents.kt`, `WorkoutClustersScreen.kt`, `WorkoutClustersViewModel.kt`, `WorkoutClusterEngine.kt`, `WorkoutSummariesDatabaseManager.java`, `RoutesDatabaseManager.kt`, `WorkoutClusterMergeTest.kt`
- **Jira**: `ATT-5666`, `ATT-5824`
- **Test Description**:
  1. *Engine Mathematical & Database Reassignment Unit Tests (`TST-SET-089.1`, `WorkoutClusterMergeTest.kt`)*:
  • Create source cluster `A` ($N_A = 2$) and target cluster `B` ($N_B = 4$).
  • Execute `WorkoutClusterEngine.mergeClusters(A.id, B.id)`.
  • Verify all workouts previously assigned to `A` have `CLUSTER_ID == B.id`.
  • Verify `B`'s new centroid coordinates match the weighted average $\frac{\text{coord}_B \cdot 4 + \text{coord}_A \cdot 2}{6}$.
  • Verify `B`'s hit count is updated to 6.
  • Verify cluster `A` is deleted from `WorkoutClusterDatabaseManager`.
  2. *Similarity Ranking Candidate Selection Unit Tests (`TST-SET-089.2`, `WorkoutClustersViewModelTest.kt`)*:
  • Given source cluster `A`, verify candidate list excludes `A`, computes similarity scores via `scoreClusters`, and orders candidates ascending (lowest score = highest similarity at index 0).
  3. *Two-Step Transition State Independence Contract Test (`TST-SET-089.3`, `WorkoutClusterMergeDialogContractTest.kt`)*:
  • In `WorkoutClustersScreen`, verify selecting candidate cluster `B` in `WorkoutClusterSelectionDialog` transitions to merge confirmation dialog immediately on the first attempt even when `onDismiss` executes immediately upon selection.
  • Verify that dismissing the selection dialog (`onDismiss`) without selection resets state cleanly and does not open confirmation dialog.
  • Verify that confirming or dismissing the confirmation dialog resets `pendingMergePair` to `null`.
  4. *9-Language Localization & Positional Specifier Audit (`TST-SET-089.4`, `TranslationParityTest.kt`)*:
  • Verify `cluster_menu_merge`, `cluster_merge_dialog_title`, `cluster_merge_dialog_message` (`%1$s`, `%2$d`, `%3$s`), and `cluster_merge_action` exist across all 9 supported locales (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`) with zero inline emojis (`ATT-5400`).
  5. *Clean-Room Targeted Regression Execution (`TST-SET-089.5`)*:
  • Execute targeted test suites (`com.atrainingtracker.trainingtracker.database.*`, `com.atrainingtracker.trainingtracker.ui.clusters.*`, `com.atrainingtracker.trainingtracker.localization.*`, `com.atrainingtracker.trainingtracker.build.*`) asserting 100% pass rate.

### TST-SET-095: **Known Locations Merge, Hybrid Geofence Geometry & Database Reconciliation Suite** [Verified]
- **Requirement(s)**: `REQ-SET-095`
- **Component**: `KnownLocationsScreen.kt`, `KnownLocationsViewModel.kt`, `KnownLocationsRepository.kt`, `KnownLocationsDatabaseManager.java`, `KnownLocationsGeometryMath.kt`, `KnownLocationsMergeTest.kt`
- **Jira**: `ATT-5889`
- **Test Description**:
  1. *Hybrid Geofence Geometry Mathematical Unit Tests (`TST-SET-095.1`, `KnownLocationsGeometryMathTest.kt`)*:
  • Given two unlocked, non-home locations $S$ and $T$ with $r_S = 200\text{ m}$, $r_T = 200\text{ m}$ at distance $d = 200\text{ m}$, verify center moves to midpoint and radius equals $300\text{ m}$.
  • Given enclosing circles ($d + r_S \le r_T$), verify larger circle is adopted without center drift.
  • Given target $T$ with `isHome == true` or `isLocked == true`, verify target center remains pinned and radius expands to $\max(r_T, \lceil d + r_S \rceil)$.
  2. *Database Transaction & Reconciliation Unit Tests (`TST-SET-095.2`, `KnownLocationsDatabaseManagerTest.kt`)*:
  • Given location $S$ and target $T$ in `StartLocation2Altitude.db`, execute `mergeLocations(sourceId, targetId)`.
  • Verify atomic update of $T$ with new coordinates, radius, consolidated `isHome`, and altitude precedence.
  • Verify $S$ is deleted from SQLite.
  • Verify `reconcileHitCountsWithWorkoutSummaries` correctly tallies workout starts within merged geofence without double-counting.
  3. *Candidate Filtering & Sorting ViewModel Unit Tests (`TST-SET-095.3`, `KnownLocationsViewModelTest.kt`)*:
  • Given source $S$, verify `getCandidateLocationsForMerge(S)` filters out $S$, excludes locations with $d > 2\,000\text{ m}$, and sorts remaining candidates ascending by raw numeric distance primitive (`ATT-3324`).
  • Verify empty list is returned when no candidates exist within $2\,000\text{ m}$.
  4. *Two-Step Merge Dialog UI Contract Tests (`TST-SET-095.4`, `KnownLocationsMergeDialogContractTest.kt`)*:
  • Verify context menu item *"Mit anderem Ort zusammenführen…"* triggers candidate picker dialog.
  • Verify candidate selection opens confirmation dialog with radius preview and pinned target notice.
  • Verify confirming merge calls `viewModel.mergeLocations(source, target)` and clears dialog state.
  • Verify cancelling either dialog resets merge state cleanly.
  5. *9-Language Localization & Positional Specifier Audit (`TST-SET-095.5`, `TranslationParityTest.kt`)*:
  • Verify `known_locations_menu_merge`, `known_locations_merge_select_target_title`, `known_locations_merge_distance_away` (`%1$s`), `known_locations_merge_no_candidates`, `known_locations_merge_dialog_title`, `known_locations_merge_dialog_message` (`%1$s`, `%2$s`, `%3$s`), `known_locations_merge_pinned_notice` (`%1$s`), and `known_locations_merge_action` exist across all 9 supported locales (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`) with zero inline emojis (`ATT-5400`).

### TST-SET-096: **Workout Cluster Split & Sub-Corridor Detection Verification Suite** [Verified]
- **Requirement(s)**: `REQ-SET-096`
- **Component**: `WorkoutClusterHeatmapScreen.kt`, `WorkoutClusterSplitSheet.kt`, `WorkoutClustersViewModel.kt`, `WorkoutClusterEngine.kt`, `WorkoutClusterCorridorDetector.kt`, `WorkoutClusterSplitTest.kt`
- **Jira**: `ATT-5900`
- **Test Description**:
  1. *Sub-Corridor Detection Algorithm Unit Tests (`TST-SET-096.1`, `WorkoutClusterCorridorDetectorTest.kt`)*:
  • Verify polyline station sampling at fractional distances 15%, 30%, 50%, 70%, 85%.
  • Verify pairwise corridor deviation metric correctly separates two parallel corridors diverging by > 500 m while grouping nearly identical tracks.
  • Verify threshold sensitivity modulation: tighter threshold (250 m) produces finer sub-groups; wider threshold (1500 m) merges into fewer groups.
  • Verify that identical tracks yield a deviation distance of 0.0 m.
  2. *Transactional Database Split & Centroid Reconciliation Unit Tests (`TST-SET-096.2`, `WorkoutClusterSplitTest.kt`)*:
  • Given cluster with 10 member workouts (6 on Corridor A, 4 on Corridor B), execute split for Corridor B.
  • Verify new `WorkoutCluster` created with `hitCount == 4`, centroid matching Corridor B member workouts, and correct spatial bounds.
  • Verify original cluster updated with `hitCount == 6` and centroid matching Corridor A member workouts.
  • Verify all 4 Corridor B workouts in `WorkoutSummaries` have `CLUSTER_ID == newClusterId`.
  • Verify member workouts renamed according to new cluster name when `renameWorkouts == true`.
  • Verify `WorkoutClusterStats` recalculated for both original and new clusters.
  3. *UI & Interaction Contract Tests (`TST-SET-096.3`, `WorkoutClusterSplitDialogContractTest.kt`)*:
  • In `WorkoutClusterHeatmapScreen`, verify `CallSplit` button appears in header when `hitCount >= 2` and is hidden when `hitCount < 2`.
  • Verify clicking `CallSplit` opens `WorkoutClusterSplitSheet`.
  • Verify slider adjustment updates detected variant groups and associated map polyline colors.
  • Verify variant checkbox toggling and editable cluster name field pre-filled with unique candidate name.
  • Verify confirm button disabled when no secondary variant is selected or when only 1 group is detected.
  4. *9-Language Localization & Positional Specifier Audit (`TST-SET-096.4`, `TranslationParityTest.kt`)*:
  • Verify `cluster_split_action`, `cluster_split_dialog_title`, `cluster_split_corridor_threshold` (`%1$s`), `cluster_split_no_variants`, `cluster_split_primary_label` (`%1$d`, `%2$s`), `cluster_split_variant_label` (`%1$d`, `%2$d`, `%3$s`), `cluster_split_rename_workouts`, and `cluster_split_action_confirm` exist across all 9 supported locales (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`) with zero inline emojis (`ATT-5400`).
  5. *Clean-Room Targeted Regression Execution (`TST-SET-096.5`)*:
  • Execute targeted test suites (`com.atrainingtracker.trainingtracker.database.*`, `com.atrainingtracker.trainingtracker.ui.clusters.*`, `com.atrainingtracker.trainingtracker.localization.*`, `com.atrainingtracker.trainingtracker.build.*`) asserting 100% pass rate.

### TST-SET-097: **Point-to-Point Route Corridor Discrimination & Bipolar Product Apex Verification Suite** [Verified]
- **Requirement(s)**: `REQ-SET-097`
- **Component**: `WorkoutClusterEngine.kt`, `TrackerService.java`, `WorkoutClusterCorridorDetector.kt`, `WorkoutRepository.kt`, `WorkoutClustersViewModel.kt`, `WorkoutClusterHeatmapScreen.kt`, `MapScreenWithTrack.kt`
- **Jira**: `ATT-5899`, `ATT-6686`, `ATT-7315`
- **Test Description**:
  1. *Bipolar Product Apex Calculation Unit Tests (`TST-SET-097.1`, `WorkoutClusterPointToPointApexTest.kt`)*:
  • Verify that for an A -> B route with lateral deflection (e.g. northern arc between Sindelfingen and Schönaich), the apex is located at the point of maximum $d(\text{start}, P) \times d(P, \text{end})$, separated from `end` by > 1.5 km.
  • Verify that for straight A -> B lines, the apex falls on the exact midpoint ($D/2$) and eliminates endpoint overshoot degeneracy.
  • Verify that for routes with longitudinal overshoots (e.g. Bike2Work #2063 telemetry), the overshoot points yield near-zero products and are never selected.
  • Verify that for closed loops ($d(\text{start}, \text{end}) < 150\text{ m}$), apex coordinates are 100% identical to legacy `findApexFromPoints(start, points, null)`.
  2. *Parallel Corridor Scoring & Discrimination Tests (`TST-SET-097.2`, `WorkoutClusterCorridorScoringTest.kt`)*:
  • Given two clusters sharing identical start (Sindelfingen) and end (Schönaich) coordinates with similar distance (~9.4 km) but separated laterally by 1.5 km (forest corridor vs. city corridor).
  • When a new workout matching the forest corridor is evaluated by `scoreClusters()`, verify that the forest cluster achieves a significantly lower similarity cost and is suggested over the city corridor cluster.
  • When a new workout matching the city corridor is evaluated, verify that the city cluster is suggested.
  3. *TrackerService Integration & Persistence Audit (`TST-SET-097.3`)*:
  • Verify that session finalization in `TrackerService` passes `endPos` into `findApexFromPoints()` and persists non-degenerate apex coordinates into `WorkoutSummaries`.
  4. *Differentiated Map Marker Labeling Test (`TST-SET-097.5`, `WorkoutClusterPointToPointApexTest.kt`)*:
  • Verify that `WorkoutCluster.isPointToPoint` and `WorkoutData.isPointToPoint` evaluate to `true` when $d(\text{start}, \text{end}) \ge 150\text{ m}$ and `false` when $d(\text{start}, \text{end}) < 150\text{ m}$.
  • Verify that `WorkoutRepository.getMapMarkerList()` assigns `R.string.route_apex` ("Scheitelpunkt" / "Apex") for point-to-point workouts and `R.string.max_line_distance` ("weiteste Entfernung" / "furthest away") for closed-loop workouts.
  • Verify that `TranslationParityTest` passes across all 9 localized languages for string resource `route_apex`.
  5. *Clean-Room Targeted Regression Execution (`TST-SET-097.4`)*:
  • Run targeted unit tests (`com.atrainingtracker.trainingtracker.clusters.*`, `com.atrainingtracker.trainingtracker.database.WorkoutCluster*`, `com.atrainingtracker.trainingtracker.ui.clusters.*`) ensuring zero regressions.

### TST-SET-098: **Equipment Lookup Normalization, Strava Ingestion & Deduplication Reconciliation Suite** [Verified]
- **Requirement(s)**: `REQ-SET-098`, `REQ-SET-002`
- **Component**: `EquipmentDbHelper.java`, `StravaBulkExportImporter.kt`, `StravaEquipmentSynchronizeThread.java`, `WorkoutSummariesDatabaseManager.java`, `SportTypeEquipmentLinkManager.kt`
- **Jira**: `ATT-6215`
- **Test Description**:
  1. *Case-Insensitive & Trimmed Lookup Unit Tests (`TST-SET-098.1`, `EquipmentDbHelperNormalizationTest.kt`)*:
  • Verify `getEquipmentId` matches `'Endurance'`, `'endurance '`, `' ENDURANCE '` to the same ID.
  • Verify `addOrUpdateStravaGear` matches existing gear regardless of case or whitespace and preserves existing user-customized `Name`.
  2. *Strava Bulk Import Gear Ingestion Audit (`TST-SET-098.2`, `StravaBulkExportImporterGearTest.kt`)*:
  • Verify importing `bikes.csv` and `shoes.csv` leaves `StravaId` as `null` and populates `StravaName`.
  • Verify subsequent Strava API sync successfully matches and enriches the bulk-imported items without creating duplicates.
  3. *Strava Equipment Synchronize Thread Contract Tests (`TST-SET-098.3`, `StravaEquipmentSyncTest.kt`)*:
  • Verify that syncing remote gear with trailing whitespace or lowercase characters links to existing uppercase local equipment without overwriting `Name`.
  4. *Real SQLite Deduplication & Cascade Reconciliation Migration Tests (`TST-SET-098.4`, `EquipmentDeduplicationMigrationTest.kt`)*:
  • Seed SQLite with duplicate bike/shoe rows (including name-polluted `StravaId`), linked sensors in `Links`, sport associations in `SportTypeLinks.db`, and workouts in `WorkoutSummaries.db`.
  • Execute migration/reconciliation.
  • Assert that surviving row retains capitalized name and valid Strava ID, workouts are repointed to surviving ID, links are consolidated without primary key errors, and duplicate rows are deleted.
  5. *Tier 1 Targeted Regression Suite (`TST-SET-098.5`)*:
  • Run targeted test suites (`com.atrainingtracker.trainingtracker.database.*`, `com.atrainingtracker.trainingtracker.migration.*`, `com.atrainingtracker.trainingtracker.onlinecommunities.strava.*`, `com.atrainingtracker.trainingtracker.build.*`) asserting 100% pass rate.

### TST-SET-099: **Paired BLE Peripheral Scan-Assisted Connection & Public/Random MAC Address Test Suite** [Verified]
- **Requirement(s)**: `REQ-SET-099`
- **Component**: `MyBTLEDevice.java`, `MyBTLEDeviceAddressAndSearchTest.kt`
- **Jira**: `ATT-6245`
- **Test Description**:
  1. *Public IEEE MAC (0xC0..0xFC OUI) Scan Discovery & Connection Unit Test (`TST-SET-099.1`, `MyBTLEDeviceAddressAndSearchTest.kt`)*:
  • Verify that a peripheral with Public IEEE MAC having OUI in `0xC0..0xFC` (e.g. `D0:C6:37:33:67:AA`) is discovered via scan assistance and connects via `result.getDevice().connectGatt(...)` during `State.SEARCHING`.
  2. *Random Static MAC Scan Discovery & Connection Unit Test (`TST-SET-099.2`, `MyBTLEDeviceAddressAndSearchTest.kt`)*:
  • Verify that a peripheral with Random Static MAC (e.g. `C6:CC:E3:F5:F0:37`) is discovered via scan assistance and connects via `result.getDevice().connectGatt(...)` during `State.SEARCHING`.
  3. *Active Speculative GATT Supersede & Stale Callback Guard Unit Test (`TST-SET-099.3`, `MyBTLEDeviceAddressAndSearchTest.kt`)*:
  • Verify that when `onScanResult()` fires with an existing non-null `mBluetoothGatt` handle, the prior GATT is closed and replaced with `result.getDevice().connectGatt(...)`.
  • Verify that `onConnectionStateChange` ignores callbacks from non-current GATT instances (`gatt != mBluetoothGatt`).
  4. *Scan Fallback Direct Connect Unit Test (`TST-SET-099.4`, `MyBTLEDeviceAddressAndSearchTest.kt`)*:
  • Verify that when scan assistance cannot be started (e.g. missing scan permission or null scanner), `startSearching()` falls back to direct `connectGatt(...)`.
  5. *Tier 1 Targeted BLE Regression Suite (`TST-SET-099.5`)*:
  • Run targeted test suites (`com.atrainingtracker.banalservice.devices.bluetooth_le.*`, `com.atrainingtracker.trainingtracker.build.*`) asserting 100% pass rate.

### TST-SET-100: **Health Connect Client Availability & Permission Verification Suite** [Verified]
- **Requirement(s)**: `REQ-SET-100`
- **Component**: `HealthConnectManager.kt`, `HealthConnectAvailabilityTest.kt`, `AndroidManifest.xml`
- **Jira**: `ATT-3052`, `ATT-6711`
- **Status**: Specified
- **Test Description**:
  1. *SDK Status Contract Tests (`TST-SET-100.1`)*:
  • Verify `HealthConnectClient.getSdkStatus()` correctly routes `SDK_AVAILABLE`, `SDK_UNAVAILABLE`, and `SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED`.
  2. *Permission Contract & Manifest Verification (`TST-SET-100.2`)*:
  • Verify required write permissions (`WRITE_EXERCISE`, `WRITE_EXERCISE_ROUTE`, `WRITE_HEART_RATE`, `WRITE_POWER`, `WRITE_SPEED`, `WRITE_DISTANCE`, `WRITE_ELEVATION_GAINED`, `WRITE_TOTAL_CALORIES_BURNED`, `WRITE_STEPS_CADENCE`, `WRITE_CYCLING_PEDALING_CADENCE`) are declared in `AndroidManifest.xml` and included in `REQUIRED_PERMISSIONS`.
  • Verify `AndroidManifest.xml` declares `VIEW_PERMISSION_USAGE` activity alias targeting `MainActivityWithNavigation` with `android.permission.START_VIEW_PERMISSION_USAGE` and intent filter for `android.intent.action.VIEW_PERMISSION_USAGE` with category `android.intent.category.HEALTH_PERMISSIONS`.
  3. *Tier 1 Targeted Unit Tests (`TST-SET-100.3`)*:
  • Run targeted test suites (`com.atrainingtracker.trainingtracker.cloud.healthconnect.*`, `com.atrainingtracker.trainingtracker.build.*`) asserting 100% pass rate.

### TST-SET-101: **Exercise Session & Telemetry Record Mapping Unit Test Suite** [Verified]
- **Requirement(s)**: `REQ-SET-101`
- **Component**: `HealthConnectRecordMapper.kt`, `HealthConnectRecordMapperTest.kt`, `HealthConnectManager.kt`
- **Jira**: `ATT-3052`, `ATT-6711`, `ATT-7668`
- **Status**: Verified
- **Test Description**:
  1. *Sport Type to Exercise Type Mapping Test (`TST-SET-101.1`)*:
  • Assert `BSportType.BIKE` -> `EXERCISE_TYPE_BIKING`, `BSportType.RUN` -> `EXERCISE_TYPE_RUNNING`, trainer -> `EXERCISE_TYPE_BIKING_STATIONARY`, treadmill -> `EXERCISE_TYPE_RUNNING_TREADMILL`, and unknown -> `EXERCISE_TYPE_OTHER_WORKOUT`.
  2. *Sensor Telemetry Series Transformation Tests (`TST-SET-101.2`)*:
  • Verify GPS track conversion to `ExerciseRoute` with correct timestamps and lat/lon/alt.
  • Verify HR samples converted to `HeartRateRecord.Sample` with valid bpm.
  • Verify power samples converted to `PowerRecord.Sample` with Watts.
  • Verify speed, distance, elevation gained, and calories series mapping.
  • Verify cadence samples converted to `CyclingPedalingCadenceRecord` (cycling) and `StepsCadenceRecord` (running).
  • Verify route permission gating: When `WRITE_EXERCISE_ROUTE` is not in granted permissions, `ExerciseSessionRecord` is constructed with `exerciseRoute = null` and no `SecurityException` is thrown.
  3. *Timestamp Boundary & Numerical Calorie Invariant Test (`TST-SET-101.3`)*:
  • Verify all sample timestamps fall strictly within `[session.startTime, session.endTime]`.
  • Verify `TotalCaloriesBurnedRecord` derives from numerical `WorkoutData.calories` without UI string parsing.
  4. *IPC Series Sample Chunking Test (`TST-SET-101.4`)*:
  • Verify series with >2,000 samples are partitioned into chunks containing $\le 2,000$ samples each.
  5. *Repository Layering Architecture Test (`TST-SET-101.5`)*:
  • Verify `HealthConnectRecordMapper` accesses samples via `WorkoutSamplesRepository`.
  6. *Route Timestamp Monotonicity & Boundary Test (`TST-SET-101.6`)*:
  • Verify multiple GPS sample rows with duplicate whole-second timestamps are deduplicated and strictly ascending (`sortedRoute[i].time < sortedRoute[i+1].time`), ensuring `ExerciseRoute` constructs without `IllegalArgumentException`.
  • Verify workouts with GPS points at or beyond `startTime + durationSec` extend `sessionRecord.endTime` so `maxRouteTime < sessionRecord.endTime` holds, preventing `ExerciseSessionRecord` time range violation (`ATT-7668`).

### TST-SET-102: **Trackless Indoor Workout Export Unit Test Suite** [Verified]
- **Requirement(s)**: `REQ-SET-102`
- **Component**: `HealthConnectRecordMapper.kt`, `HealthConnectManager.kt`, `HealthConnectRecordMapperTest.kt`
- **Jira**: `ATT-3052`
- **Status**: Verified
- **Test Description**:
  1. *Trackless Session Construction Test (`TST-SET-102.1`)*:
  • Verify `ExerciseSessionRecord` is built with `exerciseRoute = null` when workout contains no GPS fixes.
  2. *Non-Spatial Series Persistence Test (`TST-SET-102.2`)*:
  • Verify `PowerRecord`, `HeartRateRecord`, and `TotalCaloriesBurnedRecord` are mapped and exported without errors when route is null.
  3. *Null Safety & Boundary Guard Test (`TST-SET-102.3`)*:
  • Verify zero NPEs or illegal argument exceptions when processing workouts with missing or partial telemetry channels.

### TST-SET-103: **Health Connect Settings UI & Idempotent Sync Verification Suite** [Verified]
- **Requirement(s)**: `REQ-SET-103`
- **Component**: `HealthConnectSettingsDialog.kt`, `HealthConnectManager.kt`, `AppNavigationDrawer.kt`, `NavRoutes.kt`, `WorkoutHeader.kt`, `EditWorkoutScreen.kt`, `HealthConnectSyncTest.kt`, `strings_health_connect.xml`
- **Jira**: `ATT-3052`, `ATT-6712`, `ATT-6711`
- **Status**: Verified
- **Test Description**:
  1. *Settings Toggle State Persistence Test (`TST-SET-103.1`)*:
  • Verify toggle state binds to `TrainingApplication` / `SharedPreferences` preferences.
  2. *Client Record ID Determinism & Idempotency Test (`TST-SET-103.2`)*:
  • Verify `clientRecordId` generation is deterministic based on workout `fileBaseName` for sessions and sub-records.
  3. *9-Locale Translation Parity Verification (`TST-SET-103.3`)*:
  • Verify 100% parity across `values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/` for all planned strings via `TranslationParityTest`.
  4. *Navigation Drawer Wiring & Route Mapping Test (`TST-SET-103.4`)*:
  • Verify `drawer_health_connect` exists in `ids.xml`, is configured in `AppNavigationDrawer.kt` under `prefsOnlineCommunities` with Last Sync timestamp, and maps to `SettingsBottomSheetType.HEALTH_CONNECT` in `NavRoutes.toBottomSheetType()`.
  5. *Manual Sync & Edit Workout Dialog Lifecycle Test (`TST-SET-103.5`)*:
  • Verify `WorkoutHeader.onSyncHealthConnect` is wired and invokes `HealthConnectManager.syncWorkout(context, workoutData, force = true)`.
  • Verify `HealthConnectManager.syncWorkout(..., force = true)` succeeds when global auto-sync is disabled.
  • Verify `EditWorkoutScreen` does not trigger sync on checkbox toggle, but executes sync upon Save when checked.

### TST-SET-104: **Move Workout Dialog Unassignment and State Synchronization** [Verified]
- **Requirement(s)**: `REQ-SET-059`
- **Component**: `WorkoutClusterComponents.kt`, `WorkoutClustersViewModel.kt`, `WorkoutRepository.kt`, `WorkoutClustersScreen.kt`, `WorkoutClusterHeatmapScreen.kt`
- **Jira**: `ATT-7754`
- **Status**: Verified
- **Test Description**:
  1. *Selection Dialog Unassign Action Test (`TST-SET-104.1`)*:
  • Verify `WorkoutClusterSelectionDialog` renders "Keiner Route zuordnen" when `onUnassign` is non-null.
  • Verify clicking unassign card triggers `onUnassign()` and `onDismiss()`.
  2. *ViewModel Unassign and Flow Refresh Test (`TST-SET-104.2`)*:
  • Verify `WorkoutClustersViewModel.unassignWorkout` calls `WorkoutRepository.unassignClusterFromWorkout` and `refreshClusters()`.
  • Verify `_clusterWorkouts` and `_unclusteredWorkouts` are updated.
  3. *Repository Refresh Integration Test (`TST-SET-104.3`)*:
  • Verify `WorkoutRepository.unassignClusterFromWorkout` calls `WorkoutClusterRepository.refreshClusters()`.
  4. *9-Locale Translation Parity Verification (`TST-SET-104.4`)*:
  • Verify `cluster_naming__leave_unclustered` and `cluster_naming__create_new` parity across all 9 locales.
  5. *Inspected Workout Reactive Update Test (`TST-SET-104.5`)*:
  • Verify `inspectedWorkout` in `WorkoutClustersScreen` updates when cluster assignment changes or upon return from editing.

### TST-SET-105: **Immediate Workout Name Synchronization on Route Cluster Change** [Verified]
- **Requirement(s)**: `REQ-SET-059`
- **Component**: `EditWorkoutViewModel.kt`, `EditWorkoutScreen.kt`, `WorkoutClusterEngine.kt`
- **Jira**: `ATT-7667`
- **Status**: Verified
- **Test Description**:
  1. *Immediate Workout Name Update on Cluster Selection (`TST-SET-105.1`)*:
  • Verify that calling `applyClusterIdentity(cluster)` immediately sets `workoutName` to the cluster-formatted name in `_workoutData.value` and resets `userManuallyChangedWorkoutName` to `false`.
  2. *Repository Emission Name Preservation (`TST-SET-105.2`)*:
  • Verify that asynchronous emissions from `repository.initialWorkoutLoaded` adopt the new cluster's formatted name when `userManuallyChangedWorkoutName` is `false`.
  3. *Unassign Cluster Name Reversion (`TST-SET-105.3`)*:
  • Verify that calling `unassignCluster()` resets `clusterId = -1L`, `clusterName = null`, resets `userManuallyChangedWorkoutName = false`, and restores the fallback unclustered workout name.
  4. *User Manual Override Protection (`TST-SET-105.4`)*:
  • Verify that calling `onWorkoutNameChanged(...)` after cluster selection sets `userManuallyChangedWorkoutName = true`, and subsequent repository updates do NOT overwrite the manually entered name.
  5. *New Cluster Creation Name Synchronization (`TST-SET-105.5`)*:
  • Verify that calling `createNewCluster(...)` immediately sets `workoutName` to the formatted name and resets `userManuallyChangedWorkoutName` to `false`.

### TST-SET-106: **Polar H10 Internal RR Recording & GATT Arbitration Test Suite** [Verified]
- **Requirement(s)**: `REQ-SET-104`
- **Component**: `PolarH10OfflineController.kt`, `DevicesDatabaseManager.java`, `EditDeviceDialog.kt`, `EditDeviceViewModel.kt`, `TrackerService.java`
- **Jira**: `ATT-7762`
- **Status**: Verified
- **Test Description**:
  1. *PolarH10OfflineController Arming & Clock Sync Lifecycle Test (`TST-SET-106.1`)*:
  • Verify that starting internal recording invokes clock synchronization (`setLocalTime`), removes stale exercise slots, and triggers `startRecording(SampleType.RR)`.
  • Verify that subsequent calls during an active workout session are ignored (fringe-flap invariant).
  2. *GATT Arbitration & Connection Hand-off Test (`TST-SET-106.2`)*:
  • Verify clean GATT arbitration between standard 0x180D BLE streaming in `BTLEHeartRateDevice` and Polar BLE SDK PFTP operations without GATT collision or resource deadlock.
  3. *Android 8–12 Compatibility & Runtime API Guard Test (`TST-SET-106.3`)*:
  • Verify that on devices with `Build.VERSION.SDK_INT < 33`, Polar BLE SDK classes are not invoked, and standard BLE heart rate acquisition functions without runtime crashes.
  4. *DevicesDbHelper Version 13 Migration Parity Test (`TST-SET-106.4`)*:
  • Verify that upgrading a database from `DB_VERSION = 12` to `13` adds column `polarH10OfflineRecording` with `DEFAULT 1`.
  • Verify that a fresh database initialized via `onCreate()` contains the identical table schema, column types, and constraints as an upgraded database.
  5. *EditDeviceDialog & ViewModel Toggle Integration Test (`TST-SET-106.5`)*:
  • Verify that the "Polar H10 Backup Recording" switch is displayed strictly for devices of type `HRM` matching Polar H10 identifiers.
  • Verify that toggling the switch updates `EditDeviceViewModel` state and persists changes to `DevicesDatabaseManager`.
  6. *9-Language Localization Parity Audit (`TST-SET-106.6`)*:
  • Verify 100% presence and parity for all Polar H10 backup recording strings across all 9 supported application locales (`values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`).

### TST-SET-107: **Polar H10 Offline HR/RR Workout Stop Backfill & Export Gating Test Suite** [Verified]
- **Requirement(s)**: `REQ-SET-105`
- **Component**: `PolarH10OfflineController.kt`, `TrackerService.java`, `WorkoutSamplesDatabaseManager.java`, `WorkoutRrDatabaseManager.kt`, `HrvPostWorkoutRecomputer.kt`, `ExportManager.java`
- **Jira**: `ATT-7918`
- **Status**: Verified (ATT-7918)
- **Test Description**:
  1. *Zero-Gap Fast Path Evaluation Test (`TST-SET-107.1`)*:
  • Verify that when live BLE 0x180D telemetry has zero missing seconds and zero `ARTIFACT_GAP` entries, `PolarH10OfflineController.stopAndBackfillWorkout()` stops and removes the H10 exercise without executing a multi-second PFTP sample download.
  2. *Stop & Fetch Backfill Integration Test (`TST-SET-107.2`)*:
  • Verify that when gaps exist, `PolarH10OfflineController` pauses `MyBTLEDevice` GATT, fetches stored RR intervals via `fetchExercise()`, anchors them to start time, reconstructs 1-Hz HR samples (`round(60000.0 / rrMs)`), and patches missing seconds in `WorkoutSamples.db` and beats in `WorkoutRr.db` idempotently.
  3. *Post-Backfill Recomputation & Slot Cleanup Test (`TST-SET-107.3`)*:
  • Verify that `HrvPostWorkoutRecomputer.recomputeWorkoutBlocking()` updates `WorkoutSummaries.db` extrema upon successful backfill, and `removeExercise()` on the H10 is invoked strictly after SQLite commit.
  4. *Export Pipeline Gating Test (`TST-SET-107.4`)*:
  • Verify that `ExportManager.workoutFinished()` and automated file/cloud exports are deferred in `TrackerService.endWorkout()` until backfill completes, ensuring exported files contain complete HR/HRV data.
  5. *Bounded Timeout Fallback Test (`TST-SET-107.5`)*:
  • Verify that a 30-second bounded timeout gracefully terminates the backfill attempt, resumes GATT, and releases downstream exports with partial live data if the strap is powered off or unreachable.
  6. *9-Language Localization Audit (`TST-SET-107.6`)*:
  • Verify 100% presence and parity for all Polar H10 sync notification strings across all 9 supported application locales (`values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`).

### TST-SET-108: **Polar H10 Aftermath Retry Sync Banner Test Suite** [Verified]
- **Requirement(s)**: `REQ-SET-106`
- **Component**: `WorkoutSummariesDatabaseManager.java`, `PolarH10OfflineController.kt`, `WorkoutData.kt`, `WorkoutDataMapper.kt`, `TrackOnMapScreen.kt`, `PolarH10RetrySyncBanner.kt`, `WorkoutRepository.kt`, `ExportManager.java`
- **Jira**: `ATT-7919`
- **Status**: Verified (ATT-7919)
- **Test Description**:
  1. *Stop-Time Fallback & Pending Metadata Persistence (`TST-SET-108.1`)*:
  • Verify that when `PolarH10OfflineController.stopAndBackfillWorkout()` times out or fails, `TrackerService` records `polarSyncPending = 1` and exercise metadata in `WorkoutSummaries.db` without crashing or blocking finalization.
  2. *Database v31 Migration & Parity Test (`TST-SET-108.2`)*:
  • Verify that upgrading `WorkoutSummaries.db` from v30 to v31 adds pending sync columns with `DEFAULT 0` for `polarSyncPending`, matching fresh `onCreate()` schema.
  3. *M3 Aftermath Retry Banner Rendering & 9-Locale Parity (`TST-SET-108.3`)*:
  • Verify that `PolarH10RetrySyncBanner` is displayed in `TrackOnMapScreen` when `polarSyncPending == 1`.
  • Verify 100% presence and parity for all banner strings across all 9 supported application locales (`values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`) without emojis.
  4. *Deferred Wake, Backfill & Re-Export Integration (`TST-SET-108.4`)*:
  • Verify that tapping "Sync Now" wakes the strap, executes PFTP fetch for the matching exercise ID, patches `WorkoutSamples.db` and `WorkoutRr.db`, recomputes HRV summaries, clears `polarSyncPending = 0`, calls `removeExercise()` on the strap strictly after DB commit, and triggers `ExportManager.exportWorkout()`.
  5. *Dismiss Banner Action Test (`TST-SET-108.5`)*:
  • Verify that tapping "Dismiss" updates `WorkoutSummaries.db` setting `polarSyncPending = 0` and does NOT invoke `removeExercise()` on the strap.








### TST-SET-109: **Swimming SportType Template, Expert Activation, Pool Length & FIT Export Test Suite** [Verified]
- **Requirement(s)**: `REQ-SET-107`
- **Component**: `SportTypeDatabaseManager.java`, `SportTypesRepository.kt`, `DatabaseSchemaRegistry.kt`, `TuningPreferencesDataStore.kt`, `SettingsDataStoreJavaHelper.kt`, `AdvancedTuningAccordion.kt`, `AdvancedTuningScreen.kt`, `SwimmingSection.kt`, `FitFileWriter.java`, `res/values*/strings_swimming.xml`
- **Jira**: `ATT-7999`
- **Status**: Verified (ATT-7999)
- **Test Description**:
  1. *Fresh DB Cleanliness & Zero Walk Collision (`TST-SET-109.1`)*:
  • Verify fresh `SportType.db` v3 `onCreate()` seeds strictly 5 defaults (no `Swimming`), and `DatabaseSchemaRegistry` references `SportTypeDbHelper.DB_VERSION`.
  • Verify with active `SWIM` row (`minSpeed = 0.0, maxSpeed = 0.0`), `getSportTypesIdList(BSportType.UNKNOWN, 1.4)` and `0.0` never return the `SWIM` row ID.
  2. *Idempotent `ensureSwimSportTypeExists()` & Locale Mapping (`TST-SET-109.2`)*:
  • Verify calling `ensureSwimSportTypeExists()` creates one `SWIM` row (`stravaName = "Swim"`, `gcName = "swim"`, `tcxName = "Other"`, `minSpeed = 0.0`, `maxSpeed = 0.0`, `is_placeholder = 0`) and subsequent calls return the same ID without duplicates.
  • Verify existing user rows with `stravaName = "Swim"` or `gcName = "swim"` are reused without duplication.
  3. *Expert Settings DataStore, UI Contract & 9-Locale Parity (`TST-SET-109.3`)*:
  • Verify `TuningConfig` defaults (`25.0f`, `PoolLengthUnit.METERS`), clamping (`10f..100f`), `ALL_KEYS` membership, and `resetToDefaults()`.
  • Verify `TuningDesignGuidelinesContractTest`, `AdvancedTuningAccordionTest`, `AdvancedTuningScreenTest`, and `TranslationParityTest` pass across all 9 locales with zero emojis.
  4. *FIT `Sport.SWIMMING`, `SubSport.LAP_SWIMMING` & Pool Length Export (`TST-SET-109.4`)*:
  • Verify `FitFileWriter` round-trip decode encodes `Sport.SWIMMING`, `SubSport.LAP_SWIMMING`, `pool_length` (`25.0f` / `22.86f`), and `pool_length_unit` (`METRIC` / `STATUTE`) when `tcxName = "Other"` and `stravaName = "Swim"` or `gcName = "swim"`.
  5. *On-Device / Emulator UI Render Check (`TST-SET-109.5`)*:
  • Capture screenshot on `emulator-5554` verifying Expert Settings `Swimming` accordion displays Material 3 activation toggle, pool length segmented presets (`25 m`, `50 m`, `25 yd`, custom), and clean layout.

---

### TST-SET-110: **Polar Verity Sense Offline HR Recording Controller & EditDeviceDialog Test Suite** [Verified]
- **Requirement(s)**: `REQ-SET-108`
- **Component**: `PolarOfflineGattCoordinator.kt`, `PolarH10OfflineController.kt`, `PolarVeritySenseOfflineController.kt`, `PolarOfflineCapabilityStore.kt`, `EditDeviceDialog.kt`, `TrackerService.java`, `res/values*/strings_devices.xml`
- **Jira**: `ATT-7923`
- **Status**: Verified (ATT-7923)
- **Test Description**:
  1. *Device Detection & API Level Guards (`TST-SET-110.1`)*:
  • Verify `isPolarVeritySenseDevice()` matches `"Polar Sense ..."`, `"Verity Sense"`, `"Polar Verity Sense"`, `"Polar OH1"`, and cached PFTP offline capability without misclassifying Polar H10.
  • Verify `PolarVeritySenseOfflineController` returns immediately without SDK calls when API < 33 or `polarH10OfflineRecording == false`.
  2. *SDK Arming Sequence & Pre-Existing HR Cleanup (`TST-SET-110.2`)*:
  • Verify `onWorkoutStarted()` pauses GATT, awaits `FEATURE_POLAR_OFFLINE_RECORDING` & `FEATURE_POLAR_DEVICE_TIME_SETUP`, calls `setLocalTime()`, inspects `getOfflineRecordingStatus()` (invoking `stopOfflineRecording(..., HR)` first if `HR` is active), calls `startOfflineRecording(id, HR, null, null)`, and always invokes `resumeGattOperations()`.
  3. *Mid-Workout Fringe-Flap Invariant & `SYSTEM_BUSY` Resilience (`TST-SET-110.3`)*:
  • Verify repeated `onWorkoutStarted()` while `isArmed == true` makes zero additional PFTP/SDK calls until `onWorkoutStopped()`.
  • Verify `BlePsFtpUtils.PftpResponseError(PbPFtpError.SYSTEM_BUSY)` never throws, resets `isArmed = false`, records diagnostic error, and resumes standard GATT.
  4. *Stop, Fetch & Remove Coroutine API (`TST-SET-110.4`)*:
  • Verify `stopAndFetchOfflineHr()` stops `HR`, collects `listOfflineRecordings()`, resolves `PolarOfflineRecordingData.HrOfflineRecording`, and `removeOfflineRecord()` delegates `api.removeOfflineRecord()`.
  5. *`EditDeviceDialog` UI, 9-Locale Parity & Screenshot (`TST-SET-110.5`)*:
  • Verify `EditDeviceDialog` Compose test confirms Verity Sense displays HR-only toggle (default ON on API 33+, disabled with hint on `< 33`), `TranslationParityTest` passes with zero emojis across all 9 locales, and screenshot on `emulator-5554` verifies layout.

---

### TST-SET-111: **Polar Verity Sense Stop-Time HR Backfill & Aftermath Retry Sync Test Suite** [Verified]
- **Requirement(s)**: `REQ-SET-109`
- **Component**: `PolarVeritySenseOfflineController.kt`, `PolarOfflineGattCoordinator.kt`, `TrackerService.java`, `PolarH10RetrySyncBanner.kt`, `WorkoutSamplesDatabaseManager.java`, `WorkoutSummariesDatabaseManager.java`, `res/values*/strings_devices.xml`
- **Jira**: `ATT-7924`
- **Status**: Verified (ATT-7924)
- **Test Description**:
  1. *Stop-Time Backfill, 1-Hz Alignment & Gap-Only Patching (`TST-SET-111.1`)*:
  • Verify `stopAndBackfillWorkout()` preserves existing valid live `HR > 0`, GPS, cadence, and power samples while patching ONLY missing `HR` rows in `WorkoutSamples.db` using `HrOfflineRecording.startTime` + 1-Hz `samples` timestamps.
  • Verify `onWorkoutStopped()` preserves armed state for `TrackerService.endWorkout()` and `stopAndBackfillWorkout()` clears `isRecordingArmed()` in `finally` before exports run.
  2. *Zero-Gap Fast Path (`TST-SET-111.2`)*:
  • Verify when `hasMissingHeartRateSamples()` is `false`, `shouldBackfill()` returns `false`, `getOfflineRecord()` is NOT invoked, active `HR` stops, and matched flash entry is removed.
  3. *Strict HRV Integrity Invariant (`TST-SET-111.3`)*:
  • Verify `WorkoutRr.db` (`WorkoutRrRepository`) receives zero inserts and `HrvPostWorkoutRecomputer` is NEVER triggered during stop-time or deferred Verity backfill.
  4. *HR Summary Recomputation & Single-Entry Flash Safety (`TST-SET-111.4`)*:
  • Verify `SensorType.HR` extrema (`MIN`, `MAX`, `AVG`) update in `WorkoutSummaries.db` (`TABLE_EXTREMA_VALUES`) and `removeOfflineRecord()` deletes ONLY the matched `PolarOfflineRecordingEntry` strictly AFTER SQLite commit (skipping deletion if commit throws and preserving unrelated flash entries).
  5. *Aftermath Retry Pending, Copy, 9-Locale Parity & UI Screenshot (`TST-SET-111.5`)*:
  • Verify unreachable Verity Sense persists `setPolarSyncPending` with `verity:` tag, `PolarH10RetrySyncBanner` renders optical Blue Sensor Mode copy across all 9 locales (zero emojis), *Sync Now* executes `PolarVeritySenseOfflineController.syncPendingWorkout()` cleanly, and visual screenshot on `emulator-5554` verifies UI.

---

### TST-SET-112: **Polar Verity Sense Swim Mode Session Reader, Turn Extraction & SYSTEM_BUSY Test Suite** [Verified]
- **Requirement(s)**: `REQ-SET-110`
- **Component**: `PolarVeritySenseOfflineController.kt`, `PolarOfflineGattCoordinator.kt`, `PolarH10RetrySyncBanner.kt`, `res/values*/strings_devices.xml`
- **Jira**: `ATT-8000`
- **Status**: Verified (ATT-8000)
- **Test Description**:
  1. *Deterministic Swim Session Parsing & 1-Hz HR Extraction (`TST-SET-112.1`)*:
  • Verify `PolarTrainingSession` with `PbSportIdentifier == 103` is parsed as pool swimming (`isPoolSwimSession = true`), yielding 1-Hz `heartRateSamples` and wall turn timestamps.
  2. *FTU Initialization for Factory-Fresh Sensor (`TST-SET-112.2`)*:
  • Verify when `isFtuDone()` returns `false`, `doFirstTimeUse()` is invoked before reading training sessions.
  3. *SYSTEM_BUSY Mode-Switch Guidance & Tagged Pending State (`TST-SET-112.3`)*:
  • Verify when `SYSTEM_BUSY` is thrown during stop or sync, pending state `verity:swim_busy:<targetId>` is saved, and `PolarH10RetrySyncBanner` renders localized button-exit instructions in 9 locales (zero emojis).
  4. *Post-Commit Flash Session Removal & Unrelated Session Preservation (`TST-SET-112.4`)*:
  • Verify `deleteTrainingSession()` is invoked strictly AFTER SQLite commit and only for the matched session reference, leaving unrelated sessions on flash untouched.
  5. *9-Locale Localization Parity & Tier 1 Unit Regression (`TST-SET-112.5`)*:
  • Verify `TranslationParityTest` passes with zero missing keys and zero emojis across all 9 locales, and targeted unit test suite passes cleanly.

---

### TST-SET-113: **Polar Verity Sense Swim Mode Backfill, Auto SportType & Aftermath Adjustment Test Suite** [Verified]
- **Requirement(s)**: `REQ-SET-111`
- **Component**: `PolarVeritySenseOfflineController.kt`, `SportTypesRepository.kt`, `LapsDatabaseManager.java`, `WorkoutSamplesDatabaseManager.java`, `WorkoutSummariesDatabaseManager.java`, `WorkoutDetailTabbedContent.kt`, `res/values*/strings_swimming.xml`
- **Jira**: `ATT-8001`
- **Status**: Verified (ATT-8001)
- **Test Description**:
  1. *Automatic Swim SportType Provisioning & Assignment (`TST-SET-113.1`)*:
  • Verify `ensureSwimSportTypeExists()` creates/resolves the Swimming sport type and assigns `BSportType.SWIMMING` and its `sportId` to the workout summary during swim session backfill.
  2. *Pool-Length Distance, Speed & Lap SQLite Backfill (`TST-SET-113.2`)*:
  • Verify backfill calculates cumulative distance from `poolLengthMeters * turnCount`, writes `DISTANCE_m`, `SPEED_mps`, and `LAP_NR` samples into `WorkoutSamples.db`, and persists valid non-zero laps in `LapsDatabaseManager` (honoring ATT-896).
  3. *Export Hold & Summary Extrema Recalculation (`TST-SET-113.3`)*:
  • Verify `WorkoutSummariesDatabaseManager` updates total distance, average/max speed, and HR zone times before `ExportManager.workoutFinished()` is released, while `WorkoutRr.db` receives zero entries.
  4. *Aftermath Pool-Length & Turn Correction Recalculation (`TST-SET-113.4`)*:
  • Verify switching pool length (e.g. 25m to 50m) or adjusting length count (`+1`/`-1`) in `SwimWorkoutAdjustCard` recalculates workout total distance, pace, and lap splits in SQLite, refreshes `WorkoutRepository`, and triggers export invalidation.
  5. *9-Locale Localization Parity & Visual UI Inspection (`TST-SET-113.5`)*:
  • Verify `TranslationParityTest` passes with zero missing keys and zero emojis across all 9 locales for `SwimWorkoutAdjustCard`, and UI screenshot verifies layout on `emulator-5554`.

---

### TST-SET-114: **Polar Live IMU Streaming Bridge, Session Arbitration & Cadence Priority Test Suite** [Verified]
- **Requirement(s)**: `REQ-SET-112`
- **Component**: `PolarOnlineStreamingBridge.kt`, `PolarOfflineGattCoordinator.kt`, `DeviceManager.java`, `MySensorManager.java`, `DevicesDatabaseManager.java`, `DatabaseSchemaRegistry.kt`, `DeviceDataRepository.kt`, `EditDeviceDialog.kt`, `res/values*/strings_devices.xml`
- **Jira**: `ATT-8016`
- **Status**: Verified
- **Test Description**:
  1. *`Devices.db` v13->v14 Migration & Schema Registry Parity (`TST-SET-114.1`)*:
  • Verify `DevicesDbHelper.DB_VERSION == 14`, `onUpgrade(13, 14)` adds the live IMU column with `DEFAULT 0` without altering existing rows, `onCreate` matches `onUpgrade`, and `DatabaseSchemaRegistry.getMaxSupportedVersion("Devices.db") == 14`.
  2. *EditDeviceDialog `@RequiresApi(33)` Toggle & 9-Locale Parity (`TST-SET-114.2`)*:
  • Verify `EditDeviceDialog` shows the live IMU toggle only for Polar H10 / Verity Sense (API 33+ enabled, `< API 33` disabled with hint), persists opt-in via `DeviceDataRepository`, passes `TranslationParityTest` across all 9 locales, and passes Stage 4 PNG screenshot inspection (`Rule 8.5`).
  3. *Persistent Single-GATT Session, HR/RR & ACC/GYRO Streaming (`TST-SET-114.3`)*:
  • Verify `DeviceManager` routes enabled Polar H10 / Verity Sense to `PolarOnlineStreamingBridge` (and falls back to `BTLEHeartRateDevice` when disabled/`< API 33`), `PolarHrData` (`hr`, `rrsMs`, `rrAvailable`) dispatches `HR`, `RR_INTERVAL`, and `RrBeatSource` beats, and `startAccStreaming`/`startGyroStreaming` + `setAutomaticReconnection(true)` operate cleanly.
  4. *Offline Session Reuse & Verity Sense `ACC` Mutual Exclusion (`TST-SET-114.4`)*:
  • Verify `PolarOfflineGattCoordinator` reuses the active `PolarOnlineStreamingBridge` `PolarBleApi` session during offline PFTP start/stop/backfill without opening a second GATT client, and rejects concurrent `PolarOfflineRecordingApi` + `PolarOnlineStreamingApi` `ACC` activation on Verity Sense.
  5. *`BSportType.RUN` Gating & Cadence Priority Arbitration (`TST-SET-114.5`)*:
  • Verify `PolarOnlineStreamingBridge` publishes `CADENCE`/`STRIDES` only for `BSportType.RUN` (including `TTSportType.WALK`), emits zero cadence for `BSportType.BIKE`, and yields `SensorType.CADENCE` in `MySensorManager` whenever `RUN_SPEED`, `BIKE_CADENCE`, `BIKE_SPEED_AND_CADENCE`, or `BIKE_POWER` is active.

---

### TST-SET-115: **Expert Settings HRV Artifact Gate Relocation, Morning Checks Section & DFA α1 Info-Card Removal Verification Suite** [Verified]
- **Requirement(s)**: `REQ-SET-113` (`REQ-SET-113.1`..`REQ-SET-113.4`)
- **Component**: `AdvancedTuningAccordionTest.kt`, `AdvancedTuningModularityTest.kt`, `AdvancedTuningScreenTest.kt`, `AdvancedTuningVisualContractTest.kt`, `PhysiologyModelsVisibilityContractTest.kt`, `TuningDesignGuidelinesContractTest.kt`, `TranslationParityTest.kt`, `AdvancedTuningSectionsScreenshotTest.kt`
- **Jira**: `ATT-8117`, `ATT-8468`
- **Status**: Verified
- **Test Description**:
  1. *HRV Artifact Gate Relocation, DataStore Key Invariance & DFA α1 Info-Card Removal (`TST-SET-115.1`, [Verified] ATT-8468, `PhysiologyModelsVisibilityContractTest.kt`)*:
  • Verify `PhysiologyModelsSection.kt` omits `tuning_hrv_artifact_gate_title` and all 7 Morning Check controls, and omits the static DFA α1 info-card (`R.string.tuning_physiology_hrv_zones_link`), redundant trailing divider, and unused `Card`/`CardDefaults` imports, while retaining `showRpeAndFeel`.
  • Verify `SensorsGpsFilterSection.kt` hosts `tuning_hrv_artifact_gate_title` (`3.0%..5.0%`) and both `AdvancedTuningScreen.kt` and `AdvancedTuningDialog.kt` wire `hrvArtifactGateThresholdPercent` without altering `TuningPreferencesDataStore` keys.
  • Verify `PhysiologyModelsVisibilityContractTest.kt` asserts `assertFalse` on `tuning_physiology_hrv_zones_link` in `PhysiologyModelsSection.kt`.
  2. *`TuningSection.MORNING_CHECK` & `MorningCheckSection` Wiring (`TST-SET-115.2`, `AdvancedTuningAccordionTest.kt`, `AdvancedTuningModularityTest.kt`, `AdvancedTuningScreenTest.kt`, `AdvancedTuningVisualContractTest.kt`)*:
  • Verify `TuningSection.values()` contains all 12 canonical entries including `MORNING_CHECK`.
  • Verify `TuningSubtitleFormatter.formatMorningCheckSubtitle(...)` produces deterministic live summaries and `MorningCheckSection` exists as a public composable mounted in both `AdvancedTuningScreen` and `AdvancedTuningDialog` with all 7 Morning Check controls.
  3. *Design Guidelines Contract Verification (`TST-SET-115.3`, `TuningDesignGuidelinesContractTest.kt`)*:
  • Verify `SensorsGpsFilterSection.kt`, `PhysiologyModelsSection.kt`, and `MorningCheckSection.kt` strictly use `TuningToggleItem`, `SubordinateTuningToggleItem`, `TuningSliderItem`, and `HorizontalDivider` with zero `FilterChip`, raw `Switch(`, or nested `ElevatedCard(`.
  4. *9-Language Localization Parity (`TST-SET-115.4`, `TranslationParityTest.kt`)*:
  • Verify 100% translation parity, matching format specifiers, and zero emojis across all 9 locales (`EN, DE, ES, FR, IT, JA, NL, PL, PT`) after symmetric removal of `tuning_physiology_hrv_zones_link`.
  5. *Visual Compose Screenshot Verification (`TST-SET-115.5`, [Verified] ATT-8468, `AdvancedTuningSectionsScreenshotTest.kt`, Rule 8.5)*:
  • Render `SensorsGpsFilterSection`, `MorningCheckSection`, and `PhysiologyModelsSection` in Robolectric Compose, capture `docs/engineering/walkthroughs/img/ATT-8117_tuning_sections.png`, and inspect via `read_file` against the 4-point visual checklist verifying the clean layout without the DFA α1 info-card.

---

### TST-SET-117: **Supporter Tier Self-Selection Anchoring & Post-Onboarding Garage Nudge Verification Suite** (`ATT-8108`) [Verified]
- **Requirement(s)**: `REQ-SET-115` (`REQ-SET-115.1`..`REQ-SET-115.4`)
- **Component**: `GarageLeagueDetector.kt`, `SettingsDataStore.kt`, `MainActivityWithNavigation.kt`, `ATrainingTrackerApp.kt`, `SupporterScreen.kt`, `SupporterComponents.kt`, `SupporterDebugSection.kt`, `GarageLeagueDetectorTest.kt`, `SupporterPlanCardHierarchyTest.kt`, `SupporterCopyContractTest.kt`, `GarageUpgradeNudgeDialogContractTest.kt`, `TranslationParityTest.kt`, `SupporterCardsScreenshotTest.kt`
- **Jira**: `ATT-8108`
- **Status**: Verified
- **Test Description**:
  1. *Warm-Engagement Workout Gate & Unconsumed Quota (`TST-SET-117.1`, `GarageLeagueDetectorTest.kt`)*:
  • Verify `GarageNudgeEvaluator.shouldTriggerNudge(...)` and `GarageLeagueDetector.evaluateNudge()` return `null` whenever `finishedWorkoutCount < 8` (e.g., `0` or `7`), even with Gold/Diamond hardware or Strava Pro, without incrementing nudge counts or setting cooldown timestamps, while `SensorQuotaReconciliationManager` continues enforcing the 2-sensor free-tier quota truthfully.
  2. *Bounded Seasonal Cooldown & Legacy Flag Migration (`TST-SET-117.2`, `GarageLeagueDetectorTest.kt`)*:
  • Verify initial nudge triggers once `finishedWorkoutCount >= 8`; after dismissal, verify re-nudge stays suppressed when only `>= 180 days` OR only `>= 25` workouts have elapsed, triggers only when **both** `>= 180 days` AND `>= 25` additional finished workouts have passed, stops permanently once `nudgeCount >= 3`, migrates legacy `hasShownGoldGarageNudge`/`hasShownDiamondGarageNudge` safely, and resets cleanly via `SettingsDataStore.resetGarageNudgeFlags()`.
  3. *Post-Workout Evaluation & Jubilee / Quota Priority Suppression (`TST-SET-117.3`)*:
  • Verify post-workout `TRACKING_FINISHED_INTENT` nudge evaluation triggers for Day-0-paired hardware upon crossing `finishedWorkoutCount >= 8`, and strictly yields (remains `null`/suppressed) when `FirstJubileeCelebrationDialog` (`firstJubileeCelebrationState != null`) or `SensorQuotaReconciliationManager` (`keepTwoSensorsEvent != null`) is active.
  4. *Supporter Tier Persona Subtitles, Garage-Match Badge & Detekt Modularity (`TST-SET-117.4`, `SupporterPlanCardHierarchyTest.kt`, `SupporterCopyContractTest.kt`)*:
  • Verify all 5 `SupporterBasePlan.PLANS` IDs/prices remain unchanged (`REQ-SET-086.1`), `SupporterScreen` resolves matching `GarageLeague` badge eligibility (`GOLD`/`DIAMOND` strictly above `effectiveTier`), `Rule 38` (`maxLines = 1, softWrap = false`) and `Rule 73` stacked dialog contracts hold, and `SupporterPlanCard` helper extraction resolves `LongMethod`.
  5. *9-Locale Localization Parity, Zero-Emoji Hygiene & Rule 8.5 Compose PNG (`TST-SET-117.5`, `TranslationParityTest.kt`, `SupporterCardsScreenshotTest.kt`)*:
  • Execute `TranslationParityTest` and `SupporterCopyContractTest` asserting all 6 new `strings_monetization.xml` keys exist across all 9 locales with zero decorative emojis (`ATT-5400`) and zero defensive buzzwords (`ATT-5659`).
  • Render `SupporterPlanCard` variants (persona subtitles, `Silver` Recommended anchor, `Gold`/`Diamond` Garage-Match badge) via Robolectric Compose to `docs/engineering/walkthroughs/img/ATT-8108_supporter_cards.png` and inspect with `read_file` against the 4-point visual checklist (`Rule 8.5`).





---

### TST-SET-116: **Polar Live Running Cadence & Stride Counter Test Suite (`ATT-8017`)** [Verified]
- **Requirement(s)**: `REQ-SET-114`
- **Component**: `PolarLiveRunningCadenceEstimator.kt`, `PolarOnlineStreamingBridge.kt`, `FitFileWriter.kt`
- **Jira**: `ATT-8017`
- **Status**: Verified
- **Test Description**:
  1. *Polar H10 Sternum `ACC` Accuracy & Autocorrelation (`TST-SET-116.1`)*:
  • Verify `PolarLiveRunningCadenceEstimator` processes synthetic H10 `ACC` streams (50/100 Hz) across `60–100` strides/min (`120–200` steps/min) using `1.2–4.0 Hz` band-pass + adaptive peak + `5s` autocorrelation within `±2` strides/min without harmonic doubling/halving.
  2. *Polar Verity Sense `GYRO+ACC` Fusion & Rotation Invariance (`TST-SET-116.2`)*:
  • Verify `PolarLiveRunningCadenceEstimator` processes synthetic 52 Hz Verity Sense `ACC+GYRO` arm-swing streams (`0.6–2.0 Hz`) within `±2` strides/min across `60–100` strides/min and arbitrary 3D sensor rotations.
  3. *Standstill Zeroing & 1-Hz Emission (`TST-SET-116.3`)*:
  • Verify `SensorType.CADENCE` emits steady `1 Hz` updates during motion and drops cleanly to `0.0` within `>= 3s` of standstill via motion-energy gating.
  4. *First-Stride Baseline Seeding (`TST-SET-116.4`)*:
  • Verify `PolarOnlineStreamingBridge.syncCadenceSensorRegistration()` calls `mStridesSensor.accumulate(0)` on `BSportType.RUN` registration so stride `1` is counted immediately without baseline swallowing in `MyIntegerAccumulatorSensor`.
  5. *Canonical Strides/Min FIT Export Parity (`TST-SET-116.5`)*:
  • Verify `PolarOnlineStreamingBridge` emits half-step strides/min matching `ANTRunSpeedDevice`/`BTLERunSpeedDevice` and `FitFileWriter` (`RecordMesg.cadence`/`SessionMesg.avgCadence` `UINT8` serialization without scaling).
---

### TST-SET-118: **Polar H10 200 Hz Running Dynamics & FIT Export Test Suite (`ATT-8018`)** [Verified]
- **Requirement(s)**: `REQ-SET-116`
- **Component**: `PolarH10RunningDynamicsEstimator.kt`, `PolarOnlineStreamingBridge.kt`, `FitFileWriter.kt`, `SensorType.java`
- **Jira**: `ATT-8018`
- **Status**: Verified
- **Test Description**:
  1. *200 Hz Tilted-Axis Alignment, VO Double-Integration & GCT Accuracy (`TST-SET-118.1`)*:
  • Verify `PolarH10RunningDynamicsEstimator` aligns tilted trunk gravity (`~0.25 Hz` LPF), computes drift-free detrended rolling VO (`5.0..13.0 cm` over 5–8 steps), and detects `5 ms`-resolution GCT (`140..400 ms` across `150–195 spm`) within physiological plausibility bounds.
  2. *Walking GCT Suppression, Standstill Reset & 200 Hz PMD Activation (`TST-SET-118.2`)*:
  • Verify `< 130 spm` (`< 65 strides/min`) walking cleanly suppresses GCT emission, standstill/disconnect resets state, and `PolarOnlineStreamingBridge` negotiates `200 Hz` `ACC` exclusively for H10 in `BSportType.RUN` while leaving Verity Sense (`52 Hz`) and non-running sports unchanged.
  3. *SensorType, ActivityType Partition & 9-Language Localization Parity (`TST-SET-118.3`)*:
  • Verify `SensorType.VERTICAL_OSCILLATION` (`cm`), `SensorType.GROUND_CONTACT_TIME` (`ms`), `ActivityType.RUN_SPEED_AND_CADENCE`, `SensorCategoryAndSelectorContractTest`, and 9-locale `TranslationParityTest` (`EN, DE, ES, FR, IT, JA, NL, PL, PT`).
  4. *End-to-End Sample Persistence & Summary Extrema (`TST-SET-118.4`)*:
  • Verify `TrackerService`, `WorkoutDataMapper`, and `WorkoutRepository` (`streamSamples()` & `calculateAndSaveExtrema()`) persist 1-Hz `cm`/`ms` series and Aftermath `AVG`/`MIN`/`MAX` summaries with zero SQLite schema bumps.
  5. *FIT SDK `mm` (`cm * 10f`) & `ms` Record/Lap/Session Encoding (`TST-SET-118.5`)*:
  • Verify `FitFileWriterTest` decodes emitted FIT binaries and asserts exact `cm * 10f` (`mm`) `verticalOscillation`/`avgVerticalOscillation` and `ms` `stanceTime`/`avgStanceTime` across `RecordMesg`, `LapMesg`, and `SessionMesg`.
---

### TST-SET-119: **Polar Raw Telemetry GZIP Sidecar Persistence & Rolling Retention Test Suite (`ATT-8019`)** [Verified]
- **Requirement(s)**: `REQ-SET-117` (`REQ-SET-117.1`..`REQ-SET-117.4`)
- **Component**: `PolarRawTelemetryRecorder.kt`, `PolarRawTelemetryReader.kt`, `PolarRawTelemetryPruner.kt`, `RawSensorRetentionPolicy.kt`, `PolarOnlineStreamingBridge.kt`, `PolarH10OfflineController.kt`, `TrackerService.java`, `WorkoutSummariesDatabaseManager.java`, `WorkoutRepository.kt`, `TuningPreferencesDataStore.kt`, `SensorsGpsFilterSection.kt`, `TranslationParityTest.kt`
- **Jira**: `ATT-8019`
- **Status**: Verified
- **Test Description**:
  1. *Buffered GZIP Sidecar I/O, Bounded Channel & Empty Session Cleanup (`TST-SET-119.1`)*:
  • Verify `PolarRawTelemetryRecorder` writes valid GZIP-compressed CSV data to `files/raw_sensor/<fileBaseName>.polar_raw.csv.gz` via non-blocking bounded `Channel(capacity = 8192)` on `Dispatchers.IO` under simulated 200 Hz burst loads without callback stalling.
  • Verify empty or discarded workout sessions delete the partial sidecar and leave zero orphan files on disk.
  2. *Self-Describing Metadata Header & Round-Trip Replay Parsing (`TST-SET-119.2`)*:
  • Verify `PolarRawTelemetryRecorder` emits `# metadata: {...}` JSON header containing `schemaVersion`, `appVersion`, `sensorModel`, `streamMode`, and sampling rates, followed by column headers.
  • Verify `PolarRawTelemetryReader` parses metadata and reconstructs `ACC`, `GYRO`, `HR`, and `RR` frames with monotonic timestamps (`sensorTimestampNs`, `elapsedMs`), gracefully handling truncated or corrupted GZIP files.
  3. *Workout Cascade Deletion & Background Retention Pruning (`TST-SET-119.3`)*:
  • Verify `WorkoutSummariesDatabaseManager.deleteWorkout()` and `WorkoutRepository.deleteWorkoutCascade()` delete associated `.polar_raw.csv.gz` sidecars upon workout removal (`REQ-DAT-042`).
  • Verify `PolarRawTelemetryPruner` prunes sidecars according to `RawSensorRetentionPolicy` (`DAYS_30`, `DAYS_7`, `KEEP_ALL`, `OFF`) without touching workout summary database records.
  4. *TuningPreferencesDataStore Persistence & 9-Locale Translation Parity (`TST-SET-119.4`)*:
  • Verify `TuningPreferencesDataStore` persists and emits `RawSensorRetentionPolicy` choices with default `DAYS_30`.
  • Run `TranslationParityTest` verifying all 9 locales (`EN`, `DE`, `ES`, `FR`, `IT`, `JA`, `NL`, `PL`, `PT`) define all raw telemetry retention labels and summaries in `strings_devices.xml`.
  5. *Compose UI Ergonomics & Rule 8.5 Visual Screenshot (`TST-SET-119.5`)*:
  • Verify `SensorsGpsFilterSection` displays the retention selector using M3 components fitting 360dp widths across all 9 locales.
  • Verify Stage 4 generates `docs/engineering/walkthroughs/img/ATT-8019_raw_telemetry_settings.png` and passes 4-point visual inspection (`Rule 8.5`).

---

### TST-SET-120: **Process-Wide Ref-Counted `PolarBleApi` Singleton, Callback Multiplexing & Unworn-Sensor Gating Suite (`ATT-8690`)** [Verified]
- **Requirement(s)**: `REQ-SET-118` (`REQ-SET-118.1`..`REQ-SET-118.4`)
- **Component**: `PolarOfflineGattCoordinator.kt`, `PolarOnlineStreamingBridge.kt`, `PolarH10OfflineController.kt`, `PolarVeritySenseOfflineController.kt`, `PolarOfflineGattCoordinatorCoexistenceTest.kt`, `PolarOnlineStreamingBridgeTest.kt`, `PolarH10OfflineControllerTest.kt`, `PolarVeritySenseOfflineControllerTest.kt`
- **Jira**: `ATT-8690`
- **Status**: Verified
- **Test Description**:
  1. *8-Feature Superset Singleton Reuse (`TST-SET-120.1`, `PolarOfflineGattCoordinatorCoexistenceTest.kt`, `PolarH10OfflineControllerTest.kt`, `PolarVeritySenseOfflineControllerTest.kt`)*:
  • Verify with mocked `PolarBleApi` factory (`Rule 5`, offline) that `PolarOfflineGattCoordinator` initializes the shared `PolarBleApi` once with all 8 superset features and that `PolarOnlineStreamingBridge`, `PolarH10OfflineController` (`stopAndBackfillWorkout` & `syncPendingWorkout`), and `PolarVeritySenseOfflineController` reuse it without duplicate/subset `defaultImplementation()` calls.
  2. *Callback Multiplexing & Bidirectional MAC/8-Hex ID Filtering (`TST-SET-120.2`, `PolarOfflineGattCoordinatorCoexistenceTest.kt`, `PolarOnlineStreamingBridgeTest.kt`)*:
  • Verify concurrent `awaitFeaturesReady` callers and sibling `PolarOnlineStreamingBridge` callbacks register with the central multiplexer without overwriting `api.setApiCallback`, match `MAC` <-> `8-hex Polar ID` bidirectionally, and filter `deviceConnected`/`deviceDisconnected` events by target device.
  3. *Ref-Counted Lifecycle & Non-Destructive Release (`TST-SET-120.3`, `PolarOfflineGattCoordinatorCoexistenceTest.kt`, `PolarOnlineStreamingBridgeTest.kt`)*:
  • Verify `api.cleanup()` and `api.shutDown()` are never invoked while sibling controllers or bridges hold active references (`refCount > 0`), and `getActiveSession(polarId)` returns non-null while connected and `null` post-`shutDown()`.
  4. *Active-Device / Unworn-Sensor Gating & Relaxed-Mock Compatibility (`TST-SET-120.4`, `PolarOfflineGattCoordinatorCoexistenceTest.kt`, `PolarH10OfflineControllerTest.kt`, `PolarVeritySenseOfflineControllerTest.kt`)*:
  • Verify `findEnabledPolarDevice()` skips unworn/disconnected Polar devices when another Polar HRM is actively receiving data in `DeviceManager`, while preserving existing test resolution under `null` `DeviceManager` or relaxed DB mocks.

---

### TST-SET-121: **Polar Aftermath Retry Sync Lifecycle Safety & Disconnected Stop-Time Fast-Fail Suite (`ATT-8969`)** [Verified]
- **Requirement(s)**: `REQ-SET-119` (`REQ-SET-119.1`..`REQ-SET-119.3`)
- **Component**: `PolarRetrySyncCoordinator.kt`, `PolarH10RetrySyncBanner.kt`, `PolarVeritySenseOfflineController.kt`, `PolarH10OfflineController.kt`, `PolarOfflineGattCoordinator.kt`, `TrackOnMapAftermathViewModel.kt`, `WorkoutSummariesTabbedScreen.kt`, `WorkoutSummariesListFragment.kt`, `PolarRetrySyncCoordinatorTest.kt`, `PolarH10RetrySyncBannerTest.kt`, `PolarVeritySenseOfflineControllerTest.kt`, `PolarH10OfflineControllerTest.kt`, `TrackOnMapAftermathViewModelTest.kt`
- **Jira**: `ATT-8969`
- **Status**: Verified
- **Test Description**:
  1. *Non-Composition Retry Scope & Disposal Survival (`TST-SET-121.1`, `PolarRetrySyncCoordinatorTest.kt`, `PolarH10RetrySyncBannerTest.kt`)*:
  • Verify triggering *Sync Now* executes `syncPendingPolarWorkout` on the non-composition scope, deduplicates concurrent taps per `workoutId`, survives simulated composition disposal/re-entry without throwing `ForgottenCoroutineScopeException` or flickering state, and exposes reactive `isSyncing`/`errorMessage`/`completion` state.
  2. *CancellationException Propagation (`TST-SET-121.2`, `PolarVeritySenseOfflineControllerTest.kt`, `PolarH10OfflineControllerTest.kt`)*:
  • Verify `CancellationException` thrown during `syncPendingWorkout` or `stopAndBackfillWorkout` is rethrown and never swallowed by `catch (t: Throwable)`.
  3. *Aftermath HR Telemetry Graph Refresh (`TST-SET-121.3`, `PolarRetrySyncCoordinatorTest.kt` / `TrackOnMapAftermathViewModelTest.kt`)*:
  • Verify completion of deferred Polar backfill emits completion / clears `polarSyncPending`, triggering `TrackOnMapAftermathViewModel.loadAftermathData(...)` to reload `telemetryPath` and `hrZoneDistribution` for the active workout.
  4. *Disconnected Stop-Time Fast-Fail & Bounded Connection Timeout (`TST-SET-121.4`, `PolarVeritySenseOfflineControllerTest.kt`, `PolarH10OfflineControllerTest.kt`)*:
  • Verify when `DeviceManager` confirms the target Polar sensor is disconnected (`!isReceivingData()` and `!isDeviceConnected`, `apiOverride == null` / test hook), `stopAndBackfillWorkout()` fast-fails without waiting on `api.waitForConnection()`, records `setPolarSyncPending()` when `needsBackfill == true`, and resets `isRecordingArmed()` to `false`.
  • Verify when `api.waitForConnection()` hangs during stop-time backfill, `STOP_CONNECTION_TIMEOUT_MS` (`5_000 ms`) aborts connection wait gracefully without waiting `30–45 s`, records `setPolarSyncPending()` when `needsBackfill == true`, and resumes GATT.

---

### TST-SET-122: **Polar Online Bridge Offline-Sync Yield & `deviceConnecting` Handshake Grace Suite (`ATT-9175`)** [Verified]
- **Requirement(s)**: `REQ-SET-120` (`REQ-SET-120.1`..`REQ-SET-120.4`)
- **Component**: `PolarOfflineGattCoordinator.kt`, `PolarOnlineStreamingBridge.kt`, `PolarOfflineGattCoordinatorCoexistenceTest.kt`, `PolarOnlineStreamingBridgeTest.kt`
- **Jira**: `ATT-9175`
- **Priority**: High | **Type**: Functional | **Verification Method**: Unit, Integration
- **Test Description**:
  1. *Alias-Aware Ref-Counted Offline Yield & Sibling Isolation (`TST-SET-122.1`, `PolarOfflineGattCoordinatorCoexistenceTest#testWithGattYieldAndPolarApi_pausesAndResumesTargetAliasesWithoutHarmingSibling_TST_SET_122_1`, `PolarOfflineGattCoordinatorCoexistenceTest#testWithGattYieldAndPolarApi_nestedLeasesAndExceptionOrCancellationReleaseCleanly_TST_SET_122_1`, `PolarOfflineGattCoordinatorCoexistenceTest#testWithGattYieldAndPolarApi_aliasExpansionAcrossOverlappingLeases_transitionsPausedSetOnce_TST_SET_122_1`)*: Verify `withGattYieldAndPolarApi()` acquires per-device alias-aware (`MAC` <-> `8-hex`) lease tokens, tracks `pausedOfflineYieldListeners` membership across nested & alias-expanding overlapping leases strictly outside coordinator locks without pausing sibling Polar devices (`REQ-SET-118.3`), releases cleanly in `finally` (including exception & `CancellationException`), and resets in `resetForTesting()`.
  2. *Bridge Pause/Resume, Keep-Alive & Silent-HR Watchdog Deferral (`TST-SET-122.2`, `PolarOnlineStreamingBridgeTest#testOfflineGattYield_pausesReconnectSearchTimeoutAndSilentWatchdogThenResumes_TST_SET_122_2`, `PolarOnlineStreamingBridgeTest#testOfflineGattYield_whenSharedSessionConnectedDuringBackfill_syncsOpenSessionOnResume_TST_SET_122_2`, `PolarOnlineStreamingBridgeTest#testOfflineGattYield_deviceConnectedDuringYield_armsSilentWatchdogOnResumeUnlessDisarmedByHr_TST_SET_122_2`, `PolarOnlineStreamingBridgeTest#testOfflineGattYield_stalePauseCallDoesNotWedgeAndShouldKeepAliveInWaitingForStartMode_TST_SET_122_2`)*: Verify coordinator-backed `isOfflineGattYielded()` cancels active `reconnectJob`/`searchTimeoutJob`/`silentHrWatchdogJob`, blocks competing `connectToDevice()`/`disconnectFromDevice()`, keeps `shouldKeepAliveOnSearchTimeout() == true` in `WAITING_FOR_START` while yielded, ignores stale out-of-order `pauseForOfflineSync()`, arms deferred `silentHrWatchdog` on resume after mid-yield `deviceConnected` (unless disarmed by HR), and syncs already-open shared sessions or resumes reconnect cleanly.
  3. *Single-Shot Bounded `deviceConnecting()` Handshake Grace (`TST-SET-122.3`, `PolarOnlineStreamingBridgeTest#testDeviceConnecting_extendsSearchTimeoutOnceToHandshakeGraceAndPreserves5sAbsentTimeout_TST_SET_122_3`, `PolarOnlineStreamingBridgeTest#testDeviceConnecting_ignoresSiblingAndDuplicateCallbacksAndTimesOutAfterHandshakeGrace_TST_SET_122_3`)*: Verify `deviceConnecting()` for a matching Polar device extends `searchTimeoutJob` once per attempt from `5_000 ms` to `15_000 ms` (logging exact `timeoutMs`), ignores duplicate `deviceConnecting()` callbacks and sibling Polar devices, still times out cleanly after `15_000 ms` if the handshake stalls, and preserves `5_000 ms` timeout when `deviceConnecting()` never fires (`REQ-CON-045.1`).
  4. *Monolith Ceilings & Governance Contracts (`TST-SET-122.4`)*: Verify `ArchitectureConformanceContractTest` (`PolarOnlineStreamingBridge.kt <= 1,500`, `PolarOfflineGattCoordinator.kt <= 1,500`, `0` edits to `PolarVeritySenseOfflineController.kt`/`DeviceManager.java`), `InterfaceSignatureDriftContractTest`, `DeadCodeEliminationContractTest`, `DetailedDesignParityContractTest`, `SourceTextReadingAuditContractTest`, `SystemDataExchangeRequirementsContractTest`, and `SystemLiveTrackingRequirementsContractTest` (`Localization Parity: N/A`).
- **Verification Evidence**: Gate 4 Bounce 1 (`ATT-9384` @ `d2c4953f`, `Rule 7.5`, `0` `app/` drift) — `164/164 PASS` (`0` fail, `0` err, `0` skip) across 15 suites + `:app:assembleDebug` PASS + `0` new production Kotlin warnings (`9/9` verbatim `TST-SET-122` methods verified); `4/4` Python safety linters PASS; `--check-verified-evidence` EXIT `0`.

### TST-SET-123: **`TuningPreferencesDataStore` Round-Trip, `BoundedPref` & `SettingsDataStore` Zone Defaults Verification Suite (`ATT-6048`)** [Verified]
- **Requirement(s)**: `REQ-SET-121` (`REQ-SET-121.1`..`REQ-SET-121.4`)
- **Component**: `TuningPreferencesDataStore.kt`, `SettingsDataStore.kt`, `TuningPreferencesDataStoreRoundTripTest.kt`, `TuningPreferencesDataStoreAutoPauseTest.kt`, `TuningConfigTest.kt`, `SettingsDataStoreZoneAtomicTest.kt`, `DataStoreHotPathContractTest.kt`, `ArchitectureConformanceContractTest.kt`
- **Jira**: `ATT-6048`
- **Status**: Verified
- **Verification Evidence**: Verified (`ATT-6048`, SHA `2038d89b`, Gate 4 `167/167` PASS across 31 suites): `TuningPreferencesDataStoreRoundTripTest.kt` (`saveTuningConfig_roundTripsAllPersistedFieldsIncluding4AutoPauseKeys_TST_SET_123_1`, `boundedPref_dependentClampsZeroRuleAndLegacyMirrors_preserveExactBehavior_TST_SET_123_2`, `boundedPref_contractAndPrivateConstructor_verified_TST_SET_123_3`), `SettingsDataStoreZoneAtomicTest.kt` (`dataStore_centralizedZoneDefaults_matchAllReadAndResetPaths_TST_SET_123_2`), `DataStoreHotPathContractTest.kt` (`3/3`), `ArchitectureConformanceContractTest.kt` (`11/11`).
- **Test Description**:
  1. *Full `TuningConfig` Round-Trip Persistence (`TST-SET-123.1`)*:
  • Construct a `TuningConfig` where all non-derived, non-hardcoded fields (including `startInAutoPauseEnabled`, `autoPauseBikeResumeDebounceSec`, `autoPauseRunResumeDebounceSec`, `autoPauseOtherResumeDebounceSec`) differ from `TuningConfig()` defaults, keeping the 7 `REQ-MAP-050` `mapFollowMe*` fields at defaults, `autoPauseSpeedThresholdKmh`/`autoPauseDebounceSec` consistent with Bike values, and `radarAutoLightControl`/`radarSmartLightAutomation` consistent with `radarLightControlMode`.
  • Invoke `saveTuningConfig(config)`, read `tuningConfigFlow.first()`, and assert via Kotlin reflection over `TuningConfig` properties that every property round-trips identically.
  2. *`BoundedPref`, Dependent Clamps, `0 -> DEFAULT` & Legacy Mirror Characterization (`TST-SET-123.2`)*:
  • Verify out-of-range values clamp identically across `mapPreferencesToConfig`, `saveTuningConfig`, and `update*` via `BoundedPref` descriptors.
  • Verify dependent clamps (`mediumDimFactor < fullDimFactor` clamps `>= clampedFullDim`, `slopeSteepThreshold < slopeFlatThreshold` clamps `>= clampedSlopeFlat`, `dfaA1 T1/T2` gap enforcement), `navigationCueDismissDurationSec == 0 -> DEFAULT`, legacy fallbacks/mirrors (`KEY_AUTO_PAUSE_SPEED_THRESHOLD_KMH`, `KEY_AUTO_PAUSE_DEBOUNCE_SEC`, `KEY_PROFILE_X_AXIS_DOMAIN`, radar modes), `TrainingApplication` cluster sync, and existing `invalidateAutoPauseCache()` behavior remain unchanged.
  3. *Centralized `SettingsDataStore` Zone Defaults & Architectural Contracts (`TST-SET-123.3`)*:
  • Verify `SettingsSnapshot.DEFAULT`, `getDefaultValue`, `calculateProportionalPowerDefaults`, `mapSettingsSnapshot`, `checkAndMigratePowerZones`, and `resetToDefaults` return identical `HR_RUN` (`132, 145, 158, 171`), `HR_BIKE` (`127, 140, 153, 166`), and `PWR_BIKE` (`149, 203, 243, 284, 324, 405`) boundaries.
  • Run `./gradlew :app:testDebugUnitTest --rerun --tests "com.atrainingtracker.trainingtracker.settings.*"` and `ArchitectureConformanceContractTest` confirming `TuningPreferencesDataStore.kt <= 2,410` LoC, non-public constructor, and zero new `runBlocking` in `SettingsDataStore.kt`.

---

### TST-SET-124: **Polar H10 Dynamic `ACC` Rate Renegotiation, Lease/Teardown-Safe Coordinator Restart & Running Dynamics Verification Suite (`ATT-9346`)** [Verified]
- **Requirement(s)**: `REQ-SET-122` (`REQ-SET-122.1`..`REQ-SET-122.4`)
- **Component**: `PolarPmdStreamCoordinator.kt`, `PolarOnlineStreamingBridge.kt`, `PolarH10RunningDynamicsEstimator.kt`, `PolarPmdStreamCoordinatorTest.kt`, `PolarOnlineStreamingBridgeTest.kt`, `PolarH10RunningDynamicsEstimatorTest.kt`, `ArchitectureConformanceContractTest.kt`, `InterfaceSignatureDriftContractTest.kt`
- **Jira**: `ATT-9346`
- **Priority**: High | **Type**: Functional | **Verification Method**: Unit, Integration
- **Test Description**:
  1. *Dynamic H10 `100 <-> 200 Hz` Renegotiation, `STRIDES` Continuity & Unsupported-Rate/Verity Isolation (`TST-SET-124.1`, `PolarOnlineStreamingBridgeTest#testH10LateRunActivation_renegotiatesAcc100To200HzPreservesStridesAndEmitsVoGct_TST_SET_124_1`, `PolarOnlineStreamingBridgeTest#testH10RapidSportFlipDuringRenegotiation_convergesToLatestSport_andUnsupported200HzHasZeroLoop_TST_SET_124_1`)*:
     • Verify when Polar H10 connects in `BSportType.UNKNOWN` or `BIKE`, initial `startAccStreaming` negotiates `100 Hz`, and when `BSportType.RUN` subsequently activates (via `SPORT_TYPE_CHANGED_BY_USER_INTENT`, `MULTISPORT_TRANSITION_INTENT`, or Tier-3 inference observed inside `processAccData()`), `PolarOnlineStreamingBridge` triggers sibling-coroutine renegotiation, releases/re-acquires `ACC_OWNER_ONLINE`, invokes `stopStreaming(ACC)`, restarts `startAccStreaming` at `200 Hz` without self-cancellation, preserves cumulative `STRIDES` & `CADENCE`, and emits valid `VERTICAL_OSCILLATION` (`5.0..13.0 cm`) and `GROUND_CONTACT_TIME` (`140..400 ms`) registered in `MySensorManager` (`ProxySensor` + named H10 sensor).
     • Verify switching `RUN -> BIKE` unregisters VO/GCT/cadence sensors and downgrades H10 `ACC` cleanly from `200 Hz -> 100 Hz`, and rapid `UNKNOWN -> RUN -> BIKE` mid-renegotiation flips converge to `100 Hz`.
     • Verify when `requestStreamSettings` omits `200 Hz` (e.g., `50/100 Hz` only), `syncCadenceSensorRegistration(true)` + repeated `processAccData()` calls perform zero restart loops; verify Polar Verity Sense stays pinned at `52 Hz` without renegotiation or VO/GCT registration.
  2. *Coordinator Single-Flight Renegotiation, Linearizable `launchStream` `preAcquire`, Atomic `slotLock` Handoff, `ACC_OWNER_ONLINE` Lease & Post-Yield Recovery (`TST-SET-124.2`, `PolarPmdStreamCoordinatorTest#testRenegotiateSlot_siblingCoroutineSurvivesCollectorSelfTriggerReleasesAccLeaseAndDedupes_TST_SET_124_2`, `PolarPmdStreamCoordinatorTest#testRenegotiateSlot_cancelledByCancelAllRenegotiationsAndCancelAllStreamsPreventsLateRelaunch_TST_SET_124_2`, `PolarPmdStreamCoordinatorTest#testRenegotiateSlot_atomicSlotLockHandoffAndOnAbortedOnCancelOrYield_TST_SET_124_2`, `PolarPmdStreamCoordinatorTest#testLaunchStream_doesNotSpeculativelyActivateSlotDuringPreAcquire_TST_SET_124_2`, `PolarOnlineStreamingBridgeTest#testH10AccRenegotiation_cancelledOnDisconnectOfflineYieldAndShutdownWithoutLateRestart_TST_SET_124_2`, `PolarOnlineStreamingBridgeTest#testH10OfflineYieldMidStopStreaming_abortsWithoutRestartDuringYieldAndRecovers200HzVoGctOnResume_TST_SET_124_2`, `PolarOnlineStreamingBridgeTest#testH10FeatureReadyDuringOfflineYield_defersAccAndRecovers200HzVoGctOnResume_TST_SET_124_2`, `PolarOnlineStreamingBridgeTest#testH10StaleAbortedRenegotiationAndConcurrentYieldResume_respectsReplacementAndLinearizesPreAcquire_TST_SET_124_2`)*:
     • Verify `PolarPmdStreamCoordinator.launchStream()` never sets `state.active = true` speculatively before `preAcquire()` succeeds; verify `renegotiateSlot()` runs on a sibling `scope` coroutine, enforces single-flight dedup, invokes `onOldSlotCancelled` before `relaunch` (`ACC_OWNER_ONLINE` release/re-acquire), performs the relaunch check/clear/`relaunch()` atomically under `slotLock` (zero TOCTOU), invokes `onAborted` when aborted without interfering with a replacement renegotiation (`!isRenegotiating(ACC)`), and preserves `BufferOverflow.DROP_OLDEST` (`capacity = 64`) and `ERROR_ALREADY_IN_STATE` recovery (`ATT-9161`).
     • Verify `cancelAllStreams()`, `deviceDisconnected()`, `pauseForOfflineSync()` (`ATT-9175`), and `shutDown()` cancel any in-flight renegotiation coroutine immediately so `relaunch` / `startAccStreaming()` is never invoked post-disconnect or while offline PFTP yield is active; verify mid-`stopStreaming()` offline yield (`disconnectOnExit = false`) and one-shot `FEATURE_POLAR_ONLINE_STREAMING` arriving during offline yield both defer ACC while yielded (Dekker store-then-load) and automatically recover `200 Hz` `ACC`, `ACC_OWNER_ONLINE`, and `VO`/`GCT` on `resumeAfterOfflineSync()`.
  3. *Estimator `resetForGap()` Recovery & Realistic Waveform End-to-End Persistence (`TST-SET-124.3`, `PolarH10RunningDynamicsEstimatorTest#testResetForGap_afterLowRate100HzBurst_recoversOn200HzAsymmetricWaveform_TST_SET_124_3`, `PolarOnlineStreamingBridgeTest#testH10LateRunActivation_renegotiatesAcc100To200HzPreservesStridesAndEmitsVoGct_TST_SET_124_1`)*:
     • Verify feeding `100 Hz` (`dt = 10 ms`) followed by a `> 500 ms` gap (`resetForGap()`) and `200 Hz` (`dt = 5 ms`) running samples (including realistic asymmetric stance/flight sternum waveforms) resets `avgDtMs` and immediately emits drift-free `VO` (`5.0..13.0 cm`) and `GCT` (`140..400 ms`) while still suppressing sustained `50/100 Hz` streams and `< 130 spm` walking GCT.
     • Verify emitted `VO` and `GCT` samples propagate through `MySensorManager` / `ProxySensor` cleanly for `WorkoutSamples.db` / CSV / FIT consumption.
  4. *Monolith Ceiling & Governance Contracts (`TST-SET-124.4`)*:
     • Verify `ArchitectureConformanceContractTest` (`11/11`, `PolarOnlineStreamingBridge.kt` `1,492/1,500` LoC), `InterfaceSignatureDriftContractTest` (`3/3`, zero public JVM signature drift), and `verify_requirement_governance.py` (`Localization Parity: N/A`).
- **Verification Evidence**: Gate 4 Bounce 3 (`ATT-9462` @ `939725f4`, `Rule 7.5`, `0` `app/` drift) — `94/94 PASS` (`0` fail, `0` err, `0` skip) across 8 targeted suites (`PolarPmdStreamCoordinatorTest` `10/10`, `PolarOnlineStreamingBridgeTest` `49/49`, `PolarH10RunningDynamicsEstimatorTest` `5/5`, `PolarOfflineGattCoordinatorCoexistenceTest` `10/10`, `PolarDualImuBoundedHeapStressTest` `1/1`, `PolarLiveRunningCadenceEstimatorTest` `5/5`, `ArchitectureConformanceContractTest` `11/11`, `InterfaceSignatureDriftContractTest` `3/3`; `11/11` verbatim `TST-SET-124` methods verified, `0` new production Kotlin warnings); `3/3` Python safety linters PASS; `--check-verified-evidence` EXIT `0`.

---

### TST-SET-125: **Never-Connected BLE Passive Workout Convergence, Scan-Assist Gating & Queue Retry Deduplication Suite (`ATT-9243`)** [Verified]
- **Requirement(s)**: `REQ-SET-123` (`REQ-SET-123.1`..`REQ-SET-123.4`)
- **Component**: `MyBTLEDevice.java`, `DeviceSearchPeriodicRetryCoordinator.kt`, `BLEReconnectRobustnessTest.kt`, `DeviceSearchPeriodicRetryCoordinatorTest.kt`, `DeviceManagerSearchQueueTest.kt`, `MyBTLEDeviceAddressAndSearchTest.kt`, `MyBTLEDeviceLifecycleTest.kt`, `BTLEHeartRateDeviceTest.kt`, `DetailedDesignParityContractTest.kt`, `SystemLiveTrackingRequirementsContractTest.kt`, `ArchitectureConformanceContractTest.kt`
- **Jira**: `ATT-9243`
- **Priority**: High | **Type**: Functional | **Verification Method**: Unit, Integration
- **Test Description**:
  1. *Never-Connected Workout Passive Convergence, Multi-Sensor Quota & `READY -> TRACKING` Edge (`TST-SET-125.1`, `BLEReconnectRobustnessTest#testSearchTimeout_duringTrackingAndPaused_clearsSearchingAndReleasesQueueWhilePersistingReconnect`, `BLEReconnectRobustnessTest#testNeverConnectedThreeAbsentSensors_duringWorkout_convergePassiveAndUpgradeOnWakeUp_TST_SET_125_1`, `BLEReconnectRobustnessTest#testSearchTimeout_readyToTrackingEdge_replacesNonAutoConnectGattAndGuardsConcurrentRaces_TST_SET_125_1`)*:
     • Verify for `3` absent never-connected paired BLE sensors (`!mHasConnectedInSession`) during `TrackingMode.TRACKING` and `PAUSED` that initial `startSearching()` consumes `1` scan per device (`3 <= MAX_SCANS_IN_WINDOW = 4`, `0` scan-quota throttles), and upon `mSearchTimeoutRunnable` each sensor transitions `mState = State.DISCONNECTED`, clears `isSearching`, sets `mIsBackgroundReconnecting = true`, holds passive `connectGatt(..., autoConnect = true, ..., TRANSPORT_LE)`, cancels and does NOT schedule `mReconnectRunnable`, and broadcasts `SEARCHING_STOPPED_FOR_ONE_INTENT(false)`.
     • Verify the `READY -> TRACKING` transition edge: Try 1 starts in `TrackingMode.READY` (opening a direct `autoConnect = false` `BluetoothGatt`), `TrackingMode` changes to `TRACKING` before timeout, and `mSearchTimeoutRunnable` atomically closes/replaces the non-`autoConnect` handle with passive `connectGatt(..., autoConnect = true, ..., TRANSPORT_LE)` without scheduling `mReconnectRunnable` and guarding against concurrent `shutDown` / `STATE_CONNECTED`.
     • Verify mid-ride passive wake-up: once OS-managed `autoConnect = true` delivers `STATE_CONNECTED (GATT_SUCCESS)`, `mHasConnectedInSession` flips to `true`, `mCurrentReconnectDelayMs` resets to `1,000 ms`, and a subsequent mid-workout drop triggers full fast `1s -> 2s -> 4s -> 8s -> 16s` reconnect + active scan-assist.
  2. *Scan-Assist Gating, `attemptBackgroundReconnect` Timeout Skipping, Never-Connected Disconnect Floor & Silent-HR Preservation (`TST-SET-125.2`, `BLEReconnectRobustnessTest#testNeverConnectedWorkoutDisconnect_floorsDelay60sAndSkipsScanAssistAndTimeout_TST_SET_125_2`, `MyBTLEDeviceAddressAndSearchTest#testScanAssistance_onScanResult_connectsDuringDecoupledBackgroundReconnect`, `MyBTLEDeviceLifecycleTest#testResumeGattOperations_whileBackgroundReconnecting_preservesNonBlockingState`, `BTLEHeartRateDeviceTest`)*:
     • Verify a spurious `onConnectionStateChange(STATE_DISCONNECTED, status = 133/8)` on a never-connected sensor during `TRACKING`/`PAUSED` floors `mReconnectRunnable` delay at `>= 60_000 ms`, and running `attemptBackgroundReconnect()` (including via `resumeGattOperations()`) sets `mIsBackgroundReconnecting = true`, stays `State.DISCONNECTED` (`isSearching == false`), skips `mSearchTimeoutRunnable`, adds `0` scans to `sScanTimestamps`, and opens passive `connectGatt(..., autoConnect = true, ..., TRANSPORT_LE)`.
     • Verify a previously connected sensor (`mHasConnectedInSession == true`, including `BTLEHeartRateDevice` `BleHrmSilentConnectionWatchdog` `triggerSilentConnectionReconnect()`) retains `mSearchTimeoutRunnable`, active `startScanAssistance()`, and fast `1s -> 2s -> 4s -> 8s -> 16s -> ...` backoff.
     • Verify `TrackingMode.READY` (`!mHasConnectedInSession && !isMidSessionOrWorkoutActive()`) retains `mSearchTimeoutRunnable` in `attemptBackgroundReconnect()`, `1s -> 2s -> 4s -> ...` disconnect backoff, and clean `disconnectFromGatt()` termination on search timeout without scheduling `mReconnectRunnable`.
  3. *`DeviceSearchPeriodicRetryCoordinator` Keep-Alive Skip & `deviceTries` Cleanup (`TST-SET-125.3`, `DeviceSearchPeriodicRetryCoordinatorTest#testHandleSearchStopped_keepAliveDevice_removesDeviceTriesAndSkipsTries2ToN_TST_SET_125_3`, `DeviceManagerSearchQueueTest#testThreeAbsentKeepAliveDevices_withTries3_searchedOnceAndDeviceTriesCleared_TST_SET_125_3`)*:
     • Verify when `!success && current.shouldKeepAliveOnSearchTimeout() == true` with `tries = 3` (or `tries = 1`), `handleSearchStoppedForCurrentDevice()` removes `current` from `deviceTries`, does NOT re-enqueue `current` into `searchQueue`, does NOT schedule periodic re-search, does NOT call `detachAndGiveUpDevice`, and advances the queue immediately (`3` absent keep-alive BLE devices perform strictly `1` search per device, `0` tries `2..3` re-enqueues).
     • Verify when `current.shouldKeepAliveOnSearchTimeout() == false` (`READY` startup), `tries = 3 -> 2 -> 1` re-enqueue and `tries <= 1` periodic re-search / `detachAndGiveUpDevice` invariants remain unchanged.
  4. *Detailed Design Parity, System Traceability & Architecture Contracts (`TST-SET-125.4`, `DetailedDesignParityContractTest`, `SystemLiveTrackingRequirementsContractTest`, `ArchitectureConformanceContractTest`)*:
     • Verify `DetailedDesignParityContractTest` (`3/3`), `SystemLiveTrackingRequirementsContractTest` (`4/4`), `ArchitectureConformanceContractTest` (`11/11`; `DeviceManager.java <= 1,708` LoC and `PolarOnlineStreamingBridge.kt <= 1,500` LoC untouched; `MyBTLEDevice.MAX_RECONNECT_DELAY_MS == 16000L` and `isMidSessionOrWorkoutActive()` preserved), and `verify_requirement_governance.py --check-living-doc-layout` exit `0` (`Localization Parity: N/A`).
- **Verification Evidence**: Gate 4 (`ATT-9483` @ `85386e63`, `Rule 7.5`, `0` `app/` drift) — `88/88 PASS` (`0` fail, `0` err, `0` skip) across 9 Tier-1 targeted suites (`BLEReconnectRobustnessTest` `16/16`, `MyBTLEDeviceAddressAndSearchTest` `11/11`, `MyBTLEDeviceLifecycleTest` `14/14`, `BTLEHeartRateDeviceTest` `16/16`, `DeviceSearchPeriodicRetryCoordinatorTest` `6/6`, `DeviceManagerSearchQueueTest` `7/7`, `DetailedDesignParityContractTest` `3/3`, `SystemLiveTrackingRequirementsContractTest` `4/4`, `ArchitectureConformanceContractTest` `11/11`; `0` new production compiler warnings); `3/3` Python safety linters PASS; `--check-verified-evidence` EXIT `0`.

---

### TST-SET-126: **Shared `CscRevolutionTracker` & `UINT16` Event-Time Rollover Verification Suite** (`ATT-6042`) [Verified]
- **Requirement(s)**: `REQ-SET-124` (`REQ-SET-124.1`–`REQ-SET-124.4`)
- **Component**: `CscRevolutionTracker.kt`, `BTLEBikeDevice.java`, `BTLEBikePowerDevice.java`, `CscRevolutionTrackerTest.kt`, `BTLEMeasurementParsingTest.kt`, `HardwareHalIngestionIntegrationTest.kt`
- **Jira**: `ATT-6042` | **Priority**: Medium | **Type**: Functional | **Verification Method**: Unit, Integration
- **Test Description**:
  1. *Pure-JVM `CscRevolutionTracker` Rollover & Boundary Unit Tests (`TST-SET-126.1`, `CscRevolutionTrackerTest#testWheel_uint16EventTimeRollover_csc1024AndCps2048_computesWithoutDrop`, `CscRevolutionTrackerTest#testCrank_uint16EventTimeRollover_computesWithoutDrop`, `CscRevolutionTrackerTest#testWheel_uint32RevolutionWrap_andCumulativeDistance_preservesUnsignedDelta`, `CscRevolutionTrackerTest#testCrank_uint16RevolutionWrap_calculates120Rpm`, `CscRevolutionTrackerTest#testWheel_identicalTimestamps_zeroesSpeedAndNullsPaceOnFourthPacket_thenRecovers`, `CscRevolutionTrackerTest#testCrank_identicalTimestamps_zeroesCadenceOnFourthPacket_thenRecovers`, `CscRevolutionTrackerTest#testWheel_advancingTimeWithZeroRevolutions_omitsPaceUpdate`, `CscRevolutionTrackerTest#testCrank_implausibleCadenceOver250Rpm_isIgnoredAndUpdatesBaseline`, `CscRevolutionTrackerTest#testReset_clearsAllBaselinesAndIdenticalCounters`, `CscRevolutionTrackerTest#testConstructorValidation_rejectsNonPositiveWheelResolution`, `CscRevolutionTrackerTest#testConstructorValidation_rejectsNonPositiveMaxIdenticalTicks`)*:
     • Verify `UINT16` event-time rollover (`lastEventTime = 65000 -> eventTime = 500` yields `timeDiff = 1036`, and `0xFF00 -> 0x0100` yields `512`) for both `1024 Hz` (CSC wheel/crank) and `2048 Hz` (CPS wheel) without dropping the wrap sample.
     • Verify `UINT32` wheel revolution wrap (`0xFFFFFFFE -> 2`) and `UINT16` crank revolution wrap (`65535 -> 1` $\rightarrow$ `120.0 rpm` at `1024` ticks), `MAX_IDENTICAL = 4` identical-time coasting (`timeDiff == 0` zeroes speed + `null` pace / zeroes cadence on 4th identical packet), `revDiff == 0` advancing wheel time omitting pace update (no `Infinity`), `> 250.0` rpm rejection still updating baseline, and `reset()` restoring initial state.
  2. *Driver Delegation & Null-Sensor Parity Tests (`TST-SET-126.2`, `BTLEMeasurementParsingTest#testBikeDevice_uint16WheelAndCrankEventTimeRollover_computesSpeedDistanceCadenceAndNotifiesWheel`, `BTLEMeasurementParsingTest#testBikePower_uint16WheelAndCrankEventTimeRollover_computes2048HzSpeedCadenceAndOmitsNotifyWheel`, `BTLEMeasurementParsingTest#testBikeDevice_and_BikePower_identicalEventTimeZeroesAfterFourPackets`, `BTLEMeasurementParsingTest#testBikeSpeedOnly_and_BikeCadenceOnly_ignoreMissingSensorsWithoutCrash`)*:
     • Verify all existing `ATT-6162` (`TST-CON-024.1`–`.4`) and `TST-CON-005` tests pass unchanged; verify CSC (`BTLEBikeDevice` `1024 Hz`) and CPS (`BTLEBikePowerDevice` `2048 Hz`) `UINT16` wheel & crank event-time rollover tests (`lastEventTime = 65000 -> eventTime = 500`, `timeDiff = 1036`), `MAX_IDENTICAL = 4` identical-time zeroing, single-sensor subclass null-safety (`BTLEBikeSpeedDevice`, `BTLEBikeCadenceDevice`), and `notifyWheelRevolutions` parity (`BTLEBikeDevice` calls it, `BTLEBikePowerDevice` omits it).
  3. *Targeted Regression & Contract Hygiene (`TST-SET-126.3`)*:
     • Run `CscRevolutionTrackerTest`, `BTLEMeasurementParsingTest`, `HardwareHalIngestionIntegrationTest`, `ArchitectureConformanceContractTest`, `InterfaceSignatureDriftContractTest`, `DeadCodeEliminationContractTest`, and `verify_requirement_governance.py` (`Localization Parity: N/A - 0 string changes`).
- **Verification Evidence**: Gate 4 (`ATT-9493` @ `a49ad12d`, `Rule 7.5`, `0` `app/` drift) — Stage 4 `55/55 PASS` (`0` fail, `0` err, `0` skip) across 6 suites (`CscRevolutionTrackerTest` `11/11`, `BTLEMeasurementParsingTest` `19/19`, `HardwareHalIngestionIntegrationTest` `5/5`, `ArchitectureConformanceContractTest` `11/11`, `InterfaceSignatureDriftContractTest` `3/3`, `DeadCodeEliminationContractTest` `6/6`) + Gate 4 independent `35/35 PASS` (`--rerun`, `0` new warnings); Python safety & baseline checks PASS; `docs/engineering/walkthroughs/ATT-6042_walkthrough.md`.

---

### TST-SET-127: **Workout Details Polar Retry Sync Unpaired Sensor Guard, Hint & Reactivity Verification Suite** (`ATT-9509`) [Verified]
- **Requirement(s)**: `REQ-SET-125` (`REQ-SET-125.1`–`REQ-SET-125.4`)
- **Component**: `DeviceDataRepository.kt`, `PolarH10RetrySyncBanner.kt`, `TrackOnMapScreen.kt`, `WorkoutDetailTabbedContent.kt`, `DeviceDataRepositoryPolarPairingTest.kt`, `PolarH10RetrySyncBannerTest.kt`, `TranslationParityTest.kt`
- **Jira**: `ATT-9509` | **Priority**: Medium | **Type**: Functional | **Verification Method**: Unit, Integration, On-Device Manual
- **Test Description**:
  1. *Encapsulated `DeviceDataRepository.isPolarRetrySensorPaired(...)` Unit Tests (`TST-SET-127.1`, `DeviceDataRepositoryPolarPairingTest#pairedTargetVeritySensor_returnsTrue_byPolarIdAndMac`, `DeviceDataRepositoryPolarPairingTest#unpairedTargetSensor_withRetainedRowPairedZero_returnsFalse`, `DeviceDataRepositoryPolarPairingTest#deletedTargetSensor_returnsFalse`, `DeviceDataRepositoryPolarPairingTest#renamedPairedSensor_matchesPersistedPolarIdInCapabilityStore`, `DeviceDataRepositoryPolarPairingTest#siblingPolarIsolation_pairedH10DoesNotMaskUnpairedVeritySense`, `DeviceDataRepositoryPolarPairingTest#nullOrBlankPolarDeviceId_usesManufacturerAwareFamilyFallback`, `DeviceDataRepositoryPolarPairingTest#rePairingFlip_transitionsFromFalseToTrueImmediately`)*:
     • Verify paired target Polar Verity Sense / H10 (matched by 8-hex ID via `PolarOfflineCapabilityStore`, `extractPolarId`, MAC address, or device name) returns `true`.
     • Verify unpaired target (`PAIRED = 0`, row retained in `DevicesDatabaseManager`) returns `false` (explicitly checking `isPaired(dev.id)` rather than unfiltered cache rows).
     • Verify deleted target sensor (no matching row) returns `false`.
     • Verify sibling sensor isolation: unpaired Verity Sense + paired H10 returns `false` for the Verity Sense `polarDeviceId` (and vice versa).
     • Verify `null`/blank `polarDeviceId` falls back strictly to matching paired family passing `manufacturer` to `isPolarVeritySenseDevice`/`isPolarH10Device`.
     • Verify re-pairing flip (`PAIRED: 0 -> 1`) immediately transitions `isPolarRetrySensorPaired(...)` from `false` to `true`.
  2. *Behavioral Compose UI & Reactivity Tests (`TST-SET-127.2`, `PolarH10RetrySyncBannerTest#unpairedVeritySensor_disablesSyncNowKeepsDismissEnabledAndShowsHintOverStaleError`, `PolarH10RetrySyncBannerTest#unpairedH10Sensor_disablesSyncNowAndShowsH10UnpairedHint`, `PolarH10RetrySyncBannerTest#initialAsyncPairingCheck_avoidsFrame0FalseUnpairedFlicker_thenDisablesWhenUnpaired`, `PolarH10RetrySyncBannerTest#rePairingSensorOnResume_clearsHintReEnablesSyncNowAndLaunchesSync`, `PolarH10RetrySyncBannerTest#rePairingViaDeviceDataRepositoryAllDevices_reactivelyReEnablesSyncNow`, `PolarH10RetrySyncBannerTest#captureLightDarkUnpairedAndPairedBannerPng_rule85`)*:
     • Verify unpaired state (`isSensorPaired = false`) disables *"Jetzt synchronisieren"*, keeps *"Ignorieren"* (`Dismiss`) enabled, displays the model-specific localized unpaired hint (`polar_verity_retry_sync_unpaired_hint` / `polar_h10_retry_sync_unpaired_hint`), and gives the unpaired hint precedence over any stale generic `errorMessage`.
     • Verify paired state (`isSensorPaired = true`) enables *"Jetzt synchronisieren"* and hides the unpaired hint.
     • Verify `initialValue = null` async loading avoids Frame-0 false-unpaired flicker before the initial pairing check completes.
     • Verify re-pairing reactivity (`allDevices` update and `ON_RESUME` tick flipping `false -> true`) clears the unpaired hint, re-enables *"Jetzt synchronisieren"*, and executes `PolarRetrySyncCoordinator.startSync(...)` on tap.
     • Verify existing `PolarH10RetrySyncBannerTest` tests (`syncNow_survivesBannerLeavingAndReenteringCompositionWithoutForgottenScopeException`, `syncNow_failureDisplaysLocalizedErrorMessage`, `syncNow_completionEmitsWorkoutIdForTelemetryRefresh`) pass cleanly with explicit paired state (`isSensorPairedOverride = true`).
  3. *9-Locale Parity & Architecture Contracts (`TST-SET-127.3`)*:
     • Run `TranslationParityTest` verifying `polar_h10_retry_sync_unpaired_hint` & `polar_verity_retry_sync_unpaired_hint` across all 9 `values*/strings_devices.xml` locales (`EN, DE, ES, FR, IT, JA, NL, PL, PT`, zero inline emojis).
     • Run `ArchitectureConformanceContractTest`, `UiReflectionOnlyTestAuditContractTest`, and `verify_requirement_governance.py --check-repo-encapsulation` confirming zero direct DB imports in `ui/aftermath/`, zero `*DatabaseManager`/`Cursor` getters, and untouched monoliths (`DevicesDatabaseManager.java`, `WorkoutRepository.kt`).
  4. *Visual Screenshot & Live Emulator Verification (`TST-SET-127.4`, `Rule 8.5` & `emulator-5554`)*:
     • Capture Light & Dark mode PNG screenshots of the unpaired Polar Verity Sense & H10 retry sync banner states (`/tmp/att9509_verify/ATT-9509_unpaired_retry_banner_light_dark.png`), inspect via `read_file` against the 4-point Visual Self-Inspection Checklist, and verify live re-pair re-enabling on `emulator-5554` (`com.atrainingtracker.debug`).
- **Verification Evidence**: Gate 4 (`ATT-9562` @ `a336463f`, `Rule 7.5`, `0` `app/` drift) — `45/45 PASS` (`0` fail, `0` err, `0` skip) across 6 Tier-1 suites (`DeviceDataRepositoryPolarPairingTest` `7/7`, `PolarH10RetrySyncBannerTest` `9/9`, `PolarRetrySyncCoordinatorTest` `3/3`, `TranslationParityTest` `11/11`, `ArchitectureConformanceContractTest` `11/11`, `UiReflectionOnlyTestAuditContractTest` `4/4`; `0` new production warnings); `4/4` Python safety linters & governance checks PASS; `--check-verified-evidence` EXIT `0`; `Rule 8.5` Light/Dark PNG (`/tmp/att9509_verify/ATT-9509_unpaired_retry_banner_light_dark.png`) certified via `read_file`; live `emulator-5554` deployment & screencap audit scheduled in Stage 6 Cold Neon Visual Review (`Rule 3.3`); `docs/engineering/walkthroughs/ATT-9509_walkthrough.md`.

---

### TST-SET-128: **Polar Verity Sense Workout PPI Gating, Stale/Flatline HR/RR Clearing & Primary HR Arbitration Verification Suite** (`ATT-9345`) [Verified]
- **Requirement(s)**: `REQ-SET-126` (`REQ-SET-126.1`–`REQ-SET-126.4`)
- **Component**: `PolarHrStreamHealthMonitor.kt`, `PrimaryHrSensorArbitrator.kt`, `HrSensorHealthProvider.kt`, `PolarOnlineStreamingBridge.kt`, `PolarPmdStreamCoordinator.kt`, `MySensorManager.java`, `BTLEHeartRateDevice.java`, `my_btle_device.md`, `PolarHrStreamHealthMonitorTest.kt`, `PrimaryHrSensorArbitratorTest.kt`, `PolarOnlineStreamingBridgeTest.kt`, `MySensorManagerPriorityTest.kt`, `BTLEHeartRateDeviceTest.kt`
- **Jira**: `ATT-9345` | **Priority**: Critical | **Type**: Functional | **Verification Method**: Unit, Integration
- **Test Description**:
  1. *Pure-JVM `PolarHrStreamHealthMonitorTest` (`TST-SET-128.1`)*:
     • Verify `hr <= 0` or lost contact (`contactStatusSupported && !contactStatus`) NEVER counts as a valid HR packet (does not disarm `silentHrWatchdog`) and transitions to `shouldClearHr = true` (`null`) after the bounded zero/no-contact window (`10 s`).
     • Verify HR staleness timeout (`> 15 s` since last valid `hr > 0`) clears HR & RR to `null` (no infinite latching of `79 bpm` or `659 ms`).
     • Verify sustained `ARTIFACT_GAP`-only PPI/RR bursts (`>= 10 s` without `ARTIFACT_NORMAL`) signal `shouldClearRr = true` (`null`).
     • Verify constant-value HR flatline (`>= 60 s` identical `hr > 0`, e.g., `79 bpm`) and rolling `>= 95%` optical artifact saturation mark the sensor degraded, emit cooldown-bounded recovery action (stop Verity PPI + re-subscribe HR), and automatically recover to `HEALTHY` as soon as dynamic HR (`±1 bpm` change) resumes.
  2. *Pure-JVM `PrimaryHrSensorArbitratorTest` & `MySensorManager` Arbitration (`TST-SET-128.2`, `PrimaryHrSensorArbitratorTest`, `MySensorManagerPriorityTest`)*:
     • Verify healthy Polar H10 (ECG) beats healthy Polar Verity Sense (optical PPG) regardless of registration order (including Verity Sense registering first).
     • Verify healthy Polar Verity Sense (optical PPG) beats stale, null, or degraded Polar H10 (failover on H10 drop/staleness).
     • Verify healthy Polar H10 beats frozen/flatlined (`79 bpm`) or `100%`-artifact-rate Polar Verity Sense, and re-arbitrates immediately via `MySensorManager.reevaluatePrimaryHrSensor()` without holding `MySensorManager.this` during `ProxySensor.setSourceSensor(...)` (`REQ-SEN-021.3`).
     • Verify when ALL registered HR sensors are `STALE_OR_NULL`, the highest-modality sensor remains bound to `ProxySensor` (preventing spurious `mSensorsSensor` drop while emitting `null`).
     • Verify ANT+ HRM ranks between ECG BLE and Optical PPG (`ECG < ANT+ < Optical PPG < Other`).
  3. *`PolarOnlineStreamingBridgeTest` & `BTLEHeartRateDeviceTest` Lifecycle Integration (`TST-SET-128.3`)*:
     • Verify Verity Sense `startPpiStreaming` runs in `TrackingMode.READY`/`IDLE` (`ATT-9176` regression preserved), is skipped when connecting in `TRACKING`/`PAUSED`, and is explicitly stopped via `api.stopStreaming(sessionId, PmdMeasurementType.PPI)` on `READY -> TRACKING` (`TRACKING_STATE_CHANGED`) without interrupting `HR`, `ACC`, or `GYRO`.
     • Verify `processHrData` with `sample.hr == 0` / lost contact does NOT disarm `silentHrWatchdog`, clears `mHeartRateSensor` to `null` after bounded persistence, and triggers `reevaluatePrimaryHrSensor()`.
     • Verify `BTLEHeartRateDevice` `heartRate == 0` does not disarm `mSilentHrWatchdog` and clears stale HR cleanly.
  4. *Monolith, Signature, Detailed-Design Parity & Emulator Verification (`TST-SET-128.4`)*:
     • Run `ArchitectureConformanceContractTest` (`PolarOnlineStreamingBridge.kt <= 1,500`, `DeviceManager.java`/`TrackerService.java`/`DevicesDatabaseManager.java`/`PolarVeritySenseOfflineController.kt` untouched), `InterfaceSignatureDriftContractTest`, `DetailedDesignParityContractTest` (`my_btle_device.md`), `BleHrmSilentConnectionWatchdogTest`, `PolarPpiStreamProcessorTest`, and verify APK install/runtime stability on `emulator-5554` (`Localization Parity: N/A - 0 string changes`).
- **Verification Evidence**: Gate 4 Bounce 1 (`ATT-9575` @ `1099f4a9`, `Rule 7.5`, `0` `app/` drift) — `130/130 PASS` (`0` fail, `0` err, `0` skip) across 11 Tier-1 suites (`PolarHrStreamHealthMonitorTest` `7/7` [`shouldStreamVerityPpi_allowsRestingModesAndBlocksWorkoutModes`, `onHrSample_zeroAndLostContactRejectedAndClearedAfterTenSeconds`, `evaluateStaleness_clearsHrAndRrAfterFifteenSecondsSilenceWithBoundedRecovery`, `onRrSample_clearsLatchedRrAfterTenSecondsOfArtifactOnlyBeatsAndResetsOnCleanBeat`, `onHrSample_flatlineRequiresBothDurationAndMinSamplesAndSuppressesRestingSteadyHr`, `resetRrAndOpticalArtifactState_clearsPreRunOpticalSaturation`, `nextCheckDelayMs_shrinksAcrossConsecutiveInvalidPacketsWithoutDrift`], `PrimaryHrSensorArbitratorTest` `6/6` [`selectBestHrSensor_prefersHealthyEcgOverHealthyOpticalRegardlessOfRegistrationOrder`, `selectBestHrSensor_failsOverToHealthyVerityWhenH10IsStaleOrDegraded`, `selectBestHrSensor_prefersHealthyH10OverDegradedOrFlatlinedVerity`, `selectBestHrSensor_enforcesFullModalityOrderEcgAntOpticalOther`, `selectBestHrSensor_retainsHighestModalityWhenAllCandidatesAreStaleOrNull`, `selectBestHrSensor_handlesNonRelaxedMockStubsWithoutThrowing`], `PolarOnlineStreamingBridgeTest` `56/56` [`testVeritySensePpi_gatedToRestingAndExplicitlyStoppedOnWorkoutEntryWithoutInterruptingImu`, `testProcessHrData_zeroOrLostContactDoesNotDisarmWatchdogAndClearsAfterTenSeconds`, `testProcessPpiData_sustainedArtifactGapClearsLatchedRrSensor`, `testProcessPpiData_droppedInTrackingModeWithoutReLatchingRr`, `testRecoverHrStreamAndWorkoutConnect_forceStopsOrphanedFirmwarePpiEvenWhenNotLocallyStreaming`, `testStartPpiCollection_abortsIfTrackingStartsAcrossGetAvailableOnlineStreamDataTypes`, `testHrStalenessTimer_rearmsAfterOfflineYieldResume`], `PolarPmdStreamCoordinatorTest` `11/11` [`testOneShotTimer_supersededTimerNeverInvokesStaleOnElapsed`], `MySensorManagerPriorityTest` `8/8` [`hrPriority_prefersHealthyEcgOverOpticalRegardlessOfRegistrationAndSwitchesDynamicallyOutsideLock`], `BTLEHeartRateDeviceTest` `18/18` [`testParseHeartRateMeasurement_zeroAndLostContactPreserveWatchdogClearAfterTenSecondsAndReevaluatePrimaryHr`, `testParseHeartRateMeasurement_lostContactOrZeroWithRrFlagClearsRrAndDoesNotDispatchRrBeats`], `BleHrmSilentConnectionWatchdogTest` `3/3`, `PolarPpiStreamProcessorTest` `4/4`, `ArchitectureConformanceContractTest` `11/11`, `InterfaceSignatureDriftContractTest` `3/3`, `DetailedDesignParityContractTest` `3/3`; `0` new production warnings); `4/4` Python safety linters PASS; `--check-verified-evidence` EXIT `0`; independent live `emulator-5554` verification scheduled in Stage 6 Cold Neon Review (`Rule 3.3`); `docs/engineering/walkthroughs/ATT-9345_walkthrough.md`.

---

### TST-SET-129: **`EditWorkoutScreen` Polar Pending Retry Sync Banner, Dismiss & Refresh Verification Suite** (`ATT-9174`) [Verified]
- **Requirement(s)**: `REQ-SET-127` (`REQ-SET-127.1`–`REQ-SET-127.3`)
- **Component**: `EditWorkoutScreen.kt`, `EditWorkoutViewModel.kt`, `PolarH10RetrySyncBanner.kt`, `EditWorkoutPolarRetrySyncUiTest.kt`, `EditWorkoutViewModelTest.kt`
- **Jira**: `ATT-9174` | **Priority**: Medium | **Type**: Functional | **Verification Method**: Unit, Integration, On-Device Manual
- **Test Description**:
  1. *Behavioral Compose UI Tests (`TST-SET-129.1`, `EditWorkoutPolarRetrySyncUiTest#editWorkout_whenPolarSyncPendingFalse_hidesBanner`, `EditWorkoutPolarRetrySyncUiTest#editWorkout_whenPolarSyncPendingTrue_rendersVerityBannerTriggersCoordinatorAndRefreshesOnCompletion`, `EditWorkoutPolarRetrySyncUiTest#editWorkout_whenStopTimeFastFailMarksPolarPendingAfterOpen_reactivelyMountsBanner`, `EditWorkoutPolarRetrySyncUiTest#editWorkout_dismissPolarBanner_hidesBannerClearsCoordinatorAndPersistsDismiss`, `EditWorkoutPolarRetrySyncUiTest#editWorkout_unpairedPolarSensor_showsUnpairedHintDisablesSyncNowAndKeepsDismissEnabled`, `EditWorkoutPolarRetrySyncUiTest#editWorkout_captureLightDarkPolarRetryBannerPng_rule85`)*:
     • Verify banner is hidden when `polarSyncPending == false`, renders flush inside `EditWorkoutScreen` when `polarSyncPending == true`, and reactively mounts when post-processing fast-fail flips `polarSyncPending` to `true` after `EditWorkoutScreen` opens.
     • Verify tapping **"Sync Now"** launches `PolarRetrySyncCoordinator`, disables controls while syncing, triggers `repository.refreshWorkout(workoutId)` on completion, hides the banner when `polarSyncPending` becomes `false`, and preserves unsaved user edits.
     • Verify tapping **"Dismiss"** hides the banner immediately, persists `repository.dismissPolarSyncPending(workoutId)`, and ignores stale `allWorkouts` snapshots.
     • Verify unpaired Polar sensor state renders `ATT-9509` unpaired hint, disables **"Sync Now"**, and keeps **"Dismiss"** enabled.
  2. *`EditWorkoutViewModel` Dismiss & Completion Refresh Unit Tests (`TST-SET-129.2`, `TST-SET-129.3`, `EditWorkoutViewModelTest#testDismissPolarSyncPending_clearsStatePersistsAndSurvivesStaleRefresh_tstSet129_2`, `EditWorkoutViewModelTest#testPolarRetrySyncCompletion_refreshesMatchingWorkoutAndPreservesUserEdits_tstSet129_3`)*:
     • Verify `dismissPolarSyncPending()` updates `_workoutData`, delegates to `WorkoutRepository`, and prevents stale `allWorkouts` refresh resurrection.
     • Verify `PolarRetrySyncCoordinator.completedWorkoutEvents` refreshes only matching `workoutId`, updates summary stats, hides the banner, and preserves all user edits.
  3. *Visual Screenshot & Live `emulator-5554` Verification (`TST-SET-129.4`, `Rule 8.5` & `Rule 9.3`)*:
     • Capture Light/Dark Compose PNG (`ATT-9174_edit_workout_polar_retry_banner_light_dark.png`) and live `emulator-5554` screencaps before and after Dismiss (`ATT-9174_emulator_edit_workout_banner.png`, `ATT-9174_emulator_edit_workout_after_dismiss.png`), inspect all via `read_file`, verify DB persistence (`polarSyncPending = 0`) and clean `logcat`.
- **Verification Evidence**: `65/65 PASS` (`0` fail, `0` err, `0` skip) across 10 Tier-1 suites (`EditWorkoutPolarRetrySyncUiTest` `6/6`, `EditWorkoutViewModelTest` `16/16`, `PolarH10RetrySyncBannerTest` `9/9`, `PolarRetrySyncCoordinatorTest` `3/3`, `EditWorkoutLayoutContractTest` `1/1`, `EditWorkoutClassificationLayoutContractTest` `3/3`, `EditWorkoutFieldsLayoutTest` `3/3`, `ArchitectureConformanceContractTest` `11/11`, `UiReflectionOnlyTestAuditContractTest` `4/4`, `RepositoryUiCleanlinessContractTest` `9/9`); Light/Dark Compose PNG and live `emulator-5554` before/after Dismiss screenshots verified via `read_file`; evidence bundle under `docs/engineering/test_evidence/tickets/ATT-9174/` and deliverable `docs/engineering/tickets/ATT-9174.md`.

---

## Change History

| Version | Date | Author | Ticket | Summary of Changes |
| :--- | :--- | :--- | :--- | :--- |
| `1.0.0` | 2026-11-17 | AI Agent (`ATT-8164`) | `ATT-8164` | Added standardized `DOC-TST-SET` work-product control header and Change History. |
| `1.0.1` | 2026-11-17 | AI Agent (`ATT-8690`) | `ATT-8690` | Added `TST-SET-120` (offline mocked verification suite for `REQ-SET-118`). |
| `1.0.2` | 2026-11-17 | AI Agent (`ATT-8690`) | `ATT-8690` | Promoted `TST-SET-120` to `[Implemented]` following Stage 4 test implementation. |
| `1.0.3` | 2026-11-17 | AI Agent (`ATT-8690`) | `ATT-8690` | Promoted `TST-SET-120` to `[Verified]` following Stage 5 unit verification. |
| `1.0.4` | 2026-11-19 | AI Agent (`ATT-8969`) | `ATT-8969` | Added `TST-SET-121` (`[Planned]` verification suite for `REQ-SET-119`). |
| `1.0.6` | 2026-11-19 | AI Agent (`ATT-8969`) | `ATT-8969` | Promoted `TST-SET-121` to `[Implemented]` following Stage 4 test implementation. |
| `1.0.7` | 2026-11-19 | AI Agent (`ATT-8969`) | `ATT-8969` | Promoted `TST-SET-121` to `[Verified]` following Stage 5 verification. |
| `1.0.8` | 2026-11-20 | AI Agent (`ATT-8328`) | `ATT-8328` | Reconciled legacy test descriptions (`TST-SET-003`, `TST-SET-006`, `TST-SET-027`) via Chesterton's Fence audit; all 43 triage TSTs remain `[Implemented]` pending Stage 4/5 execution. |
| `1.0.9` | 2026-11-20 | AI Agent (`ATT-8328`) | `ATT-8328` | Promoted 39 verified `TST-SET-*` specifications to `[Verified]` following Gate 4 (`2a149eca`, `119/119` PASS across 22 suites); retained Bucket C (`TST-SET-006`, `011`, `013`, `058`) at `[Implemented]`. |
| `1.0.10` | 2026-11-20 | Lane B Author (`ATT-9175`, `--as agent1`) | `ATT-9175` | Added `TST-SET-122` (`[Planned]`, `.1`–`.4`, strictly symmetric `REQ-SET-120`) covering alias-aware ref-counted offline yield, `PolarOnlineStreamingBridge` pause/resume + watchdog deferral, single-shot bounded `deviceConnecting` handshake grace, and architecture/governance contracts (`N/A - no UI strings`). |
| `1.0.11` | 2026-11-20 | Lane B Author (`ATT-9175`, `--as agent1`) | `ATT-9175` | Promoted `TST-SET-122` to `[Implemented]` with all `6/6` verbatim `TestClass#testMethod` citations across `PolarOfflineGattCoordinatorCoexistenceTest` and `PolarOnlineStreamingBridgeTest`. |
| `1.0.12` | 2026-11-20 | Lane B Author (`ATT-9175`, `--as agent1`) | `ATT-9175` | Promoted `TST-SET-122` to `[Verified]` in Stage 5 reusing Gate 4 (`ATT-9378` @ `cbe8ef29`, `Rule 7.5`, `0` `app/` drift) `161/161 PASS` across 15 suites + `:app:assembleDebug` + `0` new production Kotlin warnings (`6/6` verbatim `TST-SET-122` methods verified), `4/4` Python safety linters PASS, `--check-verified-evidence` EXIT `0`, and `docs/engineering/walkthroughs/ATT-9175_walkthrough.md`. |
| `1.0.13` | 2026-11-20 | Lane B Author (`ATT-9175`, `--as agent1`) | `ATT-9175` | Stage 6 Bounce 1/3 heal: added 3 regression tests (`9/9` verbatim `TST-SET-122` methods covering overlapping-lease alias expansion, mid-yield `deviceConnected` silent-HR watchdog deferral/resume, `shouldKeepAliveOnSearchTimeout()` in `WAITING_FOR_START`, and stale out-of-order `pauseForOfflineSync()` non-wedge) and set `[Implemented]` pending Gate 4 / Stage 5 re-verification. |
| `1.0.14` | 2026-11-20 | Lane B Author (`ATT-9175`, `--as agent1`) | `ATT-9175` | Promoted `TST-SET-122` to `[Verified]` in Stage 5 Bounce 1 reusing Gate 4 Bounce 1 (`ATT-9384` @ `d2c4953f`, `Rule 7.5`, `0` `app/` drift) `164/164 PASS` across 15 suites + `:app:assembleDebug` + `0` new production Kotlin warnings (`9/9` verbatim `TST-SET-122` methods verified), `4/4` Python safety linters PASS, `--check-verified-evidence` EXIT `0`, and refreshed `docs/engineering/walkthroughs/ATT-9175_walkthrough.md`. |
| `1.0.15` | 2026-11-21 | Lane B Flash (`ATT-6048`, `--as agent1`) | `ATT-6048` | Added and verified `TST-SET-123` (`TuningPreferencesDataStoreRoundTripTest.kt`, `SettingsDataStoreZoneAtomicTest.kt`, `167/167` PASS @ `2038d89b`, Stage 6 PASS). |
| `1.0.16` | 2026-11-21 | Lane A Author (`ATT-9346`, `--as agent1`) | `ATT-9346` | Added `TST-SET-124` (`[Planned]`, `.1`–`.4`, strictly symmetric `REQ-SET-122`) covering dynamic H10 `100 <-> 200 Hz` `ACC` renegotiation, sibling-coroutine/`ACC_OWNER_ONLINE`-safe/teardown-safe `PolarPmdStreamCoordinator.renegotiateSlot()`, `PolarH10RunningDynamicsEstimator.resetForGap()` recovery, and monolith/signature contracts (`N/A - no UI strings`). |
| `1.0.17` | 2026-11-21 | Lane A Author (`ATT-9346`, `--as agent1`) | `ATT-9346` | Promoted `TST-SET-124` to `[Implemented]` with all `6/6` verbatim `TestClass#testMethod` citations (`89/89 PASS` across 8 suites including `ArchitectureConformanceContractTest` `11/11` and `InterfaceSignatureDriftContractTest` `3/3`). |
| `1.0.18` | 2026-11-21 | Lane A Author (`ATT-9346`, `--as agent1`) | `ATT-9346` | Promoted `TST-SET-124` to `[Verified]` in Stage 5 reusing Gate 4 (`ATT-9448` @ `1f92ed6c`, `Rule 7.5`, `0` `app/` drift) `89/89 PASS` across 8 suites (`6/6` verbatim `TST-SET-124` methods verified), `3/3` Python safety linters PASS, `--check-verified-evidence` EXIT `0`, and `docs/engineering/walkthroughs/ATT-9346_walkthrough.md`. |
| `1.0.19` | 2026-11-21 | Lane A Author (`ATT-9346`, `--as agent1`) | `ATT-9346` | Stage 6 Bounce 1/3 heal: added 2 regression tests (`8/8` verbatim `TST-SET-124` methods covering atomic `slotLock` relaunch handoff, `onAborted` invocation, and mid-`stopStreaming()` offline yield post-resume `200 Hz` `ACC`/VO/GCT recovery; `91/91 PASS` across 8 suites) and set `[Implemented]` pending Gate 4 / Stage 5 Bounce 1 re-verification. |
| `1.0.20` | 2026-11-21 | Lane A Author (`ATT-9346`, `--as agent1`) | `ATT-9346` | Promoted `TST-SET-124` to `[Verified]` in Stage 5 Bounce 1 reusing Gate 4 Bounce 1 (`ATT-9453` @ `522726b7`, `Rule 7.5`, `0` `app/` drift) `91/91 PASS` across 8 suites (`8/8` verbatim `TST-SET-124` methods verified), `3/3` Python safety linters PASS, `--check-verified-evidence` EXIT `0`, and refreshed `docs/engineering/walkthroughs/ATT-9346_walkthrough.md`. |
| `1.0.21` | 2026-11-21 | Lane A Author (`ATT-9346`, `--as agent1`) | `ATT-9346` | Stage 6 Bounce 2/3 heal: added regression test `testH10FeatureReadyDuringOfflineYield_defersAccAndRecovers200HzVoGctOnResume_TST_SET_124_2` (`9/9` verbatim `TST-SET-124` methods; `92/92 PASS` across 8 suites) covering Dekker offline-yield deferral for one-shot `FEATURE_POLAR_ONLINE_STREAMING` and `onAccRenegotiationAborted()`, and set `[Implemented]` pending Gate 4 / Stage 5 Bounce 2 re-verification. |
| `1.0.22` | 2026-11-21 | Lane A Author (`ATT-9346`, `--as agent1`) | `ATT-9346` | Promoted `TST-SET-124` to `[Verified]` in Stage 5 Bounce 2 reusing Gate 4 Bounce 2 (`ATT-9459` @ `8d7a449f`, `Rule 7.5`, `0` `app/` drift) `92/92 PASS` across 8 suites (`9/9` verbatim `TST-SET-124` methods verified), `3/3` Python safety linters PASS, `--check-verified-evidence` EXIT `0`, and refreshed `docs/engineering/walkthroughs/ATT-9346_walkthrough.md`. |
| `1.0.23` | 2026-11-21 | Lane A Author (`ATT-9346`, `--as agent1`) | `ATT-9346` | Stage 6 Bounce 3/3 final heal: added 2 regression tests (`testLaunchStream_doesNotSpeculativelyActivateSlotDuringPreAcquire_TST_SET_124_2` and `testH10StaleAbortedRenegotiationAndConcurrentYieldResume_respectsReplacementAndLinearizesPreAcquire_TST_SET_124_2`; `11/11` verbatim `TST-SET-124` methods, `94/94 PASS` across 8 suites) and set `[Implemented]` pending Gate 4 / Stage 5 Bounce 3 re-verification. |
| `1.0.24` | 2026-11-21 | Lane A Author (`ATT-9346`, `--as agent1`) | `ATT-9346` | Promoted `TST-SET-124` to `[Verified]` in Stage 5 Bounce 3 reusing Gate 4 Bounce 3 (`ATT-9462` @ `939725f4`, `Rule 7.5`, `0` `app/` drift) `94/94 PASS` across 8 suites (`11/11` verbatim `TST-SET-124` methods verified), `3/3` Python safety linters PASS, `--check-verified-evidence` EXIT `0`, and refreshed `docs/engineering/walkthroughs/ATT-9346_walkthrough.md`. |
| `1.0.25` | 2026-11-21 | Single-Lane Author (`ATT-9243`, `--as agent1`) | `ATT-9243` | Added `TST-SET-125` (`[Planned]`, `.1`–`.4`, strictly symmetric `REQ-SET-123`) covering never-connected BLE passive workout convergence (`READY -> TRACKING` edge), scan-assist gating & `attemptBackgroundReconnect` timeout skipping, `>= 60 s` disconnect floor, `DeviceSearchPeriodicRetryCoordinator` keep-alive `deviceTries` cleanup, and design/governance parity (`N/A - no UI strings`). |
| `1.0.26` | 2026-11-21 | Single-Lane Author (`ATT-9243`, `--as agent1`) | `ATT-9243` | Promoted `TST-SET-125` to `[Implemented]` with verbatim `TestClass#testMethod` citations (`88/88 PASS` across 9 Tier-1 suites including `DetailedDesignParityContractTest` `3/3`, `SystemLiveTrackingRequirementsContractTest` `4/4`, and `ArchitectureConformanceContractTest` `11/11`; `3/3` Python safety linters PASS). |
| `1.0.27` | 2026-11-21 | Single-Lane Author (`ATT-9243`, `--as agent1`) | `ATT-9243` | Promoted `TST-SET-125` to `[Verified]` in Stage 5 reusing Gate 4 (`ATT-9483` @ `85386e63`, `Rule 7.5`, `0` `app/` drift) `88/88 PASS` across 9 Tier-1 suites (`0` new production compiler warnings), `3/3` Python safety linters PASS, `--check-verified-evidence` EXIT `0`, and `docs/engineering/walkthroughs/ATT-9243_walkthrough.md`. |
| `1.0.28` | 2026-11-21 | Lane 2 Author (`ATT-6042`, `--as agent1`) | `ATT-6042` | Added `TST-SET-126` `[Planned]` covering pure-JVM `CscRevolutionTracker` wrap/coasting/reset math, `BTLEBikeDevice` (`1024 Hz`) & `BTLEBikePowerDevice` (`2048 Hz`) rollover delegation, null-sensor guards, and `ATT-6162` parity. |
| `1.0.29` | 2026-11-21 | Lane 2 Author (`ATT-6042`, `--as agent1`) | `ATT-6042` | Promoted `TST-SET-126` to `[Implemented]` with all `11/11` `CscRevolutionTrackerTest` + `4/4` new `BTLEMeasurementParsingTest` verbatim method citations. |
| `1.0.30` | 2026-11-21 | Lane 2 Author (`ATT-6042`, `--as agent1`) | `ATT-6042` | Promoted `TST-SET-126` to `[Verified]` in Stage 5 reusing Gate 4 (`ATT-9493` @ `a49ad12d`, `Rule 7.5`, `0` `app/` drift): Stage 4 `55/55 PASS` across 6 suites + Gate 4 independent `35/35 PASS`, safety/baseline checks PASS, and `docs/engineering/walkthroughs/ATT-6042_walkthrough.md`. |
| `1.0.31` | 2026-11-21 | Lane 1 Author (`ATT-9509`, `--as agent1`) | `ATT-9509` | Added `TST-SET-127` (`[Planned]`, `.1`–`.4`, strictly symmetric `REQ-SET-125`) covering `DeviceDataRepository` pairing detection (paired, `PAIRED=0`, deleted, sibling isolation, `manufacturer` fallback, re-pair flip), `PolarH10RetrySyncBannerTest` Compose UI & reactivity, 9-locale `TranslationParityTest`, architecture contracts, and `Rule 8.5` Light/Dark PNG + `emulator-5554` verification. |
| `1.0.32` | 2026-11-21 | Lane 1 Author (`ATT-9509`, `--as agent1`) | `ATT-9509` | Promoted `TST-SET-127` to `[Implemented]` with all `13/13` verbatim `TestClass#testMethod` citations (`DeviceDataRepositoryPolarPairingTest` `7/7`, `PolarH10RetrySyncBannerTest` `9/9`, total `45/45 PASS` across 6 Tier-1 suites + `Rule 8.5` Light/Dark PNG verified). |
| `1.0.33` | 2026-11-21 | Lane 1 Author (`ATT-9509`, `--as agent1`) | `ATT-9509` | Promoted `TST-SET-127` to `[Verified]` in Stage 5 reusing Gate 4 (`ATT-9562` @ `a336463f`, `Rule 7.5`, `0` `app/` drift): `45/45 PASS` across 6 Tier-1 suites (`13/13` verbatim methods verified), `4/4` Python safety linters PASS, `--check-verified-evidence` EXIT `0`, `Rule 8.5` Light/Dark PNG certified, and `docs/engineering/walkthroughs/ATT-9509_walkthrough.md`. |
| `1.0.34` | 2026-11-21 | Lane 1 Author (`ATT-9345`, `--as agent1`) | `ATT-9345` | Added `TST-SET-128` (`[Planned]`, `.1`–`.4`, strictly symmetric `REQ-SET-126`) covering `PolarHrStreamHealthMonitorTest`, `PrimaryHrSensorArbitratorTest`, `PolarOnlineStreamingBridgeTest` (Verity workout PPI gating & explicit `stopStreaming(PPI)` + `hr=0`/stale/flatline clearing), `MySensorManagerPriorityTest`, `BTLEHeartRateDeviceTest`, and monolith/signature/detailed-design contracts (`N/A - 0 string changes`). |
| `1.0.35` | 2026-11-21 | Lane 1 Author (`ATT-9345`, `--as agent1`) | `ATT-9345` | Promoted `TST-SET-128` to `[Implemented]` with all `17/17` new verbatim `TestClass#testMethod` citations (`123/123 PASS` across 11 Tier-1 suites including `ArchitectureConformanceContractTest` `11/11`, `InterfaceSignatureDriftContractTest` `3/3`, `DetailedDesignParityContractTest` `3/3`; aligned resting mode prose to `READY`/`IDLE`). |
| `1.0.36` | 2026-11-21 | Lane 1 Author (`ATT-9345`, `--as agent1`) | `ATT-9345` | Promoted `TST-SET-128` to `[Verified]` in Stage 5 reusing Gate 4 (`ATT-9573` @ `36870006`, `Rule 7.5`, `0` `app/` drift): `123/123 PASS` across 11 Tier-1 suites (`17/17` verbatim new methods verified, `0` new production warnings), `4/4` Python safety linters PASS, `--check-verified-evidence` EXIT `0`, and `docs/engineering/walkthroughs/ATT-9345_walkthrough.md`. |
| `1.0.37` | 2026-11-21 | Lane 1 Author (`ATT-9345`, `--as agent1`) | `ATT-9345` | Stage 6 Bounce 1/3 heal: added `+7` regression tests (`24/24` verbatim new methods, `130/130 PASS` across 11 Tier-1 suites) covering `BTLEHeartRateDevice` `0x10` RR gate on lost contact/zero HR, `PolarOnlineStreamingBridge` Verity PPI TOCTOU + `processPpiData` workout drop + `forceStopFirmwarePpi` on workout connect & recovery + `resumeAfterOfflineSync`/`evaluateHrStaleness` re-arming, `OneShotTimer` superseding guard, and drift-free `nextCheckDelayMs` + optical reset tier re-sync; set `[Implemented]` pending Gate 4 / Stage 5 Bounce 1 re-verification. |
| `1.0.38` | 2026-11-21 | Lane 1 Author (`ATT-9345`, `--as agent1`) | `ATT-9345` | Promoted `TST-SET-128` to `[Verified]` in Stage 5 Bounce 1 reusing Gate 4 Bounce 1 (`ATT-9575` @ `1099f4a9`, `Rule 7.5`, `0` `app/` drift): `130/130 PASS` across 11 Tier-1 suites (`24/24` verbatim new methods verified, `0` new production warnings), `4/4` Python safety linters PASS, `--check-verified-evidence` EXIT `0`, and refreshed `docs/engineering/walkthroughs/ATT-9345_walkthrough.md`. |
| `1.0.39` | 2026-11-22 | Lane 3 Author (`ATT-9174`, `--as agent1`) | `ATT-9174` | Added and verified `TST-SET-129` (`EditWorkoutPolarRetrySyncUiTest` `6/6` + `EditWorkoutViewModelTest` `+2` new methods; `65/65 PASS` across 10 Tier-1 suites, Light/Dark Compose PNG + live `emulator-5554` before/after Dismiss verified via `read_file`). |
