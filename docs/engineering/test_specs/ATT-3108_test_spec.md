# Stage 2: Requirement & Test Specification: ATT-3108

## Metadata
* **Ticket ID**: ATT-3108
* **Title**: LiveSegmentSheet crashes with NumberFormatException attempting to parse formatted distance string on segment finish
* **Parent Epic**: ATT-2582 (Segments: Live Tracking, Exploration & Route Integration)
* **Author**: Antigravity (Pair Programming Assistant)
* **Date**: 2026-10-10
* **Requirement ID**: `REQ-UI-340`
* **Test Specification ID**: `TST-UI-300`
* **Status**: Specified

---

## 1. Requirement Specification (REQ-UI-340)

### 1.1 Summary
The system SHALL evaluate the live segment completion scrub distance using the canonical unformatted numeric meter metric (`summary.distance_raw`) instead of parsing the localized formatted string representation (`summary.distance`), preventing fatal `NumberFormatException` upon segment finish in `LiveSegmentSheet.kt` (ATT-3108, superseding the evaluation formula in `REQ-UI-331`):

1. **Type-Safe Scrub Distance Evaluation on Finish (`LIveSegmentSheet.kt`)**:
   - When `liveSegment.liveData.segmentStatus` is `LiveSegmentStatus.FINISHED`, `LiveSegmentSheet` SHALL evaluate `externalScrubDistance` as `liveSegment.staticData.summary.distance_raw.coerceAtLeast(liveSegment.liveData.distanceOnSegment_raw)` without performing any string-to-numeric parsing (`toDouble()`).
   - When `liveSegment.liveData.segmentStatus` is `LiveSegmentStatus.ON_SEGMENT` or `LiveSegmentStatus.ON_SEGMENT_CLOSE_TO_FINISH`, it SHALL evaluate to `liveSegment.liveData.distanceOnSegment_raw`.
   - When `liveSegment.liveData.segmentStatus` is `LiveSegmentStatus.APPROACHING` or `LiveSegmentStatus.FAR_FAR_AWAY`, it SHALL evaluate to `null`.
2. **Contract & Layout Integrity (`LiveSegmentSheetContractTest.kt`, `LiveSegmentSheetLayoutTest.kt`)**:
   - Contract tests SHALL enforce `distance_raw` evaluation on `LiveSegmentStatus.FINISHED`.
   - Unit tests SHALL verify that when `SegmentSummary` has formatted distance strings (such as `"766 m"` or `"1.2 km"`), transitioning to `FINISHED` completes cleanly without throwing `NumberFormatException` and correctly resolves `externalScrubDistance` to `distance_raw`.
3. **Preservation of System Invariants**:
   - Bottom sheet wrap-content height (`REQ-UI-270`), suppressed map and zoom controls (`REQ-UI-197`), unified surface background (`REQ-UI-196`), elevation scrubber pin alignment (`REQ-UI-331`), and 100% full-suite unit test pass rate MUST be strictly preserved.

### 1.2 Chesterton's Fence Audit
* **Historical Precedent**: Introduced in Sprint 2026-40.16 (`ATT-2967`, commit `54fb2b23`).
* **Root Reason for Existing Formulation**: In ATT-2967, `externalScrubDistance` was mapped to `summary.distance.toDouble()` under the mistaken assumption that `summary.distance` was numeric. In reality, `summary.distance` is the localized, formatted string representation (e.g. `"766 m"` or `"1.2 km"`), while `summary.distance_raw` is the canonical unformatted numeric meter `Double`. Calling `toDouble()` caused an immediate fatal `NumberFormatException` upon segment finish.
* **Preservation of Intent**: Elevation profile position pinning at the finish line, wrap-content sheet layout, and 100% test pass rate remain preserved.

---

## 2. Test Specification (TST-UI-300)

### 2.1 Test Categories & Verification Scope

| Test ID | Category | Target File | Verification Scope |
| :--- | :--- | :--- | :--- |
| **TST-UI-300.1** | Contract Verification | `LiveSegmentSheetContractTest.kt` | Verify that `LIveSegmentSheet.kt` maps `LiveSegmentStatus.FINISHED` to `summary.distance_raw.coerceAtLeast(liveData.distanceOnSegment_raw)` without calling `toDouble()`. |
| **TST-UI-300.2** | Unit Test (Formatted String Safety) | `LiveSegmentSheetLayoutTest.kt` | Verify that evaluating `externalScrubDistance` with `LiveSegmentStatus.FINISHED` and `summary.distance = "766 m"` (`distance_raw = 766.0`) completes without `NumberFormatException` and returns `766.0`. |
| **TST-UI-300.3** | Unit Test (Comma Decimal Formatting) | `LiveSegmentSheetLayoutTest.kt` | Verify that evaluating `externalScrubDistance` with `summary.distance = "1,2 km"` (`distance_raw = 1200.0`) completes cleanly without parsing errors. |
| **TST-UI-300.4** | Clean-Room Full Suite Regression | Full Unit Test Suite | Execute `./gradlew testDebugUnitTest` asserting 100% pass rate with zero regressions. |

---

## 3. Acceptance Criteria (Given-When-Then)

### Scenario 1: Live Segment Completion without Crash
* **Given** an active live segment in `LiveSegmentSheet` with formatted `distance = "766 m"` and `distance_raw = 766.0`,
* **When** the segment status transitions to `LiveSegmentStatus.FINISHED`,
* **Then** `LiveSegmentSheet` calculates `externalScrubDistance` as `766.0` using `distance_raw` without throwing `NumberFormatException`.

### Scenario 2: Elevation Profile Marker Alignment at Finish
* **Given** a finished live segment,
* **When** the elevation profile renders in `LiveSegmentSheet`,
* **Then** the elevation profile marker pin rests at the segment finish distance (`summary.distance_raw`).

---

## 4. Invariant Protection

1. **Zero String Parsing**: No UI-formatted string is parsed as a number.
2. **Type Safety**: Unformatted domain fields (`Double`) are directly referenced.
3. **Clean-Room Test Pass Rate**: Full test suite must maintain 100% pass rate.
