# Living Test Specification: Settings, Profiles & Localization

**Domain**: `settings_profiles`  
**Prefixes**: `LOC`, `PRI`, `SET`  
**Scope**: Athlete settings, bike/sensor profiles, privacy zones, data stores, and 9-locale parity.  
**Total Tests**: 69

---

### TST-SET-031: **Unified Spatial Markers** [1. All views use Green pins with `control_start` icon and Red pins with `control_stop` icon. 2. Icons are vertically centered in the pin base.]
- **Requirement(s)**: `REQ-SET-041`
- **Component**: **Unified Spatial Markers**
- **Test Description**: 1. Open Workout Details, Period Map, and Route Heatmap. 2. Inspect Start/End markers.

### TST-SET-001: **Workout Learning Engine** [1. The second workout SHALL automatically be named \"Park Loop #2\". 2. The sport SHALL automatically be set to \"Running\". 3. Changing the name to \"Morning Loop\" SHALL update future suggestions to \"Morning Loop\".]
- **Requirement(s)**: `REQ-SET-006`, `007`, `008`
- **Component**: **Workout Learning Engine**
- **Test Description**: 1. Track a route. 2. Name it \"Park Loop\" and set sport to \"Running\". 3. Track the same route again.

### TST-SET-002: **Favorite Tracks Heatmap** [1. A map SHALL open showing a heatmap of all associated workouts. 2. Start, Stop, and Apex markers SHALL be visible as clear pins.]
- **Requirement(s)**: `REQ-SET-009`
- **Component**: **Favorite Tracks Heatmap**
- **Test Description**: 1. Navigate to \"Workout Clusters\" drawer. 2. Select a recurring path.

### TST-SET-003: **Cluster Tuning** [1. The master slider SHALL adjust all three tolerances proportionally. 2. The Workout Cluster database SHALL be wiped. 3. A background migration SHALL rebuild the clusters with the new parameters.]
- **Requirement(s)**: `REQ-SET-010`, `011`
- **Component**: **Cluster Tuning**
- **Test Description**: 1. Navigate to \"Workout Clusters\". 2. Open Tuning menu. 3. Adjust the master slider. 4. Optionally toggle detailed controls. 5. Tap \"Recalculate\".

### TST-SET-004: **Workout Cluster Item Map** [1. Each item SHALL display a small map. 2. Start (Green pin with start icon), End (Red pin with stop icon), and Apex (Blue pin with distance icon) SHALL be visible on the map.]
- **Requirement(s)**: `REQ-SET-012`
- **Component**: **Workout Cluster Item Map**
- **Test Description**: 1. Navigate to \"Workout Clusters\". 2. Inspect list items.

### TST-SET-005: **Name-Aware Clustering** [The system SHALL group them into the same cluster by applying the name-match bonus to the similarity score.]
- **Requirement(s)**: `REQ-SET-013`
- **Component**: **Name-Aware Clustering**
- **Test Description**: 1. Identify two workouts with same name but high spatial drift. 2. Trigger recalculation.

### TST-SET-006: **Auto-Dismiss Tuning** [The UI SHALL automatically navigate back to the Favorite Tracks list view upon completion.]
- **Requirement(s)**: `REQ-SET-014`
- **Component**: **Auto-Dismiss Tuning**
- **Test Description**: 1. Open Tuning screen. 2. Tap \"Recalculate All Clusters\". 3. Wait for completion.

### TST-SET-007: **Unique Cluster Naming** [The second cluster SHALL be automatically named \"Home var 2\".]
- **Requirement(s)**: `REQ-SET-015`
- **Component**: **Unique Cluster Naming**
- **Test Description**: 1. Create a cluster named \"Home\". 2. Create a spatially distinct route and also name it \"Home\".

### TST-SET-008: **Edit Cluster Identity** [1. The name and sport SHALL be updated in the database. 2. The UI SHALL immediately reflect the new name and sport icon in the list and details view.]
- **Requirement(s)**: `REQ-SET-016`
- **Component**: **Edit Cluster Identity**
- **Test Description**: 1. Select a workout cluster. 2. Tap Edit Icon in TopAppBar. 3. Enter new name and select a different sport. 4. Save.

