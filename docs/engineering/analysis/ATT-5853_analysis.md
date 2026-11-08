# Stage 1 Analysis: ATT-5853 - Route Details Climb Badges, Climb Icon, Section Header & Locale Grade Formatting

**Ticket**: [ATT-5853](https://atrainingtracker.atlassian.net/browse/ATT-5853)  
**Sub-task**: `[Analysis]`  
**Parent Epic**: [ATT-66](https://atrainingtracker.atlassian.net/browse/ATT-66) (*Karten & Routen*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-45.12`  
**Branch**: `feature/ATT-5853`  
**Author**: AI Agent 1 (`subagent_pro`)  
**Date**: 2026-11-08  

---

## 1. Problem Statement & Motivation
During UI audit of the Route Details screen (`RouteOnMapScreen.kt`, `detail_05_route_detail.png`, `detail_06_route_climbs.png`), four visual and localization defects were identified:
1. **Summit Badge Clipping & Excessive Top Padding**: In `ElevationProfile.kt:475`, `topPadding = 44.dp` leaves an empty white gap above the profile. When scrolling lower content in `MapDetailLayout.kt`, summit category badges (`UC`, `Cat 4`) clip under the fixed zoom toolbar boundary.
2. **Duplicate Upward Arrow Icon**: `RouteSummaryHeader.kt:135,142` renders identical `R.drawable.ic_ascent` (`↑`) arrows for both elevation gain (`↑ 139 m`) and climb count (`↑ 2 Anstiege`).
3. **Redundant Section Header Below Filter Chips**: When both climbs and segments exist, `RouteOnMapScreen.kt:181-203` renders `[Anstiege (2)] [Segmente (2)]` chips, and the breakdown composables immediately render a duplicate `Anstiege (2)` / `Segmente (2)` heading directly beneath.
4. **Hardcoded English Decimal Point in Climb Title**: `ClimbDetector.kt:164` uses `Locale.US` for grade formatting (`0.7 km @ 3.7%`), conflicting with the localized German comma (`6,5%`) displayed in the subtitle row below.

---

## 2. Root Cause Analysis & Independent Verification
* **Root Causes**:
  * Legacy `topPadding = 44.dp` in `ElevationProfile.kt:475` was reserved for internal zoom buttons that were decoupled to `GlobalTelemetryZoomToolbar` in `ATT-2386`. `requiredClimbTopClearance` requires only 24 dp at 1.0x font scale; the extra 20 dp wastes vertical space and pushes the profile into scroll clipping.
  * `RouteSummaryHeader.kt:142` duplicated `R.drawable.ic_ascent` instead of using a distinct mountain terrain icon.
  * `RouteClimbsBreakdownSection.kt` and `RouteSegmentsBreakdownSection.kt` unconditionally render section titles without awareness of parent tab filter chips.
  * `ClimbDetector.kt:164` hardcodes `String.format(Locale.US, "%.1f%%", avgGrade)`.
* **Independent Peer Review & Verification**:
  * Code audit confirms `ElevationProfile.kt:475` hardcodes `if (showZoomControls) 44.dp else 16.dp`.
  * Visual audit of `detail_05_route_detail.png` confirms the large gap above the profile; `detail_06_route_climbs.png` confirms `UC` badge clipping.
  * Audit of `RouteSummaryHeader.kt:135,142` confirms duplicate `ic_ascent`.
  * Code audit of `RouteOnMapScreen.kt:484` and `RouteSegmentsBreakdownSection.kt:65` confirms unconditional `Text(...)` headings.

---

## 3. User Scope Grounding (ATT-1250) & Complexity Split Guardrail
* **In-Scope Goals**:
  1. Calibrate `ElevationProfile.kt` top padding to `24.dp` for `showZoomControls == true`, eliminating the empty white gap and preserving summit category badge top clearance without container clipping.
  2. Disambiguate climb count in `RouteSummaryHeader.kt:142` using `R.drawable.ic_altitude` (mountain silhouette) alongside ascent `ic_ascent`.
  3. Introduce `showHeader: Boolean = true` in `RouteClimbsBreakdownSection` and `RouteSegmentsBreakdownSection`, passing `showHeader = !(climbs.isNotEmpty() && matchedSegments.isNotEmpty())` in `RouteOnMapScreen.kt`.
  4. Localize climb title grade formatting in `ClimbDetector.kt:164` using `Locale.getDefault()` and format default generated climb titles dynamically in `RouteClimbsBreakdownSection` using `LocalMetricFormatter`.
* **Out-of-Scope Non-Goals**: Modifying climb detection algorithms (`REQ-MAP-027`), segment matching, map polyline styling, or database schemas.
* **Complexity Rating**: **2 SP (Standard)**. Target Release strictly `V5.0.0`.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit (REQ-PRO-022, Rule 68)
* **Historical Origin & Commit Trace**:
  * `ATT-2386` / `REQ-UI-225` (Sprint 2026-41.1): Decoupled zoom controls to `GlobalTelemetryZoomToolbar`; retained legacy `44.dp` top padding.
  * `ATT-3453` / `REQ-UI-274` (Sprint 2026-42.2): Introduced dynamic top clearance in `ElevationProfileZoomMath.kt` for compact views.
  * `ATT-3575` / `REQ-UI-387` (Sprint 2026-42.3): Added localized climb titles, but retained hardcoded `Locale.US` for grade format.
  * `ATT-2583` / `REQ-UI-302` & `ATT-2763` / `REQ-UI-308`: Introduced tabs for climbs and segments.
* **Rule 68 Side-Effect Audit**:
  * `ElevationProfileLayoutTest.kt` (L59, L65) and `ElevationProfileClimbBadgeClippingTest.kt` (L88, L222) enforce verbatim string assertions on `topPadding = 44.dp` and `totalCanvasHeight`. These contract tests must be synchronized with the amended specification.
  * `RouteSegmentsBreakdownContractTest.kt` and `RouteOverlayLayersContractTest.kt` remain valid with default parameter `showHeader: Boolean = true`.

---

## 5. Architectural Strategy & High-Level Solution
* **ElevationProfile Polish**: Set `topPadding = if (showZoomControls) 24.dp else 16.dp` and adjust `totalCanvasHeight` to `adaptiveHeight + 8.dp`.
* **Header Icon**: In `RouteSummaryHeader.kt:142`, set `iconRes = R.drawable.ic_altitude` for climb count.
* **Section Header Deduplication**: Add `showHeader: Boolean = true` to `RouteClimbsBreakdownSection` and `RouteSegmentsBreakdownSection`; wrap header `Text` in `if (showHeader)`.
* **Grade Localization**: In `ClimbDetector.kt:164`, use `Locale.getDefault()`. In `RouteClimbsBreakdownSection`, format default climb titles on-the-fly via `LocalMetricFormatter` and active locale.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**: Climb categorization thresholds (`CAT_4`..`HC`), route point coordinates, split-pane dragging, scrubbing overlay synchronization, 9-language localization parity across EN, DE, ES, FR, IT, JA, NL, PL, PT.
* **Risk Rating**: **LOW-MEDIUM** (Controlled presentation-tier and contract test synchronization).
