# Stage 1 Analysis: ATT-2008 - [Bug] [UI/Sheets] Segment and Route Popups within Map Cannot Be Moved Upward Enough (Elevation Profile Occluded by Navigation Bar)

**Ticket**: [ATT-2008](https://rainerblind.atlassian.net/browse/ATT-2008)  
**Sub-task**: [ATT-2045](https://rainerblind.atlassian.net/browse/ATT-2045) (`[Analysis]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.11`  
**Branch**: `feature/ATT-2008`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-02  

---

## 1. Problem Statement & Motivation

During map inspection of segments and routes (`MapScreenWithTrack.kt`), as well as live segment popups during tracking (`SensorGridScreen.kt` / `LiveSegmentSheet.kt`), the bottom sheet popup cannot be dragged or moved upward sufficiently. Because the upward travel or expanded height constraint does not properly account for Android system navigation bar window insets (`WindowInsets.navigationBars`), the elevation profile at the bottom of the sheet remains positioned behind the Android 3-button navigation bar (or gesture bar), occluding the chart, axis labels, and zoom/pan controls.

### Current Behavior
* When expanding a segment or route popup in `MapScreenWithTrack.kt`, or expanding `LiveSegmentSheet` in `SensorGridScreen.kt`, the elevation profile chart sits at the very bottom edge of the display.
* On devices configured with 3-button navigation (e.g. Google Pixel 10), the system navigation buttons (Back, Home, Recents, ~48dp tall) sit directly on top of the elevation profile, occluding the X-axis distance readouts, zoom buttons, and scrubbing baseline.
* In `LiveSegmentSheet.kt`, because the content height is wrapped without navigation bar padding, `BottomSheetScaffold` caps sheet expansion at the unpadded height (~306dp). The athlete cannot drag the sheet upward any further to clear the navigation bar.

### Expected Behavior
* When expanding a segment, route, or live segment popup, the sheet expands upward sufficiently such that the entire elevation profile and all its interactive controls sit 100% above the system navigation bar.
* The bottom of the sheet content seamlessly incorporates `WindowInsets.navigationBars` padding, ensuring that the sheet's surface background extends under the navigation bar while all interactive and informational content is positioned above it.

---

## 2. Root Cause Analysis (Forensic Investigation)

1. **Missing Navigation Bar Inset in `MapDetailLayout.kt` `lowerColumn`**:
   In `MapDetailLayout.kt`:
   ```kotlin
   val lowerColumn: @Composable (Modifier) -> Unit = { colModifier ->
       Column(modifier = colModifier) {
           if (showElevationProfile) {
               activeScrubPath?.let { path ->
                   Surface(
                       color = MaterialTheme.colorScheme.surface,
                       modifier = Modifier.fillMaxWidth() // <-- MISSING navigationBarsPadding()!
                   ) { ... ElevationProfile(...) ... }
               }
           }

           // 4. ANALYTICS (Slotted - REQ-UI-205 / ATT-1393)
           analyticsContent?.let { content ->
               Surface(
                   color = MaterialTheme.colorScheme.surface,
                   modifier = Modifier.fillMaxWidth().navigationBarsPadding() // <-- Only applied here!
               ) { ... }
           }
       }
   }
   ```
   When `analyticsContent` is present (such as in `TrackOnMapScreen.kt`), it received `Modifier.navigationBarsPadding()` via `ATT-1393`. However, when inspecting Segments (`SegmentOnMapScreen.kt`), Routes (`RouteOnMapScreen.kt`), or Live Segments (`LiveSegmentSheet.kt`), `analyticsContent` is `null`. The terminal composable in `lowerColumn` is the `Surface` hosting `ElevationProfile`. Because this `Surface` has no bottom window inset padding, it is drawn flush with the physical bottom of the display.

2. **Upward Travel Cap in `LiveSegmentSheet` (`SensorGridScreen.kt`)**:
   In `SensorGridScreen.kt`, `BottomSheetScaffold` wraps `LiveSegmentSheet` in a `Box` with no explicit height constraint:
   ```kotlin
   sheetContent = {
       if (showLiveSegments) {
           Box(
               modifier = Modifier
                   .fillMaxWidth()
                   .sheetContour()
                   .background(MaterialTheme.colorScheme.surface, shape = BottomSheetDesign.SheetShape)
           ) {
               LiveSegmentSheet(liveSegment = activeSegment)
           }
       }
   }
   ```
   In `LiveSegmentSheet.kt`, `MapDetailLayout` is called with `showMap = false` and `hasScrollableContent = false`, causing it to apply `Modifier.wrapContentHeight()`. Without `navigationBarsPadding()` on `ElevationProfile`, the total measured height of the sheet is exactly `DragHandle (~16dp) + Header (~140dp) + ElevationProfile (~150dp) = ~306dp`.
   Material 3 `BottomSheetScaffold` constrains the maximum expanded drag offset to the measured height of the sheet content. Consequently, the sheet stops expanding at 306dp from the bottom of the screen. The bottom ~48dp of that height is covered by the 3-button navigation bar, and the athlete cannot drag the sheet upward any further.

3. **Bottom Inset Alignment in `MapScreenWithTrack.kt`**:
   In `MapScreenWithTrack.kt`, the sheet container is sized to `maxSheetHeight = maxHeight - statusBarHeight`. Inside, `SegmentOnMapScreen` and `RouteOnMapScreen` fill max size, with the map taking `weight(1f)` and `ElevationProfile` at the bottom. Because `ElevationProfile` lacks navigation bar padding, it aligns with the absolute bottom edge of the window (`maxHeight`), right behind the navigation bar buttons.

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  * Ensure `MapDetailLayout.kt` applies `Modifier.navigationBarsPadding()` to the `Surface` wrapping `ElevationProfile` (and telemetry graphs) when `analyticsContent == null`.
  * Ensure that when both `showElevationProfile` is false and `analyticsContent == null` in a bottom sheet (`!useStatusBarsPadding`), a defensive `navigationBarsPadding()` spacer is provided.
  * Verify that in `LiveSegmentSheet.kt` (`SensorGridScreen.kt`), the sheet's measured height increases by `navBarHeight`, enabling `BottomSheetScaffold` to expand upward by the exact navigation bar height.
  * Verify that in `MapScreenWithTrack.kt` (`SegmentOnMapScreen` and `RouteOnMapScreen`), the elevation profile sits cleanly above the system navigation bar when expanded.
  * Validate with structural contract tests and clean-room unit tests.

* **Out-of-Scope Non-Goals (Scope Bounding)**:
  * Altering the internal drawing or coordinate math of `ElevationProfile.kt`.
  * Modifying the dynamic peek height baseline calculations established in `ATT-1645`.
  * Altering full-screen map views where `analyticsContent != null`.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit

* **Original Requirement ID & Target**: Net-new requirement `REQ-UI-236` (*UI/Sheets: System Navigation Bar Inset Clearance & Upward Travel Calibration for Segment, Route, and Live Segment Popups*).
* **Related Requirements**:
  * `REQ-UI-047`: *Live Segment Sheet Height Constraint* (limits Live Segment sheet height to header + elevation profile).
  * `REQ-UI-148`: *Edge-to-Edge System Insets & Core UI Components*.
  * `REQ-UI-189`: *UI/Sheets: Standardized Boundaries, Contours, Drop Shadow Elevation & Drag Affordances*.
  * `REQ-UI-196`: *LiveSegment & Bottom Sheets: Refined Subtle Drag Handle, Harmonized Popup Spacing & Unified Surface Background*.
  * `REQ-UI-205`: *Aftermath: Analytics Slotted Content in MapDetailLayout*.
* **Historical Origin & Commit Trace**:
  * In `ATT-1393` (commit `69286d4e`), `analyticsContent` was slotted into `MapDetailLayout` and received `.navigationBarsPadding()`.
  * In `ATT-1645` (commit `f17dc40d`), dynamic peek heights were calibrated with `+ navBarHeight`, ensuring header visibility in peeked state.
  * However, the expanded state when `analyticsContent == null` was never updated with `navigationBarsPadding()`, leaving the elevation profile occluded.
* **Preservation of Core Invariants**:
  * Edge-to-edge drawing under the navigation bar remains preserved via `Surface(color = surface)`.
  * Existing peek baselines (`BottomSheetDesign.PeekHeightSegment`, `PeekHeightRoute`, `PeekHeightLiveSegment`) remain 100% untouched.
  * When `analyticsContent != null`, `analyticsContent` continues to handle `navigationBarsPadding()`, preventing double-padding.

---

## 5. Architectural Strategy & High-Level Solution

### Component: `MapDetailLayout.kt`
In `lowerColumn`:
```kotlin
val lowerColumn: @Composable (Modifier) -> Unit = { colModifier ->
    Column(modifier = colModifier) {
        if (showElevationProfile) {
            activeScrubPath?.let { path ->
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (analyticsContent == null) Modifier.navigationBarsPadding() else Modifier
                        )
                ) {
                    Box(modifier = Modifier.drawWithContent {
                        elevationLayer.record {
                            this@drawWithContent.drawContent()
                        }
                        drawLayer(elevationLayer)
                    }) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            ...
                        }
                    }
                }
            }
        } else if (analyticsContent == null && !useStatusBarsPadding) {
            Spacer(modifier = Modifier.navigationBarsPadding())
        }

        // 4. ANALYTICS (Slotted - REQ-UI-205 / ATT-1393)
        analyticsContent?.let { content ->
            Surface(
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth().navigationBarsPadding()
            ) {
                ...
            }
        }
    }
}
```

### Verification Mechanism:
1. Structural contract test asserting that `MapDetailLayout.kt` applies `navigationBarsPadding()` to the elevation profile `Surface` when `analyticsContent == null`.
2. Clean-room unit regression test suite (`./gradlew testDebugUnitTest`).
3. Verification that `SegmentOnMapScreen`, `RouteOnMapScreen`, and `LiveSegmentSheet` pass tests without regression.

---

## 6. System Invariants & Risk Assessment

* **Core Invariants**:
  1. Zero regression in existing unit tests and UI contracts.
  2. Single-thread SQLite confinement and sensor repository lifecycles remain completely decoupled.
  3. Parent ticket Human Decision Gate remains strictly enforced (Rule 1).
* **Risk Rating**: **LOW**.
  * Modifying `Surface` modifier in `MapDetailLayout` to incorporate conditional `navigationBarsPadding()` is isolated, surgical, and adheres strictly to established Material 3 window inset handling patterns.
