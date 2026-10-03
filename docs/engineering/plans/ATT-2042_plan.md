# Stage 3: Implementation Plan - ATT-2042: [Lieblingsorte/Map] Lock Favorite Location Bottom Sheet to Peek Height & Remove Misleading Drag Handle

**Ticket**: [ATT-2042](https://atrainingtracker.atlassian.net/browse/ATT-2042)  
**Sub-task**: [ATT-2067](https://atrainingtracker.atlassian.net/browse/ATT-2067) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-1396](https://atrainingtracker.atlassian.net/browse/ATT-1396) (*Lieblingsorte: Visible Value, Auto-Naming & Tracking Integration*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.12`  
**Requirement Mapping**: `REQ-UI-238`  
**Test Mapping**: `TST-UI-197`  
**Branch**: `feature/ATT-2042`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-02  

---

## 1. Problem Description & Background

In the central navigation map ([MapScreenWithTrack.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapScreenWithTrack.kt)), tapping an athlete's favorite location (*Lieblingsort*) marker pin sets `selectedLocationId` and reveals an interactive bottom peek sheet rendered via `KnownLocationOnMapSheet`.

Unlike routes (`RouteOnMapScreen`) and segments (`SegmentOnMapScreen`)—which contain rich, multi-section scrollable content (elevation profile charts, lap split breakdowns, speed/HR/power telemetry graphs, Strava leaderboards) designed for full-screen expansion (`SheetValue.Expanded`)—the favorite location sheet consists exclusively of a compact 3-line summary card:
- Location Name & Edit Icon Button
- Calibrated Reference Altitude Metric
- Historical Starts Count Badge

### Current Deficiencies:
1. **Misleading Drag Handle**: `KnownLocationOnMapSheet` renders a `MinimumDragHandle()` at the top edge. In Material 3 design grammar, a drag handle communicates that the container can and should be dragged upwards to reveal additional content.
2. **Empty Void on Upward Expansion**: Because `KnownLocationOnMapSheet` only has a compact ~80dp information footprint, dragging the sheet upwards expands `BottomSheetScaffold` to full screen height (`maxSheetHeight = maxHeight - statusBarHeight`), revealing a large, completely empty blank/surface void below the card. This creates an awkward, unfinished user experience.

`REQ-UI-238` locks the bottom sheet firmly to its peek height (`BottomSheetDesign.PeekHeightKnownLocation + navBarHeight`) whenever a favorite location is selected by disabling sheet swiping/dragging, rejecting transitions to `SheetValue.Expanded` in `confirmValueChange`, and excising the misleading drag handle from `KnownLocationOnMapSheet`.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-UI-238` (*Lieblingsorte/Map: Lock Favorite Location Bottom Sheet to Peek Height & Remove Misleading Drag Handle*)
  - Clause 1: Gesture & Swipe Lockout on `BottomSheetScaffold` (`sheetSwipeEnabled = selectedLocationId == null`).
  - Clause 2: Programmatic & State Expansion Rejection (`confirmValueChange` in `rememberStandardBottomSheetState`).
  - Clause 3: Drag Affordance Excised & Polished Layout in `KnownLocationOnMapSheet`.
  - Clause 4: Interaction & Invariant Parity (Map tap dismissal, back handler, edit dialog launch, segment/route expansion).
* **Test Mapping**: `TST-UI-197` (*Lieblingsorte/Map: Favorite Location Bottom Sheet Non-Expandable Contract & Drag Handle Removal Verification*)
  - `[TST-UI-197.1]`: `FavoriteLocationSheetContractTest.kt` (Structural & visual contract verification).
  - `[TST-UI-197.2]`: Full test suite clean-room regression (`./gradlew testDebugUnitTest`).
  - `[TST-UI-197.3]`: Localization parity verification across all 9 application locales.

---

## 3. System Invariants & Preserved Behavior

1. **Route & Segment Full Expansion Intact**: `SegmentOnMapScreen` and `RouteOnMapScreen` continue to support smooth upward swiping and full-screen expansion (`SheetValue.Expanded`) with dynamic header peek measurement (`REQ-UI-221`, `REQ-UI-237`).
2. **Dismissal & Navigation Parity**: Tapping the map canvas outside the sheet or pressing the system back button (`BackHandler`) collapses the sheet and clears `selectedLocationId = null`.
3. **Edit Dialog Functionality**: Tapping the edit icon button continues to launch `EditKnownLocationDialog` with live geofence radius adjustment.
4. **Peek Baseline Invariant**: Favorite location sheet peek height remains calibrated to `BottomSheetDesign.PeekHeightKnownLocation + navBarHeight` (`REQ-UI-221`).
5. **9-Language Localization Parity**: Zero hardcoded UI strings; all texts strictly resolve via Android string resources.
6. **Subtask Direct Completion**: Subtasks transition directly to `Erledigt` via transition `freigabe` upon passing Gate audit.
7. **Parent Human Gate Invariance**: Terminal completion of parent tickets remains reserved for the human user in `Final Review (Human)`. Never advance parent to `Erledigt`.
8. **Programmatic Pre-Check Gate**: Gate 3 verification via `python3 tools/jira_util.py check-gate ATT-2067` must pass before any production code edits.

---

## 4. Proposed Architectural Changes (SWE.2)

### Target File: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapScreenWithTrack.kt`

#### 1. Bottom Sheet State Configuration (`scaffoldState`)
```kotlin
val scaffoldState = rememberBottomSheetScaffoldState(
    bottomSheetState = rememberStandardBottomSheetState(
        skipHiddenState = false,
        confirmValueChange = { targetValue ->
            if (selectedLocationId != null && targetValue == SheetValue.Expanded) {
                false
            } else {
                true
            }
        }
    )
)
```

#### 2. Scaffold Gesture Lockout (`BottomSheetScaffold`)
```kotlin
BottomSheetScaffold(
    scaffoldState = scaffoldState,
    sheetSwipeEnabled = selectedLocationId == null,
    sheetPeekHeight = when {
        selectedSegmentId != null -> (measuredSegmentHeaderHeight ?: BottomSheetDesign.PeekHeightSegment) + navBarHeight
        selectedRouteId != null -> (measuredRouteHeaderHeight ?: BottomSheetDesign.PeekHeightRoute) + navBarHeight
        selectedLocationId != null -> BottomSheetDesign.PeekHeightKnownLocation + navBarHeight
        else -> 0.dp
    },
    sheetDragHandle = null,
    sheetShape = BottomSheetDesign.SheetShape,
    sheetContainerColor = MaterialTheme.colorScheme.surface,
    sheetShadowElevation = BottomSheetDesign.SheetShadowElevation,
    sheetTonalElevation = BottomSheetDesign.SheetTonalElevation,
    sheetContent = { ... }
)
```

#### 3. Polished `KnownLocationOnMapSheet` Without Drag Handle
```kotlin
@Composable
fun KnownLocationOnMapSheet(
    location: KnownLocation,
    onDismiss: () -> Unit,
    onEditLocation: (KnownLocation) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 12.dp)
    ) {
        // MinimumDragHandle() removed
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = location.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { onEditLocation(location) }) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = stringResource(R.string.edit_location_title),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.known_location_altitude_format, location.altitude.toInt()),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (location.startsCount > 0) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    tonalElevation = 0.dp
                ) {
                    Text(
                        text = stringResource(R.string.known_location_starts_badge, location.startsCount),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}
```

---

## 5. Step-by-Step Implementation Sequence

### Step 1: Author Targeted Contract Unit Test
* Target file: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/FavoriteLocationSheetContractTest.kt`
* Verifies:
  1. `sheetSwipeEnabled = selectedLocationId == null` is configured on `BottomSheetScaffold`.
  2. `confirmValueChange` rejects `SheetValue.Expanded` when `selectedLocationId != null`.
  3. `KnownLocationOnMapSheet` does not invoke `MinimumDragHandle()`.
  4. `KnownLocationOnMapSheet` applies `padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 12.dp)`.
  5. Peek height calculation consumes `BottomSheetDesign.PeekHeightKnownLocation`.

### Step 2: Implement Core Changes in `MapScreenWithTrack.kt`
* In `MapScreenWithTrack.kt`:
  1. Update `rememberStandardBottomSheetState` to add `confirmValueChange` guard.
  2. Add `sheetSwipeEnabled = selectedLocationId == null` to `BottomSheetScaffold`.
  3. Excise `MinimumDragHandle()` from `KnownLocationOnMapSheet` and adjust top padding from `8.dp` to `16.dp`.

### Step 3: Run Targeted Unit Tests
* Execute: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.map.FavoriteLocationSheetContractTest"`
* Verify all 5 test cases pass cleanly.

### Step 4: Run Clean-Room Full Test Suite Regression
* Execute: `./gradlew testDebugUnitTest`
* Ensure 100% test pass rate across the full codebase.

---

## 6. Gate 3 Readiness Checklist

- [x] Traceability: `REQ-UI-238` and `TST-UI-197` mapped to concrete classes and test files.
- [x] SWE.2 Architecture: Component signatures, state transitions, and padding details fully documented.
- [x] Invariants: Segment/route expansion, back button dismiss, and peek heights preserved.
- [x] Review Agent Pre-check: `tools/jira_util.py check-gate ATT-2067` ready to execute.
