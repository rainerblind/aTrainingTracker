# Stage 2: Requirement & Test Specification - ATT-2042: Lock Favorite Location Bottom Sheet to Peek Height & Remove Misleading Drag Handle

**Ticket**: [ATT-2042](https://atrainingtracker.atlassian.net/browse/ATT-2042)  
**Sub-task**: [ATT-2066](https://atrainingtracker.atlassian.net/browse/ATT-2066) (`[Test-Spec]`)  
**Parent Epic**: [ATT-1396](https://atrainingtracker.atlassian.net/browse/ATT-1396) (*Lieblingsorte: Visible Value, Auto-Naming & Tracking Integration*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.12`  
**Requirement Mapping**: `REQ-UI-238` (*Lieblingsorte/Map: Lock Favorite Location Bottom Sheet to Peek Height & Remove Misleading Drag Handle*)  
**Test Spec ID**: `TST-UI-197`  
**Branch**: `feature/ATT-2042`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-02  

---

## 1. Requirement Specification (`REQ-UI-238`)

### 1.1 Problem Statement & Rationale
In the central navigation map (`MapScreenWithTrack.kt`), tapping an athlete's favorite location (*Lieblingsort*) marker pin sets `selectedLocationId` and reveals an interactive bottom peek sheet rendered via `KnownLocationOnMapSheet`.

Unlike routes (`RouteOnMapScreen`) and segments (`SegmentOnMapScreen`)—which contain rich, multi-section scrollable content (elevation profile charts, lap split breakdowns, speed/HR/power telemetry graphs, Strava leaderboards) designed for full-screen upward expansion (`SheetValue.Expanded`)—the favorite location sheet consists exclusively of a compact 3-line summary card:
- Location Name & Edit Icon Button
- Calibrated Reference Altitude Metric
- Historical Starts Count Badge

Under the current implementation:
1. **Misleading Drag Affordance**: `KnownLocationOnMapSheet` renders a `MinimumDragHandle()` at the top edge. In Material 3 design grammar, a drag handle explicitly communicates to the user that the container can and should be dragged upwards to reveal additional content.
2. **Empty Void on Upward Expansion**: Because `KnownLocationOnMapSheet` only has a compact ~80dp information footprint, dragging the sheet upwards expands `BottomSheetScaffold` to full screen height (`maxSheetHeight = maxHeight - statusBarHeight`), revealing a large, completely empty blank/surface void below the card. This creates an awkward, unfinished user experience.

`REQ-UI-238` locks the bottom sheet firmly to its peek height (`BottomSheetDesign.PeekHeightKnownLocation + navBarHeight`) whenever a favorite location is selected by disabling sheet swiping/dragging, rejecting transitions to `SheetValue.Expanded` in `confirmValueChange`, and excising the misleading drag handle from `KnownLocationOnMapSheet`.

### 1.2 Functional & Architectural Requirements
The system SHALL lock the favorite location bottom sheet to its designated peek height, disable upward dragging/swiping gestures when a location is active, reject full-screen expansion, remove the misleading drag handle, and preserve all dismiss and edit interactions (ATT-2042):

1. **Gesture & Swipe Lockout on `BottomSheetScaffold` (`MapScreenWithTrack.kt`)**:
   - In `MapScreenWithTrack.kt`, `BottomSheetScaffold` SHALL configure `sheetSwipeEnabled = selectedLocationId == null`.
   - When `selectedLocationId != null` (favorite location pin selected), dragging and swiping the sheet up or down SHALL be completely disabled.
   - When `selectedLocationId == null` (e.g. segment or route selected), standard swipe gestures SHALL remain enabled.

2. **Programmatic & State Expansion Rejection (`rememberStandardBottomSheetState`)**:
   - In `MapScreenWithTrack.kt`, `scaffoldState` SHALL configure `rememberStandardBottomSheetState` with `confirmValueChange`:
     ```kotlin
     confirmValueChange = { targetValue ->
         if (selectedLocationId != null && targetValue == SheetValue.Expanded) {
             false
         } else {
             true
         }
     }
     ```
   - Any programmatic or fling attempt to expand the sheet to `SheetValue.Expanded` while a favorite location is active SHALL be rejected, keeping the sheet anchored strictly at `BottomSheetDesign.PeekHeightKnownLocation + navBarHeight`.

3. **Drag Affordance Excised & Polished Layout (`KnownLocationOnMapSheet`)**:
   - In `KnownLocationOnMapSheet` (in `MapScreenWithTrack.kt`), the misleading `MinimumDragHandle()` component SHALL be removed.
   - The root container `Column` SHALL apply clean padding: `padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 12.dp)`.

4. **Interaction & Invariant Parity**:
   - Tapping the map canvas outside the sheet or pressing the system back button (`BackHandler`) SHALL continue to collapse and dismiss the card cleanly (`selectedLocationId = null`).
   - Tapping the edit icon button SHALL continue to launch `EditKnownLocationDialog` with live geofence adjustment.
   - Full-screen upward expansion (`SheetValue.Expanded`) for `SegmentOnMapScreen` and `RouteOnMapScreen` SHALL remain 100% functional with dynamic self-measuring peek heights (`REQ-UI-221`, `REQ-UI-237`).
   - 100% 9-language localization parity across all supported application locales.

### 1.3 Requirement Archaeology & Chesterton's Fence Audit
1. *Original Requirement ID & Target*: Net-new requirement (`REQ-UI-238`), refining `REQ-UI-180` (*Display Favorite Locations on Central Navigation Map*) and `REQ-UI-221` (*Standardized Bottom Sheet Peek Height Baselines*) under Epic `ATT-1396` (*Lieblingsorte: Visible Value, Auto-Naming & Tracking Integration*).
2. *Historical Origin & Commit Trace*:
   - `REQ-UI-180` was introduced in Sprint 2026-40.4 (`ATT-1449`, commit `191ba05d`), consolidating favorite location pins and peek sheets onto the central map.
   - `REQ-UI-221` calibrated `PeekHeightKnownLocation = 108.dp` in Sprint 2026-40.8 (`ATT-1645`, commit `9c3dd09e`).
3. *Root Reason for Existing Formulation*: `KnownLocationOnMapSheet` inherited `MinimumDragHandle()` from the initial bottom sheet template when the central map was unified. The absence of scrollable content in favorite locations makes upward expansion superfluous and visually undesirable.
4. *Preservation of Core Invariants*: Route and segment expansion (`REQ-UI-221`, `REQ-UI-237`), map pin heart markers, geofence radius rendering, dismissal back handlers, edit dialog launches (`REQ-UI-061`), and 9-language localization parity are 100% strictly preserved.

### 1.4 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (No Drag Handle on Location Card)**:
  - *Given* an athlete tapping a favorite location pin on the central navigation map (`MapScreenWithTrack.kt`),
  - *When* the bottom info card appears,
  - *Then* no drag handle (`MinimumDragHandle`) SHALL be rendered above the title row.
* **Criterion 2 (Sheet Upward Expansion Prohibited)**:
  - *Given* the favorite location info card is displayed at peek height,
  - *When* the athlete attempts to drag or swipe the card upwards,
  - *Then* the sheet SHALL remain locked at peek height and SHALL NOT expand into an empty full-screen view (`confirmValueChange` rejects `SheetValue.Expanded`).
* **Criterion 3 (Swipe Disabled When Location Selected)**:
  - *Given* `selectedLocationId != null`,
  - *When* `BottomSheetScaffold` evaluates `sheetSwipeEnabled`,
  - *Then* `sheetSwipeEnabled` SHALL evaluate to `false`.
* **Criterion 4 (Dismissal and Edit Parity)**:
  - *Given* the favorite location info card is displayed,
  - *When* the athlete taps the map canvas or presses the system back button,
  - *Then* the sheet SHALL collapse and `selectedLocationId` SHALL be reset to `null`.
  - *When* the athlete taps the edit icon button,
  - *Then* `EditKnownLocationDialog` SHALL open with live radius adjustment.
* **Criterion 5 (Route & Segment Expansion Preserved)**:
  - *Given* a route or segment selected on the map (`selectedLocationId == null`),
  - *When* the user drags the sheet upwards,
  - *Then* the sheet SHALL expand smoothly to `SheetValue.Expanded` displaying all charts, telemetry, and leaderboards.

---

## 2. Test Specification (`TST-UI-197`)

### 2.1 Scope & Test Cases

#### Test Case 1: `FavoriteLocationSheetContractTest` (`[TST-UI-197.1]`)
* **Scope**: Composable Structural & Visual Contract Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/FavoriteLocationSheetContractTest.kt`
* **Test Procedures**:
  1. `testMapScreenWithTrack_disablesSheetSwipeWhenLocationSelected`:
     - Verifies `MapScreenWithTrack.kt` sets `sheetSwipeEnabled = selectedLocationId == null` on `BottomSheetScaffold`.
  2. `testMapScreenWithTrack_confirmValueChange_rejectsExpandedWhenLocationSelected`:
     - Verifies `rememberStandardBottomSheetState` provides `confirmValueChange` blocking `SheetValue.Expanded` when `selectedLocationId != null`.
  3. `testKnownLocationOnMapSheet_doesNotContainDragHandle`:
     - Verifies `KnownLocationOnMapSheet` composable does not call `MinimumDragHandle()`.
  4. `testKnownLocationOnMapSheet_hasCorrectTopPadding`:
     - Verifies `KnownLocationOnMapSheet` applies `top = 16.dp` padding.
  5. `testBottomSheetVisualContract_preservesPeekHeightKnownLocation`:
     - Verifies `MapScreenWithTrack.kt` continues to consume `BottomSheetDesign.PeekHeightKnownLocation`.

#### Test Case 2: `Clean-Room Full Suite Regression Execution` (`[TST-UI-197.2]`)
* **Scope**: Automated Unit & Integration Suite Regression
* **Target Command**: `./gradlew testDebugUnitTest`
* **Verification Objective**: 100% pass rate across the full application test suite without regressions.

#### Test Case 3: `Localization Parity Audit` (`[TST-UI-197.3]`)
* **Scope**: Translation Coverage & Parity Audit
* **Target Test**: `com.atrainingtracker.trainingtracker.localization.TranslationParityTest`
* **Verification Objective**: Zero missing translation keys and 100% format specifier safety across all 9 supported locales.

---

## 3. Traceability Matrix

| Requirement ID | Acceptance Criterion | Test Spec ID | Target Test File | Status |
|:---|:---|:---|:---|:---|
| `REQ-UI-238` | AC-1 (No Drag Handle) | `TST-UI-197.1` | `FavoriteLocationSheetContractTest.kt` | Planned |
| `REQ-UI-238` | AC-2 (Expansion Prohibited) | `TST-UI-197.1` | `FavoriteLocationSheetContractTest.kt` | Planned |
| `REQ-UI-238` | AC-3 (Swipe Disabled) | `TST-UI-197.1` | `FavoriteLocationSheetContractTest.kt` | Planned |
| `REQ-UI-238` | AC-4 (Dismiss & Edit Parity) | `TST-UI-197.1` | `FavoriteLocationSheetContractTest.kt` | Planned |
| `REQ-UI-238` | AC-5 (Route/Segment Expansion) | `TST-UI-197.1` | `BottomSheetVisualContractTest.kt` | Planned |
| `REQ-LOC-001` | Localization Parity | `TST-UI-197.3` | `TranslationParityTest.kt` | Planned |
