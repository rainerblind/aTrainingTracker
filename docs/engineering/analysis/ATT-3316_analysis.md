# Stage 1: Problem Domain & Root Cause Analysis - ATT-3316: Scale LiveClimbSheet elevation profile height adaptively based on climb elevation gain

**Ticket**: [ATT-3316](https://rainerblind.atlassian.net/browse/ATT-3316)  
**Sub-task**: [ATT-3392](https://rainerblind.atlassian.net/browse/ATT-3392) (`[Analysis]`)  
**Parent Epic**: [ATT-2582](https://rainerblind.atlassian.net/browse/ATT-2582) (*Segments: Live Tracking, Exploration & Route Integration*)  
**Target Release**: `Sprint 2026-42.1`  
**Active Sprint**: `Sprint 2026-42.1`  
**Requirement Mapping**: `REQ-UI-352` (Clause 1 refinement)  
**Test Mapping**: `TST-UI-312` (Clause 3 refinement)  
**Branch**: `feature/ATT-3316`  
**Author**: AI Agent 1 (Problem Domain & Root Cause Analyst)  
**Date**: 2026-10-13  

---

### Requirement Archaeology & Chesterton's Fence Audit

1. **Original Requirement ID & Target**:
   - `REQ-UI-352` (*LiveClimbSheet Design System Harmonization, Shared ElevationProfile Integration & Degenerate Geometry Safeguards*, `ATT-3264`), targeting `LiveClimbSheet.kt`, `ElevationProfile.kt`, and `LiveClimbSheetLayoutTest.kt`.
2. **Historical Origin & Commit Trace**:
   - Introduced in Sprint 2026-40.14 (`ATT-1281`) where bespoke `ClimbProfileCanvas` was given a fixed height (`68.dp`).
   - Refactored in Sprint 2026-41.10 (`ATT-3264`, commit `04ad935c`) to eliminate bespoke canvas code and reuse `ElevationProfile.kt`. To preserve backward-compatible visual appearance during the migration, `fixedHeight = 68.dp` was explicitly passed to `ElevationProfile`.
3. **Root Reason for Existing Formulation**:
   - `fixedHeight = 68.dp` was retained from the legacy `ClimbProfileCanvas` during the ATT-3264 refactoring to maintain a compact bottom sheet size and avoid disrupting existing live tracking screen layouts.
   - However, during the Sprint 2026-41.10 Joint Review, the athlete mandated:
     *"The height of the elevation profile should also depend on the height of the climb. No exceptions here."* (Documented in `Sprint_Review_and_Retro_ATT-3318.md` under Topic 3 & Rule 36).
   - Hardcoding fixed heights (e.g. `68.dp`) breaks the app's core visual design language established in Design Guidelines §2.3. A minor rolling hill (20m gain) and an alpine mountain pass (1000m gain) appeared visually identical in height, squashing vertical gradient perception for major ascents.
4. **Preservation of Core Invariants**:
   - Canonical Adaptive Height: Utilize `calculateElevationProfileHeight(range)` ($70\text{dp} \dots 200\text{dp}$) from `ElevationProfile.kt` per `REQ-UI-346`.
   - Gradient Shading: Preserve `climbGradientShading = true` and `getClimbGradeColor` color bands per `REQ-UI-352`.
   - Telemetry HUD & Badges: Preserve all live climb telemetry metrics (remaining distance, elevation gain, grade), 32dp terrain icon, and `MetricBadge` delegation per `REQ-UI-328` and `REQ-UI-352`.
   - Multi-State Tooling: Preserve all Compose `@Preview` composables (`LiveClimbSheet_OnClimb_Preview`, `LiveClimbSheet_OnClimb_Dark_Preview`, `LiveClimbSheet_Approaching_Preview`, `LiveClimbSheet_Finished_Preview`) per `REQ-UI-341`.
   - Clean-Room Test Pass Rate: 100% test pass rate across all unit tests and layout contracts.

---

## 1. Problem Statement & Motivation

During Sprint 2026-41.10 Joint Review of `ATT-3264` (*Refactor LiveClimbSheet to reuse ElevationProfile*), the athlete observed that `LiveClimbSheet` uses a hardcoded compact fixed height:
```kotlin
ElevationProfile(
    pathPoints = normalizedPoints,
    currentDistance = currentDistance,
    showZoomControls = false,
    climbGradientShading = true,
    fixedHeight = 68.dp,
    modifier = Modifier.fillMaxWidth()
)
```
The athlete mandated:
> *"The height of the elevation profile should also depend on the height of the climb. No exceptions here."*

### Why Hardcoded Height Fails User Ergonomics
1. **Loss of Scale Perception**: A short roller of 20 m elevation gain and a massive alpine HC ascent of 1,200 m gain render at the exact same 68 dp height. The visual steepness and psychological difficulty of the mountain are distorted.
2. **Violation of Project-Wide Design System**: `ElevationProfile.kt` already provides the canonical domain-proven height formula `calculateElevationProfileHeight(range)`, smoothly mapping vertical range into an adaptive height curve from $70\text{dp}$ to $200\text{dp}$. `LiveClimbSheet` bypassed this canonical mechanism by passing `fixedHeight = 68.dp`.
3. **In-Ride Ergonomics**: In `SensorGridScreen.kt`, `BottomSheetScaffold` dynamically wraps sheet content (`wrapContentHeight()`) and automatically expands to full content height upon climb activation (`scaffoldState.bottomSheetState.expand()`). Taller climbs naturally and deservedly command more vertical prominence, while small hills remain compact.

---

## 2. Forensic Investigation & Gap Analysis

### 2.1 Height Resolution Mechanics in `ElevationProfile.kt`
In `ElevationProfile.kt`:
```kotlin
val bounds = calculateElevationBounds(pathPoints, minAltitudeOverride, maxAltitudeOverride)
val range = bounds.range
val adaptiveHeight = calculateElevationProfileHeight(range)
...
val totalCanvasHeight = fixedHeight ?: if (showZoomControls) cachedData.adaptiveHeight + 28.dp else cachedData.adaptiveHeight
```
And `calculateElevationProfileHeight(range)` is defined as:
```kotlin
fun calculateElevationProfileHeight(range: Double): androidx.compose.ui.unit.Dp {
    val minH = 70f
    val maxH = 200f
    val threshold = 1000.0
    val normalizedRange = (range.coerceIn(0.0, threshold) / threshold).toFloat()
    val curvedRange = normalizedRange.toDouble().pow(0.6).toFloat()
    val height = minH + curvedRange * (maxH - minH)
    return height.toInt().dp
}
```
Key observations:
- For flat or minor ascents ($\text{range} \le 10\text{m}$): Height evaluates to $70\text{dp}$—virtually identical to the legacy $68\text{dp}$ footprint, ensuring small hills do not crowd the cockpit.
- For moderate Category 3 / 4 hills ($\text{range} \approx 100\text{m}$): Height evaluates to $\approx 102\text{dp}$.
- For Category 2 climbs ($\text{range} \approx 300\text{m}$): Height evaluates to $\approx 133\text{dp}$.
- For Category 1 climbs ($\text{range} \approx 500\text{m}$): Height evaluates to $\approx 155\text{dp}$.
- For HC mountain passes ($\text{range} \ge 1000\text{m}$): Height caps gracefully at $200\text{dp}$.

### 2.2 Padding & Usable Canvas Area
In `ElevationProfile.kt`:
```kotlin
val topPadding = if (showZoomControls) 44.dp else 16.dp
val effectiveTopPadding = if (fixedHeight != null) 8.dp else topPadding
val bottomPadding = if (fixedHeight != null) 18.dp else 24.dp
```
- Under `fixedHeight = 68.dp`, usable canvas height was $68 - 8 - 18 = 42\text{dp}$.
- Under adaptive height with `fixedHeight = null` (and `showZoomControls = false`):
  - `effectiveTopPadding = 16.dp`
  - `bottomPadding = 24.dp` (providing clean clearance for bottom distance and start ticks)
  - Usable plotting height scales dynamically from $70 - 16 - 24 = 30\text{dp}$ (tiny hill) to $200 - 16 - 24 = 160\text{dp}$ (HC col).

### 2.3 Existing Test Assertions & Architectural Contracts
In `LiveClimbSheetLayoutTest.kt`:
```kotlin
@Test
fun testLiveClimbSheet_elevationProfileConfiguredWithRefinedHeightAndGradientShading() {
    val content = liveClimbSheetFile.readText()

    assertTrue(
        "LiveClimbSheet must configure ElevationProfile with refined 68.dp fixed height (REQ-UI-341, REQ-UI-352)",
        content.contains("fixedHeight = 68.dp")
    )
...
```
This test explicitly checked for the old `fixedHeight = 68.dp` string.
Under ATT-3316:
- The contract must be updated to assert that `LiveClimbSheet` uses adaptive height scaling and does **not** hardcode `fixedHeight`.
- A dedicated test verifying adaptive height calculation across various climb categories/gains will be added.

---

## 3. Scope Bounding (ATT-1250)

### 3.1 In-Scope
1. **`LiveClimbSheet.kt`**:
   - Remove `fixedHeight = 68.dp` parameter when invoking `ElevationProfile(...)`.
   - Allow `ElevationProfile` to dynamically compute `totalCanvasHeight` via `calculateElevationProfileHeight(range)` using the climb's elevation gain.
2. **`LiveClimbSheetLayoutTest.kt`**:
   - Update contract assertion to verify that `fixedHeight` is no longer hardcoded in `LiveClimbSheet.kt`.
   - Verify that `ElevationProfile` is called with `climbGradientShading = true` and `fillMaxWidth()`.
3. **Living Documentation**:
   - Update `REQ-UI-352` in `docs/requirements.md` to reflect adaptive elevation scaling.
   - Update `TST-UI-312` in `docs/tests.md`.

### 3.2 Out-of-Scope
- Mutating `calculateElevationProfileHeight(range)` formula itself (formula is canonical and shared across `ElevationProfile.kt`, `SensorGridScreen.kt`, and map detail layouts).
- Removing `fixedHeight` parameter from `ElevationProfile.kt` (retained as optional `fixedHeight: Dp? = null` for other callers that may require explicit overrides).
- Modifying `LiveSegmentSheet.kt` or `BottomSheetScaffold` state logic.

---

## 4. Proposed Solution & Architecture

### 4.1 Modification to `LiveClimbSheet.kt`
```diff
         ElevationProfile(
             pathPoints = normalizedPoints,
             currentDistance = currentDistance,
             showZoomControls = false,
             climbGradientShading = true,
-            fixedHeight = 68.dp,
             modifier = Modifier.fillMaxWidth()
         )
```
Omitting `fixedHeight` defaults to `null`, enabling `ElevationProfile` to use `cachedData.adaptiveHeight` ($70\text{dp} \dots 200\text{dp}$).

### 4.2 Behavior Across Climb Categories
| Category | Elevation Gain ($\Delta H$) | Formula Height | Visual Impact |
| :--- | :--- | :--- | :--- |
| **Uncategorized / Roller** | $25\text{ m}$ | $79\text{ dp}$ | Compact, preserves maximum cockpit space |
| **Category 4** | $60\text{ m}$ | $93\text{ dp}$ | Clear modest rise, distinguishable from flat ground |
| **Category 3** | $150\text{ m}$ | $112\text{ dp}$ | Moderate presence, clear slope shape |
| **Category 2** | $300\text{ m}$ | $133\text{ dp}$ | Prominent profile, steep segments pop visually |
| **Category 1** | $600\text{ m}$ | $166\text{ dp}$ | Tall profile conveying sustained hard climbing |
| **Hors Catégorie (HC)** | $\ge 1000\text{ m}$ | $200\text{ dp}$ | Full canonical maximum height, imposing mountain scale |

---

## 5. Invariants & Risk Assessment

1. **Screen Clipping Risk**:
   - *Risk*: Could a $200\text{dp}$ elevation profile cause the bottom sheet to overflow or occlude essential cockpit elements?
   - *Mitigation*: In `SensorGridScreen.kt`, the sensor grid is wrapped in `Modifier.weight(1f, fill = false)` with scrollable container (`verticalScroll`), and `BottomSheetScaffold` handles content wrapping and sheet expanding smoothly. The athlete can collapse the sheet or swipe to other tabs at any time.
2. **Degenerate Altitude Protection**:
   - *Risk*: Climbs with flat altitude data or single trackpoints.
   - *Mitigation*: Safeguards from `REQ-UI-352` (`pathPoints.size < 2` guard and `(max - min).coerceAtLeast(1.0)`) guarantee `range >= 1.0` and prevent `NaN` or zero division.
3. **Clean-Room Test Pass Rate**:
   - Zero test regressions across the full 2,521+ test suite.

---

## 6. Stage Transition Plan

1. Record deliverable in `docs/engineering/analysis/ATT-3316_analysis.md`.
2. Update Jira sub-task `ATT-3392` description with deliverable content.
3. Transition `ATT-3392` to `In Überprüfung`.
4. Trigger Gate 1 audit via `python3 tools/review_agent.py audit ATT-3392`.
5. Upon Gate 1 sign-off, advance `ATT-3392` to `Erledigt` and transition parent `ATT-3316` to Stage 2 (`Test Spec`).
