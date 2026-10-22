# Stage 5 Walkthrough: ATT-4478 — Upgrade PeriodBarGraph with Timeline Rest Spacing, Sub-Hour Labels & Metric Modes

## 1. Executive Summary
Extracted and upgraded `PeriodBarGraph` into a standalone, modular analytics component under `ui.aftermath.periodlist`. The upgraded component interpolates chronological timeline gaps (`interpolateTimelineGaps`) by rendering subtle baseline ticks (2 dp) for zero-volume rest periods, formats sub-hour durations cleanly (`<1m`, `45m`, `1.5h`), renders compact X-axis period labels (`d.M`, `Www`, `MMM`, `yyyy`), supports sport-stacked vertical segments (`BIKE`, `RUN`, `OTHER`), and provides a compact 3-way `SingleChoiceSegmentedButtonRow` to switch metrics across Duration, Distance, and Ascent with dynamic scaling.

## 2. Key Architectural Changes
| File / Directory | Summary of Changes |
| :--- | :--- |
| `PeriodBarGraph.kt` | Standalone composable with timeline gap interpolation, sub-hour duration labels, compact X-axis period labels, sport-stacked proportional segments, and metric switcher (`DURATION`, `DISTANCE`, `ASCENT`). Includes Light/Dark `@Preview`. |
| `PeriodsTabsScreen.kt` | Removed 70-line legacy inline `PeriodBarGraph` and integrated upgraded standalone component with clean padding and bidirectional scroll synchronization. |
| `PeriodBarGraphTest.kt` | Comprehensive unit test suite covering `TST-PER-024` (timeline gap filling, rest buckets, gap capping, sub-hour formatting, metric switching, sport stacking, and empty/boundary edge cases). |

## 3. Verification & Test Results
* **Tier 1 Unit & Contract Tests**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.aftermath.periodlist.*" --tests "com.atrainingtracker.trainingtracker.build.*"` -> `BUILD SUCCESSFUL` (100% pass rate).
* **Resource Compilation Gate**: `./gradlew processDebugResources` -> `BUILD SUCCESSFUL`.
* **Localization Parity Audit**: `TranslationParityTest` -> `BUILD SUCCESSFUL` (100% pass across all 9 locales: EN, DE, ES, FR, IT, JA, NL, PL, PT).
* **Requirement Governance Audit**: `python3 tools/verify_requirement_governance.py` -> `EXIT CODE 0` (clean pass).
* **Target Release Version**: `V4.9.40` (FixVersion assigned to parent ticket upon sprint acceptance per Rule 8).

## 4. Requirement & Test Traceability
| Requirement ID | Test ID | Status | Verification Method |
| :--- | :--- | :--- | :--- |
| `REQ-PER-018` | `TST-PER-024` | `Verified` | `PeriodBarGraphTest` (gap interpolation, sub-hour formatting, sport stacking, metric switching, boundary protections) |
