# Stage 5 Walkthrough: ATT-2967 - Display current athlete position marker on Live Segment elevation profile graph

**Ticket**: [ATT-2967](https://atrainingtracker.atlassian.net/browse/ATT-2967)  
**Sub-task**: [ATT-3038](https://atrainingtracker.atlassian.net/browse/ATT-3038) (`[Test]`)  
**Parent Epic**: [ATT-2582](https://atrainingtracker.atlassian.net/browse/ATT-2582) (*Segments: Live Tracking, Exploration & Route Integration*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `Sprint 2026-41.6`  
**Branch**: `feature/ATT-2967`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-10  

---

## 1. Executive Summary & Verification Outcome

All requirements of `REQ-UI-331` and test specifications under `TST-UI-291` have been implemented and verified.
Real-time athlete location along an active segment is evaluated in `LiveSegmentSheet.kt` and passed via `externalScrubDistance` to `MapDetailLayout.kt`, rendering the position pin/dot dynamically on the elevation profile chart while preserving all bottom sheet height and map suppression invariants.

---

## 2. Test Execution & Regression Results

### 2.1 Targeted Contract & Component Tests
- `com.atrainingtracker.trainingtracker.ui.segments.LiveSegmentSheetContractTest`: **PASSED** (all contract assertions verified).
- `com.atrainingtracker.trainingtracker.ui.segments.LiveSegmentSheetLayoutTest`: **PASSED**.

### 2.2 Clean-Room Full Test Suite Regression
- Command: `./gradlew testDebugUnitTest`
- Result: **BUILD SUCCESSFUL in 2m 3s**
- Total Tests: **2,284 tests passed**, **0 failed**, **0 regressions**.

---

## 3. Architecture & Code Changes

1. **`LIveSegmentSheet.kt`**:
   - Computes `externalScrubDistance` based on `liveSegment.liveData.segmentStatus`:
     - `ON_SEGMENT` / `ON_SEGMENT_CLOSE_TO_FINISH` -> `distanceOnSegment_raw`
     - `FINISHED` -> `summary.distance.coerceAtLeast(distanceOnSegment_raw)`
     - `APPROACHING` / `FAR_FAR_AWAY` -> `null`
   - Forwards `externalScrubDistance` to `MapDetailLayout`.

2. **`MapDetailLayout.kt`**:
   - Synchronizes `selectedDistance = externalScrubDistance` unconditionally without null gating, allowing clean clearance when returning to approaching or reset.

3. **Living Documentation & Governance**:
   - `docs/requirements.md`: `REQ-UI-331` updated to `Verified`.
   - `docs/tests.md`: `TST-UI-291` updated to `Verified`.

---

## 4. Invariants & Preservations Check

- [x] Clamped wrap-content height without viewport expansion (`REQ-UI-270`, `TST-UI-229`).
- [x] Suppressed map (`showMap = false`) and zoom controls (`showZoomControls = false`) (`REQ-UI-197`).
- [x] Unified surface background (`MaterialTheme.colorScheme.surface`) (`REQ-UI-196`).
- [x] Zero regressions across 2,284 unit tests.

---

## 5. Recommendation

**RECOMMEND PASS**: Advance `ATT-3038` to `Erledigt` via audit, merge `feature/ATT-2967` into `sprint/2026-41.6`, assign fixVersion `V4.9.39`, and transition `ATT-2967` to `Final Review (Human)`.
