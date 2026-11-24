# Living Requirements: Settings, Profiles & Localization

**Document ID**: `DOC-REQ-SET`  
**Version**: 1.0.50  
**Status**: Reviewed  
**Approver**: Pending PO Sign-Off  

**Domain**: `settings_profiles`  
**Prefixes**: `LOC`, `MON`, `PRI`, `SET`  
**Scope**: Athlete settings, bike/sensor profiles, privacy zones, data stores, monetization, and 9-locale parity.  
**Total Requirements**: 130

---

### REQ-SET-001: HR and Power training zones (5-zone model) [Implemented]
- **Tests**: `TST-SET-082`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit, Integration
- **Implementation**: `ZonesSettingsActivity.kt`
- **Rationale**: Allow athletes to train within specific intensities.
- **Description**: HR and Power training zones (5-zone model).

### REQ-SET-002: Equipment management (Bikes/Shoes) with odometer [Implemented]
- **Tests**: `TST-SET-098`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit, Integration
- **Implementation**: `EquipmentRepository.kt`
- **Rationale**: Help users monitor gear wear and tear.
- **Description**: Equipment management (Bikes/Shoes) with odometer.

### REQ-SET-003: Configurable speed thresholds for classification [Verified]
- **Tests**: `TST-UNT-003`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit, Integration
- **Implementation**: `TrainingApplication.java`
- **Rationale**: Tailor automation to user's specific pace profile.
- **Description**: Configurable speed thresholds for classification.

### REQ-SET-004: "Fancy Name" schemes using location and detours [Implemented]
- **Tests**: `TST-SET-053`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit, Integration
- **Implementation**: `WorkoutSummariesDatabaseManager.java`
- **Rationale**: Provide descriptive workout names automatically.
- **Description**: "Fancy Name" schemes using location and detours.

### REQ-SET-050: Navigation Drawer Structure [Implemented]
- **Tests**: `TST-NAV-001`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit, Integration
- **Implementation**: `main_navigation_drawer.xml`, `MainActivityWithNavigation.java`, `ui/settings/`
- **Rationale**: The navigation drawer SHALL be organized into logical hubs: Training, Spatial, Equipment, Ecosystem, and System. Key management features (Sport Types, Training Zones, Strava, Dropbox, Export) SHALL be individual first-class items in the drawer. Technical application settings (Units, Search, Display, Tracking Tabs) SHALL also be accessible as individual drawer items.

### REQ-SET-055: Modular Settings Architecture [Implemented]
- **Tests**: `TST-INT-008`
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Implementation**: `ui/settings/`, `MainActivityWithNavigation.java`
- **Rationale**: The system's settings configuration SHALL follow a modular architectural pattern. All settings-related UI components SHALL reside within dedicated functional sub-packages under `ui.settings` (e.g., `display`, `units`, `export`). The system SHALL NOT contain redundant preference fragments or legacy XML configurations that are no longer active in the primary navigation flow.

### REQ-SET-051: Direct Tracking Tab Selection [Implemented]
- **Tests**: `TST-NAV-003`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit, Integration
- **Implementation**: `MainActivityWithNavigation.java`, `ActivityTypeSelectionHelper.kt`
- **Rationale**: The system SHALL provide a modal interface for Activity Type selection accessible directly from the navigation drawer. Upon selection, the system SHALL launch the dedicated configuration cockpit for the chosen Activity Type.

### REQ-SET-052: Modal Display Settings & Synchronized Preference State [Verified]
- **Tests**: `TST-NAV-004`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit, Integration
- **Implementation**: `DisplaySettingsDialog.kt`, `DisplaySettingsDialogFragment.kt`, `MainActivityWithNavigation.kt`, `TrainingApplication.java`
- **Rationale**: The system SHALL provide direct access to display-related preferences (`forcePortrait`, `keepScreenOn`, `noUnlocking`) via a modal dialog (`DisplaySettingsDialog`) triggered from the navigation drawer (`DisplaySettingsDialogFragment`):
1. *Default State Synchronization*: The display settings dialog MUST initialize switch states using `TrainingApplication.DEFAULT_DISPLAY_OPTIONS` (with `forcePortrait`, `keepScreenOn`, and `noUnlocking` all enabled) whenever the preference key `pref_display_options` is not explicitly persisted in `SharedPreferences`. The visual switch state in the dialog MUST match the runtime evaluation of `TrainingApplication.forcePortrait()`, `TrainingApplication.keepScreenOn()`, and `TrainingApplication.NoUnlocking()`.
2. *Atomic State Hoisting*: The dialog state MUST be hoisted at the dialog root to ensure toggling any display switch atomically updates the option set and persists it to `SharedPreferences` without dropping or reverting toggles made to sibling switches.
3. *Bidirectional Dynamic Window & Orientation Application*: When display options are modified or the dialog is dismissed, the host activity (`MainActivityWithNavigation`) SHALL immediately and dynamically apply the settings:
• `keepScreenOn`: Sets `window.decorView.keepScreenOn` to the matching boolean value.
• `noUnlocking`: When enabled, adds `WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED`; when disabled, explicitly clears `FLAG_SHOW_WHEN_LOCKED`.
• `forcePortrait`: When enabled, sets `requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT`; when disabled, explicitly resets `requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED`.
**Acceptance Criteria (Given-When-Then)**:
• *Given* a fresh install or an environment where `pref_display_options` has not been modified,
• *When* the user opens the Display Settings dialog from the navigation drawer,
• *Then* all three switches ("force portrait", "keep screen on", "keep screen unlocked") SHALL be displayed in the ON (checked) state.
• *Given* the Display Settings dialog,
• *When* the user toggles "keep screen on" to OFF and then toggles "keep screen unlocked" to OFF,
• *Then* both settings SHALL remain OFF, and SharedPreferences SHALL contain only `["forcePortrait"]`.
• *When* the user disables "force portrait",
• *Then* the activity orientation lock SHALL be released (`SCREEN_ORIENTATION_UNSPECIFIED`) dynamically without requiring an activity restart.
**Invariants**: The SharedPreferences key `pref_display_options`, option string literals (`forcePortrait`, `keepScreenOn`, `noUnlocking`), and backward-compatible reading of existing user preferences MUST NOT change.

### REQ-SET-053: Modal Units Selection [Implemented]
- **Tests**: `TST-NAV-005`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit, Integration
- **Implementation**: `MainActivityWithNavigation.java`, `UnitsSettingsDialogFragment.kt`
- **Rationale**: The system SHALL provide direct access to global unit system settings (Metric/Imperial) via a modal dialog triggered from the navigation drawer.

### REQ-SET-054: Modal Export Configuration [Implemented]
- **Tests**: `TST-NAV-006`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit, Integration
- **Implementation**: `MainActivityWithNavigation.java`, `ExportSettingsDialogFragment.kt`
- **Rationale**: The system SHALL provide direct access to file export preferences (TCX, GPX, etc.) via a modal dialog triggered from the navigation drawer.

### REQ-SET-005: Global Unit Support (Metric/Imperial) [Implemented]
- **Tests**: `TST-NAV-005`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit, Integration
- **Implementation**: `MyUnits.java`, `MyHelper.java`
- **Rationale**: The system SHALL support both Metric and Imperial unit systems globally across all modules (Tracking, Aftermath, Periods, WorkoutClusters, and Settings). All user-facing values, entry fields, and visualizations SHALL adapt dynamically to the user's unit preference.

### REQ-SET-006: Agnostic Workout Clustering [Verified]
- **Tests**: `TST-SET-001`
- **Priority**: High
- **Type**: Functional
- **Verification Method**: Unit, Integration
- **Implementation**: `WorkoutClusterEngine.kt`
- **Rationale**: The system SHALL automatically group similar tracks into \"Workout Clusters\" based on spatial fingerprints (Start, End, Apex, Distance) regardless of the initial sport type.

### REQ-SET-007: Majority-Based Sport Inference [Verified]
- **Tests**: `TST-SET-001`
- **Priority**: High
- **Type**: Functional
- **Verification Method**: Unit, Integration
- **Implementation**: `WorkoutClusterEngine.kt`, `WorkoutSummariesDatabaseManager.java`
- **Rationale**: The system SHALL determine the most likely Name and Sport for a Workout Cluster based on the majority of associated workouts. Manual confirms/overrides SHALL take precedence in the learning knowledge base.

### REQ-SET-008: Learning Feedback Loop [Verified]
- **Tests**: `TST-SET-001`
- **Priority**: High
- **Type**: Functional
- **Verification Method**: Unit, Integration
- **Implementation**: `WorkoutRepository.kt`
- **Rationale**: The system SHALL refine the cluster centroids and update probable sport associations whenever a user overrides or confirms a suggestion. The cluster's name SHALL ONLY be updated if it currently has a generic/default name (e.g., 'Cluster at...').

### REQ-SET-009: Favorite Tracks Heatmap [Verified]
- **Tests**: `TST-SET-002`
- **Priority**: High
- **Type**: Functional
- **Verification Method**: Unit, Integration
- **Implementation**: `WorkoutClusterHeatmapScreen.kt`, `MapContentScope.kt`
- **Rationale**: The system SHALL provide a visualization for Workout Clusters that overlays all associated workout tracks as a heatmap, along with the spatial fingerprint markers.

### REQ-SET-010: Tuning Parameters UI [Verified]
- **Tests**: `TST-SET-003`, `TST-SET-034`
- **Priority**: High
- **Type**: Functional
- **Verification Method**: Unit, Integration
- **Implementation**: `ClusterTuningScreen.kt`, `WorkoutClustersViewModel.kt`
- **Rationale**: The system SHALL provide a UI to edit the tuning parameters for the Workout Cluster Engine, featuring a primary \"Strict-to-Relaxed\" master slider, optional detailed controls for individual tolerances (Endpoint, Apex, Distance), and a toggle to optionally include Sport Type in similarity calculations.

### REQ-SET-011: Clustering Recalculation [Verified]
- **Tests**: `TST-SET-003`
- **Priority**: High
- **Type**: Functional
- **Verification Method**: Unit, Integration
- **Implementation**: `WorkoutClusterEngine.kt`
- **Rationale**: Upon modification of tuning parameters, the system SHALL allow the user to trigger a full recalculation of Workout Clusters across the entire workout history.

### REQ-SET-012: Workout Cluster List Visualization [Verified]
- **Tests**: `TST-SET-004`
- **Priority**: High
- **Type**: Functional
- **Verification Method**: Unit, Integration
- **Implementation**: `WorkoutClustersTabsScreen.kt`, `WorkoutClusterHeatmapScreen.kt`
- **Rationale**: The system SHALL display a Workout Cluster list using a layout harmonized with the Workout and Route lists. Each item SHALL feature a full-width header (BSportType icon and Name) positioned at the top. Secondary metadata and metrics SHALL be displayed below the header, alongside a right-aligned map preview of the spatial signature. The Cluster Detail Heatmap SHALL also utilize a similar header layout to maximize visual consistency across the analytical suite.

### REQ-SET-013: Name-Aware Clustering [Verified]
- **Tests**: `TST-SET-005`
- **Priority**: High
- **Type**: Functional
- **Verification Method**: Unit, Integration
- **Implementation**: `WorkoutClusterEngine.kt`
- **Rationale**: The system SHALL prioritize existing identical workout names when matching routes to clusters to improve grouping accuracy for user-named paths.

### REQ-SET-014: Auto-Dismiss Tuning UI [Implemented]
- **Tests**: `TST-SET-006`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit, Integration
- **Implementation**: `WorkoutClustersFragment.kt`, `WorkoutClustersViewModel.kt`
- **Rationale**: The system SHALL automatically return to the cluster list view once a recalculation process is successfully completed. *(Superseded by REQ-SET-075 / ATT-3452: Cluster tuning removed from Favorite Routes header; recalculation centralized in Advanced Settings without auto-dismissal)*.

### REQ-SET-015: Unique Cluster Naming [Verified]
- **Tests**: `TST-SET-007`
- **Priority**: High
- **Type**: Functional
- **Verification Method**: Unit, Integration
- **Implementation**: `WorkoutClusterEngine.kt`, `WorkoutClusterDatabaseManager.kt`
- **Rationale**: The system SHALL ensure that every workout cluster has a unique name by appending a variant counter (e.g., var 2) if a name collision occurs for spatially distinct routes.

### REQ-SET-016: Edit Cluster Identity [Verified]
- **Tests**: `TST-SET-008`
- **Priority**: High
- **Type**: Functional
- **Verification Method**: Unit, Integration
- **Implementation**: `WorkoutClusterHeatmapScreen.kt`, `WorkoutClusterRepository.kt`
- **Rationale**: The system SHALL allow the user to manually rename a workout cluster and change its primary sport type via a dedicated edit dialog in the cluster's detail view.

### REQ-SET-017: Manual Workout Cluster Reassignment [Verified]
- **Tests**: `TST-SET-009`
- **Priority**: High
- **Type**: Functional
- **Verification Method**: Unit, Integration
- **Implementation**: `WorkoutClusterHeatmapScreen.kt`, `WorkoutClusterEngine.kt`
- **Rationale**: The system SHALL allow the user to manually reassign a workout to a different workout cluster via the heatmap view. Tapping a workout track SHALL show a list of candidate clusters sorted by similarity score.

### REQ-SET-018: Cluster Suggestions in Edit Workout [Verified]
- **Tests**: `TST-SET-010`, `TST-SET-046`
- **Priority**: High
- **Type**: Functional
- **Verification Method**: Unit, Integration
- **Implementation**: `EditWorkoutScreen.kt`, `EditWorkoutViewModel.kt`
- **Rationale**: The system SHALL provide a list of spatially similar cluster candidates when editing a workout's name. Selecting a candidate SHALL automatically update the workout's Name, Sport, and Cluster association.

### REQ-SET-019: Manual Cluster Creation [Implemented]
- **Tests**: `TST-SET-011`
- **Priority**: High
- **Type**: Functional
- **Verification Method**: Unit, Integration
- **Implementation**: `ManualClusterScreen.kt`, `WorkoutClusterEngine.kt`
- **Rationale**: The system SHALL allow the user to manually create a new workout cluster by specifying its spatial fingerprint (Start, End, Apex) and distance metrics via a dedicated UI.

### REQ-SET-020: Workout Peek in Heatmap [Verified]
- **Tests**: `TST-SET-012`
- **Priority**: High
- **Type**: Functional
- **Verification Method**: Unit, Integration
- **Implementation**: `WorkoutClusterHeatmapScreen.kt`
- **Rationale**: The system SHALL display a detailed peek (Bottom Sheet) when a workout track is tapped in the cluster heatmap, similar to the Periods view. This peek SHALL include an option to reassign the workout to a different cluster.

### REQ-SET-021: Manual Fingerprint Editing [Implemented]
- **Tests**: `TST-SET-013`
- **Priority**: High
- **Type**: Functional
- **Verification Method**: Unit, Integration
- **Implementation**: `WorkoutClusterHeatmapScreen.kt`, `WorkoutClusterRepository.kt`
- **Rationale**: The system SHALL allow the user to manually adjust the signature points (Start, End, Apex) of an existing workout cluster via the heatmap detail view.

### REQ-SET-022: Workout Markers in Cluster Detail [Verified]
- **Tests**: `TST-SET-014`
- **Priority**: High
- **Type**: Functional
- **Verification Method**: Unit, Integration
- **Implementation**: `WorkoutClusterHeatmapScreen.kt`, `WorkoutRepository.kt`
- **Rationale**: When viewing a workout cluster heatmap, the system SHALL display the spatial signature markers (Start, Stop, Apex) for EVERY member workout of the cluster to visualize spatial distribution. Additionally, it SHALL display all individual markers (Start, Stop, Extrema) for a specific workout when peeked. Clicking on any marker SHALL trigger the workout peek functionality.

### REQ-SET-023: Global Workout Unit Support [Verified]
- **Tests**: `TST-SET-015`
- **Priority**: High
- **Type**: Functional
- **Verification Method**: Unit, Integration
- **Implementation**: `ManualClusterScreen.kt`, `ClusterTuningScreen.kt`
- **Rationale**: The system SHALL support both Metric and Imperial units in the WorkoutCluster management UI. Distance entry and tuning visualization MUST use high-level units (km/mile) with appropriate precision to ensure professional-grade spatial definition.

### REQ-SET-024: Cluster Equipment Determination [Verified]
- **Tests**: `TST-SET-016`
- **Priority**: High
- **Type**: Functional
- **Verification Method**: Unit, Integration
- **Implementation**: `WorkoutClustersListScreen.kt`, `WorkoutClusterHeatmapScreen.kt`
- **Rationale**: The system SHALL dynamically determine and display the equipment associated with a Workout Cluster's majority sport. This SHALL use the technical mapping notation (→) consistent with the sensor management UI.

### REQ-SET-031: Route-to-Cluster Synchronization [Verified]
- **Tests**: `TST-SET-022`
- **Priority**: High
- **Type**: Functional
- **Verification Method**: Unit, Integration
- **Implementation**: `WorkoutClusterEngine.kt`, `RoutesRepository.kt`
- **Rationale**: Every explicit Route in the system (imported or created) SHALL have a corresponding Workout Cluster. Renaming a route SHALL automatically update the corresponding Workout Cluster name to ensure naming consistency across the spatial knowledge base.

### REQ-SET-033: Workout Cluster Deletion [Verified]
- **Tests**: `TST-SET-024`
- **Priority**: High
- **Type**: Functional
- **Verification Method**: Unit, Integration
- **Implementation**: `WorkoutClusterHeatmapScreen.kt`, `WorkoutClusterRepository.kt`
- **Rationale**: The system SHALL allow the user to delete a Workout Cluster via a long-click context menu on its detail header. Deleting a cluster SHALL nullify all associations with workouts and routes to maintain data integrity, while preserving the actual workout recordings.

### REQ-SET-032: Reference Route Visualization [Verified]
- **Tests**: `TST-SET-023`
- **Priority**: High
- **Type**: Functional
- **Verification Method**: Unit, Integration
- **Implementation**: `WorkoutClusterHeatmapScreen.kt`, `WorkoutClustersViewModel.kt`
- **Rationale**: When a Workout Cluster is linked to an explicit Route, the system SHALL display that route's path as a prominent reference layer on the detailed heatmap view to provide an authoritative spatial baseline.

### REQ-SET-061: Idempotent Route Cluster Creation [Verified]
- **Tests**: `TST-SET-047`
- **Priority**: High
- **Type**: Functional
- **Verification Method**: Unit, Integration
- **Implementation**: `WorkoutClusterEngine.kt`, `WorkoutClusterDatabaseManager.kt`
- **Rationale**: When learning a cluster from a workout with a specified route name, the system SHALL first search for an existing cluster with the exact normalized name before creating a new cluster. If a cluster with the same name exists, the system SHALL refine the existing cluster rather than creating a duplicate cluster appended with "var 2". **Invariants**: Spatial anchors (start/end/apex/bounds) MUST be updated when matching by name.

### REQ-SET-062: Automatic Zero-Workout Cluster Purging [Verified]
- **Tests**: `TST-SET-048`
- **Priority**: High
- **Type**: Functional
- **Verification Method**: Unit, Integration
- **Implementation**: `WorkoutClusterRepository.kt`, `WorkoutClusterDatabaseManager.kt`
- **Rationale**: The system SHALL automatically delete orphan route clusters that have zero actual workouts assigned in the database during repository refresh and post-import self-healing. **Invariants**: Clusters linked to an explicit Route (imported or created) MUST NOT be deleted even when they contain zero workouts. Clusters with 1 or more assigned workouts MUST NOT be deleted.

### REQ-SET-025: Workout Cluster Collapsing Header [Verified]
- **Tests**: `TST-SET-017`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit, Integration
- **Implementation**: `WorkoutClustersTabsScreen.kt`
- **Rationale**: The Workout Cluster list SHALL feature a collapsing top app bar that integrates with the list's scroll behavior, providing a modern and consistent navigation experience similar to the Workouts and Routes screens.

### REQ-SET-027: Persistent Scroll State [Verified]
- **Tests**: `TST-SET-019`
- **Priority**: Medium
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Implementation**: `WorkoutClustersFragment.kt`, `WorkoutClustersTabsScreen.kt`
- **Rationale**: The system SHALL preserve the scroll position and active tab of the Workout Cluster list when navigating back from a detail heatmap view.

### REQ-SET-026: Workout Cluster Tabbed Layout [Verified]
- **Tests**: `TST-SET-018`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit, Integration
- **Implementation**: `WorkoutClustersTabsScreen.kt`, `WorkoutClustersFragment.kt`
- **Rationale**: The Workout Cluster list SHALL feature a tabbed layout based on `BSportType` (All, Bike, Run, Other), matching the design pattern of Workouts, Segments, and Routes.

### REQ-SET-028: Unified Action Button Layout [Verified]
- **Tests**: `TST-SET-020`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit, Integration
- **Implementation**: `WorkoutClustersTabsScreen.kt`
- **Rationale**: The "Add Cluster" Floating Action Button SHALL use a standardized layout (manual Box alignment and navigationBarsPadding) consistent with the Sport Type and Equipment management screens.

### REQ-SET-029: Storage-Layer Identity Determination [Verified]
- **Tests**: `TST-SET-021`
- **Priority**: Critical
- **Type**: Functional
- **Verification Method**: Unit, Integration
- **Implementation**: `TrackerService.java`, `WorkoutClusterEngine.kt`
- **Rationale**: To ensure data consistency and prevent UI race conditions, the system SHALL determine a workout's Workout Cluster association and Auto-Name at the storage layer (immediately after tracking ends) rather than during UI mapping.

### REQ-SET-030: Sensor-First Identity Arbitration [Verified]
- **Tests**: `TST-SET-021`
- **Priority**: Critical
- **Type**: Functional
- **Verification Method**: Unit, Integration
- **Implementation**: `EquipmentAndSportTypeDiscoveryManager.kt`, `WorkoutClusterEngine.kt`
- **Rationale**: The system SHALL implement a confidence-based arbitration model to resolve conflicts between active hardware and route history. High-confidence sensor signals (e.g., uniquely linked hardware) SHALL veto route-based sport/gear overrides, while low-confidence sessions (GPS-only) SHALL be corrected by the Workout Cluster majority.

### REQ-SET-034: Stable Cluster Naming [Verified]
- **Tests**: `TST-SET-025`, `TST-SET-042`
- **Priority**: High
- **Type**: Functional
- **Verification Method**: Unit, Integration
- **Implementation**: `WorkoutClusterEngine.kt`
- **Rationale**: The system SHALL maintain the base name of a Workout Cluster when assigning it to a workout. The auto-generated workout name (including a hit counter) MUST NOT be propagated back to the cluster's permanent name. Manual session renames in the Edit Workout screen SHALL NOT rename the linked cluster.

### REQ-SET-037: Atomic Cluster Linking [Verified]
- **Tests**: `TST-SET-028`
- **Priority**: High
- **Type**: Functional
- **Verification Method**: Unit, Integration
- **Implementation**: `WorkoutClusterEngine.kt`, `WorkoutRepository.kt`
- **Rationale**: When a workout is assigned to a Workout Cluster, the system SHALL atomically update the workout's cluster association and increment the cluster's hit count. If reassigned from another cluster, the source cluster's count SHALL be decremented but the cluster MUST NOT be deleted automatically.

### REQ-SET-038: Self-Healing Hit Counts [Verified]
- **Tests**: `TST-SET-044`
- **Priority**: High
- **Type**: Functional
- **Verification Method**: Unit, Integration
- **Implementation**: `WorkoutClusterRepository.kt`, `WorkoutClusterSelectionDialog`
- **Rationale**: The system SHALL ensure that Workout Cluster hit counts are always accurate by either calculating them on-the-fly from the workout summary database or by performing a periodic integrity check that synchronizes the redundant `hit_count` column with the actual number of associated sessions. The UI selection dialog MUST display these accurate counts.

### REQ-SET-035: Preview Heatmap [Verified]
- **Tests**: `TST-SET-026`
- **Priority**: High
- **Type**: Functional
- **Verification Method**: Unit, Integration
- **Implementation**: `WorkoutClusterComponents.kt`, `WorkoutClusterRepository.kt`
- **Rationale**: The Workout Cluster list item SHALL display a representative spatial preview on its mini-map, consisting of the linked Route path (if available) or the 5 most recent workout tracks rendered as a low-opacity heatmap.

### REQ-SET-036: Persistent DB Connection [Verified]
- **Tests**: `TST-SET-027`
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Implementation**: `WorkoutClusterDatabaseManager.kt`
- **Rationale**: The Workout Cluster database SHALL remain open during the application lifecycle to facilitate real-time debugging and inspection via external tools.

### REQ-SET-039: Locale-Aware Distance Input [Verified]
- **Tests**: `TST-SET-030`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit, Integration
- **Implementation**: `ManualClusterScreen.kt`
- **Rationale**: The manual cluster creation interface SHALL support locale-specific decimal separators (e.g., comma in DE, dot in EN) when entering workout distances.

### REQ-SET-040: Unclustered Workout Access [Verified]
- **Tests**: `TST-SET-029`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit, Integration
- **Implementation**: `WorkoutClustersTabsScreen.kt`, `WorkoutClusterRepository.kt`
- **Rationale**: The system SHALL provide a dedicated view for workouts that are not associated with any Workout Cluster to facilitate manual organization and re-clustering.

### REQ-SET-056: Unclustered Workout Visualization [Verified]
- **Tests**: `TST-SET-040`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit, Integration
- **Implementation**: `WorkoutClusterComponents.kt`, `WorkoutClustersFragment.kt`, `TrackOnMapScreen.kt`
- **Rationale**: Unclustered workouts SHALL be displayed in the cluster management UI using a layout harmonized with `ClusterItem`, including a spatial mini-map preview. Clicking an unclustered workout SHALL navigate the user to a detailed map view of that workout, featuring a direct option to manually assign it to a cluster.

### REQ-SET-057: Cluster Member Listing [Verified]
- **Tests**: `TST-SET-041`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit, Integration
- **Implementation**: `WorkoutClusterComponents.kt`, `WorkoutClustersFragment.kt`, `WorkoutSummariesViewModel.kt`
- **Rationale**: The system SHALL allow the user to view all workouts associated with a specific Workout Cluster. Clicking the hit count (number of recordings) in the cluster list or details SHALL navigate the user to a filtered workout list containing only the members of that cluster.

### REQ-SET-058: Cluster Visibility & Direct Cluster Navigation in Workout Summary [Verified]
- **Tests**: `TST-SET-043`, `TST-SET-057`, `TST-SET-064`
- **Priority**: High
- **Type**: Functional
- **Verification Method**: Unit, Integration
- **Implementation**: `WorkoutHeader.kt`, `WorkoutHeaderData.kt`, `WorkoutData.kt`, `WorkoutSummary.kt`, `WorkoutList.kt`, `TrackOnMapScreen.kt`, `WorkoutNavigationEvents.kt`, `MainActivityWithNavigation.kt`, `WorkoutClustersFragment.kt`, `WorkoutClustersViewModel.kt`
- **Rationale**: The system SHALL display the associated Workout Cluster in the workout summary header (`WorkoutHeader.kt`) directly below the workout name:
1. *Interactive Navigation Button for Clustered Workouts (`clusterId > 0`)*:
When a workout has an associated Workout Cluster, the system SHALL display an interactive Material 3 Button mirroring the design and ergonomics of the cluster detail view's recordings button (`WorkoutClusterHeatmapScreen.kt`):
• Container Color: `MaterialTheme.colorScheme.primary.copy(alpha = TTAlpha.Subtle)` (light, subtle blue tint)
• Content Color: `MaterialTheme.colorScheme.primary`
• Shape: Rounded pill shape (compact height 28–32 dp)
• Content: Leading Route Cluster icon (`ic_favorite_route`) followed by the Cluster Name.
• Interaction: Tapping the button SHALL directly navigate the user to the cluster detail view (`WorkoutClusterHeatmapScreen`) for that specific cluster.
• Event Isolation: Clicks on the cluster button MUST be consumed and SHALL NOT trigger the general workout edit action (`onClicked`).
2. *Unclustered Workouts (`clusterId <= 0` or unclustered)*:
When a workout is not associated with any cluster, the system SHALL display the Route Cluster icon (`ic_favorite_route`) and the localized label `unclustered` as a neutral, non-interactive text row using secondary/onSurfaceVariant styling.
3. *Back Navigation Ergonomics*:
When the user navigates to a cluster detail view via this workout button, pressing the top app bar back arrow or system Back button SHALL pop the backstack and return the user directly to the originating workout view.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: `REQ-SET-058` (*Cluster Visibility & Direct Cluster Navigation in Workout Summary*) and `REQ-SET-059` (*Explicit Workout Cluster Management & Assignment in Editor*).
2. *Historical Origin & Commit Trace*: Sprint 2026-40.x (ATT-388, ATT-503, ATT-1398, ATT-3277).
3. *Root Reason for Existing Formulation*: `my_locations` was historically used as a general location icon across the app. Subsequently, `R.drawable.ic_favorite_route` was introduced specifically for route polylines in the drawer (`AppNavigationDrawer.kt:170`), while `R.drawable.my_locations` became the dedicated icon for point locations (`Lieblingsorte` / start and destination locations). Retaining `my_locations` on the cluster button caused two identical icons to appear side-by-side with conflicting meanings.
4. *Preservation of Core Invariants*: Sizing (14.dp in `WorkoutHeader`, default leading icon size in `EditWorkoutScreen`), touch targets, interactive cluster navigation (`onClusterClick`), and editor popup dialogs remain 100% unaltered.

**Acceptance Criteria (Given-When-Then)**:
• *Given* a workout associated with cluster `"Morning Run"` (`clusterId = 42`),
• *When* viewed in `WorkoutSummary` or `TrackOnMapScreen`,
• *Then* the header SHALL render an interactive subtle primary tinted button with the `ic_favorite_route` icon and `"Morning Run"`.
• *When* the cluster button is tapped,
• *Then* the system SHALL navigate to `WorkoutClustersFragment` displaying `WorkoutClusterHeatmapScreen` for cluster 42.
• *When* the back button is tapped in that heatmap view,
• *Then* the system SHALL return to the workout view.
• *Given* an unclustered workout (`clusterId = -1L`),
• *When* viewed in `WorkoutSummary` or `TrackOnMapScreen`,
• *Then* the header SHALL render the neutral, non-clickable "Unclustered" text.
**Invariants**: Workout editing via clicking the rest of `WorkoutHeader` MUST NOT be compromised; existing database schemas, clustering calculations, and cluster-to-recordings navigation (`REQ-SET-057`) MUST remain unaltered.

### REQ-SET-059: Explicit Workout Cluster Management & Assignment in Editor [Verified]
- **Tests**: `TST-SET-043`, `TST-SET-050`, `TST-SET-064`, `TST-SET-104`, `TST-SET-105`
- **Priority**: High
- **Type**: Functional
- **Verification Method**: Unit, Integration
- **Implementation**: `EditWorkoutScreen.kt`, `EditWorkoutViewModel.kt`, `WorkoutClusterComponents.kt`, `WorkoutClusterEngine.kt`, `WorkoutRepository.kt`, `WorkoutClustersScreen.kt`, `WorkoutClusterHeatmapScreen.kt`, `WorkoutClustersViewModel.kt`
- **Rationale**: The system SHALL provide an explicit, dedicated cluster management selector within `EditWorkoutScreen` positioned immediately below the workout name input field:
1. *Harmonized Selector UI*: The editor SHALL display an explicit cluster selector field displaying the route cluster icon (`ic_favorite_route`), label (`cluster_naming__selected_route_label`), and current cluster name (or `cluster_naming__leave_unclustered` / `unclustered` if unassigned), matching the look and feel of the TCX import naming dialog.
2. *Unified Assignment Dialog*: Tapping the cluster selector SHALL present an assignment dialog providing:
• **Leave Unclustered ("Kein Cluster")**: An explicit action to unassign the workout (`clusterId = -1L`), atomically decrementing the previous cluster's hit count.
• **Create New ("Neuer Cluster...")**: An explicit action to create a brand new Workout Cluster from the workout session's spatial fingerprint (Start, End, Apex, Distance) and assign it to the workout.
• **Candidate Clusters**: Spatially similar cluster candidates sorted by similarity score, displaying hit counts and sport types.
3. *Atomic Hit Count & Identity Maintenance*: Reassigning or clearing a cluster SHALL atomically synchronize `WorkoutSummaries.CLUSTER_ID` and update cluster hit counts in `WorkoutClusterDatabaseManager`.
4. *Move Workout Dialog Unassignment & Reactive State Synchronization (ATT-7754)*: Tapping the "Einheit verschieben" button (`R.string.cluster_move_workout_title`) across `WorkoutClustersScreen` and `WorkoutClusterHeatmapScreen` SHALL present the cluster selection sheet (`WorkoutClusterSelectionDialog`) offering:
• **Leave Unclustered ("Keiner Route zuordnen")**: Rendered when the workout is currently assigned to a cluster (`clusterId > 0`), which unassigns the session (`clusterId = -1L`), atomically decrements the previous cluster's hit count, and triggers reactive refresh of `WorkoutClusterRepository`.
• **Create New ("Neue Lieblingsstrecke erstellen")**: Creates a new cluster from the workout's spatial fingerprint.
• **Reactive Inspection State Sync**: When returning from `EditWorkoutScreen` or modifying cluster assignments in `WorkoutClustersScreen`, `inspectedWorkout` SHALL immediately update its state to reflect the latest workout summary.
5. *Immediate Workout Name Update on Cluster Assignment & Reassignment (ATT-7667)*:
• Selecting or creating a route cluster in `EditWorkoutScreen` SHALL immediately update the workout name input field to the cluster-formatted name (or resolved custom cluster name) and reset the internal manual-edit tracking flag (`userManuallyChangedWorkoutName = false`), ensuring subsequent background repository flow emissions adopt the newly assigned cluster identity.
• Unassigning a cluster SHALL reset `userManuallyChangedWorkoutName = false`, clear cluster fields (`clusterId = -1L`, `clusterName = null`), and restore the unclustered default workout name if the user had not manually typed a custom name post-selection.
• Explicit manual text edits entered into the workout name field post-cluster-selection SHALL re-engage manual tracking (`userManuallyChangedWorkoutName = true`) and MUST NOT be overwritten by subsequent background repository updates.
**Acceptance Criteria**:
• *Given* `EditWorkoutScreen` for any workout,
• *When* viewed,
• *Then* the cluster selector SHALL be prominently visible below the workout name displaying the current assignment with leading icon `ic_favorite_route`.
• *Given* the cluster assignment dialog,
• *When* the user chooses "Leave Unclustered",
• *Then* the workout SHALL be unassigned from any cluster (`clusterId = -1L`).
• *Given* the cluster assignment dialog,
• *When* the user chooses "Create New" with a name,
• *Then* a new cluster SHALL be created from the workout and assigned to it.
• *Given* `EditWorkoutScreen` with a workout assigned to a cluster or unclustered,
• *When* the user selects a different route cluster or creates a new cluster,
• *Then* the workout name SHALL immediately update to the cluster's formatted name and subsequent repository data emissions SHALL retain that name.
• *Given* `EditWorkoutScreen` with a cluster assigned,
• *When* the user chooses "Leave Unclustered",
• *Then* the workout SHALL be unassigned from any cluster (`clusterId = -1L`) and the name SHALL revert to the default unclustered seed name.
• *Given* `EditWorkoutScreen` after selecting a cluster,
• *When* the user types a custom workout name into the text field,
• *Then* subsequent repository emissions SHALL preserve the user's custom workout name.
**Invariants**: Existing sport type and equipment selections MUST NOT be mutated without user confirmation; non-target workout metrics MUST be preserved; existing hit count recalculation logic MUST NOT be corrupted.

### REQ-SET-060: Selectable Cluster Markers [Verified]
- **Tests**: `TST-SET-045`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit, Integration
- **Implementation**: `WorkoutClusterHeatmapScreen.kt`, `WorkoutClustersViewModel.kt`, `MyPreferenceManager.kt`
- **Rationale**: The system SHALL allow the user to toggle the visibility of individual marker types (Start, End, Max Distance) within the Workout Cluster heatmap. These markers SHALL be independently selectable for both the cluster signature and the session distribution distribution. The selection state MUST be persisted independently from the Periods module preferences.

### REQ-SET-042: Markers in Previews [Verified]
- **Tests**: `TST-SET-032`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit, Integration
- **Implementation**: `PathPreviewMap.kt`, `WorkoutSummary.kt`, `RouteItem.kt`, `WorkoutClusterComponents.kt`, `PeriodSummaryCard.kt`, `WorkoutClusterEngine.kt`
- **Rationale**: The system SHALL display Start, End, and Apex markers in the mini-map previews for Workouts. For Routes, ONLY Start and End markers SHALL be displayed. ALL map previews (Clusters, Workouts, Periods) SHALL correctly frame the entire route(s) based on persisted spatial bounds.

### REQ-SET-043: Fingerprint Parity [Verified]
- **Tests**: `TST-SET-033`, `TST-SET-034`, `TST-SET-035`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit, Integration
- **Implementation**: `WorkoutClusterEngine.kt`
- **Rationale**: To ensure 100% matching accuracy, the system SHALL calculate Route spatial fingerprints using 'Max Displacement from Start', matching the live tracking engine's logic. The similarity calculation SHALL strictly include a significant penalty (+2.0) if the workout's `BSportType` is NOT IDENTICAL to the cluster's `BSportType` (when enabled), preventing cross-activity cluster contamination even between known sports and 'UNKNOWN' placeholders.

### REQ-SET-044: Manual Name Isolation [Verified]
- **Tests**: `TST-SET-042`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit, Integration
- **Implementation**: `WorkoutRepository.kt`, `WorkoutClusterEngine.kt`
- **Rationale**: Renaming a workout session in the Edit Workout screen SHALL NOT trigger a rename of the associated Workout Cluster, preserving the route's permanent identity.

### REQ-SET-063: Workout Cluster Apex Arithmetic Mean & Live-Session Tracking Synchronization [Verified]
- **Tests**: `TST-SET-049`
- **Priority**: High
- **Type**: Functional
- **Verification Method**: Unit, Integration
- **Implementation**: `WorkoutClusterEngine.kt`, `WorkoutClustersViewModel.kt`, `SpeedAndLocationDevice.java`
- **Rationale**: The system SHALL ensure that a Workout Cluster's Max Line Distance (Apex) signature marker (`maxDispLat, maxDispLng`) strictly represents the running arithmetic mean of the maximum displacement points (`maxDisplacementLatLng`) across all member workouts in the cluster, preserving the centroid across member additions, deletions, and reassignments. The cluster's apex SHALL NOT be snapped or overwritten to arbitrary individual track coordinates during UI inspection or linked route resolution. Furthermore, when a live workout finishes, `WorkoutClusterEngine.onWorkoutFinished()` SHALL pass the workout's actual `maxDisplacementLatLng` (with fallback to `endLatLng`) as the apex rather than `startLatLng`. Additionally, `SpeedAndLocationDevice` SHALL reset `mStartLocation` upon accumulator reset so that live displacement tracking is anchored to the workout's actual start location.

### REQ-SET-064: Speed-Based Multi-Sport Route Clustering & Ambiguity Disambiguation in Tracker and Workout Editor [Verified]
- **Tests**: `TST-SET-051`, `TST-SET-056`
- **Priority**: High
- **Type**: Functional
- **Verification Method**: Unit, Integration
- **Implementation**: `TrackerService.java`, `EquipmentAndSportTypeDiscoveryManager.kt`, `WorkoutClusterEngine.kt`, `EditWorkoutViewModel.kt`, `SportTypeDatabaseManager.java`
- **Rationale**: The system SHALL evaluate cluster similarity against all candidate sport types supported by a workout session's speed profile both during live tracking completion (`TrackerService`) AND in the workout editor (`EditWorkoutViewModel.kt` / `EditWorkoutScreen.kt`), ensuring cost minimization without artificial mismatch penalties:
1. *Pre-Selection & Hardware Sovereignty*:
• If the user explicitly pre-selected a sport type prior to tracking and it is not `BSportType.UNKNOWN`, candidate sports SHALL be restricted strictly to `{userSelectedSport}`.
• If high-confidence hardware sensors (e.g., bike cadence, power meter, smart trainer) are active (`identity.isHighConfidence`), candidate sports SHALL be restricted strictly to `{hardwareSport}` (`REQ-SET-030`).
2. *Speed-Based Multi-Sport Candidate Set & Overlapping Sport Types*:
• In the absence of high-confidence hardware and explicit user pre-selection, the system SHALL query `EquipmentAndSportTypeDiscoveryManager` to retrieve all candidate `BSportType`s matching the session's average speed.
• When custom sport types have overlapping speed ranges with built-in sports (e.g., custom sport `Einkaufen` configured at 7–15 km/h with base sport `BIKE` overlapping `Running` at 7.2–14.4 km/h with base sport `RUN`), ALL matching base sports SHALL be included in the candidate sports set (`{RUN, BIKE}`).
• In `SportTypeDatabaseManager`, queries with `BSportType.UNKNOWN` SHALL retrieve all speed-matching sports regardless of `BASE_SPORT_TYPE`, ensuring that walking speeds or low-speed commute stops do not block candidate retrieval.
3. *Candidate-Aware Similarity Evaluation & Cost Minimization*:
• `WorkoutClusterEngine.suggestCluster()` and `calculateSimilarity()` SHALL accept the candidate sport set. If an existing cluster's `bSportType` matches ANY sport in the candidate set, the sport similarity penalty SHALL be 0.0, allowing geometrically matching clusters to pass the similarity threshold (< 1.0).
• In `EditWorkoutViewModel.fetchClusterSuggestions()`, the system SHALL derive candidate sports from `workout.avgSpeedMps` and invoke `getClusterScores(..., candidateSportTypes = candidateSports)`. Both matching sport clusters SHALL receive 0.0 sport penalty, and the cluster with the lower geometric similarity score (cost) SHALL be ranked first.
• In `EditWorkoutViewModel.updateSuggestedSportTypeNames()`, when the workout's base sport is `UNKNOWN` or unconstrained, the suggested sports dropdown SHALL include all sports matching the speed profile across all candidate base sports (e.g., including `Einkaufen`).
• If a cluster's `bSportType` is outside the candidate set, standard sport penalties (+2.0 for UNKNOWN, +5.0 for mismatched sport) SHALL be applied.
4. *Disambiguation & Identity Adoption*:
• When an ambiguous workout matches an existing cluster, the workout SHALL adopt the cluster's `bSportType`, route name, and associated equipment.
• If multiple existing clusters across different candidate sports match geometrically, the candidate cluster with the lowest overall similarity score (minimal cost) SHALL win tie-breaking.
**Acceptance Criteria**:
• *Given* an existing Cycling cluster on a route and a newly recorded workout with average speed ~10 km/h without sensors or pre-selection where both `Running` and custom sport `Einkaufen` match the speed,
• *When* tracking ends or when viewing `EditWorkoutScreen`,
• *Then* `WorkoutClusterEngine` SHALL evaluate both Running and Cycling as candidate sports, apply 0.0 sport penalty for both clusters, and select/rank the cluster with the lowest geometric similarity cost.
• *Given* an explicit user pre-selection of Running on the same route,
• *When* evaluated,
• *Then* the Cycling cluster SHALL receive a +5.0 penalty and SHALL NOT be matched.
• *Given* an active cycling cadence sensor,
• *When* evaluated,
• *Then* candidate sports SHALL be strictly `{BIKE}`.
**Invariants**: Dedicated hardware sensor sovereignty (`REQ-SET-030`) MUST be preserved; existing clusters with single-sport matching MUST NOT be degraded; manual cluster reassignment workflows MUST remain unchanged.

### REQ-SET-065: Altitude Extrema-Aware 3D Route Clustering & Database Migration [Verified]
- **Tests**: `TST-SET-052`, `TST-UNT-015`
- **Priority**: High
- **Type**: Functional
- **Verification Method**: Unit, Integration
- **Implementation**: `WorkoutClusterDatabaseManager.kt`, `WorkoutClusterEngine.kt`, `TrainingApplication.java`, `ClusterTuningScreen.kt`, `WorkoutClustersViewModel.kt`, `WorkoutClusterHeatmapScreen.kt`, `LegacyImportEngine.kt`, `ImportEngine.kt`
- **Rationale**: The system SHALL take the geographic coordinates of the minimum altitude point (`minAltitudePos`) and maximum altitude point (`maxAltitudePos`) into account when evaluating route cluster similarity and managing cluster fingerprints:
1. *Default State & Preference Toggle*: The system SHALL provide a user preference `use_altitude_pos_for_clustering` (`TrainingApplication.useAltitudePosForClustering()`) enabled by default (`true`).
2. *Dedicated Tolerance Control*: The system SHALL provide a dedicated `altitudePositionTolerance` slider in `ClusterTuningScreen.kt` under Detailed Controls (range: 10m–500m, default: 400m).
3. *Balanced 6-Term Similarity Formulation*: When enabled and altitude coordinates are present on both the workout and the cluster candidate, `WorkoutClusterEngine.calculateSimilarity()` SHALL calculate similarity using a balanced 6-term formula:
• Start Point: weight 0.20
• End Point: weight 0.20
• Apex (Max Line Distance): weight 0.20
• Total Distance: weight 0.20
• Min Altitude Position: weight 0.10
• Max Altitude Position: weight 0.10
(Sum of weights = 1.0).
4. *Graceful Fallback & Invariants*: If altitude position coordinates are absent on either the workout or the cluster candidate (e.g., flat routes, indoor sessions, or sensorless recordings), or if the preference is disabled, the system SHALL gracefully fall back to the standard 4-term spatial scoring (Start, End, Apex, Distance with weight 0.25 each) with zero penalty.
5. *TCX Import Synchronization*: During TCX import (`LegacyImportEngine.kt` and `ImportEngine.kt`), the parser SHALL track the trackpoint coordinates of lowest and highest elevation, persist them to `TABLE_EXTREMA_VALUES` under `SensorType.ALTITUDE` for `MIN` and `MAX`, and pass them to `suggestCluster()`.
6. *Database Schema & Historical Backfill Migration*:
• The `RouteClusters.db` schema SHALL be upgraded from Version 9 to 10 by adding nullable columns: `min_alt_lat REAL`, `min_alt_lng REAL`, `max_alt_lat REAL`, `max_alt_lng REAL`.
• During migration, the system SHALL backfill existing clusters by computing the mean minimum and maximum altitude coordinates across their associated member workouts from `WorkoutSummaries` and `TABLE_EXTREMA_VALUES`.
7. *Detail Map Visualization & Interactive Fingerprint Relocation*:
• `WorkoutClusterHeatmapScreen.kt` SHALL display Min Altitude and Max Altitude markers on the map, with independent visibility toggles in the marker options dropdown.
• In fingerprint editing mode (`isEditingFingerprint = true`), both Min Altitude and Max Altitude markers SHALL be draggable, allowing the user to manually reposition the altitude extrema anchors and persist the changes (`REQ-SET-021`).
**Acceptance Criteria**:
• *Given* two routes sharing identical Start, End, Apex, and Distance, but having distinct Min/Max altitude locations (> 400m apart),
• *When* cluster similarity is evaluated with altitude position clustering enabled,
• *Then* the altitude displacement terms SHALL push the similarity score $\ge 1.0$, preventing false cluster merging.
• *Given* a workout or cluster lacking altitude data,
• *When* evaluated,
• *Then* the system SHALL fall back to 4-term scoring without penalty.
• *Given* an existing database at version 9,
• *When* the app upgrades to version 10,
• *Then* existing clusters SHALL be backfilled with member workout altitude centroids.
**Invariants**: Existing 2D cluster geometries and non-elevation routes MUST NOT fail matching; user-selected sport and equipment rules MUST remain untouched.

### REQ-SET-066: Workout Cluster Sequential Auto-Naming Counter Ergonomics [Verified]
- **Tests**: `TST-SET-053`
- **Priority**: High
- **Type**: Functional
- **Verification Method**: Unit, Integration
- **Implementation**: `WorkoutClusterEngine.kt`, `TrackerService.java`, `EditWorkoutViewModel.kt`
- **Rationale**: The system SHALL format automated workout names derived from Workout Clusters (`WorkoutCluster`) such that the initial workout session of a cluster receives only the cluster's base name without any sequential number suffix, while subsequent sessions receive an incrementing numerical suffix (`#<count>`):
1. *First Workout Session Naming (`hitCount <= 1`)*:
When a workout is assigned to a cluster where the resulting hit count is 1 (the initial session in that cluster), the system SHALL set the workout's name to the exact cluster name (`cluster.name`) without appending `"#1"`.
2. *Subsequent Workout Sessions Naming (`hitCount >= 2`)*:
When a subsequent workout matches or is assigned to a cluster resulting in a hit count of 2 or greater, the system SHALL format the workout name using `@string/cluster_autoname_format` (`"%1$s #%2$d"`), appending `"#<count>"` (e.g., `"Morning Run #2"`, `"Morning Run #3"`).
3. *Unified Call Sites*:
This ergonomic naming policy SHALL apply uniformly across all workout naming avenues:
• Live tracking completion with cluster suggestion (`TrackerService`).
• Historical and TCX workout import assignment (`WorkoutClusterEngine.assignClusterToWorkout`).
• Manual cluster assignment in workout editor (`EditWorkoutViewModel`).
• Cluster creation from existing workout (`WorkoutClusterEngine.createNewClusterFromWorkout`).
4. *Matching & Deduplication Invariants*:
Normalization (`WorkoutClusterEngine.normalizeName`) and hit count stripping (`WorkoutClusterEngine.stripHitCount`) SHALL continue to cleanly strip both numbered suffixes (`#\d+`) and base cluster names without numerical suffixes, ensuring cluster clustering, suggestion scoring, and deduplication remain consistent.

**Acceptance Criteria (Given-When-Then)**:
1. *Given* a cluster named `"Morning Run"` with no prior workouts (`hitCount = 0`),
2. *When* a workout is assigned to this cluster,
3. *Then* the workout name SHALL be formatted as `"Morning Run"`.
4. *Given* a cluster named `"Morning Run"` with 1 existing workout (`hitCount = 1`),
5. *When* a second workout is assigned to this cluster,
6. *Then* the workout name SHALL be formatted as `"Morning Run #2"`.
7. *Given* a cluster with 5 existing workouts (`hitCount = 5`),
8. *When* a sixth workout is assigned to this cluster,
9. *Then* the workout name SHALL be formatted as `"Morning Run #6"`.

**Invariants**: The underlying mathematical clustering engine, 3D topological similarity scoring, and spatial tolerances MUST remain unaltered. Numbering for workouts with count $\ge 2$ MUST strictly preserve the `"%1$s #%2$d"` pattern. Room database entities and schemas MUST NOT change.

### REQ-SET-067: Optional Route Cluster Activity Counter / Auto-Numbering [Verified]
- **Tests**: `TST-SET-054`
- **Priority**: High
- **Type**: Functional
- **Verification Method**: Unit, Integration
- **Implementation**: `WorkoutClusterDatabaseManager.kt`, `WorkoutClusterEngine.kt`, `WorkoutClustersViewModel.kt`, `WorkoutClusterHeatmapScreen.kt`, `WorkoutClusterComponents.kt`, `TrackerService.java`, `EditWorkoutViewModel.kt`
- **Rationale**: The system SHALL provide a per-cluster configuration option (`hasCounter: Boolean`) determining whether workouts belonging to a Workout Cluster receive sequential numbered suffixes (`#<count>`):
1. *Database Schema & Persistence*:
• The `RouteClusters.db` SQLite schema SHALL be upgraded from Version 10 to Version 11 by adding column `has_counter INTEGER DEFAULT 1`.
• `WorkoutCluster` SHALL include `hasCounter: Boolean = true` (default `true` for 100% backward compatibility with all existing clusters).
2. *Formatting Rule Execution*:
• When `hasCounter == false`, `WorkoutClusterEngine.formatClusterWorkoutName(...)` SHALL always return the clean base cluster name (`clusterName`) without any numerical suffix (`#<count>`), regardless of hit count.
• When `hasCounter == true`, `formatClusterWorkoutName(...)` SHALL follow `REQ-SET-066` (no suffix for `hitCount <= 1`, appending `"#<count>"` for `hitCount >= 2`).
3. *Interactive UI Configuration*:
• `EditWorkoutClusterIdentityDialog` SHALL provide an interactive Switch/Toggle allowing users to enable or disable the activity counter for the cluster.
• `EditWorkoutClusterDialog` ("Create New Route...") SHALL provide a toggle for the counter on newly created routes (default: enabled).
4. *Unified Call Sites & Invariants*:
• All workout naming avenues (`assignClusterToWorkout`, `TrackerService.java`, `EditWorkoutViewModel.kt`) SHALL respect `cluster.hasCounter`.
• `cluster.hitCount` tracking in the database, 3D similarity calculations, and cluster deduplication MUST remain unaltered.

### REQ-SET-068: Comprehensive Workout Cluster Performance & Volume Statistics [Verified]
- **Tests**: `TST-SET-055`
- **Priority**: High
- **Type**: Functional
- **Verification Method**: Unit, Integration
- **Implementation**: `WorkoutSummariesDatabaseManager.java`, `WorkoutClusterRepository.kt`, `WorkoutClustersViewModel.kt`, `WorkoutClusterHeatmapScreen.kt`, `WorkoutClusterComponents.kt`, `strings.xml`
- **Rationale**: The system SHALL calculate and display comprehensive historical performance, cumulative volume, and activity recency statistics for each Workout Cluster (`WorkoutCluster`):
1. *Database Aggregation & Model*:
• The system SHALL execute a single-pass aggregate query in `WorkoutSummariesDatabaseManager` against `WorkoutSummaries.TABLE` grouped by `clusterId`:
`COUNT(*)` as total workouts,
`MAX(timeStart)` as latest workout date,
`AVG(speedAverage_mps)` as average speed,
`AVG(timeActive_s)` as average active duration,
`MIN(CASE WHEN finished = 1 AND timeActive_s > 0 THEN timeActive_s ELSE NULL END)` as personal record (Best Time),
`SUM(distanceTotal_m)` as total cumulative distance,
`SUM(ascending)` as total cumulative elevation gain,
`SUM(timeActive_s)` as total cumulative active moving time.
• The system SHALL encapsulate these values in `data class WorkoutClusterStats` and expose them via `WorkoutClusterRepository.clusterStats: StateFlow<Map<Long, WorkoutClusterStats>>` refreshed atomically alongside clusters.
2. *Semantic Information Architecture & Layout*:
• *Compact 2-Line Identity Header (`MapDetailLayout.header`)*:
  - Row 1: `WorkoutClusterIdentityRow` (Sport icon + Cluster name) + Top-End Action buttons (Edit Fingerprint `Icons.Default.EditLocationAlt`, Rename `Icons.Default.Edit`, Long-press Delete context menu).
  - Row 2 (viewing): Compact base metadata row (`RefDistance • SportName • LinkedEquipment`).
  - Row 2 (editing): Fingerprint marker-type `FilterChip` row (`Start`, `End`, `Apex`, `MinAlt`, `MaxAlt`).
• *Split-Pane Detail Analytics Dashboard (`MapDetailLayout.analyticsContent`)*:
  - The three-tier dashboard is hosted in the scrollable lower pane beneath `MapDetailLayout`'s draggable `SplitPaneDivider`, enabling the divider and allocating the central viewport (~60–70% height) to the cluster map.
  - Detail Content Tabs: A compact tab switcher allows athletes to toggle between:
    a) *Tab 1 (Statistik / Details)*:
       - *Performance Benchmarks*: Best Time (highlighted with trophy accent), Average Time, and Sport-Adaptive Velocity (Pace for running/walking, Speed for cycling/other).
       - *Cumulative Totals*: Total Distance, Total Climbing, and Total Moving Time.
       - *Activity & Recency*: Interactive Recordings Count button (tapping navigates to filtered workout list) and Last Activity date with relative recency (e.g., `"06.09.2026 (3 days ago)"`).
    b) *Tab 2 (Leistungsverlauf / Effort Progression)*: Hosts `WorkoutClusterTrendChartCard` when valid trend workouts count $N \ge 2$. (When $N < 2$, Tab 1 is displayed directly without redundant tab bar).
• *List Card View (`ClusterItem` / `WorkoutClusterMetadataBlock`)*: The card SHALL display a compact metadata block featuring Best Time (🏆), Total Distance, and Last Hit date alongside the square map preview without visual crowding.
3. *Edge Cases & Invariants*:
• Clusters with 0 recorded workouts SHALL gracefully display default empty states without NaN, null, or division-by-zero crashes.
• Unfinished workouts (`finished = 0`) or 0-duration records MUST NOT be selected as Best Time.
• Spatial clustering calculations, fingerprint anchors, database schema structure, and filter/sort pipelines MUST remain unaffected.

**Acceptance Criteria (Given-When-Then)**:
1. *Given* a Workout Cluster with 3 completed workouts (times: 40m, 35m, 45m; distances: 10km, 10km, 10km; ascents: 100m, 120m, 110m),
2. *When* statistics are calculated and displayed,
3. *Then* the system SHALL report Best Time = 35m, Average Time = 40m, Total Distance = 30km, and Total Climbing = 330m.
4. *Given* a Running cluster,
5. *When* velocity is displayed,
6. *Then* the system SHALL format the metric as average pace (e.g. `4:30 /km`).
7. *Given* a newly created cluster with no sessions,
8. *When* viewed in list or detail view,
9. *Then* the system SHALL display empty/placeholder stats safely without errors.
10. *Given* `WorkoutClusterHeatmapScreen`,
11. *When* rendered in standard viewing mode,
12. *Then* `MapDetailLayout.header` renders only the compact 2-line identity and metadata row, while the 3-tier performance & volume dashboard is hosted in `MapDetailLayout.analyticsContent` below a draggable `SplitPaneDivider`.

**Invariants**: Cluster fingerprinting, apex anchoring, database schema, and existing cluster filtering/sorting MUST NOT be modified.

### REQ-SET-069: Workout, Route & Segment Detail Bottom Sheet Scaffold Status Bar Boundary Constraint [Implemented]
- **Tests**: `TST-SET-058`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit, Integration
- **Implementation**: `PeriodMapScreen.kt`, `MapFragmentWithTrack.kt`, `WorkoutClusterHeatmapScreen.kt`
- **Rationale**: The system SHALL constrain the maximum upward expansion of bottom sheet scaffolds hosting Workout, Route, and Segment details to the bottom edge of the Android system status bar (`WindowInsets.statusBars`):
1. *Scaffold Top Boundary Alignment*: In `PeriodMapScreen`, `MapFragmentWithTrack`, and `WorkoutClusterHeatmapScreen`, the `BottomSheetScaffold` composable SHALL apply `Modifier.statusBarsPadding()` to align the top edge of the scaffold container with the bottom boundary of the system status bar (`Y = statusBarHeight`).
2. *Status Bar Overlap Prevention*: When the bottom sheet is dragged or animated to its fully expanded state (`SheetValue.Expanded`), the top of the sheet SHALL stop at `0f` relative to the scaffold (matching the status bar bottom edge), preventing the sheet title, drag handle, or workout metadata from colliding with or drawing behind system status bar icons (clock, battery, notifications).
3. *Peek Height & Drag Handle Integrity*: The bottom sheet's peek height (`sheetPeekHeight`) SHALL continue to be measured from the bottom edge of the screen / navigation bars without offset distortion. The drag handle (`MinimumDragHandle`) SHALL retain its standard compact dimensions (~24 dp) and minimal padding, ensuring all peeked content remains fully visible in the initial popup without excessive whitespace or truncation.

**Acceptance Criteria (Given-When-Then)**:
• *Given* a workout detail peek in `PeriodMapScreen`, `WorkoutClusterHeatmapScreen`, or a segment/route detail peek in `MapFragmentWithTrack`,
• *When* the bottom sheet is in its initial peeked state,
• *Then* the sheet SHALL render with compact drag handle dimensions and the initial header content SHALL be fully visible without truncation.
• *When* the user drags or expands the bottom sheet to its maximum height (`SheetValue.Expanded`),
• *Then* the top edge of the sheet SHALL stop cleanly at the bottom boundary of the system status bar, and zero header content or drag handle graphics SHALL draw underneath the status bar icons.

**Invariants**: `sheetPeekHeight` calculations, `MinimumDragHandle` layout dimensions, and underlying map rendering logic MUST NOT be broken.

### REQ-SET-070: Direct Navigation to Workout Editor from Workout Detail Popup & Unified Action Button Layout [Verified]
- **Tests**: `TST-SET-059`
- **Priority**: High
- **Type**: Functional
- **Verification Method**: Unit, Integration
- **Implementation**: `TrackOnMapScreen.kt`, `WorkoutHeader.kt`, `PeriodMapScreen.kt`, `PeriodsFragment.kt`, `WorkoutClusterHeatmapScreen.kt`, `WorkoutClustersFragment.kt`
- **Rationale**: The system SHALL allow users to directly open the Workout Editor (`EditWorkoutScreen`) from the Workout Detail Popup (`TrackOnMapScreen` / `WorkoutHeader`) in analytical map views (Period Map and Favorite Tracks / Cluster Heatmap), and SHALL enforce a unified action button layout where the edit action is positioned on the very right:
1. *Workout Popup Edit Trigger*:
• `TrackOnMapScreen` and `WorkoutHeader` SHALL accept an optional callback `onEditWorkout: ((Long) -> Unit)? = null`.
• When `onEditWorkout != null`, `WorkoutHeader`'s top-end action row SHALL render a compact `32.dp` `IconButton` with `Icons.Default.Edit`, tinted with `MaterialTheme.colorScheme.primary` and localized content description `@string/edit_workout`. Tapping this button SHALL invoke `onEditWorkout(workoutData.id)`.
• Tapping the workout header surface itself (`onClicked`) SHALL also invoke `onEditWorkout(workoutData.id)`.
2. *Unified Action Button Ordering*:
• *Workout Popup (`WorkoutHeader`)*: The Edit Workout button SHALL be positioned on the **very right** of the action row. Contextual actions (e.g., `SwapHoriz` for cluster transfer in the cluster heatmap) SHALL be positioned to the left of the edit button.
• *Favorite Tracks Header (`ClusterSummaryHeader`)*: The action buttons in the top-end row of the Lieblingsstrecken header SHALL be positioned such that Edit Locations (`EditLocationAlt`) is on the left and Edit Name/Identity (`Icons.Default.Edit`) is on the **very right**.
3. *Host Navigation & State Integration*:
• `WorkoutClustersFragment` and `WorkoutClusterHeatmapScreen`: Passing `onEditWorkout = { workoutId -> editedWorkoutId = workoutId }` SHALL display `EditWorkoutScreen`, returning cleanly to the cluster view upon dismissal via system back or top back arrow.
• `PeriodsFragment` and `PeriodMapScreen`: `PeriodsFragment` SHALL maintain state `editedWorkoutId: Long?` and conditionally display `EditWorkoutScreen`, returning cleanly to `PeriodMapScreen` upon dismissal and refreshing period summaries via `loadPeriods()`.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an athlete viewing a workout popup in `PeriodMapScreen` or `WorkoutClusterHeatmapScreen`,
• *When* the popup is displayed,
• *Then* an edit icon button (`Icons.Default.Edit`) SHALL be visible on the very right of the workout header action row.
• *When* the athlete taps either the edit icon button or the workout header surface,
• *Then* `EditWorkoutScreen` SHALL be displayed for that workout.
• *When* the athlete saves or navigates back from `EditWorkoutScreen`,
• *Then* the screen SHALL return to the map view with the popup intact and updated workout data reflected.
• *Given* the Favorite Tracks detail screen (`WorkoutClusterHeatmapScreen`),
• *Then* `ClusterSummaryHeader` SHALL display `EditLocationAlt` on the left and `Icons.Default.Edit` on the very right.

**Invariants**: Existing export menus, deletion context menus, cluster transfer dialogs, and map drawing logic MUST NOT be altered or regressed.

### REQ-SET-071: Consistent Workout Click-to-Map Navigation & Dedicated Header Edit Action [Verified]
- **Tests**: `TST-SET-060`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit, Integration
- **Implementation**: `WorkoutSummary.kt`, `WorkoutHeader.kt`, `WorkoutSummaryCompact.kt`, `WorkoutSummariesTabbedFragment.kt`, `WorkoutSummariesListFragment.kt`
- **Rationale**: The system SHALL navigate to the workout on map screen (`TrackOnMapScreen`) whenever an athlete clicks any primary content section of a workout summary card (`WorkoutSummary.kt`), while reserving the workout editor (`EditWorkoutScreen`) strictly for the dedicated edit action button:
1. *Click-to-Map Navigation for Workout Summary Body*:
• In `WorkoutSummary.kt`, tapping any of the following body areas SHALL navigate to `TrackOnMapScreen` via `onMapClick(workoutData)`:
- The workout header (`WorkoutHeader` surface via `onClicked = { onMapClick(workoutData) }`)
- The workout description (`WorkoutDescription`)
- The workout details/metrics section (`WorkoutDetails`)
- The workout extrema section (`WorkoutExtrema`)
- The map preview / elevation section (`WorkoutMediaSection`)
2. *Dedicated Header Edit Button & Action Ordering*:
• `WorkoutHeader` SHALL render a dedicated edit icon button (`Icons.Default.Edit`, `32.dp`, primary tint) with localized content description `@string/edit_workout` when `onEditWorkout` is provided.
• In `WorkoutHeader`, the edit button SHALL be positioned to the left of the trailing 3-dots export dropdown button (`more_vert`) (after `actions()` and before `more_vert`).
• Tapping this dedicated edit button SHALL invoke `onEditWorkout()` to launch `EditWorkoutScreen`.
3. *Preserved Exceptions*:
• *Cluster Button*: When a workout is associated with a cluster (`clusterId > 0`), tapping the cluster pill button in `WorkoutHeader` SHALL navigate directly to the linked cluster heatmap screen via `onClusterClick(clusterId)` without triggering map navigation or edit mode.
• *Export Status*: Tapping the `ExportStatus` component SHALL open its internal `ExportDetailsDialog` without triggering map navigation or edit mode.
• *Export Menu (3-dots)*: Tapping the 3-dots icon button SHALL open the export options dropdown menu without triggering map navigation or edit mode.
4. *Compact Workout Summary Alignment*:
• In `WorkoutSummaryCompact.kt`, tapping the compact card continues to navigate to `TrackOnMapScreen` via `onMapClick(workoutData)`. An edit option SHALL be accessible via the long-press context menu or explicit action.
5. *Map View Edit Trigger Navigation Precedence*:
• When viewing a workout on map in `TrackOnMapScreen` within `WorkoutSummariesTabbedFragment` or `WorkoutSummariesListFragment`, tapping the edit action button SHALL immediately launch `EditWorkoutScreen` without requiring back navigation.
• Dismissing `EditWorkoutScreen` SHALL return directly to `TrackOnMapScreen`, and subsequent back navigation from `TrackOnMapScreen` SHALL return cleanly to the workout list.

**Acceptance Criteria (Given-When-Then)**:
• *Given* a workout displayed in `WorkoutSummariesTabbedFragment` or `WorkoutSummariesListFragment`,
• *When* the athlete taps the workout header title/surface, description, metrics details, extrema, or map preview,
• *Then* the system SHALL navigate directly to the detailed map screen (`TrackOnMapScreen`) for that workout.
• *When* the athlete taps the edit icon button (`Icons.Default.Edit`) situated before the 3-dots menu in the workout header,
• *Then* the system SHALL open `EditWorkoutScreen` for that workout.
• *Given* an athlete viewing `TrackOnMapScreen` in `WorkoutSummariesTabbedFragment` or `WorkoutSummariesListFragment`,
• *When* the athlete taps the edit icon button,
• *Then* the system SHALL immediately display `EditWorkoutScreen`, and dismissing the editor SHALL return directly to `TrackOnMapScreen`.
• *Given* a clustered workout,
• *When* the athlete taps the cluster button in the header,
• *Then* the system SHALL navigate directly to the cluster heatmap screen (`WorkoutClustersFragment`) for that cluster.
• *When* the athlete taps the export status badge,
• *Then* the system SHALL display `ExportDetailsDialog`.

**Invariants**: Backstack navigation returning from `TrackOnMapScreen` or `EditWorkoutScreen` MUST preserve list scroll position; cluster calculation and export processing MUST NOT be modified.

### REQ-SET-072: Edit Workout New Cluster Creation & Name Synchronization [Verified]
- **Tests**: `TST-SET-061`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit, Integration
- **Implementation**: `EditWorkoutScreen.kt`, `EditWorkoutViewModel.kt`, `WorkoutClusterEngine.kt`, `WorkoutClusterComponents.kt`, `WorkoutRepository.kt`
- **Rationale**: The system SHALL ensure that creating a new cluster from within the Edit Workout dialog cleanly updates both the cluster assignment and the workout name in accordance with user intent:
1. *Dialog Pre-fill Ergonomics*: When opening the \"Create New Route\" section in `EditWorkoutClusterDialog`, the new route name field SHALL pre-fill with the base name stripped of any numerical counter suffix (`WorkoutClusterEngine.stripHitCount(initialWorkoutName)`).
2. *Deterministic Database Synchronization (`forceIdentity = true`)*: `WorkoutClusterEngine.createNewClusterFromWorkout` SHALL invoke `assignClusterToWorkout` with `forceIdentity = true`, ensuring `WorkoutSummaries.WORKOUT_NAME` and `CLUSTER_ID` are updated in SQLite to the newly created cluster's identity.
3. *Smart Workout Name Preservation vs. Auto-Naming*:
• If the user has manually typed a custom workout name on `EditWorkoutScreen` (`userManuallyChangedWorkoutName == true`), that typed name SHALL be preserved and MUST NOT be overwritten by the new cluster's name or background database reload.
• If the workout name was not manually typed (i.e., it still reflected the old cluster's name or default pattern), creating a new cluster SHALL automatically update the workout name to the new cluster's formatted name (e.g. `\"Neue Hausrunde\"` without `#1` suffix per `REQ-SET-066`).
4. *Reactive UI State Consistency*: `EditWorkoutViewModel.createNewCluster` SHALL update `_workoutData` in memory synchronously so that `currentClusterName` and `workoutName` immediately reflect the new route upon dialog dismissal.

**Acceptance Criteria (Given-When-Then)**:
• *Given* a workout matched to cluster `\"Hausrunde #3\"` where the user did not edit the workout name field,
• *When* the user creates a new cluster named `\"Neue Runde\"` from `EditWorkoutClusterDialog`,
• *Then* the workout name SHALL become `\"Neue Runde\"` and the Selected Route field SHALL display `\"Neue Runde\"` immediately on `EditWorkoutScreen` and in SQLite.
• *Given* a workout where the user manually typed `\"My Special Ride\"` on `EditWorkoutScreen`,
• *When* the user creates a new cluster named `\"Sunday Loop\"`,
• *Then* the workout name SHALL remain `\"My Special Ride\"` while the Selected Route field updates to `\"Sunday Loop\"`.

**Invariants**: Previous cluster hit count decrement and atomic new cluster creation MUST be preserved; existing manual cluster selection flows MUST NOT be altered.

### REQ-SET-073: Dedicated Advanced Tuning Preferences Screen with Parameter Clamping and Reset-to-Defaults [Verified]
- **Tests**: `TST-SET-062`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit, Integration
- **Implementation**: `TuningPreferencesDataStore.kt`, `AdvancedTuningDialog.kt`, `BatterySaverController.kt`, `BatterySaverStateMachine.kt`, `SpeedAndLocationDevice.java`, `VerticalSpeedAndSlopeDevice.java`
- **Rationale**: The system SHALL provide an isolated, expert-level configuration screen for sensor filtering heuristics and AMOLED battery saver parameters, featuring strict value clamping and factory reset capabilities:
1. *Dedicated Navigation & Modal Integration*:
• The system SHALL provide a dedicated navigation drawer item `R.id.drawer_advanced_tuning` under `drawer__settings`, mapping to `SettingsBottomSheetType.ADVANCED_TUNING` in `NavRoutes.kt`.
• The Display Settings dialog (`DisplaySettingsDialog.kt`) SHALL also provide an explicit navigation link to launch the Advanced Tuning screen.
• The screen SHALL render a prominent advisory warning notice card informing users that parameters alter core tracking heuristics and battery performance.
2. *Configurable Parameter Scope & Clamping Invariants*:
• *Full Dimming Brightness*: Configurable between 5% and 50% (`0.05f .. 0.50f`), default 25% (`0.25f`). Clamped above the 5% safety floor.
• *Medium Dimming Brightness*: Configurable between 20% and 90% (`0.20f .. 0.90f`), default 50% (`0.50f`). Clamped $\ge$ full dimming brightness.
• *Flat Slope Cutoff*: Configurable between 0.0% and 5.0% (`0.0f .. 5.0f`), default 2.0%.
• *Steep Slope Cutoff*: Configurable between 2.0% and 15.0% (`2.0f .. 15.0f`), default 5.0%. Clamped $\ge$ flat slope cutoff.
• *Wake-up Duration*: Configurable between 5s and 60s (`5 .. 60`), default 15s.
• *Downward Damping Delay*: Configurable between 1s and 15s (`1 .. 15`), default 3s.
• *GPS Accuracy Threshold*: Configurable between 10m and 500m (`10.0f .. 500.0f`), default 200m.
• *Altitude Smoothing Window*: Configurable between 5s and 60s (`5 .. 60`), default 21s.
• *Slope Minimum Speed*: Configurable between 0.2 m/s and 2.0 m/s (`0.2f .. 2.0f`), default 0.5 m/s.
3. *Contextual Documentation & Factory Reset*:
• Each tuning item SHALL display an explanatory helper text detailing its algorithmic effect and default value tag (`Standard: X`).
• The bottom action row SHALL include a prominent single-tap action to restore all parameters atomically to factory defaults.
4. *Reactive Architecture & Synchronous Java Bridge*:
• Persisted via the unified `user_settings` DataStore (`TuningPreferencesDataStore.kt`) emitting typed Kotlin `Flow`s.
• Active tracking controllers (`BatterySaverController.kt`, `BatterySaverStateMachine.kt`) and sensor drivers (`SpeedAndLocationDevice.java`, `VerticalSpeedAndSlopeDevice.java`) SHALL adopt parameter changes immediately without app restart.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: `REQ-SET-052` (Display settings modal), `ATT-1268` (AMOLED Battery Saver), and `SpeedAndLocationDevice.java` / `VerticalSpeedAndSlopeDevice.java` constant literals.
2. *Historical Origin & Commit Trace*: `ATT-1268` (`dimmingLevel` hardcoding), `VerticalSpeedAndSlopeDevice` (`cAltitudeFilter = 21`, `MIN_SPEED = 0.5`), and `SpeedAndLocationDevice` (`ACCURACY_THRESHOLD = 200`).
3. *Root Reason for Existing Formulation*: Constants were embedded directly in device drivers and controllers during initial prototyping without a unified preferences schema or UI.
4. *Preservation of Core Invariants*: Minimum brightness safety floors (5%), division-by-zero guards on minimum speed ($\ge 0.2\text{ m/s}$), and atomic factory reset safeguards prevent corrupted state or unreadable displays.

**Acceptance Criteria (Given-When-Then)**:
• *Given* the navigation drawer or Display Settings dialog,
• *When* the athlete selects \"Advanced Tuning\",
• *Then* `AdvancedTuningDialog` SHALL open displaying the advisory warning card and all tuning sliders with default values indicated.
• *When* the athlete adjusts any slider (e.g. GPS Accuracy to 50m) and saves,
• *Then* `TuningPreferencesDataStore` SHALL persist the clamped value, and active tracking components SHALL immediately apply the updated parameter.
• *When* the athlete taps \"Reset to Defaults\",
• *Then* all tuning parameters SHALL atomically revert to factory defaults and the UI controls SHALL update immediately.

**Invariants**: Safety floor 5% brightness, positive non-zero speeds ($\ge 0.2\text{ m/s}$), non-inverted slope/brightness thresholds, and 9-language localization parity MUST be preserved.

### REQ-SET-074: Expert Settings Drawer Positioning, Localization & 50m Default GPS Tolerance [Verified]
- **Tests**: `TST-SET-063`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit, Integration
- **Implementation**: `AppNavigationDrawer.kt`, `TuningPreferencesDataStore.kt`, `strings.xml`
- **Rationale**: The system SHALL sequester expert-level tuning preferences into a dedicated section at the bottom of the navigation drawer, align terminology to "Expert Settings" across 9 languages, and tighten default GPS accuracy tolerance to 50 meters (ATT-1646):
1. *Dedicated Bottom Drawer Section (`AppNavigationDrawer.kt`)*:
• In `createDrawerGroups`, the navigation item `R.id.drawer_advanced_tuning` SHALL be removed from `drawer__settings` and placed into a dedicated, final `DrawerGroup` situated at the bottom of the drawer.
• The dedicated section SHALL use header string resource `R.string.drawer__expert_settings`.
• The automatic horizontal divider preceding the final group SHALL visually separate expert settings from standard user preferences.
2. *Naming & 100% 9-Language Localization Parity*:
• The terminology across all dialogs and drawer items SHALL be unified to "Expert Settings" (*Experten-Einstellungen*):
• String resources `drawer__expert_settings`, `advanced_tuning_title`, and `advanced_tuning_open` SHALL be updated across all 9 supported locales (DE, EN, ES, FR, IT, JA, NL, PL, PT).
3. *Default GPS Accuracy Tolerance (`TuningPreferencesDataStore.kt`)*:
• `TuningPreferencesDefaults.GPS_ACCURACY_THRESHOLD_M` SHALL be standardized to `50.0f` (reduced from `200.0f`).
• Fresh application installations and factory resets via `resetToDefaults()` SHALL initialize `gpsAccuracyThresholdMeters` to `50.0f`.
4. *Preservation of Core Invariants*:
• Navigation item ID `R.id.drawer_advanced_tuning` and bottom sheet route mapping `SettingsBottomSheetType.ADVANCED_TUNING` remain identical.
• DataStore preference keys (`tuning_gps_accuracy_threshold`) and slider clamping bounds ($10.0\text{m} .. 500.0\text{m}$) remain 100% intact.
• Saved custom athlete preferences are strictly preserved across upgrades.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: `REQ-SET-073` (*Dedicated Advanced Tuning Preferences Screen with Parameter Clamping and Reset-to-Defaults*).
2. *Historical Origin & Commit Trace*: Ticket `ATT-1304` (Sprint `2026-40.4`).
3. *Root Reason for Existing Formulation*: In `ATT-1304`, the tuning item was grouped inside general settings for speed of implementation, and the GPS default was inherited from legacy constants (`SpeedAndLocationDevice.ACCURACY_THRESHOLD = 200`).
4. *Refinement Reason*: Post-sprint user review determined that German "Erweiterte Abstimmung" was unnatural, mixing expert controls with standard settings created user confusion, and 50m provides significantly cleaner GPS tracks out-of-the-box.
5. *Preservation of Core Invariants*: Safety clamping, factory reset mechanics, non-blocking DataStore persistence, and 9-language translation parity are fully maintained.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an athlete opening the navigation drawer,
• *When* inspecting the categories,
• *Then* "Experten-Einstellungen" (or "Expert Settings") SHALL appear as a dedicated category at the very bottom of the drawer, separated by a horizontal divider below general settings, containing the `drawer_advanced_tuning` item.
• *Given* the application running in any of the 9 supported locales,
• *When* viewing the drawer section title, dialog header, or trigger button,
• *Then* the label SHALL display the localized "Expert Settings" string without missing translations.
• *Given* a new installation or an athlete tapping "Standard wiederherstellen",
• *When* inspecting `gpsAccuracyThresholdMeters`,
• *Then* the threshold SHALL evaluate to `50.0m` (`50.0f`).

**Invariants**: Navigation route mapping, DataStore keys, slider clamping, and factory reset behavior MUST be preserved.

### REQ-SET-075: Dedicated Workout Route Clustering Section in Advanced Settings, Configurable Score Threshold, Single Source of Truth, and Modularity Preservation [Verified]
- **Tests**: `TST-SET-077`, `TST-UI-318`, `TST-UI-321`
- **Priority**: High
- **Type**: Functional
- **Verification Method**: Unit, Integration
- **Tickets**: `ATT-3355`, `ATT-3452`, `ATT-5559`
- **Implementation**: `ClusterTuningSection.kt`, `WorkoutClustersTabsScreen.kt`, `WorkoutClustersScreen.kt`, `ClusterTuningContent.kt`, `AdvancedTuningAccordion.kt`, `AdvancedTuningScreen.kt`, `TuningPreferencesDataStore.kt`
- **Rationale**: The system SHALL consolidate the canonical configuration of workout route clustering algorithmic parameters into Advanced Settings (`AdvancedTuningScreen.kt` / `AdvancedTuningDialog.kt`) as a dedicated modular accordion section, adhering to the Single Source of Truth and DRY principles (Rule 36), and SHALL provide a user-configurable cluster assignment score threshold (default 1.2f, range 0.5f..2.5f, step 0.1f) in `ClusterTuningContent.kt` and `PreImportTuningBottomSheet` (ATT-3355, amended by ATT-3452 and ATT-5559, amending `REQ-SET-010` and `REQ-SET-065`, superseding `REQ-SET-014`, referencing `REQ-UI-357`):
1. *Modular Accordion Section Architecture (`ClusterTuningSection.kt`, `AdvancedTuningAccordion.kt`)*:
• The system SHALL add `ROUTE_CLUSTERING` to `TuningSection` enum in `AdvancedTuningAccordion.kt`, positioned in Phase C (History, Routes & Post-Workout) between `WORKOUT_MASKS_CARDS` and `AFTERMATH_ANALYSIS`.
• The system SHALL create `ClusterTuningSection.kt` under `ui.settings.tuning.categories`, hosting the clustering parameters UI.
• The accordion card SHALL render `Icons.Default.Hub`, localized title `R.string.tuning_cat_route_clustering` (EN: "Route & Workout Clustering", DE: "Routen- & Workout-Clustering"), animated chevron rotation, and initially collapsed state (`emptySet()`).
• `TuningSubtitleFormatter` SHALL provide `formatClusterTuningSubtitle(...)`, generating a live formatted summary string of active tolerances and toggles (e.g. `End: 200m · Apex: 400m · Dist: 20% · Alt: 400m` in metric mode, or imperial equivalent with mi/ft).
2. *Single Source of Truth, DRY Refactoring & Header Tuning Elimination (Rule 36, ATT-3452)*:
• The shared UI controls and validation logic SHALL be encapsulated in `ClusterTuningContent.kt` (< 250 lines), shared identically with zero code or layout duplication.
• The cluster tuning settings icon button (`onTuneClick`) SHALL be completely removed from `WorkoutClustersTabsScreen.kt`, and `ClusterTuningScreen.kt` and `isTuning` state handling SHALL be excised from `WorkoutClustersScreen.kt` in favor of centralized Advanced Settings.
• `ImportBackupTabsScreen.kt` SHALL consume `ClusterTuningContent` for pre-import sensitivity adjustments.
3. *DataStore, Defaults & Atomic Factory Reset Integration (ATT-5559)*:
• `TuningPreferencesDefaults` SHALL define defaults: `CLUSTER_TOL_ENDPOINTS = 200f`, `CLUSTER_TOL_APEX = 400f`, `CLUSTER_TOL_DISTANCE = 0.20f`, `CLUSTER_TOL_ALTITUDE_POS = 400f`, `CLUSTER_USE_SPORT_TYPE = true`, `CLUSTER_USE_ALTITUDE_POS = true`, and `CLUSTER_SCORE_THRESHOLD = 1.2f` (range `0.5f..2.5f`, step `0.1f`, ATT-5559).
• `TuningConfig` SHALL incorporate these 7 clustering parameters.
• `TuningPreferencesDataStore` SHALL manage persistence, updating both DataStore and `TrainingApplication.cSharedPreferences` (`SP_CLUSTER_SCORE_THRESHOLD = "cluster_score_threshold"`, with `getClusterScoreThreshold()` and `setClusterScoreThreshold(float)`) synchronously on save to ensure background services (`WorkoutClusterEngine`, `LegacyImportEngine`, `TrackerService`) read updated values immediately.
• Invoking "Auf Standardwerte zurücksetzen" (Reset to Defaults) in Advanced Settings SHALL atomically reset clustering tolerances and the score threshold to their canonical factory defaults.
4. *Dedicated Recalculate Action, Material 3 Confirmation Guard & Concurrency Isolation (ATT-3452)*:
• `ClusterTuningSection.kt` SHALL host a dedicated "Recalculate All Clusters" button (`R.string.cluster_tuning_recalculate_button`).
• Tapping the button opens a modal Material 3 confirmation `AlertDialog` (`containerColor = MaterialTheme.colorScheme.surface`, `tonalElevation = 0.dp`) displaying `R.string.cluster_tuning_warning` with high-contrast error confirmation action and cancel dismissal.
• Recalculation executes asynchronously via singleton `WorkoutClusterRepository.getInstance(context).recalculateClustersWithProgress()`, decoupled from UI Composable lifecycles.
• While active (`migrationStatus != null`), the recalculate button is disabled and displays `CircularProgressIndicator` alongside `R.string.cluster_tuning_recalculating`, and all tolerance sliders are disabled (`enabled = false`) to prevent concurrent modification.
• Process-death recovery is guaranteed by the self-healing bootstrapper in `WorkoutClusterRepository.refreshClusters()`.
5. *Cluster Assignment Score Threshold & Master Sensitivity Coupling (ATT-5559)*:
• `WorkoutClusterEngine.suggestCluster` SHALL evaluate candidate clusters against `scoreThreshold` (`it.second < scoreThreshold`), resolving the threshold dynamically via `runCatching { TrainingApplication.getClusterScoreThreshold().toDouble() }.getOrDefault(1.2)` to ensure static mock resilience in testing.
• `ClusterTuningContent.kt` SHALL incorporate `clusterScoreThreshold` into the derived master sensitivity slider calculation, mapping the threshold across its `0.5f..2.5f` range to normalize sensitivity.
• `ClusterTuningContent.kt` SHALL render a dedicated `TuningSliderItem` for `clusterScoreThreshold` with localized title `R.string.cluster_tuning_score_threshold_label`, step `0.1f`, formatted value `String.format(locale, "%.1f", scoreThreshold)`, and helper text `R.string.cluster_tuning_score_threshold_helper` ("Lower = stricter matching · Higher = more tolerant").
• `WorkoutClusterRepository.recalculateClustersAsync` SHALL accept and persist `scoreThreshold: Float? = null` alongside spatial tolerances prior to recalculation.
6. *Preservation of Invariants & Modularity*:
• All 7 parameters, SharedPreferences keys (`clusterTolEndpoints`, `clusterTolApex`, `clusterTolDistance`, `clusterTolAltitudePos`, `clusterUseSportType`, `clusterUseAltitudePos`, `cluster_score_threshold`), default values, and range validations MUST NOT be altered.
• Modularity invariant: Every source file in `ui.settings.tuning` MUST remain strictly < 400 lines of code (`AdvancedTuningModularityTest`).
• 100% 9-language translation parity maintained across EN, DE, ES, FR, IT, JA, NL, PL, PT.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Amends `REQ-SET-010` (*Tuning Parameters UI*), `REQ-SET-065` (*Altitude Extrema-Aware 3D Route Clustering & Database Migration*), `REQ-SET-075` (*Dedicated Route Clustering Section in Advanced Settings*), and `REQ-MIG-012` (*Pre-Import Sensitivity Tuning*), supersedes `REQ-SET-014` (*Auto-Dismiss Tuning UI*), integrating with `REQ-UI-357` (*Dedicated Full-Screen Advanced Settings Screen Architecture*).
2. *Historical Origin & Commit Trace*: Sprint 2026-40.2 (`REQ-SET-010`, `REQ-SET-014`), Sprint 2026-40.5 (`ATT-502` / `ATT-503`, `REQ-SET-065`), Sprint 2026-40.8 (`REQ-UI-222`), Sprint 2026-42.1 (`ATT-3344`, `REQ-UI-357`; `ATT-3355`, `REQ-SET-075`), Sprint 2026-42.2 (`ATT-3452`), Sprint 2026-45.8 (`ATT-5559`).
3. *Root Reason for Existing Formulation*: Route clustering originally hardcoded an internal candidate similarity cutoff of `1.0` in `WorkoutClusterEngine.kt`, established during initial route clustering implementation when routes were assumed to be near-identical traces. In practice, slight GPS jitter, elevation variations, or differing recording devices on identical real-world courses regularly produce composite similarity scores between 1.01 and 1.20, causing false-negative cluster rejections and redundant manual naming dialogs. ATT-5559 introduces an overarching configurable score threshold (default 1.2f, range 0.5f..2.5f) to accommodate real-world GPS variance without altering individual component tolerance geometries.
4. *Preservation of Core Invariants*: Parameter keys, storage semantics in `TrainingApplication.java`, value ranges, spatial normalization bounds, sport type firewall (`REQ-SET-064`), 3D altitude extrema logic (`REQ-SET-065`), modularity < 400 lines, and 9-language translation parity remain 100% strictly intact.

**Acceptance Criteria (Given-When-Then)**:
• *Given* the athlete browsing Advanced Settings ("Erweiterte Einstellungen") or Pre-Import Tuning,
• *When* viewing the route clustering parameters,
• *Then* a dedicated slider for "Cluster-Zuordnung Schwellenwert" (`cluster_tuning_score_threshold_label`) is rendered with range `0.5` to `2.5`, step `0.1`, default `1.2`, and directional helper text ("Lower = stricter matching · Higher = more tolerant").
• *Given* an athlete adjusting the cluster score threshold,
• *When* saving or completing pre-import tuning,
• *Then* `clusterScoreThreshold` is persisted to DataStore and SharedPreferences (`cluster_score_threshold`) and immediately utilized during workout clustering and import matching.
• *Given* an imported or completed workout with composite cluster similarity score of `1.15`,
• *When* evaluated by `WorkoutClusterEngine.suggestCluster` with default threshold `1.2`,
• *Then* the workout SHALL be matched to the existing cluster candidate instead of being rejected as unclustered.
• *Given* an active cluster recalculation in progress,
• *Then* recalculation runs in the repository coroutine scope decoupled from UI navigation, the recalculate button and all sliders (including score threshold) are disabled with progress feedback.
• *Given* an athlete invoking "Auf Standardwerte zurücksetzen" (Reset to Defaults),
• *Then* `clusterScoreThreshold` is reset to its canonical default of `1.2f`.

**Invariants**: Parameter ranges, DataStore keys, warning card, factory reset, zero tonal elevation, 400-line file limit, and 9-language localization parity MUST NOT be broken.

### REQ-SET-076: Red Cluster Recalculate Button and Direct Advanced Settings Deep-Link from Favorite Routes [Verified]
- **Tests**: `TST-SET-076`
- **Priority**: High
- **Type**: Functional
- **Verification Method**: Unit, Integration
- **Implementation**: `ClusterTuningSection.kt`, `ClusterInfoDialog.kt`, `WorkoutClustersTabsScreen.kt`, `WorkoutClustersScreen.kt`, `ATrainingTrackerApp.kt`, `WorkoutClustersFragment.kt`
- **Rationale**: The system SHALL style the cluster recalculate action button in red to communicate its destructive nature with accessible progress contrast, and SHALL provide an educational description and direct navigation deep-link from the Favorite Routes info dialog to Advanced Settings (ATT-3492, amending `REQ-SET-075` and `REQ-UI-149`):
1. *Material 3 Error Styling & Accessibility for Recalculate Button (`ClusterTuningSection.kt`)*:
• The "Recalculate All Clusters" button (`R.string.cluster_tuning_recalculate_button`) in Advanced Settings Section 6 (`ROUTE_CLUSTERING`) SHALL use Material 3 error button colors (`colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError, disabledContainerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.6f), disabledContentColor = MaterialTheme.colorScheme.onError)`).
• When recalculation is active (`isRecalculating == true`), the spinning `CircularProgressIndicator` SHALL be styled with `color = MaterialTheme.colorScheme.onError` to satisfy WCAG 2.1 contrast against the error container.
2. *Educational Description & Direct Navigation Deep-Link (`ClusterInfoDialog.kt`)*:
• Section 4 of `ClusterInfoDialog` ("Sensitivity & Parameter Tuning" / `cluster_info_tuning_title`) SHALL update its description (`cluster_info_tuning_desc`) to state that cluster matching tolerances and full cluster recalculation are configured in Advanced Settings (*Erweiterte Einstellungen*).
• Section 4 SHALL render a direct action button (`R.string.cluster_info_open_advanced_tuning`) with leading tune icon (`Icons.Default.Tune`).
• Clicking the button SHALL execute a strictly ordered state transition: invoke `onDismissRequest()` strictly before `onNavigateToAdvancedSettings()` to prevent UI backstack flicker or state race conditions.
3. *Pure Navigation Delegation Architecture (Single Source of Truth)*:
• `ClusterInfoDialog` SHALL require `onNavigateToAdvancedSettings: () -> Unit` parameter, forbidding Activity casts inside UI composables.
• `WorkoutClustersTabsScreen.kt` and `WorkoutClustersScreen.kt` SHALL declare `onNavigateToAdvancedSettings: () -> Unit` and forward it cleanly to `ClusterInfoDialog`.
• Hosting containers resolve navigation to `NavRoutes.ADVANCED_SETTINGS`.
4. *100% 9-Language Localization Parity*:
• `cluster_info_open_advanced_tuning` and updated `cluster_info_tuning_desc` SHALL maintain 100% translation parity across EN, DE, ES, FR, IT, JA, NL, PL, PT.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Amends `REQ-SET-075` (*Dedicated Workout Route Clustering Section in Advanced Settings*) and `REQ-UI-149` (*Educational Cluster Information Dialog*).
2. *Historical Origin & Commit Trace*: Sprint 2026-40.5 (`ATT-501`, `REQ-UI-149`), Sprint 2026-42.1 (`ATT-3355`), Sprint 2026-42.2 (`ATT-3452`, `REQ-SET-075`).
3. *Root Reason for Existing Formulation*: Primary button styling in `ClusterTuningSection.kt` did not visually convey the destructive nature of wiping clusters prior to tapping. In `ClusterInfoDialog.kt`, Section 4 text referenced controls "below" because it was authored when sliders resided in the same screen.
4. *Preservation of Core Invariants*: Mandatory confirmation dialog, asynchronous execution in `WorkoutClusterRepository`, `migrationStatus` concurrency locking, drawer navigation mappings, and file modularity (< 400 lines) are strictly preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an athlete viewing `Advanced Settings` -> `Route & Workout Clustering`,
• *When* inspecting the "Recalculate All Clusters" button,
• *Then* the button container color SHALL be `MaterialTheme.colorScheme.error` and content color SHALL be `MaterialTheme.colorScheme.onError`.
• *Given* an active recalculation in progress,
• *Then* the button is disabled with `disabledContainerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.6f)` and progress indicator color is `onError`.
• *Given* an athlete opening the Favorite Routes info dialog (`ClusterInfoDialog`),
• *When* viewing Section 4,
• *Then* the text SHALL explain that cluster tolerances are tuned in Advanced Settings, and a button referencing `R.string.cluster_info_open_advanced_tuning` SHALL be displayed.
• *When* the athlete taps the deep-link button,
• *Then* `onDismissRequest()` SHALL execute before `onNavigateToAdvancedSettings()`, navigating cleanly to `NavRoutes.ADVANCED_SETTINGS`.

**Invariants**: Mandatory confirmation AlertDialog, repository async recalculation, migration status locking, and 9-language translation parity MUST NOT be broken.

### REQ-SET-041: Unified Spatial Markers [Verified]
- **Tests**: `TST-SET-031`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit, Integration
- **Implementation**: `WorkoutClusterHeatmapScreen.kt`, `InteractivePeriodMap.kt`, `TrackOnMapScreen.kt`, `RouteItem.kt`
- **Rationale**: All geographical views (Workout Clusters, Periods, Workout Details, and Routes) SHALL use standardized technical markers: Green start icons, Red stop icons, and Blue apex/distance icons. Icons SHALL use the unified `createSensorMarker` visual style.

### REQ-PRI-001: Permission Transparency [Implemented]
- **Tests**: `TST-PRI-002`
- **Priority**: Critical
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Implementation**: `MainActivityWithNavigation.java`
- **Rationale**: The system SHALL provide transparent information about location and background permissions to comply with Android standards and build user trust.

### REQ-PRI-002: Accessible Privacy Policy [Implemented]
- **Tests**: `TST-MAN-001`
- **Priority**: Critical
- **Type**: Non-Functional
- **Verification Method**: Review
- **Implementation**: `privacy.md`
- **Rationale**: The system SHALL provide an accessible Privacy Policy linked to the source to inform users about data handling practices.

### REQ-PRI-003: Contextual Just-in-Time Permission Flow, Zero-Friction Cold Start, Background Location Escalation, Battery Optimization Exemption & Graceful Settings Return Handling [Verified]
- **Tests**: `TST-PRI-002`
- **Priority**: Critical
- **Type**: Functional
- **Verification Method**: Unit, Integration
- **Implementation**: `MainActivityWithNavigation.kt`, `ControlTrackingScreen.kt`, `ControlTrackingButton.kt`, `ControlTrackingViewModel.kt`, `PermissionRationaleSheet.kt`
- **Rationale**: The system SHALL NOT present modal permission dialogs or battery optimization popups during cold start; it SHALL evaluate permissions contextually when the athlete expresses tracking intent (tapping Start or searching sensors), display educational Material 3 rationale sheets highlighting athletic benefits, enforce the Android 11+ two-step background location cascade ('Allow all the time' / 'Immer zulassen'), handle battery optimization exemption ('Unrestricted' / 'Nicht eingeschränkt') with fail-safe OEM fallback, and handle denied permissions or returns from settings gracefully with an amber warning badge on the Start button and actionable feedback without infinite loops (ATT-2075).

### REQ-PRI-004: Mandatory Precise Location Gating and Dynamic Sensor Device Instantiation on Permission Escalation [Verified]
- **Tests**: `TST-PRI-003`
- **Priority**: Critical
- **Type**: Functional
- **Verification Method**: Unit, Integration
4. *Preservation of Core Invariants*: JIT permission sheet flow, battery optimization exemption handling, background location cascade, and 100% test pass rate are strictly preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an athlete on Android 12+ who granted only "Approximate Location" (`ACCESS_COARSE_LOCATION` granted, `ACCESS_FINE_LOCATION` denied),
• *When* viewing `ControlTrackingScreen`,
• *Then* the Start button SHALL display the amber permission warning badge, and tapping Start SHALL trigger the rationale sheet explaining that Precise Location is required for workout tracking.
• *Given* an athlete who grants "Precise Location" in the permission prompt or system settings,
• *When* returning to the app or completing the prompt,
• *Then* `hasLocationPermission` SHALL evaluate to `true`, the amber warning badge SHALL be cleared, and `checkOrInitializeLocationDevices()` SHALL instantiate `SpeedAndLocationDevice_GPS` without requiring an app restart.
• *Given* `BANALService` running with `mSpeedAndLocationDevice_GPS == null` due to initial permission denial,
• *When* `checkOrInitializeLocationDevices()` is invoked after permission grant,
• *Then* `mSpeedAndLocationDevice_GPS` SHALL be instantiated and added to active devices.

**Invariants**: JIT permission sheet flow, battery optimization exemption handling, background location cascade, and 100% test pass rate MUST NOT be broken.
- **Rationale**: The system SHALL strictly enforce Precise Location (`ACCESS_FINE_LOCATION`) for workout tracking and dynamically recheck permission state to lazily instantiate GPS location devices (ATT-2357):
1. *Mandatory Precise Location Gating in Tracking UI (`ControlTrackingScreen.kt`)*: `checkHasLocation` SHALL return `true` IF AND ONLY IF `ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED`. If only `ACCESS_COARSE_LOCATION` is granted, `checkHasLocation` SHALL evaluate to `false`, the Start button SHALL display the amber permission warning badge, and tapping Start SHALL trigger the permission rationale flow explaining that Precise Location is required.
2. *Precise Location Educational Rationale (`PermissionRationaleSheet.kt`)*: When location permission is missing or downgraded to Approximate, the rationale sheet SHALL display an explicit explanation (`permission_rationale_precise_location_explanation`) clarifying that Approximate location cannot record speed, distance, route trackpoints, or elevation.
3. *Dynamic Location Device Instantiation (`DeviceManager.java`)*: `DeviceManager` SHALL expose a `public synchronized void checkOrInitializeLocationDevices()` method: if `mSpeedAndLocationDevice_GPS == null`, `isPaired(gpsDeviceId) == true`, `TrainingApplication.havePermission(Manifest.permission.ACCESS_FINE_LOCATION) == true`, and GPS provider is safely available, the system SHALL instantiate `mSpeedAndLocationDevice_GPS = new SpeedAndLocationDevice_GPS(mContext, mSensorManager)`; if `mSpeedAndLocationDevice_GoogleFused == null`, `isPaired(fusedDeviceId) == true`, `TrainingApplication.havePermission(Manifest.permission.ACCESS_FINE_LOCATION) == true`, and Google Play Services is available, the system SHALL instantiate `mSpeedAndLocationDevice_GoogleFused = new SpeedAndLocationDevice_GoogleFused(mContext, mSensorManager)`; if devices are already instantiated, they SHALL NOT be re-created or duplicate-registered.
4. *Service Delegation (`BANALService.java`)*: `BANALService` SHALL expose `public void checkOrInitializeLocationDevices()` delegating to `cDeviceManager.checkOrInitializeLocationDevices()` when `cDeviceManager != null`.
5. *Dynamic Lifecycle Re-evaluation (`MainActivityWithNavigation.kt` & `ControlTrackingScreen.kt`)*: In `ControlTrackingScreen.kt`, when `fineGranted == true` in `permissionLauncher`, the system SHALL immediately invoke `BANALService.getDeviceManager()?.checkOrInitializeLocationDevices()`; in `MainActivityWithNavigation.kt`, in `onResume()` and in `onRequestPermissionsResult` when `ACCESS_FINE_LOCATION` is granted, the system SHALL invoke `BANALService.getDeviceManager()?.checkOrInitializeLocationDevices()`.
6. *100% 9-Language Localization Parity*: All newly introduced or modified string resources SHALL be localized with 100% parity across all 9 application locales (EN, DE, ES, FR, IT, JA, NL, PL, PT).

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Refines `REQ-PRI-003` (*Contextual Just-in-Time Permission Flow*) and `REQ-SEN-001` (*GPS and Sensor Device Lifecycle*).
2. *Historical Origin & Commit Trace*: Sprint 2026-40.12 (`ATT-2075` introduced JIT Material 3 permission rationale). Android 12 introduced coarse vs. fine permission separation.
3. *Root Reason for Existing Formulation*: `checkHasLocation` used `

### REQ-LOC-001: Global Translation Parity, Resource Hierarchy Alignment, and Formatter Safety [Verified]
- **Tests**: `TST-STR-018`, `TST-STR-019`, `TST-UI-385`, `TST-UI-394`, `TST-UI-395`
- **Priority**: Medium
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Implementation**: `values*/strings.xml`, `values*/strings_devices.xml`, `values*/strings_filters.xml`, `values*/arrays.xml`
- **Rationale**: The system SHALL enforce 100% translation coverage, consistent resource file distribution, array length equality, and strict format specifier parity across all 9 supported locales (EN, DE, ES, FR, IT, JA, NL, PL, PT):
1. *Format Specifier Parity & Safety*: All localized format strings MUST strictly conform to `REQ-UI-122` positional placeholder syntax (`%1$s`). Translated format strings SHALL contain the identical count, types, and indices of format specifiers as the default English resources, preventing parameter drops or `UnknownFormatConversionException` crashes.
2. *Resource Hierarchy Alignment*: Misplaced string resources SHALL be normalized across locales: `filter_max` SHALL reside exclusively in `strings_filters.xml` across all 9 locales, and `devices_sensors_title` SHALL reside in `strings_devices.xml` across all locales.
3. *String-Array Parity & Non-Translatable Safety*: All localized string arrays SHALL maintain exact item count parity with default `values/arrays.xml`. Technical value arrays (such as `backup_interval_values`) SHALL be declared with `translatable="false"`. Obsolete malformed tags SHALL be removed.
**Acceptance Criteria**:
• *Given* any supported locale,
• *When* the user interacts with routes, clusters, export notifications, filters, or sensors,
• *Then* all strings SHALL format safely without runtime crashes or missing parameters.
• *Given* automated translation parity tests,
• *When* executed,
• *Then* 100% of translatable string keys, format specifiers, and array lengths SHALL pass verification.
**Invariants**: No string keys or existing translations SHALL be lost; Android resource resolution fallbacks MUST remain intact.

### REQ-PRI-005: Proactive Background Energy & Battery Optimization Exemption Governance and Cascaded Rationale Decoupling [Verified]
- **Tests**: `TST-PRI-004`
- **Priority**: Critical
- **Type**: Functional
- **Verification Method**: Unit, Integration
4. *Decoupled Just-in-Time Rationale Cascade*:
• In `proceedAfterPermissions`, when background location is skipped ("Not now") or denied, the system SHALL NOT bypass battery optimization; if `checkIsIgnoringBatteryOptimizations() == false`, it SHALL present `RationaleStep.BATTERY_OPTIMIZATION`.
• When the athlete taps Continue on `RationaleStep.BATTERY_OPTIMIZATION`, the system SHALL launch `launchBatteryOptimizationIntent(context)` and set `pendingStartAfterBatteryExemption = true`. If exemption is confirmed upon `ON_RESUME`, `onStart()` SHALL be automatically triggered.
• If the athlete taps "Not now", `rationaleStep` SHALL reset to `NONE` and tracking SHALL start, preventing infinite rationale loops.
5. *Google Play Policy Invariant*:
• `launchBatteryOptimizationIntent` SHALL ONLY be invoked upon explicit athlete interaction (tapping the banner or tapping Continue in the rationale sheet). It SHALL NEVER be automatically triggered from `ON_RESUME` or application launch.
6. *100% 9-Language Localization Parity*:
• All banner string resources (`battery_optimization_warning_banner_title`, `battery_optimization_warning_banner_desc`, `battery_optimization_action_fix`) SHALL maintain 100% parity across EN, DE, ES, FR, IT, JA, NL, PL, PT.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Refines `REQ-PRI-003` (*Contextual Just-in-Time Permission Flow*) and `REQ-PRI-004` (*Precise Location Gating*), addressing Sprint 2026-41.1 human observation on Pixel 10 (`ATT-2357`).
2. *Historical Origin & Commit Trace*: Sprint 2026-40.12 commit `b28b74a2` (`ATT-2075`) introduced JIT permission sheets.
3. *Root Reason for Existing Formulation*: Zero-friction cold start deferred battery optimization check exclusively to the Start button, chaining it after background location. This caused it to be shadowed by background location denials and left the athlete unaware of revoked battery exemption while idle.
4. *Preservation of Core Invariants*: Zero modal popups on startup, 3-tier fallback intent cascading, precise location gating (`REQ-PRI-004`), and loop prevention remain 100% preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an athlete on `ControlTrackingScreen` with battery optimization enabled ("Optimized" or "Restricted"),
• *When* the screen renders or resumes,
• *Then* `BatteryOptimizationWarningBanner` SHALL be displayed, and `ControlTrackingButton` SHALL show the warning badge.
• *Given* an athlete tapping the action on `BatteryOptimizationWarningBanner`,
• *When* tapped,
• *Then* `launchBatteryOptimizationIntent` SHALL launch the system battery optimization dialog/settings.
• *Given* an athlete granting battery optimization exemption in system settings and returning to the app,
• *When* `ON_RESUME` executes,
• *Then* `BatteryOptimizationWarningBanner` and the Start button warning badge SHALL automatically disappear.
• *Given* an athlete tapping Start when background location is denied but battery optimization is missing,
• *When* dismissing background location rationale,
• *Then* `RationaleStep.BATTERY_OPTIMIZATION` SHALL be presented.

**Invariants**: Google Play policy compliance, zero startup popups, infinite loop prevention, and 100% test pass rate MUST NOT be broken.
- **Rationale**: The system SHALL actively monitor battery optimization exemption (`PowerManager.isIgnoringBatteryOptimizations`), render a prominent non-blocking proactive warning banner and Start button warning badge when background energy consumption is restricted, decouple the Just-in-Time rationale cascade so battery optimization is never shadowed by background location denials, and strictly adhere to Google Play policy by launching system settings ONLY upon direct athlete interaction (ATT-2621):
1. *Reactive Battery Optimization State & Lifecycle Synchronization (`ControlTrackingScreen.kt`)*:
• `ControlTrackingScreen` SHALL maintain a reactive Compose state `isIgnoringBatteryOptimizations` reflecting `PowerManager.isIgnoringBatteryOptimizations(context.packageName)`.
• On `Lifecycle.Event.ON_RESUME`, `isIgnoringBatteryOptimizations` SHALL be re-evaluated to detect exemption grants or revocations performed in Android system settings in real time.
2. *Proactive Warning Banner (`BatteryOptimizationWarningBanner`)*:
• When `isIgnoringBatteryOptimizations == false`, `ControlTrackingScreen` SHALL render a non-blocking `BatteryOptimizationWarningBanner` below `LocationCalibrationBadge` and above the central Information Area.
• The banner SHALL display an alert icon (`ic_battery_full`), localized headline (`battery_optimization_warning_banner_title`), informative description (`battery_optimization_warning_banner_desc`), and an actionable trigger ("Aktivieren" / "Einstellungen") that directly dispatches `launchBatteryOptimizationIntent(context)`.
• The banner SHALL provide a session-level dismissal option ("X"), hiding the banner for the active screen session via `rememberSaveable { mutableStateOf(false) }`.
3. *Unified Start Button Warning Indicator*:
• The warning badge on `ControlTrackingButton` SHALL evaluate to `true` whenever Precise Location is missing OR battery optimization exemption is missing: `hasPermissionWarning = !hasLocationPermission

### REQ-SET-077: Standardized Route Clustering Form Controls via TuningFormControls (Rule 36) [Verified]
- **Tests**: `TST-SET-077`
- **Priority**: High
- **Type**: Functional
- **Verification Method**: Unit, Integration
- **Implementation**: `TuningFormControls.kt`, `ClusterTuningContent.kt`, `ClusterTuningScreen.kt`, `ClusterTuningSection.kt`
- **Rationale**: The system SHALL standardize all route clustering form controls across `ClusterTuningContent.kt`, `ClusterTuningScreen.kt`, and `ClusterTuningSection.kt` using canonical, accessible form controls from `TuningFormControls.kt` (`TuningSliderItem` and `TuningToggleItem`), enforcing Rule 36 (Single Source of Truth & DRY) and guaranteeing visual and behavioral consistency across all application settings screens (ATT-3365, complementing `REQ-SET-075` and `REQ-UI-262`):
1. *Defensive Parameterization of `TuningFormControls.kt`*:
• `TuningSliderItem` SHALL accept optional parameters `enabled: Boolean = true` and `onValueChangeFinished: (() -> Unit)? = null`, forwarding them to the internal Material 3 `Slider` component and applying standard disabled alpha (`TTAlpha.Disabled`) to header text and value labels when disabled.
• When `enabled = false`, Material 3 `Slider` and `Switch` components SHALL natively enter their disabled visual and interaction state, strictly preventing user drag/tap interactions and ensuring neither `onValueChange` nor `onValueChangeFinished` callbacks are fired while disabled.
• `TuningToggleItem` SHALL accept optional parameter `enabled: Boolean = true`, forwarding it to the internal `Switch` component and applying standard disabled alpha (`TTAlpha.Disabled`) to label text when disabled.
• All existing 15+ call sites across `NavigationSection`, `SensorsGpsFilterSection`, `CockpitTypographySection`, `AmoledBatterySaverSection`, `SensorSearchTuningSection`, and `AftermathAnalysisSection` SHALL retain 100% backward and binary compatibility without signature regressions or source modifications.
2. *Standardization of `ClusterTuningContent.kt`*:
• The system SHALL replace hand-crafted `Row` + `Switch` blocks for `showDetails`, `useSportTypeForClustering`, and `useAltitudePosForClustering` with canonical `TuningToggleItem` composables.
• The system SHALL replace bespoke tolerance slider blocks (`endpointTolerance`, `apexTolerance`, `distanceTolerance`, `altitudePositionTolerance`, and `clusterScoreThreshold`, ATT-5559) with direct invocations of `TuningSliderItem`.
• The legacy `TuningSlider` composable in `ClusterTuningContent.kt` SHALL be marked with `@Deprecated` containing a fully-qualified `ReplaceWith(expression = "TuningSliderItem(title = title, valueText = valueText, helperText = \"\", defaultText = \"\", value = value, onValueChange = onValueChange, valueRange = valueRange, steps = steps, enabled = enabled, onValueChangeFinished = onValueChangeFinished)", imports = ["com.atrainingtracker.trainingtracker.ui.settings.tuning.TuningSliderItem"])`.
3. *Multi-Consumer & State Propagation Support*:
• All consumers of `ClusterTuningContent` (`ClusterTuningScreen.kt`, `ClusterTuningSection.kt`, and `ImportBackupTabsScreen.kt`) SHALL inherit unified visual rhythm, typography, and switch scaling (`scale(0.8f)`).
• The `enabled` state (e.g. `!isRecalculating`) SHALL cleanly propagate down to all toggles and sliders. The altitude position tolerance slider SHALL be gated to `enabled = enabled && useAltitudePosForClustering`.
4. *Preservation of System Invariants*:
• Tolerance bounds (`10..500f`, `0.01..0.2f`), cluster score threshold bounds (`0.5f..2.5f`, ATT-5559), master sensitivity calculation, recalculation domain locking (`isRecalculatingLock`), persistence on drag completion (`onValueChangeFinished`), and 100% full-suite unit test pass rate MUST be strictly preserved.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement (`REQ-SET-077`). Complements and standardizes implementation of `REQ-SET-075` (*Centralized Route Clustering Tuning Controls via ClusterTuningContent*) and `REQ-UI-262` (*Standardized Advanced Tuning Form Controls & Parameter Presentation*). Targets `TuningFormControls.kt`, `ClusterTuningContent.kt`, `ClusterTuningScreen.kt`, and `ClusterTuningSection.kt`.
2. *Historical Origin & Commit Trace*: `REQ-UI-262` was introduced in Sprint 2026-40.12 (`ATT-262`). `REQ-SET-075` was introduced in Sprint 2026-41.9 (`ATT-3452`).
3. *Root Reason for Existing Formulation*: When clustering parameters were centralized into `ClusterTuningContent.kt` in ATT-3452, the developer migrated the existing custom `Row`/`Switch` and custom `TuningSlider` verbatim to avoid modifying `TuningFormControls.kt`.
4. *Preservation of Core Invariants*: Tolerance ranges (`10..500f`, `0.01..0.2f`), master sensitivity calculation, recalculation domain locking (`isRecalculatingLock`), persistence on drag completion (`onValueChangeFinished`), and 100% test pass rate strictly preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* `ClusterTuningContent` rendering clustering parameters,
• *When* displaying `showDetails`, `useSportTypeForClustering`, and `useAltitudePosForClustering`,
• *Then* they render using `TuningToggleItem` with `scale(0.8f)` and standard text hierarchy, applying `TTAlpha.Disabled` to labels and disabled palette to `Switch` when `enabled = false`.
• *Given* `ClusterTuningContent` rendering tolerance parameters,
• *When* adjusting endpoint, apex, distance, and altitude position tolerances,
• *Then* they render using `TuningSliderItem` with title, active formatted value, and slider bar, applying `TTAlpha.Disabled` to header text and inhibiting `onValueChange` and `onValueChangeFinished` when `enabled = false`.
• *Given* an active cluster recalculation in progress (`isRecalculating == true`),
• *When* viewing `ClusterTuningSection`,
• *Then* all toggles and sliders receive `enabled = false` and cannot be interacted with.
• *Given* `ClusterTuningContent` with `useAltitudePosForClustering == false`,
• *When* evaluating the altitude position tolerance slider,
• *Then* it receives `enabled = false` even if the parent container is enabled (`enabled = enabled && useAltitudePosForClustering`).
• *Given* `ClusterTuningContractTest` and `AdvancedTuningVisualContractTest`,
• *When* executed on pure JVM,
• *Then* 100% of tests pass, verifying architectural conformity, binary backward compatibility, and zero regressions on existing tuning sections.

**Invariants**: Tolerance ranges, master sensitivity calculation, recalculation domain locking, drag completion persistence, and 100% full-suite test pass rate MUST NOT be broken.

### REQ-SET-078: Historical Duration and Speed/Pace Trend Chart for Workout Clusters (Lieblingsstrecken) [Verified]
- **Tests**: `TST-SET-078`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit, Integration
- **Implementation**: `WorkoutClusterTrendChartCard.kt`, `WorkoutClusterTrendMath.kt`, `WorkoutClusterHeatmapScreen.kt`, `values*/strings.xml`
- **Rationale**: The system SHALL render a historical effort trend chart for Workout Clusters in `WorkoutClusterDetailDashboard` (`WorkoutClusterHeatmapScreen.kt`, `WorkoutClusterTrendChartCard.kt`, `WorkoutClusterTrendMath.kt`), ordering member workouts chronologically, supporting dual metric modes (Duration vs Speed/Pace) with faster-is-higher vertical axis scaling, displaying cluster average reference line and PR highlight, providing interactive point touch selection with workout peek sheet launch, rendering explicit X/Y axis scale markers, clamping isolated outliers with Tukey fencing, and guaranteeing 9-language localization parity (ATT-4067, refined by ATT-4287):
1. *Chronological Effort Progression & Data Cleansing (`REQ-SET-078.1`)*:
• The system SHALL sort member workouts in ascending chronological order based on `startTimeS`.
• Workouts with non-positive duration (`activeTimeSec <= 0`) or velocity (`avgSpeedMps <= 0.0`) SHALL be excluded from the trend plot.
• When the count of valid member workouts is strictly less than 2 ($N < 2$), the progression chart card SHALL be hidden completely and the analytics pane SHALL display Tab 1 directly without a trend tab option.
• When $N \ge 2$, the progression chart card SHALL be displayed within Tab 2 (*Leistungsverlauf*) of `MapDetailLayout.analyticsContent`.
2. *Sport-Aware Dual Metric Modes & Inverted Scaling (`REQ-SET-078.2`)*:
• The chart SHALL support toggling between Duration and Speed/Pace modes.
• In Duration mode, `activeTimeSec` SHALL be plotted with inverted vertical scaling such that faster (lower) durations are rendered higher on the canvas, preserving the "higher is better" invariant.
• In Speed/Pace mode, if the cluster sport is Running (`BSportType.RUN`), pace SHALL be plotted with inverted vertical scaling (faster/lower pace higher); if Cycling (`BSportType.BIKE`) or other, speed SHALL be plotted with standard vertical scaling (higher speed higher).
• Unit conversions and formatting SHALL delegate strictly to `TimeFormatter`, `SpeedFormatter`, and `PaceFormatter`.
3. *Cluster Average Reference Line & Personal Best Highlight (`REQ-SET-078.3`)*:
• The chart SHALL draw a horizontal dashed reference line across the plot representing the cluster average value (duration, speed, or pace).
• The effort with the fastest time (minimum `activeTimeSec`) SHALL be decorated with a distinctive gold marker and PR badge.
4. *Direct Touch Selection & Peek Launch (`REQ-SET-078.4`)*:
• Tapping a data point within 24dp hit radius SHALL select that effort, drawing an active indicator ring and directly dispatching `onSelectWorkout(hit.workoutId)` to launch the workout peek sheet.
• The redundant selected-workout callout banner (`Surface` with date, duration, speed/pace, and arrow icon at the bottom of `WorkoutClusterTrendChartCard`) SHALL be removed, freeing ~50 dp of vertical space.
5. *9-Language Localization Parity (`REQ-SET-078.5`)*:
• All new UI strings (`cluster_trend_title`, `cluster_trend_duration`, `cluster_trend_speed`, `cluster_trend_pace`, `cluster_trend_pr`, `cluster_trend_avg`, `cluster_trend_outlier`) SHALL exist across all 9 application locales (`values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`).
6. *Outlier-Resilient Y-Axis Scaling & Tukey Fencing (`REQ-SET-078.6`)*:
• When valid workouts count $N \ge 4$, the vertical scale domain SHALL be calculated using statistical outlier fencing based on the Interquartile Range ($\text{IQR} = Q_3 - Q_1$).
• The effective scale domain $[\text{scaleMin}, \text{scaleMax}]$ SHALL clamp against $[Q_1 - 1.5 \times \text{IQR}, Q_3 + 1.5 \times \text{IQR}]$ when $\text{IQR} > 0$.
• Any effort falling outside $[\text{scaleMin}, \text{scaleMax}]$ SHALL be clamped to the canvas bounds ($\text{normY} = 0f$ or $1f$) and marked as an outlier (`isClampedOutlier = true`).
• When $N < 4$ or $\text{IQR} == 0$, the chart SHALL fall back safely to raw $\min..\max$ scaling.
7. *Explicit Y-Axis & X-Axis Scale Labels (`REQ-SET-078.7`)*:
• The progression chart SHALL render explicit scale markers: (a) Y-Axis Top Label displaying formatted value at $\text{normY} = 1.0f$ (fastest duration / fastest pace / highest speed); (b) Y-Axis Bottom Label displaying formatted value at $\text{normY} = 0.0f$ (slowest duration / slowest pace / lowest speed); (c) Y-Axis Average Label displaying formatted average with `Ø` / `cluster_trend_avg`; (d) X-Axis Chronological Boundary Labels displaying formatted short dates of earliest and latest workouts along the bottom axis baseline.
8. *Density-Adaptive Point Sizing & Outlier Representation (`REQ-SET-078.8`)*:
• When $N > 20$, standard non-PR point radius SHALL dynamically reduce to compact size ($2\text{ dp}$ outer / $1\text{ dp}$ inner) to prevent visual collision.
• PR efforts SHALL retain prominent gold highlighting ($6\text{ dp}$).
• Selected effort SHALL retain prominent highlight ring ($5\text{ dp}$ inner / $9\text{ dp}$ halo).
• Clamped outlier efforts SHALL be visually differentiated at the canvas boundary with an outlier glyph / ring styling.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Refines `REQ-SET-078` (*Historical Duration and Speed/Pace Trend Chart for Workout Clusters*). Targets `WorkoutClusterTrendChartCard.kt`, `WorkoutClusterTrendMath.kt`, `WorkoutClusterHeatmapScreen.kt`, and `values*/strings.xml`.
2. *Historical Origin & Commit Trace*: Sprint `2026-42.10` under ticket `ATT-4067` (commit `feat(ATT-4067): Render historical duration and speed/pace trend chart for Workout Clusters (Lieblingsstrecken)`). Codified in `docs/design_guidelines.md` Section 2.6 (Rule 48).
3. *Root Reason for Existing Formulation*: `REQ-SET-078` established the basic progression trend card with dual metric modes and "faster-is-higher" coordinate inversion using naive linear scaling across raw min..max extrema without text scale markers. When an anomalous workout occurs (e.g. 115 min vs 35 min normal), naive linear min..max scaling compresses 90%+ of sessions into an unreadable 10% band at the top of the canvas, while the lack of scale labels prevents athletes from reading absolute performance levels or chronological timeline span.
4. *Preservation of Core Invariants*: The athletic "faster-is-higher" coordinate orientation (higher placement = superior athletic performance) remains strictly preserved. The minimum workout threshold ($N \ge 2$) for displaying the card remains strictly preserved. The Personal Best gold badge, dashed average line, and interactive tap-to-peek launch remain 100% preserved. Division-by-zero resilience is preserved. All changes comply with `docs/design_guidelines.md` Section 2.6 (Rule 48) and maintain 100% clean-room test pass rate.

**Acceptance Criteria (Given-When-Then)**:
• *Given* a Workout Cluster with $N \le 1$ matched workouts,
• *When* opening `WorkoutClusterHeatmapScreen.kt`,
• *Then* the trend chart card is hidden completely.
• *Given* a Workout Cluster with $N \ge 2$ matched workouts,
• *When* opening `WorkoutClusterHeatmapScreen.kt`,
• *Then* the progression chart is rendered with points sorted chronologically.
• *Given* Duration mode or Running Pace mode,
• *When* inspecting the chart canvas,
• *Then* faster efforts are rendered vertically higher than slower efforts.
• *Given* the chart canvas,
• *When* rendered,
• *Then* a horizontal dashed average line is displayed, explicit Y-axis top, bottom, and average labels are rendered, and X-axis start and end date labels are rendered along the baseline.
• *Given* a cluster with an extreme outlier workout ($N \ge 4$),
• *When* the chart is rendered,
• *Then* the Y-axis scale is clamped using Tukey's IQR fences, normal workouts span the vertical canvas height, and the outlier is pinned to the boundary with an outlier indicator.
• *Given* a cluster with $> 20$ workouts,
• *When* inspecting data points,
• *Then* point radii adaptively scale down to 2dp to eliminate crowding.
• *Given* a rendered chart on `WorkoutClusterHeatmapScreen`,
• *When* the user taps an effort point on the canvas within the hit radius,
• *Then* `selectWorkoutForPeek(hit.workoutId)` is dispatched immediately to open the workout peek sheet, and zero redundant callout banner is rendered below the chart.

**Invariants**: Cluster centroid arithmetic mean, route matching, athletic "faster-is-higher" orientation, 9-language localization parity, and 100% clean-room test pass rate.

### REQ-PRI-006: Decoupled Multi-Permission Cascade, Contextual Bluetooth & Notification Gating, Sensor Discovery Permission Handling, and Legacy Code Retirement [Verified]
- **Tests**: `TST-PRI-005`
- **Priority**: Critical
- **Type**: Functional
- **Verification Method**: Unit, Integration
- **Implementation**: `ControlTrackingScreen.kt`, `ControlTrackingButton.kt`, `DevicesTabbedScreen.kt`, `MainActivityWithNavigation.kt`, `BTSearchForNewDevicesEngine.java`, `strings.xml` (all 9 locales)
- **Rationale**: The system SHALL decouple runtime permission evaluation so that fine location (`ACCESS_FINE_LOCATION`), notification (`POST_NOTIFICATIONS` on API 33+), and Bluetooth connect (`BLUETOOTH_CONNECT` on API 31+) are checked independently via dedicated predicates (`checkHasLocation()`, `checkHasNotification()`, `checkHasBluetoothConnect()`), rather than gating the entire rationale flow strictly on `!checkHasLocation()`, SHALL contextually prompt for missing notification or Bluetooth permissions when location is already granted, SHALL gate sensor search and scanning on Bluetooth permissions just-in-time, SHALL retire dead legacy permission methods from `MainActivityWithNavigation.kt`, and SHALL maintain 100% 9-language localization parity (ATT-3998, refined by ATT-4449, refining `REQ-PRI-003`):
1. *Decoupled Permission Check Predicates (`ControlTrackingScreen.kt`, `REQ-PRI-006.1`)*:
• `ControlTrackingScreen.kt` SHALL define granular, decoupled permission check lambdas: `checkHasLocation`, `checkHasNotification` (API 33+), `checkHasBluetoothConnect` (API 31+), and `checkHasBluetoothScan` (API 31+).
• Missing notifications or Bluetooth permissions SHALL NOT be shadowed or bypassed by location permission being already granted.
2. *Decoupled Multi-Step Cascade for Workout Start (`REQ-PRI-006.2`)*:
• When the athlete taps Start (`handleStartClick`):
- If `!checkHasLocation()`: the system SHALL present `RationaleStep.FOREGROUND` (requesting Location, Notifications on API 33+, and Bluetooth on API 31+).
- If `checkHasLocation()` is `true`: the system SHALL evaluate `proceedAfterPermissions()`:
a. If `!checkHasNotification()` (Android 13+) and notification permission has not yet been requested or dismissed in the current session: the system SHALL present `RationaleStep.NOTIFICATION` (or request `POST_NOTIFICATIONS`).
b. If paired Bluetooth sensors are registered (`hasPairedBluetoothSensors == true`) and `!checkHasBluetoothConnect()` (Android 12+): the system SHALL present `RationaleStep.BLUETOOTH` (or request `BLUETOOTH_CONNECT`).
c. If the athlete declines or dismisses notification or Bluetooth prompts ("Not Now"), the system SHALL gracefully advance to the next step without blocking workout tracking.
d. If `!checkHasBackgroundLocation()` (Android 11+): the system SHALL present `RationaleStep.BACKGROUND_LOCATION`.
e. If `!checkIsIgnoringBatteryOptimizations()`: the system SHALL present `RationaleStep.BATTERY_OPTIMIZATION`.
f. When all required/un-dismissed steps are satisfied: the system SHALL execute `onStart()`.
3. *Just-in-Time Bluetooth Permissions for Sensor Discovery (`REQ-PRI-006.3`)*:
• In `ControlTrackingScreen.kt` (`ResearchButton` / `onSearch`): when tapped, the system SHALL check whether Bluetooth permissions are granted (`BLUETOOTH_SCAN` and `BLUETOOTH_CONNECT` on API 31+; `ACCESS_FINE_LOCATION` and `BLUETOOTH_ADMIN` on API < 31); if missing, it SHALL launch the permission request launcher rather than dispatching search blindly.
• In `DevicesTabbedScreen.kt`: before initiating BLE scanning or pairing scans for `Protocol.BLUETOOTH_LE`, the system SHALL verify Bluetooth scan permission; if missing, it SHALL request permission contextually, and if denied, display `bluetooth_permission_scan_required` with action `grant_permission` rather than running a silent dead search.
• In `DevicesTabbedScreen.kt`: screen lifecycle teardown cleanup (`tabViewModel.stopSearching()`, `showProtocolSheet = false`, `selectedPairingProtocol = null`) SHALL be isolated into `DisposableEffect(Unit)`, and reactive search activation upon permission grant SHALL be isolated into `LaunchedEffect(hasBluetoothScanPermission)`, guaranteeing that granting Bluetooth scan permission during BLE pairing never executes disposal cleanup and preserves `selectedPairingProtocol` so `DeviceTypeSelectionDialog` opens seamlessly (ATT-4449).
4. *Start Button Warning Badge Synchronization (`REQ-PRI-006.4`)*:
• The warning indicator on `ControlTrackingButton` SHALL evaluate to `true` whenever: Precise Location is missing (`!hasLocationPermission`), OR battery optimization exemption is missing (`!isIgnoringBatteryOptimizations`), OR paired Bluetooth sensors exist but Bluetooth connect permission is missing (`hasPairedBluetoothSensors && !hasBluetoothPermission`).
5. *Legacy Permission Dead Code Retirement (`REQ-PRI-006.5`)*:
• Obsolete methods `getPermissions()`, `getPermissions(popup: Boolean)`, `onRequestPermissionsResult`, and `showBackgroundLocationDialog()` in `MainActivityWithNavigation.kt` SHALL be completely removed, and `MainActivityStartupPermissionContractTest.kt` SHALL retire the reflection assertion while preserving cold-start freedom and location safety.
6. *Full 9-Language Localization Parity (`REQ-PRI-006.6`)*:
• All new string resources (`bluetooth_permission_scan_required`, `grant_permission`, `permission_rationale_notification_subtitle`, `permission_rationale_bluetooth_subtitle`) SHALL be localized with 100% parity across EN, DE, ES, FR, IT, JA, NL, PL, PT.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Refines and supersedes `REQ-PRI-003` (*Contextual Just-in-Time Permission Flow, Zero-Friction Cold Start, Background Location Escalation, Battery Optimization Exemption & Graceful Settings Return Handling*) and test specification `TST-PRI-002`. Targets `ControlTrackingScreen.kt`, `ControlTrackingButton.kt`, `DevicesTabbedScreen.kt`, `MainActivityWithNavigation.kt`, and `BTSearchForNewDevicesEngine.java`.
2. *Historical Origin & Commit Trace*: Sprint 2026-40.16 under `ATT-2075` (commits `009c8f49`, `4a12158e`, `6b53f065`) replaced eager startup dialogs with contextual just-in-time sheets; refined in Sprint 2026-41.2 under `ATT-2621` (`REQ-PRI-005`); discovered during whole-app architectural code review of `feature/ATT-3921` (`0234bfc0`), filed under `ATT-3998`; refined in Sprint 2026-43.4 under `ATT-4449`.
3. *Root Reason for Existing Formulation*: Zero-friction cold start grouped all permissions into `RationaleStep.FOREGROUND` and gated it exclusively on `!checkHasLocation()`, assuming fresh installs would always lack location. This caused upgraded users with location already granted to never be prompted for `POST_NOTIFICATIONS` (Android 13+) or `BLUETOOTH_CONNECT` (Android 12+), while sensor discovery lacked Compose permission integration and `MainActivityWithNavigation` kept dead legacy methods. In ATT-3998, `tabViewModel.startSearching()` was placed inside `DisposableEffect(hasBluetoothScanPermission)`, which inadvertently disposed and wiped `selectedPairingProtocol = null` when permission changed from false to true during pairing.
4. *Preservation of Core Invariants*: Zero cold-start popups (`REQ-PRI-003`), strict fine location requirement (`REQ-PRI-004`), Android 11+ two-step background location cascade, fail-safe OEM battery optimization handling (`REQ-PRI-005`), Android 8–11 BLE compatibility (`REQ-CON-021`, ATT-3997), sensor search resource cleanup on screen exit, and 100% full test suite pass rate remain strictly preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an athlete on Android 13+ with location already granted but notifications missing,
• *When* tapping Start on `ControlTrackingScreen`,
• *Then* `checkHasNotification() == false` is detected and notification rationale/prompt is presented.
• *Given* an athlete on Android 12+ with location granted and paired BLE sensors but Bluetooth missing,
• *When* tapping Start,
• *Then* `checkHasBluetoothConnect() == false` is detected and Bluetooth rationale/prompt is presented.
• *Given* an athlete dismissing notification or Bluetooth rationale with "Not Now",
• *When* dismissed,
• *Then* the cascade proceeds to background location / battery optimization / tracking start without blocking.
• *Given* an athlete lacking fine location,
• *When* tapping Start,
• *Then* `RationaleStep.FOREGROUND` is presented with the full onboarding permission bundle.
• *Given* `ControlTrackingScreen` with `ResearchButton` on Android 12+ lacking `BLUETOOTH_SCAN`,
• *When* tapping "Suchen",
• *Then* Bluetooth permission launcher is triggered and search broadcast is withheld until granted.
• *Given* `DevicesTabbedScreen` opened on Android 12+ lacking `BLUETOOTH_SCAN`,
• *When* displaying tabs or initiating BLE pairing scan,
• *Then* permission is requested and, if denied, `bluetooth_permission_scan_required` is displayed with `grant_permission` action.
• *Given* `MainActivityWithNavigation`,
• *When* inspected,
• *Then* `getPermissions(popup: Boolean)` and `showBackgroundLocationDialog()` do not exist, and `MainActivityStartupPermissionContractTest` passes cleanly.
• *Given* `DevicesTabbedScreen` open on Android 12+ lacking `BLUETOOTH_SCAN` when selecting Bluetooth via the `+` FAB,
• *When* the athlete grants `BLUETOOTH_SCAN` in the system permission dialog,
• *Then* `hasBluetoothScanPermission` becomes `true`, `selectedPairingProtocol` remains `Protocol.BLUETOOTH_LE`, `DisposableEffect(Unit)` does NOT dispose, and `DeviceTypeSelectionDialog` renders immediately.

**Invariants**: Zero cold-start popups (`REQ-PRI-003`), strict fine location gating (`REQ-PRI-004`), 2-step background location cascade, fail-safe battery intent handling (`REQ-PRI-005`), Android 8–11 BLE compatibility (`REQ-CON-021`), and 100% clean-room test pass rate MUST NOT be broken.

### REQ-SET-079: Non-Blocking DataStore Architecture, Reactive In-Memory StateFlow Caching & Pure Parameter Passing for UI and Sensor Hot Paths [Verified]
- **Tests**: `TST-SET-079`
- **Priority**: Critical
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Implementation**: `SettingsDataStoreJavaHelper.kt`, `SettingsDataStore.kt`, `TuningPreferencesDataStore.kt`, `BatterySaverModel.kt`, `TrackingTabsScreen.kt`, `TrackingViewModel.kt`, `SpeedAndLocationDevice.java`, `VerticalSpeedAndSlopeDevice.java`, `TelemetryZoneMath.kt`, `LocationNameResolver.kt`
- **Rationale**: The system SHALL eliminate blocking `runBlocking` DataStore reads from all UI and sensor hot paths through reactive in-memory caching, singleton encapsulation, and pure parameter passing (ATT-3999, refining `REQ-SET-073` and `REQ-SET-074`):
1. *Reactive In-Memory StateFlow Caching in DataStore Repositories (`TuningPreferencesDataStore.kt`, `SettingsDataStore.kt`, `REQ-SET-079.1`)*:
• `TuningPreferencesDataStore` SHALL provide a singleton accessor `getInstance(context: Context)` and maintain an application-scoped `CoroutineScope(SupervisorJob() + Dispatchers.Default)` (`appScope`).
• `TuningPreferencesDataStore` SHALL expose an in-memory cached `StateFlow<TuningConfig>` eagerly collected from `tuningConfigFlow` (`stateIn(appScope, SharingStarted.Eagerly, TuningPreferencesDefaults.createDefaultTuningConfig())`).
• `TuningPreferencesDataStore` SHALL expose a non-blocking $O(1)$ synchronous getter `getTuningConfig(): TuningConfig` reading `tuningConfigStateFlow.value`.
• `SettingsDataStore` SHALL expose an in-memory cached state / `StateFlow` for zone threshold cutoffs (HR and Power), FTP, athlete weight, Critical Power, and $W'$, eagerly updated from `dataStore.data`.
• `SettingsDataStore` SHALL expose non-blocking $O(1)$ synchronous getters `getZoneMaxSync(zoneType: ZoneType, zoneIndex: Int): Int`, `getFtpSync(): Int?`, `getAthleteWeightKgSync(): Float`, `getCpWattsSync(): Int?`, and `getWPrimeKjSync(): Float?`.
• Unused dead code `SettingsDataStore.getSummary(zoneType)` calling `runBlocking` SHALL be completely removed.
2. *Zero Thread Blocking in Java Bridge (`SettingsDataStoreJavaHelper.kt`, `REQ-SET-079.2`)*:
• All synchronous bridge query functions in `SettingsDataStoreJavaHelper` (`getZoneMax`, `getZoneDisplayOptions`, `getTuningConfig`, `getGpsAccuracyThreshold`, `getAltitudeFilterWindow`, `getSlopeMinSpeed`, `getFtp`, `isAutoPauseEnabled`, `getAutoPauseSpeedThresholdKmh`, `getAutoPauseDebounceSec`, `getAutoPauseConfig`, `getAthleteWeightKg`, `getCpWatts`, `getEffectiveCpWatts`, `getWPrimeKj`, `getEffectiveWPrimeKj`) SHALL read directly from the singleton in-memory caches of `SettingsDataStore` and `TuningPreferencesDataStore` without calling `runBlocking` or `runBlocking(Dispatchers.IO)`.
• `SettingsDataStoreJavaHelper.setAthleteWeightKg(context, weightKg)` SHALL dispatch asynchronously onto `appScope.launch` instead of parking the caller thread via `runBlocking(Dispatchers.IO)`.
• All public static method signatures in `SettingsDataStoreJavaHelper` SHALL remain 100% backward-compatible, ensuring that existing unit tests utilizing `mockkStatic(SettingsDataStoreJavaHelper::class)` (`TelemetryZoneMathTest`, `SpeedAndLocationDeviceDecayTest`, `TrackerServiceResilienceTest`, `TrackerServiceDeviceNameQuotingTest`) continue to pass without regression.
• KDoc comments claiming `runBlocking(Dispatchers.IO)` prevents UI thread blocking SHALL be corrected to document thread-safe, non-blocking in-memory semantics.
3. *Pure Parameter Passing & Decoupled UI Hot Paths (`BatterySaverModel.kt`, `TrackingTabsScreen.kt`, `TrackingViewModel.kt`, `REQ-SET-079.3`)*:
• `BatterySaverModel.kt` SHALL expose a pure function `calculateZoneIndex(value: Double, cutoffs: List<Int>): Int` that computes the 1-based zone index without receiving `Context` or accessing `SettingsDataStoreJavaHelper`.
• The legacy overload `calculateZoneIndex(context: Context, zoneType: SettingsDataStore.ZoneType, value: Double): Int` SHALL be retained as a backward-compatible wrapper delegating to the non-blocking helper.
• `TrackingTabsScreen.kt`'s `LaunchedEffect(filteredSensorData, ...)` SHALL pass pre-resolved or observed zone cutoffs into `calculateZoneIndex`, completely eliminating repetitive DataStore queries during live sensor frame delivery.
• `TrackingViewModel.kt` SHALL observe zone threshold flows in `observeZoneDisplayOptions()`, maintaining in-memory cutoff lists per `ZoneType`. `calculateZoneColor` and `calculateZoneGaugeState` SHALL evaluate these in-memory cutoff lists directly, executing zero `SettingsDataStoreJavaHelper` or `runBlocking` calls on the UI thread during sensor update processing.
4. *Sensor Driver Non-Blocking Threshold Evaluation (`SpeedAndLocationDevice.java`, `VerticalSpeedAndSlopeDevice.java`, `REQ-SET-079.4`)*:
• `SpeedAndLocationDevice.onNewLocation` SHALL read `gpsAccuracyThresholdMeters` via the non-blocking helper without thread parking or disk I/O on the location looper thread.
• `VerticalSpeedAndSlopeDevice.calculateMetrics` SHALL read `slopeMinSpeedMps` via the non-blocking helper without thread parking on the sensor handler thread.
5. *UI Chart Rendering Non-Blocking Threshold Loading (`TelemetryZoneMath.kt`, `REQ-SET-079.5`)*:
• `loadHeartRateThresholds` and `loadPowerThresholds` in `TelemetryZoneMath.kt` SHALL execute non-blocking $O(1)$ memory reads via `SettingsDataStoreJavaHelper.getZoneMax`, ensuring that view layout and Compose drawing passes in `ElevationProfile.kt`, `MapDetailLayout.kt`, and `TelemetryMetricGraph.kt` never park the Main thread.
6. *Worker Thread Contract Guarding (`LocationNameResolver.kt`, `REQ-SET-079.6`)*:
• `LocationNameResolver.resolveLocationNameBlocking` SHALL be explicitly annotated with `@WorkerThread`, and its KDoc SHALL document that it executes blocking network/disk geocoding and must only be invoked from background worker threads.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Refines and supersedes the synchronous DataStore query and bridge specifications in `REQ-SET-073` (*Advanced Sensor & Service Tuning Preferences*) and `REQ-SET-074` (*AMOLED Battery Saver*), and interacts with `REQ-UI-399.1`, `REQ-TRK-019`, `REQ-MIG-039.2`, `REQ-TRK-018`, `REQ-UI-260`, `REQ-UI-370`. Targets `SettingsDataStoreJavaHelper.kt`, `SettingsDataStore.kt`, `TuningPreferencesDataStore.kt`, `BatterySaverModel.kt`, `TrackingTabsScreen.kt`, `TrackingViewModel.kt`, `SpeedAndLocationDevice.java`, `VerticalSpeedAndSlopeDevice.java`, `TelemetryZoneMath.kt`, and `LocationNameResolver.kt`.
2. *Historical Origin & Commit Trace*: Introduced in commit `72e33747` and expanded across `a48c8a38` (ATT-1265), `f2c2409f` (ATT-1304), `f37f3b94` (ATT-3184), `559376e2` / `1e76228d` (ATT-1289), `5bd9e171` (ATT-3677), `61ccb801` (ATT-3862), `e5d9225e` (ATT-3860), and `83d9b354` (ATT-1268). Discovered during whole-app code review of `feature/ATT-3921` (`0234bfc0`), filed under `ATT-3999`.
3. *Root Reason for Existing Formulation*: Synchronous bridges with `runBlocking` and `runBlocking(Dispatchers.IO)` were introduced as an expedient bridge for legacy Java classes (`BANALService`, `TrackerService`, sensor devices) to access asynchronous DataStore `Flow`s without rewriting Java drivers. Misleading KDoc comments claimed `runBlocking(Dispatchers.IO)` avoided UI blocking. High-frequency callers in Compose UI (`LaunchedEffect`, `TrackingViewModel`) and sensor drivers subsequently invoked these bridges on hot paths.
4. *Preservation of Core Invariants*: Sensor accuracy and filtering thresholds, slope and VAM algorithms, power metrics, auto-pause debounce, and AMOLED battery saver dimming logic remain 100% mathematically and functionally identical. The public static API and return types of `SettingsDataStoreJavaHelper` remain intact, preserving static mock compatibility (`mockkStatic(SettingsDataStoreJavaHelper::class)`) for all existing test suites. Instant reactivity (settings UI changes immediately update in-memory caches) and thread safety across Main/Sensor/Background threads are strictly guaranteed.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an active workout with HR and power sensors transmitting live telemetry,
• *When* sensor values update at high frequency (1–10 Hz) in `TrackingTabsScreen` and `TrackingViewModel`,
• *Then* zero `runBlocking` or `runBlocking(Dispatchers.IO)` calls are executed on the Android Main (UI) thread.
• *Given* active GPS and barometer sensor sampling,
• *When* incoming location fixes arrive in `SpeedAndLocationDevice.onNewLocation` or metrics are evaluated in `VerticalSpeedAndSlopeDevice.calculateMetrics`,
• *Then* threshold lookups are resolved via $O(1)$ in-memory access with zero thread parking on the location looper or sensor handler thread.
• *Given* an athlete adjusting tuning preferences (e.g. GPS accuracy rejection threshold, auto-pause debounce, slope min speed) or training zone boundaries in Settings,
• *When* the change is persisted,
• *Then* the in-memory `StateFlow` cache updates immediately and the updated values are evaluated on the very next telemetry tick without requiring an application restart.
• *Given* `BatterySaverModel.calculateZoneIndex(value: Double, cutoffs: List<Int>)`,
• *When* evaluated across any numeric input and monotonic cutoff list,
• *Then* it returns the correct 1-based zone index purely in-memory without `Context` or DataStore dependencies.
• *Given* existing unit test suites utilizing `mockkStatic(SettingsDataStoreJavaHelper::class)` (including `TelemetryZoneMathTest`),
• *When* unit tests are executed,
• *Then* static method mock bindings continue to function seamlessly with zero test regressions.
• *Given* `SettingsDataStore.kt` and `LocationNameResolver.kt`,
• *When* inspected,
• *Then* `SettingsDataStore.getSummary(zoneType)` is removed, and `LocationNameResolver.resolveLocationNameBlocking` is annotated with `@WorkerThread`.

**Invariants**: Zero behavioral or sensor metric regression, 100% clean-room test suite pass rate, strict mockkStatic compatibility, thread safety, scope bounding (ATT-1250), and parent ticket human decision gate MUST NOT be broken.

### REQ-PRI-007: Abortable First-Install Permission Onboarding Flow and Upgrade Auto-Skip Architecture [Verified]
- **Tests**: `TST-PRI-006`
- **Priority**: Critical
- **Type**: Functional
- **Verification Method**: Unit, Integration
- **Implementation**: `PermissionOnboardingSheet.kt`, `ATrainingTrackerApp.kt`, `TuningPreferencesDataStore.kt`, `strings.xml` (all 9 locales)
- **Rationale**: The system SHALL provide a welcoming, educational, and abortable/skippable first-launch permission onboarding flow (`PermissionOnboardingSheet`) on initial application launch (`savedInstanceState == null`), guiding new athletes step-by-step through essential runtime permissions (Location & Background Location, Nearby Bluetooth Sensors on Android 12+, Notifications on Android 13+) and battery optimization exemption, SHALL allow skipping individual steps or aborting the entire flow at any point without blocking app access, SHALL persist onboarding completion/dismissal in `TuningPreferencesDataStore`, SHALL automatically auto-skip onboarding on upgraded installs when all essential permissions are already held, SHALL maintain 100% 9-language localization parity, and SHALL strictly preserve existing Just-in-Time fallback rationale sheets on `ControlTrackingScreen` and `DevicesTabbedScreen` (ATT-4455, refining `REQ-PRI-003` and `REQ-PRI-006`):
1. *First-Launch Detection & Upgraded Install Auto-Skip (`ATrainingTrackerApp.kt`, `TuningPreferencesDataStore.kt`, `REQ-PRI-007.1`)*:
• `TuningPreferencesDataStore` SHALL define `KEY_HAS_COMPLETED_PERMISSION_ONBOARDING: Preferences.Key<Boolean>`, defaulting to `false` (`DEFAULT_HAS_COMPLETED_PERMISSION_ONBOARDING`), with reactive flow `hasCompletedPermissionOnboardingFlow: Flow<Boolean>` and persistence mutator `suspend fun setHasCompletedPermissionOnboarding(completed: Boolean)`.
• In `ATrainingTrackerApp.kt`, `hasCompletedPermissionOnboardingFlow` SHALL be collected safely with initial value `true` to avoid transient UI flashes while DataStore initializes.
• Startup helper `checkAreAllPermissionsGranted(context)` SHALL evaluate: (a) `ACCESS_FINE_LOCATION` granted; (b) on Android 12+ (API 31+), `BLUETOOTH_SCAN` and `BLUETOOTH_CONNECT` granted; (c) on Android 13+ (API 33+), `POST_NOTIFICATIONS` granted; (d) `PowerManager.isIgnoringBatteryOptimizations` granted.
• If `hasCompletedPermissionOnboardingFlow` emits `false` and `checkAreAllPermissionsGranted` is `true` (upgraded install or backup restore), the system SHALL immediately invoke `setHasCompletedPermissionOnboarding(true)` without presenting the onboarding UI.
• If `hasCompletedPermissionOnboardingFlow` emits `false` and permissions are missing, the system SHALL present the `PermissionOnboardingSheet` overlay.
2. *Step-by-Step Educational Onboarding Wizard (`PermissionOnboardingSheet.kt`, `REQ-PRI-007.2`)*:
• `PermissionOnboardingSheet.kt` SHALL be implemented as a modular Material 3 `ModalBottomSheet` strictly `< 400` lines.
• The wizard SHALL guide the athlete across 4 discrete setup categories:
a. Step 1 (`OnboardingStep.LOCATION`): Location and background GPS tracking (`ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`, `ACCESS_BACKGROUND_LOCATION`).
b. Step 2 (`OnboardingStep.BLUETOOTH`): Nearby Bluetooth sensors (`BLUETOOTH_SCAN`, `BLUETOOTH_CONNECT` on Android 12+; skipped on API < 31).
c. Step 3 (`OnboardingStep.NOTIFICATION`): Workout foreground service status, lap splits, and battery alerts (`POST_NOTIFICATIONS` on Android 13+; skipped on API < 33).
d. Step 4 (`OnboardingStep.BATTERY_OPTIMIZATION`): Doze mode background survival exemption (`ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`).
• The header SHALL render a step progress counter ("Step %1$d of %2$d") and an abort action button ("Skip Setup" / `permission_onboarding_skip_setup`).
3. *Total Abortability & Non-Blocking Navigation (`REQ-PRI-007.3`)*:
• The athlete SHALL be able to skip any individual step via a secondary "Skip this step" action (`permission_onboarding_skip_step`), advancing immediately to the subsequent step without requesting or granting the permission.
• Tapping "Skip Setup" (`permission_onboarding_skip_setup`) or dismissing the bottom sheet scrim at any point SHALL dismiss the flow immediately and persist `setHasCompletedPermissionOnboarding(true)`, allowing immediate access to the app cockpit without modal locks or unskippable walls.
4. *Preservation of Just-in-Time Fallback Invariants (`REQ-PRI-007.4`)*:
• All existing Just-in-Time permission checks in `ControlTrackingScreen.kt` (`handleStartClick`, `proceedAfterPermissions`) and `DevicesTabbedScreen.kt` (`checkHasBluetoothScan`) SHALL remain completely intact.
• If an athlete skips any permission during onboarding, the corresponding contextual JIT rationale sheet SHALL still be presented when they subsequently trigger an action requiring that capability (e.g. tapping Start or scanning for sensors).
5. *100% 9-Language Localization Parity (`REQ-PRI-007.5`)*:
• All user-facing onboarding strings (`permission_onboarding_title`, `permission_onboarding_step_format`, `permission_onboarding_skip_setup`, `permission_onboarding_skip_step`, `permission_onboarding_grant`, `permission_onboarding_continue`, `permission_onboarding_finish`, `permission_onboarding_location_title`, `permission_onboarding_location_desc`, `permission_onboarding_bluetooth_title`, `permission_onboarding_bluetooth_desc`, `permission_onboarding_notification_title`, `permission_onboarding_notification_desc`, `permission_onboarding_battery_title`, `permission_onboarding_battery_desc`) SHALL exist across all 9 canonical locales (`values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`) with indexed positional format specifiers (`%1$d`, `%2$d`) and zero literal `"+ "` prefixes.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Refines and amends `REQ-PRI-003` (*Contextual Just-in-Time Permission Flow, Zero-Friction Cold Start, Background Location Escalation, Battery Optimization Exemption & Graceful Settings Return Handling*) and `REQ-PRI-006` (*Decoupled Multi-Permission Cascade for Workout Start*). Targets `ATrainingTrackerApp.kt`, `TuningPreferencesDataStore.kt`, `PermissionOnboardingSheet.kt`, `ControlTrackingScreen.kt`, and `DevicesTabbedScreen.kt`.
2. *Historical Origin & Commit Trace*: `ATT-2075` (Sprint 2026-40.16) retired eager cold-start dialogs; `ATT-2621` (Sprint 2026-41.2) decoupled battery optimization warnings; `ATT-3998` (Sprint 2026-43.3) decoupled start cascade; usability friction identified during clean-install verification of `ATT-3387` where first-time athletes experienced 4 consecutive educational rationale sheets and 3 system dialogs upon first tapping Start.
3. *Root Reason for Existing Formulation*: Pure JIT moved 100% of permission requests to user actions (tapping Start or searching sensors), which prevented cold-start popups for returning athletes, but concentrated the cumulative setup burden at the moment the athlete was ready to start recording.
4. *Preservation of Core Invariants*: Returning athletes experience zero cold-start popups (`REQ-PRI-003`); strict fine location requirement (`REQ-PRI-004`); Android 11+ two-step background location cascade; fail-safe platform intent handling (`REQ-PRI-005`); JIT fallback safety preserved for skipped permissions (`REQ-PRI-006`); non-blocking app exploration; 100% full test suite pass rate.

**Acceptance Criteria (Given-When-Then)**:
• *Given* a clean app install where `hasCompletedPermissionOnboardingFlow` is false and permissions are missing,
• *When* the app is launched on cold start (`savedInstanceState == null`),
• *Then* `PermissionOnboardingSheet` is presented, displaying Step 1 (Location & GPS Tracking) with a "Skip Setup" button.
• *Given* an active `PermissionOnboardingSheet`,
• *When* the user taps "Skip Setup" or dismisses the bottom sheet,
• *Then* the sheet closes, `setHasCompletedPermissionOnboarding(true)` is persisted, and the main cockpit is accessible immediately without granting permissions.
• *Given* an active `PermissionOnboardingSheet` on Step 1,
• *When* the user taps "Skip this step",
• *Then* the wizard advances to Step 2 without requesting location permission.
• *Given* an upgraded install or backup restore where all essential permissions are already granted,
• *When* the app is launched and DataStore emits `hasCompletedPermissionOnboardingFlow == false`,
• *Then* `checkAreAllPermissionsGranted()` evaluates to true, `setHasCompletedPermissionOnboarding(true)` is persisted, and no onboarding sheet is displayed.
• *Given* a user who completed or skipped the onboarding flow without granting location permission,
• *When* they subsequently tap Start on `ControlTrackingScreen`,
• *Then* the standard JIT rationale cascade (`RationaleStep.FOREGROUND`) is presented.
• *Given* all 15 onboarding string keys,
• *When* verified by `TranslationParityTest`,
• *Then* all 9 locales pass with 100% translation parity and matching format specifiers.

**Invariants**: Zero cold-start regressions for returning users (`REQ-PRI-003`), non-blocking app exploration (total abortability), preservation of all JIT fallbacks (`REQ-PRI-006`), strict fine location requirement (`REQ-PRI-004`), fail-safe battery intent handling (`REQ-PRI-005`), UI file length `< 400` lines, 100% clean-room test pass rate.

### REQ-LOC-002: Locale Hygiene, Translation Completeness, and ExtraTranslation De-duplication [Verified]
- **Tests**: `TST-STR-019`
- **Priority**: Medium
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Implementation**: `res/values-*/strings.xml`, `res/values-{de,pl}/arrays.xml`, `TranslationParityTest.kt`
- **Rationale**: The system SHALL eliminate raw untranslated English strings across all non-default locales, purge redundant declarations of non-translatable keys from all 8 locale `strings.xml` variants to eliminate Android `ExtraTranslation` lint warnings while preserving default resource fallbacks, purge redundant string arrays referencing `@string` tokens from locale `arrays.xml` files, and enforce these hygiene standards via automated unit test guards in `TranslationParityTest.kt` (ATT-4029, Parent Epic ATT-355, status: Verified, extending `REQ-LOC-001`):
1. *Translation Completeness (`REQ-LOC-002.1`)*:
• English string literals remaining in non-English locales SHALL be translated into their respective native languages: `values-de/strings.xml` (`pebble_build_in`, `tss`, `pedal_power_balance`), `values-nl/strings.xml` (`zone_2_label`, `zone_3_label`, `zone_5_label`), `values-pl/strings.xml` (`exporting_to_file*` 4 keys), and `values-{es,fr,it,ja,nl,pl,pt}/strings.xml` (`backup_restore`).
2. *ExtraTranslation De-duplication (`REQ-LOC-002.2`)*:
• The 7 keys marked `translatable="false"` in default `values/strings.xml` (`ant_service_network_key`, `ant_service_samico_key`, `ant_service_user_key`, `intensity_factor_short`, `normalized_power_short`, `tss_short`, `work_kj_short`) SHALL be removed from all 8 localized `values-*/strings.xml` files, allowing Android runtime resource fallback to provide canonical values without triggering `ExtraTranslation` lint warnings.
3. *Redundant String-Array Excision (`REQ-LOC-002.3`)*:
• Verbatim duplicate `string-array` elements in `values-de/arrays.xml` (8 arrays) and `values-pl/arrays.xml` (5 arrays) that merely reference `@string/...` identifiers SHALL be removed, relying on base `values/arrays.xml` for structural array resolution and localized string replacement.
4. *Automated Governance Guard (`REQ-LOC-002.4`)*:
• `TranslationParityTest.kt` SHALL include an automated test (`testNoExtraTranslationsInLocales`) asserting that no string key marked `translatable="false"` in base `values/strings.xml` is redeclared in any localized `values-<locale>/strings.xml` file.
5. *Preservation of System Invariants (`REQ-LOC-002.5`)*:
• 100% translation parity across all 9 locales maintained under `TranslationParityTest`.
• Fallback resolution for non-translatable strings remains functionally identical.
• Zero regressions in UI text rendering or full test suite.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement `REQ-LOC-002` under Epic `ATT-355` (*Good and consistent UI*), extending `REQ-LOC-001` (*Global Translation Parity*). Targets `res/values-*/strings.xml`, `res/values-{de,pl}/arrays.xml`, and `TranslationParityTest.kt`.
2. *Historical Origin & Commit Trace*: Whole-app code review (`feature/ATT-3921` @ `0234bfc0`, Oct 2026). `ant_service_*` keys originated from commit `5a8d102e` ANT+ radio refactor; arrays were duplicated during legacy Eclipse-to-Gradle migration.
3. *Root Reason for Existing Formulation*: Keys and arrays were duplicated across locale folders to silence missing-resource build warnings before automated parity gating (`TranslationParityTest`) was introduced.
4. *Preservation of Core Invariants*: Android resource fallback ensures 100% availability of non-translatable strings at runtime. Zero translatable keys are removed. 100% translation parity is preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an athlete using German, Dutch, Polish, Spanish, French, Italian, Japanese, or Portuguese locale,
• *When* viewing pebble watchapp settings, zone labels, export notifications, or backup & restore headers,
• *Then* all strings appear in the selected native language without untranslated English text.
• *Given* all 8 localized `values-*/strings.xml` files,
• *When* inspected for `translatable="false"` keys defined in base `values/strings.xml`,
• *Then* zero redundant copies are declared, eliminating `ExtraTranslation` lint warnings.
• *Given* `values-de/arrays.xml` and `values-pl/arrays.xml`,
• *When* inspected,
• *Then* redundant string-arrays referencing `@string/...` are excised, resolving cleanly from base.
• *Given* `TranslationParityTest`,
• *When* `testNoExtraTranslationsInLocales` and string parity tests execute,
• *Then* all tests pass with 100% success.

**Invariants**: 100% translation parity, Android resource fallback integrity, 100% clean-room test pass rate.

---

### REQ-SET-080: Dedicated Auto-Pause Settings Cockpit & Navigation Drawer Entry [Verified]
- **Tests**: `TST-SET-080`
- **Priority**: High
- **Type**: Functional
- **Verification Method**: Unit, Integration
- **Rationale**: The system SHALL provide a dedicated, first-class Auto-Pause configuration cockpit in the Navigation Drawer's Settings section with sport-specific tabs, retiring scattered controls from Advanced Tuning to ensure high discoverability, intuitive customization, and an extensible foundation for upcoming auto-pause enhancements (ATT-4836, ATT-4924, ATT-5820):
1. *Navigation Drawer Entry & Route Resolution (`REQ-SET-080.1`)*:
• An *Auto-Pause* item (`drawer_auto_pause`) SHALL be included in the Navigation Drawer within the Settings group directly below *Sport Types* (`AppNavigationDrawer.kt`, `ids.xml`).
• `NavRoutes.toRoute(R.id.drawer_auto_pause)` SHALL resolve to `NavRoutes.AUTO_PAUSE`, and `NavRoutes.toDrawerItemId(NavRoutes.AUTO_PAUSE)` SHALL resolve to `R.id.drawer_auto_pause`.
2. *Full-Screen Tabbed Auto-Pause Settings Cockpit with Fixed Header (`AutoPauseTabsScreen.kt`, `AutoPauseViewModel.kt`, `REQ-SET-080.2`)*:
• The system SHALL render a full-screen Material 3 settings interface composed of 3 sport tabs: *Cycling* (`BSportType.BIKE`), *Running* (`BSportType.RUN`), and *Other Sports* (`BSportType.UNKNOWN`).
• The screen SHALL include a fixed (non-collapsing) top app bar with navigation drawer hamburger button (`MainActivityWithNavigation.openDrawer()`), a single global *Start in Auto-Paused* card (`TuningToggleItem`) placed between the title row and the sport tabs row, and a sport-scoped *Reset to Defaults* action (`R.string.reset_to_defaults`). Title row, global card, and tabs SHALL remain visible during content scroll.
3. *Sport-Specific Controls & Immediate Persistence (`REQ-SET-080.3`)*:
• Each sport tab SHALL provide:
  - An Enable Auto-Pause toggle (`TuningToggleItem`) with dedicated label `tuning_auto_pause_enable_title` ("Enable Auto-Pause") controlling `autoPauseBikeEnabled` / `autoPauseRunEnabled` / `autoPauseOtherEnabled`.
  - A bidirectional Speed Threshold slider (`TuningSliderItem`, `tuning_auto_pause_speed_threshold_title` / `_desc` reflecting pause below and resume above) controlling `autoPauseBikeSpeedThreshold` / `autoPauseRunSpeedThreshold` / `autoPauseOtherSpeedThreshold`, rendered conditionally when enabled, displaying speed in athlete's configured speed unit (`km/h` or `mph`).
  - A Pause Debounce Delay slider (`TuningSliderItem`) controlling `autoPauseBikeDebounceSec` / `autoPauseRunDebounceSec` / `autoPauseOtherDebounceSec`, rendered conditionally when enabled, displaying seconds.
  - A Resume Debounce Delay slider (`TuningSliderItem`) controlling `autoPauseBikeResumeDebounceSec` / `autoPauseRunResumeDebounceSec` / `autoPauseOtherResumeDebounceSec`, rendered conditionally when enabled, displaying seconds.
• All modifications SHALL persist immediately to `TuningPreferencesDataStore` without requiring an explicit save button.
• Triggering *Reset to Defaults* on a tab SHALL restore that sport's auto-pause preferences to factory defaults.
4. *Single Canonical Configuration Surface (`REQ-SET-080.4`)*:
• In accordance with Design Guidelines Section 3.2, Auto-Pause controls SHALL be excised from `SensorsGpsFilterSection.kt`, `AdvancedTuningScreen.kt`, and `AdvancedTuningDialog.kt` so that Auto-Pause maintains a single unambiguous configuration home.
5. *9-Language Localization Parity (`REQ-SET-080.5`)*:
• All new drawer and screen string resources SHALL be localized with 100% parity across all 9 supported locales (`values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`).
6. *Collapsible Calibration Accordion with Live Active Values Subtitle (`AutoPauseTabsScreen.kt`, `TuningSubtitleFormatter`, `REQ-SET-080.6`)*:
• In accordance with Design Guidelines Section 3 (*Settings & Accordion Structure*), whenever Auto-Pause is enabled for a sport tab, the three technical calibration sliders (Speed Threshold, Pause Debounce Delay, Resume Debounce Delay) SHALL be encapsulated inside an expandable accordion section (`TuningAccordionSection`) titled `tuning_auto_pause_custom_thresholds_title` ("Schwellenwerte & Verzögerung anpassen" / "Customize thresholds & delays").
• The accordion section SHALL default to initially collapsed (`isExpanded = false` via `rememberSaveable`) per sport tab, keeping the primary enable toggle prominent and scannable without scrolling.
• The accordion header SHALL render a dynamic subtitle reflecting active configured values (`TuningSubtitleFormatter.formatAutoPauseSubtitle`, e.g., `"%1$s · Pause: %2$s · Fortsetzen: %3$s"` / `"%1$s · Pause: %2$s · Resume: %3$s"`).

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Net-new requirement `REQ-SET-080`, refining `REQ-UI-036` and `REQ-TRK-018`. Targets `AutoPauseTabsScreen.kt`, `AutoPauseViewModel.kt`, `strings.xml`.
2. *Historical Origin & Commit Trace*: Ticket `ATT-4836` (commit `69e9d6d3`), Sprint `2026-44.1`, `ATT-4924` (Sprint `2026-44.2`), and `ATT-5820` (Sprint `2026-45.12`).
3. *Root Reason for Existing Formulation*: Collapsing header was copied directly from `SportTypesTabsScreen.kt` to satisfy generic tab guidelines, and calibration sliders were rendered openly inside the tab card. However, displaying three technical sliders openly pushed options off-screen; bundling them into a default-collapsed accordion adheres to progressive disclosure (Design Guidelines Section 3).
4. *Preservation of Core Invariants*: Reactive persistence to DataStore, slider step ranges, per-sport reset to defaults, Drawer navigation, and True-Black AMOLED compliance are 100% preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* the athlete opens the Auto-Pause settings screen,
• *When* the screen renders,
• *Then* a fixed header containing the title row, a single global *Start in Auto-Paused* card, and the sport tabs row is visible and does not collapse on scroll.
• *Given* any sport tab with Auto-Pause enabled,
• *When* inspecting the content card,
• *Then* the calibration sliders are encapsulated inside an accordion section titled "Schwellenwerte & Verzögerung anpassen" (`tuning_auto_pause_custom_thresholds_title`), defaulting to collapsed (`isExpanded = false`).
• *Given* the collapsed accordion header,
• *When* inspecting its subtitle,
• *Then* it displays the active speed threshold, pause debounce delay, and resume debounce delay formatted with units.
• *Given* the collapsed accordion section,
• *When* the athlete taps the accordion header,
• *Then* it smoothly expands to reveal the three calibration sliders.
• *Given* the athlete triggers *Reset to Defaults*,
• *When* confirmed,
• *Then* the active sport's auto-pause settings revert to default values and the accordion subtitle updates reactively.

**Invariants**: Backward compatibility of `TuningPreferencesDataStore` keys and default values, `TrackerService` auto-pause calculation integrity, 100% 9-language translation parity.


### REQ-SET-081: Strict Preservation of Unclustered Status on Workout Edits & Explicit Cluster Management Gating [Verified]
- **Tests**: `TST-SET-081`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit, Integration
- **Implementation**: `WorkoutRepository.kt`, `EditWorkoutViewModel.kt`, `WorkoutClusterEngine.kt`, `EditWorkoutClusteringTest.kt`
- **Rationale**: The system SHALL ensure that unclustered workouts ('Offen', `clusterId <= 0`) strictly remain unclustered when saving metadata edits, and that cluster creation or assignment occurs exclusively through explicit user actions:
1. *Strict Preservation of Unclustered Status (`REQ-SET-081.1`)*:
• When saving workout metadata via `WorkoutRepository.saveWorkout(userEditedWorkout)`, if `userEditedWorkout.clusterId <= 0` (or `clusterId == -1L`), the system SHALL bypass the route clustering learning loop entirely.
• The system MUST NOT create a new `WorkoutCluster` or assign any cluster to an unclustered workout when the user edits workout properties (such as workout name, sport, equipment, notes, or flags).
2. *Gating of Automatic Cluster Assignment (`REQ-SET-081.2`)*:
• `WorkoutRepository.saveWorkout()` SHALL NOT execute `engine.assignClusterToWorkout(...)` or override the user's cluster selection under any circumstances.
• Creating a new cluster or associating a workout with an existing cluster SHALL occur exclusively through explicit user choices in `EditWorkoutClusterDialog` (`createNewCluster` or `applyClusterIdentity`).
3. *Centroid and Sport Learning for Clustered Workouts (`REQ-SET-081.3`)*:
• If `userEditedWorkout.clusterId > 0` and the workout has valid spatial coordinates (`startLatLng`, `endLatLng`, `maxDisplacementLatLng`), `saveWorkout()` SHALL invoke `engine.learnFromWorkout(..., clusterIdOverride = userEditedWorkout.clusterId)` to update the existing cluster's running centroids, distance, and probable sport without mutating cluster assignment.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an unclustered workout (`clusterId == -1L`, "Offen"),
• *When* the athlete edits the workout name to a custom title and saves the workout via `EditWorkoutScreen`,
• *Then* `WorkoutRepository.saveWorkout()` SHALL persist the name change without creating a new `WorkoutCluster` in SQLite and the workout SHALL remain unclustered (`clusterId == -1L`).
• *Given* a workout assigned to cluster A (`clusterId > 0`),
• *When* the athlete opens `EditWorkoutScreen`, selects "Offen" (`unassignCluster`), and saves the workout with a custom name,
• *Then* the workout SHALL strictly remain unclustered (`clusterId == -1L`) and no new cluster SHALL be created.
• *Given* a workout assigned to cluster A (`clusterId == 10L`),
• *When* the athlete edits the workout name or sport and saves without changing the cluster,
• *Then* `learnFromWorkout` SHALL update cluster 10's running centroids and sport, preserving `clusterId == 10L`.

**Invariants**: Explicit cluster creation and selection in `EditWorkoutClusterDialog` (`createNewCluster`, `applyClusterIdentity`, `unassignCluster`) remain fully functional; SQLite database schemas remain unchanged.

### REQ-SET-082: Timestamped Zone Boundary Snapshots per ZoneType & Historical Workout Zone Resolution [Verified] [Verified]
- **Tests**: `TST-SET-082`
- **Priority**: Critical
- **Type**: Functional
- **Verification Method**: Unit, Integration
- **Implementation**: `AthleteMetricsDatabaseManager.kt`, `AthleteMetricsRepository.kt`, `SettingsDataStore.kt`, `WorkoutRepository.kt`, `TelemetryMetricGraph.kt`, `ZonesSettingsViewModel.kt`, `ZoneProfileTabContent.kt`, `ZoneHistoryDialog.kt`
- **Rationale**: Currently, training zone boundaries (`HR_RUN`, `HR_BIKE`, `PWR_BIKE`) are stored only as single un-timestamped preferences in `SettingsDataStore`. When an athlete updates zone boundaries, past workouts in Workout Summaries, Workout Details, and Telemetry Graphs retroactively re-evaluate against modern boundaries, distorting training history. Extending timestamped persistence to zone configurations ensures historical workout fidelity and preserves full temporal integrity:
1. *Atomic Timestamped Zone Snapshot Persistence (`REQ-SET-082.1`)*:
• The system SHALL persist training zone boundary vectors as atomic snapshots in `AthleteMetrics.db` (`zone_history` table) with columns `_id` (INTEGER PRIMARY KEY AUTOINCREMENT), `timestamp_ms` (INTEGER NOT NULL), `zone_type` (TEXT NOT NULL), and `boundaries` (TEXT NOT NULL, holding comma-separated integer thresholds).
• The database schema SHALL define index `idx_zone_history_type_time` on `(zone_type, timestamp_ms DESC, _id DESC)`.
• `AthleteMetricsDbHelper.DB_VERSION` SHALL be incremented to `2`, referenced directly in `DatabaseSchemaRegistry.kt` pursuant to Rule 62, and verified with `onCreate`/`onUpgrade` schema parity pursuant to Rule 64.
• Boundary vectors SHALL strictly enforce ascending monotonicity ($Z_1 < Z_2 < \dots < Z_n$) where `HR_RUN` and `HR_BIKE` require exactly 4 cutoffs and `PWR_BIKE` requires exactly 6 cutoffs. Invalid or non-monotonic vectors MUST be rejected.
• On database upgrade to `v2` or fresh install, initial baseline snapshots SHALL be seeded from existing `SettingsDataStore` values inside a single SQLite transaction so existing users experience zero data loss.
2. *Unidirectional Projection to SettingsDataStore (`REQ-SET-082.2`)*:
• Whenever a zone snapshot is inserted, updated, or deleted, the system SHALL update `SettingsDataStore` with the chronologically latest snapshot (`max(timestamp_ms)`) for that `ZoneType`.
• Background sensor loops, HUD tiles, and live-tracking cockpits SHALL continue reading `SettingsDataStore` for zero-latency, lock-free $O(1)$ in-memory telemetry evaluation.
3. *Historical Workout Zone Resolution (`REQ-SET-082.3`)*:
• The system SHALL provide `getEffectiveZoneBoundaries(zoneType: ZoneType, workoutStartTimeMs: Long): List<Int>` querying `zone_history` for the latest snapshot satisfying `zone_type = ? AND timestamp_ms <= workoutStartTimeMs ORDER BY timestamp_ms DESC, _id DESC LIMIT 1`.
• If no snapshot exists with `timestamp_ms <= workoutStartTimeMs`, the query SHALL fall back to the earliest recorded snapshot for that `zoneType`, and if no snapshots exist in `zone_history`, to `SettingsDataStore` defaults.
4. *Aftermath Analytics & Visualizations Integration (`REQ-SET-082.4`)*:
• In `WorkoutRepository.kt`, `getHeartRateZoneDistribution(workoutId, bSportType)` and `getPowerZoneDistribution(workoutId)` SHALL resolve the workout's start timestamp from `WorkoutSummaries` and compute zone distributions using `getEffectiveZoneBoundaries`.
• In `WorkoutDetailTabbedContent.kt`, `MapDetailLayout.kt`, and `TelemetryMetricGraph.kt`, zone band coloring, horizontal dashed threshold lines, scrubbing badges, and marginal zone distribution bars SHALL evaluate against the effective zone thresholds valid at `workoutStartTimeMs`.
5. *Profile & Zones Management UI & Backdating (`REQ-SET-082.5`)*:
• In `Profile & Zones` (`ZoneProfileTabContent.kt`, `ZonesSettingsViewModel.kt`), normal zone edits via sliders or the Zone Calculator SHALL save a new snapshot with `timestamp_ms = System.currentTimeMillis()`.
• The UI SHALL provide a dedicated "History" action launching a historical snapshots dialog allowing athletes to view past snapshots, add a backdated snapshot for a previous date, or edit/delete past snapshots with date selection and monotonicity validation.

**Acceptance Criteria (Given-When-Then)**:
• *Given* a zone snapshot $S_1$ recorded at $t_1 = \text{2025-01-01}$ with bike power threshold $Z_4 = 250\text{ W}$, a workout $W_1$ recorded at $t_2 = \text{2025-06-01}$, and a newer snapshot $S_2$ recorded at $t_3 = \text{2026-01-01}$ with $Z_4 = 284\text{ W}$,
• *When* the user views $W_1$ in Workout Summaries, Workout Details, or Telemetry Graphs,
• *Then* the Power Zone distribution, telemetry band colors, and scrubbing badges SHALL calculate against $S_1$ ($250\text{ W}$), whereas workouts recorded after $t_3$ calculate against $S_2$ ($284\text{ W}$).
• *Given* an athlete in `Profile & Zones` who enters new zone cutoffs for a past date,
• *When* the athlete saves the historical snapshot,
• *Then* the snapshot SHALL be persisted in `AthleteMetrics.db` with the selected past timestamp, and workouts recorded after that date SHALL dynamically resolve against it.
• *Given* an attempt to save non-monotonic cutoffs (e.g. $Z_2 \le Z_1$),
• *Then* persistence SHALL fail with an `IllegalArgumentException` and the UI SHALL surface a localized validation error.

**Invariants**: Monotonicity ($Z_1 < Z_2 < \dots < Z_n$) is strictly enforced; `SettingsDataStore` remains synchronized with the latest snapshot; live cockpit $O(1)$ latency is preserved; `AthleteMetrics.db` migration maintains `onCreate`/`onUpgrade` parity.

### REQ-SET-083: Two-Device Free Tier Limit Across All Sensor Protocols (REQ-MON-001) [Verified]
- **Tests**: `TST-SET-083`, `TST-LOC-001`
- **Priority**: Critical
- **Type**: Functional
- **Verification Method**: Unit, Integration
- **Implementation**: `DevicesDatabaseManager.java`, `DeviceListViewModel.kt`, `ProFeatureManager.kt`
- **Rationale**: Establish a fair, transparent monetization model that supports hardware acquisition while keeping 100% of core tracking, telemetry processing, and local file exports free.
- **Description**:
1. *Uniform 2-Device Free Limit (`REQ-SET-083.1`)*:
• In the Free tier (`!isProActive()`), athletes SHALL be permitted to pair at most `MAX_FREE_PAIRED_DEVICES = 2` devices simultaneously.
• This limit applies uniformly across ALL supported device protocols: `SMARTPHONE` (internal sensors), `BLUETOOTH_LE`, and `ANT_PLUS`.
2. *Fresh Installation Pairing Baseline (`REQ-SET-083.2`)*:
• Upon initial installation or database creation, exactly two devices SHALL be paired by default:
  - `SPEED_AND_LOCATION_GPS` (internal GPS)
  - `ALTITUDE_FROM_PRESSURE` (internal barometer, if hardware present)
• `SPEED_AND_LOCATION_GOOGLE_FUSED` and `SPEED_AND_LOCATION_NETWORK` SHALL be initialized as unpaired (`isPaired = false`), reserving the 2/2 baseline slots cleanly.
3. *Unrestricted Core Functionality (`REQ-SET-083.3`)*:
• Workout recording, sensor telemetry display, elevation profiles, interactive maps, and file export (GPX, TCX, FIT) SHALL remain 100% free and unmonetized.

**Acceptance Criteria (Given-When-Then)**:
• *Given* a fresh application install on a phone with GPS and barometer,
• *When* the database is initialized,
• *Then* exactly 2 devices (`GPS` and `ALTITUDE_FROM_PRESSURE`) are paired, and `getPairedDevicesCount()` equals 2.
• *Given* an athlete in the Free tier with 2 paired devices attempting to pair a 3rd device,
• *When* the pairing switch is toggled,
• *Then* pairing SHALL be intercepted and the Swap-or-Upgrade dialog SHALL be triggered.

**Invariants**: Core tracking, telemetry processing, local file exports, and database pairing persistence across app updates MUST NOT be compromised.

### REQ-SET-084: One-Tap Swap-or-Upgrade Dialog on 3rd Paired Device (REQ-MON-002) [Verified]
- **Tests**: `TST-SET-084`, `TST-LOC-001`
- **Priority**: High
- **Type**: Functional
- **Verification Method**: Unit, Integration
- **Implementation**: `SensorQuotaReconciliationManager.kt`, `DeviceListViewModel.kt`, `ControlTrackingViewModel.kt`, `DeviceManager.java`, `ATrainingTrackerApp.kt`, `MainActivityWithNavigation.kt`, `SwapOrUpgradeDialog.kt`, `KeepTwoSensorsBottomSheet.kt`, `DeviceDataRepository.kt`
- **Rationale**: Ensure athletes are never blocked by a hard paywall when adding sensors. When attempting to pair a 3rd device in the base version (*Basisversion*), athletes can easily unpair a currently active device with a single tap or support the ongoing development of the app to unlock unlimited sensors (*Entwicklung unterstützen*). Furthermore, athletes who return from Supporter to the base version with $> 2$ paired sensors are guided through a non-destructive reconciliation flow to keep 2 sensors.
- **Description**:
1. *Quota Interception (`REQ-SET-084.1`)*:
• When a Free tier athlete (`!isProActive()`) attempts to pair a device that would increase total paired devices beyond `MAX_FREE_PAIRED_DEVICES = 2`, `DeviceListViewModel` SHALL intercept the action and display `SwapOrUpgradeDialog` without changing database pairing state.
2. *Smart Recommendation Preselection (`REQ-SET-084.2`)*:
• In `determineRecommendedUnpairDeviceId`, the system SHALL recommend unpairing `ALTITUDE_FROM_PRESSURE` first if paired. Next, if both `SPEED_AND_LOCATION_GOOGLE_FUSED` and `SPEED_AND_LOCATION_GPS` are paired, it SHALL recommend unpairing `SPEED_AND_LOCATION_GPS` (so Google Fused is retained). Otherwise, if non-location sensors are paired alongside a location sensor, the system SHALL recommend unpairing the non-location sensor (prioritizing disconnected sensors first, oldest last-seen timestamp second) and SHALL NEVER recommend `SPEED_AND_LOCATION_GOOGLE_FUSED` or a sole paired Location sensor for unpairing. In `SwapOrUpgradeDialog`, fallback preselection SHALL likewise filter for non-location devices before defaulting to list order.
3. *Atomic Swap Execution (`REQ-SET-084.3`)*:
• Tapping "Swap" SHALL unpair the selected existing device and pair the new candidate device in a single database operation, maintaining total paired devices $\le 2$.
4. *Direct Upgrade Path & Development Support CTA (`REQ-SET-084.4`)*:
• The dialog SHALL offer a prominent "Support Development" action (*"Entwicklung unterstützen"*, `@string/swap_dialog_action_unlock` / `@string/keep_two_sensors_action_unlock`) navigating directly to `SupporterScreen` to support independent app development and unlock unlimited sensors (ATT-5661).
5. *Subscription Downgrade Sensor Quota Reconciliation & Global Triggers (`REQ-SET-084.5`)*:
• When an athlete returns to Free tier (`!isProActive()`) with $> 2$ paired sensors in `Devices.db` while not actively recording (`!isTracking()`), the centralized reconciliation manager (`SensorQuotaReconciliationManager`) SHALL present `KeepTwoSensorsBottomSheet` (`AppModalBottomSheet`) globally across the application (`ATrainingTrackerApp`), not solely within `DeviceListViewModel` / `DevicesTabbedScreen`.
• Reconciliation SHALL be automatically triggered on application start / resume (`MainActivityWithNavigation.onResume`), upon displaying the tracking cockpit (`TrackingTabsScreen` / `SensorGridScreen`), upon reactive transition to Free tier (`!isProActive()`), and prior to dispatching sensor search or workout start in `ControlTrackingViewModel`.
• The sheet SHALL present all currently paired devices, preselect 2 sensible defaults via `SensorQuotaReconciliationManager.determineDefaultKeepDeviceIds`, permit selection of at most 2 active sensors, and offer a 1-tap shortcut to `SupporterScreen`:
  a. *Slot 1 (Location)*: If both `SPEED_AND_LOCATION_GOOGLE_FUSED` and `SPEED_AND_LOCATION_GPS` are paired, preselect `SPEED_AND_LOCATION_GOOGLE_FUSED`. If only one of the two location sensors is paired, preselect that single paired location sensor.
  b. *Slot 2 (Active Remote Sensor)*: Preselect the most recently active remote sensor (`protocol != Protocol.SMARTPHONE`), prioritized by `isConnected == true` first, most recent parsed `lastSeen` timestamp second, and original list order third.
  c. *Fallback*: If $< 2$ sensors are selected (e.g. no remote sensor paired), fall back to `ALTITUDE_FROM_PRESSURE` and remaining paired devices in order.
• Confirming selection (or dismissing the sheet, applying the 2 defaults to prevent quota bypass) SHALL set `PAIRED = 0` on all unselected devices in `Devices.db`. Unselected sensors MUST NOT be deleted; all custom names, calibration factors, wheel circumferences, and equipment links MUST remain 100% intact.
6. *Multi-Sensor Swap Hardening (`REQ-SET-084.6`)*:
• In Free tier, `executeSwapAndPair()` and `DeviceDataRepository` SHALL ensure that even if more than 2 sensors were paired prior to the swap, all excess sensors beyond the newly paired candidate and retained sensor are set to `PAIRED = 0`, guaranteeing total paired count is strictly $\le 2$.
7. *BANALService Free Quota Hardware Connection Clamping (`REQ-SET-084.7`)*:
• In `DeviceManager.startSearchForPairedDevices()` / remote device creation, the system SHALL enforce defense-in-depth hardware connection clamping: on Free tier (`!isProActive()`), connecting remote hardware devices SHALL be clamped to `MAX_FREE_PAIRED_DEVICES = 2` to prevent downgraded athletes from connecting excess sensors even if UI reconciliation has not yet been processed.
8. *Rule 73 Stacked Action Button Ergonomics & Appreciative Wording (`REQ-SET-084.8`)*:
• In `SwapOrUpgradeDialog.kt`, action controls SHALL follow the Rule 73 stacked button layout for `AppModalBottomSheet`: primary action (*'Tauschen & Koppeln'*, `Button`) and secondary CTA (*'Entwicklung unterstützen'*, `FilledTonalButton`) SHALL be laid out as vertically stacked full-width buttons (`Modifier.fillMaxWidth()`, `8.dp` spacing) at the bottom of the sheet content column, leaving only a single full-width dismiss action (*'Abbrechen'*, `TextButton`) in `actions`, eliminating horizontal button crowding and broken word wrapping across all 9 locales (Rule 73, ATT-5470). Furthermore, dialog titles and messages in `SwapOrUpgradeDialog` and `KeepTwoSensorsBottomSheet` SHALL use appreciative terminology (*"In der Basisversion"* / *"In the base version"*, *"2/2 Plätze"* / *"2/2 Slots"*) rather than transactional SaaS paywall wording (*Free-Tier*, *Free-Plätze*, *Pay what you want*), preserving the single string placeholder (`%1$s`) in `swap_dialog_message` across all 9 supported locales (ATT-5661).

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Amends `REQ-SET-084` (`REQ-SET-084.2`, `REQ-SET-084.4`, `REQ-SET-084.8`) targeting `SensorQuotaReconciliationManager.kt`, `SwapOrUpgradeDialog.kt`, `KeepTwoSensorsBottomSheet.kt`, and `strings.xml`.
2. *Historical Origin & Commit Trace*: `ATT-51` (Initial quota & swap dialog), `ATT-5398` (`KeepTwoSensorsBottomSheet`), `ATT-5469` (`29251f2d`, global reconciliation manager), `ATT-5470` (Rule 73 stacked action buttons), refined in `ATT-5490` (Sprint `2026-45.8`), `ATT-5661` (appreciative copy & support CTA), and `ATT-5657` (Sprint `2026-45.9`, sole location sensor unpair protection).
3. *Root Reason for Existing Formulation*: Early copy adopted commercial SaaS subscription phrasing (*"Im Free-Tier"*, *"Free-Plätze"*, *"Pay what you want"*, *"Unbegrenzte Sensoren freischalten"*). Framing sensor expansion as supporting independent app development (*"In der Basisversion"*, *"Entwicklung unterstützen"*) reflects community-oriented voluntary appreciation while retaining clarity on the 2-sensor base limit. In `determineRecommendedUnpairDeviceId`, falling back to `firstOrNull()` inadvertently selected `Google Fused` or the sole location sensor because internal smartphone sensors reside at index 0.
4. *Preservation of Core Invariants*: Base-tier quota ($\le 2$ sensors) is strictly preserved; unselected sensors remain in SQLite with custom names and calibration intact; Rule 73 stacked button layout intact; single `%1$s` placeholder preserved across all 9 locales; Barometer unpairing priority preserved; dual-location preference (unpair GPS over Google Fused) preserved; and sole location sensor is protected from unpairing recommendations.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an athlete in the base version with 2 paired devices attempting to pair a heart rate monitor,
• *When* the toggle is clicked,
• *Then* the Swap-or-Upgrade dialog appears showing currently paired devices and the "Support Development" button (*"Entwicklung unterstützen"*).
• *Given* the Swap dialog with `ALTITUDE_FROM_PRESSURE` preselected,
• *When* the athlete taps "Swap & Pair",
• *Then* `ALTITUDE_FROM_PRESSURE` is unpaired, the heart rate monitor is paired, and total paired devices equals 2.
• *Given* an athlete in the base version with Google Fused (or sole Location sensor) and an external sensor (e.g. cadence, HRM) paired,
• *When* attempting to pair a 3rd sensor,
• *Then* `SwapOrUpgradeDialog` SHALL recommend the external non-location sensor for unpairing and SHALL NEVER recommend Google Fused or the sole Location sensor.
• *Given* an athlete downgraded to Free tier with both Google Fused and GPS paired plus remote sensors,
• *When* `KeepTwoSensorsBottomSheet` is evaluated,
• *Then* Google Fused and the most recently active remote sensor are preselected as the 2 defaults.
• *Given* an athlete downgraded to Free tier with only GPS (or only Google Fused) paired alongside remote sensors,
• *When* `KeepTwoSensorsBottomSheet` is evaluated,
• *Then* that single paired location sensor is preserved alongside the most recently active remote sensor.
• *Given* an athlete downgraded to Free tier with no remote sensors paired,
• *When* `KeepTwoSensorsBottomSheet` is evaluated,
• *Then* `ALTITUDE_FROM_PRESSURE` is preselected as the second default.
• *Given* an athlete downgraded to Free tier with $> 2$ paired sensors and not actively recording,
• *When* the app starts, the tracking screen is displayed, or sensor search / tracking is requested,
• *Then* the Keep-2-Sensors reconciliation bottom sheet is displayed globally with 2 sensible defaults preselected, and hardware connections are clamped to at most 2 paired devices.
• *Given* the reconciliation bottom sheet,
• *When* the athlete confirms selection or dismisses the sheet,
• *Then* all unselected sensors are marked unpaired in `Devices.db` with data intact, and total paired count equals 2.

**Invariants**: No unexpected unpairing without user confirmation; 1-tap swap must execute atomically; unselected sensors MUST NOT be deleted and their calibration/equipment configuration MUST be preserved; quota reconciliation MUST NOT disrupt active tracking.

### REQ-SET-085: Lifetime Pioneer Early Adopter Recognition via Historical Cutoff (REQ-MON-003) [Verified]
- **Tests**: `TST-SET-085`, `TST-LOC-001`
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Implementation**: `WorkoutSummariesDatabaseManager.java`, `SettingsDataStore.kt`, `ProFeatureManager.kt`, `SupporterScreen.kt`, `AppNavigationDrawer.kt`
- **Rationale**: Honor existing loyal athletes who tracked workouts prior to V5.0.0 by granting them permanent, lifetime Pro privileges without payment, and dynamically displaying their authentic joining year.
- **Description**:
1. *Cutoff Timestamp Evaluation (`REQ-SET-085.1`)*:
• The system SHALL define `PIONEER_CUTOFF_TIMESTAMP = "2027-01-31 23:59:59"` (UTC / SQLite standard datetime).
• `WorkoutSummariesDatabaseManager.hasWorkoutBefore(cutoffTimestamp)` SHALL query `WorkoutSummaries` for `COUNT(*) > 0` with `TIME_START <= PIONEER_CUTOFF_TIMESTAMP`.
2. *Permanent Lifetime Flag Persistence (`REQ-SET-085.2`)*:
• If at least one historical workout exists before the cutoff timestamp, `SettingsDataStore` SHALL persist `isPioneerEarlyAdopter = true`. Once true, this flag is permanent and irrevocable.
3. *Dynamic Pioneer Year & Badge (`REQ-SET-085.3`)*:
• Pioneer athletes SHALL have `isProActive() == true` with unlimited device pairing, a lifetime Pioneer badge in navigation drawer and settings, and voluntary access to Supporter tiers.
• The system SHALL dynamically query the athlete's earliest workout year via `WorkoutSummariesDatabaseManager.getEarliestWorkoutYear()` (`SELECT MIN(timeStart)`).
• When an earliest workout year is found, `SettingsDataStore` SHALL cache `pioneerSinceYear` and the system SHALL display `"★ Pioneer (Seit %1$d)"` / `"★ Pioneer (Since %1$d)"` on `SupporterScreen` (`PioneerStatusBanner`), while `AppNavigationDrawer` (`DrawerHeader`) preserves the original clean header layout and displays the Pioneer / Supporter app icon in the original full `46.dp` visual size (`scale = 1.81f`).
• When no historical workout year exists, the badge SHALL cleanly fall back to `"★ Pioneer"`.

**Acceptance Criteria (Given-When-Then)**:
• *Given* a database containing a workout recorded on or before `2027-01-31 23:59:59`,
• *When* Pro status is evaluated,
• *Then* `isPioneerEarlyAdopter` evaluates to `true`, `isProActive()` returns `true`, and unlimited device pairing is enabled.
• *Given* an athlete recognized as Pioneer with an earliest workout recorded in 2018,
• *When* viewing the Navigation Drawer or SupporterScreen banner,
• *Then* `"★ Pioneer (Seit 2018)"` / `"★ Pioneer (Since 2018)"` is displayed dynamically instead of a hardcoded year.
• *Given* a Pioneer athlete with no recorded workouts,
• *When* viewing the Navigation Drawer or SupporterScreen banner,
• *Then* `"★ Pioneer"` is displayed without a year suffix.
• *Given* a new user whose first workout is recorded on `2027-02-01 00:00:00` or later,
• *When* Pro status is evaluated,
• *Then* `isPioneerEarlyAdopter` evaluates to `false` and Free tier limits apply unless an active subscription exists.

**Invariants**: Pioneer status cannot be revoked by app updates or database re-indexes; existing workouts remain unmodified.

### REQ-SET-086: Five-Tier Google Play Supporter Subscriptions (REQ-MON-004) [Verified]
- **Tests**: `TST-SET-086`, `TST-LOC-001`
- **Priority**: High
- **Type**: Functional
- **Verification Method**: Unit, Integration
- **Implementation**: `SupporterScreen.kt`, `SupporterComponents.kt`, `BillingClientLifecycle.kt`, `ProFeatureManager.kt`, `AppNavigationDrawer.kt`
- **Rationale**: Offer transparent, flexible "Pay what you want" subscription tiers capped at max 100 € / year, allowing athletes to fund continuous independent development and feature maintenance without scope liabilities.
- **Description**:
1. *Subscription Product & 5 Base Plans (`REQ-SET-086.1`)*:
• The system SHALL integrate Google Play Billing with product ID `att_supporter_sub` across 5 base plans:
  - `bronze-monthly` (2 € / month) — Bronze Supporter
  - `bronze-yearly` (10 € / year) — Bronze Supporter
  - `silver-yearly` (25 € / year, Recommended) — Silver Supporter
  - `gold-yearly` (50 € / year) — Gold Supporter
  - `diamond-yearly` (100 € / year) — Diamond Sponsor
2. *Retirement of Hardware Voting (`REQ-SET-086.2`)*:
• `SupporterScreen` SHALL NOT display hardware device voting options or make device acquisition promises, focusing communication strictly on independent open-source sustainability.
3. *Navigation & Drawer Presence (`REQ-SET-086.3`)*:
• The Navigation Drawer SHALL display the active Supporter/Pioneer tier badge in `DrawerHeader` and provide a dedicated "Supporter / Pro" navigation destination.
4. *Card Layout & Button Wrap Prevention (`REQ-SET-086.4`)*:
• In `SupporterPlanCard`, the `"Empfehlung"` / `"Recommended"` badge SHALL be positioned vertically above the tier title.
• Action buttons in plan cards SHALL enforce `maxLines = 1, softWrap = false` to guarantee zero single-character or mid-word line wrapping (`Rule 38`).
5. *Appreciative Wording & 9-Locale Localization Parity (`REQ-SET-086.5`)*:
• `supporter_screen_subtitle` SHALL invite athletes to support the continuous development of aTrainingTracker (*"Unterstütze die Weiterentwicklung von aTrainingTracker."* / *"Support the ongoing development of aTrainingTracker."*) without defensive marketing buzzwords (*"15+ Jahre"*, *"Indie"*, *"unabhängiges Fitnesstracking"*) (ATT-5659).
• `supporter_active_tier_thank_you` and `supporter_core_invariant_body` SHALL express appreciation for supporting development plainly and consistently across all 9 supported application locales (ATT-5659).

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Amends `REQ-SET-086` (`REQ-SET-086.5`), targeting `SupporterScreen.kt`, `SupporterComponents.kt`, and `strings.xml`.
2. *Historical Origin & Commit Trace*: `ATT-51` (initial supporter model), `ATT-5371` (percent sign fix), refined in `ATT-5659` (appreciative copy without buzzwords).
3. *Root Reason for Existing Formulation*: Historical marketing copy highlighted developer pedigree and privacy commitments to differentiate from corporate trackers. Direct, simple phrasing (*"Weiterentwicklung unterstützen"*) provides genuine, professional appreciation without defensive jargon.
4. *Preservation of Core Invariants*: 100% free core invariant body intact; 5 Google Play Billing base plans, prices, and IDs remain 100% preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an athlete navigating to the Supporter screen,
• *When* the screen renders,
• *Then* 5 base plan cards are displayed in a clean responsive layout with `silver-yearly` highlighted with an `Empfehlung` badge placed above the title, with action buttons rendered on a single line without wrapping, and without hardware voting sections.
• *Given* a successful subscription purchase of `gold-yearly`,
• *When* purchases are acknowledged,
• *Then* `supporterTier` updates to `GOLD`, `isProActive()` returns `true`, and the Gold Supporter badge is displayed.

**Invariants**: Google Play Billing purchase acknowledgment within 3 days; graceful offline fallback caching of verified purchases; action buttons never wrap text across multiple lines.

### REQ-SET-087: Debug Supporter Tier Override & Pro Feature Manager Architecture (REQ-MON-005) [Verified]
- **Tests**: `TST-SET-087`, `TST-LOC-001`
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Implementation**: `ProFeatureManager.kt`, `DeveloperMenuScreen.kt`, `AppNavigationDrawer.kt`, `NavRoutes.kt`, `ATrainingTrackerApp.kt`, `AdvancedTuningScreen.kt`, `SettingsDataStore.kt`
- **Rationale**: Provide deterministic local and UI testing capabilities without requiring live Play Billing credentials, while maintaining strict release-build security invariants.
- **Description**:
1. *Unified ProFeatureManager (`REQ-SET-087.1`)*:
• `ProFeatureManager` SHALL compute effective Pro status via priority order: `BuildConfig.DEBUG` Tier Override > Lifetime Pioneer Status > Active Google Play Subscription.
• `isProActive()` SHALL return `true` if any tier $\ge$ `BRONZE` or `isPioneerEarlyAdopter == true`.
2. *Debug Developer Menu & Supporter Tier Override (`REQ-SET-087.2`)*:
• The system SHALL provide a dedicated `DeveloperMenuScreen` accessible via `R.id.drawer_developer_menu` in the navigation drawer, strictly displayed and accessible when `BuildConfig.DEBUG == true`.
• `DeveloperMenuScreen` SHALL host the Supporter Tier Override selector (`[NONE, PIONEER, BRONZE, SILVER, GOLD, DIAMOND]`), Jubilee Celebration dialog preview, and N+1 Garage upgrade previews / flag reset.
• `AdvancedTuningScreen` SHALL NOT display developer or supporter tier overrides.
• In release builds (`BuildConfig.DEBUG == false`), the Developer Menu drawer entry, screen route, and override mechanisms SHALL be completely omitted or inert, preventing unauthorized tier escalation.

**Acceptance Criteria (Given-When-Then)**:
• *Given* a debug build of the app,
• *When* the user opens the Navigation Drawer,
• *Then* `Developer Menu` is displayed, and selecting it opens `DeveloperMenuScreen` containing the Supporter Tier Override, Jubilee Celebration preview, and Garage Detector previews.
• *Given* a release build,
• *When* inspected,
• *Then* the Developer Menu drawer entry and debug overrides are completely absent.

**Invariants**: Release builds must never permit arbitrary tier override; ProFeatureManager resolution must be thread-safe.

### REQ-SET-088: Supporter N+1 Garage Detector & Upgrade Prompts (REQ-MON-006) [Verified]
- **Tests**: `TST-SET-088`, `TST-LOC-001`
- **Priority**: High
- **Type**: Functional
- **Verification Method**: Unit, Integration
- **Implementation**: `GarageLeagueDetector.kt`, `GarageDetectorDialog.kt`, `GarageUpgradeNudgeDialog.kt`, `TrainingApplication.java`, `StravaHelper.kt`, `StravaEquipmentSynchronizeThread.java`, `StravaDataPurgeManager.kt`, `EquipmentViewModel.kt`, `DeviceListViewModel.kt`, `SettingsDataStore.kt`, `SupporterDebugSection.kt`
- **Rationale**: Athlete equipment expansion reflects escalating engagement. When an athlete adds bikes, shoes, or pairs additional sensors reaching enthusiast ("Gold") or world-class ("Diamond") scale, the system detects their qualified garage league and presents a humorous, non-blocking "N+1 Cyclist Garage" dialog inviting an upgrade to Gold Pro-Supporter (50 € / year) or Diamond Sponsor (100 € / year) with the matching plan pre-selected, while preventing repeated prompt spam and honoring tier monotonicity. Furthermore, athletes with an active Strava Pro subscription (`summit == true` or `premium == true`) already invest in premium fitness software; detecting this status qualifies them for Gold League (or boosts Gold hardware to Diamond League) with tailored copy acknowledging their Strava Pro commitment (ATT-5570).
- **Description**:
1. *Garage League Qualification Thresholds (`REQ-SET-088.1`)*:
• The system SHALL classify athlete equipment and sensors into two garage leagues:
  - **Diamond League**: $\ge 4$ bikes OR $\ge 8$ remote sensors (paired or registered in `Devices.db`) OR $\ge 2$ power meters (`DeviceType.BIKE_POWER`) OR (Gold hardware qualification + active Strava Pro subscription: `summit == true || premium == true`).
  - **Gold League**: $\ge 3$ bikes OR $\ge 4$ running shoes OR $\ge 5$ remote sensors OR $\ge 1$ power meter (`DeviceType.BIKE_POWER`) OR active Strava Pro subscription (`summit == true || premium == true`).
• Diamond League evaluation SHALL strictly take precedence over Gold League evaluation.
• Strava subscriber status SHALL be ingested via `StravaHelper.storeJSONData` and `StravaEquipmentSynchronizeThread.fillDbFromJsonObject` (`summit || premium`), persisted in SharedPreferences via `TrainingApplication.setStravaSubscriber` (with `cSharedPreferences != null` guards per Rule 75), and unconditionally cleared on disconnect/purge via `TrainingApplication.deleteStravaToken` and `StravaDataPurgeManager.executeLocalDataPurge`.
2. *Tier Monotonicity Gating (`REQ-SET-088.2`)*:
• The system SHALL compare the qualified garage league against the athlete's current effective `SupporterTier`:
  - Diamond League prompt SHALL only trigger if effective tier is `< SupporterTier.DIAMOND`.
  - Gold League prompt SHALL only trigger if effective tier is `< SupporterTier.GOLD`.
• If the current effective tier is already $\ge$ the qualified league tier, no upgrade prompt SHALL be triggered.
3. *Event-Driven Post-Save Evaluation (`REQ-SET-088.3`)*:
• The evaluation SHALL execute asynchronously in the background immediately following:
  - Saving a newly added bike or running shoe in equipment management (`EquipmentViewModel.saveEquipment()`).
  - Pairing an additional sensor or completing device swap-and-pair (`DeviceListViewModel.onPairedChanged()` / `executeSwapAndPair()`).
• Evaluation and dialog triggering MUST NOT block SQLite persistence, UI responsiveness, or sensor connection lifecycles.
4. *Non-Spam Persistence (`REQ-SET-088.4`)*:
• `SettingsDataStore` SHALL persist boolean flags `hasShownGoldGarageNudge` and `hasShownDiamondGarageNudge`.
• The upgrade prompt for each league SHALL be displayed at most once across the lifetime of the application.
• Showing or dismissing the dialog SHALL set the respective league flag to `true`.
5. *Humorous N+1 Dialog & Pre-Selected Navigation (`REQ-SET-088.5`)*:
• When qualified, `GarageDetectorDialog` SHALL render a Material 3 dialog presenting dynamic gear counts (bikes, shoes, sensors, power meters) and good-humored cyclist/runner copy without decorative inline Unicode emojis (relying on the dialog's vector header icon).
• When qualified as an active Strava subscriber (`isStravaSubscriber == true`), `GarageUpgradeNudgeDialog` SHALL present tailored dialog copy referencing their Strava Pro status (`garage_detector_dialog_message_gold_strava`, `garage_detector_dialog_message_diamond_strava`) across all 9 languages without inline Unicode emojis and strictly preserving 5 positional specifiers (`%1$d`..`%5$s`).
• The dialog SHALL offer a 1-tap upgrade action navigating to `SupporterScreen` (`NavRoutes.SUPPORTER`) with the recommended plan pre-selected (`gold-yearly` for Gold League, `diamond-yearly` for Diamond League), and a secondary "Später / Weiter zum Training" dismiss action.
6. *Diagnostic Debug Preview Triggers (`REQ-SET-088.6`)*:
• `SupporterDebugSection` SHALL expose 1-tap test preview buttons for Gold and Diamond N+1 garage dialogs, and a reset button clearing both nudge flags in DataStore, strictly enclosed within `if (BuildConfig.DEBUG)`.
7. *Rule 73 Stacked Action Button Ergonomics (`REQ-SET-088.7`)*:
• In `GarageUpgradeNudgeDialog.kt`, action controls SHALL follow the Rule 73 stacked button layout for `AppModalBottomSheet`: the primary upgrade CTA (*'Upgrade auf Gold Pro / Diamond Sponsor'*, `Button(modifier = Modifier.fillMaxWidth())`) SHALL be placed as a full-width button at the bottom of the sheet content column, leaving only a single full-width dismiss action (*'Später / Weiter zum Training'*, `TextButton(modifier = Modifier.fillMaxWidth())`) in `actions`, eliminating horizontal button crowding and broken 2–3 line word wrapping across all 9 locales (Rule 73, ATT-5468).

**Acceptance Criteria (Given-When-Then)**:
• *Given* an athlete on Free or Silver tier with 2 bikes,
• *When* a 3rd bike is added and saved in Equipment Management,
• *Then* `hasShownGoldGarageNudge` is false, Gold League is detected, and `GarageDetectorDialog` is triggered with the Gold Pro-Supporter upgrade path.
• *Given* an athlete on Gold tier with 3 bikes and 7 sensors,
• *When* an 8th sensor is paired,
• *Then* Diamond League is detected, and `GarageDetectorDialog` is triggered with the Diamond Sponsor upgrade path pre-selected.
• *Given* an athlete on Free or Silver tier with 0 bikes and 0 sensors connected to Strava with an active subscription (`summit == true || premium == true`),
• *When* garage league evaluation runs,
• *Then* Gold League is detected and `GarageUpgradeNudgeDialog` displays `garage_detector_dialog_message_gold_strava`.
• *Given* an athlete with Gold hardware (e.g. 3 bikes) and an active Strava Pro subscription,
• *When* garage league evaluation runs,
• *Then* Diamond League is detected and `GarageUpgradeNudgeDialog` displays `garage_detector_dialog_message_diamond_strava`.
• *Given* an athlete who has already dismissed the Gold League dialog (`hasShownGoldGarageNudge == true`),
• *When* an additional bike is added,
• *Then* the Gold upgrade dialog is NOT re-triggered.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Amends `REQ-SET-088` (`REQ-SET-088.1`, `REQ-SET-088.5`), targeting `TrainingApplication.java`, `StravaHelper.kt`, `StravaEquipmentSynchronizeThread.java`, `StravaDataPurgeManager.kt`, `GarageLeagueDetector.kt`, `GarageUpgradeNudgeDialog.kt`, and `strings.xml` across all 9 locales.
2. *Historical Origin & Commit Trace*: Sprint 2026-45.1 (`ATT-5299`, initial detector), Sprint 2026-45.3 (`ATT-5400`, zero-emoji copy), Sprint 2026-45.6 (`ATT-5468`, Rule 73 stacked buttons), Sprint 2026-45.9 (`ATT-5570`).
3. *Root Reason for Existing Formulation*: In ATT-5299, qualification relied solely on local hardware counts because Strava account subscription telemetry had not yet been ingested into application preferences.
4. *Preservation of Core Invariants*: Rule 75 SharedPreferences null-safety (`cSharedPreferences != null`); complete data purge hygiene (`executeLocalDataPurge` resets subscriber state); tier monotonicity (`< SupporterTier.DIAMOND`, `< SupporterTier.GOLD`); once-in-a-lifetime non-spam persistence (`hasShownGoldGarageNudge`, `hasShownDiamondGarageNudge`); Rule 73 stacked action buttons; zero-emoji localization parity across all 9 languages.

**Invariants**: Equipment and sensor persistence complete before dialog triggering; each league level nudges at most once; tier monotonicity is strictly preserved; release builds compile out debug preview controls.

### REQ-SET-089: Workout Cluster Merge via Similarity-Ranked Target Selection & Confirmation [Verified]
- **Tests**: `TST-SET-089`
- **Priority**: High
- **Type**: Functional
- **Verification Method**: Unit, Integration
- **Implementation**: `WorkoutClusterComponents.kt`, `WorkoutClustersScreen.kt`, `WorkoutClustersViewModel.kt`, `WorkoutClusterEngine.kt`, `WorkoutSummariesDatabaseManager.java`, `RoutesDatabaseManager.kt`
- **Rationale**: When duplicate Workout Clusters (*Lieblingsstrecken*) exist for the same route, deleting one cluster drops historical cluster association (`clusterId = -1L`). The system SHALL provide a long-press context menu action to merge source cluster `A` into target cluster `B`, ranking candidate clusters by spatial similarity score and requiring explicit confirmation before atomically reassigning all workouts and routes, updating `B`'s weighted centroid, and deleting `A` (ATT-5666, ATT-5824):
1. *Context Menu Action (`REQ-SET-089.1`)*: `ClusterItem` context menu SHALL include an action item *"Mit anderer Lieblingsstrecke zusammenführen…"* (`R.string.cluster_menu_merge`, icon `Icons.Default.CallMerge`) triggering `onMerge(cluster)`.
2. *Similarity-Ranked Candidate Selection (`REQ-SET-089.2`)*: Target selection SHALL present candidates via `WorkoutClusterSelectionDialog` where candidates (`id != A.id`) are scored against cluster `A`'s spatial fingerprint via `WorkoutClusterEngine.scoreClusters(...)` and sorted ascending by score (lowest score = highest similarity at index 0).
3. *Explicit Merge Confirmation Dialog & Two-Step Transition Independence (`REQ-SET-089.3`)*: Selecting a candidate cluster in `WorkoutClusterSelectionDialog` SHALL immediately trigger the confirmation `AlertDialog` (`cluster_merge_dialog_title`, `cluster_merge_dialog_message`) on the first selection attempt without requiring a second context menu invocation. The confirmation dialog payload (`pendingMergePair`) SHALL remain decoupled from the selection bottom sheet dismissal lifecycle (Rule 77 / Design Guidelines §5.19), displaying source name `%1$s`, recording count `%2$d`, target name `%3$s`, confirmation action `cluster_merge_action`, and dismiss action `cancel`.
4. *Transactional Database Reassignment & Centroid Synthesis (`REQ-SET-089.4`)*:
• Reassign all workouts with `CLUSTER_ID = A.id` to `B.id` in `WorkoutSummaries.TABLE`.
• Reassign all routes with `COLUMN_CLUSTER_ID = A.id` to `B.id` in `RoutesDatabaseManager`.
• Recompute `B`'s weighted centroid:
  $$\text{coord}_{\text{new}} = \frac{\text{coord}_B \cdot N_B + \text{coord}_A \cdot N_A}{N_B + N_A}$$
  with new hit count $N_B + N_A$, update spatial bounding box, merge polyline preview paths, and update probable sport type.
• Delete source cluster `A` from `WorkoutClusterDatabaseManager` and refresh `WorkoutClusterRepository` state flows.
5. *9-Language Parity & Zero Emojis (`REQ-SET-089.5`)*: All new user-facing strings SHALL maintain 100% parity across all 9 supported locales (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`) with zero decorative inline emojis (`ATT-5400`).

**Acceptance Criteria (Given-When-Then)**:
• *Given* two similar Workout Clusters `A` and `B` in the clusters list,
• *When* the athlete long-presses cluster `A` and selects *"Mit anderer Lieblingsstrecke zusammenführen…"*,
• *Then* `WorkoutClusterSelectionDialog` opens displaying candidate clusters with `B` ranked at the top.
• *Given* the athlete selects candidate cluster `B` in `WorkoutClusterSelectionDialog`,
• *When* the candidate row is tapped on the first attempt,
• *Then* `WorkoutClusterSelectionDialog` dismisses and the merge confirmation dialog appears immediately with source `A` and target `B` without requiring a second context menu interaction.
• *Given* the merge confirmation dialog with candidate `B`,
• *When* the athlete taps *"Zusammenführen"*,
• *Then* all workouts and routes of `A` are reassigned to `B`, `B`'s hit count and centroid reflect the combined data, cluster `A` is removed from the database and UI, `WorkoutClusterRepository` emits the updated state, and `pendingMergePair` is cleared.
• *Given* all 9 locale resource files,
• *When* `TranslationParityTest` executes,
• *Then* all merge string keys exist, match format specifiers (`%1$s`, `%2$d`, `%3$s`), and contain zero inline emojis.

### Requirement Archaeology & Chesterton's Fence Audit
1. *Original Requirement ID & Target*: Amends `REQ-SET-089` (`REQ-SET-089.3`), targeting `WorkoutClustersScreen.kt` and `WorkoutClusterComponents.kt`.
2. *Historical Origin & Commit Trace*: Sprint 2026-45.10 (`ATT-5666`, commit `76a403d3`).
3. *Root Reason for Existing Formulation*: ATT-5666 modeled the transition using two separate mutable state variables (`clusterToMerge` and `mergeTargetCluster`), incorrectly assuming `onSelect` would transition states while `onDismiss` would execute only on explicit sheet dismissal. Because `WorkoutClusterSelectionDialog` calls `onSelect(cluster)` followed immediately by `onDismiss()`, `clusterToMerge` was cleared upon selection, suppressing the confirmation dialog until a second context menu tap restored `clusterToMerge`.
4. *Preservation of Core Invariants*: Decoupling selection sheet dismissal from confirmation dialog state via `pendingMergePair` ensures the confirmation dialog appears reliably on the first attempt; sheet dismissal without selection (`onDismiss` without `onSelect`) cleanly closes without triggering confirmation; backend transactional merge, centroid calculations, and 9-language localization remain 100% intact.

**Invariants**: Workouts not belonging to `A` or `B` MUST NOT be altered; SQLite transactional integrity; no duplicate routes; zero hardcoded strings.

### REQ-SET-095: Known Locations Merge with Hybrid Geofence Geometry & Cross-Database Reconciliation [Verified]
- **Tests**: `TST-SET-095`
- **Priority**: High
- **Type**: Functional
- **Verification Method**: Unit, Integration
- **Implementation**: `KnownLocationsScreen.kt`, `KnownLocationsViewModel.kt`, `KnownLocationsRepository.kt`, `KnownLocationsDatabaseManager.java`, `KnownLocationsGeometryMath.kt`
- **Rationale**: When GPS fix acquisition varies across workout sessions, multiple duplicate `KnownLocation` (*Lieblingsorte*) entries are created for semantically the same physical location. Simply deleting a duplicate location drops its geofence coverage, causing workouts and route clusters started there to lose their location association. The system SHALL provide a directional merge action ($S \rightarrow T$) from the location card context menu, filtering candidate targets within a proximity threshold ($d \le 2\,000\text{ m}$), calculating an enclosing hybrid geofence radius preview, preserving `isHome` and locked altitudes, atomically updating SQLite storage, reconciling authoritative workout start counts without double-counting, and refreshing linked route clusters:
1. *Context Menu Action (`REQ-SET-095.1`)*: Each `KnownLocationCard` context menu SHALL include an action item *"Mit anderem Ort zusammenführen…"* (`R.string.known_locations_menu_merge`, icon `Icons.Default.CallMerge`) triggering candidate target selection for source location $S$.
2. *Proximity Candidate Selection Dialog (`REQ-SET-095.2`)*: Target selection dialog SHALL present candidates ($T \ne S$) filtered strictly by geodetic center distance $d = \text{distanceBetween}(C_S, C_T) \le 2\,000\text{ m}$, sorted ascending by raw numeric distance primitive (`ATT-3324`), displaying the formatted distance label (`R.string.known_locations_merge_distance_away`). If no candidate locations exist within $2\,000\text{ m}$, a descriptive empty notice (`R.string.known_locations_merge_no_candidates`) SHALL be displayed.
3. *Explicit Merge Confirmation Dialog & Radius Preview (`REQ-SET-095.3`)*: Selecting target $T$ SHALL display an explicit confirmation dialog (`ATT-3355`, `R.string.known_locations_merge_dialog_title`, `R.string.known_locations_merge_dialog_message`) displaying $S.\text{name}$, $T.\text{name}$, previewing the calculated hybrid geofence radius $r_{\text{new}}$, and displaying a pinned-target notice (`R.string.known_locations_merge_pinned_notice`) whenever $T$ has `isHome == true` or `isLocked == true`.
4. *Hybrid Geofence Geometry Calculation (`REQ-SET-095.4`)*:
   • *Pinned-Target Mode (when $T.\text{isHome} == \text{true}$ or $T.\text{isLocked} == \text{true}$)*: The target coordinate remains fixed ($C_{\text{new}} = C_T$) and the radius expands to fully enclose the source geofence:
     $$r_{\text{new}} = \max\left(r_T,\; \lceil d + r_S \rceil\right)$$
   • *Minimum Bounding Circle Mode (default when $T$ is neither home nor locked)*: If one circle already encloses the other ($d + r_S \le r_T$ or $d + r_T \le r_S$), the enclosing circle is adopted; otherwise, the center shifts along the geodesic from $C_T$ toward $C_S$ by $\frac{d + r_S - r_T}{2}$ and the radius is computed as:
     $$r_{\text{new}} = \left\lceil \frac{d + r_T + r_S}{2} \right\rceil$$
5. *Transactional Database Reassignment & State Reconciliation (`REQ-SET-095.5`)*:
   • Inside an atomic SQLite transaction in `StartLocation2Altitude.db`:
     - Update $T$ with new center $C_{\text{new}}$, radius $r_{\text{new}}$, consolidated `isHome = T.isHome || S.isHome`, and altitude: if $T.\text{isLocked}$, retain $T$'s altitude; else if $S.\text{isLocked}$, adopt $S$'s altitude and lock status; otherwise retain $T$'s altitude.
     - Delete source location $S$ (`deleteId(S.id)`).
   • Authoritatively recompute $T.\text{hitCount}$ via `reconcileHitCountsWithWorkoutSummaries` against all workout start locations falling within $(C_{\text{new}}, r_{\text{new}})$ without double-counting.
   • Refresh `KnownLocationsRepository.loadLocations()` so active StateFlows, workout headers, and route clusters immediately reflect target $T$.
6. *9-Language Parity & Zero Emojis (`REQ-SET-095.6`)*: All new user-facing strings SHALL maintain 100% parity across all 9 supported locales (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`) with zero decorative inline emojis (`ATT-5400`).

**Acceptance Criteria (Given-When-Then)**:
• *Given* two unlocked, non-home locations $S$ and $T$ with $r_S = 200\text{ m}$, $r_T = 200\text{ m}$ at distance $d = 200\text{ m}$,
• *When* the user merges $S$ into $T$,
• *Then* $T$'s center shifts to the midpoint between $S$ and $T$, $T$'s radius becomes $300\text{ m}$, and $S$ is deleted.
• *Given* target location $T$ has `isHome = true` (or `isLocked = true`) with $r_T = 200\text{ m}$ and source $S$ ($r_S = 200\text{ m}$) is at distance $d = 150\text{ m}$,
• *When* $S$ is merged into $T$,
• *Then* $T$'s coordinate remains unchanged, $T$'s radius expands to $350\text{ m}$, and `isHome = true` is preserved.
• *Given* workouts and route clusters started within $S$ and $T$,
• *When* $S$ is merged into $T$,
• *Then* `target.hitCount` is authoritatively reconciled against `WorkoutSummaries`, and all affected workouts and `Lieblingsstrecken` immediately resolve to $T$.
• *Given* the user initiates a merge for location $S$,
• *When* candidate selection and confirmation dialogs are presented,
• *Then* only candidates within $2\,000\text{ m}$ are displayed (sorted ascending by raw distance) and the confirmation dialog previews the resulting radius $r_{\text{new}}$ before committing.

**Invariants**: Single-home exclusivity (`isHome`); user-locked altitude immutability; authoritative hit counts derived from actual workouts; strict raw numeric sorting (`ATT-3324`); explicit user confirmation (`ATT-3355`); thread confinement to `KnownLocationsDB-Thread`.

### REQ-SET-096: Workout Cluster Split via Polyline Sub-Corridor Detection & Interactive Map Preview [Verified]
- **Tests**: `TST-SET-096`
- **Priority**: High
- **Type**: Functional
- **Verification Method**: Unit, Integration
- **Implementation**: `WorkoutClusterHeatmapScreen.kt`, `WorkoutClusterSplitSheet.kt`, `WorkoutClustersViewModel.kt`, `WorkoutClusterEngine.kt`, `WorkoutClusterCorridorDetector.kt`, `WorkoutSummariesDatabaseManager.java`, `WorkoutClusterDatabaseManager.kt`
- **Rationale**: Large historical route clusters aggregate multiple distinct route variants sharing endpoints and total distance. Blending these variants distorts performance metrics (`Bestzeit`, `Ø Zeit`, `Ø Geschwindigkeit`) and trend analysis. The system SHALL provide an interactive "Split Cluster" (`Cluster aufteilen`) action with polyline sub-corridor clustering, color-coded preview map, adjustable sensitivity slider (`250 m – 1500 m`), editable variant names, workout renaming option, and atomic database split with centroid and statistics reconciliation (ATT-5900):
1. *Split Action Entry Point (`REQ-SET-096.1`)*: When viewing a cluster in `WorkoutClusterHeatmapScreen` with `hitCount >= 2`, the top header action row SHALL provide a `Split Cluster` button (`R.string.cluster_split_action`, `Icons.Default.CallSplit`, `contentDescription = R.string.cluster_split_action`).
2. *Polyline Sub-Corridor Detection Algorithm (`REQ-SET-096.2`)*:
   - For all member workouts having valid `mapPolyline`, decode track coordinates and sample each polyline at fractional distance stations $s \in \{0.15, 0.30, 0.50, 0.70, 0.85\}$ of total distance.
   - For any pair of polylines $P_i, P_j$, compute corridor deviation as the maximum geodesic station distance:
     $$D(P_i, P_j) = \max_{k=1..5} \text{distanceBetween}(P_i(s_k), P_j(s_k))$$
   - Partition workouts into sub-corridor clusters using greedy medoid clustering against a corridor width threshold $W \in [250\text{ m}, 1500\text{ m}]$ (default $W = 500\text{ m}$).
   - The largest partition SHALL be designated Group 1 (Primary Cluster), and all other partitions sorted descending by member count as candidate split variants (Groups $2..N$).
3. *Interactive Preview Sheet (`REQ-SET-096.3`)*:
   - Tapping `Split Cluster` SHALL open `WorkoutClusterSplitSheet` (`AppModalBottomSheet`).
   - The map behind the sheet SHALL render each detected corridor's member polylines in distinct palette colors (`TTColor.VariantPalette` / high-contrast distinct hues) matching the badge colors in the variant list.
   - A Material 3 `Slider` SHALL allow live adjustment of corridor width threshold $W$ from $250\text{ m}$ to $1500\text{ m}$ in $50\text{ m}$ steps, dynamically recalculating the detected variant groups and updating map colors without blocking the UI thread.
   - If only 1 group is detected at the current threshold, an informative notice (`R.string.cluster_split_no_variants`) SHALL be displayed and the split confirmation button disabled.
4. *Variant List & Customization (`REQ-SET-096.4`)*:
   - Group 1 SHALL be displayed as an unmodifiable primary row indicating it remains under the existing cluster name.
   - Each secondary variant (Groups $2..N$) SHALL display a checkbox (checked by default), a color indicator pill matching its map polyline color, workout count, average distance, and an editable `OutlinedTextField` for cluster name pre-filled with `findUniqueClusterName(baseName)`.
   - A checkbox option *"Workouts passend zur neuen Lieblingsstrecke umbenennen"* (`R.string.cluster_split_rename_workouts`, default `true`) SHALL allow athletes to automatically rename member workouts to match the new cluster name upon split.
5. *Atomic Database Split & Reconciliation (`REQ-SET-096.5`)*:
   - Upon confirming the split (`R.string.cluster_split_action_confirm`), `WorkoutClusterEngine.splitCluster(...)` SHALL execute inside an atomic SQLite transaction:
     - For each selected variant group: insert a new `WorkoutCluster` with the customized name, probable sport ID, and weighted centroid (start, end, apex, distance, altitude extrema, bounds) calculated from its member workouts.
     - Reassign corresponding workout rows in `WorkoutSummaries` to the new cluster ID (and update workout names if renaming is checked).
     - Recalculate remaining weighted centroid, bounds, hit count, and preview paths for the primary cluster.
     - Recalculate `WorkoutClusterStats` for all affected clusters.
     - Refresh `WorkoutClusterRepository` state flows and UI map state, selecting the primary cluster without leaving broken or phantom tracks.
6. *9-Language Parity & Zero Emojis (`REQ-SET-096.6`)*: All new user-facing strings SHALL maintain 100% parity across all 9 supported locales (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`) with zero decorative inline emojis (`ATT-5400`).

**Acceptance Criteria (Given-When-Then)**:
• *Given* a workout cluster with `hitCount >= 2` containing distinct route variants,
• *When* the user taps `Split Cluster` in `WorkoutClusterHeatmapScreen`,
• *Then* `WorkoutClusterSplitSheet` opens, displays a sensitivity slider defaulting to $500\text{ m}$, and colors variant polylines on the map according to their detected group.
• *Given* detected variants in `WorkoutClusterSplitSheet`,
• *When* the user drags the corridor slider from $500\text{ m}$ to $300\text{ m}$,
• *Then* sub-corridors are recalculated dynamically, splitting or merging variant groups and updating map colors immediately without blocking the UI.
• *Given* secondary variant groups in the split sheet,
• *When* the user edits a variant cluster name and leaves *"Workouts passend zur neuen Lieblingsstrecke umbenennen"* checked,
• *Then* the edited name is validated as non-blank and unique, and member workouts will be renamed upon split.
• *Given* the user confirms the split by tapping *"Aufteilen"*,
• *When* the atomic transaction completes,
• *Then* new `WorkoutCluster` records are inserted, member workouts in `WorkoutSummaries` are reassigned to their respective new clusters, original and new cluster centroids and `WorkoutClusterStats` are recalculated, and the map refreshes without phantom tracks.
• *Given* all 9 locale resource files,
• *When* `TranslationParityTest` executes,
• *Then* all split string keys exist, match format specifiers (`%1$s`, `%1$d`, `%2$s`, `%3$s`), and contain zero inline emojis.

**Invariants**: Workouts not belonging to the cluster MUST NOT be affected; primary cluster retains unselected variants; SQLite transactional atomicity; single-source-of-truth hit count reconciliation; 9-language localization parity.

### REQ-SET-097: Point-to-Point (A->B) Route Corridor Discrimination & Bipolar Product Apex [Verified]
- **Tests**: `TST-SET-097`
- **Priority**: High
- **Type**: Functional
- **Verification Method**: Unit, Integration
- **Implementation**: `WorkoutClusterEngine.kt`, `TrackerService.java`, `WorkoutClusterCorridorDetector.kt`, `WorkoutRepository.kt`, `WorkoutClustersViewModel.kt`, `WorkoutClusterHeatmapScreen.kt`, `MapScreenWithTrack.kt`
- **Rationale**: On point-to-point (A -> B) routes (e.g. Sindelfingen -> Schönaich commutes), evaluating the spatial apex solely as the point of maximum distance from `start` causes the apex to collapse onto `end` ($apex \approx end$). Furthermore, the elliptical focal sum $d(\text{start}, P) + d(P, \text{end})$ mathematically favors longitudinal GPS overshoots near endpoints ($2\delta \gg 2h^2/D$) over lateral mid-corridor curves. The system SHALL maximize the bipolar distance product $d(\text{start}, P) \times d(P, \text{end})$ to eliminate endpoint degeneracy and evaluate mid-corridor polyline deviation for candidate cluster scoring:
1. *Non-Degenerate Bipolar Product Apex Calculation (`REQ-SET-097.1`)*:
   - For all point-to-point routes where $d(\text{start}, \text{end}) \ge 150\text{ m}$, `WorkoutClusterEngine.findApexFromPoints(start, points, end)` SHALL evaluate and maximize the bipolar distance product $P(p) = d(\text{start}, p) \times d(p, \text{end})$ across all track coordinates $p$.
   - Because $P(p) = 0$ at both endpoints ($p = \text{start}$ and $p = \text{end}$), endpoint longitudinal overshoots are mathematically prevented from selection.
   - On straight collinear routes, the bipolar product naturally peaks at the track midpoint ($D/2$), and on curved detours it peaks at the apex of greatest lateral deviation, anchoring `maxDispLat` and `maxDispLng` along the mid-corridor without secondary distance thresholds or collinear fallback passes.
2. *Live Tracking Session Synchronization (`REQ-SET-097.2`)*:
   - Upon live session completion, `TrackerService.java` SHALL pass `endPos` into `findApexFromPoints(startPos, points, endPos)` so that newly recorded point-to-point workouts persist the non-degenerate mid-corridor apex in `WorkoutSummaries.MAX_LINE_DISTANCE_LAT` and `LNG`.
3. *Corridor-Aware Candidate Scoring & Disambiguation (`REQ-SET-097.3`)*:
   - When candidate clusters share matching start and end locations ($s_1 \le 0.5$ and $s_2 \le 0.5$), and member preview polylines are available, `WorkoutClusterEngine.scoreClusters()` SHALL evaluate sampled corridor deviation against candidate cluster `previewPaths` using `WorkoutClusterCorridorDetector.samplePolylineStations()` ($s \in \{0.15, 0.30, 0.50, 0.70, 0.85\}$).
   - Candidate clusters following the matching route corridor SHALL receive minimal corridor deviation penalty, reliably discriminating parallel variants (e.g. split clusters) between the same origin and destination.
4. *Loop-Route Backward Compatibility (`REQ-SET-097.4`)*:
   - For all closed-loop courses where $d(\text{start}, \text{end}) < 150\text{ m}$, the system SHALL evaluate displacement from `start` alone, preserving legacy apex coordinates, similarity calculations, and cluster matching behavior 1:1.
5. *Differentiated Map Marker Labeling for Point-to-Point Routes (`REQ-SET-097.5` / ATT-7315)*:
   - For all point-to-point routes where $d(\text{start}, \text{end}) \ge 150\text{ m}$ (or `isPointToPoint == true`), the map marker title for the apex coordinate (`ClusterMarkerType.DISTANCE` / `ic_distance`) SHALL be labeled `R.string.route_apex` ("Scheitelpunkt" in German, "Apex" across all 9 localized languages).
   - For closed-loop routes where $d(\text{start}, \text{end}) < 150\text{ m}$, the marker title SHALL retain `R.string.max_line_distance` ("weiteste Entfernung" in German, "furthest away" in English) representing the physical turnaround point furthest from the origin.
   - This dynamic differentiation SHALL apply across `WorkoutRepository.getMapMarkerList`, `WorkoutClustersViewModel`, `WorkoutClusterHeatmapScreen`, and `MapScreenWithTrack`.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: `REQ-SET-097` targeting `WorkoutClusterEngine.kt`, `WorkoutRepository.kt`, `WorkoutClustersViewModel.kt`, `WorkoutClusterHeatmapScreen.kt`, and `MapScreenWithTrack.kt`.
2. *Historical Origin & Commit Trace*: Commit `23f048e4` (ATT-6686: Use bipolar product in `findApexFromPoints`) and ATT-5899.
3. *Root Reason for Existing Formulation*: The apex marker title was historically hardcoded to `R.string.max_line_distance` ("weiteste Entfernung" / "furthest away") assuming loop routes. In ATT-6686, the bipolar product optimization shifted the apex coordinate on point-to-point routes to the midpoint / detour peak, making "weiteste Entfernung" factually incorrect since destination is furthest away.
4. *Preservation of Core Invariants*: Closed loops ($d(\text{start}, \text{end}) < 150\text{ m}$) remain labeled "weiteste Entfernung" / "furthest away" 100% preserving historical behavior. Underlying telemetry database tables and sensor types are strictly unchanged.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an A -> B point-to-point route with $d(\text{start}, \text{end}) \ge 150\text{ m}$ and a lateral corridor deflection,
• *When* `findApexFromPoints(start, points, end)` is invoked,
• *Then* the selected apex coordinate maximizes $d(\text{start}, P) \times d(P, \text{end})$ along the mid-corridor and does NOT collapse onto `end` or start/end overshoots.
• *Given* a substantially straight A -> B route,
• *When* `findApexFromPoints(start, points, end)` is invoked,
• *Then* the selected apex coordinate peaks at the midpoint of the track ($D/2$).
• *Given* two parallel candidate clusters between Sindelfingen and Schönaich separated laterally by > 500 m,
• *When* a new workout along the eastern forest corridor is scored,
• *Then* `WorkoutClusterEngine.scoreClusters()` evaluates polyline corridor deviation and ranks the eastern corridor cluster first.
• *Given* a closed-loop workout where $d(\text{start}, \text{end}) < 150\text{ m}$,
• *When* `findApexFromPoints(start, points, end)` is invoked,
• *Then* the apex coordinate is identical to legacy $\max d(\text{start}, P)$ calculation.
• *Given* an A -> B point-to-point route with $d(\text{start}, \text{end}) \ge 150\text{ m}$,
• *When* the map markers are generated or rendered on the map,
• *Then* the apex marker title SHALL be `route_apex` ("Scheitelpunkt" in German, "Apex" across all 9 localized languages).
• *Given* a closed-loop route with $d(\text{start}, \text{end}) < 150\text{ m}$,
• *When* the map markers are generated or rendered on the map,
• *Then* the apex marker title SHALL be `max_line_distance` ("weiteste Entfernung" / "furthest away").

**Invariants**: Closed-loop apex and similarity calculations MUST remain identical; thread safety under `clusterLock`; zero regressions on existing cluster test suites.

### REQ-SET-098: Equipment Lookup Normalization, Strava Import Integrity & Automatic Deduplication Reconciliation [Verified]
- **Tests**: `TST-SET-098`
- **Priority**: High
- **Type**: Functional
- **Verification Method**: Unit, Integration
- **Implementation**: `EquipmentDbHelper.java`, `StravaBulkExportImporter.kt`, `StravaEquipmentSynchronizeThread.java`, `WorkoutSummariesDatabaseManager.java`, `SportTypeEquipmentLinkManager.kt`
- **Rationale**: Discrepancies in casing, trailing whitespace, and Strava ZIP import gear name pollution cause duplicate equipment records in `Equipment.db` (e.g. `'Endurance'`, `'endurance '`, `'endurance'`), fracturing workout odometer tracking and corrupting gear linkages. The system SHALL normalize gear lookups, eliminate StravaId pollution during bulk import, and automatically reconcile existing duplicate records:
1. *Case-Insensitive & Whitespace-Trimmed Lookup (`REQ-SET-098.1`)*:
   - `EquipmentDbHelper.getEquipmentId(db, equipmentName)` and `addOrUpdateStravaGear(...)` SHALL evaluate equipment matching using trimmed, case-insensitive comparison across both `Name` and `StravaName`: `TRIM(Name) = TRIM(?) COLLATE NOCASE OR TRIM(StravaName) = TRIM(?) COLLATE NOCASE`.
2. *User-Customized Name Preservation (`REQ-SET-098.2`)*:
   - When synchronizing gear from Strava API or updating existing equipment, the system SHALL preserve existing user-defined `NAME` casing and formatting, updating only `STRAVA_ID`, `STRAVA_NAME`, `FRAME_TYPE`, and `RETIRED`. `NAME` SHALL NOT be overwritten by remote casing or whitespace variants.
3. *StravaId Pollution Prevention in Bulk Archive Ingestion (`REQ-SET-098.3`)*:
   - `StravaBulkExportImporter` SHALL NOT persist plaintext gear names into `StravaId`. `StravaId` MUST remain `null` (or empty) for items created from `bikes.csv`, `shoes.csv`, or activity gear names until populated by an authentic Strava API synchronization with a valid identifier (`b...` / `g...`).
4. *Automatic Deduplication & Cascade Reconciliation Migration (`REQ-SET-098.4`)*:
   - `EquipmentDbHelper` SHALL perform an automatic deduplication migration on existing databases (`DB_VERSION` upgrade 2 -> 3) and provide an idempotent reconciliation mechanism.
   - For all equipment sharing identical `SportType` and matching normalized name (`TRIM(Name) COLLATE NOCASE` or `TRIM(StravaName) COLLATE NOCASE` or `StravaId == Name`):
     - The canonical record SHALL retain the user-preferred capitalized `Name`, authentic `StravaId` (e.g. `b...`/`g...`), non-retired status if any variant is active, and frame type.
     - All hardware sensor pairings (`Links.EQUIPMENT_ID`) and sport type associations (`SportTypeEquipmentLink.equipment_id`) pointing to duplicate IDs SHALL be reassigned to the canonical ID, discarding duplicate link pairs.
     - All historical workout records in `WorkoutSummaries.db` (`WorkoutSummaries.equipmentId`) referencing duplicate IDs SHALL be atomically reassigned to the canonical ID.
     - Orphaned duplicate equipment rows SHALL be removed from `Equipment.db`.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an existing local bike `'Endurance'` (`_id=13`) with no Strava ID,
• *When* Strava API synchronization processes a bike named `'endurance '` with Strava ID `'b16080279'`,
• *Then* `_id=13` is matched, `StravaId` is set to `'b16080279'`, `StravaName` is set to `'endurance '`, the local `Name` remains `'Endurance'`, and no duplicate row is created.
• *Given* a Strava ZIP export with `bikes.csv` or `shoes.csv`,
• *When* `StravaBulkExportImporter` ingests gear,
• *Then* `StravaId` remains `null`, `StravaName` is set to the trimmed gear name, and no plaintext names are written into `StravaId`.
• *Given* an existing database containing duplicate equipment records for `'Endurance'` (`_id=13`, `StravaId=null`), `'endurance '` (`_id=20`, `StravaId='b16080279'`), and `'endurance'` (`_id=22`, `StravaId='endurance'`), with workouts referencing all three IDs,
• *When* the deduplication migration executes,
• *Then* rows merge into `_id=13` with `Name='Endurance'` and `StravaId='b16080279'`, all `WorkoutSummaries.equipmentId` references to IDs 20 and 22 are updated to 13, and duplicate rows 20 and 22 are deleted.

**Invariants**: Zero historical workout data loss; total workout odometer and summary stats remain consistent; sensor links and sport type mappings preserved without orphans; all DB operations execute within atomic SQLite transactions.

### REQ-SET-099: Paired BLE Peripheral Scan-Assisted Connection & Public/Random MAC Address Classification [Verified]
- **Tests**: `TST-SET-099`
- **Priority**: High
- **Type**: Functional
- **Verification Method**: Unit, Integration
- **Implementation**: `MyBTLEDevice.java`, `MyBTLEDeviceAddressAndSearchTest.kt`
- **Rationale**: Per Bluetooth Core Specification Vol 6 Part B §1.3 & §2.3, BLE address type (`Public` vs `Random`) is transmitted out-of-band in the 1-bit `TxAdd` link layer header flag rather than encoded in the MAC address string. The bitwise check `(firstOctet & 0xC0) == 0xC0` misclassifies 25% of IEEE Public MAC-48 addresses (OUI `0xC0..0xFC`, e.g. Intel `D0:C6:37`) as `ADDRESS_TYPE_RANDOM`, which causes peripherals advertising as Public to ignore connection requests on API 33+. Furthermore, speculative direct `connectGatt` populated `mBluetoothGatt` before scan assistance discovered the device, blocking `onScanResult` from utilizing the hardware-resolved `BluetoothDevice` handle. The system SHALL prioritize scan-assisted connection resolution for paired BLE peripherals and prevent conflicting unverified connections:
1. *Authoritative Scan-Resolved GATT Connection (`REQ-SET-099.1`)*:
   - When `MyBTLEDevice.startSearching()` initiates search, if `startScanAssistance(btAdapter)` successfully starts scanning (`mIsScanning == true`), the system SHALL defer direct `connectGatt` and allow `onScanResult()` to establish connection via `result.getDevice().connectGatt(mContext, false, mGattCallback, BluetoothDevice.TRANSPORT_LE)` using the hardware-verified `BluetoothDevice` handle.
   - If scan assistance cannot be started (e.g. missing `BLUETOOTH_SCAN` permission), the system SHALL fall back to direct `connectGatt(...)`.
2. *Scan Discovery GATT Handoff & Stale Guard (`REQ-SET-099.2`)*:
   - When `mScanCallback.onScanResult()` discovers a `ScanResult` matching `mAddress` while `mState == State.SEARCHING`, the system SHALL connect using `result.getDevice().connectGatt(...)`. If a speculative or fallback `mBluetoothGatt` handle already exists, it SHALL be cleanly closed prior to connecting the new handle.
   - `mGattCallback.onConnectionStateChange()` SHALL verify instance identity (`if (gatt != mBluetoothGatt) return;`) to ensure closing a superseded speculative GATT instance cannot clobber the active connection or trigger premature disconnection teardown.
3. *Public and Random Address Connection Interoperability (`REQ-SET-099.3`)*:
   - The connection pipeline SHALL establish reliable GATT connection for both Public IEEE MAC addresses with OUI in `0xC0..0xFC` (e.g. `D0:C6:37:33:67:AA`) and Random Static MAC addresses (e.g. `C6:CC:E3:F5:F0:37`, `E8:4D:74:07:F4:DD`).

**Acceptance Criteria (Given-When-Then)**:
• *Given* an unbonded paired BLE peripheral advertising with a Public IEEE MAC address whose first octet is in `0xC0..0xFC` (e.g. `D0:C6:37:33:67:AA`),
• *When* `startSearching()` is invoked with granted scan permissions,
• *Then* scan assistance discovers the advertisement and connects using `result.getDevice().connectGatt(...)` without timing out on a misclassified `[random]` filter.
• *Given* an unbonded paired BLE peripheral with a Random Static MAC address (e.g. `C6:CC:E3:F5:F0:37`),
• *When* `startSearching()` is invoked with granted scan permissions,
• *Then* scan assistance discovers the advertisement and establishes connection via `result.getDevice().connectGatt(...)`.
• *Given* an active search where a speculative `mBluetoothGatt` is open when `onScanResult()` matches `mAddress`,
• *When* `onScanResult()` executes,
• *Then* the speculative GATT instance is cleanly closed and replaced by `result.getDevice().connectGatt(...)`, and disconnection callbacks from the superseded instance are ignored.

**Invariants**: Sensor data streaming, configurable search timeout, and scan lifecycle teardown (disconnect, search timeout, and shutdown) remain strictly preserved; zero regression in BLE reconnection backoff.

### REQ-SET-100: Android Health Connect Client Integration, SDK Status & Permissions [Verified]
- **Tests**: `TST-SET-100`
- **Priority**: Critical
- **Type**: Functional
- **Verification Method**: Unit, Integration
- **Implementation**: `HealthConnectManager.kt`, `AndroidManifest.xml`, `app/build.gradle`
- **Status**: Specified
- **Rationale**: To enable interoperability with external health platforms following Google Fit deprecation, the system SHALL integrate Android Health Connect (`androidx.health.connect:connect-client:1.1.0`) as a first-class health data provider:
1. *SDK Status Lifecycle (`REQ-SET-100.1`)*:
   - The system SHALL query `HealthConnectClient.getSdkStatus(context)` before initiating any Health Connect operations.
   - If `SDK_AVAILABLE`, Health Connect functionality SHALL be fully active.
   - If `SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED`, the system SHALL prompt the user to update the Health Connect provider via Play Store intent.
   - If `SDK_UNAVAILABLE`, Health Connect UI options SHALL indicate unavailability gracefully without crashes.
2. *Runtime Health Permissions & Android 14+ Manifest Aliases (`REQ-SET-100.2`)*:
   - The system SHALL declare write permissions in `AndroidManifest.xml` (`WRITE_EXERCISE`, `WRITE_EXERCISE_ROUTE`, `WRITE_HEART_RATE`, `WRITE_POWER`, `WRITE_SPEED`, `WRITE_DISTANCE`, `WRITE_ELEVATION_GAINED`, `WRITE_TOTAL_CALORIES_BURNED`, `WRITE_STEPS_CADENCE`, `WRITE_CYCLING_PEDALING_CADENCE`) and include them in `HealthConnectManager.REQUIRED_PERMISSIONS`.
   - The system SHALL declare an `<activity-alias>` for `android.intent.action.VIEW_PERMISSION_USAGE` with category `android.intent.category.HEALTH_PERMISSIONS` and permission `android.permission.START_VIEW_PERMISSION_USAGE` targeting `MainActivityWithNavigation`, alongside `androidx.health.ACTION_SHOW_PERMISSIONS_RATIONALE`, ensuring full compatibility with Android 14+ Health Connect privacy management.
   - The system SHALL check and request required permissions using `PermissionController.createRequestPermissionResultContract()` before performing workout sync operations.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an Android device with Health Connect installed and supported,
• *When* the user accesses Health Connect settings,
• *Then* `getSdkStatus()` returns `SDK_AVAILABLE` and permission requests are presented via standard Health Connect contracts.
• *Given* an Android 14+ device,
• *When* Health Connect privacy controls or permission settings are opened,
• *Then* the system's `VIEW_PERMISSION_USAGE` activity alias is resolved and displayed with `START_VIEW_PERMISSION_USAGE` protection.
• *Given* a device without Health Connect support,
• *When* Health Connect features are accessed,
• *Then* the system reports unavailability without throwing unhandled exceptions.

**Invariants**: Core tracking loop and local SQLite persistence MUST operate independently of Health Connect availability or permission status. Undeclared permissions must never be requested at runtime.

### REQ-SET-101: Exercise Session & Telemetry Series Record Mapping [Verified]
- **Tests**: `TST-SET-101`
- **Priority**: High
- **Type**: Functional
- **Verification Method**: Unit, Integration
- **Implementation**: `HealthConnectRecordMapper.kt`, `HealthConnectManager.kt`, `WorkoutSamplesRepository.kt`, `WorkoutData.kt`
- **Status**: Verified
- **Rationale**: Completed workout metadata and sensor time-series data must map to standardized Health Connect data models:
1. *Session Metadata Mapping (`REQ-SET-101.1`)*:
   - `BSportType` SHALL map to Health Connect exercise types: `BIKE` -> `EXERCISE_TYPE_BIKING`, `RUN` -> `EXERCISE_TYPE_RUNNING`, trainer/indoor rides -> `EXERCISE_TYPE_BIKING_STATIONARY`, treadmill runs -> `EXERCISE_TYPE_RUNNING_TREADMILL`, and other sports -> `EXERCISE_TYPE_OTHER_WORKOUT`.
   - Start time, end time, duration, and title SHALL populate `ExerciseSessionRecord.Builder`. The session `endTime` SHALL satisfy `maxRouteTime < endTime`; if GPS route points reach or exceed `startTime + durationSec`, `endTime` SHALL be extended to at least `maxRouteTime.plusSeconds(1)` (`ATT-7668`).
2. *Telemetry Time-Series Conversion & Route Permission Gating (`REQ-SET-101.2`)*:
   - The system SHALL extract per-second samples via `WorkoutSamplesRepository` (`ATT-6426`) and convert them into corresponding Health Connect series records:
     - GPS coordinates with timestamps and altitude -> `ExerciseRoute` (strictly gated on `"android.permission.health.WRITE_EXERCISE_ROUTE" in granted`; if omitted or denied, `exerciseRoute` SHALL be null). Duplicate route points sharing the same timestamp SHALL be deduplicated, and route locations SHALL be strictly monotonically ordered (`sortedRoute[i].time < sortedRoute[i+1].time`, `ATT-7668`).
     - Heart rate samples (bpm) -> `HeartRateRecord`.
     - Power samples (Watts) -> `PowerRecord`.
     - Speed samples (m/s) -> `SpeedRecord`.
     - Distance and elevation gained -> `DistanceRecord` and `ElevationGainedRecord`.
     - Cadence samples (rpm / spm) -> `CyclingPedalingCadenceRecord` (for cycling) or `StepsCadenceRecord` (for running).
     - Total calories -> `TotalCaloriesBurnedRecord` derived directly from `WorkoutData.calories` (originating from `WorkoutSummaries.CALORIES`), eliminating UI formatted-string parsing (`Coding Standards §1.3`).
3. *Binder IPC Series Chunking (`REQ-SET-101.3`)*:
   - Series records (`HeartRateRecord`, `SpeedRecord`, `PowerRecord`, `DistanceRecord`, `ElevationGainedRecord`, `CyclingPedalingCadenceRecord`, `StepsCadenceRecord`) SHALL be chunked into series records containing $\le 2,000$ samples per record/chunk to prevent Binder IPC `TransactionTooLargeException` on multi-hour recordings.

**Acceptance Criteria (Given-When-Then)**:
• *Given* a completed outdoor bike workout with GPS, HR, and cadence,
• *When* Health Connect synchronization executes,
• *Then* an `ExerciseSessionRecord` (type `EXERCISE_TYPE_BIKING`) with associated `ExerciseRoute`, `HeartRateRecord`, and `CyclingPedalingCadenceRecord` series is written to Health Connect.
• *Given* GPS samples containing duplicate whole-second timestamps,
• *When* `ExerciseRoute` is constructed,
• *Then* points are deduplicated and strictly monotonically ordered without throwing `IllegalArgumentException`.
• *Given* a workout where GPS route samples coincide with or exceed `startTime + durationSec`,
• *When* `ExerciseSessionRecord` is constructed,
• *Then* `endTime` is extended so that `maxRouteTime.isBefore(endTime)` holds without `IllegalArgumentException`.
• *Given* an outdoor workout where `WRITE_EXERCISE_ROUTE` is denied,
• *When* Health Connect synchronization executes,
• *Then* the session is written with `exerciseRoute = null` without throwing `SecurityException`.
• *Given* a multi-hour workout with >2,000 continuous samples,
• *When* records are mapped and written to Health Connect,
• *Then* series records are partitioned into chunks of $\le 2,000$ samples without Binder transaction overflow.

**Invariants**: All series record timestamps must strictly fall within the exercise session's `[startTime, endTime]` interval. Calorie extraction MUST be numerical from database models, never scraped from UI presentation strings. ExerciseRoute timestamps MUST be strictly ascending with zero duplicates. Session endTime MUST be strictly after the latest route point.

### REQ-SET-102: Trackless Indoor Workout Health Connect Export Parity [Verified]
- **Tests**: `TST-SET-102`
- **Priority**: High
- **Type**: Functional
- **Verification Method**: Unit, Integration
- **Implementation**: `HealthConnectRecordMapper.kt`, `HealthConnectManager.kt`
- **Status**: Verified
- **Rationale**: Indoor workouts (smart trainer sessions, indoor runs, gym workouts) record physiological sensor streams without GPS tracks. The sync pipeline SHALL export trackless workouts without route dependency:
1. *Optional Route Decoupling (`REQ-SET-102.1`)*:
   - When workout track data contains no GPS coordinates (`lat`/`lon` null or empty), `ExerciseSessionRecord` SHALL be constructed with `exerciseRoute = null`.
2. *Series Record Persistence Parity (`REQ-SET-102.2`)*:
   - All available sensor series (`HeartRateRecord`, `PowerRecord`, `SpeedRecord`, `TotalCaloriesBurnedRecord`) SHALL be persisted normally regardless of route presence.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an indoor workout with power and HR data but no GPS track,
• *When* syncing to Health Connect,
• *Then* the exercise session and all non-spatial series records are successfully inserted without `IllegalArgumentException` or missing-route errors.

**Invariants**: Zero coordinate fabrication or null pointer exceptions for indoor trackless sessions.

### REQ-SET-103: Health Connect Settings UI, Manual Re-Sync & Write Idempotency [Verified]
- **Tests**: `TST-SET-103`
- **Priority**: High
- **Type**: Functional
- **Verification Method**: Unit, Integration
- **Implementation**: `HealthConnectSettingsDialog.kt`, `AppNavigationDrawer.kt`, `NavRoutes.kt`, `WorkoutHeader.kt`, `WorkoutDetailTabbedContent.kt`, `WorkoutSummary.kt`, `EditWorkoutScreen.kt`, `HealthConnectManager.kt`, `PeriodsViewModel.kt`, `MorningHrvCheckViewModel.kt`
- **Status**: Verified
- **Rationale**: Athletes require user-friendly settings controls and deterministic synchronization guarantees:
1. *Cloud Integrations Settings Toggle & Navigation Drawer Entry (`REQ-SET-103.1`)*:
   - The navigation drawer SHALL include `drawer_health_connect` (with dedicated vector icon and Last Sync timestamp per `Design Guidelines §3.4`) under `prefsOnlineCommunities`, mapping via `NavRoutes.toBottomSheetType()` to `SettingsBottomSheetType.HEALTH_CONNECT`.
   - The system SHALL provide a "Sync with Health Connect" preference toggle in `HealthConnectSettingsDialog`, prompting for write permissions when enabled.
   - The system SHALL provide an "Import Resting HRV from Health Connect" preference toggle in `HealthConnectSettingsDialog`, wired to `isHealthConnectHrvImportEnabled()` and prompting for `HRV_READ_PERMISSIONS` when enabled, triggering an initial 60-day backfill on enable and 14-day refreshes on `PeriodsViewModel` / `MorningHrvCheckViewModel` load.
2. *Automatic & Manual Synchronization Lifecycle (`REQ-SET-103.2`)*:
   - Completed workouts SHALL automatically synchronize in the background upon workout finalization when enabled.
   - A manual "Sync to Health Connect" action SHALL be wired in `WorkoutHeader` and invoked from `WorkoutDetailTabbedContent` and `WorkoutSummary` with `force = true`, executing even if global auto-sync is disabled.
   - `EditWorkoutScreen` SHALL display the manual sync checkbox without triggering immediate sync on toggle; sync SHALL execute asynchronously with `force = true` strictly upon clicking Save when checked.
3. *Idempotent Record Keying (`REQ-SET-103.3`)*:
   - Records written to Health Connect SHALL define `Metadata(clientRecordId = "workout_${fileBaseName}")` (and corresponding deterministic IDs for series records/chunks) to ensure repeated syncs update existing records rather than producing duplicates.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Amends `REQ-SET-100`, `REQ-SET-101`, and `REQ-SET-103` (`REQ-SET-103.1`, `103.2`), targeting `AndroidManifest.xml`, `HealthConnectManager.kt`, `HealthConnectRecordMapper.kt`, `ids.xml`, `AppNavigationDrawer.kt`, `NavRoutes.kt`, `WorkoutHeader.kt`, `WorkoutDetailTabbedContent.kt`, `WorkoutSummary.kt`, and `EditWorkoutScreen.kt`.
2. *Historical Origin & Commit Trace*: Introduced in `ATT-3052` (commit `3ef15b88`), amended in Sprint `2026-46.2` (`ATT-6712`, `ATT-6711`).
3. *Root Reason for Existing Formulation*: Originally scoped to workout write export only; `ATT-3052` omitted drawer navigation wiring, elevation and cadence permissions in manifest, Android 14+ aliases, route permission check, series sample chunking, and typed calorie retrieval due to missing `WorkoutData.calories`.
4. *Preservation of Core Invariants*: Local workout tracking, SQLite persistence, deterministic clientRecordId keying, and existing cloud export pipelines remain 100% untouched.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an athlete accessing the Navigation Drawer,
• *When* tapping "Health Connect",
• *Then* `HealthConnectSettingsDialog` opens with current sync status and Last Sync timestamp.
• *Given* a workout already synchronized to Health Connect,
• *When* the user triggers a manual re-sync,
• *Then* existing records in Health Connect are updated in-place via identical `clientRecordId` without session duplication even if global auto-sync is disabled.
• *Given* an athlete in `EditWorkoutScreen`,
• *When* toggling "Sync to Health Connect",
• *Then* no network sync is fired immediately; sync executes strictly upon Save.
• *Given* an athlete in `HealthConnectSettingsDialog`,
• *When* toggling "Import Resting HRV from Health Connect",
• *Then* `HRV_READ_PERMISSIONS` authorization is requested and an initial 60-day backfill is triggered upon confirmation.

**Invariants**: Re-sync operations must never produce duplicate entries or orphan telemetry series in Health Connect. Zero unprompted background sync operations when disabled.

### REQ-SET-104: Polar H10 Internal RR Recording & GATT Arbitration [Verified]
- **Tests**: `TST-SET-106`, `TST-CON-049`
- **Priority**: High
- **Type**: Functional
- **Verification Method**: Unit, Integration
- **Implementation**: `app/build.gradle`, `AndroidManifest.xml`, `DevicesDatabaseManager.java`, `DeviceUiData.kt`, `EditDeviceDialog.kt`, `EditDeviceViewModel.kt`, `PolarH10OfflineController.kt`, `TrackerService.java`
- **Rationale**: Athletes using Polar H10 chest straps lose heart rate and beat-to-beat RR interval telemetry during activities like swimming or when separated from their smartphone. While the Polar H10 features internal flash memory capable of recording an entire session via Polar PFTP GATT protocol, starting and managing internal recording requires proper clock synchronization, clearing existing exercises, and careful GATT arbitration with standard 0x180D BLE streaming without breaking backwards compatibility on Android 8–12 (`minSdkVersion 26`). The system SHALL provide configurable internal backup recording for Polar H10 devices:
1. *Polar BLE SDK Integration & Manifest Override (`REQ-SET-104.1`)*:
   - The project SHALL integrate `com.github.polarofficial:polar-ble-sdk:8.6.0` using `<uses-sdk tools:overrideLibrary="com.polar.sdk, com.polar.androidcommunications"/>` in `app/src/main/AndroidManifest.xml` to reconcile the SDK's higher `minSdkVersion` requirement with the app's global `minSdkVersion 26` baseline.
   - All runtime invocations of Polar BLE SDK APIs SHALL be guarded behind `@RequiresApi(Build.VERSION_CODES.TIRAMISU)` / `Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU` (API 33) runtime checks, falling back safely on earlier Android releases.
2. *Database Persistence & Migration Parity (`REQ-SET-104.2`)*:
   - `DevicesDatabaseManager` SQLite database schema SHALL be upgraded from `DB_VERSION = 12` to `DB_VERSION = 13` by adding column `polarH10OfflineRecording int DEFAULT 1` to table `devices`.
   - Both `onCreate()` and `onUpgrade()` migration paths SHALL maintain identical column definitions and default constraints.
3. *Device Configuration UI & 9-Locale Parity (`REQ-SET-104.3`)*:
   - In `EditDeviceDialog.kt` and `EditDeviceViewModel.kt`, the system SHALL display a dedicated "Polar H10 Backup Recording" switch item strictly when the inspected device is a Polar H10 heart rate strap (`deviceType == DeviceType.HRM` and name starts with `"Polar H10"` or manufacturer is Polar).
   - Toggling the switch SHALL immediately persist the setting in `DevicesDatabaseManager`.
   - All UI labels and descriptions SHALL be fully localized across all 9 supported application locales (`values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`).
4. *GATT Arbitration & Arming Lifecycle (`REQ-SET-104.4`)*:
   - When a workout begins (`TrackerService.onStartCommand`), if a paired Polar H10 device has backup recording enabled, `PolarH10OfflineController` SHALL asynchronously connect via Polar BLE SDK, synchronize device time (`setLocalTime`), query/remove any stale existing exercise session, and start internal recording of `SampleType.RR`.
   - The controller SHALL arbitrate GATT access with `MyBTLEDevice`/`BTLEHeartRateDevice` to prevent concurrent GATT connection collisions on the same peripheral MAC address.
   - *Fringe-Flap Invariant*: The internal recording session SHALL be armed strictly once at workout start and SHALL NOT re-arm, restart, or truncate ongoing recording if BLE connection is dropped and re-established mid-workout.
5. *Polar SDK Attribution & License Notice (`REQ-SET-104.5`)*:
   - In compliance with Polar BLE SDK License and GPLv3 Section 7, the application SHALL document the Polar BLE SDK third-party license notice and linking attribution in project legal/license documentation (`LICENSE`).

**Acceptance Criteria (Given-When-Then)**:
• *Given* an Android 8.0–12L device (`SDK_INT < 33`),
• *When* the app launches and connects to a Polar H10 sensor,
• *Then* the app functions normally without `ClassNotFoundException` or `NoClassDefFoundError`, streaming live HR/RR data via standard 0x180D BLE GATT.
• *Given* an Android 13+ device (`SDK_INT >= 33`) with a paired Polar H10 where backup recording is enabled,
• *When* tracking starts in `TrackerService`,
• *Then* `PolarH10OfflineController` synchronizes device time, clears stale exercises, and starts internal RR recording asynchronously without blocking foreground service startup.
• *Given* an active workout with internal recording armed,
• *When* the user swims away from phone causing BLE disconnection and later reconnects,
• *Then* standard 0x180D streaming resumes and the active internal recording session continues undisturbed without re-arming.
• *Given* a fresh install or an upgrade from DB version 12,
• *Then* `DevicesDatabaseManager` initializes or upgrades cleanly to version 13 with `polar_h10_offline_recording` defaulting to 0 (`false`).

**Invariants**: Standard 0x180D GATT heart rate and RR streaming in `BTLEHeartRateDevice` is never regressed; workout start is non-blocking even if the sensor is unreachable; full database migration parity between `onCreate` and `onUpgrade`.

### REQ-SET-105: Polar H10 Offline HR/RR Workout Stop Backfill & Export Gating [Verified]
- **Tests**: `TST-SET-107`
- **Priority**: High
- **Type**: Functional
- **Verification Method**: Unit, Integration
- **Implementation**: `PolarH10OfflineController.kt`, `TrackerService.java`, `WorkoutSamplesDatabaseManager.java`, `WorkoutRrDatabaseManager.kt`, `HrvPostWorkoutRecomputer.kt`, `ExportManager.java`
- **Status**: Verified (ATT-7918)
- **Rationale**: When endurance athletes record swimming sessions or experience mid-workout radio dropouts with an armed Polar H10, `TrackerService.endWorkout()` must retrieve the internal flash session via Polar PFTP protocol before triggering automated file serialization or cloud synchronization. Without gating, `ExportManager` publishes incomplete telemetry files lacking heart rate and HRV metrics. The system SHALL retrieve stored Polar H10 RR intervals on workout stop, patch missing samples idempotently in SQLite, recompute summary and HRV extrema, and gate downstream exports:
1. *Zero-Gap Fast Path Evaluation (`REQ-SET-105.1`)*:
   - Upon `TrackerService.endWorkout()`, if Polar H10 internal recording was armed (`isRecordingArmed == true`), the system SHALL first evaluate live telemetry continuity in `WorkoutSamples.db` and `WorkoutRr.db`.
   - If live BLE 0x180D reception was 100% continuous (`count(HR) == totalActiveSeconds` and zero `ARTIFACT_GAP`), the system SHALL bypass the slow multi-second PFTP transfer, immediately issue `stopRecording()` and `removeExercise()`, and release downstream exports without delay.
2. *Asynchronous Fetch, GATT Handoff & Foreground Progress Notification (`REQ-SET-105.2`)*:
   - When gaps exist, `PolarH10OfflineController.stopAndBackfillWorkout()` SHALL pause `MyBTLEDevice` GATT operations, establish PFTP connection to the strap, and retrieve recorded RR intervals via `fetchExercise()`.
   - A foreground notification ("Syncing HR/HRV from Polar H10..." / "Synchronisiere HF/HRV von Polar H10...") SHALL be displayed during download with 100% 9-language localization parity across all supported application locales (`values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`).
   - The fetch pipeline SHALL be bounded by a 30-second timeout (`withTimeoutOrNull(30_000L)`). If the strap is out of range, dead, or times out, the system SHALL log a warning, resume GATT, and proceed gracefully with existing live data without service crash.
3. *Timestamp Alignment & Idempotent Gap-Only SQLite Backfill (`REQ-SET-105.3`)*:
   - Retrieved RR intervals SHALL be anchored to the exercise start timestamp established at workout start (`setLocalTime`).
   - Missing 1-Hz HR samples (`round(60000.0 / rrMs)`) SHALL be inserted strictly into empty seconds in `WorkoutSamples.db` without overwriting or duplicating existing live samples.
   - Beat-to-beat RR intervals SHALL be inserted into `WorkoutRr.db`, closing matching `ARTIFACT_GAP` intervals.
4. *Post-Backfill Recomputation & Transactional Slot Cleanup (`REQ-SET-105.4`)*:
   - Upon successful SQLite backfill commit, the system SHALL invoke `HrvPostWorkoutRecomputer.recomputeWorkoutBlocking(workoutId)` to recalculate centered rMSSD, SDNN, DFA a1, and Autonomic Durability, and update `WorkoutSummaries.db` HR/HRV averages, maxima, and HR zone distributions.
   - The stored exercise on the Polar H10 strap SHALL be deleted via `removeExercise()` strictly after SQLite backfill and recomputation commit successfully.
5. *Export Pipeline Gating (`REQ-SET-105.5`)*:
   - Downstream export triggers (`ExportManager.workoutFinished()`, FIT/TCX/GPX/CSV file generation, and Strava / Health Connect / Dropbox sync) SHALL be held in `TrackerService.endWorkout()` until backfill and summary recomputation finish (or until timeout fallback expires), ensuring all exported files and cloud syncs contain complete HR and HRV metrics.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an athlete finishes a 30-minute pool swim with phone left in the locker and strap within BLE range upon return,
• *When* the athlete taps Stop in `TrackerService`,
• *Then* the stored RR exercise is fetched from the H10, missing 1-Hz HR rows in `WorkoutSamples.db` and beat rows in `WorkoutRr.db` are inserted, `HrvPostWorkoutRecomputer` updates `WorkoutSummaries.db` extrema, and `removeExercise()` cleans up the strap.
• *Given* automated exports (FIT, TCX, Strava, Health Connect) are enabled on a workout with Polar H10 gaps,
• *When* the workout ends,
• *Then* `ExportManager.workoutFinished()` is gated until backfill commits, and all exported files contain continuous HR curves and complete HRV metrics.
• *Given* an outdoor ride with a Polar H10 where 0x180D BLE streaming had zero dropouts,
• *When* the workout ends,
• *Then* the zero-gap fast path immediately stops and deletes the H10 exercise without executing a multi-second PFTP download.
• *Given* a Polar H10 that was turned off or walked away before tapping Stop,
• *When* the 30-second backfill timeout expires,
• *Then* `TrackerService` finalizes the workout using available live data without hanging or crashing.

**Invariants**: Zero data loss or corruption of existing live GPS, power, cadence, or HR telemetry; zero export of premature/partial files prior to backfill completion when strap is available; bounded 30-second timeout guarantees foreground service termination even if strap is lost; full 9-language localization parity for any user-facing notifications.

### REQ-SET-106: Polar H10 Aftermath Retry Sync Banner for Asleep Strap [Verified]
- **Tests**: `TST-SET-108`
- **Priority**: High
- **Type**: Functional
- **Verification Method**: Unit, Integration, On-Device Manual
- **Implementation**: `WorkoutSummariesDatabaseManager.java`, `PolarH10OfflineController.kt`, `WorkoutData.kt`, `WorkoutDataMapper.kt`, `TrackOnMapScreen.kt`, `PolarH10RetrySyncBanner.kt`, `WorkoutRepository.kt`, `ExportManager.java`
- **Status**: Verified (ATT-7919)
- **Rationale**: A Polar H10 powers down BLE 45s after losing skin contact (e.g. swimmer unclipping in shower before reaching locker). When stop-time backfill fails or times out, the workout must finalize without blocking, while preserving persistent metadata of the unretrieved session in `WorkoutSummaries.db`. An Aftermath retry banner prompts the athlete to wake the sensor, triggers deferred backfill, updates summaries and exports, and transactionally removes the exercise from strap memory upon successful SQLite commit.
- **Description**:
1. *Pending-Sync Metadata Persistence (`REQ-SET-106.1`)*:
   - When `PolarH10OfflineController.stopAndBackfillWorkout()` fails or times out due to an unreachable/asleep strap, `TrackerService.endWorkout()` SHALL persist `polarDeviceId`, `polarExerciseId`, `polarExpectedStartMs`, `polarExpectedEndMs`, and `polarSyncPending = 1` into `WorkoutSummaries.db` (v31 schema) before completing workout finalization.
2. *Material 3 Aftermath Retry Sync Banner (`REQ-SET-106.2`)*:
   - When viewing a workout summary where `polarSyncPending == 1` in `TrackOnMapScreen`, an M3 Card banner SHALL be rendered at the top of the summary content.
   - The banner SHALL display sensor wake instructions ("Wet electrodes and snap strap on, or place thumbs on sensors to wake"), a primary "Sync Now" button, and a "Dismiss" action.
   - All banner strings SHALL maintain 100% 9-language localization parity across EN, DE, ES, FR, IT, JA, NL, PL, PT with zero decorative inline emojis.
3. *Deferred Wake & Backfill Execution (`REQ-SET-106.3`)*:
   - Tapping "Sync Now" SHALL trigger `PolarH10OfflineController.syncPendingWorkout()`, connecting to the awake strap via PFTP, validating the matching exercise ID or overlapping start time window, and fetching recorded RR data bounded by a 30-second timeout.
   - Missing 1-Hz HR samples SHALL be patched into `WorkoutSamples.db`, and RR beats SHALL be inserted into `WorkoutRr.db` closing `ARTIFACT_GAP` ranges.
   - Upon successful SQLite commit, `HrvPostWorkoutRecomputer` SHALL recompute summary metrics, `removeExercise()` on the strap SHALL be invoked, `polarSyncPending` SHALL be set to 0 in `WorkoutSummaries.db`, and `ExportManager.exportWorkout()` SHALL be triggered to regenerate updated export files.
4. *Dismiss Banner Action (`REQ-SET-106.4`)*:
   - Tapping "Dismiss" SHALL update `WorkoutSummaries.db` setting `polarSyncPending = 0`, permanently dismissing the banner for this workout without clearing or deleting the unretrieved exercise on the strap.
5. *Database Migration Parity (`REQ-SET-106.5`)*:
   - `WorkoutSummariesDatabaseManager` upgrade from version 30 to 31 SHALL add columns `polarDeviceId` (TEXT), `polarExerciseId` (TEXT), `polarExpectedStartMs` (INTEGER), `polarExpectedEndMs` (INTEGER), and `polarSyncPending` (INTEGER DEFAULT 0), maintaining exact schema parity between `onCreate()` and `onUpgrade()`.

**Acceptance Criteria (Given-When-Then)**:
• *Given* an athlete unclips a Polar H10 in the shower and stops tracking 2 minutes later at the locker,
• *When* stop-time backfill times out after 30s,
• *Then* `TrackerService` finalizes the workout without hanging, saving `polarSyncPending = 1` in `WorkoutSummaries.db`.
• *Given* a workout with `polarSyncPending == 1` opened in Aftermath (`TrackOnMapScreen`),
• *When* the user views the screen,
• *Then* the M3 retry sync banner is displayed with wake guidance, "Sync Now", and "Dismiss".
• *Given* the athlete wets the electrodes, snaps the strap on, and taps "Sync Now",
• *When* `syncPendingWorkout()` runs,
• *Then* stored RR data is retrieved, missing 1-Hz HR and RR intervals are populated in SQLite, HRV metrics are recalculated, the exercise is deleted from the strap, the banner disappears, and exports are regenerated.
• *Given* an athlete taps "Dismiss" on the retry banner,
• *When* dismissal completes,
• *Then* `polarSyncPending` is cleared to 0 in SQLite, the banner is hidden, and no strap exercises are deleted.

**Invariants**: Workout finalization is never blocked by an asleep strap; existing live telemetry in `WorkoutSamples.db` is never overwritten; `removeExercise()` is executed strictly after SQLite commit; full 9-language localization parity without emojis.






### REQ-SET-107: Swimming SportType Template, Expert Activation, Pool Length & FIT Export [Verified]
- **Tests**: `TST-SET-109`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit, Integration, On-Device Manual
- **Implementation**: `SportTypeDatabaseManager.java`, `SportTypesRepository.kt`, `DatabaseSchemaRegistry.kt`, `TuningPreferencesDataStore.kt`, `SettingsDataStoreJavaHelper.kt`, `AdvancedTuningAccordion.kt`, `AdvancedTuningScreen.kt`, `SwimmingSection.kt`, `FitFileWriter.java`, `res/values*/strings_swimming.xml`
- **Status**: Verified (ATT-7999)
- **Rationale**: Eagerly seeding Swimming into `SportType.db` clutters UI lists for pure cyclists/runners, while assigning non-zero speed thresholds collides with walking (~1.0–1.8 m/s). Additionally, Garmin TCX forces `tcxName = "Other"` for swimming, causing `FitFileWriter` (which only checked `tcxName`) to emit `Sport.GENERIC`. Providing an unseeded template, idempotent activation, Expert pool-length settings, and `stravaName`/`gcName` FIT resolution ensures turnkey swimming support without side effects.
- **Description**:
1. *Prepared `TTSportType.SWIM` Template & Zero-Collision Bounds (`REQ-SET-107.1`)*:
   - `SportTypeDatabaseManager` SHALL define `TTSportType.SWIM` (`BSportType.UNKNOWN`, `R.string.sport_type_swim`, `gcName = "swim"`, `stravaName = "Swim"`, `tcxName = "Other"`, `runkeeperName = "Swimming"`, `trainingPeaksName = "Swim"`), localized across all 9 UI languages (`values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`) without emojis.
   - Fresh `onCreate()` SHALL continue seeding only the 5 default rows (`OTHER`, `RUN`, `BIKE`, `WALK`, `MTB`) at `DB_VERSION = 3` (referenced directly by `DatabaseSchemaRegistry`).
   - `SWIM` SHALL use `minSpeed = 0.0` and `maxSpeed = 0.0` so speed-range matching `minSpeed <= avgSpd AND maxSpeed > avgSpd` never matches `Swimming`.
2. *Idempotent `ensureSwimSportTypeExists()` (`REQ-SET-107.2`)*:
   - `SportTypeDatabaseManager` and `SportTypesRepository` SHALL expose `ensureSwimSportTypeExists(): Long` and `isSwimSportTypeActivated(): Boolean`.
   - When `ensureSwimSportTypeExists()` runs, if an active (`is_placeholder = 0`) row with case-insensitive `stravaName = "Swim"` or `gcName = "swim"` already exists, it SHALL return that row's ID without inserting a duplicate; otherwise it SHALL insert/activate the localized `SWIM` row (`minSpeed = 0.0`, `maxSpeed = 0.0`) and return its ID.
3. *Expert Settings Swimming Activation & Default Pool Length (`REQ-SET-107.3`)*:
   - `TuningPreferencesDataStore` SHALL persist `defaultPoolLengthMeters` (Float, default `25.0f`, presets `25 m`, `50 m`, `25 yd` = `22.86f`, or custom `10f..100f` meters) and `defaultPoolLengthUnit` (`PoolLengthUnit.METERS` / `YARDS`), included in `ALL_KEYS` and `resetToDefaults()`.
   - `AdvancedTuningScreen` (`TuningSection.SWIMMING`, `SwimmingSection.kt`) SHALL follow `docs/design_guidelines.md` (no `FilterChip`, raw `Switch`, or nested `ElevatedCard`), offering Swimming sport-type activation (`ensureSwimSportTypeExists()` on Save) and default pool length selection across all 9 languages.
4. *FIT Export `Sport.SWIMMING` & Lap Swimming Metadata (`REQ-SET-107.4`)*:
   - `FitFileWriter` SHALL resolve `Sport.SWIMMING` when `tcxName` contains `"swim"`, `stravaName` equals `"Swim"` (ignore case), or `gcName` equals `"swim"` (ignore case).
   - For swimming sessions where pool length (`> 0f`) or indoor pool context applies, `FitFileWriter` SHALL encode `SubSport.LAP_SWIMMING` and populate `SessionMesg` `pool_length` (meters) and `pool_length_unit` (`DisplayMeasure.STATUTE` for `YARDS`, `DisplayMeasure.METRIC` for `METERS`).

**Acceptance Criteria (Given-When-Then)**:
• *Given* a fresh installation where Swimming has not been activated, *When* inspecting `SportType.db`, *Then* only the 5 defaults exist (`Swimming` absent).
• *Given* `ensureSwimSportTypeExists()` is invoked once (or on a DB already containing user `Swim`), *When* called again, *Then* exactly one active Swimming row exists and its ID is returned idempotently.
• *Given* `Swimming` exists with `minSpeed = 0.0, maxSpeed = 0.0`, *When* speed-based lookup runs for `1.4 m/s` (`BSportType.UNKNOWN` or `RUN`) or `0.0 m/s`, *Then* `WALK`/`OTHER` resolves and `Swimming` is never selected.
• *Given* a Swimming workout (`tcxName = "Other"`, `stravaName = "Swim"`) and default pool length `25 m` (`25 yd`), *When* exported via `FitFileWriter`, *Then* `SessionMesg` encodes `Sport.SWIMMING`, `SubSport.LAP_SWIMMING`, `pool_length = 25.0` (`22.86`), and `DisplayMeasure.METRIC` (`STATUTE`).

**Invariants**: `SportType.db` `DB_VERSION = 3` unchanged; fresh DB row count stays 5; zero walk-speed collision; zero emojis in strings; full `TuningDesignGuidelinesContractTest` compliance.

---

### REQ-SET-108: Polar Verity Sense Offline HR Recording Controller & EditDeviceDialog Support (`ATT-7923`) [Verified]
- **Tests**: `TST-SET-110`, `TST-CON-049`
- **Priority**: High
- **Type**: Functional
- **Verification Method**: Unit / Integration
- **Implementation**: `PolarOfflineGattCoordinator.kt`, `PolarH10OfflineController.kt`, `PolarVeritySenseOfflineController.kt`, `TrackerService.java`, `EditDeviceDialog.kt`, `BTSearchForNewDevicesEngine.java`, `res/values*/strings_devices.xml`
- **Rationale**: Optical Polar sensors such as Polar Verity Sense use `PolarOfflineRecordingApi` (`PolarDeviceDataType.HR`) rather than H10's `PolarExerciseApi` to maintain internal backup HR recording during pool swims or BLE dropouts without adding a duplicate SQLite migration or interrupting `0x180D` live streaming.
- **Description**:
1. *Optical Offline-Capable Device Detection & Settings Reuse (`REQ-SET-108.1`)*:
   - The system SHALL recognize Polar Verity Sense / compatible `FEATURE_POLAR_OFFLINE_RECORDING` optical HR devices via device name/manufacturer matching (`"Polar Sense"`, `"Verity Sense"`, `"Polar Verity"`, `"Polar OH1"`) and persisted PFTP/offline capability cache (`PolarOfflineCapabilityStore`), reusing `Devices.db` v13 `polarH10OfflineRecording` (`DEFAULT 1`) without bumping `DB_VERSION`.
   - `EditDeviceDialog` SHALL display the `@RequiresApi(33)` backup recording toggle for detected Polar Verity Sense / compatible optical Polar HRM devices (default ON on Android 13+, disabled with Android 13 hint below API 33), using accurate HR-only copy (`device_polar_verity_backup_recording_title` / `device_polar_verity_backup_recording_desc`) vs. H10 HR + HRV copy across all 9 locales (`values*/strings_devices.xml`) without emojis.
2. *Shared GATT Arbitration & `PolarVeritySenseOfflineController` (`REQ-SET-108.2`)*:
   - Shared GATT yield (`pauseGattOperations()` / `resumeGattOperations()`), Polar 8-hex ID extraction, DB device resolution, and SDK feature-readiness awaiting SHALL be factored into `PolarOfflineGattCoordinator` reused by `PolarH10OfflineController` and `PolarVeritySenseOfflineController`.
   - On Android 13+ when backup recording is enabled, `PolarVeritySenseOfflineController.onWorkoutStarted(context)` SHALL coordinate GATT yield, await `FEATURE_POLAR_OFFLINE_RECORDING` and `FEATURE_POLAR_DEVICE_TIME_SETUP`, call `setLocalTime()`, inspect `getOfflineRecordingStatus()` (stopping pre-existing `PolarDeviceDataType.HR` if active), start `PolarDeviceDataType.HR` via `PolarOfflineRecordingApi.startOfflineRecording(..., settings = null, secret = null)`, and release GATT back to `BTLEHeartRateDevice` in a `finally` block.
   - `TrackerService` SHALL invoke `PolarVeritySenseOfflineController.setSessionStartTimeMs(...)`, `onWorkoutStarted(this)`, and `onWorkoutStopped(this)` alongside `PolarH10OfflineController`.
3. *Mid-Workout Fringe-Flap Invariant, `SYSTEM_BUSY` Resilience & Fetch/Cleanup API (`REQ-SET-108.3`)*:
   - `PolarVeritySenseOfflineController` SHALL arm once per workout (`isArmed` CAS); mid-workout `0x180D` BLE disconnects or reconnects MUST NOT stop, restart, or re-query PFTP offline recording.
   - `BlePsFtpUtils.PftpResponseError` (`PbPFtpError.SYSTEM_BUSY`) or SDK failures during arming MUST NOT crash or block workout start; the controller SHALL clear `isArmed`, restore standard GATT operations, and record a diagnosable last error state.
   - `PolarVeritySenseOfflineController` SHALL expose `suspend fun stopAndFetchOfflineHr(...)` (stopping active HR recording, querying `listOfflineRecordings`, fetching `PolarOfflineRecordingData.HrOfflineRecording`) and `suspend fun removeOfflineRecord(...)` for downstream post-workout backfill (`ATT-7924`).

**Acceptance Criteria (Given-When-Then)**:
• *Given* an Android 13+ device with a Polar Verity Sense paired, *When* opening `EditDeviceDialog`, *Then* the backup toggle defaults ON with HR-only Verity Sense copy in all 9 locales (and shows disabled Android 13 hint below API 33).
• *Given* workout start on Android 13+ with Verity Sense backup enabled, *When* `PolarVeritySenseOfflineController.onWorkoutStarted()` runs, *Then* standard GATT pauses, `setLocalTime()` and `getOfflineRecordingStatus()` execute (stopping active `HR` if present), `startOfflineRecording(..., HR, null, null)` succeeds, and `resumeGattOperations()` always executes.
• *Given* an already-armed workout experiencing a mid-workout `0x180D` BLE flap, *When* `onWorkoutStarted()` / `onDeviceDisconnected()` occurs, *Then* PFTP offline recording is NOT stopped, restarted, or re-queried.
• *Given* the Verity Sense returns `SYSTEM_BUSY` during arming, *When* `onWorkoutStarted()` executes, *Then* workout start completes non-blockingly, `isArmed` resets cleanly, and `resumeGattOperations()` restores live `0x180D` streaming.

**Invariants**: `Devices.db` stays `DB_VERSION = 13`; zero workout-start crashes or hangs on `SYSTEM_BUSY`; zero mid-workout PFTP interruptions; zero emojis in strings; full API < 33 isolation.

---

### REQ-SET-109: Polar Verity Sense Stop-Time HR Backfill & Aftermath Retry Sync (`ATT-7924`) [Verified]
- **Tests**: `TST-SET-111`
- **Priority**: High
- **Type**: Functional
- **Verification Method**: Unit / Integration
- **Implementation**: `PolarVeritySenseOfflineController.kt`, `PolarOfflineGattCoordinator.kt`, `TrackerService.java`, `WorkoutSamplesDatabaseManager.java`, `WorkoutSummariesDatabaseManager.java`, `PolarH10RetrySyncBanner.kt`, `TrackOnMapScreen.kt`, `WorkoutDetailTabbedContent.kt`, `res/values*/strings_devices.xml`
- **Rationale**: Swimmers leaving phones in lockers rely on Polar Verity Sense internal 1-Hz optical HR memory (`ATT-7923`) to recover missed BLE heart rate samples on workout stop (or later in Aftermath when powered back on in Blue Sensor Mode) before automatic exports execute, while strictly protecting `WorkoutRr.db` HRV integrity against optical 1-Hz PPG pollution.
- **Description**:
1. *Stop-Time Backfill, Export Gate & Zero-Gap Fast Path (`REQ-SET-109.1`)*:
   - On Android 13+ during `TrackerService.endWorkout()`, if `PolarVeritySenseOfflineController.isRecordingArmed()` is true, `TrackerService` SHALL invoke `PolarVeritySenseOfflineController.stopAndBackfillWorkoutBlocking(...)` BEFORE `ExportManager.workoutFinished()` executes, holding all automatic downstream exports (Strava, Health Connect, Dropbox, `.fit`, `.tcx`, `.gpx`, `.csv`) until backfill or pending-sync fallback completes.
   - `PolarVeritySenseOfflineController.onWorkoutStopped(context)` SHALL preserve armed session metadata when armed so `endWorkout()` can execute backfill (resetting `isArmed` in `stopAndBackfillWorkout()`'s `finally` block).
   - `PolarVeritySenseOfflineController.shouldBackfill(context, baseFileName)` SHALL inspect ONLY `WorkoutSamplesDatabaseManager.hasMissingHeartRateSamples(baseFileName)` (ignoring `WorkoutRr.db`). On zero missing HR seconds (`shouldBackfill == false`), the fast path SHALL stop active `HR` recording and call `removeOfflineRecord()` for ONLY the workout's matched `PolarOfflineRecordingEntry` without downloading `HrOfflineRecording`.
   - When missing HR seconds exist, the controller SHALL pause standard GATT via `PolarOfflineGattCoordinator`, stop/list/fetch `HrOfflineRecording`, align `HrOfflineRecording.startTime` + 1-Hz `samples` (`sample.hr` / `correctedHr`) to workout timestamps, and call `WorkoutSamplesDatabaseManager.patchMissingHeartRateSamples()` to populate ONLY missing (`NULL` or `<= 0`) `SensorType.HR` rows while preserving all existing valid HR, GPS, speed, cadence, and power samples.
2. *Strict HRV Integrity & Summary Recomputation (`REQ-SET-109.2`)*:
   - `PolarVeritySenseOfflineController` MUST NEVER write synthetic RR intervals or optical PPI rows into `WorkoutRr.db` and MUST NEVER invoke `HrvPostWorkoutRecomputer`.
   - After patching `WorkoutSamples.db`, the controller SHALL recompute `SensorType.HR` summary extrema (`MIN`, `MAX`, `AVG` with count) and optional EF/decoupling into `WorkoutSummaries.db` (`TABLE_EXTREMA_VALUES`) and call `WorkoutRepository.refreshWorkout(workoutId)` so Aftermath summaries and HR zone durations (`ZoneDistributionCalculator`) update cleanly.
3. *Multi-Session Flash Safety & Aftermath Deferred Retry (`REQ-SET-109.3`)*:
   - `removeOfflineRecord()` MUST execute strictly AFTER SQLite commits succeed and delete ONLY the `PolarOfflineRecordingEntry` matching the workout's expected time window (or persisted `polarExerciseId` path), preserving unrelated recordings on the 16 MB flash.
   - When Verity Sense is unreachable/powered off or no data is retrieved when backfill is needed, the controller SHALL persist `WorkoutSummariesDatabaseManager.setPolarSyncPending(...)` tagging `polarExerciseId` with `"verity:"` prefix + matched path (zero DB schema bump).
   - `PolarH10RetrySyncBanner` (in `TrackOnMapScreen.kt` and `WorkoutDetailTabbedContent.kt`) SHALL detect `"verity:"` pending state, render localized optical Blue Sensor Mode copy across all 9 locales without emojis (*"Turn on your Polar Verity Sense in Blue Sensor Mode and tap Sync Now."*), dispatch *Sync Now* to `PolarVeritySenseOfflineController.syncPendingWorkout()`, clear pending state and remove only the matched flash entry after SQLite commit, refresh `WorkoutRepository`, and trigger `ExportManager.invalidateWorkoutExport()` + `exportWorkout()`.

**Acceptance Criteria (Given-When-Then)**:
• *Given* a workout with Verity Sense backup armed and missing `HR` seconds, *When* `TrackerService.endWorkout()` executes with sensor in range, *Then* missing 1-Hz `HR` samples and `HR` extrema/zones update before `ExportManager.workoutFinished()`, `WorkoutRr.db` has zero insertions, and only the matched flash entry is removed post-commit.
• *Given* zero missing `HR` seconds in `WorkoutSamples.db`, *When* stop-time backfill executes, *Then* `getOfflineRecord` is skipped and only the matched entry is stopped and removed.
• *Given* multiple recordings on Verity Sense 16 MB flash, *When* cleanup runs, *Then* unrelated recordings remain untouched.
• *Given* Verity Sense was powered off at stop (`polarSyncPending=1`, `verity:` tag), *When* reopened in Blue Sensor Mode and user taps *Sync Now* in Aftermath, *Then* Verity copy renders in 9 locales, `syncPendingWorkout()` backfills HR, refreshes summaries, deletes only matched entry, and clears the banner.

**Invariants**: Zero `WorkoutRr.db` / `HrvPostWorkoutRecomputer` writes; zero `WorkoutSummaries.db` schema bump; gap-only `HR` patching; post-commit single-entry deletion; zero emojis in strings.

---

### REQ-SET-110: Polar Verity Sense Swim Mode Session Reader, Turn Extraction & SYSTEM_BUSY Guidance (`ATT-8000`) [Verified]
- **Tests**: `TST-SET-112`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit / Integration
- **Implementation**: `PolarVeritySenseOfflineController.kt`, `PolarOfflineGattCoordinator.kt`, `PolarH10RetrySyncBanner.kt`, `res/values*/strings_devices.xml`
- **Rationale**: When Polar Verity Sense is operated in White Swimming Mode, it records optical 1-Hz HR and gyroscope wall turns as structured Polar Training Sessions (`PolarTrainingSessionApi`) rather than raw offline recording streams, while returning `SYSTEM_BUSY` until the swimmer exits Swim Mode back to Blue Sensor Mode.
- **Description**:
1. *PolarTrainingSessionApi Session Fetch & FTU Lifecycle (`REQ-SET-110.1`)*:
   - `PolarOfflineGattCoordinator` SHALL include `FEATURE_POLAR_TRAINING_DATA` alongside `FEATURE_POLAR_OFFLINE_RECORDING` and `FEATURE_POLAR_DEVICE_TIME_SETUP` in the offline session feature set.
   - `PolarVeritySenseOfflineController` SHALL support factory-fresh devices by verifying `isFtuDone()` and calling `doFirstTimeUse()` if unprovisioned so standalone modes operate without requiring a Polar account.
   - During `stopAndBackfillWorkout` and `syncPendingWorkout`, the controller SHALL query `PolarTrainingSessionApi.getTrainingSessionReferences()` to discover completed sessions on internal flash.
2. *Deterministic Swim Session Parsing & Turn Extraction (`REQ-SET-110.2`)*:
   - The controller SHALL inspect session metadata (`PbExerciseBase.sport.value == 103` / Pool Swimming) to deterministically identify White Swimming Mode sessions.
   - For identified swim sessions, the controller SHALL extract the 1-Hz HR series from `PbExerciseSamples.heartRateSamples` and raw wall turn/length transition timestamps from `distanceSamples` (or length event samples), decoupling turn extraction from the sensor's internal pool-length setting.
   - The controller SHALL patch missing `SensorType.HR` samples into `WorkoutSamples.db` and recompute HR extrema/zones in `WorkoutSummaries.db`.
3. *SYSTEM_BUSY Guidance, Tagged Pending State & Flash Cleanup (`REQ-SET-110.3`)*:
   - When workout stop or Aftermath *Sync Now* encounters `SYSTEM_BUSY` (swimmer still in White Swimming Mode), the controller SHALL persist pending sync state with tag `verity:swim_busy:<targetId>`.
   - `PolarH10RetrySyncBanner` SHALL detect `verity:swim_busy` state and render localized instructions across all 9 UI locales without emojis: *"Please press the button on your Polar Verity Sense to exit Swim Mode (return to blue LED) and tap Sync Now."*
   - Strictly post-commit upon successful database persistence, the controller SHALL call `PolarTrainingSessionApi.deleteTrainingSession()` for ONLY the matched training session reference, ensuring the 16 MB flash memory does not fill up.

**Acceptance Criteria (Given-When-Then)**:
• *Given* a completed White Swimming Mode session on a Polar Verity Sense switched back to Blue Sensor Mode, *When* `stopAndBackfillWorkout` or `syncPendingWorkout` executes, *Then* the session is deterministically recognized (`PbSportIdentifier == 103`), 1-Hz HR and turn timestamps are extracted, missing HR samples are patched into `WorkoutSamples.db`, and `deleteTrainingSession` removes only the matched session post-commit.
• *Given* Verity Sense is stopped while still in White Swimming Mode (`SYSTEM_BUSY`), *When* `stopAndBackfillWorkout` executes, *Then* pending sync is saved with `verity:swim_busy:`, and Aftermath renders localized guidance to exit Swim Mode and tap *Sync Now* across all 9 locales without emojis.

**Invariants**: Zero `WorkoutRr.db` writes; zero `WorkoutSummaries.db` schema bumps; gap-only `HR` patching; post-commit single-session deletion; zero emojis in strings.

---

### REQ-SET-111: Polar Verity Sense Swim Mode Pool-Length Distance/Laps Backfill, Auto SportType & Aftermath Adjustment (`ATT-8001`) [Verified]
- **Tests**: `TST-SET-113`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit / Integration
- **Implementation**: `PolarVeritySenseOfflineController.kt`, `SportTypesRepository.kt`, `LapsDatabaseManager.java`, `WorkoutSamplesDatabaseManager.java`, `WorkoutSummariesDatabaseManager.java`, `WorkoutRepository.kt`, `SwimWorkoutAdjustCard.kt`, `WorkoutDetailTabbedContent.kt`, `res/values*/strings_swimming.xml`
- **Rationale**: While ATT-8000 extracts 1-Hz HR and gyroscope wall turns, full swim integration requires automatically creating/assigning the Swimming sport type, backfilling pool-length distance, speed, and lap number into SQLite databases, holding automatic exports until commit, and allowing post-workout pool length and turn adjustments in Aftermath.
- **Description**:
1. *Automatic Swim SportType Provisioning & Assignment (`REQ-SET-111.1`)*:
   - When a White Swimming Mode session is backfilled (at workout stop or via Aftermath retry sync), the controller SHALL call `SportTypesRepository.getInstance(context).ensureSwimSportTypeExists()` to idempotently create the Swimming sport type if not already active.
   - The controller SHALL assign the Swimming sport ID and `BSportType.SWIMMING` to the workout via `WorkoutSummariesDatabaseManager` and `WorkoutRepository.updateWorkoutSport()`, overriding speed-based heuristic fallbacks (`WALK`/`OTHER`).
2. *Pool-Length Distance, Speed & Lap Backfill (`REQ-SET-111.2`)*:
   - The controller SHALL read the configured default pool length via `SettingsDataStoreJavaHelper.getDefaultPoolLengthMeters(context)`.
   - For each detected length interval (defined by consecutive `turnTimestampsMs`), the controller SHALL compute cumulative `SensorType.DISTANCE_m`, segment `SensorType.SPEED_mps`, and `SensorType.LAP_NR`, backfilling them into `WorkoutSamples.db` alongside 1-Hz `SensorType.HR`.
   - The controller SHALL persist discrete lap entries into `Laps.db` via `LapsDatabaseManager.saveLap()`, enforcing the `ATT-896` non-zero lap duration/distance guard (`lapDistance > 0 && lapTime > 0`).
   - The controller SHALL recompute workout total distance, average/max speed, and HR zones in `WorkoutSummaries.db` before releasing held automatic exports (`ExportManager`).
   - `WorkoutRr.db` MUST NOT receive any optical PPG rows (strict HRV integrity).
3. *Aftermath Pool-Length & Length-Count Adjustment (`REQ-SET-111.3`)*:
   - For Verity Sense swim workouts, Workout Details (Aftermath) SHALL render a `SwimWorkoutAdjustCard` in `WorkoutDetailTabbedContent.kt`.
   - Swimmers SHALL be able to select standard pool lengths (`25 m`, `50 m`, `25 yd`, custom) or adjust total length count (`+1` / `-1` for missed/phantom gyroscope turns).
   - Upon adjustment, the system SHALL recalculate workout total distance, average pace, lap splits, and sample streams in SQLite, refresh `WorkoutRepository`, and invalidate/trigger re-export via `ExportManager`.
4. *9-Locale Localization Parity & Invariants (`REQ-SET-111.4`)*:
   - All user-facing strings for `SwimWorkoutAdjustCard` SHALL be localized across all 9 supported application locales (`values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`) with zero inline emojis.

**Acceptance Criteria (Given-When-Then)**:
• *Given* Swimming has not yet been activated in `SportType.db`, *When* a Verity Sense White Swimming Mode session is backfilled, *Then* `ensureSwimSportTypeExists()` creates the Swimming sport type and assigns it to the workout, preventing Walking misclassification.
• *Given* configured pool length is 50 m and the sensor recorded 20 turn intervals, *When* stop backfill completes, *Then* `WorkoutSamples.db`, `Laps.db`, and `WorkoutSummaries.db` record 1000 m total distance, 20 distinct laps, and 1-Hz HR before automatic exports run, with zero writes to `WorkoutRr.db`.
• *Given* a synced swim workout in Workout Details (Aftermath), *When* the user switches pool length from 25 m to 50 m or taps `+1` length, *Then* total distance, pace, and laps recalculate immediately in SQLite and refresh in the UI across all 9 locales.

**Invariants**: Zero `WorkoutRr.db` / `HrvPostWorkoutRecomputer` writes; zero `WorkoutSummaries.db` schema bumps; automatic exports held until backfill commits; idempotent `ensureSwimSportTypeExists()`; zero emojis in strings.

---

### REQ-SET-112: Polar Live IMU Streaming Bridge, Session Arbitration & Cadence Priority (`ATT-8016`) [Verified]
- **Tests**: `TST-SET-114`, `TST-CON-049`, `TST-CON-050`
- **Priority**: High
- **Type**: Functional
- **Verification Method**: Unit / Integration / On-Device Manual
- **Implementation**: `PolarOnlineStreamingBridge.kt`, `PolarOfflineGattCoordinator.kt`, `PolarH10OfflineController.kt`, `PolarVeritySenseOfflineController.kt`, `DeviceManager.java`, `MySensorManager.java`, `DevicesDatabaseManager.java`, `DatabaseSchemaRegistry.kt`, `RawDeviceDataProvider.kt`, `DeviceData.kt`, `DeviceDataRepository.kt`, `EditDeviceViewModel.kt`, `EditDeviceDialog.kt`, `res/values*/strings_devices.xml`
- **Rationale**: Running cadence and biomechanics from Polar H10 (`ACC`) and Verity Sense (`ACC` + `GYRO`) require a single persistent `PolarBleApi` session on Android 13+ delivering live `HR`/`RR` and inertial frames simultaneously without dual-GATT contention, offline PFTP conflicts, or overwriting dedicated footpod/cycling cadence sensors.
- **Description**:
1. *Devices.db v14 Opt-In & `@RequiresApi(33)` UI Toggle (`REQ-SET-112.1`)*:
   - `DevicesDatabaseManager.DevicesDbHelper` SHALL increment `DB_VERSION` to `14`, adding an opt-in live IMU column (`DEFAULT 0`) across both `onCreate` and `onUpgrade` (`13 -> 14`), tracked automatically by `DatabaseSchemaRegistry`.
   - `RawDeviceDataProvider`, `DeviceRawData`, `DeviceUiData`, `DeviceDataRepository`, `EditDeviceViewModel`, and `EditDeviceDialog` SHALL expose the `@RequiresApi(33)` live IMU streaming toggle exclusively for Polar H10 and Verity Sense BLE HRM devices (disabled with Android 13+ hint below API 33) with 9-locale parity in `strings_devices.xml` (`values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`) and zero emojis.
2. *Persistent Single-GATT Session & Live HR/RR + ACC/GYRO Streaming (`REQ-SET-112.2`)*:
   - When live IMU is enabled on Android 13+, `DeviceManager.createNewBluetoothLEDevice()` SHALL instantiate `PolarOnlineStreamingBridge` instead of `BTLEHeartRateDevice` (`0x180D`) on the same MAC address, maintaining one persistent `PolarBleApi` session with `setAutomaticReconnection(true)`; when disabled or `< API 33`, `BTLEHeartRateDevice` SHALL remain unchanged.
   - `PolarOnlineStreamingBridge` SHALL stream `PolarOnlineStreamingApi.startHrStreaming()` (`hr`, `rrsMs`, `rrAvailable`) into `SensorType.HR`, `SensorType.RR_INTERVAL`, and `RrBeatSource` -> `HRVMetricsDevice` with cumulative beat timestamping parity to `BTLEHeartRateDevice`.
   - `PolarOnlineStreamingBridge` SHALL acquire `startAccStreaming()` (H10: 50/100/200 Hz; Verity Sense: 52 Hz) and `startGyroStreaming()` (Verity Sense Blue Sensor Mode: 52 Hz) without dual-GATT disconnects.
3. *Offline Session Coexistence & Per-Channel `ACC` Mutual Exclusion (`REQ-SET-112.3`)*:
   - `PolarOfflineGattCoordinator` SHALL support active `PolarBleApi` session reuse/handoff so `PolarH10OfflineController` and `PolarVeritySenseOfflineController` share the persistent `PolarBleApi` connection for PFTP start/stop/backfill without opening a competing GATT client.
   - `PolarOnlineStreamingBridge` and `PolarVeritySenseOfflineController` MUST enforce per-channel `ACC` mutual exclusion on Verity Sense, never starting `PolarOfflineRecordingApi` and `PolarOnlineStreamingApi` concurrently on `ACC`.
4. *`BSportType.RUN` Gating & Dedicated Cadence Priority Arbitration (`REQ-SET-112.4`)*:
   - `PolarOnlineStreamingBridge` SHALL register/publish IMU-derived `SensorType.CADENCE` / `SensorType.STRIDES` ONLY when the active sport maps to `BSportType.RUN` (including `TTSportType.WALK`), and MUST NOT emit cadence during `BSportType.BIKE` or other non-running sports.
   - `MySensorManager.CADENCE_PRIORITY` SHALL rank dedicated `RUN_SPEED`, `BIKE_CADENCE`, `BIKE_SPEED_AND_CADENCE`, and `BIKE_POWER` sensors strictly above Polar IMU cadence so dedicated ANT+/BLE footpods (`ANTRunSpeedDevice`, `BTLERunSpeedDevice`) and cycling crank sensors always retain exclusive `SensorType.CADENCE` ownership.

**Acceptance Criteria (Given-When-Then)**:
• *Given* a paired Polar H10 or Verity Sense on Android 13+ with live IMU enabled (`DEFAULT 0` opt-in), *When* a Running or Walking (`BSportType.RUN`) workout runs, *Then* `PolarOnlineStreamingBridge` maintains a single persistent `PolarBleApi` session delivering 1-Hz HR (+ RR beats on H10) and `ACC` (+ `GYRO` on Verity Sense) without dual-GATT disconnects or `ACC` PFTP conflicts.
• *Given* an active dedicated ANT+/BLE footpod (`RUN_SPEED`) or a `BSportType.BIKE` workout with/without a crank cadence sensor, *When* Polar live IMU is streaming, *Then* dedicated footpod/bike sensors retain exclusive `SensorType.CADENCE` ownership and `BSportType.BIKE` receives zero Polar IMU cadence.

**Invariants**: Standard `BTLEHeartRateDevice` unchanged when opt-in is `0` or API < 33; `Devices.db` v14 `DEFAULT 0` preserves existing user configs; Verity Sense `ACC` single-owner lock; `WorkoutRr.db` never receives optical Verity PPG rows; `HIGH-RISK` Stage 4 (`subagent_pro` Doing / `subagent_flash` Gate 4 per `Rule 7.1`); Stage 4 UI screenshot verified (`Rule 8.5`).


### REQ-SET-113: Expert Settings HRV Artifact Gate Relocation, Morning Checks Section & DFA α1 Info-Card Removal (`ATT-8117`, `ATT-8468`) [Verified]
- **Tests**: `TST-SET-115`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `AdvancedTuningAccordion.kt`, `AdvancedTuningScreen.kt`, `AdvancedTuningDialog.kt`, `SensorsGpsFilterSection.kt`, `PhysiologyModelsSection.kt`, `MorningCheckSection.kt`, `res/values*/strings_hrv.xml`
- **Rationale**: `PhysiologyModelsSection.kt` mixes physiological training models, HRV RR-interval signal filtering (`hrvArtifactGateThresholdPercent`), and 7 morning wellness check settings. Relocating the HRV Artifact Gate slider to `SensorsGpsFilterSection` and extracting the 7 Morning Check controls into a dedicated `TuningSection.MORNING_CHECK` (`MorningCheckSection`) improves thematic cohesion across both `AdvancedTuningScreen` and `@Deprecated AdvancedTuningDialog` while preserving all persisted DataStore keys (`ATT-8117`, Epic `ATT-355`, refining `REQ-UI-403`, `REQ-UI-463.2`, `REQ-UI-525.1`). Furthermore, the redundant DFA α1 Info-Card (`tuning_physiology_hrv_zones_link`), redundant dividers, and unused imports are removed from `PhysiologyModelsSection.kt` to eliminate non-actionable clutter from expert settings (`ATT-8468`).
- **Description**:
  1. *HRV Artifact Gate Relocation & DFA α1 Info-Card Removal (`REQ-SET-113.1`, [Verified] ATT-8468)*:
     - `hrvArtifactGateThresholdPercent` (`tuning_hrv_artifact_gate_title` slider, range `3.0%..5.0%`, default `5.0%`) SHALL be moved from `PhysiologyModelsSection.kt` to `SensorsGpsFilterSection.kt` (`TuningSection.SENSORS_GPS`) and wired identically from both `AdvancedTuningScreen.kt` and `@Deprecated AdvancedTuningDialog.kt` without altering persisted `TuningPreferencesDataStore` keys, defaults, or clamping bounds.
     - The static DFA α1 info-card (`R.string.tuning_physiology_hrv_zones_link`), redundant trailing `HorizontalDivider`, and unused `Card`/`CardDefaults` imports SHALL be removed from `PhysiologyModelsSection.kt` (ATT-8468), leaving a single `HorizontalDivider` separating Cardio-Power Dynamics and RPE & Feel, while `showRpeAndFeel` SHALL remain in `PhysiologyModelsSection.kt`.
     - The unreferenced string resource `tuning_physiology_hrv_zones_link` SHALL be symmetrically removed across all 9 `strings_hrv.xml` locale files (`values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`), preserving 100% 9-language translation parity.
  2. *Dedicated Morning Checks Section (`REQ-SET-113.2`)*:
     - `TuningSection` in `AdvancedTuningAccordion.kt` SHALL add `TuningSection.MORNING_CHECK` (expanding the canonical lifecycle enum to 12 entries), paired with `TuningSubtitleFormatter.formatMorningCheckSubtitle(...)` and a dedicated `MorningCheckSection` composable under `ui/settings/tuning/categories/`.
     - `MorningCheckSection` SHALL house all 7 Morning Check settings (`enableMorningSleepAndWellness`, `enableSleepDurationAndQuality`, `enableMorningEnergy`, `enableMuscleSoreness`, `enableContextTags`, `showMorningCheckOnStartScreen`, `morningCheckCutoffHour`) and SHALL be mounted symmetrically in both `AdvancedTuningScreen.kt` and `@Deprecated AdvancedTuningDialog.kt`.
  3. *Design Guidelines Compliance, Contract Synchronization & 9-Language Parity (`REQ-SET-113.3`)*:
     - `SensorsGpsFilterSection`, `PhysiologyModelsSection`, and `MorningCheckSection` MUST comply strictly with `TuningDesignGuidelinesContractTest` (`TuningToggleItem`, `SubordinateTuningToggleItem`, `TuningSliderItem`, and `HorizontalDivider` only; zero `FilterChip`, raw `Switch(`, or nested `ElevatedCard(`).
     - `PhysiologyModelsVisibilityContractTest`, `AdvancedTuningAccordionTest` (12-entry `TuningSection` enum & subtitle formatter), `AdvancedTuningModularityTest`, `AdvancedTuningScreenTest`, and `AdvancedTuningVisualContractTest` SHALL be updated consistently with the new section architecture.
     - All user-facing strings SHALL maintain 100% 9-language localization parity across `values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, and `values-pt/` with zero emojis, verified by `TranslationParityTest`.
  4. *Visual Compose Screenshot Verification (`REQ-SET-113.4`, Rule 8.5)*:
     - A Robolectric Compose screenshot test SHALL capture `docs/engineering/walkthroughs/img/ATT-8117_tuning_sections.png` verifying `SensorsGpsFilterSection` (with HRV Artifact Gate), `MorningCheckSection` (all 7 controls), and `PhysiologyModelsSection` (trimmed of Morning Check, HRV Artifact Gate, and DFA α1 info-card) against the 4-point visual checklist.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Refines `REQ-UI-403` (*Physiology & Training Models*), `REQ-UI-463.2` (*Expert Settings Thematic Grouping*), and `REQ-UI-525.1` (*Morning HRV Check Cutoff & Start Screen Toggle*) in `docs/requirements/req_ui_design_system.md`. Targets `AdvancedTuningAccordion.kt`, `AdvancedTuningScreen.kt`, `AdvancedTuningDialog.kt`, `SensorsGpsFilterSection.kt`, `PhysiologyModelsSection.kt`, and new `MorningCheckSection.kt`.
2. *Historical Origin & Commit Trace*: `ATT-7532` (`647ef674`) and `ATT-7740` (`6267d2c0`) appended Morning Check toggles/cutoff controls to `PhysiologyModelsSection.kt` alongside the HRV Artifact Gate slider (`ATT-6715`). `ATT-7317` (`fce6777b`) introduced `tuning_physiology_hrv_zones_link` as an interim breadcrumb when DFA α1 thresholds moved to Training Zones. `ATT-8117` (`f628f0a6`) extracted Morning Check and HRV Artifact Gate. `ATT-8468` removes the redundant DFA α1 info-card following PO review on Pixel 10.
3. *Root Reason for Existing Formulation*: Incremental HRV feature delivery reused `PhysiologyModelsSection` as an interim home; the DFA α1 card served as an informational notice while users transitioned to the Training Zones tab.
4. *Preservation of Core Invariants*: All `TuningPreferencesDataStore` keys, defaults (`08:00`, `6..23`, `3.0%..5.0%`), `ControlTrackingViewModel` start-screen gating, `TrackingViewModelHrvTest`, `showRpeAndFeel`, Training Zones threshold configuration (`REQ-TRK-061.4`), and `SecondaryTabbedHeadersContractTest` navigation chrome remain 100% preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* the user opens Expert Settings (`AdvancedTuningScreen` or `AdvancedTuningDialog`) and expands *Sensors, GPS & Filtering*, *When* inspecting its controls, *Then* the HRV Artifact Gate threshold slider (`tuning_hrv_artifact_gate_title`, `3.0%..5.0%`) is rendered there and persists to the unchanged DataStore key.
• *Given* the user expands the new *Morning Checks* (`TuningSection.MORNING_CHECK`) accordion section, *When* toggling wellness/start-screen options or adjusting cutoff hour, *Then* all 7 Morning Check settings are grouped there with a live subtitle summary while *Physiology & Training Models* retains training models and `showRpeAndFeel`.
• *Given* the *Physiology & Training Models* section in Expert Settings, *When* viewing the space between Cardio-Power Dynamics and RPE & Feel, *Then* the DFA α1 info-card is absent, dead `Card`/`CardDefaults` imports are removed, and exactly one `HorizontalDivider` separates the controls.
• *Given* all 9 `strings_hrv.xml` files, *When* running `TranslationParityTest`, *Then* `tuning_physiology_hrv_zones_link` is absent from all 9 files and translation parity is 100%.
• *Given* `PhysiologyModelsVisibilityContractTest`, *When* scanning `PhysiologyModelsSection.kt`, *Then* it asserts `assertFalse` that `tuning_physiology_hrv_zones_link` is present.
• *Given* all category composables under `ui/settings/tuning/categories/` and 9-locale `strings*.xml` files, *When* running contract and localization tests, *Then* `TuningDesignGuidelinesContractTest`, `PhysiologyModelsVisibilityContractTest`, `AdvancedTuningAccordionTest` (12 entries), `AdvancedTuningModularityTest`, `AdvancedTuningScreenTest`, `TranslationParityTest`, and Rule 8.5 Compose PNG inspection pass cleanly.

**Invariants**: Zero `TuningPreferencesDataStore` key/default/bound changes; zero `FilterChip`, raw `Switch(`, or nested `ElevatedCard(` in tuning categories; 100% 9-language parity with zero emojis; dual-host symmetry across `AdvancedTuningScreen` and `AdvancedTuningDialog`.

---

### REQ-SET-115: Supporter Tier Self-Selection Anchoring, Garage-Match Badge & Post-Onboarding Engagement-Gated Garage Nudges (`ATT-8108`) [Verified]
- **Tests**: `TST-SET-117`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit, Integration
- **Implementation**: `GarageLeagueDetector.kt`, `SettingsDataStore.kt`, `MainActivityWithNavigation.kt`, `ATrainingTrackerApp.kt`, `SupporterScreen.kt`, `SupporterComponents.kt`, `SupporterDebugSection.kt`, `res/values*/strings_monetization.xml`
- **Rationale**: Premature Day-0 N+1 garage nudges burn permanent single-shot suppression flags before the athlete completes a workout, while unframed top-down Supporter cards anchor athletes on `10 €` Bronze instead of self-selecting by fairness, budget, and garage scale (`ATT-8108`, Epic `ATT-355`, amending `REQ-SET-088.3`, `REQ-SET-088.4` and extending `REQ-SET-086`, `REQ-UI-501`).
- **Description**:
  1. *Warm-Engagement Workout Gate & Bounded Seasonal Cooldown (`REQ-SET-115.1`)*:
     - `GarageNudgeEvaluator.shouldTriggerNudge(...)` and `GarageLeagueDetector.evaluateNudge()` SHALL require `finishedWorkoutCount >= MIN_FINISHED_WORKOUTS_FOR_NUDGE` (`8` finished workouts via `WorkoutSummariesDatabaseManager.getFinishedWorkoutCount()`) before triggering any Gold or Diamond `GarageUpgradeNudgeDialog`. Pairing sensors (`DeviceListViewModel`) or adding equipment (`EquipmentViewModel`) with `< 8` finished workouts MUST NOT consume nudge quota or mutate cooldown persistence.
     - Permanent single-shot boolean suppression (`hasShownGoldGarageNudge`, `hasShownDiamondGarageNudge`) SHALL be replaced with bounded seasonal cooldown state in `SettingsDataStore` (`lastGoldNudgeTimestampMs`, `goldNudgeCount`, `lastGoldNudgeWorkoutCount`, plus analogous Diamond keys; `MAX_NUDGES_PER_LEAGUE = 3`, allowing 1 initial nudge + up to 2 follow-up reminders). Follow-up reminders for an un-upgraded league SHALL trigger only when **both** `(nowMs - lastNudgeTimestampMs) >= 180 days` **and** `(finishedWorkoutCount - lastNudgeWorkoutCount) >= 25` additional finished workouts have elapsed.
     - Legacy users with `hasShownGoldGarageNudge == true` or `hasShownDiamondGarageNudge == true` and zero new count keys SHALL migrate safely with `nudgeCount >= 1` and cooldown anchored so no immediate re-nudge fires before `180 days` AND `25` additional workouts.
     - `SensorQuotaReconciliationManager` (`MAX_FREE_PAIRED_DEVICES = 2`) free-tier sensor quota enforcement and swap/downgrade dialogs SHALL remain untouched and truthful regardless of finished workout count.
     - `SupporterDebugSection` (`BuildConfig.DEBUG`) reset action (`SettingsDataStore.resetGarageNudgeFlags()`) SHALL clear both legacy flags and all new cooldown timestamps, nudge counts, and workout-count baselines.
  2. *Post-Workout Nudge Evaluation & Jubilee Priority Arbitration (`REQ-SET-115.2`)*:
     - Upon `TrackerService.TRACKING_FINISHED_INTENT` completion in `MainActivityWithNavigation.mTrackingStoppedReceiver` / `ATrainingTrackerApp`, the system SHALL asynchronously evaluate `GarageLeagueDetector.evaluateNudge()` so Day-0-paired athletes are nudged cleanly upon crossing `finishedWorkoutCount >= 8`.
     - Post-workout garage nudge presentation MUST strictly yield to and remain suppressed whenever `FirstJubileeCelebrationDialog` (`firstJubileeCelebrationState != null`, `REQ-REV-003`) or `SensorQuotaReconciliationManager` (`keepTwoSensorsEvent != null`, `REQ-SET-084.5`) is scheduled or active in that session.
  3. *Supporter Tier Self-Selection Subtitles, Garage-Match Badge & Detekt Modularity (`REQ-SET-115.3`)*:
     - `SupporterPlanCard` (`SupporterComponents.kt`, `SupporterScreen.kt`) SHALL display a concise 1–2 line localized fairness/persona subtitle for each of the 5 unchanged `SupporterBasePlan.PLANS` (`bronze-monthly`, `bronze-yearly`, `silver-yearly` retaining `isRecommended = true` Empfehlung/Recommended anchor above title when `effectiveTier.rank < SILVER.rank`, `gold-yearly`, `diamond-yearly`) without modifying Google Play SKUs or prices (`REQ-SET-086.1`).
     - When `GarageNudgeEvaluator.evaluateLeague()` qualifies `GOLD` or `DIAMOND` above the athlete's `effectiveTier`, `SupporterScreen` SHALL highlight the matching `gold-yearly` or `diamond-yearly` card with a subtle `"Matches your garage"` / `"Passend zu deinem Fuhrpark"` badge (`supporter_garage_match_badge`).
     - `SupporterPlanCard` SHALL extract cohesive helper subcomposables to resolve the `Detekt` `LongMethod` suppression (`app/detekt-baseline.xml`) while strictly enforcing `Rule 38` (`maxLines = 1, softWrap = false` on plan action buttons) and `Rule 73` (stacked full-width buttons in `GarageUpgradeNudgeDialog`).
  4. *9-Locale Localization Parity, Zero-Emoji Hygiene & Rule 8.5 Visual Compose Verification (`REQ-SET-115.4`)*:
     - All 5 persona subtitle strings and `supporter_garage_match_badge` SHALL be partitioned into `res/values*/strings_monetization.xml` across all 9 locales (`values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`) with zero decorative inline Unicode emojis (`ATT-5400`), zero defensive buzzwords (`ATT-5659`), and matching format specifiers.
     - Stage 4 SHALL render a Robolectric Compose PNG screenshot (`docs/engineering/walkthroughs/img/ATT-8108_supporter_cards.png`) of `SupporterPlanCard` states (persona subtitles, Silver anchor, Gold/Diamond Garage-Match badge) and verify it via `read_file` against the 4-point visual checklist (`Rule 8.5`).

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Amends `REQ-SET-088` (`REQ-SET-088.3`, `REQ-SET-088.4`) and extends `REQ-SET-086` (`REQ-SET-086.4/.5`) and `REQ-UI-501` (`SupporterPlanCard`). Targets `GarageLeagueDetector.kt`, `SettingsDataStore.kt`, `MainActivityWithNavigation.kt`, `ATrainingTrackerApp.kt`, `SupporterScreen.kt`, `SupporterComponents.kt`, `SupporterDebugSection.kt`, and `res/values*/strings_monetization.xml`.
2. *Historical Origin & Commit Trace*: `de0e1d55` (`ATT-51`), `3b4e0a20` (`ATT-5299`), `30ab5df4` (`ATT-5468`), `be746cdd` (`ATT-5570`), `7d72425c` (`ATT-6286`).
3. *Root Reason for Existing Formulation*: Boolean `hasShown*` flags prevented repeated dialog spam during consecutive device/equipment edits, and minimal plan cards avoided paywall complexity.
4. *Preservation of Core Invariants*: All 5 `SupporterBasePlan.PLANS` SKUs/prices (`REQ-SET-086.1`), `evaluateLeague()` thresholds & Strava Pro boost (`REQ-SET-088.1`), tier monotonicity (`REQ-SET-088.2`), `SensorQuotaReconciliationManager` (`REQ-SET-084.5`), `Rule 38` single-line buttons, `Rule 73` stacked dialog buttons, `ATT-5400` zero emojis, `ATT-5659` plain appreciative wording, and `REQ-REV-003` Jubilee precedence remain 100% preserved.

**Acceptance Criteria (Given-When-Then)**:
• *Given* a new athlete on `FREE` or `BRONZE` with `< 8` finished workouts, *When* they pair a power meter or add 3 bikes, *Then* `GarageUpgradeNudgeDialog` is NOT triggered and no nudge quota or cooldown state is consumed.
• *Given* a `BRONZE` athlete with a Gold-qualified garage reaching `>= 8` finished workouts on a session without `FirstJubileeCelebrationDialog` or quota reconciliation, *When* the workout finishes, *Then* `GarageUpgradeNudgeDialog` is presented; if dismissed, it stays silent until `>= 180 days` AND `>= 25` additional finished workouts have elapsed (up to `MAX_NUDGES_PER_LEAGUE = 3`).
• *Given* an athlete opening `SupporterScreen` across any of the 9 supported locales, *When* all 5 `SupporterPlanCard` items render, *Then* each card displays its localized zero-emoji persona subtitle without button wrapping (`Rule 38`), `silver-yearly` retains its Recommended anchor below Silver, and a Gold/Diamond-qualified athlete below that tier sees `"Matches your garage"` on the matching card.

**Invariants**: 5 Play base plan IDs and prices untouched; `SensorQuotaReconciliationManager` quota truthful; `Rule 38` & `Rule 73` enforced; zero emojis across 9 locales (`ATT-5400`); `FirstJubileeCelebrationDialog` never collides with garage nudge; `SupporterPlanCard` Detekt `LongMethod` eliminated.






---

### REQ-SET-114: Polar Live Running Cadence & Stride Estimation from H10 (`ACC`) and Verity Sense (`ACC`/`GYRO`) (`ATT-8017`) [Verified]
- **Tests**: `TST-SET-116`
- **Priority**: High
- **Type**: Functional
- **Verification Method**: Unit / Integration
- **Implementation**: `PolarLiveRunningCadenceEstimator.kt`, `PolarOnlineStreamingBridge.kt`, `docs/architecture.md`
- **Rationale**: Runners wearing a Polar H10 chest strap or Polar Verity Sense armband without a separate footpod (including treadmill running with a stationary smartphone) require accurate 1-Hz running cadence and cumulative stride estimation derived from live PMD `ACC`/`GYRO` frames in canonical FIT/ANT+/BLE strides/min units.
- **Description**:
1. *Canonical Strides/Min & Stride-Cycle Unit Parity (`REQ-SET-114.1`)*:
   - `PolarLiveRunningCadenceEstimator` and `PolarOnlineStreamingBridge` SHALL publish `SensorType.CADENCE` strictly in **strides/min** (full 2-step cycles/min, e.g., `60–100` strides/min corresponding to `120–200` steps/min), matching `ANTRunSpeedDevice`, `BTLERunSpeedDevice` (`0x2A53`), and `FitFileWriter` (`RecordMesg.cadence` / `SessionMesg.avgCadence` `UINT8` scaling where `85–90` strides/min displays as `170–180` spm on Strava).
   - `PolarOnlineStreamingBridge.syncCadenceSensorRegistration()` SHALL seed `mStridesSensor.accumulate(0)` upon `BSportType.RUN` sensor registration (`MyAccumulatorSensor` baseline parity) so the very first 2-step stride cycle increments `SensorType.STRIDES` to `1` without being swallowed as the baseline.
2. *Polar H10 Sternum `ACC` Cadence Estimator (`REQ-SET-114.2`)*:
   - For Polar H10 `ACC` streams (50–100/200 Hz), `PolarLiveRunningCadenceEstimator` SHALL apply a `1.2–4.0 Hz` band-pass filter (`72–240` steps/min) on acceleration magnitude combined with adaptive foot-strike peak detection and a sliding `5s` autocorrelation estimator to track fundamental stride frequency without harmonic doubling/halving.
3. *Polar Verity Sense Armband `GYRO+ACC` Fusion Estimator (`REQ-SET-114.3`)*:
   - For Polar Verity Sense `ACC` + `GYRO` streams (52 Hz), `PolarLiveRunningCadenceEstimator` SHALL fuse orientation-invariant angular-rate (`GYRO` deg/s) and `ACC` arm-swing envelope signals across a `0.6–2.0 Hz` band-pass (`36–120` strides/min = `72–240` steps/min), remaining immune to armband rotation around the bicep/forearm.
4. *1-Hz Cadence Emission, `>= 3s` Standstill Zeroing & Arbitration (`REQ-SET-114.4`)*:
   - `PolarLiveRunningCadenceEstimator` and `PolarOnlineStreamingBridge` SHALL emit steady `1 Hz` `SensorType.CADENCE` updates within `±2` strides/min across `60–100` strides/min steady running/walking (`BSportType.RUN`, including `TTSportType.WALK`), and SHALL drop `SensorType.CADENCE` cleanly to `0.0` via motion-energy gating within `>= 3s` of standstill.

**Acceptance Criteria (Given-When-Then)**:
• *Given* steady running or walking between `60–100` strides/min (`120–200` steps/min) on Polar H10 (`ACC`) or Polar Verity Sense (`ACC+GYRO`, any armband orientation), *When* processed by `PolarLiveRunningCadenceEstimator` and `PolarOnlineStreamingBridge`, *Then* `SensorType.CADENCE` updates at `1 Hz` within `±2` strides/min, `SensorType.STRIDES` increments from stride `1` (`accumulate(0)` seeded), and `FitFileWriter` exports matching strides/min (`~85–90`).
• *Given* the runner stops moving during an active run, *When* standstill persists for `>= 3s`, *Then* motion-energy gating drops `SensorType.CADENCE` cleanly to `0.0`.

**Invariants**: Canonical strides/min (`CADENCE` `36–120` active or `0.0` standstill); `BSportType.RUN`-only gating & `MySensorManager.CADENCE_PRIORITY` (`RUN_SPEED`/`BIKE_*` > `HRM`) preserved; zero UI/DB/exporter/`ANT-Android-SDKs` edits; `HIGH-RISK` Stage 4 (`subagent_pro` Doing / `subagent_flash` Gate 4 per `Rule 7.1`, non-UI DSP engine).
---

### REQ-SET-116: Polar H10 200 Hz Live Running Dynamics (Vertical Oscillation & Ground Contact Time) & FIT Export (`ATT-8018`) [Verified]
- **Tests**: `TST-SET-118`
- **Priority**: High
- **Type**: Functional
- **Verification Method**: Unit / Integration
- **Implementation**: `PolarH10RunningDynamicsEstimator.kt`, `PolarOnlineStreamingBridge.kt`, `SensorType.java`, `ActivityType.java`, `TrackerService.java`, `WorkoutRepository.kt`, `FitFileWriter.kt`, `docs/architecture.md`
- **Rationale**: Wearing a Polar H10 chest strap at 200 Hz (5 ms resolution) near the runner's center of mass provides sternum vertical acceleration transients capable of computing live Vertical Oscillation (`cm`) and Ground Contact Time (`ms`) for Cockpit telemetry, 1-Hz `WorkoutSamples.db`/`WorkoutSummaries.db` persistence, and native Garmin FIT Running Dynamics export without proprietary hardware.
- **Description**:
1. *H10-Only 200 Hz `BSportType.RUN` Activation & Safe Shutdown (`REQ-SET-116.1`)*:
   - `PolarOnlineStreamingBridge` SHALL activate `highRateRunningDynamicsActive = true` and negotiate `H10_HIGH_RATE_ACC_HZ` (`200 Hz`, graceful fallback to `100/50 Hz`) exclusively for Polar H10 (`!isVeritySense`) during `BSportType.RUN`, MUST keep Verity Sense (`52 Hz`) and non-running sports unchanged, and MUST reset estimator state cleanly on disconnect, sport switch, or `shutDown()`.
2. *200 Hz Sternum Biomechanics DSP, Physiological Bounds & Walking Guard (`REQ-SET-116.2`)*:
   - `PolarH10RunningDynamicsEstimator` SHALL continuously align the trunk vertical axis using a `~0.25 Hz` low-pass gravity estimator and project `200 Hz` `ACC` onto that vertical axis.
   - Over each running step interval `T_step`, it SHALL apply zero-mean detrended double-integration to compute peak-to-trough **Vertical Oscillation** (`cm`, bounded within physiological `5.0..13.0 cm` plausibility and smoothed across a `5–8` step rolling window without cumulative integration drift).
   - It SHALL detect foot-strike impact peak/jerk onset to toe-off upward-acceleration inflection at `5 ms` resolution for **Ground Contact Time** (`ms`, bounded within `140..400 ms`), and SHALL suppress GCT output during walking (`cadence < 130 steps/min`, i.e., `< 65 strides/min`) and zero/clear state cleanly on standstill (`>= 2–3 s`) or lower sample rates.
3. *Sensor Channels, Persistence, Aftermath Extrema & 9-Language Parity (`REQ-SET-116.3`)*:
   - Register `SensorType.VERTICAL_OSCILLATION` (`cm`, `units_cm`, `DOUBLE`) and `SensorType.GROUND_CONTACT_TIME` (`ms`, `units_time_ms`, `DOUBLE`) under `SensorCategory.SPEED_PACE_AND_DISTANCE`, wire them into `ActivityType.RUN_SPEED_AND_CADENCE`, `DeviceType.HRM`, `TrackerService`, `WorkoutDataMapper`, and `WorkoutRepository` (`streamSamples()`/`calculateAndSaveExtrema()`) with zero SQLite schema bumps, pass `SensorCategoryAndSelectorContractTest`, and provide full 9-language localization parity (`EN, DE, ES, FR, IT, JA, NL, PL, PT`) for display names and `cm`/`ms` units.
4. *Native Garmin FIT SDK Unit Scaling (`REQ-SET-116.4`)*:
   - `FitFileWriter.kt` SHALL convert Vertical Oscillation from `cm` to FIT SDK **millimeters (`mm`)** (`cm * 10f`) on `RecordMesg.setVerticalOscillation`, `LapMesg.setAvgVerticalOscillation`, and `SessionMesg.setAvgVerticalOscillation`, and write Ground Contact Time in **milliseconds (`ms`)** via `RecordMesg.setStanceTime`, `LapMesg.setAvgStanceTime`, and `SessionMesg.setAvgStanceTime`.

**Acceptance Criteria (Given-When-Then)**:
• *Given* `200 Hz` Polar H10 `ACC` streams across tilted chest orientations (`150–195 spm`) and walking (`< 130 spm`), *When* processed by `PolarH10RunningDynamicsEstimator`, *Then* running yields drift-free rolling Vertical Oscillation (`5.0..13.0 cm`) and GCT (`140..400 ms`), while walking (`< 130 spm`) cleanly suppresses GCT without invalid output.
• *Given* a recorded run with Polar H10 Running Dynamics, *When* persisted to `WorkoutSamples.db`/`WorkoutSummaries.db` and exported via `FitFileWriter`, *Then* Cockpit/Aftermath reflect `cm` and `ms` metrics, all 9 `values*/strings*.xml` files pass `TranslationParityTest`, and `FitFileWriter` encodes `mm` (`cm * 10f`) and `ms` on `RecordMesg`, `LapMesg`, and `SessionMesg`.

**Invariants**: Cross-lane ID collision guard skips `REQ-SET-115` (`ATT-8108`) -> uses `REQ-SET-116` (`TST-SET-118`); zero DB schema bumps; `FitFileWriter` strictly scales `cm * 10f -> mm`; walking (`< 130 spm`) emits no GCT; `ATT-8017` cadence (`strides/min`) & `CADENCE_PRIORITY` preserved; `HIGH-RISK` Role Rotation (`agent1` Flash: S1, G2+S3, G4+S5; `agent2` Pro: G1+S2, G3+S4, G5+merge).

---

### REQ-SET-117: Polar Raw Telemetry GZIP Sidecar Persistence & Storage Retention (`ATT-8019`) [Verified]
- **Tests**: `TST-SET-119`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit / Integration
- **Implementation**: `PolarRawTelemetryRecorder.kt`, `PolarRawTelemetryReader.kt`, `PolarRawTelemetryPruner.kt`, `RawSensorRetentionPolicy.kt`, `PolarOnlineStreamingBridge.kt`, `PolarH10OfflineController.kt`, `TrackerService.java`, `WorkoutSummariesDatabaseManager.java`, `WorkoutRepository.kt`, `TuningPreferencesDataStore.kt`, `SensorsGpsFilterSection.kt`, `docs/architecture.md`
- **Rationale**: High-frequency Polar PMD streaming (50–200 Hz ACC / 26–52 Hz GYRO + HR/RR) and offline exercise sync generate ~720k frames/hour, which would bloat the 1-Hz WorkoutSamples.db by tens of MBs; persisting compressed streaming GZIP CSV sidecars outside SQLite with rolling retention preserves database performance while capturing lossless telemetry for diagnostics and R&D.
- **Description**:
1. *Buffered Streaming GZIP Sidecar Writer (`REQ-SET-117.1`)*:
   - `PolarRawTelemetryRecorder` SHALL persist raw live PMD (`ATT-8016`) and offline sync (`ATT-7918`) sensor frames directly into a compressed file `files/raw_sensor/<fileBaseName>.polar_raw.csv.gz` external to `WorkoutSamples.db`.
   - Recording SHALL stream through a non-blocking bounded coroutine buffer `Channel<RawTelemetryFrame>(capacity = 8192)` running on `Dispatchers.IO`, ensuring BLE GATT and PMD listener callbacks never stall or drop frames due to disk I/O latency.
   - If a workout session ends with zero frames recorded or is discarded, `PolarRawTelemetryRecorder` SHALL delete the empty sidecar file and leave zero orphan files on disk.
2. *Self-Describing Header & Monotonic Multi-Channel Schema (`REQ-SET-117.2`)*:
   - The sidecar file SHALL begin with a metadata comment header `# metadata: {"schemaVersion":1,"appVersion":"...","sensorModel":"...","firmwareVersion":"...","streamMode":"LIVE|OFFLINE","accRateHz":...,"gyroRateHz":...,"sportId":...,"poolLength_m":...}` followed by the CSV column header:
     `sensorTimestampNs,elapsedMs,channel,ax_mg,ay_mg,az_mg,gx_mdps,gy_mdps,gz_mdps,hr_bpm,rr_ms`.
   - Monotonic hardware `sensorTimestampNs` and session-relative `elapsedMs` SHALL be preserved alongside discrete telemetry channels (`ACC`, `GYRO`, `HR`, `RR`).
   - A deterministic parser `PolarRawTelemetryReader` SHALL validate metadata integrity, parse frame channels, and handle corrupt or truncated GZIP archives gracefully.
3. *Workout Cascade Deletion & Off-Thread Rolling Retention (`REQ-SET-117.3`)*:
   - Workout deletion in `WorkoutSummariesDatabaseManager.deleteWorkout` and `WorkoutRepository.deleteWorkoutCascade` SHALL hook into sidecar storage and delete the corresponding `<fileBaseName>.polar_raw.csv.gz` file alongside database rows, preserving `REQ-DAT-042`.
   - `PolarRawTelemetryPruner` SHALL enforce a user-configurable rolling retention policy (`RawSensorRetentionPolicy`: `DAYS_30` [default], `KEEP_ALL`, `DAYS_7`, `OFF`) off the main thread, pruning unpinned expired sidecar files without deleting associated workout database records.
4. *M3 Settings Selector & 9-Language Localization Parity (`REQ-SET-117.4`)*:
   - `SensorsGpsFilterSection` in Compose Settings SHALL provide a Material 3 segmented button row or selector for `RawSensorRetentionPolicy` (`30 days` [default], `Keep all`, `7 days`, `Off`).
   - All string resources across all 9 supported locales (`EN`, `DE`, `ES`, `FR`, `IT`, `JA`, `NL`, `PL`, `PT`) SHALL be defined in `strings_devices.xml` with zero missing entries, matching format specifiers, and zero emojis (`Rule 38`, `ATT-5400`).
   - Stage 4 SHALL generate and inspect the verification screenshot `docs/engineering/walkthroughs/img/ATT-8019_raw_telemetry_settings.png` (`Rule 8.5`).

**Acceptance Criteria (Given-When-Then)**:
• *Given* an active Polar PMD streaming or offline sync session during a tracked workout, *When* `PolarRawTelemetryRecorder` receives high-frequency ACC/GYRO/HR/RR frames, *Then* frames are buffered via `Channel(capacity = 8192)` and written to `files/raw_sensor/<fileBaseName>.polar_raw.csv.gz` without blocking GATT callbacks, while `WorkoutSamples.db` remains strictly at 1-Hz sampling.
• *Given* an existing `.polar_raw.csv.gz` sidecar file, *When* read via `PolarRawTelemetryReader`, *Then* metadata JSON header and all frame channels parse deterministically with exact timestamp fidelity.
• *Given* an athlete deletes a workout from History or repository, *When* `WorkoutSummariesDatabaseManager.deleteWorkout` executes, *Then* the sidecar `.polar_raw.csv.gz` file is deleted concurrently.
• *Given* sidecars exceeding the retention threshold (`30 days`, `7 days`, or `Off`), *When* `PolarRawTelemetryPruner` executes, *Then* unpinned expired sidecars are deleted off the main thread while workout database summaries are preserved.
• *Given* the athlete opens Sensors & GPS Settings across any of the 9 locales, *When* `SensorsGpsFilterSection` renders, *Then* the retention selector displays localized labels (`30 days`, `Keep all`, `7 days`, `Off`) without UI clipping (`ATT-8019_raw_telemetry_settings.png`).

**Invariants**: `WorkoutSamples.db` strictly retains 1-Hz sampling with zero schema modifications; BLE GATT loops never block on file I/O; empty sessions leave zero orphan files; `ANT-Android-SDKs` submodule remains untouched at `160000 commit 0e8cea714a231fb9565170d4ed01eafa868f9798`; Role Rotation (`agent2` Pro: S2, S4, Gate 5).

---

### REQ-SET-118: Process-Wide Ref-Counted `PolarBleApi` Singleton, Multiplexed Callbacks & Unworn-Sensor Gating (`ATT-8690`) [Verified]
- **Tests**: `TST-SET-120`, `TST-CON-049`
- **Priority**: Critical
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `PolarOfflineGattCoordinator.kt`, `PolarOnlineStreamingBridge.kt`, `PolarH10OfflineController.kt`, `PolarVeritySenseOfflineController.kt`
- **Rationale**: `polar-ble-sdk` enforces a strict exact-feature-set process-wide singleton in `BDBleApiImpl$Companion.getInstance()` (`PolarBleSdkInstanceException` on mismatched subsets) and clears all active sessions inside `api.cleanup()`. Centralizing a single 8-feature superset `PolarBleApi` with ref-counted lifecycle, normalized MAC/8-hex callback multiplexing, and active-HRM unworn-sensor gating prevents singleton collisions, callback clobbering, and sibling teardown (`ATT-8690`, refining `REQ-SET-104.4`, `REQ-SET-108.2`, `REQ-SET-109.1`, `REQ-SET-110.1`, `REQ-SET-112.2`/`.3`, `REQ-CON-045.2`/`.4`).
- **Description**:
  1. *Process-Wide 8-Feature Superset Singleton (`REQ-SET-118.1`)*:
     - `PolarOfflineGattCoordinator` SHALL manage one process-wide `PolarBleApi` singleton initialized once with the 8-feature superset (`FEATURE_HR`, `FEATURE_POLAR_ONLINE_STREAMING`, `FEATURE_BATTERY_INFO`, `FEATURE_DEVICE_INFO`, `FEATURE_POLAR_DEVICE_TIME_SETUP`, `FEATURE_POLAR_H10_EXERCISE_RECORDING`, `FEATURE_POLAR_OFFLINE_RECORDING`, `FEATURE_POLAR_TRAINING_DATA`).
     - `PolarOnlineStreamingBridge`, `PolarH10OfflineController` (including `stopAndBackfillWorkout` and `syncPendingWorkout`), and `PolarVeritySenseOfflineController` MUST acquire the shared `PolarBleApi` via `PolarOfflineGattCoordinator` with zero duplicate or subset `PolarBleApiDefaultImpl.defaultImplementation(...)` calls in production code, while preserving `apiOverride`/`apiFactory` test seams.
  2. *Multiplexed Callbacks & Bidirectional MAC/8-Hex ID Matching (`REQ-SET-118.2`)*:
     - `PolarOfflineGattCoordinator` SHALL install a single multiplexed `PolarBleApiCallbackProvider` on the shared `PolarBleApi` and route events (`deviceConnected`, `deviceConnecting`, `deviceDisconnected`, `bleSdkFeatureReady`, `batteryLevelReceived`, `hrNotificationReceived`, `disInformationReceived`, `blePowerStateChanged`) to registered listeners matching either the normalized MAC address or normalized 8-hex Polar ID bidirectionally, eliminating `api.setApiCallback(...)` clobbering across bridges and concurrent `awaitFeaturesReady` callers.
     - `PolarOnlineStreamingBridge.sdkCallback` MUST filter `deviceConnected` and `deviceDisconnected` callbacks by its target device (`MAC` / `8-hex Polar ID`) so sibling Polar device events do not mutate unrelated bridge state.
  3. *Ref-Counted Lifecycle & Non-Destructive Release (`REQ-SET-118.3`)*:
     - `PolarOfflineGattCoordinator` SHALL reference-count active holders (live bridges and offline operations) and MUST NOT invoke `api.cleanup()` or `api.shutDown()` while sibling controllers or bridges hold active references (`refCount > 0`), preserving `getActiveSession(polarId)` non-null while connected and `null` post-`shutDown()`.
  4. *Active-Device / Unworn-Sensor Gating (`REQ-SET-118.4`)*:
     - `findEnabledPolarDevice()` SHALL skip unworn/disconnected Polar offline arming when another Polar HRM is actively receiving data (`DeviceManager`), while preserving compatibility with unit tests using relaxed `DevicesDatabaseManager` / `DeviceManager` mocks (including `null` `DeviceManager`).

**Acceptance Criteria (Given-When-Then)**:
• *Given* both a paired Polar H10 and Polar Verity Sense, *When* live streaming and offline arming/backfill/sync execute concurrently or sequentially across `PolarOnlineStreamingBridge`, `PolarH10OfflineController`, and `PolarVeritySenseOfflineController`, *Then* only one 8-feature superset `PolarBleApi` is created with zero `PolarBleSdkInstanceException` and zero subset `defaultImplementation` calls.
• *Given* two registered listeners (`MAC` vs `8-hex Polar ID`) or concurrent `awaitFeaturesReady` callers on the shared `PolarBleApi`, *When* SDK callbacks fire for one device, *Then* only matching listeners receive `deviceConnected`/`deviceDisconnected`/`bleSdkFeatureReady` without clobbering sibling callbacks.
• *Given* an active `PolarOnlineStreamingBridge` and an offline controller holding shared references, *When* one holder releases or completes an offline operation, *Then* `api.cleanup()`/`shutDown()` is NOT invoked while `refCount > 0`.
• *Given* an active receiving Polar HRM in `DeviceManager`, *When* `findEnabledPolarDevice()` evaluates an unworn secondary Polar sensor, *Then* offline arming skips the unworn sensor; *Given* `DeviceManager == null` or relaxed DB mocks in unit tests, *Then* `findEnabledPolarDevice()` preserves existing test resolution.

**Invariants**: `getActiveSession(polarId)` non-null while bridge connected and `null` post-`shutDown()`; `withGattYieldAndPolarApi` never tears down active bridge sessions; Verity Sense `ACC` mutual exclusion (`REQ-SET-112.3`) and `BTLEHeartRateDevice` `< API 33` fallback preserved; `apiOverride`/`apiFactory` seams preserved; zero UI strings or DB schema bumps.

---

### REQ-SET-119: Polar Aftermath Retry Sync Lifecycle Safety & Disconnected Stop-Time Fast-Fail (`ATT-8969`) [Verified]
- **Tests**: `TST-SET-121`
- **Priority**: High
- **Type**: Functional
- **Verification Method**: Unit, Integration
- **Implementation**: `PolarRetrySyncCoordinator.kt`, `PolarH10RetrySyncBanner.kt`, `PolarVeritySenseOfflineController.kt`, `PolarH10OfflineController.kt`, `PolarOfflineGattCoordinator.kt`, `TrackOnMapAftermathViewModel.kt`, `WorkoutSummariesTabbedScreen.kt`, `WorkoutSummariesListFragment.kt`
- **Rationale**: Launching `syncPendingPolarWorkout()` inside Compose `rememberCoroutineScope()` with local `remember` state throws `ForgottenCoroutineScopeException` and flickers the banner when it leaves composition during recomposition or navigation, while `catch (t: Throwable)` swallows `CancellationException` and `TrackOnMapAftermathViewModel` keeps stale HR telemetry after deferred sync. Furthermore, waiting `30–45 s` in `api.waitForConnection(...)` on a disconnected sensor blocks `TrackerService.endWorkout()` and delays the Edit Workout dialog (`ATT-8969`, refining `REQ-SET-109.1`/`.3`, `REQ-SET-106.3`, `REQ-SET-105.2`).
- **Description**:
  1. *Non-Composition Retry Sync Scope & State Hoisting (`REQ-SET-119.1`)*:
     - `PolarPendingRetrySyncSection` MUST NOT execute `syncPendingPolarWorkout(...)` on Compose `rememberCoroutineScope()`; tapping *Sync Now* SHALL dispatch onto a lifecycle-safe non-composition `CoroutineScope` (`Dispatchers.IO + SupervisorJob`) outside the composable tree, deduplicating concurrent sync requests per `workoutId`.
     - Sync progress (`isSyncing`), failure copy (`errorMessage`), and completion events SHALL be observed from reactive state keyed by `workoutId` so banner disposal/re-entry never throws `ForgottenCoroutineScopeException` or flickers.
     - `PolarVeritySenseOfflineController` and `PolarH10OfflineController` `catch (t: Throwable)` blocks inside suspend stop/sync paths MUST rethrow `CancellationException`.
  2. *Aftermath HR Telemetry Graph Refresh on Deferred Sync (`REQ-SET-119.2`)*:
     - Upon deferred Polar backfill completion for the active workout, `WorkoutSummariesTabbedScreen` and `WorkoutSummariesListFragment` (`TrackOnMapAftermathViewModel`) SHALL trigger a reload of detail telemetry (`telemetryPath`, `tracks`, `hrZoneDistribution`) without modifying `WorkoutRepository.kt` (Lane A isolation), updating the Workout Details HR graph cleanly.
  3. *Disconnected Stop-Time Fast-Fail & Bounded Connection Wait (`REQ-SET-119.3`)*:
     - During `stopAndBackfillWorkout()` in `PolarVeritySenseOfflineController` and `PolarH10OfflineController`, when `apiOverride == null` and `BANALService.getDeviceManager()` confirms the target Polar device is disconnected (`!isReceivingData()` and `!PolarOfflineGattCoordinator.isDeviceConnected(...)`), stop-time backfill SHALL fast-fail immediately without calling `api.waitForConnection(...)`, persisting `setPolarSyncPending(...)` when `needsBackfill == true`, clearing armed state, and releasing `TrackerService.endWorkout()` promptly.
     - When a stop-time connection attempt IS executed (`apiOverride != null`, `DeviceManager == null`, or device not confirmed disconnected), `api.connectToDevice(...)` + `api.waitForConnection(...)` SHALL be bounded by `STOP_CONNECTION_TIMEOUT_MS = 5_000L` independent of `BACKFILL_TIMEOUT_MS` (`45 s` / `30 s`), preserving full PFTP transfer time once connected and preserving `apiOverride` unit-test seams.
     - `TrackerService.java` MUST remain `<= 3,556` LoC (helper logic in Kotlin controllers/helpers); zero new UI strings are required (`res/values*/strings_devices.xml` reused across all 9 locales).

**Acceptance Criteria (Given-When-Then)**:
• *Given* a workout with `polarSyncPending = true` in Aftermath, *When* the user taps *Sync Now* and `PolarPendingRetrySyncSection` leaves and re-enters composition mid-sync, *Then* `syncPendingPolarWorkout` completes without `ForgottenCoroutineScopeException`, the banner does not flicker, and `TrackOnMapAftermathViewModel` reloads telemetry so the HR graph updates.
• *Given* a suspend stop/sync call throwing `CancellationException`, *When* executed in `PolarVeritySenseOfflineController` or `PolarH10OfflineController`, *Then* `CancellationException` is rethrown rather than swallowed.
• *Given* an armed Polar Verity Sense or H10 confirmed disconnected in `DeviceManager` (`apiOverride == null`) at workout stop, *When* `stopAndBackfillWorkout()` executes, *Then* `api.waitForConnection()` is skipped, `setPolarSyncPending()` is recorded if `needsBackfill == true`, `isRecordingArmed()` becomes `false`, and `endWorkout()` finishes without a `30–45 s` stall.
• *Given* `api.waitForConnection()` hangs during stop-time backfill, *When* `STOP_CONNECTION_TIMEOUT_MS` (`5_000 ms`) expires, *Then* connection times out gracefully, `setPolarSyncPending()` is persisted if `needsBackfill == true`, and GATT resumes.

**Invariants**: `TrackerService.java <= 3,556` LoC; `WorkoutRepository.kt` untouched (Lane A); Verity Sense never writes `WorkoutRr.db` or invokes `HrvPostWorkoutRecomputer`; post-commit single-entry flash cleanup preserved; zero new string keys or DB schema bumps; `apiOverride` unit-test seams preserved.

---

### REQ-SET-120: Polar Online Bridge Offline-Sync Yield & Bounded `deviceConnecting` Handshake Grace (`ATT-9175`) [Verified]
- **Target Release**: `V5.0.0`
- **Tests**: `TST-SET-122`
- **System Requirement(s)**: `SYSREQ-DEX-007`, `SYSREQ-TRK-002`
- **Priority**: High
- **Type**: Functional
- **Verification Method**: Unit, Integration
- **Implementation**: `PolarOfflineGattCoordinator.kt`, `PolarOnlineStreamingBridge.kt`
- **Rationale**: During Polar Verity Sense Aftermath retry sync on Pixel 10 (`ATT-9175`), `PolarOfflineGattCoordinator.withGattYieldAndPolarApi()` paused only `MyBTLEDevice`, leaving `PolarOnlineStreamingBridge`'s reconnect loop, `5 s` `onSearchTimeout()` (`disconnectFromDevice()`), and `12 s` `silentHrWatchdog` running against the shared `PolarBleApi` singleton and causing `45 s` backfill timeouts; additionally, ignoring `deviceConnecting()` aborted live `SESSION_OPENING` `discoverServices()` + 8-feature handshakes at `5 s`.
- **Description**:
  1. *Alias-Aware Ref-Counted Offline GATT Yield & Lock-Free Dispatch (`REQ-SET-120.1`)*: `PolarOfflineGattCoordinator` SHALL maintain per-device alias-aware (`MAC` <-> `8-hex Polar ID`) ref-counted offline GATT yield leases (`isOfflineGattYieldActive()`) and a bridge yield listener registry (`registerOfflineYieldListener` / `unregisterOfflineYieldListener`, cleared in `resetForTesting()`). `withGattYieldAndPolarApi()` SHALL acquire the lease on entry, resolve `DeviceManager` via `deviceManagerProvider`, release strictly in `finally` (including on timeout, exception, and `CancellationException`), and dispatch pause/resume callbacks strictly outside coordinator locks without affecting sibling paired Polar sensors (`REQ-SET-118.3`).
  2. *`PolarOnlineStreamingBridge` Pause/Resume & Watchdog Deferral (`REQ-SET-120.2`)*: `PolarOnlineStreamingBridge` SHALL register for offline yield events on lifecycle init and unregister in `shutDown()`. While yielded, it SHALL cancel active `reconnectJob`, `searchTimeoutJob` (under `searchTimeoutLock`), and `silentHrWatchdogJob` (under `silentHrWatchdogLock` without leaking timers or corrupting generation tokens per `REQ-CON-054`), and MUST NOT invoke `connectToDevice()`, `disconnectFromDevice()`, or `forceDisconnectDevice()` from `startSearching()`, `scheduleBackoffReconnect()`, `onSearchTimeout()`, `onSilentHrWatchdogTimeout()`, `deviceDisconnected()`, or `blePowerStateChanged(true)`. Upon final lease release (`count -> 0`, `!isShutDown`), it SHALL sync any already-open shared session via `syncAlreadyOpenSessionIfReady(api)` (or re-arm `silentHrWatchdog` if connected without HR packets) or cleanly resume searching/backoff reconnect if disconnected.
  3. *Single-Shot Bounded `deviceConnecting()` Handshake Grace (`REQ-SET-120.3`)*: `PolarOnlineStreamingBridge.sdkCallback` SHALL override `deviceConnecting(polarDeviceInfo)` filtered strictly by `matchesDeviceInfo` across target aliases. When `deviceConnecting` fires during an active search/reconnect attempt (`!isShutDown && !isConnectedFlag && !isOfflineGattYielded()`), it SHALL extend `searchTimeoutJob` (under `searchTimeoutLock` with job-identity cleanup) at most once per attempt to `maxOf(searchTimeoutMsProvider(), connectingHandshakeTimeoutMs)` (`15_000 ms` default, `@VisibleForTesting` injectable), preserving the `5_000 ms` timeout for absent sensors (`REQ-CON-045.1`) and bounded teardown if a live handshake stalls.
  4. *Monolith Ceilings, Public Signature Pin & Localization (`REQ-SET-120.4`)*: `PolarOnlineStreamingBridge.kt <= 1,500` LoC with unchanged constructor & public JVM signatures (`InterfaceSignatureDriftContractTest`); `PolarOfflineGattCoordinator.kt <= 1,500` LoC; `0` edits to `PolarVeritySenseOfflineController.kt`, `PolarH10OfflineController.kt`, `DeviceManager.java`, `TrackerService.java`, and `DevicesDatabaseManager.java`; `0` new `src/main` reads; `Localization Parity: N/A - no new or modified UI strings`.
- **Requirement Archaeology & Chesterton's Fence Audit**:
  1. *Original Requirement ID & Target*: Refines `REQ-SET-118`, `REQ-SET-119`, `REQ-CON-045`, `REQ-CON-050`, `REQ-CON-054` across `PolarOfflineGattCoordinator.kt` and `PolarOnlineStreamingBridge.kt`.
  2. *Historical Origin & Commit Trace*: `ATT-8451` (`14070717`), `ATT-8690` (`34b78941`), `ATT-8714` (`1e7a2f0b`), `ATT-8969` (`8e44039d`), `ATT-9155` (`f7ed7d94`).
  3. *Root Reason for Existing Formulation*: `withGattYieldAndPolarApi()` targeted `MyBTLEDevice.pauseGattOperations()` before `PolarOnlineStreamingBridge` existed; `5 s` `searchTimeoutJob` prevented absent Polar sensors from stalling `DeviceManager`'s sequential queue.
  4. *Preservation of Core Invariants*: Preserves `5 s` absent-sensor timeout (`REQ-CON-045.1`), sibling Polar session isolation (`REQ-SET-118.3`), `PolarOnlineStreamingBridge` constructor/public JVM signatures, `ATT-9155` watchdog generation safety, and `0` edits to `PolarVeritySenseOfflineController.kt` (Lane A `ATT-9154`).

### REQ-SET-121: Full `saveTuningConfig` Auto-Pause Persistence, `BoundedPref` Descriptors & Centralized Zone Defaults (`ATT-6048`) [Verified]
- **Tests**: `TST-SET-123`
- **Priority**: Low
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `TuningPreferencesDataStore.kt`, `SettingsDataStore.kt`
- **Rationale**: Duplicated `.coerceIn(...)` clamping and manual key read/write blocks across `TuningPreferencesDataStore.kt` caused `saveTuningConfig(config)` to omit 4 Auto-Pause keys (`KEY_START_IN_AUTO_PAUSE_ENABLED`, `KEY_AUTO_PAUSE_BIKE_RESUME_DEBOUNCE_SEC`, `KEY_AUTO_PAUSE_RUN_RESUME_DEBOUNCE_SEC`, `KEY_AUTO_PAUSE_OTHER_RESUME_DEBOUNCE_SEC`), while `SettingsDataStore.kt` duplicates default `HR_RUN`, `HR_BIKE`, and `PWR_BIKE` zone boundaries across 7 locations (`ATT-6048`, refining `REQ-SET-073`, `REQ-TRK-043`, `REQ-TRK-044`).
- **Description**:
  1. *Complete `saveTuningConfig` Auto-Pause Persistence (`REQ-SET-121.1`)*:
     - `TuningPreferencesDataStore.saveTuningConfig(config: TuningConfig)` SHALL clamp and persist all 4 previously omitted Auto-Pause keys (`KEY_START_IN_AUTO_PAUSE_ENABLED`, `KEY_AUTO_PAUSE_BIKE_RESUME_DEBOUNCE_SEC`, `KEY_AUTO_PAUSE_RUN_RESUME_DEBOUNCE_SEC`, `KEY_AUTO_PAUSE_OTHER_RESUME_DEBOUNCE_SEC`), preserving full round-trip fidelity across all non-derived, non-hardcoded fields of `TuningConfig` (excluding the 7 `REQ-MAP-050` hardcoded `mapFollowMe*` fields, 2 derived legacy `autoPause*` fields, and `radarAutoLightControl`/`radarSmartLightAutomation` derived from `radarLightControlMode`).
  2. *Declarative `BoundedPref` Descriptors & Dependent Clamp Preservation (`REQ-SET-121.2`)*:
     - Standard bounded numeric preferences across `mapPreferencesToConfig`, `mapAutoPausePreferences`, `saveTuningConfig`, and `update*`/`set*` setters in `TuningPreferencesDataStore.kt` SHALL delegate range clamping to declarative `BoundedPref` descriptors while preserving identical behavior for all dependent clamps (`mediumDimFactor >= clampedFullDim`, `slopeSteepThreshold >= clampedSlopeFlat`, `dfaA1ThresholdT1`/`T2` `MIN_DFA_A1_THRESHOLD_GAP`), `navigationCueDismissDurationSec == 0 -> DEFAULT`, legacy fallback/mirror writes (`KEY_PROFILE_X_AXIS_DOMAIN`, `KEY_AUTO_PAUSE_SPEED_THRESHOLD_KMH`, `KEY_AUTO_PAUSE_DEBOUNCE_SEC`, radar light modes), `TrainingApplication` cluster tolerance fallback/sync, and existing `TrackerService.invalidateAutoPauseCache()` call sites.
  3. *Centralized Default Zone Boundaries (`REQ-SET-121.3`)*:
     - `SettingsDataStore.kt` SHALL define centralized default zone boundary constants for `HR_RUN` (`132, 145, 158, 171`), `HR_BIKE` (`127, 140, 153, 166`), and `PWR_BIKE` (`149, 203, 243, 284, 324, 405`) and reference them across `SettingsSnapshot.DEFAULT`, `getDefaultValue`, `calculateProportionalPowerDefaults`, `getZoneMaxFlow`, `mapSettingsSnapshot`, `checkAndMigratePowerZones`, and `resetToDefaults` without behavior change.
  4. *Architectural & Hot-Path Invariants (`REQ-SET-121.4`)*:
     - `TuningPreferencesDataStore.kt` MUST remain `<= 2,410` LoC (`ArchitectureConformanceContractTest`) with a non-public constructor (`TuningPreferencesDataStoreLifecycleTest`), `SettingsDataStore.kt` MUST introduce zero `runBlocking` calls (`DataStoreHotPathContractTest`), and zero `strings*.xml` edits are required.

**Acceptance Criteria (Given-When-Then)**:
• *Given* a `TuningConfig` where all non-derived, non-hardcoded fields (including `startInAutoPauseEnabled` and `autoPause*ResumeDebounceSec`) differ from defaults (with derived legacy/radar fields consistent with their source fields), *When* `saveTuningConfig(config)` is executed, *Then* `tuningConfigFlow` emits an identical `TuningConfig` verified across all properties.
• *Given* out-of-range inputs and dependent parameters (`mediumDimFactor < fullDimFactor`, `slopeSteepThreshold < slopeFlatThreshold`, `dfaA1 T1/T2` gap violation, `navigationCueDismissDurationSec == 0`), *When* read or written via `TuningPreferencesDataStore`, *Then* `BoundedPref` and dependent clamp logic produce identical values, legacy mirrors, `TrainingApplication` syncs, and `invalidateAutoPauseCache()` calls as before refactoring.
• *Given* default `HR_RUN`, `HR_BIKE`, and `PWR_BIKE` zone reads, migrations, and resets in `SettingsDataStore`, *When* evaluated via `SettingsSnapshot.DEFAULT`, `getDefaultValue`, `mapSettingsSnapshot`, `checkAndMigratePowerZones`, and `resetToDefaults`, *Then* all boundaries match the centralized constants identically.

**Invariants**: `TuningPreferencesDataStore.kt <= 2,410` LoC; non-public `TuningPreferencesDataStore` constructor; zero new `runBlocking` in `SettingsDataStore.kt`; zero changes to `TuningConfig` public constructor signature or `invalidateAutoPauseCache()` call sites; 9-locale parity N/A (`0` UI strings).

---

### REQ-SET-122: Dynamic Polar H10 `ACC` Rate Renegotiation (`100 <-> 200 Hz`), Lease-Safe Coordinator Restart & Gap Recovery (`ATT-9346`) [Verified]
- **Target Release**: `V5.0.0`
- **Tests**: `TST-SET-124`
- **System Requirement(s)**: `SYSREQ-TRK-002`, `SYSREQ-TRK-003`
- **Priority**: High
- **Type**: Functional
- **Verification Method**: Unit, Integration
- **Implementation**: `PolarPmdStreamCoordinator.kt`, `PolarOnlineStreamingBridge.kt`, `PolarH10RunningDynamicsEstimator.kt`
- **Rationale**: In live Pixel 10 runs (`ATT-9346`, `2026-11-20_173833`), Polar H10 connected during `BSportType.UNKNOWN` (both at cold launch and after mid-run crash recovery), negotiating `100 Hz` `ACC` (`dt = 10 ms`) once inside `startAccCollection()`. When `BSportType.RUN` activated later, `syncCadenceSensorRegistration()` registered `VERTICAL_OSCILLATION` and `GROUND_CONTACT_TIME` without restarting `StreamSlot.ACC` to `200 Hz`, leaving `CADENCE`/`STRIDES` working via `50 Hz` resampling while `PolarH10RunningDynamicsEstimator`'s `avgDtMs <= 7.5 ms` gate suppressed `100%` of VO/GCT (`0 / 2,742`).
- **Description**:
  1. *Dynamic H10 `ACC` Rate Renegotiation & Session Rate Tracking (`REQ-SET-122.1`)*: `PolarOnlineStreamingBridge` SHALL track per-connection `availableAccRates: Set<Int>` and `activeAccSampleRateHz: Int?` (cleared on disconnect/`shutDown()`), subscribe to `BANALService.MULTISPORT_TRANSITION_INTENT` alongside `BANALService.SPORT_TYPE_CHANGED_BY_USER_INTENT`, and compute `desiredAccRateHz` against `availableAccRates` (`200 Hz` for `!isVeritySense && runActive` when `200` is advertised, else `100`/`50 Hz`; `52 Hz` for Verity Sense). When `StreamSlot.ACC` is active and `activeAccSampleRateHz != desiredAccRateHz` while connected and `!isOfflineGattYielded()`, `syncCadenceSensorRegistration()` SHALL request single-flight renegotiation to upgrade `100/50 -> 200 Hz` on `BSportType.RUN` entry (user tap, route select, Tier-3 inference, multisport, or crash-recovery reconnect) and downgrade `200 -> 100/50 Hz` on `RUN` exit. If `200 Hz` is absent from `availableAccRates`, `desiredAccRateHz == activeAccSampleRateHz` MUST be a no-op with zero restart loops; Verity Sense MUST stay at `52 Hz` without renegotiation or VO/GCT registration; mid-run `100/50 -> 200 Hz` upgrades (`runActive == true`) SHALL reset `runningDynamicsEstimator` only while keeping `cadenceEstimatorInstance` and cumulative `mStridesSensor` intact.
  2. *Self-Cancellation-Safe, `ACC_OWNER_ONLINE`-Safe & Teardown-Safe Renegotiation (`REQ-SET-122.2`)*: `PolarPmdStreamCoordinator` SHALL provide a single-flight, non-reentrant `renegotiateSlot(slot, onOldSlotCancelled, stopStreaming, relaunch)` helper executing on a sibling `scope` coroutine so invocations originating inside `processAccData()` (`StreamSlot.ACC` collector) cannot self-cancel renegotiation. `renegotiateSlot()` SHALL cancel the old slot, explicitly execute `onOldSlotCancelled` to release `PolarOfflineGattCoordinator.ACC_OWNER_ONLINE` across aliases (since `cancelSlot()` clears `state.job` prior to cancellation and bypasses `launchStream`'s `onFinalCleanup`), invoke `stopStreaming`, and re-verify `!isShutDownProvider() && isConnectedProvider()` before calling `relaunch` (preserving `REQ-STB-023.3` `DROP_OLDEST` capacity `64` and `REQ-CON-053.2` `ERROR_ALREADY_IN_STATE` recovery). Any in-flight renegotiation job MUST be cancelled atomically on `cancelAllStreams()`, `deviceDisconnected()`, `pauseForOfflineSync()` (`REQ-SET-120.2`), and `shutDown()` so delayed restarts MUST NEVER execute post-disconnect or during offline PFTP GATT yield.
  3. *Estimator Gap Recovery & End-to-End Persistence (`REQ-SET-122.3`)*: `PolarH10RunningDynamicsEstimator.resetForGap()` SHALL reset `avgDtMs = DEFAULT_DT_MS` (`5.0 ms`) alongside filter/buffer state so prior low-rate bursts or `> 500 ms` BLE gaps cannot permanently poison the `avgDtMs <= 7.5 ms` gate once `200 Hz` resumes. Post-renegotiation `VERTICAL_OSCILLATION` (`cm`, `5.0..13.0`) and `GROUND_CONTACT_TIME` (`ms`, `140..400`) updates SHALL propagate cleanly through `MySensorManager`, `WorkoutSamples.db`, `CSVFileWriter`, and `FitFileWriter` (`mm`/`ms`).
  4. *Monolith Ceiling, Public Signature Pin & Localization (`REQ-SET-122.4`)*: `PolarOnlineStreamingBridge.kt <= 1,500` LoC (`ArchitectureConformanceContractTest`) with unchanged public/JVM constructor & method signatures (`InterfaceSignatureDriftContractTest`); zero SQLite schema bumps; `Localization Parity: N/A - no new or modified UI strings`.
- **Acceptance Criteria (Given-When-Then)**:
  • *Given* Polar H10 connects while `BSportType.UNKNOWN` or `BIKE` is active (`100 Hz` negotiated) and later `BSportType.RUN` becomes active via user intent, `MULTISPORT_TRANSITION_INTENT`, Tier-3 inference inside `processAccData()`, or post-crash reconnect, *When* `syncCadenceSensorRegistration(true)` runs, *Then* `PolarPmdStreamCoordinator.renegotiateSlot(ACC)` runs on a sibling coroutine without self-cancellation, releases and re-acquires `ACC_OWNER_ONLINE`, stops `100 Hz`, restarts `ACC` at `200 Hz` with `DROP_OLDEST` backpressure, preserves cumulative `STRIDES`, and emits valid `VERTICAL_OSCILLATION` (`5.0..13.0 cm`) and `GROUND_CONTACT_TIME` (`140..400 ms`) registered in `MySensorManager` and persisted/exported cleanly.
  • *Given* an active `200 Hz` H10 `RUN` stream switches to `BIKE`, *When* `syncCadenceSensorRegistration(false)` runs, *Then* VO/GCT/cadence sensors unregister and `StreamSlot.ACC` downgrades cleanly back to `100 Hz`.
  • *Given* a renegotiation is in flight when `deviceDisconnected()`, `pauseForOfflineSync()`, `cancelAllStreams()`, or `shutDown()` occurs, *When* the old stream cancels, *Then* the in-flight renegotiation coroutine is cancelled immediately and `startAccStreaming()` MUST NOT be invoked post-disconnect or while offline GATT yield is active.
  • *Given* a Polar device advertising only `50/100 Hz` (no `200 Hz`) or a Polar Verity Sense (`52 Hz`), *When* `BSportType.RUN` activates and `processAccData()` streams continuously, *Then* zero restart loops occur and Verity Sense remains pinned to `52 Hz` without registering VO/GCT.
- **Requirement Archaeology & Chesterton's Fence Audit**:
  1. *Original Requirement ID & Target*: Refines `REQ-SET-116` (`REQ-SET-116.1`, `REQ-SET-116.2`) and `REQ-CON-053` (`REQ-CON-053.1`) across `PolarOnlineStreamingBridge.kt`, `PolarPmdStreamCoordinator.kt`, and `PolarH10RunningDynamicsEstimator.kt`.
  2. *Historical Origin & Commit Trace*: `6be4eb31` (`ATT-8018`), `104a028f` (`ATT-9161`), `ed05d679` (`ATT-9175`), `f8c03d6f` (`ATT-9343`).
  3. *Root Reason for Existing Formulation*: `ATT-8016`/`ATT-8018` avoided `200 Hz` BLE bandwidth outside `RUN` assuming sport settled prior to connect; `ATT-9161` prevented duplicate `launchStream(ACC)` bursts without an explicit rate-renegotiation path.
  4. *Preservation of Core Invariants*: Preserves `ATT-8017` cadence (`strides/min`) & cumulative `STRIDES`, `ATT-9343` `DROP_OLDEST` (`capacity = 64`), `ATT-9161` single-flight `StreamSlot` & `ERROR_ALREADY_IN_STATE` recovery, `ATT-9175` offline GATT yield, Verity Sense `52 Hz` isolation, and `PolarOnlineStreamingBridge.kt <= 1,500` LoC.

---

### REQ-SET-123: Never-Connected BLE Device Passive Workout Convergence, Scan-Assist Gating & Queue Retry Deduplication (`ATT-9243`) [Verified]
- **Target Release**: `V5.0.0`
- **Tests**: `TST-SET-125`
- **System Requirement(s)**: `SYSREQ-TRK-002`
- **Priority**: High
- **Type**: Functional
- **Verification Method**: Unit, Integration
- **Implementation**: `MyBTLEDevice.java`, `DeviceSearchPeriodicRetryCoordinator.kt`
- **Rationale**: On Pixel 10 field rides (`ATT-9243`, `2026-11-20`), paired BLE sensors left at home (`mHasConnectedInSession == false`) entered the same aggressive `1s -> 2s -> 4s -> 8s -> 16s` `mReconnectRunnable` + `startScanAssistance()` loop as mid-workout disconnects because `MyBTLEDevice.isMidSessionOrWorkoutActive()` returned `true` during `TRACKING`/`PAUSED`, while `DeviceSearchPeriodicRetryCoordinator` re-enqueued keep-alive sensors for tries `2..N` before checking `shouldKeepAliveOnSearchTimeout()`—emitting 54 scan-quota throttle warnings (`>= 4` scans in `30 s`), draining battery, and starving genuine mid-workout disconnect recovery.
- **Description**:
  1. *Session-Connected vs. Never-Connected Search Timeout Convergence & `READY -> TRACKING` Edge (`REQ-SET-123.1`)*:
     - `MyBTLEDevice.mSearchTimeoutRunnable` SHALL preserve `isMidSessionOrWorkoutActive()` (`DetailedDesignParityContractTest`) and distinguish previously session-connected devices (`mHasConnectedInSession == true`) from never-connected paired devices during active workouts (`!mHasConnectedInSession && isMidSessionOrWorkoutActive()`).
     - When `mHasConnectedInSession == true` and `!mIsShutDown && mState != State.CONNECTED_TO_GATT && mState != State.CONNECTED_WITH_SERVICE`, `mSearchTimeoutRunnable` SHALL retain fast exponential reconnect scheduling (`mReconnectRunnable` via `SensorReconnectBackoffPolicy`: `1s -> 2s -> 4s -> 8s -> 16s -> ... -> finalPeriodMs`), transition `mState = State.DISCONNECTED`, arm `mIsBackgroundReconnecting = true`, ensure passive `autoConnect = true` GATT if `mBluetoothGatt == null`, and release `DeviceManager` via `notifyStopSearching(false)` (`REQ-CON-036`, `REQ-CON-051`, `REQ-CON-052`).
     - When `!mHasConnectedInSession && isMidSessionOrWorkoutActive()` (`TRACKING` or `PAUSED`) and `!mIsShutDown && mState != State.CONNECTED_TO_GATT && mState != State.CONNECTED_WITH_SERVICE`, `mSearchTimeoutRunnable` SHALL stop active scan assistance, transition `mState = State.DISCONNECTED`, arm `mIsBackgroundReconnecting = true`, cancel any pending `mReconnectRunnable` and MUST NOT schedule `mReconnectRunnable`, converge to a single OS-managed passive `connectGatt(..., autoConnect = true, ..., TRANSPORT_LE)` handle (replacing any non-`autoConnect` `mBluetoothGatt` opened when Try 1 started in `TrackingMode.READY` before transitioning to `TRACKING`, while preserving an already-open `autoConnect = true` handle), and release `DeviceManager` via `notifyStopSearching(false)`.
     - When `!isMidSessionOrWorkoutActive()` (`TrackingMode.READY` with `!mHasConnectedInSession`), `mSearchTimeoutRunnable` SHALL preserve existing `disconnectFromGatt()` + `notifyStopSearching(false)` battery bounding (`REQ-CON-036.1`).
     - Upon subsequent passive `autoConnect = true` connection (`STATE_CONNECTED` with `GATT_SUCCESS`), `mHasConnectedInSession` SHALL transition to `true` and `mCurrentReconnectDelayMs` SHALL reset to `INITIAL_RECONNECT_DELAY_MS` (`1,000 ms`) so any later mid-workout drop receives full fast exponential recovery and active scan assistance.
  2. *Scan-Assist Gating, `attemptBackgroundReconnect` Timeout Skipping & Never-Connected Disconnect Floor (`REQ-SET-123.2`)*:
     - `MyBTLEDevice.initiateScanAndConnect(boolean allowScanAssist)` SHALL invoke `startScanAssistance(btAdapter)` strictly when `allowScanAssist == true`: `startSearching()` SHALL pass `true` (bounded single initial discovery scan), whereas `attemptBackgroundReconnect()` SHALL pass `mHasConnectedInSession`, guaranteeing `0` `sScanTimestamps` quota consumption by never-connected background reconnects while preserving scan-assist quota for mid-workout drops (`mHasConnectedInSession == true`) and `BTLEHeartRateDevice` / `BleHrmSilentConnectionWatchdog` `triggerSilentConnectionReconnect()` (`REQ-CON-054`).
     - `MyBTLEDevice.attemptBackgroundReconnect()` SHALL skip scheduling `mSearchTimeoutRunnable` when `!mHasConnectedInSession && isMidSessionOrWorkoutActive()` (passive OS-managed convergence without `5 s` timeout churn, including after `resumeGattOperations()`), while retaining `mSearchTimeoutRunnable` scheduling whenever `mHasConnectedInSession || !isMidSessionOrWorkoutActive()` (session-connected drops and `TrackingMode.READY` retries).
     - In `mGattCallback.onConnectionStateChange()` on `STATE_DISCONNECTED` (or `STATE_CONNECTED && status != GATT_SUCCESS`) when `!mIsShutDown`, if `!mHasConnectedInSession && isMidSessionOrWorkoutActive()`, `MyBTLEDevice` SHALL floor the scheduled `mReconnectRunnable` delay at `>= 60_000 ms` so spurious GATT error `133`/`8` callbacks on absent paired devices during workouts cannot re-trigger aggressive `1s -> 2s -> 4s` retries, while preserving `1s -> 2s -> 4s -> ...` backoff for `mHasConnectedInSession == true` and `TrackingMode.READY` disconnects.
  3. *Keep-Alive Queue Retry Deduplication & `deviceTries` Cleanup (`REQ-SET-123.3`)*:
     - In `DeviceSearchPeriodicRetryCoordinator.handleSearchStoppedForCurrentDevice()`, when `!success`, the coordinator SHALL evaluate `current.shouldKeepAliveOnSearchTimeout()` **before** `tries <= 1`.
     - When `!success && current.shouldKeepAliveOnSearchTimeout()` is `true`, the coordinator SHALL call `deviceTries.remove(current)` and SHALL NOT re-enqueue `current` onto `searchQueue` for redundant tries `2..N`, SHALL NOT schedule periodic re-search, and SHALL NOT invoke `detachAndGiveUpDevice` — releasing the sequential queue immediately (`3` absent paired BLE sensors perform `3` initial scans total instead of `9`, strictly `<= MAX_SCANS_IN_WINDOW = 4` with `0` quota throttles).
     - When `current.shouldKeepAliveOnSearchTimeout()` is `false`, existing `tries > 1` decrement + re-enqueue (`searchQueue.addLast(current)`) and `tries <= 1` periodic re-search / `detachAndGiveUpDevice` invariants (`REQ-CON-036.1`, `REQ-CON-051.2`, `REQ-CON-052.2`) MUST remain intact.
  4. *Architectural Parity, Monolith Ceilings & Localization (`REQ-SET-123.4`)*:
     - `DeviceManager.java` (`<= 1,708` LoC) and `PolarOnlineStreamingBridge.kt` (`<= 1,500` LoC) MUST remain strictly untouched; `MyBTLEDevice.MAX_RECONNECT_DELAY_MS == 16000L`, `INITIAL_RECONNECT_DELAY_MS == 1000L`, `MAX_SCANS_IN_WINDOW == 4`, `SCAN_QUOTA_WINDOW_MS == 30000L`, and all `DetailedDesignParityContractTest` symbols MUST remain intact; `docs/architecture/detailed_design/my_btle_device.md`, `docs/system/sysreq_live_tracking.md`, and `docs/system/system_software_traceability.md` SHALL stay synchronized; `Localization Parity: N/A - no new or modified UI strings`.
- **Acceptance Criteria (Given-When-Then)**:
  • *Given* `3` absent never-connected paired BLE sensors (`!mHasConnectedInSession`) during `TrackingMode.TRACKING` or `PAUSED`, *When* initial searches time out across the queue, *Then* `DeviceSearchPeriodicRetryCoordinator` removes each sensor from `deviceTries` without re-enqueueing tries `2..3`, each sensor performs at most `1` initial scan (`3 <= 4` in `30 s`, `0` quota throttles), transitions to `State.DISCONNECTED` (`isSearching == false`, `mIsBackgroundReconnecting == true`), holds passive `connectGatt(..., autoConnect = true, ..., TRANSPORT_LE)`, and schedules `0` `mReconnectRunnable` retries.
  • *Given* a never-connected BLE sensor whose Try 1 started in `TrackingMode.READY` (opening `autoConnect = false` GATT) and timed out after transitioning to `TrackingMode.TRACKING`, *When* `mSearchTimeoutRunnable` runs, *Then* the non-`autoConnect` GATT handle is replaced cleanly by passive `connectGatt(..., autoConnect = true, ..., TRANSPORT_LE)` with `0` `mReconnectRunnable` scheduled.
  • *Given* a never-connected paired BLE sensor during `TrackingMode.TRACKING` receives a spurious `STATE_DISCONNECTED` (status `133`/`8`), *When* `onConnectionStateChange` and `attemptBackgroundReconnect()` run, *Then* reconnect delay is floored at `>= 60_000 ms`, `attemptBackgroundReconnect()` skips `mSearchTimeoutRunnable` and `startScanAssistance()` (`0` scans added to `sScanTimestamps`), and re-establishes passive `autoConnect = true`.
  • *Given* a previously connected BLE sensor (`mHasConnectedInSession == true`, including one initially awakened mid-ride via passive `autoConnect = true` or `BTLEHeartRateDevice` silent-HR watchdog), *When* it disconnects mid-workout or times out during reconnect, *Then* fast `1s -> 2s -> 4s -> 8s -> 16s -> ...` backoff, `mSearchTimeoutRunnable`, and active `startScanAssistance()` execute identically to `REQ-CON-036` / `REQ-CON-052` / `REQ-CON-054`.
  • *Given* `TrackingMode.READY` startup with `!mHasConnectedInSession`, *When* an offline sensor times out or disconnects, *Then* `DeviceSearchPeriodicRetryCoordinator` preserves tries `2..N` and periodic re-search / `shutDown()` bounds (`REQ-CON-036.1`, `REQ-CON-052.2`) and `attemptBackgroundReconnect()` retains `mSearchTimeoutRunnable`.
- **Invariants**: `DeviceManager.java <= 1,708` LoC untouched; `PolarOnlineStreamingBridge.kt <= 1,500` LoC untouched; `MyBTLEDevice.MAX_RECONNECT_DELAY_MS == 16000L`, `MAX_SCANS_IN_WINDOW == 4`, `isMidSessionOrWorkoutActive()` preserved; `mGattLock` thread safety preserved; `ATT-9155` `triggerSilentConnectionReconnect()` & `PolarOfflineGattCoordinator` pause/resume preserved; `0` SQLite schema or UI string changes.
- **Requirement Archaeology & Chesterton's Fence Audit**:
  1. *Original Requirement ID & Target*: Refines `REQ-CON-036` (`REQ-CON-036.1`–`.3`), `REQ-CON-051` (`REQ-CON-051.1`–`.2`), and `REQ-CON-052` (`REQ-CON-052.1`–`.2`) across `MyBTLEDevice.java`, `DeviceSearchPeriodicRetryCoordinator.kt`, and `docs/architecture/detailed_design/my_btle_device.md`.
  2. *Historical Origin & Commit Trace*: `3dc19303` (`ATT-7755`), `d2209175` (`ATT-8998`), `6bb37af4` (`ATT-8965`), `f7ed7d94` (`ATT-9155`).
  3. *Root Reason for Existing Formulation*: `ATT-7755` included `TRACKING`/`PAUSED` inside `isMidSessionOrWorkoutActive()` so sensors turned on mid-ride could auto-connect without manual re-search, and `ATT-8998` preserved that branch while unblocking `mSearchQueue`—inadvertently subjecting never-connected absent paired sensors to active `1s -> 2s -> 4s` scan-assist loops and redundant queue tries `2..N`.
  4. *Preservation of Core Invariants*: Session-connected mid-workout drops retain fast `1s -> 2s -> 4s -> 8s -> 16s -> ...` backoff + active scan-assist; never-connected active-workout sensors retain OS-managed passive `autoConnect = true` (zero active scans, zero queue deadlock, automatic upgrade to session-connected once woken mid-ride); `READY` offline startup battery bounding and `ATT-9155` silent-HR watchdog recovery remain 100% intact.

---

### REQ-SET-124: Shared `CscRevolutionTracker` Extraction & Unsigned `UINT16` Event-Time Rollover Preservation (`ATT-6042`) [Verified]
- **Target Release**: `V5.0.0`
- **Tests**: `TST-SET-126`
- **System Requirement(s)**: `SYSREQ-TRK-002`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit, Integration
- **Implementation**: `CscRevolutionTracker.kt`, `BTLEBikeDevice.java`, `BTLEBikePowerDevice.java`
- **Localization Parity**: `N/A - no new or modified UI strings` (`0` edits to `res/values*/strings*.xml`)
- **Rationale**: `BTLEBikeDevice.java` (`236` LoC) and `BTLEBikePowerDevice.java` (`482` LoC) duplicate `9` wheel/crank state fields, `MAX_IDENTICAL = 4`, `unregisterSensors()` resets (`REQ-CON-024`, `ATT-6162`), and `~90` LoC of delta math differing only in wheel time resolution (`1024 Hz` vs `2048 Hz`), null-sensor guards, and `notifyWheelRevolutions`. Furthermore, guarding with `eventTime > mLastEventTime` drops one valid sample on every `UINT16` timer rollover (`32 s` CPS / `64 s` CSC) as identical time.
- **Description**:
  1. *Pure-JVM `CscRevolutionTracker` & Parameterization (`REQ-SET-124.1`)*: `CscRevolutionTracker(wheelTimeResolution: Int, crankTimeResolution: Int = 1024, maxIdenticalTicks: Int = 4)` (pure JVM under `banalservice/devices/bluetooth_le/`, `0` `android.*` dependencies) SHALL encapsulate wheel/crank baseline state (`last*Valid`, `initWheelRevs`, `last*Revs`, `last*EventTime`, `identical*Time`) and `reset()`. `BTLEBikeDevice` SHALL instantiate with `wheelTimeResolution = 1024` (`0x2A5B` CSC); `BTLEBikePowerDevice` SHALL instantiate with `wheelTimeResolution = 2048` (`0x2A63` CPS); both use `crankTimeResolution = 1024` and `maxIdenticalTicks = 4`.
  2. *Modular `UINT16` Event-Time Rollover & Counter Wrap Handling (`REQ-SET-124.2`)*: Both wheel and crank processing SHALL compute `timeDiff = (eventTime - lastEventTime) & 0xFFFFL` so `UINT16` timer rollovers (e.g., `65000 -> 500`, `timeDiff = 1036`) evaluate normally without dropping the wrap sample, while `timeDiff == 0L` increments `identical*Time` and triggers identical-time coasting (`0.0` speed / `null` pace, or `0.0` cadence) once `identical*Time >= maxIdenticalTicks` (`4`). Wheel evaluations SHALL compute `UINT32` modular deltas (`& 0xFFFFFFFFL`) for `revDiff` and cumulative distance, omit `mPaceSensor` update when `revDiff == 0L` (preventing `Infinity`), and preserve `REQ-CON-024` crank `UINT16` masking (`& 0xFFFFL`), floating-point cadence `(60.0 * revDiff * crankTimeResolution) / timeDiff`, and `0.0..250.0` rpm plausibility gate. Baseline `last*Revs`, `last*EventTime`, and `last*Valid = true` MUST update unconditionally on every processed packet.
  3. *Driver Delegation, Null-Sensor Guards & Disconnect Reset (`REQ-SET-124.3`)*: `BTLEBikeDevice` and `BTLEBikePowerDevice` SHALL delegate wheel/crank packets to `CscRevolutionTracker` and override `unregisterSensors()` to call `super.unregisterSensors()` and `tracker.reset()`. `BTLEBikeDevice` SHALL preserve null-sensor guards (`mSpeedSensor != null`, `mCadenceSensor != null` for `BTLEBikeSpeedDevice` / `BTLEBikeCadenceDevice`) and `mMySensorManager.notifyWheelRevolutions(revDiff, cumulativeWheelRevs, System.currentTimeMillis())` on `timeDiff > 0` wheel updates (`BTLEBikePowerDevice` retains `mIs*Supported` gates and omits `notifyWheelRevolutions`).
  4. *Preserved Guarantees & Localization (`REQ-SET-124.4`)*: All `REQ-CON-024` (`ATT-6162`), `REQ-CON-002`, and `REQ-CON-014` guarantees and existing protected sensor fields/signatures remain intact (`Localization Parity: N/A`).

**Acceptance Criteria (Given-When-Then)**:
• *Given* `CscRevolutionTracker` (or `BTLEBikeDevice` / `BTLEBikePowerDevice`) with `lastEventTime = 65000`, *When* a measurement arrives with rolled-over `eventTime = 500`, *Then* `timeDiff = 1036` is computed positively and the speed/distance or cadence sample is emitted normally without being discarded as identical time.
• *Given* `BTLEBikeDevice` (`1024 Hz`) and `BTLEBikePowerDevice` (`2048 Hz`), *When* wheel/crank packets (including `UINT16` crank wrap `65535 -> 1`, `> 250 rpm` spike, `MAX_IDENTICAL = 4` identical timestamps, single-sensor `SpeedDevice`/`CadenceDevice` null-sensor subsets) or `unregisterSensors()` occur, *Then* both delegate cleanly to `CscRevolutionTracker` with exact `ATT-6162` & `TST-CON-005` parity.

**Invariants**: `wheelTimeResolution` (`1024` vs `2048`); `crankTimeResolution = 1024`; `MAX_IDENTICAL = 4`; `0.0..250.0` rpm gate; `revDiff != 0` pace guard; unconditional baseline update; `notifyWheelRevolutions` on `BTLEBikeDevice` only; null-sensor guards intact; `0` `strings*.xml` edits.

---

### REQ-SET-125: Workout Details Polar Retry Sync Unpaired Sensor Detection, Action Guard & Localized Hint (`ATT-9509`) [Verified]
- **Target Release**: `V5.0.0`
- **Tests**: `TST-SET-127`
- **System Requirement(s)**: `SYSREQ-DEX-007`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit, Integration, On-Device Manual
- **Implementation**: `DeviceDataRepository.kt`, `PolarH10RetrySyncBanner.kt`, `TrackOnMapScreen.kt`, `WorkoutDetailTabbedContent.kt`, `strings_devices.xml`
- **Localization Parity**: 9/9 locales (`EN, DE, ES, FR, IT, JA, NL, PL, PT`) via `app/src/main/res/values*/strings_devices.xml` (`polar_h10_retry_sync_unpaired_hint`, `polar_verity_retry_sync_unpaired_hint`)
- **Rationale**: During PO testing of `ATT-9175` (Pixel 10, `2026-11-21`), unpairing a Polar Verity Sense or H10 before/during post-workout sync left *"Jetzt synchronisieren"* enabled on the Aftermath retry banner; tapping it triggered a futile BLE sync and failed with a generic error instead of explaining that the target sensor is currently unpaired (refining `REQ-SET-106.2`, `REQ-SET-109.3`, `REQ-SET-119.1`).
- **Description**:
  1. *Encapsulated Live Paired-State Query (`REQ-SET-125.1`)*: `DeviceDataRepository` SHALL expose `isPolarRetrySensorPaired(polarDeviceId: String?, isVeritySense: Boolean): Boolean` performing a live query against its private `devicesDatabaseManager` that explicitly verifies `isPaired(dev.id)` (`PAIRED > 0`, never trusting unfiltered cache rows with `PAIRED = 0`) across `DeviceType.HRM` + `Protocol.BLUETOOTH_LE` sensors. When `polarDeviceId` is non-blank, matching SHALL evaluate MAC address, `PolarOfflineCapabilityStore.getPolarDeviceId(context, mac)`, `PolarOfflineGattCoordinator.extractPolarId(...)`, and device name so an unpaired Verity Sense is never masked by a paired H10 (and vice versa). Strictly when `polarDeviceId.isNullOrBlank()`, family fallback SHALL evaluate `isPolarVeritySenseDevice` / `isPolarH10Device` passing `manufacturer` alongside `name` and `macAddress`. `DeviceDataRepository` MUST NOT expose `*DatabaseManager` or `Cursor` getters (`Rule 8.7`), and `DevicesDatabaseManager.java` & `WorkoutRepository.kt` MUST remain untouched.
  2. *Aftermath `polarDeviceId` Forwarding & Reactive Re-Evaluation (`REQ-SET-125.2`)*: Both `TrackOnMapScreen.kt` and `WorkoutDetailTabbedContent.kt` SHALL forward `workoutData.polarDeviceId` to `PolarPendingRetrySyncSection`. `PolarPendingRetrySyncSection` SHALL observe `DeviceDataRepository.allDevices` AND `Lifecycle.Event.ON_RESUME` ticks with `initialValue = null` (preventing Frame-0 false-unpaired flicker before async I/O completes per `docs/coding_standards.md` §5.10) and MUST NOT unmount `WorkoutDetailTabbedContent` (§5.9).
  3. *Unpaired Banner Guard, Semantic Hint & Re-Pair Recovery (`REQ-SET-125.3`)*: While `isSensorPaired == false`, `PolarH10RetrySyncBanner` SHALL disable *"Jetzt synchronisieren"* (`enabled = !isSyncing && isSensorPaired`), keep *"Ignorieren"* (`Dismiss`) enabled (`enabled = !isSyncing`), and render a clear localized unpaired hint (semantic warning/error styling with a Material vector icon, zero inline Unicode emojis per `docs/design_guidelines.md` §5.4 & `ATT-5400`) taking precedence over any stale generic `errorMessage`. Once the target sensor is re-paired (detected via `allDevices` or `ON_RESUME`), the unpaired hint SHALL clear and *"Jetzt synchronisieren"* SHALL re-enable automatically.
  4. *9-Locale Localization Parity & Monolith Ceilings (`REQ-SET-125.4`)*: `polar_h10_retry_sync_unpaired_hint` and `polar_verity_retry_sync_unpaired_hint` SHALL be localized across all 9 `app/src/main/res/values*/strings_devices.xml` locales (`EN, DE, ES, FR, IT, JA, NL, PL, PT`) with zero inline emojis and verified by `TranslationParityTest`. All modified files MUST stay strictly within `ArchitectureConformanceContractTest` monolith caps with zero direct `DevicesDatabaseManager` imports under `ui/aftermath/`.

**Acceptance Criteria (Given-When-Then)**:
• *Given* a workout with `polarSyncPending == true` whose target Polar Verity Sense or H10 is unpaired (`PAIRED = 0`) or deleted in `DevicesDatabaseManager` (even if a sibling Polar model remains paired), *When* the athlete opens Workout Details, *Then* *"Jetzt synchronisieren"* is disabled, *"Ignorieren"* remains enabled, and the model-specific localized unpaired hint is displayed (taking precedence over any stale generic error).
• *Given* the same Workout Details banner showing the unpaired hint, *When* the athlete pairs the target Polar sensor again and returns (`allDevices` update or `ON_RESUME`), *Then* the unpaired hint disappears, *"Jetzt synchronisieren"* re-enables, and tapping it launches `PolarRetrySyncCoordinator.startSync(...)` normally.
• *Given* a workout opening before the initial async pairing query completes (`initialValue = null`), *When* Frame 0 renders, *Then* no false unpaired hint or disabled-button flicker occurs.

**Invariants**: `PolarRetrySyncCoordinator` non-composition scope (`REQ-SET-119.1`) intact; sibling Polar isolation (`REQ-SET-118.3`) preserved; `Dismiss` enabled whenever `!isSyncing` (`REQ-SET-106.4`); `DevicesDatabaseManager.java` (`1,669/1,671`) and `WorkoutRepository.kt` (`4,986/5,117`) untouched; `0` direct DB calls in `ui/aftermath/`; `0` DB schema bumps; `9/9` locale parity (`TranslationParityTest`).

---

### REQ-SET-126: Polar Verity Sense Workout PPI Gating, Stale/Flatline HR & RR Clearing & Health-Aware Primary HR Arbitration (`ATT-9345`) [Verified]
- **Target Release**: `V5.0.0`
- **Tests**: `TST-SET-128`
- **System Requirement(s)**: `SYSREQ-TRK-002`, `SYSREQ-TRK-012`
- **Priority**: Critical
- **Type**: Functional
- **Verification Method**: Unit, Integration
- **Implementation**: `PolarHrStreamHealthMonitor.kt`, `PrimaryHrSensorArbitrator.kt`, `HrSensorHealthProvider.kt`, `PolarOnlineStreamingBridge.kt`, `PolarPmdStreamCoordinator.kt`, `MySensorManager.java`, `BTLEHeartRateDevice.java`, `my_btle_device.md`
- **Localization Parity**: `N/A - 0 string changes` (`0` edits to `res/values*/strings*.xml`)
- **Rationale**: On Pixel 10 (`ATT-9345`, run `_id = 2422`, Polar H10 + Polar Verity Sense both with `polarLiveImuEnabled = 1`), unconditional Verity Sense `startPpiStreaming()` during active running caused Verity Sense optical HR to freeze identically at `79 bpm` (`2,738/2,742 s`) and `RR_INTERVAL` to latch at `659 ms` (`919 s`, `100%` HRV artifact rate), while `MySensorManager`'s first-connected-wins `peekFirst()` bound canonical `SensorType.HR` to frozen Verity Sense over healthy Polar H10 (`95–159 bpm`, avg `146.5 bpm`), corrupting workout HR summaries, zones, and `EFFICIENCY_FACTOR`.
- **Description**:
  1. *Verity Sense Workout PPI Gating & Explicit Firmware Stop (`REQ-SET-126.1`)*: `PolarOnlineStreamingBridge` SHALL gate Polar Verity Sense `startPpiStreaming()` strictly to non-workout resting modes (`mode != TrackingMode.TRACKING && mode != TrackingMode.PAUSED`), preserving resting `READY` / `MorningHrvCheck` optical PPI streaming (`REQ-HRV-051`) while preventing optical PPG pipeline contention during active workouts (`TRACKING`/`PAUSED`). Upon receiving `TrainingApplication.TRACKING_STATE_CHANGED` entering `TRACKING` or `PAUSED`, the bridge SHALL cancel `StreamSlot.PPI` and explicitly invoke `api.stopStreaming(sessionId, PmdMeasurementType.PPI)` without interrupting `HR`, `ACC`, or `GYRO` streams; upon returning to `READY` (while connected and not yielded), Verity Sense MAY cleanly resume resting PPI streaming.
  2. *Stale/Flatline HR & RR Clearing & Recovery (`REQ-SET-126.2`)*: Pure-JVM `PolarHrStreamHealthMonitor` (integrated into `PolarOnlineStreamingBridge` and `BTLEHeartRateDevice`) SHALL ensure `silentHrWatchdog` is NEVER disarmed by `sample.hr <= 0` or lost contact (`contactStatusSupported && !contactStatus`). On `hr <= 0` / lost contact persistence (`>= 10 s`) or HR packet silence (`>= 15 s`), the sensor SHALL clear `mHeartRateSensor.newValue(null)` and `mRrIntervalSensor?.newValue(null)` (preventing infinite last-value latching of `79 bpm` / `659 ms`). On sustained `ARTIFACT_GAP`-only PPI/RR bursts (`>= 10 s` without an `ARTIFACT_NORMAL` beat), `mRrIntervalSensor?.newValue(null)` SHALL clear. On sustained constant-value HR flatline (`>= 60 s` identical `hr > 0` without ±1 bpm variation) or rolling `>= 95%` optical HRV artifact saturation, the monitor SHALL flag the HRM stream as degraded, trigger cooldown-bounded recovery (explicitly stopping Verity `PPI` if active and re-subscribing `StreamSlot.HR` without disturbing `ACC`/`GYRO`), and notify `MySensorManager.reevaluatePrimaryHrSensor()`.
  3. *Health- & Modality-Aware Primary `HR` Arbitration (`REQ-SET-126.3`)*: Pure-JVM `PrimaryHrSensorArbitrator` + lock-free `HrSensorHealthProvider` integrated into `MySensorManager` (`SensorType.HR`) SHALL order registered `SensorType.HR` candidates deterministically by `(healthTier [HEALTHY (0) < DEGRADED (1) < STALE_OR_NULL (2)], modalityTier [ECG (0) < ANT+ (1) < Optical PPG (2) < Other (3)], registrationOrder)`. Any `HEALTHY` HRM SHALL take precedence over any `DEGRADED` (flatlined / `>= 95%` optical artifact / never-emitted) or `STALE_OR_NULL` HRM regardless of registration order or modality; among `HEALTHY` candidates, ECG chest straps (Polar H10) SHALL strictly beat optical PPG armbands (Polar Verity Sense). When ALL registered HR sensors are `STALE_OR_NULL`, `PrimaryHrSensorArbitrator` SHALL still return the highest-modality sensor (preventing spurious `ProxySensor` unbinding / `mSensorsSensor` drops while stale sensors emit `null`). All `HrSensorHealthProvider` reads inside `synchronized (MySensorManager.this)` MUST be strictly lock-free (`@Volatile`/atomic), and `ProxySensor.setSourceSensor(...)` MUST be invoked strictly outside `synchronized (MySensorManager.this)` (`REQ-SEN-021.3`), re-arbitrating immediately on registration, health degradation, or recovery.
  4. *Monolith Ceilings, Public Signature Pin & Detailed Design (`REQ-SET-126.4`)*: `PolarOnlineStreamingBridge.kt <= 1,500` LoC (`<= 1,495`, unchanged constructor & public JVM signatures per `InterfaceSignatureDriftContractTest`); `MySensorManager.java <= 1,500` LoC; `0` edits to `DeviceManager.java` (`1,707/1,708`), `TrackerService.java` (`3,551/3,559`), `DevicesDatabaseManager.java` (`1,669/1,671`), and `PolarVeritySenseOfflineController.kt` (`1,483/1,500`); update `docs/architecture/detailed_design/my_btle_device.md` (`REQ-PRO-114`); `Localization Parity: N/A - 0 string changes`.
- **Requirement Archaeology & Chesterton's Fence Audit**:
  1. *Original Requirement ID & Target*: Refines `REQ-HRV-051` (`ATT-9176`), `REQ-CON-054` (`ATT-9155`), `REQ-SET-122` (`ATT-9346`), `REQ-TRK-085` (`ATT-8706`), and `REQ-SEN-021.3` across `PolarOnlineStreamingBridge.kt`, `MySensorManager.java`, and `BTLEHeartRateDevice.java`.
  2. *Historical Origin & Commit Trace*: `ATT-9176` (`32ca4857`), `ATT-9177` (`b899a864`), `ATT-9155` (`f7ed7d94`), `ATT-9346` (`939725f4`), `ATT-8706` (`7dcca5e7`).
  3. *Root Reason for Existing Formulation*: `ATT-9176` enabled Verity Sense `startPpiStreaming` across all readiness hooks for resting Morning HRV checks without gating against `TrackingMode.TRACKING`/`PAUSED`, and `MySensorManager` historically assumed a single `DeviceType.HRM` per session.
  4. *Preservation of Core Invariants*: Preserves resting `READY`/`IDLE` Verity Sense PPI streaming for Morning HRV (`REQ-HRV-051`, `ATT-9177`), H10 `100 <-> 200 Hz` `ACC` renegotiation (`REQ-SET-122`), `PolarOnlineStreamingBridge` constructor/public JVM signatures, `REQ-SEN-021.3` lock-free `ProxySensor` dispatch, `ATT-9155` initial silent-HR watchdog generation guards, and `0` edits to `DeviceManager.java`/`TrackerService.java`.

**Acceptance Criteria (Given-When-Then)**:
• *Given* a Polar Verity Sense connected with `polarLiveImuEnabled = true` in `TrackingMode.READY`, *When* `FEATURE_POLAR_ONLINE_STREAMING` is ready, *Then* `startPpiStreaming` runs for resting HRV (`REQ-HRV-051`), and *When* `TrackingMode` transitions to `TRACKING` or `PAUSED` via `TRACKING_STATE_CHANGED`, *Then* `StreamSlot.PPI` cancels and `api.stopStreaming(sessionId, PmdMeasurementType.PPI)` executes while `HR`, `ACC`, and `GYRO` continue uninterrupted.
• *Given* an active HRM emitting `sample.hr <= 0` / lost contact, going silent (`> 15 s`), latching a constant HR (`>= 60 s`), or emitting only `ARTIFACT_GAP` PPI beats (`>= 10 s`), *When* evaluated by `PolarHrStreamHealthMonitor`, *Then* `silentHrWatchdog` is not disarmed on `hr <= 0`, stale HR/RR clear to `null` instead of latching `79 bpm`/`659 ms`, flatlined/saturated streams mark degraded + trigger bounded recovery, and `MySensorManager.reevaluatePrimaryHrSensor()` runs.
• *Given* Polar Verity Sense (optical PPG) registered BEFORE Polar H10 (ECG), *When* both are healthy, *Then* canonical `SensorType.HR` binds to Polar H10; *When* Polar H10 goes stale/disconnects while Verity Sense is healthy, *Then* canonical `HR` fails over to Verity Sense; *When* Verity Sense freezes at `79 bpm` or saturates at `>= 95%` artifact rate while H10 is healthy, *Then* canonical `HR` selects Polar H10; *When* all HRMs are stale, *Then* `ProxySensor` remains bound to highest-modality HRM emitting `null`.

**Invariants**: Resting `READY` Verity PPI preserved (`REQ-HRV-051`); H10 `200 Hz` `ACC` renegotiation preserved (`REQ-SET-122`); `PolarOnlineStreamingBridge.kt <= 1,500` LoC with unchanged public JVM signatures; lock-free `HrSensorHealthProvider` + outside-lock `ProxySensor.setSourceSensor` (`REQ-SEN-021.3`); `0` edits to `DeviceManager.java`, `TrackerService.java`, `DevicesDatabaseManager.java`, `PolarVeritySenseOfflineController.kt`; `0` UI string changes.

---

### REQ-SET-127: `EditWorkoutScreen` Immediate Polar Pending Retry Sync Banner, Dismiss Persistence & Post-Sync Refresh (`ATT-9174`) [Verified]
- **Target Release**: `V5.0.0`
- **Tests**: `TST-SET-129`
- **System Requirement(s)**: `SYSREQ-DEX-007`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit, Integration, On-Device Manual
- **Implementation**: `EditWorkoutScreen.kt`, `EditWorkoutViewModel.kt`, `PolarH10RetrySyncBanner.kt`
- **Localization Parity**: `N/A - 0 string changes` (reuses existing 9/9 localized Polar retry sync & unpaired hint strings in `strings_devices.xml`)
- **Rationale**: After stopping a workout while Polar Verity Sense or Polar H10 is disconnected, stop-time fast-fail (`ATT-8969`) opens `EditWorkoutScreen` directly. Mounting `PolarPendingRetrySyncSection` directly at the top of `EditWorkoutDialog` lets the athlete reconnect and tap **"Sync Now"** (or **"Dismiss"**) immediately without closing Edit Workout and navigating to Workout Details first (refining `REQ-SET-106.2`, `REQ-SET-109.3`, `REQ-SET-119.1`, `REQ-SET-125`).
- **Description**:
  1. *Immediate Banner Mounting & Flush Geometry (`REQ-SET-127.1`)*: `EditWorkoutDialog` (`EditWorkoutScreen.kt`) SHALL mount `PolarPendingRetrySyncSection` directly at the top of its content column (below any Diagnostic Warning Card and above the Workout Name field) whenever `workoutData?.polarSyncPending == true`, forwarding `workoutId`, `polarExerciseId`, `polarDeviceId`, `outerPadding = PaddingValues(0.dp)` (aligning flush with the bottom-sheet form controls while preserving the default `16.dp`/`8.dp` outer padding for `TrackOnMapScreen` and `WorkoutDetailTabbedContent`), and `Modifier.testTag(EditWorkoutTestTags.POLAR_RETRY_SYNC_BANNER)`. When `polarSyncPending == false`, the banner SHALL NOT render. Unpaired-sensor detection, disabled **"Sync Now"**, localized unpaired hint, and reactive re-pair recovery (`REQ-SET-125`) SHALL work identically inside `EditWorkoutScreen`.
  2. *Lifecycle-Safe Sync & Race-Free Dismiss (`REQ-SET-127.2`)*: Tapping **"Sync Now"** SHALL dispatch through the existing non-composition `PolarRetrySyncCoordinator.startSync(...)` (`REQ-SET-119.1`). Tapping **"Dismiss"** SHALL clear coordinator state and invoke `EditWorkoutViewModel.dismissPolarSyncPending()`, which immediately clears `polarSyncPending = false` in editor state, guards against stale `repository.allWorkouts` emissions (`userDismissedPolarSyncPending`), and persists `POLAR_SYNC_PENDING = 0` via `WorkoutRepository.dismissPolarSyncPending(workoutId)`.
  3. *Post-Sync Summary Refresh & User-Edit / Export Preservation (`REQ-SET-127.3`)*: `EditWorkoutViewModel` SHALL observe `PolarRetrySyncCoordinator.completedWorkoutEvents` and call `repository.refreshWorkout(workoutId)` when `completedWorkoutId == workoutId`. Reactive telemetry refresh (`mergeTelemetryPreservingUserEdits`) SHALL update workout summary stats and hide the banner (`polarSyncPending = false`) upon completion while strictly preserving all 19 user-editable metadata fields and preserving existing `WorkoutPostProcessingCoordinator` / controller export gating and re-export behavior.

**Acceptance Criteria (Given-When-Then)**:
• *Given* a workout with `polarSyncPending == true` (initially or set reactively after stop-time fast-fail), *When* `EditWorkoutScreen` is open, *Then* `PolarPendingRetrySyncSection` renders at the top of the sheet with model-specific H10/Verity/unpaired copy; *When* `polarSyncPending == false`, *Then* the banner is hidden.
• *Given* the banner is visible with a paired sensor, *When* the user taps **"Sync Now"** and sync completes, *Then* `PolarRetrySyncCoordinator` runs independently of composition, `EditWorkoutViewModel` refreshes workout telemetry via `repository.refreshWorkout(workoutId)`, the banner hides, and unsaved editor fields remain intact.
• *Given* the banner is visible, *When* the user taps **"Dismiss"**, *Then* the banner hides immediately, `repository.dismissPolarSyncPending(workoutId)` persists `polarSyncPending = 0`, and stale `allWorkouts` snapshots do not resurrect the banner.

**Invariants**: `PolarRetrySyncCoordinator` non-composition scope intact (`REQ-SET-119.1`); `ATT-9509` unpaired hint & reactivity intact (`REQ-SET-125`); `REQ-TRK-091.2` 19-field user-edit preservation intact; `TrackOnMapScreen`/`WorkoutDetailTabbedContent` default outer padding unchanged; `0` monolith files touched; `0` string edits.

---

### REQ-SET-128: Preference/DataStore Singleton Constructor Privatization, `applicationContext` Normalization & Leak Elimination (`ATT-8121`) [Verified]
- **Target Release**: `V5.0.0`
- **Tests**: `TST-SET-130`
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit
- **Implementation**: `SettingsDataStore.kt`, `MyPreferenceManager.kt`, `ProFeatureManager.kt`, `ATrainingTrackerMap.kt`, `ZonesSettingsViewModel.kt`, `ZonesSettingsActivity.kt`, `TrackingViewModel.kt`, `MainActivityWithNavigation.kt`, `ATrainingTrackerApp.kt`, `SupporterDebugSection.kt`
- **Localization Parity**: `N/A - 0 string changes`
- **Rationale**: Although `TuningPreferencesDataStore` was already privatized in `ATT-7874` (`REQ-TRK-079.1`), `SettingsDataStore`, `MyPreferenceManager`, and `ProFeatureManager` still expose public constructors that allocate uncancelled `SupervisorJob` coroutine scopes and/or retain raw `Activity` contexts across 24 production call sites, while `ATrainingTrackerMap.kt` retains an unused `tuningConfigFlow.collectAsState` collector (`ATT-8121`).
- **Description**:
  1. *Singleton Encapsulation & `applicationContext` Normalization (`REQ-SET-128.1`)*: `SettingsDataStore`, `MyPreferenceManager`, and `ProFeatureManager` SHALL enforce `private` primary constructors (preventing direct production instantiation at compile time), normalize retained contexts to `context.applicationContext ?: context`, and provide thread-safe `getInstance(context)` alongside `@VisibleForTesting internal fun createForTesting(...)` (with injectable test `CoroutineScope`/`DataStore`) and synchronized `setInstanceForTesting(...)`.
  2. *Shared Scope Reuse & Production Caller Migration (`REQ-SET-128.2`)*: `MyPreferenceManager` SHALL reuse a single process-wide `CoroutineScope(SupervisorJob() + Dispatchers.IO)` across calls (zero orphaned `SupervisorJob` allocations per invocation). All production callers of `SettingsDataStore(...)` (5 sites in 3 files), `MyPreferenceManager(...)` (17 sites in 13 files), and `ProFeatureManager(...)` (2 sites in 2 files) SHALL migrate to `getInstance(...)`.
  3. *Dead Map Collector Removal (`REQ-SET-128.3`)*: `ATrainingTrackerMap.kt` SHALL remove the unused `tuningDataStore`/`tuningConfig` (`tuningConfigFlow.collectAsState`) local declarations and their unused imports.
  4. *JVM/Robolectric Singleton Isolation & Hot-Path Invariants (`REQ-SET-128.4`)*: `SettingsDataStore.getInstance(context)` and `MyPreferenceManager.getInstance(context)` MUST preserve `InMemoryPreferencesDataStore` fallback for unmocked JVM tests while upgrading an auto-created fallback (`!isTestOverride`) when a real `filesDir` context arrives, with zero added `runBlocking` in `SettingsDataStore.kt` (`REQ-SET-079`).

**Acceptance Criteria (Given-When-Then)**:
• *Given* `SettingsDataStore`, `MyPreferenceManager`, and `ProFeatureManager`, *When* inspected reflectively and called via `getInstance(activity)` and `getInstance(application)`, *Then* zero non-synthetic public constructors exist, `getInstance` returns identical singleton instances, and retained contexts are `applicationContext` (never `Activity`).
• *Given* repeated calls to `MyPreferenceManager.getInstance` / `createForTesting`, *When* inspected, *Then* all instances share one process-wide `SupervisorJob` scope without allocating orphaned scopes.
• *Given* `ATrainingTrackerMap.kt`, *When* rendered or inspected, *Then* no unused `TuningPreferencesDataStore` collector exists.

**Invariants**: `TuningPreferencesDataStore.kt` untouched (`<= 2,410` LoC, `REQ-TRK-079.1`); zero `runBlocking` in `SettingsDataStore` reads (`REQ-SET-079`); `AndroidViewModel` `@JvmOverloads` reflective constructors preserved; `0` UI string changes.

---

### REQ-SET-129: Polar Retry Sync Linearizable Bridge Yield Quiescence, Mid-Wait Disconnect Recovery & Mid-Discovery Feature Readiness (`ATT-9612`) [Verified]
- **Target Release**: `V5.0.0`
- **Tests**: `TST-SET-131`
- **System Requirement(s)**: `SYSREQ-DEX-007`, `SYSREQ-TRK-002`
- **Priority**: Critical
- **Type**: Functional
- **Verification Method**: Unit, Integration
- **Implementation**: `PolarOnlineStreamingBridge.kt`, `PolarOfflineGattCoordinator.kt`, `PolarH10OfflineController.kt`, `PolarVeritySenseOfflineController.kt`, `my_btle_device.md`
- **Localization Parity**: `N/A - 0 string changes` (`0` edits to `res/values*/strings*.xml`)
- **Rationale**: On Pixel 10 (`ATT-9612`, Workout `2431`, Polar Verity Sense `24825632`), tapping Aftermath *Sync Now* raced with an in-flight `PolarOnlineStreamingBridge` reconnect (`10:57:06.331` `extending handshake timeout to 15000ms`), resulting in a mid-sync `BluetoothGatt.disconnect()/refresh()/close()` teardown (`10:57:36.337`) followed by a `45,000 ms` hang in single-shot `connectWithinTimeout()` (`10:58:06.672`, reconnecting `31 ms` later on yield release) and an Attempt 2 failure during `10–15 s` 8-feature GATT discovery (`10:58:11`).
- **Description**:
  1. *Linearizable `PolarOnlineStreamingBridge` Yield Quiescence (`REQ-SET-129.1`)*: `PolarOnlineStreamingBridge` SHALL serialize its GATT-mutating lifecycle actions (`onSearchTimeout()` `disconnectFromDevice()`, `connectPersistentSession()` `connectToDevice()`, and `onSilentHrWatchdogTimeout()` `DISCONNECT_AND_RECONNECT` `forceDisconnectDevice()`) under `gattActionLock`, re-checking `!isShutDown.get() && !isOfflineGattYielded()` before touching `polarApi`. `pauseForOfflineSync()` SHALL cancel `reconnectJob`, `searchTimeoutJob`, `silentHrWatchdog`, `hrStalenessTimer`, and `streamCoordinator` renegotiations and traverse `synchronized(gattActionLock)` as a quiescence barrier before returning to `withGattYieldAndPolarApi()` so zero late bridge teardowns or competing connects can fire during an active offline sync.
  2. *Disconnect-Recovering & In-Flight-Reusing `connectWithinTimeout()` (`REQ-SET-129.2`)*: `PolarOfflineGattCoordinator.multiplexerCallback` SHALL track alias-aware `connectingDeviceIds` (`isDeviceConnecting()`, cleared on `deviceConnected`, `deviceDisconnected`, `blePowerStateChanged(false)`, `forceDisconnectDevice`, and `resetForTesting()`). On managed APIs (`isManagedApi(api)`), `connectWithinTimeout(api, polarId, timeoutMs)` SHALL return `true` immediately when `isDeviceConnected(polarId)`, register a `finally`-cleaned transient multiplexer listener for `deviceConnected`/`deviceDisconnected`, await an already in-flight handshake without duplicate `connectToDevice()` when `isDeviceConnecting(polarId)` on initial entry, and immediately cancel any wedged `waitForConnection()`, wait `connectRetryBackoffMs` (`250 ms` default, `@VisibleForTesting`), and re-issue `api.connectToDevice(polarId)` within the remaining `timeoutMs` budget whenever `deviceDisconnected` or a transient non-cancellation disconnect error occurs mid-wait while strictly rethrowing `CancellationException`. Unmanaged `apiOverride` mocks MUST preserve legacy single-shot behavior.
  3. *Alias-Aware `20 s` `awaitFeaturesReady()` & H10/Verity Sibling Parity (`REQ-SET-129.3`)*: `PolarOfflineGattCoordinator.FEATURE_READY_TIMEOUT_MS` SHALL be `20_000L` ms and `awaitFeaturesReady()` SHALL check `api.isFeatureReady(alias, feature)` across all registered MAC/8-hex aliases (`collectAliasClosure(polarId)`). Both `PolarH10OfflineController` (`syncPendingWorkout`, `stopAndBackfillWorkout`, `executeArming`) and `PolarVeritySenseOfflineController` (`connectAndMatchSession`, `performArmingSequence`, `stopAndFetchOfflineHr`, `removeOfflineRecord`) SHALL connect through `connectWithinTimeout()`, and `PolarH10OfflineController` SHALL await required offline features via `awaitFeaturesReady()` on managed APIs (`isManagedApi(api)`), preserving unmanaged `apiOverride` mocks.
  4. *Monolith Ceilings, Public Signature Pin & Detailed Design (`REQ-SET-129.4`)*: Keep `PolarOfflineGattCoordinator.kt <= 1,500`, `PolarOnlineStreamingBridge.kt <= 1,500` (unchanged constructor & public JVM signatures), `PolarVeritySenseOfflineController.kt <= 1,500`, `PolarH10OfflineController.kt <= 1,500`; keep `BACKFILL_TIMEOUT_MS` (`30_000L` H10 / `45_000L` Verity) and `STOP_CONNECTION_TIMEOUT_MS` (`5_000L`) unchanged; update `docs/architecture/detailed_design/my_btle_device.md` (`REQ-PRO-114`); `0` edits to `DeviceManager.java`, `TrackerService.java`, `DevicesDatabaseManager.java`, and `strings*.xml`.
- **Requirement Archaeology & Chesterton's Fence Audit**:
  1. *Original Requirement ID & Target*: Refines `REQ-SET-120` (`ATT-9175`), `REQ-SET-119` (`ATT-8969`), `REQ-SET-118` (`ATT-8690`), `REQ-CON-050`, `REQ-CON-054` across `PolarOfflineGattCoordinator.kt`, `PolarOnlineStreamingBridge.kt`, `PolarVeritySenseOfflineController.kt`, `PolarH10OfflineController.kt`.
  2. *Historical Origin & Commit Trace*: `ATT-8690` (`34b78941`), `ATT-8969` (`8e44039d`), `ATT-9155` (`f7ed7d94`), `ATT-9175` (`ed05d679`).
  3. *Root Reason for Existing Formulation*: `ATT-9175` cancelled timers without a quiescence barrier; `ATT-8969` assumed a single `connectToDevice()` call would remain uncontested until `waitForConnection()` returned; `10 s` `FEATURE_READY_TIMEOUT_MS` predated 8-feature GATT discovery durations.
  4. *Preservation of Core Invariants*: Preserves `5 s` absent-sensor timeout (`REQ-CON-045.1`), `5 s` stop-time connection cap (`REQ-SET-119.3`), `30 s`/`45 s` backfill timeouts (`SYSREQ-DEX-007`), sibling Polar isolation (`REQ-SET-118.3`), `CancellationException` rethrow (`REQ-SET-119.2`), unmanaged `apiOverride` mocks, and pinned JVM signatures (`InterfaceSignatureDriftContractTest`).

**Acceptance Criteria (Given-When-Then)**:
• *Given* an in-flight `PolarOnlineStreamingBridge` search timeout, reconnect wake-up, or silent-HR disconnect racing with `withGattYieldAndPolarApi()`, *When* `pauseForOfflineSync()` completes, *Then* zero bridge `disconnectFromDevice()` / `connectToDevice()` / `forceDisconnectDevice()` calls execute while the offline yield lease is held.
• *Given* a managed `PolarBleApi` whose stale pre-yield handshake fires `deviceDisconnected` mid-`connectWithinTimeout()` while `waitForConnection()` stays blocked, *When* `deviceDisconnected` arrives, *Then* `connectWithinTimeout()` cancels the wedged wait, re-invokes `api.connectToDevice(polarId)` after `connectRetryBackoffMs`, and connects on Attempt 1 instead of stalling until `45 s`.
• *Given* a Polar Verity Sense or Polar H10 already connected or mid-discovery (`isDeviceConnected` / `isDeviceConnecting`, `bleSdkFeatureReady` arriving between `10 s` and `20 s` or keyed by alias), *When* `syncPendingWorkout()` runs, *Then* `connectWithinTimeout()` avoids conflicting duplicate `connectToDevice()` calls and `awaitFeaturesReady()` completes backfill on Attempt 1.

**Invariants**: `H10 BACKFILL_TIMEOUT_MS = 30_000L`, `Verity BACKFILL_TIMEOUT_MS = 45_000L`, `STOP_CONNECTION_TIMEOUT_MS = 5_000L`; `5 s` absent-sensor timeout (`REQ-CON-045.1`); sibling isolation (`REQ-SET-118.3`); `CancellationException` propagation (`REQ-SET-119.2`); unmanaged `apiOverride` compatibility; all 4 Polar files `<= 1,500` LoC with unchanged public JVM signatures; `0` UI/string/DB edits.

---

### REQ-SET-130: Polar Offline HR UTC Timestamp Alignment, Bounded Gap Matching & Commit-Gated Flash Cleanup (`ATT-9611`) [Verified]
- **Target Release**: `V5.0.0`
- **Tests**: `TST-SET-132`
- **System Requirement(s)**: `SYSREQ-DEX-007`
- **Priority**: Critical
- **Type**: Functional
- **Verification Method**: Unit, Integration
- **Implementation**: `PolarOfflineHrTimestampAligner.kt`, `PolarVeritySenseOfflineController.kt`, `PolarH10OfflineController.kt`, `WorkoutSamplesDatabaseManager.java`
- **Localization Parity**: `N/A - 0 string changes` (`0` edits to `res/values*/strings*.xml`)
- **Rationale**: On Pixel 10 (`ATT-9611`, Workout `2430`, `Europe/Berlin` `CET = UTC+1`, Polar Verity Sense `24825632`), `PolarVeritySenseOfflineController.resolveRecordingAnchorMs()` converted Polar BLE SDK `8.6.0` `HrOfflineRecording.startTime` (parsed as UTC by `OfflineRecordingData.parseStartTime`) using `ZoneId.systemDefault()`, shifting the entire offline HR series `-3,600 s` earlier than UTC `WorkoutSamples.time`. Because `WorkoutSamplesDatabaseManager.patchMissingHeartRateSamples()` had zero maximum timestamp tolerance and `applyBackfillAndCommit()` ignored `patchedCount`, all 73 missing HR rows were flat-line clamped to the single final sample (`106 bpm`) and the genuine flash recording was deleted with zero `Log.i` diagnostics.
- **Description**:
  1. *Pure-JVM UTC Anchor Alignment & Timezone-Safe Entry Selection (`REQ-SET-130.1`)*: `PolarOfflineHrTimestampAligner` (pure-JVM, zero `android.*` imports) SHALL convert `HrOfflineRecording.startTime` strictly via `ZoneOffset.UTC` (falling back to `fallbackAnchorMs` ONLY when `startTime` is null, `< 2020`, or yields `<= 0L`), align valid 1-Hz HR samples (`25..250 bpm`, `hr > 0 ? hr : correctedHr`) to `anchorMs + index * 1_000L`, honor `Types.PbLocalDateTime.timeZoneOffset` strictly when `hasTimeZoneOffset()` is true AND `timeZoneOffset in -1080..1080` (guarding against `DateTimeException` on malformed protobuf bytes, else `fallbackZone`), and match `PolarOfflineRecordingEntry` entries within `[windowStartMs - toleranceMs, effectiveEndMs + toleranceMs]` evaluating all valid local offsets (`zoneId.rules.getValidOffsets(entry.date)`, handling DST fall-back 2-offset overlap hours) first and falling back to UTC path matching ONLY when zero local matches exist in a non-UTC timezone.
  2. *Bounded Nearest-Sample Matching (`REQ-SET-130.2`)*: `WorkoutSamplesDatabaseManager.patchMissingHeartRateSamples()` SHALL enforce `MAX_HR_MATCH_TOLERANCE_MS = 5_000L` (`<= 5 s`) when selecting the nearer of `hrMap.floorEntry(tsMs)` and `hrMap.ceilingEntry(tsMs)` for every missing `SensorType.HR` row (`HR IS NULL OR HR <= 0`), skipping any row whose nearest offline HR sample exceeds `5_000 ms` (never flat-line clamping out-of-window rows) while preserving existing live HR/GPS/speed/cadence/power values, the `totalRows == 0` empty-table insert path, and its exact public JVM signature.
  3. *Commit-Gated Flash Cleanup, Complete-HR Pending Fast Path & Structured Diagnostics (`REQ-SET-130.3`)*: `PolarVeritySenseOfflineController.executePendingSyncSession()` SHALL provide a symmetric zero-gap fast path when `session.offlineEntry != null && !baseFileName.isNullOrEmpty() && !shouldBackfill(appContext, baseFileName)` (preserving full `session.swimSession` backfill): skip PFTP HR download, log `Log.i` (`alreadyCompleteHr=true`), clear `polarSyncPending`, remove the matched flash entry, refresh the workout, and return `true`. In `applyBackfillAndCommit()` (defense-in-depth), capture `patchedCount` from `PolarOfflineGattCoordinator.patchMissingHeartRateAndRecordSensorType()`; whenever `baseFileName.isNullOrEmpty()`, `hrPairs.isEmpty()`, or `(patchedCount <= 0 && hasMissingHeartRateSamples(baseFileName))`, it MUST skip `recomputeHrSummaryMetrics()`, MUST NOT clear `polarSyncPending`, MUST log `Log.w`, and MUST return `false` so neither `handleStopBackfillMatch()` nor `executePendingSyncSession()` invokes `api.removeOfflineRecord()`. Conversely, when `patchedCount == 0 && !hasMissingHeartRateSamples(baseFileName)` (with non-empty `baseFileName` and `hrPairs`), it SHALL log `Log.i`, clear pending if requested, refresh the workout, and return `true` (preventing permanent pending-retry livelock). Both `PolarVeritySenseOfflineController` and `PolarH10OfflineController` SHALL emit structured `Log.i` diagnostics (`Log.w` on zero-patch rejection) recording `workoutId`, matched `entry.path`, `startTime` (Verity), `anchorMs`, `samples`/`beats`, `hrRange=[min..max]`, `patchedCount`, and `flashRemoved`.
  4. *Monolith Ceilings, Pinned Signatures & Zero Side Effects (`REQ-SET-130.4`)*: Keep `PolarVeritySenseOfflineController.kt <= 1,500` LoC (reduced below `1,492`), `PolarH10OfflineController.kt <= 1,500` LoC, `WorkoutSamplesDatabaseManager.java <= 1,500` LoC with unchanged public JVM signatures (`InterfaceSignatureDriftContractTest`); preserve Verity Sense `WorkoutRr.db` / `HrvPostWorkoutRecomputer` exclusion (`REQ-SET-109.2`) and H10 RR-only backfill (`intervals.isNotEmpty()`); `0` DB schema bumps; `0` edits to `PolarOfflineGattCoordinator.kt`, `PolarOnlineStreamingBridge.kt`, `TrackerService.java`, and `res/values*/strings*.xml`.
- **Requirement Archaeology & Chesterton's Fence Audit**:
  1. *Original Requirement ID & Target*: Refines `REQ-SET-109` (`ATT-7924`), `REQ-SET-105` (`ATT-7918`), `REQ-TRK-084.4`, `REQ-TRK-093` (`ATT-8124`) across `PolarVeritySenseOfflineController.kt`, `PolarH10OfflineController.kt`, and `WorkoutSamplesDatabaseManager.java`.
  2. *Historical Origin & Commit Trace*: `ATT-7918` (`patchMissingHeartRateSamples()`), `ATT-7924` (`resolveRecordingAnchorMs()` / `applyBackfillAndCommit()`), `ATT-8124` (H10 UTC epoch anchor).
  3. *Root Reason for Existing Formulation*: `ATT-7924` assumed local wall-clock semantics for Polar SDK `LocalDateTime`; `ATT-7918` used unbounded nearest-neighbor `floor`/`ceiling` to absorb ~1 s clock jitter.
  4. *Preservation of Core Invariants*: Preserves gap-only canonical `HR` patching, live HR/GPS/power preservation, `totalRows == 0` swim insert path, Verity Sense HRV isolation (`REQ-SET-109.2`), H10 RR-only backfill parity, pinned JVM signatures, and `< 1,500` LoC monolith budgets.

**Acceptance Criteria (Given-When-Then)**:
• *Given* a device in `Europe/Berlin` (`CET +1` / `CEST +2`) or `America/New_York` (`EST -5`) with UTC `WorkoutSamples.time` (`09:43:06Z..09:45:29Z`) and Polar SDK `HrOfflineRecording.startTime = 2026-11-22T09:43:08`, *When* `PolarOfflineHrTimestampAligner` resolves the anchor and aligns samples, *Then* `anchorMs` equals exact `2026-11-22T09:43:08Z` with zero timezone shift and dynamic 1-Hz HR samples align second-for-second.
• *Given* an offline HR series shifted by `1 h` or an unrecorded gap `> 5_000 ms` from the nearest offline sample, *When* `WorkoutSamplesDatabaseManager.patchMissingHeartRateSamples()` executes, *Then* out-of-window rows remain unpatched (`0` flat-line clamped rows) while rows within `<= 5_000 ms` receive authentic nearest HR values.
• *Given* `patchMissingHeartRateSamples()` returns `0` while `hasMissingHeartRateSamples(baseFileName)` is still `true`, *When* `stopAndBackfillWorkout()` or `syncPendingWorkout()` executes, *Then* `applyBackfillAndCommit()` returns `false`, `api.removeOfflineRecord()` and `clearPolarSyncPending()` are NOT invoked, and `Log.w` records the rejection; conversely, when `hasMissingHeartRateSamples(baseFileName)` is `false` (already complete HR), `executePendingSyncSession()` fast-paths cleanup and `applyBackfillAndCommit()` returns `true` cleanly without livelock.

**Invariants**: Canonical `HR` gap-only patching; live HR/GPS/Power preserved; `totalRows == 0` empty-table insert preserved; zero `WorkoutRr.db` writes on Verity Sense; H10 RR-only backfill preserved; `removeOfflineRecord()` gated on `patchedCount > 0 || !hasMissingHeartRateSamples` (with non-empty `baseFileName`); DST fall-back & `-1080..1080` offset guards; all touched files `<= 1,500` LoC; pinned JVM signatures unchanged; `0` UI/string/DB-schema edits.

### REQ-SET-131: Zero-`runBlocking`, Cold-Start-Safe Smartphone Raw Telemetry Opt-In Resolution (`ATT-9578`) [Verified]
- **Target Release**: `V5.0.0`
- **Tests**: `TST-SET-133`
- **Priority**: Critical
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Implementation**: `TuningPreferencesDataStore.kt`, `SettingsDataStoreJavaHelper.kt`, `SmartphoneRawTelemetryRecorder.kt`, `DataStoreHotPathContractTest.kt`
- **Rationale**: Eliminates main-thread `runBlocking` DataStore reads introduced for `recordSmartphoneRawTelemetryEnabled` (`b0bf0bc1` in `SettingsDataStoreJavaHelper.kt`, relocated to `SmartphoneRawTelemetryRecorder.isEnabled` in `7b225dee`) while preserving 100% cold-start opt-in accuracy (`TST-EXP-045.1`) across `TrackerService` start/resume hot paths without blocking the service main thread (`REQ-SET-079`).
- **Description**:
  1. *Cold-Start-Safe Eager Cache & Dual-Write Mirror (`REQ-SET-131.1`)*: `TuningPreferencesDataStore` SHALL replace the `by lazy` `stateIn` property `recordSmartphoneRawTelemetryEnabled` with an eager `MutableStateFlow<Boolean>` (`_recordSmartphoneRawTelemetryEnabled`) declared strictly before `init`. When `dataStoreOverride == null`, construction SHALL synchronously seed both `_tuningConfigStateFlow` (`TuningConfig.recordSmartphoneRawTelemetryEnabled`) and `_recordSmartphoneRawTelemetryEnabled` from default `SharedPreferences` (`KEY_RECORD_SMARTPHONE_RAW_TELEMETRY.name`, default `false`, exception-safe). All write paths (`saveTuningConfig`, `updateRecordSmartphoneRawTelemetryEnabled` via `editAndSync`, `resetToDefaults`) and the `init` DataStore collector SHALL synchronize both flows and update default `SharedPreferences` via `.apply()` strictly when `dataStoreOverride == null` and the mirrored value differs. When `dataStoreOverride != null`, `SharedPreferences` MUST NOT be read or mutated.
  2. *Non-Blocking $O(1)$ Java Bridge (`REQ-SET-131.2`)*: `SettingsDataStoreJavaHelper.isRecordSmartphoneRawTelemetryEnabled(context: Context): Boolean` SHALL read `getTuningConfig(context).recordSmartphoneRawTelemetryEnabled` in $O(1)$ with zero `runBlocking`, preserving its public `@JvmStatic` signature and `mockkStatic(SettingsDataStoreJavaHelper::class)` compatibility (`REQ-SET-079.2`).
  3. *Zero-`runBlocking` Recorder Opt-In Delegation (`REQ-SET-131.3`)*: `SmartphoneRawTelemetryRecorder.isEnabled(context: Context?): Boolean` MUST remove `runBlocking { ...recordSmartphoneRawTelemetryFlow.first() }` and delegate directly to `SettingsDataStoreJavaHelper.isRecordSmartphoneRawTelemetryEnabled(context)` inside its exception-safe guard so `TrackerService` start/resume hot paths never park the main thread while preserving `TST-EXP-045.1` cold-start accuracy.
  4. *Hot-Path Contract Regression Guard & Budgets (`REQ-SET-131.4`)*: `DataStoreHotPathContractTest` SHALL continue banning `runBlocking` in `SettingsDataStoreJavaHelper.kt` and SHALL additionally assert that `SmartphoneRawTelemetryRecorder.kt` contains zero `runBlocking` calls outside timeout-bounded `stopBlocking()`. `TuningPreferencesDataStore.kt` MUST stay `<= 2,410` LoC (`ArchitectureConformanceContractTest`), `TrackerService.java` and `TrainingApplication.java` untouched, and `0` string changes.
- **Requirement Archaeology & Chesterton's Fence Audit**:
  1. *Original Requirement ID & Target*: Refines `REQ-SET-079` (`ATT-3999`) and `REQ-EXP-045.1` (`ATT-9348`) on `TuningPreferencesDataStore.kt`, `SettingsDataStoreJavaHelper.kt`, `SmartphoneRawTelemetryRecorder.kt`, and `DataStoreHotPathContractTest.kt`.
  2. *Historical Origin & Commit Trace*: `b0bf0bc1` (`ATT-9348`) added `runBlocking` in `SettingsDataStoreJavaHelper.kt`; `ATT-9569` flagged `DataStoreHotPathContractTest`; `7b225dee` (`ATT-8136`) relocated `runBlocking` into `SmartphoneRawTelemetryRecorder.isEnabled()`.
  3. *Root Reason for Existing Formulation*: Prevented stale cold-start `StateFlow` `initialValue = false` from silently dropping raw smartphone telemetry on fresh process launch or post-crash `TrackerService` restart.
  4. *Preservation of Core Invariants*: DataStore remains single authoritative source of truth (`ADR-005`), cold-start accuracy (`TST-EXP-045.1`) is 100% preserved, main-thread `runBlocking` DataStore reads are eliminated across both `SettingsDataStoreJavaHelper` and `SmartphoneRawTelemetryRecorder`, and `mockkStatic` / `InMemoryPreferencesDataStore` isolation remain intact.

**Acceptance Criteria (Given-When-Then)**:
• *Given* `recordSmartphoneRawTelemetryEnabled = true` was persisted via `saveTuningConfig` or `updateRecordSmartphoneRawTelemetryEnabled(true)`, *When* a cold start occurs (`TuningPreferencesDataStore.setInstanceForTesting(null)`) and `SmartphoneRawTelemetryRecorder.isEnabled(context)` or `SettingsDataStoreJavaHelper.isRecordSmartphoneRawTelemetryEnabled(context)` is invoked synchronously, *Then* both return `true` immediately in $O(1)$ without `runBlocking`.
• *Given* `resetToDefaults()` or `updateRecordSmartphoneRawTelemetryEnabled(false)` executes, *When* a cold start follows, *Then* both accessors return `false` immediately.
• *Given* `DataStoreHotPathContractTest` executes, *When* auditing `SettingsDataStoreJavaHelper.kt` and `SmartphoneRawTelemetryRecorder.kt`, *Then* zero `runBlocking` DataStore reads exist.

**Invariants**: Zero `runBlocking` DataStore reads; `TST-EXP-045.1` cold-start accuracy preserved; `dataStoreOverride != null` unit tests hermetically isolated from `SharedPreferences`; `mockkStatic(SettingsDataStoreJavaHelper::class)` preserved; `TuningPreferencesDataStore.kt <= 2,410` LoC; `0` edits to `TrackerService.java`, `TrainingApplication.java`, UI, or strings.

---

## Change History

| Version | Date | Author | Ticket | Summary of Changes |
| :--- | :--- | :--- | :--- | :--- |
| `1.0.0` | 2026-11-17 | AI Agent (`ATT-8164`) | `ATT-8164` | Added standardized `DOC-REQ-SET` work-product control header and Change History. |
| `1.0.1` | 2026-11-17 | AI Agent (`ATT-8690`) | `ATT-8690` | Added `REQ-SET-118` (process-wide ref-counted `PolarBleApi` singleton, multiplexed callbacks, MAC/8-hex matching, unworn-sensor gating). |
| `1.0.2` | 2026-11-17 | AI Agent (`ATT-8690`) | `ATT-8690` | Promoted `REQ-SET-118` to `[Implemented]` following Stage 4 construction. |
| `1.0.3` | 2026-11-17 | AI Agent (`ATT-8690`) | `ATT-8690` | Promoted `REQ-SET-118` to `[Verified]` following Stage 5 unit verification. |
| `1.0.5` | 2026-11-18 | AI Agent (`ATT-8714`) | `ATT-8714` | Added symmetric `TST-CON-050` link on `REQ-SET-112` (`REQ-PRO-127` parity). |
| `1.0.6` | 2026-11-19 | AI Agent (`ATT-8969`) | `ATT-8969` | Added `REQ-SET-119` (lifecycle-safe Aftermath retry scope, HR graph refresh, and disconnected stop-time fast-fail / bounded connection wait). |
| `1.0.7` | 2026-11-19 | AI Agent (`ATT-8969`) | `ATT-8969` | Promoted `REQ-SET-119` to `[Implemented]` following Stage 4 construction (`PolarRetrySyncCoordinator`, fast-fail, 5s connection bound). |
| `1.0.8` | 2026-11-19 | AI Agent (`ATT-8969`) | `ATT-8969` | Promoted `REQ-SET-119` to `[Verified]` following Stage 5 verification (`155/155` unit/Compose/contract tests). |
| `1.0.9` | 2026-11-20 | AI Agent (`ATT-8328`) | `ATT-8328` | Promoted 39 unblocked `REQ-SET-*` requirements to `[Verified]` following Gate 4 (`2a149eca`, `119/119` PASS); retained `REQ-SET-014`, `019`, `021`, `069` at `[Implemented]`. |
| `1.0.10` | 2026-11-20 | Lane A Flash (`ATT-8330`, `--as agent1`) | `ATT-8330` | Promoted `REQ-SET-003` (`TST-UNT-003`) to `[Verified]` following Gate 4 verification (`3e20473b`, `Rule 7.5`). |
| `1.0.11` | 2026-11-20 | Lane B Author (`ATT-9175`, `--as agent1`) | `ATT-9175` | Added `REQ-SET-120` (`[Planned]`, `.1`–`.4`, linked to `SYSREQ-DEX-007`/`SYSREQ-TRK-002`) for alias-aware ref-counted offline GATT yield pause/resume on `PolarOnlineStreamingBridge` and single-shot bounded `deviceConnecting` handshake grace (`N/A - no UI strings`). |
| `1.0.12` | 2026-11-20 | Lane B Author (`ATT-9175`, `--as agent1`) | `ATT-9175` | Promoted `REQ-SET-120` to `[Implemented]` following Stage 4 construction (`PolarOfflineGattCoordinator.kt` `1,285/1,500`, `PolarOnlineStreamingBridge.kt` `1,466/1,500`, `my_btle_device.md` `v1.0.7`, `0` edits to `PolarVeritySenseOfflineController.kt`/`DeviceManager.java`). |
| `1.0.13` | 2026-11-20 | Lane B Author (`ATT-9175`, `--as agent1`) | `ATT-9175` | Promoted `REQ-SET-120` to `[Verified]` in Stage 5 reusing Gate 4 (`ATT-9378` @ `cbe8ef29`) `161/161 PASS` across 15 suites + `:app:assembleDebug` + `0` new production Kotlin warnings (`Rule 7.5`, `0` `app/` drift since `cbe8ef29`), `4/4` Python safety linters PASS, `--check-verified-evidence` EXIT `0`, and `docs/engineering/walkthroughs/ATT-9175_walkthrough.md`. |
| `1.0.14` | 2026-11-20 | Lane B Author (`ATT-9175`, `--as agent1`) | `ATT-9175` | Stage 6 Bounce 1/3 heal: removed sticky `isPausedForOfflineSync` so `PolarOfflineGattCoordinator` is single atomic source of truth, guarded `pauseForOfflineSync()` against stale calls, armed `silentHrWatchdog` on resume after mid-yield `deviceConnected`, kept yielded bridge alive via `shouldKeepAliveOnSearchTimeout()` in `WAITING_FOR_START`, tracked `pausedOfflineYieldListeners` set for alias-expansion transitions, logged exact `timeoutMs`, and set `[Implemented]` pending Gate 4 / Stage 5 re-verification. |
| `1.0.15` | 2026-11-20 | Lane B Author (`ATT-9175`, `--as agent1`) | `ATT-9175` | Promoted `REQ-SET-120` to `[Verified]` in Stage 5 Bounce 1 reusing Gate 4 Bounce 1 (`ATT-9384` @ `d2c4953f`) `164/164 PASS` across 15 suites + `:app:assembleDebug` + `0` new production Kotlin warnings (`Rule 7.5`, `0` `app/` drift since `d2c4953f`), `4/4` Python safety linters PASS, `--check-verified-evidence` EXIT `0`, and refreshed `docs/engineering/walkthroughs/ATT-9175_walkthrough.md`. |
| `1.0.16` | 2026-11-21 | Lane B Flash (`ATT-6048`, `--as agent1`) | `ATT-6048` | Added and verified `REQ-SET-121` (`saveTuningConfig` 4 missing Auto-Pause keys, `BoundedPref` descriptors, and centralized `SettingsDataStore` zone defaults; Stage 4 verified `167/167` PASS @ `2038d89b`). |
| `1.0.17` | 2026-11-21 | Lane A Author (`ATT-9346`, `--as agent1`) | `ATT-9346` | Added `REQ-SET-122` (`[Planned]`, `.1`–`.4`, linked to `SYSREQ-TRK-002`/`SYSREQ-TRK-003`) for dynamic Polar H10 `100 <-> 200 Hz` `ACC` rate renegotiation, sibling-coroutine/`ACC_OWNER_ONLINE`-safe/teardown-safe `PolarPmdStreamCoordinator.renegotiateSlot()`, and `PolarH10RunningDynamicsEstimator.resetForGap()` `avgDtMs` recovery (`N/A - no UI strings`). |
| `1.0.18` | 2026-11-21 | Lane A Author (`ATT-9346`, `--as agent1`) | `ATT-9346` | Promoted `REQ-SET-122` to `[Implemented]` following Stage 4 construction (`PolarPmdStreamCoordinator.kt` `463` LoC, `PolarOnlineStreamingBridge.kt` `1,492/1,500` LoC, `PolarH10RunningDynamicsEstimator.kt` `546` LoC; `89/89 PASS` across 8 suites + `3/3` Python safety linters). |
| `1.0.19` | 2026-11-21 | Lane A Author (`ATT-9346`, `--as agent1`) | `ATT-9346` | Promoted `REQ-SET-122` to `[Verified]` in Stage 5 reusing Gate 4 (`ATT-9448` @ `1f92ed6c`, `Rule 7.5`, `0` `app/` drift) `89/89 PASS` across 8 suites (`6/6` verbatim `TST-SET-124` methods), `3/3` Python safety linters PASS, `--check-verified-evidence` EXIT `0`, and `docs/engineering/walkthroughs/ATT-9346_walkthrough.md`. |
| `1.0.20` | 2026-11-21 | Lane A Author (`ATT-9346`, `--as agent1`) | `ATT-9346` | Stage 6 Bounce 1/3 heal: added `onAborted` callback & atomic `slotLock` relaunch handoff in `PolarPmdStreamCoordinator.renegotiateSlot()`, `cancelAllRenegotiations()` returning cancelled slot set, `resumeAccAfterOfflineSync` post-yield `ACC` recovery & `preAcquire` yield/connect guard in `PolarOnlineStreamingBridge` (`1,493/1,500` LoC), `+2` regression tests (`91/91 PASS`), and set `[Implemented]` pending Gate 4 / Stage 5 Bounce 1 re-verification. |
| `1.0.21` | 2026-11-21 | Lane A Author (`ATT-9346`, `--as agent1`) | `ATT-9346` | Promoted `REQ-SET-122` to `[Verified]` in Stage 5 Bounce 1 reusing Gate 4 Bounce 1 (`ATT-9453` @ `522726b7`, `Rule 7.5`, `0` `app/` drift) `91/91 PASS` across 8 suites (`8/8` verbatim `TST-SET-124` methods), `3/3` Python safety linters PASS, `--check-verified-evidence` EXIT `0`, and refreshed `docs/engineering/walkthroughs/ATT-9346_walkthrough.md`. |
| `1.0.22` | 2026-11-21 | Lane A Author (`ATT-9346`, `--as agent1`) | `ATT-9346` | Stage 6 Bounce 2/3 heal: added Dekker store-then-load `resumeAccAfterOfflineSync` handoff in `tryAcquireOnlineAccChannel()` (preserving one-shot `FEATURE_POLAR_ONLINE_STREAMING` during offline GATT yield) and `onAccRenegotiationAborted()` (closing yield-release-before-`finally` micro-race), moved pure `buildSettingWithRate()` to `PolarPmdStreamCoordinator` (`PolarOnlineStreamingBridge.kt` `1,492/1,500` LoC), added regression test (`92/92 PASS`), and set `[Implemented]` pending Gate 4 / Stage 5 Bounce 2 re-verification. |
| `1.0.23` | 2026-11-21 | Lane A Author (`ATT-9346`, `--as agent1`) | `ATT-9346` | Promoted `REQ-SET-122` to `[Verified]` in Stage 5 Bounce 2 reusing Gate 4 Bounce 2 (`ATT-9459` @ `8d7a449f`, `Rule 7.5`, `0` `app/` drift) `92/92 PASS` across 8 suites (`9/9` verbatim `TST-SET-124` methods), `3/3` Python safety linters PASS, `--check-verified-evidence` EXIT `0`, and refreshed `docs/engineering/walkthroughs/ATT-9346_walkthrough.md`. |
| `1.0.24` | 2026-11-21 | Lane A Author (`ATT-9346`, `--as agent1`) | `ATT-9346` | Stage 6 Bounce 3/3 final heal: eliminated speculative `state.active = true` before `preAcquire()` in `PolarPmdStreamCoordinator.launchStream()` (`497` LoC) for 100% linearizable Dekker handoff, guarded `onAccRenegotiationAborted()` with `!streamCoordinator.isRenegotiating(ACC)` in `PolarOnlineStreamingBridge` (`1,492/1,500` LoC), added 2 regression tests (`94/94 PASS`), and set `[Implemented]` pending Gate 4 / Stage 5 Bounce 3 re-verification. |
| `1.0.25` | 2026-11-21 | Lane A Author (`ATT-9346`, `--as agent1`) | `ATT-9346` | Promoted `REQ-SET-122` to `[Verified]` in Stage 5 Bounce 3 reusing Gate 4 Bounce 3 (`ATT-9462` @ `939725f4`, `Rule 7.5`, `0` `app/` drift) `94/94 PASS` across 8 suites (`11/11` verbatim `TST-SET-124` methods), `3/3` Python safety linters PASS, `--check-verified-evidence` EXIT `0`, and refreshed `docs/engineering/walkthroughs/ATT-9346_walkthrough.md`. |
| `1.0.26` | 2026-11-21 | Single-Lane Author (`ATT-9243`, `--as agent1`) | `ATT-9243` | Added `REQ-SET-123` (`[Planned]`, `.1`–`.4`, linked to `SYSREQ-TRK-002`) for never-connected paired BLE passive workout convergence (`READY -> TRACKING` edge), scan-assist gating & `attemptBackgroundReconnect` timeout skipping, `>= 60 s` disconnect floor, and `DeviceSearchPeriodicRetryCoordinator` keep-alive `deviceTries` cleanup (`N/A - no UI strings`). |
| `1.0.27` | 2026-11-21 | Single-Lane Author (`ATT-9243`, `--as agent1`) | `ATT-9243` | Promoted `REQ-SET-123` to `[Implemented]` following Stage 4 construction (`MyBTLEDevice.java` `1,424` LoC, `DeviceSearchPeriodicRetryCoordinator.kt` `236` LoC, `my_btle_device.md` `v1.0.8`, `DeviceManager.java` & `PolarOnlineStreamingBridge.kt` untouched; `88/88 PASS` across 9 Tier-1 suites + `3/3` Python safety linters, `0` new production warnings). |
| `1.0.28` | 2026-11-21 | Single-Lane Author (`ATT-9243`, `--as agent1`) | `ATT-9243` | Promoted `REQ-SET-123` to `[Verified]` in Stage 5 reusing Gate 4 (`ATT-9483` @ `85386e63`, `Rule 7.5`, `0` `app/` drift) `88/88 PASS` across 9 Tier-1 suites (`8/8` verbatim `TST-SET-125` methods verified, `0` new production warnings), `3/3` Python safety linters PASS, `--check-verified-evidence` EXIT `0`, and `docs/engineering/walkthroughs/ATT-9243_walkthrough.md`. |
| `1.0.29` | 2026-11-21 | Lane 2 Author (`ATT-6042`, `--as agent1`) | `ATT-6042` | Added `REQ-SET-124` `[Planned]` (`SYSREQ-TRK-002`) for shared pure-JVM `CscRevolutionTracker` extraction and modular `UINT16` event-time rollover preservation across `BTLEBikeDevice` (`1024 Hz`) and `BTLEBikePowerDevice` (`2048 Hz`). |
| `1.0.30` | 2026-11-21 | Lane 2 Author (`ATT-6042`, `--as agent1`) | `ATT-6042` | Promoted `REQ-SET-124` to `[Implemented]` following Stage 4 construction (`CscRevolutionTracker.kt` `209` LoC, `BTLEBikeDevice.java` `258` LoC, `BTLEBikePowerDevice.java` `425` LoC; zero `android.*` dependencies in tracker, zero string edits). |
| `1.0.31` | 2026-11-21 | Lane 2 Author (`ATT-6042`, `--as agent1`) | `ATT-6042` | Promoted `REQ-SET-124` to `[Verified]` in Stage 5 reusing Gate 4 (`ATT-9493` @ `a49ad12d`, `Rule 7.5`, `0` `app/` drift): Stage 4 `55/55 PASS` across 6 suites + Gate 4 independent `35/35 PASS` (`0` new warnings), safety & baseline checks PASS, and `docs/engineering/walkthroughs/ATT-6042_walkthrough.md`. |
| `1.0.32` | 2026-11-21 | Lane 1 Author (`ATT-9509`, `--as agent1`) | `ATT-9509` | Added `REQ-SET-125` (`[Planned]`, `.1`–`.4`, linked to `SYSREQ-DEX-007`) for Workout Details Polar retry sync unpaired sensor detection (`DeviceDataRepository.isPolarRetrySensorPaired`), disabled sync button + localized hint across 9 locales, `allDevices`/`ON_RESUME` reactivity (`initialValue = null`), and sibling isolation. |
| `1.0.33` | 2026-11-21 | Lane 1 Author (`ATT-9509`, `--as agent1`) | `ATT-9509` | Promoted `REQ-SET-125` to `[Implemented]` following Stage 4 construction (`DeviceDataRepository.kt` `834/1,500`, `PolarH10RetrySyncBanner.kt` `470/1,500`, `TrackOnMapScreen.kt`, `WorkoutDetailTabbedContent.kt`, 9/9 `strings_devices.xml`; `45/45 PASS` across 6 suites + `Rule 8.5` Light/Dark PNG verified). |
| `1.0.34` | 2026-11-21 | Lane 1 Author (`ATT-9509`, `--as agent1`) | `ATT-9509` | Promoted `REQ-SET-125` to `[Verified]` in Stage 5 reusing Gate 4 (`ATT-9562` @ `a336463f`, `Rule 7.5`, `0` `app/` drift): `45/45 PASS` across 6 Tier-1 suites (`0` new warnings), `4/4` Python safety linters PASS, `--check-verified-evidence` EXIT `0`, `Rule 8.5` Light/Dark PNG certified, and `docs/engineering/walkthroughs/ATT-9509_walkthrough.md`. |
| `1.0.35` | 2026-11-21 | Lane 1 Author (`ATT-9345`, `--as agent1`) | `ATT-9345` | Added `REQ-SET-126` (`[Planned]`, `.1`–`.4`, linked to `SYSREQ-TRK-002`/`SYSREQ-TRK-012`) for Verity Sense workout PPI gating & explicit firmware `stopStreaming(PPI)`, stale/flatline HR & RR clearing + recovery (`PolarHrStreamHealthMonitor`), health- & modality-aware primary `HR` arbitration (`PrimaryHrSensorArbitrator` + lock-free `HrSensorHealthProvider`), and monolith/detailed-design compliance (`N/A - 0 string changes`). |
| `1.0.36` | 2026-11-21 | Lane 1 Author (`ATT-9345`, `--as agent1`) | `ATT-9345` | Promoted `REQ-SET-126` to `[Implemented]` following Stage 4 construction (`PolarHrStreamHealthMonitor.kt` `346`, `HrSensorHealthProvider.kt` `72`, `PrimaryHrSensorArbitrator.kt` `144`, `PolarOnlineStreamingBridge.kt` `1,475/1,500`, `PolarPmdStreamCoordinator.kt` `590/1,500`, `MySensorManager.java` `733/1,500`, `BTLEHeartRateDevice.java` `591/1,500`, `my_btle_device.md` `v1.0.9`; `123/123 PASS` across 11 Tier-1 suites; aligned resting mode prose to `READY`/`IDLE`). |
| `1.0.37` | 2026-11-21 | Lane 1 Author (`ATT-9345`, `--as agent1`) | `ATT-9345` | Promoted `REQ-SET-126` to `[Verified]` in Stage 5 reusing Gate 4 (`ATT-9573` @ `36870006`, `Rule 7.5`, `0` `app/` drift): `123/123 PASS` across 11 Tier-1 suites (`0` new production warnings), `4/4` Python safety linters PASS, `--check-verified-evidence` EXIT `0`, and `docs/engineering/walkthroughs/ATT-9345_walkthrough.md`. |
| `1.0.38` | 2026-11-21 | Lane 1 Author (`ATT-9345`, `--as agent1`) | `ATT-9345` | Stage 6 Bounce 1/3 heal: gated `BTLEHeartRateDevice` `0x10` RR parsing on `decision.getAcceptForSensor()`, hardened `PolarOnlineStreamingBridge` (`1,485/1,500` LoC) Verity PPI TOCTOU (`preAcquire` + post-suspend recheck + `processPpiData` workout guard + `forceStopFirmwarePpi` on workout connect & `recoverHrStream`), added active-owner guard to `PolarPmdStreamCoordinator.OneShotTimer` (`599/1,500`), added drift-free `PolarHrStreamHealthMonitor.nextCheckDelayMs()` (`389` LoC) + `resumeAfterOfflineSync`/`evaluateHrStaleness` timer re-arming + `resetRrAndOpticalArtifactState` tier re-sync (`+7` tests, `130/130 PASS`), and set `[Implemented]` pending Gate 4 / Stage 5 Bounce 1 re-verification. |
| `1.0.39` | 2026-11-21 | Lane 1 Author (`ATT-9345`, `--as agent1`) | `ATT-9345` | Promoted `REQ-SET-126` to `[Verified]` in Stage 5 Bounce 1 reusing Gate 4 Bounce 1 (`ATT-9575` @ `1099f4a9`, `Rule 7.5`, `0` `app/` drift): `130/130 PASS` across 11 Tier-1 suites (`24/24` verbatim new methods verified, `0` new production warnings), `4/4` Python safety linters PASS, `--check-verified-evidence` EXIT `0`, and refreshed `docs/engineering/walkthroughs/ATT-9345_walkthrough.md`. |
| `1.0.40` | 2026-11-22 | Lane 3 Author (`ATT-9174`, `--as agent1`) | `ATT-9174` | Added and verified `REQ-SET-127` (`EditWorkoutScreen` immediate Polar pending retry sync banner, race-free dismiss persistence, post-sync refresh via `PolarRetrySyncCoordinator.completedWorkoutEvents`, `outerPadding` flush alignment; `65/65 PASS` across 10 Tier-1 suites + Light/Dark Compose PNG + live `emulator-5554` verification). |
| `1.0.41` | 2026-11-22 | Lane 3 Author (`ATT-8121`, `--as agent1`) | `ATT-8121` | Added `REQ-SET-128` (`[Planned]`, `.1`–`.4`) for privatizing `SettingsDataStore`, `MyPreferenceManager`, and `ProFeatureManager` constructors, normalizing `applicationContext`, reusing a shared `SupervisorJob` scope in `MyPreferenceManager`, migrating all 24 production callers to `getInstance`, removing the dead `tuningConfig` collector in `ATrainingTrackerMap.kt`, and preserving JVM/Robolectric `InMemoryPreferencesDataStore` singleton isolation (`N/A - 0 string changes`). |
| `1.0.42` | 2026-11-22 | Lane 3 Author (`ATT-8121`, `--as agent1`) | `ATT-8121` | Promoted `REQ-SET-128` to `[Verified]` following Stage 4/5 construction & verification (`231/231 PASS` across 34 targeted suites incl. `PreferenceSingletonsLifecycleTest` `7/7`, `ArchitectureConformanceContractTest` `11/11`, `0` new Kotlin warnings, live `emulator-5554` smoke install & `read_file` PNG verified, `CRASH_LOGCAT_CLEAN`). |
| `1.0.44` | 2026-11-23 | Lane 2 Author (`ATT-9612`, `--as agent1`) | `ATT-9612` | Added `REQ-SET-129` (`[Planned]`, `.1`–`.4`, linked to `SYSREQ-DEX-007`/`SYSREQ-TRK-002`) for linearizable `PolarOnlineStreamingBridge` yield quiescence (`gattActionLock`), disconnect-recovering & in-flight-reusing `connectWithinTimeout()`, alias-aware `20 s` `awaitFeaturesReady()`, and H10/Verity Sense sibling parity (`N/A - 0 string changes`). |
| `1.0.45` | 2026-11-23 | Lane 2 Author (`ATT-9612`, `--as agent1`) | `ATT-9612` | Promoted `REQ-SET-129` to `[Verified]` following Stage 4/5 construction & verification (`232/232 PASS` across 24 Polar/BLE/Contract suites, `0` production Kotlin warnings, all 4 Polar files `<= 1,500` LoC, `my_btle_device.md` `v1.0.12`, and `docs/engineering/walkthroughs/ATT-9612_walkthrough.md`). |
| `1.0.46` | 2026-11-23 | Lane 2 Author (`ATT-9611`, `--as agent1`) | `ATT-9611` | Added `REQ-SET-130` (`[Planned]`, `.1`–`.4`, linked to `SYSREQ-DEX-007`) for pure-JVM UTC `HrOfflineRecording.startTime` alignment (`PolarOfflineHrTimestampAligner`), `<= 5_000 ms` bounded matching in `WorkoutSamplesDatabaseManager.patchMissingHeartRateSamples()`, `patchedCount > 0` flash-deletion gate, and structured Verity Sense + H10 backfill diagnostics (`N/A - 0 string changes`). |
| `1.0.47` | 2026-11-23 | Lane 2 Author (`ATT-9611`, `--as agent1`) | `ATT-9611` | Gate 3 (`ATT-9661`) revision: added symmetric already-complete-HR fast path in `executePendingSyncSession()` + defense-in-depth `patchedCount == 0 && !hasMissingHeartRateSamples` livelock guard (`REQ-SET-130.3`), DST fall-back `getValidOffsets()` entry matching, and `-1080..1080` `PbLocalDateTime.timeZoneOffset` range guard (`REQ-SET-130.1`). |
| `1.0.48` | 2026-11-23 | Lane 2 Author (`ATT-9611`, `--as agent1`) | `ATT-9611` | Promoted `REQ-SET-130` to `[Verified]` following Stage 4/5 construction & verification (`250/250 PASS` across 24 Polar/`WorkoutSamples`/Contract suites, `0` production Kotlin/Java warnings, `PolarOfflineHrTimestampAligner.kt` `266` LoC, `PolarVeritySenseOfflineController.kt` `1,476/1,500`, `PolarH10OfflineController.kt` `936/1,500`, `WorkoutSamplesDatabaseManager.java` `1,478/1,500`, and `docs/engineering/walkthroughs/ATT-9611_walkthrough.md`). |
| `1.0.49` | 2026-11-24 | Lane 2 Author (`ATT-9578`, `--as agent1`) | `ATT-9578` | Added `REQ-SET-131` (`[Planned]`, `.1`–`.4`, refining `REQ-SET-079` & `REQ-EXP-045.1`) for zero-`runBlocking`, cold-start-safe smartphone raw telemetry opt-in resolution via eager `MutableStateFlow` + delta-only `SharedPreferences` cold-start mirror in `TuningPreferencesDataStore`, `O(1)` delegation from `SmartphoneRawTelemetryRecorder.isEnabled` through `SettingsDataStoreJavaHelper`, and `DataStoreHotPathContractTest` regression guard (`N/A - 0 string changes`). |
| `1.0.50` | 2026-11-24 | Lane 2 Author (`ATT-9578`, `--as agent1`) | `ATT-9578` | Promoted `REQ-SET-131` to `[Verified]` following Stage 4/5 construction & verification (`122/122 PASS` across 24 suites, `0` new production warnings, `TuningPreferencesDataStore.kt` `2,279/2,410`, `SettingsDataStoreJavaHelper.kt` `370`, `SmartphoneRawTelemetryRecorder.kt` `835`, live `emulator-5554` smoke `CRASH_LOGCAT_CLEAN`, evidence bundle `docs/engineering/test_evidence/tickets/ATT-9578/`). |
