# Stage 5 Walkthrough - ATT-3364: Harmonize live segment and live climb bottom sheets under unified sheet layout

**Ticket**: [ATT-3364](https://rainerblind.atlassian.net/browse/ATT-3364)  
**Stage 5 Subtask**: [ATT-3559](https://rainerblind.atlassian.net/browse/ATT-3559) (`[Test]`)  
**Parent Epic**: [ATT-431](https://rainerblind.atlassian.net/browse/ATT-431) (*Clean Code*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-42.3`  
**Requirement Mapping**: `REQ-UI-366` (`Verified`)  
**Test Mapping**: `TST-UI-329` (`Verified`)  
**Branch**: `feature/ATT-3364`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-14  

---

## 1. Executive Summary & Implementation Details

During active tracking in the cockpit, Strava Live Segments and Live Climbs (ClimbPro) pop up as interactive bottom sheets at the bottom of the cockpit tabs. However, their internal implementations and mounting logic had diverged into parallel code paths:
1. `LIveSegmentSheet.kt` had a casing typo in git tracking (capital `I` instead of standard PascalCase).
2. In `SensorGridScreen.kt:284-325`, both sheets were mounted inside duplicate conditional `Box` wrappers with identical `.fillMaxWidth().wrapContentHeight().sheetContour().background(...)` modifier chains and identical `clickable { expand() }` logic. Additionally, `LiveClimbSheet` inadvertently omitted `.wrapContentHeight()`.

ATT-3364 harmonizes the bottom sheet architecture in accordance with **Rule 36 (Single Source of Truth & DRY — Shared Code Over Duplication)**, standardizes file naming via `git mv`, and extracts a reusable container composable (`LiveTrackingSheetContainer`) while preserving all existing system invariants.

### Key Architectural Changes & Refactorings

1. **Filename Normalization via `git mv` (`LiveSegmentSheet.kt`)**:
   - Renamed `app/src/main/java/com/atrainingtracker/trainingtracker/ui/segments/LIveSegmentSheet.kt` to `LiveSegmentSheet.kt` enforcing standard PascalCase across git and Kotlin compiler outputs.
   - Updated reflection class lookups to `LiveSegmentSheetKt` and updated file references in `LiveSegmentSheetLayoutTest.kt`, `LiveSegmentSheetContractTest.kt`, and `MapDetailLayoutNavigationBarsPaddingTest.kt`.

2. **Creation of Reusable `LiveTrackingSheetContainer`**:
   - Authored `app/src/main/java/com/atrainingtracker/trainingtracker/ui/components/core/LiveTrackingSheetContainer.kt`.
   - Encapsulates `.fillMaxWidth().wrapContentHeight().sheetContour().background(MaterialTheme.colorScheme.surface, shape = BottomSheetDesign.SheetShape)` and partially expanded `clickable(onClick = onExpand)` gesture handling into a single, authoritative container adhering to **Rule 36 (Single Source of Truth & DRY)**.

3. **Cockpit Scaffold Consolidation in `SensorGridScreen.kt`**:
   - Consolidated duplicate `Box` blocks in `sheetContent` into a single invocation of `LiveTrackingSheetContainer`.
   - Preserved active entity selection and priority (`showLiveSegments && activeSegment != null` prioritized over `activeLiveClimb` per `REQ-MAP-027` Section 4).

4. **Automated Unit & Contract Tests**:
   - Authored `app/src/test/java/com/atrainingtracker/trainingtracker/ui/components/core/LiveTrackingSheetContainerTest.kt` verifying composable existence, modifier contracts, contour boundary stroke, and design tokens.
   - Updated `LiveSegmentSheetContractTest.kt` and `LiveSegmentSheetLayoutTest.kt` to assert compliance with `LiveTrackingSheetContainer`.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-UI-366` (*Harmonized Live Segment and Live Climb Tracking Bottom Sheet Architecture & Container (Rule 36)*) $\to$ **Status: Verified**
* **Test Mapping**: `TST-UI-329` (*Harmonized Live Segment and Live Climb Tracking Bottom Sheet Architecture & Container Verification*) $\to$ **Status: Verified**

---

## 3. Preserved Invariants & Invariant Verification

1. **Rule 36 (Single Source of Truth & DRY)**: Single container composable (`LiveTrackingSheetContainer`) handles sheet contour, surface styling, and expansion gesture logic for both Live Segments and Live Climbs.
2. **Visual Precedence Invariant (`REQ-MAP-027` Section 4)**: Active Live Segment takes priority over Live Climb when both occur simultaneously.
3. **Clamped Content Height (`REQ-UI-270`)**: Both sheets enforce `.wrapContentHeight()` preventing unwanted full-viewport expansion when elevation profiles render without map.
4. **Dynamic Position Marker Invariant (`REQ-UI-331`, `REQ-UI-340`)**: Elevation profile position pins remain fully synchronized with live rider telemetry.
5. **Clean-Room Regression Free**: Full unit test suite passes 100% with zero failures across 2,675+ tests.

---

## 4. Test Verification Results

### Unit & Contract Test Suites

| Test Suite | Description | Result |
| :--- | :--- | :--- |
| `LiveTrackingSheetContainerTest` | Verifies container composable existence, modifiers, contouring, and design tokens | **PASS** (3/3 tests) |
| `LiveSegmentSheetLayoutTest` | Verifies `LiveSegmentSheetKt` reflection, design tokens, zoom suppression, and surface background contracts | **PASS** (5/5 tests) |
| `LiveSegmentSheetContractTest` | Verifies PascalCase file path, wrapContentHeight forwarding, map suppression, container integration, and dynamic distance evaluation | **PASS** (5/5 tests) |
| `LiveClimbSheetLayoutTest` | Verifies `LiveClimbSheetKt` reflection, elevation profile embedding, badges, typography, and localization | **PASS** (6/6 tests) |
| `MapDetailLayoutNavigationBarsPaddingTest` | Verifies navigationBarsPadding contracts and updated `LiveSegmentSheet.kt` path | **PASS** (4/4 tests) |
| `BottomSheetVisualContractTest` | Verifies bottom sheet design tokens, shape, container color, and contour application | **PASS** |
| `TrackingTabWysiwygContractTest` | Verifies bottom sheet scaffold gating strictly to `ScreenMode.TRACKING` | **PASS** |

### Clean-Room Full Suite Regression Run
* Command: `./gradlew testDebugUnitTest`
* Result: **100% Tests Passed** (2,675+ tests completed, 0 failures, 0 errors, 0 regressions).
