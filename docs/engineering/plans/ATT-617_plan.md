# Implementation Plan - ATT-617: Comprehensive High-Fidelity TCX Workout Importer

## 1. Executive Summary & Problem Statement
The current TCX importer (`LegacyImportEngine.importFromTcx`) provides basic import capability for GPS coordinates, altitude, distance, heart rate, and cycling cadence. However, historical and third-party TCX files (from Garmin, Strava, Polar, Wahoo) contain high-fidelity telemetry that is currently discarded or flattened:
1. **Instantaneous Speed in TPX Extensions**: Trackpoint extension schemas (`<Extensions><TPX><Speed>`) are not parsed; speed is currently only derived as a session average from distance/time.
2. **Running Cadence in TPX Extensions**: Running workouts store cadence as `<RunCadence>` inside `<TPX>`, which is ignored by the parser, losing running cadence telemetry.
3. **Multi-Lap Preservation**: Multi-lap activities (intervals, auto-laps, structured workouts) are flattened into a single synthetic Lap 0. Individual lap split times, distances, average speeds, and max speeds are lost in `LapsDatabaseManager`.
4. **Active vs. Elapsed Duration & Pause Handling**: Workout duration is estimated simply as `points.size`, which distorts duration when GPS smart recording or pause periods exist.
5. **Supplementary Metrics & Metadata**: Lap-level `<Calories>` and activity-level `<Notes>` are discarded instead of populating `WorkoutSummaries`.

This implementation upgrades `LegacyImportEngine` and `LapsDatabaseManager` to parse and persist all standard TCX elements and Garmin ActivityExtension v2 streams with 100% fidelity, matching the native tracking fidelity of the app.

---

