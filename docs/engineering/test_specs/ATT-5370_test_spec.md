# Stage 2: Requirement & Test Specification - ATT-5370: Universal Marginal Histograms & Widened Strip Layout Parity

**Ticket**: [ATT-5370](https://rainerblind.atlassian.net/browse/ATT-5370)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `Sprint 2026-45.5`  
**Requirement Mapping**: `REQ-UI-446` (`REQ-UI-446.1`, `REQ-UI-446.2`, `REQ-UI-446.3`)  
**Test Spec ID**: `TST-UI-446` (`TST-UI-446.1`, `TST-UI-446.2`, `TST-UI-446.3`, `TST-UI-446.4`)  
**Branch**: `feature/ATT-5370`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-04  

---

## 1. Requirement Specification (REQ-UI-446)

### 1.1 Problem Statement & Rationale
In Workout Details / Map inspection views (`MapDetailLayout`, `WorkoutSummary`, `TrackOnMapScreen`), right-side marginal histograms are restricted to Heart Rate and Power. Sibling charts (`ElevationProfile`, `SPEED/PACE`, `W_PRIME_BAL`) leave their right margin unused (empty 25dp strip in portrait; blank spacer in landscape). The 25dp portrait strip is too narrow (~20dp bar width) for frequency scannability. This ticket unifies marginal histograms across all stacked graphs in primary series color, widens portrait margin to 52dp (~44dp bar width), and preserves Rule 64 1:1 plot alignment.

### 1.2 Functional & Architectural Requirements
1. **Universal Stacked Marginal Histograms (`REQ-UI-446.1`)**: Render a right-side Y-axis marginal histogram on all stacked graphs (`ElevationProfile`, `SPEED/PACE`, `HEART_RATE`, `POWER`, `W_PRIME_BAL`). Parameterize `ElevationProfile(alignWithTelemetryCharts: Boolean = false)` so standalone callers stay clean.
2. **Series-Color Non-Zoned Rendering (`REQ-UI-446.2`)**: Non-zoned charts (`ElevationProfile`, `SPEED/PACE`, `W_PRIME_BAL`) SHALL compute 20–24 linear frequency bins across `[dataMin, dataMax]` and render in primary series color. For each bin, background track (`alpha = 0.15f`) is drawn unconditionally across `[dataMin, dataMax]` (Rule 69); foreground bars are gated on positive frequency. Filter out-of-domain bins and clamp to `[dataMin, dataMax]` without `.coerceAtLeast(1f)` stacking at `y = 0` (Rule 70). Preserve 5/7-zone coloring for HR and Power.
3. **Widened Marginal Strip & 1:1 Plot Alignment (`REQ-UI-446.3`)**: In portrait, widen `endPaddingPx` to 52dp (~44dp bar width). In landscape, maintain 72%/28% dual-pane split without blank fallback spacers, aligning X-axis bounds and scrubbing cursors 1:1 across all stacked charts (Rule 64). Maintain `startPadding = 50.dp`.

### 1.3 Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Amends `REQ-UI-251` (Portrait Marginal Strip), `REQ-UI-431` (Landscape Marginal Pane), and `REQ-UI-433.4` (Multi-Chart Alignment).
* **Historical Origin & Commit Trace**: Commits `c8192a0e` (`ATT-2147`), `dcb8f15f` (`ATT-2145`), `56b230ec` (`ATT-4774`), `9ce1ef68` / `cbb8317a` (`ATT-5281` / `ATT-5311`, Rules 69 & 70).
* **Root Reason for Existing Formulation**: `endPadding = 25.dp` was reserved solely for HR/Power zone labels; non-zoned charts lacked physiological zones and left this space empty. Widening to 52dp with uniform linear bins gives all metrics visual frequency scannability.
* **Preservation of Core Invariants**: 1:1 plot width and scrubbing alignment (Rule 64), unconditional background tracks (Rule 69), domain clamping (Rule 70), unaffected standalone callers (`alignWithTelemetryCharts = false`).

### 1.4 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Universal Portrait Strip)**: *Given* stacked graphs in portrait mode, *When* rendered, *Then* all charts render a 44dp marginal histogram inside a 52dp margin, colored by series color (or zones for HR/Power).
* **Criterion 2 (Landscape 72/28 Alignment)**: *Given* stacked graphs in landscape mode, *When* rendered, *Then* all charts share 72%/28% bounds without blank spacers, aligning scrubbing cursors 1:1 (Rule 64).
* **Criterion 3 (Track Continuity & Clamping)**: *Given* non-zoned histograms, *When* rendered across `[dataMin, dataMax]`, *Then* background tracks (`alpha = 0.15f`) render unconditionally and out-of-domain bins never stack at `y = 0` (Rules 69 & 70).
* **Criterion 4 (Standalone Caller Isolation)**: *Given* non-stacked callers of `ElevationProfile`, *When* rendered, *Then* they default to `alignWithTelemetryCharts = false` with clean layouts.

---

## 2. Test Specification (TST-UI-446)

### Test Case 1: Universal Marginal Histogram & Widened Portrait Margin (`TST-UI-446.1`)
* **Scope**: Contract Test (`TelemetryMetricGraphTest.kt`, `ElevationProfileTest.kt`)
* **Expected Result**: Assert `endPaddingPx = 52.dp` for stacked charts; assert marginal histograms rendered for `ElevationProfile`, `Speed/Pace`, `HR`, `Power`, `W'bal`.

### Test Case 2: Series-Color Rendering, Track Continuity & Domain Clamping (`TST-UI-446.2`)
* **Scope**: Contract Test (`TelemetryMetricGraphTest.kt`, `TelemetryMetricGraphLandscapeContractTest.kt`)
* **Expected Result**: Non-zoned bins drawn in series color; unconditional `alpha = 0.15f` tracks (Rule 69); out-of-domain bins clamped with zero `y = 0` stacking (Rule 70).

### Test Case 3: Landscape 72/28 Multi-Chart Alignment & Standalone Parity (`TST-UI-446.3`)
* **Scope**: Contract Test (`TelemetryMetricGraphLandscapeContractTest.kt`, `MapDetailLayoutTest.kt`)
* **Expected Result**: 72%/28% split across all stacked charts; standalone `ElevationProfile` callers default to `alignWithTelemetryCharts = false`.

### Test Case 4: Localization Parity & Targeted Clean-Room Suite (`TST-UI-446.4`)
* **Scope**: Localization & Unit Test Suite (`TranslationParityTest`, `map.*`, `zones.*`, `ThemeColorCentralizationAuditTest`)
* **Expected Result**: 100% pass rate.

---

## 3. Traceability Matrix

| Test Case | Scope | Target File | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-UI-446.1` | Contract | `TelemetryMetricGraphTest.kt`, `ElevationProfileTest.kt` | `REQ-UI-446.1`, `REQ-UI-446.3` | Specified |
| `TST-UI-446.2` | Contract | `TelemetryMetricGraphTest.kt`, `TelemetryMetricGraphLandscapeContractTest.kt` | `REQ-UI-446.2` | Specified |
| `TST-UI-446.3` | Contract | `TelemetryMetricGraphLandscapeContractTest.kt` | `REQ-UI-446.1`, `REQ-UI-446.3` | Specified |
| `TST-UI-446.4` | Suite | `TranslationParityTest.kt`, package tests | `REQ-UI-106`, `REQ-PRO-001` | Specified |
