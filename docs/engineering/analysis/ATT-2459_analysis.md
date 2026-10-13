# Stage 1 Analysis: ATT-2459 - Remove tabs and Heimweg card from route selector in favor of clean proximity recency list

**Ticket**: [ATT-2459](https://atrainingtracker.atlassian.net/browse/ATT-2459)  
**Sub-task**: [ATT-2567](https://atrainingtracker.atlassian.net/browse/ATT-2567) (`[Analysis]`)  
**Parent Epic**: [ATT-2564](https://atrainingtracker.atlassian.net/browse/ATT-2564) (*Navigation: Turn-by-Turn Guidance & Cockpit Prompts*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `2026-41.1`  
**Branch**: `feature/ATT-2459`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-06  

---

## 1. Problem Statement & Motivation

During on-device review of the Route Selector bottom sheet ("Route wählen", `RouteSelectorSheet.kt`), the athlete experience was impaired by visual and semantic clutter:
1. **Redundant Sort/Filter Tab Bar**: A 3-tab bar (*In der Nähe*, *Zuletzt gefahren*, *Länge*) was rendered whenever the athlete had 5 or more saved routes (`REQ-MAP-024` Clause 3). When preparing to ride, athletes overwhelmingly want routes relevant to their current location, not manual switching between multiple tabs.
2. **Context-Mismatched "Heimweg" Card**: A prominent "Heimweg" (Take Me Home) card was pinned to the top of the route selector (`REQ-MAP-029` / ATT-1953). When an athlete is starting a workout from home or a designated starting location, offering return navigation is redundant and distracting. Heimweg is strictly a mid-ride return navigation action (handled separately in ATT-2462) and does not belong in the pre-ride route selection sheet.

The goal of ATT-2459 is to streamline `RouteSelectorSheet.kt` into a clean, focused, proximity-recency route list without filter tabs and without the pinned Heimweg card.

---

## 2. Root Cause Analysis (Forensic Investigation)

1. **Tab Bar Coupling**:
   - In `RouteSelectorSheet.kt` (lines 196–218), `TabRow` is conditionally rendered based on `uiState.showFilterTabs` (`allRoutes.size >= 5`).
   - In `RouteSelectorViewModel.kt` (lines 71–98), `_selectedTab` triggers one of three sorting branches: `RouteFilterTab.NEARBY`, `RECENT`, or `LENGTH`.
   - By eliminating `TabRow`, the selector consistently relies on the smart proximity ranking engine (`RouteProximityRanker`), which automatically prioritizes nearby routes departing in the direction of travel with recency tie-breaking.

2. **Heimweg Card Coupling**:
   - In `RouteSelectorSheet.kt` (lines 128–167), a primary-container `Card` with `R.drawable.ic_nav_home` is pinned above the route list.
   - `onTakeMeHome` callback is passed through `RouteSelectorModalBottomSheet` and `RouteSelectorContent` to invoke `returnNavRepo.startTakeMeHome()`.
   - Removing this card leaves the top of the sheet focused strictly on active route status and available routes to follow.

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  * Remove the pinned Heimweg (Take Me Home) card from `RouteSelectorSheet.kt`.
  * Remove the `TabRow` filter tabs (*In der Nähe / Zuletzt gefahren / Länge*) from `RouteSelectorSheet.kt`.
  * Ensure `RouteSelectorViewModel` defaults cleanly to proximity-recency ranking.
  * Clean up unused `onTakeMeHome` parameter from `RouteSelectorModalBottomSheet` call sites (`TrackingTabsScreen.kt`).
  * Verify clean empty state rendering when no saved routes are available.

* **Out-of-Scope Non-Goals (Scope Bounding)**:
  * Adding configurable proximity radius threshold in expert settings (explicitly covered in ATT-2460).
  * Aligning mid-ride Heimweg navigation HUD look and feel (explicitly covered in ATT-2462).
  * Changing `RoutesRepository` database queries or schema.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit (REQ-PRO-022)

* **Original Requirement ID & Target**: Amends `REQ-MAP-024` Clause 3 (*Adaptive Route Selector Sheet UI*) and `REQ-MAP-029` (*"Take Me Home" Return Navigation HUD*).
* **Historical Origin & Commit Trace**: Tickets `ATT-1835` (Sprint `2026-40.14`) and `ATT-1953` (Sprint `2026-40.15`).
* **Root Reason for Existing Formulation**:
  - `ATT-1835` introduced filter tabs because early user testing had large flat route lists without proximity filtering.
  - `ATT-1953` placed the Heimweg card at the top of the route selector as an easily accessible launch pad before dedicated return navigation triggers existed.
* **Preservation of Core Invariants**:
  - In-ride automated route detection (`RouteAutoDetector`, `AutoDetectedRouteBanner`) remains fully active.
  - Active route cancellation (`ActiveRouteBanner`, `onStopRoute`) remains fully functional.
  - `RouteProximityRanker` continues to evaluate proximity, heading, sport matching, and recency.
  - 100% full-suite test pass rate is strictly preserved.

---

## 5. Architectural Strategy & High-Level Solution

1. **`RouteSelectorSheet.kt`**:
   - Remove the `Card(onClick = onTakeMeHome, ...)` composable block.
   - Remove the `if (uiState.showFilterTabs) { TabRow(...) }` composable block.
   - Simplify parameters of `RouteSelectorModalBottomSheet` and `RouteSelectorContent` (remove `onTakeMeHome`).
2. **`RouteSelectorViewModel.kt`**:
   - Set sorting to default to `RouteProximityRanker.rankRoutes(...)` using current location, bearing, and recency.
   - Deprecate or remove `RouteFilterTab` enum and `showFilterTabs` flag from `RouteSelectorUiState`.
3. **`TrackingTabsScreen.kt`**:
   - Remove `onTakeMeHome` parameter from `RouteSelectorModalBottomSheet(...)` invocation.
4. **Unit & Contract Testing**:
   - Update `RouteSelectorViewModelTest.kt` to reflect single proximity-recency pipeline.
   - Add contract test asserting absence of Heimweg card and absence of `TabRow` in `RouteSelectorSheet.kt`.

---

## 6. System Invariants & Risk Assessment

* **Core Invariants**:
  1. Zero regressions across existing unit and integration test suites.
  2. Route selection, activation, and cancellation remain 100% functional.
  3. Auto-detected candidate route banner (`AutoDetectedRouteBanner`) remains undisturbed.
  4. Parent ticket Human Decision Gate remains strictly enforced.
* **Risk Rating**: **LOW** (Clean UI simplification and dead-code removal without changing core data schemas).
