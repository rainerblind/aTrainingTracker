# Stage 1 Analysis: ATT-1398 Lieblingsorte Intelligent Workout Auto-Naming

**Ticket**: [ATT-1398](https://atrainingtracker.atlassian.net/browse/ATT-1398)  
**Parent Epic**: [ATT-1396](https://atrainingtracker.atlassian.net/browse/ATT-1396) (*Lieblingsorte: Visible Value, Auto-Naming & Tracking Integration*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.3`  
**Subtask**: `ATT-1564`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-09-29  

---

## 1. Problem Domain & Background Analysis

In `aTrainingTracker`, "Lieblingsorte" (known start locations in `StartLocation2Altitude.db`) serve as authoritative elevation references and geofence anchors. However, despite recognizing when workouts start and finish at these locations (e.g. "Zuhause", "Büro", "Olympiapark"), this spatial knowledge is discarded during workout finalization:

### Current Deficiencies:
1. **Cryptic Default Workout Names**:
   - In `TrackerService.java` (line 760), a new workout summary is initialized with `values.put(WorkoutSummaries.WORKOUT_NAME, mBaseFileName)` (e.g. `2026-09-25_19-15-10`).
   - If the workout matches an existing Route Cluster (`suggestion != null`), it adopts the cluster name (e.g. "Isarrunde #3").
   - However, if the workout does NOT match an existing cluster (`suggestion == null`), `TrackerService.java` leaves the workout name as the cryptic timestamp string (`mBaseFileName`).
   - Athletes must manually rename every unclustered activity in the app or Strava.
2. **Generic Cluster Seed Proposals**:
   - In `WorkoutClusterEngine.kt` (lines 577 and 722), when a new route family / cluster proposal is seeded, it defaults to `cluster_default_name_format` ("Workout at 2026-09-25" / "Einheit bei 2026-09-25"), completely ignoring known location names.

---

## 2. Forensic Root Cause & Call-Site Audit

### Call-Sites in `TrackerService.java`:
* Line 760: Initial workout summary insert assigns `WORKOUT_NAME = mBaseFileName`.
* Line 1296: `engine.suggestCluster(...)` evaluates spatial cluster matches.
* Line 1313-1316:
  ```java
  } else {
      // No cluster match -> use hardware identity
      summariesManager.applyInferredIdentity(mWorkoutID, identity);
  }
  ```
  *Root Cause*: There is no fallback logic querying `KnownLocationsDatabaseManager` to resolve start and destination locations when no cluster matches.

### Call-Sites in `WorkoutClusterEngine.kt`:
* Line 577:
  ```kotlin
  val clusterName = if (!isDefault) normalizedName!! else context.getString(R.string.cluster_default_name_format, fileBaseName?.take(10) ?: "Workout")
  ```
* Line 722:
  ```kotlin
  val fallbackName = workout.workoutName.ifBlank { context.getString(R.string.cluster_default_name_format, "Workout") }
  ```
  *Root Cause*: Cluster creation relies strictly on generic string formats without querying `KnownLocationsDatabaseManager.getMyLocation(start)`.

---

## 3. Chesterton's Fence & Invariant Archaeology

* **Cluster Hierarchy Invariant**: Established route clusters and explicit user cluster names MUST ALWAYS have precedence over automatic location naming.
* **User Edit Invariant**: Custom workout names manually edited by the athlete via `WorkoutRepository.setWorkoutName` MUST NEVER be overwritten by background services or automated cluster engines.
* **Hardware Inferred Identity Invariant**: Sensor-based sport type detection (`identity`) and sensor streams MUST NOT be perturbed.
* **Database Concurrency Invariant**: Spatial queries to `KnownLocationsDatabaseManager` must remain thread-safe and non-blocking during workout finalization.

---

## 4. Scope Bounding (ATT-1250 Grounding)

### In-Scope:
1. **Intelligent Auto-Naming Engine (`WorkoutAutoNamingHelper.kt`)**:
   - Query `KnownLocationsDatabaseManager.getMyLocation(startPosRaw)` and `getMyLocation(endPosRaw)`.
   - Heuristics:
     - **Round Trip (Start == End within geofence)**:
       - Run: `"Lauf ab {Startort}"` (DE) / `"Run from {Startort}"` (EN)
       - Bike: `"Fahrt ab {Startort}"` (DE) / `"Ride from {Startort}"` (EN)
       - Other: `"Runde ab {Startort}"` (DE) / `"Loop from {Startort}"` (EN)
     - **Point-to-Point (Start != End, both known)**:
       - Run: `"Lauf von {Startort} nach {Zielort}"` (DE) / `"Run from {Startort} to {Zielort}"` (EN)
       - Bike: `"Fahrt von {Startort} nach {Zielort}"` (DE) / `"Ride from {Startort} to {Zielort}"` (EN)
       - Other: `"Aktivität von {Startort} nach {Zielort}"` (DE) / `"Activity from {Startort} to {Zielort}"` (EN)
     - **Start Only Known**:
       - Run: `"Lauf ab {Startort}"` / `"Run from {Startort}"`
       - Bike: `"Fahrt ab {Startort}"` / `"Ride from {Startort}"`
       - Other: `"Aktivität ab {Startort}"` / `"Activity from {Startort}"`
     - **Destination Only Known**:
       - Run: `"Lauf nach {Zielort}"` / `"Run to {Zielort}"`
       - Bike: `"Fahrt nach {Zielort}"` / `"Ride to {Zielort}"`
       - Other: `"Aktivität nach {Zielort}"` / `"Activity to {Zielort}"`
2. **Integration into `TrackerService.java`**:
   - Upon session finalization without cluster match, resolve auto-name and update `WorkoutSummaries.WORKOUT_NAME`.
3. **Integration into `WorkoutClusterEngine.kt`**:
   - When suggesting/creating a new cluster, default name to `"Runde ab {Startort}"` / `"Loop from {Startort}"` if start location is recognized.
4. **100% 9-Language Localization Parity**:
   - String templates across `de`, `en`, `es`, `fr`, `it`, `ja`, `nl`, `pl`, `pt`.

### Out-of-Scope:
- Modifying SQLite database schemas for `WorkoutSummaries` or `KnownLocations`.
- UI redesign of workout list items or history screens.

---

## 5. Risk Assessment & Stage Gate 1 Readiness

* **Risk Level**: LOW. Additive string resolution logic invoked during workout finalization.
* **Recommendation**: RECOMMEND PASS. Ready for Stage 2 (Requirement & Test Specification).
