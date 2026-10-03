# Stage 2: Requirement & Test Specification - ATT-2138: Suppress Zoom Controls When Neither Elevation Profile Nor Telemetry Graphs Are Displayed

**Ticket**: [ATT-2138](https://rainerblind.atlassian.net/browse/ATT-2138)  
**Sub-task**: [ATT-2155](https://rainerblind.atlassian.net/browse/ATT-2155) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-111](https://rainerblind.atlassian.net/browse/ATT-111) (*Aftermath: Compact Post-Workout Visual Analytics & Graphs*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `Sprint 2026-40.13`  
**Branch**: `feature/ATT-2138`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-03  

---

## 1. Formal Requirements Specification

### REQ-UI-249: Aftermath/Zoom: Reactive Zoom Controls Suppression in MapDetailLayout When No Zoomable Graphs Are Rendered
The system SHALL suppress `GlobalTelemetryZoomToolbar` in `MapDetailLayout.kt` whenever neither an elevation profile nor any telemetry metric graphs are rendered, eliminating non-functional UI clutter and returning vertical space to the viewport (ATT-2138):

1. **Toolbar Activation Guard (`hasZoomToolbar`)**:
   * In `MapDetailLayout.kt`, `hasZoomToolbar` SHALL evaluate to `true` IF AND ONLY IF:
     - `showZoomControls == true`, AND
     - `(showElevationProfile || hasTelemetryGraphs) == true`, AND
     - `!activeScrubPath.isNullOrEmpty()`.
   * If both `showElevationProfile` and `hasTelemetryGraphs` are false, `hasZoomToolbar` SHALL evaluate to `false`.

2. **Vertical Space Recovery**:
   * In `BoxWithConstraints` within `MapDetailLayout.kt`, `toolbarHeightPx` SHALL evaluate to `0f` when `!hasZoomToolbar`.
   * `SplitPaneMath.calculateAvailableHeight` SHALL exclude toolbar height when `hasZoomToolbar == false`, allocating the full vertical height to the map and remaining scrollable content.

3. **Overlay Invariants**:
   * When `hasZoomToolbar == false`, `scrubbingOverlay` SHALL NOT render, preventing phantom scrubbing telemetry badges.

4. **Preserved Invariants**:
   * Standalone elevation profiles (`showElevationProfile == true`) retain full zoom and pan functionality.
   * Telemetry-only inspections (`showElevationProfile == false && hasTelemetryGraphs == true`) retain full zoom and pan functionality across Speed, HR, and Power graphs.

---

## 2. Formal Test Specification

### TST-UI-208: Aftermath/Zoom: Reactive Zoom Controls Suppression in MapDetailLayout Verification
* **Scope**: Verification of `REQ-UI-249` via architectural contract test, unit regression, and clean-room full suite execution.

#### Test Cases:
1. **Structural Guard Assertion (`MapDetailLayoutZoomContractTest.kt`)**:
   * Inspect `MapDetailLayout.kt` source AST/text.
   * Assert `hasZoomToolbar` formulation includes `(showElevationProfile || hasTelemetryGraphs)`.
   * Assert `toolbarHeightPx` evaluates to `0f` when `!hasZoomToolbar`.
2. **Full Regression Execution**:
   * Execute `./gradlew testDebugUnitTest` verifying 100% pass rate.
