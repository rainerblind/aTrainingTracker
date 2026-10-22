# Living Requirements: Settings, Profiles & Localization

**Domain**: `settings_profiles`  
**Prefixes**: `LOC`, `PRI`, `SET`  
**Scope**: Athlete settings, bike/sensor profiles, privacy zones, data stores, and 9-locale parity.  
**Total Requirements**: 83

---

### REQ-SET-001: HR and Power training zones (5-zone model) [Verified]
- **Tests**: `TST-MAN-001`
- **Implementation**: `ZonesSettingsActivity.kt`
- **Rationale**: Allow athletes to train within specific intensities.
- **Description**: HR and Power training zones (5-zone model).

### REQ-SET-002: Equipment management (Bikes/Shoes) with odometer [Verified]
- **Tests**: `TST-MAN-001`
- **Implementation**: `EquipmentRepository.kt`
- **Rationale**: Help users monitor gear wear and tear.
- **Description**: Equipment management (Bikes/Shoes) with odometer.

### REQ-SET-003: Configurable speed thresholds for classification [Verified]
- **Tests**: `TST-UNT-003`
- **Implementation**: `TrainingApplication.java`
- **Rationale**: Tailor automation to user's specific pace profile.
- **Description**: Configurable speed thresholds for classification.

### REQ-SET-004: "Fancy Name" schemes using location and detours [Verified]
- **Tests**: `TST-STR-001`
- **Implementation**: `WorkoutSummariesDatabaseManager.java`
- **Rationale**: Provide descriptive workout names automatically.
- **Description**: "Fancy Name" schemes using location and detours.

### REQ-SET-050: Navigation Drawer Structure [Verified]
- **Tests**: `TST-NAV-001`
- **Implementation**: `main_navigation_drawer.xml`, `MainActivityWithNavigation.java`, `ui/settings/`
- **Rationale**: The navigation drawer SHALL be organized into logical hubs: Training, Spatial, Equipment, Ecosystem, and System. Key management features (Sport Types, Training Zones, Strava, Dropbox, Export) SHALL be individual first-class items in the drawer. Technical application settings (Units, Search, Display, Tracking Tabs) SHALL also be accessible as individual drawer items.

### REQ-SET-055: Modular Settings Architecture [Verified]
- **Tests**: `TST-INT-008`
- **Implementation**: `ui/settings/`, `MainActivityWithNavigation.java`
- **Rationale**: The system's settings configuration SHALL follow a modular architectural pattern. All settings-related UI components SHALL reside within dedicated functional sub-packages under `ui.settings` (e.g., `display`, `units`, `export`). The system SHALL NOT contain redundant preference fragments or legacy XML configurations that are no longer active in the primary navigation flow.

### REQ-SET-051: Direct Tracking Tab Selection [Verified]
- **Tests**: `TST-NAV-003`
- **Implementation**: `MainActivityWithNavigation.java`, `ActivityTypeSelectionHelper.kt`
- **Rationale**: The system SHALL provide a modal interface for Activity Type selection accessible directly from the navigation drawer. Upon selection, the system SHALL launch the dedicated configuration cockpit for the chosen Activity Type.

### REQ-SET-052: Modal Display Settings & Synchronized Preference State [Verified]
- **Tests**: `TST-NAV-004`
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

### REQ-SET-053: Modal Units Selection [Verified]
- **Tests**: `TST-NAV-005`
- **Implementation**: `MainActivityWithNavigation.java`, `UnitsSettingsDialogFragment.kt`
- **Rationale**: The system SHALL provide direct access to global unit system settings (Metric/Imperial) via a modal dialog triggered from the navigation drawer.

### REQ-SET-054: Modal Export Configuration [Verified]
- **Tests**: `TST-NAV-006`
- **Implementation**: `MainActivityWithNavigation.java`, `ExportSettingsDialogFragment.kt`
- **Rationale**: The system SHALL provide direct access to file export preferences (TCX, GPX, etc.) via a modal dialog triggered from the navigation drawer.

### REQ-SET-005: Global Unit Support (Metric/Imperial) [Verified]
- **Tests**: `TST-MAN-001`
- **Implementation**: `MyUnits.java`, `MyHelper.java`
- **Rationale**: The system SHALL support both Metric and Imperial unit systems globally across all modules (Tracking, Aftermath, Periods, WorkoutClusters, and Settings). All user-facing values, entry fields, and visualizations SHALL adapt dynamically to the user's unit preference.

### REQ-SET-006: Agnostic Workout Clustering [Verified]
- **Tests**: `TST-SET-001`
- **Implementation**: `WorkoutClusterEngine.kt`
- **Rationale**: The system SHALL automatically group similar tracks into \"Workout Clusters\" based on spatial fingerprints (Start, End, Apex, Distance) regardless of the initial sport type.

### REQ-SET-007: Majority-Based Sport Inference [Verified]
- **Tests**: `TST-SET-001`
- **Implementation**: `WorkoutClusterEngine.kt`, `WorkoutSummariesDatabaseManager.java`
- **Rationale**: The system SHALL determine the most likely Name and Sport for a Workout Cluster based on the majority of associated workouts. Manual confirms/overrides SHALL take precedence in the learning knowledge base.

### REQ-SET-008: Learning Feedback Loop [Verified]
- **Tests**: `TST-SET-001`
- **Implementation**: `WorkoutRepository.kt`
- **Rationale**: The system SHALL refine the cluster centroids and update probable sport associations whenever a user overrides or confirms a suggestion. The cluster's name SHALL ONLY be updated if it currently has a generic/default name (e.g., 'Cluster at...').

### REQ-SET-009: Favorite Tracks Heatmap [Verified]
- **Tests**: `TST-SET-002`
- **Implementation**: `WorkoutClusterHeatmapScreen.kt`, `MapContentScope.kt`
- **Rationale**: The system SHALL provide a visualization for Workout Clusters that overlays all associated workout tracks as a heatmap, along with the spatial fingerprint markers.

### REQ-SET-010: Tuning Parameters UI [Proposed]
- **Tests**: `TST-SET-003`, `TST-SET-034`
- **Implementation**: `ClusterTuningScreen.kt`, `WorkoutClustersViewModel.kt`
- **Rationale**: The system SHALL provide a UI to edit the tuning parameters for the Workout Cluster Engine, featuring a primary \"Strict-to-Relaxed\" master slider, optional detailed controls for individual tolerances (Endpoint, Apex, Distance), and a toggle to optionally include Sport Type in similarity calculations.

### REQ-SET-011: Clustering Recalculation [Verified]
- **Tests**: `TST-SET-003`
- **Implementation**: `WorkoutClusterEngine.kt`
- **Rationale**: Upon modification of tuning parameters, the system SHALL allow the user to trigger a full recalculation of Workout Clusters across the entire workout history.

### REQ-SET-012: Workout Cluster List Visualization [Verified]
- **Tests**: `TST-SET-004`
- **Implementation**: `WorkoutClustersTabsScreen.kt`, `WorkoutClusterHeatmapScreen.kt`
- **Rationale**: The system SHALL display a Workout Cluster list using a layout harmonized with the Workout and Route lists. Each item SHALL feature a full-width header (BSportType icon and Name) positioned at the top. Secondary metadata and metrics SHALL be displayed below the header, alongside a right-aligned map preview of the spatial signature. The Cluster Detail Heatmap SHALL also utilize a similar header layout to maximize visual consistency across the analytical suite.

