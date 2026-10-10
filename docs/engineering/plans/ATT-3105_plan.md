# Stage 3: Implementation Plan - ATT-3105: Normalize Climb popup elevation profile X-axis to climb distance instead of full route distance

**Ticket**: [ATT-3105](https://atrainingtracker.atlassian.net/browse/ATT-3105)  
**Sub-task**: [ATT-3145](https://atrainingtracker.atlassian.net/browse/ATT-3145) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-2565](https://atrainingtracker.atlassian.net/browse/ATT-2565) (*Climbs: Detection, Live ClimbPro & Elevation Pacing*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `2026-41.8`  
**Requirement Mapping**: `REQ-UI-338` (*Climb Detail Elevation Profile Normalization to Local Climb Distance Domain*)  
**Test Mapping**: `TST-UI-298` (*Climb Detail Elevation Profile Local Distance Normalization & Scrubbing Synchronization Verification*)  
**Branch**: `feature/ATT-3105`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-10  

---

## 1. Problem Description & Background

During Sprint 2026-41.7 Joint Review on Google Pixel 10 (documented in evidence attachment `climb_elevation_profile_progress_bug.png`), inspecting an individual climb in the Climb detail popup (`ClimbDetailSheet.kt` hosting `ClimbOnMapScreen.kt`) revealed that the elevation profile chart displayed global parent route coordinates (e.g. 0 to 28.10 km) instead of local climb distance (e.g. 0 to 722 m).

For a 722 m climb beginning at km 27.38 of a 28.10 km route:
- The X-axis spans 0, 5, 10, 15, 20, 28.10 km.
- The climb profile is compressed into a narrow vertical sliver between 97.4% and 100% of the canvas width.
- Approximately 97% of the chart is left completely blank.

Forensic analysis confirmed that `Climb.pathPoints` retains the cumulative parent route distances, and `ClimbOnMapScreen` passes `climb.pathPoints` directly to `MapDetailLayout` as `activeScrubPath`. `ElevationProfile` computes chart domains from `0.0` to `pathPoints.last().distance`, squishing the profile.

This plan details the precise atomic steps to introduce `Climb.normalizedPathPoints`, pass it as `activeScrubPath` in `ClimbOnMapScreen.kt`, and provide comprehensive unit and contract tests verifying local distance normalization, scrubbing lookup synchronization, and header route offset preservation.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-UI-338` (*Climb Detail Elevation Profile Normalization to Local Climb Distance Domain*)
* **Test Mapping**: `TST-UI-298` (*Climb Detail Elevation Profile Local Distance Normalization & Scrubbing Synchronization Verification*)

---

## 3. System Invariants & Preserved Behavior

1. **Entity Immutability Invariant**: The stored `climb.pathPoints` in `Climb` and SQLite (`Climbs.db`) remains completely unmodified. `normalizedPathPoints` is a computed property.
2. **Coordinate & Elevation Integrity Invariant**: Latitude, longitude, altitude, timestamps, and sensor telemetry fields are strictly preserved during normalization.
3. **Full Chart Width Invariant**: In `ClimbOnMapScreen`, the climb elevation profile spans 100% of the chart width from base (0.0 m) to summit ($d_{\text{summit}}$).
4. **Header Route Offset Invariant**: `ClimbHeader` continues to render the global route offset (e.g. "Start at 27.4 km") using `climb.pathPoints.firstOrNull()?.distance`.
5. **Interactive Scrubbing Parity**: Scrubbing distance in $[0, d_{\text{summit}}]$ resolves to the accurate physical `PathPoint` via `TelemetryMetricUtils.findNearestPoint`, synchronizing the map marker position cleanly.
6. **Sub-Task Self-Sufficiency**: Sub-tasks transition autonomously to `Erledigt` upon passing automated Gate audits (`freigabe`).
7. **Parent Human Gate Invariance**: Final completion of parent ticket `ATT-3105` is reserved exclusively for the human user in `Final Review (Human)`.

---

## 4. Proposed Architectural Changes

### Step 1: `Climb.kt` - Add Computed Property `normalizedPathPoints`
Add the following property to `Climb` in `app/src/main/java/com/atrainingtracker/trainingtracker/climbs/Climb.kt`:
```kotlin
    /**
     * Returns a copy of [pathPoints] where distances are normalized to start at 0.0 meters.
     * Preserves latLng, altitude, and any associated telemetry intact.
     */
    val normalizedPathPoints: List<PathPoint>
        get() {
            if (pathPoints.isEmpty()) return emptyList()
            val startDist = pathPoints.first().distance
            if (startDist == 0.0) return pathPoints
            return pathPoints.map { it.copy(distance = (it.distance - startDist).coerceAtLeast(0.0)) }
        }
```

### Step 2: `ClimbOnMapScreen.kt` - Pass Normalized Path to `MapDetailLayout`
In `app/src/main/java/com/atrainingtracker/trainingtracker/ui/climbs/ClimbOnMapScreen.kt`:
```kotlin
    val normalizedClimbPath = remember(climb) {
        climb.normalizedPathPoints
    }

    MapDetailLayout(
        bSportType = bSportType,
        zoomFocus = if (climbBounds != null) MapZoomFocus.EXPLICIT_BOUNDS else MapZoomFocus.FIT_PRIMARY,
        initialBounds = climbBounds,
        activeScrubPath = normalizedClimbPath,
        useStatusBarsPadding = useStatusBarsPadding,
        showMap = showMap,
        onHeaderHeightMeasured = onHeaderHeightMeasured,
        header = { ... },
        mapContent = {
            climbs(listOf(climb))
            ...
        },
        modifier = modifier
    )
```

### Step 3: Unit Tests - `ClimbNormalizationTest.kt`
Create unit test suite in `app/src/test/java/com/atrainingtracker/trainingtracker/climbs/ClimbNormalizationTest.kt`:
- **`testNormalizedPathPoints_shiftsDistancesToBaseZero`**: Route distances [27380.0, 27700.0, 28102.0] become [0.0, 320.0, 722.0]. LatLng and altitude remain untouched.
- **`testNormalizedPathPoints_emptyListReturnsEmpty`**: Empty points list returns empty list.
- **`testNormalizedPathPoints_alreadyZeroBaseReturnsSameList`**: Points starting at 0.0 return the identical list instance without reallocation.
- **`testScrubbingLookup_findNearestPointResolvesCorrectCoordinates`**: `TelemetryMetricUtils.findNearestPoint(climb.normalizedPathPoints, 320.0)` matches the midpoint correctly.

### Step 4: Contract Tests - `ClimbDetailSheetContractTest.kt`
Extend contract tests in `app/src/test/java/com/atrainingtracker/trainingtracker/ui/climbs/ClimbDetailSheetContractTest.kt`:
- Verify `ClimbOnMapScreen.kt` passes `normalizedClimbPath` / `normalizedPathPoints` as `activeScrubPath` to `MapDetailLayout`.
- Verify `ClimbHeader.kt` preserves `startDistMeters = climb.pathPoints.firstOrNull()?.distance` and `routes_climb_start_at`.

---

## 5. Verification Plan

1. **Targeted Unit Tests**:
   - Run `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.climbs.ClimbNormalizationTest"`
   - Run `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.climbs.ClimbDetailSheetContractTest"`
2. **Clean-Room Full Suite Regression**:
   - Run `./gradlew testDebugUnitTest` verifying 100% test pass rate.
3. **ASPICE Living Documentation Synchronization**:
   - Update `REQ-UI-338` in `docs/requirements.md` from `Draft` to `Verified`.
   - Update `TST-UI-298` in `docs/tests.md` from `Draft` to `Verified`.
   - Run `python3 tools/verify_requirement_governance.py --base-ref sprint/2026-41.8` to confirm governance compliance.
