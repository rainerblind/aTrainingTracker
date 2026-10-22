# Stage 1 Analysis: ATT-4017 - [Bug] [Medium] Missing altitude is stored as 0 m and corrupts profiles and extrema

**Ticket**: [ATT-4017](https://rainerblind.atlassian.net/browse/ATT-4017)  
**Parent Epic**: [ATT-111](https://rainerblind.atlassian.net/browse/ATT-111) (*Aftermath: Compact Post-Workout Visual Analytics & Graphs*)  
**Target Release**: `V4.9.40` | **Active Sprint**: `2026-43.5` | **Branch**: `feature/ATT-4017`  
**Author**: AI Agent 1 (Implementer - `subagent_pro`) | **Date**: 2026-10-21  

---

## 1. Problem Domain & Root Cause Analysis

* **Defect 1: Sentinel `0.0` Conflates Missing Altitude with Sea Level (`WorkoutRepository.kt`)**:
  * In `getWorkoutTrackPoints` (L423) and `getWorkoutTelemetryPoints` (L501), null cursor values map to `0.0`: `val alt = if (altIdx != -1 && !cursor.isNull(altIdx)) cursor.getDouble(altIdx) else 0.0`.
  * In L505, inclusion check `if (hr != null || power != null || speed != null || alt != 0.0)` treats `0.0` as "no altitude". Legitimate sea-level workouts (0.0 m) are rejected, while missing samples are injected as 0.0 m.
* **Defect 2: Crop Stream Downsampling Injects 0.0 m Canyons (`WorkoutRepository.kt:1220`)**:
  * During workout crop, L1220 downsamples altitudes into `ALTITUDE_STREAM` using `sampledAltitudes.add(s.altitude ?: 0.0)`. Missing altitude samples inject literal `0.0`, creating artificial plunging canyons on mountain profiles.
* **Defect 3: Authoritative Extrema Reconciliation Persists False `minAltitude = 0.0` (`WorkoutDataMapper.kt:1043`)**:
  * `reconcileAltitudeExtrema()` compares stored `recordedMin` (e.g. 450 m) to `streamMin`. Because crop injected `0.0`, `recordedMin > streamMin` evaluates to true, falsely diagnosing corruption and writing `minAltitude = 0.0` to SQLite `extrema_values`.
* **Defect 4: Elevation Profile Smoothing & Bounds Distortion (`ElevationProfile.kt`, `ElevationSmoothingMath.kt`)**:
  * Gaussian kernel regression in `smoothAltitudes` averages valid terrain (e.g. 500 m) with `0.0`, dragging adjacent points down. `calculateElevationBounds` sets `streamMin = 0.0`, collapsing vertical chart scale.
* **Defect 5: Decoupled Visibility Sentinel (`TrackOnMapScreen.kt:175-176`, `MapModels.kt:412`)**:
  * `hasTracklessAltitude = !hasGpsTrack && (activeScrubPath?.any { it.altitude != 0.0 } == true)` uses `!= 0.0` as an existence proxy, hiding profile cards for true sea-level sessions.

---

## 2. Call Site & Blast Radius Audit

| Component | Target File | Nature of Change & Impact |
| :--- | :--- | :--- |
| **Track Point DTO** | `MapModels.kt` | Make `PathPoint.altitude` nullable (`Double? = null`). Decouple `toMapTrack()` fallback to `alts.getOrNull(index)`. |
| **Workout Repository** | `WorkoutRepository.kt` | Map null cursor to `null` in `getWorkoutTrackPoints` and `getWorkoutTelemetryPoints`. Interpolate/skip nulls in `cropWorkout` `ALTITUDE_STREAM`. |
| **Extrema Reconciler** | `WorkoutDataMapper.kt` | Guard `reconcileAltitudeExtrema` against unphysical 0.0 gaps in streams when recorded extrema are positive. |
| **Aftermath Screen & ViewModels** | `TrackOnMapScreen.kt`, `TrackOnMapAftermathViewModel.kt` | Replace `it.altitude != 0.0` with `it.altitude != null` (supporting sea-level 0.0 m). Update `fastPath` to preserve `Double?`. |
| **Profile Canvas & Math** | `ElevationProfile.kt`, `ElevationSmoothingMath.kt` | Ignore/interpolate `null` altitudes in Gaussian smoothing and `calculateElevationBounds`. |
| **Unit & Contract Tests** | `WorkoutRepositoryTelemetryTest.kt`, `MapModelsTest.kt`, `TracklessAftermathVisualContractTest.kt` | Verify null-gap preservation, sea-level retention, crop extrema invariance, and contract string parity. |

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  1. Null-safe altitude mapping in `WorkoutRepository.kt` without `0.0` fallback.
  2. Make `PathPoint.altitude` nullable (`Double? = null`) to cleanly separate missing altitude from sea level.
  3. Prevent `reconcileAltitudeExtrema` from corrupting `minAltitude` to 0.0 on workouts with altitude gaps.
  4. Eliminate 0 m canyon plunge artifacts in `ElevationProfile` and `cropWorkout` streams.
  5. Add unit tests for altitude gaps, sea-level workouts, and crop extrema preservation.
* **Out-of-Scope Non-Goals**:
  1. No SQLite database schema migrations (`SensorType.ALTITUDE` column is already nullable).
  2. No changes to hardware sensor listeners or barometric fusion algorithms in `BANALService`.
  3. No changes to profile chart gesture disambiguation or zoom controls.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit (REQ-PRO-022)

* **Original Requirement IDs & Targets**:
  * `REQ-DAT-009` (*Authoritative Altitude Extrema Reconciliation*, `ATT-508`): Created to heal pre-ATT-499 GPS startup spikes by comparing extrema against the stream envelope. Root reason: Assumed stream was continuous and gap-free; when crop injected `0.0`, `streamMin` falsely became 0. Preservation: Core self-healing logic remains, but ignores null/gap artifacts.
  * `REQ-UI-332` (*Trackless Elevation Profile Telemetry Ingestion*, `ATT-3046`): Used `it.altitude != 0.0` because `PathPoint.altitude` was non-null `Double`. Root reason: `0.0` was the only sentinel available. Preservation: Replacing `!= 0.0` with `!= null` restores the true invariant: displaying profiles whenever altitude telemetry exists (including sea level).
  * `REQ-UI-126` (*Elevation Bounds Sanitization*, `ATT-508`): Ensures profile baseline envelopes valid terrain; filtering nulls guarantees correct bounds.

---

## 5. Architectural Strategy & System Invariants

* **Nullable DTO Architecture (`PathPoint.altitude: Double? = null`)**:
  * `PathPoint` represents missing altitude as `null`. Existing call sites without altitude default cleanly to `null`.
* **Gap-Resilient Crop Stream Generation (`WorkoutRepository.kt`)**:
  * In `cropWorkout`, if all retained points lack altitude, `encodedAltitudes` is omitted (`null`). For sporadic gaps, interpolate linearly from adjacent valid points or omit nulls rather than injecting `0.0`.
* **Sea-Level & Extrema Integrity Guard (`WorkoutDataMapper.kt`)**:
  * `reconcileAltitudeExtrema` checks that candidate `streamMin` represents a valid physical baseline and not an isolated gap artifact before overriding positive `recordedMin`.
* **System Invariants**: Zero DB schema migration; 100% backward compatibility for route and segment callers; full support for genuine 0.0 m sea-level workouts; clean-room unit test pass rate.
* **Risk Rating**: **MEDIUM** — Cross-cutting changes to `PathPoint` DTO and aftermath elevation rendering; mitigated by default null arguments and comprehensive existing test suite.