### REQ-SET-013: Name-Aware Clustering [Verified]
- **Tests**: `TST-SET-005`
- **Implementation**: `WorkoutClusterEngine.kt`
- **Rationale**: The system SHALL prioritize existing identical workout names when matching routes to clusters to improve grouping accuracy for user-named paths.

### REQ-SET-014: Auto-Dismiss Tuning UI [Superseded]
- **Tests**: `TST-SET-006`
- **Implementation**: `WorkoutClustersFragment.kt`, `WorkoutClustersViewModel.kt`
- **Rationale**: The system SHALL automatically return to the cluster list view once a recalculation process is successfully completed. *(Superseded by REQ-SET-075 / ATT-3452: Cluster tuning removed from Favorite Routes header; recalculation centralized in Advanced Settings without auto-dismissal)*.

### REQ-SET-015: Unique Cluster Naming [Verified]
- **Tests**: `TST-SET-007`
- **Implementation**: `WorkoutClusterEngine.kt`, `WorkoutClusterDatabaseManager.kt`
- **Rationale**: The system SHALL ensure that every workout cluster has a unique name by appending a variant counter (e.g., var 2) if a name collision occurs for spatially distinct routes.

### REQ-SET-016: Edit Cluster Identity [Verified]
- **Tests**: `TST-SET-008`
- **Implementation**: `WorkoutClusterHeatmapScreen.kt`, `WorkoutClusterRepository.kt`
- **Rationale**: The system SHALL allow the user to manually rename a workout cluster and change its primary sport type via a dedicated edit dialog in the cluster's detail view.

### REQ-SET-017: Manual Workout Cluster Reassignment [Verified]
- **Tests**: `TST-SET-009`
- **Implementation**: `WorkoutClusterHeatmapScreen.kt`, `WorkoutClusterEngine.kt`
- **Rationale**: The system SHALL allow the user to manually reassign a workout to a different workout cluster via the heatmap view. Tapping a workout track SHALL show a list of candidate clusters sorted by similarity score.

### REQ-SET-018: Cluster Suggestions in Edit Workout [Verified]
- **Tests**: `TST-SET-010`, `TST-SET-046`
- **Implementation**: `EditWorkoutScreen.kt`, `EditWorkoutViewModel.kt`
- **Rationale**: The system SHALL provide a list of spatially similar cluster candidates when editing a workout's name. Selecting a candidate SHALL automatically update the workout's Name, Sport, and Cluster association.

### REQ-SET-019: Manual Cluster Creation [Verified]
- **Tests**: `TST-SET-011`
- **Implementation**: `ManualClusterScreen.kt`, `WorkoutClusterEngine.kt`
- **Rationale**: The system SHALL allow the user to manually create a new workout cluster by specifying its spatial fingerprint (Start, End, Apex) and distance metrics via a dedicated UI.

### REQ-SET-020: Workout Peek in Heatmap [Verified]
- **Tests**: `TST-SET-012`
- **Implementation**: `WorkoutClusterHeatmapScreen.kt`
- **Rationale**: The system SHALL display a detailed peek (Bottom Sheet) when a workout track is tapped in the cluster heatmap, similar to the Periods view. This peek SHALL include an option to reassign the workout to a different cluster.

### REQ-SET-021: Manual Fingerprint Editing [Verified]
- **Tests**: `TST-SET-013`
- **Implementation**: `WorkoutClusterHeatmapScreen.kt`, `WorkoutClusterRepository.kt`
- **Rationale**: The system SHALL allow the user to manually adjust the signature points (Start, End, Apex) of an existing workout cluster via the heatmap detail view.

### REQ-SET-022: Workout Markers in Cluster Detail [Verified]
- **Tests**: `TST-SET-014`
- **Implementation**: `WorkoutClusterHeatmapScreen.kt`, `WorkoutRepository.kt`
- **Rationale**: When viewing a workout cluster heatmap, the system SHALL display the spatial signature markers (Start, Stop, Apex) for EVERY member workout of the cluster to visualize spatial distribution. Additionally, it SHALL display all individual markers (Start, Stop, Extrema) for a specific workout when peeked. Clicking on any marker SHALL trigger the workout peek functionality.

### REQ-SET-023: Global Workout Unit Support [Verified]
- **Tests**: `TST-SET-015`
- **Implementation**: `ManualClusterScreen.kt`, `ClusterTuningScreen.kt`
- **Rationale**: The system SHALL support both Metric and Imperial units in the WorkoutCluster management UI. Distance entry and tuning visualization MUST use high-level units (km/mile) with appropriate precision to ensure professional-grade spatial definition.

### REQ-SET-024: Cluster Equipment Determination [Verified]
- **Tests**: `TST-SET-016`
- **Implementation**: `WorkoutClustersListScreen.kt`, `WorkoutClusterHeatmapScreen.kt`
- **Rationale**: The system SHALL dynamically determine and display the equipment associated with a Workout Cluster's majority sport. This SHALL use the technical mapping notation (→) consistent with the sensor management UI.

### REQ-SET-031: Route-to-Cluster Synchronization [Verified]
- **Tests**: `TST-SET-022`
- **Implementation**: `WorkoutClusterEngine.kt`, `RoutesRepository.kt`
- **Rationale**: Every explicit Route in the system (imported or created) SHALL have a corresponding Workout Cluster. Renaming a route SHALL automatically update the corresponding Workout Cluster name to ensure naming consistency across the spatial knowledge base.

### REQ-SET-033: Workout Cluster Deletion [Verified]
- **Tests**: `TST-SET-024`
- **Implementation**: `WorkoutClusterHeatmapScreen.kt`, `WorkoutClusterRepository.kt`
- **Rationale**: The system SHALL allow the user to delete a Workout Cluster via a long-click context menu on its detail header. Deleting a cluster SHALL nullify all associations with workouts and routes to maintain data integrity, while preserving the actual workout recordings.

### REQ-SET-032: Reference Route Visualization [Verified]
- **Tests**: `TST-SET-023`
- **Implementation**: `WorkoutClusterHeatmapScreen.kt`, `WorkoutClustersViewModel.kt`
- **Rationale**: When a Workout Cluster is linked to an explicit Route, the system SHALL display that route's path as a prominent reference layer on the detailed heatmap view to provide an authoritative spatial baseline.

### REQ-SET-061: Idempotent Route Cluster Creation [Verified]
- **Tests**: `TST-SET-047`
- **Implementation**: `WorkoutClusterEngine.kt`, `WorkoutClusterDatabaseManager.kt`
- **Rationale**: When learning a cluster from a workout with a specified route name, the system SHALL first search for an existing cluster with the exact normalized name before creating a new cluster. If a cluster with the same name exists, the system SHALL refine the existing cluster rather than creating a duplicate cluster appended with "var 2". **Invariants**: Spatial anchors (start/end/apex/bounds) MUST be updated when matching by name.

