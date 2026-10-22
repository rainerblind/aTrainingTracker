# Stage 3: Implementation Plan - ATT-3364: Harmonize live segment and live climb bottom sheets under unified sheet layout

**Ticket**: [ATT-3364](https://rainerblind.atlassian.net/browse/ATT-3364)  
**Sub-task**: [ATT-3553](https://rainerblind.atlassian.net/browse/ATT-3553) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-431](https://rainerblind.atlassian.net/browse/ATT-431) (*Clean Code*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-42.3`  
**Requirement Mapping**: `REQ-UI-366`  
**Test Spec Mapping**: `TST-UI-329`  
**Branch**: `feature/ATT-3364`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-14  

---

## 1. Architectural Design & SWE.2 Specification

### 1.1 Architectural Motivation & Single Source of Truth (Rule 36)

During live tracking in the cockpit (`SensorGridScreen.kt`), real-time feedback sheets pop up above the bottom navigation bar for **Strava Live Segments** (`LiveSegment`) and **Live Climbs / ClimbPro** (`LiveClimbData`). However, their implementations have diverged:
1. `LIveSegmentSheet.kt` has a filename casing typo (`LI` instead of `Li`).
2. `SensorGridScreen.kt:284-325` duplicates 40 lines of container scaffolding, contour styling, surface background, and `clickable { expand() }` logic across both entities.
3. In `SensorGridScreen.kt:306-320`, `LiveClimbSheet` omitted `.wrapContentHeight()`, creating an unintentional geometric divergence from `LiveSegmentSheet` (`REQ-UI-270`).

### 1.2 Target Architectural Structure

```
                  ┌──────────────────────────────────────────────┐
                  │          SensorGridScreen.kt                 │
                  │   sheetContent = {                           │
                  │     LiveTrackingSheetContainer(...) {        │
                  │       if (activeSegment != null)             │
                  │          LiveSegmentSheet(...)               │
                  │       else if (activeClimb != null)          │
                  │          LiveClimbSheet(...)                 │
                  │     }                                        │
                  │   }                                          │
                  └──────────────────────┬───────────────────────┘
                                         │ encapsulates
                                         ▼
                  ┌──────────────────────────────────────────────┐
                  │       LiveTrackingSheetContainer.kt          │
                  │  - fillMaxWidth().wrapContentHeight()        │
                  │  - sheetContour()                            │
                  │  - background(surface, SheetShape)           │
                  │  - clickable when isPartiallyExpanded        │
                  └──────────────┬───────────────────────────────┘
                                 │ hosts
                 ┌───────────────┴────────────────┐
                 ▼                                ▼
┌─────────────────────────────────┐ ┌───────────────────────────────────┐
│     LiveSegmentSheet.kt         │ │       LiveClimbSheet.kt           │
│ (Renamed from LIveSegmentSheet) │ │                                   │
│ - MapDetailLayout(showMap=false)│ │ - Column(fillMaxWidth())          │
│ - SegmentHeader                 │ │ - Header Row (32dp icon, title,   │
│ - HorizontalDivider(0.5dp)      │ │   category badge, counter)        │
│ - SegmentLiveDetails            │ │ - HorizontalDivider(0.5dp)        │
│ - ElevationProfile with pin     │ │ - Live HUD & ElevationProfile pin │
└─────────────────────────────────┘ └───────────────────────────────────┘
```

---

## 2. UI Consistency (Rule 23)

* **Closest Existing Reference Screen**:
  - `SensorGridScreen.kt` bottom sheet slot (`sheetContent`) and `EntityDetailSheetScaffold.kt` in `ui/components/core/`.
* **Reused Components (`ui/components/`)**:
  - `BottomSheetDesign` (`SheetShape`, `PeekHeightLiveSegment`, `SheetShadowElevation`, `SheetTonalElevation`).
  - `Modifier.sheetContour()` from `BottomSheetDesign.kt`.
  - `ElevationProfile` from `ui/map/ElevationProfile.kt`.
  - `MetricBadge` from `ui/components/MetricBadge.kt`.
* **Theme Tokens**:
  - **Shapes**: `BottomSheetDesign.SheetShape` (`RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)`).
  - **Container Color**: `MaterialTheme.colorScheme.surface` (100% opaque, consistent across Light, Dark, and AMOLED).
  - **Border / Contour**: `MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)`, `BorderWidth = 1.dp`.
  - **Dividers**: `HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))`.
  - **Spacing Scale**: Canonical spacing scale (`16.dp` horizontal, `4.dp`–`8.dp` vertical padding).
* **Justification for New One-Off Styles**:
  - Zero new one-off styles. 100% adherence to existing Material 3 theme tokens, `BottomSheetDesign`, and established spacing.

---

## 3. Invariants & Safety Measures

1. **Visual Precedence Invariant (`REQ-MAP-027` Section 4)**:
   - When both an active segment and an active climb are present during tracking, `showLiveSegments && activeSegment != null` takes strict visual precedence over `activeLiveClimb`.
2. **Clamped Content Height (`REQ-UI-270`)**:
   - The sheet container must wrap content height (`.wrapContentHeight()`), preventing unwanted expansion to full viewport height when elevation profile is rendered without map.
3. **Dynamic Position Marker Invariant (`REQ-UI-331`, `REQ-UI-340`)**:
   - Live athlete position pin synchronization on the elevation profile (`externalScrubDistance` in `LiveSegmentSheet`, `currentDistance` in `LiveClimbSheet`) must remain completely intact.
4. **Touch & Click-to-Expand Invariant**:
   - Clicking the sheet while in `SheetValue.PartiallyExpanded` must trigger `scaffoldState.bottomSheetState.expand()`, while leaving touches unaffected in `Expanded` state so elevation scrubbing functions smoothly.
5. **Zero Test Regressions**:
   - All existing tests in `LiveSegmentSheetContractTest`, `LiveSegmentSheetLayoutTest`, `LiveClimbSheetLayoutTest`, `MapDetailLayoutNavigationBarsPaddingTest`, and the full unit test suite must pass with 0 failures.

---

## 4. Bite-Sized Atomic Implementation Steps

### Step 1: Normalize Filename (`LIveSegmentSheet.kt` -> `LiveSegmentSheet.kt`)
- Execute `git mv app/src/main/java/com/atrainingtracker/trainingtracker/ui/segments/LIveSegmentSheet.kt app/src/main/java/com/atrainingtracker/trainingtracker/ui/segments/LiveSegmentSheet.kt`.
- Update file references in test fixtures:
  - `app/src/test/java/com/atrainingtracker/trainingtracker/ui/segments/LiveSegmentSheetContractTest.kt`
  - `app/src/test/java/com/atrainingtracker/trainingtracker/ui/segments/LiveSegmentSheetLayoutTest.kt` (update class lookup to `com.atrainingtracker.trainingtracker.ui.segments.LiveSegmentSheetKt`)
  - `app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/MapDetailLayoutNavigationBarsPaddingTest.kt`
- **Verification Command**: `./gradlew testDebugUnitTest --tests "*LiveSegmentSheet*"`

### Step 2: Create Reusable `LiveTrackingSheetContainer.kt`
- Create `app/src/main/java/com/atrainingtracker/trainingtracker/ui/components/core/LiveTrackingSheetContainer.kt`:
  - Declare `@Composable fun LiveTrackingSheetContainer(...)` accepting `isPartiallyExpanded: Boolean`, `onExpand: () -> Unit`, `modifier: Modifier = Modifier`, and `content: @Composable BoxScope.() -> Unit`.
  - Apply `.fillMaxWidth().wrapContentHeight().sheetContour().background(MaterialTheme.colorScheme.surface, shape = BottomSheetDesign.SheetShape)`.
  - Apply `.clickable(onClick = onExpand)` conditionally when `isPartiallyExpanded` is true.
- **Verification Command**: `./gradlew compileDebugKotlin`

### Step 3: Refactor `SensorGridScreen.kt` Sheet Content Slot
- In `app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/SensorGridScreen.kt:283-326`:
  - Consolidate duplicate `Box` blocks into a single invocation of `LiveTrackingSheetContainer`.
  - Maintain the active sheet selection logic:
    ```kotlin
    if (shouldShowBottomSheet && screenMode == ScreenMode.TRACKING) {
        if ((showLiveSegments && activeSegment != null) || (showLiveClimbs && activeLiveClimb != null)) {
            LiveTrackingSheetContainer(
                isPartiallyExpanded = scaffoldState.bottomSheetState.currentValue == SheetValue.PartiallyExpanded,
                onExpand = {
                    coroutineScope.launch {
                        scaffoldState.bottomSheetState.expand()
                    }
                }
            ) {
                if (showLiveSegments && activeSegment != null) {
                    LiveSegmentSheet(liveSegment = activeSegment)
                } else if (activeLiveClimb != null) {
                    LiveClimbSheet(liveClimb = activeLiveClimb!!)
                }
            }
        }
    }
    ```
- **Verification Command**: `./gradlew testDebugUnitTest --tests "*SensorGridScreen*" --tests "*LiveSegmentSheet*"`

### Step 4: Add Unit Tests for `LiveTrackingSheetContainer`
- Create `app/src/test/java/com/atrainingtracker/trainingtracker/ui/components/core/LiveTrackingSheetContainerTest.kt`:
  - Verify container file exists, composable is public, and applies `.wrapContentHeight()`, `.sheetContour()`, and `BottomSheetDesign.SheetShape`.
  - Verify conditional expansion logic contract.
- Run targeted layout and contract tests:
  - `./gradlew testDebugUnitTest --tests "*LiveTrackingSheetContainerTest*" --tests "*LiveSegmentSheet*" --tests "*LiveClimbSheet*"`

### Step 5: Full Clean-Room Regression Suite Execution
- Run full suite: `./gradlew testDebugUnitTest` and assert 100% passing tests with 0 failures.

---

## 5. Gate 3 Readiness Verification
- All functional changes mapped to `REQ-UI-366` and `TST-UI-329`.
- Atomic steps are self-contained and sequentially verifiable.
- Visual tokens, invariants, and Single Source of Truth (Rule 36) strictly enforced.
