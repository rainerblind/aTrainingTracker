# Stage 5: Walkthrough & Verification - ATT-1817: Standardize popup and bottom sheet surface background across LiveSegment, Routes, Segments, and Settings

**Ticket**: [ATT-1817](https://rainerblind.atlassian.net/browse/ATT-1817)  
**Sub-task**: [ATT-1879](https://rainerblind.atlassian.net/browse/ATT-1879) (`[Test]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.7`  
**Requirement Mapping**: `REQ-UI-218` (*UI/Theme: Standardized Surface Background Tokens and Zero Tonal Elevation across Popups, Bottom Sheets, and Dialogs*)  
**Test Mapping**: `TST-UI-172`  
**Branch**: `feature/ATT-1817`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-01  

---

## 1. Executive Summary & Verification Overview

Ticket ATT-1817 standardized the surface container colors and tonal elevation tokens across all bottom sheet scaffolds, modal bottom sheets, settings sheets, live segment popups, and confirmation dialogs in `aTrainingTracker`.

Forensic investigation and pixel sampling from device screenshots previously identified an artificial grey overlay (`#EDEDED`, RGB 237, 237, 237) and visual background contrast splits:
1. **The Grey `#EDEDED` Mystery**: In Material 3, tonal elevation applies an alpha tint overlay to white surfaces. For 2dp elevation, the formula yields a $6.94\%$ dark overlay: $255 \times (1 - 0.0694) = 237.29 \rightarrow 237$ (`#EDEDED`).
2. **LiveSegment Contrast Split (`SensorGridScreen` / `LiveSegmentSheet`)**: The header card applied `Modifier.background(MaterialTheme.colorScheme.surface)` (pure white #FFFFFF), while the lower elevation container was inside `Surface(color = surface)` which inherited the parent scaffold's `LocalAbsoluteTonalElevation = 2.dp`, tinting it to `#EDEDED`. This created a visible horizontal contrast split.
3. **Scaffold Container Defaults**: `MapScreenWithTrack.kt`, `PeriodMapScreen.kt`, and `WorkoutClusterHeatmapScreen.kt` omitted `sheetContainerColor`, defaulting to `surfaceContainerLow` with 2dp tonal elevation.
4. **Modal Sheets & Settings**: `AppModalBottomSheet.kt` and `FilterBottomSheetScaffold.kt` omitted `containerColor`, defaulting to `surfaceContainerLow`.
5. **Confirmation Dialogs**: `AlertDialog` instances in `StravaSettingsDialog.kt`, `WorkoutClusterHeatmapScreen.kt`, `WorkoutClustersScreen.kt`, `DevicesTabbedScreen.kt`, and `ImportBackupTabsScreen.kt` omitted `containerColor` and `tonalElevation`, defaulting to `surfaceContainerHigh` with 6dp tonal elevation.

With this implementation:
- **Centralized Design Token Refinement (`BottomSheetDesign.kt`)**: Set `SheetTonalElevation = 0.dp` (reduced from `2.dp`). All physical boundary tokens (`SheetCornerRadius = 20.dp`, `SheetShape`, `SheetShadowElevation = 8.dp`, `BorderWidth = 1.dp`, `DragHandleWidth = 32.dp`, `DragHandleHeight = 3.dp`) remain 100% preserved.
- **Modal Sheets & Settings Dialogs**: `AppModalBottomSheet.kt` and `FilterBottomSheetScaffold.kt` explicitly declare `containerColor = MaterialTheme.colorScheme.surface` and `tonalElevation = BottomSheetDesign.SheetTonalElevation` (`0.dp`).
- **Persistent Scaffolds**: `MapScreenWithTrack.kt`, `PeriodMapScreen.kt`, `WorkoutClusterHeatmapScreen.kt`, and `SensorGridScreen.kt` uniformly specify `sheetContainerColor = MaterialTheme.colorScheme.surface`, `sheetTonalElevation = BottomSheetDesign.SheetTonalElevation` (`0.dp`), `sheetShape = BottomSheetDesign.SheetShape`, and `sheetShadowElevation = BottomSheetDesign.SheetShadowElevation`.
- **LiveSegment Seamlessness**: The horizontal contrast split in `LiveSegmentSheet` is completely eliminated; both header and elevation containers seamlessly share pure `MaterialTheme.colorScheme.surface` with zero tonal elevation.
- **Confirmation Dialogs**: Standardized `containerColor = MaterialTheme.colorScheme.surface` and `tonalElevation = 0.dp` across all `AlertDialog` instances, matching `DeleteConfirmationDialog.kt`.

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Method | Result | Status in Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-UI-218` (item 1) | `[TST-UI-172.1]` | Unit Test (`BottomSheetDesignTest.testBottomSheetDesign_tokenConstants`) | **PASSED** | `Verified` |
| `REQ-UI-218` (item 2) | `[TST-UI-172.2]` | Contract Test (`BottomSheetVisualContractTest.testAppModalBottomSheet_consumesBottomSheetDesignTokensAndBorder`, `testFilterBottomSheetScaffold_consumesBottomSheetDesignTokens`) | **PASSED** | `Verified` |
| `REQ-UI-218` (item 3) | `[TST-UI-172.3]` | Contract Test (`BottomSheetVisualContractTest.testPersistentMapScaffolds_consumeBottomSheetDesignTokens`, `testSensorGridScreen_consumesBottomSheetDesignTokens`) | **PASSED** | `Verified` |
| `REQ-UI-218` (item 4) | `[TST-UI-172.4]` | Contract Test (`BottomSheetVisualContractTest.testMapDetailLayout_appliesSheetShapeAndSurfaceBackground`) | **PASSED** | `Verified` |
| `REQ-UI-218` (item 5) | `[TST-UI-172.5]` | Contract Test (`BottomSheetVisualContractTest.testConfirmationDialogs_consumeSurfaceAndZeroTonalElevation`) | **PASSED** | `Verified` |
| `REQ-PRO-001` | `[TST-UI-172.6]` | Clean-Room Full Suite Regression (`./gradlew testDebugUnitTest`) | **PASSED** (100%) | `Verified` |

---

## 3. Automated Test Evidence

### Targeted BottomSheet Unit & Contract Tests
```text
./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.components.core.BottomSheet*"
BUILD SUCCESSFUL in 22s
32 actionable tasks: 1 executed, 31 up-to-date
```
- `BottomSheetDesignTest.testBottomSheetDesign_tokenConstants`: PASSED
- `BottomSheetDesignTest.testBottomSheetDesign_sheetShapeCurvature`: PASSED
- `BottomSheetDesignTest.testBottomSheetDesign_sheetContourModifier`: PASSED
- `BottomSheetVisualContractTest.testSensorGridScreen_consumesBottomSheetDesignTokens`: PASSED
- `BottomSheetVisualContractTest.testMapDetailLayout_appliesSheetShapeAndSurfaceBackground`: PASSED
- `BottomSheetVisualContractTest.testFilterBottomSheetScaffold_consumesBottomSheetDesignTokens`: PASSED
- `BottomSheetVisualContractTest.testAppModalBottomSheet_consumesBottomSheetDesignTokensAndBorder`: PASSED
- `BottomSheetVisualContractTest.testPersistentMapScaffolds_consumeBottomSheetDesignTokens`: PASSED
- `BottomSheetVisualContractTest.testConfirmationDialogs_consumeSurfaceAndZeroTonalElevation`: PASSED

### Clean-Room Full Suite Regression (`./gradlew testDebugUnitTest`)
```text
./gradlew testDebugUnitTest
BUILD SUCCESSFUL in 3m 40s
32 actionable tasks: 1 executed, 31 up-to-date
```
- Total test suite: 100% pass rate, 0 failures, 0 regressions across all modules.

---

## 4. Hardware / Physical Verification (Pixel 10)

* **LiveSegment Popup Surface Seamlessness**:
  1. Launch active workout tracking with a simulated or live route segment.
  2. Expand the LiveSegment popup bottom sheet in the cockpit HUD.
  3. Verify that the boundary between the segment header and the lower elevation profile renders seamlessly with identical pure surface fill (`#FFFFFF` in light mode), completely free of the previous horizontal grey `#EDEDED` split.
* **Map Screen Bottom Sheets (Routes, Segments, Locations)**:
  1. Open `MapScreenWithTrack` and select a route, segment, or location.
  2. Verify that the persistent bottom sheet scaffold renders with clean surface background without grey tinting.
  3. Verify that drop shadow (`8.dp`), top curvature (`20.dp`), and boundary contour line remain crisp.
* **Modal Bottom Sheets & Filter Dialogs**:
  1. Open any filter bottom sheet (`FilterBottomSheetScaffold`) or settings sheet (`AppModalBottomSheet`).
  2. Verify that the modal container background uses clean `MaterialTheme.colorScheme.surface` with zero grey overlay.
* **Confirmation Dialogs**:
  1. Open Strava Settings and tap Disconnect, or trigger a cluster delete prompt in Heatmap/Clusters.
  2. Verify that the confirmation `AlertDialog` renders with unified surface container background and zero tonal elevation.

---

## 5. Invariant & Governance Verification

1. **Physical Boundary Delineations Preserved**:
   - `SheetCornerRadius = 20.dp` (`SheetShape`).
   - Boundary outline stroke: 1.dp `outlineVariant` at 60% opacity (`sheetContour`).
   - Drop shadow elevation: 8.dp (`SheetShadowElevation`).
   - Drag handle pill: 32dp x 3dp with subtle `outlineVariant` color.
2. **System Bars Insets Preserved**: Edge-to-edge system insets (`navigationBarsPadding()`, `imePadding()`, `statusBarsPadding()`) per `REQ-UI-148` remain untouched.
3. **Clean-Room Test Integrity**: 100% test pass rate across the full application test suite with 0 regressions.
4. **Living Docs Traceability**: Both `REQ-UI-218` and `TST-UI-172` are synchronized and marked `Verified`.
5. **Human Decision Gate**: Parent ticket `ATT-1817` transitions to `Final Review (Human)` assigned to `rainer`.
