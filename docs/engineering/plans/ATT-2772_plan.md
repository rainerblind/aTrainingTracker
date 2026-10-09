# Stage 3: Implementation Plan - ATT-2772: Limit sensor name width and allow line wrapping in ControlTrackingScreen remote device tiles

**Ticket**: [ATT-2772](https://atrainingtracker.atlassian.net/browse/ATT-2772)  
**Sub-task**: [ATT-2799](https://atrainingtracker.atlassian.net/browse/ATT-2799) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-355](https://atrainingtracker.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `Unscheduled` (In-Sprint `2026-41.4`)  
**Active Sprint**: `2026-41.4`  
**Requirement Mapping**: `REQ-UI-304` (*Control Tracking Screen Remote Device Tile Bounded Width and Multiline Name Wrapping*)  
**Test Mapping**: `TST-UI-264` (*Control Tracking Screen Remote Device Tile Bounded Width, Multiline Wrapping & Centering Contract Verification*)  
**Branch**: `improvement/ATT-2772`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-08  

---

## 1. Problem Description & Background

In `ControlTrackingScreen`, connected sensors are rendered in a horizontal `LazyRow` via `RemoteDevices.kt`. Currently, `RemoteDeviceItem` has no width constraint on its root `Column`, and the `Text` composable rendering the sensor name does not restrict `maxLines`, specify `overflow`, or specify `textAlign`. When paired with sensors having long broadcast names (e.g. `"Wahoo TICKR X 12345678"`, `"Garmin Varia RTL515 Radar"`), the text renders on a single line stretching the entire tile to 150–240 dp. This creates large empty gaps around the 48 dp icon and prematurely pushes adjacent sensor tiles off-screen.

The objective of this ticket is to:
1. Bound the maximum horizontal width of `RemoteDeviceItem` to 72 dp (`Modifier.widthIn(max = 72.dp)`).
2. Configure the sensor name `Text` composable with `maxLines = 2`, `overflow = TextOverflow.Ellipsis`, and `textAlign = TextAlign.Center`.
3. Preserve existing spacing (`Arrangement.spacedBy(8.dp)`), padding, click listeners, and stable keying.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-UI-304` (*Control Tracking Screen Remote Device Tile Bounded Width and Multiline Name Wrapping*)
* **Test Mapping**: `TST-UI-264` (*Control Tracking Screen Remote Device Tile Bounded Width, Multiline Wrapping & Centering Contract Verification*)

---

## 3. System Invariants & Preserved Behavior

1. **Zero Unintended Regressions**: Existing `ControlTrackingScreen` layout, permission flows, and `RemoteDevices` tests must continue to pass cleanly.
2. **Design Grid Harmony (Rule 23)**: 72 dp tile width aligns with the 8 dp spacing scale ($9 \times 8 = 72\text{ dp}$), preserving uniform $8\text{ dp}$ tile separation and $48\text{ dp}$ icon centering.
3. **Touch Target Accessibility**: Sensor tiles remain fully clickable with touch targets meeting the 48x48 dp minimum.
4. **Subtask Self-Sufficiency**: Subtasks transition directly to `Erledigt` upon passing Gate audit via `freigabe`.
5. **Parent Human Gate Invariance**: Terminal completion of parent ticket `ATT-2772` remains reserved for the human user in `Final Review (Human)`.

---

## 4. Proposed Architectural Changes

### Component 1: `RemoteDevices.kt` (`RemoteDeviceItem`)
* Apply `Modifier.widthIn(max = 72.dp)` to the root `Column` container of `RemoteDeviceItem`.
* Configure `Text(text = device.name, ...)`:
  - `maxLines = 2`: Allows names to wrap onto a second line.
  - `overflow = TextOverflow.Ellipsis`: Truncates excess characters with ellipsis if the name exceeds 2 lines.
  - `textAlign = TextAlign.Center`: Centers the wrapped lines directly beneath the 48 dp icon.

### Component 2: `RemoteDevicesContractTest.kt`
* Add contract assertions verifying that:
  - `RemoteDeviceItem` declares `Modifier.widthIn(max = 72.dp)`.
  - `Text` specifies `maxLines = 2` and `overflow = TextOverflow.Ellipsis`.
  - `Text` specifies `textAlign = TextAlign.Center`.

### UI Consistency (Rule 23 — mandatory if UI is added or changed)
* **Reference screen / component**: Existing `RemoteDeviceItem` in `RemoteDevices.kt` / `ControlTrackingScreen.kt`.
* **Reused components**: Material 3 `Icon`, `Text`, `RoundedCornerShape(4.dp)`, `MaterialTheme.typography.labelMedium`.
* **Theme tokens**:
  - Shapes: `RoundedCornerShape(4.dp)` on icon clipping.
  - Spacing: `4.dp` internal padding, `4.dp` spacer between icon and text, `8.dp` inter-tile spacing (`Arrangement.spacedBy(8.dp)`), `72.dp` max width ($9 \times 8\text{ dp}$).
  - Colors: `MaterialTheme.colorScheme.onSurface`, `Color.Unspecified` for sensor icon drawable.
  - Typography: `MaterialTheme.typography.labelMedium` (12 sp).
* **New one-off styles & justification**: None; adheres strictly to `docs/design_guidelines.md` §5.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Update Contract Tests (`RemoteDevicesContractTest.kt`)
* File: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/tracking/controltracking/RemoteDevicesContractTest.kt`
* Changes:
  - Add `testRemoteDeviceItemWidthBoundedTo72Dp` verifying `widthIn(max = 72.dp)`.
  - Add `testRemoteDeviceItemTextWrappingAndEllipsis` verifying `maxLines = 2` and `overflow = TextOverflow.Ellipsis`.
  - Add `testRemoteDeviceItemTextCentered` verifying `textAlign = TextAlign.Center`.

### Step 2: Update UI Implementation (`RemoteDevices.kt`)
* File: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/controltracking/RemoteDevices.kt`
* Changes:
  - In `RemoteDeviceItem`, add `.widthIn(max = 72.dp)` to `Modifier`.
  - In `Text`, add `textAlign = TextAlign.Center`, `maxLines = 2`, `overflow = TextOverflow.Ellipsis`.
  - Ensure necessary Compose UI imports (`androidx.compose.ui.text.style.TextAlign`, `androidx.compose.ui.text.style.TextOverflow`) are present.

### Step 3: Execute Targeted Unit Tests
* Command:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.tracking.controltracking.RemoteDevicesContractTest" --tests "com.atrainingtracker.trainingtracker.ui.tracking.controltracking.ControlTrackingSensorHeaderContractTest"
  ```
* Target: 100% pass rate.

---

## 6. Verification & Rollback Plan

* **Verification**:
  - Targeted unit tests pass cleanly.
  - Full clean-room test suite `./gradlew testDebugUnitTest` passes 100%.
  - On-device visual check on Pixel 10 ensuring tiles wrap cleanly and look balanced.
* **Rollback**:
  - Clean git branch `improvement/ATT-2772` can be reverted or deleted without affecting `sprint/2026-41.4`.
