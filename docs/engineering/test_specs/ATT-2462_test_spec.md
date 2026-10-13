# Stage 2: Requirement & Test Specification - ATT-2462: Align Heimweg (Take Me Home) UI look and feel with the app design system

**Ticket**: [ATT-2462](https://atrainingtracker.atlassian.net/browse/ATT-2462)  
**Sub-task**: [ATT-2592](https://atrainingtracker.atlassian.net/browse/ATT-2592) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-2564](https://atrainingtracker.atlassian.net/browse/ATT-2564) (*Navigation: Turn-by-Turn Guidance & Cockpit Prompts*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `2026-41.1`  
**Requirement Mapping**: `REQ-UI-282` (Amends `REQ-MAP-029` Clause 5, interfaces with `REQ-UI-280`)  
**Test Spec ID**: `TST-UI-242`  
**Branch**: `feature/ATT-2462`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-06  

---

## 1. Requirement Specification (REQ-UI-282)

### 1.1 Problem Statement & Rationale
In ATT-1953, "Take Me Home" return navigation was introduced (`REQ-MAP-029`). In ATT-2459, the pinned Heimweg card was removed from the Route Selector bottom sheet because offering return navigation before starting a workout from a known location made no sense. However, when the Route Selector is opened **mid-ride** (during an active or paused tracking session), return navigation to home-base is a natural entry point. Furthermore, `ReturnNavigationHud` used raw emoji `🏠`, inconsistent card background and elevation, and did not adhere to `docs/design_guidelines.md` §5.

### 1.2 Functional & Architectural Requirements
1. **Mid-Ride Heimweg Entry Point in Route Selector (`RouteSelectorSheet.kt`)**:
   - `RouteSelectorModalBottomSheet` and `RouteSelectorContent` SHALL support conditional rendering of a dedicated "Take Me Home" entry card when opened mid-ride:
     - Parameter `isMidRide: Boolean = false` (default false).
     - Parameter `onTakeMeHome: (() -> Unit)? = null`.
   - When `isMidRide == true` and `onTakeMeHome != null`, `RouteSelectorContent` SHALL render `MidRideHeimwegCard` above the route candidate list:
     - Shape: `RoundedCornerShape(12.dp)` conforming to Rule 23 baseline.
     - Border: `BorderStroke(1.dp, TTColor.RouteSelected.copy(alpha = 0.35f))` providing semantic route green domain branding.
     - Background: `MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)` with dynamic Light, Dark, and AMOLED theme compliance.
     - Icon: 36.dp container with 8.dp radius (`TTColor.RouteSelected.copy(alpha = 0.12f)`), housing `Icons.Default.Home` tinted `TTColor.RouteSelected`.
     - Text: Title `@string/nav_take_me_home` (`MaterialTheme.typography.titleMedium`), subtitle `@string/nav_home_destination_desc` (`MaterialTheme.typography.bodySmall`).
     - Tapping the card invokes `onTakeMeHome()` and dismisses the bottom sheet.
   - When `isMidRide == false`, `MidRideHeimwegCard` SHALL NOT be rendered, preserving the clean pre-ride list contract of ATT-2459 (`REQ-UI-280`).
2. **Harmonized ReturnNavigationHud (`ReturnNavigationHud.kt`)**:
   - Raw emoji `🏠` SHALL be completely excised from the title text and anywhere in `ReturnNavigationHud.kt`.
   - Container shape SHALL be `RoundedCornerShape(12.dp)` with `BorderStroke(1.dp, TTColor.RouteSelected.copy(alpha = 0.35f))`.
   - Background SHALL use `MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)` (or theme container) without excessive drop shadows.
   - Destination icon SHALL use `Icons.Default.Home` (tinted `TTColor.RouteSelected` or `MaterialTheme.colorScheme.primary`) when `isHomeDestination == true`, and directional icon when reverse navigation is active.
   - Typography SHALL use standard `MaterialTheme.typography.titleSmall` / `bodyMedium` with `FontWeight.SemiBold`.
   - Dismiss button SHALL use `Icons.Default.Close` with localized description `@string/nav_stop_return`.
3. **Integration Site in Cockpit (`TrackingTabsScreen.kt`)**:
   - `TrackingTabsScreen.kt` SHALL evaluate mid-ride tracking state: `isMidRide = trackingMode == TrackingMode.TRACKING || trackingMode == TrackingMode.PAUSED`.
   - `TrackingTabsScreen.kt` SHALL pass `isMidRide = isMidRide` and `onTakeMeHome = { returnNavRepo.startTakeMeHome(); showRouteSelectorSheet = false }` to `RouteSelectorModalBottomSheet`.
4. **100% 9-Language Localization Parity**:
   - All strings (`nav_take_me_home`, `nav_home_destination_desc`, `nav_stop_return`) SHALL be defined and verified across all 9 supported application locales (EN, DE, ES, FR, IT, JA, NL, PL, PT).

### 1.3 Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Amends `REQ-MAP-029` Clause 5 (*"Take Me Home" Entry Points & Visual HUD Presentation*) and interfaces with `REQ-UI-280` (*Streamlined Route Selector*).
* **Historical Origin & Commit Trace**: Ticket `ATT-1953`, Sprint `2026-40.16` (commit `0994f794`), and Ticket `ATT-2459`, Sprint `2026-41.1` (commit `cf4aafd8`).
* **Root Reason for Existing Formulation**: ATT-1953 originally placed the Heimweg card unconditionally in the route selector. ATT-2459 removed it because it was confusing at workout start. The user decision in ATT-2462 refines this: Heimweg belongs in the Route Selector *only* mid-ride, where athletes naturally look for a way back.
* **Preservation of Core Invariants**:
  - Unconfigured workout start retains the clean proximity list without Heimweg card.
  - Active route banners, candidate auto-detection, and cancellation remain completely untouched.
  - 100% full-suite test pass rate is strictly maintained.

### 1.4 Acceptance Criteria (Given-When-Then)
* *Given* an athlete on `ControlTrackingScreen` before starting a workout (`TrackingMode.IDLE` or `READY`),
* *When* opening the Route Selector sheet,
* *Then* `MidRideHeimwegCard` SHALL NOT be visible.
* *Given* an athlete during an active or paused tracking session (`TrackingMode.TRACKING` or `PAUSED`),
* *When* opening the Route Selector sheet,
* *Then* `MidRideHeimwegCard` SHALL be displayed above the route list with a 12.dp shape, green border accent, and Home icon.
* *When* the athlete taps `MidRideHeimwegCard`,
* *Then* `onTakeMeHome` SHALL be invoked, starting return navigation, and the sheet SHALL dismiss.
* *Given* `ReturnNavigationHud`,
* *When* rendering return navigation metrics,
* *Then* no emoji `🏠` SHALL appear in the title, and the card SHALL render with a 12.dp rounded corner shape and subtle green border stroke.
* *Given* all 9 supported locales,
* *When* translation parity test runs,
* *Then* zero missing entries and zero format specifier mismatches SHALL occur.

---

## 2. Test Specification (TST-UI-242)

### Test Case 1: RouteSelectorSheet Mid-Ride Conditional Contract (`TST-UI-242.1`)
* **Scope**: Structural & Composable Contract Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/routes/RouteSelectorMidRideContractTest.kt`
* **Actions**:
  1. Verify `RouteSelectorSheet.kt` exposes parameters `isMidRide: Boolean` and `onTakeMeHome: (() -> Unit)?`.
  2. Verify `RouteSelectorContent` contains `MidRideHeimwegCard` conditional check on `isMidRide && onTakeMeHome != null`.
  3. Verify `MidRideHeimwegCard` uses `RoundedCornerShape(12.dp)`, `TTColor.RouteSelected`, and `Icons.Default.Home`.
  4. Verify `TrackingTabsScreen.kt` passes `isMidRide` and `onTakeMeHome` to `RouteSelectorModalBottomSheet`.
* **Expected Result**: All contract assertions pass.

### Test Case 2: ReturnNavigationHud Design System Contract (`TST-UI-242.2`)
* **Scope**: Composable Contract Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/routes/ReturnNavigationHudContractTest.kt`
* **Actions**:
  1. Verify `ReturnNavigationHud.kt` does NOT contain emoji `🏠`.
  2. Verify `ReturnNavigationHud.kt` uses `RoundedCornerShape(12.dp)`.
  3. Verify `ReturnNavigationHud.kt` incorporates `TTColor.RouteSelected` or green domain accent.
  4. Verify `ReturnNavigationHud.kt` uses `Icons.Default.Home`.
* **Expected Result**: All assertions pass.

### Test Case 3: 9-Language Localization Parity Audit (`TST-UI-242.3`)
* **Scope**: Localization Parity Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/localization/TranslationParityTest.kt`
* **Goal**: Verify string presence and matching placeholders across all 9 locales:
  - EN, DE, ES, FR, IT, JA, NL, PL, PT
  - Keys: `nav_take_me_home`, `nav_home_destination_desc`, `nav_stop_return`.
* **Expected Result**: 100% parity, zero missing entries.

### Test Case 4: Full Clean-Room Regression Suite (`TST-UI-242.4`)
* **Command**: `./gradlew testDebugUnitTest`
* **Goal**: Verify 100% pass rate across the full test suite.

---

## 3. Traceability Matrix

| Test Case | Scope | Component Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-UI-242.1` | UI Contract | `RouteSelectorSheet.kt`, `TrackingTabsScreen.kt` | `REQ-UI-282.1`, `REQ-UI-282.3` | Specified |
| `TST-UI-242.2` | UI Contract | `ReturnNavigationHud.kt` | `REQ-UI-282.2` | Specified |
| `TST-UI-242.3` | Localization | `strings.xml` (all 9 locales) | `REQ-UI-282.4`, `REQ-UI-106` | Specified |
| `TST-UI-242.4` | Regression | Full Test Suite | `REQ-PRO-001` | Specified |
