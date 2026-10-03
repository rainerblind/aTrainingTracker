# Stage 5: Walkthrough & Verification - ATT-2042: Lock Favorite Location Bottom Sheet to Peek Height & Remove Misleading Drag Handle

**Ticket**: [ATT-2042](https://atrainingtracker.atlassian.net/browse/ATT-2042)  
**Sub-task**: [ATT-2069](https://atrainingtracker.atlassian.net/browse/ATT-2069) (`[Test]`)  
**Parent Epic**: [ATT-1396](https://atrainingtracker.atlassian.net/browse/ATT-1396) (*Lieblingsorte: Visible Value, Auto-Naming & Tracking Integration*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.12`  
**Requirement Mapping**: `REQ-UI-238` (*Lieblingsorte/Map: Lock Favorite Location Bottom Sheet to Peek Height & Remove Misleading Drag Handle*)  
**Test Spec ID**: `TST-UI-197`  
**Branch**: `feature/ATT-2042`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-02  

---

## 1. Executive Summary & Verification Overview

In the central navigation map (`MapScreenWithTrack.kt`), tapping a favorite location (*Lieblingsort*) marker pin reveals an interactive bottom peek sheet rendered via `KnownLocationOnMapSheet`.

Unlike routes (`RouteOnMapScreen`) and segments (`SegmentOnMapScreen`)—which contain rich, multi-section scrollable content (elevation profile charts, lap split breakdowns, speed/HR/power telemetry graphs, Strava leaderboards) designed for full-screen expansion (`SheetValue.Expanded`)—the favorite location sheet consists exclusively of a compact 3-line summary card:
- Location Name & Edit Icon Button
- Calibrated Reference Altitude Metric
- Historical Starts Count Badge

### Solved Deficiencies:
1. **Misleading Drag Handle Excised**: In `KnownLocationOnMapSheet`, the misleading `MinimumDragHandle()` at the top edge was removed. Top padding was adjusted to `16.dp` for a clean floating card layout.
2. **Sheet Swiping & Gestures Locked When Active**: In `MapScreenWithTrack.kt`, `BottomSheetScaffold` now configures `sheetSwipeEnabled = selectedLocationId == null`. When an athlete interacts with a favorite location, dragging and swiping the sheet is completely disabled.
3. **Programmatic / Fling Expansion Rejected**: In `rememberStandardBottomSheetState`, `confirmValueChange` explicitly blocks `SheetValue.Expanded` whenever `selectedLocationId != null`, anchoring the sheet strictly to peek height (`BottomSheetDesign.PeekHeightKnownLocation + navBarHeight`).
4. **All Interactions & Invariants Preserved**:
   - Dismissal via tapping the map canvas or pressing the back button (`BackHandler`) resets `selectedLocationId = null` and collapses the sheet cleanly.
   - Tapping the edit button launches `EditKnownLocationDialog` with live geofence preview.
   - Upward dragging and full expansion for segments and routes remain 100% functional.

---

## 2. Requirement & Test Verification Matrix

| Requirement Clause | Test Case ID | Verification Method | Result | Status in Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-UI-238` (Clause 1: Swipe Lockout) | `[TST-UI-197.1]` | Contract Test (`FavoriteLocationSheetContractTest`) | **PASSED** | `Verified` |
| `REQ-UI-238` (Clause 2: Expansion Rejection) | `[TST-UI-197.1]` | Contract Test (`FavoriteLocationSheetContractTest`) | **PASSED** | `Verified` |
| `REQ-UI-238` (Clause 3: Drag Handle Removed) | `[TST-UI-197.1]` | Contract Test (`FavoriteLocationSheetContractTest`) | **PASSED** | `Verified` |
| `REQ-UI-238` (Clause 4: Invariants & Peek Height) | `[TST-UI-197.1]` | Contract Test (`BottomSheetVisualContractTest`) | **PASSED** | `Verified` |
| `REQ-PRO-001` (Regression Safety) | `[TST-UI-197.2]` | Clean-Room Full Suite Regression (`./gradlew testDebugUnitTest`) | **PASSED** (100%) | `Verified` |
| `REQ-LOC-001` (Localization Parity) | `[TST-UI-197.3]` | Resource Parity across 9 locales | **PASSED** | `Verified` |

---

## 3. Automated Test Evidence

### Targeted Unit & Visual Contract Tests
- `FavoriteLocationSheetContractTest`: 100% PASSED (5/5 tests).
  - `testMapScreenWithTrack_disablesSheetSwipeWhenLocationSelected`: PASSED
  - `testMapScreenWithTrack_confirmValueChange_rejectsExpandedWhenLocationSelected`: PASSED
  - `testKnownLocationOnMapSheet_doesNotContainDragHandle`: PASSED
  - `testKnownLocationOnMapSheet_hasCorrectTopPadding`: PASSED
  - `testMapScreenWithTrack_consumesPeekHeightKnownLocation`: PASSED
- `BottomSheetVisualContractTest`: 100% PASSED (all tests).

---

## 4. Hardware / Physical Verification (Google Pixel 10)

1. **Lieblingsort Pin Tap**:
   - Open map screen (`MapScreenWithTrack`).
   - Tap any favorite location marker pin (e.g. "Zuhause").
   - Verify that info card appears at peek height (~108dp + navBarHeight) displaying Name, Edit button, Altitude, and Starts count.
   - Verify that NO drag handle pill is rendered at the top of the card.
2. **Expansion Gesture Block**:
   - Attempt to drag or swipe the info card upwards.
   - Verify that the card does not move upward into an empty full-screen void; the sheet remains locked at peek height.
3. **Dismissal & Edit Dialog Launch**:
   - Tap outside the sheet on the map background or press device back button: verify the sheet collapses and pin selection clears.
   - Re-open card and tap Edit icon: verify `EditKnownLocationDialog` opens immediately with live geofence circle preview.
4. **Segment & Route Expansion Parity**:
   - Tap a segment or route on the map: verify that upward drag smoothly expands the sheet to full height revealing elevation profile and metrics.
