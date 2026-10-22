# Stage 2: Requirement & Test Specification - ATT-3364: Harmonize live segment and live climb bottom sheets under unified sheet layout

**Ticket**: [ATT-3364](https://rainerblind.atlassian.net/browse/ATT-3364)  
**Sub-task**: [ATT-3548](https://rainerblind.atlassian.net/browse/ATT-3548) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-431](https://rainerblind.atlassian.net/browse/ATT-431) (*Clean Code*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-42.3`  
**Requirement Mapping**: `REQ-UI-366` (*Harmonized Live Segment and Live Climb Tracking Bottom Sheet Architecture & Container (Rule 36)*), complementing `REQ-MAP-027`, `REQ-UI-196`, `REQ-UI-270`, `REQ-UI-331`, and `REQ-UI-340`  
**Test Spec ID**: `TST-UI-329`  
**Branch**: `feature/ATT-3364`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-14  

---

## 1. Requirement Specification (REQ-UI-366)

### 1.1 Problem Statement & Rationale
Live activity tracking bottom sheets in the cockpit display real-time feedback for Strava Live Segments (`LiveSegment`) and Live Climbs (`LiveClimbData`). However, their implementations diverged across sprints:
1. `LIveSegmentSheet.kt` has an improper capitalization typo in its filename.
2. `SensorGridScreen.kt:284-325` duplicates 40 lines of `Box` container modifiers, contour styling, surface background, and `clickable { expand() }` logic for both sheets.
3. `LiveClimbSheet.kt` and `LiveSegmentSheet.kt` implement parallel layouts from scratch rather than sharing a unified container and layout scaffolding, violating **Rule 36 (Single Source of Truth & DRY)**.

### 1.2 Functional & Architectural Scope

1. **Filename Normalization**:
   - The system SHALL rename `LIveSegmentSheet.kt` to `LiveSegmentSheet.kt` with standard PascalCase.
   - All imports and reflection-based test references SHALL resolve to `LiveSegmentSheet` / `LiveSegmentSheetKt`.

2. **Unified Sheet Scaffolding & Container in Cockpit (`SensorGridScreen.kt`)**:
   - The system SHALL extract a reusable composable `LiveTrackingSheetContainer` (or unified container composable) in `SensorGridScreen.kt` (or `ui/tracking/`).
   - `LiveTrackingSheetContainer` SHALL encapsulate `.fillMaxWidth().wrapContentHeight().sheetContour().background(MaterialTheme.colorScheme.surface, shape = BottomSheetDesign.SheetShape)` and partially-expanded click-to-expand gesture logic.
   - In `SensorGridScreen.kt`, the duplicate `Box` blocks SHALL be replaced by a single unified sheet slot hosting `LiveTrackingSheetContainer`.

3. **Visual & Structural Parity**:
   - Both `LiveSegmentSheet` and `LiveClimbSheet` SHALL render with identical outer container shape (`BottomSheetDesign.SheetShape`), surface container color (`MaterialTheme.colorScheme.surface`), and canonical 0.5dp horizontal divider (`color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)`).
   - Invariant: When both an active segment and an active climb occur, `activeSegment` continues to take visual precedence, exactly preserving `REQ-MAP-027` Section 4.

4. **Preservation of System Invariants**:
   - Wrap-content sheet height (`REQ-UI-270`), dynamic peek height (`BottomSheetDesign.PeekHeightLiveSegment`), live rider progress pins on the elevation profile (`REQ-UI-331`), type-safe finish evaluation (`REQ-UI-340`), and 100% full-suite test pass rate MUST NOT be regressed.

---

## 2. Requirement Archaeology & Chesterton's Fence Audit

1. **Original Requirement ID & Target**: Net-new requirement (`REQ-UI-366`). Harmonizes and consolidates scaffolding of `REQ-MAP-027` (*Persistent Climbs Database & Live ClimbPro Cockpit Sheet*), `REQ-UI-196` (*Live Segment Bottom Sheet Design & Surface Background*), `REQ-UI-270` (*Clamped Bottom Sheet Height to Elevation Profile Content Height*), and `REQ-UI-340` (*LiveSegmentSheet Type-Safe Numeric Metric Evaluation on Segment Finish*).
2. **Historical Origin & Commit Trace**: `LIveSegmentSheet.kt` was authored in Sprint 2026-40.1 (`ATT-1644` / `ATT-1735`), refined in Sprint 2026-40.16 (`ATT-2231`, `ATT-2967`), and Sprint 2026-41.8 (`ATT-3108`). `LiveClimbSheet.kt` was introduced in Sprint 2026-40.14 (`ATT-1281`, commit `ceaf4070`).
3. **Root Reason for Existing Formulation**: `LiveClimbSheet` was developed independently of `LiveSegmentSheet` to quickly implement ClimbPro without coupling to `MapDetailLayout`. Mounting logic in `SensorGridScreen.kt` was copy-pasted to support the new entity.
4. **Preservation of Core Invariants**: Visual precedence of segments over climbs, elevation profile pin synchronization, peek heights, contour clipping, and 100% test pass rate strictly preserved.

---

## 3. Acceptance Criteria (Given-When-Then)

* **Criterion 1 (Correct File Casing)**:
  * *Given* the repository file tree in `ui/segments`,
  * *When* inspecting the live segment sheet file,
  * *Then* the file is named `LiveSegmentSheet.kt` with standard PascalCase, and git tracks it under the corrected filename.
* **Criterion 2 (Unified Sheet Mounting in Cockpit)**:
  * *Given* `SensorGridScreen.kt` rendering bottom sheet content,
  * *When* evaluating the active sheet slot for segments or climbs,
  * *Then* both entities mount via a single shared `LiveTrackingSheetContainer` composable, eliminating duplicate `Box` modifier chains and expansion lambdas.
* **Criterion 3 (Visual & Token Parity)**:
  * *Given* active segment and active climb bottom sheets in the cockpit,
  * *When* comparing sheet appearance,
  * *Then* both sheets utilize identical container backgrounds (`MaterialTheme.colorScheme.surface`), shapes (`BottomSheetDesign.SheetShape`), contour borders, and 0.5dp dividers.
* **Criterion 4 (Contract & Test Integrity)**:
  * *Given* all layout and contract tests in `LiveSegmentSheetContractTest.kt`, `LiveSegmentSheetLayoutTest.kt`, and `LiveClimbSheetLayoutTest.kt`,
  * *When* executed on pure JVM,
  * *Then* 100% of tests pass, verifying both the renamed file and the unified container.

---

## 4. Test Specification (TST-UI-329)

### 4.1 Unit & Contract Test Cases

1. **`LiveSegmentSheetContractTest.kt`**:
   - Update file path lookups from `LIveSegmentSheet.kt` to `LiveSegmentSheet.kt`.
   - Verify `wrapContentHeight` and map suppression contracts are maintained.
   - Verify `SensorGridScreen.kt` mounts `LiveSegmentSheet` inside a container that wraps content height.
   - Verify `externalScrubDistance` evaluation contracts (`REQ-UI-331`, `REQ-UI-340`) are preserved.

2. **`LiveSegmentSheetLayoutTest.kt`**:
   - Update reflection checks from `LIveSegmentSheetKt` to `LiveSegmentSheetKt`.
   - Assert `LiveSegmentSheet` composable is public and exists.
   - Assert `BottomSheetDesign.DragHandleWidth` and `DragHandleHeight` token values.

3. **`LiveClimbSheetLayoutTest.kt`**:
   - Assert `LiveClimbSheet` composable is public and exists.
   - Verify shared elevation profile, status badge, category chip, and HUD telemetry contracts remain intact.

4. **`LiveTrackingSheetContainerTest.kt`**:
   - Verify that `LiveTrackingSheetContainer` renders content, applies `.sheetContour()`, `.wrapContentHeight()`, and forwards click-to-expand in `PartiallyExpanded` state.

5. **Clean-Room Regression Suite**:
   - Run `./gradlew testDebugUnitTest` and assert 100% passing tests with zero regressions.
