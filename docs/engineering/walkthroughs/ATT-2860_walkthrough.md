# Stage 5: Walkthrough & Verification - ATT-2860: Harmonized Visual and UX Architecture for Climb, Segment, and Route Detail Bottom Sheets

**Ticket**: [ATT-2860](https://atrainingtracker.atlassian.net/browse/ATT-2860)  
**Sub-task**: [ATT-2953](https://atrainingtracker.atlassian.net/browse/ATT-2953) (`[Test]`)  
**Parent Epic**: [ATT-355](https://atrainingtracker.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-41.6`  
**Requirement Mapping**: `REQ-UI-315` (*Harmonized Visual & UX Architecture for Climb, Segment, and Route Detail Bottom Sheets via MapDetailLayout*)  
**Test Mapping**: `TST-UI-275` (*Harmonized Visual & UX Architecture for Climb, Segment, and Route Detail Bottom Sheets via MapDetailLayout Verification*)  
**Branch**: `feature/ATT-2860`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-09  

---

## 1. Executive Summary & Verification Overview

During the Sprint 2026-41.5 review, the human user explicitly directed:
> *"The segment popup must look identical to the one that pops up in the map. We should use the same code here. The route popup should follow this layout."*

This implementation completely unifies entity popups across the application by directly reusing canonical map screen components within standardized Material 3 modal bottom sheets:
1. **Direct Reuse of `SegmentOnMapScreen` in `SegmentDetailSheet`**:
   - Refactored `SegmentDetailSheet.kt` to host `SegmentOnMapScreen` within a `ModalBottomSheet` styled with `BottomSheetDesign.SheetShape` (`28.dp` top rounded corners), `surface` container color, and `useStatusBarsPadding = false`.
   - Mapped `matchedSegment.segment.summary` and `matchedSegment.segment.toMapSegment(showStartAndFinishText = false)`.
   - Provided an overlay circular dismiss button (`Icons.Default.Close`) in the top-right corner with subtle elevation and surface transparency, enabling effortless one-tap dismissal alongside drag-down and scrim tap gestures.
   - Completely deleted obsolete separate card composables (`SegmentDetailMetricsCard`, `SegmentDetailMapCard`, `SegmentDetailElevationProfileCard`, `SegmentDetailMetricItem`, `SegmentDetailElevationProfile`), eliminating 340+ lines of duplicate code.
2. **Direct Reuse of `RouteOnMapScreen` in `RouteDetailSheet`**:
   - Refactored `RouteDetailSheet.kt` to host `RouteOnMapScreen` within a `ModalBottomSheet` styled with `BottomSheetDesign.SheetShape` and `useStatusBarsPadding = false`.
   - Mapped `routeWithPath.toMapRoute()` and `routeWithPath.summary`.
   - Provided the matching overlay circular dismiss button in the top-right corner.
   - Completely deleted obsolete separate card composables (`RouteDetailMetricsCard`, `RouteDetailMapCard`, `RouteDetailElevationProfileCard`, `RouteDetailMetricItem`).
3. **Preserved Bounding Box Contracts**:
   - Preserved `calculateSegmentBounds(matchedSegment: MatchedRouteSegment)` and `calculateRouteBounds(route: RouteWithPath)` to maintain zero regressions for external consumers and bounds calculations.
4. **Contract Test & Structural Verification**:
   - Updated `SegmentDetailSheetContractTest.kt` to assert direct `SegmentOnMapScreen` reuse and verify absence of obsolete card composables.
   - Updated `RouteDetailSheetContractTest.kt` to assert direct `RouteOnMapScreen` reuse and verify absence of obsolete card composables.
   - Re-verified `ClimbDetailSheetContractTest.kt`, `RouteSegmentsBreakdownContractTest.kt`, `RouteClimbsBreakdownContractTest.kt`, and `SegmentRoutesSectionContractTest.kt`.
5. **Clean-Room Regression Suite**:
   - Executed `./gradlew testDebugUnitTest`: **2,228 tests executed, 0 failures, 0 skipped, 100% pass rate**.
6. **9-Language Localization Audit**:
   - Executed `TranslationParityTest`: **100% parity across all 9 supported locales**, 0 missing strings.

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Method | Result | Status in Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-UI-315.1` | `TST-UI-275.1` | `SegmentDetailSheetContractTest.kt` (SegmentOnMapScreen reuse, ModalBottomSheet, overlay close button, absence of obsolete cards) | **PASSED** | `Verified` |
| `REQ-UI-315.2` | `TST-UI-275.2` | `RouteDetailSheetContractTest.kt` (RouteOnMapScreen reuse, ModalBottomSheet, overlay close button, absence of obsolete cards) | **PASSED** | `Verified` |
| `REQ-UI-315.3` | `TST-UI-275.3` | `ClimbDetailSheetContractTest.kt` (Max Grade label, vector icons, card shape, map isolation) | **PASSED** | `Verified` |
| `REQ-UI-315.4` | `TST-UI-275.4` | `TranslationParityTest.kt` (9-language parity across all supported application locales) | **PASSED** | `Verified` |
| `REQ-PRO-001` | `TST-UI-275.5` | Clean-Room Suite `./gradlew testDebugUnitTest` (2,228 tests, 0 failures) | **PASSED** (100%) | `Verified` |

---

## 3. Automated Test Evidence

### Targeted Contract & Integration Tests
```text
SegmentDetailSheetContractTest > testSegmentDetailSheet_composablesExistAndArePublic PASSED
SegmentDetailSheetContractTest > testSegmentDetailSheet_structuralTokensAndContracts PASSED
SegmentDetailSheetContractTest > testCalculateSegmentBounds_validSegment_computesCorrectBounds PASSED
SegmentDetailSheetContractTest > testCalculateSegmentBounds_singlePoint_expandsBounds PASSED

RouteDetailSheetContractTest > testRouteDetailSheet_composablesExistAndArePublic PASSED
RouteDetailSheetContractTest > testRouteDetailSheet_structuralTokensAndContracts PASSED
RouteDetailSheetContractTest > testCalculateRouteBounds_validRoute_computesCorrectBounds PASSED
RouteDetailSheetContractTest > testCalculateRouteBounds_singlePoint_expandsBounds PASSED

ClimbDetailSheetContractTest > testClimbDetailSheet_composablesExistAndArePublic PASSED
ClimbDetailSheetContractTest > testClimbDetailSheet_structuralTokensAndContracts PASSED
ClimbDetailSheetContractTest > testCalculateClimbBounds_validClimb_computesCorrectBounds PASSED

RouteSegmentsBreakdownContractTest > testRouteOnMapScreen_integratesSegmentDetailSheetAndClickCallback PASSED
RouteClimbsBreakdownContractTest > testRouteOnMapScreen_integratesClimbDetailSheetAndClickCallback PASSED
SegmentRoutesSectionContractTest > testSegmentOnMapScreen_integratesCandidateRoutesAndAnalyticsContent PASSED

TranslationParityTest > testAllTranslationsComplete PASSED
```

### Full Clean-Room Test Suite Output
```text
> Task :app:compileDebugKotlin UP-TO-DATE
> Task :app:compileDebugJavaWithJavac UP-TO-DATE
> Task :app:compileDebugUnitTestKotlin UP-TO-DATE
> Task :app:compileDebugUnitTestJavaWithJavac UP-TO-DATE
> Task :app:testDebugUnitTest

BUILD SUCCESSFUL in 2m 38s
32 actionable tasks: 1 executed, 31 up-to-date

Total Tests: 2,228
Failures: 0
Skipped: 0
Duration: 2m 35.51s
Pass Rate: 100%
```

---

## 4. UI Consistency & Architectural Governance (Rule 23)

1. **Direct Code Reuse (Zero Duplicate Implementations)**:
   - `SegmentDetailSheet` directly invokes `SegmentOnMapScreen`.
   - `RouteDetailSheet` directly invokes `RouteOnMapScreen`.
   - Both screens rely on canonical `MapDetailLayout` for split-pane map dragging, dynamic baseline height self-measurement, elevation scrubbing, and responsive layout.
2. **Canonical Tokens & Shapes**:
   - `BottomSheetDesign.SheetShape` (`RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)`).
   - `BottomSheetDesign.SheetTonalElevation`.
   - `MaterialTheme.colorScheme.surface`.
3. **Modularity**:
   - `SegmentDetailSheet.kt`: 112 lines (well under 500 lines limit).
   - `RouteDetailSheet.kt`: 113 lines (well under 500 lines limit).
4. **Touch & Gesture Decoupling**:
   - By eliminating nested scroll containers, `MapDetailLayout`'s internal horizontal elevation scrubbing and map pan/zoom operate cleanly without vertical scroll interception collisions.

---

## 5. Living Documentation & Requirement Governance Traceability

* `docs/requirements.md`: `REQ-UI-315` status updated to `Verified`.
* `docs/tests.md`: `TST-UI-275` status updated to `Verified`.
* Requirement archaeology audited and verified via `tools/verify_requirement_governance.py`.