### TST-SET-009: **Manual Workout Reassignment** [1. The workout SHALL be moved to the target cluster. 2. Centroids for both clusters SHALL be recalculated. 3. The UI SHALL refresh to show the workout in its new home.]
- **Requirement(s)**: `REQ-SET-017`
- **Component**: **Manual Workout Reassignment**
- **Test Description**: 1. Open a Workout Heatmap. 2. Tap a specific workout polyline. 3. Select a different cluster from the candidate list. 4. Save.

### TST-SET-010: **Edit Workout Suggestions** [1. The workout name SHALL update to the cluster's name. 2. The sport SHALL update to the cluster's sport. 3. Saving SHALL persist the cluster association.]
- **Requirement(s)**: `REQ-SET-018`
- **Component**: **Edit Workout Suggestions**
- **Test Description**: 1. Edit a workout. 2. Tap the \"My Locations\" icon in the Name field. 3. Select a suggested cluster.

### TST-SET-011: **Manual Cluster Creation** [1. A new cluster SHALL be created in the database with 0 hit count. 2. The cluster SHALL be visible in the list.]
- **Requirement(s)**: `REQ-SET-019`
- **Component**: **Manual Cluster Creation**
- **Test Description**: 1. Navigate to \"Workout Clusters\". 2. Tap \"+\" FAB. 3. Fill in name and distance. 4. Tap map to set 3 points. 5. Save.

### TST-SET-012: **Workout Peek & Move** [1. The workout details SHALL be visible in the sheet. 2. The \"Move Workout\" dialog SHALL appear upon tapping the FAB.]
- **Requirement(s)**: `REQ-SET-020`
- **Component**: **Workout Peek & Move**
- **Test Description**: 1. Open a Workout Heatmap. 2. Tap a specific workout track. 3. Observe the Bottom Sheet peek. 4. Tap the \"Move\" FAB in the peek.

### TST-SET-013: **Manual Fingerprint Edit** [1. The cluster centroid SHALL be updated in the database. 2. Future matching SHALL use the new coordinates.]
- **Requirement(s)**: `REQ-SET-021`
- **Component**: **Manual Fingerprint Edit**
- **Test Description**: 1. Open a Workout Heatmap. 2. Tap a signature marker (Start/End/Apex). 3. Select \"Edit Position\". 4. Tap map to set new position. 5. Save.

### TST-SET-014: **Workout Markers in Cluster** [1. Markers for ALL member workouts SHALL be visible with subtle transparency. 2. Tapping a marker SHALL correctly trigger the detailed peek for that workout.]
- **Requirement(s)**: `REQ-SET-022`
- **Component**: **Workout Markers in Cluster**
- **Test Description**: 1. Open a Workout Heatmap. 2. Observe the distribution of markers for all members. 3. Tap a specific workout track OR any marker. 4. Inspect markers in the peek.

### TST-SET-015: **Imperial Units in Clusters** [1. Manual distance input SHALL accept miles and store as meters. 2. Tuning tolerances SHALL be displayed in miles with high precision.]
- **Requirement(s)**: `REQ-SET-023`
- **Component**: **Imperial Units in Clusters**
- **Test Description**: 1. Switch to Imperial. 2. Add manual cluster (miles). 3. View tuning details (mile).

### TST-SET-016: **Cluster Equipment Determination** [1. The list of equipment linked to the cluster's primary sport SHALL be visible.]
- **Requirement(s)**: `REQ-SET-024`
- **Component**: **Cluster Equipment Determination**
- **Test Description**: 1. Open a Workout Cluster. 2. Verify the associated equipment list (→).

### TST-SET-017: **Workout Cluster Collapsing Header** [1. The header SHALL collapse smoothly as the user scrolls. 2. The header SHALL re-expand when the user scrolls back to the top.]
- **Requirement(s)**: `REQ-SET-025`
- **Component**: **Workout Cluster Collapsing Header**
- **Test Description**: 1. Navigate to \"Workout Clusters\". 2. Scroll the list downwards. 3. Observe the TopAppBar behavior.

