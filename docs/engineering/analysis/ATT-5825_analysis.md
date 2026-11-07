# Stage 1: Problem Domain & Root Cause Analysis - ATT-5825

**Ticket**: [ATT-5825](https://rainerblind.atlassian.net/browse/ATT-5825)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*) | **Story Points**: 1 SP  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.11` | **Branch**: `feature/ATT-5825`  
**Requirement Mapping**: `REQ-UI-465` (refining `REQ-UI-195`, `REQ-UI-217`, `REQ-UI-244`)  
**Test Mapping**: `TST-UI-465` (verifying `TST-UI-149.1`, `TST-UI-178.2`)  
**Author**: AI Agent 1 (`subagent_pro`) | **Date**: 2026-11-07  

---

## 1. Problem Domain & Background
Inspection of `KnownLocationsScreen.kt` (lines 463–635) and `docs/attachments/known_locations_list_screenshot.png` reveals three visual defects:
1. **48dp Touch Target Bloat in `FlowRow`**: Drill-down badges (*Starts* and *Strecken*) use Material 3 `Surface(onClick)`, which enforces `LocalMinimumInteractiveComponentSize = 48.dp`. Squeezed beside the map thumbnail, wrapped badges generate massive vertical whitespace in the card center.
2. **Misleading Altitude Presentation**: Elevation above sea level uses an ascent climbing arrow (`R.drawable.ic_ascent`, `↑`) in prominent primary blue (`colorScheme.primary`), dominating the card over primary metrics (*Starts* and *Strecken*).
3. **Square 100×100dp Map Aspect Ratio**: The large heart pin and compulsory Google watermark obscure half the thumbnail, while inflating vertical card height so only 3–4 items fit on screen.

### Independent Peer Review & Hypothesis Verification (Rules 69 & 70, ATT-3534)
* **Visual Audit**: Verified `known_locations_list_screenshot.png` via `read_file` (Rule 71); confirmed badge gap and icon clash.
* **Touch Target Solution**: Wrapping badge `Surface`s in `LocalMinimumInteractiveComponentSize provides Dp.Unspecified` eliminates the 48dp phantom gap without degrading tap targets.
* **Semantic Altitude**: Replacing `ic_ascent` with a mountain icon (`Icons.Default.Terrain` / `ic_altitude`) in `onSurfaceVariant` (with optional radius) restores proper visual hierarchy.
* **Compact Landscape Thumbnail**: `116 × 80 dp` expands horizontal map context and compacts card height by ~20dp.

---

## 2. Chesterton's Fence & Invariant Archaeology
* **Original Requirement ID & Target**: `REQ-UI-195` (Badges row), `REQ-UI-217` (Thumbnail preview), `REQ-UI-244` (100dp harmonization).
* **Historical Origin & Commit Trace**: `ATT-1643` (`REQ-UI-195`), `ATT-1813` (`REQ-UI-217`), `ATT-2051` (`REQ-UI-244`, commit `e29c614b`).
* **Root Reason for Existing Formulation**: `REQ-UI-244` harmonized 100dp with cluster cards; `REQ-UI-195` decoupled badges from altitude but overlooked Material 3's implicit 48dp interactive size.
* **Preservation of Core Invariants**:
  1. *Drill-Down & Edit Actions*: Single-tap edit, long-press context menu (`REQ-UI-061`), and starts/routes drill-downs remain intact.
  2. *Map Preview Engine*: Google Maps lite mode, dark theme styling, and `LocalInspectionMode` fallback remain intact.
  3. *Calibrated Zoom Math*: `KnownLocationZoomMath.calculateThumbnailZoom` invariant strictly preserved.

---

## 3. User Scope Grounding (ATT-1250) & Complexity Split Guardrail
* **In-Scope**:
  1. Suppress 48dp min touch target gap on badges via `LocalMinimumInteractiveComponentSize provides Dp.Unspecified`.
  2. Add subtle visual emphasis to *Starts* badge (`FontWeight.SemiBold`, secondaryContainer tint).
  3. Replace `ic_ascent` with `Icons.Default.Terrain` in `onSurfaceVariant` secondary styling.
  4. Change `KnownLocationThumbnailMap` dimensions from `100 × 100 dp` to `116 × 80 dp`.
  5. Update structural contract assertions in `KnownLocationCardLayoutTest.kt`.
* **Out-of-Scope**: Geofence tracking algorithms, database schemas, or route cluster card layouts.
* **Complexity Rating**: **1 SP (Trivial)**. Targeted UI polish within a single composable screen.

---

## 4. Blast Radius & Side-Effect Audit (Rule 68)
| Affected Component | Impact / Verification |
| :--- | :--- |
| `KnownLocationsScreen.kt` | UI polish in `KnownLocationCard` and `KnownLocationThumbnailMap`. |
| `KnownLocationCardLayoutTest.kt` | Contract tests updated to assert `116.dp` x `80.dp` and terrain icon. |

---

## 5. System Invariants & Risk Assessment
* **Core Invariants**: All test tags (`location_starts_badge_*`, `location_map_preview_*`) and click handlers preserved.
* **Risk Rating**: **LOW** (Presentation-only composable polish; zero database or state changes).