### REQ-SET-062: Automatic Zero-Workout Cluster Purging [Verified]
- **Tests**: `TST-SET-048`
- **Implementation**: `WorkoutClusterRepository.kt`, `WorkoutClusterDatabaseManager.kt`
- **Rationale**: The system SHALL automatically delete orphan route clusters that have zero actual workouts assigned in the database during repository refresh and post-import self-healing. **Invariants**: Clusters linked to an explicit Route (imported or created) MUST NOT be deleted even when they contain zero workouts. Clusters with 1 or more assigned workouts MUST NOT be deleted.

### REQ-SET-025: Workout Cluster Collapsing Header [Verified]
- **Tests**: `TST-SET-017`
- **Implementation**: `WorkoutClustersTabsScreen.kt`
- **Rationale**: The Workout Cluster list SHALL feature a collapsing top app bar that integrates with the list's scroll behavior, providing a modern and consistent navigation experience similar to the Workouts and Routes screens.

### REQ-SET-027: Persistent Scroll State [Verified]
- **Tests**: `TST-SET-019`
- **Implementation**: `WorkoutClustersFragment.kt`, `WorkoutClustersTabsScreen.kt`
- **Rationale**: The system SHALL preserve the scroll position and active tab of the Workout Cluster list when navigating back from a detail heatmap view.

### REQ-SET-026: Workout Cluster Tabbed Layout [Verified]
- **Tests**: `TST-SET-018`
- **Implementation**: `WorkoutClustersTabsScreen.kt`, `WorkoutClustersFragment.kt`
- **Rationale**: The Workout Cluster list SHALL feature a tabbed layout based on `BSportType` (All, Bike, Run, Other), matching the design pattern of Workouts, Segments, and Routes.

### REQ-SET-028: Unified Action Button Layout [Verified]
- **Tests**: `TST-SET-020`
- **Implementation**: `WorkoutClustersTabsScreen.kt`
- **Rationale**: The "Add Cluster" Floating Action Button SHALL use a standardized layout (manual Box alignment and navigationBarsPadding) consistent with the Sport Type and Equipment management screens.

### REQ-SET-029: Storage-Layer Identity Determination [Verified]
- **Tests**: `TST-SET-021`
- **Implementation**: `TrackerService.java`, `WorkoutClusterEngine.kt`
- **Rationale**: To ensure data consistency and prevent UI race conditions, the system SHALL determine a workout's Workout Cluster association and Auto-Name at the storage layer (immediately after tracking ends) rather than during UI mapping.

### REQ-SET-030: Sensor-First Identity Arbitration [Verified]
- **Tests**: `TST-SET-021`
- **Implementation**: `EquipmentAndSportTypeDiscoveryManager.kt`, `WorkoutClusterEngine.kt`
- **Rationale**: The system SHALL implement a confidence-based arbitration model to resolve conflicts between active hardware and route history. High-confidence sensor signals (e.g., uniquely linked hardware) SHALL veto route-based sport/gear overrides, while low-confidence sessions (GPS-only) SHALL be corrected by the Workout Cluster majority.

### REQ-SET-034: Stable Cluster Naming [Verified]
- **Tests**: `TST-SET-025`, `TST-SET-042`
- **Implementation**: `WorkoutClusterEngine.kt`
- **Rationale**: The system SHALL maintain the base name of a Workout Cluster when assigning it to a workout. The auto-generated workout name (including a hit counter) MUST NOT be propagated back to the cluster's permanent name. Manual session renames in the Edit Workout screen SHALL NOT rename the linked cluster.

### REQ-SET-037: Atomic Cluster Linking [Verified]
- **Tests**: `TST-SET-028`
- **Implementation**: `WorkoutClusterEngine.kt`, `WorkoutRepository.kt`
- **Rationale**: When a workout is assigned to a Workout Cluster, the system SHALL atomically update the workout's cluster association and increment the cluster's hit count. If reassigned from another cluster, the source cluster's count SHALL be decremented but the cluster MUST NOT be deleted automatically.

### REQ-SET-038: Self-Healing Hit Counts [Verified]
- **Tests**: `TST-SET-044`
- **Implementation**: `WorkoutClusterRepository.kt`, `WorkoutClusterSelectionDialog`
- **Rationale**: The system SHALL ensure that Workout Cluster hit counts are always accurate by either calculating them on-the-fly from the workout summary database or by performing a periodic integrity check that synchronizes the redundant `hit_count` column with the actual number of associated sessions. The UI selection dialog MUST display these accurate counts.

### REQ-SET-035: Preview Heatmap [Verified]
- **Tests**: `TST-SET-026`
- **Implementation**: `WorkoutClusterComponents.kt`, `WorkoutClusterRepository.kt`
- **Rationale**: The Workout Cluster list item SHALL display a representative spatial preview on its mini-map, consisting of the linked Route path (if available) or the 5 most recent workout tracks rendered as a low-opacity heatmap.

### REQ-SET-036: Persistent DB Connection [Verified]
- **Tests**: `TST-SET-027`
- **Implementation**: `WorkoutClusterDatabaseManager.kt`
- **Rationale**: The Workout Cluster database SHALL remain open during the application lifecycle to facilitate real-time debugging and inspection via external tools.

### REQ-SET-039: Locale-Aware Distance Input [Verified]
- **Tests**: `TST-SET-030`
- **Implementation**: `ManualClusterScreen.kt`
- **Rationale**: The manual cluster creation interface SHALL support locale-specific decimal separators (e.g., comma in DE, dot in EN) when entering workout distances.

### REQ-SET-040: Unclustered Workout Access [Verified]
- **Tests**: `TST-SET-029`
- **Implementation**: `WorkoutClustersTabsScreen.kt`, `WorkoutClusterRepository.kt`
- **Rationale**: The system SHALL provide a dedicated view for workouts that are not associated with any Workout Cluster to facilitate manual organization and re-clustering.

### REQ-SET-056: Unclustered Workout Visualization [Verified]
- **Tests**: `TST-SET-040`
- **Implementation**: `WorkoutClusterComponents.kt`, `WorkoutClustersFragment.kt`, `TrackOnMapScreen.kt`
- **Rationale**: Unclustered workouts SHALL be displayed in the cluster management UI using a layout harmonized with `ClusterItem`, including a spatial mini-map preview. Clicking an unclustered workout SHALL navigate the user to a detailed map view of that workout, featuring a direct option to manually assign it to a cluster.

### REQ-SET-057: Cluster Member Listing [Verified]
- **Tests**: `TST-SET-041`
- **Implementation**: `WorkoutClusterComponents.kt`, `WorkoutClustersFragment.kt`, `WorkoutSummariesViewModel.kt`
- **Rationale**: The system SHALL allow the user to view all workouts associated with a specific Workout Cluster. Clicking the hit count (number of recordings) in the cluster list or details SHALL navigate the user to a filtered workout list containing only the members of that cluster.

