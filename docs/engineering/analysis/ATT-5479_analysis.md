# Stage 1 Analysis: ATT-5479 - Reject (0.0, 0.0) Null-Island EXIF GPS Coordinates and Exclude Invalid Coordinates from Map Markers & Bounds

**Ticket**: [ATT-5479](https://rainerblind.atlassian.net/browse/ATT-5479)  
**Parent Epic**: None (*Standalone Bug*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-45.7`  
**Branch**: `feature/ATT-5479`  
**Author**: AI Agent 1 (`subagent_pro`)  
**Date**: 2026-11-06  

---

## 1. Problem Statement & Motivation
During Sprint Review 2026-45.6, attaching a photo without valid GPS coordinates to Workout 457 (*"Zum Bäcker #47"*) caused the Workout Detail map camera to zoom out to a 5,000 km continental view covering Europe down to Null Island (Gulf of Guinea, `0.0° N, 0.0° E`). At this zoom level, the ~2 km workout track in Germany (`48.65° N, 9.05° E`) collapses into a sub-pixel artifact hidden under the Start pin.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
1. **ExifInterface Zero-Rational Quirk**: `ExifInterface.getLatLong(latLong)` returns `true` when EXIF GPS rational tags exist as `0/1, 0/1`, populating `[0.0f, 0.0f]`.
2. **Track Interpolation Bypassed**: `WorkoutPhotoManager.extractExifMetadata` (lines 93–97) accepts `(0.0, 0.0)` as non-null, bypassing the fallback timestamp-based track interpolation (`if (latitude == null || longitude == null)` line 241) and persisting `(0.0, 0.0)` into `workout_photos`.
3. **Map Bounds Pollution**: `WorkoutRepository.getWorkoutMarkers` (lines 981–994) adds `LocationMarker(LatLng(0.0, 0.0))` without coordinate validation. `MapBoundsController` (lines 114–148) includes all markers in `LatLngBounds.Builder`, blowing out camera bounds to the equator.
4. **Independent Peer Review & Verification**:
   * Verified live call sites: `WorkoutPhotoManager.kt:94`, `WorkoutRepository.kt:984`, `MapBehaviors.kt:126`, `SpatialRegionEngine.kt:213-217`.
   * Confirmed Workout 457 on-device data has `(0.0, 0.0)` in SQLite `workout_photos`. Self-healing during photo loading can re-interpolate actual coordinates from `WorkoutSamples.db` using `taken_at_ms`.

---

## 3. User Scope Grounding (ATT-1250) & Complexity Guardrail
* **In-Scope Goals**:
  * Implement pure validator `GeoUtils.isValidGpsCoordinate(lat, lng)` (`abs(lat) >= 1e-5 || abs(lng) >= 1e-5` and within `[-90..90, -180..180]`).
  * Guard `WorkoutPhotoManager.extractExifMetadata` to return `null, null` on invalid GPS coordinates, allowing `addPhoto()` fallback track interpolation to run.
  * Guard `WorkoutRepository.getWorkoutMarkers` against invalid coordinates.
  * Guard `MapBoundsController` against invalid coordinates in `LatLngBounds.Builder`.
  * Self-heal existing `(0.0, 0.0)` rows in `workout_photos` (Workout 457) by re-interpolating via `taken_at_ms` or resetting to `null`, supported by `updatePhotoCoordinates` in `WorkoutSummariesDatabaseManager`.
* **Out-of-Scope Non-Goals**: Modifying camera capture APIs, UI photo strip layout, or map styling.
* **Complexity Audit**: 2 SP (Standard multi-component bugfix spanning EXIF parsing, repository markers, camera bounds, and self-healing DB update).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-DAT-038.2` (`WorkoutPhotoManager.kt`, `WorkoutSummariesDatabaseManager.java`) and `REQ-UI-448.3` (`WorkoutRepository.kt`, `MapBehaviors.kt`).
* **Historical Origin & Commit Trace**: Commit `5c9d9653` (`ATT-43`, Sprint `2026-45.6`).
* **Root Reason for Existing Formulation**: Assumed `ExifInterface.getLatLong()` only returns `true` for legitimate non-zero GPS fixes and that `photo.latitude != null` guarantees valid coordinates.
* **Preservation of Core Invariants**: Genuine coordinates remain unchanged; photos without GPS taken during workouts correctly acquire track coordinates; camera framing remains tightly focused on actual workout trajectories.

---

## 5. Architectural Strategy & High-Level Solution
* **Shared Validator**: Add `GeoUtils.isValidGpsCoordinate(lat: Double?, lng: Double?): Boolean`.
* **Defense-in-Depth Pipeline**:
  1. *Ingestion*: `WorkoutPhotoManager.extractExifMetadata` sets `lat/lng = null` if `!isValidGpsCoordinate`, triggering `interpolateCoordinatesAtTimestamp`.
  2. *Self-Healing*: `WorkoutPhotoManager.getPhotosForWorkout` / `WorkoutRepository.getWorkoutPhotos` detects `!isValidGpsCoordinate`, re-interpolates against `WorkoutSamples.db` using `taken_at_ms`, and persists repaired coordinates via `WorkoutSummariesDatabaseManager.updatePhotoCoordinates`.
  3. *Presentation*: `WorkoutRepository.getWorkoutMarkers` rejects invalid photo coordinates.
  4. *Camera Bounds*: `MapBoundsController` guards all markers and tracks with `isValidGpsCoordinate`.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**:
  1. Map camera bounds strictly encompass valid workout trackpoints and legitimate markers.
  2. Existing photos without GPS remain safe and accessible (thumbnails, viewer, share) with `null` GPS.
  3. Symmetrical, non-destructive database updates via parameterized queries.
* **Risk Rating**: **LOW** (Defensive coordinate validation and self-healing; isolated from background recording engine).
