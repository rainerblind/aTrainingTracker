# Stage 5: Walkthrough - ATT-3343: Ensure tracking map fills all remaining vertical space above elevation profile

**Ticket**: [ATT-3343](https://rainerblind.atlassian.net/browse/ATT-3343)  
**Sub-task**: [ATT-3442](https://rainerblind.atlassian.net/browse/ATT-3442) (`[Test]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `Sprint 2026-42.1`  
**Active Sprint**: `Sprint 2026-42.1`  
**Requirement Mapping**: `REQ-UI-358`  
**Test Mapping**: `TST-UI-319`  
**Branch**: `feature/ATT-3343`  
**Author**: AI Agent 1 (Verification & Quality Engineer)  
**Date**: 2026-10-13  

---

### Requirement Archaeology & Chesterton's Fence Audit

1. **Original Requirement ID & Target**:
   - Amends `REQ-UI-346` (*Canonical Adaptive Height & Weight Decoupling for Sole Elevation Profile on Tracking Screen*, Sprint 2026-41.9, ATT-3173) and `REQ-UI-339` (*Dynamic Cockpit Weight Allocation & Flexible Vertical Expansion for Elevation Profile when Map is Disabled*, Sprint 2026-41.8, ATT-3107), and complements `REQ-UI-355` (*Active Route Full Elevation Profile with Map-Matched Position Marker, Categorized Climbs & Per-Tab Rolling Window Scoping During Tracking*, Sprint 2026-41.10 / Sprint 2026-42.1, ATT-3199 & ATT-3317).
   - Formulated as `REQ-UI-358` (*Dynamic Cockpit Viewport Allocation, Asymmetric Map Weight Expansion & Bounded Sensor Grid Containment*).
   - Targets `SensorGridScreen.kt` and `SensorGridScreenLayoutTest.kt`.
2. **Historical Origin & Commit Trace**:
   - Sprint 2026-41.8 (`ATT-3107`, commit `8d6ef4e1`): Introduced `Modifier.weight(1f, fill = false)` on the sensor grid Column to prevent sensor rows from crowding the elevation profile when the map is disabled.
   - Sprint 2026-41.9 (`ATT-3173`, commit `ad0c2020`): Excised `Modifier.weight(1f)` from the elevation profile `Surface` to eliminate half-screen stretching when the map is hidden, establishing canonical adaptive height ($70\text{dp} \dots 200\text{dp}$).
3. **Root Reason for Existing Formulation**:
   - In `ATT-3107`, `weight(1f, fill = false)` was placed on the sensor grid Column so it wraps content for 1–2 rows and scrolls for many rows without pushing bottom cockpit views off screen.
   - However, when the map is visible (`state.mapState.showMap == true`), the map is also assigned `Modifier.weight(1f)`.
   - In Jetpack Compose `Column`, weighted children divide the available remaining space proportionally according to their weights. Because both the sensor grid and the map declare weight `1f`, total weight is `2f` (50% quota each).
   - Because the sensor grid specifies `fill = false`, it only consumes its intrinsic content height (e.g. 1 row = ~80dp). Crucially, Compose does *not* reallocate the unused portion of the sensor grid's 50% quota to the other weighted child (the map).
   - The map remains hard-capped at its 50% allocation. The unweighted elevation profile renders directly below the map, leaving the remaining unused 50% space (~250–350dp) completely empty as dead white space at the bottom of the screen.
4. **Preservation of Core Invariants**:
   - **Invariant 1 (Map-Disabled Invariant)**: When `state.mapState.showMap == false`, the sensor grid retains `Modifier.weight(1f, fill = false)` so it wraps content for 1–2 rows and scrolls smoothly for 4–6 rows, and the elevation profile renders at its canonical adaptive height ($70\text{dp} \dots 200\text{dp}$) directly below it (`REQ-UI-346`).
   - **Invariant 2 (Adaptive Height Invariant)**: The elevation profile retains its canonical adaptive height computed via `calculateElevationProfileHeight(range)` with non-weighted wrapping `Surface` (`padding(top = 8.dp)`).
   - **Invariant 3 (Sensor Grid Containment Invariant)**: When the map is visible, the sensor grid wraps content naturally when few rows exist, and is bounded to a maximum proportional constraint (`heightIn(max = availableHeight * 0.5f)`) with internal scrolling when many rows exist, preventing it from starving the map.
   - **Invariant 4 (Full Flexible Expansion Invariant)**: When the map is visible, the map Box is the sole flexible weight consumer (`Modifier.weight(1f, fill = true)`), expanding to consume 100% of all vertical space remaining between the sensor grid and the elevation profile (or bottom edge).
   - **Invariant 5 (System Stability)**: Recomposition isolation (`TrackingMapContainer`), ambient navigation overlays, live climb sheets, and 100% full-suite unit test pass rate MUST be strictly preserved.

---

## 1. Executive Summary

This deliverable documents Stage 5 verification and walkthrough for **ATT-3343**. Previously, on tracking tabs configured with sensor fields, a map, and an elevation profile (e.g. the "Karte" tab), the map only occupied ~40% of the screen when 1–2 sensor rows were configured, leaving a blank dead white space void (~40% of screen height) empty below the elevation profile.

ATT-3343 resolves this defect by establishing dynamic viewport allocation and asymmetric weight distribution in `SensorGridScreen.kt` in full compliance with `REQ-UI-358`:
1. **Dynamic Available Viewport Measurement**: Wrapped the tracking & preview stationary content in `BoxWithConstraints` to capture `val availableHeight = maxHeight`.
2. **Asymmetric Weight Allocation**:
   - When the map is visible (`state.mapState.showMap == true`), the sensor grid Column applies `Modifier.fillMaxWidth().heightIn(max = availableHeight * 0.5f).verticalScroll(rememberScrollState())`, wrapping intrinsically when 1–2 rows are present and bounded to at most 50% screen height when 4–6 rows are present.
   - The map Box is configured as the sole flexible weight consumer with `Modifier.fillMaxWidth().weight(1f, fill = true)`, expanding to consume 100% of all vertical space remaining between the sensor grid and the elevation profile, eliminating all dead space voids.
   - When the map is hidden (`state.mapState.showMap == false`), canonical fallback behavior from `REQ-UI-346` is strictly preserved (`weight(1f, fill = false)`).
3. **Adaptive Elevation Profile Anchoring**: Elevation profile retains non-weighted wrapping Surface with canonical adaptive height ($70\text{dp} \dots 200\text{dp}$) and expands seamlessly down to the bottom screen boundary when profile is disabled or has < 2 points.

---

## 2. Verification Results

### 2.1 Targeted Unit & Contract Testing

#### `SensorGridScreenLayoutTest`
- `testLayoutStructure_boxWithConstraintsAndAsymmetricWeightAllocation`: **PASSED** (Verifies `BoxWithConstraints` container, `availableHeight` measurement, conditional sensor grid modifier branching, and map `weight(1f, fill = true)`).
- `testLayoutGeometry_simulationCalculatesZeroDeadSpace`: **PASSED** (Verifies mathematical geometry: 80dp sensor + 128dp profile -> 592dp map with 0dp dead space; 480dp sensor clamped to 400dp + 128dp profile -> 272dp map with 0dp dead space; map-disabled fallback).
- `testElevationProfile_doesNotApplyWeightWhenMapDisabled`: **PASSED**.
- `testElevationProfile_doesNotPassFillParentHeightOrFillMaxSize`: **PASSED**.
- `testElevationProfile_guardedByPathPointsSize`: **PASSED**.

#### `SensorGridScreenRouteIntegrationTest`
- `SensorGridScreenRouteIntegrationTest`: **PASSED** (100% pass across all route overlay, turn prompt banner, and navigation HUD contract tests).

#### `TranslationParityTest`
- `TranslationParityTest`: **PASSED** (100% localization parity across EN, DE, ES, FR, IT, JA, NL, PL, PT).

### 2.2 Clean-Room Test Suite Execution
- **Command**: `./gradlew testDebugUnitTest`
- **Result**: **BUILD SUCCESSFUL** (2559+ tests completed, 100% pass rate across entire suite, zero regressions).

---

## 3. Living Documentation Status

- **`docs/requirements.md`**: `REQ-UI-358` updated to `Verified`.
- **`docs/tests.md`**: `TST-UI-319` updated to `Verified`.
- **Governance Check**: `python3 tools/verify_requirement_governance.py --base-ref sprint/2026-42.1` -> Clean pass.

---

## 4. Subtask & Ticket Advancement Plan

1. Stage 5 subtask **ATT-3442** (`[Test]`) description updated with this walkthrough.
2. Subtask transitioned to `In Überprüfung` and Gate 5 audit initiated via `python3 tools/review_agent.py audit ATT-3442`.
3. Upon Gate 5 sign-off (`RECOMMEND PASS`), subtask moved to `Erledigt` via transition `freigabe`.
4. Parent ticket **ATT-3343** transitioned to `Final Review (Human)`.
5. Changes committed to `feature/ATT-3343`.
6. Branch `feature/ATT-3343` merged into `sprint/2026-42.1` with `--no-ff`, and `feature/ATT-3343` deleted.
