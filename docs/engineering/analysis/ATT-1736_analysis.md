# Stage 1 Analysis: ATT-1736 - [ElevationProfile/LiveSegment] Suppress elevation zoom controls in LiveSegment popup

**Ticket**: [ATT-1736](https://atrainingtracker.atlassian.net/browse/ATT-1736)  
**Sub-task**: [ATT-1760](https://atrainingtracker.atlassian.net/browse/ATT-1760) (`[Analysis]`)  
**Parent Epic**: [ATT-355](https://atrainingtracker.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.6`  
**Branch**: `feature/ATT-1736`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-01  

---

## 1. Problem Statement & Motivation

During on-device testing and the sprint review of Sprint `2026-40.5` (evaluating ticket [ATT-1647](https://atrainingtracker.atlassian.net/browse/ATT-1647) / [ATT-1644](https://atrainingtracker.atlassian.net/browse/ATT-1644)), the user observed that within the LiveSegment popup during tracking cockpit view, the elevation zoom controls (`+`, `-`, pan/scrub mode toggle) are still displayed over the elevation profile.

In the active tracking cockpit (`SensorGridScreen.kt`), the `LiveSegmentSheet` (`LIveSegmentSheet.kt`) acts as an ambient bottom sheet / popup alerting the athlete to segment progress, distance remaining, and upcoming elevation changes. Unlike full-screen detail views ([TrackOnMapScreen.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/TrackOnMapScreen.kt), [RouteOnMapScreen.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/routes/RouteOnMapScreen.kt), [SegmentOnMapScreen.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/segments/SegmentOnMapScreen.kt)), the LiveSegment popup has limited vertical real estate and is designed for at-a-glance consumption during intensive training. Having interactive zoom controls rendered over the profile clutters the popup, risks accidental touches while cycling or running, and detracts from the clean visual hierarchy.

---

## 2. Root Cause Analysis & Architectural Investigation

### 2.1 Current Implementation in `MapDetailLayout.kt` and `LIveSegmentSheet.kt`
- In [MapDetailLayout.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapDetailLayout.kt#L210-L221), `ElevationProfile` is instantiated with hardcoded `showZoomControls = true`:
  ```kotlin
  ElevationProfile(
      pathPoints = path,
      currentDistance = selectedDistance,
      minAltitudeOverride = minAltitudeOverride,
      maxAltitudeOverride = maxAltitudeOverride,
      onDistanceSelected = { selectedDistance = it },
      showZoomControls = true,
      xAxisDomain = tuningConfig.profileXAxisDomain,
      bSportType = bSportType,
      modifier = Modifier.fillMaxWidth()
  )
  ```
- [LIveSegmentSheet.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/segments/LIveSegmentSheet.kt#L38-L71) leverages `MapDetailLayout` with `useStatusBarsPadding = false` and `showMap = false`.
- However, because `MapDetailLayout` does not expose a `showZoomControls` parameter, `showZoomControls = true` is unconditionally passed down to `ElevationProfile`, causing the zoom controls row and legend info button to be rendered in the compact sheet popup.

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  * Add a configurable `showZoomControls: Boolean = true` parameter to `MapDetailLayout`.
  * Pass `showZoomControls = showZoomControls` from `MapDetailLayout` to `ElevationProfile`.
  * In `LiveSegmentSheet` (`LIveSegmentSheet.kt`), explicitly pass `showZoomControls = false` to suppress zoom controls completely in the LiveSegment popup.
  * Update `ElevationProfileLayoutTest.kt` and `LiveSegmentSheetLayoutTest.kt` to verify that `MapDetailLayout` supports `showZoomControls` and `LiveSegmentSheet` sets it to `false`.
  * Update living specifications `docs/requirements.md` (`REQ-UI-197`) and `docs/tests.md` (`TST-UI-151`).

* **Out-of-Scope Non-Goals (Scope Bounding)**:
  * Modifying zoom control behaviors or gestures on full-screen detail views (`TrackOnMapScreen`, `RouteOnMapScreen`, `SegmentOnMapScreen`, `WorkoutClusterHeatmapScreen`), where zoom controls remain enabled (`showZoomControls = true`).
  * Changing `LiveSegment` telemetry extraction, segment status tracking, or GPS matching logic.
  * Changing background styling or padding (which is addressed separately in ticket `ATT-1735`).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit (REQ-PRO-022)

### Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**:
  - `REQ-UI-197` (*Elevation Profile Contextual Zoom Controls Visibility & Non-Overlapping Scrubber Text Architecture*), targeting `ElevationProfile.kt` and `MapDetailLayout.kt`.
* **Historical Origin & Commit Trace**:
  - Ticket `ATT-1647`, Sprint `2026-40.5`.
* **Root Reason for Existing Formulation**:
  - `REQ-UI-197` parameterized `ElevationProfile` with `showZoomControls: Boolean = false`, suppressing controls in preview cards (`WorkoutSummary.kt`, `RouteItem.kt`, `SegmentItem.kt`) while enabling them in `MapDetailLayout.kt`.
  - When `MapDetailLayout` was updated to set `showZoomControls = true`, it did not distinguish between full-screen inspection screens and compact popup sheets such as `LiveSegmentSheet`.
* **Preservation of Core Invariants**:
  - Full-screen map detail layouts continue to display zoom controls and legend button by default (`showZoomControls = true`).
  - Interactive scrubbing, elevation calculations, and coordinate display remain fully supported and unregressed.
  - Suppressing zoom controls in `LiveSegmentSheet` improves cockpit ergonomics and prevents accidental touch inputs during active workouts.

---

## 5. Architectural Strategy & High-Level Solution

1. **Parameterize `MapDetailLayout.kt`**:
   - Add `showZoomControls: Boolean = true` to `MapDetailLayout` signature with default value `true`.
   - Forward `showZoomControls = showZoomControls` into `ElevationProfile`.
2. **Configure `LIveSegmentSheet.kt`**:
   - In `LiveSegmentSheet`, invoke `MapDetailLayout` with `showZoomControls = false`.
3. **Verify with Tests**:
   - Update `LiveSegmentSheetLayoutTest.kt` to inspect that `LIveSegmentSheet.kt` explicitly sets `showZoomControls = false`.
   - Update `ElevationProfileLayoutTest.kt` to verify that `MapDetailLayout.kt` supports the `showZoomControls` parameter defaulting to `true`.
4. **Documentation**:
   - Update `REQ-UI-197` in `docs/requirements.md` and `TST-UI-151` in `docs/tests.md`.

---

## 6. System Invariants & Risk Assessment

* **Core Invariants**:
  1. Default parameter `showZoomControls: Boolean = true` in `MapDetailLayout` ensures zero behavioral regression across all existing detail screens (`TrackOnMapScreen`, `RouteOnMapScreen`, `SegmentOnMapScreen`, `WorkoutClusterHeatmapScreen`).
  2. Single-thread SQLite confinement and background dispatchers remain completely untouched.
  3. Parent human gate invariance preserved.
* **Risk Rating**: **LOW**
  - Justification: Pure declarative Compose parameter pass-through without mutable state or threading impact.
