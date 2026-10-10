# Stage 3 Architecture & Implementation Plan: ATT-3046 - Display elevation profile in workout details for trackless workouts with altitude data

**Ticket**: [ATT-3046](https://atrainingtracker.atlassian.net/browse/ATT-3046)  
**Sub-task**: [ATT-3069](https://atrainingtracker.atlassian.net/browse/ATT-3069) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-68](https://atrainingtracker.atlassian.net/browse/ATT-68) (*Improve WorkoutSummaries*)  
**Target Release**: Unassigned (`None`) per Rule 19 (Lösungsversion assigned only when finished)  
**Active Sprint**: `Sprint 2026-41.7`  
**Branch**: `feature/ATT-3046`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-10  

---

## 1. Architectural Overview (SWE.2)

This plan implements `REQ-UI-332` to decouple elevation profile visibility from GPS track presence in the workout aftermath screen (`TrackOnMapScreen.kt`) and ingest recorded altitude telemetry into `PathPoint` objects in `WorkoutRepository.kt`.

### Layering & Component Architecture
```
┌────────────────────────────────────────────────────────┐
│ UI Layer: TrackOnMapScreen.kt                         │
│ • hasTracklessAltitude = !hasGpsTrack && hasAltitude   │
│ • showElevationProfile = (hasGpsTrack ||               │
│     hasTracklessAltitude) && prefs && isElevationPost  │
│ • activeScrubPath = telemetryPath.ifEmpty { tracks }   │
└───────────────────────────┬────────────────────────────┘
                            │ forwards activeScrubPath & showElevationProfile
                            ▼
┌────────────────────────────────────────────────────────┐
│ Presentation & Layout: MapDetailLayout.kt              │
│ • isTrackless = isTracklessWorkout(activeScrubPath)   │
│ • isElevationTimeDomain = true (Time Domain X-Axis)    │
│ • Renders ElevationProfile over [0, totalDurationSec]  │
└────────────────────────────────────────────────────────┘
                            ▲
                            │ supplies telemetryPath
┌────────────────────────────────────────────────────────┐
│ Repository Layer: WorkoutRepository.kt                 │
│ • getWorkoutTelemetryPoints(workoutId: Long)           │
│ • Queries SensorType.ALTITUDE.name from samples table  │
│ • Populates PathPoint(altitude = alt)                  │
│ • Filters where (hr != null || power != null ||        │
│                  speed != null || alt != 0.0)          │
└───────────────────────────┬────────────────────────────┘
                            │ queries SQLite table
                            ▼
┌────────────────────────────────────────────────────────┐
│ Database Layer: WorkoutSamples.db                      │
│ • Table: samples_<baseFileName>                        │
│ • Columns: TIME_ACTIVE, ALTITUDE, HR, POWER, SPEED     │
└────────────────────────────────────────────────────────┘
```

---

## 2. UI Consistency & Design Guidelines Audit (Rule 23)

1. **Closest Existing Reference Screen**:
   - `TrackOnMapScreen.kt` for outdoor GPS workouts.
2. **Reused Components**:
   - `ElevationProfile.kt` via `MapDetailLayout.kt`.
   - `ScrubbingTelemetryBadge.kt` for instantaneous scrub telemetry readouts.
3. **Theme & Tokens**:
   - No new color tokens or shapes introduced. All graph fills, outlines, and axes use existing `MaterialTheme.colorScheme` tokens defined in `ElevationProfile`.
4. **Time Domain Consistency**:
   - Trackless elevation profile uses `ProfileDomainMath.isEffectiveTimeDomain`, which renders elapsed duration identical to continuous heart rate and power graphs (`REQ-UI-235`).

---

## 3. Atomic Implementation Steps

### Step 1: Ingest Altitude in `WorkoutRepository.kt`
- **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/WorkoutRepository.kt`
- **Changes**:
  1. Add `val altIdx = cursor.getColumnIndex(SensorType.ALTITUDE.name)`.
  2. Read `val alt = if (altIdx != -1 && !cursor.isNull(altIdx)) cursor.getDouble(altIdx) else 0.0`.
  3. Include in row filter:
     `if (hr != null || power != null || speed != null || alt != 0.0)`
  4. Assign `altitude = alt` in `PathPoint(...)` instantiation.
- **Verification**: `WorkoutRepositoryTelemetryTest.kt` passes.

### Step 2: Decouple `showElevationProfile` in `TrackOnMapScreen.kt`
- **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/TrackOnMapScreen.kt`
- **Changes**:
  1. Update `activeScrubPath`:
     ```kotlin
     val activeScrubPath = remember(hasGpsTrack, bestTrack, tracks, telemetryPath) {
         if (hasGpsTrack) {
             bestTrack?.path ?: tracks.firstOrNull()?.path
         } else {
             telemetryPath.ifEmpty { tracks.firstOrNull { it.path.isNotEmpty() }?.path }
         }
     }
     ```
  2. Compute decoupled elevation gating:
     ```kotlin
     val hasTracklessAltitude = !hasGpsTrack && (activeScrubPath?.any { it.altitude != 0.0 } == true)
     val hasAltitudeData = workoutData.minAltitude != null || (activeScrubPath?.any { it.altitude != 0.0 } == true)

     showElevationProfile = (hasGpsTrack || hasTracklessAltitude) &&
         activeDetailPrefs.showElevationProfile &&
         isElevationPostMap &&
         hasAltitudeData,
     ```
- **Verification**: Contract tests compile and pass.

### Step 3: Update Unit & Contract Tests
- **Files**:
  - `app/src/test/java/com/atrainingtracker/trainingtracker/ui/aftermath/WorkoutRepositoryTelemetryTest.kt`
    - Add `testExtractTelemetryPoints_populatesAltitude_whenPresentInSamplesDb`.
    - Add `testExtractTelemetryPoints_ingestsPointsWithOnlyAltitude_whenHrPowerSpeedAbsent`.
  - `app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/TracklessAftermathVisualContractTest.kt`
    - Update contract assertion: `content.contains("showElevationProfile = (hasGpsTrack || hasTracklessAltitude) &&")`.
    - Add test asserting trackless altitude elevation profile activation.
  - `app/src/test/java/com/atrainingtracker/trainingtracker/ui/aftermath/TrackOnMapScreenDetailPreferencesContractTest.kt`
    - Update contract assertion: `content.contains("activeDetailPrefs.showElevationProfile && isElevationPostMap && hasAltitudeData")`.

### Step 4: Clean-Room Full Suite Regression Execution
- **Command**: `./gradlew testDebugUnitTest`
- **Assertion**: 100% pass rate, 0 failures, 0 regressions.

---

## 4. Invariant Protection & Verification Checklist

- [x] **Zero GPS Outdoor Regressions**: When `hasGpsTrack == true`, `showElevationProfile` evaluates identical to previous behavior.
- [x] **Zero-Altitude Protection**: When neither GPS nor non-zero altitude telemetry exists, `hasAltitudeData == false`, keeping the elevation profile hidden.
- [x] **Preference & Section Integrity**: `activeDetailPrefs.showElevationProfile` and `isElevationPostMap` continue to govern presentation.
- [x] **KDoc / JavaDoc Compliance**: Methods adhere to standard documentation invariants.
- [x] **Gate 3 Verification Guard**: Gate 3 programmatic pre-check (`tools/jira_util.py check-gate ATT-3069`) must exit 0 before Stage 4 code modifications.
