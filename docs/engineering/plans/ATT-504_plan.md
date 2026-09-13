# Implementation Plan: ATT-504 - Period Summary Visual & Data Fixes

## 1. Goal Description
Resolve three reported defects in the Period Summary components:
1. **Heatmap Truncation ("Heat map only in parts")**: Uniformly sample points across ALL paths in `allPaths` using `samplingStep = max(1, totalPoints / maxPoints)` to ensure complete geographic representation without path truncation.
2. **Heatmap Over-Dominance ("Heat map too dominant")**: Use semi-transparent ARGB gradient colors (~0.5–0.7 alpha) and tune tile overlay opacity (0.4–0.6) so underlying terrain, roads, and map labels remain clearly legible.
3. **Missing Period & Longest Workout Metrics ("Values missing: Duration, Length, Ascent")**:
   - Update `PeriodSummariesDatabaseManager.kt` to query `WorkoutSummariesTable` for `name`, `durationSec`, `distanceMeters`, and `ascentMeters` when loading `longest_workout_id` from the database.
   - Add `totalAscentMeters` to `PeriodSummary` data model and aggregation logic in `PeriodsRepository.kt`.
   - Update Period Summary card headers, Map headers, and Sub-Sport rows (`CompactMetricRow`) to display the full metric triad (Duration, Length/Distance, Ascent).

---

## 2. Proposed Changes

### Component 1: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapUtils.kt`
#### [MODIFY] [MapUtils.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapUtils.kt)
- Update `createHeatmapProvider(...)`:
  - Calculate `totalPoints = allPaths.sumOf { it.size }`.
  - Calculate `samplingStep = max(1, (totalPoints.toDouble() / maxPoints).toInt())`.
  - Iterate through ALL paths, adding points every `samplingStep` interval across the whole dataset so no workouts are truncated.
  - Update default gradient colors with translucent alpha channels (e.g. `0x9900E5FF`, `0xB30000FF`, `0xCC311B92`).

### Component 2: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/periodlist/PeriodMapUtils.kt`
#### [MODIFY] [PeriodMapUtils.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/periodlist/PeriodMapUtils.kt)
- Update `getPeriodMapVisuals(...)`:
  - Adjust opacity levels (0.4f for Week, 0.5f for Month, 0.6f for Year) to ensure terrain and road labels remain visible through the density layer.

### Component 3: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/periodlist/PeriodData.kt`
#### [MODIFY] [PeriodData.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/periodlist/PeriodData.kt)
- Add `val totalAscentMeters: Long = 0L` to `PeriodSummary` data class with default value `0L` for backward compatibility.

### Component 4: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/periodlist/PeriodSummariesDatabaseManager.kt`
#### [MODIFY] [PeriodSummariesDatabaseManager.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/periodlist/PeriodSummariesDatabaseManager.kt)
- Add helper `fetchLongestWorkoutsFromDb(...)` that queries `WorkoutSummariesTable` for `name`, `durationSec`, `distanceMeters`, and `ascentMeters` for a set of workout IDs.
- In `getAllSummaries(...)` and `getSportStatsForPeriod(...)`, replace dummy `LongestWorkout(id, "", 0, 0.0, 0)` instantiation with real metrics fetched from `WorkoutSummariesTable`.

### Component 5: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/periodlist/PeriodsRepository.kt`
#### [MODIFY] [PeriodsRepository.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/periodlist/PeriodsRepository.kt)
- Update `aggregateWorkoutsToDay(...)` and `aggregateChildrenToParent(...)` to aggregate `totalAscentMeters` across workouts and child summaries.

### Component 6: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/periodlist/PeriodSummaryCard.kt`
#### [MODIFY] [PeriodSummaryCard.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/periodlist/PeriodSummaryCard.kt)
- Period Header: Display Duration, Length (`df.format_with_units(summary.totalDistance)` if > 0), and Ascent (`af.format_with_units(summary.totalAscentMeters)` if > 0).
- `CompactMetricRow`: Display Length and Duration only (no Ascent).

### Component 7: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/periodlist/PeriodMapScreen.kt`
#### [MODIFY] [PeriodMapScreen.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/periodlist/PeriodMapScreen.kt)
- Map Header: Display Duration, Length (`summary.totalDistance`), and Ascent (`summary.totalAscentMeters`).

---

## 3. Verification Plan

### Automated Tests
- Execute `app:unitTests` to verify no regressions in repository aggregation or map utility calculations.

### Manual Verification Steps (`TST-PER-013` & `TST-PER-014`)
1. **Uniform Heatmap Verification (`TST-PER-013`)**:
   - Open a Monthly or Yearly period containing 20+ workouts.
   - Verify all workout regions appear in the heatmap without truncation.
   - Verify road labels and terrain remain clearly legible underneath the translucent heatmap.
2. **Volume Metrics & Longest Workout Verification (`TST-PER-014`)**:
   - Open the Period Summary card list for a period loaded from the database.
   - Verify the Period header displays Duration, Length (Distance), and Ascent.
   - Verify sub-sport rows (`CompactMetricRow`) display Ascent alongside Distance and Duration.
   - Verify the Longest Workout highlight for sub-sports displays non-zero name, duration, distance, and ascent!