## 2. Requirement & Test Mapping
* **Requirement**: [REQ-MIG-024](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/requirements.md#L306) (*High-Fidelity TCX Telemetry & Multi-Lap Structure Import*)
* **Verification Test**: [TST-MIG-021](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/tests.md#L130) (*High-Fidelity TCX Telemetry & Multi-Lap Structure Import*)
* **Jira Test Sub-Task**: [ATT-618](https://atrainingtracker.atlassian.net/browse/ATT-618)
* **Jira Planning Sub-Task**: [ATT-619](https://atrainingtracker.atlassian.net/browse/ATT-619)

---

## 3. Impact Analysis (SWE.1.BP.5)
### Mapped Requirements Cross-Check
| File | Mapped Requirements | Safety & Non-Regression Confirmation |
|:---|:---|:---|
| `LegacyImportEngine.kt` | `REQ-EXT-008`, `REQ-MIG-005`, `REQ-MIG-013`, `REQ-MIG-014`, `REQ-MIG-015`, `REQ-MIG-016`, `REQ-MIG-017`, `REQ-MIG-018`, `REQ-MIG-019`, `REQ-MIG-021`, `REQ-MIG-022`, `REQ-MIG-024` | Automated Strava upload (`REQ-EXT-008`), deduplication (`REQ-MIG-005`), background concurrency (`REQ-MIG-014`), sparse stream detection (`REQ-MIG-018`), and source filename preservation (`REQ-MIG-022`) are strictly preserved. |
| `LapsDatabaseManager.java` | `REQ-TRK-002` | An overloaded `saveLap` method is introduced accepting `timeStart`. Existing callers (`TrackerService`) remain unchanged. |
| `WorkoutSummariesDatabaseManager.java` | `REQ-SET-004`, `REQ-SET-007`, `REQ-MAP-020`, `REQ-PER-001` | Schema is untouched; existing columns (`calories`, `description`, `laps`, `timeActive_s`, `timeTotal_s`) are utilized without modification. |

### Component Interface Integrity
* **Exporters (`TCXFileWriter.java`)**: `TCXFileWriter` already queries `LapsDatabaseManager` per lap (`LAP_NR`) and writes `<Extensions><TPX><Speed>` from `SensorType.SPEED_mps` and `<RunCadence>` from `SensorType.CADENCE`. The enhanced import directly satisfies the exporter's expectations, making import and export fully symmetrical.
* **UI & Period Summaries**: `WorkoutSummaries` row values (`calories`, `timeActive_s`, `description`) will now show rich data in the UI (Workout List, Detail Card, Period Summaries) instead of empty or 0-values.

---

## 4. Proposed Technical Changes (SWE.3)

### 4.1. `LapsDatabaseManager.java`
* Add overloaded method:
  ```java
  public void saveLap(long workoutId, long lapNr, @Nullable String timeStart, int lapTime, double lapDistance, double averageSpeed)
  ```
  Allowing explicit persistence of the lap's original ISO start timestamp into `Laps.TIME_START` rather than defaulting to current system time.

### 4.2. `LegacyImportEngine.kt`
* **Introduce Internal Data Models**:
  ```kotlin
  data class ParsedLap(
      val lapNr: Long,
      val startTime: String?,
      var totalTimeSeconds: Double = 0.0,
      var distanceMeters: Double = 0.0,
      var maxSpeed: Double? = null,
      var calories: Int? = null,
      var avgHeartRate: Int? = null,
      var maxHeartRate: Int? = null
  )
  ```
* **XML Pull Parsing Enhancements**:
  * Extract local tag name ignoring XML namespace prefixes (handling `<Speed>`, `<ns3:Speed>`, `<RunCadence>`, etc.).
  * Track `<Lap>` elements: record start time, total time seconds, distance, max speed, and calories. Increment `currentLapIndex` for subsequent trackpoints.
  * In `<Trackpoint>`:
    * Tag each sample with `SensorType.LAP_NR.name = currentLapIndex`.
    * Parse TPX `<Speed>` (m/s) -> populate `SensorType.SPEED_mps` and register in `foundSensors`.
    * Parse TPX `<RunCadence>` -> populate `SensorType.CADENCE` (if not already set by standard cadence) and register in `foundSensors`.
  * Outside `<Trackpoint>`:
    * Parse activity `<Notes>` -> store in workout `notes`.
* **Statistical Recalculation (`recalculateStats`)**:
  * Pass parsed laps, calories, and notes into `recalculateStats`.
  * Derive `activeTime` as sum of `totalTimeSeconds` across all parsed laps (fallback to trackpoint count if 0).
  * Derive `totalTime` as elapsed duration between first and last trackpoint timestamp (or active time if higher).
  * Persist `WorkoutSummaries.CALORIES`, `WorkoutSummaries.DESCRIPTION`, `WorkoutSummaries.LAPS`.
  * Iterate through all `ParsedLap` entries and call `lapsDb.saveLap` for each lap with its respective start time, duration, distance, and average speed.

---

## 5. Verification Plan (SWE.4 / SWE.5)
### Automated Unit Verification (`TcxHighFidelityImportTest.kt`)
* Construct a comprehensive multi-lap TCX fixture:
  * Lap 0 (1000m, 300s, 70 kcal) with trackpoints containing TPX `<Speed>` (3.33 m/s) and `<RunCadence>` (85 spm).
  * 300s pause gap.
  * Lap 1 (1500m, 450s, 110 kcal) with trackpoints containing TPX `<Speed>` (3.50 m/s) and `<RunCadence>` (88 spm).
  * Activity `<Notes>Interval session in the woods</Notes>`.
* Execute `importFromTcx()`.
* Validate:
  1. `LapsDatabaseManager` contains 2 records for the workout with exact split times, distances, and average speeds.
  2. Samples table contains `SPEED_mps`, `CADENCE`, and `LAP_NR` with values 0 and 1.
  3. `WorkoutSummaries` contains `TIME_ACTIVE_s` = 750, `TIME_TOTAL_s` = 1050 (including pause), `CALORIES` = 180, and `DESCRIPTION` = `"Interval session in the woods"`.
  4. Extrema calculations for speed and cadence reflect the instantaneous trackpoint values.
* Full regression pass: `./gradlew testDebugUnitTest`.
