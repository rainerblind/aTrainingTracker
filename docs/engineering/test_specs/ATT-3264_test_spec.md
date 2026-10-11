# Stage 2: Requirement & Test Specification - ATT-3264: Refactor LiveClimbSheet to reuse LiveSegments layout and shared ElevationProfile

**Ticket**: [ATT-3264](https://atrainingtracker.atlassian.net/browse/ATT-3264)  
**Sub-task**: [ATT-3273](https://atrainingtracker.atlassian.net/browse/ATT-3273) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-2582](https://atrainingtracker.atlassian.net/browse/ATT-2582) (*[Epic] Segments: Live Tracking, Exploration & Route Integration*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `2026-41.10`  
**Requirement Mapping**: `REQ-UI-352` (*LiveClimbSheet Design System Harmonization, Shared ElevationProfile Integration & Degenerate Geometry Safeguards*)  
**Test Spec ID**: `TST-UI-312`  
**Branch**: `feature/ATT-3264`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-11  

---

## 1. Requirement Specification (REQ-UI-352)

### 1.1 Problem Statement & Rationale
During initial ClimbPro delivery (`ATT-1281`), `LiveClimbSheet.kt` was constructed rapidly to provide in-ride telemetry and visual gradient feedback for ascending route climbs. To accelerate delivery, several components were implemented in an ad-hoc, isolated manner rather than reusing the established design system:
1. `LiveClimbSheet.kt` introduced a bespoke 170-line private composable `ClimbProfileCanvas` that duplicates coordinate projection, bounds calculation, path building, baseline drawing, and rider marker interpolation already maintained in the canonical `ElevationProfile.kt`.
2. `LiveClimbSheet.kt` introduced standalone `ClimbCategoryChip` and `ClimbStatusBadge` composables wrapping raw `Surface` instead of using the standardized `MetricBadge.kt`.
3. The two-row header and telemetry HUD duplicated the structure, typography, and visual hierarchy of `SegmentHeader.kt` and `SegmentLiveDetails.kt`.

This requirement refactors `LiveClimbSheet.kt` to harmonize with the design system, replaces `ClimbProfileCanvas` with an extended `ElevationProfile.kt` supporting slope gradient color shading, delegates chips and badges to `MetricBadge.kt`, and establishes mathematical guards against degenerate geometries (zero points, single point, flat altitude spans).

### 1.2 Functional & Architectural Requirements
The system SHALL harmonize `LiveClimbSheet.kt` with the canonical design system, reuse `ElevationProfile.kt` for gradient-shaded climb profiles, and enforce defensive boundary safeguards against degenerate geometries (ATT-3264, amending `REQ-UI-328` and `REQ-UI-341`, and complementing `REQ-UI-015`):
1. *Shared ElevationProfile Integration & Bespoke Canvas Elimination*:
   - In `ElevationProfile.kt`, the system SHALL add support for optional slope gradient color shading via `climbGradientShading: Boolean = false` and `segmentColorProvider: ((Double) -> Color)? = null`.
   - In `ElevationProfile.kt`, the system SHALL add optional parameter `fixedHeight: Dp? = null`. When provided, `fixedHeight` overrides `totalCanvasHeight` so callers can request a compact fixed profile height (e.g. `68.dp`).
   - The system SHALL extract `getClimbGradeColor(gradePercent: Double): Color` as a shared public top-level function in `com.atrainingtracker.trainingtracker.ui.climbs`, mapping grade bands: `<3%` (Green `#4CAF50`), `3-6%` (Light Green `#8BC34A`), `6-9%` (Amber `#FFC107`), `9-12%` (Orange `#FF9800`), `12-20%` (Red `#E53935`), `>20%` (Dark `#212121`).
   - In `LiveClimbSheet.kt`, the bespoke private `ClimbProfileCanvas` composable SHALL be completely excised and replaced with `ElevationProfile(pathPoints = climb.pathPoints, currentDistance = currentDistance, showZoomControls = false, climbGradientShading = true, fixedHeight = 68.dp, modifier = Modifier.fillMaxWidth())`.
2. *Badge & Chip Design System Harmonization (`MetricBadge.kt`)*:
   - In `MetricBadge.kt`, `MetricBadge` SHALL accept optional parameter `shape: Shape = RoundedCornerShape(4.dp)`, preserving 100% backward compatibility for existing callers while allowing custom corner radii.
   - In `LiveClimbSheet.kt`, `ClimbCategoryChip` SHALL delegate directly to `MetricBadge(text = stringResource(category.labelRes), containerColor = bgColor, contentColor = textColor, shape = RoundedCornerShape(6.dp), modifier = modifier)`.
   - In `LiveClimbSheet.kt`, `ClimbStatusBadge` SHALL delegate directly to `MetricBadge(text = label, containerColor = containerColor, contentColor = contentColor, shape = RoundedCornerShape(12.dp), modifier = modifier)`.
3. *Header & Telemetry HUD Layout Symmetry*:
   - The top header row, subtitle row, 0.5dp divider, and 2-column telemetry HUD in `LiveClimbSheet.kt` SHALL strictly maintain layout symmetry, typography tokens, and spacing established in `SegmentHeader.kt` and `SegmentLiveDetails.kt`.
4. *Defensive Degenerate Geometry & Numerical Overflow Safeguards*:
   - In `ElevationProfile.kt`, if `pathPoints.size < 2`, the composable SHALL safely return without rendering canvas paths and without throwing exceptions.
   - In `ElevationProfile.kt` and `calculateElevationBounds`, vertical range calculation SHALL enforce `val range = (max - min).coerceAtLeast(1.0)` to eliminate division-by-zero or `NaN` coordinate projections on flat climbs or zero altitude delta.
   - In `ElevationProfile.kt`, horizontal distance span SHALL enforce `val totalDist = (pathPoints.last().distance - pathPoints.first().distance).coerceAtLeast(1.0)` to prevent division-by-zero in X coordinate scaling.
5. *Preservation of System Invariants & Localization*:
   - Instantaneous grade, remaining distance, remaining elevation gain, and rider marker tracking logic MUST NOT be altered.
   - 100% 9-language localization parity (EN, DE, ES, FR, IT, JA, NL, PL, PT) MUST be strictly preserved.
   - Zero regression across existing callers of `ElevationProfile.kt`, `SegmentHeader.kt`, `SegmentLiveDetails.kt`, or `MetricBadge.kt`.

### 1.3 Requirement Archaeology & Chesterton's Fence Audit
1. *Original Requirement ID & Target*: Amends `REQ-UI-328` (*Live Climb Cockpit Sheet Visual Design Alignment with Live Segment System*, ATT-2947) and `REQ-UI-341` (*Live Climb Cockpit Sheet Interactive Visual Design Optimization, Compose Previews & Ground Line Presentation*, ATT-3103), targeting `LiveClimbSheet.kt`, `ElevationProfile.kt`, and `MetricBadge.kt`.
2. *Historical Origin & Commit Trace*: Sprint 2026-40.14 (`ATT-1281`) introduced `ClimbProfileCanvas`. Sprint 2026-41.7 (`ATT-2947`, commit `ea5dfc85`) aligned typography with `SegmentHeader`. Sprint 2026-41.8 (`ATT-3103`, commit `479c3b80`) added Compose previews and ground line.
3. *Root Reason for Existing Formulation*: `ClimbProfileCanvas` was kept private in `LiveClimbSheet.kt` because `ElevationProfile.kt` did not yet support slope gradient color providers or fixed height constraints. With the maturation of `ElevationProfile.kt`, maintaining two independent canvas engines is tech debt that produces subtle visual inconsistencies.
4. *Preservation of Core Invariants*: Live tracking calculations, rider marker positioning, 68dp canvas height, ground line baseline, multi-state Compose `@Preview` tooling, and 100% test pass rate remain strictly preserved.

### 1.4 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Shared ElevationProfile with Gradient Shading)**:
  * *Given* an active live climb displayed in `LiveClimbSheet`
  * *When* the climb profile renders
  * *Then* `ElevationProfile` is invoked with `climbGradientShading = true`, rendering the elevation polygon with grade-banded colors matching `getClimbGradeColor` and a rider position marker at `currentDistance`.
* **Criterion 2 (MetricBadge Delegation for Chips and Badges)**:
  * *Given* `ClimbCategoryChip` and `ClimbStatusBadge` in `LiveClimbSheet`
  * *When* rendered in the sheet header
  * *Then* both components delegate directly to `MetricBadge` with custom corner shapes (6.dp and 12.dp) rather than raw `Surface` duplicates.
* **Criterion 3 (Degenerate Geometry Resilience)**:
  * *Given* an empty `pathPoints` list or single-point climb
  * *When* `ElevationProfile` or `LiveClimbSheet` is composed
  * *Then* no exception is thrown and the UI handles the degenerate state gracefully.
  * *Given* a flat climb with `minAlt == maxAlt` (`altSpan == 0.0`)
  * *When* `ElevationProfile` calculates coordinate bounds
  * *Then* altitude range is coerced to $\ge 1.0$, preventing division-by-zero or `NaN` coordinate outputs.
* **Criterion 4 (Contract and Non-Regression Parity)**:
  * *Given* the automated test suite
  * *When* `LiveClimbSheetLayoutTest` and full clean-room unit regression tests execute
  * *Then* 100% of tests pass with zero regressions across `ElevationProfile` callers.

---

## 2. Test Specification (TST-UI-312)

### Test Case 1: `ElevationProfile` Gradient Shading & Degenerate Geometry (`TST-UI-312.1`)
* **Scope**: Unit & Contract Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/climbs/ElevationProfileClimbShadingTest.kt` (or `ElevationProfileLayoutTest.kt`)
* **Actions & Assertions**:
  1. Verify `getClimbGradeColor(gradePercent)` returns expected colors for bands:
     - grade < 3% -> `#4CAF50` (Green)
     - 3% <= grade < 6% -> `#8BC34A` (Light Green)
     - 6% <= grade < 9% -> `#FFC107` (Amber)
     - 9% <= grade < 12% -> `#FF9800` (Orange)
     - 12% <= grade < 20% -> `#E53935` (Red)
     - grade >= 20% -> `#212121` (Dark)
  2. Verify `calculateElevationBounds` enforces `range.coerceAtLeast(1.0)` when `min == max`.
  3. Verify `ElevationProfile` handles `pathPoints.isEmpty()` and `pathPoints.size == 1` gracefully without throwing `NoSuchElementException`.
  4. Verify `fixedHeight` overrides `totalCanvasHeight` when provided.

### Test Case 2: `MetricBadge` Custom Shape Extension (`TST-UI-312.2`)
* **Scope**: Unit & Contract Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/components/MetricBadgeShapeTest.kt`
* **Actions & Assertions**:
  1. Verify default `shape` parameter is `RoundedCornerShape(4.dp)`.
  2. Verify custom `shape` (e.g. `RoundedCornerShape(6.dp)` or `RoundedCornerShape(12.dp)`) can be supplied and compiles cleanly without breaking existing callers.

### Test Case 3: `LiveClimbSheet` Layout, Badges & ElevationProfile Contract (`TST-UI-312.3`)
* **Scope**: Layout & Contract Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/climbs/LiveClimbSheetLayoutTest.kt`
* **Actions & Assertions**:
  1. Verify `LiveClimbSheet` invokes `ElevationProfile(` instead of `ClimbProfileCanvas(`.
  2. Verify `ClimbProfileCanvas` is completely removed from `LiveClimbSheet.kt`.
  3. Verify `ClimbCategoryChip` and `ClimbStatusBadge` delegate to `MetricBadge`.
  4. Verify `@Preview` composables (`LiveClimbSheet_OnClimb_Preview`, `LiveClimbSheet_OnClimb_Dark_Preview`, `LiveClimbSheet_Approaching_Preview`, `LiveClimbSheet_Finished_Preview`) are present and intact.

### Test Case 4: 9-Language Localization Parity Audit (`TST-UI-312.4`)
* **Scope**: Localization Parity Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/TranslationParityTest.kt`
* **Actions & Assertions**:
  1. Verify all climb string resources exist across EN, DE, ES, FR, IT, JA, NL, PL, PT.
  2. Verify zero missing keys and 100% translation completeness.

### Test Case 5: Clean-Room Full Suite Regression (`TST-UI-312.5`)
* **Command**: `./gradlew testDebugUnitTest`
* **Goal**: Verify 100% test pass rate across the full application test suite with 0 regressions.

---

## 3. Traceability Matrix

| Test Case | Scope | Method / Component Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-UI-312.1` | Unit | `ElevationProfile`, `getClimbGradeColor`, `calculateElevationBounds` | `REQ-UI-352` (clauses 1, 4) | Specified |
| `TST-UI-312.2` | Unit | `MetricBadge` custom shape parameter | `REQ-UI-352` (clause 2) | Specified |
| `TST-UI-312.3` | Contract | `LiveClimbSheet`, `ClimbCategoryChip`, `ClimbStatusBadge` | `REQ-UI-352` (clauses 1, 2, 3) | Specified |
| `TST-UI-312.4` | Localization | `TranslationParityTest` (all 9 locales) | `REQ-LOC-001`, `REQ-UI-352` (clause 5) | Specified |
| `TST-UI-312.5` | Regression | `./gradlew testDebugUnitTest` | `REQ-PRO-001` | Specified |
