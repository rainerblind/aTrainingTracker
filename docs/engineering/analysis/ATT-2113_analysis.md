# Stage 1 Analysis: ATT-2113 - [UI/UX] [Aftermath] Align Scrubbing Telemetry Badge with Zoom Controls and Offset to the Right

**Ticket**: [ATT-2113](https://rainerblind.atlassian.net/browse/ATT-2113)  
**Sub-task**: [ATT-2120](https://rainerblind.atlassian.net/browse/ATT-2120) (`[Analysis]`)  
**Parent Epic**: [ATT-111](https://rainerblind.atlassian.net/browse/ATT-111) (*Compact Post-Workout Visual Analytics & Graphs*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `Sprint 2026-40.13`  
**Branch**: `feature/ATT-2113`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-03  

---

## 1. Problem Statement & Motivation

In Sprint 2026-40.12 (`ATT-2016` / `REQ-UI-241`), the floating `ScrubbingTelemetryBadge` was hoisted from within `ElevationProfile.kt` into the persistent lower viewport container of `MapDetailLayout.kt`. This resolved the problem where scrolling graphs buried the badge.

During on-device physical verification of Sprint 2026-40.12 on Google Pixel 10 (Android 16), the human tester observed:
> *"I already had a look at it and can confirm that it works. Thanks a lot. However, it is currently shown below the 'control zooming' buttons. The box should be moved upwards such that its top aligns with the top of the control zoom buttons (more or less). Moreover, from my point of view, it should be moved more to the right."*

Currently:
1. **Vertical Offset**: In `MapDetailLayout.kt`, `GlobalTelemetryZoomToolbar` (36dp height) is declared as an independent sibling in the parent `Column`. The `Box` containing `scrubbingOverlay()` begins *below* the toolbar, forcing the badge 36dp down into the chart viewport.
2. **Horizontal Centering**: The badge is pinned with `.align(Alignment.TopCenter)` and `.padding(top = 4.dp)`, placing it squarely in the middle horizontally. While the zoom controls occupy the left ~116dp of the toolbar, the right half of the toolbar is vacant space. Centering the badge below the toolbar causes it to needlessly occlude the top curve of the elevation or telemetry graphs.

---

## 2. Root Cause Analysis (Forensic Investigation)

### 2.1 Viewport Container Hierarchy in `MapDetailLayout.kt`
Lines 595–623 of `MapDetailLayout.kt`:
```kotlin
// PERSISTENT STICKY GLOBAL ZOOM TOOLBAR (REQ-UI-225 / ATT-1876)
if (hasZoomToolbar) {
    GlobalTelemetryZoomToolbar(
        ...
        modifier = Modifier.fillMaxWidth()
    )
}

Box(
    modifier = Modifier
        .weight(1f - splitFraction)
        .fillMaxWidth()
) {
    lowerColumn(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    )
    scrubbingOverlay()
}
```
Because `GlobalTelemetryZoomToolbar` sits outside `Box`, the top of `Box` is positioned at $y = \text{toolbarHeightPx}$ relative to the bottom of `SplitPaneDivider`. Any composable inside `scrubbingOverlay()` aligned to `Alignment.TopCenter` or `Alignment.TopEnd` begins 36dp below the toolbar.

### 2.2 Toolbar Horizontal Real Estate Analysis
`GlobalTelemetryZoomToolbar.kt` allocates `36.dp` height and renders a horizontal `Row` with `padding(horizontal = 16.dp)` and `spacedBy(8.dp)`:
- Zoom Out (`-`): 28dp x 28dp
- Zoom In (`+`): 28dp x 28dp
- Pan/Scrub Toggle: 28dp x 28dp
- (Optional) Zoom Reset Pill: ~60dp x 24dp
On a typical mobile viewport (e.g. 412dp on Pixel 10), the left controls occupy at most ~184dp. The remaining ~228dp on the right side of the toolbar is completely empty.

### 2.3 Visual Co-Location Opportunity
`ScrubbingTelemetryBadge` has a compact 2-row layout with an intrinsic height of approximately 34–36dp and typical width of ~180–200dp. Moving the badge into the sticky toolbar row area on the right (`Alignment.TopEnd`) creates visual alignment with the zoom controls on the left:
- Top edges align side by side ($y \approx 2\text{--}4\text{dp}$).
- The badge utilizes previously wasted horizontal space in the sticky control bar.
- The underlying elevation and telemetry graphs in `lowerColumn` gain unobstructed vertical clearance.

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  - Restructure `MapDetailLayout.kt` lower viewport so that `GlobalTelemetryZoomToolbar` and `lowerColumn` share a common parent viewport `Box` hosting `scrubbingOverlay()`.
  - Update `scrubbingOverlay` to anchor `ScrubbingTelemetryBadge` at `Alignment.TopEnd` with appropriate padding (`top = 2.dp, end = 8.dp`) so its top edge aligns with the zoom buttons on the left.
  - Update both split-pane branches (`showMap && hasScrollableContent` and `!showMap && hasScrollableContent`) consistently.
  - Update contract tests (`MapDetailLayoutScrubbingBadgeContractTest.kt`) to verify the new alignment and placement.
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  - Do NOT modify the internal contents, metrics, formatting, or zone coloring of `ScrubbingTelemetryBadge`.
  - Do NOT alter zoom math, pan gestures, or `SplitPaneDivider` behavior.
  - Do NOT affect `lowerColumn` scroll contents or slot architecture (`metadataContent`, `analyticsContent`).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit

* **Original Requirement ID & Target**: `REQ-UI-241` (*Aftermath/Scrubbing: Persistent Floating Scrubbing Telemetry Badge Pinned Above Scrollable Graphs in MapDetailLayout*), refined by net-new requirement `REQ-UI-246` under Epic `ATT-111`.
* **Historical Origin & Commit Trace**: Commit `0064fba1` (`ATT-2016`, Sprint 2026-40.12) originally hoisted `ScrubbingTelemetryBadge` to `MapDetailLayout.kt` and placed it at `Alignment.TopCenter` below the toolbar.
* **Root Reason for Existing Formulation**: In `ATT-2016`, the author placed `scrubbingOverlay()` inside the lower `Box` below `GlobalTelemetryZoomToolbar`, centered horizontally because `ElevationProfile` historically had a centered layout.
* **Preservation of Core Invariants**:
  - Floating sticky behavior across scroll depth is 100% preserved.
  - Universal scrubbing across all graphs (Elevation, Speed, HR, Power) is 100% preserved.
  - Trackless workout scrubbing along the time axis is 100% preserved.
  - Dynamic zone badge colors (`REQ-UI-242`) and touch gesture transparency are 100% preserved.
  - The refined positioning improves ergonomic usability and eliminates chart occlusion.

---

## 5. Architectural Strategy & High-Level Solution

### 5.1 Unified Lower Viewport Container (`MapDetailLayout.kt`)
In `MapDetailLayout.kt`, for both `showMap && hasScrollableContent` and `!showMap && hasScrollableContent`:
```kotlin
Box(
    modifier = Modifier
        .weight(1f - splitFraction)
        .fillMaxWidth()
) {
    Column(modifier = Modifier.fillMaxSize()) {
        if (hasZoomToolbar) {
            GlobalTelemetryZoomToolbar(...)
        }
        lowerColumn(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
        )
    }
    scrubbingOverlay()
}
```

### 5.2 TopEnd Alignment with Top Button Alignment
In `scrubbingOverlay`:
```kotlin
ScrubbingTelemetryBadge(
    ...
    modifier = Modifier
        .align(Alignment.TopEnd)
        .padding(top = 2.dp, end = 8.dp)
)
```
Since the `Box` begins immediately below `SplitPaneDivider`, `GlobalTelemetryZoomToolbar` starts at $y = 0$. The zoom buttons have a 28dp height inside a 36dp toolbar centered vertically (top at $y = 4\text{dp}$). With `padding(top = 2.dp)`, the top of the badge aligns cleanly with the top of the zoom buttons.

---

## 6. System Invariants & Risk Assessment

* **Core Invariants**:
  1. Zero regression on scrubbing gesture resolution or zoom toolbar interactions.
  2. Single-finger vertical scroll on `lowerColumn` remains completely unhindered.
  3. Clean-room unit test suite pass rate remains 100%.
  4. Parent ticket Human Decision Gate remains strictly enforced.
* **Risk Rating**: **LOW**
  - Layout-only modifier refinement within existing Compose containers.
  - Zero state management or persistence modifications.
