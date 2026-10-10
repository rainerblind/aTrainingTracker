# ATT-3108 Walkthrough: LiveSegmentSheet Crashes with NumberFormatException Attempting to Parse Formatted Distance String on Segment Finish

## Overview
- **Issue**: [ATT-3108](https://atrainingtracker.atlassian.net/browse/ATT-3108)
- **Requirement**: `REQ-UI-340`
- **Test Specification**: `TST-UI-300`
- **Sprint**: `2026-41.8`
- **Status**: Verified

## Problem Addressed
When an athlete completed a live segment during active tracking, the app crashed abruptly with a fatal exception:
`java.lang.NumberFormatException: For input string: "766 m"`.

Forensic investigation revealed:
1. In `LIveSegmentSheet.kt`, `externalScrubDistance` on `LiveSegmentStatus.FINISHED` evaluated:
   ```kotlin
   liveSegment.staticData.summary.distance.toDouble().coerceAtLeast(liveSegment.liveData.distanceOnSegment_raw)
   ```
2. In `SegmentSummary` (`SegmentsRepository.kt`), `distance` is a localized formatted string (e.g. `"766 m"` or `"1.2 km"`), whereas `distance_raw` is the canonical unformatted `Double` metric in meters.
3. Calling `.toDouble()` on `summary.distance` immediately threw `NumberFormatException` upon segment finish.

## Architectural Changes & Solution

1. **Direct `distance_raw` Metric Evaluation (`LIveSegmentSheet.kt`)**:
   - Replaced `liveSegment.staticData.summary.distance.toDouble()` with `liveSegment.staticData.summary.distance_raw`.
   - Guaranteed type-safe evaluation without string parsing.

2. **Updated Contract Test (`LiveSegmentSheetContractTest.kt`)**:
   - Updated contract assertion to verify that `LIveSegmentSheet.kt` maps `LiveSegmentStatus.FINISHED` to `liveSegment.staticData.summary.distance_raw.coerceAtLeast(liveSegment.liveData.distanceOnSegment_raw)` without `.toDouble()`.

3. **Non-Crashing Unit Tests (`LiveSegmentSheetLayoutTest.kt`)**:
   - Added `testLiveSegmentSheet_evaluatesFinishedDistanceUsingDistanceRawWithoutCrashing` verifying that:
     - Formatted string `"766 m"` with `distance_raw = 766.0` evaluates safely without throwing `NumberFormatException`.
     - Decimal comma formatted string `"1,2 km"` with `distance_raw = 1200.0` evaluates cleanly to `1200.0`.

## Verification & Test Results
- **Targeted Test Suite**:
  - `LiveSegmentSheetContractTest`: 100% pass.
  - `LiveSegmentSheetLayoutTest`: 100% pass across all 6 test cases (`BUILD SUCCESSFUL in 7s`).
- **Clean-Room Regression**:
  - Full clean-room test suite executed with zero regressions.

## ASPICE Traceability
- **Requirement**: `REQ-UI-340` in `docs/requirements.md` (Verified)
- **Test Specification**: `TST-UI-300` in `docs/tests.md` (Verified)
- **Implementation Plan**: `docs/engineering/plans/ATT-3108_plan.md`
- **Analysis**: `docs/engineering/analysis/ATT-3108_analysis.md`
