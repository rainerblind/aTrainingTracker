# Stage 2: Requirement & Test Specification - ATT-2772: Limit sensor name width and allow line wrapping in ControlTrackingScreen remote device tiles

**Ticket**: [ATT-2772](https://atrainingtracker.atlassian.net/browse/ATT-2772)  
**Sub-task**: [ATT-2798](https://atrainingtracker.atlassian.net/browse/ATT-2798) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-355](https://atrainingtracker.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `Unscheduled` (In-Sprint `2026-41.4`)  
**Active Sprint**: `2026-41.4`  
**Requirement Mapping**: `REQ-UI-304` (*Control Tracking Screen Remote Device Tile Bounded Width and Multiline Name Wrapping*)  
**Test Spec ID**: `TST-UI-264` (*Control Tracking Screen Remote Device Tile Bounded Width, Multiline Wrapping & Centering Contract Verification*)  
**Branch**: `improvement/ATT-2772`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-08  

---

## 1. Requirement Specification (`REQ-UI-304`)

### 1.1 Problem Statement & Rationale
In `ControlTrackingScreen`, when sensors with long broadcast or custom names (e.g. `"Wahoo TICKR X 12345678"`, `"Garmin Varia RTL515 Radar"`) are active, the device name renders on an unconstrained single line within `RemoteDeviceItem`. Because the parent `Column` has no width constraint, the tile expands horizontally up to 240 dp, creating large whitespace gaps on either side of the 48 dp icon and prematurely pushing adjacent sensor tiles off-screen. Bounding the tile width to 72 dp and allowing up to 2 wrapped text lines with centered alignment and ellipsis preserves compact, uniform visual spacing.

### 1.2 Functional & Architectural Requirements
The system SHALL bound the maximum horizontal width of remote device tiles in `RemoteDevices.kt` and enable multi-line text wrapping for sensor names in `ControlTrackingScreen.kt`, eliminating excessive whitespace voids and preventing adjacent sensor tiles from being unnecessarily pushed off-screen (ATT-2772):
1. *Tile Width Bounding (`RemoteDeviceItem`)*:
   - `RemoteDeviceItem` root `Column` SHALL declare a bounded maximum width of $72\text{ dp}$ (`Modifier.widthIn(max = 72.dp)`).
   - The $72\text{ dp}$ constraint SHALL apply consistently across all device types and screen configurations, preserving the $8\text{ dp}$ design grid alignment and $48\text{ dp}$ icon centering.
2. *Sensor Name Multiline Wrapping & Truncation*:
   - The `Text` component rendering `device.name` in `RemoteDeviceItem` SHALL set `maxLines = 2`, enabling sensor names to wrap cleanly onto a second line.
   - The `Text` component SHALL set `overflow = TextOverflow.Ellipsis`, ensuring names exceeding 2 lines are cleanly truncated without expanding tile height or overflowing boundaries.
   - The `Text` component SHALL set `textAlign = TextAlign.Center`, ensuring single-line and double-line sensor labels remain symmetrically centered beneath the $48\text{ dp}$ device icon.
3. *Preservation of System Invariants*:
   - Spacing between adjacent tiles SHALL remain `Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)`.
   - Content padding of `LazyRow` (`PaddingValues(horizontal = 8.dp, vertical = 4.dp)`) and outer screen margin clearance SHALL remain intact.
   - `ResearchButton` and `SearchArea` layout isolation (`REQ-UI-301`) SHALL remain intact.
   - Device click handling (`onClick = { onDeviceClick(device) }`) and stable item keying (`key = { it.id }`) SHALL remain intact.
   - Zero regressions across clean-room test suite (`./gradlew testDebugUnitTest`).

### 1.3 Requirement Archaeology & Chesterton's Fence Audit
1. *Original Requirement ID & Target*: Net-new requirement refining `REQ-UI-301` (*Control Tracking Screen Sensor Header Layout Isolation & Collision Elimination*, clause 3).
2. *Historical Origin & Commit Trace*: Ticket `ATT-2772`, sprint `2026-41.4`, target release `V4.9.39`, Epic `ATT-355` (*Good and consistent UI*).
3. *Root Reason for Existing Formulation*: In `ATT-2479`, header collision between `ResearchButton` and `RemoteDevices` was resolved, but `RemoteDeviceItem` inherited unconstrained single-line text layout from legacy code, causing long sensor names to stretch tiles to 150–240 dp with massive whitespace gaps.
4. *Preservation of Core Invariants*: LazyRow modifier forwarding, 8.dp item spacing, click listeners, and 100% unit test pass rate remain strictly preserved.

### 1.4 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Multiline Wrapping & Bounded Width)**:
  - *Given* one or more connected sensors displayed in `RemoteDevices` with long broadcast names (e.g. "Wahoo TICKR X 12345678"),
  - *When* observing the rendered tiles in `ControlTrackingScreen`,
  - *Then* the sensor name SHALL wrap onto up to 2 lines within a maximum tile width of 72 dp, and names exceeding 2 lines SHALL truncate with ellipsis (`...`).
* **Criterion 2 (Short Name Centering)**:
  - *Given* sensors with short names (e.g. "HRM", "Speed"),
  - *When* observing the rendered tiles,
  - *Then* the names SHALL remain symmetrically centered under the 48 dp icon without layout distortion.
* **Criterion 3 (Grid Spacing & Uniformity)**:
  - *Given* multiple adjacent sensor tiles in `RemoteDevices`,
  - *When* measuring visual layout,
  - *Then* the tiles SHALL maintain uniform 8 dp spacing without excessive whitespace voids.

### 1.5 System Invariants
- Zero regression in existing `ControlTrackingScreen` layout or sensor pairing functionality.
- Touch target minimum: tile remains clickable and accessible.
- Clean-room test suite pass rate: 100%.

---

## 2. Test Specification (`TST-UI-264`)

### Test Case 1: Bounded Maximum Tile Width (`TST-UI-264.1`)
* **Scope**: Contract Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/tracking/controltracking/RemoteDevicesContractTest.kt`
* **Method**: `testRemoteDeviceItemWidthBoundedTo72Dp`
* **Assertion**: Verify that `RemoteDeviceItem` applies `Modifier.widthIn(max = 72.dp)` to its root layout container.

### Test Case 2: Multiline Wrapping and Ellipsis Overflow (`TST-UI-264.2`)
* **Scope**: Contract Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/tracking/controltracking/RemoteDevicesContractTest.kt`
* **Method**: `testRemoteDeviceItemTextWrappingAndEllipsis`
* **Assertion**: Verify that `Text` composable for device name configures `maxLines = 2` and `overflow = TextOverflow.Ellipsis`.

### Test Case 3: Symmetrical Text Centering (`TST-UI-264.3`)
* **Scope**: Contract Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/tracking/controltracking/RemoteDevicesContractTest.kt`
* **Method**: `testRemoteDeviceItemTextCentered`
* **Assertion**: Verify that `Text` composable for device name configures `textAlign = TextAlign.Center`.

### Test Case 4: Full Clean-Room Regression Suite (`TST-UI-264.4`)
* **Command**: `./gradlew testDebugUnitTest`
* **Goal**: Verify 100% pass rate across the full test suite (2,146+ tests).

---

## 3. Traceability Matrix

| Test Case | Scope | Target / Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-UI-264.1` | Contract | `RemoteDevicesContractTest.testRemoteDeviceItemWidthBoundedTo72Dp` | `REQ-UI-304` | Specified |
| `TST-UI-264.2` | Contract | `RemoteDevicesContractTest.testRemoteDeviceItemTextWrappingAndEllipsis` | `REQ-UI-304` | Specified |
| `TST-UI-264.3` | Contract | `RemoteDevicesContractTest.testRemoteDeviceItemTextCentered` | `REQ-UI-304` | Specified |
| `TST-UI-264.4` | Regression | `./gradlew testDebugUnitTest` | `REQ-PRO-001`, `REQ-UI-304` | Specified |
