# Stage 3: Implementation Plan - ATT-4017: Missing altitude is stored as 0 m and corrupts profiles and extrema

**Ticket**: [ATT-4017](https://rainerblind.atlassian.net/browse/ATT-4017) | **Sub-task**: Pending Submission (`[Impl-Plan]`)  
**Parent Epic**: [ATT-111](https://rainerblind.atlassian.net/browse/ATT-111) (*Aftermath: Compact Post-Workout Visual Analytics & Graphs*)  
**Target Release**: `V4.9.40` | **Active Sprint**: `2026-43.5` | **Branch**: `feature/ATT-4017`  
**Requirement Mapping**: `REQ-DAT-035` (amending `REQ-DAT-009`, `REQ-UI-332`, `REQ-UI-126`) | **Test Mapping**: `TST-DAT-030`  
**Author**: AI Agent 1 (Implementer - `subagent_pro`) | **Date**: 2026-10-21  

---

## 1. Architecture & Invariant Compliance

* **SWE.2 Architecture (`REQ-DAT-035`)**:
  1. *Nullable Altitude DTO & Safe Extraction (`REQ-DAT-035.1`)*: Declare `PathPoint.altitude: Double? = null`. Map null SQLite cursor altitude to `null` in `WorkoutRepository` (`getWorkoutTrackPoints`, `getWorkoutTelemetryPoints`). Retain sea-level samples ($0.0\text{ m}$) in telemetry inclusion filter: `(hr != null || power != null || speed != null || alt != null)`.
  2. *Gap-Resilient Crop Downsampling (`REQ-DAT-035.2`)*: In `cropWorkout`, omit `ALTITUDE_STREAM` if all retained points lack altitude. For sporadic gaps, forward-fill / interpolate from adjacent valid samples rather than injecting `0.0`.
  3. *Extrema Reconciliation Protection (`REQ-DAT-035.3`)*: In `WorkoutDataMapper.reconcileAltitudeExtrema`, filter out isolated 0.0 m gap drops before overriding valid positive `recordedMin`.
  4. *Aftermath & Elevation Rendering Parity (`REQ-DAT-035.4`)*: In `TrackOnMapScreen.kt`, check `it.altitude != null` (supporting sea-level 0.0 m). In `ElevationSmoothingMath` and `calculateElevationBounds`, ignore/interpolate null altitudes without collapsing baseline to 0 m.
* **Core Invariants & Preserved Behavior (`REQ-DAT-035.5`)**:
  1. *Zero SQLite Schema Migrations*: `SensorType.ALTITUDE` column is already nullable.
  2. *Backward Compatibility*: Default parameters (`val altitude: Double? = null`) maintain compatibility for route and segment callers.
  3. *Sea-Level Validity*: Workouts at sea level ($0.0\text{ m}$) display elevation profiles and valid extrema.
* **UI Consistency (Rule 23)**: No UI layout changes; restores existing elevation cards for sea-level sessions.

---

## 2. Atomic Implementation Steps

* **Step 1 — Nullable `PathPoint.altitude` & Repository Mapping (`REQ-DAT-035.1`)**:
  * In `MapModels.kt`: `data class PathPoint(..., val altitude: Double? = null, ...)`. Update `toMapTrack()` to `alts.getOrNull(index)`.
  * In `WorkoutRepository.kt`: map cursor altitude to `null` if `cursor.isNull(altIdx)` in `getWorkoutTrackPoints` and `getWorkoutTelemetryPoints`. Update telemetry filter to `alt != null`.
* **Step 2 — Gap-Resilient Crop Stream Downsampling (`REQ-DAT-035.2`)**:
  * In `WorkoutRepository.cropWorkout`: omit `encodedAltitudes` if no altitudes exist. Forward-fill missing samples with `lastKnownAlt` instead of injecting `0.0`.
* **Step 3 — Extrema Reconciliation Protection Against Gap Corruption (`REQ-DAT-035.3`)**:
  * In `WorkoutDataMapper.reconcileAltitudeExtrema`: filter out isolated 0.0 gap drops when `recordedMin > 15.0` and stream has non-zero points.
* **Step 4 — Aftermath Screen & Elevation Smoothing Gap Safety (`REQ-DAT-035.4`)**:
  * In `TrackOnMapScreen.kt`: update `hasTracklessAltitude` and `hasAltitudeData` to check `it.altitude != null`.
  * In `ElevationSmoothingMath.kt` & `ElevationProfile.kt`: ignore null/NaN altitudes in Gaussian regression sums and envelope min/max.
  * In `MapDetailLayout.kt`, `ClimbOnMapScreen.kt`, `TrackingElevationProfileResolver.kt`: safe-call `it.altitude ?: 0.0`.
* **Step 5 — Unit & Contract Test Suite Updates (`TST-DAT-030.1`–`TST-DAT-030.6`)**:
  * Update `WorkoutRepositoryTelemetryTest.kt` for null altitude mapping and sea-level retention (`TST-DAT-030.1`).
  * Add unit tests in `WorkoutRepositoryCropTest.kt` / `WorkoutDataMapperAltitudeTest.kt` for crop downsampling and extrema protection (`TST-DAT-030.2`, `TST-DAT-030.3`).
  * Update `TracklessAftermathVisualContractTest.kt`, `MapModelsTest.kt`, and `ElevationProfileBoundsTest.kt` for nullable altitude contracts (`TST-DAT-030.4`, `TST-DAT-030.5`).

---

## 3. File-Level Change Matrix

| File Path | Role | Nature of Modifications |
| :--- | :--- | :--- |
| `app/.../ui/map/MapModels.kt` | Production | Declare `PathPoint.altitude: Double? = null`; `toMapTrack()` uses `alts.getOrNull(index)`. |
| `app/.../ui/aftermath/WorkoutRepository.kt` | Production | Null-safe cursor extraction in track/telemetry points; gap-resilient crop downsampling. |
| `app/.../ui/aftermath/WorkoutDataMapper.kt` | Production | Guard `reconcileAltitudeExtrema` against unphysical 0.0 gap drops. |
| `app/.../ui/aftermath/TrackOnMapScreen.kt` | Production | Check `it.altitude != null` for `hasTracklessAltitude` and `hasAltitudeData`. |
| `app/.../ui/map/TrackOnMapAftermathViewModel.kt` | Production | `fastPath` preserves nullable altitude via `alts.getOrNull(index)`. |
| `app/.../ui/map/ElevationProfile.kt`, `ElevationSmoothingMath.kt` | Production | Null-safe bounds, smoothing regression, and scrubbing interpolation. |
| `app/.../ui/map/MapDetailLayout.kt`, `ClimbOnMapScreen.kt` | Production | Null-safe altitude interpolation in scrubbing badge and climb metrics. |
| `app/.../tracking/tracking/TrackingElevationProfileResolver.kt` | Production | Safe-call `it.altitude != null` and interpolate with null fallback. |
| `app/.../ui/map/MapModelsTest.kt`, `TracklessAftermathVisualContractTest.kt` | Test | Update padded stream and visibility contract checks for `it.altitude != null`. |
| `app/.../ui/aftermath/WorkoutRepositoryTelemetryTest.kt`, `WorkoutDataMapperAltitudeTest.kt` | Test | Add tests for null cursor extraction, sea-level retention, and extrema protection. |
| `app/.../ui/map/ElevationProfileBoundsTest.kt` | Test | Add tests for bounds sanitization on tracks with altitude gaps. |

---

## 4. Test Execution Strategy

* **Tier 1 — Per-Ticket Verification (Stages 4 & 5)**:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.map.*" \
    --tests "com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutRepository*" \
    --tests "com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutDataMapper*" \
    --tests "com.atrainingtracker.trainingtracker.ui.aftermath.TrackOnMapScreen*"
  ```
* **Governance Verification**: `python3 tools/verify_requirement_governance.py`

---

## 5. Rollback & Risk Mitigation

* **Risk Level**: **MEDIUM**. Cross-cutting `PathPoint.altitude` nullability; mitigated by default arguments (`Double? = null`) and targeted contract tests.
* **Rollback Plan**: Zero SQLite database schema changes; fully revertible via `git checkout` / `git revert` on `feature/ATT-4017`.
