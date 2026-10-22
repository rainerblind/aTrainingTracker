# Stage 5 Verification & Walkthrough: ATT-3779

## 1. Summary of Changes
* **Ticket**: `ATT-3779` — Render `ElevationProfile` empty state on tracking tabs when enabled before tracking starts or without active route
* **Requirements & Tests**: `REQ-UI-394`, `TST-UI-357`
* **Modified Files**:
  * [`ElevationProfile.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/ElevationProfile.kt):
    * Added `showEmptyState: Boolean = false` to both `ElevationProfile` composable overloads (defaulting to `false` so post-workout detail views with `< 2` points continue to remain hidden).
    * Added `ElevationProfileEmptyState` composable that uses the canonical minimum height `val targetHeight = fixedHeight ?: calculateElevationProfileHeight(0.0)` (`70.dp`, strictly complying with [`docs/design_guidelines.md`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/design_guidelines.md) Section 2.3 "No Hardcoded Fixed Heights"), draws 3 subtle horizontal reference grid lines (`colorScheme.outlineVariant.copy(alpha = 0.25f)`), and displays a centered `Icons.Default.Terrain` icon and `R.string.elevation_profile_empty_state` label (`labelMedium`, `colorScheme.onSurfaceVariant`, single-line ellipsis).
    * Added `ElevationProfileEmptyStateLightPreview` and `ElevationProfileEmptyStateDarkPreview` `@Preview` composables.
  * [`SensorGridScreen.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/SensorGridScreen.kt):
    * Changed the elevation profile visibility gate at line 661 from `if (state.showElevationProfile && resolvedProfile.pathPoints.size >= 2)` to `if (state.showElevationProfile)` and passed `showEmptyState = true` to `ElevationProfile(...)`.
    * Preserved the `ATT-3311` bottom-padding fence comment and `Modifier.padding(top = 8.dp)` surface layout.
  * `app/src/main/res/values*/strings.xml` (9 locales: `values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`):
    * Added localized `elevation_profile_empty_state` strings across all 9 supported languages.
  * [`ElevationProfileEmptyStateContractTest.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/ElevationProfileEmptyStateContractTest.kt):
    * Created `TST-UI-357` verifying `calculateElevationProfileHeight(0.0) == 70.dp`, `ElevationProfile.kt` empty-state contract, `SensorGridScreen.kt` `showEmptyState = true` wiring, and 9-locale `elevation_profile_empty_state` parity.

## 2. Verification & Clean-Room Regression Results
1. **Targeted Unit & Contract Tests (`TST-UI-357`)**:
   * `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.map.ElevationProfileEmptyStateContractTest"` — **PASSED** (4/4 test cases green).
2. **Full Clean-Room Unit Test Suite**:
   * `./gradlew testDebugUnitTest` — **PASSED** (100% pass rate, 0 failures, 0 regressions).

## 3. UI Consistency & Invariant Audit
* **Canonical Adaptive Height (`docs/design_guidelines.md` Section 2.3)**: `ElevationProfileEmptyState` computes its default height exclusively via `calculateElevationProfileHeight(0.0)` (`70.dp`), reserving the exact vertical space that a flat/initial live track occupies so that the sensor grid does not jump when the 2nd GPS point arrives.
* **Post-Workout Backward Compatibility**: All other `ElevationProfile` call sites (`TrackOnMapScreen`, `ClimbDetailSheet`, `SegmentDetailSheet`, `LapSummaryRow`) retain the default `showEmptyState = false` behavior.
* **9-Language Localization Parity**: `elevation_profile_empty_state` verified across all 9 locales.
