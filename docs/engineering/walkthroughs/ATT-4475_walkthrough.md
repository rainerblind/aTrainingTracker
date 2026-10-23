# Stage 5 Walkthrough: ATT-4475 — Decouple Anchor Tracks, Eliminate UI-Thread Polyline Decoding & Restore Clickability

## 1. Executive Summary
Eliminated vector track double-rendering, UI-thread polyline decoding OOM-guard bypasses, and 4x heap polyline duplication in `InteractivePeriodMap` and `PeriodsRepository`. Scoped `PeriodSummary.workoutIdToPolylineMap` strictly to spatial anchor workouts ($\le 5$: North, South, East, West, Longest) and pre-decoded anchor paths into `workoutIdToPathMap` off the main thread. In `InteractivePeriodMap.kt`, rendered `anchorTracks` strictly while `mapState.isLoading == true`, seamlessly handing off to `memberTracks` upon load completion. Added `findClosestTrackOnMapClick` with zoom-adaptive tolerance in `PeriodMapUtils.kt` and wired map tap handling to `onWorkoutClick`, restoring full route clickability and single-workout highlight peeking even in large periods (> 30 workouts) rendered via line-density heatmaps.

## 2. Key Architectural Changes
| File / Directory | Summary of Changes |
| :--- | :--- |
| `PeriodsRepository.kt` | Scoped `summary.workoutIdToPolylineMap` and `workoutIdToPathMap` strictly to $\le 5$ spatial anchor workouts in `enrich()`, pre-decoding anchor paths on the background thread and eliminating 4x heap duplication in `groupedPeriods`. |
| `InteractivePeriodMap.kt` | Rendered `anchorTracks` exclusively while `mapState.isLoading == true`, eliminating double-rendering when `memberTracks` take over. Replaced synchronous UI-thread `PolyUtil.decode` with pre-decoded `workoutIdToPathMap`. Wired map clicks to spatial tap hit-testing. |
| `PeriodMapUtils.kt` | Added `findClosestTrackOnMapClick(tap, paths, zoom)` utilizing `PolyUtil.isLocationOnPath` with zoom-adaptive tolerance (5m–100m). Updated `prepareCardMapPreviewSync` to prioritize full `summary.polylines`. |
| `PeriodMapScreen.kt` | Evaluated `isAdaptiveLayering` against total workouts and heatmap paths count. Passed `peekedTracks` as highlighted vector track when memberTracks is empty on large periods. |
| `InteractivePeriodMapTest.kt` | Comprehensive unit test suite covering `TST-PER-027.1`..`TST-PER-027.5` (anchor isolation, track deduplication, zero UI decoding, spatial tap hit-testing, and large period peek highlighting). |

## 3. Verification & Test Results
* **Tier 1 Unit & Contract Tests**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.aftermath.periodlist.*" --tests "com.atrainingtracker.trainingtracker.build.*"` -> `BUILD SUCCESSFUL` (100% pass rate).
* **Resource Compilation Gate**: `./gradlew processDebugResources` -> `BUILD SUCCESSFUL`.
* **Requirement Governance Audit**: `python3 tools/verify_requirement_governance.py` -> `EXIT CODE 0` (clean pass).
* **Target Release Version**: `V4.9.40` (FixVersion assigned to parent ticket upon sprint acceptance per Rule 8).

## 4. Requirement & Test Traceability
| Requirement ID | Test ID | Status | Verification Method |
| :--- | :--- | :--- | :--- |
| `REQ-PER-021` | `TST-PER-027` | `Verified` | `InteractivePeriodMapTest` & `./gradlew testDebugUnitTest` |
