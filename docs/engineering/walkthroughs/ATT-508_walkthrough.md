# Walkthrough: Altitude Bounds Sanitization, Strict Containment & Extrema Self-Healing (ATT-508)

## 1. Overview
This change resolves two related elevation display anomalies:
1. **Unplausible Startup Bounds**: Ca' Savio sea-level route displaying unplausible bounds of -63 m and +60 m due to historical GPS cold-start pollution in SQLite summary extrema.
2. **Bottom Baseline Clipping (Iteration 2)**: Workout "Kurz zum Bäcker" (07.09.2026, 06:21 in Schönaich) where the elevation curve dipped below the bottom axis bound (364 m) due to `minAltitudeOverride` exceeding `streamMin` and narrow-span expansion allowing `bounds.min > streamMin`.

---

## 2. Key Changes

### Component 1: [ElevationProfile.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/ElevationProfile.kt#L90-L144) (Presentation Layer - `REQ-UI-126`)
* **Strict Containment Invariant**: Refined [`calculateElevationBounds`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/ElevationProfile.kt#L90-L144):
  * If `minAltitudeOverride > streamMin`, it is clamped down to `streamMin` to prevent the bottom bound from rising above actual route points.
  * If `minAltitudeOverride < streamMin - 15.0m`, it is clamped to `streamMin` (removing rogue spikes).
  * Symmetrically, `maxAltitudeOverride` is clamped to `streamMax` if `< streamMax` or `> streamMax + 15.0m`.
* **Safe Aesthetic Span**: When expanding a narrow route to the 20 m aesthetic minimum span, `expandedMin` is capped at `streamMin` (`minOf(mid - 10.0, streamMin)`), and `expandedMax` is bounded at `streamMax`.
* **Guarantee**: Under all circumstances, $[S_{\min}, S_{\max}] \subseteq [\text{bounds.min}, \text{bounds.max}]$; no plotted point or scrubber marker can ever lie outside the visual bounds.

### Component 2: [WorkoutDataMapper.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/WorkoutDataMapper.kt#L500-L554) (Self-Healing Data Layer - `REQ-DAT-009`)
* **Bidirectional Healing**: Refined [`reconcileAltitudeExtrema`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/WorkoutDataMapper.kt#L500-L554):
  * Heals SQLite `extrema_values` whenever stored extrema contradict the stream (`recordedMin > streamMin` or `recordedMax < streamMax`), as well as rogue spikes (`recordedMin < streamMin - 15.0` or `recordedMax > streamMax + 15.0`).
  * Harmonizes the summary extrema table and the elevation profile chart on identical, true values ([`REQ-UI-013`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/requirements.md)).

### Component 3: [AltitudeFromPressureDevice.java](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/banalservice/devices/AltitudeFromPressureDevice.java#L171-L185) (Sensor Driver Layer - `REQ-CON-011`)
* Reordered `onSensorChanged()` to execute `initPressureSensor()` prior to emitting the first measurement, preventing uncalibrated standard atmosphere values from entering active sessions.

### Component 4: Automated Unit Tests
* [ElevationProfileBoundsTest.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/ElevationProfileBoundsTest.kt) (`TST-UI-079`): Added tests asserting that `bounds.min <= streamMin` and `bounds.max >= streamMax` when overrides fall inside the stream, and proving zero curve clipping on *"Kurz zum Bäcker"*.
* [WorkoutDataMapperAltitudeTest.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/test/java/com/atrainingtracker/trainingtracker/ui/aftermath/WorkoutDataMapperAltitudeTest.kt) (`TST-DAT-003`): Added tests asserting bidirectional healing for `recordedMin > streamMin` and `recordedMax < streamMax`.

---

## 3. Verification Results

### Automated Unit Tests
```bash
./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.map.ElevationProfileBoundsTest" --tests "com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutDataMapperAltitudeTest"
```
* **Result**: `BUILD SUCCESSFUL in 28s` (32 actionable tasks, 12 executed, 20 up-to-date; all 10 test cases passed green with 0 errors).
