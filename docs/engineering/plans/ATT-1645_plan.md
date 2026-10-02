# Stage 3: Implementation Plan - ATT-1645: Calibrate and Optimize Initial Height & Peek Baselines for Popups and Bottom Sheets (Dynamic Self-Measuring Baselines)

**Ticket**: [ATT-1645](https://atrainingtracker.atlassian.net/browse/ATT-1645)  
**Sub-task**: [ATT-2011](https://atrainingtracker.atlassian.net/browse/ATT-2011) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-355](https://atrainingtracker.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.11`  
**Requirement Mapping**: `REQ-UI-221` (*UI/Sheets: Standardized Bottom Sheet Peek Height Baselines and Information Footprint Framing*)  
**Test Mapping**: `TST-UI-175` (*Bottom Sheet Peek Baselines, Design Token Centralization & System Navigation Inset Verification*)  
**Branch**: `feature/ATT-1645`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-02  

---

## 1. Problem Description & Architectural Motivation

Bottom sheet peek baselines across the application were standardized into centralized tokens in `BottomSheetDesign.kt` in Sprint `2026-40.8`. However, physical review on Google Pixel 10 hardware in Sprint `2026-40.10` confirmed that static Dp constants are inherently brittle:
1. In `SegmentOnMapScreen`, system font scaling, display zoom, or localized segment title wrapping pushes the third row of the `SegmentDetails` card (altitude icon, elevation gain, min and max altitude) behind the navigation bar.
2. In `RouteOnMapScreen`, route descriptions with differing line counts are partially obscured behind the navigation bar.
3. In `LiveSegmentSheet` (`SensorGridScreen`), `PeekHeightLiveSegment = 126.dp` cleanly frames live progress without prematurely revealing the underlying elevation profile chart, confirming that exact header boundary alignment provides an optimal athletic experience.

To solve font scaling and title line count variations permanently, this implementation plan establishes **Dynamic Self-Measuring Header Peek Baselines** while retaining `BottomSheetDesign` constants as defensive initial-render fallbacks.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-UI-221` (*UI/Sheets: Standardized Bottom Sheet Peek Height Baselines and Information Footprint Framing*)
* **Test Mapping**: `TST-UI-175` (*Bottom Sheet Peek Baselines, Design Token Centralization & System Navigation Inset Verification*)

---

## 3. System Invariants & Preserved Behavior

1. **System Insets Invariant**: Every persistent sheet peek height MUST strictly add `navBarHeight` (`WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()`) to ensure the header floats cleanly above the system navigation bar on 3-button and gesture navigation modes.
2. **Defensive Fallback Invariant**: When dynamic measurement is pending during the initial frame or in preview mode, `sheetPeekHeight` safely falls back to `BottomSheetDesign` baseline tokens, preventing zero-height layout collapses or visual glitches.
3. **Maximum Height Boundary Constraint**: The maximum expanded height constraint (`maxSheetHeight = maxHeight - statusBarHeight`) established under `REQ-SET-069` remains strictly unaltered.
4. **Drag Handle Dimensions**: `MinimumDragHandle` layout dimensions (32dp x 3dp, 15dp total vertical footprint) remain strictly unaltered.
5. **Subtask Direct Completion**: Sub-task `ATT-2011` transitions directly to `Erledigt` upon passing Gate 3 review via `freigabe`.
6. **Parent Human Gate Invariance**: Moving parent ticket `ATT-1645` to `Erledigt` remains an inviolable human decision gate reserved exclusively for the user.

---

## 4. Proposed Architectural Changes

### Component 1: `MapDetailLayout.kt` (Header Self-Measurement Container)
Add `onHeaderHeightMeasured: ((Dp) -> Unit)? = null` callback parameter. Wrap `MinimumDragHandle()` and `Surface(header)` in a column with `Modifier.fillMaxWidth().onGloballyPositioned`:
```kotlin
@Composable
fun MapDetailLayout(
    // ... existing parameters ...
    analyticsContent: (@Composable ColumnScope.() -> Unit)? = null,
    onHeaderHeightMeasured: ((Dp) -> Unit)? = null
) {
    // ...
    Column(
        modifier = modifier
            .then(if (showMap) Modifier.fillMaxSize() else Modifier.wrapContentHeight())
            .then(if (!useStatusBarsPadding) Modifier.background(MaterialTheme.colorScheme.surface) else Modifier)
    ) {
        val density = LocalDensity.current
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .onGloballyPositioned { coordinates ->
                    if (!useStatusBarsPadding && onHeaderHeightMeasured != null) {
                        val heightDp = with(density) { coordinates.size.height.toDp() }
                        onHeaderHeightMeasured(heightDp)
                    }
                }
        ) {
            // DRAG HANDLE (For sheets)
            if (!useStatusBarsPadding) {
                MinimumDragHandle()
            }

            // 1. HEADER (Slotted)
            Surface(
                color = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
                shape = if (useStatusBarsPadding) RectangleShape else BottomSheetDesign.SheetShape,
                modifier = if (useStatusBarsPadding) Modifier.statusBarsPadding() else Modifier
            ) {
                Box(modifier = Modifier.drawWithContent {
                    headerLayer.record {
                        this@drawWithContent.drawContent()
                    }
                    drawLayer(headerLayer)
                }) {
                    header()
                }
            }
        }
        // ... remainder of MapDetailLayout unchanged ...
    }
}
```

### Component 2: `SegmentOnMapScreen.kt` & `RouteOnMapScreen.kt` (Screen-Level Forwarding)
Accept optional `onHeaderHeightMeasured: ((Dp) -> Unit)? = null` and forward to `MapDetailLayout`:
```kotlin
// SegmentOnMapScreen.kt
@Composable
fun SegmentOnMapScreen(
    segmentSummary: SegmentSummary?,
    segment: MapSegment?,
    backgroundPaths: List<MappablePath> = emptyList(),
    modifier: Modifier = Modifier,
    useStatusBarsPadding: Boolean = true,
    showMap: Boolean = true,
    onHeaderHeightMeasured: ((Dp) -> Unit)? = null
) {
    // ...
    MapDetailLayout(
        // ...
        onHeaderHeightMeasured = onHeaderHeightMeasured
    )
}

// RouteOnMapScreen.kt
@Composable
fun RouteOnMapScreen(
    route: MapRoute?,
    routeSummary: RouteSummary?,
    backgroundPaths: List<MappablePath> = emptyList(),
    onToggleSelection: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    useStatusBarsPadding: Boolean = true,
    showMap: Boolean = true,
    onHeaderHeightMeasured: ((Dp) -> Unit)? = null
) {
    // ...
    MapDetailLayout(
        // ...
        onHeaderHeightMeasured = onHeaderHeightMeasured
    )
}
```

### Component 3: `MapScreenWithTrack.kt` (Scaffold State Management & Dynamic Peek)
Track dynamic measured header heights for segments and routes, computing `sheetPeekHeight` dynamically with defensive fallback:
```kotlin
var measuredSegmentHeaderHeight by remember(selectedSegmentId) { mutableStateOf<Dp?>(null) }
var measuredRouteHeaderHeight by remember(selectedRouteId) { mutableStateOf<Dp?>(null) }

BottomSheetScaffold(
    scaffoldState = scaffoldState,
    sheetShape = BottomSheetDesign.SheetShape,
    sheetContainerColor = MaterialTheme.colorScheme.surface,
    sheetShadowElevation = BottomSheetDesign.SheetShadowElevation,
    sheetTonalElevation = BottomSheetDesign.SheetTonalElevation,
    sheetPeekHeight = when {
        selectedSegmentId != null -> {
            val base = measuredSegmentHeaderHeight ?: BottomSheetDesign.PeekHeightSegment
            base + navBarHeight
        }
        selectedRouteId != null -> {
            val routeSummary = allRoutes.find { it.summary.id == selectedRouteId }?.summary
            val defaultPeek = if (routeSummary?.description.isNullOrEmpty()) {
                BottomSheetDesign.PeekHeightRoute
            } else {
                BottomSheetDesign.PeekHeightRouteWithDescription
            }
            val base = measuredRouteHeaderHeight ?: defaultPeek
            base + navBarHeight
        }
        selectedLocationId != null -> BottomSheetDesign.PeekHeightKnownLocation + navBarHeight
        else -> 0.dp
    },
    // ...
    sheetContent = {
        // ...
        SegmentOnMapScreen(
            // ...
            onHeaderHeightMeasured = { measuredSegmentHeaderHeight = it }
        )
        // ...
        RouteOnMapScreen(
            // ...
            onHeaderHeightMeasured = { measuredRouteHeaderHeight = it }
        )
    }
)
```

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Pre-Construction Gate Check
* Verify Gate 3 prerequisite: `python3 tools/jira_util.py check-gate ATT-2011`.
* Confirm command returns exit code 0 (`GATE_PASSED: ATT-2011 is Erledigt`).

### Step 2: Implement Self-Measurement in `MapDetailLayout.kt`
* **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapDetailLayout.kt`
* Add `onHeaderHeightMeasured: ((Dp) -> Unit)? = null` parameter.
* Wrap `MinimumDragHandle() + Surface(header)` in `onGloballyPositioned` column.

### Step 3: Forward Callback in `SegmentOnMapScreen.kt` & `RouteOnMapScreen.kt`
* **Files**:
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/segments/SegmentOnMapScreen.kt`
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/routes/RouteOnMapScreen.kt`
* Add parameter and forward to `MapDetailLayout`.

### Step 4: Wire Dynamic Peek in `MapScreenWithTrack.kt`
* **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapScreenWithTrack.kt`
* Remember measured heights per selected entity.
* Feed dynamic values into `sheetPeekHeight`.
* Pass measurement callbacks to child sheet composables.

### Step 5: Update Unit and Contract Tests
* **Files**:
  - `app/src/test/java/com/atrainingtracker/trainingtracker/ui/components/core/BottomSheetDesignTest.kt`
  - `app/src/test/java/com/atrainingtracker/trainingtracker/ui/components/core/BottomSheetVisualContractTest.kt`
* Verify token constants, callback parameter declarations, `onGloballyPositioned` usage, and dynamic peek wiring.
* Execute targeted test runner:
  ```bash
  ./gradlew testDebugUnitTest --tests com.atrainingtracker.trainingtracker.ui.components.core.BottomSheetDesignTest --tests com.atrainingtracker.trainingtracker.ui.components.core.BottomSheetVisualContractTest
  ```

### Step 6: Clean-Room Full Suite Regression Execution
* Execute:
  ```bash
  ./gradlew testDebugUnitTest
  ```
* Verify 0 failures, 0 regressions across all modules.

---

## 6. Pre-Check Command for Stage 4

Before modifying any source code in Stage 4, execute:
```bash
python3 tools/jira_util.py check-gate ATT-2011
```
Code editing is strictly prohibited until this command exits with code 0.
