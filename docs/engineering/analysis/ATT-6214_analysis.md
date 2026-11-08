# Stage 1 Analysis: ATT-6214 - Move floating Layers button to top=16dp and unify TopEnd actions stacking

**Ticket**: [ATT-6214](https://rainerblind.atlassian.net/browse/ATT-6214)  
**Sub-task**: `[Analysis]`  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Global UI & Design System*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-45.13`  
**Branch**: `feature/ATT-6214`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-08  

---

## 1. Problem Statement & Motivation
In ATT-5809, the workout share button was unified into `WorkoutHeader`, suppressing the floating Share FAB on map detail views (`showFloatingShareButton = workoutData == null`). However, in `TrackOnMapScreen.kt:324, 470`, the technical tracks Layers button retains a legacy hardcoded `padding(top = 76.dp, end = 16.dp)`, leaving an awkward 60dp empty gap above the button when the Share button is absent. Furthermore, `RouteOnMapScreen.kt:360` also hardcodes `top = 76.dp`, creating fragile, duplicated manual layout math across map screens.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
1. **Legacy Hardcoded Offset in Callers (`TrackOnMapScreen.kt:324, 470`)**:
   - `MapDetailLayout` renders floating controls inside `Box(Modifier.fillMaxSize().padding(top = controlsTopOffsetDp))` (REQ-UI-491).
   - In `TrackOnMapScreen.kt`, the Layers button specifies `Modifier.align(Alignment.TopEnd).padding(top = 76.dp, end = 16.dp)`. This offset was calculated as `16.dp (top padding) + 44.dp (Share button) + 16.dp (spacing) = 76.dp`.
   - With `showFloatingShareButton == false` for workouts, the Share button is suppressed, leaving the top 60dp unoccupied and placing the Layers button in mid-air.
2. **Independent Peer Review & Verification**:
   - Verified `MapDetailLayout.kt:360-406`: Share button is conditionally rendered at `Alignment.TopEnd` with `padding(16.dp)`. The `overlay()` slot has no layout coordination with the Share button.
   - Verified `RouteOnMapScreen.kt:360`: Also hardcodes `padding(top = 76.dp, end = 16.dp)` to dodge the Share button.
   - Hypothesis confirmed: Introducing a unified `topEndActions: @Composable (ColumnScope.() -> Unit)? = null` slot in `MapDetailLayout` inside `Column(Modifier.align(Alignment.TopEnd).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp))` enables automatic stacking. When the Share button is hidden, the first action naturally docks at `top = 16.dp`; when present, subsequent actions stack at `76.dp` with zero hardcoding.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  - In `MapDetailLayout.kt`, introduce `topEndActions: @Composable (ColumnScope.() -> Unit)? = null` within an adaptive `Column(Modifier.align(Alignment.TopEnd).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp))`.
  - In `WorkoutDetailTabbedContent.kt`, expose `mapTopEndActions` forwarding to `MapDetailLayout`.
  - In `TrackOnMapScreen.kt:324, 470`, migrate the Layers button to `topEndActions` so it docks at `top = 16.dp`.
  - In `RouteOnMapScreen.kt:350`, migrate the route layers button to `topEndActions`, eliminating hardcoded `76.dp`.
  - Add layout contract tests in `MapDetailLayoutTest.kt` / `TrackOnMapScreenTest.kt`.
* **Out-of-Scope Non-Goals**:
  - Modifying general map layers in `MapScreenWithTrack.kt` (`Karten -> Karte`, which has its own full-screen HUD).
  - Altering dropdown menu items or track layer visibility logic.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Amends `REQ-UI-308` (Clause 2) and complements `REQ-UI-491`, establishing `REQ-UI-500` (*Unified MapDetailLayout Top-End Floating Actions Stacking & Adaptive Layers Placement*).
* **Historical Origin & Commit Trace**: `ATT-2763` (`REQ-UI-308`), `ATT-5809` (share button moved to WorkoutHeader), `ATT-5851` (`REQ-UI-491`).
* **Root Reason for Existing Formulation**: `top = 76.dp` was hardcoded in individual callers as a quick manual offset to prevent overlapping the Share button before `showFloatingShareButton` became dynamic.
* **Preservation of Core Invariants**: Dynamic status bar inset clearance (`controlsTopOffsetDp`, REQ-UI-491), 44dp button sizes, `TTAlpha.Overlay` surface styling, and dropdown menu behavior MUST remain strictly preserved.

---

## 5. Architectural Strategy & High-Level Solution
* `MapDetailLayout.kt`: Add `topEndActions: @Composable (ColumnScope.() -> Unit)? = null`. Replace standalone Share `Surface` with `Column(Modifier.align(Alignment.TopEnd).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp), horizontalAlignment = Alignment.End) { if (showFloatingShareButton) ShareSurface(); topEndActions?.invoke(this) }`.
* `WorkoutDetailTabbedContent.kt`: Forward `mapTopEndActions: @Composable (ColumnScope.() -> Unit)? = null` to `MapDetailLayout`.
* `TrackOnMapScreen.kt` & `RouteOnMapScreen.kt`: Pass Layers button via `topEndActions` without hardcoded `76.dp` offsets.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**: REQ-UI-491 status bar clearance preserved; 44dp touch target invariant; 100% unit test pass rate.
* **Risk Rating**: **LOW** (1 SP, localized Compose layout unification with zero DB, state machine, or lifecycle impact).
