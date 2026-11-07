# Stage 1: Problem Domain & Root Cause Analysis - ATT-5826

**Ticket**: [ATT-5826](https://rainerblind.atlassian.net/browse/ATT-5826)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355): Good and consistent UI | **Story Points**: 2 SP  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.10` | **Branch**: `feature/ATT-5826`  
**Requirement Mapping**: `REQ-UI-464` (refining `REQ-UI-408.4`)  
**Test Mapping**: `TST-UI-464` (verifying `TST-UI-371.6`)  
**Author**: AI Agent 1 (`subagent_pro`) | **Date**: 2026-11-07  

---

## 1. Problem Domain & Background
In `WorkoutDetailTabbedContent.kt`, athletes cannot horizontally swipe away from the **"Map & Graphs"** tab (`tab_map_graphs`), trapping them unless they tap the top tab strip. Swipes also fail from map/chart areas on other tabs.

### Independent Peer Review & Hypothesis Verification (Rules 69 & 70, ATT-3534)
Forensic audit of `WorkoutDetailTabbedContent.kt`, `MapDetailLayout.kt`, and `WorkoutDetailTabConfig.kt` confirms all 3 root causes:
1. **Blanket Isolation across `MapDetailLayout` (`WorkoutDetailTabbedContent.kt:288-298, 380-390`)**:
   `MapDetailLayout` is enclosed in an outer `Box` that sets `isTouchingInteractiveCanvas = true` on any touch. On `tab_map_graphs`, `nonMapNonGraphSections` is empty (`analyticsContent == null`), so `isTouchingInteractiveCanvas` stays `true` over 100% of the surface (including `SplitPaneDivider`, `GlobalTelemetryZoomToolbar`, headings, spacers, Y-axis, and histogram margins).
2. **`lastTouchXDp` Overwritten on `MOVE` Events (`WorkoutDetailTabbedContent.kt:208-218`)**:
   `lastTouchXDp` is updated inside `while (true) { awaitPointerEvent(...) }` on every `MOVE` event. An edge swipe starting at `x = 10.dp` crosses the `24.dp` gutter boundary mid-motion (e.g. `x = 25.dp`), instantly setting `userScrollEnabled = false` and aborting the swipe.
3. **1-Frame Recomposition Lag in `HorizontalPager(userScrollEnabled = ...)`**:
   At rest, `isTouchingInteractiveCanvas = true` and `lastTouchXDp = null`, evaluating `userScrollEnabled = false`. When the finger touches down at the edge, `HorizontalPager` evaluates `userScrollEnabled = false` during the initial `ACTION_DOWN` pass and drops the event before state recomposition takes effect.

---

## 2. Chesterton's Fence & Invariant Archaeology
* **Original Requirement ID & Target**: `REQ-UI-408.4` (Gesture Isolation in `WorkoutDetailTabConfig.kt` and `WorkoutDetailTabbedContent.kt`).
* **Historical Origin & Commit Trace**: `ATT-3920` (commit `ea5fa705`).
* **Root Reason for Existing Formulation**: `requiresHorizontalGestureIsolation` was introduced to prevent 2D map panning and horizontal chart scrubbing from accidentally triggering `HorizontalPager` page switches.
* **Preservation of Core Invariants**:
  1. *Plot Area Scrubbing Isolation*: Horizontal scrubbing inside active chart plot areas (`50.dp .. width - 52.dp`) and map bounds remains 100% isolated from pager swipe.
  2. *Layout Modes & Config Wire Parity*: Mode A/B/C layout resolution and `v2|` serialization remain unchanged.
  3. *Vertical Scroll Fluidity*: Vertical viewport scrolling via `ChartGestureDisambiguator` and `SplitPaneDivider` vertical dragging remain unimpeded.

---

## 3. User Scope Grounding (ATT-1250) & Complexity Split Guardrail
* **In-Scope**:
  1. **Edge Gutter Lock (`32.dp`)**: Evaluate X-coordinate strictly at initial `ACTION_DOWN` (`awaitFirstDown`); lock edge-swipe state across `MOVE` until release.
  2. **Zero 1-Frame Lag**: Synchronize pager availability at `ACTION_DOWN` so edge and neutral swipes are never dropped.
  3. **Granular Interactive Canvas Bounding**: Restrict `isTouchingInteractiveCanvas = true` strictly to the inner `mapBox` and chart plot area (`50.dp .. width - (if (isLandscape) 0.dp else 52.dp)`).
  4. **Non-Interactive Strip Pass-Through**: Ensure `SplitPaneDivider`, `GlobalTelemetryZoomToolbar`, chart headings, spacers, Y-axis (`0..50.dp`), and histogram margins (`52.dp`) set `isTouchingInteractiveCanvas = false`.
* **Out-of-Scope**: Altering map rendering, zoom math, chart binning, or tab persistence schemas.
* **Complexity Rating**: **2 SP (Standard)**. Refined touch disambiguation across 3 targeted UI composables.

---

## 4. Blast Radius & Side-Effect Audit (Rule 68)
| Affected Component | Impact / Verification |
| :--- | :--- |
| `WorkoutDetailTabbedContent.kt` | Locks initial `DOWN` touch position; eliminates 1-frame lag on `HorizontalPager`. |
| `MapDetailLayout.kt` | Restricts interactive canvas flag to map/plot surfaces; marks divider, toolbar, headers as neutral. |
| `WorkoutDetailTabConfig.kt` | Updates default `edgeSwipeGutterDp` to `32f` and refines plot-area isolation contract. |
| `WorkoutDetailTabsTest.kt` | Asserts edge-gutter lock, neutral strip pass-through, and `TST-UI-371.6` parity. |

---

## 5. System Invariants & Risk Assessment
* **Core Invariants**: Chart scrubbing and 2D map dragging preserved in active areas; vertical scrolling intact; zero regression on card tabs.
* **Risk Rating**: **LOW** (Localized touch event filtering and boundary checking).
