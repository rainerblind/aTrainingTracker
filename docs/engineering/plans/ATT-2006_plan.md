# Stage 3: Implementation Plan - ATT-2006: [Aftermath/Telemetry] Display Heart Rate Graph When HR Telemetry Exists Even Without GPS Track

**Ticket**: [ATT-2006](https://rainerblind.atlassian.net/browse/ATT-2006)  
**Sub-task**: [ATT-2019](https://rainerblind.atlassian.net/browse/ATT-2019) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-111](https://rainerblind.atlassian.net/browse/ATT-111) (*Compact Post-Workout Visual Analytics & Graphs*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.11`  
**Requirement Mapping**: `REQ-UI-235`  
**Test Mapping**: `TST-UI-194`  
**Branch**: `feature/ATT-2006`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-02  

---

## 1. Problem Description & Background

Athletes frequently record training sessions without GPS coordinates (e.g. indoor treadmill running, stationary cycling trainer sessions, indoor rowing, or outdoor sessions where GPS lock was not acquired). While spatial map coordinates and elevation profiles do not exist for these activities, they contain rich sensor telemetry—specifically continuous Heart Rate and Cycling Power streams recorded at 1 Hz in SQLite `WorkoutSamples.db`.

In the current architecture:
1. `WorkoutRepository.getWorkoutTrackPoints` discards all sample rows where `latitude` or `longitude` columns are null or unindexed (`latIdx == -1 || lonIdx == -1 || cursor.isNull(latIdx) || cursor.isNull(lonIdx)`), returning an empty point list.
2. `TrackOnMapAftermathViewModel` populates `tracks` strictly from GPS fixes, leaving `tracks` empty for trackless workouts.
3. `TrackOnMapScreen` resolves `activeScrubPath = tracks.find { it.type == TrackType.BEST }?.path ?: tracks.firstOrNull()?.path`, which evaluates to `null`.
4. `MapDetailLayout` guards all telemetry graph rendering inside `activeScrubPath?.let { path -> ... }` and `hasTelemetryGraphs = showZoomControls && activeScrubPath != null && ...`, completely suppressing the continuous Heart Rate curve despite valid HR samples in SQLite.
5. Simultaneously, `ATrainingTrackerMap` renders an empty Google Map centered at (0.0, 0.0) in the Atlantic Ocean off the coast of Africa.
6. When scrubbing on trackless data, `TelemetryMetricGraph` dispatches `nearest?.distance` (which is 0.0), pinning the cursor to 0:00.

`REQ-UI-235` decouples sensor telemetry extraction and ingestion from GPS coordinates, renders continuous telemetry curves along the Time domain across workout duration, enables synchronized time-domain scrubbing with instantaneous metric readouts, and cleanly collapses the Google Map container when no GPS coordinates exist.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-UI-235` (*Aftermath/Telemetry: Continuous Sensor Telemetry Graph Ingestion, Time-Domain Scrubbing, and Map Collapse for Trackless Workouts*)
  - Clause 1: Decoupled Telemetry Extraction (`WorkoutRepository.kt`).
  - Clause 2: ViewModel Telemetry Ingestion (`TrackOnMapAftermathViewModel.kt`).
  - Clause 3: TrackOnMapScreen Scrubber & Layout Wiring (`TrackOnMapScreen.kt`).
  - Clause 4: Map Collapse & Scrollable Full-Screen Layout (`MapDetailLayout.kt`).
  - Clause 5: Synchronized Time-Domain Scrubbing (`TelemetryMetricGraph.kt`).
  - Clause 6: Preservation of Core Invariants (GPS workouts, 9-language localization parity, SQLite single-thread confinement).
* **Test Mapping**: `TST-UI-194` (*Aftermath/Telemetry: Trackless Continuous Telemetry Graph Ingestion, Time-Domain Scrubbing, and Map Collapse Verification*)
  - `[TST-UI-194.1]`: `WorkoutRepositoryTelemetryTest.kt` (Extraction, data integrity, decimation).
  - `[TST-UI-194.2]`: `TrackOnMapViewModelTelemetryTest.kt` (State exposure, ingestion when tracks empty).
  - `[TST-UI-194.3]`: `TracklessAftermathVisualContractTest.kt` (GPS detection, map collapse, vertical scrolling, header readout).
  - `[TST-UI-194.4]`: `TelemetryMetricGraphTracklessScrubbingTest.kt` (Time-domain dispatch, cursor positioning).
  - `[TST-UI-194.5]`: Clean-room full test suite regression (`./gradlew testDebugUnitTest`).

---

## 3. System Invariants & Preserved Behavior

1. **Zero Unintended Regressions**: Standard GPS workouts continue to render map, elevation profile, and telemetry graphs with spatial scrubbing (`REQ-UI-201`, `REQ-UI-206`, `REQ-UI-233`).
2. **Padding & Alignment Invariant**: Horizontal padding parity (`startPaddingPx = 50.dp, endPaddingPx = 25.dp`) remains strictly preserved across `ElevationProfile` and `TelemetryMetricGraph`.
3. **Thread Safety & Dispatcher Affinity**: SQLite database extraction in `WorkoutRepository` remains isolated to `Dispatchers.IO`. UI mutations remain on `Dispatchers.Main`.
4. **9-Language Localization Parity**: Zero hardcoded strings. All UI text tokens exist across all 9 supported application locales.
5. **Subtask Self-Sufficiency**: Subtasks transition directly to `Erledigt` upon passing Gate audit via `freigabe`.
6. **Parent Human Gate Invariance**: Terminal completion of parent tickets remains reserved for the human user in `Final Review (Human)`. Never advance parent to `Erledigt`.
7. **Programmatic Pre-Check Gate**: Gate 3 verification via `tools/jira_util.py check-gate ATT-2019` must pass before any production code edits.

---

## 4. Proposed Architectural Changes (SWE.2)

### Component 1: `WorkoutRepository.kt` (`com.atrainingtracker.trainingtracker.ui.aftermath`)
* **Add Method**:
  ```kotlin
  suspend fun getWorkoutTelemetryPoints(workoutId: Long): List<PathPoint> = withContext(Dispatchers.IO) {
      val points = ArrayList<PathPoint>()
      val db = workoutSamplesDatabaseManager.readableDatabase ?: return@withContext emptyList()
      val tableName = WorkoutSamplesDatabaseManager.getSamplesTableName(workoutId)

      // Query all recorded samples for the workout
      db.query(tableName, null, null, null, null, null, null).use { cursor ->
          val timeActiveIdx = cursor.getColumnIndex(SensorType.TIME_ACTIVE.name)
          val timeTotalIdx = cursor.getColumnIndex(SensorType.TIME_TOTAL.name)
          val timeIdx = cursor.getColumnIndex(WorkoutSamplesDatabaseManager.WorkoutSamplesDbHelper.TIME)
          val hrIdx = cursor.getColumnIndex(SensorType.HR.name)
          val powerIdx = cursor.getColumnIndex(SensorType.POWER.name)
          val speedIdx = cursor.getColumnIndex(SensorType.SPEED_mps.name)

          var initialEpochSec: Long? = null
          var sampleIndex = 0L

          while (cursor.moveToNext()) {
              val timeSec = when {
                  timeActiveIdx != -1 && !cursor.isNull(timeActiveIdx) -> cursor.getLong(timeActiveIdx)
                  timeTotalIdx != -1 && !cursor.isNull(timeTotalIdx) -> cursor.getLong(timeTotalIdx)
                  timeIdx != -1 && !cursor.isNull(timeIdx) -> {
                      val (offset, anchor) = parseTimestampOffset(cursor.getString(timeIdx), initialEpochSec)
                      initialEpochSec = anchor
                      offset ?: sampleIndex
                  }
                  else -> sampleIndex
              }

              val hr = if (hrIdx != -1 && !cursor.isNull(hrIdx)) cursor.getInt(hrIdx) else null
              val power = if (powerIdx != -1 && !cursor.isNull(powerIdx)) cursor.getInt(powerIdx) else null
              val speed = if (speedIdx != -1 && !cursor.isNull(speedIdx)) cursor.getDouble(speedIdx) else null

              if (hr != null || power != null || speed != null) {
                  points.add(
                      PathPoint(
                          distance = 0.0,
                          latLng = LatLng(0.0, 0.0),
                          altitude = 0.0,
                          timeSec = timeSec,
                          hr = hr,
                          power = power,
                          speedMps = speed,
                          slope = null
                      )
                  )
              }
              sampleIndex++
          }
      }

      // Decimate if count exceeds 800 to maintain 120fps UI performance
      if (points.size > 800) {
          val step = kotlin.math.ceil(points.size / 800.0).toInt()
          points.filterIndexed { index, _ ->
              index == 0 || index == points.lastIndex || index % step == 0
          }
      } else {
          points
      }
  }
  ```

### Component 2: `TrackOnMapAftermathViewModel.kt` (`com.atrainingtracker.trainingtracker.ui.map`)
* **State Updates**:
  ```kotlin
  data class AftermathMapUIState(
      val tracks: List<MapTrack> = emptyList(),
      val availableTrackTypes: Set<TrackType> = setOf(TrackType.BEST),
      val segments: List<MapSegment> = emptyList(),
      val routes: List<MapRoute> = emptyList(),
      val markers: List<LocationMarker> = emptyList(),
      val bSportType: BSportType = BSportType.UNKNOWN,
      val zoomFocus: MapZoomFocus = MapZoomFocus.FIT_PRIMARY,
      val hrZoneDistribution: ZoneDistributionData? = null,
      val powerZoneDistribution: ZoneDistributionData? = null,
      val telemetryPath: List<PathPoint> = emptyList() // REQ-UI-235
  )
  ```
* **Phase 1 Reset**:
  Clear `telemetryPath = emptyList()` upon loading a new workout.
* **Phase 4 Telemetry Fallback**:
  ```kotlin
  if (fullTracks.isNotEmpty()) {
      withContext(Dispatchers.Main) {
          _uiState.value = _uiState.value.copy(
              tracks = fullTracks,
              availableTrackTypes = fullTracks.map { it.type }.toSet()
          )
      }
  } else {
      val telemetryPoints = workoutRepository.getWorkoutTelemetryPoints(workoutId)
      withContext(Dispatchers.Main) {
          _uiState.value = _uiState.value.copy(
              telemetryPath = telemetryPoints
          )
      }
  }
  ```

### Component 3: `TrackOnMapScreen.kt` (`com.atrainingtracker.trainingtracker.ui.aftermath`)
* **Parameter & State Additions**:
  - Accept `telemetryPath: List<PathPoint> = emptyList()`.
  - Calculate `val hasGpsTrack = remember(tracks) { tracks.any { it.path.isNotEmpty() && it.latLngs.any { p -> p.latitude != 0.0 || p.longitude != 0.0 } } }`.
  - Bind `val activeScrubPath = remember(hasGpsTrack, tracks, telemetryPath) { if (hasGpsTrack) (bestTrack?.path) else telemetryPath.ifEmpty { null } }`.
  - Pass `showMap = showMap && hasGpsTrack` and `showElevationProfile = hasGpsTrack && (workoutData.minAltitude != null || (activeScrubPath?.any { it.altitude != 0.0 } == true))` to `MapDetailLayout`.

### Component 4: `MapDetailLayout.kt` (`com.atrainingtracker.trainingtracker.ui.map`)
* **Layout Adaptation**:
  - Recognize trackless state: `val isTrackless = (activeScrubPath?.lastOrNull()?.distance ?: 0.0) == 0.0 && (activeScrubPath?.lastOrNull()?.timeSec ?: 0) > 0`.
  - If `isTrackless`, enforce `ProfileXAxisDomain.TIME` for telemetry graphs and calculate `totalSpan = (activeScrubPath?.lastOrNull()?.timeSec ?: 0).toDouble()`.
  - Outer column: apply `if (showMap || hasScrollableContent) Modifier.fillMaxSize() else Modifier.wrapContentHeight()`.
  - In `else` branch (when `!showMap`): when `hasScrollableContent == true`, apply `Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())` to `lowerColumn`.
  - Header metric readout during scrubbing: when `selectedDistance != null`, compute instantaneous HR/Power and zone tag, displaying next to heading title (e.g. `148 bpm • Z3`).

### Component 5: `TelemetryMetricGraph.kt` (`com.atrainingtracker.trainingtracker.ui.map`)
* **Scrubbing & Cursor Adaptation**:
  - In touch drag and tap handlers, when `isTimeDomain`:
    ```kotlin
    val isTrackless = (pathPoints.lastOrNull()?.distance ?: 0.0) == 0.0 && (pathPoints.lastOrNull()?.timeSec ?: 0) > 0
    if (isTrackless) {
        currentOnDistanceSelectedState(nearest?.timeSec?.toDouble())
    } else {
        currentOnDistanceSelectedState(nearest?.distance)
    }
    ```
  - In cursor drawing, when `isTrackless`:
    Evaluate `cursorDistSpan = currentDistance` directly against `0.0..totalSpan` and `startDist..(startDist + visibleSpan)`.
    Resolve `nearestPoint = pathPoints.minByOrNull { abs(it.timeSec - currentDistance) }`.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Programmatic Gate 3 Pre-Check
* Verify Gate 3 status of `ATT-2019` via `python3 tools/jira_util.py check-gate ATT-2019`.

### Step 2: Decoupled Telemetry Extraction (`WorkoutRepository.kt`)
* Implement `getWorkoutTelemetryPoints(workoutId: Long): List<PathPoint>`.
* Add unit tests in `WorkoutRepositoryTelemetryTest.kt`.
* Verify with:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutRepositoryTelemetryTest"
  ```

### Step 3: ViewModel Ingestion (`TrackOnMapAftermathViewModel.kt`)
* Add `telemetryPath: List<PathPoint>` to `AftermathMapUIState`.
* Populate `telemetryPath` in `loadAftermathData()` when `fullTracks` is empty.
* Add unit tests in `TrackOnMapViewModelTelemetryTest.kt`.
* Verify with:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.map.TrackOnMapViewModelTelemetryTest"
  ```

### Step 4: Screen Wiring & Map Suppression (`TrackOnMapScreen.kt`)
* Add `telemetryPath` parameter to `TrackOnMapScreen.kt`.
* Wire `WorkoutSummariesTabbedScreen.kt` and `WorkoutSummariesListFragment.kt` to forward `aftermathUIState.telemetryPath`.
* Evaluate `hasGpsTrack`, conditional `showMap`, and fallback `activeScrubPath`.

### Step 5: Layout Container Scrolling & Header Readouts (`MapDetailLayout.kt`)
* Update `MapDetailLayout.kt` to enforce `ProfileXAxisDomain.TIME` for trackless workouts.
* Apply `verticalScroll` and `weight(1f)` to `lowerColumn` when `showMap == false && hasScrollableContent == true`.
* Render instantaneous metric and zone badge in graph section headers during active scrubbing.

### Step 6: Scrubbing Cursor in Time Domain (`TelemetryMetricGraph.kt`)
* Update `TelemetryMetricGraph.kt` gesture dispatch and cursor evaluation for trackless time domain.
* Add unit tests in `TelemetryMetricGraphTracklessScrubbingTest.kt`.
* Verify with:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.map.TelemetryMetricGraphTracklessScrubbingTest"
  ```

### Step 7: Visual & Contract Test Suite
* Author `TracklessAftermathVisualContractTest.kt` verifying map collapse, graph presence, and header readouts.
* Verify all targeted test suites:
  ```bash
  ./gradlew testDebugUnitTest \
    --tests "com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutRepositoryTelemetryTest" \
    --tests "com.atrainingtracker.trainingtracker.ui.map.TrackOnMapViewModelTelemetryTest" \
    --tests "com.atrainingtracker.trainingtracker.ui.map.TelemetryMetricGraphTracklessScrubbingTest" \
    --tests "com.atrainingtracker.trainingtracker.ui.map.TracklessAftermathVisualContractTest"
  ```

---

## 6. Verification & Rollback Plan

* **Verification**: Targeted unit tests during construction, followed by clean-room full test suite regression (`./gradlew testDebugUnitTest`) in Stage 5.
* **Rollback**: Branch isolation on `feature/ATT-2006` allows complete rollback via `git reset --hard origin/sprint/2026-40.11` without impacting integration branches.
