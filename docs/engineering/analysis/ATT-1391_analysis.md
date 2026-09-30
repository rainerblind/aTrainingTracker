# Stage 1 Analysis: ATT-1391 - Aftermath: Synchronized Multi-Metric Scrubbing on Elevation Profile

**Ticket**: [ATT-1391](https://atrainingtracker.atlassian.net/browse/ATT-1391)  
**Sub-task**: [ATT-1704](https://atrainingtracker.atlassian.net/browse/ATT-1704) (`[Analysis]`)  
**Parent Epic**: [ATT-111](https://atrainingtracker.atlassian.net/browse/ATT-111) (*Aftermath: Compact Post-Workout Visual Analytics & Graphs*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.5`  
**Branch**: `feature/ATT-1391`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-09-30  

---

## 1. Problem Statement & Motivation

In the post-workout Aftermath inspection interface (`MapDetailLayout.kt` / `ElevationProfile.kt`), the interactive elevation profile scrubber currently only highlights the spatial location dot on the map and renders a basic text label displaying route distance and altitude (e.g. `14.2 km | 345 m`).

Ambitious athletes ("Halb-Profis") analyzing their recorded training sessions face two critical analytical limitations:
1. **Disconnection Between Terrain and Physiological/Mechanical Response**:
   Athletes cannot correlate climbs, steep pitches, or descents with their physiological and biomechanical response. Questions such as *"What was my heart rate at the steepest pitch of that 12% climb?"*, *"How much power was I generating when the gradient eased?"*, or *"Did my cadence or pace drop on that hill?"* cannot be answered from the elevation profile alone.
2. **Fixed Distance-Only Domain**:
   The elevation profile chart is strictly plotted against total distance on the X-axis. For interval training, criteriums, track workouts, or stationary sessions, athletes frequently reason in *Elapsed Time* rather than distance. There is currently no option in the app to switch the profile X-axis domain to *Elapsed Time* (`hh:mm:ss`).

**Target Outcome**:
Enhance `ElevationProfile` and `MapDetailLayout` to support synchronized multi-metric scrubbing (Heart Rate, Power, Speed/Pace, Altitude, Slope, Distance, and Elapsed Time) with a dedicated floating telemetry overlay badge, graceful omission of missing sensors, and a user-configurable X-axis domain setting (*Distance* vs *Elapsed Time*) in `Experten-Einstellungen` (`TuningPreferencesDataStore`).

---

## 2. Root Cause Analysis (Forensic Investigation & Codebase Archaeology)

### 2.1 Spatial and Telemetry Disconnect in `PathPoint` & `WorkoutRepository`
- In [MapModels.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapModels.kt#L130-L136), `PathPoint` is currently defined as:
  ```kotlin
  data class PathPoint(
      val distance: Double,
      val latLng: LatLng,
      val altitude: Double
  )
  ```
  It completely omits telemetry attributes: elapsed time, heart rate, power, speed/pace, and slope.
- In [WorkoutRepository.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/WorkoutRepository.kt#L278-L323), `getWorkoutTrackPoints(workoutId, trackType)` queries the SQLite samples database table (`WorkoutSamplesDatabaseManager.getTableName(baseFileName)`).
- Forensic inspection of [TrackerService.java](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/tracker/TrackerService.java#L1026-L1050) and [WorkoutSamplesDatabaseManager.java](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/database/WorkoutSamplesDatabaseManager.java#L466-L485) reveals that the workout samples table records comprehensive telemetry for every tracked sample point:
  - `SensorType.HR.name` (`int`)
  - `SensorType.POWER.name` (`real`)
  - `SensorType.SPEED_mps.name` (`real`)
  - `SensorType.SLOPE.name` (`int`)
  - `SensorType.CADENCE.name` (`real`)
  - `SensorType.TIME_ACTIVE.name` (`int`)
  - `SensorType.TIME_TOTAL.name` (`int`)
  - `WorkoutSamplesDbHelper.TIME` (`time`, datetime text)
- Currently, `WorkoutRepository.getWorkoutTrackPoints()` discards all sensor columns, reading only `latIdx`, `lonIdx`, `altIdx`, and `distIdx`. Because the underlying database already stores these columns per sample point, no database migrations or new tables are required.

### 2.2 Polyline Simplification Preservation
- In [TrackOnMapAftermathViewModel.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/TrackOnMapAftermathViewModel.kt#L148-L180), when high-resolution tracks are simplified for map performance via `PolyUtil.simplify()`, `PathPoint` instances are reconstructed:
  ```kotlin
  for (target in simplifiedLatLngs) {
      while (originalIdx < fullPath.size && fullPath[originalIdx].latLng != target) {
          originalIdx++
      }
      if (originalIdx < fullPath.size) {
          result.add(fullPath[originalIdx])
          originalIdx++
      }
  }
  ```
  Because `fullPath[originalIdx]` is directly added to `result`, enriching `PathPoint` ensures all telemetry survives simplification intact.

### 2.3 Scrubber Interaction & Overlay Limitations in `ElevationProfile.kt`
- In [ElevationProfile.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/ElevationProfile.kt#L551-L584), the scrubber marker only renders a single canvas line and measures a simple text string:
  ```kotlin
  val combinedLabel = "${distanceFormatter.format_with_units(dist)} | ${altitudeFormatter.format_with_units(interAlt)}"
  canvas.nativeCanvas.drawText(combinedLabel, ...)
  ```
- Cramming Heart Rate, Power, Pace, and Gradient into a single raw Canvas `drawText` call creates severe truncation, lacks visual hierarchy, and cannot leverage Material 3 color tokens or training zone badges.
- A dedicated Compose floating telemetry card (`ScrubbingTelemetryBadge`) rendered above the profile when active provides modern visual polish, formatted chips with icons, and graceful handling of absent sensors.

### 2.4 Profile X-Axis Domain Configuration
- Currently, `ElevationProfile` and `ElevationProfileZoomMath` are strictly hardcoded to route distance in meters.
- In `TuningPreferencesDataStore.kt` and `AdvancedTuningDialog.kt` (introduced and structured in `REQ-SET-073` and `REQ-SET-074` / `ATT-1646`), expert preferences are centrally governed via Preferences DataStore.
- Adding a preference for `profileXAxisDomain` (`ProfileXAxisDomain.DISTANCE` vs `ProfileXAxisDomain.TIME`) allows athletes to toggle their preferred domain in `Experten-Einstellungen`.

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  1. **Enriched `PathPoint` Telemetry**:
     - Extend `PathPoint` with backward-compatible default parameters (`timeSec: Long = 0L`, `hr: Int? = null`, `power: Int? = null`, `speedMps: Double? = null`, `slope: Double? = null`).
     - Populate telemetry in `WorkoutRepository.getWorkoutTrackPoints()` from `WorkoutSamplesDatabaseManager`.
  2. **Multi-Metric Telemetry Scrubbing**:
     - When scrubbing on `ElevationProfile`, resolve the nearest/interpolated sample point and emit via callback (`onPointSelected: (PathPoint?) -> Unit`).
     - Display a modern floating telemetry badge/overlay (`ScrubbingTelemetryBadge`) presenting:
       - Distance & Elapsed Time (e.g. `14.2 km` / `42:15`)
       - Altitude & Gradient (e.g. `345 m` / `+6.5%`)
       - Heart Rate (`bpm`), Power (`W`), and Speed/Pace (`km/h` or `min/km` per sport type).
     - Gracefully omit absent metrics (e.g. omit Power if no power meter was connected).
  3. **Configurable X-Axis Domain (`ProfileXAxisDomain`)**:
     - Support `DISTANCE` (default) and `TIME` (Elapsed Time).
     - Persist setting in `TuningPreferencesDataStore` and surface a clean dropdown/toggle in `AdvancedTuningDialog` under a new *"Aftermath & Profil-Analytik"* category.
     - Adapt `ElevationProfile` rendering and tick intervals (`hh:mm:ss` / `mm:ss`) when `TIME` is selected.
  4. **Localization Parity**:
     - Provide full 9-language translations for all new labels and preference descriptions.

* **Out-of-Scope Non-Goals (Scope Bounding)**:
  - 5-zone time-in-zones horizontal distribution bars for Heart Rate (strictly reserved for [ATT-1389](https://atrainingtracker.atlassian.net/browse/ATT-1389)).
  - 5-zone time-in-zones horizontal distribution bars for Cycling Power (strictly reserved for [ATT-1390](https://atrainingtracker.atlassian.net/browse/ATT-1390)).
  - Split / Interval lap charts (strictly reserved for [ATT-1392](https://atrainingtracker.atlassian.net/browse/ATT-1392)).
  - Shareable snapshot graphic composition alterations (strictly reserved for [ATT-1393](https://atrainingtracker.atlassian.net/browse/ATT-1393)).
  - Modifying live tracking sensor processing or raw SQLite schema.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit

* **Original Requirement ID & Target**:
  - `REQ-UI-192`: Interactive Elevation Profile Zoom, Pan & Centered Scaling Navigation.
  - `REQ-UI-197`: Contextual Zoom Controls Visibility & Non-Overlapping Scrubber Text Architecture.
  - `REQ-SET-074`: Advanced Tuning Drawer Partitioning & Safety Defaults.
* **Historical Origin & Commit Trace**:
  - `REQ-UI-192` committed in `ATT-527` (Sprint `2026-40.4`).
  - `REQ-UI-197` committed in `ATT-1647` (Sprint `2026-40.5`).
  - `REQ-SET-074` committed in `ATT-1646` (Sprint `2026-40.5`).
* **Root Reason for Existing Formulation**:
  - `REQ-UI-192` established the horizontal gesture, viewport scaling math (`ElevationProfileZoomMath`), and pan clamping.
  - `REQ-UI-197` established the separation between preview list cards (`showZoomControls = false`) and full inspection views (`showZoomControls = true`), plus vertical layout decoupling between overlay control buttons and the canvas scrubber label.
* **Preservation of Core Invariants**:
  - `REQ-UI-192` zoom/pan scaling math is purely based on scalar window dimensions and remains 100% reusable for both distance and time.
  - Existing `onDistanceSelected: (Double?) -> Unit` callback is preserved for 100% backward compatibility with existing callers.
  - `REQ-UI-197` non-overlapping vertical architecture and suppression in preview cards is strictly preserved.
  - Net-new requirement `REQ-UI-201` builds cleanly upon these foundations without regressing existing contracts.

---

## 5. Architectural Strategy & High-Level Solution

### 5.1 Enriched `PathPoint` Domain Model
In [MapModels.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapModels.kt):
```kotlin
data class PathPoint(
    val distance: Double,
    val latLng: LatLng,
    val altitude: Double,
    val timeSec: Long = 0L,
    val hr: Int? = null,
    val power: Int? = null,
    val speedMps: Double? = null,
    val slope: Double? = null
)
```
Default arguments guarantee complete backward compatibility for all existing construction sites (`PathPoint(dist, latLng, alt)`).

### 5.2 Telemetry Querying in `WorkoutRepository.kt`
In `WorkoutRepository.getWorkoutTrackPoints(workoutId, trackType)`:
Resolve column indices for `TIME_ACTIVE`, `TIME_TOTAL`, `HR`, `POWER`, `SPEED_mps`, and `SLOPE`.
When iterating cursor rows, extract these values safely (handling nulls) and construct enriched `PathPoint` instances.

### 5.3 `ProfileXAxisDomain` Preference in `TuningPreferencesDataStore.kt`
```kotlin
enum class ProfileXAxisDomain {
    DISTANCE,
    TIME
}
```
Added to `TuningConfig` and `TuningPreferencesDataStore` with factory default `ProfileXAxisDomain.DISTANCE`.
In `AdvancedTuningDialog.kt`, add Category 3: *"Aftermath & Profil-Analytik"* with an interactive selector for X-axis domain.

### 5.4 Elevation Profile Multi-Metric Scrubbing Overlay
In `ElevationProfile.kt`:
- Support `xAxisDomain: ProfileXAxisDomain = ProfileXAxisDomain.DISTANCE`.
- Support `bSportType: BSportType = BSportType.CYCLING`.
- Support `onPointSelected: ((PathPoint?) -> Unit)? = null`.
- When scrubbing, find the nearest `PathPoint` along the active X dimension.
- Render `ScrubbingTelemetryBadge`: a floating `Surface` card positioned gracefully at the top of the profile displaying active metrics (Heart Rate in bpm, Power in W, Speed/Pace, Altitude, Gradient, Distance, and Time).
- If HR or Power are absent (`null`), omit them cleanly without leaving empty placeholders.

---

## 6. System Invariants & Risk Assessment

* **Core Invariants**:
  1. Zero regression in existing unit tests and UI rendering for routes, segments, and workout previews.
  2. Complete backward compatibility of `PathPoint` and `ElevationProfile` constructor parameters.
  3. Thread safety: database cursor reading remains strictly on `Dispatchers.IO`.
  4. 9-language localization parity strictly enforced.
  5. Terminal Human Decision Gate remains strictly intact (`Final Review (Human)`).

* **Risk Rating**: **LOW**
  - All database tables and columns already exist in `WorkoutSamples.db`.
  - `PathPoint` default arguments avoid touching call sites across unrelated features.
  - Math for zoom/pan scaling is already decoupled in `ElevationProfileZoomMath`.