### TST-SET-018: **Workout Cluster Tabbed Layout** [1. The list SHALL filter clusters correctly based on the selected sport type tab. 2. Swiping SHALL be smooth and synchronized with the tab indicator.]
- **Requirement(s)**: `REQ-SET-026`
- **Component**: **Workout Cluster Tabbed Layout**
- **Test Description**: 1. Navigate to \"Workout Clusters\". 2. Observe the tabs at the top. 3. Swipe between tabs (All, Bike, Run, Other).

### TST-SET-019: **Workout Cluster Scroll Persistence** [1. The list SHALL remain scrolled to the previously viewed position. 2. The active tab SHALL be preserved.]
- **Requirement(s)**: `REQ-SET-027`
- **Component**: **Workout Cluster Scroll Persistence**
- **Test Description**: 1. Scroll down the \"All\" tab. 2. Select a cluster to open its heatmap. 3. Navigate back.

### TST-SET-020: **Unified FAB Layout** [1. The FAB SHALL be positioned at the bottom-right. 2. It SHALL correctly respect system navigation bar padding.]
- **Requirement(s)**: `REQ-SET-028`
- **Component**: **Unified FAB Layout**
- **Test Description**: 1. Navigate to \"Workout Clusters\". 2. Observe FAB position and styling. 3. Compare with \"Sport Types\" and \"Equipment\" FABs.

### TST-SET-021: **Identity Arbitration** [1. The workout SHALL adopt the cluster's Name. 2. The sport and equipment SHALL remain hardware-driven (Vetoing the route majority).]
- **Requirement(s)**: `REQ-SET-029`, `030`
- **Component**: **Identity Arbitration**
- **Test Description**: 1. Link a specific sensor to a bike. 2. Record a session with that sensor on a route matched to a cluster with a different majority sport.

### TST-SET-022: **Route-to-Cluster Matching** [1. A new Workout Cluster SHALL automatically be created for the imported route. 2. Recalculating history SHALL also include existing routes as cluster seeds.]
- **Requirement(s)**: `REQ-SET-031`
- **Component**: **Route-to-Cluster Matching**
- **Test Description**: 1. Import a GPX Route. 2. Navigate to \"Workout Clusters\".

### TST-SET-023: **Reference Route in Heatmap** [1. The authoritative route's path SHALL be visible as a solid, prominent line overlaid on the workout heatmap.]
- **Requirement(s)**: `REQ-SET-032`
- **Component**: **Reference Route in Heatmap**
- **Test Description**: 1. Open a Workout Cluster that is linked to an explicit Route. 2. Observe the heatmap visualization.

### TST-SET-024: **Workout Cluster Deletion** [1. The cluster SHALL be deleted. 2. Associated workouts SHALL remain in history but lose their cluster assignment.]
- **Requirement(s)**: `REQ-SET-033`
- **Component**: **Workout Cluster Deletion**
- **Test Description**: 1. Open a Workout Cluster detail view. 2. Long-click the header. 3. Select \"Delete\". 4. Confirm deletion.

### TST-SET-025: **Stable Cluster Naming** [1. The workout SHALL be named \"Park Loop #X\". 2. The cluster name SHALL remain \"Park Loop\".]
- **Requirement(s)**: `REQ-SET-034`
- **Component**: **Stable Cluster Naming**
- **Test Description**: 1. Identify a cluster named \"Park Loop\". 2. Track a workout matching this cluster.

### TST-SET-026: **Preview Heatmap** [1. If a Route is linked, its path is visible. 2. If no Route, a subtle heatmap of recent tracks is visible. 3. All 3 signature markers are visible.]
- **Requirement(s)**: `REQ-SET-035`
- **Component**: **Preview Heatmap**
- **Test Description**: 1. Open the Favorite Tracks list. 2. Observe the mini-map in a list item.

### TST-SET-027: **Persistent DB Connection** [1. RouteClusters.db should be visible and accessible as an active connection.]
- **Requirement(s)**: `REQ-SET-036`
- **Component**: **Persistent DB Connection**
- **Test Description**: 1. Start the app. 2. Open Android Studio Database Inspector.

