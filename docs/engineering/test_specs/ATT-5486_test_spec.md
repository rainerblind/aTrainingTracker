# Stage 2: Requirement & Test Specification - ATT-5486: Fixed TTColor.Zone1 Green for Marginal Altitude Histogram

**Ticket**: [ATT-5486](https://rainerblind.atlassian.net/browse/ATT-5486)  
**Sub-task**: `[Req & Test Spec]`  
**Parent Epic**: [ATT-826](https://rainerblind.atlassian.net/browse/ATT-826) (*Laps*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.7` | **Branch**: `feature/ATT-5486`  
**Requirement Mapping**: `REQ-UI-446` (`REQ-UI-446.2`) | **Test Spec ID**: `TST-UI-446` (`TST-UI-446.2`)  
**Author**: AI Agent 1 (`subagent_flash`) | **Date**: 2026-11-06  

---

## 1. Requirement Specification (`REQ-UI-446.2`)

### 1.1 Problem Statement & Rationale
In `ElevationProfile.kt` (line 502), the marginal altitude histogram (`altitudeHistogram`) currently derives its bar color dynamically from the first segment: `val altColor = cachedData.segments.firstOrNull()?.color ?: colorScheme.primary`. Because segments are colored by instantaneous slope (`getElevationGradeColor(grade)`), starting on an incline (>5% or GPS elevation spike) turns the entire altitude frequency distribution orange or red (`TTColor.Zone3`/`Zone4`). Altitude represents elevation above sea level, not slope; the marginal histogram SHALL use a consistent base color (`TTColor.Zone1` Green) regardless of initial driveway gradient.

### 1.2 Functional & Architectural Requirements
1. **Fixed Base Green for Marginal Altitude Histogram (`REQ-UI-446.2`)**:
   - In `ElevationProfile.kt`, `altitudeHistogram` SHALL render using fixed `TTColor.Zone1` green across both portrait (`endPaddingPx = 52.dp`) and landscape dual-pane (`AlignedMarginalZoneDistribution`) modes.
   - Remove dynamic lookup `cachedData.segments.firstOrNull()?.color` and unused `colorScheme` dependency from the `altitudeHistogram` `remember` block.
2. **Preservation of Core Chart Invariants**:
   - Maintain 1:1 horizontal plot-width alignment and scrubbing cursor synchronization (Rule 64).
   - Maintain unconditional `alpha = 0.15f` background container tracks across `[dataMin, dataMax]` (Rule 69).
   - Maintain domain-boundary clamping and filtering preventing out-of-domain bin stacking at `y = 0` (Rule 70).
   - Maintain `alignWithTelemetryCharts = false` default for non-stacked callers (`SensorGridScreen`, `LiveClimbSheet`, `ClimbDetailSheet`, `RouteItem`, `SegmentItem`).

### 1.3 Acceptance Criteria (Given-When-Then)
* **AC-1 (Steep Start Color Invariance)**: *Given* a track where the initial segment has a steep climb gradient (e.g. +12% slope $\rightarrow$ Zone 4 Red), *When* `ElevationProfile` renders with `alignWithTelemetryCharts = true`, *Then* the marginal altitude histogram bars render in `TTColor.Zone1` Green, NOT Zone 4 Red.
* **AC-2 (Flat/Descent Start Invariance)**: *Given* a track starting on flat/descent (<2% slope), *When* `ElevationProfile` renders, *Then* the marginal altitude histogram bars render in `TTColor.Zone1` Green.
* **AC-3 (Landscape Dual-Pane Parity)**: *Given* landscape orientation (`Configuration.ORIENTATION_LANDSCAPE`), *When* `AlignedMarginalZoneDistribution` renders for altitude, *Then* it uses `TTColor.Zone1` Green matching portrait mode.

### Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Amends `REQ-UI-446.2` (*Series-Color Rendering for Non-Zoned Graphs*, `ATT-5370`). Targets `ElevationProfile.kt`.
* **Historical Origin & Commit Trace**: Commit `75485932` (`ATT-5370`, Sprint 2026-45.5) introduced universal marginal histograms across stacked workout charts and sampled segment 0 as a primary stroke color proxy.
* **Root Reason for Existing Formulation**: `ATT-5370` attempted to match each graph's marginal histogram to its primary stroke color. Because `ElevationProfile` lacks a single stroke color and consists of multi-colored gradient segments, segment 0 was sampled as an improvised proxy (`cachedData.segments.firstOrNull()?.color`), causing the entire altitude histogram to turn orange or red if the activity began on an incline.
* **Preservation of Core Invariants**: Retains 1:1 plot alignment (Rule 64, 52dp portrait / 72% landscape), unconditional background tracks (Rule 69), domain-boundary clamping (Rule 70), and clean-room test stability.

---

## 2. Test Specification (`TST-UI-446.2`)

### 2.1 Test Cases
1. **TC-1: Steep Start Slope Gradient Invariance (`TST-UI-446.2`)**:
   - *Scope*: Unit test in `ElevationProfileGradeColorTest.kt`.
   - *Procedure*: Construct elevation track with steep initial segment (+12% grade). Verify `altColor` equals `TTColor.Zone1` and does not inherit segment 0 slope color (`TTColor.Zone4`).
2. **TC-2: Flat Start Slope Invariance (`TST-UI-446.2`)**:
   - *Scope*: Unit test in `ElevationProfileGradeColorTest.kt`.
   - *Procedure*: Construct track starting with flat terrain (0.5% grade). Verify `altColor` equals `TTColor.Zone1`.
3. **TC-3: Targeted Test Suite Regression (`TST-UI-446.4`)**:
   - *Command*: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.map.*" --tests "com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest"`
   - *Expected*: 100% pass rate across map and theme audit suites.

---

## 3. Traceability Matrix
| Requirement ID | Test Case ID | Scope | Status |
| :--- | :--- | :--- | :--- |
| `REQ-UI-446.2` | `TST-UI-446.2` (TC-1, TC-2) | Unit (`ElevationProfileGradeColorTest`) | Planned (`ATT-5486`) |
| `REQ-UI-446.3` | `TST-UI-446.3` (TC-3) | Contract (`TelemetryMetricGraphLandscapeContractTest`) | Verified |
| `REQ-UI-446.4` | `TST-UI-446.4` (TC-3) | Full Suite (`TranslationParityTest`, `ThemeAudit`) | Verified |
