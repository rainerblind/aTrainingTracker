# Stage 3 Implementation Plan: Improve Filtering (ATT-882)

**Ticket**: [ATT-882](https://atrainingtracker.atlassian.net/browse/ATT-882) / Sub-task: [ATT-1104](https://atrainingtracker.atlassian.net/browse/ATT-1104)  
**Author**: Agent 1 (Pair Programming Assistant)  
**Date**: 2026-09-17  
**Target Version**: `V4.9.37`  
**Git Branch**: `feature/ATT-882`  
**Requirement**: [REQ-UI-157](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/requirements.md#L293)  
**Test Specification**: [TST-UI-110](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/tests.md#L333)  

---

## 1. Executive Summary & Design Rationale

### 1.1 Problem Statement
The workout list filtering sheet (`WorkoutFilterBottomSheet.kt`) provides basic multi-dimensional filtering, but lacks precision controls:
1. **Unfiltered Sport Sub-Types**: Regardless of whether the athlete is viewing the Bike, Run, or Other tab, all sports ever recorded in the database are listed in the sport selector.
2. **Missing Date Interval**: Athletes can only filter by year/month, but cannot specify custom start and end dates ($[T_{\text{start}}, T_{\text{end}}]$).
3. **Missing Distance Interval**: Athletes can only select open-ended minimum distance thresholds ($D \ge D_{\text{min}}$), but cannot specify an upper bound or custom interval ($D_{\text{min}} \le D \le D_{\text{max}}$).
4. **Missing Duration Interval**: Athletes can only select open-ended minimum duration thresholds ($T \ge T_{\text{min}}$), but cannot specify an upper bound or custom interval ($T_{\text{min}} \le T \le T_{\text{max}}$).

*Note: Per explicit user direction, the 5th ellipsis point from the original ticket description is omitted.*

### 1.2 Architectural Solution
1. **Tab-Aware Sport Filtering (`WorkoutTabsScreen.kt` & `WorkoutFilterBottomSheet.kt`)**:
   - Inspect active tab from `pagerState.currentPage`:
     - Page 1 $\rightarrow$ `BSportType.BIKE`
     - Page 2 $\rightarrow$ `BSportType.RUN`
     - Page 3 $\rightarrow$ `BSportType.UNKNOWN` (Other)
     - Page 0 $\rightarrow$ `null` (All)
   - Pass `activeBSportType: BSportType?` to `WorkoutFilterBottomSheet`.
   - Filter `availableSports` by `activeBSportType == null || it.bSportType == activeBSportType`.
2. **Date Interval Filtering**:
   - Provide custom start and end date controls in `WorkoutFilterBottomSheet`.
   - Use Material 3 `DatePickerDialog` to select start and end dates.
   - Bind to `startDateS` (00:00:00 UTC) and `endDateS` (23:59:59 UTC).
3. **Distance Interval Filtering**:
   - Extend `WorkoutFilterCriteria` with `val maxDistanceMeters: Double? = null`.
   - Update `matches()` to enforce $D_{\text{min}} \le \text{workout.totalDistance} \le D_{\text{max}}$.
   - Provide numeric inputs (Min km, Max km) and interval preset chips in `WorkoutFilterBottomSheet`.
4. **Duration Interval Filtering**:
   - Extend `WorkoutFilterCriteria` with `val maxDurationSec: Long? = null`.
   - Update `matches()` to enforce $T_{\text{min}} \le \text{workout.activeTimeSec} \le T_{\text{max}}$.
   - Provide numeric inputs (Min min, Max min) and interval preset chips in `WorkoutFilterBottomSheet`.
5. **Active Filter Chips & Serialization**:
   - Update `ActiveFilterChipsRow.kt` to format distance and duration intervals ($D_{\text{min}} - D_{\text{max}}$ km, $T_{\text{min}} - T_{\text{max}}$ min).
   - Ensure backward-compatible JSON serialization/deserialization in `WorkoutFilterCriteria`.
6. **Localization Parity**:
   - Localize all labels and formats across all 9 supported locales (EN, DE, ES, FR, IT, JA, NL, PL, PT).

---

## 2. Invariants & Safety Guardrails

1. **Tab Partitioning Integrity**:
   - The top-level tab filtering by `BSportType` in `WorkoutTabsScreen` remains unchanged.
2. **Backward Compatibility**:
   - `WorkoutFilterCriteria.fromJson` safely handles legacy JSON strings that omit `maxDistanceMeters` or `maxDurationSec` with null defaults.
3. **Filter Count Accounting**:
   - Distance range counts as 1 filter dimension (regardless of whether lower, upper, or both bounds are set).
   - Duration range counts as 1 filter dimension.
   - Date range counts as 1 filter dimension.
4. **Clean Reset Guarantee**:
   - Tapping "Clear all" or individual chip removal clears the bounds completely.
   - Navigating back to main tracking resets the filters as per `REQ-UI-132`.

---

## 3. Detailed Component Changes

### 3.1 `WorkoutFilterCriteria.kt`
- Add properties:
  ```kotlin
  val maxDistanceMeters: Double? = null,
  val maxDurationSec: Long? = null
  ```
- Update `activeFilterCount`:
  ```kotlin
  if (minDistanceMeters != null || maxDistanceMeters != null) count++
  if (minDurationSec != null || maxDurationSec != null) count++
  ```
- Update `matches(workout: WorkoutData)`:
  ```kotlin
  if (minDistanceMeters != null && workout.totalDistance < minDistanceMeters) return false
  if (maxDistanceMeters != null && workout.totalDistance > maxDistanceMeters) return false
  if (minDurationSec != null && workout.activeTimeSec < minDurationSec) return false
  if (maxDurationSec != null && workout.activeTimeSec > maxDurationSec) return false
  ```
- Update `toJson()` and `fromJson()` with null-safe handling.

### 3.2 `WorkoutTabsScreen.kt`
- Resolve `activeBSportType`:
  ```kotlin
  val activeBSportType = when (pagerState.currentPage) {
      1 -> BSportType.BIKE
      2 -> BSportType.RUN
      3 -> BSportType.UNKNOWN
      else -> null
  }
  ```
- Pass `activeBSportType` into `WorkoutFilterBottomSheet`.
- Wire `onRemoveDistanceRange` and `onRemoveDurationRange` in `ActiveFilterChipsRow`.

### 3.3 `WorkoutFilterBottomSheet.kt`
- Accept `activeBSportType: BSportType? = null`.
- Filter `availableSports` by active tab.
- Date interval controls with `DatePickerDialog` for `startDateS` and `endDateS`.
- Distance interval controls: Min/Max inputs + preset chips.
- Duration interval controls: Min/Max inputs + preset chips.

### 3.4 `ActiveFilterChipsRow.kt`
- Display interval format chips (`X - Y km`, `X - Y min`).

### 3.5 Localization (`strings.xml` across all 9 locales)
- Add necessary interval formatting and label strings.

---

## 4. Verification Plan

### 4.1 Automated Unit Tests
1. **Criteria Interval Evaluation (`WorkoutFilterCriteriaTest.kt`)**:
   - Distance lower bound, upper bound, bounded interval, out-of-bounds rejection.
   - Duration lower bound, upper bound, bounded interval, out-of-bounds rejection.
   - Backward-compatible JSON round-trip serialization/deserialization.
   - `activeFilterCount` calculation.
2. **Tab-Aware Sport Filtering Logic**:
   - Verification with `BSportType.BIKE`, `BSportType.RUN`, `BSportType.UNKNOWN`, and `null`.
3. **Full Suite Regression**:
   - Run `./gradlew testDebugUnitTest` to guarantee 0 regressions across the entire project.

### 4.2 Manual / UI Verification
- Build and run app (`./gradlew installDebug`).
- Navigate to Workouts tab:
  - Switch to Bike tab, open filter: verify only bike sports shown.
  - Switch to Run tab, open filter: verify only run sports shown.
  - Switch to Other tab, open filter: verify only non-bike/non-run sports shown.
  - Test setting date interval via date picker: verify workouts outside date range disappear.
  - Test distance interval: set 10 km to 30 km, verify workouts are filtered accurately.
  - Test duration interval: set 30 min to 60 min, verify workouts are filtered accurately.
  - Verify active filter chips strip shows interval representations and chips can be removed cleanly.