### TST-SET-028: **Hit Count Synchronization** [1. The hit count should automatically update to 0. 2. Assigning a workout via editor should correctly increment both DBs.]
- **Requirement(s)**: `REQ-SET-037`, `038`
- **Component**: **Hit Count Synchronization**
- **Test Description**: 1. Identify a cluster with hit count > 0. 2. Use Database Inspector to manually clear `clusterId` for its workouts. 3. Open Favorite Tracks list.

### TST-SET-029: **Unclustered Tab Verification** [1. Workouts with clusterId=-1 are visible. 2. Selection dialog opens and reassignment works correctly.]
- **Requirement(s)**: `REQ-SET-040`
- **Component**: **Unclustered Tab Verification**
- **Test Description**: 1. Navigate to Favorite Tracks. 2. Open 'Offen' tab. 3. Tap a workout.

### TST-SET-030: **Locale-Aware Distance Input** [1. The 'Save' button becomes enabled. 2. Saving the cluster correctly parses '5,5' as 5.5 units (km or miles).]
- **Requirement(s)**: `REQ-SET-039`
- **Component**: **Locale-Aware Distance Input**
- **Test Description**: 1. Set device locale to German. 2. Open 'Add Workout Cluster' flow. 3. Enter '5,5' in the distance field.

### TST-SET-032: **Markers & Framing in Previews** [1. Workout previews show Start (Green), End (Red), and Apex (Blue) markers. 2. Route previews show ONLY Start and End markers. 3. All previews correctly frame the entire route, ensuring no parts are cropped.]
- **Requirement(s)**: `REQ-SET-042`
- **Component**: **Markers & Framing in Previews**
- **Jira**: ``ATT-390``
- **Test Description**: 1. Open Workout History, Routes, and My Locations lists. 2. Observe the mini-maps.

### TST-SET-033: **Fingerprint Parity** [1. Both the Route and Workout SHALL have the exact same Apex coordinate (Furthest from Start). 2. Grouping accuracy is 100%.]
- **Requirement(s)**: `REQ-SET-043`
- **Component**: **Fingerprint Parity**
- **Test Description**: 1. Import a Route. 2. Record a workout on that exact route. 3. Navigate to Favorite Tracks.

### TST-SET-034: **Sport-Aware Clustering** [Clustering logic correctly applies/removes the sport-type similarity penalty.]
- **Requirement(s)**: `REQ-SET-010`, `REQ-SET-043`
- **Component**: **Sport-Aware Clustering**
- **Jira**: ``ATT-399``
- **Test Description**: 1. Navigate to My Locations -> Tuning. 2. Toggle 'Take Sport Type into account' to ON. 3. Recalculate clusters. 4. Verify that Runs and Rides on the same path remain in separate clusters. 5. Toggle to OFF and recalculate. 6. Verify they merge into one cluster.

### TST-SET-035: **Sport Mismatch with UNKNOWN** [The 'Running' workout SHALL NOT match the 'UNKNOWN' cluster (+2.0 penalty applied).]
- **Requirement(s)**: `REQ-SET-043`
- **Component**: **Sport Mismatch with UNKNOWN**
- **Jira**: ``ATT-427``
- **Test Description**: 1. Ensure Tuning toggle is ON. 2. Record a 'Running' workout on a path that currently has an 'UNKNOWN' cluster.

### TST-SET-040: **Unclustered Workout Navigation** [1. Layout is consistent. 2. Map opens correctly. 3. Cluster selection dialog appears.]
- **Requirement(s)**: `REQ-SET-056`
- **Component**: **Unclustered Workout Navigation**
- **Jira**: ``ATT-278``
- **Test Description**: 1. Open 'Unclustered' tab in My Locations. 2. Verify layout parity with clusters (mini-map exists). 3. Tap a workout. 4. Verify navigation to map. 5. Tap 'Move to Cluster' icon.

### TST-SET-041: **Cluster Member Listing** [1. List is correctly filtered by clusterId.]
- **Requirement(s)**: `REQ-SET-057`
- **Component**: **Cluster Member Listing**
- **Jira**: ``ATT-280``
- **Test Description**: 1. Open 'Clusters' tab. 2. Click on the \"X recordings\" text for a cluster. 3. Verify navigation to a list containing only those recordings.

