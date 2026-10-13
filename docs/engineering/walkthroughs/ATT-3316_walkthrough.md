# Stage 5: Walkthrough & Verification Report - ATT-3316: Scale LiveClimbSheet elevation profile height adaptively based on climb elevation gain

**Ticket**: [ATT-3316](https://rainerblind.atlassian.net/browse/ATT-3316)  
**Sub-task**: [ATT-3396](https://rainerblind.atlassian.net/browse/ATT-3396) (`[Test]`)  
**Parent Epic**: [ATT-2582](https://rainerblind.atlassian.net/browse/ATT-2582) (*Segments: Live Tracking, Exploration & Route Integration*)  
**Target Release**: `Sprint 2026-42.1`  
**Active Sprint**: `Sprint 2026-42.1`  
**Requirement Mapping**: `REQ-UI-352`  
**Test Mapping**: `TST-UI-312`  
**Branch**: `feature/ATT-3316`  
**Author**: AI Agent 1 (Verification & Release Quality Engineer)  
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

## 1. Executive Summary

This Stage 5 Verification Report validates the successful implementation of **ATT-3316**:
1. Hardcoded `fixedHeight = 68.dp` has been excised from `LiveClimbSheet.kt`.
2. `ElevationProfile` defaults to adaptive canvas height evaluation via `calculateElevationProfileHeight(range)`, ensuring that climb elevation profiles scale smoothly from $70\text{dp}$ (flat/small rollers) up to $200\text{dp}$ (HC alpine climbs) according to vertical relief ($\Delta H$).
3. All design system invariants (slope gradient shading, `MetricBadge` chips, Compose `@Preview` states, degenerate geometry safeguards) remain 100% intact.
4. Living documents (`docs/requirements.md` for `REQ-UI-352` and `docs/tests.md` for `TST-UI-312`) are updated to `Verified`.

---

## 2. Verification Matrix

| Test Suite / Verification Item | Target Scope | Result | Status |
| :--- | :--- | :--- | :--- |
| `LiveClimbAdaptiveHeightTest` | Adaptive height formula across climb categories & climb bounds calculation | 3/3 passed | **PASS** |
| `LiveClimbSheetLayoutTest` | Layout contract, no hardcoded fixedHeight, MetricBadge delegation, Compose Previews | 6/6 passed | **PASS** |
| `ElevationProfileClimbShadingTest` | Grade color mapping bands & degenerate geometry guards | 4/4 passed | **PASS** |
| `ElevationProfileLayoutTest` | ElevationProfile composable canvas sizing & layout constraints | Passed | **PASS** |
| Full Regression Suite (`./gradlew testDebugUnitTest`) | Entire application unit test suite | 100% pass rate (2,525+ tests) | **PASS** |

---

## 3. Detailed Verification Results

### 3.1 Adaptive Height Formula Verification (`LiveClimbAdaptiveHeightTest`)
- Verified that `calculateElevationProfileHeight(range)` scales monotonically according to the canonical curved progression:
  - Range $0.0\text{ m} \to 70\text{dp}$ (minimum floor)
  - Range $20.0\text{ m} \to 82\text{dp}$ (minor roller)
  - Range $60.0\text{ m} \to 94\text{dp}$ (Category 4 climb)
  - Range $150.0\text{ m} \to 111\text{dp}$ (Category 3 climb)
  - Range $300.0\text{ m} \to 133\text{dp}$ (Category 2 climb)
  - Range $600.0\text{ m} \to 165\text{dp}$ (Category 1 climb)
  - Range $1000.0\text{ m} \to 200\text{dp}$ (Hors Catégorie climb, ceiling)
  - Range $1500.0\text{ m} \to 200\text{dp}$ (clamped to 200dp max)
- Verified `calculateElevationBounds` calculates range accurately for climb polyline path points and feeds into `calculateElevationProfileHeight`.

### 3.2 Layout Contract & Absence of Hardcoded Fixed Height
- Verified that `LiveClimbSheet.kt` does NOT declare `fixedHeight = 68.dp` or any other fixed height constraint on `ElevationProfile`.
- Verified that `climbGradientShading = true` remains active.
- Verified that `ClimbCategoryChip` and `ClimbStatusBadge` delegate to `MetricBadge`.

---

## 4. Visual Consistency & Compose Previews Audit (Rule 23)

In accordance with Rule 23 and Design Guidelines §2.3:
- The Live Climb cockpit sheet integrates into `SensorGridScreen`'s `BottomSheetScaffold`.
- The scaffold container uses `wrapContentHeight()`, seamlessly expanding to host the dynamically sized profile ($70\text{dp} \dots 200\text{dp}$) without visual clipping.
- The Compose Previews (`LiveClimbSheet_OnClimb_Preview`, `LiveClimbSheet_OnClimb_Dark_Preview`, `LiveClimbSheet_Approaching_Preview`, `LiveClimbSheet_Finished_Preview`) compile and render with full theme fidelity.

---

## 5. Governance & Integration

- Subtask `ATT-3396` (`[Test]`) is transitioned to `In Überprüfung` for Gate 5 audit.
- Parent ticket `ATT-3316` is handed over to `Final Review (Human)`.
- Verified changes are integrated into `sprint/2026-42.1` via GitFlow Strategy A (`--no-ff`).