### REQ-SET-058: Cluster Visibility & Direct Cluster Navigation in Workout Summary [`TST-SET-043`, `TST-SET-057`, `TST-SET-064`]
- **Tests**: `WorkoutHeader.kt`, `WorkoutHeaderData.kt`, `WorkoutData.kt`, `WorkoutSummary.kt`, `WorkoutList.kt`, `TrackOnMapScreen.kt`, `WorkoutNavigationEvents.kt`, `MainActivityWithNavigation.kt`, `WorkoutClustersFragment.kt`, `WorkoutClustersViewModel.kt`
- **Implementation**: Provide seamless bidirectional navigation between individual workout sessions and their parent route clusters with visual parity to the cluster recordings button.
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

### REQ-SET-059: Explicit Workout Cluster Management & Assignment in Editor [`TST-SET-050`, `TST-SET-064`]
- **Tests**: `EditWorkoutScreen.kt`, `EditWorkoutViewModel.kt`, `WorkoutClusterComponents.kt`, `WorkoutClusterEngine.kt`, `WorkoutRepository.kt`
- **Implementation**: Ensure full parity with TCX route clustering, providing transparent, discoverable, and complete cluster lifecycle management (Uncluster, Create New, Reassign) in the workout editor.
- **Rationale**: The system SHALL provide an explicit, dedicated cluster management selector within `EditWorkoutScreen` positioned immediately below the workout name input field:
1. *Harmonized Selector UI*: The editor SHALL display an explicit cluster selector field displaying the route cluster icon (`ic_favorite_route`), label (`cluster_naming__selected_route_label`), and current cluster name (or `cluster_naming__leave_unclustered` / `unclustered` if unassigned), matching the look and feel of the TCX import naming dialog.
2. *Unified Assignment Dialog*: Tapping the cluster selector SHALL present an assignment dialog providing:
• **Leave Unclustered ("Kein Cluster")**: An explicit action to unassign the workout (`clusterId = -1L`), atomically decrementing the previous cluster's hit count.
• **Create New ("Neuer Cluster...")**: An explicit action to create a brand new Workout Cluster from the workout session's spatial fingerprint (Start, End, Apex, Distance) and assign it to the workout.
• **Candidate Clusters**: Spatially similar cluster candidates sorted by similarity score, displaying hit counts and sport types.
3. *Atomic Hit Count & Identity Maintenance*: Reassigning or clearing a cluster SHALL atomically synchronize `WorkoutSummaries.CLUSTER_ID` and update cluster hit counts in `WorkoutClusterDatabaseManager`.
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
**Invariants**: Existing sport type and equipment selections MUST NOT be mutated without user confirmation; non-target workout metrics MUST be preserved; existing hit count recalculation logic MUST NOT be corrupted.

### REQ-SET-060: Selectable Cluster Markers [Verified]
- **Tests**: `TST-SET-045`
- **Implementation**: `WorkoutClusterHeatmapScreen.kt`, `WorkoutClustersViewModel.kt`, `MyPreferenceManager.kt`
- **Rationale**: The system SHALL allow the user to toggle the visibility of individual marker types (Start, End, Max Distance) within the Workout Cluster heatmap. These markers SHALL be independently selectable for both the cluster signature and the session distribution distribution. The selection state MUST be persisted independently from the Periods module preferences.

### REQ-SET-042: Markers in Previews [Verified]
- **Tests**: `TST-SET-032`
- **Implementation**: `PathPreviewMap.kt`, `WorkoutSummary.kt`, `RouteItem.kt`, `WorkoutClusterComponents.kt`, `PeriodSummaryCard.kt`, `WorkoutClusterEngine.kt`
- **Rationale**: The system SHALL display Start, End, and Apex markers in the mini-map previews for Workouts. For Routes, ONLY Start and End markers SHALL be displayed. ALL map previews (Clusters, Workouts, Periods) SHALL correctly frame the entire route(s) based on persisted spatial bounds.

### REQ-SET-043: Fingerprint Parity [Proposed]
- **Tests**: `TST-SET-033`, `TST-SET-034`, `TST-SET-035`
- **Implementation**: `WorkoutClusterEngine.kt`
- **Rationale**: To ensure 100% matching accuracy, the system SHALL calculate Route spatial fingerprints using 'Max Displacement from Start', matching the live tracking engine's logic. The similarity calculation SHALL strictly include a significant penalty (+2.0) if the workout's `BSportType` is NOT IDENTICAL to the cluster's `BSportType` (when enabled), preventing cross-activity cluster contamination even between known sports and 'UNKNOWN' placeholders.

### REQ-SET-044: Manual Name Isolation [Verified]
- **Tests**: `TST-SET-042`
- **Implementation**: `WorkoutRepository.kt`, `WorkoutClusterEngine.kt`
- **Rationale**: Renaming a workout session in the Edit Workout screen SHALL NOT trigger a rename of the associated Workout Cluster, preserving the route's permanent identity.

### REQ-SET-063: Workout Cluster Apex Arithmetic Mean & Live-Session Tracking Synchronization [Verified]
- **Tests**: `TST-SET-049`
- **Implementation**: `WorkoutClusterEngine.kt`, `WorkoutClustersViewModel.kt`, `SpeedAndLocationDevice.java`
- **Rationale**: The system SHALL ensure that a Workout Cluster's Max Line Distance (Apex) signature marker (`maxDispLat, maxDispLng`) strictly represents the running arithmetic mean of the maximum displacement points (`maxDisplacementLatLng`) across all member workouts in the cluster, preserving the centroid across member additions, deletions, and reassignments. The cluster's apex SHALL NOT be snapped or overwritten to arbitrary individual track coordinates during UI inspection or linked route resolution. Furthermore, when a live workout finishes, `WorkoutClusterEngine.onWorkoutFinished()` SHALL pass the workout's actual `maxDisplacementLatLng` (with fallback to `endLatLng`) as the apex rather than `startLatLng`. Additionally, `SpeedAndLocationDevice` SHALL reset `mStartLocation` upon accumulator reset so that live displacement tracking is anchored to the workout's actual start location.

### REQ-SET-064: Speed-Based Multi-Sport Route Clustering & Ambiguity Disambiguation in Tracker and Workout Editor [`TST-SET-051`, `TST-SET-056`]
- **Tests**: `TrackerService.java`, `EquipmentAndSportTypeDiscoveryManager.kt`, `WorkoutClusterEngine.kt`, `EditWorkoutViewModel.kt`, `SportTypeDatabaseManager.java`
- **Implementation**: Eliminate false duplicate cluster creation and inaccurate sport guessing after tracking and inside the workout editor on recurring routes when intermediate speeds or overlapping custom sports allow multiple plausible sport types.
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

### REQ-SET-065: Altitude Extrema-Aware 3D Route Clustering & Database Migration [`TST-SET-052`, `TST-UNT-015`]
- **Tests**: `WorkoutClusterDatabaseManager.kt`, `WorkoutClusterEngine.kt`, `TrainingApplication.java`, `ClusterTuningScreen.kt`, `WorkoutClustersViewModel.kt`, `WorkoutClusterHeatmapScreen.kt`, `LegacyImportEngine.kt`, `ImportEngine.kt`
- **Implementation**: Prevent false cluster merging in hilly and alpine terrain where 2D horizontal fingerprints cannot distinguish ridge from valley routes, and provide complete 3D cluster fingerprint lifecycle management (editing, tuning, visualization, import).
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

