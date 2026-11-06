# Stage 3: Implementation Plan - ATT-5486: Fixed TTColor.Zone1 Green for Marginal Altitude Histogram

**Ticket**: [ATT-5486](https://rainerblind.atlassian.net/browse/ATT-5486)  
**Sub-task**: [ATT-5517](https://rainerblind.atlassian.net/browse/ATT-5517) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-826](https://rainerblind.atlassian.net/browse/ATT-826) (*Laps*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.7` | **Branch**: `feature/ATT-5486`  
**Requirement Mapping**: `REQ-UI-446` (`REQ-UI-446.2`) | **Test Mapping**: `TST-UI-446` (`TST-UI-446.2`)  
**Author**: AI Agent 1 (Implementer) | **Date**: 2026-11-06  

---

## 1. Problem Description & Background
In `ElevationProfile.kt` (line 502), the marginal altitude histogram currently samples the instantaneous slope gradient of the first track segment (`cachedData.segments.firstOrNull()?.color ?: colorScheme.primary`). Because segment 0 slope varies with the initial driveway or terrain incline, the entire altitude frequency strip changes color non-deterministically (Green for flat, Orange for moderate incline, Red for steep incline). ATT-5486 decouples altitude distribution from initial slope by assigning a fixed `TTColor.Zone1` base green.

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-UI-446.2` (*Series-Color Rendering for Non-Zoned Graphs*, amended for `ATT-5486`)
* **Test Mapping**: `TST-UI-446.2` (TC-1: Steep start color invariance, TC-2: Flat start color invariance), `TST-UI-446.4` (Targeted suite)

---

## 3. System Invariants & Preserved Behavior
1. **Rule 64 Plot Alignment**: Strict 1:1 horizontal plot-width alignment (50dp start / 52dp end in portrait; 72%/28% dual-pane in landscape).
2. **Rule 69 & 70 Histogram Rendering**: Unconditional background tracks (`alpha = 0.15f`) and domain-boundary clamping preventing out-of-domain stacking at `y = 0`.
3. **Caller Boundary Invariance**: Non-stacked callers continue defaulting to `alignWithTelemetryCharts = false` without marginal histograms.
4. **Database & Service Safety**: Zero SQLite schema, repository, or service alterations. 100% UI composable change.

---

## 4. Proposed Architectural Changes
* **Component 1: `ElevationProfile.kt` (`ui/map/`)**:
  - In `remember(pathPoints, cachedData.minAlt, cachedData.maxAlt, alignWithTelemetryCharts)`:
    - Replace `val altColor = cachedData.segments.firstOrNull()?.color ?: colorScheme.primary` with `val altColor = TTColor.Zone1`.
    - Drop unused `colorScheme` argument from the `remember` key list.
* **UI Consistency (Rule 23)**:
  - Reference component: `ElevationProfile.kt` / `TelemetryMetricGraph.kt`.
  - Reused tokens: `TTColor.Zone1` (canonical terrain base green `#4CAF50`), matching `GradeLegendItem` flat/descent (<2%).
  - Zero new one-off styles or theme alterations.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)
* **Step 1: Assign Fixed `TTColor.Zone1` in `ElevationProfile.kt`**:
  - File: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/ElevationProfile.kt`
  - Replace line 502 with `val altColor = TTColor.Zone1`.
* **Step 2: Add Unit & Contract Verification in `ElevationProfileGradeColorTest.kt`**:
  - File: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/ElevationProfileGradeColorTest.kt`
  - Add `testElevationProfile_marginalAltitudeHistogramColorIsFixedZone1Green()` asserting `altColor = TTColor.Zone1` invariance regardless of initial gradient.
* **Step 3: Targeted Unit & Governance Test Execution**:
  - `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.map.*" --tests "com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest"`
  - `python3 tools/verify_requirement_governance.py`

---

## 6. Verification & Rollback Plan
* **Verification**: Targeted unit tests and `verify_requirement_governance.py`.
* **Rollback**: UI-only change; clean revert on `feature/ATT-5486`.
