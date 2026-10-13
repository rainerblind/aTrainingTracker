# Stage 4: Implementation - ATT-3316: Scale LiveClimbSheet elevation profile height adaptively based on climb elevation gain

**Ticket**: [ATT-3316](https://rainerblind.atlassian.net/browse/ATT-3316)  
**Sub-task**: [ATT-3395](https://rainerblind.atlassian.net/browse/ATT-3395) (`[Implementation]`)  
**Parent Epic**: [ATT-2582](https://rainerblind.atlassian.net/browse/ATT-2582) (*Segments: Live Tracking, Exploration & Route Integration*)  
**Target Release**: `Sprint 2026-42.1`  
**Active Sprint**: `Sprint 2026-42.1`  
**Requirement Mapping**: `REQ-UI-352` (Clause 1 refinement)  
**Test Mapping**: `TST-UI-312` (Clause 3 refinement)  
**Branch**: `feature/ATT-3316`  
**Author**: AI Agent 1 (Software Construction Engineer)  
**Date**: 2026-10-13  

---

### Requirement Archaeology & Chesterton's Fence Audit

1. **Original Requirement ID & Target**:
   - `REQ-UI-352` (*LiveClimbSheet Design System Harmonization, Shared ElevationProfile Integration & Degenerate Geometry Safeguards*, `ATT-3264`), targeting `LiveClimbSheet.kt`, `ElevationProfile.kt`, and `LiveClimbSheetLayoutTest.kt`.
2. **Historical Origin & Commit Trace**:
   - Sprint 2026-40.14 (`ATT-1281`) introduced `ClimbProfileCanvas` with a fixed height (`68.dp`).
   - Sprint 2026-41.10 (`ATT-3264`, commit `04ad935c`) refactored `LiveClimbSheet` to reuse `ElevationProfile.kt` and retained `fixedHeight = 68.dp` for layout parity.
3. **Root Reason for Existing Formulation**:
   - `fixedHeight = 68.dp` was retained to prevent visual changes to bottom sheet height during the refactoring.
   - However, during Sprint 2026-41.10 Joint Review, the athlete mandated:
     *"The height of the elevation profile should also depend on the height of the climb. No exceptions here."* (Documented in `Sprint_Review_and_Retro_ATT-3318.md` under Topic 3 & Rule 36, and codified in `docs/design_guidelines.md` §2.3).
   - Constraining all climbs to 68 dp squashed gradient and altitude perception on significant climbs (500–1200 m gain), making them look identical to minor 20 m rollers.
4. **Preservation of Core Invariants**:
   - Canonical Adaptive Height: Utilize `calculateElevationProfileHeight(range)` ($70\text{dp} \dots 200\text{dp}$) from `ElevationProfile.kt` per `REQ-UI-346`.
   - Gradient Shading: Preserve `climbGradientShading = true` and `getClimbGradeColor` color mapping per `REQ-UI-352`.
   - Telemetry HUD & Badges: Preserve 32dp terrain icon, titleLarge climb name, labelLarge route counter, headlineMedium monospace grade, bold remaining distance/elevation, and `MetricBadge` delegation per `REQ-UI-328` and `REQ-UI-352`.
   - Multi-State Tooling: Preserve all Compose `@Preview` composables (`LiveClimbSheet_OnClimb_Preview`, `LiveClimbSheet_OnClimb_Dark_Preview`, `LiveClimbSheet_Approaching_Preview`, `LiveClimbSheet_Finished_Preview`) per `REQ-UI-341`.
   - Clean-Room Test Pass Rate: 100% test pass rate across all unit tests and layout contracts.

---

## 1. Summary of Changes

In accordance with the approved Stage 3 Implementation Plan for **ATT-3316**, this implementation delivers adaptive height scaling for the climb elevation profile in `LiveClimbSheet.kt`, eliminating hardcoded fixed height constraints:

1. **`LiveClimbSheet.kt` (`ui/climbs`)**:
   - Excised `fixedHeight = 68.dp` from the `ElevationProfile` composable call.
   - Allowed `ElevationProfile` to default `fixedHeight = null`, triggering canonical adaptive canvas height evaluation via `calculateElevationProfileHeight(range)`.
   - Canvas height now scales smoothly from $70\text{dp}$ (for flat or minor rolling hills $\le 20\text{m}$) up to $200\text{dp}$ (for alpine mountain passes $\ge 1000\text{m}$).

2. **`LiveClimbSheetLayoutTest.kt` (`ui/climbs`)**:
   - Updated layout contract test to assert that `LiveClimbSheet.kt` does NOT contain hardcoded `"fixedHeight"`.
   - Maintained assertions for `climbGradientShading = true` and delegation of chips/badges to `MetricBadge`.

3. **`LiveClimbAdaptiveHeightTest.kt` (`ui/climbs`)**:
   - Added unit test suite verifying:
     - Mathematical output of `calculateElevationProfileHeight(range)` across climb categories: $0\text{m} \to 70\text{dp}$, $20\text{m} \to 82\text{dp}$, $60\text{m} \to 94\text{dp}$ (Cat 4), $150\text{m} \to 111\text{dp}$ (Cat 3), $300\text{m} \to 133\text{dp}$ (Cat 2), $600\text{m} \to 165\text{dp}$ (Cat 1), $1000\text{m} \to 200\text{dp}$ (HC), $1500\text{m} \to 200\text{dp}$.
     - Automatic height calculation from `calculateElevationBounds` for multi-point climb path tracks.
     - Static contract check verifying absence of `fixedHeight` and enforcement of `climbGradientShading = true`.

---

## 2. File Changes Breakdown

| Target File | Status | Description |
| :--- | :--- | :--- |
| `app/src/main/java/.../ui/climbs/LiveClimbSheet.kt` | Modified | Removed `fixedHeight = 68.dp` parameter from `ElevationProfile` invocation. |
| `app/src/test/java/.../ui/climbs/LiveClimbSheetLayoutTest.kt` | Modified | Updated contract test to assert absence of `fixedHeight` and presence of adaptive shading. |
| `app/src/test/java/.../ui/climbs/LiveClimbAdaptiveHeightTest.kt` | Created | Unit tests verifying adaptive height math across categories and climb bounds. |
| `docs/requirements.md` | Modified | Updated `REQ-UI-352` to reflect adaptive elevation profile height scaling per Rule 36. |
| `docs/tests.md` | Modified | Updated `TST-UI-312` to reflect adaptive elevation profile height testing. |

---

## 3. Targeted Test Results

- **Command**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.climbs.*"`
- **Result**: **BUILD SUCCESSFUL** (20 tests passed, 0 failed).
  - `LiveClimbAdaptiveHeightTest`: 3/3 passed.
  - `LiveClimbSheetLayoutTest`: 6/6 passed.
  - `ElevationProfileClimbShadingTest`: 4/4 passed.
  - Other climb unit tests: 7/7 passed.
- **Related Profile Layout Command**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.map.ElevationProfileLayoutTest"`
- **Result**: **BUILD SUCCESSFUL** (all tests passed).
