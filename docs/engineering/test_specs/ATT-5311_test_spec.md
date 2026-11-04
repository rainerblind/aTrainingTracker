# Stage 2: Requirement & Test Specification - ATT-5311: TelemetryMetricGraph Marginal Histogram Domain Boundary Clamping

**Ticket**: [ATT-5311](https://rainerblind.atlassian.net/browse/ATT-5311)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-45.4`  
**Requirement Mapping**: `REQ-UI-443` (`REQ-UI-443.2`, `REQ-UI-443.3`)  
**Test Spec ID**: `TST-UI-443` (`TST-UI-443.2`, `TST-UI-443.3`)  
**Branch**: `feature/ATT-5311`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-03  

---

## 1. Requirement Specification (REQ-UI-443)

### 1.1 Problem Statement & Rationale
In `TelemetryMetricGraph.kt`, marginal histogram drawing loops in portrait and landscape iterate over `linearBins` unconditionally. Bins with `rangeMin >= dataMax` clamp to `topPaddingPx` via `valueToY(...)`, and `.coerceAtLeast(1f)` forces a 1px height. Stacking dozens of `alpha = 0.15f` rectangles at the top canvas boundary creates an opaque solid black horizontal bar (ATT-5311).

### 1.2 Functional & Architectural Requirements
1. **Portrait Domain Filtering & Clamping (`REQ-UI-443.2`)**: In `TelemetryMetricGraph.kt:488-510`, the marginal strip drawing loop SHALL skip out-of-domain bins (`bin.rangeMax <= dataMin || bin.rangeMin >= dataMax`) and clamp each bin's span to `[dataMin, dataMax]`. Bins with clamped height $\le 0$ SHALL NOT be rendered with `.coerceAtLeast(1f)`.
2. **Landscape Domain Filtering & Clamping (`REQ-UI-443.3`)**: In `TelemetryMetricGraph.kt:944-977` (`AlignedMarginalZoneDistribution`), the pane drawing loop SHALL mirror portrait domain filtering and clamping to `[dataMin, dataMax]`, preventing out-of-domain bins from stacking at boundary coordinates.
3. **In-Domain Continuity & Invariant Preservation (`REQ-UI-443.4`)**: All in-domain `linearBins` SHALL retain unconditional background tracks (`alpha = 0.15f`) and duration-gated foreground bars (`bin.durationSec > 0L`). Standalone histogram folding (`TelemetryHistogramChart.kt`), 50dp start padding, and 72%/28% landscape split SHALL remain invariant.

### Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-UI-443.2` & `REQ-UI-443.3`, amending `REQ-UI-251` & `REQ-UI-431`. Targets `TelemetryMetricGraph.kt`.
* **Historical Origin & Commit Trace**: Commits `9d44d0f5` (Sprint 2026-45.2, `ATT-5190`) and `9ce1ef68` (Sprint 2026-45.3, `ATT-5281`). Amending ticket `ATT-5311` (Sprint 2026-45.4).
* **Root Reason for Existing Formulation**: `ATT-5281` made tracks unconditional across `linearBins` to eliminate white horizontal stripes, but `linearBins` exceeds `[dataMin, dataMax]`. Clamping coordinates without filtering bins stacked 1px rects at `y = 0`. Domain filtering and clamping eliminates stacking while preserving track continuity within `[dataMin, dataMax]`.
* **Preservation of Core Invariants**: In-domain track continuity, foreground duration gating, standalone histogram folding, 50dp start padding, 72%/28% landscape split.

### 1.4 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Boundary Stacking Prevention)**: *Given* power bins extending above `dataMax` or below `dataMin`, *When* the marginal histogram renders in portrait or landscape, *Then* out-of-domain bins are filtered and clamped so zero stacked rectangles or black bars appear at `y = 0` / top padding.
* **Criterion 2 (In-Domain Track Continuity)**: *Given* in-domain power bins with `durationSec == 0L`, *When* the marginal histogram renders, *Then* background container tracks (`alpha = 0.15f`) are drawn unconditionally.

---

## 2. Test Specification (TST-UI-443)

### Test Case 1: `testPortraitMarginalHistogram_filtersOutOfDomainBinsAndPreventsTopBarStacking` (`TST-UI-443.2`)
* **Scope**: Contract / Behavioral Unit Test (`TelemetryMetricGraphMarginalBarsContractTest.kt`)
* **Preconditions**: `linearBins` containing bins within `[dataMin, dataMax]` and out-of-domain bins with `rangeMin >= dataMax` (e.g. 500–600 W, 600–700 W when `dataMax = 500 W`).
* **Action**: Simulate portrait strip coordinate mapping and rendering logic.
* **Expected Result**: Out-of-domain bins are excluded; zero rectangles are drawn at `rectTop = 0f` with `rectHeight = 1f`.

### Test Case 2: `testLandscapeMarginalHistogram_filtersOutOfDomainBinsAndPreventsTopBarStacking` (`TST-UI-443.3`)
* **Scope**: Contract / Behavioral Unit Test (`TelemetryMetricGraphLandscapeContractTest.kt`)
* **Preconditions**: Same out-of-domain and in-domain `linearBins` distribution.
* **Action**: Simulate landscape `AlignedMarginalZoneDistribution` coordinate mapping and rendering logic.
* **Expected Result**: Out-of-domain bins are excluded; zero rectangles are drawn at boundary coordinates.

### Test Case 3: 9-Language Localization & Specifier Audit (`TST-UI-443.LOC`)
* **Scope**: Localization Parity Test (`TranslationParityTest`)
* **Expected Result**: 100% parity across all 9 locales; no string modifications required.

### Test Case 4: Targeted Clean-Room Test Suite (`TST-UI-443.REG`)
* **Scope**: Tier 1 Targeted Unit Tests (`aftermath.zones.*`, `map.*`, `build.*`, `ThemeColorCentralizationAuditTest`)
* **Expected Result**: 100% test pass rate.

---

## 3. Traceability Matrix

| Test Case | Scope | Target File | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-UI-443.2` | Contract | `TelemetryMetricGraphMarginalBarsContractTest.kt` | `REQ-UI-443.2` | Specified |
| `TST-UI-443.3` | Contract | `TelemetryMetricGraphLandscapeContractTest.kt` | `REQ-UI-443.3` | Specified |
| `TST-UI-443.LOC` | Localization | `TranslationParityTest.kt` | `REQ-UI-106` | Specified |
| `TST-UI-443.REG` | Regression | Full targeted unit test suite | `REQ-PRO-001`, `REQ-UI-443.4` | Specified |
