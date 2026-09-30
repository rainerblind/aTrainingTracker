# Stage 1 Analysis: ATT-1737 - [ElevationProfile] Resolve visual overlap between multi-metric scrubbing badge and zoom controls

**Ticket**: [ATT-1737](https://atrainingtracker.atlassian.net/browse/ATT-1737)  
**Sub-task**: [ATT-1770](https://atrainingtracker.atlassian.net/browse/ATT-1770) (`[Analysis]`)  
**Parent Epic**: [ATT-355](https://atrainingtracker.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.6`  
**Branch**: `feature/ATT-1737`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-01  

---

## 1. Problem Statement & Motivation

During on-device testing and the sprint review of Sprint `2026-40.5` (evaluating ticket [ATT-1647](https://atrainingtracker.atlassian.net/browse/ATT-1647)), the user demonstrated via on-device screenshot (`screenshot_elevation_overlap.png`) that when scrubbing along the elevation profile in detailed workout inspection views (such as [TrackOnMapScreen.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/TrackOnMapScreen.kt) via [MapDetailLayout.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapDetailLayout.kt)):
> *"When scrubbing along the left side of the elevation profile, the floating multi-metric telemetry badge (`ScrubbingTelemetryBadge` from ATT-1391) collides with and directly overlaps the zoom controls (+, -, pan mode)."*

In `screenshot_elevation_overlap.png`, the user scrubbed to distance `953 m` (3:00, 380 m, -2.0%, 14.7 km/h). The floating telemetry badge rendered at the top of the chart surface with `Alignment.TopCenter` and `padding(top = 2.dp)`. Because the badge spans ~260dp horizontally across a typical 360-400dp mobile screen, its left boundary extended into the interval $x \in [50\text{dp}, 130\text{dp}]$, directly colliding with and obscuring the zoom controls row (`+`, `-`, Pan/Scrub toggle) positioned at `padding(start = 50.dp, top = 2.dp)`. This rendered the controls unreadable and partially untappable.

---

## 2. Root Cause Analysis & Architectural Investigation

### 2.1 Call Sites & Layout Geometry Audit
1. **[ElevationProfile.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/ElevationProfile.kt#L671-L680)**:
   - In ticket `ATT-1647`, vertical clearance was engineered between the zoom controls row at `top = 2.dp` (height 24dp, ending at 26dp) and an old single-line canvas text label whose baseline was placed at $y = -4\text{dp}$ in canvas coordinates with canvas `topPadding = 44.dp` (text occupying $28\text{dp} .. 40\text{dp}$).
   - In ticket `ATT-1391` (`REQ-UI-201`), the multi-metric floating Compose badge `ScrubbingTelemetryBadge` was introduced to display multi-sensor telemetry (HR, Power, Speed/Pace, Altitude, Slope, Time, Distance).
   - However, `ScrubbingTelemetryBadge` was attached with:
     ```kotlin
     modifier = Modifier
         .align(Alignment.TopCenter)
         .padding(top = 2.dp)
     ```
   - This placed `ScrubbingTelemetryBadge` in the vertical interval $[2\text{dp}, 44\text{dp}]$, directly sharing the vertical coordinates of the zoom controls row at $[2\text{dp}, 26\text{dp}]$.
   - Because `ScrubbingTelemetryBadge` is ~260dp wide, centering it horizontally unconditionally forces its left flank into $x \approx 50\text{dp} .. 130\text{dp}$, causing guaranteed collision and occlusion of the zoom buttons.

### 2.2 Root Cause Summary
`ScrubbingTelemetryBadge` was assigned `padding(top = 2.dp)` rather than being vertically separated below the controls row (`top = 2.dp .. 26.dp`), violating the vertical separation architecture intended by `REQ-UI-197`.

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  * Re-architect the vertical layout geometry in [ElevationProfile.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/ElevationProfile.kt) so that the floating `ScrubbingTelemetryBadge` and the zoom controls row occupy strictly non-overlapping vertical bands across all scrubbing positions.
  * Position `ScrubbingTelemetryBadge` at `top = 28.dp` (providing $\ge 2\text{dp}$ clear vertical separation below the controls row ending at 26dp).
  * Adjust `topPadding` of the Canvas to `72.dp` (when `showZoomControls == true`) and adjust `totalCanvasHeight` to `cachedData.adaptiveHeight + 48.dp`, strictly preserving the exact drawable chart plotting height (`cachedData.adaptiveHeight - 48.dp`).
  * Update [ElevationProfileLayoutTest.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/ElevationProfileLayoutTest.kt) to formally verify the 3-layer non-overlapping vertical geometry.
  * Update living specifications in `docs/requirements.md` (`REQ-UI-197`) and `docs/tests.md` (`TST-UI-151`).

* **Out-of-Scope Non-Goals (Scope Bounding)**:
  * Modifying telemetry calculations or formatting within `ScrubbingTelemetryBadge`.
  * Modifying zoom/pan mathematics in `ElevationProfileZoomMath.kt`.
  * Modifying list preview card elevation profiles (where `showZoomControls == false` and `topPadding = 16.dp`).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit (REQ-PRO-022)

### Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**:
  - `REQ-UI-197` (*Elevation Profile Contextual Zoom Controls Visibility & Non-Overlapping Scrubber Text Architecture*), targeting `ElevationProfile.kt` and `MapDetailLayout.kt`.
* **Historical Origin & Commit Trace**:
  - Ticket `ATT-1647` (Commit on `sprint/2026-40.5`) and ticket `ATT-1391` (`REQ-UI-201`).
* **Root Reason for Existing Formulation**:
  - `ATT-1647` established `topPadding = 44.dp` and `totalCanvasHeight = cachedData.adaptiveHeight + 20.dp` to clear the old single-line text label (28..40dp) from the controls row (2..26dp).
  - `ATT-1391` introduced `ScrubbingTelemetryBadge` (~42dp tall) but hardcoded `padding(top = 2.dp)`, placing it directly over the controls row.
* **Preservation of Core Invariants**:
  - Viewport zooming calculations, centroid scaling, pan clamping, adaptive distance ticks, and unit formatting remain 100% intact per `REQ-UI-192`.
  - Exact drawable chart plotting height (`cachedData.adaptiveHeight - 48.dp`) is preserved by expanding `totalCanvasHeight` in tandem with `topPadding`.
  - Preview card zoom suppression (`showZoomControls == false`) and LiveSegment popup zoom suppression (`ATT-1736`) remain 100% intact.

---

## 5. Architectural Strategy & High-Level Solution

### 5.1 Three-Layer Non-Overlapping Vertical Band Architecture
In [ElevationProfile.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/ElevationProfile.kt), when `showZoomControls == true`:
1. **Layer 1: Controls & Legend Row (`top = 2.dp .. 26.dp`)**:
   - Zoom controls row: `Alignment.TopStart`, `padding(start = 50.dp, top = 2.dp)`, height `24.dp`.
   - Legend button: `Alignment.TopEnd`, `padding(end = 4.dp, top = 2.dp)`, height `24.dp`.
   - Vertical interval: $[2.0\text{dp}, 26.0\text{dp}]$.
2. **Clearance 1**: $2.0\text{dp}$ separation between controls bottom (26.0dp) and badge top (28.0dp).
3. **Layer 2: Multi-Metric Telemetry Badge (`top = 28.dp .. 70.dp`)**:
   - `ScrubbingTelemetryBadge`: `Alignment.TopCenter`, `padding(top = 28.dp)`.
   - Maximum visual height: $\approx 42.0\text{dp}$.
   - Vertical interval: $[28.0\text{dp}, 70.0\text{dp}]$.
4. **Clearance 2**: $2.0\text{dp}$ separation between badge bottom (70.0dp) and Canvas curve start (72.0dp).
5. **Layer 3: Chart Plotting Canvas (`top = 72.dp .. `)**:
   - Canvas `topPadding`: `72.dp` (increased from `44.dp`).
   - Canvas `totalCanvasHeight`: `cachedData.adaptiveHeight + 48.dp` (increased from `+ 20.dp`).
   - Exact drawable chart plotting height: $(\text{adaptiveHeight} + 48\text{dp}) - 24\text{dp} - 72\text{dp} = \text{adaptiveHeight} - 48\text{dp}$ (100% invariant).

---

## 6. System Invariants & Risk Assessment

* **Core Invariants**:
  1. No text or control overlap across any scrubbing distance ($0 .. D_{\text{total}}$).
  2. Total drawable chart plotting area height is strictly invariant.
  3. No changes to database, threading, or sensor telemetry.
* **Risk Rating**: **LOW**
  - Pure declarative layout geometry correction eliminating visual collision.
