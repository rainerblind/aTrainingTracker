# Stage 1 Problem Domain & Root Cause Analysis: ATT-3046 - Display elevation profile in workout details for trackless workouts with altitude data

**Ticket**: [ATT-3046](https://atrainingtracker.atlassian.net/browse/ATT-3046)  
**Sub-task**: [ATT-3067](https://atrainingtracker.atlassian.net/browse/ATT-3067) (`[Analysis]`)  
**Parent Epic**: [ATT-68](https://atrainingtracker.atlassian.net/browse/ATT-68) (*Improve WorkoutSummaries*)  
**Target Release**: Unassigned (`None`) per Rule 19 (Lösungsversion assigned only when finished)  
**Active Sprint**: `Sprint 2026-41.7`  
**Branch**: `feature/ATT-3046`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-10  

---

## 1. Problem Domain & Background

When an athlete opens the workout aftermath detail screen (`TrackOnMapScreen.kt`) for an activity recorded without GPS location coordinates (e.g. indoor workouts with barometric altimeter data, stationary smart bike sessions with virtual elevation profiles, or imported `.tcx` / `.fit` files with altitude streams but no GPS fixes), the elevation profile graph is completely suppressed.

This creates an inconsistent user experience:
1. The **Altitude Statistics Table** (rendering minimum, average, and maximum altitude in `WorkoutExtrema`) is displayed with valid non-null elevation numbers.
2. Directly below this table, an empty blank space exists where the elevation profile graph is expected to be displayed.
3. Athletes are unable to visualize their elevation profile over time or scrub through altitude changes during the workout session.

---

## 2. Requirement Archaeology & Chesterton's Fence (`REQ-PRO-022`)

1. **Original Historical Context**:
   - In Sprint 2026-40.10 (`ATT-2006` / `REQ-UI-235`), support for trackless workouts was introduced in `TrackOnMapScreen.kt` and `MapDetailLayout.kt` to collapse the Google Map (preventing Null Island Atlantic Ocean artifacts) and render continuous Heart Rate and Cycling Power telemetry graphs along the Time domain.
   - When formulating `showElevationProfile` in `TrackOnMapScreen.kt` (`commit b9d73a723c`):
     ```kotlin
     showMap = showMap && hasGpsTrack,
     showElevationProfile = hasGpsTrack && (workoutData.minAltitude != null || (activeScrubPath?.any { it.altitude != 0.0 } == true))
     ```
     Elevation visibility was bundled with `hasGpsTrack`.

2. **Why It Was Formulated That Way ("Why Was the Fence Built?")**:
   - At the time of `ATT-2006`, trackless workouts were viewed almost exclusively as indoor treadmill runs or stationary trainer rides without barometric altitude sensors. Consequently, elevation profiles were assumed to be relevant only for GPS-tracked outdoor activities.
   - Later, in Sprint 2026-40.15 (`ATT-2016` / `REQ-UI-241`) and Sprint 2026-40.16 (`ATT-2311`), `ProfileDomainMath` and `MapDetailLayout` were systematically upgraded with time-domain elevation capabilities:
     ```kotlin
     val isTrackless = ProfileDomainMath.isTracklessWorkout(activeScrubPath)
     val isElevationTimeDomain = ProfileDomainMath.isEffectiveTimeDomain(tuningConfig.elevationXAxisDomain, activeScrubPath)
     ```
   - While the chart rendering engine (`ElevationProfile.kt` / `MapDetailLayout.kt`) became fully capable of plotting altitude over time (`ProfileXAxisDomain.TIME`), `TrackOnMapScreen.kt` was never updated, remaining strictly hard-gated behind `hasGpsTrack`.

3. **Invariants to Preserve**:
   - **Absence of Elevation Data**: Workouts with neither GPS tracks nor non-zero altitude data must NEVER display an empty/flat elevation card.
   - **GPS Workouts Unaltered**: Standard outdoor GPS workouts must continue to render elevation profiles against distance with full spatial map scrubbing.
   - **Detail Preference & Dynamic Order Integrity**: User preferences (`activeDetailPrefs.showElevationProfile`) and custom section ordering (`isElevationPostMap`) must continue to gate elevation display.
   - **100% Clean-Room Regression Pass Rate**: All unit and contract tests across `:app` must pass without regressions.

---

## 3. Forensic Root Cause Analysis (RCA)

Our code audit identified two distinct defects preventing elevation profile presentation for trackless workouts:

### Defect 1: Hard-Gated Visibility in `TrackOnMapScreen.kt`
In `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/TrackOnMapScreen.kt` (line 167):
```kotlin
showElevationProfile = hasGpsTrack && activeDetailPrefs.showElevationProfile && isElevationPostMap && (workoutData.minAltitude != null || (activeScrubPath?.any { it.altitude != 0.0 } == true)),
```
Because of `hasGpsTrack &&`, whenever `hasGpsTrack == false`, `showElevationProfile` unconditionally resolves to `false`, even when:
- `activeDetailPrefs.showElevationProfile == true`
- `isElevationPostMap == true`
- `workoutData.minAltitude != null`
- `activeScrubPath?.any { it.altitude != 0.0 } == true`

### Defect 2: Missing Altitude Extraction in `WorkoutRepository.getWorkoutTelemetryPoints`
In `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/WorkoutRepository.kt` (lines 413–453):
```kotlin
val distIdx = cursor.getColumnIndex(SensorType.DISTANCE_m.name)
val timeActiveIdx = cursor.getColumnIndex(SensorType.TIME_ACTIVE.name)
val timeTotalIdx = cursor.getColumnIndex(SensorType.TIME_TOTAL.name)
val timeIdx = cursor.getColumnIndex(WorkoutSamplesDatabaseManager.WorkoutSamplesDbHelper.TIME)
val hrIdx = cursor.getColumnIndex(SensorType.HR.name)
val powerIdx = cursor.getColumnIndex(SensorType.POWER.name)
val speedIdx = cursor.getColumnIndex(SensorType.SPEED_mps.name)
// Missing: SensorType.ALTITUDE.name!
...
if (hr != null || power != null || speed != null) {
    points.add(
        PathPoint(
            distance = dist,
            latLng = LatLng(0.0, 0.0),
            altitude = 0.0, // Hardcoded to 0.0!
            timeSec = timeSec,
            hr = hr,
            power = power,
            speedMps = speed,
            slope = null
        )
    )
}
```
For trackless workouts, `TrackOnMapAftermathViewModel` populates `telemetryPath` from `WorkoutRepository.getWorkoutTelemetryPoints(workoutId)`. Because `SensorType.ALTITUDE` was omitted from column indexing and `altitude` was hardcoded to `0.0`, any altitude samples recorded in `WorkoutSamples.db` were discarded, resulting in `activeScrubPath?.any { it.altitude != 0.0 } == false`!

---

## 4. Proposed Solution & Architecture

### Step 1: Update Telemetry Ingestion in `WorkoutRepository.kt`
1. Query `val altIdx = cursor.getColumnIndex(SensorType.ALTITUDE.name)`.
2. Extract `val alt = if (altIdx != -1 && !cursor.isNull(altIdx)) cursor.getDouble(altIdx) else 0.0`.
3. Ingest points when altitude is present:
   `if (hr != null || power != null || speed != null || alt != 0.0)`
4. Set `altitude = alt` on the constructed `PathPoint`.

### Step 2: Fallback in `TrackOnMapScreen.kt` for `activeScrubPath`
Ensure `activeScrubPath` resolves correctly when `hasGpsTrack == false`:
```kotlin
val activeScrubPath = remember(hasGpsTrack, bestTrack, tracks, telemetryPath) {
    if (hasGpsTrack) {
        bestTrack?.path ?: tracks.firstOrNull()?.path
    } else {
        telemetryPath.ifEmpty { tracks.firstOrNull { it.path.isNotEmpty() }?.path }
    }
}
```

### Step 3: Decouple `showElevationProfile` in `TrackOnMapScreen.kt`
Allow elevation profile rendering when valid altitude telemetry is present, regardless of GPS track availability:
```kotlin
val hasTracklessAltitude = !hasGpsTrack && (activeScrubPath?.any { it.altitude != 0.0 } == true)
val hasAltitudeData = workoutData.minAltitude != null || (activeScrubPath?.any { it.altitude != 0.0 } == true)

showElevationProfile = (hasGpsTrack || hasTracklessAltitude) &&
    activeDetailPrefs.showElevationProfile &&
    isElevationPostMap &&
    hasAltitudeData
```
- For GPS workouts (`hasGpsTrack == true`): Renders when user preferences and section orders permit and altitude data exists.
- For trackless workouts (`hasGpsTrack == false`): Renders when `activeScrubPath` contains non-zero altitude points to plot along the Time domain.
- For zero-altitude workouts: Remains cleanly suppressed.

### Step 4: Contract & Unit Test Synchronization
1. Update `WorkoutRepositoryTelemetryTest.kt` to verify that `SensorType.ALTITUDE` samples are extracted with valid altitude values.
2. Update `TracklessAftermathVisualContractTest.kt` and `TrackOnMapScreenDetailPreferencesContractTest.kt` string assertions to reflect the decoupled `showElevationProfile` gating.
3. Add a dedicated contract test for trackless elevation profile rendering in `TracklessAftermathVisualContractTest.kt`.

---

## 5. Scope & Boundary (ATT-1250 Grounding)

- **In Scope**:
  - `TrackOnMapScreen.kt`: Decoupling `showElevationProfile` from `hasGpsTrack` when `hasTracklessAltitude` is true.
  - `WorkoutRepository.kt`: Reading `SensorType.ALTITUDE` and populating `PathPoint.altitude` in `getWorkoutTelemetryPoints`.
  - Targeted unit and contract tests in `WorkoutRepositoryTelemetryTest.kt`, `TracklessAftermathVisualContractTest.kt`, and `TrackOnMapScreenDetailPreferencesContractTest.kt`.
- **Out of Scope**:
  - Modifications to `MapDetailLayout.kt` or `ElevationProfile.kt` (already fully support time-domain rendering).
  - Database schema changes to `WorkoutSamples.db` or `WorkoutSummaries.db` (existing schema already includes `ALTITUDE`).
  - Unrelated aftermath screen refactoring or styling alterations.

---

## 6. Risk Rating & Mitigation

| Risk | Impact | Probability | Mitigation Strategy |
| :--- | :--- | :--- | :--- |
| Empty elevation graph displayed for indoor workouts without altitude | Low | Low | Gated strictly by `activeScrubPath?.any { it.altitude != 0.0 } == true` for trackless sessions. |
| Regression on outdoor GPS workouts | High | Low | Invariant preservation ensures `hasGpsTrack` continues to branch to standard distance-domain elevation. |
| Memory/Performance churn during decimation | Medium | Low | Uniform stride decimation (max 800 points) preserved for all telemetry paths. |

**Overall Risk Rating**: **LOW** (Well-isolated, leverages existing time-domain infrastructure in `MapDetailLayout`).

---

## 7. Recommendation

**RECOMMEND PASS**: Advance to Stage 2 (Requirement & Test Specification) to formalize the requirement amendment (`REQ-UI-332` amending `REQ-UI-235`), define Given-When-Then acceptance criteria, and formulate test specifications (`TST-UI-292`).
