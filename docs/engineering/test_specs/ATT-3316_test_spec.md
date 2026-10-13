# Stage 2: Requirement & Test Specification - ATT-3316: Scale LiveClimbSheet elevation profile height adaptively based on climb elevation gain

**Ticket**: [ATT-3316](https://rainerblind.atlassian.net/browse/ATT-3316)  
**Sub-task**: [ATT-3393](https://rainerblind.atlassian.net/browse/ATT-3393) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-2582](https://rainerblind.atlassian.net/browse/ATT-2582) (*Segments: Live Tracking, Exploration & Route Integration*)  
**Target Release**: `Sprint 2026-42.1`  
**Active Sprint**: `Sprint 2026-42.1`  
**Requirement Mapping**: `REQ-UI-352` (Clause 1 refinement)  
**Test Mapping**: `TST-UI-312` (Clause 3 refinement)  
**Branch**: `feature/ATT-3316`  
**Author**: AI Agent 1 (Requirement & Test Specification Engineer)  
**Date**: 2026-10-13  

---

### Requirement Archaeology & Chesterton's Fence Audit

1. **Original Requirement ID & Target**:
   - `REQ-UI-352` (*LiveClimbSheet Design System Harmonization, Shared ElevationProfile Integration & Degenerate Geometry Safeguards*, `ATT-3264`), targeting `LiveClimbSheet.kt`, `ElevationProfile.kt`, and `LiveClimbSheetLayoutTest.kt`.
2. **Historical Origin & Commit Trace**:
   - Sprint 2026-40.14 (`ATT-1281`) introduced `ClimbProfileCanvas` with a fixed height (`68.dp`).
   - Sprint 2026-41.10 (`ATT-3264`, commit `04ad935c`) replaced `ClimbProfileCanvas` with `ElevationProfile.kt` and retained `fixedHeight = 68.dp` for layout parity.
3. **Root Reason for Existing Formulation**:
   - `fixedHeight = 68.dp` was retained to prevent visual changes to bottom sheet height during the refactoring.
   - During Sprint 2026-41.10 Joint Review, the athlete explicitly mandated:
     *"The height of the elevation profile should also depend on the height of the climb. No exceptions here."* (ASPICE Governance Rule 36).
   - Constraining all climbs to 68 dp squashed gradient and elevation perception on significant climbs (500–1200 m gain), making them look identical to minor 20 m rollers.
4. **Preservation of Core Invariants**:
   - Canonical Adaptive Height: Utilize `calculateElevationProfileHeight(range)` ($70\text{dp} \dots 200\text{dp}$) from `ElevationProfile.kt` per `REQ-UI-346`.
   - Gradient Shading: Preserve `climbGradientShading = true` and `getClimbGradeColor` color mapping per `REQ-UI-352`.
   - Telemetry HUD & Badges: Preserve 32dp terrain icon, titleLarge climb name, labelLarge route counter, headlineMedium monospace grade, bold remaining distance/elevation, and `MetricBadge` delegation per `REQ-UI-328` and `REQ-UI-352`.
   - Multi-State Tooling: Preserve all Compose `@Preview` composables (`LiveClimbSheet_OnClimb_Preview`, `LiveClimbSheet_OnClimb_Dark_Preview`, `LiveClimbSheet_Approaching_Preview`, `LiveClimbSheet_Finished_Preview`) per `REQ-UI-341`.
   - Clean-Room Test Pass Rate: 100% test pass rate across all unit tests and layout contracts.

---

## 1. Requirement Specification (`REQ-UI-352` Clause 1 Refinement)

### 1.1 Formal Clause Definition
In `docs/requirements.md`, `REQ-UI-352` Clause 1 is refined as follows:
```markdown
1. *Shared ElevationProfile Integration & Bespoke Canvas Elimination*:
• In `ElevationProfile.kt`, the system SHALL support optional slope gradient color shading via `climbGradientShading: Boolean = false` and `segmentColorProvider: ((Double) -> Color)? = null`.
• In `ElevationProfile.kt`, the system SHALL maintain optional parameter `fixedHeight: Dp? = null`. When provided, `fixedHeight` overrides `totalCanvasHeight` to enforce a compact profile height. When `null`, `totalCanvasHeight` evaluates to `cachedData.adaptiveHeight` ($70\text{dp} \dots 200\text{dp}$) via canonical `calculateElevationProfileHeight(range)`.
• The system SHALL extract `getClimbGradeColor(gradePercent: Double): Color` as a shared public top-level function in `com.atrainingtracker.trainingtracker.ui.climbs`, mapping standard grade bands: $<3\%$ Green, $3-6\%$ Light Green, $6-9\%$ Amber, $9-12\%$ Orange, $12-20\%$ Red, $>20\%$ Dark.
• In `LiveClimbSheet.kt`, bespoke `ClimbProfileCanvas` SHALL be completely excised and replaced with `ElevationProfile(pathPoints = normalizedPoints, currentDistance = currentDistance, showZoomControls = false, climbGradientShading = true, modifier = Modifier.fillMaxWidth())`, allowing profile canvas height to scale adaptively from $70\text{dp}$ to $200\text{dp}$ based on climb elevation gain without hardcoded fixed height constraints (ATT-3316, Rule 36).
```

### 1.2 Acceptance Criteria (Given-When-Then)

#### Criterion 1: Adaptive Scaling on Live Climb Profile
- **Given** an athlete approaching or actively riding a climb with known elevation gain $\Delta H$,
- **When** `LiveClimbSheet` renders the climb elevation profile,
- **Then** `ElevationProfile` computes its canvas height adaptively via `calculateElevationProfileHeight(range)` where `range` is the vertical elevation range of the climb:
  - For small hills ($\Delta H \le 30\text{m}$), canvas height is $\approx 70\text{dp} \dots 86\text{dp}$;
  - For moderate Category 3 / 4 climbs ($\Delta H \approx 100\text{m} \dots 150\text{m}$), canvas height is $\approx 102\text{dp} \dots 112\text{dp}$;
  - For Category 2 climbs ($\Delta H \approx 300\text{m}$), canvas height is $\approx 133\text{dp}$;
  - For Category 1 climbs ($\Delta H \approx 600\text{m}$), canvas height is $\approx 166\text{dp}$;
  - For Hors Catégorie (HC) passes ($\Delta H \ge 1000\text{m}$), canvas height is $200\text{dp}$.

#### Criterion 2: Absence of Hardcoded Height Override
- **Given** `LiveClimbSheet.kt`,
- **When** inspecting the invocation of `ElevationProfile`,
- **Then** `fixedHeight` is NOT passed (evaluating to default `null`), preventing hardcoded layout constraints from overriding adaptive scaling.

#### Criterion 3: BottomSheet Content Wrapping & Stability
- **Given** an active workout tracking session in `ScreenMode.TRACKING` with `showLiveClimbs == true`,
- **When** a live climb sheet pops up for a small hill vs. an HC col,
- **Then** `BottomSheetScaffold` expands to wrap the sheet content cleanly (`wrapContentHeight()`), with no overlapping, clipping, or visual distortion of telemetry cards or sensor tiles.

#### Criterion 4: Gradient Shading & Telemetry Continuity
- **Given** an active climb displayed in `LiveClimbSheet`,
- **When** the elevation profile renders,
- **Then** slope gradient color shading (`climbGradientShading = true`), rider marker position (`currentDistance`), and all header/telemetry HUD tokens remain 100% preserved.

---

## 2. Test Specification (`TST-UI-312` Clause 3 Refinement)

### 2.1 Unit & Contract Tests (`LiveClimbSheetLayoutTest.kt`)
1. **Contract Test: `testLiveClimbSheet_elevationProfileConfiguredWithAdaptiveHeightAndGradientShading`**:
   - Verify `LiveClimbSheet.kt` does NOT contain `fixedHeight = 68.dp` or any hardcoded `fixedHeight` parameter.
   - Verify `LiveClimbSheet.kt` invokes `ElevationProfile` with `climbGradientShading = true`.
   - Verify `LiveClimbSheet.kt` invokes `ElevationProfile` with `modifier = Modifier.fillMaxWidth()`.
   - Verify `LiveClimbSheet.kt` delegates `ClimbCategoryChip` and `ClimbStatusBadge` to `MetricBadge`.

2. **Adaptive Height Calculation Verification (`LiveClimbAdaptiveHeightTest.kt`)**:
   - Verify `calculateElevationProfileHeight(range)` outputs expected heights across climb categories:
     - Range $0.0\text{m} \implies 70\text{dp}$
     - Range $20.0\text{m} \implies 77\text{dp}$
     - Range $60.0\text{m} \implies 93\text{dp}$ (Cat 4)
     - Range $150.0\text{m} \implies 112\text{dp}$ (Cat 3)
     - Range $300.0\text{m} \implies 133\text{dp}$ (Cat 2)
     - Range $600.0\text{m} \implies 166\text{dp}$ (Cat 1)
     - Range $1000.0\text{m} \implies 200\text{dp}$ (HC)
     - Range $1500.0\text{m} \implies 200\text{dp}$ (clamped to max 200dp)

3. **Multi-State Compose Preview Audit**:
   - Verify all 4 Compose previews compile and render:
     - `LiveClimbSheet_OnClimb_Preview`
     - `LiveClimbSheet_OnClimb_Dark_Preview`
     - `LiveClimbSheet_Approaching_Preview`
     - `LiveClimbSheet_Finished_Preview`

4. **Localization Parity Audit**:
   - Verify all climb string resources maintain 100% parity across all 9 supported locales (EN, DE, ES, FR, IT, JA, NL, PL, PT).

5. **Clean-Room Regression Execution**:
   - Execute `./gradlew testDebugUnitTest` asserting 100% pass rate with 0 failures across all 2,521+ tests.

---

## 3. Traceability Matrix

| Requirement Clause | Test Identifier | Verification Method | Target File |
| :--- | :--- | :--- | :--- |
| `REQ-UI-352` (Adaptive Height & No Fixed Height) | `TST-UI-312.1` | Static Contract & Source Inspection | `LiveClimbSheetLayoutTest.kt` |
| `REQ-UI-352` (Adaptive Range Formula Mapping) | `TST-UI-312.2` | Mathematical Unit Test | `LiveClimbAdaptiveHeightTest.kt` |
| `REQ-UI-352` (Gradient Shading & MetricBadge) | `TST-UI-312.3` | Architectural Contract Test | `LiveClimbSheetLayoutTest.kt` |
| `REQ-UI-352` (Multi-State Tooling) | `TST-UI-312.4` | Reflection & Composable Audit | `LiveClimbSheetLayoutTest.kt` |
| `REQ-UI-352` (Localization Parity) | `TST-UI-312.5` | XML Resource String Comparison | `LiveClimbSheetLayoutTest.kt` |
| `REQ-UI-352` (Clean-Room Pass Rate) | `TST-UI-312.6` | Gradle Regression Suite Execution | `./gradlew testDebugUnitTest` |

---

## 4. Invariants & Non-Functional Constraints

1. **Rule 36 Compliance**: Hardcoding fixed heights (e.g. `68.dp`) in bottom sheets or dialogs is strictly prohibited; profile heights must scale dynamically based on vertical elevation span $\Delta H = H_{\max} - H_{\min}$ using canonical domain formulas (`calculateElevationProfileHeight(range)`).
2. **Canonical Height Range**: Minimum height $70\text{dp}$, maximum height $200\text{dp}$, sublinear power curve exponent $0.6$ per `calculateElevationProfileHeight`.
3. **Zero Regression for Non-Climb Callers**: Callers that explicitly supply `fixedHeight` (or default to adaptive height) continue to function identically.
4. **Clean-Room Test Suite Pass Rate**: 100% test pass rate across the complete test suite.
