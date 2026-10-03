# Stage 1: Problem Domain & Root Cause Analysis - ATT-2138: Suppress Zoom Controls When Neither Elevation Profile Nor Telemetry Graphs Are Displayed

**Ticket**: [ATT-2138](https://rainerblind.atlassian.net/browse/ATT-2138)  
**Sub-task**: [ATT-2154](https://rainerblind.atlassian.net/browse/ATT-2154) (`[Analysis]`)  
**Parent Epic**: [ATT-111](https://rainerblind.atlassian.net/browse/ATT-111) (*Aftermath: Compact Post-Workout Visual Analytics & Graphs*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `Sprint 2026-40.13`  
**Branch**: `feature/ATT-2138`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-03  

---

## 1. Problem Domain & Forensic Investigation

### 1.1 Physical Device Observation & Reproduction
In the detailed workout view (`TrackOnMapScreen` / `MapDetailLayout`), a persistent sticky zoom toolbar (`GlobalTelemetryZoomToolbar`) provides zoom and pan controls (`-`, `+`, Pan/Touch, Reset) for horizontal time/distance magnification across synchronized graphs.

When an athlete uses the Advanced Settings matrix table (`workoutDetailPrefs`) to disable both:
1. "Höhenprofil" (`showElevationProfile = false`), and
2. "Telemetrie-Diagramme" (`showTelemetryCharts = false`),
or when inspecting a workout recorded without sensor telemetry (no speed, HR, or power data), the zoom toolbar remains permanently visible directly beneath the map split divider.

In this state:
* There are zero scrollable curves or telemetry charts rendered in `lowerColumn`.
* The zoom buttons (`-`, `+`, Reset) perform no visible action.
* The 36dp toolbar needlessly consumes screen real estate, compressing the remaining content (map, metadata, lap splits, zone cards).

### 1.2 Architectural Root Cause
In `MapDetailLayout.kt` (line 124):
```kotlin
val hasZoomToolbar = showZoomControls && activeScrubPath != null && activeScrubPath.isNotEmpty()
```
The guard condition only checks if `showZoomControls` is true and `activeScrubPath` contains points. It does NOT evaluate whether any zoom-compatible graph is actually rendered.
Specifically:
* `showElevationProfile` is ignored.
* `hasTelemetryGraphs` (which checks `showTelemetryCharts` and the presence of HR, Speed, or Power data) is ignored.

---

## 2. Requirement Archaeology & Chesterton's Fence Audit

1. **Original Requirement Target**:
   `REQ-UI-225` (`ATT-1876`: Persistent Sticky Global Zoom Toolbar Between Map and Telemetry Graphs).
2. **Historical Origin**:
   When `GlobalTelemetryZoomToolbar` was introduced in Sprint 2026-40.6, it assumed that if `activeScrubPath` was present, the elevation profile was always visible. Later, in Sprint 2026-40.12 (`ATT-2030`), independent toggling of individual sections (`WorkoutDetailPreferences`) was introduced, making it possible to disable `showElevationProfile` and `showTelemetryCharts` independently.
3. **Chesterton's Fence Rationale**:
   The toolbar was designed specifically to control horizontal scaling of the elevation profile and telemetry metric graphs. When neither component is displayed, the toolbar has no purpose and should be suppressed.
4. **Preserved Invariants**:
   * When `showElevationProfile` is true and points exist -> toolbar remains visible.
   * When `hasTelemetryGraphs` is true (even if elevation profile is off) -> toolbar remains visible.
   * Toolbar height (`36dp`) in `SplitPaneMath.calculateAvailableHeight` already defaults to `0f` when `!hasZoomToolbar`, automatically returning the vertical space to the viewport.
   * Touch gestures and contract tests for `SplitPaneMath` are preserved.

---

## 3. Scope Bounding & Proposed Architecture

* **Condition Update in `MapDetailLayout.kt`**:
  ```kotlin
  val hasZoomToolbar = showZoomControls && (showElevationProfile || hasTelemetryGraphs) && !activeScrubPath.isNullOrEmpty()
  ```
* **Structural Contract Test**:
  Add assertions in `MapDetailLayoutZoomContractTest.kt` verifying that `hasZoomToolbar` evaluates `(showElevationProfile || hasTelemetryGraphs)`.
