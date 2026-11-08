# Stage 1 Analysis: ATT-5845 - ElevationProfile & RouteItem: Fix Y-axis label collision ('400' overlapping '383 m') and hide empty profile when route has no altitude data

**Ticket**: [ATT-5845](https://rainerblind.atlassian.net/browse/ATT-5845)  
**Sub-task**: [ATT-6054](https://rainerblind.atlassian.net/browse/ATT-6054) (`[Analysis]`)  
**Parent Epic**: [ATT-66](https://rainerblind.atlassian.net/browse/ATT-66) (*Karten & Routen (Maps & Routes)*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-45.12`  
**Branch**: `feature/ATT-5845`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-08  

---

## 1. Problem Statement & Motivation
During UI testing of the Routes screen (`screen_04_routen.png`), two defects were identified in `ElevationProfile`:
1. **Y-Axis Label Collision**: On route cards with compact elevation profiles (e.g. `Nur ein Test`), the intermediate Y-axis grid label `400` directly overlaps the bottom `minAltLabel` (`383 m`). Furthermore, intermediate labels are rendered with a fixed `-110f` X-offset, misaligning them with right-aligned `minAltLabel` and `maxAltLabel`.
2. **Empty Elevation Profile on Flat GPX Routes**: For routes without altitude data (e.g. `bike2work`, `↑ 0 m`), `calculateElevationBounds` applies a default 20m minimum span centered on 0, causing `RouteItem` to render a meaningless `-10 m` to `10 m` flat green bar.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
1. **Clearance & Alignment in `ElevationProfile.kt:799-810`**:
   - Intermediate grid labels are drawn at baseline `y + 10f`, but the vertical clearance guard checks `(height - y) > (textPaint.textSize * 1.2f)`. Omitting the `+ 10f` downward shift reduces actual baseline separation to `< 29 px` (less than `32 px` text height), causing `400` to collide with `383 m` at `y = height`.
   - Drawing at `-110f` creates an arbitrary hardcoded gap; `minAltLabel` and `maxAltLabel` are right-aligned to `-labelWidth - 10f`.
2. **Unconditional Profile in `RouteItem.kt:113`**:
   - `RouteItem` renders `ElevationProfile` unconditionally, even when all `pathPoints` have null/zero altitude and `summary.elevationGain == 0.0`.
* **Independent Peer Review & Verification**:
   - Inspected `screen_04_routen.png` via `read_file` (`Rule 71`): Confirmed `400` overlapping `383 m` and the synthetic `[-10 m, 10 m]` bar on `bike2work`.
   - Cross-ticket invariant audit (`ATT-5853`): Confirmed `topPadding` (`24.dp` / `16.dp`) and `totalCanvasHeight` (`+ 8.dp`) tested by `ElevationProfileLayoutTest` and `ElevationProfileClimbBadgeClippingTest` remain completely unaffected.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  - Calculate vertical clearance using actual label baseline (`y + 10f`), enforcing `>= textPaint.textSize * 1.35f` clearance against `height`, `maxAltLabel`, and adjacent ticks.
  - Right-align intermediate altitude labels to `-textPaint.measureText(altText) - 10f`.
  - Suppress `ElevationProfile` rendering in `RouteItem.kt` when `pathPoints` lacks non-zero altitude data and `elevationGain <= 0.0`.
* **Out-of-Scope Non-Goals**:
  - Modifying `calculateElevationBounds` fallback logic for full-screen route detail views (`MapDetailLayout`).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Net-new requirement `REQ-UI-493`, refining `REQ-UI-197` (*Elevation Profile Layout Architecture*) and `REQ-UI-225` (*Route Item Layout*).
* **Historical Origin & Commit Trace**: `ATT-508` (bounds calculation) and `ATT-2463` (`RouteItem` layout).
* **Root Reason for Existing Formulation**: The clearance guard was written before the `+ 10f` downward baseline adjustment was added; `RouteItem` assumed all imported routes contained elevation data.
* **Preservation of Core Invariants**: `ATT-5853` badge clearance invariants, chart zoom/pan math, and full-screen elevation detail inspection remain 100% preserved.

---

## 5. Architectural Strategy & High-Level Solution
* In `ElevationProfile.kt`:
  - Calculate `labelBaselineY = y + 10f`.
  - Guard: `(height - labelBaselineY) >= textPaint.textSize * 1.35f && (labelBaselineY - highlightPaint.textSize) >= textPaint.textSize * 1.35f && Math.abs(y - lastY) >= textPaint.textSize * 1.35f`.
  - Right-align: `val altWidth = textPaint.measureText(altText); canvas.nativeCanvas.drawText(altText, -altWidth - 10f, labelBaselineY, textPaint)`.
* In `RouteItem.kt`:
  - Guard `ElevationProfile` with `hasElevationData = pathPoints.any { it.altitude != null && !it.altitude.isNaN() && Math.abs(it.altitude) > 1e-3 } || summary.elevationGain > 0.0`.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**:
  1. `ATT-5853` top padding (`24.dp` / `16.dp`) and climb badge clearance tests pass 100%.
  2. Routes with authentic elevation continue to render elevation profiles without regression.
  3. No changes to public API of `ElevationProfile` or `RouteItem`.
* **Risk Rating**: **LOW** (Localized drawing math correction and conditional composable visibility).