### TST-SET-042: **Workout Session Naming Isolation** [1. Workout is named 'Morning Run'. 2. Cluster name remains 'Park Loop' in the database and cluster list.]
- **Requirement(s)**: `REQ-SET-034`, `REQ-SET-044`
- **Component**: **Workout Session Naming Isolation**
- **Jira**: ``ATT-317``
- **Test Description**: 1. Track a workout matching cluster 'Park Loop'. 2. Open 'Edit Workout' screen. 3. Change name to 'Morning Run'. 4. Save.

### TST-SET-043: **Cluster Visibility & Re-clustering** [1. Cluster name and icon are visible and aligned to the left. 2. Re-assignment in editor successfully updates the workout's association.]
- **Requirement(s)**: `REQ-SET-058`, `REQ-SET-059`
- **Component**: **Cluster Visibility & Re-clustering**
- **Jira**: ``ATT-395``
- **Test Description**: 1. Open the Workout History list. 2. Verify each item displays its Cluster Name with the leading route icon at the very left below the workout name. 3. Edit a workout and manually re-assign it to a different cluster. 4. Save and verify the update in the list.

### TST-SET-044: **Selection Dialog Hit Count Accuracy** [The cluster SHALL correctly display "5 Aufzeichnungen" (or equivalent) in the suggestion list.]
- **Requirement(s)**: `REQ-SET-038`
- **Component**: **Selection Dialog Hit Count Accuracy**
- **Jira**: ``ATT-429``
- **Test Description**: 1. Identify a cluster with 5 associated workouts. 2. Open a workout for manual re-clustering or trigger a TCX import interaction. 3. Open the "Select Existing Route" dialog.

### TST-SET-045: **Selectable Cluster Markers** [Visibility reflects user selection and persists across sessions.]
- **Requirement(s)**: `REQ-SET-060`
- **Component**: **Selectable Cluster Markers**
- **Jira**: ``ATT-463``
- **Test Description**: 1. Open a Workout Cluster heatmap. 2. Tap the Marker Options button. 3. Uncheck "Start Point". 4. Verify that all green pins (signature and member distribution) disappear. 5. Re-check and Verify reappearance. 6. Re-check persistence after app restart.

### TST-SET-046: **Reactive Editor Suggestions** [Scores update reactively to sport changes.]
- **Requirement(s)**: `REQ-SET-018`
- **Component**: **Reactive Editor Suggestions**
- **Jira**: ``ATT-447``
- **Test Description**: 1. Edit a 'Running' workout on a 'Cycling' path. 2. Observe high similarity score (> 5.0) for 'Cycling' cluster in suggestions. 3. Change workout sport to 'Cycling'. 4. Verify score drops below 1.0 (penalty removed).

### TST-SET-047: **Idempotent Route Cluster Creation** [The system refines the existing "Route A" cluster and does NOT create a duplicate cluster named "Route A var 2".]
- **Requirement(s)**: `REQ-SET-061`
- **Component**: **Idempotent Route Cluster Creation**
- **Jira**: ``ATT-543``
- **Test Description**: 1. Import multiple TCX workout files that follow the same route name (e.g. "Route A"). 2. Verify cluster assignment.

### TST-SET-048: **Automatic Zero-Workout Cluster Purging** [Unlinked orphan route clusters with 0 workouts are automatically deleted from the database. Clusters linked to an explicit Route are preserved even with 0 workouts.]
- **Requirement(s)**: `REQ-SET-062`
- **Component**: **Automatic Zero-Workout Cluster Purging**
- **Jira**: ``ATT-544``
- **Test Description**: 1. Trigger a TCX import or cluster refresh. 2. Inspect the cluster list and database for orphan clusters with 0 assigned workouts and no linked Route. 3. Import an explicit Route with 0 workouts.

