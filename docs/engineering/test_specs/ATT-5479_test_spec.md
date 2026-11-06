# Stage 2: Requirement & Test Specification - ATT-5479: Reject (0.0, 0.0) Null-Island EXIF GPS Coordinates

**Ticket**: [ATT-5479](https://rainerblind.atlassian.net/browse/ATT-5479)  
**Sub-task**: `[Req & Test Spec]`  
**Parent Epic**: None (*Standalone Bug*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.7` | **Branch**: `feature/ATT-5479`  
**Requirement Mapping**: `REQ-UI-447`, `REQ-UI-448` | **Test Spec ID**: `TST-UI-447`, `TST-UI-448`  
**Author**: AI Agent 1 (`subagent_flash`) | **Date**: 2026-11-06  

---

## 1. Requirement Specification (`REQ-UI-447`, `REQ-UI-448`)

### 1.1 Problem Statement & Rationale
Zeroed EXIF GPS tags (`0/1, 0/1`) yield `(0.0, 0.0)` in `ExifInterface.getLatLong()`. `WorkoutPhotoManager.extractExifMetadata` accepts these as valid non-null coordinates, bypassing fallback trackpoint interpolation. Persisted `(0.0, 0.0)` rows then generate map markers in `WorkoutRepository.getWorkoutMarkers` and blow out camera bounds in `MapBoundsController`, collapsing local workout tracks into invisible sub-pixel artifacts.

### 1.2 Functional & Architectural Requirements
1. **Coordinate Validation Predicate (`REQ-UI-448.3`, `GeoUtils`)**: The system SHALL provide `GeoUtils.isValidGpsCoordinate(lat: Double?, lng: Double?): Boolean`, returning `true` iff both coordinates are finite, `lat in -90.0..90.0`, `lng in -180.0..180.0`, and strictly excluding Null Island (`abs(lat) >= 1e-5 || abs(lng) >= 1e-5`).
2. **EXIF Null-Island Rejection & Interpolation Trigger (`REQ-UI-448.4`)**: `WorkoutPhotoManager.extractExifMetadata` SHALL return `lat = null, lng = null` when EXIF coordinates fail `isValidGpsCoordinate`, enabling timestamp-based GPS track interpolation (`interpolateCoordinatesAtTimestamp`) to execute.
3. **Database Self-Healing (`REQ-UI-448.4`)**: When loading photos for a workout in `WorkoutPhotoManager` / `WorkoutRepository`, existing database records with invalid or `(0.0, 0.0)` coordinates SHALL be self-healed: the system SHALL re-interpolate valid coordinates from `WorkoutSamples.db` using `taken_at_ms` (or reset to `null`) and persist via `WorkoutSummariesDatabaseManager.updatePhotoCoordinates`.
4. **Marker & Bounds Sanitization (`REQ-UI-447.2`, `REQ-UI-448.3`)**: `WorkoutRepository.getWorkoutMarkers`, `MapBoundsController` (`MapBehaviors.kt`), and `PhotoTrackOverlayComposer` SHALL reject invalid coordinates (`!isValidGpsCoordinate`), ensuring map markers and camera bounds remain strictly focused on authentic workout routes.

### 1.3 Acceptance Criteria (Given-When-Then)
* **AC-1 (EXIF Rejection & Track Interpolation)**: *Given* an attached photo with zeroed EXIF GPS tags (`0.0, 0.0`), *When* parsed by `WorkoutPhotoManager`, *Then* coordinates are rejected as `null` and fallback track interpolation resolves true route coordinates.
* **AC-2 (Database Self-Healing)**: *Given* an existing record in SQLite `workout_photos` with `(0.0, 0.0)` coordinates, *When* loaded in `WorkoutPhotoManager` / `WorkoutRepository`, *Then* coordinates are re-interpolated or reset to `null` and updated in the DB.
* **AC-3 (Map Markers & Bounds Guard)**: *Given* photos or markers with invalid/Null-Island coordinates, *When* `WorkoutRepository.getWorkoutMarkers` and `MapBoundsController` execute, *Then* invalid coordinates are excluded from markers and `LatLngBounds.Builder`, preserving tight camera framing.

### Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-DAT-038.2` (`WorkoutPhotoManager.kt`, `WorkoutSummariesDatabaseManager.java`) and `REQ-UI-448.3` (`WorkoutRepository.kt`, `MapBehaviors.kt`).
* **Historical Origin & Commit Trace**: Commit `5c9d9653` (`ATT-43`, Sprint `2026-45.6`).
* **Root Reason for Existing Formulation**: Assumed `ExifInterface.getLatLong()` only returns `true` for legitimate non-zero GPS fixes and `photo.latitude != null` guarantees valid coordinates.
* **Preservation of Core Invariants**: Genuine coordinates remain unchanged; photos without GPS taken during workouts correctly acquire track coordinates; camera framing remains tightly focused on actual workout trajectories.

---

## 2. Test Specification (`TST-UI-447`, `TST-UI-448`)

### Test Case 1: Pure Coordinate Validation Contract (`TST-UI-448.3`, `GeoUtilsTest.kt`)
* Assert `GeoUtils.isValidGpsCoordinate` returns `false` for `(0.0, 0.0)`, `(1e-6, 1e-6)`, `(NaN, NaN)`, `(Infinity, 0.0)`, `(91.0, 0.0)`, and `null`.
* Assert `isValidGpsCoordinate` returns `true` for authentic locations (`(48.65, 9.05)`).

### Test Case 2: EXIF Rejection & DB Self-Healing (`TST-UI-448.4`, `WorkoutPhotoManagerTest.kt`)
* Test `extractExifMetadata` treats `(0.0, 0.0)` as `null`, allowing track fallback interpolation.
* Test loading photos with `(0.0, 0.0)` self-heals by re-interpolating or setting `null` via `updatePhotoCoordinates`.

### Test Case 3: Marker Filtering & Bounds Exclusion (`TST-UI-448.3`, `WorkoutPhotoUiContractTest.kt`, `MapBoundsControllerTest.kt`)
* Test `WorkoutRepository.getWorkoutMarkers` excludes photos with `(0.0, 0.0)` or invalid coordinates.
* Test `MapBoundsController` excludes invalid markers from `LatLngBounds.Builder`.

### Test Case 4: Clean-Room Governance & Regression (`TST-UI-448.6`)
* Run `python3 tools/verify_requirement_governance.py` and targeted unit test suite.

---

## 3. Traceability Matrix

| Requirement ID | Description | Test Case ID | Verification Method |
| :--- | :--- | :--- | :--- |
| `REQ-UI-448.3` | `GeoUtils.isValidGpsCoordinate` & Marker Filtering | `TST-UI-448.3` | Unit & Contract Test |
| `REQ-UI-448.4` | EXIF (0.0, 0.0) Rejection & DB Self-Healing | `TST-UI-448.4` | Database & Manager Test |
| `REQ-UI-447.2` | Overlay Silhouette & Bounds Sanitization | `TST-UI-447.1` | Overlay Composer Test |
| `REQ-UI-448` | System Invariants & Zero Continental Zoom Regression | `TST-UI-448.6` | Clean-Room Suite |
