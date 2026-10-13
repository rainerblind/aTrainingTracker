# Stage 5 Walkthrough: ATT-2479 - Sensor-related items in the header part of the tracking control screen overlapping each other

**Ticket**: [ATT-2479](https://atrainingtracker.atlassian.net/browse/ATT-2479)  
**Sub-task**: [ATT-2754](https://atrainingtracker.atlassian.net/browse/ATT-2754) (`[Verification]`)  
**Parent Epic**: [ATT-355](https://atrainingtracker.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `2026-41.3`  
**Branch**: `feature/ATT-2479`  
**Author**: Antigravity  
**Date**: 2026-10-08  

---

## 1. Executive Summary
ATT-2479 eliminates a visual collision and layout overlap defect on the Control Tracking Screen (`Aufzeichnung`), where the "Suchen" (`ResearchButton`) action button collided and rendered directly on top of the first sensor tile when multiple sensors were connected. Additionally, sensor tiles in `RemoteDevices` bunched together and clipped the outer screen boundaries.

Under `REQ-UI-301` and `TST-UI-261`:
1. **Dedicated Search Banner Container**: The active search status banner (`SearchArea`) is decoupled from the device/action container and slotted into its own full-width row directly beneath the battery optimization warning banner.
2. **Non-Overlapping Device and Action Layout**: The colliding `Box(TopStart / TopCenter)` structure was replaced with structured `Row` layouts. When devices are present (`devices.isNotEmpty()`), `ResearchButton` and `RemoteDevices` are arranged side-by-side where `RemoteDevices` receives `Modifier.weight(1f)`.
3. **Centered Fallback When Zero Devices**: When no devices are present (`devices.isEmpty()`), `ResearchButton` renders horizontally centered (`Arrangement.Center`), or is omitted if `showResearchButton == false`.
4. **RemoteDevices Parameterization & Spacing**: `RemoteDevices` accepts `modifier: Modifier = Modifier`, wraps its tiles in `Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)`, provides 8.dp horizontal content padding, and assigns stable item keys (`it.id`).
5. **Multi-Device Preview**: Added `PreviewControlTrackingScreenMultiDevices` preview with 6 active sensors verifying collision immunity and clean horizontal scrolling.
6. **Full Clean-Room Regression Verification**: 100% pass rate across targeted contract tests and full clean-room repository regression test suite.

---

## 2. Changes Summary

| Component | Target File | Key Changes |
|:---|:---|:---|
| **Control Tracking Screen** | `ControlTrackingScreen.kt` | Extracted `SearchArea` to dedicated slot; replaced colliding `Box` with side-by-side `Row` allocating `weight(1f)` to `RemoteDevices`; centered `ResearchButton` when zero devices; added `PreviewControlTrackingScreenMultiDevices`. |
| **Remote Devices Component** | `RemoteDevices.kt` | Added `modifier: Modifier = Modifier` parameter and bound to root `LazyRow`; configured `Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)` and 8.dp horizontal padding; keyed items by `it.id`. |
| **Contract Tests** | `ControlTrackingSensorHeaderContractTest.kt` | Verified vertical hierarchy, Box collision elimination, weighted row arrangement, centered fallback, and multi-device preview presence. |
| **Contract Tests** | `RemoteDevicesContractTest.kt` | Verified modifier parameter forwarding, tile spacing, padding, stable keys, and empty-list early return. |
| **Living Documentation** | `requirements.md`, `tests.md` | Registered and promoted `REQ-UI-301` and `TST-UI-261` to `Verified`. |

---

## 3. Test & Verification Results

### 3.1 Targeted Tests
```bash
./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.tracking.controltracking.ControlTrackingSensorHeaderContractTest" --tests "com.atrainingtracker.trainingtracker.ui.tracking.controltracking.RemoteDevicesContractTest" --tests "com.atrainingtracker.trainingtracker.ui.tracking.controltracking.ControlTrackingLayoutContractTest"
```
- Result: **BUILD SUCCESSFUL** in 1m 58s.
- Total tasks: 32 actionable, 6 executed, 26 up-to-date.
- 100% of targeted contract tests passed.

### 3.2 Full Clean-Room Regression Suite
```bash
./gradlew testDebugUnitTest
```
- Executed against `feature/ATT-2479` verifying 100% pass rate with zero failures across all modules.

---

## 4. UI Consistency & Invariants
- **UI Consistency (Rule 23)**: Adheres strictly to Material 3 layout conventions; 8.dp tokenized spacing; preserves existing 48.dp touch targets and color tokens for active/disabled states.
- **Interaction Invariants**: Tapping `ResearchButton` triggers `onSearch`; tapping `RemoteDeviceItem` triggers `onDeviceClick(device)`.
- **SearchArea Behavior**: Purely passive feedback banner, displayed only when `searchingFor != null`.