### TST-SET-049: **Route Cluster Apex Arithmetic Mean & Live Tracking Synchronization** [The cluster apex marker strictly represents the arithmetic mean of all member workouts, without UI-triggered database mutations or track-snapping deviations. Live workout tracking and accumulator resets preserve spatial integrity.]
- **Requirement(s)**: `REQ-SET-063`
- **Component**: **Route Cluster Apex Arithmetic Mean & Live Tracking Synchronization**
- **Jira**: ``ATT-1138``
- **Test Description**: 1. Assign or learn workouts into a Workout Cluster. 2. Verify that the cluster's `maxDispLat` and `maxDispLng` are the exact arithmetic mean of the member workouts' maximum displacement coordinates. 3. Delete or move a member workout and verify the moving average reverses accurately. 4. Select/inspect the cluster in `WorkoutClustersViewModel` and verify that the cluster's apex in SQLite is NOT mutated or overwritten. 5. Verify `WorkoutClusterEngine.onWorkoutFinished` propagates `maxDisplacementLatLng` instead of `startLatLng`. 6. Verify `SpeedAndLocationDevice` clears `mStartLocation` upon reset.

### TST-SET-050: **Explicit Cluster Selection (No Cluster, New Cluster) in Edit Workout** [1. Cluster selection is prominent and accessible below the workout name with the look and feel of the TCX import dialog. 2. "No Cluster" explicitly detaches the workout from any cluster. 3. "New Cluster" creates a new cluster from the workout. 4. Candidate cluster selection updates the cluster identity and hit counts reliably.]
- **Requirement(s)**: `REQ-SET-059`
- **Component**: **Explicit Cluster Selection (No Cluster, New Cluster) in Edit Workout**
- **Jira**: ``ATT-666``
- **Test Description**: 1. Open the Workout History list and tap a workout to open `EditWorkoutScreen`. 2. Verify an explicit Cluster Assignment field is rendered directly below the Workout Name input, displaying the route icon (`my_locations`), label ("Route Assignment" / "Selected Route"), and current cluster name (or "Unclustered" / "Leave Unclustered"). 3. Tap the Cluster Assignment field to launch the assignment dialog (harmonized with `RouteClusterNamingDialog`). 4. Select "Leave Unclustered" / "Kein Cluster" and save the workout: verify that the workout is unassigned (`clusterId = -1L`), its previous cluster hit count is decremented, and the workout header displays "Unclustered". 5. Re-open editor, tap the Cluster Assignment field, select "Create New..." / enter a new route name, and save: verify a new cluster is generated from the workout's spatial fingerprint and assigned to the workout. 6. Re-open editor, tap the Cluster Assignment field, select an existing cluster candidate: verify the workout is reassigned, hit counts are updated atomically, and the new cluster name is displayed.

### TST-SET-051: **Speed-Based Multi-Sport Cluster Discovery & Identity Resolution** [1. Ambiguous intermediate-speed workout matches existing route cluster without creating false duplicate cluster. 2. Workout adopts cluster sport type. 3. Explicit user pre-selection and hardware sensors take absolute precedence.]
- **Requirement(s)**: `REQ-SET-064`
- **Component**: **Speed-Based Multi-Sport Cluster Discovery & Identity Resolution**
- **Jira**: ``ATT-773``
- **Test Description**: 1. Create/seed a Workout Cluster with `BSportType.BIKE` for a known route geometry. 2. Simulate/record a workout following the identical route geometry with average speed 3.0 m/s without paired cadence/power sensors and without user pre-selection (speed suggests both `RUN` and `BIKE`). 3. Verify that `suggestCluster` identifies the existing bike cluster with 0.0 sport penalty and links the workout to it, adopting `BSportType.BIKE`. 4. Repeat with explicit user pre-selection of `BSportType.RUN`: verify the bike cluster receives +5.0 penalty and is rejected. 5. Repeat with high-confidence cadence sensor active: verify candidate sports are strictly `{BIKE}`.

### TST-SET-052: **Altitude Extrema-Aware Cluster Matching, Migration & Fingerprint Relocation** [1. Database upgrades to v10 seamlessly with historical altitude backfill.
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

### TST-SET-053: **Workout Cluster Auto-Naming Counter Ergonomics Verification** [The first workout of a cluster receives the clean cluster name without `"#1"`. Subsequent workouts receive incrementing numerical suffixes `"#<count>"`. Clustering matching, suggestions, and normalization remain 100% stable.]
- **Requirement(s)**: `REQ-SET-066`
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

