# Stage 1 Analysis: ATT-2462 - Align Heimweg (Take Me Home) UI look and feel with the app design system

**Ticket**: [ATT-2462](https://atrainingtracker.atlassian.net/browse/ATT-2462)  
**Sub-task**: [ATT-2591](https://atrainingtracker.atlassian.net/browse/ATT-2591) (`[Analysis]`)  
**Parent Epic**: [ATT-2564](https://atrainingtracker.atlassian.net/browse/ATT-2564) (*Navigation: Turn-by-Turn Guidance & Cockpit Prompts*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `2026-41.1`  
**Branch**: `feature/ATT-2462`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-06  

---

## 1. Problem Statement & Motivation

During Sprint 2026-40.16 Joint Review, the athlete and developer observed that the visual presentation of "Take Me Home" (Heimweg) return navigation (`ReturnNavigationHud`, introduced in ATT-1953) did not visually harmonize with the rest of the application design system:
1. `ReturnNavigationHud` used raw emoji `🏠` in the text title string, inconsistent container coloring (`secondaryContainer` with elevated card shadow), and `painterResource(id = R.drawable.ic_nav_home)` rather than the unified Material theme tokens and `Icons.Default.Home` vector icon.
2. In ATT-2459, the pinned Heimweg card was removed from the Route Selector bottom sheet because offering return navigation before starting a workout from a known location made no sense. However, when the Route Selector is opened **mid-ride** (during an active or paused tracking session), return navigation to home-base is the most natural, prominent route option the athlete seeks.
3. The visual presentation of Heimweg cards and banners lacks the semantic green touch (`TTColor.RouteSelected` / `TTColor.RouteActiveNavigation`), standard 12.dp corner radius, and token-based styling specified in `docs/design_guidelines.md` §5.

---

## 2. Root Cause Analysis (Forensic Investigation)

1. **Ad-hoc Implementation in ATT-1953**:
   - `ReturnNavigationHud.kt` was implemented rapidly as a functional prototype:
     - Embedded raw emoji: `"🏠 ${navigationState.destinationName}"`
     - Background: `MaterialTheme.colorScheme.secondaryContainer` without route-specific green semantic branding.
     - Icon: `R.drawable.ic_nav_home` with hardcoded dimensions.
2. **Missing Mid-Ride Context in Route Selector (ATT-2459 vs. ATT-2462)**:
   - In ATT-2459, `onTakeMeHome` and the static Heimweg card were stripped unconditionally from `RouteSelectorSheet.kt`.
   - The route selector sheet was not aware of tracking mode state (`isMidRide` / `trackingMode.isTrackingOrPaused`), preventing it from presenting Heimweg conditionally when the user actually needs it during an outdoor ride.
3. **Decoupling from Home Base Designation (ATT-2472)**:
   - ATT-2472 has successfully established explicit favorite location home designation (`isHome == true`) and `HomeLocationResolver`.
   - The UI presentation layer now needs to surface this designated home destination cleanly and consistently.

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  1. *Mid-Ride Route Selector Entry*: Conditionally present a prominent, app-consistent "Heimweg" (Take Me Home) card at the top of `RouteSelectorSheet` when opened during an active tracking session (`isMidRide == true`). Tapping activates `onTakeMeHome` via `ReturnNavigationRepository.startTakeMeHome()`.
  2. *ReturnNavigationHud Harmonization*: Redesign `ReturnNavigationHud.kt` to strictly obey `docs/design_guidelines.md` §5:
     - Remove raw emoji `🏠`.
     - Standardize shape to `RoundedCornerShape(12.dp)` with subtle green border accent (`TTColor.RouteSelected.copy(alpha = 0.35f)`).
     - Standardize background to `MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)` or theme container tokens ensuring full Light, Dark, and AMOLED compatibility.
     - Use `Icons.Default.Home` for home-base destination and `Icons.AutoMirrored.Filled.Navigation` (or standard arrow) for reverse route navigation.
     - Align typography with `MaterialTheme.typography.titleMedium` / `bodyMedium`.
  3. *9-Language Localization Parity*: Ensure any new or adjusted labels (e.g. `nav_take_me_home`, `nav_return_to_home`) maintain 100% parity across all 9 locales.
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  * Modifying SQLite schema or home resolution algorithm (completed and verified in ATT-2472).
  * Changing turn-by-turn turn prompt HUD or chimes (governed by ATT-1450).
  * Altering route filtering radius or recency ranking (completed and verified in ATT-2460).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit

* **Original Requirement ID & Target**: Amends `REQ-MAP-029` Clause 5 (*"Take Me Home" Entry Points & Visual HUD Presentation*) and interfaces with `REQ-UI-280` (*Streamlined Route Selector*).
* **Historical Origin & Commit Trace**: Ticket `ATT-1953`, Sprint `2026-40.16` (commit `0994f794`), and Ticket `ATT-2459`, Sprint `2026-41.1` (commit `cf4aafd8`).
* **Root Reason for Existing Formulation**: ATT-1953 originally placed the Heimweg card unconditionally in the route selector. ATT-2459 removed it because it was confusing at workout start. The user decision in ATT-2462 refines this: Heimweg belongs in the Route Selector *only* mid-ride, where athletes naturally look for a way back.
* **Preservation of Core Invariants**:
  - Unconfigured workout start retains the clean proximity list without Heimweg card.
  - Active route banners, candidate auto-detection, and cancellation remain completely untouched.
  - 100% full-suite test pass rate is strictly maintained.

---

## 5. Architectural Strategy & High-Level Solution

1. **Presentation Layer: `ReturnNavigationHud.kt`**:
   - Refactor visual tree:
     - Container: `Surface` or `Card` with `shape = RoundedCornerShape(12.dp)`, `border = BorderStroke(1.dp, TTColor.RouteSelected.copy(alpha = 0.35f))`, `color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)`.
     - Icon box: 36.dp box with `RoundedCornerShape(8.dp)`, `background = TTColor.RouteSelected.copy(alpha = 0.12f)`, containing `Icons.Default.Home` (tinted `TTColor.RouteSelected`).
     - Text: Destination title clean (no emoji), remaining distance and ETA formatted crisply with `MaterialTheme.colorScheme.onSurface`.
     - Dismiss button: Standard `IconButton` with `Icons.Default.Close`.
2. **Mid-Ride Heimweg Card in `RouteSelectorSheet.kt`**:
   - Add parameter `isMidRide: Boolean = false` and `onTakeMeHome: (() -> Unit)? = null` to `RouteSelectorModalBottomSheet` and `RouteSelectorContent`.
   - When `isMidRide == true` and `onTakeMeHome != null`, render a dedicated `MidRideHeimwegCard` above the route list:
     - Styled identically to `RouteSelectionButton` (`shape = RoundedCornerShape(12.dp)`, `TTColor.RouteSelected` accent).
     - Title: `stringResource(R.string.nav_take_me_home)`.
     - Subtitle: destination name from `returnNavigationState.destinationName` (or `@string/nav_home_destination_desc`).
3. **Integration Site in `TrackingTabsScreen.kt`**:
   - Check `val isMidRide = trackingMode == TrackingMode.TRACKING || trackingMode == TrackingMode.PAUSED`.
   - Pass `isMidRide = isMidRide` and `onTakeMeHome = { returnNavRepo.startTakeMeHome(); showRouteSelectorSheet = false }` to `RouteSelectorModalBottomSheet`.

---

## 6. System Invariants & Risk Assessment

* **Core Invariants**:
  1. Zero regression in existing features, route selection, and unit tests.
  2. UI strictly conforms to `docs/design_guidelines.md` §5 (12.dp / 8.dp shapes, standard spacing, `TTColor` route tints, dynamic Light/Dark/AMOLED theme parity).
  3. Parent ticket Human Decision Gate remains strictly enforced.
* **Risk Rating**: **LOW** (Presentation-layer refinements with clear scope boundaries and no database migrations).
