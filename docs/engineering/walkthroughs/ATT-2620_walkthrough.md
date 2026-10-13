# Stage 5 Walkthrough: ATT-2620 - Tracking tab configuration mode lower section not accessible or visible

**Ticket**: [ATT-2620](https://atrainingtracker.atlassian.net/browse/ATT-2620)  
**Sub-task**: [ATT-2719](https://atrainingtracker.atlassian.net/browse/ATT-2719) (`[Test]`)  
**Parent Epic**: [ATT-278](https://atrainingtracker.atlassian.net/browse/ATT-278) (*Cockpit & Live Telemetry Modernization*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `2026-41.3`  
**Branch**: `feature/ATT-2620`  
**Author**: Antigravity  
**Date**: 2026-10-08  

---

## 1. Executive Summary
ATT-2620 resolves a critical UI/UX bug in the tracking tab editor (`ScreenMode.CONFIGURATION`) where the lower section containing the spatial toggles (Map, Elevation Profile, Live Segments, Live Climbs, Lap Button) was occluded, cut off, or inaccessible when tabs had multiple sensor rows.

Under `REQ-UI-295`:
1. **Unified Scrollable Container**: In `ScreenMode.CONFIGURATION`, the entire configuration body is enclosed in a single `verticalScroll(rememberScrollState())` container with `navigationBarsPadding()` and `padding(bottom = 16.dp)`, guaranteeing effortless vertical scrolling to all cards and complete clearance of Android system navigation bars.
2. **Nested Scroll Trap Elimination**: The sensor grid `Column` in configuration mode was stripped of its inner `verticalScroll()`, enabling all sensor rows, `RowAdder` buttons, and `ColAdder` buttons to lay out naturally without measurement conflicts.
3. **Live Overlay Exclusion**: `ATrainingTrackerMap` and `ElevationProfile` are conditionally excluded from `ScreenMode.CONFIGURATION`, preventing `Modifier.weight(1f)` from consuming screen height or intercepting gestures.
4. **Scaffold Isolation**: `BottomSheetScaffold` isolates peek height, swipe gestures, and sheet content to `ScreenMode.TRACKING`, ensuring climb or segment sheets never occlude the configuration canvas.
5. **Zero Regression on Active Tracking**: In `ScreenMode.TRACKING` and `ScreenMode.PREVIEW`, the unscrollable outer column, inner sensor grid scrolling, and expanded weighted live map remain 100% intact.
6. **Clean-Room Verification**: 100% test pass rate across the full regression test suite with zero failures.

---

## 2. Changes Summary

| Component | Target File | Key Changes |
|:---|:---|:---|
| **Editor Architecture** | `SensorGridScreen.kt` | Enclosed `ScreenMode.CONFIGURATION` in unified `verticalScroll(rememberScrollState())` container with `navigationBarsPadding()`; removed nested scroll from sensor grid; gated `BottomSheetScaffold` to `ScreenMode.TRACKING`; excluded `ATrainingTrackerMap` and `ElevationProfile` from `CONFIGURATION` mode. |
| **Contract Tests** | `TrackingTabWysiwygContractTest.kt` | Added tests verifying unified scroll container, exclusion of live map/elevation from configuration, and bottom sheet scaffold mode isolation. |
| **Living Documentation** | `requirements.md`, `tests.md` | Promoted `REQ-UI-295` and `TST-UI-255` to `Verified`. |

---

## 3. Test & Verification Results

### 3.1 Targeted Tests
```bash
./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.tracking.TrackingTabWysiwygContractTest"
```
- Result: **BUILD SUCCESSFUL** in 1m 15s.
- 100% of targeted contract and architecture tests passed.

### 3.2 Full Clean-Room Regression Suite
```bash
./gradlew testDebugUnitTest
```
- Result: **BUILD SUCCESSFUL** in 9m 6s.
- Total tasks: 32 actionable, 12 executed, 20 up-to-date.
- Zero failures, zero regressions across entire project test suite.

---

## 4. UI Consistency & Invariants
- **UI Consistency (Rule 23)**: Retains existing M3 elevated card semantics for `SpatialCockpitToggleCard` and bottom dock container; seamlessly integrates Compose system insets handling via `navigationBarsPadding()`.
- **Zero Nested Scroll Traps**: Compose layout constraints are strictly respected by having only one scrolling axis container in the hierarchy.
- **Tracking Invariant**: In active tracking mode, live map expands with `weight(1f)` and sensor grid scrolls independently.