### TST-SET-054: **Optional Route Cluster Counter Verification** [When `hasCounter` is true (default), sequential numbering `#2`, `#3` applies. When `hasCounter` is false, workouts retain the static cluster name without numbers. Existing databases upgrade safely to v11.]
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

### TST-SET-055: **Workout Cluster Performance & Volume Statistics Verification** [Aggregate statistics accurately calculate Best Time, Average Time, Average Speed/Pace, Total Distance, Total Climbing, and Recency. Metrics adapt to sport type, handle zero-workout edge cases, and render in a structured 3-tier layout across all 9 locales.]
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
  3. Presentation & Sport-Adaptive Verification:
  • Running Sport: Verify velocity formats as Pace (e.g., `min/km`).
  • Cycling Sport: Verify velocity formats as Speed (e.g., `km/h`).
  • Detail Dashboard (`WorkoutClusterSummaryHeader`): Verify 3-tier card layout renders all metrics with trophy icon for Best Time.
  • List Card (`ClusterItem`): Verify Best Time, Total Distance, and Last Hit render cleanly without clipping the 100x100 preview map.
  4. Localization & Parity Check:
  • Verify 100% translation coverage for all new stat labels across all 9 supported languages (EN, DE, ES, FR, IT, JA, NL, PL, PT).
  5. Invariant & Regression Check:
  • Full regression suite (`./gradlew testDebugUnitTest`) passes with 0 failures; spatial clustering and sorting remain 100% intact.

### TST-SET-056: **Multi-Sport Candidate Clustering & Cost Minimization in Workout Editor & Tracker** [1. Multi-sport speed profile correctly identifies all candidate sports across base sport boundaries without premature single-sport lock-in.
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

### TST-SET-057: **Direct Navigation from Workout to Linked Workout Cluster Verification** [1. Clustered workouts render a prominent, compact subtle primary tinted Button mirroring the cluster detail screen's recordings button.
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

### TST-SET-058: **Workout, Route & Segment Bottom Scaffold Status Bar Boundary & Peek Verification** [1. When fully expanded, bottom sheets stop cleanly at the bottom of the system status bar across Workouts, Routes, and Segments with zero overlap.
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

### TST-SET-059: **Workout Popup Direct Edit & Header Button Layout Verification** [1. Edit button appears on the very right across both workout popup and cluster summary headers.
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

### TST-SET-060: **Workout Card Click-to-Map Navigation & Dedicated Header Edit Action Verification** [1. Tapping workout body sections (header, description, details, extrema, map) reliably navigates to `TrackOnMapScreen`.
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

### TST-SET-061: **Edit Workout New Cluster Creation & Name Synchronization Verification** [1. Create Cluster dialog pre-fills clean base name without counter.
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

### TST-SET-062: **Advanced Tuning Preferences Clamping, Persistence, and Reset Verification** [1. All tuning parameters clamp strictly within bounds.
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

### TST-SET-063: **Expert Settings Navigation Drawer Grouping, Localization and 50m GPS Default Verification** [1. Expert Settings appears in a dedicated final drawer group at the bottom of the navigation drawer.
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

### TST-SET-064: **Favorite Route Icon Disambiguation for Clusters in Workout Header and Editor** [Clustered and unclustered cluster chips in WorkoutHeader and the selected route field in EditWorkoutScreen render `ic_favorite_route`. Point locations (Lieblingsorte) preserve `my_locations`, eliminating visual ambiguity between routes and geographic endpoints.]
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

### TST-SET-076: **Red Cluster Recalculate Button and Direct Advanced Settings Deep-Link from Favorite Routes Verification** [Recalculate button styled in high-visibility Material 3 error colors with accessible progress indicator; Favorite Routes info dialog provides educational explanation and direct deep-link to Advanced Settings with strict dismissal ordering; 100% 9-language parity; modularity < 400 lines preserved; 100% test pass rate.]
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

### TST-SET-077: **Standardized Route Clustering Form Controls via TuningFormControls Verification** [Route clustering form controls in ClusterTuningContent standardized with TuningFormControls (Rule 36), full state propagation, negative callback gating, and deprecation preservation; 100% test pass rate.]
- **Requirement(s)**: `REQ-SET-077`
- **Component**: **Standardized Route Clustering Form Controls via TuningFormControls Verification**
- **Jira**: ``ATT-3365``
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
  4. *Clean-Room Full Suite Regression Execution*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with zero regressions.

