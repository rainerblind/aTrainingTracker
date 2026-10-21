# Stage 2: Requirement & Test Specification - ATT-4017: [Bug] [Medium] Missing altitude is stored as 0 m and corrupts profiles and extrema

**Ticket**: [ATT-4017](https://rainerblind.atlassian.net/browse/ATT-4017) | **Sub-task**: Pending Submission (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-111](https://rainerblind.atlassian.net/browse/ATT-111) (*Aftermath: Compact Post-Workout Visual Analytics & Graphs*)  
**Target Release**: `V4.9.40` | **Active Sprint**: `2026-43.5` | **Branch**: `feature/ATT-4017`  
**Requirement Mapping**: `REQ-DAT-035` (amending `REQ-DAT-009`, `REQ-UI-332`, `REQ-UI-126`) | **Test Spec ID**: `TST-DAT-030`  
**Author**: AI Agent 1 (Implementer - `subagent_flash`) | **Date**: 2026-10-21  

---

## 1. Requirement Traceability Matrix

| Requirement | Description | Test Case | Target Artifact | Status |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-DAT-035.1` | Nullable `PathPoint.altitude: Double? = null` & repository null cursor mapping | `TST-DAT-030.1` | `MapModels.kt`, `WorkoutRepository.kt` | Planned |
| `REQ-DAT-035.2` | Gap-resilient `cropWorkout` downsampling (no 0.0 m plunge injection) | `TST-DAT-030.2` | `WorkoutRepository.kt` | Planned |
| `REQ-DAT-035.3` | Safeguarded `reconcileAltitudeExtrema` against stream zero-gap corruption | `TST-DAT-030.3` | `WorkoutDataMapper.kt` | Planned |
| `REQ-DAT-035.4` | Aftermath visibility (`it.altitude != null`) & elevation smoothing/bounds | `TST-DAT-030.4`, `TST-DAT-030.5` | `TrackOnMapScreen.kt`, `ElevationSmoothingMath.kt` | Planned |
| `REQ-DAT-035.5` | System invariants (zero DB schema migrations, sea-level 0.0m retention) | `TST-DAT-030.6` | `./gradlew testDebugUnitTest` | Planned |

---

## 2. Formal Requirement Specifications

* **Nullable DTO & Repository Extraction (`REQ-DAT-035.1`)**:
  * `PathPoint.altitude` SHALL be nullable (`val altitude: Double? = null`).
  * In `WorkoutRepository` (`getWorkoutTrackPoints` and `getWorkoutTelemetryPoints`), null cursor altitude SHALL resolve to `null`. Telemetry inclusion filter SHALL check `(hr != null || power != null || speed != null || alt != null)`.
  * In `MapModels.toMapTrack()`, `altitude` SHALL resolve via `alts.getOrNull(index)` preserving `null`.
* **Gap-Resilient Crop Downsampling (`REQ-DAT-035.2`)**:
  * In `WorkoutRepository.cropWorkout`, downsampling for `ALTITUDE_STREAM` SHALL NOT inject `0.0` for missing samples (`s.altitude ?: 0.0`). Missing samples SHALL be omitted or linearly interpolated; if all points lack altitude, `encodedAltitudes` SHALL be `null`.
* **Extrema Reconciliation Protection (`REQ-DAT-035.3`)**:
  * In `WorkoutDataMapper.reconcileAltitudeExtrema`, the system SHALL NOT overwrite positive `recordedMin` with `0.0` when `streamMin == 0.0` is an unphysical single-point drop or gap artifact.
* **Aftermath & Elevation Rendering Parity (`REQ-DAT-035.4`)**:
  * In `TrackOnMapScreen.kt`, `hasTracklessAltitude` and `hasAltitudeData` SHALL check `it.altitude != null` (allowing true sea-level `0.0 m` sessions to display elevation cards).
  * `ElevationSmoothingMath.smoothAltitudes` and `calculateElevationBounds` SHALL filter/interpolate missing `null`/`NaN` altitudes without collapsing chart baseline to 0 m.
* **System Invariants (`REQ-DAT-035.5`)**:
  * Zero SQLite database migrations (`SensorType.ALTITUDE` column is already nullable).
  * Full backward compatibility for callers of `PathPoint(distance, latLng)`.

### Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Net-new `REQ-DAT-035` (amending `REQ-DAT-009`, `REQ-UI-332`, `REQ-UI-126`). Targets `MapModels.kt`, `WorkoutRepository.kt`, `WorkoutDataMapper.kt`, `TrackOnMapScreen.kt`, `TrackOnMapAftermathViewModel.kt`, `ElevationProfile.kt`, `ElevationSmoothingMath.kt`.
* **Historical Origin & Commit Trace**: `ATT-4017` (Sprint 2026-43.5); `REQ-DAT-009` (`ATT-508`, commits `b4f69aef`, `f3122721`); `REQ-UI-332` (`ATT-3046`, commit `1e6a14d5`); `PathPoint` non-nullable altitude (`ATT-1391`, commit `54d58852`).
* **Root Reason for Existing Formulation**: Non-nullable `PathPoint.altitude: Double` forced all callers to provide a default primitive, using `0.0` as the missing sentinel. This conflated missing altitude telemetry with legitimate sea-level measurements ($0.0\text{ m}$), causing `TrackOnMapScreen` to check `!= 0.0` as an existence proxy, crop downsampling to inject 0.0 m canyons, and `reconcileAltitudeExtrema()` to overwrite positive minimum altitudes.
* **Preservation of Core Invariants**: GPS startup spike reconciliation (`REQ-DAT-009`), time-domain trackless elevation profiles (`REQ-UI-332`), zero SQLite schema migrations, and 100% test pass rate are strictly preserved.

---

## 3. Formal Test Case Specifications (Given / When / Then)

* **`TST-DAT-030.1` — Repository Nullable Altitude Extraction & Telemetry Filter**:
  * *Given* cursor with null altitude in `getWorkoutTrackPoints` or `getWorkoutTelemetryPoints`, *When* queried, *Then* `PathPoint.altitude` evaluates to `null`. Legitimate sea-level samples (`0.0 m`) without HR/speed/power are retained in `getWorkoutTelemetryPoints`.
* **`TST-DAT-030.2` — Crop Downsampling & Canyon Plunge Prevention**:
  * *Given* retained samples containing missing altitudes, *When* `cropWorkout` downsamples altitudes into `ALTITUDE_STREAM`, *Then* no 0.0 m plunge canyon points are injected. If all points lack altitude, `encodedAltitudes` is `null`.
* **`TST-DAT-030.3` — Extrema Reconciliation Guard Against Stream Gap Corruption**:
  * *Given* workout with `recordedMin = 450.0` and stream containing missing-altitude artifacts (`streamMin == 0.0`), *When* `reconcileAltitudeExtrema` runs, *Then* `minAltitude` is NOT corrupted to `0.0 m`. Genuine sea-level sessions reconcile to `0.0 m`.
* **`TST-DAT-030.4` — Trackless Aftermath Elevation Profile Visibility**:
  * *Given* a trackless workout with genuine sea-level points (`altitude = 0.0`), *When* `TrackOnMapScreen` evaluates visibility, *Then* `hasTracklessAltitude` evaluates to `true` and the elevation card renders. When all altitudes are `null`, it evaluates to `false`.
* **`TST-DAT-030.5` — Elevation Smoothing & Bounds Gap Handling**:
  * *Given* path points containing `null`/`NaN` altitudes, *When* `smoothAltitudes` and `calculateElevationBounds` execute, *Then* smoothing ignores null points without collapsing baseline to 0 m.

---

## 4. Edge Case & Regression Matrix

| Edge Case / Invariant | Precondition | Trigger / Input | Expected Result |
| :--- | :--- | :--- | :--- |
| **Legitimate Sea-Level Session** | Outdoor or trackless workout at 0.0 m | Elevation profile rendered | Profile card displays valid 0.0 m baseline; not hidden by false `!= 0.0` sentinel. |
| **Full Altitude Stream Absence** | Indoor treadmill session with no altitude sensor | Workout cropped via `cropWorkout` | `encodedAltitudes` remains `null`; no synthetic 0.0 m stream generated. |
| **Mountain GPS Startup Spike** | Pre-ATT-499 recording with startup spike -63 m | Workout loaded via `fromCursor` | `reconcileAltitudeExtrema` heals corrupted spike to stream min per `REQ-DAT-009`. |

---

## 5. Localization & Resource Parity Matrix

* **Resource Impact**: Zero new string keys required. Existing aftermath elevation labels (`elevation_chart_title`, `ascent`, `descent`, `min`, `max`) exist with 100% parity across all 9 canonical locales (`values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`).
* **Regression Test**: Run `TranslationParityTest` to confirm 0 missing entries and 100% format specifier parity.
