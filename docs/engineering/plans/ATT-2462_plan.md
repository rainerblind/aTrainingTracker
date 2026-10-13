# Stage 3: Implementation Plan - ATT-2462: Align Heimweg (Take Me Home) UI look and feel with the app design system

**Ticket**: [ATT-2462](https://rainerblind.atlassian.net/browse/ATT-2462)  
**Sub-task**: [ATT-2593](https://rainerblind.atlassian.net/browse/ATT-2593) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-2564](https://rainerblind.atlassian.net/browse/ATT-2564) (*Navigation: Turn-by-Turn Guidance & Cockpit Prompts*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-41.1`  
**Requirement Mapping**: `REQ-UI-282` (amends `REQ-MAP-029` Clause 5, interfaces with `REQ-UI-280`)  
**Test Mapping**: `TST-UI-242`  
**Branch**: `feature/ATT-2462`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-06  

---

## 1. Problem Description & Background

In ATT-1953, "Take Me Home" (Heimweg) return navigation was introduced (`REQ-MAP-029`). In ATT-2459, the pinned Heimweg card was removed from the Route Selector bottom sheet because offering return navigation before starting a workout from a known base was confusing and counter-intuitive. However, when the Route Selector is opened **mid-ride** (during an active or paused tracking session), return navigation to the designated home-base is a natural and highly desired entry point.

Additionally, the glanceable `ReturnNavigationHud` in the cockpit currently uses a raw Unicode emoji (`🏠`) in the title text, lacks the semantic subtle route green domain styling established in `docs/design_guidelines.md` §5.4, and exhibits visual discrepancies across themes.

This plan details the architectural implementation to align Heimweg UI with the app design system and cleanly expose the mid-ride Route Selector entry point without regressing pre-ride behavior.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-UI-282` (*Take Me Home Design System Alignment & Mid-Ride Entry Point*)
  * Amends `REQ-MAP-029` Clause 5 (*"Take Me Home" Entry Points & Visual HUD Presentation*).
  * Preserves `REQ-UI-280` (*Streamlined Route Selector* - pre-ride clean list without pinned Heimweg card).
* **Test Mapping**: `TST-UI-242` (*Take Me Home Visual System and Mid-Ride Selection Parity*)
  * `TST-UI-242.1`: `RouteSelectorSheet` mid-ride conditional contract.
  * `TST-UI-242.2`: `ReturnNavigationHud` design system and emoji-free typography contract.
  * `TST-UI-242.3`: 100% 9-language localization parity audit.

---

## 3. System Invariants & Preserved Behavior

1. **Zero Unintended Regressions**: Existing route selection, proximity ordering, auto-detection prompts, and clean-room unit tests continue to pass with 0 failures.
2. **Pre-Ride Clean Slate**: When opening the Route Selector before a workout is started (`TrackingMode.IDLE` or `READY`), the list remains clean without the Heimweg card, preserving ATT-2459 behavior.
3. **Thread Safety & Dispatcher Affinity**: Route selection, return navigation initialization, and location resolution continue to run asynchronously via coroutines on appropriate dispatchers.
4. **Subtask Self-Sufficiency**: Subtasks transition directly to `Erledigt` upon passing Gate audit via `freigabe`.
5. **Parent Human Gate Invariance**: Terminal completion of parent tickets remains reserved for the human user in `Final Review (Human)`.

---

## 4. Proposed Architectural Changes

### Component 1: Mid-Ride Entry Point in `RouteSelectorSheet.kt`
* Extend `RouteSelectorModalBottomSheet` and `RouteSelectorContent` with:
  * `isMidRide: Boolean = false`
  * `onTakeMeHome: (() -> Unit)? = null`
* Implement composable `MidRideHeimwegCard`:
  * Renders conditionally above candidate list when `isMidRide && onTakeMeHome != null`.
  * Shape: `RoundedCornerShape(12.dp)` conforming to Rule 23 baseline.
  * Border: `BorderStroke(1.dp, TTColor.RouteSelected.copy(alpha = 0.35f))` providing semantic route green domain branding.
  * Background: `MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)` with dynamic Light, Dark, and AMOLED theme compliance.
  * Icon badge: 36.dp container with 8.dp radius (`TTColor.RouteSelected.copy(alpha = 0.12f)`), housing `Icons.Default.Home` tinted `TTColor.RouteSelected`.
  * Title: `@string/take_me_home_title` (`MaterialTheme.typography.titleMedium`, `FontWeight.SemiBold`).
  * Subtitle: `@string/take_me_home_desc` (`MaterialTheme.typography.bodySmall`, `MaterialTheme.colorScheme.onSurfaceVariant`).
  * Click action: Triggers `onTakeMeHome()`.

### Component 2: Harmonized `ReturnNavigationHud.kt`
* Eliminate raw Unicode emoji `🏠` from the title string formatting.
* Retain clear visual distinction via destination icon: `painterResource(id = R.drawable.ic_nav_home)` tinted `TTColor.RouteSelected` when `isHomeDestination == true`.
* Container card: `RoundedCornerShape(12.dp)` with `BorderStroke(1.dp, TTColor.RouteSelected.copy(alpha = 0.35f))`.
* Background container: `MaterialTheme.colorScheme.secondaryContainer` (or theme surfaceVariant) with crisp text contrast across Light, Dark, and AMOLED modes.
* Dismiss action button: Localized content description `@string/nav_stop_return`.

### Component 3: Cockpit Integration in `TrackingTabsScreen.kt`
* Evaluate `isMidRide` state: `val isMidRide = trackingMode == TrackingMode.TRACKING || trackingMode == TrackingMode.PAUSED`.
* Pass `isMidRide = isMidRide` and `onTakeMeHome = { returnNavRepo.startTakeMeHome(); showRouteSelectorSheet = false }` to `RouteSelectorModalBottomSheet`.

### Component 4: 9-Language Localization Audit
* Verify that strings `@string/take_me_home_title`, `@string/take_me_home_desc`, and `@string/nav_stop_return` exist and are consistent across all 9 supported locales: `values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`.

### UI Consistency (Rule 23 — mandatory if UI is added or changed)
* **Reference screen / component**: `RouteSelectorSheet.kt` (`RouteCard`, `ActiveRouteBanner`) and `SensorGridScreen.kt` HUD overlays.
* **Reused components**: `Card`, `Icon`, `Text`, `RoundedCornerShape(12.dp)` from `ui/theme/` and Material 3 design system.
* **Theme tokens**:
  * Shapes: `RoundedCornerShape(12.dp)` for cards/banners, `8.dp` for inner icon badges.
  * Spacing: `4.dp`, `8.dp`, `12.dp`, `16.dp` standard scale.
  * Colors: `MaterialTheme.colorScheme.surfaceVariant`, `MaterialTheme.colorScheme.secondaryContainer`, `MaterialTheme.colorScheme.onSurface`, `TTColor.RouteSelected` (`Color(0xFF228B22)` domain green navigation accent).
* **New one-off styles & justification**: None. Uses existing tokens and follows §5.4 route domain green accents.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Composable Refactoring in `RouteSelectorSheet.kt`
* Files: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/routes/RouteSelectorSheet.kt`
* Changes:
  1. Add `isMidRide: Boolean = false` and `onTakeMeHome: (() -> Unit)? = null` parameters to `RouteSelectorModalBottomSheet` and `RouteSelectorContent`.
  2. Implement `MidRideHeimwegCard` with 12.dp radius, green border accent, home icon badge, and localized strings.
  3. Conditionally render `MidRideHeimwegCard` when `isMidRide && onTakeMeHome != null`.

### Step 2: Styling and Typography Harmonization in `ReturnNavigationHud.kt`
* Files: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/routes/ReturnNavigationHud.kt`
* Changes:
  1. Remove raw emoji `"🏠 "` from `Text(text = ...)` title formatting.
  2. Apply `BorderStroke(1.dp, TTColor.RouteSelected.copy(alpha = 0.35f))` to the Card.
  3. Tint home icon with `TTColor.RouteSelected` when `navigationState.isHomeDestination == true`.

### Step 3: Wiring Mid-Ride State in `TrackingTabsScreen.kt`
* Files: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/trackingtabs/TrackingTabsScreen.kt`
* Changes:
  1. Pass `isMidRide` and `onTakeMeHome` closure to `RouteSelectorModalBottomSheet`.

### Step 4: Unit & Contract Tests Construction
* Files:
  * `app/src/test/java/com/atrainingtracker/trainingtracker/ui/routes/RouteSelectorMidRideContractTest.kt`
  * `app/src/test/java/com/atrainingtracker/trainingtracker/ui/routes/ReturnNavigationHudContractTest.kt`
* Changes:
  1. Create contract test verifying `isMidRide` parameter exposure and conditional card rendering logic.
  2. Update/expand `ReturnNavigationHudContractTest` verifying elimination of emoji and compliance with design tokens.
* Targeted Test Commands:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.routes.*" --tests "com.atrainingtracker.trainingtracker.localization.TranslationParityTest"
  ```

---

## 6. Verification & Rollback Plan

* **Verification**:
  - Run targeted unit tests for `ui.routes` and `TranslationParityTest`.
  - In Stage 5, execute full clean-room test suite `./gradlew testDebugUnitTest`.
  - Validate living documentation (`docs/requirements.md`, `docs/tests.md`).
* **Rollback**:
  - Branch isolation on `feature/ATT-2462` enables total revert without affecting `sprint/2026-41.1` or `develop`.