### TST-PRI-002: **Contextual Just-in-Time Permission Flow, Zero-Friction Cold Start, Background Location Escalation & Battery Optimization Verification** [Cold start is free of blocking modal dialogs; permissions requested contextually at moment of intent; background location escalation ('Allow all the time') and battery optimization exemption ('Unrestricted') handled gracefully with fail-safe OEM fallback; warning badge signals missing permissions with direct settings guidance; 100% test pass rate.]
- **Requirement(s)**: `REQ-PRI-003`
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

### TST-PRI-003: **Precise Location Gating and Dynamic Sensor Device Instantiation Verification** [Workout tracking strictly requires Precise Location (ACCESS_FINE_LOCATION); Approximate location triggers educational rationale and amber warning badge; GPS devices instantiate dynamically upon permission grant without app restart; 100% 9-language translation parity; 100% test pass rate.]
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

### TST-PRI-004: **Proactive Background Energy Warning Banner, Start Badge Integration & Cascade Decoupling Verification** [Proactive battery warning banner renders dynamically; Start button badge reflects missing background energy; rationale cascade decouples from background location; 100% unit test pass rate.]
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

### TST-SET-078: **Workout Cluster Historical Effort Trend Chart, Dual-Metric Inversion, Outlier-Resilient Y-Scaling, X/Y Axis Scale Labels, and 9-Language Parity Verification Suite** [Chronological sorting, non-positive filtering, faster-is-higher scaling, division-by-zero resilience, PR index detection, touch hit testing, Tukey IQR outlier-resilient Y-scaling, X/Y axis scale markers, density-adaptive point sizing, 9-language localization parity, and full test suite pass specified.]
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
  4. *PR Determination & Touch Hit Testing Test (`TST-SET-078.4`, `WorkoutClusterTrendMathTest.kt`)*:
  • Verify PR index corresponds to minimum duration effort.
  • Verify nearest-point touch detection identifies points within radius $R$ and returns null beyond $R$.
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
  10. *9-Language Localization Parity for Outlier String (`TST-SET-078.10`, `WorkoutClusterTrendLocalizationTest.kt` / `TranslationParityTest.kt`)*:
  • Verify string key `cluster_trend_outlier` exists and is non-empty across all 9 application locales (`values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`).
  11. *Clean-Room Regression Suite Execution (`TST-SET-078.11`)*:
  • Execute `./gradlew testDebugUnitTest` asserting 100% pass rate with zero regressions.

### TST-PRI-005: **Decoupled Multi-Permission Cascade, Contextual Bluetooth & Notification Gating, Sensor Discovery Permission Handling, and Legacy Code Retirement Verification Suite** [Decoupled permission check predicates allow independent notification and Bluetooth gating when location is already granted; Just-in-Time sensor discovery prompts athlete for missing Bluetooth permissions rather than failing silently; dead legacy methods in MainActivityWithNavigation are retired; 100% 9-language translation parity; 100% test pass rate.]
- **Requirement(s)**: `REQ-PRI-006`, `REQ-UI-106`
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

### TST-SET-079: **Non-Blocking DataStore Architecture, Reactive In-Memory StateFlow Caching, Pure Parameter Passing & Hot Path Decoupling Verification Suite** [Reactive in-memory StateFlow caching, non-blocking Java helper query contracts, pure BatterySaverModel zone index math, hot path UI and sensor thread decoupling architecture verification, mockkStatic compatibility preservation, and clean-room full test pass specified.]
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

### TST-PRI-006: **Abortable First-Install Permission Onboarding Flow, DataStore Persistence, Upgrade Auto-Skip & 9-Language Localization Verification Suite** [Abortable first-install permission onboarding flow verified across DataStore persistence, upgraded user auto-skip, wizard step navigation and abortability, JIT fallback preservation, 9-language localization parity, and clean-room full test suite execution.]
- **Requirement(s)**: `REQ-PRI-007`, `REQ-PRI-003`, `REQ-PRI-006`, `REQ-UI-106`
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
