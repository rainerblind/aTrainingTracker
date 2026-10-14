# Stage 4: Implementation - ATT-3364: Harmonize live segment and live climb bottom sheets under unified sheet layout

**Ticket**: [ATT-3364](https://rainerblind.atlassian.net/browse/ATT-3364)  
**Sub-task**: [ATT-3558](https://rainerblind.atlassian.net/browse/ATT-3558) (`[Implementation]`)  
**Parent Epic**: [ATT-431](https://rainerblind.atlassian.net/browse/ATT-431) (*Clean Code*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-42.3`  
**Requirement Mapping**: `REQ-UI-366`  
**Test Spec Mapping**: `TST-UI-329`  
**Branch**: `feature/ATT-3364`  
**Author**: AI Agent 1 (Software Construction Engineer)  
**Date**: 2026-10-14  

---

### Requirement Archaeology & Chesterton's Fence Audit

1. **Original Requirement ID & Target**:
   - `REQ-UI-366` (*Harmonized Live Segment and Live Climb Tracking Bottom Sheet Architecture & Container (Rule 36)*), complementing `REQ-MAP-027`, `REQ-UI-196`, `REQ-UI-270`, `REQ-UI-331`, and `REQ-UI-340`.
2. **Historical Origin & Commit Trace**:
   - `LIveSegmentSheet.kt` was authored in Sprint 2026-40.1 (`ATT-1644` / `ATT-1735`) with a capital `I` casing typo in git.
   - `LiveClimbSheet.kt` was introduced in Sprint 2026-40.14 (`ATT-1281`, commit `ceaf4070`).
   - Mounting in `SensorGridScreen.kt:284-325` duplicated 40 lines of `Box` containers, contour styling, surface background, and `clickable { expand() }` logic. In addition, the climb block inadvertently omitted `.wrapContentHeight()`.
3. **Root Reason for Existing Formulation**:
   - Divergent branches developed independently across sprints, with developers copying the outer wrapper without consolidating into a reusable container.
4. **Preservation of Core Invariants**:
   - Visual Precedence: Segment takes priority over climb when both are active (`REQ-MAP-027`).
   - Clamped Sheet Height: Sheets wrap content height cleanly (`REQ-UI-270`).
   - Live Rider Pins: Elevation profile pins remain fully dynamic and synchronized (`REQ-UI-331`).
   - Clean-Room Tests: 100% test pass rate maintained.

---

## 1. Summary of Changes

In accordance with the approved Stage 3 Implementation Plan for **ATT-3364**, this implementation delivers:

1. **Filename Normalization via `git mv`**:
   - Renamed `app/src/main/java/com/atrainingtracker/trainingtracker/ui/segments/LIveSegmentSheet.kt` to `LiveSegmentSheet.kt` enforcing standard PascalCase across git and Kotlin compiler outputs.
   - Updated reflection class lookup to `LiveSegmentSheetKt` and updated file references in `LiveSegmentSheetLayoutTest.kt`, `LiveSegmentSheetContractTest.kt`, and `MapDetailLayoutNavigationBarsPaddingTest.kt`.

2. **Creation of Reusable `LiveTrackingSheetContainer`**:
   - Authored `app/src/main/java/com/atrainingtracker/trainingtracker/ui/components/core/LiveTrackingSheetContainer.kt`.
   - Encapsulates `.fillMaxWidth().wrapContentHeight().sheetContour().background(MaterialTheme.colorScheme.surface, shape = BottomSheetDesign.SheetShape)` and partially expanded `clickable(onClick = onExpand)` gesture handling into a single, authoritative container adhering to **Rule 36 (Single Source of Truth & DRY)**.

3. **Cockpit Scaffold Consolidation in `SensorGridScreen.kt`**:
   - Replaced duplicate `Box` blocks with a single invocation of `LiveTrackingSheetContainer`.
   - Preserved active entity selection and priority (`showLiveSegments && activeSegment != null` prioritized over `activeLiveClimb`).

4. **Automated Unit & Contract Tests**:
   - Created `app/src/test/java/com/atrainingtracker/trainingtracker/ui/components/core/LiveTrackingSheetContainerTest.kt` verifying composable existence, modifier contracts, contour boundary stroke, and design tokens.
   - Updated `LiveSegmentSheetContractTest.kt` and `LiveSegmentSheetLayoutTest.kt` to assert compliance with `LiveTrackingSheetContainer`.

---

## 2. File Changes Breakdown

| Target File | Status | Description |
| :--- | :--- | :--- |
| `ui/segments/LiveSegmentSheet.kt` | Renamed | Normalized filename from `LIveSegmentSheet.kt` to `LiveSegmentSheet.kt` via `git mv`. |
| `ui/components/core/LiveTrackingSheetContainer.kt` | New | Shared reusable container composable encapsulating contour, shape, surface background, wrapContentHeight, and click-to-expand. |
| `ui/tracking/tracking/SensorGridScreen.kt` | Modified | Refactored `sheetContent` to host `LiveTrackingSheetContainer`, excising duplicate `Box` wrappers. |
| `ui/components/core/LiveTrackingSheetContainerTest.kt` | New | Contract and token verification test suite for `LiveTrackingSheetContainer`. |
| `ui/segments/LiveSegmentSheetContractTest.kt` | Modified | Updated path to `LiveSegmentSheet.kt` and container wrapContentHeight assertion to verify `LiveTrackingSheetContainer`. |
| `ui/segments/LiveSegmentSheetLayoutTest.kt` | Modified | Updated reflection check to `LiveSegmentSheetKt`, path to `LiveSegmentSheet.kt`, and surface background contract to check `LiveTrackingSheetContainer`. |
| `ui/map/MapDetailLayoutNavigationBarsPaddingTest.kt` | Modified | Updated path from `LIveSegmentSheet.kt` to `LiveSegmentSheet.kt`. |

---

## 3. Targeted Test Results

Targeted tests executed via `./gradlew testDebugUnitTest --tests "*LiveTrackingSheetContainerTest*" --tests "*LiveSegmentSheet*" --tests "*LiveClimbSheet*" --tests "*MapDetailLayout*"`:
- `LiveTrackingSheetContainerTest`: 3/3 passed (100%)
- `LiveSegmentSheetLayoutTest`: 5/5 passed (100%)
- `LiveSegmentSheetContractTest`: 5/5 passed (100%)
- `LiveClimbSheetLayoutTest`: 6/6 passed (100%)
- `MapDetailLayoutNavigationBarsPaddingTest`: 4/4 passed (100%)
- Overall targeted suite: **23/23 tests passed** (0 failures).