### REQ-SET-066: Workout Cluster Sequential Auto-Naming Counter Ergonomics [`TST-SET-053`]
- **Tests**: `WorkoutClusterEngine.kt`, `TrackerService.java`, `EditWorkoutViewModel.kt`
- **Implementation**: Avoid unnatural and cluttered names like "Morning Run #1" for solitary or initial route activities while preserving numbered series tracking for recurring routes.
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

### REQ-SET-067: Optional Route Cluster Activity Counter / Auto-Numbering [`TST-SET-054`]
- **Tests**: `WorkoutClusterDatabaseManager.kt`, `WorkoutClusterEngine.kt`, `WorkoutClustersViewModel.kt`, `WorkoutClusterHeatmapScreen.kt`, `WorkoutClusterComponents.kt`, `TrackerService.java`, `EditWorkoutViewModel.kt`
- **Implementation**: Allow users to suppress numerical suffixes (#2, #3, ...) for routes where static unnumbered naming is preferred (e.g., commutes, races, named trail loops) while preserving sequential numbering for routes where desired.
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

### REQ-SET-068: Comprehensive Workout Cluster Performance & Volume Statistics [`TST-SET-055`]
- **Tests**: `WorkoutSummariesDatabaseManager.java`, `WorkoutClusterRepository.kt`, `WorkoutClustersViewModel.kt`, `WorkoutClusterHeatmapScreen.kt`, `WorkoutClusterComponents.kt`, `strings.xml`
- **Implementation**: Provide athletes with comprehensive historical insights, personal benchmarks, and cumulative volume tracking for recurring routes while preserving sub-second database performance.
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
• *Detail Screen Dashboard (`WorkoutClusterSummaryHeader`)*: The system SHALL present a three-tier structured dashboard:
a) *Performance Benchmarks*: Best Time (highlighted with trophy accent), Average Time, and Sport-Adaptive Velocity (Pace for running/walking, Speed for cycling/other).
b) *Cumulative Totals*: Total Distance, Total Climbing, and Total Moving Time.
c) *Activity & Recency*: Interactive Recordings Count button (tapping navigates to filtered workout list) and Last Activity date with relative recency (e.g., `"06.09.2026 (3 days ago)"`).
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

**Invariants**: Cluster fingerprinting, apex anchoring, database schema, and existing cluster filtering/sorting MUST NOT be modified.

### REQ-SET-069: Workout, Route & Segment Detail Bottom Sheet Scaffold Status Bar Boundary Constraint [`TST-SET-058`]
- **Tests**: `PeriodMapScreen.kt`, `MapFragmentWithTrack.kt`, `WorkoutClusterHeatmapScreen.kt`
- **Implementation**: Prevent status bar icons and clocks from colliding with bottom sheet titles across all map detail sheets while preserving initial peek height and a compact drag handle.
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

### REQ-SET-070: Direct Navigation to Workout Editor from Workout Detail Popup & Unified Action Button Layout [`TST-SET-059`]
- **Tests**: `TrackOnMapScreen.kt`, `WorkoutHeader.kt`, `PeriodMapScreen.kt`, `PeriodsFragment.kt`, `WorkoutClusterHeatmapScreen.kt`, `WorkoutClustersFragment.kt`
- **Implementation**: Enable athletes to modify workout attributes directly from map peek popups without losing spatial context, and harmonize action button hierarchy across headers.
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

### REQ-SET-071: Consistent Workout Click-to-Map Navigation & Dedicated Header Edit Action [`TST-SET-060`]
- **Tests**: `WorkoutSummary.kt`, `WorkoutHeader.kt`, `WorkoutSummaryCompact.kt`, `WorkoutSummariesTabbedFragment.kt`, `WorkoutSummariesListFragment.kt`
- **Implementation**: Provide intuitive and consistent inspection navigation across workout cards by standardizing map view as the default click action, while providing a clear and accessible dedicated edit action on the very right of the header.
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

### REQ-SET-072: Edit Workout New Cluster Creation & Name Synchronization [`TST-SET-061`]
- **Tests**: `EditWorkoutScreen.kt`, `EditWorkoutViewModel.kt`, `WorkoutClusterEngine.kt`, `WorkoutClusterComponents.kt`, `WorkoutRepository.kt`
- **Implementation**: Ensure seamless cluster creation ergonomics from Edit Workout without reverting user edits or retaining stale cluster names.
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

### REQ-SET-073: Dedicated Advanced Tuning Preferences Screen with Parameter Clamping and Reset-to-Defaults [`TST-SET-062`]
- **Tests**: `TuningPreferencesDataStore.kt`, `AdvancedTuningDialog.kt`, `BatterySaverController.kt`, `BatterySaverStateMachine.kt`, `SpeedAndLocationDevice.java`, `VerticalSpeedAndSlopeDevice.java`
- **Implementation**: Expose critical tracking and battery saver heuristics to athletes and testers with strict safety clamping, reactive updates, and single-tap factory reset.
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

### REQ-SET-074: Expert Settings Drawer Positioning, Localization & 50m Default GPS Tolerance [`TST-SET-063`]
- **Tests**: `AppNavigationDrawer.kt`, `TuningPreferencesDataStore.kt`, `strings.xml`
- **Implementation**: Sequester expert tuning options from general preferences, refine German terminology, and provide better out-of-the-box GPS track accuracy.
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

