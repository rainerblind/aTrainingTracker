# Stage 1 Analysis: ATT-2772 - Limit sensor name width and allow line wrapping in ControlTrackingScreen remote device tiles

**Ticket**: [ATT-2772](https://atrainingtracker.atlassian.net/browse/ATT-2772)  
**Sub-task**: [ATT-2797](https://atrainingtracker.atlassian.net/browse/ATT-2797) (`[Analysis]`)  
**Parent Epic**: [ATT-355](https://atrainingtracker.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `Unscheduled` (In-Sprint `2026-41.4`)  
**Active Sprint**: `2026-41.4`  
**Branch**: `improvement/ATT-2772`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-08  

---

## 1. Problem Statement & Motivation

During the Sprint 2026-41.3 review on physical hardware (Pixel 10), human testing of the restructured sensor header (introduced under ATT-2479 / `REQ-UI-301`) revealed an ergonomic and visual layout flaw in `RemoteDevices.kt`:
* When sensors with long broadcast or custom names are paired and active (e.g., `"Wahoo TICKR X 12345678"`, `"Garmin Varia RTL515 Radar"`, `"Stages Power Meter L"`), the sensor name text in `RemoteDeviceItem` renders on a single unconstrained line.
* Because the parent `Column` in `RemoteDeviceItem` has no explicit width constraint (`Modifier.width` or `Modifier.widthIn`), the entire tile expands to the full measured width of the single-line string (often 150 dp to 240 dp).
* Since the device type icon remains fixed at 48x48 dp with 4 dp internal padding, centering a 48 dp icon within a 200+ dp column creates vast whitespace voids (75–100 dp of empty space on either side of the icon).
* This artificial tile expansion rapidly exhausts the horizontal viewport width of the `LazyRow` container, pushing adjacent sensor tiles off-screen and forcing unnecessary horizontal scrolling even when only 2 or 3 sensors are connected.

---

## 2. Root Cause Analysis (Forensic Investigation)

### Technical Analysis of `RemoteDevices.kt`
Inspection of `com.atrainingtracker.trainingtracker.ui.tracking.controltracking.RemoteDevices.kt` reveals the exact mechanism:
```kotlin
@Composable
private fun RemoteDeviceItem(
    device: RemoteDeviceUIData,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(4.dp)
    ) {
        Icon(
            painter = painterResource(id = device.iconRes),
            contentDescription = null,
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(4.dp)),
            tint = Color.Unspecified
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = device.name,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        // TODO: Add Battery State :)
    }
}
```
1. **Unbounded Parent Column Measurement**:
   In Compose, `LazyRow` allows its children to measure with infinite horizontal width constraint (`Constraints.Infinity`). Without an explicit width bound on `Column`, the column's measured width is determined by the maximum intrinsic width of its children: $\max(48\text{ dp} + 8\text{ dp}, \text{width}(\text{Text}))$.
2. **Unconstrained Text Properties**:
   `Text` does not specify `maxLines` (defaults to unbounded `Int.MAX_VALUE`), does not specify `overflow` (defaults to `Clip`), does not specify `textAlign` (defaults to `TextAlign.Start`), and does not constrain its width. Consequently, text layout places all characters on line 1, expanding `Text` to the string's full width.
3. **Severe Visual Asymmetry**:
   When multiple sensors have disparate name lengths (e.g. `"HRM"` [~30 dp] alongside `"Garmin Varia RTL515 Radar"` [~210 dp]), adjacent tiles exhibit wildly disparate widths (56 dp vs 218 dp), destroying grid rhythm and making `Arrangement.spacedBy(8.dp)` ineffective at establishing uniform tile density.

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  1. Constrain `RemoteDeviceItem` width to a well-proportioned, design-consistent maximum width (`Modifier.widthIn(max = 72.dp)` or `Modifier.width(72.dp)`).
  2. Enable clean multiline text wrapping in `RemoteDeviceItem` text by setting `maxLines = 2`, `overflow = TextOverflow.Ellipsis`, and `textAlign = TextAlign.Center`.
  3. Ensure sensor tiles remain compact, well-proportioned, and visually cohesive, preserving `Arrangement.spacedBy(8.dp)` between tiles.
  4. Ensure short names (e.g. `"HRM"`, `"Speed"`) remain centered under the icon without layout distortion.
  5. Add automated contract and regression tests in `RemoteDevicesContractTest.kt` verifying bounded width, maxLines, ellipsis overflow, and centered text alignment.
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  1. No changes to `ResearchButton` or `SearchArea` layout (governed by REQ-UI-301 / ATT-2479).
  2. No changes to BLE / ANT+ connection lifecycles or background scanning workers.
  3. No changes to battery state indicator rendering (flagged with existing TODO comment).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit

* **Original Requirement ID & Target**: Net-new requirement `REQ-UI-304` (*Control Tracking Screen Remote Device Tile Bounded Width and Multiline Name Wrapping*), refining `REQ-UI-301` (*Control Tracking Screen Sensor Header Layout Isolation & Collision Elimination* clause 3).
* **Historical Origin & Commit Trace**:
  - `REQ-UI-301` introduced in commit `7bf2cfc3` (Sprint 2026-41.3, ticket ATT-2479).
  - Target release: `V4.9.39`, Epic `ATT-355` (*Good and consistent UI*).
* **Root Reason for Existing Formulation**:
  `ATT-2479` resolved the macroscopic layout collision between `ResearchButton`, `SearchArea`, and `RemoteDevices` by arranging them into a non-overlapping horizontal Row. The internal item layout of `RemoteDeviceItem` was preserved as-is from legacy code, which assumed short test device names (e.g. `"HRM-123"`, `"Speed-X"` in previews).
* **Preservation of Core Invariants**:
  - `RemoteDevices` signature (`modifier: Modifier = Modifier`, default empty guard, stable keying `{ it.id }`) remains 100% intact.
  - Spacing (`Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)`) and padding (`horizontal = 8.dp, vertical = 4.dp`) remain intact.
  - Icon dimensions (`48.dp`, rounded corner `4.dp`), icon tint (`Color.Unspecified`), and click listener propagation remain intact.
  - Zero regression across clean-room test suite (`./gradlew testDebugUnitTest`).

---

## 5. Architectural Strategy & High-Level Solution

### Component Modification: `RemoteDevices.kt`
Update `RemoteDeviceItem` to:
1. Apply `Modifier.widthIn(max = 72.dp)` (or fixed `Modifier.width(72.dp)`) to the root `Column`:
   - $72\text{ dp}$ is a natural multiple of the 8 dp design grid ($9 \times 8 = 72\text{ dp}$).
   - With 4 dp horizontal padding on each side ($8\text{ dp}$ total), available text width is $64\text{ dp}$, which comfortably accommodates 8–10 characters per line at `labelMedium` (12 sp).
2. Configure `Text`:
   - `textAlign = TextAlign.Center`: Ensures wrapped 2-line text and single-line text are centered symmetrically below the 48 dp icon.
   - `maxLines = 2`: Allows names like `"Polar H10"` or `"Wahoo TICKR"` to wrap onto a second line cleanly.
   - `overflow = TextOverflow.Ellipsis`: Gracefully truncates excessively long names exceeding 2 lines with `"..."` rather than blowing out vertical height.

---

## 6. System Invariants & Risk Assessment

* **Core Invariants**:
  1. Zero regression in existing features and unit tests (`RemoteDevicesContractTest.kt`, `ControlTrackingSensorHeaderContractTest.kt`).
  2. Touch target accessibility: Tile clickable area remains at least 48x48 dp.
  3. Parent ticket Human Decision Gate remains strictly enforced (`Final Review (Human)`).
* **Risk Rating**: **LOW**
  - Self-contained UI layout refinement localized entirely within `RemoteDeviceItem` composable in `RemoteDevices.kt`.
  - Zero database, state machine, sensor, or network dependencies.
