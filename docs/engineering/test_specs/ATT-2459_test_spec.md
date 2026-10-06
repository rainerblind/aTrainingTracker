# Stage 2: Requirement & Test Specification - ATT-2459: Remove tabs and Heimweg card from route selector in favor of clean proximity recency list

**Ticket**: [ATT-2459](https://atrainingtracker.atlassian.net/browse/ATT-2459)  
**Sub-task**: [ATT-2568](https://atrainingtracker.atlassian.net/browse/ATT-2568) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-2564](https://atrainingtracker.atlassian.net/browse/ATT-2564) (*Navigation: Turn-by-Turn Guidance & Cockpit Prompts*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `2026-41.1`  
**Requirement Mapping**: `REQ-UI-280` (*Streamlined Route Selector: Removal of Sort Tabs and Pinned Heimweg Card in Favor of Proximity-Recency Ranking*)  
**Test Spec ID**: `TST-UI-240`  
**Branch**: `feature/ATT-2459`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-06  

---

## 1. Requirement Specification (REQ-UI-280)

### 1.1 Problem Statement & Rationale
During on-device review of the Route Selector bottom sheet ("Route wählen", `RouteSelectorSheet.kt`), the athlete experience was impaired by redundant controls and context-mismatched actions:
1. **Redundant Sort/Filter Tab Bar**: A 3-tab bar (*In der Nähe*, *Zuletzt gefahren*, *Länge*) was rendered whenever the athlete had 5 or more saved routes (`REQ-MAP-024` Clause 3). When preparing to ride, athletes overwhelmingly want routes relevant to their current location, not manual switching between multiple tabs.
2. **Context-Mismatched "Heimweg" Card**: A prominent "Heimweg" (Take Me Home) card was pinned to the top of the route selector (`REQ-MAP-029` / ATT-1953). When an athlete is starting a workout from home or a designated starting location, offering return navigation is redundant and distracting. Heimweg is strictly a mid-ride return navigation action (handled separately in ATT-2462) and does not belong in the pre-ride route selection sheet.

The system SHALL streamline `RouteSelectorSheet.kt` and `RouteSelectorViewModel.kt` into a focused, proximity-recency route list without filter tabs and without the pinned Heimweg card.

### 1.2 Functional & Architectural Requirements
1. *Removal of Heimweg Card (`RouteSelectorSheet.kt`)*:
   • The system SHALL remove the pinned "Heimweg" / "Take Me Home" card (`Card(onClick = onTakeMeHome, ...)`) from `RouteSelectorSheet.kt`.
   • The `onTakeMeHome` parameter SHALL be removed from `RouteSelectorModalBottomSheet` and `RouteSelectorContent`.
   • In `TrackingTabsScreen.kt`, the `RouteSelectorModalBottomSheet` call site SHALL no longer supply `onTakeMeHome`.
2. *Removal of Sort/Filter Tab Bar (`RouteSelectorSheet.kt` & `RouteSelectorViewModel.kt`)*:
   • The system SHALL remove the `TabRow` filter tabs (*In der Nähe / Zuletzt gefahren / Länge*) and the conditional `uiState.showFilterTabs` guard from `RouteSelectorSheet.kt`.
   • In `RouteSelectorViewModel.kt`, route ranking SHALL unconditionally default to `RouteProximityRanker.rankRoutes(...)` using the athlete's current location, bearing, and recency tie-breaking.
   • The `RouteFilterTab` enum, `selectedTab`, and `showFilterTabs` properties SHALL be eliminated from `RouteSelectorUiState` and `RouteSelectorViewModel`.
3. *Preservation of In-Ride Detection & Active Route Controls*:
   • `ActiveRouteBanner` (allowing 1-tap route cancellation via `onStopRoute`) SHALL remain prominently rendered when a route is currently active.
   • `AutoDetectedRouteBanner` (allowing activation or dismissal of dynamically detected route candidates) SHALL remain fully functional within `RouteSelectorSheet.kt`.
4. *Clean Empty State*:
   • When no saved routes are available or no routes exist, the sheet SHALL render a centered empty state placeholder informing the athlete (`@string/route_empty_title` and `@string/route_empty_desc`).
5. *100% 9-Language Localization Parity*:
   • All remaining string resources in the sheet SHALL maintain 100% parity across all 9 supported application locales (EN, DE, ES, FR, IT, JA, NL, PL, PT).

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (No Heimweg Card in Route Selector)**:
  * *Given* an athlete opening the Route Selector bottom sheet from `ControlTrackingScreen`,
  * *When* the sheet appears,
  * *Then* the "Heimweg" / "Take Me Home" card SHALL NOT be present anywhere in the sheet.
* **Criterion 2 (No Sort Tabs & Unconditional Proximity Ranking)**:
  * *Given* an athlete with 10 saved routes opening the Route Selector,
  * *When* viewing the route list,
  * *Then* no `TabRow` or sort tabs (*In der Nähe / Zuletzt gefahren / Länge*) SHALL be displayed, and routes SHALL be ranked directly by the 5-tier proximity-recency engine (`RouteProximityRanker`).
* **Criterion 3 (Active Route Cancellation Intact)**:
  * *Given* an active navigated route,
  * *When* opening `RouteSelectorSheet`,
  * *Then* `ActiveRouteBanner` SHALL be displayed at the top with route name, metrics, and a "Clear / Abwählen" action that resets active navigation upon tap.
* **Criterion 4 (Auto-Detection Intact)**:
  * *Given* an auto-detected candidate route evaluated by `RouteAutoDetector`,
  * *When* opening `RouteSelectorSheet`,
  * *Then* `AutoDetectedRouteBanner` SHALL display the candidate prompt with activate and dismiss actions.
* **Criterion 5 (Clean Empty State)**:
  * *Given* a user with zero saved routes,
  * *When* opening `RouteSelectorSheet`,
  * *Then* the empty state title and description SHALL render centered in the sheet without layout overflows.

### 1.4 System Invariants
* **Invariant 1**: In-ride auto-detection engine (`RouteAutoDetector`) and live tracking banner (`AutoDetectedRouteBanner`) in `SensorGridScreen` MUST NOT be altered or regressed.
* **Invariant 2**: Route activation (`RoutesRepository.setActiveNavigatedRoute`) and cancellation contracts MUST remain strictly functional.
* **Invariant 3**: Pure geodesic distance and bearing calculations in `RouteProximityRanker` MUST NOT be compromised.
* **Invariant 4**: Full clean-room test suite (`./gradlew testDebugUnitTest`) MUST maintain a 100% pass rate.

### Requirement Archaeology & Chesterton's Fence Audit
* `Original Requirement ID & Target`: Amends `REQ-MAP-024` Clause 3 (*Adaptive Route Selector Sheet UI*) and `REQ-MAP-029` Clause 5 (*"Take Me Home" Entry Points*).
* `Historical Origin & Commit Trace`: Tickets `ATT-1835` (Sprint `2026-40.14`) and `ATT-1953` (Sprint `2026-40.15`).
* `Root Reason for Existing Formulation`: ATT-1835 introduced filter tabs because initial testing featured flat route lists without proximity filtering; ATT-1953 placed the Heimweg card at the top of the route selector as an easily accessible launch pad before dedicated return navigation triggers existed.
* `Preservation of Core Invariants`: In-ride automated route detection (`RouteAutoDetector`, `AutoDetectedRouteBanner`) remains fully active; active route cancellation (`ActiveRouteBanner`, `onStopRoute`) remains fully functional; `RouteProximityRanker` continues to evaluate proximity, heading, sport matching, and recency; 100% full-suite test pass rate is strictly preserved.

---

## 2. Test Specification (TST-UI-240)

### Test Case 1: Route Selector Contract & Structural Decoupling (`TST-UI-240.1`)
* **Scope**: Contract / Static Analysis Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/routes/RouteSelectorSheetTest.kt`
* **Preconditions**: Project source tree loaded.
* **Action**: Inspect `RouteSelectorSheet.kt` and `TrackingTabsScreen.kt`.
* **Expected Result**:
  1. `RouteSelectorSheet.kt` does NOT reference `TabRow` or `showFilterTabs`.
  2. `RouteSelectorSheet.kt` does NOT contain `onTakeMeHome` or `ic_nav_home` or `take_me_home_title`.
  3. `RouteSelectorModalBottomSheet` does NOT accept `onTakeMeHome`.
  4. `RouteSelectorSheet.kt` preserves public composables: `RouteSelectorModalBottomSheet`, `RouteSelectorContent`, `ActiveRouteBanner`, `AutoDetectedRouteBanner`, and `RouteCard`.

### Test Case 2: RouteSelectorViewModel Proximity-Recency Pipeline (`TST-UI-240.2`)
* **Scope**: ViewModel StateFlow Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/routes/RouteSelectorViewModelTest.kt`
* **Preconditions**: Mock `RoutesRepository` with sample routes.
* **Action**:
  1. Verify initial state defaults to `RouteProximityRanker` ranking.
  2. Update location via `viewModel.onLocationChanged(...)` and verify proximity re-ranking.
  3. Verify route selection, stop/clear route, candidate activation, and candidate dismissal.
  4. Verify absence of filter tabs state.
* **Expected Result**: State updates deterministically; routes rank by proximity and recency.

### Test Case 3: 9-Language Localization & Specifier Audit (`TST-UI-240.3`)
* **Scope**: Localization Parity Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/localization/TranslationParityTest.kt`
* **Goal**: Verify string presence and matching `%s`/`%d` tokens across all 9 locales:
  * EN, DE, ES, FR, IT, JA, NL, PL, PT
* **Expected Result**: 100% parity, zero missing entries, zero format specifier mismatches.

### Test Case 4: Clean-Room Regression Suite (`TST-UI-240.4`)
* **Command**: `./gradlew testDebugUnitTest`
* **Goal**: Verify 100% pass rate across the full test suite.
* **Expected Result**: BUILD SUCCESSFUL, 0 failed tests.

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-UI-240.1` | Contract | `RouteSelectorSheetTest` | `REQ-UI-280` Clauses 1, 2, 3 | Specified |
| `TST-UI-240.2` | Unit / ViewModel | `RouteSelectorViewModelTest` | `REQ-UI-280` Clause 2 | Specified |
| `TST-UI-240.3` | Localization | `TranslationParityTest` | `REQ-UI-280` Clause 5 | Specified |
| `TST-UI-240.4` | Regression | `./gradlew testDebugUnitTest` | `REQ-PRO-001` | Specified |
