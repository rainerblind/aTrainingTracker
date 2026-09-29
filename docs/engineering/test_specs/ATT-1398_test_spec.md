# Stage 2 Requirement & Test Specification: ATT-1398 Lieblingsorte Intelligent Workout Auto-Naming

**Ticket**: [ATT-1398](https://atrainingtracker.atlassian.net/browse/ATT-1398)  
**Parent Epic**: [ATT-1396](https://atrainingtracker.atlassian.net/browse/ATT-1396) (*Lieblingsorte: Visible Value, Auto-Naming & Tracking Integration*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.3`  
**Subtask**: `ATT-1565`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-09-29  

---

## 1. Requirement Traceability & Formal Specification

### Formal Requirement: `REQ-TRK-011` (*Intelligent Workout Auto-Naming based on Recognized Start and Destination Lieblingsorte*)
The system SHALL automatically generate meaningful, localized workout names based on recognized favorite locations (*Lieblingsorte*) for recorded sessions that do not match an existing Route Cluster (ATT-1398):

1. **Start & Destination Location Resolution**:
   - Upon workout finalization in `TrackerService.java`, when a session does not match an existing cluster (`suggestion == null`), the system SHALL resolve start and end coordinates (`startPosRaw`, `endPosRaw`) against `KnownLocationsDatabaseManager.getMyLocation(...)`.
2. **Naming Heuristics**:
   - **Round Trips (Start == Destination within geofence)**: When both start and end resolve to the same location (or $\le \text{geofence radius}$), the name SHALL format based on activity sport type:
     - Running (`BSportType.RUN`): Localized "Run from %1$s" (German: "Lauf ab %1$s").
     - Cycling (`BSportType.BIKE`): Localized "Ride from %1$s" (German: "Fahrt ab %1$s").
     - General / Other (`BSportType.UNKNOWN` / `CONFLICT`): Localized "Loop from %1$s" (German: "Runde ab %1$s").
   - **Point-to-Point (Start != Destination, both recognized)**: When start and destination resolve to distinct locations:
     - Running: Localized "Run from %1$s to %2$s" (German: "Lauf von %1$s nach %2$s").
     - Cycling: Localized "Ride from %1$s to %2$s" (German: "Fahrt von %1$s nach %2$s").
     - Other: Localized "Activity from %1$s to %2$s" (German: "Aktivität von %1$s nach %2$s").
   - **Start Only Recognized**: When only start resolves to a known location, format with single-location prefix (e.g. "Run from %1$s" / "Lauf ab %1$s").
   - **Destination Only Recognized**: When only destination resolves, format as "Run to %1$s" / "Lauf nach %1$s".
   - **Neither Recognized (Fallback)**: If neither start nor destination matches a known location, the system SHALL retain standard default naming (`mBaseFileName`).
3. **Cluster Proposal Seeding (`WorkoutClusterEngine.kt`)**:
   - When suggesting or creating a new Route Cluster for an unclustered track that starts at a known location, the default cluster name SHALL be seeded with "Loop from %1$s" / "Runde ab %1$s" instead of generic "Workout at YYYY-MM-DD".
4. **User Sovereignty & Invariants**:
   - Established route cluster matches (`suggestion != null`) SHALL take precedence over location naming.
   - Manual user edits to workout names (`WorkoutRepository.setWorkoutName`) SHALL remain strictly immutable.
   - Single-thread database serialization (`KnownLocationsDB-Thread`), hardware sport type inference, and 9-language localization parity SHALL be preserved.

---

## 2. Test Specification: `TST-TRK-003`

### Target Test Suite:
`app/src/test/java/com/atrainingtracker/trainingtracker/database/WorkoutAutoNamingHelperTest.kt`  
`app/src/test/java/com/atrainingtracker/trainingtracker/database/WorkoutClusterEngineLocationTest.kt`

### Test Procedures:

1. **`testGenerateWorkoutName_roundTrip_run`**:
   - *Given* mock `startLocation = "Zuhause"` and `endLocation = "Zuhause"`, `BSportType.RUN`.
   - *When* `WorkoutAutoNamingHelper.generateWorkoutName()` is invoked,
   - *Then* return `"Run from Zuhause"` (EN) / `"Lauf ab Zuhause"` (DE).

2. **`testGenerateWorkoutName_roundTrip_bike`**:
   - *Given* `startLocation = "Zuhause"`, `endLocation = "Zuhause"`, `BSportType.BIKE`.
   - *When* `generateWorkoutName()` is invoked,
   - *Then* return `"Ride from Zuhause"` (EN) / `"Fahrt ab Zuhause"` (DE).

3. **`testGenerateWorkoutName_pointToPoint_run`**:
   - *Given* `startLocation = "Zuhause"`, `endLocation = "Büro"`, `BSportType.RUN`.
   - *When* `generateWorkoutName()` is invoked,
   - *Then* return `"Run from Zuhause to Büro"` (EN) / `"Lauf von Zuhause nach Büro"` (DE).

4. **`testGenerateWorkoutName_pointToPoint_bike`**:
   - *Given* `startLocation = "Zuhause"`, `endLocation = "Büro"`, `BSportType.BIKE`.
   - *When* `generateWorkoutName()` is invoked,
   - *Then* return `"Ride from Zuhause to Büro"` (EN) / `"Fahrt von Zuhause nach Büro"` (DE).

5. **`testGenerateWorkoutName_startOnly`**:
   - *Given* `startLocation = "Zuhause"`, `endLocation = null`, `BSportType.RUN`.
   - *When* `generateWorkoutName()` is invoked,
   - *Then* return `"Run from Zuhause"` / `"Lauf ab Zuhause"`.

6. **`testGenerateWorkoutName_destinationOnly`**:
   - *Given* `startLocation = null`, `endLocation = "Büro"`, `BSportType.BIKE`.
   - *When* `generateWorkoutName()` is invoked,
   - *Then* return `"Ride to Büro"` / `"Fahrt nach Büro"`.

7. **`testGenerateWorkoutName_neitherKnown_returnsNull`**:
   - *Given* `startLocation = null`, `endLocation = null`.
   - *When* `generateWorkoutName()` is invoked,
   - *Then* return `null`.

8. **`testClusterSeeding_seedsFromLocation`**:
   - *Given* unclustered workout starting at known location "Olympiapark",
   - *When* generating default cluster proposal name,
   - *Then* return `"Loop from Olympiapark"` (EN) / `"Runde ab Olympiapark"` (DE).

9. **Localization Parity Audit**:
   - Verify string keys across 9 locales:
     - `workout_name_run_from`, `workout_name_ride_from`, `workout_name_activity_from`, `workout_name_round_trip`
     - `workout_name_run_from_to`, `workout_name_ride_from_to`, `workout_name_activity_from_to`
     - `workout_name_run_to`, `workout_name_ride_to`, `workout_name_activity_to`
     - `cluster_seed_name_loop_format`

10. **Clean-Room Regression**:
    - Run `./gradlew testDebugUnitTest` to guarantee 100% pass rate.