### REQ-SET-075: Dedicated Workout Route Clustering Section in Advanced Settings, Single Source of Truth, and Modularity Preservation [`TST-UI-318`, `TST-UI-321`]
- **Tests**: `ClusterTuningSection.kt`, `WorkoutClustersTabsScreen.kt`, `WorkoutClustersScreen.kt`, `ClusterTuningContent.kt`, `AdvancedTuningAccordion.kt`, `AdvancedTuningScreen.kt`, `TuningPreferencesDataStore.kt`
- **Implementation**: Consolidate workout route clustering algorithmic parameters into Advanced Settings as a dedicated accordion section, eliminating code duplication, excising redundant header tuning from favorite routes, and providing recalculate action with confirmation dialog.
- **Rationale**: The system SHALL consolidate the canonical configuration of workout route clustering algorithmic parameters into Advanced Settings (`AdvancedTuningScreen.kt` / `AdvancedTuningDialog.kt`) as a dedicated modular accordion section, adhering to the Single Source of Truth and DRY principles (Rule 36), and SHALL preserve existing parameter ranges, keys, defaults, and modularity bounds (ATT-3355, amended by ATT-3452, amending `REQ-SET-010` and `REQ-SET-065`, superseding `REQ-SET-014`, referencing `REQ-UI-357`):
1. *Modular Accordion Section Architecture (`ClusterTuningSection.kt`, `AdvancedTuningAccordion.kt`)*:
• The system SHALL add `ROUTE_CLUSTERING` to `TuningSection` enum in `AdvancedTuningAccordion.kt`, positioned in Phase C (History, Routes & Post-Workout) between `WORKOUT_MASKS_CARDS` and `AFTERMATH_ANALYSIS`.
• The system SHALL create `ClusterTuningSection.kt` under `ui.settings.tuning.categories`, hosting the clustering parameters UI.
• The accordion card SHALL render `Icons.Default.Hub`, localized title `R.string.tuning_cat_route_clustering` (EN: "Route & Workout Clustering", DE: "Routen- & Workout-Clustering"), animated chevron rotation, and initially collapsed state (`emptySet()`).
• `TuningSubtitleFormatter` SHALL provide `formatClusterTuningSubtitle(...)`, generating a live formatted summary string of active tolerances and toggles (e.g. `End: 200m · Apex: 400m · Dist: 20% · Alt: 400m` in metric mode, or imperial equivalent with mi/ft).
2. *Single Source of Truth, DRY Refactoring & Header Tuning Elimination (Rule 36, ATT-3452)*:
• The shared UI controls and validation logic SHALL be encapsulated in `ClusterTuningContent.kt` (< 250 lines), shared identically with zero code or layout duplication.
• The cluster tuning settings icon button (`onTuneClick`) SHALL be completely removed from `WorkoutClustersTabsScreen.kt`, and `ClusterTuningScreen.kt` and `isTuning` state handling SHALL be excised from `WorkoutClustersScreen.kt` in favor of centralized Advanced Settings.
• `ImportBackupTabsScreen.kt` SHALL consume `ClusterTuningContent` for pre-import sensitivity adjustments.
3. *DataStore, Defaults & Atomic Factory Reset Integration*:
• `TuningPreferencesDefaults` SHALL define defaults: `CLUSTER_TOL_ENDPOINTS = 200f`, `CLUSTER_TOL_APEX = 400f`, `CLUSTER_TOL_DISTANCE = 0.20f`, `CLUSTER_TOL_ALTITUDE_POS = 400f`, `CLUSTER_USE_SPORT_TYPE = true`, `CLUSTER_USE_ALTITUDE_POS = true`.
• `TuningConfig` SHALL incorporate these 6 clustering parameters.
• `TuningPreferencesDataStore` SHALL manage persistence, updating both DataStore and `TrainingApplication.cSharedPreferences` synchronously on save to ensure background services (`WorkoutClusterEngine`, `LegacyImportEngine`, `TrackerService`) read updated values immediately.
• Invoking "Auf Standardwerte zurücksetzen" (Reset to Defaults) in Advanced Settings SHALL atomically reset clustering tolerances to their canonical factory defaults.
4. *Dedicated Recalculate Action, Material 3 Confirmation Guard & Concurrency Isolation (ATT-3452)*:
• `ClusterTuningSection.kt` SHALL host a dedicated "Recalculate All Clusters" button (`R.string.cluster_tuning_recalculate_button`).
• Tapping the button opens a modal Material 3 confirmation `AlertDialog` (`containerColor = MaterialTheme.colorScheme.surface`, `tonalElevation = 0.dp`) displaying `R.string.cluster_tuning_warning` with high-contrast error confirmation action and cancel dismissal.
• Recalculation executes asynchronously via singleton `WorkoutClusterRepository.getInstance(context).recalculateClustersWithProgress()`, decoupled from UI Composable lifecycles.
• While active (`migrationStatus != null`), the recalculate button is disabled and displays `CircularProgressIndicator` alongside `R.string.cluster_tuning_recalculating`, and all tolerance sliders are disabled (`enabled = false`) to prevent concurrent modification.
• Process-death recovery is guaranteed by the self-healing bootstrapper in `WorkoutClusterRepository.refreshClusters()`.
5. *Preservation of Invariants & Modularity*:
• All 6 parameters, SharedPreferences keys (`clusterTolEndpoints`, `clusterTolApex`, `clusterTolDistance`, `clusterTolAltitudePos`, `clusterUseSportType`, `clusterUseAltitudePos`), default values, and range validations MUST NOT be altered.
• Modularity invariant: Every source file in `ui.settings.tuning` MUST remain strictly < 400 lines of code (`AdvancedTuningModularityTest`).
• 100% 9-language translation parity maintained across EN, DE, ES, FR, IT, JA, NL, PL, PT.

**Requirement Archaeology & Chesterton's Fence Audit**:
1. *Original Requirement ID & Target*: Amends `REQ-SET-010` (*Tuning Parameters UI*) and `REQ-SET-065` (*Altitude Extrema-Aware 3D Route Clustering & Database Migration*), supersedes `REQ-SET-014` (*Auto-Dismiss Tuning UI*), integrating with `REQ-UI-357` (*Dedicated Full-Screen Advanced Settings Screen Architecture*).
2. *Historical Origin & Commit Trace*: Sprint 2026-40.2 (`REQ-SET-010`, `REQ-SET-014`), Sprint 2026-40.5 (`ATT-502` / `ATT-503`, `REQ-SET-065`), Sprint 2026-40.8 (`REQ-UI-222`), Sprint 2026-42.1 (`ATT-3344`, `REQ-UI-357`; `ATT-3355`, `REQ-SET-075`).
3. *Root Reason for Existing Formulation*: Clustering tolerances originally resided inside a standalone screen (`ClusterTuningScreen.kt`) opened from an overflow menu in `WorkoutClustersScreen.kt`. In ATT-3355, the controls were consolidated into Advanced Settings, but the legacy header button in favorite routes was temporarily retained, leaving dual competing paths and omitting full recalculation in Advanced Settings. ATT-3452 eliminates the redundant header trigger and unifies recalculation with a Material 3 confirmation guard.
4. *Preservation of Core Invariants*: Parameter keys, storage semantics in `TrainingApplication.java`, value ranges, modularity < 400 lines, and 9-language translation parity remain 100% strictly intact.

**Acceptance Criteria (Given-When-Then)**:
• *Given* the athlete browsing Advanced Settings ("Erweiterte Einstellungen"),
• *When* viewing the category list,
• *Then* a dedicated section "Routen- & Workout-Clustering" (`ROUTE_CLUSTERING`) appears in Phase C with an active subtitle summarizing current tolerances and toggles.
• *Given* the athlete modifying clustering tolerances in Advanced Settings,
• *When* tapping Save,
• *Then* the values are persisted to DataStore and SharedPreferences and immediately take effect in subsequent workout clustering and import operations.
• *Given* the athlete tapping "Recalculate All Clusters" in Advanced Settings,
• *When* tapped,
• *Then* a modal confirmation `AlertDialog` appears warning of cluster wiping before any recalculation starts.
• *Given* an active recalculation in progress,
• *Then* recalculation runs in the repository coroutine scope decoupled from UI navigation, the recalculate button and sliders are disabled with progress feedback, and self-healing bootstrap protects against process death.
• *Given* an athlete viewing Favorite Routes (`WorkoutClustersTabsScreen`),
• *Then* zero cluster tuning buttons are present in the top app bar.

**Invariants**: Parameter ranges, DataStore keys, warning card, factory reset, zero tonal elevation, 400-line file limit, and 9-language localization parity MUST NOT be broken.

