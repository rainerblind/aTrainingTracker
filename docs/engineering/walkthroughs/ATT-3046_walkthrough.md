# Stage 5 Walkthrough: ATT-3046 - Display elevation profile in workout details for trackless workouts with altitude data

**Ticket**: [ATT-3046](https://atrainingtracker.atlassian.net/browse/ATT-3046)  
**Sub-task**: [ATT-3071](https://atrainingtracker.atlassian.net/browse/ATT-3071) (`[Test]`)  
**Parent Epic**: [ATT-2006](https://atrainingtracker.atlassian.net/browse/ATT-2006) (*Indoor / Trackless Workouts & Telemetry Enhancement*)  
**Active Sprint**: `Sprint 2026-41.7`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-10  

---

## 1. Executive Summary & Verification Outcome

All requirements of `REQ-UI-332` and test specifications under `TST-UI-292` have been fully constructed, verified, and audited.
The workout details aftermath screen (`TrackOnMapScreen.kt`) and the underlying workout repository (`WorkoutRepository.kt`) have been updated to cleanly display elevation profile charts plotted along the Time domain for trackless workouts that contain barometric or sensor altitude data:

1. **Altitude Telemetry Ingestion (`WorkoutRepository.kt`)**:
   - `getWorkoutTelemetryPoints(workoutId: Long)` now extracts `SensorType.ALTITUDE.name` from `WorkoutSamples.db`.
   - Ingested samples correctly populate `PathPoint.altitude = alt`.
   - The telemetry inclusion filter accepts samples with `alt != 0.0`, ensuring sessions recording barometric altitude without heart rate, power, or speed are preserved in `telemetryPath`.
2. **Decoupled Visibility Gating (`TrackOnMapScreen.kt`)**:
   - `TrackOnMapScreen.kt` decouples `showElevationProfile` from `hasGpsTrack`:
     `val hasTracklessAltitude = !hasGpsTrack && (activeScrubPath?.any { it.altitude != 0.0 } == true)`
     `val hasAltitudeData = workoutData.minAltitude != null || (activeScrubPath?.any { it.altitude != 0.0 } == true)`
     `val showElevationProfile = (hasGpsTrack || hasTracklessAltitude) && activeDetailPrefs.showElevationProfile && isElevationPostMap && hasAltitudeData`
   - For trackless sessions with altitude data, `showElevationProfile` resolves to `true`, enabling `MapDetailLayout` to render the elevation profile chart plotted along the Time domain (`ProfileXAxisDomain.TIME`).
   - For trackless sessions with no altitude data (all 0.0 / null), `showElevationProfile` resolves to `false`, suppressing empty chart artifacts and blank space.
3. **Active Scrub Path Resolution**:
   - `activeScrubPath` resolves to `telemetryPath.ifEmpty { tracks.firstOrNull { it.path.isNotEmpty() }?.path }` when `hasGpsTrack == false`, capturing altitude from both telemetry paths and trackless map tracks.
4. **Preservation of System Invariants**:
   - Standard outdoor GPS workouts continue to render distance-domain elevation profiles with spatial scrubbing.
   - User detail preferences (`activeDetailPrefs.showElevationProfile`) and dynamic section ordering (`isElevationPostMap`) remain respected.

---

## 2. Test Execution & Regression Results

### 2.1 Targeted Unit & Visual Contract Tests
- Command: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutRepositoryTelemetryTest" --tests "com.atrainingtracker.trainingtracker.ui.map.TracklessAftermathVisualContractTest" --tests "com.atrainingtracker.trainingtracker.ui.aftermath.TrackOnMapScreenDetailPreferencesContractTest"`
- Result: **All tests passed cleanly in 6s**.
- Verified:
  - `WorkoutRepositoryTelemetryTest.getWorkoutTelemetryPoints extracts altitude samples`: verifies `SensorType.ALTITUDE` samples are mapped to `PathPoint.altitude`.
  - `WorkoutRepositoryTelemetryTest.getWorkoutTelemetryPoints retains altitude-only samples`: verifies samples containing only altitude are retained in `telemetryPath`.
  - `TracklessAftermathVisualContractTest.elevation profile visibility is decoupled from gps track when altitude exists`: verifies `showElevationProfile` evaluates to true for trackless workouts with altitude and false when altitude is zero/absent.
  - `TrackOnMapScreenDetailPreferencesContractTest.trackless workout respects detail preferences for elevation profile`: verifies detail preference toggle and section ordering continue to gate visibility.

### 2.2 Clean-Room Full Test Suite Regression
- Command: `./gradlew testDebugUnitTest`
- Result: **BUILD SUCCESSFUL in 2m 14s**
- Outcome: **100% pass rate**, **0 failures**, **0 errors**, **0 regressions across entire test suite**.

---

## 3. Living Documentation & Governance Synchronization

- `docs/requirements.md`: `REQ-UI-332` updated to `Verified`.
- `docs/tests.md`: `TST-UI-292` updated to `Verified`.
- `tools/verify_requirement_governance.py --base-ref sprint/2026-41.7`: Verified clean pass (code 0).

---

## 4. ASPICE Traceability Matrix

| Requirement | Test Specification | Implementation Files | Test Files | Status |
|---|---|---|---|---|
| `REQ-UI-332` | `TST-UI-292` | `WorkoutRepository.kt`, `TrackOnMapScreen.kt` | `WorkoutRepositoryTelemetryTest.kt`, `TracklessAftermathVisualContractTest.kt`, `TrackOnMapScreenDetailPreferencesContractTest.kt` | **Verified** |
