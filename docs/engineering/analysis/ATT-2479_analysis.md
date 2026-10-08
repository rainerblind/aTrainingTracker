# Stage 1 Analysis: ATT-2479 - Sensor overlapping when there are too many sensors active

**Ticket**: [ATT-2479](https://atrainingtracker.atlassian.net/browse/ATT-2479)  
**Sub-task**: [ATT-2750](https://atrainingtracker.atlassian.net/browse/ATT-2750) (`[Analysis]`)  
**Parent Epic**: [ATT-355](https://atrainingtracker.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-41.3`  
**Branch**: `feature/ATT-2479`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-08  

---

## 1. Problem Statement & Motivation

On the Control Tracking screen (`Aufzeichnung`, `ControlTrackingScreen.kt`), when multiple sensors (e.g. 5–6 sensors such as Magene HR, speed, cadence) are active or connected, the "Suchen" (`ResearchButton`) action button collides directly with and renders on top of the first sensor icon. Furthermore, the search progress area (`SearchArea`) partially overlaps with the research button icon.

### Visual Evidence (from Jira Attachment 10957 / `Screenshot_20261005-211137.png`)
* Active search text: `"Suche nach Garmin HR new..."` with a circular spinner is centered at the top.
* Directly underneath, a row of 6 sensor tiles (`Magene HR`, `spd`, `cad`, `cad`, `Magene HR`, `spd`) spans the screen width.
* The "Suchen" refresh icon and text are rendered directly over the top-left of the first sensor tile (`Magene HR`), creating visual corruption and overlapping touch targets.

---

## 2. Root Cause Analysis (Forensic Investigation)

Inspection of `ControlTrackingScreen.kt` (lines 287–316) reveals the architectural flaw:

```kotlin
Box(modifier = Modifier
    .fillMaxWidth()
    .padding(bottom = 8.dp)
) {
    // The Information Area - Anchored to the MATHEMATICAL CENTER of the screen
    Column(
        modifier = Modifier.align(Alignment.TopCenter),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        SearchArea(
            searchingFor = searchingFor
        )

        RemoteDevices(
            devices = devices,
            onDeviceClick = onDeviceClick
        )
    }

    // Research Button - Anchored to the far left of the screen
    // note that this must be added at the end to get the clicking working...
    if (showResearchButton) {
        Box(modifier = Modifier.align(Alignment.TopStart)) {
            ResearchButton(
                isEnabled = searchingFor == null,
                onClick = onSearch
            )
        }
    }
}
```

### Mechanism of Failure
1. **Z-Order Stacking in `Box`**:
   The container is a single `Box` with two children:
   - Child 1: `Column(modifier = Modifier.align(Alignment.TopCenter))` containing `SearchArea` and `RemoteDevices`.
   - Child 2: `Box(modifier = Modifier.align(Alignment.TopStart))` containing `ResearchButton`.
2. **Horizontal Collision**:
   When only 1 or 2 sensors are paired, `RemoteDevices` is narrow (~120–160 dp) and sits in the center of the screen, leaving the top-left area (x = 0..64 dp) unoccupied. But when 5 or 6 sensors are paired, `RemoteDevices` spans the full display width (~360–400 dp). The leftmost sensor is positioned at x ≈ 4 dp, directly beneath `Alignment.TopStart`.
3. **Vertical Collision with `SearchArea`**:
   When `searchingFor == null`, `SearchArea` occupies only ~8 dp. Both `ResearchButton` and `RemoteDevices` start near y = 0. But when `searchingFor != null`, `SearchArea` expands vertically to ~36 dp, pushing `RemoteDevices` down. However, `ResearchButton` remains anchored at `TopStart` (y = 0), spanning y = 0..84 dp. As a result, `ResearchButton` vertically intersects both `SearchArea` and the upper portion of `RemoteDevices`.

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  1. Restructure the header layout in `ControlTrackingScreen.kt` to eliminate overlapping `Box` z-stacking.
  2. Isolate `SearchArea` in a dedicated vertical row above sensor/action controls.
  3. Place `ResearchButton` and `RemoteDevices` in a non-overlapping horizontal layout hierarchy (`Row`), ensuring `ResearchButton` has a dedicated slot and `RemoteDevices` occupies the remaining width with horizontal scrolling when sensor count exceeds available space.
  4. Ensure `RemoteDevices` accepts an optional `Modifier` parameter for flexible layout composition.
  5. Center `ResearchButton` cleanly when no remote devices exist (`devices.isEmpty()`), avoiding awkward corner pinning.
  6. Maintain 100% 9-language localization parity and unit test suite pass rate.

* **Out-of-Scope Non-Goals (Scope Bounding)**:
  1. Modifying sensor connection logic, Bluetooth LE / ANT+ scanning protocols, or service lifecycles (`BANALService`).
  2. Changing the sensor detail dialog (`EditDeviceDialog`, `SensorSourceDialog`).
  3. Modifying the bottom route selection card (`bottomContent`) or primary action buttons (`ControlTrackingButton`, `SportTypeSelector`).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit

* **Original Requirement ID & Target**: `REQ-UI-259` (*Conditional Research Button Visibility Based on Real Paired Remote Devices*).
* **Historical Origin & Commit Trace**: Introduced in `ATT-2057` to conditionally render the "Suchen" button only when paired remote sensors exist in SQLite.
* **Root Reason for Existing Formulation**:
  `REQ-UI-259` specified:
  > *"The central information area (SearchArea and RemoteDevices) SHALL preserve its Alignment.TopCenter anchor within the fillMaxWidth() container, guaranteeing zero layout shift or misalignment when the research button is hidden."*
  To satisfy this, the author placed both elements into a `Box` so hiding `ResearchButton` would not alter the center coordinate of `RemoteDevices`. However, this assumed `RemoteDevices` would never expand to the screen boundary, which fails when users connect multiple sensors (HR, power, cadence, speed, radar).
* **Preservation of Core Invariants**:
  The core invariant of `REQ-UI-259`—that `ResearchButton` is conditionally displayed based on `hasPairedRemoteDevices` and is interactive only when search is idle—is 100% preserved. The layout restructuring refines the spatial arrangement to eliminate coordinate collisions without breaking the conditional visibility lifecycle.

---

## 5. Architectural Strategy & High-Level Solution

### Layout Architecture in `ControlTrackingScreen.kt`
Replace the overlapping `Box` with a clean `Column` and `Row` composition:

```kotlin
Column(
    modifier = Modifier
        .fillMaxWidth()
        .padding(bottom = 8.dp),
    horizontalAlignment = Alignment.CenterHorizontally
) {
    // 1. Dedicated Search Status Row (centered, non-overlapping)
    SearchArea(
        searchingFor = searchingFor
    )

    // 2. Action & Devices Row (strictly side-by-side, no z-stacking)
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (devices.isEmpty() && showResearchButton) Arrangement.Center else Arrangement.Start
    ) {
        if (showResearchButton) {
            ResearchButton(
                isEnabled = searchingFor == null,
                onClick = onSearch,
                modifier = Modifier.padding(end = 4.dp)
            )
        }

        if (devices.isNotEmpty()) {
            RemoteDevices(
                devices = devices,
                onDeviceClick = onDeviceClick,
                modifier = Modifier.weight(1f)
            )
        }
    }
}
```

### Parameterization in `RemoteDevices.kt`
* Add `modifier: Modifier = Modifier` to `RemoteDevices(devices, onDeviceClick, modifier)`.
* Use `Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)` in `LazyRow` to give sensor tiles breathing room and avoid margin collisions.

---

## 6. System Invariants & Risk Assessment

* **Core Invariants**:
  1. `showResearchButton` conditional visibility (`REQ-UI-259`) remains strictly intact.
  2. `ResearchButton` disabled state during active search (`searchingFor != null`) remains preserved.
  3. `RemoteDevices` click handler (`onDeviceClick`) dispatches device details dialog without interference.
  4. Precise location gating (`REQ-PRI-004`) and permission rationale flow (`REQ-PRI-003`) remain untouched.
  5. Zero regression in existing unit tests (`./gradlew testDebugUnitTest`).
* **Risk Rating**: **LOW**
  - Pure Compose layout restructuring.
  - Zero state machine or database schema changes.
  - Testable via automated contract tests and Compose UI structure verification.