### REQ-SET-076: Red Cluster Recalculate Button and Direct Advanced Settings Deep-Link from Favorite Routes [`TST-SET-076`]
- **Tests**: `ClusterTuningSection.kt`, `ClusterInfoDialog.kt`, `WorkoutClustersTabsScreen.kt`, `WorkoutClustersScreen.kt`, `ATrainingTrackerApp.kt`, `WorkoutClustersFragment.kt`
- **Implementation**: Style cluster recalculate button in red error colors and provide direct deep-link from Favorite Routes info to Advanced Settings.
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

### REQ-SET-041: Unified Spatial Markers [Proposed]
- **Tests**: `TST-SET-031`
- **Implementation**: `WorkoutClusterHeatmapScreen.kt`, `InteractivePeriodMap.kt`, `TrackOnMapScreen.kt`, `RouteItem.kt`
- **Rationale**: All geographical views (Workout Clusters, Periods, Workout Details, and Routes) SHALL use standardized technical markers: Green start icons, Red stop icons, and Blue apex/distance icons. Icons SHALL use the unified `createSensorMarker` visual style.

### REQ-PRI-001: Permission Transparency [Verified]
- **Tests**: `TST-MAN-001`
- **Implementation**: `MainActivityWithNavigation.java`
- **Rationale**: The system SHALL provide transparent information about location and background permissions to comply with Android standards and build user trust.

### REQ-PRI-002: Accessible Privacy Policy [Verified]
- **Tests**: `TST-MAN-001`
- **Implementation**: `privacy.md`
- **Rationale**: The system SHALL provide an accessible Privacy Policy linked to the source to inform users about data handling practices.

### REQ-PRI-003: Contextual Just-in-Time Permission Flow, Zero-Friction Cold Start, Background Location Escalation, Battery Optimization Exemption & Graceful Settings Return Handling [Verified]
- **Tests**: `TST-PRI-002`
- **Implementation**: `MainActivityWithNavigation.kt`, `ControlTrackingScreen.kt`, `ControlTrackingButton.kt`, `ControlTrackingViewModel.kt`, `PermissionRationaleSheet.kt`
- **Rationale**: The system SHALL NOT present modal permission dialogs or battery optimization popups during cold start; it SHALL evaluate permissions contextually when the athlete expresses tracking intent (tapping Start or searching sensors), display educational Material 3 rationale sheets highlighting athletic benefits, enforce the Android 11+ two-step background location cascade ('Allow all the time' / 'Immer zulassen'), handle battery optimization exemption ('Unrestricted' / 'Nicht eingeschränkt') with fail-safe OEM fallback, and handle denied permissions or returns from settings gracefully with an amber warning badge on the Start button and actionable feedback without infinite loops (ATT-2075).

### REQ-PRI-004: Mandatory Precise Location Gating and Dynamic Sensor Device Instantiation on Permission Escalation [Require Precise Location (ACCESS_FINE_LOCATION) for workout tracking, provide educational rationale on Approximate-only grant, and lazily instantiate GPS location devices dynamically upon permission grant.]
- **Tests**: ACCESS_COARSE_LOCATION` under the assumption that coarse location could serve as a degraded fallback. However, `SpeedAndLocationDevice_GPS` strictly requires `ACCESS_FINE_LOCATION` to construct Android `LocationManager.requestLocationUpdates` with `GPS_PROVIDER`, causing complete GPS silence when only coarse was granted.
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

### REQ-LOC-001: Global Translation Parity, Resource Hierarchy Alignment, and Formatter Safety [`TST-STR-018`]
- **Tests**: `values*/strings.xml`, `values*/strings_devices.xml`, `values*/strings_filters.xml`, `values*/arrays.xml`
- **Implementation**: Eliminate format string crash risks, internationalization technical debt, and resource file inconsistencies across all supported languages.
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

### REQ-PRI-005: Proactive Background Energy & Battery Optimization Exemption Governance and Cascaded Rationale Decoupling [Proactive background energy warning banner, Start button badge integration, and decoupled battery optimization rationale cascade.]
- **Tests**: !isIgnoringBatteryOptimizations`.
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

### REQ-SET-077: Standardized Route Clustering Form Controls via TuningFormControls (Rule 36) [`TST-SET-077`]
- **Tests**: `TuningFormControls.kt`, `ClusterTuningContent.kt`, `ClusterTuningScreen.kt`, `ClusterTuningSection.kt`
- **Implementation**: Standardized route clustering form controls via TuningFormControls (Rule 36).
- **Rationale**: The system SHALL standardize all route clustering form controls across `ClusterTuningContent.kt`, `ClusterTuningScreen.kt`, and `ClusterTuningSection.kt` using canonical, accessible form controls from `TuningFormControls.kt` (`TuningSliderItem` and `TuningToggleItem`), enforcing Rule 36 (Single Source of Truth & DRY) and guaranteeing visual and behavioral consistency across all application settings screens (ATT-3365, complementing `REQ-SET-075` and `REQ-UI-262`):
1. *Defensive Parameterization of `TuningFormControls.kt`*:
• `TuningSliderItem` SHALL accept optional parameters `enabled: Boolean = true` and `onValueChangeFinished: (() -> Unit)? = null`, forwarding them to the internal Material 3 `Slider` component and applying standard disabled alpha (`TTAlpha.Disabled`) to header text and value labels when disabled.
• When `enabled = false`, Material 3 `Slider` and `Switch` components SHALL natively enter their disabled visual and interaction state, strictly preventing user drag/tap interactions and ensuring neither `onValueChange` nor `onValueChangeFinished` callbacks are fired while disabled.
• `TuningToggleItem` SHALL accept optional parameter `enabled: Boolean = true`, forwarding it to the internal `Switch` component and applying standard disabled alpha (`TTAlpha.Disabled`) to label text when disabled.
• All existing 15+ call sites across `NavigationSection`, `SensorsGpsFilterSection`, `CockpitTypographySection`, `AmoledBatterySaverSection`, `SensorSearchTuningSection`, and `AftermathAnalysisSection` SHALL retain 100% backward and binary compatibility without signature regressions or source modifications.
2. *Standardization of `ClusterTuningContent.kt`*:
• The system SHALL replace hand-crafted `Row` + `Switch` blocks for `showDetails`, `useSportTypeForClustering`, and `useAltitudePosForClustering` with canonical `TuningToggleItem` composables.
• The system SHALL replace bespoke tolerance slider blocks (`endpointTolerance`, `apexTolerance`, `distanceTolerance`, `altitudePositionTolerance`) with direct invocations of `TuningSliderItem`.
• The legacy `TuningSlider` composable in `ClusterTuningContent.kt` SHALL be marked with `@Deprecated` containing a fully-qualified `ReplaceWith(expression = "TuningSliderItem(title = title, valueText = valueText, helperText = \"\", defaultText = \"\", value = value, onValueChange = onValueChange, valueRange = valueRange, steps = steps, enabled = enabled, onValueChangeFinished = onValueChangeFinished)", imports = ["com.atrainingtracker.trainingtracker.ui.settings.tuning.TuningSliderItem"])`.
3. *Multi-Consumer & State Propagation Support*:
• All consumers of `ClusterTuningContent` (`ClusterTuningScreen.kt`, `ClusterTuningSection.kt`, and `ImportBackupTabsScreen.kt`) SHALL inherit unified visual rhythm, typography, and switch scaling (`scale(0.8f)`).
• The `enabled` state (e.g. `!isRecalculating`) SHALL cleanly propagate down to all toggles and sliders. The altitude position tolerance slider SHALL be gated to `enabled = enabled && useAltitudePosForClustering`.
4. *Preservation of System Invariants*:
• Tolerance bounds (`10..500f`, `0.01..0.2f`), master sensitivity calculation, recalculation domain locking (`isRecalculatingLock`), persistence on drag completion (`onValueChangeFinished`), and 100% full-suite unit test pass rate MUST be strictly preserved.

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

