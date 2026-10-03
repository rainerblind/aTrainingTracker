# Stage 3: Implementation Plan - ATT-2138: Suppress Zoom Controls When Neither Elevation Profile Nor Telemetry Graphs Are Displayed

**Ticket**: [ATT-2138](https://rainerblind.atlassian.net/browse/ATT-2138)  
**Sub-task**: [ATT-2156](https://rainerblind.atlassian.net/browse/ATT-2156) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-111](https://rainerblind.atlassian.net/browse/ATT-111) (*Aftermath: Compact Post-Workout Visual Analytics & Graphs*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `Sprint 2026-40.13`  
**Branch**: `feature/ATT-2138`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-03  

---

## 1. Architectural Design & SWE.2 Structure

To satisfy `REQ-UI-249`, `MapDetailLayout.kt` will couple `hasZoomToolbar` to the actual presence of zoomable graph components.

```
                    MapDetailLayout State Evaluation
                                   │
                                   ├──► showElevationProfile && !activeScrubPath.isNullOrEmpty()
                                   │       OR
                                   └──► hasTelemetryGraphs (showTelemetryCharts && has HR/Speed/Power)
                                           │
                                           ▼
                                 Any Zoomable Graph?
                                   ├──────────────┬──────────────┐
                                  YES             NO            NO
                            (active graphs)  (no graphs)   (no points)
                                   │              │              │
                                   ▼              ▼              ▼
                          hasZoomToolbar = true   hasZoomToolbar = false
                                   │              │
                                   │              ├──► GlobalTelemetryZoomToolbar suppressed
                                   │              ├──► toolbarHeightPx = 0f (Space recovered)
                                   │              └──► scrubbingOverlay suppressed
                                   ▼
                       GlobalTelemetryZoomToolbar rendered
```

---

## 2. Atomic Implementation Steps

### Step 1: Update `hasZoomToolbar` Guard in `MapDetailLayout.kt`
* In `MapDetailLayout.kt` (line 124):
  ```kotlin
  val hasZoomToolbar = showZoomControls && (showElevationProfile || hasTelemetryGraphs) && !activeScrubPath.isNullOrEmpty()
  ```

### Step 2: Implement Contract Test
* Create `app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/MapDetailLayoutZoomContractTest.kt`:
  * Assert `hasZoomToolbar` checks `(showElevationProfile || hasTelemetryGraphs)`.
  * Assert `toolbarHeightPx` is conditionally set to `0f` when `!hasZoomToolbar`.

### Step 3: Run Targeted Unit Tests & Clean-Room Regression
* Run targeted tests:
  ```bash
  ./gradlew testDebugUnitTest --tests "*MapDetailLayoutZoomContractTest*" --tests "*MapDetailLayoutTest*"
  ```
* Run full suite:
  ```bash
  ./gradlew testDebugUnitTest
  ```

---

## 3. Invariants & Risk Mitigation

1. **Active Graph Continuity**: If either `showElevationProfile` is true or `hasTelemetryGraphs` is true, the toolbar behaves identically to previous versions.
2. **Height Calculation Exactness**: `SplitPaneMath.calculateAvailableHeight` consumes `dividerHeightPx + toolbarHeightPx`. When `hasZoomToolbar` is false, `toolbarHeightPx` is `0f`, seamlessly granting the full 36dp height back to the viewport.
3. **No Gesture Conflict**: Suppressing the toolbar completely removes any touch target or overlay on the upper edge of the lower pane.
