# Stage 1 Analysis: ATT-5851 - MapDetailLayout: Prevent floating map buttons (Share & overlay FABs) from colliding with system status bar when header collapses

**Ticket**: [ATT-5851](https://rainerblind.atlassian.net/browse/ATT-5851)  
**Sub-task**: [ATT-6023](https://rainerblind.atlassian.net/browse/ATT-6023) (`[Analysis]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Global UI & Design System*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-45.12`  
**Branch**: `feature/ATT-5851`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-08  

---

## 1. Problem Statement & Motivation
In full-screen detail views utilizing `MapDetailLayout.kt` with `useStatusBarsPadding == true` (e.g. Favorite Routes / `WorkoutClusterHeatmapScreen`, Route Details / `RouteOnMapScreen`, Segment Details / `SegmentOnMapScreen`, and Climb Details / `ClimbOnMapScreen`), scrolling down collapses the top header offscreen (`connection.appBarOffset == -headerHeightPx`), reducing `currentTopPaddingPx` to `0`. While the underlying `ATrainingTrackerMap` bleeds edge-to-edge behind the status bar, the floating Share button (`Modifier.align(Alignment.TopEnd).padding(16.dp)`) and top-aligned `overlay()` FABs (e.g. Marker dropdown at `76.dp`, Layers FAB at `76.dp`, Edit banner at `12.dp`) translate to the screen edge, colliding directly with Android system status bar icons (clock, 5G, battery).

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
In `MapDetailLayout.kt:717-719`, `currentTopPaddingPx = (headerHeightPx + connection.appBarOffset).coerceAtLeast(0)`. In `mapBox`, the floating controls (`overlay()` and the Share button) are placed inside a `Box` whose top position on screen is dictated by `currentTopPaddingPx`. When the header collapses to `0`, the controls remain at fixed top margins (e.g. `16.dp`), directly intersecting the system status bar (`WindowInsets.statusBars.getTop(density)`).
* **Independent Peer Review & Verification**:
  * Inspected `docs/attachments/detail_02_lieblingsstrecke_bottom.png` and `detail_04_segment_bottom.png` via `read_file` (`Rule 71`): Confirmed the Share button sits at `y = 16.dp`, overlapping the `5G` and `100%` battery indicator.
  * Cross-checked callers: `WorkoutClusterHeatmapScreen.kt:519` (Marker button at `top = 76.dp`), `RouteOnMapScreen.kt:360` (Layers button at `top = 76.dp`), `TrackOnMapScreen.kt:469` (Technical Tracks at `top = 76.dp`), and `SegmentOnMapScreen.kt` (Share FAB). All rely on `MapDetailLayout`'s top boundary.
  * Landscape audit: Status bar insets in landscape orientation remain accurately reported by `WindowInsets.statusBars.getTop(density)`, ensuring universal protection.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  * Compute dynamic status bar clearance `floatingControlsTopOffsetDp = with(density) { (statusBarHeightPx - currentTopPaddingPx).coerceAtLeast(0).toDp() }` when `useStatusBarsPadding == true`.
  * Wrap `overlay()` and `SHARE BUTTON` inside `mapBox` in a container with `.fillMaxSize().padding(top = floatingControlsTopOffsetDp)`.
  * Guarantee that map buttons never collide with status bar items while maintaining `16.dp` clearance below the expanded header.
* **Out-of-Scope Non-Goals**:
  * Header click refactoring or bottom-sheet drag handle decoupling (scoped exclusively to `ATT-5835`).
  * Modifying map zoom/scrubbing math or bottom sheet behavior (`useStatusBarsPadding == false`).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-UI-250` (*Collapsing Upper Metadata Architecture & Map Squashing Prevention via NestedScroll in MapDetailLayout*).
* **Historical Origin & Commit Trace**: Introduced in `ATT-2150` (`commit e2b80b2d`) to prevent large metadata from squashing the map by allowing full-screen expansion.
* **Root Reason for Existing Formulation**: `currentTopPaddingPx` dropped to `0` to give the map full-screen bleed, but overlooked status bar encroachment of floating map action controls.
* **Preservation of Core Invariants**: Edge-to-edge map bleed under the status bar is 100% preserved; uncollapsed `16.dp` button spacing below header is preserved; bottom sheet layouts (`useStatusBarsPadding == false`) remain unaltered (`statusBarHeightPx = 0`).

---

## 5. Architectural Strategy & High-Level Solution
* In `MapDetailLayout.kt`:
  1. Calculate `statusBarHeightPx = if (useStatusBarsPadding) WindowInsets.statusBars.getTop(density) else 0`.
  2. Compute `floatingControlsTopOffsetDp = with(density) { (statusBarHeightPx - currentTopPaddingPx).coerceAtLeast(0).toDp() }`.
  3. Pass `floatingControlsTopOffsetDp` to `mapBox`, wrapping `overlay()` and `SHARE BUTTON` in `Box(modifier = Modifier.fillMaxSize().padding(top = floatingControlsTopOffsetDp))`.
  4. Ensure zero disruption to `ATT-5835` lines (drag handle / header click).

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**:
  1. `ATrainingTrackerMap` continues to render edge-to-edge behind the translucent/transparent status bar.
  2. Bottom sheets (`useStatusBarsPadding == false`) maintain zero top offset (`floatingControlsTopOffsetDp == 0.dp`).
  3. No changes to public API of `MapDetailLayout` or caller screens (`RouteOnMapScreen`, `SegmentOnMapScreen`, etc.).
* **Risk Rating**: **LOW** (Strictly scoped layout padding calculation inside `mapBox` without logic, threading, or data mutations).
