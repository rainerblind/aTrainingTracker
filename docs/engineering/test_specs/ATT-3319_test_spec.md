# Stage 2: Requirement & Test Specification - ATT-3319: Ensure Segment and Climb bottom sheets always pop up to full height on new active entity

**Ticket**: [ATT-3319](https://rainerblind.atlassian.net/browse/ATT-3319)  
**Sub-task**: [ATT-3333](https://rainerblind.atlassian.net/browse/ATT-3333) (`[Test-Spec]`)  
**Parent Epic**: [ATT-2582](https://rainerblind.atlassian.net/browse/ATT-2582) (*Segments: Live Tracking, Exploration & Route Integration*)  
**Target Release**: `Sprint 2026-42.1`  
**Active Sprint**: `Sprint 2026-42.1`  
**Requirement Mapping**: `REQ-UI-343`  
**Test Mapping**: `TST-UI-303`  
**Branch**: `feature/ATT-3319`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-12  

---

## 1. Detailed Requirements & Architectural Constraints

### 1.1 Formal Specification (REQ-UI-343 Amendment)
The system SHALL default the live climb and segment bottom sheet (`BottomSheetScaffold`) in `SensorGridScreen.kt` to the fully expanded state (`SheetValue.Expanded`) when becoming active, providing immediate visibility of the elevation profile graph, instantaneous gradient profile, and live pacing telemetry HUD without requiring manual touch interaction or swipe gestures while riding or running (ATT-3176, ATT-3319, amending `REQ-UI-327` and aligning with Design Guidelines §5.8):

1. **Initial Sheet Value Evaluation (`SensorGridScreen.kt:233`)**:
   * `initialSheetValue` SHALL evaluate to: `if (shouldShowBottomSheet) SheetValue.Expanded else SheetValue.Hidden`.

2. **Entity-Aware Reactive Transition to Expanded State (`SensorGridScreen.kt:242`)**:
   * The system SHALL compute a composite active entity key (`activeEntityKey`) uniquely identifying the current active live segment or live climb:
     * When `showLiveSegments && activeSegment != null`, `activeEntityKey` SHALL incorporate `activeSegment.staticData.summary.stravaId`.
     * When `showLiveClimbs && activeLiveClimb != null`, `activeEntityKey` SHALL incorporate `activeLiveClimb.climb.id`, `activeLiveClimb.routeIndex`, and `activeLiveClimb.climb.name`.
     * Otherwise, `activeEntityKey` SHALL evaluate to `null`.
   * The reactive sheet controller `LaunchedEffect` SHALL be keyed on `(shouldShowBottomSheet, activeEntityKey)`.
   * When `shouldShowBottomSheet == true`, `LaunchedEffect` SHALL call `scaffoldState.bottomSheetState.expand()`, smoothly animating the sheet to `SheetValue.Expanded` regardless of whether the sheet's current state is `SheetValue.Hidden` or `SheetValue.PartiallyExpanded`.
   * When `shouldShowBottomSheet == false`, `LaunchedEffect` SHALL call `scaffoldState.bottomSheetState.hide()`.

3. **Preserved Manual Swipe & Collapse Ergonomics**:
   * When `shouldShowBottomSheet == true`, `sheetSwipeEnabled` SHALL remain `true`, allowing the athlete to manually collapse the sheet to peek height (`SheetValue.PartiallyExpanded`).
   * Because `activeEntityKey` remains constant while an active segment or climb is being traversed, manual user swipe-down gestures SHALL NOT trigger the `LaunchedEffect` or cause the sheet to bounce back up during that active entity.
   * Upon completion of the current entity and activation of a subsequent entity, `activeEntityKey` mutations SHALL re-trigger `LaunchedEffect`, reliably expanding the sheet to `SheetValue.Expanded` for the new entity.

4. **Preservation of System Invariants**:
   * Active-tab gating (`isTabActive`), hidden state handling (`SheetValue.Hidden`), zero peek height and transparent container when hidden, and 100% full-suite unit test pass rate MUST be strictly preserved.

### 1.2 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Initial Auto-Expansion on Activation)**:
  * *Given* an active workout on a tracking tab with live segments or live climbs enabled,
  * *When* the athlete approaches or enters a segment or climb (`shouldShowBottomSheet` becomes `true`),
  * *Then* the bottom sheet automatically pops up to `SheetValue.Expanded`, displaying the full elevation profile canvas and HUD without manual touch intervention.

* **Criterion 2 (Auto-Expansion from Peek State on Entity Switch)**:
  * *Given* an athlete who has previously swiped the bottom sheet down to peek height (`SheetValue.PartiallyExpanded`),
  * *When* the current segment/climb completes and a new segment or climb activates,
  * *Then* `activeEntityKey` changes and the sheet automatically expands back to `SheetValue.Expanded` for the new entity.

* **Criterion 3 (Sequential Entity Traversal along Routes)**:
  * *Given* an athlete traversing consecutive climbs or segments along a route where `shouldShowBottomSheet` remains continuously `true`,
  * *When* the active entity changes from entity A to entity B,
  * *Then* `activeEntityKey` updates, re-triggering `LaunchedEffect` and calling `scaffoldState.bottomSheetState.expand()`.

* **Criterion 4 (Manual Swipe-Down Stability during Ongoing Entity)**:
  * *Given* an active live climb or segment currently expanded,
  * *When* the athlete manually swipes down the sheet to `SheetValue.PartiallyExpanded`,
  * *Then* the sheet remains collapsed at peek height for the remainder of that entity's traversal without bouncing back up.

* **Criterion 5 (Suppression on Inactive Tabs or Disabled Mode)**:
  * *Given* an active climb or segment occurring while the athlete switches to an inactive tab or non-tracking mode (`shouldShowBottomSheet == false`),
  * *Then* the sheet animates to `SheetValue.Hidden` with 0 dp peek height.

### Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-UI-343` (*Live Segment and Climb Cockpit BottomSheet Default Fully Expanded Presentation*), amending Clause 2.
* **Historical Origin & Commit Trace**: Introduced in Sprint 2026-41.8 (`ATT-3176`, commit `6b9d0315`), which amended `REQ-UI-327` (Sprint 2026-41.7, `ATT-2945`).
* **Root Reason for Existing Formulation**: ATT-3176 added `if (scaffoldState.bottomSheetState.currentValue == SheetValue.Hidden) expand()` with the intention of avoiding redundant calls to `expand()` if already expanded. However, it inadvertently blocked expansion when the sheet was in `SheetValue.PartiallyExpanded`, and keyed only on `shouldShowBottomSheet`.
* **Preservation of Core Invariants**: Manual swipe-down gesture ergonomics during an ongoing entity remain strictly preserved because `LaunchedEffect(shouldShowBottomSheet, activeEntityKey)` does not re-fire when the athlete swipes. Tab isolation (`isTabActive`), hidden state handling (`hide()`), and 100% full-suite unit test pass rate remain strictly preserved.

---

## 2. Concrete Test Specifications & Verification Procedures

### 2.1 AST & Architectural Contract Test (`SensorGridScreenClimbSuppressionTest.kt`)

* **TST-UI-303.1: Active Entity Key Derivation**:
  * Verify that `SensorGridScreen.kt` defines `activeEntityKey` referencing `activeSegment?.staticData?.summary?.stravaId` and `activeLiveClimb`.
* **TST-UI-303.2: LaunchedEffect Keying on Entity Key & BottomSheet Visibility**:
  * Verify that `LaunchedEffect` is keyed on `(shouldShowBottomSheet, activeEntityKey)`.
* **TST-UI-303.3: Unconditional Expand Call on Positive Sheet Visibility**:
  * Verify that when `shouldShowBottomSheet` is true, `scaffoldState.bottomSheetState.expand()` is invoked without the restricting `currentValue == SheetValue.Hidden` guard.
* **TST-UI-303.4: Hide Call on Negative Sheet Visibility**:
  * Verify that when `!shouldShowBottomSheet`, `scaffoldState.bottomSheetState.hide()` is called when `currentValue != SheetValue.Hidden`.

### 2.2 Full Regression Suite (`./gradlew testDebugUnitTest`)
* Execute `./gradlew testDebugUnitTest` verifying 100% pass rate across all unit test suites.

---

## 3. Traceability Matrix

| Requirement Clause | Test Specification | Target File | Verification Method |
| :--- | :--- | :--- | :--- |
| `REQ-UI-343.1` (Initial Value) | `TST-UI-303.1` | `SensorGridScreen.kt` | Unit & Contract Test |
| `REQ-UI-343.2` (Entity Key & Expand) | `TST-UI-303.2`, `TST-UI-303.3` | `SensorGridScreen.kt` | Unit & Contract Test |
| `REQ-UI-343.3` (Swipe Ergonomics) | `TST-UI-303.2` | `SensorGridScreen.kt` | Unit & Contract Test |
| `REQ-UI-343.4` (Invariants) | `TST-UI-303.4` | `SensorGridScreen.kt` | Unit & Full Regression |

---

## 4. 9-Language Localization Parity

* **String Resource Impact**: Zero new string resources introduced.
* **Localization Verification**: All 9 supported application locales (EN, DE, ES, FR, IT, JA, NL, PL, PT) maintain 100% parity with zero missing translation keys.
