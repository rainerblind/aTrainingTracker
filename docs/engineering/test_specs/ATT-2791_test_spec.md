# Stage 2: Requirement & Test Specification - ATT-2791: Center sensor device tiles on ControlTrackingScreen with balanced header layout

**Ticket**: [ATT-2791](https://atrainingtracker.atlassian.net/browse/ATT-2791)  
**Sub-task**: [ATT-2881](https://atrainingtracker.atlassian.net/browse/ATT-2881) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-355](https://atrainingtracker.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `2026-41.5`  
**Requirement Mapping**: `REQ-UI-313` (*Control Tracking Screen Symmetrical Balanced Sensor Header Centering and Touch-Safe Accessibility Isolation*, refining `REQ-UI-301`)  
**Test Spec Mapping**: `TST-UI-273` (*Control Tracking Screen Sensor Header Centering & Accessibility Parity Verification*)  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-09  

---

## 1. Formal Requirement Specification (`REQ-UI-313`)

### REQ-UI-313: Control Tracking Screen Symmetrical Balanced Sensor Header Centering and Touch-Safe Accessibility Isolation

The system SHALL guarantee true horizontal screen centering for connected sensor device tiles (`RemoteDevices`) in `ControlTrackingScreen.kt` when the re-search action button (`ResearchButton`) is visible, eliminating rightward layout drift for single and dual sensor configurations, preserving zero visual collision for multi-device setups, and enforcing strict accessibility semantics hygiene (ATT-2791, refining `REQ-UI-301`):

1. **Symmetrical Balancing Container Architecture (`ControlTrackingScreen.kt`)**:
   - When `devices.isNotEmpty()` and `showResearchButton == true`:
     - The sensor header `Row` SHALL be composed of three horizontal components:
       a. *Leading Slot*: Active `ResearchButton(isEnabled = searchingFor == null, onClick = onSearch)`.
       b. *Center Slot*: `RemoteDevices(devices = devices, onDeviceClick = onDeviceClick, modifier = Modifier.weight(1f))`.
       c. *Trailing Slot*: Symmetrical balancing placeholder anchor matching the layout footprint of `ResearchButton`.
   - When `devices.isNotEmpty()` and `showResearchButton == false`:
     - `RemoteDevices` SHALL occupy the full width (`Modifier.fillMaxWidth()`) without trailing balancing anchors.
   - When `devices.isEmpty()` and `showResearchButton == true`:
     - `ResearchButton` SHALL remain centered horizontally (`Arrangement.Center`).

2. **Touch-Safe Accessibility & Semantics Hygiene**:
   - The trailing balancing anchor SHALL be wrapped in a container that strictly applies:
     - `Modifier.clearAndSetSemantics { }` to completely remove the placeholder node from the Compose accessibility semantic tree.
     - `Modifier.alpha(0f)` to render the placeholder completely transparent.
   - The trailing placeholder SHALL NOT declare interactive click handlers or focusable properties (`onClick = {}`, `isEnabled = false`), guaranteeing that screen readers (TalkBack) and directional hardware navigation completely ignore the placeholder and never introduce phantom touch traps.

3. **Multi-Locale Footprint Equivalence**:
   - The trailing placeholder SHALL host an inactive `ResearchButton` instance to guarantee sub-pixel layout width equivalence with the leading button across all 9 supported locales (EN, DE, ES, FR, IT, JA, NL, PL, PT) and all user display/font scale configurations without hardcoded pixel guesswork.

4. **Multi-Device Non-Collision & Bounded Scrolling Invariant**:
   - For 1 or 2 sensors, the sensor cluster SHALL be centered mathematically along the true horizontal screen axis.
   - For 3+ sensors, `RemoteDevices`'s inner `LazyRow` SHALL scroll smoothly within the allocated `Modifier.weight(1f)` bounds without clipping outer screen margins or drawing beneath `ResearchButton`.

5. **Preservation of System Invariants**:
   - `SearchArea` placement above the sensor controls (`REQ-UI-301` Clause 1) MUST remain intact.
   - Bounded 72 dp sensor tile width and 2-line name wrapping (`REQ-UI-304`) MUST remain intact.
   - Zero regressions across clean-room test suite (`./gradlew testDebugUnitTest`).

---

## 2. Acceptance Criteria (Given-When-Then)

* **Scenario 1: Single connected sensor centering**
  - **Given** an athlete with a single connected sensor (e.g. "cad") and `showResearchButton == true`,
  - **When** observing `ControlTrackingScreen`,
  - **Then** the sensor tile is centered along the true horizontal midpoint of the device screen,
  - **And** the leading `ResearchButton` and trailing balancing anchor have equal width.

* **Scenario 2: Accessibility tree hygiene**
  - **Given** `ControlTrackingScreen` rendered with a trailing balancing anchor,
  - **When** TalkBack accessibility traversal inspects the sensor header,
  - **Then** the trailing anchor is completely excluded from the accessibility tree via `clearAndSetSemantics`,
  - **And** only the active leading `ResearchButton` and connected sensor tiles are focusable.

* **Scenario 3: Multi-device scrolling non-collision**
  - **Given** an athlete with 6 connected sensors and `showResearchButton == true`,
  - **When** scrolling `RemoteDevices` inside `ControlTrackingScreen`,
  - **Then** sensor tiles scroll smoothly within `weight(1f)` without overlapping the leading `ResearchButton`.

* **Scenario 4: Zero connected sensors**
  - **Given** zero connected sensors (`devices.isEmpty()`) and `showResearchButton == true`,
  - **When** observing `ControlTrackingScreen`,
  - **Then** `ResearchButton` is centered horizontally on the screen.

---

## 3. Test Specification (`TST-UI-273`)

### Test Suite: `ControlTrackingSensorHeaderContractTest.kt`

1. **`testSymmetricalBalancingAnchorExistsWhenDevicesPresent`**:
   - Verify `ControlTrackingScreen.kt` declares a trailing balancing anchor when `devices.isNotEmpty()` and `showResearchButton`.
   - Verify trailing anchor declares `clearAndSetSemantics`.
   - Verify trailing anchor uses `alpha(0f)`.

2. **`testRemoteDevicesAllocatedWeightBetweenBalancedAnchors`**:
   - Verify `RemoteDevices` receives `modifier = Modifier.weight(1f)` between leading and trailing components.

3. **`testSearchAreaPrecedesSensors`**:
   - Verify `SearchArea` remains in its dedicated slot above sensor rows.

4. **`testZeroDevicesCenteredResearchButton`**:
   - Verify `ResearchButton` centers horizontally when `devices.isEmpty()`.

5. **`testMultiDevicePreviewsExist`**:
   - Verify multi-device Compose preview composable compiles and covers 6 devices.

6. **Clean-Room Regression Suite**:
   - Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with 0 regressions.
