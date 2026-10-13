# Stage 2: Requirement & Test Specification - ATT-1281: Persistent Climbs Database & Live ClimbPro Cockpit Sheet (Route & Free-Riding Support)

**Ticket**: [ATT-1281](https://rainerblind.atlassian.net/browse/ATT-1281)  
**Sub-task**: [ATT-2293](https://rainerblind.atlassian.net/browse/ATT-2293) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-66](https://rainerblind.atlassian.net/browse/ATT-66) (*[Epic] Improve Routes*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `Sprint 2026-40.14`  
**Requirement Mapping**: `REQ-MAP-027` (*Persistent Climbs Database & Live ClimbPro Cockpit Sheet (Route & Free-Riding Support)*)  
**Test Spec ID**: `TST-MAP-029`  
**Branch**: `feature/ATT-1281`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-04  

---

## 1. Requirement Specification (REQ-MAP-027)

### 1.1 Problem Statement & Rationale
During outdoor cycling and running workouts, pacing on sustained climbs is essential. Athletes need real-time awareness of upcoming climbs, including remaining distance, remaining elevation gain, current gradient, and a color-coded slope profile, both when following an active navigation route and during free riding.

### 1.2 Functional & Architectural Requirements

1. **Automated Climb Detection Engine (`ClimbDetector.kt`)**:
   - The system SHALL analyze route trackpoints (`PathPoint`) with elevation profiles to detect ascents.
   - Ascents SHALL qualify as climbs when total length $\ge \text{climbMinLengthMeters}$ (default $500\text{ m}$), average gradient $\ge \text{climbMinGradientPercent}$ (default $3.0\%$), and total elevation gain $\ge 20\text{ m}$.
   - The detector SHALL tolerate micro-dips within a climb (up to $15\text{ m}$ vertical descent or $150\text{ m}$ horizontal distance) without splitting the ascent, provided net slope remains positive and meets the average grade threshold.
   - Climbs SHALL be classified into standard categories based on $\text{Score} = \text{length (m)} \times \text{gradient (\%)}$:
     - $\text{Score} \ge 80,000$: `HC` (Hors Catégorie)
     - $\text{Score} \ge 64,000$: `CAT_1`
     - $\text{Score} \ge 32,000$: `CAT_2`
     - $\text{Score} \ge 16,000$: `CAT_3`
     - $\text{Score} \ge 8,000$: `CAT_4`
     - $\text{Score} < 8,000$: `UNCATEGORIZED`

2. **Persistent Climbs Database & Spatial Deduplication (`ClimbsDatabaseManager.kt`)**:
   - The system SHALL maintain an independent SQLite database `Climbs.db` with table `climbs`:
     - `id INTEGER PRIMARY KEY AUTOINCREMENT`
     - `name TEXT NOT NULL`
     - `route_id INTEGER`
     - `start_lat REAL NOT NULL`, `start_lng REAL NOT NULL`
     - `end_lat REAL NOT NULL`, `end_lng REAL NOT NULL`
     - `distance_m REAL NOT NULL`
     - `elevation_gain_m REAL NOT NULL`
     - `avg_grade REAL NOT NULL`
     - `max_grade REAL NOT NULL`
     - `climb_category TEXT NOT NULL`
     - `path_polyline TEXT NOT NULL`
   - Indexes `idx_climbs_start_lat_lng` and `idx_climbs_route_id` SHALL be maintained.
   - The system SHALL execute spatial deduplication: When inserting a climb, if both its start coordinate and summit coordinate lie within $50\text{ m}$ of an existing climb, the duplicate SHALL be skipped or updated.

3. **Live Climb Tracking Engine (`LiveClimbsRepository.kt`)**:
   - The repository SHALL track live GPS coordinates and bearing from `BANALServiceRepository`.
   - The state machine SHALL transition through:
     - `FAR_FAR_AWAY`: Distance to start $> 250\text{ m}$.
     - `APPROACHING`: Distance to start $\le 250\text{ m}$ with bearing difference $\le 45^\circ$.
     - `ON_CLIMB`: Crossed start gate in the forward direction.
     - `FINISHED`: Crossed summit finish gate; stays in finished for 5 seconds before returning to `FAR_FAR_AWAY`.
   - When navigating an active route (`RoutesRepository.activeNavigatedRouteId`), climbs along the route SHALL be sequenced with index and total count (e.g. "Anstieg 2 von 4").
   - When free riding (no active route), all nearby stored climbs in `Climbs.db` SHALL be tracked.

4. **Live Climb Cockpit Sheet UI (`LiveClimbSheet.kt`, `SensorGridScreen.kt`)**:
   - The bottom sheet in `SensorGridScreen.kt` SHALL render:
     - Header: Climb name, category badge, and route sequence counter if applicable.
     - Elevation profile curve colored by slope segments using app standard tokens:
       - $< 2\%$: `TTColor.Zone1` (Chartreuse)
       - $2 - 5\%$: `TTColor.Zone2` (Green)
       - $5 - 10\%$: `TTColor.Zone3` (Orange)
       - $10 - 15\%$: `TTColor.Zone4` (Red)
       - $15 - 20\%$: `TTColor.Zone5` (Dark Violet)
       - $> 20\%$: `Color.Black`
     - Real-time rider position marker along the profile curve.
     - Pacing HUD telemetry: Remaining distance to summit, remaining elevation gain ($\Delta h_{\text{rest}}$), and current gradient (%).
   - Priority Arbiter: When a Strava Live Segment is active (`ON_SEGMENT`), it SHALL take visual precedence on the sheet. Upon finishing the segment, the sheet SHALL seamlessly revert to the ongoing climb.

5. **User Tuning & Localization Parity**:
   - The system SHALL provide user toggles in `TuningPreferencesDataStore.kt`:
     - `showLiveClimbs` (Boolean, default `true`)
     - `climbMinLengthMeters` (Int, default `500`)
     - `climbMinGradientPercent` (Double, default `3.0`)
   - All user-facing strings SHALL maintain 100% parity across all 9 application locales (EN, DE, ES, FR, IT, JA, NL, PL, PT).

### 1.3 Acceptance Criteria (Given-When-Then)

* **Criterion 1 (Climb Detection from Route Points)**:
  - *Given* a route with a 1.2km hill ascending 84m (average grade 7.0%),
  - *When* evaluated by `ClimbDetector.detectClimbs`,
  - *Then* exactly 1 climb SHALL be detected with category `CAT_4`, length ~1200m, and gain ~84m.

* **Criterion 2 (Micro-Dip Tolerance)**:
  - *Given* a 2.5km climb that includes a brief 100m flat/slight descent dipping 5m before continuing upward,
  - *When* evaluated by `ClimbDetector.detectClimbs`,
  - *Then* the ascent SHALL NOT be split into two separate climbs, and the full climb SHALL be retained.

* **Criterion 3 (Spatial Deduplication)**:
  - *Given* a stored climb in `ClimbsDatabaseManager` with start (48.0, 11.0) and summit (48.01, 11.02),
  - *When* another route containing an ascent starting at (48.0001, 11.0001) and ending at (48.0101, 11.0201) is inserted,
  - *Then* the system SHALL recognize the duplicate ($\Delta \text{dist} \le 50\text{ m}$) and NOT create a redundant row.

* **Criterion 4 (Live Tracking State Machine)**:
  - *Given* an athlete approaching a stored climb within 250m heading towards the start line,
  - *When* GPS updates are processed,
  - *Then* `LiveClimbsRepository` SHALL transition to `APPROACHING`, then to `ON_CLIMB` upon crossing the start gate, and to `FINISHED` upon crossing the summit.

* **Criterion 5 (Cockpit Sheet Pacing Display & Priority)**:
  - *Given* an active climb in progress,
  - *When* `LiveClimbSheet` is composed in `SensorGridScreen`,
  - *Then* it SHALL display the remaining distance to summit, remaining vertical gain, and color-coded slope profile. If an overlapping Strava segment triggers, the Strava segment SHALL be shown until completed.

---

## 2. Test Specification (TST-MAP-029)

### Test Case 1: `ClimbDetectorTest` (`[TST-MAP-029.1]`)
* **Scope**: Pure JVM Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/climbs/ClimbDetectorTest.kt`
* **Test Methods**:
  1. `detectClimbs_detectsValidClimbAndCalculatesCategory`: Verifies detection of a 2km 6% climb (Cat 4), calculating accurate distance, gain, average grade, and max grade.
  2. `detectClimbs_ignoresFlatOrDescendingTracks`: Verifies that a flat 5km track or descending track produces 0 climbs.
  3. `detectClimbs_toleratesMicroDipsWithinClimb`: Verifies that small dips do not partition a sustained ascent.
  4. `detectClimbs_assignsCorrectClimbCategories`: Verifies category score thresholds (Cat 4, 3, 2, 1, HC).

### Test Case 2: `ClimbsDatabaseManagerTest` (`[TST-MAP-029.2]`)
* **Scope**: Unit / Robolectric SQLite Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/database/ClimbsDatabaseManagerTest.kt`
* **Test Methods**:
  1. `insertAndRetrieveClimb`: Verifies schema creation and inserting/querying a climb entity.
  2. `spatialDeduplication_skipsDuplicateWithin50Meters`: Verifies that duplicate ascents within 50m of start and end points are deduplicated.
  3. `deleteClimb_removesEntity`: Verifies delete operation.
  4. `getClimbsForRoute_returnsLinkedClimbs`: Verifies retrieval by `route_id`.

### Test Case 3: `LiveClimbsRepositoryTest` (`[TST-MAP-029.3]`)
* **Scope**: Coroutine StateFlow Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/climbs/LiveClimbsRepositoryTest.kt`
* **Test Methods**:
  1. `trackingState_transitionsFromFarToApproachingAndOnClimb`: Verifies start gate approach and crossing transitions.
  2. `trackingState_transitionsToFinishedAtSummit`: Verifies finish gate crossing at the summit.
  3. `routeContext_sequencesClimbsAlongActiveRoute`: Verifies climb sequencing (index/total) when active route is set.

### Test Case 4: 9-Language Localization & Translation Parity (`[TST-MAP-029.4]`)
* **Scope**: Localization Parity Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/TranslationParityTest.kt`
* **Goal**: Verify presence and format specifier matching for all new climb strings across EN, DE, ES, FR, IT, JA, NL, PL, PT.

### Test Case 5: Clean-Room Full-Suite Regression (`[TST-MAP-029.5]`)
* **Scope**: Full Clean-Room Regression
* **Command**: `./gradlew testDebugUnitTest`
* **Goal**: Verify 100% pass rate across all project unit tests with zero regressions.

---

## 3. Traceability Matrix

| Test Case | Scope | Class Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `[TST-MAP-029.1]` | Unit | `ClimbDetector` | `REQ-MAP-027.1` | Specified |
| `[TST-MAP-029.2]` | Database | `ClimbsDatabaseManager` | `REQ-MAP-027.2` | Specified |
| `[TST-MAP-029.3]` | Repository | `LiveClimbsRepository` | `REQ-MAP-027.3` | Specified |
| `[TST-MAP-029.4]` | Localization | `TranslationParityTest` | `REQ-MAP-027.5`, `REQ-UI-106` | Specified |
| `[TST-MAP-029.5]` | Regression | `./gradlew testDebugUnitTest` | `REQ-PRO-001` | Specified |
