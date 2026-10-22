# Stage 2: Requirement & Test Specification - ATT-4285: Keep RouteTabbedScreen in composition while EditRouteScreen bottom sheet is open to prevent tab bar scroll jump

**Ticket**: [[ATT-4285]](https://rainerblind.atlassian.net/browse/ATT-4285)  
**Sub-task**: [[ATT-4341]](https://rainerblind.atlassian.net/browse/ATT-4341) (`[Req & Test Spec]`)  
**Parent Epic**: [[ATT-66]](https://rainerblind.atlassian.net/browse/ATT-66) (*Improve Routes*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-43.1`  
**Requirement Mapping**: `REQ-UI-423` (*Modal Overlay Composition Retention for Route Tabbed Screen During Route Editing*)  
**Test Spec ID**: `TST-UI-387`  
**Branch**: `feature/ATT-4285`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-20  

---

## 1. Requirement Specification ([REQ-UI-423])

### 1.1 Problem Statement & Rationale
In **aTrainingTracker**, the Routes hub (`RoutesScreen.kt`) provides comprehensive route asset management, sorting, filtering, and preview inspection across four activity tabs (All, Bike, Run, Other). Routes can be inspected on a full-screen interactive map (`RouteOnMapScreen`), or edited directly via an inline modal bottom sheet (`EditRouteScreen.kt` wrapping `AppModalBottomSheet`).

During on-device testing in the Sprint Review of `ATT-4049` (Multi-file GPX route selection and import), a noticeable visual regression was observed when editing a route on non-zero tabs (specifically on the rightmost tab, "Sonstige" / "Other" corresponding to `BSportType.UNKNOWN`, page index 3 in `pagerState`):
1. **Abrupt Tab Bar Jump Animation**: When the athlete taps the dedicated edit action button on a route card in the rightmost tab, `EditRouteScreen` opens. Upon saving or dismissing the bottom sheet, the `PrimaryScrollableTabRow` header visibly animates/jumps from the leftmost tab ("Alle" / "All") all the way across to the rightmost tab.
2. **Loss of Collapsing Header Offset**: Any vertical scroll offset accumulated in `CollapsingAppBarNestedScrollConnection` is lost; the header snaps back to fully expanded (`appBarOffset = 0`).
3. **Empty Background Flashing During Sheet Display**: While `EditRouteScreen` is open, the underlying screen is completely unmounted, rendering a blank `Box` container behind the bottom sheet scrim rather than the dimmed routes list.

The root cause in `RoutesScreen.kt` (lines 186–277) is that `EditRouteScreen` was wired into an exclusive conditional branch:
```kotlin
if (selectedRouteIdForDetails != null) {
    RouteOnMapScreen(...)
} else if (selectedRouteIdForEdit != null) {
    EditRouteScreen(...)
} else {
    RouteTabbedScreen(...)
}
```
Because `RouteTabbedScreen` is removed from the Compose composition tree whenever `selectedRouteIdForEdit != null` and re-created from scratch when `selectedRouteIdForEdit` is reset to `null`, `PrimaryScrollableTabRow` initializes a new internal `ScrollState(0)` at the leftmost tab and then animates (`animateScrollTo`) to `pagerState.currentPage` on first layout. It also resets `CollapsingAppBarNestedScrollConnection` and unmounts the list states.

The expected behavior is that opening and dismissing `EditRouteScreen` acts as a true modal overlay over `RouteTabbedScreen`. `RouteTabbedScreen` must remain mounted in the composition tree throughout the bottom sheet lifecycle, preserving tab scroll position, list scroll states, and collapsing header offsets with zero tab bar jump animation upon dismissal.

### 1.2 Functional & Architectural Requirements
The system SHALL retain `RouteTabbedScreen` in the composition tree during route editing and render `EditRouteScreen` as an overlay, conforming to ASPICE Governance Rule 47 and Design Guidelines Section 5.8 (`REQ-UI-423`):

1. **Continuous Composition Retention of `RouteTabbedScreen` (`REQ-UI-423.1`)**:
   - In `RoutesScreen.kt`, when `selectedRouteIdForDetails == null`, `RouteTabbedScreen` SHALL remain unconditionally mounted in the composition tree regardless of whether `selectedRouteIdForEdit` is `null` or populated with an active route ID.
   - `RouteTabbedScreen` SHALL NOT be unmounted or disposed when the athlete opens the route editor.

2. **Modal Overlay Composition for `EditRouteScreen` (`REQ-UI-423.2`)**:
   - In `RoutesScreen.kt`, `EditRouteScreen` SHALL be rendered conditionally *alongside* `RouteTabbedScreen` inside the non-details branch whenever `selectedRouteIdForEdit != null` and a matching route is found in `routes`.
   - The mutually exclusive `else if (selectedRouteIdForEdit != null)` branch is **STRICTLY PROHIBITED** in `RoutesScreen.kt`.
   - `EditRouteScreen` (which wraps `AppModalBottomSheet`) SHALL render on top of the mounted `RouteTabbedScreen`, displaying the dimmed route list beneath the modal sheet scrim.

3. **Tab Bar Scroll Offset & Header State Preservation (`REQ-UI-423.3`)**:
   - Dismissing `EditRouteScreen` (via save, cancel, back button navigation, or backdrop scrim click) SHALL clear `selectedRouteIdForEdit = null` without triggering unmounting, remounting, or scroll reset of `RouteTabbedScreen`.
   - The internal horizontal `ScrollState` of `PrimaryScrollableTabRow` SHALL remain intact at its current scroll position, preventing jump animations from tab 0 to the active tab (e.g. tab 3 "Other" / "Sonstige").
   - `CollapsingAppBarNestedScrollConnection` SHALL preserve its accumulated `appBarOffset` without snapping back to expanded state.
   - Lazy list scroll states (`allSportsListState`, `bikeListState`, `runListState`, `otherListState`) SHALL remain preserved.

4. **Preservation of System & Navigation Invariants (`REQ-UI-423.4`)**:
   - Full-screen route detail inspection (`RouteOnMapScreen` when `selectedRouteIdForDetails != null`) SHALL remain completely unaffected.
   - Route metadata updates via `viewModel.updateRoute(it)` and cancellation logic in `EditRouteScreen` SHALL remain 100% functional.
   - Back button handling (`BackHandler`) inside the route editing overlay SHALL dismiss `EditRouteScreen` by setting `selectedRouteIdForEdit = null`.
   - Clean-room unit test suite pass rate under `./gradlew testDebugUnitTest` SHALL remain 100%.

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Overlay Composition During Editing)**:
  * *Given* the athlete is on any non-zero tab (e.g. Tab 3 "Other" / "Sonstige") in `RouteTabbedScreen` with a scrolled list and partially collapsed header,
  * *When* the athlete taps the edit action on a route card setting `selectedRouteIdForEdit`,
  * *Then* `RouteTabbedScreen` remains continuously mounted in the composition tree, and `EditRouteScreen` is displayed as an overlay on top of the dimmed route list.
* **Criterion 2 (Tab Bar Scroll Offset & Zero Jump on Dismissal)**:
  * *Given* `EditRouteScreen` is active as a modal overlay over Tab 3,
  * *When* the athlete saves changes (`onSave`), cancels (`onCancel`), taps the scrim, or presses the back button (`BackHandler`),
  * *Then* `EditRouteScreen` is dismissed (`selectedRouteIdForEdit = null`), `RouteTabbedScreen` remains mounted without remounting, and `PrimaryScrollableTabRow` retains its exact horizontal scroll offset without jumping or animating from Tab 0.
* **Criterion 3 (Collapsing Header Offset Preservation)**:
  * *Given* `RouteTabbedScreen` with an accumulated collapsing app bar offset (`appBarOffset != 0`),
  * *When* `EditRouteScreen` is opened and subsequently dismissed,
  * *Then* the app bar retains its exact vertical scroll offset without snapping back to fully expanded.
* **Criterion 4 (Full-Screen Route Map Inspection Parity)**:
  * *Given* `selectedRouteIdForDetails != null`,
  * *When* a route is selected for map inspection,
  * *Then* `RouteOnMapScreen` continues to display as a full-screen view.
* **Criterion 5 (9-Language Parity & Zero New String Mutations)**:
  * *Given* all 9 canonical application locales (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`),
  * *When* audited for route editing UI strings,
  * *Then* zero new string resources are required, and existing route editing strings maintain 100% translation parity with zero missing keys.

### 1.4 System Invariants
1. **Modal Overlay Composition Retention Invariant (Rule 47)**: Modal bottom sheets and dialogs must always be rendered as overlays alongside the host screen, never in mutually exclusive branches that unmount the underlying screen.
2. **State Continuity Invariant**: Non-saveable Compose state (`PrimaryScrollableTabRow` internal `ScrollState`, `CollapsingAppBarNestedScrollConnection.appBarOffset`, lazy list states) must not be destroyed by opening/closing modal sheets.
3. **Data Mutation Parity**: Route updating via `viewModel.updateRoute(it)` and cancellation logic must remain 100% functional.
4. **Clean-Room Test Suite Pass Rate**: `./gradlew testDebugUnitTest` must pass with 100% success (0 regressions).
5. **Single Atomic Commit Invariant (Rule 44)**: Zero intermediate git commits during Stage 1–4; all deliverables, code, and tests are squashed into a single atomic commit at the end of Stage 5.

### Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Net-new requirement `REQ-UI-423`, refining `REQ-UI-191` (*Standardized List Item Heading Click Interaction Semantics & Dedicated Edit Affordance*) and `REQ-UI-159` (*Single-Activity Architecture & Jetpack Compose Navigation Modernization*), and enforcing Governance Rule 47 / Design Guidelines Section 5.8 (*Modal Overlay Composition Retention*). Targets `RoutesScreen.kt` and `RoutesScreenContractTest.kt`.
* **Historical Origin & Commit Trace**: `ef96c234` (Sprint 2026-40.7, ATT-1083) migrated navigation to Compose with an initial `if / else if / else` branching model; `fea001b7` (Sprint 2026-41.2, ATT-1541 / `REQ-UI-191`) added dedicated edit button setting `selectedRouteIdForEdit = id`. Sprint Review 2026-42.10 (ATT-4049) discovered tab bar jump when editing from tab index 3.
* **Root Reason for Existing Formulation**: When `RoutesScreen.kt` was first created during the single-activity Compose migration (ATT-1083), screen-level destination states (`selectedRouteIdForDetails` and `selectedRouteIdForEdit`) were naively modeled as mutually exclusive `if / else if / else` branches. The developer did not distinguish between a full-screen replacement destination (`RouteOnMapScreen`) and a modal bottom sheet overlay (`EditRouteScreen` wrapping `AppModalBottomSheet`), inadvertently unmounting `RouteTabbedScreen` whenever a route edit was triggered.
* **Preservation of Core Invariants**: Full-screen route details inspection (`RouteOnMapScreen` when `selectedRouteIdForDetails != null`) remains completely intact. Route editing save (`viewModel.updateRoute`), cancel, and dismiss behaviors in `EditRouteScreen` remain 100% intact. `PrimaryScrollableTabRow`, `CollapsingAppBarNestedScrollConnection`, and list scroll states are preserved without unmounting. All existing `RoutesScreenContractTest` assertions pass. Clean-room test suite `./gradlew testDebugUnitTest` remains at 100% pass rate.

---

## 2. Test Specification ([TST-UI-387])

### Test Case 1: `testRoutesScreen_compositionRetention_routeTabbedScreenMountedWhenEditing` (`[TST-UI-387.1]`)
* **Scope**: Architectural & Composition Hierarchy Contract Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/routes/RoutesScreenContractTest.kt`
* **Preconditions**: `RoutesScreen.kt` source file resolved.
* **Action**: Parse `RoutesScreen.kt` source text:
  1. Inspect the non-details branch (`else` corresponding to `selectedRouteIdForDetails == null`).
  2. Verify that `RouteTabbedScreen` is called unconditionally within this branch.
  3. Verify that `EditRouteScreen` is composed *inside* the same branch alongside `RouteTabbedScreen`, conditioned on `if (selectedRouteIdForEdit != null)`.
  4. Verify that `else if (selectedRouteIdForEdit != null)` is **strictly absent** from `RoutesScreen.kt`.
* **Expected Result**:
  - `RouteTabbedScreen` is rendered unconditionally when not inspecting details.
  - `EditRouteScreen` is composed as an overlay inside the same container branch.
  - Exclusive branching `else if (selectedRouteIdForEdit != null)` is absent (`assertFalse`).

### Test Case 2: `testRoutesScreen_editRouteScreen_overlayContractsAndDismissal` (`[TST-UI-387.2]`)
* **Scope**: Contract & Dismissal State Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/routes/RoutesScreenContractTest.kt`
* **Preconditions**: `RoutesScreen.kt` source file resolved.
* **Action**: Inspect `EditRouteScreen` invocation and dismiss callbacks in `RoutesScreen.kt`:
  1. Verify `onSave` invokes `viewModel.updateRoute(it)` and clears `selectedRouteIdForEdit = null`.
  2. Verify `onCancel` clears `selectedRouteIdForEdit = null`.
  3. Verify `BackHandler` is declared within the `selectedRouteIdForEdit != null` block and clears `selectedRouteIdForEdit = null`.
* **Expected Result**:
  - All dismissal paths (`onSave`, `onCancel`, `BackHandler`) cleanly reset `selectedRouteIdForEdit = null`.
  - Route mutation `viewModel.updateRoute(it)` is invoked upon save.

### Test Case 3: 9-Language Localization & String Resource Audit (`[TST-UI-387.3]`)
* **Scope**: 9-Locale Localization Verification
* **Target**: Canonical 9 application locales:
  * `values/strings.xml` (EN)
  * `values-de/strings.xml` (DE)
  * `values-es/strings.xml` (ES)
  * `values-fr/strings.xml` (FR)
  * `values-it/strings.xml` (IT)
  * `values-ja/strings.xml` (JA)
  * `values-nl/strings.xml` (NL)
  * `values-pl/strings.xml` (PL)
  * `values-pt/strings.xml` (PT)
* **Goal**: Verify string presence, non-blank values, and zero regressions across all route editing string tokens:
  - `route_edit` (Title of the bottom sheet)
  - `name` (Label for route name text field)
  - `route_sport_type` (Header for sport type selection)
  - `save` / `cancel` (Action button labels)
* **9-Locale Verification Findings**:
  - **New String Resources**: **None**. No new string resources are required for this ticket. The change is strictly an architectural Compose layout refactoring in `RoutesScreen.kt`.
  - **Existing String Integrity**: All required string resources already exist and have 100% translation parity across all 9 canonical locales.
  - **Format Specifiers**: Zero format specifiers added or modified.

### Test Case 4: Clean-Room Regression Suite (`[TST-UI-387.4]`)
* **Scope**: Clean-Room Full Suite Regression
* **Command**: `./gradlew testDebugUnitTest`
* **Goal**: Verify 100% pass rate across the full test suite with zero regressions.

---

## 3. Traceability Matrix

| Test Case | Scope | Target File / Verification Item | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `[TST-UI-387.1]` | Contract | `RoutesScreenContractTest.kt` (`RouteTabbedScreen` composition retention; `EditRouteScreen` overlay composition; exclusion of `else if`) | `REQ-UI-423.1`, `REQ-UI-423.2` | Specified |
| `[TST-UI-387.2]` | Contract | `RoutesScreenContractTest.kt` (`EditRouteScreen` dismissal contracts: `onSave`, `onCancel`, `BackHandler` clearing `selectedRouteIdForEdit`) | `REQ-UI-423.3`, `REQ-UI-423.4` | Specified |
| `[TST-UI-387.3]` | Localization | 9-locale audit across `values*/strings.xml` (confirming zero new strings required, 100% parity) | `REQ-UI-423.5`, `REQ-LOC-001` | Specified |
| `[TST-UI-387.4]` | Regression | `./gradlew testDebugUnitTest` | `REQ-PRO-001` | Specified |