### REQ-SET-078: Historical Duration and Speed/Pace Trend Chart for Workout Clusters (Lieblingsstrecken) [`TST-SET-078`]
- **Tests**: `WorkoutClusterTrendChartCard.kt`, `WorkoutClusterTrendMath.kt`, `WorkoutClusterHeatmapScreen.kt`, `values*/strings.xml`
- **Implementation**: Render historical duration and speed/pace trend chart for Workout Clusters with faster-is-higher scaling, outlier-resilient Y-scaling, X/Y axis scale labels, density-adaptive point sizing, cluster average reference line, PR highlight, interactive peek, and 9-language parity.
- **Rationale**: The system SHALL render a historical effort trend chart for Workout Clusters in `WorkoutClusterDetailDashboard` (`WorkoutClusterHeatmapScreen.kt`, `WorkoutClusterTrendChartCard.kt`, `WorkoutClusterTrendMath.kt`), ordering member workouts chronologically, supporting dual metric modes (Duration vs Speed/Pace) with faster-is-higher vertical axis scaling, displaying cluster average reference line and PR highlight, providing interactive point touch selection with workout peek sheet launch, rendering explicit X/Y axis scale markers, clamping isolated outliers with Tukey fencing, and guaranteeing 9-language localization parity (ATT-4067, refined by ATT-4287):
1. *Chronological Effort Progression & Data Cleansing (`REQ-SET-078.1`)*:
• The system SHALL sort member workouts in ascending chronological order based on `startTimeS`.
• Workouts with non-positive duration (`activeTimeSec <= 0`) or velocity (`avgSpeedMps <= 0.0`) SHALL be excluded from the trend plot.
• When the count of valid member workouts is strictly less than 2 ($N < 2$), the progression chart card SHALL be hidden completely from `WorkoutClusterDetailDashboard`.
• When $N \ge 2$, the progression chart card SHALL be displayed.
2. *Sport-Aware Dual Metric Modes & Inverted Scaling (`REQ-SET-078.2`)*:
• The chart SHALL support toggling between Duration and Speed/Pace modes.
• In Duration mode, `activeTimeSec` SHALL be plotted with inverted vertical scaling such that faster (lower) durations are rendered higher on the canvas, preserving the "higher is better" invariant.
• In Speed/Pace mode, if the cluster sport is Running (`BSportType.RUN`), pace SHALL be plotted with inverted vertical scaling (faster/lower pace higher); if Cycling (`BSportType.BIKE`) or other, speed SHALL be plotted with standard vertical scaling (higher speed higher).
• Unit conversions and formatting SHALL delegate strictly to `TimeFormatter`, `SpeedFormatter`, and `PaceFormatter`.
3. *Cluster Average Reference Line & Personal Best Highlight (`REQ-SET-078.3`)*:
• The chart SHALL draw a horizontal dashed reference line across the plot representing the cluster average value (duration, speed, or pace).
• The effort with the fastest time (minimum `activeTimeSec`) SHALL be decorated with a distinctive gold marker and PR badge.
4. *Interactive Touch Selection & Peek Launch (`REQ-SET-078.4`)*:
• Tapping a data point within 24dp hit radius SHALL select that effort, drawing an active indicator ring and updating a compact callout card with date, duration, speed/pace, and PR badge.
• Tapping the selected point or callout card SHALL dispatch `selectWorkoutForPeek(workout.id)`.
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
• Clamped outlier efforts SHALL be visually differentiated at the canvas boundary with an outlier glyph / ring styling and an outlier badge in the callout card.

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
• *Given* a rendered chart,
• *When* the user taps an effort point,
• *Then* the callout card updates with that effort's metrics (including outlier badge if applicable), and tapping the callout dispatches `selectWorkoutForPeek`.

**Invariants**: Cluster centroid arithmetic mean, route matching, athletic "faster-is-higher" orientation, 9-language localization parity, and 100% clean-room test pass rate.

### REQ-PRI-006: Decoupled Multi-Permission Cascade, Contextual Bluetooth & Notification Gating, Sensor Discovery Permission Handling, and Legacy Code Retirement [`TST-PRI-005`]
- **Tests**: `ControlTrackingScreen.kt`, `ControlTrackingButton.kt`, `DevicesTabbedScreen.kt`, `MainActivityWithNavigation.kt`, `BTSearchForNewDevicesEngine.java`, `strings.xml` (all 9 locales)
- **Implementation**: Decoupled multi-permission cascade for workout start, contextual Bluetooth and notification prompts when location is already granted, JIT sensor discovery permissions, and legacy code retirement.
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

### REQ-SET-079: Non-Blocking DataStore Architecture, Reactive In-Memory StateFlow Caching & Pure Parameter Passing for UI and Sensor Hot Paths [`TST-SET-079`]
- **Tests**: `SettingsDataStoreJavaHelper.kt`, `SettingsDataStore.kt`, `TuningPreferencesDataStore.kt`, `BatterySaverModel.kt`, `TrackingTabsScreen.kt`, `TrackingViewModel.kt`, `SpeedAndLocationDevice.java`, `VerticalSpeedAndSlopeDevice.java`, `TelemetryZoneMath.kt`, `LocationNameResolver.kt`
- **Implementation**: Eliminate blocking runBlocking DataStore reads from UI and sensor hot paths via reactive in-memory StateFlow caching, singleton repositories, pure parameter passing, and non-blocking Java bridges.
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

### REQ-PRI-007: Abortable First-Install Permission Onboarding Flow and Upgrade Auto-Skip Architecture [`TST-PRI-006`]
- **Tests**: `PermissionOnboardingSheet.kt`, `ATrainingTrackerApp.kt`, `TuningPreferencesDataStore.kt`, `strings.xml` (all 9 locales)
- **Implementation**: Abortable first-install permission onboarding flow on initial app launch, DataStore persistence, upgraded user auto-skip, and JIT fallback preservation.
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

### REQ-LOC-002: Locale Hygiene, Translation Completeness, and ExtraTranslation De-duplication [`TST-STR-019`]
- **Tests**: `res/values-*/strings.xml`, `res/values-{de,pl}/arrays.xml`, `TranslationParityTest.kt`
- **Implementation**: Eliminate untranslated English strings across 8 locales, purge 7 redundant translatable="false" keys from locales, excise redundant string-arrays in DE/PL, and enforce with automated parity guard.
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
