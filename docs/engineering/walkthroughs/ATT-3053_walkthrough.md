# Stage 5 Walkthrough: ATT-3053 - Harmonize Climb popup visual design and architecture with Segment and Route popups using shared base layout

**Ticket**: [ATT-3053](https://atrainingtracker.atlassian.net/browse/ATT-3053)  
**Sub-task**: [ATT-3076](https://atrainingtracker.atlassian.net/browse/ATT-3076) (`[Test]`)  
**Parent Epic**: [ATT-355](https://atrainingtracker.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Active Sprint**: `Sprint 2026-41.7`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-10  

---

## 1. Executive Summary & Verification Outcome

All requirements of `REQ-UI-333` and test specifications under `TST-UI-293` have been fully constructed, verified, and audited.
In direct response to product owner feedback from the Sprint 2026-41.6 review of ATT-2860 (*"The popup for the climbs must be (alomst) similr to the popup of the segments. From my point of view, the climb popup should share some of the code form the segment (and route) popup. I.e., I think there should be a common base class..."*), the bottom sheet architecture has been unified:

1. **Shared Bottom Sheet Scaffolding (`EntityDetailSheetScaffold.kt`)**:
   - Extracted `EntityDetailSheetScaffold` to `com.atrainingtracker.trainingtracker.ui.components.core`.
   - Encapsulates `ModalBottomSheet` configured with `rememberModalBottomSheetState(skipPartiallyExpanded = true)`, `BottomSheetDesign.SheetShape` (20.dp top curvature), `BottomSheetDesign.SheetTonalElevation` (0.dp), `containerColor = MaterialTheme.colorScheme.surface`, `dragHandle = null`, and `fillMaxHeight()`.
   - Embeds the floating circular dismiss close button (`IconButton` on `CircleShape` 2.dp elevated `Surface` with `Icons.Default.Close`) anchored at `Alignment.TopEnd` with `8.dp` padding and `zIndex(10f)`.
   - Reused across `ClimbDetailSheet`, `SegmentDetailSheet`, and `RouteDetailSheet`, eliminating duplicated sheet scaffolding boilerplate.
2. **Harmonized Climb Inspection Screen (`ClimbOnMapScreen.kt` & `ClimbDetailSheet.kt`)**:
   - `ClimbDetailSheet` directly hosts `ClimbOnMapScreen` within `EntityDetailSheetScaffold`.
   - `ClimbOnMapScreen` is powered by the canonical `MapDetailLayout`, providing full-bleed collapsible map viewports, interactive `ElevationProfile` chart with scrubbing synchronization, and map pins.
   - Structured `ClimbHeader`: Displays sport icon (`BSportType.BIKE`), climb name (titleLarge, bold), `ClimbCategoryChip`, route counter badge (`routeIndex of totalRouteClimbs`), and start offset distance along route (`routes_climb_start_at`).
   - Structured `ClimbDetails`: 3-row metric presentation conforming to `SegmentDetails` design tokens (Row 1: Distance; Row 2: Average and Maximum Grade; Row 3: Ascent Elevation Gain and Min/Max Altitude).
   - `HorizontalDivider` with 0.5.dp thickness and `outlineVariant` color.
3. **Preservation of System Invariants**:
   - Retained `calculateClimbBounds(climb: Climb)` with single-point fallback.
   - Retained `ClimbDetailElevationProfile` for backward compatibility.
   - Preserved non-destructive dismissal (closing the sheet returns directly to `RouteOnMapScreen` without clearing route selection or map state).

---

## 2. Test Execution & Regression Results

### 2.1 Targeted Unit & Contract Tests
- Command: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.components.core.EntityDetailSheetScaffoldContractTest" --tests "com.atrainingtracker.trainingtracker.ui.climbs.ClimbDetailSheetContractTest" --tests "com.atrainingtracker.trainingtracker.ui.routes.RouteClimbsBreakdownContractTest" --tests "com.atrainingtracker.trainingtracker.ui.routes.RouteDetailSheetContractTest" --tests "com.atrainingtracker.trainingtracker.ui.segments.SegmentDetailSheetContractTest"`
- Result: **All tests passed cleanly in 6s**.
- Verified:
  - `EntityDetailSheetScaffoldContractTest`: Verifies composable exists, public modifier, styling tokens, close button, and its consumption across `SegmentDetailSheet`, `RouteDetailSheet`, and `ClimbDetailSheet`.
  - `ClimbDetailSheetContractTest`: Verifies `ClimbDetailSheet` delegates to `EntityDetailSheetScaffold` and `ClimbOnMapScreen`, `ClimbHeader` and `ClimbDetails` structure, `MapDetailLayout` integration, and `calculateClimbBounds` math.
  - `RouteClimbsBreakdownContractTest`: Verifies `RouteOnMapScreen` climb click callback wiring and `ClimbDetailSheet` presentation.
  - `RouteDetailSheetContractTest`: Verifies `RouteDetailSheet` delegates to `EntityDetailSheetScaffold` and `RouteOnMapScreen`.
  - `SegmentDetailSheetContractTest`: Verifies `SegmentDetailSheet` delegates to `EntityDetailSheetScaffold` and `SegmentOnMapScreen`.

### 2.2 Clean-Room Full Test Suite Regression
- Command: `./gradlew testDebugUnitTest`
- Result: **BUILD SUCCESSFUL in 2m 30s**
- Outcome: **100% pass rate**, **0 failures**, **0 errors**, **0 regressions across all 2303+ tests**.

---

## 3. Living Documentation & Governance Synchronization

- `docs/requirements.md`: `REQ-UI-333` updated to `Verified`.
- `docs/tests.md`: `TST-UI-293` updated to `Verified`.
- `tools/verify_requirement_governance.py --base-ref sprint/2026-41.7`: Verified clean pass (code 0).

---

## 4. ASPICE Traceability Matrix

| Requirement | Test Specification | Target Implementation | Test File | Status |
|---|---|---|---|---|
| `REQ-UI-333` Clause 1 | `TST-UI-293` Case 1 | `EntityDetailSheetScaffold.kt`, `SegmentDetailSheet.kt`, `RouteDetailSheet.kt`, `ClimbDetailSheet.kt` | `EntityDetailSheetScaffoldContractTest.kt`, `SegmentDetailSheetContractTest.kt`, `RouteDetailSheetContractTest.kt` | **Verified** |
| `REQ-UI-333` Clause 2 | `TST-UI-293` Case 2 | `ClimbDetailSheet.kt`, `ClimbOnMapScreen.kt` | `ClimbDetailSheetContractTest.kt` | **Verified** |
| `REQ-UI-333` Clause 3-4 | `TST-UI-293` Case 3, 4 | Localization resources & codebase | Full clean-room test suite | **Verified** |
