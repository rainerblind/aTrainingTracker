# Stage 3: Implementation Plan - ATT-2145: Landscape Compact Aligned Marginal Zone Distribution Bars

**Ticket**: [ATT-2145](https://rainerblind.atlassian.net/browse/ATT-2145)  
**Parent Epic**: [ATT-2144](https://rainerblind.atlassian.net/browse/ATT-2144) (*Landscape Mode*)  
**Target Release**: `V4.9.41` | **Active Sprint**: `sprint/2026-43.9` | **Branch**: `feature/ATT-2145`  
**Requirement Mapping**: `REQ-UI-431` (`REQ-UI-431.1`–`6`)  
**Test Mapping**: `TST-UI-396` (`TST-UI-396.1`–`6`)  
**Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-10-23  

---

## 1. Problem Description & Architectural Context
In aftermath workout analysis, athletes inspect telemetry graphs (HR, Power, Cadence, Pace) across physiological training zones. While portrait mode uses an ultra-slim 25dp inline canvas strip (`REQ-UI-251`), its restricted width cannot render zone labels, percentages, or durations. In landscape mode (~640–900dp width), abundant horizontal space enables decoupling the marginal zone distribution into a dedicated ~28% side-by-side pane docked directly to the right of the ~72% telemetry graph. This plan designs a responsive architecture ensuring exact 1:1 vertical Y-axis coordinate alignment with graph zone thresholds, legible metrics text (`Z5 45% 19:40 min`), synchronous mode switching (`FIVE_ZONES` vs. `HISTOGRAM`), and byte-for-byte portrait fidelity.

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-UI-431` (*Landscape Compact Aligned Marginal Zone Distribution Bars Right of Telemetry Graph*)
* **Test Mapping**: `TST-UI-396` (*Landscape Compact Aligned Marginal Zone Distribution Bars Verification Suite*)

---

## 3. System Invariants & Preserved Behavior
1. **Portrait Invariant Fidelity**: In portrait (`Configuration.ORIENTATION_PORTRAIT`), `TelemetryMetricGraph` retains the 25dp inline strip per `REQ-UI-251` with 100% byte-for-byte fidelity.
2. **Multi-Chart Scrubber Alignment**: `startPadding = 50.dp` remains strictly identical across both orientations, ensuring synchronized vertical scrubbing with `ElevationProfile`.
3. **Gesture & Callback Integrity**: Drag scrubbing, tap selection, pan/zoom, and callbacks (`onDistanceSelected`, `onPointSelected`, `onZoomChanged`) operate with zero suppression (Rule 34).
4. **Data & Schema Neutrality**: Zero database schema mutations; pure presentation-layer Compose enhancement.

---

## 4. Proposed Architectural Changes & UI Consistency
* **Orientation Branching (`TelemetryMetricGraph.kt`)**: Detect `LocalConfiguration.current.orientation`. When `isLandscape && zoneDistribution != null`, compose a horizontal `Row`:
  - *Left Chart Pane (`Modifier.weight(0.72f)`)*: Continuous curve canvas, omitting the inline 25dp strip to maximize curve detail.
  - *Right Marginal Pane (`Modifier.weight(0.28f)`)*: Aligned zone bars canvas with exact matching plot paddings (`topPadding = 10.dp`, `bottomPadding = 24.dp`, height = 110.dp).
* **Aligned Zone Bar Rendering**: Map zone thresholds to Y coordinates using the same normalization formula. Draw weighted bars with overlaid high-contrast text: Zone Label (`Z1`–`Z5`), Percentage share, and formatted Duration (`TimeFormatter.formatElapsedSeconds`).
* **Synchronous Mode Observability**: Observe hoisted `zoneDisplayMode: ZoneCardDisplayMode` for seamless switching between 5-zone horizontal bars and histogram frequency bars.
* **UI Consistency**: Reuses `MaterialTheme.colorScheme`, `TimeFormatter`, `TelemetryZoneMath`, and standard zone colors (`ColorZoneUtils`). Includes light/dark `@Preview` composables.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Code Lock Verification (Rule 4)
* Confirm exit code 0 via `python3 tools/jira_util.py check-gate <Impl-Plan-Subtask-Key>`.

### Step 2: Aligned Marginal Zone Distribution Composable
* **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/TelemetryMetricGraph.kt` (or internal helper)
* **Change**: Implement `AlignedMarginalZoneDistribution` composable rendering horizontal zone bars (5-zone and histogram modes) with 1:1 Y-axis coordinate alignment and legible label/percentage/duration typography.

### Step 3: Landscape Dual-Pane Composition in TelemetryMetricGraph
* **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/TelemetryMetricGraph.kt`
* **Change**: Integrate orientation detection. When `isLandscape && zoneDistribution != null`, split layout into 72/28 `Row`. Retain portrait inline strip when in portrait mode. Add `@Preview` composables.

### Step 4: Contract & Architectural Unit Tests
* **File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/TelemetryMetricGraphLandscapeContractTest.kt`
* **Tests**: Implement `TST-UI-396.1`–`5`: landscape 72/28 split, 1:1 vertical Y-axis coordinate alignment, granular text formatting, mode switching, and portrait regression safety.

### Step 5: Verification & Governance Audit
* Run:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.map.*"
  python3 tools/verify_requirement_governance.py
  ```

---

## 6. Verification & Rollback Plan
* **Verification**: Targeted unit tests (`TelemetryMetricGraphLandscapeContractTest`, `TelemetryMetricGraphMarginalBarsContractTest`), followed by full suite regression (`./gradlew testDebugUnitTest`).
* **Rollback**: Discard uncommitted changes via `git checkout -- . && git clean -fd`.
